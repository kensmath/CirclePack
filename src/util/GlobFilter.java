package util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

/**
 * Object to filter file names by a list of glob patterns.
 * 
 * @author Bradford Smith
 */
class GlobFilter implements FilenameFilter {
	private List<String> patterns;

	public GlobFilter(List<String> globs) {
		patterns = GlobResources.glob2regexList(globs);
	}

	@Override
	public boolean accept(File dir, String name) {
		for (String p : patterns) {
			if (name.matches(p)) return true;
		}
		return false;
	}
}