package util;

import java.util.ArrayList;

import complex.Complex;
import dcel.HalfEdge;
import dcel.PackDCEL;
import exceptions.DataException;
import geometry.CommonMath;

/**
 * Utility class holding fundamental geometric data localized to 
 * (triangular only) face of some parent complex. Typically, this 
 * is temporary storage for data needed to do repacking computations
 * and was 12/2020 to accommodate DCEL structures.
 * 
 * The derived class 'TriAspects' has been used in many other
 * situtaions.e.g., with projective and affine structures and 
 * with discrete Schwarzians. 
 *  
 * @author kens
 */
public class TriData {
	
	public int hes;      // geometry, passed on creation from parent packing
	public int face;     // index of this face in parent
	
	// triples of data
	public int[] vert;   // 'Face.vert' vector (as ordered in packdata 'faces')
	double[] radii;      // concrete numbers representing labels
	Complex[] center;    // centers of circles
	double[] angles;     // angle 
	
	// edge data: [i] entry for edge <i,i+1>
	double[] invDist;    // inversive distance
	public double []schwarzian; // signed scalar coeffs for schwarzian derivative 
	
	// constructor(s)
	public TriData() { // default euclidean
		hes=0;
		vert=new int[3];
		radii=new double[3];
		allocCenters();
		angles=new double[3];
		invDist=new double[3];
		schwarzian=new double[3];
		for (int i=0;i<3;i++) {
			radii[i]=.05;
			angles[i]=Math.PI/3.0;
			invDist[i]=1.0; // default
			schwarzian[i]=0.0; // default
		}
	}

	public TriData(PackDCEL pdcel,int f) {
		this();
		try {
			hes=pdcel.p.hes;
			ArrayList<HalfEdge> eflower=pdcel.faces[f].getEdges();
			if (eflower.size()!=3)
				throw new DataException();
			for (int j=0;j<3;j++) {
				HalfEdge he=eflower.get(j);
				int v=he.origin.vertIndx;
				radii[j]=pdcel.p.getRadius(v);
				center[j]=pdcel.p.getCenter(v);
				invDist[j]=he.getInvDist();
				schwarzian[j]=he.getSchwarzain();
			}
			fillAngles();
		} catch(Exception ex) {
			throw new DataException("error building 'TriData' for face f="+f);
		}
	}

	/**
	 * allocate 'center[]' and create with value 0.0
	 */
	public void allocCenters() {
		center=new Complex[3];
		for (int j=0;j<3;j++)
			center[j]=new Complex(0.0);
	}

	public void setRadius(int j,double r) {
		if (radii==null || radii.length!=3)
			radii=new double[3];
		radii[j]=r;
	}
	
	public double getRadius(int j) {
		return radii[j];
	}
	
	public void setCenter(Complex z,int j) {
		if (center==null || center.length!=3)
			center=new Complex[3];
		center[j]=new Complex(z);
	}
	
	/**
	 * Get the center as new Complex.
	 * @param j int, index in 'vert'
	 * @return new Complex
	 */
	public Complex getCenter(int j) {
		return new Complex(center[j]);
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

	public void fillAngles() {
		for (int j=0;j<3;j++) {
			angles[j]=CommonMath.get_face_angle(radii[j],radii[(j+1)%3],radii[(j+2)%3],
					invDist[j],invDist[(j+1)%3],invDist[(j+2)%3],hes);
		}
	}
}
