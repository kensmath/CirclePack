package workshops;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CPBase;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.PackDCEL;
import dcel.Schwarzian;
import exceptions.CombException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.CommonMath;
import listManip.FaceLink;
import listManip.HalfLink;
import math.Mobius;
import util.TriAspect;

/**
 * Static routines for doing various layout processes.
 * 
 * @author kstephe2
 *
 */
public class LayoutShop {

	/**
	 * Given chain 'hlink' (generally closed), return the list of vertex centers. If
	 * any red vertices are encounted then polygon may cross side-paired edges, so
	 * we find the chain of faces to its left (closed if hlink is closed) and use
	 * these in succession to recompute the centers along hlink; the resulting list
	 * is not closed. Assume first halfedge from hlink is in place and work from
	 * there. If edges in hlink are not contiguous, work with the maximal initial
	 * contiguous subsegment.
	 * 
	 * @param pdcel PackDCEL.
	 * @param hlink HalfLink
	 * @param hes   int
	 * @return ArrayList<Complex>, null on error
	 */
	public static ArrayList<Complex> layoutPolygon(PackDCEL pdcel, HalfLink hlink, int hes) {
		if (hlink == null || hlink.size() <= 1)
			return null;
		ArrayList<Complex> Zlist = new ArrayList<Complex>();

		// check if any red vertices are involved
		boolean nored = true;
		Iterator<HalfEdge> his = hlink.iterator();
		while (his.hasNext() && nored) {
			HalfEdge he = his.next();
			if (he.origin.redFlag)
				nored = false;
		}

		// easy case, accumulate centers
		if (nored) {
			his = hlink.iterator();
			while (his.hasNext()) {
				HalfEdge he = his.next();
				Zlist.add(pdcel.getVertCenter(he));
			}
			// if not closed, add last vertex
			if (hlink.getLast().twin.origin != hlink.getFirst().origin)
				Zlist.add(pdcel.getVertCenter(hlink.getLast().next));
			return Zlist;
		}

		// hard case; need layout to recompute centers

		// if closed, don't need last edge
		if (hlink.getLast().twin.origin == hlink.getFirst().origin)
			hlink.removeLast();
		HalfLink leftlink = HalfLink.leftsideLink(pdcel, hlink);
		if (leftlink == null)
			return null;

		// create triAspects for these faces
		ArrayList<TriAspect> aspectlist = new ArrayList<TriAspect>();
		his = leftlink.iterator();
		while (his.hasNext()) {
			aspectlist.add(new TriAspect(pdcel, his.next().face));
		}

		// start with both ends of the first edge
		HalfEdge firsthe = hlink.getFirst();
		Vertex firstV = firsthe.origin;
		Zlist.add(pdcel.getVertCenter(firsthe));
		Zlist.add(pdcel.getVertCenter(firsthe.next));

		// call routine to layout the chain of TriAspects
		int j = aspectlist.get(0).vertIndex(firstV.vertIndx);
		layTriAspects(aspectlist, j);

		// look for successive ends of hlink edges in aspectlist
		his = hlink.iterator();
		his.next(); // toss first edge
//		Iterator<HalfEdge> llits=leftlink.iterator();
		Iterator<TriAspect> tais = aspectlist.iterator();
		while (his.hasNext()) {
			int V = his.next().next.origin.vertIndx; // next vert to find
			boolean hit = false;
			while (tais.hasNext() && !hit) {
				TriAspect triaspect = tais.next();
				int k = -1;
				if ((k = triaspect.vertIndex(V)) >= 0) {
					hit = true;
					Zlist.add(triaspect.center[k]);
				}
			}
			if (!hit)
				throw new CombException("didn't find center for V in TriAspects");
		}

		return Zlist;
	}

	/**
	 * Given a chain of contiguous 'TriAspect's, assume the j edge of the first is
	 * already placed, compute its third center, then continue along aspects setting
	 * shared centers and fixing third centers. Note: new centers end up in the
	 * TriAspect's, but are not updated anywhere else.
	 * 
	 * @param aspects ArrayList<TriAspect>
	 * @param j       int
	 */
	public static void layTriAspects(ArrayList<TriAspect> aspects, int j) {
		Iterator<TriAspect> tais = aspects.iterator();
		TriAspect nextasp = tais.next();
		TriAspect lastasp = nextasp;

		// set only the third center of first TriAspect
		CircleSimple cs = lastasp.compOppCircle(j,false);
		lastasp.center[(j + 2) % 3] = cs.center;

		// now successively adjust the next TriAspect's centers
		while (tais.hasNext()) {
			TriAspect asp = tais.next(); // compute centers for this
			int J = asp.nghb_Tri(lastasp);
			if (J < 0)
				throw new CombException("somehow, chain of aspects is broken," + " face" + asp.faceIndx);
			int v = asp.vert[J];
			int w = asp.vert[(J + 1) % 3];
			int oldv = lastasp.vertIndex(v);
			int oldw = lastasp.vertIndex(w);
			asp.center[J] = new Complex(lastasp.center[oldv]); // reset shared centers
			asp.center[(J + 1) % 3] = new Complex(lastasp.center[oldw]);
			cs = asp.compOppCircle(J,false);
			asp.center[(J + 2) % 3] = cs.center;
			lastasp = asp;
		}
	}

	/**
	 * Recompute centers along list of faces; only do contiguous faces, and assume
	 * first is already in place.
	 * 
	 * @param pdcel    PackDCEL
	 * @param facelist FaceLink
	 * @param hes      int
	 * @param useSchw  boolean, true, use schwarzians instead of radii
	 * @return int last face index, 0 on failure
	 */
	public static int layoutFaceList(PackDCEL pdcel,FaceLink facelist,
			int hes,boolean useSchw) {
		if (facelist == null || facelist.size() == 0)
			return 0;
		Iterator<Integer> fis = facelist.iterator();
		DcelFace last_face = pdcel.faces[fis.next()];
		while (fis.hasNext()) {
			int g = fis.next(); // next face
			DcelFace next_face = pdcel.faces[g];
			HalfEdge he = last_face.faceNghb(next_face).twin;

			// shuck non-neighbors
			while (he == null && fis.hasNext()) {
				next_face = pdcel.faces[fis.next()];
				he = last_face.faceNghb(next_face);
			}

			// note order: we use centers for 'he' as an edge
			// of 'last_face', so 'he.twin' is the base in
			// the next face.
			TriAspect ftri=null;
			TriAspect gtri=null;
			int prev_g=-1;
			if (he != null && next_face.faceIndx>0) {
				if (last_face.faceIndx==prev_g)
					ftri=gtri;
				else
					ftri=new TriAspect(pdcel,last_face);
				gtri=new TriAspect(pdcel,next_face);
				int ans=-1;
				if (useSchw) {
					double s=he.getSchwarzian();
					ans=workshops.LayoutShop.schwPropogate(ftri, gtri,
							he,s,1);
				}
				else {
					double rad=pdcel.getVertRadius(he.twin.prev);
					ans=workshops.LayoutShop.radPropogate(ftri, gtri,
							he,rad,1);
				}
				
				if (ans>0) {
					int j=gtri.vertIndex(he.twin.prev.origin.vertIndx);
					pdcel.setCent4Edge(he.twin.next.next, gtri.center[j]);
					pdcel.setRad4Edge(he.twin.next.next, gtri.radii[j]);
				}
			}
		} // end of while

		return last_face.faceIndx;
	}

	/**
	 * Update given 'gtri' g using nghb'ing 'ftri' and the 
	 * opposite radius 'rad' in g. Note that 'edge' is 
	 * an edge of f, 'edge.twin' an edge of g. 'gtri' is
	 * updated with data with f and with new data for opposite
	 * vertex.
	 * 
	 * Notes: 
	 *   + want 'gtri' because it may contain invDist's
	 *   + if 'edge' is red with twinred, then the data on
	 *     shared edge may differ in f and g (e.g. in affine
	 *     structure case). Calling routine may want to apply
	 *     a Mobius to align the shared edge and thus get
	 *     updated 'rad' before calling this routine. 
	 *   + For mode=2, substitute 'labels' for 'radii'
	 *   
	 * @param ftri TriAspect
	 * @param gtri TriAspect
	 * @param fedge HalfEdge, edge of face f
	 * @param rad  double, radius (or label)
	 * @param mode int, mode=2 use 'labels'
	 * @return int, -1 on error
	 */
	public static int radPropogate(TriAspect ftri, TriAspect gtri,
			HalfEdge fedge,double rad,int mode) {
		if (fedge.isBdry())
			return -1;
		int fv = ftri.edgeIndex(fedge);
		if (fv < 0)
			throw new ParserException("TriAspect does not contain given "
					+ "HalfEdge " + fedge);
		int fw = (fv+1)%3;
		int gw=gtri.edgeIndex(fedge.twin);
		int gv=(gw+1)%3;
		int gopp=(gw+2)%3;

		// copy f data for shared vertices with g
		gtri.setCenter(ftri.getCenter(fw), gw);
		gtri.setCenter(ftri.getCenter(fv), gv);
		CircleSimple cs=null;
		if (mode == 2) {
			gtri.setLabel(rad,gopp);
			gtri.setLabel(ftri.getLabel(fw), gw);
			gtri.setLabel(ftri.getLabel(fv), gv);
			gtri.setLabel(rad,gopp);
			cs = gtri.compOppCircle(gw,true); // computation uses labels
		} else {
			gtri.setRadius(ftri.getRadius(fw), gw);
			gtri.setRadius(ftri.getRadius(fv), gv);
			gtri.setRadius(rad,gopp);
			cs = gtri.compOppCircle(gw,false); 
		}
		gtri.setCenter(cs.center, gopp);
		if (mode == 2)
			gtri.setLabel(cs.rad, gopp);
		else
			gtri.setRadius(cs.rad, gopp);

		return 1;
	}

	/**
	 * Update given 'gtri' g using nghb'ing 'ftri' and the
	 * schwarzian for 'edge' (which is an edge of f). 'gtri' is
	 * updated with data with f and with new data for opposite
	 * vertex.
	 * 
	 * Notes: 
	 *   + if 'edge' is red with twinred, then the data on
	 *     shared edge may differ in f and g (e.g. in affine
	 *     structure case). Calling routine may want to apply
	 *     a Mobius to align the shared edge and thus get
	 *     updated 'rad' before calling this routine. 
	 *   + For mode=2, substitute 'labels' for 'radii'
	 *   
	 * @param ftri TriAspect
	 * @param gtri TriAspect
	 * @param fedge HalfEdge, edge of face f
	 * @param s double, schwarzian
	 * @param mode int, mode=2 use 'labels'
	 * @return int, -1 on error
	 */
	public static int schwPropogate(TriAspect ftri,TriAspect gtri,
			HalfEdge fedge,double s, int mode) {
		if (fedge.isBdry())
			return -1;
		int fv = ftri.edgeIndex(fedge);
		if (fv < 0)
			throw new ParserException("TriAspect does not contain given "
					+ "HalfEdge " + fedge);
		int fw = (fv+1)%3;
		int gw=gtri.edgeIndex(fedge.twin);
		int gv=(gw+1)%3;
		int gopp=(gw+2)%3;

		gtri.setCenter(ftri.getCenter(fv),gv);
		gtri.setCenter(ftri.getCenter(fw),gw);
		if (mode == 2) { // copy over shared labels:
			gtri.setLabel(ftri.getLabel(fv),gv);
			gtri.setLabel(ftri.getLabel(fw),gw);
		} else { // default, shared radii
			gtri.setRadius(ftri.getRadius(fv),gv);
			gtri.setRadius(ftri.getRadius(fw),gw );
		}

		// update tang pts, compute map FROM "base equilateral"
		ftri.setTanPts();
		Mobius bm_f = Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				ftri.tanPts[0],ftri.tanPts[1],ftri.tanPts[2],
				0, ftri.hes);

		// compute the target circle
		CircleSimple sC = Schwarzian.getThirdCircle(s, fv, bm_f, ftri.hes);
		gtri.setCenter(sC.center,gopp);
		if (mode == 2)
			gtri.setLabel(sC.rad,gopp);
		else
			gtri.setRadius(sC.rad,gopp);

		return 0;
	}

	/**
	 * Given HalfEdge (v,w) with faces f and g (left/right resp.), compute the
	 * center of vert opposite (w,v) in face g based on data in 'tri_f': either
	 * centers for v and w and the radius for the opposite vertex or the schwarzian
	 * of the edge if 'useSchw' is true.
	 * 
	 * Note that the new data (including for v,w) is in the returned 'TriAspect', so
	 * the user may have to save what is needed on return.
	 * 
	 * @param pdcel   PackDCEL
	 * @param fedge   HalfEdge, edge of face f
	 * @param tri_f   TriAspect, data for f
	 * @param useSchw boolean
	 * @return TriAspect, null if hedge is bdry
	 */
	public static TriAspect analContinue(PackDCEL pdcel, HalfEdge fedge, 
			TriAspect tri_f, boolean useSchw) {

		TriAspect tri_g = new TriAspect(pdcel, fedge.twin.face);

		if (!useSchw) {
			int fv = tri_f.vertIndex(fedge.origin.vertIndx);
			int fw = tri_f.vertIndex(fedge.twin.origin.vertIndx);

			// copy f data on edge to g
			int j = tri_g.vertIndex(fedge.twin.origin.vertIndx);
			tri_g.setCircleData(j, tri_f.getCircleData(fw));
			int k = tri_g.vertIndex(fedge.origin.vertIndx);
			tri_g.setCircleData(k, tri_f.getCircleData(fv));

			// compute opposite data using radius
			tri_g.setCircleData((k + 1) % 3, tri_g.compOppCircle(j,false));
			return tri_g;
		}

//		???????

		return tri_g;
	}

}
