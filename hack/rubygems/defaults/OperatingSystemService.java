package rubygems.defaults;

import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.runtime.load.BasicLibraryService;

// for suppress error when using -d options
public class OperatingSystemService implements BasicLibraryService {

	@Override
	public boolean basicLoad(Ruby runtime) throws IOException {
		return false;
	}

}
