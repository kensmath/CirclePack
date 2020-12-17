package geometry;

import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
import math.CirMatrix;
import packing.PackData;
import util.UtilPacket;

/**
 * General calls to math routines; these call the relevant routine in
 * the appropriate geometry. I try to get similar names and arguments 
 * in every geometry. Only towards the end of this file are routines
 * that require 'PackData'.
 * @author kstephe2
 *
 */
public class CommonMath {
	
	/** 
	 * Given radii, inv. distances, and geometry, place a triple of 
	 * circles; first is at origin, next in standard orientation 
	 * (namely, in eucl, on positive x-axis), last determined by 
	 * law of cosines.
	 * 
	 * TODO: use this to replace 'place_face' in other contexts
	 *  
	 * remain in s-radius, so we have to convert.
	 * @param sC0 CircleSimple,
	 * @param sC1 CircleSimple,
	 * @param sC2 CircleSimple,
	 * @param hes int
	 * @return int, 0 on layout error, circle data given in CircleSimple's
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
	 * @param sC0 CircleSimple,
	 * @param sC1 CircleSimple,
	 * @param sC2 CircleSimple,
	 * @param id0 double
	 * @param id1 double
	 * @param id2 double
	 * @param hes int
	 * @return int, 0 on layout error, circle data given in CircleSimple's
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
	 * @return CircleSimple
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
	 * @return CircleSimple
	 */
	public static CircleSimple tri_incircle(Complex z1,Complex z2,Complex z3,int hes) {
		if (hes<0) 
			return HyperbolicMath.hyp_tri_incircle(z1,z2,z3);
		if (hes>0)
			return SphericalMath.sph_tri_incircle(z1, z2, z3);
		return EuclMath.eucl_tri_incircle(z1, z2, z3);
	}
	
	/** 
	 * Compute the distance between two points. Note that in the spherical
	 * case, centers are expected to be (theta,phi) form.
	 * @param z Complex
	 * @param w Complex
	 * @param hes int, geometry
	 * @return double
	 */
	public static double get_pt_dist(Complex z,Complex w,int hes) {
		if (hes<0)
			return HyperbolicMath.h_dist(z, w);
		else if (hes>0)
			return SphericalMath.s_dist(z, w);
		else
			return EuclMath.e_dist(z,w);
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
	 * Compute the length an edge should have between circles of radii r1 and r2
	 * if inversive distance is given.
	 * @param r1 double
	 * @param r2 double
	 * @param inv_dist double
	 * @param hes geometry
	 * @return double
	 */
	public static double inv_dist_edge_length(double r1,double r2, double inv_dist,int hes) {
		if (hes<0)
			return HyperbolicMath.h_invdist_length(r1, r2, inv_dist);
		else if (hes>0)
			return SphericalMath.s_invdist_length(r1, r2, inv_dist);
		else 
			return EuclMath.e_invdist_length(r1, r2, inv_dist);
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
	
	/**
	 * Convert 'CirMatrix' to 'CircleSimple' in requested geometry. 
	 * @param cM CirMatrix, 2x2 representation of a circle
	 * @param hes int, geometry
	 * @return CircleSimple, null on error (e.g. improper hyp case)
	 */
	public static CircleSimple cirMatrix_to_geom(CirMatrix cM,int hes) {
		if (cM==null)
			return null;
		CircleSimple outCS=new CircleSimple();
		CirMatrix CC=CirMatrix.normalize(cM); // a should be +-1 or 0
		
		// typical data (recall, a.x=-1 ==> all entries were multiplied by -1)
		Complex ecent=CC.c.times(-1.0*CC.a.x); // c entry is -center
		double reald=CC.d.x*CC.a.x; // throw out any extraneous imaginary part
		double rsq=ecent.absSq()-reald;
		if (rsq<=0) {
			CirclePack.cpb.errMsg("error in a 'CirMatrix'");
			return null;
		}
		// positive eucl radius
		double erad=Math.sqrt(rsq); 

		// sph case, radius/center. See the conventions about inside/outside, 
		if (hes>0) {
			// circle is a straight line (goes through south pole)
			if (CC.a.abs() < CirMatrix.CM_TOLER) {
				// through origin? Hence a hemisphere
				if (CC.d.abs() < CirMatrix.CM_TOLER) {
					outCS.center.y = outCS.rad = Math.PI / 2.0;
					outCS.center.x=CC.b.conj().arg();
					return outCS;
				} 

				// straight line, but NOT through origin
				double R=CC.d.abs(); // distance to origin
				double theta=Math.atan2(-1.0*CC.b.y,CC.b.x);
				double atn=Math.atan(R);
				double rho=Math.PI/2.0-atn; 
				if (CC.d.x<0) // encloses origin (north pole)?
					rho+=2.0*atn;
				outCS.rad=rho;
				outCS.center=new Complex(theta,Math.PI-rho);

				return outCS;
			} // end of 'straight line' cases

			// else a circle
			CircleSimple sc=SphericalMath.e_to_s_data(ecent, erad);
			outCS.center=sc.center;
			outCS.rad=sc.rad;
			if (CC.a.x<0) { // want outside of euclidean circle
				outCS.center=SphericalMath.getAntipodal(sc.center);
				outCS.rad=Math.PI-sc.rad;
			}
			return outCS;
		} // done with sph
		
		// hyp case: return null if circle is not in unit disc
		if (hes<0) { 
			if (CC.a.x<=0) { // straight line or outside
				CirclePack.cpb.errMsg("Improper hyp conversion of 'CirMatrix'");
				return null;
			}
			if (ecent.abs()+erad>1.0) // not in disc
				return null;
			
			return HyperbolicMath.e_to_h_data(ecent, erad);
		}
		
		// else eucl; watch for line
		if (CC.a.x==0 && CC.a.y==0) { // yes, is a line
			outCS.lineFlag=true;
			outCS.center=CC.b.conj();  // unit normal toward interior
			outCS.rad=-2.0*CC.d.x;     // signed distance from origin
			return outCS;
		}
		outCS.center=ecent; 
		outCS.rad=erad*CC.a.x; // may be negative if a=-1
		return outCS;
		
	}
	
	/**
	 * Compute angle at v0 in mutually tangent triple of circles with
	 * given radii. Assume tangency. x-radii in hyp case.
	 * @param rad0 double. 
	 * @param rad1 double
	 * @param rad2 double
	 * @param hes int, geometry
	 * @return double, -1.0 on error
	 */
	public static double get_face_angle(double rad0,double rad1,double rad2,int hes)
			throws DataException {
		double theCos=0.0;
		if (hes<0) // hyp 
			theCos=HyperbolicMath.h_comp_x_cos(rad0,rad1,rad2);
		else if (hes>0) { // sph
			theCos=SphericalMath.s_comp_cos(rad0, rad1, rad2);
		}
		else { // eucl
			UtilPacket up=new UtilPacket();
			if (EuclMath.e_cos_overlap(rad0, rad1, rad2, up))
				theCos=up.value;
			else
				throw new DataException("euclidean incompatibility.");
		}
		if (Math.abs(theCos)>1.0)
			throw new DataException("error calculating angle");
		return Math.acos(theCos);
	}

	/**
	 * Compute any anlge sum
	 * @param p PackData
	 * @param v int
	 * @param rad double
	 * @param uP UtilPacket, instantiate by calling routine
	 * @return boolean, false on some failutre
	 */
	public static boolean get_anglesum(PackData p,int v,double rad,UtilPacket uP) {
		  if (p.hes<0) // hyp
			  return p.h_anglesum_overlap(v,rad,uP);
		  else if (p.hes>0) // sph
			  return p.s_anglesum(v,rad,uP);
		  else // eucl
			  return p.e_anglesum_overlap(v,rad,uP);
	}
	

}
