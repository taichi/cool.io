package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyObject;

/**
 * socket.rb on JRuby <br/>
 * UNIXSocket is not support
 * 
 * @author taichi
 */
public class Socket {

	public static void load(Ruby runtime) {
		Utils.defineClass(runtime, TCPSocket.class, TCPSocket::new);
	}

	static class TCPSocket extends RubyObject {

		private static final long serialVersionUID = 4867192704824514803L;

		public TCPSocket(Ruby runtime, RubyClass metaClass) {
			super(runtime, metaClass);
		}

	}
}
