package geometry;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import baryStuff.BaryPoint;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import dcel.PackDCEL;
import exceptions.DataException;
import math.Mobius;
import math.Point3D;
import packing.PackData;
import util.RadIvdPacket;
/**
 * Static methods for mathematical operations in euclidean geometry.
 */

public class EuclMath{

	public static final double OKERR=.000000001; // TODO: set more rationally

	/**
	 * Find eucl center/rad for circle thru 3 points in eucl plane.
	 * Find intersection of perp bisector of two sides for 'cent',
	 * then compute radius.
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return CircleSimple
	 */
	public static CircleSimple circle_3(Complex z1, Complex z2, Complex z3) {
		double a1 = z2.x-z1.x;
		double a2 = z3.x-z2.x;
		double b1 = z2.y-z1.y;
		double b2 = z3.y-z2.y;
		double det = (a1 * b2 - b1 * a2);
		if (Math.abs(det) < 0.0000000000001) // return with radius 0.0
			return new CircleSimple(false);

		double dum = z2.absSq()/2.0;
		double c1 = dum-z1.absSq()/2.0;
		double c2 = z3.absSq()/2.0-dum;

		Complex cent=new Complex((b2*c1-b1*c2)/det,(a1*c2-a2*c1)/det);
		double c_rad=cent.minus(z1).abs();
		return new CircleSimple(cent,c_rad,1);
	}
    
  /**
	 * Given three eucl radii and edge inv distances, compute 
	 * cosine of the angle at r0. Note: ivdj is the inversive 
	 * distance for the edge with origin at rj, so edge 
	 * opposite is index (j+1)%3.
	 * @param r0 double
	 * @param r1 double
	 * @param r2, double
	 * @param ivd0 double
	 * @param ivd1 double
	 * @param ivd2 double
	 * @return double cosine of angle
	 */
	public static double e_cos_overlap(double r0, double r1, double r2,
			double ivd0, double ivd1, double ivd2) {

		if ((ivd0==1.0) && (ivd1==1.0) && (ivd2==1.0))
			return e_cos_overlap(r0,r1,r2);
		// squared radii
		double e0sq = r0 * r0;
		double e1sq = r1 * r1;
		double e2sq = r2 * r2;
		// edge lengths squared
		double t0=2.0*r0*r1*ivd0;
		double t1=2.0*r1*r2*ivd1;
		double t2=2.0*r2*r0*ivd2;
		double len0sq=e0sq+e1sq+t0;
		double len1sq=e1sq+e2sq+t1;
		double len2sq=e2sq+e0sq+t2;
		double cos_ang=(len0sq+len2sq-len1sq)/(2.0*Math.sqrt(len0sq * len2sq));
		if (cos_ang < -1.0 || cos_ang>1.0) { // error? 
			if (cos_ang>1.0)
				return .9999999999; 
			if (cos_ang<-1.0)
				return -.9999999999;
		}
		return cos_ang;
	}
	
	/**
	 * Using law of cosines, compute cos(angle at e0) in
	 * tangent triple with eucl radii r0,r1,r2 and all
	 * inv distances = 1.0;
	 * @param r0 double
	 * @param r1 double
	 * @param r2 double
	 * @return double, cos(angle)
	 */
	public static double e_cos_overlap(double r0, double r1, double r2) {
		double c=r1*r2;
		return 1-2*c/(r0*(r0+r1+r2)+c);
	}
	
	/**
	 * Use law of cosines, compute cos(angle at c1) in eucl
	 * triangle with given corners.
	 * @param z0 Complex, first corner, etc.
	 * @param z1
	 * @param z2
	 * @return double, cosine of angle
	 */
	public static double e_cos_corners(Complex z0,Complex z1,Complex z2) {
		double l1=z1.minus(z0).abs();
		double l2=z2.minus(z0).abs();
		double l12=z2.minus(z1).abs();
		double denom=2.0*l1*l2;
		return (l1*l1+l2*l2-l12*l12)/denom;
	}
	
	
	/**
	 * Using law of cosines, compute cos(angle at p1) in eucl 3D 
	 * triangle with given corners in space.
	 * @param p0 Point3D
	 * @param p1 Point3D
	 * @param p2 Point3D
	 * @return double, cosine of angle
	 */
	public static double e_cos_3D(Point3D p0,Point3D p1,Point3D p2) {
		double l0=Point3D.distance(p0, p1);
		double l2=Point3D.distance(p2, p0);
		double l1=Point3D.distance(p1, p2);
		double denom=2.0*l0*l2;
		return (l0*l0+l2*l2-l1*l1)/denom;
	}
	
	/**
	 * Given face data in 'RadIvdPacket', return euclidean area
	 * @param rip RadIvdPacket
	 * @return double
	 */
	public static double eArea(RadIvdPacket rip) {
		return eArea(rip.rad[0],rip.rad[1],rip.rad[2],
				rip.ivd[0],rip.ivd[1],rip.ivd[2]);
	}
	
	 /**
	 * Given three eucl radii and edge inv distances, compute
	 * face area using Heron's Formula. 'tj' is for the edge
	 * <j,j+1>.
	 * @param r0 double
	 * @param r1 double
	 * @param r2 double
	 * @param t0 double
	 * @param t1 double
	 * @param t2 double
	 * @return double
	 */
	public static double eArea(double r0,double r1,double r2,
			double t0,double t1,double t2) {
		double a=e_ivd_length(r0,r1,t0);
		double b=e_ivd_length(r1,r2,t1);
		double c=e_ivd_length(r2,r0,t2);
		return Math.sqrt((a+b+c)*(a+b-c)*(a+c-b)*(b+c-a))/4.0;
	}
	
	/**
	 * Does half-line from p1 through p2 intersect triangle <a,b,c>?
	 * @param p1 Complex
	 * @param p2 Complex
	 * @param a Complex
	 * @param b Complex
	 * @param c Complex
	 * @return boolean
	 */
	public static boolean doesItHit(Complex p1, Complex p2,
			Complex a,Complex b,Complex c) {
		Complex v=p2.minus(p1);
		if (v.abs()<.00000001) 
			throw new DataException("points are too close");
		Complex p1a=a.minus(p1);
		Complex p1b=b.minus(p1);
		Complex p1c=c.minus(p1);
		double arga=p1a.divide(v).arg();
		double argb=p1b.divide(v).arg();
		double argc=p1c.divide(v).arg();
		
		if ((arga>EuclMath.OKERR && argb>EuclMath.OKERR
				&& argc>EuclMath.OKERR) ||
				(arga<-EuclMath.OKERR && argb<-EuclMath.OKERR
						&& argc<-EuclMath.OKERR))
			return false;
		return true;
	}
	
	/**
	 * Is point in eucl triangle <a,b,c>
	 * @param p1 Complex
	 * @param a Complex
	 * @param b Complex
	 * @param c Complex
	 * @return boolean
	 */
	public static boolean pt_in_eucl_tri(Complex p1,
			Complex a,Complex b,Complex c) {
		if ((a.x-p1.x)*(b.y-p1.y)-(b.x-p1.x)*(a.y-p1.y)<0) 
			return false;
		if ((b.x-p1.x)*(c.y-p1.y)-(c.x-p1.x)*(b.y-p1.y)<0) 
			return false;
		if ((c.x-p1.x)*(a.y-p1.y)-(a.x-p1.x)*(c.y-p1.y)<0) 
			return false;
		return true;
	}
	
	/**
	 * Find the eucl BaryPoint for given point relative to given
	 * oriented triple. Note that the point need not be in the
	 * triangle.
	 * @param z Complex, given point
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return BaryPoint, null if corners colinear.
	 */
	public static BaryPoint e_pt_to_bary(Complex z,
			Complex z0,Complex z1,Complex z2) {

		// linear alg: 2x2 T
		double a=z0.x-z2.x;
		double b=z1.x-z2.x;
		double c=z0.y-z2.y;
		double d=z1.y-z2.y;
		double det=a*d-b*c;
		if (Math.abs(det)<CPBase.GENERIC_TOLER) { // points colinear
			// TODO: can find two non-zero corners, use them and origin
			return null;
		}
		double x=z.x-z2.x;
		double y=z.y-z2.y;
		
		// solve inv(T)(x,y) = (b1, b2)
		return (new BaryPoint((d*x-b*y)/det,(-c*x+a*y)/det));
	}

	/**, 
	 * if line from p1 in direction of p2 hits (oriented)
	 * triangle {a,b,c}, return first entry point, else
	 * return null. (Return Complex because {a,b,c} may 
	 * have been rotated.)
	 * @param p1 Complex
	 * @param p2 Complex, assume p1 not equal to p2
	 * @param a Complex
	 * @param b Complex
	 * @param c Complex
	 * @return Complex first hit (possibly p1 itself) or null
	 */
	public static Complex firstHit(Complex p1,Complex p2,
			Complex A,Complex B,Complex C) {

		if (pt_in_eucl_tri(p1,A,B,C)) 
			return new Complex(p1);
		Complex p12=p2.minus(p1);
		if (p12.abs()<.000001)
			throw new DataException("p1 is not supposed to equal p2");
		
		// one of bp coords must be < 0, rotate so that's a
		BaryPoint bp=EuclMath.e_pt_to_bary(p1,A,B,C);
		Complex a=null;
		Complex b=null;
		Complex c=null;
		if (bp.b0<0) {
			a=new Complex(A);
			b=new Complex(B);
			c=new Complex(C);
		}
		else if (bp.b1<0) {
			a=new Complex(B);
			b=new Complex(C);
			c=new Complex(A);
		}
		else if (bp.b2<0) {
			a=new Complex(C);
			b=new Complex(A);
			c=new Complex(B);
		}
		else 
			throw new DataException("implies p1 is in triangle");

		Complex p1a=a.minus(p1);
		Complex p1b=b.minus(p1);
		Complex p1c=c.minus(p1);
		double arga=p1a.divide(p12).arg();
		double argb=p1b.divide(p12).arg();
		double argc=p1c.divide(p12).arg();
		
		// all on one side or other?
		if ((arga>=-OKERR && argb>=-OKERR && argc>=-OKERR) ||
				(arga<=OKERR && argb<=OKERR && argc<=OKERR)) {
			// line pts at some corner?
			if (Math.abs(arga)<OKERR || Math.abs(argb)<OKERR || 
					Math.abs(argc)<OKERR) {
				if (Math.abs(argb)<OKERR) {
					if (Math.abs(argc)<OKERR && 
							p12.minus(p1b).abs()>=p12.minus(p1c).abs())
						return new Complex(c);
					return new Complex(b);
				}
				if (Math.abs(argc)<OKERR)
					return new Complex(c);
				return new Complex(a); // since all one side
			}
			return null;
		}

		// which side is seen from p1? [a b], [b c], or [c a]?
		if (argc<0 && argb >0) // note: must have argc < argb 
			return segIntersect(p1,p2,b,c);
		if (arga<0)  // b c must be on left
			return segIntersect(p1,p2,c,a);
		return segIntersect(p1,p2,a,b);
	}

	/**
	 * Return point where halfline from p1 in direction of p2 hits
	 * directed segment [a,b] from its right side. Return null 
	 * if it doesn't hit. Assume p1 != p2.
	 * @param p1 Complex
	 * @param p2 Complex
	 * @param a Complex
	 * @param b Complex
	 * @return new Complex, null if doesn't hit
	 */
	public static Complex segIntersect(Complex p1,Complex p2,
			Complex a,Complex b) {
		Complex p1b=b.minus(p1);
		if (p1b.abs()<OKERR) return new Complex(p1); // p1 == b
		Complex p1p2=p2.minus(p1);
		Complex p1a=a.minus(p1);
		if (p1a.abs()<OKERR) return new Complex(p1); // p1==a
		double theta=p1p2.divide(p1b).arg();
		if (theta<-OKERR) return null; // b not to right?
		double phi=p1a.divide(p1p2).arg();
		if (phi<-OKERR) return null; // a not to left?
		
		// check for p1 on [a b]
		Complex ab=b.minus(a);
		double lengab=ab.abs();
		if (Math.abs(p1b.abs()+p1a.abs()-lengab)<OKERR/lengab)
			return new Complex(p1);
			
		// p1 p2 parallel to ab
		double argdist=Math.abs(ab.arg()-p1p2.arg());
		if (argdist<OKERR || Math.abs(argdist-Math.PI)<OKERR) {
			if (theta<OKERR) 
				return new Complex(b);
			if (Math.abs(p1p2.divide(p1a).arg())<OKERR)
				return new Complex(a);
			else return null; // must be paraallel or point away from [a b]
		}
		
		// generic case; lin alg to find where line intersects [a,b] (in that order)
//		double lam=((p2.x-p1.x)*(p1.x-a.y)-(p2.y-p1.y)*(p1.x-a.x))/
//		((b.y-a.y)*(p2.x-p1.x)-(b.x-a.x)*(p2.y-p1.y));
		double lam=((a.y-b.y)*(b.x-p1.x)-(a.x-b.x)*(b.y-p1.y))/
			((a.y-b.y)*(p2.x-p1.x)-(a.x-b.x)*(p2.y-p1.y));
		return (new Complex(p1.x+lam*(p2.x-p1.x),p1.y+lam*(p2.y-p1.y)));
		
	}

	/**
	 * Compute barycenter of euclidean triangle from corners.
	 * @param p0 Complex
	 * @param p1 Complex
	 * @param p2 Complex
	 * @return new Complex
	 */
	public static Complex eucl_tri_center(Complex p0,Complex p1,Complex p2) {
		  Complex ctr =new Complex();
	  	  
		  ctr.x=(p0.x+p1.x+p2.x)*.333333333333;
		  ctr.y=(p0.y+p1.y+p2.y)*.333333333333;
		  return ctr;
	}

	/**
	 * Return 'CircleSimple' with rad/cent of inscribed circle
	 * for triangular face with given corners. Incenter has 
	 * barycentric coords a/p, b/p, c/p, where a, b, c are 
	 * opposite edge lengths, p is perimeter.
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex, circle centers
	 * @return CircleSimple
	 */
	public static CircleSimple eucl_tri_incircle(Complex z0,
			Complex z1,Complex z2) {
		CircleSimple sc=new CircleSimple();

		// side lengths
		double a=z1.minus(z2).abs();
	  	double b=z0.minus(z2).abs();
	  	double c=z0.minus(z1).abs();
	  	double p=a+b+c;
	  	sc.center=z0.times(a/p).add(z1.times(b/p).add(z2.times(c/p)));
		sc.rad=.5*Math.sqrt(((a+b-c)*(a+c-b)*(b+c-a))/p);
		return sc;
	}
	
	/**
	 * Return 'inradius' s (radius of inscribed circle) for eucl triangle
	 * given radii. Given edge lengths a, b, c, use 
	 *    (1/2)*s*(a+b+c)=area=sqrt((a+b+c)*(a+b-c)*(a+c-b)*(b+c-a))/4.0
	 * Formula is actually easier with radii (if this is tangency circle
	 * triangle). s=sqrt((r0*r1*r2)/(r0+r1+r2)). 
	 * @param a double
	 * @param b double
	 * @param c double, are edge lengths
	 * @return double
	 */
	public static double eucl_tri_inradius(double a,double b,double c) {
		return (.5*Math.sqrt(((a+b-c)*(a+c-b)*(b+c-a))/(a+b+c)));
	}
	
	/**
	 * Given two circles that are supposed to be tangent, find the 
	 * tangency point on the geodesic between them. Actually, return
	 * pt with distances from z1, z2 having proportions r1, r2.
	 * @param z1 Complex
	 * @param z2 Complex, circle centers
	 * @param r1 double
	 * @param r2 double, radii
	 * @return new Complex, null on error
	 */
	public static Complex eucl_tangency(Complex z1,Complex z2,double r1,double r2) {
		Complex zvec=z2.minus(z1);
		return z1.add(zvec.times(r1/(r1+r2)));
	}

	/**
	 * Given centers/radii of two circles and rad of third, and 
	 * inv distances oj of edge <j,j+1>, find eucl center of circle 3.
	 * Use lengths of a, b, c of sides, with a the length between
	 * known centers. Get angle theta between sides a and c by law of
	 * cosines.
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param r0 double
	 * @param r1 double
	 * @param r2 double
	 * @param ivd0 double
	 * @param ivd1 double
	 * @param ivd2 double
	 * @return CircleSimple
	 */
	public static CircleSimple e_compcenter(Complex z0,Complex z1,double r0,
			double r1,double r2,double ivd0,double ivd1,double ivd2) {
		// squared lengths of sides
		double l0 = r0 * r0 + r1 * r1 + 2 * r0 * r1 * ivd0; // l0=a^2
		double l1 = r1 * r1 + (r2) * (r2) + 2 * r1 * (r2) * ivd1; // l1=b^2
		double l2 = r0 * r0 + (r2) * (r2) + 2 * r0 * (r2) * ivd2; // l2=c^2
		 
		double ld = 0.5 * (l2 + l0 - l1); // a c cos(theta)=(c^2+a^2-b^2)/2
		if ((ld * ld) > (l2 * l0))
			return new CircleSimple(false);

		// drop perp to side a, get base, height G, H 
		double B = ld / Math.sqrt(l0); // v.x= c cos(theta)
		double H = Math.sqrt(l2 - B*B);
		Complex v = new Complex(B,H);
		
		// if a were laid out along x-axis, v is vector to center
		Complex w = z1.sub(z0);
		double s = w.abs();
		if (s < 1.0e-30)
			return new CircleSimple(false);
		w = w.times(1 / s);
		// answer is z0+w*v
		Complex z=new Complex((z0.x + w.x * v.x - w.y * v.y),
				(z0.y + w.x * v.y + w.y * v.x));
		return new CircleSimple(z, r2, 1);
	}
	
	/**
	 * Given two centers and three euclidean radii, compute third center. 
	 * This is for tangency case. The 'CircleSimple' class is
	 * simply for transferring data back.
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param e1 double
	 * @param e2 double
	 * @param e3 double
	 * @return CircleSimple
	*/
	public static CircleSimple e_compcenter(Complex z1,Complex z2,double e1,
			double e2,double e3) {
		return e_compcenter(z1,z2,e1,e2,e3,1.0,1.0,1.0);
	}
	
	/** 
	 * Compute inversive distance (or cos(overlap)) between two eucl 
	 * circles. Return negative value if overlap more than PI/2. 
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r1 double
	 * @param r2 double
	 * @return double, default 1.0 (tangency) if situation not yet covered. 
	*/
	public static double inv_dist(Complex z1,Complex z2,double r1,double r2) {
	  if (r1>0 && r2>0) {
	      return ((z1.minus(z2).absSq()-(r1*r1+r2*r2))/(2.0*r1*r2));
	  }
	  // TODO: need to handle: one plane, r=0, or one negative r 
	  return 1.0;
	}

	/** 
	 * Compute quasiconformal dilatation of barycentric map between euclidean 
	 * triangles with edge lengths <A,B,C> in domain and <a,b,c> (corresponding) 
	 * in range.
	 * @param A double
	 * @param B double
	 * @param C double, domain
	 * @param a double
	 * @param b double
	 * @param c double, range
	 * @return double, dilatation, negative if one or both triangles are 
	 * degenerate or other error.
	 */
	public static double e_dilatation(double A,double B,double C,
					  double a,double b,double c) {
	  double AB2,ab2;
	  double okerr=OKERR;

	  if ((AB2=2.0*A*B)<okerr || (ab2=2.0*a*b)<okerr
	      || C<okerr || c<okerr
	      || (A>=(B+C+okerr) && B>=(A+C+okerr) && C>=(A+B+okerr))
	      || (a>=(b+c+okerr) && b>=(a+c+okerr) && c>=(a+b+okerr))
	      ) return -1.0;

	  double cos1=(A*A+B*B-C*C)/AB2; // cos(theta) 
	  double cos2=(a*a+b*b-c*c)/ab2;
	  double sin1=Math.sqrt(1-cos1*cos1);
	  double sin2=Math.sqrt(1-cos2*cos2);
	  double x=B*cos1;
	  double y=1.0/(B*sin1);
	  double u=b*cos2;
	  double v=b*sin2;
	  return affine_dilatation(a/A, (u-(a/A)*x)*y,(double) 0.0, v*y);
	}
	

	/** 
	 * Compute the real dilatation of affine transformation 
	 * for 2x2 real matra [a b;c d]. 
	 * @param a double
	 * @param b double
	 * @param c double
	 * @param d double
	 * @return double, dilatation, negative on error. 
	*/
	public static double affine_dilatation(double a,double b,double c,
					       double d) {
	  double det;
	  if ((det=(a*d-b*c))<OKERR) return -1.0;
	  double Q=(a*a+b*b+c*c+d*d)/((2.0)*det);
	  // TODO: need error check here, but for now (10/07), do this
	  double ans=Q*Q-1.0;
	  if (ans<=0.0) return 1.0;
	  return Q+Math.sqrt(Q*Q-1.0);
	}
	
	/** 
	 * distance in three space from (x,y,z) to (X,Y,Z)
	 * @param xyz Point3D
	 * @param XYZ Point3D
	 * @return double 
	*/
	public static double xyz_dist(Point3D xyz,Point3D XYZ) {
	  return (Math.sqrt((xyz.x-XYZ.x)*(xyz.x-XYZ.x)+
		       (xyz.y-XYZ.y)*(xyz.y-XYZ.y)+
		       (xyz.z-XYZ.z)*(xyz.z-XYZ.z)));
	}

	/** 
	 * Euclidean circles determining 2 faces, with radii r1...r4
	 * counterclockwise, r1, r3 at ends of common edge and with 
	 * overlaps, 'ivd_common' for common edge, 'ivdj' for edge 
	 * <r_j, r_{j+1}>. Compute cross-ratio of circle centers.
	 * @param r1 double
	 * @param r2 double
	 * @param r3 double
	 * @param r4 double
	 * @param ivd1 double
	 * @param ivd2 double
	 * @param ivd3 double
	 * @param ivd4 double
	 * @param ivd_common double
	 * @return Complex, cross ratio: (z1-z2)(z3-z4)/((z1-z4)(z3-z2)). 
	*/
	public static Complex quad_cross_ratio(
			double r1,double r2,double r3,double r4,
			double ivd1,double ivd2,double ivd3,double ivd4,
			double ivd_common) {

	  // compute edge lengths using radii/overlaps 
	  double L5=r1*r1+r3*r3+2*r1*r3*ivd_common;
	  double l5=Math.sqrt(L5);
	  double L1=r1*r1+r2*r2+2*r1*r2*ivd1;
	  double L2=r2*r2+r3*r3+2*r2*r3*ivd2;
	  double L3=r3*r3+r4*r4+2*r3*r4*ivd3;
	  double L4=r4*r4+r1*r1+2*r1*r4*ivd4;

	  Complex z1=new Complex(0.0);
	  Complex z3=new Complex(l5);
	  double l1c=(L1+L5-L2)/(2.0*l5);
	  Complex z2=new Complex(l1c,-Math.sqrt(L1-l1c*l1c));
	  double l4c=(L4+L5-L3)/(2.0*l5);
	  Complex z4=new Complex(l4c,Math.sqrt(L4-l4c*l4c));
	  Complex num1=z1.minus(z2).divide(z1.minus(z4));
	  Complex num2=z3.minus(z4).divide(z3.minus(z2));
	  return num1.times(num2);
	} 

	/**
	 * Return the eucl distance from z to w
	 * @param z Complex
	 * @param w Complex
	 * @return double
	 */
	public static double e_dist(Complex z,Complex w) {
		return Math.sqrt(Math.pow(z.x-w.x, 2)+Math.pow(z.y-w.y, 2));
	}

	/** 
	 * Return eucl length l of edge between centers of eucl
	 * circles of radii r1, r2, and inv dist 'ivd'. 
	 * Then l*l=r1*r1+r2*r2+2*r1*r2*ivd;
	 * @param r1 double
	 * @param r2 double
	 * @param ivd double
	 * @return double
	*/
	public static double e_ivd_length(double r1,double r2,double ivd) {
		return (Math.sqrt(r1*r1+r2*r2+2.0*r1*r2*ivd));
	}
	
	/** 
	 * Find the distance of the point z to the eucl line through z1, z2.
	 * @param z Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return double, -1 on error. 
	*/
	public static double dist_to_line(Complex z,Complex z1, Complex z2) {
	    Complex base=z1.minus(z2);
	    double dist=base.abs();
	    if (dist<OKERR) return -1.0; // base too small for accuracy
	    Complex vec=z1.minus(z);
	    if (vec.abs()<OKERR || z.minus(z2).abs()<OKERR) return 0.0;
	    // formula based on vector cross-product: |vec x base|/|base|
	    return (Math.abs(vec.x*base.y-vec.y*base.x)/dist);
	} 
	
	/**
	 * Find distance from z to a path. (See 'PathUtil.gpDistance'
	 * for (signed) distance to Path2D.Double.)
	 * @param z Complex z
	 * @param plist Vector<Complex>
	 * @return double
	 */
	public static double dist_to_path(Complex z,Vector<Complex> plist) {
	    double mindist=1000000.0;
	    double ndist;
		
	    if (plist==null) return 0.0;
	    Iterator<Complex> plst=plist.iterator();

	    Complex z2=new Complex((Complex)plst.next());
	    while (plst.hasNext()) {
		Complex z1=new Complex(z2);
		z2=new Complex((Complex)plst.next());
		ndist=dist_to_segment(z,z1,z2);
		mindist=(ndist<mindist) ? ndist : mindist;
	    }
	    return mindist;
	} 
	
	/**
	 * Eucl distance from 'z' to euclidean segment [z1,z2]; 
	 * compare distance to line and to ends.
	 * @param z Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return double 
	*/
	public static double dist_to_segment(Complex z,Complex z1, Complex z2) {
		double toline=dist_to_line(z,z1,z2);
		Complex w1=z.minus(z1);
		Complex w2=z.minus(z2);
		Complex w=z1.minus(z2); // dirction of line
		double d1=w1.abs();
		double d2=w2.abs();
		double minm=d1;
		minm = (d2<d1) ? d2 : d1;
		if (minm<OKERR) return 0.0; // z essentially equals z1 or z2
		// check dot products to see if ends are in different directions;
		// if yes, then closest point is in between, else is closest endpoint
		if ((w1.x*w.x+w1.y*w.y)*(w2.x*w.x+w2.y*w.y)<=0.0) return toline;
		return minm;
	}
	
	/**
	 * Return the point on segment [z1,z2] which is closest to z; may
	 * be a projection to interior point or may be an end point.
	 * @param pt Point3D
	 * @param z1 Point3D
	 * @param z2 Point3D
	 * @return Point3D, 
	 */
	public static Point3D proj_to_seg(Point3D pt,Point3D z1,Point3D z2) {
		Point3D base=new Point3D(z2.x-z1.x,z2.y-z1.y,z2.z-z1.z);
		if (base.norm()<OKERR) // z1 and z2 are too close together
			return new Point3D(z1);
		Point3D z1z=new Point3D(pt.x-z1.x,pt.y-z1.y,pt.z-z1.z);
		Point3D proj=Point3D.proj_vector(z1z, base);
		if (Point3D.DotProduct(proj,base)<=0.0) // z1 is closest
			return new Point3D(z1);
		if (proj.normSq()>base.normSq()) // z2 is closest
			return new Point3D(z2);
		return new Point3D(proj.x+base.x,proj.y+base.y,proj.z+base.z);
	}	

	/**
	 * Return the point on segment [z1,z2] which is closest to z
	 * @param pt Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return Complex, null on error
	 */
	public static Complex proj_to_seg(Complex pt,Complex z1,Complex z2) {
		Point3D pt3=new Point3D(pt.x,pt.y,0.0);
		Point3D z13=new Point3D(z1.x,z1.y,0.0);
		Point3D z23=new Point3D(z2.x,z2.y,0.0);
		Point3D proj=proj_to_seg(pt3,z13,z23);
		if (proj==null)
			return null;
		return new Complex(proj.x,proj.y);
	}
	
	/**
	 * The 'effective' eucl radii of a eucl triangulation is computed
	 * by methods of Gerald Orick. We might add more options later.
	 * Note: this just uses the centers, so it applies even if the
	 * radii don't form a packing. 'radii' must be created in calling
	 * routine: if null, radii recorded directly in p.rData.
	 * @param p PackData
	 * @param radii double[], if null, use radii from p
	 * @return 1
	 */
	public static int effectiveRad(PackData p,double []radii) {
		for (int v=1;v<=p.nodeCount;v++) {
			// for each node find the area sum and angle sum
			Complex z=p.getCenter(v);

			double thsum = 0.0;
			double asum = 0.0;
			int num=p.countFaces(v);
			int[] flower=p.packDCEL.vertices[v].getFlower(true);
			for (int j=0;j<num;j++) {
				Complex spoke0 = 
						z.minus(p.getCenter(flower[j]));
				Complex spoke1 = 
						z.minus(p.getCenter(flower[j+1]));
				Complex farside = (p.getCenter(flower[j])).
					minus(p.getCenter(flower[j+1]));
				double a, b, c;
				a = spoke0.abs(); b = farside.abs(); c = spoke1.abs();
				double facerad = (a+c-b)/2.0;
				double th = Math.acos((a*a+c*c-b*b)/(2*a*c));
				thsum += th;
				asum += facerad*facerad*th/2.0;
			}
			if (radii==null)
				p.setRadius(v,Math.sqrt(asum/(thsum/2.0)));
			else radii[v]= Math.sqrt(asum/(thsum/2.0));
		}
		return 1;
	}
	
	/**
	 * return the 3D point in triangle 'p0','p1','p2' using barycentric
	 * coordinates of 'bp'. Can be used for plane points (third coord 0), 
	 * but mainly intended for working with barycentric coords in hyp 
	 * and sph geometry.
	 * @param bp BaryPoint
	 * @param p0 Point3D
	 * @param p1 Point3D
	 * @param p2 Point3D
	 * @return Point3D
	 */
	public static Point3D getSpacePoint(BaryPoint bp,
			Point3D p0,Point3D p1,Point3D p2) {
		return new Point3D(
				bp.b0*p0.x+bp.b1*p1.x+bp.b2*p2.x,
				bp.b0*p0.y+bp.b1*p1.y+bp.b2*p2.y,
				bp.b0*p0.z+bp.b1*p1.z+bp.b2*p2.z);
	}
	
	/** 
	 * Normalizes eucl data of pack p by putting point a at origin 
	 * and g (if not too close to a) on the positive y-axis.
	 * @param p PackDCEL
	 * @param a Complex (usually, a=alpha center)
	 * @param g Complex (usually, g=gamma center)
	 * @return the Mobius applied
	*/
	public static Mobius e_norm_pack(PackDCEL pdcel,Complex a,Complex g) {
		Mobius mob=Mobius.mobNormPlane(a,g);
		if (Mobius.frobeniusNorm(mob)>Mobius.MOB_TOLER) {
			// directly adjust in 'Vertex'
			for (int v = 1; v <= pdcel.vertCount; v++) {
				Complex z = pdcel.vertices[v].center;
				pdcel.vertices[v].center = mob.apply(z);
			}
			// directly adjust in red chain
			if (pdcel.redChain != null) {
				RedEdge rtrace = pdcel.redChain;
				do {
					rtrace.setCenter(mob.apply(rtrace.getCenter()));
					rtrace = rtrace.nextRed;
				} while (rtrace != pdcel.redChain);
			}
		}
		return mob;
	} 
	
	/** 
	 * In eucl geometry, tangency triple, compute derivative
	 * for angle at x w.r.t radius x.
	 * @param x radius
	 * @param y radius
	 * @param z radius
	 * @return double
	*/
	public static double Fx(double x,double y,double z) {
	  double a=x*(x+y+z);
	  double b=y*z;
	  return ( (-b)*(2*x+y+z)/((a+b)*Math.sqrt(a*b)) );
	}
		
	/** 
	 * In eucl geometry, tangency triple, compute derivative
	 * for angle at x w.r.t radius y.
	 * @param x radius
	 * @param y radius
	 * @param z radius
	 * @return double
	*/
	public static double Fy(double x,double y,double z) {
	  double a=x*(x+y+z); 
	  double b=y*z;
	  return (x*y*(x+y)/((a+b)*Math.sqrt(a*b)));
	} 

	/** 
	 * In eucl geometry, tangency triple, compute derivative
	 * for angle at x w.r.t radius z.
	 * @param x radius
	 * @param y radius
	 * @param z radius
	 * @return double
	 */
	public static double Fz(double x,double y,double z) {
	  return (Fy(x,z,y));
	} 
	
	/**
	 * Return 'true' if {z1,z2,z3} form a positively oriented
	 * triangle (ie. counterclockwise).
	 * TODO: doesn't work for spherical points yet.
	 */
	public static boolean ccWise(Complex z1,Complex z2,Complex z3) {
		// w=(z3-z2)/(z2-z1)
		Complex w=z3.minus(z2).divide(z2.minus(z1));
		if (w.y>0) return true;
		return false;
	}
	
	/**
	 * Data here is for a quad with eucl corners {vz, rz, wz,lz},
	 * with {vz,vw} being an edge. Return the 4 angles of the quad
	 * @param vz Complex
	 * @param rz Complex
	 * @param wz Complex
	 * @param lz Complex, (counterclockwise corners)
	 * @return double[4], angles at vz,rz,wz,lz (resp.) 
	 */
	public static double []QuadAngles(Complex vz,
			Complex rz,Complex wz,Complex lz) {
		double []angles=new double[4];
		double edg=vz.minus(wz).abs();
		double lv_edge=lz.minus(vz).abs();
		double lw_edge=lz.minus(wz).abs();
		double rv_edge=rz.minus(vz).abs();
		double rw_edge=rz.minus(wz).abs();
	
		// angles at v, left/right
		double al=Math.acos((lv_edge*lv_edge+edg*edg-lw_edge*lw_edge)/(2.0*lv_edge*edg));
		double ar=Math.acos((rv_edge*rv_edge+edg*edg-rw_edge*rw_edge)/(2.0*rv_edge*edg));
		angles[0]=al+ar;
		
		// angles at w, left/ right
		al=Math.acos((lw_edge*lw_edge+edg*edg-lv_edge*lv_edge)/(2.0*lw_edge*edg));
		ar=Math.acos((rw_edge*rw_edge+edg*edg-rv_edge*rv_edge)/(2.0*rw_edge*edg));
		angles[2]=al+ar;

		angles[1]=Math.acos((rw_edge*rw_edge+rv_edge*rv_edge-edg*edg)/(2.0*rw_edge*rv_edge));
		angles[3]=Math.acos((lw_edge*lw_edge+lv_edge*lv_edge-edg*edg)/(2.0*lw_edge*lv_edge));

		return angles;
	}
	
}

