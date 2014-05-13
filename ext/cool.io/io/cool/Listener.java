package io.cool;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
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
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.anno.JRubyConstant;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * listener.rb on JRuby <br/>
 * UNIXListener is not support
 * 
 * @author taichi
 */
public class Listener extends IOWatcher {

	static final Logger LOG = LoggerFactory.getLogger(Listener.class.getName());

	private static final long serialVersionUID = -1592741627890256654L;

	public Listener(Ruby runtime, RubyClass metaClass, NioEventLoopGroup group) {
		super(runtime, metaClass, group);
	}

	@JRubyConstant
	public static final int DEFAULT_BACKLOG = 1024;

	// @JRubyMethod
	public IRubyObject listen(IRubyObject backlog) {
		LOG.info("listen backlog={}", backlog);
		this.io.callMethod("listen", backlog);
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject close() {
		LOG.info("close");
		if (getRuntime().getTrue().equals(isAttached())) {
			detach();
		}
		return getRuntime().getNil();
	}

	IRubyObject makeCoolioSocket(SocketChannel socket) {
		if (socket instanceof NioSocketChannel) {
			// NioSocketChannel nsc = (NioSocketChannel) socket;
			// SocketChannel を RubyObjectのSocket的なアレに変換する。
			// IRubyObject sockRO = null;
			// Channelは既にnetty管理下にあるので、attach的な事はいらない。
			// 引渡されてきているCoolio::Socket的なオブジェクトの生成はやらないといけない。
			// on_connectionはインターナルなイベントなので通知しなくていいかもしれない。
			// this.callMethod("on_connection");
		}
		throw getRuntime().newArgumentError("Must be NioSocketChannel");
	}

	@JRubyMethod(name = "on_connection")
	public IRubyObject onConnection() {
		return getRuntime().getNil(); // do nothing.
	}

	@Override
	Channel translate(Loop loop) {
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
		cp.addLast(new LoggingHandler(LogLevel.INFO),
				new ChannelInitializer<Channel>() {
					@Override
					public void initChannel(Channel ch) throws Exception {
						LOG.info("initChannel {}", ch);
						ch.pipeline()
								.addLast(
										new Acceptor(
												group,
												new ChannelInitializer<SocketChannel>() {
													@Override
													protected void initChannel(
															SocketChannel ch)
															throws Exception {
														LOG.info("initChannel with accept");
														ch.pipeline()
																.addLast(
																		new ServerHandler());
													}
												}, currentChildOptions,
												currentChildAttrs));
						// Nettyがacceptするので、Listener#on_readable は呼び出さない。
						// dispatchOnReadable();
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
				// stop accept new connections for 1 second to allow the channel
				// to
				// recover
				// See https://github.com/netty/netty/issues/1328
				config.setAutoRead(false);
				ctx.channel().eventLoop().schedule(new Runnable() {
					@Override
					public void run() {
						config.setAutoRead(true);
					}
				}, 1, TimeUnit.SECONDS);
			}
			// still let the exceptionCaught event flow through the pipeline to
			// give
			// the user
			// a chance to do something with it
			ctx.fireExceptionCaught(cause);
		}
	}

	@Sharable
	class ServerHandler extends ChannelInboundHandlerAdapter {
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
