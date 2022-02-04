package dcel;

import java.awt.Color;

import complex.Complex;
import exceptions.CombException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import util.ColorUtil;

/** 
 * DCEL HalfEdge. Its 'face' is on its left. As we transition to a DCEL
 * core model of packings, each edge will carry an 'inversive distance',
 * 'schwarzian', and color (though these will be redundant for now). 
 * Careful, as edges are not persistent. 
 * TODO: figure out how to transfer date when edges change.
 * @author kstephe2
 *
 */
public class HalfEdge {

	public Vertex origin;
	public HalfEdge twin;
	public DcelFace face;
	public HalfEdge next;
	public HalfEdge prev;
	public int edgeIndx;
	public int mark;
	public RedEdge myRedEdge; // set when finishing up construction.
	
	// TODO: figure out how to transfer this info as edges change.
	double invDist;  // inversive distance assigned, default 1.0 (tangency)
	double schwarzian; // see 'Schwarzian.java'
	Color color;  // not used much yet.
	
	// for temporary use only
	public int eutil; 
	Complex genTang;  // "general tangency" point; not yet used/maintained

	// constructor(s)
	public HalfEdge() {
		origin=null;
		twin=null;
		face=null;
		next=null;
		prev=null;
		color=ColorUtil.getFGColor();
		edgeIndx=-1; // indicates index is not set
		myRedEdge=null;
		invDist=1.0;
		schwarzian=0.0;
		eutil=0;
	}
	
	public HalfEdge(Vertex v) {
		this();
		origin=v;
	}
	
	public HalfEdge(HalfEdge he) { // clone
		origin=he.origin;
		twin=he.twin;
		face=he.face;
		next=null;
		prev=null;
		edgeIndx=-1;
		myRedEdge=null;
		invDist=he.invDist;
		schwarzian=he.schwarzian;
		eutil=0;
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
	 * This halfedge should be in a closed cycle of
	 * halfedges; return the count.
	 * @return int
	 */
	public int getCycleCount() {
		int count=0;
		HalfEdge he=this;
		int safety=1000;
		do {
			count++;
			safety--;
			he=he.next;
		} while (he!=this && safety>0);
		if (safety==0)
			throw new CombException("edge "+this+" cycle does not close up");
		return count;
	}

	/** 
	 * CAUTION: faces are not always non-null. But ideal
	 * faces should be non-null and have index < 0. 
	 * If 'face' or twin face is ideal (faceIndx<0), then 
	 * this and its twin are "boundary" edges.
	 * @return boolean
	 */
	public boolean isBdry() {
		if ((face!=null && face.faceIndx<0) || 
				(twin.face!=null && twin.face.faceIndx<0))
			return true;
		return false;
	}
	
	/**
	 * determines if origin is neighbor with vertex of index 'w'.
	 * @param w int
	 * @return
	 */
	public boolean isNghb(int w) {
		HalfEdge he=this;
		do {
			if (he.twin.origin.vertIndx==w)
				return true;
			he=he.prev.twin;
		} while (he!=this);
		return false;
	}
	
	/**
	 * Return the next half-hex 'HalfEdge'; 
	 * if this edge is <v,w>, then pass by two 
	 * intervening spokes to find <w,u>; that is, 
	 * 3 clw around w from <w,v>. Return null on failure. 
	 * Note that return might be <w,v> if degree 
	 * of w is three, or null if a bdry edge is 
	 * encountered. 
	 * @return HalfEdge, null on failure
	 */
	public HalfEdge HHleft() {
		HalfEdge spoke=this.twin;
		int tick=3;
		do {
			tick--;
			// reached a bdry edge?
			if (spoke.twin.face!=null && spoke.twin.face.faceIndx<0) {
//				if (tick==0)
//					return spoke;
				return null;
			}
			spoke=spoke.twin.next; // rotate clw
		} while (tick>0);
		if (tick==0)
			return spoke;
		return null;
	}
	
	/**
	 * Return the next half-hex 'HalfEdge'. That
	 * is, pass two intervening spokes on the right.
	 * If this edge is <v,w>, then find edge <w,u> which is
	 * 3 interior edges cclw around w from <w,v>. Return
	 * null on failure. Note that return might be
	 * <w,v> if degree of w is three, or null if
	 * a bdry edge is encountered. 
	 * @return HalfEdge, null on failure
	 */
	public HalfEdge HHright() {
		HalfEdge spoke=this.twin;
		int tick=3;
		do {
			tick--;
			// reached a bdry edge?
			if (spoke.face!=null && spoke.face.faceIndx<0) {
				if (tick==0)
					return spoke;
				return null;
			}
			spoke=spoke.prev.twin; // rotate cclw
		} while (tick>0);
		if (tick==0)
			return spoke;
		return null;
	}
	
	public RedEdge getRedEdge() {
		return myRedEdge;
	}
	
	/**
	 * set the 'myRedEdge' pointer 
	 * @param redE
	 */
	public void setRedEdge(RedEdge redE) {
		myRedEdge=redE;
	}
	
	/**
	 * set schwarzian of this edge; user must handle setting of
	 * twin edge.
	 * @param sch double
	 */
	public void setSchwarzian(double sch) {
		schwarzian=sch;
	}
	
	public double getSchwarzian() {
		return schwarzian;
	}
	
	public Color getColor() {
		return new Color(color.getRed(),color.getGreen(),color.getBlue(),color.getAlpha());
	}
	
	public void setColor(Color col) {
		color=new Color(col.getRed(),col.getGreen(),col.getBlue(),col.getAlpha());
	}
	
	public int getMark() {
		return mark;
	}
	
	public void setMark(int m) {
		mark=m;
	}
	
	/**
	 * Set legal 'invDist' for 'this' and 'this.twin'.
	 * Legal values lie in [-1,infty): in [-1,1] circles 
	 * overlapping with angle acos(invDist), so 1.0 is
	 * default, tangency and 0.0 represnts orthogonality. 
	 * Values in (-1,0) called "deep" overlaps, those 
	 * in (0,infty) for "separated" circles. Deep overlaps 
	 * and separations can lead to packing incompatibilities: 
	 * existence and uniqueness results guaranteed only 
	 * for values in [0,1].
	 * Note: Set to 1.0 unless 'invd' differs from 1.0 by more 
	 * than .000001.
	 * @param invd double
	 */
	public void setInvDist(double invd) {
		if (invd<-1.0)
			throw new ParserException("Inversive distance must be in [-1,infty)");
		if (Math.abs(invd-1.0)<.000001) {
			invDist=1.0;
			if (twin!=null)
				twin.invDist=1.0;
		}
		else {
			invDist=invd;
			if (twin!=null)
				twin.invDist=invd;
		}
	}
	
	/**
	 * return 1.0 unless 'invDist' differs by more than .000001
	 * @return
	 */
	public double getInvDist() {
		if (Math.abs(invDist-1.0)<.000001)
			return 1.0;
		return invDist;
	}
	
	/**
	 * Return 'HalfEdge' opposite 'this'; i.e., 'this.origin'
	 * must be even degree.
	 * @return HalfEdge, null if not even degree
	 */
	public HalfEdge findOppEdge() {
		HalfEdge he=this;
		HalfEdge ohe=this;
		do {
			he=he.prev.twin; // cclw
			ohe=ohe.twin.next; // clw
			if (he==ohe)
				return he;
		} while (he!=this);
		return null;
	}
	
	/**
	 * Give oriented 'EdgeSimple' <v,w> of endpoints
	 * @param he HalfEdge
	 * @return EdgeSimple, null on error
	 */
	public static EdgeSimple getEdgeSimple(HalfEdge he) {
		if (he==null)
			return null;
		return new EdgeSimple(he.origin.vertIndx,he.twin.origin.vertIndx);
	}
	
	/**
	 * clone: caution, pointers may be in conflict or outdated, 
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
