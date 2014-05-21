package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyIO;
import org.jruby.RubyModule;
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

	public static void load(Ruby r) {
		RubyClass io = Utils.defineClass(r, IO.class, IO::new);
		RubyModule coolio = Utils.getModule(r);
		Class<?> cls = IO.class;
		RubyClass rc = coolio.defineClassUnder(cls.getSimpleName(),
				r.getObject(), IO::new);
		io.extend(new IRubyObject[] { coolio.getConstant("Meta") });
		io.callMethod(
				"event_callback",
				new IRubyObject[] { r.newSymbol("on_read"),
						r.newSymbol("on_write_complete"),
						r.newSymbol("on_close") });
		rc.defineAnnotatedMethods(cls);

	}

	RubyIO io;

	public IO(Ruby r, RubyClass rc) {
		super(r, rc);
	}

	@JRubyMethod(required = 1)
	public IRubyObject initialize(IRubyObject io) {
		if (io instanceof RubyIO) {
			this.io = (RubyIO) io;
		} else {
			throw getRuntime().newArgumentError("must be RubyIO");
		}
		return getRuntime().getNil();
	}

	@JRubyMethod(required = 1)
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

	@JRubyMethod(required = 1)
	public IRubyObject write(IRubyObject data) {
		return this.io.write(getRuntime().getCurrentContext(), data);
	}

	@JRubyMethod()
	public IRubyObject close() {
		if (io != null && io.isClosed() == false) {
			io.close();
		}
		return getRuntime().getNil();
	}

	public void callOnRead(IRubyObject data) {
		callMethod("on_read", data);
	}

	public void callOnWriteComplete() {
		callMethod("on_write_complete");
	}

	public void callOnClose() {
		callMethod("on_close");
	}

}
