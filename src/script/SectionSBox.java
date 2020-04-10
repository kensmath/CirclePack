package script;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.dnd.DropTarget;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.tree.MutableTreeNode;

import allMains.CPBase;
import circlePack.PackControl;
import dragdrop.EditDropListener;

/**
 * StackBox for SECTION nodes. This basically just has a title for now.
 * @author kens
 *
 */
public class SectionSBox extends StackBox {

	private static final long 
	serialVersionUID = 1L;

	int lheight;
	int lwidth;

	JEditorPane titleField;
	JPanel headerPanel;
	JPanel accCanPanel;
	Border dispB; // title border
	Border editB;
	JButton ocButton;

	// Constructor
	public SectionSBox(CPTreeNode tnode,int mode) {
		super(tnode,mode);

		editModeMenu=cmdActionPopup(true);
		dispModeMenu=cmdActionPopup(false);
		boxMenu=dispModeMenu;
		addMouseListener(this);
		buildComponents();
		redisplaySB(myWidth);
	}

	public void buildComponents() {

		// borders
		Border inner=new EtchedBorder(Color.blue,Color.blue);
		dispBorder=BorderFactory.createCompoundBorder(marginBorder,inner);
		dispB=new EmptyBorder(0,0,0,0);
		editB=new LineBorder(Color.LIGHT_GRAY);

		// build headerPanel
		headerPanel=new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel,BoxLayout.LINE_AXIS));
		headerPanel.setBackground(Color.white);
		headerPanel.addMouseListener(this);
		new DropTarget(headerPanel,new EditDropListener(this));
		headerPanel.setAlignmentX(0);

		// prepare title field
		titleField = new JEditorPane("TrueType",tNode.displayString);
		util.EmacsBindings.addEmacsBindings(titleField);
		titleField.setFont(new Font("Serif",Font.BOLD+Font.ITALIC,14+PackControl.fontIncrement));
		titleField.setMinimumSize(new Dimension(150,30));
		titleField.setEditable(false);
		titleField.setAlignmentY(0);
		titleField.addMouseListener(this);
		new DropTarget(titleField,new EditDropListener(this));

		accCanPanel = new JPanel();
		new DropTarget(accCanPanel,new EditDropListener(this));
		accCanPanel.setLayout(null);
		acceptButton.setBounds(0,2,acceptButton.getWidth(),acceptButton.getHeight());
		accCanPanel.add(acceptButton);
		cancelButton.setBounds(0,12,cancelButton.getWidth(),cancelButton.getHeight());
		accCanPanel.add(cancelButton);
		deleteButton.setBounds(74,10,deleteButton.getWidth(),deleteButton.getHeight());
		accCanPanel.add(deleteButton);
		accCanPanel.setBorder(new EtchedBorder());
		setFixedSizes(accCanPanel,96,30);
		new DropTarget(accCanPanel,new EditDropListener(this));

		// STACK
		accCanPanel.setAlignmentY(0);
	}

	private void buildHeader() {
		headerPanel.removeAll();
		ocButton=openButton;
		if (isOpen) ocButton=closeButton;
		ocButton.setAlignmentY(0);
		headerPanel.add(ocButton);

		if (currentMode==DISPLAY) { // display
			titleField.setFont(new Font("Serif",Font.BOLD+Font.ITALIC,14+PackControl.fontIncrement));
			titleField.setForeground(Color.blue);
			titleField.setEditable(false);
			titleField.setBorder(dispB);
		}
		else { // currentMode==EDIT or NEW)
			headerPanel.add(accCanPanel);
			titleField.setFont(new Font("TrueType",Font.ITALIC,14+PackControl.fontIncrement));
			titleField.setForeground(Color.black);
			titleField.setEditable(true);
			titleField.setBorder(editB);
		}

		titleField.setMaximumSize(new Dimension(myWidth-120,25));
		headerPanel.add(titleField);

		headerPanel.setMinimumSize(new Dimension(150,40));
		headerPanel.setMaximumSize(new Dimension(myWidth,200));
	}

	public void redisplaySB(int wide) { // rebuild each time
		myWidth=wide;
		removeAll();  // get rid of all child StackBox's
		buildHeader();
		this.add(headerPanel);
		this.setBorder(dispBorder);
		//		setMinimumSize(new Dimension(150,20));
		//		setMaximumSize(new Dimension(tNode.width,3000));
		if (!isOpen) {
			revalidate(); 
			return;
		}

		// put in child StackBox's
		for (int i=0;i<tNode.getChildCount();i++) {
			CPTreeNode tn=tNode.getChild(i);
			this.add(tn.stackBox);
		}
		revalidate();
	}

	public void startEdit() {
		if (currentMode!=DISPLAY) return;

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		currentMode=EDIT;
		boxMenu=editModeMenu;
		tNode.tntype=CPTreeNode.EDIT_SECTION;
		isOpen=true; // open to help add elements; user can always close
		redisplaySB(myWidth);
		//		manager.repopulateRecurse((CPTreeNode)tNode.getParent());

		//AF>>>//
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
		
		if (currentMode==NEW && tNode.getChildCount()==0) {
			deleteNode();
		}
		else {
			tNode.tntype=CPTreeNode.SECTION;
			currentMode=DISPLAY;
			boxMenu=dispModeMenu;
		}

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
		if (currentMode==DISPLAY) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		tNode.displayString=titleField.getText().replace('\n',' ').replace('\"','\'');
		tNode.tntype=CPTreeNode.SECTION;
		currentMode=DISPLAY;
		boxMenu=dispModeMenu;
		if (!manager.hasChanged) {
			manager.hasChanged=true;
			PackControl.scriptHover.scriptTitle(manager.scriptName,true);
		}
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
	 * Delete SECTION and all its children; should have moved children 
	 * already if they were to be saved -- see StackBox code.
	 * I think redisplaySB is called elsewhere.
	 */
	public void deleteNode() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		deleteChildNodes();
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		try{
			parTN.remove((MutableTreeNode)tNode);
		} catch(NullPointerException npe){}
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

	/**
	 * Remove all the contents, leaving empty SECTION
	 */
	public void deleteChildNodes() {
		for (int i=0;i<tNode.getChildCount();i++) {
			CPTreeNode cpTN=(CPTreeNode)tNode.getChild(i);
			cpTN.stackBox.deleteNode();
		}	
	}

	public void createNewTN() {	}

	/**
	 * Create menus for 'SectionSBox's, either edit or display mode.
	 * @param editmode boolean: true, then for edit mode
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

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/sectionAbove.png"));
		mi=new JMenuItem("add section above",ii);
		mi.setActionCommand("add_above_section");
		mi.addActionListener(this);
		editpop.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/sectionBelow.png"));
		mi=new JMenuItem("add section below",ii);
		mi.setActionCommand("add_below_section");
		mi.addActionListener(this);
		editpop.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/kill_16x16.png"));
		mi=new JMenuItem("delete this section",ii);
		mi.setActionCommand("delete_node");
		mi.addActionListener(this);
		editpop.add(mi);

		return editpop;
	}

	public int getHeaderHeight() {
		try {
			return headerPanel.getHeight();
		} catch (Exception ex) {
			return 10;
		}
	}
}
