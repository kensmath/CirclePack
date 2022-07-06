package math;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import exceptions.MiscException;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;

/**
 * CirMatrix holds the representation of a circle as a 2x2
 * Hermitian complex matrix. This extends 'Mobius' because circles in
 * this form can be manipulated via compositions with Mobius
 * transformations. See, e.g., 'mobius_of_circle'.
 * 
 * Note: or purposes of drawing, all straight lines are 
 * stored in 'CircleSimple' objects as very large circles,
 * but with 'lineflag' true: the center is FAUX_RAD times 
 * the unit normal pointing to the interior, radius is 
 * adjusted to give proper distance from the origin. 
 * 
 * -------------------------------
 * Math of 'CirMatrix':
 * 
 * * Straight line a*x+b*y+c=0 can be rewritten using B=(a-i*b)/2 as  
 *         B*z+conj(B)*conj(z)+c=0
 *   We can normalize by replacing (a,b) by (a,b)/sqrt(a^2+b^2)
 *   and c by c/sqrt(a^2+b^2), so |B|=1/2. 
 * 
 * * Circle with center z0, radius r is (z-z0)*conj(z-z0)=r^2.
 *   This can be written using B=-conj(z0) and g=z0*conj(z0)-r^2 as
 *         z*conj(z)+B*z+conj(B)*conj(z)+g=0.
 * 
 * * Both together: A*z*conj(z)+B*z+conj(B)*conj(z)+g=0 where A=+-1 
 *   for circle, a=0 for line.
 * 
 * So we represent all circles/lines by matrix M=[a,b;c,d], with
 * entry a is +-1 or 0.
 * 
 * When a=+-1:
 *  +1 ==> normal circle (interior inside circle)
 * 		entry b: -conj(z0)
 * 		entry c: -z0 = conj(b)  (hence hermitian)
 * 		entry d: always real = |z0|^2 - r^2 (real); 
 * 				 0 ==> circle goes through origin
 *	Conventions on inside/outside: 
 *      a=1,  inside of normal circle, note det = -r*r
 *	    a=-1, outside (normal circle but with entries 
 *    	      multiplied by -1)
 *    
 * When a=0:
 *   This means circle is a line through infinity.
 *   
 *   Normalization first: multiply all entries by 1/|b| so
 *      that cross diagonal entries have magnitude 1.
 *      Thus, get into form [0 B; conj(B) d] with |B|=1.
 *   
 *   Now:
 *   	n = conj(B) is a unit normal to the line.
 *   
 *   Conventions on inside/outside:
 *      n points towards "interior" of the circle
 *      -d*n/2 is a point on the line, thus 
 *           d=0: line goes through the origin
 *           d>0: origin is on the interior
 *           d<0: origin is exterior
 * (These are not automatic, code has to keep track case-by-case and
 * adjust the matrix.)   
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
	
	// for eucl circle (or straight line if 'lineflag' true)
	public CirMatrix(CircleSimple cs) { 
		super(); // a=1.0
		Complex cent=new Complex(cs.center);
		double rad=cs.rad;
		if (cs.lineFlag) {
			Complex unitnormal=cent.divide(CPBase.FAUX_RAD);
			// diff > 0 means origin is "interior"
			double diff=2.0*(rad-CPBase.FAUX_RAD);
			this.a=new Complex(0.0);
			this.b=unitnormal.conj();
			this.c=unitnormal;
			this.d=new Complex(-diff);
		}
		else {
			this.b=cent.conj().times(-1.0);
			this.c=cent.times(-1.0);
			this.d=new Complex((double)cent.absSq()-rad*rad);
		}
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
	 * For C a 2x2 'CirMatrix', this returns its image under M 
	 * (oriented true) or M^{-1} (oriented false). Returns a 2x2
	 * 'CirMatrix' with the normalization conventions.
	 * 
	 * The computation is: result = G^{t}*C*conj(G), where G = M^{-1}*det(M)
	 * Note: If M = [a b;c d], then G=[d -b;-c a] and G^{t}=[d -c;-b a] (not
	 * the hermitian transpose).
	 * 
	 * @param M Mobius
	 * @param C CirMatrix
	 * @param oriented boolean, false ==> use Mob^{-1}
	 * @return CirMatrix
	 */
	public static CirMatrix applyTransform(Mobius M, CirMatrix C, boolean oriented) {
		// get working Mobius
		Mobius MM=M.cloneMe();
		if (!oriented) // want M^{-1}?
			MM=(Mobius)MM.inverse();
		boolean outsideC=false; // disc is outside of incoming C
		Complex pt=new Complex(0.0);
		
		// get working CirMatrix CC as normal circle (or straight line)
		//   and find reference pt "inside"
		CirMatrix CC=new CirMatrix();
		CC.a=new Complex(C.a);
		CC.b=new Complex(C.b);
		CC.c=new Complex(C.c);
		CC.d=new Complex(C.d);
		
		if (C.a.x==-1) { // outside of original circle
			outsideC=true;
			CC.a=CC.a.times(-1.0);
			CC.b=CC.b.times(-1.0);
			CC.c=CC.c.times(-1.0);
			CC.d=CC.d.times(-1.0);
		}
		if (C.a.x==0) { // original is a line
			pt=C.c.times(Math.abs(C.d.x)*20); // move in direction of normal 
		}
		else pt=CC.c.times(-1.0);

		// define G=MM^(-1) * det(MM)
		Mobius G = new Mobius(); 
		G.a = new Complex(MM.d);  
		G.b = MM.b.times(-1.0);
		G.c = MM.c.times(-1.0);
		G.d = new Complex(MM.a);

		// compute G^{t}*C: do NOT use usual matrix 'rmult/lmult': 
		//    then normalize
		CirMatrix tmpC=new CirMatrix();
		tmpC.a=(G.a.times(CC.a)).plus(G.c.times(CC.c));
		tmpC.b=(G.a.times(CC.b)).plus(G.c.times(CC.d));
		tmpC.c=(G.b.times(CC.a)).plus(G.d.times(CC.c));
		tmpC.d=(G.b.times(CC.b)).plus(G.d.times(CC.d));
		
		// compute tmpC*conj(G)
		G.conj();
		CirMatrix outC = new CirMatrix();
		outC.a=(tmpC.a.times(G.a)).plus(tmpC.b.times(G.c));
		outC.b=(tmpC.a.times(G.b)).plus(tmpC.b.times(G.d));
		outC.c=(tmpC.c.times(G.a)).plus(tmpC.d.times(G.c));
		outC.d=(tmpC.c.times(G.b)).plus(tmpC.d.times(G.d));
		
		// due to round off, have to identify lines, then normalize
		if (outC.a.abs()<.0001) {
			outC.a=new Complex(0.0);
			double divisor=outC.c.abs();
			outC.b=outC.b.divide(divisor);
			outC.c=outC.c.divide(divisor);
			outC.d=new Complex(outC.d.divide(divisor).x); // should be real
		}
		
		// else should be a regular circle; normalize for roundoff 
		//     and to insure a = +1
		else {Complex scalar=outC.a.reciprocal();
			outC.a=new Complex(1.0);
			outC.b=outC.b.times(scalar);
			outC.c=outC.c.times(scalar);
			outC.d=outC.d.times(scalar);
		}
		
		// See if reference point for CC is mapped inside outC
		//   and adjust accordingly. If it's inside outC but 
		//   but 'outsideC' is set, of if outC is a line and 
		//   pt is not inside, then multiply by -1. 
		Complex MMpt=MM.apply(pt); // outC.getCenter();
		int cent_inside=pt_inside(outC,MMpt); // outC.getRadius();
		if ((cent_inside==1 && outsideC) || 
				cent_inside==-1) { // && outC.a.x==0) { // put on same side of both
			outC.a=outC.a.times(-1.0);
			outC.b=outC.b.times(-1.0); // cent_inside=1.0;
			outC.c=outC.c.times(-1.0);
			outC.d=outC.d.times(-1.0);
		}

		return outC;
	}

	/**
	 * Create 2x2 matrix representation of circle given sph 
	 * radius/center; normalize for inside/outside conventions.
	 * 
	 * @param center Complex, (theta,phi) form
	 * @param rad double
	 * @param C CirMatrix, instantiated by calling routine
	 * @return int 1
	 */
	public static CirMatrix sph2CirMatrix(Complex center, double rad) {

		CirMatrix C=new CirMatrix();
		C.oriented =true;

		// goes through S pole, so projects to straight line
		if (Math.abs(center.y + rad - Math.PI) < CM_TOLER) {
			
			C.a=new Complex(0.0);
			// set b unit length so conj(b) is towards center
			double theta=center.x;
			C.b=new Complex(Math.cos(theta),-Math.sin(theta));
			C.c=C.b.conj();

			// contains N pole? line through origin
			if (Math.abs(rad - Math.PI / 2.0) < CM_TOLER) 
				return C;

			// straight line, but NOT through origin
			double R = Math.sin(center.y - rad) / (1 + Math.cos(center.y - rad));
			// R = signed eucl distance from origin to the line, 
			C.d=new Complex(R/(-2.0),0);
			return C;
			// when C.d.x>0, origin is inside the halfplane, else outside
		} 

		// project to circle
		CircleSimple sc = SphericalMath.s_to_e_data(center, rad);
		Complex ez = sc.center;
		double R = sc.rad;
		if (sc.flag==-1)
			R *=-1.0; // set negative
			
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
		return C;
	}
 
	/**
	 * Determine if pt is inside, on, or outside of C.
	 * if |pt|>10^{8}, consider it to be infinity.
	 * @param C CirMatrix, in normalized form
	 * @param pt Complex 
	 * @return double: >0 for inside, 0 for on, <0 for outside
	 */
	public static int pt_inside(CirMatrix C,Complex pt) {
		
		if (Double.isNaN(pt.x) || Double.isNaN(pt.y))
			pt.x=pt.y=1000000000.0;

		if (C.a.abs()!=0 && Math.abs(C.a.abs()-1.0)>.000000000001) 
			throw new MiscException("'C' does not seem to be normalized.");

		int ans=1;
		
		// regular circle? C.a=+-1
		if (C.a.abs()>CM_TOLER) {
			double rad=Math.sqrt(C.a.x*(C.b.absSq()-C.d.x));
			if (pt.abs()>100000000.0)
				ans=-1;
			else {
				double dist=pt.add(C.c).abs(); // C.c= -center
				if (dist>(rad+CM_TOLER)) // outside
					ans=-1;
				else if (dist>(rad-CM_TOLER)) // on
					ans=0;
			}
			return (int)(C.a.x*ans); // swap if C bounds its outside
		}
		
		// line? C.c is unit normal into interior, so see if vector
		//    to point has dot product with it which is > +1
		if (pt.abs()>10000000.0)
			return 0; // infinity is on the line
		double dot=pt.x*C.c.x+pt.y*C.c.y;
		double transdist=-C.d.x/2.0;
		if (dot<transdist-CM_TOLER) // outside
			ans=-1;
		else if (dot<transdist+CM_TOLER) // on the line
			ans=0;
		return ans;
	}

	/**
	 * Convert 'CirMatrix' to 'CircleSimple' in requested geometry.
	 * If a straight line in eucl case: set lineflag, multiply 
	 * conj(b) by FAUX_RAD for 'center', and set 'rad' to 
	 * FAUX_RAD+d/2. 
	 * @param CC CirMatrix, 2x2 representation of a circle
	 * @param hes int, geometry
	 * @return CircleSimple, null on error (e.g. improper hyp case)
	 */
	public static CircleSimple cirMatrix_to_geom(CirMatrix CC,int hes) {
		if (CC==null)
			return null;
		CircleSimple outCS=new CircleSimple();
		
		// typical data
		Complex ecent=CC.c.times(-1.0);  // c entry is -center
		double reald=CC.d.x; // throw out any extraneous imaginary part
		if (CC.a.x<.5) { // a.x=-1 ==> all entries were multiplied by -1 
			ecent=ecent.times(-1.0);
			reald *=-1.0;
		} 
		double rsq=ecent.absSq()-reald; // rad^2
		if (CC.a.x!=0 && rsq<=0) {
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
		if (CC.a.x==0) { // yes, is a line
			outCS.lineFlag=true;
			Complex unitnormal=CC.c;
			outCS.center=unitnormal.times(CPBase.FAUX_RAD);  // unit normal toward interior
			
			outCS.rad=CPBase.FAUX_RAD+(CC.d.x/2.0); // FAUX_RAD - signed distance from origin
			return outCS;
		}
		outCS.center=ecent; 
		outCS.rad=erad*CC.a.x; // may be negative if a=-1
		return outCS;
	}
	
	/**
	 * If a.x==0, this is a straight line and we use large
	 * circle using FAUX_RAD. Also, recall, a.x=-1 ==> all 
	 * entries were multiplied by -1.
	 * @return double
	 */
	public double getRadius() {
		Complex ecent=c.times(-1.0*a.x); // c entry is -center
		double reald=d.x*a.x; // throw out any extraneous imaginary part
		double radius=Math.sqrt(ecent.absSq()-reald); // rad^2
		if (a.x==0) {
			// FAUX_RAD - signed distance from origin
			radius=CPBase.FAUX_RAD+(d.x/2.0); 
		}
		return radius;
	}
	
	/**
	 * If a.x==0, this is a straight line and we use a fake
	 * center FAUX_RAD distance out. Also, recall, a.x=-1 ==> all 
	 * entries were multiplied by -1.  
	 * @return
	 */
	public Complex getCenter() {
		Complex center=c.times(-1.0*a.x);
		if (a.x==0) 
			center=c.times(CPBase.FAUX_RAD);  // unit normal toward interior
		return center;
	}
	
}
