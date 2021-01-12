package packQuality;

import java.util.Iterator;

import allMains.CPBase;
import complex.Complex;
import dcel.HalfEdge;
import exceptions.DataException;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import packing.PackData;
import util.UtilPacket;

/**
 * Various measures of packing quality to help identify layout errors,
 * misplaced circles, poorly packed faces, etc. Started 4/2002.
 * 
 * TODO: Needs better organization in future.
 * @author kens
 */
public class QualMeasures {

	/**
	 * "visual error" of an edge is diff/smallest, where diff is the
	 * absolute difference between desired distance (based on radii 
	 * and overlap) and actual distance between endpoints, and smallest 
	 * is the lesser of the two radius. If less than .01, should be
	 * almost postscript invisible.  
	 * @param p PackData
	 * @param elist EdgeLink; edges to run through
	 * @param uP UtilPack; instantiated by calling routine
	 * @return EdgeSimple, worst edge or null on error; uP.rtnFlag==1 implies
	 * some radii too small to depend upon.
	 */
	public static EdgeSimple visualErrMax(PackData p,EdgeLink elist,UtilPacket uP) {
		EdgeSimple worstedge=null;
		if (elist==null || elist.size()==0)
			return worstedge;

		double worstviserr=0.0;
		Iterator<EdgeSimple> elst=elist.iterator();
		uP.rtnFlag=0;
		while (elst.hasNext()) {
			try {
				
				// get the edge, its radii, centers, and inv_dist
				EdgeSimple edge=elst.next();
				double inv_dist=1.0;
				int indx = p.nghb(edge.v, edge.w);
				if (p.overlapStatus && indx >= 0)
					inv_dist=p.getInvDist(edge.v,p.kData[edge.v].flower[indx]);
				Complex z1=p.getCenter(edge.v);
				Complex z2=p.getCenter(edge.w);
				double r1=p.getRadius(edge.v);
				double r2=p.getRadius(edge.w);
				double verr=-1.0;
				if (p.hes<0) { // hyperbolic
					CircleSimple sc=HyperbolicMath.h_to_e_data(z1, r1);
					z1=sc.center;
					r1=sc.rad;
					sc=HyperbolicMath.h_to_e_data(z2, r2);
					z2=sc.center;
					r2=sc.rad;
					verr=visual_error(r1,r2,z1,z2,inv_dist);
				}
				
				else if (p.hes>0) { // spherical
					verr=sph_visual_error(r1,r2,z1,z2,inv_dist);
				}
				
				else { // eucl
					verr=visual_error(r1,r2,z1,z2,inv_dist);
				}
				
				if (verr>worstviserr) {
					worstedge=new EdgeSimple(edge);
					worstviserr=verr;
				}
				else if (verr<0) 
					uP.rtnFlag=-1;
			} catch(Exception ex) {
				System.err.println("visualErrMax problem: "+ex.getMessage());
				uP.rtnFlag=-1; 
				return null;
			}
		} // end of while
		
		uP.value=worstviserr;
		return worstedge;
	}
	
	/**
	 * Given euclidean data, find diff/smallest, where diff is the
	 * difference bwtween the eucludean distance between endpoint centers
	 * and intended distance (based on radii and overlap) and smallest
	 * is the smaller of the two eucl radius. Numerical problems an
	 * occur if smallest radius is less than TOLER; return -1  
	 * @param r1 double
	 * @param r2 double
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param inv_dist double, 1.0 for tangency
	 * @return double, visual error (-1 if rad too small for safe computation)
	 */
	public static double visual_error(double r1,double r2, Complex z1, Complex z2, double inv_dist) {
		double diff=Math.abs(z1.minus(z2).abs()-Math.sqrt(r1*r1+r2*r2+2.0*r1*r2*inv_dist));
		double r = (r2<r1) ? r2:r1;
		if (r<CPBase.GENERIC_TOLER)
			return -1.0; 
		return diff/r;
	}
	
	/** same as 'visual-error', but using sph radii/centers
	 * @param r1 double
	 * @param r2 double
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param inv_dist double
	 * @return double, visual error (-1 if rad too small for safe computation)
	 */
	public static double sph_visual_error(double r1,double r2, Complex z1, Complex z2, double inv_dist) {
		double desired=Math.acos(Math.cos(r1)*Math.cos(r2)-Math.sin(r1)*Math.sin(r2)*inv_dist);
		double diff=Math.abs(desired-SphericalMath.s_dist(z1,z2));
		double r = (r2<r1) ? r2:r1;
		if (r<CPBase.GENERIC_TOLER)
			return -1.0; 
		return diff/r;
	}
	
	/** 
	 * Returns the most well-placed vertex of given face.
	 * On computation error, return vert[0].
	 */	  
	public static int pin_face(PackData p,int face) {
	  int it=p.faces[face].vert[0];
	  double dlength,elength,newrat,rat=-1.0;
	  int []qual=new int[3];

	  for (int i=0;i<3;i++) {
	      qual[i]=0;
	      int v=p.faces[face].vert[0];
	      int num=p.getNum(v)+p.getBdryFlag(v);
	      for (int j=0;j<num;j++) {
	    	  int w=p.kData[v].flower[j];
	    	  try {
	    		  if ((dlength=desired_length(p,v,w))>0.0
	    				  && (elength=edge_length(p,v,w))>0.0
	    				  && elength<2.0*dlength)
	    			  qual[i] += 1;
	    		  if ((newrat=((double)qual[i])/((double)num))>rat) {
	    			  rat=newrat;
	    			  it=v;
	    		  }
	    	  } catch (Exception ex) {}
	      }
	  }
	  return it;
	}
	  
	/**
	 * Return intended length of edge based on radii and overlaps or inversive
	 * distances. Return -1 on error. 
	 * @param p PackData
	 * @param v int;
	 * @param w int;
	 * @return double, -1 in case of infinite hyp radius
	 */
	public static double desired_length(PackData p, int v, int w) {
		int indx;

		// TODO: are these formulae right?
		if (p.hes > 0) { 
			double rv=p.getRadius(v);
			double rw=p.getRadius(w);
			if (p.overlapStatus && (indx = p.nghb(v, w)) >= 0)
				// spherical cosine law: cos(c)=cos(a)*cos(b)+sin(a)*sin(b)*cos(theta) 
				return Math.acos(Math.cos(rv)*Math.cos(rw)+Math.sin(rv)*Math.sin(rw)*p.getInvDist(v,p.kData[v].flower[indx]));
			return (rv+rw);
		} 
		
		else if (p.hes == 0) { // eucl
			double rv=p.getRadius(v);
			double rw=p.getRadius(w);
			if (p.overlapStatus && (indx = p.nghb(v, w)) >= 0)
				return Math.sqrt(rv*rv+rw*rw+2.0*rv*rw*p.getInvDist(v,p.kData[v].flower[indx]));
			return (rv+rw);
		} 
		
		else { // hyp
			if (p.getRadius(v) <= 0 || p.getRadius(w) <= 0)
				return -1;
			if (p.overlapStatus && (indx = p.nghb(v, w)) >= 0)
				return (HyperbolicMath.acosh(HyperbolicMath.h_invdist_cosh(p.getRadius(v),
						p.getRadius(w), p.getInvDist(v,p.kData[v].flower[indx]))));
			else
				return (p.getActualRadius(v) + p.getActualRadius(w));
		}
	} 

	/**
	 * Return distance between centers
	 * @param v, w, vertices
	 * @return, double: distance, or -1 on error, or negative eucl radius 
	 *   for infinite distance in hyp case
	 */
	public static double edge_length(PackData p, int v, int w) {
		Complex a, b;

		a = p.getCenter(v);
		b = p.getCenter(w);
		if (Complex.isNaN(a) || Complex.isNaN(b))
			throw new DataException("encountered NaN");
		if (p.hes > 0) // sph
			return (SphericalMath.s_dist(a, b));
		if (p.hes == 0) // eucl
			return a.minus(b).abs();
		return HyperbolicMath.h_dist(a, b); // 
	}
	
	/**
	 * Compare e = edgelength of faces (based on circle computed centers) to d =
	 * desired edgelength (based on radii and overlaps). Those in facelist with
	 * 1/crit < (e/d) < crit have plot_flag set to 0. 
	 * NOTE: Calling routine cleans up facelist. Also, user may want to 'set_plot_flags' 
	 * before or after this routine.
	 * @param p, PackData
	 * @param crit, double > 1.0
	 * @param facelist, FaceLink
	 * @return int, count of poorly plotted faces or -1 on error.
	 */
	public static int face_error(PackData p,double crit,FaceLink facelist) {
		int count=0,v1,v2,face;
		double elength,dlength,recip,quo;
	  
		if (crit<=1.0 || facelist==null || facelist.size()==0) return -1;
		recip=1/crit;
		Iterator<Integer> flist=facelist.iterator();
		while (flist.hasNext() && p.faces[(face=(Integer)flist.next())].plotFlag>0) {
			for (int i=0;i<3;i++) {
				v1=p.faces[face].vert[i];
				v2=p.faces[face].vert[(i+1)%3];
				try {
					if ((elength=edge_length(p,v1,v2))>=0.0
							&& (dlength=desired_length(p,v1,v2))>0.0
							&& ((quo=elength/dlength)>crit || quo<recip)) {
						p.faces[face].plotFlag=0;
						count++;
						i=3; // break out of for loop
					}
				} catch (Exception ex) {}
			} // end of for loop
		} // end of while
		return count;
	}
	
	/**
	 * Report the edge (v,w) from given EdgeLink having worst relative error, 
	 * meaning "err/smaller" where smaller is the smaller of the two radii
	 * and err is the difference between the "correct" length (i.e., based on 
	 * radii and overlap) and the actual length based on centers. (7/2014 and 6/2015) 
	 * @param p PackData
	 * @param vlist EdgeLink
	 * @param uP UtilPacket: uP.value gives threshhold to count as error (allocated by calling routine)
	 * @return EdgeSimple, worst edge from list, null on error (e.g., if NaN) 
	 */
	public static EdgeSimple rel_contact_error(PackData p,EdgeLink elist,UtilPacket uP) {

		EdgeSimple retedge=null;
		Iterator<EdgeSimple> elst=elist.iterator();
		while (elst.hasNext()) {
			try {
				EdgeSimple edge=elst.next();
				double desired=desired_length(p,edge.v,edge.w);
				double length=edge_length(p,edge.v,edge.w);
				
				// get smallest of two radii
				double smaller=p.getRadius(edge.v);
				smaller = (p.getRadius(edge.w)<smaller) ? p.getRadius(edge.w) : smaller;
				double myrelerr=Math.abs((desired-length)/smaller);
				if (myrelerr>uP.value) {
					uP.value=myrelerr;
					retedge=new EdgeSimple(edge);
				}
			} catch(Exception ex) {
				System.err.println("rel_contact_error problem: "+ex.getMessage());
				uP.rtnFlag=-1; 
				return null;
			}
		}
		return retedge;
	}
	
	/**
	 * Check the orientation of the given faces (based on locations of their
	 * vertices), defaulting to 'all'. Return the index of the first that 
	 * fails to be positively oriented, 0 in case all are okay, -1 for error 
	 * such as NaN center, etc.
	 * @param p PackData
	 * @param flink FaceLink (possibly null)
	 * @return int, 0 if all okay, -1 on error, else index of first failed face
	 */
	public static int badOrientation(PackData p, FaceLink flink) {
		
		// default to all faces
		if (flink==null || flink.size()==0)
			flink=new FaceLink(p,"a");
		Iterator<Integer> flk=flink.iterator();
		int failface=0;
		while (failface==0 && flk.hasNext()) {
			int f=flk.next();
			int []vert=p.faces[f].vert;
			try {
				Complex vz=p.getCenter(vert[0]);
				Complex uz=p.getCenter(vert[1]);
				Complex wz=p.getCenter(vert[2]);
				if (p.hes>0) { // sphere case
					double []vu_tan=SphericalMath.sph_tangent(vz, uz);
					double []vw_tan=SphericalMath.sph_tangent(vz, wz);
					double []cross=SphericalMath.crossProduct(vu_tan,vw_tan);
					double []vdir=SphericalMath.s_pt_to_vec(vz);
					double dp=SphericalMath.dot_prod(cross, vdir);
					if (dp<.000000001) // points opposite to vz?
						return f;
				}
				else { // eucl or hyp cases
					Complex vu=uz.minus(vz);
					Complex vw=wz.minus(vz);
				
					// third component of cross product should be positive
					double k=(vu.x*vw.y-vu.y*vw.x);
					if (k<.000000001)
						return f;
				}
			} catch(Exception ex) {
				return -1;
			}
		}
		
		// got here, then all is okay
		return 0;
	}
		
	/** 
	 * For given vertex 'v', find worst visual error for edges 
	 * from v. 
	 * @param p PackData
	 * @param v int
	 * @return double
	 */
	public static double vert_ErrMax(PackData p,int v) {
		double worstviserr=0.0;
		for (int j=0;j<(p.getNum(v)+p.getBdryFlag(v));j++) {
			EdgeSimple edge=new EdgeSimple(v,p.kData[v].flower[j]);
			double verr=edge_vis_error(p,edge);
			if (verr>worstviserr) {
				worstviserr=verr;
			}
		}
		return worstviserr;
	}
	
	/** 
	 * get visual error of this edge
	 * @param p PackData
	 * @param edge EdgeSimple
	 * @return double
	 */
	public static double edge_vis_error(PackData p,EdgeSimple edge) {
		double inv_dist=1.0;
		int v=edge.v;
		int w=edge.w;
		if (p.overlapStatus) {
			int indx=p.nghb(v,w);
			inv_dist=p.getInvDist(v,p.kData[v].flower[indx]);
		}
		Complex z1=p.getCenter(v);
		Complex z2=p.getCenter(w);
		double r1=p.getRadius(v);
		double r2=p.getRadius(w);
		double verr=-1.0;
		if (p.hes<0) { // hyperbolic
			CircleSimple sc=HyperbolicMath.h_to_e_data(z1, r1);
			z1=sc.center;
			r1=sc.rad;
			sc=HyperbolicMath.h_to_e_data(z2, r2);
			z2=sc.center;
			r2=sc.rad;
			verr=visual_error(r1,r2,z1,z2,inv_dist);
		}
		
		else if (p.hes>0) { // spherical
			verr=sph_visual_error(r1,r2,z1,z2,inv_dist);
		}
		
		else { // eucl
			verr=visual_error(r1,r2,z1,z2,inv_dist);
		}
		return verr;
	}

	/** 
	 * get visual error for DCEL 'HalfEdge'
	 * @param p PackData
	 * @param edge HalfEdge
	 * @return double
	 */
	public static double d_edge_vis_error(PackData p,HalfEdge edge) {
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		Complex zv=p.packDCEL.getVertCenter(edge);
		Complex zw=p.packDCEL.getVertCenter(edge.twin);
		double rv=p.getRadius(v);
		double rw=p.getRadius(w);
		double verr=-1.0;
		if (p.hes<0) { // hyperbolic
			CircleSimple sc=HyperbolicMath.h_to_e_data(zv, rv);
			zv=sc.center;
			rv=sc.rad;
			sc=HyperbolicMath.h_to_e_data(zw, rw);
			zw=sc.center;
			rw=sc.rad;
			verr=visual_error(rv,rw,zv,zw,edge.getInvDist());
		}
		
		else if (p.hes>0) { // spherical
			verr=sph_visual_error(rv,rw,zv,zw,edge.getInvDist());
		}
		
		else { // eucl
			verr=visual_error(rv,rw,zv,zw,edge.getInvDist());
		}
		return verr;
	}

}
