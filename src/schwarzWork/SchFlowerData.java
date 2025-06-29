package schwarzWork;

import java.util.ArrayList;

import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
import geometry.CircleSimple;
import packing.PackCreation;

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
 * CAUTION: In my paper on schwarzians, indexing
 * is from zero, e.g. uzians are {u_0,u_1,...u_{n-1}}
 * and in normalization, c_0 is the half plane
 * y<=-2i. However, in the code indexing is from 1,
 * so c_0 is c_n. However, in both the paper and
 * the code, the n-3 parameters are u_1,...,u_{n-3}.
 * 
 * Easy to encounter errors or improper values
 * in computations: e.g., if some u becomes negative.
 * We throw a 'DataException' (and generally
 * catch this for some tailored reaction).
 * 
 * CAUTION: Major difficulties with branching:
 *  + methods (e.g., function f) for finding
 *    the n-2 schwarzians only for un-branched
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
 * last 3 uzians in 'uzs' may be recomputed to
 * complete a packing (edge) label. 
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
			status=compute(); // fills uzian[] and sets branchDeg
		status=1;
	}
	
	/**
	 * The uzians must be set and we do computations 
	 * based on the first n-3 of them, then recompute
	 * the remaining 3, watching for out-of-bounds values
	 * @return int 1 if okay, -n if branching of order n,
	 * 0 on failure (due to exceptions).
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
		//   watch for dspmt very large, negative, or 0, which
		//   means there's branching.
		double[] tdata=null;
		double dspmt;
		for (int j=3;j<=(N-2);j++) {
			if (recipSqRad[j-1]==0) {
				if (j==N-2) {
					CirclePack.cpb.errMsg("Can't complete a flower "+
							"since c_{n-2} is a halfplane");
					return 0;
				}
				double r=1/(recipSqRad[j-2]*recipSqRad[j-2]); // same rad as previous
				t[j]=t[j-2]-2.0*uzian[j]*sqrt3*r; // but to left of c(j-1)
				recipSqRad[j]=recipSqRad[j-2];
				hits[j]=1;
			}
			else if (recipSqRad[j-1]<0) {
				tdata=Sit4(uzian[j-1],recipSqRad[j-2],recipSqRad[j-1]);
				t[j]=t[j-1]+tdata[0];
				recipSqRad[j]=tdata[1];
				radius[j]=1/(tdata[1]*tdata[1]);
				hits[j]=1;
			}
			else {
				tdata=Sit3(uzian[j-1],recipSqRad[j-2],recipSqRad[j-1]);
				dspmt=tdata[0];
				recipSqRad[j]=tdata[1];
				t[j]=t[j-1]+dspmt;
				radius[j]=1/(tdata[1]*tdata[1]);
			}
		}

		// now, compute the last three uzians
		if (N==3) {
			uzian[N-2]=uzian[N-1]=uzian[N]=oosq3;
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
			throw new DataException("Flower illigitimate as s_{N}>1.");

		if (branchDeg==0)
			return 1;
		else
			return -branchDeg;
	}

	/**
	 * Given the tangency point of c_{n-3} and the
	 * radii of c_{n-4} and c_{n-3}, we can compute
	 * the tangency point of c_{n-2} and the 3 
	 * final uzians, u_{n-3}, u_{n-2}, and u_{n-1}.
	 * (Recall, r_{n-2}=1).
	 * @param tang double, c_{n-3} tangency point
	 * @param rm4 double, r_{n-4}
	 * @param rm3 double, r_{n-3}
	 * @return double[4], t,um2,um1,u
	 */
	public static double[] compLast(double tang,double rm4,double rm3) {
		double[] answers=new double[4];
		double delta=2.0*Math.sqrt(rm3);
		answers[0]=tang+delta;
		answers[1]=oosq3*(Math.sqrt(rm3)+Math.sqrt(rm3/rm4));
		answers[2]=2.0*oosq3/(delta);
		answers[3]=answers[0]*oosq3/2.0;
		return answers;
	}
	
	/**
	 * Situation 1: Find the tangency point of the
	 * last petal c_{n-2}. Radius 1 is mandated.
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
	 * Situation 2: find petal c1; no radii data needed
	 * since c0 is always radius 1.
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
	 * petals. Note that tdata[0] and tdata[1] may be
	 * negative when this placement shows branching.
	 * @param u double
	 * @param isqr double
	 * @param isqR double
	 * @return double[2], delta, recip of sqrt of radius
	 */
	public static double[] Sit3(double u,double isqr,double isqR) {
		double[] tdata=new double[2];
	    tdata[0]=2/(isqR*isqR*sqrt3*u-isqR*isqr);
	    tdata[1]=sqrt3*u*isqR-isqr; 
	    return tdata;
	}
	
	/**
	 * This takes care of the next petal after one 
	 * leading to branching. The situation occurs when 
	 * isqR is <= 0 (though ==0 should generally be
	 * handled separately).
	 * @param u double
	 * @param isqr double
	 * @param isqR double, negative
	 * @return double[2], delta, recip of sqrt of radius
	 */
	public static double[] Sit4(double u,double isqr,double isqR) {
		double [] tdata=new double[2];
		if (Math.abs(isqR)<.008) { // nghb tangent to center at infinity
			tdata[0]=2*sqrt3*u/(isqr*isqr);
	        tdata[1]=isqr;
	        return tdata;
		}
		return Sit3(u,-isqr,-isqR);
	}
	
	/**
	 * Compute full array of "constraint" functions 
	 * C_j, j=0,...,(N-1), based on given N uzians 
	 * {u0,u1,...,u(N-1)}. We set C_0=0.0, C_1=1.0, and
	 * C_{N-1}=1.0 in advance and compute the rest 
	 * recursively, j=2,...,N-2. As long as the 
	 * C_k, 2<=k<j, are positive, then C_j is the 
	 * reciprocal root of radius r_j, j=2,..., N-2,
	 * in the normalized layout. Use this recursive:
	 * 
	 * (*) C_{j+1}=sqrt{3}*u_j*C_j - C_{j-1} 
	 *     for 1 <= j <= N-2.
	 * 
	 * Convention in the paper on schwarzians is that 
	 * constraint C_j has j-1 arguments u1,...,u{j-1}, 
	 * for 2<=j<=N-2. If some C_j is negative, then the
	 * flower is branched and (*) may not hold, but
	 * we continue anyway.
	 * 
	 * NOTE: Often we call this with uzians indices
	 * shifted; calling routine takes care of this
	 * on input and output. 
	 * 
	 * NOTE: If C_j are positive, the flower is 
	 * un-branched and we can compute everything 
	 * in the half-plane normalization from the C_j:
	 * 	+	r_j=1/(C_j)^2 and 
	 * 	+	displacement delta_j=2/(C_j*C_{j+1}), 
	 *      1<=j<=N-2
	 * 	+	u_{N-2}=(1+C_{N-3})/(sqrt3*C_{N-2}) 
	 *      (which is (*))
	 * 
	 * @param uz uzians, indexed from 1
	 * @param allpos Boolean, true=all positive; instantiate by calling 
	 * @return ArrayList: <0.0,1.0,C_2,...,C_{N-2},C_{N-1}>
	 */
	public static ArrayList<Double> constraints(double[] uz,Boolean allpos) {
		allpos=true;
		int N=uz.length-1;
		if (N<4)
			return null;

		ArrayList<Double> cons=new ArrayList<Double>(0);
		cons.add(0,Double.valueOf(0.0));
		cons.add(1,Double.valueOf(1.0));
		double Cj=1.0;
		double Cjm1=0.0;
		// recursively define C_{j+1} for j=1,...,(n-3)
		// keep going even if a negative is encountered
		for (int j=1;j<=(N-3);j++) {
			double cjp1=sqrt3*uz[j]*Cj-Cjm1;
			if (cjp1<=0.0) 
				allpos=false;
			cons.add(Double.valueOf(cjp1));
			Cjm1=Cj;
			Cj=cjp1;
		}
		cons.add(N-1,Double.valueOf(1.0));
		return cons;
	}
	
	/**
	 * Compute the "error" if given uzians are laid
	 * out as a euclidean n-flower around the unit disc. 
	 * Use the 'seed' call from 'PackCreation'; we 
	 * don't need the packing, but CircleSimple.center
	 * contains the complex error x+iy, where x is the 
	 * error in radii x=r-R, and y is the error in the
	 * angle sum y=Theta-A|.
	 * The uzians form an actual closed flower iff result
	 * is zero. Note that we only use n-1 uzians, so if
	 * 'layoutErr' is zero, the final uzian can be computed.
	 * @param uzians double[], will convert to schwarzians
	 * @param order int
	 * @return Complex
	 */
	public static Complex euclFlower(double[] uzians,
			int order) {
		int n=uzians.length;
		ArrayList<Double> schvec=new ArrayList<Double>(n);
		for (int j=0;j<n;j++)
			schvec.add(1.0-uzians[j]);
		CircleSimple cs=new CircleSimple();
		Complex layoutErr=new Complex(0.0);
		PackCreation.seed(schvec,cs,layoutErr,order);
		return layoutErr; // this contains the error
	}
	
	/**
	 * Compute the intrinsic schwarzians for a 
	 * uniform flower of N petals with 'anglesum'. 
	 * Typically, anglesum = multiple of 2*pi.
	 * @param N int
	 * @param anglesum double
	 * @return double
	 */
	public static double uniformS(int N,double anglesum) {
		return 1.0-(2.0*Math.cos(anglesum/(2.0*N))/sqrt3);
	}
			
}
