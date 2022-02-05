package dcel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

import complex.Complex;
import exceptions.CombException;
import exceptions.DCELException;
import geometry.CircleSimple;
import listManip.HalfLink;
import util.ColorUtil;

/** 
 * DCEL Vertex contains combinatorial, geometric, and other
 * miscellaneous data. Center information may be overridden
 * by data in 'RedHEdge's. If this is a boundary vertex, 
 * its halfedge should be the downstream (cclw) boundary edge. 
 * 'redFlag' indicates vertex is in the red chain; 'spokes' is 
 * only used during red chain processing.
 * @author kstephe2, starting in 2016
 *
 */
public class Vertex {
	
	// combinatoric
	public HalfEdge halfedge;	// a halfedge pointing away from this vert
	public int vertIndx;		// index from associated 'PackData'
	public int bdryFlag;		// 0 for interior, 1 for boundary
	public boolean redFlag;		// is this a vertex along the red chain?
	
	// geometric
	public Complex center;	// center as complex number 
	public double rad;		// radius of circle (note: x-radius form in hyp case
							//    namely, x=1-exp(-2h) for hyp radius h finite; 
							//    for h infinite (horocycle) stores negative of 
							//    eucl radius (if computed) for plotting convenience.
							//    (x_radius, x_radii, x_rad, x-radius, x-radii, x-rad)
	public double aim;		// desired angle sum at this vertex (actual angle, not divided by Pi)
	public double curv;	    // angle sum at this vertex. 

	// other
	public Color color;
	public int mark;
	public int plotFlag;	// often OBE
	
	// for temporary data during some processing
	public int vutil;
	public HalfEdge[] spokes;
	
	public Vertex(int v) {
		vertIndx=v;
		halfedge=null;
		vertIndx=-1;
		redFlag=false;
		spokes=null;
		center=new Complex(0.0);
		rad=.05;
		color=ColorUtil.getFGColor();
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
	 * If 'spoke.origin' v is interior, has an even number
	 * of spokes, this returns the opposite spoke. If v bdry,
	 * this returns the "most-opposite" bdry spoke, bias
	 * to downstream bdry edge in case of a tie. 
	 * 
	 * If 'hexflag', then interior v must have 6 spokes
	 * and if v is bdry, then if it has 3 faces and 
	 * 'spoke' itself is bdry, return the other bdry spoke.
	 * 
	 * (see 'axis_proj').
	 * @param spoke HalfEdge
	 * @param hexflag boolean
	 * @return HalfEdge or null on failure 
	 */
	public static HalfEdge oppSpoke(HalfEdge spoke,boolean hexflag) {
		Vertex vert=spoke.origin;
		int safety=2000;
		HalfEdge cclw=spoke;
		HalfEdge clw=spoke;
		int N=vert.getNum();
		
		// interior case
		if (vert.bdryFlag==0) {
			if (hexflag && N!=6)
				return null;
			do {
				safety--;
				cclw=cclw.prev.twin;
				clw=clw.twin.next;
				if (cclw==clw && cclw!=spoke)
					return cclw;
			} while (cclw!=spoke && clw!=spoke && safety>0);
			if (safety==0)
				throw new CombException("safety value blew in looking for opposite to "+spoke);
			return null;
		}
		
		if (hexflag && (N!=3 || !spoke.isBdry())) 
				return null;

		// else 'vert' is bdry: first check if 'spoke' is bdry
		if (spoke==vert.halfedge) // downstream bdry edge?
			return spoke.twin.next.twin; // upstream bdry
		if (spoke.twin.isBdry()) // upstream bdry edge?
			return vert.halfedge; // downstream bdry

		// else spoke points to interior
		do {
			safety--;
			cclw=cclw.prev.twin;
			clw=clw.twin.next;
			if (cclw.origin.bdryFlag>0)
				return vert.halfedge;
			else if (clw.origin.bdryFlag>0)
				return vert.halfedge.twin.next.twin;
		} while (safety>0);
		if (safety==0)
			throw new CombException("inconsistency looking for bdry oppSpoke");
		return null;
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
	 * Return traditional cclw flower of petals, closed if
	 * interior. 
	 * @return int[]
	 */
	public int[] getPetals() {
		return getFlower(false);
	}
	
	/**
	 * Return normal cclw flower of nghb indices.
	 * Get it by chasing spokes, which will close up whether bdry or
	 * interior, so have to use 'bdryFlag'.
	 * If this is a 'RedVertex', call 'getRedFlower'; it may be interior
	 * or boundary. If not a 'RedVertex', it should be interior and we
	 * get the flower in the usual way, starting with 'halfedge'.
	 * @param closeInt boolean; true, then close up if interior
	 * @return int[]
	 */
	public int[] getFlower(boolean closeInt) {
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
		
		// close up interior flowers?
		if (closeInt && bdryFlag==0)
			vlist.add(halfedge.twin.origin.vertIndx); 

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
	 * vertex as origin, starting with 'halfedge'. Do not close up.
	 * Expect that if vertex is bdry, first in list will have 
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
	public ArrayList<DcelFace> getFaceFlower() {
		ArrayList<DcelFace> fflower=new ArrayList<DcelFace>();
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
	 * get clone of color
	 * @return new Color
	 */
	public Color getColor() {
		return ColorUtil.cloneMe(color);
	}
	
	/**
	 * Get circle data
	 * @return CircleSimple
	 */
	public CircleSimple getCircleSimple() {
		return new CircleSimple(center,rad);
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
		nv.center=new Complex(center);
		nv.rad=rad;
		nv.aim=aim;
		nv.color=ColorUtil.cloneMe(color);
		nv.mark=mark;
		nv.curv=curv;
		nv.plotFlag=plotFlag;
		nv.vutil=vutil;
		return nv;
	}
	
	/**
	 * Copy data to 'this' from 'sourceV'
	 * @param sourceV Vertex
	 */
	public void cloneData(Vertex sourceV) {
		center=new Complex(sourceV.center);
		rad=sourceV.rad;
		aim=sourceV.aim;
		curv=sourceV.curv;
		mark=sourceV.mark;
		color=ColorUtil.cloneMe(sourceV.color);
	}

}
