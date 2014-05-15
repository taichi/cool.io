package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.builtin.InstanceVariables;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public interface Utils {

	static final Logger LOG = LoggerFactory.getLogger(Utils.class.getName());

	static RubyClass defineClass(Ruby runtime, Class<?> cls, ObjectAllocator oa) {
		return defineClass(runtime, runtime.getObject(), cls, oa);
	}

	static RubyModule getModule(Ruby runtime) {
		return runtime.getModule("Coolio");
	}

	static RubyClass getClass(Ruby runtime, String name) {
		return getModule(runtime).getClass(name);
	}

	static RubyClass defineClass(Ruby runtime, RubyClass parent, Class<?> cls,
			ObjectAllocator oa) {
		RubyModule coolio = getModule(runtime);
		RubyClass rc = coolio.defineClassUnder(cls.getSimpleName(), parent, oa);
		rc.defineAnnotatedMethods(cls);
		return rc;
	}

	static IRubyObject getVar(IRubyObject ro, String key) {
		InstanceVariables vars = ro.getInstanceVariables();
		IRubyObject result = vars.getInstanceVariable(key);
		LOG.info("getVar {} {}", key, result);
		return result;
	}

	static <T extends IRubyObject> T setVar(IRubyObject ro, String key, T value) {
		InstanceVariables vars = ro.getInstanceVariables();
		vars.setInstanceVariable(key, value);
		LOG.info("setVar {} {}", key, value);
		return value;
	}
}
