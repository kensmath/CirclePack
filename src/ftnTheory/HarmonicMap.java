package ftnTheory;

import packing.PackData;
import packing.RData;
import allMains.CirclePack;

/** 
 * Routines for experimenting with harmonic mappings (and 
 * analogous analytic functions 6/10). These are highly 
 * structured at this time (7/2007), requiring two base 
 * eucl packings in p0 and p1 with identical combinatorics
 * and results are put in p2. 
 *
 * Theory: If h and g are analytic then f = h + conj(g) is harmonic. 
 * If |h'| never vanishes and |h'|>|g'|, then f is orientation preserving. 
 * Certain other conditions will imply that f is one-to-one (which is 
 * generally intended when using the term "harmonic mapping" in
 * function theory). A good set of examples is h = identity and
 * g = (1/n)(conj(z))^n on the unit disc. (See Duren's book.)
 * Discrete version would have boundary radii of H are greater
 * than those of G and H is locally univalent. 

This routine computes F = H + conj(G) (i.e., computes the resulting
complex number associated with each vertex), copies H into p2
and sets its centers based on H + conj(G).

The calling routine may have to handle various other things:
* check that Hp and Gp are euclidean, have same nodecount.
* check that Hp is locally univalent.
* check that bdry radii of Hp are larger than those of Gp
* send status to user.

May put other options in later, such as rotating and/or translating
H and/or G.

*/
public class HarmonicMap {
	
	PackData H_pack;
	PackData G_pack;
	PackData F_pack;

	/**
	 * Given packings G and H (euclidean, same nodeCount), return 
	 * a RData vector reflecting harmonic function F = H + conj(G). 
	 * Return null on error.
	 * @param Hp, PackData
	 * @param Gp, PackData
	 * @return RDate, null on error
	 */
	public static RData []h_g_bar(PackData Hp,PackData Gp) {
		if (ck_size(Hp,Gp)==0) return null;
		RData []rdata=new RData[Hp.nodeCount+1];
		for (int j=1;j<=Hp.nodeCount;j++) {
			rdata[j]=Hp.rData[j].clone();
			rdata[j].center=Hp.rData[j].center.add(Gp.rData[j].center.conj());
		}
		return rdata;
	}
	
	/**
	 * Parallel to 'h_g_bar'
	 * Given packings G and H (euclidean, same nodeCount), 
	 * return a RData vector with centers reflecting the "sum" 
	 * function F = H + G. Also, set radii as sum of H and G.
	 * Return null on error.
	 * @param Hp, PackData
	 * @param Gp, PackData
	 * @return RData, null on error
	 */
	public static RData []h_g_add(PackData Hp,PackData Gp) {
		if (ck_size(Hp,Gp)==0) return null;
		RData []rdata=new RData[Hp.nodeCount+1];
		for (int j=1;j<=Hp.nodeCount;j++) {
			rdata[j]=Hp.rData[j].clone();
			rdata[j].center=Hp.rData[j].center.add(Gp.rData[j].center);
			rdata[j].rad=Hp.rData[j].rad+Gp.rData[j].rad;
		}
		return rdata;
	} 

	/**
	 * Check that Hp and Gp are euclidean and have the same nodeCount (assumed to
	 * imply the same combinatorics).
	 * @param Hp
	 * @param Gp
	 * @return 1 if okay, 0 on error
	 */
	public static int ck_size(PackData Hp,PackData Gp) {
		if (!Hp.status || !Gp.status) {
			CirclePack.cpb.myErrorMsg("One of p"+Hp.packNum+
					" or p"+Gp.packNum+" is not loaded.");
			return 0;
		}
		if (Hp.hes!=0 || Gp.hes!=0) {
			CirclePack.cpb.myErrorMsg("Packings p"+Hp.packNum+
					" and p"+Gp.packNum+" are not both euclidean.");
			return 0;
		}
		if (Hp.nodeCount != Gp.nodeCount) {
			CirclePack.cpb.myErrorMsg("Packings p"+Hp.packNum+
					" and p"+Gp.packNum+" do not have the same nodeCount.");
			return 0;
		}
		return 1;
	}
}
