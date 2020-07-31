package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import deBugging.DCELdebug;
import exceptions.CombException;
import komplex.EdgeSimple;
import listManip.NodeLink;
import listManip.VertexMap;

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
			Vertex newV = new Vertex();
			newV.vertIndx = v;
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
				int indx_wv = nghb(w, v, bouquet);
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
				m = nghb(w, v, bouquet);
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
		// still didn't work?
		if (alpha==null) {
			alpha=heArrays[firstInterior][0];
		}
		
		// Done, so now populate the DCEL
		pdcel.redChain=null;
		pdcel.vertCount=vertcount;
		pdcel.alpha=alpha;
		pdcel.faceCount=0;
		pdcel.intFaceCount=0;
		pdcel.vertices=vertices;
		pdcel.edges=edges;
		pdcel.faces=null;
		pdcel.idealFaces=null;
		pdcel.euler = 0;
			
		return vertcount;
	}
	
	/**
	 * Given a minimal DCEL (with vertices, edges, and faces)
	 * and a list of its 'Vertex's, possibly emtpy, generate 
	 * a new DCEL which omits vertices not in 'arrayV'.
	 * It creates new face indices, idealfaces, redchain, sides, 
	 * and so forth, returning a fully prepared 'PackDCEL'.
	 * The "red" chain is a closed cclw chain of edges about a
	 * simple connected fundamental domain within the complex and
	 * containing the specified vertices of the given DCEL.
	 * (Note that due to combinatorics, some vertices of 'arrayV'
	 * may ultimately be excluded.)
	 * @param bouquet int[][], normal bouquet
	 * @param arrayV NodeLink, vertices to keep, if null, keep all
	 * @return PackDCEL
	 */
	public static PackDCEL redDCELbuilder(int[][] bouquet, NodeLink arrayV,int alphaIndx) {
		boolean debug=false;
		PackDCEL pdcel=new PackDCEL(); 
		createVE(pdcel,bouquet,alphaIndx);
		int vertcount=pdcel.vertCount;

		if (debug) {
			DCELdebug.showEdges(pdcel);
			DCELdebug.log_full(pdcel);
		}
		
		// identify vertices intended to be keepers
		int[] keepV = new int[pdcel.vertCount + 1];
		if (arrayV!=null && arrayV.size()>0) {
			Iterator<Integer> vit=arrayV.iterator();
			while (vit.hasNext()) {
				int v=vit.next();
				if (v>0 && v<=vertcount && keepV[v]==0) {
					keepV[v]=1;
				}
			}
		}
		else { // else mark all as keepers
			for (int k = 1; k <= pdcel.vertCount; k++)
							keepV[k] = 1;
		}
		
		// in any case, for now mark bdry as non-keepers
		for (int k = 1; k <= pdcel.vertCount; k++) 
			if (isBouqBdry(bouquet,k)) 
				keepV[k]=0;
		
		// =======
		// Identify appropriate 'alpha'; * keeper, * interior.
		//    prefer interior keeper petals as well.
		int alph=pdcel.alpha.origin.vertIndx;
		if (keepV[alph]==0) { // oops, not a keeper
			pdcel.alpha=null;
			for (int v=1;(v<=pdcel.vertCount && pdcel.alpha==null);v++) {
				int[] flower=bouquet[v];
				boolean toss=false;
				if (keepV[v]!=0 && !isBouqBdry(bouquet,v)) {
					for (int j=0;(j<flower.length && !toss);j++) {
						int w=flower[j];
						if (isBouqBdry(bouquet,w) || keepV[w]==0) // bdry or not a keeper? 
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
		// No luck
		if (pdcel.alpha==null)
			throw new CombException("No qualified 'alpha' edge was identified");

		// =========== now we're going to build the 'redChain' ==========

		// build 'orderEdges' as we encounter new faces: convention is that
		//   'HalfEdge' in the list is associated with its face (ie. that
		//   on its left).
		int[] doneV = new int[pdcel.vertCount + 1]; // 1 if engulfed
		ArrayList<HalfEdge> orderEdges=new ArrayList<HalfEdge>(); 
		Vertex firstV=pdcel.alpha.origin; // should be interior

		// Initial redChain is chain of outer edges cclw about firstV.
		// Also begin the 'orderEdges' list from spoke twins.
		ArrayList<HalfEdge> spokes = firstV.getEdgeFlower();
		HalfEdge he=spokes.get(0);
		he.origin.halfedge=he; // point firstV at this first spoke 
		orderEdges.add(he);
		pdcel.redChain=new RedHEdge(he.next); // start of 'redChain'
		he.twin.origin.halfedge=he.next;
		RedHEdge rtrace=pdcel.redChain;
		for (int k = 1; k < spokes.size(); k++) { // add rest about
			he = spokes.get(k);
			rtrace.nextRed=new RedHEdge(he.next);
			rtrace.nextRed.prevRed=rtrace;
			rtrace=rtrace.nextRed;
			orderEdges.add(he);
			he.next.origin.halfedge=he.next;
		}
		rtrace.nextRed=pdcel.redChain;
		pdcel.redChain.prevRed=rtrace;
		doneV[firstV.vertIndx] = 1; // engulfed

		// ======================= main loop ==============
		// loop through red chain as long as we keep adding faces
		//    or collapsing the redchain.
//		boolean tmpdebug=false;
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
			if (redN<=2) {
				pdcel.redChain=null;
				break;
			}
			
			// look at 'redN'+1 successive 'RedHEdge's 
			for (int N=0;(N<=redN && !redisDone);N++) {
				
				if (debug) {
					deBugging.DCELdebug.printRedChain(currRed);
					deBugging.DCELdebug.halfedgeends(pdcel, pdcel.redChain.myEdge);
				}
				
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
				int u = currRed.prevRed.myEdge.origin.vertIndx;
				int w = currRed.myEdge.twin.origin.vertIndx;
				
//				if (tmpdebug) { // tmpdebug=true;
//					DCELdebug.printRedChain(currRed);
//				}
				
				// if v is not done yet
				if (doneV[v] ==0 ) { 
					// not done, so process fan of faces outside red chain
					HalfEdge upspoke = currRed.prevRed.myEdge.twin;
					HalfEdge downspoke = currRed.myEdge;

					// doubling back on itself? So u==w.
					//  Three possibilites:
					//  (1) degeneracy to sphere: handled above
					//  (2) v is keeper: enclose it
					//  (3) v is not a keeper: no change, just mark 'util' for later
					if (upspoke == downspoke) {
						upspoke.origin.halfedge=downspoke; // safety 
						
						// enclose v if a keeper (hence interior)
						if (keepV[v]!=0) { // enclose this vertex
							currRed.prevRed.prevRed.nextRed = currRed.nextRed;
							currRed.nextRed.prevRed = currRed.prevRed.prevRed;
//System.err.println(v+" got done");							
							doneV[v] = 1;
							currRed.nextRed.myEdge.origin.halfedge = currRed.nextRed.myEdge;
							hit = true;
						}
						// mark to avoid pasting later
						else {
							currRed.util=1;
							currRed.prevRed.util=1;
						}
					}
					
					// Done? none of these are keepers? done with v
					else if (keepV[v]==0 && keepV[u]==0 && keepV[w]==0) {
						downspoke.origin.halfedge=downspoke;
//System.err.println(v+" done");							
						doneV[v] = 1;
					}

					// remaining cases, which break into cases
					else {
						// If v is a keeper (hence interior), we check if we can
						//   enclose it. If yes, then do that. Otherwise, pass on
						//   to next code where we try to add one cclw face.
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
								hit=true;
								spktrace=spktrace.prev.twin;
								cclw=cclw.nextRed;
							}
							cclw.nextRed=currRed.nextRed;
							currRed.nextRed.prevRed=cclw;
//System.err.println(v+" is done");							
							doneV[v]=1;
						}
						// else we try to add just one cclw face about v;
						//    check that it's eligible and that one or other end is keeper
						else if ((redge=isMyEdge(pdcel.redChain,upspoke.next))==null && 
								(keepV[v]!=0 || keepV[upspoke.twin.origin.vertIndx]!=0)) {
							cclw.nextRed=new RedHEdge(upspoke.next);
							orderEdges.add(upspoke);
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
		} // end of main loop while, should be done with red chain

//		tmpdebug=false;
		
		// TODO: may want to reconfigure redChain to minimize the 
		//   occurrence of 'blue' elements --- successive red edges
		//   belonging to the same face.
		
		// -------- finish dcel structure, red pastings, find sides -----------

		// Count how many times each redchain vert is visited
		int[] redverts=new int[vertcount+1];
		RedHEdge nxtre=pdcel.redChain;
		do {
			int v=nxtre.myEdge.origin.vertIndx;
			redverts[v]++;
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);

		// create 'ChokeData' to record outgoing/incoming 'RedHEdge's
		ChokeData []chokeData=new ChokeData[vertcount+1];
		nxtre=pdcel.redChain;
		do {
			int v=nxtre.myEdge.origin.vertIndx;
			chokeData[v]=new ChokeData();
			chokeData[v].vert=v;
			chokeData[v].redSpokes=new RedHEdge[redverts[v]];
			chokeData[v].inSpokes=new RedHEdge[redverts[v]];
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);

		// catalog outgoing, then incoming 'RedHEdge's
		int[] outhits=new int[vertcount+1];
		nxtre=pdcel.redChain;
		do {
			int v=nxtre.myEdge.origin.vertIndx; // out from v
			chokeData[v].redSpokes[outhits[v]]=nxtre;
			outhits[v]++;
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		int[] inhits=new int[vertcount+1];
		nxtre=pdcel.redChain;
		do {
			int w=nxtre.myEdge.twin.origin.vertIndx; // in from w
			chokeData[w].inSpokes[inhits[w]]=nxtre;
			inhits[w]++;
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		if (debug && pdcel.redChain!=null) { // debug=truw;
			DCELdebug.printRedChain(pdcel.redChain);
			debug=false;
		}
		
		nxtre=pdcel.redChain;
		do {
			Vertex vert=nxtre.myEdge.origin;
			int v=vert.vertIndx;

			ChokeData cdata=chokeData[v];
				
			// get flower for origin based on first emerging red spoke
			// (Careful: we need the corresponding 'HalfEdge' versus 'RedHEdge'.)
			HalfEdge start=cdata.redSpokes[0].myEdge.next.prev;
			ArrayList<Integer> everts=new ArrayList<Integer>();
			everts.add(start.origin.vertIndx);
			HalfEdge ehe=start.prev.twin;
			while(ehe!=start) {
				everts.add(ehe.origin.vertIndx);
				ehe=ehe.prev.twin;
			}
			int flen=everts.size();
			
			// identify in/out directions in the flower
			cdata.outEdge=new RedHEdge[flen];
			cdata.inEdge=new RedHEdge[flen];
			for (int k=0;k<cdata.redSpokes.length;k++) {
				int rw=cdata.redSpokes[k].myEdge.twin.origin.vertIndx;
				int iw=cdata.inSpokes[k].myEdge.twin.origin.vertIndx;
				for (int j=0;j<everts.size();j++) {
					int vv=everts.get(j);
					if (vv==rw)
						cdata.outEdge[j]=cdata.redSpokes[k];
					if (vv==iw)
						cdata.inEdge[j]=cdata.inSpokes[k];
				}
			}
			
			// count double hits: edges both in and out
			cdata.doublecount=0;
			for (int j=0;j<flen;j++) {
				if (cdata.outEdge[j]!=null && cdata.inEdge[j]!=null)
					cdata.doublecount++;
			}
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		
		// Vertices: ========================================
		
		// mark redChain vertices as done.
		nxtre=pdcel.redChain;
		do {
			doneV[nxtre.myEdge.origin.vertIndx]=1;
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		// gather and re-index all done 'Vertex's
		ArrayList<Vertex> verts=new ArrayList<Vertex>();
		pdcel.newOld=new VertexMap();
		int tick=0;
		for (int m=1;m<=pdcel.vertCount;m++) { 
			if (doneV[pdcel.vertices[m].vertIndx]!=0) {
				Vertex v=pdcel.vertices[m];
				tick++;
				pdcel.newOld.add(new EdgeSimple(tick,v.vertIndx));
				v.vertIndx=tick;
				verts.add(v);
			}
		}
		pdcel.vertCount=tick; // this is new vertCount
		pdcel.vertices=new Vertex[pdcel.vertCount+1];
		Iterator<Vertex> vit=verts.iterator();
		tick=0;
		while (vit.hasNext()) {
			pdcel.vertices[++tick]=vit.next();
		}
		
		
		// ========== Process redChain and bdry =================
		// Several stages: 
		//   (1) mark switchback red edges, those are across from a red edge,
		//       but are not to be pasted.
		//       E.g., edges from vert with single outgoing red spoke. Moreover, 
		//       have to check for seam of edges ending in such a vertex, as these
		//       also should not get red twin.
		//   (2) Establish red-to-red twins when red edges are to be identified;
		//       note that normal twins persist, both for pasted and unpasted 
		//       'redChain' edges.
		//   (3) find free sides, set up ideal faces, etc.
		// 
		// The 'redChain' itself should not change in this processing

		// RedChain: ====================================================
		// We use 'chokeData' to find free and paired red edges.
		
		// Step (1): ----------- use 'util' to mark red edges not needing twins
		nxtre=pdcel.redChain;
		do { // set 'util's to 0
			nxtre.util=0; 
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		// Set 'util' for red edges, with verts having single 'redSpoke'
		boolean gotone=true;
		nxtre=pdcel.redChain;
		do {
			int v=nxtre.myEdge.origin.vertIndx;
			if (redverts[v]==1) {
				RedHEdge re=chokeData[v].redSpokes[0];
				re.util=1;
				re.prevRed.util=1;
				gotone=true;
			}
		nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		// find (successively) verts with 2 red spokes and red 
		//   with 'util' already marked. These should have 
		//   spokes marked as 'util' as well
		while (gotone) {
			gotone=false;
			nxtre=pdcel.redChain;
			do {
				int v=nxtre.myEdge.origin.vertIndx;
				ChokeData cdata=chokeData[v];
				int k=cdata.inEdge.length;
				if (redverts[v]==2 && cdata.doublecount==2) { 
					boolean hitutil=false;
					for (int j=0;(j<k && !hitutil);j++) {
						if ((cdata.outEdge[j]!=null && cdata.outEdge[j].util!=0) || 
								(cdata.inEdge[j]!=null && cdata.inEdge[j].util!=0))
							hitutil=true;
					}
					if (hitutil) {
						gotone=true;
						cdata.doublecount=0; // shouldn't need to check this ChokeData again
					}
				}
				nxtre=nxtre.nextRed;
			}  while (nxtre!=pdcel.redChain);
		}
					
		// Step (2): --------- survey verts to find remaining red matchup to twins	
		nxtre=pdcel.redChain;
		do {
			Vertex vert=nxtre.myEdge.origin;
			int v=vert.vertIndx;
			ChokeData cdata=chokeData[v];
			if (cdata.doublecount>0) {
				RedHEdge outRed=null;
				RedHEdge inRed=null;
				int k=cdata.inEdge.length;
				for (int j=0;j<k;j++) {
					outRed=cdata.outEdge[j];
					inRed=cdata.inEdge[j];
					if (outRed!=null && inRed!=null && outRed.util==0 && inRed.util==0) {
						outRed.twinRed=inRed;
						inRed.twinRed=outRed;
					}
				}
			}
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);

		// Step 3: catalog side pairings, free sides, create ideal faces.
		pdcel.sideStarts=new ArrayList<RedHEdge>();
		pdcel.bdryStarts=new ArrayList<RedHEdge>();
		pdcel.idealFaces=new ArrayList<Face>();
		int sidecount=0; 
		nxtre=pdcel.redChain;
		do {
			// look for unpasted redChain edge, hence a free side.
			//    For free edges, fix 'myEdge' next/prev to form a closed
			//    linked chain about the boundary; this may cut out some 
			//    original edges that are dead. 
			if (nxtre.twinRed==null) {
				nxtre.mobIndx=++sidecount; // index is non-zero
				Face newface=new Face(); // new ideal face
				pdcel.idealFaces.add(newface);
				nxtre.myEdge.twin.face=newface;
				nxtre.myEdge.twin.face.edge=nxtre.myEdge.twin;  // point to normal twin of red edge
				
				// propagate free side upstream to find its first edge 
				RedHEdge trace=nxtre.prevRed;
				while (trace.twinRed==null && trace!=nxtre) {
					trace.mobIndx=sidecount;
					trace.myEdge.twin.face=newface;
					trace.myEdge.twin.prev=trace.prevRed.myEdge.twin;
					trace.prevRed.myEdge.twin.next=trace.myEdge.twin;
					trace=trace.prevRed;
				}

				// maybe simply connected?
				if (trace==nxtre) {
					nxtre=pdcel.redChain;
					nxtre.prevRed.myEdge.twin.next=nxtre.myEdge.twin;
					nxtre.myEdge.twin.prev=nxtre.prevRed.myEdge.twin;
					pdcel.sideStarts.add(nxtre);
					newface.edge=nxtre.myEdge.twin;
					break;
				}
				
				// else have the start
				RedHEdge freeStart=trace.nextRed;
				pdcel.bdryStarts.add(freeStart);
				pdcel.sideStarts.add(freeStart); // for completeness sake
				newface.edge=freeStart.myEdge.twin;
				
				// now have to propagate downstream
				trace=nxtre.nextRed;
				while (trace.twinRed==null && trace!=nxtre) {
					trace.mobIndx=sidecount;
					trace.myEdge.twin.face=newface;
					trace.myEdge.twin.next=trace.nextRed.myEdge.twin;
					trace.nextRed.myEdge.twin.prev=trace.myEdge.twin;
					trace=trace.nextRed;
				}
				trace.prevRed.myEdge.twin.prev=freeStart.myEdge.twin;
				freeStart.myEdge.twin.next=trace.prevRed.myEdge.twin;

				// Finally for this free edge, point 'origin.halfedge's
				//    in backward direction (forward direction wrt interior), though
				//    this may be ambiguous because ideal faces may not be 
				//    combinatorial polygons; some edges can be repeated in 
				//    opposite directions (but are not pasted).
				HalfEdge frtr=freeStart.myEdge.twin;
				do {
					frtr.twin.origin.halfedge=frtr.twin;
					frtr=frtr.next;
				} while (frtr!=freeStart.myEdge.twin);
				
				nxtre=trace;
			}
			// else, get side pairing; move back, then forward, while twins match
			else {
				RedHEdge twinre=nxtre.twinRed;
				nxtre.mobIndx=++sidecount;
				twinre.mobIndx=-sidecount; // negative of its twin
				
				// first look upstream to find start
				RedHEdge mytrace=nxtre.prevRed;
				RedHEdge twintrace=twinre.nextRed;
				while (mytrace.twinRed==twintrace && twintrace.twinRed==mytrace && mytrace.mobIndx==0) {
					mytrace.mobIndx=sidecount;
					mytrace=mytrace.prevRed;
					twintrace.mobIndx=-sidecount;
					twintrace=twintrace.nextRed;
				}
				pdcel.sideStarts.add(mytrace.nextRed);
				
				// look downstream for start of pasted edge
				mytrace=nxtre.nextRed;
				twintrace=twinre.prevRed;
				while (mytrace.twinRed==twintrace && twintrace.twinRed==mytrace && mytrace.mobIndx==0) {
					mytrace.mobIndx=sidecount;
					mytrace=mytrace.nextRed;
					twintrace.mobIndx=-sidecount; // negative to allow match with paired side
					twintrace=twintrace.prevRed;
				}
				pdcel.sideStarts.add(mytrace.prevRed);
				nxtre=mytrace;
			}
		} while (nxtre!=pdcel.redChain);
		
		// Faces and Edges: ================================================
		// 
		// zero out 'edgeIndx's and create faces for every edge
		Iterator<HalfEdge> eit=pdcel.edges.iterator();
		while(eit.hasNext()) {
			HalfEdge ne=eit.next();
			ne.edgeIndx=0;
			ne.face=new Face();
		}
		
		// Using 'orderEdges':
		//    * index interior faces, 
		//    * catalog and re-index edges
		//    * set 'LayoutOrder', adding faces only if opposite vert not hit
		pdcel.LayoutOrder=new ArrayList<Face>();
		int []vhit=new int[pdcel.vertCount+1];
		vhit[pdcel.alpha.origin.vertIndx]=1;
		vhit[pdcel.alpha.twin.origin.vertIndx]=1;
		int ftick=0;
		int etick=0;
		
		// Each edge in 'orderEdges' is associated with a new face,
		//   namely, it's own face, and in turn 'face.edge' is this edge.
		pdcel.faces=new ArrayList<Face>();
		Iterator<HalfEdge> oit=orderEdges.iterator();
		while (oit.hasNext()) {
			HalfEdge hfe=oit.next();
			Face tface=hfe.face;
			pdcel.faces.add(tface);
			if (tface.faceIndx==0)
				tface.faceIndx=++ftick;
			tface.edge=hfe;
			int oppv=hfe.next.next.origin.vertIndx;
			if (vhit[oppv]==0) { // yes, add to 'LayoutOrder'
				vhit[oppv]=1;
				pdcel.LayoutOrder.add(hfe.face);
			}
			
			// also index any new edges of this face
			HalfEdge fhe=tface.edge;
			do {
				if (fhe.edgeIndx==0)
					fhe.edgeIndx=++etick;
				fhe=fhe.next;
			} while (fhe!=tface.edge);
		}
		pdcel.intFaceCount=ftick;
		
		// index ideal faces and their edges
		rtrace=pdcel.redChain;
		do {
			if (rtrace.twinRed==null) {
				HalfEdge ihe=rtrace.myEdge.twin;
				Face iface=ihe.face;
				if (iface.faceIndx==0) {
					iface.faceIndx=-(++ftick); // not negative denoting ideal
				}
				do {
					if (ihe.edgeIndx==0) {
						ihe.edgeIndx=++etick;
					}
					ihe=ihe.next;
				} while (ihe!=rtrace.myEdge.twin);
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);

		pdcel.faceCount=ftick;
		pdcel.euler=pdcel.vertCount-etick/2+pdcel.faceCount;

		// adjust 'redChain' to be first "red" face hit following layout order
		if (pdcel.redChain!=null) {
			RedHEdge newRC=null;
			HalfEdge red=null;
			Iterator<Face> reit=pdcel.LayoutOrder.iterator();
			while (eit.hasNext() && red==null) {
				Face face=reit.next();
				red=face.isRed();
			}
			if (red!=null) {
				if ((newRC=isMyEdge(pdcel.redChain,red))!=null)
					pdcel.redChain=newRC;
			}
		}
		
		return pdcel;
	}

	/**
	 * Find index of w in flower of v. See 'PackData.nghb'
	 * 
	 * @param v    int
	 * @param w    int
	 * @param bouq int[][], array of flowers
	 * @return int, -1 on error
	 */
	public static int nghb(int v, int w, int[][] bouq) {
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

}