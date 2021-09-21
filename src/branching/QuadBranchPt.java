package branching;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import komplex.CookieMonster;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.UtilPacket;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.ParserException;

/**
 * A @see GenBranchPt of the "quad" type invented by James Ashe.
 * 
 * @author kens
 *
 */

public class QuadBranchPt extends GenBranchPt {
	
	// faces sharing edge supporting the branching ('myIndex' set to singFace_f)
	public int singFace_f;		
	public int singFace_g;
	double cosOver;        		// cos of overlap angle between 1 and 2.
	public int fracFace;        // which face (f=1, g=2) is doing the flipping? 
	FaceLink borderLink;  		// chain used for layout (local indexing)

	// Constructor
	public QuadBranchPt(PackData p,int bID,int f,int g,double aim) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.FRACTURED;
		singFace_f=myIndex=f;
		singFace_g=g;
		super.initLocalPacking(); // this calls createMyPack
	}

	/**
	 * Create packing via a cookie method; set 'vertexMap' and 'bdryLink'
	 * @return @see PackData
	 */
	public PackData createMyPack() {
		PackData myPack=null;
		
		int indx=packData.face_nghb(singFace_g,singFace_f);
		if (indx<0)
			throw new CombException("Faces do not share an edge");
		int v=packData.faces[singFace_f].vert[indx];
		int w=packData.faces[singFace_f].vert[(indx+1)%3];

		// organize face vertices so vert[0] is interior
		if (packData.isBdry(v)) {
				indx=(indx+1)%3;
				v=packData.faces[singFace_f].vert[indx];
				if (packData.isBdry(v)) 
					throw new CombException("Shared edge has no inteior end");
		}
		
		int f=singFace_f;
		int g=singFace_g;
		if (v==w) {
			f=g;
			g=singFace_f;
		}

		// arrange vertex indexing: shared edge e=(1,2), others 3 and 4
		int []vert=new int[4];
		indx=packData.face_nghb(g,f);
		vert[0]=v;
		vert[1]=packData.faces[f].vert[(indx+1)%3];
		vert[3]=packData.faces[f].vert[(indx+2)%3];
		indx=packData.face_nghb(f,g);
		vert[2]=packData.faces[g].vert[(indx+2)%3];

		// get surrounding face link
		NodeLink corn=new NodeLink(packData);
		corn.add(vert[0]);
		corn.add(vert[2]);
		corn.add(vert[1]);
		corn.add(vert[3]);
//		bdryLink=PackData.islandSurround(packData,corn);
//		if (bdryLink==null)
//			throw new CombException("Didn't get 'faceSurround' list");

		// cookie
		NodeLink seedlist=new NodeLink(packData,"If "+singFace_f+" "+singFace_g);
		packData.gen_mark(seedlist,-1,true); // mark generations from pair of faces
		
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
  	  		myPack.swap_nodes(v2, 4);
  	  		vertexMap=myPack.vertexMap.makeCopy();
  	  		myPack.setAlpha(1);
  	  		myPack.setGamma(2);
  	  		myPack.setCombinatorics();
  	  		boolean debug=false;
  	  		if (debug)
  	  			DebugHelp.debugPackWrite(myPack,"Quad.p");
  	  	} catch (Exception ex) {
  	  		packData.poisonVerts=holdPoison;
  	  		throw new CombException("cookie failed: "+ex.getMessage());
  	  	}
  	  	packData.poisonVerts=holdPoison;
  	  	
  	  	// default overlap edge (1,2), tangency
  	  	cosOver=1.0;
		
		// default colors: right side face ('singFace_g') is red/green/blue 
		myPack.setCircleColor(1,new Color(200,0,0)); // red
		myPack.setCircleColor(2,new Color(0,0,240)); // blue
		myPack.setCircleColor(3,new Color(0,200,0)); // green
		myPack.setCircleColor(4,new Color(180,180,240)); // purple
				
		// need to convert 'bdryLink' to local face numbers
		borderLink=new FaceLink(myPack);
//		Iterator<Integer> dL=bdryLink.iterator();
//		while (dL.hasNext()) {
//			int F=dL.next();
//			borderLink.add(myPack.what_face(vertexMap.findV(packData.faces[F].vert[0]),
//					vertexMap.findV(packData.faces[F].vert[1]),
//					vertexMap.findV(packData.faces[F].vert[2])));
//		}
		
		myPack.set_aim_default();
		myPack.alloc_overlaps();
		
		matchCount=myPack.nodeCount;
		
		// Verts 1,2,3,4 are packed here; set aim < 0 in parent
		for (int vv=1;vv<=4;vv++)
			packData.setAim(vertexMap.findW(vv),-1.0);
		
		// 'rData' points to corresponding parent 'rData'
		for (int vv=1;vv<=matchCount;vv++)
			myPack.rData[vv]=packData.rData[vertexMap.findW(vv)];
		
		setPoisonEdges();
  	  	return myPack;
	}
	
	public void delete() {
		
		// Verts 1,2,3,4 are packed here; set aim < 0 in parent
		for (int vv=1;vv<=4;vv++) {
			if (!packData.isBdry(vv))
				packData.setAim(vertexMap.findW(vv),2.0*Math.PI);
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
		// TODO: dhow do we repack?
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
		myHolonomy=new Mobius(); // identity
		return Mobius.frobeniusNorm(myHolonomy);
	}
	
	/**
	 * TODO: 
	 */
	public double currentError() {
		// TODO: yet to code this
		return 0.0;
	}
	
	/**
	 * Quadface branch point parameters are the overlap angles associated with 
	 * the shared edge and face designation for layout. Data should be "n x",
	 * where n is 1 or 2 (meaning, face f or g, resp) and overlap is x*Pi,
	 * x in [-1,1]. 
	 * @param flagSegs Vector<Vector<String>>
	 * @return int, count
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("missing parameters");
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				// which face?
				int n=Integer.parseInt(items.get(0));
				if (n==2)
					fracFace=singFace_g;
				else
					fracFace=singFace_f;
				
				// what overlap?
				double ovlp=Double.parseDouble(items.get(1));
				if (ovlp<-1.0 || ovlp>1.0)
					throw new ParserException("overlap not in [-1,1]");
				cosOver=Math.cos(ovlp*Math.PI);
				myPackData.set_single_invDist(1,2,cosOver);
				count=1;
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
		return new String("QuadFace branch point: aim "+
				myAim/Math.PI+"*Pi on faces "+singFace_f+" "+singFace_g);
	}
	
	public String reportExistence() {
		return new String("Started 'QuadFace' branch point; faces = "+singFace_f+" and "+singFace_g);
	}
	
	public String reportStatus() {
		return new String("'QuadFace', ID "+branchID+": faces f,g ="+singFace_f+", "+singFace_g+
				", aim="+myAim+", holonomy err="+super.myHolonomyError());
	}

	/**
	 * Make all edges of 'singFace_f' and 'singFace_g' poison
	 */
	public int setPoisonEdges() {
		EdgeLink elink=new EdgeLink(packData);
		elink.add(new EdgeSimple(myPackData.faces[singFace_f].vert[0],myPackData.faces[singFace_f].vert[1]));
		elink.add(new EdgeSimple(myPackData.faces[singFace_f].vert[1],myPackData.faces[singFace_f].vert[2]));
		elink.add(new EdgeSimple(myPackData.faces[singFace_f].vert[2],myPackData.faces[singFace_f].vert[0]));
		elink.add(new EdgeSimple(myPackData.faces[singFace_g].vert[0],myPackData.faces[singFace_g].vert[1]));
		elink.add(new EdgeSimple(myPackData.faces[singFace_g].vert[1],myPackData.faces[singFace_g].vert[2]));
		elink.add(new EdgeSimple(myPackData.faces[singFace_g].vert[2],myPackData.faces[singFace_g].vert[0]));
		parentPoison=elink;
		return elink.size();
	}
	
	/** 
	 * All circles are placed by the parent.
	 */
	public int placeMyCircles() {
		return 1;
	}
}
