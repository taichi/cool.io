package io.cool;

import io.netty.channel.nio.NioEventLoopGroup;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * socket.rb on JRuby <br/>
 * UNIXSocket is not support
 * 
 * @author taichi
 */
public class Socket extends IO {

	private static final long serialVersionUID = -2068274809894172808L;

	public static void load(Ruby runtime) {
		RubyModule rm = runtime.getModule("Coolio");
		RubyClass sock = Utils.defineClass(runtime, rm.getClass("IO"),
				Socket.class, Socket::new);
		Utils.defineClass(runtime, sock, TCPSocket.class, TCPSocket::new);
	}

	Connector connector;

	public Socket(Ruby r, RubyClass rc) {
		super(r, rc);
		rc.callMethod(
				"event_callback",
				new IRubyObject[] { r.newSymbol("on_connect"),
						r.newSymbol("on_connect_failed") });
		rc.callMethod(
				"alias_method",
				new IRubyObject[] { r.newSymbol("on_resolve_failed"),
						r.newSymbol("on_connect_failed") });
	}

	@JRubyMethod(rest = true)
	public IRubyObject initialize(IRubyObject[] args) {
		// do nothing.
		return getRuntime().getNil();
	}

	@JRubyMethod(meta = true, rest = true)
	public static IRubyObject connect(ThreadContext context,
			IRubyObject socket, IRubyObject[] args) {
		// TODO make client socket
		return context.nil;
	}

	static class Connector extends IOWatcher {

		private static final long serialVersionUID = -608265534082675529L;

		public Connector(Ruby runtime, RubyClass metaClass,
				NioEventLoopGroup group) {
			super(runtime, metaClass, group);
		}
		// TODO client socketの生成処理
	}

	static class TCPSocket extends Socket {

		private static final long serialVersionUID = 4867192704824514803L;

		public TCPSocket(Ruby r, RubyClass rc) {
			super(r, rc);
			rc.attr_reader(
					r.getCurrentContext(),
					new IRubyObject[] { r.newSymbol("remote_host"),
							r.newSymbol("remote_addr"),
							r.newSymbol("remote_port"),
							r.newSymbol("address_family") });
		}
	}
}
