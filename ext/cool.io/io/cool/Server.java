package io.cool;

import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Arrays;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * server.rb on JRuby <br/>
 * UNIXServer is not support
 * 
 * @author taichi
 */
public class Server extends Listener {

	public Server(Ruby runtime, RubyClass metaClass, NioEventLoopGroup group) {
		super(runtime, metaClass, group);
	}

	RubyClass socketClass;
	IRubyObject[] initArgs;

	@JRubyMethod(rest = true)
	public IRubyObject initialize(IRubyObject[] args, Block block) {
		if (args.length < 1) {
			// see. https://github.com/jruby/jruby/issues/1692
			throw getRuntime().newArgumentError(
					"Server#initialize needs 1 or more arguments");
		}
		super.initialize(args[0]);
		IRubyObject klass;
		if (args.length < 2 || args[1].isNil()) {
			klass = Utils.getModule(getRuntime()).getClass("Socket");
		} else {
			klass = args[1];
		}
		this.initArgs = Arrays.copyOfRange(args, 2, args.length);
		if (klass instanceof RubyClass) {
			RubyClass rc = (RubyClass) klass;
			DynamicMethod method = rc.searchMethod("initialize");
			int arity = method.getArity().getValue();
			int expected = arity >= 0 ? arity : -(arity + 1);
			int size = initArgs.length + 1;
			if ((arity >= 0 && size != expected)
					|| (arity < 0 && size < expected)) {
				String msg = String
						.format("wrong number of arguments for %s#initialize (%d for %d)",
								rc.getName(), size, expected);
				throw getRuntime().newArgumentError(msg);
			}
			this.socketClass = rc;
		}

		System.out.println(klass);
		System.out.println(Arrays.asList(args));
		System.out.println(block);
		return this;
	}

	static class TCPServer extends Server {

		public TCPServer(Ruby runtime, RubyClass metaClass,
				NioEventLoopGroup group) {
			super(runtime, metaClass, group);
		}

	}

}
