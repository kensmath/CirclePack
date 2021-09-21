package branching;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import canvasses.DisplayParser;
import circlePack.PackControl;
import dcel.DcelCreation;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RawDCEL;
import dcel.RedHEdge;
import dcel.Vertex;
import deBugging.DCELdebug;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.StringUtil;
import util.UtilPacket;

/**
 * A 'GenBranchPt' of the "chaparone" type is associated 
 * with a circle (versus with an interstice). This formulation
 * originated with Ed Crane and turns out to be preferable 
 * to the related "shifted" type. One adds "sister" and 
 * "chaparone" circles to the branch point combinatorics. 
 * Parameters tell where chaperones are added and each 
 * chaparone has an overlap angle and its supplementary 
 * angle as parameters.
 * 
 * The circle is represented as two 'sister' circles. 
 * Going around the petals of the original vertex, one 
 * encounters "prejump/jump" pairs of contiguous petals. 
 * The "jump" petal is in the flower of one sister, while 
 * the preceding "prejump" petal is in the flower of 
 * the other. Jumps are determined by two parameters on 
 * creation and these are subject to change. The 
 * parameters first determine locations of two chaperones 
 * in the combinatorics, then provide overlap angles. 
 * Each jump gets an overlap angle a between its chaperone 
 * and the prejump petal; the supplementary angle sa is 
 * the overlap between the chaperone and the jump petal. 
 * When a is close to 0, jump circle remains almost in 
 * contact with its original sister, and as a grows it jumps 
 * more firmly onto the next sister. 
 * 
 * Thus the circles from jump1 to prejump2 are associated
 * with sister2 only, from jump2 to prejump1 are associated
 * with sister1 only. In particular, jumps and prejumps 
 * must be distinct. 
 * 
 * There is a third chaperone "fall guy" who carries 
 * this branch point's "aim"; this is normally 4*Pi, in 
 * which case the fall guy's radius will drop to zero. 
 * However, to visualize the underlying structure, one 
 * can set the aim to 2*Pi, while to follow computations 
 * one can let it incrementally approach 4*Pi.
 * 
 * Changes in local structure require changes to 'packData':
 *   * 'packData.poisonEdges' are updated via EdgeLink 
 *   	'parentPoison'.
 *   * Aims of 'myIndex' and its petals are set negative 
 *   	(so parent doesn't repack)
 *   * The radius of v is sent to parent after repack 
 *      if transData[v]<0.
 *    
 * TODO: may eventually want to convert between 'shifted' 
 * and 'chaperone' types.

 * @author kens
 */

public class ChapBranchPt extends GenBranchPt {
	
	// adjustment info, parameters
	PackData origChild;			  // original as cut from parent
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
	public ChapBranchPt(PackData p,int bID,double aim,
			int v,double psdo1,double psdo2) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.CHAPERONE;
		myIndex=v;
		int numSV=p.countFaces(myIndex);
		
		if (packData.getBdryFlag(myIndex)!=0 || packData.countFaces(myIndex)<5)
			throw new CombException(
					"singular vert must be interior, degree at least 5");

		// set jump indices: note, these are local petal indices 
		//   for flower of 'myIndex' but will be same petal indices 
		//   as corresponding circles of the parent's flower.
		jumpIndx=new int[3];
		jumpIndx[1]=((int)Math.floor(psdo1)+1)%numSV;
		jumpIndx[2]=((int)Math.floor(psdo2)+1)%numSV;
		jumpCircle=new int[3];
		int[] flower=p.getFlower(myIndex);
		jumpCircle[1]=flower[jumpIndx[1]];
		jumpCircle[2]=flower[jumpIndx[2]];
		preJump=new int[3];
		preJump[1]=flower[(jumpIndx[1]-1+numSV)%numSV];
		preJump[2]=flower[(jumpIndx[2]-1+numSV)%numSV];

		// store overlaps
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
	public ChapBranchPt(PackData p,int bID,double aim,
			int v,int w1,int w2,double o1,double o2) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.CHAPERONE;
		myIndex=v;
		
		if (packData.getBdryFlag(myIndex)!=0 || packData.countFaces(myIndex)<5)
			throw new CombException("chaperone vert must be interior, degree at least 5");

		// set jump indices: note, these are local flower indices for 
		//    'myIndex' but will be same index as corresponding circles 
		//    of the parent's flower.
		jumpIndx=new int[3];
		jumpIndx[1]=p.nghb(myIndex,w1);
		jumpIndx[2]=p.nghb(myIndex,w2);
		int numSV=p.countFaces(myIndex);
		int[] flower=p.getFlower(myIndex);
		jumpCircle=new int[3];
		jumpCircle[1]=flower[jumpIndx[1]];
		jumpCircle[2]=flower[jumpIndx[2]];
		
		if ((jumpIndx[2]-jumpIndx[1]+numSV)%numSV<2 || (jumpIndx[1]-jumpIndx[2]+numSV)%numSV<2) {
			CirclePack.cpb.errMsg("char jump vertices "+jumpCircle[1]+", "+jumpCircle[2]+
					" are too close; must be separated by at least one petal");
			System.out.println("chaperone jump vertices are too close");
		}
			
		preJump=new int[3];
		preJump[1]=flower[(jumpIndx[1]-1+numSV)%numSV];
		preJump[2]=flower[(jumpIndx[2]-1+numSV)%numSV];

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
	 * Create 'origChild', which is just a flower with the 
	 * degree of 'myIndex' whose petals are identified with 
	 * those of 'myIndex'. However, we return a modified version 
	 * with chaperone circles whose combinatorial locations 
	 * and data are determined by the user via parameters.
	 * @return PackData
	 */
	public PackData createMyPack() {

		// a simple flower
		Vertex myVert=packData.packDCEL.vertices[myIndex];
		PackData myPack=DcelCreation.seed(packData.countFaces(myIndex),packData.hes);
		HalfLink outer=myVert.getOuterEdges();
		myPack.vertexMap=new VertexMap();
		myPack.vertexMap.add(new EdgeSimple(1,myIndex));
		Iterator<HalfEdge> outis=outer.iterator();
		parentHPoison=outer;
		int tick=2;
		while (outis.hasNext()) {
			int w=outis.next().origin.vertIndx;
			myPack.vertexMap.add(new EdgeSimple(tick++,w));
		}
		
		// add 'outer' edges to parent poison edges
		if (packData.poisonHEdges==null)
			packData.poisonHEdges=new HalfLink();
		packData.poisonHEdges.abutMore(outer);
		
  	  	vertexMap=myPack.vertexMap.makeCopy(); // reset to get the swaps
  	  	myPack.setAlpha(1);
		myPack.set_aim_default();
		matchCount=myPack.nodeCount;

  	  	boolean debug=false;
  	  	if (debug)
  	  		DebugHelp.debugPackWrite(myPack,"Chaperone.p");

		// set up transData; this shouldn't change with 'setParameters'
		transData=new int[matchCount+1];
		for (int i=1;i<=matchCount;i++) { // bdry are +, rest minus
			int ww=vertexMap.findW(i);
			if (ww!=0) {
				if (myPack.getBdryFlag(i)!=0) {
					transData[i]=ww; // positive: parent sends info on this vert to local
					packData.setAim(ww,-1.0);
				}
				else {
					transData[i]=-ww; // negative: local sends info on this vert to parent
					packData.setAim(ww,-1.0);
				}
			}
		}
		
		// store as permanent 'origChild' 
		origChild=myPack;

		// make packing with added chaperones
  	  	return modifyMyPack(); 
	}
	
	public void delete() {
		// remove poison edges associated with this branch point
		if (parentHPoison!=null)
			packData.poisonHEdges=HalfLink.removeDuplicates(parentHPoison,false);

		// reset aims of parent
		if (packData.getBdryFlag(myIndex)==0)
			packData.setAim(myIndex,2.0*Math.PI);
		int[] petals=packData.getPetals(myIndex);
		for (int j=0;j<petals.length;j++) {
			int k=petals[j];
			if (packData.getBdryFlag(k)==0)
				packData.setAim(k,2.0*Math.PI);
		}
	}
	
	/**
	 * This is an ordinary repacking, though the complex has
	 * extra circles and due to deep overlaps we use 'oldReliable'.
	 * @param cycles int, max iterations (if <0, set default)
	 * @return @see UtilPacket: 'rtnFlag' -1 on error, 'value' 
	 * l^2 norm of angle sum error.
	 * @param int cycles
	 * @return UtilPacket
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
	 * Local layout for this branch point using the usual
	 * 'layoutOrder'. Normalized position has sister 1 at 
	 * origin, sister 2 centered on the positive real axis, 
	 * hence usually internally tangent at z=1. (Note that 
	 * it may be that sister2 is larger than sister1.) 
	 * Update 'myHolonomy'.
	 * @param norm <code>boolean</code>: true, move to normalized position
	 * @return <ocde>double</code>, error in myHolonomy
	 */
	public double layout(boolean norm) {
		
		myPackData.packDCEL.layoutPacking();
		Mobius holomob=PackData.holonomyMobius(myPackData,myHoloBorder);
		double frobNorm=Mobius.frobeniusNorm(holomob);
		if (frobNorm<0)
			return 0;
		return frobNorm;
	}
	
	/**
	 * Assume parent has laid out its circles, use parent
	 * data to lay out the branch point circles according 
	 * to local 'layoutOrder'; this places 'sister2', 
	 * 'chap[1]', 'chap[2]', and 'newBrSpot'.
	 * @return 0 on error
	 */
	public int placeMyCircles() {
		int count=0;
		// get centers from parent
		for (int v=1;v<=matchCount;v++) {
			myPackData.setCenter(v,packData.getCenter(Math.abs(transData[v])));
			count++;
		}
		
		layout(false);
		return count;
	}
	

	/**
	 * Assume radii have been updated, what is the angle sum error?
	 * @return double, l^2 angle sum error.
	 */
	public double currentError() {
		return myPackData.angSumError();
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
					myPackData.setAim(newBrSpot,myAim);
					myPackData.setRadius(newBrSpot,0.5); // kick-start repacking
					count++;
					break;
				}
				case 'j': // jump petals (as vertex indices from parent)
				{
					jumpIndx=new int[3]; // jumpIndx[0] empty
					int numV=origChild.countFaces(1);
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
			if (parentHPoison!=null)
				packData.poisonHEdges=HalfLink.removeDuplicates(parentHPoison,false);
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
		int numV=origChild.countFaces(1);
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
		
		boolean debug=false; // debug=true;
		
		// Create the new packing starting with the original cutout patch
		PackData modifyPack=origChild.copyPackTo();
		modifyPack.alloc_pack_space(modifyPack.nodeCount+5,true);
		PackDCEL pdc=modifyPack.packDCEL;

		HalfLink spokes=pdc.vertices[1].getEdgeFlower();
		HalfEdge wedge=spokes.get(jumpIndx[1]);
		HalfEdge uedge=spokes.get(jumpIndx[2]);
		int w=wedge.twin.origin.vertIndx;
		int u=uedge.twin.origin.vertIndx;
		
		HalfEdge sisteredge=RawDCEL.splitFlower_raw(pdc, wedge, uedge);
		sister2=sisteredge.origin.vertIndx;
		
		chap=new int[3]; // chap[0] entry is not used
		HalfEdge sp1=pdc.findHalfEdge(sister2,w);
		HalfEdge sp2=pdc.findHalfEdge(1,u);
		chap[1]=RawDCEL.splitEdge_raw(pdc,sp1).twin.origin.vertIndx;
		chap[2]=RawDCEL.splitEdge_raw(pdc,sp2).twin.origin.vertIndx;
		newBrSpot=RawDCEL.splitEdge_raw(pdc,sisteredge).twin.origin.vertIndx;
		pdc.fixDCEL_raw(modifyPack);
		
		debug=false; // debug=true;
		if (debug)
			DCELdebug.printBouquet(pdc);
		
		// set colors
		modifyPack.setCircleColor(1,new Color(125,0,0)); // light red
		modifyPack.setCircleColor(sister2,new Color(255,0,0)); // dark red
		modifyPack.setCircleColor(chap[1],new Color(0,200,0)); // green
		modifyPack.setCircleColor(chap[2],new Color(0,0,200)); // blue
		modifyPack.setCircleColor(newBrSpot,new Color(205,205,205)); // grey
				
		// set overlaps
		HalfEdge he=pdc.findHalfEdge(chap[1],preJump[1]);		
		he.setInvDist(cos_overs[1]);

		he=pdc.findHalfEdge(chap[1],jumpCircle[1]);
		he.setInvDist((-1.0)*cos_overs[1]);
		
		he=pdc.findHalfEdge(chap[2],preJump[2]);
		he.setInvDist(cos_overs[2]);
		
		he=pdc.findHalfEdge(chap[2],jumpCircle[2]);
		he.setInvDist((-1.0)*cos_overs[2]);
		
		// 'alpha' is edge <1 chap[2]>
		pdc.alpha=pdc.findHalfEdge(1,chap[2]);
		modifyPack.directAlpha(1);
		modifyPack.directGamma(sister2);
		
		// Start red chain with bdry edge of 'alpha.twin's
		HalfEdge het=pdc.alpha.twin.prev.twin.next;
		RedHEdge rtrace=pdc.redChain;
		while (rtrace.myEdge!=het)
			rtrace=rtrace.nextRed;
		pdc.redChain=rtrace;
		
		// get layoutOrder
		HalfLink redlink=new HalfLink(modifyPack,"-R");
		pdc.layoutOrder=RawDCEL.leftsideLink(pdc,redlink);
		pdc.layoutOrder.add(0,pdc.layoutOrder.removeLast());
		pdc.layoutOrder.add(0,pdc.alpha);
		pdc.layoutOrder.removeLast();
		pdc.layoutOrder.add(pdc.alpha.twin); // this picks up 'newBrPoint'
		lastLayoutEdge=pdc.alpha.twin;
		
		// unbranched packing to start
		try {
			modifyPack.set_aim_default();
			modifyPack.fillcurves();
			modifyPack.repack_call(100);
			modifyPack.packDCEL.layoutPacking(); 
		} catch(Exception ex) {
			throw new CombException("layoutCenters: "+ex.toString());
		}
		
		if (debug) // debug=true;
			DebugHelp.debugPackWrite(modifyPack,"modifyPack.p");
		
		// now reset aim to get branching
		modifyPack.setAim(newBrSpot,pi2); // myAim=pi2;
		return modifyPack;
	}
	
	/**
	 * See if there are special actions for display on screen 
	 * of parent packing. If so, do them, remove them, and 
	 * pass the rest to 'super'.
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
	 * The parameters for 'chaperone' type are (jump1, jump2),
	 * (indices for circles jumping to new sister) and 
	 * associated overlap angles over1, over2.
	 * @return String
	 */
	public String getParameters() {
		int[] flower=packData.getFlower(myIndex);
		return new String("'chaperone' branch point: aim "+
				myAim/Math.PI+"*Pi, vertex "+myIndex+", jumps "+
				flower[jumpIndx[1]]+" "+flower[jumpIndx[2]]+
				", overlaps "+Math.acos(cos_overs[1])/Math.PI+"*Pi "+
				Math.acos(cos_overs[2])/Math.PI+"*Pi");
	}
	
	public String reportExistence() {
		return new String(
				"Started 'chaperone' branch point; v = "+
						myIndex);				
	}
	
	public String reportStatus() {
		return new String("'chap' ID"+branchID+": vert="+myIndex+
				"; j1="+jumpCircle[1]+"; j2="+jumpCircle[2]+
				"; over1="+Math.acos(cos_overs[1])/Math.PI+"; over2="+Math.acos(cos_overs[2])/Math.PI+
				"; aim="+myAim/Math.PI+"; holonomy err="+super.myHolonomyError());
	}

}
