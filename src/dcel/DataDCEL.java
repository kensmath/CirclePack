package dcel;

import java.util.Iterator;

import deBugging.DCELdebug;
import exceptions.CombException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import packing.PackData;
import packing.RData;
import util.ColorUtil;
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
		p.bdryCompCount=pdcel.idealFaceCount;
		p.alloc_pack_space(p.nodeCount+10,true);
		p.alpha=pdcel.alpha.origin.vertIndx; // <alpha,beta> is the initial edge
		p.beta=pdcel.alpha.twin.origin.vertIndx;
		p.activeNode=p.alpha;
		p.firstFace=1;
		
		if (debug) 
			DCELdebug.log_full(pdcel);
		
		// create kData/rData
		for (int v=1;v<=pdcel.vertCount;v++) {
//			System.out.println("k/rData got to v "+v);
			Vertex vtx=pdcel.vertices[v];
			int[] flower=vtx.getFlower();
			int num=flower.length-1;
			p.kData[v].bdryFlag=0;
			if (vtx instanceof RedVertex)
				p.kData[v].bdryFlag=((RedVertex)vtx).bdryFlag;
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
		//       if it exists
		if (pdcel.p!=null) {
			if (pdcel.newOld==null) {
				throw new CombException("'newOld' VertexMap is missing from DCEL");
			}
			Iterator<EdgeSimple> noit=pdcel.newOld.iterator();
			while (noit.hasNext()) {
				es=noit.next();
				int newindx=es.v;
				int oldindx=es.w;
				// put in 'Vertex'
				Vertex vert=pdcel.vertices[newindx];
				p.setCenter(newindx,pdcel.p.getCenter(oldindx));
				p.setRadius(newindx,pdcel.p.getRadius(oldindx));
				p.rData[newindx].aim=pdcel.p.rData[oldindx].aim;
				p.rData[newindx].curv=pdcel.p.rData[oldindx].curv;
			}
		}
		
		// set initial data in 'RedHEdge's
		RedHEdge rtrace=pdcel.redChain;
		do {
			HalfEdge edge=rtrace.myEdge;
			int v=edge.origin.vertIndx;
			rtrace.setCenter(p.getCenter(v));
			rtrace.setRadius(p.getRadius(v));
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		
		// Arrange the side pairings in order of 'sideStarts'
		if (pdcel.sideStarts!=null) {
			Iterator<RedHEdge> rit=pdcel.sideStarts.iterator();
			int sptick=0;
			pdcel.pairLink=new D_PairLink(p);
			pdcel.pairLink.add(null); // first is null
			EdgeLink oldnew=new EdgeLink();
			while (rit.hasNext()) {
				RedHEdge rstart=rit.next();
				
				D_SideData sideData=new D_SideData(p);
				sideData.spIndex=++sptick; // indexed from 1
				oldnew.add(new EdgeSimple(rstart.mobIndx,sptick));
				sideData.startEdge=rstart;

				// if paired, find the end
				RedHEdge rtrc=rstart;
				if (rstart.twinRed!=null) {
					while (rtrc.twinRed!=null &&
							rtrc.nextRed==rtrc.twinRed.prevRed.twinRed) {
						rtrc=rtrc.nextRed;
					}
					sideData.endEdge=rtrc;
				}
				else { // part (perhaps not all) of boundary component
					do {
						rtrc=rtrc.nextRed;
					} while (rtrc!=rstart && rtrc.twinRed==null);
					sideData.endEdge=rtrc.prevRed;
				}
				pdcel.pairLink.add(sptick,sideData); // 0 entry is null
			}
			
			// find and label the pairings and free sides
			int pairCount=0;
			int freeCount=1;
			Iterator<D_SideData> pdpit=pdcel.pairLink.iterator();
			pdpit.next(); // flush null entry 
			while(pdpit.hasNext()) {
				D_SideData sdata=pdpit.next();
				
				// a paired side
				if (sdata.startEdge.twinRed!=null) {
					int oldindx=oldnew.findV(sdata.spIndex);
					if (oldindx>0) {
						int mate=oldnew.findW(-oldindx);
						D_SideData oppData=pdcel.pairLink.get(mate);
						sdata.mateIndex=oppData.spIndex;
						oppData.mateIndex=sdata.spIndex;
						sdata.pairedEdge=oppData;
						oppData.pairedEdge=sdata;
						char c=(char)('a'+pairCount);
						sdata.label=String.valueOf(c);
						c=(char)('A'+pairCount);
						oppData.label=String.valueOf(c);
						sdata.color=ColorUtil.spreadColor(pairCount); // distinct colors
						oppData.color=ColorUtil.spreadColor(pairCount); // distinct colors
						pairCount++;
					}
				}
				else {
					sdata.mateIndex=-1;
					sdata.pairedEdge=null;
					sdata.label=String.valueOf(freeCount);
					freeCount++;
				}
			}
		}

		// misc data to arrange
		p.euler=pdcel.euler;
		p.genus=(2-p.euler-p.bdryCompCount)/2;
		p.bdryStarts=new int[p.bdryCompCount+1];
		for (int j=1;j<=p.bdryCompCount;j++) {
			Face iface=pdcel.idealFaces[j];
			p.bdryStarts[j]=iface.edge.twin.origin.vertIndx;
		}
		p.vertexMap=pdcel.newOld;
		p.chooseGamma();
		p.fileName=new String("clone");
		p.status=true;
		if (pdcel.p!=null)
			pdcel.p.packDCEL=null;
		pdcel.p=p;
		p.alpha=pdcel.alpha.origin.vertIndx;
		p.beta=pdcel.alpha.twin.origin.vertIndx;
		
		// attach this dcel
		pdcel.newOld=null;
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
