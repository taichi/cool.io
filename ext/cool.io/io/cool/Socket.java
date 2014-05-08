package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
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
		Utils.defineClass(runtime, Socket.class, Socket::new);
		Utils.defineClass(runtime, TCPSocket.class, TCPSocket::new);
	}

	public Socket(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
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
