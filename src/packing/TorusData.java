package packing;

import java.util.ArrayList;
import java.util.Iterator;

import combinatorics.komplex.RedEdge;
import complex.Complex;
import dcel.SideData;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import input.CommandStrParser;
import math.Mobius;

/** 
 * Routines for computing and storing data on a packing
 * that is a 1-torus. We assume that the radii have been
 * computed. On creation of a TorusData object,
 * the torus will be adjusted to standard normalization 
 * with two side-pairings which have been updated.
 * 
 * For flat tori, first corner is at origin, next is at 1,
 * and side-pairings are translations z -> z+a, z -> z+b.
 * 
 * For affine tori, common fixed point of the side-pairing 
 * maps is at the origin and the first corner is at 1. Thus
 * the side-pairing maps are z -> az and z -> bz.

 * There are also static routines for working with tori.
 */
public class TorusData {
	PackData packData;
	public boolean flat;   // yes for regular torus, no for affine 
	public int cornerVert; // corner vertex
	public ArrayList<Complex> cornerPts; // cclw corners, indexed from 1.
	public Complex x_ratio; // cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
	public Complex mean;  // average of four corners --- for display use
	public Complex teich;  // Teichmuller parameter
	public Complex tau;    // conformal modulus
	public Complex affCoeff; // affine coefficient, 0.0 for flat
	
	// constructor
	public TorusData(PackData p) {
		packData=p;
		if (!TorusData.isTopTorus(packData))
			throw new DataException("packing is not a torus");
		
		// arrange two side-pairings, if necessary
		if (p.packDCEL.pairLink==null || p.packDCEL.pairLink.size()!=5) {
			CommandStrParser.jexecute(p,"newRed -t");
			CommandStrParser.jexecute(p,"layout");
			if (p.packDCEL.updatePairMob()==0)
				throw new CombException("failed to update side-pairings");
		}		
		
		// flat?
		flat=true;
		if (TorusData.isAffineTorus(packData))
			flat=false;
		
		// find normalizing Mobius
		Mobius mob=normalizeTorus(packData);
		
		// Apply the Mobius transformation, update
		Mobius.mobiusDirect(p,mob);
		p.packDCEL.updatePairMob();
		
		cornerPts=TorusData.getTorusCorners(packData);
		cornerVert=p.packDCEL.pairLink.get(1).startEdge.
				myEdge.origin.vertIndx;
		
		// cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
		x_ratio=cornerPts.get(1).minus(cornerPts.get(2)).
				times(cornerPts.get(3).minus(cornerPts.get(4))).
				divide(cornerPts.get(1).minus(cornerPts.get(4)).
				times(cornerPts.get(3).minus(cornerPts.get(2))));
		
		// for viewing purposes
		mean=new Complex(0.0);
		for (int i=1;i<5;i++) {
			mean.add(cornerPts.get(i).times(.25));
		}
		
		// set tiech and tau
		if (flat) {
			affCoeff=new Complex(0.0);
			teich=cornerPts.get(4);
			tau=TorusData.Teich2Tau(teich);
		}

		// else, affine: compute Teichmuller parameter 'T'
		//   Sass affCoeff parameter c. Corners of affine 
		//   fundamental domain are <1, exp(c), exp(cT+c), exp(T)>, 
		//   which is image of parallelogram <0,1,T+1,T> under 
		//   z -> exp(cz). Multi-valued nature of logs requires
		//   finding change in argument along edges.
		else {
			double a;   // log(|z2|)
			double b;   // argument change on [z1 z2]
			double M;   // log(|z4|)
			double N;   // argument change on [z1,z4]

			a=Math.log(cornerPts.get(1).abs());
			b=0.0; // arg change along [z1,z2] edge
			SideData sd=p.packDCEL.pairLink.get(1);
			RedEdge rtrace=sd.startEdge;
			do {
				b += rtrace.nextRed.getCenter().
					divide(rtrace.getCenter()).arg();
				rtrace=rtrace.nextRed;
			} while (rtrace!=sd.endEdge.nextRed);
		
			M=Math.log(cornerPts.get(3).abs());
			N=0.0;  // arg change along [z1,z4] edge 
			sd=p.packDCEL.pairLink.get(4);
			rtrace=sd.startEdge;
			do {
				N -= rtrace.nextRed.getCenter().
					divide(rtrace.getCenter()).arg();
				rtrace=rtrace.nextRed;
			} while (rtrace!=sd.endEdge.nextRed);

			affCoeff=new Complex(a,b);
			teich=new Complex(M,N).
				divide(affCoeff);
			tau=TorusData.Teich2Tau(teich);
		}
	}
	
	/**
	 * Compute the Mobius transformation putting this torus 
	 * in standard position. This may change the packing data
	 * by new layout, if necessary.
	 * @param p PackData
	 * @return Mobius
	 */
	public static Mobius normalizeTorus(PackData p) {
		
		if (!TorusData.isTopTorus(p))
			throw new ParserException("packing is not a torus");

		// arrange two-side pairings only
		if (p.packDCEL.pairLink==null || p.packDCEL.pairLink.size()!=5) {
			CommandStrParser.jexecute(p,"newRed -t");
			CommandStrParser.jexecute(p,"layout");
			if (p.packDCEL.updatePairMob()==0)
				throw new CombException("failed to update side-pairings");
		}
		  
		// find the 4 locations for the corner vertex
		Iterator<SideData> pdpl=
				p.packDCEL.pairLink.iterator();
		SideData epair=null;
		epair=pdpl.next(); // first slot is empty
		Complex[] Z=new Complex[4];
		int j=0;
		while(pdpl.hasNext()) {
			epair=pdpl.next();
			Z[j]=epair.startEdge.getCenter();
			j++;
		}

		Mobius mob=new Mobius();
		if (!isAffineTorus(p)) {
			mob.b=Z[0].times(-1.0);
			mob.d=Z[1].minus(Z[0]);
			return mob;
		}
		// affine case
		// In affine case, side-pairings are linear:
		//    z0 -> z3 and z1 -> z2
		//    z0 -> z1 and z3 -> z2
		// The have a common finite fixed point.
		// where 'fixed' is common fixed point, 
		Complex numtor=Z[0].times(Z[2]).minus(Z[1].times(Z[3]));
		Complex denom=Z[0].add(Z[2]).minus(Z[1]).minus(Z[3]);
		Complex fixed=numtor.minus(denom);
		// So so mob(z)=az+b where b=fixed/(fixed-Z[0]) and a= 1/(Z[0]-fixed)
		mob.a=new Complex(1.0).divide(Z[0].minus(fixed));
		mob.b=fixed.divide(fixed.minus(Z[0]));
		return mob;
	}
	
	/**
	 * Is this packing a topological torus?
	 * @param p PackData
	 * @return boolean
	 */
	public static boolean isTopTorus(PackData p) {
		if (p.genus==1 && p.getBdryCompCount()==0)
			return true;
		return false;
	}

	/**
	 * Is this an affine torus, based on comparing
	 * 'radii' for opposite 'redEdge's. (A regular
	 * torus would have the same radius for a red vertex
	 * in all its locations.) 
	 * @param p PackData
	 * @return boolean
	 */
	public static boolean isAffineTorus(PackData p) {
		
		if (!isTopTorus(p))
			throw new CombException("This is not a torus");
		
		// No side pairings?
		if (p.packDCEL.pairLink==null)
			throw new CombException("Not a torus or side-pairings are not set");
		  
		// check number of sides
		int N=p.packDCEL.pairLink.size()-1;
		if (N!=4 && N!=6) 
			throw new CombException("Incorrect number of side-pairings");
		
		// compare radii of opposite vertex on each sidepair 
		double radcomp=1.0;
		Iterator<SideData> plink=p.packDCEL.pairLink.iterator();
		plink.next(); // first is empty
		while (plink.hasNext()) {
			SideData ep=(SideData)plink.next();
			double rad=ep.endEdge.getRadius();
			SideData oppep=p.packDCEL.pairLink.get(ep.mateIndex);
			double ratio=rad/oppep.startEdge.nextRed.getRadius();
			radcomp=(ratio>radcomp) ? ratio : radcomp; 
		}
		
		// if significant difference in some pair of 
		//    opposite radii, then must be affine.
		if (radcomp>1.00001)
			return true;
			
		return false;
	}

	/**
	 * Get array of corner locations in cclw order, indexed from 1.
	 * @param p PackData
	 * @return ArrayList<Complex>, first entry null
	 */
	public static ArrayList<Complex> getTorusCorners(PackData p) {
		if (p.packDCEL.pairLink==null || !TorusData.isTopTorus(p))
			throw new DataException("packing not a torus or side-pairs missing");
		
		ArrayList<Complex> corners=new ArrayList<Complex>(0);
		corners.add(null); // first entry is null
		Iterator<SideData> pdpl=p.packDCEL.pairLink.iterator();
		SideData epair=null;
		epair=pdpl.next(); // first slot is empty
		while(pdpl.hasNext()) {
			epair=pdpl.next();
			corners.add(epair.startEdge.getCenter());
		}
		return corners;
	}
	
	/** 
	 * Given a complex 'teich' Teichmuller parameter in the
	 * upper half plane, return its representative point 'tau'
	 * in moduli space (the set z, Im(z)>0 so that Re(z) in 
	 * (-1/2,1/2] and |z| \ge 1, z.y>0)
	 * Use modular group actions PSL2Z.
	 * @param teich Complex; should have Im(teich)>0
	 * @return Complex tau, null on error
	 */
	public static Complex Teich2Tau(Complex teich) {
		if (teich.y<=0.0) 
			return null;
		Complex w=new Complex(teich);
		w.x=w.x-Math.floor(w.x)-
		    Math.floor(2*(w.x-Math.floor(w.x)));
		int count=0;
		Complex m_one=new Complex(-1.0);
		while (count<1000 && (w.y<Math.sqrt(1.0-w.x*w.x))) {
			count++;
			w=m_one.divide(w);
			w.x=w.x-Math.floor(w.x)-
			Math.floor(2*(w.x-Math.floor(w.x)));
		}
		if (count==1000) { // error
			return null;
		}
		return w;
	}

}