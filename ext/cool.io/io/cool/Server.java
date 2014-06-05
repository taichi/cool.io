package io.cool;

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
			RubyArray a = (RubyArray) maybeArray;
			IRubyObject[] args = a.toJavaArray();
			IRubyObject[] newArgs = new IRubyObject[args.length + 1];
			newArgs[0] = toIO(channel);
			System.arraycopy(args, 0, newArgs, 1, args.length);
			return newArgs;
		}
		return new IRubyObject[] { toIO(channel), maybeArray };
	}

	@Override
	public IRubyObject attach(IRubyObject loop) {
		super.attach(loop);
		if (loop instanceof Loop) {
			Channel channel = translate((Loop) loop);
			register(channel);
		}
		return this;
	}

	// register FD to Selector
	void register(Channel channel) {
		NioEventLoopGroup group = Coolio.getIoLoop(getRuntime());
		ChannelFuture future = group.register(channel);
		if (future.cause() != null) {
			if (channel.isRegistered()) {
				channel.close();
			} else {
				channel.unsafe().closeForcibly();
			}
		}
		future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
		this.channel = channel;
	}

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
		NioEventLoopGroup group = Coolio.getIoLoop(getRuntime());
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
										ch.pipeline()
												.addLast(
														new SocketEventDispatcher(
																sock));
										ch.closeFuture().addListener(
												cf -> sock.callOnClose());
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
