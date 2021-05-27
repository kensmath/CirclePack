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
	 * Generic call: use Java or C depending on what's available and preferred.
	 * @param puncture_v, vertex to puncture if Java is used; if <=0, default to 
	 * antipodal to 'alpha'
	 * @param puncture_v int; if -1, default
	 * @param cycles, repacking cycles if Java is used 
	 * @return
	 */
	public int maxPack(int puncture_v,int cycles) {
		if (!useSparseC)
			return maxPackJ_Sph(puncture_v,cycles);
		return maxPackC_Sph();
	}
	
	/**
	 * Spherical repack in Java (used if packing small or there is
	 * no C library). Caution: if puncture_v<=0, choose puncture antipodal
	 * to alpha. NOTE: This can be poor choice, so try to set v if results
	 * are bad.
	 * @param puncture_v int, if <=0, choose antipodal to alpha
	 * @param cycles int, repack cycles
	 * @return cycle count.
	 */
	public int maxPackJ_Sph(int puncture_v,int cycles) {
		p.hes=1;
		if (puncture_v<=0) puncture_v=p.antipodal_vert(p.getAlpha());
		if (puncture_v<=0) 
			throw new CombException("improper puncturing vertex");
		  
		// save lists to restore later
		holdLists(p);
		  
		  // Method: puncture, max_pack in unit disc, project, then 
		  //   normalize. Note: swapping, so puncture is 'nodeCount', 
		  //   allows indices to be kept in tact.
		  if (puncture_v!=p.nodeCount) {
			  p.swap_nodes(puncture_v,p.nodeCount);
		  }
		  if (p.puncture_vert(p.nodeCount)==0) { 
			  throw new CombException("puncture failed");
		  }

		  // max_pack in the disc 
		  p.geom_to_h();
		  p.setGeometry(-1);
		  p.setCombinatorics();
		  p.set_aim_default();
		  
		  // set bdry radii to 5.0 (preferrable to -.1 which can
		  //   mess up placement of degree 2 bdry circles.
		  try {
			  CommandStrParser.jexecute(p,"set_rad 5.0 b");
		  } catch (Exception ex) {}
		  int count=p.repack_call(cycles);
		  p.fillcurves();
		  try {
			  p.comp_pack_centers(false,false,2,CommandStrParser.LAYOUT_THRESHOLD);
		  } catch (IOException ex) {};
		  
		  // TODO: were should I (or should I?) apply NSpole
		  
		  // TODO: 6/17. Oops, I think these should be commented out in favor
		  //       of the call to proj_max_to_s.
//		  CommandStrParser.jexecute(p,"geom_to_s");
//		  CommandStrParser.jexecute(p,"add_ideal");
		  
	   	  p.proj_max_to_s(0,1.0); // old action (but reliable)
		  if (puncture_v != p.nodeCount) {
			  p.swap_nodes(puncture_v,p.nodeCount);
			  p.setCombinatorics();
		  }
		  restoreLists(p);
		  return count;
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
		if (goPack.setSphBdry()>0) // these sets bdry rad/centers (if there are 3 bdry verts)
			goPack.setMode(GOpacker.FIXED_BDRY);
		int cnt=goPack.continueRiffle(30);
		goPack=null; // close the GOpacker

		p.fillcurves();
		return cnt;
	}
	
}
