package packing;

import java.util.Iterator;

import komplex.CookieMonster;
import komplex.EdgeSimple;
import komplex.KData;
import listManip.EdgeLink;
import listManip.NodeLink;
import listManip.VertexMap;
import panels.CPScreen;
import allMains.CirclePack;

import complex.Complex;

import exceptions.DataException;
import exceptions.MiscException;
import exceptions.ParserException;

/**
 * PackLite simply holds data for reading/writing in compressed and highly
 * structured form with no key words
 * 
 * Purposes:
 * 
 * (1) put things in compressed array form for efficient communication between
 * processor (e.g., between Java/C++/matlab calls).
 * 
 * (2) Package packable portions of larger packings (eg. for parallel or
 * progressive packing). This involves re-indexing and hence mapping to parent
 * indices.
 * 
 * (3) For compressed file format, see 'PackData.writeLite'
 * 
 * (4) Re-index the vertices in a way suitable to sparse-matrix computations:
 * interior first, bdry last in cclw order. For spherical case, last 3 go with a
 * face in clw order --- these 3 may be used as a bdry.
 * 
 * What to store? When generating PackLite, use bit-encoded info. "act"
 * (parallel to 'writePack') 3: 00004 non-default inv_dist & aims 4: 00010 radii
 * 5: 00020 centers 18: 0400000 misc: add ideal vert for each interior bdry hole
 * 
 * History: started with 'p_light' structure in former C++ version of CPack,
 * then version for communicating with C++ libraries, more recently (10/2014) for
 * sending info to matlab code, and now (3/2015) to implement Orick's methods
 * using calls sparse matrix solvers via C++ code.
 * 
 * @author kens
 * 
 */
public class PackLite {

	public PackData parent; // may be null
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

	public PackLite(PackData p, NodeLink int_V) throws MiscException {
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
			createFrom(parent,false,int_V,0,0);
		}
	}

	/**
	 * Populate from PackData 'p'.
	 * 
	 * @param p
	 *            PackData
	 * @return vertCount or 0 on error
	 */
	public int createFrom(PackData p) throws MiscException {
		NodeLink intV = new NodeLink(p, "i");
		return createFrom(p, false, intV, 0, 0);
	}

	/**
	 * Create PackLite from PackData p, core vertices intV, alpha = alp,
	 * gamma=gam. If 'addIdeals' is true, then add ideal vertices to all
	 * boundary components except outside --- try to identify the outside by
	 * setting gam.
	 * 
	 * Here we re-index to vertices so the interior come first (starting with
	 * alp) and the bdry come at the end, in counterclockwise order, starting
	 * with gam.
	 * 
	 * If this is a sphere, then we choose a face containing gam and put list
	 * its vertices (clockwise order) as the last 3 vertices. These may be
	 * treated as 3 bdry vertices in applying Orick's algorithm.
	 * 
	 * @param p PackData
	 * @param addIdeals boolean: if true, we add ideal vertices
	 * @param intV NodeLink
	 * @param alp int, suggested alpha
	 * @param gam int, suggested gamma
	 * @return PackLite
	 * @throws MiscException
	 */
	public int createFrom(PackData packData,boolean addIdeals,
			NodeLink intV,int alp,int gam) throws MiscException {
		
		NodeLink intlist=null;
		if (intV==null || intV.size()==0) // default to interior vertices
			intlist=new NodeLink(packData,"i");
		else 
			intlist=intV;
		
		if (alp==0)
			alp=packData.alpha;
		if (gam==0)
			gam=packData.gamma;
		
		// task 1: ------ identify "bdry", "interior" 
		int []util=new int[packData.nodeCount+1];
		Iterator<Integer> iV=intlist.iterator();
		while (iV.hasNext()) {
			int v=iV.next();
			util[v]=-v; // negative if in 'intV'
			// mark nghbs of 'intV'
			for (int j=0;j<(packData.kData[v].num+packData.kData[v].bdryFlag);j++) {
				int k=packData.kData[v].flower[j];
				if (util[k]==0)
					util[k]=k; // positive (at least temporarily)
			}
		}
		
		PackData p=packData;
		
		// to avoid changing 'packData', may need copy if we 'cookie' or 'add_ideal' is needed
		boolean mustcookie=false; // are there any vertices not in or next to interior?
		for (int i=1;(i<=packData.nodeCount && !mustcookie);i++)
			if (util[i]==0)
				mustcookie=true;

		if (mustcookie || p.bdryCompCount>=2) // create new packing
			p=packData.copyPackTo();
		
		// now work with p (which is typically just packData itself)
  	  	int []new2old=null;

		// alp not in 'intV'?
		if (alp<1 || alp>p.nodeCount || util[alp]>=0) {
			alp=intlist.get(0);
		}
		p.alpha=alp;
		
		// gam not nghb of 'intV'?
		if (util[gam]<=0) {
			gam=p.bdryStarts[1]; 
		}
  	  	int newGam=gam;
  	  	int newAlp=alp;
  	  	
		// mark all nghbs of intV as poison
  	  	p.poisonVerts=new NodeLink(p);
		for (int v=1;v<=p.nodeCount;v++)
			if (util[v]>0)
				p.poisonVerts.add(v);
		
		// need to cookie? (avoid this if possible)
		boolean okay=false;
		if (mustcookie && p.poisonVerts.size()>0) {
  	  		CookieMonster cM=null;
  	  		try {
  	  			cM=new CookieMonster(p,"P");
  	  			int outcome=cM.goCookie();
  	  			if (outcome>0) { // success
  	  	  			new2old=cM.new2old;
  	  	  			p=cM.getPackData();
  	  	  			newGam=p.vertexMap.findV(newGam);
  	  	  			newAlp=p.vertexMap.findV(newAlp);
  	  	  			cM=null;
  	  	  			okay=true;
  	  			}
  	  			else if (outcome<0) 
  	  				throw new ParserException();
  	  			// else if outcome==0, no change in packing
  	  		} catch (Exception ex) {
  	  			CirclePack.cpb.errMsg("cookie failed in 'writeLite'; proceed with full packing");
  	  		}
		}
		
		// if we do without cookie, we still need 'new2old', so set it to identity
		if (!okay) { 
  			new2old=new int[p.nodeCount+1];
  			for (int ii=1;ii<=p.nodeCount;ii++)
  				new2old[ii]=ii; // need this later
  	  		if (newGam<=0 || newGam>p.nodeCount ||
  	  				p.kData[newGam].bdryFlag==0)
  	  			newGam=p.bdryStarts[1];
  	  		if (newAlp<=0 || newAlp>p.nodeCount ||
  	  				p.kData[newAlp].bdryFlag!=0) {
  	  			p.chooseAlpha();
  	  			newAlp=p.alpha;
  	  		}
  	  		
  		}
  	  	
  	  	// arrange for 'gam' to be in first bdry component
  	  	if (p.bdryCompCount>0) {
  	  		int gamBdry=1;
  	  		if (newGam<1 || newGam>p.nodeCount)
  	  			newGam=p.bdryStarts[1];
  	  		else {
  	  			gamBdry=p.whichBdryComp(newGam);
  	  			if (gamBdry<0)
  	  				newGam=p.bdryStarts[1];
  	  			else if (gamBdry>1) {
  	  				int holdw=p.bdryStarts[1];
  	  				p.bdryStarts[1]=newGam;
  	  				p.bdryStarts[gamBdry]=holdw;
  	  			}
  	  			else {
  	  				p.bdryStarts[1]=newGam;
  	  			}
  	  	  	}
		}
  	  	
  	  	// add ideal to all other bdy comps, if there are any
  	  	int idealVertCount=0;
  	  	if (addIdeals && p.bdryCompCount>=2) {
  	  		NodeLink bl=new NodeLink(p);
  	  		for (int b=2;b<=p.bdryCompCount;b++)
  	  			bl.add(p.bdryStarts[b]);
  	  		idealVertCount=p.add_ideal(bl);
  	  	}
		
  	  	// check alpha
  	  	if (newAlp<1)
  	  		p.chooseAlpha();

		// task 2: ------------------ re-index, counts ------------------------------
  	  	// now work entirely in new packing: assume 'intlist' is now all interiors
  	  	
		int []p_Indices=new int[p.nodeCount+1];
		int []newIndices=new int[p.nodeCount+1];
		util=new int[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) {
			if (p.kData[v].bdryFlag==0)
				util[v]=-v; // negative at interior
			else
				util[v]=v;
		}
		int newIndx=1;
		
		// get all interiors, alpha first
		if (util[p.alpha]<0) {
			newIndices[p.alpha]=newIndx;
			p_Indices[newIndx++]=p.alpha;
			util[p.alpha]=0; // so we don't repeat it
		}
		for (int v=1;v<=p.nodeCount;v++)
			if (util[v]<0) {
				newIndices[v]=newIndx;
				p_Indices[newIndx++]=v;
			}
		
		// get the count of interiors; needed, e.g., for Tutte embedding
		intVertCount=newIndx-1;
		
		// if no boundary, p should be a sphere. We want to list 3 verts of 
		//     one face in, clw order, as last three indices --- in Orick's approach, 
		//      these may be treated as bdry.
		if (intVertCount==p.nodeCount) {
			p.bdryCompCount=0;
			int b1=-1;
			int b2=-1;
			int b3=-1;
			
			// try to use clw verts of face containing gam as boundary
			if (newGam>0 && newGam<=p.nodeCount && p.nghb(newGam, newAlp)<0) {
				b1=newGam;
				b2=p.kData[newGam].flower[1];
				b3=p.kData[newGam].flower[0];
			}
			else { // use antipodal vert
				b1=p.antipodal_vert(p.alpha);
				b2=p.kData[b1].flower[1];
				b3=p.kData[b1].flower[0];
			}

			// swap b1 and p.nodeCount-2
			int new1=newIndices[b1];
			int old1=p_Indices[p.nodeCount-2];
			p_Indices[new1]=old1;
			newIndices[old1]=new1;
			p_Indices[p.nodeCount-2]=b1;
			newIndices[b1]=p.nodeCount-2;

			// swap b2 and p.nodeCount-1
			new1=newIndices[b2];
			old1=p_Indices[p.nodeCount-1];
			p_Indices[new1]=old1;
			newIndices[old1]=new1;
			p_Indices[p.nodeCount-1]=b2;
			newIndices[b2]=p.nodeCount-1;

			// swap b3 and p.nodeCount
			new1=newIndices[b3];
			old1=p_Indices[p.nodeCount];
			p_Indices[new1]=old1;
			newIndices[old1]=new1;
			p_Indices[p.nodeCount]=b3;
			newIndices[b3]=p.nodeCount;
			vertCount=p.nodeCount;
			intVertCount=p.nodeCount-3;
			bdryCount=3;
		}
		// now boundary component (should be just one) in cclw order
		else { 
			for (int b=1;b<=p.bdryCompCount;b++) {
				int w=p.bdryStarts[b];
				int stopw=p.kData[w].flower[p.kData[w].num]; // upstream nghb
				if (util[w]>0) {
					newIndices[w]=newIndx;
					p_Indices[newIndx++]=w;
					util[w]=0; // so this one won't be duplicated
				}
				while (w!=stopw) {
					w=p.kData[w].flower[0];
					if (util[w]>0) {
						newIndices[w]=newIndx;
						p_Indices[newIndx++]=w;
						util[w]=0; // so this one won't be duplicated
					}
				}
			}
			vertCount=newIndx-1;
			bdryCount=vertCount-intVertCount;
		}

		// task 3: ------------------------ store the info ----------------------------
		
		// various counts
		vCount=0;
		flowerCount=0;
		aimCount=0;
		invDistCount=0;
		double aim=-1.0;
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			flowerCount += p.kData[v].num+3;
			
			// is this a variable vertex
			if ((aim=p.rData[v].aim)>=0.0) {

				// list as variable vert
				vCount++;
				
				// is aim also non-default? (non-default 'aims' indexed from 0)
				if (p.kData[v].bdryFlag==1 || Math.abs(aim-2.0*Math.PI)>.0000001) 
					aimCount++;
			}

			// does it have new inversive distances? (only pairs (v,w) with w>v) 
			if (p.overlapStatus && p.kData[v].invDist!=null) {
				for (int jj=0;jj<(p.kData[v].num+p.kData[v].bdryFlag);jj++) {
					int w=p.kData[v].flower[jj];
					if (w>v && p.getInvDist(v,p.kData[v].flower[jj])!=1.0)
						invDistCount++;
				}
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

			// variable vertex
			if ((aim=p.rData[v].aim)>=0.0) {
				varIndices[vtick++]=n;
				
				// is aim also non-default? (non-default 'aims' indexed from 0)
				if (aimCount>0 && (p.kData[v].bdryFlag==1 || Math.abs(aim-2.0*Math.PI)>.0000001)) 
					aims[aimtick++]=aim;
			}

			// does it have new inversive distances? (only pairs (v,w) with w>v) 
			if (invDistCount>0 && p.overlapStatus && p.kData[v].invDist!=null) {
				for (int jj=0;jj<(p.kData[v].num+p.kData[v].bdryFlag);jj++) {
					int w=p.kData[v].flower[jj];
					if (w>v && p.getInvDist(v,p.kData[v].flower[jj])!=1.0) {
						invDistLink.add(new EdgeSimple(v,w));
						invDistances[iDtick++]=p.getInvDist(v,p.kData[v].flower[jj]);
					}
				}
			}
		}
		
		// **** all flowers "v num p_0 p_1 .... p_num" (local indices)
		flowerHeads=new int[flowerCount];
		int tick=0; // start at index 0
		flowers=new int[vertCount+1][]; 
		vNum=new int[vertCount+1];
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			vNum[n]=p.kData[v].num;
			flowers[n]=new int[vNum[n]+1];
			flowerHeads[tick++]=n;
			flowerHeads[tick++]=vNum[n];
			for (int j=0;j<=p.kData[v].num;j++) {
				flowers[n][j]=flowerHeads[tick++]=newIndices[p.kData[v].flower[j]];
			}
		}
		
		// **** original indices, indexed from 1
		v2parent=new int[vertCount+1];
		parent2v=new int[packData.nodeCount+1];
		tick=1;
		for (int n=1;n<=vertCount;n++) {
			int px=p_Indices[n];
			int overNC=px-p.nodeCount+idealVertCount;
			
			// Caution: added ideal verts don't have corresponding verts in the parent
			if (overNC>0)
				v2parent[tick++]=-overNC; // store negative of 'j' if this is jth added ideal
			else {
				int n2o=new2old[px];
				v2parent[tick]=n2o; // all the way back to original parent (not just p)
				parent2v[n2o]=tick;
				tick++;
			}
		}
		
		// **** radii/centers, indexed from 1
		radii=new double[vertCount+1];
		centers=new Complex[vertCount+1];
		tick=1;
		for (int n=1;n<=vertCount;n++) {
			int v=p_Indices[n];
			radii[tick]=p.getRadius(v);
			centers[tick]=p.getCenter(v);
			tick++;
		}
		
		return vertCount;
	}

	/**
	 * Convert 'this' into a packing. If 'checkCount' and 'vertCount' and
	 * 'v2parent' are okay (e.g., no added ideal verts, etc.), then we
	 * convert back to original vertices; otherwise the packing uses the local
	 * indices from 'this'. In either case, 'vertexMap' maps local to the
	 * 'v2parent' (when >0), e.g., when data came from a piece of another
	 * packing or ideal verts were added.
	 * 
	 * @return PackData or null on error
	 */
	public PackData convertTo() {

		if (vertCount < 4 || intVertCount < 1 || flowerCount == 0) {
			CirclePack.cpb
					.errMsg("Conversion of PackLite failed: missing data");
			return null;
		}

		PackData p = new PackData((CPScreen) null);
		p.nodeCount = vertCount;
		p.hes = hes;
		p.kData = new KData[vertCount + 1];
		p.rData = new RData[vertCount + 1];
		p.vertexMap = new VertexMap();

		// set up veconversion default=identity
		int[] vertConvert = new int[vertCount + 1];
		for (int v = 1; v <= vertCount; v++)
			vertConvert[v] = v;

		// see about converting to 'origIndices'; check first
		if (checkCount == vertCount) {
			int hit = -1;
			int[] ck = new int[vertCount + 1];
			for (int v = 1; (v <= vertCount && hit < 0); v++) {
				if (v2parent[v] < 1 || v2parent[v] > vertCount
						|| ck[v2parent[v]] != 0)
					hit = 0;
				ck[v2parent[v]] = v;
			}
			if (hit < 0) { // legal indices, no ideals, no redundancies
				vertConvert = v2parent;
			}
		}

		p.alpha = vertConvert[1];
		if (intVertCount < vertCount)
			p.gamma = vertConvert[intVertCount + 1];
		else
			p.gamma = vertConvert[vertCount];

		// store the flower info
		int tick = 0;
		for (int vv = 1; vv <= vertCount; vv++) {
			int v = vertConvert[vv];
			p.kData[v] = new KData();
			p.rData[v] = new RData();
			if (flowerHeads[tick++] != vv) {
				// this is an error;
				continue;
			}
			int num = p.kData[v].num = flowerHeads[tick++];
			p.kData[v].flower = new int[num + 1];
			for (int j = 0; j <= num; j++)
				p.kData[v].flower[j] = vertConvert[flowerHeads[tick++]];
		}

		// some default stuff
		double rad = 0.025;
		if (hes < 0)
			rad = 1.0 - Math.exp(-1.0);
		for (int v = 1; v <= vertCount; v++) {
			p.setRadius(v,rad);
			p.setCenter(v,new Complex(0.0));
		}

		// set vertexMap (whether we converted back to original or not)
		for (int v = 1; v <= vertCount; v++) {
			int oldv = v2parent[v];
			// added ideal vertices are negative, don't put in map
			if (oldv > 0)
				p.vertexMap.add(new EdgeSimple(v, oldv));
		}

		// store radii
		if (radii != null) {
			for (int v = 1; v <= vertCount; v++)
				p.setRadius(vertConvert[v],radii[v]);
		}

		// store centers
		if (centers != null) {
			for (int v = 1; v <= vertCount; v++)
				p.setCenter(vertConvert[v],new Complex(centers[v]));
		}

		// process packing, drawingorder and complex_count
		if (p.setCombinatorics() <= 0) {
			throw new DataException(
					"combinatoric problems with conversion of PackLite");
		}

		// nondefault aims
		p.set_aim_default();
		if (aimCount > 0) {
			for (int i = 0; i < aimCount; i++)
				p.rData[vertConvert[aimIndices[i]]].aim = aims[i];
		}

		if (invDistCount > 0 && invDistLink != null) {
			p.alloc_overlaps();
			tick = 0;
			Iterator<EdgeSimple> iL = invDistLink.iterator();
			while (tick < invDistCount && iL.hasNext()) {
				EdgeSimple edge = iL.next();
				p.set_single_invDist(vertConvert[edge.v],vertConvert[edge.w],
						invDistances[tick++]);
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
