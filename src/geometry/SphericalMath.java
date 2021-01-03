package geometry;
import math.Point3D;
import packing.PackData;
import util.UtilPacket;
import allMains.CPBase;
import baryStuff.BaryPoint;
import complex.Complex;

/** 
 * The unit (Riemann) sphere is x^2 + y^2 + z^2 = 1 in 3-space. 
 * Spherical points are stored as complex numbers in usual polar
 * form (theta,phi); radii are spherical, measured in radians. 
 * To get the correct orientation on the sphere when viewed from 
 * outside, we project from the SOUTH pole (unlike the typical 
 * complex analysis definition projecting from the NORTH pole). 
 * (So a + oriented triple in the plane is + oriented when 
 * looked at from OUTSIDE the sphere).
 * 
 * So for us, the origin is N and infinity is S, the SOUTH pole.
 * Under our stereographic projection, points (u,v) in the plane 
 * and (x,y,z) on the sphere are related by:
 *     u=x/(1+z); v=y/(1=z)
 *     z=(1-(u*u+v*v))/(1+(u*u=v*v)); x=u(1+z); y=v(1+z).
 *     
 * Inversive distance: if spherical circles c1 c2 are radius r1, r2, 
 * and their centers are spherical distance d apart, then 
 *    delta(c1,c2)=(cos(d)-cos(r1)*cos(r2))/(sin(r1)*sin(r2)) 
 * 
 * See also SphereLayout.java. 
 * TODO: in the long run, using hyperboloid model and Minkowski space
 * may have advantages. There the sphere is the boundary of H^3 and
 * we could use "geometric algebra" both for the hyperbolic plane and
 * the Riemann sphere. 
*/
public class SphericalMath{
  public static final double S_TOLER = .0000000000001;
  public static final int INITIAL_CAPACITY = 100;
  
  /**
   * Find inversive distance on sphere between circles.
   * @param z1 Complex, (theta,phi) center
   * @param z2 Complex, (theta,phi) center
   * @param r1 double, sph rad
   * @param r2 double, sph rad
   * @return double
   */
  public static double s_inv_dist(Complex z1,Complex z2,double r1,double r2) {
	  double d=s_dist(z1,z2);
	  return (Math.cos(d)-Math.cos(r1)*Math.cos(r2))/(Math.sin(r1)*Math.sin(r2));
  }
  
  /** 
	 * Return sph length of edge with spherical radii r1, r2, and inv dist 'ovlap'.
	 * For two circles, 'inv_dist'=(-cos(phi)+cos(r1)*cos(r2))/(sin(r1)*sin(r2)),
	 * where phi is the angle between the unit vectors p1, p2 to the centers of the
	 * two circles. That is, phi=dot(p1,p2).
	 * @param r1 double 
	 * @param r2 double
	 * @param inv_dict double
	 * @return double  
	*/
  public static double s_invdist_length(double r1,double r2,double inv_dist) {
	  double cosphi=Math.cos(r1)*Math.cos(r2)-inv_dist*Math.sin(r1)*Math.sin(r2);
	  return Math.acos(cosphi);
  }
  
  /** 
   * Compute cosine of angle at the first circle in spherical
   * triangle formed by triple. Increment flag on error: 
   * r1+r2+r3>M_PI or denom zero. 
   * TODO: Is this okay if r1+r2+r3 > M_PI? 
  */
  public static double s_comp_cos(double r1,double r2,double r3) {
	  double denom;
	  double sumr;

	  sumr=r1+r2+r3;
	  if (Math.abs(Math.PI-sumr)<S_TOLER) { // three centers lie on great circle
		  return -1.0;
	  }
	  if ((denom=Math.sin(r1+r2)*Math.sin(r1+r3))<S_TOLER) {
		  return denom;
	  }
	  return (Math.cos(r2+r3)-(Math.cos(r1+r2)*Math.cos(r1+r3)))/denom;
  } 
  
  /** 
   * Return area of Spherical triangle formed by mutually tangent triples
   * of circles of spherical radii r1, r2, and r3; from L'Huilier's Formula.
   * (TODO: need formulae for triples with inversive distances.)
   */
  public static double s_area(double r1,double r2,double r3) {
	  double s=(r1+r2+r3); // semi-perimeter
	  double t=Math.sqrt(Math.tan(s/2.0)*Math.tan(r1/2.0)*Math.tan(r2/2.0)*Math.tan(r3/2.0));
	  return 4.0*Math.atan(t);
  }
	
  /**
   * Find the maximum value that the radius of vert can have 
   * based on the current radii of neighbors. (Because no triple
   * can have radii summing to more than PI.)
   * @param PackData p
   * @param vert
   * @return
   */
  public static double sph_rad_max(PackData p,int vert) {
		if (vert<1 || vert>p.nodeCount) return 0.0; // error
		double mx=0.0;
		int num=p.getNum(vert);
		for (int j=0;j<num;j++) {
			double sum =p.getRadius(p.kData[vert].flower[j])
				+p.getRadius(p.kData[vert].flower[j+1]);
			mx = (sum>mx) ? sum : mx;
		}
		return Math.PI-mx;
	}
	
  /**
   * Find "incircle", sph center/radius of circle inscribed in 
   * trianglular face with given corners.
   * ASSUME the three are mutually tangent. Find the 3D eucl circle
   * through the points of tangency. For center may have to consider
   * orientation for large face cases.
   * @param z1,z2,z3, Complex, sph centers
   * @return CircleSimple
   */
	public static CircleSimple sph_tri_incircle(Complex z1,Complex z2,Complex z3) {

		// edge lengths
		double a=s_dist(z3,z2);
		double b=s_dist(z1,z3);
		double c=s_dist(z1,z2);
		
		// putative radii (based just on edge lengths)
		double r1=(b+c-a)/2.0;
		double r2=(a+c-b)/2.0;
		double r3=(a+b-c)/2.0;
		
		// pts of tangency 
		Complex T12=sph_tangency(z1,z2,r1,r2);
		Complex T23=sph_tangency(z2,z3,r2,r3);
		Complex T31=sph_tangency(z3,z1,r3,r1);
		
		return circle_3_sph(T12,T23,T31);
	}
	
	/** 
	 * Given three points on the sph, find the sph center/rad
	 * for the circle containing them; choose center to get
	 * proper orientation {A, B, C}.
	 * @param A, B, C, Complex sph points
	 * @return CircleSimple with spherical data
	 */
	public static CircleSimple circle_3_sph(Complex A,Complex B,Complex C) {
		// 3D points
		Point3D a=new Point3D(A);
		Point3D b=new Point3D(B);
		Point3D c=new Point3D(C);

		// use 3D edges to get o.n. {X,Y,Z} system: triangle in X,Y-plane
		Point3D AB = new Point3D(b.x-a.x,b.y-a.y,b.z-a.z);
		Point3D X=AB.divide(AB.norm()); 
		Point3D AC = new Point3D(c.x-a.x,c.y-a.y,c.z-a.z);
		Point3D Z= Point3D.CrossProduct(X,AC);
		Z=Z.divide(Z.norm());
//		Point3D BC = new Point3D(c.x-b.x,c.y-b.y,c.z-b.z);
		Point3D Y= Point3D.CrossProduct(Z,X);
		
		// XYZ-coords: point z1 = {0,0,0}, z2={|AB|,0,0}, z3={(AC,X),(AC,Y),0}
		Complex z1=new Complex(0.0);
		Complex z2=new Complex(AB.norm());
		Complex z3=new Complex(Point3D.DotProduct(AC,X),Point3D.DotProduct(AC,Y));
		CircleSimple sc=EuclMath.circle_3(z1, z2, z3);
		Complex cent=sc.center;
		Point3D affineCenter= Point3D.vectorSum(a,
				Point3D.vectorSum(X.times(cent.x),Y.times(cent.y)));
		
		// results:
		CircleSimple resultSC=new CircleSimple();
		resultSC.center=Z.getAsSphPoint();
		// ====== Consider situations:
		// essentially at origin?
		if (affineCenter.norm()<.001) { 
			resultSC.rad=Math.PI/2.0;
		}
		// average distances to given points
		else {
			resultSC.rad=(s_dist(resultSC.center,A)+s_dist(resultSC.center,B)+
				s_dist(resultSC.center,C))/3.0;
			if (Point3D.DotProduct(Z,affineCenter)<0) {
				resultSC.rad = Math.PI-resultSC.rad;
			}
		}
		return resultSC;
	}
	
	/** 
	 * Given three points on the sph, find the sph center/rad
	 * for the circle containing them; choose center to get
	 * proper orientation {A, B, C}.
	 * @param A Complex; sph points
	 * @param B Complex
	 * @param C Complex
	 * @return CircleSimple with spherical data
	 */
	public static CircleSimple circle_3_sph_planB(Complex A,Complex B,Complex C) {
		// 3D points
		Point3D a=new Point3D(A);
		Point3D b=new Point3D(B);
		Point3D c=new Point3D(C);

		// use 3D edges to get o.n. {X,Y,Z} system: triangle in X,Y-plane
		Point3D AB = new Point3D(b.x-a.x,b.y-a.y,b.z-a.z);
		Point3D AC = new Point3D(c.x-a.x,c.y-a.y,c.z-a.z);
		Point3D Z= Point3D.CrossProduct(AB,AC);
		Complex sz=proj_vec_to_sph(Z);
		double sr=s_dist(sz,A);
		
		return new CircleSimple(sz,sr,1);
	}
	
  /**
   * Spherical distance between two spherical (i.e., (theta,phi)) points
   * @param z Complex
   * @param w Complex
   * @return double
   */
  public static double s_dist(Complex z, Complex w){
    double[] v1, v2;
    double dotprod;

    if((Math.abs(z.x-w.x) < S_TOLER) && (Math.abs(z.y-w.y) < S_TOLER))
      return (0.0);

    v1 = s_pt_to_vec(z);
    v2 = s_pt_to_vec(w);
    dotprod = dot_prod(v1, v2);
    if(Math.abs(dotprod) > (1.0 - S_TOLER))
      return Math.PI;

    return Math.acos(dotprod);
  }
  
  /** 
   * Stereographic projection of complex number to complex spherical point, 
   * form (theta,phi). IMPORTANT: note that we project so zero goes to North pole,
   * infinity to South.
   * @param z Complex
   * @return new Complex, (theta,phi)
   */
  public static Complex proj_pt_to_sph(Complex z) {
	  double zs=z.absSq();
	  if (zs<.00000000001) return new Complex(0.0);
	  return new Complex(Math.atan2(z.y,z.x),Math.acos((1-zs)/(1+zs)));
  }

  /**
   * Find distance from spherical point to geodesic between two
   * spherical points.
   */
  public static double s_dist_pt_to_line(Complex z,Complex end1,Complex end2) {
  	double []A;
  	double []B;
  	double []C;
  	double []AxB;
  	double []CxAxB;
  	A=s_pt_to_vec(end1);
  	B=s_pt_to_vec(end2);
  	C=s_pt_to_vec(z);
  	AxB=crossProduct(A,B); 
  	CxAxB=crossProduct(C,AxB);
  	double p=(AxB[0]*AxB[0]+AxB[1]*AxB[1]+AxB[2]*AxB[2]);
  	double d=(CxAxB[0]*CxAxB[0]+CxAxB[1]*CxAxB[1]+CxAxB[2]*CxAxB[2]);
  	// d=p*Math.sin(theta)
  	return Math.PI/2.0-Math.asin(Math.sqrt(d/p)); // distance is Pi/2-theta.
  }

  /**
   * Converts a (theta,phi) point on sphere to unit vector (x,y,z) 
   * of doubles. If phi=0.0, then get (0,0,1); if phi=Pi, get (0,0,-1);
   * @param sph_z Complex, spherical coords (theta,phi)
   * @return double[3]
   */
  public static double[] s_pt_to_vec(Complex sph_z){
    double[] V = new double[3];
    double s=Math.sin(sph_z.y);
    V[0] = s * Math.cos(sph_z.x);
    V[1] = s * Math.sin(sph_z.x);
    V[2] = Math.cos(sph_z.y);

    return V;
  }
  
  /**
   * compute the eucl distance in 3D between to (theta,phi)
   * points on the sphere.
   * @param z Complex
   * @param w Complex
   * @return double
   */
  public static double eucl_dist3D(Complex z,Complex w) {
	  double []pt0=SphericalMath.s_pt_to_vec(z);
	  double []pt1=SphericalMath.s_pt_to_vec(w);
	  return (Math.sqrt((pt1[0]-pt0[0])*(pt1[0]-pt0[0]) +
			  (pt1[1]-pt0[1])*(pt1[1]-pt0[1]) +
			  (pt1[2]-pt0[2])*(pt1[2]-pt0[2])));
  }
  
  /**
   * Return new Complex (theta,phi) representing projection of given Point3D
   * to the unit sphere; recall, origin goes to NORTH pole.
   * @param p3d Point3D
   * @return sph coords (theta,phi) 
   */
  public static Complex proj_vec_to_sph(Point3D p3d) {
	  return proj_vec_to_sph(p3d.x,p3d.y,p3d.z);
  } 
  
  /**
   * Return new Complex (theta,phi) representing projection of given 3D vector
   * (x,y,z) to the unit sphere; recall, origin goes to NORTH pole.
   * @param x double
   * @param y double
   * @param z double
   * @return sph coords (theta,phi), default to N if vector norm is too small.
   */
  public static Complex proj_vec_to_sph(double x,double y,double z) {
	  double dist;

	  // default for things near origin 
	  if ((dist=Math.sqrt(x*x+y*y+z*z))< S_TOLER) {
		  return new Complex(0.0);
	  }
	  return new Complex(Math.atan2(y,x),Math.acos(z/dist));	
  } 
  
/**
 * Given sph point z=(theta,phi)), return new Complex (y,z) on visual plane. 
 *  return null if on back. 
 * @param z=(theta,phi)
 * @return new Complex (y,z) on visual plane
 */
public static Complex sphToVisualPlane(Complex z) {
	return sphToVisualPlane(z.x,z.y);
}

/**
 *  Given (theta,phi), return new Complex (y,z) on visual plane. 
 *  return null if on back.
 *  @param theta,phi spherical point
 *  @return new Complex (y,z) on visual plane 
 */
public static Complex sphToVisualPlane(double theta,double phi) {
	return new Complex(Math.sin(phi)*Math.sin(theta),Math.cos(phi)); 
}

/**
 * Dot product of 3-vectors
 * @param V double[3]
 * @param W double[3]
 * @return double
 */
public static double dot_prod(double V[], double W[]){
    return (V[0] * W[0] + V[1] * W[1] + V[2] * W[2]);
  }

/**
 * Length of real 3-vector.
 * @param X
 * @return
 */
public static double vec_norm(double X[]){
    return(Math.sqrt(X[0] * X[0] + X[1] * X[1] + X[2] * X[2]));
  }

//class Vector2D {
//    double y;
//    double z;
//  }

  // (OBE) Note: simple computation for control point for
  // drawing arc of great circle between two points.
  // 
  // The quadratic curve is formed so the lines from 
  //   the control point to the two ends are tangent
  //   to the curve.
  //
  // Only difficulty is if points are antipodal: return
  // controlpoint as the origin; may eventually need a flag
  // to indicate this.
  
  public static Point3D computeControl(Point3D A,Point3D B) {
	  double AdotB=Point3D.DotProduct(A,B);
	  if (AdotB<-.9999999) { // antipodal
		  return new Point3D(0.0,0.0,0.0);
	  }
	  return Point3D.vectorSum(A,B).times(1/(1+AdotB));
  }
  
  public static double[] crossProduct(double X[], double Y[]) {
    double[] Z = new double[3];
    Z[0] = X[1] * Y[2] - X[2] * Y[1];
    Z[1] = X[2] * Y[0] - X[0] * Y[2];
    Z[2] = X[0] * Y[1] - X[1] * Y[0];
    return (Z);
  }
  
  /**
   * Given two sph circles which are (supposed to be) tangent, find the 
   * tangency point on the geodesic between them. Actually, returns
   * pt with distances from z1, z2 having proportions r1, r2.
   * If z1 and z2 essentially antipodal, then result is numerically
   * unstable. If r1+r2 > Pi, then have to distinguish which way to
   * go from z1 toward z2.
   * @param z1 Complex (theta,phi)
   * @param z2 Complex (theta,phi)
   * @param r1 double
   * @param r2 double
   * @return new Complex, (theta,phi), null on error
   */
  public static Complex sph_tangency(Complex z1,Complex z2,double r1,double r2) {
	  double dratio=(s_dist(z1,z2))*r1/(r1+r2);
	  // try first direction
	  Point3D T3=new Point3D(sph_tangent(z1,z2));
	  Complex tp1=sph_shoot(z1,T3,dratio);
	  double err1=Math.abs(s_dist(z1,tp1)-r1);
	  if (err1<S_TOLER) // looks good 
		  return tp1;
	  
	  // try opposite direction
	  T3=T3.times(-1.0); // flip tangent
	  Complex tp2=sph_shoot(z1,T3,dratio);
	  double err2=Math.abs(s_dist(z1,tp2)-r1);
	  if (err2<S_TOLER) // looks good 
		  return tp2;
	  
	  // else, take the one with the smallest error
	  if (err1<=err2)
		  return tp1;
	  return tp2;
  }

  /** 
   * Find center of third circle in ordered tangent triple. Note: 
   * orientation is counterclockwise looking at sphere from outside. 
   * Overlaps not yet used, but flag is there to parallel other geoms..
  */ 
  public static CircleSimple s_compcenter(Complex z1,Complex z2,
  		double r1,double r2,double r3,double o1,double o2,double o3) {
    double a,b,c,angle;
    double []TV;
    double []P=new double[3];
    double []N=new double[3];
    double []mtan=new double[3];
    Complex z=new Complex(0.0);

    a=Math.sin(z1.y)*Math.cos(z1.x);
    b=Math.sin(z1.y)*Math.sin(z1.x);
    c=Math.cos(z1.y);
    // angle is how far around from T we will rotate 
    angle=Math.acos(( Math.cos(r2+r3)-Math.cos(r1+r3)*Math.cos(r1+r2) )/
  	     ( Math.sin(r1+r3)*Math.sin(r1+r2) ));
    // TV is a tangent vector at z1
    TV=sph_tangent(z1,z2);
    // N is T x z1 
    N[0]=b*TV[2]-c*TV[1];
    N[1]=c*TV[0]-a*TV[2];
    N[2]=a*TV[1]-b*TV[0];
    // P will point toward the new center 
    P[0]=Math.cos(angle)*TV[0]+Math.sin(angle)*N[0];
    P[1]=Math.cos(angle)*TV[1]+Math.sin(angle)*N[1];
    P[2]=Math.cos(angle)*TV[2]+Math.sin(angle)*N[2];
    mtan[0]=Math.cos(r1+r3)*a+Math.sin(r1+r3)*P[0];
    mtan[1]=Math.cos(r1+r3)*b+Math.sin(r1+r3)*P[1];
    mtan[2]=Math.cos(r1+r3)*c+Math.sin(r1+r3)*P[2];
    if(mtan[2]<=(1.0-S_TOLER)) {
    	z=new Complex(Math.atan2(mtan[1],mtan[0]),Math.acos(mtan[2]));
    }
    return new CircleSimple(z,r3,1);
  }
  
  public static CircleSimple s_compcenter(Complex z1,Complex z2,
	  		double r1,double r2,double r3) {
	  	return s_compcenter(z1,z2,r1,r2,r3,1.0,1.0,1.0);
  }
  
  /**
   * Given z, w on sphere, return the sph point which is distance 'dist'
   * (in radians) from z in direction of w. 
   * @param ctr1 (theta,phi)
   * @param ctr2 (theta,phi)
   * @param dist double (radians)
   * @return new Complex
   */
  public static Complex s_shoot(Complex z,Complex w,double dist) {
	  // if very close, just return z
	  if (Math.abs(dist)<S_TOLER) return new Complex(z);
	  
	  // adjust mod 2*pi until dist lies in [0,2*pi]
	  double pi2=2.0*Math.PI;
	  while (dist<0) dist+=pi2;
	  while (dist>pi2) dist -=pi2;
	  
	  double []T=sph_tangent(z,w);
	  double []V=s_pt_to_vec(z);
	  double []A=new double[3];
	  double cosd=Math.cos(dist);
	  double sind=Math.sin(dist);
	  A[0]=cosd*V[0]+sind*T[0];
	  A[1]=cosd*V[1]+sind*T[1];
	  A[2]=cosd*V[2]+sind*T[2];
	  return proj_vec_to_sph(A[0],A[1],A[2]);
  }

  /**
   * Given sph point z, and T, unit vector tangent at z, compute
   * the sph point which is distance 'dist' (in radians) from 
   * z in direction T. 
   * @param z Complex, (theta,phi)
   * @param T Point3D, unit tangent
   * @param dist double (radians)
   * @return new Complex (theta,phi)
   */
  public static Complex sph_shoot(Complex z,Point3D T,double dist) {
	  double []V=s_pt_to_vec(z);
	  double []A=new double[3];
	  double cosd=Math.cos(dist);
	  double sind=Math.sin(dist);
	  A[0]=cosd*V[0]+sind*T.x;
	  A[1]=cosd*V[1]+sind*T.y;
	  A[2]=cosd*V[2]+sind*T.z;
	  return proj_vec_to_sph(A[0],A[1],A[2]);
  }

  /** 
   * Given pts on sphere, return ptr to unit length 3-vector in 
   * tangent space of first, pointing toward second. Result is
   * always a unit vector perp to the vector to the first point. 
   * Ambiguities: 
   *  + If pts are essentially equal or antipodal, result
   *    will be numerically unstable. 
   *  + In general, two directions point toward second point.
   *    Calling routine must judge, accepting the result here
   *    or using its negative.
   * @param ctr1 (theta,phi)
   * @param ctr2 (theta,phi)
   * @return double[3] unit vector
   */
  public static double[] sph_tangent(Complex ctr1,Complex ctr2) {
    double d,vn;
    double []A;
    double []B;
    double []P=new double[3];
    double []T=new double[3];

    A=s_pt_to_vec(ctr1);
    B=s_pt_to_vec(ctr2);
    d=dot_prod(A,B);
    // Find projection of B on plane normal to A
    P[0]=B[0]-d*A[0];  P[1]=B[1]-d*A[1];  P[2]=B[2]-d*A[2];

    // A and B essentially parallel? 
    if ((vn=vec_norm(P))<S_TOLER)
    {
    	// if A is not N or S, point in horizontal direction
    	double pn=Math.sqrt(A[0]*A[0]+A[1]*A[1]);
    	if (pn>.0000001) {
    		// get orthogonal, X coord 0
    		T[0]=A[1]/pn;
    		T[1]=-A[0]/pn;
    		T[2]=0;
    	}
    	// otherwise, point toward (0,1,0)
    	else {
    		T[0]=0.0;
    		T[1]=1.0;
    		T[2]=0.0;
    	}
        return T;
    }
    T[0]=P[0]/vn;T[1]=P[1]/vn;T[2]=P[2]/vn;
    return T;
  } 

  /**
   * Returns new Complex giving stereographic projection (recall, we
   * project from the south pole) of spherical point z to complex 
   * point w in plane. Key is |w| = sin(phi)/(1+cos(phi)).
   * 
   * If z is essentially at sorth pole, project to distance 10000 from origin.
   * @param z Complex (theta,phi)
   * @return new Complex
   */
  public static Complex s_pt_to_plane(Complex z) {
	  double cosphi=Math.cos(z.y);
	  
	  // at south pole?
	  if (cosphi<-.99999999) {
		  return new Complex(10000*Math.cos(z.x),10000*Math.sin(z.x));
	  }
	  double r=Math.sin(z.y)/(1.0+cosphi);
	  double x=r*Math.cos(z.x);
	  double y=r*Math.sin(z.x);
	  return new Complex(x,y);
  }
  
  /** 
   * Projects circles from sphere to plane, when possible. Note
   * that our 'stereographic projection' is from the south pole.
   * Circles properly enclosing infinity (south pole) gets an 
   * antipital point of z as its fake center and -(Math.PI-rad) as
   * its fake radius, indicating that the outside of the resulting 
   * eucl circle is actually our disc -- generally, calling routine
   * needs to change this negative sign. Note: we don't permanently store the
   * negative radius, so we loose track that eucl radii and center
   * are fake; the user must be sure to use this info when it's
   * available or make provisions to save it somehow. Also, a circle 
   * essentially passing through infinity will be given a small expansion
   * in radius to get euclidean data, but of course it's fake. 
   * return an error CircleSimple if setting negative radius. 
   * @param z Complex (theta,phi) center
   * @param r double spherical radius
   * @return CircleSimple
  */
  public static CircleSimple s_to_e_data(Complex z,double r) {
    int flipflag=1;
    double er; // new radius
    double []V;
    double m,RR,rr,up,down;
    Complex e; // new center

    V=s_pt_to_vec(z);
    if (Math.abs(z.y+r-Math.PI)<S_TOLER) /* essentially hit's infinity: 
  					   increment r slightly and proceed. */
      r += 2.0*S_TOLER; 
    if ((z.y+r)>=(Math.PI+S_TOLER)) { // encloses infinity 
        r=Math.PI-r;
        V[0] *= (-1.0);
        V[1] *= (-1.0);
        V[2] *= (-1.0);
        z=proj_vec_to_sph(V[0],V[1],V[2]);
        flipflag=-1;
      }
    up=z.y+r;
    down=z.y-r;
    if (Math.abs(z.y)<S_TOLER) { // essentially centered at np 
        er=Math.sin(up)/(1.0+Math.cos(up));
        e=new Complex(0.0);
        if (flipflag<0) er *= -1.0;
        return new CircleSimple(e,er,flipflag);
    }
    if (Math.abs(z.y-Math.PI)< S_TOLER) {
      /* essentially centered at sp, radius must be small since
         the circle wasn't flipped, so give it huge negative radius. */
        er=-100000;
        e=new Complex(0.0);
        return new CircleSimple(e,er,-1);
      }
    if (Math.abs(up-Math.PI)< .00001) {
      /* circle essentially passes through infinity, so decrease up
         slightly */
        up -= .00001;
      }
    RR=Math.sin(up)/(1.0+Math.cos(up));
    rr=Math.sin(down)/(1.0+Math.cos(down));
    er=Math.abs(RR-rr)/2.0;
    if (flipflag<0) er *= -1.0;
    m=(RR+rr)/2.0;
    e=new Complex(V[0]*m/Math.sin(z.y),V[1]*m/Math.sin(z.y));
    return new CircleSimple(e,er,flipflag);
  }
  		
  /** 
   * Converts circle data to the sphere in a new 'CircleSimple'.
   * Caution: projection is NOT the standard "stereographic"; see
   * comments at the beginning of this file.
   * Note that r<0 means the circle bounds the outside disc, which 
   * affects the spherical center/radius.
   * @param ez Complex eucl center
   * @param er double eucl radius
   * @return CircleSimple
  */
  public static CircleSimple e_to_s_data(Complex ez,double er) {
	  
      Complex sz=new Complex(0.0); // will be sph center
      double []P3=new double[3]; // 3D point on sphere

      double nsq=ez.absSq();
      double rr=Math.abs(er); // note, r negative handled later
      
      // if er too small, project center, set sr=er unchanged.
      if (rr<S_TOLER) {
    	  double denom=nsq+1.0;
    	  P3[0]=(2*ez.x)/denom;
    	  P3[1]=(2*ez.y)/denom;
    	  P3[2]=(2.0-denom)/denom;
    	  if(P3[2]>(1.0-S_TOLER)) { // near N pole
    		  ez.x=ez.y=0.0;
    		  return new CircleSimple(sz,er,0);
    	  }
    	  if (P3[2]<(S_TOLER-1.0)) { // near S pole
    		  sz.x=0.0;
    		  sz.y=Math.PI;
    		  return new CircleSimple(sz,er,0);
    	  }
    	  sz.y=Math.acos(P3[2]);
    	  sz.x=Math.atan2(P3[1],P3[0]);
    	  return new CircleSimple(sz,er,1); 
      }
      
      // General strategy: project 3 equally spaced points of the
      //   euclidean circle to 3 points on the sphere.
      
      Complex []epts=new Complex[3];
      epts[0]=ez.plus(rr);
      epts[1]=ez.plus(CPBase.omega3[1].times(rr));
      epts[2]=ez.plus(CPBase.omega3[2].times(rr));
      
      Complex []spts=new Complex[3];
      for (int j=0;j<3;j++) 
    	  spts[j]=proj_pt_to_sph(epts[j]);
      
      // for er<0, reverse orientation
      if (er<0) {  
    	  Complex h=spts[0];
    	  spts[0]=spts[2];
    	  spts[2]=h;
      }
      
      // find the circle through the 3 spherical points
      CircleSimple cS=circle_3_sph_planB(spts[0],spts[1],spts[2]);

      return cS;
  }
  
  /** 
   * True if sph_pt (i.e., (theta,phi)) lies in triangle with given
   * spherical points as corners for a CONVEX triangle. 
   * TODO: handle non-convex triangles
   * @param sph_pt (theta,phi)
   * @param z1 (theta,phi)
   * @param z2 (theta,phi)
   * @param z3 (theta,phi)
   * @return boolean
   */
  public static boolean pt_in_sph_tri(Complex sph_pt,Complex z1,Complex z2,Complex z3) {
  	    double []X;
	    double []Y;
	    double []Z;
	    double []P;
	    double []C;
	    P=s_pt_to_vec(sph_pt);
	    X=s_pt_to_vec(z1);
	    Y=s_pt_to_vec(z2);
	    Z=s_pt_to_vec(z3);
	    
	    // is it on wrong side of plane through X,Y,Z?
	    double []XY=new double[3];
	    XY[0]=Y[0]-X[0];
	    XY[1]=Y[1]-X[1];
	    XY[2]=Y[2]-X[2];
	    double []XZ=new double[3];
	    XZ[0]=Z[0]-X[0];
	    XZ[1]=Z[1]-X[1];
	    XZ[2]=Z[2]-X[2];
	    double []XP=new double[3];
	    XP[0]=P[0]-X[0];
	    XP[1]=P[1]-X[1];
	    XP[2]=P[2]-X[2];
	    C=crossProduct(XY,XZ);
	    // wrong direction? can't be in triangle
	    if (dot_prod(C,XP)<0.0)
	    	return false;
	    
	    double []YZ=new double[3];
	    YZ[0]=Z[0]-Y[0];
	    YZ[1]=Z[1]-Y[1];
	    YZ[2]=Z[2]-Y[2];
	    
	    // is 
	    
	    C=crossProduct(Y,X);
	    if (dot_prod(P,C)>0) return false;
	    C=crossProduct(Z,Y);
	    if (dot_prod(P,C)>0) return false;
	    C=crossProduct(X,Z);
	    if (dot_prod(P,C)>0) return false;
	    return true;
	    
  }
  
  /** 
   * Given a complex point (y,z) on the viewing screen, find the
   * corresponding spherical point (theta,phi) on the front. Return 
   * in UtilPacket. 'rtnFlag' is 0 on failure (visual point is not 
   * within unit disc). '(value,errval)' is sph point (re, im).
   * @param pt (y,z)
   * @return UtilPacket
   */
  public static UtilPacket screen_to_s_pt(Complex pt) {
    double xx=1.0 - pt.absSq();
    if (xx<0) {
        return new UtilPacket(0,0.0,0.0);
    }
    double []V=new double[3];
    V[0]=xx;
    V[1]=pt.x;
    V[2]=pt.y;
    Complex z=proj_vec_to_sph(V[0],V[1],V[2]);
    return new UtilPacket(1,z.x,z.y);
  }
  
  /** 
   * Given points on sphere, return new Complex barycenter (theta,phi)
   * of triangle they form; inside determined by orientation.
   * @param z1 (theta,phi)
   * @param z2 (theta,phi)
   * @param z3 (theta,phi)
   * @return new Complex barycenter (theta,phi)
  */
  public static Complex sph_tri_center(Complex z1,Complex z2,Complex z3) {
    double[] X,Y,Z,M,C,D;

    X=s_pt_to_vec(z1);
    Y=s_pt_to_vec(z2);
    Z=s_pt_to_vec(z3);
    
    // centroid
    M=new double[3];
    M[0]=(X[0]+Y[0]+Z[0])/3.0;
    M[1]=(X[1]+Y[1]+Z[1])/3.0;
    M[2]=(X[2]+Y[2]+Z[2])/3.0;
    
    // C= YxX, vn = |C| 
    C=crossProduct(Y,X);
    double vn=Math.sqrt(C[0]*C[0]+C[1]*C[1]+C[2]*C[2]); 
    if (vn<S_TOLER) { // almost parallel 
        D=crossProduct(Z,Y);
        vn=Math.sqrt(D[0]*D[0]+D[1]*D[1]+D[2]*D[2]);
        if (vn<S_TOLER || dot_prod(D,X)<0) // M should be good. 
        	return (proj_vec_to_sph(M[0],M[1],M[2]));
        return (proj_vec_to_sph((-1.0)*M[0],(-1.0)*M[1],(-1.0)*M[2]));
    }
    if (vec_norm(M)<S_TOLER) // almost coplanar 
      return (proj_vec_to_sph(-C[0],-C[1],-C[2]));
    if (dot_prod(C,Z)<0) {
  	 Complex ans=proj_vec_to_sph(M[0],M[1],M[2]);
    	 return (ans);
    }
    return (proj_vec_to_sph((-1.0)*M[0],(-1.0)*M[1],(-1.0)*M[2]));
  } 
  
  /**
	 * Given points z1,z2,z3 and z on the sphere (theta,phi), find barycentric 
	 * coords of z relative to spherical triangle {z1,z2,z3}. For conversion the
	 * other way, see BaryPoint.bp2Complex.
	 * @param z Complex, (theta,phi)
	 * @param z1 (theta,phi)
	 * @param z2 (theta,phi)
	 * @param z3 (theta,phi)
	 * @return BaryPoint
	 */
	public static BaryPoint s_pt_to_bary(Complex z,Complex z1, Complex z2, Complex z3) {
		// TODO: not finished
		return null;
	}
	
	/**
	 * Given a spherical point (theta, phi), return a new Complex 
	 * antipodal point in (theta,phi) form
	 * @param s_pt (theta,phi)
	 * @return new Complex
	 */
	public static Complex getAntipodal(Complex s_pt) {
		
		// handle theta
		double x=s_pt.x-Math.PI;
		if (s_pt.x<0)
			x += 2.0*Math.PI;
		Complex pole=new Complex(x);
		double pmp=Math.PI-s_pt.y;
		if (Math.abs(pmp)<S_TOLER) // south pole?
			pole.y=Math.abs(pmp);
		else
			pole.y=pmp;
		return pole;
	}
	
	/**
	 * Find centroid in 3-space of points in the plane stereo projected
	 * to the sphere after application of a transformation z --> a*z+b+c*i.
	 * @param P Complex[], points in the plane (indexed from 1)
	 * @param trans double[3], {a,b,c} coeff for transfomation
	 * @return Point3D, centroid location
	 */
	public static Point3D transCentroid(Complex []P,double []trans) {
		int N=P.length-1;
		double X=0;
		double Y=0;
		double Z=0;
		
		for (int n=1;n<=N;n++) {
			double u=trans[0]*P[n].x+trans[1];
			double v=trans[0]*P[n].y+trans[2];
			double sq=u*u+v*v;
			double denom=1.0+sq;
			X += 2.0*u/denom;
			Y += 2.0*v/denom;
			Z +=(1.0-sq)/denom;
		}

		double dn=(double)N;
		return new Point3D(X/dn,Y/dn,Z/dn);
	}
			
	/**
	 * Given a list of sph points, (theta,phi), find their 3-space centroid
	 * @param pts Complex[] (theta,phi) form
	 * @return Point3D, null on error
	 */
	public static Point3D getCentroid(Complex []pts) {
		
		// find the centroid
		int sz=pts.length-1;
		double xcoord=0.0;
		double ycoord=0.0;
		double zcoord=0.0;
		for (int j=1;j<=sz;j++) {
			double []xyz=s_pt_to_vec(pts[j]);
			xcoord +=xyz[0];
			ycoord +=xyz[1];
			zcoord +=xyz[2];
		}

		return new Point3D(xcoord/sz,ycoord/sz,zcoord/sz);
	}
	
	/**
	 * Stereographic projection: (u,v) --> (x,y,z) on unit sphere
	 *     z=(1-(u*u+v*v))/(1+(u*u=v*v));    x=u(1+z);    y=v(1+z).
	 * @param z
	 * @return Point3D
	 */
	public static Point3D e_to_sph_vec(Complex ez) {
		double z=ez.absSq();
		double x=ez.x*(1.0+z);
		double y=ez.y*(1.0+z);
		return new Point3D(x,y,z);
	}

}
