package ftnTheory;

import geometry.HyperbolicMath;
import geometry.CircleSimple;
import input.CPFileManager;

import java.io.BufferedWriter;
import java.util.Iterator;
import java.util.Vector;

import komplex.AmbiguousZ;
import komplex.DualGraph;
import komplex.EdgeSimple;
import listManip.BaryLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.PointLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.StringUtil;
import util.UtilPacket;
import allMains.CPBase;
import allMains.CirclePack;
import baryStuff.BaryPoint;
import branching.ChapBranchPt;
import branching.FracBranchPt;
import branching.GenBranchPt;
import branching.QuadBranchPt;
import branching.ShiftBranchPt;
import branching.SingBranchPt;
import branching.TradBranchPt;

import complex.Complex;

import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.ParserException;

/**
 * Generalized branch points are small subregions of circle packing
 * complexes which are structured to support branching-like behavior 
 * via various non-standard packing procedures. 
 * 
 * "Traditional" branching is very rigid, meaning that discrete objects
 * cannot adequately mimic classical behavior. This is especially true
 * in the case of multiply connected combinatorics. The goal is to 
 * provide parameters which allow global coherent behavior by accepting 
 * small local islands of incoherence. 
 * 
 * Among the methods currently available are "singular" and "fractured" 
 * branching in faces, "shifted" and "chaperone" branching in circles,
 * and "quad" branching in pairs of faces. Others options may be developed,
 * but the main ones now are "singular" (replacing "fractured" and 
 * "chaperone" (replacing, but sometimes referred to as, "shifted");
 * "quad" hasn't been fully developed and may not be needed. 
 * 
 * For general background, see Ashe's thesis and experiments.
 * 
 * @author Ken Stephenson, James Ashe, Edward Crane, started 5/2012.
 *
 */
public class GenBranching extends PackExtender {
	
	public PackData refPack;  // reference copy of original packing
	
	Vector<GenBranchPt> branchPts; // ignore 0 index, start with 1
	public static final double LAYOUT_THRESHOLD=.00001; // for layouts based on quality
	FaceLink parentBorder;
	
	GraphLink layoutTree;  // tree for parent layout
	int []vertTracker;  // who is laying out each vertex? branchID for those interior 
						//   to branch point
	int []faceTracker;  // who is doing each face? branchID if face is in bdryLink,
	public static double m2pi=2.0*Math.PI;

	// Constructor
    public GenBranching(PackData p) {
		super(p);
		extensionType="GENERALIZED_BRANCHING";
		extensionAbbrev="GB";
		toolTip="'Generalized_Branching' provides methods for incorporating various "+
		  	"generalized branched points into the parent circle packing. "+
				"(For purposes of interaction, a copy of the original parent is "+
				"held in 'refPack'.)";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
			packData.poisonEdges=null;
			packData.poisonVerts=null;
		}
		
		parentBorder=new FaceLink(packData,"Ra");
		parentBorder.add(parentBorder.getFirst());
		branchPts=new Vector<GenBranchPt>(3);
		branchPts.add((GenBranchPt)null); // make index 0 empty; numbering is from 1
		layoutTree=parentLayout();
		
		refPack=packData.copyPackTo(); // maintains original data and geometry
	}

    /**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		int count=0;
		GenBranchPt cmdBranchPt=null; // designated branch point for this command?
		
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
					
					// want 'delete' command to go through even if no branch point
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
			throw new ParserException("parse error with branch ID: "+ex.getMessage());
		}
		// ---------------------------------------------------
		
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
					case 'X':
					{
						fullWipe=true;
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
				
				// here's key routine: find out type of branching and parameters
				double []data=getClickData(pt);
				
				int mode=(int)data[0];
				int indx=(int)data[1];
				NodeLink nlink=null;
				FaceLink flink=null;
								
				// handle wipeouts first
				if (mode<0 || mode>3 || indx<=0 || indx>packData.nodeCount) 
					return 0;

				// wipe out contiguous branch points (including, possibly the current circle)
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
					packData.poisonEdges=null;
					packData.poisonVerts=null;
					branchPts=new Vector<GenBranchPt>(3);
					branchPts.add((GenBranchPt)null);
					packData.setCombinatorics();
					packData.set_aim_default();
					packData.free_overlaps();
				}
				
				// circle?
				if (mode==2) { // data[2]/[3] are the jump petals, data[4]/[5] the overlaps
					ChapBranchPt cbp=new ChapBranchPt(packData,branchPts.size(),theAim*Math.PI,indx,
						(int)data[2],(int)data[3],data[4],data[5]);
					msg(cbp.reportExistence());
					branchPts.add(cbp);
				} 

				// traditional branch point
				else if (mode==3) { // traditional branch point
					TradBranchPt tbp=new TradBranchPt(packData,branchPts.size(),theAim*Math.PI,indx);
					msg(tbp.reportExistence());
					branchPts.add(tbp);
				}

				// interstice?
				else if (mode==1) { // find data and create 
					SingBranchPt sbp=new SingBranchPt(packData,branchPts.size(),theAim*Math.PI,indx,data[2],data[3]); 
					msg(sbp.reportExistence());
					branchPts.add(sbp);
				}
				else 
					return 0;
				
				// get layoutTree, use it to set parent's drawing order
				layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
				DualGraph.tree2Order(packData,layoutTree);
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
				CPScreen cpS=CPBase.pack[pnum];
				PackData newCopy=refPack.copyPackTo();
				for (int v=1;v<=newCopy.nodeCount;v++)
					newCopy.kData[v].plotFlag=1;
				if (cpS!=null) {
					return cpS.swapPackData(newCopy,false);
				}
			} catch (Exception ex) {
				return 0;
			}
		}	
		// =========== holonomy ===================
		if (cmd.startsWith("holon")) {
			// Idea is to use parentBorder to check holonomy of full packing
			double frobNorm=PolyBranching.holonomy_trace(packData, null, parentBorder, false);
			if (frobNorm<0)
				return 0;
			return 1;
		}
		
		// =========== delete =====================
		if (cmd.startsWith("dele")) {
			if (cmdBranchPt==null) {
				if (branchPts.size()>1)
					throw new ParserException("branch pt wasn't specified.");
				return 1;
			}
			
			// otherwise, delete
			cmdBranchPt.delete();
			branchPts.remove(cmdBranchPt);

			// reset parent's drawing order
			layoutTree=parentLayout();
			DualGraph.tree2Order(packData,layoutTree);
			count++;
		}
		
		// ============= export ===============
		else if (cmd.startsWith("export")) {
			if (cmdBranchPt==null) {
				if (branchPts!=null && branchPts.size()>1)
					cmdBranchPt=branchPts.get(1);
				if (cmdBranchPt==null)
					return 0;
			}
			boolean script_flag=false;
			String name=new String("branch"+cmdBranchPt.branchID+".p");
			String str=null;
			try {
				items=flagSegs.get(0);
				str=items.get(0);
				if (StringUtil.isFlag(str)) {
					if (str.startsWith("-s")) 
						script_flag=true;
					else {
						int pk=StringUtil.qFlagParse(str);
						if (pk>=0 && pk!=packData.packNum) {
							CPScreen cpS=CPBase.pack[pk];
							if (cpS!=null && cmdBranchPt.getMyPackData().status) {
								PackData qdata=cmdBranchPt.getMyPackData().copyPackTo();
								return cpS.swapPackData(qdata,false);
							}
						}
					}
					items.remove(0);
					if (items.size()>0) // get name at end
						name=items.get(0);
				}
				else // just name
					name=new String(str);
			} catch (Exception ex) {
				name=new String("local"+cmdBranchPt.branchID+".p");
			}
			BufferedWriter fp = CPFileManager.openWriteFP(CPFileManager.PackingDirectory,
					false, name, script_flag);
			try {
				cmdBranchPt.getMyPackData().writePack(fp, 020017, false); 
				fp.flush();
				fp.close();
			} catch (Exception ex) {
				throw new InOutException("write of branch packing failed");
			}
	    	if (script_flag) {
	    		CPBase.scriptManager.includeNewFile(name);
	    		CirclePack.cpb.msg("Wrote "+name+" to script");
	    	}
			else 
				CirclePack.cpb.msg("Wrote '"+name+"' to "+CPFileManager.PackingDirectory);
			return ++count;
		}
		
		// =========== send data ==================
		else if (cmd.startsWith("sendmy")) {
			// designated?
			if (cmdBranchPt!=null) {
				cmdBranchPt.sendMyCenters();
				cmdBranchPt.sendMyRadii();
				count++;
			}
			// else all
			else { 
				for (int b=1;b<branchPts.size();b++) {
					GenBranchPt gbp=branchPts.get(b);
					gbp.sendMyCenters();
					gbp.sendMyRadii();
					count++;
				}
			}
		}
		
		// ========== reset_overlaps =========
		else if (cmd.startsWith("reset_o")) {
		
			if (cmdBranchPt==null || (cmdBranchPt.myType!=GenBranchPt.CHAPERONE &&
					cmdBranchPt.myType!=GenBranchPt.SINGULAR))
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
			
			if (cmdBranchPt.myType==GenBranchPt.CHAPERONE) {
				ChapBranchPt cbp=(ChapBranchPt)cmdBranchPt;
				cbp.resetOverlaps(ovlp[0],ovlp[1]);
			}
			else {
				SingBranchPt sbp=(SingBranchPt)cmdBranchPt;
				sbp.resetOverlaps(ovlp[0],ovlp[1]);
			}
			return 1;
		}
		
		// =========== repack =====================
		else if (cmd.startsWith("repack")) {
			
			// one argument, number of iterations
			int cycles=100;
			try {
				items=flagSegs.get(0);
				cycles=Integer.parseInt(items.get(0));
			} catch (Exception ex) {}
			
			// repack designated branch point
			if (cmdBranchPt!=null) {
				UtilPacket uP=cmdBranchPt.riffleMe(cycles);
				if (uP.rtnFlag>=0) {
//					CirclePack.cpb.msg("Repacked branch point "+cmdBranchPt.branchID+"; error = "+uP.value);
					count++;
				}
			}
			// else, repack all branch points 
			else {
				// first, repack all branch points, send their radii to parent
				for (int b=1;b<branchPts.size();b++) {
					GenBranchPt gbp=branchPts.get(b);
					UtilPacket uP=gbp.riffleMe(cycles);
					if (uP.rtnFlag>=0) {
//						CirclePack.cpb.msg("Repacked branch point "+gbp.branchID+"; error = "+uP.value);
						count++;
					}
				}
			}
			return count;
		}
		
		// =========== Repack =====================
		else if (cmd.startsWith("Repack")) {
			double currErr=1.0;
			double prevErr=1.0;
			
			// there is just one argument, max iterations
			int parentcycles=10;
			int cycles=100;
			try {
				items=flagSegs.get(0);
				cycles=Integer.parseInt(items.get(0));
			} catch (Exception ex) {}
			
			// ping-pong between branch point repacking and parent
			//   repacking, exchanging radii in between steps.
			int safety=0;
			int docount=1;
			while (docount>0 && currErr>PackData.OKERR && safety<cycles) {
				docount=0;
				safety++;
				currErr=0.0;
					
				// first, repack all branch points, send their radii to parent
				for (int b=1;b<branchPts.size();b++) {
					GenBranchPt gbp=branchPts.get(b);
					UtilPacket uP=gbp.riffleMe(-1);
					if (uP.rtnFlag>=0) {
//						CirclePack.cpb.msg("Repacked branch point "+gbp.branchID+"; error = "+uP.value);
						currErr += gbp.currentError();
						docount++;
					}
//					msg("Repack branch points "+safety);
				}
				
				// repack parent (don't use C library) 
				//    (this should not change interior info of branch points)
//				count+=
				packData.fillcurves();
				int rc=packData.repack_call(parentcycles,true,false);
				if (rc>0) {
					docount++;
					currErr += packData.angSumError();
//					msg("Repack parent "+safety);
				}
				
				// try to prevent unhelpful runs
				if ((prevErr-currErr)> 10*PackData.OKERR) {
					prevErr=currErr;
				}
				else 
					safety *= 2; // cut down the cycles
					
					
			} // end of while

			
			// any success?
			if (safety>0) {
				msg("Repacking: "+safety+" cycles, error = "+currErr);
				count=1+docount;
			}
			else {
				msg("Repacking did not seem to do anything");
				count=0;
			}
		}
		
		// =========== layout =================
		else if (cmd.startsWith("layout")) {
			// lay out specified or all branch points in their normalized positions.
			int id=-1;
			try {
				if (cmdBranchPt!=null) {
					id=cmdBranchPt.branchID;
					cmdBranchPt.layout(true);
					count++;
				}
				else for (int b=1;b<branchPts.size();b++) {
					GenBranchPt gbp=branchPts.get(b);
					id=gbp.branchID;
					gbp.layout(true);
					count++;
				}
			} catch (Exception ex) {
				Oops("problem laying out branch point "+id);
			}
		}
		
		// =========== Layout =================
		else if (cmd.startsWith("Layout")) {
			
			int layoutCount=0;
			try {
				layoutCount=combinedLayout();
				count++;
			} catch (Exception ex) {
				errorMsg("Parent seems to have no layout; lay out the branch point(s) alone");
			}
			if (layoutCount<=0) {
				try {
					for (int b=1;b<branchPts.size();b++) {
						GenBranchPt gbp=branchPts.get(b);
						gbp.layout(true);
						count++;
					}
				} catch (Exception e) {
					throw new InOutException("problem in branch point layouts");
				}
			}
			
			// if layout of parent seems to succeed
			else {
				try {
					for (int b=1;b<branchPts.size();b++) {
						GenBranchPt gbp=branchPts.get(b);
						gbp.placeMyCircles();
						count++;
					}
				} catch (Exception e) {
					throw new InOutException("problem in branch point layouts");
				}
			}
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
				if (ans<0) { 
					// get layoutTree, use it to set parent's drawing order
					layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
					DualGraph.tree2Order(packData,layoutTree);
				}
			} catch (Exception ex) {
				Oops("Branch ID="+cmdBranchPt.branchID+", set parameters failed: "+ex.getMessage());
			}
			return Math.abs(ans);
		}
		
		// =========== get_parameters =================
		else if (cmd.startsWith("get_para")) {
			if (cmdBranchPt==null) {
				if (branchPts!=null || branchPts.size()==1) {
					CirclePack.cpb.msg(branchPts.get(1).getParameters());
					return 1;
				}
				return 0;
			}
			CirclePack.cpb.msg(cmdBranchPt.getParameters());
			return 1;
		}
		
		// ============== set_G =========================
		else if (cmd.startsWith("set_G")) {
			if (layoutTree!=null) {
				CPBase.Glink=layoutTree.makeCopy();
				return CPBase.Glink.size();
			}
			else 
				CPBase.Glink=null;
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
			double sumsq = tmp*tmp;
			count++;
			for (int b=1;b<branchPts.size();b++) {
				tmp=branchPts.get(b).getAngSumError();
				sumsq += tmp*tmp;
			}
			sumsq=Math.sqrt(sumsq);
			msg("anglesum l^2 error = "+sumsq);
		}
		
		// =========== create various branch types =============
		// ============ traditional
		else if (cmd.startsWith("bp_trad")) { // type=1
			try {
				double aim=Double.parseDouble(items.get(0));
				int v=Integer.parseInt(items.get(1));
				int bindx=brptExists(v,GenBranchPt.TRADITIONAL);
				TradBranchPt tbp=null;
				// already exists?
				if (bindx>=0) { 
					// nothing to do here
				}
				// else create
				else {
					tbp=new TradBranchPt(packData,branchPts.size(),aim*Math.PI,v);
					msg(tbp.reportExistence());
					branchPts.add(tbp);
					
					// get layoutTree, use it to set parent's drawing order
					layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
					DualGraph.tree2Order(packData,layoutTree);
				}
				count=GenBranchPt.TRADITIONAL;
			} catch (Exception ex) {
				throw new ParserException("'traditional' usage: v aim");
			}
		}
		// ============ fractured
		else if (cmd.startsWith("bp_frac")) { // type=2
			try {
				double aim=Double.parseDouble(items.get(0));
				int f=Integer.parseInt(items.get(1));
				int bindx=brptExists(f,GenBranchPt.FRACTURED);
				FracBranchPt fbp=null;
				// already exists?
				if (bindx>=0) { 
					// nothing to do here
				}
				// else create
				else {
					fbp=new FracBranchPt(packData,branchPts.size(),f,aim*Math.PI);
					msg(fbp.reportExistence());
					branchPts.add(fbp);

					// get layoutTree, use it to set parent's drawing order
					layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
					DualGraph.tree2Order(packData,layoutTree);
				}				
				count=GenBranchPt.FRACTURED;
			} catch (Exception ex) {
				throw new ParserException("'fractured' usage: f aim");
			}
		}
		// ============ quad face
		else if (cmd.startsWith("bp_quad")) { // type=3
			try {
				double aim=Double.parseDouble(items.get(0));
				int f=Integer.parseInt(items.get(1));
				int g=Integer.parseInt(items.get(2));
				int bindx=brptExists(f,GenBranchPt.QUADFACE); // TODO: need to check g as well
				QuadBranchPt qbp=null;
				// already exists?
				if (bindx>=0) { 
					// nothing to do here yet
				}
				// else create
				else {
					qbp =new QuadBranchPt(packData,branchPts.size(),f,g,aim*Math.PI);
					msg(qbp.reportExistence());
					branchPts.add(qbp);

					// get layoutTree, use it to set parent's drawing order
					layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
					DualGraph.tree2Order(packData,layoutTree);
				}
				count=GenBranchPt.QUADFACE;
			} catch (Exception ex) {
				throw new ParserException("'quadface' usage: f g aim");
			}
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
						throw new ParserException("bp_sing now requires flags -[abfo]");
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
						// bary coords are angle based appropriate to interstice (not to face itself)
						getO1=(1-bp.b0)/2.0;
						getO2=(1-bp.b1)/2.0;
						count++;
						break;
					}
					case 'f': // face
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
					case 'X':  // wipe out old
					{
						packData.poisonEdges=null;
						packData.poisonVerts=null;
						branchPts=new Vector<GenBranchPt>(3);
						branchPts.add((GenBranchPt)null);
						packData.setCombinatorics();
						packData.set_aim_default();
						packData.free_overlaps();
					}
					} // end of switch

				} // end of while
			} catch(Exception ex) {
				throw new ParserException("usage: -a {a} -f {f} -o {o1 o2} [-b {blist}]. "+ex.getMessage());
			}
			
			if (getF<1 || getF>packData.faceCount)
				throw new ParserException("singular face missing or inappropriate");
			
			int bindx=brptExists(getF,GenBranchPt.SINGULAR);
			SingBranchPt sbp=null;
			// already exists?
			if (bindx>=0) { 
				sbp=(SingBranchPt)branchPts.get(bindx);
				sbp.setParameters(StringUtil.flagSeg("-o "+getO1+" "+getO2));
			}
			// else create
			else {
				sbp=new SingBranchPt(packData,branchPts.size(),
					getAim,getF,getO1,getO2);
				msg(sbp.reportExistence());
				branchPts.add(sbp);
					
				// get layoutTree, use it to set parent's drawing order
				layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
				DualGraph.tree2Order(packData,layoutTree);
			}
			count=GenBranchPt.SINGULAR;
		}

		// ============ shift
		else if (cmd.startsWith("bp_shift")) { // type=5
			try {
				double aim=Double.parseDouble(items.get(0));
				int v=Integer.parseInt(items.get(1));
				int w=Integer.parseInt(items.get(2));
				ShiftBranchPt sbp=new ShiftBranchPt(packData,branchPts.size(),v,w,aim*Math.PI);
				msg(sbp.reportExistence());
				branchPts.add(sbp);
				count=GenBranchPt.SHIFTED;
				
				// get layoutTree, use it to set parent's drawing order
				layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
				DualGraph.tree2Order(packData,layoutTree);
				
			} catch (Exception ex) {
				throw new ParserException("'shifted' usage: v w aim");
			}
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
						throw new ParserException("missing flag -[ajov] or -z {x y} ");
					String sstr=items.remove(0);
					switch (sstr.charAt(1)) {
					case 'a': // new aim
					{
						getAim=Double.parseDouble(items.get(0));
						count++;
						break;
					}
					case 'j': // jump petals (as vertex indices from parent)
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
					case 'v': // vertex
					{
						NodeLink nl=new NodeLink(packData,items);
						getV=nl.get(0);
						count++;
						break;
					}
					case 'X':  // wipe out old
					{
						packData.poisonEdges=null;
						packData.poisonVerts=null;
						branchPts=new Vector<GenBranchPt>(3);
						branchPts.add((GenBranchPt)null);
						packData.setCombinatorics();
						packData.set_aim_default();
						packData.free_overlaps();
					}
					} // end of switch
				
					// TODO: may want to accommodate more jumps/overlaps in future,
					//       depending, e.g., on 'myAim'.
				} // end of while
			} catch(Exception ex) {
				throw new ParserException("usage: -a {a} -v {v} -j {j1 j2} -o {o1 o2}. "+ex.getMessage());
			}
			
			if (getV<1 || getV>packData.nodeCount) // packData.vert_isPoison(1);
				throw new ParserException("chaperone vertex missing or inappropriate");
			if (getW1<0 || getW2<0) {
				getW1=packData.kData[getV].flower[1];
				getW2=packData.kData[getV].flower[3];
			}
			
			ChapBranchPt cbp=new ChapBranchPt(packData,branchPts.size(),getAim*Math.PI,
					getV,getW1,getW2,getO1,getO2);

			if (cbp!=null) {
				msg(cbp.reportExistence());
				branchPts.add(cbp);
				count=GenBranchPt.CHAPERONE;
					
				// get layoutTree, use it to set parent's drawing order 
				layoutTree=parentLayout(); //  CPBase.Glink=layoutTree.makeCopy(); cpCommand("disp -w -ddc20t3 Glist");
				DualGraph.tree2Order(packData,layoutTree);  // DualGraph.printGraph(layoutTree);
				count++;
			}
		}
		return count;
	}

	/**
	 * We start with a normal layout tree and drawing order for 'packData',
	 * but then we modify it to keep branch points whole. Careful: plotFlag's may
	 * be reset.
	 */
	public GraphLink parentLayout() {
		// create typical full layout to get appropriate red chain to use below
		if (packData.firstFace<1 || packData.firstFace>packData.faceCount)
			packData.firstFace=1;
		GraphLink gl=DualGraph.buildDualGraph(packData,packData.firstFace,null); // DualGraph.printGraph(gl);
		packData.dualGraph=branchSpanner(gl,packData.firstFace); // DualGraph.printGraph(packData.dualGraph);
		DualGraph.tree2Order(packData,packData.dualGraph);
		
		// need poison edges; start with outer edges of redChain poison
		packData.poisonEdges=new EdgeLink(packData,"Ra"); // cpCommand("disp -ec20t8 P");

		// debug: see the poison edges with cpCommand("disp -ec198t8 P");
		
		boolean debug=false; // debug=true;
		if (debug) {
			CPBase.Glink=packData.dualGraph.makeCopy();
			cpCommand("disp -ddc20t8 Glist");
		}
		
		// clear out branch point 'attachFace' entries, get their poison edges
		for (int b=1;b<branchPts.size();b++) {
			GenBranchPt gbp=branchPts.get(b);
			gbp.setAttachFace(null);
			packData.poisonEdges.abutMore(gbp.parentPoison);
		}

		// identify ends of poison edges as poison vertices, used to find firstface
		packData.poisonVerts=new NodeLink(packData,"Ie P"); // cpCommand("disp -cf P");
		
		// set parent's firstFace: want it (if possible) to be interior, with no 
		//    poison vertices. Try in order:
		//  (1) current 'firstFace'
		//  (2) containing alpha 
		//  (3) farthest from poison and boundary
		//  (4) any 'faceOK'
		//  (5) current 'firstFace' (last resort)
		int firstFace=-1;
		if (faceOK(packData.firstFace))
			firstFace=packData.firstFace;
		if (firstFace<=0 || packData.firstFace>packData.faceCount) {
			int farV=packData.alpha;
			// try to use some face containing 'alpha'
			for (int kk=0;(kk<packData.kData[farV].num && firstFace<0);kk++) {
				int f=packData.kData[farV].faceFlower[kk];
				if (faceOK(f))
					firstFace=f;
			}

			// no luck, try some face far from poison and boundary
			if (firstFace<0) {
				NodeLink sa=new NodeLink(packData,"P b");
				farV=packData.gen_mark(sa,-1,false);
				if (farV>0) {
					for (int kk=0;(kk<packData.kData[farV].num && firstFace<0);kk++) {
						int f=packData.kData[farV].faceFlower[kk];
						if (faceOK(f))
							firstFace=f;
					}
				}
				// next, try any face
				if (firstFace<0) {
					for (int ff=1;(ff<=packData.faceCount && firstFace<0);ff++) {
						if (faceOK(ff))
							firstFace=ff;
					}
				}
				// nothing yet? just stick with current
				if (firstFace<0) {
					firstFace=packData.firstFace;
				}
			}
		}
		
		// now find the dual spanning graph 
		// TODO: note this will treat the branch regions as islands and include
		//       them in the red chain --- we want old redchain.
		GraphLink dG=DualGraph.buildDualGraph(packData,firstFace,packData.poisonEdges);
		GraphLink ans=null;
		try {
			ans=DualGraph.drawSpanner(packData,dG,firstFace); // CPBase.Glink=dG.makeCopy();cpCommand("disp -w -ddc190t4 Glist");
		} catch (Exception ex) {
			msg("dual spanning graph for the parent is null");
		}
		return ans; // CPBase.Glink=ans.makeCopy();cpCommand("disp -w -ddc190t4 Glist");
	}

	/** 
	 * Check if face f is free of boundary or poison vertices
	 * @param f int
	 * @return boolean
	 */
	public boolean faceOK(int f) {
		for (int i=0;i<3;i++) {
			int v=packData.faces[f].vert[i];
			if (packData.kData[v].bdryFlag!=0 || packData.poisonVerts.containsV(v)>=0)
				return false;
		}
		return true;
	}
	
	/**
	 * First, the parent positions most circles using the layout tree, then
	 * the branch points place any circles they're responsible for. After that,
	 * things are normalized --- first the parent, then the branch circles.
	 * @return int, count of actions
	 */
	public int combinedLayout() {
		int count=0;
		if (layoutTree==null) { // CPBase.Glink=layoutTree.makeCopy();cpCommand("disp -w -f -ddc180t2 Glist");
			throw new ParserException("do custom layout first");
		}
		
		// find and place the first face
		EdgeSimple edge=layoutTree.get(0); // this should be root
		if (edge.v!=0)
			throw new DataException("first entry of layoutTree is not a root");
		int f=edge.w; // root face
		edge=layoutTree.get(1);
		int indx=(packData.face_nghb(edge.w, f)+2)%3; // if next is (f,g), use info on g
		int pl=packData.place_face(f,indx);
		if (pl==0)
			throw new CombException("error plotting root face "+f);
		
		Iterator<EdgeSimple> gk=layoutTree.iterator();
		edge=gk.next(); // get rid of root
		while(gk.hasNext()) {
			edge=gk.next();
			int currf=edge.v;
			int nextf=edge.w;
			indx=packData.face_nghb(currf,nextf);
			if (!packData.layByFaces(currf,nextf))
				throw new DataException("error in layByFace, "+edge.v+" "+edge.w);
			count++;
		} // end of while

		// position the branch points
		for (int b=1;b<branchPts.size();b++) {
			GenBranchPt gbp=branchPts.get(b);
			gbp.placeMyCircles();
			count++;
		}
		
		// normalize
		Complex az=packData.getCenter(packData.alpha);
		Complex gz=packData.getCenter(packData.gamma);
		double agdist=az.minus(gz).abs();
		Mobius mob=null;
		if (agdist>=0.00001) {
			if (packData.hes<0) 
				mob=Mobius.standard_mob(az,gz);
			else 
				mob=Mobius.affine_mob(az,gz,new Complex(0.0),new Complex(0.0,agdist));
		}
		if (mob!=null) {
			for (int v=1;v<=packData.nodeCount;v++)
				packData.setCenter(v,mob.apply(packData.getCenter(v)));
			for (int b=1;b<branchPts.size();b++) {
				GenBranchPt gbp=branchPts.get(b);
				gbp.applyMob(mob);
				count++;
			}
		}
			
		return count;
		
	}
	
	/**
	 * Specialized version of 'DualGraph.drawSpanner' which scoops up branch
	 * points as it encounters them. Basic idea is to pick up faces in generational 
	 * order, but when we encounter any branch point for first time, pick up 
	 * max connected subtree of dual edges in its 'bdryLink'.
	 * Calling routine may want to choose 'startface' --- if not valid, search for one.
	 * @param p @see PackData
	 * @param graph @see GraphLink
	 * @param startface int, first face to use.
	 * @return
	 */
	public GraphLink branchSpanner(GraphLink graph,int startface) {
		
		boolean debug=false;
		if (graph==null || graph.size()==0)
			throw new ParserException("problem with graph or node "+startface);

		int []bfaces=new int[packData.faceCount+1];
		
		// mark all 'bdryLink' faces
		for (int b=1;b<branchPts.size();b++) {
			Iterator<Integer> bl=branchPts.get(b).bdryLink.iterator();
			while (bl.hasNext()) {
				bfaces[bl.next()]=b;
				if (debug) // debug=true;
					System.err.println("next b: "+b);
			}
			
		}

		// invalid starting face; choose one.
		// TODO: need more rational choice
		if (!graph.nodeExists(startface)) {
			startface=graph.get(0).w;
		}
		
		// keep track of:
		//  * vgens[]: generation of circles, 1 means is in startface
		//  * futil[]: generation of faces (lowest generation of its vertices)
		//  * currFaceGen: which generation is being done now?
		//  * currFaces: lowest gen faces with placed partners
		
		int count=0; // faces processed
		
		// store generations of verts, those of startface are 1.
		for (int v=1;v<=packData.nodeCount;v++) {
			packData.kData[v].utilFlag=0;
			// TODO: commented out changes in vertex plotFlags on 5/23/13. 
			//       Don't know why this was needed.
//			packData.kData[v].plotFlag=0;
		}
		
		// set startface vertices at generation 1
		for (int j=0;j<3;j++) {
			int k=packData.faces[startface].vert[j];
			packData.kData[k].utilFlag=1;
//			packData.kData[k].plotFlag=1;
		}
		
		// get generations of the other vertices
		util.UtilPacket uP=new util.UtilPacket();
		int []vgens=packData.label_generations(-1,uP);
		
		//  store generation of faces (lowest generation of vertices) 
		int []futil=new int[packData.faceCount+1];
		for (int f=1;f<=packData.faceCount;f++) {
			futil[f]=vgens[packData.faces[f].vert[0]];
			for (int j=1;j<3;j++) {
				int k=packData.faces[f].vert[j];
				if (vgens[k]<futil[f])
					futil[f]=vgens[k];
			}
			packData.faces[f].plotFlag=0;
		}

		// rotating lists of current/next faces being processed
		FaceLink currFaces=null;
		FaceLink nextFaces=new FaceLink(packData);
		
		// start tree
		GraphLink dtree = new GraphLink();
		dtree.add(new EdgeSimple(0,startface));
		
		// see if it's in a bdryLink
		if (bfaces[startface]>0) {
			GenBranchPt gbp=branchPts.get(bfaces[startface]);
			dtree=bdrySubLink(gbp,startface);
			for (int i=0;i<gbp.bdryLink.size();i++) {
				int ff=gbp.bdryLink.get(i);
				packData.faces[ff].plotFlag=++count;
				nextFaces.add(ff);
			}				
		}
		else {
			nextFaces.add(startface);
			packData.faces[startface].plotFlag=++count;
		}		
		int currFaceGen=0;
		int safety=packData.faceCount;
		while(nextFaces.size()>0 && safety>0) {
			safety--;
			currFaces=nextFaces;
			nextFaces=new FaceLink(packData);
			
			// get lowest generation in 'currFaces'
			currFaceGen=futil[currFaces.get(0)]; // generation of this list
			for (int fi=1;fi<currFaces.size();fi++) {
				int cg=futil[currFaces.get(fi)];
				currFaceGen = (cg>currFaceGen) ? cg:currFaceGen;
			}
			
			// process the current list
			while (currFaces.size()>0) {
				int face=currFaces.remove(0);
//System.err.println("dS face="+face);				
				NodeLink partners=graph.findPartners(face);
				Iterator<Integer> ptns=partners.iterator();
				while (ptns.hasNext()) {
					int nghb=ptns.next();
					if (packData.faces[nghb].plotFlag==0) {
						
						// add the dual edge
						dtree.add(new EdgeSimple(face,nghb));
						
						// if in a bdryLink, add dual edges
						if (bfaces[nghb]>0) {
							GenBranchPt gbp=branchPts.get(bfaces[nghb]);
							dtree.abutMore(bdrySubLink(gbp,nghb));
							for (int i=0;i<gbp.bdryLink.size();i++) {
								int ff=gbp.bdryLink.get(i);
								packData.faces[ff].plotFlag=++count;
								// add ff to one of the lists
								if (futil[ff]<=currFaceGen)
									currFaces.add(ff);
								else 
									nextFaces.add(ff);
							}				
						}
						else {
							packData.faces[nghb].plotFlag=++count;
							// add nghb to one of the lists
							if (futil[nghb]<=currFaceGen)
								currFaces.add(nghb);
							else 
								nextFaces.add(nghb);
						}
					}
				} // end of while on partners
			} // end of while on currFace
		} // end of outer while
		
		return dtree;
	}	
	
	/**
	 * Find maximal tree subgraph in 'bdryLink' for 'GenBranchPt' 'gbp', 
	 * starting with face 'f'. Reset 'faceTracker' to -ID of faces reached.
	 * @param gbp @see GenBranchPt
	 * @param f int, first face
	 * @return @see GraphLink, starting with (f,g) for some face g. Null on error or 
	 * if bdryLink has only f in it.
	 */
	public GraphLink bdrySubLink(GenBranchPt gbp,int f) {
		int indx=gbp.bdryLink.containsV(f);
		if (indx<0 || gbp.bdryLink.size()<2)
			return null;
		GraphLink ans=new GraphLink();
		
		// need to know if it's closed
		int clsd=0;
		if (gbp.bdryLink.getLast()==gbp.bdryLink.getFirst()) // closed
			clsd=1;
		
		int lastf=f;
		int currf=f;
		if (indx==0) { // f is first in list
			for (int i=1;i<(gbp.bdryLink.size()-clsd);i++) {
				lastf=currf;
				currf=gbp.bdryLink.get(i);
				ans.add(new EdgeSimple(lastf,currf));
//				faceTracker[currf]=-gbp.branchID;
			}
			return ans;	
		}
		if (indx==gbp.bdryLink.size()-1) { // f is last in list
			currf=f;
			for (int i=gbp.bdryLink.size()-1;i>=clsd;i--) {
				lastf=currf;
				currf=gbp.bdryLink.get(i);
				ans.add(new EdgeSimple(lastf,currf));
//				faceTracker[currf]=-gbp.branchID;
			}
			return ans;
		}
		
		// else, f is in interior of list
		// go up 
		for (int i=indx+1;i<(gbp.bdryLink.size()-clsd);i++) {
			lastf=currf;
			currf=gbp.bdryLink.get(i);
			ans.add(new EdgeSimple(lastf,currf));
//			faceTracker[currf]=-gbp.branchID;
		}
		// go down
		currf=f;
		for (int i=indx-1;i>=0;i--) {
			lastf=currf;
			currf=gbp.bdryLink.get(i);
			ans.add(new EdgeSimple(lastf,currf));
//			faceTracker[currf]=-gbp.branchID;
		}
		return ans;
	}
	
	/**
	 * See if index j (face or vertex, depending) is already a branch
	 * point with 'myType' type. If yes, return first matching index
	 * @param j int, face or vertex
	 * @param type int, 'myType'
	 * @return int 'myType' or -1 if no branch point of right type exists
	 */
	public int brptExists(int j,int type) {
		if(branchPts==null || branchPts.size()==0)
			return -1;
		Iterator<GenBranchPt> bps=branchPts.iterator();
		while (bps.hasNext()) {
			GenBranchPt gbp=bps.next();
			if (gbp!=null && gbp.myType==type && gbp.myIndex==j)
				return gbp.branchID;
		}
		return -1;
	}
	
	/**
	 * Given the branch point ID, find its index in current 'branchPts' vector.
	 * ID is assigned on creation, but may get out of line with index in 'branchPts'
	 * @param bpID int, assigned on creation
	 * @return -1 on failure to find
	 */
	public int findBPindex(int bpID) {
		if (branchPts==null || branchPts.size()==1) // 0th spot should be empty
			return -1;
		int indx=-1;
		for (int j=1;j<branchPts.size();j++) {
			GenBranchPt gbp=branchPts.get(j);
			if (gbp.branchID==bpID) {
				if (indx>0)
					throw new DataException("more than one branch point with ID = "+bpID);
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
			GenBranchPt gbp=branchPts.get(j);
			if ((gbp.myType==GenBranchPt.CHAPERONE && nlink.containsV(gbp.myIndex)!=-1) ||
					(gbp.myType==GenBranchPt.SHIFTED && nlink.containsV(gbp.myIndex)!=-1) ||
					(gbp.myType==GenBranchPt.TRADITIONAL && nlink.containsV(gbp.myIndex)!=-1))
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
			GenBranchPt gbp=branchPts.get(j);
			if ((gbp.myType==GenBranchPt.FRACTURED && flink.containsV(gbp.myIndex)!=-1) ||
					(gbp.myType==GenBranchPt.QUADFACE && flink.containsV(gbp.myIndex)!=-1) ||
					(gbp.myType==GenBranchPt.SINGULAR && flink.containsV(gbp.myIndex)!=-1))
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
		if (branchPts==null || branchPts.size()==1)
			return nlink;
		for (int j=1;j<branchPts.size();j++) {
			GenBranchPt gbp=branchPts.get(j);
			if (gbp.myType==GenBranchPt.CHAPERONE || gbp.myType==GenBranchPt.SHIFTED ||
					gbp.myType==GenBranchPt.TRADITIONAL)
				nlink.add(gbp.myIndex);
		}
		return nlink;
	}
	
	/**
	 * Find the faces which are branch faces for some branch point
	 * @return FaceLink, empty if none found
	 */
	public FaceLink getBPfaces() {
		FaceLink flink=new FaceLink(packData);
		if (branchPts==null || branchPts.size()==1)
			return flink;
		for (int j=1;j<branchPts.size();j++) {
			GenBranchPt gbp=branchPts.get(j);
			if (gbp.myType==GenBranchPt.SINGULAR || gbp.myType==GenBranchPt.QUADFACE ||
					gbp.myType==GenBranchPt.FRACTURED)
				flink.add(gbp.myIndex);
		}
		return flink;
	}
	
	/**
	 * Given a point 'pt', determine if it gives 'singular' (interstice) or
	 * 'chaperone' branching (relative to 'refPack') and return appropriate 
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
	 * (4) TODO: near tangency point, use special structure: is common point
	 * of two interstices, eg.
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
	 *      [2],[3] = jump petals (circle indices in refPack, not petal indices)
	 *      [4],[5] = overlaps in [0,1], depending on situation
	 *    
	 * @param pt Complex
	 * @return double[2], double[4], or double[6], null on error 
	 */
	public double []getClickData(Complex pt) {
		if (pt==null) return null;
		double []ans=null;
		
		// first check if pt is inside a circle
		int v=NodeLink.grab_one_vert(refPack,"z "+pt.x+" "+pt.y);

		// not in circle, try interstice
		if (v<=0) { 
			// barycentric coords are related to angles, see 'HyperbolicMath.ideal_bary'
			BaryPoint bpt=BaryLink.grab_one_barypoint(refPack,new String("-i "+pt.x+" "+pt.y));
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
		if (refPack.kData[v].bdryFlag>0) { // can't yet branch at boundary circle
			CirclePack.cpb.errMsg("Can't yet branch at boundary vertex v="+v);
			throw new DataException(v+" is a bdry vertex");
		}

		// Key variables are R, r, and shadow.
		int num=refPack.kData[v].num;
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
		if ((R-r)<dcutoff*refPack.getRadius(v)) {
			ans=new double[2];
			ans[0]=3.0;
			ans[1]=(double)v;
			return ans;
		}

		// prepare return data for circle case
		ans=new double[6];
		ans[0]=2.0; 
		ans[1]=(double)v; // circle index

		// closed list the arguments of edges from v to petals v_j
		
		//    TODO: If nearOne>=0, pt is very close to a tangency point, 
		//    may want to use singular branching or some special routine.
//		int nearOne=-1;
		double []petalAngs=new double[num+1]; // closed cycle of angles to petals
		for (int j=0;j<num;j++) {
			Complex c2pet=refPack.getCenter(refPack.kData[v].flower[j]).minus(cent);
			petalAngs[j]=(c2pet.arg()+m2pi)%m2pi;
//			c2pet=c2pet.times(R/c2pet.abs());
//			if (c2pet.minus(cent2pt).abs()<.025*R) {
//				nearOne=refPack.kData[v].flower[j];
//			}
		}
		petalAngs[num]=petalAngs[0]; // close up
		
		// "click" data for a point on the circle is a double c = J + F,
		//    where the integer part J is the index of the petal
		//    containing the point, and the F is the cclw fraction of
		//    angle the point occupies in that petal.
		// We get the upstream and downstream max and min data we can use
		ClickValue centClick=petalClicks(c2pt_arg,petalAngs); // click data for pt itself
		ClickValue maxUpClick=petalClicks(c2pt_arg-Math.PI/2.0,petalAngs); // pi/2 clw
		ClickValue maxDownClick=petalClicks(c2pt_arg+Math.PI/2.0,petalAngs); // pi/2 cclw
		
		// move one petal upstream, one petal downstream
		ClickValue minUpClick=new ClickValue((centClick.petal-1.0+num)%num+centClick.fraction); 
		ClickValue minDownClick=new ClickValue((centClick.petal+1.0)%num+centClick.fraction); // one petal downstream

		// get the 'shadow' cast by pt; this is half the angular arc subtended
		//   by a circle through pt, perpendicular to 'cent2pt' and hitting the circle
		//   of radius R at right angles (i.e., hyp geodesic, vis-a-vis this disc).
		// 'shadow' is in [0,pi/2].
		double shadow=HyperbolicMath.shadow_angle((R-r)/R); // geodesic half-length 

		// TODO: CAUTION, not at all sure about this (4/2016)
		// Use transform T to map real click data for 'pt' and 'shadow'
		//   monotonically to virtual click data used to find jumps and overlaps. 
		// On the upstream side: T: [0,pi/2] --> [minright, maxright].
		// On the downstream side: T: [0,pi/2] --> [minleft, maxleft].
		ClickValue vertualUp=clickTransform(shadow,minUpClick,maxUpClick,num);
		ClickValue vertualDown=clickTransform(shadow,minDownClick,maxDownClick,num);

		// jump and overlap depend on upstream or downstream
		int upj=(int)((vertualUp.petal+1)%num);
		ans[2]=refPack.kData[v].flower[upj];
		ans[4]=1.0-vertualUp.fraction;
		
		int downj=(int)(vertualDown.petal+1)%num;
		ans[3]=refPack.kData[v].flower[downj];
		ans[5]=vertualDown.fraction;
		
		return ans;
/*		if (sameface) {
		
			// set jumps
			ans[2]=(double)refPack.kData[v].flower[cent_petal];
			ans[3]=(double)refPack.kData[v].flower[(cent_petal+2)%num];

			// what face are we in?
			int face=refPack.face_right_of_edge(refPack.kData[v].flower[cent_petal],v);
			
			// project 'pt' to boundary of circle for v
			Complex pt2bdry=cent.add(cent2pt.times(R/cent2pt.abs()));
			
			// find barycentric coords of pt2bdry in this interstice
			BaryPoint bpt=BaryLink.grab_one_barypoint(refPack,new String("-i "+pt2bdry.x+" "+pt2bdry.y));
			int hit=-1;
			for (int m=0;(m<3 && hit<0);m++)
				if (refPack.faces[face].vert[m]==v)
					hit=m;
			if (bpt.face!=face || hit<0)
				throw new CombException("BaryLink face conflict or face should contain 'v'");
			
			// Use appropriate barycentric coord as 'beta' (see 'IntersticeCoord.jpg' and
			//   'ChapParams_tri.jpg' for geometry)
			double beta=0.0;
			if (hit==0)
				beta=bpt.b3;
			else if (hit==1)
				beta=bpt.b1;
			else 
				beta=bpt.b2;
			
			double sl=(petalDir[cent_petal+1].divide(petalDir[cent_petal]).arg()); // angle subtended by sector
			double prop= 2*g/sl; // proportion occupied by shadow
			
			// compliicated because our overlap parameter setup is 
			//   not symmetric. Think of "reach" of jump circles,
			//   rch1 and rch2. rch1+rch2 >= 1, and rch1, rch2 <= 1,
			//   want rch1/rch2 = beta/(1-beta).
			double ln1=-Math.log(beta);
			double ln2=-Math.log(1.0-beta);
			double lnmin=(ln1<ln2) ? ln1 : ln2;
			double factor=Math.exp(prop*lnmin); // in [1,min(1/beta,1/(1-beta)]
						
			// use this factor
			ans[4]=factor*beta;
			ans[5]=1.0-factor*(1.0-beta);
			return ans;
		}

		int takeoff_petal=-1;
		int landing_petal=-1;
		for (int j=0;j<num;j++) {
			double ao=petalDir[(j+1)%num].divide(petalDir[j]).arg();
			double dfr=takeoff_pt.divide(petalDir[j]).arg();
			if (dfr>=0 && dfr<=ao)
				takeoff_petal=j;
			double dfl=landing_pt.divide(petalDir[j]).arg();
			if (dfl>=0 && dfl<=ao)
				landing_petal=j;
		}

		// use takeoff_pt to set first jump info
		int r_vert=refPack.kData[v].flower[takeoff_petal+1];
		ans[2]=(double)r_vert;
		double dfr=petalDir[takeoff_petal+1].divide(takeoff_pt).arg();
		double arcr=petalDir[takeoff_petal+1].divide(petalDir[takeoff_petal]).arg();
		ans[4]=dfr/arcr;
		
		// landing point for second jump
		ans[3]=(double)refPack.kData[v].flower[(landing_petal+1)%num];		
		double dfl=landing_pt.divide(petalDir[landing_petal]).arg();
		double arcl=petalDir[landing_petal+1].divide(petalDir[landing_petal]).arg();
		ans[5]=1.0-dfl/arcl;

		return ans;
		
*/
		
	}
	
	/**
	 * Transform x in [0,pi/2] into return T(x) in [mn,mx], with T(0)=mn, T(pi/2)=mx.
	 * Note that mn may be less than or greater than mx, so we may have to reverse.
	 * 
	 * TODO: Currently we use linear interpolation, but as we learn more, we may be able
	 * to improve on this for our purposes.
	 * 
	 * @param value double in [0,pi/2]
	 * @param mn ClickValue
	 * @param mx ClickValue
	 * @param num int, number of petals
	 * @return ClickValue
	 */
	public static ClickValue clickTransform(double value,ClickValue mn,ClickValue mx,int num) {

		int fcount=(mx.petal-mn.petal+num)%num;
		double fracdiff=mx.fraction-mn.fraction;
		double comp=value*2.0/Math.PI;
		double result=((double)fcount+fracdiff)*comp;
		int newpetal=((int)Math.floor(result)+num)%num;
		return new ClickValue(newpetal+result-Math.floor(result));
	}
	
	// OBE, but saved in case it's useful again
	public double []old_getClickData(Complex pt) {
		if (pt==null) return null;
		double []ans=null;
		
		// first check if pt is inside a circle
		int v=NodeLink.grab_one_vert(refPack,"z "+pt.x+" "+pt.y);

		// not in circle, try interstice
		if (v<=0) { 
			// barycentric coords are related to angles, see 'HyperbolicMath.ideal_bary'
			BaryPoint bpt=BaryLink.grab_one_barypoint(refPack,new String("-i "+pt.x+" "+pt.y));
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

		// okay, we're in a circle
		int num=refPack.kData[v].num;
		
		// can't yet branch a boundary circle 
		if (refPack.kData[v].bdryFlag>0) {
			CirclePack.cpb.errMsg("Can't yet branch at boundary vertex v="+v);
			throw new DataException(v+" is a bdry vertex");
		}

		double rad=refPack.getRadius(v);
		Complex dvec=pt.minus(refPack.getCenter(v));
		double dist=dvec.abs();
		if (dist>rad) // error: not in the right circle
			throw new DataException("hum...?, selected point is not in circle "+v);
		
		// close to center? use traditional branch point
		// TODO: need to experiment to find best 'dcutoff'
		double dcutoff=.025;
		if (dist<dcutoff*refPack.getRadius(v)) {
			ans=new double[2];
			ans[0]=3.0;
			ans[1]=(double)v;
			return ans;
		}

		// think of this as radius of sister2 (thus necessarily the smaller).
		double smallrad=rad-dist;

		// TODO: if dist==rad, perhaps treat as singular? else, prepare special
		//       new type of branch point (to avoid numerical difficulties)?

		// gather info to decide on jumps and overlaps
		
		// find all tangency points with petals; special case if pt is very close
		@SuppressWarnings("unused")
		int nearOne=-1;
		Complex []petalTangency=new Complex[num+1];
		for (int j=0;j<=num;j++) {
			petalTangency[j]=refPack.tangencyPoint(new EdgeSimple(v,refPack.kData[v].flower[j]));
			if (petalTangency[j].minus(pt).abs()<.025*refPack.getRadius(v)) {
				nearOne=refPack.kData[v].flower[j];
			}
		}
		
		// TODO: if nearOne>0, use routine for point where two interstices meet
		
		// find face pt lies in 
		FaceLink faces=refPack.tri_search(pt);
		int myface=-1;
		if (faces!=null && faces.size()>0) 
			myface=faces.get(0);
				
		// if pt lies in the incircle of 'myface', that should tell us
		//    the jump circles and give estimate of parameters.
		if (myface>0) {
			CircleSimple incirc=refPack.faceIncircle(myface,AmbiguousZ.getAmbiguousZs(refPack));
			if (pt.minus(incirc.center).abs()<incirc.rad) {
				ans=new double[6];
				ans[0]=2.0;
				ans[1]=(double)v;

				int k=refPack.face_index(myface,v);
				int w1=refPack.faces[myface].vert[(k+1)%3];
				int w2=refPack.faces[myface].vert[(k+2)%3];
				Complex w1w2tangency=refPack.tangencyPoint(new EdgeSimple(w1,w2));
				
				// get angle-type interstice coords: 
				// NOTE: order of tangencies: opposite v, w2, then w1. 
				//       Barycentric coord b1 should be negative 
				Complex z1=w1w2tangency.minus(incirc.center);
				Complex z2=petalTangency[refPack.nghb(v,w2)].minus(incirc.center);
				Complex z3=petalTangency[refPack.nghb(v,w1)].minus(incirc.center);
				z1=z1.divide(incirc.rad);
				z2=z2.divide(incirc.rad);
				z3=z3.divide(incirc.rad);
				Complex z=pt.minus(incirc.center);
				z=z.divide(incirc.rad);
				BaryPoint barypt=HyperbolicMath.ideal_bary(z,z1,z2,z3);
				
				// TODO: have to figure out how to use the coords
				ans[2]=barypt.b1;
				ans[3]=barypt.b2;

				ans[4]=w1;
				int j=refPack.nghb(v,w2);
				ans[5]=refPack.kData[v].flower[(j+1)%num];
				return ans;
			}
		}
		
		// find face z is in
		dvec=dvec.divide(dist); // unit vector
		
		// save unit vectors from center to petal centers
		Complex []petalVectors=new Complex[num+1];
		for (int j=0;j<=num;j++) {
			int k=refPack.kData[v].flower[j];
			petalVectors[j]=new Complex(refPack.getCenter(k).minus(refPack.getCenter(v)));
			petalVectors[j]=petalVectors[j].divide(petalVectors[j].abs()); 
		}
		
		// angle sum at sister2 in <sister2, petal[j], petal[j+1]>
		double []petalAngles=new double[num+1];
		for (int j=0;j<=num;j++) {
			double r1=refPack.getRadius(refPack.kData[v].flower[j]);
			double r2=refPack.getRadius(refPack.kData[v].flower[(j+1)%num]);
			double m1=r1/(smallrad+r1);
			double m2=r2/(smallrad+r2);
			petalAngles[j]=Math.acos(1-2*m1*m2);
		}
		
		// which sector is to contain tangency point of sister1,sister2?
		// pos[j] true ==> petalVectors[j] left when looking in dvec direction
		boolean []pos=new boolean[num+1];
		for (int j=0;j<=num;j++) {
			Complex pvec=petalVectors[j];
			if (pvec.divide(dvec).arg()<=0.0)
				pos[j]=false;
			else pos[j]=true;
		}
		
		// jump1 is first clw petal from dvec direction: here's where we jump to sister2.
		int jump1=-1;
		for (int j=1;j<=num;j++) {
			if (jump1==-1 && pos[j] && !pos[j-1])
				jump1=j;
		}
		
		// note: if jump1<0, should mean that our vertex isn't interior.
		if (jump1==-1)
			throw new ParserException("jump1<0, vertex may not be interior");
		
		// reset to zero if at last petal of closed flower
		if (refPack.kData[v].bdryFlag==0)
			jump1=jump1%num;
		
		// how deep is dvec in this sector? The deeper in, the smaller overlap 1
		int pj=(jump1-1+num)%num; // index of prejump
		double overlap1=1.0-dvec.divide(petalVectors[pj]).arg()/petalVectors[jump1].divide(petalVectors[pj]).arg();
		
		// Now have to find jump 2; 
		// Try to judge how may circles will go how far around sister2
		
		// get angle at sister2 as if jump1 were tangent to prejump and sister2
		int jumpCir1=refPack.kData[v].flower[jump1];
		int pv1=refPack.kData[v].flower[pj];
		double sp=pt.minus(refPack.getCenter(refPack.kData[v].flower[pj])).abs(); // distance pv1 to sister2
		double pv1v1=refPack.getRadius(pv1)+refPack.getRadius(jumpCir1);
		double v1s=rad+refPack.getRadius(jumpCir1);
		double ang=Math.acos((sp*sp+pv1v1*pv1v1-v1s*v1s)/(2.0*sp*pv1v1));
		Complex stpv=refPack.getCenter(pv1).minus(pt); // vector, pt to center of prejump1
		double pva=dvec.divide(stpv).arg(); // angle along sister1; subtract this
		double anglesum=ang-pva;
		
		// add angles of successive neighbors of sister2 until exceeding 2pi
		int n=jump1;
		while (anglesum<=Math.PI) {
			n=(n+1)%num;
			if (n==(jump1-1+num)%num)
				throw new DataException("angles must be wrong");
			anglesum+=petalAngles[n];
		}
		int jumpCir2=refPack.kData[v].flower[n];
		double overlap2=(anglesum-Math.PI)/Math.PI;

		ans=new double[6];
		ans[0]=2.0;
		ans[1]=(double)v;
		ans[2]=overlap1;
		ans[3]=overlap2;
		ans[4]=jumpCir1;
		ans[5]=jumpCir2;
		return ans;
	}
	
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
	public static ClickValue petalClicks(double spot,double []args) {
		int num=args.length-1;
		spot=(spot+m2pi)%m2pi;
		for (int j=0;j<num;j++) {
			double a=(spot-args[j]+m2pi)%m2pi;
			double ac=(spot-args[j+1]+m2pi)%m2pi;
			if (a>=0 && ac>Math.PI) {
				double diff=(args[j+1]-args[j]+m2pi)%m2pi;
				return new ClickValue(j+a/diff);
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
		
		return ((double)k)*Math.PI; // essentially, myang should equal full angle sum
	}
	
	/**
	 * Delete a branch point
	 * @param bpID
	 * @return 0 on error or not found
	 */
	public int deleteBP(int bpID) {
		int indx=findBPindex(bpID);
		if (indx<=0)
			return 0;
		GenBranchPt gbp=branchPts.remove(indx);
		gbp.delete();
		return indx; // should be >= 1
	}
	
		
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("'Comment'","-b{b}",null,
				"first flag '-b' designates a branch point ID number"));
		cmdStruct.add(new CmdStruct("disp","-[shyj] {usual}",null,
				"display on parents packing. For chaperone: s=sisters, "+
				"h=chaperones, y=putative branch point, j=jumps. Also, {usual} display options"));
		cmdStruct.add(new CmdStruct("status","-b{b}",null,
				"report the status of branch point 'b'"));
		cmdStruct.add(new CmdStruct("angsum_err",null,null,
				"report the l^2 anglesum error of parent and all branch points"));
		cmdStruct.add(new CmdStruct("set_Glist",null,null,
				"set Glist to 'layoutTree'"));
		cmdStruct.add(new CmdStruct("get_param",null,null,
				"report branch point parameters"));
		cmdStruct.add(new CmdStruct("repack","[-b{b}] [N]",null,
				"repack specified (or default to all) branch point, N repack cycles"));
		cmdStruct.add(new CmdStruct("Repack","[N]",null,
				"Repack everything, parent and branch points, in 'ping-pong' precess"));
		cmdStruct.add(new CmdStruct("layout",null,null,
				"layout specified (default to all) branch point in normalized position "+
				"(assume packed)"));
		cmdStruct.add(new CmdStruct("Layout",null,null,
				"layout parent and branch points (assume all are packed)"));
		cmdStruct.add(new CmdStruct("click","-[xX] z [-a {x}] ",null,
				"create a chaparone or a singular branch point (as appropriate) at the "+
				"point z (relative to 'refPack'); '-x' flag means to remove other nearby "+
				"branch points; '-X' remove all others; -a set aim to x*Pi"));
		cmdStruct.add(new CmdStruct("sendmydata","[-b{b}]",null,
				"send branch point radii, centers to parent (may want layout/align first)"));
		cmdStruct.add(new CmdStruct("reset_over","o1 o2",null,
				"For resetting overlaps for 'singular' and 'chaperone' branch points"));
		cmdStruct.add(new CmdStruct("export","[-qn] [-s {filename}]",null,
				"Export the local packing to packing 'n', the script, or "+
				"to 'filename' in the packing directory"));
		cmdStruct.add(new CmdStruct("delete","-b{b}",null,
				"delete branch point 'b'"));
		cmdStruct.add(new CmdStruct("set_param","{param list}",null,
				"Set parameters for branch point, format depends on type: "+
				"sing '-a {a} -o {o1 o2}'; "+
				"chap '-a {a} -j {w1 w2} -o {o1 o2}'"));
		
		// creating branch point types:
		cmdStruct.add(new CmdStruct("bp_trad","-a {a} -v {v}",null,
				"Create 'traditional' branch point, aim 'a', vert 'v'."));
		cmdStruct.add(new CmdStruct("bp_frac","-a {a} -f {f}",null,
				"Create 'fractured' branch point, aim 'a', face 'f'."));
		cmdStruct.add(new CmdStruct("bp_quad","-a {a} -f {f g}",null,
				"Create 'quadface' branch point: aim 'a', contig faces 'f', 'g'."));
		cmdStruct.add(new CmdStruct("bp_sing","-a {a} -f {f} -o {o1 o2} [-b {blist}]",null,
				"Create 'singular' branch point, aim 'a'; face 'f'; "+
				"overlaps 'o1', 'o2' in [0,1], o1+o2 in [0,1]. "+
				"'blist' is 'BaryLink' option for face and overlaps."));
		cmdStruct.add(new CmdStruct("bp_shift","-a {a} -v {v} -w {w}",null,
				"Create 'shifted' branch point, aim 'a', vert 'v'."));
		cmdStruct.add(new CmdStruct("bp_chap","-a {a} -v {v} -j {w1 w2} -o {o1 o2}",null,
				"Create 'chaperone' branch point, aim 'a', vert 'v'; "+
				"optional jump vertices, petals 'w1' 'w2', "+
				"overlap parameters 'o1', 'o2' in [0,1]."));
		cmdStruct.add(new CmdStruct("copy","{pnum}",null,
				"write 'refPack' into designated packing"));
		
	}

}

/**
 * Utility class: 'spot' in [0,2pi] has integer part 'petal' (an index
 * to petals of some flower) and 'fraction', the cclw fraction of the
 * petal's opening that spot occupies.
 * @author kens (2016)
 */
class ClickValue {
	public int petal;
	public double fraction;
	
	// Constructor
	public ClickValue(double spot) {
		petal=(int)Math.floor(spot);
		fraction=spot-petal;
	}
}
