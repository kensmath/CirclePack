package math;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import complex.MathComplex;
import exceptions.DataException;
import exceptions.MobException;
import ftnTheory.SchwarzMap;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import math.group.ComplexTransformation;
import math.group.GroupElement;
import packing.PackData;
import packing.Schwarzian;

/**
 * Mobius transformations of the sphere are represented by 
 * 2x2 complex matrices [a b; c d]. They form a group under composition, 
 * hence this class extends @see GroupElement. Image of z
 * if 'oriented' is (az+b)/(cz+d); if not oriented, then replace
 * z by conj(z).  
 * 
 * Orientation preserving and standard normalization on creation 
 * is ad-bc=1. TODO: =-1 or non-oriented?? 
 * 
 * ???? old ????? The norm of Mobius m is 
 * 
 *     ||m|| = 2*cosh(rho(zeta,M(zeta))
 *    
 * where zeta= (0,0,1) in upper half space, M is the
 * ????  
 *   
 * Author: Fedor Andreev with adjustments by Ken Stephenson
 */
public class Mobius extends ComplexTransformation implements GroupElement {

	public Complex a;
	public Complex b;
	public Complex c;
	public Complex d;
	public boolean oriented; // if true, then orientation preserving

	public int util; // mostly for passing info from methods
	public double error; // mostly for passing error from methods

	public static double almostAffine = 0.00000001;
	public static double MOD1 = .99999999; // almost 1
	public static double MOB_TOLER = .000000000001; // threshold

	// TODO: work on these thresholds.

	// Constructors
	public Mobius() { // identity
		a = new Complex(1.0);
		b = new Complex(0.0);
		c = new Complex(0.0);
		d = new Complex(1.0);
		oriented = true;
	}

	public Mobius(Complex ap, Complex bp, Complex cp, Complex dp, boolean noflip) { // PSL(2,C)
		Complex detSq = ap.times(dp).minus(bp.times(cp)).sqrt().reciprocal();
		a = ap.times(detSq);
		b = bp.times(detSq);
		c = cp.times(detSq);
		d = dp.times(detSq);
		oriented = noflip;
	}

	public Mobius(Complex ap, Complex bp, Complex cp, Complex dp) { // PSL(2,C)
		this(ap, bp, cp, dp, true);
	}

	public Mobius(double ap, double bp, double cp, double dp, boolean noflip) { // PSL(2,R)
		this(new Complex(ap), new Complex(bp), new Complex(cp),
				new Complex(dp), noflip);
	}

	public Mobius(double ap, double bp, double cp, double dp) { // PSL(2,R)
		this(new Complex(ap), new Complex(bp), new Complex(cp),
				new Complex(dp), true);
	}

	public Mobius(int ia, int ib, int ic, int id, boolean noflip) { // PSL(2,Z)
		this(new Complex((double) ia), new Complex((double) ib), new Complex(
				(double) ic), new Complex((double) id), noflip);
	}

	public Mobius(int ia, int ib, int ic, int id) { // PSL(2,Z)
		this(new Complex((double) ia), new Complex((double) ib), new Complex(
				(double) ic), new Complex((double) id), true);
	}

	// clone constructor
	public Mobius(Mobius mob) {
		this();
		a=new Complex(mob.a);
		b=new Complex(mob.b);
		c=new Complex(mob.c);
		d=new Complex(mob.d);
		oriented=mob.oriented;
	}

	/**
	 * Return new Complex by applying this Mobius tranformation to z.
	 * 
	 * @param z Complex
	 * @return Complex
	 */
	public Complex apply(Complex z) {
		if (!oriented) {
			Complex w = z.conj();
			return ((a.times(w)).plus(b)).divide((c.times(w)).plus(d));
		}
		return (a.times(z)).plus(b).divide((c.times(z)).plus(d));
	}
	
	/**
	 * Given spherical point (theta,phi), apply mobius and return sph pt.
	 * @param z Complex, spherical point
	 * @return Complex, spherical point
	 */
	public Complex apply_2_s_pt(Complex z) {
		  double cosphi=Math.cos(z.y);
		  
		  // at south pole?
		  if (cosphi<-.99999999) {
			  // south pole is fixed
			  if (c.abs()<.0000001)
				  return new Complex(0.0,Math.PI); 
			  return SphericalMath.proj_pt_to_sph(a.divide(c));
		  }
		  
		  // stereo proj to plane, apply mobius, project back
		  Complex nz=SphericalMath.s_pt_to_plane(z);
		  Complex mz=apply(nz);
		  return SphericalMath.proj_pt_to_sph(mz);
	}

	/**
	 * Return new Complex number by applying this Mobius (or inverse) to z.
	 * 
	 * @param z Complex
	 * @param oriented boolean, if false use inverse
	 * @return Complex
	 */
	public Complex apply(Complex z, boolean oriented) {
		if (oriented)
			return apply(z);
		Mobius Minv = (Mobius) this.inverse();
		return Minv.apply(z);
	}

	/**
	 * Return new Complex giving determinant of this Mobius.
	 */
	public Complex det() {
		return (a.times(d).minus(c.times(b)));
	}

	/**
	 * Normalize 'this' Mobius so that ad-bc=1.
	 *  
	 * TODO: what happens if 'this' is orientation reversing?
	 */
	public void normalize() {
		Complex dett = det();
		if (dett.abs() < .000000001)
			throw new MobException("Mobius: det too small to trust");
		if (dett.sub(new Complex(1.0)).abs() < .0000001)
			return; // already okay
		Complex detSqrt = dett.sqrt().reciprocal();
		a = a.times(detSqrt);
		b = b.times(detSqrt);
		c = c.times(detSqrt);
		d = d.times(detSqrt);
	}

	/**
	 * Return new Complex giving trace-squared of this Mobius transformation:
	 * (Recall; must adjust so determinant is 1, so result is (a+d)^2/det^2.)
	 * @return Complex
	 */
	public Complex getTraceSqr() {
		Complex dtm = det();
		Complex z = a.add(d);
		z = z.times(z);
		return (z.divide(dtm.times(dtm)));
	}

	/**
	 * Returns new Mobius, the inverse of this Mobius
	 */
	public GroupElement inverse() {
		Complex det = (a.times(d)).minus(b.times(c));
		return new Mobius(d.divide(det), (b.divide(det)).times(-1f),
				(c.divide(det)).times(-1f), a.divide(det));
	}

	/**
	 * Change 'this' by complex conjugation of all entries.
	 */
	public void conj() {
		a = a.conj();
		b = b.conj();
		c = c.conj();
		d = d.conj();
	}

	/**
	 * Change 'this' to its transpose. So [ a b; c d]
	 * becomes [a c;b d].
	 */
	public void transpose() {
		Complex tmp;
		tmp = new Complex(b);
		b = new Complex(c);
		c = tmp;
	}

	/**
	 * Return new Mobius, scale and/or rotate this by argument
	 * @param factor Complex
	 * @return GroupElement
	 */
	public GroupElement scale(Complex factor) {
		return new Mobius(a.mult(factor), b.mult(factor), c, d);
	}
	
	/**
	 * Return new Mobius; multiply all entries by complex scalar
	 * @param scalar Complex
	 * @return GroupElement
	 */
	public GroupElement multiply_by_scalar(Complex scalar) {
		return new Mobius(a.times(scalar),b.times(scalar),c.times(scalar),d.times(scalar),oriented);
	}

	/**
	 * Return new Mobius, multiply this on left by argument; ie.,
	 * output=left*this
	 * @param left GroupElement
	 * @return GroupElement
	 */
	public GroupElement lmult(GroupElement left) {
		return new Mobius((a.times(((Mobius) left).a)).plus(c
				.times(((Mobius) left).b)), (b.times(((Mobius) left).a)).plus(d
				.times(((Mobius) left).b)), (a.times(((Mobius) left).c)).plus(c
				.times(((Mobius) left).d)), (b.times(((Mobius) left).c)).plus(d
				.times(((Mobius) left).d)));
	}

	/**
	 * Return new Mobius, multiply this on right by argument; ie.,
	 * output=this*right
	 * @param left GroupElement
	 * @return GroupElement
	 */
	public GroupElement rmult(GroupElement right) {
		Mobius M = new Mobius(
				(a.times(((Mobius) right).a)).plus(b.times(((Mobius) right).c)),
				(a.times(((Mobius) right).b)).plus(b.times(((Mobius) right).d)),
				(c.times(((Mobius) right).a)).plus(d.times(((Mobius) right).c)),
				(c.times(((Mobius) right).b)).plus(d.times(((Mobius) right).d)));
		return M;
	}

	/**
	 * Type of Mobius is Parabolic, elliptic, hyperbolic, or loxodromic
	 * @return String
	 */
	public String getType() {
		Complex sqdet = ((a.times(d)).minus(b.times(c))).sqrt();
		Complex tr = (this.a.plus(this.d)).divide(sqdet);
		String type = "";
		if (MathComplex.isSmall(tr.imag())) {
			if (MathComplex.isSmall(tr.real() - 2f)
					|| MathComplex.isSmall(tr.real() + 2f))
				type = "Parabolic";
			if (tr.real() > -2f && tr.real() < 2f)
				type = "Elliptic";
			if (tr.real() < -2f || tr.real() > 2f)
				type = "Hyperbolic";
		} else
			type = "Loxodromic";
		return type;
	}

	// public void addFixedPoints() {
	// Complex sqdisc = ( ( (d.minus(a)).times(d.minus(a))).plus( (b.times(c)).
	// times(4f))).sqrt();
	// Complex z1 = ( ( (a.minus(d)).plus(sqdisc)).divide(c)).times(.5);
	// Complex z2 = ( ( (a.minus(d)).minus(sqdisc)).divide(c)).times(.5);
	// ManagerMobius.fixedPoints.addElement(z1);
	// ManagerMobius.fixedPoints.addElement(z2);
	// }
	
	
	public String toString() {
		// return a.toString() + "\t" + b.toString() + "\n" + c.toString() +
		// "\t" + d.toString() + "\n";
		return a.toString3() + " " + b.toString3() + " " + c.toString3() + " "
				+ d.toString3() + " ";

	}
	
	/**
	 * Print out a mobius in form to copy into matlab.
	 * @param varname String
	 * @param mob Mobius
	 */
	public void MobPrint(String varname) {
		System.out.println(varname+" = ["+a.toString()+"  ,  "+b.toString()+" ;   "+c.toString()+"  ,  "+d.toString()+"];");
	}
	
	public Complex getC() {
		return c;
	}

	public Complex getA() {
		return a;
	}

	public Complex getB() {
		return b;
	}

	public Complex getD() {
		return d;
	}

	// public CPCircle applyToCircle(CPCircle circle) {
	// Complex center = circle;
	// double radius = circle.getRadius();
	// Complex Q;
	// double s;
	// if (!isAffine()) {
	// something is wrong in this code!!!! (Fedor)
	// Complex z = circle.minus( ( ( (circle.plus(d.divide(c))).conj()).
	// reciprocal()).times(radius * radius));
	// Q = ( (a.times(z)).plus(b)).divide( (c.times(z)).plus(d));
	// s = (Q.minus( ( (a.times(circle.plus(radius))).plus(b)).
	// divide( (c.times(circle.plus(radius))).plus(d)))).abs();
	// Complex toCenter = circle.getPoint(1).sub(circle.getCenter());
	// Complex point2 = circle.getCenter().sub(toCenter);
	// Complex im1 = this.apply(circle.getPoint(1)); // a point on the image
	// Complex im2 = this.apply(point2); // a point on the image
	// Complex temp = im2.sub(im1);
	// s = .5*temp.abs();
	// Q = (im1.plus(im2)).times(.5);
	// Complex z1=z;
	// z1=z1.add(r);
	// Complex z2=z;
	// z2.setImaginary(z2.getImaginary()+r);
	// z3=z;z3.re -= r;
	// tmp1=mobius(Mob,z1,flag);
	// tmp2=mobius(Mob,z2,flag);
	// tmp3=mobius(Mob,z3,flag);
	// circle_3(tmp1,tmp2,tmp3,newz,newr);

	/*
	 * find out if point mapped to infinity is inside the circle
	 */
	// if (cAbs(Mob.c)<m_toler) /* infinity is fixed */
	// {
	// if (r<0) (*newr) *= -1;
	// return 1;
	// }
	// nd.re=-Mob.d.re;nd.im=-Mob.d.im;
	// new_inf=cdiv(nd,Mob.c);
	// dist=cAbs(csub(z,new_inf));
	// if ((r<0 && (dist+r)>0.0) /* it is, so we want outside of circle */
	// || (r>0 && dist<r))
	// (*newr) *= -1;
	// return 1;
	// }
	// else {
	// Q = ( (a.times(circle)).plus(b)).divide(d);
	// s = (Q.minus( ( (a.times(circle.plus(radius))).plus(b)).
	// divide(d))).abs();
	// }
	// CPCircle r = new CPCircle(Q.x,Q.y,s);
	// r.finalizeColor(circle,this.getLevel());
	// return r;
	// }

	/**
	 * Find fixed point by quadratic formula; use '+' sign
	 * @return Complex
	 */
	public Complex getFixedPoint1() {
		Complex diff = d.minus(a);
		Complex sqdisc = ((diff.mult(diff)).plus((b.times(c)).times(4.0)))
				.sqrt();
		if (this.isAffine())
			if (MathComplex.isSmall(diff))
				return null;
			else
				return b.divide(diff);
		else
			return (((a.minus(d)).plus(sqdisc)).divide(c)).times(.5);
	}

	/**
	 * Find fixed point by quadratic formula; use '-' sign
	 * @return Complex
	 */
	public Complex getFixedPoint2() {
		Complex diff = d.minus(a);
		Complex sqdisc = ((diff.mult(diff)).plus((b.times(c)).times(4.0)))
				.sqrt();
		if (this.isAffine())
			if (MathComplex.isSmall(diff))
				return null;
			else
				return b.divide(diff);
		else
			return (((a.minus(d)).minus(sqdisc)).divide(c)).times(.5);
	}

	/**
	 *  true if essentially affine (i.e., |c| < 'almostAffine' (threshold))
	 *  @return boolean
	 */
	public boolean isAffine() {
		if (MathComplex.absSq(c) < almostAffine)
			return true;
		else
			return false;
	}

	/**
	 * Create Mobius (det=1) mapping complexes {a,b,c} to {0, 1, infty}, resp. 
	 * General form is [b-c -a(b-c); b-a -c(b-a)], with special cases if
	 * a, b, or c is infinity. Return null on failure or roundoff problems,
	 * e.g., a and b too close. 
	 * TODO: Need to clean up, perhaps allow null
	 * arguments for infinity.
	 * 
	 * @param a Complex, going to 0
	 * @param b Complex, going to 1
	 * @param c Complex, going to infty
	 * @return new Mobius or null
	 */
	public static Mobius standard3Point(Complex a, Complex b, Complex c) {

		Mobius mob = new Mobius(); // initiate as identity

		// compute min of approximate spherical distances
		double sdst = a.minus(b).abs() / (1 + a.absSq());
		double hd = 1.0;
		sdst = (sdst < (hd = b.minus(c).abs() / (1 + b.absSq()))) ? sdst : hd;
		sdst = (sdst < (hd = c.minus(a).abs() / (1 + c.absSq()))) ? sdst : hd;

		if (sdst < 10 * MOB_TOLER)
			return mob; // identity

		// check if any are at infinity
		int inf = -1;
		if (a.absSq() > 10000000000.0)
			inf = 0;
		if (b.absSq() > 10000000000.0) {
			if (inf == 0)
				return mob; // two points are too close
			inf = 1;
		}
		if (c.absSq() > 10000000000.0) {
			if (inf >= 0)
				return mob; // two points are too close
			inf = 2;
		}

		if (inf == 2) { // c=inf-->inf, for Az+B
			mob.c = new Complex(0.0);
			mob.d = new Complex(1.0);
			mob.a = new Complex(1.0);
			mob.a = mob.a.divide(b.sub(a));
			mob.b = a.divide(a.sub(b));
		} else if (inf == 0) { // a at infinity; like above, then reciprocate
			mob.a = new Complex(0.0);
			mob.b = new Complex(1.0);
			mob.c = new Complex(1.0);
			mob.c = mob.c.divide(b.sub(c));
			mob.d = c.divide(c.sub(b));
		} else if (inf == 1) { // b at infinity; [1 -a;1 -c]
			mob.a = new Complex(1.0);
			mob.b = new Complex(-a.x, -a.y);
			mob.c = new Complex(1.0);
			mob.d = new Complex(-c.x, -c.y);
		} else { // general case:
			mob.a = b.minus(c);
			mob.b = a.times(mob.a).times(-1.0);
			mob.c = b.sub(a);
			mob.d = c.times(mob.c).times(-1.0);
		}

		mob.normalize();
		return mob;
	}
	
	/**
	 * Create Mobius mapping given pair of circles to given pair
	 * of circles. Project to eucl data if in other geometries. 
	 * Idea is to match 3 points: circle 1 and 2 centers,
	 * and point on boundary of circle 1 in direction opposite to circle 2.
	 * 
	 * TODO: Perhaps not very robust for spherical circles around or 
	 * containing infinity.
	 *  
	 * @param cent1 Complex // first pair
	 * @param rad1 double
	 * @param cent2 Complex
	 * @param rad2 double
	 * @param Cent1 Complex // second pair
	 * @param Rad1 double
	 * @param Cent2 Complex
	 * @param Rad2 double
	 * @param hes1 int
	 * @param hes2 int
	 * @return Mobius, identity on error
	 */
	public static Mobius mob_tang_circles(Complex cent1,double rad1,
			Complex cent2,double rad2,Complex Cent1,double Rad1,
			Complex Cent2,double Rad2,int hes1,int hes2) {
		Complex z1=new Complex(cent1);
		Complex z2=new Complex(cent2);
		double r1=rad1;
		double r2=rad2;
		Complex Z1=new Complex(Cent1);
		Complex Z2=new Complex(Cent2);
		double R1=Rad1;
		double R2=Rad2;
		Mobius mob=new Mobius();
		CircleSimple sC=new CircleSimple();
		
		// check for geometry conversions
		if (hes1<0) {
			sC=HyperbolicMath.h_to_e_data(z1, r1);
			z1=sC.center;
			r1=sC.rad;
			sC=HyperbolicMath.h_to_e_data(z2, r2);
			z2=sC.center;
			r2=sC.rad;
		}
		if (hes2<0) {
			sC=HyperbolicMath.h_to_e_data(Z1, R1);
			Z1=sC.center;
			R1=sC.rad;
			sC=HyperbolicMath.h_to_e_data(Z2, R2);
			Z2=sC.center;
			R2=sC.rad;
		}
		if (hes1>0) {
			sC=SphericalMath.s_to_e_data(z1, r1);
			z1=sC.center;
			r1=sC.rad;
			sC=SphericalMath.s_to_e_data(z2, r2);
			z2=sC.center;
			r2=sC.rad;
		}		
		if (hes2>0) {
			sC=SphericalMath.s_to_e_data(Z1, R1);
			Z1=sC.center;
			R1=sC.rad;
			sC=SphericalMath.s_to_e_data(Z2, R2);
			Z2=sC.center;
			R2=sC.rad;
		}
		
		Complex vec=z2.minus(z1);
		double vmod=vec.abs();
		Complex Vec=Z2.minus(Z1);
		double Vmod=Vec.abs();
		if (vmod<.0000000001 || Vmod<.0000000001) {
			CirclePack.cpb.errMsg("In 'mob_tang_circles', centers "+
					"too close for computation");
			return mob;
		}
		
		// check if realignment is needed.
		if ((Z1.minus(z1).abs()+Z2.minus(z2).abs()+
				Math.abs(R1-r1)+Math.abs(R2-r2))<.0001)
				return mob;
		
		// find z on circle 1, opposite circle 2 center 
		vec=vec.divide(vmod);
		Vec=Vec.divide(Vmod);
		// note that r1 (and/or R1) could be negative in sph case.
		Complex z=z1.plus(vec.times(-1.0*r1));
		Complex Z=Z1.plus(Vec.times(-1.0*R1));
		mob=Mobius.mob_xyzXYZ(z1,z2,z,Z1,Z2,Z,0,0);
		
		return mob;
	}
	
	/**
	 * Find Mobius that maps centers of 3 circles of eucl packing p to 
	 * the centers of 3 other circles.
	 * @param p PackData, eucl only for now
	 * @param v
	 * @param u
	 * @param w
	 * @param V
	 * @param U
	 * @param W
	 * @return new Mobius, null on error
	 */
	public static Mobius mob_vuwVUW(PackData p,int v,int u,int w, int V,int U,int W) {
		if (p.hes!=0) {
			CirclePack.cpb.errMsg("usage: 'mob_vuwVUW' only applies to euclidean packings");
			return null;
		}
		return mob_xyzXYZ(p.rData[v].center,p.rData[u].center,p.rData[w].center,
				p.rData[V].center,p.rData[U].center,p.rData[W].center,p.hes,p.hes);
	}

	/**
	 * Create Mobius mapping Complex pts x,y,z to X,Y,Z resp. 
	 * (Depends on the two geometries; for sphere, points must be
	 * stereo projected to the plane.)
	 * @param x Complex
	 * @param y Complex
	 * @param z Complex
	 * @param X Complex
	 * @param Y Complex
	 * @param Z Complex
	 * @param hes int, geometry of x,y,z
	 * @param Hes int, geometry of X,Y,Z
	 * @return Mobius
	 */
	public static Mobius mob_xyzXYZ(Complex x, Complex y, Complex z, Complex X,
			Complex Y, Complex Z,int hes,int Hes) {
		if (hes>0) {
			x=SphericalMath.s_pt_to_plane(x);
			y=SphericalMath.s_pt_to_plane(y);
			z=SphericalMath.s_pt_to_plane(z);
		}
		if (Hes>0) {
			X=SphericalMath.s_pt_to_plane(X);
			Y=SphericalMath.s_pt_to_plane(Y);
			Z=SphericalMath.s_pt_to_plane(Z);
		}
		Mobius m = Mobius.standard3Point(x, y, z);
		Mobius M = Mobius.standard3Point(X, Y, Z);
		GroupElement MInverse = (GroupElement) M.inverse();
		return (Mobius) m.lmult(MInverse);
	}

	/**
	 * Convenience routine: return value of z under (a-z)/(1-z*conj(a)).
	 * 
	 * @param z Complex
	 * @param a Complex
	 * @return Complex
	 */
	public static Complex mob_trans(Complex z, Complex a) {
		return (a.sub(z).divide(MathComplex.ID.sub(a.conj().mult(z))));
	}

	/**
	 * Convenience routine: return preimage of w under mobius of disc which maps
	 * a to zero and b to positive x-axis
	 * 
	 * @param w Complex
	 * @param a Complex
	 * @param b Complex
	 * @return Complex
	 */
	public static Complex mobDiscInvValue(Complex w, Complex a, Complex b) {
		Complex z;
		double c;

		z = mob_trans(b, a);
		/* check if b is very close to a */
		if ((c = (z.abs())) < MOB_TOLER)
			return (a);
		z = z.times(1 / c);
		return mob_trans(z.mult(w), a);
	}

	/**
	 * Convenience routine: return value at z of mobius transform of unit disc
	 * which maps a to the origin and (if a and g are not essentially equal) maps g 
	 * to positive x-axis. 
	 * @param z Complex
	 * @param a Complex, |a|<1.0
	 * @param g Complex
	 * @return Complex
	 */
	public static Complex mobDiscValue(Complex z, Complex a, Complex g) {
		Complex w, v;
		double c;
		
		if (a.abs()>MOD1)
			throw new DataException("'a' is too close to unit circle");

		v = mob_trans(g, a);
		c = v.abs();
		if (c < MOB_TOLER)
			return (mob_trans(z, a));
		w = mob_trans(z, a).divide(v);
		return w.times(c);
	}
	
	/**
	 * Given 3D point interior to unit sphere, create the Mobius in 3-space
	 * which maps the sphere to itself, the point to the origin, while 
	 * fixing the endpoints on the sphere of the vector through the point. 
	 * @param pt Point3D
	 * @return Mobius (normalized), identity if close to origin, null on error
	 */
	public static Mobius move3Dpoint2origin(Point3D pt) {
		
		boolean debug=false;
		
		// check: too close to the sphere? return null
		double a=pt.norm();
		if (a>.99999) 
			return null;
		// too close to origin? return identity
		if (a<.000001)
			return new Mobius(); // identity
		
		// Create M to rotate so axis is toward north pole
		Complex axis=Point3D.p3D_2_sph(pt);
		Mobius M=rotate2North(axis); // M.apply(axis); 
		// TODO: not sure rotate2North works correctly
		
		// point is now (0,0,a) and we need to apply a
		//   scaling map z -> lambda*z. To find lambda,
		//   we consider the 2D mobius in the slice throuh
		//   the z-axis in 3D which maps (0,0,a) to the
		//   origin in that slice; follow what happens to 
		//   point (1,0,0) to see what lambda is.
		double lambda=Math.sqrt((1+a)/(1-a));
		Mobius scaler=new Mobius(lambda,0.0,0.0,1.0/lambda);
		
		// return inv(M)*scaler*M
		Mobius invM=(Mobius)M.inverse();
		Mobius SM=(Mobius)(M.lmult(scaler));
		Mobius ans=(Mobius)SM.lmult(invM);
		
		// debug=true;
		if (debug) {
			Complex s_pt=SphericalMath.proj_vec_to_sph(pt);
			Complex anspt=ans.apply(s_pt);
			Complex ns_pt=SphericalMath.proj_pt_to_sph(anspt);
			@SuppressWarnings("unused")
			double []vec=SphericalMath.s_pt_to_vec(ns_pt);
		}
		
		return ans;
		
	}
	
	/**
	 * Given (theta,phi) point on the sphere, find rigid motion of sphere
	 * moving it to the north pole. Compute angle and then cross product to
	 * get axis of rotation.
	 * @param sph_z Complex (theta,phi)
	 * @return Mobius (normalized)
	 */
	public static Mobius rotate2North(Complex sph_z) {
		
		// is direction already almost north? return identity
		if (sph_z.y<.001)
			return new Mobius();
		
		// is it almost south? return inversion z->1/z
		if (Math.abs(sph_z.y-Math.PI)<.001)
			return new Mobius(0.0,1.0,1.0,0.0);
		
		double []vec=SphericalMath.s_pt_to_vec(sph_z);
		Point3D pt=new Point3D(vec[0],vec[1],vec[2]);
		Point3D np=new Point3D(0,0,1.0);
		double theta=Math.acos(Point3D.DotProduct(pt,np));
		Point3D axis=Point3D.CrossProduct(pt,np);
		Complex axis_z=SphericalMath.proj_vec_to_sph(axis);
		Mobius mob=sphRotation(theta,axis_z);
		// debug
		boolean debug=false;
		if (debug) { // debug=true;
			Complex sph_w=mob.apply_2_s_pt(sph_z);
			System.out.println("rotated to (theta,phi) = ("+sph_w.x+","+sph_w.y+")");
		}
		return mob;
	}
	
	/**
	 * Create Mobius representing rigid rotation of the sphere (in SU(2)).
	 * Described by ccw angle as viewed toward origin from direction of 
	 * 'axis', given in (theta,phi) form. 
	 * @param ang double, theta=ang/2 is the half-angle
	 * @param axis Complex, spherical point, (theta,phi) 
	 * @return Mobius (normalized)
	 */
	public static Mobius sphRotation(double ang,Complex axis) {
		double theta=ang/2.0;
		double []sphpt=SphericalMath.s_pt_to_vec(axis); // SphericalMath.proj_pt_to_sph(axis));
		double s=Math.sin(theta);
		double c=Math.cos(theta);
		Complex z=new Complex(c,sphpt[0]*s);
		Complex w=new Complex(sphpt[1]*s,sphpt[2]*s);
		return new Mobius(z,w,w.conj().times(-1.0),z.conj());
	}

	/**
	 * Create Mobius (det=1) for rotation by angle ang*Pi, fixing 0, infty.
	 * @param ang double; multiply by Pi to get radians
	 * @return Mobius (normalized)
	 */
	public static Mobius rotation(double ang) {
		Complex pr=new Complex(0.0,ang*Math.PI);
		return new Mobius(pr.exp(),new Complex(0.0),new Complex(0.0),new Complex(1.0));
	}

	/**
	 * Create automorphism of unit disc carrying a->A and b->B. Various
	 * situations and possible exceptions; Must have |a|=1 and |A|=1
	 * simultaneously (same for b and B). If |a|=1=|b|, then call trans_abAB If
	 * a,b,A,B are interior points, technically, must have hyp_dist (a,b) =
	 * hyp_dist (A,B). But this routine carries the geodesic through a,b to that
	 * through A,B, with midpoint going to midpoint.
	 * 
	 * @param a Complex
	 * @param b Complex
	 * @param A Complex
	 * @param B Complex
	 * @return Mobius (normalized) or null; see Mobius.error for size of error
	 */
	public static Mobius auto_abAB(Complex a, Complex b, Complex A, Complex B) {

		// 0=all interior, 1=one pair on unit circle, 2=both pairs on unit
		// circle
		int category = 0;

		// categorize and error check
		double aa = a.abs();
		double Aa = A.abs();
		double ba = b.abs();
		double Ba = B.abs();
		if ((aa >= MOD1 && Aa < MOD1) || (Aa >= MOD1 && aa < MOD1)
				|| (ba >= MOD1 && Ba < MOD1) || (Ba >= MOD1 && ba < MOD1)) {
			throw new MobException("mismatch re: unit circle");
		}
		if (aa >= MOD1 || ba >= MOD1) {
			category = 1;
			if (aa >= MOD1 && ba >= MOD1)
				category = 2;
		}

		switch (category) {
		case 1: {
			if (aa >= MOD1) { // swap, pairs, return inverse
				Complex hold = new Complex(a);
				a = b;
				b = hold;
				hold = new Complex(A);
				A = B;
				B = hold;
			}

			Mobius mob = new Mobius();
			Mobius M1 = standard_mob(a, b);
			Mobius M2 = standard_mob(A, B);
			M2.inverse();
			mob = (Mobius) M2.rmult(M1); // M = M2 * M1

			// if we switch, have to switch back
			if (aa >= MOD1)
				mob.inverse();
			mob.normalize();
			return mob;
		}
		case 2: {
			return trans_abAB(a, b, A, B, new Complex(0.0), new Complex(0.0));
		}
		case 0: {
			int err_hit = 0;
			Mobius M1, M2, mob, tmp;

			mob = new Mobius();
			M1 = standard_mob(a, b);
			err_hit = M1.util;
			tmp = standard_mob(A, B);
			err_hit += tmp.util;
			if (err_hit > 0) {
				mob.util = 1;
				mob.normalize();
				return mob;
			}
			M2 = (Mobius) tmp.inverse();
			mob = (Mobius) M2.rmult(M1); // M = M2 * M1
			mob.error = B.sub(mob.apply(b)).abs();
			mob.normalize();
			return mob;
		}
		} // end of switch
		throw new MobException("failed to find method");
	}

	/**
	 * Create automorphism of unit disc mapping a->A, b->B when a,b,A,B are all
	 * on the unit circle. There is an additional degree of freedom, so also
	 * match c->C (as closely as possible). If c or C is outside the disc, set
	 * them both to 0. ignore them both. TODO: return not yet normalized
	 * 
	 * @param a Complex
	 * @param b Complex
	 * @param A Complex
	 * @param B Complex
	 * @param c Complex
	 * @param C Complex
	 * @return Mobius (normalized), null on error
	 */
	public static Mobius trans_abAB(Complex a, Complex b, Complex A, Complex B,
			Complex c, Complex C) throws MobException {

		// all on unit circle?
		if (a.abs() < MOD1 || b.abs() < MOD1 || A.abs() < MOD1
				|| B.abs() < MOD1)
			return null;

		Mobius mob = new Mobius();

		// too close to work with, just rotate a to A */
		if (a.sub(b).abs() < MOB_TOLER || A.sub(B).abs() < MOB_TOLER) {
			mob = new Mobius(A.divide(a), new Complex(0.0), new Complex(1.0),
					new Complex(0.0));
			mob.normalize();
			return mob;
		}

		// value outside disc means default to zero
		if (c.abs() > 1.001 || C.abs() > 1.001) {
			c = new Complex(0.0);
			C = new Complex(0.0);
		}

		// M1 maps a->1, b->-1, M2 maps A->1, B->-1
		Mobius M1, M2;
		try {
			M1 = norm_abc(a, b, c);
			M2 = norm_abc(A, B, C);
		} catch (MobException mex) {
			throw new MobException("'norm_abc' error: " + mex.getMessage());
		}
		// the result we want: inv(M2)*M1
		mob = (Mobius) M2.inverse().rmult(M1);
		mob.normalize();
		return mob;
	}

	/**
	 * Create automorphism of unit disc carrying a to origin, b to positive
	 * x-axis. If b and a are essentially equal, then don't do the rotation. 
	 * Has form e^{it}(z-a)/(1-~az), where t=-arg((b-a)/(1-~ab)).
	 * (~=conjugate)
	 * @param a Complex, |a|<1.0
	 * @param b Complex
	 * @return Mobius
	 */
	public static Mobius standard_mob(Complex a, Complex b) throws MobException {

		double arg, x, sp, sm, tp, tm;
		Complex w, ex, exa, ac;
		Mobius M = new Mobius();

		if (a.abs()>MOD1)
			throw new DataException("'a' is almost on the unit circle");
		ac = a.conj();
		w = ac.times(b);
		w = new Complex(1.0 - w.x, -w.y);
		if (w.abs() < MOB_TOLER) {
			M.util = 1;
			return M;
		}
		w = b.sub(a).divide(w);
		arg = (-1.0) * (w.arg());
		ex = new Complex(0.0, arg);
		ex = ex.exp();
		x = w.abs();
		sp = Math.sqrt(1 + x);
		if ((tm = (1 - x)) < 0.0) {
			M.util = 1;
			return M;
		}
		sm = Math.sqrt(1 - x);
		tp = sp + sm;
		tm = sp - sm;
		exa = ex.times(a);
		M = new Mobius(
				new Complex(ex.x * tp + ac.x * tm, ex.y * tp + ac.y * tm),
				new Complex(-exa.x * tp - tm, -exa.y * tp), new Complex(-ex.x
						* tm - ac.x * tp, -ex.y * tm - ac.y * tp), new Complex(
						exa.x * tm + tp, exa.y * tm));
		return M;
	}

	/**
	 * Create automorphism of eucl plane carrying a->A, b->B.
	 * @param a Complex
	 * @param b Complex
	 * @param A Complex
	 * @param B Complex
	 * @return Mobius, identity on error.
	 */
	public static Mobius affine_mob(Complex a, Complex b, Complex A, Complex B)
			throws MobException {
		Mobius M;
		if (a.sub(b).abs() < 100 * MOB_TOLER
				|| A.sub(B).abs() < 100 * MOB_TOLER) {
			M = new Mobius();
			M.util = 1;
			return M;
		}
		Complex tmp = new Complex((A.sub(B)).divide(a.sub(b)));
		M = new Mobius(tmp, A.sub(tmp.times(a)), new Complex(0.0), new Complex(
				1.0));
		return M;
	}

	/**
	 * Create an automorphism of the unit disc mapping a->1, b->-1, and c as
	 * close as possible to the origin. a, b are on unit circle, c interior. On
	 * error, throw MobException. Strategy: Map to right halfplane with R so
	 * a->infty, b to imag axis; translate by -R(b) so image of b at origin;
	 * divide by image of c to put on the unit circle; map back to disc with W
	 * mapping infty->1, 0->-1, 1->0. Will end up with a->1, b->-1, c on
	 * imaginary axis (hence closest to zero).
	 * @param a Complex, |a|=1.0
	 * @param b Complex, |b|=1.0
	 * @param c Complex, |c|<1.0
	 * @return Mobius
	 */
	public static Mobius norm_abc(Complex a, Complex b, Complex c)
			throws MobException {

		Complex A, B, One, img;
		Mobius R, T, M, W;

		// is data valid??
		if (a.abs() < MOD1 || b.abs() < MOD1 || c.abs() >= MOD1) {
			throw new MobException();
		}
		// make sure they're on the unit circle
		A = a.mult(a.abs());
		B = b.mult(b.abs());
		One = new Complex(1.0);
		// R to map unit disc to right half plane, 0->1, -A->0, A->infty.
		// i.e., R(z)=(z+A)/(-z+A)
		R = new Mobius(One, A, new Complex(-1.0), A);
		img = R.apply(B);
		// T translates by -img to put image of B at 0.
		T = new Mobius(One, new Complex(0.0, -img.y), new Complex(0.0), One);
		// compose these
		M = (Mobius) T.rmult(R);
		// scale by reciprocal of image of c
		M = (Mobius) M.scale(M.apply(c).reciprocal());
		// W maps back to the disc: 1->0, 0->-1,infty->1; ie. z->(z-1)/(z+1)
		W = new Mobius(One, new Complex(-1.0), One, One);
		// compose for final map
		return (Mobius) W.rmult(M);
	}
	
	/**
	 * Create the Mobius edge derivative for base equilateral
	 * from real schwarzian 's'. Base equilateral is formed by 
	 * tangent triple of radius sqrt(3), symmetric w.r.t. the 
	 * origin, and with tangencies at third roots of unity. 
	 * 'j' indicates the relevant edge, see 'Schwarzian.java'.
	 *     mob = [1 + c*z,-c*z^2; c, 1-c*z], 
	 * where z is the fixed point and c = s*i*conj(psi), where 
	 * 'psi' is the unit vector in the direction of the oriented 
	 * jth edge. In the base equilateral setting, therefore,
	 * the mobius simplifies to
	 *     mob = [1+s  -s*w ; s*conj(w)  1-s],
	 * where 'w' is the third root of unity normal to the jth edge.
	 * In particular, i*psi=-w, so i*conj(psi)= - conj(i*psi) = 
	 * - conj(-w)=conj(w) and because w is also the fixed point.
	 * @param s double, real schwarzian
	 * @param j int, which edge
	 * @return Mobius, identity on error
	 */
	public static Mobius stdBaseMobius(double s,int j) {
		Mobius mob=new Mobius(new Complex(1+s),
				CPBase.omega3[j].times(-s),
				CPBase.omega3[j].times(s).conj(),
				new Complex(1-s));
		return mob;
	}

	/* ----------------- applying Mobius to single circles ------- */

	/**
	 * Apply mobius (flag false) or inverse (flag true) to a single circle
	 * in specified geometry. Note that in eucl case, negative newr 
	 * means use outside of circle; user will have to handle this in 
	 * calling routine. Center/radius in specified geometry returned 
	 * in 'CircleSimple'.
	 * @param Mob Mobius
	 * @param hes int, geometry
	 * @param z Complex, circle center
	 * @param r double, circle radius
	 * @param sC CircleSimple, use this to return results
	 * @param oriented boolean, if false, use Mob^{-1}
	 * @return int, 0 on error, results in 'sC' in specified geometry
	 */
	public static int mobius_of_circle(Mobius Mob, int hes, Complex z,
			double r, CircleSimple sC, boolean oriented) {
		double tmpr;
		Complex tmpz;
		CircleSimple sc;

		if (hes < 0) { // hyperbolic
			sc = HyperbolicMath.h_to_e_data(z, r);
			tmpz = new Complex(sc.center);
			tmpr = sc.rad;
			
			CirMatrix C=new CirMatrix(tmpz,tmpr);
			CirMatrix CC=CirMatrix.applyTransform(Mob,C,oriented);

			sc=CirMatrix.cirMatrix2eucl(CC);
			if (sc==null)
				return 0;
			
			sc = HyperbolicMath.e_to_h_data(sc.center, sc.rad);
			sC.center = new Complex(sc.center);
			sC.rad = sc.rad;
			return 1;
		}
		if (hes > 0) { // spherical
			// check for nan situations
			if (Double.isNaN(z.x) || Double.isNaN(z.y))
				return 0;
			// for NaN problem with r, just move center
			if (Double.isNaN(r)) { // just move center (without adjusting as
									// usual)
				sC.center = Mob.apply_2_s_pt(z);
				/* fixup: for Nan problem, should get better estimate of r 
				 * using, e.g., derivative of Mob.*/
				sC.rad = r;
				return 1;
			}
			CirMatrix C =CirMatrix.sph2CirMatrix(z, r);
			CirMatrix CC = CirMatrix.applyTransform(Mob, C, oriented);
			sc=CirMatrix.cirMatrix2sph(CC);
			if (Double.isNaN(sC.center.x) || Double.isNaN(sC.center.y)) {
				sc.center = Mob.apply(z);
				/* fixup: see above */
				/*
				 * fixup: problem is sometimes that imaginary part comes out
				 * negative. Have to check why? is this (or could it be) okay?
				 */
				sc.rad = r;
			}
			sC.center=new Complex(sc.center);
			sC.rad=sc.rad;
			return 1;
		} else { // euclidean
			CirMatrix C=new CirMatrix(z,r);
			CirMatrix CC=CirMatrix.applyTransform(Mob,C,oriented);

			CircleSimple scl=CirMatrix.cirMatrix2eucl(CC);
			sC.center=new Complex(scl.center);
			sC.rad=scl.rad;
			return 1;
		}
	}
 
	/**
	 * Find the orthogonal 
	 * @param axis
	 * @return
	 */
//	public static Mobius rotateAxis(Point3D axis) {
		
//	}
	
	/**
	 * Given intended north and south pole spherical circle data, (zN,rN) and
	 * (zS,rS), return the Mobius transformation centering them at N and S
	 * poles, resp. Return identity on error.
	 * 
	 * If rE>0, then normalization is included to move circle (zE,rE) to one
	 * centered at 1 on the sphere. Otherwise, normalization is one that first
	 * gives N and S circles the same radius, then applies mobius z-->factor*z.
	 * (Default factor=1 means N and S should end up with same spherical
	 * radius.)
	 * 
	 * Strategy is to first find mobius MM centering N, S at their respective
	 * poles; then work out normalization.
	 * @param zN Complex
	 * @param zS Complex
	 * @param zE Complex
	 * @param rN double
	 * @param rS double
	 * @param rE double
	 * @param factor double
	 * @return Mobius
	 */
	public static Mobius NS_mobius(Complex zN, Complex zS, Complex zE,
			double rN, double rS, double rE, double factor) {

		boolean dilation_flag = false;
		Complex tmp, s, T_ez2, ew1, ez1, ez2, coef;
		Complex Sctr, Nctr, NS, ES, EN;
		double Srad, Nrad, Nmod, Smod;
		Mobius MM = new Mobius();
		Mobius M1 = new Mobius();
		Mobius M2 = new Mobius();
		double t, a, b, argz, argw, lam, dist, denom;
		double[] V = new double[3];
		double[] T = new double[3];
		double[] C = new double[3];

		if (Math.abs(rE) < MOB_TOLER)
			dilation_flag = true; /* No 'east' circle given */
		if (dilation_flag && factor <= 0.0)
			factor = 1.0;

		/* check for data problems */
		NS = zN.minus(zS);
		ES = zE.minus(zS);
		EN = zE.minus(zN);

		if ((!dilation_flag && (ES.abs() < MOB_TOLER || EN.abs() < MOB_TOLER))
				|| NS.abs() < MOB_TOLER) {
			return new Mobius(); // identity
		}
		dist = SphericalMath.s_dist(zN, zS);

		// N/S essentially antipodal already -- just need rotations
		if (Math.abs(dist - Math.PI) < MOB_TOLER) {
			if (Math.abs(zN.y) < MOB_TOLER) // don't need anything
				MM = new Mobius(); // identity
			else if (Math.abs(zN.y - Math.PI) < MOB_TOLER) { // switch poles by
																// inversion
				MM.a = new Complex(0.0);
				MM.b = new Complex(1.0);
				MM.c = new Complex(1.0);
				MM.d = new Complex(0.0);
				MM.oriented = true;
			} else { // zN to origin, zS to infinity
				denom = 1 / Math.sin(zN.y);
				Nctr = new Complex(Math.cos(zN.x) * denom, Math.sin(zN.x)
						* denom);
				denom = 1 / Math.sin(zS.y);
				Sctr = new Complex(Math.cos(zS.x) * denom, Math.sin(zS.x)
						* denom);

				MM.a = new Complex(1.0);
				MM.b = new Complex(-Nctr.x, -Nctr.y);
				MM.c = new Complex(1.0);
				MM.d = new Complex(-Sctr.x, -Sctr.y);
				MM.oriented = true;
				MM.normalize();
			}
		}

		/*
		 * Non-antipodal: involves interesting geometry due to fact that sph
		 * circle centers are not preserved under Mobious.
		 */
		/*
		 * Consider the plane containing the origin, zN, and zS. Intersection
		 * with sphere gives disc bounded by great circle thru zN and zS; we
		 * treat this as the unit disc and locate points via angular arguments
		 * (sph distance). Going counterclockwise, locate pts w2, zS, w1, z1,
		 * zN, z2, where the w's and z's are, resp., on the circles (zS,rS) and
		 * (zN,rN). Let g be the hyp geodesic orthogonal to both the geodesics
		 * w1 to w2 and z1 to z2. The endpoints of g on the unit circle are the
		 * precise points which we want to send to N and S. They are on the
		 * fixed point set of anticonformal involution of the disc interchanging
		 * z1, z2 and w1, w2.
		 */

		/*
		 * In the disc, the idea is to put w2 at 1 as base for measuring the
		 * others using angles (sph distances). Then move to the upper
		 * half-plane with T, where T(1)=infinity, T(w1)=0, and T(z1)=1. In the
		 * half-plane, g is orthog to the imaginary axis and to the geo through
		 * T(z1)=1 and T(z2)>1 (draw a picture!). g ends at points +/-
		 * sqrt(T(z2)). Carry these back to unit circle under T^{-1} and then
		 * onto sphere using angle and tangent direction from zS to zN.
		 */

		else {
			/*
			 * locate points by putting w2 at 1, progress through ew1, ez1, ez2
			 * counterclockwise using distances radii info.
			 */
			ew1 = new Complex(0.0, 2 * rS).exp();
			ez1 = new Complex(0.0, dist + rS - rN).exp();
			ez2 = new Complex(0.0, dist + rS + rN).exp();
			/*
			 * trans to upper half-plane is
			 * T(z)=coef*fac=[(ez1-1.0)/(ez1-ew1)]*[(z-ew1)/(z-1.0)], and we
			 * only need T(ez2), which should be positive.
			 */
			coef = ez1.minus(MathComplex.ID).divide(ez1.minus(ew1));
			tmp = ez2.minus(ew1).divide(ez2.minus(MathComplex.ID));
			T_ez2 = coef.times(tmp);
			lam = Math.sqrt(T_ez2.x);
			/*
			 * pts corresp to ends of g on real line are lam and -lam. Have to
			 * map these back to unit circle under inverse of T, up to factor
			 * this is TT(z)=(-z+ew1*coef)/(-z+coef), and then find their
			 * arguments.
			 */
			s = new Complex(-lam);
			tmp = s.add(ew1.times(coef)).divide(s.add(coef));
			if ((argz = (tmp.arg() - rS)) < -Math.PI)
				argz += 2.0 * Math.PI;
			s.x = lam;
			tmp = s.add(ew1.times(coef)).divide(s.add(coef));
			if ((argw = (tmp.arg() - rS)) < 0)
				argw += 2.0 * Math.PI;
			/*
			 * argw and argz are the sph distance from zS on circle to desired
			 * pts between w1 and w2 and between z1 and z2, resp.
			 */
			V = SphericalMath.s_pt_to_vec(zS); // vector pointing to zS
			T = SphericalMath.sph_tangent(zS, zN); // tangent vector at zS in
												// direction zN

			/*
			 * here's the point, first in vec form, then projected to eucl
			 * plane, which should go to the South pole. (only need a radius for
			 * its sign -- tell if we want the outside.)
			 */
			C[0] = V[0] * Math.cos(argw) + T[0] * Math.sin(argw);
			C[1] = V[1] * Math.cos(argw) + T[1] * Math.sin(argw);
			C[2] = Srad = V[2] * Math.cos(argw) + T[2] * Math.sin(argw);
			denom = Math.sqrt(C[0] * C[0] + C[1] * C[1]);
			if (denom < MOB_TOLER) { // at one of the poles?
				Sctr = new Complex(0.0);
				Smod = 0.0;
			} else {
				Smod = denom / (1.0 + C[2]); // modulus of eucl center
				Sctr = new Complex(C[0] * Smod / denom, C[1] * Smod / denom);
			}

			// now the point intended to go to the North pole
			C[0] = V[0] * Math.cos(argz) + T[0] * Math.sin(argz);
			C[1] = V[1] * Math.cos(argz) + T[1] * Math.sin(argz);
			C[2] = Nrad = V[2] * Math.cos(argz) + T[2] * Math.sin(argz);
			denom = Math.sqrt(C[0] * C[0] + C[1] * C[1]);
			if (denom < MOB_TOLER) { // at one of the poles
				Nctr = new Complex(0.0);
				Nmod = 0.0;
			} else {
				Nmod = denom / (1.0 + C[2]); // modulus of eucl center
				Nctr = new Complex(C[0] * Nmod / denom, C[1] * Nmod / denom);
			}

			// Now build appropriate mobius

			if (Nmod < MOB_TOLER || Smod < MOB_TOLER) { // at least one at a
														// pole
				if (Nmod < MOB_TOLER) {
					if (Smod < MOB_TOLER) { // Sctr is at other
						if (Nrad < 0) { // simply have to invert
							MM.a = new Complex(0.0);
							MM.b = new Complex(1.0);
							MM.c = new Complex(1.0);
							MM.d = new Complex(0.0);
							MM.oriented = true;
							MM.normalize();
						} else
							MM = new Mobius(); // identity
					} else {
						if (Nrad < 0) { /*
										 * Nctr at south pole, need to invert
										 * first, get new Sctr
										 */
							M1.a = new Complex(0.0);
							M1.b = new Complex(1.0);
							M1.c = new Complex(1.0);
							M1.d = new Complex(0.0);
							M1.oriented = true;
							M1.normalize();

							denom = Sctr.x * Sctr.x + Sctr.y * Sctr.y;
							Sctr = new Complex(Sctr.x / denom, -Sctr.y / denom);
						} else
							M1 = new Mobius(); // identity
						// move Sctr to infinity
						M2.a = new Complex(0.0);
						M2.b = new Complex(1.0);
						M2.c = new Complex(1.0);
						M2.d = new Complex(-Sctr.x, -Sctr.y);
						M2.oriented = true;
						M2.normalize();

						MM = (Mobius) M2.rmult(M1); // M2*M1
					}
				} else {
					if (Srad > 0) { /*
									 * Sctr at north pole, need to invert first,
									 * get new Nctr
									 */
						M1.a = new Complex(0.0);
						M1.b = new Complex(1.0);
						M1.c = new Complex(1.0);
						M1.d = new Complex(0.0);
						M1.oriented = true;

						denom = Nctr.x * Nctr.x + Nctr.y * Nctr.y;
						Nctr = new Complex(Nctr.x / denom, -Nctr.y / denom);
					} else
						M1 = new Mobius(); // identity
					// move Nctr to origin
					M2.a = new Complex(1.0);
					M2.b = new Complex(-Nctr.x, Nctr.y);
					M2.c = new Complex(0.0);
					M2.d = new Complex(1.0);
					M2.oriented = true;
					M2.normalize();
					MM = (Mobius) M2.rmult(M1); // M2*M1
				}
			} else { // generic case; move Nctr to origin, Sctr to infty
				MM.a = new Complex(1.0);
				MM.b = new Complex(-Nctr.x, -Nctr.y);
				MM.c = new Complex(1.0);
				MM.d = new Complex(-Sctr.x, -Sctr.y);
				MM.oriented = true;
				MM.normalize();
			}
		}

		// Now we have MM; need images of sph circles under MM
		CircleSimple sc = new CircleSimple(true);
		mobius_of_circle(MM, 1, zN, rN, sc, true);
		double rrN = sc.rad;
		mobius_of_circle(MM, 1, zS, rS, sc, true);
		double rrS = sc.rad;
		Complex zzE = null;
		double rrE = 0.0;
		if (!dilation_flag) {
			mobius_of_circle(MM, 1, zE, rE, sc, true);
			zzE = new Complex(sc.center);
			rrE = sc.rad;
		}
		/* Have two choices for normalization */

		/*
		 * Case 1: Place circle 1 at one (when on sphere).
		 * 
		 * Project pts of E closest to N and to S to the positive numbers a and
		 * b. Necessary dilation is t where t^2=1/(ab).
		 */

		if (!dilation_flag) {
			double phi = zzE.y - rrE;
			a = Math.sin(phi) / (1.0 + Math.cos(phi));
			phi = zzE.y + rrE;
			b = Math.sin(phi) / (1.0 + Math.cos(phi));
			t = Math.sqrt(1 / Math.abs(a * b)); /*
												 * ab should be positive, but
												 * fabs() is cautionary
												 */

			M1.a = new Complex(0.0, -zzE.x);
			M1.a = M1.a.exp().times(t);
			M1.b = new Complex(0.0);
		}

		/*
		 * Case 2: first scale by t to get N/S circles the same size; then
		 * dilate by factor.
		 */
		else {
			a = Math.sin(rrN) / (1.0 + Math.cos(rrN));
			double phi = Math.PI - rrS;
			b = Math.sin(phi) / (1.0 + Math.cos(phi));
			t = Math.sqrt(1 / Math.abs(a * b)); /*
												 * ab should be positive, but
												 * fabs() is cautionary
												 */

			M1.a = new Complex(t * factor);
			M1.b = new Complex(0.0);
		}
		M1.c = new Complex(0.0);
		M1.d = new Complex(1.0);
		M1.oriented = true;
		M1.normalize();

		return (Mobius) M1.rmult(MM);
	}

	/**
	 * Create Mobius mapping outside of eucl circle c1 onto inside of eucl
	 * circle c2. Radius of c1 (resp. c2) may be negative meaning it contains
	 * infinity: must move to unit circle, invert, move back, then proceed.
	 * 
	 * @param ctrl Complex, center of c1
	 * @param CPrad1 double, radius of c1
	 * @param ctr2 Complex, center of c2
	 * @param CPrad2 double, radius of c2
	 * @return Mobius, identity on error and increment err_flag.
	 */
	public static Mobius cir_invert(Complex ctr1, double CPrad1, Complex ctr2,
			double CPrad2) {
		Mobius mob1;
		Mobius mob2;
		
		if (Math.abs(CPrad1)<.00000000001 || Math.abs(CPrad2)<.00000000001)
			throw new MobException("one of radii is too small to work with");
		
		Complex a = new Complex(ctr1);
		a.x+=Math.abs(CPrad1);
		Complex b = new Complex(ctr2);
		b.x+=Math.abs(CPrad2);
		
		mob1=(Mobius)Mobius.affine_mob(ctr1,a,ctr2,b);
		
		// just need affine map carrying c1 to c2
		if (CPrad1<0 && CPrad2>0) 
			return mob1; 
		
		// need affine c1 to c2, then invert in c2 
		if (CPrad1<0.0) { // note: CPrad2<0
			Mobius toU=Mobius.affine_mob(ctr2,b,new Complex(0.0),new Complex(1.0));
			return (Mobius)toU.inverse().rmult(Mobius.recip_mob().rmult(toU));
		}
		
		// inversion in c2 with center/rad (c.r) given by z -->
		// (c*z+r*r-c*c)/(z-c)
		Complex w = ctr2.times(ctr2);
		mob2 = new Mobius(ctr2, new Complex(CPrad2 * CPrad2 - w.x, -w.y),
				new Complex(1.0), new Complex(-ctr2.x, -ctr2.y));
		return (Mobius) mob2.rmult(mob1);
	}

	/**
	 * Create Mobius giving (anticonformal) reflection in euclidean circle.
	 * Given radius r may be negative for outside of the circle; this
	 * doesn't affect the computations.
	 * @param ctr Complex
	 * @param r double, |r| > .00000000001 (r may be negative for outside of eucl circle,
	 *            but that doesn't change affect routine.
	 * @return Mobius, det = -1 (identity if circle is too small)
	 */
	public static Mobius reflected(Complex ctr, double r) {
		r=Math.abs(r);
		if (r < .00000000001)  // r too small
			throw new MobException("radius too small");
		return new Mobius(new Complex(ctr), new Complex(r * r - ctr.abs()),
				new Complex(1.0), new Complex(-ctr.x, ctr.y), false);
	}

	/**
	 * Create Mobius z-->1/z
	 * 
	 * @return Mobius
	 */
	public static Mobius recip_mob() {
		return new Mobius(new Complex(0.0), new Complex(1.0), new Complex(1.0),
				new Complex(0.0));
	}

	/**
	 * For Mobius, returns the distance of normalized mobius from identity in
	 * the Frobenius norm (which is sqrt of sum of squares of abs entries).
	 * Useful for seeing how close a mobius is to being the identity.
	 * 
	 * @param mob Mobius
	 * @return double, Frobenius norm, >=0, but -1 on error (such as extreme det(mob))
	 */
	public static double frobeniusNorm(Mobius mob) {
		if (mob == null)
			return -1.0;

		// ill conditioned?
		double cond = mob.det().abs();
		if (cond < .0000000000001 || cond > 1000000000)
			return -1.0;

		mob.normalize();
		double sum = mob.a.minus(new Complex(1.0)).absSq();
		sum += mob.b.absSq();
		sum += mob.c.absSq();
		sum += mob.d.minus(new Complex(1.0)).absSq();

		return Math.sqrt(sum);
	}

	/**
	 * Create a new @see Path2D.Double which is Mobius transformation of given
	 * path. If 'oriented' is false, apply inverse of mob.
	 * @param mob Mobius
	 * @param gp Path2D.Double
	 * @param oriented boolean
	 * @return Path2D.Double, null on error
	 */
	public static Path2D.Double path_Mobius(Mobius mob, Path2D.Double gp,
			boolean oriented) {
		if (gp == null)
			return null;
		Path2D.Double newgp = new Path2D.Double();
		Complex z, w;
		int type;
		double[] coords = new double[2];

		// this should be a FlattenedPathIterator (but not sure)
		PathIterator gpit = gp.getPathIterator(null, .01);
		while (!gpit.isDone()) {
			type = gpit.currentSegment(coords);
			switch (type) {
			case PathIterator.SEG_MOVETO:
				z = new Complex(coords[0], coords[1]);
				w = mob.apply(z, oriented);
				newgp.moveTo(w.x, w.y);
				break;
			case PathIterator.SEG_LINETO:
				z = new Complex(coords[0], coords[1]);
				w = mob.apply(z, oriented);
				newgp.lineTo(w.x, w.y);
				break;
			case PathIterator.SEG_CLOSE:
				newgp.closePath();
				break;
			default:
				System.out.println("Unexpected type: " + type);
			}
			gpit.next();
		}
		return newgp;
	}

	/**
	 * Create a new Mobius object which is identical to 'this'.
	 * @return Mobius
	 */
	public Mobius copyMob() {
		Mobius mob = new Mobius();
		mob.a = new Complex(a);
		mob.b = new Complex(b);
		mob.c = new Complex(c);
		mob.d = new Complex(d);
		mob.oriented = oriented;
		mob.error = error;
		mob.util = util;
		return mob;
	}
	
	/**
	 * Generate a string giving a, b, b, d (and orientation) for use in output.
	 * TODO: should I just override "toString"?
	 * @return StringBuilder
	 */
	public StringBuilder mob2String() {
		String lsep=System.getProperty("line.separator");
		String fmstr="%.10e";
		StringBuilder strbld= new StringBuilder("a = "+String.format(fmstr, a.x)+" + "+String.format(fmstr, a.y)+"i;"+lsep+
								"b = "+String.format(fmstr, b.x)+" + "+String.format(fmstr, b.y)+"i;"+lsep+
								"c = "+String.format(fmstr, c.x)+" + "+String.format(fmstr, c.y)+"i;"+lsep+
								"d = "+String.format(fmstr, d.x)+" + "+String.format(fmstr, d.y)+"i");
		if (!oriented)
			strbld.append(lsep+"  -1");
		return strbld;
	}

	/**
	 * See what this mobius does to circles for v and w.
	 * @param schwarzMap TODO
	 * @param f int, face
	 * @param v 
	 * @param w
	 */
	public void debugMob(SchwarzMap schwarzMap, int f, int v, int w) {
		System.out.println("Face "+f+": First circle "+v);
		Schwarzian.CirMobCir(this,schwarzMap.packData.hes,schwarzMap.packData.rData[v].rad,schwarzMap.packData.rData[v].center);
		System.out.println("Second circle "+w);
		Schwarzian.CirMobCir(this,schwarzMap.packData.hes,schwarzMap.packData.rData[w].rad,schwarzMap.packData.rData[w].center);
	}
}
