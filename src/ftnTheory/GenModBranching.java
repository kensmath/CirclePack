package ftnTheory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import baryStuff.BaryPoint;
import branching.ChapBrModPt;
import branching.GenBrModPt;
import branching.SingBrModPt;
import branching.TradBrModPt;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.HyperbolicMath;
import komplex.EdgeSimple;
import listManip.BaryLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PointLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.StringUtil;

/**
 * Generalized branch points are small subcomplexes of 
 * circle packing complexes which are structured to 
 * support local branching-like behavior via various 
 * non-standard constructions and manipulations.
 * 
 * "Traditional" branching is very rigid, meaning that 
 * discrete objects cannot adequately mimic classical 
 * behavior. This is especially true in the case of 
 * multiply connected combinatorics. The goal is to 
 * provide parameters which allow global coherent 
 * behavior by accepting small local islands of 
 * incoherence. 
 * 
 * Among the methods currently available are 
 * "singular" branching of faces and "chaperone" 
 * branching of circles. We include "traditional" 
 * branching of circles; others, such as "quad" 
 * branching may be developed.

 * For general background, see James Ashe's thesis and 
 * experiments. The original code and philosophy,
 * used e.g. in our joint paper with Crane, is OBE; 
 * this new code is much simpler due to DCEL structures.
 * 
 * @author Ken Stephenson, James Ashe, Edward Crane, 
 * started 5/2012. revised versions started 9/21
 *
 */
public class GenModBranching extends PackExtender {
	
	// Stored copy of original packing, with layout and everything.
	// NOTE: Usually displayed on a separate canvas for interactive
	//   "clicks", but the data is computed using the stored
	//   'refPack', not its copy.
	public PackData refPack;  
	
	Vector<GenBrModPt> branchPts; // indexing starts with 1
	public static final double LAYOUT_THRESHOLD=.00001; // for layouts based on quality
	public HalfLink holoBorder; // for holonomy about the parent's red chain
	public static double m2pi=2.0*Math.PI;
	ArrayList<Vertex> exclusions; // vertices to avoid when choosing 'alpha'
    private HalfLink poisonHEdges; // migrate to this

	// Constructor
    public GenModBranching(PackData p) {
		super(p);
		extensionType="GENERALIZED_BRANCHING_MOD";
		extensionAbbrev="GB";
		toolTip="'Generalized_Branching' provides methods for "
				+ "incorporating various generalized branched "
				+ "points into the parent circle packing. "
				+ "(For purposes of interaction, a copy of the "
				+ "original parent is held in 'refPack'.)";
		registerXType();
		if (running) {
			poisonHEdges=null;
			refPack=packData.copyPackTo(); // maintains original
			packData.packExtensions.add(this);
		}
		
		// initialize 'holoBorder' along full red chain
		holoBorder=HalfLink.HoloHalfLink(packData.packDCEL,-1);
		branchPts=new Vector<GenBrModPt>(3);
		branchPts.add((GenBrModPt)null); // index 0 empty; number from 1
	}

    /**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		int count=0;
		GenBrModPt cmdBranchPt=null; // designated branch point for this command?
		
		// -----------------------------------------------------
		// check for -b{n} (or -b {n}) flag designating a branch point n>=1; 
		//    Note: we remove the -b flag and number, leaving the rest
		try {
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				String str=items.get(0);
				// is this a '-b' flag?
				if (StringUtil.isFlag(str) && str.charAt(1)=='b') {
					items.remove(0);
					if (str.length()>2) 
						str=str.substring(2);
					else
						str=items.remove(0);
					
					// was there anything else to keep in this segment?
					if (items.size()==0)  // no
						flagSegs.remove(0);
					
					// this is given ID
					int bn=Integer.parseInt(str);
					int bpIndx=findBPindex(bn);
					
					// want 'delete' command to go through even 
					//    if no branch point
					if (bpIndx<0) {
						if (cmd.startsWith("delet"))
							return 1;
						else { 
							Oops("no branch point found for ID = "+bn);
							return 0;
						}
					}
					
					cmdBranchPt=branchPts.get(bpIndx);
				}
			}
		} catch(Exception ex) {
			throw new ParserException(
					"parse error with branch ID: "+ex.getMessage());
		}
		// ---------------------------------------------------
		
		// =========== event ====== (append horizon to 'vlist')
		if (cmd.startsWith("event")) {
			if (branchPts.size()==1)
				return 1;
			GenBrModPt gbp=branchPts.get(1);
			if (cmdBranchPt!=null)
				gbp=cmdBranchPt;

			if (packData.elist==null)
				packData.elist=new EdgeLink();
			Iterator<HalfEdge> bis=gbp.eventHorizon.iterator();
			while (bis.hasNext()) {
				HalfEdge he=bis.next();
				packData.elist.add(new EdgeSimple(he.origin.vertIndx,
						he.twin.origin.vertIndx));
			}
			return 1;
		}
		
		// =========== click ===================
		if (cmd.startsWith("click")) {
			boolean localWipe=false;
			boolean fullWipe=false;
			double theAim=4.0;
			Iterator<Vector<String>> its=flagSegs.iterator();
			while (its.hasNext()) {
				items=its.next();
				String str=items.get(0);
				if (StringUtil.isFlag(str)) {
					items.remove(0);
					char c=str.charAt(1);
					switch(c) {
					case 'x': 
					{
						localWipe=true;
						break;
					}
					case 'X': // global revert
					{
						revert();
						break;
					}
					case 'a':
					{
						try {
							theAim=Double.parseDouble(items.get(0));
							items.remove(0);
						} catch(Exception ex) {
							theAim=4.0;
						}
						break;
					}
					} // end of switch
				}
			} // end of while
			
			try {
				Complex pt=PointLink.grab_one_z(StringUtil.reconItem(items));
				
				// key routine: find type of branching/parameters at 'pt'
				double []data=getClickData(refPack,pt);
				
				int mode=(int)data[0];
				int indx=(int)data[1];
				NodeLink nlink=null;
				FaceLink flink=null;
								
				// handle wipeouts first
				if (mode<0 || mode>3 || indx<=0 || indx>packData.nodeCount) 
					return 0;

				// wipe out contiguous branch points 
				//    (including, possibly the current circle)
				if (localWipe) {
					if (mode>1) // circle case
						nlink=new NodeLink(packData,"Iv "+indx);
					else // face case
						nlink=new NodeLink(packData,"If "+indx);
					Vector<Integer> vIDs=bps4verts(nlink);
					Iterator<Integer> vs=vIDs.iterator();
					while (vs.hasNext())
						deleteBP(vs.next());
					
					if (mode>1)
						flink=new FaceLink(packData,"Iv "+indx);
					else
						flink=new FaceLink(packData,"If "+indx);
					vIDs=bps4faces(flink);
					vs=vIDs.iterator();
					while (vs.hasNext())
						deleteBP(vs.next());
				}
				else if (fullWipe) {
					branchPts=new Vector<GenBrModPt>(3);
					branchPts.add((GenBrModPt)null);
					packData.setCombinatorics();
					packData.set_aim_default();
				}
				
				// circle?
				if (mode==2) { // data[2]/[3] are the jump petals, data[4]/[5] the overlaps
					ChapBrModPt cbp=new ChapBrModPt(this,branchPts.size(),
							theAim*Math.PI,indx,(int)data[2],(int)data[3],data[4],data[5]);
					installBrPt(cbp);
				} 

				// traditional branch point
				else if (mode==3) { // traditional branch point
					TradBrModPt tbp=new TradBrModPt(this,
							branchPts.size(),theAim*Math.PI,indx);
					installBrPt(tbp);
				}

				// interstice?
				else if (mode==1) { // find data and create 
					SingBrModPt sbp=new SingBrModPt(this,branchPts.size(),
							theAim*Math.PI,indx,data[2],data[3]); 
					installBrPt(sbp);
				}
				else 
					return 0;
				
				// get drawing order for outside of branch regions
				return 1;
				
			} catch (Exception ex) {
				throw new ParserException("failed in 'click' processing");
			}
		}
		
		// ========== copy <pnum> 
		if (cmd.startsWith("copy")) { // copy 'refPack' to some pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				PackData newCopy=refPack.copyPackTo();
				for (int v=1;v<=newCopy.nodeCount;v++)
					newCopy.setPlotFlag(v, 1);
				return CirclePack.cpb.swapPackData(newCopy,pnum,false);
			} catch (Exception ex) {
				return 0;
			}
		}
		
		// =========== revert ===========
		if (cmd.startsWith("revert")) { // revert 'packData' to 'refPack'
			revert();
			return 1;
		}
		
		// =========== holonomy ===================
		if (cmd.startsWith("holon")) {
			// Idea is to use holoBorder to check holonomy of full packing
			// Note: this does not actually change any centers
			Mobius holomob=PackData.holonomyMobius(packData,holoBorder);
			double frobNorm=Mobius.frobeniusNorm(holomob);
			if (frobNorm<0)
				return 0;
			return 1;
		}
		
		// =========== delete =====================
		if (cmd.startsWith("dele")) {
			int bps=branchPts.size()-1;
			if (bps==0) {
				CirclePack.cpb.errMsg(
						"The packing has no generalized branch points to delete.");
				return 1;
			}
			
			// is all is specified or there's only one branch pt, revert. 
			items=(Vector<String>)flagSegs.get(0);
			if (items!=null && items.get(0).startsWith("a") || bps==1) { 
				revert();
				return 1;
			}
			
			// nothing specified?
			if (cmdBranchPt==null) {
				if (branchPts.size()>1)
					throw new ParserException("branch pt wasn't specified.");
				return 1;
			}
			
			// otherwise, delete specified one
			deleteBP(cmdBranchPt.branchID);

			// reset parent's drawing order
			count++;
		}
		
		// ========== reset_overlaps =========
		else if (cmd.startsWith("reset_o")) {
		
			if (cmdBranchPt==null || (cmdBranchPt.myType!=GenBrModPt.CHAPERONE &&
					cmdBranchPt.myType!=GenBrModPt.SINGULAR))
				return 0;
			
			double []ovlp=new double[2];
			try {
				items=flagSegs.get(0);
				ovlp[0]=Double.parseDouble(items.get(0));
				ovlp[1]=Double.parseDouble(items.get(1));
			} catch (Exception ex) {
				errorMsg("didn't get 2 overlap parameters");
				return 0;
			}
			
			if (cmdBranchPt.myType==GenBrModPt.CHAPERONE) {
				ChapBrModPt cbp=(ChapBrModPt)cmdBranchPt;
				cbp.resetOverlaps(ovlp[0],ovlp[1]);
			}
			else {
				SingBrModPt sbp=(SingBrModPt)cmdBranchPt;
				sbp.resetOverlaps(ovlp[0],ovlp[1]);
			}
			return 1;
		}

		// =========== local display command =================
		else if (cmd.startsWith("disp")) {
			count=1; // in case there are no branch points
			// do one, if designated
			if (cmdBranchPt!=null) {
				count=0;
				count = cmdBranchPt.displayMe(flagSegs);
			}
			// else do all
			else for (int p=1;p<branchPts.size();p++)
				count += branchPts.get(p).displayMe(flagSegs);
		}
		
		// =========== set_parameters =================
		else if (cmd.startsWith("set_para")) {
			if (cmdBranchPt==null)
				Oops("usage (depends on type): set_param -b{b} {parameters}");
			int ans=-1;
			try {
				ans=cmdBranchPt.setParameters(flagSegs);
				if (ans==0) 
					throw new ParserException();
			} catch (Exception ex) {
				Oops("Branch ID="+cmdBranchPt.branchID+
						", set parameters failed: "+ex.getMessage());
			}
			return Math.abs(ans);
		}
		
		// =========== get_parameters =================
		else if (cmd.startsWith("get_para")) {
			if (cmdBranchPt==null) {
				if (branchPts!=null && branchPts.size()>1) {
					CirclePack.cpb.msg(branchPts.get(1).getParameters());
					return 1;
				}
				return 0;
			}
			CirclePack.cpb.msg(cmdBranchPt.getParameters());
			return 1;
		}
		
		// =========== branch point status =================
		else if (cmd.startsWith("statu")) {
			if (branchPts.size()<2) { // first index is empty
				msg("Pack p"+packData.packNum+" has no branch points");
				count++;
			}
			else if (cmdBranchPt!=null) {
				msg(cmdBranchPt.reportStatus());
				count++;
			}
			else for (int b=1;b<branchPts.size();b++) {
				msg(branchPts.get(b).reportStatus());
				count++;
			}
			
			msg("parent anglesum error: "+packData.angSumError());
		}
		
		// ============ anglesum error
		else if (cmd.startsWith("angsum_err")) {
			double tmp=packData.angSumError();
			msg("anglesum l^2 error = "+tmp);
		}
		
		// =========== create various branch types =============
		
		// ============ traditional
		else if (cmd.startsWith("bp_trad")) { // type=1
			double aim=2.0;
			int getV=0;
			try {
				Iterator<Vector<String>> fit=flagSegs.iterator();
				while (fit.hasNext()) {
					items=fit.next();
					if (!StringUtil.isFlag(items.get(0)))
						throw new ParserException("bp_trad requires flags -[ai]");
					String sstr=items.remove(0);
					switch (sstr.charAt(1)) {
					case 'a': // aim, multiplied by 2pi here
					{
						aim=Double.parseDouble(items.get(0))*Math.PI;
						count++;
						break;
					}
					case 'v': // OBE: deprecated
					case 'i': // index of vertex
					{
						NodeLink vl=new NodeLink(packData,items);
						getV=vl.get(0);
						count++;
						break;
					}
					case 'X':  // wipe out all old
					{
						revert();
					}
					} // end of switch
				} // end of while	
			} catch(Exception ex) {
				throw new ParserException(
						"usage: -a {a} -i {v} "+
								ex.getMessage());
			}

			// match against 'exclusions'.
			HalfLink hlink=packData.packDCEL.vertices[getV].getOuterEdges();
			Iterator<HalfEdge> his=hlink.iterator();
			boolean hitx=false;
			if (exclusions!=null) {
				while (his.hasNext() && !hitx) {
					if (exclusions.contains(his.next().origin))
						hitx=true;
					if (exclusions.contains(packData.packDCEL.vertices[getV]))
						hitx=true;
				}
			}
			
			if (hitx)
				throw new ParserException("traditional branch at "+getV+
						" would interfere with another branch point");
			TradBrModPt tbp=new TradBrModPt(this,
					branchPts.size(),aim,getV);
			count=installBrPt(tbp);
		}

		// ============ singular
		else if (cmd.startsWith("bp_sing")) { // type=4
			double getAim=4.0;
			int getF=-1;
			double getO1=.3333333333;
			double getO2=.3333333333;

			try {
				Iterator<Vector<String>> fit=flagSegs.iterator();
				while (fit.hasNext()) {
					items=fit.next();
					if (!StringUtil.isFlag(items.get(0)))
						throw new ParserException("bp_sing requires flags -[abio]");
					String sstr=items.remove(0);
					switch (sstr.charAt(1)) {
					case 'a': // aim, multiplied by 2pi here
					{
						getAim=Double.parseDouble(items.get(0));
						getAim *=Math.PI;
						count++;
						break;
					}
					case 'b': // 'BaryLink' processing
					{
						BaryLink bl=new BaryLink(packData,items);
						if (bl.size()==0)
							break;
						BaryPoint bp=bl.get(0);
						if (bp.face>0)
							getF=bp.face;
						// bary coords are angle based appropriate 
						//    to interstice (not to face itself)
						getO1=(1-bp.b0)/2.0;
						getO2=(1-bp.b1)/2.0;
						count++;
						break;
					}
					case 'f': // OBE: deprecated
					case 'i': // index of face
					{
						FaceLink fl=new FaceLink(packData,items);
						getF=fl.get(0);
						count++;
						break;
					}
					case 'o': // overlaps, should be in [0,1]
					{
						getO1=Double.parseDouble(items.get(0));
						getO2=Double.parseDouble(items.get(1));
						count++;
						break;
					}
					case 'X':  // wipe out all old
					{
						revert();
					}
					} // end of switch

				} // end of while
			} catch(Exception ex) {
				throw new ParserException(
						"usage: -a {a} -i {f} -o {o1 o2} [-b {blist}]. "+
								ex.getMessage());
			}
			
			if (getF<1 || getF>packData.faceCount)
				throw new ParserException("singular face missing or inappropriate");
			
			// match again 'exclusions'.
			combinatorics.komplex.DcelFace singFace=packData.packDCEL.faces[getF];
			HalfEdge he=singFace.edge;
			boolean hitx=false;
			do {
				if (exclusions!=null && exclusions.contains(he.origin))
					hitx=true;
				he=he.next;
			} while (he!=singFace.edge && !hitx);
			if (hitx)
				throw new ParserException("singular face "+singFace+" would "+
						"interfere with another branch point");

			// create the branch point
			SingBrModPt sbp=new SingBrModPt(this,branchPts.size(),
					getAim,getF,getO1,getO2);
			
			count=installBrPt(sbp);
		}

		// ============ chaperone
		else if (cmd.startsWith("bp_chap")) { // type=6
			double getAim=4.0;
			int getV=-1;
			int getW1=-1;
			int getW2=-1;
			double getO1=.3333333333;
			double getO2=.3333333333;

			try {
				Iterator<Vector<String>> fit=flagSegs.iterator();
				while (fit.hasNext()) {
					items=fit.next();
					if (!StringUtil.isFlag(items.get(0)))
						throw new ParserException("missing flag -[aijo] or -z {x y} ");
					String sstr=items.remove(0);
					switch (sstr.charAt(1)) {
					case 'a': // new aim
					{
						getAim=Double.parseDouble(items.get(0));
						count++;
						break;
					}
					case 'j': // jump petals indices 
					{
						getW1=Integer.parseInt(items.get(0));
						getW2=Integer.parseInt(items.get(1));
						count += 2;
						break;
					}
					case 'o': // overlaps
					{
						getO1=Double.parseDouble(items.get(0));
						getO2=Double.parseDouble(items.get(1));
						count++;
						break;
					}
					case 'v': // OBE: deprecated
					case 'i': // index of vertex
					{
						NodeLink nl=new NodeLink(packData,items);
						getV=nl.get(0);
						count++;
						break;
					}
					case 'X':  // wipe out all former branch points
					{
						revert();
					}
					} // end of switch
				
					// TODO: may want to accommodate more jumps/overlaps in future,
					//       depending, e.g., on 'myAim'.
				} // end of while
			} catch(Exception ex) {
				throw new ParserException(
						"usage: -a {a} -i {v} -j {j1 j2} -o {o1 o2}. "+
								ex.getMessage());
			}
			
			if (getV<1 || getV>packData.nodeCount) // packData.vert_isPoison(1);
				throw new ParserException("chaperone vertex missing or inappropriate");
			if (getW1<0 || getW2<0) { // default settings
				int[] flower=packData.getFlower(getV);
				getW1=flower[1];
				getW2=flower[3];
			}
			
			// match against 'exclusions'.
			HalfLink hlink=packData.packDCEL.vertices[getV].getOuterEdges();
			Iterator<HalfEdge> his=hlink.iterator();
			boolean hitx=false;
			if (exclusions!=null) {
				while (his.hasNext() && !hitx) {
					if (exclusions.contains(his.next().origin))
						hitx=true;
					if (exclusions.contains(packData.packDCEL.vertices[getV]))
						hitx=true;
				}
			}

			if (hitx)
				throw new ParserException("chaperone branch at "+getV+
						" would interfere with another branch point");

			// create the branch point
			ChapBrModPt cbp=new ChapBrModPt(this,branchPts.size(),getAim*Math.PI,
					getV,getW1,getW2,getO1,getO2);

			count=installBrPt(cbp);
		}
		return count;
	} // done with cmdParser

	public void updateExclusions() {
		poisonHEdges=new HalfLink();
		exclusions=new ArrayList<Vertex>();
		for (int b=1;b<branchPts.size();b++) {
			GenBrModPt gbp=branchPts.get(b);
			poisonHEdges.abutMore(gbp.eventHorizon);
			Iterator<Vertex> eis=gbp.myExclusions.iterator();
			while (eis.hasNext())
				exclusions.add(eis.next());
		}
	}
	
	/**
	 * This creates 'layoutOrder' by partially lays out 
	 * the parent, circumventing the branch points, and then
	 * appending edges to handle its additional circles.
	 * @return HalfLink
	 */
	public void setLayoutOrder() {
		
		updateExclusions(); // update poisons and vertex exclusions
		
		// reset parent's 'alpha' if necessary
		Vertex alph=packData.packDCEL.alpha.origin;
		if (exclusions!=null && alph!=null && exclusions.contains(alph)) {
			NodeLink vlist=new NodeLink();
			Iterator<Vertex> vis=exclusions.iterator();
			while (vis.hasNext()) 
				vlist.add(vis.next().vertIndx);
			packData.packDCEL.setAlpha(0, vlist,false);
		}
		
		// update the packing/dcel first
		packData.packDCEL.fixDCEL(packData);

		// first get the layout circumventing the branch points
		HalfLink outerOrder=CombDCEL.partialTree(packData.packDCEL,
				poisonHEdges); // allMains.CPBase.Hlink=partOrder;

		// throw in 'layoutAddons' from branch points
		for (int b=1;b<branchPts.size();b++) {
			GenBrModPt gbp=branchPts.get(b);
			outerOrder.abutMore(gbp.layoutAddons);
		}

		// record
		packData.packDCEL.layoutOrder=outerOrder;
	}

	/**
	 * First, position the parent's outer circles using 'outerLayout'.
	 * Then layout each branch point as follows: layout 'myHoloBorder'
	 * whose first edge was positioned by the parent, then 
	 * 'lastLayoutEdge' to pick up 'newBrPoint'. 
	 * After that, everything is normalized based on the parent's
	 * 'alpha' and 'gamma', and that Mobius is applied to reposition 
	 * the centers of each branch point packing.
	 * Note that the parent holds data for all vertices except
	 * any added to branch points, e.g., chaperones, and their
	 * data is held by the 'myPackData's.  
	 * @return int, count of actions
	 */
	public int combinedLayout() {
		return 0;
	}
	
	/**
	 * See if index j (face or vertex, depending) is already a branch
	 * point with 'myType' type. If yes, return first matching index
	 * @param j int, face or vertex
	 * @param type int, 'myType'
	 * @return int 'myType' or -1 if no branch point of right type exists
	 */
	public int brptExists(int j,int type) {
		if(branchPts==null || branchPts.size()<=1)
			return -1;
		Iterator<GenBrModPt> bps=branchPts.iterator();
		bps.next(); // flush first null entry
		while (bps.hasNext()) {
			GenBrModPt gbp=bps.next();
			if (gbp!=null && gbp.myType==type) {
				if (type==GenBrModPt.SINGULAR) {
					combinatorics.komplex.DcelFace face=packData.packDCEL.faces[j];
					HalfEdge he=face.edge;
					do {
						if (he==gbp.myEdge) {
							return gbp.branchID;
						}
						he=he.next;
					} while(he!=face.edge);
					return -1;
				}
				else if (type==GenBrModPt.CHAPERONE) {
					if (gbp.myEdge.origin.vertIndx==j)
						return gbp.branchID;
					return -1;
				}
				else if (type==GenBrModPt.QUADFACE) {
					return -1; // not yet implemented
				}
			}
		}
		return -1;
	}
	
	/**
	 * Return true if j is one of the "excluded" vertices, not
	 * eligible for supporting a new branch point.
	 * @param j int
	 * @return boolean
	 */
	public boolean isExcluded(int j) {
		if (exclusions==null)
			return false;
		Iterator<Vertex> vis=exclusions.iterator();
		while (vis.hasNext())
			if (vis.next().vertIndx==j)
				return true;
		return false;
	}
	
	/**
	 * Given the branch point ID, find its index in current 
	 * 'branchPts' vector. ID is assigned on creation, but 
	 * may get out of line with index in 'branchPts'
	 * @param bpID int, assigned on creation
	 * @return -1 on failure to find
	 */
	public int findBPindex(int bpID) {
		if (branchPts==null || branchPts.size()<=1) // 0th spot should be empty
			return -1;
		int indx=-1;
		for (int j=1;j<branchPts.size();j++) {
			GenBrModPt gbp=branchPts.get(j);
			if (gbp.branchID==bpID) {
				if (indx>0)
					throw new DataException(
							"more than one branch point with ID = "+bpID);
				indx=j;
			}
		}
		return indx;
	}
	
	/**
	 * Return a list of branch ID's for branch points having vert in
	 * the given list.
	 * @param nlink NodeLink
	 * @return int vector, empty if none found
	 */
	public Vector<Integer> bps4verts(NodeLink nlink) {
		Vector<Integer> ans=new Vector<Integer>(1);
		if (branchPts==null || branchPts.size()==1)
			return ans;
		for (int j=1;j<branchPts.size();j++) {
			GenBrModPt gbp=branchPts.get(j);
			if ((gbp.myType==GenBrModPt.CHAPERONE && 
					nlink.containsV(gbp.myEdge.origin.vertIndx)!=-1) ||
					(gbp.myType==GenBrModPt.TRADITIONAL && 
					nlink.containsV(gbp.myEdge.origin.vertIndx)!=-1))
				ans.add(gbp.branchID);
		}
		return ans;
	}
	
	/**
	 * Return a list of branch ID's for branch points having a face in
	 * the given list.
	 * @param flink FaceLink
	 * @return int vector, empty if none found
	 */
	public Vector<Integer> bps4faces(FaceLink flink) {
		Vector<Integer> ans=new Vector<Integer>(1);
		if (branchPts==null || branchPts.size()==1)
			return ans;
		for (int j=1;j<branchPts.size();j++) {
			GenBrModPt gbp=branchPts.get(j);
			if ((gbp.myType==GenBrModPt.QUADFACE && 
					flink.containsV(gbp.myEdge.face.getVertIndx(j))>=0) ||
					(gbp.myType==GenBrModPt.SINGULAR && 
					flink.containsV(gbp.myEdge.face.faceIndx)>=0))
				ans.add(gbp.branchID);
		}
		return ans;
	}
	
	/**
	 * Find the circles which are branch circles for some branch point
	 * @return NodeLink, empty if none found
	 */
	public NodeLink getBPverts() {
		NodeLink nlink=new NodeLink(packData);
		if (branchPts==null || branchPts.size()<=1)
			return nlink;
		for (int j=1;j<branchPts.size();j++) {
			GenBrModPt gbp=branchPts.get(j);
			if (gbp.myType==GenBrModPt.CHAPERONE || 
					gbp.myType==GenBrModPt.TRADITIONAL)
				nlink.add(gbp.myEdge.origin.vertIndx);
		}
		return nlink;
	}
	
	/**
	 * Find the faces which are branch faces for some branch point
	 * @return FaceLink, empty if none found
	 */
	public FaceLink getBPfaces() {
		FaceLink flink=new FaceLink(packData);
		if (branchPts==null || branchPts.size()<=1)
			return flink;
		for (int j=1;j<branchPts.size();j++) {
			GenBrModPt gbp=branchPts.get(j);
			if (gbp.myType==GenBrModPt.SINGULAR || 
					gbp.myType==GenBrModPt.QUADFACE)
				flink.add(gbp.myEdge.face.faceIndx);
		}
		return flink;
	}
	
	/**
	 * Designed for use in study of generalized branching.
	 * Given a point 'pt', determine if it determines
	 * 'singular' (interstice) or 'chaperone' branching 
	 * (relative to 'refPack') and return appropriate 
	 * parameters: 
	 * 
	 * (1) Singular: return face index and barycentric coords b1, b2.
	 *     These are angle-based coords, as computed with the 'i' flag in 
	 *     BaryLink.
	 * 
	 * (2) For a circle, return jump indices and 'overlap' parameters. Use
	 *     the shadow arc 'pt' to determine situation. See "ChapClick.jpg"
	 *     for details.
	 * 
	 * (3) near to center, use traditional branch point
	 * 
	 * (4) TODO: near tangency point, use special structure: this, e.g.,
	 *     is common point of two interstices.
	 * 
	 * Return vector, length depending on situation:
	 *   [0] = 1 for interstice, 2 for circle, 3 for traditional,
	 *         TODO: 4 for intersection point of two interstices
	 *   [1] = index (face for interstice or circle for circle or traditional)
	 *   for traditional:
	 *      nothing further
	 *   for interstice:
	 *   	[2],[3] = bary coords b1, b2 for interstice
	 *   for chaperone
	 *      [2],[3] = jump petals (circle indices in refPack)
	 *      [4],[5] = overlaps in [0,1], depending on situation
	 *    
	 * @param refPack PackData    
	 * @param pt Complex
	 * @return double[2], double[4], or double[6], null on error 
	 */
	public double []getClickData(PackData refPack, Complex pt) {
		if (pt==null) 
			return null;
		double []ans=null;
		
		// first check if pt is inside a circle
		int v=NodeLink.grab_one_vert(refPack,"z "+pt.x+" "+pt.y);

		// not in circle, try interstice
		if (v<=0) { 
			// barycentric coords relative to angles, 
			//     see 'HyperbolicMath.ideal_bary'
			BaryPoint bpt=BaryLink.grab_one_barypoint(refPack,
					new String("-i "+pt.x+" "+pt.y));
			if (bpt!=null) {
				ans=new double[4];
				ans[0]=1.0;
				ans[1]=(double)bpt.face;
				ans[2]=bpt.b0;
				ans[3]=bpt.b1;
				return ans;
			}
			return null; // nope
		}

		// OK, now we are in a circle. 
		if (refPack.isBdry(v)) { // can't yet branch at boundary circle
			CirclePack.cpb.errMsg("Can't yet branch at boundary vertex v="+v);
			throw new DataException(v+" is a bdry vertex");
		}

		// Key variables are R, r, and shadow.
		int num=refPack.countFaces(v);
		int[] flower=refPack.getFlower(v);
		double R=refPack.getRadius(v);
		Complex cent=refPack.getCenter(v);
		Complex cent2pt=pt.minus(cent);
		double r=R-cent2pt.abs(); // ideally, radius of smaller sister when packed
		double c2pt_arg=cent2pt.arg();
		if (r<0) // error: not in the right circle
			throw new DataException("hum...?, selected point is not in circle "+v);
		
		// close to center of v? use traditional branch point
		// TODO: need to experiment to find best 'dcutoff'
		double dcutoff=.025;
		if ((R-r)<dcutoff*R) {
			ans=new double[2]; 
			ans[0]=3.0; // traditional
			ans[1]=(double)v;
			return ans;
		}

		// else prepare return data for circle case
		ans=new double[6];
		ans[0]=2.0; // circle
		ans[1]=(double)v; // circle index

		// closed list the arguments of edges from v to petals v_j
		
		//    TODO: If nearOne>=0, pt is very close to a tangency point, 
		//    may want to use singular branching or some special routine.
//		int nearOne=-1;
		double []petalAngs=new double[num+1]; // closed cycle of angles to petals
		for (int j=0;j<num;j++) {
			Complex c2pet=refPack.getCenter(flower[j]).minus(cent);
			petalAngs[j]=(c2pet.arg()+m2pi)%m2pi;
//			c2pet=c2pet.times(R/c2pet.abs());
//			if (c2pet.minus(cent2pt).abs()<.025*R) {
//				nearOne=flower[j];
//			}
		}
		petalAngs[num]=petalAngs[0]; // close up
		
		// "click" data for a point on the circle is a double c = J + F,
		//    where the integer part J is the index of the petal
		//    containing the point, and the F is the cclw fraction of
		//    angle the point occupies in that petal.
		// We get the upstream and downstream max and min data we can use
		ClickModValue centClick=
				petalClicks(c2pt_arg,petalAngs); // click data for pt itself
		ClickModValue maxUpClick=
				petalClicks(c2pt_arg-Math.PI/2.0,petalAngs); // pi/2 clw
		ClickModValue maxDownClick=
				petalClicks(c2pt_arg+Math.PI/2.0,petalAngs); // pi/2 cclw
		
		// move one petal upstream, one petal downstream
		ClickModValue minUpClick=
				new ClickModValue((centClick.petal-1.0+num)%num+
				centClick.fraction);
		ClickModValue minDownClick=
				new ClickModValue((centClick.petal+1.0)%num+
				centClick.fraction); 

		// get the 'shadow' cast by pt; this is half the angular 
		//   arc subtended by a circle through pt, perpendicular to 
		//   'cent2pt' and hitting the circle of radius R at right 
		//   angles (i.e., hyp geodesic, vis-a-vis this disc).
		// 'shadow' is in [0,pi/2].
		double shadow=HyperbolicMath.shadow_angle((R-r)/R); // geodesic half-length 

		// TODO: CAUTION, not at all sure about this (4/2016)
		// Use transform T to map real click data for 'pt' and 'shadow'
		//   monotonically to virtual click data used to find jumps and overlaps. 
		// On the upstream side: T: [0,pi/2] --> [minright, maxright].
		// On the downstream side: T: [0,pi/2] --> [minleft, maxleft].
		ClickModValue vertualUp=
				clickTransform(shadow,minUpClick,maxUpClick,num);
		ClickModValue vertualDown=
				clickTransform(shadow,minDownClick,maxDownClick,num);

		// jump and overlap depend on upstream or downstream
		int upj=(int)((vertualUp.petal+1)%num);
		ans[2]=flower[upj];
		ans[4]=1.0-vertualUp.fraction;
		
		int downj=(int)(vertualDown.petal+1)%num;
		ans[3]=flower[downj];
		ans[5]=vertualDown.fraction;
		
		return ans;
	}
	
	/**
	 * Transform x in [0,pi/2] into return T(x) in [mn,mx], 
	 * with T(0)=mn, T(pi/2)=mx. Note that mn may be less 
	 * than or greater than mx, so we may have to reverse.
	 * 
	 * TODO: Currently we use linear interpolation, but as 
	 * we learn more, we may be able to improve on this 
	 * for our purposes.
	 * 
	 * @param value double in [0,pi/2]
	 * @param mn ClickValue
	 * @param mx ClickValue
	 * @param num int, number of petals
	 * @return ClickValue
	 */
	public static ClickModValue clickTransform(double value,
			ClickModValue mn,ClickModValue mx,int num) {

		int fcount=(mx.petal-mn.petal+num)%num;
		double fracdiff=mx.fraction-mn.fraction;
		double comp=value*2.0/Math.PI;
		double result=((double)fcount+fracdiff)*comp;
		int newpetal=((int)Math.floor(result)+num)%num;
		return new ClickModValue(newpetal+result-Math.floor(result));
	}
	
	// OBE: I've remove 'old_getClickData'
	
	/**
	 * Given argument 'spot' and closed list of arguments to petals 
	 * {v_1, v_2, ... v_n, v_1}, return double j+f where j is the 
	 * petal index 'spot' lies in (i.e., spot is between the arguments 
	 * for v_j and v_{j+1}) and f is the proportion of that petal 
	 * that 'spot' takes up.
	 * @param spot double: arg in [-2pi,2pi], adjusted to [0,2pi)
	 * @param args double[]: closed list of petal direction arguments
	 * @return ClickValue, null on error
	 */
	public static ClickModValue petalClicks(double spot,double []args) {
		int num=args.length-1;
		spot=(spot+m2pi)%m2pi;
		for (int j=0;j<num;j++) {
			double a=(spot-args[j]+m2pi)%m2pi;
			double ac=(spot-args[j+1]+m2pi)%m2pi;
			if (a>=0 && ac>Math.PI) {
				double diff=(args[j+1]-args[j]+m2pi)%m2pi;
				return new ClickModValue(j+a/diff);
			}
		}
		return null;
	}
	
	/**
	 * Given 'myang' (adjusted to lie in [0,2pi)) and array of 
	 * angles in faces of a flower, return 'pseudoangle', which
	 * is pi times the number of face angles myang covers completely 
	 * plus the proportion (of pi) that myang covers in the next
	 * face. The pseudoangle is used in two different ways for
	 * the two chaperones for a chaperone branch point.
	 * @param myang double, target angle
	 * @param angs
	 * @return double pseudoangle
	 */
	public double pseudoAngle(double myang,double []angs) {
		double pi2=2.0*Math.PI;
		
		// make sure myang is in [0,2pi].
		while (myang<0)
			myang +=pi2;
		while (myang>pi2)
			myang -=pi2;
		
		int k=angs.length;
		double accumAng=0.0;
		int tick=0;
		double incr=0.0;
		while (tick<k && myang>accumAng) {
			incr=angs[tick];
			if (myang<=(accumAng+incr)) 
				return ((double)tick+(myang-accumAng)/incr)*Math.PI; 
			tick++;
			accumAng +=incr;
		}
		
		return ((double)k)*Math.PI; // myang should ~ full anglesum
	}
	
	/**
	 * Install a new branch point
	 * parameters. 
	 * @param bpt GenBrModPt
	 * @return int
	 */
	public int installBrPt(GenBrModPt bpt) {
		if (bpt!=null && bpt.success) {
			msg(bpt.reportExistence());
			branchPts.add(bpt);
				
			// recompute layout of 'packData'
			setLayoutOrder(); 
				
			// renew colors and parameters
			bpt.renew();
			return bpt.myType;
		}
		return 0;
	}
	
	/**
	 * Dismantle and then delete a branch point
	 * @param bpID
	 * @return 0 on error or not found
	 */
	public int deleteBP(int bpID) {
		int indx=findBPindex(bpID);
		if (indx<=0)
			return 0;
		
		// reset 'packData'
		GenBrModPt gbp=branchPts.remove(indx);
		gbp.dismantle();
		
		this.setLayoutOrder();
		
		return indx; // should be >= 1
	}
	
	/**
	 * Throw out all generalized branch points and revert
	 * to the original packing held in 'refPack'.
	 */
	public void revert() {
		branchPts=new Vector<GenBrModPt>();
		branchPts.add(null); // first spot empty
		exclusions=new ArrayList<Vertex>();
		PackData newCopy=refPack.copyPackTo();
		CirclePack.cpb.swapPackData(newCopy,packData.packNum,true);
		packData.set_aim_default();
		packData.set_invD_default();
	}
		
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("event",null,null,
				"append event horizon vertices to packing's 'vlist'"));
		cmdStruct.add(new CmdStruct("'Comment'","-b{b}",null,
				"first flag '-b' designates a branch point ID number"));
		cmdStruct.add(new CmdStruct("disp","-[shyj] {usual}",null,
				"display on parents packing. For chaperone: s=sisters, "+
				"h=chaperones, y=putative branch point, "+
						"j=jumps. Also, {usual} display options"));
		cmdStruct.add(new CmdStruct("status","-b{b}",null,
				"report the status of branch point 'b'"));
		cmdStruct.add(new CmdStruct("angsum_err",null,null,
				"report the l^2 anglesum error of parent and "+
						"all branch points"));
		cmdStruct.add(new CmdStruct("get_param",null,null,
				"report branch point parameters"));
		cmdStruct.add(new CmdStruct("click","-[xX] z [-a {x}] ",null,
				"create a chaparone or a singular branch point "+
						"(as appropriate) at the "+
				"point z (relative to 'refPack'); '-x' flag means "+
						"to remove other nearby "+
				"branch points; '-X' remove all others; -a set aim to x*Pi"));
		cmdStruct.add(new CmdStruct("reset_over","o1 o2",null,
				"For resetting overlaps for 'singular' and 'chaperone' "+
						"branch points"));
		cmdStruct.add(new CmdStruct("delete","-b{b}",null,
				"delete branch point 'b'"));
		cmdStruct.add(new CmdStruct("set_param","{param list}",null,
				"Set parameters for branch point, format depends on type: "+
				"sing '-a {a} -o {o1 o2}'; "+
				"chap '-a {a} -j {w1 w2} -o {o1 o2}'"));
		cmdStruct.add(new CmdStruct("copy","{pnum}",null,
				"write 'refPack' into designated packing"));
		cmdStruct.add(new CmdStruct("revert",null,null,
				"revert to the original unbranched packing 'refPack'"));
		
		// creating branch point types:
		cmdStruct.add(new CmdStruct("bp_trad","-a {a} -i {v}",null,
				"Create 'traditional' branch point, aim 'a', vert 'v'."));
		cmdStruct.add(new CmdStruct("bp_sing",
				"-a {a} -i {f} -o {o1 o2} [-b {blist}]",null,
				"Create 'singular' branch point, aim 'a'; face 'f'; "+
				"overlaps 'o1', 'o2' in [0,1], o1+o2 in [0,1]. "+
				"'blist' is 'BaryLink' option for face and overlaps."));
		cmdStruct.add(new CmdStruct("bp_chap",
				"-a {a} -i {v} -j {w1 w2} -o {o1 o2}",null,
				"Create 'chaperone' branch point, aim 'a', vert 'v'; "+
				"optional jump vertices, petals 'w1' 'w2', "+
				"overlap parameters 'o1', 'o2' in [0,1]."));

	}

}

/**
 * Utility class: 'spot' in [0,2pi] has integer part 'petal' (an index
 * to petals of some flower) and 'fraction', the cclw fraction of the
 * petal's opening that spot occupies.
 * @author kens (2016)
 */
class ClickModValue {
	public int petal;
	public double fraction;
	
	// Constructor
	public ClickModValue(double spot) {
		petal=(int)Math.floor(spot);
		fraction=spot-petal;
	}
}
