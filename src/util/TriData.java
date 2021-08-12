package util;

import dcel.HalfEdge;
import dcel.PackDCEL;
import exceptions.DataException;
import geometry.CommonMath;
import listManip.HalfLink;

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
	
	// Caution: edge data [i] entry is for edge OPPOSITE to vertex i.
	double[] invDist;    // inversive distance: null if none non-trivial
	
	// constructor(s)
	public TriData() {
		this(null);
	}
	
	public TriData(PackDCEL pdc) { // default euclidean
		pdcel=pdc;
		hes=0;
		invDist=null;
		if (pdcel.p!=null)
			hes=pdcel.p.hes;
		vert=new int[3];
		radii=new double[3];
		for (int j=0;j<3;j++) {
			radii[j]=.05;
		}
	}

	public TriData(PackDCEL pdc,int f) {
		this(pdc);
		try {
			face=f;
			hes=pdcel.p.hes;
			HalfLink eflower=pdcel.faces[f].getEdges();
			if (eflower.size()!=3)
				throw new DataException();
			for (int j=0;j<3;j++) {
				HalfEdge he=eflower.get(j);
				int v=he.origin.vertIndx;
				vert[j]=v;
				radii[j]=pdcel.p.getRadius(v);
				
				// only create invDist if non-trivial is found
				double ivd=he.getInvDist();
				if (ivd!=1.0) {
					if (invDist==null) {
						invDist=new double[3];
						invDist[0]=invDist[1]=invDist[2]=1.0;
					}
					setInvDist((j+2)%3,ivd); // store for opposite vert
				}
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
	
	/**
	 * are there any non-trivail inversive distances?
	 * @return boolean
	 */
	public boolean hasInvDist() {
		if (invDist!=null)
			return true;
		return false;
	}
	
	/**
	 * Caution: invDist[j] is for edge opposite vertex j.
	 * @param j int
	 * @return double, 1.0 if 'invDist' is null
	 */
	public double getInvDist(int j) {
		if (invDist==null)
			return 1.0;
		if (Math.abs(invDist[j]-1.0)<.000001)
			return 1.0;
		return invDist[j];
	}
	
	/**
	 * Caution: invDist[j] is for edge opposite vertex j.
	 * Note: this call may have no effect if 'sch' is 1.0.
	 * @param j int
	 * @param sch double
	 */
	public void setInvDist(int j, double sch) {
		if (invDist==null && sch!=1.0) {
			invDist=new double[3];
			invDist[0]=invDist[1]=invDist[2]=1.0;
			invDist[j]=sch;
		}
		else 
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
	 * 'rad' at v itself. Recall, if 'invDist' exists, then
	 * invDist[j] is for edge opposite vertex j.
	 * @param j int
	 * @param rad double
	 * @return double
	 */
	public double compOneAngle(int j,double rad) {
		if (invDist!=null)
			return CommonMath.get_face_angle(rad,radii[(j+1)%3],radii[(j+2)%3],
				getInvDist(j),getInvDist((j+1)%3),getInvDist((j+2)%3),hes);
		return CommonMath.get_face_angle(rad,radii[(j+1)%3],radii[(j+2)%3],hes);
	}

	/**
	 * Return angle at vert[j] using current data. Recall, if 
	 * 'invDist' exists, then invDist[j] is for edge opposite vertex j.
	 * @param j int
	 * @return double
	 */
	public double compOneAngle(int j) {
		if (invDist!=null)
			return CommonMath.get_face_angle(radii[j],radii[(j+1)%3],radii[(j+2)%3],
				getInvDist(j),getInvDist((j+1)%3),getInvDist((j+2)%3),hes);
		return CommonMath.get_face_angle(radii[j],radii[(j+1)%3],radii[(j+2)%3],hes);
	}

}
