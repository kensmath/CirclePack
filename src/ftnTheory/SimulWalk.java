package ftnTheory;

import java.util.Vector;

import packing.PackData;
import packing.PackExtender;
import exceptions.CombException;

/**
 * For construction of 'Simultaneous' covering surfaces. 
 */
public class SimulWalk extends PackExtender {
	
	PackData pack2; // second ground packing
	PackData simulCover;  // simulcovering packing
	
	// Constructor
	public SimulWalk(PackData p) {
		super(p);
		packData=p;
		extensionType="SIMULWALK";
		extensionAbbrev="SW";
		toolTip="'SimulWalk' is for manipulating 'Simultaneous' coverings";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}	
	}
	
	/** 
	 * Given a packing and two neighboring verts, to be
	 * associated with 0 and 1, color faces black and white
	 * if possible --- i.e., if the complex is tripartite.
	 * Third type of vert is 'infty'. Order {0,1,infty} is
	 * white, opposite order is black.
	 * @param pk
	 * @param v0; '0' vert
	 * @param v1; '1' vert (must be neighbor of v0)
	 */
	public static void BlackWhiteFaces(PackData p,int v0,int v1) {
		
		// check suitability
		if (3*(p.nodeCount/3)!=p.nodeCount)
			throw new CombException("Does not have nodeCount divisible by 3");
		if (v0<1 || v1 < 1 || v0>p.nodeCount || v1>p.nodeCount ||
				p.nghb(v0,v1)<0)
			throw new CombException("error in vertices");
		
	}
	
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
//		Vector<String> items = null;
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
//		cmdStruct.add(new CmdStruct("setNS","n s",null,"Set north/south poles"));
	}
}
