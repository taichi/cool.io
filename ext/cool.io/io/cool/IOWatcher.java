package io.cool;

import io.netty.channel.socket.SocketChannel;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyIO;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class IOWatcher extends Watcher {

	private static final long serialVersionUID = -9155305357984430840L;

	private static final Logger LOG = LoggerFactory.getLogger(IOWatcher.class
			.getName());

	RubyIO io;

	public IOWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1, argTypes = { RubyIO.class })
	public IRubyObject initialize(IRubyObject io) {
		this.io = (RubyIO) io;
		return getRuntime().getNil();
	}

	@JRubyMethod(required = 2, argTypes = { RubyIO.class, IRubyObject.class })
	public IRubyObject initialize(IRubyObject io, IRubyObject flags) {
		initialize(io);
		// TODO parse flags
		LOG.info("** IOWatcher with flags not implemented.");
		return getRuntime().getNil();
	}

	void dispatchOnReadable() {
		this.callMethod("on_readable");
	}

	void dispatchOnWritable() {
		this.callMethod("on_writable");
	}

	@JRubyMethod(name = "on_readable")
	public IRubyObject onReadable() {
		return getRuntime().getNil(); // do nothing.
	}

	@JRubyMethod(name = "on_writable")
	public IRubyObject onWritable() {
		return getRuntime().getNil(); // do nothing.
	}

	protected RubyIO toIO(SocketChannel channel) {
		return RubyIO.newIO(getRuntime(), NettyHack.runJavaChannel(channel));
	}
}