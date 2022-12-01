package baryStuff;

import allMains.CPBase;
import complex.Complex;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import math.Point3D;
import packing.PackData;

/**
 * A BaryPoint is a triple of barycentric coords describing a
 * point relative to a triangle; typically, a point in a triangle,
 * on an edge, or at a corner. At creation we enforce b0+b1+b2=1. 
 * However, we do not require that the bj are all non-negative; 
 * negatives imply the point is outside the triangle.
 * 
 * TODO: 'face' is ephemeral, should be replaced by v0, v1, v2.
 * Also, want more info, so I'm defining a 'BaryPointData' class;
 * should migrate 'BaryPoint' to this when I have time. (2/2020). 
 * 
 * The "standard" simplex is {(x,y): x>=0, y>=0, (x+y)<=1},
 * so b0 is associated with the origin, b1 with (1,0), and
 * b2 with (0,1) and x ~ b1, y ~ b2.
 * @author kens
 *
 */
public class BaryPoint {

	public static final double THRESHOLD=.0001;
	public double b0,b1,b2; // the barycentric coordinates
	public int face; 		// face index, default -1; aux info in some cases
						    // Caution: face indices are ephemeral!
	
	// Constructor(s)
	public BaryPoint(double a,double b) {
		b0=a;
		if (Math.abs(b0)<THRESHOLD) b0=0.0;
		b1=b;
		if (Math.abs(b1)<THRESHOLD) b1=0.0;
		b2=1-a-b;
		if (Math.abs(b2)<THRESHOLD) b2=0.0;
		face=-1;
	}
	
	public BaryPoint(double a,double b,double c) {
		this(a,b);
		b2=c;
	}
	
	public BaryPoint(BaryPoint bp) {
		b0=bp.b0;
		b1=bp.b1;
		b2=bp.b2;
		face=-1; // face info lost
	}
	
	public BaryPoint() {
		this(1.0,0.0);
	}
	
	/**
	 * Return the corresponding eucl point in eucl triangle {z1,z2,z3}. 
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return new Complex
	 */
	public Complex bPt2euclPt(Complex z1,Complex z2,Complex z3) {
		return (new Complex(z1.x*b0+z2.x*b1+z3.x*b2,z1.y*b0+z2.y*b1+z3.y*b2));
	}
	
	/**
	 * Where is BaryPoint relative to triangle? (up to OKERR)
	 * 0=inside? -1=outside? 1,2,3 = at vertex?
	 * 12,23,13 = on indicated edge?
	 * @param bp BaryPoint
	 * @return int: 0 inside; -1 outside; 1,2,3 (at vert index); 12, 23, 13 (on edge xy)
	 */
	public static int baryPtInside(BaryPoint bp) {
		double min=bp.b0;
		min = (bp.b1<min) ? bp.b1 : min;
		min = (bp.b2<min) ? bp.b2 : min;
		if (min<-PackData.OKERR) return -1; // outside
		if (min<=PackData.OKERR) { // within toler, on edge or vertex
			if (bp.b0<=bp.b1 && bp.b0<=bp.b2) {
				if (bp.b1<=PackData.OKERR) 
					return 3; // at 3
				if (bp.b2<=PackData.OKERR)
					return 2; // at 2
				return 23; // else on edge
			}
			if (bp.b1<=bp.b0 && bp.b1<=bp.b2) {
				if (bp.b0<=PackData.OKERR) 
					return 3; // at 3
				if (bp.b2<=PackData.OKERR)
					return 1; // at 1
				return 13; // else on edge
			}
			if (bp.b2<=bp.b1 && bp.b2<=bp.b0) {
				if (bp.b0<=PackData.OKERR) 
					return 2; // at 2
				if (bp.b1<=PackData.OKERR)
					return 1; // at 1
				return 12; // else on edge
			}
		}
		return 0;
	}
	
	/**
	 * Is BaryPoint on one of lines defining the triangle?
	 * @param bp BaryPoint
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return int -1 no; 1, 2, 3, hit z?; if 12, 13, 23 on line 
	 */
	public static int baryPtExt(BaryPoint bp,
			Complex z1,Complex z2,Complex z3) {
		double uplim=1.0-CPBase.GENERIC_TOLER;
		if (Math.abs(bp.b0)<CPBase.GENERIC_TOLER) { // look to 23
			if (bp.b1>uplim) return 2;
			if (bp.b2>uplim) return 3;
			return 23;
		}
		if (Math.abs(bp.b1)<CPBase.GENERIC_TOLER) { // look to 13
			if (bp.b0>uplim) return 1;
			if (bp.b2>uplim) return 3;
			return 13;
		}
		if (Math.abs(bp.b2)<CPBase.GENERIC_TOLER) { // look to 12
			if (bp.b0>uplim) return 1;
			if (bp.b1>uplim) return 2;
			return 12;
		}
		return -1;
	}
	
//	public static BaryPoint pt3D_2_BPt(Point3D pt,
//			Point3D pt1,Point3D pt2,Point3D pt3) {
//	}
			

	/**
	 * return the corresponding 3D point in triangle 'p1','p2','p3' Can be 
	 * used for plane points (third coord 0), but mainly intended for working 
	 * with barycentric coords in hyp and sph geometry.
	 * @param p1 Point3D
	 * @param p2 Point3D
	 * @param p3 Point3D
	 * @return Point3D
	 */
	public Point3D getSpacePoint(Point3D p1,Point3D p2,Point3D p3) {
		return new Point3D(
				b0*p1.x+b1*p2.x+b2*p3.x,
				b0*p1.y+b1*p2.y+b2*p3.y,
				b0*p1.z+b1*p2.z+b2*p3.z);
	}
	
	/**
	 * Given triangle corners, point z, and the geometry, return the barycentric
	 * coordinates of z
	 * @param hes int, geometry
	 * @param z Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return BaryPoint, null on error
	 */
	public static BaryPoint complex2bp(int hes,Complex z,Complex z1,Complex z2,Complex z3) {
		if (hes==0) { // eucl
			return EuclMath.e_pt_to_bary(z,z1,z2,z3);
		}
		if (hes<0) { // hyp
			return HyperbolicMath.h_pt_to_bary(z, z1, z2, z3);
		}
		if (hes>0) { // TODO: sph case
			return null;
		}
		return null;
	}
	
	/**
	 * Convert the barycentric coords of 'this' to a point in the given
	 * triangle, depending on geometry.
	 * @param hes int, geometry
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @return Complex, exception in hyp case for point outside disc
	 */
	public Complex bp2Complex(int hes,Complex z1,Complex z2,Complex z3) {
		if (hes==0) { // eucl
			return new Complex(b0*z1.x+b1*z2.x+b2*z3.x,
					b0*z1.y+b1*z2.y+b2*z3.y);
		}
		if (hes<0) { // hyp
			return HyperbolicMath.bary_to_h_pt(this, z1, z2, z3);
		}

		// else must be sphere (too close to origin goes to north pole)
		Point3D pt1=Point3D.sph_2_p3D(z1);
		Point3D pt2=Point3D.sph_2_p3D(z2);
		Point3D pt3=Point3D.sph_2_p3D(z3);
		return Point3D.p3D_2_sph(EuclMath.getSpacePoint(this,pt1,pt2,pt3));
	}
	
	/**
	 * new copy of the 'BaryPoint'
	 */
	public BaryPoint clone() {
		BaryPoint newbp=new BaryPoint(this);
		newbp.face=this.face;
		return newbp;
	}
	
	public void printDebug(PackData p,String lead) {
		int[] vert=p.packDCEL.faces[face].getVerts();
		System.out.println(lead+": face="+this.face+" verts="+
				vert[0]+" "+vert[1]+" "+vert[2]+" barys: "+
				this.b0+" "+this.b1+" "+this.b2);
	}
	
	/**
	 * To contend with roundoff error, this returns the "nearest" 
	 * BaryPoint having non-negative entries to given BaryPoint 'inpt'. 
	 * Return 'inpt' itself if interior (i.e. non-negative entries); 
	 * return null if 'cutoff' is true and 'inpt' is too far from 
	 * interior. Keep same face.
	 * 
	 * NOTE: thresholding applies to barycentric coords, not to
	 * actual euclidean coords. We use the standard simplex with 
	 * x=b2, y=b2; for point outside, we project to edge toward 
	 * opposite vertex, or to a corner.
	 * 
	 * @param inpt BaryPoint
	 * @param cutoff boolean; true, return null if threshold violated
	 * @return BaryPoint, non-negative entries or null if too far
	 */
	public static BaryPoint nearestBary(BaryPoint inpt,boolean cutoff) {
		BaryPoint outpt=null;
		double x=inpt.b1;
		double y=inpt.b2;
		
		// inpt is already inside
		if (inpt.b0>=0 && x>=0 && y>=0) {
			 outpt=new BaryPoint(inpt);
			 outpt.face=inpt.face;
			 return outpt;
		}
		
		// 'cutoff' set and too far out of tolerance?
		if (cutoff) {
			if ((x+y)<1.0+THRESHOLD && x>-1.0*THRESHOLD && y>-1.0*THRESHOLD) {
				outpt=new BaryPoint(1-(x+y),x); // this call fixes coords
				outpt.face=inpt.face;
			}
			return outpt;
		}

		// project to corner or to edge (along line to opposite corner)
		outpt=new BaryPoint();
		outpt.face=inpt.face;
		if (x<0) {
			if (y<0) {
				outpt.b1=outpt.b2=0;
				outpt.b0=1.0;
			}
			else if ((x+y)>=1) {
				outpt.b0=outpt.b1=0;
				outpt.b2=1.0;
			}
			else {
				outpt.b1=0.0;
				outpt.b2=y*(1.0+x/(1.0-x));
				outpt.b0=1.0-outpt.b2;
			}
		}
		else if (y<0) {
			if ((x+y)>=1) {
				outpt.b0=outpt.b2=0.0;
				outpt.b1=1.0;
			}
			else {
				outpt.b2=0;
				outpt.b1=x*(1.0+y/(1-y));
				outpt.b0=1.0-outpt.b1;
			}
		}
		else if ((x+y)>1.0) {
			outpt.b0=0;
			outpt.b1=x/(x+y);
			outpt.b2=y/(x+y);
		}
		return outpt;
	}
	
	/**
	 * Given a euclidean triangle, corners {z0,z1,z2} and vector 'vec',
	 * return image of 'vec' under linear transformation taking 
	 * z0 --> (0,0), z1 --> (1,0), z2 --> (0,1). This is direction
	 * in the standard simplex corresponding to 'vec' and is used
	 * to help build streamlines.
	 * @param vec Complex, original geometric vector
	 * @param z0 Complex, corners
	 * @param z1 Complex
	 * @param z2 Complex
	 * @return Complex, direction vector, null on error 
	 */
	public static Complex vec2simplex(Complex vec,Complex z0,Complex z1,Complex z2) {
		Complex a=z1.minus(z0);
		Complex b=z2.minus(z0);
		
		// are these too small or close to parallel?
		if (Point3D.CrossProduct(a, b).norm()<PackData.OKERR)
			return null;
		
		// transformation is inverse of matrix (a.x b.x;a.y b.y)
		double det=a.x*b.y-b.x*a.y;
		return new Complex((b.y*vec.x -b.x*vec.y)/det,(-a.y*vec.x+a.x*vec.y)/det);
	}
	
	/**
	 * Starting in standard simplex at (b0,b1), find up-gradient exit 
	 * bary coords B0,B1; i.e., transit in direction closest to 'grad'. 
	 * Exit may be a corner or edge. Return null if 'grad' is too small 
	 * (no movement), if no direction in simplex is up-gradient, or if
	 * exit is essentially equal to entrance.
	 * 
	 * Batch of cases, starting with start at boundary/corner.
	 * @param b0 double
	 * @param b0 double
	 * @param grad Complex
	 * @return double[], B0, B1.
	 */
	public static double []upGrad(double b0,double b1,Complex grad) {
		if (grad.abs()<PackData.OKERR || b0<0.0 || b1<0.0)
			return null;
		
		// regularize data
		double x=b1;
		if (x<PackData.OKERR) x=0.0;
		double y=1-(b0+x);
		if (y<PackData.OKERR) y=0.0;
		if (y>1.0) y=1.0;
		grad=grad.divide(grad.abs()); // unit vector
		double a=grad.x;
		double b=grad.y;
		double m=b/a;

		double []ans=new double[2];
		
		// vertical gradient
		if (Math.abs(a)<PackData.TOLER) {
			
			// going up
			if (b>0.0) {
				if (y<(1.0-x-PackData.OKERR)) { // go out the hypotenuse
					ans[1]=x;
					return ans;
				}
				return null; // 'start'=='end'
			}
			
			// going down, exit lower edge
			if (y>PackData.OKERR) {
				ans[0]=1.0-x;
				ans[1]=x;
				return ans;
			}
			
			// else, 'start'=='end'
			return null;
		}
					
		// start is on left edge?
		if (x==0.0) {
			
			// grad upward? 
			if (b>0.0) {
				
				// essentially, (x,y) is at the top corner, gradient is upward
				if (y>(1.0-PackData.OKERR)) // 'start' = 'end' 
					return null; 
				
				// exit at top corner?
				if (a<PackData.OKERR) {
					return ans;
				}
				
				// else exit on hypotenuse
				ans[1]=(1.0-y)/(1.0+m);
				return ans;
			}
			
			// else slope downward
			
			// exit at origin?
			if (a<PackData.OKERR) {
				if (y==0.0) // 'start'='end'
					return null;
				ans[0]=1.0;
				return ans;
			}
			
			// is spt at origin? since b<0, a>0, exit at right corner
			if (y==0.0) {
				ans[1]=1.0;
				return ans;
			}
			
			double xreach= -y/m;
			
			// exit hypotenuse
			if (xreach>1-PackData.OKERR) {
				ans[1]=(1.0-y)/(1.0+m);
				return ans;
			}
			
			// exit lower edge
			ans[0]=1.0-xreach;
			ans[1]=xreach;
			return ans;
		} // done with x==0
		
		// start is on bottom?
		if (y==0.0) {
			
			// go right
			if (a>0.0) {
				
				// exit at right corner?
				if (b<PackData.OKERR) {
					if (x>(1.0-PackData.OKERR)) // 'start'='end'
						return null;
					ans[1]=1.0;
					return ans;
				}
				
				// exit hypotenuse
				ans[1]=(1.0+m*x)/(1.0+m);
				return ans;
			}
			
			// else, go left, a<0
			
			// exit at origin
			if (b<PackData.OKERR) {
				if (x==0.0) // 'start'='end'
					return null;
				ans[0]=1.0;
				return ans;
			}
			
			double yreach=-x*m;
			
			// exit hypotenuse
			if (yreach>1.0) {
				ans[1]=(1.0+m*x)/(1.0+m);
				return ans;
			}
			
			// exit left side
			ans[0]=1.0+x*m;
			return ans;
			
		} // done with y==0
		
		// start on hypotenuse
		if (Math.abs(1.0-(x+y))<PackData.OKERR) {
			
			// going left?
			if (a<0.0) {
				
				double yinter=y-x*m;
				
				// exit at upper corner?
				if (yinter>(1.0-PackData.OKERR)) {
					if (x<PackData.OKERR) // 'start'='end'
						return null;
					return ans;
				}
				
				// exit at origin
				if (Math.abs(yinter)<PackData.OKERR) {
					ans[0]=1.0;
					return ans;
				}
				
				// exit left side
				if (yinter>0.0) {
					ans[0]=1.0-yinter;
					return ans;
				}
				
				// exit bottom
				ans[1]=x-y/m;
				ans[0]=1-ans[1];
				return ans;
			}
			
			// going right
			// if perpendicular to hypotenuse, null
			if (Math.abs(1.0-m)<PackData.OKERR)
				return null;
			
			// exit upper corner?
			if (m>1.0) 
				return ans;

			// exit right corner?
			if (m>-1.0-PackData.OKERR) {
				ans[1]=1.0;
				return ans;
			}
				
			// exit bottom edge
			double xinter=x-y/m;
			ans[0]=1.0-xinter;
			ans[1]=xinter;
			return ans;
		} // done with start on hypotenuse
			
		// Now (x,y) must be inside, slope not vertical
		
		// horizontal?
		if (Math.abs(b)<PackData.OKERR) {
			
			// exit on hypotenuse?
			if (a>0.0) {
				ans[0]=0.0;
				// exit at corner?
				if (y<PackData.OKERR) 
					ans[1]=1.0;
				else ans[1]=x;
				return ans;
			}
			
			// exit on left
			ans[1]=0.0;
			
			// exit at origin
			if (y<PackData.OKERR)
				ans[0]=1.0;
			else if (y>1.0-PackData.OKERR) // exit at top corner
				ans[0]=0.0;
			return ans;
		}
		
		// find intercepts
		double xint=x-y/m;
		double yint=-m*x+y;
		
		// going down?
		if (b<0.0) {
			
			// exit at origin?
			if (Math.abs(xint)<PackData.OKERR) {
				ans[0]=1.0;
				ans[1]=0.0;
				return ans;
			}
			
			// exit on left edge?
			if (xint<0.0) {
				ans[0]=1.0-yint;
				ans[1]=0.0;
				return ans;
			}
			
			// exit at right corner?
			if (Math.abs(1.0-xint)<PackData.OKERR) {
				ans[0]=0.0;
				ans[1]=1.0;
				return ans;
			}
			
			// exit on bottom edge?
			if (xint<1.0) {
				ans[0]=1.0-xint;
				ans[1]=xint;
				return ans;
			}
			
			// exit on hypotenuse is covered later
		}
		
		// going right (only exit hypotenuse not covered)
		double xx=(1.0+m*x-y)/(1.0+m);
		if (a>0.0) {
			
			// exit at top corner?
			if (xx<PackData.OKERR) {
				ans[0]=ans[1]=0.0;
				return ans;
			}
			
			// exit on hypotenuse
			ans[0]=0.0;
			ans[1]=xx;
			return ans;
		}

		// going up left is all that's left
		
		// out top corner?
		if (Math.abs(1.0-yint)<PackData.OKERR) {
			ans[0]=ans[1]=0.0;
			return ans;
		}
		
		// out left side
		if (yint<1.0) {
			ans[0]=1.0-yint;
			ans[1]=0.0;
			return ans;
		}
		
		// else out hypotenuse
		ans[0]=0.0;
		ans[1]=xx;
		return ans;
	}
	
}
