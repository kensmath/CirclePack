package dcel;

import java.awt.Color;

import komplex.EdgeSimple;
import panels.CPScreen;

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
	public Face face;
	public HalfEdge next;
	public HalfEdge prev;
	public int edgeIndx;
	public int mark;
	public RedHEdge myRedEdge; // set when finishing up construction.
	
	// TODO: figure out how to transfer this info as edges change.
	double invDist;  // inversive distance assigned, default 1.0 (tangency)
	double schwarzian; // see 'Schwarzian.java'
	Color color; 
	
	public int util;  // for temporary use only

	// constructor(s)
	public HalfEdge() {
		origin=null;
		twin=null;
		face=null;
		next=null;
		prev=null;
		color=CPScreen.getFGColor();
		edgeIndx=-1; // indicates index is not set
		myRedEdge=null;
		invDist=1.0;
		schwarzian=0.0;
		util=0;
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
		util=0;
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
	
	public RedHEdge getRedEdge() {
		return myRedEdge;
	}
	
	public void setRedEdge(RedHEdge redE) {
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
	
	public double getSchwarzain() {
		return schwarzian;
	}
	
	public Color getColor() {
		return new Color(color.getRed(),color.getGreen(),color.getBlue(),color.getAlpha());
	}
	
	public void setColor(Color col) {
		color=new Color(col.getRed(),col.getGreen(),col.getBlue(),col.getAlpha());
	}
	
	/**
	 * Set to 1.0 unless 'invd' differs by more than .000001
	 * @param invd double
	 */
	public void setInvDist(double invd) {
		if (Math.abs(invd-1.0)<.000001)
			invDist=1.0;
		else
			invDist=invd;
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
	 * clone: caution, pointers may be conflict or be outdated, 
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
	
	/**
	 * Give oriented 'EdgeSimple' <v,w> of endpoints
	 * @param he HalfEdge
	 * @return EdgeSimple
	 */
	public static EdgeSimple getEdgeSimple(HalfEdge he) {
		return new EdgeSimple(he.origin.vertIndx,he.twin.origin.vertIndx);
	}

}
