package io.cool;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyIO;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;

/**
 * @author taichi
 */
public class IOWatcher extends Watcher {

	private static final long serialVersionUID = -9155305357984430840L;

	private static final Logger LOG = Utils.getLogger(IOWatcher.class);

	RubyIO io;
	int interestOps = SelectionKey.OP_READ;
	protected ChannelFuture future;

	public IOWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1, optional = 1)
	public IRubyObject initialize(IRubyObject[] args) {
		this.io = (RubyIO) args[0];
		this.interestOps = parseFlags(args);
		return getRuntime().getNil();
	}

	int parseFlags(IRubyObject[] args) {
		if (1 < args.length) {
			IRubyObject flag = args[1];
			String s = (String) flag.toJava(String.class);
			if ("r".equals(s)) {
				return SelectionKey.OP_READ;
			} else if ("w".equals(s)) {
				return SelectionKey.OP_WRITE;
			} else if ("rw".equals(s)) {
				return SelectionKey.OP_READ | SelectionKey.OP_WRITE;
			} else {
				String msg = String.format(
						"invalid event type: '%s' (must be 'r', 'w', or 'rw')",
						s);
				throw getRuntime().newArgumentError(msg);
			}
		}
		return SelectionKey.OP_READ;
	}

	@Override
	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject arg) throws IOException {
		super.doAttach(arg);
		java.nio.channels.Channel ch = this.io.getChannel();
		LOG.debug("{}", ch);
		if (ch instanceof DatagramChannel) {
			register((DatagramChannel) ch);
		} else if (ch instanceof java.nio.channels.SocketChannel) {
			register((java.nio.channels.SocketChannel) ch);
		} else {
			throw getRuntime().newArgumentError(
					"Unsupported channel Type " + ch);
		}
		return this;
	}

	void register(java.nio.channels.DatagramChannel dc) {
		Channel ch = new NioDatagramChannel(dc);
		ch.config().setAutoRead(false);
		ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
			@Override
			public void channelActive(ChannelHandlerContext ctx)
					throws Exception {
				dispatch("on_readable");
			}
		});
		register(ch);
	}

	void register(java.nio.channels.SocketChannel sc) {
		Channel ch = new NioSocketChannel(sc);
		ch.config().setAutoRead(false);
		ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
			@Override
			public void channelActive(ChannelHandlerContext ctx)
					throws Exception {
				dispatch("on_writable");
			}
		});
		register(ch);
		try {
			sc.finishConnect();
		} catch (IOException e) {
			LOG.error(e);
		}
	}

	void register(Channel ch) {
		ChannelPromise promise = ch.newPromise();
		promise.addListener(f -> {
			if (f.isSuccess() == false) {
				LOG.error(f.cause());
			}
		});
		this.future = Coolio.getIoLoop(getRuntime()).register(ch, promise);
	}

	@JRubyMethod
	@Override
	public IRubyObject detach() {
		this.future.awaitUninterruptibly().channel().deregister();
		return super.detach();
	}

	void dispatch(String event) {
		LOG.debug("dispatch {}", event);
		dispatch(l -> {
			LOG.debug("accept dispatch {} {}", event, getMetaClass());
			this.callMethod(event);
		});
	}

	@JRubyMethod(name = "on_readable")
	public IRubyObject onReadable() {
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "on_writable")
	public IRubyObject onWritable() {
		return getRuntime().getNil(); // do nothing.
	}

	protected RubyIO toIO(SocketChannel channel) {
		return RubyIO.newIO(getRuntime(), NettyHack.runJavaChannel(channel));
	}
}
