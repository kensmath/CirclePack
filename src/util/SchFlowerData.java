package util;

import dcel.Schwarzian;
import exceptions.LayoutException;

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
	final static double oosq3=1/Math.sqrt(3);

	public int N; // number of faces (degree for interior)
	public double[] uzian; // typically u_1,..,u_{n-3} are data
	public double[] radius; // eucl radii r_j, j=1,..,n-1 (r_1=r_{n-1}=1
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
	 * the remaining 3. There can be problems for 
	 * branched flowers.  
	 * 
	 * (The uzians are stored in 'CPBase.Dlink' for
	 * possible use in other calls.)
	 * @return int, 
	 */
	public int compute() {
		int[] hits=new int[N+1]; // mark when computed
		t[1]=0.0;
		radius[1]=radius[N-1]=1.0;
		hits[1]=1;
		branchDeg=0; // at end, branch degree
		boolean right=true; // true: moving right, else left
		
		// compute data for c_2
		if (N>3) {
			double[] Xr=Schwarzian.situationInitial(1.0-uzian[1]);
			t[2]=Xr[0];
			radius[2]=Xr[1];
			hits[2]=1;
		}
		
		// compute as generic up to N-3; change
		//   course if we hit first negative displacement
		double[] Xr=null;
		double dspmt;
		for (int j=3;j<=(N-2);j++) {
			Xr=Schwarzian.situationGeneric
					(1.0-uzian[j-1],radius[j-2],radius[j-1]);
			dspmt=Xr[0];
			
			// if displacement is negative, break out
			if (dspmt<0.0) {
				if (right) {
					status=-j; // this is first neg displacement
					right=false;
					branchDeg += 1;
					// badly ambiguous situation
					if (Xr[0]<-200.0) { // petal j is huge, close
						// to being equal to c_n (half-plane)
						t[j]=Xr[0];
					
						// TODO: finish this from computations
					}
					else {
						radius[j]=Xr[1];
						t[j]=t[j-1]+dspmt;
						hits[j]=1;
					}
				} // done if were moving right
				else {
					right=true;
					if (dspmt>0)
						throw new LayoutException(
								"Opps; should be negative "+
								"after moving left");
					dspmt=-1*dspmt;
					radius[j]=Xr[1];
					t[j]=t[j-1]-dspmt;
					hits[j]=1;
				}
			}
			else {
				radius[j]=Xr[1];
				t[j]=t[j-1]+dspmt;
				hits[j]=1;
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
}
