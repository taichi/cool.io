package io.cool;

import java.io.IOException;
import java.util.function.Consumer;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;

/**
 * @author taichi
 */
public class Watcher extends RubyObject {

	private static final long serialVersionUID = -7312205638559031598L;

	private static final Logger LOG = Utils.getLogger(Watcher.class);

	IRubyObject loop = getRuntime().getNil();

	public static void load(Ruby runtime) throws IOException {
		RubyClass watcher = Utils.defineClass(runtime, Watcher.class,
				Watcher::new);
		RubyModule coolio = Utils.getModule(runtime);
		RubyClass iowatcher = coolio.defineClassUnder(
				IOWatcher.class.getSimpleName(), watcher, IOWatcher::new);
		iowatcher.defineAnnotatedMethods(Watcher.class);
		iowatcher.defineAnnotatedMethods(IOWatcher.class);

		RubyClass listener = coolio.defineClassUnder("Listener", iowatcher,
				IOWatcher::new);
		RubyClass server = coolio.defineClassUnder(
				Server.class.getSimpleName(), listener, Server::new);
		server.defineAnnotatedMethods(Watcher.class);
		server.defineAnnotatedMethods(IOWatcher.class);
		server.defineAnnotatedMethods(Server.class);

		StatWatcher.load(runtime);

		RubyClass timer = coolio.defineClassUnder(
				TimerWatcher.class.getSimpleName(), watcher, TimerWatcher::new);
		timer.defineAnnotatedMethods(Watcher.class);
		timer.defineAnnotatedMethods(TimerWatcher.class);
	}

	public Watcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject arg) throws Exception {
		return this.doAttach(arg);
	}

	protected IRubyObject doAttach(IRubyObject arg) {
		LOG.debug("attach BEGIN {} {} {}", Utils.threadName(), this, arg);
		if (arg instanceof Loop) {
			Loop loop = (Loop) arg;
			loop.attach(this);
			this.loop = loop;
		}
		LOG.debug("attach END   {} {} {}", Utils.threadName(), this, arg);
		return this;
	}

	@JRubyMethod
	public IRubyObject detach() {
		return doDetach();
	}

	protected IRubyObject doDetach() {
		LOG.debug("detach BEGIN {} {} {}", Utils.threadName(), this, this.loop);
		if (this.loop.isNil()) {
			throw new IllegalStateException("not attached to a loop");
		}
		if (this.loop instanceof Loop) {
			Loop loop = (Loop) this.loop;
			loop.detach(this);
		}
		LOG.debug("detach END {} {} {}", Utils.threadName(), this, this.loop);
		return this;
	}

	@JRubyMethod(name = "attached?")
	public IRubyObject isAttached() {
		return this.loop.isNil() ? getRuntime().getFalse() : getRuntime()
				.getTrue();
	}

	@JRubyMethod
	public IRubyObject evloop() {
		return this.loop;
	}

	protected void dispatch(Consumer<Loop> fn) {
		if (this.loop instanceof Loop) {
			Loop l = (Loop) this.loop;
			l.supply(fn);
		}
	}

	// TODO not implemented.

	@JRubyMethod
	public IRubyObject enable() {
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject disable() {
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "enabled?")
	public IRubyObject isEnabled() {
		return getRuntime().getNil();
	}
}
