package util;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import exceptions.InOutException;

/**
 * Static utility routines associated with files, URLs,
 * directories, etc.
 */
public class FileUtil {

	/** 
	 * TODO: this is clutzy, not sure it will work
	 * on other operating system.
	 * 
	 * What is the proper separator? '/' or '\' ?
	 * 
	 * Idea is to form link addresses that are
	 * relative to "user.home"
	 */
	public static String exciseHome(String origstr) {
		String newstr=origstr.toLowerCase().replace("/","\\");
		String home=System.getProperty("user.home").
				toLowerCase().replace("/","\\");
		int k=home.indexOf(File.separator);
		if (k>0 && k<home.length()-1)
			home=home.substring(k);
		k=newstr.indexOf(":");
		if (k>0 && k<newstr.length()-1)
			newstr=newstr.substring(k+1);
		while (newstr.startsWith("\\"))
			newstr=newstr.substring(1);
		while (home.startsWith("\\"))
			home=home.substring(1);
		if (newstr.startsWith(home))
			newstr=newstr.substring(home.length());
		return newstr;
	}
	
	/**
	 * Code from gemini: Determine if 'urlString' is
	 * a local or remote file.
	 * @param urlString String
	 * @return boolean
	 */
	public static boolean isLocal(String urlString) {
		try {
			URI uri = new URI(urlString); // Use URI for more robust parsing
			String scheme = uri.getScheme();
			String host = uri.getHost();

			if (scheme == null) {
				// Handle cases where the URL might be a simple file path
				return !urlString.contains("://"); // If there is no :// it is a local path
			} else if (scheme.equalsIgnoreCase("file")) {
				return true;
			} else if (host == null || host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("0:0:0:0:0:0:0:1")) {
				return true;
			}
			return false;
		} catch (URISyntaxException e) {
			// Handle invalid URL syntax
			return false; // Or throw an exception if appropriate
		}
	}
	
	/**
	 * See if given string converts to valid URL
	 * (via a URI). Return a valid, confirmed URL 
	 * (may not point to a valid, reachable, readable 
	 * resource), or null on failure.
	 * @param string String
	 * @return valid URL or null
	 */
	public static URL tryURL(String string) {
		if (string==null || string.length()==0)
			return null;
		string=string.trim().replace("%20", " ");
		
		URL outURL=null;
		try {
			outURL=(new URL(string));
		} catch(MalformedURLException  mex) {
			return null;
		}
		return outURL;
	}
	
	/**
	 * Attempt to create a valid URL from 
	 * 'urlString'. We first check for direct
	 * creation. If that fails, we attempt to 
	 * provide the correct protocol if one is 
	 * not explicitly stated and try to fix 
	 * common syntax errors, in order of 
	 * likelihood.
	 * 
	 * Return a valid, confirmed URL (may not point
	 * to a valid, reachable, readable resource), 
	 * or null on failure.
	 * 
	 * @param urlString String, potential URL
	 * @return URL or null 
	 */
	public static URL parseURL(String urlString) {

		URL dummy=null;
		String home=System.getProperty("user.home");
		
		// okay as is?
		if ((dummy=tryURL(urlString))!=null)
			return dummy;
		
		// no protocol? try 'file' with home directory. 
		if (!urlString.startsWith("file") && 
				!urlString.startsWith("htt") &&
				!urlString.startsWith("www")) {
			
			// should be local file; start with HomeDirectory
			if (urlString.startsWith("~")) {
				urlString=home+urlString.substring(1);
			}
			else {
				// clean beginning of urlString
				urlString=FileUtil.exciseHome(urlString);
				while (urlString.startsWith("/") || 
						urlString.startsWith(":") ||
						urlString.startsWith("\\")) {
					urlString = urlString.substring(1).trim();
				}
				urlString=home+File.separator+urlString;
			}
			return dummy=FileUtil.tryURL("file:///" + urlString);
		}

		// Quick and dirty: first, check for wrong 
		//   number of slashes in file url's. 
		//   The URL constructor doesn't seem to 
		//   care about the wrong number of slashes, 
		//   so this method wasn't catching that error 
		//   when this next block was at the bottom of 
		//   the method.
		if (urlString.startsWith("file")) { 
			urlString = urlString.substring(4);
			while (urlString.startsWith("/") || 
					urlString.startsWith(":") ||
					urlString.startsWith("\\"))
				urlString = urlString.substring(1).trim();
			urlString=FileUtil.exciseHome(urlString);
			
			return dummy=FileUtil.tryURL(
					"file:///" + home + urlString);
		}
		
		// Reaching here, assume the protocol is missing and 
		//   HTTP protocol is intended, then check for validity.

		if ((dummy=FileUtil.tryURL("http://" + urlString))!=null)
			return dummy;

		// else fall through
		// Reaching here, might be wrong number of slashes,
		//   which is fairly common. First check for leading 
		//   symbol errors.
		while (urlString.startsWith("/") || urlString.startsWith(":")) 
			urlString = urlString.substring(1).trim();
			if ((dummy =FileUtil.tryURL("http://" + urlString))!=null)
				return dummy;

		// else fall through
		// Now check for indicated HTTP protocol, but 
		//  wrong number of initial slashes.
		if (urlString.startsWith("htt") ||
				urlString.startsWith("www")) {
			if (urlString.startsWith("http")) {
				urlString = urlString.substring(4);
				if (urlString.startsWith("s")) 
					urlString=urlString.substring(1);
			}
		}

		while (urlString.startsWith("/") || urlString.startsWith(":")) 
			urlString = urlString.substring(1).trim();
		if ((dummy =FileUtil.tryURL("http://" + urlString))!=null)
			return dummy;

		// else fall through in case we add other checks
		
		// Who knows what the user did: failure, return null
		return null;
	}

	/**
	 * Return the path to a local file when given
	 * a url starting with 'file'.
	 * @param url String
	 * @return String name for file
	 */
	public static String clearFileProtocol(String url) {
		String filepath=url;
		if (url.startsWith("file")) {
			filepath = url.substring(4);
			while (filepath.startsWith("/") || url.startsWith(":")) 
				filepath = filepath.substring(1).trim();
			if (filepath==null || filepath.length()==0)
				return null;
			return filepath;
		}
		return new String(url);
	}
	
	/**
	 * Often need to strip 'file:\\' or 'http:\\' or
	 * 'https:\\' in directory name before it can
	 * be used
	 * @param directory File
	 * @return File, possibly null
	 */
	public static File clearPreName(File directory) {
		String str=directory.getPath();
		if (str.startsWith("file:\\")) 
			str=str.substring(6);
		else if (str.startsWith("http:\\"))
			str=str.substring(7);
		else if (str.startsWith("https:\\"))
			str=str.substring(8);
		
		File newfile;
		try {
			newfile=new File(str);
		} catch(Exception iox) {
			throw new InOutException(
				"problem clearing file prename. "+iox.getMessage());
		}
		return newfile;
	}

	/**
	 * Return list of files in the given directory.
	 */
	public static File[] getFileList(File directory) {
		File nfile=clearPreName(directory);
		return nfile.listFiles();
	}
	
	
	/**
	 * Find characters before and after the last '.' 
	 * in 'filename'. Main name and, E.g., 'html', 'jpg', etc.
	 * @param filename String
	 * @return String[2]: {main string, ext (possibly null)}
	 */
	public static String[] getFilenameExtension(String filename) {
		int n=-1;
		int nn=0;
		while ((nn=filename.indexOf('.',(n+1)))>n) {
			n=nn;
		}
		String[] ans=new String[2];
		  
		// no '.' 
		if (n<0) {
			ans[0]=filename;
			ans[1]=null;
			return ans;
		}
		if (n==filename.length()-1) {
			ans[0]=filename.substring(0,filename.length());
			ans[1]=null;
			return ans;
		}
		ans[0]=filename.substring(0,n);
		ans[1]=filename.substring(n+1);
		return ans;
	}

	/**
	 * 
	 * TODO: this doesn't seem to work well, easily
	 * timing out.
	 * 
	 * Check if a url is contactable, ResponseCode==200
	 * @param url URL
	 * @return boolean
	 */
	public static boolean isContactable(URL url) {
		try {
			if (url.getProtocol().startsWith("htt")) {
				HttpURLConnection huc = (HttpURLConnection) url.openConnection();
		 
				int resCode;
				resCode = huc.getResponseCode();
				if (resCode==200)
					return true;
				return false;
			}
			return false;
		} catch (IOException e) {
			throw new InOutException("failed 'isContactable' attempt");
		}
	}
	
	/**
	 * Convert a file size to a nicely formatted string. 
	 * The string will be formatted similar to the file 
	 * sizes in the Apache web server directory view.
	 * 
	 * @param size long, representation of file size
	 * @return String equivalent of file size formatted 
	 * for human readability
	 */
	public static String readableFileSize(long size) {
	    if (size <= 0) return "0";
	    
	    // Prefixes are none, kilo, mega, giga, tera.
	    final String[] units = new String[] {"", "K", "M", "G", "T"};
	    int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
	    return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + units[digitGroups];
	}

	/**
	 * Convert a Unix time stamp to a nicely formatted string. 
	 * The formating is similar to the time stamps in the Apache 
	 * web server directory view.
	 * 
	 * @param time long, in milliseconds since the epoch
	 * @return String equivalent of time stamp formatted for human readability
	 */
	public static String readableTimeStamp(long time) {
		// This is matched to Apache. Example: 09-Mar-2009 20:37
		return new SimpleDateFormat("dd-MMM-yyyy HH:mm").format(new Date(time));
	}
	
	public static boolean AddressReadable(String urlString) {
		return Files.isReadable(Paths.get(urlString));
	}

}
