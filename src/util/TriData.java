package util;

import java.util.ArrayList;

import dcel.HalfEdge;
import dcel.PackDCEL;
import exceptions.DataException;
import geometry.CommonMath;

/**
 * Utility class holding fundamental geometric data needed for
 * repacking computations, localized to (triangular only) face 
 * of some parent complex. Typically, this is temporary storage
 * during repack computations and is NOT routinely maintained; 
 * started 12/2020 to accommodate repacking for DCEL structures.
 * 
 * The derived class 'TriAspects' has been used in many other
 * situtaions.e.g., with projective and affine structures and 
 * with discrete Schwarzians. 
 *  
 * @author kens
 */
public class TriData {
	
	public PackDCEL pdcel;  // parent DCEL
	
	public int hes;      // geometry, passed on creation from parent packing
	public int face;     // index of this face in parent
	
	// triples of data
	public int[] vert;   // 'Face.vert' vector (as ordered in packdata 'faces')
	public double[] radii;      // concrete numbers representing labels
	
	// edge data: [i] entry for edge <i,i+1>
	public double[] invDist;    // inversive distance
	
	// constructor(s)
	public TriData() {
		this(null);
	}
	
	public TriData(PackDCEL pdc) { // default euclidean
		pdcel=pdc;
		hes=0;
		if (pdcel.p!=null)
			hes=pdcel.p.hes;
		vert=new int[3];
		radii=new double[3];
		invDist=new double[3];
		for (int i=0;i<3;i++) {
			radii[i]=.05;
			invDist[i]=1.0; // default to tangency
		}
	}

	public TriData(PackDCEL pdc,int f) {
		this(pdc);
		try {
			face=f;
			hes=pdcel.p.hes;
			ArrayList<HalfEdge> eflower=pdcel.faces[f].getEdges();
			if (eflower.size()!=3)
				throw new DataException();
			for (int j=0;j<3;j++) {
				HalfEdge he=eflower.get(j);
				int v=he.origin.vertIndx;
				vert[j]=v;
				radii[j]=pdcel.p.getRadius(v);
				invDist[j]=he.getInvDist();
			}
		} catch(Exception ex) {
			throw new DataException("error building 'TriData' for face f="+f);
		}
	}
	
	/**
	 * Upload all radii to the packing. Note: record only for the edges
	 * associated with 'face', since the same vertex may get different 
	 * radii for other faces (e.g.,as recorded in appropriate 'RedHEdge's) 
	 */
	public void uploadRadii() {
		HalfEdge he=pdcel.faces[face].edge;
		for (int j=0;j<3;j++) {
			pdcel.setRad4Edge(he, radii[j]);
			he=he.next;
		}
	}

	/**
	 * allocate 'center[]' and create with value 0.0
	 */
	public void setRadius(int j,double r) {
		if (radii==null || radii.length!=3)
			radii=new double[3];
		radii[j]=r;
	}
	
	public double getRadius(int j) {
		return radii[j];
	}
	
	public double getInvDist(int j) {
		return invDist[j];
	}
	
	public void setInvDist(int j, double sch) {
		invDist[j]=sch;
	}
	
	/** 
	 * Find local index (0,1, or 2) for vertex v in this 
	 * triangle; return -1 if v is not among its vertices.
	 * @param v int
	 * @return int, local index or -1 on failure
	 */
	public int vertIndex(int v) {
		for (int j=0;j<3;j++)
			if (vert[j]==v) return j;
		return -1;
	}

	/**
	 * Return angle at v=vert[j] using current data, but radius 
	 * 'rad' at v itself.
	 * @param j int
	 * @param rad double
	 * @return double
	 */
	public double compOneAngle(int j,double rad) {
		return CommonMath.get_face_angle(rad,radii[(j+1)%3],radii[(j+2)%3],
				invDist[j],invDist[(j+1)%3],invDist[(j+2)%3],hes);
	}

	/**
	 * Return angle at vert[j] using current data.
	 * @param j
	 * @return
	 */
	public double compOneAngle(int j) {
		return CommonMath.get_face_angle(radii[j],radii[(j+1)%3],radii[(j+2)%3],
				invDist[j],invDist[(j+1)%3],invDist[(j+2)%3],hes);
	}

}
