package io.cool;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author taichi
 */
public class Watcher extends RubyObject {

	private static final long serialVersionUID = -7312205638559031598L;

	public static void define(Ruby runtime) throws IOException {
		RubyModule coolio = runtime.defineModule("Coolio");

		RubyClass watcher = defineWatcher(coolio, runtime.getObject(),
				Watcher.class, Watcher::new);
		defineWatcher(coolio, watcher, IOWatcher.class, IOWatcher::new);
		defineWatcher(coolio, watcher, StatWatcher.class, StatWatcher::new);
		defineWatcher(coolio, watcher, TimerWatcher.class, TimerWatcher::new);
	}

	static RubyClass defineWatcher(RubyModule rm, RubyClass parent,
			Class<?> cls, ObjectAllocator oa) {
		RubyClass rc = rm.defineClassUnder(cls.getSimpleName(), parent, oa);
		rc.defineAnnotatedMethods(cls);
		return rc;
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