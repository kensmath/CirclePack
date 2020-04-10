package util;

import math.Matrix3D;
import math.Point3D;
import panels.CPScreen;

import complex.Complex;

/**
 * For maintaining viewpoint for spherical packings. Spherical 
 * centers are given as complex numbers, but really they are 
 * (theta, phi) pairs; radii are angles from 0 to Pi. (Everything 
 * is done in radian measure.)
 * @author kens
 */
public class SphView {

	CPScreen parent;    // parent canvas
	public Matrix3D viewMatrix;  // maintains information on current view of sphere 
	
	// Constructor
	public SphView() {
		defaultView(); 
	}
	
	public void setParent(CPScreen cps) {
		parent=cps;
	}
	
	/**
	 * The default spherical view is from the positive x direction,
	 * with slight tip towards the viewer and to the left
	 */
	public void defaultView() {
		viewMatrix=Matrix3D.FromEulerAnglesXYZ(0.0,0.1*Math.PI,-0.03*Math.PI);
	}

	/**
	 * Return new Complex giving coords of visual plane (i.e., (y,z) coords in 
	 * 3-space) for given point pt=(theta,phi) from the apparent sphere.
	 * @param pt, complex (theta,phi) (from apparent sphere.
	 * @return new Complex (y,z) in 3-space (x-axis toward the viewer).
	 */
	public static Complex s_pt_to_visual_plane(Complex s_pt) {
		return new Complex(Math.sin(s_pt.y)*Math.sin(s_pt.x),Math.cos(s_pt.y));
	} 
	
	/**
	 * Return (theta,phi) (on apparent sphere) when given pt=(y,z) on visual 
	 * plane. If |pt|>1, project point to unit circle first.
	 * @param pt= Complex (y,z) 
	 * @return (theta,phi) on apparent sphere
	 */
	public static Complex visual_plane_to_s_pt(Complex pt) {
		double abz=pt.abs();
		if (abz>1.0) pt=pt.divide(abz);
		if (Math.abs(Math.abs(pt.y)-1)<.00000000001) { // one of poles
			if (pt.y<0) return new Complex(0,Math.PI);
			return new Complex(0.0);
		}
		// formulae for pt (x,y,z): theta=asin(y/sqrt(1-z^2)), phi=acos(z)
		return new Complex(Math.asin(pt.x/Math.sqrt(1.0-pt.y*pt.y)),Math.acos(pt.y));
	}
	
	/**
	 * Convert pt (theta,phi) on the real sphere to new Complex (theta,pi)
	 * on the apparent sphere using viewMatrix. (Check: cos(theta)<0 implies 
	 * pt is on back of apparent sphere.)
	 * @param sph_pt (theta,phi)
	 * @return new Complex (theta,phi)
	 */
	public Complex toApparentSph(Complex sph_pt) {
		Point3D pt=new Point3D(Math.sin(sph_pt.y)*Math.cos(sph_pt.x),
				Math.sin(sph_pt.y)*Math.sin(sph_pt.x),
						Math.cos(sph_pt.y));
		Point3D new_pt=Matrix3D.times(viewMatrix,pt);
		return new Complex(Math.atan2(new_pt.y,new_pt.x),Math.acos(new_pt.z)); 
	}

	/**
	 * Converts point on the apparent sphere to new Complex (theta,phi)
	 * on the real sphere using transpose (inverse) of viewMatrix.
	 * @param sph_pt (theta,phi)
	 * @return new Complex (theta,phi)
	 */
	public Complex toRealSph(Complex sph_pt) {
		Point3D pt=new Point3D(Math.sin(sph_pt.y)*Math.cos(sph_pt.x),
				Math.sin(sph_pt.y)*Math.sin(sph_pt.x),
						Math.cos(sph_pt.y));
		Point3D new_pt=Matrix3D.times(viewMatrix.Transpose(),pt);
		return new Complex(Math.atan2(new_pt.y,new_pt.x),Math.acos(new_pt.z)); 
	}

}
