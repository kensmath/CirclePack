package dcel;

/** DCEL HalfEdge. Its 'face' is on its left. If face==null,
 * then this haldedge and its twin are bdry halfedges.
 * @author kstephe2
 *
 */
public class HalfEdge {

	public Vertex origin;
	public HalfEdge twin;
	public Face face;
	public HalfEdge next;
	public HalfEdge prev;
	public int edgeIndx;
	public int util;
	
	// constructor(s)
	public HalfEdge() {
		origin=null;
		twin=null;
		face=null;
		next=null;
		prev=null;
		edgeIndx=-1; // indicates index is not set
		util=0;
	}
	
	public HalfEdge(Vertex v) {
		this();
		origin=v;
	}
	
	/**
	 * Detach this edge: adjust so nothing refers to it,
	 * make 'prev' and 'next' null.
	 * Calling routine can check 'maroon' to see if one or 
	 * both ends are marooned (i.e., has 'halfedge' set to null);
	 * @return Vertex[2]
	 */
	public Vertex []detach() {
		Vertex []maroon=new Vertex[2];
		maroon[0]=maroon[1]=null;
		boolean hits=false;
	
		// handle origin end
		if (twin.next==this) { // marooned?
			origin.halfedge=null;
			maroon[0]=origin;
			hits=true;
		}
		else { // detach this end
			twin.next.prev=prev;
			prev.next=twin.next;
			origin.halfedge=prev.twin;
		}
		
		// handle far end
		if (next==twin) { // marooned?
			twin.origin.halfedge=null; 
			maroon[1]=twin.origin;
			hits=true;
		}
		else { // detach far end
			next.prev=twin.prev;
			twin.prev.next=next;
			twin.origin.halfedge=twin.prev.twin;
		}

		// null out 'prev' and 'next'
		next=prev=null;
		twin.next=twin.prev=null;

		// return
		if (!hits) 
			return null;
		return maroon;
	}

	/** 
	 * If 'face' or twin face is null or ideal (faceIndx<0), then 
	 * this and its twin are "boundary" edges.
	 * @return boolean
	 */
	public boolean isBdry() {
		if (face==null || face.faceIndx<0 || twin.face==null || twin.face.faceIndx<0)
			return true;
		return false;
	}
	
	/**
	 * clone: caution, pointer may be outdated, 
	 * @return new HalfEdge
	 */
	public HalfEdge clone() {
		HalfEdge he=new HalfEdge();
		he.face=face;
		he.next=next;
		he.prev=prev;
		he.twin=twin;
		he.origin=origin;
		he.edgeIndx=edgeIndx; // may want to reset
		return he;
	}

	/** 
	 * spit out the two end vertex indices
	 */
	public String toString() {
		return(" "+origin.vertIndx+" "+twin.origin.vertIndx+" ");
	}

}
