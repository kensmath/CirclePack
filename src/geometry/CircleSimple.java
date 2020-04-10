package geometry;

import packing.PackData;

import complex.Complex;

/**
 * Basic, simple circle, mainly for transferring data from methods. 'flag' passes
 * some code for interpreting output/input. 
 * Normally, 
 *     flag=-1 on error, flag=1 on success, flag=0 neutral. 
 * Other options may be agreed in particular calls.
 * lineFlag false, the regular circles (rad<0 means outside)
 *     true, then center=normal towards interior, rad= signed distance from origin
*/
public class CircleSimple{
	public int flag;          // utility flag
	public boolean lineFlag;  // +-1, regular (inside/outside), 0 line
	public Complex center;
	public double rad;

	// Constructors
	public CircleSimple(Complex c, double r,int f) {
		flag=f;
		lineFlag=false;
		center = new Complex(c);
		
		rad = r;
	}

	public CircleSimple(double x, double y, double r,int f) {
		flag=f;
		lineFlag=false;
		center = new Complex(x, y);
		rad = r;
	}
	
	/**
	 * Create empty SimpleCircle; ok true, set 'flag=0' (OK); else 'flag=-1', error.
	 * @param ok
	 */
	public CircleSimple(boolean ok) {
		center=new Complex(0.0);
		rad=0.0;
		if (ok) flag=0; // create empty SimpleCircle
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
   * Store radius, center, and plotflag in 'p.rData[v]'
   * @param p @see PackData
   * @param v int, vertex
   * @return int SimpleCircle.flag
   */
  public int save(PackData p,int v) {
	  if (p==null || v<=0 || v>p.nodeCount) return -1;
	  if (flag>=0) { // seems to be good data
		  p.rData[v].center=new Complex(center);
		  p.rData[v].rad=rad;
		  p.kData[v].plotFlag=flag;
	  }
	  return flag; 
  }
}
