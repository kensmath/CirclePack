package packing;

import java.util.Iterator;

import allMains.CirclePack;
import complex.Complex;
import dcel.CombDCEL;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedEdge;
import dcel.Vertex;
import exceptions.DataException;
import exceptions.MiscException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.VertexMap;
import panels.CPScreen;

/**
 * PackLite simply holds packing data in compressed and highly
 * structured form which avoids key words. Originally this was
 * intended for efficient communication between processors, 
 * or to repack a portion of a larger packing. However, I have
 * now removed the read/write commands and methods. The purpose
 * now is to prepare data for 'GOPacker' by re-indexing 
 * vertices in a way suitable to its sparse-matrix computations: 
 * interior first, bdry last in cclw order. For spherical case, 
 * last 3 go with a face in clw order as faux bdry.
 * 
 * What to store? When generating PackLite, use bit-encoded info. 
 * "act" (parallel to 'writePack') 3: 00004 non-default inv_dist & 
 * aims; 4: 00010 radii; 5: 00020 centers; 18: 0400000 misc.
 * 
 * History: started with 'p_light' structure in former C++ version 
 * of CPack, then version for communicating with C++ libraries, more 
 * recently (10/2014) for sending info to matlab code, and  
 * (3/2015) to implement Orick's methods using calls sparse matrix 
 * solvers via C++ code. Unfortunately, my C++ calls are not working
 * (2021). In converting to DCEL structures, I have removed the
 * reading/writing of files in packLite form.
 * 
 * @author kens
 * 
 */
public class PackLite {

	private static final long serialVersionUID = 1L;

	public PackData parent; 
	public int hes; // geometry (generally inherited from parent)
	public int checkCount; // used as weak check against potential parent
	public int vertCount; // total of local indices, contiguous 1 ... vertCount
	public int intVertCount; // total of interior vertices
	public int vCount; // count of variable vertices (may include, e.g., bdry verts)
	public int bdryCount;  // saved for convenience
	public int flowerCount; // each flower takes num+3 entries
	public int aimCount; // number of non-default aims in 'aims'
	public int invDistCount; // number of non-default inversive distances in
								// 'invDistances'
	public int[] counts; // Important: indexed from 1 (not from 0)
						 // lead list of 20 integers, counts and flags
	/**
	 * +++++++++++++++ note, this is changing in 10/2014, needs updating see
	 * 'DataFormats.info'. ++++++++++++++++++++++++++++++++++
	 */

	/**
	 * Lead data (size 20 for future expansion) indicates: 1: checkCount (=
	 * parent's nodeCount) for (weak) consistency checking 2: hes = geometry
	 * (-1=hyp, 0=eucl, 1=sph) 3: vertCount (count of local indices) = size of
	 * 'orig_indices', 'radii', 'centers' blocks 4: flowerCount = size of flower
	 * block 5: intVertCount (rest are "bdry" for purpose, e.g., of Tutte
	 * embedding in Orick's algorithm) 6: vCount = count of "variable" verts
	 * (whose radii can be changed) 7: aimCount = count of non-default aims =
	 * size of 'aimIndices' block = size of aim block 8: invDistCount = count of
	 * edges having non-default inversive distances = size 'invDistances' = half
	 * the size of 'invDistEdges'. Caution: it's possible that both ends are
	 * 'fixed' vertices. 9: 0 ==> no radii, 1 ==> radii 10: 0 ==> no centers, 1
	 * ==> centers 11: 0 (future use) 12: 0 (future use) 13: 0 (future use) 14:
	 * 0 (future use) 15: 0 (future use) 16: 0 (future use) 17: 0 (future use)
	 * 18: 0 (future use) 19: 0 (future use) 20: 0 (future use)
	 */

	public int[] generations;
	public int[] varIndices; // stored from 0 index
	public int[] flowerHeads; // stored from 0 index, list "v num p(0)..p(num)"
	public int[] v2parent;    // v2parent[v] is parent index for local v; negative is v is, e.g., added ideal
	public int[] parent2v;    // parent2v[j] is local index for parent j, 0 if no local index
//	public int[] origIndices; // origIndices[v] is parent index for local v.
							  // always needed due to reordering;
							  // Negative entries for added ideal verts, but 
	public double[] radii;    // block of radii: in hyp case, use x-radii, so
							  //    negative indicates horocycle.
	public Complex[] centers; // block of centers
	public double[] centerRe;
	public double[] centerIm;
	public int[] aimIndices; // local indices of variable verts having
								// non-default aims
								// (stored from index 0)
	public double[] aims; // corresponding aims (stored from index 0).
	public int[] invDistEdges; // list u1 v1 u2 v2 ... of non-default inversive
								// distances
								// (only the uj<vj pairs are included)
	public EdgeLink invDistLink; // store edges (v,w) with non-default inv
									// distances (v<w only)
	public double[] invDistances; // non-default inverse distances, sync'ed with
									// 'invDistEdges'.
									// (stored from index 0)
	public int []vNum;   	// number of faces, usual 'num', so one less than degree at bdry
	public int [][]flowers;    // 
	
	// Constructor
	public PackLite(PackData p) throws MiscException { //
		parent = p;
		hes = 0;
		if (p != null)
			hes = p.hes;
		counts = new int[21];
		vertCount = intVertCount = vCount = bdryCount = flowerCount = aimCount = invDistCount = 0;
		varIndices = v2parent = parent2v = aimIndices = null;
		radii = aims = centerRe = centerIm= null;
		invDistEdges = null;
		invDistLink = null;
		invDistances = null;
		centers = null;
		flowerHeads = null;
		generations = null;
		if (parent != null) {
			createFrom(parent);
		}
	}

	/**
	 * Populate from PackData 'p'.
	 * 
	 * @param p PackData
	 * @return vertCount or 0 on error
	 */
	public int createFrom(PackData p) {
		return createFrom(p,0);
	}

	/**
	 * Create PackLite from PackData p with 'alp' as 
	 * suggested alpha vertex. The packing must be simply
	 * connected. If a sphere, we puncture a face far
	 * from alpha to create a faux bdry. We re-index 
	 * vertices so the interior come first (starting with 
	 * alp) and the bdry comes at the end, in 
	 * counterclockwise order, starting with gam.
	 * 
	 * @param p PackData
	 * @param alp int, suggested alpha
	 * @return int, 0 on error
	 */
	public int createFrom(PackData packData,int alp) {
		
		if (!packData.isSimplyConnected()) {
			CirclePack.cpb.errMsg(
					"Can't create 'PackLite', as packing is not "+
							"simply connected");
			return 0;
		}
		
		// work with copy so 'packData' doesn't get changed
		PackData p=packData.copyPackTo();
		p.setAlpha(alp);
		alp=p.alpha;
		int gam=0;
		
		if (p.packDCEL.redChain==null) { // sphere?
			gam=p.antipodal_vert(alp);
			dcel.DcelFace fauxface=p.packDCEL.vertices[gam].halfedge.face;
			p.puncture_face(fauxface.faceIndx);
		}

		// prune to ensure every bdry has an interior nghb.
		int rslt=CombDCEL.pruneDCEL(p.packDCEL);
		packData.vertexMap=null;
		if (rslt>0) { 
			p.packDCEL.fixDCEL(p);
			packData.vertexMap=p.packDCEL.oldNew;
			alp=p.packDCEL.alpha.origin.vertIndx;
			gam=p.packDCEL.redChain.myEdge.origin.vertIndx;
		}

		// 'new2old' is needed later
  	  	int []new2old=new int[p.nodeCount+1];
  	  	if (packData.vertexMap==null) {
			for (int ii=1;ii<=p.nodeCount;ii++)
  	  			new2old[ii]=ii; 
  	  	}
  	  	else {
  	  		for (int ii=1;ii<=p.nodeCount;ii++) 
  	  			new2old[ii]=packData.vertexMap.findW(ii);
  	  	}

		// re-index, set counts 
		int[] p_Indices=new int[p.nodeCount+1];
		int[] newIndices=new int[p.nodeCount+1];
		int[] util=new int[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) {
			if (!p.isBdry(v))
				util[v]=-v; // negative at interior
			else
				util[v]=v;
		}
		int newIndx=0;
		
		// get all interiors, alpha first
		newIndices[alp]=newIndx;
		p_Indices[newIndx++]=alp;
		util[alp]=0; // so we don't repeat it
		for (int v=1;v<=p.nodeCount;v++) {
			if (util[v]<0) {
				newIndices[v]=++newIndx;
				p_Indices[newIndx]=v;
			}
		}
		
		// save count of interiors; needed, e.g., for Tutte embedding
		intVertCount=newIndx;
		
		// now the cclw bdry
		RedEdge rtrace=p.packDCEL.redChain;
		do {
			int w=rtrace.myEdge.origin.vertIndx;
			newIndices[w]=++newIndx;
			p_Indices[newIndx]=w;
			rtrace=rtrace.nextRed;
		} while (rtrace!=p.packDCEL.redChain);
		vertCount=newIndx;
		bdryCount=vertCount-intVertCount;

		// store the info; indexing is from 0
		vCount=0;
		flowerCount=0;
		aimCount=0;
		invDistCount=0;
		double aim=-1.0;
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			flowerCount += p.countFaces(v)+3; 
			
			// is this a variable vertex (int or bdry)
			if ((aim=p.getAim(v))>=0.0) {
				vCount++; // list as variable vert

				// is aim also non-default? (non-default 'aims' indexed from 0)
				if (p.isBdry(v) || Math.abs(aim-2.0*Math.PI)>.0000001) 
					aimCount++;
			}

			// non-trivial inversive distances? ((v,w) only with w>v)
			Vertex vert=p.packDCEL.vertices[v];
			HalfLink spokes=vert.getSpokes(null);
			Iterator<HalfEdge> sis=spokes.iterator();
			while (sis.hasNext()) {
				HalfEdge he=sis.next();
				double ivd=he.getInvDist();
				if (ivd!=1.0 && he.twin.origin.vertIndx>v)
					invDistCount++;
			}
		} // done with counts
		
		// store the desired data
		varIndices=new int[vCount];
		
		// do we want 'aims' and inv dist? 
		aims=new double[aimCount];
		invDistances=new double[invDistCount];
		invDistLink=new EdgeLink(p);
		
		int vtick=0;
		int aimtick=0;
		int iDtick=0;
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			Vertex vert=p.packDCEL.vertices[v];
			HalfLink spokes=vert.getSpokes(null);
			Iterator<HalfEdge> sis=spokes.iterator();
			while (sis.hasNext()) {
				HalfEdge he=sis.next();
				double ivd=he.getInvDist();
				if (ivd!=1.0 && he.twin.origin.vertIndx>v) {
					invDistLink.add(new EdgeSimple(v,he.twin.origin.vertIndx));
					invDistances[iDtick++]=ivd;
				}
			}

			// variable vertex
			if ((aim=p.getAim(v))>=0.0) {
				varIndices[vtick++]=n;
				
				// is aim also non-default?
				if (aimCount>0 && (p.isBdry(v) || 
						Math.abs(aim-2.0*Math.PI)>.0000001)) 
					aims[aimtick++]=aim;
			}
		}
		
		// **** all flowers "v num p_0 p_1 .... p_num" (local indices)
		flowerHeads=new int[flowerCount];
		int tick=0; // start at index 0
		flowers=new int[vertCount+1][]; 
		vNum=new int[vertCount+1];
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			vNum[n]=p.countFaces(v);
			flowers[n]=new int[vNum[n]+1];
			flowerHeads[tick++]=n;
			flowerHeads[tick++]=vNum[n];
			int[] flower=p.getFlower(v);
			for (int j=0;j<=vNum[n];j++) {
				flowers[n][j]=flowerHeads[tick++]=
						newIndices[flower[j]];
			}
		}
		
		// **** original indices, indexed from 1
		v2parent=new int[vertCount+1];
		parent2v=new int[packData.nodeCount+1];
		tick=1;
		for (int n=1;n<=vertCount;n++) {
			int px=p_Indices[n]; // index in p
			int n2o=new2old[px]; // index in packData
			v2parent[n]=n2o; 
			parent2v[n2o]=n;
		}
		
		// **** radii/centers, indexed from 1
		radii=new double[vertCount+1];
		centers=new Complex[vertCount+1];
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			radii[n]=p.getRadius(v);
			centers[n]=p.getCenter(v);
		}
		
		return vertCount;
	}

	/**
	 * Convert 'this' into a packing. Note that if 'this' was
	 * created from 'parent', it may not reconstitute exactly.
	 * E.g, if a sphere, we get the faux bdry, or if pruned,
	 * it may be missing some vertices. In any case, 
	 * 'vertexMap' maps local indexes to the 'parent' 
	 * indices.
	 * @return PackData or null on error
	 */
	public PackData convertTo() {

		if (vertCount < 4 || intVertCount < 1 || flowerCount == 0) {
			CirclePack.cpb
					.errMsg("Conversion of PackLite failed: missing data");
			return null;
		}
		
		int[] indx=new int[vertCount+1];
		for (int ii=1;ii<=vertCount;ii++)
			indx[ii]=ii;
		
		if (parent.nodeCount==vertCount)
			for (int ii=1;ii<=vertCount;ii++)
				indx[ii]=v2parent[ii];

		// build the vertex bouquet
		int[][] bouquet=new int[vertCount+1][];
		int tick=0;
		for (int vv = 1; vv <= vertCount; vv++) {
			int v=indx[vv];
			int num=flowerHeads[tick++];
			int[] flower=new int[num+1];
			for (int j=0;j<=num;j++)
				flower[j]=indx[flowerHeads[tick++]];
			bouquet[v]=flower;
		}

		PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet);
		PackData p = new PackData((CPScreen) null);
		pdcel.fixDCEL(p);
		p.hes = parent.hes;
		p.setAlpha(indx[1]);
		p.setGamma(indx[intVertCount+1]);

		// set up vertexMap: 
		p.vertexMap = new VertexMap();
		for (int v=1;v<=vertCount;v++) 
			p.vertexMap.add(new EdgeSimple(v,indx[v]));

		// some default stuff
		double rad = 0.025;
		if (hes < 0)
			rad = 1.0 - Math.exp(-1.0);
		for (int v = 1; v <= vertCount; v++) {
			p.setRadius(indx[v],rad);
			p.setCenter(indx[v],new Complex(0.0));
		}

		// set vertexMap (whether we converted back to original or not)
		for (int v = 1; v <= vertCount; v++) {
			p.vertexMap.add(new EdgeSimple(v, indx[v]));
		}

		// store radii
		if (radii != null) {
			for (int v = 1; v <= vertCount; v++)
				p.setRadius(indx[v],radii[v]);
		}

		// store centers
		if (centers != null) {
			for (int v = 1; v <= vertCount; v++)
				p.setCenter(indx[v],new Complex(centers[v]));
		}

		// nondefault aims
		p.set_aim_default();
		if (aimCount > 0) {
			for (int i = 0; i < aimCount; i++)
				p.setAim(indx[aimIndices[i]],aims[i]);
		}

		if (invDistCount > 0 && invDistLink != null) {
			tick = 0;
			Iterator<EdgeSimple> iL = invDistLink.iterator();
			while (tick < invDistCount && iL.hasNext()) {
				EdgeSimple es=iL.next();
				es.v=indx[es.v];
				es.w=indx[es.w];
				HalfEdge edge=p.packDCEL.findHalfEdge(es);
				if (edge==null) 
					throw new DataException("failed to find edge "+es);
				edge.setInvDist(invDistances[tick++]);
			}
		}

		return p;
	}

	/**
	 * Place appropriate data (eg. radii) from 'this' in p. There are minimal
	 * compatibility checks, mainly 'nodeCount'.
	 * 
	 * @param p
	 *            , existing PackData
	 * @return p itself (on success) or null on error (leaving p unchanged)
	 */
	public PackData convertTo(PackData p) {
		// TODO: see 'convert_from_p_light' in old 'CPcpp'
		return null;
	}

}
