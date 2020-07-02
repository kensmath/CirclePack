package microLattice;

/** 
 * A 'latNode' is a node used in 'MicroGrid'; it represents combinatoric
 * information on a node in the basic microgrid. Its location in the
 * microgrid is in the signed integer coords (u,w), where location is
 * in the grid is u*V1 + w*V2, using the vectors V1, V2 for generating
 * the grid:
 * 		V1 = <1/2, -CPBase.sqrt3by2> and 
 * 		V2 = <1/2, CPBase.sqrt3by2>.
 * 'latNode's generally belong to some superlattice, and 'level' gives
 * its level, from which the calling routine can obtain other 
 * information such as lattice spacing, radii, etc.
 * @author kstephe2, 2/2017 and 7/2020
 */
import java.awt.Color;

import allMains.CPBase;
import complex.Complex;

public class LatNode {
	public int myVert;		// index in 'packData'
	public int level;       // microgrid level to which this belongs
	int u;			        // u coord in microgrid
	int w;			        // w coord in microgrid
	Color color;	
	boolean chosen;         // true if node is a center at its level
	int mark;				// utility
		
	// Constructor
	public LatNode(int v,int uu,int ww,int lev) {
		myVert=v;
		level=lev;
		u=uu;
		w=ww;
		chosen=false;
		mark=0;
	}
		
	/** 
	 * Find real world location given scaling
	 * @param microScaling double
	 * @return Complex
	 */
	public Complex getZ(double microScaling) {
		double mrad=microScaling/2.0;
		return new Complex(mrad*((double)(u+w)),mrad*CPBase.sqrt3*((double)(w-u)));
	}
}
