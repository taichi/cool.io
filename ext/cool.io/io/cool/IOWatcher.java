package io.cool;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioTask;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Semaphore;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyIO;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
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
	ChannelFuture future;

	public IOWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1, optional = 1)
	public IRubyObject initialize(ThreadContext context, IRubyObject[] args) {
		this.io = RubyIO.convertToIO(context, args[0]);
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
		LOG.debug("attach {} {}#{}", getMetaClass(), ch,
				System.identityHashCode(ch));
		if (ch instanceof DatagramChannel) {
			DatagramChannel dc = (DatagramChannel) ch;
			register(dc);
		} else if (ch instanceof java.nio.channels.SocketChannel) {
			java.nio.channels.SocketChannel sc = (java.nio.channels.SocketChannel) ch;
			register(sc);
		} else {
			throw getRuntime().newArgumentError(
					"Unsupported channel Type " + ch);
		}
		return this;
	}

	void register(java.nio.channels.SocketChannel sc) throws IOException {
		Channel ch = new NioSocketChannel(sc);
		ch.config().setRecvByteBufAllocator(() -> new HackHandle());
		Semaphore semaphore = new Semaphore(1);
		ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
			@Override
			public void channelActive(ChannelHandlerContext ctx)
					throws Exception {
				if (semaphore.tryAcquire()) {
					dispatch(SelectionKey.OP_WRITE, semaphore);
				}
			}

			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg)
					throws Exception {
				ch.config().setAutoRead(false);
				if (semaphore.tryAcquire()) {
					dispatch(SelectionKey.OP_READ, semaphore);
				}
			}

			@Override
			public void channelReadComplete(ChannelHandlerContext ctx)
					throws Exception {
				ch.config().setAutoRead(true);
			}
		});
		if (sc.isConnectionPending()) {
			if (sc.finishConnect()) {
				Utils.setVar(this, "@so_error", RubyFixnum.zero(getRuntime()));
			} else {
				Utils.setVar(this, "@so_error", RubyFixnum.one(getRuntime()));
			}
			if (semaphore.tryAcquire()) {
				dispatch(SelectionKey.OP_WRITE, semaphore);
			}
		}
		future = Coolio.getIoLoop(getRuntime()).register(ch);
	}

	SelectableTask task;

	class SelectableTask implements NioTask<SelectableChannel> {
		SelectionKey lastKey;
		Semaphore semaphre = new Semaphore(1);

		@Override
		public void channelReady(SelectableChannel ch, SelectionKey key)
				throws Exception {
			if (semaphre.tryAcquire()) { // wait for the worker
				dispatch(key.readyOps(), semaphre);
			}
			this.lastKey = key;
		}

		@Override
		public void channelUnregistered(SelectableChannel ch, Throwable cause)
				throws Exception {
			if (cause == null) {
				LOG.debug("channelUnregistered {} {}#{} {}",
						Utils.threadName(), ch, System.identityHashCode(ch),
						ch.isRegistered());
			} else {
				LOG.debug("channelUnregistered {} {}#{} {}",
						Utils.threadName(), ch, System.identityHashCode(ch),
						cause);
			}
		}
	}

	void register(SelectableChannel ch) throws IOException {
		NioEventLoopGroup group = Coolio.getIoLoop(getRuntime());
		NioEventLoop nel = (NioEventLoop) group.next();
		try {
			this.task = new SelectableTask();
			ch.configureBlocking(false);
			nel.register(ch, this.interestOps, this.task);
			nel.execute(() -> {
			});
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	void dispatch(int readyOps, Semaphore semaphore) {
		if ((readyOps & SelectionKey.OP_READ) != 0 || readyOps == 0) {
			dispatch("on_readable", semaphore);
		} else if ((readyOps & SelectionKey.OP_WRITE) != 0) {
			dispatch("on_writable", semaphore);
		}
	}

	void dispatch(String event, Semaphore semaphore) {
		dispatch(l -> {
			try {
				this.callMethod(event);
			} finally {
				// c実装ではwatcher.cのdetachでloopの中に抱え込んだイベントのうち
				// 当該watcherに関係のあるものだけをフィルタリングして消しているが、
				// Java実装ではそこで削除されるイベントの数が簡単に10000単位になるので、
				// semaphoreを使ってそもそもイベントをスタックしない。
				semaphore.release();
			}
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

	@JRubyMethod
	@Override
	public IRubyObject detach() {
		LOG.debug("detach {}", getMetaClass());

		if (this.task != null) {
			SelectionKey sk = this.task.lastKey;
			if (sk != null && sk.isValid()) {
				LOG.debug("SelectionKey#cancel {}", getMetaClass());
				sk.cancel();
			}
		}
		if (this.future != null) {
			LOG.debug("Channel#deregister {}", getMetaClass());
			this.future.awaitUninterruptibly().channel().deregister();
		}
		return super.detach();
	}
}
