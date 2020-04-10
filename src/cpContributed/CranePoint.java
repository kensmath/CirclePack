package cpContributed;

import packing.PackData;

import complex.Complex;

/**
 * "Crane points" in circle packing are vertices (or circles)
 * that are being developed (after a suggestion by Edward
 * Crane) to accommodate parameterized branching. Used by
 * James Ashe in 'FracBranching'.
 * @author kens
 *
 */
public class CranePoint {
	public int vert;
	public int num;
	public double theta;
	public double shiftratio;
	public double radius;
	public double jumpAng;
	public Complex center;
	public Complex littleCenter;
	public double littleRad;
	public int littleHES; //little center data is which geometry?
	public int alpha;
	
	// petal data must be updated
	public double []petalAng; // array of angles at neighbors
	double []petalR; // petal radii
	public double []petalI; //inversive distance from petals to little circle
	public Complex []petalZ; // petal centers
	public double value;
	public double anglesumError;
	public double anglesum;
	
	public PackData pd;
	
	// Constructor
		public CranePoint(PackData p, int v,double t,double s,int P) {
		pd=p;
		vert=v;
		theta=t*Math.PI;
		shiftratio=s;
		if (s<0) {
			shiftratio = -1/s;
		}
		alpha=P;
		num=pd.kData[vert].num;
		petalAng=new double[num];//num+1
		petalR=new double[num];//num+1
		petalZ=new Complex[num];//num+1
		petalI=new double[num];//num+1
		jumpAng=2*Math.PI;//TODO change to variable
	}
	
	/**
	 * Refresh the petal radii from my PackData
	 */
	public void refreshPackRadii() {
		for (int j=0;j<=num;j++) {
			petalR[j]=pd.rData[vert].rad;
		}
	}
	
	/**
	 * You can install petal radii that are not from
	 * the PackData.
	 * @param radii
	 * @return -1 on error.
	 */
	public int loadPetalRadii(double []radii) {
		if (radii==null || radii.length<(num+1))
			return -1;
		for (int j=0;j<=num;j++)
			petalR[j]=radii[j];
		return num;
	}
	
	/**
	 * Return my angle sum based on radius R. User must
	 * be sure to update petal radii.
	 * @param R, use as 'CranePoint' radius 
	 */
	public void angleSum(double R) {
		
	}
	
	/**
	 * Refresh the 'petalZ' (petal centers) for normalized
	 * position and 'petalAngs' (petal angles); based on 
	 * current data.
	 */
	public void refreshData() {
		
	}
	
	/**
	 * Returns an array of angles stored for this shift point.
	 * Note that these may need to be refreshed if data has
	 * changed. They represent angles AT the neighboring petals
	 * in the face(s) shared with the Crane point itself.
	 * Generically, this will be a sum of the angles in two
	 * shared faces; but at petal 0 and petal 'num', it is 
	 * just from one shared face. If the flower is closed, the
	 * first and last angles have to be added for the 0 
	 * neighbor.
	 * @return double []
	 */
	public double []getAngs() {
		double []ans=new double[pd.kData[vert].num+1];
		for (int j=0;j<=pd.kData[vert].num;j++)
			ans[j]=petalAng[j];
		return ans;
	}
	
}


