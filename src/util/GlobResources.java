package util;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import allMains.CPBase;

/**
 * Getting resources based on glob patterns in names.
 * 
 * @author Bradford Smith
 * 
*/ 
public class GlobResources {

	/**
	 * Return a 'Set' with base file names of all resources in given
	 * directory that match given glob patterns.
	 * NOTE: The glob patterns used here are very simple.  Only '*' (match
	 * any number of characters) and '?' (match exactly one character)
	 * are recognized.
	 * 
	 * @param dir path to a directory to find resources in (e.g. "/Icons/"
	 * @param patterns glob patterns to match files 
	 * @return Set
	 */
	public static Set<String> glob(String dir,List<String> globs) {
		// try to get the url for the directory
		URL dir_url = CPBase.getResourceURL(dir);
		if (dir_url == null) {
			// couldn't find the directory, so return an empty set
			return Collections.emptySet();
		} else if (dir_url.getProtocol().equals("file")) {
			return globFileURL(dir_url, globs);
		} else if (dir_url.getProtocol().equals("jar")) {
			return globJarURL(dir_url, globs);
		} else {
			// URL is a protocol I don't know how to handle
			return Collections.emptySet();
		}
	}

	/**
	 * Convert a list of glob pattern strings into a list of regular
	 * expression pattern strings.
	 * 
	 * @param globs glob patterns to convert
	 * @return new List<String> containing the regular expressions
	 */
	public static ArrayList<String> glob2regexList(List<String> globs) {
		ArrayList<String> regexes = new ArrayList<String>();
		for (String g : globs) {
			regexes.add(glob2regex(g));
		}
		return regexes;
	}

	/**
	 * Convert a glob pattern into a regular expression.
	 * 
	 * NOTE: The only special characters recognized are '*' for "match any
	 * sequence of characters" and '?' for "match any one character.  Also,
	 * these characters cannot be escaped in the pattern.  This keeps the 
	 * code below much simpler, and it seems unlikely that such escaping 
	 * would be useful.
	 * 
	 * @param g glob pattern to convert
	 * @return String containing the equivalent regular expression.
	 */
	public static String glob2regex(String g) {
		// pattern that matches any number of characters that aren't '*' or
		// '?' followed by one of those characters or the end of the string.
		Pattern p = Pattern.compile("([^\\*\\?]*)([\\*\\?]|$)");
		// matcher to look through matches to p in g
		Matcher m = p.matcher(g);
		// I'll build up my regular expression in this string
		StringBuilder regex = new StringBuilder();
		// glob pattern has to start matching at the beginning
		regex.append("^");
		String literal;
		String special;
		while (m.find()) {
			literal = m.group(1); // literal string before special
			special = m.group(2); // "*", "?", or "" (at end of string)
			// quote literal in case it contains special characters
			regex.append(Pattern.quote(literal));
			// convert '*' or '?' to equivalent regular expression
			if (special.equals("*")) {
				regex.append(".*");
			} else if (special.equals("?")){
				regex.append(".");
			} else {
				// must be empty string, nothing to append
			}
		}
		// glob pattern has to match all the way to the end
		regex.append("$");
		return regex.toString();
	}

	/**
	 * Return a list of the base file names that match the given patterns and
	 * can be found in the directory indicated by the given jar URL.
	 * 
	 * @param url jar protocol URL (e.g. "jar:file:/foo/bar.jar!/baz/boff/")
	 * @param globs glob patterns (e.g. ("*.png"))
	 * @return String set containing base file names.
	 */
	static Set<String> globJarURL(URL url, List<String> globs) {
		try {
			Set<String> results = new TreeSet<String>();
			// convert glob patterns with path to regular expressions
			List<String> patterns = glob2regexList(globs);
			JarURLConnection conn = (JarURLConnection)url.openConnection();
			// get the name of the directory from the URL
			String dir = conn.getJarEntry().getName();
			// make sure the directory ends with a '/'
			dir = dir.replaceFirst("/?$", "/");
			// find all entries from the jar file that match the patterns.
			Enumeration<JarEntry> entries = conn.getJarFile().entries();
			String path; // path within the jar file
			while (entries.hasMoreElements()) {
				path = entries.nextElement().getName();
				if (path.startsWith(dir)) {
					String name = path.substring(dir.length(), path.length());
					for (String p : patterns) {
						if (name.matches(p)) {
							results.add(name);
							break;
						}
					}					
				}
			}
			return results;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Collections.emptySet();
	}

	/**
	 * Return a list of the base file names that match the given patterns and
	 * can be found in the directory indicated by the given file URL.
	 * 
	 * @param url jar protocol URL (e.g. "file:/foo/bar/baz/")
	 * @param patterns glob patterns (e.g. ("*.png"))
	 * @return String set containing base file names.
	 */
	private static Set<String> globFileURL(URL url, List<String> patterns) {
		try {
			File dir = new File(url.toURI());
			String[] names = dir.list(new GlobFilter(patterns));
			return new TreeSet<String>(Arrays.asList(names));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return Collections.emptySet();
	}
}
