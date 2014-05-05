package io.cool;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class Watcher extends RubyObject {

	private static final long serialVersionUID = -7312205638559031598L;

	public static void define(Ruby runtime) throws IOException {
		RubyClass watcher = Utils.defineClass(runtime, Watcher.class,
				Watcher::new);
		Utils.defineClass(runtime, watcher, IOWatcher.class, IOWatcher::new);
		Utils.defineClass(runtime, watcher, StatWatcher.class, StatWatcher::new);
		Utils.defineClass(runtime, watcher, TimerWatcher.class,
				TimerWatcher::new);
	}

	public Watcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject attach(IRubyObject loop) {
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject detach() {
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject enable() {
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject disable() {
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject evloop() {
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "attached?")
	public IRubyObject attached() {
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "enabled?")
	public IRubyObject enabled() {
		return getRuntime().getNil();
	}

	// TODO
}