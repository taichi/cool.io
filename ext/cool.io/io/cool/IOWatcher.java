package io.cool;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.io.UncheckedIOException;

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

	private static final long serialVersionUID = -9155305357984430840L;

	private static final Logger LOG = LoggerFactory.getLogger(IOWatcher.class
			.getName());

	RubyIO io;
	ChannelFuture future;

	public IOWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1, optional = 1)
	public IRubyObject initialize(IRubyObject[] args) {
		this.io = (RubyIO) args[0];
		if (1 < args.length) {
			IRubyObject flag = args[1];
			// TODO
		}
		return getRuntime().getNil();
	}

	@Override
	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject arg) {
		super.doAttach(arg);
		java.nio.channels.Channel ch = this.io.getChannel();
		LOG.info("{}", ch);
		if (ch instanceof java.nio.channels.DatagramChannel) {
			register((java.nio.channels.DatagramChannel) ch);
		} else if (ch instanceof java.nio.channels.SocketChannel) {
			register((java.nio.channels.SocketChannel) ch);
		} else {
			throw getRuntime().newArgumentError(
					"Unsupported channel Type " + ch);
		}
		return this;
	}

	void register(java.nio.channels.DatagramChannel channel) {
		DatagramChannel dc = new NioDatagramChannel(channel);
		dc.config().setAutoRead(false);
		dc.pipeline().addLast(new ChannelInboundHandlerAdapter() {
			@Override
			public void channelActive(ChannelHandlerContext ctx)
					throws Exception {
				dispatchOnReadable();
			}
		});
		register(dc);
	}

	void register(java.nio.channels.SocketChannel ch) {
		SocketChannel sc = new NioSocketChannel(ch);
		sc.pipeline().addLast(new ChannelInboundHandlerAdapter() {
			@Override
			public void channelActive(ChannelHandlerContext ctx)
					throws Exception {
				dispatchOnWritable();
			}
		});
		register(sc);
		try {
			ch.finishConnect();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	void register(Channel ch) {
		ChannelPromise cp = ch.newPromise();
		cp.addListener(f -> {
			if (f.isSuccess() == false) {
				LOG.error(f.cause());
			}
		});
		this.future = Coolio.getIoLoop(getRuntime()).register(ch, cp);
	}

	@JRubyMethod
	public IRubyObject detach() {
		this.future.awaitUninterruptibly().channel().deregister();
		return super.doDetach();
	}

	void dispatchOnReadable() {
		dispatch("on_readable");
	}

	void dispatchOnWritable() {
		dispatch("on_writable");
	}

	void dispatch(String event) {
		dispatch(l -> this.callMethod(event));
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