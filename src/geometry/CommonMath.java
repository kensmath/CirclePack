package geometry;

import complex.Complex;
import math.CirMatrix;

/**
 * General calls to math routines that then call the appropriate geometry.
 * Try to have the same name and arguments in every geometry.
 * @author kstephe2
 *
 */
public class CommonMath {
	
	/**
	 * Convert 'CirMatrix' to 'SimpleCircle' in specified geometry
	 * @param C CirMatrix
	 * @param hes int
	 * @return SimpleCircle
	 */
	public static CircleSimple any_cirMatrix2sC(CirMatrix C,int hes) {
		CircleSimple sC=CirMatrix.euclCircle(C);
		if (hes>0) 
			return SphericalMath.e_to_s_data(sC.center,sC.rad);
		if (hes<0)
			return HyperbolicMath.e_to_h_data(sC.center,sC.rad);
		return sC;
	}
	
	/** 
	 * Given radii, inv. distances, and geometry, place a triple of 
	 * circles; first is at origin, next in standard orientation 
	 * (namely, in eucl, on positive x-axis), last determined by 
	 * law of cosines.
	 * 
	 * TODO: use this to replace 'place_face' in other contexts
	 *  
	 * remain in s-radius, so we have to convert.
	 * @param sC0 SimpleCircle,
	 * @param sC1 SimpleCircle,
	 * @param sC2 SimpleCircle,
	 * @param hes int
	 * @return int, 0 on layout error, circle data given in SimpleCircles
	*/
	public static int placeOneFace(CircleSimple sC0,CircleSimple sC1,CircleSimple sC2,int hes) {
		return placeOneFace(sC0,sC1,sC2,1.0,1.0,1.0,hes);
	}

	/** 
	 * Given radii, inv. distances, and geometry, place a triple of 
	 * circles; first is at origin, next in standard orientation 
	 * (namely, in eucl, on positive x-axis), last determined by 
	 * law of cosines.
	 * 
	 * TODO: use this to replace 'place_face' in other contexts
	 *  
	 * @param sC0 SimpleCircle,
	 * @param sC1 SimpleCircle,
	 * @param sC2 SimpleCircle,
	 * @param id0 double
	 * @param id1 double
	 * @param id2 double
	 * @param hes int
	 * @return int, 0 on layout error, circle data given in SimpleCircles
	*/
	public static int placeOneFace(CircleSimple sC0,CircleSimple sC1,CircleSimple sC2,
			double id0,double id1,double id2,int hes) {

	  // first is always at origin
	  sC0.center=new Complex(0.0);
	  
	  // place the second on the real axis
	  if (hes<0) { // hyp case 
	    double x1=sC0.rad;
	    double s1=HyperbolicMath.x_to_s_rad(x1);
	    double x2=sC1.rad;
	    double s2=HyperbolicMath.x_to_s_rad(x2);
	    if (s1<=0) {
	      x1 = 0.99;
	      s1=HyperbolicMath.x_to_s_rad(x1);
	      sC0.rad=x1;
	      /* strcpy(msgbuf,"Circle at origin had "
		     "infinite radius; radius reset.");*/
	    }

	    if (s2<=0) { /* if next one is infinite radius */
	      sC1.center=new Complex(1.0);
	      double erad=x1/((1+s1)*(1+s1));
	      sC1.rad=(-1)*(1-erad*erad)/(2.0+2.0*erad*id0);
	    }
	    else { 
	      double x12 = x1*x2;
	      double x1p2 = x1+x2;
	      double s12 = s1*s2;
	      double x = (x1p2-x12)/(s12*(1+s12)) - (2*x1p2 - (1+id0)*x12)/(4*s12);
	      double s= x + Math.sqrt(x*(x+2));
	      sC1.center=new Complex(s/(s+2),0.0);
	    }
	  }
	  else if (hes>0) { // sphere case 
	    // next out pos x-axis 
	    // TODO: need to incorporate overlaps
	    sC1.center = new Complex(0.0,sC0.rad+sC1.rad);
	  }
	  else { // eucl case 
	    // next on x-axis
	    double r=sC0.rad;
	    double r2=sC1.rad;
	    sC1.center=new Complex(Math.sqrt(r*r+r2*r2+2*r*r2*id0),0.0);
	  }
	  
	  CircleSimple sC=new CircleSimple();
	  sC=CommonMath.comp_any_center(sC0.center, sC1.center,sC0.rad,sC1.rad,
				sC2.rad, id0, id1,id2,hes);
	  sC2.center=sC.center;
	  return sC.flag;  // in case there's an error
	}
	
	/**
	 * Compute third circle give two centers, radii, and overlaps
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r1 double
	 * @param r2 double
	 * @param r3 double
	 * @param o1 double, overlap opposite z1
	 * @param o2 double, opposite z2
	 * @param o3 double, opposite z3
	 * @param hes int
	 * @return SimpleCircle
	 */
	public static CircleSimple comp_any_center(Complex z1, Complex z2,
	  		 double r1,double r2,double r3, double o1,double o2,double o3,int hes) {
		if (hes<0)
			return HyperbolicMath.h_compcenter(z1,z2,r1,r2,r3,o1,o2,o3);
		if (hes>0)
			return SphericalMath.s_compcenter(z1,z2,r1,r2,r3,o1,o2,o3);
		else
			return EuclMath.e_compcenter(z1,z2,r1,r2,r3,o1,o2,o3);
	}
	
	/**
	 * Compute third circle give two centers, radii, no overlaps
	 * @param z1
	 * @param z2
	 * @param r1
	 * @param r2
	 * @param r3
	 * @param hes
	 * @return
	 */
	public static CircleSimple comp_any_center(Complex z1, Complex z2,
	  		 double r1,double r2,double r3, int hes) {
		if (hes<0)
			return HyperbolicMath.h_compcenter(z1,z2,r1,r2,r3,1.0,1.0,1.0);
		if (hes>0)
			return SphericalMath.s_compcenter(z1,z2,r1,r2,r3,1.0,1.0,1.0);
		else
			return EuclMath.e_compcenter(z1,z2,r1,r2,r3,1.0,1.0,1.0);
	} 
	
	/**
	 * Get triangle incircle from corners (this is incircle of triangle, not 
	 * dependent on the circles.
	 * TODO: hyp computations don't seem right yet.
	 * @param z1
	 * @param z2
	 * @param z3
	 * @param hes int
	 * @return SimpleCircle
	 */
	public static CircleSimple tri_incircle(Complex z1,Complex z2,Complex z3,int hes) {
		if (hes<0) 
			return HyperbolicMath.hyp_tri_incircle(z1,z2,z3);
		if (hes>0)
			return SphericalMath.sph_tri_incircle(z1, z2, z3);
		return EuclMath.eucl_tri_incircle(z1, z2, z3);
	}
	
	/**
	 * Compute inversive distance between two circles
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r1 double
	 * @param r2 double
	 * @param hes int
	 * @return double
	 */
	public static double get_inv_dist(Complex z1,Complex z2,double r1,double r2,int hes) {
		if (hes<0) {
			CircleSimple sC1=HyperbolicMath.h_to_e_data(z1,r1);
			CircleSimple sC2=HyperbolicMath.h_to_e_data(z2,r2);
			return EuclMath.inv_dist(sC1.center,sC2.center,sC1.rad,sC2.rad);
		}
		if (hes>0) {
			return SphericalMath.s_inv_dist(z1, z2, r1, r2);
		}
		return EuclMath.inv_dist(z1, z2,r1, r2);
	}

	/**
	 * Given 2 circles, find tangency point. Actually, returns point
	 * between with position weighted by the two radii (depending on
	 * the geometry).
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r1 double
	 * @param r2 double
	 * @param hes int
	 * @return new Complex
	 */
	public static Complex get_tang_pt(Complex z1,Complex z2,double r1,double r2,int hes) {
		if (hes==0)
			return EuclMath.eucl_tangency(z1,z2,r1,r2);
		else if (hes>0)
			return SphericalMath.sph_tangency(z1,z2,r1,r2);
		else
			return HyperbolicMath.hyp_tangency(z1,z2,r1,r2);
	}
	
}
