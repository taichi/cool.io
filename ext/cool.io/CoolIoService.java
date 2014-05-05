import io.cool.IOBuffer;
import io.cool.Loop;
import io.cool.Watcher;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.runtime.load.BasicLibraryService;

/**
 * @author taichi
 */
public class CoolIoService implements BasicLibraryService {

	@Override
	public boolean basicLoad(Ruby runtime) throws IOException {
		IOBuffer.define(runtime);
		Loop.define(runtime);
		Watcher.define(runtime);

		return true;
	}
}
