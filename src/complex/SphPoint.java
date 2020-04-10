package complex;

import math.Matrix3D;
import math.Point3D;

/**
 * Represents a point on the sphere, giving real world (theta,phi)
 * and (x,y) on the apparent sphere (which requires a Matrix3D for
 * sphere orientation).
 * Note: the apparent sphere is ephemeral; recompute (x,y) as needed
 * for use. 'onFront' indicates front or back of apparent sphere.
 * @author kens
 *
 */
public class SphPoint extends Complex {
	boolean onFront;

	// Constructors
	public SphPoint(Complex tp,Matrix3D trans) {
		onFront=true;
		// convert to point on apparent sphere 
		Complex z=matrix3Dz(true,trans,tp);
		x=z.x;
		y=z.y;
		if (Math.cos(x)<0) onFront=false;
	}
	
	public SphPoint(double th,double ph,Matrix3D trans) {
		this(new Complex(th,ph),trans);
	}
	
	public SphPoint() {
		this(0.0,0.0,Matrix3D.Identity());
	}
	
	public SphPoint(Complex z) {
		this(z.x,z.y,Matrix3D.Identity());
	}
	
	/**
	 * Move to canvas (assuming (x,y) are up to date)
	 * @param z
	 * @return
	 */
	public Complex toCanvas() {
		return new Complex(Math.sin(y)*Math.sin(x),Math.cos(y));
	}
	
	/**
	 *  Given theta and phi, return visual screen location (i.e., (y,z))
	 *  This also sets 'onFront' flag.  
	 */
	public Complex toVisualPlane() {
		if (Math.cos(x)<0) onFront=false;
		return new Complex (Math.sin(y)*Math.sin(x),Math.cos(y)); 
	}
	
	/**
	 * TODO: I don't think I call this: see toApparentSph and toRealSph
	 * in SphView.java.
	 * 
	 * Connect points on real and on apparent spheres. When toA==true,
	 * takes actual spherical point, (theta,phi), applies orthogonal 
	 * transformation to give the spherical point on the apparent sphere;
	 * vice-verse if toA==false. (see old 'ss_view').
	 * @boolean, true means going TO apparent sphere
	 * @param trans, orthog. trans
	 * @param sph_pt
	 * @return
	 */
	public static Complex matrix3Dz(boolean toA,Matrix3D trans,Complex sph_pt) {
		Point3D new_pt;
		Point3D pt=new Point3D(Math.sin(sph_pt.y)*Math.cos(sph_pt.x),
						Math.sin(sph_pt.y)*Math.sin(sph_pt.x),
								Math.cos(sph_pt.y));
		if (toA) 
			new_pt=Matrix3D.times(trans,pt);
		else
			new_pt=Matrix3D.times(trans.Transpose(),pt);
		return new Complex(Math.atan2(new_pt.y,new_pt.x),Math.acos(new_pt.z)); 
	}
}
