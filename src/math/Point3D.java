package math;
import geometry.SphericalMath;
import packing.PackData;

import complex.Complex;

/**
 * Represents 3D point (x,y,z). Has conversions between (theta,phi) 
 * and (x,y,z) vector for points on the Riemann sphere.
 * @author Ken Stephenson
 */
public class Point3D {
  public double x,y,z;
  
  // Constructors
  public Point3D(double x, double y, double z) {
    this.x = x; this.y = y; this.z = z;
  }
  
  public Point3D() {
	  this.x=0.0;this.y=0.0;this.z=0.0;
  }
  
  public Point3D(Point3D pt) {
	  this.x=pt.x;this.y=pt.y;this.z=pt.z;
  }
  
  /**
   * Convert point in spherical coords (theta,phi) to Point3D={x,y,z}
   * @param theta
   * @param phi
   */
  public Point3D(double theta, double phi) {
    x = Math.sin(phi) * Math.cos(theta);
    y = Math.sin(phi) * Math.sin(theta);
    z = Math.cos(phi);
  }
  
  /**
   * Convert complex (theta,phi) to {x,y,z}
   * @param z, Complex
   */
  public Point3D(Complex z) { // theta, phi packaged as complex
	  this(z.x,z.y);
  }
  
  /** 
   * return 'theta' value for projection of (x,y,z) onto the unit sphere
   * @return double theta
   */
  public double getTheta() {
    return Math.atan2(y,x);
  }
  
  /** 
   * Get the 'phi' value for projection of (x,y,z) onto the unit sphere
   * @return double phi
   */
  public double getPhi() {
    double dist = Math.sqrt(x * x + y * y + z * z);
    return Math.acos(z/dist);
  }
  
  /**
   * Return displacement p2-p1; i.e., from p1 to p2.
   * @param p1 Point3D
   * @param p2 Point3D
   * @return new Point3D
   */
  public static Point3D displacement(Point3D p1,Point3D p2) {
	  return new Point3D(p2.x-p1.x,p2.y-p1.y,p2.z-p1.z);
  }
  
  /** 
   * subtract Point3D
   * @param p2
   * @return new Point3D, this-p2
   */
  public Point3D sub(Point3D p2) {
	  return displacement(p2,this);
  }

  /**
   * Cross product uXv
   * @param u Point3D
   * @param v Point3D
   * @return new Point3D
   */
  public static Point3D CrossProduct(Point3D u, Point3D v) {
    return new Point3D (u.y * v.z - u.z * v.y,
                        u.z * v.x - u.x * v.z,
                        u.x * v.y - u.y * v.x);
  }
  
  /**
   * Cross product (x,y,0) X (u,v,0)
   * @param z Complex: z=(x,y)
   * @param w Complex: w=(u,v)
   * @return new Point3D
   */
  public static Point3D CrossProduct(Complex z,Complex w) {
	  Point3D u=new Point3D(z.x,z.y,0.0);
	  Point3D v=new Point3D(w.x,w.y,0.0);
	  return CrossProduct(u,v);
  }
  
  /**
   * dot product u dot v
   * @param u
   * @param v
   * @return double
   */
  public static double DotProduct(Point3D u, Point3D v) {
    return u.x*v.x+u.y*v.y+u.z*v.z;
  }

  /**
   * vector sum 
   * @param u Point3D
   * @param v Point3D
   * @return new Point3D
   */
  public static Point3D vectorSum(Point3D u, Point3D v) {
	  return new Point3D(u.x+v.x,u.y+v.y,u.z+v.z);
  }
  
  /**
   * scalar multiple 
   * @param u Point3D
   * @param c Point3D
   * @return new Point3D
   */
  public static Point3D scalarMult(Point3D u,double c) {
	  return new Point3D(u.x*c,u.y*c,u.z*c);
  }
  
  /**
   * Find the angle between vectors in the plane they form
   * @param a Point3D
   * @param b Point3D
   * @return double, zero if one of vectors is length 0
   */
  public static double intersectAng(Point3D a,Point3D b) {
	  double na=a.norm();
	  double nb=b.norm();
	  if (na<.000000000001 || nb<.000000000001)
		  return 0;
	  return Math.acos(DotProduct(a,b)/(na*nb));
  }
  
  /**
   * square of length of this 3 vector
   * @return double
   */
  public double normSq() {
    return x*x+y*y+z*z;
  }
  
  /**
   * Length of this 3 vector
   * @return double
   */
  public double norm() {
    return Math.sqrt(x*x+y*y+z*z);
  }
  
  /**
   * sum of abs values of x, y, z
   * @return double
   */
  public double L1Norm() {
		return (Math.abs(x) + Math.abs(y) + Math.abs(z));
  }
  
  /**
   * Make this a unit vector. If it is too close to zero,
   * return a new copy of 'this'.
   * @return a new Point3D
   */
  public Point3D normalize() {
	  double d=this.norm();
	  if (d<PackData.TOLER)
		  return new Point3D(this);
	  Point3D n3d=new Point3D(this);
	  return n3d.divide(d);
  }
  
  /**
   * scalar multiple by 1/d
   * @param d double
   * @return new Point3D
   */
  public Point3D divide(double d) {
    return new Point3D(x/d,y/d,z/d);
  }
  
  /**
   * scalar multiple by d
   * @param d double
   * @return new Point3D
   */
  public Point3D mult(double d) {
    return new Point3D(x*d,y*d,z*d);
  }
  
  /**
   * Length of remaining vector after projection of
   * this vector in direction of v is removed.
   * @param v Point3D
   * @return double
   */
  double rem(Point3D v) {
		double d = DotProduct(this,v) / v.norm();
		return Math.sqrt(this.normSq() - d * d);
  }
  
  /**
   * projects (x,y,z) to point on projected horizon (unit circle in yz-plane),
   * returning complex sph point (theta,phi). Point near yz-origin defaults
   * to north pole.
   * @param pt Point3D
   * @return new Complex, (theta, phi)
   */
  public Complex projToHorizon() {
	  double norm=Math.sqrt(y*y+z*z);
	  if (norm < .000000001) return new Complex(0,0);
	  double Y=y/norm;
	  double Z=z/norm;
	  if (Math.abs(Y)<.0000001) { // north or south pole
		  if (Z>0) return new Complex(0,0);
		  else return new Complex(0,Math.PI/2.0);
	  }
	  if (Y<0) return new Complex(-Math.PI/2,Math.acos(Z));
	  return new Complex(Math.PI/2,Math.acos(Z));
  }
  
  /**
   * eucl distance between 'Point3D's
   * @param A Point3D
   * @param B Point3D
   * @return double
   */  
  public static double distance(Point3D A, Point3D B) {
    return Math.sqrt((A.x-B.x)*(A.x-B.x)+(A.y-B.y)*(A.y-B.y)+(A.z-B.z)*(A.z-B.z));
  }
  
/**
 * direct conversion of (x,y,z) (on unit sphere) to spherical point; no 
 * manipulations. 
 * @return new Complex, (theta,phi)
 */
  public Complex getAsSphPoint() {
    return new Complex(this.getTheta(),this.getPhi());
  }
  
  
  /**
   * Return this vector minus projection in direction of vector v.
   * @param v Point3D
   * @return new Point3D
   */
  public Point3D mod(Point3D v) {
	  Point3D V=proj_vector(this,v);
	  return displacement(V,this);
  }

  /**
   * produce a perp vector; default is <1 0 0>.
   * @return new Point3D
   */
  public Point3D perp() {
	Point3D perp = new Point3D(z, -x, y); 
	perp.mod(this);
	perp.normalize();
	if (Double.isNaN(perp.L1Norm()))
		return new Point3D(1.0, 0.0, 0.0);
	return perp;
  }

  /**
   * Compute euclidean area of triangle in 3-space using
   * edge lengths and Heron's formula.
   * @param p0 Point3D
   * @param p1 Point3D
   * @param p2 Point3D
   * @return double
   */
  public static double triArea(Point3D p0,Point3D p1,Point3D p2) {
		double a=distance(p0,p1);
		double b=distance(p1,p2);
		double c=distance(p2,p0);
		double ans=Math.sqrt((a+b+c)*(a+b-c)*(a+c-b)*(b+c-a))/4.0;
		if (Double.isNaN(ans))
			ans=0.0;
		return ans;
  }

  /**
   * Return the projection of v in the direction of w.
   * If w is too small, return zero vector.
   * @param v Point3D
   * @param w Point3D
   * @return new Point3D
   */
  public static Point3D proj_vector(Point3D v,Point3D w) {
	  if (w.norm()<PackData.TOLER)
		  return new Point3D(0.0,0.0,0.0);
	  Point3D W=new Point3D(w);
	  double d=DotProduct(v,w);
	  W.mult(d/w.norm());
	  return W;
  }
  
  /**
   * Converts a (theta,phi) point on sphere to Point3D
   * @param z Complex, spherical coords
   * @return Point3D
   */
  public static Point3D sph_2_p3D(Complex sph_z){
	  return new Point3D(Math.sin(sph_z.y) * Math.cos(sph_z.x),
			  Math.sin(sph_z.y) * Math.sin(sph_z.x),
			  Math.cos(sph_z.y));
  }

  /**
   * Return new Complex (theta,phi) representing projection of Point3D 
   * to the unit sphere; points close to origin in 3D go to NORTH pole.
   * @param pt Point3D
   * @return Complex, sph coords (theta,phi) 
   */
  public static Complex p3D_2_sph(Point3D pt) {
	  // default to north pole for things too near origin 
	  if (pt.norm()<SphericalMath.S_TOLER) {
		  return new Complex(0.0);
	  }
	  return new Complex(Math.atan2(pt.y,pt.x),Math.acos(pt.z/pt.norm()));	
  } 
  
}
