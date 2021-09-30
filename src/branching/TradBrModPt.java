package branching;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import canvasses.DisplayParser;
import circlePack.PackControl;
import dcel.HalfEdge;
import dcel.RawDCEL;
import dcel.Vertex;
import ftnTheory.GenModBranching;
import listManip.FaceLink;
import listManip.HalfLink;
import math.Mobius;

/**
 * This is a traditional branch point, extra angle assigned to an interior circle.
 * It is defined in this convoluted way to make processing fit the mold of more
 * general branch types.
 * 
 * Changes made to 'packData':
 *   * add to 'poisonEdges' via EdgeLink 'parentPoison'.
 *   * Aim of 'myIndex' set to -1 (so parent doesn't repack)
 *   * send radius of 'myIndex' to parent after repack.
 *    
 * @author kstephe2
 *
 */
public class TradBrModPt extends GenBrModPt {

//	int myIndex;      // vertex we are branching (parent's index, 1 locally)
	
	// Constructor
	public TradBrModPt(GenModBranching g,int bID,double aim,int v) {
		super(g,bID,(FaceLink)null,aim);
		gmb=g;
		myType=GenBranchPt.TRADITIONAL;
		myIndex=v;
		
		// debug help
		System.out.println("traditional branch attempt: a = "+aim/Math.PI+"; v = "+v);
		
		modifyPackData();
	}

	// modify 'packData'
	public int modifyPackData() {
		eventHorizon=pdc.vertices[myIndex].getOuterEdges();
		myHoloBorder=RawDCEL.leftsideLink(pdc,eventHorizon);
		myExclusions=new ArrayList<Vertex>();
		Iterator<HalfEdge> vis=eventHorizon.iterator();
		HalfEdge starthe=null;
		while (vis.hasNext()) {
			HalfEdge he=vis.next();
			if (starthe==null && he.isBdry())
				starthe=he;
			myExclusions.add(he.origin);
		}
		myExclusions.add(pdc.vertices[myIndex]);
		layoutAddons=new HalfLink();
		layoutAddons.add(starthe);
		
		return p.nodeCount;
	}
	
	/**
	 * Noting to do for a traditional branch point
	 */
	public void dismantle() {
	}

	/**
	 * Assume radii have been updated, what is the angle sum error?
	 * @return double, l^2 angle sum error.
	 */
	public double currentError() {
		return (p.getCurv(myIndex)-p.getAim(myIndex));
	}
	
	/**
	 * No parameters to set for this branch type.
	 * @return 1
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		return 1;
	}
	
	 /**
	  * See if there are special actions for display on screen of parent packing.
	  * If so, do them, remove them, and pass the rest to 'super'. May flush some
	  * commands designed for other types of branch points.
	  * 
	  * @param flagSegs flag sequences
	  * @return int count of display actions
	  */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		Vector<String> items=new Vector<String>(2);
		int n=0;
		int fs=flagSegs.size();
		for (int j=fs-1;j>=0;j--) {
			items=flagSegs.get(j);
			String str=items.get(0);

			// flush options designed for other types of branch points
			if (str.startsWith("-h") || str.startsWith("-y") || 
					str.startsWith("-j") || str.startsWith("-s")) { 
			}

			flagSegs.remove((Object)items);
		} // end of while

		// pass rest of display commands to 'super'
		n+=DisplayParser.dispParse(p,p.cpScreen,flagSegs);
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(p,false); 
		return n;	
	}
	
	/**
	 * Return string with aim at v
	 * @return String
	 */
	public String getParameters() {
		return new String("Traditional branch point, aim "+
				p.getAim(myIndex)/Math.PI+"*Pi at vertex "+myIndex);
	}
	
	
	public String reportExistence() {
		return new String("Started 'traditional' branch point; center = "+myIndex);
	}
	
	public String reportStatus() {
		return new String("'traditional', ID "+branchID+": vert="+myIndex+
				", aim="+myAim+", holonomy err="+
				Mobius.frobeniusNorm(getLocalHolonomy()));

	}

	/**
	 * Set 'plotFlag' for petals of vert 1, get positions from parent,
	 * then compute and set the center of vertex 1 locally and for 'myIndex' 
	 * in parent.
	 * @return 0 on error
	 */
	public int placeMyCircles() {
		return 0;
	}
	
}
