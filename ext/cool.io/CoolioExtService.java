import io.Buffer;
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
		return true;
	}
}
