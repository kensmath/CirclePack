package workshops;

import java.util.ArrayList;
import java.util.Iterator;

import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.PackDCEL;
import exceptions.CombException;
import geometry.CircleSimple;
import geometry.CommonMath;
import listManip.FaceLink;
import listManip.HalfLink;
import util.TriAspect;

/**
 * Static routines for doing various layout processes.
 * @author kstephe2
 *
 */
public class LayoutShop {
	
	/**
	 * Given chain 'hlink' (generally closed), return the list of
	 * vertex centers. If any red vertices are encounted then polygon
	 * may cross side-paired edges, so we find the chain of faces
	 * to its left (closed if hlink is closed) and use these in 
	 * succession to recompute the centers along hlink; the resulting
	 * list is not closed. Assume first halfedge from hlink is in 
	 * place and work from there. If edges in hlink are not contiguous, 
	 * work with the maximal initial contiguous subsegment.
	 * @param pdcel PackDCEL. 
	 * @param hlink HalfLink
	 * @param hes int
	 * @return ArrayList<Complex>, null on error
	 */
	public static ArrayList<Complex> layoutPolygon(PackDCEL pdcel,
			HalfLink hlink,int hes) {
		if (hlink==null || hlink.size()<=1)
			return null;
		ArrayList<Complex> Zlist=new ArrayList<Complex>();
		
		// check if any red vertices are involved
		boolean nored=true;
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext() && nored) {
			HalfEdge he=his.next();
			if (he.origin.redFlag)
				nored=false;
		}
		
		// easy case, accumulate centers
		if (nored) {
			his=hlink.iterator();
			while (his.hasNext()) {
				HalfEdge he=his.next();
				Zlist.add(pdcel.getVertCenter(he));
			}
			// if not closed, add last vertex
			if (hlink.getLast().twin.origin!=hlink.getFirst().origin)
				Zlist.add(pdcel.getVertCenter(hlink.getLast().next)); 
			return Zlist;
		}
		
		// hard case; need layout to recompute centers
		
		// if closed, don't need last edge
		if (hlink.getLast().twin.origin==hlink.getFirst().origin)
			hlink.removeLast(); 
		HalfLink leftlink=HalfLink.leftsideLink(pdcel, hlink);
		if (leftlink==null)
			return null;

		// create triAspects for these faces
		ArrayList<TriAspect> aspectlist=new ArrayList<TriAspect>();
		his=leftlink.iterator();
		while (his.hasNext()) {
			aspectlist.add(new TriAspect(pdcel,his.next().face));
		}
		
		// start with both ends of the first edge
		HalfEdge firsthe=hlink.getFirst();
		Vertex firstV=firsthe.origin;
		Zlist.add(pdcel.getVertCenter(firsthe));
		Zlist.add(pdcel.getVertCenter(firsthe.next));
		
		// call routine to layout the chain of TriAspects
		int j=aspectlist.get(0).vertIndex(firstV.vertIndx);
		layTriAspects(aspectlist,j);
		
		// look for successive ends of hlink edges in aspectlist
		his=hlink.iterator();
		his.next(); // toss first edge
//		Iterator<HalfEdge> llits=leftlink.iterator();
		Iterator<TriAspect> tais=aspectlist.iterator();
		while (his.hasNext()) {
			int V=his.next().next.origin.vertIndx; // next vert to find
			boolean hit=false;
			while (tais.hasNext() && !hit) {
				TriAspect triaspect=tais.next();
				int k=-1;
				if ((k=triaspect.vertIndex(V))>=0) {
					hit=true;
					Zlist.add(triaspect.center[k]);
				}
			}
			if (!hit)
				throw new CombException("didn't find center for V in TriAspects");
		}
		
		return Zlist;
	}
	
	/**
	 * Given a chain of contiguous 'TriAspect's, assume the j edge
	 * of the first is already placed, compute its third center, then
	 * continue along aspects setting shared centers and fixing
	 * third centers. Note: new centers end up in the TriAspect's, 
	 * but are not updated anywhere else.
	 * @param aspects ArrayList<TriAspect>
	 * @param j int
	 */
	public static void layTriAspects(ArrayList<TriAspect> aspects,int j) {
		Iterator<TriAspect> tais=aspects.iterator();
		TriAspect nextasp=tais.next();
		TriAspect lastasp=nextasp;
		
		// set only the third center of first TriAspect
		CircleSimple cs=lastasp.compOppCircle(j);
		lastasp.center[(j+2)%3]=cs.center;

		// now successively adjust the next TriAspect's centers
		while(tais.hasNext()) {
			TriAspect asp=tais.next(); // compute centers for this
			int J=asp.nghb_Tri(lastasp);
			if (J<0)
				throw new CombException("somehow, chain of aspects is broken,"
						+ " face"+asp.faceIndx);
			int v=asp.vert[J];
			int w=asp.vert[(J+1)%3];
			int oldv=lastasp.vertIndex(v);
			int oldw=lastasp.vertIndex(w);
			asp.center[J]=new Complex(lastasp.center[oldv]); // reset shared centers
			asp.center[(J+1)%3]=new Complex(lastasp.center[oldw]);
			cs=asp.compOppCircle(J);
			asp.center[(J+2)%3]=cs.center;
			lastasp=asp;
		}
	}
	
	/**
	 * Recompute centers along list of faces; only do contiguous
	 * faces, and assume first is already in place.
	 * @param pdcel PackDCEL 
	 * @param facelist FaceLink
	 * @param hes int
	 * @return int last face index, 0 on failure
	 */
	public static int layoutFaceList(PackDCEL pdcel,FaceLink facelist,int hes) {
		if (facelist==null || facelist.size()==0)
			return 0;
		Iterator<Integer> fis=facelist.iterator();
		DcelFace last_face=pdcel.faces[fis.next()];
		while (fis.hasNext()) {
			int g=fis.next(); // next face
			DcelFace next_face=pdcel.faces[g];
			HalfEdge he=last_face.faceNghb(next_face).twin;
			
			// shuck non-neighbors
			while (he==null && fis.hasNext()) {
				next_face=pdcel.faces[fis.next()];
				he=last_face.faceNghb(next_face);
			}
			
			// note order: we use centers for 'he' as an edge 
			//    of 'last_face', so 'he.twin' is the base in 
			//    the next face.
			if (he!=null) {
				CircleSimple sc=CommonMath.comp_any_center(
		    		pdcel.getCircleSimple(he.next),pdcel.getCircleSimple(he),
		    		pdcel.getVertRadius(he.twin.next.next),
		    		he.twin.next.getInvDist(),he.twin.next.next.getInvDist(),
		    		he.twin.getInvDist(),hes);
				pdcel.setCent4Edge(he.twin.next.next, sc.center);
				if (sc.rad<0) // horocycle?
					pdcel.setRad4Edge(he.twin.next.next,sc.rad);
			}
		} // end of while

		return last_face.faceIndx;
	}
	
}
