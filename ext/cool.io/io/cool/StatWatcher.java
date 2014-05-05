package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class StatWatcher extends Watcher {

	private static final long serialVersionUID = 4923375467750839661L;

	public StatWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject path, IRubyObject interval) {
		String p = path.asJavaString();
		double d = RubyNumeric.num2dbl(interval);

		System.out.printf("**** %s %s %n", p, d);
		return getRuntime().getNil();
	}

	// TODO
}