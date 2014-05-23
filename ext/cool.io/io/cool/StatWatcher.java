package io.cool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.stream.Stream;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyStruct;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class StatWatcher extends Watcher {

	public static final String STAT_INFO = "StatInfo";

	private static final long serialVersionUID = 4923375467750839661L;

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
	public IRubyObject initialize(IRubyObject path, IRubyObject interval) {
		IRubyObject i = interval.isNil() ? RubyFixnum.zero(getRuntime())
				: interval;

		Utils.setVar(this, "@path", path);
		Utils.setVar(this, "@interval", i);

		return getRuntime().getNil();
	}

	@JRubyMethod(name = "path")
	public IRubyObject getPath() {
		return Utils.getVar(this, "@path");
	}

	IRubyObject makeStatInfo(Path path) {
		RubyModule coolio = Utils.getModule(getRuntime());

		IRubyObject nil = getRuntime().getNil();

		// http://linuxjm.sourceforge.jp/html/LDP_man-pages/man2/stat.2.html
		// http://linux.die.net/man/2/stat

		BasicFileAttributes attrs = readAttributes(path);

		IRubyObject atime = at(0L);
		IRubyObject mtime = at(0L);
		IRubyObject ctime = at(0L);

		IRubyObject dev = nil;
		IRubyObject ino = nil;
		IRubyObject mode = nil;
		IRubyObject nlink = nil;

		IRubyObject uid = nil;
		IRubyObject gid = nil;
		IRubyObject rdev = nil;

		IRubyObject size = nil;

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
