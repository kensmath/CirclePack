package geometry;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import complex.Complex;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.Vertex;
import exceptions.DataException;
import listManip.HalfLink;
import math.CirMatrix;
import math.Mobius;
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
		  double sdist=SphericalMath.s_ivd_length(sC0.rad,sC1.rad,id0);
	    sC1.center = new Complex(0.0,sdist);
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
	
	public static CircleSimple comp_any_center(CircleSimple cs1,
			CircleSimple cs2,double r3,double o1,double o2,double o3,int hes) {
		return comp_any_center(cs1.center,cs2.center,cs1.rad,cs2.rad,
				r3,o1,o2,o3,hes);
	}
	
	/**
	 * Compute third circle given two centers, radii, and overlaps
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
	 * Give indication of relative error between centers/rad 
	 * of two circles in same geometry. Result is error as
	 * fraction of average of the two radii. 
	 * @param cs1 CircleSimple
	 * @param cs2 CircleSimple
	 * @param hes int
	 * @return double
	 */
	public static double circleCompError(CircleSimple cs1,CircleSimple cs2,int hes) {
		double avgRad=(0.5)*(cs1.rad+cs2.rad);
		double diff_radii=Math.abs(cs1.rad-cs2.rad);
		double diff_centers=get_pt_dist(cs1.center,cs2.center,hes);
		return (diff_radii+diff_centers)/avgRad;
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
	 * Compute the length between circles given radii r1 and r2 
	 * and inversive distance 'ivd'.
	 * @param r1 double
	 * @param r2 double
	 * @param ivd double
	 * @param hes geometry
	 * @return double, -1 for infinite in hyp case
	 */
	public static double ivd_edge_length(double r1,double r2, double ivd,int hes) {
		if (hes<0) 
			return HyperbolicMath.h_ivd_length(r1, r2, ivd);
		else if (hes>0)
			return SphericalMath.s_ivd_length(r1, r2, ivd);
		else 
			return EuclMath.e_ivd_length(r1, r2, ivd);
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
		if (hes<0) // hyp with x-radii
			theCos=HyperbolicMath.h_comp_x_cos(rad0,rad1,rad2);
		else if (hes>0) { // sph
			theCos=SphericalMath.s_comp_cos(rad0, rad1, rad2);
		}
		else { // eucl
			theCos=EuclMath.e_cos_overlap(rad0, rad1, rad2);
		}
		if (Math.abs(theCos)>1.0)
			throw new DataException("error calculating angle");
		return Math.acos(theCos);
	}

	/**
	 * Compute angle at v0 in mutually tangent triple of circles with
	 * given radii. x-radii in hyp case.
	 * @param r0 double
	 * @param r1 double
	 * @param r2 double
	 * @param o0 double
	 * @param o1 double
	 * @param o2 double
	 * @param hes int
	 * @return double
	 * @throws DataException
	 */
	public static double get_face_angle(double r0,double r1,double r2,
			double o0,double o1,double o2,int hes) throws DataException {
		double theCos=0.0;
		if (hes<0) { // hyp 
			theCos=HyperbolicMath.h_comp_cos(r0,r1,r2,o0,o1,o2);
		}
		else if (hes>0) { // sph
			theCos=SphericalMath.s_comp_cos(r0, r1, r2);
		}
		else { // eucl
			theCos=EuclMath.e_cos_overlap(r0, r1, r2, o0,o1,o2);
		}
		if (Math.abs(theCos)>1.0)
			throw new DataException("error calculating angle");
		return Math.acos(theCos);
	}

	/**
	 * Compute angle sum at 'v' given radius 'rad'
	 * @param p PackData
	 * @param v int
	 * @param rad double
	 * @param uP UtilPacket, instantiated by calling routine
	 * @return boolean, false on some failure
	 */
	public static boolean get_anglesum(PackData p,int v,double rad,UtilPacket uP) {
		if (p.packDCEL!=null) {
			try {
				uP.value=p.packDCEL.getVertAngSum(p.packDCEL.vertices[v],rad);
			} catch (Exception ex) {
				return false;
			}
			return true;
		}
		
		if (p.hes<0) // hyp
			return p.h_anglesum_overlap(v,rad,uP);
		else if (p.hes>0) // sph
			return p.s_anglesum(v,rad,uP);
		else // eucl
			return p.e_anglesum_overlap(v,rad,uP);
	}

	/**
	 *  Return center of incircle of triangle formed by
	 *  given points in given geometry. (For hyperbolic, we
	 *  use the euclidean incircle, not very satisfactory.)
	 *  @param p0 Complex
	 *  @param p1 Complex
	 *  @param p2 Complex
	 *  @param hes int
	 *  @return Complex
	 */
	public static Complex tripleIncircle(Complex p0,
			Complex p1,Complex p2,int hes) {
		CircleSimple sc=null;
		if (hes<=0) // in hyp case, use eucl incircle
	// TODO: need incenter of generic hyperbolic triangle.
    //			need radii to use 'HyperbolicMath.hyp_tang_incircle'
		sc=EuclMath.eucl_tri_incircle(p0,p1,p2);
		else 
			sc=SphericalMath.sph_tri_incircle(p0,p1,p2);
		return sc.center;
	}
	
	/**
	 * Build array of corners for the dual face of 'hedge.origin'.
	 * In non-red case, build from successive dual edges. If
	 * 'redFlag', layout first face and its center, then when
	 * necessary, successively recompute the next faces. Class
	 * 'TripFaceData' helps maintain the data.
	 * A couple details: Edges crossing a bdry edge end 
	 * at a point of the edge; for bdry 'vert', include 
	 * its center as the first and last of face "corners".
	 * @param pdcel
	 * @param hedge HalfEdge
	 * @param hes int
	 * @return ArrayList<Complex> non-closed list of corners
	 */
	public static ArrayList<Complex> buildDualFace(PackDCEL pdcel,
			HalfEdge hedge,int hes) {
		Vertex vert=hedge.origin;
		
		// get spokes
		HalfLink spokes=vert.getSpokes(hedge);
		CircleSimple csVert=pdcel.getVertData(hedge); // should remain
		Complex[] dualends=new Complex[2];
		ArrayList<Complex> carray=new ArrayList<Complex>();
		
		// non-red vertex, most common
		if (!vert.redFlag) {
			Iterator<HalfEdge> sis=spokes.iterator();
			while (sis.hasNext()) {
				dualends=pdcel.getDualEdgeEnds(sis.next());
				carray.add(dualends[1]);
			}
			return carray;
		}
		
		// red vertices treated differently 
		if (vert.bdryFlag!=0) // is bdry? start with center
			carray.add(pdcel.getVertCenter(hedge));
			
		Iterator<HalfEdge> sis=spokes.iterator();
			
		// first edge
		HalfEdge nxtspoke=sis.next();
		dualends=pdcel.getDualEdgeEnds(nxtspoke);
		carray.add(dualends[0]); // if bdry vert, this is pt on bdry edge
		carray.add(dualends[1]);
		boolean recomp=false; // once recomputing starts, it continues
		CircleSimple csV=pdcel.getVertData(nxtspoke);
		CircleSimple csW=pdcel.getVertData(nxtspoke.next);
		CircleSimple csLastW=pdcel.getVertData(nxtspoke.next.next);
			
		while (sis.hasNext()) {
			nxtspoke=sis.next();
			csV=pdcel.getVertData(nxtspoke);
			csW=pdcel.getVertData(nxtspoke.next);
			
			// decide whether to recompute next tip or use
			//   saved values: remember, positions of spoke
			//   ends can depend on HalfEdge.
			if (recomp || circleCompError(csVert,csV,hes)>.001 ||
					circleCompError(csLastW,csW,hes)>.001) {
				recomp=true;
				Mobius mob=Mobius.mob_MatchCircles(csV,csW,
						csVert,csLastW,hes,hes);
				
				// apply mob to faceZ and record as next corner
				Complex faceZ=pdcel.getFaceCenter(nxtspoke.face);
				carray.add(mob.apply(faceZ));
				
				// also find the new location for the spoke end.
				csLastW=pdcel.getVertData(nxtspoke.next.next);
				CircleSimple csOut=new CircleSimple();
				Mobius.mobius_of_circle(mob, hes, csLastW, csOut, true);
				csLastW=csOut;
			} 
		
			// just compute new dual edge end
			else {
				if (nxtspoke.face.faceIndx<=0) {
					nxtspoke=nxtspoke.twin;
					dualends=pdcel.getDualEdgeEnds(nxtspoke);
					carray.add(dualends[0]);
				}
				else {
					dualends=pdcel.getDualEdgeEnds(nxtspoke);
					carray.add(dualends[1]);
				}
				csLastW=pdcel.getVertData(nxtspoke.next.next);
			}
		} // end of while through spokes
		return carray;
	}
	
	/**
	 * Given three circles, find the incircle of the triangular
	 * face they form. 
	 * @param cs1 CircleSimple
	 * @param cs2 CircleSimple
	 * @param cs3 CircleSimple
	 * @param hes int
	 * @return
	 */
	public static CircleSimple circle3Incircle(CircleSimple cs0,
			CircleSimple cs1,CircleSimple cs2,int hes) {
		CircleSimple[] cS= {cs0,cs1,cs2};
		Complex[] pts=new Complex[3];
		for (int j=0;j<3;j++) {
			pts[j]=CommonMath.genTangPoint(cS[j],cS[(j+1)%3],hes);
			if (hes>0)
				pts[j]=SphericalMath.s_pt_to_plane(pts[j]);
		}
		CircleSimple theCircle=EuclMath.circle_3(pts[0], pts[1], pts[2]);
		if (hes<0)
			theCircle=HyperbolicMath.e_to_h_data(theCircle);
		if (hes>0)
			theCircle=SphericalMath.e_to_s_data(theCircle);
		return theCircle;
	}

	// TODO: improve notion of "generalized tangency" to
	//       get conformal invariant definition and
	//       tangency in (essentially) tangent case.
	// Note: sph case can go wrong if one or both circles
	//       contain infinity.
	/* As of 6/2021:
	 * Work with eucl circles c1=(z1,r1), c2=(z2,r2). 
	 * The GT point will be located on the ray L from
	 * z1 through z2, so we work with real number 
	 * distances on L. 
	 * 
	 * First get inversive distance between c1/c2.
	 *    invD=(|z1-z2|^2-(r1^2+r2^2))/(2*r1*r2).
	 * (this may or may not correspond with "intended" 
	 * inversive distance). 
	 * 
	 * We identify the c1/c2 configuration with a 
	 * normalized configuration C-/C+ that is a
	 * mobius image: namely, based on inD alone, 
	 * can compute radius R and center circles 
	 * C-, C+ of radius R on the negative/positive 
	 * real axis (resp.) the left edge of C- hits -1 
	 * and the right edge of C+ hits +1.
	 * 
	 * Here's R: 
	 *    R=(-2+sqrt(2+2*invD))/(invD-1)
	 *    
	 * Triples of points to be identified are {A,B,C}
	 * on L and {a,b,c} in the normalized situation.
	 * 
	 * For {A,B,C}, let x = |z1-z2|. Then A = x-r2, 
	 * B=r1, C=x+r2. (These are points along L)
	 * A is the distance along L from z1 to the first
	 * intersection point of L and c2, B is intersection
	 * point of L and c1, and C is the further intersection
	 * point of L and c2. (Note that B and C are positive, 
	 * but A may be negative in case of deep overlap.)
	 * 
	 * The corresponding points {a,b,c} of the real line
	 * are: a is the left edge of C+, b is the right edge
	 * of C-, and c is +1. Thus, a = 1-2R, b = -1+2R, and
	 * c = 1.
	 * 
	 * If T is the Mobius map: T{a,b,c} --> {A,B,C}, then
	 * by obvious symmetry about the y-axis in the 
	 * normalized situation, T(0) is what we are after. 
	 * Return the point along L which is distance T(0) 
	 * from z1 in the direction of z2.
	 *
	 * @param cs1 CircleSimple
	 * @param cs2 CircleSimple
	 * @param hes int
	 * @return new Complex 
	 */
	public static Complex genTangPoint(CircleSimple cs1,
			CircleSimple cs2,int hes) {

		// convert to eucl circles
		if (hes<0) {
			cs1=HyperbolicMath.h_to_e_data(cs1);
			cs2=HyperbolicMath.h_to_e_data(cs2);
		}
		else if (hes>0) {
			cs1=SphericalMath.s_to_e_data(cs1);
			cs2=SphericalMath.s_to_e_data(cs2);
		}
		
		double x=cs1.center.minus(cs2.center).abs();
		double t=cs1.rad; // prepare for tangency

		double invD=(x*x-(cs1.rad*cs1.rad+cs2.rad*cs2.rad))/(2*cs1.rad*cs2.rad);
		
		// if not essentially tangent, find real value t
		if (Math.abs(invD-1.0)>.001) {
			
			// points on L
			Complex A=new Complex(x-cs2.rad);
			Complex B=new Complex(cs1.rad);
			Complex C=new Complex(x+cs2.rad);
			
			// radius and points for normalized config
			double R=(-2.0+Math.sqrt(2.0+2.0*invD))/(invD-1.0);
			Complex a=new Complex(1.0-2.0*R);
			Complex b=new Complex(-1+2.0*R);
			Complex c=new Complex(1.0);
			
			Mobius mob=Mobius.mob_xyzXYZ(a,b,c,A,B,C,0,0);
			t=mob.apply(new Complex(0.0)).x;
		}
		
		// have t, find point distance t along L from cs1.center
		Complex pt=cs2.center.minus(cs1.center).times(t/x).add(cs1.center);
		if (hes>0) 
			return SphericalMath.proj_pt_to_sph(pt);
		return pt;
	}
	
	/**
	 * Given 2 circles, find generalized tangency point. 
	 * Actually, this is intermediate point with position 
	 * weighted by the two radii (depending on geometry).
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r1 double
	 * @param r2 double
	 * @param hes int
	 * @return new Complex
	 */
	public static Complex get_tang_pt(Complex z1,Complex z2,
			double r1,double r2,int hes) {
		if (hes==0)
			return EuclMath.eucl_tangency(z1,z2,r1,r2);
		else if (hes>0)
			return SphericalMath.sph_tangency(z1,z2,r1,r2);
		else
			return HyperbolicMath.hyp_tangency(z1,z2,r1,r2);
	}
	

	
} // end of class

/** 
 * For holding data on a face. Calling routine must know the
 * order of the data, the geometry, etc.
 * @author kstephe2
 *
 */
class TmpFaceData {
	public Complex[] center;
	public double[] rad;
	public double[] invDist;
	
	public TmpFaceData(Complex[] pts,double[] r,double[] id) {
		for (int j=0;j<3;j++) {
			center[j]=pts[j];
			rad[j]=r[j];
			invDist[j]=id[j];
		}
	}
}
