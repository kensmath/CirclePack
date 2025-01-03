package script;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import allMains.CPBase;
import allMains.CirclePack;
import canvasses.ActiveWrapper;
import canvasses.CursorCtrl;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import dragdrop.ToolDragSourceListener;
import dragdrop.ToolTransferable;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.ParserException;
import frames.AboutFrame;

/*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*
 Overview:  This class is responsible for most of the behavior for handling scripts.
 It implements JTree methods; this is a tree of CPTreeNode's created from DOM
 nodes of a script document or added/modified/deleted later.
 This manager listens for events relating to scripts, maintains the data for the script tree,
 keeps track of the current command, finds the next command, and several other things.
 Uses a DOM parser to read in a script file and CPTreeNode's to manage the TreeModel
 interface.
 *--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*/

import images.CPIcon;
import input.CPFileManager;
import input.FileDialogs;
import mytools.MyTool;
import mytools.MyToolEditor;
import packing.CPdrawing;
import packing.PackData;
import util.FileUtil;
import util.StringUtil;

public class ScriptManager implements ActionListener {

	public int WIDTH=535; // reset on script startup.
	public int WIDTH_INC=15; // boxes narrow as nesting depth increases

	// Script data
	public String scriptName; // name of current script (whether file or URL)
	public String creationDate;  // TODO: not yet implemented: null until first save, then fixed
	public ImageIcon defaultTag; // 'tag' is creator's visual tag (image)
	public ImageIcon myScriptTag; // 'tag' specified in script.
	public String scriptTagname; // 'tag' image filename
	public String scriptDescription; // from <description> element
	public boolean scriptLevel; // script setting for 'AdvancedMode'
	public boolean scriptMapMode; // script setting for 'MapCanvasMode'
	File workingFile; // working copy of script

	// data files which are included in script
	public Vector<IncludedFile> includedFiles=new Vector<IncludedFile>();

	protected int id; // random identifier for tmp file names
	boolean editEnabled;
	public boolean hasChanged;
	public CPTreeNode rootNode;
	public CPTreeNode cpScriptNode;
	public CPTreeNode cpDataNode;
	CPTreeNode nextCmdNode;

	TreePath pathToCurrentNode;
	protected Vector<TreeModelListener> treeModelListeners;
	protected StringTokenizer tokenizer;
	protected TreeCellRenderer treeRenderer;
	public ScriptLoader scriptLoader;
	public TNWriter tnWriter;
	protected Rectangle intendedRect; // store Rectangle that should appear in stackScroll.
	boolean cmdOpenMode;  // if true, then commands not in edit mode should be 'open'.

	// Static variables
	public static int padding = 20; // pixel padding above/below for viewPort adjustment
	public static int cmdCount=0;
	public static final int INSERT_IN = 0;
	public static final int INSERT_ABOVE = -1;
	public static final int INSERT_BELOW = 1;

	// Constructors
	public ScriptManager() {
		scriptDescription=null;
		scriptTagname="";
		scriptLevel=true; // default: advanced mode
		scriptMapMode=false; // default, single canvas
		editEnabled = false;
		id = new Random().nextInt(32000);
		hasChanged = false;
		treeModelListeners = new Vector<TreeModelListener>();
		scriptLoader=new ScriptLoader(this);
		tnWriter=new TNWriter(this);
		cmdOpenMode=false; // default and startup value
	}

	public void redisplayCPscriptSB() {
		cpScriptNode.stackBox.redisplaySB(cpScriptNode.stackBox.myWidth);
	}

	public void redisplayCPdataSB() {
		cpDataNode.stackBox.redisplaySB(cpDataNode.stackBox.myWidth);
	}

	/**
	 * Used only when new script is loaded; this is 'repopulateRecurse'
	 * from the top nodes, 'cpScriptNode' and 'cpDataNode'.
	 */
	public void populateDisplay() {
		if (rootNode==null) return;

		// recursively populate CPscript and CPdata
		//AF>>>//
		// Reverted order for consistency - first script, then data.
		// Lockable JViewport fixes the same problem.
		repopulateRecurse(cpScriptNode);
		cpScriptNode.stackBox.redisplaySB(cpScriptNode.stackBox.myWidth);
		repopulateRecurse(cpDataNode);
		cpDataNode.stackBox.redisplaySB(cpDataNode.stackBox.myWidth);
		//<<<AF//
	}

	// ??? what was this for? (8/2010)
	//	Runnable setViewRect = new Runnable() {
	//	    public void run() {
	//	    	PackControl.scriptHover.stackScroll.revalidate();
	//	    	PackControl.scriptHover.stackScroll.setVisible(true);
	//	    	PackControl.scriptHover.stackScroll.scrollRectToVisible(intendedRect);
	//	    }};

	/**
	 * Recursively repopulate the script 'StackBox's
	 */
	public void repopulateRecurse(CPTreeNode treeNode) {
		// this shouldn't occur
		//		if (treeNode.stackBox==null) // must be 'root' node
		//			return;
		if (treeNode.stackBox.isOpen && treeNode.getChildCount()>0) {

			if (treeNode.tntype==CPTreeNode.CPSCRIPT || treeNode.tntype==CPTreeNode.CPDATA ||
					treeNode.tntype==CPTreeNode.SECTION)
				treeNode.consolidateNodes();

			// continue recursion
			for (int j=treeNode.getChildCount()-1;j>=0;j--) {
				CPTreeNode child=(CPTreeNode)treeNode.getChild(j);
				child.stackBox.myWidth=treeNode.stackBox.myWidth-WIDTH_INC;
				repopulateRecurse(child);
			}
		}

		// NOTE: LINEUP's are never open, but they redisplay their own children
		treeNode.stackBox.redisplaySB(treeNode.stackBox.myWidth);
	}


	/**
	 * Debug: print out the dimensions of the stackboxes
	 * @param treeNode
	 */
	public void debugLayoutRecurse(CPTreeNode treeNode) {
		int count;
		if ((count=treeNode.getChildCount())>0) {
			for (int j=count-1;j>=0;j--) {
				CPTreeNode child=(CPTreeNode)treeNode.getChild(j);
				child.debugSize(); // print info
				debugLayoutRecurse(child);
			}
		}
	}

	/**
	 * Clear the script 'toolVector', 'toolBar', and vector of
	 * 'scriptMode's. Refill toolBar with clones of the script 
	 * with 'MyTool's that are named or contain #XY command and
	 * with 'MyCanvasMode's. Refill 'scriptMode' vector with
	 * clones of 'MyCanvasMode's. Command/modes currently being 
	 * edited will show only after edit is accepted or canceled.
	 */
	public void repopulateBar() {
		if (rootNode==null) return;
		PackControl.scriptToolHandler.wipeoutTools();
		CursorCtrl.scriptModes.removeAllElements();
		barRecurse(rootNode);
	}

	/**
	 * Recursion to put cmd icons in the scriptBar
	 * @param treeNode
	 */
	public void barRecurse(CPTreeNode treeNode) {
		int count=treeNode.getChildCount();
		for (int j=0;j<count;j++) { 
			CPTreeNode child=treeNode.getChild(j);
			if ((child.tntype==CPTreeNode.COMMAND && (child.isNamed() || child.isXY())) ||
					child.tntype==CPTreeNode.MODE) {
				// deBugging.PrintIcon.printImageIcon(child.tTool.cpIcon.imageIcon,"barRecur");
				PackControl.scriptToolHandler.updateClone(child.tTool);
			}
			else barRecurse(child);
		}
	}

	/**
	 * Use: Creates a new CPTreeNode wrapping the given domNode. 
	 * Note: this is only used when creating a new script from a file.
	 * Caution: may return null.
	 */
	protected CPTreeNode initCPTreeNode(Node domNode) {
		CPIcon cpIcon;
		boolean isDropable=false;

		// Misc stuff: don't want to use, but may want to pass along when
		//   script is saved. Caution: I don't know proper way to handle
		//   most of these DOM types. But, we shouldn't encounter them.
		short ntype=domNode.getNodeType();
		if (ntype==Node.COMMENT_NODE || ntype==Node.ATTRIBUTE_NODE ||
				ntype==Node.NOTATION_NODE || ntype==Node.PROCESSING_INSTRUCTION_NODE) {
			return initOtherTN(domNode.getNodeValue());
		}

		// NOTE: As yet there is no tree node for these
		//   administrative elements. Need checkboxes for
		//   editing these options in the script.

		// Script description: only one allowed, no tree node is created
		if (domNode.getNodeType()==Node.ELEMENT_NODE
				&& domNode.getNodeName().equals("description")) {
			Node node=domNode.getFirstChild();
			if (node!=null && (scriptDescription==null || scriptDescription.trim().length()==0))
				scriptDescription=node.getNodeValue();
			return null; 
		}

		// command??
		if (domNode.getNodeType()==Node.ELEMENT_NODE
				&& domNode.getNodeName().equals("cmd")) {
			String cmd_text = domNode.getFirstChild().getNodeValue();
			String name_text=null;
			String mnem_text=null;
			String tool_tip=null;
			String icon_name=null;

			boolean inLine=true;
			if (domNode.hasAttributes()) {
				NamedNodeMap map = domNode.getAttributes();

				// name?
				Node name = map.getNamedItem("name");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						name_text=tmp_text.trim();
						mnem_text=name_text.substring(0,1);
					}
				}

				// mnemonic?
				name = map.getNamedItem("mnemonic");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						mnem_text=tmp_text.trim().substring(0,1);
					}
				}
				else if (name_text!=null) {
					mnem_text=name_text.substring(0,1);
				}

				// tooltip?
				name = map.getNamedItem("tooltip");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						tool_tip=tmp_text.trim();
					}
					else tool_tip=null;
				}

				// inline?
				name=map.getNamedItem("inline");
				if (name!=null) {
					String tmp_text=name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0 &&
							(tmp_text.trim().charAt(0)=='n' || tmp_text.trim().charAt(0)=='N'))
						inLine=false;
				}

				// iconname?
				name = map.getNamedItem("iconname");
				if (name != null) {
					try {
						StringBuilder tmp_bld = new StringBuilder(name.getNodeValue().trim());
						//						if (tmp_text!=null && tmp_text.trim().length()!=0) {
						icon_name=CPFileManager.getFileName(tmp_bld);
					} catch (Exception ex) {}
				}

				// dropable?
				name=map.getNamedItem("dropable");
				isDropable=false;
				if (name!=null) {
					String tmp_text=name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						tmp_text=tmp_text.trim();
						if (tmp_text.startsWith("y") || tmp_text.startsWith("Y"))
							isDropable=true;
					}
				}

			}

			// set icon
			if (icon_name!=null && icon_name.length()>0) {
				MyToolEditor mte=PackControl.scriptToolHandler.toolEditor;
				int indx=mte.getCPIconIndx("script/"+icon_name);
				if (indx>=0)
					cpIcon=mte.theCPIcons.get(indx).clone();
				else cpIcon=new CPIcon("script/commandIcon.png");
				//					cpIcon=new CPIcon("script/"+icon_name);
				if (mte.getCPIconIndx(cpIcon)<0) {
					mte.theCPIcons.add(cpIcon);
					mte.resetIconList();
				}
			}
			else cpIcon=getNextIcon().clone(); // get random icon

			CPTreeNode tnode=initCmdTN(cmd_text,name_text,mnem_text,tool_tip,inLine,cpIcon,isDropable);
			tnode.isCursor=false;
			tnode.isHandy=false;
			return tnode;
		}

		// mode
		if (domNode.getNodeType()==Node.ELEMENT_NODE
				&& domNode.getNodeName().equals("mode")) {
			String cmd_text = domNode.getFirstChild().getNodeValue();
			String cmd_m2 = null;
			String cmd_m3 = null;
			String mode_name=null;
			String icon_name=null;
			String short_tip=null;
			String tool_tip=null;			
			//			String handy=null;
			boolean hndy=true;
			String pt_str=null; // hot point "x y" string
			Point hotPt=null;

			if (domNode.hasAttributes()) {
				NamedNodeMap map = domNode.getAttributes();

				// name?
				Node name = map.getNamedItem("name");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						mode_name=tmp_text.trim();
					}
				}

				// iconname?
				name = map.getNamedItem("iconname");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						icon_name=tmp_text.trim();
					}
				}

				// handy? default is true, should only look for "no"
				name=map.getNamedItem("handy");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().equals("no")) 
						hndy=false;
				}

				// short_tip?
				name = map.getNamedItem("shorttip");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						short_tip=tmp_text.trim();
					}
				}

				// tip?
				name = map.getNamedItem("tooltip");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						tool_tip=tmp_text.trim();
					}
				}

				// cmd_m2?
				name = map.getNamedItem("cmd2");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						cmd_m2=tmp_text.trim();
					}
				}

				// cmd_m3?
				name = map.getNamedItem("cmd3");
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						cmd_m3=tmp_text.trim();
					}
				}

				// pt_str?
				name = map.getNamedItem("point");
				hotPt=null;
				if (name != null) {
					String tmp_text = name.getNodeValue();
					if (tmp_text!=null && tmp_text.trim().length()!=0) {
						pt_str=tmp_text.trim();
					}
					try {
						Vector<String> ptstr=StringUtil.string2vec(pt_str,false);
						int x=Integer.parseInt(ptstr.get(0));
						int y=Integer.parseInt(ptstr.get(1));
						hotPt=new Point(x,y);
					} catch (Exception ex) {}
				}
			}

			// set icon
			if (icon_name!=null && icon_name.length()>0) {
				MyToolEditor mte=PackControl.scriptToolHandler.toolEditor;
				int indx=mte.getCPIconIndx("script/"+icon_name);
				if (indx>=0)
					cpIcon=mte.theCPIcons.get(indx).clone();
				cpIcon=new CPIcon("script/"+icon_name);
				if (mte.getCPIconIndx(cpIcon)<0) {
					mte.theCPIcons.add(cpIcon);
					mte.resetIconList();
				}
			}
			else cpIcon=getNextIcon().clone(); // get random icon

			CPTreeNode tnode=initModeTN(mode_name,cpIcon,hotPt,
					cmd_text,cmd_m2,cmd_m3,short_tip,tool_tip,hndy);
			tnode.isCursor=true;
			tnode.isHandy=hndy;
			return tnode;
		}

		// designated (versus default) text??
		if (domNode.getNodeType()==Node.ELEMENT_NODE
				&& domNode.getNodeName().equals("text")) {
			Node node=domNode.getFirstChild();
			String text;
			if (node!=null && (text = domNode.getFirstChild().getNodeValue())!=null)
				return initTextTN(text);
			else return null;
		}

		// section??
		else if (domNode.getNodeName().equals("Section")) {
			String title=null;
			if (domNode.hasAttributes()) {
				NamedNodeMap map = domNode.getAttributes();
				Node name = map.getNamedItem("title");
				if (name != null) {
					title=new String(name.getNodeValue().trim());
				}
			}
			return initSectionTN(title);
		}

		// file??
		else if (domNode.getNodeName().equals("file")) {
			String filename=domNode.getFirstChild().getNodeValue();
			return new CPTreeNode(filename.trim(),CPTreeNode.FILE, null);
		}
		else if (domNode.getNodeName().equals("header")) { // header was handled already
			return null;
		}
		// default is probably (??) text.
		String text=domNode.getNodeValue();
		return initTextTN(text);
	}

	/**
	 * Create command CPTreeNode and its MyTool; only called when reading
	 * new script.
	 * MyTools created from scripts are executed first based on 'name',
	 * if given, forcing standard handling, e.g. 'infile_' substitutions,
	 * etc. Otherwise, the command string is executed as with other MyTool's.
	 * (see MyToolListener 'actionPerformed' to see if this is handled correctly.)
	 * @return
	 */
	public CPTreeNode initCmdTN(String cmd_text,String name_text,
			String mnem_text,String tool_tip,boolean inLine,
			CPIcon cpIcon,boolean dropIt) {
		String cmd=null;
		if (cmd_text!=null) cmd=cmd_text.trim().replace('\n',' ');
		if (name_text!=null) name_text=name_text.trim();
		if (mnem_text!=null) mnem_text=mnem_text.trim();
		MyTool myTool=new MyTool(cpIcon,cmd,name_text,mnem_text,
				tool_tip,"SCRIPT:",dropIt,
				PackControl.scriptToolHandler.toolListener);

		if (myTool.isDropable()) {
			DragSource dragSource=DragSource.getDefaultDragSource();
			dragSource.createDefaultDragGestureRecognizer(myTool,DnDConstants.ACTION_LINK,
					new DragGestureListener() {
				public void dragGestureRecognized(DragGestureEvent event) {
					Transferable transferable=new ToolTransferable((MyTool)event.getComponent());
					event.startDrag(null,transferable,new ToolDragSourceListener());
				}
			});
		}

		CPTreeNode tnode=new CPTreeNode(cmd,CPTreeNode.COMMAND,inLine,myTool);
		tnode.isCursor=false;
		tnode.isHandy=false;
		if (tool_tip!=null && tool_tip.trim().length()>0)
			tnode.tipStart=true;
		else 
			tnode.tipStart=false;
		return tnode;
	}

	/**
	 * Create mode CPTreeNode and its MyCanvasMode; only called 
	 * when reading new script.
	 * @return, CPTreeNode
	 */	public CPTreeNode initModeTN(String modename,CPIcon cpIcon,
			 Point hotPt,String cmd_text,String cmd2_text,
			 String cmd3_text,String shorttip,
			 String tip,boolean hndy) {

		 String cmd=null;
		 String cmd2=null;
		 String cmd3=null;
		 if (cmd_text!=null) cmd=cmd_text.trim().replace('\n',' ');
		 if (cmd2_text!=null) cmd2=cmd2_text.trim().replace('\n',' ');
		 if (cmd3_text!=null) cmd3=cmd3_text.trim().replace('\n',' ');
		 MyCanvasMode myMode=new MyCanvasMode(modename,cpIcon,hotPt,
				 cmd,cmd2,cmd3,shorttip,tip,"SCRIPT:",hndy);

		 // re-embellish cpIcon with mouse hot point
		 cpIcon.embellishMe(modename,false,true,hndy,false);
		 myMode.setButtonIcon();

		 myMode.cursorIcon.setImageIcon(myMode.cursorIcon.
				 embellishBase(null,false,true,false,false));

		 if (myMode.isDropable()) {
			 DragSource dragSource=DragSource.getDefaultDragSource();
			 dragSource.createDefaultDragGestureRecognizer((MyTool)myMode,DnDConstants.ACTION_LINK,
					 new DragGestureListener() {
				 public void dragGestureRecognized(DragGestureEvent event) {
					 Transferable transferable=new ToolTransferable((MyTool)event.getComponent());
					 event.startDrag(null,transferable,new ToolDragSourceListener());
				 }
			 });
		 }

		 CPTreeNode tnode= new CPTreeNode(cmd,CPTreeNode.MODE,false,(MyTool)myMode);
		 tnode.isCursor=true;
		 tnode.isHandy=true; // default
		 return tnode;
	 }

	 /**
	  * Create section CPTreeNode, possibly with title
	  * @param title
	  * @return
	  */
	 protected CPTreeNode initSectionTN(String title) {
		 if (title==null || title.trim().length()==0)
			 return new CPTreeNode(null,CPTreeNode.SECTION);
		 return new CPTreeNode(title.trim(),CPTreeNode.SECTION);
	 }

	 /**
	  * Create text CPTreeNode; text is trimmed, stripped of \n. If text was
	  * all whitespace, then return null. This is called only when loading a
	  * new script.
	  * @param text
	  * @return
	  */
	 protected CPTreeNode initTextTN(String text) {
		 if (text==null || text.trim().length()==0) return null;
		 text=text.trim();
		 return new CPTreeNode(text,CPTreeNode.TEXT);
	 }


	 /**
	  * Create "OTHER" catchall CPTreeNode.
	  * @param contentStr
	  * @return
	  */
	 protected CPTreeNode initOtherTN(String contentStr) {
		 return new CPTreeNode(contentStr,CPTreeNode.OTHER,null);
	 }

	 /**
	  * Called when creating new command node; it opens in edit mode.
	  * @return
	  */
	 public CPTreeNode createNEWCmdTN() {
		 MyTool myTool=new MyTool(getNextIcon(),"",null,null,null,"SCRIPT:",
				 false,PackControl.scriptToolHandler.toolListener);
		 return new CPTreeNode("",CPTreeNode.EDIT_CMDorMODE,true,myTool);
	 }

	 /**
	  * Called when creating new file CPTreeNode and its MyTool;
	  * caution, may return null if filename is null or empty.
	  * @param filename
	  * @return
	  */
	 protected CPTreeNode createNEWFileTN(String filename) {
		 if (filename==null || filename.trim().length()==0) return null;
		 return new CPTreeNode(filename.trim(),CPTreeNode.FILE, null);
	 }

	 /**
	  * Create a new CPTreeNode of given type above (abbel=0) or below
	  * (abbel=1) the refNode in its parent's list. Redisplay handled
	  * elsewhere.
	  * @param refNode, CPTreeNode reference node
	  * @param type
	  * @param abbel: create 0=above, 1=below
	  */
	 public void insertNewTN(CPTreeNode refNode,int type,int abbel) {
		 CPTreeNode tn;
		 String orig_name;
		 String tmpName;
		 File temp;

		 if (type==CPTreeNode.FILE) { // open dialog to choose file
			 File f;
			 if ((f=FileDialogs.loadDialog(FileDialogs.FILE,true))==null) return;
			 orig_name=f.getName();
			 // copy into file of same name in temporary directory
			 tmpName=new String(orig_name);
			 temp = new File(System.getProperty("java.io.tmpdir"),tmpName);
			 try {
				 BufferedReader reader = new BufferedReader(new FileReader(f));
				 BufferedWriter writer = new BufferedWriter(new FileWriter(temp));
				 String line;
				 while ((line = reader.readLine()) != null) {
					 writer.write(line);
					 writer.newLine();
				 }
				 reader.close();
				 writer.flush();
				 writer.close();
			 } catch (IOException e) {
				 String errmsg=new String("IOException in copying file for script: "+e.getMessage());
				 PackControl.consoleCmd.dispConsoleMsg(errmsg);
				 PackControl.shellManager.recordError(errmsg);
				 return;
			 }

			 // Create CPTreeNode and add to tree, update file count
			 includeNewFile(orig_name);
			 return;
		 }
		 if (type==CPTreeNode.COMMAND) tn=createNEWCmdTN();
		 else if (type==CPTreeNode.TEXT) tn=new CPTreeNode("",CPTreeNode.EDIT_TEXT);
		 else if (type==CPTreeNode.SECTION) tn=new CPTreeNode("",CPTreeNode.EDIT_SECTION);
		 else return; // no new node

		 //AF>>>//
		 // GUI changes start here.
		 // Changed flow of control to reach end of function once reaching here.
		 // Lock the viewport so GUI changes don't jitter and relocate.
		 ((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		 //<<<AF//

		 // where to put?
		 CPTreeNode par=(CPTreeNode)refNode.getParent();
		 if (refNode.tntype==CPTreeNode.CPSCRIPT) {
			 refNode.stackBox.isOpen=true; // should open when adding
			 cpScriptNode.add(tn);
			 tn.stackBox.myWidth=WIDTH-WIDTH_INC;
			 refNode.stackBox.add(tn.stackBox);
			 tn.stackBox.manager.repopulateRecurse(refNode);
		 }
		 else if (refNode.tntype==CPTreeNode.SECTION && refNode.stackBox.isOpen) {
			 tn.stackBox.myWidth=refNode.stackBox.myWidth-WIDTH_INC;
			 if (abbel==0) { // first spot
				 refNode.insert(tn,0);
				 refNode.stackBox.add(tn.stackBox,0);
			 }
			 else {
				 refNode.add(tn);
				 refNode.stackBox.add(tn.stackBox);
			 }
			 tn.stackBox.manager.repopulateRecurse(par);
		 }
		 else {
			 // if it's a new command or file and is in LINEUP box, must open that
			 if (refNode.tntype==CPTreeNode.COMMAND || refNode.tntype==CPTreeNode.FILE) {
				 if (par.tntype==CPTreeNode.LINEUP)
					 par.stackBox.open();
			 }

			 TreePath path=getPathToNode(refNode);
			 CPTreeNode cpTN=(CPTreeNode)path.getLastPathComponent();
			 par=(CPTreeNode)refNode.getParent();
			 tn.stackBox.myWidth=par.stackBox.myWidth-WIDTH_INC;
			 int index=par.getIndex(cpTN);
			 if (index>=0) {
				 par.insert(tn,index+abbel);
				 par.stackBox.add(tn.stackBox);
				 tn.stackBox.manager.repopulateRecurse(par);
			 }
		 }

		 //AF>>>//
		 // Get a reference to tn.stackBox to pass to the event queue.
		 final StackBox tnStackBox = tn.stackBox;
		 // Redisplay the stack box (implies revalidate()).
		 tnStackBox.redisplaySB(tnStackBox.myWidth);
		 // Unlock the viewport and get the stack box in view.
		 EventQueue.invokeLater(new Runnable() {
			 public void run() {
				 ((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
				 tnStackBox.setViewRect();
			 }
		 });
		 //<<<AF//
	 }

	 /**
	  * Searches for and deletes reference to an included file;
	  * the given name is that held by the file's CPTreeNode.
	  */
	 public void removeIncludedFile(String orig_name) {
		 if (orig_name==null || orig_name.trim().length()==0
				 || rootNode==null || includedFiles==null || includedFiles.size()==0)
			 return;
		 int i;
		 for (i=(cpDataNode.getChildCount()-1);i>=0;i--) {
			 CPTreeNode tn=(CPTreeNode)cpDataNode.getChild(i);
			 if (tn.displayString.equals(orig_name)) cpDataNode.remove(i);
		 }
		 // remove from list
		 for (int j=includedFiles.size()-1;j>=0;j--) {
			 IncludedFile iFile=includedFiles.get(j);
			 if (iFile.origName.equals(orig_name)) {
				 includedFiles.remove(j);
			 }
		 }
		 DataSBox dsb=(DataSBox)cpDataNode.stackBox;
		 dsb.redisplaySB(dsb.myWidth);
		 return;
	 }

	 /**
	  * Finds the next icon (based on 'cmdCount') in 'theCPIcons' list.
	  * @return
	  */
	 public CPIcon getNextIcon() {
		 CPIcon icon=(CPIcon)PackControl.scriptToolHandler.toolEditor.theCPIcons.get(
				 (cmdCount % PackControl.scriptToolHandler.toolEditor.theCPIcons.size()));
		 cmdCount++;
		 return icon;
	 }


	 /**
	  * Creates the default "starter" script file and returns a string 
	  * giving its path.
	  */
	 public String createDefaultScript() {
		 File f=null;
		 try {
			 f = new File(System.getProperty("java.io.tmpdir"),
					 new String("new_script.cps"));
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
			 PackControl.consoleCmd.dispConsoleMsg(errmsg);
			 PackControl.shellManager.recordError(errmsg);
			 return null;
		 }
		 return f.getAbsolutePath();
	 }

	 /**
	  * Creates depthfirst enumeration, finds cpTN, then sets 'nextCmdNode' to
	  * next inline command (strictly) downstream. (Note: probably easiest
	  * to redo enumeration each time.) If cpTN is null, this will initialize
	  * 'nextCmdNode'. If cpTN isn't in tree or has no inline command after
	  * it, then nextCmdNode is set to null (which should be okay).
	  * Caution: right now, can pick up command node being edited; may have
	  * to put more effort into this.
	  * @param cpTN CPTreeNode
	  */
	 public void resetNextCmdNode(CPTreeNode cpTN) {
		 if (rootNode==null) 
			 return;

		 CPTreeNode firstCmdNode=null;
		 Enumeration<TreeNode> tEnum=rootNode.preorderEnumeration();
		 // cpTN to CPSCRIPT (so it will be downstream from rootNode)
		 if (cpTN==null) cpTN=cpScriptNode;

		 CPTreeNode node=cpTN;
		 // first task is find cpTN; also, observe what is first command node
		 while(tEnum.hasMoreElements()) {
			 node=(CPTreeNode)tEnum.nextElement();
			 if (firstCmdNode==null && (node.tntype==CPTreeNode.COMMAND 
					 || node.tntype==CPTreeNode.EDIT_CMDorMODE))
				 firstCmdNode=node;
			 if (node==cpTN) break;
		 }
		 if (node!=cpTN) { // didn't find cpTN; this shouldn't happen
			 nextCmdNode=null;
			 return;
		 }

		 // Found cpTN: now find next active command beyond (not equal)
		 while(tEnum.hasMoreElements()) {

			 // remove next icon from old command
			 nextCmdNode=null;
			 StackBox oldsb=cpTN.stackBox;
			 if (oldsb instanceof CmdSBox) oldsb.redisplaySB(oldsb.myWidth);

			 node=(CPTreeNode)tEnum.nextElement();
			 CPTreeNode tN=node;
			 if ((tN.tntype==CPTreeNode.COMMAND || tN.tntype==CPTreeNode.EDIT_CMDorMODE)
					 && tN.isInline()) {
				 nextCmdNode=node;
				 if (cpTN.tntype==CPTreeNode.COMMAND
						 || cpTN.tntype==CPTreeNode.EDIT_CMDorMODE) { // redo former one
					 CmdSBox cb=(CmdSBox)cpTN.stackBox;
					 cb.redisplaySB(cb.myWidth);
				 }
				 CmdSBox cb=(CmdSBox)nextCmdNode.stackBox;
				 cb.redisplaySB(cb.myWidth);

				 // set enabled status of "next" and "top" icons in all 'NextBundle's
				 PackControl.scriptBar.nextBundle.enableNext((boolean)(nextCmdNode!=null));
				 PackControl.vertScriptBar.nextBundle.enableNext((boolean)(nextCmdNode!=null));

				 boolean ok=(boolean)(firstCmdNode!=null && nextCmdNode!=firstCmdNode);
				 PackControl.scriptBar.nextBundle.enableTop(ok);
				 PackControl.vertScriptBar.nextBundle.enableTop(ok);

				 return;
			 }
		 }

		 // didn't find any more command nodes
		 nextCmdNode=null;
		 //AF>>>//
		 // Removed scroll-to-bottom behavior on script completion.
		 // Feel free to delete.
		 //int scrollMaximum = PackControl.scriptHover.stackScroll.getVerticalScrollBar().getMaximum();
		 //PackControl.scriptHover.stackScroll.getVerticalScrollBar().setValue(scrollMaximum);
		 //<<<AF//

		 // set enabled status of "next" and "top" icons in all 'NextBundle's
		 PackControl.scriptBar.nextBundle.enableNext(false);
		 PackControl.vertScriptBar.nextBundle.enableNext(false);
		 PackControl.scriptBar.nextBundle.enableTop((boolean)(firstCmdNode!=null));
		 PackControl.vertScriptBar.nextBundle.enableTop((boolean)(firstCmdNode!=null));

		 return;
	 }

	 /**
	  * Need to cancel former nextCmdNode??
	  */
	 public void resetNextCmdNode() {
		 if (nextCmdNode!=null
				 && (nextCmdNode.tntype==CPTreeNode.COMMAND
				 || nextCmdNode.tntype==CPTreeNode.EDIT_CMDorMODE)) {
			 StackBox sb=nextCmdNode.stackBox;
			 nextCmdNode=null;
			 sb.redisplaySB(sb.myWidth);
		 }
		 resetNextCmdNode(nextCmdNode);
	 }

	 /**
	  * Compute the height of a node in stack area 
	  * for stackScroll display. Use 'preorder' (reverse 
	  * of depth first) enumeration, which is the 
	  * display order.
	  * @param tnode CPTreeNode
	  * @return int, 0 on error
	  */
	 public int getBoxPoint(CPTreeNode cpTN) {
		 int height=0;
		 int extra=20; // how much space between elements?
		 if (cpTN==null) 
			 return (0);

		Enumeration<TreeNode> tEnum=rootNode.preorderEnumeration();

		 // add heights until you reach given node
		 CPTreeNode node=cpTN;
		 while(tEnum.hasMoreElements()) {
			 node=(CPTreeNode)tEnum.nextElement();
			 if (node.tntype==CPTreeNode.CPSCRIPT) {
				 ScriptSBox ssb=(ScriptSBox)node.stackBox;
				 height +=ssb.getHeaderHeight()+extra;
			 }
			 else if (node.tntype==CPTreeNode.SECTION) {
				 SectionSBox ssb=(SectionSBox)node.stackBox;
				 height +=ssb.getHeaderHeight()+extra;
			 }
			 else if (node.tntype==CPTreeNode.CPDATA  || node==cpTN)
				 return Math.max(0,height);
			 else if (node.tntype!=CPTreeNode.ROOT) height += node.stackBox.getHeight()+extra;
		 }
		 return 0; // shouldn't reach here
	 }

	 /**
	  * Returns the string for the current command node if it is active. (I think
	  * it's active unless it's the last one and has already been executed.)
	  * @return String
	  */
	 public String getCommandString() {
		 if (nextCmdNode==null)
			 return null;
		 return nextCmdNode.tTool.getCommand();
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
	  * When saving, need to first resolve all open edit decisions; for now,
	  * accept them all. May want to rethink this later.
	  */
	 public void acceptAllEdits() {
		 if (rootNode==null) return;
		 Enumeration<?> bFirst=rootNode.breadthFirstEnumeration();
		 while (bFirst.hasMoreElements()) {
			 CPTreeNode tN=(CPTreeNode)bFirst.nextElement();
			 if (tN.stackBox!=null) tN.stackBox.acceptEdit();
		 }
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
					 PackControl.consoleCmd.dispConsoleMsg(errmsg);
					 PackControl.shellManager.recordError(errmsg);
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
		 if ((url= FileUtil.tryURL(name))==null) {
			 String errmsg=new String("IOException in finding "+name);
			 PackControl.consoleCmd.dispConsoleMsg(errmsg);
			 PackControl.shellManager.recordError(errmsg);
			 url=null;
		 }

		 return url;
	 }

	 /**
	  * Use: Loads script 'name' from file or from the web; if 'keepName'
	  * true, adds name to the 'script_URLs' file. Attempt to set stackScroll
	  * bar in the right spot.
	  * @param name String, may be temp name (e.g., loaded from web)
	  * @param oridName String, name to save under
	  * @param keepName boolean: true, then record origName in maintained list
	  * @return 0 on failure
	  */
	 public int loadNamedScript(String name,String origName,boolean keepName) {
		 if (name==null) return 0;
		 URL url=getScriptURL(name);
		 if (url==null) 
			 return 0;
		 int result=0;
		 if (hasChanged) { // has there been editing in current script?
			 result=queryUserForSave();
		 }
		 if (result<0) 
			 return 0; // closed or cancelled
		 String endname=url.toString(); // Note: seems to convert separator to standard '/'
		 int k=endname.lastIndexOf('/');
		 if (k>0 && k<endname.length())
			 endname=endname.substring(k+1);
		 scriptName=endname;

		 // TODO: Alex, how do I find if this is a file? If it is (and name is not 'new_script'),
		 //    may want to change 'scriptDirectory'.
		 // here is actual loading
		 if (scriptLoader.loadScriptURL(url)) {
			 cmdOpenMode=false; 
			 PackControl.scriptBar.setOpenAllButton(false);
			 repopulateBar();
			 populateDisplay();
			 try {
				 /*
				 StringBuilder fulname = new StringBuilder();
				 if (url.getAuthority()!=null)
					 fulname.append(url.getAuthority());
				 fulname.append(url.getPath());
				 */
				 if (keepName) ScriptBundle.m_locator.
				 	add2List(origName.toString(),false); //true);
			 } catch (Exception ex) {
				 throw new InOutException("exception in saving script file name");
			 }
			 CirclePack.cpb.msg("Loaded script: "+name);
			 PackControl.scriptHover.scriptTitle(scriptName,false);
			 resetNextCmdNode(null);
			 // Tell Swing to redo the layout because it has changed.
			 PackControl.scriptHover.revalidate();
			 // Push viewport change to EventQueue end, in case queued events change the view.
			 EventQueue.invokeLater(new Runnable() {
				 public void run() {
					 // Scroll to top.
					 PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0,0));
				 }
			 });
			 return 1;
		 }
		 return 0;
	 }

	 /**
	  * Given script filename (or name from pop up dialog, 
	  * or file chosen in browser), read in script and search 
	  * for/execute 'EOL' command (execute on load).
	  * Note: filename may be temp file (e.g. if file is downloaded from the web),
	  * so we provide 'origName' to saving in the list.
	  * @param filename String
	  * @param origName String, name to save under
	  * @param keepname boolean: true, then store in list of names.
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
	  * Takes a file already created in java.io.tmpdir and adds it to the
	  * script; a new random 'id' prefix is automatically appended and the
	  * file is added to 'includedFiles', with data 'type' determined by
	  * filename extension.  
	  * @param newName String, duplicate names not allowed.
	  */
	 public void includeNewFile(String newName) {
		 if (newName==null || newName.trim().length()==0 || dup_name(newName)) {
			 String errmsg="Error: proposed name must not be empty or a duplicate";
			 PackControl.consoleCmd.dispConsoleMsg(errmsg);
			 PackControl.shellManager.recordError(errmsg);
			 return;
		 }

		 int new_id = new Random().nextInt(32000); 
		 File newFile = CPFileManager.renameTmpFile(newName,new String(new_id+newName));
		 int datatype=IncludedFile.setDataType(newName,newFile);
		 if (newFile!=null) {
			 newFile.deleteOnExit();
			 if (datatype==IncludedFile.ABOUT_IMAGE) // put at front
				 includedFiles.insertElementAt(new IncludedFile(datatype,newName,newFile),0);
			 else 
				 includedFiles.add(new IncludedFile(datatype,newName,newFile));
			 if (!hasChanged) {
				 hasChanged = true;
				 PackControl.scriptHover.scriptTitle(
						 PackControl.scriptManager.scriptName,true);
			 }
			 CPTreeNode cptn=cpDataNode;
			 CPTreeNode tn=new CPTreeNode(newName.trim(),CPTreeNode.FILE, null);
			 if (datatype==IncludedFile.ABOUT_IMAGE)
				 cptn.insert(tn,0); // put at front
			 else cptn.add(tn);
			 DataSBox dsb=(DataSBox)cptn.stackBox;
			 tn.stackBox.myWidth=cptn.stackBox.myWidth-WIDTH_INC;
			 dsb.updateCount();
			 if (dsb.isOpen) {
				 dsb.add(tn.stackBox);
				 redisplayCPdataSB();
			 }
			 repopulateRecurse(cptn);
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
	  * Get command string for a named command in script:
	  * mode=0, first character of name matches first char of key
	  * mode=1, name matches key.
	  */
	 public String findCmdByName(String key,int mode) {
		 if (key==null || key.trim().length()==0) return null;
		 key=key.trim();
		 Enumeration<?> bFirst=rootNode.breadthFirstEnumeration();
		 while (bFirst.hasMoreElements()) {
			 CPTreeNode tN=(CPTreeNode)bFirst.nextElement();
			 if (tN.isNamed()) {
				 String tnname=tN.getName();
				 boolean okay=false;
				 if (mode==0) {
					 char c=key.charAt(0);
					 if (tnname!=null && c==tnname.charAt(0)) okay=true;
				 }
				 else if (mode==1 && key.equals(tnname)) 
					 okay=true;
				 // return cmd to execute. (For mode, this "mode_change <name>"
				 if (okay) {
					 if (tN.tntype==CPTreeNode.COMMAND) // command to e
						 return tN.tTool.getCommand();
					 if (tN.tntype==CPTreeNode.MODE)
						 return new String("mode_change "+tN.tTool.nameString);
				 }
			 }
		 }  // end of while
		 return null;
	 }

	 /**
	  * Find and execute script command when key is pressed in 'ActiveWrapper';
	  * commands apply to the associated packing.
	  * @param e, KeyEvent
	  * @param key, String
	  */
	 public void executeCmdByKey(KeyEvent e,String key) {
		 String cmd=findCmdByName(key,0); // find based on first char only
		 Component myComp=e.getComponent();
		 if (cmd==null || !(myComp instanceof ActiveWrapper)) return;
		 ActiveWrapper aWrapper=(ActiveWrapper)myComp;
		 CPdrawing cpS=aWrapper.getCPDrawing();
		 PackData thePack=cpS.getPackData();

		 // check command for variables '#..': Currently check only ' #XY'
		 if (cmd.contains(" #XY") || cmd.contains(" #xy")) { // command requires mouse location;
			 Point mousept;
			 try {
				 mousept=MouseInfo.getPointerInfo().getLocation();
			 } catch(Exception exc) {
				 String errmsg="Error getting mouse location";
				 PackControl.consoleCmd.dispConsoleMsg(errmsg);
				 PackControl.shellManager.recordError(errmsg);
				 return;
			 }
			 Dimension dim=aWrapper.getSize();
			 // get screen coords of 'activeScreen' top left corner
			 Point onScreen=aWrapper.getLocationOnScreen(); 
			 Point pt=new Point(mousept.x-onScreen.x,mousept.y-onScreen.y);
			 if (pt.x<=0 || pt.y<=0 || pt.x>=dim.width || pt.y>=dim.height) {

				 String errmsg="Mouse not in ActiveCanvas";
				 PackControl.consoleCmd.dispConsoleMsg(errmsg);
				 PackControl.shellManager.recordError(errmsg);
				 return; // mouse was not in the ActiveCanvas
			 }
			 Point2D.Double pot=cpS.pt2RealPt(pt, dim.width,dim.height);
			 String subxy=new String(" "+pot.x+" "+pot.y+" ");
			 String newCmd=cmd.replaceAll(" #XY",subxy).replaceAll(" #xy",subxy);
			 // NOTE: for spherical packing, parser must convert to real point 
			 CPBase.trafficCenter.parseWrapper(newCmd,thePack,false,true,0,null);
			 return;
		 }

		 if (cmd!=null && cmd.length()>0) {
			 CPBase.trafficCenter.parseWrapper(cmd,thePack,true,true,0,null);
		 }
	 }

	 /**
	  * Return the 'nameString' of nextCmdNode.tTool
	  * @return String
	  */
	 public String getNextCmdName() {
		 if (nextCmdNode==null || nextCmdNode.tTool==null ||
				 nextCmdNode.tTool.nameString==null) return null;
		 return new String(nextCmdNode.tTool.nameString);
	 }

	 /**
	  * Execute the (inline) command given in nextCmdNode and then
	  * reset nextCmdNode.
	  */
	 public void executeNextCmd() {
		 if (nextCmdNode == null)
			 return;
		 String s = getCommandString();
		 String oldname=new String(PackControl.scriptManager.scriptName);
		 if (s!=null) {

			 // TODO: part of continuing problem: want thread in many cases, e.g. shows
			 //   progress bar, but don't want it in others. 
			 boolean useThread=true;

			 CPBase.trafficCenter.parseWrapper(s,CirclePack.cpb.getActivePackData(),false,useThread,0,null);
			 // if command loaded new script, avoid resetting 'next'
			 if (!PackControl.scriptManager.scriptName.equals(oldname)) return; 
		 }
		 resetNextCmdNode(nextCmdNode);
	 }



	 /**
	  * Find the tree path to given CPTreeNode.
	  * @param node
	  * @return
	  */
	 public TreePath getPathToNode(CPTreeNode node) {
		 Enumeration<?> bFirst=rootNode.breadthFirstEnumeration();
		 while (bFirst.hasMoreElements()) {
			 CPTreeNode cptn=(CPTreeNode)bFirst.nextElement();
			 if (cptn==node) {
				 return new TreePath(cptn.getPath());
			 }
		 }
		 return null;
	 }

	 protected void fireTreeStructureChanged(TreePath path) {
		 int length = treeModelListeners.size();
		 TreeModelEvent evt = new TreeModelEvent(this, path);
		 for (int i = 0; i < length; i++) {
			 treeModelListeners.elementAt(i)
			 .treeStructureChanged(evt);
		 }
	 }

	 /**
	  * Notify listeners that a new object exists at the given path. 
	  * path is the TreePath to the node inserted
	  */
	 protected void fireTreeNodesInserted(TreePath path) {
		 CPTreeNode changedChild = (CPTreeNode)path.getLastPathComponent();
		 CPTreeNode parent = (CPTreeNode)path.getParentPath().getLastPathComponent();
		 int changedIndex = parent.getIndex(changedChild);

		 int[] index = { changedIndex };
		 Object[] child = { changedChild };
		 TreeModelEvent evt = new TreeModelEvent(this, path.getParentPath(),
				 index, child);

		 int length = treeModelListeners.size();
		 for (int i = 0; i < length; i++) {
			 treeModelListeners.elementAt(i)
			 .treeNodesInserted(evt);
		 }
	 }



	 /**
	  * Notify listeners that the path given has had a child deleted. path is the
	  * TreePath to the Parent of the deleted node
	  */
	 protected void fireTreeNodesRemoved(TreePath path, int index,
			 CPTreeNode deletedChild) {
		 int indeces[] = { index };
		 Object[] child = { deletedChild };

		 TreeModelEvent evt = new TreeModelEvent(this, path, indeces, child);
		 int length = treeModelListeners.size();
		 for (int i = 0; i < length; i++) {
			 treeModelListeners.elementAt(i)
			 .treeNodesRemoved(evt);
		 }
	 }

	 /**
	  * Use: gives options for saving a script when changes have been made.
	  * If the answer is yes, a save dialog is displayed otherwise the
	  * changes are lost. 
	  * @return int, only -1 (=cancel/close dialog) has effect in calling routine.
	  */
	 public int queryUserForSave() {
		 Object[] options={"Yes, save","No, Discard","Cancel"};
		 if (!hasChanged) return 1;
		 int result = JOptionPane.showOptionDialog(null, // PackControl.scriptFrame,
				 "File contents have changed.\n"
				 + "Would you like to save before proceeding?", "Save?",
				 JOptionPane.YES_NO_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE,null,
				 options,options[0]);

		 if (result == JOptionPane.YES_OPTION) {
			 File f;
			 if ((f=FileDialogs.saveDialog(FileDialogs.SCRIPT,true))!=null) {
				 tnWriter.Write_from_TN(f);
				 hasChanged=false;
				 PackControl.scriptHover.scriptTitle(scriptName,false);
			 }
			 return 1;
		 } 
		 else if (result == JOptionPane.CLOSED_OPTION
				 || result == JOptionPane.CANCEL_OPTION) {
			 return -1;
		 }
		 return 0; // no
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

	 /**
	  * Actions initiated in the 'scriptAction' panel and button presses
	  * come through this method.
	  */
	 public void actionPerformed(ActionEvent e) {
		 String command = e.getActionCommand();
		 Object source = e.getSource();

		 if (source instanceof JTextField) {
		 }
		 else {
			 // scriptAction tool actions
			 if (command.equals("SCRIPT:Load script file")) {
				 int n=CPBase.scriptManager.getScript(null,null,true);
				 if (n>0) {
					 ScriptBundle.m_locator.setSuccess();
				 }
			 } 
			 else if (command.equals("SCRIPT:open browser")) {
				 PackControl.browserFrame.setVisible(true);
				 PackControl.browserFrame.setState(Frame.NORMAL);
			 }
			 else if (command.equals("SCRIPT:New script")) {
				 String tmpname=createDefaultScript();
				 if (tmpname!=null) {
					 loadNamedScript(tmpname,tmpname,true);
					 PackControl.aboutFrame.setVisible(false);
				 }
				 else {
					 String errmsg="Failed to create starter script";
					 PackControl.consoleCmd.dispConsoleMsg(errmsg);
					 PackControl.shellManager.recordError(errmsg);
				 }
			 } 
			 else if (command.equals("SCRIPT:Next script cmd")) {
				 executeNextCmd();
				 // AF:
				 if (nextCmdNode!=null) nextCmdNode.stackBox.setViewRect(); // scroll new 'next' into view
			 } 
			 else if (command.equals("SCRIPT:Reset script")) {
				 resetNextCmdNode();
				 if (nextCmdNode!=null) redisplayCPscriptSB();
				 // AF: 
				 PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0,0));
			 } 
			 else if (command.equals("SCRIPT:Save script")) {
				 File f;
				 if ((f=FileDialogs.saveDialog(FileDialogs.SCRIPT,true))!=null) {
					 tnWriter.Write_from_TN(f);
					 hasChanged=false;
					 try {
						 ScriptBundle.m_locator.
						 	add2List(f.getCanonicalPath(),false);
					 } catch (Exception ex){}
					 scriptName=f.getName();
					 PackControl.scriptHover.scriptTitle(scriptName,false);
				 }
			 }
			 // end of scriptAction tools
		 }
	 }

	 /**
	  * Get and scale the image associated with the script (and appearing
	  * in the "About" frame). Also, set 'scriptTagname'. If 'scriptTagname' 
	  * is empty, then look first for 'AboutImage', then for default; 
	  * leave 'scriptTagname' blank in these cases. 
	  * 
	  * TODO: allow loading of file named 'AboutImage.*', in which case it is
	  * stored as the 'AboutImage', knocking out any existing; 'scriptTagname'
	  * is then blanked.
	  * 
	  * If 'tagname' is not empty, look in order:
	  * (1) among CPData "IMAGE" files in the script
	  * (2) in '~/myCirclePack/' directory; or 
	  * (3) in '/Icons/tags/'; 
	  * 
	  * Then 
	  * (4) see if there's an 'AboutImage', empty 'scriptTagname'.
	  * (5) settle for default 'myCPtag.jpg' and error msg.

	  * @param tagname
	  * @return, @see ImageIcon, null on error
	  */
	 public ImageIcon getTagImage(String tagname) {
		 URL url=null;
		 File aboutFile=null;
		 
		 if (tagname!=null && (tagname=tagname.trim()).length()>0) {
			 // first search among script's file
			 try {
				 for (int j=includedFiles.size()-1;j>=0;j--) {
					 IncludedFile iFile=includedFiles.get(j);
					 if (iFile.origName.equals(tagname)) 
						 url=new URL("file:///"+iFile.tmpFile);
				 }
			 } catch(Exception ex) {
				 CirclePack.cpb.errMsg("Problem getting 'tag' file from script");
			 }				

			 // now search 'myCirclePack'
			 if (url==null) {
				 try {
					 String pth=new String(CPFileManager.HomeDirectory+File.separator+"myCirclePack"+
							 File.separator+tagname);
					 if (new File(pth).exists()) { 
						 url=new URL("file:"+pth);
						 scriptTagname=new String(tagname);
					 }
				 } catch (Exception ex) {
					 CirclePack.cpb.errMsg("tag imagefile problem");
					 return null;
				 }
			 }

			 // otherwise, search resources 
			 if (url==null) {
				 url=CPBase.getResourceURL("/Icons/tags/"+tagname);
			 }
					 
			 if (url!=null) {
				 scriptTagname=new String(tagname);
			 }
		 }
//				 if (url==null) {
//					 CirclePack.cpb.errMsg("failed loading script tag image '"+tagname+"'");
//					 url=CPBase.getResourceURL("/Icons/tags/myCPtag.jpg");
//					 CirclePack.cpb.errMsg("try default tag 'myCPtag.jpg'");
//				 }
			

		 // now look for 'AboutImage', then default
		 else if (url==null) {
			 aboutFile=this.getAboutTmpFile();
			 if (aboutFile!=null) {
				 scriptTagname=new String("");
				 tagname=new String("");
			 }

			 // go for default?
			 if (aboutFile==null) {
				 url=CPBase.getResourceURL("/Icons/tags/myCPtag.jpg");
				 if (url!=null) {
					 scriptTagname=new String("");
					 tagname=new String("");
				 }
				 else {
					 CirclePack.cpb.errMsg("failed loading default script tag");
					 return null;
				 }
			 }
		 }		 
		 
		 if (url==null && aboutFile==null) {
			 CirclePack.cpb.errMsg("failed loading script tag image '"+tagname+"'");
			 return null;
		 }

		 // load/resize image
		 BufferedImage bI=null;
		 try {
			 if (url!=null) 
				 bI = ImageIO.read(url);
			 else { 
				 bI=ImageIO.read(aboutFile);
				 if (bI==null)
					 throw new DataException("'about' problem: mayby not decoded?");
			 }
		 } catch (Exception e) {
			 CirclePack.cpb.errMsg("Error loading 'tag' image: "+e.getMessage());
			 return null;
		 }
		 
		 ImageIcon iI=new ImageIcon(util.GetScaleImage.scaleBufferedImage(bI,AboutFrame.ABOUTWIDTH,AboutFrame.ABOUTHEIGHT));
		 return iI;
	 }

	 public boolean getOpenMode() {
		 return cmdOpenMode;
	 }

	 public void setOpenMode(boolean open) {
		 cmdOpenMode=open;
	 }

	 /**
	  * Use: Returns the root CPTreeNode.
	  */
	 public Object getRoot() {
		 return rootNode;
	 }
	 /**
	  * Toggle 'cmdOpenMode' (whether all command boxes are open or closed) 
	  */
	 public void toggleCmdOpenMode() {
		 if (cmdOpenMode) {
			 cmdOpenMode=false;
			 PackControl.scriptBar.setOpenAllButton(false);
		 }
		 else {
			 cmdOpenMode=true;
			 PackControl.scriptBar.setOpenAllButton(true);
		 }
	 }

	 /**
	  * Check for 'About' image in the includedFiles and return the
	  * temporary File where it's stored.
	  * @return File or null on error
	  */
	 public File getAboutTmpFile() {
		 File aboutFile=null;
		 try {
			 for (int j=includedFiles.size()-1;j>=0;j--) {
				 IncludedFile iFile=includedFiles.get(j);
				 if (iFile.origName.startsWith("AboutImage")) {
					 aboutFile=iFile.tmpFile;
					 if (aboutFile.exists())
						 return aboutFile;
					 else
						 throw new InOutException("Putative 'About' file, "+aboutFile.getName()+" does not exist");
				 }
			 }
//			 CirclePack.cpb.errMsg("There seems to be no 'AboutImage' for this script");
			 return null;
		 } catch(Exception ex) {
//			 CirclePack.cpb.errMsg("Problem getting 'AboutImage' file from script");
			 return null;
		 }				
	 }

}
