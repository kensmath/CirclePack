package schwarzWork;

import complex.Complex;

/**
 * Temporary holder for data related to Schwarzian 
 * related data for a given edge <v,w>. Associated 
 * packing(s) depend on context. 
 * @author kensm
 *
 */
public class SchwarzData {
	// oriente edge (v,w)
	public int v;
	public int w;
	public int flag; // non-zero to indicate error or problem
	
	// Complex Schwarzian Derivative and it scalar coeff.
	public Complex Schw_Deriv; 
	public double Schw_coeff; 
	
	// intrinsic schwarzians
	public double domain_schwarzian;
	public double range_schwarzian;
	
	// derivative at 1 of adjusted map from 
	//    base equilateral to face f,
	//    1/(c+d)^2.
	public Complex dmf_deriv;

	// Constructor
	public SchwarzData() {
		flag=0;
	}
	
	public SchwarzData(int vv,int ww) {
		flag=0;
		v=vv;
		w=ww;
	}
}
