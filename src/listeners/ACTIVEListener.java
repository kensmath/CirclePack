package listeners;

import handlers.ACTIVEHandler;
import input.TrafficCenter;

import java.awt.event.ActionEvent;

import mytools.MyPopupMenu;
import mytools.MyToolHandler;
import util.ModeMenuItem;
import canvasses.ActiveWrapper;
import circlePack.PackControl;
import exceptions.InOutException;

/**
 * Listener for the 'ActiveWrapper's, the canvasses with
 * lots of user interaction.
 * @author kens
 *
 */
public class ACTIVEListener extends MyToolListener {

	// Constructor
	public ACTIVEListener(ACTIVEHandler tH) {
		super((MyToolHandler)tH);
	}

	// So far there is no menu for the MAIN tool bar
	public MyPopupMenu createBarMenu() {
		return null;
	}

	// no tool menu yet, either
	public MyPopupMenu createToolMenu() { 
		return null;
	}

	public void sortByName(String cname) {
		if (cname.equals("Load packing")) {
			try {
				TrafficCenter.cmdGUI("load_pack");
			} catch (Exception ex) {
				throw new InOutException("error in choosing pack: "+ex.getMessage());
			}
			return;
		}
		else if (cname.equals("Show axes")) {
			PackControl.activeFrame.getCPDrawing().toggleAxisMode();
		}
		else if (cname.equals("Map open")) {
			PackControl.mapCanvasAction(true);
		}
		else if (cname.equals("Map close")) {
			PackControl.mapCanvasAction(false);
		}
	}

	public void sortByAction(String cmd) {}
	
	/** 
	 * Catch mode menu selections
	 */
	public void sortCursorCtrl(ActionEvent e) {
		Object src=(Object)e.getSource();
		if (src instanceof ModeMenuItem) {
			ActiveWrapper actWrapper=((ACTIVEHandler)parentHandler).activeWrapper;
			ModeMenuItem mmI=(ModeMenuItem)src;
			actWrapper.activeMode=mmI.parentMode;
			actWrapper.setCursor(actWrapper.activeMode.modeCursor);
		}
	}
	
}
