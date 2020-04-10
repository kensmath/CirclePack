package script;

import input.CPFileManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

import mytools.MyTool;
import util.Base64InOut;
import canvasses.MyCanvasMode;
import circlePack.PackControl;

/**
 * Write script to a file using the tree of CPTreeNode's
 * @author kens
 *
 */
public class TNWriter {

	private ScriptManager manager;
	private BufferedWriter writer;
	
	// Constructor
	public TNWriter(ScriptManager mgr) {
		manager=mgr;
	}
	
	/** 
	 * Write the CPTreeNodes to a file
	 * @param f, File
	 */
	public void Write_from_TN(File f) {
		if (!manager.isScriptLoaded()) return;
		manager.acceptAllEdits();
		if (manager.rootNode==null 
				|| manager.rootNode.getChildCount()!=2) return;
		// must have 2 children: CPSCRIPT, CPDATA
		int type=manager.rootNode.getChild(0).getType();
		if (type!=CPTreeNode.CPSCRIPT) return;
		type=manager.rootNode.getChild(1).getType();
		if (type!=CPTreeNode.CPDATA) return;

		try {
			writer = new BufferedWriter(new FileWriter(f));
			writer.write("<?xml version=\"1.0\"?>");
			writer.newLine();
			DateFormat defaultDate=DateFormat.getDateInstance();
			writer.write("<CP_Scriptfile date=\""+defaultDate.format(new Date())+"\">");
			writer.newLine();
			writer.flush();

			// recursively write the CPscript nodes
			scriptRecurse(manager.cpScriptNode); // CPscript 

			// write the CPdata nodes
			writer.write("<CPdata>");
			writer.newLine();
			writeDataFiles();
			writer.write("  </CPdata>");
			writer.newLine();
			writer.flush();

			writer.write("</CP_Scriptfile>");
			writer.newLine();
			writer.flush();
			writer.close();
			manager.scriptName = f.getName();
		} catch(IOException ioe) {
			String errmsg=new String("Failed to open '"+f.toString()+"'");
			PackControl.consoleCmd.dispConsoleMsg(errmsg);
			PackControl.shellManager.recordError(errmsg);
			return;
		}
		return;
	}
		
	// recursively writes info from CPTreeNodes, depending on type
	private void scriptRecurse(CPTreeNode treeNode) throws IOException {
		int type=treeNode.tntype;
		String str=null;
		switch (type) {
		case CPTreeNode.CPSCRIPT: {
			writer.write("<CPscript title=\""+
					treeNode.displayString.trim()+"\" ");
			if (!manager.scriptLevel)
				writer.write("level=\"min\" ");
			if (manager.scriptMapMode)
				writer.write("screenmode=\"map\" ");
			ScriptSBox ssb=(ScriptSBox)(manager.cpScriptNode.stackBox);
			String tagname=ssb.tagField.getText().trim();
			if (tagname!=null && tagname.length()>0)
				writer.write("iconname=\""+tagname+"\"");
			writer.write(">");
			writer.newLine();
			// Is there a script "description"?
			if (manager.scriptDescription!=null 
					&& manager.scriptDescription.trim().length()>0) {
				writer.write("<description>"+manager.scriptDescription+
						"</description>");
				writer.newLine();
			}
			for (int i=0;i<treeNode.getChildCount();i++) 
				scriptRecurse(treeNode.getChild(i));
			writer.write("  </CPscript>");
			writer.newLine();
			break;
		}
		case CPTreeNode.SECTION: {
			writer.write("<Section ");
			if (treeNode.displayString!=null) 
				writer.write("title=\""+treeNode.displayString+"\">");
			writer.newLine();
			for (int i=0;i<treeNode.getChildCount();i++) 
				scriptRecurse(treeNode.getChild(i));
			writer.write("</Section>");
			writer.newLine();
			break;
		}
		case CPTreeNode.LINEUP: { // just take the children
			for (int i=0;i<treeNode.getChildCount();i++) 
				scriptRecurse(treeNode.getChild(i));
			break;
		}
		case CPTreeNode.TEXT: {
			writer.write("<text> "+treeNode.displayString+" </text>");  
			writer.newLine();
			break;
		}
		case CPTreeNode.COMMAND: {
			MyTool myTool=treeNode.tTool;
			if (myTool==null) break; // shouldn't happen
			writer.write("<cmd");
			if (!treeNode.isInline()) writer.write(" inline=\"no\"");
			if (myTool.getName()!=null && myTool.getName().trim().length()>0) 
				writer.write(" name=\""+myTool.getName()+"\"");
			if (myTool.getMnem()!=null && myTool.getMnem().trim().length()>0) 
				writer.write(" mnemonic=\""+myTool.getMnem()+"\"");
			if (myTool.getToolTip()!=null && myTool.getToolTip().trim().length()>0)
				writer.write(" tooltip=\""+myTool.getToolTip()+"\"");
			if ((str=new String(myTool.getCPIcon().getIconName().trim()))!=null) {
				StringBuilder stbld=new StringBuilder(str);
				String nstr=CPFileManager.getFileName(stbld);
//				int k=str.lastIndexOf('/');
//				if (k>=0 && k<str.length()) str=str.substring(k+1,str.length()); 
				writer.write(" iconname=\""+nstr+"\""); //.replace('"','\'')+"\"");
			}
			if (myTool.isDropable())
				writer.write(" dropable=\"yes\"");
			// have to add iconname info to store iconname if it is not just random
			writer.write(">"+myTool.getCommand()+" </cmd>");
			writer.newLine();
			break;
		}
		case CPTreeNode.MODE: {
			MyCanvasMode myMode=(MyCanvasMode)treeNode.tTool;
			if (myMode==null) break; // shouldn't happen
			writer.write("<mode");
			if (myMode.nameString!=null && myMode.nameString.trim().length()>0) 
				writer.write(" name=\""+myMode.nameString.trim()+"\"");
			if ((str=new String(myMode.getCPIcon().getIconName().trim()))!=null) {
				int k=str.lastIndexOf('/');
				if (k>=0 && k<str.length()) str=str.substring(k+1,str.length()); 
				writer.write(" iconname=\""+str.replace('"','\'')+"\"");
			}
			if (!myMode.handy) { // default is yes, only include if "no"
				writer.write(" handy=\"no\"");
			}
			if (myMode.shortTip!=null && myMode.shortTip.trim().length()>0)
				writer.write(" shorttip=\""+myMode.shortTip.trim().replace('"','\'')+"\"");
			if (myMode.getToolTip()!=null && myMode.getToolTip().trim().length()>0)
				writer.write(" tooltip=\""+myMode.getToolTip()+"\"");
			if (!myMode.isDropable()) // default is "yes"
				writer.write(" dropable=\"no\"");
			if (myMode.cmd2!=null  && myMode.nameString.trim().length()>0) {
				writer.write(" cmd2=\""+myMode.cmd2.trim().replace('"','\'')+"\"");
			}
			if (myMode.cmd3!=null  && myMode.nameString.trim().length()>0) {
				writer.write(" cmd3=\""+myMode.cmd3.trim().replace('"','\'')+"\"");
			}
			if (myMode.hotPoint!=null) { // generally, null
				writer.write(" point=\""+Integer.toString(myMode.hotPoint.x)+
						" "+Integer.toString(myMode.hotPoint.y)+"\"");
			}
			// have to add iconname info to store iconname if it is not just random
			writer.write(">"+myMode.getCommand()+" </mode>");
			writer.newLine();
			break;
		}
		} // end of switch
	}
	
	// handle list of files
	public void writeDataFiles() throws IOException {
		String s;
		for (int j=0;j<manager.includedFiles.size();j++) {
			IncludedFile incFile=(IncludedFile)manager.includedFiles.get(j);
			String moniker="rawdata"; // default
			switch ((int)incFile.dataType){
				case 1: {
					moniker="circlepacking";
					break;
				}
				case 2: {
					moniker="path";
					break;
				}
				case 3: {
					moniker="xyzData";
					break;
				}
				case 4: {
					moniker="commands";
					break;
				}
				case 5: {
					moniker="image";
					break;	
				}
				case 6: {
					moniker="AboutImage";
					break;
				}
			}
			writer.write("    <"+moniker+" name=\"" + incFile.origName + "\">");
			File ifile=incFile.tmpFile;
			writer.newLine();
			
			// if IMAGE, assume binary and encode before saving
			if ((int)incFile.dataType==IncludedFile.IMAGE || 
					(int)incFile.dataType==IncludedFile.ABOUT_IMAGE) { // must encode base64
				Base64InOut.fileInto64(ifile);
			}
			
			BufferedReader tempReader = new BufferedReader(new FileReader(
					ifile));
			while ((s = tempReader.readLine()) != null) {
				writer.write(s);
				writer.newLine();
			}
			tempReader.close();
			
			writer.write("    </"+moniker+">");
			writer.newLine();
		} // end of while
	}

}
