package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import exceptions.CombException;
import exceptions.DCELException;

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
	
	public int util;			// temporary data only
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
	 * array of cclw halfedges out of this vertex.
	 * @return
	 */
	public HalfEdge[] getSpokes() {
		int num=getNum();
		HalfEdge[] spokes=new HalfEdge[num];
		HalfEdge he=halfedge;
		for (int j=0;j<num;j++) {
			spokes[j]=he;
			he=he.prev.twin;
		}
		return spokes;
	}
		
	/**
	 * Get cclw ordered vector of "spokes", 'HalfEdge's with this
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
		nv.util=util;
		return nv;
	}

}
