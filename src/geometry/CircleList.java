package geometry;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Vector;

import math.Point3D;
import panels.CPScreen;

import complex.Complex;
import complex.MathComplex;

/**
 * This is a geometry utility class. It uses a simple matix propogation
 * method to generate 2D points forming a circle or arc of circle. 
 * @author kens
 *
 */
public class CircleList {
	static double m2pi=2.0*Math.PI;
	static double piby2=Math.PI/2.0;
	static double[] A,B,C,Chat; 
	static Vector<Point2D.Double> vec;
	// unit circle case, static
	static double ustep=m2pi/128; 
	static double uss=ustep*ustep/4.0;
	static double ua11=(1-uss)/(1+uss);
	static double ua12= (-ustep)/(1+uss);
	static double ua21= (-ua12);
	static double ua22=ua11;
	
	/**
	 * Compute vector of points forming an oriented arc of a circle
	 * with given radius, center, given start angle (radians) and
	 * angle extend (positive or negative radians) and number of 
	 * divisions (with possibly additional point for clean finish). 
	 * Return a vector with at least two points.
	 * If N=1, this gives endpoints of eucl line segment (e.g., when an
	 * arc is so small that there's no value in plotting more points).
	 * @param center, Complex
	 * @param rad
	 * @param startAng, double, radians
	 * @param extend, double, radians, positive or negative
	 * @return Vector of Point2D.Double's
	 */
	public static Vector<Point2D.Double> getArcList(Complex center,
			double rad,double startAng,double extent,int N) {
		double zr,zi,wr,wi;
		Point2D.Double startPt,endPt;

		if (N<1) N=1;
		
		zr=rad*Math.cos(startAng);
		zi=rad*Math.sin(startAng);
		startPt=new Point2D.Double(zr+center.x,zi+center.y);
		endPt=new Point2D.Double(rad*Math.cos(startAng+extent)+center.x,
				rad*Math.sin(startAng+extent)+center.y);
		// Proportion of those steps to use based on 'extent' an 'N'
		int n=(int)(Math.floor(N*Math.abs(extent)/m2pi)); 
		Vector<Point2D.Double> vect=new Vector<Point2D.Double>(n+2);
		// Start with first point
		vect.add(startPt);
		// Perhaps just add line segment
		if (n<=1) { 
			vect.add(endPt);
			return vect;
		}
		// A 2x2 matrix propogates points of circle based on N divisions
		double step=m2pi/N; 
		if (extent<0.0) step = -step;
		double ss=step*step/4.0;
		double a11=(1-ss)/(1+ss);
		double a12= (-step)/(1+ss);
		double a21= (-a12);
		double a22=a11; 

		for (int j=0;j<n;j++) {
			wr=zr;
			wi=zi;    
			zr=a11*wr+a12*wi;
		    zi=a21*wr+a22*wi;
		    vect.add(new Point2D.Double(zr+center.x,zi+center.y));
		}
		// finish with endpoint
		vect.add(endPt); 
		return vect;
	}

	/**
	 * Given sph points on the horizon of sphere, get arclist for 
	 * counterclockwise horizon arc. 'N' is 128. 
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return Vector of Point2D.Double's
	 */
	public static Vector<Point2D.Double> getUnitArcList(Complex z1,
			Complex z2) {
		Point3D a=new Point3D(z1);
		Point3D b=new Point3D(z2);
		double ang1=Math.atan2(a.z,a.y);
		double ang2=Math.atan2(b.z,b.y);
		double extent=MathComplex.radAngDiff(ang1,ang2);
		return vec=CircleList.getUnitArcList(ang1,extent);
	}
		
	/** 
	 * Same as 'getArcList' for arcs of the unit circle, 'N' is 128.
	 * Note: 'extent' should always be positive here.
	 * @param startAng, double, radians
	 * @param extent, positive double, radians
	 * @return Vector of Point2D.Double's
	 */
	public static Vector<Point2D.Double> getUnitArcList(double startAng,
			double extent) {
		double zr,zi,wr,wi;
		Point2D.Double startPt,endPt;

		zr=Math.cos(startAng);
		zi=Math.sin(startAng);
		startPt=new Point2D.Double(zr,zi);
		endPt=new Point2D.Double(Math.cos(startAng+extent),
				Math.sin(startAng+extent));
		
		// proportion of 128 increments we use, based on extent
		int n=(int)(Math.floor(128*Math.abs(extent)/m2pi)); 
		Vector<Point2D.Double> vect=new Vector<Point2D.Double>(n+2);
		// Start with first point
		vect.add(startPt);
		// Perhaps just add line segment
		if (n<=1) { 
			vect.add(endPt);
			return vect;
		}
		// static 2x2 matrix propogates points 
		for (int j=0;j<n;j++) {
			wr=zr;
			wi=zi;    
			zr=ua11*wr+ua12*wi;
		    zi=ua21*wr+ua22*wi;
		    vect.add(new Point2D.Double(zr,zi));
		}
		// finish with endpoint
		vect.add(endPt); 
		return vect;
	}
		
	/**
	 * Using static variables, convert 2D list to 3D and then project
	 * and put in the static 'vec'. First spot in vec already occupied.
	 * @param twoD
	 */
	public static void convertToVec(Vector<Point2D.Double> twoD) {
		double[] pt=new double[3];
		for (int j=1;j<twoD.size();j++) {
			Point2D.Double p2D=(Point2D.Double)twoD.get(j);
			pt[1]= Chat[1]+p2D.getX()*A[1]+p2D.getY()*B[1]; 
			pt[2]= Chat[2]+p2D.getX()*A[2]+p2D.getY()*B[2];
			vec.add(new Point2D.Double(pt[1],pt[2]));
		}
	}
	/**
	 * Cent is Point3D center of spherical circle, {A,B,Cent} is 
	 * right-hand orthonormal system of Point3D vectors, 'sphrad' is 
	 * spherical radius. end1 and end2 are Point3D endpoints of the 
	 * desired arc. If end1=null, want full circle, otherwise we
	 * assume end1 and end2 are on the horizon and positively 
	 * oriented (as viewed from Cent direction). 
	 * @param {A,B,Cent} RHR orthonormal system
	 * @param sphrad, spherical radius 
	 * @param end1, end2 Point3D ends
	 * @param gp existing Path2D.Double
	 * @param start=true ==> clear/restart gp
	 * @param N number of steps in drawing full circle by matrix 
	 *  propogation
	 */
	public static void buildSphList(Point3D A,Point3D B,Point3D Cent,
			double sphrad,Point3D end1,Point3D end2,Path2D.Double gp,
			boolean start,int N,CPScreen cpS) {
		
		double a1,extent;
		
		// full circle?
		if (end1==null) {
			a1=0.0;
			extent=2.0*Math.PI;
		}
		else {
			a1=Math.atan2(Point3D.DotProduct(B,end1),
					Point3D.DotProduct(A,end1));
			double a2=Math.atan2(Point3D.DotProduct(B,end2),
					Point3D.DotProduct(A,end2));
			extent=MathComplex.radAngDiff(a1,a2);
		}
		double rad=Math.sin(sphrad);
//		int N=getNumSteps(rad*6.0/wwidth);
		// Here's a 2D circle in AB-coords as seen from Cent direction:
		Vector<Point2D.Double> vec=
			CircleList.getArcList(MathComplex.ZERO,rad,a1,extent,N);
		
		// Now convert to 3D using AB-coords, then project back to 
		//   viewer's yz-coords to put in path. 
		double Y,Z;
		int sz=vec.size();
		int init_i=0;
		double cosr=Math.cos(sphrad);
		double Cy=cosr*Cent.y;
		double Cz=cosr*Cent.z;
		if (start) { // gp is to be reset and started again
			gp.reset();
			if (sz==0) return;
			else { // set first point
				Point2D.Double pt=(Point2D.Double)vec.get(0);
				Y=Cy+pt.x*A.y+pt.y*B.y;
				Z=Cz+pt.x*A.z+pt.y*B.z;
				gp.moveTo(cpS.toPixX(Y),cpS.toPixY(Z));
			}
			init_i=1;
		}
		for (int i=init_i;i<vec.size();i++) {
			Point2D.Double pt=(Point2D.Double)vec.get(i);
			Y=Cy+pt.x*A.y+pt.y*B.y;
			Z=Cz+pt.x*A.z+pt.y*B.z;
			gp.lineTo(cpS.toPixX(Y),cpS.toPixY(Z));
		}
//		gp.closePath();
	}
	
	/**
	 * This routine appends a Point2D.Double vector to existing 'gp'
	 * Path2D.Double; 'start' true then reset and set first point;
	 * else append to 'gp' (which must have current point set already).
	 * @param gp, created in calling routine.
	 */
	public static void circlePoints(Vector<Point2D.Double> vec,
			Path2D.Double gp,boolean start,CPScreen cpS) {
		int sz=vec.size();
		if (sz==0) return;
		if (start) {
			gp.reset();
			Point2D.Double fpt=(Point2D.Double)vec.get(0);
			gp.moveTo(cpS.toPixX(fpt.x),cpS.toPixY(fpt.y));
			for (int i=1;i<sz;i++) {
				fpt=(Point2D.Double)vec.get(i);
				gp.lineTo(cpS.toPixX(fpt.x),cpS.toPixY(fpt.y));
			}
		}
		else {
			for (int i=0;i<sz;i++) {
				Point2D.Double fpt=(Point2D.Double)vec.get(i);
				gp.lineTo(cpS.toPixX(fpt.x),cpS.toPixY(fpt.y));
			}
		}
	}
	
	/** 
	 * Starts or extends a 'Path2D.Double' with points of visible
	 * portion of spherical geodesic
	 * @param sg spherical geodesic (should be 'visible')
	 * @param gp Path2D.Double
	 * @param start true, then reset gp and establish first point
	 * @param N number of steps in drawing full circle by matrix 
	 *  propogation
	 */
	public static void sphGeodesicList(SphGeodesic sg,Path2D.Double gp,
			boolean start,int N,CPScreen cpS) {
		if (start) gp.reset();
		if (sg.lineFlag) {
			Complex a=SphericalMath.sphToVisualPlane(sg.z1.x,sg.z1.y);
			Complex b=SphericalMath.sphToVisualPlane(sg.z2.x,sg.z2.y);
			if (start) 
				gp.moveTo(cpS.toPixX(a.x),cpS.toPixY(a.y));
			else gp.lineTo(cpS.toPixX(a.x),cpS.toPixY(a.y));
			gp.lineTo(cpS.toPixX(b.x),cpS.toPixY(b.y));
			return;
		}
		else {
			if (sg.followHorizon()!=0) { // geodesic lies along horizon; 
				// if straight line, already handled above
//				int N=getNumSteps(6.0/wwidth);
				Vector<Point2D.Double> vec=
					CircleList.getUnitArcList(sg.z1,sg.z2);
				CircleList.circlePoints(vec,gp,start,cpS);
			}
			else {
				// find an orthonormal basis; note it is easier 
				//   here for geodesic circles than for other circles.
				Point3D C=new Point3D(sg.center);
				double plen=Math.sqrt(C.y*C.y+C.z*C.z);
				// unit orthog to yz-proj of C
				Point3D A=new Point3D(0.0,-C.z/plen,C.y/plen); 
				Point3D B=Point3D.CrossProduct(C,A);
				
				// both ends should be visible
				Point3D end1=new Point3D(sg.z1);
				Point3D end2=new Point3D(sg.z2);
				
				// create path
				CircleList.buildSphList(A,B,new Point3D(0,0,0),
						piby2,end1,end2,gp,start,N,cpS);
			}
		}
	}
	
	/**
	 * The 'footprint' is size of the full circle from which an arc will be drawn
	 * relative to the canvas size; typically footprint=rad*6/Xwidth. Note that in
	 * various routines the number of steps actually used is in proportion to
	 * the 'extent' of the arc itself.
	 * @param footprint, size of full circle relative to canvas real world window
	 * @return number of steps to use in polygon approximating circular arc.
	 */
	public static int getNumSteps(double footprint) {
		if (footprint<.001) return 3;
		if (footprint<.01) return 8;
		if (footprint<.05) return 16;
		if (footprint<.1) return 32;
		if (footprint<.5) return 64;
		return 128; 
	}
}
