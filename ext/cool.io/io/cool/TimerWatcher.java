package io.cool;

import java.math.BigDecimal;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;

/**
 * @author taichi
 */
public class TimerWatcher extends Watcher {

	private static final long serialVersionUID = 9053518598303171222L;

	static final Logger LOG = Utils.getLogger(TimerWatcher.class);

	ScheduledFuture<?> currentFuture; // TODO need lock?

	public TimerWatcher(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject interval, IRubyObject repeating) {
		double d = RubyNumeric.num2dbl(interval);
		boolean is = repeating.isTrue();
		LOG.debug("{} {}", d, is);
		if (d < 0) {
			throw getRuntime().newArgumentError(
					"interval must be positive value");
		}

		Utils.setVar(this, "@interval", interval);
		Utils.setVar(this, "@repeating", repeating);
		return getRuntime().getNil();
	}

	@Override
	@JRubyMethod(required = 1, argTypes = { Loop.class })
	public IRubyObject attach(IRubyObject loop) {
		super.doAttach(loop);
		schedule();
		return this;
	}

	void callOnTimer() {
		LOG.debug("callOnTimer {} {}", getMetaClass(), this.loop);
		if (this.loop instanceof Loop) {
			Loop lp = (Loop) this.loop;
			lp.supply(l -> {
				LOG.debug("supply {} {}", getMetaClass(),
						currentFuture.isCancelled());
				if (currentFuture.isCancelled() == false) {
					callMethod("on_timer");
				}
			});
		}
	}

	void cancel() {
		if (currentFuture != null && currentFuture.isDone() == false) {
			currentFuture.cancel(true);
		}
	}

	void schedule() {
		IRubyObject interval = Utils.getVar(this, "@interval");
		double d = RubyNumeric.num2dbl(interval);
		BigDecimal bd = BigDecimal.valueOf(d)
				.multiply(BigDecimal.valueOf(1000));
		long delay = bd.longValue();

		IRubyObject repeating = Utils.getVar(this, "@repeating");
		ScheduledExecutorService pool = Coolio.getWorkerPool(getRuntime());
		LOG.debug("schedule {} delay:{} repeating:{}", getMetaClass(), delay,
				repeating);
		if (repeating.isTrue()) {
			currentFuture = pool.scheduleAtFixedRate(this::callOnTimer, delay,
					delay, TimeUnit.MILLISECONDS);
		} else {
			currentFuture = pool.schedule(this::callOnTimer, delay,
					TimeUnit.MILLISECONDS);
		}
	}

	@Override
	@JRubyMethod
	public IRubyObject detach() {
		super.detach();
		cancel();
		return this;
	}

	@JRubyMethod
	public IRubyObject reset() {
		cancel();
		schedule();
		return this;
	}

}
