package util;

import java.util.Iterator;

import combinatorics.komplex.HalfEdge;
import dcel.PackDCEL;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.CommonMath;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;

/**
 * Utility class holding fundamental geometric data needed for
 * repacking computations, localized to (triangular only) face 
 * of some parent complex. Typically, this is temporary storage
 * during repack computations and is NOT routinely maintained.
 * 
 * The derived class 'TriAspects' has been used in additional
 * situtaions.e.g., with projective and affine structures and 
 * with discrete Schwarzians. 'labels' are typically of use only
 * in the eucl setting and are treated as homogeneous radii --
 * that is, only their ratios are important. 
 *  
 * @author kens
 */
public class TriData {
	
	public PackDCEL pdc;  // parent DCEL
	
	public int hes;      // geometry, passed on creation from parent packing
	public int face;     // TODO: get rid of index of this face
	public HalfEdge baseEdge;  
	
	// triples of data
	public int[] vert;   // vertices (as ordered in packdata 'faces')
	public double[] radii;  // concrete numbers representing labels
	public double[] labels; // labels for verts: often homogeneous coords

	// TODO: Rejigger: changing so edge data [i] is for edge whose
	//       origin is vertex i.
	// Caution: edge data [i] entry is for edge OPPOSITE to vertex i.
	double[] invDist;    // inversive distance: null if none non-trivial
	
	// constructor(s)
	public TriData() {
		this(null);
	}
	
	public TriData(PackDCEL pdcel) { // default euclidean
		pdc=pdcel;
		hes=0;
		invDist=null; // only allocate when needed
		if (pdc!=null && pdc.p!=null)
			hes=pdc.p.hes;
		vert=new int[3];
		radii=new double[3];
		for (int j=0;j<3;j++) {
			radii[j]=.05;
		}
	}

	public TriData(PackDCEL pdcel,combinatorics.komplex.DcelFace fce) {
		this(pdcel);
		baseEdge=fce.edge;
		face=baseEdge.face.faceIndx; 
		try {
			hes=pdcel.p.hes;
			int j=0;
			HalfEdge he=baseEdge;
			do {
				vert[j]=he.origin.vertIndx;
				radii[j]=pdc.getVertRadius(he);
				if (hes==0) {
					if (labels==null) {
						labels=new double[3];
					}
					labels[j]=radii[j];
				}
				// only create invDist if non-trivial is found
				double ivd=he.getInvDist();
				if (ivd!=1.0) {
					if (invDist==null) {
						invDist=new double[3];
						invDist[0]=invDist[1]=invDist[2]=1.0;
					}
					setInvDist(j,ivd); // store
				}
				j++;
				he=he.next;
			} while (j<3);
		} catch(Exception ex) {
			throw new DataException("error building 'TriData' for face f="+face);
		}
	}
	
	/**
	 * Upload radii to parent packing. Note: record only for edges
	 * associated with this face, since the same vertex may get 
	 * different radii for other faces (e.g., as recorded in 
	 * appropriate 'RedEdge's). 
	 * @param pdcel PackDCEL 
	 */
	public void uploadRadii(PackDCEL pdcel) {
		HalfEdge he=baseEdge;
		for (int j=0;j<3;j++) {
			pdcel.setRad4Edge(he, radii[j]);
			he=he.next;
		}
	}
	
	public void uploadRadii() {
		uploadRadii(pdc);
	}
	
	public double getRadius(int j) {
		return radii[j];
	}
	
	public double getLabel(int j) {
		return labels[j];
	}
	
	public void setLabel(double lbl,int j) {
		labels[j]=lbl;
	}

	/**
	 * Find the 'HalfEdge' with index j.
	 * @param j int, 0, 1, or 2
	 * @return
	 */
	public HalfEdge getHalfEdge(int j) {
		if (j==0)
			return baseEdge;
		if (j==1)
			return baseEdge.next;
		return baseEdge.next.next;
	}
	
	/**
	 * are there any non-trivial inversive distances for 
	 * this face?
	 * @return boolean
	 */
	public boolean hasInvDist() {
		if (invDist!=null)
			return true;
		return false;
	}
	
	/**
	 * Caution: invDist[j] is for edge <j,j+1>
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
	 * @param ivd double
	 */
	public void setInvDist(int j, double ivd) {
		if (invDist==null && ivd!=1.0) {
			invDist=new double[3];
			invDist[0]=invDist[1]=invDist[2]=1.0;
			invDist[j]=ivd;
		}
		else 
			invDist[j]=ivd;
	}
	
	/** 
	 * Find local index (0,1, or 2) for vertex v in this 
	 * triangle; return -1 if v is not among its vertices.
	 * @param v int
	 * @return int, local index or -1 on failure
	 */
	public int vertIndex(int v) {
		for (int j=0;j<3;j++)
			if (vert[j]==v) 
				return j;
		return -1;
	}

	/** 
	 * Find local index (0,1, or 2) for vertex v which
	 * is origin of 'edge' in this face; return -1 
	 * if not an edge of this face.
	 * @param edge HalfEdge
	 * @return int, local index or -1 on failure
	 */
	public int edgeIndex(HalfEdge edge) {
		if (edge==baseEdge)
			return 0;
		if (edge==baseEdge.next)
			return 1;
		if (edge==baseEdge.next.next)
			return 2;
		return -1;
	}
	
	/**
	 * Return angle at vert[j] using current data. If 
	 * 'invDist' exists, then invDist[j] is for edge <j,j+1>
	 * @param j int
	 * @return double
	 */
	public double compOneAngle(int j) {
		if (invDist!=null)
			return CommonMath.get_face_angle(radii[j],radii[(j+1)%3],
					radii[(j+2)%3],getInvDist(j),
					getInvDist((j+1)%3),getInvDist((j+2)%3),hes);
		return CommonMath.get_face_angle(radii[j],radii[(j+1)%3],
				radii[(j+2)%3],hes);
	}

	
	/**
	 * Return angle at v=vert[j] using current data, but radius 
	 * 'rad' at v itself. If 'invDist' exists, then invDist[j] is 
	 * for edge <j,j+1>
	 * @param j int
	 * @param rad double
	 * @return double
	 */
	public double compOneAngle(int j,double rad) {
		if (invDist!=null)
			return CommonMath.get_face_angle(rad,radii[(j+1)%3],
					radii[(j+2)%3],getInvDist(j),getInvDist((j+1)%3),
					getInvDist((j+2)%3),hes);
		return CommonMath.get_face_angle(rad,radii[(j+1)%3],
				radii[(j+2)%3],hes);
	}
	
	/**
	 * Return angle at v=vert[j] using 'radii', except radius 
	 * at v itself is its current radius multiplied by 'factor'. 
	 * @param j int
	 * @param factor double
	 * @return double
	 */
	public double compFactorAngle(int j,double factor) {
		double rad=radii[j]*factor;
		if (invDist!=null)
			return CommonMath.get_face_angle(rad,radii[(j+1)%3],
					radii[(j+2)%3],getInvDist(j),
					getInvDist((j+1)%3),getInvDist((j+2)%3),hes);
		return CommonMath.get_face_angle(rad,radii[(j+1)%3],
				radii[(j+2)%3],hes);
	}

	/**
     * We may want to move local 'radii' or 'labels' (typically 
     * labels are only for eucl case) stored in 'PackDCEL.triData'
     * to the radii of the parent packing. Modes are: 
     *   1: use local 'radii', 
     *   2: use 'labels' 
     * Some vertices will have different labels in different 
     * faces; the DCEL structure expects this for red vertices.
     * For other vertices, 'Vertex' get the value in the first
     * face containing that vertex. Recompute curvatures.
     * @param p PackData
     * @param vlist NodeLink
     * @param mode int
     * @return int, count
     */
    public static int reapRadii(PackData p,NodeLink vlist,int mode) {
    	PackDCEL pdcel=p.packDCEL;
    	if (pdcel.triData==null || 
    			pdcel.triData.length<(pdcel.faceCount+1)) 
    		throw new ParserException("'triData' allocation problem");
    	if (vlist==null || vlist.size()==0)
    		vlist=new NodeLink(p,"a");
    	Iterator<Integer> vis=vlist.iterator();
    	int count=0;
    	while (vis.hasNext()) {
    		combinatorics.komplex.Vertex vert=pdcel.vertices[vis.next()];
    		if (vert.redFlag) {
    			HalfLink spokes=vert.getEdgeFlower();
    			Iterator<HalfEdge> sis=spokes.iterator();
    			while (sis.hasNext()) {
    				HalfEdge he=sis.next();
    				int f=he.face.faceIndx;
    				int indx=pdcel.triData[f].edgeIndex(he);
    				if (he.myRedEdge!=null) {
    	    			if (mode==2)
        					pdcel.setRad4Edge(he,pdcel.triData[f].labels[indx]);
    	    			else
        					pdcel.setRad4Edge(he,pdcel.triData[f].radii[indx]);
    				}
    			}
    		}
    		else { // store from first face containing v
    			int v=vert.vertIndx;
    			combinatorics.komplex.DcelFace face=pdcel.vertices[v].halfedge.face;
    			TriData vtd=pdcel.triData[face.faceIndx];
    			int vindx=vtd.vertIndex(v);
    			if (mode==2)
    				p.setRadius(v,vtd.labels[vindx]);
    			else
    				p.setRadius(v,vtd.radii[vindx]);
    		}
    		count++;
    	}
    	p.fillcurves();
    	return count;
    }
    
}
