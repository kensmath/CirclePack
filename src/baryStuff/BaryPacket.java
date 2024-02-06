package baryStuff;

import packing.PackData;
import complex.Complex;

/**
 * Hold information on a line segment lying in a triangle
 * using barycentric coords. Currently for eucl only.
 * @author kens
 *
 */
public class BaryPacket {
	
	public int []vert;
	public int faceIndx;
	public BaryPoint start; // line start, barycentric coords
	public BaryPoint end; // line end
	
	// Constructor
	public BaryPacket() {
		vert=new int[3];
		faceIndx=-1;
	}
	
	public BaryPacket(int v,int u,int w) {
		vert=new int[3];
		vert[0]=v;
		vert[1]=u;
		vert[2]=w;
		faceIndx=-1;
	}
	
	public BaryPacket(PackData p,int face) {
		vert=p.getFaceVerts(face);
		faceIndx=face;
	}
	
	/**
	 * Record 'start' and adjust it to make sure it's
	 * in the closed triangle.
	 * @param strt, BaryPoint
	 */
	public void setStart(BaryPoint strt) {
		start=new BaryPoint(strt);
		if (start.b0<0.0) start.b0=0.0;
		if (start.b0>1.0) start.b0=1.0;
		if (start.b1<0.0) start.b1=0.0;
		if (start.b1>1.0) start.b1=1.0;
		start.b2=1.0-start.b0-start.b1;
	}
	
	/**
	 * Record 'end' and adjust it to make sure it's
	 * in the closed triangle.
	 * @param strt, BaryPoint
	 */
	public void setEnd(BaryPoint strt) {
		end=new BaryPoint(strt);
		if (end.b0<0.0) end.b0=0.0;
		if (end.b0>1.0) end.b0=1.0;
		if (end.b1<0.0) end.b1=0.0;
		if (end.b1>1.0) end.b1=1.0;
		end.b2=1.0-end.b0-end.b1;
	}
	
	/**
	 * Is 'start' on an edge? If no, return -1,
	 * else return index 0,1,2 if at corner, 12, 31, or 23
	 * if on edge. 
	 * Note: ambiguous if 'start' is at corner.
	 */ 
	public int isStartOnEdge() {
		if (start.b0==0) {
			if (start.b1==0)
				return 2;
			if (start.b2==0)
				return 1;
			return 23;
		}
		if (start.b1==0) {
			if (start.b2==0)
				return 0;
			return 31;
		}
		if (start.b2==0) 
			return 12;
		return -1;
	}
	
	/**
	 * Is 'start' at a corner? If no, return -1,
	 * else return index 0,1,2 of corner. Note: easier to
	 * be a corner than an edge.
	 */ 
	public int isStartAtCorner() {
		if (Math.abs(start.b0-1)<BaryPoint.THRESHOLD*5.0)
			return 0;
		if (Math.abs(start.b1-1)<BaryPoint.THRESHOLD*5.0)
			return 1;
		if (Math.abs(start.b2-1)<BaryPoint.THRESHOLD*5.0)
			return 2;
		return -1;
	}

	
	/**
	 * Is 'end' on an edge? If no, return -1,
	 * else return index 0,1,2 of first vert of edge.
	 * Note: ambiguous if 'end' is at corner.
	 */ 
	public int isEndOnEdge() {
		if (Math.abs(end.b0)<BaryPoint.THRESHOLD)
			return 1; 
		if (Math.abs(end.b1)<BaryPoint.THRESHOLD)
			return 2;
		if (Math.abs(end.b2)<BaryPoint.THRESHOLD)
			return 0;
		return -1;
	}
	
	/**
	 * Is 'end' at a corner? If no, return -1,
	 * else return index 0,1,2 of corner. Note: easier to
	 * be a corner than an edge.
	 */ 
	public int isEndAtCorner() {
		if (Math.abs(end.b0-1)<BaryPoint.THRESHOLD*5.0)
			return 0;
		if (Math.abs(end.b1-1)<BaryPoint.THRESHOLD*5.0)
			return 1;
		if (Math.abs(end.b2-1)<BaryPoint.THRESHOLD*5.0)
			return 2;
		return -1;
	}

	/**
	 * Get the complex number for 'start' relative to packing p
	 * @param p, PackData
	 * @param bp, BaryPacket
	 * @return Complex or null on error
	 */
	public Complex getStartZ(PackData p) {
		return (start.bPt2euclPt(p.getCenter(vert[0]),
				p.getCenter(vert[1]),p.getCenter(vert[2])));
	}
	
	/**
	 * Get the complex number for 'end' relative to packing p
	 * @param p PackData
	 * @param bp BaryPacket
	 * @return Complex or null on error
	 */
	public Complex getEndZ(PackData p) {
		return (end.bPt2euclPt(p.getCenter(vert[0]),
				p.getCenter(vert[1]),p.getCenter(vert[2])));
	}
	
	/**
	 * The BaryPoint 'this.start' must be set and must lie in 
	 * the closed face. (Move it a little if too close to 
	 * vertex or edge.) Given 'baryNext' is second point, in 
	 * barycentric coords. This routine sets 'this.end' to
	 * 'baryNext', if in closure, or to edge point or vertex 
	 * in direction of 'baryNext' if that direction crosses
	 * the simplex. On failure, set 'end' to null. 
	 * Return codes:
	 *   * 3: 'baryNext' is strictly inside, becomes 'end'
	 *   * 2: 'baryNext' is on bdry, becomes 'end'
	 *   * 1: 'end' on bdry or corner
	 *   * 0: 'end' essentially equal to 'start', may
	 *        need to get a new baryNext to try.
	 *   * -1: 'start' was not set or is not in the closed face
	 *   * -2: 'start' on edge and line to 'baryNext' does not
	 *         go towards the face (need to try another face)
	 * @return code
	 */
	public int findEndPt(BaryPoint baryNext) {

		// round off a little, if necessary
		start=new BaryPoint(start.b0,start.b1);
		if (Math.abs(baryNext.b0)<BaryPoint.THRESHOLD) baryNext.b0=0.0;
		if (Math.abs(baryNext.b1)<BaryPoint.THRESHOLD) baryNext.b1=0.0;
		if (Math.abs(baryNext.b2)<BaryPoint.THRESHOLD) baryNext.b2=0.0;
		
		// is 'baryNext' equal to 'start'?
		if (Math.abs(baryNext.b0-start.b0)<BaryPoint.THRESHOLD*10.0 &&
				Math.abs(baryNext.b1-start.b1)<BaryPoint.THRESHOLD*10.0 &&
				Math.abs(baryNext.b2-start.b2)<BaryPoint.THRESHOLD*10.0) {
			end=new BaryPoint(start);
			return 0;
		}
			
		// is baryNext in closed triangle?
		if (baryNext.b0>=0.0 && baryNext.b1>=0.0 && baryNext.b0<=(1.0-baryNext.b1)) {
			end=new BaryPoint(baryNext);
			// interior?
			if (end.b0>0 && end.b1>0 && end.b2>0) 
				return 3;
			// else on bdry
			return 2;
		}
		
		// -----
		// if start is on some edge, maybe vector to 'baryNext' points away
		
		// 'start' on left edge 
		if (start.b0==0.0) {
			// baryNext also on left (not in the triangle)
			if (baryNext.b0==0.0) {
				if ((start.b1==0 && baryNext.b1<=0.0) ||
						(start.b2==0 && baryNext.b1>=1.0)) { // points away
					end=null;
					return -2;
				}
				end = new BaryPoint();
				end.b0=0.0;
				if (baryNext.b1>start.b1) end.b1=1.0;
				if (baryNext.b1<start.b1) end.b1=0.0;
				end.b2=1-end.b1;
				return 1;
			}
			
			// nextBary points away
			if (baryNext.b0<0.0 || (start.b1==0.0 && baryNext.b1<0.0) ||
					(start.b1>(1.0-BaryPoint.THRESHOLD) && baryNext.b1>(1.0-baryNext.b0))) {
				end=null;
				return -2;
			}
		}

		// 'start' on bottom edge 
		if (start.b1==0.0) {
			// baryNext also on bottom
			if (baryNext.b1==0.0) { 
				if ((start.b0==0 && baryNext.b0<=0.0) ||
						(start.b2==0 && baryNext.b0>=1.0)) { // points away
					end=null;
					return -2;
				}
				end = new BaryPoint();
				end.b1=0.0;
				if (baryNext.b0>1) end.b0=1.0;
				if (baryNext.b0<0) end.b0=0.0;
				end.b2=1-end.b0;
				return 1;
			}
			
			// nextBary points away
			if (baryNext.b1<0.0 || (start.b0==0.0 && baryNext.b0<0.0) ||
					(start.b0>(1.0-BaryPoint.THRESHOLD) && baryNext.b1>(1.0-baryNext.b0))) {
				end=null;
				return -2;
			}
		}
		
		// 'start' on slanted edge 
		if (start.b2==0.0) {
			// baryNext also on slant
			if (baryNext.b2==0.0) { 
				if ((start.b0==0 && baryNext.b0<=0) || 
						(start.b1==0 && baryNext.b0>=1.0)) { // point away
					end=null;
					return -2;
				}
				end = new BaryPoint();
				end.b2=0.0;
				if (baryNext.b0>1) end.b0=1.0;
				if (baryNext.b0<0) end.b0=0.0;
				end.b1=1-end.b0;
				return 1;
			}
			
			if (Math.abs(start.b0-baryNext.b0)<BaryPoint.THRESHOLD) { // vertical
				if (baryNext.b1>start.b1) { // points away from triangle
					end=null;
					return -2;
				}
				// else must drop vertically to x-axis
				end=new BaryPoint(); 
				end.b0=baryNext.b0;
				end.b1=0.0;
				end.b2=1-end.b0;
				return 1;
			}
			
			// get slope: it tells if line fails to enter face
			double m=(baryNext.b1-start.b1)/(baryNext.b0-start.b0);
			if (baryNext.b0>start.b0 && m>=-1.0 ||
					baryNext.b0<start.b0 && m<=-1.0) {
				end=null;
				return -2;
			}
			
			if (baryNext.b0>start.b0) { // exit on x-axis
				end=new BaryPoint();
				end.b0=start.b0-start.b1/m;
				end.b1=0.0;
				end.b2=1.0-end.b0;
				return 1;
			}

			// now, baryNext is to left, at y-intercept or x-intercept
			double b=start.b1-start.b0*m;
			if (b>=0) {
				end=new BaryPoint();
				end.b0=0.0;
				end.b1=b;
				end.b2=1.0-end.b1;
				return 1;
			}
			
			end=new BaryPoint();
			end.b0=start.b0-start.b1/m;
			end.b1=0.0;
			end.b2=1.0-end.b0;
			return 1;
		}

		// doesn't point away, find exit (points on slope already done)
		end=new BaryPoint(baryNext);
		
		// line is vertical?
		if (Math.abs(baryNext.b0-start.b0)<BaryPoint.THRESHOLD) { 
			end.b0=start.b0;
			if (baryNext.b1<start.b1) {
				end.b1=0.0;
				end.b2=1.0-end.b0;
				return 1;
			}
			if (baryNext.b1>start.b1) {
				end.b1=1.0-end.b0;
				end.b2=0.0;
				return 1;
			}
		}

		// not vertical, find slope
		double m=(baryNext.b1-start.b1)/(baryNext.b0-start.b0);
		
		// baryNext to the right of start: can exit on slope or x-axis
		if (baryNext.b0>start.b0) {
			if (Math.abs(m+1.0)<BaryPoint.THRESHOLD) { // parallel
				if (start.b2==0.0) { // exit at right corner
					end.b0=1.0;
					end.b1=end.b2=0.0;
					return 1;
				}
				// find x intercept
				end.b0=start.b0+start.b1;
				end.b1=0.0;
				end.b2=1.0-end.b0;
				return 1;
			}
			// not parallel: 
			// x coord of intersection with slant
			double xint=(1.0-start.b1+start.b0*m)/(m+1.0);
			if (xint>=1) {
				// find x intercept
				end.b0=start.b0-start.b1/m;
				end.b1=0.0;
				end.b2=1.0-end.b0;
				return 1;
			}
			if (xint>start.b0) { // hits slope
				end.b0=xint;
				end.b1=1.0-end.b0;
				end.b2=0.0;
				return 1;
			}
			// otherwise, must exit on x-axis, m<0
			end.b0=start.b0-start.b1/m;
			end.b1=0;
			end.b2=1.0-end.b0;
			return 1;
		}
		
		// now, baryNext strictly to the left of start
		if (Math.abs(m+1.0)<BaryPoint.THRESHOLD) { // parallel 
			// find y intercept
			end.b0=0.0;
			end.b1=start.b1+start.b0;
			end.b2=1.0-end.b1;
			return 1;
		}
		
		// not parallel, get y intercept
		double b=start.b1-start.b0*m;
		if (b<=0.0) { // need x intercept
			end.b0=start.b0-start.b1/m;
			end.b1=0.0;
			end.b2=1.0-end.b0;
			return 1;
		}
		if (b>1.0) { // intersects slope
			double xint=(1.0-b)/(m+1.0);
			end.b0=xint;
			end.b2=0.0;
			end.b1=1.0-end.b0;
			return 1;
		}
		// hits left side
		end.b0=0.0;
		end.b1=b;
		end.b2=1.0-end.b1;
		return 1;
	}

}
