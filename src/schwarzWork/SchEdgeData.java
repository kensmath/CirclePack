package schwarzWork;

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
	
	// latest errors: computed schwarzian - current schwarzian
	double errorCCLW; 
	double errorCLW;

	// constructor
	public SchEdgeData(HalfEdge edge) {
		myHEdge=edge;
		myN=edge.origin.getNum();
		
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
	 * Update 'errorCCLW' and 'errorCLW': errors are
	 * computed - current. The computed are based on
	 * edges from this 'origin':
	 *   + [0]: previous myN-3 schwarzians and 
	 *   + [1]: subsequent myN-3 schwarzians
	 * Note that we use uzians in the actual computation.
	 * @return Double[], 
	 */
	public void setEdgeErrors() {
		double[] uz=new double[myN+1]; // indexed from 1
		
		// get cclw direction first
		HalfEdge he=startCCLW;
		for (int j=1;j<=myN;j++) { 
			uz[j]=1.0-he.getSchwarzian();
			he=he.prev.twin;
		}
		ArrayList<Double> uarray=SchFlowerData.constraints(uz);
		double compU=((1.0+uarray.get(myN-3))/
				(sqrt3*uarray.get(myN-2)));
		errorCCLW=1.0-compU-he.getSchwarzian();
		
		// get clw direction next
		he=startCLW;
		for (int j=1;j<=myN;j++) { 
			uz[j]=1.0-he.getSchwarzian();
			he=he.twin.next;
		}
		uarray=SchFlowerData.constraints(uz);
		compU=((1.0+uarray.get(myN-3))/
				(sqrt3*uarray.get(myN-2)));
		errorCLW=1.0-compU-he.getSchwarzian();
	}
	
	public void colorEdge() {
		setEdgeErrors();
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
		
		// TODO: we're assuming values in [-.1,.1] 
		double x=scherr/0.1;
		// error is computed-current;
		// blue means schwarzian needs to increase
		if (scherr>0.0) // increase schwarzian
			myHEdge.setColor(ColorUtil.blue_interp(x));
		else // decrease schwarzian
			myHEdge.setColor(ColorUtil.red_interp(-1.0*x));
	
		return;
	}
	
}
