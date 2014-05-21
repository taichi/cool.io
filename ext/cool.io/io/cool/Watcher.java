package io.cool;

import io.cool.Socket.Connector;
import io.netty.channel.nio.NioEventLoopGroup;

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

	static final Logger LOG = LoggerFactory.getLogger(Watcher.class.getName());

	private static final long serialVersionUID = -7312205638559031598L;

	IRubyObject loop = getRuntime().getNil();

	public static void load(Ruby runtime) throws IOException {
		RubyClass watcher = Utils.defineClass(runtime, Watcher.class,
				Watcher::new);
		// share groups all of IOWatchers.
		NioEventLoopGroup group = Coolio.IO_EVENT_LOOP;
		RubyClass iowatcher = Utils.defineClass(runtime, watcher,
				IOWatcher.class, (r, rc) -> new IOWatcher(r, rc, group));
		RubyClass listener = Utils.defineClass(runtime, iowatcher,
				Listener.class, (r, rc) -> new Listener(r, rc, group));
		listener.defineAnnotatedConstants(Listener.class);
		Utils.defineClass(runtime, listener, Server.class,
				(r, rc) -> new Server(r, rc, group));

		Utils.defineClass(runtime, watcher, StatWatcher.class, StatWatcher::new);
		Utils.defineClass(runtime, watcher, TimerWatcher.class,
				(r, rc) -> new TimerWatcher(r, rc, Coolio.LOCAL_EVENT_LOOP));
		RubyClass connector = Utils.defineClass(runtime, iowatcher,
				Connector.class, (r, rc) -> new Connector(r, rc, group));
		connector.addReadWriteAttribute(runtime.getCurrentContext(),
				"_connector");
	}

	public Watcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(required = 1)
	public IRubyObject attach(IRubyObject loop) {
		// TODO attach と detach の処理に一貫性は必要か？
		if (this.loop.isNil() == false) {
			this.detach();
		}
		IRubyObject watchers = Utils.getVar(loop, "@watchers");
		RubyHash hash;
		if (watchers instanceof RubyHash) {
			hash = (RubyHash) watchers;
		} else {
			hash = Utils.setVar(loop, "@watchers",
					RubyHash.newHash(getRuntime()));
		}
		hash.put(this, getRuntime().getTrue());

		IRubyObject aw = Utils.getVar(loop, "@active_watchers");
		if (RubyFixnum.zero(getRuntime()).equals(aw) || aw == null
				|| aw.isNil()) {
			aw = RubyFixnum.one(getRuntime());
		} else {
			aw = getRuntime().newFixnum(RubyFixnum.fix2int(aw) + 1);
		}
		Utils.setVar(loop, "@active_watchers", aw);
		this.loop = loop;
		return this;
	}

	@JRubyMethod
	public IRubyObject detach() {
		LOG.info("detach");
		if (this.loop.isNil()) {
			throw new IllegalStateException("not attached to a loop");
		}
		RubyHash hash = (RubyHash) Utils.getVar(loop, "@watchers");
		hash.remove(this);

		RubyFixnum aw = (RubyFixnum) Utils.getVar(loop, "@active_watchers");
		aw = getRuntime().newFixnum(RubyFixnum.fix2int(aw) - 1);
		Utils.setVar(loop, "@active_watchers", aw);
		this.loop = getRuntime().getNil();
		LOG.info("detach {}", this);
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