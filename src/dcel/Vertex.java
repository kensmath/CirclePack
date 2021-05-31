package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import exceptions.CombException;
import exceptions.DCELException;
import listManip.HalfLink;

/** 
 * DCEL Vertex only contains combinatorial data; center/rad data are
 * elsewhere, in 'VData's and 'RedHEdge's. The 'vertIndx'  
 * typically aligns with the index in 'PackData'. If this is a 
 * boundary vertex, then its halfedge should be downstream 
 * (counterclockwise) boundary edge. 'redFlag' indicates vertex
 * is in the red chain; 'spokes' is only used during red chain 
 * processing.
 * @author kstephe2, starting in 2016
 *
 */
public class Vertex {
	
	public HalfEdge halfedge;	// a halfedge pointing away from this vert
	public int vertIndx;		// index from associated 'PackData'
	public int bdryFlag;		// 0 for interior, 1 for boundary
	public boolean redFlag;		// is this a vertex along the red chain? 
	
	public int vutil;			// temporary data only
	public HalfEdge[] spokes;	// outgoing spokes -- only during processing
	
	public Vertex() {
		halfedge=null;
		vertIndx=-0;
		redFlag=false;
		spokes=null;
	}
	
	public Vertex(int v) {
		this();
		vertIndx=v;
	}

	/**
	 * Get usual number of faces (not counting ideal)
	 * @return int
	 */
	public int getNum() {
		HalfEdge he=halfedge;
		int tick=0;
		do {
			tick++;
			he=he.prev.twin;
		} while (he!=halfedge);
		return tick-bdryFlag; // subtract 1 for bdry vertex
	}

	/**
	 * A 'Vertex' is considered a boundary vertex if one of its
	 * edges is a boundary edge (it or its twin has an ideal face).
	 * @return boolean
	 */
	public boolean isBdry() {
		HalfLink flower=getEdgeFlower();
		Iterator<HalfEdge> fit=flower.iterator();
		while (fit.hasNext()) {
			HalfEdge he=fit.next();
			if (he.isBdry())
				return true;
		}
		return false;
	}
	
	/**
	 * Return normal cclw flower of nghb indices, closed if interior.
	 * Get it by chasing spokes, which will close up whether bdry or
	 * interior, so have to use 'bdryFlag'.
	 * If this is a 'RedVertex', call 'getRedFlower'; it may be interior
	 * or boundary. If not a 'RedVertex', it should be interior and we
	 * get the flower in the usual way, starting with 'halfedge'.
	 * @return int[]
	 */
	public int[] getFlower() {
		ArrayList<Integer> vlist=new ArrayList<Integer>();
		HalfEdge he=halfedge;
		int safety=1000;
		do {
			vlist.add(he.twin.origin.vertIndx);
			he=he.prev.twin;
			safety--;
		} while (he!=halfedge && safety>0);
		if (safety==0) 
			throw new DCELException("triggered safety exit.");
		if (bdryFlag==0)
			vlist.add(halfedge.twin.origin.vertIndx); // close up

		int[] flower=new int[vlist.size()];
		Iterator<Integer> vit=vlist.iterator();
		int tick=0;
		while (vit.hasNext()) {
			flower[tick++]=vit.next();
		}
		
		return flower;
	}
	
	/**
	 * Get cclw 'HalfEdge's, "spokes", out of this vertex
	 * starting with 'start' ('halfedge' by default).
	 * @param start HalfEdge (could be null)
	 * @return HalfLink
	 */
	public HalfLink getSpokes(HalfEdge start) {
		HalfEdge he=halfedge;
		if (start!=null && start.origin==this)
			he=start;
		int num=getNum();
		if (he.origin.bdryFlag>0) // bdry?
			num++;
		HalfLink ans=new HalfLink();
		for (int j=0;j<num;j++) {
			ans.add(he);
			he=he.prev.twin;
		}
		return ans;
	}
		
	/**
	 * Get cclw ordered vector of "spokes", 'HalfEdge's with this
	 * vertex as origin, starting with 'halfedge'. Expect
	 * that if vertex is bdry, first in list will have 
	 * ideal 'twin.face' and last will have ideal 'face'.
	 * @return ArrayList<HalfEdge> or null on error
	 */
	public HalfLink getEdgeFlower() {
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
	public HalfLink getEdgeFlower(HalfEdge start,HalfEdge stop) {
		if (start==null) {
			return getEdgeFlower(); // full flower, start at its 'halfedge'
		}
		if (start.origin==null || start.origin!=this)
			throw new CombException("'start' not appropriate");
		if (stop==null)
			stop=start;
		
		// add spokes including 'start', up to, not including 'stop'
		HalfLink eflower=new HalfLink();
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
	 * Get cclw list of 'HalfEdge's surrounding the union of
	 * faces incident to this 'Vertex', including edges 
	 * surrounding any incident ideal face. List is open.
	 * @param start HalfEdge, with this 
	 * @param stop HalfEdge, with this
	 * @return ArrayList<HalfEdge>, null if start==stop
	 */
	public HalfLink getOuterEdges() {
		HalfLink eflower=getEdgeFlower();
		HalfLink outer=new HalfLink();
		Iterator<HalfEdge> eit=eflower.iterator();
		int safety=100*eflower.size();
		while (eit.hasNext() && safety>0) {
			HalfEdge spoke=eit.next();
			HalfEdge he=spoke.next;
			do {
				if (he.twin.origin==this)
					break;
				outer.add(he);
				he=he.next;
				safety--;
			} while (he!=spoke.next);
		}
		if (safety==0)
			throw new CombException("looped in 'getOuterEdges'");
		return outer;
	}
	
	/**
	 * Get cclw ordered open vector of neighboring faces; this
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

	/** return the index of the vertex opposite 'halfedge'
	 * @return int
	 */
	public int getOpposite() {
		return halfedge.next.twin.origin.vertIndx;
	}

	public String toString() {
		return new String(""+vertIndx);
	}
	
	/**
	 * Clone: caution, 'halfedge' and 'redFlag' may be outdated.
	 * @return new Vertex
	 */
	public Vertex clone() {
		Vertex nv=new Vertex(vertIndx);
		nv.halfedge=halfedge;
		nv.bdryFlag=bdryFlag;
		nv.redFlag=redFlag;
		nv.vutil=vutil;
		return nv;
	}

}
