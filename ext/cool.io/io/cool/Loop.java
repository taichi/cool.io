package io.cool;

import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.local.LocalEventLoopGroup;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class Loop extends RubyObject {

	private static final long serialVersionUID = 2379379965441404573L;

	private static final Logger LOG = LoggerFactory.getLogger(Loop.class
			.getName());

	public static void define(Ruby runtime) throws IOException {
		MultithreadEventLoopGroup group = new LocalEventLoopGroup(1);
		Utils.defineClass(runtime, Loop.class, (r, rc) -> {
			return new Loop(r, rc, group);
		});
	}

	MultithreadEventLoopGroup group;

	public Loop(Ruby runtime, RubyClass metaClass,
			MultithreadEventLoopGroup group) {
		super(runtime, metaClass);
		this.group = group;
	}

	@JRubyMethod(name = "ev_loop_new", visibility = Visibility.PRIVATE)
	public IRubyObject startLoop(IRubyObject flags) {
		if (getRuntime().isDebug()) {
			int f = RubyNumeric.fix2int(flags);
			LOG.debug("flags are omitted {}", f);
		}
		// do nothing, already started.
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "run_once")
	public IRubyObject runOnce(IRubyObject timeout) {
		// LOG.info("{}", timeout.isNil());
		// TODO このタイムアウトを使って空のCallbackを呼んでもらう理由がよくわからぬ。
		// https://github.com/tarcieri/cool.io/commit/7453ed1ff1e20de4c99002e24407fcacdb0ad081

		// Loop.rbのrunメソッドによるwhileループがCPUサイクルを食いすぎるので大人しくさせる為にスレッドを適宜止める。
		long t = 500;// 特に根拠のない数字。このスレッドでは何もしないのでずっと止まってて貰っても良いのでは？
		if (timeout != null && timeout.isNil() == false) {
			t = Math.round(RubyNumeric.num2dbl(timeout) * 1000);
		}
		// for deadlock detection.
		group.schedule(() -> {
		}, t, TimeUnit.MILLISECONDS).awaitUninterruptibly();
		return getRuntime().getNil();
	}

	@JRubyMethod(name = "run_nonblock")
	public IRubyObject runNonBlock() {
		// TODO unimplemented
		return getRuntime().getNil();
	}
}
