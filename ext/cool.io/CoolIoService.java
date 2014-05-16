import io.cool.Coolio;
import io.cool.IO;
import io.cool.IOBuffer;
import io.cool.Loop;
import io.cool.Socket;
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
		Coolio.load(runtime);

		IOBuffer.load(runtime);
		IO.load(runtime);
		Loop.load(runtime);
		Watcher.load(runtime); // load servers
		Socket.load(runtime);

		return true;
	}
}