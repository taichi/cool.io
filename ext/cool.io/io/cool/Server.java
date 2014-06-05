package io.cool;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyProc;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * server.rb on JRuby <br/>
 * UNIXServer is not support
 * 
 * @author taichi
 */
public class Server extends Listener {

	private static final long serialVersionUID = 2524963169711545569L;

	private static final Logger LOG = LoggerFactory.getLogger(Server.class
			.getName());

	Channel channel;

	public Server(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	// same as on_connection
	@SuppressWarnings("unchecked")
	Socket<SocketChannel> makeSocket(SocketChannel channel) {
		RubyClass socketClass = (RubyClass) Utils.getVar(this, "@klass");
		Ruby r = getRuntime();
		ThreadContext c = r.getCurrentContext();
		Socket<SocketChannel> connection = (Socket<SocketChannel>) socketClass
				.newInstance(c, makeArgs(channel), Block.NULL_BLOCK);
		connection.initialize(channel);
		connection.callOnConnect();

		IRubyObject maybeBlock = getInstanceVariable("@block");
		if (maybeBlock.isNil() == false && maybeBlock instanceof RubyProc) {
			RubyProc block = (RubyProc) maybeBlock;
			block.call(c, new IRubyObject[] { connection });
		}
		return connection;
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
	public IRubyObject attach(IRubyObject loop) {
		super.attach(loop);
		if (loop instanceof Loop) {
			this.channel = register((Loop) loop);
		}
		return this;
	}

	protected Channel register(Loop loop) {
		java.nio.channels.Channel ch = this.io.getChannel();
		LOG.info("{}", ch);

		if (ch instanceof java.nio.channels.ServerSocketChannel) {
			return makeUp((java.nio.channels.ServerSocketChannel) ch);
		}
		if (ch instanceof java.nio.channels.DatagramChannel) {
			// TODO not implemented
		}
		throw getRuntime().newArgumentError("Unsupported channel Type " + ch);
	}

	Channel makeUp(java.nio.channels.ServerSocketChannel channel) {
		ServerBootstrap b = new ServerBootstrap();
		b.group(Coolio.getIoLoop(getRuntime()))
				// TODO support ServerSocket Options
				.option(ChannelOption.SO_BACKLOG, 1024)
				.channelFactory(() -> new NioServerSocketChannel(channel))
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch)
							throws Exception {
						LOG.info("initChannel with accept");
						Socket<SocketChannel> sock = makeSocket(ch);
						ch.pipeline().addLast(new SocketEventDispatcher(sock));
						ch.closeFuture().addListener(cf -> sock.callOnClose());
					}
				});
		ChannelFuture future = b.register();
		future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		return future.awaitUninterruptibly().channel();
	}

	@Override
	public IRubyObject detach() {
		LOG.info("detach");
		super.detach();
		channel.close().awaitUninterruptibly();
		this.channel = null;
		LOG.info("detach {}", this);
		return this;
	}

	@Override
	public IRubyObject isAttached() {
		IRubyObject t = getRuntime().getTrue();
		IRubyObject f = getRuntime().getFalse();
		if (t.equals(super.isAttached())) {
			return this.channel == null ? f : t;
		}
		return f;
	}

}
