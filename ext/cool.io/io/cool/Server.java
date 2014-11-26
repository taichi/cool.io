package io.cool;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
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
	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject loop) {
		super.attach(loop);
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
						Socket<SocketChannel> sock = makeSocket(ch);
						ch.pipeline().addLast(new SocketEventDispatcher(sock));
						ch.closeFuture().addListener(cf -> sock.callOnClose());
					}
				});
		this.future = b.register();
		this.future
				.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
	}

	ChannelFuture future;

	@Override
	@JRubyMethod
	public IRubyObject detach() {
		return super.detach();
	}

	@Override
	@JRubyMethod(name = "attached?")
	public IRubyObject isAttached() {
		return super.isAttached();
	}

	@JRubyMethod
	public IRubyObject close() throws Exception {
		if (getRuntime().getTrue().equals(isAttached())) {
			detach();
		}
		this.future.await().channel().close();
		return this;
	}

}
