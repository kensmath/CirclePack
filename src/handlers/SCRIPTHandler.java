package handlers;

import java.io.File;

import javax.swing.border.EmptyBorder;

import listeners.SCRIPTListener;
import mytools.MyTool;
import mytools.MyToolHandler;
import canvasses.MyCanvasMode;

public class SCRIPTHandler extends MyToolHandler {

	// Constructor 
	public SCRIPTHandler(File toolFile) {
		super(toolFile,"SCRIPT:");
		toolBar.setBorder(new EmptyBorder(0,0,0,0));
//		toolBar.setToolTipText("Tools from the current script");
		toolListener=new SCRIPTListener(this);
	}
	
	/**
	 * Add clone at current 'toolIndx' (set elsewhere) of 
	 * 'toolVector'; for 'MyCanvasMode', update 'mytool.menuItem'
	 * (for the original, not the clone) and add it to 'scriptModes'.
	 */
	public void updateClone(MyTool mytool) {
		if (mytool==null) return;
		if (toolIndx>=toolVector.size()) toolIndx=toolVector.size(); // default to last
		else if (toolIndx>=0) { // replacing existing
			toolVector.remove(toolIndx);
		}
//		 deBugging.PrintIcon.printImageIcon(mytool.cpIcon.imageIcon,"afterclone");
		toolVector.add(toolIndx,mytool.clone());
//		 deBugging.PrintIcon.printImageIcon(mytool.cpIcon.imageIcon,"afterclone");

		// update original's 'menuItem'
		if (mytool instanceof MyCanvasMode)
			((MyCanvasMode)mytool).updateMenuItem();
		
		toolEditor.addTool(mytool.cpIcon); // add baseIcon to 'iconBox' list
		toolIndx=toolVector.size();
		hasChanged=true;
		repopulateTools();
	}
	
}
