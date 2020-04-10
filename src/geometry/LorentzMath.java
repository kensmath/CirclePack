package geometry;

import math.Point4D;

import complex.Complex;

/**
 * Beginning now (12/2012) to build computations for geometry based on 
 * Lorentzian geometry. I expect to use these in certain situations now
 * and consider using them to replace older routines as I build up 
 * the procedures and reliability. The generality, linearity, and
 * associated "geometric algebra" are advantages. (Probably should have 
 * transitioned years ago!)
 * 
 * We model hyperbolic 3-space as R^4 with the Lorentz quadratic form
 * Q(t,x,y,z)=t^2-x^2-y^2-z^2 of signature (1,3) and the associated
 * bilinear form (t,x,y,z).(t',x',y',z')=tt'-xx'-yy'-zz'.
 * NOTE: in the literature, both this Q and its negative, signature (3,1),
 * are used. E.g., see papers by Hestenes.
 * 
 * The light cone is the cone of null vectors Q(v)=0. The hyperboloid
 * model of hyperbolic geometry is the sheet S+ of the hyperboloid
 * Q(v)=1, t^2-x^2-y^2-z^2=1, with t>0. The identification is via
 * projection from (-1,0,0,0): namely if x^2+y^2+z^2<1, then
 *   (x,y,z) <---> (s,sx,sy,sz) where s=1/sqrt(1-x^2-y^2-z^2).
 * S+ is asymptotic to the light cone as t grows so its boundary is
 * the unit sphere.
 * 
 * Discs are represented as points, namely
 * 
 *   {(t,x,y,z): t^2-x^2-y^2-z^2 = -1}, t>0
 *   
 * Boundary of disc (t,x,y,z) is 
 * 
 *   {(a,b,c):a^2+b^2+c^2=1, ax+by+cz=t}
 *   
 * where t/(x^2+y^2+z^2)=cos(r), r = sph radius.
 * 
 * 
 * For the 2D hyperbolic model simply set z=0.
 * 
 *  
 * See also 'Point4D'.
 * @author kens
 *
 */
public class LorentzMath {
	
	/**
	 * Given two circles (as Point4D), find inversive distance between them.
	 * @param c1 Point4D
	 * @param c2 Point4D
	 * @return double
	 */
	public static double invDistLM(Point4D c1,Point4D c2) {
		return Complex.aCosh(c1.t*c2.t-c1.x*c2.x-c1.y*c2.y-c1.z*c2.z);
	}
	

}
