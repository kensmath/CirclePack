package rePack;

import allMains.CirclePack;
import exceptions.PackingException;
import input.CommandStrParser;
import packing.PackData;

/**
 * Spherical circle packing computations. Currently this is 
 * only for maximal packing of a sphere. 
 * 
 * As of 3/2023, changing from using euclidean packing to
 * hyperbolic due to layout error with the former: 
 * We "puncture" a vertex, max_pack that in the hyp plane,
 * project to the sphere, project to the sphere, which also
 * normalizes.
 * 
 * @author kens 1/2021 and 3/2023
 *
 */
public class SphPacker extends RePacker {
	
    public static final int SPH_GOPACK_THRESHOLD=501;  // for smaller packs, default to Java
    public static final double MPI2=2.0*Math.PI;
    int punc_vert; 
    boolean swap=false;

	// Constructor
	public SphPacker(PackData pd,int p_vert,int pass_limit) { // pass_limit suggests using Java methods
    	p=pd;
    	pdcel=p.packDCEL;
    	if (pass_limit<0) 
    		passLimit=PASSLIMIT;
		else 
			passLimit=pass_limit; // passLimit=5000;
    	punc_vert=p_vert;
		punc_vert=load(); 
	}

	/**
	 * Abstract methods not yet used here.
	 */
	public int reStartRiffle(int passNum) {return 1;}
	public double l2quality(double crit) {return 1;}
	public int startRiffle() {return 1;}
	public int restartRiffle(int passnum) {return 1;}
	public int continueRiffle(int passNum) {return 1;}

	/**
	 * choose to puncture; if punc_vert is not zero, then
	 * use that, else try max index, but if degree is less
	 * than 6, then look petal vert with largest degree.
	 * @return int, vert index
	 */
	public int load() {
		int pv=punc_vert;
		if (pv<=0 || (pv>0 && p.isBdry(pv))) {
			pv=p.nodeCount;
			int deg=p.packDCEL.vertices[pv].getNum();
			if (deg<6) {
				int[] flower=p.packDCEL.vertices[pv].getFlower(false);
				for (int j=0;j<flower.length;j++) {
					int d=p.packDCEL.vertices[flower[j]].getNum();
					if (d>deg && !p.isBdry(flower[j])) 
						pv=flower[j];
				}
			}
		}
		return pv;
	}
	
	/**
	 * puncture, max_pack in hyp plane, but move to sphere is in
	 * 'reapResults', thus allowing chance to see intermediate stage.
	 * @param pass_limit
	 * @return
	 * @throws PackingException
	 */
	public int maxPack(int pass_limit) throws PackingException {
		passLimit=pass_limit; // passLimit=5000;
		int ok=1;
		if (punc_vert!=p.nodeCount) {
			if (p.packDCEL.swapNodes(punc_vert,p.nodeCount)==0)
				return 0;
			swap=true;
		}
		ok *=p.puncture_vert(p.nodeCount);
		if (ok!=0) {
			ok *=CommandStrParser.jexecute(p,"max_pack "+passLimit);
		}
		if (ok==0)
			return 0;
		return ok;
	}
	
	/**
	 * Here we add the missing vertex back in, project to the sphere
	 * and normalize, and swap if needed
	 */
	public void reapResults() {
		if (CommandStrParser.jexecute(p,"proj")==0) {
			CirclePack.cpb.errMsg("Opps, failed to convert back to sphere)");
			return;
		}
		if (swap && p.packDCEL.swapNodes(punc_vert,p.nodeCount)==0)
			CirclePack.cpb.errMsg("Opps, failed to swap vertices back");
		return;
	}
	
}
