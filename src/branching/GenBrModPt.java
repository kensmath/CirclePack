package branching;

import java.util.ArrayList;
import java.util.Vector;

import dcel.PackDCEL;
import dcel.Vertex;
import ftnTheory.GenModBranching;
import listManip.FaceLink;
import listManip.HalfLink;
import math.Mobius;
import packing.PackData;

/**
 * Revised (9/21) parent class for various types of "generalized 
 * branch points". Within a complex, a generalized branch point 
 * is a simply connected subcomplex on whose interior circles we 
 * impose some type of local behavior having a global effect like 
 * that of a classical branch point. The "eventHorizon" of the 
 * subcomplex is the oriented chain of surrounding edges, typically
 * closed. The structural changes in the branch point are now
 * imposed on the parent so that the parent can be repacked and
 * laid out with the branch point adjustments included. (This is
 * a major change from the original approach.)  
 * 
 * The goal of local machinations inside the branch point is
 * to make the holonomy on the eventHorizon vanish. This may 
 * involve various adjustments to combinatorics, overlaps, etc.,
 * depending on the type of the branch point. 
 * 
 * For example:
 * 
 * (1) For a "traditional" discrete branch point v, the 
 * subcomplex is just its flower and an angle sum condition is 
 * placed at the center v, e.g. aim = 4 pi. 
 * 
 * (2) For "singular" and "fractured" branching the subcomplex 
 * consists of a face {v,u,w}, so the eventHorizon is the 
 * link of face edges. The internal structure involves 
 * distributing an extra 2 pi angle sum among v, u, and w 
 * and/or distributing overlaps on the edges of the face.
 * 
 * (3) A "quad" branch point involves fracturing two 
 * contiguous faces.
 * 
 * (4) For "chaperone" branching at v, the subcomplex is 
 * the flower augmented with a 'sister' to v and two
 * chaperones and their overlaps.
 * 
 * In all cases there is basic functionality that has 
 * to be defined and implemented: get/set control 
 * parameters, local layout addons, holonomy calculations,
 * display actions, etc.
 * 
 * For now, all local branch computations will be eucl or hyp and
 * layouts will be in normalized positions.
 * 
 * @author kstephe2
 *
 */
public abstract class GenBrModPt {

	static final double RIFF_TOLER=.00000001;
	static final double pi2=Math.PI*2.0;
	
	// branch type
	public static final int TRADITIONAL=1;
	public static final int FRACTURED=2;
	public static final int QUADFACE=3;
	public static final int SINGULAR=4; // singular face, using chaperones
	public static final int SHIFTED=5;  // shifted, using ratio/phase parameters
	public static final int CHAPERONE=6; // shifted, using chaperone circles
	
	public GenModBranching gmb;  // parent extender

	public int myType;	  // type (see above)
	public int branchID;  // id number, starting from 0 as created by parent
	public int myIndex;	  // may be face index or vert index, depending on type
	double myAim;		  // target angle sum; e.g. 4*pi.
	PackData p;    		  // who's your daddy? (held by GenModBranching extender)
	public HalfLink layoutAddons;  // adjoined to parent layout for this branch
	public HalfLink myHoloBorder;  // for local holonomy 
	Mobius myHolonomy;    // Can update this after each layout

	// TODO: converting to dcel structure, build temporary parallel data 
	public HalfLink eventHorizon;  // surrounding the branch region
	public ArrayList<Vertex> myExclusions; // vertices on/inside 'eventHorizon'

	PackDCEL pdc; // convenience
	
	// Constructor
	public GenBrModPt(GenModBranching gmb,int bID,FaceLink blink,double aim) {
		p=gmb.packData;
		pdc=p.packDCEL; 
		branchID=bID;
		myAim=aim;
		myHolonomy=null;
		layoutAddons=null;
		myHoloBorder=null;
	}
	
	// ************** abstract methods ******************
	
	// create the local packing based on type of branch point
	abstract int modifyPackData();
	
	// prepare to delete by resetting 'packData'
	abstract public void dismantle();
	
	// current
	abstract public double currentError();
	
	// report existence and main data
	abstract public String reportExistence();
	
	// report status: id, vert/face, holonomy error, etc.
	abstract public String reportStatus();

	// report parameters in String
	abstract public String getParameters();

	// set parameters (list depends on branch type)
	abstract public int setParameters(Vector<Vector<String>> flagSegs);
	
	// ************************************************

	/**
	 * Return latest local holonomy; usually updated after layout
	 * when the first edge is in place. This does not affect the
	 * current centers.
	 * @return Mobius, null on error
	 */
	public Mobius getLocalHolonomy() {
		if (myHoloBorder==null || myHoloBorder.size()==0)
			return null;
		return PackData.holonomyMobius(p,myHoloBorder);
	}
	
	// each type should process its local display options 
	public int displayMe(Vector<Vector<String>> flagSegs) {
		return 1;
	}

}
