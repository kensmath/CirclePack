package komplex;

import java.util.Vector;

import complex.Complex;
import deBugging.LayoutBugs;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import geometry.CommonMath;
import packing.PackData;

/**
 * An 'AmbiguousZ' has vectors that hold multiple possible 
 * radii/centers for a given vertex as may be laid out 
 * via the redchain; e.g., in multi-connected settings. 
 * (Even radii may be ambiguous, as in projective packings like 
 * affine tori.) Generally, the user will call 'getAmbiguousZs' 
 * to get an array of all ambiguities, and then use only those 
 * that are encountered and have non-null 'AmbiguousZ'.
 * @author kstephe2, Summer 2017

 */
public class AmbiguousZ {

	// vectors (in parallel) of possible radii and centers (in whatever geometry)
	public Vector<Double> radii; 
	public Vector<Complex> centers;

	// constructor
	public AmbiguousZ(double r, Complex initCent) { 
		// first entries are generally the normal stored values
		centers = new Vector<Complex>(0);
		centers.add(initCent);
		radii = new Vector<Double>(0);
		radii.add(Double.valueOf(r));
	}

	/**
	 * Fill an array of ambiguous radi/centers for packing p based on its 'RedEdges'. 
	 * We assume the user has created the standard layout, so the centers can 
	 * be found by going around the red edge list. An array entry is 'null' if 
	 * there is no ambiguity for that vertex. When p is simply connected return null.
	 * 
	 * @param p PackData
	 * @return []AmbiguousZ, null if p simply connected or on error
	 */
	public static AmbiguousZ[] getAmbiguousZs(PackData p) {

		boolean debug=false;
		
		// error checking
		if (p == null || p.redChain == null || p.isSimplyConnected()) {
			return null;
		}

		AmbiguousZ[] ambigZs = new AmbiguousZ[p.nodeCount + 1];
		RedEdge rtrace, hold;
		rtrace = hold = p.firstRedEdge;
		if (hold == null) // no/defective redchain
			return null;

// debug		
		if (debug) { // debug=true;
			LayoutBugs.pRedEdges(p);
		}
		
		// get data for the vertex of the first 'RedEdge'
		int f = rtrace.face;
		
// debug
		if (debug) // debug=true; 
			System.out.println(f);
		
		int v = p.faces[f].vert[rtrace.vIndex];
		ambigZs[v] = new AmbiguousZ(rtrace.rad, rtrace.center);

		// now get the rest
		rtrace = rtrace.nextRed;
		while (rtrace != hold) {
			v = p.faces[rtrace.face].vert[rtrace.vIndex];
			if (ambigZs[v] == null) {
				ambigZs[v] = new AmbiguousZ(rtrace.rad, rtrace.center);
			} else {
				ambigZs[v].radii.add(rtrace.rad);
				ambigZs[v].centers.add(rtrace.center);
			}
			
			// check for blue
//			if (rtrace.face==rtrace.nextRed.face) {
				// vertex is cclw from 'v' in this face, but center stored in 'prev.center'
//				int vv=p.faces[rtrace.face].vert[(rtrace.vIndex+1)%3];
//				Complex zz=new Complex(rtrace.prev.center);
				
//				if (ambigZs[vv] == null) {
//					ambigZs[vv] = new AmbiguousZ(p.rData[vv].rad, zz);
//				} else {
//					ambigZs[vv].centers.add(zz);
//				}
//			}
			
			rtrace = rtrace.nextRed;
		} // end of while

		return ambigZs;
	}
	
	/**
	 * Given another circle (c,r) and invDistance, find among this rad/cents
	 * the best match with correct distance. Note: it is conceivable that the
	 * result could still be ambiguous, but that should be unlikely. The 
	 * relative error (min dist/truedist) is returned as 'CircleSimple.rad'.
	 * @param z Complex
	 * @param r double
	 * @param invD double
	 * @param hes int, geometry
	 * @return CircleSimple, null on error
	 */
	public CircleSimple theOne(Complex z, double r, double invD, int hes) {
		int best = -1;
		double truedist=1.0; // needs to be initialized, will be changed if used
		int num = centers.size();
		Complex cent=null;
		double rad;
		double thislength = 2*r; // sum of two radii
		double newerr;
		double minerr=100.0 * r;;
		for (int j = 0; j < num; j++) {
			cent = centers.get(j);
			rad = (double)radii.get(j);
			thislength=rad+r;
			truedist=CommonMath.get_pt_dist(z, cent,hes);
			thislength=CommonMath.ivd_edge_length(rad, r, invD, hes);
			newerr = Math.abs(truedist - thislength);
			if (newerr < minerr) {
				best = j;
				minerr = newerr;
			}
		}

		if (best < 0)
			return null;
		CircleSimple sC=new CircleSimple();
		sC.center=new Complex(centers.get(best));
		sC.rad=minerr/thislength; // store error here so it won't be lost 
		return sC;
	}
}
