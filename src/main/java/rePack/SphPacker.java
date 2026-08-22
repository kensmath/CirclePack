package rePack;

import JNI.GOPackException;
import JNI.GOPackNative;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import exceptions.PackingException;
import geometry.CircleSimple;
import geometry.SphericalMath;
import input.CommandStrParser;
import packing.PackData;

/**
 * Spherical circle packing computations. Currently 
 * this is only for maximal packing of a sphere.
 * As of 8/2026, Claude incorporated use of the
 * C++ version of GOPack for large max packings.
 * Normalization puts alph at the north pole and
 * centroid of tangency points at the origin in 3D. 

 * @author kens 1/2021, 3/2023, 8/2026
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
			passLimit=pass_limit; 
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
	public void reapResults() {}
	
	/**
	 * choose to puncture; if punc_vert is not 
	 * zero, then use it, else try max index, 
	 * but if degree is less than 6, then look the
	 * petal vert with largest degree.
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
	 * For large packings, call C++ version of GOPack.
	 * @param pass_limit, int: does nothing for GOPack
	 * @return 1 on success
	 * @throws PackingException
	 */
	public int maxPack(int pass_limit) throws PackingException {
		passLimit=pass_limit; // passLimit=5000;

		// For large packings, hand the whole complex to
		// the C++ version of GOPack:
		if (p.nodeCount>SPH_GOPACK_THRESHOLD && CPBase.gopackAvailable()) {
			try {
				int[][] bouquet=p.getBouquet();
				double[][] result=GOPackNative.computeMaximalPackingFromComplex(
						p.nodeCount,bouquet,1,0.0,passLimit); // geometry=1: spherical
				double[] radii=result[0];
				double[] centerX=result[1];
				double[] centerY=result[2];

				// GOPack returns its internal eucl data,
				// which is converted to spherical form,
				// already normalized.
				for (int v=1;v<=p.nodeCount;v++) {
					CircleSimple sc=SphericalMath.e_to_s_data(
							new Complex(centerX[v],centerY[v]),radii[v]);
					p.setCenter(v,new Complex(sc.center));
					p.setRadius(v,sc.rad);
				}
				p.setGeometry(1);
				return 1;
			} catch (GOPackException gpe) {
				// The gopack call failed, drop through to java
				// version.
				System.err.println("GOPack spherical max-pack failed, "+
						"falling back to Java routine: "+gpe.getMessage());
			}
		}

/* NOTE: I tried puncturing a face; it works, but layout is
 * not good. Revert to puncturing a vertex
*/
		int ok=1;
		int farvert=p.packDCEL.layoutOrder.getLast().origin.vertIndx;

		if (farvert!=p.nodeCount) {
			if (p.packDCEL.swapNodes(farvert,p.nodeCount)==0)
				return 0;
			swap=true;
		}
		ok *=p.puncture_vert(p.nodeCount);
		if (ok==0)
			return 0;
		p.geom_to_h();
		p.setGeometry(-1);
	  
		// must set radii before creating h_packer
		CommandStrParser.jexecute(p,"set_rad .01 a");
		CommandStrParser.jexecute(p,"set_rad 9.0 b");
		HypPacker h_packer=new HypPacker(p,-1);
		ok *=h_packer.maxPack(passLimit);
		
		if (ok==0) {
			System.err.println("ok is 0");
			return 0;
		}
		int act=p.geom_to_s();
		p.setGeometry(1);
		if (act==0)
			System.err.println("geom_to_s failed");
		act=CommandStrParser.jexecute(p,"add_ideal");
		if (act==0)
			System.err.println("add_ideal failed");
		CommandStrParser.jexecute(p,"copy 2");		

		p.setCenter(p.nodeCount,new Complex(0.0));
		p.setRadius(p.nodeCount,CPBase.piby2);
		if (swap && p.packDCEL.swapNodes(punc_vert,p.nodeCount)==0)
			CirclePack.cpb.errMsg("Opps, failed to swap vertices back");
		return ok;
	}

}
