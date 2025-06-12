package script;

import images.CPIcon;
import images.IconComboBox;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.tree.MutableTreeNode;

import mytools.MyTool;
import mytools.MyToolEditor;
import allMains.CPBase;
import canvasses.ActiveWrapper;
import canvasses.CursorCtrl;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import dragdrop.EditDropListener;
import dragdrop.ToolDragSourceListener;
import dragdrop.ToolTransferable;

/**
 * StackBox for COMMAND nodes
 * @author kens
 *
 */
public class CmdSBox extends StackBox implements ItemListener, KeyListener {

	private static final long 
	serialVersionUID = 1L;

	MyTool holdTool;
	int upperheight;
	int lowerheight;

	Dimension dim;
	static Dimension compactDim=new Dimension(54,34); // trial/error for compact dimensions
	static Dimension compactDimNext=new Dimension(78,34); // with NEXT
	JTextField nameField;
	JTextField tipField;
	JCheckBox inlineBox;
	JCheckBox dropBox;
	JCheckBox cursorBox;
	JCheckBox handyBox;
	JCheckBox eolBox; // execute_on_load indicator
	JCheckBox tipBox; // ask for tooltip 
	boolean inline;
	boolean dropable;
	boolean isCursor;
	boolean isHandy;
	boolean nextFlag;
	MyToolEditor toolEditor; // for iconCombo box methods
	IconComboBox iconCombo;
	Popup compList;
	Timer compTimer;
	JLabel nameLabel; // command "name"
	JLabel eolLabel;
	JLabel tipLabel;
	JPanel tipEolPanel; // contains Tip and EOL checkboxes

	JPanel accCanPanel;
	JPanel tryPanel;
	JPanel tipPanel;
	JEditorPane cmdEditor;
	JPanel upperPanel;
	JPanel compactPanel;
	Component upperGlue; // spacing for upperPanel
	Border emptyBorder;
	Border compactBorder;  // for when in 'LineSBox'

	// Constructor
	public CmdSBox(CPTreeNode tnode,int mode) {
		super(tnode,mode);
		toolEditor=PackControl.scriptToolHandler.toolEditor;
		editModeMenu=cmdActionPopup(true);
		dispModeMenu=cmdActionPopup(false);
		addMouseListener(this);

		buildComponents();
		redisplaySB(myWidth);
	}

	public void buildComponents() {
		emptyBorder=new EmptyBorder(0,0,0,0);
		Border inner=new LineBorder(Color.green);
		dispBorder=marginBorder;
		editBorder=BorderFactory.createCompoundBorder(marginBorder,inner);
		compactBorder=new EmptyBorder(0,0,0,0);

		// Icon combo boxes, for tool and for cursor
		iconCombo=new IconComboBox();
		iconCombo.setIconList(toolEditor.theCPIcons);
		iconCombo.setToolTipText("Select a tool icon ");
		setFixedSizes(iconCombo,50,28);

		// set icons
		displayIcon=tNode.tTool.getCPIcon().getImageIcon();
		if (displayIcon==null) displayIcon=CPIcon.CPImageIcon("script/commandIcon.png");

		// upper panel persists; lower is needed only in EDIT/NEW modes
		upperPanel = new JPanel(null);
		upperPanel.setMinimumSize(new Dimension(150,37));
		upperPanel.setLayout(new BoxLayout(upperPanel,BoxLayout.LINE_AXIS));
		upperPanel.setBackground(Color.white);
		new DropTarget(upperPanel,new EditDropListener(this));
		upperPanel.setAlignmentY(0);		

		// prepare checkbox's
		inline=tNode.isInline();
		inlineBox=new JCheckBox("");
		inlineBox.setBackground(Color.white);
		inlineBox.addItemListener(this);
		isHandy=tNode.isHandy();
		handyBox=new JCheckBox("");
		handyBox.setBackground(Color.white);
		handyBox.addItemListener(this);
		isCursor=tNode.isCursor();
		cursorBox=new JCheckBox("");
		cursorBox.setBackground(Color.white);
		cursorBox.addItemListener(this);
		dropable=tNode.isDropable();
		dropBox=new JCheckBox("");
		dropBox.setBackground(Color.white);
		dropBox.addItemListener(this);
		eolBox=new JCheckBox("");
		eolBox.setBackground(Color.white);
		eolBox.addItemListener(this);
		tipBox=new JCheckBox("");
		tipBox.setBackground(Color.white);
		tipBox.addItemListener(this);
		if (tNode.tipStart) { // can be true only first time through
			tipBox.setSelected(true);
			tNode.tipStart=false;
		}

		compList=null;
		compTimer = new Timer(5000, new ActionListener() {
			public void actionPerformed(ActionEvent e) { 
				if(compList!=null) compList.hide();
			}
		});

		compTimer.setRepeats(false);

		// accept/cancel/checkbox cluster
		accCanPanel=new JPanel(null);
		accCanPanel.setBackground(Color.white);
		acceptButton.setBounds(0,2,
				acceptButton.getWidth(),acceptButton.getHeight());
		accCanPanel.add(acceptButton);
		cancelButton.setBounds(0,18,
				cancelButton.getWidth(),cancelButton.getHeight());
		accCanPanel.add(cancelButton);

		// "try" it button
		tryPanel=new JPanel(null);
		tryPanel.setBackground(Color.white);
		tryButton.setBounds(16,6,22,22);
		tryPanel.add(tryButton);
		tryPanel.setPreferredSize(tryPanel.getPreferredSize());

		// inline checkbox
		inline=tNode.isInline();
		inlineBox.setToolTipText("Executes inline? (with 'NEXT') ");
		inlineBox.setBounds(72,2,20,14);
		accCanPanel.add(inlineBox);

		// dropable checkbox
		dropable=tNode.isDropable();
		dropBox.setToolTipText("Allow for drag/drop? ");
		dropBox.setBounds(72,18,20,14);
		accCanPanel.add(dropBox);

		// cursor checkbox
		cursorBox.setToolTipText("Should this be an active cursor?");
		cursorBox.setBounds(88,2,20,14);
		accCanPanel.add(cursorBox);

		// handy checkbox (can only be true if cursor is true)
		isHandy=tNode.isHandy();
		handyBox.setToolTipText("With cursor, should mouse drag image?");
		handyBox.setBounds(88,18,20,14);
		accCanPanel.add(handyBox);

		accCanPanel.add(tryPanel);
		setFixedSizes(accCanPanel,110,32); // a little longer to give space

		// command name stuff
		nameLabel=new JLabel("  name? ");
		nameField=new JTextField(myTool.getName());
		nameField.setToolTipText("Command name? ");
		setFixedSizes(nameField,50,24);

		// cmd editor 
		cmdEditor=new JEditorPane("TrueType","");
		cmdEditor.setMinimumSize(new Dimension(150,40));
		util.EmacsBindings.addEmacsBindings(cmdEditor);
		cmdEditor.setFont(new Font(cmdEditor.getFont().toString(),
				Font.ROMAN_BASELINE+Font.BOLD,11+PackControl.fontIncrement));
		cmdEditor.setFocusTraversalKeysEnabled(false); // catch TABs	
		new DropTarget(cmdEditor,new EditDropListener(this));
		cmdEditor.addKeyListener(this);
		cmdEditor.addMouseListener(this);  // not sure what this is for
		cmdEditor.setBorder(new EtchedBorder());
		String ctns=tNode.tTool.getCommand();
		if (ctns==null) ctns=" "; // default to ' '
		cmdEditor.setText(ctns);
		cmdEditor.setAlignmentX(0);

		// tooltip checkbox: open/close textarea on/off
		tipEolPanel=new JPanel(null);
		tipEolPanel.setBackground(Color.white);
		tipLabel=new JLabel("Tip?");
		tipLabel.setBounds(10,2,40,15); //340,2,55,15);
		tipLabel.setToolTipText("Open text editor for tool tip");
		tipEolPanel.add(tipLabel);
		tipBox.setToolTipText("Open text editor for tool tip");
		tipBox.setBounds(10,18,20,14);//340,18,20,14);
		tipEolPanel.add(tipBox);

		// EOL checkbox (execute_on_load): turn on/off depending
		eolLabel=new JLabel("EOL?");
		eolLabel.setBounds(50,2,40,15);//378,2,40,15);
		eolLabel.setToolTipText("Auto execute this command when script is loaded");
		tipEolPanel.add(eolLabel);
		eolBox.setToolTipText("Auto execute this command when script is loaded");
		eolBox.setBounds(50,18,20,14);//383,18,20,14);
		tipEolPanel.add(eolBox);
		setFixedSizes(tipEolPanel,90,34);

		tipPanel=new JPanel();
		tipPanel.setLayout(new BoxLayout(tipPanel,BoxLayout.LINE_AXIS));
		tipPanel.setAlignmentX(0);
		JLabel tLabel=new JLabel("  ToolTip:  ");
		tipField=new JTextField();
		tipField.setBorder(new LineBorder(Color.black));
		tipField.setEditable(true);
		util.EmacsBindings.addEmacsBindings(tipField);
		tipField.setFocusTraversalKeysEnabled(false); // catch TABs

		tLabel.setAlignmentY(0);
		tipPanel.add(tLabel);
		tipField.setAlignmentY(0);
		tipPanel.add(tipField);

		tipPanel.setMinimumSize(new Dimension(150,22));

		compactPanel=new JPanel(null);
		compactPanel.setBackground(Color.white);

		upperGlue=Box.createHorizontalGlue(); // at right end of upperPanel
	}

	/**
	 * Build compact structure. This may be panel within 'this'
	 * or may be 'this' itself when in a 'LineSBox'.
	 */
	public void buildCompact() {
		compactPanel.removeAll();
		int leftspot=4;
		if (nextFlag) {
			nextButton.setBounds(leftspot,8,24,18);
			nextButton.setVisible(nextFlag);
			compactPanel.add(nextButton);
			leftspot+=26; // leave some space to right
		}
		myTool.setBounds(leftspot,5,24,24);
		compactPanel.add(myTool);
		myTool.addMouseListener(this); 
		leftspot+=28;

		// two checkboxes
		JPanel cbpanel=new JPanel(null);
		cbpanel.setBackground(Color.white);

		// open/close button
		JButton button=openButton;
		button.setToolTipText("show the command text ");
		if (isOpen) {
			button=closeButton;
			button.setToolTipText("hide the command text ");
		}
		button.setBounds(0,0,12,12); //16,16);
		button.setVisible(true);
		cbpanel.add(button);

		// inline indication
		if (tNode.isInline()) {
			checkButton.setBounds(0,14,12,12); //12,12);
			cbpanel.add(checkButton);
		}
		cbpanel.setBounds(leftspot,4,12,26); // a little padding on right?
		if (tNode.tntype==CPTreeNode.MODE)
			inlineBox.setVisible(false);
		else 
			inlineBox.setVisible(true);
		compactPanel.add(cbpanel);

		Dimension dmn=compactDim;
		if (nextFlag) {
			dmn=compactDimNext;
			compactPanel.setBorder(new LineBorder(Color.green,2,false));
		}
		else 
			compactPanel.setBorder(emptyBorder);

		setFixedSizes(compactPanel,dmn.width,dmn.height); 
	}

	public void redisplaySB(int wide) {
		myWidth=wide;
		this.removeAll();

		// set 'next' status
		nextFlag=false;
		if (manager.nextCmdNode==tNode && tNode.isInline()) {
			nextFlag=true;
		}
		nextButton.setVisible(nextFlag);

		buildSB();

		if (currentMode==DISPLAY) {
			CPTreeNode parTN=(CPTreeNode)this.tNode.getParent();
			if (parTN!=null && parTN.tntype==CPTreeNode.LINEUP && !isOpen())
				setBorder(compactBorder);
			else
				this.setBorder(dispBorder);
		}
		else 
			this.setBorder(editBorder);
		upperPanel.setAlignmentX(0);
		this.add(upperPanel);
		if (currentMode!=DISPLAY) {
			this.add(cmdEditor);
			if (currentMode!=DISPLAY) 
				this.add(tipPanel);
		}
		
		revalidate();
	}

	private void buildSB() {
		buildUpperPanel();
		buildLowerPanel();
		buildTipPanel();
		if (currentMode==DISPLAY)
			boxMenu=dispModeMenu;
		else 
			boxMenu=editModeMenu;
	}

	public void buildUpperPanel() {
		upperPanel.removeAll();
		upperPanel.revalidate();

		if (currentMode==DISPLAY) {
			buildCompact();
			upperPanel.add(compactPanel);
			if (isOpen) {
				cmdEditor.setEditable(false);
				cmdEditor.setBackground(new Color(220,220,220));//Color.LIGHT_GRAY);
				upperPanel.add(cmdEditor);
			}
			upperheight=37;
			upperPanel.setBorder(emptyBorder);
		}
		else { // edit mode. horizontal layout
			upperPanel.add(accCanPanel);
			
			upperPanel.add(tryPanel);

			// nextButton; visibility is turned on/off
			upperPanel.add(nextButton);

			// name stuff
			upperPanel.add(nameLabel);
			upperPanel.add(nameField);

			// icon choice
			upperPanel.add(iconCombo);
			upperPanel.add(tipEolPanel);

			upperPanel.add(deleteButton);

			// spacer glue
			upperPanel.add(upperGlue);

			// set selections
			int indx=toolEditor.getCPIconIndx(tNode.tTool.getCPIcon());
			if (indx<0)	{
				indx=toolEditor.randomCPIcon();
				iconCombo.iconBox.setSelectedIndex(indx);
			}
			iconCombo.iconBox.setSelectedIndex(indx);
			inlineBox.setSelected(inline);
			dropBox.setSelected(dropable);
			cursorBox.setSelected(tNode.isCursor);
			handyBox.setSelected(isHandy);
			if (myTool.getName().equals("*"))
				eolBox.setSelected(true);
			else eolBox.setSelected(false);

			// wrap up
			//			upperPanel.setBorder(new EtchedBorder());
			upperheight=37;
		}
	}

	/**
	 * Lower panel has the command editor
	 */
	public void buildLowerPanel() {
		switch (currentMode) {
		case DISPLAY: {
			//			if (isOpen) {
			//				cmdEditor.setEditable(false);
			//				cmdEditor.setBackground(new Color(200,200,200));//Color.LIGHT_GRAY);
			//				lowerheight=20;
			//			}
			break;
		}
		case NEW: {} // fall through
		case EDIT: {
			cmdEditor.setEditable(true);
			cmdEditor.setBackground(Color.white);
			break;
		}
		} // end of switch
	}

	public void buildTipPanel() {
		switch (currentMode) {
		case DISPLAY: {
			return;
		}
		case NEW: {} // fall through
		case EDIT: {
			if (tipBox.isSelected()) 
				tipPanel.setVisible(true);
			else
				tipPanel.setVisible(false);
			break;
		}
		} // end of switch
	}

	/**
	 * Return (after some processing) the command string in
	 * 'cmdEditor'. 
	 * @return
	 */
	public String getCmdText() {
		return (detailCmdStr(cmdEditor.getText()));
	}

	public void startEdit() {
		if (currentMode!=DISPLAY) return;

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		// if MODE, remove menuItem from 'scriptModes'.
		if (tNode.tntype==CPTreeNode.MODE) {
			CursorCtrl.scriptModes.remove((MyCanvasMode)this.myTool);
			// remove as cursor in any 'ActiveWrapper's.
			if (PackControl.activeFrame.activeScreen.activeMode==tNode.tTool)
				PackControl.activeFrame.mainToolHandler.
				setCanvasMode(ActiveWrapper.defaultMode);
			if (PackControl.mapPairFrame.domainScreen.activeMode==tNode.tTool)
				PackControl.mapPairFrame.domainScreen.getToolHandler().
				setCanvasMode(ActiveWrapper.defaultMode);
			if (PackControl.mapPairFrame.rangeScreen.activeMode==tNode.tTool)
				PackControl.mapPairFrame.rangeScreen.getToolHandler().
				setCanvasMode(ActiveWrapper.defaultMode);
		}
		tNode.tntype=CPTreeNode.EDIT_CMDorMODE;
		currentMode=EDIT;
		holdTool=myTool;

		// set iconCombo to current icon (if in theCPIcons)
		int index=toolEditor.getCPIconIndx(myTool.getCPIcon());
		if (index>=0) iconCombo.iconBox.setSelectedIndex(index);

		// remove tool from toolbar and hashtable for now.
		manager.repopulateBar();
		CPBase.hashedTools.remove(myTool.getKey());
		//		redisplaySB(myWidth);

		if (tNode.tntype==CPTreeNode.MODE) {
			dropBox.setVisible(false);
			inlineBox.setVisible(false);
			eolBox.setVisible(false);
		}
		else {
			dropBox.setVisible(true);
			inlineBox.setVisible(true);
			if (tNode.tTool.nameString.equals("*")) {
				nameLabel.setVisible(false);
				nameField.setVisible(false);
				eolLabel.setVisible(true);
				eolBox.setVisible(true);
				eolBox.setSelected(true);
			}
		}

		tipField.setText(tNode.tTool.getToolTip());

		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		if (parTN.tntype==CPTreeNode.LINEUP) 
			parTN.stackBox.open(); // destroy this LINEUP node
		manager.repopulateRecurse((CPTreeNode)tNode.getParent());

		//AF>>>//
		// Redisplay the stack box (implies revalidate()).
		redisplaySB(myWidth);
		// Unlock the viewport and get the stack box in view.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
				setViewRect();
			}
		});
		//<<<AF//
	}

	public void cancelEdit() {
		if (currentMode==DISPLAY) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		if (currentMode==NEW) {
			deleteNode(); // only place where NEW & EDIT diverge?
		}
		else {
			currentMode=DISPLAY;
			tNode.tTool=myTool=holdTool;
			if (manager.cmdOpenMode)
				isOpen=true;

			// return to hashtable, toolbar if named; repopulate
			CPBase.hashedTools.put(myTool.getKey(),myTool);
			if (!holdTool.isCursorSet()) 
				tNode.tntype=CPTreeNode.COMMAND;
			else {
				tNode.tntype=CPTreeNode.MODE;
				CursorCtrl.scriptModes.add((MyCanvasMode)myTool);
			}
			manager.repopulateRecurse((CPTreeNode)tNode.getParent());
			if (tNode.isNamed() || tNode.isXY()) manager.repopulateBar();
		}
		
		//AF>>>//
		// Redisplay the stack box (implies revalidate()).
		redisplaySB(myWidth);
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}

	public void acceptEdit() {
		if (currentMode==DISPLAY) return;

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		CPIcon cpIcon;

		// remove old tool
		if (currentMode==EDIT) CPBase.hashedTools.remove(holdTool.getKey());

		// fix tool data
		String cmd=getCmdText();
		String name=nameField.getText().trim().replace('"','\'');
		String tip=tipField.getText().trim().replace('\n',' ').replace('"','\'');
		if (tip!=null && tip.length()==0) tip=null;
		if (name.length()==0) name=null;

		// choose tool icon from iconCombo
		cpIcon=(CPIcon)iconCombo.iconBox.getSelectedItem();

		// if this is a regular tool
		if (!isCursor) {
			// set mnemonic
			String mnem_text=null;
			char c;
			if (name!=null && (((c=name.charAt(0))>='A' && c<='z') 
					|| (c>='0' && c<='9') || c=='*')) { 
				mnem_text=new String(String.valueOf(c));
			}
			myTool=new MyTool(cpIcon,cmd,name,mnem_text,tip,"SCRIPT:",
					dropable,PackControl.scriptToolHandler.toolListener);
			// 	deBugging.PrintIcon.printImageIcon(myTool.cpIcon.imageIcon,new String("tool"+name));
			tNode.tTool=myTool;
			tNode.tntype=CPTreeNode.COMMAND; 
			tNode.setInline(inline);

			// if there's no 'next', reset
			if (inline && manager.nextCmdNode==null) manager.resetNextCmdNode();
		}
		else { // is (or wants to be) cursor
			CursorCtrl.canvasModes.remove(holdTool);
			CursorCtrl.scriptModes.remove(holdTool);
			CursorCtrl.userModes.remove(holdTool);
			MyCanvasMode myMode=new MyCanvasMode(name,cpIcon,null,
					cmd,null,null,name,new String("Cursor: "+cmd),
					"SCRIPT:",isHandy);

			// reestablish info if this was already a cursor;
			//    'menuItem' is updated when repopulating toolbar
			if (holdTool instanceof MyCanvasMode) {
				MyCanvasMode holdMode=(MyCanvasMode)holdTool;
				myMode.hotPoint=holdMode.hotPoint;
				myMode.setCmd2(holdMode.cmd2);
				myMode.setCmd3(holdMode.cmd3);
				if (holdMode.shortTip!=null)
					myMode.setShortTip(holdMode.shortTip);
			}

			// re-embellish tool cpIcon (with mouse hot point, possibly key)
			myMode.cpIcon.embellishMe(name,false,true,false,false);
			myMode.setButtonIcon();

			// embellish cursor icon, update menuItem, add to Modes vector
			if (myMode.cursorIcon!=null)
				myMode.setCursor(myMode.hotPoint);
			myMode.updateMenuItem();

			CursorCtrl.scriptModes.add(myMode);

			myTool=(MyTool)myMode;
			tNode.tTool=myTool;
			tNode.isCursor=true;
			tNode.isHandy=isHandy; // reflects checkbox setting
			tNode.tntype=CPTreeNode.MODE;
			tNode.setInline(false);
		}

		// get/set toolTip
		String current_tip=null;
		try {
			current_tip=tipField.getText().trim();
			if (current_tip.length()==0)
				current_tip=null;
		} catch (Exception ex) {
			current_tip=null;
		}
		myTool.setToolTip(current_tip);

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

		// out of edit mode
		currentMode=DISPLAY;
		if (manager.cmdOpenMode)
			isOpen=true;
		else 
			isOpen=false;

		// add to hash table and perhaps to bar
		CPBase.hashedTools.put(myTool.getKey(),myTool);

		if (!manager.hasChanged) {
			manager.hasChanged=true;
			PackControl.scriptHover.scriptTitle(manager.scriptName,true);
		}
		manager.repopulateRecurse((CPTreeNode)tNode.getParent());
		if (name!=null || tNode.isXY()) 
			manager.repopulateBar(); 
		// 		deBugging.PrintIcon.printImageIcon(PackControl.scriptToolHandler.toolVector.get(0).cpIcon.imageIcon,"other1before")

		//AF>>>//
		// Redisplay the stack box (implies revalidate()).
		redisplaySB(myWidth);
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}

	public void open() {
		if (isOpen) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		if (parTN.tntype==CPTreeNode.LINEUP)
			parTN.stackBox.open();
		isOpen=true;
		manager.repopulateRecurse((CPTreeNode)tNode.getParent());
		
		//AF>>>//
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}

	public void close() {
		if (!isOpen) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		isOpen=false;
		manager.repopulateRecurse((CPTreeNode)tNode.getParent());
		
		//AF>>>//
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}

	/**
	 * Delete this node: get rid of CPTreeNode and StackBox.
	 */
	public void deleteNode() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		if (manager.nextCmdNode!=null && manager.nextCmdNode==tNode) { // this is 'NEXT' command
			manager.resetNextCmdNode(tNode);
		}
		// remove tool
		if (currentMode==DISPLAY) CPBase.hashedTools.remove(myTool.getKey());
		else if (currentMode==EDIT) CPBase.hashedTools.remove(holdTool.getKey());

		// also delete this CPTreeNode from the tree (ie. destroy it)
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		if (parTN.tntype==CPTreeNode.LINEUP)
			parTN.stackBox.open();
		parTN=(CPTreeNode)tNode.getParent(); // new parent
		try{
			parTN.remove((MutableTreeNode)tNode);
		} catch(NullPointerException npe){}
		manager.repopulateRecurse(parTN);
		if (!manager.hasChanged) {
			manager.hasChanged=true;
			PackControl.scriptHover.scriptTitle(manager.scriptName,true);
		}
		manager.repopulateBar();
		
		//AF>>>//
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}

	public void deleteChildNodes() {}

	/** 
	 * Listens to various checkbox and sets 'inline' status 
	 * */
	public void itemStateChanged(ItemEvent e) {
		Object source = e.getItemSelectable();
		if (source == inlineBox) {
			int tick=e.getStateChange();
			if (tick==ItemEvent.DESELECTED && inline) {
				inline=false;
				tNode.setInline(false);
				// if 'next', reposition
				if (manager.nextCmdNode==tNode) {
					manager.resetNextCmdNode(manager.nextCmdNode);
					redisplaySB(myWidth);
				}
			}
			else if (tick==ItemEvent.SELECTED && !inline) {
				inline=true;
				tNode.setInline(true);
				if (manager.nextCmdNode==null) {
					manager.resetNextCmdNode();
					redisplaySB(myWidth);
				}
			}
			return;
		}
		if (source == dropBox) {
			int tick=e.getStateChange();
			// note: dropable has effect only when mytool is created
			if (tick==ItemEvent.DESELECTED && dropable) {
				dropable=false;
			}
			else if (tick==ItemEvent.SELECTED && !dropable) {
				dropable=true;
			}
			return;
		}
		if (source == cursorBox) {
			int tick=e.getStateChange();
			if (tick==ItemEvent.DESELECTED && isCursor) {
				isCursor=false;
				isHandy=false; // have to be a cursor to be 'handy'
				handyBox.setSelected(false);
				dropBox.setVisible(true);
				inlineBox.setVisible(true);
				eolLabel.setVisible(true);
				eolBox.setVisible(true);
				eolBox.setSelected(false);
			}
			else if (tick==ItemEvent.SELECTED && !isCursor) {
				isCursor=true;
				isHandy=true; // default: deselect in 'handyBox'
				handyBox.setSelected(true);
				dropBox.setVisible(false);
				inlineBox.setVisible(false);
				dropBox.setSelected(false);
				dropable=false;
				eolLabel.setVisible(false);
				eolBox.setVisible(false);
				eolBox.setSelected(false);
			}
			return;
		}
		if (source == handyBox) {
			int tick=e.getStateChange();
			if (tick==ItemEvent.DESELECTED && isHandy) {
				isHandy=false; 
			}
			else if (tick==ItemEvent.SELECTED && !isHandy && isCursor) {
				isHandy=true;
			}
			return;
		}
		if (source == tipBox) {
			int tick=e.getStateChange();
			if (tick==ItemEvent.DESELECTED) {
				tipPanel.setVisible(false);
			}
			else if (tick==ItemEvent.SELECTED) {
				tipPanel.setVisible(true);
			}
			revalidate();
			return;
		}
		if (source==eolBox) {
			int tick=e.getStateChange();
			// eolBox should only show if there is no other eol command
			//    TODO: what fi two are being edited at same time?
			if (tick==ItemEvent.DESELECTED) {
				nameLabel.setVisible(true);
				nameField.setVisible(true);
				nameField.setText("");
				cursorBox.setVisible(true);
				handyBox.setVisible(true);
			}
			else if (tick==ItemEvent.SELECTED) {
				nameLabel.setVisible(false);
				nameField.setVisible(false);
				nameField.setText("*");
				cursorBox.setVisible(false);
				handyBox.setVisible(false);
			}
			return;
		}
		return;
	}

	/**
	 * Create menus for 'CmdBox's, either edit or display mode.
	 * @param editmode, boolean: true, then for edit mode
	 * @return JPopupMenu
	 */
	public JPopupMenu cmdActionPopup(boolean editmode) {
		JPopupMenu editpop=new JPopupMenu();

		ImageIcon ii;
		JMenuItem mi;
		if (editmode) {
			ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/accept_label.png"));
			mi=new JMenuItem("accept edit",ii);
			mi.setActionCommand("accept_edit");
			mi.addActionListener(this);
			editpop.add(mi);

			ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/cancel_label.png"));
			mi=new JMenuItem("cancel edit",ii);
			mi.setActionCommand("cancel_edit");
			mi.addActionListener(this);
			editpop.add(mi);
		}
		else {
			ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/e_edit.png"));
			mi=new JMenuItem("edit",ii);
			mi.setActionCommand("edit");
			mi.addActionListener(this);
			editpop.add(mi);
		}

		editpop.add(buildAddMenu());

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/kill_16x16.png"));
		mi=new JMenuItem("delete this node",ii);
		mi.setActionCommand("delete_node");
		mi.addActionListener(this);
		editpop.add(mi);

		return editpop;
	}

	public void keyReleased(KeyEvent evt) {
		String cmd =getCmdText();

		// hide completion list if a key is hit
		if (compList != null)
			compList.hide();

		if (currentMode==EDIT || currentMode==NEW) switch (evt.getKeyCode()) {	
		// do command completion with TAB
		case KeyEvent.VK_TAB: {
			String hold="";
			String tailCmd=cmd.trim();
			int k=cmd.lastIndexOf(';');
			if (k>=0) {
				hold=cmd.substring(0,k+1);
				tailCmd=cmd.substring(k+1).trim();
			}
			String resp[] = PackControl.consoleCmd.complete(tailCmd);
			cmdEditor.setText(hold+resp[0]); // completed command

			// creates a tooltip and places it directly below the cmdline box
			JToolTip tip = cmdEditor.createToolTip();
			tip.setTipText("<html>" + resp[1] + "</html>");
			Rectangle rect = cmdEditor.getBounds();
			Point loc = cmdEditor.getLocationOnScreen();
			compList = PopupFactory.getSharedInstance().getPopup(cmdEditor, tip,
					loc.x, loc.y + rect.height - 2);
			compList.show();

			// hide tip after 5 secs
			compTimer.start();

			break;
		}
		}
	}

	// ignore these events (req'd for KeyListener)
	public void keyPressed(KeyEvent evt) {}
	public void keyTyped(KeyEvent evt) {}

	/**
	 * prepare command strings: trim, get rid of line breaks and '<', '>', etc.
	 * Currently allow '"' double quotes.
	 * @param rawstr
	 * @return
	 */
	public String detailCmdStr(String rawstr) {
		String cmd=rawstr.trim().replace('\n',' '); // .replace('\"','\'');
		cmd=cmd.replaceAll("<=",".le.");
		cmd=cmd.replaceAll("<",".lt.");
		cmd=cmd.replaceAll(">=",".ge.");
		cmd=cmd.replaceAll(">",".gt.");
		cmd=cmd.replaceAll("&&",".and.");
		cmd=cmd.replaceAll("&",".and.");
		cmd=cmd.replaceAll("!=",".ne.");
		cmd=cmd.replaceAll("!!","xbx"); // placeholder
		cmd=cmd.replaceAll("!",".not.");
		cmd=cmd.replaceAll("xbx","!!");
		return cmd;
	}

}
