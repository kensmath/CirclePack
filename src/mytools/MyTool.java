package mytools;

import java.awt.Color;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionListener;

import javax.swing.JButton;

import allMains.CPBase;
import allMains.CirclePack;
import canvasses.ActiveWrapper;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import dragdrop.ToolDragSourceListener;
import dragdrop.ToolTransferable;
import images.CPIcon;
import packing.PackData;
import util.PopupBuilder;

/**
 * Creates a JButton with icon (eventually in various states),
 * associated CmdString (with name, mnemonic, tooltip, command, 
 * dropability, etc.) useful for implementing various actions.
 * 
 * For MyTool's in tool bars, execution is one of two methods: 
 * 1. If tool is 'dropable', then execute by drag/drop on a canvas. 
 * 2. Otherwise, tool should be associated with an 'ActiveWrapper' 
 * canvas and uses its 'getScreen' method to find the associated 
 * 'CPScreen' to be used in 'jexecute' calls.
 * 
 * MyTool's are also used in scripts.
 * @author kens
 */
public class MyTool extends JButton {

	private static final long 
	serialVersionUID = 1L;
	
	public CPIcon cpIcon; // icon and its filename
	private String cmdString; // CirclePack commands
	public String nameString; // Name, for menu lookups
	public String mnemonic; // one letter/digit mnemonic
	private String toolTip; // hovering tool tip
	public String toolType;  // see 'PackControl.toolixes'
	public boolean dropable; // drop on canvass or just click?
	
	public Object toolObject;  // Need to attach object? e.g., Mobius
	public ActiveWrapper activeWrapper;
	public PopupBuilder popUpMenu;
	public ActionListener actListener;
	
	// Constructors
	public MyTool() { // empty tool, e.g., for cloning
		super(null,null); // setIcon later
		cpIcon=null;
		cmdString=null;
		nameString=null;
		mnemonic=null;
		toolTip=null;
		toolType=null;
		dropable=false;
		toolObject=null;
		activeWrapper=null;
		popUpMenu=null;
		actListener=null;
	}
	
	/**
	 * Create 'MyTool' JButton
	 * @param cpIc, CPIcon (often created from icon image name)
	 * @param cmdstr
	 * @param name 
	 * @param mnem, String, first character is keyboard shortcut
	 * @param tip, ToolTipText
	 * @param tool_type (e.g., MISC) 
	 * @param dropit, boolean, dropable on canvas?
	 * @param listener, ActionListener
	 * @param pum, PopupBuilder (for popup menus)
	 */
	public MyTool(CPIcon cpIc,String cmdstr,String name,String mnem,
			String tip,String tool_type,boolean dropit,
			ActionListener listener,PopupBuilder pum) {
		super(null,null); // setIcon later
		popUpMenu=pum;
		toolObject=null;
		if (cpIc==null) cpIcon=PackControl.defaultCPIcon;
		else cpIcon=cpIc;

		cmdString=detailed(cmdstr);
		nameString=detailed(name).replace('\"','\'');
		mnemonic=detailed(mnem).replace('\"','\'');
		toolTip=detailed(tip).replace('\"','\'');
		if (toolTip.length()==0) toolTip=null;

		// set up as drag/drop source
		toolType=detailed(tool_type).replace('\"','\'');
		if (toolType==null) // default is MYTOOL:
			toolType=CPBase.tooltypes[2];
		dropable=dropit; // default is true
		actListener=listener;
		if (actListener!=null) this.addActionListener(actListener);

		// create embellished tool icon: teardrop, popup
		if (!toolType.startsWith(("XTEN")))
				cpIcon.embellishMe(mnemonic,((cmdString.contains("#XY") || cmdString.contains("#xy")) 
						&& dropable),false,false, popUpMenu!=null);
//		 deBugging.PrintIcon.printImageIcon(cpIcon.imageIcon,new String("embellish"+mnemonic));
		// the actioncommand should be the 'key' used in hashedTools.
		setActionCommand((String)this.getKey());
		
		setPreferredSize(cpIcon.getDimension());
		setRolloverEnabled(true);
		setBorderPainted(false);
		setBackground(Color.white);
		setOpaque(false);
		if (toolTip == null || toolTip.length()==0) 
			setToolTipText(cmdString);
		else 
			setToolTipText(toolTip);

		// 'activeWrapper' null unless tool is attached to one of canvasses.
		// Must be set by the routine calling for this mytool.
		activeWrapper = null;
		
		// add to hashedTools
		CPBase.hashedTools.put(getKey(),this);
		
		// add icon to underlying JButton
		setButtonIcon(); 
		
	}
	
	public MyTool(CPIcon cpIc,String cmdstr,String name,String mnem,
			String tip,String tool_type,boolean dropit,
			ActionListener listener) {
		this(cpIc,cmdstr,name,mnem,tip,tool_type,dropit,listener,
				(PopupBuilder)null);
	}
	
	public CPIcon getCPIcon() {
		return cpIcon;
	}
	
	/**
	 * update the JButton with cpIcon image (eg, after embellishing)
	 */
	public void setButtonIcon() {
		setIcon(cpIcon.getImageIcon());
	}

	public void setCPIcon(CPIcon cpi) {
		cpIcon=cpi;
	}
	
	public void setObject(Object obj) {
		toolObject=(Object)obj;
	}
	
	public Object getObject() {
		return (Object)toolObject;
	}

	public String getCommand() {
		return cmdString;
	}
	
	/**
	 * Return current 'toolTip', trimmed. 
	 * @return null if null or trims to 0 length
	 */
	public String getToolTip() {
		if (toolTip==null || toolTip.trim().length()==0) {
			toolTip=null;
			return toolTip;
		}
		return toolTip.trim();
	}

	/**
	 * Set the 'cmdString'; null if str is null or empty.
	 * @param str
	 */
	public void setCommand(String str) {
		if (str==null || str.length()==0)
			cmdString=null;
		else cmdString=detailed(str);
	}

	public String getName() {
		return nameString;
	}

	public boolean isNamed() {
		if (nameString==null || nameString.trim().length()==0) return false;
		return true;
	}

	public void setName(String str) {
		nameString=detailed(str);
	}

	public String getMnem() {
		return mnemonic;
	}

	public void setMnem(String str) {
		mnemonic=detailed(str);
	}

	/**
	 * Set internal 'toolTip' string and set tooltip text.
	 * If 'toolTip' is null, default to use 'cmdString' as
	 * the tooltip text. 
	 * @param str
	 */
	public void setToolTip(String str) {
		if (str==null) {
			toolTip=null;
			if (cmdString!=null)
				setToolTipText(cmdString);
		}
		else { 
			toolTip=detailed(str).replace('\"','\'');
			setToolTipText(toolTip);
		}
	}

	public String getToolType() {
		return toolType;
	}

	public boolean isDropable() {
		return dropable;
	}

	/**
	 * Make sure there's a name to use in hashing: for typical command
	 * tools, this is 'cmdString', else the tool's 'nameString'; for
	 * modes, this is preceded by "Mode: ".
	 * @return
	 */
	public String formName() {
		StringBuilder strbuf=new StringBuilder();
		if (this instanceof MyCanvasMode) {
			strbuf.append("Mode: ");
		}
		if (cmdString!=null && cmdString.length()>0) 
			strbuf.append(cmdString);
		else if (nameString!=null && nameString.length()>0) 
			strbuf.append(nameString);
		else 
			strbuf.append("no cmd or name");
		return strbuf.toString();
	}

	public void formMnem() { // mnemonic to null or first char of name
		if (nameString!=null && nameString.length()>0) mnemonic=nameString.substring(0,1);
		else mnemonic=null;
	}

	// the 'key' is used in hashedTools; should be unique
	public String getKey() {
		return new String(toolType+formName());
	}
	
	public PopupBuilder getPopUpMenu() {
		return popUpMenu;
	}

	/**
	 * Utility: returns a new trimmed string, '\n' replaced 
	 * by ' ', or a non-null but empty string
	 * @param str
	 * @return
	 */
	public String detailed(String str) {
		if (str==null || str.trim().length()==0) return new String("");
		return str.trim().replace('\n',' ');
	}

	/**
	 * Execute 'cmdString'; ends up going through 'parseWrapper' with
	 * separate thread.
	 */
	public void execute() { // apply to canvas packing or active packing
		if (activeWrapper!=null) // tool created to apply to particular window
			execute(activeWrapper.getCPScreen().getPackData());
		else execute(CirclePack.cpb.getActivePackData());
	}

	/** 
	 * Execute 'cmdString' via 'parseWrapper' and separate execution thread.
	 * @param p, PackData
	 */
	public void execute(PackData p) { // apply to specified packing
		if (cmdString!=null) { 
			// note: last 'true' means to allow separate thread for execution.
			CPBase.trafficCenter.parseWrapper(cmdString,p,false,true,0,null);
			
		}
	}
	
	/**
	 * May need to clone tools: e.g., when adding to script toolbar or 
	 * copying between tool bars. Note: does not put in hashedtools, in
	 * tool bars, or in mode vectors.
	 * @return 'MyTool' or 'MyCanvasMode' as appropriate
	 */
	public MyTool clone() {
		if (this instanceof MyCanvasMode) {
			MyCanvasMode tm=(MyCanvasMode)this;
			// Note: 'menuItem' is null in the clone
			MyCanvasMode ct=new MyCanvasMode();
			if (cpIcon!=null) ct.cpIcon=cpIcon.clone();
			if (cmdString!=null) ct.setCommand(new String(cmdString));
			if (nameString!=null) ct.nameString=new String(nameString);
			if (mnemonic!=null) ct.mnemonic=new String(mnemonic);
			if (toolTip==null || toolTip.length()==0)
				ct.setToolTipText(ct.getCommand());
			else ct.setToolTipText(toolTip);
			if (toolTip!=null) ct.setToolTip(new String(toolTip));
			if (toolType!=null) ct.toolType=toolType;
			ct.dropable=dropable;
			ct.addActionListener(actListener);
			if (tm.modeCursor!=null) ct.modeCursor=tm.modeCursor;
			if (tm.cursorIcon!=null) ct.cursorIcon=tm.cursorIcon.clone();
			ct.handy=tm.handy;
			if (tm.cmd2!=null) ct.cmd2=new String(tm.cmd2);
			if (tm.cmd3!=null) ct.cmd3=new String(tm.cmd3);
			if (tm.shortTip!=null) ct.shortTip=new String(tm.shortTip);
			if (tm.hotPoint!=null) ct.hotPoint=new Point(tm.hotPoint);
			
			ct.setActionCommand((String)ct.getKey());
			ct.setPreferredSize(ct.cpIcon.getDimension());
			ct.setRolloverEnabled(true);
			ct.setBorderPainted(false);
			ct.setBackground(Color.white);
			ct.setOpaque(false);
			ct.setButtonIcon();
			return ct;
		}

		MyTool ct= new MyTool();
		// load stuff in it (but don't add to hashtools)
		if (cpIcon!=null) ct.cpIcon=cpIcon.clone();
		// deBugging.PrintIcon.printImageIcon(cpIcon.imageIcon,"cpIcon");
		// deBugging.PrintIcon.printImageIcon(ct.cpIcon.imageIcon,"clone");
		if (cmdString!=null) ct.cmdString=new String(cmdString);
		if (nameString!=null) ct.nameString=new String(nameString);
		if (mnemonic!=null) ct.mnemonic=new String(mnemonic);
		if (toolTip==null || toolTip.length()==0)
			ct.setToolTipText(cmdString);
		else ct.setToolTipText(toolTip);
		if (toolType!=null) ct.toolType=toolType;
		ct.dropable=dropable;
		ct.addActionListener(actListener);

		ct.setActionCommand((String)ct.getKey());
		ct.setPreferredSize(ct.cpIcon.getDimension());
		ct.setRolloverEnabled(true);
		ct.setBorderPainted(false);
		ct.setBackground(Color.white);
		ct.setOpaque(false);
		ct.setButtonIcon();
		if (dropable) {
			DragSource dragSource=DragSource.getDefaultDragSource();
			dragSource.createDefaultDragGestureRecognizer(ct,DnDConstants.ACTION_LINK,
				new DragGestureListener() {
				public void dragGestureRecognized(DragGestureEvent event) {
					Transferable transferable=new ToolTransferable((MyTool)event.getComponent());
					event.startDrag(null,transferable,new ToolDragSourceListener());
				}
			});
		}
		
		return ct;
	}

}
