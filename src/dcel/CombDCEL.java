package dcel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.DataException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import panels.PathManager;
import util.ColorUtil;
import util.PathUtil;
import util.StringUtil;

/**
 * Static combinatorial routines for working with DCEL structures.
 * 
 * The "*_raw" methods typically work just with combinatorics
 * (no rad/cents, little or no dependence on PackData parent).
 * The calling routines do further processing, generally calling
 * 'fillInside' and then 'attachDCEL' to the packing. If red 
 * chain cannot be modified, then 'redChain' is set to null and
 * calling routine would run 'redchain_by_edge'. See 'fixDCEL'.
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
		try {
			CombDCEL.redchain_by_edge(pdcel,hlink,alphaEdge,false);
			CombDCEL.fillInside(pdcel);
			
			// reset bdry 'aim's
			if (pdcel.redChain!=null) {
				RedEdge rtrace=pdcel.redChain;
				do {
					Vertex vert=rtrace.myEdge.origin;
					if (vert.bdryFlag==1)
						vert.aim=-1.0;
					rtrace=rtrace.nextRed;
				} while(rtrace!=pdcel.redChain);
			}
			
			// DCELdebug.printRedChain(pdcel.redChain);
		} catch (Exception ex) {
			throw new DCELException(ex.getMessage());
		}
		return pdcel;
	}

	/**
	 * Given a bouquet of combinatoric data alone, create a
	 * DCEL structure with 'vertices' and 'edges' only.
	 * Bouquet satisfies usual conventions: counterclockwise order, 
	 * indexed contiguously from 1, bdry/interior flower 
	 * open/closed, resp. Calling routine builds red chain, does
	 * 'fillInside' and 'attachDCEL'.
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
		return getRawDCEL(p.getBouquet(),p.getAlpha());
	}
	
	/**
	 * Given a bouquet of combinatoric data alone, create a minimal
	 * DCEL structure, including 'vertices' and 'edges' only.
	 * Bouquet satisfies usual conventions: counterclockwise order, 
	 * indexed contiguously from 1, bdry/interior flower 
	 * open/closed, resp. 'alpha' helpedge should start at interior  
	 * @param bouquet int[][]
	 * @param alphaIndx int, suggested 'alpha' vertex, may be <=0
	 * @return packDCEL
	 */
	public static PackDCEL getRawDCEL(int[][] bouquet,int alphaIndx) {
		PackDCEL pdcel=new PackDCEL();
		int vertcount = bouquet.length - 1; // 0th entry is empty
		int size=((int)((vertcount-1)/1000))*1000+1000;
		Vertex[] vertices = new Vertex[size + 1]; // extra space
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
		
		// Designate bdry edges by creating 'face' with index -1. 
		// Note that other 'face's entries are not set, since we 
		//   do not catalog the faces, but we need to identify
		//   bdry edges in 'redchain_by_edge'.
		for (int v=1;v<=vertcount;v++) {
			Vertex vert=vertices[v];
			if (vert.bdryFlag==1) {
				HalfEdge he=heArrays[v][0];
				he.twin.face=new DcelFace(-1);
				he.twin.next.face=new DcelFace(-1);
			}
		}
		
		// designate 'alpha' halfedge; use suggested 'alphaIndx', if interior
		HalfEdge alpha=null;
		if (alphaIndx<=0 || (alphaIndx<=vertcount && bdryverts[alphaIndx]!=0))
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
	 * Modify given 'pdcel' based on red chain formed about 
	 * given 'alphaEdge' and not crossing any edge in 'hlink'.
	 * Face and edge indexing are no longer reliable. After
	 * building the red chain, continue processing with
	 * 'finishRedChain', which organizes vertex indexing and
	 * builds 'oldNew'. See more details there.
	 * After 'finishRedChain', the calling routine should call
	 * 'fillInside' to complete processing.  
	 * The 'prune' flag true means every bdry vertex must have an 
	 * interior neighbor; this enters just one spot in the code.
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink
	 * @param alphaEdge HalfEdge (if null, revert to 'pdcel.alpha')
	 * @param prune boolean, true, bdry with interor nghbs
	 * @return int, vertCount
	 */
	public static int redchain_by_edge(PackDCEL pdcel,
			HalfLink hlink,HalfEdge alphaEdge,boolean prune) {
		
		// debug? Try to draw things on existing packing
		//    debugPack=CPBase.packings[0];
		boolean debug=false; // debug=true;
		PackData debugPack=pdcel.p; // 
		boolean click=false; // help debug
		int vertcount=pdcel.vertCount;

		// reset 'alpha' to given edge, but avoid 'hlink'
		if (alphaEdge==null)
			alphaEdge=pdcel.alpha;
		if (alphaEdge==null)
			pdcel.setAlpha(1,NodeLink.incident(hlink),false);
		// if 'alphaEdge' is not null, check forbidden
		else {
			int alp=alphaEdge.origin.vertIndx;
			pdcel.setAlpha(alp,NodeLink.incident(hlink),false);
		}

		// ============= mark verts/edges ==============
		
		// undo 'redFlag's, null 'redChain', set 'eutil'/'vstat':
		//   eutil -1 for forbidden edges/twins
		//   vstat: -1; at least one forbidden edge or bdry
		//          +1; interior, in comp of interiors with 'alpha'
		//          +2; touches a +1 (converted from -1)  
		//          -2; later disqualified
		int[] vstat=new int[vertcount+1];
 		for (int k=1;k<=vertcount;k++) {
			Vertex vtx=pdcel.vertices[k];
			vtx.redFlag=false;
			// 'eutil's identify forbidden/bdry edges
			HalfEdge edge=vtx.halfedge;
			HalfEdge he=edge;
			int safety=1010;
			do {
				safety--;
				he.myRedEdge=null; // toss old red edge pointers
				he.eutil=0;
				// set 'eutil' -1 for bdry edges
				if (he.isBdry()) {
					he.eutil=-1;
					vstat[he.origin.vertIndx]=-1;
					vstat[he.twin.origin.vertIndx]=-1;
				}
				he=he.prev.twin; // cclw
			} while (he!=edge && safety>0);
			if (safety==0)
				throw new CombException("'safety' valve breached, edge "+edge);
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
		
		// 'vstat' = 1 if interior connected comp with alpha 
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
				int[] petals=pdcel.vertices[v].getPetals(); // open list
				for (int j=0;j<petals.length;j++) {
					if (vstat[petals[j]]==0) {
						vstat[petals[j]]=1;
						nxv.add(petals[j]);
					}
					else if (vstat[petals[j]]==-1)
						vstat[petals[j]]=2; // next to component
				}
			}
		}
		
		// ============== start redchain using alpha ================
		HalfEdge he=pdcel.alpha;
		pdcel.redChain=new RedEdge(he);
		RedEdge rtrace=pdcel.redChain;
		do {
			he=he.next;
			pdcel.redChain.nextRed=new RedEdge(he);
			pdcel.redChain.nextRed.prevRed=pdcel.redChain;
			pdcel.redChain=pdcel.redChain.nextRed;
		} while(he.next!=pdcel.alpha);
		// close up
		pdcel.redChain.nextRed=rtrace;
		rtrace.prevRed=pdcel.redChain;
		pdcel.redChain=pdcel.redChain.nextRed;
		
		if (debug) { // debug=true; DCELdebug.printRedChain(pdcel.redChain);
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
		RedEdge currRed=null;
		boolean hit = true; // true if we added a face or collapsed an edge
		boolean redisDone=false; // totally done?
		int redN=0;
		while (hit && pdcel.redChain!=null && !redisDone) {
			hit = false; // debug=true;

			// current count of red chain as safety stop mechanism
			redN=0;
			RedEdge re=pdcel.redChain;
			do {
				redN++;
				re=re.nextRed;
			} while(re!=pdcel.redChain);

			// look at 'redN'+1 successive 'RedEdge's 
			for (int N=0;(N<=redN && !redisDone);N++) {
				currRed = pdcel.redChain;
				pdcel.redChain = currRed.nextRed; // set up pointer for next pass

				// check for degeneracy: triangulation of sphere
				if (pdcel.redChain.nextRed.nextRed==pdcel.redChain) {
					pdcel.redChain = null;
					return pdcel.vertCount;
				}

				// ****************** main work *****************

				// processing the current red edge. 
				// working on v; u,v,w successive verts cclw 
				// along redchain 
				int v = currRed.myEdge.origin.vertIndx;
				
//				DCELdebug.printRedChain(pdcel.redChain);
				
				if (debug && click) { // debug=true;
					System.out.println("next v: "+v);
					DCELdebug.drawTmpRedChain(debugPack,currRed);
				}
				
				// shrink backtrack?
				HalfEdge upspoke = currRed.prevRed.myEdge.twin;
				HalfEdge downspoke = currRed.myEdge;
				if (upspoke==downspoke && vstat[v]==1 && upspoke.eutil!=-1) {
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
					//    Check fan of edges outside current red segment 
					//    to see if any are red/forbidden. If not, can 
					//    close up. Otherwise, following code tries to 
					//    add one cclw face.
					boolean canclose=false;
					RedEdge redge=null;
					HalfEdge spktrace=upspoke;
					if (vstat[v]==1) {
						redge=isMyEdge(pdcel.redChain,spktrace);
						while (spktrace!=downspoke && redge==null && 
								spktrace.eutil>=0) {
							spktrace=spktrace.prev.twin; // cclw
							redge=isMyEdge(
									pdcel.redChain,spktrace);
						}
						if (redge==currRed && redge.myEdge.eutil>=0) // yes, can close up
							canclose=true;
					}
						
					// now we can close up or we're ready to fall through
					RedEdge cclw=currRed.prevRed.prevRed;
					if (canclose) { // yes, enclose v
						spktrace=upspoke;
						while (spktrace!=downspoke) { // add a new link
							cclw.nextRed=new RedEdge(spktrace.next);
							cclw.nextRed.prevRed=cclw;
							
/* debugging							
 DCELdebug.edge2face(spktrace);
 DCELdebug.edgeConsistency(pdcel,spktrace).toString();
 System.out.println(DCELdebug.vertConsistency(pdcel,115).toString());
*/ 
							
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
					
					// else, if 'upspoke' and 'upspoke.next' are not red,
					//   might be able to add one cclw face about v.
					else if (isMyEdge(pdcel.redChain,upspoke)==null &&
							isMyEdge(pdcel.redChain,upspoke.next)==null) {
						boolean OK=false;
						
						// criteria for cclw face about v:
						
						// 'prune' true, but one or the other end is interior
						if (prune && 
								(vstat[upspoke.origin.vertIndx]==1 || 
								vstat[upspoke.twin.origin.vertIndx]==1)) {
							OK=true;
						}
						
						// not 'prune' and one or other end is int or has 
						//   int nghb (and not disqualified, see below)
						if (!OK && !prune && upspoke.eutil!=-1 &&
								(vstat[upspoke.origin.vertIndx]>=1 || 
								vstat[upspoke.twin.origin.vertIndx]>=1)) {
							OK=true;
						}
						
						// we add cclw face, bump out redchain
						if (OK) {
							RedEdge red1=new RedEdge(upspoke.next);
							RedEdge red2=new RedEdge(upspoke.prev);
							cclw.nextRed=red1;
						
							if (debug) {
								EdgeSimple es=new EdgeSimple(upspoke.origin.vertIndx,
										upspoke.twin.origin.vertIndx);
								DCELdebug.drawEdgeFace(debugPack, es);
							}
							
							red1.prevRed=cclw;
							red1.nextRed=red2;
							red2.prevRed=red1;
							red2.nextRed=currRed;
							currRed.prevRed=red2;
							markFaceUtils(upspoke);
							hit=true;
							
							// need change inn vstat for opposite vert?
							//   if vstat[opp_v]=2 might have v
							int opp_v=red2.myEdge.origin.vertIndx;
							if (red2.myEdge.twin==currRed.myEdge)
								vstat[opp_v]=-2; 
						}
					}
				} // done with this v;
			} // done on this cycle through redchain
			
			if (debug) { // debug=true;
				DCELdebug.printRedChain(pdcel.redChain);
			 // DCELdebug.redConsistency(pdcel); DCELdebug.printRedChain(pdcel.redChain);
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
							return pdcel.vertCount;
						}
						
						pdcel.redChain=rtrace.prevRed;
						
					}
					RedEdge rhold=rtrace.prevRed;
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
		
		
		return finishRedChain(pdcel, pdcel.redChain);
	}

	/**
	 * This completes 'redchain_by_edge' processing. Moreover,
	 * sometimes we wish to prescribe a red chain (as with 
	 * 1-tori), and this processes a proposed red chain.
	 * CAUTION: Be sure to run 'wipeRedChain', tossing old
	 * 'RedEdge's, reset 'Vertex.redFlag's, also check 
	 * 'HalfEdge.eutil'. (if 'eutil' is negative, then we
	 * don't allow that edge to be red-twinned.)
	 * Here preRedVertices are created and processed (to form new
	 * vertices, if necessary), surviving and new vertices are
	 * identified, 'vertCount' and 'vertices' are reset.
	 * Vertex count typically changes, but I've tried to 
	 * save indexing as much as possible. In 'pdcel.oldNew'
	 * we have pairs {v,w} only if w is a new index, e.g.,
	 * a changed index for original v or index for a new vertex
	 * originating from original v.
	 * @param pdcel PackDCEL
	 * @param redchain RedEdge
	 * @return int, new vertCount
	 */
	public static int finishRedChain(PackDCEL pdcel,
			RedEdge redchain) {
		int vertcount=pdcel.vertCount; 
		boolean debug=false; // debug=true;
		// DCELdebug.printRedChain(pdcel.redChain);
		
		// ============ set 'myRedEdge' pointers ================
		
		// ensure 'myRedEdge's are set and consistent
		RedEdge nxtre=redchain;
		do {
			nxtre.myEdge.setRedEdge(nxtre);
			nxtre=nxtre.nextRed;
			
		} while (nxtre!=redchain);
		
		// ============ identify orig vert status in 'vstat' ========
		// vstat[v] = -1 if v is on redchain
		// vstat[v] = 1 if v is interior (on left side of 'redchain')
		// vstat[v] = 2 converted from -1 if next to interior
		// vstat[v] = 3 (converted -1 so all original verts are >=0)
		
		// redchain vertices
		int[] vstat=new int[vertcount+1];
		RedEdge rtrace=redchain;
		do {
			vstat[rtrace.myEdge.origin.vertIndx]=-1;
			rtrace=rtrace.nextRed;
		} while (rtrace!=redchain);
		
		// interior means to the left of redchain; start marking
		//   immediate left
		NodeLink currv=new NodeLink();
		NodeLink nextv=new NodeLink();
		rtrace=redchain;
		do {
			
			if (debug) // debug=true;
				System.out.println(" myEdge "+rtrace.myEdge+"; next "+
						rtrace.myEdge.next+"; next "+rtrace.myEdge.next.next);
			
			int v=rtrace.myEdge.next.next.origin.vertIndx;
			if (vstat[v]==0) { // not on redchain itself
				vstat[v]=1;
				nextv.add(v);
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redchain); // DCELdebug.printRedChain(redchain);
		
		// very rarely, 'nextv' could be empty. The put alpha in it
		if (nextv==null || nextv.size()==0) {
			nextv.add(pdcel.alpha.origin.vertIndx);
			vstat[pdcel.alpha.origin.vertIndx]=1;
		}

		// use two-list procedure to mark all interiors as 1.
		//   watch for boundary verts, which would indicate error
		//   in proposed red chain.
		while (nextv!=null && nextv.size()>0) {
			currv=nextv;
			nextv=new NodeLink();
			Iterator<Integer> cis=currv.iterator();
			while (cis.hasNext()) {
				int v=cis.next();
				if (vstat[v]==1) {
					HalfEdge he=pdcel.vertices[v].halfedge;
					do {
						int w=he.next.origin.vertIndx;
						// error if this is bdry vertex
						if (vstat[w]==0 && pdcel.vertIsBdry(he.next.origin)!=null)
							throw new CombException(
								"'finishRedChain' encountered a bdry vertex "+
									"not in the red chain.");

						if (vstat[w]==0) {
							nextv.add(w);
							vstat[w]=1;
						}
						else if (vstat[w]==-1) // red next to interior?
							vstat[w]=2;
						he=he.prev.twin; // cclw
					} while (he!=pdcel.vertices[v].halfedge);
				}
			} // end of while for 'currv'
		} // end of while for 'nextv' // DCELdebug.printRedChain(redlist);

		// ============ identify red twins ==========================
		
		// If an edge and twin are both red, then we decide
		//   whether these should be red twins. 
		// Also, mark all redChain vertices as done. 
		// The 'redChain' is not changed by this or subsequent operations,
		//   though it may gain new red vertices.
		// Set 'myRedEdge's

		nxtre=redchain;
		do {
			if (debug) { // debug=true;
				System.out.println("nxtre="+nxtre.myEdge);
			}
			if (nxtre.twinRed==null) {
				// does twin edge also red and not forbidden?
				HalfEdge ctwin=nxtre.myEdge.twin;
				RedEdge crossRed=ctwin.getRedEdge();
				if (debug) {
					System.out.println("twinRed is null: twin eutil="+
							ctwin.eutil);
				}
				if (crossRed!=null && ctwin.eutil>=0) {
					if (debug) {
						System.out.println("  crossRed="+crossRed.myEdge);
					}
					nxtre.twinRed=crossRed;
					crossRed.twinRed=nxtre;
				}
				if (nxtre.twinRed==null) { // must be bdry
					nxtre.myEdge.twin.face=new DcelFace(-1);
					nxtre.myEdge.twin.face.edge=nxtre.myEdge.twin;
				}
			}
			nxtre=nxtre.nextRed;
		} while (nxtre!=redchain);

		// ========= create and swap out 'PreRedVertex's =============
		
		rtrace=redchain;
		do {
			int v=rtrace.myEdge.origin.vertIndx;
			// check if already converted
			if (!(pdcel.vertices[v] instanceof PreRedVertex)) {
				PreRedVertex redV=new PreRedVertex(v); // use parent's index
				redV.halfedge=pdcel.vertices[v].halfedge;
				redV.num=pdcel.vertices[v].getNum();
				redV.bdryFlag=pdcel.vertices[v].bdryFlag;
				redV.halfedge.origin=redV;
				if (pdcel.vertices[v].bdryFlag==0)
					redV.closed=true;
				else 
					redV.closed=false;
				redV.redSpoke=new RedEdge[redV.num+1];
				redV.inSpoke=new RedEdge[redV.num+1];
				rtrace.myEdge.origin=redV;
				redV.cloneData(pdcel.vertices[v]); // copy cent/rad/aim etc.
				pdcel.vertices[v]=redV;
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redchain); // DCELdebug.printRedChain(redchain);

		// record 'redSpoke', 'inSpoke' hits 
		rtrace=redchain;
		do {
			int v=rtrace.myEdge.origin.vertIndx;
			int w=rtrace.myEdge.twin.origin.vertIndx;
			PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];
			PreRedVertex rW=(PreRedVertex)pdcel.vertices[w];
			int j=-1;
			int[] petals=pdcel.vertices[v].getPetals(); // open flower
			for (int k=0;(k<=rV.num && j<0);k++) { 
				if (petals[k]==w) {
					j=k;
					rV.redSpoke[j]=rtrace;
				}
			}
			j=-1;
			petals=pdcel.vertices[w].getPetals();
			for (int k=0;(k<=rW.num && j<0);k++) {
				if (petals[k]==v) {
					j=k;
					rW.inSpoke[j]=rtrace;
				}
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redchain);

		// =========== process to get 'RedVertex's =============
		
		// The 'redChain' of 'RedEdge's has not changed, but some of
		//   the 'Vertex's it passes through will be new as we
		//   process the 'PreRedVertex's.
		// Pass through the redChain. When you encounter a 'PreRedVertex', 
		//   then it is processed (after rotating, if necessary); 
		//   this entry in 'pdcel.vertices' converts to a 'Vertex' with
		//   redFlag set and new 'Vertex's may be introduced elsewhere 
		//   in the redChain.
		ArrayList<Vertex> addedVertices=new ArrayList<Vertex>(); // new vertices
		rtrace=redchain;
		do {
			int v=Math.abs(rtrace.myEdge.origin.vertIndx);
			
			// if not processed, then it's siblings are not created yet either
			if (pdcel.vertices[v] instanceof PreRedVertex) { 
				PreRedVertex rV=(PreRedVertex)pdcel.vertices[v];
				
				if (debug) { // debug=true;
					System.out.println("'process' "+rV);
				}

				// process: convert to 'Vertex's with 'redFlag's set;
				//    may create new verts and insert in redchain.
				//    indices set to negative of parent indices.
				ArrayList<Vertex> redAdded=process(rV);
					
				// first of the new vertex replaces the original
				Vertex newV=redAdded.get(0);
				pdcel.vertices[v]=newV;

				// any remaining are added to 'newVertices'.
				// Identified as new by negative 'vertIndx'.
				int sz=redAdded.size();
				if (sz>1) 
					for (int j=1;j<sz;j++) { 
						Vertex nv=redAdded.get(j);
						nv.vertIndx=-nv.vertIndx;  // Note: minus parent's index
						addedVertices.add(redAdded.get(j));
					}
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redchain);
		debug=false;		
		
		// ========== set bdry next/prev =============
		// redChain vertices that were interior got no new edges;
		// others got a new twin for the 'inSpoke' of their fan
		rtrace=redchain;
		do {
			Vertex rvert=rtrace.myEdge.origin;
			if (rvert.bdryFlag==1 && rvert.spokes!=null) {
				try {
					int num=rvert.spokes.length-1;
					rvert.spokes[0].twin.next=rvert.spokes[num];
					rvert.spokes[num].prev=rvert.spokes[0].twin;
				} catch(Exception ex) {
					throw new DCELException("vertex "+rvert.vertIndx+
							" should have 'spokes' entries");
				}
				rvert.spokes=null;
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redchain);
		
		if (debug) { // debug=true;
			DCELdebug.RedOriginProblem(redchain);
			rtrace=redchain;
			int safety=vertcount;
			do {
				DCELdebug.vertFaces(rtrace.myEdge.origin);
				rtrace=rtrace.nextRed;
				safety--;
			} while (rtrace!=redchain && safety>0);
		}
		
		// ============= last job: clean up 'pdcel.vertices' =====
		
		// We may have lost some vertices and/or added vertices.
		// Try to keep indices unchanged for as many original 
		// vertices as possible, so we first put added vertices
		// into open slots in the original index set and after that, 
		// append the rest at the end. If there remain open slots 
		// in to original index set, we do successive shifts to 
		// keep indices contiguous.
		
		// search red chain for original vertices not interior or
		//    next to interior; set vstat=3.
		rtrace=redchain;
		do {
			int v=rtrace.myEdge.origin.vertIndx;
			if (v>0 && vstat[v]<=0) // w/o interior nghb
				vstat[v]=3;
			rtrace=rtrace.nextRed;
		} while(rtrace!=redchain);

		// first, get counts: max possible and actual
		int maxCount=vertcount; // max possible
		if (addedVertices!=null) {
			maxCount+=addedVertices.size();
		}
		int totalCount=0; // total (original and added) 
		// vertices picked up during processing.
		for (int k=1;k<=vertcount;k++) { // original
			if (vstat[k]>0)
				totalCount++;
		}
		if (addedVertices!=null) // new
			totalCount+=addedVertices.size();

		// accounting: 
		int[] vindices=new int[vertcount+1]; // new indices for originals
		Vertex[] allSlots=new Vertex[maxCount+1]; // if !=0, slot is filled

		// store existing vertices that survived in their own slots
		for (int v=1;v<=vertcount;v++) {
			if (vstat[v]>0) {
				allSlots[v]=pdcel.vertices[v];
				vindices[v]=v;
			}
		}
		
		// Finish by putting added verts into successive emtpy slots
		int slotptr=1;
		Iterator<Vertex> avis=addedVertices.iterator();
		while (avis.hasNext()) {
			Vertex vert=avis.next();
			// put in next open
			while (allSlots[slotptr]!=null && slotptr<maxCount)
				slotptr++;
			allSlots[slotptr]=vert; 
		}
		
		// 'allSlots' now has all vertices (original and new), 
		// but may have some empty slots (intermingled with slots 
		// of original vertices only). For contiguity, repeatedly 
		// fill successive still-open slots: shift following slots 
		// down to fill in. 'vindices' keeps track of new indices 
		// for original verts.
		slotptr=1;
		while (slotptr<maxCount && allSlots[slotptr]!=null)
			slotptr++; // slotptr should point to first open slot
		
		boolean shift=true;
		while (slotptr<maxCount && shift) {
			shift=false;
			for (int j=slotptr+1;j<=maxCount;j++) {
				Vertex svert=allSlots[j];
				if (svert!=null) { // 'vertIndx's should be positive
					vindices[svert.vertIndx]--;
					shift=true;
				}
				allSlots[j-1]=allSlots[j];
			}
			while (shift && slotptr<maxCount && allSlots[slotptr]!=null)
				slotptr++; // next open slot
		} 
		
		// Note: at this point, all non-empty slots are contiguous, 
		//   holes filled in as much as possible, empty slots filled in.
        int size=((int)((totalCount-1)/1000))*1000+1000;
        if (size<pdcel.vertices.length)
        	size=pdcel.vertices.length;
		Vertex[] newVertices=new Vertex[size+1];
		for (int j=1;j<=totalCount;j++) { 
			newVertices[j]=allSlots[j];
		}
		
		// Now we must set the new indices in 'newVertices'.
		//   However, we want to take care of 'oldNew', so do
		//   the added vertices first before changing their 
		//   orig vert indices.
		VertexMap oldnew=new VertexMap();
		for (int j=1;j<=totalCount;j++) {
			Vertex vert=newVertices[j];
			int oldIndx = Math.abs(vert.vertIndx); // +/- original parent index
			if (oldIndx!=j)
				oldnew.add(new EdgeSimple(oldIndx,j)); // add only if changed
			vert.vertIndx=j;
		}

		pdcel.vertCount=totalCount;
		pdcel.vertices=newVertices;
		pdcel.oldNew=oldnew; // Note: old is original; original may still
							 // exist, but may have new index.		
		pdcel.redChain=redchain;

		// debugging: // stopblue=true;		
		boolean stopblue=false; 
		if (!stopblue) { 
			// try to minimize blue faces; 
			// Note: old redChain could be orphaned
			blueCleanup(pdcel);
		}

		return pdcel.vertCount;
	}	
	
	/** 
	 * See if the red chain can be shifted to eliminate some blue
	 * faces. (In earlier work, a "blue" face has two successive
	 * red edges, so might be eliminated by shortcutting via the
	 * remaining edge.)
	 * 
	 * TODO: Lots of situations to cover; make sure changes
	 * doesn't make things worse or create a new blue.
	 * Handle cases as we can.
	 * 
	 * Cycle through because some may need to be eliminated
	 * before others.
	 * @param pdcel
	 * @return int, count of changes made
	 */
	public static int blueCleanup(PackDCEL pdcel) {
		int count=0; 
		
		// first, move 'redChain' if part of blue face
		RedEdge rtrace=pdcel.redChain;
		if (rtrace.myEdge.next==rtrace.nextRed.myEdge) // first edge?
			pdcel.redChain=rtrace.nextRed.nextRed;
		else if (rtrace.myEdge.prev==rtrace.prevRed.myEdge) // second edge?
			pdcel.redChain=rtrace.nextRed;
			
		boolean gotone=true;
		while (gotone) {
			gotone=false;
			
			// pass around red chain
			rtrace=pdcel.redChain; // DCELdebug.printRedChain(pdcel.redChain);
			INNER_LOOP: do {

				HalfEdge base=rtrace.myEdge;

				// if not first red edge of a blue face, skip
				if (base.next!=rtrace.nextRed.myEdge) {
					rtrace=rtrace.nextRed;
					continue INNER_LOOP;
				}
				
				HalfEdge shortcut=base.prev.twin;

				// would shortcut create new blue face? skip
				if (rtrace.prevRed.myEdge.next==shortcut ||
						shortcut.next==rtrace.nextRed.nextRed.myEdge) {
					rtrace=rtrace.nextRed;
					continue INNER_LOOP;
				}

				RedEdge tw1=rtrace.twinRed;
				RedEdge tw2=rtrace.nextRed.twinRed;

				// no twin reds? skip
				if (tw1==null && tw2==null) {
					rtrace=rtrace.nextRed;
					continue INNER_LOOP;
				}
				
				// easiest situation: successive reds have successive
				//   twins as well: make 'shortcut' into a red edge
				if (tw2!=null && tw1!=null && tw2.nextRed==tw1) {
					Vertex vert=rtrace.nextRed.myEdge.origin;
					RedEdge rhold=rtrace.nextRed;
					
					// will orphan tw1; tip vert will not be red
					if (tw1==pdcel.redChain)
						pdcel.redChain=tw1.nextRed;
					vert.redFlag=false;
						// DCELdebug.printRedChain(pdcel.redChain);
					// reuse rtrace/tw2 (which hold data for their origins)
					rtrace.myEdge.myRedEdge=null;
					rtrace.nextRed.myEdge.myRedEdge=null;
					rtrace.myEdge=shortcut;
					shortcut.myRedEdge=rtrace;
					tw2.myEdge.myRedEdge=null;
					tw1.myEdge.myRedEdge=null;
					tw2.myEdge=shortcut.twin;
					shortcut.twin.myRedEdge=tw2;
					
					tw2.twinRed=rtrace;
					rtrace.twinRed=tw2;
					
					tw2.nextRed=tw1.nextRed;
					tw1.nextRed.prevRed=tw2;
					
					rtrace.nextRed=rhold.nextRed;
					rhold.nextRed.prevRed=rtrace;
					
					// check: if bdry, make it the 'halfedge'
					HalfEdge he=rtrace.myEdge;
					if (he.twin.face!=null && he.twin.face.faceIndx<0)
						rtrace.myEdge.origin.halfedge=rtrace.myEdge;
					he=tw2.myEdge;
					if (he.twin.face!=null && he.twin.face.faceIndx<0)
						he.origin.halfedge=he;
					
					// tw1 and rhold should now be orphaned
					
					gotone=true;
					count++;
				}
					
				// TODO: else, more complicated to improve
				//   without just creating a different blue.
				//   This blue may be improved later when
				//   handling another blue.

				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);
		} // done with outer while loop
		// DCELdebug.printRedChain(pdcel.redChain);
		return count;
	}

	/**
	 * Given a DCEL structure with an already processed 
	 * red chain and 'vertices' updated, we process to 
	 * create 'faces', 'edges', 'layoutOrder', etc. 
	 * This can be used when we modify the structure 
	 * inside but can keep or modify the red chain, 
	 * e.g., when flipping an edge, adding an edge, etc. 
	 * @param pdcel
	 * @return PackDCEL
	 */
	public static void fillInside(PackDCEL pdcel) {
		boolean debug=false; // debug=true;
		// NOTE about debugging: Many debug routines depend on
		//      original vertex indices, which might be found in
		//      pdcel.oldNew.
		
		// ============ initialize various variables =============
		
		pdcel.triData=null;  // filled when needed for repacking
		// DCELdebug.printRedChain(pdcel.redChain);
		// we prefer that 'alpha' be set already with non-red origin; 
		//    else choose an edge with non-red origin.
		if (pdcel.alpha!=null && pdcel.alpha.origin.redFlag)
			pdcel.alpha=null;
		if (pdcel.alpha==null) {
			for (int v=1;(v<=pdcel.vertCount && pdcel.alpha==null);v++) {
				Vertex vert=pdcel.vertices[v];
				if (vert.redFlag)
					continue;
				pdcel.alpha=vert.halfedge;
			}
		}
		
		if (pdcel.alpha==null) 
			throw new CombException(
					"'alpha' is missing and 'fillInside' failed to set it");
		
		// zero out 'HalfEdge.eutil' and 'Vertex.vutil' to be used
		//    to keep track during processing.
		for (int v=1;v<=pdcel.vertCount;v++) {
			pdcel.vertices[v].vutil=0;
			HalfEdge edge=pdcel.vertices[v].halfedge; 
			// DCELdebug.edgeFlowerUtils(pdcel,pdcel.vertices[11]);
			HalfEdge trace=edge;
			int safety=1010;
			do {
				trace.eutil=0;
				trace.twin.eutil=0;
				trace=trace.prev.twin; // cclw
				safety--;
			} while(trace!=edge && safety>0);
			if (safety==0)
				throw new CombException(
						"safetied out: 'fillInside', edge "+edge);
		}
		
		// zero out 'RedEdge.redutil'
		RedEdge rtrace=pdcel.redChain; // DCELdebug.printRedChain(pdcel.redChain);
		if (rtrace!=null) { // not spherical case
			int safety=2*pdcel.vertCount+100;
			do {
				safety--;
				rtrace.redutil=0;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain && safety>0);
			if (safety==0) {
				throw new DCELException("redChain doesn't seem to be closed");
			}
		}
		
		// =============== Build 'orderEdges' =================
		
		// Build list of edges as we encounter new faces. Convention 
		//   is that each 'HalfEdge' in 'orderEdges' is associated with 
		//   its face (ie. face on its left, to be created later). 
		//   Each new face encountered, set 'eutil' to 1 for all its edges.
		
		// Three stage process: 
		//  (A) first search for faces using verts connected via 
		//      non-red edges to alpha or verts across edges from these
		//      (because there may be non-red edges with both ends red)
		//  (B) then pick up all faces to the left of the redchain.
		//      while doing this, may pick up non-red vertices not
		//      visited in the first step.
		//  (C) Finally search for faces using non-red verts found in
		//      step 2. This step is needed because there may be 
		//      components of non-red verts separated by red-ended
		//      edges (though this is atypical).
		
		ArrayList<HalfEdge> orderEdges=new ArrayList<HalfEdge>(); 	
		int ordertick=0;
		
		// two list strategy starting with alpha and picking up
		//   non-red nghbs and non-red verts across edges.
		int[] vhits=new int[pdcel.vertCount+1];
		ArrayList<Vertex> currv=new ArrayList<Vertex>(0);
		ArrayList<Vertex> nxtv=new ArrayList<Vertex>(0);

		// (A) ----------------------------------------------
		// put alpha in first and mark edges around its face
		orderEdges.add(pdcel.alpha);
		ordertick++; 
		HalfEdge tr=pdcel.alpha;
		do { 
			tr.eutil=1;
			if (!tr.origin.redFlag)
				nxtv.add(tr.origin);
			if (!tr.twin.prev.origin.redFlag)
				nxtv.add(tr.twin.prev.origin); // across the edge
			tr=tr.next;
		} while(tr!=pdcel.alpha);

		if (nxtv.size()==0)
			throw new CombException("Problem: 'alpha' and all nghbs are red");
		
		// keep two lists to progress through rest of reachable
		//   non-red verts. Note: 'nxtv' starts with at least 'alpha'
		//   or some nghb or cross edge vert.
		while (nxtv.size()>0) {
			currv=nxtv;
			nxtv=new ArrayList<Vertex>(0);

			Iterator<Vertex> cit=currv.iterator();
			while (cit.hasNext()) {
				Vertex vert=cit.next();
				if (vhits[vert.vertIndx]!=0 || vert.redFlag) {
					continue;
				}

				if (debug) // debug=true;
					System.out.println("in currv, vert "+vert);				
				
				// vert is non-red; rotate cclw to find spoke with eutil==1
				HalfEdge startspoke=null;
				HalfEdge he=vert.halfedge;
				do {
					if (he.eutil!=0)
						startspoke=he;
					he=he.prev.twin; // move cclw
				} while (startspoke==null && he!=vert.halfedge);
				
				// if no spokes have been hit, find outer edge with twin hit
				if (startspoke==null) {
					he=vert.halfedge;
					do {
						if (he.next.twin.eutil!=0 && he.next.myRedEdge==null) {
							orderEdges.add(he.next);
							ordertick++;
							tr=he.next;
							do {
								tr.eutil=1;
								tr=tr.next;
							} while(tr!=he.next);
							
							startspoke=he; // this has now been hit
						}
						he=he.prev.twin; // cclw spoke
					} while (startspoke==null && he!=vert.halfedge);
				}
				
				// if 'startspoke' not found, hold 'vert' for later processing 
				if (startspoke==null) {
					nxtv.add(vert); 
					continue;
				}
				startspoke=he; // cclw of edge with 'eutil'==1
				
				// rotate cclw to find successive contiguous faces
				he=startspoke;
				do {
					if (he.eutil==0 && he.myRedEdge==null && he.twin.eutil==1) {
						orderEdges.add(he);
						ordertick++;
						
						// consider adding to 'nxtv'
						Vertex nv=he.next.twin.origin; // next vert in flower
						if (!nv.redFlag && vhits[nv.vertIndx]==0)
							nxtv.add(nv);
						nv=he.next.twin.prev.origin; // across outer edge
						if (!nv.redFlag && vhits[nv.vertIndx]==0)
							nxtv.add(nv);

						// mark face edges
						tr=he;
						do {
							tr.eutil=1;
							tr=tr.next;
						} while (tr!=he);
					}
					he=he.prev.twin; // cclw spoke
				} while(he!=startspoke);
				vhits[vert.vertIndx]=1;
			} // end of while on 'currv'
		} // end of while on 'nxtv'
		currv=new ArrayList<Vertex>(0);
		nxtv=new ArrayList<Vertex>(0);
		
		// (B) --------------------------------------------------------

		// Status: all 'orderEdges' entries have 'origin' in
		//   non-red verts reachable from alpha.
		// However, if not a sphere, may have red faces not yet reached.
		//   We need to find these and set 'redChain' appropriately.
		if (pdcel.redChain!=null) {
			RedEdge goodRed=null;
			HalfEdge goodEdge=null;

			// Find good place to start on red chain
			if (pdcel.redChain.myEdge.eutil!=0) // first edge in 'orderEdges'
				goodRed=pdcel.redChain;
		
			// else, have to find red face with non-red edge twin eutil set.
			if (goodRed==null) {
				rtrace=pdcel.redChain;
				
				// best: has some red edge itself been hit?
				do {
					rtrace=rtrace.nextRed;
					if (rtrace.myEdge.eutil!=0) {
						goodRed=rtrace;
						goodEdge=rtrace.myEdge;
					}
				} while (goodRed==null && rtrace.nextRed!=pdcel.redChain);
				
				// next best: has some red face non-red edge twin been hit?
				if (goodRed==null) { 
					rtrace=pdcel.redChain;
					do {
						tr=rtrace.myEdge.next;
						do {
							if (tr.myRedEdge==null && tr.twin.eutil!=0) {
								goodRed=rtrace;
								goodEdge=tr;
							}
							tr=tr.next;
						} while (goodRed==null && tr!=rtrace.myEdge);
						rtrace=rtrace.nextRed;
					} while (goodRed==null && rtrace!=pdcel.redChain);
				}
				
				if (goodRed==null)
					throw new DCELException("redChain edges have no twins in 'orderEdges'");
					
				pdcel.redChain=goodRed;
				orderEdges.add(goodEdge);
				ordertick++;
					
				// mark all the edges
				tr=pdcel.redChain.myEdge;
				do {
					tr.eutil=1;
					tr=tr.next;
				} while (tr!=pdcel.redChain.myEdge);
			}
		
			// Circulate clw around terminal vertex of successive red edges 
			//   to get any remaining faces on left side of redchain. Note
			//   that as we pivot clw, the next red edge's face is always
			//   added, if necessary.
			rtrace=pdcel.redChain;
			RedEdge nxred=rtrace.nextRed;
			while (nxred!=pdcel.redChain) {
				tr=rtrace.myEdge.next;
				while (tr!=nxred.myEdge) {
					tr=tr.twin;
					Vertex vert=tr.origin;
					
					// Add this for checking in step (c)
					if (vhits[vert.vertIndx]==0 && !vert.redFlag) 
						nxtv.add(tr.twin.origin);
					
					if (tr.eutil!=0) { // already handled
						tr=tr.next;
						continue;
					}
					else { // add to 'orderEdges' and mark edges
						orderEdges.add(tr);
						ordertick++;
						HalfEdge ntr=tr;
						do {
							ntr.eutil=1;
							ntr=ntr.next;
						} while(ntr!=tr);
					}
					tr=tr.next; // next outspoke from end of red edge
				}
				rtrace=nxred;
				nxred=rtrace.nextRed;
			}					
		} // done searching redchain
		
		// (C) ======================================================
		
		// Going around the redchain, did we find non-red verts to process?
		if (nxtv!=null && nxtv.size()>0) {
			// keep two lists again, adding new non-red verts;
			//   this time around we don't need to bother with cross-edge
			//   vertices.
			while (nxtv.size()>0) {
				currv=nxtv;
				nxtv=new ArrayList<Vertex>(0);

				Iterator<Vertex> cit=currv.iterator();
				while (cit.hasNext()) {
					Vertex vert=cit.next();
					if (vhits[vert.vertIndx]!=0 || vert.redFlag) {
						continue;
					}

					if (debug) // debug=true;
						System.out.println("in currv, vert "+vert);				
					
					// vert is non-red; rotate cclw to find spoke with eutil==1.
					//   In this step, this should always exist
					HalfEdge startspoke=null;
					HalfEdge he=vert.halfedge;
					do {
						if (he.eutil!=0)
							startspoke=he;
						he=he.prev.twin; // move cclw
					} while (startspoke==null && he!=vert.halfedge);
					
					// if 'startspoke' not found, revisit 'vert'. (Should not happen)
					if (startspoke==null) {
						nxtv.add(vert); 
						continue;
					}
					startspoke=he; // cclw of edge with 'eutil'==1
					
					// rotate cclw to find successive contiguous faces
					he=startspoke;
					do {
						if (he.eutil==0 && he.myRedEdge==null && he.twin.eutil==1) {
							orderEdges.add(he);
							ordertick++;
							
							// consider adding to 'nxtv'
							Vertex nv=he.next.twin.origin; // next vert in flower
							if (!nv.redFlag && vhits[nv.vertIndx]==0)
								nxtv.add(nv);
							if (!nv.redFlag && vhits[nv.vertIndx]==0)
								nxtv.add(nv);

							// mark face edges
							tr=he;
							do {
								tr.eutil=1;
								tr=tr.next;
							} while (tr!=he);
						}
						he=he.prev.twin; // cclw spoke
					} while(he!=startspoke);
					vhits[vert.vertIndx]=1;
				} // end of while on 'currv'
			} // end of while on 'nxtv'
		}
		
		if (debug) // debug=true;
			System.out.println("ordertick = "+ordertick);
			
		debug=false; // debug=true;
		
		// ============== Faces and Edges: ==================================

		// We account for these anew based entirely on 'orderEdges'; we index
		//   edges face by face, including the ideal faces later, and we create 
		//   new faces (most will be garbage'd) 
				
		// Use 'orderEdges' (which should be unchanged with redChain work): 
		//    * index interior faces, 
		//    * catalog and re-index all edges, face=by=face
		//    * add faces to 'LayoutOrder' only if opposite vert not hit
		//    * find first face that places a 'RedVertex'; this will be 
		//      the packings 'redChain'
		ArrayList<HalfEdge> tmpEdges=new ArrayList<HalfEdge>();
		tmpEdges.add(null); // null in first spot
		ArrayList<DcelFace> tmpFaceList=new ArrayList<DcelFace>();
		tmpFaceList.add(null); // null in first spot
		int ftick=0;
		int etick=0;
		RedEdge newRedChain=null; 

		// Three passes through 'orderEdges'

		// (1) First pass is to catalog faces and edges. There's
		//     a one-to-one correspondence between 'orderEdges'
		//     and faces. Create face and set its edge  
		// DCELdebug.drawOrderEdgeFace(pdcel.p,orderEdges);
		Iterator<HalfEdge> oit=orderEdges.iterator(); // debug=true;
		while (oit.hasNext()) { 
			HalfEdge hfe=oit.next();
			hfe.edgeIndx=++etick;
			tmpEdges.add(hfe);
			DcelFace newface=new DcelFace(++ftick);
			hfe.face=newface;
			newface.edge=hfe;
			tmpFaceList.add(newface);
	
			
// debugging			
// System.out.println("edge(face): "+hfe+"("+ftick+")");			
			
			// get the other edges of this face (typically 2 others)
			HalfEdge nxe=hfe.next;
			int safety=2000;
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
				if (pdcel.oldNew!=null) {
					es.v=pdcel.oldNew.findV(es.v);
					es.w=pdcel.oldNew.findV(es.w);
				}
				DCELdebug.drawEdgeFace(pdcel.p,es);
				System.out.println("["+hfe.origin.vertIndx+","+hfe.twin.origin.vertIndx+
						"]");
			}
		}
		debug=false;
		
		// (2) Second pass is to fill 'layoutOrder' to ensure all
		//     vertices get laid out, including 'RedVertex's 
		//     (which may get multiple locations). Also, to set 
		//     'redChain' to first 'RedEdge' we are able to lay out.
		boolean[] fhits=new boolean[ftick+1];
		pdcel.alpha.origin.vutil=1;
		pdcel.alpha.twin.origin.vutil=1;
		@SuppressWarnings("unused")
		int count_vhits=2;
		
		pdcel.layoutOrder=new HalfLink();
		
		oit=orderEdges.iterator();
		while (oit.hasNext()) {
			HalfEdge hfe=oit.next();

			// check second edge around to identify opposite vertex 
			HalfEdge sea=hfe.next.next;
			Vertex oppVert=sea.origin;
			
			// if normal 'Vertex' 
			if (!oppVert.redFlag && oppVert.vutil==0) { // add 'hfe' to 'layoutOrder'
				oppVert.vutil=1;
				count_vhits++;
				pdcel.layoutOrder.add(hfe);
				fhits[hfe.face.faceIndx]=true;
					
				if (debug) { // debug=true;
					System.out.println("  normal "+hfe.face.faceIndx+" = <"+
							hfe.face.toString()+">, oppVErt "+oppVert.vertIndx+
							", add to 'tmpLayout'");				
					DCELdebug.drawEdgeFace(pdcel,hfe);
				}
			}
			
			// for 'RedVertex', have to process: 
			//     * decide if this instance of this vertex (there may be 
			//       many) has already been hit
			//     * search for appropriate 'redChain' start. 
			else if (oppVert.redFlag){
				RedEdge redge=nextRedEdge(sea);
				if (redge.redutil==0) { // use this face to lay it out
					redge.redutil=1;
					count_vhits++;
					pdcel.layoutOrder.add(hfe);
					fhits[hfe.face.faceIndx]=true;
					
					if (debug) { // debug=true;
						System.out.println("  red case "+hfe.face.faceIndx+" = <"+
								hfe.face.toString()+">, oppVErt "+oppVert.vertIndx+
								", add to 'tmpLayout'; red edge = "+redge.myEdge);				
						DCELdebug.drawEdgeFace(pdcel,hfe);
					}

				}
			
				// search for appropriate 'redChain' start: find first 'RedVertex'
				//   encountered and then add clw faces to reach the first 'RedEdge'.
				// TODO: this may be first edge of "blue" face. May want to avoid
				//   this. 
				if (newRedChain==null) { 
					
					// pivot clw adding faces until hitting redge
					// Note: none of these faces could have been hit before
					while (sea!=redge.myEdge) {
						oppVert=sea.twin.next.next.origin;

						if (debug) { // debug=true;
							System.out.println(" normal pivot "+sea.twin.face.faceIndx+
									" = <"+sea.twin.face.toString()+">");
							DCELdebug.drawEdgeFace(pdcel,sea.twin);
						}
						
						boolean alreadyhit=false;
						if (oppVert.redFlag) {
							RedEdge nre=nextRedEdge(sea.twin.next.next);
							if (nre.redutil==1) // already plotted?
								alreadyhit=true;
							nre.redutil=1; // in any case, will be plotted
						}
						else if (oppVert.vutil==1) {
							alreadyhit=true;
						}

						// put in 'layoutOrder'?
						if (!alreadyhit) {  
							oppVert.vutil=1;
							pdcel.layoutOrder.add(sea.twin);
							fhits[sea.twin.face.faceIndx]=true;
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
					if (redge.redutil==0) { // yes, add 'hfe' to 'layoutOrder'
						redge.redutil=1;
						count_vhits++;
						pdcel.layoutOrder.add(hfe);
						fhits[hfe.face.faceIndx]=true;
						
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
		
		// (3) Third pass is pick up the stragglers --- faces whose vertices
		//     are all laid out after passing through 'layoutOrder'

		pdcel.stragglers=new HalfLink();

		oit=orderEdges.iterator();
		while (oit.hasNext()) {
			HalfEdge hfe=oit.next();
			if (!fhits[hfe.face.faceIndx])
				pdcel.stragglers.add(hfe);
		}
		
//System.out.println("'count_vhits = "+count_vhits);

		pdcel.intFaceCount=ftick;

		debug=false;
		// DCELdebug.printRedChain(newRedChain,null); // pdcel.oldNew); 
		
		pdcel.sideStarts=null;
		pdcel.pairLink=null;
		ArrayList<RedEdge> bdryStarts=null;
		
		// not a sphere?
		if (pdcel.redChain!=null) {
			
			// zero-out 'mobIndx'
			RedEdge nxtred=pdcel.redChain;
			rtrace=nxtred;
			do {
				rtrace.mobIndx=0;
				rtrace=rtrace.nextRed;
			} while(rtrace!=pdcel.redChain);
			
			// ======== Catalog side pairings, free sides, create ideal faces ========
			pdcel.sideStarts=new ArrayList<RedEdge>();
			bdryStarts=new ArrayList<RedEdge>();
			int sidecount=0; 
			int safety=1000;
			RedEdge stopEdge=null;
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

					
					// check if simply connected with no red twins
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
					RedEdge freeStart=rtrace.nextRed;
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
					int sideIndx=nxtred.twinRed.mobIndx; // non-negative
					rtrace=nxtred;

					// if not 0, then this is paired with earlier side
					if (sideIndx!=0) {
						pdcel.sideStarts.add(rtrace);
						do {
							rtrace.mobIndx=-sideIndx;
							rtrace=rtrace.nextRed;
						} while (rtrace.twinRed!=null && 
								rtrace.twinRed.mobIndx==sideIndx);
						nxtred=rtrace;
					}
					
					// else this is a new paired edge
					else { 
						sideIndx=++sidecount;;
						rtrace.mobIndx=sideIndx;
					
						// first look upstream to find start
						RedEdge twintrace=rtrace.twinRed;
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
				if (pdcel.oldNew!=null) {
					es.v=pdcel.oldNew.findV(es.v);
					es.w=pdcel.oldNew.findV(es.w);
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
		pdcel.faces=new DcelFace[ftick+1];
		for (int f=1;f<=ftick;f++) {
			pdcel.faces[f]=tmpFaceList.get(f); // pdcel.tmpFaceList.size();
		}
		tmpFaceList=null;
		
		// (3) Ideal faces: --------------------------------------------------
		pdcel.idealFaceCount=0;
		if (bdryStarts!=null && bdryStarts.size()>0) {
			ArrayList<DcelFace> tmpIdealFaces=new ArrayList<DcelFace>(0);
			Iterator<RedEdge> rit=bdryStarts.iterator();
			int idtick=0;
			while (rit.hasNext()) {
				RedEdge redge=rit.next();
				HalfEdge he=redge.myEdge.twin;
				if (he.face!=null) { // not in bdry
					continue;
				}
				
				// is this side already handled?
				DcelFace newface=new DcelFace();
				newface.faceIndx=-(++idtick);
				newface.edge=redge.myEdge.twin;
				do {
					he.face=newface;
					he=he.next;
				} while (he!=redge.myEdge.twin);
				tmpIdealFaces.add(newface);
			}
			pdcel.idealFaceCount=idtick;
			pdcel.idealFaces=new DcelFace[idtick+1];
			for (int j=0;j<idtick;j++) {
				pdcel.idealFaces[j+1]=tmpIdealFaces.get(j);
			}
		}
		
//		if (debug) { // debug=true;
//			DCELdebug.drawEdgeFace(pdcel,tmpLayout);
//			DCELdebug.drawEdgeFace(pdcel,tmpfullLayout);
//			debug=false;
//		}

		// Arrange the side pairings in order of 'sideStarts'
		if (pdcel.sideStarts!=null) {
			Iterator<RedEdge> rit=pdcel.sideStarts.iterator();
			int sptick=0;
			pdcel.pairLink=new PairLink();
			pdcel.pairLink.add(null); // first is null
			EdgeLink oldnew=new EdgeLink();
			while (rit.hasNext()) {
				RedEdge rstart=rit.next();
				
				SideData sideData=new SideData();
				sideData.spIndex=++sptick; // indexed from 1
				oldnew.add(new EdgeSimple(rstart.mobIndx,sptick));
				sideData.startEdge=rstart;

				// if paired, find the end
				RedEdge rtrc=rstart;
				if (rstart.twinRed!=null) {
					while (rtrc.twinRed!=null &&
							rtrc.nextRed==rtrc.twinRed.prevRed.twinRed) {
						rtrc=rtrc.nextRed;
					}
					sideData.endEdge=rtrc;
				}
				else { // segment (perhaps not all) of a boundary component
					do {
						rtrc=rtrc.nextRed;
					} while (rtrc!=rstart && rtrc.twinRed==null);
					sideData.endEdge=rtrc.prevRed;
				}
				pdcel.pairLink.add(sptick,sideData); // 0 entry is null
			} // done with while through sidestarts
			
			// find and label the pairings and free sides
			int pairCount=0;
			int freeCount=1;
			Iterator<SideData> pdpit=pdcel.pairLink.iterator();
			pdpit.next(); // flush null entry 
			while(pdpit.hasNext()) {
				SideData sdata=pdpit.next();
				
				// a paired side
				if (sdata.startEdge.twinRed!=null) {
					int oldindx=oldnew.findV(sdata.spIndex);
					if (oldindx>0) {
						int mate=oldnew.findW(-oldindx);
						SideData oppData=pdcel.pairLink.get(mate);
						sdata.mateIndex=oppData.spIndex;
						oppData.mateIndex=sdata.spIndex;
						char c=(char)('a'+pairCount);
						sdata.label=String.valueOf(c);
						// Note: in place of upper case, use repeat lower
						//   (e.g. instead of 'A', label is 'aa'). This 
						// leads to correct icon in "Resurces/Icons/mobius"
						oppData.label=
								new String(String.valueOf(c)+String.valueOf(c));
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
		return;
	}
	
	/**
	 * Determine a partial order to lay out faces
	 * for computing radii, starting with 'alpha' and 
	 * avoiding crossing any forbidden edges in 'hlink' 
	 * (or in the red chain). This method is used, e.g., 
	 * to layout outside one or more general branch points, 
	 * where we layout additional local edges separately.  
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink
	 * @return HalfLink, null on error
	 */
	public static HalfLink partialTree(PackDCEL pdcel,
			HalfLink hlink) {
		
		// include bdry edges in 'hlink'
		if (hlink==null)
			hlink=new HalfLink();
		if (pdcel.redChain!=null) {
			RedEdge rtrace=pdcel.redChain;
			do {
				hlink.add(rtrace.myEdge);
				hlink.add(rtrace.myEdge.twin);
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);
		}
		if (hlink.size()==0)
			return null;
		for (int j=1;j<=pdcel.edgeCount;j++)
			pdcel.edges[j].eutil=0;

		int alph=pdcel.alpha.origin.vertIndx;
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext()) {
			HalfEdge he=his.next();
			if (he.origin.vertIndx==alph || he.twin.origin.vertIndx==alph) {
				CirclePack.cpb.errMsg("partialTree: 'alpha' is on a forbidden edge");
				return null;
			}
			he.eutil=-1;
			he.twin.eutil=-1;
		}
		
		// Get the 'HalfLink' started
		HalfLink partialOrder=new HalfLink();
		partialOrder.add(pdcel.alpha);
		pdcel.alpha.eutil=1;
		pdcel.alpha.next.eutil=1;
		pdcel.alpha.next.next.eutil=1;
		
		// keep two lists of face indices
		FaceLink currf=new FaceLink();
		FaceLink nextf=currf;
		nextf.add(pdcel.alpha.face.faceIndx);
		boolean hit=true;
		while (hit && nextf.size()>0) {
			hit=false;
			currf=nextf;
			nextf=new FaceLink();
			
			Iterator<Integer> cis=currf.iterator();
			while (cis.hasNext()) {
				int f=cis.next(); // this face should be done
				DcelFace face=pdcel.faces[f];
				HalfEdge he=face.edge;
				do {
					if (he.twin.eutil==0) { // not forbidden, not handled
						DcelFace newface=he.twin.face;
						partialOrder.add(he.twin);
						if (newface.edge.eutil>=0) 
							newface.edge.eutil=1;
						if (newface.edge.next.eutil>=0)
							newface.edge.next.eutil=1;
						if (newface.edge.next.next.eutil>=0)
							newface.edge.next.next.eutil=1;
						nextf.add(newface.faceIndx);
						hit=true;
					}
					he=he.next;
				} while (he!=face.edge);
			} // done with currf	
		} // done with nextf
		
//		CPBase.Hlink=partialOrder;

		return partialOrder;
	}
	
	/** 
	 * Alternative face drawing orders for simply connected 
	 * complexes. Typically ('keepon=true') we get a 'HalfLink'
	 * which lays out every circle, but our aim is to avoid 
	 * some layout problems by not using some possibly unreliable
	 * vertices for later layouts, e.g., avoiding use of
	 * 3/4-degree vertices and/or vertices having extremely 
	 * small radii. (Note that we are not actually computing the
	 * layout, just arranging the drawing order.)
	 * 
	 * The vertices to avoid using (as much as possible) are
	 * provided in 'uvert'. We keep track in 'vstatus' of 
	 * these, of vertices that have been laid out, and thus
	 * of vertices whose layout is still needed. We create a
	 * copy of 'pdcel.layoutOrder' to use while we generate
	 * 'newOrder'; in particular, we always start 'newOrder'
	 * with 'alpha', even if unreliable. 
	 * 
	 * After this, we continue with 'layoutOrder'; each time
	 * we encountering an edge that has an unreliable end we
	 * skip it, but save it in 'redolist' while continuing with
	 * 'layoutOrder'. Otherwise, we pass repeatedly via
	 * two list to try to find reliable edges to lay out out
	 * any vertices not yet placed. If 'keepon' is true, we
	 * ultimately can use unreliable edges. If false, we 
	 * never use an unreliable, but rather return with the
	 * incomplete 'newOrder' layout. The calling routine 
	 * can use the return HalfLink, perhaps saving it or
	 * using it to replace 'layoutOrder'.
	 * @param pdcel PackDCEL
	 * @param uvert NodeLink
	 * @param keepon boolean
	 * @return HalfLink, null on error
	*/
	public static HalfLink tailorFaceOrder(PackDCEL pdcel,
			NodeLink uvert,boolean keepon) {
		
		// 'vstatus': -1 unreliable; -2 is placed; 1 placed
		int[] vstatus=new int[pdcel.vertCount+1];
		Iterator<Integer> uis=uvert.iterator();
		while (uis.hasNext())
			vstatus[uis.next()]=-1;
		
		// get started
		HalfEdge he=pdcel.alpha;
		HalfLink newOrder=new HalfLink();
		newOrder.add(he);
		int v=he.origin.vertIndx;
		if (vstatus[v]<-1)
			vstatus[v]=-2;
		v=he.next.origin.vertIndx;
		if (vstatus[v]<-1)
			vstatus[v]=-2;
		v=he.next.next.origin.vertIndx;
		if (vstatus[v]<-1)
			vstatus[v]=-2;
		
		HalfLink layout=pdcel.layoutOrder.makeCopy();
		HalfLink redolist=new HalfLink();
		Iterator<HalfEdge> lois=layout.iterator();
		while (lois.hasNext()) {
			he=lois.next();
			// can we use this edge?
			if (vstatus[he.origin.vertIndx]>0 &&
					vstatus[he.next.origin.vertIndx]>0) {
				int w=he.next.next.origin.vertIndx;
				if (vstatus[w]==-1)
					vstatus[w]=-2;
				else if (vstatus[w]==0)
					vstatus[w]=1;
			}
			else 
				redolist.add(he);
		}
		
		if (redolist.size()==0)
			return newOrder;
		
		// now we cycle through 'redolist' to identify
		//    vertices not placed, see if they're ready
		//    to be placed, then follow 'redolist' to
		//    see if this allows any of them to be placed.

		// finding one ready for
		boolean hits=true;
		while (hits && redolist.size()>0) {
			hits=false;
			// go until you find one vert you can place
			for (int j=0;(j<redolist.size() && !hits);j++) {
				HalfEdge edgetry=redolist.get(j);
				Vertex oppVert=edgetry.next.next.origin;
				int opp=oppVert.vertIndx;
				
				// if 'opp' not placed, try to place it using
				//   outer edges
				if (vstatus[opp]==-1 || vstatus[opp]==0) { // not yet placed
					HalfLink outer=oppVert.getOuterEdges();
					Iterator<HalfEdge> ois=outer.iterator();
					while (ois.hasNext()) {
						he=ois.next();
						if (vstatus[he.origin.vertIndx]>0 &&
								vstatus[he.next.origin.vertIndx]>0) {
							if (vstatus[opp]==-1) {
								vstatus[opp]=-2;
								redolist.remove(j);
								hits=true;
							}
							else if (vstatus[opp]==0) {
								vstatus[opp]=1;
								redolist.remove(j);
								hits=true;
							}
							
							// only keep edges of redolist if target vert
							//   still not placed.
							HalfLink newredo=new HalfLink();
							for (int k=0;k<redolist.size();k++) {
								he=redolist.get(k);
								opp=he.next.next.origin.vertIndx;
								// not placed?
								if (vstatus[opp]==-1 || vstatus[opp]==0) {
									newredo.add(he);
								}
							}
							redolist=newredo;
						}
					}
				}
			} // done going through 'redolist'
		} // 

		// reaching here, if 'redolist' is not yet empty, there
		//   should be vertices not laid out; the only choice
		//   now is whether to use unreliables to finish off
		if (!keepon || redolist.size()==0)
			return newOrder;
		
		newOrder.abutMore(redolist);
		return newOrder;
	}
		      
	/**
	 * 'pdcel' should be complete with red chain
	 * established. Goal is to renumber the vertices,
	 * starting with alpha and working through the
	 * interior first, then adding the red vertices
	 * last. We first clone 'pdcel', holding 'pdcel.p'
	 * to see if there are any errors; if all seemed
	 * to go well, we also rejigger p.vData to the
	 * new numbering so we can save its data (assuming
	 * p is not null). If all goes well, we 'attachDCEL'
	 * the new DCEL. If vData shifting goes bad, we
	 * adversely affect p.
	 * @param pdc PackDCEL
	 * @return int, vertcount on success, -oldv for error
	 * with DCEL renumbering or error with vData
	 * shifting.
	 */
	public static int reNumber(PackDCEL pdc) {
	
		PackDCEL pdcel=CombDCEL.cloneDCEL(pdc);
		
		PackData hold_pd=pdc.p; // hold_pd.getFlower(1132);
		
		int vertcount=pdcel.vertCount;
		pdcel.setAlpha(0, null,true);

		// ============= mark verts/edges ==============

		// 'vindx'[v] = new index for v
		int[] vindx=new int[vertcount+1];
		int vtick=1;
		
		// use two lists for interior component
		NodeLink nxv=new NodeLink();
		nxv.add(pdcel.alpha.origin.vertIndx);
		vindx[pdcel.alpha.origin.vertIndx]=vtick;
		NodeLink curv=new NodeLink();
		while (nxv.size()>0) {
			curv=nxv;
			nxv=new NodeLink();
			Iterator<Integer> cis=curv.iterator();
			while (cis.hasNext()) {
				int v=cis.next();
				int[] petals=pdcel.vertices[v].getPetals();
				for (int j=0;j<petals.length;j++) {
					if (vindx[petals[j]]==0 && 
							pdcel.vertices[petals[j]].bdryFlag==0) {
						vindx[petals[j]]=++vtick;
						nxv.add(petals[j]);
					}
				}
			} 
		}
		
		// travel red chain to find the rest
		if (pdcel.redChain!=null) {
			RedEdge rtrace=pdcel.redChain;
			do {
				Vertex vert=rtrace.myEdge.origin;
				int oldv=vert.vertIndx;
				if (vindx[oldv]!=0) {
					CirclePack.cpb.errMsg("'d_reNumber' error in red chain vert "+oldv);
					return -oldv;
				}
				vindx[oldv]=++vtick;
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);
		}
		
		if (vtick!=vertcount) {
			CirclePack.cpb.errMsg("'d_reNumber': didn't get expected count, "+
					"vertCount="+vertcount+", but vtick="+vtick);
			return -vtick;
		}
		
		// gather new 'vertices' for 'pdcel':
		Vertex[] newverts=new Vertex[vertcount+1];
		pdcel.oldNew=new VertexMap();
		for (int v=1;v<=vertcount;v++) {
			newverts[vindx[v]]=pdcel.vertices[v];
			newverts[vindx[v]].vertIndx=vindx[v];
			if (v!=vindx[v])
				pdcel.oldNew.add(new EdgeSimple(v,vindx[v]));
		}
		pdcel.vertices=newverts;
		
		if (newverts[0]!=null)
			throw new CombException("'d_reNumber' failed to get at least one vertex");

		int rslt=hold_pd.attachDCEL(pdcel); 
		hold_pd.vertexMap=pdcel.oldNew;
		return rslt;
	}
		
	/**
	 * Given a 'HalfEdge', check if origin is 'RedVertex'. If so,
	 * return the next clw 'RedEdge' (possibly itself). If not,
	 * return null;
	 * @param edge HalfEdge
	 * @return RedEdge or null 
	 */
	public static RedEdge nextRedEdge(HalfEdge edge) {
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
		try {
			int[] flower = bouq[v];
			for (int j = 0; j < flower.length; j++)
				if (flower[j] == w)
					return j;
		} catch(Exception ex) {}
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
	 * @param rededge RedEdge
	 * @return boolean
	 */
	public static boolean isSphere(int[] keepv,RedEdge rededge) {
		if (rededge.nextRed!=rededge.prevRed) 
			return false;
		int v=rededge.myEdge.origin.vertIndx;
		int w=rededge.prevRed.myEdge.origin.vertIndx;
		if (keepv[v]!=0 || keepv[w]!=0)
			return true;
		return false;
	}
	
	/**
	 * Find 'RedEdge' from redChain whose 'myEdge' is equal to given 'edge'
	 * @param redchain RedEdge
	 * @param edge HalfEdge
	 * @return RedEdge or null
	 */
	public static RedEdge isMyEdge(RedEdge redchain,HalfEdge edge) {
		if (redchain==null)
			return null;
		RedEdge rtrace=redchain;
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
		int safety=2000;
		do {
			safety--;
			if (he.eutil==0)
				he.eutil=1;
			he=he.next;
		} while(he!=edge && safety>0);
		if (safety==0) {
			throw new CombException("error in 'markFaceUtils' for edge "+edge);
		}
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
		return RawManip.markGenerations(pdcel, seedstop);
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
	public static PackDCEL puncture_vert(PackDCEL pdcel,int v) {
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
				throw new CombException("puncture error: "+
						"no interior 'seed' to avoid "+v);
		}

		// set prohibited edges in 'hlink', set baseEdge for extraction
		StringBuilder strbld=new StringBuilder(Integer.toString(v)+" ");
		HalfEdge baseEdge=pdcel.alpha;
		if (seed>0) { // have to change baseEdge?
			baseEdge=pdcel.vertices[seed].halfedge;
			strbld.append(" -v "+Integer.toString(seed));
		}
		HalfLink hlink=cookieData(pdcel.p,strbld.toString());
	    return CombDCEL.extractDCEL(pdcel,hlink,baseEdge);
	}

	public static HalfLink cookieData(PackData p,String str) {
		Vector<Vector<String>> flagSegs=StringUtil.flagSeg(str);
		return cookieData(p,flagSegs);
	}
	
	/**
	 * The "cookie" process (cutting out a subcomplex from an
	 * existing packing) involves specifying a new red chain 
	 * defining the subcomplex. To get that red chain, we must
	 * specify a 'HalfLink' of forbidden edges that can't be
	 * crossed as the red chain in built. 
	 * 
	 * In this routine, we process 'flags' to set seed and 
	 * forbidden edges. Note that bdry edges are always forbidden.
	 * 
	 * If there is an list of "poison" vertices (to be excised), 
	 * they should appear in first vector of strings in 
	 * 'flags' without a preceding flag. Edges separating these
	 * from the "seed" vertex will be included as forbidden.
	 * 
	 * Then check for flag segments:
	 * * Flags: -v {v}, for identifying seed: 'p.alpha' is reset.
	 * * Flag -e {u v...} is edge list, adds to any forbidden
	 *   edges already included.
	 * * flag -n {v}, non-keepers; any edges with both ends
	 *   non-keepers will be added as forbidden edges.
	 *   
	 * Note: difference between 'poison' (vertices to be excised)
	 * and 'non-keepers': the latter may remain in the boundary of 
	 * the excised structure.
     *
	 * If no forbidden edges are specified then use 
	 * 'CPBase.ClosedPath' (if not null); points on the side 
	 * of 'CPBase.ClosedPath' opposite to 'seed' become poison 
	 * by default.
	 * 
	 * Return 'HalfLink' of forbidden edges which are actually
	 * encountered, which can then be used, with alpha, to 
	 * create a red chain.
	 *   
	 * @param p PackData
	 * @param flags Vector<Vector<String>>; may be null
	 * @return HalfLink, forbidden edges
	 */
	public static HalfLink cookieData(PackData p,
			Vector<Vector<String>> flags) {
		boolean debug=false;
		PackDCEL pdcel=p.packDCEL; 
		NodeLink vlink=new NodeLink();
		HalfLink hlink=new HalfLink();
		int[] eutil=new int[pdcel.edgeCount+1]; // mark chosen edges
		
		// read incoming data
		while (flags!=null && flags.size()>0) { 
			Vector<String> items=(Vector<String>)flags.remove(0);
			// if no flag, then list is poison vertices
			if (!StringUtil.isFlag(items.get(0))) { // no flag? poison vertices
				vlink=new NodeLink(p,items);
			}
			else {
				String str=(String)items.get(0);
				if (str.equals("-v")) { // given seed, reset 'alpha' and continue
					if (items.size()<2) 
						throw new ParserException(
								"cookie crumbled: error in -v flag");
					int alp=Integer.parseInt((String)items.get(1));
					Vertex alphavert=pdcel.vertices[alp];
					if (alphavert.bdryFlag!=0) 
						throw new ParserException(
								"cookie crumbled: chosen seed "+alp+
								"is not interior");
					pdcel.alpha=pdcel.vertices[alp].halfedge;
					items.remove(1);
					items.remove(0);
				}
				else if (str.equals("-e")) { // specified forbidden edges
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

					// mark verts that are non-keepers in 'vutil'
					Iterator<Integer> nis=nonvs.iterator();
					while (nis.hasNext()) {
						int v=nis.next();
						pdcel.vertices[v].vutil=v;
					}
					
					// edges between non-keepers are forbidden
					int w;
					for (int v=1;v<=pdcel.vertCount;v++) {
						Vertex vert=pdcel.vertices[v];
						if (vert.vutil!=0) {
							int[] petals=vert.getPetals(); // open flower
							for (int j=0;j<petals.length;j++) {
								if ((w=pdcel.vertices[petals[j]].vutil)>v) {
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
				
		// If no forbiddens so far, then use stored 'ClosePath'
		if (hlink.size()==0 && vlink.size()==0) {
			if (CPBase.ClosedPath==null) 
				throw new ParserException("cookie: No path defined.");
			// which side is seed on?
			boolean seed_wrap= 
					PathManager.path_wrap(p.
							getCenter(pdcel.alpha.origin.vertIndx)); 
			for (int v=1;v<=p.nodeCount;v++) {
				if (seed_wrap!=PathManager.path_wrap(p.getCenter(v))) { 
					vlink.add(v);
				}
			}
			
			if (debug) // debug=true; 
				CPBase.Vlink=vlink;
		}
		
		if (vlink.size()!=0) {
			hlink.separatingLinks(pdcel,vlink,pdcel.alpha.origin.vertIndx);
			// add any missing bdry edges
			for (int f=1;f<=pdcel.idealFaceCount;f++) {
				DcelFace idealf=pdcel.idealFaces[f];
				HalfEdge he=idealf.edge;
				do {
					if (eutil[he.edgeIndx]==0) 
						hlink.add(he.twin);
					he=he.next;
				} while (he!=idealf.edge);
			}
		}
		
		// want to see what's marked
		if (debug) { // debug=true;
			deBugging.DCELdebug.drawHalfLink(p, hlink);
			CPBase.Elink=new EdgeLink();
			CPBase.Elink.abutHalfLink(hlink);
			CPBase.Vlink=vlink;
			CPBase.Hlink=hlink;
		}
		
		return hlink; // hlink.size();
	}
	
	/**
	 * Given 'pdcel' with redchain, we want to "prune" so 
	 * that every bdry vertex has an interior neighbor. 
	 * We simply cookie with halfLink as the current 
	 * red chain and 'prune'=true. Calling routine should
	 * 'fixDCEL'.
	 * @param pdcel PackDCEL
	 * @return int, count of adjustments made (may be zero)
	 */
	public static int pruneDCEL(PackDCEL pdcel) {
		int vcount=pdcel.vertCount;
		if (pdcel.redChain==null)
			return 0;
		HalfLink hlink=new HalfLink();
		RedEdge rtrace=pdcel.redChain;
		do {
			if (pdcel.isBdryEdge(rtrace.myEdge))
				hlink.add(rtrace.myEdge);
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain);
		
		// cookie with 'prune' true
		CombDCEL.redchain_by_edge(pdcel, hlink, null, true);
		
		// see if anything was pruned
		return (vcount-pdcel.vertCount);
	}
	
	/**
	 * Create largest simply connected complex containing
	 * 'seed' vertices as generation 0, and up to generation 
	 * no bigger than 'gen' from those seeds. This is two step
	 * process: first create a new red chain, but then clone 
	 * vertices as needed to cut apart any twinned red 
	 * segments to get simple connectivity. Use 'vutil' to 
	 * refer to parent vertices. We clone 'pdcel' first;
	 * calling routine must process on return. 
	 * @param pdcel PackDCEL
	 * @param vec_seed int[], default to 'alpha'
	 * @param gen int
	 * @return PackDCEL
	 */
	public static PackDCEL gen2red(PackDCEL pdcel,
			int[] vec_seed,int gen) {
		
		// make sure there's enough space
		int tick=0;
		RedEdge rtrace=pdcel.redChain;
		do {
			tick++;
			rtrace=rtrace.nextRed;
		} while(rtrace!=pdcel.redChain);
		int n=pdcel.vertCount+tick+10;

		// clone
		PackDCEL newcel=CombDCEL.cloneDCEL(pdcel);
		Vertex[] newverts=new Vertex[n];
		if (pdcel.vertices.length<n) {
			for (int j=1;j<=pdcel.vertCount;j++)
				newverts[j]=pdcel.vertices[j];
		}
		newcel.vertices=newverts;
		
		// TODO: not yet sure of behavior. I think we identify
		//   forbidden edges by current red chain and those of
		//   generation < 'gen'. But then we have to separate
		//   any twinned red segments, making clones of their
		//   vertices.
		
		return (PackDCEL)null;
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
	public static PackDCEL puncture_face(PackDCEL pdcel,int f) {
		DcelFace face=pdcel.faces[f];
		HalfLink hlink=new HalfLink();
		HalfEdge he=face.edge;
		hlink.add(he);
		hlink.add(he.next);
		hlink.add(he.next.next);
		return extractDCEL(pdcel,hlink,pdcel.alpha);
	}
	
	/**
	 * Double this packing across one or more full 
	 * bdry components, or if 'segment' is true, 
	 * double across a segment of one bdry component. 
	 * Using clones, so original data should be unchanged.
	 * 'oldNew' can be used to find reflection of alpha.
	 * @param pdc PackDCEL
	 * @param blist NodeLink
	 * @param segment boolean
	 * @return PackDCEL
	 */
	public static PackDCEL doubleDCEL(PackDCEL pdc,NodeLink blist,
			boolean segment) {
	
		try {
			// clone two copies
			PackDCEL pdc1=CombDCEL.cloneDCEL(pdc);
			PackDCEL pdc2=CombDCEL.cloneDCEL(pdc);
			CombDCEL.reorient(pdc2);
			
			if (blist==null || blist.size()==0)
	    		  blist=new NodeLink(pdc.p,"B");

			else if (segment) {
				int n=blist.size()-1; // number of edges
				int v=blist.getLast(); // choose last to be clw
				PackDCEL newpdcel=CombDCEL.adjoin(pdc1, pdc2,v,v,n);
				return newpdcel; 
			}			
		
			// first adjoins two, the rest are self-adjoins 
			int v=blist.remove(0); 
			int shift=pdc1.vertCount; // will be added to indices of pdc2 
			PackDCEL pdcel=CombDCEL.adjoin(pdc1, pdc2, v, v, -1);
			
			// Establish 'EdgeLink' for new indices after first adjoin
			EdgeLink indxlink=new EdgeLink();
			Iterator<Integer> bis=blist.iterator();
			while (bis.hasNext()) {
				v=bis.next();
				int dv=pdcel.oldNew.findW(v+shift); // new index after first adjoin
				if (dv==0) 
					dv=v+shift;
				indxlink.add(new EdgeSimple(v,dv)); 
			}
			
			// The rest are self-adjoins
			while (indxlink.size()>0) {
				
				// next adjoin
				EdgeSimple nextid=indxlink.remove(0); 	
				pdcel=CombDCEL.adjoin(pdcel,pdcel,nextid.v,nextid.w,-1);
				
				// readjust remaining indices
				Iterator<EdgeSimple> eis=indxlink.iterator();
				while (eis.hasNext()) {
					EdgeSimple es=eis.next();
					es.w=pdcel.oldNew.findW(es.w);
					if (es.w==0) {
						CirclePack.cpb.errMsg("failed to adjoin component for "+v);
						return pdcel;
					}
				}
			}

			return pdcel;

		} catch (Exception ex) {
			throw new ParserException("'double' error: "+ex.getMessage());
		}
	}
	
	/**
	 * Given 'pdc' (sometimes a clone of original DCEL), reverse
	 * its orientation: clone all edges, establish prev, next, 
	 * twins, reverse redchain, adjust faces, keep vertices but 
	 * adjust bdry halfedges, reflect centers in imaginary axis. 
	 * Complete with 'fillInside' command. Result remains in 'pdc' 
	 * @param pdc PackDcel
	 */
	public static void reorient(PackDCEL pdc) {
		HalfEdge[] edges=new HalfEdge[pdc.edgeCount+1];
		
		// create new edges, same origins
		for (int e=1;e<=pdc.edgeCount;e++) {
			pdc.edges[e].edgeIndx=e; // make sure indices are aligned
			edges[e]=new HalfEdge(pdc.edges[e].origin);
			edges[e].edgeIndx=e;
		}
		
		// set vertex 'halfedge's; same edge for interior, but
		//    need downstream edge for bdry.
		for (int v=1;v<=pdc.vertCount;v++) {
			Vertex vert=pdc.vertices[v];
			if (vert.bdryFlag>0) {
				vert.halfedge=edges[vert.halfedge.twin.next.edgeIndx];
			}
			else 
				vert.halfedge=edges[vert.halfedge.edgeIndx];
			vert.center.x=-1.0*vert.center.x; // reflect in y-axis
		}
		
		// set edge pointers
		for (int e=1;e<=pdc.edgeCount;e++) {
			HalfEdge he=edges[e];
			HalfEdge orighe=pdc.edges[e];
			he.twin=edges[orighe.twin.edgeIndx];
			he.next=edges[orighe.twin.prev.twin.edgeIndx];
			he.prev=edges[orighe.twin.next.twin.edgeIndx];
			if (orighe.twin.face.faceIndx<=0) // outer bdry edge?
				he.face=new DcelFace(-1);
		}

		// reset 'alpha' 'gamma'
		try {
			pdc.alpha=edges[pdc.alpha.edgeIndx];
			pdc.gamma=edges[pdc.gamma.edgeIndx];
		} catch(Exception ex) {}
		
		// reverse redchain
		if (pdc.redChain!=null) {
			// first we index (from 0) using 'redutil'
			int rCount=0;
			RedEdge rtrace=pdc.redChain;
			do {
				rtrace.redutil=rCount++;
				rtrace=rtrace.nextRed;
				Complex z=rtrace.getCenter();
				z.x=-1.0*z.x;
				rtrace.setCenter(z);
			} while (rtrace!=pdc.redChain);
			
			// store array of red and their 'myEdge's
			RedEdge[] rededges=new RedEdge[rCount];
			int[] myedges=new int[rCount];
			rtrace=pdc.redChain;
			do {
				rededges[rtrace.redutil]=rtrace.clone();
				myedges[rtrace.redutil]=rtrace.myEdge.edgeIndx;
				rtrace=rtrace.nextRed;
			} while(rtrace!=pdc.redChain);
		
			// build reverse redchain, 'twinRed's set later
			RedEdge newRedChain=rededges[rCount-1];
			RedEdge res;
			for (int r=0;r<rCount;r++) {
				res=rededges[r];
				res.nextRed=rededges[(r-1+rCount)%rCount];
				res.prevRed=rededges[(r+1)%rCount];
				HalfEdge new_myedge=edges[myedges[(r-1+rCount)%rCount]].twin;
				res.myEdge=new_myedge;
				new_myedge.myRedEdge=res;
				// outdated stuff
				res.mobIndx=0;
				res.twinRed=null; 
			}
			pdc.redChain=newRedChain; 
			
			// look for twinning
			rtrace=pdc.redChain;
			RedEdge rt;
			do {
				if (rtrace.twinRed==null &&
						(rt=rtrace.myEdge.twin.myRedEdge)!=null) {
					rtrace.twinRed=rt;
					rt.twinRed=rtrace;
				}
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdc.redChain);
		}
	
		// wrap up
		pdc.edges=edges;
		pdc.triData=null;
		CombDCEL.fillInside(pdc);
	}
	  
	/**
     * Create an exact duplicate of this 'pdc' and
     * all its existing objects. Note that faces,
     * ideal faces, layoutOrder, etc. may be missing.
     * PackData set to null and 'triData' is not copied.
     * @param pdc PackDCEL
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
    	
    	// new vertices
       	pdcel.vertices=new Vertex[pdc.vertCount+1];
    	for (int v=1;v<=pdc.vertCount;v++) {
    		pdcel.vertices[v]=pdc.vertices[v].clone();
    		pdcel.vertices[v].vertIndx=v;
    	}
    	
    	// new edges --- pointing to new origins
    	pdcel.edges=new HalfEdge[pdc.edgeCount+1];
    	for (int e=1;e<=pdc.edgeCount;e++) {
    		pdcel.edges[e]=pdc.edges[e].clone();
    		pdcel.edges[e].origin=pdcel.vertices[pdc.edges[e].origin.vertIndx];
    		pdcel.edges[e].edgeIndx=e; // make sure the indices are correct
    	}
    	
    	// HalfEdge' pointers 
    	for (int ee=1;ee<=pdcel.edgeCount;ee++) {
    		HalfEdge newhe=pdcel.edges[ee];
    		HalfEdge oldhe=pdc.edges[ee];
    		newhe.next=pdcel.edges[oldhe.next.edgeIndx];
    		newhe.prev=pdcel.edges[oldhe.prev.edgeIndx];
    		newhe.twin=pdcel.edges[oldhe.twin.edgeIndx];
    	}

    	// reset vertex 'halfedge's
    	for (int v=1;v<=pdc.vertCount;v++) {
    		pdcel.vertices[v].halfedge=
    				pdcel.edges[pdc.vertices[v].halfedge.edgeIndx];
    	}
    	
    	// note: there may be no faces/idealfaces
    	if (pdc.faces!=null && pdc.faceCount>0) {
    		pdcel.faces=new DcelFace[pdc.faceCount+1];
    		pdcel.idealFaces=new DcelFace[pdc.idealFaceCount+1];
    		for (int f=1;f<=pdc.faceCount;f++) {
    			pdcel.faces[f]=pdc.faces[f].clone();
    			pdcel.faces[f].edge=pdcel.edges[pdc.faces[f].edge.edgeIndx];
    			pdcel.faces[f].faceIndx=f;
    		}
    		for (int g=1;g<=pdc.idealFaceCount;g++) {
    			pdcel.idealFaces[g]=pdc.idealFaces[g].clone();
    			pdcel.idealFaces[g].edge=
    					pdcel.edges[pdc.idealFaces[g].edge.edgeIndx];
    		}
        	for (int e=1;e<=pdc.edgeCount;e++) {
        		int findx=pdc.edges[e].face.faceIndx;
        		if (findx>0) 
        			pdcel.edges[e].face=pdcel.faces[findx];
        		if (findx<0)
        			pdcel.edges[e].face=pdcel.idealFaces[-findx];
        	}
    	}

    	// count/catalog redchain edges: note that red edges aen't
    	//   indexed, so 'redutil' will be tmp index (from 1)
    	int redcount=0;
    	if (pdc.redChain!=null) {
    		RedEdge rtrace=pdc.redChain;
    		do {
    			redcount++;
    			rtrace=rtrace.nextRed;
    		} while(rtrace!=pdc.redChain);
    	}
    	RedEdge[] newRedEdges=null;
    	if (redcount>0) {
    		newRedEdges=new RedEdge[redcount+1];
    		int rtick=0;
    		RedEdge oldrtrace=pdc.redChain;
    		do {
    			newRedEdges[++rtick]=oldrtrace.clone();
    			// set index, store in both old and new 'redutil'
    			newRedEdges[rtick].redutil=oldrtrace.redutil=rtick;
    			newRedEdges[rtick].myEdge=
    					pdcel.edges[oldrtrace.myEdge.edgeIndx];
    			newRedEdges[rtick].myEdge.myRedEdge=newRedEdges[rtick];
    			oldrtrace=oldrtrace.nextRed;
    		} while(oldrtrace!=pdc.redChain);
    	}

    	if (pdc.redChain!=null) {
    		pdcel.redChain=newRedEdges[pdc.redChain.redutil];
    		for (int j=1;j<=redcount;j++) {
    			RedEdge reg=newRedEdges[j];
    			reg.nextRed=newRedEdges[reg.nextRed.redutil];
    			reg.prevRed=newRedEdges[reg.prevRed.redutil];
    			if (reg.twinRed!=null) 
    				reg.twinRed=newRedEdges[reg.twinRed.redutil];
    		}
    	}

    	// clone other things
    	if (pdc.stragglers!=null) {
    		pdcel.stragglers=new HalfLink();
    		Iterator<HalfEdge> fois=pdc.stragglers.iterator();
    		while (fois.hasNext()) {
    			pdcel.stragglers.add(pdcel.edges[fois.next().edgeIndx]);
    		}
    	}
    	if (pdc.layoutOrder!=null) {
    		pdcel.layoutOrder=new HalfLink();
    		Iterator<HalfEdge> heis=pdc.layoutOrder.iterator();
    		while (heis.hasNext()) {
    			pdcel.layoutOrder.add(pdcel.edges[heis.next().edgeIndx]);
    		}
    	}
    	if (pdc.sideStarts!=null) {
    		Iterator<RedEdge> ssis=pdc.sideStarts.iterator();
    		pdcel.sideStarts=new ArrayList<RedEdge>();
    		while (ssis.hasNext()) {
    			pdcel.sideStarts.add(newRedEdges[ssis.next().redutil]);
    		}
    	}
    	
    	pdcel.oldNew=null;
    	if (pdc.oldNew!=null && pdc.oldNew.size()>0) 
    		pdcel.oldNew=pdc.oldNew.clone();
    	
    	int alp=0;
    	if (pdc.alpha!=null)
    		alp=pdc.alpha.origin.vertIndx;
    	pdcel.setAlpha(alp, null, false);
    	int gam=0;
    	if (pdc.gamma!=null)
    		gam=pdc.gamma.origin.vertIndx;
    	pdcel.setGamma(gam);
    	
    	if (pdc.pairLink!=null && pdc.pairLink.size()>1) {
    		pdcel.pairLink=new PairLink();
    		Iterator<SideData> pis=pdc.pairLink.iterator();
    		pis.next(); // flush first null entry
    		pdcel.pairLink.add(null); // first null entry in new
    		while (pis.hasNext()) {
    			RedEdge rhe=null;
    			SideData old_sd=pis.next();
    			SideData sd=old_sd.clone();

    			// fix pointers
    			if ((rhe=sd.startEdge)!=null)
    				sd.startEdge=newRedEdges[rhe.redutil];
    			if ((rhe=sd.endEdge)!=null)
    				sd.endEdge=newRedEdges[rhe.redutil];
    				
    			pdcel.pairLink.add(sd);
    		}
    	}

    	pdcel.triData=null; // don't clone, as is generally temporary data
    	return pdcel;
    }
    
    /**
     * Return cclw linked open list of verts on the same bdry
     * component as 'v' using DCEL structure. Null on error or if
     * 'v' not bdry. 
     * @param p PackData
     * @param v int
     * @return new NodeLink, null on failure
     */
    public static NodeLink bdryCompVerts(PackData p,int v) {
    	if (!p.isBdry(v))
    		return null;
    	NodeLink list=new NodeLink(p);
    	HalfEdge firsttwin=p.packDCEL.vertices[v].halfedge.twin.next;
    	HalfEdge he=firsttwin;
    	int safety=p.nodeCount;
    	do {
    		safety--;
    		list.add(he.origin.vertIndx);
    		he=he.prev;
    	} while (he!=firsttwin && safety>0);
    	if (safety==0)
    		throw new CombException("bdry component doesn't seem to close up");
    	return list;
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
				if (rV.inSpoke[j] == null || 
						rV.inSpoke[j].twinRed != rV.redSpoke[j])
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
		RedEdge[] tmpRedSpoke = new RedEdge[num + 1];
		RedEdge[] tmpInSpoke = new RedEdge[num + 1];
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
//		boolean debug=false; // debug=true;
		HalfEdge he=null;
		prV.bdryFlag=1;
		ArrayList<Vertex> redList=new ArrayList<Vertex>();

		// rotate to get 'redSpoke' as first edge so we avoid worrying 
		//   about index wrapping around; finish if it's interior 
		//   (i.e. all red edges are paired and pasted) 
		if (prV.closed && CombDCEL.rotateMe(prV)==0) { 
			Vertex newV = new Vertex(prV.vertIndx); // this is parent's index
			newV.cloneData(prV);
			newV.redFlag=true;
			newV.bdryFlag=0;
			newV.halfedge=prV.redSpoke[0].myEdge;

			// fix 'origin's cclw and return, don't fill 'spokes'
			he=newV.halfedge;
			do {
				he.origin=newV;
				he = he.prev.twin;
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
						j = w - 1; // when we continue search, 
									// this will repeat the last direction
						k = prV.num + 1; // kick out of 'for'
					}
				}
				if (v == w) { // didn't find a match to form the fan
					CirclePack.cpb.errMsg("problem matching in/red spokes");
					throw new DCELException("'didn't find 'inSpoke' "+
					"with 'redSpoke'");
				}
				
				// The 'spokes' now form a fan about a new 'RedVertex', 
				// from 'redSpoke[v]' cclw to 'inSpoke[w]'. If 
				// 'redSpoke[w]' exists (and so was not pasted), then 
				// replace the last spoke in this fan by a new twin 
				// for the 'inSpoke' (prev/next readjusted later)
				Vertex newV = new Vertex(prV.vertIndx);
				newV.cloneData(prV);
				newV.redFlag=true;
				newV.halfedge=prV.redSpoke[v].myEdge;
				prV.redSpoke[v].myEdge.origin=newV;
				newV.spokes = new HalfEdge[w-v + 1];
				newV.bdryFlag = 1;
				newV.aim=-.1;
				if (prV.redSpoke[w]!=null) {
					// replace last of this fan
					HalfEdge new_w = new HalfEdge();
					new_w.face = new DcelFace();
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
					throw new DCELException("infinite look, 'redSpoke' for <" + 
							prV.redSpoke[v].myEdge.origin.vertIndx + " "
							+ prV.redSpoke[v].myEdge.twin.origin.vertIndx + ">");

				// The 'spokes' now form a fan about a new 'RedVertex', from
				// 'redSpoke[v]' cclw to 'inSpoke[w]'. If 'redSpoke[w]' exists
				// (it can't be pasted), then replace the last spoke in this
				// fan by a new twin for the 'inSpoke' (prev/next readjusted later)
				if (prV.redSpoke[w]!=null) {
					// replace last of this fan
					HalfEdge new_w = new HalfEdge();
					new_w.face = new DcelFace();
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
	   * bdry of 'pdc1', counterclockwise on bdry of 'pdc2'.
	   * 
	   * Note: pdc2 may be changed: e.g., 'vertIndx's, 'halfedge's,
	   * etc. However, the DCEL's are typically clones, so original 
	   * copy should remain in tact. 
	   * 
	   * TODO: not sure if red chains are essential. But if 
	   * both pdc1/2 have intact red chains, try to meld them.
	   * 
	   * Note: if n==1, then result may have disconnected interior,
	   * and 'fillInside' will not catalog all of the complex.
	   * 
	   * Note: If n<0, identify the full bdry components 
	   * (they must be the same length).
	   *  
	   * If not self-adjoin, then some 'Vertex's of 'pdc2' will be
	   * abandoned. Mark them by setting 'halfedge' to null and
	   * put new index in 'vutil'.
	   * 
	   * Call 'wrapAdjoin' to wrap up before returning; this
	   * creates 'oldNew'. Calling routine only needs to 'attachDCEL', 
	   * but may have some updating because of new indexing, saving
	   * lists, etc.
	   * 
	   * @param pdc1 PackDCEL (typically a clone)
	   * @param pdc2 PackDCEL (typically a clone)
	   * @param v1 int
	   * @param v2 int
	   * @param n int, number of edges (or negative)
	   * @return modified 'PackDCEL' or null or 'CombException' on error
	   */
	  public static PackDCEL adjoin(PackDCEL pdc1,PackDCEL pdc2,
			  int v1,int v2, int n) {
		  
		  // 'he1/2' to be identified: clw from 'v1', cclw from 'v2'
		  HalfEdge he1=pdc1.vertices[v1].halfedge.twin.next.twin;
		  HalfEdge he2=pdc2.vertices[v2].halfedge; // DCELdebug.printBouquet(pdc2);

		  // save some red edge // DCELdebug.printRedChain(pdc1.redChain);
		  RedEdge red1=he1.myRedEdge;
		  RedEdge red2=he2.myRedEdge;
		  
//		  if (red1==null || red2==null)
//			  throw new CombException("edges should hare red edges");

		  // store original vert indices in 'vutil'
		  for (int v=1;v<=pdc1.vertCount;v++) 
			  pdc1.vertices[v].vutil=v;
		  if (pdc2!=pdc1)
			  for (int v=1;v<=pdc2.vertCount;v++) 
				  pdc2.vertices[v].vutil=v;
		  
		  // get edge counts
		  int n1=he1.twin.getCycleCount();
		  int n2=he2.twin.getCycleCount(); // DCELdebug.printRedChain(pdc2.redChain);

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
						  Vertex vert=nxtedge.origin;
						  nxtedge=nxtedge.next;
						  CombDCEL.zipEdge(pdc1,vert);
					  }
					  return CombDCEL.wrapAdjoin(pdc1, pdc2);
				  }
					  
				  // need to check suitability
				  if (he2.twin.prev.prev==he1.twin)
					  throw new CombException(
							  "self-adjoin: given vertices too close");
				  HalfEdge nxtedge=he1.twin;
				  int indx2=0;
				  for (int j=1;(j<n1 && indx2==0);j++) {
					  nxtedge=nxtedge.next;
					  if (nxtedge==he2.twin)
						  indx2=j;
				  }
					  
				  // not enough edges
				  if ((indx2+1)<2*n || indx2==2*n)
					  throw new CombException(
							  "self-adjoin: not enough edges");
					  
				  // can we zip the other direction from common vert?
				  if ((indx2+1)==2*n) {
					  
					  // find the clw edge from common vert
					  nxtedge=he2.twin;
					  for (int j=1;j<n;j++) {
						  nxtedge=nxtedge.prev;
					  }
					  
					  for (int j=1;j<=n;j++) {
						  Vertex cmVert=nxtedge.origin;
						  nxtedge=nxtedge.next;
						  CombDCEL.zipEdge(pdc1,cmVert);
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
		  			// DCELdebug.printRedChain(pdc2.redChain);
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
		  oldvert1.vutil=vert1.vertIndx; // vutil holds new index
		  he=he2.twin;
		  do {
			  he.origin=vert2;
			  he=he.twin.next; // clw
		  } while(he!=he2.twin);
		  oldvert2.halfedge=null; // abandoned
		  oldvert2.vutil=vert2.vertIndx; // vutil holds new index
		  vert2.halfedge=he2.twin.prev.twin;

		  // fix things pointing to orphaned he1.twin, he2.twin
		  he1.twin.prev.next=he2.twin.next;
		  he2.twin.next.prev=he1.twin.prev;
		  
		  he1.twin.next.prev=he2.twin.prev;
		  he2.twin.prev.next=he1.twin.next;
		
		  // fix h1/2 as twins
		  he2.twin=he1;
		  he1.twin=he2;
		  he1.myRedEdge=null;
		  he2.myRedEdge=null;
		  
		  // meld the red chains; red1/2 become orphans
		  if (red1!=null && red2!=null) {
			  if (red1==pdc1.redChain)
				  pdc1.redChain=red1.nextRed;
			  red1.prevRed.nextRed=red2.nextRed;
			  red2.nextRed.prevRed=red1.prevRed;
			  
			  red1.nextRed.prevRed=red2.prevRed;
			  red2.prevRed.nextRed=red1.nextRed;
		  
			  red1.twinRed=null;
			  red2.twinRed=null;
		  }

		  // now zip the rest   // pdc1.vertices[4].getFlower();
		  PackDCEL ansDCEL=null;
		  try {
			  HalfEdge nxtedge=vert2.halfedge;
			  for (int j=2;j<=n;j++) {
				  HalfEdge hold=nxtedge.twin.prev.twin;
				  CombDCEL.zipEdge(pdc1,nxtedge.origin);
				  nxtedge=hold;
			  }
			  ansDCEL=CombDCEL.wrapAdjoin(pdc1, pdc2);
		  } catch (Exception ex) {
			  throw new CombException("zipping problem: "+ex.getMessage());
		  }
		  // DCELdebug.printRedChain(ansDCEL.redChain);
		  return ansDCEL;   // ansDCEL.vertices[10].getFlower();
	  }
	  
	  /**
	   * Special helper routine to wrap up 'd_adjoin' 
	   * when ready to return results, which are in 
	   * 'pdc1'. This re-indexes 'vertices' and forms
	   * new 'vertices'. In processing, orphaned 
	   * vertices are indicated by 'halfedge'=null. 
	   * Build 'oldNew' to connect old and new vertex 
	   * indices (entry only when old/new differ). 
	   * Note that if pdc2!=pdc1, then no pdc1 
	   * vertices are orphaned, so pdc2 vertices 
	   * numbering have been adjusted to start with 
	   * pdc1.vertCount+1. In both pdc1 and pdc2,
	   * 'vutil' still holds the original index.
	   * @param pdc1 PackDCEL
	   * @param pdc2 PackDCEL, may be same as pdc1
	   * @return same PackDCEL
	   */
	  public static PackDCEL wrapAdjoin(PackDCEL pdc1,PackDCEL pdc2) {
		  pdc1.oldNew=new VertexMap();
		  ArrayList<Vertex> v_array=new ArrayList<Vertex>();
		  int vtick=0;
		  
		  // start indexing with surviving pdc1 vertices;
		  for (int v=1;v<=pdc1.vertCount;v++) {
			  Vertex vert=pdc1.vertices[v];
			  if (vert.halfedge!=null) { 
				  ++vtick;
				  v_array.add(vert);
				  // if pdc2!=pdc1, should have vtick=v=vert.vutil
				  if (vtick!=vert.vutil && vert.vutil!=0)
					  pdc1.oldNew.add(new EdgeSimple(vert.vutil,vtick));
//System.out.println(" add "+new EdgeSimple(vtick,vert.vutil));					  
			  }
		  }
			 
		  // new indices for any orphaned pdc1 verts
		  for (int v=1;v<=pdc1.vertCount;v++) {
			  Vertex vert=pdc1.vertices[v];
			  if (vert.halfedge==null) { 
				  // 'vutil' is orig new index, but may have changed
				  int new_indx=pdc1.oldNew.findW(vert.vutil); 
				  if (new_indx==0)
					  new_indx=vert.vutil;
				  if (new_indx!=vert.vertIndx)
					  pdc1.oldNew.add(new EdgeSimple(vert.vertIndx,new_indx));
//System.out.println(" add for orphaned "+new EdgeSimple(new_indx,vert.vertIndx));
			  }
		  }

		  // not self-adjoin? index the surviving pdc2 verts
		  if (pdc2!=pdc1) {
			  vtick=pdc1.vertCount; // start here
			  for (int v=1;v<=pdc2.vertCount;v++) {
				  Vertex vert=pdc2.vertices[v];
				  if (vert.halfedge!=null) { // not abandoned?
					  ++vtick;
					  if (vtick!=vert.vutil)
						  pdc1.oldNew.add(new EdgeSimple(vert.vutil,vtick));
					  vert.vutil=vtick;
					  v_array.add(vert);
				  }
			  } // DCELdebug.printRedChain(pdc2.redChain);
			  
			  // oldNew for orphaned verts: 
			  //  NOTE: "old" = original pdc2 index = vertIndx - pdc1.vertCount
			  for (int v=1;v<=pdc2.vertCount;v++) {
				  Vertex vert=pdc2.vertices[v];
				  if (vert.halfedge==null) { // was abandoned
					  int newindx=vert.vutil; // pdc1 vert this was identified with
					  int orig2Indx=vert.vertIndx-pdc1.vertCount;
					  pdc1.oldNew.add(new EdgeSimple(orig2Indx,newindx));
				  }
			  }
		  }
		  
		  // create new 'vertices' with new 'vertIndx's
		  pdc1.vertCount=v_array.size(); // this should equal 'vtick'
		  if (pdc1.vertCount>=pdc1.vertices.length-1)
			  pdc1.alloc_vert_space(pdc1.vertCount+10,false);
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
	   * Given 'HalfLink' list of edges, flip as many as possible.
	   * Each time we get a new 'HalfEdge', remove it from list.
	   * Calling routine must complete combinatorics  
	   * @param pdcel PackDCEL
	   * @param hlink HalfLink
	   * @return count (even if ultimately fails)
	   */
	  public static int flipEdgeList(PackDCEL pdcel,HalfLink hlink) {
		  if (hlink==null || hlink.size()==0)
			  return 0;
		  int count=0;
		  while (hlink.size()>0) {
			  HalfEdge he=hlink.remove(0);
			  HalfEdge new_edge=RawManip.flipEdge_raw(pdcel,he);
			  
			  // success?
			  if (new_edge==null) {
				  CirclePack.cpb.errMsg("flipping edges failed with edge "+he);
				  break;
			  }
			  count++;
		  }
		  return count;
	  }

	  /**
	   * Flip an interior edge. In a triangulation, an interior edge is
	   * shared by two faces. To "flip" the edge means to remove it and
	   * replace it with the other diagonal in the union of those faces. 
	   * The number of faces, edges, and vertices is not changed, but 
	   * the calling routine must update combinatorics. 'redProblem' 
	   * is instantiated by the calling routine; if true on return, 
	   * then the flipped edge was in the red chain, requiring extra 
	   * work in the update. Faces are reused.
	   * @param pdcel PackDCEL
	   * @param hedge HalfEdge
	   * @param redProblem Boolean, return true if red Chain is disrupted
	   * @return int 1 on success
	   */
	  public static int flipEdge(PackDCEL pdcel,HalfEdge hedge,
			  Boolean redProblem) {
		  redProblem=false;
		  
		  // if 'hedge' is bdry or faces not triangles, error
		  if (pdcel.isBdryEdge(hedge) || 
				  hedge.next.next.next!=hedge ||
				  hedge.twin.next.next.next!=hedge.twin)
			  return 0;
		  if (hedge.myRedEdge!=null || hedge.twin.myRedEdge!=null) { // in redchain
			  redProblem=true;
		  }
		  DcelFace leftf=hedge.face;
		  leftf.edge=hedge;
		  DcelFace rightf=hedge.twin.face;
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
	   * Given 'pdcel' must be a topological torus with an 
	   * existing 'redChain'. Generically a red chain will
	   * have 3 side pairings; this routine finds a new red 
	   * chain with just two and returns the linked list: 
	   * CAUTION: 'myEdges' are set, but not 'myRedEdge's,
	   * in order that old data can be cleared out on return. 
	   * The calling routine must also call 'fillInside', 
	   * repack, layout, etc. (see former 'ProjStruct.torus4layout')
	   * @param pdcel PackDCEL
	   * @return RedEdge, linked list
	   */
	  public static RedEdge torus4Sides(PackDCEL pdcel) {

		  try {
			  if (pdcel.redChain==null) {
				  CombDCEL.redchain_by_edge(pdcel, null, null, false);
				  CombDCEL.fillInside(pdcel); // need 'pairLink'
			  }
		  } catch (Exception ex) {
			  throw new CombException("Failed to find or build a red "+
					  "chain for this torus");
		  }

		  // From red chain, get short closed non-separating path
		  HalfLink path=PathUtil.getNonSeparating(pdcel);
		  
		  // now choose 'seededge': want edge of 'path' whose
		  //   origin has high degree and verts to left/right
		  //   are not on 'path'.
		  HalfEdge seededge=null;
		  int bigdegree=-1;
		  
		  // mark vertices along 'path'
		  int[] phits=new int[pdcel.vertCount+1];
		  Iterator<HalfEdge> his=path.iterator();
		  while (his.hasNext()) {
			  phits[his.next().origin.vertIndx]=1;
		  }

		  his=path.iterator();
		  while (his.hasNext()) {
			  HalfEdge he=his.next();
			  int cf=pdcel.countFaces(he.origin);
			  int vl=he.next.next.origin.vertIndx;
			  int vr=he.twin.next.next.origin.vertIndx;
			  if (cf>bigdegree && phits[vl]==0 && phits[vr]==0) {
				  bigdegree=cf;
				  seededge=he;
			  }
		  }
		  
		  if (seededge==null) 
			  throw new CombException("couldn't find qualifying 'seededge'");

		  // rotate 'path' to start with 'seededge'
		  path=HalfLink.rotateMe(path,seededge);

		  // get 'cutPath' starting and ending at 'seededge.origin'
		  //   and otherwise disjoint from 'path'.
		  HalfLink cutPath=PathUtil.getCutPath(pdcel,path,seededge);

		  // Form new red chain: path:cutPath:path-:cutPath-
		  RedEdge newChain=new RedEdge(seededge);
		  RedEdge newR=newChain;
		  
		  his=path.iterator();
		  his.next(); // already got 'seededge'
		  RedEdge pastR;
		  while (his.hasNext()) {
			  pastR=newR;
			  HalfEdge he=his.next();
			  newR=new RedEdge(he);
			  pastR.nextRed=newR;
			  newR.prevRed=pastR;
		  }
		  
		  // link in 'cutPath'
		  his=cutPath.iterator();
		  while (his.hasNext()) {
			  pastR=newR;
			  HalfEdge he=his.next();
			  newR=new RedEdge(he);
			  pastR.nextRed=newR;
			  newR.prevRed=pastR;
		  }
		  
		  // link in reverse of 'path'
		  path=HalfLink.reverseLink(path);
		  his=path.iterator();
		  while (his.hasNext()) {
			  pastR=newR;
			  HalfEdge he=his.next().twin;
			  newR=new RedEdge(he);
			  pastR.nextRed=newR;
			  newR.prevRed=pastR;
		  }
		  
		  // link in reverse of 'cutPath'
		  cutPath=HalfLink.reverseLink(cutPath);
		  his=cutPath.iterator();
		  while (his.hasNext()) {
			  pastR=newR;
			  HalfEdge he=his.next().twin;
			  newR=new RedEdge(he);
			  pastR.nextRed=newR;
			  newR.prevRed=pastR;
		  }
		  
		  // close up 
		  newR.nextRed=newChain;
		  newChain.prevRed=newR;
		  
// debugging		  
//		  DCELdebug.printRedChain(newChain);
		  
		  return newChain;
	  }

	/**
	 * Modify 'pdcel' by zipping together the two bdry edges 
	 * from 'vert'. This means the downstream edge is twinned
	 * with the upstream edge, and so a vertex may be orphaned.
	 * The redchain should remain in tact, though it will be 
	 * discarded if result is a sphere (e.g., in 2-edge or
	 * 4-edge bdry cases).
	 * 
	 * Typically orphan downstream vertex, unless in the
	 * special 2-edge bdry case. Set 'halfedge' to null for
	 * the orphaned vertex and show new vertIndx in its 'vutil'.
	 * Calling routine is responsible for keeping track of 
	 * this (see, e.g., 'wrapAdjoin') and for completing updates 
	 * of 'pdcel'.
	 * 
	 * @param pdcel PackDCEL
	 * @param vert Vertex
	 * @return int, -1, or 0: if positive, then this is the 
	 * 	  one orphaned vertex; -1 means no orphaned vertex 
	 *    (i.e., 2-edge bdry that gets closed up), 
	 *    and 0 for error.
	 */
	public static int zipEdge(PackDCEL pdcel,Vertex vert) {
		if (vert.bdryFlag==0) // nothing to zip
			return 1;
		if (!vert.redFlag)
			throw new CombException("Vertex "+vert.vertIndx+
					" is not on the red chain");
	
		// edges to be kept; these become twins
		HalfEdge outedge=vert.halfedge;
		HalfEdge inedge=outedge.twin.next.twin;
		
		// exception if 'vert' will end up with only two nghbs
		if (outedge.prev.twin==inedge.next) {
			throw new CombException("vert "+vert.vertIndx+
					" would have only two nghbs");
		}
		
		RedEdge redout=outedge.myRedEdge;
		RedEdge redin=inedge.myRedEdge;
		
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
				return -1;
			}
			
			// else, the bdry component may be a bubble in red chain
			if (redout.prevRed!=redin && redout.nextRed!=redin) {
				return -1;
			}
			
			// else, red chain is shrunk in one or other direction
			RedEdge upred; // set in case of further red shrinkage
			RedEdge downred;
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
					return -1;
				}
				upred=upred.prevRed;
				downred=downred.nextRed;
				upred.nextRed=downred;
				downred.prevRed=upred;
			}
			return -1; // no orphaned vertex
		}
		
		// 4-edge bdry component?
		if (outedge.twin.prev.origin==inedge.twin.next.twin.origin) {
			HalfEdge pre_edge=inedge.twin.next.twin;
			HalfEdge post_edge=outedge.twin.prev.twin;
			RedEdge pre_red=pre_edge.myRedEdge;
			RedEdge post_red=post_edge.myRedEdge;
			Vertex oppVert=pre_edge.origin;
			Vertex leftVert=outedge.twin.origin;
			Vertex rightVert=inedge.origin;
			
			// orphan 'leftVert'
			HalfEdge firstspoke=outedge.twin;
			HalfEdge he=firstspoke;
			do {
				he.origin=rightVert;
				he=he.twin.next; // clw
			} while (he!=firstspoke);
			leftVert.halfedge=null;
			leftVert.vutil=rightVert.vertIndx;
			
			// vertices are interior
			vert.bdryFlag=0;
			rightVert.bdryFlag=0;
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
				return leftVert.vertIndx; // orphaned vertex
			}
			
			// check for red contraction at 'vert' and/or 'oppVert'
			//   and subsequent possible contraction
			RedEdge upred=redout.prevRed; 
			RedEdge downred=redin.nextRed;
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
				
			// if contraction at both 'vert' and 'oppVert',
			//   then there may be more contraction
			if (!oppVert.redFlag && !vert.redFlag) {
				while (upred.twinRed==downred) { // collapse an edge
					downred.myEdge.origin.redFlag=false;
					upred.myEdge.myRedEdge=null;
					downred.myEdge.myRedEdge=null;
					if (upred.prevRed==downred) { // end? must be sphere
						upred.myEdge.origin.redFlag=false;
						pdcel.redChain=null;
						return leftVert.vertIndx;
					}
					upred=upred.prevRed;
					downred=downred.nextRed;
					upred.nextRed=downred;
					downred.prevRed=upred;
				}
			}
			return leftVert.vertIndx;
		}
		
		// else zip up just first edge
		Vertex savevert=redin.myEdge.origin;
		HalfEdge downstream=redout.myEdge.twin.prev.twin;
		HalfEdge upstream=redin.myEdge.twin.next.twin;
		downstream.twin.next=upstream.twin;
		upstream.twin.prev=downstream.twin;
		savevert.halfedge=downstream;

		
		// reset origin for spokes of vertex being orphaned
		HalfEdge he=redout.nextRed.myEdge;
		Vertex xvert=he.origin;
		do {
			he.origin=savevert;
			he=he.prev.twin; // cclw
		} while (he!=redout.nextRed.myEdge);
		
		// orphan vertex 'xvert'
		xvert.halfedge=null; 
		xvert.vutil=savevert.vertIndx;
	
		// zip base becomes interior
		vert.bdryFlag=0;
		vert.aim=2.0*Math.PI;
		
		// identify
		outedge.twin=inedge;
		inedge.twin=outedge;
	
		// we may contract red chain
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
		
		return xvert.vertIndx; // this vertex orphaned
	}
	
	/** 
	 * Check for an "axis-extended" 'HalfLink' from v to w 
	 * starting with spoke 'arrow' at v and with length no
	 * more than 'lgth'. Exclude paths through bdry verts 
	 * (aside for first/last). "axis-extended" means vertices 
	 * are even degree and edges extend through axis (the
	 * same number of edges on each side of the path). 
	 * Return null on failure.
	 * Note: could have 'w' equal start index v of 'arrow', 
	 * but we reach 'w' successfully by length 'lgth' only 
	 * if we come in on axis shared with outgoing 'arrow'.
	 * @param arrow HalfEdge
	 * @param w int
	 * @param lgth int
	 * @param hexflag boolean, true; only deg 6 verts along path
	 * @return HalfLink, null on failure
	*/
	public static HalfLink axisExtended(HalfEdge arrow,int w,
			int lgth,boolean hexflag) {
		int v=arrow.origin.vertIndx;
		HalfLink link=new HalfLink();
		link.add(arrow);
		if (arrow.twin.origin.vertIndx==w)
			return link;
		if (lgth==1) { // 'lgth' 1 means 'arrow' must point to w
			return null;
		}
		HalfEdge nxtarrow=arrow;
		int tick=1;
		while (tick<lgth) {
			nxtarrow=Vertex.oppSpoke(nxtarrow.twin,hexflag);
			if (nxtarrow==null) // e.g., no "axis"
				return null;
			Vertex stv=nxtarrow.twin.origin;
			
			// reached w?
			if (stv.vertIndx==w) {
				if (w!=v) { // done
					link.add(nxtarrow);
					return link;
				}
				// if w=v there are side condition; if not met go on unless
				//     v is a bdry vertex, in that case, stop here
				if (w==v) { // lineup condition and interior, else continue
					if (arrow.origin.bdryFlag>0) {
						link.add(nxtarrow);
						return link;
					}
					if (Vertex.oppSpoke(nxtarrow,hexflag)==arrow) {
						if (hexflag && arrow.origin.getNum()!=6)
							return null;
						link.add(nxtarrow);
						return link;
					}
				}
			}

			int num=stv.getNum();
			if (hexflag) {  // reached interior non-hex or bdry non-halfhex
				if ((stv.bdryFlag==0 && num!=6) || (stv.bdryFlag>0 && num!=3))
					return null;
			}
			
			// not eliminated, so add and continue
			link.add(nxtarrow);
			tick++;
		}
		return link;
	}
	
	/**
	 * Find shortest axis_extended HalfLink from 'basevert' ending
	 * at 'w' through interior vertices (except for first/last)
	 * and no longer than 'lgth'. If 'hexflag', then intermediate
	 * verts must be hex.
	 * Note: 'basevert' may have index v,
	 * @param basevert Vertex
	 * @param w int
	 * @param lgth int
	 * @param hexflag boolean, true then through hex vertices only
	 * @return HalfLink, null on failure
	 */
	public static HalfLink shootExtended(Vertex basevert,int w,
			int lgth,boolean hexflag) {
		ArrayList<HalfLink> lists=new ArrayList<HalfLink>();
		
		// get 'HalfLink' for each spoke of 'basevert'
		HalfEdge arrow=basevert.halfedge;
		do {
			HalfLink link=CombDCEL.axisExtended(arrow,w,lgth,hexflag);
			if (link!=null)
				lists.add(link);
			arrow=arrow.prev.twin; // cclw spoke
		} while (arrow!=basevert.halfedge);
		
		if (lists.size()==0)
			return null;
		HalfLink bestlink=lists.remove(0);
		int bestLength=bestlink.size();
		Iterator<HalfLink> lis=lists.iterator();
		while (lis.hasNext()) {
			HalfLink nxtlink=lis.next();
			if (nxtlink.size()>bestLength) {
				bestLength=nxtlink.size();
				bestlink=nxtlink;
			}
		}
		return bestlink;
	}
	
	/**
	 * Convert 'NodeLink' list of vertices into a 'HalfLink' 
	 * chain of connected edges. (If 'hexFlag' is true, 
	 * then "hex-extended" edges, meaning string of verts 
	 * between v and w are interior and degree 6. First
	 * and last vertices need not be of degree 6.)
	 * @param pdcel PackDCEL
	 * @param vertlist NodeLink
	 * @param hexFlag boolean, true, then want "hex-extended" edges
	 * @return
	 */
	public static HalfLink verts2Edges(PackDCEL pdcel,NodeLink vertlist,
			boolean hexFlag) {
		HalfLink ans=new HalfLink(pdcel.p);
		if (vertlist==null || vertlist.size()==0) 
			return ans;
		Iterator<Integer> vlist=vertlist.iterator();
		int endv=(Integer)vlist.next();
		while (vlist.hasNext()) {
			int nextv=(Integer)vlist.next();
			
			// eat any duplicates of 'endv' if 'hexFlag' false
			while (vlist.hasNext() && (!hexFlag && nextv==endv)) {
				nextv=(Integer)vlist.next();
			}
			
			HalfEdge petal=pdcel.findHalfEdge(endv,nextv);
			if (hexFlag) { // look for/use axis-extended edges
				if (petal!=null)
					ans.add(petal);
				else {
					Vertex basevert=pdcel.vertices[endv];
					HalfLink hlink=
						CombDCEL.shootExtended(basevert,nextv,32,hexFlag);
					if (hlink!=null && hlink.size()>0) 
						ans.abutMore(hlink);
				}
			}
			else if (petal!=null) {
				ans.add(petal);
			}

			// TODO: don't default to geodesic; and don't yet have DCEL version
//			else { 
//				ans.abutMore(EdgeLink.getCombGeo(p,
//					new NodeLink(p,endv),new NodeLink(p,nextv),null));
//			}
				
			endv=nextv;
		} // end of while
		return ans;
	}
	  
	/**
	 * Slit open along given edges via 'cookie' method.
	 * 'hlink' must form a chain; start and/or end may 
	 * be boundary, rest must be interior. If the complex 
	 * is disconnected, the surviving portion is determined
	 * by 'pdcel.alpha'. The new bdry segment is the 
	 * oriented bdry from returned {firstEnd,lastEnd}.
	 * Errors may damage pdcel.
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink
	 * @return int[2]={first,last}, null on error
	 */
	public static int[] slitComplex(PackDCEL pdcel,HalfLink hlink) {
		  if (hlink==null || hlink.isEmpty() || pdcel.p==null)
			  return null;
		  
		  // check conditions and linkage
		  HalfEdge he;
		  if (hlink.size()==1) {
			  he=hlink.get(0);
			  if (he.origin.bdryFlag>0 && he.twin.origin.bdryFlag>0) {
				  throw new CombException("usage: 'slit' cannot be done "+
						  "for a single edge with both ends on the bdry.");
			  }
		  }
		  Iterator<HalfEdge> his=hlink.iterator();
		  he=his.next();
		  NodeLink verts=new NodeLink();
		  Vertex startV=he.origin;
		  verts.add(startV.vertIndx);
		  Vertex endV=he.next.origin;
		  while(his.hasNext()) {
			  he=his.next();
			  startV=he.origin;
			  verts.add(startV.vertIndx);
			  if (startV.bdryFlag>0)
				  throw new DataException("usage: slit; intermediate "+
						  "vertices must be interior");
			  if (he.origin!=endV) 
				  throw new DataException("usage: slit; HalfLink must "+
						  " form a contiguous chain");
			  endV=he.next.origin;
		  }
		  verts.add(endV.vertIndx);

		  int firstEnd=-1;
		  int lastEnd=-1;
		  
		  // if start is interior, end bdry, then reverse chain
		  if (startV.bdryFlag==0 && endV.bdryFlag!=0) {
			  hlink=HalfLink.reverseLink(hlink);
			  hlink=HalfLink.reverseElements(hlink);
		  }

		  // proceed via "cookie" approach?
		  startV=hlink.get(0).origin;
		  firstEnd=startV.vertIndx;
		  if (startV.bdryFlag>0)
			  firstEnd=pdcel.vertCount+1; // this will be the clone's index
		  lastEnd=hlink.getLast().twin.origin.vertIndx;
		  redchain_by_edge(pdcel,hlink,pdcel.alpha,false);
		  fillInside(pdcel);
		  int[] ans=new int[2];
		  ans[0]=firstEnd;
		  ans[1]=lastEnd;
		  return ans;
	}

	  /**
	   * Add a layer of nodes to bdry segment from vertex v1 to v2. 
	   * Three modes:
	   * 
	   * TENT: add one-on-one layer, a new bdry vert for each edge 
	   * between v1 and v2. Unless v1==v2, v1 and v2 remain as 
	   * bdry vertices.
	   * 
	   * DEGREE: add nghb's to make verts from v1 to v2 (inclusive) 
	   * interior with degree d. Note that v1 gets at least one new
	   * nghb, so this could exceed degree d. If v1==v2 or v1 is 
	   * nghb of v2, do whole bdry component.
	   * 
	   * DUPLICATE: attach "square" face with bary center to 
	   * each edge between v1 and v2. Unless v1==v2, v1 and v2 
	   * remain on bdry.
	   * 
	   * Set centers and radii of new vertices based on reference
	   * vertices.
	   * 
	   * Calling routine checks v1,v2 and updates combinatorics.
	   * 
	   * @param pdcel  PackDCEL
	   * @param mode   int, how to add: 0=TENT, 1=DEGREE, 2=DUPLICATE
	   * @param degree int
	   * @param v1     int, start bdry vert
	   * @param v2     int, end bdry vert
	   * @return int, count of added vertices
	   */
	  public static int addlayer(PackDCEL pdcel, int mode, 
			  int deg, int v1, int v2) {
		  if (!pdcel.p.onSameBdryComp(v1,v2)) 
			  throw new CombException(
					  v1+" and "+v2+" are not on the same bdry component");

		  int count = 0;
		  HalfLink addedEdges=new HalfLink();
		  HalfEdge edge = pdcel.vertices[v1].halfedge.twin;
		  if (mode == CPBase.TENT) {
			  // int lastv=pdcel.vertices[v2].halfedge.twin.next.twin.origin.vertIndx;
			  
			  // tent over first edge
			  Vertex newV=RawManip.addVert_raw(pdcel,edge);
			  addedEdges.add(edge);
			  int nextv=edge.origin.vertIndx;
			  while (nextv != v2) {
				  edge = pdcel.vertices[nextv].halfedge.twin;
				  newV=RawManip.addVert_raw(pdcel,edge);
				  if (newV==null)
					  throw new CombException("failed to add tent to "+edge);
				  addedEdges.add(edge);
				  RawManip.enfold_raw(pdcel,nextv);
				  nextv=edge.origin.vertIndx;
				  count++;
			  }
			  if (v1 == v2) {
				  RawManip.enfold_raw(pdcel, v1);
				  count++;
			  }
		  }
		  else if (mode == CPBase.DEGREE) {
			  int origCount=pdcel.vertCount;
			  Vertex vert = pdcel.vertices[v1];
			  if (v2 == v1) // move v2 upstream from v1
				  v2 = vert.halfedge.twin.next.twin.origin.vertIndx;
			  int v = v1;
			  int nextv = v;

			  // start with tent over clw edge 
			  edge=pdcel.vertices[v].halfedge.twin.next;
			  if (RawManip.addVert_raw(pdcel, edge)==null)
				  return 0;
			  addedEdges.add(edge);
			  count++;

			  // go until you finish v2
			  do {
				  v = nextv; // get 'v' and downstream bdry nghb 'nextv'
				  nextv = pdcel.vertices[v].halfedge.twin.origin.vertIndx;
				  int need = deg - pdcel.countPetals(v);
				  for (int i = 1; i <= need; i++) {
					  edge=pdcel.vertices[v].halfedge.twin.next;
					  if (RawManip.addVert_raw(pdcel,edge)==null)
						  return count;
					  addedEdges.add(edge);
					  count++;
				  }
				  RawManip.enfold_raw(pdcel,v);
			  } while (nextv<=origCount && v!=v2 && pdcel.redChain!=null);
		  }
		  else if (mode == CPBase.DUPLICATE) { // DCELdebug.redConsistency(pdcel);
			  
			  // if doing full bdry, stop before last box
			  boolean close=(v1==v2);
			  if (close)
				  v2=edge.next.twin.origin.vertIndx;
			  
			  int v=edge.origin.vertIndx;
			  
			  // add full box to 'edge'
			  RawManip.addBox_raw(pdcel,edge,true);
 			  addedEdges.add(edge);
 			  addedEdges.add(edge.next.twin);
 			  addedEdges.add(edge.prev.twin);
 			  count++;
 			  while (v!=v2) {
 				  edge=pdcel.vertices[v].halfedge.twin;
 				  v=edge.origin.vertIndx;
 				  RawManip.addBox_raw(pdcel,edge,false);
 	 			  addedEdges.add(edge);
 	 			  addedEdges.add(edge.next.twin.prev.twin);
 	 			  count++;
 			  }
 			  
 			  // closed?
 			  if (close) {
 				  edge=pdcel.vertices[v].halfedge.twin;
 				  Vertex baryV=RawManip.addVert_raw(pdcel,edge);
 	 			  addedEdges.add(edge);
 	 			  RawManip.enfold_raw(pdcel,edge.origin.vertIndx);
 	 			  RawManip.enfold_raw(pdcel,edge.twin.origin.vertIndx);
 	 			  RawManip.enfold_raw(pdcel,baryV.vertIndx);
 				  count++;
 			  }
		  }
		  if (count>0) { // store successive new cent/rad
			  pdcel.addedVertData(addedEdges);
		  }
		  return count;
	  }
	  
} // end of 'CombDCEL'

/**
 * Inner class: temporary object to hold 'redSpoke' and 'inSpoke' data
 * until appropriate 'Vertex's are created and processed.
 * @author kstephe2, 8/2020
 */
class PreRedVertex extends Vertex {
	public RedEdge[] redSpoke;    // outgoing 'RedEdge'
	public RedEdge[] inSpoke;     // incoming 'RedEdge'
	public int num;
	boolean closed;   // if true, then original flower was closed
	
	// Constructor
	public PreRedVertex(int v) {
		super(v);
		closed=false;
		num=-1;
	}
}
