import io.Buffer;
import io.cool.Coolio;
import io.cool.Loop;
import io.cool.Watcher;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.runtime.load.BasicLibraryService;

/**
 * @author taichi
 */
public class CoolioExtService implements BasicLibraryService {

	@Override
	public boolean basicLoad(Ruby runtime) throws IOException {
		Buffer.load(runtime);

		Coolio.load(runtime);

		Loop.load(runtime);
		Watcher.load(runtime);

		return true;
	}
}
