package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import auxFrames.SphWidget;
import exceptions.MiscException;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
/**
 * This code incorporates old 'SpherePack' code as a
 * PackExtender. Goal is to allow the user to manually
 * adjust spherical radii to try to get a spherical
 * packing or branched packing, with various feedback
 * and manipulation features to help. On request, an
 * auxiliary frame like the current one pops open. 
 */

public class SpherePack extends PackExtender {
	
	SphWidget auxFrame;  // auxiliary frame for 'SpherePack' manipulations
	
	// Constructor
	public SpherePack(PackData p) {
		super(p);
		packData=p;
		extensionType="SPHEREPACK";
		extensionAbbrev="SP";
		toolTip="'SpherePack' provides tools to manually adjust "+
			"spherical radii for a complex, typically to try for "+
					"packing radii";
		registerXType();
		int rslt=1;
		try {
			if (!packData.status || packData.nodeCount<4)
				rslt=0;
			else if (packData.hes<=0) 
				rslt=cpCommand(packData,"geom_to_s");
			if (rslt==1) {
				auxFrame=new SphWidget(packData);
				if (auxFrame==null) rslt=0;
			}

		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			errorMsg("SP: failed converting to sph geom, or aux frame failed");
			running=false;
		}
		if (running) {
			packData.packExtensions.add(this);
		}
	}

	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
	
		try {
		if (cmd.startsWith("update")) {
			auxFrame.updateBars();
			return 1;
		}
		
		if (cmd.startsWith("open")) {
			auxFrame.setVisible(true);
			return 1;
		}
		
		if (cmd.startsWith("close")) {
			auxFrame.setVisible(false);
			return 1;
		}

		if (cmd.startsWith("lock")) {
			NodeLink vlist=new NodeLink(packData,items);
			Iterator<Integer> vlst=vlist.iterator();
			while (vlst.hasNext()) {
				auxFrame.setLock(vlst.next());
			}
			return 1;
		}
		} catch(Exception ex) {
			throw new MiscException("Some problem executing |sp| command "+cmd);
		}
		return 1;
	}
	
	public void killMe() {
		if (auxFrame!=null)
			auxFrame.dispose();
		auxFrame=null;
		super.killMe();
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("open",null,null,"opens the 'SpherePack' frame"));
		cmdStruct.add(new CmdStruct("close",null,null,"closes the 'SpherePack' frame"));
		cmdStruct.add(new CmdStruct("lock","v..",null,"lock in specified radii"));
		cmdStruct.add(new CmdStruct("update",null,null,"update from packData"));
	}
	
}
