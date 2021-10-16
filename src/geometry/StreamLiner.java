package geometry;

import allMains.CirclePack;
import baryStuff.BaryPacket;
import baryStuff.BaryPoint;
import complex.Complex;
import dcel.HalfEdge;
import exceptions.DataException;
import komplex.EdgeSimple;
import listManip.BaryCoordLink;
import listManip.FaceLink;
import math.Point3D;
import packing.PackData;

/**
 * A class for creating streamlines, a la curvature flow lines, 
 * as Chuck Collins did using matlab in joint paper with me and
 * Toby Driscoll.
 *  
 * Steamlines are associated with a packing and are represented 
 * in 'BaryLink's of of 'BaryPacket's, hence in barycentric 
 * coordinates.
 * 
 * The 'basePack' is euclidean, contains the combinatorics and 
 * vertex locations. The basePack typically provides a way to 
 * plot the streamlines, since the packing used to create them 
 * may not have a convenient layout.
 * 
 * @author kstephe2
 *
 */
public class StreamLiner {
	PackData basePack;		// copy for combinatorics and centers 
	double[] dataValues;	// values engendering the streamlines
	Point3D[] normals;		// normals to the faces (ccw oriented)
	
	public StreamLiner(PackData p) {
		basePack=p.copyPackTo();
		dataValues=null;
	}
	
	/**
	 * Build 'BaryLink' giving streamline of gradient starting 
	 * at 'pt' (caution: relative to the carrier of 'basePack').
	 * We follow the gradient uphill (downhill) from 'pt'. Stop
	 * on hitting a boundary edge or vertex or a local extremum.
	 * 
	 * @param pt Complex
	 * @param uphill boolean
	 * @return BaryCoordLink, null on error
	 */
	public BaryCoordLink getStreamline(Complex pt, boolean uphill) {
		BaryCoordLink outLink=new BaryCoordLink();
		
		// what face are we in?
		FaceLink flk=basePack.tri_search(pt);
		if (flk==null || flk.size()==0)
			return null;
		dcel.Face face=basePack.packDCEL.faces[flk.get(0)];
		int[] vert=face.getVerts();
		Complex v0=basePack.getCenter(vert[0]);
		Complex v1=basePack.getCenter(vert[1]);
		Complex v2=basePack.getCenter(vert[2]);

		// find the first 'BaryPoint'
		BaryPoint startBpt=BaryPoint.complex2bp(basePack.hes, pt, v0, v1, v2);
		startBpt.face=face.faceIndx;
		
		// chase along 
		int safety=1000;
		while (startBpt!=null && safety>0) {
			safety--;
			
			// decide which way to go from 'startBpt'
			startBpt=whichWay(startBpt,uphill);
			
			// did we reach an error or a stopping point?
			if (startBpt==null) {
				if (outLink!=null && outLink.size()>0)
					return outLink;
				else return null;
			}
			BaryPacket newBP=faceTransit(startBpt,uphill);
			
			if (newBP==null) {
				if (outLink!=null && outLink.size()>0)
					return outLink;
				return null;
			}
			
			outLink.add(newBP);
			startBpt=newBP.end.clone();
		} // end of while
		
		if (outLink!=null && outLink.size()>0)
			return outLink;
		return null;
	}
	
	/**
	 * Given BaryPoint, find the up-gradient exit point 
	 * in that same face and return the 'BaryPacket' with
	 * 'start' and 'end' points. Return null if 'start' and
	 * 'end' are essentially equal or if the streamline stops.
	 * @param spt BaryPoint
	 * @param uphill
	 * @return BaryPacket, null on error or 'start'=='end'
	 */
	public BaryPacket faceTransit(BaryPoint spt,boolean uphill) {
		if (spt==null || spt.face<1)
			return null;
		
		// grad
		Complex grad=new Complex(-normals[spt.face].x,-normals[spt.face].y);
		if (!uphill) 
			grad=grad.times(-1.0);
		dcel.Face face=basePack.packDCEL.faces[spt.face];
		int[] vert=face.getVerts();
		
		Complex vec=BaryPoint.vec2simplex(grad,basePack.getCenter(vert[0]),
				basePack.getCenter(vert[1]),basePack.getCenter(vert[2]));
		double []baryc=BaryPoint.upGrad(spt.b0,spt.b1,vec);
		if (baryc==null)
			return null;
		
		BaryPoint exitpt=new BaryPoint(baryc[0],baryc[1]);
		exitpt.face=spt.face;
		
		BaryPacket outPacket= new BaryPacket(vert[0], vert[1], vert[2]);
		outPacket.faceIndx = spt.face;
		outPacket.start = spt.clone();
		outPacket.start.face=spt.face;
		outPacket.end = exitpt;
		return outPacket;
	}

	/**
	 * For each face, find the normal to the face as normalized
	 * cross product of 3D vectors from v0 to v1 and v1 to v2.
	 * (Caution: using centers from 'basePack'.)
	 */
	public void setNormals() {
		if (dataValues==null)
			throw new DataException("'dataValues' are not set, see 'logmod'");
//		if (dataValues.length!=basePack.nodeCount+1) {
//			CirclePack.cpb.errMsg("Note: sizes of 'streamLiner.basePack' and 'logmod' don't agree");
//		}
		normals=new Point3D[basePack.faceCount+1];
		for (int f=1;f<=basePack.faceCount;f++) {
			dcel.Face face=basePack.packDCEL.faces[f];
			int[] v=face.getVerts();
			Complex z0=basePack.getCenter(v[0]);
			Complex z1=basePack.getCenter(v[1]);
			Complex z2=basePack.getCenter(v[2]);
			
			// get the sides as 3 vectors
			Point3D side01=new Point3D(z1.x-z0.x,z1.y-z0.y,
					dataValues[v[1]]-dataValues[v[0]]);
			Point3D side12=new Point3D(z2.x-z1.x,z2.y-z1.y,
					dataValues[v[2]]-dataValues[v[1]]);
			normals[f]=Point3D.CrossProduct(side01,side12);
			double nm=normals[f].norm();
			if (nm<.00000001 && nm<.000001*side01.norm()*side12.norm()) // should not be so small
				throw new DataException("face normal is ambiguous");
			
			// normalize
			normals[f]=normals[f].divide(nm);
			
			// TODO: might want check to see if essentially vertical
			
		}
		return;
	}
	
	/**
	 * Store desired values in 'dataValues'
	 * @param values []double
	 * @return count, 0 on error
	 */
	public int setDataValues(double []values) {
		if (values==null)
			return 0;
		if (values.length>basePack.nodeCount+1) {
			CirclePack.cpb.errMsg("given vector is too long ("+values.length+") for 'basePack' ("+basePack.nodeCount+")");
		}
		
		dataValues=new double[values.length];
		int j=0;
		for (j=1;j<values.length;j++) 
			dataValues[j]=values[j];
		return j-1;
	}
	
	/**
	 * Given BaryPoint 'inpt' as starting point, return new
	 * BaryPoint with 'face' properly set (i.e., we are determining 
	 * which face the streamline goes into next). Other routines 
	 * will find where it goes in that face.
	 * 
	 * If 'inpt' is interior, return this face or possibly not move 
	 * at all. If on edge interior, might stay in this face (generally
	 * implying a move along the edge to a corner), or go into
	 * neighboring face if there is one.
	 * @param inpt BaryPoint
	 * @return BaryPoint, null on error or no movement (ie. stationary)
	 */
	public BaryPoint whichWay(BaryPoint inpt,boolean uphill) {
		if (inpt.face<=0)
			return null;
		
		int ins=BaryPoint.baryPtInside(inpt);
		
		// outside? error
		if (ins<0) 
			return null;
		
		// interior? use same face
		if (ins==0) {
			BaryPoint outpt=new BaryPoint(inpt);
			outpt.face=inpt.face;
			return outpt;
		}
		
		// at vertex?
		if (ins<4) {
			int v=basePack.packDCEL.faces[inpt.face].getVerts()[ins-1];
			// if boundary vertex, stop
			if (basePack.isBdry(v))
				return null;
			
			int num=basePack.countFaces(v);
			int[] flower=basePack.getFlower(v);
			Complex []edgevec=new Complex[num+1];
			for (int j=0;j<num;j++) {
				edgevec[j]=basePack.getCenter(flower[j]).minus(basePack.getCenter(v));
			}
			edgevec[num]=edgevec[0];
			
			// what is the largest gradient that goes into its face?
			int bestindx=-1;
			double bestgrad=-1.0;
			int[] faceFlower=basePack.getFaceFlower(v);
			for (int j=0;j<num;j++) {
				Complex grad=new Complex(-normals[faceFlower[j]].x,-normals[faceFlower[j]].y);
				if (!uphill)
					grad=grad.times(-1.0);
				if (gradFaceCheck(grad,edgevec[j],edgevec[j+1])) {
					if (grad.abs()>bestgrad) {
						bestgrad=grad.abs();
						bestindx=j;
					}
				}
			}
			
			// if there's none, then look for best edge
			if (bestindx<0 || bestgrad<=0.0) {
				bestgrad=-1;
				bestindx=-1;
				for (int j=0;j<num;j++) {
					double jump=dataValues[flower[j]]-dataValues[v];
					if (!uphill)
						jump *= -1.0;
					jump /= edgevec[j].abs();
					if (jump>bestgrad) {
						bestgrad=jump;
						bestindx=j;
					}
				}
			}				
			
			// did we find some direction to go?
			if (bestgrad>0.0 && bestindx>=0) {
				int bestface=faceFlower[bestindx];
				int vindx=basePack.packDCEL.faces[bestface].getVertIndx(v);
				double b1=0.0;
				double b2=0.0;
				if (vindx==0)
					b1=1.0;
				else if (vindx==1)
					b2=1.0;
				BaryPoint outpt=new BaryPoint(b1,b2);
				outpt.face=bestface;
				return outpt; 
			}
			
			// vert must be extremum
			return null;
		} // end of vertex
		
		// on edge? 
		if (ins>10) {
			dcel.Face face=basePack.packDCEL.faces[inpt.face];
			int []vert=face.getVerts();
			int v=-1;
			int w=-1;
			int v_indx; // which index in 'vert'?
			if (ins==12) {
				v_indx=0;
				v=vert[0];
				w=vert[1];
			}
			else if (ins==13) {
				v_indx=2;
				v=vert[2];
				w=vert[0];
			}
			else {
				v_indx=1;
				v=vert[1];
				w=vert[2];
			}
			
			// stop if this is boundary edge
			if (basePack.isBdry(v) && basePack.isBdry(w))
				return null;

			// inpt.face is on the left of vw_edge
			int face_l=inpt.face;
			HalfEdge vwhe=basePack.packDCEL.findHalfEdge(new EdgeSimple(v,w));
			int face_r=vwhe.twin.face.faceIndx;
			int vw_indx=basePack.nghb(v, w);
			int num=basePack.countFaces(v);
			Complex vw_edge=basePack.getCenter(w).minus(basePack.getCenter(v));
			
			// have to decide if going left (this same face) or going right (neighboring
			//   face) are viable, then compare them.
			Complex grad_l=new Complex(-normals[face_l].x,-normals[face_l].y);
			Complex grad_r=new Complex(-normals[face_r].x,-normals[face_r].y);
			if (!uphill) {
				grad_l=grad_l.times(-1.0);
				grad_r=grad_r.times(-1.0);
			}
			boolean ck_left=true;
			boolean ck_right=true;
			
			// do the gradients point into the faces?
			double arg_l=grad_l.divide(vw_edge).arg();
			double arg_r=grad_r.divide(vw_edge).arg();
			if ((arg_l<0.0 && arg_l>-Math.PI) || grad_l.abs()<PackData.TOLER) 
				ck_left=false;
			if (arg_r>=0 || grad_r.abs()<PackData.TOLER)
				ck_right=false;

			// neither face? perhaps follow edge, so return current face
			if (!ck_left && !ck_right) {
				return inpt.clone();
			}
			
			if (!ck_right) {
				BaryPoint outpt=new BaryPoint(inpt);
				outpt.face=face_l;
				return outpt;
			}
			
			// right is our choice; must adjust face
			vert=basePack.packDCEL.faces[face_r].getVerts();
			int k=-1;
			for (int j=0;(j<3 && k<0);j++) {
				if (vert[j]==w)
					k=j;
			}
			if (k<0)
				return null;
			
			// have to find bary coords from face_l
			double bv=0.0;
			double bw=0.0;
			if (v_indx==0) {
				bv=inpt.b0;
				bw=inpt.b1;
			}
			else if (v_indx==1) {
				bv=inpt.b1;
				bw=inpt.b2;
			}
			else {
				bv=inpt.b2;
				bw=inpt.b0;
			}
			
			// set them  w.r.t. face_r
			BaryPoint outpt=null;
			if (k==0) {
				outpt=new BaryPoint(bw,bv);
			}
			else if (k==1) {
				outpt=new BaryPoint(0,bw);
			}
			else {
				outpt=new BaryPoint(bv,0);
			}
			outpt.face=face_r;

			return outpt;
		} // done with edge
		
		// shouldn't reach here
		return null; 
	}

	/**
	 * Given a gradient direction and the left/right sides of a
	 * triangular face, return true if the gradient points into 
	 * the face. 
	 * @param grad Complex
	 * @param right Complex
	 * @param left Complex
	 * @return boolean, true if grad points into face
	 */
	public static boolean gradFaceCheck(Complex grad,Complex right,Complex left) {
		try {
			if (left.divide(grad).arg()<0.0 || right.divide(grad).arg()>0.0)
				return false;
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

}
