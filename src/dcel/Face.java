package dcel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

import exceptions.CombException;
import panels.CPScreen;

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
	Color color;
	
	// Constructor
	public Face() {
		edge=null;
		faceIndx=0;
		color=CPScreen.getFGColor();
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
		ArrayList<HalfEdge> bdryedges=getEdges();
		Iterator<HalfEdge> bit=bdryedges.iterator();
		while (bit.hasNext()) {
			HalfEdge he=bit.next();
			if (he.twin.face==null || he.twin.face.faceIndx<0) 
				return he;
		}
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
		int safety=100;
		do {
			vertlist.add(nxtedge.origin.vertIndx);
			nxtedge=nxtedge.next;
			safety--;
		} while (nxtedge!=edge && safety>0);
		if (safety==0) 
			System.out.println("Break out with face "+faceIndx);
		int []ans=new int[vertlist.size()];
		Iterator<Integer> vlst=vertlist.iterator();
		int tick=0;
		while (vlst.hasNext()) {
			ans[tick++]=(int)vlst.next();
		}
		return ans;
	}
	
	public Color getColor() {
		return new Color(color.getRed(),color.getGreen(),color.getBlue(),color.getAlpha());
	}
	
	public void setColor(Color col) {
		color=new Color(col.getRed(),col.getGreen(),col.getBlue(),col.getAlpha());
	}
	

	/**
	 * Return ordered array of 'HalfEdge's around the face
	 * @return ArrayList<HalfEdge>
	 */
	public ArrayList<HalfEdge> getEdges() {
		ArrayList<HalfEdge> rslt=new ArrayList<HalfEdge>();
		HalfEdge nxtedge=edge;
		do {
			rslt.add(nxtedge);
			nxtedge=nxtedge.next;
		} while (nxtedge!=edge);
		return rslt;
	}
	
	public String toString() {
		int[] myverts=getVerts();
		StringBuilder sb=new StringBuilder();
		for (int j=0;j<myverts.length;j++)
			sb.append(myverts[j]+" ");
		return sb.toString();
	}
}
