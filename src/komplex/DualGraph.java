package komplex;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.LayoutException;
import exceptions.ParserException;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import packing.PackData;
import packing.RedChainer;
import util.BuildPacket;

/**
 * For creating/manipulating combinatoric information for
 * the dual graphs G for circle packing complexes K, i.e., the
 * vertices of G correspond to faces of K, an edge of G, <f,g>
 * indicates that faces f and g share an edge, and the 
 * (polygonal) faces of G correspond to the vertices of K.
 * 
 * * Now (2/2009) the aim is building spanning trees for G
 *   with static methods.
 *   
 * * Also (6/2010) building plottrees, dual trees showing the
 * order in which faces are laid down in drawing order.
 * (Note that a plottree will generally not pick up all the
 * nodes of the dual graph; some nodes are not needed since
 * the three circles of that face have already been laid out
 * by other faces.)
 * 
 * * Also (12/2010), treeTrim, which modifies a tree by removing
 * designated edges, and reattaching the cutoff tree by 
 * introducing some non-proscribed edge. Main idea is to be 
 * able to easily modify a plottree to get a new plot tree with
 * particular properties --- eg. to get a particularly nice
 * layout of a torus.
 * @author kens
 *
 */
public class DualGraph {

	/**
	 * Build dual graph of the packing p starting with 'startface'.
	 * Should "spiral" out by generations, should have no repeats,
	 * doesn't cross poison edges if any are specified 
	 * @param p PackData
	 * @param startface int
	 * @param poison EdgeLink, poison, don't cross (may be null)
	 * @return GraphLink
	 */
	public static GraphLink buildDualGraph(PackData p,int startface,EdgeLink poison) {
		GraphLink dlink=new GraphLink();
		if (startface<=0 || startface>p.faceCount)
			startface=p.firstFace;
		if (startface==0) // default to 1
			startface=1;
		int startvert=p.faces[startface].vert[0];
		NodeLink nextNodes=new NodeLink(p,startvert);
		NodeLink currNodes=new NodeLink(p);
		int []gen=new int[p.nodeCount+1];
		gen[startvert]=1;
		int tick=1;
		while (nextNodes.size()>0) {
			tick++;
			currNodes=nextNodes;
			nextNodes=new NodeLink(p);
			Iterator<Integer> cl=currNodes.iterator();
			while (cl.hasNext()) {
				int v=cl.next();
				for (int j=0;j<(p.countFaces(v)+p.getBdryFlag(v));j++) {
					int w=p.kData[v].flower[j];
					if (gen[w]==0) {
						gen[w]=tick;
						nextNodes.add(w);
					}
					if ((gen[w]==gen[v] && w>v || gen[w]>gen[v]) &&
							!EdgeLink.ck_in_elist(poison,v,w)) {
						EdgeSimple edge=p.dualEdge(v,w);
						if (edge!=null) 
							dlink.add(edge);
					}
				}
			}
		}
		if (dlink.size()==0) return null;
		return dlink;
	}

	/**
	 * Build a GraphLink representing a directed spanning tree for the dual
	 * graph based on the stored face drawing order. The resulting root is form
	 * <0,f> where f is the 'firstFace'.
	 * 
	 * A spanning tree reaches ALL faces reached in the drawing order (each 
	 * once and only once). In particular:
	 * 
	 * 1. It picks up faces omitted in the drawing order because they weren't
	 * needed for drawing circles. This can be overridden by setting 
	 * 'plottree' to true, so you only get the faces needed to ensure all
	 * circles get placed.
	 * 
	 * 2. It uses nghb faces: in drawing order, a circle can be associated with
	 * a face because two of its circles are in place --- but they may be in
	 * place without the nghb face itself being in the tree.
	 * 
	 * 3. Gets ALL the faces of the red chain. In particular, the red chain is a
	 * connected tree starting at 'firstRedFace'. Its only branching is due to
	 * blue faces.
	 * 
	 * @param p PackData
	 * @param plottree boolean: true, just want plot tree, sufficient for layout.
	 * @return GraphLink
	 */
	public static GraphLink easySpanner(PackData p, boolean plottree) {
		GraphLink dlink = new GraphLink();

		// boolean debug=true;
		boolean debug = false;
		if (debug)
			System.err.println("Debugging easySpanner");

		int ftick = 1; // face counter
		int ctick = 1; // circle counter

		// keep track of faces; when positive, indicates number
		//    of its vertices in place; set to -ftick when in tree,
		//    with ftick indicating the order it was put in.
		int[] fck = new int[p.faceCount + 1];

		// keep track of vertes that have had a face placed
		int[] cck = new int[p.nodeCount + 1];

		// start by processing 'firstFace'; it becomes root
		Face nxtFace = p.faces[p.firstFace];
		for (int j = 0; j < 3; j++) {
			// mark its circles as having a face laid out
			int v = nxtFace.vert[j];
			cck[v] = ctick++;

			if (debug)
				System.err.println(" use face "+p.firstFace+" for vert "+v);

			// mark each face to indicate one of its vertices is in place
			int num = p.countFaces(v);
			int[] faceFlower=p.getFaceFlower(v);
			for (int jj = 0; jj < num; jj++) {
				int f = faceFlower[jj];
				fck[f] += 1;
			}
		}

		// add first face as the root
		dlink.addRoot(p.firstFace);
		fck[p.firstFace] = -ftick; // first face is placed
		if (debug)
			System.err.println("firstFace = " + p.firstFace + " " + -ftick);
		ftick++;

		// add pairs to dlink until reaching 'firstFace' or 'firstRedFace'
		//   Careful of two situations: 
		//      'firstRedFace' may be same as 'firstFace'; 
		//       there may be no boundary, no 'redchain'
		int nextface = p.firstFace;
		int curface = p.firstFace;
		Face curFace = nxtFace;
		if (p.redChain==null || (curface != p.firstRedFace)) { 
			nxtFace = p.faces[curFace.nextFace];
			nextface = p.faces[curface].nextFace; // LayoutBugs.print_drawingorder(p);
			while ((p.redChain==null || (nextface != p.firstRedFace)) && 
					nextface != p.firstFace
					&& nextface > 0 && nextface <= p.faceCount) {
				if (fck[nextface] >= 0) { // face not yet placed?
					int v = nxtFace.vert[(nxtFace.indexFlag + 2) % 3]; // vert to be laid
					if (debug) 
						System.err.println(" pre: use face "+nextface+" for vert "+v);
					cck[v] = ctick++;

					// update star(v) faces
					int num = p.countFaces(v);
					int[] faceFlower=p.getFaceFlower(v);
					for (int jj = 0; jj < num; jj++) {
						int f = faceFlower[jj];
						fck[f] += 1;
					}

					// try to put nextface in tree first
					int fopp = p.face_opposite(curFace.nextFace, v);
					if (fopp > 0 && fck[fopp] < 0) {
						dlink.add(fopp, nextface);
						fck[nextface] = -ftick;
						if (debug) System.err.println("eS pre edge= <"+fopp+" "+nextface+">  "+-ftick);
						ftick++;
					}

					// check completed faces in star(v) but not yet in tree
					int vcnt = 0;
					faceFlower=p.getFaceFlower(v);
					for (int jj = 0; jj < num; jj++) {
						int f = faceFlower[jj];
						if (fck[f] == 3)
							vcnt++;
					}

					// keep trying faces around v, new ones may become eligible
					boolean hit = true;
					while (hit && vcnt > 0) {
						hit = false;
						for (int jj = 0; (jj < num && vcnt > 0); jj++) {
							int f = faceFlower[jj];
							// just want faces with verts already in place
							Face Ff = p.faces[f];
							for (int k = 0; (k < 3 && fck[f] == 3); k++) {
								int ff = p.face_opposite(f, Ff.vert[k]);
								if (ff > 0 && fck[ff] < 0) {
									dlink.add(ff, f);
									fck[f] = -ftick;
									if (debug) System.err.println("eS edge= <"+ff+" "+f+">  "+-ftick);
									ftick++;
									vcnt--;
									hit = true;
								}
							}
						} // end of for
					} // end of inner while
				}

				curFace = nxtFace;
				curface = nextface;
				nxtFace = p.faces[curFace.nextFace];
				nextface = curFace.nextFace;
			} // end of while to get to 'firstRedFace'
		}

		// follow the full redChain (if there is one)
		if (p.redChain!=null && (nextface == p.firstRedFace)) {
			RedList nextred = p.redChain;
			
			// add the first red face (if not already there as the firstFace)
			int v;
			int num;
			if (nextface != p.firstFace) {
				v = nxtFace.vert[(nxtFace.indexFlag + 2) % 3]; // vert to be
																// laid
				if (debug)
					System.err.println(" first red face " + nextface
							+ " for vert " + v);
				cck[v] = ctick++;

				// update star(v) faces: Note: for v on red edge, this
				// could boost count to more than 3 for some face,
				// but these will be redChain faces, so they're handled
				// w/o regard to this count.
				num = p.countFaces(v);
				int[] faceFlower=p.getFaceFlower(v);
				for (int jj = 0; jj < num; jj++) {
					int f = faceFlower[jj];
					fck[f] += 1;
				}

				int fopp = p.face_opposite(curFace.nextFace, v);
				if (fopp > 0 && fck[fopp] >= 0)
					throw new CombException("can't put redface in tree");
				dlink.add(fopp, nextface);
				fck[nextface] = -ftick;
				if (debug)
					System.err.println("eS red edge= <" + fopp + " " + nextface
							+ ">  " + -ftick);
				ftick++;
			}

			// continue through rest of redChain
			RedList cred = nextred;
			nextred = cred.next;
			while (nextred != p.redChain) {
				if (fck[nextred.face] >= 0) { // put this face in the tree
					v = nextred.vert(nextred.vIndex); // circle to be laid out
					if (debug)
						System.err.println(" along red face " + nextred.face
								+ " for vert " + v);
					cck[v] = ctick++;
					num = p.countFaces(v);
					int[] faceFlower=p.getFaceFlower(v);
					for (int jj = 0; jj < num; jj++) {
						int f = faceFlower[jj];
						if (fck[f] >= 0)
							fck[f] += 1;
					}

					int useface = cred.face;
					if (p.face_nghb(cred.face, nextred.face) < 0)
						useface = nextred.prev.face;
					dlink.add(useface, nextred.face);
					fck[nextred.face] = -ftick;
					if (debug)
						System.err.println("eS red edge= <" + useface + " "
								+ nextred.face + ">  " + -ftick);
					ftick++;
					cred = nextred;
				}
				nextred = nextred.next;
			} // end of while
			
			if (debug)
			System.err.println("done with red");

			// done with red, but have to get the rest
			curFace = p.faces[p.redChain.face];
			curface = p.redChain.face;
			nxtFace = p.faces[curFace.nextFace];
			nextface = p.faces[curface].nextFace;
			while (nextface != p.firstFace && nextface > 0
					&& nextface <= p.faceCount) {
				if (fck[nextface] >= 0) {
					v = nxtFace.vert[(nxtFace.indexFlag + 2) % 3]; // vert to be
																	// laid
					if (debug)
						System.err.println(" post: use face " + nextface
							+ " for vert " + v);
					cck[v] = ctick++;

					// update info for star(v) faces
					num = p.countFaces(v);
					int[] faceFlower=p.getFaceFlower(v);
					for (int jj = 0; jj < num; jj++) {
						int f = faceFlower[jj];
						if (fck[f] >= 0)
							fck[f] += 1;
					}

					// try to put nextface in tree first
					int fopp = p.face_opposite(curFace.nextFace, v);
					if (fopp > 0 && fck[fopp] < 0) {
						dlink.add(fopp, nextface);
						fck[nextface] = -ftick;
						if (debug)
							System.err.println("eS post edge= <" + fopp + " "
									+ nextface + ">  " + -ftick);
						ftick++;
					}

					// are there completed star(v) faces not in tree?
					int vcnt = 0;
					faceFlower=p.getFaceFlower(v);
					for (int jj = 0; jj < num; jj++) {
						int f = faceFlower[jj];
						if (fck[f] == 3)
							vcnt++;
					}

					// if so, continue trying: new ones may be eligible
					boolean hit = true;
					while (hit && vcnt > 0) {
						hit = false;
						faceFlower=p.getFaceFlower(v);
						for (int jj = 0; jj < num; jj++) {
							int f = faceFlower[jj];
							Face Ff = p.faces[f];
							// while f is not placed
							for (int k = 0; (k < 3 && fck[f] == 3); k++) {
								int ff = p.face_opposite(f, Ff.vert[k]);
								if (ff > 0 && fck[ff] < 0) {
									dlink.add(ff, f);
									fck[f] = -ftick;
									if (debug)
										System.err.println("eS pickup edge= <" + ff
												+ " " + f + ">  " + -ftick);
									ftick++;
									vcnt--;
									hit = true;
								}
							}
						}
					} // end of inner while
				}

				curFace = nxtFace;
				curface = nextface;
				nxtFace = p.faces[curFace.nextFace];
				nextface = curFace.nextFace;
			} // end of outer while
		} // done in the case that there was a 'redChain'

		// check: are all vertices laid out?
		if (debug) {
			for (int v=1;v<=p.nodeCount;v++) {
				if (cck[v]==0) {
					CirclePack.cpb.errMsg("error in 'easySpanner', vert "+v);
					throw new CombException("didn't get all vertices");
				}
			}
		}

		// stopping with plot tree only?
		if (plottree)
			return dlink;

		// else continue, checking for interior faces not in tree
		FaceLink flist = new FaceLink(p);
		for (int f = 1; f <= p.faceCount; f++) {
			if (fck[f] >= 0) {
				flist.add(f);
				for (int j = 0; j < 3; j++) {
					if (cck[p.faces[f].vert[j]] <= 0)
						throw new CombException("face " + f
								+ " has missing vert " + p.faces[f].vert[j]);
				}
			}
		}

		// cycle through remaining faces, adding to tree or putting in new list
		FaceLink rlink;
		boolean hit = true;
		while (hit && flist.size() > 0) {
			hit = false;
			rlink = new FaceLink(p);

			Iterator<Integer> flt = flist.iterator();

			// proceed through flist, add to tree, create leftover list
			while (flt.hasNext()) {
				int nextf = flt.next();
				if (fck[nextf] >= 0) {
					if (fck[nextf] < 3)
						throw new CombException("face " + nextf
								+ " should be complete.");
					boolean gotit = false;
					int thef = -1;
					int bsf = -p.faceCount;
					for (int j = 0; j < 3; j++) {
						int f = p.face_opposite(nextf,
								p.faces[nextf].vert[j]);
						// is it in tree and earlier than others?
						if (f > 0 && fck[f] < 0 && (fck[f] > bsf)) {
							bsf = fck[f];
							thef = f;
						}
					}
					if (thef > 0) {
						dlink.add(thef, nextf);
						fck[nextf] = -ftick;
						if (debug)
							System.err.println("eS thef edge= <" + thef + " "
									+ nextf + ">  " + -ftick);
						ftick++;
						gotit = true;
					}

					if (!gotit)
						rlink.add(nextf); // have to try again later
				}
			} // end of inner while
			flist = rlink;
		} // end of outer while

		if (dlink == null || dlink.size() == 0)
			return null;
		return dlink;
	}
	
	/**
	 * Return 'GraphLink' whose edges are the dual graph edges
	 * associated with the existing drawing order; i.e., the tree 
	 * we can use to layout the packing. See comments for 'easySpanner'.
	 * @param p PackData
	 * @return GraphLink
	 */
	public static GraphLink plotTree(PackData p) {
		return easySpanner(p,true);
	}
	
	
	/**
	 * Use a tree in the dual graph of pack p to set the face drawing 
	 * order. Start at root, go until a red face is hit, then go around 
	 * the full red chain, then complete the rest.
	 * @param p PackData
	 * @param graph GraphLink
	 * @return int, count of faces in drawing order or -1 on error
	 */
	public static int tree2Order(PackData p,GraphLink graph) {
		Vector<Integer> roots=graph.isForest();
		if (roots==null || roots.size()!=1 || roots.get(0)<0)
			return -1;
		return graph2Order(p,graph,roots.get(0));
	}
	
	/**
	 * Use a subgraph of the dual graph to set the face drawing 
	 * order in pack p. Go until a red face is hit, then go around 
	 * the full red chain, then complete the rest.
	 * TODO: should 'baseface' be removed, find it as root?
	 * @param p PackData
	 * @param graph GraphLink
	 * @param baseface int
	 * @return int, count of faces in drawing order.
	 */
	public static int graph2Order(PackData p,GraphLink graph,int baseface) {
//		boolean debug=true;
		boolean debug=false;
		
		int count=0;
		
		// get RedList from dual tree
		// LayoutBugs.log_GraphLink(p,graph); or DualGraph.printGraph(graph);
		RedList newRedList=DualGraph.graph2red(p,graph,baseface);
		// deBugging.LayoutBugs.log_RedList(p,newRedList);
		
		// process to get redChain
		RedChainer newRC=new RedChainer(p);
		BuildPacket bP=new BuildPacket();
		bP=newRC.redface_comb_info(newRedList, false);
		if (!bP.success) {
			throw new LayoutException("Layout error in DualGraph");
		}
		p.setSidePairs(bP.sidePairs);
		p.labelSidePairs(); // establish 'label's
		p.redChain=p.firstRedEdge=bP.firstRedEdge;
		for (int f=1;f<=p.faceCount;f++) {
			p.faces[f].nextRed=0;
		}
		RedList rtrace=(RedList)p.redChain.next;
		p.faces[p.firstRedFace].nextRed=rtrace.face;
		int safety=p.faceCount+10;
		while (rtrace!=p.redChain && safety>0) {
			safety--;
			p.faces[rtrace.face].nextRed=rtrace.next().face;
			rtrace=(RedList)rtrace.next;
		}
			
		if (debug)
			LayoutBugs.log_RedList(p,p.redChain);
		
		// initialize
		for (int f=1;f<=p.faceCount;f++) {
			p.faces[f].nextFace=0;
			p.faces[f].rwbFlag=-1; 
		}
		
		// mark the red
		RedList trace=p.redChain;
		p.faces[trace.face].rwbFlag=1;
		while ((trace=trace.next)!=p.redChain) {
			p.faces[trace.face].rwbFlag=1;
		}

		// which faces are plotted
		int []fck=new int[p.faceCount+1];

		// which face plotted each vertex
		int []cck=new int[p.nodeCount+1];
		
		// process the root
		int root=graph.get(0).w;
		int lastface=p.firstFace=root;
		fck[p.firstFace]=++count;
		p.faces[p.firstFace].indexFlag=0;
		for (int j=0;j<3;j++) {
			cck[p.faces[root].vert[j]]=root;
		}
		
		// -------- simply-connected (applied to tree, not full complex) -----
		if (p.getSidePairs()==null || p.getSidePairs().size()==0) { 

			Iterator<EdgeSimple> tc=graph.iterator();
			tc.next(); // shuck root entry
			while (tc.hasNext()) {
				EdgeSimple edge=tc.next();
				int f1=edge.v;
				int f2=edge.w;
				int indx=p.face_nghb(f1,f2);
				if (indx<0) 
					throw new CombException("faces "+f1+" and "+f2+" don't share an edge");
				cck[p.faces[f2].vert[(indx+2)%3]]=f2;
				p.faces[f2].indexFlag=indx;
				fck[f2]=++count;
				lastface=p.faces[lastface].nextFace=f2; 
			}
		} // end of simply connected case
		
		// -------- multiply-connected (applied to tree, not full complex) ---
		else {
			int hit_red=0;
			if (p.faces[root].rwbFlag>0) // root might be red
				hit_red=root;
			Iterator<EdgeSimple> tc=graph.iterator();
			tc.next(); // shuck the root
			int f1=0;
			
			// put faces in drawing order until reaching the first red
			while (tc.hasNext() && hit_red==0) {
				EdgeSimple edge=tc.next();
				f1=edge.v;
				if (fck[f1]==0) 
					throw new CombException("this face should have been plotted");
				int f2=edge.w;
				int indx=p.face_nghb(f1,f2);
				if (indx<0) 
					throw new CombException("faces "+f1+" and "+f2+" don't share an edge");

				// put in drawing order?
				int vert=p.faces[f2].vert[(indx+2)%3];
				if (fck[f2]==0) { // && cck[vert]==0) {
					fck[f2]=++count;
					p.faces[f2].indexFlag=indx;
					cck[vert]=f2;
					lastface=p.faces[lastface].nextFace=f2;
				}
				
				// is this the first red?
				if (p.faces[f2].rwbFlag>0) {
					hit_red=f2;
				}
				
			} // end of first while --- should stop after plotting first red 
			
			// put in the red chain
		    int red_count=1;
			p.position_redlist(p.redChain,hit_red);
			int curf=p.firstRedFace=hit_red;
		    trace=p.redChain.next;
		    int newf=trace.face;
		    while (trace!=p.redChain && red_count<(3*p.faceCount)) {
		    	red_count++;
		    	// skip over faces with nextFace already set, or if pointing to self
		    	while (trace!=p.redChain && (p.faces[newf].nextFace!=0 || newf==curf)) {
		    		trace=trace.next;
		    		newf=trace.face;
		    	}
		    	if (trace!=p.redChain) {
		    		p.faces[curf].nextFace=p.faces[curf].nextRed=newf;
					int index=p.face_nghb(trace.prev.face,newf);
		    		p.faces[newf].indexFlag=index;
		    		fck[newf]=++count;
		    		int vert=p.faces[newf].vert[(p.faces[newf].indexFlag+2) % 3];
		    		cck[vert]=newf;

					lastface=p.faces[lastface].nextFace=newf;
		    		trace=trace.next;
		    		curf=newf;
		    		newf=trace.face;
		    	}
		    } // end of second while
		    if (red_count > (3*p.faceCount)) throw new LayoutException("'red_count' is too large");
		    
		    // Now continue to get the rest of the faces
			while (tc.hasNext()) {
				EdgeSimple edge=tc.next();
				if (fck[edge.v]>0) {
					f1=edge.v;
					int f2=edge.w;
					int indx=p.face_nghb(f1,f2);
					if (indx<0) 
						throw new CombException("faces "+f1+" and "+f2+" don't share an edge");
					// put in drawing order?
					int vert=p.faces[f2].vert[(indx+2)%3];
					if (fck[f2]==0) { // && cck[vert]==0) {
						fck[f2]=++count;
						p.faces[f2].indexFlag=indx;
						cck[vert]=f2;
						int tmpface=lastface;
						lastface=p.faces[tmpface].nextFace=f2;
					}
				}
			} // end of third while
		} // end of else
		
		// ??? close up list by pointing back to first.
		// I think it points to zero --- not sure
		p.faces[lastface].nextFace=0;
		
		if (debug) // (goes to /tmp/faceOrder_..._log)
			LayoutBugs.log_faceOrder(p);
		
		return count;
	}
	
	/**
	 * Given dual graph and starting face, create a drawing order
	 * for the packing p. Effort is to place faces by generation.
	 * 2/4/2011
	 * NOTE: this changes packing info, so don't accidently reorder.
	 * @param p PackData
	 * @param graph GraphLink
	 * @param startface int
	 * @return int, count of faces placed.
	 */
	public static int graph2order(PackData p,GraphLink graph,int startface) {

		if (graph==null || graph.size()==0 || !graph.nodeExists(startface))
			throw new ParserException("problem with graph or node "+startface);

		int count=0; // faces processed
		
		// store generations of verts, those of startface are 1.
		for (int v=1;v<=p.nodeCount;v++) {
			p.kData[v].utilFlag=0;
			p.kData[v].plotFlag=0;
		}
		for (int j=0;j<3;j++) {
			int k=p.faces[startface].vert[j];
			p.kData[k].utilFlag=1;
			p.kData[k].plotFlag=1;
		}
		util.UtilPacket uP=new util.UtilPacket();
		int []vgens=p.label_generations(-1,uP);
		
		//  store generation of faces by lowest vertex degree.
		int []futil=new int[p.faceCount+1];
		for (int f=1;f<=p.faceCount;f++) {
			futil[f]=vgens[p.faces[f].vert[0]];
			for (int j=1;j<3;j++) {
				int k=p.faces[f].vert[j];
				if (vgens[k]<futil[f])
					futil[f]=vgens[k];
			}
			p.faces[f].nextFace=startface;
			p.faces[f].plotFlag=0;
		}

		// rotating lists of current/next faces to being processed
		FaceLink currFaces=null;
		FaceLink nextFaces=new FaceLink(p);
		
		nextFaces.add(startface);
		p.faces[startface].plotFlag=++count;
		
		// keep track of:
		//  * vgens[]: generations of circles, 1 = in startface
		//  * futil[]: holds lowest generation of vert for each face
		//  * currFaceGen: which generation is being done now?
		//  * currFaces: lowest gen faces with placed partners
		//  * plot1Faces: next lowest gen partners
		// adjust:
		//  * vert plotFlag: plotted verts, shows which face was used
		//  * face plotFlag:
		//  * nextFace: next face in plot order
		//  * indexFlag: for which vert this face places
		
		int currFaceGen=0;
		int lastface=startface;
		int safety=p.faceCount;
		while(nextFaces.size()>0 && safety>0) {
			safety--;
			currFaces=nextFaces;
			nextFaces=new FaceLink(p);
			currFaceGen=futil[currFaces.get(0)]; // generation of this list
			while (currFaces.size()>0) {
				int face=currFaces.remove(0);
System.err.println("g2o face="+face);				
				NodeLink partners=graph.findPartners(face);
				Iterator<Integer> ptns=partners.iterator();
				while (ptns.hasNext()) {
					int nghb=ptns.next();
//					if (p.faces[nghb].plotFlag==0) {
						
						// plot 'nghb' using 'face'
						int indx=p.face_nghb(face,nghb);
						if (indx<0) 
							throw new CombException("faces "+face+" and "+nghb+" don't share an edge");
						int vert=p.faces[nghb].vert[(indx+2)%3];
						p.faces[nghb].indexFlag=indx;
						p.faces[nghb].plotFlag=++count;
						if (p.kData[vert].plotFlag==0) {
							p.kData[vert].plotFlag=nghb;
							lastface=p.faces[lastface].nextFace=nghb; // yes, is in plot order
						}
						
System.err.println("  place nghb "+nghb+" point to vert "+vert);
						
						// add this neighbor to one of the lists
						if (futil[nghb]<=currFaceGen)
							currFaces.add(nghb);
						else 
							nextFaces.add(nghb);
//					}
				} // end of while on partners
			} // end of while on currFace
		} // end of outer while
		
		return count;
	}
	
	/**
	 * Given a rooted GraphLink of faces, return pruned list having
	 * (in addition to the root) those edges which account for an
	 * encounter with a vertex which wasn't hit before. E.g. for edge
	 * (g,f) find vertex of f opposite the edge shared with g and see
	 * if it's already been found.
	 * 
	 * This routine is needed for efficiency in drawing circles based
	 * on a GraphLink tree.
	 * @param p PackData
	 * @param graph GraphLink
	 * @return GraphLink
	 */
	public static GraphLink pruneDrawSpan(PackData p,GraphLink graph) {
		
		if (graph==null || graph.size()==0 || graph.get(0).v!=0)
			throw new ParserException("problem with incoming graph or root");
		
		// count layout "hits" for 
		int []hits=new int[p.nodeCount+1];
		
		Iterator<EdgeSimple> gph=graph.iterator();
		EdgeSimple edge=gph.next();
		GraphLink newgl=new GraphLink(p,edge);
		
		// first is root; indicate its vertices are hit.
		int []vert=p.faces[edge.w].vert;
		hits[vert[0]]=hits[vert[1]]=hits[vert[2]]=1;
		
		while (gph.hasNext()) {
			edge=gph.next();
			int j=p.face_nghb(edge.v, edge.w);
			if (j>=0) {
				j=(j+2)%3;
				int v=p.faces[edge.w].vert[j];
				hits[v]++; // may want info about hits in some future revision
				if (hits[v]==1) { // first hit on this vertex
					newgl.add(edge);
				}
			}
		}
		return newgl;
	}		
	
	/**
	 * To extract a spanning tree from a given graph in the dual
	 * for sake of drawing order; compute and use an ordering by
	 * generations within the given graph. Calling routine may 
	 * specify the 'startface', else the second entry of the
	 * first edge of the graph is used.
	 * @param p PackData
	 * @param graph GraphLink
	 * @param startface int, first face to use.
	 * @return GraphLink
	 */
	public static GraphLink drawSpanner(PackData p,GraphLink graph,int startface) {

		if (graph==null || graph.size()==0)
			throw new ParserException("problem with graph or node "+startface);

		// invalid starting face; choose one.
		// TODO: need more rational choice
		if (startface<0 || !graph.nodeExists(startface)) {
			startface=graph.get(0).w;
		}
		
		int count=0; // faces processed
		
		// prepare to store the generations of verts
		for (int v=1;v<=p.nodeCount;v++) {
			p.kData[v].utilFlag=0;
			// TODO: commented out changes in vertex plotFlags on 5/23/13. 
			//       Don't know why this was needed.
//			p.kData[v].plotFlag=0;
		}
		
		// set startface vertices are generation 1
		for (int j=0;j<3;j++) {
			int k=p.faces[startface].vert[j];
			p.kData[k].utilFlag=1;
//			p.kData[k].plotFlag=1;
		}
		
		// get generations of the other vertices
		util.UtilPacket uP=new util.UtilPacket();
		int []vgens=p.label_generations(-1,uP);
		
		//  store generations of faces (lowest generation of its vertices) 
		int []futil=new int[p.faceCount+1];
		for (int f=1;f<=p.faceCount;f++) {
			futil[f]=vgens[p.faces[f].vert[0]];
			for (int j=1;j<3;j++) {
				int k=p.faces[f].vert[j];
				if (vgens[k]<futil[f])
					futil[f]=vgens[k];
			}
			p.faces[f].plotFlag=0;
		}

		// start tree with the root
		GraphLink dtree = new GraphLink();
		dtree.add(new EdgeSimple(0,startface));
		
		// rotating lists of current/next faces being processed
		FaceLink currFaces=null;
		FaceLink nextFaces=new FaceLink(p);
		
		nextFaces.add(startface);
		p.faces[startface].plotFlag=++count;
		
		// keep track of:
		//  * futil[]: holds lowest generation of vert for each face
		//  * currFaceGen: which generation is being done now?
		//  * currFaces: lowest gen faces with placed partners
		//  * plot1Faces: next lowest gen partners
		int currFaceGen=0;
		int safety=p.faceCount;
		while(nextFaces.size()>0 && safety>0) {
			safety--;
			currFaces=nextFaces;
			nextFaces=new FaceLink(p);
			currFaceGen=futil[currFaces.get(0)]; // generation of this list
			while (currFaces.size()>0) {
				int face=currFaces.remove(0);
//System.err.println("dS face="+face);				
				NodeLink partners=graph.findPartners(face);
				Iterator<Integer> ptns=partners.iterator();
				while (ptns.hasNext()) {
					int nghb=ptns.next();
					if (p.faces[nghb].plotFlag==0) {
						dtree.add(new EdgeSimple(face,nghb));
						p.faces[nghb].plotFlag=++count;
//System.err.println("  edge ["+face+" "+nghb+"]");
						
						// add this neighbor to one of the lists
						if (futil[nghb]<=currFaceGen)
							currFaces.add(nghb);
						else 
							nextFaces.add(nghb);
					}
				} // end of while on partners
			} // end of while on currFace
		} // end of outer while
		
		return dtree;
	}	
	
	/**
	 * Given a subgraph of the dual graph of packing 'p', return
	 * the chain of 'red' faces forming its outer edge, the leaves.
	 * Caution: this may not be whole boundary unless the component 
	 * of the subgraph containing 'baseface' is contractible. This
	 * is automatic 'graph' is a spanning tree.
	 * @param p PackData
	 * @param graph GraphLink; recall, nodes = faces of 'p', edges = dual edges
	 * @param baseface int
	 * @return RedList
	 */
	public static RedList graph2red(PackData p,GraphLink graph,int baseface) {
//		boolean debug=true;
		boolean debug=false;
		if (!graph.nodeExists(baseface))
			throw new CombException("baseface "+baseface+" not in the graph");
		
		// Note: some vertices and faces are not involved in the tree:
		// facegens[f] gives lowest generation distance from baseface in tree, or -1 if not in tree
		int []facegens=graph.graphDistance(baseface,p.faceCount); // DualGraph.printGraph(graph);
		
		// vertgens[v] gives lowest generation of faces containing v, or is -1  
		int []vertgens=new int[p.nodeCount+1];
			for (int v=1;v<=p.nodeCount;v++)
				vertgens[v]=-1;			
		for (int f=1;f<=p.faceCount;f++) {
			int fg=facegens[f];
			if (fg>0)
				for (int j=0;j<3;j++) {
					int w=p.faces[f].vert[j];
					if (vertgens[w]==-1 || vertgens[w]>fg)
					vertgens[w]=fg;
			}
		}
		
		// The 'Spokes' class is for keeping track of status of vertices.
		//   E.g., keep track of incident white edges: 'white' edges 
		//   are interior and are white because either the dual edge is 
		//   in original tree or it is marked as closing up a flower
		//   during processing.
		Spokes []spokes=new Spokes[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) { 
			spokes[v]=new Spokes(v,p.countFaces(v),p.getBdryFlag(v));
			spokes[v].gen=vertgens[v];
		}
		
		// mark duals to tree edges in 'Spokes' structure. 
		Iterator<EdgeSimple> tl=graph.iterator();
		while (tl.hasNext()) {
			EdgeSimple faces=tl.next();
			EdgeSimple verts=null;
			if (faces.v!=0) { // to ignore tree 'roots'
				verts=p.reDualEdge(faces.v,faces.w);
				if (debug) // debug=true;
					System.err.println("("+faces.v+" "+faces.w+")");
				int v=verts.v;
				int w=verts.w;
				spokes[v].flower[p.nghb(v,w)]=w;
				spokes[w].flower[p.nghb(w,v)]=v;
			}
		}
		
		// Processing involves repeatedly looking for spokes with deficit=1;
		//   when closing one up, you add to the list of new spokes with 
		//   deficit=1. Ping pong between these lists.
		LinkedList<Spokes> currSpokes=null; 
		LinkedList<Spokes> nextSpokes=new LinkedList<Spokes>();
		
		// get started with first list of ones to look at
		for (int v=1;v<=p.nodeCount;v++) {
			if (spokes[v].gen>0 && spokes[v].deficit()==1) {
				nextSpokes.add(spokes[v]);
			}
		}
		
		// process the list and start the new one
		while (nextSpokes.size()>0) {
			currSpokes=nextSpokes;
			nextSpokes=new LinkedList<Spokes>();
			Iterator<Spokes> cS=currSpokes.iterator();
			while (cS.hasNext()) {
				Spokes curr=cS.next();
				if (curr.deficit()==1) {
					for (int j=0;j<curr.num;j++) {
						if (curr.flower[j]==0) {
							int ww=p.kData[curr.vert].flower[j];
//System.out.println("edge "+curr.vert+" "+ww);
							curr.flower[j] = -ww;
							spokes[ww].flower[p.nghb(ww,curr.vert)]=-curr.vert;
							if (spokes[ww].deficit()==0) { 
								spokes[ww].status=1; // done with ww
							}
							else if (spokes[ww].deficit()==1) {
								nextSpokes.add(spokes[ww]);
							}
							j=curr.num; // kick out of for loop
							curr.status=1; // done with curr.vert
						}
					}
					if (curr.deficit()!=0)
						throw new CombException("didn't fix deficit at vert "+curr.vert);
				}
			} // end of inner while
		} // end of outer while
		if (debug) System.out.println("done setting white spokes");
		
		// To find the red chain, start by setting red[v]=1 if v is 'red',
		//   meaning gen>0 and status is 0, meaning it was not closed in by
		//   the tree. (gen>0 should omit verts separated from tree)
		int []redV=new int[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) {
			if (spokes[v].gen>0 && spokes[v].status==0)
				redV[v]=1;
		}
		
		// We want to start with the red vert having face
		//   of lowest generation, and we want to use that
		//   face to get the red chain started.
		int minv=0; // which red vert?
		int faceIndx=0; // index of low generation face?
		int minfgen=p.faceCount; // lowest generation yet?
		for (int v=1;v<=p.nodeCount;v++) {
			int fg=0;
			if (redV[v]==1 ) {
				int[] faceFlower=p.getFaceFlower(v);
				for (int j=0;j<p.countFaces(v);j++) {
					int f=faceFlower[j];
					fg=facegens[f];
					if (fg>0 && fg<minfgen) {
						faceIndx=j;
						minfgen=fg;
						minv=v;
					}
				}
			}
		}
		
		// sphere should be only case where all edges get closed
		if (minv==0 || minfgen==p.faceCount) {
				return null;
		}

		// try to start with this vert and face:
		int currV=minv;
		int currIndx=faceIndx;
		int currF=p.kData[minv].flower[currIndx];
		Spokes currSpoke=spokes[currV];
		int num=currSpoke.num;
		int []flower=currSpoke.flower;
		int redJ=-1;

		if (debug) System.err.println("get started with red vert "+currV+ " and face "+currF);

		// check if this face would be blue (i.e. bounding edges
		//   both open. I think this can't happen unless this is
		//    baseface.
		if (flower[currIndx]==0 && flower[currIndx+1]==0) { // yes, it's blue
			// go to next counterclockwise vertex
			int acrs=p.kData[currV].flower[currIndx]; // other end of this spoke
			redJ=p.nghb(acrs,currV);
			currV=acrs;
		}
		else { // sweep counterclockwise until open edge
			if (p.isBdry(currV)) { // if bdry vert, sweep
				while (spokes[currV].flower[currIndx+1]!=0)
					currIndx++;
			}
			else {
				while (spokes[currV].flower[(currIndx+1)%p.countFaces(currV)]!=0)
					currIndx=(currIndx+1)%p.countFaces(currV);
			}
			redJ=currIndx+1;  
		}
		
		// redJ should point to the first open spoke with the previous
		//   (clockwise) spoke not open. 
		if (redJ==-1) {
			throw new CombException("no spoke transition");
		}
			
		// this give our first redchain face
		if (redJ==0) { // currV must be interior
			currF=p.getFaceFlower(currV,p.countFaces(currV)-1);
		}
		else 
			currF=p.getFaceFlower(currV,redJ-1);

		if (debug) System.err.println("first red face is "+currF);

		// set up to stop construction when we encounter these again
		int stopface=currF;
		int stopvert=currV;
		RedList redlist=new RedList(p,currF);
		redlist.center=new Complex(0.0);
		RedList trace=redlist;
		
		// now loop --- come in with:
		//   * currV = latest red vertex
		//   * currF = latest red face
		//   * redJ = index of red edge entering currV
		//            (this is an edge of currF)
		//   We need to add the fan of currV and then loop
		boolean wflag=true;
		int safety=p.nodeCount*10;
		while((currF!=stopface || currV!=stopvert || wflag) && safety>0) {
			wflag=false;
			safety--;
			num=p.countFaces(currV);
			flower=spokes[currV].flower;
			int[] faceFlower=p.getFaceFlower(currV);
			int nextface=0;
			
			// add clockwise faces for closed edges
			int n=1;
			int j=(redJ+num-n)%num;
			int nextV=p.kData[currV].flower[j];
			while (flower[j]!=0) {
				n++;
				j=(redJ+num-n)%num;
				nextface=faceFlower[j];
				nextV=p.kData[currV].flower[j];
				if (nextface!=stopface || nextV!=stopvert) {
					trace=new RedList(trace,nextface);
					trace.center=new Complex(0.0);
					if (debug) System.out.println("next red face "+nextface);
				}
			}
			if (n==1) { // blue face
//				trace=new RedList(trace,currF);
//				redlist.center=new Complex(0.0);
				if (debug) System.out.println("blue face "+currF);
				nextface=faceFlower[j];
				nextV=p.kData[currV].flower[(redJ+num-1)%num];
			}
			currF=nextface;
			redJ=p.nghb(nextV,currV);
			currV=nextV;
			if (debug) System.out.println("currV "+currV+"; currF "+currF);			
		}
		if (safety<=0) {
			throw new CombException("error processing tree to redlist");
		}
		
		// final check: is the first face itself 'blue'? (might find this only
		//    at the end) Then have to remove one instance, patch up.
		if (redlist.face==redlist.prev.face) {
			redlist.next.prev=redlist.prev;
			redlist.prev.next=redlist.next;
			redlist=redlist.next;
			if (redlist.face==redlist.prev.face) {
				throw new CombException("impossible redchain: two blues in a row?");
			}
		}
		
		return redlist;
	}

	/**
	 * Print the edges in this graph to standard out for debugging
	 * @param graph GraphLink
	 */
	public static void printGraph(GraphLink graph) {
		Iterator<EdgeSimple> gl=graph.iterator();
		StringBuilder strbld=new StringBuilder("Graph listing:  ");
		while (gl.hasNext()) {
			EdgeSimple edge=gl.next();
			strbld.append(" ["+edge.v+" "+edge.w+"] ->");
		}
		strbld.append("  done\n");
		System.out.println(strbld.toString());
	}
	
	/**
	 * Given a subgraph of the dual graph of packing 'p', return
	 * the associated packing. Not sure what pathologies could be
	 * encountered. Hope this is a way to manage 'cookie'. This
	 * code is pilfored from 'graph2red'.
	 * @param p PackData
	 * @param graph GraphLink: CAUTION: nodes = faces of 'p', edges = dual edges
	 * @return PackData
	 */
	public static PackData graph2packing(PackData p,GraphLink graph) {
		boolean debug=false;
		int baseface=graph.get(0).v;
		if (baseface<=0)
			baseface=graph.get(0).w;
		if (baseface<=0 || baseface>p.faceCount)
			throw new ParserException("failed to find first face");
		
		// Note: some vertices and faces are not involved in the tree:
		// facegens[f] gives lowest generation distance from baseface in tree, or -1 if not in tree
		int []facegens=graph.graphDistance(baseface,p.faceCount);
		
		// vertgens[v] gives lowest generation of faces containing v, or is -1  
		int []vertgens=new int[p.nodeCount+1];
			for (int v=1;v<=p.nodeCount;v++)
				vertgens[v]=-1;			
		for (int f=1;f<=p.faceCount;f++) {
			int fg=facegens[f];
			if (fg>0)
				for (int j=0;j<3;j++) {
					int w=p.faces[f].vert[j];
					if (vertgens[w]==-1 || vertgens[w]>fg)
					vertgens[w]=fg;
			}
		}
		
		// The 'Spokes' class is for keeping track of status of vertices.
		//   E.g., keep track of incident white edges: 'white' edges 
		//   are interior and are white because either the dual edge is 
		//   in original tree or it is marked as closing up a flower
		//   during processing.
		Spokes []spokes=new Spokes[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) { 
			spokes[v]=new Spokes(v,p.countFaces(v),p.getBdryFlag(v));
			spokes[v].gen=vertgens[v];
		}
		
		// mark duals to tree edges in 'Spokes' structure. 
		Iterator<EdgeSimple> tl=graph.iterator();
		while (tl.hasNext()) {
			EdgeSimple faces=tl.next();
			EdgeSimple verts=null;
			if (faces.v!=0) { // to ignore tree 'roots'
				verts=p.reDualEdge(faces.v,faces.w);
				int v=verts.v;
				int w=verts.w;
				spokes[v].flower[p.nghb(v,w)]=w;
				spokes[w].flower[p.nghb(w,v)]=v;
			}
		}
		
		// Processing involves repeatedly looking for spokes with deficit=1;
		//   when closing one up, you add to the list of new spokes with 
		//   deficit=1. Ping pong between these lists.
		LinkedList<Spokes> currSpokes=null; 
		LinkedList<Spokes> nextSpokes=new LinkedList<Spokes>();
		
		// get started with first list of ones to look at
		for (int v=1;v<=p.nodeCount;v++) {
			if (spokes[v].gen>0 && spokes[v].deficit()==1) {
				nextSpokes.add(spokes[v]);
			}
		}
		
		// process the list and start the new one
		while (nextSpokes.size()>0) {
			currSpokes=nextSpokes;
			nextSpokes=new LinkedList<Spokes>();
			Iterator<Spokes> cS=currSpokes.iterator();
			while (cS.hasNext()) {
				Spokes curr=cS.next();
				if (curr.deficit()==1) {
					for (int j=0;j<curr.num;j++) {
						if (curr.flower[j]==0) {
							int ww=p.kData[curr.vert].flower[j];
//System.out.println("edge "+curr.vert+" "+ww);
							curr.flower[j] = -ww;
							spokes[ww].flower[p.nghb(ww,curr.vert)]=-curr.vert;
							if (spokes[ww].deficit()==0) { 
								spokes[ww].status=1; // done with ww
							}
							else if (spokes[ww].deficit()==1) {
								nextSpokes.add(spokes[ww]);
							}
							j=curr.num; // kick out of for loop
							curr.status=1; // done with curr.vert
						}
					}
					if (curr.deficit()!=0)
						throw new CombException("didn't fix deficit at vert "+curr.vert);
				}
			} // end of inner while
		} // end of outer while
		if (debug) System.out.println("done setting white spokes");
		
		// To find the red chain, start by setting red[v]=1 if v is 'red',
		//   meaning gen>0 and status is 0, meaning it was not closed in by
		//   the tree. (gen>0 should omit verts separated from tree)
		int []redV=new int[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) {
			if (spokes[v].gen>0 && spokes[v].status==0)
				redV[v]=1;
		}
		
		// We want to start with the red vert having face
		//   of lowest generation, and we want to use that
		//   face to get the red chain started.
		int minv=0; // which red vert?
		int faceIndx=0; // index of low generation face?
		int minfgen=p.faceCount; // lowest generation yet?
		for (int v=1;v<=p.nodeCount;v++) {
			int fg=0;
			if (redV[v]==1 ) {
				for (int j=0;j<p.countFaces(v);j++) {
					int f=p.getFaceFlower(v,j);
					fg=facegens[f];
					if (fg>0 && fg<minfgen) {
						faceIndx=j;
						minfgen=fg;
						minv=v;
					}
				}
			}
		}
		
		// sphere should be only case where all edges get closed
		if (minv==0 || minfgen==p.faceCount) {
				return null;
		}

		// try to start with this vert and face:
		int currV=minv;
		int currIndx=faceIndx;
		int currF=p.kData[minv].flower[currIndx];
		Spokes currSpoke=spokes[currV];
		int num=currSpoke.num;
		int []flower=currSpoke.flower;
		int redJ=-1;

		if (debug) System.err.println("get started with red vert "+currV+ " and face "+currF);

		// check if this face would be blue (i.e. bounding edges
		//   both open. I think this can't happen unless this is
		//    baseface.
		if (flower[currIndx]==0 && flower[currIndx+1]==0) { // yes, it's blue
			// go to next counterclockwise vertex
			int acrs=p.kData[currV].flower[currIndx]; // other end of this spoke
			redJ=p.nghb(acrs,currV);
			currV=acrs;
		}
		else { // sweep counterclockwise until open edge
			if (p.isBdry(currV)) { // if bdry vert, sweep
				while (spokes[currV].flower[currIndx+1]!=0)
					currIndx++;
			}
			else {
				while (spokes[currV].flower[(currIndx+1)%p.countFaces(currV)]!=0)
					currIndx=(currIndx+1)%p.countFaces(currV);
			}
			redJ=currIndx+1;  
		}
		
		// redJ should point to the first open spoke with the previous
		//   (clockwise) spoke not open. 
		if (redJ==-1) {
			throw new CombException("no spoke transition");
		}
			
		// this give our first redchain face
		if (redJ==0) { // currV must be interior
			currF=p.getFaceFlower(currV,p.countFaces(currV)-1);
		}
		else 
			currF=p.getFaceFlower(currV,redJ-1);

		if (debug) System.err.println("first red face is "+currF);

		// set up to stop construction when we encounter these again
		int stopface=currF;
		int stopvert=currV;
		RedList redlist=new RedList(p,currF);
		redlist.center=new Complex(0.0);
		RedList trace=redlist;
		
		// now loop --- come in with:
		//   * currV = latest red vertex
		//   * currF = latest red face
		//   * redJ = index of red edge entering currV
		//            (this is an edge of currF)
		//   We need to add the fan of currV and then loop
		boolean wflag=true;
		int safety=p.nodeCount*10;
		while((currF!=stopface || currV!=stopvert || wflag) && safety>0) {
			wflag=false;
			safety--;
			num=p.countFaces(currV);
			flower=spokes[currV].flower;
			int[] faceFlower=p.getFaceFlower(currV);
			int nextface=0;
			
			// add clockwise faces for closed edges
			int n=1;
			int j=(redJ+num-n)%num;
			int nextV=p.kData[currV].flower[j];
			while (flower[j]!=0) {
				n++;
				j=(redJ+num-n)%num;
				nextface=faceFlower[j];
				nextV=p.kData[currV].flower[j];
				if (nextface!=stopface || nextV!=stopvert) {
					trace=new RedList(trace,nextface);
					trace.center=new Complex(0.0);
					if (debug) System.out.println("next red face "+nextface);
				}
			}
			if (n==1) { // blue face
//				trace=new RedList(trace,currF);
//				redlist.center=new Complex(0.0);
				if (debug) System.out.println("blue face "+currF);
				nextface=faceFlower[j];
				nextV=p.kData[currV].flower[(redJ+num-1)%num];
			}
			currF=nextface;
			redJ=p.nghb(nextV,currV);
			currV=nextV;
			if (debug) System.out.println("currV "+currV+"; currF "+currF);			
		}
		if (safety<=0) {
			throw new CombException("error processing tree to redlist");
		}
		
		// TODO: not done with this routine, 8/3/2012
		return null;
	}

}


class Spokes {
	int vert;          // associated vertex
	public int status; // 0 = nothing done; 1, done
	int gen;           // lowest face generation containing 'vert'
	int num;		   // usual flower 'num'
	int bFlag; 		   // set to 'bdryFlag': 1=bdry, 0=interior
	int []flower;      // set to petal index w or -w if this edge was added, else 0
	
	public Spokes(int v,int n,int bf) {
		vert=v;
		num=n;
		bFlag=bf;
		flower=new int[n+bFlag];
		status=0; 
		gen=0;
	}
	
	/**
	 * Count number of edges from vert which are 0
	 * @return
	 */
	public int deficit() {
		int accum=num+bFlag;
		for (int j=0;j<num+bFlag;j++) {
			if (flower[j]!=0)
				accum--;
		}
		return accum;
	}
	
}
