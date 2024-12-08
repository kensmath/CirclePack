package browser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import input.CPFileManager;
import util.FileUtil;

/**
 * BrowserUtilities is a static utility class 
 * containing convenience methods related to web 
 * browsing and network transactions.
 * 
 * @see BrowserUtilities#parseURL(String)
 * @see BrowserUtilities#downloadTemporaryFile(URL)
 * @author Alex Fawkes
 */
public class BrowserUtilities {
	/**
	 * This is a static utility class and is not meant to be instantiated.
	 */
	private BrowserUtilities() {}

	/**
	 * Attempt to parse a valid URL from 
	 * 'urlString'. This method will attempt to 
	 * guess the correct protocol if one is not 
	 * explicitly stated and will fix common 
	 * syntax errors. We check the most common 
	 * permutations of a valid URL in order of 
	 * likelihood. The function should exit fastest
	 * for the most common errors.
	 * 
	 * TODO: want to have a check for user input of 
	 * file name without explicitly writing "file:/"
	 * at beginning. E.g., look for "~/" or add 
	 * 
	 * @param urlString String, potential URL
	 * @return a valid string for a URL (may not point
	 * to a valid resource), or null on failure
	 */
	public static URL parseURL(String urlString) {

		// NOTE: URL's do not allow spaces, so %20 is used 
		//   (20 being the hexidecimal for 32, which is a space).
		urlString = urlString.trim().replace("%20", " ");

		// one without protocol, try 'HomeDirectory' 
		if (!urlString.startsWith("file") && 
				!urlString.startsWith("htt")) {
			if (urlString.startsWith("~")) {
				urlString=CPFileManager.HomeDirectory+urlString.substring(1);
			}
			else {
				int k=(CPFileManager.HomeDirectory.toString()).length();
				if (urlString.startsWith(CPFileManager.HomeDirectory.toString())) {
					urlString=CPFileManager.HomeDirectory+urlString.substring(k);
				}
			}
			try {
				URL dummy=new URL("file:" + urlString);
				return dummy;
			} catch (MalformedURLException e) {
				return null;
			}
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
			while (urlString.startsWith("/") || urlString.startsWith(":")) 
				urlString = urlString.substring(1).trim();
			try {
				URL dummy=new URL("file:" + urlString);
				return dummy;
			} catch (MalformedURLException e) {
				return null;
			}
		}
		
		// If initially valid, return
		try {
			URL dummy=new URL(urlString);
			return dummy;
		} catch (MalformedURLException e) {	
			// fall through if not already a valid url string
		}

		// Reaching here, assume the protocol is missing and 
		//   HTTP protocol is intended, then check for validity.
		try {
			URL dummy = new URL("http://" + urlString);
			return dummy;
		} catch (MalformedURLException e) {
			// fall through
		}

		// Reaching here, might be wrong number of slashes,
		//   which is fairly common. First check for leading 
		//   symbol errors.
		try {
			while (urlString.startsWith("/") || urlString.startsWith(":")) 
				urlString = urlString.substring(1).trim();
			URL dummy = new URL("http://" + urlString);
			return dummy;
		} catch (MalformedURLException e) {
			// fall through
		}

		// Now check for indicated HTTP protocol, but 
		//  wrong number of initial slashes.
		if (urlString.startsWith("http")) {
			urlString = urlString.substring(4);
			while (urlString.startsWith("/") || urlString.startsWith(":")) 
				urlString = urlString.substring(1).trim();
			try {
				URL dummy = new URL("http://" + urlString);
				return dummy;
			} catch (MalformedURLException e) {
				// fall through in case we add other checks
			}
		}

		// Who knows what the user did: failure, return null
		return null;
	}

	/**
	 * Downloads a file locally which will be deleted when 
	 * the Java Virtual Machine terminates. This method will 
	 * block until the download is complete.
	 * 
	 * @param tar String, url string of file to download
	 * @return File, the stored temporary file
	 * @throws IOException on download failure
	 */
	public static File downloadTemporaryFile(String tar) 
		throws IOException { 

		// Get the name of the file to download.
		String target=parseURL(tar).getPath();
		String targetName = new File(target).getName();

		// Get a unique temporary file and use that name to create a unique
		// temporary directory instead. Then download the file to the unique
		// temporary directory to preserve the original file name.
		File temporaryDirectory;
		try {
			// Use the target name as the prefix and a blank suffix.
			temporaryDirectory = File.createTempFile(targetName, "");
		} catch (IOException e) {
			// Couldn't create a temporary file. Throw the exception up.
			throw e;
		}

		// Delete the temporary file and create 
		//   a directory in its place. If an error 
		//   occurs, throw an exception up.
		if (!temporaryDirectory.delete()) throw new IOException(
				"Failed to delete temporary file " + temporaryDirectory
				+ " in preparation for temporary directory creation!");
		if (!temporaryDirectory.mkdir()) throw new IOException(
				"Failed to create temporary directory " + temporaryDirectory + "!");

		// Get a handle to our new temporary file, and mark both 
		//   the directory and the file for deletion on exit.
		File temporaryFile = new File(temporaryDirectory, targetName);
		temporaryDirectory.deleteOnExit();
		temporaryFile.deleteOnExit();

		// Open a channel to the remote file.
		ReadableByteChannel rbc;
		try {
			rbc = Channels.newChannel(new URL(target).openStream());
		} catch (IOException e) {
			// Failed to open a channel. Throw the exception up.
			throw e;
		}

		// Create an output stream to the local temporary file.
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(temporaryFile);
		} catch (FileNotFoundException e1) {
			// Through dark sorcery, there is a directory with the exact name of our
			// file in our brand new temporary directory. This should never happen,
			// but if it does, close the channel and throw the exception up.

			// If closing the channel fails, just ignore it. There's nothing we can do,
			// and the prior exception is more important.
			try {
				rbc.close();
			} catch (IOException e2) {}

			throw e1;
		}

		// Download the file using channels.
		try {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		} catch (IOException e1) {
			// The download failed. Close our channels and throw the exception up.
			// If closing channels fails, just ignore it. There's nothing we can do,
			// and the prior exception is more important.
			try {
				rbc.close();
			} catch (IOException e2) {}
			try {
				fos.close();
			} catch (IOException e2) {}

			throw e1;
		}

		// Close the resource handles. If either fail, just ignore it. It's more
		// important to get the successfully downloaded file to the caller.
		try {
			rbc.close();
		} catch (IOException e) {}
		try {
			fos.close();
		} catch (IOException e) {}

		return temporaryFile;
	}

	/**
	 * Returns the URL temporary file with HTML 
	 * representation of a directory, similar to Apache 
	 * web server's directory view. The directory
	 * may be local or on the web.
	 * @param directoryURL URL, source URL
	 * @return URL, null on failure 
	 */
	public static URL pageForDirectory(URL directoryURL) {

		File directory = new File(directoryURL.getFile());
		if (!directory.exists()) // directory.getPath()
			return null; // directory.getPath();
		if (!directory.isDirectory())
			return null;

		// Build an HTML page.
		StringBuilder pageText = new StringBuilder();
		pageText.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">");
		pageText.append("<html>");
		pageText.append("<head>");
		pageText.append("<title>");
		pageText.append("Index of ");
		pageText.append(directoryURL.toString());
		pageText.append("</title>");
		pageText.append("<style>");
		pageText.append(".type_label {");
		pageText.append("font-size: 0.9em;");
		pageText.append("font-weight: bold;");
		pageText.append("}");
		pageText.append("</style>");
		pageText.append("</head>");
		pageText.append("<body>");
		pageText.append("<h1>");
		pageText.append("Index of ");
		pageText.append(directoryURL.toString());
		pageText.append("</h1>");
		pageText.append("<table>");
		pageText.append("<tr>");
		pageText.append("<td></td>");
		pageText.append("<td>Name</td>");
		pageText.append("<td>Last modified</td>");
		pageText.append("<td>Size</td>");
		pageText.append("</tr>");
		pageText.append("<tr>");
		pageText.append("<th colspan=\"4\"><hr></th>");
		pageText.append("</tr>");

		String href = directoryURL.toString();
				
		pageText.append("<tr>");
		pageText.append("<td class=\"type_label\">[DIR]</td>");
		pageText.append("<td>");
		pageText.append("<a href=\"");
		pageText.append(href);
		pageText.append("\">");
		pageText.append("Parent Directory");
		pageText.append("</a>");
		pageText.append("</td>");
		// No modified date for directories.
		pageText.append("<td></td>");
		// No file size for directories.
		pageText.append("<td>-</td>");
		pageText.append("</tr>");

		// Get a list of the contents of this directory.
		File[] directoriesAndFiles = 
				FileUtil.getFileList(directory);
		if (directoriesAndFiles != null) {
			// The directory exists and has contents.
			
			// Sort out directories and files.
			ArrayList<File> directories = new ArrayList<File>();
			ArrayList<File> files = new ArrayList<File>();
			for (File directoryOrFile : directoriesAndFiles) {
				// Don't include hidden directories or files.
				if (directoryOrFile.isHidden()) 
					continue;
				
				if (directoryOrFile.isDirectory())
					directories.add(directoryOrFile);
				else
					files.add(directoryOrFile);
			}

			// TODO: Alphabetize? Implement this (should already 
			//   happen under most operating systems, but 
			//   it isn't guaranteed).

			// Display links to all directories in the current directory.
			for (File currentDirectory : directories) {

				href = currentDirectory.toString();
					
				pageText.append("<tr>");
				pageText.append("<td class=\"type_label\">[DIR]</td>");
				pageText.append("<td>");
				pageText.append("<a href=\"");
				pageText.append(href);
				pageText.append("\">");
				pageText.append(currentDirectory.getName());
				pageText.append("</a>");
				pageText.append("</td>");
				// No modified date for directories.
				pageText.append("<td></td>");
				// No file size for directories.
				pageText.append("<td>-</td>");
				pageText.append("</tr>");
			}
			
			// Display links to all files in the current directory.
			for (File currentFile : files) {

				href = currentFile.toString();

				pageText.append("<tr>");
				pageText.append("<td class=\"type_label\">[FILE]</td>");
				pageText.append("<td>");
				pageText.append("<a href=\"");
				pageText.append(href);
				pageText.append("\">");
				pageText.append(currentFile.getName());
				pageText.append("</a>");
				pageText.append("</td>");
				pageText.append("<td>");
				pageText.append(readableTimeStamp(currentFile.lastModified()));
				pageText.append("</td>");
				pageText.append("<td>");
				pageText.append(readableFileSize(currentFile.length()));
				pageText.append("</td>");
				pageText.append("</tr>");
			}
		}

		pageText.append("<tr>");
		pageText.append("<th colspan=\"4\"><hr></th>");
		pageText.append("</tr>");
		pageText.append("</table>");
		pageText.append("<address>Local File System</address>");
		pageText.append("</body>");
		pageText.append("</html>");

		// put into file
		BufferedWriter writer=null;
		String filename="myScripts.html";
		File dir=null;
		try {
			dir=new File(System.getProperty("java.io.tmpdir"),
					directory.getName()); // dir.exists()
			if (dir.exists()) {
				dir.delete();
			}
			dir.mkdir();
	
			writer = CPFileManager.
			openWriteFP(dir,false,filename,false);
			writer.write(pageText.toString());
		} catch (IOException ex) {
			// Print messqage exception occurred as
			// invalid. directory local path is passed
			System.err.print("save problems: "+ex.getMessage());
			return null;
		}
		try {
			if (writer!=null) {
				writer.flush();
				writer.close();
			}
		} catch(IOException ex) {
			System.err.println("problem with 'writer': "+ex.getMessage());
		}

		URL outURL=null;
		try {
			outURL=new URL("file://"+dir.getPath()+"/"+filename);
		} catch (MalformedURLException e) {
			System.err.println("failed to save directory results");
		}
		
		return outURL;
	}
		
	/**
	 * Convert a file size to a nicely formatted string. The string will be formatted
	 * similar to the file sizes in the Apache web server directory view.
	 * 
	 * @param size <code>long</code> representation of file size
	 * @return <code>String</code> equivalent of file size formatted for human readability
	 */
	protected static String readableFileSize(long size) {
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
	 * @param time in milliseconds since the epoch
	 * @return <code>String</code> equivalent of time stamp formatted for human readability
	 */
	protected static String readableTimeStamp(long time) {
		// This is matched to Apache. Example: 09-Mar-2009 20:37
		return new SimpleDateFormat("dd-MMM-yyyy HH:mm").format(new Date(time));
	}
}