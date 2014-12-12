package io.cool;

import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioTask;
import io.netty.channel.socket.SocketChannel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Semaphore;

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
	int interestOps = SelectionKey.OP_READ;

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
		LOG.info("{}", ch);
		if (ch instanceof DatagramChannel) {
			DatagramChannel dc = (DatagramChannel) ch;
			register(dc);
		} else if (ch instanceof java.nio.channels.SocketChannel) {
			java.nio.channels.SocketChannel sc = (java.nio.channels.SocketChannel) ch;
			register(sc);
			// TODO when i call finishConnect?
			sc.finishConnect();
		} else {
			throw getRuntime().newArgumentError(
					"Unsupported channel Type " + ch);
		}
		return this;
	}

	void register(SelectableChannel ch) throws IOException {
		NioEventLoopGroup group = Coolio.getIoLoop(getRuntime());
		NioEventLoop nel = (NioEventLoop) group.next();
		try {
			Semaphore semaphore = new Semaphore(1);
			ch.configureBlocking(false);
			nel.register(ch, this.interestOps,
					new NioTask<SelectableChannel>() {
						@Override
						public void channelReady(SelectableChannel ch,
								SelectionKey key) throws Exception {
							// FIXME selectするthreadとloopのスレッドの処理回数を併せる為の措置
							if (semaphore.tryAcquire()) {
								dispatch(key.readyOps(), semaphore);
							}
						}

						@Override
						public void channelUnregistered(SelectableChannel ch,
								Throwable cause) throws Exception {
							if (cause == null) {
								LOG.debug("channelUnregistered", ch);
							} else {
								LOG.debug("channelUnregistered", cause);
							}
						}
					});
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
}
