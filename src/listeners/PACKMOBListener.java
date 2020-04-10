package listeners;


import handlers.PACKMOBHandler;

import java.awt.event.ActionEvent;

import javax.swing.JMenuItem;

import mytools.MyPopupMenu;
import mytools.MyToolHandler;

/**
 * This will be for the the MOBIUSHandler and utilityHandler;
 * see PACKMOBListener for the PACKMOBHandler.
 */
public class PACKMOBListener extends MyToolListener {
	public MyPopupMenu sidePairBarMenu; // extra menu for sidepair panel, which 
	
	// Constructor
	public PACKMOBListener(MyToolHandler tH) {
		super(tH);
	}
	
	// Methods required in subclasses of MyToolListerner
	public MyPopupMenu createBarMenu() { // menu for the JPanel
		MyPopupMenu bMenu = new MyPopupMenu(parentHandler,"Mobius Tools");
		JMenuItem menuItem;

		menuItem = new JMenuItem("Update");
		menuItem.setActionCommand("update sidepairings");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);

		menuItem = new JMenuItem("Save Side Pairings?");
		menuItem.setActionCommand("Save side pairings");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);
		
		return bMenu;
	}
	
	public MyPopupMenu createToolMenu() { // menu for the tools on the JPanel
		MyPopupMenu tMenu = new MyPopupMenu(parentHandler,"Side Pairing Maps");
		JMenuItem menuItem;
		
		menuItem = new JMenuItem("Show details");
		menuItem.setActionCommand("show details");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);

		menuItem = new JMenuItem("Copy");
		menuItem.setActionCommand("copy mobius");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);

		menuItem = new JMenuItem("Paste");
		menuItem.setActionCommand("paste mobius");
		menuItem.addActionListener(this);
		tMenu.add(menuItem);

		menuItem = new JMenuItem("Apply Mobius");
		menuItem.setActionCommand("apply mobius");
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
  		if (cmd.equals("update sidepairings")) {
  			parentHandler.openToolEditor();
  		}
  		else if (cmd.equals("show details")) {
  			parentHandler.editTool();
  		}
  		else if (cmd.equals("copy mobius")) {
  			parentHandler.copyTool();
  		}
  		else if (cmd.equals("paste mobius")) {
  			parentHandler.pasteTool();
  		}
  		else if (cmd.equals("apply mobius")) {
  			PACKMOBHandler pmh=(PACKMOBHandler)parentHandler;
  			pmh.applyMobius();
  		}
	}
	
	public void sortCursorCtrl(ActionEvent e) {}
}
