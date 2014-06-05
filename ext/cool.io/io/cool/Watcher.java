package io.cool;

import io.cool.Socket.Connector;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyHash;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class Watcher extends RubyObject {

	private static final long serialVersionUID = -7312205638559031598L;

	private static final Logger LOG = LoggerFactory.getLogger(Watcher.class
			.getName());

	IRubyObject loop = getRuntime().getNil();

	public static void load(Ruby runtime) throws IOException {
		RubyClass watcher = Utils.defineClass(runtime, Watcher.class,
				Watcher::new);
		RubyClass iowatcher = Utils.defineClass(runtime, watcher,
				IOWatcher.class, IOWatcher::new);
		RubyClass listener = Utils.defineClass(runtime, iowatcher,
				Listener.class, Listener::new);
		listener.defineAnnotatedConstants(Listener.class);
		Utils.defineClass(runtime, listener, Server.class, Server::new);

		StatWatcher.load(runtime);

		Utils.defineClass(runtime, watcher, TimerWatcher.class,
				TimerWatcher::new);
		RubyClass connector = Utils.defineClass(runtime, iowatcher,
				Connector.class, Connector::new);
		connector.addReadWriteAttribute(runtime.getCurrentContext(),
				"_connector");
	}

	public Watcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject loop) {
		LOG.info("attach BEGIN {}", this);
		// TODO attach と detach の処理に一貫性は必要か？
		if (this.loop.isNil() == false) {
			this.detach();
		}
		IRubyObject watchers = Utils.getVar(loop, "@watchers");
		RubyHash hash;
		if (watchers instanceof RubyHash) {
			hash = (RubyHash) watchers;
		} else {
			hash = RubyHash.newHash(getRuntime());
		}
		hash.put(this, getRuntime().getTrue());
		Utils.setVar(loop, "@watchers", hash);

		IRubyObject aw = Utils.getVar(loop, "@active_watchers");
		if (RubyFixnum.zero(getRuntime()).equals(aw) || aw == null
				|| aw.isNil()) {
			aw = RubyFixnum.one(getRuntime());
		} else {
			aw = getRuntime().newFixnum(RubyFixnum.fix2int(aw) + 1);
		}
		Utils.setVar(loop, "@active_watchers", aw);
		this.loop = loop;
		LOG.info("attach END   {}", this);
		return this;
	}

	@JRubyMethod
	public IRubyObject detach() {
		LOG.info("detach BEGIN {}", this);
		if (this.loop.isNil()) {
			throw new IllegalStateException("not attached to a loop");
		}
		RubyHash hash = (RubyHash) Utils.getVar(loop, "@watchers");
		hash.remove(this);

		RubyFixnum aw = (RubyFixnum) Utils.getVar(loop, "@active_watchers");
		aw = getRuntime().newFixnum(RubyFixnum.fix2int(aw) - 1);
		Utils.setVar(loop, "@active_watchers", aw);
		this.loop = getRuntime().getNil();
		LOG.info("detach END  {}", this);
		return this;
	}

	@JRubyMethod(name = "attached?")
	public IRubyObject isAttached() {
		return this.loop.isNil() ? getRuntime().getFalse() : getRuntime()
				.getTrue();
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

	@JRubyMethod
	public IRubyObject evloop() {
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "enabled?")
	public IRubyObject isEnabled() {
		return getRuntime().getNil();
	}
}