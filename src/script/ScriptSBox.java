package script;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Point;
import java.awt.dnd.DropTarget;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import circlePack.PackControl;
import dragdrop.EditDropListener;

/**
 * StackBox for the persistent CPscript node, i.e., the 
 * first node (with the script title) containing the text
 * and command nodes. 
 * @author kens
 *
 */
public class ScriptSBox extends StackBox implements ItemListener {

	private static final long 
	serialVersionUID = 1L;
		
	int lwidth=0;  // leftPanel width
	int lheight=36; // height
	JButton ocButton; // open/closed indicator
	int ocwidth=20; // open/closed button width
	int ocheight=20; // height
	public JEditorPane descriptField;
	JEditorPane titleField;
	public JTextField tagField; // tag image filename (optional)
	
	JCheckBox levelBox;
	JCheckBox mapModeBox;
	boolean myLevel;
	boolean myMapMode;
	JPanel accCanPanel;
	
	JPanel mainPanel; // always appears at top of the scriptBox 
	JPanel upperPanel; // ocbutton, accCanPanel, title 
	JPanel lowerPanel; // edit mode, description, tag, etc.
	Border titleBorder;
	Border greenBorder;
	Border emptyB;
		
	// Constructor
	public ScriptSBox(CPTreeNode tnode,int mode) {
		super(tnode,mode);
		setBackground(Color.white);
		editModeMenu=cmdActionPopup(true);
		dispModeMenu=cmdActionPopup(false);
		buildComponents();
		buildSB();
	}
	
	/**
	 * 'leftPanel' is empty or has accept/cancel info
	 */
	public void buildComponents() {
		
		// borders
		Border outer=new EmptyBorder(2,2,2,2);
		Border inner=new LineBorder(Color.LIGHT_GRAY);
		dispBorder = BorderFactory.createCompoundBorder(outer, inner);
		editBorder=dispBorder;
		titleBorder=new EmptyBorder(0,2,4,2);
		greenBorder=BorderFactory.createCompoundBorder(new EmptyBorder(2,2,2,2),new LineBorder(Color.green));
		emptyB=new EmptyBorder(0,0,0,0);

		mainPanel=new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel,BoxLayout.PAGE_AXIS));
		
		upperPanel=new JPanel();
		upperPanel.setLayout(new BoxLayout(upperPanel,BoxLayout.LINE_AXIS));
		upperPanel.setMinimumSize(new Dimension(150,30));
		upperPanel.setAlignmentX(0);
		
		// accept/cancel/mode cluster
		accCanPanel = new JPanel(null);
		new DropTarget(accCanPanel,new EditDropListener(this));

		int w=acceptButton.getWidth();
		int h=acceptButton.getHeight();
		acceptButton.setBounds(0,2,w,h);
		cancelButton.setBounds(0,16,w,h);

		accCanPanel.add(acceptButton);
		accCanPanel.add(cancelButton);
			
		// level checkbox
		levelBox=new JCheckBox("");
		levelBox.addItemListener(this);
		myLevel=manager.scriptLevel;
		levelBox.setSelected(myLevel);
		levelBox.setToolTipText("Open GUI in 'advanced' mode");
		levelBox.setBounds(72,2,20,14);
		accCanPanel.add(levelBox);

		// map mode checkbox (want paired canvasses?)
		mapModeBox=new JCheckBox("");
		mapModeBox.addItemListener(this);
		myMapMode=manager.scriptMapMode;
		mapModeBox.setSelected(myMapMode);
		mapModeBox.setToolTipText("Open GUI in 2-window 'map' mode");
		mapModeBox.setBounds(72,16,20,14);
		accCanPanel.add(mapModeBox);

		lwidth=94;
		setFixedSizes(accCanPanel,lwidth,30);
		accCanPanel.setAlignmentY(JComponent.CENTER_ALIGNMENT);
		
		titleField = new JEditorPane("TrueType",tNode.displayString);
		titleField.setFont(new Font("Serif",Font.BOLD+Font.ITALIC,16+PackControl.fontIncrement));
		util.EmacsBindings.addEmacsBindings(titleField);
		new DropTarget(titleField,new EditDropListener(this));
		titleField.addMouseListener(this);
		titleField.setMinimumSize(new Dimension(150,25));
			
		// lower stuff, only in edit mode
		lowerPanel=new JPanel();
		lowerPanel.setLayout(new BoxLayout(lowerPanel,BoxLayout.PAGE_AXIS));
		lowerPanel.setAlignmentX(0);

		// 'About' label and descriptionField 
		JLabel tLabel=new JLabel("\"About\" description:");
		tLabel.setAlignmentX(0);
		
		// description editor: script node only created once, so these
		//    does not get 'description' when script is loaded
		String current_tip=manager.scriptDescription;
		descriptField=new JEditorPane("TrueType",current_tip);
		util.EmacsBindings.addEmacsBindings(descriptField);
		descriptField.setFocusTraversalKeysEnabled(false); // catch TABs
		new DropTarget(descriptField,new EditDropListener(this));
		descriptField.setBorder(new LineBorder(Color.black));
		descriptField.setMinimumSize(new Dimension(150,50));
		descriptField.addMouseListener(this);
		descriptField.setAlignmentX(0);
		
		// tag area
		JPanel tagStuff=new JPanel();
		tagStuff.setLayout(new BoxLayout(tagStuff,BoxLayout.LINE_AXIS));
		new DropTarget(tagStuff,new EditDropListener(this));
		tagStuff.setAlignmentX(0);
		
		JLabel nLabel=new JLabel("Tag image filename: ");
		setFixedSizes(nLabel,130,24);
		
		// script node created only once, so this doesn't get tag name on loading
		String current_tag=manager.scriptTagname; 
		tagField=new JTextField(current_tag);
		tagField.setToolTipText("Optional: your personal 'tag' image "+
				"file, 400x120 jpg in 'myCirclePack/'");
		new DropTarget(tagField,new EditDropListener(this));
		tagField.setMinimumSize(new Dimension(150,20));
		tagField.setMaximumSize(new Dimension(800,20));
		tagField.addMouseListener(this);

		tagStuff.add(nLabel);
		tagStuff.add(tagField);
		tagStuff.setMinimumSize(new Dimension(200,25));


		lowerPanel.add(tLabel);
		lowerPanel.add(descriptField);
		lowerPanel.add(tagStuff);
		lowerPanel.setMinimumSize(new Dimension(150,50));
	}
	
	/**
	 * repopulate 'mainPanel'
	 */
	public void buildSB() {
		this.setBorder(dispBorder);
		mainPanel.removeAll();
		upperPanel.removeAll();

//		new DropTarget(upperPanel,new EditDropListener(this));

		// always add open/close button to upperPanel
		if (isOpen) ocButton=closeButton;
		else ocButton=openButton;
		ocButton.setAlignmentY(0.5f);
		upperPanel.add(ocButton);
		titleField.setText(tNode.displayString);

		if (currentMode==DISPLAY) { // add titleField
			boxMenu=dispModeMenu;
			titleField.setEditable(false);
			titleField.setFont(new Font("Serif",Font.BOLD+Font.ITALIC,18+PackControl.fontIncrement));
			titleField.setForeground(Color.blue);
			titleField.setBorder(titleBorder);
			titleField.setAlignmentY(0);
			
			upperPanel.add(titleField);
			upperPanel.setBackground(Color.white);
			mainPanel.setBorder(emptyB);
			mainPanel.add(upperPanel);
		}
		else { // edit mode, add acc/can and titleField 
			// STACK
//			upperPanel.setBorder(new LineBorder(Color.cyan,2,false));
			upperPanel.setMaximumSize(new Dimension(2000,34));
			upperPanel.add(accCanPanel);
			upperPanel.setBackground(defaultBGColor);
			ocButton.setAlignmentY(1f);

			titleField.setEditable(true);
			titleField.setBorder(new LineBorder(Color.black));
			titleField.setFont(new Font("TrueType",Font.ITALIC,14+PackControl.fontIncrement));
			titleField.setForeground(Color.black);
			titleField.setAlignmentY(JComponent.CENTER_ALIGNMENT);
			
			// STACK: try these
			ocButton.setAlignmentY(.2f);
			accCanPanel.setAlignmentY(.1f);
			titleField.setAlignmentY(0);
			
			upperPanel.add(titleField);		
			mainPanel.add(upperPanel);
			mainPanel.add(lowerPanel);
			boxMenu=editModeMenu;
			mainPanel.setBorder(greenBorder);
		}
		
		this.add(mainPanel);
	}
	
	public void redisplaySB(int wide) { // rebuild headerPanel each time
		myWidth=wide;
		this.removeAll(); // remove all stackBox's
		buildSB();
		
		// if open, add children
		if (isOpen) {
			for (int i=0;i<tNode.getChildCount();i++) {
				CPTreeNode tn=tNode.getChild(i);
				this.add(tn.stackBox);
			}
		}
		revalidate();
	}

	/** 
	 * Since script node is only created once, script loader calls
	 * this to set 'scriptDescription' and 'scriptTagname' when a
	 * new script is loaded.
	 */
	public void updateLoad() {
		try {
			if (manager.scriptDescription!=null)
				descriptField.setText(manager.scriptDescription.trim());
			if (manager.scriptTagname!=null)
				tagField.setText(manager.scriptTagname.trim());
		} catch(Exception ex) {}
	}
	
	public void openSB() { 
		isOpen=true;
		redisplaySB(myWidth);
	}
	
	public void startEdit() {
		if (currentMode==EDIT) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		currentMode=EDIT;
		redisplaySB(myWidth);
		
		//AF>>>//
		// Unlock the viewport and get the stack box in view.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
				// setViewRect() doesn't seem to work well here (misses completely).
				// Scroll to top instead.
				PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0, 0));
			}
		});
		//<<<AF//
	}
	
	public void cancelEdit() {
		if (currentMode!=EDIT) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		currentMode=DISPLAY;
		redisplaySB(myWidth);
		
		//AF>>>//
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}
		
	public void acceptEdit() {
		if (currentMode!=EDIT) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		tNode.displayString=titleField.getText().replace('\n',' ').replace('\"','\'');
		currentMode=DISPLAY;
		if (!manager.hasChanged) {
			manager.hasChanged=true;
			PackControl.scriptHover.scriptTitle(manager.scriptName,true);
		}
		manager.scriptDescription=descriptField.getText().trim();
		  // replace('\n',' ').replace('\"','\'');
		if (manager.scriptDescription!=null &&
				manager.scriptDescription.trim().length()==0)
			manager.scriptDescription=null;
		manager.scriptTagname=tagField.getText().trim();
		ImageIcon iI=manager.getTagImage(manager.scriptTagname);
		if (iI!=null)
			manager.myScriptTag=iI;
		redisplaySB(myWidth);
		
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
	 * Delete all the children nodes.
	 */
	public void deleteNode() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		deleteChildNodes();
		redisplaySB(myWidth);
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
	
	/**
	 * Remove all the contents, leaving empty CPSCRIPT
	 */
	public void deleteChildNodes() {
		for (int i=0;i<tNode.getChildCount();i++) {
			CPTreeNode cpTN=(CPTreeNode)tNode.getChild(i);
			cpTN.stackBox.deleteNode();
		}	
	}
	
	/**
	 * Set checkbox for level: true=advanced, false=min
	 * @param bool
	 */
	public void setLevelCk(boolean bool) {
		levelBox.setSelected(bool);
	}
	
	/**
	 * Set checkbox for map mode: true=paired, false = single
	 * @param bool
	 */
	public void setMapCk(boolean bool) {
		mapModeBox.setSelected(bool);
	}
	
	/** 
	 * Listens to various checkbox and sets 'inline' status 
	 * */
    public void itemStateChanged(ItemEvent e) {
        Object source = e.getItemSelectable();
        if (source == levelBox) {
        	int tick=e.getStateChange();
        	if (tick==ItemEvent.DESELECTED && manager.scriptLevel) {
        		manager.scriptLevel=false;
        		tNode.setInline(false);
        	}
        	else if (tick==ItemEvent.SELECTED && !manager.scriptLevel) {
        		manager.scriptLevel=true;
        		tNode.setInline(true);
        	}
        	return;
        }
        if (source == mapModeBox) {
        	int tick=e.getStateChange();
        	// note: dropable has effect only when mytool is created
        	if (tick==ItemEvent.DESELECTED && manager.scriptMapMode) {
        		manager.scriptMapMode=false;
        	}
        	else if (tick==ItemEvent.SELECTED && !manager.scriptMapMode) {
        		manager.scriptMapMode=true;
        	}
        	return;
        }
        return;
    }
    
    /**
     * Create menus for 'ScriptBox's, either edit or display mode.
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

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/commandBelow.png"));
		mi=new JMenuItem("add command below",ii);
		mi.setActionCommand("add_below_command");
		mi.addActionListener(this);
		editpop.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/textBelow.png"));
		mi=new JMenuItem("add text below",ii);
		mi.setActionCommand("add_below_text");
		mi.addActionListener(this);
		editpop.add(mi);
		
		return editpop;
	}	
	
	public int getHeaderHeight() {
		try {
			return upperPanel.getHeight();
		} catch (Exception ex) {
			return 10;
		}
	}
}
