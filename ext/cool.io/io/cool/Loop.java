package io.cool;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyHash;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;

/**
 * @author taichi
 */
public class Loop extends RubyObject {

	private static final long serialVersionUID = 2379379965441404573L;

	private static final Logger LOG = Utils.getLogger(Loop.class);

	Lock lock = new ReentrantLock();
	BlockingQueue<Consumer<Loop>> events = new LinkedBlockingQueue<>();

	public static void load(Ruby runtime) throws IOException {
		Utils.defineClass(runtime, Loop.class, Loop::new);
	}

	public Loop(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod(name = "ev_loop_new", visibility = Visibility.PRIVATE, required = 1)
	public IRubyObject startLoop(IRubyObject flags) {
		if (getRuntime().isDebug()) {
			int f = RubyNumeric.fix2int(flags);
			LOG.debug("flags are omitted {}", f);
		}
		return getRuntime().getNil();
	}

	@JRubyMethod(argTypes = { RubyNumeric.class }, optional = 1)
	public IRubyObject run_once(IRubyObject[] timeout)
			throws InterruptedException {
		long t = 500;
		if (0 < timeout.length) {
			IRubyObject tmp = timeout[0];
			if (tmp.isNil() == false) {
				double d = RubyNumeric.num2dbl(tmp);
				if (0 < d) {
					t = Math.round(d * 1000);
				}
			}
		}

		if(Utils.debug) {
			LOG.debug(
					"run_once timeout:{} events:{} running:{} active_watchers:{} watchers:{}",
					t, this.events.size(), Utils.getVar(this, "@running"),
					Utils.getVar(this, "@active_watchers"),
					Utils.getVar(this, "@watchers"));
		}
		Consumer<Loop> ev = this.events.poll(t, TimeUnit.MILLISECONDS);
		if (ev != null) {
			doLock(l -> {
				ev.accept(this);
				LOG.debug("accepted thread:{} events:{}", Utils.threadName(),
						this.events.size());
			});
		}
		return RubyNumeric.int2fix(getRuntime(), this.events.size());
	}

	@JRubyMethod(name = "run_nonblock")
	public IRubyObject runNonBlock() {
		Coolio.getWorkerPool(getRuntime()).submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				events.take().accept(Loop.this);
				return null;
			}
		});
		return RubyNumeric.int2fix(getRuntime(), this.events.size());
	}

	void attach(Watcher watcher) {
		doLock(l -> internalAttach(watcher));
	}

	void supply(Consumer<Loop> event) {
		doLock(l -> {
			this.events.add(event);
			LOG.debug("supply {} {}", Utils.threadName(), this.events.size());
		});
	}

	void detach(Watcher watcher) {
		doLock(l -> internalDetach(watcher));
	}

	void doLock(Consumer<Lock> op) {
		lock.lock();
		try {
			op.accept(lock);
		} finally {
			lock.unlock();
		}
	}

	void internalAttach(Watcher w) {
		if (w.loop.isNil() == false) {
			internalDetach(w);
		}
		RubyHash hash = watchers();
		hash.put(w, getRuntime().getTrue());
		Utils.setVar(this, "@watchers", hash);

		RubyFixnum aw = numberOfActiveWatchers();
		aw = getRuntime().newFixnum(RubyNumeric.fix2int(aw) + 1);
		Utils.setVar(this, "@active_watchers", aw);
		w.loop = this;
	}

	void internalDetach(Watcher w) {
		RubyHash hash = watchers();
		hash.remove(w);
		RubyFixnum aw = numberOfActiveWatchers();
		if (RubyFixnum.zero(getRuntime())
				.op_lt(getRuntime().getCurrentContext(), aw)
				.equals(getRuntime().getTrue())) {
			aw = getRuntime().newFixnum(RubyNumeric.fix2int(aw) - 1);
			Utils.setVar(this, "@active_watchers", aw);
		}
		w.loop = getRuntime().getNil();
	}

	RubyHash watchers() {
		IRubyObject watchers = Utils.getVar(this, "@watchers");
		if (watchers instanceof RubyHash) {
			return (RubyHash) watchers;
		}
		return RubyHash.newHash(getRuntime());
	}

	RubyFixnum numberOfActiveWatchers() {
		IRubyObject aw = Utils.getVar(this, "@active_watchers");
		if (aw instanceof RubyFixnum) {
			return (RubyFixnum) aw;
		}
		return RubyFixnum.zero(getRuntime());
	}
}
