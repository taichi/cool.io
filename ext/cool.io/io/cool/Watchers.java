package io.cool;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;

/**
 * @author taichi
 */
public class Watchers {
	public static void define(Ruby runtime) throws IOException {

		RubyModule coolio = runtime.defineModule("Coolio");
		RubyClass ioWatcher = coolio.defineClassUnder("IOWatcher",
				runtime.getObject(), IOWatcher::new);
		ioWatcher.defineAnnotatedMethods(IOWatcher.class);

		RubyClass statWatcher = coolio.defineClassUnder("StatWatcher",
				runtime.getObject(), StatWatcher::new);
		statWatcher.defineAnnotatedMethods(StatWatcher.class);
	}

	static class Watcher extends RubyObject {
		private static final long serialVersionUID = 2834517103433193775L;

		public Watcher(Ruby runtime, RubyClass metaClass) {
			super(runtime, metaClass);
		}

		// TODO
	}

	public static class IOWatcher extends RubyObject {

		private static final long serialVersionUID = -43089848973662723L;

		public IOWatcher(Ruby runtime, RubyClass metaClass) {
			super(runtime, metaClass);
		}

		// TODO
	}

	public static class StatWatcher extends RubyObject {

		private static final long serialVersionUID = -5093689236065033103L;

		public StatWatcher(Ruby runtime, RubyClass metaClass) {
			super(runtime, metaClass);
		}

		// TODO
	}

	public static class TimerWatcher extends RubyObject {
		private static final long serialVersionUID = 9221926883221268652L;

		public TimerWatcher(Ruby runtime, RubyClass metaClass) {
			super(runtime, metaClass);
		}

		// TODO
	}

}
