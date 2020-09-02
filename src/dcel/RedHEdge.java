package dcel;

import complex.Complex;
import geometry.CircleSimple;
import util.TriAspect;

/**
 * After processing a dcel (see 'CombDCEL.redcookie'), there is, except
 * in the case of a topological sphere, a 'redChain', which is a closed
 * linked list of 'RedHEdge's surrounding a simply connected patch of
 * interior faces. In non-simply connected cases, segments of red edges
 * may be identified. Outside the redChain are one or more ideal faces.
 * NOTE: the dcel structure still lies with the 'HalfEdge's via 'myEdge'.
 * @author kstephe2, 7/2020
 *
 */
public class RedHEdge {
	public HalfEdge myEdge;  // hold original info on edge
	public TriAspect myTri;  // hold real data.
	public RedHEdge nextRed;
	public RedHEdge prevRed;
	public RedHEdge twinRed; // across a red edge
	public int mobIndx;    // index into sidepair Mobius maps
	public int util;
	
	// A red edge 'origin' may have several cent/rad's
	Complex center;
	double rad;
	
	// Constructor
	public RedHEdge(HalfEdge he) {
		myEdge=he;
		nextRed=null;
		prevRed=null;
		nextRed=null;
		mobIndx=0;
		center=new Complex(0.0);
		rad=.5;
	}

	/**
	 * Get center/radius
	 * @return CircleSimple
	 */
	public CircleSimple getData() {
		return new CircleSimple(new Complex(center),rad,1);
	}
	
	public Complex getCenter() {
		return new Complex(center);
	}
	
	/**
	 * This does not set center in parent 'PackData'
	 * @param z Complex
	 */
	public void setCenter(Complex z) {
		center=new Complex(z);
	}
	
	public double getRadius() {
		return rad;
	}
	
	/**
	 * This does not set rad in parent 'PackData'
	 * @param r double
	 */
	public void setRadius(double r) {
		rad=r;
	}

}
