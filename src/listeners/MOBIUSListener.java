package listeners;


import java.awt.event.ActionEvent;

import javax.swing.JMenuItem;

import mytools.MyPopupMenu;
import mytools.MyToolHandler;

/**
 * This will be for the the MOBIUSHandler and utilityHandler;
 * see 'PACKMODListener' for the 'PACKMOBHandler'.
 */
public class MOBIUSListener extends MyToolListener {
	
	// Constructor
	public MOBIUSListener(MyToolHandler tH) {
		super(tH);
	}
	
	// Methods required in subclasses of MyToolListerner
	public MyPopupMenu createBarMenu() { // menu for the JPanel
		MyPopupMenu bMenu = new MyPopupMenu(parentHandler,"Mobius Tools");
		JMenuItem menuItem;

		menuItem = new JMenuItem("New Mobius");
		menuItem.setActionCommand("New MyTool");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);

		menuItem = new JMenuItem("Save Mobius Tools?");
		menuItem.setActionCommand("Save MyTools");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);
		
		return bMenu;
	}
	
	public MyPopupMenu createToolMenu() { // menu for the tools on the JPanel
		MyPopupMenu tMenu = new MyPopupMenu(parentHandler,"Command Tool");
		JMenuItem menuItem;
		
		menuItem = new JMenuItem("Move Icon earlier");
		menuItem.setActionCommand("Move left");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);
		
		menuItem = new JMenuItem("Move Icon later");
		menuItem.setActionCommand("Move right");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);
		
		menuItem = new JMenuItem("Show/Edit");
		menuItem.setActionCommand("Edit MyTool");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);

		menuItem = new JMenuItem("Delete");
		menuItem.setActionCommand("Delete MyTool");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);
		
		return tMenu;
	}
	
// ========== actions specified in one (and only one) of the following =====
	
	/**
	 * The object has a CmdString with no command, so sort by name.
	 */
	public void sortByName(String cname) {
	}
	
	/**
	 * The object is a MenuItem, so sort by actionCommand.
	 */
	public void sortByAction(String cmd) {
  		if (cmd.equals("New MyTool")) {
  			parentHandler.openToolEditor();
  		}
  		else if (cmd.equals("Save MyTools")) {
  			parentHandler.displaySaveDialog();
  		}
  		else if (cmd.equals("Delete MyTool")) {
  			parentHandler.deleteTool(); 
  		}
  		else if (cmd.equals("Edit MyTool")) {
  			parentHandler.editTool();
  		}
  		else if (cmd.equals("Copy MyTool")) {
  			parentHandler.copyTool();
  		}
  		else if (cmd.equals("Paste MyTool")) {
  			parentHandler.pasteTool();
  		}
	  	else if (cmd.equals("Move left")) {
	  		parentHandler.moveBackward();
	  	}
	  	else if (cmd.equals("Move right")) {
	  		parentHandler.moveForward();
	  	} 
	}

	public void sortCursorCtrl(ActionEvent e) {}
}
