package handlers;


import java.io.File;

import listeners.MOBIUSListener;
import mytools.MyToolHandler;

/**
 * This handles Mobius MyTool's on a panel which applies
 * to all packings. See the 'PACKMOBHandler's for sidepairing
 * and utility transformations attached to specific packings.
 * @author kens
 *
 */
public class MOBIUSHandler extends MyToolHandler {
	
	// Constructor 
	public MOBIUSHandler(File toolFile) {
		super(toolFile,"MOBIUS:");
		toolListener=new MOBIUSListener(this);
		if (toolFile!=null) appendFromFile(toolFile);
	}
}
