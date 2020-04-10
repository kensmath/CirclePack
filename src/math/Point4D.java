package math;

/**
 * Represents 4D point (t,x,y,z), intended for points in Lorentz space;
 * see 'LorentzMath'. 
 * 
 * Circles are represented by points (t,x,y,z) satisfying t^2-x^2-y^2-z^2 = -1.
 * This corresponds to the spherical circle 
 *    {(a,b,c):a^2+b^2+c^2=1, ax+by+cz=t} 
 * @author 
 */
public class Point4D {
  public double t,x,y,z;
  
  // Constructors
  public Point4D(double t,double x, double y, double z) {
    this.t=t; this.x = x; this.y = y; this.z = z;
  }
  
  public Point4D(Point4D pt) {
	  this.t=pt.t;this.x=pt.x;this.y=pt.y;this.z=pt.z;
  }
  
  /**
   * Lorentz product u.v
   * @param u Point4D
   * @param v Point4D
   * @return double
   */
  public static double LorentzProduct(Point4D u, Point4D v) {
    return u.t*v.t-u.x*v.x-u.y*v.y-u.z*v.z;
  }

  /**
   * vector sum 
   * @param u Point4D
   * @param v Point4D
   * @return new Point4D
   */
  public static Point4D vectorSum(Point4D u, Point4D v) {
	  return new Point4D(u.t+v.t,u.x+v.x,u.y+v.y,u.z+v.z);
  }
  
  /**
   * scalar multiple 
   * @param u Point4D
   * @param c Point4D
   * @return new Point4D
   */
  public static Point4D scalarMult(Point4D u,double c) {
	  return new Point4D(u.t*c,u.x*c,u.y*c,u.z*c);
  }
  
  /**
   * scalar multiple by d
   * @param d double
   * @return new Point4D
   */
  public Point4D mult(double d) {
    return new Point4D(t*d,x*d,y*d,z*d);
  }

  /**
   * scalar multiple by 1/d
   * @param d double
   * @return new Point3D
   */
  public Point4D divide(double d) {
    return new Point4D(t/d,x/d,y/d,z/d);
  }

  /**
   * Lorentz form has signature (1,3), so is indefinite; norm
   * can be positive, negative, or zero
   * @return
   */
  public double norm() {
    return Math.sqrt(t*t-x*x-y*y-z*z);
  }
  
}
