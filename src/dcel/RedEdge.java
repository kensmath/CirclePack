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
 * This is not persistent data; these edges change, e.g., when we change
 * the DCEL.
 * TODO: do we need to save data when changing the DCEL?
 * @author kstephe2, 7/2020
 *
 */
public class RedEdge {
	public HalfEdge myEdge;  // hold original info on edge
	public RedEdge nextRed;
	public RedEdge prevRed;
	public RedEdge twinRed; // across a red edge
	public int mobIndx;      // index into sidepair Mobius maps
	
	// The 'origin' for this red edge may have several other red edges
	//    with different cent/rad's
	Complex center;
	double rad;
	
	public int redutil; // for temporary use only

	// Constructor
	public RedEdge(HalfEdge he) {
		myEdge=he;
		nextRed=null;
		prevRed=null;
		nextRed=null;
		mobIndx=0;
		center=new Complex(0.0);
		rad=.5;
		redutil=0;
		// set 'redutil' if ideal face is across myEdge;
		//    this is used in 'd_redChainBuilder'
		if (he.twin!=null && he.twin.face!=null && 
				he.twin.face.faceIndx<0)
			redutil=1;
		
	}

	/**
	 * Get center/radius. Center may be null.
	 * @return CircleSimple
	 */
	public CircleSimple getData() {
		return new CircleSimple(getCenter(),rad,1);
	}
	
	/**
	 * Get center; return null if it's null
	 * @return new Complex
	 */
	public Complex getCenter() {
		if (center==null)
			return null;
		return new Complex(center);
	}
	
	/**
	 * Doesn't set center in 'PackData' or in 'origin' 
	 * @param z Complex
	 */
	public void setCenter(Complex z) {
		center=new Complex(z);
	}
	
	public double getRadius() {
		return rad;
	}
	
	/**
	 * This does not set rad or in 'origin'
	 * @param r double
	 */
	public void setRadius(double r) {
		rad=r;
	}

	/**
	 * Return center/rad data specific to the red edge.
	 * Note: see 'PackData.getCircleSimple' for data from 'vData'. 
	 * @return CircleSimple
	 */
	public CircleSimple getCircleSimple() {
		return new CircleSimple(center,rad);
	}
	
	/**
	 * Set center and radius
	 * @param cS CircleSimple
	 */
	public void setCircleSimple(CircleSimple cS) {
		center=cS.center;
		rad=cS.rad;
	}
	
	/**
	 * clone: CAUTION: pointers may be in conflict or outdated.
	 * @return new RedHEdge
	 */
	public RedEdge clone() {
		RedEdge rhe=new RedEdge(this.myEdge);
		rhe.nextRed=nextRed;
		rhe.prevRed=prevRed;
		rhe.twinRed=twinRed;
		rhe.mobIndx=mobIndx;
		rhe.myEdge=myEdge;
		rhe.center=new Complex(center);
		rhe.rad=rad;
		rhe.redutil=redutil;
		return rhe;
	}
	
	public String toString() {
		return myEdge.toString();
	}

	
}
