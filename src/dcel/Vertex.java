package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import complex.Complex;
import exceptions.CombException;

/** DCEL Vertex. The 'vertIndx' is typically the index of the
 * vertex in the associated 'PackData'; this is how we get 
 * concrete geometric info, e.g., circle centers. 
 * If this is a boundary vertex, then its halfedge should point 
 * to downstream (counterclockwise) 
 * bdry neighbor.
 * @author kstephe2, 2016
 *
 */
public class Vertex {
	
	public HalfEdge halfedge;	// a halfedge pointing away from this vert
	public int vertIndx;		// index from associated 'PackData'
	// TODO: deciding on whether to keep data here or, as usual, in packData
	public Complex center;		
	public double rad;

	public Vertex() {
		halfedge=null;
		vertIndx=-1;
		center=null;
	}

	/**
	 * A 'Vertex' is considered a boundary vertex if one of its
	 * edges is a boundary edge (it or its twin has an ideal face).
	 * @return boolean
	 */
	public boolean isBdry() {
		ArrayList<HalfEdge> flower=getEdgeFlower();
		Iterator<HalfEdge> fit=flower.iterator();
		while (fit.hasNext()) {
			HalfEdge he=fit.next();
			if (he.isBdry())
				return true;
		}
		return false;
	}
	
	/**
	 * return traditional flower of neighbor indices, closed is not bdry.
	 * Careful: due to some edges possibly being 'RedHEdges's, the normal
	 * chasing edge method may loop, so we use indices to keep track.	
	 * @return int[]
	 */
	public int[] getFlower() {
		ArrayList<Integer> vlist=new ArrayList<Integer>();
		boolean bdry=false; // is this a bdry vertex?
		boolean backfill=false; // set if we need to fill clw
		
		int firstV=halfedge.twin.origin.vertIndx;
		vlist.add(firstV);
		HalfEdge nxtedge=halfedge;

		// common bdry case: first edge twin is a bdry edge. get rest cclw
		if (nxtedge.twin.face==null || nxtedge.twin.face.faceIndx<0) {
			bdry=true;
			while (nxtedge.face!=null && nxtedge.face.faceIndx>=0) {
				vlist.add(nxtedge.prev.origin.vertIndx);
				nxtedge=nxtedge.prev.twin;
			}
		}
		// first edge itself is bdry edge? Then just backfill
		else if (nxtedge.face==null || nxtedge.face.faceIndx<0) {
			bdry=true;
			backfill=true;
		}
		// else add cclw until hitting bdry or returning to firstV
		else {
			int currV=nxtedge.prev.origin.vertIndx;
			while (currV!=firstV && !bdry) {
				vlist.add(currV);
				nxtedge=nxtedge.prev.twin;
				currV=nxtedge.prev.origin.vertIndx;
				if (nxtedge.face==null || nxtedge.face.faceIndx<0) 
					bdry=true;
			}
			if (currV==firstV) { // close up, interior vertex
				backfill=false;
				vlist.add(currV);
			}
			else
				backfill=true;
		}

		// bdry vertex, rest of flower clw  
		if (backfill) {
			nxtedge=halfedge.twin.next;
			int upV=nxtedge.twin.origin.vertIndx;
			while(upV!=firstV && nxtedge.face!=null && nxtedge.face.faceIndx>=0) {
				vlist.add(upV);
				nxtedge=nxtedge.twin.next;
				upV=nxtedge.twin.origin.vertIndx;
			}
		}			

		int[] flower=new int[vlist.size()];
		for (int j=0;j<vlist.size();j++)
			flower[j]=vlist.get(j);
		
		return flower;
	}

	/**
	 * Get cclw ordered vector of 'HalfEdge's with this
	 * vertex as origin, starting with 'halfedge'. Expect
	 * that if vertex is bdry, first in list will have 
	 * ideal 'twin.face' and last will have ideal 'face'.
	 * @return ArrayList<HalfEdge> or null on error
	 */
	public ArrayList<HalfEdge> getEdgeFlower() {
		if (halfedge==null)
			throw new CombException("Vertex has no 'halfedge'");
		return getEdgeFlower(halfedge,null);
	}
	
	/**
	 * Get cclw ordered vector of spokes, that is, 'HalfEdge's 
	 * with this as origin. Include 'start' and go counterclockwise
	 * to 'stop', but 'stop' is not included. (E.g. not closed list.)
	 * @param start HalfEdge, if null, start at 'halfedge'
	 * @param stop HalfEdge, if null, set 'stop' = 'start'
	 * @return ArrayList<HalfEdge> or null on error
	 */
	public ArrayList<HalfEdge> getEdgeFlower(HalfEdge start,HalfEdge stop) {
		if (start==null) {
			return getEdgeFlower(); // full flower, start at its 'halfedge'
		}
		if (start.origin==null || start.origin!=this)
			throw new CombException("'start' not appropriate");
		if (stop==null)
			stop=start;
		
		// add spokes including 'start', up to, not including 'stop'
		ArrayList<HalfEdge> eflower=new ArrayList<HalfEdge>();
		HalfEdge nxtedge=start;
		int safety=1000;
		do {
			eflower.add(nxtedge);
			nxtedge=nxtedge.prev.twin;
			safety--;
		} while (nxtedge!=stop && safety>0);
		if (safety==0) 
			throw new CombException("loop in getEdgeFlower for vert "+vertIndx);
		return eflower;
	}
	
	/**
	 * Get list of 'HalfEdge's surrounding the union of
	 * faces incident to this 'Vertex', counterclockwise.
	 * Note this includes any ideal faces. 
	 * @param start HalfEdge with this as origin
	 * @param stop HalfEdge with this as origin
	 * @return ArrayList<HalfEdge>, null if start==stop
	 */
	public ArrayList<HalfEdge> getOuterEdges(HalfEdge start, HalfEdge stop) {
		if (start==null || stop==null)
			throw new CombException("bad start/stop data");
		if (start==stop) // no edges --- legitimate in some situations
			return null;
		ArrayList<HalfEdge> eflower=getEdgeFlower(start,null);
		ArrayList<HalfEdge> outer=new ArrayList<HalfEdge>();
		Iterator<HalfEdge> eit=eflower.iterator();
		int safety=100*eflower.size();
		HalfEdge he=null;
		while (eit.hasNext() && (he=eit.next())!=stop && safety>0) {
			HalfEdge nxtedge=halfedge.next;
			do {
				outer.add(nxtedge);
				nxtedge=nxtedge.next;
				safety--;
			} while (nxtedge!=he.prev);
		}
		if (safety==0)
			throw new CombException("looped in 'getOuterEdges'");
		return outer;
	}
	
	/**
	 * Get cclw ordered vector of neighboring faces; this
	 * will include an ideal face if bdry vertex. Normally
	 * if vertex is bdry, last (and only last) face in list 
	 * might be an ideal face, but only calling routine will
	 * know.
	 * @return ArrayList<Face> or null on error
	 */
	public ArrayList<Face> getFaceFlower() {
		ArrayList<Face> fflower=new ArrayList<Face>();
		HalfEdge nxtedge=halfedge;
		do {
			fflower.add(nxtedge.face);
			nxtedge=nxtedge.prev.twin;
		} while (nxtedge!=halfedge);
		return fflower;

	}

	/**
	 * Clone: caution, 'halfedge' pointer may be outdated.
	 * @return new Vertex
	 */
	public Vertex clone() {
		Vertex nv=new Vertex();
		nv.halfedge=halfedge;
		nv.vertIndx=vertIndx;
		return nv;
	}

}
