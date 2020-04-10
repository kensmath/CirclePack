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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.JTextComponent;

import circlePack.PackControl;

import allMains.CPBase;
import dragdrop.EditDropListener;

/**
 * StackBox for persistent 'CPdata' node, which holds 'FILE'
 * and/or 'LINEUP' nodes as children.
 * 
 * Formating: seems good as of 8/16/11
 * @author kens
 *
 */
public class DataSBox extends StackBox {

	private static final long 
	serialVersionUID = 1L;
	
	// no reason for fileBox's to be too wide, so use this as bound
	static int EDITWIDTH=800; // 

	JTextComponent headText; // indicates number of files
	JPanel headerPanel;
	JButton ocButton;  // holds openButton or closedButton
	Border headBorder;
	Border titleBorder;
	
	// Constructor
	public DataSBox(CPTreeNode tnode,int mode) {
		super(tnode,mode);
		boxMenu=cmdActionPopup(false);
		addMouseListener(this);
		setBackground(Color.white);
		buildComponents();
		this.setBorder(dispBorder);
		buildSB();
	}
	
	public void buildComponents() {
		Border outer=new EmptyBorder(2,2,2,2);
		Border inner=new LineBorder(Color.LIGHT_GRAY);
		dispBorder = BorderFactory.createCompoundBorder(outer, inner);
		editBorder=dispBorder;
		headBorder=new LineBorder(Color.green);
		titleBorder=new EmptyBorder(0,2,0,2);

		headerPanel=new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel,BoxLayout.LINE_AXIS));
		headerPanel.setBackground(Color.white);
		headerPanel.setMinimumSize(new Dimension(150,25));
		headerPanel.setAlignmentX(0);
		new DropTarget(headerPanel,new EditDropListener(this));
		
		// 
		headText=new JTextArea(tNode.displayString);
		headText.setFont(new Font("Serif",Font.BOLD+Font.ITALIC,16+PackControl.fontIncrement));
		headText.setEditable(false);
		headText.addMouseListener(this);

		// contents don't change
		ocButton=openButton;
		if (isOpen) ocButton=closeButton;
		headerPanel.add(ocButton);
		headerPanel.add(headText);
		
		new DropTarget(headText,new EditDropListener(this));
		headText.setAlignmentY(0);  // this may be reason +- button is higher

		if (isOpen) ocButton=closeButton;
		else ocButton=openButton;

	}
	
	private void buildSB() {
		ocButton=openButton;
		if (isOpen) ocButton=closeButton;
		headerPanel.remove(0);
		headerPanel.add(ocButton,0);
		this.add(headerPanel);
	}
	
	public void redisplaySB(int wide) {
		myWidth=wide;
		this.removeAll();
		buildSB();

		// set size info
		// STACK
//		int wdth=((myWidth-4)<EDITWIDTH) ? myWidth-4 : EDITWIDTH;
		headerPanel.setMaximumSize(new Dimension(myWidth,40));
		headerPanel.setPreferredSize(new Dimension(myWidth,30));
		
		// add children if open
		updateCount();
		if (!isOpen) return;
		tNode.consolidateNodes();
		for (int i=0;i<tNode.getChildCount();i++) {
			CPTreeNode tn=tNode.getChild(i);
			this.add(tn.stackBox);
		}
		revalidate();
	}
	
	public void open() {
		if (isOpen) return;
		isOpen=true;
		manager.repopulateRecurse(tNode);
	}
	
	/**
	 * Displaying number of included CPdata files in script panel
	 */
	public void updateCount() {
		tNode.displayString=new String(" Files: "+manager.includedFiles.size());
		headText.setText(tNode.displayString);
	}
	
	// nothing to edit
	public void startEdit() {} 
	
	public void cancelEdit() {}
		
	public void acceptEdit() {}
	
	/**
	 * Delete all FILE's.
	 */
	public void deleteNode() {
		deleteChildNodes();
		manager.redisplayCPdataSB();
	}
	
	/**
	 * Remove all the contents, but leave CPDATA itself
	 */
	public void deleteChildNodes() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		for (int i=0;i<tNode.getChildCount();i++) {
			CPTreeNode cpTN=(CPTreeNode)tNode.getChild(i);
			cpTN.stackBox.deleteNode();
		}
		
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
     * Create menus for 'DataBox's, either edit or display mode.
     * @param editmode boolean: true, then for edit mode
     * @return JPopupMenu
     */
	public JPopupMenu cmdActionPopup(boolean editmode) {
		JPopupMenu editpop=new JPopupMenu();

		ImageIcon ii;
		JMenuItem mi;

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/textBelow.png"));
		mi=new JMenuItem("add new data file",ii);
		mi.setActionCommand("add_below_text");
		mi.addActionListener(this);
		editpop.add(mi);
		
		return editpop;
	}
		
}
