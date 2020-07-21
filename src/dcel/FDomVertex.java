package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import complex.Complex;
import exceptions.CombException;

/**
 * 'FDom*' is for 'fundamental domain'. 
 * Part of effort for managing covering spaces using DCEL structures.

 * FDomVertex is associated with a vertex of a fundamental region.
 * The index points to the associated 'PackData' vertex; this helps 
 * get concrete geometric info, e.g., circle centers. 
 * If this is a boundary vertex, then its FDomHalfedge should point 
 * to downstream (counterclockwise) bdry neighbor.
 * @author kstephe2, 8/2019
 *
 */
public class FDomVertex {
	
	public FDomHalfEdge halfedge;	// a halfedge pointing away from this vert
	public int vertIndx;		// index from associated 'PackData'
	public Complex center;		// rarely set; e.g., when getting dual loactions
								//  (normally, get center from 'PackData' using 'vertIndex'

	public FDomVertex() {
		halfedge=null;
		vertIndx=-1;
		center=null;
	}

	/**
	 * A 'FDomVertex' is considered a boundary vertex if one of its
	 * edges is a boundary edge (it or its twin has an ideal face).
	 * @return boolean
	 */
	public boolean isBdry() {
		ArrayList<FDomHalfEdge> flower=getEdgeFlower();
		Iterator<FDomHalfEdge> fit=flower.iterator();
		while (fit.hasNext()) {
			FDomHalfEdge he=fit.next();
			if (he.isBdry())
				return true;
		}
		return false;
	}
	
	/**
	 * Return flower of 'Vertex's 
	 * @return
	 */
	public ArrayList<FDomVertex> getVertexFlower() {
		ArrayList<FDomVertex> vertlist=new ArrayList<FDomVertex>();
		boolean bdry=false; // is this a bdry vert?
		FDomHalfEdge misb=null; // set if bdry is misplaced
		
		// yes, first edge is a bdry edge
		if (halfedge.twin.face==null)
			bdry=true;
		FDomHalfEdge nxtedge=halfedge;
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
	public ArrayList<FDomHalfEdge> getEdgeFlower() {
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
	public ArrayList<FDomHalfEdge> getEdgeFlower(FDomHalfEdge start,FDomHalfEdge stop) {
		if (start==null) {
			return getEdgeFlower(); // full flower, start at its 'halfedge'
		}
		if (start.origin==null || start.origin!=this)
			throw new CombException("'start' not appropriate");
		if (stop==null)
			stop=start;
		
		// add spokes including 'start', up to, not including 'stop'
		ArrayList<FDomHalfEdge> eflower=new ArrayList<FDomHalfEdge>();
		FDomHalfEdge nxtedge=start;
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
	public ArrayList<FDomHalfEdge> getOuterEdges(FDomHalfEdge start, FDomHalfEdge stop) {
		if (start==null || stop==null)
			throw new CombException("bad start/stop data");
		if (start==stop) // no edges --- legitimate in some situations
			return null;
		ArrayList<FDomHalfEdge> eflower=getEdgeFlower(start,null);
		ArrayList<FDomHalfEdge> outer=new ArrayList<FDomHalfEdge>();
		Iterator<FDomHalfEdge> eit=eflower.iterator();
		int safety=100*eflower.size();
		FDomHalfEdge he=null;
		while (eit.hasNext() && (he=eit.next())!=stop && safety>0) {
			FDomHalfEdge nxtedge=halfedge.next;
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
	public ArrayList<FDomFace> getFaceFlower() {
		ArrayList<FDomFace> fflower=new ArrayList<FDomFace>();
		FDomHalfEdge nxtedge=halfedge;
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
	public FDomVertex clone() {
		FDomVertex nv=new FDomVertex();
		nv.halfedge=halfedge;
		nv.vertIndx=vertIndx;
		return nv;
	}

}
