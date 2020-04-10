package listeners;

import java.awt.event.ActionEvent;

import allMains.CPBase;
import allMains.CirclePack;
import mytools.MyPopupMenu;
import mytools.MyToolHandler;

/**
 * Will be for the Script tool bar
 * See MyToolListener for more details.
 */
public class SCRIPTListener extends MyToolListener {
	
	// Constructor
	public SCRIPTListener(MyToolHandler tH) {
		super(tH);
	}
	
	// Methods required in subclasses of MyToolListerner
	// menu for the JPanel
	public MyPopupMenu createBarMenu() {
		return null;
	}
	
	// menu for the tools on the JPanel
	public MyPopupMenu createToolMenu() {
		return null;
	}
	
// actionPerformed of abstract class MyToolListener calls one (only one) 
	// Note: 'scriptAction' toolBar actions handled by ScriptManager.
	
	/**
	 * The object is a named tool; call for execution by name.
	 */
	public void sortByName(String cname) {
		CPBase.trafficCenter.parseWrapper(new String("["+cname+"]"),
				CirclePack.cpb.getActivePackData(),false,true,0,null);
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
