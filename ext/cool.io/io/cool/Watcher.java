package io.cool;

import io.netty.channel.nio.NioEventLoopGroup;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyHash;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.builtin.InstanceVariables;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class Watcher extends RubyObject {

	static final Logger LOG = LoggerFactory.getLogger(Watcher.class.getName());

	private static final long serialVersionUID = -7312205638559031598L;

	public static void load(Ruby runtime) throws IOException {
		RubyClass watcher = Utils.defineClass(runtime, Watcher.class,
				Watcher::new);
		// share groups all of IOWatchers.
		NioEventLoopGroup group = new NioEventLoopGroup();
		// TODO how to shutdown groups ?
		// runtime.addFinalizer(() -> group.shutdownGracefully());
		RubyClass iowatcher = Utils.defineClass(runtime, watcher,
				IOWatcher.class, (r, rc) -> {
					return new IOWatcher(r, rc, group);
				});
		Utils.defineClass(runtime, iowatcher, Listener.class, (r, rc) -> {
			return new Listener(r, rc, group);
		});
		Utils.defineClass(runtime, watcher, StatWatcher.class, StatWatcher::new);
		Utils.defineClass(runtime, watcher, TimerWatcher.class,
				TimerWatcher::new);
	}

	public Watcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject attach(IRubyObject loop) {
		InstanceVariables vars = loop.getInstanceVariables();
		IRubyObject watchers = vars.getInstanceVariable("@watchers");
		LOG.info("{}", watchers);
		RubyHash hash;
		if (watchers instanceof RubyHash) {
			hash = (RubyHash) watchers;
		} else {
			hash = RubyHash.newHash(getRuntime());
			vars.setInstanceVariable("@watchers", hash);
		}
		hash.put(this, getRuntime().getTrue());

		IRubyObject aw = vars.getInstanceVariable("@active_watchers");
		LOG.info("{}", aw);
		if (RubyFixnum.zero(getRuntime()).eql(aw) || aw == null || aw.isNil()) {
			aw = RubyFixnum.one(getRuntime());
		} else {
			aw = getRuntime().newFixnum(RubyFixnum.fix2int(aw) + 1);
		}
		vars.setInstanceVariable("@active_watchers", aw);

		return this;
	}

	// TODO not implemented.

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
}