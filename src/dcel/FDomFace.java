package dcel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import exceptions.CombException;

/**
 * 'FDom*' is for 'fundamental domain'. 
 * Part of effort for managing covering spaces using DCEL structures.
 * 
 * @author kstephe2 Aug 2019
 *
 */
public class FDomFace {

	public FDomHalfEdge edge; // 'this' is on the left of its halfedge
	public int vcount;        // number of bdry edges
	public int faceIndx;      // utility index for this face	
	public char []codes;      // side-pairs, a A b B c B ... etc.
	
	// Constructor
	public FDomFace(int n) {
		edge=null;
		faceIndx=0;
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
		FDomHalfEdge he=edge;
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
	public FDomHalfEdge isIdeal() {
		FDomHalfEdge nxtedge=edge;
		do {
			if (nxtedge.twin.face==null || nxtedge.twin.face.faceIndx<0)
				return nxtedge;
			nxtedge=nxtedge.next;
		} while(nxtedge!=edge);
		return null;
	}
	
	/**
	 * return counterclockwise (non-closed) list of vertices 
	 * defining this face.
	 * TODO: may want to start with particular vertex.
	 * @return int[]
	 */
	public int[] getVerts() {
		List<Integer> vertlist=new ArrayList<Integer>();
		FDomHalfEdge nxtedge=edge;
		do {
			vertlist.add(nxtedge.origin.vertIndx);
			nxtedge=nxtedge.next;
		} while (nxtedge!=edge);
		int []ans=new int[vertlist.size()];
		Iterator<Integer> vlst=vertlist.iterator();
		int tick=0;
		while (vlst.hasNext()) {
			ans[tick++]=(int)vlst.next();
		}
		return ans;
	}

	/**
	 * Return ordered array of 'HalfEdge's around the face
	 * @return ArrayList<HalfEdge>
	 */
	public ArrayList<FDomHalfEdge> getEdges() {
		ArrayList<FDomHalfEdge> rslt=new ArrayList<FDomHalfEdge>();
		FDomHalfEdge nxtedge=edge;
		do {
			rslt.add(nxtedge);
			nxtedge=nxtedge.next;
		} while (nxtedge!=edge);
		return rslt;
	}
}
