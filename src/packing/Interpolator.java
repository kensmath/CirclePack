package packing;

import java.util.ArrayList;

import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import math.Point3D;
import util.ZRhold;

public class Interpolator {
	PackData p;
	PackData q;
	ArrayList<ArrayList<ZRhold>> array;
	ArrayList<ZRhold> bottom;
	ArrayList<ZRhold> top;
	int N; // default to 20
	
	// Constructors
	public Interpolator(PackData pack,PackData qack) {
		p=pack;
		q=qack;
		bottom=loadZR(p);
		top=loadZR(q);
		N=20; // default
	}
	
	public Interpolator(PackData pack,PackData qack,int n) {
		this(pack,qack);
		N=n;
		fillArray();
	}
	
	public Interpolator(ArrayList<ZRhold> bot,PackData pack,int n) {
		bottom=bot;
		p=pack;
		top=loadZR(p);
		N=n;
		fillArray();
	}
	
	public ArrayList<ZRhold> get(int j) {
		return array.get(j);
	}
	
	
	/**
	 * Load 'ZRhold' array for this packing. In hyp
	 * case, convert to eucl for interpolation steps.
	 * @param p PackData
	 * @return ArrayList<ZRhold>
	 */
	public static ArrayList<ZRhold> loadZR(PackData p) {
		ArrayList<ZRhold> ary=new ArrayList<ZRhold>();
		ary.add(null); // empty
		for (int v=1;v<=p.nodeCount;v++) {
			CircleSimple cs=p.getData(v);
			if (p.hes<00) {
				cs=HyperbolicMath.h_to_e_data(cs);
			}
			ary.add(new ZRhold(cs.center,cs.rad));
		}
		return ary;
	}
	
	/**
	 * Store the centers/radii to the packing. Note
	 * that in the hyp case, the 'ZRhold' data was
	 * converted to eucl, so we have to convert back. 
	 * Also note that for red vertices v, we only 
	 * update only the pertinent rededge data, not 
	 * the data in 'packDCEL.vertices[]'. 
	 * @param p
	 * @param ArrayList<ZRhold>
	 */
	public static void restoreZR(PackData p,
			ArrayList<ZRhold> azrh) {
		for (int v=1;v<=p.nodeCount;v++) {
			CircleSimple cs=new CircleSimple(
					azrh.get(v).getCenter(),azrh.get(v).getRadius());
			if (p.hes<0) // convert
				cs=HyperbolicMath.e_to_h_data(cs);
			Vertex vert=p.packDCEL.vertices[v];
			// a normal 'Vertex'? 
			if (!vert.redFlag) {
				vert.center=cs.center;
				vert.rad=cs.rad;
			}

			// else, go clw to reach a spoke that has 'myRedEdge'
			else {
				HalfEdge he=vert.halfedge; 
				while (he.myRedEdge==null) {
					he=he.twin.next;
				}
				he.myRedEdge.setCenter(cs.center);
				he.myRedEdge.setRadius(cs.rad);
			}
		}
		return;
	}
	
	/**
	 * Fill the final array of 'ZRhold' entries for 
	 * all vertices, all stages, 0 to N. 
	 */
	public void fillArray() {
		array=new ArrayList<ArrayList<ZRhold>>();
		array.add(null);
		double factor=1/((double)N);
		for (int k=1;k<N;k++) {
			double x=k*factor;
			ArrayList<ZRhold> newarray=new ArrayList<ZRhold>();
			newarray.add(null);
			for (int v=1;v<=p.nodeCount;v++) 
				newarray.add(interpolate(bottom.get(v),top.get(v),x,p.hes));
			array.add(newarray);
		}
		array.add(top);
	}
	
	/**
	 * Interpolate between to complex centers and radii.
	 * @param zr1 ZRhold
	 * @param zr2 ZRhold
	 * @param x double
	 * @param hes int
	 * @return ZRhold
	 */
	public ZRhold interpolate(ZRhold zr1,ZRhold zr2,double x,int hes) {
		Complex z1=zr1.getCenter();
		double r1=(double)zr1.getRadius();
		Complex z2=zr2.getCenter();
		double r2=(double)zr2.getRadius();
		Double rad=Double.valueOf(r1*(1-x)+r2*x);
		Complex Z=null;
		if (hes<=0) {
			Z= new Complex(z1.times(1-x).
					add(z2.times(x)));
		}
		if (hes>0) {
			Point3D mid=new Point3D(0.0,0.0,0.0);
			Point3D p1=z1.getAsPoint();
			Point3D p2=z2.getAsPoint();
			mid.x=p1.x*(1-x)+p2.x*x;
			mid.y=p1.y*(1-x)+p2.y*x;
			mid.z=p1.z*(1-x)+p2.z*x;
			Z=new Complex(mid.getTheta(),mid.getPhi());
		}
		return new ZRhold(Z,rad);
	}
	
	

	
}


