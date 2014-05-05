package io.cool;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class Loop extends RubyObject {

	private static final long serialVersionUID = 2379379965441404573L;

	public static void define(Ruby runtime) throws IOException {
		Utils.defineClass(runtime, Loop.class, Loop::new);
		// TODO
	}

	public Loop(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@Override
	public IRubyObject initialize(ThreadContext context) {
		startLoop(RubyFixnum.zero(getRuntime()));
		return super.initialize(context);
	}

	@JRubyMethod(name = "ev_loop_new", visibility = Visibility.PRIVATE)
	public IRubyObject startLoop(IRubyObject flags) {
		int f = RubyNumeric.fix2int(flags);
		System.out.printf("*** Loop %d%n", f);
		// TODO
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "run_once")
	public IRubyObject runOnce() {
		System.out.printf("+++ run_once %n");
		// TODO
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "run_once")
	public IRubyObject runOnce(IRubyObject timeout) {
		double t = RubyNumeric.num2dbl(timeout);
		System.out.printf("*** run_once %s%n", t);
		// TODO
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "run_nonblock")
	public IRubyObject runNonBlock() {
		// TODO
		return getRuntime().getNil();
	}
}
