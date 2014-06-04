package io.cool;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.anno.JRubyConstant;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * listener.rb on JRuby <br/>
 * UNIXListener is not support
 * 
 * @author taichi
 */
public class Listener extends IOWatcher {

	private static final long serialVersionUID = -2281643801267613411L;

	private static final Logger LOG = LoggerFactory.getLogger(Listener.class
			.getName());

	public Listener(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyConstant
	public static final int DEFAULT_BACKLOG = 1024;

	@JRubyMethod
	public IRubyObject listen(IRubyObject backlog) {
		LOG.info("listen backlog={}", backlog);
		this.io.callMethod("listen", backlog);
		return getRuntime().getNil();
	}

	@JRubyMethod
	public IRubyObject close() {
		LOG.info("close");
		if (getRuntime().getTrue().equals(isAttached())) {
			detach();
		}
		return getRuntime().getNil();
	}
}
