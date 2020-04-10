package math;

import complex.Complex;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import geometry.SphericalMath;

/**
 * CirMatrix holds the representation of a circle as a 2x2
 * Hermitian complex matrix. This extends 'Mobius' because circles in
 * this form can be manipulated via compositions with Mobius
 * transformations. See, e.g., 'mobius_of_circle'.
 * 
 * Math: 
 * 
 * * Straight line a*x+b*y+c=0 can be rewritten using B=(a-i*b)/2 as  
 *         B*z+conj(B)*conj(z)+c=0
 * 
 * * Circle with center z0, radius r is (z-z0)*conj(z-z0)=r^2.
 *   This can be written using B=conj(-z0) and g=z0*conj(z0)-r^2 as
 *         z*conj(z)+B*z+conj(B)*conj(z)+g=0
 * 
 * * Both together: A*z*conj(z)+B*z+conj(B)*conj(z)+g=0 where A=1 for circle
 * 
 * So we represent circle, center z0, radius r as [a,b;c,d] where
 * 
   * entry a: always real
   *   0 ==> straight line (goes through S pole)
   *   +1 ==> normal circle (interior inside circle)
   * entry b:
   *   -conj(z0)
   * entry c:
   *   -z0
   * entry d: always real
   *   |z0|^2 - r^2 (real); 0 ==> goes through origin
   *   -1 ==> normal circle (interior is outside circle)
   * like normal circle above, but all entries multiplied by -1
   *      
   * Conventions on inside/outside: 
   *    a=1, inside of normal circle, note det = -r*r
   *    a=-1, outside (normal circle with entries multiplied by -1)
   *    a=0 is line, conj(b) is unit length, points towards interior, -2*d is
   *        signed translation distance in direction of conj(b).
   *        d=0, through origin
   *    	d>0, origin is interior
   *        d<0, line, origin is exterior
   * These are not automatic, code has to keep track case-by-case and
   * adjust the matrix.   
   * 
 * @author kens
 *
 */
public class CirMatrix extends Mobius {
	
	public static final double CM_TOLER = .000000001;

	// Constructor(s)
	public CirMatrix() {
		super(); // start identity matrix
	}
	
	// for eucl data
	public CirMatrix(Complex z,double r) { // for interior eucl circle
		super();
		this.b=z.conj().times(-1.0);
		this.c=z.times(-1.0);
		this.d=new Complex((double)z.absSq()-r*r);
	}
	
	// for any geometry
	public CirMatrix(Complex z,double r,int hes) { // for interior eucl circle
		super();
		CircleSimple sC=new CircleSimple();
		if (hes<0) 
			sC=HyperbolicMath.h_to_e_data(z, r);
		else if (hes>0)
			sC=SphericalMath.s_to_e_data(z, r);
		this.b=sC.center.conj().times(-1.0);
		this.c=sC.center.times(-1.0);
		this.d=new Complex((double)sC.center.absSq()-sC.rad*sC.rad);
	}
	
	// clone
	public CirMatrix(Mobius mb) {
		super(); 
		a=mb.a;
		b=mb.b;
		c=mb.c;
		d=mb.d;
		oriented=mb.oriented;
	}
	
	/** 
	 * C is circle in 2x2 matrix form. This returns the image 
	 * of C under M (oriented true) or M^{-1} (oriented false)
	 * as a 2x2 'CirMatrix' with the normalization conventions
	 * satisfied.
	 * 
	 * The computation is: result = G^{t}*C*conj(G), where G = M^{-1}*det(M)
	 * Note: If M = [a b;c d], then G=[d -b;-c a] and G^{t}=[d -c;-b a] (not
	 * hermitian transpose).
	 * 
	 * @param M Mobius
	 * @param C CirMatrix
	 * @param oriented boolean, false ==> use Mob^{-1}
	 * @return CirMatrix, normalized
	 */
	public static CirMatrix applyTransform(Mobius M, CirMatrix C, boolean oriented) {
		Mobius MM=M.copyMob();
		if (!oriented) // want M^{-1}?
			MM=(Mobius)MM.inverse();

		// define G=MM^(-1) * det(MM)
		Mobius G = new Mobius(); 
		G.a = new Complex(MM.d);  
		G.b = new Complex(-1.0 * MM.b.x, -1.0*MM.b.y);
		G.c = new Complex(-1.0 * MM.c.x, -1.0*MM.c.y);
		G.d = new Complex(MM.a);

		// compute G^{t}*C: can't use usual matrix 'rmult/lmult': they normalize
		CirMatrix tmpC=new CirMatrix();
		tmpC.a=(G.a.times(C.a)).plus(G.c.times(C.c));
		tmpC.b=(G.a.times(C.b)).plus(G.c.times(C.d));
		tmpC.c=(G.b.times(C.a)).plus(G.d.times(C.c));
		tmpC.d=(G.b.times(C.b)).plus(G.d.times(C.d));
		
		// compute tmpC*conj(G)
		G.conj();
		CirMatrix outC = new CirMatrix();
		outC.a=(tmpC.a.times(G.a)).plus(tmpC.b.times(G.c));
		outC.b=(tmpC.a.times(G.b)).plus(tmpC.b.times(G.d));
		outC.c=(tmpC.c.times(G.a)).plus(tmpC.d.times(G.c));
		outC.d=(tmpC.c.times(G.b)).plus(tmpC.d.times(G.d));
		
		// basic normalization
		if (outC.a.abs()<CM_TOLER) // straight (within tolerance)?
			outC.a=new Complex(0.0);
		else { // regular circle, set a to 1
			Complex scalar=outC.a.reciprocal();
			outC.a=new Complex(1.0);
			outC.b=outC.b.times(scalar);
			outC.c=outC.c.times(scalar);
			outC.d=outC.d.times(scalar);
		}
		
		// C a regular circle? See if center is inside outC
		if (C.a.abs()>CM_TOLER) {
			double cent_inside=pt_inside(outC,MM.apply(C.c.times(-1.0)));
			if (cent_inside*C.a.x<0.0) { // put on same side
				outC.a=outC.a.times(-1.0);
				outC.b=outC.b.times(-1.0);
				outC.c=outC.c.times(-1.0);
				outC.d=outC.d.times(-1.0);
			}
			return outC;
		}
		
		// else C is a line
		
		// is outC a regular circle? reverse procedure above
		if (outC.a.abs()>CM_TOLER) {
			Complex inv_cent=((Mobius)(MM.inverse())).apply(outC.b.conj());
			double cent_inside=pt_inside(C,inv_cent);
			if (cent_inside<0.0) {
				outC.a=outC.a.times(-1.0);
				outC.b=outC.b.times(-1.0);
				outC.c=outC.c.times(-1.0);
				outC.d=outC.d.times(-1.0);
			}
			return outC;
		}
		
		// else outC is also a line
		// translation length -2*C.d in direction of conj(C.b).
		double trans=-2.0*C.d.x;
		Complex ptinC=C.b.conj().times(1+trans); // point inside C.
		Complex ptoutC=MM.apply(ptinC); // image point inside outC
		// compare dot product conj(outC.b).ptoutC to 
		if (outC.b.x*ptoutC.x-outC.b.y*ptoutC.y<(-2.0*outC.d.x)) {
			outC.a=outC.a.times(-1.0);
			outC.b=outC.b.times(-1.0);
			outC.c=outC.c.times(-1.0);
			outC.d=outC.d.times(-1.0);
		}
		return outC;
	}

	/**
	 * Create 2x2 matrix representation of circle given sph radius/center;
	 * normalize for inside/outside conventions.
	 * 
	 * @param center Complex, (theta,phi) form
	 * @param rad double
	 * @param C CirMatrix, instantiated by calling routine
	 * @return int 1
	 */
	public static int s_to_matrix_data(Complex center, double rad, CirMatrix C) {

		C.a = new Complex(0.0);
		C.b = new Complex(0.0);
		C.c = new Complex(0.0);
		C.d = new Complex(0.0); // note: C.c is set at end
		C.oriented = true;

		// goes through S pole, so projects to straight line
		if (Math.abs(center.y + rad - Math.PI) < CM_TOLER) {
			
			// set b unit length so conj(b) is towards center
			double theta=center.x;
			C.b.x=Math.cos(theta);
			C.b.y=-Math.sin(theta);
			C.c=C.b.conj();

			// contains N pole? line through origin
			if (Math.abs(rad - Math.PI / 2.0) < CM_TOLER) 
				return 1;

			// straight line, but NOT through origin
			double R = Math.sin(center.y - rad) / (1 + Math.cos(center.y - rad));
			// R = signed eucl distance from origin to the line, 
			C.d.x=R/(-2.0);
			return 1;
			// when C.d.x>0, origin is inside, else outside
		} 

		// project to circle
		CircleSimple sc = SphericalMath.s_to_e_data(center, rad);
		Complex ez = sc.center;
		double R = sc.rad;
			
		// this represents the circle
		C.a=new Complex(1.0);
		C.b = new Complex(-ez.x, ez.y); // B= - conj(z)
		C.d = new Complex(ez.abs() * ez.abs() - R * R);
		C.c=C.b.conj();
			
		// if we want exterior, multiply through by -1
		if (R<0) {
			C.a=C.a.times(-1.0);
			C.b=C.b.times(-1.0);
			C.c=C.c.times(-1.0);
			C.d=C.d.times(-1.0);
		}
		return 1;
	}
 
	/**
	 * Given 2x2 matrix form of circle satisfying all our conventions, 
	 * find sph radius/center. See the conventions about inside/outside, 
	 * signs, etc.
	 * 
	 * @param C CirMatrix, 
	 * @param sC SimpleCircle, instantiated by calling routine
	 * @return 1
	 */
	public static int matrix_to_s_data(CirMatrix C, CircleSimple sC) {

		// circle is a straight line (goes through south pole)
		if (C.a.abs() < CM_TOLER) {

			// through origin? Hence a hemisphere
			if (C.d.abs() < CM_TOLER) {
				sC.center.y = sC.rad = Math.PI / 2.0;
				sC.center.x=C.b.conj().arg();
				return 1;
			} 

			// straight line, but NOT through origin
			double R=C.d.abs(); // distance to origin
			double theta=Math.atan2(-1.0*C.b.y,C.b.x);
			double atn=Math.atan(R);
			double rho=Math.PI/2.0-atn; 
			if (C.d.x<0) // encloses origin (north pole)?
				rho+=2.0*atn;
			sC.rad=rho;
			sC.center=new Complex(theta,Math.PI-rho);

			return 1;
		} // end of 'straight line' cases

		// else a circle
		boolean outside=false;
		if (C.a.x<0) { // want outside
			outside=true;
			C.a=C.a.times(-1.0);
			C.b=C.b.times(-1.0);
			C.c=C.c.times(-1.0);
			C.d=C.d.times(-1.0);
		}
		
		// find euclidean data
		Complex z=C.c.times(-1.0);
		double erad = Math.sqrt(z.absSq() - C.d.x); // C.d should be real
		
		// convert
		CircleSimple sc=SphericalMath.e_to_s_data(z, erad);
		sC.center=sc.center;
		sC.rad=sc.rad;
		if (outside) {
			sC.center=SphericalMath.getAntipodal(sc.center);
			sC.rad=Math.PI-sc.rad;
		}
		return 1;
	}
	
	/**
	 * Determine if pt is inside, on, outside C.
	 * if |pt|>10^{8}, consider it to be infinity.
	 * @param C CirMatrix, in normalized form
	 * @param pt Complex 
	 * @return double: >0 for inside, 0 for on, <0 for outside
	 */
	public static double pt_inside(CirMatrix C,Complex pt) {
		
		if (Double.isNaN(pt.x) || Double.isNaN(pt.y))
			pt.x=pt.y=1000000000.0;

		double ans=1.0;
		
		// regular circle? C.a=+-1
		if (C.a.abs()>CM_TOLER) {
			double rad=Math.sqrt(C.b.absSq()-C.d.x);
			if (pt.abs()>100000000.0)
				ans=-1.0;
			else {
				double dist=pt.add(C.b.conj()).abs();
				if (dist>(rad+CM_TOLER)) // outside
					ans=-1.0;
				else if (dist>(rad-CM_TOLER)) // on
					ans=0.0;
			}
			return (int)(C.a.x*ans); // swap if C bounds its outside
		}
		
		// line? conj(C.b) points into interior, so see if vector
		//    to point has dot product with it which is > +1
		if (pt.abs()>10000000.0)
			return 0;
		double dot=pt.x*C.b.x-pt.y*C.b.y;
		double transdist=-2.0*C.d.x;
		if (dot<transdist-CM_TOLER) // outside
			ans=-1.0;
		else if (dot<transdist+CM_TOLER) // on the line
			ans=0.0;
		return ans;
	}
	
	/** Normalize so a is 1, -1, or zero
	 * @param C CirMatrix
	 * @return CirMatrix
	 */
	public static CirMatrix normalize(CirMatrix C) {
		CirMatrix Cout=new CirMatrix(C);
		if (C.a.abs()<CM_TOLER) {
			Cout.a=new Complex(0.0);
			return Cout;
		}
		Cout.b =Cout.b.divide(C.a);
		Cout.c =Cout.c.divide(C.a);
		Cout.d =Cout.d.divide(C.a);
		
		if (C.a.minus(-1.0).abs()<CM_TOLER) // intended to be -1?
			C.a=new Complex(-1.0);
		else
			C.a=new Complex(1.0);
		return Cout;
	}
	
	/**
	 * Return euclidean data for inner/outer circle or line.
	 * @param C CirMatrix
	 * @return SimpleCircle, null on error
	 */
	public static CircleSimple euclCircle(CirMatrix C) {
		if (C==null)
			return null;
		CirMatrix CC=normalize(C); // a should be +-1 or 0
		if (CC==null)
			return null;
		CircleSimple sp=new CircleSimple();
		if (CC.a.x==0 && CC.a.y==0) {
			sp.lineFlag=true;
			sp.center=CC.b.conj();  // unit normal toward interior
			sp.rad=-2.0*CC.d.x;     // signed distance from origin
			return sp;
		}
		sp.center=CC.c.times(-1.0*CC.a.x); // a=-1 for outside case 
		double reald=CC.d.x*CC.a.x; // throw out any imaginary part
		sp.rad=Math.sqrt(sp.center.absSq()-reald)*CC.a.x;
		return sp;
	}

}
