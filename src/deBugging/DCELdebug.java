package deBugging;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import allMains.CPBase;
import complex.Complex;
import dcel.SideData;
import dcel.DcelFace;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedEdge;
import dcel.Vertex;
import exceptions.CombException;
import exceptions.DCELException;
import input.CPFileManager;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import panels.CPScreen;
import util.DispFlags;

public class DCELdebug {
	
	static File tmpdir=new File(System.getProperty("java.io.tmpdir"));
	static int rankStamp=1; // progressive number to distinguish file instances
	
	public static void listRedThings(PackDCEL pdcel) {
		int safety=1000;
		
		System.out.println("'listRedThings: redChain is "+pdcel.redChain+"; red edges are: \n");
		
		// find all 'HalfEdges' with 'myRedEdge' non-null
		int count=0;
		StringBuilder strbld=new StringBuilder("edges with 'myRedEdge':  ");
		for (int e=1;e<=pdcel.edgeCount;e++) {
			HalfEdge he=pdcel.edges[e];
			if (he.myRedEdge!=null) {
				strbld.append("("+he+")  ");
				count++;
				safety--;
				if ((count%8)==0) {
					System.out.println(strbld.toString());
					strbld=new StringBuilder();
				}
			}
			if (safety<=0) {
				System.out.println("Safety'ed out on edges.");
				return;
			}
		}
		System.out.println(strbld.toString()+"\ndone with edges.");
				
		// find all vertices with 'redFlag'
		strbld=new StringBuilder("red vertices are:  ");
		for (int v=1;v<=pdcel.vertCount;v++) {
			Vertex vert=pdcel.vertices[v];
			if (vert.redFlag) {
				strbld.append(" "+vert+",  ");
				count++;
				safety--;
				if ((count%10)==0) {
					System.out.println(strbld.toString());
					strbld=new StringBuilder();
				}
			}
			if (safety<=0) {
				System.out.println(strbld.toString()+"\nSafety'ed out on vertices.");
				return;
			}
		}
		System.out.println(strbld.toString()+"\ndone with edges.");
	}
	
	/**
	 * check if all array indexes agree with their recorded "*Indx". 
	 * @param pdcel PackDCEL
	 */
	public static void indexConsistency(PackDCEL pdcel) {
		int vhits=0;
		int fhits=0;
		int ehits=0;
		for (int j=1;j<=pdcel.vertCount;j++)
			if (pdcel.vertices[j].vertIndx!=j)
				vhits++;
		for (int j=1;j<=pdcel.edgeCount;j++)
			if (pdcel.edges[j].edgeIndx!=j)
				ehits++;
		for (int j=1;j<=pdcel.faceCount;j++)
			if (pdcel.faces[j].faceIndx!=j)
				fhits++;
		if (vhits==0 && fhits==0 && ehits==0)
			System.out.println("Index consistency check: all good.");
		else
			System.err.println("Index consistency problemss: vhits="+vhits+
					"; ehits="+ehits+"; fhits="+fhits);
	}
	
	
	/**
	 * Compare 'myEdge' to 'edges' entry with same index.
	 * @param pdcel PackDCEL
	 */
	public static void redindx(PackDCEL pdcel) {
		int safety=1000;
		boolean bug=false;
		RedEdge rtrace=pdcel.redChain;
		System.err.println("start 'redindx' check.");
		do {
			safety--;
			int myEdge_index=rtrace.myEdge.edgeIndx;
			if (rtrace.myEdge!=pdcel.edges[myEdge_index]) {
				System.err.println("MyEdge index inconsistency: "+rtrace);
				bug=true;
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdcel.redChain && safety>0);
		if (safety==0)
			System.err.println("'redindx' code bombed out following red chain");
		if (!bug)
			System.err.println("Red indices all match");
		
	}
	
	/**
	 * Display colored face/edge pairs from 'glink' in screen
	 * for packing 'pnum' or pdcel.p if 'pnum'<0.
	 * @param pdcel PackDCEL
	 * @param pnum int
	 * @param glink GraphLink
	 */
	public static void visualDualEdges(PackDCEL pdcel,int pnum,GraphLink glink) {
		PackData p=null;
		if (pnum<0)
			p=pdcel.p;
		else 
			p=CPBase.packings[pnum];
		Iterator<EdgeSimple> git=glink.iterator();
		while (git.hasNext()) {
			int f=git.next().w;
			HalfEdge hfe=pdcel.faces[f].edge;
			EdgeSimple es=new EdgeSimple(hfe.origin.vertIndx,hfe.twin.origin.vertIndx);
			drawEdgeFace(p,es);
		}
	}
	
	public static StringBuilder edgeConsistency(PackDCEL pdcel,HalfEdge he) {
		StringBuilder strbld=new StringBuilder(" ["+he+"]: ");
		boolean okay=true;
		StringBuilder sb=new StringBuilder(" error(s): ");
		if (he.next.prev!=he || he.next.prev!=he) {
			sb.append(" bad links; ");
			okay=false;
		}
		if (he.twin.twin!=he) {
			sb.append(" bad twinning; ");
			okay=false;
		}
		if (he.myRedEdge!=null) {
			strbld.append(" (is red) ");
			if (he.myRedEdge.myEdge!=he) {
				sb.append(" bad red link; ");
				okay=false;
			}
			if (!he.origin.redFlag || !he.twin.origin.redFlag) {
				sb.append(" missing redFlags; ");
				okay=false;
			}
		}
		if (okay) {
			strbld.append(" looks OK.");
		}
		else
			strbld.append(sb.toString());
		return strbld;
	}
	
	public static int edgeConsistency(PackDCEL pdcel,HalfLink hlink) {
		int tick=50; // limit of 50 edges
		int count=0;
		if (hlink==null || hlink.size()==0)
			return 0;
		Iterator<HalfEdge> hits=hlink.iterator();
		while (hits.hasNext() && tick>0) {
			tick--;
			HalfEdge he=hits.next();
			StringBuilder sb=edgeConsistency(pdcel,he);
			System.out.println(sb.toString());
			count++;
		}
		System.out.println("done: count="+count);
		return count;
	}
	
	public static StringBuilder vertConsistency(PackDCEL pdcel,int v) {
		Vertex vert=pdcel.vertices[v];
		int num=vert.getNum()+1;
		StringBuilder strbld=new StringBuilder();
		strbld.append(" v="+vert.vertIndx+" num="+
				vert.getNum()+" bdryFlag="+
				vert.bdryFlag+"\n");
		HalfEdge he=vert.halfedge;
		do {
			strbld.append(" s="+he);
			if (he.myRedEdge!=null)
				strbld.append(" (r)");
			strbld.append(" t="+he.twin+" ");
			if (he.twin.myRedEdge!=null)
				strbld.append(" (r); ");
			else strbld.append("; ");
			he=he.prev.twin;
			num--;
		} while (he!=vert.halfedge && num>=0);
		strbld.append("\n");
		if (he!=vert.halfedge) {
			strbld.append("error in spoke count: num="+num+"\n");
		}
		return strbld;
	}

	public static int vertConsistency(PackDCEL pdcel,NodeLink vlist) {
		if (vlist==null || vlist.size()==0)
			vlist=new NodeLink(null,"a(1 100)");
		int count=0;
		Iterator<Integer> vit=vlist.iterator();
		System.out.println("Vert Consistency:");
		while (vit.hasNext()) {
			int v=vit.next();
			StringBuilder sb=vertConsistency(pdcel,v);
			System.out.println(sb.toString());
			count++;
		}
		System.out.println("done: count="+count);
		return count;
	}
	
	
	public static int redConsistency(RedEdge redchain) {
		int count=0;
		if (redchain==null) {
			System.err.println(" redConsistency failed: no 'redchain' given");
			return 0;
		}
		
		RedEdge rhe=redchain;
		System.out.println("redConsistency check: first edge "+rhe.myEdge);
		int safety=3000;
		do {
			safety--;
			if (rhe.twinRed!=null) {
				RedEdge rtwin=rhe.twinRed;
				if (rtwin.twinRed!=rhe) {
					System.err.println("   twinRed's don't point to one another, edge "+rhe.myEdge);
					count++;
				}
				if (rhe.myEdge.twin!=rtwin.myEdge) {
					System.err.println("   twin inconsistency, edge "+rhe.myEdge);
					count++;
				}
				if (rhe.nextRed.prevRed!=rhe || rhe.prevRed.nextRed!=rhe) {
					System.err.println("   nextRed/prevRed inconsistency, edge "+rhe.myEdge);
					count++;
				}
			}
			else {
				HalfEdge he=rhe.myEdge;
				if (he.myRedEdge!=rhe) {
					System.err.println("   inconsistency with 'myRedEdge' for edge "+he);
					count++;
				}
				if (he.next.prev!=he || he.prev.next!=he) {
					System.err.println("   next/prev inconsistency, edge "+he);
					count++;
				}
				if (he.twin.face==null || he.twin.face.faceIndx>0) {
					System.err.println("   missing ideal face for edge "+he);
					count++;
				}
				if (!he.origin.redFlag || he.origin.bdryFlag==0) {
					System.err.println("   'redFlag' or 'bdryFlag' is not right, edge "+he);
					count++;
				}
				if (he.twin.myRedEdge!=null) {
					System.err.println("   twin should not have 'myRedEdge', edge "+he);
					count++;
				}

			}
			rhe=rhe.nextRed;
		} while (rhe!=redchain && safety>0);
		if (safety==0)
			System.err.println("Exited due to safety overrun.");
		System.out.println("Done: error count = "+count);
		return count;
	}
	
	/**
	 * List spokes of each vertex and for each spoke
	 * its 'next', 'prev', and 'twin'.	
	 * @param pdcel
	 * @return int
	 */
	public static int log_edges_by_vert(PackDCEL pdcel) {
		String filename=new String("DCEL_edges_by_vert"+(rankStamp++)+"_log.txt");
		BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
		int count=0;
		try {
			
			System.out.println("'DCEL' info logged to: "+
					  tmpdir.toString()+File.separator+filename);
			
			// Vertices
			dbw.write("\nVertices: ==================== \n\n");
			for (int v=1;(v<=pdcel.vertCount && count<100);v++) {
				Vertex vert=pdcel.vertices[v];
				dbw.write("Vertex "+vert+"\n");
				HalfLink hlink=vert.getEdgeFlower();
				Iterator<HalfEdge> his=hlink.iterator();
				while (his.hasNext()) {
					dbw.write(thisSpoke(his.next()).toString()+"\n");
				}
				count++;
				dbw.write("\n");
			}
		  
			dbw.write("\n================================== end\n");
			dbw.flush();
			dbw.close();
		} catch(Exception ex) {
			System.err.print(ex.toString());
		    try {
		    	dbw.flush();
				dbw.close();
		    } catch (Exception exe) {}
		}			
		return count;
	}
	
	public static int log_full(PackDCEL pdcel) {
		String filename=new String("DCEL_VEF_"+(rankStamp++)+"_log.txt");
		BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
		int count=0;
		try {
			
			System.out.println("'DCEL' info logged to: "+
					  tmpdir.toString()+File.separator+filename);
			
			// Vertices
			dbw.write("\nVertices: ==================== \n\n");
			for (int v=1;(v<=pdcel.vertCount && count<100);v++) {
				Vertex vert=pdcel.vertices[v];
				dbw.write(thisVertex(vert).toString());
				count++;
			}
			count=0;
			
			try {
			// Edges
			dbw.write("\nEdges: ==================== \n\n");
			for (int e=1;e<=pdcel.edgeCount;e++) {
				dbw.write(thisEdge(pdcel.edges[e]).toString());
				count++;
			}
			count=0;
			
			// Faces
			dbw.write("\nFaces: ==================== \n\n");
			for (int f=1;(f<=pdcel.faceCount && count<100);f++) {
				dbw.write(thisFace(pdcel.faces[f]).toString());
				count++;
			}
			count=0;
			} catch (Exception ex) {
				dbw.write("\nSome exception in reading edges or faces");
			}
			
			// RedChain
			dbw.write("\nRedChain: =========================== \n\n");
			if (pdcel.redChain==null)
				dbw.write("    There is no 'redChain'\n");
			else {
				dbw.write(thisRedEdge(pdcel.redChain).toString());
				RedEdge rtrace=pdcel.redChain.nextRed;
				while (rtrace!=pdcel.redChain && count<100) {
					dbw.write(thisRedEdge(rtrace).toString());
					rtrace=rtrace.nextRed;
				}
				dbw.write("\n");
			}
			  
			dbw.write("================================== end\n");
			dbw.flush();
			dbw.close();
		  } catch(Exception ex) {
		      System.err.print(ex.toString());
		      try {
		  		dbw.flush();
				dbw.close();
		      } catch (Exception exe) {}
		  }			
		return count;
	}
	
	public static void printBouquet(PackDCEL pdcel) {
		System.out.println("DCEL bouquet:");
		StringBuilder strbld=null;
		for (int v=1;v<=pdcel.vertCount;v++) {
			Vertex vert=pdcel.vertices[v];
			if (vert.halfedge!=null) {
				strbld=new StringBuilder("  "+vert.vertIndx+":   ");
				int safety=15;
				HalfEdge he=vert.halfedge;
				do {
					strbld.append(" "+he.twin.origin.vertIndx);
					he=he.prev.twin; // cclw
					safety--;
				} while(he!=vert.halfedge && safety>0);
				if (safety==0) {
					strbld.append("  oops, safetied out");
				}
				if (he.twin.face==null || he.twin.face.faceIndx>=0)
					strbld.append(" "+vert.halfedge.twin.origin.vertIndx);
				System.out.println(strbld.toString());
			}
		}
		System.out.println("done");
	}
	
	public static void edgeFlowerUtils(PackDCEL pdcel,Vertex vert) {
		HalfEdge he=vert.halfedge;
		do {
			System.out.println(" spoke ("+he+"), util = "+he.eutil);
			he=he.prev.twin;
		} while (he!=vert.halfedge);
	}

	public static void drawEdgeFace(PackDCEL pdcel,ArrayList<DcelFace> facelist) {
		Iterator<DcelFace> fit=facelist.iterator();
		while (fit.hasNext()) {
			DcelFace f=fit.next();
			EdgeSimple es=new EdgeSimple(f.edge.origin.vertIndx,f.edge.twin.origin.vertIndx);
			if (pdcel.oldNew!=null) {
				es.v=pdcel.oldNew.findV(es.v);
				es.w=pdcel.oldNew.findV(es.w);
			}
			drawEdgeFace(pdcel.p,es);
			System.out.println("edge "+es+" and faceIndx "+f.faceIndx);
		}
	}

	/**
	 * Given oriented 'HalfEdge', draw it in blue, its face 
	 * on left in pale red, and the three circles.
	 * @param pdcel PackDCEL
	 * @param hfe HalfEdge
	 */
	public static void drawEFC(PackDCEL pdcel,HalfEdge hfe) {
		drawEdgeFace(pdcel,hfe);
		CPScreen cps=pdcel.p.cpScreen;
		HalfEdge he=hfe;
		int safety=100;
		do {
			cps.drawCircle(pdcel.getVertData(he),null);
			cps.rePaintAll();
			he=he.next;
			safety--;
		} while (he!=hfe && safety>0);
		if (safety==0) 
			throw new CombException("exit due to safety");
	}
	
	/**
	 * Given oriented 'HalfEdge', draw it in blue and face 
	 * on left in pale red.
	 * @param pdcel PackDCEL
	 * @param hfe HalfEdge
	 */
	public static void drawEdgeFace(PackDCEL pdcel,HalfEdge hfe) {
		Complex z1=pdcel.getVertCenter(hfe);
		Complex z2=pdcel.getVertCenter(hfe.next);
		DispFlags dispflags=new DispFlags("t5c5");
		pdcel.p.cpScreen.drawEdge(z1,z2,dispflags);
		CommandStrParser.jexecute(pdcel.p,"disp -ffc120 "+hfe.face.faceIndx);
		pdcel.p.cpScreen.rePaintAll();
	}

	/**
	 * Given oriented edge, draw in blue and face on left in pale red
	 * @param p PackData
	 * @param edge EdgeSimple 
	 */
	public static void drawEdgeFace(PackData p,EdgeSimple edge) {
		if (p==null)
			return;
		StringBuilder strbld=new StringBuilder("disp -et5c5 "+edge.v+" "+edge.w+" ");
		int[] ans=p.left_face(edge);
		if (ans[0]!=0) {
			strbld.append(" -ffc120 "+ans[0]);
		}
		CommandStrParser.jexecute(p,strbld.toString());
		p.cpScreen.rePaintAll();
	}
	
	public static void drawEuclCircles(CPScreen cps,Complex[] Z,double[] R) {
		cps.clearCanvas(true);
		int len=Z.length-1;
		for (int v=1;v<=len;v++) {
			cps.drawCircle(Z[v],R[v],new DispFlags());
			cps.drawIndex(Z[v],v,1);
		}
		cps.rePaintAll();
	}
	
	/**
	 * Display the edges in the given 'hlink'
	 * @param p PackData
	 * @param hlink HalfLink
	 */
	public static void drawHalfLink(PackData p,HalfLink hlink) {
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext()) {
			HalfEdge he=his.next();
			Complex z=p.getCenter(he.origin.vertIndx);
			Complex w=p.getCenter(he.next.origin.vertIndx);
			DispFlags dflags=new DispFlags("c195t4");
			p.cpScreen.drawEdge(z, w, dflags);
		}
		p.cpScreen.rePaintAll();
	}

	/**
	 * Draw redchain edges on parent packing
	 * @param p
	 * @param redge
	 */
	public static void drawTmpRedChain(PackData p,RedEdge redge) {
		if (p==null)
			return;
		RedEdge rtrace=redge;
		do {
			Complex z0=p.getCenter(rtrace.myEdge.origin.vertIndx);
			Complex z1=p.getCenter(rtrace.myEdge.twin.origin.vertIndx);
			DispFlags dflags=new DispFlags("c195t4");
			p.cpScreen.drawEdge(z0, z1, dflags);
			p.cpScreen.rePaintAll();
			rtrace=rtrace.nextRed;
		} while (rtrace!=null && rtrace!=redge);
		
	}
	
	/**
	 * Draw whole redChain starting at redge; this uses newly
	 * stored centers, not the parent packing
	 * @param p PackData
	 * @param redge RedEdge
	 */
	public static void drawRedChain(PackData p,RedEdge redge) {
		RedEdge rtrace=redge;
		do {
			drawRedEdge(p,rtrace);
//System.out.println(" red edge "+rtrace.myEdge);
			rtrace=rtrace.nextRed;
		} while (rtrace!=redge);
	}
	
	/**
	 * print rad/center
	 * @param p
	 * @param v
	 */
	public static void printRadCenter(PackDCEL pdcel,int v) {
		System.out.println("Rad/Center for v="+v+": rad="+
				pdcel.p.getRadius(v)+" / "+pdcel.p.getCenter(v));
	}
	
	/**
	 * Given a red edge, draw it in red on packing p's screen
	 * @param p PackData
	 * @param redge RedEdge
	 */
	public static void drawRedEdge(PackData p,RedEdge redge) {
		Complex z0=redge.getCenter();
		Complex z1=redge.nextRed.getCenter();
		DispFlags dflags=new DispFlags("c195t4");
		p.cpScreen.drawEdge(z0, z1, dflags);
		p.cpScreen.rePaintAll();
	}

	public static void printRedChain(RedEdge redge,VertexMap vmap) {
		StringBuilder sb=new StringBuilder("vertices are:\n");
		StringBuilder sbold=new StringBuilder("old indices:\n");
		RedEdge nxtre=redge;
		int safety=1000;
		do {
			safety--;
			sb.append(" -> "+nxtre.myEdge.origin.vertIndx);
			if (vmap!=null)
				sbold.append(" -> "+vmap.findW(nxtre.myEdge.origin.vertIndx));
			nxtre=nxtre.nextRed;
		} while (nxtre!=redge && safety>0);
		if (safety==0) 
			System.err.println("debug routine 'printRedChain' safetied out");
		sb.append(" -> "+nxtre.myEdge.origin.vertIndx);
		if (vmap!=null)
			sbold.append(" -> "+vmap.findW(nxtre.myEdge.origin.vertIndx));
		System.out.println(sb.toString());
		if (vmap!=null)
			System.out.println(sbold.toString());
	}
	
	public static void printRedChain(RedEdge redge) {
		printRedChain(redge,null);
	}
	
	public static void redChainDetail(PackDCEL pdcel) {
		StringBuilder strbld=new StringBuilder("RedChain Detail: \n");
		
		// redchain and twinRed's
		strbld.append("  RedChain/twinRed: \n");
		RedEdge rtrace=pdcel.redChain;
		do {
			try {
				if (rtrace.twinRed!=null)
					strbld.append("    ["+rtrace.myEdge+"]/["+rtrace.twinRed.myEdge+"] ->");
				else 
					strbld.append("    ["+rtrace.myEdge+"] ->");
				// is this a "blue" face?
				if (rtrace.nextRed.myEdge.next==rtrace.myEdge.prev) {
					strbld.append("  (this and next form BLUE face)\n");
				}
				else strbld.append("\n");

				rtrace=rtrace.nextRed;
			} catch (Exception ex) {}
		} while (rtrace!=pdcel.redChain);
		
		// do by side pairs
		if (pdcel.pairLink!=null && pdcel.pairLink.size()>0) {
			strbld.append("Side pairs:\n");
			Iterator<SideData> sit=pdcel.pairLink.iterator();
			sit.next(); // first is null
			while (sit.hasNext()) {
				SideData sdata=sit.next();
				if (sdata==null)
					continue;
				try {
					strbld.append("Side  spIndex "+sdata.spIndex+"; mateIndex "+
						sdata.mateIndex+"; start/end Edge "+sdata.startEdge.myEdge+"/"+
						sdata.endEdge.myEdge+"; pairedEdge indx "+
						sdata.mateIndex+"; label "+sdata.label+"\n");
					rtrace=sdata.startEdge.prevRed;
					while (rtrace!=sdata.endEdge) {
						rtrace=rtrace.nextRed;
						if (rtrace.twinRed!=null)
							strbld.append("    ["+rtrace.myEdge+"]/["+rtrace.twinRed.myEdge+"] ->");
						else
							strbld.append("    ["+rtrace.myEdge+"] ->");
						// is this a "blue" face?
						if (rtrace.nextRed.myEdge.next==rtrace.myEdge.prev) {
							strbld.append("    (this and next form BLUE face)\n");
						}
						else strbld.append("\n");
					}
				} catch (Exception ex) {}
			}
		}

		System.out.println(strbld.toString());
	}
	
	// ================ check consistency of twin origins ==================
	
	public static void EdgeOriginProblem(ArrayList<HalfEdge> edges) {
		Iterator<HalfEdge> eit=edges.iterator();
		System.out.println("Comparing red edge origin' to red edge prev.twin origin:");
		while (eit.hasNext()) {
			EdgeSimple es=OPrevTwinO(eit.next());
			if (es.v!=es.w)
				System.out.println(" !! "+es.toString());
				System.out.println("  "+es.toString());
		}
	}
	
	public static void RedOriginProblem(RedEdge redge) {
		RedEdge re=redge;
		System.out.println("Comparing red edge origin' to red edge prev.twin origin:");
		int safety=100;
		do {
			EdgeSimple es=OPrevTwinO(re.myEdge);
			System.out.println("   "+es.toString());
			re=re.nextRed;
			safety--;
		} while (re!=redge && safety>0);
		if (safety==0) {
			throw new DCELException("Kaboom on redchain");
		}
	}

	/**
	 * Find origin of edge and edge.prev.twin (clw spoke); 
	 * should be same.
	 * @param edge
	 * @return EdgeSimple, or null
	 */
	public static EdgeSimple OPrevTwinO(HalfEdge edge) {
		if (edge==null || edge.twin==null) {
			System.err.println(" edge (or twin) is null, vert ");
			return null;
		}
		int v=edge.origin.vertIndx;
		int w=edge.prev.twin.origin.vertIndx;
		if (v!=w) {
			System.err.println("origin inconsistency: my vert = "+v+" and prev.twin vert ="+w);
		}
		return new EdgeSimple(v,w);
	}
			

// ================= plot face based on edge 
	public static void tri_of_edge(HalfEdge []edges,int v,int w) {
		for (int e=1;e<edges.length;e++) {
			HalfEdge he=edges[e];
			if (Math.abs(he.origin.vertIndx)==v && Math.abs(he.twin.origin.vertIndx)==w)
				triVerts(he);
		}
	}

	public static void triVerts(HalfEdge edge) {
		StringBuilder sb=new StringBuilder("vertices for face of edge <"+edge.origin.vertIndx+" "
				+edge.twin.origin.vertIndx+">\n");
		sb.append(triVertString(edge));
		System.out.println(sb.toString());
	}
	
	/**
	 * List next vertices up until closure, limit of 5
	 * @param edge
	 * @return
	 */
	public static String triVertString(HalfEdge edge) {
		HalfEdge nxte=edge;
		StringBuilder sb=new StringBuilder(" "+nxte.origin.vertIndx+" --> ");
		int count=5;
		do {
			if (nxte.next==null) {
				System.err.println("edge "+nxte.toString()+" 'next' is null.");
				return sb.toString();
			}
			nxte=nxte.next;
			sb.append(nxte.origin.vertIndx+" --> ");
			count--;
		} while (nxte!=edge && count>0);
		return sb.toString();
	}
	
	public static int vertFaces(Vertex V) {
		StringBuilder sb=new StringBuilder("Vertex "+V.vertIndx+" successive faces:\n");
		int safety=12;
		HalfEdge edge=V.halfedge;
		do {
			sb.append("    next face: "+triVertString(edge)+"\n");
			edge=edge.prev.twin;
			safety--;
		} while (edge!=V.halfedge && safety>0);
		System.out.println(sb.toString());
		return 12-safety;
	}
	
	public static void faceVerts(HalfEdge edge) {
		StringBuilder sb=new StringBuilder("follow 12 edges from <"+edge.origin.vertIndx+","+
				edge.twin.origin.vertIndx+">, face "+edge.face.faceIndx+"\n");
		HalfEdge he=edge;
		sb.append(he.origin.vertIndx);
		int safety=12;
		do {
			int nbr=10;
			do {sb.append(" --> "+he.origin.vertIndx);
				he=he.next;
				nbr--;
			} while (he!=edge && nbr>0);
			System.out.println(sb.toString());
			sb=new StringBuilder();
			safety--;
		} while (he!=edge && safety>0);
		if (safety>0)
			sb.append("   ended.\n");

		System.out.println(sb.toString());		
	}
	
	/**
	 * print the edge ends around the redChain using 'myEdge' and its twin
	 * @param redge RedEdge
	 */
	public static void redChainEnds(RedEdge redge) {
		int safety=1000;
		System.out.println("Here are ends of 'redChain' edges: ");
		RedEdge nxtre=redge;
		do {
			if (nxtre.twinRed!=null) {
				System.out.println("   <"+nxtre.myEdge.origin.vertIndx+
						","+nxtre.myEdge.twin.origin.vertIndx+">;  "+
						"twinRed <"+nxtre.twinRed.myEdge.origin.vertIndx+
						","+nxtre.twinRed.myEdge.twin.origin.vertIndx+">");
			}
			else 
				System.out.println("   <"+nxtre.myEdge.origin.vertIndx+
						","+nxtre.myEdge.twin.origin.vertIndx+">");
			nxtre=nxtre.nextRed;
			safety--;
		} while (nxtre!=redge && safety>0);
		if (safety==0) {
			System.err.println("redChain closure problem");
		}
	}
	
	public static void halfedgeends(PackDCEL pdcel,dcel.HalfEdge edge) {
		System.out.println(" ("+edge.origin.vertIndx+","+edge.twin.origin.vertIndx+") ");
	}
	
	public static StringBuilder thisVertex(Vertex vert) {
		return new StringBuilder("Vertex ("+vert.hashCode()+") "+vert.vertIndx+"; "
				+ "halfedge ("+vert.halfedge.hashCode()+"): "+
				"\n    check: halfedge origin ("+vert.halfedge.origin.hashCode()+")="+
				vert.halfedge.origin.vertIndx+"\n");
	}
	
	public static StringBuilder thisSpoke(HalfEdge edge) {
		return new StringBuilder("  Spoke: "+edge+": next="+edge.next+", prev="+edge.prev+", twin="+edge.twin);
	}		
	
	public static StringBuilder thisEdge(HalfEdge edge) {
		return new StringBuilder("HalfEdge, index "+edge.edgeIndx+": <"+edge.origin.vertIndx+","+edge.twin.origin.vertIndx+">, "+
				"\n  Hash ("+edge.hashCode()+
				")\n    prev ("+edge.prev.hashCode()+"): next ("+edge.next.hashCode()+"); twin ("+edge.twin.hashCode()+")"+
				" twin.twin ("+edge.twin.twin.hashCode()+");"+
				"\n    Check: Face ("+edge.face.hashCode()+
				"); Face.halfedge ("+edge.face.edge.hashCode()+")\n");
	}
	
	public static StringBuilder thisRedEdge(RedEdge redge) {
		return new StringBuilder("This is 'RedEdge', myEdge index "+redge.myEdge.edgeIndx+": nextRed ("+redge.nextRed.hashCode()+
				"); prevRed ("+redge.prevRed.hashCode()+")\n");

	}
	
	public static StringBuilder thisFace(DcelFace face) {
		StringBuilder sb= new StringBuilder("Face ("+face.hashCode()+"); faceIndx "+face.faceIndx+
				"; edge ("+face.edge.hashCode()+"); face.edge.face ("+face.edge.face.hashCode()+")");
		sb.append("\n     corner indices are: ");
		int[] verts=face.getVerts();
		int num=verts.length;
		for (int j=0;j<num;j++) 
			sb.append("  "+verts[j]);
		sb.append("\n");
			
		return sb;
	}

	/**
	 * Given HalfEdge, find up to 10 successive 'next' edges
	 * (and their twins).
	 * @param edge HalfEdge
	 */
	public static void edge2face(HalfEdge edge) {
		if (edge==null) 
			return;
		StringBuilder strbld=new StringBuilder("edge-to-face: "+edge+
				"\n   successive edges(twins) are: ");
		HalfEdge he=edge;
		int tick=0;
		do {
			he=he.next;
			strbld.append(" --> "+he+" ("+he.twin+")");
			tick++;
		} while (he!=edge && tick<10);
		System.err.println(strbld.toString());
	}

	/**
	 * for a HalfEdge, show the 5 next edges and 5 previous edges
	 * @param edge
	 */
	public static void show4edges(HalfEdge edge) {
		if (edge==null) 
			return;
		StringBuilder strbld=new StringBuilder("Data on edge: "+edge+"\n   next's are: ");
		HalfEdge he=edge;
		for (int j=0;j<5;j++) {
			he=he.next;
			strbld.append(" --> "+he);
		}
		strbld.append("\n    prev's are: ");
		he=edge;
		for (int j=0;j<5;j++) {
			he=he.prev;
			strbld.append(" <-- "+he);
		}
		strbld.append("\n");
		System.err.println(strbld.toString());
	}
}
