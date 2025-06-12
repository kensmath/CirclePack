package geometry;
import allMains.CPBase;
import baryStuff.BaryPoint;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import dcel.PackDCEL;
import exceptions.DataException;
import exceptions.LayoutException;
import exceptions.MobException;
import math.Mobius;
import math.Point3D;
import packing.PackData;

/**
 * Static methods for mathematical operations in hyperbolic geometry.
 *
 * Our model of the hyperbolic plane is the unit disc with the Poincare
 * metric; namely ds=2|dz|/(1-z^2), so curvature is -1.
 * 
 * Note on radii: h is "hyperbolic" radius, value the user sets/sees,
 * but internal storage is using "x-radius" and on occasion, "s-radius".
 * Infinite h corresponds with s=0 and x=1.
 * 
 * Some formulae:
 * 
 * * relations among the h-, x-, and s-radii:
 * 	   s=exp(-h),  x = 1-s*s,  s=sqrt(1-x),  h = log(1/s) = -1/2 log(1-x)
 * 
 * * point with eucl distance r and hyp distance h from origin, then:
 * 	    h=log((1+r)/(1-r)),  r=(exp(h)-1)/(exp(h)+1),  s=(1-r)/(1+r) 
 *   and cosh(h)=(1+r*r)/(1-r*r)
 *   
 * * for a circle eucl rad r at the origin this means:
 * 		s = (1-r)/(1+r),  r = (1-s)/(1+s),  x = 4r/(r+1)^2 = 4r/(1+2r+r^2), 
 *      and	r=(2-x - 2sqrt(1-x)) / x,  h = log((1+r)/(1-r))
 * 
 * * Hyperbolic law of cosines: Given hyp side lengths l1, l2, l3, the
 *   angle a1 opposite side l1 is
 * 		a1 = acos( (cosh(l2)*cosh(l3) - cosh(l1)) / (sinh(l2)*sinh(l3)) )
 *   For infinite lengths, this is ambiguous, but can be resolved from
 *   radii if triangle is formed by circles. See 'h_cos_s_overlap' and
 *   'h_comp_x_cos'.
 *   
 * * If ircle at origin of eucl radius a has inv dist ivd with a horocycle,
 *   then the eucl rad of horocycle is given by b
 *       b = (1-a*a)/(2+2*a*ivd)
 *   
 * * See 'h_to_e_data' and 'e_to_h_data' for converting centers/radii
 *   
 * * hyp distance l between circles of radii h1, h2, and inv distance ivd: 
 *      cosh(l) = cosh(h1)*cosh(h2)+sinh(h1)*sinh(h2)*ivd
 *   length l infinite iff at least one of h1, h2 is infinite. 
 *   See 'h_invdist_length' and 'h_dist'
 *        
 * In addition to the disc, hyperbolic geometry has the "hyperboloid" 
 * model. Namely, consider the sheet S with t>0 of the hyperboloid 
 * t^2-(x1^2+x2^2)=1, or t=sqrt(1+(x1^2+x2^2)). (Note, this is 
 * restriction to z=0 of the model for hyperbolic 3-space. 
 * See 'SphereLayout.java'.)
 * 
 * Point (t,x1,x2) of S is associated with point (u1,u2,0) 
 * of unit disc in the plane lying on the line from 
 * (t,x1,x2) to (-1,0,0). 
 * 
 * So (t,x1,x2) <--> (u1,u2), where
 *    uj=xj/(1+t), j=1,2
 *    t=sqrt(1+(x1^2+x2^2))
 *    xj=2uj/(1-(u1^2+u2^2))=2uj/(1-||u||^2), j=1,2
 *    t=(1+||u||^2)/(1-||u||^2) with ||u||^2=u1^2+u2^2
 * 
 * There is also the "Klein" or "Beltrami-Klein" model, also in the
 * unit disc. In this model, hyperbolic geodesics are euclidean 
 * straight lines, useful, eg., in 'pt_in_hyp_tri'. Given z in the
 * disc in Poicare model and w in the disc in Klein model, then
 *    w = (2*z)/(1+|z|^2), and z = w/(1+sqrt(1-|w|^2)).
 */
public class HyperbolicMath{
	
	// TODO: set this more rationally
	public static final double OKERR = .000000001; 

	/**
	 * Gives the euclidean representation of a hyperbolic circle for display
	 * purposes.
	 * 
	 * @param cs CircleSimple
	 * @return CircleSimple
	 */
	public static CircleSimple h_to_e_data(CircleSimple cs) {
		return h_to_e_data(cs.center, cs.rad);
	}

	/**
	 * Gives the euclidean representation of a hyperbolic 
	 * circle for display purposes.
	 * 
	 * @param h_center Complex
	 * @param x_rad    double
	 * @return CircleSimple
	 */
	public static CircleSimple h_to_e_data(Complex h_center,double x_rad) {
		double e_rad;
		Complex e_center = null;

		// Note: negative means infinite hyp radius; expect 
		//    value to be negative of eucl radius of horocycle.
		if (x_rad < 0) { 
							// 
			int warning_flag = 1;
			e_rad = -x_rad;
			if (e_rad >= 1.0) {
				warning_flag = -1;
				e_rad = 1.0 - OKERR;
			}
			double habs = h_center.abs();
			if (habs > OKERR)
				e_center = h_center.divide(habs).times(1 - e_rad);
			else {
				warning_flag = -1;
				e_center = new Complex(1.0).times(1 - e_rad);
			}
			return new CircleSimple(e_center, e_rad, warning_flag);
		}
		double s_rad = x_to_s_rad(x_rad);

		double ahc = h_center.abs();
		if (ahc > 0.999999999999)
			ahc = 0.999999999999;

		double g = ((1 + ahc) / (1 - ahc));
		double a = g / s_rad;
		double d = (a - 1) / (a + 1);
		double b = g * s_rad;
		double k = (b - 1) / (b + 1);
		e_rad = (d - k) * 0.5;
		double aec = (d + k) * 0.5;

		if (ahc < 0.0000000000001) { // round off to origin as center.
			e_center = new Complex(0.0);
		} else {
			b = aec / ahc;
			e_center = h_center.times(b);
		}
		return new CircleSimple(e_center, e_rad, 1);
	}

	/**
	 * Converts circle data from eucl to hyp with x-rad. 
	 * For horocycle, x-rad is negative of eucl radius. 
	 * Circles outside disc are scaled to become horocycles
	 * and returned 'flag' is -1.
	 * 
	 * @param e_center Complex
	 * @param e_rad    double
	 * @return CircleSimple
	 */
	public static CircleSimple e_to_h_data(Complex e_center, double e_rad) {
		CircleSimple h_circle = new CircleSimple(true);
		h_circle.flag = 1;
		double aec = e_center.abs(); // e_center.arg()/Math.PI;
		double dist = aec + e_rad;

		if (dist > 1.000001) {
			aec /= dist;
			e_rad /= dist;
			e_center = e_center.divide(dist);
			dist = 1.0;
			h_circle.flag = -1;
		}
		if ((.99999) < dist) { // horocycle
			h_circle.rad = -e_rad;
			h_circle.center = e_center.divide(aec);
			return h_circle;
		}
		double c2 = aec * aec;
		double r2 = e_rad * e_rad;
		if (aec < .0000000000001) { // circle at origin
			h_circle.center = new Complex(0.0);
		} else {
			double b = Math.sqrt((1 + 2 * aec + c2 - r2) / 
					(1 - 2 * aec + c2 - r2));
			double ahc = (b - 1) / (b + 1);
			b = ahc / aec;
			h_circle.center = e_center.times(b);
		}
		h_circle.rad = s_to_x_rad(Math.sqrt((1 - 2 * e_rad + r2 - c2) / 
				(1 + 2 * e_rad + r2 - c2)));
		return h_circle;
	}

	/**
	 * temp (??) routine to convert s-radius to x-radius
	 * 
	 * @param s double
	 * @return double
	 */
	public static double s_to_x_rad(double s) {
		if (s <= 0)
			return s;
		return (1.0 - s * s);
	}

	/**
	 * convert x-radius to s-radius. If x<=0, just return x; 
	 * might be intentionally negative.
	 * 
	 * @param x double
	 * @return double
	 */
	public static double x_to_s_rad(double x) {
		if (x <= 0.0)
			return x;
		if (x >= 1.0)
			x = .99999999;
		return (Math.sqrt(1.0 - x));
	}

	/**
	 * Converts 'x-radius' (the way hyp radii are generally stored) 
	 * to the 'h-radius', actual hyp radius (the user-friendly form). 
	 *     x=1-exp(-2h) (@see PackData.getRadius).
	 * 
	 * @param x double, x-radius
	 * @return double, h-radius; if x<=0, return x itself
	 */
	public static double x_to_h_rad(double x) {
		if (x > 0.0) {
			if (x > .0001)
				return ((-0.5) * Math.log(1.0 - x));
			else
				return (x * (1.0 + x * (0.5 + x / 3)) / 2);
		}
		return x;
	}

	public static CircleSimple e_to_h_data(CircleSimple cs) {
		return e_to_h_data(cs.center, cs.rad);
	}

	/**
	 * Return the eucl rad for horocycle which overlaps a 
	 * circle centered at origin and having x-radius x1. at 
	 * origin having s-radius >0 given the cosine of the
	 * overlap angle.
	 * 
	 * @param x1      double, x-radius of circle at origin
	 * @param invdist double, inversive distance between circles
	 * @return double, 0.0 if circle at origin is a horocycle
	 */
	public static double h_horo_rad(double x1, double invdist) {
		double s1 = x_to_s_rad(x1);
		if (s1 <= 0) // horocycle at origin?
			return 0;
		double r = (1 - s1) / (1 + s1); // get eucl radius
		double R = (1 - r * r) / (2.0 + 2 * r * invdist);
		return R;
	}

	/**
	 * Given ordered triple of x-radii, compute cosine(angle) 
	 * at first circle in triangle formed by mutually tangent 
	 * circles.
	 *  
	 * TODO: These come from laws of cosines, but I'm not sure
	 * the ones involving a single horocycle are right.
	 * 
	 * (If there are inv distances, see 'h_comp_cos'.)
	 * 
	 * @param x0 double
	 * @param x1 double
	 * @param x2 double
	 * @return double, cos of angle
	 */
	public static double h_comp_x_cos(double x0, double x1, double x2) {
		if (x0 <= 0)
			return (1.0);
		if ((x1 <= 0) && (x2 <= 0))
			return (2.0 * x0 - 1); // OK
		if (x1<= 0) {
			double tmp = x2 - x0 * x2;
			return ((x0 - tmp) / (x0 + tmp)); // OK
		}
		if (x2 <= 0) {
			double tmp = x1 - x0 * x1;
			return ((x0 - tmp) / (x0 + tmp)); // OK
		}
		
		double ans = x0 * (x0 + (1.0 - x0) * (x1 + x2 - x1 * x2)) / 
				((x0 + x2 - x0 * x2) * (x0 + x1 - x0 * x1));
		ans = 2.0 * ans - 1.0; // OK
		if (ans > 1.0)
			return 0.9999999999999;
		if (ans < -1.0)
			return -0.9999999999999;
		return (ans);
	}

	/**
	 * Given three x-radii for tangent circles, compute
	 * up.value=cos(angle at e1).
	 * @param x0 double
	 * @param x1 double
	 * @param x2 double
	 * @return double cos(angle)
	 */
	public static double h_comp_cos(double x0, double x1, double x2) {
		return h_comp_cos(x0, x1, x2, 1.0, 1.0, 1.0);
	}

	/**
	 * Given three x-radii and inv distances, compute 
	 * up.value=cos(angle at x0). (ivdj = inv_dist of edge <j,j+1>)
	 * @param x0   double
	 * @param x1   double
	 * @param x2   double
	 * @param ivd0 double
	 * @param ivd1 double
	 * @param ivd2 double
	 * @return double cos(angle)
	 */
	public static double h_comp_cos(double x0,double x1,double x2,
			double ivd0,double ivd1,double ivd2) {

		// all tangencies? call other routine
		if ((ivd0 == 1) && (ivd1 == 1) && (ivd2 == 1)) {
			return h_comp_x_cos(x0, x1, x2);
		}

		// all radii finite? use hyp cosine law
		if (x0 > 0 && x1 > 0 && x2 > 0) {
			// cosh of edge lengths
			double ch0 = h_ivd_cosh(x0, x1, ivd0);
			double ch1 = h_ivd_cosh(x1, x2, ivd1); 
			double ch2 = h_ivd_cosh(x2, x0, ivd2);
			// law of cosines: ch1=ch0*ch2-s0*s1*ivd 
			//    recall sinh^2=cosh^2-1, so sj=sqrt(chj*chj-1)
			return (ch0 * ch2 - ch1) / 
					Math.sqrt((ch2 * ch2 - 1.0) * (ch0 * ch0 - 1.0));
		}

		// if first is horocycle, angle is zero.
		if (x0 <= 0) {
			return 1.0;
		}

		// Remaining cases must involve horocycle(s). We compute 
		//   using eucl radii e0, e1, e2 and inv distances, assuming 
		//   first circle is centered at the origin.
		double s0 = x_to_s_rad(x0); // recall x=1-s*s
		double e0 = (1.0 - s0) / (1.0 + s0);
		double e1;
		double e2;

		double s1 = x_to_s_rad(x1);
		double s2 = x_to_s_rad(x2);

		// Now find the eucl radii for the two petals. 
		
		// second is horocycle?
		if (x1 <= 0) { 
			if (ivd0 == 1.0) { // tangency: e0+2*e1=1
				e1 = 0.5 * (1.0 - e0);
			}
			// else we compute eucl dist d between centers,
			// then use d+e1=1 to find e1.
			else {
				e1 = (1.0 - e0 * e0) / (2 * (e0 * ivd0 + 1.0));
			}
		}
		// else, average points C and F, closest, furthest from origin
		else {
			// hyp distance between centers +- hyp radius
			double hypdist=acosh(h_ivd_cosh(x0, x1, ivd0));
			double hyprad=-Math.log(1-x1)/2; 
			// recall r=(exp(h)-1)/(exp(h)+1)
			double exphypC = Math.exp(hypdist-hyprad);
			double exphypF = Math.exp(hypdist+hyprad);
			double C=(exphypC-1)/(exphypC+1);
			double F=(exphypF-1)/(exphypF+1);
			e1=(F-C)/2;
		}

/*		
			if (ivd0 == 1.0) { // tangency
				// E=e0+2*e1 <==> H=h0+2*h1, so E=(1-exp(-H))/(1+exp(-H))
				// and exp(-H)=s0*(s1^2)
				double s011 = s0 * s1 * s1;
				e1 = 0.5 * ((1 - s011) / (1 + s011) - e0);
			} else {
				// E=sqrt(e1^2+e2^2+2*e1*e2*inv)+e2 and H=h_dist+h2
				// E=(1-exp(-H))/(1+exp(-H))
				double ch0 = h_ivd_cosh(x0, x1, ivd0);
				double lch0 = ch0 + Math.sqrt(ch0 * ch0 - 1.0);
				double E = (lch0 - s1) / (lch0 + s1);
				e1 = (E * E - e0 * e0) / (2.0 * (e0 * ivd0 + E));
			}
		}
*/
		
		// third is horocyle?
		if (x2 <= 0) { 
			if (ivd2 == 1.0) { // tangency: e0+2*e1=1
				e2 = 0.5 * (1.0 - e0);
			}
			// else we compute eucl dist d between centers,
			// then use d+e1=1 to find e1.
			else {
				e2 = (1.0 - e0 * e0) / (2 * (e0 * ivd2 + 1.0));
			}
		}
		// else, half diameter (closest to furthest from origin)
		else {
			// hyp distance between centers +- hyp radius
			double hypdist=acosh(h_ivd_cosh(x0, x2, ivd2));
			double hyprad=-Math.log(1-x2)/2; 
			// recall r=(exp(h)-1)/(exp(h)+1)
			double exphypC = Math.exp(hypdist-hyprad);
			double exphypF = Math.exp(hypdist+hyprad);
			double C=(exphypC-1)/(exphypC+1);
			double F=(exphypF-1)/(exphypF+1);
			e2=(F-C)/2;
		}

/*		
		if (s2 <= 0) { // horocycle? So E=1
			if (ivd2 == 1.0) { // tangency: e0+2*e1=1
				e2 = 0.5 * (1.0 - e0);
			}
			// else we compute eucl dist d between centers,
			// then use d+e1=1 to find e1.
			else {
				e2 = (1.0 - e0 * e0) / (2 * (e0 * ivd2 + 1.0));
			}
		} else { // else regular circle; compare eucl/hyp dist to M
			if (ivd2 == 1.0) { // tangency
				// E=e0+2*e2 <==> H=h0+2*h2, so E=(1-exp(-H))/(1+exp(-H))
				// and exp(-H)=s0*(s2^2)
				double s022 = s0 * s2 * s2;
				e2 = 0.5 * ((1 - s022) / (1 + s022) - e0);
			} else {
				// E=sqrt(e0^2+e1^2+2*e0*e1*inv)+e1 and H=h_dist+h1
				// E=(1-exp(-H))/(1+exp(-H))
				// double ch2 = h_ivd_cosh(x2, x0, ivd2);
				// double lch2 = ch2 + Math.sqrt(ch2 * ch2 - 1.0);
				// double E = (lch2 - s1) / (lch2 + s1);
				// e2 = (E * E - e0 * e0) / (2.0 * (e0 * ivd2 + E));
				
			}
		}
*/
		
		// Now compute with the euclidean radii
		return EuclMath.e_cos_overlap(e0, e1, e2, ivd0, ivd1, ivd2);
	}

	/**
	 * Find third circle in triple when first two are
	 * horocycles with given centers (on unit circle),
	 * three radii (neg eucl for horocycles), along 
	 * with all inv distances. Want arc (z1,z0) to be
	 * considered cclw, so third circle is generally 
	 * outside the geodesic between them. Note that we 
	 * make no consistency checks.
	 * 
	 * Create Mobius M of disc to UHP with 
	 *     a -> 0 x -> i, b -> infinity,
	 * where x is the intersection of first (sometimes
	 * second) horocycle with the geodesic arc between
	 * them. 
	 *    M=[i*(b-x), i*a*(x-b); a-x, b*(x-a)].
	 * M moves the unit disc to the UHP; the
	 * first horocycle has center at the origin and goes
	 * through i (so radius 1/2), while the second horocycle 
	 * is a horizontal line
	 * 
	 * Computational fact: in UHP, if one horocycle is tangent to 
	 * real line and has eucl rad R and another is tangent at 
	 * infinity with inv distance d between them, then second is
	 * horizontal line at eucl height (1+d)*R. We also use this
	 * fact, properly adjusted, when third is not a horocycle.
	 * 
	 * Under M, first becomes circle through i and tangent at 
	 * origin, second is horizontal line through at height 
	 * H = 1+ivd0. If third is horocycle, its eucl rad is 
	 * R2=H/(1+ivd1). If x2>0, then can solve for eucl radius 
	 * R2 by using overlap ivd1 with horizontal line and using 
	 * hyperbolic radius h as integral (1/t)dt. 
	 * 
	 * In both cases, compute eucl center z2 and radius R2 of
	 * third circle. Apply inv(M) to get eucl third circle in
	 * the disc, and finally, convert to hyperbolic circle.
	 *  
	 * @param z0   Complex
	 * @param z1   Complex
	 * @param x1 double (neg of eucl radius)
	 * @param z2 third radius
	 * @param ivd0 double
	 * @param ivd1 double
	 * @param ivd2 double
	 * @return CircleSimple: not rad is negative of eucl radius
	 */
	public static CircleSimple h_horo_center(Complex z0,Complex z1,
			double x0, double x1, double x2, 
			double ivd0,double ivd1,double ivd2) {
		
		Complex x=new Complex(0.0);
		double way=-1.0; // third is to left of y-axis in UHP

		// First have to find x. 2*theta is angle at origin of
		//    arc spanned by geodesic between z0, z1.
		double theta=z0.divide(z1).arg(); // theta/Math.PI; 
		if (theta<0) 
			theta=theta+2*Math.PI;  // put it between 0 and pi
		theta *=0.5;
		
		// If z0, z1 too close, return error
		if (theta<.00001 || 2.0*Math.PI-theta<.00001) {
			throw new DataException("bdry z0, z1 are too close");
		}
		
		// Cases: 
		
		// z0, z1 essentially antipodal
		if (Math.abs(theta-CPBase.piby2)<.000001) {
			x=z0.times(1+2*x0);  // recall eucl rad is -x0
		}
		else {
			// if arc (z0,z1) is greater than pi, then interchange
			//   first and second
			if ((theta-CPBase.piby2)>.000001) {
				way=1.0;
				double hold=x0;
				x0=x1;
				x1=hold;
				hold=ivd1;
				ivd1=ivd2;
				ivd2=hold;
				Complex holdz=z0;
				z0=z1;
				z1=holdz;
				theta=Math.PI-theta;
			}
		
			// find x by finding circle containing the geodesic
			//    from z0 to z1. Radius is X, center centX is along 
			//    bisector of geodesic.
			double X=Math.tan(theta); 
			Complex rot=new Complex(0,-theta);
			rot=rot.exp();
			Complex centX=z0.times(rot).divide(Math.cos(theta));
			double alpha=Math.atan(-x0/X);
			Complex euclz0=z0.times(1+x0).minus(centX); // eucl center of z0
			rot=new Complex(0,alpha);
			rot=rot.exp();
			x=euclz0.divide(euclz0.abs()).times(rot).times(X).add(centX);			
		}
		
		// generate map
		Mobius map=new Mobius(
				new Complex(0,1).times(z1.minus(x)),
				new Complex(0,1).times(z0.times(x.minus(z1))),
				z0.minus(x),z1.times(x.minus(z0)));
		map.normalize(); 
		Mobius remap=(Mobius)map.inverse(); // (Mobius)map.rmult(remap);

		CircleSimple csIn=new CircleSimple(); // needed later

		if (x2<0) { // third is horocycle
			double R2=(1.0+ivd0)/(2*(1.0+ivd1)); // eucl rad of third
				
			// find eucl center of target circle using right
			//    triangle formed by centers
			double hypot2=0.25+R2*R2+R2*ivd2; // dist, first/third, squared
			double l2=(R2-0.5); // height 
			double C2 = Math.sqrt(hypot2-l2*l2); // base
			Complex z2=new Complex(way*C2,R2); // real part negative
			csIn=new CircleSimple(z2,R2);
		}
		
		// more complicated if third is finite. Letting R2 denote
		//   its radius, ivd2 gives us h=(1+ivd2)*R2 as distance 
		//   that bottome of circle is below H. Get another expression
		//   because the diameter, 2r is 2*(hyp rad)=2*log(1/sqrt(1-z)).
		// So we can solve for R2.
		else {
			double L=(1.0+ivd0)/2; // height of horizontal line
			double R2=L*x2/(2-x2+x2*ivd1);
			// triangle form by eucl centers of first ant third
			double Y=L-R2*ivd1;
			double X=way*Math.sqrt(0.25+R2*R2+R2*ivd2-(Y-0.5)*(Y-0.5));
			Complex cent=new Complex(X,Y); // remap.apply(cent).arg()/Math.PI;
			csIn=new CircleSimple(cent,R2); // eucl third circ
		}
		// find eucl circle image under remap

/*
// debuggin
		Complex a=csIn.center.add(new Complex(csIn.rad,0));
		Complex b=new Complex(csIn.center.x,1.0);
		Complex c=csIn.center.minus(new Complex(csIn.rad,0));
		
		Complex za=remap.apply(a);
		Complex zb=remap.apply(b);
		Complex zc=remap.apply(c);
		
		CircleSimple altOut=EuclMath.circle_3(za, zb, zc);
		
		System.out.println("remap(i) = "+remap.apply(new Complex(0.0,1.0)));
*/		
		CircleSimple csOut=new CircleSimple(); // csOut.center.arg()/Math.PI;
		Mobius.mobius_of_circle(remap, 0, csIn,csOut,true);
//		System.out.println("csOut: eucl center = "+altOut.center+"; 
//		       eucl rad = "+altOut.rad);
//		System.out.println("altOut: eucl center = "+altOut.center+"; 
//		       eucl rad = "+altOut.rad);

		// Convert to hyp data
		return e_to_h_data(csOut);
	}

	/**
	 * TODO: some confusion on what this does and whether 
	 * there's an error. All three circles are horocycles, so 
	 * centers z0, z1 should be on unit circle. Idea is to 
	 * compute data for third circle. Third center should be 
	 * centered on unit circle, so radius returned should be the 
	 * negative of its eucl radius. e1 is positive eucl radius 
	 * of circle 1. (??) Inversive distances passed as
	 * usual (ivdj = inv_dist of edge <j,j+1>).
	 * 
	 * @param z0   Complex
	 * @param z1   Complex
	 * @param e2   double
	 * @param ivd0 double
	 * @param ivd1 double
	 * @param ivd2 double
	 * @return CircleSimple
	 * 
	 */
	public static CircleSimple old_h_horo_center(Complex z0, Complex z1, 
			double e2, double ivd0, double ivd1, double ivd2)
			throws MobException {

		// Is one of z0 or z1 too far from unit circle to be accurate?
		if ((z0.x * z0.x + z0.y * z0.y) < (0.999999999999) || 
				(z1.x * z1.x + z1.y * z1.y) < (0.999999999999)) {
			return (new CircleSimple(false));
		}

		/*
		 * Don't follow some of this: third circle is supposed 
		 * to be a horocycle, so s-rad should be zero. Let's assume 
		 * that (don't need x_rad, which used to be passed in here 
		 * as an argument). double S2=HyperbolicMath.x_to_s_rad(x_rad2);
		 * 
		 * if (S2<=0) srad1=0.0; else srad1=(S2)*(S2);
		 */
		/*
		 * first, compute eucl center/rad of third circle in a 
		 * normalized situation: namely first horo at -1 with 
		 * eucl rad R (computed below), second horo at 1, eucl 
		 * rad 1/2, target circle in upper half plane. We compute 
		 * r/ectr as eucl rad/cent of target circle in this situation.
		 */

		double srad1 = 0.0;
		double R = 2 / (ivd2 + 3.0);
		double d = Math.sqrt(0.25 + R * R + R * ivd2);
		double r = (2.0 * (d + R * ivd2) + 1.0) * (1.0 - srad1)
				/ (8.0 * d * (1.0 + srad1) + ((2.0 - 4.0 * d) * ivd0 - 
						4.0 * R * ivd1) * (srad1 - 1.0));
		Complex ectr = new Complex();
		ectr.x = 0.5 * (1 - d) - (1.0 / (2.0 * d)) * (0.25 + r * ivd0 - 
				R * R - 2.0 * R * r * ivd1);
		double a_sq = 0.25 + r * (r + ivd0);
		ectr.y = Math.sqrt(a_sq - (ectr.x - 0.5) * (ectr.x - 0.5));

		/*
		 * Next, find normalizing mobius transformations: 
		 * M1 moves original centers on unit circle to -1/1, resp., 
		 * while M2 fixes -1/1 and causes second circle to go 
		 * through the origin.
		 */

		// find 3 pts on the original second circle 
		//    (this is were e2 is used).
		Complex ecent = new Complex((1.0 - e2) * z1.x, (1.0 - e2) * z1.y);
		Complex p0 = new Complex(ecent.x, ecent.y + e2);
		Complex p1 = new Complex(ecent.y, ecent.x + e2);
		Complex p2 = new Complex(ecent.y, ecent.x - e2);

		Complex One = new Complex(1.0);
		Complex negOne = new Complex(-1.0);
		Complex Two = new Complex(2.0);
		Mobius M1 = (Mobius) Mobius.trans_abAB(z0, z1, negOne, One, Two, Two);
		/*
		 * if there's some error, err_flag will be set 
		 * (though we don't currently test it) and M1 should be 
		 * identity
		 */
		/*
		 * put 'Two' arguments in to signal that we accept one 
		 * unspecified degree of freedom; we handle it with work below.
		 */

		Complex w0 = M1.apply(p0);
		Complex w1 = M1.apply(p1);
		Complex w2 = M1.apply(p2);
		CircleSimple tc = EuclMath.circle_3(w0, w1, w2); // find new eucl rad
		double rad = tc.rad;
		double pp = (1.0 - 2 * rad); 
		// pp is the spot where M1(c2) hits real axis
		// M2(z)->(z-pp)/(-z*pp+1) moves pp to origin.
		Mobius M2 = new Mobius(new Complex(1.0, 0.0), 
				new Complex(-pp, 0.0), new Complex(-pp, 0.0),
				new Complex(1.0, 0.0));
		w0 = M2.apply(w0);
		w1 = M2.apply(w1);
		w2 = M2.apply(w2);
		tc = EuclMath.circle_3(w0, w1, w2); // find new eucl rad
		rad = tc.rad;

		// pick three pts on normalized circle three
		double h = ectr.abs();
		if (h > .0000000000001) { // typical: not centered at origin
			p0 = ectr.times((h + r) / h);
			p1 = ectr.times((h - r) / h);
			p2 = new Complex(ectr);
			p2.x += r;
		} else {
			p0 = new Complex(ectr);
			p0.x -= r;
			p1 = new Complex(ectr);
			p1.x += r;
			p2 = new Complex(ectr);
			p2.y -= r;
		}

		// apply inverses of first M2 and then M1.
		Mobius m1 = (Mobius) M1.inverse();
		Mobius m2 = (Mobius) M2.inverse();
		p0 = m2.apply(p0);
		p1 = m2.apply(p1);
		p2 = m2.apply(p2);
		p0 = m1.apply(p0);
		p1 = m1.apply(p1);
		p2 = m1.apply(p2);

		// images under mobius
		tc = EuclMath.circle_3(p0, p1, p2); // find new eucl cent/rad
		rad = tc.rad;
		// p1 should be on the unit circle
		return new CircleSimple(p0, -rad, 1);
		/*
		 * ?? Don't know what to make of this old stuff if 
		 * (S3<=.0000000000001) {horocycle? p1 should be on 
		 * unit circle return new CircleSimple(p1,-rad,1))
		 * return EuclideanToHyperbolic(z3,x_rad3);
		 */
	}

	/**
	 * Compute area of the hyperbolic triangle formed by circles 
	 * with given x-radii and inversive distances.
	 * 
	 * @param riP RadIvdPacket
	 * @return double
	 */
	public static double h_area(PackData p,int f) {
		combinatorics.komplex.DcelFace face=p.packDCEL.faces[f];
		HalfEdge he=face.edge;
		double accum=Math.PI;
		do {
			double r0=he.origin.rad;
			double ivd0=he.getInvDist();
			double r1=he.next.origin.rad;
			double ivd1=he.next.getInvDist();
			double r2=he.next.next.origin.rad;
			double ivd2=he.next.next.getInvDist();
			accum -= Math.acos(h_comp_cos(r0,r1,r2,ivd0,ivd1,ivd2));
			he=he.next;
		} while(he!=face.edge);

		return accum;
	}

	/**
	 * Find "incircle", hyp center/radius of circle inscribed 
	 * in trianglular face with given corners. ASSUME the three 
	 * are mutually tangent; we convert to eucl circles and 
	 * apply eucl algorithm. TODO: should also handle non-tangency cases
	 * -- e.g., just get incircle of arbitrary hyp triangle.
	 * 
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r0 double (hyp x-radius)
	 * @param r1 double (hyp x-radius)
	 * @param r2 double (hyp x-radius)
	 * @return CircleSimple
	 */
	public static CircleSimple hyp_tang_incircle(Complex z0,
			Complex z1, Complex z2, double r0, double r1, double r2) {
		CircleSimple sC0 = h_to_e_data(z0, r0);
		CircleSimple sC1 = h_to_e_data(z1, r1);
		CircleSimple sC2 = h_to_e_data(z2, r2);
		CircleSimple sc = EuclMath.eucl_tri_incircle(sC0.center, 
				sC1.center, sC2.center);
		return e_to_h_data(sc.center, sc.rad);
	}

	/**
	 * Get inscribed circle for any hyp triangle.
	 * 
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return CircleSimple
	 */
	public static CircleSimple hyp_tri_incircle(Complex z0,
			Complex z1, Complex z2) {
		Complex w0 = null;
		Complex w1 = null;
		Complex w2 = null;

		// if there is a non-horocycle, we will move it to the origin
		if (z0.absSq() < .9999) {
			w0 = z0;
			w1 = z1;
			w2 = z2;
		} else
			z0 = z0.divide(z0.abs());
		if (w0 == null && z1.absSq() < .9999) {
			w0 = z1;
			w1 = z2;
			w2 = z0;
		} else if (w0 == null)
			z1 = z1.divide(z1.abs());
		if (w0 == null && z2.absSq() < .9999) {
			w0 = z2;
			w1 = z0;
			w2 = z1;
		}

		// all essentially horocycles? Apply mobius to put them at -1, 1, i,
		// resp., then circle radius 1/4, center i/4
		if (w0 == null) {
			Mobius tmob = Mobius.trans_abAB(w0, w1, 
					new Complex(-1.0), new Complex(1.0), w2, 
					new Complex(0.0, 1.0));
			CircleSimple sC = new CircleSimple();
			int rslt = Mobius.mobius_of_circle(tmob, -1, 
					new Complex(0.0, 0.25), 0.25, sC, false);
			if (rslt == 0)
				throw new LayoutException("failed getting incircle "
						+"for ideal triangle");
			return sC;
		}

		// move w1 to origin, w2 to positive x-axis
		Mobius tmob = Mobius.standard_mob(w0, w1); // tmob.apply(w1);
		Complex pt1 = tmob.apply(w1);
		Complex pt2 = tmob.apply(w2); // image of w3

		// now find the center/rad of geodesic between pt2, pt3
		HypGeodesic hgeo = new HypGeodesic(pt1, pt2);

		// center is on bisecting ray
		double theta = pt2.arg() / 2.0;

		// use quadratic formula to find
		double ct = Math.cos(theta);
		double st = Math.sin(theta);
		double x = hgeo.center.x;
		double y = hgeo.center.y;
		double R = hgeo.rad;
		double b = -2.0 * (x * ct + (y + R) * st);
		double a = ct * ct;
		double c = x * x + y * y - R * R;

		double rho = (-b - Math.sqrt(b * b - 4.0 * a * c)) / (2.0 * a);
		double r = rho * st;
		Complex ctr = new Complex(rho * ct, r);

		CircleSimple sC = new CircleSimple();
		int rslt = Mobius.mobius_of_circle(tmob, -1, ctr, 
				Math.abs(r), sC, false);
		if (rslt == 0)
			throw new LayoutException("failed getting incircle "
					+ "for ideal triangle");
		// convert back to hyp geometry
		sC = e_to_h_data(sC);
		return sC;
	}

	/**
	 * Given two circles which are supposed to be tangent, 
	 * find the tangency point on the geodesic between them. 
	 * Actually, return pt with distances from z1, z2
	 * having proportions of euclidean radii rho1, rho2.
	 * 
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r1 (hyp x-radius)
	 * @param r2 (hyp x-radius)
	 * @return new Complex, null on error
	 */
	public static Complex hyp_tangency(Complex z1, Complex z2, 
			double r1, double r2) {
		CircleSimple sC1 = h_to_e_data(z1, r1);
		CircleSimple sC2 = h_to_e_data(z2, r2);
		return EuclMath.eucl_tangency(sC1.center, sC2.center, sC1.rad, sC2.rad);
	}

	/**
	 * Find the hyp center of third circle when given
	 * hyp centers/x-radii of first two (already in place), 
	 * x-radius of third, and inv distances ivd1/ivd2 of
	 * third and first two. 
	 * 
	 * Method involves putting given centers in normalized
	 * positions, computing third, then applying Mobius to
	 * reset given two centers. Mechanism depend on existence 
	 * of any horocycles, and some involve converting to 
	 * euclidean calculations:
	 * 
	 * Situations:
	 * (1) Most typical is no horocycles
	 * (2) First is finite:
	 *     (a) Second is horocycle, confert to euclidean
	 *     (b) Else if third is horocycle, interchange second/third,
	 *         set 'sgn=-1".
	 * (3) First is horocycle, second not, interchange them and 
	 *     set 'sgn' to -1.
	 * (4) First 2 are horycycles;
	 *     (a) If third is finite, exchange with first and
	 *         proceed as in (2)
	 *     (b) all horocycles? compute in euclidean
	 *     
	 * Method: assume first centers are at origin and 
	 * positive x-axis, use law of cosines to find angle
	 * at the origin. Use this with hyp edge length to third 
	 * origin to find third center. Finally, apply Mobius
	 * to reposition origin two centers. 
	 * to compute is to treat first circle as though at the 
	 * origin, compute the angle at the origin
	 * @param z0      Complex
	 * @param z1      Complex
	 * @param x0      double
	 * @param x1      double
	 * @param x2      double
	 * @param ivd0    double
	 * @param ivd1    double
	 * @param ivd2    double
	 * @return CircleSimple
	 */
	public static CircleSimple h_compcenter(Complex z0, Complex z1,
			double x0,double x1,double x2,
			double ivd0,double ivd1,double ivd2) {
		Complex z2;
		Mobius map; // normalize: z0 at origin or at -1, z1 on + real axis
		Mobius remap; // return to original
		double R2=.20;

		// (1) Most Common: all finite. Assume first at origin, 
		//     second on pos x-axis; compute angle at first, 
		//     dist from origin of third to get normalized center,
		//     then apply remap to carry first/second to original 
		//     positions.
		if (x0>0 && x1>0 && x2>0) {
			map=Mobius.standard_mob(z0, z1);
			remap=(Mobius)map.inverse();
			double ch0=h_ivd_cosh(x0,x1,ivd0);
			double ch1=h_ivd_cosh(x1,x2,ivd1);
			double ch2=h_ivd_cosh(x2,x0,ivd2);
			// compute angle at first by law of cosines: 
			//    recall sinh^2=cosh^2-1; see 'h_comp_cos'
			double cosang=(ch2 * ch0 - ch1) / 
					Math.sqrt((ch2 * ch2 - 1.0) * (ch0 * ch0 - 1.0));
			double sinang=Math.sqrt(1.0-cosang*cosang);
			double len01=h_ivd_length(x0,x2,ivd2);
			double exph=Math.exp(-len01);
			R2=(1-exph)/(1+exph);
			Complex z=new Complex(R2*cosang,R2*sinang);
			z2=remap.apply(z);
			return new CircleSimple(z2, x2, 1);
		}

		CircleSimple csIn;
		CircleSimple csOut=new CircleSimple(); // instantiate
		
		// First and second finite, third horocycle
		if (x0>0 && x1>0 && x2<0) {
			map=Mobius.standard_mob(z0, z1); // map.apply(z1);
			remap=(Mobius)map.inverse();
			double R0=(2-x0 - 2*Math.sqrt(1-x0)) / x0; // eucl radius at origin
			double len0=h_ivd_length(x0,x1,ivd0);
			double s=Math.exp(-len0);
			double edist=(1-s)/(1+s);
			Complex hypz=new Complex(edist); // absolute value of hyp center
			double R1=h_to_e_data(new CircleSimple(hypz,x1)).rad;
			R2=(1.0-R0*R0)/(2.0+2.0*R0*ivd2); // eucl rad of third circle
			double cosang=EuclMath.e_cos_overlap(R0, R1, R2,ivd0,ivd1,ivd2);
			double sinang=Math.sqrt(1.0-cosang*cosang);
			Complex z=new Complex(cosang,sinang); // z.abs();
			csIn=new CircleSimple(z,-R2);
			Mobius.mobius_of_circle(remap, -1, csIn, csOut, true);
			return csOut;
		}
		
		// first two are horocycles
		if (x0<0 && x1<0) { // first and second are horocycles
			return h_horo_center(z0,z1,x0,x1,x2,ivd0,ivd1,ivd2);
		}
		
		int flag=1; // 1=normal; -1, interchange first/second;
		
		// (3) first horocycle, second finite; we interchange them 
		if (x0<0 && x1>0) {
			Complex holdz=z1;
			z1=z0;
			z0= holdz;
			double hold = x0;
			x0 = x1;
			x1 = hold;
			hold = ivd1;
			ivd2 = ivd1;
			ivd1 = hold;
			flag=-1;
		}
		
		// First is now finite, second is horocycle, third may be
		//   finite or infinite. Convert to eucl situation. 
		// HyperbolicMath.x_to_h_rad(x2);
		double r0=(2-x0 - 2*Math.sqrt(1-x0)) / x0; // eucl radius at origin
		double R1=(1-r0*r0)/(2.0+2.0*r0*ivd0); // eucl rad of horocycle
			
		// compute 
		double len2=0.0; // need hyp length later if third is finite
			
		// if third is horocycle
		if (x2<0) {
			R2=(1-r0*r0)/(2.0+2.0*r0*ivd2); // eucl rad of third
		}
		else { // third is finite, need to find its eucl radius
			len2=h_ivd_length(x0,x2,ivd2); 
			double s=Math.exp(-len2);
			double edist=(1-s)/(1+s);
			Complex hypz=new Complex(edist); // absolute value of hyp center
			// now position on positive x-axis just to find eucl radius R2
			CircleSimple cSe=h_to_e_data(new CircleSimple(hypz,x2));
			R2=cSe.rad; 
		}

		double cosang=EuclMath.e_cos_overlap(r0,R1,R2,ivd0,ivd1,ivd2);
		double sinang=Math.sqrt(1.0-cosang*cosang);
		z2=new Complex(cosang,sinang); // center's direction
			
		// Finish up
		if (x2>0) { // third is finite
			double s=Math.exp(-len2); // convert len2 to eucl distance 
			double edist=(1-s)/(1+s);
			z2=z2.times(edist); // hyp center
			if (flag==-1)
				z2=z2.conj();
			csIn=new CircleSimple(z2,x2);
		}
		else {
			if (flag==-1)
				z2=z2.conj();
			csIn=new CircleSimple(z2,(-1.0)*R2);
		}
		map=Mobius.standard_mob(z0, z1);
		remap=(Mobius)map.inverse();
		Mobius.mobius_of_circle(remap, -1, csIn, csOut, true);
		return csOut;
	}
	
	/**
	 * Given 2 hyp centers/x-radii and x-radius of third, return third as
	 * CircleSimple. (Note: third circle has radius the negative of 
	 * euclidean radius if it is a horocycle). ivdj=inv dist of edge 
	 * <j,j+1>. No consistency check on first two circles 
	 * or incompatiblities. 'iflag' true reflects incompatibilities 
	 * (but not yet passed back).
	 * @param z0      Complex
	 * @param z1      Complex
	 * @param x0      double
	 * @param x1      double
	 * @param x2      double
	 * @param ivd0    double
	 * @param ivd1    double
	 * @param ivd2    double
	 * @return CircleSimple
	 * 
	 */
	public static CircleSimple old_h_compcenter(Complex z0, Complex z1, 
			double x0,double x1,double x2, 
			double ivd0,double ivd1,double ivd2) {
		CircleSimple sici;

		double s0 = HyperbolicMath.x_to_s_rad(x0);
		double s1 = HyperbolicMath.x_to_s_rad(x1);
		double s2 = HyperbolicMath.x_to_s_rad(x2);

		// If any eucl radii are invalid, return with an error
		if (s0 <= (-1.0) || s1 <= (-1.0) || s2<=(-1.0))
			throw new DataException("error computing center: "+
					"horocycle eucl rad <= -1.0");

		Complex a = z0;
		Complex b = z1;
		double sgn = 1; // signal orientation
		if ((s0 <= 0) && (s1 > 0)) { // second circle finite, first not?
			// then interchange order
			a = z1;
			b = z0;
			double hold = s0;
			s0 = s1;
			s1 = hold;
			hold = x0;
			x0 = x1;
			x1 = hold;
			hold = ivd1;
			ivd2 = ivd1;
			ivd1 = hold;
			sgn = (-1.0); // indicate orientation reversal
		}
		if (s0 > 0) { // first is now finite
			double cc = h_comp_cos(x0, x1, x2, ivd0, ivd1, ivd2);

			// if third circle is finite
			if (s2 > 0) {
				double x13 = x0 * (x2);
				double x1p3 = x0 + (x2);
				double s13 = s0 * s2;
				double acstuff = (x1p3 - x13) / 
						(s13 * (1 + s13)) - (2 * x1p3 - (1 + ivd1) * x13) / 
						(4 * s13);
				if (acstuff < 0.0) // error
					return new CircleSimple(false);
				double side_p1 = acstuff + Math.sqrt(acstuff * (acstuff + 2));

				double ahc = side_p1 / (side_p1 + 2); // abs value of hyp center
				// center as if z0 at origin
				Complex z3 = new Complex(cc * ahc, sgn * 
						Math.sqrt(1 - cc * cc) * ahc);

				if (a.x == b.x && a.y == b.y)
					z3.x *= ahc;

				z3 = Mobius.mobDiscInvValue(z3, a, b); // move to right place
				return new CircleSimple(z3, x2, 1);
			}

			// else third circle is a horocycle

			double r = (1 - s0) / (1 + s0);
			double sc = (r * r + 1 + 2 * r * ivd1) / 
					(2 * (1 + r * ivd1)); // abs value of eucl center
			Complex c = new Complex();
			c.x = sc * cc;
			double cc2;
			if ((cc2 = cc * cc) > 1.0) // error
				return new CircleSimple(false);
			c.y = sc * sgn * Math.sqrt(1 - cc2);
			double rad = 1 - sc; // Now have c and its radius
			Complex w0 = new Complex(c.x - rad, c.y);
			Complex w1 = new Complex(c.x + rad, c.y);
			Complex w2 = new Complex(c.x, c.y + rad); // 3 points on the circle
			w0 = Mobius.mobDiscInvValue(w0, a, b);
			w1 = Mobius.mobDiscInvValue(w1, a, b);
			w2 = Mobius.mobDiscInvValue(w2, a, b); // 3 pts on new circle
			sici = EuclMath.circle_3(w0, w1, w2);
			sici.rad *= -1.0; // store neg of eucl radius as x_rad
			// get hyp center on unit circle
			sici.center = sici.center.times(1.0 / (sici.center.abs())); 
		}

		// first two horocycles, third finite (Note: generally prefer 
		//    to avoid this situation)
		else if (s2 > 0) {
			// Idea: build triple with circle 3 centered at origin, 
			//   then apply a Mobius to get its euclidean radius and 
			//   tangency points right to original values, see where 
			//   circle 3 ends up
			// here get eucl rad of circle 3 if centered at origin
			double erad = (1.0 - s2) / (1.0 + s2); 
			// Compute eucl radii of horocycles with correct inv dist 
			//   to circle 3 (at origin)
			double hororad1 = (1.0 - erad * erad) / (2.0 * (1 + erad * ivd1)); 
			double hororad2 = (1.0 - erad * erad) / (2.0 * (1 + erad * ivd2)); 

			// Assume circle 1 tangent at z=1, compute tangency 
			//    point of circle 2.
			double dist12 = EuclMath.e_ivd_length(hororad1, hororad2, ivd1);
			double d1 = 1.0 - hororad1;
			double d2 = 1.0 - hororad2;
			double theta = Math.acos((d1 * d1 + d2 * d2 - dist12 * dist12) / 
					(2.0 * d1 * d2));

			// Mobius that fixes +-1 and moves d1=1-2*hororad1 to 1+2*s1 
			//   (recalling that s1 is euclidean rad with negative sign). 
			//   Mobius is form (z-t)/(1-tz), and since d1 gets carried 
			//   to d2, can compute t. Apply it to origin to see where 
			//   hyp center of circle 3 is and to tangency point of circle 2.
			d1 = 1.0 - 2.0 * hororad1;
			d2 = 1.0 + 2.0 * s0;
			double t = (d2 - d1) / (d2 * d1 - 1.0);
			Mobius m1 = new Mobius(new Complex(1.0), new Complex(-t), 
					new Complex(-t), new Complex(1.0));
			Complex newZ = m1.apply(new Complex(0.0));
			theta = m1.apply(new Complex(0.0, theta).exp()).arg();

			// Next want parabolic fixing 1 and mapping tangency point 
			//   of circle 2 so it makes original angle between the 
			//   original horocycles. Use map (z+1)/(z-1). This maps 
			//   disc to left half plane, is own inverse. The parabolics 
			//   are shift in halfplane by t in imaginary direction.
			//   Mobius is ((2+it)z-it)/(itz+(2-it)).
			//   Under this map, exp{a} goes to -sin(a)/(1-cos(a)) time i. 
			//   So want t to equal to difference between old image and new.
			double origTheta = z1.divide(z0).arg();
			t = Math.sin(theta) / (1.0 - Math.cos(theta))- Math.sin(origTheta)/
					(1.0 - Math.cos(origTheta));
			Mobius m2 = new Mobius(new Complex(2.0, t), new Complex(0.0, -t), 
					new Complex(0.0, t),new Complex(2.0, -t));
			Complex nextZ = m2.apply(newZ);

			// Final step is to rotate to put tangency point of circle 1 
			//   where it was originally
			sici = new CircleSimple(true);
			sici.center = nextZ.times(z0); // recall z1 should be on unit circle
			sici.rad = x2;
		}

		// remaining case: all three are horocycles. Expect -eucl 
		//   radius as s-rad of target circle.
		else {
			try {
				sici = old_h_horo_center(z0, z1, -s1, ivd0, ivd1, ivd2);
			} catch (MobException mex) {
				throw new MobException("error computing horocycle center");
			}
		}

		return sici;
	}

	/**
	 * Compute a the hyperbolic circle tangent to two given circles.
	 * @param z0
	 * @param z1
	 * @param x0
	 * @param x1
	 * @param x2
	 * @return CircleSimple
	 */
	public static CircleSimple h_compcenter(Complex z0, Complex z1, 
			double x0, double x1, double x2) {
		return h_compcenter(z0, z1, x0, x1, x2, 1.0, 1.0, 1.0);
	}

	/**
	 * Compute the hyperbolic distance in Poincare metric from z to w. 
	 * General formula inside disc is log((a+d)/(a-d)), where 
	 * d = | z - w |, a = | 1 - zwb	 * |, with zwb = z*cconj(w).
	 * 
	 * @param z Complex
	 * @param w Complex
	 * @return double: 0 if z==w (within small tolerance); 
	 * 		return negative of eucl distance if one or both of z,w 
	 * 		are outside and/or within small tolerance of unit circle.
	 */
	public static double h_dist(Complex z, Complex w) {
		double d;

		if ((d = z.minus(w).abs()) < .0000000000001)
			return 0.0;
		if (z.abs() > .999999999999 || w.abs() > .999999999999)
			return -d;
		Complex zwb1 = z.times(w.conj());
		zwb1.x = 1.0 - zwb1.x;
		zwb1.y *= -1;
		double a = zwb1.abs();
		return (Math.log((a + d) / (a - d)));
	}

	/**
	 * Return cosh(len), 'len' = hyp length of edge between 
	 * centers of circles with x-radii x1, x2, and inv distance 
	 * 'ivd'. Return -1 if one or both radii negative (horocycle, 
	 * so distance is infinite).
	 * 
	 * Original formula for hyperbolic radii h1, h2 is: 
	 *   cosh(len) = cosh(h1)*cosh(h2)+sinh(h1)*sinh(h2)*ivd
	 * In terms of x-radii x1, e.g., 
	 *   cosh(h1)=(2-x1)/(2*sqrt(1-x1)) and sinh(h1)=x1/(2*sqrt(1-x1))
	 * 
	 * Notes: 
	 * 		if ivd>=1.0 (tangent/separation) then len = h1+h2+acosh(ivd)
	 * 		and	recall h=-log(1-x)/2;
	 * 
	 * @param x1  double
	 * @param x2  double
	 * @param ivd double
	 * @return double, cosh(length), -1 if infinite
	 */
	public static double h_ivd_cosh(double x1, double x2, double ivd) {
		if (x1 < 0 || x2 < 0)
			return -1.0;
		if (ivd>=1.0) { // tangency or separation, return cosh(len)
			double len=-0.5*(Math.log(1-x1)+Math.log(1-x2))+acosh(ivd);
			return Math.cosh(len);
		}

		// else overlap
		double num=(2.0 - x1) * (2.0 - x2) + x1 * x2 * ivd;
		double denom=4.0 * Math.sqrt((1 - x1) * (1 - x2));
		return num/denom;
	}

	/**
	 * Return hyp length of edge between heyp centers of 
	 * circles with x-radii x1, x2, and inversive distance 'ivd'. 
	 * Return -1 if length is infinite.
	 * @param x1 double
	 * @param x2 double
	 * @param ivd double
	 * @return double, -1 if length is infinite
	 */
	public static double h_ivd_length(double x1, double x2, double ivd) {
		if (x1<0 || x2<0)
			return -1;
		if (ivd>=1) {
			double h1=HyperbolicMath.x_to_h_rad(x1);
			double h2=HyperbolicMath.x_to_h_rad(x2);
			return h1+h2+HyperbolicMath.acosh(ivd);
		}
		double csh = h_ivd_cosh(x1, x2, ivd);
		return HyperbolicMath.acosh(csh);
	}

	/**
	 * This version of 'acosh' (arccosh) takes x >= 1 and returns 
	 * the branch with acosh(x)>=0
	 * 
	 * @param x double
	 * @return double arccosh
	 */
	public static double acosh(double x) {
		return Math.log(x + Math.sqrt(x * x - 1));
	}

	/**
	 * Given x-radius for hyp circle, return inversive distance 
	 * to unit circle. Recall x=1-exp(-2h) where h is the hyperbolic 
	 * radius. Inv dist is coth(h)=cosh(h)/sinh(h)
	 * 
	 * @param x double, hyperbolic x-radius
	 * @return double, inv distance = coth(h),
	 */
	public static double x_rad2invdist(double x) {
		if (x <= 0.0) // negative implies eucl radius of horocycle
			return 1.0;
		return ((2 / x) - 1.0); // (exp(h)+exp(-h))/(exp(h)-exp(-h));
	}

	/**
	 * Normalizes hyperbolic packing by putting 'a' at origin and 
	 * (if g is not essentially equal to a) rotating so 'g' is on 
	 * positive y-axis.
	 * 
	 * @param p PackDCEL
	 * @param a Complex (usually, a=alpha center)
	 * @param g Complex (usually, g=gamma center)
	 * @return int: 0=nothing done
	 */
	public static Mobius h_norm_pack(PackDCEL pdcel, Complex a, Complex g) {
		if (a.abs() > Mobius.MOD1)
			throw new DataException("'a' is too close to unit circle");
		Mobius mob = Mobius.mobNormDisc(a, g);
		if (Mobius.frobeniusNorm(mob) > Mobius.MOB_TOLER) {
			// directly adjust in 'Vertex'
			for (int v = 1; v <= pdcel.vertCount; v++) {
				Complex z = pdcel.vertices[v].center;
				Complex newz = mob.apply(z);
				pdcel.vertices[v].center = newz;

				// if horocycle, adjust radius as well
				double radius = pdcel.vertices[v].rad;
				if (radius < 0) {
					double newr = horoEuclRad(mob, z, -radius);
					pdcel.vertices[v].rad = -newr;
				}
			}
			// directly adjust in red chain
			if (pdcel.redChain != null) {
				RedEdge rtrace = pdcel.redChain;
				do {
					Complex z = rtrace.getCenter();
					Complex newz = mob.apply(z);
					rtrace.setCenter(newz);

					// if horocycle, adjust radius as well
					double radius = rtrace.getRadius();
					if (radius < 0) {
						double newr = horoEuclRad(mob, z, -radius);
						rtrace.setRadius(-newr);
					}

					rtrace = rtrace.nextRed;
				} while (rtrace != pdcel.redChain);
			}
		}
		return mob;
	}

	/**
	 * Given horocycle (center z, eucl radius rad) and Mobius 
	 * transformation of the disc, return eucl radius of the 
	 * image horocycle.
	 * 
	 * @param mob Mobius
	 * @param z   Complex
	 * @param rad double, positive
	 * @return double, new positive radius
	 */
	public static double horoEuclRad(Mobius mob, Complex z, double rad) {
		// get three pts on original horocycle
		Complex z0 = z.divide(z.abs());
		Complex z1 = z0.times(1.0 - 2.0 * rad);
		Complex e_ctr = z0.times(1.0 - rad);
		Complex perp = z0.times(rad).times(new Complex(0, 1));
		Complex z2 = e_ctr.add(perp);
		z0 = mob.apply(z0);
		z1 = mob.apply(z1);
		z2 = mob.apply(z2);
		CircleSimple sc = EuclMath.circle_3(z0, z1, z2);
		return sc.rad;
	}

	/**
	 * Find is point z is in the hyp triangle with corners p0,p1,p2. 
	 * Convert to Klein model so we can use eucl computation. Data 
	 * is given in usual hyp form (i.e., Poincare model).
	 * 
	 * @param z  Complex
	 * @param p0 Complex
	 * @param p1 Complex
	 * @param p2 Complex
	 * @return boolean
	 */
	public static boolean pt_in_hyp_tri(Complex z, Complex p0, 
			Complex p1, Complex p2) {
		Complex kz = z.divide(1.0 + z.absSq());
		Complex k0 = p0.divide(1.0 + p0.absSq());
		Complex k1 = p1.divide(1.0 + p1.absSq());
		Complex k2 = p2.divide(1.0 + p2.absSq());
		return EuclMath.pt_in_eucl_tri(kz, k0, k1, k2);
	}

	/**
	 * Given 'dist' in [0,1], return half-angle theta of arc of 
	 * unit circle subtended by geodesics having minimum eucl 
	 * distance 'dist' from the origin.
	 * 
	 * @param dist, double >= 0, <= 1
	 * @return Double half-angle, null on error
	 */
	public static Double shadow_angle(double dist) {
		if (dist < 0 || dist > 1.0)
			return null;
		return Double.valueOf(Math.atan((1 - dist * dist) / (2.0 * dist)));
	}

	/**
	 * Find angle-based barycentric coordinates for z wrt an 
	 * ideal hyperbolic triangle with ideal vertices z0, z1, z2 
	 * (counterclockwise). Since angles are conformally invariant, 
	 * compute by moving z to origin, find angles between the
	 * geodesics to the corners. NOTE: works for points in unit 
	 * disc but outside the ideal triangle; so coord(s) will be 
	 * negative.
	 * 
	 * NOTE: useful for locating points in interstices: the 
	 * tangency points of the circles forming a face are on the 
	 * boundary of the incircle, and the circles of the face are 
	 * geodesics relative to that circle.
	 *
	 * Coords are bj=1-(tj/pi) where tj is angle between 
	 * geodesics through z and z(j+1) and z and z(j+2).
	 * 
	 * TODO: don't have inverse of this function: go from bary 
	 * coords and corners to get the point.
	 * 
	 * @param z  Complex
	 * @param z0 Complex, on unit circle
	 * @param z1 Complex, on unit circle
	 * @param z2 Complex, on unit circle
	 * @return BaryPoint; null on error or if z not in interstice
	 */
	public static BaryPoint ideal_bary(Complex z, 
			Complex z0, Complex z1, Complex z2) {

		// check if zj are on unit circle, z is in unit disc
		if (Math.abs(1.0 - z0.abs()) > OKERR || 
				Math.abs(1.0 - z1.abs()) > OKERR || 
				Math.abs(1.0 - z2.abs()) > OKERR
				|| z.abs() > 1.0 - OKERR)
			throw new DataException("bad data: zj must be on unit circle"
					+ ", z in unit disc");

		Complex[] p = new Complex[3];
		Complex one = new Complex(1.0);
		// Mobius moving z to origin is p --> (p-z)/(1-conj(z)*p). 
		//    Apply this to zj
		p[0] = z0.minus(z).divide(one.minus(z.conj().times(z0)));
		p[1] = z1.minus(z).divide(one.minus(z.conj().times(z1)));
		p[2] = z2.minus(z).divide(one.minus(z.conj().times(z2)));

		double angle = p[2].divide(p[1]).arg();
		// should be positive (though coord b1 could come out negative)
		if (angle < 0) 
			angle += 2.0 * Math.PI;
		double b1 = (1.0 - angle / Math.PI);
		angle = p[0].divide(p[2]).arg();
		if (angle < 0) // should be positive
			angle += 2.0 * Math.PI;
		double b2 = (1.0 - angle / Math.PI);

// debug
// System.out.println("(b1,b2,b3) = ("+b1+","+b2+","+(1-b1-b2)+")");
		return new BaryPoint(b1, b2);
	}

	/**
	 * Given points z1,z2,z3 and z in the unit disc, find 
	 * barycentric coords of z relative to hyperbolic triangle 
	 * {z1,z2,z3}. For conversion the other way, see
	 * BaryPoint.bp2Complex.
	 * 
	 * @param z  Complex
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return BaryPoint
	 */
	public static BaryPoint h_pt_to_bary(Complex z, 
			Complex z0, Complex z1, Complex z2) {

		// check if all are in unit disc
		double max = -1.0;
		max = (z0.abs() > max) ? z0.abs() : max;
		max = (z1.abs() > max) ? z1.abs() : max;
		max = (z2.abs() > max) ? z2.abs() : max;
		if (max > 1.000001)
			throw new DataException("some point not in disc");

		// if one is essentially on unit circle, shrink all slightly
		if (max > .99999999) {
			z0 = z0.times(.999999);
			z1 = z1.times(.999999);
			z2 = z2.times(.999999);
		}

		// find points p0,p1,p2 on the hyperboloid t-(x1^2+x2^2) = 1
		Point3D p0 = pt2Hyperboloid(z0);
		Point3D p1 = pt2Hyperboloid(z1);
		Point3D p2 = pt2Hyperboloid(z2);

		// displacement vectors and normal to plane containing the points
		Point3D v01 = Point3D.displacement(p0, p1);
		Point3D v02 = Point3D.displacement(p0, p2);
		Point3D nrml = Point3D.CrossProduct(v01, v02);

		// check if triangle is degenerate
		if (nrml.norm() < .00001)
			throw new DataException("triangle close to degenerate");

		// project z to hyperboloid
		Point3D hyp_pt = pt2Hyperboloid(z);

		// project hyp_pt away from origin to the plane
		double s = Point3D.DotProduct(nrml, p0) / 
				Point3D.DotProduct(nrml, hyp_pt);
		Point3D pp0 = Point3D.scalarMult(hyp_pt, s);
		Point3D v10 = Point3D.displacement(p0, pp0);

		// now represent v10 as linear combination of v12 and v13; solve a
		// linear system: v10=a*v12+b*v13. Conside [v12 v13 v10] matrix and
		// row reduce.
		Point3D row1 = new Point3D(v01.x, v02.x, v10.x);
		Point3D row2 = new Point3D(v01.y, v02.y, v10.y);
		Point3D row3 = new Point3D(v01.z, v02.z, v10.z);
		Point3D hold;

		// interchange rows, if necessary, to get first pivot
		if (Math.abs(row1.x) < .0001) {
			hold = new Point3D(row1.x, row1.y, row1.z);
			if (Math.abs(row2.x) < .0001) {
				row1 = row3;
				row3 = hold;
			} else {
				row1 = row2;
				row2 = hold;
			}
		}

		// make pivot 1
		row1 = Point3D.scalarMult(row1, 1.0 / row1.x);

		// whip out below
		hold = Point3D.scalarMult(row1, -row2.x);
		row2 = Point3D.vectorSum(row2, hold);
		hold = Point3D.scalarMult(row1, -row3.x);
		row3 = Point3D.vectorSum(row3, hold);

		// interchange rows, if necessary, to get second pivot
		if (Math.abs(row2.y) < .0001) {
			hold = new Point3D(row2.x, row2.y, row2.z);
			row2 = row3;
			row3 = hold;
		}

		// make second pivot 1
		row2 = Point3D.scalarMult(row2, 1.0 / row2.y);

		// whip out entry above second pivot
		hold = Point3D.scalarMult(row2, -row1.y);
		row1 = Point3D.vectorSum(row1, hold);

		// read off solutions in 3rd column; these
		// give barycentric coords
		return new BaryPoint(1 - row1.z - row2.z, row1.z);
	}

	/**
	 * Given corners, convert 'BaryPoint' to complex value
	 * 
	 * @param bp BaryPoint
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return new Complex
	 */
	public static Complex bary_to_h_pt(BaryPoint bp, 
			Complex z0, Complex z1, Complex z2) {
		double max = -1.0;
		max = (z0.abs() > max) ? z0.abs() : max;
		max = (z1.abs() > max) ? z1.abs() : max;
		max = (z2.abs() > max) ? z2.abs() : max;
		if (max > 1.000001)
			throw new DataException("some point not in disc");

		// if one is essentially on unit circle, shrink all slightly
		if (max > .99999999) {
			z0 = z0.times(.999999);
			z1 = z1.times(.999999);
			z2 = z2.times(.999999);
		}

		// find points pt1,pt2,pt3 on the hyperboloid t-(x1^2+x2^2) = 1
		Point3D pt0 = HyperbolicMath.pt2Hyperboloid(z0);
		Point3D pt1 = HyperbolicMath.pt2Hyperboloid(z1);
		Point3D pt2 = HyperbolicMath.pt2Hyperboloid(z2);

		// find the point in space
		Point3D spt = EuclMath.getSpacePoint(bp, pt0, pt1, pt2);

		// project it toward the origin until it hits the paraboloid
		double s = Math.sqrt(spt.z * spt.z - spt.x * spt.x - spt.y * spt.y);
		Point3D hyp_pt = Point3D.scalarMult(spt, 1 / s);

		// project to the disc
		return HyperbolicMath.projPt2disc(hyp_pt);
	}

	/**
	 * Given two points in {z>=1.0} (typically on the hyperboloid), 
	 * find outer normal to plane containing them and (0,0,1).
	 * 
	 * @param p1 Point3D
	 * @param p2 Point3D
	 * @return Point3D
	 */
	public static Point3D outerNormal(Point3D p1, Point3D p2) {

		// replace by displacement vectors from (0,0,1)
		p1.z -= 1.0;
		p2.z -= 1.0;

		// both essentially at the origin? ambiguous, return 0 vector
		if (p1.norm() < .0000001 && p2.norm() < .0000001)
			return new Point3D(0.0, 0.0, 0.0);

		// one is essentially at the origin? vertical plane
		// if (x,y) is proj of vect from p1 to p2, then want (y,-x)
		// (rotated clockwise by pi/2).
		if (p1.norm() < .0000001 || p2.norm() < .0000001)
			return new Point3D(p2.y - p1.y, p1.x - p2.x, 0.0);

		// p1, p2 essentially the same? Say p1=(x,y,z), then
		// normal vector to p1 at origin in xy-plane is (-y,x,0),
		// so use cross product of latter with former.
		if (Point3D.displacement(p1, p2).norm() < .0000001) {
			return new Point3D(p1.x * p1.z, p1.y * p1.z, 
					p1.x * p1.x + p1.y * p1.y);
		}

		// typical situation?
		return Point3D.CrossProduct(p2, p1);
	}

	/**
	 * Converts a point w of the unit disc to a point on the 
	 * upper sheet of the hyperboloid t^2-(x1^2+x2^2) = 1. 
	 * To cover all possibilities, if w is on or outside the 
	 * unit circle we return point with z-coord set to -|w|.
	 * 
	 * @param w Complex
	 * @return Point3D (x1,x2,t) on paraboloid; if t<0, then 
	 * 		point is on or outside the unit circle
	 */
	public static Point3D pt2Hyperboloid(Complex w) {
		if (w.abs() >= 1.0)
			return new Point3D(w.x, w.y, -w.abs());
		double wabsSq = w.absSq();
		double t = (1.0 + wabsSq) / (1.0 - wabsSq);
		return new Point3D(w.x * (1 + t), w.y * (1 + t), t);
	}

	/**
	 * Convert 3D 'pt' on or above hyperboloid to unit disc.
	 * 
	 * @param pt Point3D
	 * @return Complex
	 */
	public static Complex projPt2disc(Point3D pt) {
		double t = Math.sqrt(1.0 + (pt.x * pt.x + pt.y * pt.y));
		double h = pt.z - t;
		if (pt.z < 0 || h < -.00001)
			throw new DataException("point below hyperboloid");

		return new Complex(pt.x / (1 + t), pt.y / (1 + t));
	}

}
