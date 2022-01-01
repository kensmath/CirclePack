package dcel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

import exceptions.CombException;
import listManip.HalfLink;
import util.ColorUtil;

/**
 * DCEL face. Careful, the 'edge' and 'faceIndx' are not
 * persistent data but may be changed, e.g., when 'alpha'
 * is reset. Typically, the 'edge' is chosen based on layout
 * order: its vertices are assumed in place, next vertex 
 * around is computed based on these. 'faceIndx' may not
 * correspond to face index in 'PackData'.
 * 
 * @author kstephe2
 *
 */
public class Face {

	public HalfEdge edge; // 'this' is on the left of its halfedge
	public int faceIndx;  // utility index for this face
	public Color color;
	public int mark;
	public int plotFlag;
	public int util;
	
	// Constructor(s)
	public Face() {
		edge=null;
		faceIndx=0;
		color=ColorUtil.getFGColor();
	}
	
	public Face(int i) {
		this();
		faceIndx=i;
	}
	
	/**
	 * Return the face across the side opposite
	 * to 'v'. We assume this face has 3 vertices.
	 * Null on error: e.g., v is not a 
	 * vertex of this face or if the expected face 
	 * was not instantiated.
	 * @param v int
	 * @return Face, null on error
	 */
	public Face faceOpposite(int v) {
		HalfEdge he=edge;
		int safety=4;
		do {
			safety--;
			if (he.origin.vertIndx==v) {
				return he.next.twin.face;
			}
			he=he.next;
		} while (he!=edge && safety>0);
		if (safety==0)
			throw new CombException("face "+this.faceIndx+" doesn't "+
					"have face opposite to "+v);
		return null;
	}
	
	/**
	 * Return cclw ordered array of neighboring face indices.
	 * Close up by repeating first entry if there are no ideal
	 * faces as neighbors. If there is a neighboring ideal face
	 * (and we assume there is at most one), then return an open
	 * list with the last being the ideal face neighbor.
	 * @return Vector<Integer>
	 */
	public ArrayList<Integer> faceFlower() {
		ArrayList<Integer> flower=new ArrayList<Integer>(0);

		int ihit=0;
		HalfEdge he=edge;
		do {
			int htfi=he.twin.face.faceIndx;
			flower.add(htfi);
			if (htfi<0)
				ihit++;
			he=he.next;
		} while (he!=edge);
		
		if (ihit>1)
			throw new CombException("Face "+faceIndx+" has more than one ideal face as neighbor");
		if (ihit==0) {
			flower.add(flower.get(0)); // close up
			return flower;
		}
		
		ArrayList<Integer> newfl=new ArrayList<Integer>(0);
		int hit=-1;
		for (int i=0;(i<flower.size() && hit<0);i++) {
			if (flower.get(i)>0)
				newfl.add(flower.get(i));
			else
				hit=i;
		}
		if (hit==flower.size()-1) {
			newfl.add(flower.get(hit)); // put ideal neighbor last
			return newfl;
		}
		for (int j=flower.size()-1;j>hit;j--) {
			newfl.add(0,flower.get(j));
		}
		newfl.add(flower.get(hit)); // put ideal neighbor last
		return newfl;
		
	}
		
	/**
	 * Is this an "ideal" face? If yes, return an edge whose twin 
	 * edge has a null face (or an ideal face), else return null
	 * @return HalfEdge, null if no boundary edge found
	 */
	public HalfEdge isIdeal() {
		HalfEdge nxtedge=edge;
		do {
			if (nxtedge.twin.face==null || nxtedge.twin.face.faceIndx<0)
				return nxtedge;
			nxtedge=nxtedge.next;
		} while(nxtedge!=edge);
		return null;
	}
	
	/**
	 * Does this face share an edge (not just a vertex) with an ideal face
	 * @return 'HalfEdge', first shared, or null if none shared
	 */
	public HalfEdge isRed() {
		HalfLink bdryedges=getEdges();
		Iterator<HalfEdge> bit=bdryedges.iterator();
		while (bit.hasNext()) {
			HalfEdge he=bit.next();
			if (he.twin.face==null || he.twin.face.faceIndx<0) 
				return he;
		}
		return null;
	}
	
	/**
	 * Return the edge of 'this' face if its twin is
	 * an edge of 'gface', else return null. Should 
	 * work for both normal and ideal faces.
	 * @param gface Face
	 * @return HalfEdge, null if no shared edge
	 */
	public HalfEdge faceNghb(Face gface) {
		HalfEdge he=edge;
		do {
			if (he.twin.face==gface)
				return he;
			he=he.next;
		} while (he!=edge);
		return null;
	}
	
	/**
	 * Return counterclockwise (non-closed) array of vertex indices 
	 * defining this (possibly ideal) face.
	 * TODO: may want to start with particular vertex.
	 * @return int[]
	 */
	public int[] getVerts() {
		ArrayList<Integer> vertlist=new ArrayList<Integer>();
		HalfEdge nxtedge=edge;
		int safety=10000;
		do {
			vertlist.add(nxtedge.origin.vertIndx);
			nxtedge=nxtedge.next;
			safety--;
		} while (nxtedge!=edge && safety>0);
		if (safety==0) 
			throw new CombException("Break out: face "+faceIndx);
		int []ans=new int[vertlist.size()];
		Iterator<Integer> vlst=vertlist.iterator();
		int tick=0;
		while (vlst.hasNext()) {
			ans[tick++]=(int)vlst.next();
		}
		return ans;
	}

	/** 
	 * Count the number of edges
	 * @return int
	 */
	public int getNum() {
		int count=0;
		HalfEdge nxtedge=edge;
		int safety=100;
		do {
			count++;
			nxtedge=nxtedge.next;
			safety--;
		} while (nxtedge!=edge && safety>0);
		if (safety==0) 
			System.out.println("Break out with face "+faceIndx);
		return count;
	}
	
	/**
	 * Return color, null if null
	 * @return new Color
	 */
	public Color getColor() {
		if (color==null)
			return null;
		return ColorUtil.cloneMe(color);
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
	 * Find the index of vertex 'v' in the list of vertices around
	 * this face. Index 0 is index of the origin of 'face.edge'.
	 * @param v int
	 * @return int indx, -1 on error
	 */
	public int getVertIndx(int v) {
		int indx=0;
		HalfEdge nxtedge=edge;
		do {
			if (nxtedge.origin.vertIndx==v)
				return indx;
			indx++;
			nxtedge=nxtedge.next;
		} while (nxtedge!=edge);
		return -1;
	}

	/**
	 * Return ordered array of 'HalfEdge's around the face,
	 * starting with 'he'; list is open, so first edge is not 
	 * repeated at the end.
	 * @return ArrayList<HalfEdge>, null if 'he' not an edge
	 */
	public HalfLink getEdges(HalfEdge he) {
		if (he.face!=this)
			return null;
		HalfLink rslt=new HalfLink();
		HalfEdge nxtedge=he;
		do {
			rslt.add(nxtedge);
			nxtedge=nxtedge.next;
		} while (nxtedge!=he);
		return rslt;
	}

	/**
	 * Return ordered array of 'HalfEdge's around the face;
	 * list is open, so first edge is not repeated at the end.
	 * The first entry is the 'edge' for this face.
	 * @return ArrayList<HalfEdge>
	 */
	public HalfLink getEdges() {
		return getEdges(edge);
	}
	
	/**
	 * clone: CAUTION: pointers may be in conflict or outdated, 
	 * @return new Face
	 */
	public Face clone() {
		Face face=new Face();
		face.color=ColorUtil.cloneMe(color);
		face.edge=edge;
		face.faceIndx=faceIndx;
		face.mark=mark;
		return face;
	}
	
	public String toString() {
		int[] myverts=getVerts();
		StringBuilder sb=new StringBuilder();
		for (int j=0;j<myverts.length;j++)
			sb.append(myverts[j]+" ");
		return sb.toString();
	}
}
