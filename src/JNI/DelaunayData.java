package JNI;

import geometry.SphericalMath;

import java.util.Iterator;
import java.util.Vector;

import komplex.EdgeSimple;
import komplex.Face;
import komplex.Triangulation;
import listManip.EdgeLink;
import math.Point3D;

import complex.Complex;

/** 
 * Maintain data to pass to C/C++ Delaunay routines 'triangle' and 'qhull'.
 * 
 * Note: it appears that C++ calls fail if they try to read arrays that are 'null'. 
 * @author kstephe2 5/2015
 *
 */
public class DelaunayData {
	public int geometry;		// 0 for eucl 2D, 1 for spherical

	// point info
	public int pointCount;		// number of nodes of point set
	public double []ptX;
	public double []ptY;
	
	// bdry info if there's a boundary; note, it must close up
	public int bdryCount;	// edge count of closed bdry 
	public int []edgeV;		// indices of bdry edge starts
	public int []edgeW;     // bdry edge ends
	
	// face information
	public int myfaceCount;	// number of faces on return
	public int []triLite;	// linearized array of face indices; 
							// for face f use indices {3*f, 3*f+1, 3*f+2}
	
	// constructor(s)
	public DelaunayData() {
		geometry=0;
		pointCount=0;
		ptX=null;
		ptY=null;
		bdryCount=0;
		myfaceCount=0;
		edgeV=null;
		edgeW=null;
		triLite=null;
	}
	
	public DelaunayData(int hes,Vector<Complex> Zvec) {
		this();
		pointCount=Zvec.size();
		ptX=new double[pointCount+1]; // indexed from 1
		ptY=new double[pointCount+1]; // indexed from 1
		for (int i=0;i<pointCount;i++) {
			Complex pz=Zvec.get(i);
			ptX[i+1]=pz.x;
			ptY[i+1]=pz.y;
		}
		geometry=hes;
	}
	
	public DelaunayData(int hes,Vector<Complex> Zvec,EdgeLink elink) {
		this(hes,Zvec);
		bdryCount=0;
		if (elink!=null && elink.size()>0)
			bdryCount=elink.size();
		edgeV=new int[bdryCount];
		edgeW=new int[bdryCount];
		if (elink!=null && elink.size()>0) {
			int tick=0;
			Iterator<EdgeSimple> el=elink.iterator();
			while (el.hasNext()) {
				EdgeSimple edge=el.next();
				edgeV[tick]=edge.v;
				edgeW[tick++]=edge.w;
			}
		}
	}
	
	/**
	 * Convert 'triLite' list of face vertices to Triangulation object.
	 * @return Triangulation or null on error
	 */ 
	public Triangulation getTriangulation() {
		if (myfaceCount<1 || triLite==null || triLite.length<3*myfaceCount)
			return null;
		Triangulation tri=new Triangulation();
		tri.faceCount=myfaceCount;
		tri.nodeCount=pointCount;
		tri.faces=new Face[tri.faceCount+1];
		int tick=0;
		for (int f=1;f<=tri.faceCount;f++) {
			tri.faces[f]=new Face(3);
			tri.faces[f].vert[0]=triLite[tick++];
			tri.faces[f].vert[1]=triLite[tick++];
			tri.faces[f].vert[2]=triLite[tick++];
		}
		tri.nodes=new Point3D[tri.nodeCount+1];
		for (int v=1;v<=tri.nodeCount;v++) {
			if (geometry>0) { // 3D point
				double []svec=SphericalMath.s_pt_to_vec(new Complex(ptX[v],ptY[v]));
				tri.nodes[v]=new Point3D(svec[0],svec[1],svec[2]);
			}
			else // 2D point
				tri.nodes[v]=new Point3D(ptX[v],ptY[v],0.0);
		}
		return tri;
	}

}