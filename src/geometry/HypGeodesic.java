package geometry;
import complex.Complex;
import complex.MathComplex;
/**
 * Holds info on geodesics for use in plotting. Used in any of 
 * the geometries. See 'SphericalMath' for geometric background.
 */
public class HypGeodesic{
	
	public Complex center; // hyp: eucl center of eucl circle perp to unit circle
	public Complex z1; 
	public Complex z2;  // arc-drawing center and endpoints (ccw)
	public boolean lineFlag; 
	// eucl radius and angles, ang1 <= ang2
	public double rad;
	public double ang1;
	public double ang2;  
  
	public double startAng;
	public double extent; 	// angle (degrees) extent to go from z1 to z2 on circular 
  							// arc. NOTE: because of flip of y-coords, and because
							// arc may be turning left or right, have to adjust
							// start angle and extent may be neg/positive.
	static final double m2pi=2.0*Math.PI;
  
	// Constructor
	public HypGeodesic(Complex a,Complex b){
		z1=new Complex(a);
		z2=new Complex(b);
		lineFlag = false;
		center = new Complex();
		rad = ang1 = ang2 = startAng = extent = 0.0;
		
		double aba = z1.abs();
		double abb = z2.abs();
		Complex w = z1.minus(z2);
		double d = w.abs();
		
		if((aba<0.0000000000001) || (abb<0.0000000000001) || (d<0.00002) || (d>1.999) 
				|| ((Math.abs(z1.y)<0.0000000000001) 
						&& (Math.abs(z2.y)<0.0000000000001))){
			lineFlag=true;
		}

		else {
		    // find center and radius
		    if (aba > 0.999999999999 && abb > 0.999999999999){ // both on unit circle
		    	double dd = 4 - d * d;
		    	w=z1.add(z2);
		    	center=new Complex(2 * w.x / dd,2 * w.y / dd);
		    	rad=d / Math.sqrt(dd);
		    }
		    else {
		        // need third point
		    	w=z2.times(1.0/z2.absSq());
		        if (aba<abb) // third point is reflection of a
		           	w=z1.times(1.0/z1.absSq());
		        CircleSimple tmpcirc = EuclMath.circle_3(z1, z2, w);
		        if (tmpcirc.flag<0) {
		        	lineFlag=true;
		        }
		        else {rad=tmpcirc.rad;
		        	center=tmpcirc.center;
		        }
		    }
		    if (!lineFlag) {
		    	startAng=z1.minus(center).arg();
		    	double diff=MathComplex.radAngDiff(startAng,z2.minus(center).arg());
		    	//	 going from z1 to z2, arc may bend left or right:
		    	Complex z = center.minus(z1).divide(z2.minus(z1));
		    	if (z.y<= 0) diff=diff-m2pi; // bends right
		    	extent=diff; 
		    	// If the arc measure is very small (small curvature) draw as line; 
		    	//   fixup: with experience we might adjust this threshhold.
		    	double hge=Math.abs(extent);
		    	if (hge < .01 || Math.abs(Math.PI-hge)<.01) lineFlag=true;
		    }
		}
	}
}
