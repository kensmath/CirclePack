package listeners;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import mytools.MyPopupMenu;
import mytools.MyTool;
import mytools.MyToolHandler;
import util.ModeMenuItem;
import allMains.CPBase;

/**
 * This is an abstract class for listeners created by MyToolHandlers.
 * It creates appropriate bar menus and tool menus (in parallel with 
 * subclasses of the abstract MyToolEditor class).
 * The actionPerformed first executes commands found in JCPFrame.hashedTools. 
 * Then it calls abstract methods that are set up in subclasses to handle 
 * specific types of actions. 
 * 
 * Subclasses listen for actions from menuitems of its bar and tool 
 * menus. The parentHandler is mouselistener (e.g., so it knows tool 
 * index), while this listens for actions on menuitems.
 * 
 * @author kens
 *
 */
public abstract class MyToolListener implements ActionListener {
	public MyToolHandler parentHandler;
	public MyPopupMenu barMenu;
	public MyPopupMenu toolMenu;
	
	// Constructor
	public MyToolListener(MyToolHandler tH) {
		parentHandler=tH;
		
		// prepare bar menu
		barMenu=createBarMenu();
		if (barMenu!=null) 
			parentHandler.toolBar.setPopupMenu(barMenu);
		
		// prepare menu for tools on the bar
		toolMenu=createToolMenu();
		if (toolMenu!=null) 
			parentHandler.setButtonMenu(toolMenu);
	}
	
	// subclasses must supply these methods
	public abstract MyPopupMenu createBarMenu(); // menu for the JPanel 
	public abstract MyPopupMenu createToolMenu(); // menu for the tools on the JPanel
	
	// Most MyTools execute their own commands, but some (particularly on the
	//   MAIN menu) must be sorted by the 'name' in their CmdString.
	public abstract void sortByName(String cname); // sort by CmdString name
	
	// Other actions result from selecting MenuItems (on either menu): those
	//   are directed from here.
	public abstract void sortByAction(String cmd); // sort by CmdString name
	
	// For 'ACTIVEListeners' there are also popup cursor menu choices
	public abstract void sortCursorCtrl(ActionEvent e);
	
	/**
	 * actionPerformed first checks if the object is a tool
	 * that can execute its own command. If not, then the 
	 * subclass supplies the two sorting methods described above.
	 */
	public void actionPerformed (ActionEvent e){
		String command = e.getActionCommand();
		
		// have to fix up tools to get the dropable property here.
		MyTool mt=(MyTool)CPBase.hashedTools.get(command);
			
/* ================ this is recognized as a hashed command ================= */
		// If not dropable (e.g., cursor changes), try self executing first, 
		//   then search by name. If dropable, skip self execution since drop
		//   target will handle it.
		if (mt!=null) {

			/* ======== check for popup menu ==================== */
			if (mt.popUpMenu!=null) {
				Component cpt=(Component)e.getSource();
				mt.popUpMenu.show(cpt,24,24); // x,y relative to cpt
				return;
			}
			
			/* ========== Contains a command? ======= */

			String cpcmd=mt.getCommand();
			if (!mt.isDropable() && cpcmd!=null && cpcmd.length()!=0) {
				mt.execute(); 
				return;
			}
			
			/* ========== else, try sorting on CmdString name ======== */
			String cpname=mt.getName();
			if (cpname==null || cpname.length()==0) return;
			else sortByName(cpname);
			
			return;
		}
		
/* ================== the rest come from MenuItems ===================== */
		// Sort by ActionCommand: parentHandler should handle the action. 
		
		Object obj=e.getSource();
		if (obj instanceof ModeMenuItem) {
			sortCursorCtrl(e);
			return;
		}
		if (!(obj instanceof JMenuItem)) return;
  		sortByAction(command);
  		return;
	}
}
		
