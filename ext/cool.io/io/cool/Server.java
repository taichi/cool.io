package io.cool;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyIO;
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

	private static final long serialVersionUID = 2880224255152633861L;

	public Server(Ruby runtime, RubyClass metaClass, NioEventLoopGroup group) {
		super(runtime, metaClass, group);
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
		connection.send(c, r.newSymbol("on_connect"), Block.NULL_BLOCK);

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
			RubyArray a = (RubyArray) maybeArray;
			IRubyObject[] args = a.toJavaArray();
			IRubyObject[] newArgs = new IRubyObject[args.length + 1];
			newArgs[0] = toIO(channel);
			System.arraycopy(args, 0, newArgs, 1, args.length);
			return newArgs;
		}
		return new IRubyObject[] { toIO(channel), maybeArray };
	}

	RubyIO toIO(SocketChannel channel) {
		return RubyIO.newIO(getRuntime(), NettyHack.runJavaChannel(channel));
	}

	@Override
	protected Channel translate(Loop loop) {
		java.nio.channels.Channel ch = this.io.getChannel();
		LOG.info("{}", ch);

		if (ch instanceof java.nio.channels.ServerSocketChannel) {
			return makeUp(new NioServerSocketChannel(
					(java.nio.channels.ServerSocketChannel) ch));
		}
		if (ch instanceof java.nio.channels.DatagramChannel) {
			// TODO not implemented
		}
		throw getRuntime().newArgumentError("Unsupported channel Type " + ch);
	}

	@SuppressWarnings("unchecked")
	Channel makeUp(ServerSocketChannel channel) {
		// TODO support ServerSocket Options
		final Map<ChannelOption<?>, Object> options = new HashMap<>();
		synchronized (options) {
			channel.config().setOptions(options);
		}
		ChannelPipeline cp = channel.pipeline();

		// TODO support per connection Options and Attributes.
		final Entry<ChannelOption<?>, Object>[] currentChildOptions = new Entry[0];
		final Entry<AttributeKey<?>, Object>[] currentChildAttrs = new Entry[0];
		cp.addLast(new LoggingHandler(LogLevel.INFO));
		cp.addLast(new ChannelInitializer<Channel>() {
			@Override
			public void initChannel(Channel ch) throws Exception {
				LOG.info("initChannel {}", ch);
				ch.pipeline().addLast(
						new Acceptor(group,
								new ChannelInitializer<SocketChannel>() {
									@Override
									protected void initChannel(SocketChannel ch)
											throws Exception {
										LOG.info("initChannel with accept");
										Socket<SocketChannel> sock = makeSocket(ch);
										ch.pipeline().addLast(
												new ServerHandler(sock));
									}
								}, currentChildOptions, currentChildAttrs));
			}
		});
		return channel;
	}

	/**
	 * @see io.netty.bootstrap.ServerBootstrap.ServerBootstrapAcceptor
	 */
	static class Acceptor extends ChannelInboundHandlerAdapter {

		static final Logger LOG = LoggerFactory.getLogger(Acceptor.class
				.getName());

		private final EventLoopGroup childGroup;
		private final ChannelHandler childHandler;
		private final Entry<ChannelOption<?>, Object>[] childOptions;
		private final Entry<AttributeKey<?>, Object>[] childAttrs;

		public Acceptor(EventLoopGroup childGroup, ChannelHandler childHandler,
				Entry<ChannelOption<?>, Object>[] childOptions,
				Entry<AttributeKey<?>, Object>[] childAttrs) {
			this.childGroup = childGroup;
			this.childHandler = childHandler;
			this.childOptions = childOptions;
			this.childAttrs = childAttrs;
		}

		@Override
		@SuppressWarnings("unchecked")
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			final Channel child = (Channel) msg;

			child.pipeline().addLast(childHandler);

			for (Entry<ChannelOption<?>, Object> e : childOptions) {
				try {
					if (!child.config().setOption(
							(ChannelOption<Object>) e.getKey(), e.getValue())) {
						LOG.warn("Unknown channel option: " + e);
					}
				} catch (Throwable t) {
					LOG.warn("Failed to set a channel option: " + child, t);
				}
			}

			for (Entry<AttributeKey<?>, Object> e : childAttrs) {
				child.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
			}

			try {
				childGroup.register(child).addListener(
						new ChannelFutureListener() {
							@Override
							public void operationComplete(ChannelFuture future)
									throws Exception {
								if (!future.isSuccess()) {
									forceClose(child, future.cause());
								}
							}
						});
			} catch (Throwable t) {
				forceClose(child, t);
			}
		}

		private static void forceClose(Channel child, Throwable t) {
			child.unsafe().closeForcibly();
			LOG.warn("Failed to register an accepted channel: " + child, t);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
				throws Exception {
			final ChannelConfig config = ctx.channel().config();
			if (config.isAutoRead()) {
				config.setAutoRead(false);
				ctx.channel().eventLoop().schedule(new Runnable() {
					@Override
					public void run() {
						config.setAutoRead(true);
					}
				}, 1, TimeUnit.SECONDS);
			}
			ctx.fireExceptionCaught(cause);
		}
	}

	class ServerHandler extends ChannelInboundHandlerAdapter {

		final Socket<SocketChannel> socket;

		public ServerHandler(Socket<SocketChannel> socket) {
			this.socket = socket;
		}

		// TODO dispatch event.
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg)
				throws Exception {
			LOG.info("{} {}", msg, msg.getClass());
			ByteBuf buf = (ByteBuf) msg;
			ctx.write(msg);
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx)
				throws Exception {
			ctx.flush();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			LOG.warn(cause);
			ctx.close();
		}
	}

}
