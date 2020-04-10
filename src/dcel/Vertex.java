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
	public Complex center;		// rarely set; e.g., when getting dual loactions
								//  (normally, get center from 'PackData' using 'vertIndex'

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
	 * Return flower of 'Vertex's 
	 * @return
	 */
	public ArrayList<Vertex> getVertexFlower() {
		ArrayList<Vertex> vertlist=new ArrayList<Vertex>();
		boolean bdry=false; // is this a bdry vert?
		HalfEdge misb=null; // set if bdry is misplaced
		
		// yes, first edge is a bdry edge
		if (halfedge.twin.face==null)
			bdry=true;
		HalfEdge nxtedge=halfedge;
		do {
			vertlist.add(nxtedge.twin.origin);
			if (nxtedge.twin.face==null) {
				if (!bdry) {
					bdry=true;
					misb=nxtedge; // meaning: misplaced bdry
				}
			}
			nxtedge=nxtedge.prev.twin;
		} while (nxtedge!=halfedge);
		if (!bdry)
			vertlist.add(halfedge.twin.origin); // close up
		
		// misplaced bdry detected? start vertlist over 
		if (misb!=null) {
			nxtedge=misb;
			do {
				vertlist.add(nxtedge.twin.origin);
				if (nxtedge!=misb && nxtedge.twin.face==null) {
					return null;
				}
				nxtedge=nxtedge.twin.next;
			} while (nxtedge!=misb);
			
		}
		
		return vertlist;
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
