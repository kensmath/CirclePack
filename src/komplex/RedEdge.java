package komplex;

import complex.Complex;

/**
 * The "red chain" of a packing consists of a chain (closed, linked list)
 * of 'RedList's surrounding a simply-connected core. (Only topological
 * spheres have no red chain.) The outer edges of the red chain are
 * red "edges". A red face might have none, one, or (if it's blue)
 * two red edges. After building the red chain, we go through and
 * build a subordinate closed linked list of 'RedEdge's: namely, we
 * convert certain 'RedList' objects to 'RedEdge's and set up
 * 'nextRed', 'prevRed' for linking. In addition, we clone any "blue"
 * red faces so we have a representative for each of its two red
 * edges (though the second copy is invisible in the 'next/prev'
 * linking). 
 * 
 * Thus, 'RedEdge' is only constructed as a replacement for a 'RedList'
 * object, i.e., clone and replace.
 * @author kens
 *
 */
public class RedEdge extends RedList {
	
	public int startIndex;   // index in 'face.vert[]' for START of this edge
	public RedEdge crossRed; // matching 'RedEdge' or null 
	public int cornerFlag;   // bit encoding: 0-bit = start, 1-bit = end, 

	// for linking (just the 'RedEdge's embedded sub chain)
	public RedEdge nextRed;
	public RedEdge prevRed;
	
	/** 
	 * Constructor -- ALWAYS cloning an existing 'RedList', BUT note
	 * that we use the SAME pointers to its next/prev.
	 */
	public RedEdge(RedList redface) {
		super(redface.packData);
		face=redface.face;
		vIndex=redface.vIndex;
		rad=redface.rad;
		center=new Complex(redface.center);
		next=redface.next;
		prev=redface.prev;
	}
	
	/**
	 * Return true if this red edge is paired with the given red edge.
	 * @param redEdge
	 * @return
	 */
	public boolean isPaired(RedEdge redEdge) {
		// starts/ends comingle?
		int mystart=vert(startIndex);
		int myend=vert((startIndex+1)%3);
		int itstart=redEdge.vert(redEdge.startIndex);
		int itend=redEdge.vert((redEdge.startIndex+1)%3);
		if (mystart==itend && myend==itstart) return true;
		return false;
	}

	/**
	 * True if 'redEdge' is the next red edge to this
	 * @param redEdge
	 * @return
	 */
	public boolean isNextRed(RedEdge redEdge) {
		if (vert((startIndex+1)%3)==redEdge.vert(redEdge.startIndex))
			return true;
		return false;
	}

	/**
	 * True if 'redEdge' is the previous red edge to this
	 * @param redEdge
	 * @return
	 */
	public boolean isPrevRed(RedEdge redEdge) {
		if (redEdge.vert((redEdge.startIndex+1)%3)==vert(startIndex))
			return true;
		return false;
	}

	/**
	 * Given an existing 'RedEdge', find the 'RedList' (which may be
	 * a 'RedEdge' already) which has the next red edge in the red chain.
	 * If 'redEdge' is the first red edge of a "blue" face, then return
	 * 'redEdge' itself --- calling routine needs to check, e.g., to clone.
	 * @param redEdge, 'RedEdge'
	 * @return pointer to RedList (which may be a 'RedEdge')
	 */
	public static RedList nextRedEdge(RedEdge redEdge) {
		// if "blue" and at first red edge
		if (redEdge.next.face==redEdge.prev.face 
				&& redEdge.vert(redEdge.startIndex)==redEdge.sharedPrev())
			return redEdge; 
		int vert=redEdge.sharedNext();
		RedList trace=redEdge;
		while (trace!=null && trace.sharedNext()==vert) {
			trace=trace.next;
		}
		return trace;
	}
	
	/**
	 * Clone a 'RedEdge'. Set next/prev and nextRed/prevRed to null;
	 * these must be set by the calling routine. 
	 * @param rededge 'RedEdge'
	 * @return new 'RedEdge' with next/prev and nextRed/prevRed null.
	 */
	public static RedEdge clone(RedEdge rededge) {
		RedEdge newMe=new RedEdge(rededge);
		newMe.face=rededge.face;
		newMe.vIndex=rededge.vIndex;
		newMe.rad=rededge.rad;
		newMe.center=new Complex(rededge.center);
		newMe.prev=newMe.next=null;
		newMe.util=rededge.util;
		newMe.nextRed=newMe.prevRed=null;
		return newMe;
	}
	
	/**
	 * Find vertex at start of this red edge
	 * @return, index of vertex
	 */
	public int myFirstVert() {
		return packData.faces[face].vert[startIndex];
	}
		
}
