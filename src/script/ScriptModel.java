package script;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import org.apache.xerces.parsers.DOMParser;

import allMains.CPBase;
import allMains.CirclePack;
import exceptions.InOutException;
import exceptions.ParserException;
import input.CPFileManager;
import input.FileDialogs;
import util.Base64InOut;
import util.FileUtil;

/**
 * This is the model behind CirclePack scripts. It loads script
 * files, storing the data files, creating the workingFile, 
 * setting up the DOM document.
 * For GUI creation and interaction, see 'ScriptManager'.
 * TODO: various actions need both standalone and GUI versions.
 * @author kens
 */
public class ScriptModel {

	// holds the document obtained from the script
	public static org.w3c.dom.Document doc=null;
	File workingFile; // working copy of script
	
	// Script data
	public String scriptName; // name of current script (whether file or URL)
	public String creationDate;  // TODO: not yet implemented: null until first save, then fixed
	public String scriptDescription; // from <description> element

	// data files which are included in script
	public Vector<IncludedFile> includedFiles=new Vector<IncludedFile>();

	protected int id; // random identifier for tmp file names
	boolean editEnabled;
	public boolean hasChanged;

	// Static variables
	public static int cmdCount=0;

	// Constructors
	public ScriptModel() {
		doc=null;
		scriptDescription=null;
		editEnabled = false;
		id = new Random().nextInt(32000);
		hasChanged = false;
	}
	
	/**
	 * This loads from a file (generally 'manager.scriptFile').
	 * This is only called after user has chance to save the current 
	 * script (if it has changed).
	 * @param url URL of script
	 * @return true if the load seemed to work.
	 */
	public org.w3c.dom.Document loadScriptDOM(URL url) {
		
		org.w3c.dom.Document newDoc=null;

		try {
			
			// create working copy 'manager.workingFile', pick off included data files
			if (createWorkingFile(url)==0) {
				CirclePack.cpb.errMsg("Encountered error in loading the script.");
				return null;
			}
			URL workingURL=FileUtil.tryURL("file:"+ workingFile.getCanonicalPath());
			if (workingURL==null)
				return null;
			
			DOMParser parser = new DOMParser();
			newDoc=null;
			try {
				parser.parse(workingURL.toString());
				newDoc = parser.getDocument();
			} catch(Exception ex) {
				CirclePack.cpb.myErrorMsg("Caught (probably) SAXParseException (for XML parsing) in loadng script.\n"+	
						ex.getMessage());
				ex.printStackTrace(System.err);
				return null;
			} 
		} catch (Exception exc) {
			CirclePack.cpb.myErrorMsg("Exception in loadScript on startup.\n"+exc.getMessage());
			exc.printStackTrace(System.err);
			return null;
		}

		// if appropriate, reset 'scriptDirectory'
		// TODO: past (?) problem: resets based on default loading of 'new_script.xmd'
		if (url.getProtocol().equals("file")) { 
			String cpath=url.getPath();
			int k=cpath.lastIndexOf('/');
			if (!cpath.contains("new_script.xmd") && k>0) {
				// change if it is not just /tmp
				if (!cpath.substring(0,k).startsWith(System.getProperty("java.io.tmpdir")))  
					CPFileManager.ScriptDirectory =new File(cpath);
			}
		}
		
		return newDoc;
	}
	
	/**
	 * Use: Copy the original script file or web data into a working file 
	 * in XML format. The original is unaltered until a save action takes place. 
	 * This method calls processIncludedFiles() when the data section has been
	 * reached so that the included files can be exported for later reinclusion.
	 * @param url URL
	 * @return 0 on error.
	 */
	public int createWorkingFile(URL url) {
		BufferedReader reader;
		try {
			reader = new BufferedReader(new InputStreamReader(url.openStream()));
		} catch (IOException e) {
			System.err.println("IOException in ScriptHandler.createWorkingFile().");
			return 0;
		}
		workingFile = new File(System.getProperty("java.io.tmpdir"),
				new String(id + scriptName));
		workingFile.deleteOnExit();

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(
					workingFile));
			String line;
			includedFiles.removeAllElements();

			while ((line = reader.readLine()) != null) {
				if (line.indexOf("<CPdata>") != -1) {
					writer.write("<CPdata>");
					writer.newLine();
					processIncludedFiles(reader);
					writeFileNames(writer);
					writer.write("</CPdata>");
					writer.newLine();
				} else {
					writer.write(line);
					writer.newLine();
				}
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			System.err.println("IOException in 'ScriptHandler.createWorkingFile()'");
			return 0;
		}
		return 1;
	}

	
	/**
	 * Use: This method is responsible for processing the 'CPdata' section of a
	 * script and creating the vector of 'includedFiles'.
	 * 
	 * When it finds a file, it creates an 'IncludedFile' object, which keeps
	 * track of the original filename and 'tmpFile' in system temp directory 
	 * prepended with the current script 'id'. Use 'includedFiles'
	 * so that save operations can reincorporate the original (or new added) data.
	 * @param reader @see BufferedReader
	 */
	protected void processIncludedFiles(BufferedReader reader)
		throws IOException {
		String line;
		String orig_name="";
		String newName=null;
		File temp;
		int safeID=new Random().nextInt(32000);
		
		while ((line = reader.readLine()) != null) {
			
			// shuck lines until we reach a tagged line
			while (line!=null && line.indexOf("<")==-1)
				line=reader.readLine();
			if (line==null || (line.indexOf("</CPdata>")) != -1) 
				return;
		
			// else, starting with the tagged line
			PrintWriter tempWriter;

			// Have to check deprecated name style: i.e., <name>..</name> tag
			if (line.indexOf("<name>")!=-1) {
				orig_name = line.substring(line.indexOf(">") + 1, 
						line.indexOf("</"));
				orig_name = orig_name.trim();
				if (orig_name.length()==0)
					orig_name = new String("notnamed"+includedFiles.size());
				// read through whitespace lines
				while ((line=reader.readLine()) !=null && line.indexOf("<")==-1);
				// didn't find any subsequent tag?
				if (line==null) 
					return;
			}

			// reaching here, should have the initial tag line of tagged region.
			//   If it has 'name' attribute, that overrides any old style "name" info.
			int indx=line.indexOf("<");
			int newidx=line.indexOf("name=");
			if (newidx != -1) { // found name attribute
				newidx = newidx + 6; // move past the first "
				int end = line.indexOf("\"", newidx);
				if (end == -1 || (end - 1) == newidx) { // default name
					orig_name = new String("notnamed"+includedFiles.size());
				} 	
				else // given name
					orig_name = line.substring(newidx, end);
			}
			else if (orig_name.length()==0)
				orig_name = new String("notnamed"+includedFiles.size());
				
			// In any case, should now have name, be ready for data lines
			newName=new String(safeID + orig_name);
			temp = new File(System.getProperty("java.io.tmpdir"),newName);
			temp.createNewFile();
			temp.deleteOnExit();
			tempWriter = new PrintWriter(new FileWriter(temp));

			int datatype=IncludedFile.RAW; // default
			int count=0;
			if (line.length()>indx+14 && line.substring(indx+1,indx+14).equalsIgnoreCase("circlepacking")) {
				datatype=IncludedFile.PACKING;
			}
			else if(line.length()>indx+5 && line.substring(indx+1,indx+5).equalsIgnoreCase("path")) {
				datatype=IncludedFile.PATH;
			}
			else if(line.length()>indx+8 && line.substring(indx+1,indx+8).equalsIgnoreCase("xyzData")) {
				datatype=IncludedFile.XYZ;
			}
			else if (line.length()>indx+9 && line.substring(indx+1,indx+9).equalsIgnoreCase("commands")) {
				datatype=IncludedFile.CMDS;
			}
			else if (line.length()>indx+9 && line.substring(indx+1,indx+6).equalsIgnoreCase("image")) {
				datatype=IncludedFile.IMAGE;
			}
			else if (line.length()>indx+14 && line.substring(indx+1,indx+11).equalsIgnoreCase("aboutimage")) {
				datatype=IncludedFile.ABOUT_IMAGE;
			}
			
			// base64 encoded image files? read characters
			if (datatype==IncludedFile.IMAGE || datatype==IncludedFile.ABOUT_IMAGE) {
				char c;
				while ((c=(char) reader.read())>0 && !(c=='<')) {
					tempWriter.write(c);
				}
				count++;
			}
			
			// transfer lines until another tagged line is encountered;
			//   (last line should have 'END', but this isn't checked)
			// TODO: don't know if '<' can occur in base64 encoded image file.
			else { 
				while ((line=reader.readLine().trim())!=null && !line.startsWith("<")) { 
					tempWriter.println(line);
					if (line.length()!=0) count++;
				}
			}
				
			tempWriter.flush();
			tempWriter.close();

			// get anything? add to vector of included files
			if (count>0) {
				
				// IMAGE should be base64 encoded, so decode
				if (datatype==IncludedFile.IMAGE || datatype==IncludedFile.ABOUT_IMAGE) { 
					temp=Base64InOut.fileOutof64(temp);
				}
				
				// add to 'includedFiles' list: duplicate names stored once, file contains last contents
				boolean dup=false;
				for (int i=0;i<includedFiles.size();i++) 
					if (includedFiles.get(i).origName.equals(orig_name))
						dup=true;
				if (!dup) {
					includedFiles.add(new IncludedFile(datatype,orig_name,temp));
				}
			}

			orig_name="";
		} // end of main while loop
	} 
	
	/**
	 * After included files have been processed, this writes their filenames 
	 * into the CPdata section of workingFile.
	 * @param writer
	 * @throws IOException
	 */
	protected void writeFileNames(BufferedWriter writer) throws IOException {
		for (int j=0;j<includedFiles.size();j++) {
			IncludedFile incFile=(IncludedFile)includedFiles.get(j);
			writer.write(new String("<file>" + incFile.origName + "</file>"));
			writer.newLine();
		}
	}

	/**
	 * Creates the default "starter" script file and returns a string 
	 * giving its path.
	 */
	public String createDefaultScript() {
		File f=null;
		try {
			f = new File(System.getProperty("java.io.tmpdir"),
					 new String("new_script.xmd"));
			f.deleteOnExit();

			BufferedWriter writer = new BufferedWriter(new FileWriter(f));
			writer.write("<?xml version=\"1.0\"?>");
			writer.write("<CP_Scriptfile>\n");
			writer.write("  <CPscript title=\"Empty script for editing\">\n");
			//			writer.write("<text>This is a starter script: add/edit elements as desired.</text>");
			writer.write("  </CPscript>\n");
			writer.write("  <CPdata>\n");
			writer.write("  </CPdata>\n");
			writer.write("</CP_Scriptfile>\n");
			writer.flush();
			writer.close();
		} catch (Exception e) {
			String errmsg=new String("ScriptHandler.createNewDocument: "+e.getMessage());
			CirclePack.cpb.myErrorMsg(errmsg);
			return null;
		}
		return f.getAbsolutePath();
	}

	/**
	 * Return index into 'includedFiles' if 'filename' is found.
	 * @param filename String, assume trimmed
	 * @return int, index (first encountered) or -1 on not found
	 */
	public int check4filename(String filename) {
		for (int j=0;j<includedFiles.size();j++)
			if (includedFiles.get(j).origName.equals(filename))
				return j;
		return -1;
	}

	 /**
	  * Get 'tmpFile' for an included 'filename'.
	  * @param filename String, base name
	  * @return File with 'tmpFile' name, null if not found
	  */
	 public File getTrueIncluded(String filename) {
		 Iterator<IncludedFile> itf=includedFiles.iterator();
		 while (itf.hasNext()) {
			 IncludedFile incfile=itf.next();
			 if (incfile.origName.equals(filename))
				 return incfile.tmpFile;
		 }
		 return null; // didn't find the named file
	 }

	 /**
	  * Sets up the URL for script based on 'namE'. If it starts with
	  * 'htt' then it's assumed to be web address, else look for file
	  * in file system, first by 'namE' alone, then in 'ScriptDirectory'.
	  * @param namE String
	  * @return URL or null on error or failure
	  */
	 public URL getScriptURL(String namE) {
		 if (namE==null) 
			 return null;
		 String name=namE.trim();
		 if (name.startsWith("file:")) {
			 if (name.length()==5)
				 return null;
			 name=name.substring(5);
		 }

		 if (name.startsWith("www."))
			 name=new String("http://"+name);

		 // If we are looking in the file system (versus web)
		 URL url=null;
		 if (!name.startsWith("htt") && !name.startsWith("ftp") && !name.startsWith("gopher")) {
			 File temp=null;
			 // TODO: other syntax for home?
			 if (name.startsWith("~")) {
				 name=name.substring(1);
				 temp=new File(CPFileManager.HomeDirectory,name);
			 }
			 else temp = new File(name);
			 if (temp==null || !temp.exists()) {
				 // next, try in 'ScriptDirectory':
				 temp=new File(CPFileManager.ScriptDirectory,name);
				 if (temp.exists() == false) {
					 String errmsg=new String("Requested script '"+namE+"' not found");
					 CirclePack.cpb.myErrorMsg(errmsg);
					 return null;
				 }
			 }
			 name=new String("file:"+temp.toString());
		 }
		 else { // if from web, need to pick off file name from end
			 int index=name.lastIndexOf(File.separatorChar);
			 if (index<0) index=0;
			 String nameonly=name.substring(index+1,name.length());
			 if (nameonly.length()==0)
				 return null;
		 }

		 // AF:
		 if ((url=FileUtil.tryURL(name))==null) { 
			 String errmsg=new String("failed to create URL for "+name);
			 CirclePack.cpb.myErrorMsg(errmsg);
			 url=null;
		 }
		 return url;
	 }

	 /**
	  * Given script filename (or name from pop up dialog, or file chosen in browser),
	  * read in script and search for/execute 'EOL' command (execute on load).
	  * Note: filename may be temp file (e.g. if file is downloaded from the web),
	  * so we provide 'origName' to saving in the list.
	  * @param filename <code>String</code>
	  * @param origName <code>String</code>, name to save under
	  * @param keepname <code>boolean</code>: true, then store in list of names.
	  * @return int, 0 = on failure. 
	  */
	 public int getScript(String filename,String origName,boolean keepname) {
		 String oName=null;
		 if (origName!=null)
			 oName=new String(origName);
		 // no name? pop up dialog
		 if (filename==null || filename.length()==0) { 
			 File f;
			 try {
				 if ((f=FileDialogs.loadDialog(FileDialogs.SCRIPT,true))!=null)
					 filename=f.getCanonicalPath();
				 if (filename==null || filename.trim().length()==0)
					 return 0;
				 oName=new String(filename);
			 } catch (IOException iox) {
				 throw new ParserException("dialog failed to get script name");
			 }
		 }

		 // reaching here, we have a filename and oName 
		 try {
			 int lf=CPBase.scriptManager.loadNamedScript(filename,oName,keepname);

			 // Check for execute_on_load command. If next command is 
			 //   named "*", then execute it (and move next); else look
			 //   for first "*" named command.
			 // TODO: more than one * command is allowed; do we only
			 //   want to execute one?
			 if (lf>0) {
				 String ncn=CPBase.scriptManager.getNextCmdName();
				 if (ncn!=null && ncn.equals("*")) 
					 CPBase.scriptManager.executeNextCmd();
				 else {
					 String  brktcmd = (String)CPBase.scriptManager.findCmdByName("*",1);
					 if (brktcmd!=null)
						 CPBase.trafficCenter.parseWrapper(brktcmd,
								 CirclePack.cpb.getActivePackData(),true,true,0,null);
				 }
			 }
			 return lf;
		 } catch (Exception ex) {
			 throw new InOutException("usage: <filename>: "+ex.getMessage());
		 }	
	 }

	 /**
	  * return true if this name is already in use.
	  * @param name
	  * @return
	  */
	 protected boolean dup_name(String name) {
		 int i;
		 for (i=0;i<includedFiles.size();i++) {
			 IncludedFile icf=includedFiles.get(i);
			 if (name.equals(icf.origName)) return true;
		 }
		 return false;
	 }

	 /**
	  * @return boolean
	  */
	 public boolean isScriptLoaded() {
		 if (workingFile == null) {
			 return false;
		 }
		 return true;
	 }

}
