package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyIO;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * io.rb on JRuby
 * 
 * @author taichi
 */
public class IO extends RubyObject {

	private static final long serialVersionUID = 5960920173752724702L;

	public static void load(Ruby runtime) {
		Utils.defineClass(runtime, IO.class, IO::new);
	}

	RubyIO io;

	public IO(Ruby r, RubyClass metaClass) {
		super(r, metaClass);
		RubyModule rm = r.getModule("Coolio");
		metaClass.extend(new IRubyObject[] { rm.getConstant("Meta") });
		metaClass.callMethod(
				"event_callback",
				new IRubyObject[] { r.newSymbol("on_read"),
						r.newSymbol("on_write_complete"),
						r.newSymbol("on_close") });
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject io) {
		if (io instanceof RubyIO) {
			this.io = (RubyIO) io;
		} else {
			throw new IllegalArgumentException("must be RubyIO");
		}
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject attach(IRubyObject loop) {

		return this;
	}

	@JRubyMethod(name = "attached?")
	public IRubyObject isAttached() {
		return this;
	}

	@JRubyMethod
	public IRubyObject detach() {
		return this;
	}

	@JRubyMethod
	public IRubyObject enable() {
		return this;
	}

	@JRubyMethod
	public IRubyObject disable() {
		return this;
	}

	@JRubyMethod
	public IRubyObject write(IRubyObject data) {
		int size = 0;
		// TODO not implemented
		return RubyNumeric.int2fix(getRuntime(), size);
	}

}
