package geometry;

import packing.PackData;

import complex.Complex;

/**
 * Basic, simple circle, mainly for transferring data in/out 
 * of methods. Note: geometry is NOT specified. May use 'flag' 
 * to codes for interpreting output/input. 
 * Normally, 
 *     flag=-1 on error, flag=1 on success, flag=0 neutral. 
 * Other options may be agreed in particular calls.
 * lineFlag:
 *     false: regular circles (rad<0 means outside)
 *     true: center is unit normal vector towards interior 
 *           times CPBase.FAUX_RAD, while -rad is FAUX_RAD plus
 *           signed distance from origin (so if rad>FAUX_RAD,
 *           then origin is "interior" and line is shifted by
 *           vector -rad*unit normal.)
 * NOTE: 'lineflag' is only used in eucl setting; it is NOT set 
 * in spherical geometry for circles through infinity.
 * 
 * @author kens
*/
public class CircleSimple{
	public int flag;          // utility flag
	public boolean lineFlag;  // true if a line: then center is
							  // unit normal, rad = shift from origin
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
	  if (p==null || v<=0 || v>p.nodeCount) 
		  return -1;
	  if (flag>=0) { // seems to be good data
		  p.setCenter(v,new Complex(center));
		  p.setRadius(v,rad);
		  p.setPlotFlag(v,flag);
	  }
	  return flag; 
  }
}
