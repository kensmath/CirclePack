package script;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.dnd.DropTarget;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.tree.MutableTreeNode;

import allMains.CPBase;
import circlePack.PackControl;
import dragdrop.EditDropListener;

/**
 * StackBox for TEXT nodes
 * @author kens
 *
 */
public class TextSBox extends StackBox {

	private static final long 
	serialVersionUID = 1L;

	JPanel accCanPanel;
	JEditorPane editPane; // holds document when in EDIT mode
	JTextArea textPane; // holds document when in DISPLAY mode (only needed for linewrap feature)
	Border emptyBorder;

	// Constructor
	public TextSBox(CPTreeNode tnode,int mode) {
		super(tnode,mode);
		setLayout(new BoxLayout(this,BoxLayout.LINE_AXIS)); // override PAGE_AXIS
		editModeMenu=cmdActionPopup(true);
		dispModeMenu=cmdActionPopup(false);
		addMouseListener(this);
		setMinimumSize(new Dimension(150,30));
		buildComponents();
		buildSB();

		// set icons
		//		displayIcon=null;
	}

	public void buildComponents() {

		// borders
		emptyBorder=new EmptyBorder(0,0,0,0);
		Border inner=new LineBorder(Color.green);
		dispBorder=marginBorder;
		editBorder=BorderFactory.createCompoundBorder(marginBorder,inner);

		// acc/can cluster
		accCanPanel = new JPanel();
		new DropTarget(accCanPanel,new EditDropListener(this));
		accCanPanel.setLayout(null);
		accCanPanel.setAlignmentY(0);

		acceptButton.setBounds(0,2,acceptButton.getWidth(),acceptButton.getHeight());
		accCanPanel.add(acceptButton);
		cancelButton.setBounds(0,12,cancelButton.getWidth(),cancelButton.getHeight());
		accCanPanel.add(cancelButton);
		deleteButton.setBounds(74,10,deleteButton.getWidth(),deleteButton.getHeight());
		accCanPanel.add(deleteButton);
		accCanPanel.setBorder(new EtchedBorder());
		setFixedSizes(accCanPanel,96,30);
		accCanPanel.setAlignmentY(0);

		editPane = new JEditorPane();
		editPane.setFont(new Font(editPane.getFont().toString(),
				Font.ROMAN_BASELINE+Font.PLAIN,11+PackControl.fontIncrement));
		editPane.setText(tNode.displayString);
		util.EmacsBindings.addEmacsBindings(editPane);
		new DropTarget(editPane,new EditDropListener(this));
		editPane.setMinimumSize(new Dimension(150,30));
		editPane.addMouseListener(this);
		editPane.setAlignmentY(0);
		editPane.setBorder(new LineBorder(Color.green));

		textPane=new JTextArea();
		textPane.setFont(new Font(textPane.getFont().toString(),
				Font.ROMAN_BASELINE+Font.PLAIN,11+PackControl.fontIncrement));
		textPane.setText(tNode.displayString);
		textPane.setMinimumSize(new Dimension(150,30));
		textPane.setEditable(false);
		textPane.setLineWrap(true);
		textPane.setWrapStyleWord(true);
		new DropTarget(textPane,new EditDropListener(this));
		textPane.addMouseListener(this);
		textPane.setAlignmentY(0);
	}

	public void redisplaySB(int wide) {
		myWidth=wide;
		this.removeAll();
		buildSB();

		if (currentMode==DISPLAY) {
			this.add(textPane);
			this.setBorder(dispBorder);
		}
		else { // EDIT
			this.add(accCanPanel);
			this.add(editPane);
			this.setBorder(marginBorder);//editBorder);
		}
		revalidate();
	}

	private void buildSB() {
		if (currentMode==StackBox.DISPLAY) {
			editPane.setEditable(false);
		}
		else {
			editPane.setEditable(true);
		}

		// set menu
		boxMenu=editModeMenu;
		if (currentMode==DISPLAY)
			boxMenu=dispModeMenu;
	}

	/**
	 * 'editPane' gets text from 'textPane'
	 */
	public void startEdit() {
		if (currentMode!=DISPLAY) return; // already in edit mode

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		currentMode=EDIT;
		editPane.setText(textPane.getText());
		tNode.tntype=CPTreeNode.EDIT_TEXT;

		redisplaySB(myWidth);
		//AF>>>//
		// Unlock the viewport and set the view to this component.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
				setViewRect();
			}
		});
		//<<<AF//
	}

	/**
	 * 'textPane' contents should not have changed until
	 * 'acceptEdit'
	 */
	public void cancelEdit() {
		if (currentMode==DISPLAY) return;

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		if (currentMode==NEW) {
			CPTreeNode parTN=(CPTreeNode)tNode.getParent();
			deleteNode(); // only place where NEW & EDIT diverge?
			manager.repopulateRecurse(parTN);
		}
		else {
			tNode.tntype=CPTreeNode.TEXT;
			currentMode=DISPLAY;
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
	 * Here 'textPane' is reset to 'editPane's contents. During
	 * edit, new content is only in 'editPane'. Note that if the
	 * text is all whitespace, the node is discarded.
	 */
	public void acceptEdit() {
		if (currentMode==DISPLAY) return;

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		String text=editPane.getText().trim(); 
		//.replace('\n',' ').replace('\"','\'');
		//text=text.replaceAll("<=",".le.");
		//text=text.replaceAll("<",".lt.");
		//text=text.replaceAll(">=",".ge.");
		//text=text.replaceAll(">",".gt.");
		if (text.length()==0) {
			deleteNode(); // only place where NEW & EDIT diverge?
			redisplaySB(myWidth);
		}
		else {
			tNode.displayString=text; // may not be needed until saving the script
			textPane.setText(editPane.getText());

			tNode.tntype=CPTreeNode.TEXT;
			currentMode=DISPLAY;
			if (!manager.hasChanged) {
				manager.hasChanged=true;
				PackControl.scriptHover.scriptTitle(manager.scriptName,true);
			}
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
	 * Delete this node: get rid of CPTreeNode and StackBox
	 */
	public void deleteNode() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		// Delete this CPTreeNode from the tree (ie. distroy it)
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		try{
			parTN.remove((MutableTreeNode)tNode);
		} catch(NullPointerException npe){}
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

	public void deleteChildNodes() {}

	/**
	 * Create menus for 'TextSBox's, either edit or display mode.
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

}
