package io.cool;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyObject;

/**
 * @author taichi
 */
public class IOBuffer extends RubyObject {
	// TODO purge this class

	private static final long serialVersionUID = -4361435260503933077L;

	public static void load(Ruby runtime) throws IOException {
		runtime.getIO().defineClassUnder("Buffer", runtime.getObject(),
				IOBuffer::new);
	}

	public IOBuffer(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

}