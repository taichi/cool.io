package io.cool;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.nio.NioEventLoopGroup;

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

	static final Logger LOG = LoggerFactory
			.getLogger(IOWatcher.class.getName());

	private static final long serialVersionUID = 8955411821845834738L;

	final NioEventLoopGroup group;

	RubyIO io;

	public IOWatcher(Ruby runtime, RubyClass metaClass, NioEventLoopGroup group) {
		super(runtime, metaClass);
		this.group = group;
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject io) {
		if (io instanceof RubyIO) {
			this.io = (RubyIO) io;
		} else {
			throw new IllegalArgumentException("must be RubyIO");
		}
		return getRuntime().getNil();
	}

	@JRubyMethod
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

	@Override
	public IRubyObject attach(IRubyObject loop) {
		super.attach(loop);
		if (loop instanceof Loop) {
			Channel channel = translate((Loop) loop);
			register(channel);
		} else {
			throw new IllegalArgumentException("Must be io.cool.Loop");
		}
		return this;
	}

	Channel translate(Loop loop) {
		java.nio.channels.Channel ch = this.io.getChannel();
		LOG.info("{}", ch);
		throw new UnsupportedOperationException("Unsupported Channel Type "
				+ ch);
	}

	// register FD to Selector
	void register(Channel channel) {
		ChannelFuture future = this.group.register(channel);
		if (future.cause() != null) {
			if (channel.isRegistered()) {
				channel.close();
			} else {
				channel.unsafe().closeForcibly();
			}
		}
		future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
	}
}