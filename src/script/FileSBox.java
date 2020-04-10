package script;

import images.CPIcon;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.dnd.DropTarget;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.tree.MutableTreeNode;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import dragdrop.EditDropListener;
import exceptions.DataException;
import exceptions.ParserException;

/**
 * StackBox for FILE nodes, i.e., nodes in the script window 
 * associated with included files.
 * @author kens
 *
 */
public class FileSBox extends StackBox {

	private static final long 
	serialVersionUID = 1L;

	String fileName;
	File editFile; // holds temporary edits of file
	File file;
	JPanel contentPanel;
	JScrollPane contentScroll;
	JTextField nameField;
	JEditorPane jep;
	JButton fileButton;
	JPanel accCanPanel;
	JLabel nNLabel;
	JPanel headPanel; 
	Component headGlue;
	Border emptyBorder;
	IncludedFile includedFile;

	// Constructor
	public FileSBox(CPTreeNode tnode,int mode,IncludedFile iFile) {
		super(tnode,mode);
		includedFile=iFile;
		marginBorder=new EmptyBorder(4,4,4,4); // modify margin size
		displayIcon=CPIcon.CPImageIcon("script/fileIcon.png"); // shouldn't be needed
		fileName=tnode.displayString;
		editModeMenu=cmdActionPopup(true);
		dispModeMenu=cmdActionPopup(false);
		boxMenu=dispModeMenu;
		addMouseListener(this);
		buildComponents();
		buildSB();
	}

	public void buildComponents() {
		emptyBorder=new EmptyBorder(0,0,0,0);
		Border inner=new LineBorder(Color.green);
		dispBorder=marginBorder;
		editBorder=BorderFactory.createCompoundBorder(marginBorder,inner);

		headPanel=new JPanel();
		headPanel.setLayout(new BoxLayout(headPanel,BoxLayout.LINE_AXIS));
		headPanel.setBackground(Color.white);
		new DropTarget(headPanel,new EditDropListener(this));

		// button with filename
		fileButton = new JButton(tNode.displayString);
		fileButton.setToolTipText(tNode.displayString);
		new DropTarget(fileButton,new EditDropListener(this));
		fileButton.addMouseListener(this);
		fileButton.setToolTipText(includedFile.getTypeString());

		// name area
		nameField = new JTextField(tNode.displayString);
		util.EmacsBindings.addEmacsBindings(nameField);
		nameField.setBorder(emptyBorder);
		nameField.setMinimumSize(new Dimension(150,22));
		nameField.setMaximumSize(new Dimension(300,22));
		nameField.setPreferredSize(new Dimension(300,22));
		nameField.addMouseListener(this);

		// accept/cancel/delete cluster
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

		// new name label
		nNLabel=new JLabel("  new name?   ");

		headGlue=Box.createHorizontalGlue();
	}

	public void redisplaySB(int wide) {
		myWidth=wide;
		this.removeAll();
		headPanel.removeAll();

		// when in LineSBox, closed, and DISPLAY, just show as compact bundle
		CPTreeNode parTN=(CPTreeNode)this.tNode.getParent();
		if (parTN!=null && parTN.tntype==CPTreeNode.LINEUP && !isOpen() && currentMode==DISPLAY ) {
			headPanel.add(fileButton);
			this.add(headPanel);
			this.setBorder(emptyBorder);
			revalidate();
			return;
		}

		buildSB();

		if (currentMode==DISPLAY) { 
			setBorder(dispBorder);
		}
		else {
			buildFileEditor();
			setBorder(editBorder);
		}
		headPanel.setAlignmentX(0);
		this.add(headPanel);
		if (currentMode!=DISPLAY) {
			contentPanel.setAlignmentX(0);
			add(contentPanel);
		}
		revalidate();
	}

	public void buildSB() {
		if (currentMode==DISPLAY) {
			headPanel.add(fileButton);
			nameField.setEditable(false);
			nameField.setBorder(emptyBorder);
			nameField.setToolTipText("data file name");
			nameField.setBackground(Color.white);
		}
		else {
			headPanel.add(accCanPanel);
			headPanel.add(nNLabel); // 'new name?' label
			nameField.setEditable(true);
			nameField.setText(tNode.displayString);
			nameField.setBorder(new LineBorder(Color.black));
			nameField.setToolTipText("Change name for this data?");
			headPanel.add(nameField);
		}
		headPanel.add(headGlue);
		headPanel.setBorder(emptyBorder);
	}

	public void buildFileEditor() {
		if (currentMode==DISPLAY)
			return;  // file edit window not used in DISPLAY mode
//		file=new File(System.getProperty("java.io.tmpdir"),
//				new String(manager.id + tNode.displayString));
		// open editorPane
		try {
			jep = new JEditorPane(getFileURL());
			util.EmacsBindings.addEmacsBindings(jep);
		} catch(Exception ex) {
			PackControl.consoleCmd.dispConsoleMsg("Problem opening "+
					includedFile.origName);
			jep = new JEditorPane();
			util.EmacsBindings.addEmacsBindings(jep);
		}

		contentPanel=new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel,BoxLayout.PAGE_AXIS));// null);
		jep.setFont(new Font(jep.getFont().toString(),Font.PLAIN,11));
		jep.setBorder(new EmptyBorder(2,2,2,2));

		contentScroll=new JScrollPane(jep);
		contentScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		contentScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		contentScroll.setAlignmentX(0);
		contentPanel.add(contentScroll);
		contentScroll.setMinimumSize(new Dimension(200,30));
		contentScroll.setMaximumSize(new Dimension(DataSBox.EDITWIDTH,200));
		contentPanel.setPreferredSize(new Dimension(DataSBox.EDITWIDTH,200));
	}

	public void startEdit() {
		if (currentMode!=DISPLAY) return;
		if (includedFile.origName.startsWith("AboutImage")) {
			CirclePack.cpb.errMsg("Editing of 'AboutImage' is not allowed");
			return;
		}

		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		currentMode=EDIT;
		boxMenu=editModeMenu;
		tNode.tntype=CPTreeNode.EDIT_FILE;
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		if (parTN.tntype==CPTreeNode.LINEUP) 
			parTN.stackBox.open(); // destroy this LINEUP node
		manager.repopulateRecurse(manager.cpDataNode);

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
			manager.repopulateRecurse(manager.cpDataNode);
			return;
		}
		else {
			tNode.tntype=CPTreeNode.FILE;
			currentMode=DISPLAY;
			boxMenu=dispModeMenu;
			manager.repopulateRecurse(manager.cpDataNode);
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

	/**
	 * Note, avoid name conflicts. Type of file does not change (even if a new
	 * extension type is given).
	 */
	public void acceptEdit() {
		if (currentMode==DISPLAY) return;
		
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//

		// get the name
		String text=nameField.getText().trim().replace('\n',' ').replace('\"','\'');
		if (text.length()==0) {
			throw new ParserException("Data file name was empty, accept action aborted");
		}
		if (!text.equals(tNode.displayString)) { // if name has been changed, 
				
			// name conflict? add "_1" until unused name is found
			while(manager.check4filename(text)>=0) 
				text=new String(text+"_1");
			
			// set 'origName' and new temporary file
			includedFile.origName=new String(text);
			int new_id=new Random().nextInt(32000);
			tNode.displayString=new String(text);
			includedFile.tmpFile=new File(System.getProperty("java.io.tmpdir"),
					new String(new_id + tNode.displayString));
		}
		
		// write
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(includedFile.tmpFile));
			jep.write(writer);

			includedFile.origName=new String(tNode.displayString);
			fileButton.setText(tNode.displayString);
		} catch (Exception ioe) {
			PackControl.consoleCmd.dispConsoleMsg("Exception in accepting "+
						text+" as a data file");
		}
		
		if (!manager.hasChanged) {
			manager.hasChanged=true;
			PackControl.scriptHover.scriptTitle(manager.scriptName,true);
		}
		tNode.tntype=CPTreeNode.FILE;
		currentMode=DISPLAY;
		boxMenu=dispModeMenu;
		manager.repopulateRecurse(manager.cpDataNode);
		
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

	/**
	 * Delete this FILE: get rid of CPTreeNode and StackBox
	 */
	public void deleteNode() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		// remove from list, update file count
		// 		if (currentMode!=DISPLAY) contentScroll=null; // why is this needed? 
		for (int j=manager.includedFiles.size()-1;j>=0;j--) {
			IncludedFile iFile=(IncludedFile)manager.includedFiles.get(j);
			if (iFile.origName.equals(tNode.displayString)) {
				manager.includedFiles.remove(j);
			}
		}
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		try{
			parTN.remove((MutableTreeNode)tNode);
		} catch(NullPointerException npe){}
		if (!manager.hasChanged) {
			manager.hasChanged=true;
			PackControl.scriptHover.scriptTitle(manager.scriptName,true);
		}
		manager.repopulateRecurse(manager.cpDataNode);
		
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
	 * Create menus for 'FileBox's, either edit or display mode.
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

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/textBelow.png"));
		mi=new JMenuItem("add data file below",ii);
		mi.setActionCommand("add_below_text");
		mi.addActionListener(this);
		editpop.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/build.png"));
		mi=new JMenuItem("export",ii);
		mi.setActionCommand("export_file");
		mi.addActionListener(this);
		editpop.add(mi);

		ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/kill_16x16.png"));
		mi=new JMenuItem("delete this data file",ii);
		mi.setActionCommand("delete_node");
		mi.addActionListener(this);
		editpop.add(mi);

		return editpop;
	}
	
	/**
	 * 
	 * @return URL
	 */
	public URL getFileURL() {
		try {
			return includedFile.tmpFile.toURI().toURL();
		} catch (Exception ex) {
			throw new DataException("failed to get data file URL");
		}	
	}

}
