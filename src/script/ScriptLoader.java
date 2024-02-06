package script;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Random;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ScriptException;
import input.CPFileManager;
import util.Base64InOut;

/**
 * Methods for loading CirclePack XML "scripts"
 * 
 * This class creates manager.workingFile, an XML working copy of 
 * the script (with CPdata files broken out), and the tree of 
 * CPTreeNode's. ScriptManager handles manipulations after this.
 * @author kens
 */
public class ScriptLoader {

	private ScriptManager manager; 
	
	// Constructor
	public ScriptLoader(ScriptManager mgr) {
		manager=mgr;
	}

	/**
	 * This loads from a file (generally 'manager.scriptFile').
	 * This is only called after user has chance to save the current 
	 * script (if it has changed).
	 * @param url URL of script
	 * @return true if the load seemed to work.
	 */
	public boolean loadScriptURL(URL url) {

		try {
			
			// create working copy 'manager.workingFile', pick off included data files
			if (createWorkingFile(url)==0) {
				CirclePack.cpb.errMsg("Encountered error in loading the script.");
				return false;
			}
			URL workingURL=new URL("file:"+
					manager.workingFile.getCanonicalPath());
			DOMParser parser = new DOMParser();
			org.w3c.dom.Document doc=null;
			try {
				parser.parse(workingURL.toString());
				doc = parser.getDocument();
			} catch(Exception ex) {
				if (PackControl.consoleCmd!=null) // this happens during startup
					CirclePack.cpb.errMsg("Caught (probably) SAXParseException (for XML parsing) in loadng script.");
				System.err.println(ex.getMessage());
				ex.printStackTrace(System.err);
				return false;
			} 

//			viewSerializedTree(doc); // for debugging
			processXMLDocument(doc);
			
			// set description, tag, load AboutImage
			ScriptSBox ssb=(ScriptSBox)(manager.cpScriptNode.stackBox);
			ssb.updateLoad();
			manager.myScriptTag=manager.getTagImage(manager.scriptTagname);

			manager.hasChanged = false;
		} catch (Exception exc) {
			if (PackControl.consoleCmd!=null) // this happens during startup
				CirclePack.cpb.errMsg("An exception occurred in loadScript.");
			System.err.println(exc.getMessage());
			exc.printStackTrace(System.err);
			PackControl.scriptHover.
				initScriptArea(PackControl.scriptHover.stackArea.getWidth()); // reinitialize default
			return false;
		}

		// if appropriate, reset 'scriptDirectory'
		// TODO: past (?) problem: resets based on default loading of 'new_script.cps'
		if (url.getProtocol().equals("file")) { 
			String cpath=url.getPath();
			int k=cpath.lastIndexOf('/');
			if (!cpath.contains("new_script.cps") && k>0) {
				// change if it is not just /tmp
				if (!cpath.substring(0,k).startsWith(System.getProperty("java.io.tmpdir")))  
					CPFileManager.ScriptDirectory =new File(cpath.substring(0,k));
			}
		}
		
		
		PackControl.scriptHover.scriptTitle(manager.scriptName,false);

		PackControl mW=(PackControl)CirclePack.cpb;
		
		// restart 'PackControl', since startup options are in the script
		mW.resetCanvasLayout();
		return true;
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
		manager.workingFile = new File(System.getProperty("java.io.tmpdir"),
				new String(manager.id + manager.scriptName));
		manager.workingFile.deleteOnExit();

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(
					manager.workingFile));
			String line;
			manager.includedFiles.removeAllElements();

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
					orig_name = new String("notnamed"+manager.includedFiles.size());
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
					orig_name = new String("notnamed"+manager.includedFiles.size());
				} 	
				else // given name
					orig_name = line.substring(newidx, end);
			}
			else if (orig_name.length()==0)
				orig_name = new String("notnamed"+manager.includedFiles.size());
				
			// In any case, should now have name, be ready for data lines
			newName=new String(safeID + orig_name);
			temp = new File(System.getProperty("java.io.tmpdir"),newName);
			temp.createNewFile();
			temp.deleteOnExit();
			tempWriter = new PrintWriter(new FileWriter(temp));

			int datatype=IncludedFile.RAW; // default
			int count=0;
			if (line.length()>indx+14 && line.substring(indx+1,indx+14).
					equalsIgnoreCase("circlepacking")) {
				datatype=IncludedFile.PACKING;
			}
			else if(line.length()>indx+5 && line.substring(indx+1,indx+5).
					equalsIgnoreCase("path")) {
				datatype=IncludedFile.PATH;
			}
			else if(line.length()>indx+8 && line.substring(indx+1,indx+8).
					equalsIgnoreCase("xyzData")) {
				datatype=IncludedFile.XYZ;
			}
			else if (line.length()>indx+9 && line.substring(indx+1,indx+9).
					equalsIgnoreCase("commands")) {
				datatype=IncludedFile.CMDS;
			}
			else if (line.length()>indx+9 && line.substring(indx+1,indx+6).
					equalsIgnoreCase("image")) {
				datatype=IncludedFile.IMAGE;
			}
			else if (line.length()>indx+14 && line.substring(indx+1,indx+11).
					equalsIgnoreCase("aboutimage")) {
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
			// Note: it appears that '<' and '>' cannot occur in base64 
			//   encoded image files.
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
				for (int i=0;i<manager.includedFiles.size();i++) 
					if (manager.includedFiles.get(i).origName.equals(orig_name))
						dup=true;
				if (!dup) {
					manager.includedFiles.add(new IncludedFile(datatype,orig_name,temp));
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
		for (int j=0;j<manager.includedFiles.size();j++) {
			IncludedFile incFile=(IncludedFile)manager.includedFiles.get(j);
			writer.write(new String("<file>" + incFile.origName + "</file>"));
			writer.newLine();
		}
	}

	/**
	 * Use: This method is called after the working file version of an XML 
	 * document has been DOM parsed. It creates all the CPTreeNode's under
	 * cpScriptNode and cpDataNode, sets 'PackControl.MapCanvasMode' and
	 * 'PackControl.AdvancedMode' if non-default. Included files have already
	 * been processed.
	 * @param doc @see org.w3c.dom.Document
	 * @throws ScriptException
	 */
	public void processXMLDocument(org.w3c.dom.Document doc) throws ScriptException {
	    ScriptManager.cmdCount=new Random().nextInt(32000); // for random icon choices

		NodeList domChildren = doc.getDocumentElement().getChildNodes();
		// CPscript node is required, CPdata is optional
		if (domChildren.getLength()==0) 
			throw new ScriptException("'CPscript' node is required");
		int hit=0;
		for (int j=0;j<domChildren.getLength();j++) {
			Node domchild=(Node)(domChildren).item(j);
			if (domchild.getNodeName().equals("CPscript")) hit=1;
		}
		if (hit==0) 
			throw new ScriptException("first node wasn't 'CPscript'");

		// go ahead, assume okay; on error, go to default 
		manager.rootNode.displayString = manager.scriptName;

		manager.cpDataNode.removeAllChildren();
		manager.cpDataNode.stackBox.removeAll();
		manager.cpDataNode.stackBox.isOpen=false;
		manager.cpDataNode.displayString="Files: 0"; // default

		PackControl.MapCanvasMode=manager.scriptMapMode=false;
		PackControl.AdvancedMode=manager.scriptLevel=true;
		for (int j=0;j<domChildren.getLength();j++) {
			Node domchild=(Node)(domChildren).item(j);
			if (domchild.getNodeName().equals("CPscript")) {
				manager.scriptDescription=null;
				manager.scriptTagname="";
				manager.myScriptTag=manager.defaultTag;
				ScriptSBox smb=(ScriptSBox)manager.cpScriptNode.stackBox;
				if (smb.descriptField!=null) 
					smb.descriptField.setText("");
				if (smb.tagField!=null) smb.tagField.setText("");
				manager.cpScriptNode.removeAllChildren();
				manager.cpScriptNode.stackBox.isOpen=true;
				manager.cpScriptNode.stackBox.removeAll();
				
				// see if there's a name
				String head="unnamed";
				
				// check for title, first among attributes
				NamedNodeMap map = domchild.getAttributes();
				Node name = map.getNamedItem("title");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						head=tmp_text.trim();
					}
				}
				
				Node tagname=map.getNamedItem("iconname");
				if (tagname!=null) {
					String tmp_text=tagname.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						manager.scriptTagname=tmp_text.trim();
					}
				}
				
				// check level: only option now 'min'; default to advanced.
				Node level = map.getNamedItem("level");
				if (level!=null) {
					String tmp_text = level.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						tmp_text=tmp_text.trim().toLowerCase();
						if (tmp_text.startsWith("min")) {
							PackControl.AdvancedMode=manager.scriptLevel=
								manager.cpScriptNode.isInline=false;
						}
						else { 
							PackControl.AdvancedMode=manager.scriptLevel=
								manager.cpScriptNode.isInline=true;
						}
					}
				}
				
				// check screenmode: only option now 'map' for pair; default to single window
				Node smode = map.getNamedItem("screenmode");
				if (smode!=null) {
					String tmp_text = smode.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						tmp_text=tmp_text.trim().toLowerCase();
						if (tmp_text.startsWith("map")) {
							PackControl.MapCanvasMode=
								manager.scriptMapMode=true;
						}
						else 
							PackControl.MapCanvasMode=manager.scriptMapMode=false;
						
					}
				}
				
				// <header> element (first occurrence) overrides title.
				NodeList kids = domchild.getChildNodes();
				for (int i=0;i<kids.getLength();i++) {
					if (kids.item(i).getNodeName().equals("header")) {
						head=kids.item(i).getFirstChild().getNodeValue();
						break;
					}
				}
				manager.cpScriptNode.displayString=head;
				
				// set checkboxes
				ScriptSBox ssb=(ScriptSBox)manager.cpScriptNode.stackBox;
				ssb.setMapCk(manager.scriptMapMode);
				ssb.setLevelCk(manager.scriptLevel);
				
				manager.cpScriptNode.stackBox.currentMode=StackBox.DISPLAY;
				int wide=manager.cpScriptNode.stackBox.myWidth;
				manager.cpScriptNode.stackBox.redisplaySB(wide);
				recurseOnNode(domchild,manager.cpScriptNode);
			} 
			else if (domchild.getNodeName().equals("CPdata")) {
				manager.cpDataNode.displayString=(
						new String("Files: "+ manager.includedFiles.size()));
				int wide=manager.cpDataNode.stackBox.myWidth;
					manager.cpDataNode.stackBox.redisplaySB(wide);
				recurseOnNode(domchild,manager.cpDataNode);
			}
		}
		
	}

	/**
	 * Uses recursion to populate the child list CPTreeNode's 
	 * under cpScriptNode or cpDataNode in parallel with the 
	 * children in the document tree.
	 * @param Node domNode
	 * @param CPTreeNode treeNode
	 */
	protected void recurseOnNode(Node domNode,CPTreeNode treeNode) {
		NodeList domChildren = domNode.getChildNodes();

		if (treeNode.getAllowsChildren()) { 
			for (int i = 0; i < domChildren.getLength(); i++) {
				Node domchild=(Node)(domChildren).item(i);
				CPTreeNode mirror=manager.initCPTreeNode(domchild);
				if (mirror!=null) {
					treeNode.add(mirror);
					mirror.stackBox.myWidth=
						treeNode.stackBox.myWidth-PackControl.scriptManager.WIDTH_INC;
					recurseOnNode(domchild,mirror);
				}
			}
		}
	}
	
}
