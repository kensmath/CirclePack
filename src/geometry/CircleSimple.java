package geometry;

import packing.PackData;

import complex.Complex;

/**
 * Basic, simple circle, mainly for transferring data 
 * in/out of methods. Note: geometry is NOT specified. 
 * May us 'flag' to codes for interpreting output/input. 
 * Normally, 
 *     flag=-1 on error, flag=1 on success, flag=0 neutral. 
 * Other options may be agreed in particular calls.
 * lineFlag:
 *     false: regular circles (rad<0 means outside)
 *     true: center is normal vector towards interior, rad = signed distance from origin
*/
public class CircleSimple{
	public int flag;          // utility flag
	public boolean lineFlag;  // +-1, regular (inside/outside), 0 line
	public Complex center;
	public double rad;

	// Constructors
	public CircleSimple(Complex c,double r) {
		this(c,r,0);
	}
	
	public CircleSimple(Complex c, double r,int f) {
		flag=f;
		lineFlag=false;
		if (c!=null)
			center = new Complex(c);
		else 
			center=null;
		rad = r;
	}
	
	/**
	 * Create empty CircleSimple; ok true, set 'flag=0' (OK); else 'flag=-1', error.
	 * @param ok boolean
	 */
	public CircleSimple(boolean ok) {
		center=new Complex(0.0);
		rad=0.0;
		if (ok) flag=0; // create empty CircleSimple
		else flag=-1; // some error 
		lineFlag=false;
	}

	public CircleSimple() {
		this(true);
	}
	
	/**
	 * An error in building if flag<0
	 * @return true on error
	 */
  public boolean gotError() {
    if (flag<0)
      return true;
    else
      return false;
  }

  /**
   * Store radius, center for 'v'
   * @param p PackData
   * @param v int, vertex
   * @return int CircleSimple.flag
   */
  public int save(PackData p,int v) {
	  if (p==null || v<=0 || v>p.nodeCount) return -1;
	  if (flag>=0) { // seems to be good data
		  p.setCenter(v,new Complex(center));
		  p.setRadius(v,rad);
//		  p.kData[v].plotFlag=flag;
	  }
	  return flag; 
  }
}
