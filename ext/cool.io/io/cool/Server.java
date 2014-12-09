package io.cool;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyProc;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * server.rb on JRuby
 * 
 * @author taichi
 */
public class Server extends IOWatcher {

	private static final long serialVersionUID = 2524963169711545569L;

	private static final Logger LOG = LoggerFactory.getLogger(Server.class
			.getName());

	public Server(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	// same as on_connection
	IRubyObject makeSocket(SocketChannel channel) {
		RubyClass socketClass = (RubyClass) Utils.getVar(this, "@klass");
		Ruby r = getRuntime();
		ThreadContext c = r.getCurrentContext();
		IRubyObject sock = socketClass.newInstance(c, makeArgs(channel),
				Block.NULL_BLOCK);
		dispatch(l -> sock.callMethod(c, "on_connect"));

		IRubyObject maybeBlock = getInstanceVariable("@block");
		if (maybeBlock.isNil() == false && maybeBlock instanceof RubyProc) {
			RubyProc block = (RubyProc) maybeBlock;
			block.call(c, new IRubyObject[] { sock });
		}
		return sock;
	}

	IRubyObject[] makeArgs(SocketChannel channel) {
		IRubyObject maybeArray = Utils.getVar(this, "@args");
		if (maybeArray.isNil()) {
			return new IRubyObject[] { toIO(channel) };
		}
		if (maybeArray instanceof RubyArray) {
			RubyArray a = (RubyArray) maybeArray.dup();
			a.unshift(toIO(channel));
			return a.toJavaArray();
		}
		return new IRubyObject[] { toIO(channel), maybeArray };
	}

	@Override
	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject loop) {
		super.doAttach(loop);
		java.nio.channels.Channel ch = this.io.getChannel();
		LOG.info("{}", ch);
		if (ch instanceof java.nio.channels.ServerSocketChannel) {
			register((java.nio.channels.ServerSocketChannel) ch);
		} else {
			throw getRuntime().newArgumentError(
					"Unsupported channel Type " + ch);
		}
		return this;
	}

	void register(java.nio.channels.ServerSocketChannel channel) {
		ServerBootstrap b = new ServerBootstrap();
		b.group(Coolio.getIoLoop(getRuntime()))
				.channelFactory(() -> new NioServerSocketChannel(channel))
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch)
							throws Exception {
						LOG.info("initChannel with accept");
						IRubyObject sock = makeSocket(ch);
						ch.pipeline().addLast(new SocketEventDispatcher(sock));
						ch.closeFuture().addListener(
								cf -> sock.callMethod(sock.getRuntime()
										.getCurrentContext(), "on_close"));
					}
				});
		this.future = b.register();
		this.future
				.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
	}

}
