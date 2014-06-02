package io.cool;

import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyObjectSpace;
import org.jruby.RubyStruct;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.JavaInternalBlockBody;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class StatWatcher extends Watcher {

	public static final String STAT_INFO = "StatInfo";

	private static final long serialVersionUID = 4923375467750839661L;

	final WatchService watchService;
	final EventExecutorGroup watcherPool;
	final EventExecutorGroup workerPool;

	Future<?> watcherFuture; // TODO need lock?
	WatchKey watchKey;

	public static void load(Ruby runtime) {
		RubyModule coolio = Utils.getModule(runtime);
		RubyClass watcher = Utils.getClass(runtime, "Watcher");
		RubyClass statWatcher = Utils.defineClass(runtime, watcher,
				StatWatcher.class, (r, rc) -> new StatWatcher(r, rc,
						Coolio.LOCAL_EVENT_LOOP));
		watcher.extend(new IRubyObject[] { coolio.getConstant("Meta") });
		// TODO ruby版には無いが不便ないのだろうか。
		statWatcher.callMethod("event_callback",
				new IRubyObject[] { runtime.newSymbol("on_change") });

		IRubyObject[] args = Stream.concat(
				Stream.of(runtime.newString(STAT_INFO)),
				Arrays.asList("mtime", "ctime", "atime", "dev", "ino", "mode",
						"nlink", "uid", "guid", "rdev", "size", "blksize",
						"blocks").stream().map(s -> runtime.newSymbol(s)))
				.toArray(IRubyObject[]::new);

		RubyClass statInfo = RubyStruct.newInstance(runtime.getStructClass(),
				args, Block.NULL_BLOCK);
		coolio.setConstant(STAT_INFO, statInfo);
	}

	public StatWatcher(Ruby runtime, RubyClass metaClass,
			EventExecutorGroup group) {
		super(runtime, metaClass);
		this.workerPool = group;
		// TODO 全てのStatWatcherでWatchServiceを共有すべきだが…
		this.watchService = Utils.newWatchService();
		this.watcherPool = new LocalEventLoopGroup(1);
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject path, IRubyObject interval) {
		IRubyObject i = interval.isNil() ? RubyFixnum.zero(getRuntime())
				: interval;

		// ファイル単位でしか監視できなくて辛くないのかな。
		Utils.setVar(this, "@path", path);
		Utils.setVar(this, "@interval", i);

		RubyObjectSpace.define_finalizer(this, new IRubyObject[] { this },
				new Block(new JavaInternalBlockBody(getRuntime(),
						Arity.NO_ARGUMENTS) {
					@Override
					public IRubyObject yield(ThreadContext context,
							IRubyObject value) {
						Utils.close(watchService);
						cancel();
						watcherPool.shutdownGracefully();
						return context.nil;
					}
				}, getRuntime().getCurrentContext().currentBinding()));

		return getRuntime().getNil();
	}

	@JRubyMethod(name = "path")
	public IRubyObject getPath() {
		return Utils.getVar(this, "@path");
	}

	@Override
	public IRubyObject attach(IRubyObject loop) {
		super.attach(loop);
		Path path = Paths.get(getPath().asJavaString());
		BasicFileAttributes attrs = readAttributes(path);
		if (attrs.isRegularFile()) {
			path = path.getParent();
		}
		watchKey = Utils.watch(this.watchService, path);
		start();
		return this;
	}

	void start() {
		this.watcherFuture = watcherPool
				.submit(() -> {
					try {
						while (Thread.interrupted() == false) {
							WatchKey key = watchService.take();
							Path path = Path.class.cast(key.watchable());
							for (WatchEvent<?> event : key.pollEvents()) {
								dispatch(path, event);
							}
							key.reset();
						}
					} catch (ClosedWatchServiceException
							| RejectedExecutionException e) {
						LOG.debug("any time no problem.", e);
					} catch (Exception e) {
						LOG.error(e);
					}
				});
	}

	// TODO how to remove entries?
	ConcurrentHashMap<Path, IRubyObject> map = new ConcurrentHashMap<>();

	void dispatch(Path root, WatchEvent<?> event) {
		Path resolved = root.resolve(Path.class.cast(event.context()));
		Path path = Paths.get(getPath().asJavaString());
		if (resolved.endsWith(path)) {
			IRubyObject current = makeStatInfo(resolved);
			workerPool.submit(() -> {
				try {
					IRubyObject prev = map.get(resolved);
					DynamicMethod method = getMetaClass().searchMethod(
							"on_change");
					Arity a = method.getArity();
					Arity.TWO_REQUIRED.checkArity(getRuntime(), a.getValue());
					callMethod("on_change", prev, current);
				} finally {
					map.put(resolved, current);
				}
			});
		}
	}

	void cancel() {
		if (watchKey != null) {
			watchKey.cancel();
		}
		if (watcherFuture != null && watcherFuture.isDone() == false
				&& watcherFuture.isCancellable()) {
			watcherFuture.cancel(true);
		}
	}

	public IRubyObject detach() {
		super.detach();
		cancel();
		return this;
	}

	IRubyObject makeStatInfo(Path path) {
		RubyModule coolio = Utils.getModule(getRuntime());

		IRubyObject nil = getRuntime().getNil();

		// http://linuxjm.sourceforge.jp/html/LDP_man-pages/man2/stat.2.html
		// http://linux.die.net/man/2/stat

		BasicFileAttributes attrs = readAttributes(path);

		IRubyObject atime = at(attrs.lastAccessTime().toMillis());
		IRubyObject mtime = at(attrs.lastModifiedTime().toMillis());
		IRubyObject ctime = at(attrs.creationTime().toMillis());

		// TODO unsupported
		IRubyObject dev = nil;
		IRubyObject ino = nil;
		IRubyObject mode = nil;
		IRubyObject nlink = nil;

		// TODO unsupported
		IRubyObject uid = nil;
		IRubyObject gid = nil;
		IRubyObject rdev = nil;

		IRubyObject size = RubyFixnum.newFixnum(getRuntime(), attrs.size());

		// TODO unsupported
		IRubyObject blksize = nil;
		IRubyObject blocks = nil;

		IRubyObject[] args = { mtime, ctime, atime, dev, ino, mode, nlink, uid,
				gid, rdev, size, blksize, blocks, };
		return RubyStruct.newStruct(coolio.getConstant(STAT_INFO), args,
				Block.NULL_BLOCK);
	}

	BasicFileAttributes readAttributes(Path path) {
		try {
			return Files.readAttributes(path, BasicFileAttributes.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	RubyTime at(long milliseconds) {
		return RubyTime.newTime(getRuntime(), milliseconds);
	}
}
