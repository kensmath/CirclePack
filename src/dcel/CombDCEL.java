package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;

/**
 * Static combinatorial routines for working with DCEL structures.
 * TODO: Routines are gathered from earlier work, so all needs careful 
 * debugging. Eventually will handle creation differently.
*/
public class CombDCEL {

	/**
	 * Given a bouquet of combinatoric data alone, create a
	 * DCEL structure with 'vertices' and 'edges' only.
	 * Bouquet satisfies usual conventions: counterclockwise order, 
	 * indexed contiguously from 1, bdry/interior flower 
	 * open/closed, resp.
	 * @param pdcel PackDCEL
	 * @param bouquet int[][]
	 * @return vertcount
	 */
	public static int createVE(PackDCEL pdcel,int[][] bouquet) {
		return createVE(pdcel,bouquet,0);
	}
	
	/**
	 * Given a bouquet of combinatoric data alone, create a
	 * DCEL structure with 'vertices' and 'edges' only.
	 * Bouquet satisfies usual conventions: counterclockwise order, 
	 * indexed contiguously from 1, bdry/interior flower 
	 * open/closed, resp. 'alpha' helpedge should start at interior  
	 * @param pdcel PackDCEL (included because it is instantiated by calling routine)
	 * @param bouquet int[][]
	 * @param alphaIndx int, suggested 'alpha' vertex, may be 0
	 * @return vertcount
	 */
	public static int createVE(PackDCEL pdcel,int[][] bouquet,int alphaIndx) {
		int vertcount = bouquet.length - 1; // 0th entry is empty
		Vertex[] vertices = new Vertex[vertcount + 1];
		int[] bdryverts=new int[vertcount+1];  // flag bdry vertices
		int firstInterior=0; 
		int edgecount=0;
		ArrayList<HalfEdge> edges = new ArrayList<HalfEdge>();

		// store arrays of 'HalfEdge's for all vertices
		HalfEdge[][] heArrays = new HalfEdge[vertcount + 1][];

		// create new 'Vertex's and fill 'heArrays'
		for (int v = 1; v <= vertcount; v++) {
			int[] flower = bouquet[v];
			int count = flower.length;

			if (flower[0] == flower[count - 1]) { // v interior
				count--;
				if (firstInterior==0)
					firstInterior=v;
			}
			else // v is bdry
				bdryverts[v]=1;

			if (count == 0)
				throw new CombException("bad count for vertex " + v);
			heArrays[v] = new HalfEdge[count];

			// create the new 'Vertex' and its first 'HaldEdge'
			Vertex newV = new Vertex(v);
			heArrays[v][0] = new HalfEdge(newV);
			heArrays[v][0].edgeIndx = ++edgecount;
			newV.halfedge = heArrays[v][0];
			vertices[v] = newV;
			edges.add(heArrays[v][0]);

			// create the rest of outgoing edges
			for (int k = 1; k < count; k++) {
				heArrays[v][k] = new HalfEdge(newV);
				heArrays[v][k].edgeIndx = ++edgecount;
				edges.add(heArrays[v][k]);
			}
		} // end of for loop on 'vertcount'

		// establish all twins (using info in 'bouquet')
		for (int v = 1; v <= vertcount; v++) {
			int[] flower = bouquet[v];
			int count = flower.length;
			if (flower[0] == flower[count - 1]) // v interior
				count--;
			for (int k = 0; k < count; k++) {
				int w = flower[k];
				int indx_wv = nghb(bouquet,w, v);
				if (indx_wv < 0) {
					CirclePack.cpb.errMsg("Error: missing edge in 'CreateVertsEdges'");
				}
				heArrays[v][k].twin = heArrays[w][indx_wv];
			}
		}

		// establish next/prev
		for (int v = 1; v <= vertcount; v++) {
			int[] flower = bouquet[v];
			int count = flower.length;
			if (flower[0] == flower[count - 1]) // v interior
				count--;
			for (int k = 0; k < count; k++) {
				HalfEdge he = heArrays[v][k];
				int w = flower[k];

				// prev = twin of cclw edge
				int m = (k + 1) % count;
				he.prev = heArrays[v][m].twin;

				// next = edge from w clw to edge to v
				m = nghb(bouquet,w, v);
				int[] wflower = bouquet[w];
				int wcount = wflower.length;
				if (wflower[0] == wflower[wcount - 1]) // w interior
					wcount--;
				m = (m - 1 + wcount) % wcount; // clockwise
				he.next = heArrays[w][m];
			}
		}
		
		// designate 'alpha' halfedge; use suggested 'alphaIndx', if interior
		HalfEdge alpha=null;
		if (alphaIndx==0 || bdryverts[alphaIndx]!=0)
			alphaIndx=firstInterior;
		if (alphaIndx>0 && alphaIndx<=vertcount && bdryverts[alphaIndx]==0) {
			int k=heArrays[alphaIndx].length;
			for (int j=0;(j<k && alpha==null);j++) {
				int w=heArrays[alphaIndx][j].twin.origin.vertIndx;
				if (bdryverts[w]==0) // ok, other end is interior
					alpha=heArrays[alphaIndx][j];
			}
		}
		// if that didn't work, look further
		if (alpha==null) {
		for (int v=1;(v<=vertcount && alpha==null);v++) {
			if (bdryverts[v]==0) { // v interior
				int k=heArrays[v].length;
				for (int j=0;(j<k && alpha==null);j++) {
					int w=heArrays[v][j].twin.origin.vertIndx;
					if (bdryverts[w]!=0) // ok, other end is interior
						alpha=heArrays[v][j];
				}
			}
		}
		}
		// still didn't work? need to set it to something.
		if (alpha==null) {
			alpha=heArrays[firstInterior][0];
		}
		
		// Done, so now populate the DCEL
		pdcel.redChain=null;
		pdcel.vertCount=vertcount;
		pdcel.alpha=alpha;
		pdcel.faceCount=0;
		pdcel.intFaceCount=0;
		pdcel.idealFaceCount=0;
		pdcel.vertices=vertices;
		pdcel.edges=edges;
		pdcel.faces=null;
		pdcel.idealFaces=null;
		pdcel.euler = 0;
			
		return vertcount;
	}
	
	/**
	 * Given a minimal DCEL (with vertices, edges) and a list
	 * (possibly empty) of vertices that are non-keepers, generate 
	 * a new DCEL which adds the non-keepers to the boundary.
	 * 
	 * This creates new face indices, idealfaces, redchain, sides, 
	 * and so forth, returning a fully prepared 'PackDCEL'.
	 * The "red" chain is a closed cclw chain of edges about a
	 * simple connected fundamental domain within the complex.
	 * 
	 * Notes: 
	 *   * 'nonKeepers' may or may not end up in the final DCEL;
	 *     any that remain are part of the boundary and may occur
	 *     in the redChain more than once. E.g. you can introduce
	 *     a cut along some edges, putting them in the boundary.
	 *   * Additional vertices may be lost -- e.g., 'keepers' that
	 *     are surrounded by nonKeepers.
	 *   * If you want to excise a set of "poison" vertices, then
	 *     set 'poisonFlag' and include their neighboring vertices
	 *   * An isolated vertex in 'nonKeeper' should be okay, except
	 *     for a combinatorial sphere with a single vert in 
	 *     'nonKeepers'. (If you want to "puncture" a point, you need
	 *     to include its neighbors in 'nonKeepers'
	 * @param p PackData, could be null
	 * @param bouquet int[][], normal bouquet
	 * @param nonKeepers NodeLink,
	 * @param poisonFlag boolean, if true, then augment 
	 *    'nonKeepers' with their neighbors 
	 * @return PackDCEL
	 */
	public static PackDCEL redDCELbuilder(PackData p,
			int[][] bouquet, NodeLink nonKeepers,boolean poisonFlag) {
		boolean debug=false;
		PackDCEL pdcel=new PackDCEL(); 
		pdcel.p=p;
		createVE(pdcel,bouquet,p.alpha);
		int vertcount=pdcel.vertCount;

		if (debug) { // debug=true;
			DCELdebug.tri_of_edge(pdcel,24,37);
		}
		
		//============ early bookkeeping re 'alpha' and keepers ========== 
		// Classify verts in 'keepV[]' and 'bdryNon[]' in steps (1) - (7). 
		// 'bdryNon[v]' is true, meaning v is nonkeeper only because it is bdry,
		// is used only in setting red twins. 'keepV'is further modified.

		// (1) initialize to keepV to -1.
		int[] keepV = new int[pdcel.vertCount + 1];
		for (int k=1;k<=pdcel.vertCount;k++) {
			keepV[k]=-1;
		}
		
		// (2) set keepV 0 for all specified 'nonkeepers'
		if (nonKeepers!=null && nonKeepers.size()>0) {
			Iterator<Integer> vit=nonKeepers.iterator();
			while (vit.hasNext()) {
				int v=vit.next();
				if (v>0 && v<=vertcount) {
					keepV[v]=0;
				}
			}
		}
		
		// (3) if not 'poisonFlag' then restore interior nonkeepers
		//     without some nonkeeper or bdry nghb. (Because the results
		//     are ambiguous if a nonkeeper is isolated.)
		if (!poisonFlag) {
			for (int k=1;k<=pdcel.vertCount;k++) {
				if (keepV[k]==0) {
					int num=bouquet[k].length-1;
					if (bouquet[k][0]==bouquet[k][num]) { // k is ineterior
						boolean keepme=true;
						// is some nghb nonkeeper or bdry? then don't keepme
						for (int j=0;(j<num && keepme);j++) { 
							if (keepV[bouquet[k][j]]==0 || isBouqBdry(bouquet,bouquet[k][j]))
								keepme=false;
						}
						if (keepme)
							keepV[k]=-1; // k reverts to being possible keeper
					}
				}
			}
		}
		
		// (4) 'poisonFlag' means make nghbs of nonkeepers also nonkeepers
		else { 
			boolean[] poison = new boolean[pdcel.vertCount + 1];
			for (int k=1;k<=pdcel.vertCount;k++) 
				if (keepV[k]==0) {
					poison[k]=true;
					int num=bouquet[k].length;
					for (int j=0;j<num;j++)
						poison[bouquet[k][j]]=true;
				}
			for (int k=1;k<=pdcel.vertCount;k++) 
				if (poison[k]) {
					keepV[k]=0;
				}
		}
			
		// (5) Make all bdry nonkeepers
		for (int k=1;k<=pdcel.vertCount;k++) { 
			if (isBouqBdry(bouquet,k)) {
				keepV[k]=0;
			}
		}
		
		// (6) Good 'alpha'? a keeper interior (prefer int/keeper petals, too)
		int alph=pdcel.alpha.origin.vertIndx;
		if (keepV[alph]==0) { // oops, not a keeper
			pdcel.alpha=null;
			for (int v=1;(v<=pdcel.vertCount && pdcel.alpha==null);v++) {
				int[] flower=bouquet[v];
				boolean toss=false;
				if (keepV[v]!=0 && !isBouqBdry(bouquet,v)) {
					for (int j=0;(j<flower.length && !toss);j++) {
						int w=flower[j];
						// bdry or not a keeper? 
						if (isBouqBdry(bouquet,w) || keepV[w]==0) 
							toss=true;
					}
				}
				if (!toss) // got our alpha
					pdcel.alpha=pdcel.vertices[v].halfedge;
			}
			// still nothing? at least require that all petals are keepers
			if (pdcel.alpha==null) {
				for (int v=1;(v<=pdcel.vertCount && pdcel.alpha==null);v++) {
					int[] flower=bouquet[v];
					boolean toss=false;
					if (keepV[v]!=0 && !isBouqBdry(bouquet,v)) {
						for (int j=0;(j<flower.length && !toss);j++) {
							int w=flower[j];
							if (keepV[w]==0) // not a keeper? 
								toss=true;
						}
					}
					if (!toss) // got our alpha
						pdcel.alpha=pdcel.vertices[v].halfedge;
				}
			}
			// still nothing? choose first keeper interior
			if (pdcel.alpha==null) {
				for (int v=1;(v<=pdcel.vertCount && pdcel.alpha==null);v++) 
					if (keepV[v]!=0 && !isBouqBdry(bouquet,v))
						pdcel.alpha=pdcel.vertices[v].halfedge;
			}			
		}
		// No luck? throw exception
		if (pdcel.alpha==null)
			throw new CombException("Calling routine must identify a qualified 'alpha' edge");
		
		// else we have our 'alpha' vertex
		Vertex firstV=pdcel.alpha.origin; // should be interior

		// (7) Set 'alphaComp' true for possible keepers in the interior 
		//     connected component of 'firstV.vertIndx'
		boolean[] alphaComp=new boolean[pdcel.vertCount+1];
		keepV[firstV.vertIndx]=1;
		alphaComp[firstV.vertIndx]=true;
		NodeLink nextV=new NodeLink();
		nextV.add(firstV.vertIndx);
		NodeLink currV=null;
		while (nextV.size()>0) {
			currV=nextV;
			nextV=new NodeLink();
			Iterator<Integer> cvit=currV.iterator();
			while (cvit.hasNext()) {
				int v=cvit.next();
				for (int j=0;j<bouquet[v].length;j++) {
					int w=bouquet[v][j];
					if (keepV[w]<0) {
						keepV[w]=1;
						alphaComp[w]=true;
						nextV.add(w);
					}
				}
			} // done going through 'currV'
		} // end of while, so 'nextV' is empty

		// ===================== 'doneV' to track vertices ===================
		boolean[] doneV = new boolean[pdcel.vertCount + 1]; 

		// Part of bookkeeping is 'HalfEdge.util'. Initiate to 0;
		//    during processing, set for edges eliminated from being 
		//    added to the evolving redChain. Later, if found that 
		//    all edges from a vertex are excluded, set 'doneV' true.
		Iterator<HalfEdge> heit=pdcel.edges.iterator();
		while (heit.hasNext()) { // set all to 0  
			heit.next().util=0;
		}
		// First, all edges with origin not connected to firstV are done. 
		heit=pdcel.edges.iterator();
		while (heit.hasNext()) { 
			HalfEdge edge=heit.next();
			int ov=edge.twin.origin.vertIndx;
			if (keepV[ov]<0) {
				edge.util=1;
				edge.twin.util=1;
				doneV[ov]=true; 
			}
		}
		
		// =========== now we're going to build the 'redChain' ==========

		// Build list of edges as we encounter new faces: convention is that
		//   each 'HalfEdge' in 'orderEdges' is associated with its face (ie. that
		//   on its left). Keep updating 'doneV'.

		ArrayList<HalfEdge> orderEdges=new ArrayList<HalfEdge>(); 

		// Initial redChain is chain of outer edges cclw about 'firstV'.
		// Also begin the 'orderEdges' list from incoming spokes of 'firstV'
		ArrayList<HalfEdge> spokes = firstV.getEdgeFlower();
		HalfEdge he=spokes.get(0);
		he.origin.halfedge=he; // point firstV at this first spoke 
		orderEdges.add(he);
		
		// start of 'redChain'
		pdcel.redChain=new RedHEdge(he.next);
		he.twin.origin.halfedge=he.next;
		RedHEdge rtrace=pdcel.redChain;
		for (int k = 1; k < spokes.size(); k++) { // add rest about
			he = spokes.get(k);
			rtrace.nextRed=new RedHEdge(he.next);
			rtrace.nextRed.prevRed=rtrace;
			rtrace=rtrace.nextRed;
			orderEdges.add(he);
			markFaceUtils(he);			

			if (debug) { // debug=true;
				EdgeSimple es=new EdgeSimple(he.origin.vertIndx,
						he.twin.origin.vertIndx);
				DCELdebug.drawEdgeFace(p, es);
			}
			
			he.next.origin.halfedge=he.next;
		}
		rtrace.nextRed=pdcel.redChain;
		pdcel.redChain.prevRed=rtrace;
		doneV[firstV.vertIndx] = true; // engulfed

		// ======================= main loop ==============
		
		// loop through red chain as long as we keep adding faces
		//    or collapsing the redchain. \\ debug=true;
		RedHEdge currRed=null;
		boolean hit = true; // true if we added a face or collapsed an edge
		boolean redisDone=false; // totally done?
		while (hit && pdcel.redChain!=null && !redisDone) {
			hit = false;

			// current count of red chain as safety stop mechanism
			int redN=0;
			RedHEdge re=pdcel.redChain;
			do {
				redN++;
				re=re.nextRed;
			} while(re!=pdcel.redChain);
			
			// Degenerate? if redChain enclosed 2 verts and one/both keepers,
			//    then must be sphere, done.
			if (redN<=2 && (keepV[pdcel.redChain.myEdge.origin.vertIndx]!=0 ||
						keepV[pdcel.redChain.myEdge.twin.origin.vertIndx]!=0)) {
				pdcel.redChain=null;
				redisDone=true;
			}
			
			// look at 'redN'+1 successive 'RedHEdge's 
			for (int N=0;(N<=redN && !redisDone);N++) {
				currRed = pdcel.redChain;
				pdcel.redChain = currRed.nextRed; // set up pointer for next pass

				// check for degeneracy: triangulation of sphere
				if (isSphere(keepV,currRed)) {
					pdcel.redChain = null;
					redisDone=true;
					hit=false;
					break;
				}

				// ****************** main work *****************

				// processing the current red edge. 
				// working on v; u,v,w successive verts cclw along redchain 
				int v = currRed.myEdge.origin.vertIndx;
				
				if (debug) { // debug=true;
					DCELdebug.drawRedChain(pdcel.p,currRed);
					DCELdebug.printRedChain(pdcel.redChain);
				}
				
				// if v is not done yet 
				if (!doneV[v] && !(doneV[v]=isVertDone(currRed.myEdge))) { 
					// not done, so process fan of faces outside red chain
					HalfEdge upspoke = currRed.prevRed.myEdge.twin;
					HalfEdge downspoke = currRed.myEdge;

					// doubling back on itself? So u==w.
					// degeneracy to sphere is handled above;
					// If v is keeper: enclose it
					if (upspoke == downspoke) {
						upspoke.origin.halfedge=downspoke; // safety 
					
						// enclose v if a keeper (hence interior)
						if (keepV[v]!=0) { // enclose this vertex
							currRed.prevRed.prevRed.nextRed = currRed.nextRed;
							currRed.nextRed.prevRed = currRed.prevRed.prevRed;
							
							if (debug) {
//								System.err.println(v+" collapse done");
								CommandStrParser.jexecute(p,"disp -tfc218 "+v);
							}

							doneV[v] = true;
							currRed.nextRed.myEdge.origin.halfedge = 
									currRed.nextRed.myEdge;
							hit = true;
						}
					}
					
					// remaining cases, which break into cases
					else {
						// If v is a keeper (hence interior), we check if we can
						//   enclose it. If yes, then do that. Otherwise, pass on
						//   to following code where we try to add one cclw face.
						boolean canclose=false;
						RedHEdge redge=null;
						HalfEdge spktrace=upspoke;
						if (keepV[v]!=0) {
							redge=isMyEdge(pdcel.redChain,spktrace);
							while (spktrace!=downspoke && redge==null) {
								redge=isMyEdge(pdcel.redChain,(spktrace=spktrace.prev.twin));
							}
							if (redge==currRed) // yes, we can close up around v 
								canclose=true;
						}
						
						// now we can close up or we're ready to fall through
						RedHEdge cclw=currRed.prevRed.prevRed;
						if (canclose) { // yes, enclose v
							spktrace=upspoke;
							while (spktrace!=downspoke) { // add a new link
								cclw.nextRed=new RedHEdge(spktrace.next);
								cclw.nextRed.prevRed=cclw;
								orderEdges.add(spktrace);
								markFaceUtils(spktrace);			
								hit=true;
								
								if (debug) {
									EdgeSimple es=new EdgeSimple(spktrace.origin.vertIndx,
											spktrace.twin.origin.vertIndx);
									DCELdebug.drawEdgeFace(p, es);
								}
								
								spktrace=spktrace.prev.twin;
								cclw=cclw.nextRed;
							}
							cclw.nextRed=currRed.nextRed;
							currRed.nextRed.prevRed=cclw;

							if (debug) {
//								System.err.println(v+" flat, done");
								CommandStrParser.jexecute(p,"disp -tfc218 "+v);
							}

							doneV[v]=true;
						}
						// else we try to add just one cclw face about v;
						//    check that it's eligible and that one of ends is keeper
						else if (isMyEdge(pdcel.redChain,upspoke)==null &&
								(redge=isMyEdge(pdcel.redChain,upspoke.next))==null && 
								(keepV[v]!=0 || keepV[upspoke.twin.origin.vertIndx]!=0)) {
							cclw.nextRed=new RedHEdge(upspoke.next);
							orderEdges.add(upspoke);
							markFaceUtils(upspoke);			

							if (debug) {
								EdgeSimple es=new EdgeSimple(upspoke.origin.vertIndx,
										upspoke.twin.origin.vertIndx);
								DCELdebug.drawEdgeFace(p, es);
							}
							
							hit=true;
							cclw.nextRed.prevRed=cclw;
							cclw=cclw.nextRed;
							cclw.nextRed=new RedHEdge(upspoke.next.next);
							cclw.nextRed.prevRed=cclw;
							cclw=cclw.nextRed;
							cclw.nextRed=currRed;
							currRed.prevRed=cclw;
						}
					}
				} // done with v;
			} // done on this cycling through redchain
		} // end of main loop while, should be done creating red chain

		debug=false; // DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
	     // DCELdebug.EdgeOriginProblem(pdcel.edges);

		// DCELdebug.redChainEnds(pdcel.redChain);
				     
		// TODO: may want to reconfigure redChain to minimize the 
		//   occurrence of 'blue' elements --- successive red edges
		//   belonging to the same face. This will cause tough changes
		//   in 'layoutEdges' as well.
		
		// ============ identify red twins ==========================
		
		// If an edge is a red edge in both directions, then we decide
		//   whether these should be red twins. Rule: if both ends are 
		//   keepers  or one is a keeper and the other is either interior
		//   of not a keeper but neighbored a keeper, twin them. 'bdryNon[u]'
		//   true if nonkeeper only because 'u' is bdry.
		//   Also, mark all redChain vertices as done. 
		// The 'redChain' is not changed by this or subsequent operations,
		//   though it may gain new 'RedVertex's.

		RedHEdge nxtre=pdcel.redChain;
		do {
			if (nxtre.twinRed==null) {
				// is the opposite edge also red?
				RedHEdge crossRed=isMyEdge(pdcel.redChain,nxtre.myEdge.twin);
				if (crossRed!=null) {
					int v=nxtre.myEdge.origin.vertIndx;
					int w=crossRed.myEdge.origin.vertIndx;
					if (alphaComp[v] || alphaComp[w]) {
						nxtre.twinRed=crossRed;
						crossRed.twinRed=nxtre;
					}
				}
			}
			doneV[nxtre.myEdge.origin.vertIndx]=true;
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);

		// ========= create and swap out 'PreRedVertex's =============
		
		// DCELdebug.redChainEnds(pdcel.redChain);
		
		rtrace=pdcel.redChain;
		do {
			int v=rtrace.myEdge.origin.vertIndx;
			// check if already converted
			if (!(pdcel.vertices[v] instanceof PreRedVertex)) {
				PreRedVertex redV=new PreRedVertex(v);
				redV.halfedge=pdcel.vertices[v].halfedge;
				redV.halfedge.origin=redV;
				redV.num=bouquet[v].length-1;
				redV.closed=bouquet[v][0]==bouquet[v][redV.num]; // closed flower?
				redV.redSpoke=new RedHEdge[redV.num+1];
				redV.inSpoke=new RedHEdge[redV.num+1];
				rtrace.myEdge.origin=redV;
				pdcel.vertices[v]=redV;
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain); // DCELdebug.printRedChain(pdcel.redChain);

		// DCELdebug.redChainEnds(pdcel.redChain);

		// record 'redSpoke', 'inSpoke' hits 
		rtrace=pdcel.redChain;
		do {
			int v=rtrace.myEdge.origin.vertIndx;
			int w=rtrace.myEdge.twin.origin.vertIndx;
			PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];
			PreRedVertex rW=(PreRedVertex)pdcel.vertices[w];
			int j=-1;
			for (int k=0;(k<=rV.num && j<0);k++) { 
				if (bouquet[v][k]==w) {
					j=k;
					rV.redSpoke[j]=rtrace;
				}
			}
			j=-1;
			for (int k=0;(k<=rW.num && j<0);k++) {
				if (bouquet[w][k]==v) {
					j=k;
					rW.inSpoke[j]=rtrace;
				}
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);


		// DCELdebug.redChainEnds(pdcel.redChain);

		// =========== process to get 'RedVertex's =============
		
		// The 'redChain' of 'RedHEdge's has not changed, but some of
		//   the 'RedVertex's it passes through will be new as we
		//   process the 'PreRedVertex's.
		// Pass through the redChain. When you encounter a 'PreRedVertex', 
		//   then it is processed (after rotating, if necessary); 
		//   this entry in 'pdcel.vertices' converts to a 'RedVertex' 
		//   and new 'RedVertex's may be introduced elsewhere in the redChain.
		ArrayList<Vertex> addedVertices=new ArrayList<Vertex>(); // new vertices
		rtrace=pdcel.redChain;
		do {
			int v=Math.abs(rtrace.myEdge.origin.vertIndx);
			// if not processed, then it's siblings are not created yet either
			if (pdcel.vertices[v] instanceof PreRedVertex) { 
				PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];

				// debug=true;
				if (debug) {
					DCELdebug.redVertFaces(rV); // DCELdebug.vertFaces(newVertices.get(31));
				}

				// process, convert to 'RedVertex's, possibly create new ones
				ArrayList<RedVertex> redAdded=rV.process();
					
//				System.out.println(" next red v = "+v);

				// first of the new reds replaces the original
				RedVertex newV=redAdded.get(0);
				pdcel.vertices[v]=newV;

				// any remaining must be indexed and added to 'newVertices'
				int sz=redAdded.size();
				if (sz>1) 
					for (int j=1;j<sz;j++) 
						addedVertices.add(redAdded.get(j));
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		debug=false;
		
		// ========== set new edge next/prev =============
		
		// redChain vertices that are interior, got no new edges;
		// others got a new twin for the 'inSpoke' of their fan
		rtrace=pdcel.redChain;
		do {
			RedVertex rvert=(RedVertex)rtrace.myEdge.origin;
//System.out.println(rvert.vertIndx+"  --> ");			
			if (rvert.bdryFlag==1) {
				int num=rvert.spokes.length-1;
				rvert.spokes[0].twin.next=rvert.spokes[num];
				rvert.spokes[num].prev=rvert.spokes[0].twin;
			}
//			else
//				System.out.println(" vert "+rvert.vertIndx+" is not boundary");
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		
		if (debug) { // debug=true;
			DCELdebug.RedOriginProblem(pdcel.redChain);
			rtrace=pdcel.redChain;
			int safety=pdcel.vertCount;
			do {
				DCELdebug.vertFaces(rtrace.myEdge.origin);
				rtrace=rtrace.nextRed;
				safety--;
			} while (rtrace!=pdcel.redChain && safety>0);
		}

	     // DCELdebug.EdgeOriginProblem(pdcel.edges);

		// ============== Faces and Edges: ==================================

		// We account for these anew based entirely on 'orderEdges'; we index
		//   edges face by face, including the ideal faces later, and we create 
		//   new faces (most will be garbage'd) 
//		heit=orderEdges.iterator();
//		while(heit.hasNext()) {
//			HalfEdge ne=heit.next();
//			ne.edgeIndx=0;
//			ne.face=new Face();
//		}
				
		// Use 'orderEdges' (which should be unchanged with redChain work): 
		//    * index interior faces, 
		//    * catalog and re-index all edges, face=by=face
		//    * set 'LayoutOrder', adding faces only if opposite vert not hit
		//    * find first face that places a 'RedVertex'; this will be 'redChain'
		pdcel.edges=new ArrayList<HalfEdge>();
		pdcel.edges.add(null); // null in first spot
		pdcel.faces=new ArrayList<Face>();
		pdcel.faces.add(null); // null in first spot
		RedHEdge newRedChain=null;
		pdcel.LayoutOrder=new ArrayList<Face>();
		int[] vhit=new int[pdcel.vertCount+1];
		int ftick=0;
		int etick=0;
		
		// refresh 'alpha', get 'edges' and 'faces' st
		pdcel.alpha=orderEdges.get(0);
		vhit[pdcel.alpha.origin.vertIndx]=1;
		vhit[pdcel.alpha.twin.origin.vertIndx]=1;
				
		// Each edge in 'orderEdges' is associated with a new face,
		//   namely, it's own face, and in turn 'face.edge' is this edge.
		Iterator<HalfEdge> oit=orderEdges.iterator();
		while (oit.hasNext()) {
			HalfEdge hfe=oit.next();
// System.out.println(" hfe: "+hfe.toString());			
			hfe.edgeIndx=++etick;
			pdcel.edges.add(hfe);
			Face newface=new Face();
			hfe.face=newface;
			newface.edge=hfe;
			newface.faceIndx=++ftick;
			pdcel.faces.add(newface);
			
			// get the other edges of this face (typically 2 others)
			HalfEdge nxe=hfe.next;
			int safety=20;
			do {
				nxe.edgeIndx=++etick;
				pdcel.edges.add(nxe);
				nxe.face=newface;
				nxe=nxe.next;
				safety--;
			} while (nxe!=hfe && safety>0);
			if (safety==0) {
				throw new DCELException("run away face edge tracing");
			}
								
			if (debug) { // debug=true;
				int hfev=pdcel.newOld.findW(hfe.origin.vertIndx);
				int hfew=pdcel.newOld.findW(hfe.twin.origin.vertIndx);
				EdgeSimple es=new EdgeSimple(hfev,hfew);
				DCELdebug.drawEdgeFace(pdcel.p,es);
				System.out.println("["+hfe.origin.vertIndx+","+hfe.twin.origin.vertIndx+
						"] (or in origin indexing, "+es.toString()+")");
			}

			// check second edge around and opposite vertex 
			HalfEdge sea=hfe.next.next;
			int oppv=sea.origin.vertIndx;
			if (vhit[oppv]==0) { // yes, add 'hfe' to 'LayoutOrder'
				vhit[oppv]=1;
				pdcel.LayoutOrder.add(hfe.face);
			}
			
			// check for appropriate 'redChain' start: namely, is 'oppv'
			//   red and its edge 'RedHEdge'?
			// DCELdebug.RedOriginProblem(pdcel.redChain);
			if (newRedChain==null && sea.origin instanceof RedVertex) { 
				newRedChain=isMyEdge(pdcel.redChain,sea); // may still be null
			}
		} // end of while through 'orderEdges'
		pdcel.intFaceCount=ftick;

		debug=false;
		// DCELdebug.printRedChain(pdcel.redChain); 
		
		// ======== Catalog side pairings, free sides, create ideal faces ========
		pdcel.sideStarts=new ArrayList<RedHEdge>();
		pdcel.bdryStarts=new ArrayList<RedHEdge>();
		pdcel.idealFaces=new ArrayList<Face>();
		int sidecount=0; 
		nxtre=pdcel.redChain;
		int safety=1000;
		RedHEdge stopEdge=null;
		do {

			// DCELdebug.redChainEnds(pdcel.redChain);
			
			// look for unpasted redChain edge, hence a free side.
			if (nxtre.twinRed==null) {
//System.out.println(" enter free vert = "+nxtre.prevRed.myEdge.origin.vertIndx);					

				++sidecount; // index is non-zero
				
				// search upstream to find first edge
				rtrace=nxtre.prevRed;
				while (rtrace.twinRed==null && rtrace!=nxtre) {
					rtrace=rtrace.prevRed;
				}

				// check if simply connected
				if (rtrace==nxtre) {
					Face newface=new Face(); // new ideal face
					newface.faceIndx=-(++ftick);
					pdcel.idealFaceCount++;
					pdcel.idealFaces.add(newface);
					pdcel.redChain.myEdge.twin.face=newface;
					newface.edge=pdcel.redChain.myEdge.twin;
					pdcel.bdryStarts.add(pdcel.redChain);
					pdcel.sideStarts.add(pdcel.redChain);
					
// System.out.println("sc: rtrace="+rtrace.toString());

					// go around and catalog
					rtrace=pdcel.redChain;
					do {
						rtrace.mobIndx=sidecount;
						rtrace.myEdge.twin.edgeIndx=++etick;
						pdcel.edges.add(rtrace.myEdge.twin);
						rtrace.myEdge.twin.face=newface;
						rtrace=rtrace.nextRed;
					} while (rtrace!=pdcel.redChain);
					break;
				}
					
				// else we have the start:
				RedHEdge freeStart=rtrace.nextRed;
// System.out.println(" freeStart = "+freeStart.myEdge.origin.vertIndx);					
				if (stopEdge==null)
					stopEdge=freeStart;
				Face newface=new Face(); // new ideal face
				newface.faceIndx=-(++ftick);
				newface.edge=freeStart.myEdge.twin;
				pdcel.idealFaceCount++;
				pdcel.idealFaces.add(newface);
				pdcel.bdryStarts.add(freeStart);
				pdcel.sideStarts.add(freeStart); 
				
				// propagate downstream
				rtrace=freeStart;
				do {
					rtrace.mobIndx=sidecount;
					rtrace.myEdge.twin.edgeIndx=++etick;
					pdcel.edges.add(rtrace.myEdge.twin);
					rtrace.myEdge.twin.face=newface;
					
// System.out.println("free rtrace = <"+rtrace.myEdge.origin.vertIndx+","+
//		 rtrace.myEdge.twin.origin.vertIndx+">");
					
					rtrace=rtrace.nextRed;
				} while (rtrace.twinRed==null);
				
				if (debug) { // debug=true;
					DCELdebug.faceVerts(freeStart.myEdge);
				}
				
				nxtre=rtrace;
			}
			// else, get side pairing; move back, then forward, while twins match
			else {
				RedHEdge twinre=nxtre.twinRed;
				nxtre.mobIndx=++sidecount;
				twinre.mobIndx=-sidecount; // negative of its twin
				
				// first look upstream to find start
				rtrace=nxtre.prevRed;
				RedHEdge twintrace=twinre.nextRed;
				while (rtrace.twinRed==twintrace && twintrace.twinRed==rtrace && rtrace.mobIndx==0) {
					rtrace.mobIndx=sidecount;
					rtrace=rtrace.prevRed;
					twintrace.mobIndx=-sidecount;
					twintrace=twintrace.nextRed;
				}
				rtrace=nxtre.nextRed;
				pdcel.sideStarts.add(rtrace);
// System.out.println(" pastedStart = "+rtrace.myEdge.origin.vertIndx);					
				if (stopEdge==null)
					stopEdge=rtrace;
				
				// look downstream for start of pasted edge
				twintrace=twinre.prevRed;
				while (rtrace.twinRed==twintrace && twintrace.twinRed==rtrace && rtrace.mobIndx==0) {
					rtrace.mobIndx=sidecount;
					rtrace=rtrace.nextRed;
					twintrace.mobIndx=-sidecount; // negative to allow match with paired side
					twintrace=twintrace.prevRed;
				}
				pdcel.sideStarts.add(rtrace.prevRed);
				nxtre=rtrace;
			}
			safety--;
		} while (nxtre!=stopEdge && safety>0);
		if (safety==0) {
			System.err.println("Failed in indexing free side.");
		}

		// ============== Verteces, Reorganize =================
		// catalog all live vertices: namely, 'keepV=1' for interior 
		//   connected to alpha, 'redChain' vertices, 'addedVertex', new vertices
		ArrayList<Vertex> newVertices=new ArrayList<Vertex>(); 
		VertexMap newold=new VertexMap();
		int vcount=0;
		vhit=new int[pdcel.vertCount+1];
		
		// get all interiors first
		for (int v=1;v<=pdcel.vertCount;v++) {
			Vertex vt=pdcel.vertices[v];
			if (keepV[v]==1) {
				vt.vertIndx=++vcount;
				newVertices.add(vt);
				newold.add(new EdgeSimple(vcount,v));
				vhit[v]=1;
			}
		}
		
		// pick up additional redChain vertices
		for (int v=1;v<=pdcel.vertCount;v++) {
			Vertex vt=pdcel.vertices[v];
			if (vhit[v]==0 && (vt instanceof RedVertex)) {
				vt.vertIndx=++vcount;
				newVertices.add(vt);
				newold.add(new EdgeSimple(vcount,v));
				vhit[v]=1;
			}
		}
		
		// pick up vertices added during 'PreRedVertex.process()'.
		Iterator<Vertex> adit=addedVertices.iterator();
		while (adit.hasNext()) {
			Vertex vt=adit.next();
			int oldIndx=vt.vertIndx;
			vt.vertIndx=++vcount;
			newVertices.add(vt);
			newold.add(new EdgeSimple(vcount,oldIndx));
		}
		pdcel.vertCount=vcount;
		pdcel.vertices=new Vertex[vcount+1];
		Iterator<Vertex> vit=newVertices.iterator();
		while (vit.hasNext()) {
			Vertex vt=vit.next();
			pdcel.vertices[vt.vertIndx]=vt;
		}
		pdcel.newOld=newold;

		// ======================== should be done ==========
		pdcel.faceCount=ftick;
		pdcel.euler=pdcel.vertCount -pdcel.edges.size()/2 + pdcel.faceCount;
		if (newRedChain!=null)
			pdcel.redChain=newRedChain;

		return pdcel;
	}

	/**
	 * Find index of w in flower of v. See 'PackData.nghb'
	 * @param bouq int[][], array of flowers
	 * @param v    int
	 * @param w    int
	 * @return int, -1 on error
	 */
	public static int nghb(int[][] bouq,int v, int w) {
		int len = bouq.length - 1;
		if (v < 1 || v > len || w < 1 || w > len)
			return -1;
		int[] flower = bouq[v];
		for (int j = 0; j <= flower.length; j++)
			if (flower[j] == w)
				return j;
		return -1;
	}
	
	/**
	 * Is w a boundary vertex according to bouquet?
	 * @param bouquet int[][]
	 * @param w int
	 * @return boolean
	 */
	public static boolean isBouqBdry(int[][] bouq,int w) {
		if (w<1 || w>=bouq.length)
			return false;
		int[] flower=bouq[w];
		if (flower[0]!=flower[flower.length-1])
			return true;
		return false;
	}

	/**
	 * Check if red chain has degenerated to two edges and one 
	 * end or other is a keeper. This must be a sphere (possibly
	 * with one non-keeper).  
	 * @param keepv int[]
	 * @param rededge RedHEdge
	 * @return boolean
	 */
	public static boolean isSphere(int[] keepv,RedHEdge rededge) {
		if (rededge.nextRed!=rededge.prevRed) 
			return false;
		int v=rededge.myEdge.origin.vertIndx;
		int w=rededge.prevRed.myEdge.origin.vertIndx;
		if (keepv[v]!=0 || keepv[w]!=0)
			return true;
		return false;
	}
	
	/**
	 * Find 'RedHEdge' from redChain whose 'myEdge' is equal to given 'edge'
	 * @param redchain RedHEdge
	 * @param edge HalfEdge
	 * @return RedHEdge or null
	 */
	public static RedHEdge isMyEdge(RedHEdge redchain,HalfEdge edge) {
		if (redchain==null)
			return null;
		RedHEdge rtrace=redchain;
		do {
			if (rtrace.myEdge==edge)
				return rtrace;
			rtrace=rtrace.nextRed;
		} while(rtrace!=redchain);
		return null;
	}

	/**
	 * set 'util' to 1 for all edges around face defined by this 'edge'
	 * @param edge HalfEdge
	 */
	public static void markFaceUtils(HalfEdge edge) {
		HalfEdge he=edge;
		do {
			he.util=1;
			he=he.next;
		} while(he!=edge);
	}

	/**
	 * check if all all edges from 'edge' origin vertex
	 * are set to 1.
	 * @param edge
	 * @return boolean, false if some 'util' is zero
	 */
	public static boolean isVertDone(HalfEdge edge) {
		HalfEdge he=edge;
		do {
			if (he.util==0)
				return false;
			he=he.prev.twin; // cclw search
		} while(he!=edge);
		return true;
	}

}