package schwarzWork;

import java.awt.Color;
import java.util.ArrayList;

import allMains.CPBase;
import combinatorics.komplex.HalfEdge;
import util.ColorUtil;

/**
 * Data for 'HalfEdge's subject to adjustment
 * when circle packing via intrinsic schwarzians.
 * Computing a new schwarzian involves the N-3
 * previous edge schwarzians, so we want that to
 * be easy to compute.
 * 
 * These objects are instantiate when 'SchwarzPack'
 * is initiated and put in an array, indexed starting
 * at 1. See 'theEdges' for array index. 
 * 
 */

public class SchEdgeData {
	final static double sqrt3=Math.sqrt(3);
	
	HalfEdge myHEdge; // which edge is this?
	int myN; // degree of 'Origin' of 'myHEdge'
	HalfEdge startCCLW; // start of cclw N-3 schwarzians
	HalfEdge startCLW; // start of clw N-3 schwarzians
	
	// compute remaining three schwarzians to form
	//   flower in both cclw and clw direction.
	//  + for cclw, myEdge and next two
	//  + for clw, myEdge and previous two
	double[] newSch_cclw;
	double[] newSch_clw;
	
	// NOTE: if a negative constraint is encountered,
	//   set 'allPosFlag' to false; means wecan't 
	//   complete an unbranched flower. Need to 
	//   work out what to do in branched cases.
	Boolean allPosFlag; // null until computation is 
	// carried out.

	// constructor
	public SchEdgeData(HalfEdge edge) {
		allPosFlag=null;
		myHEdge=edge;
		myN=edge.origin.getNum();
		newSch_cclw=new double[3];
		newSch_clw=new double[3];
		
		// 'startEdge' is N-3 clockwise edges away,
		//    so move 3 cclw.
		startCCLW=edge;
		for (int j=1;j<=3;j++)
			startCCLW=startCCLW.prev.twin;
		startCLW=edge;
		for (int j=1;j<=3;j++)
			startCLW=startCLW.twin.next;
	}
	
	/**
	 * Find the schwarzians that will complete the
	 * flower for this edge. 
	 * @return false if a negative constraint, either
	 *    cclw or clw.
	 */
	public Boolean finishFlower() {
		
		// ditch old results
		newSch_cclw=null;
		newSch_clw=null;
		
		double[] uz=new double[myN+1];

		// get cclw direction first
		HalfEdge he=startCCLW;
		for (int j=1;j<=myN;j++) { 
			uz[j]=1.0-he.getSchwarzian();
			he=he.prev.twin;
		}
		
		newSch_cclw=comp3sch(uz);
		if (newSch_cclw==null) {
			newSch_clw=null;
			return false;
		}

		// get clw direction
		he=startCLW;
		for (int j=1;j<=myN;j++) { 
			uz[j]=1.0-he.getSchwarzian();
			he=he.twin.next;
		}
		
		newSch_clw=comp3sch(uz);
		if (newSch_clw==null) {
			newSch_cclw=null;
			return false;
		}		

		return true;
	}
	
	/**
	 * Given 'uzians' (indexed from 1), compute the
	 * next three schwarzians base on u1,u2,...,u{n-3}
	 * @param uzians double[], indexed from 1
	 * @return double[3], null if constraint is <= 0.
	 */
	public double[] comp3sch(double[] uzians) {
		double[] newsch=new double[3];
		Boolean pflag=Boolean.valueOf(true);
		
		// use copy of 'uzians'
		double[] uz=new double[myN+1];
		for (int j=1;j<=myN+1;j++)
			uz[j]=uzians[j];
		
		ArrayList<Double> uarray=SchFlowerData.constraints(uz,pflag);
		if (!pflag)
			return null;
		
		// else constraints should remain positive
		double compU=((1.0+uarray.get(myN-3))/
			(sqrt3*uarray.get(myN-2)));
		// put this in the list of uzians
		uz[myN-2]=compU;
		newsch[0]=1.0-compU;
		
		// shift for next uzian
		for (int j=1;j<=myN;j++)
			uz[j-1]=uz[j];
		uz[0]=0.0; // indexed from 1
		uarray=SchFlowerData.constraints(uz,pflag);
		compU=((1.0+uarray.get(myN-3))/
			(sqrt3*uarray.get(myN-2)));
		// put this in the list of uzians
		uz[myN-2]=compU;
		newsch[1]=1.0-compU;

		// shift for last uzian
		for (int j=1;j<=myN;j++)
			uz[j-1]=uz[j];
		uz[0]=0.0; // indexed from 1
		uarray=SchFlowerData.constraints(uz,pflag);
		compU=((1.0+uarray.get(myN-3))/
			(sqrt3*uarray.get(myN-2)));
		// put this in the list
		uz[myN-2]=compU;
		newsch[2]=1.0-compU;
		
		return newsch;
	}
	
	/**
	 * Update errors/color for this edge, 'errorCCLW' 
	 * and 'errorCLW': errors are computed - current. 
	 * Computations based on edges from this 'origin':
	 *   + [0]: previous myN-3 schwarzians and 
	 *   + [1]: subsequent myN-3 schwarzians
	 * Note that we use uzians in the actual computation.
	 * 
	 * @param mode int; will try different approaches
	 * @return Double[], 
	 */
	public void colorEdge(int mode) {
		Boolean allpos=finishFlower();
		if (!allpos || newSch_cclw==null) {
			myHEdge.setColor(Color.yellow);
			return;
		}

		double errorCCLW=newSch_cclw[0]-myHEdge.getSchwarzian();
		double errorCLW=newSch_clw[0]-myHEdge.getSchwarzian();
		
		double scherr=(errorCCLW+errorCLW)/2.0;
		if (Math.abs(scherr)<.0000001) {
			myHEdge.setColor(CPBase.DEFAULT_CANVAS_BACKGROUND);
			return;
		}
		
		// color green if opposite signs
		if ((errorCCLW>0.0 && errorCLW<0.0) ||
				errorCCLW<0.0 && errorCLW>0.0) {
			double x=scherr/0.05;
			myHEdge.setColor(ColorUtil.green_interp(Math.abs(x)));
			return;
		}
		
		// TODO: we're assuming values in [-.5,.5] 
		double x=scherr/0.5;
		// error is computed-current;
		// blue means schwarzian needs to increase
		if (scherr>0.0) // increase schwarzian
			myHEdge.setColor(ColorUtil.blue_interp(x));
		else // decrease schwarzian
			myHEdge.setColor(ColorUtil.red_interp(-1.0*x));
	
		return;
	}
	
}
