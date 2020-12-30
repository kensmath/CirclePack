package branching;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import canvasses.DisplayParser;
import circlePack.PackControl;
import complex.Complex;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import input.CommandStrParser;
import komplex.CookieMonster;
import komplex.DualGraph;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.StringUtil;
import util.UtilPacket;

/**
 * A @see GenBranchPt of the "singular" type conceived by Ed Crane. 
 * The bdryLink surrounds the specified face 'singFace' and the
 * connection circles (so named by James Ashe). The face vertices 
 * 1,2,3 correspond in order with vertices of 'singFace' in the parent.
 * We add a central vertex (with aim 4*pi) and 3 chaperone circles 
 * between the original vertices of 'singFace'. Two overlap angles are 
 * the parameters (along with a third so they sum to PI). Note that
 * because the vertices of 'singFace' will end up overlapping, they 
 * will not get the right angle sum in the parent. We therefore have
 * to scoop them inside the bdryLink and layout more circles in the
 * branch point.
 * Changes made to 'packData':
 *   * add to 'poisonEdges' via EdgeLink 'parentPoison'.
 *   * aims of parent set <0 for vertices of 'myIndex' face (so parent
 *     won't repack them).
 *   * send radius of v to parent after repack if transData[v]<0.
 *   * set overlaps in parent for edges of 'myIndex' face (this affects
 *     its computation of angle sums. 
 *   
 *    
 * @author kens, May 2012
 *
 */

public class SingBranchPt extends GenBranchPt {
	
//	public int myIndex;		// face supporting the branching
	double []cos_overs;         // cos of overlap angle attached to an edge.
	FaceLink borderLink;  		// chain used for layout (local indexing)
	int []chap;					// chaperone circles: sj is across from vert j, j=1,2,3 (chap[0] empty)
	int []connect;				// 'connection' circles (local indices); jth is across from j vert (connect[0] empty)
	int newBrSpot;				// central chaperone should be radius 0, centered at point where three other chaperone intersect
	
	// Constructor
	public SingBranchPt(PackData p,int bID,double aim, int f, double o1,double o2) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.SINGULAR;
		myIndex=f;
		
		// set default overlaps to  pi/3
		cos_overs=new double[3];
		cos_overs[0]=cos_overs[1]=cos_overs[2]=0.5; // cosine of PI/3.0
		
		// debug help
		System.out.println("sing attempt: a = "+aim/Math.PI+"; f = "+f+"; overlaps/Pi "+o1+" "+o2+" "+(1.0-o1-o2));
		
		super.initLocalPacking(); // this calls createMyPack
		resetOverlaps(o1,o2);
	}

	/**
	 * Create packing via a cookie method; set 'vertexMap' and 'bdryLink'
	 */
	public PackData createMyPack() {
		PackData myPack=null;

		// get face surrounding face link
		Face face=packData.faces[myIndex];
		NodeLink beach=new NodeLink(packData);
		for (int ii=0;ii<3;ii++)
			beach.add(face.vert[ii]);
		bdryLink=PackData.islandSurround(packData,beach);
		if (bdryLink==null)
			throw new CombException("Didn't get 'faceSurround' list");

		// identify 'connect's (no longer want these in 'beach')
		int []parentconnect=new int[3]; // parent indices of 'connection' vertices
		for (int ii=0;ii<3;ii++) {
			int vv=face.vert[ii];
			int ww=face.vert[(ii+1)%3];
			int jj=packData.find_common_left_nghb(ww,vv);
			if (jj<0) 
				return null; // 'myIndex' face can't have a bdry edge
			int kk=(ii+2)%3;
			parentconnect[kk]=packData.kData[ww].flower[jj];
		}
		
		// cookie
		NodeLink seedlist=new NodeLink(packData);
		for (int j=0;j<3;j++) {
			seedlist.add(face.vert[j]);
		}
		packData.gen_mark(seedlist,-1,true); // mark generations from the face
		
		// TODO: do we need to save/restore parent 'PoisonVerts'?
		NodeLink holdPoison=null;
		if (packData.poisonVerts!=null && packData.poisonVerts.size()>0)
			holdPoison=packData.poisonVerts.makeCopy();

		packData.poisonVerts=new NodeLink(packData,"{c:m.gt.1}");
		packData.poisonEdges=new EdgeLink(packData,"Ivw P");
		
  	  	CookieMonster cM=null; // (note: may get all of parent)
  	  	try {
  	  		cM=new CookieMonster(packData,new String("-v "+face.vert[0]));
  	  		int outcome=cM.goCookie();
  	  		if (outcome<0)
  	  			throw new CombException();
 	  		if (outcome>0) {
  	  			myPack=cM.getPackData();
  	  	  		myPack.cpScreen=null;
  	  	  		myPack.hes=packData.hes;  
  	  	  		vertexMap=myPack.vertexMap.makeCopy();
  	  		}
  	  		else { // no vertices were cut out, 'vertexMap' is identity
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
  	  		int v0=myPack.vertexMap.findV(face.vert[0]);
  	  		myPack.swap_nodes(v0, 1);
  	  		int v1=myPack.vertexMap.findV(face.vert[1]);
  	  		myPack.swap_nodes(v1, 2);
  	  		int v2=myPack.vertexMap.findV(face.vert[2]);
  	  		myPack.swap_nodes(v2, 3);
  	  		vertexMap=myPack.vertexMap.makeCopy(); // reset to get the swaps
  	  		myPack.alpha=1;
  	  		myPack.gamma=2;
  	  		myPack.setCombinatorics();
  	  		
 	  		// set the local 'connect' vertices
 	  		connect=new int[4];  // indexed from 1
 	  		for (int k=0;k<3;k++)
 	  			connect[k+1]=vertexMap.findV(parentconnect[k]);

 			matchCount=myPack.nodeCount;

  	  		boolean debug=false;
  	  		if (debug)
  	  			DebugHelp.debugPackWrite(myPack,"Frac.p");
  	  	} catch (Exception ex) {
  	  		packData.poisonVerts=holdPoison;
  	  		throw new CombException("cookie failed: "+ex.getMessage());
  	  	}
  	  	packData.poisonVerts=holdPoison;
  	  	
		// Change combinatorics: add barycenter to singFace, chaperone circle
		//   to edges of singFace.
		int centFace=myPack.what_face(1,2,3);
		myPack.add_barycenter(centFace);
		myPack.split_edge(3,1); // opposite 2
		myPack.split_edge(2,3); // opposite 1
		myPack.split_edge(1,2); // opposite 3
		newBrSpot=myPack.nodeCount;
		chap=new int[4]; // chap[0] is not used
		chap[1]=newBrSpot-1;
		chap[2]=newBrSpot-2;
		chap[3]=newBrSpot-3;
		
		// get into correct order
		myPack.swap_nodes(newBrSpot,chap[3]);
		// flower around 'newBrSpot' should be {1,chap[3],2,chap[2],3,chap[3],1}
		
		// default colors 
		myPack.kData[1].color=new Color(200,0,0); // red
		myPack.kData[chap[1]].color=new Color(255,0,0); // light red
		myPack.kData[2].color=new Color(0,200,0); // green
		myPack.kData[chap[2]].color=new Color(0,255,0); // light green
		myPack.kData[3].color=new Color(0,0,200); // blue
		myPack.kData[chap[3]].color=new Color(0,0,255); // light blue
		// set aims 
		myPack.set_aim_default();
		myPack.setAim(newBrSpot,myAim); // 4*pi is only real possibility
		myPack.setAim(chap[1],pi2);
		myPack.setAim(chap[2],pi2);
		myPack.setAim(chap[3],pi2);
		
		myPack.setCombinatorics(); // DebugHelp.debugPackWrite(myPack,"myPack_debug.p");
		
/* debugging
		Iterator<EdgeSimple> vtm=vertexMap.iterator();
		System.err.println("vertexMap");
		while (vtm.hasNext()) {
			EdgeSimple edge=vtm.next();
			System.err.println("("+edge.v+","+edge.w+")");
		}
*/
		
		myPack.alloc_overlaps();
		
		// Verts 1,2,3 are packed locally; set aim < 0 in parent
		for (int vv=1;vv<=3;vv++)
			packData.setAim(vertexMap.findW(vv),-1.0);
		
		// need to convert 'bdryLink' to local face numbers
		borderLink=new FaceLink(myPack);
		Iterator<Integer> dL=bdryLink.iterator();
		while (dL.hasNext()) {
			int F=dL.next();
			borderLink.add(myPack.what_face(vertexMap.findV(packData.faces[F].vert[0]),
					vertexMap.findV(packData.faces[F].vert[1]),
					vertexMap.findV(packData.faces[F].vert[2])));
		}
		
		// set face drawing order: poison edges allow one face to get to 'newBranchSpot' 
		myPack.firstFace=borderLink.get(0);
		myPack.poisonEdges=new EdgeLink(myPack);
		for (int j=1;j<myPack.kData[newBrSpot].num;j++) {
			int vj=myPack.kData[newBrSpot].flower[j];
			int wj=myPack.kData[newBrSpot].flower[j+1];
			myPack.poisonEdges.add(new EdgeSimple(vj,wj));
		}
		myPack.poisonEdges.add(new EdgeSimple(newBrSpot,myPack.kData[newBrSpot].flower[0]));
		myPack.poisonEdges.add(new EdgeSimple(newBrSpot,myPack.kData[newBrSpot].flower[1]));

		GraphLink gl=DualGraph.buildDualGraph(myPack,myPack.firstFace,myPack.poisonEdges);
		myPack.drawingTree=DualGraph.drawSpanner(myPack,gl,myPack.firstFace);
		// DualGraph.tree2Order(myPack,myPack.drawingTree); 
		// deBugging.LayoutBugs.print_drawingorder(myPack);
		// DualGraph.printGraph(myPack.drawingTree);
		
		// set up transData, set some parent 'aim's
		transData=new int[matchCount+1];
		for (int i=1;i<=matchCount;i++) { // bdry are +, rest minus
			int ww=vertexMap.findW(i);
			if (myPack.kData[i].bdryFlag!=0) 
				transData[i]=ww; // positive: parent sends this to local
			else {
				transData[i]=-ww; // negative: local sends this to parent
				packData.setAim(ww,-1.0);
			}
		}
		
		setPoisonEdges();
  	  	return myPack;
	}
	
	/**
	 * Intend to delete this branch point, so reestablish parent packing.
	 */
	public void delete() {
		
		// reset parent aims to default
		for (int i=1;i<=matchCount;i++) { // bdry are +, rest minus
			int ww=vertexMap.findW(i);
			if (packData.kData[ww].bdryFlag!=0)
				packData.setAim(ww,-1.0);
			else {
				packData.setAim(ww,2.0*Math.PI);
			}
		}
		
		// reset face's inv distances to tangency
		int v1=Math.abs(transData[1]);
		int v2=Math.abs(transData[2]);
		int v3=Math.abs(transData[3]);
		if (packData.overlapStatus) {
			packData.set_single_invDist(v1, v2,1.0);
			packData.set_single_invDist(v2, v3,1.0);
			packData.set_single_invDist(v3, v1,1.0);
		}
		
		// remove poison edges
		packData.poisonEdges.removeUnordered(parentPoison);
	}
	
	/**
	 * The computation is a standard riffle ('oldReliable') on the 
	 * augmented packing --- but it may involve some deep overlaps.
	 * Note: change some parent radii.
	 * @param cycles int, max iterations (if <0, set default)	
	 * @return @see UtilPacket: 'value' is l^2 norm of angle sum error.
	 */
	public UtilPacket riffleMe(int cycles) {
		// get parent radii
		for (int i=1;i<=matchCount;i++) {
			if (transData[i]>0)
				myPackData.setRadius(i,packData.getRadius(transData[i]));
		}
		if (cycles<0)
			cycles = 5; // subject to trial and error
		UtilPacket uP=new UtilPacket();
		uP.rtnFlag=-1;
		try {
			uP.rtnFlag=myPackData.repack_call(cycles,true,false); // use 'oldReliable'
			uP.value=myPackData.angSumError();
		} catch (Exception ex) {
			throw new ParserException("repack layout failed");
		}
		
		// transfer radii to parent
		for (int i=1;i<=matchCount;i++) {
			if (transData[i]<0)
				packData.setRadius(-transData[i],myPackData.getRadius(i));
		}
		return uP;
	}
	
	/**
	 * Layout procedure involves laying out the 'borderLink' faces only; this
	 * places all the circles (except 'newBrSpot', which is often radius
	 * zero in any case). Optional normalization places the common point
	 * where 1, 2, 3, and the three chaperone circles all meet. (In other words,
	 * 'newBrSpot' is at the origin.) Also rotate so 1 is on the positive y-axis. 
	 * 
	 * Update 'myHolonomy'.
	 * @param norm boolean, if true, freshly layout in normalized position
	 * @return double, error in myHolonomy
	 */
	public double layout(boolean norm) {

		int F=myPackData.firstFace;
		int V=myPackData.faces[F].vert[myPackData.faces[F].indexFlag];
		int W=myPackData.faces[F].vert[(myPackData.faces[F].indexFlag+1)%3];
		
		if (norm) {
			// place first face to get initial position
			if (myPackData.place_face(F,myPackData.faces[F].indexFlag)==0)
				throw new DataException("failed to locate first local face");
			Complex []firstF=new Complex[2];
			firstF[0]=myPackData.getCenter(V);
			firstF[1]=myPackData.getCenter(W);
		
			// now use 'drawingTree' computed in 'createMyPack' to layout
			myPackData.layoutTree(null,myPackData.drawingTree,null,null,true,false,1.0);

			// final location of F (only different if 'borderLink' is closed)
			Complex []lastF=new Complex[2];
			lastF[0]=myPackData.getCenter(V);
			lastF[1]=myPackData.getCenter(W);

			// update myHolonomy
			if (myPackData.hes<0) // hyp
				myHolonomy=Mobius.auto_abAB(firstF[0],firstF[1],lastF[0],lastF[1]);
			else // eucl
				myHolonomy=Mobius.affine_mob(firstF[0],firstF[1],lastF[0],lastF[1]);
		}
		
		// for layout itself, use pruned drawingTree;  
		GraphLink prunedTree=DualGraph.pruneDrawSpan(myPackData,myPackData.drawingTree);
//		DualGraph.printGraph(myPackData.drawingTree);DualGraph.printGraph(prunedTree);
		myPackData.layoutTree(null,prunedTree,null,null,true,false,1.0);
		
		// normalize: 1 center is at rad[1]*i on imaginary axis; center of chap2
		//    has argument -pi/2 + overlap[2], modulus radius of newBrSpot-2.
		if (norm) {
			double e1=myPackData.getRadius(1); // radius for 1
			double em=myPackData.getRadius(chap[2]); // radius for newBrSpot-2
			
			// hyperbolic? have to adjust to euclidean data
			if (myPackData.hes<0) {
				// get euclidean radii of 1 and v=chap[2] (as though they go through the origin);
				double h1=HyperbolicMath.x_to_h_rad(e1);
				double exph=Math.exp(h1);
				e1=(exph-1.0)/(exph+1.0);
				double hm=HyperbolicMath.x_to_h_rad(em);
				exph=Math.exp(hm);
				em=(exph-1.0)/(exph+1.0);
			}
			
			// new eucl centers for 1 and chap2, respectively
			Complex A=new Complex(0.0,e1); 
			Complex B=new Complex(Math.log(em),-Math.PI/2.0+Math.acos(cos_overs[2])).exp();

			Mobius mob=new Mobius();
			if (myPackData.hes<0) {
				CircleSimple sc=HyperbolicMath.e_to_h_data(A,e1);
				A=sc.center;
				sc=HyperbolicMath.e_to_h_data(B,em);
				B=sc.center;
				mob=Mobius.auto_abAB(myPackData.getCenter(1),
						myPackData.getCenter(chap[2]),A,B);
			}
			else
				mob=Mobius.affine_mob(myPackData.getCenter(1),
						myPackData.getCenter(chap[2]),A,B);
			for (int v=1;v<=myPackData.nodeCount;v++) 
				myPackData.setCenter(v,mob.apply(myPackData.getCenter(v)));
			
			// put barycenter chaperone at the origin
//			myPackData.rData[newBrSpot].center=new Complex(0.0);
		}
			
		return Mobius.frobeniusNorm(myHolonomy);
	}
		
	/**
	 * Assume radii have been updated, what is the angle sum error?
	 * @return double, l^2 angle sum error.
	 */
	public double currentError() {
		myPackData.fillcurves();
		return myPackData.angSumError();
	}
		
	/**
	 * Singular branch point parameters are the overlap angles associated
	 * with the three edges of 'singFace'. This is similar to 'fractured'
	 * branch points, but since the overlap angles must sum to Pi, we 
	 * specify overlaps for 1,2 and for 2,3, then compute that for 3,1. 
	 * Vertices 1,2,3 are packData.face[myIndx].vert[0],[1],[2], resp.
	 * so overlap [j] is for circles forming edge opposite vert [j+1]. 
	 * @param Vector<Vector<String>> flagSegs, overlaps
	 * @return int, 3 for success
	 */
	 public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		boolean gotovers=false;
		double []ovlp=new double[2];
		
		// get flags: -a aim, -o o1 o2
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				if (!StringUtil.isFlag(items.get(0)))
					throw new ParserException("usage: -a aim -o o1 o2");
				String str=items.remove(0);
				switch (str.charAt(1)) {
				case 'a': // new aim
				{
					myAim=Double.parseDouble(items.get(0));
					count++;
					break;
				}
				case 'o': // overlaps
				{
					for (int i=0;i<2;i++) {
						ovlp[i]=Double.parseDouble(items.remove(0));
						if (ovlp[i]<0 || ovlp[i]>1.0)
							throw new ParserException("overlap not in [0,1]");
						count++;
					}
					if ((ovlp[0]+ovlp[1])>1.0000001)
						throw new ParserException("sum of o1 o2 overlaps not in [0,1]");
					gotovers=true;
					break;
				}
				} // end of switch
			} catch(Exception ex) {
				throw new ParserException("usage: -a aim, -o o1 o2");
			}
		} // end of while
		
		// set overlaps
		myPackData.setAim(myPackData.nodeCount,myAim);
		if (gotovers)
			count+=resetOverlaps(ovlp[0],ovlp[1]);
		
		return count;
	 }
	
	 /**
	  * For an already established 'singular' branch point, this just
	  * adjusts the overlap angles. 'o1' and 'o2' in [0,1] (they are
	  * multiplied by Pi here) 
	  * @param o1 double
	  * @param o2 double
	  * @return 1
	  */
	 public int resetOverlaps(double o1,double o2) {
		 double o3=1.0-(o1+o2);
		if (o1<0.0 || o1>1.0 || o2<0.0 || o2>1.0 || o3<0.0)
			throw new DataException("'singular' usage: 2 overlaps in [0,1], sum <= 1");

		// shift numbering to match 'vert' order in parent face
		int pv=packData.faces[myIndex].vert[0];
		if (vertexMap.findW(2)==pv) {
			double hold=o3;
			o3=o2;
			o2=o1;
			o1=hold;
		}
		else if (vertexMap.findW(3)==pv) {
			double hold=03;
			o3=o1;
			o1=o2;
			o2=hold;
		}
		
		cos_overs[0]=Math.cos(o1*Math.PI);
		cos_overs[1]=Math.cos(o2*Math.PI);
		cos_overs[2]=Math.cos(o3*Math.PI);
		
		// To get overlap ov on edge (2,3), we have vertex 1 overlap 
		//    chap2 and chap3 (i.e. the neighboring chaperone circles) by ov.
		myPackData.alloc_overlaps();
		myPackData.set_single_invDist(1,chap[2],cos_overs[0]);
		myPackData.set_single_invDist(1,chap[3],cos_overs[0]);
		myPackData.set_single_invDist(2,chap[1],cos_overs[1]);
		myPackData.set_single_invDist(2,chap[3],cos_overs[1]);
		myPackData.set_single_invDist(3,chap[1],cos_overs[2]);
		myPackData.set_single_invDist(3,chap[2],cos_overs[2]);
		
		// Adjust parent info: parent will see circles of singFace as overlapping,
		//   which affects its own angle sums in its repacking process.
		int v1=Math.abs(transData[1]);
		int v2=Math.abs(transData[2]);
		int v3=Math.abs(transData[3]);
		if (!packData.overlapStatus)
			packData.alloc_overlaps();
		double invdist=Math.cos(Math.PI-Math.acos(cos_overs[0])-Math.acos(cos_overs[1]));
		packData.set_single_invDist(v1, v2, invdist);
		invdist=Math.cos(Math.PI-Math.acos(cos_overs[1])-Math.acos(cos_overs[2]));
		packData.set_single_invDist(v2, v3, invdist);
		invdist=Math.cos(Math.PI-Math.acos(cos_overs[2])-Math.acos(cos_overs[0]));
		packData.set_single_invDist(v3, v1, invdist);
		
		return 1;
	 }
	 
	 /**
	  * See if there are special actions for display on screen of parent packing.
	  * If so, do them, remove them, and pass the rest to 'super'. May flush some
	  * commands designed for other types of branch points.
	  * 
	  * @param flagSegs flag sequences
	  * @return int count of display actions
	  */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		Vector<Vector<String>> newFlagSegs=new Vector<Vector<String>>(1);
		Vector<String> items=new Vector<String>(2);
		int n=0;
		for (int j=0;j<flagSegs.size();j++) {
			String dispCode=null;
			items=flagSegs.get(j);
			String str=items.get(0);
			boolean hit=false;
			
			// get info to reconstruct new command: 
			// e.g. -h1fc20 converts to -cfc20 <chap[1]>
			String suff="";  // save suffex of original, e.g. 'fc5t4' 
			String target=null; // build target list
			Character c=null;
			if (str.length()>2)   // possible number here
				c=Character.valueOf(str.charAt(2));

			// look for objects to parse here
			char c2;
			if (str.startsWith("-s") || str.startsWith("-j")) { // flush these for singular points
				hit=false;
			}
			else if (str.length()>1 && ((c2=str.charAt(1))=='h' || c2=='y')) {
				
				if (str.startsWith("-h")) { // chaperones
					if (c!=null && ((char)c=='1' || (char)c=='2' || (char)c=='3')) {
						if ((char)c=='1')
							target=new String(" "+chap[1]+" ");
						else if ((char)c=='2')
							target=new String(" "+chap[2]+" ");
						else
							target=new String(" "+chap[3]+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else { // default to all three
						target=new String(" "+chap[1]+" "+chap[2]+" "+chap[3]+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
					dispCode="-c";
					hit=true;
				}
				else if (str.startsWith("-y")) { // newBrSpot
					target=new String(" "+newBrSpot+" ");
					dispCode="-c";
					hit=true;
				}

			} // h/y circle case
			
			// In singular case, edges between the three circles (r,g,b) go through
			//   the branch value. This option draws those edges.
			else if (str.startsWith("-e")) {
				dispCode="-e";
				hit=true;
				target=new String(" "+newBrSpot+" 1  "+newBrSpot+" 2  "+newBrSpot+" 3");
			}
			
			// else if -w or -wr flag, have parent run it, or store it to pass on to parent
			else if (items.size()>0 && items.get(0).startsWith("-w")) {
					String fs=items.remove(0);
					CommandStrParser.jexecute(packData,"disp "+fs);
					n++;
					if (items.size()>0) // save anything after the -w flag
						newFlagSegs.add(items);
			}
			
			// just past along other items
			else { 
				newFlagSegs.add(items);
				hit=false;
			}
			
			// build display string and display on packData's screen
			if (hit) {
				StringBuilder pulloff=new StringBuilder();
				pulloff.append(dispCode);
				pulloff.append(suff);
				pulloff.append(target);
			
			    // I guess 'attachFace' isn't set for chaperone branch points
//				else if (str.contains("-a")) { // attachFace (parent's data)
//					n+=DisplayParser.dispParse(packData,StringUtil.flagSeg("-ff "+this.attachFace));
//					flagSegs.remove((Object)items);
//				}
			
				// found something? display on parent canvas
				if (pulloff.length()>0) {
					n=DisplayParser.dispParse(myPackData,packData.cpScreen,StringUtil.flagSeg(pulloff.toString()));
				}
			}
		} // end of loop through items

		n+=DisplayParser.dispParse(myPackData,packData.cpScreen,newFlagSegs);
		
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
		return n;
	}
	
	/**
	 * 
	 * @return String
	 */
	public String getParameters() {
		return new String("Singular branch face, aim "+
				myAim/Math.PI+"*Pi on face "+myIndex);
	}
	
	public String reportExistence() {
		return new String("Started 'singular' branch point; face = "+myIndex);
	}
	
	public String reportStatus() {
		return new String("'singular', ID "+branchID+": face="+myIndex+
				", aim="+myAim+", holonomy err="+super.myHolonomyError());
	}

	/**
	 * Make edges in the parent from the 'singFace' vertices to the connection vertices 
	 * poison (because of overlaps, we repack/layout the connection circles here), as 
	 * well as the edges of 'singFace'
	 * @return size of 'parentPoison'
	 */
	public int setPoisonEdges() {
		EdgeLink elink=new EdgeLink(packData);
		elink.add(new EdgeSimple(vertexMap.findW(1),vertexMap.findW(connect[3])));
		elink.add(new EdgeSimple(vertexMap.findW(connect[3]),vertexMap.findW(2)));
		elink.add(new EdgeSimple(vertexMap.findW(2),vertexMap.findW(connect[1])));
		elink.add(new EdgeSimple(vertexMap.findW(connect[1]),vertexMap.findW(3)));
		elink.add(new EdgeSimple(vertexMap.findW(3),vertexMap.findW(connect[2])));
		elink.add(new EdgeSimple(vertexMap.findW(connect[2]),vertexMap.findW(1)));
		// also edges of 'singFace' itself
		elink.add(new EdgeSimple(packData.faces[myIndex].vert[0],packData.faces[myIndex].vert[1]));
		elink.add(new EdgeSimple(packData.faces[myIndex].vert[1],packData.faces[myIndex].vert[2]));
		elink.add(new EdgeSimple(packData.faces[myIndex].vert[2],packData.faces[myIndex].vert[0]));
		
		parentPoison=elink;
		return elink.size();
	}

	/**
	 * The parent should place all except the chaperones. For each
	 * chaperone use fancy with faces shared with connection vertex.
	 * Note: 'layout' places local circles irrespective of parent. 
	 */
	public int placeMyCircles() {
		int count=0;
		
		// get centers from parent
		for (int i=1;i<=matchCount;i++) {
			myPackData.setCenter(i,new Complex(packData.getCenter(Math.abs(transData[i]))));
			count++;
		}
		
		layout(false);
		return count;
	}
	

}
