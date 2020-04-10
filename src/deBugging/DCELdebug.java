package deBugging;

import java.util.ArrayList;
import java.util.Iterator;

import dcel.HalfEdge;
import dcel.PackDCEL;

public class DCELdebug {

	public static void printRedChain(PackDCEL pdcel) {
		if (pdcel.redChain==null) {
			System.err.println("'redChain' is null");
			return;
		}
		dcel.RedHEdge nxtre=pdcel.redChain;
		StringBuilder sb=new StringBuilder("vertices are:\n");
		do {
			sb.append(" "+nxtre.myedge.origin.vertIndx);
			nxtre=nxtre.next;
		} while (nxtre!=pdcel.redChain);
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
	
	public static void usualflower(PackDCEL pdcel,dcel.Vertex vert) {
		StringBuilder sb=new StringBuilder("flower for vertex "+vert.vertIndx+"\n");
		int []flower=pdcel.usualFlower(vert);
		for (int j=0;j<flower.length;j++)
			sb.append(" "+flower[j]);
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
}
