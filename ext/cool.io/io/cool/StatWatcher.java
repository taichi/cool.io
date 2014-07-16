package io.cool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyStruct;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class StatWatcher extends Watcher {

	private static final long serialVersionUID = 4387711551695038606L;

	static final Logger LOG = LoggerFactory.getLogger(StatWatcher.class
			.getName());

	public static final String STAT_INFO = "StatInfo";

	final AtomicReference<IRubyObject> previous = new AtomicReference<>(
			getRuntime().getNil());
	Path watchFilePath;
	BiConsumer<Path, WatchEvent<?>> listener;

	public static void load(Ruby runtime) {
		RubyModule coolio = Utils.getModule(runtime);
		RubyClass watcher = Utils.getClass(runtime, "Watcher");
		RubyClass statWatcher = Utils.defineClass(runtime, watcher,
				StatWatcher.class, StatWatcher::new);
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

	public StatWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject path) {
		return initialize(path, RubyFixnum.zero(getRuntime()));
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject path, IRubyObject interval) {
		IRubyObject i = interval.isNil() ? RubyFixnum.zero(getRuntime())
				: interval;

		this.watchFilePath = Paths.get(path.asJavaString()).toAbsolutePath();

		// ファイル単位でしか監視できなくて辛くないのかな。
		Utils.setVar(this, "@path", path);
		Utils.setVar(this, "@interval", i);

		return getRuntime().getNil();
	}

	Path getWatchFilePath() {
		return this.watchFilePath;
	}

	@Override
	public IRubyObject attach(IRubyObject loop) {
		DynamicMethod method = getMetaClass().searchMethod("on_change");
		Arity.TWO_REQUIRED.checkArity(getRuntime(), method.getArity()
				.getValue());

		super.attach(loop);

		FileSentinel fs = Coolio.getFileSentinel(getRuntime());
		this.listener = fs.register(this::dispatch);
		fs.watch(getWatchFilePath());

		return this;
	}

	void dispatch(Path root, WatchEvent<?> event) {
		Coolio.getWorkerLoop(getRuntime()).submit(
				() -> {
					LOG.info("BEGIN run in worker {} {}", event.kind().name(),
							root);
					try {
						Path resolved = root.resolve(Path.class.cast(event
								.context()));
						LOG.info("watch target {} resolved path {}",
								getWatchFilePath(), resolved);
						if (resolved.equals(getWatchFilePath())) {
							IRubyObject current = makeStatInfo(resolved);
							IRubyObject prev = this.previous
									.getAndUpdate(p -> current);
							callMethod("on_change", prev, current);
						}
					} catch (IOException e) {
						LOG.info(e);
					}
					LOG.info("END run in worker {} {}", event.kind().name(),
							root);
				});
	}

	public IRubyObject detach() {
		super.detach();
		FileSentinel fs = Coolio.getFileSentinel(getRuntime());
		fs.unwatch(getWatchFilePath());
		fs.unregister(this.listener);
		return this;
	}

	IRubyObject makeStatInfo(Path path) throws IOException {
		RubyModule coolio = Utils.getModule(getRuntime());

		IRubyObject nil = getRuntime().getNil();

		// http://linuxjm.sourceforge.jp/html/LDP_man-pages/man2/stat.2.html
		// http://linux.die.net/man/2/stat

		BasicFileAttributes attrs = Files.readAttributes(path,
				BasicFileAttributes.class);

		IRubyObject atime = at(attrs.lastAccessTime().toMillis());
		IRubyObject mtime = at(attrs.lastModifiedTime().toMillis());
		IRubyObject ctime = at(attrs.creationTime().toMillis());

		// if u want to unsupported informations, use FFI.
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

	RubyTime at(long milliseconds) {
		return RubyTime.newTime(getRuntime(), milliseconds);
	}
}
