package dcel;

import java.util.Iterator;

import deBugging.DCELdebug;
import komplex.EdgeSimple;
import packing.PackData;
import packing.RData;

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
		p.setBdryCompCount(pdcel.idealFaceCount);
		p.alloc_pack_space(p.nodeCount+10,true);
		p.directAlpha(pdcel.alpha.origin.vertIndx); // <alpha,beta> is the initial edge
		p.beta=pdcel.alpha.twin.origin.vertIndx;
		p.activeNode=p.getAlpha();
		p.firstFace=1;
		
		if (debug) 
			DCELdebug.log_full(pdcel);
		
		// create kData/rData
		for (int v=1;v<=pdcel.vertCount;v++) {
//			System.out.println("k/rData got to v "+v);
			Vertex vtx=pdcel.vertices[v];
			int[] flower=vtx.getFlower(true); // traditional flower
			int num=flower.length-1;
			p.setBdryFlag(v,0);
			if (vtx.redFlag)
				p.setBdryFlag(v,vtx.bdryFlag);
			p.kData[v].flower=flower;
			p.kData[v].num=num;
			p.rData[v]=new RData(); // duplicated data below
		}
		
		// create traditional 'PackData.faces' entries (not ideal faces)
		p.faces=new komplex.Face[pdcel.faceCount+1];
		for (int f=1;f<=p.faceCount;f++) {
			Face face=pdcel.faces[f];
			int[] vts=face.getVerts();
			komplex.Face fce=new komplex.Face(vts.length);
			fce.vert=vts;
			fce.indexFlag=0;
			fce.plotFlag=1;
			p.faces[face.faceIndx]=fce;
		}
		
		// drawing order record 'nextFace' info in 'PackData' 
		//    in the traditional way
		for (int f=1;f<=p.faceCount;f++) {
			p.faces[f].nextFace=0;
		}
		Iterator<EdgeSimple> git=pdcel.computeOrder.iterator();
		EdgeSimple es=git.next(); // first is the root
		p.firstFace=es.w;
		while (git.hasNext()) {
			es=git.next();
			p.faces[es.v].nextFace=es.w;
		}
		// set old drawing order in PackDAta for remaining faces
		git=pdcel.faceOrder.iterator();
		git.next(); // toss the root 
		while (git.hasNext()) {
			es=git.next();
			if (p.faces[es.v].nextFace!=0)
				p.faces[es.v].nextFace=es.w;
		}

		// TODO: temporary: transfer cent/rad data from pdcel.p,
		//       if it exists. Note that sphere will have no 'oldNew'
		if (pdcel.p!=null) {
			if (pdcel.oldNew!=null) {
				Iterator<EdgeSimple> noit=pdcel.oldNew.iterator();
				while (noit.hasNext()) {
					es=noit.next();
					int newindx=es.w;
					int oldindx=es.v;
					// put in 'Vertex'
					p.setCenter(newindx,pdcel.p.getCenter(oldindx));
					p.setRadius(newindx,pdcel.p.getRadius(oldindx));
					p.setAim(newindx,pdcel.p.getAim(oldindx));
					p.setCurv(newindx,pdcel.p.getCurv(oldindx));
				}
			}
			else {
				for (int v=1;v<=pdcel.vertCount;v++) {
					p.setCenter(v,pdcel.p.getCenter(v));
					p.setRadius(v,pdcel.p.getRadius(v));
					p.setAim(v,pdcel.p.getAim(v));
					p.setCurv(v,pdcel.p.getCurv(v));
				}
			}
		}
		
		// set initial data in 'RedHEdge's if not a sphere
		if (pdcel.redChain!=null) {
			RedHEdge rtrace=pdcel.redChain;
			do {
				HalfEdge edge=rtrace.myEdge;
				int v=edge.origin.vertIndx;
				rtrace.setCenter(p.getCenter(v));
				rtrace.setRadius(p.getRadius(v));
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);
		}

		// misc data to arrange
		p.euler=pdcel.vertCount-pdcel.edgeCount+pdcel.faceCount;
		p.genus=(2-p.euler-p.getBdryCompCount())/2;
		p.intrinsicGeom=PackData.getIntrinsicGeom(p);
		p.bdryStarts=new int[p.getBdryCompCount()+1];
		for (int j=1;j<=p.getBdryCompCount();j++) {
			Face iface=pdcel.idealFaces[j];
			p.bdryStarts[j]=iface.edge.twin.origin.vertIndx;
		}
		p.vertexMap=pdcel.oldNew;
		p.chooseGamma();
		p.fileName=new String("clone");
		p.status=true;
		if (pdcel.p!=null)
			pdcel.p.packDCEL=null;
		pdcel.p=p;
		p.directAlpha(pdcel.alpha.origin.vertIndx);
		p.beta=pdcel.alpha.twin.origin.vertIndx;
		
		// attach this dcel
		pdcel.oldNew=null;
		p.attachDCEL(pdcel);
		return p;
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
