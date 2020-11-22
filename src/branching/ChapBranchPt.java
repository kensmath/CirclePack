package branching;

import input.CommandStrParser;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import komplex.CookieMonster;
import komplex.DualGraph;
import komplex.EdgeSimple;
import komplex.KData;
import komplex.RedEdge;
import komplex.RedList;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import packing.RData;
import util.StringUtil;
import util.UtilPacket;
import allMains.CPBase;
import allMains.CirclePack;
import canvasses.DisplayParser;
import circlePack.PackControl;

import complex.Complex;

import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import ftnTheory.GenBranching;
import geometry.HyperbolicMath;

/**
 * A @see GenBranchPt of the "chaparone" type is associated with a 
 * circle (versus an interstice). This idea originated with Ed Crane,
 * and is preferable to the related "shifted" type. Add "sister" and chaparone 
 * circles to the branch point combinatorics. Parameters tell where 
 * chaperones are added and each chaparone has an overlap angle and its 
 * supplementary angle as parameters.
 * 
 * The circle is represented as two 'sister' circles. Going around the petals
 * of the original vertex, one encounters "prejump/jump" pairs of contiguous 
 * petals. The "jump" petal is in the flower of one sister, while the 
 * preceding "prejump" petal is in the flower of the other. Jumps are 
 * determined by two parameters on creation and these are subject to change.
 * The parameters first determine locations of two chaperones in the 
 * combinatorics, then provide overlap angles. Each jump gets an overlap 
 * angle a between its chaperone and the prejump petal; the supplementary angle
 * sa is the overlap between the chaperone and the jump petal. When a is
 * close to 0, jump circle remains almost in contact with original sister, and
 * as a grows, it jumps more firmly onto the next sister. 
 * 
 * Thus the circles from jump1 to prejump2 are on sister2 only, from jump2 to 
 * prejump1 are on sister1 only. In particular, jumps and prejumps must be
 * distinct. 
 * 
 * There is a third chaperone "fall guy" who carries this branch point's
 * "aim"; this is normally 4*Pi, in which case the fall guy's radius will 
 * drop to zero. However, to see the underlying structure, one can set the aim
 * to 2*Pi or to follow computations you can let it approach 4*Pi.
 * 
 * Changes in the local structure require changes to 'packData':
 *   * 'packData.poisonEdges' are updated via EdgeLink 'parentPoison'.
 *   * Aims of 'myIndex' and its petals are set negative (so parent doesn't repack)
 *   * The radius of v is sent to parent after repack if transData[v]<0.
 *    
 * TODO: may eventually want to convert between 'shifted' and 'chaperone' types.

 * @author kens
 */

public class ChapBranchPt extends GenBranchPt {
	
	FaceLink borderLink;  		  // chain used for layout (local indexing)
	EdgeLink radToParent;		  // for some interiors, need to send radii to parent: list (loc,par)
	EdgeLink radFromParent;		  // for bdry, need to get radii from parent: list (loc,par)
	
	// adjustment info, parameters
	PackData origChild;			  // original as cut from parent (same as "shifted")
	int petalCount;				  // number of petals in original flower
	int sister2;				  // vert index of sister2 (sister 1 is vertex 1)
	int newBrSpot;				  // circle at branch value, radius essentially 0, aim=myAim
	
	// chap, jumpIndex, cos_overs indices start with 1 (not 0)
	int []chap;					  // chaperone chap[1] before jump1, chap[2] after jump2
	int []jumpCircle;			  // local index of jump circle (in flower of next sister)
	int []preJump;				  // local index of petal preceding the jump (in flower of previous sister) 
	int []jumpIndx;			      // indices of jump petals in myIndex in parent packing --- chap[j] is petal 
								  //   jumpIndx[j], j=1,2, to next sister. (With aim 4Pi, have 
								  //   two jumps: onto sister2, then back to sister1.			 
	double []cos_overs; 		  // overlap angles associated with chaperone circles:
	
	// may be used for conversion to/from 'shifted' type
	double petalPhase;			  // The phase angle 't' where singPetal is tangent
	double sisterRatio;			  // Ratio sister2/sister1, normally in (0,1].
	static double LAYOUT_THRESHOLD = .00000001;

	// Constructors
	public ChapBranchPt(PackData p,int bID,double aim,int v,double psdo1,double psdo2) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.CHAPERONE;
		myIndex=v;
		int numSV=p.kData[myIndex].num;
		
		if (packData.kData[myIndex].bdryFlag!=0 || packData.kData[myIndex].num<5)
			throw new CombException("singular vert must be interior, degree at least 5");

		// set jump indices: note, these are local petal indices for flower of 'myIndex' 
		//     but will be same petal indices as corresponding circles of the parent's flower.
		jumpIndx=new int[3];
		jumpIndx[1]=((int)Math.floor(psdo1)+1)%numSV;
		jumpIndx[2]=((int)Math.floor(psdo2)+1)%numSV;
		jumpCircle=new int[3];
		jumpCircle[1]=p.kData[myIndex].flower[jumpIndx[1]];
		jumpCircle[2]=p.kData[myIndex].flower[jumpIndx[2]];
		preJump=new int[3];
		preJump[1]=p.kData[myIndex].flower[(jumpIndx[1]-1+numSV)%numSV];
		preJump[2]=p.kData[myIndex].flower[(jumpIndx[2]-1+numSV)%numSV];

		// set overlaps
		double o1=1-(psdo1-Math.floor(psdo1));
		double o2=1-(psdo2-Math.floor(psdo2));
		cos_overs=new double[3];
		cos_overs[1]=Math.cos(o1*Math.PI);
		cos_overs[2]=Math.cos(o2*Math.PI);

		// debug help
		System.out.println("chap pseudo try: a = "+aim/Math.PI+"; v = "+v+
				"; jumps "+jumpCircle[1]+" "+jumpCircle[2]+"; "+
				"overlaps "+o1/Math.PI+" "+o2/Math.PI);
				
		// this calls createMyPack (which saves 'origChild')
		super.initLocalPacking(); 
	}
	
	// Constructor
	public ChapBranchPt(PackData p,int bID,double aim,int v,int w1,int w2,double o1,double o2) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.CHAPERONE;
		myIndex=v;
		
		if (packData.kData[myIndex].bdryFlag!=0 || packData.kData[myIndex].num<5)
			throw new CombException("chaperone vert must be interior, degree at least 5");

		// set jump indices: note, these are local flower indices for 'myIndex' but
		//     will be same index as corresponding circles of the parent's flower.
		jumpIndx=new int[3];
		jumpIndx[1]=p.nghb(myIndex,w1);
		jumpIndx[2]=p.nghb(myIndex,w2);
		int numSV=p.kData[myIndex].num;
		jumpCircle=new int[3];
		jumpCircle[1]=p.kData[myIndex].flower[jumpIndx[1]];
		jumpCircle[2]=p.kData[myIndex].flower[jumpIndx[2]];
		
		if ((jumpIndx[2]-jumpIndx[1]+numSV)%numSV<2 || (jumpIndx[1]-jumpIndx[2]+numSV)%numSV<2) {
			CirclePack.cpb.errMsg("char jump vertices "+jumpCircle[1]+", "+jumpCircle[2]+
					" are too close; must be separated by at least one petal");
			System.out.println("chaperone jump vertices are too close");
		}
			
		preJump=new int[3];
		preJump[1]=p.kData[myIndex].flower[(jumpIndx[1]-1+numSV)%numSV];
		preJump[2]=p.kData[myIndex].flower[(jumpIndx[2]-1+numSV)%numSV];

		// set overlaps
		cos_overs=new double[3];
		cos_overs[1]=Math.cos(o1*Math.PI);
		cos_overs[2]=Math.cos(o2*Math.PI);

		// debug help
		System.out.println("chap attempt: a = "+aim/Math.PI+"; v = "+v+"; jumps "+w1+" "+w2+"; "+
				"overlaps/Pi "+o1+" "+o2);
				
		// this calls createMyPack (which saves 'origChild')
		super.initLocalPacking(); 
	}
	
	/**
	 * Create 'origChild' packing via a cookie method as in "shifted" type. 
	 * However, 'myPackData' will be a modified version with chaperone circles
	 * whose combinatorial locations are determined by the user as part of 
	 * parameter setting.
	 *  
	 * The 'vertexMap' should be fine, but must update 'borderLink' for any
	 * combinatorial changes.
	 * @return PackData
	 */
	public PackData createMyPack() {
		PackData myPack=null;
		
		// identify island containing branch circle
		NodeLink petals=new NodeLink(packData);
		for (int j=0;j<packData.kData[myIndex].num;j++)
			petals.add(packData.kData[myIndex].flower[j]);
		bdryLink=PackData.islandSurround(packData,petals);
		if (bdryLink==null)
			throw new CombException("Failed to build surround faces");

		// cookie
		NodeLink seedlist=new NodeLink(packData,myIndex);
		packData.gen_mark(seedlist,-1,true); // mark generations from myVert
		
		// Note: need to save/restore parent 'PoisonVerts'
		NodeLink holdPoison=null;
		if (packData.poisonVerts!=null && packData.poisonVerts.size()>0)
			holdPoison=packData.poisonVerts.makeCopy();
		
		// now set poisons
		packData.poisonVerts=new NodeLink(packData,"{c:m.gt.2}");
		packData.poisonEdges=new EdgeLink(packData,"Ivw P");

  	  	CookieMonster cM=null; // (note: may get all of parent)
  	  	boolean didcookie=false;
  	  	try {
  	  		cM=new CookieMonster(packData,new String("-v "+myIndex));
  	  		int outcome=cM.goCookie();
  	  		if (outcome<0)
  	  			throw new CombException();
  	  		if (outcome>0) {
  	  			myPack=cM.getPackData();
  	  	  		myPack.cpScreen=null;
  	  	  		myPack.hes=packData.hes;  
  	  	  		vertexMap=myPack.vertexMap.makeCopy();
  	  	  		didcookie=true;
  	  		}
  	  	} catch (Exception ex) {
  	  	}
  	  		
  	  	if (!didcookie) { // no vertices cut out: copy packData
  	  		myPack=packData.copyPackTo();
  	  		myPack.cpScreen=null;
  	  		vertexMap=myPack.vertexMap=new VertexMap();
  	  		for (int v=1;v<=myPack.nodeCount;v++)
  	  			vertexMap.add(new EdgeSimple(v,v));
  	  	}
  	  		
  	  	// if spherical, best we can do for now is eucl
  	  	if (myPack.hes>0) 
  	  		myPack.hes=0;
  	  		
  	  	// swap nodes: 'myIndex' becomes 1, its first petal becomes 2
  	  	int firstpetal=myPack.kData[myIndex].flower[0];
  	  	myPack.swap_nodes(vertexMap.findV(myIndex), 1);
  	  	myPack.swap_nodes(vertexMap.findV(firstpetal), 2);
  	  	vertexMap=myPack.vertexMap.makeCopy(); // reset to get the swaps
  	  	myPack.alpha=1;
  	  	myPack.gamma=2;
  	  	myPack.setCombinatorics();
  	  	boolean debug=false;
  	  	if (debug)
  	  		DebugHelp.debugPackWrite(myPack,"Shifted.p");
  	  	packData.poisonVerts=holdPoison;
  	  	
		// need to convert 'bdryLink' to local face numbers
		borderLink=new FaceLink(myPack);
		Iterator<Integer> dL=bdryLink.iterator();
		while (dL.hasNext()) {
			int F=dL.next();
			borderLink.add(myPack.what_face(vertexMap.findV(packData.faces[F].vert[0]),
					vertexMap.findV(packData.faces[F].vert[1]),
					vertexMap.findV(packData.faces[F].vert[2])));
		}
		myPack.set_aim_default();
		myPack.alloc_overlaps();
		
		matchCount=myPack.nodeCount;

		// set up transData; this shouldn't change with 'setParameters'
		transData=new int[matchCount+1];
		for (int i=1;i<=matchCount;i++) { // bdry are +, rest minus
			int ww=vertexMap.findW(i);
			if (myPack.kData[i].bdryFlag!=0) 
				transData[i]=ww; // positive: parent sends info on this vert to local
			else {
				transData[i]=-ww; // negative: local sends info on this vert to parent
				packData.rData[ww].aim=-1.0;
			}
		}
		
		// 'myIndex' and its petals are packed locally; set aim < 0 in parent
		packData.rData[myIndex].aim=-1.0;
		for (int j=0;j<packData.kData[myIndex].num+packData.kData[myIndex].bdryFlag;j++)
			packData.rData[packData.kData[myIndex].flower[j]].aim=-1.0;

		// store as permanent 'origChild' 
		origChild=myPack;

		setPoisonEdges();
  	  	return modifyMyPack();
	}
	
	public void delete() {
		// reset aims of parent
		if (packData.kData[myIndex].bdryFlag==0)
			packData.rData[myIndex].aim=2.0*Math.PI;
		for (int j=0;j<packData.kData[myIndex].num+packData.kData[myIndex].bdryFlag;j++) {
			int k=packData.kData[myIndex].flower[j];
			if (packData.kData[k].bdryFlag==0)
				packData.rData[k].aim=2.0*Math.PI;
		}
		
		// remove poison edges associated with this branch point
		if (parentPoison!=null)
			packData.poisonEdges.removeUnordered(parentPoison);
	}
	
	/**
	 * This is an ordinary repacking, though the complex has
	 * extra circles and due to deep overlaps, we use 'oldReliable'.
	 * @param cycles int, max iterations (if <0, set default)
	 * @return @see UtilPacket: 'rtnFlag' -1 on error, 'value' l^2 norm 
	 * of angle sum error.
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
			if (uP.rtnFlag>=0)
				uP.value = myPackData.angSumError();
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
	 * Normalized position has sister 1 centered at the origin and
	 * sister 2 centered on the positive real axis, hence usually
	 * internally tangent at z=1. (Note that it may be that sister2
	 * is larger than sister1.) Update 'myHolonomy'.
	 * @param norm <code>boolean</code>: true, move to normalized position
	 * @return <ocde>double</code>, error in myHolonomy
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
		
			// compute holonomy
			// use 'drawingTree' (computed in 'modifyMyPack') for temp layout
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
		
		// for layout itself, use pruned drawingTree
		GraphLink prunedTree=DualGraph.pruneDrawSpan(myPackData,myPackData.drawingTree);
		myPackData.layoutTree(null,prunedTree,null,null,true,false,1.0);
		
		// normalize: 1 center is at rad[1]*i on imaginary axis; center of chap[2]
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
			
			// new eucl centers for 1 and chap[2], respectively
			// TODO: Problem -- layout was tailored for radius of newBrSpot being zero.
//			Complex A=new Complex(0.0,e1); 
//			Complex B=new Complex(Math.log(em),-Math.PI/2.0+Math.acos(cos_overs[2])).exp();

//			Mobius mob=new Mobius();
//			if (myPackData.hes<0) {
//				CircleSiimple sc=HyperbolicMath.e_to_h_data(A,e1);
//				A=sc.center;
//				sc=HyperbolicMath.e_to_h_data(B,em);
//				B=sc.center;
//				mob=Mobius.auto_abAB(myPackData.rData[1].center,
//						myPackData.rData[chap[2]].center,A,B);
//			}
//			else
//				mob=Mobius.affine_mob(myPackData.rData[1].center,
//						myPackData.rData[chap[2]].center,A,B);
			
//			for (int v=1;v<=myPackData.nodeCount;v++) 
//				myPackData.rData[v].center=mob.apply(myPackData.rData[v].center);
			
			// put barycenter chaperone at the origin
			// myPackData.rData[newBrSpot].center=new Complex(0.0);
		}
			
/*		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		boolean errflag=false; // only use 'well-plotted' in layout
		boolean dflag=false;   // debugging help 
		try {
			myPackData.fillcurves();
			myPackData.comp_pack_centers(errflag,dflag,opt,LAYOUT_THRESHOLD);
		} catch(Exception ex) {
			throw new CombException("shrpPoint layout: "+ex.toString());
		}

		// update myHolonomy
		myHolonomy=myHolonomy();
		
		// translate 1 to origin, rotate to get 2 on positive x-axis
		if (norm) {
			Complex cent1=myPackData.rData[1].center;
			for (int v=0;v<=myPackData.nodeCount;v++) {
				myPackData.rData[v].center=myPackData.rData[v].center.minus(cent1);
			}
			double theta=(-1.0)*myPackData.rData[2].center.arg();
			myPackData.rotate(theta);
		}
*/		
		return Mobius.frobeniusNorm(myHolonomy);
	}
	
	/**
	 * Parent lays out all original circles (with radii computed here and
	 * respecting poison edges we set); local layout using parent locations
	 * places 'sister2', 'chap[1]', 'chap[2]', and 'newBrSpot'.
	 * @return 0 on error
	 */
	public int placeMyCircles() {
		int count=0;
		// get centers from parent
		for (int v=1;v<=matchCount;v++) {
			myPackData.setCenter(v,packData.getCenter(Math.abs(transData[v])));
			myPackData.kData[v].plotFlag=1;
			count++;
		}
		
		layout(false);
		return count;
	}
	
	/**
	 * Compute the holonomy Mobius transform by laying out 'borderLink'
	 * using current local radii.
	 * @return @see Mobius, 
	 */
	public Mobius myHolonomy() {
		int F=borderLink.get(0);

		// initial location of F
		Complex []firstF=new Complex[3];
		firstF[0]=myPackData.getCenter(myPackData.faces[F].vert[0]);
		firstF[1]=myPackData.getCenter(myPackData.faces[F].vert[1]);
		firstF[2]=myPackData.getCenter(myPackData.faces[F].vert[2]);
		
		// if borderLink closed, compute holonomy
		myPackData.recomp_facelist(borderLink);
		
		// final location of F (only different if 'bdryLink' is closed)
		Complex []lastF=new Complex[3];
		lastF[0]=myPackData.getCenter(myPackData.faces[F].vert[0]);
		lastF[1]=myPackData.getCenter(myPackData.faces[F].vert[1]);
		lastF[2]=myPackData.getCenter(myPackData.faces[F].vert[2]);

		// update myHolonomy
		return Mobius.mob_xyzXYZ(firstF[0],firstF[1],firstF[2],lastF[0],lastF[1],lastF[2],0,0);
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
	 * reset parameters using angles o1, o2. These are
	 * @param o1 angle
	 * @param o2 angle
	 * @return 1 on success
	 */
	public int resetParameters(double o1,double o2){

		jumpIndx=new int[3];
		jumpIndx[1]=(int)Math.floor(o1/Math.PI);
		jumpIndx[2]=(int)Math.floor(o2/Math.PI);
		double ov1=o1-Math.floor(o1)/Math.PI; // remainder/Pi
		double ov2=o1-Math.floor(o2)/Math.PI; // remainder/Pi
		
		jumpCircle=new int[3];
		jumpCircle[1]=packData.kData[myIndex].flower[jumpIndx[1]];
		jumpCircle[2]=packData.kData[myIndex].flower[jumpIndx[2]];
		int numSV=packData.kData[myIndex].num;
		preJump=new int[3];
		preJump[1]=packData.kData[myIndex].flower[(jumpIndx[1]-1+numSV)%numSV];
		preJump[2]=packData.kData[myIndex].flower[(jumpIndx[2]-1+numSV)%numSV];

		// remove old poisons
		if (parentPoison!=null)
			packData.poisonEdges.removeUnordered(parentPoison);
		setPoisonEdges();
		myPackData=modifyMyPack();
		if (myPackData==null)
			throw new CombException("Failed to create packing for general branch point, type "+myType);
		myPackData.fillcurves();
		
		// set overlaps
		return resetOverlaps(ov1,ov2);
	}
	
	/**
	 * Chaperone branch point parameters are jump circles and an overlap 
	 * angle for each (to be multiplied by PI here). We read "v0 v1 a0 a1", 
	 * where v0 v1 are petal vertices in parent --- store in jumpIndx[]
	 * as local flower indices --- and a0 and a1 are multiplied by Pi; 
	 * their cosines are stored in cos_overs[] (negative is cosine of 
	 * supplementary angle).
	 * Then call 'modifyMyPack' to actually create the new 'myPackData'. 
	 * @param flagSegs Vector<Vector<String>>, "v0 v1 a0 a1"
	 * @return int count; 0 on error, negative if new 'layoutTree' is required in parent
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("usage: -a aim -j j1 j2 -o o1 o2");
		int count=0;
		boolean gotjumps=false;
		boolean gotovers=false;
		double []ovlp=new double[2];
		
		// parse the parameter info: -a aim, -j j1 j2, -o o1 o2
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				if (!StringUtil.isFlag(items.get(0)))
					throw new ParserException("usage: -a aim -j j1 j2 -o o1 o2");
				String str=items.remove(0);
				switch (str.charAt(1)) {
				case 'a': // new aim
				{
					myAim=Double.parseDouble(items.get(0))*Math.PI;
					myPackData.rData[newBrSpot].aim=myAim;
					myPackData.setRadius(newBrSpot,0.5); // kick-start repacking
					count++;
					break;
				}
				case 'j': // jump petals (as vertex indices from parent)
				{
					jumpIndx=new int[3]; // jumpIndx[0] empty
					int numV=origChild.kData[1].num;
					// get local vertices for jumps, store
					int locV=vertexMap.findV(Integer.parseInt(items.get(0)));
					jumpIndx[1]=origChild.nghb(1,locV);
					locV=vertexMap.findV(Integer.parseInt(items.get(1)));
					jumpIndx[2]=origChild.nghb(1,locV);
					int jdist=(jumpIndx[2]-jumpIndx[1]+numV)%numV;
					if (jumpIndx[1]<0 || jumpIndx[2]<0 || jdist==0)
						throw new ParserException("usage: must give two (distinct) petal vertices");
					if (jumpIndx[1]==jumpIndx[2] || jdist <2 || jdist==(numV-1))
						throw new ParserException("error with given petals: too close");
					count += 2;
					gotjumps=true;
					break;
				}
				case 'o': // overlaps
				{
					for (int i=0;i<2;i++) {
						ovlp[i]=Double.parseDouble(items.remove(0));
						if (ovlp[i]<0 || ovlp[i]>1.0) {
							throw new ParserException("overlap not in [0,1]");
						}
						cos_overs[i+1]=Math.cos(ovlp[i]*Math.PI);
						count++;
					}
					count++;
					gotovers=true;
					break;
				}
				} // end of switch
			
				// TODO: may want to accommodate more jumps/overlaps in future,
				//       depending, e.g., on 'myAim'.
			} catch(Exception ex) {
				throw new ParserException(ex.getMessage());
			}
		}

		if (gotjumps) {
			// remove old poisons
			if (parentPoison!=null)
				packData.poisonEdges.removeUnordered(parentPoison);
			setPoisonEdges();
			myPackData=modifyMyPack();
			if (myPackData==null)
				throw new CombException("Failed to create packing for general branch point, type "+myType);
			myPackData.fillcurves();
		}
		if (gotovers) {

			// reset overlaps
			return resetOverlaps(ovlp[0],ovlp[1]);
		}
		return count;
	}
	
	/**
	 * For an already established 'chaperone' branch point, this just
	 * adjusts the overlap angles at jump1 and jump2, respectively. For
	 * each jump, get value in [0,1] (to be multiplied by PI) for overlap
	 * angle; in both cases, oj is overlap with previous neighbor, it's 
	 * complement with subsequent neighbor
	 * @param o1 o2 double, in [0,1]
	 * @return 1
	 */
	public int resetOverlaps(double o1,double o2) {
		
		if (o1<0 || o1>=1.0 || o2<0 || o2>=1.0)
			throw new DataException("'chaperon' usage: 2 overlaps in [0,1]");
		
		// restriction: if jumps are only 1 circle apart (only one circle on sister2)
		int numV=origChild.kData[1].num;
		int jdist=(jumpIndx[2]-jumpIndx[1]+numV)%numV;
		if (jdist==1) {
			
			// if jump 1 and prejump 2 are neighbors then ovlp[0] can be anything
			//   in [0,1], but I think ovlp[1] must equal ovlp[0].
			// REASON: If sister 2 goes to zero in radius, then sister1, jump 1, and
			//   prejump 2 go though common point (at the 'fallguy'), so sum of overlaps
			//   must be Pi, but jump 1 and prejump 2 are tangent, so overlap of jump 1
			//   and prejump2 must be Pi-ovlp[0]. By definition is this same overlap is
			//   the supplement of ovlp[1], so ovlp[0]=ovlp[1].
			// ?????? does sister 2 have to go to zero in radius ?????
			if (jumpIndx[2]%numV==(jumpIndx[1]+1)%numV) {
				if (o2>o1)
					o2=o1;
				CirclePack.cpb.msg("short jump: overlap 2 cut back to size of overlap 1");
			}
			// if jump 1 right after jump 2, then ovlp[0]<=ovlp[1] (both cases can't hold) 
			else if (jumpIndx[1]%numV==(jumpIndx[2]+1)%numV) {
				if (o1>o2)
					o1=o2;
				CirclePack.cpb.msg("short jump: overlap 1 cut back to size of overlap 2");
			}
		}
		
		cos_overs[1]=Math.cos(o1*Math.PI);
		cos_overs[2]=Math.cos(o2*Math.PI);
		
		// set overlaps
		myPackData.alloc_overlaps();
		myPackData.set_single_invDist(chap[1],preJump[1],cos_overs[1]);
		myPackData.set_single_invDist(chap[1],jumpCircle[1],(-1.0)*cos_overs[1]);
		myPackData.set_single_invDist(chap[2],preJump[2],cos_overs[2]);
		myPackData.set_single_invDist(chap[2],jumpCircle[2],(-1.0)*cos_overs[2]);
		
		return 1;
	}
	
	/**
	 * Modify a copy of stored 'origChild' packing based on current
	 * 'jumpIndx[]' and 'cos_overs[]' (which have to be stored in the
	 * right places). This sets chap[.].
	 * @return @see PackData
	 */
	public PackData modifyMyPack() {
		
		// Create the new packing starting with the original cutout patch
		PackData modifyPack=origChild.copyPackTo();
		int numV=modifyPack.kData[1].num;
		
  		// DebugHelp.debugPackWrite(myPackData,"ChapChild.p");
		modifyPack.alloc_pack_space(modifyPack.nodeCount+5,true);
		KData kData1=modifyPack.kData[1].clone();
		
		// new vertices: 
		chap=new int[3]; // chap[0] entry is not used
		sister2=modifyPack.nodeCount + 1;
		chap[1]=sister2+1;
		chap[2]=chap[1]+1;
		newBrSpot=chap[2]+1;
		
		// save info before it's lost
		preJump=new int[3];
		jumpCircle=new int[3];
		preJump[1]=kData1.flower[(jumpIndx[1]-1+numV)%numV];
		jumpCircle[1]=kData1.flower[jumpIndx[1]];
		preJump[2]=kData1.flower[(jumpIndx[2]-1+numV)%numV];
		jumpCircle[2]=kData1.flower[jumpIndx[2]];
		
		// create newCent
		KData newK=new KData();
		RData newR=new RData();
		int []newflower=new int[5];
		newflower[0]=newflower[4]=1;
		newflower[1]=chap[1];
		newflower[2]=sister2;
		newflower[3]=chap[2];
		newK.num=4;
		newK.flower=newflower;
		newK.bdryFlag=0;
		newK.plotFlag=1;
		newR.rad=.05;
		newR.aim=myAim;
		modifyPack.kData[newBrSpot]=newK.clone();
		modifyPack.rData[newBrSpot]=newR.clone();

		// create chap[1]
		newflower=new int[6];
		newflower[0]=newflower[5]=1;
		newflower[1]=kData1.flower[(jumpIndx[1]-1+numV)%numV];
		newflower[2]=kData1.flower[jumpIndx[1]];
		newflower[3]=sister2;
		newflower[4]=newBrSpot;
		newK.num=5;
		newK.flower=newflower;
		newK.bdryFlag=0;
		newK.plotFlag=1;
		newR.center=new Complex(0.0);
		newR.rad=.05;
		newR.aim=pi2;
		modifyPack.kData[chap[1]]=newK.clone();
		modifyPack.rData[chap[1]]=newR.clone();
			
		// create chap[2]
		newflower=new int[6];
		newflower[0]=newflower[5]=1;
		newflower[1]=newBrSpot;
		newflower[2]=sister2;
		newflower[3]=kData1.flower[(jumpIndx[2]-1+numV)%numV];
		newflower[4]=kData1.flower[jumpIndx[2]];
		newK.num=5;
		newK.flower=newflower;
		newK.bdryFlag=0;
		newK.plotFlag=1;
		newR.center=new Complex(0.0);
		newR.rad=.05;
		newR.aim=pi2;
		modifyPack.kData[chap[2]]=newK.clone();
		modifyPack.rData[chap[2]]=newR.clone();
		
		// Split the flower: adjust at 1
		newK=new KData();
		newR=new RData();
		int newcount=(jumpIndx[1]-jumpIndx[2]+numV)%numV;
		newflower=new int[newcount+4];
		newflower[0]=newBrSpot;
		newflower[1]=chap[2];
		for (int j=0;j<newcount;j++)
			newflower[j+2]=kData1.flower[(jumpIndx[2]+j)%numV];
		newflower[newcount+2]=chap[1];
		newflower[newcount+3]=newBrSpot;
		newK.num=newcount+3;
		newK.flower=newflower;
		newK.bdryFlag=0;
		newK.plotFlag=1;
		newR.aim=pi2;
		newR.rad=origChild.getRadius(1);

		modifyPack.kData[1]=newK.clone();
		modifyPack.rData[1]=newR.clone();
		
		// rest of flower goes with new sister2
		newcount=(jumpIndx[2]-jumpIndx[1]+numV)%numV;
		newflower=new int[newcount+4];
		newflower[0]=newBrSpot;
		newflower[1]=chap[1];
		for (int j=0;j<newcount;j++) {
			int w=kData1.flower[(jumpIndx[1]+j)%numV];
			newflower[j+2]=w;
			
			// have to replace 1 by sister2 in flower of w
			int dx=origChild.nghb(w,1);
			if (dx==0 && origChild.kData[w].bdryFlag==0) 
				modifyPack.kData[w].flower[0]=modifyPack.kData[w].flower[modifyPack.kData[w].num]=sister2;
			else
				modifyPack.kData[w].flower[dx]=sister2;
			
		}
		newflower[newcount+2]=chap[2];
		newflower[newcount+3]=newBrSpot;
		newK.num=newcount+3;
		newK.flower=newflower;
		newK.bdryFlag=0;
		newK.plotFlag=1;
		newR.rad=.75*origChild.getRadius(1);
		newR.center=new Complex(0.0);
		newR.aim=pi2;
		
		modifyPack.kData[sister2]=newK.clone();
		modifyPack.rData[sister2]=newR.clone();
		
		// fix preJump1
		int indx=modifyPack.nghb(preJump[1],1);
		modifyPack.insert_petal(preJump[1],indx,chap[1]);
		
		// fix jump1
		indx=modifyPack.nghb(jumpCircle[1],preJump[1]);
		modifyPack.insert_petal(jumpCircle[1],indx,chap[1]);
		
		// fix preJump2 (careful, this may be jump1)
		indx=(modifyPack.nghb(preJump[2],jumpCircle[2])+1)%(modifyPack.kData[preJump[2]].num);
		modifyPack.insert_petal(preJump[2],indx,chap[2]);
		
		// fix jump2
		indx=(modifyPack.nghb(jumpCircle[2],1)+1)%(modifyPack.kData[jumpCircle[2]].num+1);
		modifyPack.insert_petal(jumpCircle[2],indx,chap[2]);

	  	// new pack should be ready
		modifyPack.nodeCount=newBrSpot;
		
		boolean debug=false; // debug=true;
	  	if (debug)
	  		DebugHelp.debugPackWrite(modifyPack,"ChapParam.p");

		modifyPack.setCombinatorics();
		
		// set colors
		modifyPack.kData[1].color=new Color(125,0,0); // light red
		modifyPack.kData[sister2].color=new Color(255,0,0); // dark red
		modifyPack.kData[chap[1]].color=new Color(0,200,0); // green
		modifyPack.kData[chap[2]].color=new Color(0,0,200); // blue
		modifyPack.kData[newBrSpot].color=new Color(205,205,205); // grey
				
		// set overlaps
		modifyPack.alloc_overlaps();
		modifyPack.set_single_invDist(chap[1],preJump[1],cos_overs[1]);
		modifyPack.set_single_invDist(chap[1],jumpCircle[1],(-1.0)*cos_overs[1]);
		modifyPack.set_single_invDist(chap[2],preJump[2],cos_overs[2]);
		modifyPack.set_single_invDist(chap[2],jumpCircle[2],(-1.0)*cos_overs[2]);
		
		// unbranched packing to start
		try {
			double crit=GenBranching.LAYOUT_THRESHOLD;
			int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
			modifyPack.set_aim_default();
			modifyPack.fillcurves();
			modifyPack.repack_call(100);
			modifyPack.comp_pack_centers(false,false,opt,crit);
		} catch(Exception ex) {
			throw new CombException("layout: "+ex.toString());
		}
		
		// now reset aim to get branching
		modifyPack.rData[newBrSpot].aim=myAim;
		
		// first face is {1,chap[2],u)
		modifyPack.firstFace=modifyPack.what_face(1,chap[2],modifyPack.kData[1].flower[(modifyPack.nghb(1,chap[2])+1)%modifyPack.kData[1].num]);
/*		GraphLink dG=DualGraph.buildDualGraph(myPackData,myPackData.firstFace,eL);
		dG=DualGraph.drawSpanner(myPackData,dG,myPackData.firstFace);
		DualGraph.tree2Order(myPackData,dG);
*/
		
		// need to redefine 'borderLink'; first mark the bdry faces
		int []futil=new int[modifyPack.faceCount+1];
		for (int v=1;v<=modifyPack.nodeCount;v++) {
			if (modifyPack.kData[v].bdryFlag!=0) {
				for (int j=0;j<modifyPack.kData[v].num;j++)
					futil[modifyPack.kData[v].faceFlower[j]]=1;
			}
		}
		
		// Find first red edge which is a bdry face
		RedEdge rededge=modifyPack.firstRedEdge;
		int wflag=0;
		RedEdge re1=null;
		while(re1==null && (rededge!=modifyPack.firstRedEdge || wflag++==0)) {
			wflag=1;
			if (futil[rededge.face]==1)
				re1=rededge;
			rededge=rededge.nextRed;
		} // end of while
		
		if (re1==null)
			throw new CombException("Didn't find red edge");
		modifyPack.firstRedEdge=re1;
		modifyPack.firstRedFace=re1.face;
		
		// build closed borderLink of redchain bdry faces
		borderLink=new FaceLink(modifyPack);
		borderLink.add(re1.face);
		RedList redface=re1.next;
		while (redface.face!=modifyPack.firstRedFace && futil[redface.face]==1) {
			borderLink.add(redface.face);
			redface=redface.next;
		}
		
		// closed?
		if (redface.face==modifyPack.firstRedFace)
			borderLink.add(redface.face);
		
		// set face drawing order: poison edges allow just one face to reach 'newBrSpot'
		//   (radius of 'newBrSpot' may be zero, so its faces are not used for subsequent layout
		if (borderLink!=null && borderLink.size()>0)
			modifyPack.firstFace=borderLink.get(0);
		else 
			modifyPack.firstFace=modifyPack.kData[modifyPack.bdryStarts[1]].faceFlower[0];
		modifyPack.poisonEdges=new EdgeLink(modifyPack,"b");
		for (int j=1;j<modifyPack.kData[newBrSpot].num;j++) {
			int vj=modifyPack.kData[newBrSpot].flower[j];
			int wj=modifyPack.kData[newBrSpot].flower[j+1];
			modifyPack.poisonEdges.add(new EdgeSimple(vj,wj));
		}
		modifyPack.poisonEdges.add(new EdgeSimple(newBrSpot,modifyPack.kData[newBrSpot].flower[0]));
		modifyPack.poisonEdges.add(new EdgeSimple(newBrSpot,modifyPack.kData[newBrSpot].flower[1]));

		GraphLink gl=DualGraph.buildDualGraph(modifyPack,modifyPack.firstFace,modifyPack.poisonEdges);
		modifyPack.drawingTree=DualGraph.drawSpanner(modifyPack,gl,modifyPack.firstFace);
		CPBase.Glink=null;
//		CPBase.Glink= modifyPack.drawingTree.makeCopy();DualGraph.printGraph(modifyPack.drawingTree);
		
		return modifyPack;
	}

	/**
	 * See if there are special actions for display on screen of parent packing.
	 * If so, do them, remove them, and pass the rest to 'super'.
	 * 
	 * @param flagSegs flag sequences
	 * @return int count of display actions
	 */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		StringBuilder pulloff=new StringBuilder();
		Vector<Vector<String>> newFlagSegs=new Vector<Vector<String>>(1);
		Vector<String> items=new Vector<String>(2);
		int n=0;
		for (int j=0;j<flagSegs.size();j++) {
			items=flagSegs.get(j);
			String str=items.get(0);
			
			// get info to reconstruct new command: 
			// e.g. -s1fc20 converts to -cfc20 <sister1>
			String suff="";  // save suffex of original, e.g. 'fc5t4' 
			String target=null; // build target list
			Character c=null;  // possible number character
			if (str.length()>2)
				c=Character.valueOf(str.charAt(2));

			// look for objects to parse here
			char c2;
			if (str.length()>1 && ((c2=str.charAt(1))=='s' || c2=='h' || c2=='y' || c2=='j')) {
				if (str.startsWith("-s")) { // sisters
					if (c!=null && (char)c=='1' || (char)c=='2') {
						if ((char)c=='1')
							target=" 1";
						else
							target=new String(" "+sister2+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else { 
						target=new String(" 1 "+sister2+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
				}
				else if (str.startsWith("-h")) { // chaperones
					if (c!=null && (char)c=='1' || (char)c=='2') {
						if ((char)c=='1')
							target=new String(" "+chap[1]+" ");
						else
							target=new String(" "+chap[2]+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else {
						target=new String(" "+chap[1]+" "+chap[2]+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
				}
				else if (str.contains("-j")) { // jump circles
					if (c!=null && (char)c=='1' || (char)c=='2') {
						if ((char)c=='1')
							target=new String(" "+jumpCircle[1]+" ");
						else
							target=new String(" "+jumpCircle[2]+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else { 
						target=new String(" "+jumpCircle[1]+" "+jumpCircle[2]+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
				}
				else if (str.startsWith("-y")) { // newBrSpot
					target=new String(" "+newBrSpot+" ");
				}
				
				// build display string
				pulloff.append("-c");
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
			// else if -w flag, have parent run it, or store it to pass on to parent
			else {
				if (items.size()>0 && items.get(0).startsWith("-w")) {
					String fs=items.remove(0);
					CommandStrParser.jexecute(packData,"disp "+fs);
					n++;
					if (items.size()>0) // save anything after the -w flag
						newFlagSegs.add(items);
				}
				else 
					newFlagSegs.add(items);
			}
				
		} // end of loop through items

		n+=DisplayParser.dispParse(myPackData,packData.cpScreen,newFlagSegs);
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
		return n;
	}
	
	/**
	 * The parameters (for 'chaperone' version of "shifted") are (jump1, jump2),
	 * (indices for circles jumping to new sister) and associated overlap angles over1, over2.
	 * @return String
	 */
	public String getParameters() {
		return new String("'chaperone' branch point: aim "+
				myAim/Math.PI+"*Pi, vertex "+myIndex+", jumps "+
				packData.kData[myIndex].flower[jumpIndx[1]]+
				" "+packData.kData[myIndex].flower[jumpIndx[2]]+
				", overlaps "+Math.acos(cos_overs[1])/Math.PI+"*Pi "+Math.acos(cos_overs[2])/Math.PI+"*Pi");
	}
	
	public String reportExistence() {
		return new String("Started 'chaperone' branch point; v = "+myIndex);				
	}
	
	public String reportStatus() {
		return new String("'chap' ID"+branchID+": vert="+myIndex+
				"; j1="+jumpCircle[1]+"; j2="+jumpCircle[2]+
				"; over1="+Math.acos(cos_overs[1])/Math.PI+"; over2="+Math.acos(cos_overs[2])/Math.PI+
				"; aim="+myAim/Math.PI+"; holonomy err="+super.myHolonomyError());
	}
	
	/**
	 * Create poison edges for the parent that enclose the chaperones; the 
	 * edges run from 'myIndex' out to predecessor of jump[1], around to 
	 * jump[2], then back to 'myIndex'. 
	 * @return size of 'parentPoison'.
	 */
	public int setPoisonEdges() {
		EdgeLink elink=new EdgeLink(packData);
		if (packData.kData[myIndex].bdryFlag!=0)
			throw new ParserException("'myIndex' should be interior");
		int []flower=packData.kData[myIndex].flower;
		int num=packData.kData[myIndex].num;
		elink.add(new EdgeSimple(myIndex,flower[(jumpIndx[1]-1+num)%num]));
		elink.add(new EdgeSimple(myIndex,flower[jumpIndx[2]]));
		int diff=(jumpIndx[2]-jumpIndx[1]+1+num)%num;
		int tick=0;
		do {
			int indx=(jumpIndx[1]-1+num+tick)%num;
			elink.add(new EdgeSimple(flower[indx],flower[(indx+1)%num]));
			tick++;
		} while (tick<diff);
		parentPoison=elink;
		return elink.size();
	}

}
