package dcel;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import complex.Complex;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import geometry.EuclMath;
import geometry.CircleSimple;
import input.CPFileManager;
import komplex.EdgeSimple;
import komplex.KData;
import komplex.Triangulation;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import packing.RData;

/** 
 * This "DCEL" structure is to be used for covering spaces,
 * that is, when there is a fundamental domain and some
 * attached copies.
 * @author kstephe2, Aug 2019
 *
 */
public class FDomDCEL {
	
	public PackData p;

	public int vertCount;		// number of vertices
	public int edgeCount;
	public int faceCount;
	public int intFaceCount;	// number of interior faces (larger face indices are ideal faces)
	public int euler;           // euler characteristic of surface
	
	public FDomVertex []vertices; // indexed from 1 
	public ArrayList<FDomHalfEdge> edges;
	public ArrayList<FDomFace> faces; // indexed from 1 (first entry 'null')
	public ArrayList<FDomFace> idealFaces; // "ideal" faces ("outer" faces)
	boolean debug;
	public FDomHalfEdge alpha;
	
	// Constructor(s)
	public FDomDCEL(int n) { // fundamental domain with n edges
		p=null;
		vertCount=n;
		vertices=null;
		edges=null;
		faces=new ArrayList<FDomFace>(2);
		faces.add(1,new FDomFace(n));
		idealFaces=new ArrayList<FDomFace>(2);
		idealFaces.add(1,new FDomFace(-1));
		debug=false;
		euler=3; // impossible euler char 
	}

	/**
	 * Based on current 'bdryFaces', find all bdry edges
	 * @return ArrayList<HalfEdge>
	 */
	public ArrayList<FDomHalfEdge> getBdryEdges() {
		ArrayList<FDomHalfEdge> bdryedges=new ArrayList<FDomHalfEdge>();
		Iterator<FDomFace> bit=idealFaces.iterator();
		while (bit.hasNext()) {
			FDomHalfEdge he=bit.next().edge;
			FDomHalfEdge nxtedge=he;
			do {
				bdryedges.add(nxtedge);
				nxtedge=nxtedge.next;
			} while (nxtedge!=he);
		}
		return bdryedges;
	}
	
	/**
	 * Determine if vertex is bdry; if no, return null,
	 * else return first 'HalfEdge', that is, one with 
	 * 'twin.face' being an ideal face. (This normally is 
	 * 'v.halfedge', but calling routine can use return
	 * info to reset 'v.halfedge' if desired.) 
	 * @param v Vertex
	 * @return HalfEdge, null if not bdry vertex
	 */
	public FDomHalfEdge vertIsBdry(FDomVertex v) {
		FDomHalfEdge nxtedge=v.halfedge;
		do {
			if (idealFaces!=null && idealFaces.contains(nxtedge.twin.face))
				return nxtedge;
			nxtedge=nxtedge.prev.twin;
		} while(nxtedge!=v.halfedge);
		return null;
	}
		
	/**
	 * Is this a boundary edge?
	 * @param he FDomHalfEdge
	 * @return boolean
	 */
	public boolean edgeIsBdry(FDomHalfEdge he) {
		if (idealFaces.contains(he.face))
			return true;
		return false;
	}

	/**
	 * Find if there is a halfedge from v to w. 
	 * @param v int
	 * @param w int
	 * @return HalfEdge, null if not found
	 */
	public FDomHalfEdge findEdge(int v,int w) {
		Iterator<FDomHalfEdge> eit=edges.iterator();
		while (eit.hasNext()) {
			FDomHalfEdge he=eit.next();
			if (he.origin.vertIndx==v) {
				ArrayList<FDomHalfEdge> eflower=he.origin.getEdgeFlower();
				Iterator<FDomHalfEdge> heit=eflower.iterator();
				while (heit.hasNext()) {
					FDomHalfEdge hfe=heit.next();
					if (hfe.twin.origin.vertIndx==w)
						return hfe;
				}
			}
		}
		return null;
	}
	
	/**
	 * Return the vertices forming the face starting at edge from
	 * v to w, one of its petals. 
	 * @param bouquet
	 * @param v int, vertex
	 * @param w int, petal
	 * @return int[], null on error
	 */
	// TODO: in the middle of development

//	public static int []getFace(int [][]bouquet,int v,int w) {
//		int vcount=bouquet.length-1;
//		if (v<1 || v> vcount) {
//			return null;
//		}
//		int[] flower=bouquet[v];
		
//		int indx_vw=nghb(v,w,bouquet);
//		if (indx_vw<0)
//			return null;
		
//		NodeLink nlink=new NodeLink();
//		nlink.add(v);
//		int nextv=w;
//		int holdv=v;
//		int safety=bouquet.length;
//		while (nextv!=holdv && safety>0) {
//			safety--;
//			nlink.add(nextv);
//			flower=bouquet[nextv];
//			int num=flower.length;
//			int indx=nghb(nextv,v,bouquet);
//			if (indx==0) // first and last repeat
//				indx=num-2;
//			else
//				indx=(indx+num-1)%num;
//			v=nextv;
//			nextv=flower[indx];
//		}
//		if (safety==0)
//			throw new CombException("loop crash in 'getFace'");
		
//		int n=nlink.size();
//		int []list=new int[n];
//		for (int k=0;k<n;k++)
//			list[k]=nlink.get(k);
		
//		return list;
//	}
	
}

