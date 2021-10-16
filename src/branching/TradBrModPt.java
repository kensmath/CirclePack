package branching;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import canvasses.DisplayParser;
import circlePack.PackControl;
import dcel.HalfEdge;
import dcel.RawDCEL;
import dcel.Vertex;
import ftnTheory.GenModBranching;
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
		super(g,bID,aim);
		gmb=g;
		myType=GenBrModPt.TRADITIONAL;
		myEdge=pdc.vertices[v].halfedge;
		
		// debug help
		System.out.println("traditional branch attempt: a = "+aim/Math.PI+"; v = "+v);
		
		modifyPackData();
		
		success=true;
	}

	// modify 'packData'
	public int modifyPackData() {
		eventHorizon=myEdge.origin.getOuterEdges();
		myHoloBorder=RawDCEL.leftsideLink(pdc,eventHorizon);
		myExclusions=new ArrayList<Vertex>();
		Iterator<HalfEdge> vis=eventHorizon.iterator();
		HalfEdge starthe=null;
		while (vis.hasNext()) {
			HalfEdge he=vis.next();
			if (starthe==null && !he.isBdry())
				starthe=he;
			myExclusions.add(he.origin);
		}
		myExclusions.add(myEdge.origin);
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
	 * No parameters to set for this branch type.
	 * @return 1
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		return 1;
	}
	
	 /**
	  * See if there are special actions for display on screen.
	  * I don't think there are any for traditional branch points,
	  * so pass the rest of flags to 'super'. May flush some
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
		int v=myEdge.origin.vertIndx;
		return new String("Traditional branch point, aim "+
				p.getAim(v)/Math.PI+"*Pi at vertex "+v);
	}
	
	
	public String reportExistence() {
		return new String("Started 'traditional' branch point; center = "+
				myEdge.origin.vertIndx);
	}
		
	public String reportStatus() {
		return new String("'traditional', ID "+branchID+": vert="+
				myEdge.origin.vertIndx+
				", aim="+myAim+", holonomy err="+
				Mobius.frobeniusNorm(getLocalHolonomy()));

	}

	/**
	 * Changes in 'packData' may require us to reassert
	 * the data for this branch point. This is also called
	 * when a newly created branch point is first installed.
	 */
	public void renew() {
		int v=myEdge.origin.vertIndx;
		// reset the aim
		p.setAim(v, myAim);
		// set color
		p.setCircleColor(v,new Color(200,0,0)); // red
	}
	
}
