package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class TimerWatcher extends Watcher {

	private static final long serialVersionUID = 9053518598303171222L;

	public TimerWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject interval, IRubyObject repeating) {
		double d = RubyNumeric.num2dbl(interval);
		boolean is = repeating.isTrue();
		System.out.printf("%s %s%n", d, is);
		return getRuntime().getNil();
	}

	// TODO
}