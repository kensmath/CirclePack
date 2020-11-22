package branching;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import complex.Complex;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.ParserException;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import komplex.CookieMonster;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import rePack.EuclPacker;
import rePack.HypPacker;
import util.UtilPacket;

/**
 * A @see GenBranchPt of the "fractured" type developed by James Ashe.
 * The specified face, 'singFace', and surrounding chain are cut from
 * the parent packing: local vertices 1,2,3 correspond in order with
 * vertices of 'singFace' in the parent.
 * 
 * @author kens
 *
 */

public class FracBranchPt extends GenBranchPt {
	
//	public int myIndex;		// face whose vertices support the branching
	double []cos_overs;         // cos of overlap angle attached to an edge.
	FaceLink borderLink;  		// chain used for layout (local indexing)

	// Constructor
	public FracBranchPt(PackData p,int bID,int f,double aim) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.FRACTURED;
		myIndex=f;
		super.initLocalPacking(); // this calls createMyPack
	}

	/**
	 * Create packing via a cookie method; set 'vertexMap' and 'bdryLink'
	 */
	public PackData createMyPack() {
		PackData myPack=null;

		// organize face vertices so vert[0] is interior
		Face face=packData.faces[myIndex];
		int []vert=new int[3];
		for (int i=0;i<3;i++)
			vert[i]=face.vert[i];
		int v=vert[0];
		if (packData.kData[v].bdryFlag!=0) {
			int hold=vert[0];
			vert[0]=vert[1];
			vert[1]=vert[2];
			vert[2]=hold;
			v=vert[0];
			if (packData.kData[v].bdryFlag!=0) {
				hold=vert[0];
				vert[0]=vert[1];
				vert[1]=vert[2];
				vert[2]=hold;
				v=vert[0];
				if (packData.kData[v].bdryFlag!=0)
					throw new CombException("face "+myIndex+" has no interior vertex");
			}
		}

		// get surrounding face link
		NodeLink corn=new NodeLink(packData);
		for (int ii=0;ii<3;ii++)
			corn.add(vert[ii]);
		bdryLink=PackData.islandSurround(packData,corn);
		if (bdryLink==null)
			throw new CombException("Didn't get 'faceSurround' list");

		// cookie
		NodeLink seedlist=new NodeLink(packData);
		for (int j=0;j<3;j++)
			seedlist.add(face.vert[j]);
		packData.gen_mark(seedlist,-1,true); // mark generations from the face
		
		// Note: need to save/restore parent 'PoisonVerts'
		NodeLink holdPoison=null;
		if (packData.poisonVerts!=null && packData.poisonVerts.size()>0)
			holdPoison=packData.poisonVerts.makeCopy();

		packData.poisonVerts=new NodeLink(packData,"{c:m.gt.1}");
		packData.poisonEdges=new EdgeLink(packData,"Ivw P");

  	  	CookieMonster cM=null; // (note: may get all of parent)
  	  	try {
  	  		cM=new CookieMonster(packData,new String("-v "+v));
  	  		int outcome=cM.goCookie();
  	  		if (outcome<0)
  	  			throw new CombException();
 	  		if (outcome>0) {
  	  			myPack=cM.getPackData();
  	  	  		myPack.cpScreen=null;
  	  	  		myPack.hes=packData.hes;  
  	  	  		vertexMap=myPack.vertexMap.makeCopy();
  	  		}
  	  		else { // no vertices were cut out 
  	  			myPack=packData.copyPackTo();
  	  			myPack.cpScreen=null;
  	  			vertexMap=myPack.vertexMap=new VertexMap();
  	  			for (int vv=1;vv<=myPack.nodeCount;vv++)
  	  				vertexMap.add(new EdgeSimple(vv,vv));
  	  		}
  	  		
  	  		// if spherical, best we can do for now is eucl
  	  		if (myPack.hes>0) 
  	  			myPack.hes=0;
  	  		
  	  		// swap nodes to get singFace to be <1,2,3> 
  	  		vertexMap=myPack.vertexMap.makeCopy();
  	  		int v0=vertexMap.findV(vert[0]);
  	  		int v1=vertexMap.findV(vert[1]);
  	  		int v2=vertexMap.findV(vert[2]);
  	  		myPack.swap_nodes(v0, 1);
  	  		myPack.swap_nodes(v1, 2);
  	  		myPack.swap_nodes(v2, 3);
  	  		vertexMap=myPack.vertexMap.makeCopy(); // reset to get the swaps
  	  		myPack.alpha=1;
  	  		myPack.gamma=2;
  	  		myPack.setCombinatorics();
  	  		boolean debug=false;
  	  		if (debug)
  	  			DebugHelp.debugPackWrite(myPack,"Frac.p");
  	  	} catch (Exception ex) {
  	  		packData.poisonVerts=holdPoison;
  	  		throw new CombException("cookie failed: "+ex.getMessage());
  	  	}
  	  	packData.poisonVerts=holdPoison;
  	  	
		// default overlaps
		cos_overs=new double[3];
		cos_overs[0]=cos_overs[1]=cos_overs[2]=1.0;
		
		// default colors 
		myPack.kData[1].color=new Color(200,0,0); // red
		myPack.kData[2].color=new Color(0,200,0); // green
		myPack.kData[3].color=new Color(0,0,200); // blue
		
		// default aims
		myPack.set_aim_default();
		
		// need to convert 'bdryLink' to local face numbers
		borderLink=new FaceLink(myPack);
		Iterator<Integer> dL=bdryLink.iterator();
		while (dL.hasNext()) {
			int F=dL.next();
			borderLink.add(myPack.what_face(vertexMap.findV(packData.faces[F].vert[0]),
					vertexMap.findV(packData.faces[F].vert[1]),
					vertexMap.findV(packData.faces[F].vert[2])));
		}
		
		myPack.alloc_overlaps();
		
		matchCount=myPack.nodeCount;
		
		// Verts 1,2,3 are packed here; set aim < 0 in parent
		for (int vv=1;vv<=3;vv++)
			packData.rData[vertexMap.findW(vv)].aim=-1.0;
		
		// 'rData' points to corresponding parent 'rData'
		for (int vv=1;vv<=matchCount;vv++)
			myPack.rData[vv]=packData.rData[vertexMap.findW(vv)];
				
		setPoisonEdges();
		return myPack;
	}
	
	public void delete() {
		
		// reset aims in parent
		for (int vv=1;vv<=3;vv++) {
			int k=vertexMap.findW(vv);
			if (packData.kData[k].bdryFlag==0)
				packData.rData[k].aim=2.0*Math.PI;
		}
		
		// remove poison edges
		packData.poisonEdges.removeUnordered(parentPoison);
	}

	/**
	 * Iterate to find how to distribute extra angle sum among the three
	 * fractured branch points to give a coherent packing; idea is James 
	 * Ashe's. The extra at each needs to be twice the angle there in the 
	 * triangle they form. This is a fixed point of the map that takes a
	 * given distribution, does the repacking, and puts out the resulting
	 * angles.
	 * @param cycles max iterations (if <0, set default)
	 * @return @see UtilPacket: 'rtnFlag' -1 on error, 'value' l^2 norm 
	 * of angle sum error.
	 */
	public UtilPacket riffleMe(int cycles) {
		if (cycles<0)
			cycles = 5; // subject to trial and error
		UtilPacket uP=new UtilPacket();
		uP.rtnFlag=-1;
		double offBy=1.0;
		while (offBy>RIFF_TOLER && uP.rtnFlag<cycles) {
			double []rad=new double[3];
			UtilPacket inuP=new UtilPacket();
			for (int i=0;i<3;i++)
				rad[i]=myPackData.getRadius(i+1);
			
			// set new aims based on current face angles
			for (int i=0;i<3;i++) { 
				if (myPackData.hes<0) {
					HyperbolicMath.h_cos_s_overlap(rad[i],rad[(i+1)%3],rad[(i+2)%3],
							cos_overs[i],cos_overs[(i+1)%3],cos_overs[(i+2)%3],inuP);
				}
				else {
					EuclMath.e_cos_overlap(rad[i],rad[(i+1)%3],rad[(i+2)%3],
							cos_overs[i],cos_overs[(i+1)%3],cos_overs[(i+2)%3],inuP);
				}
				myPackData.rData[i+1].aim=pi2+2.0*Math.acos(inuP.value);
			}
			
			// repack
			try {
				if (myPackData.hes<0)
					HypPacker.oldReliable(myPackData,cycles);
				else 
					EuclPacker.oldReliable(myPackData,cycles);
			} catch (Exception ex) {
				throw new ParserException("repack layout failed");
			}
			
			// compute new angles and get error
			double accum=0.0;
			for (int i=0;i<3;i++) 
				rad[i]=myPackData.getRadius(i+1);
			for (int i=0;i<3;i++) { 
				if (myPackData.hes<0) {
					HyperbolicMath.h_cos_s_overlap(rad[i],rad[(i+1)%3],rad[(i+2)%3],
							cos_overs[i],cos_overs[(i+1)%3],cos_overs[(i+2)%3],inuP);
				}
				else {
					EuclMath.e_cos_overlap(rad[i],rad[(i+1)%3],rad[(i+2)%3],
							cos_overs[i],cos_overs[(i+1)%3],cos_overs[(i+2)%3],inuP);
				}
				double diff=2.0*Math.acos(inuP.value)-(myPackData.rData[i+1].aim-pi2);
				accum +=diff*diff;
			}
			offBy=Math.sqrt(accum);
			uP.rtnFlag++;
		} // end of while
		uP.value=offBy;
		return uP;
	}
	
	/**
	 * Layout procedure uses 'borderLink': place successive faces starting with 
	 * first in 'bdryLink'. Optionally normalize, which freshly places first
	 * face with vert 1 at origin and rotates afterward to put 2 on positive y-axis.
	 * Update 'myHolonomy'.
	 * @param norm boolean, if true, freshly layout the first face an normalize
	 * @return double, error in myHolonomy
	 */
	public double layout(boolean norm) {

		int F=borderLink.get(0);

		// place first face 
		int indx=myPackData.face_index(F,1);
		myPackData.place_face(F,indx);
		
		// initial location of F
		Complex []firstF=new Complex[3];
		firstF[0]=myPackData.getCenter(myPackData.faces[F].vert[0]);
		firstF[1]=myPackData.getCenter(myPackData.faces[F].vert[1]);
		firstF[2]=myPackData.getCenter(myPackData.faces[F].vert[2]);
		
		// layout in order
		myPackData.recomp_facelist(borderLink);
		
		// final location of F (only different if 'bdryLink' is closed)
		Complex []lastF=new Complex[3];
		lastF[0]=myPackData.getCenter(myPackData.faces[F].vert[0]);
		lastF[1]=myPackData.getCenter(myPackData.faces[F].vert[1]);
		lastF[2]=myPackData.getCenter(myPackData.faces[F].vert[2]);

		// update myHolonomy
		myHolonomy=Mobius.mob_xyzXYZ(firstF[0],firstF[1],firstF[2],lastF[0],lastF[1],lastF[2],0,0);
		
		// rotate to get 2 on positive y-axis
		if (norm) {
			double theta=(-1.0)*myPackData.getCenter(2).arg();
			myPackData.rotate(theta+Math.PI/2.0);
		}
		
		return Mobius.frobeniusNorm(myHolonomy);
	}
	
	/**
	 * Assume radii have been updated, what is the error?
	 * @return double, l^2 error in fracturing accuracy
	 */
	public double currentError() {
		double accum=0.0;
		double []rad=new double[3];
		UtilPacket inuP=new UtilPacket();
		
		for (int i=0;i<3;i++) 
			rad[i]=myPackData.getRadius(i+1);
		for (int i=0;i<3;i++) { 
			if (myPackData.hes<0) {
				HyperbolicMath.h_cos_s_overlap(rad[i],rad[(i+1)%3],rad[(i+2)%3],
						cos_overs[i],cos_overs[(i+1)%3],cos_overs[(i+2)%3],inuP);
			}
			else {
				EuclMath.e_cos_overlap(rad[i],rad[(i+1)%3],rad[(i+2)%3],
						cos_overs[i],cos_overs[(i+1)%3],cos_overs[(i+2)%3],inuP);
			}
			double diff=2.0*Math.acos(inuP.value)-(myPackData.rData[i+1].aim-pi2);
			accum +=diff*diff;
		}
		return Math.sqrt(accum);
	}
	
	/**
	 * Fractured branch point parameters are the overlap angles associated
	 * with the three edges. Note that overlap angle overlaps[j] is for edge 
	 * opposite vert j+1: e.g. overlaps[2] is opposite from vert 3. 
	 * @param Vector<Vector<String>> flagSegs, overlaps
	 * @return int, 3 for success
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("missing parameters: invdistances 1,2,3");
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				for (int i=0;i<3;i++) {
					double ovlp=Double.parseDouble(items.remove(0));
					if (ovlp<0 || ovlp>=1.0)
						throw new ParserException("inv distance not in (0,1]");
					cos_overs[i]=Math.cos(ovlp*Math.PI);
				}
				myPackData.set_single_invDist(2,3,cos_overs[0]);
				myPackData.set_single_invDist(3,1,cos_overs[1]);
				myPackData.set_single_invDist(1,2,cos_overs[2]);
				count=3;
			} catch(Exception ex) {
				throw new ParserException();
			}
		}
		return count;
	}
	

	/**
	 * 
	 * @return String
	 */
	public String getParameters() {
		return new String("Fractured branch face, aim "+
				myAim/Math.PI+"*Pi on face "+myIndex+", overlaps");
	}
	
	public String reportExistence() {
		return new String("Started 'fractured' branch point; face = "+myIndex);
	}
	
	public String reportStatus() {
		return new String("'fractured', ID "+branchID+": face="+myIndex+
				", aim="+myAim+", holonomy err="+super.myHolonomyError());
	}

	/**
	 * We make the three edges of 'singFace' into poison edges
	 */
	public int setPoisonEdges() {
		EdgeLink elink=new EdgeLink(packData);
		elink.add(new EdgeSimple(myPackData.faces[myIndex].vert[0],myPackData.faces[myIndex].vert[1]));
		elink.add(new EdgeSimple(myPackData.faces[myIndex].vert[1],myPackData.faces[myIndex].vert[2]));
		elink.add(new EdgeSimple(myPackData.faces[myIndex].vert[2],myPackData.faces[myIndex].vert[0]));
		parentPoison=elink;
		return elink.size();
	}
	
	/**
	 * The parent places all the circles.
	 * @return 1
	 */
	public int placeMyCircles() {
		return 1;
	}
	
}
