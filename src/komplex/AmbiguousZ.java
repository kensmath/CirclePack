package komplex;

import java.util.Vector;

import complex.Complex;
import deBugging.LayoutBugs;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import packing.PackData;

/**
 * Structure to hold and manipulate multiple possible radii/centers 
 * for vertices laid out via the redchain. (radii often in common, but
 * may differ, e.g., in projective packings like affine tori.)
 * Generally, the user will call 'getAmbiguousZs' to get an array of all
 * ambiguities, using only those that are encountered and have non-null
 * 'AmbiguousZ'.
 * @author kstephe2, Summer 2017

 */
public class AmbiguousZ {

	public Vector<Double> radii; 
	public Vector<Complex> centers; // optional centers (in whatever geometry)

	// constructor
	public AmbiguousZ(double r, Complex initCent) { // note: "real" center is
													// first
		centers = new Vector<Complex>(0);
		centers.add(initCent);
		radii = new Vector<Double>(0);
		radii.add(Double.valueOf(r));
	}

	/**
	 * Fill an array of ambiguous centers for packing p based on its 'RedEdges'. 
	 * We assume the user has created the standard layout, so the centers can 
	 * be found by going around the red edge list. An array entry is 'null' if 
	 * there is no ambiguity for that vertex. When p is simply connected return null.
	 * 
	 * @param p PackData
	 * @return []AmbiguousZ, null if p simply connected or on error
	 */
	public static AmbiguousZ []getAmbiguousZs(PackData p) {

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
	 * Given another circle (c,r) and invDistance, find which of these centers
	 * best matches the correct distance. Note: it is conceivable that the
	 * result could still be ambiguous, but that should be unlikely. The 
	 * relative error (min dist/truedist) is returned as 'SimpleCircle.rad'.
	 * @param z Complex
	 * @param r double
	 * @param invD double
	 * @param hes int, geometry
	 * @return SimpleCircle, null on error
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
			if (hes == 0) { // eucl
				truedist = z.minus(cent).abs();
				thislength = EuclMath.e_invdist_length(rad, r, invD);
			} else if (hes < 0) { // hyp
				truedist = HyperbolicMath.h_dist(z, cent);
				thislength = HyperbolicMath.h_invdist_length(rad, r, invD);
			} else // sph case not yet done
				return null;
			
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
