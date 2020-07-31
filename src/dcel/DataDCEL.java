package dcel;

import java.util.Iterator;

import complex.Complex;
import deBugging.DCELdebug;
import exceptions.CombException;
import komplex.EdgeSimple;
import packing.PackData;
import packing.RData;
import util.TriAspect;

/**
 * Static routines related to the "data" for DCEL's. 
 * Due to compute time considerations, much of the data on radii,
 * edges, overlaps, schwarzians, etc. will remain in 'KData' and
 * 'RData' arrays. However, data associated with the vertices
 * on the 'redChain' can have several values. 
 * 
 * TODO: these routines are preliminary, we'll see how it works.
 * 
 * @author kstephe2, 7/2020
 *
 */
public class DataDCEL {

	/**
	 * Create 'TriAspect's for each of the 'redChain'
	 * @param pdcel
	 * @return count, 0 is no redChain (should be sphere)
	 */
	public static int createTri(PackDCEL pdcel) {
		if (pdcel.redChain==null)
			return 0;
		
		// geometry, default to eucl
		int hes=0;
		if (pdcel.p!=null)
			hes=pdcel.p.hes;

		int count=0;
		RedHEdge rtrace=pdcel.redChain;
		do {
			TriAspect tri=new TriAspect(hes);
			tri=new TriAspect(hes);
			tri.face=rtrace.myEdge.face.faceIndx;
			tri.vert=setVerts(rtrace);
			rtrace.myTri=tri;
			rtrace=rtrace.nextRed;
			count++;
		} while(rtrace!=pdcel.redChain);
		
		return count;
	}
	
	/**
	 * Given a dcel structure, create a traditional packing.
	 * We use traditional methods with the packing, so we
	 * adjust the 'faceIndx's in the dcel after they're computed
	 * in 'complex_count'.
	 * 
	 * TODO: this is preliminary while trying to incorporate 
	 * dcel thinking. 7/2020. E.g. we don't use the dcel redChain
	 * yet, or dcel layout order, etc.
	 * @param pdcel PackDCEL
	 * @return PackData
	 */
	public static PackData dcel_to_packing(PackDCEL pdcel) {
		boolean debug=false;
		PackData p=new PackData(null);
		p.hes=0;
		if (pdcel.p!=null)
			p.hes=pdcel.p.hes;
		p.nodeCount=pdcel.vertCount;
		p.faceCount=pdcel.intFaceCount;
		p.bdryCompCount=pdcel.idealFaces.size();
		p.alloc_pack_space(p.nodeCount+10,false);
		p.alpha=pdcel.alpha.origin.vertIndx;
		p.firstFace=1;
		
		if (debug) 
			DCELdebug.log_full(pdcel);
		
		// create kData
		for (int v=1;v<=pdcel.vertCount;v++) { 

			int[] flower=pdcel.vertices[v].getFlower();
			int num=flower.length;
			if (flower[0]!=flower[num-1])
				p.kData[v].bdryFlag=1;
			p.kData[v].flower=flower;
			p.kData[v].num=num-1;
			p.rData[v]=new RData();
		}
		
		// create traditional 'PackData.faces' entries
		p.faces=new komplex.Face[pdcel.faceCount+1];
		Iterator<Face> fit=pdcel.faces.iterator();
		while(fit.hasNext()) {
			komplex.Face fce=new komplex.Face(3);
			Face face=fit.next();
			HalfEdge he=face.edge;
			int[] vert=new int[3];
			vert[0]=he.origin.vertIndx; // start with first end of 'edge'
			vert[1]=he.next.origin.vertIndx;
			vert[2]=he.next.next.origin.vertIndx;
			fce.vert=vert;
			fce.indexFlag=0;
			fce.plotFlag=1;
			p.faces[face.faceIndx]=fce;
		}
		
		// set face drawing order
		Iterator<Face> lit=pdcel.LayoutOrder.iterator();
		int faceIndx=lit.next().faceIndx;
		p.firstFace=faceIndx;
		while (lit.hasNext()) {
			Face face=lit.next();
			int nxtIndx=face.faceIndx;
			p.faces[faceIndx].nextFace=nxtIndx;
			faceIndx=nxtIndx;
		}

		// TODO: temporary: transfer cent/rad data from pdcel.p,
		//       if it exists
		if (pdcel.p!=null) {
			if (pdcel.newOld==null) {
				throw new CombException("'newOld' VertexMap is missing from DCEL");
			}
			Iterator<EdgeSimple> noit=pdcel.newOld.iterator();
			while (noit.hasNext()) {
				EdgeSimple es=noit.next();
				int newindx=es.v;
				int oldindx=es.w;
				pdcel.vertices[newindx].center=new Complex(pdcel.p.rData[oldindx].center);
				p.rData[newindx].center=new Complex(pdcel.p.rData[oldindx].center);
				p.rData[newindx].rad=pdcel.p.rData[oldindx].rad;
				pdcel.vertices[newindx].center=new Complex(pdcel.p.rData[oldindx].center);
				pdcel.vertices[newindx].rad=pdcel.p.rData[oldindx].rad;
				p.rData[newindx].aim=pdcel.p.rData[oldindx].aim;
				p.rData[newindx].curv=pdcel.p.rData[oldindx].curv;
			}
		}
		
		return p;
	}
	
	
	/**
	 * Get the center appropriate to the vertex 'v', where v is
	 * a vertex of this 'RedHEdge's face. This value may be held
	 * by the 'TriAspect' for this face, or that of 'preRed',
	 * or in the case of a "blue" face, by 'nextRed', or if
	 * not these, then by 'pdcel.p'.
	 * @param pdcel PackDCEL
	 * @param redge RedHEdge   
	 * @param v int
	 * @return Complex, null on error (e.g., improper v)
	 */
	public static Complex getCenter(PackDCEL pdcel,RedHEdge redge,int v) {
		Complex ans=null;
		TriAspect tri=redge.myTri;
		if (tri==null)
			throw new CombException("'RedHEdge' does not have a 'TriAspect'");
		int vindx=tri.vertIndex(v);
		if (vindx<0)
			return null;
		
		// tri is responsible for v (i.e., is v the end of 'redge'?)
		if (v==redge.myEdge.twin.origin.vertIndx)
			return tri.getCenter(vindx);
		
		// else if v is origin of 'redge', then look to 'prevRed'
		else if (v==redge.myEdge.origin.vertIndx)
			return redge.prevRed.myTri.getCenter(v);
		
		// else if v is end of 'nextRed.myEdge.twin'
		else if (v==redge.nextRed.myEdge.twin.origin.vertIndx)
			return redge.nextRed.myTri.getCenter(v);
		
		// else, get it from 'Vertex'
		return new Complex(pdcel.vertices[v].center);
		
	}

	/**
	 * Find the vertices for the 'face' associated with this
	 * 'RedHEdge'. Note, the three cclw vertices are origins of
	 * the three edges, starting with 'face.edge'. (E.g., 
	 * 'face.edge' is edge <vert[0],vert[1]>.) 
	 * @param edge RedHEdge
	 * @return int[]
	 */
	public static int[] setVerts(RedHEdge edge) {
		int[] vs=new int[3];
		HalfEdge he=edge.myEdge.face.edge;
		vs[0]=he.origin.vertIndx;
		vs[1]=he.next.origin.vertIndx;
		vs[2]=he.next.next.origin.vertIndx;
		return vs;
	}
	
}
