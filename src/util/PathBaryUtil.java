package util;

import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.Vector;

import baryStuff.BaryPacket;
import baryStuff.BaryPoint;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.EuclMath;
import komplex.EdgeSimple;
import listManip.BaryCoordLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import packing.PackData;

/**
 * Static methods to aid in working with euclidean paths,
 * especially via barycentric coordinates.
 * 
 * * Given seg [z1,z2], where, in barycentric coords,
 *   does the line enter/leave a given face?
 *   
 * @author kens
 *
 */
public class PathBaryUtil {
	
	/**
	 * Does euclidean segment {p1,p2} intersect triangle?
	 * @param p1, p2
	 * @param z1, z2, z3
	 * @return 
	 */
	public static boolean doesSegXTri(Complex p1,Complex p2,
			Complex z1,Complex z2,Complex z3) {

		// one point inside or on? 
		BaryPoint bp1=EuclMath.e_pt_to_bary(p1, z1, z2, z3);
		if (BaryPoint.baryPtInside(bp1)>=0) return true;
		BaryPoint bp2=EuclMath.e_pt_to_bary(p2, z1, z2, z3);
		if (BaryPoint.baryPtInside(bp2)>=0) return true;
		
		// thus, p1 or p2 must be on opposite side from some vert.
		
		// both on opposite of any one of edges?
		if ((bp1.b0<0 && bp2.b0<0) || (bp1.b1<0 && bp2.b1<0) ||
				(bp1.b2<0 && bp2.b2<0)) return false;
		
		// p1 must be on neg side of some edge, p2 across that edge.
		double vx=p2.x-p1.x;
		double vy=p2.y-p1.y;
		double d1=(z1.x-p1.x)*vx+(z1.y-p1.y)*vy;
		double d2=(z2.x-p1.x)*vx+(z2.y-p1.y)*vy;
		double d3=(z3.x-p1.x)*vx+(z3.y-p1.y)*vy;
		// if all dot products have same sign, false
		if ((d1>=0 && d2>=0 && d3>=0) || (d1<=0 && d2<=0 && d3<=0))
			return false;
		return true;
	}

	/**
	 * Given line seg, triangle (both oriented) that are KNOWN to
	 * intersect, return BaryPoint's of first and last points on
	 * segment inside triangle.
	 * @param p1,p2, Complex segment ends
	 * @param z1,z2,z3
	 * @return BdryPoint[2], entry, exit points
	 */
	public static BaryPoint []hitPoints(Complex p1,Complex p2,
			Complex z1,Complex z2,Complex z3) {
		BaryPoint []barys=new BaryPoint[2];

		// starts inside?
		BaryPoint bp1=EuclMath.e_pt_to_bary(p1, z1, z2, z3);
		BaryPoint bp2=EuclMath.e_pt_to_bary(p2, z1, z2, z3);
		boolean p1_inside=false;
		boolean p2_inside=false;
		if (BaryPoint.baryPtInside(bp1)>=0)
			p1_inside=true;
		if (BaryPoint.baryPtInside(bp2)>=0) 
			p2_inside=true;
		if (p1_inside && p2_inside) {
			barys[0]=bp1;
			barys[1]=bp2;
			return barys;
		}
		
		Complex enty = null;
		Complex ext = null;
		// bp1 outside
		if (p1_inside) 
			enty=new Complex(p1);
		else 
			enty=EuclMath.firstHit(p1,p2,z1,z2,z3);
		if (p2_inside)
			ext=new Complex(p2);
		else
			ext=EuclMath.firstHit(p2,p1,z1,z2,z3);
		if (enty==null || ext==null) 
			throw new DataException("error hitting triangle");
		barys[0]=EuclMath.e_pt_to_bary(enty, z1, z2, z3);
		barys[1]=EuclMath.e_pt_to_bary(ext, z1, z2, z3);
		return barys;
	}
		
	/**
	 * Build 'BaryLink' vector for pack p from given path: note path
	 * may enter and leave carrier, so this returns a vector of non-
	 * trivial segments.
	 * @param p, PackData. 
	 * @param path, Path2D.Double (may be multiple segments)
	 * @return Vector<BaryLink> or null on error
	 */
	public static Vector<BaryCoordLink> fromPath(PackData p,Path2D.Double path) {
		if (p.hes!=0) {
			throw new ParserException("'BaryLink': only available in eucl case");
		}
		if (path==null) return null;
		Vector<Vector<Complex>> zSegments=PathUtil.gpPolygon(path);
		Vector<BaryCoordLink> Blinks=new Vector<BaryCoordLink>(1);

		// iterate through the segments if 'path' has more than one.
		for (int j=0;j<zSegments.size();j++) {
			Vector<Complex> zPath =zSegments.get(j);
			if (zPath!=null && zPath.size()>0) {
				// cut out essentially repeated entries
				for (int jj=0;jj<(zPath.size()-1);jj++) {
					Complex z=zPath.get(jj);
					while ((jj+1)<zPath.size() && 
							zPath.get(jj+1).minus(z).abs()<.000001)
						zPath.remove(jj+1);
				}
				if (zPath.size()>1) {
					boolean healthy=true;
					do {
						segAnswer sAns=nextSegment(p,zPath);
						if (sAns==null) 
							healthy=false;
						else if (sAns.blink!=null) {
							healthy=sAns.healthy;
							Blinks.add(sAns.blink);
						}
					} while (healthy && zPath.size()>0);
				}
			}
		} // end of for
		return Blinks;
	}
	
	/** 
	 * Given path, find the next continuous segment actually
	 * entering the packing carrier. 
	 * @param p
	 * @param healthy, return health: false if error encountered
	 * @param zPath, returned with new starting spot if not used up
	 * @return BaryLink, null if nothing found
	 */
	public static segAnswer nextSegment(PackData p,Vector<Complex> zPath) {
		if (zPath==null || zPath.size()==0) 
			return new segAnswer(true,null);
		Complex start=zPath.remove(0);
		BaryPacket currfbp=null;
		Complex nextz=null;
		Complex []z=new Complex[3];
		int currFaceIndx=0;
		int hitResult=0;
		boolean gothit=false; // true only entering carrier and bdry edge is hit
		boolean healthy=true;
		
		// finding a first face: can be tough
		FaceLink flink=p.tri_search(start);
		// not in face? must be outside carrier.
		if (flink==null || flink.size()==0) { 
			// We pass around bdry edges in order and use segIntersect
			EdgeLink elink=new EdgeLink(p,"b");
			if (elink==null || elink.size()==0)
				return new segAnswer(healthy,null);
			
			// toss segments of zPath that don't get a hit
			while (zPath.size()>0 && !gothit) {
				double minDist=10000000;
				nextz=zPath.remove(0);
				double pathSegLength=start.minus(nextz).abs();
				Iterator<EdgeSimple> elst=elink.iterator();
				EdgeSimple holdEdge=null;
				Complex holdStart=null;
				while (elst.hasNext()) {
					EdgeSimple edge=elst.next();
					Complex a=p.getCenter(edge.v);
					Complex b=p.getCenter(edge.w);
					Complex hitPt=EuclMath.segIntersect(start,nextz,a,b);
					if (hitPt!=null) {
						double dist=start.minus(hitPt).abs();
						if (dist<=pathSegLength && dist<minDist) {
							holdEdge=new EdgeSimple(edge.v,edge.w);
							holdStart=new Complex(hitPt);
						}
					}
				} // end of pass through bdry edges
				if (holdEdge!=null) { // success: now, which face?
					start=new Complex(holdStart); // entry point
					if (start.minus(nextz).abs()>EuclMath.OKERR*100) {
						zPath.insertElementAt(nextz,0); // need to get 'nextz' later
					}
					else if (zPath.size()==0) { // only got one point
						return new segAnswer(healthy,null);
					}
					// is 'start' an endpoint?
					if (start.minus(p.getCenter(holdEdge.v)).abs()<EuclMath.OKERR*10) {
						currFaceIndx=faceFromZ(p,holdEdge.v,nextz);
						start=p.getCenter(holdEdge.v);
					}
					else if (start.minus(p.getCenter(holdEdge.w)).abs()<EuclMath.OKERR*10) {
						currFaceIndx=faceFromZ(p,holdEdge.w,nextz);
						start=p.getCenter(holdEdge.w);
					} 
					else currFaceIndx=p.getFaceFlower(holdEdge.v,0); // face
					gothit=true;
				}
				else { // toss segment, try next
					start=nextz;
				}
			} // end of outer while (trying new segments of the path against whole bdry)
			
			// never crossed the boundary? zPath should be empty
			if (!gothit) 
				return new segAnswer(healthy,null);
			
			// build FaceLink so we can continue
			flink=new FaceLink(p);
			flink.add(currFaceIndx);
		}
		
		// 'start' should be in face(s) of this list; It might be 'nextz' 
		do {
			if (zPath.size()>0) 
				nextz=zPath.remove(0);
		} while (nextz!=null && start.minus(nextz).abs()<EuclMath.OKERR*10 && zPath.size()>0);
		if (nextz!=null && start.minus(nextz).abs()<EuclMath.OKERR*10) {
			return new segAnswer(healthy,null); // just one point
		}
		if (nextz==null)
			return new segAnswer(healthy,null); 
		
		// find first with segment actually entering carrier
		gothit=false;
		int repeat=2;
		while (repeat>0 && !gothit) {
			Iterator<Integer> flst=flink.iterator();
			while (flst.hasNext() && !gothit) {
				currFaceIndx=flst.next();
			
				// check if segment [start,nextz] actually enters the face
				int[] fverts=p.getFaceVerts(currFaceIndx);
				z[0]=p.getCenter(fverts[0]);
				z[1]=p.getCenter(fverts[1]);
				z[2]=p.getCenter(fverts[2]);
				currfbp=new BaryPacket(p,currFaceIndx);
				currfbp.setStart(EuclMath.e_pt_to_bary(start, z[0],z[1],z[2]));
				BaryPoint baryNext=EuclMath.e_pt_to_bary(nextz, z[0],z[1],z[2]);
				
				hitResult=currfbp.findEndPt(baryNext);
				if (hitResult>=1)
				gothit=true;
			} // end of inner while

			// didn't enter? It would appear that 'start' is on edge of some face, but
			//   vector to 'nextz' points away. Need to shift 'start' toward 'nextz' 
			//   a little and try once more.
			if (!gothit) {
				Complex vec=nextz.minus(start);
				// move distance 1/100 toward 'nextz'
				start=start.add(vec.times(.01/vec.abs()));
				repeat--;
			}
		} // end of outer while

		// no hit; put 'nextz' back in zPath another try for another segment
		if (!gothit && start.minus(nextz).abs()>EuclMath.OKERR*100) {
			zPath.insertElementAt(nextz,0);
			return new segAnswer(healthy,null);
		}
		
		// Reaching here, we have first BaryPacket: look for continuations
		BaryCoordLink blk=new BaryCoordLink(p);
		int safety=10*p.faceCount;
		do {
			blk.add(currfbp);
			start=currfbp.getEndZ(p);
			// did we (essentially) reach nextz?
			if (start.minus(nextz).abs()<EuclMath.OKERR*100) {
				if (zPath.size()==0) {
					return new segAnswer(healthy,blk);
				}
				nextz=zPath.remove(0);
			}
			
			// start new packet (even though start may be exit point)
			currfbp=new BaryPacket(p,currFaceIndx);
			currfbp.setStart(EuclMath.e_pt_to_bary(start,z[0],z[1],z[2]));
			BaryPoint baryNext=EuclMath.e_pt_to_bary(nextz,z[0],z[1],z[2]);
			
			// find end in direction of 'baryNext'
			int locCount=0;
			while ((hitResult=currfbp.findEndPt(baryNext))<1) { // things to check
				locCount++;
//				System.out.println("goResult = "+ goResult);
				if (hitResult==0) { // 'end' equals 'start'
					if (zPath.size()==0) {
						return new segAnswer(healthy,blk);
					}	
					// will try this face again with new 'nextz'
					nextz=zPath.remove(0);
					baryNext=EuclMath.e_pt_to_bary(nextz, z[0],z[1],z[2]);
				}
				else if (hitResult==-2) { // line misses: find new face
					int code=currfbp.isStartOnEdge();
					if (code==-1) { // error: start should be on some edge
						healthy=false;
						return new segAnswer(healthy,blk);
					}
					int holdFace=currFaceIndx;
					currFaceIndx=PathBaryUtil.getNextFace(p,holdFace,code,nextz);
					if (currFaceIndx==holdFace) {
						System.err.println("'fromPath':didn't get new face");
						healthy=false;
						return new segAnswer(healthy,blk);
					}
					
					// no next face? exiting carrier, so finished
					if (currFaceIndx==-1) {
						return new segAnswer(healthy,blk);
					}
					
					// else have our new face to try with same 'nextz'
					int[] fverts=p.getFaceVerts(currFaceIndx);
					z[0]=p.getCenter(fverts[0]);
					z[1]=p.getCenter(fverts[1]);
					z[2]=p.getCenter(fverts[2]);
					currfbp=new BaryPacket(p,currFaceIndx);
					currfbp.setStart(EuclMath.e_pt_to_bary(start,z[0],z[1],z[2]));
					baryNext=EuclMath.e_pt_to_bary(nextz,z[0],z[1],z[2]);
				}
				if (locCount>2) {
					System.err.println("currFaceIndx="+currFaceIndx+" nextz="+nextz+
							" start="+start);
				}
			} // end of while
			
			// if 'nextBary' in closed triangle (= 'nextz') or close enough to 'nextz'
			if (hitResult>=2 || 
					currfbp.getEndZ(p).minus(nextz).abs()<EuclMath.OKERR*1000) { // 'end' strictly inside
				if (zPath.size()==0) {
					blk.add(currfbp);
					return new segAnswer(healthy,blk);
				}
				nextz=zPath.remove(0);
			}
			// if 'end' came out on bdry
//			else if (hitResult==1) {
//				System.err.println("got 1, currFaceIndx="+currFaceIndx);
//			}
			safety--;
		} while (safety>0);
		
		// shouldn't reach here
		healthy=false;
		return new segAnswer(healthy,blk);
	}
		
    /**
     * 
     * @param f int, current face index
     * @param code int, where previous pt situated
     * code = 0,1,2 (at vert 0, 1, or 2)
     *      = 12, 31, 23 (edge)
     * @param nextz (only needed if at vertex)
     * @return next face index
     */
    public static int getNextFace(PackData p,int f,int code,Complex p2) {
    	int[] fverts=p.getFaceVerts(f);
    	int v=0;
    	int w=0;
    	try {
    	if (code<10) { // at corner
    		v=fverts[code];
    		return faceFromZ(p,v,p2);
    	}
    	if (code==12) {
    		w=fverts[1];
    		v=fverts[0];
    	}
    	if (code==31) {
    		w=fverts[0];
    		v=fverts[2];
    	}
    	if (code==23) {
    		w=fverts[2];
    		v=fverts[1];
    	}
    	if (p.isBdry(v) && w==p.getFirstPetal(v))
    		return -1; 
    	if (p.packDCEL!=null) {
    		HalfEdge wv=p.packDCEL.findHalfEdge(new EdgeSimple(w,v));
    		return wv.face.faceIndx;
    	}
    	return p.getFaceFlower(w,p.nghb(w,v)); // to left of {w,v}
    	} catch(Exception ex) {
//    		System.out.println("w,v = "+w+" "+v);
    		throw new CombException("hum??? bad code? "+code);
    	}
    }
	
	/**
	 * Return index of face in flower of 'v' which the line from 
	 * its center to p2 enters. If none, return -1 (e.g., out of carrier)
	 * @param p
	 * @param v
	 * @param p2
	 * @return
	 */
	public static int faceFromZ(PackData p,int v,Complex p2) {
		int num=p.countFaces(v);
		Complex me=p.getCenter(v);
		Complex vp2=p2.minus(me);
		int[] flower=p.getFlower(v);
		double arg1;
		double arg2=p.getCenter(flower[0]).minus(me).divide(vp2).arg();
		for (int j=1;j<=num;j++) {
			arg1=arg2;
			arg2=p.getCenter(flower[j]).minus(me).divide(vp2).arg();
			if (arg1<=0 && arg2>0)
				return p.getFaceFlower(v,j-1);
		}
		if (Math.abs(arg2)<EuclMath.OKERR) // ?? not sure what this does
			return p.getFaceFlower(v,num-1);
		return -1;
	}
	
	/**
	 * For the given packing, convert BaryLink to Path2D.Double.
	 * Note: I think this handles only paths with single component.
	 * @param p
	 * @param blk, BaryLink
	 * @return null on error or empty path
	 */
	public static Path2D.Double baryLink2path(PackData p,BaryCoordLink blk) {
		if (blk==null || blk.size()==0) 
			return null;
		Path2D.Double path=new Path2D.Double();
		Iterator<BaryPacket> bit = blk.iterator();
		BaryPacket bp=bit.next();
		Complex z=bp.getStartZ(p);
		if (z!=null)
		path.moveTo(z.x,z.y);
		z=bp.getEndZ(p);
		if (z!=null)
		path.lineTo(z.x,z.y);
		while(bit.hasNext()) {
			z=bit.next().getEndZ(p);
			if (z==null) 
				return path;
			path.lineTo(z.x,z.y);
		}
		return path;
	}
	
	/**
	 * Just to carry results back from nextSegment
	 */
	static class segAnswer {
		public boolean healthy;
		public BaryCoordLink blink;
		
		public segAnswer(boolean health,BaryCoordLink blk) {
			healthy=health;
			blink=blk;
		}
	}
	
}
