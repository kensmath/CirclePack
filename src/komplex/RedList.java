package komplex;

import complex.Complex;
import deBugging.LayoutBugs;
import exceptions.RedListException;
import packing.PackData;

/**
 * Class for use in doubly linked closed list of faces forming the 
 * "red chain" of a packing complex. After initial instance 'next' and 
 * 'prev' should never be null (see 'RedList' constructor), though 
 * adjustments are needed in developing the final red chain.
 * 
 * Some red faces have "red" edges, that is, outside edges for the red
 * chain; the 'RedList' is then changed to subclass 'RedEdge', giving an
 * embedded subchain used to find side pairings, border segments, etc.

 * A 'RedEdge' has a 'cornerFlag' structure which is rather complicated. 
 * Each "side" of the complex (maximal segment of paired edges) has 
 * "begin" and "end" vertices.
 *  
 * BEGIN vertex: The side's first corner is the END vertex of a red edge 
 * belonging to an entry 'redlist' in the red chain, and the info on it 
 * is stored in 'redlist.cornerFlag'. (CAUTION: this red edge precedes, 
 * and is NOT part of, the "side" itself.) In 'cornerFlag', the vertex 
 * at the START of the red edge has index 'i' in 'redlist.face.vert[]', 
 * and 'cornerFlag[i]' has its 0-bit set to indicate a BEGIN corner. 
 * (NOTE: vert[(i+1)%3] is thus the vertex starting the "side".)
 * 
 * END vertex: The side's last corner is the END vertex of a red edge
 * belonging to an entry 'redlist' in the red chain, and the info on it
 * is stored in 'redlist.cornerFlag'. This red edge is actually the last
 * edge in the "side". Its END vertex has index 'j' in 'redlist.face.vert[]',
 * and 'cornerFlag[j]' has its 1-bit set to indicate an END corner.
 * 
 * @author kens
 *
 */
public class RedList {

	public PackData packData;  // need this for, eg., faces[].vert[] info
	public int face;		   // index of associated face in 'p.faces'
	public int vIndex;        /* index (in 'faces[face].vert') of the vert this 
						      * redface is responsible for in drawing order 
						      * (the one 'center' and 'rad' apply to) */
	/* CAUTION: 'Face.indexFlag' points to a different vertex; namely, 
	 * vert[indexFlag] and vert[(indexFlag+1)%3] are USED TO PLOT the vertex
	 * associated with 'vIndex'.
	 */
	public double rad;       // radius of vIndex vert 
	public Complex center;   /* center of vIndex vert (may differ from rData and from 
						      * this same vert in other redfaces) */
	public RedList next;     // next redface
	public RedList prev;     // previous redface
	
	public boolean done;     // used in redchain work; true ==> finished with this face
	public int util;         // utility, mainly for passing indices, eg 'next_red_edge' 
	
	// Constructors
	public RedList(PackData p) { 	// create empty redface
		packData=p;
		done=false;
	}

	public RedList(PackData p,int f) { 	// create initial redface
		packData=p;
		face=f;
		done=false;
	}

	/**
	 * create new redface and insert after 'current' in the linked list.
	 * Note: if 'this' is going to be "blue", the calling routine has
	 * to manage insertion of a copy of 'current'.
	 */
	public RedList(RedList current,int f) throws RedListException {
		if (current==null) throw new RedListException();
		packData=current.packData;
		if (current.next==null) { // current was the only redlist
			prev=next=current;
			current.next=current.prev=this;
		}
		else {
			prev=current;
			next=current.next;
			current.next.prev=this;
			current.next=this;
		}
		face=f;
		done=false;
	}
	
	/**
	 * Return vertex with given index in 'face.vert[]'; RedListException
	 * on error.
	 */
	public int vert(int idx) {
		if (idx<0 || idx>2) throw new RedListException();
		return packData.faces[face].vert[idx];
	}

	/**
	 * Return outside vertex this red face shares with 'prev' red face,
	 * RedListException on error.
	 * @return int, vert index
	 */
	public int sharedPrev() {
		int idx=packData.face_nghb(prev.face,face);
		if (idx<0) throw new RedListException();
		return packData.faces[face].vert[(idx+1)%3];
	}
	
	/**
	 * Return outside vertex this red face shares with 'next' red face,
	 * RedListException on error.
	 * @return int, vert index
	 */
	public int sharedNext() {
		int idx=packData.face_nghb(next.face,face);
		if (idx<0) throw new RedListException();
		return packData.faces[face].vert[idx];
	}
	
	public boolean hasNext() {
		if (next!=null) return true;
		return false;
	}
	
	public boolean hasPrev() {
		if (prev!=null) return true;
		return false;
	}
	
	public RedList next() throws RedListException {
		if (next==null) throw new RedListException();
		return next;
	}
	
	public RedList prev() throws RedListException {
		if (next==null) throw new RedListException();
		return prev;
	}
	
	/** 
	 * Given 'redface' in the red chain and 'indx' (into 'face.vert[]'), 
	 * find the red face responsible for laying out the associated 
	 * 'targetVert' circle when transiting the red chain --- this returned
	 * redface is where the 'center' and 'rad' used for appropriate layout 
	 * are maintained.
	 * @param redface RedList (possibly RedEdge)
	 * @param indx int, index in 'redface.face.vert[]'
	 * @return RedList responsible 
	 */
	public static RedList whos_your_daddy(RedList redface,int indx) {
		PackData p=redface.packData;
		
		boolean debug=false; // debug=true;
		
		// special circumstance: 'redface' is a "blue" 'RedEdge'.
		//   In this case, there are 2 copies, and we must point
		//   to the FIRST, because that is the one which is visible
		//   in the 'redChain' list used in setting centers.
		if (redface instanceof RedEdge) {
			RedEdge redge=(RedEdge)redface;
			if (redface.face==redge.prevRed.face)
				redface=(RedList)redge.prevRed;
		}
		
		// is redface itself responsible?
		if (indx==redface.vIndex) 
			return redface;

		// so, some other face is responsible
		int targetVert=p.faces[redface.face].vert[indx];
		int safety=p.countFaces(targetVert);
		RedList rtrace=redface.prev;
		int tick=0;

		// move back through the red faces until you get the
		//   one that holds data for 'targetVert'
		while (tick<safety) {
			int v=p.faces[rtrace.face].vert[rtrace.vIndex];
			if (v==targetVert)
				return (RedList)rtrace;
			rtrace=rtrace.prev;
			tick++;
		}
		throw new RedListException(); // something must have gone wrong
	}
	
	/**
	 * Remove 'this' element itself from the closed linked 'RedList' that
	 * it is part of and reclose the resulting list. Return the pointer 
	 * to 'this' itself so the calling routine can get its handle and 
	 * then destroy it.
	 * @param 
	 * @return
	 */
	public RedList remove() {
		if (next==null) return null; // this must be last in linked list
		// cutloose from and re-close the linked list
		this.prev.next=next;
		this.next.prev=prev;
		return this;
	}
	
	/**
	 * Return a new 'RedEdge' created (or cloned) from given 'RedList'.
	 * Use first available red edge in the red chain; if there is none, 
	 * return null. Reset the 'RedList' links (but calling routine must 
	 * handle the 'RedEdge' links). 
	 * @param redface, 'RedList' or 'RedEdge'
	 * @return new 'RedEdge' or null 
	 */
	public static RedEdge redEdgeMe(RedList redface) {
		if (redface==null) return null;
		if (redface.sharedNext()==redface.sharedPrev()) return null;
		int indx=redface.packData.face_nghb(redface.prev.face,redface.face);
		if (indx<0) return null;
		return redEdgeMe(redface,(indx+1)%3);
	}

	/**
	 * Return a new 'RedEdge' created (or cloned) from given 'RedList' 
	 * with index 'idx' as 'startIndex'. Only call this directly if 
	 * 'idx' points to second red edge of "blue" face (in particular, 
	 * 'idx' should always be legal). Reset the 'RedList' links (but 
	 * calling routine must handle the 'RedEdge' links). 
	 * Return null on error (e.g, idx or redface doesn't qualify). 
	 * @param redface, 'RedList' or 'RedEdge'
	 * @param idx, desired 'startIndex'
	 * @return new 'RedEdge' or null
	 */
	public static RedEdge redEdgeMe(RedList redface,int idx) {

		RedEdge newMe=new RedEdge(redface);
		if (redface instanceof RedEdge) { // already 'RedEdge'?
			RedEdge redge=(RedEdge)redface;
			newMe.cornerFlag=redge.cornerFlag;
			newMe.crossRed=redge.crossRed;
			newMe.nextRed=redge.nextRed;
			newMe.prevRed=redge.prevRed;
		}
		
		// finish: resetting will typically garbage original 'redface'
 		newMe.startIndex=idx;
 		RedList redn=redface.next;
 		RedList redp=redface.prev;
 		// careful not to lose 'redChain' reference
		if (newMe.packData.redChain==redface) 
			newMe.packData.redChain=newMe;
		redp.next=newMe;
		redn.prev=newMe;
		return newMe;
	}
	
	/**
	 * Revert the given 'RedEdge' to a straight 'RedList' and reset
	 * the 'RedList' links. Always returns a new 'RedList' or null.
	 * @param rededge, 'RedEdge'
	 * @return new 'RedList'
	 */
	public static RedList redListMe(RedEdge rededge) {
		RedList newMe=new RedList(rededge.packData);
		newMe.face=rededge.face;
		newMe.vIndex=rededge.vIndex;
		newMe.rad=rededge.rad;
		newMe.center=new Complex(rededge.center);
		newMe.next=rededge.next;
		newMe.prev=rededge.prev;
		// finish: resetting will typically garbage old 'rededge'
 		RedList redn=rededge.next;
 		RedList redp=rededge.prev;
		if (newMe.packData.redChain==rededge)
			newMe.packData.redChain=newMe;
		redp.next=newMe;
		redn.prev=newMe;
		return newMe;
	}

	/** 
	 * Return index of start of red edge if this 'RedList' has a red edge,
	 * else -1; if this is a "blue" red face, then give the first of its
	 * two red edges.
	 * @return index or -1 on failure
	 */
	public int redEdgeIndex() {
		int vp=sharedPrev();
		if (sharedNext()==vp) return -1;
		return packData.face_index(face, vp);
	}

	/**
	 * Clone a 'RedList'. Set next/prev to null so they can be set
	 * by the calling routine. ('RedEdge' has its own 'clone' routine)
	 * @param new redlist with next/prev set to null
	 * @return
	 */
	public static RedList clone(RedList redlist) {
		RedList newMe=new RedList(redlist.packData);
		newMe.face=redlist.face;
		newMe.vIndex=redlist.vIndex;
		newMe.rad=redlist.rad;
		newMe.center=new Complex(redlist.center);
		newMe.prev=newMe.next=null;
		newMe.util=redlist.util;
		return newMe;
	}
}
