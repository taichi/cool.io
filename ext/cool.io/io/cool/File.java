package io.cool;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFile;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.platform.Platform;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.io.ChannelDescriptor;
import org.jruby.util.io.ChannelStream;
import org.jruby.util.io.ModeFlags;
import org.jruby.util.io.OpenFile;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class File extends RubyObject {

	private static final long serialVersionUID = 6637244927066950642L;

	private static final Logger LOG = LoggerFactory.getLogger(File.class
			.getName());

	public File(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	public static void load(Ruby runtime) {
		Utils.defineClass(runtime, File.class, File::new);
	}

	@JRubyMethod(meta = true)
	public static IRubyObject open(ThreadContext context, IRubyObject self,
			IRubyObject path) throws IOException {
		if (Platform.IS_WINDOWS == false) {
			throw context.runtime.newRuntimeError("windows only.");
		}
		return new NonBlockingFile(context.getRuntime(), path.asJavaString());
	}

	// hack for "readpartial only works with Nio based handlers" on windows.
	private static final class NonBlockingFile extends RubyFile {
		private static final long serialVersionUID = 2762017601237016388L;

		NonBlockingFile(Ruby runtime, String path) throws IOException {
			super(runtime, runtime.getFile());
			LOG.info("open NonBlockingFile {}", path);
			MakeOpenFile();
			this.path = path;
			try {
				FileChannel channel = FileChannel.open(Paths.get(path),
						StandardOpenOption.READ);
				openFile = new OpenFile();
				ModeFlags md = ModeFlags.createModeFlags(ModeFlags.RDONLY
						| ModeFlags.BINARY);
				ChannelDescriptor cd = new ChannelDescriptor(channel, md);
				openFile.setMainStream(ChannelStream.open(runtime, cd));
				openFile.setMode(md.getOpenFileFlags());
			} catch (NoSuchFileException e) {
				throw getRuntime().newErrnoENOENTError(e.getMessage());
			}
		}

		@Override
		public IRubyObject close() {
			LOG.info("close NonBlockingFile {}", path);
			return super.close();
		}
	}
}