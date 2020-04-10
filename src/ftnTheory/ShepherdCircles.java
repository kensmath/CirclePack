package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import listManip.FaceLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.DispFlags;
import util.UtilPacket;

/**
 * For experimenting with various alternatives to standard branching:
 * e.g., CranePoints, CraneFaces, Ashe fractional branching, etc.
 * Idea is to use auxiliary "Shepherd" circles to manipulate the
 * geometry and hopefully exploit existence and uniqueness theorems.
 * Started in Bristol, October 2011.
 * @authors Stephenson, Ed Crane, and James Ashe.
 */
public class ShepherdCircles extends PackExtender {
	public Vector<CraneFace> craneFaces=null;

	// Constructor
	public ShepherdCircles(PackData p) {
		super(p);
		extensionType="SHEPHERD_CIRCLES";
		extensionAbbrev="SC";
		toolTip="'ShepherdCircles': using auxiliary circles to construct parameterized "+
			" branching: CranePoints, CraneFaces, Ashe iteration, etc.";
		registerXType();
		if (packData.hes>0) {
			CirclePack.cpb.errMsg("SC Warning: packing should not be spherical");
		}
		if (running) {
			packData.packExtensions.add(this);
		}
		craneFaces=new Vector<CraneFace>(3);
	}	
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		// ----- set CraneFace -----------
		if (cmd.startsWith("setFace")) {
			int f=0;
			try {
				items=flagSegs.get(0);
				f=Integer.parseInt(items.get(0));
			} catch (Exception ex) {
				Oops("must give a face index");
			}
			CraneFace cF=new CraneFace(packData,f);
			if (!cF.OK) { //failed
				this.msg("face "+f+" failed as CraneFace");
				return 0;
			}
			craneFaces.add(cF);
			return 1;
		}
		
		else if (cmd.startsWith("setOv")) {
			int f=0;
			double a=0.0;
			double b=0.0;
			double c=0.0;
			int click=0; 
			try {
				items=flagSegs.get(0);
				f=Integer.parseInt(items.get(0));
				click++;
				a=Double.parseDouble(items.get(1));
				click++;
				b=Double.parseDouble(items.get(2));
				click++;
				c=Double.parseDouble(items.get(3));
				click++;
			} catch (Exception ex) {
				if (click==0) {
					Oops("error: must give CraneFace");
				}
				if (click==1) { // default to tangency for all
					a=b=c=0.0;
				}
				if (click==2) {  // default to tangency for two edges
					b=c=0.0;
				}
				if (click==3) { // force overlaps to sum to 1
					c=1-a-b;
				}
			}
			
			if (a<0.0 || b<0.0 || c<0.0)
				Oops("SC: improper overlaps: "+a+", "+b+", "+c);
			
			CraneFace cf=null;
			if ((cf=isCF(f))!=null) {
				return cf.setOverlaps(a,b,c);
			}
			return 0;
		}
		
		else if (cmd.startsWith("layff")) {
			int f=0;
			try {
				items=flagSegs.get(0);
				f=Integer.parseInt(items.get(0));
			} catch (Exception ex) {
				Oops("must give a face index");
			}
			if (isCF(f)==null) {
				Oops("face "+f+" isn't a CraneFace");
			}
			
			int u=packData.faces[f].vert[2];

			// put the first face in standard position
			packData.place_face(1,0);
			
			// first circle at origin
//			packData.rData[v].center=new Complex(0.0);
			
			// second circle on positive real axis
//			double invdist=packData.kData[v].overlaps[packData.nghb(v, w)];
//			double x=EuclMath.e_invdist_length(packData.rData[v].rad,packData.rData[w].rad,invdist);
//			packData.rData[w].center=new Complex(x,0.0);
			
			// Third, compute like normal, then conjugate
//			SimpleCircle sC=EuclMath.e_compcenter(packData.rData[v].center,packData.rData[w].center,
//					packData.rData[v].rad,packData.rData[w].rad,packData.rData[u].rad,true,
//					packData.kData[w].overlaps[packData.nghb(w, u)],
//					packData.kData[v].overlaps[packData.nghb(v, u)],
//					packData.kData[v].overlaps[packData.nghb(v, w)]);
			packData.rData[u].center=new Complex(packData.rData[u].center.conj());
			return 1;
		}
		
		else if (cmd.startsWith("layout")) {
			FaceLink flist=null;
			try {
				flist=new FaceLink(packData,flagSegs.get(0));
			} catch(Exception ex) {
				flist=new FaceLink(packData,"a");
				return 0;
			}
			int f=flist.remove(0); // first should be already placed
			DispFlags dflags=new DispFlags(null);
			packData.layout_facelist(null,flist,dflags,null,true,false,f,-1);
			return flist.size();
		}
		
		else if (cmd.startsWith("ashe")) {
			int f=0;
			int n=1;
			try {
				items=flagSegs.get(0);
				f=Integer.parseInt(items.get(0));
				try {
					n=Integer.parseInt(items.get(1));
				} catch (Exception ex) {}
			} catch (Exception ex) {
				Oops("usage: f n, index of CraneFace and number of iterations");
			}
			CraneFace cF=null;
			if ((cF=isCF(f))==null) {
				Oops("face "+f+" isn't a CraneFace");
			}
			
			double error=AsheFixedPoint(n,cF);
			msg("Ashe iteration error "+error);
			return n;
		}

		return super.cmdParser(cmd, flagSegs); // if not found, try super
	}
	
	/**
	 * Iterate to find weights giving a coherent packing; idea is James Ashe's.
	 * Weights need to be twice the angles in the triangle formed by the 
	 * fractured branch points.
	 * @param n, int, max iterations
	 * @return number of iterations
	 */
    public double AsheFixedPoint(int n,CraneFace cF) {
		int count=0;
		double offBy=1.0;
		int f=cF.face;
		int []verts=new int[3];
		verts=packData.faces[f].vert;
		double []excess=new double[3];
		double []weights=new double[3];
		
		// set initial excess to pi/3
		for (int j=0;j<3;j++) {
			weights[j]=Math.PI/3.0;
			packData.rData[verts[j]].aim=2.0*(Math.PI+weights[j]);
		}

		// now iterate
		while ((offBy>.00001 && count<n)) {
			
			// NOTE: repack (-o option because of overlaps)
			cpCommand(packData,"repack -o");
		
			// get 'excess', how much each angle of triangle exceeds 2pi divided by 2
			UtilPacket uP=new UtilPacket();
			for (int j=0;j<3;j++) {
				if (!genCosOverlap(packData.rData[verts[j]].rad,
						packData.rData[verts[(j+1)%3]].rad,
						packData.rData[verts[(j+2)%3]].rad,
						cF.overlps[j],cF.overlps[(j+1)%3],cF.overlps[(j+2)%3],uP)) {
					Oops("some problem in getting cos of overlaps");
				}
				excess[j]=Math.acos(uP.value); // twice the excess angle
			}
			
			// how far off?
			offBy=0.0;
			for (int j=0;j<3;j++) { 
				double diff = (excess[j]-weights[j]);
				offBy += diff*diff;
				weights[j]=excess[j];
			}
			
			
			// use excess to set new weights
			for (int j=0;j<3;j++) {
				packData.rData[verts[j]].aim=2*(Math.PI+weights[j]);
			}

			msg("FracBranching 3: error="+offBy+" after "+count+" iterations");
			count++;

		} // end of while
		return offBy;
	}

    /**
     * Compute angle sum given 3 radii and (overlaps of opposite edges).
     * @param R
     * @param r1
     * @param r2
     * @param overR, opposite to R
     * @param over1, opposite to r1
     * @param over2, opposite to r2
     * @param uP, UtilPacket for passing results, must be created by calling routine
     * @return true if it seemed to work.
     */
	public boolean genCosOverlap(double R,double r1,double r2,double overR,double over1,double over2,UtilPacket uP) {
		switch(packData.hes) {
		case -1: {
 			return HyperbolicMath.h_cos_s_overlap(R, r1, r2, overR,over1,over2,uP);
		}
		case 1: {
			Oops("cannot compute spherical case; need to insert spherical case.");
			return false;
		}
		default: {
			return EuclMath.e_cos_overlap(R, r1, r2, overR,over1,over2,uP);
		}
	}
}
    /**
     * If f is a Crane face, this returns its 'CraneFace' class.
     * @param f
     * @return, 'CraneFace' 
     */
    public CraneFace isCF(int f) {
    	Iterator<CraneFace> cfs=craneFaces.iterator();
    	CraneFace cf=null;
    	while (cf==null && cfs.hasNext()) {
    		cf=cfs.next();
    		if (cf.face!=f)
    			cf=null;
    		else return cf;
    	}
    	return null;
	}
    
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("setFace","f",null,
			"designate f as a singular Crane face; return 'true' on success"));
		cmdStruct.add(new CmdStruct("setOver","f a b",null,
			"assuming f is a singular Crane face, set the edge overlaps 01=a, 12=b"));
		cmdStruct.add(new CmdStruct("layff","f",null,"Layout a Crane face; use current "+
		"radii, CraneFace overlaps, normalized position, reverse oriented"));
		cmdStruct.add(new CmdStruct("ashe",null,null,"do Ashe iteration to get coherent packing"));
		cmdStruct.add(new CmdStruct("layout",null,null,"Layout the packing based on "+
			"current data, report holonomy error"));
	}	
	
}

/**
 * Currently an internal class for testing Crane's ideas for branching
 * in interstices.
 * @author kstephe2
 */
class CraneFace {
	PackData packData;
	boolean OK;
	int face;
	double []overlps;
		
	// Constructor
	public CraneFace(PackData p,int f) {
		packData=p;
		if (f<=0 || f>packData.faceCount) {
			OK=false;
			return;
		}
		face=f;
		boolean hit=false;
		
		// are all vertices of the face interior?
		for (int j=0;j<3;j++) { 
			if (packData.kData[packData.faces[f].vert[j]].bdryFlag!=0)
				hit=true;
		}
		if (hit) {
			OK=false;
			return;
		}
		OK=true;
		overlps=new double[3];
		for (int j=0;j<3;j++)
			overlps[j]=Math.cos(Math.PI/3.0); // default overlaps
	}

	/**
	 * Set the overlaps: a, b, and c=1-a-b must all be <=1/2, >=0,
	 * else go to default a=b=c=1/3. Stored as inversive distance,
	 * e.g. cos(a*PI).
	 * @param a in [0,.5], overlap/pi for edge OPPOSITE to vert[0]
	 * @param b in [0,.5], for edge OPPOSITE to vert[1]
	 * @return int: 1 on success, 0 on error (but set to default)
	 */
	public int setOverlaps(double a,double b,double c) {
		// tangency: no true overlaps, so just return
		if (a<.0000001 || b<.0000001 || c<.000001)
			return 1; 
		
		overlps[0]=Math.cos(a*Math.PI);
		overlps[1]=Math.cos(b*Math.PI);
		overlps[2]=Math.cos(c*Math.PI);
		
		// allocate overlap space and set these overlaps:
		//  Note: overlap[.] is overlap for side OPPOSITE vert[.]
		if (!packData.overlapStatus) {
			packData.alloc_overlaps();
		}
		for (int j=0;j<3;j++) {
			int v=packData.faces[face].vert[j];
			int w=packData.faces[face].vert[(j+1)%3];
			packData.set_single_overlap(v,packData.nghb(v,w),overlps[(j+2)%3]);
		}
		
		return 1;
	}
	
}
