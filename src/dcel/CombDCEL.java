package dcel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import panels.PathManager;
import util.ColorUtil;
import util.StringUtil;

/**
 * Static combinatorial routines for working with DCEL structures.
 * TODO: Routines are gathered from earlier work, so all needs careful 
 * debugging. 
*/
public class CombDCEL {

	/**
	 * Starting with 'pdcel' structure, extract new DCEL avoiding the
	 * forbidden edges in 'hlink'. Start based on 'alphaEdge', but if
	 * this is null, use 'pdcel.alpha'.
	 * @param pdcel packDCEL
	 * @param hlink HalfLink
	 * @param alphaEdge HalfEdge
	 * @return PackDCEL
	 */
	public static PackDCEL extractDCEL(PackDCEL pdcel,HalfLink hlink,HalfEdge alphaEdge) {
		PackDCEL ndcel=null;
		try {
			ndcel=CombDCEL.redchain_by_edge(pdcel,hlink,alphaEdge);
			CombDCEL.d_FillInside(ndcel);
		} catch (Exception ex) {
			throw new DCELException(ex.getMessage());
		}
		return ndcel;
	}

	/**
	 * Given a bouquet of combinatoric data alone, create a
	 * DCEL structure with 'vertices' and 'edges' only.
	 * Bouquet satisfies usual conventions: counterclockwise order, 
	 * indexed contiguously from 1, bdry/interior flower 
	 * open/closed, resp.
	 * @param bouquet int[][]
	 * @return PackDCEL
	 */
	public static PackDCEL getRawDCEL(int[][] bouquet) {
		return getRawDCEL(bouquet,0);
	}

	/**
	 * Given a packing, create its bouquet and get minimal
	 * DCEL structure with 'vertices' and 'edges' only.
	 * @param p PackData
	 * @return PackDCEL
	 */
	public static PackDCEL getRawDCEL(PackData p) {
		return getRawDCEL(p.getBouquet(),p.alpha);
	}
	
	/**
	 * Given a bouquet of combinatoric data alone, create a minimal
	 * DCEL structure, including 'vertices' and 'edges' only.
	 * Bouquet satisfies usual conventions: counterclockwise order, 
	 * indexed contiguously from 1, bdry/interior flower 
	 * open/closed, resp. 'alpha' helpedge should start at interior  
	 * @param bouquet int[][]
	 * @param alphaIndx int, suggested 'alpha' vertex, may be 0
	 * @return packDCEL
	 */
	public static PackDCEL getRawDCEL(int[][] bouquet,int alphaIndx) {
		PackDCEL pdcel=new PackDCEL();
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
			if (bdryverts[v]==1)
				newV.bdryFlag=1;
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
					throw new CombException("Error: missing edge in 'CreateVertsEdges'");
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
		
		// Identify bdry edges by creating'face' with index -1. 
		// Note that other 'face's entries are not set, since we 
		//   do not catalog the faces, but we need to identify
		//   bdry edges in 'redchain_by_edge'.
		for (int v=1;v<=vertcount;v++) {
			Vertex vert=vertices[v];
			if (vert.bdryFlag==1) {
				HalfEdge he=heArrays[v][0];
				he.twin.face=new Face(-1);
				he.twin.next.face=new Face(-1);
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
		pdcel.edges=new HalfEdge[edges.size()+1];
		Iterator<HalfEdge> eit=edges.iterator();
		int tick=0;
		while (eit.hasNext()) {
			pdcel.edges[++tick]=eit.next();
		}
		pdcel.edgeCount=tick;

		return pdcel;
	}
	
	/**
	 * Form redchain in 'pdcel'based on given 'alphaEdge' and not
	 * crossing any edge in 'hlink'. Calling routine should call
	 * 'd_FillInside' to complete process of 'pdcel'.
	 * 
	 * Note: 1/17/2021: changing philosophy on how to do cookie
	 * first, then will handle other redchain situations now handled
	 * in 'd_redChainBuilder'.
	 * 
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink
	 * @param alphaEdge HalfEdge (if null, revert to 'pdcel.alpha'
	 * @return 
	 */
	public static PackDCEL redchain_by_edge(PackDCEL pdcel,HalfLink hlink,HalfEdge alphaEdge) {
		boolean debug=false; // debug=true;
		
		// revert 'RedVertex's; null 'redChain'
		for (int k=1;k<=pdcel.vertCount;k++) {
			if (pdcel.vertices[k] instanceof RedVertex) {
				RedVertex rdv=(RedVertex)(pdcel.vertices[k]);
				Vertex vtx=new Vertex();
				vtx.halfedge=rdv.halfedge;
				vtx.vertIndx=rdv.vertIndx;
				vtx.bdryFlag=rdv.bdryFlag;
				vtx.util=rdv.util;
				HalfEdge rtr=vtx.halfedge;
				do {
					rtr.origin=vtx;
					rtr=rtr.prev.twin;
				} while (rtr!=null && rtr!=vtx.halfedge);
				pdcel.vertices[k]=vtx;
			}
		}
		pdcel.redChain=null;
		
		// zero out edge/vert 'util's
		int vertcount=pdcel.vertCount;
		for (int v=1;v<=vertcount;v++) 
			pdcel.vertices[v].util=0;
		for (int e=1;e<=pdcel.edgeCount;e++) {
			HalfEdge edge=pdcel.edges[e];
			edge.util=0;
// System.out.println(" e = "+e);			
			// set 'util' -1 for bdry edges
			if (edge.face!=null && (edge.face.faceIndx<0 || edge.twin.face.faceIndx<0))
				edge.util=-1;
		}
		
		// set edge 'util' to identify forbidden edges (include bdry edges above)
		// set vert 'util' for vertices with at least one forbidden edge
		if (hlink!=null) {
			Iterator<HalfEdge> his=hlink.iterator();
			while (his.hasNext()) {
				HalfEdge he=his.next();
				he.util=-1;
				he.twin.util=-1;
				he.origin.util=-1;
				he.twin.origin.util=-1;
			}
		}

		// TODO: do we need this
//		if (alphaEdge.util!=0)
//			throw new DCELException("alpha cannot have any forbidden edges");
		
		// ============== start redchain using chosen edge ================
		
		if (alphaEdge==null) {
			alphaEdge=pdcel.alpha;
		}
		HalfEdge he=alphaEdge;
		pdcel.redChain=new RedHEdge(he);
		RedHEdge rtrace=pdcel.redChain;
		do {
			he=he.next;
			pdcel.redChain.nextRed=new RedHEdge(he);
			pdcel.redChain.nextRed.prevRed=pdcel.redChain;
			pdcel.redChain=pdcel.redChain.nextRed;
		} while(he.next!=alphaEdge);
		// close up
		pdcel.redChain.nextRed=rtrace;
		rtrace.prevRed=pdcel.redChain;
		pdcel.redChain=pdcel.redChain.nextRed;

		// identify vertices that are done: all spokes are forbidden
		//    or have been handled. Recall that v can be hit from two
		//    sides by two segments of redchain, so even if ultimately
		//    encircled, that may take several passes.
		boolean[] doneV=new boolean[vertcount+1];
		
		// ======================= main loop ==============
		
		// loop through red chain as long as we keep adding faces
		//    or collapsing the redchain. \\ debug=true;
		RedHEdge currRed=null;
		boolean hit = true; // true if we added a face or collapsed an edge
		boolean redisDone=false; // totally done?
		while (hit && pdcel.redChain!=null && !redisDone) {
			hit = false; // debug=true;

			// current count of red chain as safety stop mechanism
			int redN=0;
			RedHEdge re=pdcel.redChain;
			do {
				redN++;
				re=re.nextRed;
			} while(re!=pdcel.redChain);

			// look at 'redN'+1 successive 'RedHEdge's 
			for (int N=0;(N<=redN && !redisDone);N++) {
				currRed = pdcel.redChain;
				pdcel.redChain = currRed.nextRed; // set up pointer for next pass

				// check for degeneracy: triangulation of sphere
				if (pdcel.redChain.nextRed.nextRed==pdcel.redChain) {
					pdcel.redChain = null;
					return pdcel;
				}

				// ****************** main work *****************

				// processing the current red edge. 
				// working on v; u,v,w successive verts cclw along redchain 
				int v = currRed.myEdge.origin.vertIndx;
				
//				if (debug) { // debug=true;
//					DCELdebug.drawTmpRedChain(pdcel.p,currRed);
//					DCELdebug.printRedChain(pdcel.redChain);
//				}
				
				// process v if not done and if previous red edge not blocked
				if (!doneV[v] && !(doneV[v]=isVertDone(currRed.myEdge))) {
					// not done? query faces outside this segment of red chain
					HalfEdge upspoke = currRed.prevRed.myEdge.twin;
					HalfEdge downspoke = currRed.myEdge;
					
					// if 'upspoke' is forbidden, we go on
					if (upspoke.util<0) { 
						continue;
					}

					// doubling back on itself? 
					// (degeneracy to sphere is handled above)
					// If v is keeper: enclose it
					if (upspoke == downspoke) {
						upspoke.origin.halfedge=downspoke; // safety 

						currRed.prevRed.prevRed.nextRed = currRed.nextRed;
						currRed.nextRed.prevRed = currRed.prevRed.prevRed;
						doneV[v] = true;
						currRed.nextRed.myEdge.origin.halfedge = 
							currRed.nextRed.myEdge;
						hit = true;
						continue;
					}
					
					// Remaining cases break into cases:
					// If vert 'util' is zero, see if we can enclose it. Check 
					//    fan of edge outside current segment to see is any are
					//    red. If not, can close up.
					// Otherwise, following code tries to add one cclw face.
					boolean canclose=false;
					RedHEdge redge=null;
					HalfEdge spktrace=upspoke;
					if (pdcel.vertices[v].util==0) {
						redge=isMyEdge(pdcel.redChain,spktrace);
						while (spktrace!=downspoke && redge==null) {
							redge=isMyEdge(
									pdcel.redChain,(spktrace=spktrace.prev.twin));
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
							markFaceUtils(spktrace);			
							hit=true;
								
							if (debug) {
								EdgeSimple es=new EdgeSimple(spktrace.origin.vertIndx,
										spktrace.twin.origin.vertIndx);
								DCELdebug.drawEdgeFace(pdcel.p, es);
								DCELdebug.drawTmpRedChain(pdcel.p,pdcel.redChain);
							}
								
							spktrace=spktrace.prev.twin;
							cclw=cclw.nextRed;
						}
						cclw.nextRed=currRed.nextRed;
						currRed.nextRed.prevRed=cclw;

//						if (debug) {
//							System.err.println(v+" flat, done");
//							CommandStrParser.jexecute(pdcel.p,"disp -tfc218 "+v);
//						}

						doneV[v]=true;
					}
					
					// else, if 'upspoke' is not red, add one cclw face about v.
					else if (isMyEdge(pdcel.redChain,upspoke)==null &&
								isMyEdge(pdcel.redChain,upspoke.next)==null) {
						cclw.nextRed=new RedHEdge(upspoke.next);
						if (debug) {
							EdgeSimple es=new EdgeSimple(upspoke.origin.vertIndx,
								upspoke.twin.origin.vertIndx);
							DCELdebug.drawEdgeFace(pdcel.p, es);
						}
							
						cclw.nextRed.prevRed=cclw;
						cclw=cclw.nextRed;
						cclw.nextRed=new RedHEdge(upspoke.next.next);
						cclw.nextRed.prevRed=cclw;
						cclw=cclw.nextRed;
						cclw.nextRed=currRed;
						currRed.prevRed=cclw;
						markFaceUtils(upspoke);
						hit=true;
					}
				} // done with this v;
			} // done on this cycle through redchain
		} // end of main loop while, should be done creating red chain
		
		debug=false; // DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
		 // DCELdebug.printRedChain(pdcel.redChain);
	     // DCELdebug.EdgeOriginProblem(pdcel.edges);
		 // debug=true;
		 // DCELdebug.redChainEnds(pdcel.redChain);
		
		
		// ============ set 'myRedEdge' pointers ====================
		
		RedHEdge nxtre=pdcel.redChain;
		do {
			nxtre.myEdge.setRedEdge(nxtre);
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		// ============ identify red twins ==========================
		
		// If an edge is a red edge in both directions, then we decide
		//   whether these should be red twins. Rule: if both ends are 
		//   keepers  or one is a keeper and the other is either interior
		//   of not a keeper but neighbored a keeper, twin them. 'bdryNon[u]'
		//   true if nonKeeper only because 'u' is bdry.
		//   Also, mark all redChain vertices as done. 
		// The 'redChain' is not changed by this or subsequent operations,
		//   though it may gain new 'RedVertex's.
		// Set 'myRedEdge's

		nxtre=pdcel.redChain;
		do {
			if (nxtre.twinRed==null) {
				// is the opposite edge also red?
				HalfEdge ctwin=nxtre.myEdge.twin;
				RedHEdge crossRed=nxtre.myEdge.twin.getRedEdge();
				if (crossRed!=null && ctwin.util<0) {
					nxtre.twinRed=crossRed;
					crossRed.twinRed=nxtre;
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
				redV.num=pdcel.vertices[v].getNum();
				redV.bdryFlag=pdcel.vertices[v].bdryFlag;
				redV.halfedge.origin=redV;
				if (pdcel.vertices[v].bdryFlag==0)
					redV.closed=true;
				else redV.closed=false;
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
			int[] flower=pdcel.vertices[v].getFlower();
			for (int k=0;(k<=rV.num && j<0);k++) { 
				if (flower[k]==w) {
					j=k;
					rV.redSpoke[j]=rtrace;
				}
			}
			j=-1;
			flower=pdcel.vertices[w].getFlower();
			for (int k=0;(k<=rW.num && j<0);k++) {
				if (flower[k]==v) {
					j=k;
					rW.inSpoke[j]=rtrace;
				}
			}
			
//System.out.println("spokes for "+v+" and "+w);			
			
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
			
//System.out.println("handle vert "+v);

			// if not processed, then it's siblings are not created yet either
			if (pdcel.vertices[v] instanceof PreRedVertex) { 
				PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];

				// debug=true;
				if (debug) {
					DCELdebug.redVertFaces(rV); // DCELdebug.vertFaces(pdcel.vertices[14]);
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
		
		// ========== set bdry next/prev =============
		// redChain vertices that were interior, got no new edges;
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
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		
		if (debug) { // debug=true;
			DCELdebug.RedOriginProblem(pdcel.redChain);
			rtrace=pdcel.redChain;
			int safety=vertcount;
			do {
				DCELdebug.vertFaces(rtrace.myEdge.origin);
				rtrace=rtrace.nextRed;
				safety--;
			} while (rtrace!=pdcel.redChain && safety>0);
		}
		
		// ============= last job: clean up 'pdcel.vertices' =====
		// Want to reindex, with interiors first, then redchain. There
		// may be some original vertices that have been cut out.
		ArrayList<Vertex> newVertices=new ArrayList<Vertex>(0); 
		VertexMap newold=new VertexMap();
		int vcount=0;
		
		// get non-red connected to alpha first: use two lists 
		for (int v=1;v<=vertcount;v++) {
			pdcel.vertices[v].util=0;
		}
		ArrayList<Vertex> currv=new ArrayList<Vertex>(0);
		ArrayList<Vertex> nxtv=new ArrayList<Vertex>(0);

		// start with 'alpha'
		int vv=pdcel.alpha.origin.vertIndx;
		pdcel.alpha.origin.vertIndx=++vcount;
		pdcel.alpha.origin.util=1;
		nxtv.add(pdcel.alpha.origin);
		newVertices.add(pdcel.alpha.origin);
		newold.add(new EdgeSimple(vcount,vv));

		while (nxtv.size()>0) {
			currv=nxtv;
			nxtv=new ArrayList<Vertex>(0);
			Iterator<Vertex> vit=currv.iterator();
			while (vit.hasNext()) {
				Vertex vtx=vit.next();
				ArrayList<HalfEdge> flower=vtx.getEdgeFlower();
				Iterator<HalfEdge> fit=flower.iterator();
				while (fit.hasNext()) {
					Vertex wtx=fit.next().twin.origin;
					// other end of this spoke
					int w=wtx.vertIndx;
					if (!(wtx instanceof RedVertex) && wtx.util==0) {
						wtx.util=1;
						wtx.vertIndx=++vcount;
						nxtv.add(wtx);
						newVertices.add(wtx);
						newold.add(new EdgeSimple(vcount,w));
					}
				} // end of while through spokes
			} // end of while through 'currv'
		} // end of while 

		// pick up additional redChain vertices that are interior
		for (int v=1;v<=vertcount;v++) {
			Vertex vt=pdcel.vertices[v];
			if (vt.util==0 && (vt instanceof RedVertex) && vt.bdryFlag==0) {
				vt.vertIndx=++vcount;
				newVertices.add(vt);
				newold.add(new EdgeSimple(vcount,v));
				vt.util=1;
			}
		}
		
		// pick up remaining redChain vertices
		for (int v=1;v<=vertcount;v++) {
			Vertex vt=pdcel.vertices[v];
			if (vt.util==0 && (vt instanceof RedVertex)) {
				vt.vertIndx=++vcount;
				newVertices.add(vt);
				newold.add(new EdgeSimple(vcount,v));
				vt.util=1;
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
		
		blueCleanup(pdcel); // try to eliminate blue faces in the 'redChain'
		
		// DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
		return pdcel;
	}
	
	/**
	 * Given a DCEL, (perhaps only minimal, with vertices, edges, 
	 * not faces) and a list (possibly empty) of vertices that 
	 * are 'nonKeeper's, generate a new DCEL which includes the 
	 * nonKeepers as part of the boundary. If 'poisonFlag' is set, 
	 * then nghbs of nonKeepers are included as nonKeepers.
	 * 
	 * NOTE: 'face' data is not assumed to be set in 'pdcel',
	 * and faces are not created or processed here. However, we
	 * need face info in one spot to identify bdry edges (i.e.,
	 * those with 'face' having negative 'faceIndx'). So in 
	 * 'getRaw' we set faces only for this purpose, and if the
	 * input 'pdcel' is from other settings, it should have 
	 * faces set.
	 *  
	 * This routine only creates red chain. If not a combinatorial 
	 * sphere, we build a "red" chain as a closed cclw chain of 
	 * edges about a simple connected fundamental domain within 
	 * the complex. The result is NOT a fully prepared 'PackDCEL';
	 * the calling routine must invoke 'd_FillInside' to complete 
	 * processing.
	 * 
	 * Notes: 
	 *   * bdry vertices v are automatically included in nonKeepers,
	 *     but 'bdryNon[v]' indicates if that is the only reason
	 *     v is a non-keeper (i.e., versus a "real" non-keeper.
	 *   * 'nonKeepers' may or may not end up in the final DCEL;
	 *     any that remain are part of the boundary. Note: they 
	 *     may occur in the redChain more than once. E.g. you 
	 *     can introduce a cut along some formerly interior edges, 
	 *     putting them into the boundary and introducing an ideal
	 *     face.
	 *   * Additional vertices may be lost -- e.g., 'keepers' that
	 *     are separated from the 'alpaIndx' by nonKeepers and 
	 *     vertices that don't have an interior neighbor.
	 *   * Set 'poisonFlag' if you want to treat the nonKeepers as 
	 *     "poison"; this will include their neighboring vertices 
	 *     as nonKeepers.
	 *   * Unless the 'poisonFlag' is set, isolated interior vertices
	 *     in 'nonKeeper' will remain as keepers. (If you want to 
	 *     "puncture" a point, you need to include its neighbors 
	 *     in 'nonKeepers'. E.g., to puncture a single point, list
	 *     it in 'nonKeepers' and set 'poisonFlag'.)
	 * @param pdcel PackDCEL
	 * @param nonKeepers NodeLink,
	 * @param poisonFlag boolean, if true, then augment 
	 *    'nonKeepers' with their neighbors 
	 * @return PackDCEL (call to 'd_FillInide' is included)
	 */
	public static PackDCEL d_redChainBuilder(PackDCEL pdcel,
			NodeLink nonKeepers,boolean poisonFlag) {
		boolean debug=false; // debug=true;

		int vertcount=pdcel.vertCount;

		//============ early bookkeeping re 'alpha' and keepers ========== 
		// Classify verts in 'keepV[]' and 'bdryNon[]' in steps (1) - (7). 
		// 'bdryNon[v]' is true, meaning v is nonKeeper only because it is bdry,
		// is used only in setting red twins. 'keepV' is further modified.

		// (1) initialize: keepV to -1; revert 'RedVertex's; null 'redChain'
		int[] keepV = new int[vertcount + 1];
		for (int k=1;k<=vertcount;k++) {
			keepV[k]=-1;
			if (pdcel.vertices[k] instanceof RedVertex) {
				RedVertex rdv=(RedVertex)(pdcel.vertices[k]);
				Vertex vtx=new Vertex();
				vtx.halfedge=rdv.halfedge;
				vtx.vertIndx=rdv.vertIndx;
				vtx.bdryFlag=rdv.bdryFlag;
				vtx.util=rdv.util;
				HalfEdge rtr=vtx.halfedge;
				do {
					rtr.origin=vtx;
					rtr=rtr.prev.twin;
				} while (rtr!=null && rtr!=vtx.halfedge);
				pdcel.vertices[k]=vtx;
			}
		}
		pdcel.redChain=null;
		
		// (2) set keepV 0 for all specified 'nonKeepers'
		if (nonKeepers!=null && nonKeepers.size()>0) {
			Iterator<Integer> vit=nonKeepers.iterator();
			while (vit.hasNext()) {
				int v=vit.next();
				if (v>0 && v<=vertcount) {
					keepV[v]=0;
				}
			}
		}
		
		// (3) if not 'poisonFlag' then restore any interior nonKeeper
		//     not having some nonKeeper or bdry nghb. (Because the results
		//     are ambiguous if a nonKeeper is isolated.)
		if (!poisonFlag) {
			for (int k=1;k<=vertcount;k++) {
				if (keepV[k]==0) {
					Vertex vert=pdcel.vertices[k];
					int[] flower=vert.getFlower();
					if (vert.bdryFlag==0) { // k is interior
						int num=flower.length-1;
						boolean keepme=true;
						// is some nghb nonkeeper or bdry? then don't keepme
						for (int j=0;(j<num && keepme);j++) { 
							if (keepV[flower[j]]==0 || pdcel.vertices[flower[j]].bdryFlag==1)
								keepme=false;
						}
						if (keepme)
							keepV[k]=-1; // k reverts to being possible keeper
					}
				}
			}
		}
		
		// (4) 'poisonFlag' means make nghbs of nonKeepers also nonKeepers
		else { 
			boolean[] poison = new boolean[vertcount + 1];
			for (int k=1;k<=vertcount;k++) 
				if (keepV[k]==0) {
					Vertex vert=pdcel.vertices[k];
					int[] flower=vert.getFlower();
					int num=flower.length-1;
					for (int j=0;j<num;j++) 
						poison[flower[j]]=true;
				}
			for (int k=1;k<=vertcount;k++) 
				if (poison[k]) {
					keepV[k]=0;
				}
		}
			
		// (5) Make bdry verts into nonKeepers
		boolean[] bdryNon=new boolean[vertcount+1];
		for (int k=1;k<=vertcount;k++) { 
			if (pdcel.vertices[k].bdryFlag==1 && keepV[k]==-1) {
				bdryNon[k]=true;
				keepV[k]=0;
			}
		}
		
		// (6) Good 'alpha'? // want interior keeper (prefer int/keeper petals, too)
		int alph=pdcel.alpha.origin.vertIndx;
		pdcel.alpha=null;
		if (keepV[alph]!=0) { // see if alph is okay
			int[] flower=pdcel.vertices[alph].getFlower();
			boolean toss=false;
			for (int j=0;(j<flower.length && !toss);j++) {
				int w=flower[j];
				// not a keeper? (e.g., bdry) 
				if (keepV[w]==0) 
					toss=true;
			}
			if (!toss) // got our alpha
				pdcel.alpha=pdcel.vertices[alph].halfedge;
		}
		if (pdcel.alpha==null)  { // look further
			for (int v=1;(v<=vertcount && pdcel.alpha==null);v++) {
				int[] flower=pdcel.vertices[v].getFlower();
				boolean toss=false;
				if (keepV[v]==0)
					toss=true;
				if (!toss) {
					for (int j=0;(j<flower.length && !toss);j++) {
						int w=flower[j];
						// not a keeper? (e.g., bdry) 
						if (keepV[w]==0) 
							toss=true;
					}
				}
				if (!toss) // got our alpha
					pdcel.alpha=pdcel.vertices[v].halfedge;
			}

			// still nothing? choose first keeper interior
			if (pdcel.alpha==null) {
				for (int v=1;(v<=vertcount && pdcel.alpha==null);v++) 
					if (keepV[v]!=0)
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
		boolean[] alphaComp=new boolean[vertcount+1];
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
				Vertex vert=pdcel.vertices[cvit.next()];
				int[] flower=vert.getFlower();
				for (int j=0;j<flower.length;j++) {
					int w=flower[j];
					if (keepV[w]<0) {
						keepV[w]=1;
						alphaComp[w]=true;
						nextV.add(w);
					}
				}
			} // done going through 'currV'
		} // end of while, so 'nextV' is empty

		// ===================== 'doneV' to track vertices ===================
		boolean[] doneV = new boolean[vertcount + 1]; 

		// Part of bookkeeping is 'HalfEdge.util'. Initiate to 0;
		//    during processing, set for edges eliminated from being 
		//    added to the evolving redChain. Later, if found that 
		//    all edges from a vertex are excluded, set 'doneV' true.
		int ecount=pdcel.edges.length-1;
		for (int e=1;e<=ecount;e++) {  // set all to 0  
			HalfEdge edge=pdcel.edges[e];
			edge.util=0;
		}
		
		// First, all edges whose origin is not connected to firstV are done.
		for (int e=1;e<=ecount;e++) {
			HalfEdge edge=pdcel.edges[e];
			int ov=edge.twin.origin.vertIndx;
			if (keepV[ov]<0) {
				edge.util=1;
				edge.twin.util=1;
				doneV[ov]=true; 
			}
		}
		
		// =========== now we're going to build the 'redChain' ==========

		// Initial redChain is chain of outer edges cclw about 'firstV'.
		// Also begin the 'orderEdges' list from incoming spokes of 'firstV'
		ArrayList<HalfEdge> spokes = firstV.getEdgeFlower();
		HalfEdge he=spokes.get(0);
		he.origin.halfedge=he; // point firstV at this first spoke 
		
		// start of 'redChain'
		pdcel.redChain=new RedHEdge(he.next);
		he.twin.origin.halfedge=he.next;
		RedHEdge rtrace=pdcel.redChain;
		for (int k = 1; k < spokes.size(); k++) { // add rest about
			he = spokes.get(k);
			rtrace.nextRed=new RedHEdge(he.next);
			rtrace.nextRed.prevRed=rtrace;
			rtrace=rtrace.nextRed;
			markFaceUtils(he);			

			if (debug) { // debug=true;
				EdgeSimple es=new EdgeSimple(he.origin.vertIndx,
						he.twin.origin.vertIndx);
				DCELdebug.drawEdgeFace(pdcel.p, es);
				DCELdebug.drawTmpRedChain(pdcel.p,pdcel.redChain);
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
			hit = false; // debug=true;

			// current count of red chain as safety stop mechanism
			int redN=0;
			RedHEdge re=pdcel.redChain;
			do {
				redN++;
				re=re.nextRed;
			} while(re!=pdcel.redChain);
			
			// Degenerate? if redChain has just 2 verts and one/both keepers,
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
				
//				if (debug) { // debug=true;
//					DCELdebug.drawTmpRedChain(pdcel.p,currRed);
//					DCELdebug.printRedChain(pdcel.redChain);
//				}
				
				// process v if not done and if previous red edge not blocked
				if (!doneV[v] && !(doneV[v]=isVertDone(currRed.myEdge)) && 
						currRed.prevRed.redutil==0) {
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
							
//							if (debug) {
//								System.err.println(v+" collapse done");
//								CommandStrParser.jexecute(pdcel.p,"disp -tfc218 "+v);
//							}

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
								markFaceUtils(spktrace);			
								hit=true;
								
								if (debug) {
									EdgeSimple es=new EdgeSimple(spktrace.origin.vertIndx,
											spktrace.twin.origin.vertIndx);
									DCELdebug.drawEdgeFace(pdcel.p, es);
									DCELdebug.drawTmpRedChain(pdcel.p,pdcel.redChain);
								}
								
								spktrace=spktrace.prev.twin;
								cclw=cclw.nextRed;
							}
							cclw.nextRed=currRed.nextRed;
							currRed.nextRed.prevRed=cclw;

//							if (debug) {
//								System.err.println(v+" flat, done");
//								CommandStrParser.jexecute(pdcel.p,"disp -tfc218 "+v);
//							}

							doneV[v]=true;
						}
						// else consider adding one cclw face about v.
						else if (isMyEdge(pdcel.redChain,upspoke)==null &&
								isMyEdge(pdcel.redChain,upspoke.next)==null) {
							// what is situation? 
							//  * ends both real non-keepers? 
							//  * both ends non-keepers?
							// get status of v and u: real non-keeper?
							int u=upspoke.next.origin.vertIndx;
							int y=upspoke.next.next.origin.vertIndx;
							
							// Don't add face if both ends real non-keepers; set
							//    'redutil' to avoid this red edge in later passes
							if ((keepV[v]==0 && !bdryNon[v]) && (keepV[u]==0 && !bdryNon[u])) 
								currRed.prevRed.redutil=1;
							
							// else add a face across this edge. 
							else {
								cclw.nextRed=new RedHEdge(upspoke.next);
								markFaceUtils(upspoke);
								if (debug) {
									EdgeSimple es=new EdgeSimple(upspoke.origin.vertIndx,
											upspoke.twin.origin.vertIndx);
									DCELdebug.drawEdgeFace(pdcel.p, es);
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
					}
				} 
				else { // check for backtracking: w -> v -> w 
					int cv=currRed.nextRed.myEdge.origin.vertIndx;
					int cw=currRed.prevRed.myEdge.origin.vertIndx;
					if (cv==cw && keepV[v]!=0) {
						currRed.prevRed.prevRed.nextRed=currRed.nextRed;
						currRed.nextRed.prevRed=currRed.prevRed.prevRed;
						currRed=currRed.nextRed;
					}
				} // done with v;
			} // done on this cycling through redchain
		} // end of main loop while, should be done creating red chain

		debug=false; // DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
		 // DCELdebug.printRedChain(pdcel.redChain);
	     // DCELdebug.EdgeOriginProblem(pdcel.edges);
		 // debug=true;
		 // DCELdebug.redChainEnds(pdcel.redChain);

		// A sphere?
		if (pdcel.redChain==null) {
			d_FillInside(pdcel);
			return pdcel;
		}
		
		// ============ set 'myRedEdge' pointers ====================
		
		RedHEdge nxtre=pdcel.redChain;
		do {
			nxtre.myEdge.setRedEdge(nxtre);
			nxtre=nxtre.nextRed;
		} while (nxtre!=pdcel.redChain);
		
		// ============ identify red twins ==========================
		
		// If an edge is a red edge in both directions, then we decide
		//   whether these should be red twins. Rule: if both ends are 
		//   keepers  or one is a keeper and the other is either interior
		//   of not a keeper but neighbored a keeper, twin them. 'bdryNon[u]'
		//   true if nonKeeper only because 'u' is bdry.
		//   Also, mark all redChain vertices as done. 
		// The 'redChain' is not changed by this or subsequent operations,
		//   though it may gain new 'RedVertex's.
		// Set 'myRedEdge's

		nxtre=pdcel.redChain;
		do {
			if (nxtre.twinRed==null) {
				// is the opposite edge also red?
				RedHEdge crossRed=nxtre.myEdge.twin.getRedEdge();
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
				redV.num=pdcel.vertices[v].getNum();
				redV.bdryFlag=pdcel.vertices[v].bdryFlag;
				redV.halfedge.origin=redV;
				if (pdcel.vertices[v].bdryFlag==0)
					redV.closed=true;
				else redV.closed=false;
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
			int[] flower=pdcel.vertices[v].getFlower();
			for (int k=0;(k<=rV.num && j<0);k++) { 
				if (flower[k]==w) {
					j=k;
					rV.redSpoke[j]=rtrace;
				}
			}
			j=-1;
			flower=pdcel.vertices[w].getFlower();
			for (int k=0;(k<=rW.num && j<0);k++) {
				if (flower[k]==v) {
					j=k;
					rW.inSpoke[j]=rtrace;
				}
			}
			
//System.out.println("spokes for "+v+" and "+w);			
			
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
			
//System.out.println("handle vert "+v);

			// if not processed, then it's siblings are not created yet either
			if (pdcel.vertices[v] instanceof PreRedVertex) { 
				PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];

				// debug=true;
				if (debug) {
					DCELdebug.redVertFaces(rV); // DCELdebug.vertFaces(pdcel.vertices[14]);
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
		
		// ========== set bdry next/prev =============
		// redChain vertices that were interior, got no new edges;
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
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		
		if (debug) { // debug=true;
			DCELdebug.RedOriginProblem(pdcel.redChain);
			rtrace=pdcel.redChain;
			int safety=vertcount;
			do {
				DCELdebug.vertFaces(rtrace.myEdge.origin);
				rtrace=rtrace.nextRed;
				safety--;
			} while (rtrace!=pdcel.redChain && safety>0);
		}
		
		// ============= last job: clean up 'pdcel.vertices' =====
		// Want to reindex, with interiors first, then redchain. There
		// may be some original vertices that have been cut out.
		ArrayList<Vertex> newVertices=new ArrayList<Vertex>(0); 
		VertexMap newold=new VertexMap();
		int vcount=0;
		
		// get non-red connected to alpha first: use two lists 
		for (int v=1;v<=vertcount;v++) {
			pdcel.vertices[v].util=0;
		}
		ArrayList<Vertex> currv=new ArrayList<Vertex>(0);
		ArrayList<Vertex> nxtv=new ArrayList<Vertex>(0);

		// start with 'alpha'
		int vv=pdcel.alpha.origin.vertIndx;
		pdcel.alpha.origin.vertIndx=++vcount;
		pdcel.alpha.origin.util=1;
		nxtv.add(pdcel.alpha.origin);
		newVertices.add(pdcel.alpha.origin);
		newold.add(new EdgeSimple(vcount,vv));

		while (nxtv.size()>0) {
			currv=nxtv;
			nxtv=new ArrayList<Vertex>(0);
			Iterator<Vertex> vit=currv.iterator();
			while (vit.hasNext()) {
				Vertex vtx=vit.next();
				ArrayList<HalfEdge> flower=vtx.getEdgeFlower();
				Iterator<HalfEdge> fit=flower.iterator();
				while (fit.hasNext()) {
					Vertex wtx=fit.next().twin.origin;
					// other end of this spoke
					int w=wtx.vertIndx;
					if (!(wtx instanceof RedVertex) && wtx.util==0) {
						wtx.util=1;
						wtx.vertIndx=++vcount;
						nxtv.add(wtx);
						newVertices.add(wtx);
						newold.add(new EdgeSimple(vcount,w));
					}
				} // end of while through spokes
			} // end of while through 'currv'
		} // end of while 

		// pick up additional redChain vertices that are interior
		for (int v=1;v<=vertcount;v++) {
			Vertex vt=pdcel.vertices[v];
			if (vt.util==0 && (vt instanceof RedVertex) && vt.bdryFlag==0) {
				vt.vertIndx=++vcount;
				newVertices.add(vt);
				newold.add(new EdgeSimple(vcount,v));
				vt.util=1;
			}
		}
		
		// pick up remaining redChain vertices
		for (int v=1;v<=vertcount;v++) {
			Vertex vt=pdcel.vertices[v];
			if (vt.util==0 && (vt instanceof RedVertex)) {
				vt.vertIndx=++vcount;
				newVertices.add(vt);
				newold.add(new EdgeSimple(vcount,v));
				vt.util=1;
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
		
		blueCleanup(pdcel); // try to eliminate blue faces in the 'redChain'
		
		// DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
		return pdcel;
	}
	
	/** 
	 * See if the red chain can be shifted to eliminate some blue
	 * faces. Cycle through because some may need to be eliminated
	 * before others.
	 * TODO: finish this
	 * @param pdcel
	 */
	public static void blueCleanup(PackDCEL pdcel) {
		boolean gotone=true;
		while (gotone) {
			gotone=false;
			RedHEdge rtrace=pdcel.redChain;
			do {
				// do next two red edges form blue face? 
				if (rtrace.myEdge.prev==rtrace.nextRed.myEdge.next && 
						(rtrace.twinRed!=null || rtrace.nextRed.twinRed!=null)) {
					
					// evaluation stage: possibilites are 
					//    1. move first edge cclw
					//    2. move second edge clw
					// check feasibility and quality
					RedHEdge upedge=rtrace.prevRed;

					// TODO: put off for now: 10/3/2020

				}
			} while (rtrace!=pdcel.redChain);
		}
	}

	/**
	 * Given a DCEL structure with an already processed red chain 
	 * and established 'alpha' edge, process the interior to create
	 * the faces, layout order, etc. This can be used when we modify 
	 * the structure inside but can keep the red chain, e.g., when
	 * flipping an edge. The 'HalfEdge's already exist, but we find them
	 * using 'pdcel.vertices' and create 'pdcel.edges' at the end.
	 * @param pdcel
	 * @return PackDCEL
	 */
	public static void d_FillInside(PackDCEL pdcel) {
		boolean debug=false;
		// NOTE about debugging: Many debug routines depend on
		//      original vertex indices, which can be found in
		//      pdcel.newold.
		
		pdcel.triData=null;  // filled when needed for repacking
		
		// use 'HalfEdge.util' to keep track of edges hit; this
		//   should be used in initializing interior edges.
		for (int v=1;v<=pdcel.vertCount;v++) {
			pdcel.vertices[v].util=0;
			HalfEdge edge=pdcel.vertices[v].halfedge; 
			// DCELdebug.edgeFlowerUtils(pdcel,pdcel.vertices[17]);
			HalfEdge trace=edge;
			do {
				trace.util=0;
				trace.twin.util=0;
				trace=trace.prev.twin;
			} while(trace!=edge);
		}
		
		// zero out 'RedHEdge.redutil'
		RedHEdge rtrace=pdcel.redChain;
		if (rtrace!=null) // not spherical case
			do {
				rtrace.redutil=0;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);
		
		// Build list of edges as we encounter new faces: convention is that
		//   each 'HalfEdge' in 'orderEdges' is associated with its face (ie. that
		//   on its left). 
		ArrayList<HalfEdge> orderEdges=new ArrayList<HalfEdge>(); 	
		int ordertick=0;
		
		// get non-red vertices connected to alpha first: use two lists 
		int[] vhits=new int[pdcel.vertCount+1];
		ArrayList<Vertex> currv=new ArrayList<Vertex>(0);
		ArrayList<Vertex> nxtv=new ArrayList<Vertex>(0);
		nxtv.add(pdcel.alpha.origin);

		// put alpha in first and mark its edges
		orderEdges.add(pdcel.alpha);
		ordertick++;
		
		if (debug) // debug=true;
			DCELdebug.drawEdgeFace(pdcel,pdcel.alpha);
			
		HalfEdge tr=pdcel.alpha;
		do { 
			tr.util=1;
			tr=tr.next;
		} while(tr!=pdcel.alpha);

		while (nxtv.size()>0) {
			currv=nxtv;
			nxtv=new ArrayList<Vertex>(0);
			Iterator<Vertex> cit=currv.iterator();
			while (cit.hasNext()) {
				Vertex vert=cit.next();

				if (vhits[vert.vertIndx]!=0) {
					continue;
				}
				
				// DCELdebug.edgeFlowerUtils(pdcel,pdcel.vertices[17]);

				// rotate cclw to find util==1; should always exist
				HalfEdge startedge=vert.halfedge;
				HalfEdge he=startedge;
				while(he.util==0) {
					he=he.prev.twin; // break;
				}
				startedge=he.prev.twin;
				he=startedge;
				
				// move cclw through to layout new faces
				do {
					if (he.util==0 && he.twin.util==1) {
						orderEdges.add(he);
						ordertick++;
						Vertex oppV=he.twin.origin;
						if (!(oppV instanceof RedVertex) && vhits[oppV.vertIndx]==0)
							nxtv.add(oppV);
						
						if (debug) { // debug=true;
							int oldv=pdcel.newOld.findW(he.origin.vertIndx);
							int oldw=pdcel.newOld.findW(he.twin.origin.vertIndx);
							System.out.println("  edge = ("+oldv+","+oldw+")");
							DCELdebug.drawEdgeFace(pdcel,he);
						}
						
						// mark face edges
						tr=he;
						do { 
							tr.util=1;
							if (debug) {
								System.out.println(" set util of "+tr);
							}
							tr=tr.next;
						} while(tr!=he);
					}
					he=he.prev.twin;
				} while(he!=startedge);
				vhits[vert.vertIndx]=1;
			} // end of while on 'currv'
		} // end of while on 'nxtv'
		
		// If no a sphere, go around redChain for stragglers
		if (pdcel.redChain!=null) {
			RedHEdge startred=pdcel.redChain;
			
			// don't want to start with unplotted "blue" face
			while (startred.myEdge.util==0 && (startred.myEdge.next==startred.nextRed.myEdge
					|| startred.myEdge.prev==startred.prevRed.myEdge))
				startred=startred.nextRed;
			
			rtrace=startred;
			do {
				
				// if associated face already plotted, continue
				if (rtrace.myEdge.util==1) {
					rtrace=rtrace.nextRed;
					continue;
				}

				// otherwise, search cclw for edge with util==0, twin.util==1
				HalfEdge stopedge=rtrace.prevRed.myEdge.next;
				HalfEdge he=rtrace.myEdge;
				while (he!=stopedge && he.util==0)
					he=he.prev.twin;
				if (he.util==0)
					throw new DCELException("seems that no edge from red vertex "+he.origin.vertIndx+
							" has been laid out");
				HalfEdge bhe=he.twin;
				
				// then rotate clw adding faces
				do {
					orderEdges.add(bhe);
					ordertick++;
					
					if (debug) // debug=true;
						DCELdebug.drawEdgeFace(pdcel,bhe);
						
					HalfEdge bt=bhe;
					do {
						bt.util=1;
						bt=bt.next;
					} while (bt!=bhe); 
					bhe=bhe.next.twin; // next clw spoke
				} while (bhe.twin!=rtrace.myEdge);
				rtrace=rtrace.nextRed;
			} while (rtrace!=startred);	
		}
		
		if (debug) 
			System.out.println("ordertick = "+ordertick);
			
		debug=false; // debug=true;
		
		// ============== Faces and Edges: ==================================

		// We account for these anew based entirely on 'orderEdges'; we index
		//   edges face by face, including the ideal faces later, and we create 
		//   new faces (most will be garbage'd) 
				
		// Use 'orderEdges' (which should be unchanged with redChain work): 
		//    * index interior faces, 
		//    * catalog and re-index all edges, face=by=face
		//    * set 'LayoutOrder', adding faces only if opposite vert not hit
		//    * find first face that places a 'RedVertex'; this will be 'redChain'
		ArrayList<HalfEdge> tmpEdges=new ArrayList<HalfEdge>();
		tmpEdges.add(null); // null in first spot
		ArrayList<Face> tmpFaceList=new ArrayList<Face>();
		tmpFaceList.add(null); // null in first spot
		RedHEdge newRedChain=null;
		int ftick=0;
		int etick=0;

		// Two passes through 'orderEdges'
		
		// (1) First pass is to catalog faces and edges. There's
		//     a one-to-one correspondence between 'orderEdges'
		//     and faces. Create face and set its edge  
		// DCELdebug.drawOrderEdgeFace(pdcel.p,orderEdges);
		Iterator<HalfEdge> oit=orderEdges.iterator();
		while (oit.hasNext()) {
			HalfEdge hfe=oit.next();
			hfe.edgeIndx=++etick;
			tmpEdges.add(hfe);
			Face newface=new Face();
			hfe.face=newface;
			newface.edge=hfe;
			newface.faceIndx=++ftick;
			tmpFaceList.add(newface);
			
// System.out.println("edge(face): "+hfe+"("+ftick+")");			
			
			// get the other edges of this face (typically 2 others)
			HalfEdge nxe=hfe.next;
			int safety=200;
			do {
				nxe.edgeIndx=++etick;
				tmpEdges.add(nxe);
				nxe.face=newface;
				nxe=nxe.next;
				safety--;
			} while (nxe!=hfe && safety>0);
			if (safety==0) {
				throw new DCELException("runaway face edge tracing");
			}
								
			if (debug) { // debug=true;
				int hfev=hfe.origin.vertIndx;
				int hfew=hfe.twin.origin.vertIndx;
				EdgeSimple es=new EdgeSimple(hfev,hfew);
				if (pdcel.newOld!=null) {
					es.v=pdcel.newOld.findW(es.v);
					es.w=pdcel.newOld.findW(es.w);
				}
				DCELdebug.drawEdgeFace(pdcel.p,es);
				System.out.println("["+hfe.origin.vertIndx+","+hfe.twin.origin.vertIndx+
						"]");
			}
		}
		debug=false;
		
		// (2) Second pass is to determine layout order by ensuring
		//     all vertices get laid out, including 'RedVertex's, 
		//     which may get multiple locations, and to set 'redChain'
		//     to a proper first 'RedHEdge'.
		boolean[] fhits=new boolean[ftick+1];
		pdcel.alpha.origin.util=1;
		pdcel.alpha.twin.origin.util=1;
		int count_vhits=2;
		
		oit=orderEdges.iterator();
		ArrayList<Face> tmpLayout=new ArrayList<Face>();
		ArrayList<Face> tmpfullLayout=new ArrayList<Face>();
		while (oit.hasNext()) {
			HalfEdge hfe=oit.next();
			if (!fhits[hfe.face.faceIndx]) {
				tmpfullLayout.add(hfe.face);
 				fhits[hfe.face.faceIndx]=true;
				
				if (debug) { // debug=true;
					System.out.println("face      "+hfe.face.faceIndx+
							" = <"+hfe.face.toString()+">");
					DCELdebug.drawEdgeFace(pdcel,hfe);
				}

			}
		
			// check second edge around to identify opposite vertex 
			HalfEdge sea=hfe.next.next;
			Vertex oppVert=sea.origin;
			
			// if normal 'Vertex' 
			if (!(oppVert instanceof RedVertex)) {
				if (oppVert.util==0) { // yes, add 'hfe' to 'tmpLayout'
					oppVert.util=1;
					count_vhits++;
					tmpLayout.add(hfe.face);
					
					if (debug) { // debug=true;
						System.out.println("  normal "+hfe.face.faceIndx+" = <"+
								hfe.face.toString()+">, oppVErt "+oppVert.vertIndx+
								", add to 'tmpLayout'");				
						DCELdebug.drawEdgeFace(pdcel,hfe);
					}
				}
			}
			
			// for 'RedVertex', have to process: 
			//     * decide if this instance of this vertex (there may be 
			//       many) has already been hit
			//     * search for appropriate 'redChain' start. 
			else {
				RedHEdge redge=nextRedEdge(sea);
				if (redge.redutil==0) {
					redge.redutil=1;
					count_vhits++;
					tmpLayout.add(hfe.face);
					
					if (debug) { // debug=true;
						System.out.println("  red case "+hfe.face.faceIndx+" = <"+
								hfe.face.toString()+">, oppVErt "+oppVert.vertIndx+
								", add to 'tmpLayout'; red edge = "+redge.myEdge);				
						DCELdebug.drawEdgeFace(pdcel,hfe);
					}

				}
			
				// search for appropriate 'redChain' start: find first 'RedVertex'
				//   encountered and then add clw faces to the first 'RedHEdge'.
				// TODO: this may be first edge of "blue" face. May want to avoid
				//   this. 
				if (newRedChain==null) { 
					
					// pivot clw adding faces until hitting redge
					// Note: none of this faces could have been hit before
					while (sea!=redge.myEdge) {
						tmpfullLayout.add(sea.twin.face);
						fhits[sea.twin.face.faceIndx]=true;
						oppVert=sea.twin.next.next.origin;

						if (debug) { // debug=true;
							System.out.println(" normal pivot "+sea.twin.face.faceIndx+
									" = <"+sea.twin.face.toString()+">");
							DCELdebug.drawEdgeFace(pdcel,sea.twin);
						}
						
						boolean alreadyhit=false;
						if ((oppVert instanceof RedVertex)) {
							RedHEdge nre=nextRedEdge(sea.twin.next.next);
							if (nre.redutil==1) // already plotted?
								alreadyhit=true;
							nre.redutil=1; // in any case, will be plotted
						}
						else if (oppVert.util==1) {
							alreadyhit=true;
						}

						// put in 'tmpLayout'?
						if (!alreadyhit) {  
							oppVert.util=1;
							tmpLayout.add(sea.twin.face);
							count_vhits++;

							if (debug) { // debug=true;
								System.out.println("  clw face "+sea.twin.face.faceIndx+" = <"+
										sea.twin.face.toString()+">, oppVErt "+oppVert.vertIndx+
										", add to 'tmpLayout'; red edge = "+redge.myEdge);				
								DCELdebug.drawEdgeFace(pdcel,hfe);
							}
						}							
						sea=sea.twin.next;
					}
					newRedChain=redge;
				}
				else {
					if (redge.redutil==0) { // yes, add 'hfe' to 'tmpLayout'
						redge.redutil=1;
						count_vhits++;
						tmpLayout.add(hfe.face);

						if (debug) { // debug=true;
							System.out.println("  later red "+hfe.face.faceIndx+" = <"+
									hfe.face.toString()+">, oppVErt "+oppVert.vertIndx+
									", add to 'tmpLayout'; red edge = "+redge.myEdge);				
							DCELdebug.drawEdgeFace(pdcel,hfe);
						}
					}
				}
			} // done when red
		} // end of while through 'orderEdges'
		
//System.out.println("'count_vhits = "+count_vhits);

		pdcel.intFaceCount=ftick;

		debug=false;
		// DCELdebug.printRedChain(pdcel.redChain,pdcel.newOld); 
		
		pdcel.sideStarts=null;
		pdcel.bdryStarts=null;
		
		// not a sphere?
		if (pdcel.redChain!=null) {
			// ======== Catalog side pairings, free sides, create ideal faces ========
			pdcel.sideStarts=new ArrayList<RedHEdge>();
			pdcel.bdryStarts=new ArrayList<RedHEdge>();
			int sidecount=0; 
			RedHEdge nxtre=pdcel.redChain;
			int safety=1000;
			RedHEdge stopEdge=null;
			do {
				// see if this is unpasted redChain edge, hence a free side.
				if (nxtre.twinRed==null) {
					++sidecount; // index is non-zero
					
					// search upstream to find first edge
					rtrace=nxtre.prevRed;
					while (rtrace.twinRed==null && rtrace!=nxtre) {
						rtrace.myEdge.twin.face=null;
						rtrace=rtrace.prevRed;
					}

					
					// check if simply connected
					if (rtrace==nxtre) {
						pdcel.bdryStarts.add(pdcel.redChain);
						pdcel.sideStarts.add(pdcel.redChain);

						// go around cataloging twin edges
						rtrace=pdcel.redChain;
						do {
							rtrace.mobIndx=sidecount;
							rtrace.myEdge.twin.edgeIndx=++etick;
							rtrace.myEdge.twin.face=null;
							tmpEdges.add(rtrace.myEdge.twin);
							rtrace=rtrace.nextRed;
						} while (rtrace!=pdcel.redChain);
						break;
					}
						
					// else record in 'bdryStarts' and 'sideStarts'
					RedHEdge freeStart=rtrace.nextRed;
					if (stopEdge==null)
						stopEdge=freeStart;
					pdcel.idealFaceCount++;
					pdcel.bdryStarts.add(freeStart);
					pdcel.sideStarts.add(freeStart); 
					
					// propagate downstream
					rtrace=freeStart;
					do {
						rtrace.mobIndx=sidecount;
						rtrace.myEdge.twin.edgeIndx=++etick;
						tmpEdges.add(rtrace.myEdge.twin);
						rtrace.myEdge.twin.face=null;
						rtrace=rtrace.nextRed;
					} while (rtrace.twinRed==null);
					
					if (debug) { // debug=true;
						DCELdebug.faceVerts(freeStart.myEdge);
					}
					
					// set to look for next side
					nxtre=rtrace;
				}
				// else, get side pairing; move back, then forward, while twins match
				else {				
					int sideIndx=nxtre.twinRed.mobIndx; // should be positive
					rtrace=nxtre;

					// if not 0, then this is paired with earlier side
					if (sideIndx!=0) {
						pdcel.sideStarts.add(rtrace);
						do {
							rtrace.mobIndx=-sideIndx;
							rtrace=rtrace.nextRed;
						} while (rtrace.twinRed!=null && rtrace.twinRed.mobIndx==sideIndx);
						nxtre=rtrace;
					}
					
					// else this is a new paired edge
					else { 
						sideIndx=++sidecount;;
						rtrace.mobIndx=sideIndx;
					
						// first look upstream to find start
						RedHEdge twintrace=rtrace.twinRed;
						while (rtrace.prevRed.twinRed==twintrace.nextRed && 
							twintrace.nextRed.twinRed==rtrace.prevRed) {
							rtrace=rtrace.prevRed;
							twintrace=rtrace.twinRed;
							rtrace.mobIndx=sideIndx;
						}
						pdcel.sideStarts.add(rtrace);
						if (stopEdge==null)
							stopEdge=rtrace;
					
						// look downstream while still pasted
						rtrace=nxtre;
						twintrace=rtrace.twinRed;
						while (rtrace.nextRed.twinRed==twintrace.prevRed && 
							twintrace.prevRed.twinRed==rtrace.nextRed) { 
							rtrace=rtrace.nextRed;
							twintrace=rtrace.twinRed;
							rtrace.mobIndx=sideIndx;
						}

						nxtre=rtrace.nextRed;
					}
				}
				safety--;
			} while (nxtre!=stopEdge && safety>0);
			
			// DCELdebug.printRedChain(pdcel.redChain,null);
			
			if (safety==0) {
				System.err.println("Failed in indexing free side.");
			}
		}
		
		if (debug) {   // debug=true;
			Iterator<HalfEdge> oeit=orderEdges.iterator();
			while (oeit.hasNext()) {
				HalfEdge hee=oeit.next();
				EdgeSimple es=new EdgeSimple(hee.origin.vertIndx,hee.twin.origin.vertIndx);
				if (pdcel.newOld!=null) {
					es.v=pdcel.newOld.findW(es.v);
					es.w=pdcel.newOld.findW(es.w);
				}
				DCELdebug.drawEdgeFace(pdcel.p,es);
			}
		}

		// ===================================================================
		//                  create, fill the lists 
		// ===================================================================

		// (1) Edges: --------------------------------------------------------
		pdcel.edges=new HalfEdge[etick+1];
		for (int j=1;j<=etick;j++) {
			pdcel.edges[j]=tmpEdges.get(j);
			pdcel.edges[j].edgeIndx=j; // may be set already
		}
		tmpEdges=null;
		
		// (2) Faces: --------------------------------------------------------
		pdcel.faces=new Face[ftick+1];
		for (int f=1;f<=ftick;f++) {
			pdcel.faces[f]=tmpFaceList.get(f); // pdcel.tmpFaceList.size();
		}
		tmpFaceList=null;
		
		// (3) Ideal faces: --------------------------------------------------
		if (pdcel.bdryStarts!=null && pdcel.bdryStarts.size()>0) {
			ArrayList<Face> tmpIdealFaces=new ArrayList<Face>(0);
			Iterator<RedHEdge> rit=pdcel.bdryStarts.iterator();
			int idtick=0;
			while (rit.hasNext()) {
				RedHEdge redge=rit.next();
				HalfEdge he=redge.myEdge.twin;
				if (he.face!=null) { // no in bdry
					continue;
				}
				
				// is this side already handled?
				Face newface=new Face();
				newface.faceIndx=-(++idtick);
				newface.edge=redge.myEdge.twin;
				do {
					he.face=newface;
					he=he.next;
				} while (he!=redge.myEdge.twin);
				tmpIdealFaces.add(newface);
			}
			pdcel.idealFaceCount=idtick;
			pdcel.idealFaces=new Face[idtick+1];
			for (int j=0;j<idtick;j++) {
				pdcel.idealFaces[j+1]=tmpIdealFaces.get(j);
			}
		}
		
		if (debug) { // debug=true;
			DCELdebug.drawEdgeFace(pdcel,tmpLayout);
			DCELdebug.drawEdgeFace(pdcel,tmpfullLayout);
			debug=false;
		}
		
		// (4) Create 'faceOrder' and 'computeOrder': ------------------------
		// set drawing order for computations of cent/rad
		pdcel.computeOrder=new GraphLink();
		Iterator<Face> lit=tmpLayout.iterator();
		int faceIndx=lit.next().faceIndx;
		pdcel.computeOrder.add(new EdgeSimple(0,faceIndx)); // root 
		while (lit.hasNext()) {
			Face face=lit.next();
			int nxtIndx=face.faceIndx;
			EdgeSimple es=new EdgeSimple(face.edge.twin.face.faceIndx,nxtIndx);
			pdcel.computeOrder.add(es);
			faceIndx=nxtIndx;
		}
		
		// set drawing order for all faces
		pdcel.faceOrder=new GraphLink();
		lit=tmpfullLayout.iterator();
		faceIndx=lit.next().faceIndx;
		pdcel.faceOrder.add(new EdgeSimple(0,faceIndx));
		while (lit.hasNext()) {
			Face face=lit.next();
			int nxtIndx=face.faceIndx;
			pdcel.faceOrder.add(new EdgeSimple(face.edge.twin.face.faceIndx,nxtIndx));
			faceIndx=nxtIndx;
		}
		tmpfullLayout=null;
		tmpLayout=null;

		// Arrange the side pairings in order of 'sideStarts'
		if (pdcel.sideStarts!=null) {
			Iterator<RedHEdge> rit=pdcel.sideStarts.iterator();
			int sptick=0;
			pdcel.pairLink=new D_PairLink();
			pdcel.pairLink.add(null); // first is null
			EdgeLink oldnew=new EdgeLink();
			while (rit.hasNext()) {
				RedHEdge rstart=rit.next();
				
				D_SideData sideData=new D_SideData();
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
					sdata.label=String.valueOf(freeCount);
					freeCount++;
				}
			}
		}

		// ==================================================================
		//                  should be done!!
		// ==================================================================
		pdcel.faceCount=ftick;
		pdcel.edgeCount=etick;
		if (newRedChain!=null)
			pdcel.redChain=newRedChain;
	}
	
	/** 
	 * Convert a chain of 'HalfEdge's to form a new "red chain", and
	 * process its side pairings. Typically this will now pass to
	 * 'd_FillInside'. If the given chain is improper, return null.
	 * @param edges
	 * @return RedHEdge, null on error
	 */
	// TODO: under development, 10/2020
//	public static RedHEdge chain2Red(ArrayList<HalfEdge> edges) {
//		RedHEdge newChain=-new RedHEdge();
//		Iterator<HalfEdge> eit=edges.iterator();
//		while (eit.hasNext()) {
			
//		}
//	}
	
	/**
	 * Generate a minimal DCEL (vertices, edges only) by subdividing 
	 * each edge into 2 edges, and each n-sided (non-ideal) face of 
	 * 'pdcel' into n+1 faces. 'pdcel' data is modified, but we preserve 
	 * much of the original structure: orig vert indices unchanged; 
	 * orig vert halfedge's unchanged; 'pdcel.redChain' subdivided to
	 * get new red chain. All new edges and red edges come from new 
	 * vertices and red vertices.
	 * Calling routine should run 'd_redChainBuilder' next.
	 * @param pdcel PackDCEL, complete with faces,edges,vertices 
	 * @return new PackDCEL
	 */
	public static PackDCEL hexRefine(PackDCEL pdcel) {
		PackDCEL ndcel=new PackDCEL();
		ndcel.idealFaceCount=pdcel.idealFaceCount;
		int eCount=pdcel.edges.length-1;
		
		// 'util' < 0 will mark bdry edges
		for (int e=1;e<=eCount;e++) {
			HalfEdge he=pdcel.edges[e];
			if (he.isBdry())
				he.util=-1;
			else
				he.util=0;
		}
		
		// tmp lists for vertices/edges, starting with originals
		ArrayList<Vertex> tmpVerts=new ArrayList<Vertex>();
		ArrayList<HalfEdge> tmpEdges=new ArrayList<HalfEdge>();
		ArrayList<Face> tmpFaces=new ArrayList<Face>();

		// half all occurrences of radii (radius may depend on edge)
		int origCount=pdcel.vertCount;
		for (int v=1;v<=origCount;v++) { // in normal storage 
			pdcel.p.vData[v].rad=pdcel.p.vData[v].rad/2.0;
		}
		RedHEdge rtrace=pdcel.redChain; 
		if (rtrace!=null) { // also, in any 'RedHEdge's
			do {
				rtrace.rad=rtrace.rad/2.0;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);
		}

		// add vertices to 'tmpVerts'
		for (int v=1;v<=origCount;v++) 
			tmpVerts.add(pdcel.vertices[v]);

		// add edges to 'tmpEdges'
		for (int e=1;e<=eCount;e++) {
			tmpEdges.add(pdcel.edges[e]);
		}
		
		// keep track of counts/indices
		int etick=eCount;
		int vtick=pdcel.vertCount;
		
		// first loop to subdivide original edges 
		for (int e=1;e<=eCount;e++) {
			HalfEdge edge=pdcel.edges[e];
			
			// if not already handled
			if (edge.util<=0) {
				boolean bdry=(edge.util==-1);
				HalfEdge tedge=edge.twin;
				
				// make > 0 so we don't revisit
				edge.util=etick;
				tedge.util=etick;
				
				RedHEdge redge=edge.myRedEdge;
				RedHEdge tredge=tedge.myRedEdge;
				
				// two new edges
				HalfEdge newEdge=new HalfEdge();
				newEdge.util=++etick;
				newEdge.edgeIndx=etick;
				newEdge.invDist=edge.invDist;
				HalfEdge newTwin=new HalfEdge();
				newTwin.util=++etick;
				newTwin.edgeIndx=etick;
				newTwin.invDist=edge.invDist;
				tmpEdges.add(newEdge);
				tmpEdges.add(newTwin);
				
				// fix twins
				edge.twin=newTwin;
				newTwin.twin=edge;
				newEdge.twin=tedge;
				tedge.twin=newEdge;
				
				// fix 
				newEdge.next=edge.next;
				newEdge.next.prev=newEdge;
				edge.next=newEdge;
				newEdge.prev=edge;

				newTwin.next=tedge.next;
				newTwin.next.prev=newTwin;
				newTwin.prev=tedge;
				tedge.next=newTwin;

				// if this is a red edge
				if (redge!=null || tredge!=null) {
					RedVertex midRedvert=new RedVertex(++vtick);
					tmpVerts.add(midRedvert);
					newEdge.origin=midRedvert;
					newTwin.origin=midRedvert;
					midRedvert.halfedge=newEdge;

					// does 'edge' have red edge?
					if (redge!=null) {
						RedHEdge newRedge=new RedHEdge(newEdge);
						newEdge.myRedEdge=newRedge;
						newRedge.nextRed=redge.nextRed;
						newRedge.prevRed=redge;
						redge.nextRed.prevRed=newRedge;
						redge.nextRed=newRedge;
					}
					// does 'tedge' have red edge?
					if (tredge!=null) {
						RedHEdge newRedge=new RedHEdge(newTwin);
						newTwin.myRedEdge=newRedge;
						newRedge.nextRed=tredge.nextRed;
						newRedge.prevRed=tredge;
						tredge.nextRed.prevRed=newRedge;
						tredge.nextRed=newRedge;
					}
					// if both, then set up red twinning
					if (redge!=null && tredge!=null) {
						redge.twinRed=newTwin.myRedEdge;
						newTwin.myRedEdge.twinRed=redge;
						tredge.twinRed=newEdge.myRedEdge;
						newEdge.myRedEdge.twinRed=tredge;
					}
				}
				// else a normal vertex
				else {
					Vertex midvert=new Vertex(++vtick);
					tmpVerts.add(midvert);
					newEdge.origin=midvert;
					newTwin.origin=midvert;
					midvert.halfedge=newEdge;
				}
				
				// original 'edge' bdry? Same for new vert 
				if (bdry) {
					edge.twin.origin.bdryFlag=1;
				}
			}				
		} // done with look through edges
		
		// loop on faces, create new edges -- one pair for each vert
		int ftick=0;
		for (int f=1;f<=pdcel.intFaceCount;f++) {
			Face face=pdcel.faces[f];
			ArrayList<HalfEdge> edges=face.getEdges();
			Iterator<HalfEdge> eits=edges.iterator();
			while (eits.hasNext()) {
				HalfEdge edgeout=eits.next();
				edgeout.face=null;
				if (edgeout.origin.vertIndx>origCount)
					continue;
				
				// starting edges
				HalfEdge edgeout2=edgeout.next;
				HalfEdge edgein=edgeout.prev;
				HalfEdge edgein2=edgein.prev;
				
				// new twins
				HalfEdge newedge=new HalfEdge(edgeout.twin.origin);
				newedge.edgeIndx=++etick;
				tmpEdges.add(newedge);
				HalfEdge newtwin=new HalfEdge(edgein.origin);
				newtwin.edgeIndx=++etick;
				tmpEdges.add(newtwin);
				
				newedge.twin=newtwin;
				newtwin.twin=newedge;
				
				// fix newedge
				newedge.next=edgein;
				edgein.prev=newedge;
				
				edgeout.next=newedge;
				newedge.prev=edgeout;
				
				// fix newtwin
				newtwin.next=edgeout2;
				edgeout2.prev=newtwin;
				
				edgein2.next=newtwin;
				newtwin.prev=edgein2;
				
				// create/store faces
				Face newface=new Face();
				newface.faceIndx=++ftick;
				newface.edge=edgeout;
				tmpFaces.add(newface);
				HalfEdge he=edgeout;
				do {
					he.face=newface;
					he=he.next;
				} while (he!=edgeout);
				
			} // end of while through face edges
		} // done with faces

		// count/store vertices, 
		ndcel.vertCount=tmpVerts.size();
		ndcel.vertices=new Vertex[ndcel.vertCount+1];
		Iterator<Vertex> tvit=tmpVerts.iterator();
		vtick=0;
		while(tvit.hasNext()) {
			ndcel.vertices[++vtick]=tvit.next();
		}
		
		// count/store edges
		ndcel.edgeCount=tmpEdges.size();
		ndcel.edges=new HalfEdge[ndcel.edgeCount+1];
		if (etick!=ndcel.edgeCount) {
			throw new CombException("'ndcel' edge counts don't match: etick="+etick
					+" and edgeCount="+ndcel.edgeCount);
		}
		Iterator<HalfEdge> tis=tmpEdges.iterator();
		etick=0;
		while (tis.hasNext()) {
			ndcel.edges[++etick]=tis.next();
		}

/*		
		// count/store faces
		ndcel.intFaceCount=ftick;
		ndcel.faces=new Face[ftick+1];
		Iterator<Face> tfit=tmpFaces.iterator();
		ftick=0;
		while(tfit.hasNext()) {
			Face face=tfit.next();
			ndcel.faces[face.faceIndx]=face;
		}
*/
		
		// final settings
		ndcel.redChain=pdcel.redChain;
		ndcel.alpha=pdcel.alpha;
		ndcel.gamma=pdcel.gamma;
		return ndcel;
	}
	
	/**
	 * Given a 'HalfEdge', check if origin is 'RedVertex'. If so,
	 * return the next clw 'RedHEdge' (possibly itself). If not,
	 * return null;
	 * @param edge HalfEdge
	 * @return RedHEdge or null 
	 */
	public static RedHEdge nextRedEdge(HalfEdge edge) {
		if (!(edge.origin instanceof RedVertex))
			return null;
		if (edge.myRedEdge!=null)
			return edge.myRedEdge;
		HalfEdge he=edge;
		do {
			if (he.myRedEdge!=null)
				return he.myRedEdge;
			he=he.twin.next;
		} while(he!=edge);
		return null;
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
		for (int j = 0; j < flower.length; j++)
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
	 * For edges of this face, set 'util' to 1 if it is zero.
	 * @param edge HalfEdge
	 */
	public static void markFaceUtils(HalfEdge edge) {
		HalfEdge he=edge;
		do {
			if (he.util==0)
				he.util=1;
			he=he.next;
		} while(he!=edge);
	}
	
	/**
	 * Given a bouquet, return a bouquet with reverse orientation.
	 * @param bouq int[][]
	 * @return new bouquet[][]
	 */
	public static int[][] reverseOrientation(int[][] bouq) {
		int vcount=bouq.length-1;
		int [][] newBouquet=new int[vcount+1][];
		for (int v=1;v<=vcount;v++) {
			int len=bouq[v].length;
			newBouquet[v]=new int[len];
			for (int j=0;j<len;j++) {
				newBouquet[v][j]=bouq[v][len-j-1];
			}
		}
		return newBouquet;
	}
	

	/**
	 * check if all all edges from 'edge' origin vertex
	 * have non-zero 'util', i.e. are forbidden, bdry, or already touched
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
	
	/** 
	 * Remove one vertex. This is called a "puncture" if 'v' has 2 
	 * generations of interior neighbors. Otherwise, may result in
	 * bdry vertices with no interior neighbors. 
	 * 
	 * TODO: coordinate with "rm_cir", adjust lists of verts/faces 
	 * 
	 * @param pdcel PackDCEL
	 * @param v int
	 * @return PackDCEL
	 */
	public static PackDCEL d_puncture_vert(PackDCEL pdcel,int v) {
		// if 'v' is 'alpha', need to modify 'alpha' in the new DCEL
		StringBuilder strbld=new StringBuilder(Integer.toString(v)+" ");
		HalfEdge baseEdge=pdcel.alpha;
		if (v==pdcel.alpha.origin.vertIndx) {
			baseEdge=pdcel.alpha.next;
			// ask for this to be new seed
			strbld.append(" -v "+Integer.toString(baseEdge.origin.vertIndx));
		}
		HalfLink hlink=d_CookieData(pdcel.p,strbld.toString());
	    return CombDCEL.extractDCEL(pdcel,hlink,baseEdge);
	}

	public static HalfLink d_CookieData(PackData p,String str) {
		Vector<Vector<String>> flagSegs=StringUtil.flagSeg(str);
		return d_CookieData(p,flagSegs);
	}
	
	/**
	 * Process the incoming strings to set seed and forbidden 
	 * edges for cookie'ing DCEL structures. Forbidden edges 
	 * one which are never crossed in building the red chain. 
	 * 
	 * If there is an input list of vertices to be excised, 
	 * they should appear in  first vector of strings in 
	 * 'flags' without a preceding flag. Outer edges about 
	 * these will be included as forbidden.
	 * 
	 * Then check for flag segments:
	 * * Flags: -v {v}, for identifying seed to replace 'p.alpha'.
	 * * Flag -e {u v...} is edge list, adds to any forbidden
	 *   already included.
	 * * flag -n {v}, non-keepers; any edges with both ends
	 *   non-keepers will be added as forbidden edges.
	 *   
	 * Note the difference between vertices to be excised
	 * and one that are non-keepers. The latter may remain
	 * in the boundary of the resulting DCEL structure.
     *
	 * If no verts are listed and poisonVerts was empty on entry, then the 
	 *   points on the side of 'ClosedPath' (if there is one) opposite to 'seed' 
	 *   are poison by default.
	 *   
	 * @param p PackData
	 * @param flags Vector<Vector<String>>; may be null
	 * @return HalfLink, forbidden edges
	 */
	public static HalfLink d_CookieData(PackData p,Vector<Vector<String>> flags) {
		boolean debug=false;
		PackDCEL pdcel=p.packDCEL;
		if (pdcel==null) 
			return null;
		NodeLink vlink=new NodeLink();
		HalfLink hlink=new HalfLink();
		
		// read incoming data
		while (flags!=null && flags.size()>0) { 
			Vector<String> items=(Vector<String>)flags.remove(0);
			if (!StringUtil.isFlag(items.get(0))) { // not flag? must be poison vertices
				vlink=new NodeLink(p,items);
			}
			else {
				String str=(String)items.get(0);
				if (str.equals("-v")) { // set seed
					if (items.size()<2) 
						throw new ParserException("cookie crumbled: error in -v flag");
					pdcel.alpha=pdcel.vertices[Integer.parseInt((String)items.get(1))].halfedge;
					items.remove(1);
					items.remove(0);
				}
				else if (str.equals("-e")) { // get poison edges (kill any poison verts)
					if (items.size()<2) 
						throw new ParserException("cookie crumbled: no edges with -e flag");
					items.remove(0);
					EdgeLink elink=new EdgeLink(p,items);
					hlink.addSimpleEdges(pdcel, elink);
				}
				else if (str.equals("-n")) { // non keepers
					if (items.size()<1) 
						throw new ParserException("cookie crumbled: nothing with -n flag");
					items.remove(0);
					NodeLink nonvs=new NodeLink(p,items);
					
					// set 'util' to zero
					for (int v=1;v<=pdcel.vertCount;v++) 
						pdcel.vertices[v].util=0;

					Iterator<Integer> nis=nonvs.iterator();
					while (nis.hasNext()) {
						int v=nis.next();
						pdcel.vertices[v].util=v;
					}
					
					int w;
					for (int v=1;v<=pdcel.vertCount;v++) {
						Vertex vert=pdcel.vertices[v];
						if (vert.util!=0) {
							int[] flower=vert.getFlower();
							for (int j=0;j<flower.length;j++) {
								if ((w=pdcel.vertices[j].util)>v)
									hlink.add(pdcel.findHalfEdge(v,w));
							}
						}
					}
				} // done with 'n' flag
			} // done with flags
		} // done with while through segments
				
		// If no poisons so far, then use stored 'ClosePath'
		if (hlink.size()==0 && vlink.size()==0) {
			if (CPBase.ClosedPath==null) 
				throw new ParserException("cookie: No path defined.");
			boolean seed_wrap=
					PathManager.path_wrap(p.getCenter(pdcel.alpha.origin.vertIndx)); // which side is seed on?
			for (int v=1;v<=p.nodeCount;v++) {
				if (seed_wrap!=PathManager.path_wrap(p.getCenter(v))) { 
					vlink.add(v);
				}
			}
		}
		
		if (vlink.size()==0)
			return hlink;
		hlink.separatingLinks(pdcel,vlink,pdcel.alpha.origin.vertIndx);
		if (debug) { // debug=true;
			deBugging.DCELdebug.drawHalfLink(p, hlink);
		}
		return hlink;
	}
	
	/** 
	 * Remove one face. 
	 * 
	 * TODO: coordinate with "rm_face", adjust lists of verts/faces
	 * 
	 * @param pdcel PackDCEL
	 * @param f int
	 * @return PackDCEL
	 */
	public static PackDCEL d_puncture_face(PackDCEL pdcel,int f) {
		Face face=pdcel.faces[f];
		HalfLink hlink=new HalfLink();
		HalfEdge he=face.edge;
		hlink.add(he);
		hlink.add(he.next);
		hlink.add(he.next.next);
		return extractDCEL(pdcel,hlink,pdcel.alpha);
	}
	  
	/**
     * Create an exact duplicate of this PackDCEL with all new objects,
     * PackData set to null and 'triData' is not copied.
     */
    public static PackDCEL clone(PackDCEL pdc) {
    	PackDCEL pdcel=new PackDCEL();
    	pdcel.triData=null; // don't clone, as is generally temporary data
    	pdcel.vertCount=pdc.vertCount;
    	pdcel.edgeCount=pdc.edgeCount;
    	pdcel.faceCount=pdc.faceCount;
    	pdcel.intFaceCount=pdc.intFaceCount;
    	pdcel.idealFaceCount=pdc.idealFaceCount;
    	pdcel.newOld=null;
    	if (pdc.newOld!=null && pdc.newOld.size()>0) 
    		pdcel.newOld=pdc.newOld.clone();
    	
    	// --------------- create the new objects -----------------
    	// Caution: these replicate old pointers; reset later
    	
    	// count/catalog redchain edges: note that red edges aen't
    	//   indexed, so 'redutil' will be tmp index (from 1)
    	int redcount=0;
    	if (pdc.redChain!=null) {
    		RedHEdge rtrace=pdc.redChain;
    		do {
    			redcount++;
    			rtrace=rtrace.nextRed;
    		} while(rtrace!=pdc.redChain);
    	}
    	RedHEdge[] newRedEdges=null;
    	EdgeSimple[] red2edge=null; // <r,e> r=red index, e=myEdge index
    	if (redcount>0) {
    		newRedEdges=new RedHEdge[redcount+1];
    		red2edge=new EdgeSimple[redcount+1];
    		int rtick=0;
    		RedHEdge oldrtrace=pdc.redChain;
    		do {
    			newRedEdges[++rtick]=oldrtrace.clone();
    			// set index
    			newRedEdges[rtick].redutil=oldrtrace.redutil=rtick;
    			red2edge[rtick]=new EdgeSimple(rtick,oldrtrace.myEdge.edgeIndx);
    			oldrtrace=oldrtrace.nextRed;
    		} while(oldrtrace!=pdc.redChain);
    	}

    	pdcel.vertices=new Vertex[pdc.vertCount+1];
    	for (int v=1;v<=pdc.vertCount;v++) {
    		pdcel.vertices[v]=pdc.vertices[v].clone();
    	}
    	pdcel.edges=new HalfEdge[pdc.edgeCount+1];
    	for (int e=1;e<=pdc.edgeCount;e++) {
    		pdcel.edges[e]=pdc.edges[e].clone();
    	}
    	pdcel.faces=new Face[pdc.faceCount+1];
    	for (int f=1;f<=pdc.faceCount;f++) {
    		pdcel.faces[f]=pdc.faces[f].clone();
    	}
    	pdcel.idealFaces=new Face[pdc.intFaceCount+1];
    	for (int f=1;f<=pdc.idealFaceCount;f++) {
    		pdcel.idealFaces[f]=pdc.idealFaces[f].clone();
    	}
    	
    	// ------------------ reset pointers ------------------
    	
    	// replace old pointers with parallel new objects, using
    	//   indices to translate.
    	
    	// Vertex's: new vert gets new 'halfedge' attached
    	for (int v=1;v<=pdcel.vertCount;v++) {
    		pdcel.vertices[v].halfedge=
    			pdcel.edges[pdc.vertices[v].halfedge.edgeIndx];
    	}
    	
    	// HalfEdge's: 
    	for (int ee=1;ee<=pdcel.edgeCount;ee++) {
    		HalfEdge newhe=pdcel.edges[ee];
    		HalfEdge oldhe=pdc.edges[ee];
    		if (newhe.myRedEdge!=null) {
    			newhe.myRedEdge=newRedEdges[oldhe.myRedEdge.redutil];
    			newhe.myRedEdge.myEdge=newhe;
    		}
    		newhe.next=pdcel.edges[oldhe.next.edgeIndx];
    		newhe.prev=pdcel.edges[oldhe.prev.edgeIndx];
    		newhe.twin=pdcel.edges[oldhe.twin.edgeIndx];
    		newhe.origin=pdcel.vertices[pdc.edges[ee].origin.vertIndx];
    		int findx=oldhe.face.faceIndx;
    		if (findx>0)
    			newhe.face=pdcel.faces[findx];
    		else
    			newhe.face=pdcel.idealFaces[-findx];
    	}
    	for (int f=1;f<=pdcel.faceCount;f++) {
    		Face face=pdcel.faces[f];
    		face.edge=pdcel.edges[pdc.faces[f].edge.edgeIndx];
    	}
    	for (int f=1;f<=pdcel.idealFaceCount;f++) {
    		Face face=pdcel.idealFaces[f];
    		face.edge=pdcel.edges[pdc.idealFaces[f].edge.edgeIndx];
    	}
    	if (pdc.redChain!=null) {
    		pdcel.redChain=newRedEdges[pdc.redChain.redutil];
    		for (int j=1;j<=redcount;j++) {
    			RedHEdge reg=newRedEdges[j];
    			reg.nextRed=newRedEdges[reg.nextRed.redutil];
    			reg.prevRed=newRedEdges[reg.prevRed.redutil];
    			if (reg.twinRed!=null) 
    				reg.twinRed=newRedEdges[reg.twinRed.redutil];
    		}
    	}
    	
    	if (pdc.pairLink!=null && pdc.pairLink.size()>1) {
    		pdcel.pairLink=new D_PairLink();
    		pdcel.pairLink.add(null);
    		Iterator<D_SideData> pis=pdc.pairLink.iterator();
    		pis.next(); // flush first null entry
    		while (pis.hasNext()) {
    			RedHEdge rhe=null;
    			D_SideData sd=pis.next().clone();

    			// fix pointers
    			if ((rhe=sd.startEdge)!=null)
    				sd.startEdge=newRedEdges[rhe.redutil];
    			if ((rhe=sd.endEdge)!=null)
    				sd.endEdge=newRedEdges[rhe.redutil];
    				
    			pdcel.pairLink.add(sd.clone());
    		}
    		
    	}
    	return pdcel;
    }
    
    /**
     * Return linked list of verts on the same bdry component
     * as 'v' using DCEL structure. Null on error or 'v' not bdry. 
     * @param p PackData
     * @param v int
     * @return new NodeLink, null on failure
     */
    public static NodeLink bdryCompVerts(PackData p,int v) {
    	if (p.packDCEL==null)
    		throw new DCELException("'bdryCompVerts' routines requires DCEL structure");
    	if (!p.isBdry(v))
    		return null;
    	Vertex vert=p.packDCEL.vertices[v];
    	Face idealf=vert.halfedge.twin.face;
    	int[] fverts=idealf.getVerts();
    	int len=fverts.length;
    	int offset=-1;
    	for (int j=0;(j<len && offset<0);j++) {
    		if (fverts[j]==v)
    			offset=j;
    	}
    	if (offset<0)
    		throw new DCELException("v="+v+" was not found on bdry component");
    	
    	// put in list with v first
    	NodeLink vlink=new NodeLink(p);
    	for (int j=0;j<len;j++) {
    		vlink.add(fverts[(j+offset)%len]);
    	}
    	return vlink;
   	}

}