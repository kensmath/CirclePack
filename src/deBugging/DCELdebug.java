package deBugging;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import dcel.Face;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedHEdge;
import dcel.Vertex;
import input.CPFileManager;
import input.CommandStrParser;
import komplex.EdgeSimple;
import packing.PackData;

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
			Iterator<HalfEdge> eits=pdcel.edges.iterator();
			while (eits.hasNext() && count<100) {
				dbw.write(thisEdge(eits.next()).toString());
				count++;
			}
			count=0;
			
			// Faces
			dbw.write("\nFaces: ==================== \n\n");
			for (int f=1;(f<pdcel.faces.size() && count<100);f++) {
				dbw.write(thisFace(pdcel.faces.get(f)).toString());
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
		int v=redge.myEdge.origin.vertIndx;
		int w=redge.myEdge.twin.origin.vertIndx;
		CommandStrParser.jexecute(p,"disp -et9c195 "+v+" "+w);
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
	
	public static void faceVerts(PackDCEL pdcel,dcel.Face face) {
		StringBuilder sb=new StringBuilder("vertices for face "+face.faceIndx+"\n");
		HalfEdge he=face.edge;
		do {
			sb.append(" "+he.origin.vertIndx);
			he=he.next;
		} while (he!=face.edge);
		System.out.println(sb.toString());		
	}
	
	public static void halfedgeends(PackDCEL pdcel,dcel.HalfEdge edge) {
		System.out.println(" ("+edge.origin.vertIndx+","+edge.twin.origin.vertIndx+") ");
	}
	
	public static void showEdges(PackDCEL pdcel) {
		showEdges(pdcel,pdcel.edges);
	}
	
	public static void showEdges(PackDCEL pdcel,ArrayList<HalfEdge> edges) {
		if (pdcel.edges==null) {
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
