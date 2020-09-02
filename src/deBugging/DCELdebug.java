package deBugging;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import complex.Complex;
import dcel.Face;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.PreRedVertex;
import dcel.RedHEdge;
import dcel.RedVertex;
import dcel.Vertex;
import exceptions.DCELException;
import input.CPFileManager;
import input.CommandStrParser;
import komplex.EdgeSimple;
import komplex.GraphSimple;
import listManip.GraphLink;
import packing.PackData;
import util.DispFlags;

public class DCELdebug {
	
	static File tmpdir=new File(System.getProperty("java.io.tmpdir"));
	static int rankStamp=1; // progressive number to distinguish file instances
	
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
			
			// Edges
			dbw.write("\nEdges: ==================== \n\n");
			Iterator<HalfEdge> eits=pdcel.tmpEdges.iterator();
			while (eits.hasNext() && count<100) {
				dbw.write(thisEdge(eits.next()).toString());
				count++;
			}
			count=0;
			
			// Faces
			dbw.write("\nFaces: ==================== \n\n");
			for (int f=1;(f<pdcel.tmpFaceList.size() && count<100);f++) {
				dbw.write(thisFace(pdcel.tmpFaceList.get(f)).toString());
				count++;
			}
			count=0;
			
			// RedChain
			dbw.write("\nRedChain: =========================== \n\n");
			if (pdcel.redChain==null)
				dbw.write("    There is no 'redChain'\n");
			else {
				dbw.write(thisRedEdge(pdcel.redChain).toString());
				RedHEdge rtrace=pdcel.redChain.nextRed;
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

	public static void drawOrderEdgeFace(PackData p,ArrayList<HalfEdge> elist) {
		Iterator<HalfEdge> eit=elist.iterator();
		while (eit.hasNext()) {
			HalfEdge he=eit.next();
			drawEdgeFace(p,new EdgeSimple(he.origin.vertIndx,he.twin.origin.vertIndx));
		}
	}

	/**
	 * draw on pack p the edges dual to the give dual edges and
	 * their faces.
	 * @param p PackData
	 * @param glink GraphLink
	 */
	public static void drawEdgeFace(PackData p,GraphLink glink) {
		Iterator<EdgeSimple> git=glink.iterator();
		while (git.hasNext()) {
			GraphSimple gs=(GraphSimple)git.next();
			EdgeSimple es=p.packDCEL.dualEdge_to_Edge(gs);
			drawEdgeFace(p,es);
		}
	}
	
	public static void drawEdgeFace(PackData p,ArrayList<Face> facelist) {
		Iterator<Face> fit=facelist.iterator();
		while (fit.hasNext()) {
			Face f=fit.next();
			EdgeSimple es=new EdgeSimple(f.edge.origin.vertIndx,f.edge.twin.origin.vertIndx);
			drawEdgeFace(p,es);
			System.out.println("edge "+es+" and faceIndx "+f.faceIndx);
		}
	}
	
	public static void drawEdgeFace(PackData p,HalfEdge hfe) {
		drawEdgeFace(p,new EdgeSimple(hfe.origin.vertIndx,hfe.twin.origin.vertIndx));
	}
	
	/**
	 * Given oriented edge, draw in blue and face on left in pale red
	 * @param p PackData
	 * @param edge EdgeSimple 
	 */
	public static void drawEdgeFace(PackData p,EdgeSimple edge) {
		if (p!=null) {
			StringBuilder strbld=new StringBuilder("disp -et5c5 "+edge.v+" "+edge.w+" ");
			int[] ans=p.left_face(edge);
			if (ans[0]!=0) {
				strbld.append(" -ffc120 "+ans[0]);
			}
			CommandStrParser.jexecute(p,strbld.toString());
		}
	}
	
	/**
	 * Draw whole redChain starting at redge
	 * @param p PackData
	 * @param redge RedHEdge
	 */
	public static void drawRedChain(PackData p,RedHEdge redge) {
		RedHEdge rtrace=redge;
		do {
			drawRedEdge(p,rtrace);
			rtrace=rtrace.nextRed;
		} while (rtrace!=redge);
	}
	
	/**
	 * Given a red edge, draw it in red on packing p's screen
	 * @param p PackData
	 * @param redge RedHEdge
	 */
	public static void drawRedEdge(PackData p,RedHEdge redge) {
		Complex z0=redge.getCenter();
		Complex z1=redge.nextRed.getCenter();
		DispFlags dflags=new DispFlags("c195t4");
		p.cpScreen.drawEdge(z0, z1, dflags);
		p.cpScreen.repaint();
	}
	
	public static void printRedChain(RedHEdge redge) {
		StringBuilder sb=new StringBuilder("vertices are:\n");
		RedHEdge nxtre=redge;
		do {
			sb.append(" --> "+nxtre.myEdge.origin.vertIndx);
			nxtre=nxtre.nextRed;
		} while (nxtre!=redge);
		sb.append(" --> "+nxtre.myEdge.origin.vertIndx);
		System.out.println(sb.toString());
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
	
	public static void RedOriginProblem(RedHEdge redge) {
		RedHEdge re=redge;
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
	public static void tri_of_edge(PackDCEL pdcel,int v,int w) {
		Iterator<HalfEdge> eit=pdcel.tmpEdges.iterator();
		while (eit.hasNext()) {
			HalfEdge he=eit.next();
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
	
	/** 
	 * list edges forming all the faces at 'redV'
	 * @param redV RedVertex
	 * @return int, count
	 */
	public static int redVertFaces(PreRedVertex redV) {
		RedHEdge firstRE=redV.redSpoke[0];
		if (firstRE==null) {
			System.err.println("redV = "+redV.vertIndx+": problem with 'spokes' or 'redSpoke'");
			return 0;
		}
		StringBuilder sb=new StringBuilder("RedVertex "+redV.vertIndx+" successive faces:\n");
		int safety=12;
		HalfEdge edge=firstRE.myEdge;
		do {
			sb.append("    next face: "+triVertString(edge)+"\n");
			edge=edge.prev.twin;
			safety--;
		} while (edge!=firstRE.myEdge && safety>0);
		System.out.println(sb.toString());
		return 12-safety;
	}
		
	public static int spokeFaces(RedVertex redV) {
		if (redV.spokes==null)
			return 0;
		StringBuilder sb=new StringBuilder("RedVertex "+redV.vertIndx+":\n");
		int n=redV.spokes.length;
		for (int j=0;j<n;j++) {
			sb.append("  spoke "+j+": "+triVertString(redV.spokes[j])+"\n");
		}
		System.out.println(sb.toString());
		return n;
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
	 * @param redge RedHEdge
	 */
	public static void redChainEnds(RedHEdge redge) {
		int safety=1000;
		System.out.println("Here are ends of 'redChain' edges: ");
		RedHEdge nxtre=redge;
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
	
	public static void showEdges(PackDCEL pdcel) {
		showEdges(pdcel,pdcel.tmpEdges);
	}
	
	public static void showEdges(PackDCEL pdcel,ArrayList<HalfEdge> edges) {
		if (pdcel.tmpEdges==null) {
			System.err.println("'edges' is null");
			return;
		}
		System.out.println("First 25 edges: ");
		int n=0;
		Iterator<HalfEdge> heit=edges.iterator();
		while (heit.hasNext() && n<25) {
			HalfEdge edge=heit.next();
			StringBuilder sb=new StringBuilder("edge=("+edge.origin.vertIndx+","+edge.twin.origin.vertIndx+"),  ");
			sb.append("prev=("+edge.prev.origin.vertIndx+","+edge.prev.twin.origin.vertIndx+"),  ");
			sb.append("next=("+edge.next.origin.vertIndx+","+edge.next.twin.origin.vertIndx+")");
			System.out.println(sb.toString());
			n++;
		}
		System.out.println("done after n="+n+"\n");
	}
	
	public static StringBuilder thisVertex(Vertex vert) {
		return new StringBuilder("Vertex ("+vert.hashCode()+") "+vert.vertIndx+"; "
				+ "halfedge ("+vert.halfedge.hashCode()+"): "+
				"\n    check: halfedge origin ("+vert.halfedge.origin.hashCode()+")="+
				vert.halfedge.origin.vertIndx+"\n");
	}
	
	public static StringBuilder thisEdge(HalfEdge edge) {
		return new StringBuilder("HalfEdge, index "+edge.edgeIndx+": <"+edge.origin.vertIndx+","+edge.twin.origin.vertIndx+">, "+
				"\n  Hash ("+edge.hashCode()+
				")\n    prev ("+edge.prev.hashCode()+"): next ("+edge.next.hashCode()+"); twin ("+edge.twin.hashCode()+")"+
				" twin.twin ("+edge.twin.twin.hashCode()+");"+
				"\n    Check: Face ("+edge.face.hashCode()+
				"); Face.halfedge ("+edge.face.edge.hashCode()+")\n");
	}
	
	public static StringBuilder thisRedEdge(RedHEdge redge) {
		return new StringBuilder("This is 'RedHEdge', myEdge index "+redge.myEdge.edgeIndx+": nextRed ("+redge.nextRed.hashCode()+
				"); prevRed ("+redge.prevRed.hashCode()+")\n");

	}
	
	public static StringBuilder thisFace(Face face) {
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
	 * for a HalfEdge, show the 5 next edges and 5 previous edges
	 * @param edge
	 */
	public static void show5edges(HalfEdge edge) {
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
