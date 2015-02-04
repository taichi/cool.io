package io.cool;

import io.Buffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioTask;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyIO;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.JavaObject;
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

	Channel channel;
	Consumer<IOWatcher> disposer;
	Semaphore semaphore = new Semaphore(1);

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

	void register(java.nio.channels.SocketChannel sc) {
		Channel ch = new NioSocketChannel(sc);
		ch.config().setRecvByteBufAllocator(() -> new HackHandle());
		ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
			@Override
			public void channelActive(ChannelHandlerContext ctx)
					throws Exception {
				dispatch(SelectionKey.OP_WRITE);
			}

			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg)
					throws Exception {
				ch.config().setAutoRead(false);
				dispatch(SelectionKey.OP_READ);
			}

			@Override
			public void channelReadComplete(ChannelHandlerContext ctx)
					throws Exception {
				ch.config().setAutoRead(true);
			}
		});
		ch.closeFuture().addListener(f -> dispatch(SelectionKey.OP_WRITE));
		if (sc.isOpen() && sc.isConnectionPending()) {
			RubyFixnum num = RubyFixnum.one(getRuntime());
			try {
				if (sc.finishConnect()) {
					num = RubyFixnum.zero(getRuntime());
				}
			} catch (IOException e) {
				// suppress error
			}
			Utils.setVar(this, "@so_error", num);
			dispatch(SelectionKey.OP_WRITE);
		}
		this.disposer = this::dispose;
		this.channel = ch;
		Coolio.getIoLoop(getRuntime()).register(ch);
	}

	protected void dispose(IOWatcher w) {
		LOG.debug("Channel#deregister {}", getMetaClass());
		this.channel.deregister();
	}

	@JRubyMethod(argTypes = { JavaObject.class }, required = 1)
	public IRubyObject receive(IRubyObject ch) {
		JavaObject wrapper = (JavaObject) ch;
		this.channel = (Channel) wrapper.dataGetStruct();
		return this;
	}

	@JRubyMethod(argTypes = { Buffer.class }, required = 1)
	public IRubyObject write(IRubyObject buffer) {
		if (this.channel == null) {
			throw getRuntime()
					.newRuntimeError(
							"write after attach. jruby implementation is not support write before attach.");
		}
		Buffer buff = (Buffer) buffer;
		LOG.debug("write buffer {}", buff);
		ByteBuf cp = buff.internalBuffer().copy();
		buff.clear();
		this.channel.writeAndFlush(cp,
				this.channel.newPromise().addListener(f -> {
					dispatch("on_write_complete");
				}));
		return this;
	}

	@JRubyMethod
	public IRubyObject validate_writable(ThreadContext context) {
		LOG.debug("validate_writable {} {}", this,
				System.identityHashCode(this));
		if (this.channel != null && this.channel.isOpen() == false) {
			throw getRuntime().newIOError("socket is not writable");
		}
		return context.nil;
	}

	class SelectableTask implements NioTask<SelectableChannel> {
		SelectionKey lastKey;

		@Override
		public void channelReady(SelectableChannel ch, SelectionKey key)
				throws Exception {
			dispatch(key.readyOps());
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

	void register(SelectableChannel ch) {
		NioEventLoopGroup group = Coolio.getIoLoop(getRuntime());
		NioEventLoop nel = (NioEventLoop) group.next();
		try {
			SelectableTask task = new SelectableTask();
			ch.configureBlocking(false);
			nel.register(ch, this.interestOps, task);
			nel.execute(() -> {
			});
			this.disposer = w -> {
				SelectionKey sk = task.lastKey;
				if (sk != null && sk.isValid()) {
					LOG.debug("SelectionKey#cancel {}", getMetaClass());
					sk.cancel();
				}
			};
		} catch (IOException ex) {
			throw getRuntime().newIOErrorFromException(ex);
		}
	}

	void dispatch(int readyOps) {
		if ((readyOps & SelectionKey.OP_READ) != 0 || readyOps == 0) {
			dispatch("on_readable");
		} else if ((readyOps & SelectionKey.OP_WRITE) != 0) {
			dispatch("on_writable");
		}
	}

	void dispatch(String event) {
		// c実装ではwatcher.cのdetachでloopの中に抱え込んだイベントのうち
		// 当該watcherに関係のあるものだけをフィルタリングして消している。
		// Java実装ではChannelからデータを読み取らない限りSelectorからイベントが配信され続ける。
		// これによって残タスク数が簡単に数万になってしまうので、semaphoreを使ってそもそもイベントをスタックしないようにしている。
		// これによってRubyコード側の処理性能に全体的な性能が引っ張られてしまう。
		if (semaphore.tryAcquire()) {
			LOG.debug("dispatch {} {}", event, getMetaClass());
			dispatch(l -> {
				try {
					this.callMethod(event);
					LOG.debug("called {} {}", event, getMetaClass());
				} finally {
					semaphore.release();
				}
			});
		}
	}

	@JRubyMethod(name = "on_readable")
	public IRubyObject onReadable() {
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "on_writable")
	public IRubyObject onWritable() {
		return getRuntime().getNil(); // do nothing.
	}

	@JRubyMethod
	@Override
	public IRubyObject detach() {
		LOG.debug("detach {}", getMetaClass());
		if (this.disposer != null) {
			this.disposer.accept(this);
		}
		return super.detach();
	}
}
