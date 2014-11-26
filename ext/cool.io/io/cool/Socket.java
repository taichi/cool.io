package io.cool;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyIO;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * socket.rb on JRuby <br/>
 * UNIXSocket is not support
 * 
 * @author taichi
 */
public class Socket<C extends Channel> extends RubyObject {

	private static final long serialVersionUID = -2434070702852103374L;

	private static final Logger LOG = LoggerFactory.getLogger(Socket.class
			.getName());

	public static void load(Ruby r) {
		RubyClass sock = Utils.defineClass(r, Utils.getClass(r, "IO"),
				Socket.class, Socket::new);
		sock.callMethod("watcher_delegate",
				new IRubyObject[] { r.newSymbol("@_connector") });
		sock.callMethod(
				"event_callback",
				new IRubyObject[] { r.newSymbol("on_connect"),
						r.newSymbol("on_connect_failed") });
		sock.callMethod(
				"alias_method",
				new IRubyObject[] { r.newSymbol("on_resolve_failed"),
						r.newSymbol("on_connect_failed") });
		Utils.defineClass(r, sock, TCPSocket.class, TCPSocket::new);
	}

	C channel;

	Connector connector;

	RubyIO io;

	public Socket(Ruby r, RubyClass rc) {
		super(r, rc);
	}

	public void initialize(C channel) {
		this.channel = channel;
	}

	public void callOnConnect() {
		Ruby r = getRuntime();
		ThreadContext c = r.getCurrentContext();
		send(c, r.newSymbol("on_connect"), Block.NULL_BLOCK);
	}

	@JRubyMethod(required = 1, argTypes = { RubyIO.class })
	public IRubyObject initialize(IRubyObject io) {
		this.io = (RubyIO) io;
		return getRuntime().getNil();
	}

	@JRubyMethod(rest = true)
	public IRubyObject initialize(IRubyObject[] args) {
		if (0 < args.length) {
			return initialize(args[0]);
		}
		return getRuntime().getNil();
	}

	public IRubyObject write(IRubyObject data) {
		// TODO Support Buffering ?
		// use PooledByteBufAllocator
		ByteList buf = data.asString().getByteList();
		ByteBuf msg = Unpooled.wrappedBuffer(buf.getUnsafeBytes(), buf.begin(),
				buf.length());
		int length = msg.readableBytes();
		LOG.info("length {}", length);
		// TODO CRuby版とon_write_completeが呼び出されるタイミングがズレたかも…
		// TODO 毎回Flushするのはパフォーマンス的にみて相当ひどい。
		// JRuby版だけはSocket#flush足すかも。要検証
		this.channel.writeAndFlush(msg)
				.addListener(cf -> callOnWriteComplete());
		return RubyFixnum.int2fix(getRuntime(), length);
	}

	public void setConnector(Connector connector) {
		this.connector = connector;
		this.setInstanceVariable("@_connector", connector);
	}

	public Connector getConnector() {
		return this.connector;
	}

	@JRubyMethod
	public IRubyObject close() {
		LOG.info("close");
		if (connector != null
				&& getRuntime().getTrue().equals(connector.isAttached())) {
			connector.detach();
		}
		if (io != null && io.isClosed() == false) {
			io.close();
		}
		return getRuntime().getNil();
	}

	public void callOnRead(IRubyObject data) {
		callMethod("on_read", data);
	}

	public void callOnWriteComplete() {
		callMethod("on_write_complete");
	}

	public void callOnClose() {
		callMethod("on_close");
	}

	public static class Connector extends IOWatcher {

		private static final long serialVersionUID = -608265534082675529L;

		Socket<SocketChannel> coolioSocket;

		InetSocketAddress address;

		public Connector(Ruby runtime, RubyClass metaClass) {
			super(runtime, metaClass);
		}

		@JRubyMethod(argTypes = { IRubyObject.class, RubyFixnum.class })
		public IRubyObject initialize(IRubyObject host, IRubyObject port) {
			this.address = new InetSocketAddress(host.asJavaString(),
					RubyFixnum.fix2int(port));
			return this;
		}

		@Override
		@JRubyMethod(required = 1, argTypes = { Loop.class })
		public IRubyObject attach(IRubyObject loop) {
			super.attach(loop);
			Bootstrap b = new Bootstrap();
			EventLoopGroup group = Coolio.getIoLoop(getRuntime());
			b.group(group).channel(NioSocketChannel.class)
					// TODO support TCP options
					.option(ChannelOption.TCP_NODELAY, true)
					.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						public void initChannel(SocketChannel ch)
								throws Exception {
							coolioSocket.io = toIO(ch);
							coolioSocket.initialize(ch);
							coolioSocket.callOnConnect();
							ch.pipeline().addLast(
							// new LoggingHandler(LogLevel.INFO),
									new SocketEventDispatcher(coolioSocket));
							ch.closeFuture().addListener(
									cf -> coolioSocket.callOnClose());
						}
					});
			// TODO ここでconnectするのが正しいのか微妙。
			// selectループをトリガすることを期待されているのは、
			// Loop#run辺りだがNettyのインターフェースとそれ程上手くは適合しない。
			b.connect(address);
			return this;
		}

	}

	public static class TCPSocket extends Socket<SocketChannel> {

		private static final long serialVersionUID = 4867192704824514803L;

		public TCPSocket(Ruby r, RubyClass rc) {
			super(r, rc);
		}

		@JRubyMethod(meta = true)
		public static IRubyObject connect(ThreadContext context,
				IRubyObject self, IRubyObject host, IRubyObject port) {
			RubyClass sockClazz = (RubyClass) self;
			TCPSocket sock = (TCPSocket) sockClazz.allocate();
			RubyClass conClazz = Utils.getClass(context.getRuntime(),
					"Connector");
			Connector connector = (Connector) conClazz.newInstance(context,
					host, port, Block.NULL_BLOCK);
			sock.setConnector(connector);
			connector.coolioSocket = sock; // TODO 循環参照…
			return sock;
		}

		@JRubyMethod(name = "remote_host")
		public IRubyObject getRemoteHost() {
			return RubyString.newString(getRuntime(), getRemoteAddress()
					.getHostName());
		}

		private InetSocketAddress getRemoteAddress() {
			return channel.remoteAddress();
		}

		@JRubyMethod(name = "remote_addr")
		public IRubyObject getRemoteAddr() {
			return RubyString.newString(getRuntime(), getRemoteAddress()
					.getAddress().getHostAddress());
		}

		@JRubyMethod(name = "remote_port")
		public IRubyObject getRemotePort() {
			return RubyFixnum.newFixnum(getRuntime(), getRemoteAddress()
					.getPort());
		}

		@JRubyMethod(name = "address_family")
		public IRubyObject getAddressFamily() {
			String family = "AF_INET";
			InetAddress addr = getRemoteAddress().getAddress();
			if (addr instanceof Inet6Address) {
				family = "AF_INET6";
			}
			return RubyString.newString(getRuntime(), family);
		}

		@JRubyMethod(name = "peeraddr")
		public IRubyObject getPeeraddr() {
			return RubyArray.newArrayLight(getRuntime(), getAddressFamily(),
					getRemotePort(), getRemoteHost(), getRemoteAddr());
		}
	}
}
