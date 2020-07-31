package dcel;

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
	
	// Constructor
	public RedHEdge(HalfEdge he) {
		myEdge=he;
		nextRed=null;
		prevRed=null;
		nextRed=null;
		mobIndx=0;
	}
}
