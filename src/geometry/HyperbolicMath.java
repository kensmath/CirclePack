package geometry;
import baryStuff.BaryPoint;
import complex.Complex;
import complex.MathComplex;
import exceptions.DataException;
import exceptions.LayoutException;
import exceptions.MobException;
import komplex.RedEdge;
import komplex.RedList;
import math.Mobius;
import math.Point3D;
import packing.PackData;
import util.RadIvdPacket;

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
 * 		s=exp(-h),   x = 1-s*s,   s=sqrt(1-x),  h = log(1/s)
 * 
 * * point with eucl distance r and hyp distance h from origin, then:
 * 	    h=log((1+r)/(1-r)),  r=(exp(h)-1)/(exp(h)+1),  s=(r-1)/(r+1) 
 *   and cosh(h)=(1+r*r)/(1-r*r)
 *   
 * * for a circle at the origin this means:
 * 		s = (r-1)/(r+1),  r = (s+1)/(s-1),  x = 4r/(r+1)^2, and
 * 		r=(2-x + 2sqrt(1-x)) / x
 * 
 * * Hyperbolic law of cosines: Given hyp side lengths l1, l2, l3, the
 *   angle a1 opposite side l1 is
 * 		a1 = acos( (cosh(l2)*cosh(l3) - cosh(l1)) / (sinh(l2)*sinh(l3)) )
 *   For infinite lengths, this is ambiguous, but can be resolved from
 *   radii if triangle is formed by circles. See 'h_cos_s_overlap' and
 *   'h_comp_x_cos'.
 *   
 *   
 * * hyp distance l between circles of radii h1, h2, and inv distance ivd: 
 *      cosh(l) = cosh(h1)*cosh(h2)+sinh(h1)*sinh(h2)*ivd
 *   length l infinite iff at least one of h1/h2 is infinite. 
 *   See 'h_invdist_length' and 'h_dist'
 *        
 * In addition to the disc, hyperbolic geometry has the "hyperboloid" 
 * model. Namely, consider the sheet S with t>0 of the hyperboloid t^2-(x1^2+x2^2)=1, 
 * or t=sqrt(1+(x1^2+x2^2)). (Note, this is restriction to z=0 of the model
 * for hyperbolic 3-space. See 'SphereLayout.java'.)
 * 
 * Point (t,x1,x2) of S is associated with point (u1,u2,0) of unit disc in the
 * plane lying on the line from (t,x1,x2) to (-1,0,0). 
 * 
 * So (t,x1,x2) <--> (u1,u2), where
 *    uj=xj/(1+t), j=1,2
 *    t=sqrt(1+(x1^2+x2^2))
 *    xj=2uj/(1-(u1^2+u2^2))=2uj/(1-||u||^2), j=1,2
 *    t=(1+||u||^2)/(1-||u||^2) with ||u||^2=u1^2+u2^2
 *    
 */
public class HyperbolicMath{
	
	public static final double OKERR=.000000001; // TODO: set more rationally

  /** 
   *  Gives the euclidean representation of a hyperbolic circle
   *  for display purposes.
   *  @param h_center Complex
   *  @param x_rad double
   *  @return CircleSimple
   */
  public static CircleSimple HyperbolicToEuclidean(Complex h_center, double x_rad){
    double s_rad;
    Complex e_center=null;

    if(x_rad < 0){ // negative means infinite hyperbolic radius; expect value to
    	           //   to be negative of eucl radius of horocycle.
      e_center=h_center.times(1.0+x_rad);
      double e_rad = - (x_rad);
      return new CircleSimple(e_center, e_rad,1);
    }
    else
      s_rad = Math.sqrt(1 - x_rad); // convert to s-rad; x=1-s^2

    double ahc = h_center.abs();
    if(ahc > 0.999999999999)
      ahc = 0.999999999999;

    double g = ((1 + ahc) / (1 - ahc));
    double a = g / s_rad;
    double d = (a - 1) / (a + 1);
    double b = g * s_rad;
    double k = (b - 1) / (b + 1);
    double e_rad = (d - k) * 0.5;
    double aec = (d + k) * 0.5;

    if(ahc < 0.0000000000001){ // round off to origin as center.
      e_center=new Complex(0.0);
    }
    else{
      b = aec / ahc;
      e_center=h_center.times(b);
    }
    return new CircleSimple(e_center, e_rad,1);
  }
  
  /**
   * Converts circle data from eucl to hyp. (Formerly 'e_to_h_data')
   * @param e_center Complex
   * @param e_rad double
   * @return CircleSimple, null circle doesn't lie in closed disc
   */ 
  public static CircleSimple EuclideanToHyperbolic(Complex e_center,double e_rad) {
	  CircleSimple h_circle=new CircleSimple(true);
	  double aec=MathComplex.abs(e_center);
	  double dist=aec+e_rad;
	  if (dist>1.000001)//0000001)
		  return null; // not in closed disc 
	  if ((.999999999999)<dist) { // horocycle 
		  h_circle.rad=-e_rad;
		  h_circle.center.x=e_center.x/aec;
		  h_circle.center.y=e_center.y/aec;
		  h_circle.flag=-1;
		  return h_circle;
	  }
	  double c2=aec*aec;
	  double r2=e_rad*e_rad;
	  if (aec<.0000000000001) { // circle at origin
		  h_circle.center.x=0;
		  h_circle.center.y=0;
	  }
	  else {
		  double b=Math.sqrt((1+2*aec+c2-r2)/(1-2*aec+c2-r2));
		  double ahc=(b-1)/(b+1);
		  b=ahc/aec;
		  h_circle.center.x=b*e_center.x;
		  h_circle.center.y=b*e_center.y;
	  }
	  h_circle.rad=s_to_x_rad(Math.sqrt((1-2*e_rad+r2-c2)/(1+2*e_rad+r2-c2)));
	  h_circle.flag=1;
	  return h_circle;
  }  

  /** 
   * temp (??) routine to convert s-radius to x-radius
   * @param s double
   * @return double 
   */
  public static double s_to_x_rad(double s) {
    if (s <= 0)
      return s;
    return (1.0 - s * s);
  }
  
  /** 
   * temp (??) routine to convert x-radius to s-radius. 
   * If x<=0, just return x; might be intentionally negative.
   * @param x double
   * @return double 
   */
  public static double x_to_s_rad(double x) {
	  if (x<=0.0) return x;
	  return (Math.sqrt(1.0-x));
  }
  
  /** 
   * Converts 'x-radius' (the way hyp radii are generally stored) to the
   * 'h-radius', actual hyp radius (the user-friendly form). x=1-exp(-2h)
   * (@see PackData.getRadius).
   * @param x double, x-radius
   * @return double, h-radius; if x<=0, return x itself
   */
  public static double x_to_h_rad(double x) {
	  if (x > 0.0) {
		  if (x>.0001) 
	    	  return ((-0.5)*Math.log(1.0-x));
	      else 
	    	  return (x*(1.0+x*(0.5+x/3))/2);
	  }
	  return x;
  }
  
  /** 
   * Converts circle data from hyp to eucl. Note: if h_center is
   * the origin, then 
   *     e_rad=(1-s_rad)/(1+s_rad), s_rad=(1+e_rad)/(1-e_rad),
   * where s_rad = exp(-h_rad).
   * Numerics can cause problems: if x_rad < 0, assume this is a horocycle, so
   * eucl radius is -x_rad and center is in direction of h_center (or on pos
   * real axis if h_center=0.0.)
   * @param h_center Complex
   * @param x_rad double (x-radii are our usual internal storage for hyp radii)
   * @return CircleSimple 
  */
  public static CircleSimple h_to_e_data(Complex h_center,double x_rad) {
    double e_rad;
    Complex e_center=new Complex(0.0);
    
    // horocycle?
    if (x_rad<0.0) {
    	int warning_flag=1;
    	e_rad=-x_rad;
    	if (e_rad>=1.0) {
    		warning_flag=-1;
    		e_rad=1.0-OKERR;
    	}
    	double habs=h_center.abs();
    	if (habs>OKERR)
    		e_center=h_center.divide(habs).times(1-e_rad);
    	else {
    		warning_flag=-1;
    		e_center=new Complex(1.0).times(1-e_rad);
    	}

    	return new CircleSimple(e_center,e_rad,warning_flag);
    }

    // TODO: fixup: temp conversion 
    double s_rad=x_to_s_rad(x_rad);
    if (s_rad<=0) { // inf hyp radius (usually is negative of eucl radius)
        e_center=h_center.times(1.0+s_rad);
        e_rad=(-s_rad); // assumes -s_rad is meaningful
        return new CircleSimple(e_center,e_rad,1);
      }
    
    double ahc=h_center.abs();
    
    // new code using x-radii and other information 
    double n1 = (1+s_rad)*(1+s_rad);
    double n2 = n1 - ahc*ahc*x_rad*x_rad/n1;
    e_rad = Math.abs(x_rad*(1-ahc*ahc)/n2); // can be <0 due to num error 
    double b = 4*s_rad/n2;
    e_center=new Complex(b*h_center.x,b*h_center.y);
    return new CircleSimple(e_center,e_rad,1);
    
/*    // revert to old code
    if (ahc > .999999999999)  // almost horocycle? avoid error. 
      ahc = .999999999999;
    g=((1+ahc)/(1-ahc));
    a=g/s_rad;
    d=(a-1)/(a+1);
    b=g*s_rad;
    k=(b-1)/(b+1);
    e_rad = (d-k)*0.5;
    aec=(d+k)*0.5;
    if (ahc<.0000000000001) {
        e_center.x=0;
        e_center.y=0;
      }
    else {
        b=aec/ahc;
        e_center.x = b*h_center.x;
        e_center.y=b*h_center.y;
      } 
    return new CircleSimple(e_center,e_rad,1);

    /* old code
    if (ahc > .999999999999)  % almost horocycle? avoid error. 
      ahc = .999999999999;
    g=((1+ahc)/(1-ahc));
    a=g/s_rad;
    d=(a-1)/(a+1);
    b=g*s_rad;
    k=(b-1)/(b+1);
    *e_rad = (d-k)*0.5;
    aec=(d+k)*0.5;
    if (ahc<.0000000000001)
      {
        e_center->re=0;
        e_center->im=0;
      }
    else
      {
        b=aec/ahc;
        e_center->re = b*h_center.x;
        e_center->im=b*h_center.y;
      } */
  } 

  /** 
   * Converts circle data from eucl to hyp, with x-radius.
   * (If result is horocycle, stored radius is negative of the
   * euclidean radius --- in particular, this is not conformally
   * invariant, so depends on the center too.) 
   * @param e_center Complex
   * @param e_rad double
   * @return CircleSimple (flag, center, radius)
  */
  public static CircleSimple e_to_h_data(Complex e_center,double e_rad) {
    double x_rad;
    Complex h_center=null;
    int flag=1;
    
    double aec=e_center.abs();
    double dist=aec+e_rad;
    if (dist>(1.000000000001)) { // not in closed disc; push in to form horocycle
    	aec /= dist;
    	e_rad /=dist;
    	dist=1.0;
    	flag=0;
    }
    if (dist > 0.999999999999) { // horocycle 
        x_rad=(-e_rad);
        h_center=e_center.times(1/aec);
        return new CircleSimple(h_center,x_rad,flag);
    }
    double c2=aec*aec;
    double r2=e_rad*e_rad;
    if (aec<.0000000000001) { // circle at origin 
        h_center=new Complex(0.0);
    }
    else {
        double b=Math.sqrt((1+2*aec+c2-r2)/(1-2*aec+c2-r2));
        double ahc=(b-1)/(b+1);
        b=ahc/aec;
        h_center=e_center.times(b);
    }
    x_rad=s_to_x_rad(Math.sqrt((1-2*e_rad+r2-c2)/(1+2*e_rad+r2-c2)));
    return new CircleSimple(h_center,x_rad,flag);
  }

  /** 
   * Return the eucl radius of the horocycle which overlaps a circle 
   * at origin having s-radius >0 given the cosine of the overlap angle.
   * @param s1 double, s-radius of circle at origin
   * @param ovlp double, cosine of overlap angle. 
   * @return double
  */
  public static double h_horo_rad(double s1,double ovlp) {
  	double r=(1-s1)/(1+s1);
  	return (1-r*r)/(2.0+2*r*ovlp);
  }


  /** 
   * Given ordered triple of x-radii, compute cosine(angle)
   * at first circle in triangle formed by mutually tangent 
   * circles. (If there are inv distances, see 'h_comp_cos'.)
   * @param x1 double
   * @param x2 double
   * @param x3 double
   * @return double, cos of angle
  */
  public static double h_comp_x_cos(double x1,double x2,double x3) {
	  if (x1<=0) 
		  return (1.0);
	  if ((x2<=0) && (x3<=0)) 
		  return (2.0*x1-1);
	  if (x2<=0) {
		  double tmp = x3-x1*x3;
		  return ((x1-tmp)/(x1+tmp));
	  }
	  if (x3<=0) {
		  double tmp = x2-x1*x2;
		  return ((x1-tmp)/(x1+tmp));
	  }
	  double ans=x1 * (x1+(1.0-x1)*(x2+x3-x2*x3)) / ((x1+x3-x1*x3)*(x1+x2-x1*x2));
	  ans=2.0*ans-1.0;
	  if (ans>1.0) return 0.9999999999999;
	  if (ans<-1.0) return -0.9999999999999;
	  return (ans);
  }

	/**
	 * Given three x-radii and inv distances for opposite sides, compute
	 * up.value=cos(angle at e1). 
	 * @param x1 double
	 * @param x2  double
	 * @param x3  double
	 * @return double cos(angle)
	 */
	public static double h_comp_cos(double x1, double x2, double x3) {
		return h_comp_cos(x1,x2,x3,1.0,1.0,1.0);
	}

	/**
	 * Given three x-radii and inv distances for opposite sides, compute
	 * up.value=cos(angle at e1). 
	 * @param x1 double
	 * @param x2  double
	 * @param x3  double
	 * @param t1  double
	 * @param t2  double
	 * @param t3  double
	 * @return double cos(angle)
	 */
	public static double h_comp_cos(double x1, double x2, double x3,
			double t1, double t2, double t3) {
		
		// all tangencies? call other routine
		if ((t1 == 1) && (t2 == 1) && (t3 == 1)) {
			return h_comp_x_cos(x1, x2, x3);
		}
		
		// all radii finite? find edge lengths, use cosine law
		if (x1>0 && x2>0 && x3>0) {
			double ch=h_ivd_cosh(x2,x3,t1); // cosh's of edge lengths
			double ch3=h_ivd_cosh(x1,x3,t2);
			double ch2=h_ivd_cosh(x1,x2,t3);
			// use law of cosines: recall sinh^2=cosh^2-1
			return (ch3*ch2 - ch) / Math.sqrt((ch3*ch3-1.0)*(ch2*ch2-1.0));
		}

		// horocycle at vertex 1? angle is zero.
		if (x1 <= 0) {
			return 1.0;
		}

		// recall x=1-s*s
		double s1 = x_to_s_rad(x1);
		double s2 = x_to_s_rad(x2);
		double s3 = x_to_s_rad(x3);
		
		// Remaining cases must involve horocycle(s) and inv distance(s).
		// We compute using euclidean data; wolog, center first at origin.
		double e1 = (1.0-s1)/(1.0+s1); // eucl radius, center at origin
		double e2;
		double e3;

		// Now find the petal radii. Wolog, center petal circle on x axis.
		//   Consider outer point p where petal circle intersects 
		//   the x axis. We can find the eucl distance E and hyp
		//   distance H from p to origin and use these to solve for 
		//   e2 (or e3). 


		// find radius e2 for second circle
		double ivd=t3;
		if (s2 <= 0) { 	// horocycle? So E=1
			if (ivd == 1) { // tangency: e1+2*e2=1
				e2 = 0.5*(1.0-e1);
			}
			// else we compute eucl dist d between centers, 
			//   then use d+e2=1 to find e2.
			else { 
				e2 = (1.0-e1*e1)/2*(e1*ivd+1.0);
			}
		} 
		else { // else regular circle; compare eucl/hyp dist to M
			if (ivd == 1) { // tangency
				// E=e1+2*e2 <==> H=h1+2*h2, so E=(1-exp(-H))/(1+exp(-H))
				//   and exp(-H)=s1*(s2^2)
				double s122=s1*s2*s2;
				e2 = 0.5*((1-s122)/(1+s122)-e1);
			} 
			else {
				// E=sqrt(e1^2+e2^2+2*e1*e2*inv)+e2 and H=h_dist+h2
				// E=(1-exp(-H))/(1+exp(-H))
				double ch2=h_ivd_cosh(x1,x2,ivd);
				double lch2=ch2+Math.sqrt(ch2*ch2-1.0);
				double E=(lch2-s2)/(lch2+s2);
				e2=(E*E-e1*e1)/(2.0*(e1*ivd+E));
			}
		}

		// Similar for second petal
		ivd=t2;
		if (s3 <= 0) { 	// horocycle? So E=1
			if (ivd == 1) { // tangency: e1+2*e2=1
				e3 = 0.5*(1.0-e1);
			}
			// else we compute eucl dist d between centers, 
			//   then use d+e2=1 to find e2.
			else { 
				e3 = (1.0-e1*e1)/2*(e1*ivd+1.0);
			}
		} 
		else { // else regular circle; compare eucl/hyp dist to M
			if (ivd == 1) { // tangency
				// E=e1+2*e3 <==> H=h1+2*h3, so E=(1-exp(-H))/(1+exp(-H))
				//   and exp(-H)=s1*(s3^2)
				double s133=s1*s3*s3;
				e3 = 0.5*((1-s133)/(1+s133)-e1);
			} 
			else {
				// E=sqrt(e1^2+e2^2+2*e1*e2*inv)+e2 and H=h_dist+h2
				// E=(1-exp(-H))/(1+exp(-H))
				double ch3=h_ivd_cosh(x1,x3,ivd);
				double lch3=ch3+Math.sqrt(ch3*ch3-1.0);
				double E=(lch3-s2)/(lch3+s2);
				e3=(E*E-e1*e1)/(2.0*(e1*ivd+E));
			}
		}

		// Now compute with the euclidean radii
		return EuclMath.e_cos_overlap(e1, e2, e3, t1, t2, t3);
	}

	/** 
   * TODO: some confusion on what this does and whether there's an error.
   * All three circles are horocycles, so centers z1, z2 should be on 
   * unit circle. Idea is to compute data for third circle. Third center
   * should be on unit circle, radius returned should be the negative of its
   * euclidean radius. e2 is positive euclidean radius of circle 2. (??) 
   * Inversive distances passed as usual (oj = inv_dist between edge opposite j). 
   * @param z1 Complex
   * @param z2 Complex
   * @param e2 double
   * @param o1 double
   * @param o2 double
   * @param o3 double
   * @return CircleSimple
   * 
  */
  public static CircleSimple h_horo_center(Complex z1,Complex z2,double e2,
	  double o1,double o2,double o3) throws MobException {

	  // Is one of z1 or z2 too far from unit circle to be accurate?
      if ((z1.x*z1.x+z1.y*z1.y)<(0.999999999999) 
    		  || (z2.x*z2.x+z2.y*z2.y)<(0.999999999999)) {
    	  return (new CircleSimple(false)); 
      }
      
/* Don't follow some of this: third circle is supposed to be a horocycle,
so s-rad should be zero. Let's assume that (don't need x_rad3, which
used to be passed in here as an argument).  
        double S3=HyperbolicMath.x_to_s_rad(x_rad3);

        if (S3<=0) srad2=0.0;
        else srad2=(S3)*(S3);
*/	
      /* first, compute eucl center/rad of third circle in a normalized 
       * situation: namely first horo at -1 with eucl rad R (computed below), 
       * second horo at 1, eucl rad 1/2, target circle in upper half plane.
       * We compute r/ectr as eucl rad/cent of target circle in this situation. */

      double srad2=0.0;
      double R=2/(o3+3.0);
      double d=Math.sqrt(0.25 + R*R+R*o3);
      double r=(2.0*(d+R*o3)+1.0)*(1.0-srad2)/
        (8.0*d*(1.0+srad2)+((2.0-4.0*d)*o1 - 4.0*R*o2)*(srad2-1.0));
      Complex ectr=new Complex();
      ectr.x=0.5*(1-d)-(1.0/(2.0*d))*(0.25+r*o1-R*R-2.0*R*r*o2);
      double a_sq=0.25+r*(r+o1);
      ectr.y=Math.sqrt(a_sq-(ectr.x-0.5)*(ectr.x-0.5));

      /* Next, find normalizing mobius transformations: M1 moves original
       * centers on unit circle to -1/1, resp., while M2 fixes -1/1 and 
       * causes second circle to go through the origin. */

      // find 3 pts on the original second circle (this is were e2 is used).
      Complex ecent=new Complex((1.0-e2)*z2.x,(1.0-e2)*z2.y);
      Complex p1=new Complex(ecent.x,ecent.y+e2);
      Complex p2=new Complex(ecent.y,ecent.x+e2);
      Complex p3=new Complex(ecent.y,ecent.x-e2);
      
      Complex One=new Complex(1.0);
      Complex negOne=new Complex(-1.0);
      Complex Two=new Complex(2.0);
      Mobius M1=(Mobius)Mobius.trans_abAB(z1,z2,negOne,One,Two,Two);
      /* if there's some error, err_flag will be set (though
         we don't currently test it) and M1 should be identity */
      /* put 'Two' arguments in to signal that we accept one
         unspecified degree of freedom; we handle it with work below.*/

      Complex w1=M1.apply(p1);
      Complex w2=M1.apply(p2);
      Complex w3=M1.apply(p3);
      CircleSimple tc=EuclMath.circle_3(w1,w2,w3); // find new eucl rad
      double rad=tc.rad;
      double pp=(1.0-2*rad); // pp is the spot where M1(c2) hits real axis
      // M2(z)->(z-pp)/(-z*pp+1) moves pp to origin.
      Mobius M2=new Mobius(new Complex(1.0,0.0),new Complex(-pp,0.0),new Complex(-pp,0.0),
    		  new Complex(1.0,0.0));
      w1=M2.apply(w1);
      w2=M2.apply(w2);
      w3=M2.apply(w3);
      tc=EuclMath.circle_3(w1,w2,w3); // find new eucl rad
      rad=tc.rad;

      // pick three pts on normalized circle three
      double h=ectr.abs();
      if (h>.0000000000001) { // typical: not centered at origin
		p1=ectr.times((h+r)/h);
		p2=ectr.times((h-r)/h);
		p3=new Complex(ectr);p3.x += r;
      }
      else { 
			p1=new Complex(ectr);p1.x -= r;
			p2=new Complex(ectr);p2.x += r;
			p3=new Complex(ectr);p3.y -= r;
      }
	
      // apply inverses of first M2 and then M1.
      Mobius m1=(Mobius)M1.inverse();
      Mobius m2=(Mobius)M2.inverse();
      p1=m2.apply(p1);
      p2=m2.apply(p2);
      p3=m2.apply(p3);
      p1=m1.apply(p1);
      p2=m1.apply(p2);
      p3=m1.apply(p3);

      // images under mobius 
      tc=EuclMath.circle_3(p1,p2,p3); // find new eucl cent/rad
      rad=tc.rad;
      // p1 should be on the unit circle
      return new CircleSimple(p1,-rad,1);
/* ?? Don't know what to make of this old stuff
      if (S3<=.0000000000001) { // horocycle? p1 should be on unit circle 
    	  return new CircleSimple(p1,-rad,1);
      }
      return EuclideanToHyperbolic(z3,x_rad3);
*/      
  }
  
  /** 
   * Compute area of the hyperbolic triangle formed by circles
   * with given x-radii and inversive distances (for opposite size).
   * @param riP RadIvdPacket
   * @return double
   */
  public static double h_area(RadIvdPacket riP) {
	  double[] ang=new double[3];
	  double[] cosang=new double[3];
	  for (int j=0;j<3;j++)
		  cosang[j]=h_comp_cos(riP.rad[j],riP.rad[(j+1)%3],riP.rad[(j+2)%3],
				  riP.oivd[j],riP.oivd[(j+1)%3],riP.oivd[(j+2)%3]);
	  return (Math.PI-Math.acos(cosang[0])-
			  Math.acos(cosang[1])-Math.acos(cosang[2]));
  }
  
  /**
   * Find "incircle", hyp center/radius of circle inscribed in 
   * trianglular face with given corners. ASSUME the three are 
   * mutually tangent; we convert to eucl circles and apply eucl
   * algorithm.
   * TODO: should also handle non-tangency cases -- e.g., just get
   * incircle of arbitrary hyp triangle.
   * @param z1 Complex
   * @param z2 Complex
   * @param z3 Complex
   * @param r1 double (hyp x-radius)
   * @param r2 double (hyp x-radius)
   * @param r3 double (hyp x-radius)
   * @return CircleSimple
   */
	public static CircleSimple hyp_tang_incircle(Complex z1,Complex z2,Complex z3,
			double r1,double r2,double r3) {
		CircleSimple sC1=h_to_e_data(z1,r1);
		CircleSimple sC2=h_to_e_data(z2,r2);
		CircleSimple sC3=h_to_e_data(z3,r3);
		CircleSimple sc=EuclMath.eucl_tri_incircle(sC1.center,sC2.center,sC3.center);
		return e_to_h_data(sc.center,sc.rad);
	}
	
	/**
	 * Get inscribed circle for any hyp triangle. 
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return CircleSimple
	 */
	public static CircleSimple hyp_tri_incircle(Complex z1,Complex z2,Complex z3) {
		Complex w1=null;
		Complex w2=null;
		Complex w3=null;
		
		// if there is a non-horocycle, we will move it to the origin
		if (z1.absSq()<.9999) {
			w1=z1;
			w2=z2;
			w3=z3;
		}
		else z1=z1.divide(z1.abs()); 
		if (w1==null && z2.absSq()<.9999) {
			w1=z2;
			w2=z3;
			w3=z1;
		}
		else if (w1==null) 
			z2=z2.divide(z2.abs());
		if (w1==null && z3.absSq()<.9999) {
			w1=z3;
			w2=z1;
			w3=z2;
		}
		
		// all essentially horocycles? Apply mobius to put them at -1, 1, i, 
		// resp., then circle radius 1/4, center i/4
		if (w1==null) {
			Mobius tmob=Mobius.trans_abAB(w1,w2,new Complex(-1.0),
					new Complex(1.0),w3,new Complex(0.0,1.0));
			CircleSimple sC=new CircleSimple();
			int rslt=Mobius.mobius_of_circle(tmob, -1,
					new Complex(0.0,0.25), 0.25, sC, false);
			if (rslt==0)
				throw new LayoutException("failed getting incircle "+
						"for ideal triangle");
			return sC;
		}
		
		// move w1 to origin, w2 to positive x-axis
		Mobius tmob=Mobius.standard_mob(w1, w2); // tmob.apply(w1);
		Complex pt2=tmob.apply(w2);
		Complex pt3=tmob.apply(w3); // image of w3
		
		// now find the center/rad of geodesic between pt2, pt3
		HypGeodesic hgeo=new HypGeodesic(pt2,pt3);
		
		// center is on bisecting ray 
		double theta=pt3.arg()/2.0;
		
		// use quadratic formula to find
		double ct=Math.cos(theta);
		double st=Math.sin(theta);
		double x=hgeo.center.x;
		double y=hgeo.center.y;
		double R=hgeo.rad;
		double b=-2.0*(x*ct+(y+R)*st);
		double a=ct*ct;
		double c=x*x+y*y-R*R;

		double rho=(-b-Math.sqrt(b*b-4.0*a*c))/(2.0*a);
		double r=rho*st;
		Complex ctr=new Complex(rho*ct,r);
		
		CircleSimple sC=new CircleSimple();
		int rslt=Mobius.mobius_of_circle(tmob, -1, ctr, Math.abs(r), sC, false);
		if (rslt==0)
			throw new LayoutException("failed getting incircle for ideal triangle");
		// convert back to hyp geometry
		sC=e_to_h_data(sC.center,sC.rad);
		return sC;
	}
	
  /**
   * Given two circles which are supposed to be tangent, find the 
   * tangency point on the geodesic between them. Actually, return
   * pt with distances from z1, z2 having proportions of euclidean
   * radii rho1, rho2.
   * @param z1 Complex
   * @param z2 Complex
   * @param r1 (hyp x-radius)
   * @param r2 (hyp x-radius)
   * @return new Complex, null on error
   */
  public static Complex hyp_tangency(Complex z1,Complex z2,double r1,double r2) {
	  CircleSimple sC1=h_to_e_data(z1,r1);
	  CircleSimple sC2=h_to_e_data(z2,r2);
	  return EuclMath.eucl_tangency(sC1.center,sC2.center,sC1.rad,sC2.rad);
  }

  /** 
   * Given 2 hyp centers/x-radii and x-radius of third, return 
   * third as CircleSimple. (Note: third circle has radius the
   * negative of euclidean radius if it is a horocycle).
   * oj=inv dist of edge opposite circle j. No consistency check on 
   * first two circles or incompatiblities. 'iflag' true reflects
   * incompatibilities (but not yet passed back). 
   * Return empty CircleSimple on error.
   * @param z1 Complex 
   * @param z2 Complex
   * @param x1 double 
   * @param x2 double 
   * @param x3 double
   * @param o1_flag boolean (OBE, can remove)
   * @param ivd1 double
   * @param ivd2 double
   * @param ivd3 double
   * @return CircleSimple
   *  
  */
public static CircleSimple h_compcenter(Complex z1, Complex z2,
 		 double x1,double x2,double x3, double ivd1,double ivd2,double ivd3) {
    double sgn=1;
    CircleSimple sici;

    double s1=HyperbolicMath.x_to_s_rad(x1);
    double s2=HyperbolicMath.x_to_s_rad(x2);
    double s3=HyperbolicMath.x_to_s_rad(x3);

    // If s3 is wrong, return with an error
    if (s1<=(-1.0) || s2<=(-1.0)) // not valid eucl radius
      throw new DataException("error in computing center: horocycle radius <= -1.0");
    
    Complex a=z1;
    Complex b=z2;
    if ((s1<=0) && (s2>0)) { // second circle finite, first not? 
      // then interchange order 
      a=z2; b=z1; double s=s1; s1=s2; s2=s; s=x1; x1=x2; x2 = s;
      s=ivd1; ivd1=ivd2; ivd2=s; sgn=(-1.0); 
    }
    if (s1>0) { // first is now finite
    	double cc=h_comp_cos(x1,x2,x3,ivd1,ivd2,ivd3);

        // if third circle is finite 
        if (s3>0) {
        	double x13 = x1*(x3);
        	double x1p3 = x1+(x3);
        	double s13=s1*s3;
        	double acstuff=(x1p3-x13)/(s13*(1+s13))-(2*x1p3-(1+ivd2)*x13)/(4*s13);
        	if (acstuff<0.0) // error
        		return new CircleSimple(false);
        	double side_p1= acstuff + Math.sqrt(acstuff*(acstuff+2));
        
        	double ahc=side_p1/(side_p1+2); // abs value of hyp center
        	// center as if z1 at origin
        	Complex z3=new Complex(cc*ahc,sgn*Math.sqrt(1-cc*cc)*ahc);  
        
        	if (a.x==b.x && a.y==b.y) z3.x *= ahc;

        	z3=Mobius.mobDiscInvValue(z3,a,b); // move to right place
        	return new CircleSimple(z3,x3,1);
        }

        // else third circle is a horocycle 

        double r=(1-s1)/(1+s1);
        double sc=(r*r+1+2*r*ivd2)/(2*(1+r*ivd2)); // abs value of eucl center
        Complex c=new Complex();
        c.x=sc*cc;
        double cc2;
        if ((cc2=cc*cc)>1.0) // error
        	return new CircleSimple(false);
        c.y=sc*sgn*Math.sqrt(1-cc2);
        double rad=1-sc; // Now have c and its radius
        Complex w1=new Complex(c.x-rad,c.y);
        Complex w2=new Complex(c.x+rad,c.y);
        Complex w3=new Complex(c.x,c.y+rad); // three points on the circle 
        w1=Mobius.mobDiscInvValue(w1,a,b);
        w2=Mobius.mobDiscInvValue(w2,a,b);
        w3=Mobius.mobDiscInvValue(w3,a,b);       // 3 pts on new circle
        sici=EuclMath.circle_3(w1,w2,w3);
        sici.rad *= -1.0; // store neg of eucl radius as x_rad
        sici.center=sici.center.times(1.0/(sici.center.abs())); // get hyp center on unit circle
    }
    
    // first two horocycles, third finite (Note: generally prefer to avoid this situation)
    else if (s3>0) {
    	// Idea: build triple with circle 3 centered at origin, then apply a Mobius to 
    	//       get its euclidean radius and tangency points right to original values,
    	//       see where circle 3 ends up
    	double erad=(1.0-s3)/(1.0+s3); // eucl radius of circle 3 if centered at origin
    	// Compute eucl radii of horocycles with correct inv dist to circle 3 (at origin)
    	double hororad1=(1.0-erad*erad)/(2.0*(1+erad*ivd2));  // o2 = inv dist, circles 1 and 3
    	double hororad2=(1.0-erad*erad)/(2.0*(1+erad*ivd1));  // o1 = inv dist, circles 2 and 3
    	
    	// Assume circle 1 tangent at z=1, compute tangency point of circle 2.
    	double dist12=EuclMath.e_ivd_length(hororad1,hororad2,ivd3);
    	double d1=1.0-hororad1;
    	double d2=1.0-hororad2;
    	double theta=Math.acos((d1*d1+d2*d2-dist12*dist12)/(2.0*d1*d2));
    	
    	// Mobius that fixes +-1 and moves d1=1-2*hororad1 to 1+2*s1 (recalling
    	//   that s1 is euclidean rad with negative sign). Mobius is form (z-t)/(1-tz),
    	//   and since d1 gets carried to d2, can compute t. 
    	// Apply it to origin to see where hyp center of circle 3 is and to tangency
    	//   point of circle 2.
    	d1=1.0-2.0*hororad1;
    	d2=1.0+2.0*s1;
    	double t=(d2-d1)/(d2*d1-1.0);
    	Mobius m1=new Mobius(new Complex(1.0),new Complex(-t),new Complex(-t),new Complex(1.0));
    	Complex newZ=m1.apply(new Complex(0.0));
    	theta=m1.apply(new Complex(0.0,theta).exp()).arg();
    	
    	// Next want parabolic fixing 1 and mapping tangency point of circle 2 so it makes
    	//   original angle between the original horocycles.
    	// Use map (z+1)/(z-1). This maps disc to left half plane, is own inverse. The
    	//   parabolics are shift in halfplane by t in imaginary direction.     	
    	//   Mobius is ((2+it)z-it)/(itz+(2-it)).
    	// Under this map, exp{a} goes to -sin(a)/(1-cos(a)) time i. So want t to equal
    	//   to difference between old image and new.
    	double origTheta=z2.divide(z1).arg();
    	t=Math.sin(theta)/(1.0-Math.cos(theta))-Math.sin(origTheta)/(1.0-Math.cos(origTheta));
    	Mobius m2=new Mobius(new Complex(2.0,t),new Complex(0.0,-t),new Complex(0.0,t),new Complex(2.0,-t));
    	Complex nextZ=m2.apply(newZ);
    	
    	// Final step is to rotate to put tangency point of circle 1 where it was originally
    	sici=new CircleSimple(true);
    	sici.center=nextZ.times(z1); // recall z1 should be on unit circle
    	sici.rad=x3;
    }
    
    // remaining case: all three are horocycles. Expect -eucl radius as
    //      s-rad of target circle.
    else {
        try {
        	sici=h_horo_center(z1,z2,-s2,ivd1,ivd2,ivd3);
        } catch(MobException mex) {
        	throw new MobException("error computing horocycle center");
        }
    }
    
    return sici; 
} 

/**
 * Compute a hyperbolic circle
 * @param z1
 * @param z2
 * @param x1
 * @param x2
 * @param x3
 * @return CircleSimple
 */
public static CircleSimple h_compcenter(Complex z1,Complex z2,
	  		 double x1,double x2,double x3) {
	  return h_compcenter(z1,z2,x1,x2,x3,1.0,1.0,1.0);
  }

  /** 
   * Compute the hyperbolic distance in Poincare metric from z to w. 
   * General formula inside disc is log((a+d)/(a-d)), where d = | z - w |, 
   * a = | 1 - zwb |, with zwb = z*cconj(w).
   * @param z Complex
   * @param w Complex
   * @return double: 0 if z==w (within small tolerance); return negative of
   * eucl distance if one or both of z,w are outside and/or within small 
   * tolerance of unit circle. 
  */
  public static double h_dist(Complex z,Complex w) {
    double d;
    
    if ((d=z.minus(w).abs())<.0000000000001) return 0.0;
    if (z.abs()> .999999999999 || w.abs() > .999999999999)
      return -d;
    Complex zwb1=z.times(w.conj());
    zwb1.x=1.0-zwb1.x;
    zwb1.y *= -1;
    double a=zwb1.abs();
    return (Math.log((a+d)/(a-d)));
  }
  
  /**  
   * Return cosh(len), 'len' = hyp length of edge between 
   * centers of circles with x-radii x1, x2, and inv distance 
   * 'ivd'. Return -1 if one or both radii negative (horocycle, 
   * so distance is infinite).
   * 
   * Original formula for hyperbolic radii h1, h2 is:
   *   cosh(l) = cosh(h1)*cosh(h2)+sinh(h1)*sinh(h2)*ivd
   * 
   * Note: l = h1+h2 if ivd==1.0.
   * @param x1 double
   * @param x2 double
   * @param ivd double
   * @return double, cosh(length), -1 if infinite
  */
  public static double h_ivd_cosh(double x1,double x2,double ivd) {
      if (x1<0 || x2<0) 
    	  return -1.0;
      return ((2.0-x1)*(2.0-x2)+x1*x2*ivd)/(4.0*Math.sqrt((1-x1)*(1-x2)));
  }

  /**
   * Return length of edge between circles with x-radii x1, x2, and
   * inversive distance 'ivd'. Return -1 if length is infinite.
   * @param x1
   * @param x2
   * @param ivd
   * @return double, -1 if length is infinite
   */
  public static double h_ivd_length(double x1,double x2,double ivd) {
	  double csh=h_ivd_cosh(x1,x2,ivd);
	  if (csh<0)
		  return -1;
	  return HyperbolicMath.acosh(csh);
  }
  
  /**
   * This version of 'acosh' (arccosh) takes x >= 1 and returns the 
   * branch with acosh(x)>=0
   * @param x double
   * @return double arccosh
   */
  public static double acosh(double x) {
      return Math.log(x+Math.sqrt(x*x-1));
  }
  
  /**
   * Given x-radius for hyp circle, return inversive
   * distance to unit circle. Recall x=1-exp(-2h) where
   * h is the hyperbolic radius. Inv dist is coth(h)=cosh(h)/sinh(h)
   * @param x double, hyperbolic x-radius
   * @return double, inv distance = coth(h),
   */
  public static double x_rad2invdist(double x) {
	  if (x<=0.0) // negative implies eucl radius of horocycle
		  return 1.0;
	  return ((2/x)-1.0); // (exp(h)+exp(-h))/(exp(h)-exp(-h));
  }
	
	/**
	 * Normalizes hyperbolic packing by putting point a at origin and (if g is
	 * not essentially equal to a) rotating to put g on positive y-axis.
	 * @param p @see PackData
	 * @param a Complex (usually, a=alpha center)
	 * @param g Complex (usually, g=gamma center)
	 * @return int: 0=nothing done, 1=translation, 2=rotation, 3=both
	 */
	public static int h_norm_pack(PackData p,Complex a, Complex g) {
		Complex z=new Complex(1.0);
		int retn=0;
		boolean keepon = false;
		RedList trace;
		
		if (a.abs()>Mobius.MOD1)
			throw new DataException("'a' is too close to unit circle");
		
		// get rotation amount: multiply by z 
		if (a.abs()>OKERR)  // transform by a to get new g
			g=Mobius.mob_trans(g,a);
		double argag=0.0;
		if (g.abs()>100.0*OKERR) {
			argag=Math.PI/2.0-g.arg();
			retn=2;
		}
		z=new Complex(0.0,argag).exp();

		// is a non-zero enough to bother to move?
		if (a.abs()>OKERR) {
			for (int i = 1; i <= p.nodeCount; i++)
				p.setCenter(i,Mobius.mob_trans(p.getCenter(i),a).times(z));
			if ((trace = p.redChain) != null) {
				keepon = true;
				while (trace != p.redChain || keepon) {
					keepon = false;
					trace.center = Mobius.mob_trans(trace.center,a).times(z);
					
		    		// if "blue", must fix second copy also
					if (trace.prev.face==trace.next.face && trace instanceof RedEdge) {
						RedEdge re=((RedEdge)trace).nextRed;
						if (re.face==trace.face) // yes, this is second copy 
							re.center=Mobius.mob_trans(re.center,a).times(z);
					}
					
					trace = trace.next;
				}
			}
			return retn+1;
		}
		
		else if (retn==2) { // just do rotation
			for (int i = 1; i <= p.nodeCount; i++)
				p.setCenter(i,p.getCenter(i).times(z));
			if ((trace = p.redChain) != null) {
				keepon = true;
				while (trace != p.redChain || keepon) {
					keepon = false;
					trace.center = trace.center.times(z);

		    		// if "blue", must fix second copy also
					if (trace.prev.face==trace.next.face && trace instanceof RedEdge) {
						RedEdge re=((RedEdge)trace).nextRed;
						if (re.face==trace.face) // yes, this is second copy 
							re.center=re.center.times(z);
					}

					trace = trace.next;
				}
			}
			return retn;
		}
		
		return 0; // did neither
	}

	/**
	 * Is point z in counterclockwise oriented hyp triangle? Apply mobius 
	 * moving z to origin and check third component of cross products of 
	 * vectors 0 to p_j and 0 to p_(j+1); one or more negative iff z is 
	 * outside the (oriented) triangle.
	 * @param z Complex
	 * @param p1 
	 * @param p2
	 * @param p3
	 * @return boolean
	 */
	public static boolean pt_in_hyp_tri(Complex z,Complex p1,Complex p2,Complex p3) {
		
		// Move to origin and see if in euclidean triangle 
		// (p-z)/(1-conj(z)*p)
		if ((p3.x*p1.y-p3.y*p1.x)<0)
			return false;
		if ((p1.x*p2.y-p1.y*p2.x)<0)
			return false;
		if ((p2.x*p3.y-p2.y*p3.x)<0)
			return false;
		return true;
	}
	
	/**
	 * Given 'dist' in [0,1], return half-angle theta of arc 
	 * of unit circle subtended by geodesics having minimum 
	 * eucl distance 'dist' from the origin. 
	 * @param dist, double >= 0, <= 1
	 * @return Double half-angle, null on error
	 */
	public static Double shadow_angle(double dist) {
		if (dist<0 || dist>1.0)
			return null;
		return Double.valueOf(Math.atan((1-dist*dist)/(2.0*dist)));
	}
	
	/**
	 * Find angle-based barycentric coordinates for z wrt an ideal hyperbolic 
	 * triangle with ideal vertices z0, z1, z2 (counterclockwise). Since angles 
	 * are conformally invariant, compute by moving z to origin, find angles 
	 * between the geodesics to the corners. 
	 * NOTE: works for points in unit disc but outside the ideal triangle; so
	 * coord(s) will be negative. 
	 * 
	 * NOTE: useful for locating points in interstices: the tangency points 
	 * of the circles forming a face are on the boundary of the incircle, and
	 * the circles of the face are geodesics relative to that circle. 
	 *
	 * Coords are bj=1-(tj/pi) where tj is angle between geodesics
	 * through z and z(j+1) and z and z(j+2).
	 * 
	 * TODO: don't have inverse of this function: go from bary coords and 
	 * corners to get the point.
	 * 
	 * @param z Complex
	 * @param z0 Complex, on unit circle
	 * @param z1 Complex, on unit circle
	 * @param z2 Complex, on unit circle
	 * @return BaryPoint; null on error or if z not in interstice
	 */
	public static BaryPoint ideal_bary(Complex z,Complex z0,Complex z1,Complex z2) {

		// check if zj are on unit circle, z is in unit disc
		if (Math.abs(1.0-z0.abs())>OKERR || Math.abs(1.0-z1.abs())>OKERR || 
				Math.abs(1.0-z2.abs())>OKERR || z.abs()>1.0-OKERR)
			throw new DataException("bad data: zj must be on unit circle, z in unit disc");

		Complex []p=new Complex[3];
		Complex one=new Complex(1.0);
		// Mobius moving z to origin is p --> (p-z)/(1-conj(z)*p). Apply this to zj
		p[0]=z0.minus(z).divide(one.minus(z.conj().times(z0)));
		p[1]=z1.minus(z).divide(one.minus(z.conj().times(z1)));
		p[2]=z2.minus(z).divide(one.minus(z.conj().times(z2)));
		
		double angle=p[2].divide(p[1]).arg();
		if (angle<0) // should be positive (though coord b1 could come out negative)
			angle += 2.0*Math.PI;
		double b1=(1.0-angle/Math.PI);
		angle=p[0].divide(p[2]).arg();
		if (angle<0) // should be positive
			angle += 2.0*Math.PI;
		double b2=(1.0-angle/Math.PI);
		
// debug
// System.out.println("(b1,b2,b3) = ("+b1+","+b2+","+(1-b1-b2)+")");
		return new BaryPoint(b1,b2);
	}
	
	/**
	 * Given points z1,z2,z3 and z in the unit disc, find barycentric coords
	 * of z relative to hyperbolic triangle {z1,z2,z3}. For conversion the
	 * other way, see BaryPoint.bp2Complex.
	 * @param z Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return BaryPoint
	 */
	public static BaryPoint h_pt_to_bary(Complex z,Complex z1, Complex z2, Complex z3) {
		
		// check if all are in unit disc
		double max=-1.0;
		max = (z1.abs()>max)?z1.abs():max;
		max = (z2.abs()>max)?z2.abs():max;
		max = (z3.abs()>max)?z3.abs():max;
		if (max>1.000001)
			throw new DataException("some point not in disc");
		
		// if one is essentially on unit circle, shrink all slightly
		if (max>.99999999) {
			z1=z1.times(.999999);
			z2=z2.times(.999999);
			z3=z3.times(.999999);
		}
	
		// find points p1,p2,p3 on the hyperboloid t-(x1^2+x2^2) = 1
		Point3D p1=pt2Hyperboloid(z1);
		Point3D p2=pt2Hyperboloid(z2);
		Point3D p3=pt2Hyperboloid(z3);
		
		// displacement vectors and normal to plane containing the points
		Point3D v12=Point3D.displacement(p1,p2);
		Point3D v13=Point3D.displacement(p1,p3);
		Point3D nrml=Point3D.CrossProduct(v12,v13);

		// check if triangle is degenerate
		if (nrml.norm()<.00001)
			throw new DataException("triangle close to degenerate");
		
		// project z to hyperboloid
		Point3D hyp_pt=pt2Hyperboloid(z);
		
		// project hyp_pt away from origin to the plane
		double s=Point3D.DotProduct(nrml,p1)/Point3D.DotProduct(nrml,hyp_pt);
		Point3D p0=Point3D.scalarMult(hyp_pt,s);
		Point3D v10=Point3D.displacement(p1,p0);
		
		// now represent v10 as linear combination of v12 and v13; solve a
		//   linear system: v10=a*v12+b*v13. Conside [v12 v13 v10] matrix and
		//   row reduce.
		Point3D row1=new Point3D(v12.x,v13.x,v10.x);
		Point3D row2=new Point3D(v12.y,v13.y,v10.y);
		Point3D row3=new Point3D(v12.z,v13.z,v10.z);
		Point3D hold;
		
		// interchange rows, if necessary, to get first pivot
		if (Math.abs(row1.x)<.0001) {
			hold=new Point3D(row1.x, row1.y,row1.z);
			if (Math.abs(row2.x)<.0001) {
				row1=row3;
				row3=hold;
			}
			else {
				row1=row2;
				row2=hold;
			}
		}
		
		// make pivot 1
		row1=Point3D.scalarMult(row1,1.0/row1.x);
		
		// whip out below
		hold=Point3D.scalarMult(row1,-row2.x);
		row2=Point3D.vectorSum(row2,hold);
		hold=Point3D.scalarMult(row1,-row3.x);
		row3=Point3D.vectorSum(row3,hold);
		
		// interchange rows, if necessary, to get second pivot
		if (Math.abs(row2.y)<.0001) {
			hold=new Point3D(row2.x,row2.y,row2.z);
			row2=row3;
			row3=hold;
		}
		
		// make second pivot 1
		row2=Point3D.scalarMult(row2,1.0/row2.y);
		
		// whip out entry above second pivot
		hold=Point3D.scalarMult(row2,-row1.y);
		row1=Point3D.vectorSum(row1,hold);
		
		// read off solutions in 3rd column; these
		//   give barycentric coords
		return new BaryPoint(1-row1.z-row2.z,row1.z);
	}
	
	/**
	 * Given corners, convert 'BaryPoint' to complex value
	 * @param bp BaryPoint
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return new Complex
	 */
	public static Complex bary_to_h_pt(BaryPoint bp,Complex z1,Complex z2,Complex z3) {
		double max=-1.0;
		max = (z1.abs()>max)?z1.abs():max;
		max = (z2.abs()>max)?z2.abs():max;
		max = (z3.abs()>max)?z3.abs():max;
		if (max>1.000001)
			throw new DataException("some point not in disc");
		
		// if one is essentially on unit circle, shrink all slightly
		if (max>.99999999) {
			z1=z1.times(.999999);
			z2=z2.times(.999999);
			z3=z3.times(.999999);
		}
	
		// find points pt1,pt2,pt3 on the hyperboloid t-(x1^2+x2^2) = 1
		Point3D pt1=HyperbolicMath.pt2Hyperboloid(z1);
		Point3D pt2=HyperbolicMath.pt2Hyperboloid(z2);
		Point3D pt3=HyperbolicMath.pt2Hyperboloid(z3);
		
		// find the point in space
		Point3D spt=EuclMath.getSpacePoint(bp,pt1,pt2,pt3);
		
		// project it toward the origin until it hits the paraboloid
		double s=Math.sqrt(spt.z*spt.z-spt.x*spt.x-spt.y*spt.y);
		Point3D hyp_pt=Point3D.scalarMult(spt,1/s);
		
		// project to the disc
		return HyperbolicMath.projPt2disc(hyp_pt);
	}
	
	/**
	 * Given two points in {z>=1.0} (typically on the hyperboloid), 
	 * find outer normal to plane containing them and (0,0,1).
	 * @param p1 Point3D
	 * @param p2 Point3D
	 * @return Point3D
	 */
	public static Point3D outerNormal(Point3D p1,Point3D p2) {
		
		// replace by displacement vectors from (0,0,1)
		p1.z -= 1.0;
		p2.z -= 1.0;
		
		// both essentially at the origin? ambiguous, return 0 vector
		if (p1.norm()<.0000001 && p2.norm()<.0000001)
			return new Point3D(0.0,0.0,0.0);
		
		// one is essentially at the origin? vertical plane
		// if (x,y) is proj of vect from p1 to p2, then want (y,-x) 
		//    (rotated clockwise by pi/2).
		if (p1.norm()<.0000001 || p2.norm()<.0000001)
			return new Point3D(p2.y-p1.y,p1.x-p2.x,0.0);
		
		// p1, p2 essentially the same? Say p1=(x,y,z), then
		//     normal vector to p1 at origin in xy-plane is (-y,x,0),
		//     so use cross product of latter with former.
		if (Point3D.displacement(p1, p2).norm()<.0000001) {
			return new Point3D(p1.x*p1.z,p1.y*p1.z,p1.x*p1.x+p1.y*p1.y);
		}
		
		// typical situation?		
		return Point3D.CrossProduct(p2,p1);		
	}
	
	/**
	 * Converts a point w of the unit disc to a point on the upper sheet of the
	 * hyperboloid t^2-(x1^2+x2^2) = 1. To cover all possibilities, if w is on or
	 * outside the unit circle we return point with z-coord set to  -|w|.
	 * @param w Complex
	 * @return Point3D (x1,x2,t) on paraboloid; if t<0, then point is on or 
	 * outside the unit circle
	 */
	public static Point3D pt2Hyperboloid(Complex w) {
		if (w.abs() >= 1.0)
			return new Point3D(w.x, w.y, -w.abs());
		double wabsSq=w.absSq();
		double t = (1.0 + wabsSq)/(1.0 - wabsSq);
		return new Point3D(w.x *(1+t), w.y *(1+t), t);
	}

	/**
	 * Convert 3D 'pt' on or above hyperboloid to unit disc.
	 * @param pt Point3D
	 * @return Complex
	 */
	public static Complex projPt2disc(Point3D pt) {
		double t=Math.sqrt(1.0+(pt.x*pt.x+pt.y*pt.y));
		double h = pt.z - t;
		if (pt.z < 0 || h < -.00001)
			throw new DataException("point below hyperboloid");
		
		return new Complex(pt.x /(1+t), pt.y /(1+t));
	}

}
