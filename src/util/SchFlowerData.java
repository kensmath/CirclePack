package util;

import exceptions.DataException;

/**
 * Work with intrinsic schwarzians of individual 
 * closed flowers in normalized setting: 
 * Center C is upper half plane, petal c_n is 
 * half-plane {y<= -2}, petal c_1 is tangent to C
 * at at 0 with eucl radius 1, petals c_j tangent 
 * at t[j] for j=1,..,n-1. Data is 'uzian' 
 * variable u, u=1-s where s is intrinsic 
 * schwarzian. Uzians u should be restricted
 * to be positive.
 * 
 * Easy to encounter errors or improper values
 * in computations: e.g., if some u becomes negative.
 * We throw and 'DataException' (and generally
 * catch this for some tailored reaction).
 * 
 * CAUTION: Major difficulties with branching:
 *  + methods (e.g., function f) for finding
 *    the n-2 schwarzians only for un-branche
 *  + direction reverses: one step left, 
 *    then adjust next step to be sure it 
 *    goes right. Displacement can be negative
 *    or zero (if target petal to C at infinity,
 *    i.e., is a half plane.)
 *  + If c_{n-2} is a half plane, then cannot
 *    complete since c_{n-1} is ambiguous.
 *  + for single branching, could overcome this
 *    by finishing using layout in reverse from 
 *    c_{n-1}.
 *  + but this doesn't work if we don't know how
 *    to compute the last schwarzian.
 * 
 * Variables are indexed from 1 so they correspond to
 * normal petal indexing from 1 to n, so argument 
 * 'uzs' in constructor is length N+1. The
 * last 3 uzians in 'uzs' will be recomputed.
 * 
 * 'status'
 * 
 * @author kensm, 3/2024
 *
 */
public class SchFlowerData {
	final static double sqrt3=Math.sqrt(3);
	final static double oosq3=1/sqrt3;

	public int N; // number of faces (degree for interior)
	public double[] uzian; // typically u_1,..,u_{n-3} are data
	public double[] radius; // eucl radii r_j, j=1,..,n-1 (r_1=r_{n-1}=1
	public double[] recipSqRad; // reciprocal square root, possibly neg or 0
	public double[] t; // tangency points, t[1]=0
	public int branchDeg; // 0 unless branched
	int status; // 1 if simple flower, -n if branched of
				// order n (n times around) and 0 on error.
	
	// Constructor
	public SchFlowerData(double[] uzs) {
		status=0;
		N=uzs.length-1;
		uzian=new double[N+1]; // indexed from 1
		radius=new double[N+1]; // indexed from 1
		recipSqRad=new double[N+1]; // indexed from 1
		t=new double[N+1]; // indexed from 1
		for (int j=1;j<=N;j++)
			uzian[j]=uzs[j];
		branchDeg=0;
		for (int j=0;(j<=N-3 && status==0);j++)
			if (uzian[j]<0) 
				status =-j;
		if (status==0)
			status=compute();
	}
	
	/**
	 * The uzians must be set and we do computations 
	 * based on the first n-3 of them, then recompute
	 * the remaining 3, watching for out-of-bounds values
	 * @return int, 1 for simple flower, -n for branching 
	 */
	public int compute() {
		
		// initialize
		int[] hits=new int[N+1]; // mark when computed
		t[1]=0.0;
		radius[1]=radius[N-1]=recipSqRad[1]=recipSqRad[N-1]=1.0;
		hits[1]=1; // c_1 is in place to start
		branchDeg=0; // increment for each branching event
		
		// compute data for c_2
		if (N>3) {
			double[] tdata=Sit2(uzian[1]);
			t[2]=t[1]+tdata[0];
			radius[2]=1/(tdata[1]*tdata[1]);
			recipSqRad[2]=tdata[1];
			hits[2]=1;
		}
		
		// compute as generic up to and including N-2;
		//   watch for dspmt to be negative or 0, which
		//   means there's branching.
		double[] tdata=null;
		double dspmt;
		for (int j=3;j<=(N-2);j++) {
			tdata=Sit3(uzian[j-1],recipSqRad[j-2],recipSqRad[j-1]);
			dspmt=tdata[0];
			recipSqRad[j]=tdata[1];
			
			// if displacement is zero or negative, then
			//    we've reached a branch event and special
			//    procedures apply:
			//    * recipSqRad[j] will be negative or zero
			//    * place this petal, c_{j}; it may be a
			//      a half plane, hence tangency at infinity
			//    * then go on to compute c_{k}, k=j+1: 
			//      the recipSqRad[j] will be negative, so we 
			//      have to negate it to compute c_{k}.
			//    * Then have to increment j before continuing.
			if (Math.abs(dspmt)<.00001) { // essentially <= 0
				branchDeg++;
				// if essentially 0, c(j) is half plane.
				if (dspmt>-.00001) { 

					// c(n-2) a half plane means placing c(n-1) is
					//   either impossible or ambiguous, so we
					//   throw an exception.
					if (j==N-2)
						throw new DataException("Can't complete a flower "+
								"since c_{n-2} is a halfplane");
					t[j]=t[j-1]; 
					radius[j]=100000.0;
					recipSqRad[j]=0.0;
					hits[j]=-1;
					
					// place next petal, too; use modified Situation 1
					int k=j+1;
					double r=1/(recipSqRad[k-2]*recipSqRad[k-2]); // same rad as previous
					t[k]=t[j-1]-2.0*uzian[k-1]*sqrt3*r; // but to left of c(j-1)
					recipSqRad[k]=recipSqRad[k-2];
					hits[k]=1;
				}
				else { // dspmt definitely negative
					t[j]=t[j-1]+dspmt; // tangency moves left
					radius[j]=1/(recipSqRad[j]*recipSqRad[j]);
					
					// place next petal, too; moves right again
					int k=j+1;
					// CAUTION: invSqRad[k-1] will be negative;
					tdata=Sit4(uzian[k-1],recipSqRad[k-2],-1.0*recipSqRad[k-1]);
					t[k]=t[k-1]+tdata[0];
					recipSqRad[k]=tdata[1];
					radius[k]=1/(tdata[1]*tdata[1]);
					hits[k]=1;
				}
				j=j+1; // increment j
			} // done with dspmt <= 0
			
			// normal move to right
			else {  
				t[j]=t[j-1]+dspmt;
				radius[j]=1/(tdata[1]*tdata[1]);
			}
		}

		// now, compute the last three uzians
		if (N==3) { 
			uzian[1]=uzian[2]=uzian[3]=oosq3;
			return 1;
		}

		// Now mandate c(n-1)= 1, compute last data
		radius[N-1]=1.0;
		double[] lastdata=compLast(t[N-2],radius[N-3],radius[N-2]);
		t[N-1]=lastdata[0];
		uzian[N-2]=lastdata[1];
		uzian[N-1]=lastdata[2];
		uzian[N]=lastdata[3];
		
		if (uzian[N]<0)
			throw new DataException("Flower illigitimate as s_{n}>1.");

		if (branchDeg==0)
			return 1;
		else
			return -branchDeg;
	}

	/**
	 * Given the tangency point of c_{n-2} and the
	 * radii of c_{n-3} and c_{n-2}, we can compute
	 * the tangency point of c_{n-1} and the 3 
	 * final uzians, u_{n-2}, u_{n-1}, and u_n.
	 * (Recall, r_{n-1}=1).
	 * @param tang double, c_{n-2} tangency point
	 * @param rm3 double, r_{n-3}
	 * @param rm2 double, r_{n-2}
	 * @return double[4], t,um2,um1,u
	 */
	public static double[] compLast(double tang,double rm3,double rm2) {
		double[] answers=new double[4];
		double delta=2.0*Math.sqrt(rm2);
		answers[0]=tang+delta;
		answers[1]=oosq3*(Math.sqrt(rm2)+Math.sqrt(rm2/rm3));
		answers[2]=2.0*oosq3/(delta);
		answers[3]=answers[0]*oosq3/2.0;
		return answers;
	}
	
	/**
	 * Situation 1: Find the tangency point of the
	 * last petal c_{n-1}. Radius 1 is mandated.
	 * @param u double
	 * @return
	 */
	public static double[] Sit1(double u) {
		double[] tdata=new double[2];
	    tdata[0]=2.0*sqrt3*u;
	    tdata[1]=1.0;
	    return tdata;
	}
	
	/**
	 * Situation 2: find petal c2; no radii data needed
	 * since c1 is always radius 1.
	 * @param u double
	 * @return double[2], displacement, recip sqrt of radius
	 */
	public static double[] Sit2(double u) {
		double[] tdata=new double[2];
	    tdata[0]=2/(sqrt3*u);
	    tdata[1]=sqrt3*u;
	    return tdata;
	}

	/**
	 * The generic situation as we place successive 
	 * petals. Note that tdata[1] returned may be
	 * negative when this placement shows branching.
	 * @param u double
	 * @param isqr double
	 * @param isqR double
	 * @return double[2], delta, recip of sqrt of radius
	 */
	public static double[] Sit3(double u,double isqr,double isqR) {
		double[] tdata=new double[2]; // isqR*=-1;
	    tdata[0]=2/(isqR*isqR*sqrt3*u-isqR*isqr);
	    tdata[1]=sqrt3*u*isqR-isqr; // tdata[1]=sqrt3*u*isqR-isqr;
	    return tdata;
	}
	
	/**
	 * This takes care of the next petal after one 
	 * involved in branching. The situation when 
	 * isqR is <= 0. Note that tdata[1] will be
	 * negative again, which calling routine must
	 * adjust.
	 * @param u double
	 * @param isqr double
	 * @param isqR double
	 * @return double[2], delta, recip of sqrt of radius
	 */
	public static double[] Sit4(double u,double isqr,double isqR) {
		double [] tdata=new double[2];
		if (Math.abs(isqR)<.008) { // nghb tangent to center at infinity
			tdata[0]=-2*sqrt3*u/(isqr*isqr);
	        tdata[1]=isqr;
	        return tdata;
		}
		return Sit3(u,isqr,isqR);
	}
}
