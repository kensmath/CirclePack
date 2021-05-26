package dcel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
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
 * 
 * The "*_raw" methods typically work just with combinatorics
 * (no rad/cents, little or no dependence on PackData parent).
 * The calling routines do further processing, generally calling
 * 'd_FillInside' and then 'attachDCEL' to the packing. If red 
 * chain cannot be modified, then 'redChain' is set to null and
 * calling routine would run 'redchain_by_edge'. See 'fixDCEL_raw'.
 * 
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
	 * DCEL structure with 'vertices' and 'edges' only. This
	 * does not attach the DCEL structure.
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
	 * Form red chain in 'pdcel' based on given 'alphaEdge' and not
	 * crossing any edge in 'hlink'. I've tried to keep indexing of
	 * vertices as much as possible. Calling routine should call
	 * 'd_FillInside' to complete processing of 'pdcel'.
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink
	 * @param alphaEdge HalfEdge (if null, revert to 'pdcel.alpha')
	 * @return 
	 */
	public static PackDCEL redchain_by_edge(PackDCEL pdcel,
			HalfLink hlink,HalfEdge alphaEdge) {
		
		// debug? Try to draw things on existing packing
		//    debug=true;debugPack=CPBase.packings[0];
		boolean debug=false; 
		PackData debugPack=pdcel.p; // 
		boolean click=false; // help debug
		
		int vertcount=pdcel.vertCount;

		// reset 'alpha' to given edge, but avoid 'hlink'
		if (alphaEdge==null)
			alphaEdge=pdcel.alpha;
		if (alphaEdge==null)
			pdcel.setAlpha(1,NodeLink.incident(hlink));
		// if 'alphaEdge' is not null, check forbidden
		else {
			int alp=alphaEdge.origin.vertIndx;
			pdcel.setAlpha(alp,NodeLink.incident(hlink));
		}

		// ============= mark verts/edges ==============
		
		// undo 'redFlag's, null 'redChain', set 'eutil'/'vstat':
		//   eutil -1 for forbidden edges/twins
		//   vstat: -1; at least one forbidden edge or bdry
		//          +1; interior, in comp of interiors with 'alpha'
		//          +2; touches a +1 (converted from -1)  
		int[] vstat=new int[vertcount+1];
 		for (int k=1;k<=vertcount;k++) {
			Vertex vtx=pdcel.vertices[k];
			vtx.redFlag=false;
			// 'eutil's identify forbidden/bdry edges
			HalfEdge edge=vtx.halfedge;
			HalfEdge he=edge;
			do {
				he.myRedEdge=null; // toss old red edge pointers
				he.eutil=0;
				// set 'eutil' -1 for bdry edges
				if ((he.face!=null && he.face.faceIndx<0) || 
						(he.twin.face!=null && he.twin.face.faceIndx<0)) {
					he.eutil=-1;
					vstat[he.origin.vertIndx]=-1;
					vstat[he.twin.origin.vertIndx]=-1;
				}
				he=he.prev.twin;
			} while (he!=edge);
		}
		pdcel.redChain=null;
		
		if (hlink!=null) {
			Iterator<HalfEdge> his=hlink.iterator();
			while (his.hasNext()) {
				HalfEdge he=his.next();
				he.eutil=-1;
				he.twin.eutil=-1;
				vstat[he.origin.vertIndx]=-1;
				vstat[he.twin.origin.vertIndx]=-1;
			}
		}
		
		// 'vstat' =1 if interior connected comp with alpha 
		NodeLink nxv=new NodeLink();
		nxv.add(pdcel.alpha.origin.vertIndx);
		vstat[pdcel.alpha.origin.vertIndx]=1;
		NodeLink curv=new NodeLink();
		while (nxv.size()>0) {
			curv=nxv;
			nxv=new NodeLink();
			Iterator<Integer> cis=curv.iterator();
			while (cis.hasNext()) {
				int v=cis.next();
				int[] flower=pdcel.vertices[v].getFlower();
				for (int j=0;j<flower.length;j++) {
					if (vstat[flower[j]]==0) {
						vstat[flower[j]]=1;
						nxv.add(flower[j]);
					}
					else if (vstat[flower[j]]==-1)
						vstat[flower[j]]=2;
				}
			}
		}
		
		// ============== start redchain using alpha ================
		HalfEdge he=pdcel.alpha;
		pdcel.redChain=new RedHEdge(he);
		RedHEdge rtrace=pdcel.redChain;
		do {
			he=he.next;
			pdcel.redChain.nextRed=new RedHEdge(he);
			pdcel.redChain.nextRed.prevRed=pdcel.redChain;
			pdcel.redChain=pdcel.redChain.nextRed;
		} while(he.next!=pdcel.alpha);
		// close up
		pdcel.redChain.nextRed=rtrace;
		rtrace.prevRed=pdcel.redChain;
		pdcel.redChain=pdcel.redChain.nextRed;
		
		if (debug) {
			DCELdebug.drawTmpRedChain(pdcel.p,pdcel.redChain);
		}

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
		int redN=0;
		while (hit && pdcel.redChain!=null && !redisDone) {
			hit = false; // debug=true;

			// current count of red chain as safety stop mechanism
			redN=0;
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
				// working on v; u,v,w successive verts cclw 
				// along redchain 
				int v = currRed.myEdge.origin.vertIndx;
				
				if (debug && click) { // debug=true;
					DCELdebug.drawTmpRedChain(debugPack,currRed);
//					DCELdebug.printRedChain(pdcel.redChain);
				}
				
				// shrink backtrack?
				HalfEdge upspoke = currRed.prevRed.myEdge.twin;
				HalfEdge downspoke = currRed.myEdge;
				if (upspoke==downspoke && vstat[v]==1) {
					currRed.prevRed.prevRed.nextRed=currRed.nextRed;
					currRed.nextRed.prevRed=currRed.prevRed.prevRed;
					upspoke.myRedEdge=null;
					downspoke.myRedEdge=null;
					doneV[v]=true;
				}
				
				// process v if not done and previous red edge not blocked
				click=false;
				if (!doneV[v] && !(doneV[v]=isVertDone(currRed.myEdge))) {
					click=true;
					// if 'upspoke' is forbidden, we go on
					if (upspoke.eutil<0) { 
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
					
					// Remainder break into cases:
					// If 'vstat'==1, interior, see if we can enclose it. 
					//    Check fan of edge outside current red segment 
					//    to see if any are red/forbidden. If not, can 
					//    close up. Otherwise, following code tries to 
					//    add one cclw face.
					boolean canclose=false;
					RedHEdge redge=null;
					HalfEdge spktrace=upspoke;
					if (vstat[v]==1) {
						redge=isMyEdge(pdcel.redChain,spktrace);
						while (spktrace!=downspoke && redge==null && spktrace.eutil>=0) {
							spktrace=spktrace.prev.twin;
							redge=isMyEdge(
									pdcel.redChain,spktrace);
						}
						if (redge==currRed && redge.myEdge.eutil>=0) // yes, we can close up around v 
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
								DCELdebug.drawEdgeFace(debugPack, es);
								DCELdebug.drawTmpRedChain(debugPack,pdcel.redChain);
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
					
					// else, if 'upspoke' is not red, and one or the other
					//    end has 'vstat'>=1 (is or touches interior component
					//    of alpha), add one cclw face about v.
					else if (isMyEdge(pdcel.redChain,upspoke)==null &&
								isMyEdge(pdcel.redChain,upspoke.next)==null &&
								(vstat[upspoke.origin.vertIndx]>=1  || 
								vstat[upspoke.twin.origin.vertIndx]>=1)) {
						cclw.nextRed=new RedHEdge(upspoke.next);
						
						if (debug) {
							EdgeSimple es=new EdgeSimple(upspoke.origin.vertIndx,
								upspoke.twin.origin.vertIndx);
							DCELdebug.drawEdgeFace(debugPack, es);
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
			
			if (debug) { // debug=true;
				DCELdebug.printRedChain(pdcel.redChain);
			 // DCELdebug.redConsistency(pdcel);
			}
	
		} // end of main loop while, should be done creating red chain
		
		// though all are "doneV", may still be backtrack and
		//   perhaps degeneracy
		hit=true;
		while (hit && pdcel.redChain!=null) {
			hit=false;
			redN=2;
			rtrace=pdcel.redChain;
			do {
				redN++;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);

			rtrace=pdcel.redChain;
			do {
				redN--;
				// if backtrack with nextRed
				if (rtrace.myEdge.eutil>=0 &&
						rtrace.myEdge==rtrace.nextRed.myEdge.twin) {
					
					// do we have to keep 'redChain' alive?
					if (rtrace==pdcel.redChain || rtrace.nextRed==pdcel.redChain) {
						
						// if only two edges left, either they twin or we're done
						if (rtrace.nextRed.nextRed==rtrace) {
							if (rtrace.myEdge==rtrace.nextRed.myEdge.twin) {
								pdcel.redChain=null;
							}
							return pdcel;
						}
						
						pdcel.redChain=rtrace.prevRed;
						
					}
					RedHEdge rhold=rtrace.prevRed;
					rtrace.myEdge.myRedEdge=null;
					rtrace.nextRed.myEdge.myRedEdge=null;
					rtrace.prevRed.nextRed=rtrace.nextRed.nextRed;
					rtrace.nextRed.nextRed.prevRed=rtrace.prevRed;
					rtrace=rhold;
					hit=true;
				}
				rtrace=rtrace.nextRed;
			} while (redN>0);
		}
		
		debug=false; // DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
		 // DCELdebug.printRedChain(pdcel.redChain);
	     // DCELdebug.EdgeOriginProblem(pdcel.edges);
		 // debug=true;
		 // DCELdebug.redChainEnds(pdcel.redChain);
  		 // DCELdebug.redConsistency(pdcel);
		
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
		//   though it may gain new red vertices.
		// Set 'myRedEdge's

		nxtre=pdcel.redChain;
		do {
			if (debug) { // debug=true;
				System.out.println("nxtre="+nxtre.myEdge);
			}
			if (nxtre.twinRed==null) {
				// does twin edge also red and not forbidden?
				HalfEdge ctwin=nxtre.myEdge.twin;
				RedHEdge crossRed=ctwin.getRedEdge();
				if (debug) {
					System.out.println("twinRed is null: twin eutil="+
							ctwin.eutil);
				}
				if (crossRed!=null && ctwin.eutil>0) {
					if (debug) {
						System.out.println("  crossRed="+crossRed.myEdge);
					}
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
			
// System.out.println("spokes for "+v+" and "+w);			
			
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);

		// DCELdebug.redChainEnds(pdcel.redChain);

		// =========== process to get 'RedVertex's =============
		
		// The 'redChain' of 'RedHEdge's has not changed, but some of
		//   the 'Vertex's it passes through will be new as we
		//   process the 'PreRedVertex's.
		// Pass through the redChain. When you encounter a 'PreRedVertex', 
		//   then it is processed (after rotating, if necessary); 
		//   this entry in 'pdcel.vertices' converts to a 'Vertex' with
		//   redFlag set and new 'Vertex's may be introduced elsewhere 
		//   in the redChain.
		ArrayList<Vertex> addedVertices=new ArrayList<Vertex>(); // new vertices
		rtrace=pdcel.redChain;
		do {
			int v=Math.abs(rtrace.myEdge.origin.vertIndx);
			
// System.out.println("handle vert "+v);

			// if not processed, then it's siblings are not created yet either
			if (pdcel.vertices[v] instanceof PreRedVertex) { 
				PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];

				// process: convert to 'Vertex's with 'redFlag's set;
				//    may create new verts and insert in redchain.
				//    indices set to negative of parent indices.
				ArrayList<Vertex> redAdded=process(rV);
					
// System.out.println(" next red v = "+v);

				// first of the new vertex replaces the original
				Vertex newV=redAdded.get(0);
				pdcel.vertices[v]=newV;

				// any remaining are added to 'newVertices'.
				// Identified as new by negative 'vertIndx'.
				int sz=redAdded.size();
				if (sz>1) 
					for (int j=1;j<sz;j++) { 
						Vertex nv=redAdded.get(j);
						nv.vertIndx=-nv.vertIndx;  // indicate with negative
						addedVertices.add(redAdded.get(j));
					}
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		debug=false;		
		
		// ========== set bdry next/prev =============
		// redChain vertices that were interior got no new edges;
		// others got a new twin for the 'inSpoke' of their fan
		rtrace=pdcel.redChain;
		do {
			Vertex rvert=rtrace.myEdge.origin;
// System.out.println(rvert.vertIndx+"  --> ");			
			if (rvert.bdryFlag==1 && rvert.spokes!=null) {
				try {
					int num=rvert.spokes.length-1;
					rvert.spokes[0].twin.next=rvert.spokes[num];
					rvert.spokes[num].prev=rvert.spokes[0].twin;
				} catch(Exception ex) {
					throw new DCELException("vertex "+rvert.vertIndx+" should have 'spokes' entries");
				}
				rvert.spokes=null;
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
		
		// Try to keep as many indices of original vertices 
		// unchanged as possible. Then we have some added vertices
		// to slot in to open slots in the index set. Then we have
		// to do successive shifts to keep indices contiguous.
		
		// search red chain for original vertices not interior or
		//    next to interior with vstat set to 3.
		rtrace=pdcel.redChain;
		do {
			int v=rtrace.myEdge.origin.vertIndx;
			if (v>0 && vstat[v]<=0) // w/o interior nghb
				vstat[v]=3;
			rtrace=rtrace.nextRed;
		} while(rtrace!=pdcel.redChain);

		// first, get counts: max possible and actual
		int maxCount=vertcount; // max possible
		if (addedVertices!=null) {
			maxCount+=addedVertices.size();
		}
		int totalCount=0; // total (original and added) 
		// vertices picked up during processing.
		for (int k=1;k<=vertcount;k++) {
			if (vstat[k]>0)
				totalCount++;
		}
		if (addedVertices!=null)
			totalCount+=addedVertices.size();

		// accounting: 
		int[] vindices=new int[maxCount+1]; // hold final indices
		boolean[] openslots=new boolean[maxCount+1]; // true=open slot

		// make new array of vertices
		ArrayList<Vertex> newVertices=new ArrayList<Vertex>(0);
		// keep indices of existing vertices up to 'vertcount'
		for (int v=1;v<=vertcount;v++) {
			if (vstat[v]>0) {
				newVertices.add(pdcel.vertices[v]);
				vindices[v]=v;
			}
		}
		// make remaining open slots
		for (int j=1;j<=maxCount;j++) {
			if (vindices[j]==0)
				openslots[j]=true;
		}
		
		// added vertices go into successive slots
		int slotptr=0;
		if (addedVertices!=null && addedVertices.size()>0) {
			Iterator<Vertex> vit=addedVertices.iterator();
			while (vit.hasNext() && slotptr<=totalCount) {
				Vertex newV=vit.next();
				while (!openslots[slotptr])
					slotptr++;
				if (slotptr>totalCount)
					throw new CombException("outran valid slots, totalCount = "+totalCount);
				newV.vertIndx=slotptr;
				newVertices.add(newV);
				openslots[slotptr]=false;
				vindices[slotptr]=-slotptr; // store as negative
				slotptr++;
			}
		}
		while (slotptr<=totalCount && !openslots[slotptr])
			slotptr++;
		if (slotptr>(totalCount+1))
			throw new CombException("overran total slots");
		
		// still-open slots: decrease later indices to keep contiguity
		while (slotptr<maxCount) {
			for (int j=slotptr+1;j<=maxCount;j++) {
				if (vindices[j]!=0)
					vindices[j]--;
			}
			slotptr++;
			while (slotptr<maxCount && !openslots[slotptr])
				slotptr++;
		}
		
		// Now set the new indices
		VertexMap newold=new VertexMap();
		Iterator<Vertex> nit=newVertices.iterator();
		Vertex[] newarray=new Vertex[totalCount+1];
		while (nit.hasNext()) {
			Vertex vert=nit.next();
			int oldindx=vert.vertIndx;
			int k=vindices[oldindx]; // negative for added vert
			vert.vertIndx=Math.abs(k);
			newarray[vert.vertIndx]=vert;
			// record in newold only when different and old was original
			if (k>0 && k!=oldindx)
				newold.add(new EdgeSimple(k,oldindx));
		}

		pdcel.vertCount=totalCount;
		pdcel.vertices=newarray;
		pdcel.newOld=newold;
		
//		blueCleanup(pdcel); // try to eliminate blue faces in the 'redChain'
		
		// DCELdebug.drawRedChain(pdcel.p,pdcel.redChain);
		return pdcel;
	}
		 
	/** 
	 * TODO: worth pursuing? Lots of situations to cover.
	 * 
	 * See if the red chain can be shifted to eliminate some blue
	 * faces. Cycle through because some may need to be eliminated
	 * before others.
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
//					RedHEdge upedge=rtrace.prevRed;

					// TODO: put off for now: 10/3/2020

				}
			} while (rtrace!=pdcel.redChain);
		}
	}

	/**
	 * Given a DCEL structure with an already processed red chain, 
	 * an 'alpha' edge, and 'vertices' updated, we process to 
	 * create 'faces', 'edges', layout order, etc. This can be 
	 * used when we modify the structure inside but can keep or
	 * modifty the red chain, e.g., when flipping an edge, 
	 * adding an edge, etc. 
	 * @param pdcel
	 * @return PackDCEL
	 */
	public static void d_FillInside(PackDCEL pdcel) {
		boolean debug=false; // debug=true;
		// NOTE about debugging: Many debug routines depend on
		//      original vertex indices, which might be found in
		//      pdcel.newold.
		
		pdcel.triData=null;  // filled when needed for repacking
		
		// use 'HalfEdge.eutil' to keep track of edges hit; this
		//   should be used in initializing interior edges.
		for (int v=1;v<=pdcel.vertCount;v++) {
			pdcel.vertices[v].vutil=0;
			HalfEdge edge=pdcel.vertices[v].halfedge; 
			// DCELdebug.edgeFlowerUtils(pdcel,pdcel.vertices[17]);
			HalfEdge trace=edge;
			do {
				trace.eutil=0;
				trace.twin.eutil=0;
				trace=trace.prev.twin;
			} while(trace!=edge);
		}
		
		// zero out 'RedHEdge.redutil'
		RedHEdge rtrace=pdcel.redChain;
		if (rtrace!=null) { // not spherical case
			int safety=pdcel.edgeCount;
			do {
				safety--;
				rtrace.redutil=0;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain && safety>0);
			if (safety==0) {
				throw new DCELException("redChain doesn't seem to be closed");
			}
		}
		
		// Build list of edges as we encounter new faces: 
		//   convention is that each 'HalfEdge' in 'orderEdges' 
		//   is associated with its face (ie. the face on 
		//   its left, not yet created). 
		ArrayList<HalfEdge> orderEdges=new ArrayList<HalfEdge>(); 	
		int ordertick=0;
		
		// get non-red vertices connected to alpha first: use two lists 
		int[] vhits=new int[pdcel.vertCount+1];
		ArrayList<Vertex> currv=new ArrayList<Vertex>(0);
		ArrayList<Vertex> nxtv=new ArrayList<Vertex>(0);

		// put alpha in first and mark its edges
		orderEdges.add(pdcel.alpha);
		ordertick++;
		
		if (debug) // debug=true;
			DCELdebug.drawEdgeFace(pdcel,pdcel.alpha);
			
		HalfEdge tr=pdcel.alpha;
		do { 
			tr.eutil=1;
			if (!tr.origin.redFlag)
				nxtv.add(tr.origin);
			tr=tr.next;
		} while(tr!=pdcel.alpha);

		// now keep two lists to progress through rest of
		//   interior.
		while (nxtv.size()>0) {
			currv=nxtv;
			nxtv=new ArrayList<Vertex>(0);
			Iterator<Vertex> cit=currv.iterator();
			while (cit.hasNext()) {
				Vertex vert=cit.next();
//System.out.println("in currv, vert "+vert);				

				if (vhits[vert.vertIndx]!=0) {
					continue;
				}
				
				// DCELdebug.edgeFlowerUtils(pdcel,pdcel.vertices[17]);

				// rotate cclw to find eutil==1; should always exist
				HalfEdge startspoke=vert.halfedge;
				HalfEdge he=startspoke;
				int safety=10000;
				while(he.eutil==0 && safety>0) {
					he=he.prev.twin; // move cclw
					safety--;
				}
				if (safety==0)
					throw new CombException("startedge "+startspoke+"; didn't find eutil==1");
				startspoke=he.prev.twin;
				he=startspoke;
				
				// move cclw through to layout new faces
				do {
					if (he.eutil==0 && he.twin.eutil==1) {
						orderEdges.add(he);
						ordertick++;
						Vertex oppV=he.twin.origin;
						if (!oppV.redFlag && vhits[oppV.vertIndx]==0)
							nxtv.add(oppV);
						
						if (debug) { // debug=true;
							int oldv=pdcel.newOld.findW(he.origin.vertIndx);
							int oldw=pdcel.newOld.findW(he.twin.origin.vertIndx);
							System.out.println("  edge = ("+oldv+","+oldw+")");
//							DCELdebug.drawEdgeFace(pdcel,he);
						}
						
						// mark face edges
						tr=he;
						do { 
							tr.eutil=1;
							if (debug) {
								System.out.println(" set 'eutil' of "+tr);
							}
							tr=tr.next;
						} while(tr!=he);
					}
					he=he.prev.twin;
				} while(he!=startspoke);
				vhits[vert.vertIndx]=1;
			} // end of while on 'currv'
		} // end of while on 'nxtv'
		
		// Not a sphere? go around redChain for stragglers
		if (pdcel.redChain!=null) {
			RedHEdge startred=pdcel.redChain;
			
			// don't want to start with unplotted "blue" face
			while (startred.myEdge.eutil==0 && (startred.myEdge.next==startred.nextRed.myEdge
					|| startred.myEdge.prev==startred.prevRed.myEdge))
				startred=startred.nextRed;
			
			rtrace=startred;
			do {
				
				// if associated face already plotted, continue
				if (rtrace.myEdge.eutil==1) {
					rtrace=rtrace.nextRed;
					continue;
				}

				// otherwise, search cclw for edge with eutil==0, twin.eutil==1
				HalfEdge stopedge=rtrace.prevRed.myEdge.next;
				HalfEdge he=rtrace.myEdge;
				while (he!=stopedge && he.eutil==0)
					he=he.prev.twin;
				if (he.eutil==0)
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
						bt.eutil=1;
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
			Face newface=new Face(++ftick);
			hfe.face=newface;
			newface.edge=hfe;
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
		pdcel.alpha.origin.vutil=1;
		pdcel.alpha.twin.origin.vutil=1;
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
			if (!oppVert.redFlag) {
				if (oppVert.vutil==0) { // yes, add 'hfe' to 'tmpLayout'
					oppVert.vutil=1;
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
						if (oppVert.redFlag) {
							RedHEdge nre=nextRedEdge(sea.twin.next.next);
							if (nre.redutil==1) // already plotted?
								alreadyhit=true;
							nre.redutil=1; // in any case, will be plotted
						}
						else if (oppVert.vutil==1) {
							alreadyhit=true;
						}

						// put in 'tmpLayout'?
						if (!alreadyhit) {  
							oppVert.vutil=1;
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
		pdcel.pairLink=null;
		ArrayList<RedHEdge> bdryStarts=null;
		
		// not a sphere?
		if (pdcel.redChain!=null) {
			
			// zero-out 'mobIndx'
			RedHEdge nxtred=pdcel.redChain;
			rtrace=nxtred;
			do {
				rtrace.mobIndx=0;
				rtrace=rtrace.nextRed;
			} while(rtrace!=pdcel.redChain);
			
			// ======== Catalog side pairings, free sides, create ideal faces ========
			pdcel.sideStarts=new ArrayList<RedHEdge>();
			bdryStarts=new ArrayList<RedHEdge>();
			int sidecount=0; 
			int safety=1000;
			RedHEdge stopEdge=null;
			do {
				// see if this is unpasted redChain edge, hence a free side.
				if (nxtred.twinRed==null) {
					++sidecount; // index is non-zero
					
					// search upstream to find first edge
					rtrace=nxtred.prevRed;
					while (rtrace.twinRed==null && rtrace!=nxtred) {
						rtrace.myEdge.twin.face=null;
						rtrace=rtrace.prevRed;
					}

					
					// check if simply connected
					if (rtrace==nxtred) {
						bdryStarts.add(pdcel.redChain);
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
					bdryStarts.add(freeStart);
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
					nxtred=rtrace;
				}
				// else, get side pairing; move back, then forward, while twins match
				else {				
					int sideIndx=nxtred.twinRed.mobIndx; // should be positive
					rtrace=nxtred;

					// if not 0, then this is paired with earlier side
					if (sideIndx!=0) {
						pdcel.sideStarts.add(rtrace);
						do {
							rtrace.mobIndx=-sideIndx;
							rtrace=rtrace.nextRed;
						} while (rtrace.twinRed!=null && rtrace.twinRed.mobIndx==sideIndx);
						nxtred=rtrace;
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
						rtrace=nxtred;
						twintrace=rtrace.twinRed;
						while (rtrace.nextRed.twinRed==twintrace.prevRed && 
							twintrace.prevRed.twinRed==rtrace.nextRed) { 
							rtrace=rtrace.nextRed;
							twintrace=rtrace.twinRed;
							rtrace.mobIndx=sideIndx;
						}

						nxtred=rtrace.nextRed;
					}
				}
				safety--;
			} while (nxtred!=stopEdge && safety>0);
			
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
		pdcel.idealFaceCount=0;
		if (bdryStarts!=null && bdryStarts.size()>0) {
			ArrayList<Face> tmpIdealFaces=new ArrayList<Face>(0);
			Iterator<RedHEdge> rit=bdryStarts.iterator();
			int idtick=0;
			while (rit.hasNext()) {
				RedHEdge redge=rit.next();
				HalfEdge he=redge.myEdge.twin;
				if (he.face!=null) { // not in bdry
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
	 * Given a 'HalfEdge', check if origin is 'RedVertex'. If so,
	 * return the next clw 'RedHEdge' (possibly itself). If not,
	 * return null;
	 * @param edge HalfEdge
	 * @return RedHEdge or null 
	 */
	public static RedHEdge nextRedEdge(HalfEdge edge) {
		if (!edge.origin.redFlag)
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
	 * For edges of this face, set 'eutil' to 1 if it is zero.
	 * @param edge HalfEdge
	 */
	public static void markFaceUtils(HalfEdge edge) {
		HalfEdge he=edge;
		do {
			if (he.eutil==0)
				he.eutil=1;
			he=he.next;
		} while(he!=edge);
	}
	
	/**
	 * Find the component of interiors containing the given
	 * 'seed' and avoiding forbidden vertices from 'hlink'.
	 * Set up for call to 'markGenerations', so if 'seed' 
	 * is 0, try in order alpha, or nghb of alpha, or first 
	 * non-forbidden interior. ans[v] >0 if v is in the 
	 * connected component of 'seed'. ans[0] is count of
	 * verts in the component.
	 *   
	 * @param pdcel PackDCEL
	 * @param seed int, may be 0
	 * @param hlink HalfLink, may be null
	 * @return int[]
	 */
	public static int[] findComponent(PackDCEL pdcel,int seed,HalfLink hlink ) {
		int[] seedstop=new int[pdcel.vertCount+1];
		
		// mark bdry verts
		for (int v=1;v<=pdcel.vertCount;v++) 
			if (pdcel.vertices[v].isBdry())
				seedstop[v]=-1;
		
		// mark ends of hlink edges
		if (hlink!=null) {
			Iterator<HalfEdge> his=hlink.iterator();
			while (his.hasNext()) {
				HalfEdge he=his.next();
				seedstop[he.origin.vertIndx]=-1;
				seedstop[he.twin.origin.vertIndx]=-1;
			}
		}
		return RawDCEL.markGenerations(pdcel, seedstop);
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
	 * have non-zero 'eutil', i.e. are forbidden, bdry, or already touched
	 * @param edge
	 * @return boolean, false if some 'eutil' is zero
	 */
	public static boolean isVertDone(HalfEdge edge) {
		HalfEdge he=edge;
		do {
			if (he.eutil==0)
				return false;
			he=he.prev.twin; // cclw search
		} while(he!=edge);
		return true;
	}
	
	/** 
	 * Are v/w bdry and on same bdry component?
	 * @param v int
	 * @param w int
	 * @return boolean
	 */
	public static boolean onSameBdryComp(PackDCEL pdcel,int v,int w) {
		try {
			Vertex vert=pdcel.vertices[v];
			Vertex wert=pdcel.vertices[w];
			if (vert.bdryFlag==0 || wert.bdryFlag==0)
				return false;
			if (v==w)
				return true;
			HalfEdge vtwin=vert.halfedge.twin;
			HalfEdge wtwin=wert.halfedge.twin;
			HalfEdge he=vtwin;
			do {
				if (he==wtwin)
					return true;
				he=he.next;
			} while (he!=vtwin);
			  return false;
		  } catch(Exception ex) {
			  throw new CombException("Some comb. error with "+v+" or "+w);
		  }
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
		// set appropriate 'seed' if 'alpha' is a nghb of 'v'
		int seed=-1;
		int alp=pdcel.alpha.origin.vertIndx;
		if (v==alp || pdcel.findHalfEdge(new EdgeSimple(alp,v))!=null) {
			for (int k=1;k<=pdcel.vertCount;k++) {
				Vertex vert=pdcel.vertices[k];
				if (pdcel.vertIsBdry(vert)!=null)
					vert.vutil=-1; // bdry not eligible 
				else 
					pdcel.vertices[k].vutil=0;
			}
			// v and nghbs not eligible
			pdcel.vertices[v].vutil=-1;
			HalfEdge he=pdcel.vertices[v].halfedge;
			do {
				pdcel.vertices[he.next.origin.vertIndx].vutil=-1;
				he=he.prev.twin;
			} while (he!=pdcel.vertices[v].halfedge);
			
			// now choose first vertex with 'vutil'=0
			for (int k=1;(k<=pdcel.vertCount && seed<0);k++) { 
				if (pdcel.vertices[k].vutil==0) {
					seed=k;
				}
			}
			if (seed<0) 
				throw new CombException("puncture error: no interior 'seed' to avoid "+v);
		}

		// set prohibited edges in 'hlink', set baseEdge for extraction
		StringBuilder strbld=new StringBuilder(Integer.toString(v)+" ");
		HalfEdge baseEdge=pdcel.alpha;
		if (seed>0) { // have to change baseEdge?
			baseEdge=pdcel.vertices[seed].halfedge;
			strbld.append(" -v "+Integer.toString(seed));
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
	 * are ones which are never crossed in building the red chain;
	 * bdry edges are always included.
	 * 
	 * If there is an input list of vertices to be excised, 
	 * they should appear in  first vector of strings in 
	 * 'flags' without a preceding flag. Outer edges about 
	 * these will be included as forbidden.
	 * 
	 * Then check for flag segments:
	 * * Flags: -v {v}, for identifying seed: 'p.alpha' is reset.
	 * * Flag -e {u v...} is edge list, adds to any forbidden
	 *   already included.
	 * * flag -n {v}, non-keepers; any edges with both ends
	 *   non-keepers will be added as forbidden edges.
	 *   
	 * Note: difference between 'poison' (vertices to be excised)
	 * and 'non-keepers': the latter may remain in the boundary of 
	 * the excised DCEL structure.
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
		int[] eutil=new int[pdcel.edgeCount+1]; // mark chosen edges
		
		// read incoming data
		while (flags!=null && flags.size()>0) { 
			Vector<String> items=(Vector<String>)flags.remove(0);
			if (!StringUtil.isFlag(items.get(0))) { // no flag? must be poison vertices
				vlink=new NodeLink(p,items);
			}
			else {
				String str=(String)items.get(0);
				if (str.equals("-v")) { // given seed, reset 'alpha' and continue
					if (items.size()<2) 
						throw new ParserException("cookie crumbled: error in -v flag");
					pdcel.alpha=pdcel.vertices[Integer.parseInt((String)items.get(1))].halfedge;
					items.remove(1);
					items.remove(0);
				}
				else if (str.equals("-e")) { // specified poison edges
					if (items.size()<2) 
						throw new ParserException("cookie crumbled: no edges with -e flag");
					items.remove(0);
					EdgeLink elink=new EdgeLink(p,items);
					Iterator<EdgeSimple> eis=elink.iterator();
					while (eis.hasNext()) {
						HalfEdge he=pdcel.findHalfEdge(eis.next());
						if (he!=null) {
							if (eutil[he.edgeIndx]==0) {
								hlink.add(he);
								eutil[he.edgeIndx]=1;
								eutil[he.twin.edgeIndx]=1;
							}
						}
					}
				}
				else if (str.equals("-n")) { // non keepers
					if (items.size()<1) 
						throw new ParserException("cookie crumbled: nothing with -n flag");
					items.remove(0);
					NodeLink nonvs=new NodeLink(p,items);
					
					// set 'vutil' to zero
					for (int v=1;v<=pdcel.vertCount;v++) 
						pdcel.vertices[v].vutil=0;

					Iterator<Integer> nis=nonvs.iterator();
					while (nis.hasNext()) {
						int v=nis.next();
						pdcel.vertices[v].vutil=v;
					}
					
					// include edges between non-keepers
					int w;
					for (int v=1;v<=pdcel.vertCount;v++) {
						Vertex vert=pdcel.vertices[v];
						if (vert.vutil!=0) {
							int[] flower=vert.getFlower();
							for (int j=0;j<flower.length;j++) {
								if ((w=pdcel.vertices[flower[j]].vutil)>v) {
									HalfEdge he=pdcel.findHalfEdge(v,w);
									if (eutil[he.edgeIndx]==0) {
										hlink.add(he);
										eutil[he.edgeIndx]=1;
										eutil[he.twin.edgeIndx]=1;
									}
								}
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
		
		if (vlink.size()!=0) {
			hlink.separatingLinks(pdcel,vlink,pdcel.alpha.origin.vertIndx);
			// add any missing bdry edges
			for (int f=1;f<=pdcel.idealFaceCount;f++) {
				Face idealf=pdcel.idealFaces[f];
				HalfEdge he=idealf.edge;
				do {
					if (eutil[he.edgeIndx]==0) 
						hlink.add(he.twin);
					he=he.next;
				} while (he!=idealf.edge);
			}
		}
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
	 * Given 'pdc' (sometimes a clone of original dcel), reverse
	 * its orientation in place. We clone all the edges, establish 
	 * prev, next, twins, redchain, halfedges, etc., based on 
	 * edge indices. We keep faces and vertices, reverse the red 
	 * chain. Complete with 'd_FillInside' command. 
	 * Result remains in 'pdc' 
	 * @param pdc PackDcel
	 */
	public static void reorient(PackDCEL pdc) {
		HalfEdge[] edges=new HalfEdge[pdc.edgeCount+1];
		
		// create new edges
		for (int e=1;e<=pdc.edgeCount;e++) {
			edges[e]=pdc.edges[e].clone();
			edges[e].myRedEdge=null;
		}
		// reset edge pointers
		for (int e=1;e<=pdc.edgeCount;e++) {
			HalfEdge he=edges[e];
			HalfEdge orighe=pdc.edges[e];
			he.twin=edges[orighe.twin.edgeIndx];
			he.next=edges[orighe.twin.prev.twin.edgeIndx];
			he.prev=edges[orighe.twin.next.twin.edgeIndx];
			he.face=orighe.twin.face;
		}
		// reset face edge
		for (int f=1;f<=pdc.faceCount;f++)
			pdc.faces[f].edge=edges[pdc.faces[f].edge.edgeIndx];
		if (pdc.idealFaceCount>0)
			for (int j=1;j<=pdc.idealFaceCount;j++)
				pdc.idealFaces[j].edge=edges[pdc.idealFaces[j].edge.edgeIndx];
		// reset vert halfedges
		for (int v=1;v<=pdc.vertCount;v++) {
			Vertex vert=pdc.vertices[v];
			vert.halfedge=edges[vert.halfedge.edgeIndx];
		}
		// reset 'alpha' 'gamma'
		pdc.alpha=edges[pdc.alpha.prev.twin.edgeIndx];
		if (pdc.gamma!=null)
			pdc.gamma=edges[pdc.gamma.prev.twin.edgeIndx];
		// reverse redchain
		if (pdc.redChain!=null) {
			int rCount=0;
			// first index using 'redutil'
			RedHEdge rtrace=pdc.redChain;
			do {
				rtrace.redutil=++rCount;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdc.redChain);
			RedHEdge[] rededges=new RedHEdge[rCount+1];
			rtrace=pdc.redChain;
			do {
				rededges[rtrace.redutil]=rtrace.clone();
				rtrace=rtrace.nextRed;
			} while(rtrace!=pdc.redChain);
		
			// build reverse redchain
			RedHEdge newRedChain=rededges[1];
			for (int r=1;r<=rCount;r++) {
				rtrace=rededges[r];
				rtrace.nextRed=rededges[rtrace.prevRed.redutil];
				rtrace.prevRed=rededges[rtrace.nextRed.redutil];
				if (rtrace.twinRed!=null)
					rtrace.twinRed=rededges[rtrace.twinRed.redutil];
				rtrace.myEdge=edges[rtrace.myEdge.twin.edgeIndx];
				rtrace.myEdge.myRedEdge=rtrace;
			}
			pdc.redChain=newRedChain;
		}
	
		// wrap up
		pdc.edges=edges;
		pdc.triData=null;
		CombDCEL.d_FillInside(pdc);
	}
	  
	/**
     * Create an exact duplicate of this PackDCEL with all new objects,
     * PackData set to null and 'triData' is not copied.
     */
    public static PackDCEL cloneDCEL(PackDCEL pdc) {
    	PackDCEL pdcel=new PackDCEL();
    	pdcel.vertCount=pdc.vertCount;
    	pdcel.edgeCount=pdc.edgeCount;
    	pdcel.faceCount=pdc.faceCount;
    	pdcel.intFaceCount=pdc.intFaceCount;
    	pdcel.idealFaceCount=pdc.idealFaceCount;
    	
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
    	int[] red2edge=null; // e=myEdge index
    	if (redcount>0) {
    		red2edge=new int[redcount+1];
    		newRedEdges=new RedHEdge[redcount+1];
    		int rtick=0;
    		RedHEdge oldrtrace=pdc.redChain;
    		do {
    			newRedEdges[++rtick]=oldrtrace.clone();
    			// set index
    			newRedEdges[rtick].redutil=oldrtrace.redutil=rtick;
    			red2edge[rtick]=oldrtrace.myEdge.edgeIndx;
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
    	
    		// set 'myEdges'
    		RedHEdge rtrace=pdcel.redChain;
    		do {
    			int rdx=rtrace.redutil;
    			rtrace.myEdge=pdcel.edges[red2edge[rdx]];
    			rtrace.myEdge.myRedEdge=rtrace;
    			rtrace=rtrace.nextRed;
    		} while (rtrace!=pdcel.redChain);
    	}
    	
    	// clone other things
    	if (pdc.faceOrder!=null) {
    		pdcel.faceOrder=new GraphLink();
    		Iterator<EdgeSimple> fois=pdc.faceOrder.iterator();
    		while (fois.hasNext()) {
    			pdcel.faceOrder.add(fois.next().clone());
    		}
    	}
    	if (pdc.computeOrder!=null) {
    		pdcel.computeOrder=new GraphLink();
    		Iterator<EdgeSimple> cois=pdc.computeOrder.iterator();
    		while (cois.hasNext()) {
    			pdcel.computeOrder.add(cois.next().clone());
    		}
    	}
    	if (pdc.sideStarts!=null) {
    		Iterator<RedHEdge> ssis=pdc.sideStarts.iterator();
    		pdcel.sideStarts=new ArrayList<RedHEdge>();
    		while (ssis.hasNext()) {
    			pdcel.sideStarts.add(newRedEdges[ssis.next().redutil]);
    		}
    	}
    	
    	pdcel.newOld=null;
    	if (pdc.newOld!=null && pdc.newOld.size()>0) 
    		pdcel.newOld=pdc.newOld.clone();
    	
    	if (pdc.alpha!=null && pdc.alpha.edgeIndx>0)
    		pdcel.alpha=pdcel.edges[pdc.alpha.edgeIndx];
    	if (pdc.gamma!=null && pdc.gamma.edgeIndx>0)
    		pdcel.gamma=pdcel.edges[pdc.gamma.edgeIndx];
    	
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

    	pdcel.triData=null; // don't clone, as is generally temporary data
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

	/**
	 * The 'PreRedVertex' must have a closed flower, and we rotate so the
	 * first petal is 'redSpoke'. Further, if there is a "slit", 
	 * meaning a common direction with 'red/inSpoke' which are 
	 * not pasted, then the first slit's 'redSpoke' will be rotated
	 * to be the first petal. If there are no slits then return 0, 
	 * meaning this vertex, although on the 'redChain', must be interior.
	 * @return 'num', -1 on error, and 0 if there are no slits
	 */
	public static int rotateMe(PreRedVertex rV) {
		if (rV.redSpoke==null) {
			return -1;
		}
		int num=rV.redSpoke.length-1;
		boolean noslit=false;

		// find the first 'redSpoke'
		int firstRSj = -1;
		for (int j = 0; (j < num && firstRSj < 0); j++)
			if (rV.redSpoke[j] != null)
				firstRSj = j;
		// 
		int J = -1;
		for (int j = 0; (j < num && J < 0); j++) {
			if (rV.redSpoke[j] != null) {
				if (rV.inSpoke[j] == null || rV.inSpoke[j].twinRed != rV.redSpoke[j])
					J = j;
			}
		}
		// no slit found, so start at first 'redSpoke'
		if (J < 0) {
			noslit=true;
			J = firstRSj;
		}
		
		// interior?
		if (noslit) {
			rV.redSpoke[0]=rV.redSpoke[firstRSj]; // temporary to set 'helpedge'
			return 0;
		}

		// rotate everything by J
		RedHEdge[] tmpRedSpoke = new RedHEdge[num + 1];
		RedHEdge[] tmpInSpoke = new RedHEdge[num + 1];
		for (int j = 0; j < num; j++) {
			tmpRedSpoke[j] = rV.redSpoke[(j + J) % num];
			tmpInSpoke[j] = rV.inSpoke[(j + J) % num];
		}
		// close up
		tmpRedSpoke[num] = tmpRedSpoke[0];
		tmpInSpoke[num] = tmpInSpoke[0];

		// save
		rV.redSpoke = tmpRedSpoke;
		rV.inSpoke = tmpInSpoke;
		
		return num;
	}

	/**
	 * The 'PreRedVertex' is temporary; we use it to 
	 * gather entries in 'redSpoke', 'inSpoke', and we 
	 * 'rotateMe' if necessary to avoid wrapping problems. 
	 * In 'process' we then find "fans" of contiguous vertices 
	 * stretching cclw from a 'redSpoke' to 'inSpoke', possibly 
	 * with intermediate twin'ed in/out red edges. We 
	 * introduce a new 'Vertex' to go in the 'redChain' for each 
	 * such fan and also add new 'HalfEdge's to last 'inSpoke' 
	 * of each fan. Note that the first of the returned 
	 * 'Vertex's will replace the original, the others 
	 * will have their 'vertIndx's adjusted when the calling 
	 * routine catalogs vertices.
	 * @return ArrayList<RedVertex> of new 'RedVertex's
	 */
	public static ArrayList<Vertex> process(PreRedVertex prV) {
		boolean debug=false; // debug=true;
		HalfEdge he=null;
		prV.bdryFlag=1;
		ArrayList<Vertex> redList=new ArrayList<Vertex>();

		// rotate to get 'redSpoke' as first edge so we avoid worrying 
		//   about index wrapping around; finish if it's interior 
		//   (i.e. all red edges are paired and pasted) 
		if (prV.closed && CombDCEL.rotateMe(prV)==0) { 
			Vertex newV = new Vertex(prV.vertIndx);
			newV.redFlag=true;
			newV.bdryFlag=0;
			newV.halfedge=prV.redSpoke[0].myEdge;

			// fix 'origin's cclw and return, don't fill 'spokes'
			he=newV.halfedge;
			he.origin=newV;
			do {
				he = he.prev.twin;
				he.origin=newV;
			} while (he != newV.halfedge);
			redList.add(newV);
			return redList;
		}

		// redList.get(0).spokes[0].origin.vertIndx;

		// look for successive cclw 'fans' between 'redSpoke' 
		//    and 'inSpoke'
		for (int j = 0; j < prV.num; j++) {
			if (prV.redSpoke[j] != null) { // should get hit when j=0
				int v = j;
				int w = j;
				for (int k = j + 1; k <= prV.num; k++) {
					if (prV.inSpoke[k] != null
							&& (prV.redSpoke[k] == null || 
							(prV.redSpoke[k].twinRed != prV.inSpoke[k]))) {
						w = k;
						j = w - 1; // when we continue search, this will repeat the last direction
						k = prV.num + 1; // kick out of 'for'
					}
				}
				if (v == w) { // didn't find a match to form the fan
					CirclePack.cpb.errMsg("problem matching in/red spokes");
					throw new DCELException("'didn't find 'inSpoke' match with 'redSpoke'");
				}
				
				// The 'spokes' now form a fan about a new 'RedVertex', from
				// 'redSpoke[v]' cclw to 'inSpoke[w]'. If 'redSpoke[w]' exists
				// (and so was not pasted), then replace the last spoke in this
				// fan by a new twin for the 'inSpoke' (prev/next readjusted later)
				Vertex newV = new Vertex(prV.vertIndx);
				newV.redFlag=true;
				newV.halfedge=prV.redSpoke[v].myEdge;
				prV.redSpoke[v].myEdge.origin=newV;
				newV.spokes = new HalfEdge[w-v + 1];
				newV.bdryFlag = 1;
				if (prV.redSpoke[w]!=null) {
					// replace last of this fan
					HalfEdge new_w = new HalfEdge();
					new_w.face = new Face();
					new_w.face.edge = new_w;
					new_w.origin = newV;
					prV.inSpoke[w].myEdge.twin = new_w;
					new_w.twin = prV.inSpoke[w].myEdge;
					newV.spokes[w-v]=new_w;
				}
				
				he = prV.redSpoke[v].myEdge; //	DCELdebug.triVerts(he);
				he.origin=newV;
				newV.spokes[0]=he;
				int safety = 100;
				int tick = 0;
				do {
					he = he.prev.twin;
					he.origin=newV;
					newV.spokes[++tick] = he;
					safety--;
				} while (he != prV.inSpoke[w].myEdge.twin && safety > 0);
				if (safety == 0)
					throw new DCELException("infinite look, 'redSpoke' for <" + prV.redSpoke[v].myEdge.origin.vertIndx + " "
							+ prV.redSpoke[v].myEdge.twin.origin.vertIndx + ">");

				// The 'spokes' now form a fan about a new 'RedVertex', from
				// 'redSpoke[v]' cclw to 'inSpoke[w]'. If 'redSpoke[w]' exists
				// (it can't be pasted), then replace the last spoke in this
				// fan by a new twin for the 'inSpoke' (prev/next readjusted later)
				if (prV.redSpoke[w]!=null) {
					// replace last of this fan
					HalfEdge new_w = new HalfEdge();
					new_w.face = new Face();
					new_w.face.edge = new_w;
					new_w.origin = newV;
					prV.inSpoke[w].myEdge.twin = new_w;
					new_w.twin = prV.inSpoke[w].myEdge;
					newV.spokes[w-v]=new_w;
				}
				
				// add this to our array
				redList.add(newV);
			}

		}
		if (redList.size() == 0) {
			throw new DCELException("error: there are no 'bdryFan's");
		}
		return redList;
	}

	/** 
	   * Adjoin a boundary segment of pdc2 to a boundary segment
	   * of pdc1, starting with 'v2' in 'pdc2' to vert v1 in 'pdc1'
	   * and proceeding n edges CLOCKWISE (negative direction) on
	   * bdry of 'pdc1', counterclockwise on bdry of 'pdc2'. Note
	   * that dcel's are clones, so error should leave original data
	   * in tact. Note: If n<0, identify full bdry components 
	   * (they must be the same length).
	   *  
	   * If not self-adjoin, then some 'Vertex's of 'pdc2' will be
	   * abandoned. Mark them by setting 'halfedge' to null.
	   * 
	   * Call 'wrapAdjoin' to wrap up before returning; calling 
	   * routine only needs to 'attachDCEL', but may have some 
	   * updating because of new indexing.
	   * 
	   * @param pdc1 PackDCEL (should be a clone)
	   * @param pdc2 PackDCEL (should be a clone)
	   * @param v1 int
	   * @param v2 int
	   * @param n int, number of edges (or negative)
	   * @return modified 'PackDCEL' or null or 'CombException' on error
	   */
	  public static PackDCEL d_adjoin(PackDCEL pdc1,PackDCEL pdc2,int v1,int v2, int n) {
		  
		  // store original vert indices in 'vutil'
		  for (int v=1;v<=pdc1.vertCount;v++) 
			  pdc1.vertices[v].vutil=v;
		  if (pdc2!=pdc1)
			  for (int v=1;v<=pdc2.vertCount;v++) 
				  pdc2.vertices[v].vutil=v;
		  
		  // 'he1/2' to be identified: clw from 'v1' cclw from 'v2'
		  HalfEdge he1=pdc1.vertices[v1].halfedge.twin.next.twin;
		  HalfEdge he2=pdc2.vertices[v2].halfedge;

		  // get edge counts
		  int n1=he1.twin.getCycleCount();
		  int n2=he2.twin.getCycleCount();

		  // check: pdc1==pdc2 and v1/v2 on same bdry component?
		  boolean samecomp=false;
		  if (pdc1==pdc2) {
			  samecomp=CombDCEL.onSameBdryComp(pdc1,v1,v2);
		  }
		  
		  // proper data?
		  if (n<=0) { // paste full bdrys
			  if (n1!=n2) 
				  throw new CombException("'n'<=0 but 'n1!=n2'");
			  else if(samecomp)
				  throw new CombException("Can't self-paste full bdry comp");
			  else
				  n=n1;
		  }
		  if (!((n1>n && n2>n) || (n1==n2 && n1<=n) ||
				  (n1==n && n2>(n+1)) || (n2==n && n1>(n+1)))) {
			  throw new CombException("edge counts are incompatible");
		  }
		  
		  // for debugging, reindex 'pdc2' can use old index in 'vutil'
		  //    so we can connect with data upon return 
		  if (pdc2!=pdc1) {
			  for (int v=1;v<=pdc2.vertCount;v++) {
				  Vertex vert=pdc2.vertices[v];
				  vert.vertIndx +=pdc1.vertCount;
			  }
		  }
		  
		  // ============ identify =====================
		  
		  // self identification: same packing, same bdry component
		  if (pdc1==pdc2 && samecomp) {
			  try {

				  // zip from common vertex?
				  if (he1.twin.origin==he2.origin) {
					  HalfEdge nxtedge=he1.twin;
					  for (int j=1;j<=n;j++) {
						  CombDCEL.zipEdge(pdc1,nxtedge.origin);
						  nxtedge=nxtedge.next;
					  }
					  return CombDCEL.wrapAdjoin(pdc1, pdc2);
				  }
					  
				  // need to check suitability
				  if (he2.twin.prev.prev==he1.twin)
					  throw new CombException("self-adjoin: given vertices too close");
				  HalfEdge he=he1.twin;
				  HalfEdge lastedge=he;
				  int indx2=0;
				  for (int j=1;(j<n1 && indx2==0);j++) {
					  lastedge=he=he.next;
					  if (he==he2.twin)
						  indx2=j;
				  }
					  
				  // not enough edges
				  if ((indx2+1)<2*n || indx2==2*n)
					  throw new CombException("self-adjoin: not enough edges");
					  
				  // can we zip the other direction from common vert?
				  if ((indx2+1)==2*n) {
					  HalfEdge nxtedge=lastedge; // start at other ennd
					  for (int j=1;j<=n;j++) {
						  CombDCEL.zipEdge(pdc1,nxtedge.origin);
						  nxtedge=nxtedge.next;
					  }
					  return CombDCEL.wrapAdjoin(pdc1, pdc2);
				  }
			  } catch (CombException cex) {
				  throw new CombException("same component: "+cex.getMessage());
			  }
		  }

		  // reaching here, we are ready to paste 'he1' to 'he2', whether 
		  //    packings are the same or different. Then we can complete
		  //    by repeating 'zipEdge'
		  
		  // must fix pointers to vertices first
		  Vertex vert1=he1.next.origin;
		  Vertex vert2=he1.origin;
		  Vertex oldvert1=he2.origin;
		  Vertex oldvert2=he2.twin.origin;
		  HalfEdge he=he2;
		  do {
			  he.origin=vert1;
			  he=he.prev.twin; // cclw
		  } while (he!=he2);
		  oldvert1.halfedge=null; // abandoned
		  oldvert1.vutil=vert1.vertIndx;
		  he=he2.twin;
		  do {
			  he.origin=vert2;
			  he=he.twin.next; // clw
		  } while(he!=he2.twin);
		  oldvert2.halfedge=null; // abandoned
		  oldvert2.vutil=vert2.vertIndx;
		  vert2.halfedge=he2.twin.prev.twin;

		  // fix pointers for orphaned he1.twin, he2.twin 
		  he1.twin.prev.next=he2.twin.next;
		  he2.twin.next.prev=he1.twin.prev;
		  he1.twin.next.prev=he2.twin.prev;
		  he2.twin.prev.next=he1.twin.next;
		
		  // fix h1/2 as twins
		  he2.twin=he1;
		  he1.twin=he2;
		  RedHEdge red1=he1.myRedEdge;
		  RedHEdge red2=he2.myRedEdge;
		  red1.twinRed=red2;
		  red2.twinRed=red1;

		  // now zip the rest   // pdc1.vertices[37].getFlower();
		  HalfEdge nxtedge=red1.prevRed.myEdge.twin;
		  for (int j=2;j<=n;j++) {
			  HalfEdge hold=nxtedge.next;
			  CombDCEL.zipEdge(pdc1,nxtedge.origin);
			  nxtedge=hold;
		  }
		  
		  return CombDCEL.wrapAdjoin(pdc1, pdc2);
	  }
	  
	  /**
	   * Special helper routine to wrap up 'd_adjoin' 
	   * when ready to return results, which are in 
	   * 'pdc1'. This re-indexes 'vertices' and forms
	   * new 'vertices'. In processing, orphaned 
	   * vertices are indicated by 'halfedge'=null. 
	   * Build 'newOld' to connect new and old vertex 
	   * indices. Note that if pdc2!=pdc1, then no pdc1 
	   * vertices are orphaned, so pdc2 vertices 
	   * numbering have been adjusted to start with 
	   * pdc1.vertCount+1. 'vutil' still holds the
	   * original index for both pdc1 and pdc2.
	   * @param pdc1 PackDCEL
	   * @param pdc2 PackDCEL, may be same as pdc1
	   * @return same PackDCEL
	   */
	  public static PackDCEL wrapAdjoin(PackDCEL pdc1,PackDCEL pdc2) {
		  pdc1.newOld=new VertexMap();
		  ArrayList<Vertex> v_array=new ArrayList<Vertex>();
		  int vtick=0;
		  
		  // start indexing with surviving pdc1 vertices;
		  for (int v=1;v<=pdc1.vertCount;v++) {
			  Vertex vert=pdc1.vertices[v];
			  if (vert.halfedge!=null) { 
				  ++vtick;
				  v_array.add(vert);
				  // if pdc2==pdc1, should have vtick=v=vert.vutil
				  pdc1.newOld.add(new EdgeSimple(vtick,vert.vutil));
//System.out.println(" add "+new EdgeSimple(vtick,vert.vutil));					  
			  }
		  }
			 
		  // new indices for any orphaned verts
		  for (int v=1;v<=pdc1.vertCount;v++) {
			  Vertex vert=pdc1.vertices[v];
			  if (vert.halfedge==null) { // 'vutil', replacement index
				  int new_indx=pdc1.newOld.findV(vert.vutil);
				  pdc1.newOld.add(new EdgeSimple(new_indx,vert.vertIndx));
//System.out.println(" add for orphaned "+new EdgeSimple(new_indx,vert.vertIndx));					  			  }
			  }
		  }

		  // not self-adjoin? index surviving pdc2 verts
		  if (pdc2!=pdc1) {
			  vtick=pdc1.vertCount; // start here
			  for (int v=1;v<=pdc2.vertCount;v++) {
				  Vertex vert=pdc2.vertices[v];
				  if (vert.halfedge!=null) {
					  ++vtick;
					  vert.vutil=vtick;
					  pdc1.newOld.add(new EdgeSimple(vtick,vert.vutil));
					  v_array.add(vert);
				  }
			  }
			  
			  // new indices for any orphaned verts
			  for (int v=1;v<=pdc2.vertCount;v++) {
				  Vertex vert=pdc2.vertices[v];
				  if (vert.halfedge==null) { // 'vutil', replacement index
					  int rep_indx=pdc2.vertices[vert.vutil].vertIndx;
					  int newindx=pdc2.vertices[rep_indx].vutil;
					  pdc1.newOld.add(new EdgeSimple(newindx,vert.vertIndx));
				  }
			  }
		  }
		  
		  // create new 'vertices' with new 'vertIndx's
		  pdc1.vertCount=v_array.size(); // this should equal 'vtick'
		  pdc1.vertices=new Vertex[pdc1.vertCount+1];
		  Iterator<Vertex> vis=v_array.iterator();
		  vtick=0;
		  while (vis.hasNext()) {
			  Vertex vert=vis.next();
			  vert.vertIndx=++vtick; // 'vutil' still has old index
			  pdc1.vertices[vtick]=vert;
		  }
		  
		  return pdc1;
	  }

	  /**
	   * Flip an interior edge. In a triangulation, an interior edge is
	   * shared by two faces. To "flip" the edge means to remove it and
	   * replace it with the other diagonal in the union of those faces. The
	   * number of faces, edges, and vertices is not changed, but the calling
	   * routine must update combinatorics. 'redProblem' is instantiated by
	   * the calling routine; if true on return, then the flipped edge was
	   * in the red chain, requiring extra work in the update. Faces are
	   * reused.
	   * @param pdcel PackDCEL
	   * @param hedge HalfEdge
	   * @param redProblem Boolean, return true if red Chain is disrupted
	   * @return int 1 on success
	   */
	  public static int flipEdge(PackDCEL pdcel,HalfEdge hedge,Boolean redProblem) {
		  redProblem=false;
		  
		  // if 'hedge' is bdry or faces not triangles, error
		  if (pdcel.isBdryEdge(hedge) || 
				  hedge.next.next.next!=hedge ||
				  hedge.twin.next.next.next!=hedge.twin)
			  return 0;
		  if (hedge.myRedEdge!=null || hedge.twin.myRedEdge!=null) { // in redchain
			  redProblem=true;
		  }
		  Face leftf=hedge.face;
		  leftf.edge=hedge;
		  Face rightf=hedge.twin.face;
		  rightf.edge=hedge.twin;
		  Vertex leftv=hedge.next.next.origin;
		  Vertex rightv=hedge.twin.next.next.origin;
				
		  // save some info for later
		  HalfEdge hn=hedge.next;
		  HalfEdge hp=hedge.prev;
		  HalfEdge twn=hedge.twin.next;
		  HalfEdge twp=hedge.twin.prev;
				
		  // have to make sure ends don't have old 'halfedge's
		  if (hedge.origin.halfedge==hedge)
					hedge.origin.halfedge=hedge.prev.twin; // cclw spoke
		  if (hedge.twin.origin.halfedge==hedge.twin)
					hedge.twin.origin.halfedge=hedge.twin.prev.twin; // cclw spoke
				
		  // fix he and its twin
		  hedge.origin=rightv;
		  hedge.twin.origin=leftv;
		  hedge.next=hp;
		  hedge.prev=twn;
		  hedge.twin.next=twp;
		  hedge.twin.prev=hn;
				
		  hn.next=hedge.twin;
		  hn.prev=twp;
		  hp.next=twn;
		  hp.prev=hedge;
		  twn.prev=hp;
		  twn.next=hedge;
		  twp.next=hn;
		  twp.prev=hedge.twin;
				
		  hedge.next.next.face=hedge.next.face=leftf;
		  hedge.twin.next.next.face=hedge.twin.next.face=rightf;

		  return 1;
	  }
	  
	  /**
	   * Given 'pdcel' must be a topological torus with
	   * an initial 'redChain'. Typically, the red chain
	   * has 3 side pairings; this routine finds a new 
	   * red chain with just two side pairings. The calling 
	   * routine must do 'd_FillInside', repack, layout, etc. 
	   * (see former 'ProjStruct.torus4layout')
	   * @param pdcel PackDCEL
	   * @return RedHEdge
	   * @throws CombException
	   */
	  public static RedHEdge torus4Sides(PackDCEL pdcel) {

			// put red chain in 'HalfLink'
			HalfLink redpath = new HalfLink();
			RedHEdge rtrace = pdcel.redChain;
			do {
				redpath.add(rtrace.myEdge);
				rtrace = rtrace.nextRed;
			} while (rtrace != pdcel.redChain);

			RedHEdge newChain = null;
			try {
				// get first generator based on red chain
				HalfLink generator1 = CombDCEL.shortCut(pdcel, redpath);

				HalfLink generator2 = CombDCEL.shortCut(pdcel, generator1);

				// create all the red edges, link along generators
				Iterator<HalfEdge> g1 = generator1.iterator();
				while (g1.hasNext()) {
					HalfEdge he = g1.next();
					he.myRedEdge = new RedHEdge(he);
					he.twin.myRedEdge = new RedHEdge(he.twin);
					he.myRedEdge.twinRed = he.twin.myRedEdge;
					he.twin.myRedEdge.twinRed = he.myRedEdge;
				}
				int g1size = generator1.size();
				for (int j = 1; j < g1size - 1; j++) {
					HalfEdge he1 = generator1.get(j - 1);
					HalfEdge he2 = generator1.get(j);
					HalfEdge he3 = generator1.get(j + 1);
					he2.myRedEdge.prevRed = he1.myRedEdge;
					he1.myRedEdge.nextRed = he2.myRedEdge;
					he3.myRedEdge.prevRed = he2.myRedEdge;
					he2.myRedEdge.nextRed = he3.myRedEdge;
				}

				Iterator<HalfEdge> g2 = generator2.iterator();
				while (g2.hasNext()) {
					HalfEdge he = g2.next();
					he.myRedEdge = new RedHEdge(he);
					he.twin.myRedEdge = new RedHEdge(he.twin);
					he.myRedEdge.twinRed = he.twin.myRedEdge;
					he.twin.myRedEdge.twinRed = he.myRedEdge;
				}
				int g2size = generator2.size();
				for (int j = 1; j < g2size - 1; j++) {
					HalfEdge he1 = generator2.get(j - 1);
					HalfEdge he2 = generator2.get(j);
					HalfEdge he3 = generator2.get(j + 1);
					he2.myRedEdge.prevRed = he1.myRedEdge;
					he1.myRedEdge.nextRed = he2.myRedEdge;
					he3.myRedEdge.prevRed = he2.myRedEdge;
					he2.myRedEdge.nextRed = he3.myRedEdge;
				}

				// figure out linkage: start with 'generator2'
				int corner = generator2.get(0).origin.vertIndx;

				// rotate 'generator1' to start at 'corner'
				int safety = generator1.size();
				while (generator1.get(0).origin.vertIndx != corner && safety > 0) {
					HalfEdge he = generator1.remove(0);
					generator1.add(he);
					safety--;
				}
				if (safety == 0)
					throw new CombException("'generator1' doesn't " + "contain vertex " + corner);

				// now link ends: order is
				// g2 --> g1 --> -g2 --> -g1 --> g2
				HalfEdge g2b = generator2.get(0);
				HalfEdge g2e = generator2.getLast();
				HalfEdge g1b = generator1.get(0);
				HalfEdge g1e = generator1.getLast();

				newChain = g2b.myRedEdge;

				g2b.myRedEdge.prevRed = g1b.twin.myRedEdge;
				g1b.twin.myRedEdge.nextRed = g2b.myRedEdge;

				g2e.myRedEdge.nextRed = g1b.myRedEdge;
				g1b.myRedEdge.prevRed = g2e.myRedEdge;

				g1e.myRedEdge.nextRed = g2e.twin.myRedEdge;
				g2e.twin.myRedEdge.prevRed = g1e.myRedEdge;

				g2b.twin.myRedEdge.nextRed = g1e.twin.myRedEdge;
				g1e.twin.myRedEdge.prevRed = g2b.twin.myRedEdge;

			} catch (Exception ex) {
				throw new CombException("'torus4Sides' went wrong; " + ex.getMessage());
			}

			return newChain;
	  }
	  
	  /**
	   * Return the shortest closed edge path starting and 
	   * ending at a vertex of the input 'path' and otherwise
	   * not intersecting 'path'. If 'path' separates the 
	   * complex, then throw an exception. Normally 'path' 
	   * either closed or has endpoints on the boundary.
	   * We work by counting generations on the left and
	   * on the right of 'path' --- in particular, edges
	   * in 'path' must be interior. 
	   * @param pdcel PackDCEL
	   * @param path HalfLink
	   * @return PackDCEL or null on error
	   */
	  public static HalfLink shortCut(PackDCEL pdcel,
			  HalfLink path) {

		  // no bdry edges allowed
		  Iterator<HalfEdge> pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfEdge he=pis.next();
			  if (pdcel.isBdryEdge(he))
				  throw new DCELException("'ShortCut' error: edge "+he+" is a bdry edge");
			  he.origin.vutil=0;
			  he.twin.origin.vutil=0;
		  }
		  
		  // 1. find 'shortest' cut, by counting generations
		  //    + from the left and - from the right for whole
		  //    path; probably not closed.
		  // 2. Else, cycle through vertices v of 'path',
		  //    counting generations from v, get shortest
		  //    starting/ending at v.
		  // 4. Use best cut among those.
		  
		  // =========== 1 =================
		  HalfLink firstLink=getShortPath(pdcel,path,path);
		  
		  // closed?
		  if (firstLink.get(0).origin.vertIndx==
				  firstLink.getLast().twin.origin.vertIndx)
			  return firstLink;

		  // =============== 2 ====================
		  HalfLink bestpath=null; // shortest closed
		  int bestlength=0;
		  HalfLink nexttry=null;
		  pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfLink seed=new HalfLink();
			  seed.add(pis.next());
			  nexttry=getShortPath(pdcel,path,seed);
			  if (nexttry.get(0).origin.vertIndx!=
					  nexttry.getLast().twin.origin.vertIndx)
				  throw new DCELException("this path should always be closed");
			  if (bestlength>0 && nexttry.size()<bestlength) {
				  bestpath=nexttry;
				  bestlength=bestpath.size();
			  }
		  }
		  return bestpath;
	  }
	  
	  /**
	   * Return 'HalfLink' path which is among the shortest 
	   * combinatorially which starts and ends at one of 
	   * vertices in 'seed'. Make a small shift of ends
	   * if it will close the path without lengthening it.
	   * @param pdcel PackDCEL
	   * @param path HalfLink
	   * @param seed HalfLink
	   * @return HalfLink
	   */
	  public static HalfLink getShortPath(PackDCEL pdcel,
			  HalfLink path,HalfLink seed) {
		  HalfLink link1=new HalfLink();
		  int bound=pdcel.vertCount+1;
		  
		  // set all 'vutil' to bound+1 = "untouched"
		  for (int v=1;v<=pdcel.vertCount;v++) {
			  pdcel.vertices[v].vutil=bound;
		  }
		  
		  // two-list method to count generations (+/-)
		  NodeLink currv=new NodeLink();
		  NodeLink nextv=new NodeLink();

		  // set 'vutil' 0 on 'path'
		  Iterator<HalfEdge> pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfEdge he=pis.next();
			  he.origin.vutil=0;
			  he.twin.origin.vutil=0;
		  }
		  
		  // set 'vutil' +/- on left/right of 'seed'
		  boolean lhit=false;
		  boolean rhit=false;
		  Iterator<HalfEdge> sis=seed.iterator();
		  while (sis.hasNext()) {
			  HalfEdge he=sis.next();
			  int vl=he.next.next.origin.vertIndx;
			  int vr=he.twin.next.next.origin.vertIndx;
			  if (pdcel.vertices[vl].vertIndx==0) {
				  pdcel.vertices[vl].vutil=1;
				  nextv.add(vl);
				  lhit=true;
			  }
			  if (pdcel.vertices[vr].vertIndx==0) {
				  pdcel.vertices[vr].vutil=-1;
				  nextv.add(vr);
				  rhit=true;
			  }
		  }
		  if (!lhit || !rhit) {
			  throw new CombException("failed to get started with + or - vertices");
		  }
		  
		  int safety=2*bound;
		  int hitvert=0; // first hit (has both + and - nghb)
		  while (nextv.size()>0 && hitvert==0 && safety>0) {
			  currv=nextv;
			  nextv=new NodeLink();
			  Iterator<Integer> cis=currv.iterator();
			  while (cis.hasNext() && hitvert==0) {
				  Vertex vert=pdcel.vertices[cis.next()];
				  int vutil=vert.vutil;
//				  if (vutil!=0) // not needed?
//					  continue;
				  int[] flower=vert.getFlower();
				  for (int j=0;j<flower.length;j++) {
					  Vertex wert=pdcel.vertices[flower[j]];
					  int hit=0;
					  if (wert.vutil>=bound) {
						  if (vutil<0) { // right side hit
							  hit=1;
							  wert.vutil=vutil-1;
							  nextv.add(wert.vertIndx);
						  }
						  else if (vutil>0) { // left side hit
							  if (hit==1) { // simultaneous hit
								  hitvert=vert.vertIndx;
								  nextv=null;
							  }
							  else {
								  wert.vutil=vutil+1;
								  nextv.add(wert.vertIndx);
							  }
						  }
					  }
				  }
			  } // done with while on currv
			  safety--;
		  } // done with while on nextv
		  
		  if (safety<=0 || hitvert==0) {
			  throw new DCELException("hum...? no collision "+
					  "or overran safety in 'ShortCut'");
		  }
		  
		  // +/- generations first collide at 'hitvert'
		  // for 'HalfLink' from left side to right side 
		  if (hitvert!=0) {
			  
			  // find +/- petals
			  Vertex hitVert=pdcel.vertices[hitvert];
			  int vneg=0;
			  int vpos=0;
			  int[] flower=hitVert.getFlower();
			  for (int j=0;(j<flower.length && (vneg==0 || vpos==0));j++) {
				  int val=pdcel.vertices[flower[j]].vutil;
				  if (vneg==0 && val<0) 
					  vneg=flower[j];
				  if (vpos==0 && val>0 && val<bound)
					  vpos=flower[j];
			  }
			  if (vneg==0 || vpos==0) {
				  throw new DCELException("not collision at "+hitvert+"??");
			  }
		  
			  // walk back through increasingly smaller + generations
			  link1.add(pdcel.findHalfEdge(new EdgeSimple(hitvert,vpos)));
			  while (pdcel.vertices[vpos].vutil!=0) {
				  HalfLink eflower=pdcel.vertices[vpos].getEdgeFlower();
				  int myindx=pdcel.vertices[vpos].vutil;
				  HalfEdge hhedge=null;
				  Iterator<HalfEdge> eis=eflower.iterator();
				  while (eis.hasNext() && hhedge==null) {
					  HalfEdge he=eis.next();
					  if (he.twin.origin.vutil==myindx-1)
						  hhedge=he;
				  }
				  if (hhedge==null) {
					  throw new DCELException("lost + generational link");
				  }
				  link1.add(hhedge);
				  vpos=hhedge.twin.origin.vertIndx;
			  }
			  link1=HalfLink.reverseElements(link1);
			  link1=HalfLink.reverseLink(link1);
			  
			  // now walk through increasingly less - generations
			  while (pdcel.vertices[vneg].vutil!=0) {
				  HalfLink eflower=pdcel.vertices[vneg].getEdgeFlower();
				  int myindx=pdcel.vertices[vneg].vutil;
				  HalfEdge hhedge=null;
				  Iterator<HalfEdge> eis=eflower.iterator();
				  while (eis.hasNext() && hhedge==null) {
					  HalfEdge he=eis.next();
					  if (he.twin.origin.vutil==(myindx+1))
						  hhedge=he;
				  }
				  if (hhedge==null) {
					  throw new DCELException("lost - generational link");
				  }
				  link1.add(hhedge);
				  vneg=hhedge.twin.origin.vertIndx;
			  }
		  }
		  
		  // closed already?
		  HalfEdge edgefirst=link1.get(0);
		  HalfEdge edgelast=link1.getLast();
		  int v=edgefirst.origin.vertIndx;
		  int w=edgelast.twin.origin.vertIndx;
		  if (v==w)
			  return link1;
		  
		  // simple adjustment?
		  if (edgefirst.next.next.origin.vertIndx==w) {
			  link1.add(0,edgefirst.next.twin);
			  return link1;
		  }
		  if (edgelast.prev.origin.vertIndx==w) {
			  link1.add(0,edgefirst.next.next);
			  return link1;
		  }
		  int lastIndx=link1.size()-1;
		  if (edgelast.twin.prev.origin.vertIndx==v) {
			  link1.add(lastIndx,edgelast.twin.next);
			  return link1;
		  }
		  if (edgelast.next.origin.vertIndx==v) {
			  link1.add(lastIndx,edgelast.twin.prev.twin);
			  return link1;
		  }

		  return link1;
	  }
	  
	  /**
	   * Create a new PackDCEL seed with n petals
	   * @param n int
	   * @return PackDCEL
	   */
	  public static PackDCEL seed_raw(int n) {
		  if (n<3 || n>1000) 
			  throw new ParserException("'seed' is limited to degree between 3 and 1000");
		  int[][] bouquet=new int[n+2][];
		  bouquet[1]=new int[n+1];
		  for (int j=0;j<n;j++)
			  bouquet[1][j]=j+2;
		  bouquet[1][n]=bouquet[1][0]; // close up
		  for (int k=0;k<n;k++) {
			  bouquet[k+2]=new int [3];
			  bouquet[k+2][0]=(k+1)%n+2;
			  bouquet[k+2][1]=1;
			  bouquet[k+2][2]=((k+n)-1)%n+2;
		  }
		  return getRawDCEL(bouquet,1);
	  }

	/**
	 * Modify 'pdcel' by zipping up the bdry edges from 'vert'.
	 * This means the upstream vert on the boundary is consolidated
	 * with the downstream vert and so a vertex may be lost.
	 * The redchain should remain in tact, though it will be 
	 * discarded if result is a sphere.
	 * 
	 * May abandon one vertex and two edges; for verts, set
	 * 'Vertex.halfedge' to null and 'vutil' to new vertIndx.
	 * Calling routine is responsible for keeping track of 
	 * this and for completing updates of 'pdcel'.
	 * 
	 * @param pdcel PackDCEL
	 * @param vert Vertex
	 * @return 1 on success, 0 on failure
	 */
	public static int zipEdge(PackDCEL pdcel,Vertex vert) {
		if (vert.bdryFlag==0) // nothing to zip
			return 1;
		if (!vert.redFlag)
			throw new CombException("Vertex "+vert.vertIndx+" is not on the red chain");
	
		// edges to be kept; these become twins
		HalfEdge outedge=vert.halfedge;
		HalfEdge inedge=outedge.twin.next.twin;
		
		RedHEdge redout=outedge.myRedEdge;
		RedHEdge redin=inedge.myRedEdge;
		
		// 3-edge bdry component? would leave a loop
		if (outedge.twin.prev.origin==inedge.origin) 
			throw new CombException("pasting would leave loop");
		
		// 2-edge bdry (single slit)? close up, adjust red
		if (outedge.twin.origin==inedge.origin) {
			// set twins
			outedge.twin=inedge;
			inedge.twin=outedge;
			redin.twinRed=redout;
			redout.twinRed=redin;
			
			// ends become interior
			vert.bdryFlag=0;
			inedge.origin.bdryFlag=0;
			
			// red chain just these two? this will be a sphere.
			if (redout.prevRed==redin && redout.nextRed==redin) {
				// identify
				outedge.myRedEdge=null;
				inedge.myRedEdge=null;
				vert.redFlag=false;
				redin.myEdge.origin.redFlag=false;
				pdcel.redChain=null; // toss the redchain
				return 1;
			}
			
			// else, the bdry component may be a bubble in red chain
			if (redout.prevRed!=redin && redout.nextRed!=redin) {
				return 1;
			}
			
			// else, red chain is shrunk in one or other direction
			RedHEdge upred; // set in case of further red shrinkage
			RedHEdge downred;
			if (redout.prevRed==redin) { // remove 'vert' from red chain
				if (pdcel.redChain==redout || pdcel.redChain==redin)
					pdcel.redChain=redout.nextRed;
				// identify
				outedge.myRedEdge=null;
				inedge.myRedEdge=null;
				upred=redin.prevRed;
				downred=redout.nextRed;
				upred.nextRed=downred;
				downred.prevRed=upred;
				vert.redFlag=false;
			}
			else { // remove other end from red chain
				if (pdcel.redChain==redout || pdcel.redChain==redin)
					pdcel.redChain=redin.nextRed;
				// identify
				outedge.myRedEdge=null;
				inedge.myRedEdge=null;
				upred=redout.prevRed;
				downred=redin.nextRed;
				upred.nextRed=downred;
				downred.prevRed=upred;
				inedge.origin.redFlag=false;
			}
				
			// there may be more shrinkage of the redchain
			while (upred.twinRed==downred) { // collapse an edge
				downred.myEdge.origin.redFlag=false;
				upred.myEdge.myRedEdge=null;
				downred.myEdge.myRedEdge=null;
				if (upred.prevRed==downred) { // reached end? must be sphere
					upred.myEdge.origin.redFlag=false;
					pdcel.redChain=null;
					return 1;
				}
				upred=upred.prevRed;
				downred=downred.nextRed;
				upred.nextRed=downred;
				downred.prevRed=upred;
			}
			return 1;
		}
		
		// 4-edge bdry component?
		if (outedge.twin.prev.origin==inedge.twin.next.twin.origin) {
			HalfEdge pre_edge=inedge.twin.next.twin;
			HalfEdge post_edge=outedge.twin.prev.twin;
			RedHEdge pre_red=pre_edge.myRedEdge;
			RedHEdge post_red=post_edge.myRedEdge;
			Vertex oppVert=pre_edge.origin;
			Vertex leftVert=outedge.twin.origin;
			Vertex rightVert=inedge.origin;
			
			// orphan 'rightVert'
			HalfEdge firstspoke=rightVert.halfedge;
			HalfEdge he=firstspoke;
			do {
				he.origin=leftVert;
				he=he.prev.twin; // cclw
			} while (he!=rightVert.halfedge);
			rightVert.halfedge=null;
			rightVert.vutil=leftVert.vertIndx;
			
			// vertices are interior
			vert.bdryFlag=0;
			leftVert.bdryFlag=0;
			oppVert.bdryFlag=0;
			
			// twin things
			inedge.twin=outedge;
			outedge.twin=inedge;
			post_edge.twin=pre_edge;
			pre_edge.twin=post_edge;
			redin.twinRed=redout;
			redout.twinRed=redin;
			pre_red.twinRed=post_red;
			post_red.twinRed=pre_red;
			
			// check for sphere
			if (pre_red.nextRed==redin && redin.nextRed==redout &&
					redout.nextRed==post_red && post_red.nextRed==pre_red) {
				inedge.myRedEdge=null;
				outedge.myRedEdge=null;
				pre_edge.myRedEdge=null;
				post_edge.myRedEdge=null;
				pdcel.redChain=null;
				return 1;
			}
			
			// check for red shrinkage at 'vert' and/or 'oppVert'
			//   and subsequent possible shrinkage
			RedHEdge upred=redout.prevRed; 
			RedHEdge downred=redin.nextRed;
			if (redout.prevRed==redin) { // remove 'vert' from red chain
				if (pdcel.redChain==redout || pdcel.redChain==redin)
					pdcel.redChain=redout.nextRed;
				// identify
				outedge.myRedEdge=null;
				inedge.myRedEdge=null;
				upred=redin.prevRed;
				downred=redout.nextRed;
				upred.nextRed=downred;
				downred.prevRed=upred;
				vert.redFlag=false;
			}
			if (post_red.nextRed==pre_red) { // remove 'oppVert' from red chain
				if (pdcel.redChain==post_red || pdcel.redChain==pre_red)
					pdcel.redChain=post_red.prevRed;
				// identify
				post_edge.myRedEdge=null;
				pre_edge.myRedEdge=null;
				upred=post_red.prevRed;
				downred=pre_red.nextRed;
				upred.nextRed=downred;
				downred.prevRed=upred;
				oppVert.redFlag=false;
			}
				
			// if shrinkage at both 'vert' and 'oppVert',
			//   then there may be more shrinkage
			if (!oppVert.redFlag && !vert.redFlag) {
				while (upred.twinRed==downred) { // collapse an edge
					downred.myEdge.origin.redFlag=false;
					upred.myEdge.myRedEdge=null;
					downred.myEdge.myRedEdge=null;
					if (upred.prevRed==downred) { // reached end? must be sphere
						upred.myEdge.origin.redFlag=false;
						pdcel.redChain=null;
						return 1;
					}
					upred=upred.prevRed;
					downred=downred.nextRed;
					upred.nextRed=downred;
					downred.prevRed=upred;
				}
			}
			return 1;
		}
		
		// else zip up just first edge
		Vertex savevert=redin.myEdge.origin;
		HalfEdge downstream=redout.nextRed.myEdge.twin;
		HalfEdge upstream=redin.prevRed.myEdge.twin;
		downstream.next=upstream;
		upstream.prev=downstream;
		savevert.halfedge=downstream.twin;
	
		// reset origin for spokes of vertex being abandoned
		HalfEdge he=redout.nextRed.myEdge;
		Vertex xvert=he.origin;
		do {
			he.origin=savevert;
			he=he.prev.twin; // cclw
		} while (he!=redout.nextRed.myEdge);
		
		// abandon vertex 'xvert'
		xvert.halfedge=null; 
		xvert.vutil=savevert.vertIndx;
	
		// identify
		outedge.twin=inedge;
		inedge.twin=outedge;
	
		// we may shrink red chain
		if (redout.prevRed==redin) {
			if (pdcel.redChain==redout || pdcel.redChain==redin)
				pdcel.redChain=redout.nextRed;
			redin.prevRed.nextRed=redout.nextRed;
			redout.nextRed.prevRed=redin.prevRed;
			redin.myEdge.myRedEdge=null;
			redout.myEdge.myRedEdge=null;
			vert.redFlag=false;
		}
		
		// else redout/in remain in redchain and become red twins
		else {
			redout.twinRed=redin;
			redin.twinRed=redout;
		}
		
		return 1;
	}
	  
}

/**
 * Inner class: temporary object to hold 'redSpoke' and 'inSpoke' data
 * until appropriate 'Vertex's are created and processed.
 * @author kstephe2, 8/2020
 */
class PreRedVertex extends Vertex {
	public RedHEdge[] redSpoke;    // outgoing 'RedHEdge'
	public RedHEdge[] inSpoke;     // incoming 'RedHEdge'
	public int num;
	boolean closed;         // if true, then original flower was closed
	
	// Constructor
	public PreRedVertex(int v) {
		super(v);
		closed=false;
		num=-1;
	}
}
