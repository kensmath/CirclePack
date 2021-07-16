package mytools;

import handlers.ACTIVEHandler;
import images.CPIcon;
import input.FileDialogs;

import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

import listeners.MyToolListener;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import panels.MyToolBar;
import util.PopupBuilder;
import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import deBugging.PrintIcon;
import dragdrop.ToolDragSourceListener;
import dragdrop.ToolTransferable;
import frames.CmdToolEditor;
import frames.MobiusToolEditor;
import frames.ScriptToolEditor;

/**
 * This is an abstract class for various 'handlers' for loading, 
 * manipulating, saving of MyTool and MyToolBar stuff. 
 * The user's tools are eventually to be read and saved in files.
 * 
 * @author kens
 *
 */
public abstract class MyToolHandler implements MouseListener {

	public static boolean hasChanged=false;
	public String toolType; 
	public String toolPrefix; // category of tool: MAIN:, SCRIPT:, etc.
	public Vector<MyTool> toolVector;  // pointer to current tool vector
	public int toolIndx; // what's the index for next tool?
	public MyToolBar toolBar; // JPanel for display
	public MyToolEditor toolEditor;
	public MyPopupMenu toolMenu; // menu that pops up on tools
	public MyToolListener toolListener;
	public File toolFile; // file for tools
	public String frameTitle;

	// Constructors
	public MyToolHandler(File mytoolsFile,String tool_type) {
		toolFile = mytoolsFile; // store for possible subclass use
		toolBar=new MyToolBar();
		toolVector = new Vector<MyTool>();
		toolMenu=null;
		toolListener=null;
		toolIndx=0;
		toolType=new String(tool_type);
		if (toolType==null || toolType.length()==0) toolType="MISC:";
		int k=tool_type.indexOf(':'); 
		if (k>0)
			toolPrefix=tool_type.substring(0,k+1);
		setEditor(toolType); // could be reset
	}
	
	/**
	 * The Menu that appears on tools is created/set in the listener.
	 * @param mpm
	 */
	public void setButtonMenu(MyPopupMenu mpm) {
		toolMenu=mpm;
	}

	/**
	 * This creates an edit tool appropriate to this type of toolbar
	 * @param t_type
	 */
	public void setEditor(String t_type) { // default is MYTOOL CmdToolEditor
		if (t_type.startsWith("MAIN:")) toolEditor=new CmdToolEditor(toolType,this); 
		else if (t_type.startsWith("BASIC:")) toolEditor=new CmdToolEditor(toolType,this);
		else if (t_type.startsWith("MYTOOL:")) toolEditor=new CmdToolEditor(toolType,this);
		else if (t_type.startsWith("SCRIPT:")) toolEditor=new ScriptToolEditor(toolType,this); 
		else if (t_type.startsWith("MOBIUS:")) toolEditor=new MobiusToolEditor(toolType,this);
		else if (t_type.startsWith("SIDEPAIR:")) toolEditor=new MobiusToolEditor(toolType,this);
		else toolEditor=new CmdToolEditor(toolType,this);
	}

	/**
	 * open CmdToolFrame: should already exist; clear and show.
	 * Attach the latest vector of icons as the iconCombo model
	 *
	 */
	public void openToolEditor() {
		toolEditor.clearfields();
		toolEditor.setVisible(true);
	}
	
	/**
	 * open CmdToolFrame to edit existing tool
	 * @param mytool
	 */
	public void editTool() {
 		try{
 			toolEditor.setEntries((MyTool)toolVector.get(toolIndx));
 		} catch(ArrayIndexOutOfBoundsException ex) {
 			toolIndx=toolVector.size();
 			return;
 		}
 		toolEditor.setVisible(true);
 	}
		
	public void editTool(MyTool mytool) {
		// should inform that one already open; or bring it to the top
		if ((toolIndx=toolVector.indexOf(mytool))<0) return;
		editTool();
	}
	
	public void moveBackward() {
		if (toolIndx<=0 || toolIndx>=toolVector.size()) { // nowhere to move
 			toolIndx=toolVector.size();
 			return;
 		}
 		MyTool holdbutton;
 		try{ 
 			holdbutton=(MyTool)toolVector.get(toolIndx-1);
 		} catch(ArrayIndexOutOfBoundsException ex) {
 			toolIndx=toolVector.size();
 			return;
 		}
 		toolVector.remove(toolIndx-1);
 		toolVector.add(toolIndx,holdbutton);
			toolIndx=toolVector.size();
 		repopulateTools();
 	}
	
	public void moveForward() {
 		if (toolIndx<0 || toolIndx>=(toolVector.size()-1)) { // nowhere to move
 			toolIndx=toolVector.size();
 			return;
 		}
 		MyTool holdbutton;
 		try{ 
 			holdbutton=(MyTool)toolVector.get(toolIndx);
 		} catch(ArrayIndexOutOfBoundsException ex) {return;}
 		toolVector.remove(toolIndx);
 		toolVector.add(toolIndx+1,holdbutton);
			toolIndx=toolVector.size();
 		repopulateTools();
	}
	
	public void writeTools() { // default name (can change with popup dialog)
		writeTools(toolFile);
	}
	
	public void writeTools(File file) {
		String filename=file.toString();
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			
			// Stuff at top of file
			writer.write("<?xml version=\"1.0\"?>\n<CP_ToolBox>\n");
			writer.write("<version>"+PackControl.CPVersion+"</version>\n\n");
			writer.write("<name>"+filename+"</name>\n");
			writer.write("<creator> </creator>\n"); // intend to put user name here
			writer.write("<date>"+new Date().toString()+"</date>");
			writer.write("\n\n");
			
			String indent="  ";
			for (int i=0;i<toolVector.size();i++) {
				MyTool mt=(MyTool)toolVector.get(i);
				
				// open with attributes
				writer.write("<MyTool ");
				writer.write("name=\""+mt.getName()+"\" ");
				writer.write("type=\""+mt.getToolType()+"\" ");
				String mn=(String)mt.getMnem();
				if (mn!=null &&  mn.length()==1) 
					writer.write(" mnemonic=\""+mn+"\" ");
				if (mt.isDropable()) writer.write("dropable=\"yes\" ");
				writer.write(">\n");
				
				// icon name and commands
				String cmd_str=(String)mt.getCommand();
				if (cmd_str!=null)
					writer.write(indent+"<cmd>"+cmd_str+"</cmd>\n");
				String data=(String)mt.getCPIcon().getIconName();
				if (data!=null)
					writer.write(indent+"<iconname>"+data+"</iconname>\n");
				data=mt.getToolTip();
				if (data!=null && data.length()>0)
					writer.write(indent+"<tooltip>"+data+"</tooltip>\n");
				
				// close
				writer.write("</MyTool>\n\n");
			}
			// Stuff at bottom
			writer.write("</CP_ToolBox>\n");
		  	writer.flush();
		  	writer.close();
		  	CirclePack.cpb.msg("Saved MyTools to "+file.toString());
		} catch(IOException ioe) {
			String errmsg=new String("Couldn't open '"+toolFile.toString()+"'");
			PackControl.consoleCmd.dispConsoleMsg(errmsg);
			PackControl.shellManager.recordError(errmsg);
			return;
		}
	}
	
	/**
	 * Displays a save dialog so the user can save the current
	 * tools in a file. The user can cancel if desired in which case
	 * nothing happens. 
	 */
	public int displaySaveDialog(){
		File theFile;
		if ((theFile=FileDialogs.saveDialog(FileDialogs.TOOLS,true))!=null) {
			writeTools(theFile);
			return 1;
		}
	    return 0;
	}
	
	/**
	 * 
	 */
	public int displayLoadDialog() {
		File theFile;
		if ((theFile=FileDialogs.loadDialog(FileDialogs.TOOLS,true))!=null) {
			this.appendFromFile(theFile);
			return 1;
		}
		return 0;
	}
	
	/**
	 * Duplicate copy to clipboard
	 *
	 */
	public void copyTool() {
		// not yet ready
	}
	
	/**
	 * insert tool from clipboard
	 *
	 */
	public void pasteTool() {
		// not yet ready
	}
	
	/**
	 * Remove a given tool from hashedTools by finding
	 * its tool index.
	 * @param mytool
	 */
	public void deleteTool(MyTool mytool) {
		if ((toolIndx=toolVector.indexOf(mytool))<0) return;
		deleteTool();
	}
	
	/**
	 * Delete the tool at current 'toolIndx' and reset
	 * toolIndx to end of 'toolVector'.
	 */
	public void deleteTool() {
		if (toolIndx<0 || toolIndx>=toolVector.size()) return;
		MyTool mytool=(MyTool)toolVector.get(toolIndx);
		if (mytool==null) return;
 		CPBase.hashedTools.remove(mytool.getKey());
 		toolVector.remove(toolIndx);
		toolIndx=toolVector.size();
 		repopulateTools();
	}

	/**
	 * Create a new MyTool tool in toolVector; toolIndx has been set
	 * elsewhere to indicate where to add it. We put tool in hashedTools. 
	 * (Should check elsewhere to remove earlier version from hashedTools 
	 * if there is one; e.g. when editing.)
	 */
	public MyTool createTool(CPIcon cpIcon,String cmdtext,String nametext,
			String mnem,String tiptext,boolean dropit,
			PopupBuilder popUpMenu) {
		MyTool mytool=new MyTool(cpIcon,cmdtext,nametext,mnem,tiptext,toolType,
				dropit,toolListener,popUpMenu);
		mytool.addMouseListener(this);
		
		if (mytool.isDropable()) {
			DragSource dragSource=DragSource.getDefaultDragSource();
			dragSource.createDefaultDragGestureRecognizer(mytool,DnDConstants.ACTION_LINK,
				new DragGestureListener() {
				public void dragGestureRecognized(DragGestureEvent event) {
					Transferable transferable=new ToolTransferable((MyTool)event.getComponent());
					event.startDrag(null,transferable,new ToolDragSourceListener());
				}
			});
		}
		
		if (toolIndx>=toolVector.size()) toolIndx=toolVector.size(); // default to last
		else if (toolIndx>=0) { // replacing existing
			toolVector.remove(toolIndx);
		}
		toolVector.add(toolIndx,mytool);
		toolEditor.addTool(cpIcon); // add to iconBox list
		repopulateTools();
		toolIndx=toolVector.size();
		hasChanged=true;
		return mytool;
	}

	/**
	 * Insert a tool created elsewhere; assume toolIndx location set elsewhere
	 * @param newtool
	 */
	public void addTool(MyTool newtool) {
		if (newtool==null) return;
		createTool(newtool.getCPIcon(),newtool.getCommand(),newtool.getName(),
				newtool.getMnem(),newtool.getToolTip(),newtool.dropable,
				newtool.getPopUpMenu());
	}
	
	/**
	 * Clear all icons from ToolBar; careful, this misses things
	 * orphaned from toolVector
	 */
	public void clearToolBar() {
		// remove from bar
		for (int i=0;i<toolVector.size();i++) {
			toolBar.remove((MyTool)toolVector.get(i));
		}
	}
	
	/**
	 * Remove all 'toolVector' tools from 'toolBar', then
	 * empty 'toolVector'. Note that 'MyTool's not in
	 * toolVector may be ophaned and will remain in toolBar.
	 *
	 */
	public void wipeoutTools() {
		// remove from bar
		clearToolBar();
		toolVector.removeAllElements(); // empty vector
		toolIndx=0;
		toolBar.repaint();
		hasChanged=true;
	}

	/**
	 * Empty 'toolBar', then add all 'toolVector' 'MyTools' into toolBar.
	 */
	public void repopulateTools() {
		boolean debug=false;
		toolBar.removeAll();
		for (int i=0;i<toolVector.size();i++) {
			MyTool mtool=(MyTool)toolVector.get(i);
			toolBar.add(mtool);
			if (debug) {
				PrintIcon.printImageIcon(mtool.cpIcon.imageIcon,new String("repopulate"+i));
			}
		}
		toolBar.revalidate();
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {
		if ((e.getButton() == MouseEvent.BUTTON3) && toolMenu!= null) {
			// have to know which element the button was pressed on
			toolIndx=toolVector.indexOf((MyTool)e.getSource());
			if (toolIndx<0) {
				toolIndx=toolVector.size();
				return;
			}
			toolMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	
	/**
	 * This pops up a file chooser for loading a toolbar and on 
	 * success returns the tool box name. On failure, returns null.
	 * (Currently the tools are appended to the toolbar.)
	 * 
	 * TODO: problem with directory: starts pointing to Resource,
	 * perhaps in 'jar'; but then might change to local directory. 
	 */
	public String ToolLoadDialog() {
		File theFile=null;
		if ((theFile=FileDialogs.loadDialog(FileDialogs.TOOLS,true))!=null) {
			if (appendFromFile(theFile)) { // yes, got it
				return theFile.getName();
			}
		}
		return null;
	}

	/**
	 * read the chosen file and append its tools to the
	 * toolbar of this handler or to the 'MyCanvasMode'
	 * vectors of 'CursorCtrl'.
	 * @param File filename with complete path
	 */
	public boolean appendFromFile(File filename) {
	
		if (filename==null) return false;
		int i=0;
		Element toolElement;
		NodeList nl=null;
		try {
			DOMParser parser = new DOMParser();
			parser.parse(filename.toString());
			org.w3c.dom.Document doc = parser.getDocument();
			
			Element docEle = doc.getDocumentElement();
			
			//get a nodelist of <MyTool> elements
			nl = docEle.getElementsByTagName("MyTool");
			if(nl != null && nl.getLength() > 0) {
				for(i = 0 ; i < nl.getLength();i++) {
					toolElement=(Element)nl.item(i);
					parseMyTool(toolElement);
				}
			}
		}   catch(Exception exc) {
		      System.err.println("An exception occurred in loading the toolbox "+filename.toString());
		      System.err.println(exc.getMessage());
//		      exc.printStackTrace(System.err);
		      return false;
		    }
		hasChanged=false; // reset
		return true;
	}
	
	/**
	 * Extract XML info to create the tool. This will check
	 * the tool type and create the tool only if it matches
	 * the toolType for this handler.
	 * @param tE
	 */
	public void parseMyTool(Element tE) {
		// careful: these find first instances, even in subelements.
		String name= tE.getAttribute("name");
		String yesno=tE.getAttribute("dropable");
		String needsC=tE.getAttribute("needsC");
		String mnemonic=tE.getAttribute("mnemonic");
		String tooltype=tE.getAttribute("type");
		String handy=tE.getAttribute("handy");
		
		String tip=getTextValue(tE,"tooltip");
		String cmd_text=getTextValue(tE,"cmd"); 
		String cmd2_text=getTextValue(tE,"cmd_m2");
		String cmd3_text=getTextValue(tE,"cmd_m3");
		String iconname=getTextValue(tE,"iconname");

		// don't include if C library is not available
		// TODO: may need to update now that 'DelaunayBuild' and 'SolverFunction' are
		//   separate libraries.
		if (needsC!=null && needsC.equals("yes") && 
				!JNIinit.DelaunayStatus())
			return;
			
		// elements initiating canvass 'modes'
		String modename=getTextValue(tE,"modename");
		String canvasmode=getTextValue(tE,"canvasmode"); // short description
		String cursorpoint=getTextValue(tE,"cursorpoint");

		// is this the right 'type' for this toolbar? (MISC: is okay for any toolbar)
		if (tooltype!=null && !tooltype.startsWith(this.toolPrefix) 
				&& !tooltype.startsWith("MISC:") && tooltype.length()>0) return;
		
		// First see if this is an 'active mode'; i.e., gives a cursor, 
		//   cursor sweet spot, activemode string, and invokes a mode 
		//   in the active canvas.
		if (canvasmode!=null) {
			Point point=null;
			if (cursorpoint!=null) { // cursor sweet spot may be given
				String coords[]=cursorpoint.split("\\s+");
				if (coords.length==2) {
					int x=Integer.valueOf(coords[0]).intValue();
					int y=Integer.valueOf(coords[1]).intValue();
					point=new Point(x,y);
				}
			}
			
			boolean hndy=false;
			if (handy!=null && handy.equals("yes"))
				hndy=true;

			CPIcon cpIcon= new CPIcon(iconname);
			MyCanvasMode tm=new MyCanvasMode(modename,cpIcon,point,
					cmd_text,cmd2_text,cmd3_text,canvasmode,tip,
					toolPrefix,hndy);
			
			// put 'menuItem' in mode vector; script handled separately
			if (!toolPrefix.equals("SCRIPT:"))
				tm.updateMenuItem();
				
			return;
		}

		// check for 'menu' entries: can only accept one top level menu
		PopupBuilder popUp=null;
		NodeList nl=tE.getElementsByTagName("menu");
		if (nl!=null && nl.getLength()>0) {
			if (this instanceof ACTIVEHandler) {
				try {
					popUp=new PopupBuilder(nl.item(0),((ACTIVEHandler)this).activeWrapper);
					cmd_text=new String(name); // have to have identifier
					try {
						String attachTo=nl.item(0).getAttributes().
						    getNamedItem("attachTo").getFirstChild().getNodeValue().trim();
						if (attachTo.contains("canv")) {
							ACTIVEHandler aH=(ACTIVEHandler)this;
							aH.activeWrapper.button3Popup=popUp;
						}
					} catch (Exception ex) {}
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("Failed building menu "+name+": "+ex.getMessage());
					return;
				}
			}
			return; // don't build the tool. (This could be left for redundancy)
		}

		// otherwise is a typical tool
		boolean dropable=true;
		if (yesno!=null && yesno.equals("no")) dropable=false;
		
		// create the tool
		CPIcon cpIcon= new CPIcon(iconname);
		createTool(cpIcon,cmd_text,name,mnemonic,tip,dropable,popUp);
	}
	
	private String getTextValue(Element ele, String tagName) {
		String textVal = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element)nl.item(0);
			Node eln=el.getFirstChild();
			if (eln!=null) textVal = eln.getNodeValue();
		}

		return textVal;
	}
}
