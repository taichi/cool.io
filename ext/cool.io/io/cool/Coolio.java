package io.cool;

import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class Coolio {

	static final Logger LOG = LoggerFactory.getLogger(Coolio.class.getName());

	final static NioEventLoopGroup IO_EVENT_LOOP;

	final static LocalEventLoopGroup LOCAL_EVENT_LOOP;

	static {
		IO_EVENT_LOOP = new NioEventLoopGroup();
		LOCAL_EVENT_LOOP = new LocalEventLoopGroup(1);
	}

	public static void load(Ruby runtime) {
		RubyModule coolio = runtime.defineModule("Coolio");
		coolio.defineAnnotatedMethods(Coolio.class);
	}

	@JRubyMethod(module = true)
	public static void shutdown(ThreadContext context, IRubyObject self) {
		LOG.info("shutdown");
		IO_EVENT_LOOP.shutdownGracefully();
		LOCAL_EVENT_LOOP.shutdownGracefully();
		NettyHack.shutdown();
	}
}
