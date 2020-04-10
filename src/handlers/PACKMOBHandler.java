package handlers;


import java.io.File;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import images.CPIcon;
import listeners.PACKMOBListener;
import mytools.MyTool;
import mytools.MyToolHandler;
import util.PopupBuilder;

/**
 * This handles the side pairing Mobius MyTool's associated 
 * with the currently active packing; in particular, 'toolVector' 
 * points to the 'packTools[]' vector containing the active packings
 * mobius tools. 
 */
public class PACKMOBHandler extends MyToolHandler {

	// each pack has vector of mobius tools held in 'packTools'
	// Note: 'toolVector' is simply a pointer to active pack vector
	private Vector<MyTool> []packTools;
	
	// Constructor 
	@SuppressWarnings("unchecked")
	public PACKMOBHandler(File toolFile) {
		super(toolFile,"MOBIUS:");
		toolListener=new PACKMOBListener(this);
		if (toolFile!=null) appendFromFile(toolFile);
		packTools=new Vector[CPBase.NUM_PACKS];
		for (int ii=0;ii<CPBase.NUM_PACKS;ii++) {
			packTools[ii]=new Vector<MyTool>();
		}
		toolVector=(Vector<MyTool>)packTools[0]; // redefine
		repopulateTools();
	}
	
	/**
	 * Create side pair mobius tool and add to appropriate vector/bar based 
	 * on pack number. If this is the active packing, update the side pair tool 
	 * bar and repaint. (This is not efficient, since it will be called for
	 * every new side pair tool, but I currently don't have any other place to
	 * call for updating.)
	 */
	public void createSidePairTool(int packnum,CPIcon cpIcon,String cmdtext,
			String nametext,String mnem,String tiptext,boolean dropit) {
		MyTool button=new MyTool(cpIcon,cmdtext,nametext,mnem,tiptext,toolType,
				dropit,toolListener,(PopupBuilder)null);
		button.addMouseListener(this);

		packTools[packnum].add(button);
		if (packnum==CirclePack.cpb.getActivePackNum()) {
			toolVector=packTools[packnum];
			repopulateTools();
		}
		return;
	}

	/**
	 * Wipe out the side-pair tools for the indicated pack; generally this
	 * is to clear sidepairings when updated ones are ready to be installed.
	 * @param packnum
	 */
	public void flushSides(int packnum) {
		packTools[packnum].removeAllElements(); 
		if (packnum==CirclePack.cpb.getActivePackNum()) {
			toolVector=packTools[packnum];
			repopulateTools();
		}
	}

	/**
	 * When changing packings, have to adjust which mobius tools are loaded.
	 * Note that active pack number has already been updated when we
	 * arrive here.
	 */
	public void changeActivePack() {
		toolVector=packTools[CirclePack.cpb.getActivePackNum()];
		toolIndx=toolVector.size();
		repopulateTools();
	}
	
	/**
	 * apply a Mobius transformation in the side pairing bar
	 */
	public void applyMobius() {
		MyTool mobTool=(MyTool)packTools[CirclePack.cpb.getActivePackNum()].get(toolIndx);
		mobTool.execute();
	}
	
}
