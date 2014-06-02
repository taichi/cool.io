package io.cool;

import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.RubyObjectSpace;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.JavaInternalBlockBody;
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
		// TODO org.jruby.RubyBasicObject.setInternalVariable(String, Object) 使う
		IO_EVENT_LOOP = new NioEventLoopGroup();
		// TODO how many workers do we need?
		LOCAL_EVENT_LOOP = new LocalEventLoopGroup(1);
	}

	public static void load(Ruby runtime) {
		RubyModule coolio = runtime.defineModule("Coolio");
		coolio.defineAnnotatedMethods(Coolio.class);
		RubyObjectSpace.define_finalizer(coolio, new IRubyObject[] { coolio },
				new Block(
						new JavaInternalBlockBody(runtime, Arity.NO_ARGUMENTS) {
							@Override
							public IRubyObject yield(ThreadContext context,
									IRubyObject value) {
								shutdown(context, value);
								return context.nil;
							}
						}, runtime.getCurrentContext().currentBinding()));
	}

	public static void shutdown(ThreadContext context, IRubyObject self) {
		LOG.info("shutdown");
		IO_EVENT_LOOP.shutdownGracefully();
		LOCAL_EVENT_LOOP.shutdownGracefully();
	}
}
