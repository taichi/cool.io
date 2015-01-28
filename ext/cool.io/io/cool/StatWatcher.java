package io.cool;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

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

/**
 * @author taichi
 */
public class StatWatcher extends Watcher {

	private static final long serialVersionUID = 4387711551695038606L;

	static final Logger LOG = Utils.getLogger(StatWatcher.class);

	public static final String STAT_INFO = "StatInfo";

	final AtomicReference<IRubyObject> previous = new AtomicReference<>(
			makeEmptyStatInfo());
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
		LOG.debug("{} {}", this, getWatchFilePath());

		super.doAttach(loop);

		FileSentinel fs = Coolio.getFileSentinel(getRuntime());
		this.listener = fs.register(this::dispatch);
		fs.watch(getWatchFilePath());

		return this;
	}

	void dispatch(Path root, WatchEvent<?> event) {
		if (OVERFLOW.equals(event.kind())) {
			LOG.warn("OVERFLOW {} {}", root, event.context());
			return;
		}
		if (loop instanceof Loop) {
			Loop lp = (Loop) loop;
			lp.supply(l -> {
				LOG.debug("BEGIN run in worker {} {}", event.kind().name(),
						root);
				Path resolved = root.resolve(Path.class.cast(event.context()));
				LOG.debug("watch target {} resolved path {}",
						getWatchFilePath(), resolved);
				if (resolved.equals(getWatchFilePath())) {
					final IRubyObject current;
					if (ENTRY_DELETE.equals(event.kind())) {
						current = makeEmptyStatInfo();
					} else {
						current = makeStatInfo(resolved);
					}
					IRubyObject prev = this.previous.getAndUpdate(p -> current);
					callMethod("on_change", prev, current);
				}
				LOG.debug("END run in worker {} {}", event.kind().name(), root);
			});
		}

	}

	public IRubyObject detach() {
		super.detach();
		FileSentinel fs = Coolio.getFileSentinel(getRuntime());
		fs.unwatch(getWatchFilePath());
		fs.unregister(this.listener);
		return this;
	}

	IRubyObject makeStatInfo(Path path) {
		try {
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

			IRubyObject[] args = { mtime, ctime, atime, dev, ino, mode, nlink,
					uid, gid, rdev, size, blksize, blocks, };
			return makeStatInfo(args);
		} catch (IOException e) {
			return makeEmptyStatInfo();
		}
	}

	IRubyObject makeStatInfo(IRubyObject[] args) {
		RubyModule coolio = Utils.getModule(getRuntime());
		return RubyStruct.newStruct(coolio.getConstant(STAT_INFO), args,
				Block.NULL_BLOCK);
	}

	IRubyObject makeEmptyStatInfo() {
		IRubyObject[] args = {};
		return makeStatInfo(args);
	}

	RubyTime at(long milliseconds) {
		return RubyTime.newTime(getRuntime(), milliseconds);
	}
}
