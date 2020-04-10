package baryStuff;

import math.Point3D;

/**
 * Doing more with barycentric coords in connection with 3D printing work,
 * so I've created this package (2/2020). Plan to migrate some other things
 * here, maybe make a superclass. For now, I need something to hold face,
 * barycentric coords, and some data.
 * Note always compute b_point.z so sum of bary coords is 1.0; not all 
 * need to be positive, can represent point outside the triangle.
 * @author kens
 *
 */
public class BaryPtData {

	public static final double THRESHOLD=.0001;
	public Point3D b_point;     // in barycentric coordinates
	public int []verts;		    // face indices too ephermeral
	public double realValue;    // carry some data
	public int utilint;         // utility: e.g., holding face index
	
	// constructors
	public BaryPtData() {
		b_point=new Point3D(1.0,0.0,0.0);
		verts=new int[3];
		verts[0]=verts[1]=verts[2]=-1;
	}
	
	public BaryPtData(int v0,int v1,int v2,double b0,double b1) {
		this();
		verts[0]=v0;
		verts[1]=v1;
		verts[2]=v2;
		b_point=new Point3D(b0,b1,1.0-(b0+b1));
	}

	public BaryPtData(int v0,int v1,int v2,double b0,double b1,double x) {
		this(v0,v1,v2,b0,b1);
		realValue=x;
	}
	
	public boolean isInside() {
		if (b_point.x<0.0 || b_point.y<0.0 || b_point.z<0.0)
				return false;
		return true;
	}
	
	/**
	 * Find actual geometric point relative to given ordered list of
	 * euclidean vertex locations.
	 * @param a Point3D
	 * @param b Point3D
	 * @param c Point3D
	 * @return Point3D
	 */
	public Point3D getGeomPoint(Point3D a,Point3D b,Point3D c) {
		Point3D ans=new Point3D();
		ans.x=b_point.x*a.x+b_point.y*b.x+b_point.z*c.x;
		ans.y=b_point.x*a.y+b_point.y*b.y+b_point.z*c.y;
		ans.z=b_point.x*a.z+b_point.y*b.z+b_point.z*c.z;
		return ans;
	}
}
