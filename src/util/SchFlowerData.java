package util;

/**
 * For working with individual closed flowers 
 * in normalized setting: Center C is upper half 
 * plane, petal c_n is half-plane {y<= -2}, petal 
 * c_1 is tangent at 0 with eucl radius 1, 
 * petals c_j tangent at t[j] for j=1,..,n-1. 
 * Data is 'uzian' variable u, which is
 * given by u=1-s where s is intrinsic schwarzian.
 * 
 * CAUTION: Major difficulties with branching:
 *  + methods (e.g., function f) for finding
 *    last three schwarzians does not hold.
 *  + direction reverses: one step left, 
 *    then adjust next step to be sure it 
 *    goes right. Displacement should never
 *    be zero, but can be negative.
 *  + large circle going left could even
 *    be close to (or even equal to) half
 *    plane of petal c_n. This leaves ambiguity
 *    about where next petal to the right
 *    goes.
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
	public double[] invSqRad; // recip sqroot, possibly neg or 0
	public double[] t; // tangency points
	public int branchDeg; // 0 unless branched
	int status; // 1 if simple flower, -1 if branched,
				// and 0 on error.
	
	// Constructor
	public SchFlowerData(double[] uzs) {
		status=0;
		N=uzs.length-1;
		uzian=new double[N+1]; // indexed from 1
		radius=new double[N+1]; // indexed from 1
		invSqRad=new double[N+1]; // indexed from 1
		t=new double[N+1]; // indexed from 1
		for (int j=1;j<=N;j++)
			uzian[j]=uzs[j];
		branchDeg=0;
		for (int j=N;j>=1;j--) 
			if (uzian[j]<=0) 
				status =-j;
		if (status==0)
			status=compute();
	}
	
	/**
	 * The uzians must be set and we do computations 
	 * based on the first n-3 of them, then recompute
	 * the remaining 3. 
	 * @return int, 1 for simple flower, -n for branching 
	 */
	public int compute() {
		int[] hits=new int[N+1]; // mark when computed
		t[1]=0.0;
		radius[1]=radius[N-1]=invSqRad[1]=invSqRad[N-1]=1.0;
		hits[1]=1;
		branchDeg=0; // at end, branch degree
		
		// compute data for c_2
		if (N>3) {
			double[] tdata=Sit2(uzian[1]);
			t[2]=t[1]+tdata[0];
			radius[2]=1/(tdata[1]*tdata[1]);
			invSqRad[2]=tdata[1];
			hits[2]=1;
		}
		
		// compute as generic up to N-3 or until dspmt is
		//   negative or 0
		double[] tdata=null;
		double dspmt;
		for (int j=3;j<=(N-2);j++) {
			tdata=Sit3(uzian[j-1],invSqRad[j-2],invSqRad[j-1]);
			dspmt=tdata[0];
			invSqRad[j]=tdata[1];
			
			// if displacement is zero or negative, then
			//    we've reached a branch event and special
			//    procedures apply:
			//    * place this petal, c_{j};
			//    * then go on to compute c_{j+1}: 
			//      the invSqRad[j] will be negative, so we 
			//      have to negate it to compute c_{j+1}.
			//    * Then have to increment j before continuing.
			if (Math.abs(dspmt)<.00001) { // essentially <= 0
				branchDeg++;
				if (dspmt>-.00001) { // essentially 0, 
					// c_j is half plane; need fake data
					t[j]=t[j-1];
					radius[j]=10000.0;
					invSqRad[j]=0.0;
					hits[j]=-1;
					
					// next petal uses Situation 1
					j=j+1;
					double r=1/(invSqRad[j-2]*invSqRad[j-2]); // rad of previous
					t[j]=t[j-1]-2.0*uzian[j-1]*sqrt3*r; // to left of previous 
					invSqRad[j]=invSqRad[j-2];
					hits[j]=1;
				}
				else {
					t[j]=t[j-1]+dspmt; // tangency moves left
					radius[j]=1/(invSqRad[j]*invSqRad[j]);
					
					// next petal moves right again
					j=j+1;
					// CAUTION: invSqRad[j-1] will be negative;
					tdata=Sit4(uzian[j-1],invSqRad[j-2],-1.0*invSqRad[j-1]);
					t[j]=t[j-1]+tdata[0];
					invSqRad[j]=tdata[1];
					radius[j]=1/(tdata[1]*tdata[1]);
					hits[j]=1;
				}
			}
			else {  // regular move to right
				t[j]=t[j-1]+dspmt;
				radius[j]=1/(tdata[1]*tdata[1]);
			}
		}
		
		// get last tangency point
		double delta=2.0*Math.sqrt(radius[N-2]);
		t[N-1]=t[N-2]+delta;
		radius[N-1]=1.0;
		
		// now, compute the last three
		if (N==3) { 
			uzian[1]=uzian[2]=uzian[3]=oosq3;
			return 1;
		}
		
		uzian[N-2]=oosq3*(Math.sqrt(radius[N-2])+
				Math.sqrt(radius[N-2]/radius[N-3]));
		uzian[N-1]=2.0*oosq3/(delta);
		uzian[N]=t[N-1]*oosq3/2.0;

		if (branchDeg==0)
			return 1;
		else
			return -branchDeg;
	}
	
	/**
	 * Situation 2: find petal c2; no radii data needed
	 * @param u double
	 * @return double[2], delta, recip sqrt of radius
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
