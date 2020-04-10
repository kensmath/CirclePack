package browser;

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

/**
 * BrowserUtilities is a static utility class containing convenience methods
 * related to web browsing and network transactions.
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
	 * Attempt to parse a valid URL from a string. This method will attempt
	 * to guess the correct protocol if one is not explicitly stated and
	 * fix common syntax errors.
	 * 
	 * @param url a <code>String</code> representation of a potential URL
	 * @return a syntactically valid <code>URL</code> object (may not point
	 * to a valid resource), or <code>null</code> on failure
	 */
	public static URL parseURL(String url) {
		/*
		 * Here we'll check the most common permutations of a valid URL
		 * in order of likelihood. The function will exit fastest for
		 * the most common errors, and slowest for the rarest errors.
		 */
		URL parsedUrl;
		url = url.trim();

		// Quick and dirty hack: check for the wrong number of slashes in FILE URLs
		// first. The URL constructor doesn't seem to care about the wrong number of
		// slashes, so this method wasn't catching that error because this next block
		// was at the bottom of the method.
		if (url.startsWith("file")) {
			url = url.substring(4);
			while (url.startsWith("/") || url.startsWith(":")) url = url.substring(1);
			try {
				parsedUrl = new URL("file:///" + url);
				return parsedUrl;
			} catch (MalformedURLException e) {}
		}
		
		// Check for initial validity.
		try {
			parsedUrl = new URL(url);
			return parsedUrl;
		} catch (MalformedURLException e) {}

		// Assume the protocol is missing and HTTP protocol is intended,
		// then check for validity.
		try {
			parsedUrl = new URL("http://" + url);
			return parsedUrl;
		} catch (MalformedURLException e) {}

		// Wrong number of slashes is fairly common. First check for
		// leading symbol errors.
		try {
			while (url.startsWith("/") || url.startsWith(":")) url = url.substring(1);
			parsedUrl = new URL("http://" + url);
			return parsedUrl;
		} catch (MalformedURLException e) {}

		// Now check for indicated HTTP protocol, but wrong number of
		// initial slashes.
		if (url.startsWith("http")) {
			url = url.substring(4);
			while (url.startsWith("/") || url.startsWith(":")) url = url.substring(1);
			try {
				parsedUrl = new URL("http://" + url);
				return parsedUrl;
			} catch (MalformedURLException e) {}
		}

		// Who knows what the user did. Return null to indicate failure.
		return null;
	}

	/**
	 * Downloads a file to disk which will be deleted when the Java Virtual Machine
	 * terminates. This method will block until the download is complete.
	 * 
	 * @param target a <code>URL</code> of the temporary file to download
	 * @return a <code>File</code> object pointing to the successfully downloaded
	 * temporary file
	 * @throws IOException on download failure
	 */
	public static File downloadTemporaryFile(URL target) throws IOException {
		// Get the name of the file to download.
		String targetName = new File(target.getPath()).getName();

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

		// Delete the temporary file and create a directory in its place. If
		// an error occurs, throw an exception up.
		if (!temporaryDirectory.delete()) throw new IOException("Failed to delete temporary file " + temporaryDirectory
				+ " in preparation for temporary directory creation!");
		if (!temporaryDirectory.mkdir()) throw new IOException("Failed to create temporary directory " + temporaryDirectory + "!");

		// Get a handle to our new temporary file, and mark both the directory
		// and the file for deletion on exit.
		File temporaryFile = new File(temporaryDirectory, targetName);
		temporaryDirectory.deleteOnExit();
		temporaryFile.deleteOnExit();

		// Open a channel to the remote file.
		ReadableByteChannel rbc;
		try {
			rbc = Channels.newChannel(target.openStream());
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
	 * Returns an HTML representation of a directory. This representation is similar
	 * to Apache web server's directory view.
	 * 
	 * @param directoryPath the path of the directory to render in HTML
	 * @return HTML page representing the directory, or <code>null</code> on failure
	 */
	public static String pageForDirectory(String directoryPath) {
		File directory = new File(directoryPath);
		if (!directory.exists()) return null;

		// Build an HTML page.
		StringBuilder pageText = new StringBuilder();
		pageText.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">");
		pageText.append("<html>");
		pageText.append("<head>");
		pageText.append("<title>");
		pageText.append("Index of ");
		pageText.append(directoryPath);
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
		pageText.append(directoryPath);
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

		// Display a link to the parent directory, if parent exists.
		if (directory.getParent() != null) {
			File parentDirectory = new File(directory.getParent());

			try {
				String href = parentDirectory.toURI().toURL().toString();
				
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
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}

		// Get a list of the contents of this directory.
		File[] directoriesAndFiles = directory.listFiles();
		if (directoriesAndFiles != null) {
			// The directory exists and has contents.
			
			// Sort out directories and files.
			ArrayList<File> directories = new ArrayList<File>();
			ArrayList<File> files = new ArrayList<File>();
			for (File directoryOrFile : directoriesAndFiles) {
				// Don't include hidden directories or files.
				if (directoryOrFile.isHidden()) continue;
				
				if (directoryOrFile.isDirectory())
					directories.add(directoryOrFile);
				else
					files.add(directoryOrFile);
			}

			// Alphabetize directories and files.
			// TODO: Implement this (should already happen under most operating systems, but it isn't guaranteed).

			// Display links to all directories in the current directory.
			for (File currentDirectory : directories) {
				try {
					String href = currentDirectory.toURI().toURL().toString();
					
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
				} catch (MalformedURLException e) {
					e.printStackTrace();
					continue;
				}
			}
			
			// Display links to all files in the current directory.
			for (File currentFile : files) {
				try {
					String href = currentFile.toURI().toURL().toString();

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
				} catch (MalformedURLException e) {
					e.printStackTrace();
					continue;
				}
			}
		}

		pageText.append("<tr>");
		pageText.append("<th colspan=\"4\"><hr></th>");
		pageText.append("</tr>");
		pageText.append("</table>");
		pageText.append("<address>Local File System</address>");
		pageText.append("</body>");
		pageText.append("</html>");

		return pageText.toString();
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
	 * Convert a Unix time stamp to a nicely formatted string. The string will be formatted
	 * similar to the time stamps in the Apache web server directory view.
	 * 
	 * @param time in milliseconds since the epoch
	 * @return <code>String</code> equivalent of time stamp formatted for human readability
	 */
	protected static String readableTimeStamp(long time) {
		// This is matched to Apache. Example: 09-Mar-2009 20:37
		return new SimpleDateFormat("dd-MMM-yyyy HH:mm").format(new Date(time));
	}
}