package packing;
import complex.Complex;


/**
 * Structure for various 'real' data associated with circles of a packing.
 * @author kens
 *
 */
public class RData{
	
	public Complex center;	// center as complex number 
	public double rad;		// radius of circle (note: x-radius form in hyp case
							//    namely, x=1-exp(-2h) for hyp radius h finite; 
							//    for h infinite (horocycle) stores negative of 
							//    eucl radius (if computed) for plotting convenience.
							//    (x_radius, x_radii, x_rad, x-radius, x-radii, x-rad)
	public double curv;	    // angle sum at this vertex. 
	public double aim;		// desired angle sum at this vertex (actual angle, not divided by Pi)
	
	// Constructor (needed only to create 'center')
	public RData() {
		center=new Complex(0.0); 
	}
	
	public RData clone() {
		RData Rout=new RData();
		Rout.center=new Complex(center);
		Rout.rad=rad;
		Rout.aim=aim;
		Rout.curv=curv;
		return Rout;
	}

}