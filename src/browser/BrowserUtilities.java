package browser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;

import exceptions.InOutException;
import input.CPFileManager;
import util.FileUtil;

/**
 * BrowserUtilities is a static utility class 
 * containing convenience methods related to web 
 * browsing and network transactions.
 * 
 * @see FileUtil#parseURL(String)
 * @see BrowserUtilities#downloadTemporaryFile(URL)
 * @author Alex Fawkes
 */
public class BrowserUtilities {
	/**
	 * This is a static utility class and is not meant to be instantiated.
	 */
	private BrowserUtilities() {}

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
		String target=FileUtil.parseURL(tar).getPath();
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
		URL osURL=FileUtil.tryURL(tar);
		if (osURL==null) 
			throw new InOutException("failed to open 'target' temp file");

		try {
			rbc = Channels.newChannel(osURL.openStream());
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
	 * representation of a directory, similar 
	 * to what web servers present. 
	 * @param directoryURL URL, source URL
	 * @return URL, null on failure 
	 */
	public static URL pageForDirectory(URL directoryURL) {

		// could let server handle this automatically for
		//   non-local directories. But let's try to do
		//   that internally.
//		if (!directoryURL.getProtocol().startsWith("file"))
//			return directoryURL; // formated by server
		
		// check for directory 
		File directory = new File(directoryURL.getFile());
		if (!directory.exists() || !directory.isDirectory())
			return null;

		// clean up the name of the directory
		String dirPath=directoryURL.getPath();
		int n=dirPath.indexOf(":");
		if (n>0 && dirPath.startsWith("/")) // annoying leading /
			dirPath=dirPath.substring(1);

		// get name of the parent directory
		String parentPath=dirPath;
		parentPath=FileUtil.cleardotdot(dirPath);
		while (parentPath.endsWith("/"))
			parentPath=parentPath.substring(0,parentPath.length()-1);
		n=parentPath.indexOf(":");
		if (n>0 && parentPath.startsWith("/")) // annoying leading /
			parentPath=parentPath.substring(1);
		n=parentPath.lastIndexOf("/");
		parentPath=parentPath.substring(0,n);
		if (parentPath.charAt(parentPath.length()-1)!='/')
			parentPath=parentPath+"/";

		// Build the HTML page.
		StringBuilder pageText = new StringBuilder();
		pageText.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
		pageText.append("<html>\n");
		
		pageText.append("<head> \n"+
		  "\t<meta charset=\"utf-8\"> \n"+
		  "\t<meta name=\"color-scheme\" content=\"light dark\"> \n"+
		  "\t<meta name=\"google\" value=\"notranslate\">\n");
		
		pageText.append("<script> \n"+
		  "function addRow(name, url, isdir, size, size_string,"+
			"date_modified,date_modified_string) {\n"+
		      "\tif (name==\".\" || name == \"..\")\n"+
			  "\t\treturn;\n\n");

		pageText.append("\tvar root=\""+dirPath+"\";\n"+
				"\tif (root.substr(-1) !==\"/\")\n"+
				"\t\troot += \"/\";\n\n");

		pageText.append("\tvar tbody = document.getElementById(\"tbody\");\n"+
		    "\tvar row = document.createElement(\"tr\");\n"+
		    "\tvar file_cell = document.createElement(\"td\");\n"+
		    "\tvar link = document.createElement(\"a\");\n\n");
		    
		pageText.append("\tlink.className = isdir ? \"icon folder\" : \"icon file\";\n\n");

		pageText.append("\tif(isdir) {\n"
				+ "\t\tname = name + \"/\";\n"
				+ "\t\turl = url + \"/\";\n"
				+ "\t\tsize = 0;\n"
				+ "\t\tsize_string = \"\";\n"
				+ "\t}\n\n");

		pageText.append("\t// get name\n"
		      + "\tlink.innerText = name;\n"
		      + "\tlink.href = root + url;\n\n");

		pageText.append("\tfile_cell.dataset.value = name;\n"
			  + "\tfile_cell.appendChild(link);\n\n");

		pageText.append("\trow.appendChild(file_cell);\n"
				+ "\trow.appendChild(createCell(size,size_string));\n"
				+ "\trow.appendChild(createCell(date_modified, date_modified_string));\n\n");

		pageText.append("\ttbody.appendChild(row);\n"
				+ "}\n\n");

		pageText.append("function createCell(value, text) {\n"
				+ "\tvar cell=document.createElement(\"td\")\n"
				+ "\tcell.setAttribute(\"class\", \"detailsColumn\");\n"
				+ "\tcell.dataset.value = value;\n"
				+ "\tcell.innerText = text;\n"
				+ "\treturn cell;\n"
				+ "}\n\n");

		pageText.append("function start(location) {\n"
				+ "\tvar header = document.getElementById(\"header\");\n"
				+ "\theader.innerText = header.innerText.replace(\"LOCATION\", location);\n");
			
		pageText.append("\tdocument.getElementById(\"title\").innerText = header.innerText;\n"
				+ "}\n\n");

		pageText.append("function onHasParentDirectory() {\n"
		        + "\tvar box = document.getElementById(\"parentDirLinkBox\");\n"
		        + "\tbox.style.display = \"block\";\n"
		        + "\tvar root = document.location.pathname;\n"
				+ "\tif (root.substr(-1) !==\"/\")\n"
				+ "\t\troot += \"/\";\n\n");

		pageText.append("\tvar link = document.getElementById(\"parentDirLink\");\n"
				+ "\tlink.href = root + \"..\";\n"
				+ "}\n\n");

		pageText.append("function sortTable(column) {\n"
				+ "\tvar theader = document.getElementById(\"theader\");\n"
				+ "\tvar oldOrder = theader.cells[column].dataset.order || '1';\n"
				+ "\toldOrder = parseInt(oldOrder, 10);\n"
				+ "\tvar newOrder = 0 - oldOrder;\n"
				+ "\ttheader.cells[column].dataset.order = newOrder;\n\n");

		pageText.append("\tvar tbody = document.getElementById(\"tbody\");\n"
				+ "\tvar rows = tbody.rows;\n"
				+ "\tvar list = [],i;\n"
				+ "\tfor (i = 0; i < rows.length; i++) {\n"
				+ "\t\tlist.push(rows[i]);\n"
				+ "\t}\n\n");

		pageText.append("\tlist.sort(function(row1,row2) {\n"
				+ "\t\tvar a = row1.cells[column].dataset.value;\n"
				+ "\t\tvar b = row2.cells[column].dataset.value;\n"
				+ "\t\tif (column) {\n"
				+ "\t\t\ta = parseInt(a,10);\n"
				+ "\t\t\tb = parseInt(b,10);\n"
				+ "\t\t\treturn a>b ? newOrder : a<b ? oldOrder : 0;\n"
				+ "\t\t}\n\n"); 

		pageText.append("\t// Column 0 is text.\n"
				+ "\tif (a>b)\n"
				+ "\t\treturn newOrder;\n"
				+ "\tif (a<b)\n"
				+ "\t\treturn oldOrder;\n"
				+ "\treturn 0;\n"
				+ "});\n\n");

		pageText.append("\t// Appending an existing child again just moves it.\n"
				+ "\tfor (i = 0; i < list.length; i++) {\n"
				+ "\t\ttbody.appendChild(list[i]);\n"
				+ "\t}\n"
				+ "} // end of sortTable\n\n");

		pageText.append("// Add event handlers to column headers.\n"
				+ "function addHandlers(element, column) {\n"
				+ "\t\telement.onclick = (e) => sortTable(column);\n"
				+ "\t\telement.onkeydown = (e) => {\n"
				+ "\t\t\tif (e.key == 'Enter' || e.key == ' ') {\n"
				+ "\t\t\t\tsortTable(column);\n"
				+ "\t\t\t\te.preventDefault();\n"
				+ "\t\t\t}\n"
				+ "\t\t};\n"
				+ "}\n\n");

		pageText.append("function onLoad() {\n"
				+ "\taddHandlers(document.getElementById('nameColumnHeader'),0);\n"
				+ "\taddHandlers(document.getElementById('sizeColumnHeader'),1);\n"
				+ "\taddHandlers(document.getElementById('dateColumnHeader'),2);\n"
				+ "}\n\n");

		pageText.append("window.addEventListener('DOMContentLoaded',onLoad);\n"
				+ "</script>\n\n");
		
		pageText.append("<style>\n"
				+ "\th1 {\n"
				+ "\t\tborder-bottom: 1px solid #c0c0c0;\n"
				+ "\t\tmargin-bottom: 10px;\n"
				+ "\t\tpadding-bottom: 10px;\n"
				+ "\t\twhite-space: nowrap;\n"
				+ "\t}\n\n");
		
		pageText.append("\ttable {\n"
				+ "\t\tborder-collapse: collapse;\n"
				+ "\t}\n\n");
		
		pageText.append("\tth {\n"
				+ "\t\tcursor: pointer;\n"
				+ "\t}\n\n");

		pageText.append("\ttd.detailsColumn {\n"
				+ "\t\tpadding-inline-start: 2em;\n"
				+ "\t\tpadding-bottom: 2px;\n"
				+ "\t\ttext-align: end;\n"
				+ "\t\twhite-space: nowrap;\n"
				+ "\t}\n\n");

		pageText.append("\ta.icon {\n"
				+ "\t\tpadding-inline-start: 1.5em;\n"
				+ "\t\ttext-decoration: none;\n"
				+ "\t\tpadding-bottom: 3px;\n"
				+ "\t\tuser-select: auto;\n"
				+ "\t}\n\n");
				
		pageText.append("\ta.icon:hover {\n"
				+ "\t\ttext-decoration: underline;\n"
				+ "\t}\n\n");

		pageText.append("\ta.file {\n"
				+ "\t\tbackground : url(\"data:image/gif;base64,R0lGODlhFAAWAMIAAP///8z//5mZmTMzMwAAAAAAAAAAAAAAACH+TlRoaXMgYXJ0IGlzIGluIHRoZSBwdWJsaWMgZG9tYWluLiBLZXZpbiBIdWdoZXMsIGtldmluaEBlaXQuY29tLCBTZXB0ZW1iZXIgMTk5NQAh+QQBAAABACwAAAAAFAAWAAADWDi6vPEwDECrnSO+aTvPEddVIriN1wVxROtSxBDPJwq7bo23luALhJqt8gtKbrsXBSgcEo2spBLAPDp7UKT02bxWRdrp94rtbpdZMrrr/A5+8LhPFpHajQkAOw==\") left top no-repeat;\n"
				+ "\t}\n\n");
	
		pageText.append("\ta.back {\n"
				+ "\t\tbackground : url(\"data:image/gif;base64,R0lGODlhFAAWAMIAAP///8z//5mZmWZmZjMzMwAAAAAAAAAAACH+TlRoaXMgYXJ0IGlzIGluIHRoZSBwdWJsaWMgZG9tYWluLiBLZXZpbiBIdWdoZXMsIGtldmluaEBlaXQuY29tLCBTZXB0ZW1iZXIgMTk5NQAh+QQBAAABACwAAAAAFAAWAAADSxi63P4jEPJqEDNTu6LO3PVpnDdOFnaCkHQGBTcqRRxuWG0v+5LrNUZQ8QPqeMakkaZsFihOpyDajMCoOoJAGNVWkt7QVfzokc+LBAA7\") left top no-repeat;\n"
				+ "\t}\n\n");

	
		pageText.append("\ta.folder {\n"
				+ "\t\tbackground : url(\"data:image/gif;base64,R0lGODlhFAAWAMIAAP/////Mmcz//5lmMzMzMwAAAAAAAAAAACH+TlRoaXMgYXJ0IGlzIGluIHRoZSBwdWJsaWMgZG9tYWluLiBLZXZpbiBIdWdoZXMsIGtldmluaEBlaXQuY29tLCBTZXB0ZW1iZXIgMTk5NQAh+QQBAAACACwAAAAAFAAWAAADVCi63P4wyklZufjOErrvRcR9ZKYpxUB6aokGQyzHKxyO9RoTV54PPJyPBewNSUXhcWc8soJOIjTaSVJhVphWxd3CeILUbDwmgMPmtHrNIyxM8Iw7AQA7\") left top no-repeat;\n"
				+ "\t}\n\n");

		pageText.append("\ta.unknown {\n"
				+ "\t\tbackground : url(\"data:image/gif;base64,R0lGODlhFAAWAMIAAP///8z//5mZmTMzMwAAAAAAAAAAAAAAACH+TlRoaXMgYXJ0IGlzIGluIHRoZSBwdWJsaWMgZG9tYWluLiBLZXZpbiBIdWdoZXMsIGtldmluaEBlaXQuY29tLCBTZXB0ZW1iZXIgMTk5NQAh+QQBAAABACwAAAAAFAAWAAADaDi6vPEwDECrnSO+aTvPEQcIAmGaIrhR5XmKgMq1LkoMN7ECrjDWp52r0iPpJJ0KjUAq7SxLE+sI+9V8vycFiM0iLb2O80s8JcfVJJTaGYrZYPNby5Ov6WolPD+XDJqAgSQ4EUCGQQEJADs=\") left top no-repeat;\n"
				+ "\t}\n\n");

		pageText.append("\ta.blank {\n"
				+ "\t\tbackground : url(\"data:image/gif;base64,R0lGODlhFAAWAKEAAP///8z//wAAAAAAACH+TlRoaXMgYXJ0IGlzIGluIHRoZSBwdWJsaWMgZG9tYWluLiBLZXZpbiBIdWdoZXMsIGtldmluaEBlaXQuY29tLCBTZXB0ZW1iZXIgMTk5NQAh+QQBAAABACwAAAAAFAAWAAACE4yPqcvtD6OctNqLs968+w+GSQEAOw==\") left top no-repeat;\n"
				+ "\t}\n\n");

		pageText.append("\thtml[dir=rtl] a {\n"
				+ "\t\tbackground-position-x: right;\n"
				+ "\t}\n\n");

		pageText.append("\t#parentDirLinkBox {\n"
				+ "\t\tmargin-bottom: 10px;\n"
				+ "\t\tpadding-bottom: 10px;\n"
				+ "\t}\n\n");

		pageText.append("</style>\n\n");
				
		pageText.append("\t<title id=\"title\">Index of "+dirPath+"</title>\n\n"
				+ "</head>\n\n");

		pageText.append("<body>\n\n");

		// set the top header line
		pageText.append("\t<h1 id=\"header\">Index of "+dirPath+"</h1>\n"
				+ "\t\t<div id=\"parentDirLinkBox\" style=\"display: block;\">\n"
				+ "\t\t\t<a id=\"parentDirLink\" class=\"icon back\" href=\""+parentPath+"\">\n"
//				+ "\t\t\t<span id=\"parentDirText\">[parent directory]</span>\n"
				+ "\t\t\t</a>\n"
				+ "\t\t</div>\n\n");
		
		pageText.append("\n<table>\n"
				+ "\t\t<thead>\n"
				+ "\t\t\t<tr class=\"header\" id=\"theader\">\n"
				+ "\t\t\t\t<th id=\"nameColumnHeader\" tabindex=\"0\" role=\"button\">Name</th>\n"
				+ "\t\t\t\t<th id=\"sizeColumnHeader\" class=\"detailsColumn\" tabindex=\"1\" role=\"button\"> Size </th>\n"
				+ "\t\t\t\t<th id=\"dateColumnHeader\" class=\"detailsColumn\" tabindex=\"2\" role=\"button\"> Date Modified </th>\n"
				+ "\t\t\t</tr>\n"
				+ "\t\t</thead>\n\n");

		
		
		pageText.append("<tbody id=\"tbody\">\n\n");
		pageText.append("</tbody>\n\n"
				+ "</table>\n\n");


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
	
			// Display links to all directories in the current directory.
			for (File dir : directories) {
				pageText.append(encodeDirFile(dir,1)+"\n");
			}
			
			// Display links to all files in the current directory.
			for (File file : files) {
				pageText.append(encodeDirFile(file,0)+"\n");
			}
			
			pageText.append("</body>\n"
					+ "</html>\n");
		}

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

		return FileUtil.tryURL("file:///"+dir.getPath()+"/"+filename);
	}
	
	public static String encodeDirFile(File file,int type) {
		String flong=Long.toString((long)file.length());
		String ftime=Long.toString((long)file.lastModified());
		int t=Integer.valueOf(type);
		String fsize=FileUtil.readableFileSize((long)file.length());
		String mtime=FileUtil.readableTimeStamp((long)file.lastModified());
 
 		StringBuilder stbld=new StringBuilder("<script>addRow(");
		stbld.append("\""+file.getName()+"\",\""+file.getName()+"\","+t
				+ ","+flong+",\""+fsize+"\","+ftime+",\""+mtime+"\");</script>");
 		
		return stbld.toString();
	}

}