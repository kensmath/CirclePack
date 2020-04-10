package script;

import images.CPIcon;
import input.CPFileManager;
import input.FileDialogs.DataFileFilter;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.MutableTreeNode;

import mytools.MyTool;
import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;

/**
 * Our script display will be a ScrollBox containing a stack of StackBox's; 
 * certain StackBox can contain more StackBox's. StackBox's are panels, with
 * layout depending on type and edit/display status. 
 * Subclasses are defined for CPscript, CPdata, Section, cmd, file, and text 
 * (often implicit) nodes; leaf nodes are cmd, file, and text and don't have
 * children. Currently (8/10) am trying to incorporate 'LineSBox's, which
 * would simply consolidate strings of successive iconified 'CmdSBox's; this
 * is not yet completed.
 * 
 * If mode is NEW, an appropriate CPTreeNode must be created as first thing.
 * 
 * @author kens
 *
 */
public abstract class StackBox extends JPanel implements ActionListener,
MouseListener {

	private static final long 
	serialVersionUID = 1L;
	
	// modes:
	static final int DISPLAY=1;
	static final int EDIT=2;
	static final int NEW=3; // only diverges from EDIT in 'cancelEdit' call.
	LongLabel acceptButton;
	LongLabel cancelButton;
	LongLabel deleteButton;
	JButton checkButton;
	JButton dropButton;
	JButton tryButton;
	JButton openButton;
	JButton closeButton;
	JButton nextButton;
	CPTreeNode tNode;
	int depth; // nesting depth (e.g., determines spacing)
	int currentMode;
	MyTool myTool;
	ScriptManager manager; // local pointer
	Border marginBorder; // provides the l/r insets
	Border editBorder;
	Border dispBorder;

	ImageIcon displayIcon;
	int myWidth;  // width of component (formerly held by 'CPTreeNode');

	boolean isOpen; // for elements with children or command elements: open for display?
	
	// Note: CPSCRIPT, SECTION, CPDATA, have vertical box layout,
	//   they have 'headerPanel' for horizontal layout associate
	//   with title/editing stuff.
	
	// menu's that will appear on r-mouse.
	JPopupMenu boxMenu;
	JPopupMenu editModeMenu;
	JPopupMenu dispModeMenu;
	public static Color defaultBGColor;
	
	// Constructor
	public StackBox(CPTreeNode tnode,int mode) {
		super();
    	myWidth=PackControl.scriptManager.WIDTH-2;
		defaultBGColor=getBackground();
		this.setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));

		manager=PackControl.scriptManager;
		marginBorder=new EmptyBorder(1,manager.WIDTH_INC,1,manager.WIDTH_INC);
		setBackground(Color.white);
//		addMouseListener(this);
 		myTool=null;
 		currentMode=mode;
 		if (currentMode<1 || currentMode>3) currentMode=3; // default to new
 		try {
 			tNode=tnode;
 			myTool=tNode.tTool;
 		} catch(Exception ex) {
 			System.err.println("error: all stackBox's must have CPTreeNode's");
 		}
		myWidth=PackControl.scriptManager.WIDTH;
		
		// default 'isOpen' settings:
		isOpen=true;
		int type=tNode.tntype;
		if (type == CPTreeNode.COMMAND || type==CPTreeNode.CPDATA ||type==CPTreeNode.FILE) 
			isOpen=false;
	
		// for ROOT or EDIT modes (except SECTION), don't need these
//		if ((type>1 && type<11) || type==13) {
			closeButton=ModeButton(-1); // minus ("currently open")
			setFixedSizes(closeButton,16,16);
			openButton=ModeButton(1); // plus ("currently closed")
			setFixedSizes(openButton,16,16);
//		}
		
		// edit "accept/cancel" buttons
		acceptButton=new LongLabel(LongLabel.ACCEPT);
		acceptButton.addActionListener(this);
		acceptButton.setBorderPainted(false);
		acceptButton.setBackground(Color.white);
		acceptButton.setOpaque(false);
		
		cancelButton=new LongLabel(LongLabel.CANCEL);
		cancelButton.addActionListener(this);
		cancelButton.setBorderPainted(false);
		cancelButton.setBackground(Color.white);
		cancelButton.setOpaque(false);
		
		if (type==7 || type==9 || type==11) {
			// "Try" button
			Icon tryIcon=CPIcon.CPImageIcon("script/try_exec.png");
			tryButton=new JButton(tryIcon);
			tryButton.addActionListener(this);
			tryButton.setActionCommand("trial_exec");
			// STACK
			setFixedSizes(tryButton,22,22);
//			tryButton.setPreferredSize(new Dimension(22,22));
			tryButton.setBorderPainted(false);
			tryButton.setBackground(Color.white);
			//		tryButton.setOpaque(false);
			tryButton.setToolTipText("Try the edited command ");
		
			// "inline" checkbox
			Icon checkIcon=CPIcon.CPImageIcon("script/inline_chk.png");
			checkButton=new JButton(checkIcon);
			checkButton.setSize(new Dimension(12,12));
			checkButton.setBorderPainted(false);
			checkButton.setBackground(Color.white);
//			checkButton.setOpaque(false);
			checkButton.setToolTipText("this command executes inline ");
		
			// "dropable" checkbox
			Icon dropIcon=CPIcon.CPImageIcon("script/drop_chk.png");
			dropButton=new JButton(dropIcon);
			dropButton.setSize(new Dimension(12,12));
			dropButton.setBorderPainted(false);
			dropButton.setBackground(Color.white);
//			dropButton.setOpaque(false);
			dropButton.setToolTipText("this command can be drag/dropped ");

			Icon nextIcon=CPIcon.CPImageIcon("script/n_ptr.png");
			nextButton=new JButton(nextIcon);
			// STACK
			setFixedSizes(nextButton,24,22);
//			nextButton.setPreferredSize(new Dimension(20,20));
			nextButton.setBorderPainted(false);
			nextButton.setBackground(Color.WHITE);
//			nextButton.setOpaque(false);
			nextButton.setToolTipText("This is the 'NEXT' command");
//			nextButton.setAlignmentY(0);
			nextButton.setVisible(false);
		}
		
		// "delete" button
		if (type>3) {
			deleteButton=new LongLabel(LongLabel.DELETE);
			deleteButton.addActionListener(this);
			deleteButton.setBorderPainted(false);
			deleteButton.setBackground(Color.white);
			setFixedSizes(deleteButton,30,30);
//			deleteButton.setOpaque(false);
		}
	
		boxMenu=null;
		editModeMenu=null;
		dispModeMenu=null;
		setAlignmentX(0);
	}
	
	// subclasses must supply these methods
	public abstract void buildComponents(); // build the various panels, borders, etc.
	public abstract void redisplaySB(int wide); // redo layout and add child stackboxes (if any) 
	public abstract void startEdit(); // change into edit mode
	public abstract void cancelEdit(); // leave as is
	public abstract void acceptEdit(); // accept edit changes; set DISPLAY mode
	public abstract void deleteNode(); // destroy CPTreeNode, children, everything.
	public abstract void deleteChildNodes(); // if there are children
	
	/** 
	 * Removes a StackBox child from the JPanel of this StackBox
	 * @param SB
	 */
	public void removeChild(StackBox SB) {
		this.remove((StackBox)SB);
	}
	
	public void redisplayParentSB() {
		CPTreeNode par=(CPTreeNode)tNode.getParent();
		manager.repopulateRecurse(par);
	}
	
	public boolean isOpen() {
		return isOpen;
	}
	
	/**
	 * Open this StackBox for display purposes; may have
	 * ancestors to open as well.
	 */
	public void open() {
		if (isOpen) return;
		if (tNode.tntype==CPTreeNode.CPSCRIPT || tNode.tntype==CPTreeNode.CPDATA ||
				tNode.tntype==CPTreeNode.SECTION || tNode.tntype==CPTreeNode.EDIT_SECTION 
				|| tNode.tntype==CPTreeNode.COMMAND || tNode.tntype==CPTreeNode.MODE) {
			isOpen=true;
			redisplaySB(myWidth);
		}
	}
	
	/**
	 * Close this StackBox for display purposes; redisplay
	 * should shuck stackbox children, though they may remain open.
	 */
	public void close() {
		if (!isOpen) return;		
		if (tNode.tntype==CPTreeNode.CPSCRIPT || tNode.tntype==CPTreeNode.CPDATA ||
				tNode.tntype==CPTreeNode.SECTION || tNode.tntype==CPTreeNode.EDIT_SECTION 
				|| tNode.tntype==CPTreeNode.COMMAND || tNode.tntype==CPTreeNode.MODE) {
			isOpen=false;
			redisplaySB(myWidth);
		}
	}
	
	public JButton ModeButton(int type) {
		Dimension dim=new Dimension(20,20);
		JButton button=new JButton();
		button.addActionListener(this);
		if (type==1) { // + (so currently closed)
			Icon icon=CPIcon.CPImageIcon("script/small_plus.png");
			button.setIcon(icon);
			button.setBackground(Color.white);
			button.setOpaque(false);
			button.setBorderPainted(false);
			button.setToolTipText("Open this node");
			button.setActionCommand("open_node");
			button.setPreferredSize(dim);
			button.setMaximumSize(dim);
			button.setMinimumSize(dim);
			return button;
		}
		if (type==-1) { // - (so currently open)
			Icon icon=CPIcon.CPImageIcon("script/small_minus.png");
			button.setIcon(icon);
			button.setBackground(Color.white);
			button.setOpaque(false);
			button.setBorderPainted(false);
			button.setToolTipText("Close this node");
			button.setActionCommand("close_node");
			button.setPreferredSize(dim);
			button.setMaximumSize(dim);
			button.setMinimumSize(dim);
			return button;
		}
		return null;
	}
	
	/**
	 * In drag/drop operation, an action command is sent here to
	 * be forwarded to actionPerformed.
	 * @param actionCmd
	 */
	public void editAction(String command) {
		if (command.equals("edit")) {
			startEdit();
		}
		else if (command.startsWith("add_above") || command.startsWith("add_below")) { 
			if (this instanceof DataSBox || this instanceof FileSBox) { 
				// can only add a file below: give user chooser dialog
				manager.insertNewTN(tNode,CPTreeNode.FILE,1);
				return;
			}
			else if (this instanceof ScriptSBox && command.startsWith("add_above")) return;
			
			int abbel=1; // below (downstream)
			if (command.startsWith("add_above")) abbel=0; // upstream (i.e., insert at current spot)
			// type is: 0=COMMAND, 1=TEXT,2=SECTION
			int tNtype=CPTreeNode.COMMAND; 
			if (command.endsWith("text")) tNtype=CPTreeNode.TEXT;
			if (command.endsWith("section")) tNtype=CPTreeNode.SECTION;
			manager.insertNewTN(tNode,tNtype,abbel);
			return;
		}
		else if (command.equals("Next script cmd")) {
			if (tNode.tntype!=CPTreeNode.COMMAND &&
					tNode.tntype!=CPTreeNode.EDIT_CMDorMODE &&
					tNode.tntype!=CPTreeNode.SECTION &&
					tNode.tntype!=CPTreeNode.EDIT_SECTION &&
					tNode.tntype!=CPTreeNode.TEXT &&
					tNode.tntype!=CPTreeNode.EDIT_TEXT) 
				return;
			CPTreeNode pred=(CPTreeNode)tNode.getParent();
			int indx;
			if ((indx=pred.getIndex(tNode))<0) return;
			if (indx>0)
				pred=(CPTreeNode)pred.getChild(indx-1);
			
			// is there a previous next to redisplay?
			if (manager.nextCmdNode!=null) {
				StackBox oldsb=manager.nextCmdNode.stackBox;
				manager.nextCmdNode=null;
				if (oldsb instanceof CmdSBox) 
					oldsb.redisplaySB(oldsb.myWidth);
			}
			manager.resetNextCmdNode(pred);
		}
	}
	
	public JMenu buildAddMenu() {
		JMenu addMenu=new JMenu("add a node");
		
		ImageIcon ii = new ImageIcon(CPBase.getResourceURL("/Icons/script/commandAbove.png"));
		JMenuItem mi=new JMenuItem("add command above",ii);
		mi.setActionCommand("add_above_command");
		mi.addActionListener(this);
		addMenu.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/commandBelow.png"));
		mi=new JMenuItem("add command below",ii);
		mi.setActionCommand("add_below_command");
		mi.addActionListener(this);
		addMenu.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/textAbove.png"));
		mi=new JMenuItem("add text above",ii);
		mi.setActionCommand("add_above_text");
		mi.addActionListener(this);
		addMenu.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/textBelow.png"));
		mi=new JMenuItem("add text below",ii);
		mi.setActionCommand("add_below_text");
		mi.addActionListener(this);
		addMenu.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/sectionAbove.png"));
		mi=new JMenuItem("add section above",ii);
		mi.setActionCommand("add_above_section");
		mi.addActionListener(this);
		addMenu.add(mi);
		
		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/sectionBelow.png"));
		mi=new JMenuItem("add section below",ii);
		mi.setActionCommand("add_below_section");
		mi.addActionListener(this);
		addMenu.add(mi);
		
		return addMenu;
	}

	/**
	 * Attempt to 'setViewPosition' of 'stackScroll' to show the
	 * 'Rectangle' of this stackBox.
	 * 
	 */
	// STACK
	public void setViewRect() {
		Rectangle rect=getRect();
		Rectangle vRect=PackControl.scriptHover.stackScroll.getViewport().getViewRect();
		int y=rect.y;
		int height=rect.height;
		int Y=vRect.y;
		int H=vRect.height;
		if (y<vRect.y) // scroll up
			PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0,y));
		else if ((y+height)>(Y+H)) 
			PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0,y+height-H));
	}

	/**
	 * Calculate the position of the upper left corner of
	 * the stackBox; have to compute where it is up the ancestor
	 * tree of parents.
	 * @return
	 */
	public Rectangle getRect() {
		int y=this.getY();
		CPTreeNode parent=(CPTreeNode)tNode.getParent();
		while (parent!=null && parent!=manager.rootNode) {
			y+=parent.stackBox.getY();
			parent=(CPTreeNode)parent.getParent();
		}
		// STACK
//System.err.println("getRect call: y = "+y+" and width/height="+getWidth()+" "+getHeight());		
		return new Rectangle(0,y,getWidth(),getHeight());
	}
	
	/**
	 * Have to ask user whether to delete grouping only or all elements too.
	 * @return
	 */
	public int queryDeletion() {
		Object[] options={"'Section' header only?", "Section AND all contents?"};
		int result=JOptionPane.showOptionDialog(this,"Delete What? Just the "
				+"'Section' grouping or the grouping AND all its elements?",
				"Delete?",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,
				options,options[0]);
		if (result == JOptionPane.YES_OPTION) 
			return 1; // just section
		if (result==JOptionPane.NO_OPTION)
			return -1; // everything
		return 0; // must have closed (= cancelled)
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {
		if ((e.getButton() == MouseEvent.BUTTON3) && boxMenu!= null) {
			// have to identify which StackBox the button was pressed on
			boxMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}
	
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	
	/**
	 * Handle various open/close options, pass edit
	 * options to 'editAction'
	 */
	public void actionPerformed(ActionEvent e){
		String command = e.getActionCommand();

		if (command.equals("edit")) {
			startEdit();
		}
		else if (command.equals("cancel_edit")) 
			cancelEdit();
		else if (command.equals("export_file")) {
			if (this instanceof FileSBox) {
				File dataOutFile=null;
				FileSBox fsbox=(FileSBox)this;
				
				// run a file chooser
				JFileChooser dbox = new JFileChooser();
				dbox.setCurrentDirectory(CPFileManager.PackingDirectory);
				dbox.setSelectedFile(new File(CPFileManager.PackingDirectory+File.separator+fsbox.includedFile.origName));
				dbox.setFileFilter(new DataFileFilter());
				int result = dbox.showDialog(
						(Component) PackControl.activeFrame,"Export data file");
				if (result == JFileChooser.APPROVE_OPTION) {
					dataOutFile = dbox.getSelectedFile();
					CPFileManager.PackingDirectory = dbox.getCurrentDirectory(); // change directory
				}
				try {
					CPFileManager.copyFile(fsbox.includedFile.tmpFile,dataOutFile);
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("Failed in copying "+fsbox.includedFile.tmpFile.getName());
					return;
				}
				CirclePack.cpb.msg("Exported data file to "+dataOutFile.getPath());
			} 
			else return;
		}
		else if (command.equals("delete_node")) {
			if (this instanceof SectionSBox) {
				int result=queryDeletion();
				if (result==0) // cancel deletion
					return;
				
				//AF>>>//
				// Lock the viewport so GUI changes don't jitter and relocate.
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
				//<<<AF//
				
				CPTreeNode parTN=(CPTreeNode)tNode.getParent();
				if (result==-1) { // remove everything
					deleteChildNodes();
					if (parTN!=null) parTN.remove((MutableTreeNode)tNode);
				}
				else { // save children as siblings under parent
					if (parTN==null) {
						//AF>>>//
						// Unlock the viewport.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
							}
						});
						//<<<AF//
						return;
					}
					int index=parTN.getIndex(tNode);
					// Note: child vec depleted by one each step
					int ccount=tNode.getChildCount();
					for (int i=0;i<ccount;i++)
						parTN.insert((CPTreeNode)tNode.getChild(0),index+i+1);
					parTN.remove(index);
				}
				if (!manager.hasChanged) {
					manager.hasChanged=true;
					PackControl.scriptHover.scriptTitle(manager.scriptName,true);
				}
				manager.repopulateRecurse(parTN);
				
				//AF>>>//
				// Unlock the viewport.
				EventQueue.invokeLater(new Runnable() {
					public void run() {
						((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
					}
				});
				//<<<AF//
			}
			else deleteNode();
		}
		else if (command.equals("accept_edit")) 
			acceptEdit();
		else if (command.equals("open_node")) 
			open();
		else if (command.equals("close_node")) 
			close();

		// for CmdSBox's, this executes the command as currently edited.
		else if (command.equals("trial_exec") && currentMode!=DISPLAY) {
			CmdSBox csb=(CmdSBox)(this.tNode.stackBox);
			String str=csb.getCmdText();
			CPBase.trafficCenter.parseWrapper(str,CirclePack.cpb.getActivePackData(),false,true,0,null);
		}
		else editAction(command);
	}
	
	/**
	 * Set Min=Max=Pref Sizes all to same value. Normally used for component
	 * that shouldn't vary.
	 * @param jc
	 * @param wide
	 * @param high
	 */
	public static void setFixedSizes(JComponent jc,int wide,int high) {
		jc.setMinimumSize(new Dimension(wide,high));
		jc.setMaximumSize(new Dimension(wide,high));
		jc.setPreferredSize(new Dimension(wide,high));
	}
}
