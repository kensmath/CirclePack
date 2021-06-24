package dcel;
import java.awt.Color;

import complex.Complex;
import exceptions.CombException;
import geometry.CircleSimple;
import util.ColorUtil;


/**
 * Structure associated with DCEL combinatorics. Holds persistent
 * information on for DCEL 'vertex's. Note that data associated with
 * 'HalfEdges' and 'dcel.Faces', such as indices, are not persistent, as
 * they can change with combinatorial changes. 
 * @author kens, 12/2020
 *
 */
public class VData{ 
	
	// data that may preceed combinatorics
	public Complex center;	// center as complex number 
	public double rad;		// radius of circle (note: x-radius form in hyp case
							//    namely, x=1-exp(-2h) for hyp radius h finite; 
							//    for h infinite (horocycle) stores negative of 
							//    eucl radius (if computed) for plotting convenience.
							//    (x_radius, x_radii, x_rad, x-radius, x-radii, x-rad)
	public double aim;		// desired angle sum at this vertex (actual angle, not divided by Pi)
	public Color color;
	public int mark;

	// data that needs to be be updated
	public int num;         // number of nghb'ing (non-ideal) faces
	public int[] findices;  // indices of nghb'ing (non-ideal) faces
	public int[] myIndices; // my index in corresponding face (aligned with 'findices') 
	public double curv;	    // angle sum at this vertex. 
	public int plotFlag;	// often OBE
	public int qualFlag;    // often OBE

	int bdryFlag;           // set in 'PackData.attachDCEL' according to 'Vertex.bdryFlag' 

	// Constructor (needed only to create 'center')
	public VData() {
		center=new Complex(0.0);
		rad=.05;
		color=ColorUtil.getFGColor();
	}
	
	public VData clone() {
		VData Vout=new VData();
		Vout.center=new Complex(center);
		Vout.rad=rad;
		Vout.aim=aim;
		Vout.color=ColorUtil.cloneMe(color);
		Vout.mark=mark;

		Vout.num=num;
		Vout.findices=new int[num];
		Vout.myIndices=new int[num];
		for (int k=0;k<num;k++) {
			Vout.findices[k]=findices[k];
			Vout.myIndices[k]=myIndices[k];
		}
		Vout.curv=curv;
		Vout.bdryFlag=bdryFlag;
		Vout.plotFlag=plotFlag;
		Vout.qualFlag=qualFlag;
		return Vout;
	}
	
	public int getBdryFlag() {
		return bdryFlag;
	}
	
	/**
	 * set clone of 'col'
	 * @param col Color
	 */
	public void setColor(Color col) {
		if (col==null)
			color=null;
		else
			color=ColorUtil.cloneMe(col);
	}
	
	/**
	 * get clone of color
	 * @return new Color
	 */
	public Color getColor() {
		return ColorUtil.cloneMe(color);
	}
	
	public void setBdryFlag(int bf) {
		if (bf!=0 && bf!=1)
			throw new CombException("error setting 'bdryFlag'");
		bdryFlag=bf;
	}
	
	/**
	 * Get circle data
	 * @return CircleSimple
	 */
	public CircleSimple getCircleSimple() {
		return new CircleSimple(center,rad);
	}

}