package dcel;
import java.awt.Color;

import complex.Complex;
import panels.CPScreen;


/**
 * Structure associated with DCEL combinatorics. Holds persistent
 * information on for DCEL 'vertex's. Note that data associated with
 * 'HalfEdges' and 'dcel.Faces', such as indices, are not persistent, as
 * they can change with combinatorial changes. 
 * @author kens, 12/2020
 *
 */
public class VData{ 
	
	public Complex center;	// center as complex number 
	public double rad;		// radius of circle (note: x-radius form in hyp case
							//    namely, x=1-exp(-2h) for hyp radius h finite; 
							//    for h infinite (horocycle) stores negative of 
							//    eucl radius (if computed) for plotting convenience.
							//    (x_radius, x_radii, x_rad, x-radius, x-radii, x-rad)
	public double curv;	    // angle sum at this vertex. 
	public double aim;		// desired angle sum at this vertex (actual angle, not divided by Pi)
	public Color color;
	
	// Constructor (needed only to create 'center')
	public VData() {
		center=new Complex(0.0);
		rad=.05;
		color=CPScreen.getFGColor();
	}
	
	public VData clone() {
		VData Vout=new VData();
		Vout.center=new Complex(center);
		Vout.rad=rad;
		Vout.aim=aim;
		Vout.curv=curv;
		Vout.color=new Color(color.getRed(),color.getGreen(),color.getBlue(),color.getAlpha());
		return Vout;
	}

}