package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class IOWatcher extends Watcher {

	private static final long serialVersionUID = 8955411821845834738L;

	public IOWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject reader) {
		// TODO reader
		System.out.println("** IOWatcher not implemented.");
		return getRuntime().getNil();
	}

	// TODO
}