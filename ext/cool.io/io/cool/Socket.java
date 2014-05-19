package io.cool;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
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
public class Socket<C extends Channel> extends IO {

	private static final long serialVersionUID = -2434070702852103374L;

	private static final Logger LOG = LoggerFactory.getLogger(Socket.class
			.getName());

	public static void load(Ruby r) {
		RubyClass sock = Utils.defineClass(r, Utils.getClass(r, "IO"),
				Socket.class, Socket::new);
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

	public Socket(Ruby r, RubyClass rc) {
		super(r, rc);
	}

	public void initialize(C channel) {
		this.channel = channel;
	}

	@JRubyMethod(rest = true)
	public IRubyObject initialize(IRubyObject[] args) {
		if (0 < args.length) {
			return initialize(args[0]);
		}
		return getRuntime().getNil();
	}

	@Override
	public IRubyObject write(IRubyObject data) {
		// TODO Support Buffering ?
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

	@JRubyMethod(meta = true, rest = true)
	public static IRubyObject connect(ThreadContext context,
			IRubyObject socket, IRubyObject[] args) {
		// TODO make client socket
		return context.nil;
	}

	public static class Connector extends IOWatcher {

		private static final long serialVersionUID = -608265534082675529L;

		public Connector(Ruby runtime, RubyClass metaClass,
				NioEventLoopGroup group) {
			super(runtime, metaClass, group);
		}

		// TODO client socketの生成処理
		@Override
		protected Channel translate(Loop loop) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	public static class TCPSocket extends Socket<SocketChannel> {

		private static final long serialVersionUID = 4867192704824514803L;

		public TCPSocket(Ruby r, RubyClass rc) {
			super(r, rc);
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
