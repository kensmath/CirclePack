package rePack;

import java.io.IOException;

import JNI.JNIinit;
import allMains.CPBase;
import exceptions.CombException;
import input.CommandStrParser;
import packing.PackData;

/**
 * Spherical circle packing computations. 
 * 
 * History: As of 4/08, there are still few methods available, and none 
 * intrinsic to spherical geometry. The only problems that can be solved 
 * routinely are maximal packings and a few special branched maximal packings, 
 * and even these are carried out in hyperbolic geometry. The complex is
 * punctured, the is max packed in the unit disc, the packing is projected 
 * to the sphere where the southern hemisphere is included for the missing
 * vertex, then some normalization may be applied (as with 'NSpole').
 * 
 * There are calls here to Orick's method, which computes the maximal
 * packing in the disc via interwoven iteration that gives both radii 
 * and centers. In particular, 'layout' calls must be avoided. Orick's
 * code uses sparse matrices and is implemented in GOpacker and SolverFunction lib
 * If that library is not available, computations default to the former
 * methods.
 * 
 * @author kens
 *
 */
public class SphPacker extends RePacker {
	
	// Constructors
	public SphPacker(PackData pd,int pass_limit) { // pass_limit suggests using Java methods
		super(pd,pass_limit,false);
	}

	public SphPacker(PackData pd,boolean useC) {
		super(pd,CPBase.RIFFLE_COUNT,useC);
	}
	
	public SphPacker(PackData pd) {
		super(pd,CPBase.RIFFLE_COUNT,true);
	}
	
	/**
	 * Abstract methods not yet used here.
	 */
	public int load() {return 1;}
	public int startRiffle() {return 1;}
	public int reStartRiffle(int passNum) {return 1;}
	public int continueRiffle(int passNum) {return 1;}
	public double l2quality(double crit) {return 1;}
	public void reapResults() {};
	
	public void setSparseC(boolean useC) {
		useSparseC=false;
		if (useC) { // requested to use GOpacker routines if possible
			if (p.nodeCount<GOPACK_THRESHOLD) { // for smaller packing, use Java
				useSparseC=false;
				return;
			}
			if (JNIinit.SparseStatus())
				useSparseC=true;
		}
		return;
	}

	/**
	 * Pack using Orick's code in C for maximal packing on sphere.
	 * Result gives both centers and radii of packing, unnormalized.
	 * TODO: get info on north/south pole vertices.
	 * @return int
	 */
	public int maxPackC_Sph() {
		GOpacker goPack=new GOpacker(p);
		goPack.setMode(1); // max pack mode
		goPack.startRiffle();
		if (goPack.setSphBdry()>0) // these sets 3 bdry rad/centers
			goPack.setMode(GOpacker.FIXED_BDRY);
		int cnt=goPack.continueRiffle(30);
		goPack=null; // close the GOpacker

		p.fillcurves();
		return cnt;
	}
	
}
