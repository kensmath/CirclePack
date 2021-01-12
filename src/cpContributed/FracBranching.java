package cpContributed; // Date:5/4/2012

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import geometry.SphericalMath;
import listManip.FaceLink;
import listManip.NodeLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.DispFlags;
import util.StringUtil;
import util.UtilPacket;

/**
 * Work with "fractional" branching, with branch points fractured to reside in part
 * at multiple vertices.
 */
public class FracBranching extends PackExtender {

	public int [][]branchVert;
	public double excessBranching;
	final static double pi2=Math.PI*2.0;
	public static double LOC_TOLER=.000000001;//TODO .00000000001;
	public String []stateStr={"ERROR","START","HAVE_BP"};
	public Vector<CranePoint> cranePts=null;
	public int []vertStatus;
    double TOLER=.00000000001;//max=.00000000001;//norm=.00000000001;standard=.000000001;
    double OKERR=.0000001;
	private int fbState;
	
	// Constructor
	public FracBranching(PackData p) {
		super(p);
		packData=p;
		vertStatus = new int[packData.nodeCount+1];
		extensionType="FRACBRANCHING";
		extensionAbbrev="FB";
		toolTip="FracBranching: experiments with fractured branch points --- "+
		"sharing branching among a cluster of vertices.";
		registerXType();
		cranePts=new Vector<CranePoint>(1);
		
		excessBranching=pi2;
		if (running) { 
			packData.packExtensions.add(this);
		}
		fbState=1;
	}
	//CmdParser
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		if (cmd.startsWith("sdisp")) {
			return a1_sdisp(flagSegs,items);
		}
		else if (cmd.startsWith("lay")) {
			return a1_lay(flagSegs, items);
		}
		else if (cmd.startsWith("rif")) {
			return a1_rif(flagSegs, items);
		}
		else if (cmd.startsWith("spt")) {
			return a1_spt(flagSegs, items);
		}
		else if (cmd.startsWith("branch")) {
			return a1_branch(flagSegs, items);
		}
		else if (cmd.startsWith("branch_4")) {
			return a1_branch_4(flagSegs, items);
		}
		else if (cmd.startsWith("weig")) {
			return a1_weigh(flagSegs, items);
		}
		else if (cmd.startsWith("compu")) {
			return a1_compu(flagSegs, items);
		}
		else if (cmd.startsWith("cur_err")) {
			return a1_curr_err(flagSegs, items);
		}		
		else if (cmd.startsWith("data")) {
			return a1_data(flagSegs, items);
		}		
		else if (cmd.startsWith("path_error")) {
			return a1_path_error(flagSegs, items);
		}		
		else if (cmd.startsWith("turn_sum")) {
			return a1_turn_sum(flagSegs, items);
		}		
		else if (cmd.startsWith("rad_diff")) {
			return a1_rad_diff(flagSegs, items);
		}
		else if (cmd.startsWith("pos_diff")) {
			return a1_pos_diff(flagSegs, items);
		}		
		else if (cmd.startsWith("exp_set_over")) {
			return a1_exp_set_over(flagSegs, items);
		}
		else if (cmd.startsWith("exp_DAF_shift")) {
			return a1_exp_DAF_shift(flagSegs, items);
		}
		else if (cmd.startsWith("expDAFShiftFind")) {
			return a1_exp_DAFShiftFind(flagSegs, items);
		}
		else if (cmd.startsWith("expDAFfracInvFind")) {
			return a1_exp_DAFfracInvFind(flagSegs, items);
		}
		else if (cmd.startsWith("exp_DWshiftHex")) {
			return a1_exp_DWshiftHex(flagSegs, items);
		}
		else if (cmd.startsWith("exp_weirshift")) {
			return a1_exp_DWshiftBig(flagSegs, items);
		}
		else if (cmd.startsWith("exp_weirshift")) {
			return a1_exp_DWshiftSmall(flagSegs, items);
		}
		else if (cmd.startsWith("exp_DWshiftBig")) {
			return a1_exp_DWshiftBig(flagSegs, items);
		}
		else if (cmd.startsWith("exp_findweirshift")) {
			return a1_exp_DWshiftfind(flagSegs, items);
		}
		else if (cmd.startsWith("expDWfracfind")) {
			return a1_exp_DWfracfind(flagSegs, items);
		}
		else if (cmd.startsWith("expDWfrac")) {
			return a1_exp_DWfrac(flagSegs, items);
		}
		else if (cmd.startsWith("overlap_shuffle_in")) {
			return a1_overlap_shuffle_in(flagSegs, items);
		}		
		else if (cmd.startsWith("overlap_shuffle_out")) {
			return a1_overlap_shuffle_out(flagSegs, items);
		}		
		else if (cmd.startsWith("overlap_random_in")) {
			return a1_overlap_random_in(flagSegs, items);
		}
		else if (cmd.startsWith("overlap_random_out")) {
			return a1_overlap_random_out(flagSegs, items);
		}
		else if (cmd.startsWith("border_rad")) {
			return a1_border_rad(flagSegs, items);
		}
		else if (cmd.startsWith("border_overlap")) {
			return a1_border_overlap(flagSegs, items);
		}
		else if (cmd.startsWith("border_pos")) {
			return a1_border_pos(flagSegs, items);
		}
		else if (cmd.startsWith("crane_pos")) {
			return a1_crane_pos(flagSegs, items);
		}
		else if (cmd.startsWith("border_random_ovr")) {
			return a1_border_random_ovr(flagSegs, items);
		}
		else if (cmd.startsWith("find_set_inv")) {
			return a1_find_set_inv(flagSegs, items);
		}
		else if (cmd.startsWith("borderchain")) {
			return a1_borderchain(flagSegs, items);
		}
	    else if (cmd.startsWith("chain_err")) {
	    	return a1_chain_err(flagSegs, items);
	    }
	    else if (cmd.startsWith("frob_norm")) {
	    	return a1_frob_norm(flagSegs, items);
	    }
	    else if (cmd.startsWith("find_branch_DAF")) {
	    	return a1_find_branch_DAF();
	    }
	    else if (cmd.startsWith("find_branch_DW")) {
	    	return a1_find_branch_DW();
	    }
	    else if (cmd.startsWith("bd_rad_DW")) {
	    	return a1_bd_rad_DW(flagSegs, items);
	    }
	    else if (cmd.startsWith("DAFerror")) {
	    	return a1_DAFerror(flagSegs, items);
	    }
	    else if (cmd.startsWith("DWerror")) {
	    	return a1_DWFerror(flagSegs, items);
	    }
	    else if (cmd.startsWith("chain_lay")) {
	    	return a1_chain_lay(flagSegs, items);
	    }
	    else if (cmd.startsWith("find_frac_DAF")) {
	    	return a1_find_frac_DAF();
	    }
	    else if (cmd.startsWith("exp_DAFfracInv")) {
	    	return a1_exp_DAFfracInv(flagSegs, items);
	    }
	    else if (cmd.startsWith("find_cheat")) {
	    	return a1_find_cheat(flagSegs, items);
	    }
	    else if (cmd.startsWith("adjust_inv")) {
	    	return a1_adjust_inv(flagSegs, items);
	    }
	    else if (cmd.startsWith("exp_DAF_InvShift")) {
	    	return a1_exp_DAF_InvShift(flagSegs, items);
	    }
	    else if (cmd.startsWith("find_border_DAF")) {
	    	return a1_find_border_DAF();
	    }
	    else if (cmd.startsWith("exp_DWhexBdRad")) {
	    	return a1_exp_DWhexBdRad(flagSegs, items);
	    }
		return super.cmdParser(cmd, flagSegs);
	}
	//End CmdParser
	//Cmd.starts
		
//Redirected from cmd.starts
	public int a1_find_cheat(Vector<Vector<String>> flagSegs, Vector<String> items) {
		double ratio; int v1, v2, vp, v_big, alpha; 
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			vp = Integer.parseInt((String)items.get(2));
			alpha = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, v");
		}
		if (packData.getRadius(v1)>=packData.getRadius(v2)) {
			ratio =packData.getRadius(v2)/packData.getRadius(v1);v_big=v1;
		} 
		else {
			ratio =packData.getRadius(v1)/packData.getRadius(v2);v_big=v2;
	 	}
		double angle =genAngleSides(genPtDist(packData.getCenter(v_big),packData.getCenter(vp)),
				genPtDist(packData.getCenter(v_big),packData.getCenter(alpha)),
				genPtDist(packData.getCenter(alpha),packData.getCenter(vp)));
		double theta =angle/Math.PI;
		alpha =packData.nghb(v_big, alpha);
		msg("|fb| spt "+theta+" "+ratio+" "+alpha+" "+v_big);
		return 1;
	}
	public int a1_borderchain(Vector<Vector<String>> flagSegs, Vector<String> items) {
		//finds the borderchain for vertex b; stores chain in flist;finds frob error.
		int b;
		try {
			items=flagSegs.get(0);
			b=Integer.parseInt((String)items.get(0));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get a border vertex b");
		}
//		int n =shiftLayoutFacelist(packData,b);
		if (cranePts.isEmpty()==false) {
			shuffle(500);
			for (int v=1;v<=packData.nodeCount;v++) {
				if (vertStatus[v]==2) {
					shiftToOverlaps(v);
				}
			}
			cpCommand(packData,"repack"); //TODO: use repack -o
			cpCommand(packData,"layout");
			msg("Shiftpoints have been converted to overlaps and repacked");
		}
		
		String Flist =findBorderChain(b);
		cpCommand(packData,"set_flist "+Flist);
		cpCommand(packData,"disp -f");
		FaceLink faces1=new FaceLink(packData,Flist);
		Mobius mob1 =holonomy_mob(faces1);
		double err =Math.abs(2-Math.pow(frobNorm(mob1),2));
		
		msg("faces stored in flist; Frob. Error ="+err);
		msg(" "+Flist);
		return 1;
		}
	public int a1_border_overlap(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,b1,n; //vi=vertices to be matched by location. b1=the border vertex
		double x;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			b1 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			x = Double.parseDouble((String)items.get(4));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, b1,n, or x");
		}
		double err =overlapBorderShuffle(packData,n,v1,v2,b1,x);
		msg("FrobNorm Error from v1 to v2, "+err);
		return 1;
	}
	public int a1_border_pos(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,b1,n,new_or_old; 
		double x;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			b1 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			new_or_old = Integer.parseInt((String)items.get(4));
			x = Double.parseDouble((String)items.get(5));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, b1, n, " +
					"old_or_new, center_frob_or both, or x");
		}
		double err =borderAdjPos(v1,v2,b1,n,new_or_old, x);
		msg("Euclidean distance from "+v1+" to "+v2+", "+err);
		return 1;
	}
	public int a1_adjust_inv(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1, v2; double inv_inc;
		try {
			items=flagSegs.get(0);
			inv_inc = Double.parseDouble((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			v2 = Integer.parseInt((String)items.get(2));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get inv increment, v1, or, v2." +
					" Sorry, only single edges at a time");
		}
		if (!packData.overlapStatus) { //alocates space for overlaps
			packData.alloc_overlaps();
		}
		packData.set_single_invDist(v1,v2,packData.getInvDist(v1, v2)+inv_inc);
		return 1;
	}
	
	public int a1_bd_rad_DW(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,v3,n,punch; double x;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			v3 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			x = Double.parseDouble((String)items.get(4));
			punch = Integer.parseInt((String)items.get(5));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, b1, n, " +
					", or x");
		}
		String FL1 = "25 26 11 12 13 14 15 22 25"; //1st fundamental chain
		String FL2 = "1 2 3 13 14 18 19 8 1"; //2nd fundamental chain
		if (punch ==4) {
			FL1 = "15 16 10 9 8 5 8 14 15"; //1st fundamental chain
			FL2 = "1 12 13 14 7 6 3 2 1"; //2nd fundamental chain
		}
		int type =0;//0=Frob, 1=Distance
    	double err =expDWborderAdj(v1, v2, v3, n, FL1, FL2 ,x,type);
		msg("DONE: Frob norm error ="+err);
		return 1;
	}
	
	public int a1_exp_DWhexBdRad(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,v3,n,new_or_old; double x;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			v3 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			x = Double.parseDouble((String)items.get(4));
			new_or_old = Integer.parseInt((String)items.get(5));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, b1, n, " +
					", or x");
		}
		String FL1 = "15 14 2 1 7 8 24 25 42 83 82 81 15"; //1st fundamental chain
		String FL2 = "47 -F 47 31 30 10 11 1 7 6 5 20 36 35 44 48 47"; //2nd fundamental chain
    	double err =expDWborderAdj(v1, v2, v3, n, FL1, FL2 ,x,new_or_old);
		msg("DONE: Frob norm error ="+err);
		return 1;
	}
	public int a1_border_rad(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,b1,n,new_or_old ; //vi=vertices to be matched by location. b1=the border vertex
		double x;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			b1 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			new_or_old = Integer.parseInt((String)items.get(4));
			x = Double.parseDouble((String)items.get(5));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, b1, n or x");
		}
		double err =borderAdjRad(v1,v2,b1,n,new_or_old, x);
		msg("Difference of radii at "+v1+" and "+v2+" is "+err);
		return 1;
	}
	public int a1_border_random_ovr(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,b1,n; //vi=vertices to be matched by location. b1=the border vertex
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			b1 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, b1, or n");
		}
		double err =overlapBorderOutRandom(packData,v1,v2,b1,n);
		msg("Frob error (modified) from v1 to v2, "+err);
		return 1;
	}
	public int a1_branch_4(Vector<Vector<String>> flagSegs, Vector<String> items) {
		branchVert=new int[1][4]; int i =0;
		try {
			items=flagSegs.get(0);
			branchVert[0][0]=Integer.parseInt((String)items.get(0));
			branchVert[0][1]=Integer.parseInt((String)items.get(1));
			branchVert[0][2]=Integer.parseInt((String)items.get(2));
			branchVert[0][3]=Integer.parseInt((String)items.get(3));
		} catch(Exception ex) {
			fbState=0;
			throw new ParserException("failed to get 4 branch vertices");
		}
		while (i<4) {
			int check =packData.nghb(branchVert[0][i%4],branchVert[0][(i+1)%4]);
			if (check<0) {
				throw new ParserException("branch vertices "+branchVert[0][i%4]+
						" and "+branchVert[0][(i+1)&4]+" must be neighbors.");
			}
			check =packData.nghb(branchVert[0][0],branchVert[0][2]);
			if (check<0) {
				throw new ParserException("branch vertices "+branchVert[0][0]+
						" and "+branchVert[0][2]+" must be neighbors.");
			}
			i++;
		}
		if (packData.isBdry(branchVert[0][0]) ||
				packData.isBdry(branchVert[0][1]) ||
				packData.isBdry(branchVert[0][2])) {
			fbState=0;
			throw new ParserException("branch vertices must be interior");
		}
		fbState=2;
		return 1;
	}
	/**
	 * Stores branch points in branchVert[][] array. If 3 ints then a new array is created [0][3] 
	 * and these ints are stored in order as vertices. If {i,v1,v2,v3} then the last three are 
	 * copied into branchVert[i][3]. If i exceeds the lenght then branchVert is expanded. 
	 * @param flagSegs
	 * @param items
	 * @return
	 */
	public int a1_branch(Vector<Vector<String>> flagSegs, Vector<String> items) {
		items=flagSegs.get(0);
		if (items.size()==3) {
			branchVert =new int[1][3];
			try {
				branchVert[0][0]=Integer.parseInt((String)items.get(0));
				branchVert[0][1]=Integer.parseInt((String)items.get(1));
				branchVert[0][2]=Integer.parseInt((String)items.get(2));
			} catch(Exception ex) {
				fbState=0;
				throw new ParserException("failed to get 3 branch vertices");
			}
			if (packData.isBdry(branchVert[0][0]) ||
					packData.isBdry(branchVert[0][1]) ||
					packData.isBdry(branchVert[0][2])) {
				msg("NOTE: one branch vertex is not interior");
			}
		}
		else {
			int i=Integer.parseInt((String)items.get(0));
			if (branchVert==null) branchVert =new int[i][3];
			if (branchVert.length-1<=i) {
				int branchVertOld[][]=branchVert;
				branchVert=new int[i+1][3];
				for(int j=0; j<branchVertOld.length; j++) {
					for(int k=0;k<branchVertOld[j].length;k++) {
						branchVert[j][k] = branchVertOld[j][k];
					}//end for k
				}//end for j
			}//end if case when new is bigger
			try {
				branchVert[i][0]=Integer.parseInt((String)items.get(1));
				branchVert[i][1]=Integer.parseInt((String)items.get(2));
				branchVert[i][2]=Integer.parseInt((String)items.get(3));
			} catch(Exception ex) {
				fbState=0;
				throw new ParserException("failed to get 3 branch vertices");
			}
		}//end if items.size()==4
		fbState=2;
		return 1;
	}
	public int a1_chain_err(Vector<Vector<String>> flagSegs, Vector<String> items) {
		items=flagSegs.get(0);
		double err =fundChainError(items,250,0,1);
		msg("Chain error: "+err);
		return 1;
	}
	public int a1_chain_lay(Vector<Vector<String>> flagSegs, Vector<String> items) {
		try {
			items=flagSegs.get(0);
		} catch (Exception ex) {
			throw new ParserException("failed to get a face list");
		}
		String flist =vectorToString(items," ");
//		layChain(flist);
		msg("Chain "+flist+" has been layed out.");
		return 1;
	}
	public int a1_compu(Vector<Vector<String>> flagSegs, Vector<String> items) {
		if (fbState<2)
			throw new ParserException("branch vertices have not been chosen");
		int n;
		double inv;
		try {
			items=flagSegs.get(0);
			n=Integer.parseInt((String)items.get(0));
		} catch (Exception ex) {
			errorMsg("usage: compute <n>");
			n=100;
		}
		if (branchVert[0].length==3) {
			fracShuffle3(n,1);
		}
		if (branchVert[0].length==4) {
			try {
				inv=Double.parseDouble((String)items.get(1));
			} catch (Exception ex) {
				throw new ParserException ("failed to get an overlap assignment " +
						"for edge "+branchVert[0]+" "+branchVert[2]);
			}
			if (inv>1 || inv<0) {
				throw new ParserException ("inversive distance assignment " +
						"for edge "+branchVert[0]+" "+branchVert[2]+" must be between 0 and 1");
			}
			return fracShuffle4(n,inv);
		}
		return 1;
	}
	public int a1_crane_pos(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v0,v1,v2,n; double x;
		try {
			items=flagSegs.get(0);
			v0 = Integer.parseInt((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			v2 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			x = Double.parseDouble((String)items.get(4));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v0, v1, v2, n, " +
					"or x");
		}
		double err =craneAdjPos(packData,v0,v1,v2,n,x);
		msg("Forb error (modified) from v1 to v2, "+err);
		return 1;
	}
	public int a1_curr_err(Vector<Vector<String>> flagSegs, Vector<String> items)  {
		if (flagSegs.isEmpty()==true) {
			msg("current grerror: "+String.format("%.8e",errorTotal()));
		}
		items = flagSegs.get(0);
		if (items.get(0).equals("h")) {
			try {
				Integer.parseInt((String)items.get(1));
			} catch (Exception ex) {
				errorMsg("To check holonomy error, h must be followed by a border vertex");
			}
			int b =Integer.parseInt((String)items.get(1));
			if (!packData.isBdry(b)) {
				Oops(""+b+" is not a border vertex.");
			}
			FaceLink faces1=new FaceLink(packData,findBorderChain(b));
			double err =Math.abs(2-Math.pow(frobNorm(holonomy_mob(faces1)),2));
			msg("Frobenius Norm error of holonomy on "+b+"'s border chain ="+err);
		}
		return 1;
	}
	public int a1_data(Vector<Vector<String>> flagSegs, Vector<String> items) {
		if (fbState<2)
			throw new ParserException("branch vertices have not been chosen");
		packData.fillcurves();
		msg("FracBranching data: \n" +
				"  '(v,w/pi)' = ("+
				branchVert[0][0]+","+String.format("%.6e",(packData.getCurv(branchVert[0][0])-pi2)/Math.PI)+"); ("+
				branchVert[0][1]+","+String.format("%.6e",(packData.getCurv(branchVert[0][1])-pi2)/Math.PI)+"); ("+
				branchVert[0][2]+","+String.format("%.6e",(packData.getCurv(branchVert[0][2])-pi2)/Math.PI)+")\n");
		msg("  '(v,angle/pi)' = ("+
				branchVert[0][0]+","+String.format("%.6e",triAngle(0,0)/Math.PI)+"); ("+
				branchVert[0][1]+","+String.format("%.6e",triAngle(0,1)/Math.PI)+"); ("+
				branchVert[0][2]+","+String.format("%.6e",triAngle(0,2)/Math.PI)+")\n");
		return 1;
	}
	public int a1_DAFerror(Vector<Vector<String>> flagSegs, Vector<String> items) {
		double err =DAFerror(0); //0 =frob,1=dist
		msg("DAF error ="+err);
		return 1;
	}
	public int a1_DWFerror(Vector<Vector<String>> flagSegs, Vector<String> items) {
    	String FL1 = "25 26 11 12 13 14 15 22 25"; //1st fundamental chain
    	String FL2 = "1 2 3 13 14 18 19 8 1"; //2nd fundamental chain
		double err =DWFerror(FL1,FL2,0); //0 =frob,1=dist
		msg("DW error ="+err);
		return 1;
	}
	public int a1_exp_DAF_shift(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,v2,P1,P2;
		try {
			items=flagSegs.get(0);
			P1 = Integer.parseInt((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			P2 = Integer.parseInt((String)items.get(2));
			v2 = Integer.parseInt((String)items.get(3));
			n = Integer.parseInt((String)items.get(4));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, P1, v2, P2, or n");
		}		
		double[][] A =expDAFshift(v1,P1,v2,P2,n);
	    String strFilePath = "/home/jim/Desktop/exp_DAF_data.txt"; //"C://FileIO//demo.txt";
	    try {
	    writeToFile(A,strFilePath); //TODO: use a default path
	    	} catch (Exception ex) {
	    	throw new ParserException("failed to save data to "+strFilePath);
	    }
		msg("data written to "+strFilePath);
		return 1;
	}
	public int a1_exp_DAF_InvShift(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,P1,B1,B2;
		try {
			items=flagSegs.get(0);
			P1 = Integer.parseInt((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			B1 = Integer.parseInt((String)items.get(2));
			B2 = Integer.parseInt((String)items.get(3));
			n = Integer.parseInt((String)items.get(4));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get P1, v1, B1, B2, or n");
		}		
		double[][] A= expDAFshiftInv(v1,P1,B1,B2,n);
	    String strFilePath = "/home/jim/Desktop/exp_DAF_data.txt"; //"C://FileIO//demo.txt";
	    try {
	    writeToFile(A,strFilePath); //TODO: use a default path
	    	} catch (Exception ex) {
	    	throw new ParserException("failed to save data to "+strFilePath);
	    }
		msg("data written to "+strFilePath);
		return 1;
	}
	public int a1_exp_DAFShiftFind(Vector<Vector<String>> flagSegs, Vector<String> items) {
		try {
			items=flagSegs.get(0);
			Integer.parseInt((String)items.get(0)); // v1
			Integer.parseInt((String)items.get(1)); // v2
			Integer.parseInt((String)items.get(2)); // n
			Integer.parseInt((String)items.get(3)); // type
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, or n");
		}
		return 1; //expDAFShiftFind(v1,v2,n,type);
	}
	public int a1_exp_DAFfracInvFind(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1,v2,n,type;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			n = Integer.parseInt((String)items.get(2));
			type = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get v1, v2, or n");
		}
		return expDAFfracInvFind(v1,v2,n,type);
	}
	public int a1_exp_DAFfracInv(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int f1,f2,n,type;
		try {
			items=flagSegs.get(0);
			f1 = Integer.parseInt((String)items.get(0));
			f2 = Integer.parseInt((String)items.get(1));
			n = Integer.parseInt((String)items.get(2));
			type = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get f1, f2, or n");
		}
		return expDAFfracInv(f1,f2,n,type);
	}
	public int a1_exp_DWfrac(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,v2,v3,u1,u2,u3;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			v3 = Integer.parseInt((String)items.get(2));
			u1 = Integer.parseInt((String)items.get(3));
			u2 = Integer.parseInt((String)items.get(4));
			u3 = Integer.parseInt((String)items.get(5));
			n = Integer.parseInt((String)items.get(7));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("need v1 v2 v3 u1 u2 u3 and n; two set of vertices on a face" +
					" and iteration number");
		}	
//		String FL1 = "15 14 2 1 7 8 24 25 42 83 82 81 15"; //1st fundamental chain 
		String FL1 = "15 14 2 1 7 8 24 25 42 80 79 78 15"; //1st fundamental chain HexTorusReflex
//		String FL2 = "47 31 30 10 11 1 7 6 5 20 36 35 44 48 47"; //2nd fundamental chain 
		String FL2 = "47 31 30 10 11 1 7 6 5 20 36 35 44 48 47"; //2nd fundamental chain HexTorusReflex
    	double[][] A = expWeirFracInv(packData,v1,v2,v3,u1,u2,u3,FL1,FL2,n);
    	String strFilePath = "/home/jim/Desktop/exp_data_DWF_hex_frac.txt"; //"C://FileIO//demo.txt";
   		try {
    		writeToFile(A,strFilePath); //TODO: use a default path
	   		} catch (Exception ex) {
	    		throw new ParserException("failed to save data to "+strFilePath);
	    	}
    	msg("data written to "+strFilePath);
    	return 1;
	}
	public int a1_exp_DWfracfind(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v0,u0,type;
		try {
			items=flagSegs.get(0);
			v0 = Integer.parseInt((String)items.get(0));
			u0 = Integer.parseInt((String)items.get(1));
			n = Integer.parseInt((String)items.get(2));
			type = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("v0 u0 n; two interior vertices not sharing an edge" +
					", and iteration number");
		}
		return expWeirFracInvFind(v0,u0,n,type);
	}
	public int a1_exp_DWshiftSmall(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,v2,v3,type,punch,P1,P2,P3;
		try {
			items=flagSegs.get(0);
			P1 = Integer.parseInt((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			P2 = Integer.parseInt((String)items.get(2));
			v2 = Integer.parseInt((String)items.get(3));
			P3 = Integer.parseInt((String)items.get(4));
			v3 = Integer.parseInt((String)items.get(5));
			n = Integer.parseInt((String)items.get(6));
			punch = Integer.parseInt((String)items.get(7));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get shift vertices v1, v2, or v3; " +
					", or iteration number n");
		}
		//punch 16
		String FL1 = "25 26 11 12 13 14 15 22 25"; //1st fundamental chain
		String FL2 = "1 2 3 13 14 18 19 8 1"; //2nd fundamental chain
		if (punch ==4) {
			FL1 = "15 16 10 9 8 5 8 14 15"; //1st fundamental chain
			FL2 = "1 12 13 14 7 6 3 2 1"; //2nd fundamental chain
		}
		type =0;
    	double[][] A = expWeirShift(P1,P2,P3,v1,v2,v3,n,FL1,FL2,type);
    	String strFilePath = "/home/jim/Desktop/exp_data_weir.txt"; //"C://FileIO//demo.txt";
   		try {
    		writeToFile(A,strFilePath); //TODO: use a default path
	   		} catch (Exception ex) {
	    		throw new ParserException("failed to save data to "+strFilePath);
	    	}
    	msg("data written to "+strFilePath);
    	return 1;
	}
	public int a1_exp_DWshiftBig(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,v2,v3,type,P1,P2;
		try {
			items=flagSegs.get(0);
			P1 = Integer.parseInt((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			P2 = Integer.parseInt((String)items.get(2));
			v2 = Integer.parseInt((String)items.get(3));
			Integer.parseInt((String)items.get(4)); // P3
			v3 = Integer.parseInt((String)items.get(5));
			n = Integer.parseInt((String)items.get(6));
			type = Integer.parseInt((String)items.get(7));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get shift vertices v1, v2, or v3; " +
					", or iteration number n");
		}
    	String FL1 = "56 49 55 54 72 122 11 8 2 3 4 16 22 37 38 39 32 33 34 56"; //the fundamental chain
    	String FL2 = "1";//TODO find other FUND chain
    	double[][] A = expWeirShift(P1,P2,P2,v1,v2,v3,n,FL1,FL2,type);
    	String strFilePath = "/home/jim/Desktop/exp_data_weir.txt"; //"C://FileIO//demo.txt";
   		try {
    		writeToFile(A,strFilePath); //TODO: use a default path
	   		} catch (Exception ex) {
	    		throw new ParserException("failed to save data to "+strFilePath);
	    	}
    	msg("data written to "+strFilePath);
    	return 1;
	}
	public int a1_exp_DWshiftHex(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,v2,v3,type,P1,P2,P3;
		try {
			items=flagSegs.get(0);
			P1 = Integer.parseInt((String)items.get(0));
			v1 = Integer.parseInt((String)items.get(1));
			P2 = Integer.parseInt((String)items.get(2));
			v2 = Integer.parseInt((String)items.get(3));
			P3 = Integer.parseInt((String)items.get(4));
			v3 = Integer.parseInt((String)items.get(5));
			n = Integer.parseInt((String)items.get(6));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get shift vertices P1,v1,P2,v2,P3 or v3; " +
					", or iteration number n");
		}
		//punch 16
//		String FL1 = "15 14 2 1 7 8 24 25 42 83 82 81 15"; //1st fundamental chain 
		String FL1 = "15 14 2 1 7 8 24 25 42 80 79 78 15"; //1st fundamental chain HexTorusReflex
//		String FL2 = "47 31 30 10 11 1 7 6 5 20 36 35 44 48 47"; //2nd fundamental chain 
		String FL2 = "47 31 30 10 11 1 7 6 5 20 36 35 44 48 47"; //2nd fundamental chain HexTorusReflex
		type =0;
    	double[][] A = expWeirShift(P1,P2,P3,v1,v2,v3,n,FL1,FL2,type);
    	String strFilePath = "/home/jim/Desktop/exp_data_DWF_hex.txt"; //"C://FileIO//demo.txt";
   		try {
    		writeToFile(A,strFilePath); //TODO: use a default path
	   		} catch (Exception ex) {
	    		throw new ParserException("failed to save data to "+strFilePath);
	    	}
    	msg("data written to "+strFilePath);
    	return 1;
	}
	public int a1_exp_DWshiftfind(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,v1,v2,v3,type;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
			v3 = Integer.parseInt((String)items.get(2));
			n = Integer.parseInt((String)items.get(3));
			type = Integer.parseInt((String)items.get(4));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get shift vertices v1, v2, or v3; " +
					", or iteration number n");
		}
 		String FL1 ="25 26 11 12 13 14 15 22 25";
 		String FL2 ="1 2 3 13 14 18 19 8 1";
    	return expWeirShiftFind(v1,v2,v3,n,FL1,FL2,type);
	}
	public int a1_exp_set_over(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int n,b1,v1,v2;
		try {
			items=flagSegs.get(0);
			n = Integer.parseInt((String)items.get(0));
			b1 = Integer.parseInt((String)items.get(1));
			v1 = Integer.parseInt((String)items.get(2));
			v2 = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get n, b1, v1, or v2");
		}
		double[][] A = expSetOver(packData,n,b1,v1,v2); //TODO:add outgoing border circles
	    String strFilePath = "/home/jim/Desktop/exp_data.txt"; //"C://FileIO//demo.txt";
	    try {
	    writeToFile(A,strFilePath); //TODO: use a default path
	    	} catch (Exception ex) {
	    	throw new ParserException("failed to save data to "+strFilePath);
	    }
		msg("data written to "+strFilePath);
		return 1;
	}
	public int a1_find_set_inv(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v; 
		try {
			items=flagSegs.get(0);
			v =Integer.parseInt((String)items.get(0));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get a vertex v");
		}
		Iterator<String> itr = flagSegs.get(0).iterator();
		while (itr.hasNext()) {
			updateStatus();
			v =Integer.parseInt((String)itr.next());
			if (vertStatus[v]==2)  
			{ shiftToOverlaps(v);
			msg("Set inversvive distances for shift point "+v);
			} else {
				findSetInv(v);
				msg("Set inversvive distances for vertex according to current layout"+v);
			}
		}
//		cpCommand(packData,"repack -o"); 
		cpCommand(packData,"repack -o");
		msg("Applied: repack -o");
//		cpCommand(packData,"layout");
		return 1;
	}
 	public int a1_find_border_DAF() {
 		return findBestBorderDAF();
 	}
 	public int a1_find_branch_DAF() {
 		double V[]=findBestBranchDAF();
 		msg("lowest error of "+V[2]+" on vertices "+V[0]+" "+V[1]);
 		return 1;
 	}
 	public int a1_find_frac_DAF() {
 		findBestFracDAF();
 		return 1;
 	}
 	public int a1_find_branch_DW() {
// 		String Flist = "56 49 55 54 72 122 11 8 2 3 4 16 22 37 38 39 32 33 34 56";
 		String Flist1 ="25 26 11 12 13 14 15 22 25";
 		String Flist2 ="1 2 3 13 14 18 19 8 1";
 		double V[]=findBestBranchDW(Flist1,Flist2,0); //TODO: add type option
 		msg("DONE: find_branch_DW; lowest error for small torus ="
 				+V[3]+" branching at vertices "+V[0]+" "+V[1]+" "+V[2]);
 		return 1;
 	}
	public int a1_frob_norm(Vector<Vector<String>> flagSegs, Vector<String> items) {
		items=flagSegs.get(0);
		double err =fundChainError(items,250,0,1);
		msg("Frobenius norm error: "+err);
		return 1;
	}
	public int a1_lay(Vector<Vector<String>> flagSegs, Vector<String> items) {
		//layoutRoutine
		double result=fullLayout(packData);
		double err =totalLayoutError();
		msg("layout "+result+" layout error "+err);
		return 1;
	}
	public int a1_overlap_random_in(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v;
		int n;
		try {
			items=flagSegs.get(0);
			n = Integer.parseInt((String)items.get(0));
			v = Integer.parseInt((String)items.get(1));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get n or v");
		}
//		String Fl2 = "13 14 15 16 17 18 19 20 21 22 23 24 13";
		String Fl1 = "7 6 5 4 3 2 1 12 11 10 9 8 7"; //holonomy face list
		FaceLink faces1=new FaceLink(packData,Fl1); //TODO use incoming border vertex to build holonomy
		double err =overlapFlowerInRandom(n,v,faces1);
		msg("random_in Ahflors error, "+err);
		cpCommand(packData,"repack");
		cpCommand(packData,"layout");
		cpCommand(packData,"disp -w -c -R");
		return 1;
	}
	public int a1_overlap_random_out(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v,n,b1,b2;
		try {
			items=flagSegs.get(0);
			n = Integer.parseInt((String)items.get(0));
			v = Integer.parseInt((String)items.get(1));
			b1 = Integer.parseInt((String)items.get(2));
			b2 = Integer.parseInt((String)items.get(3));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get n, v, b1, or b2");
		}
		String Fl2 = "13 14 15 16 17 18 19 20 21 22 23 24 13";
		String Fl1 = "7 6 5 4 3 2 1 12 11 10 9 8 7"; //holonomy face list
		FaceLink faces1=new FaceLink(packData,Fl1); //TODO use incoming border vertex to build holonomy
		FaceLink faces2=new FaceLink(packData,Fl2);
		double err =overlapFlowerOutRandom(n,v,b1,b2,faces1,faces2);
		msg("random_out Ahflors error, "+err);
		cpCommand(packData,"repack");
		cpCommand(packData,"layout");
		cpCommand(packData,"disp -w -c -R");
		return 1;
	}
	public int a1_overlap_shuffle_in(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v,n;
		try {
			items=flagSegs.get(0);
			n = Integer.parseInt((String)items.get(0));
			v = Integer.parseInt((String)items.get(1));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get n, v, b1, or b2");
		}
//		String Fl2 = "13 14 15 16 17 18 19 20 21 22 23 24 13";
		String Fl1 = "7 6 5 4 3 2 1 12 11 10 9 8 7"; //holonomy face list
		FaceLink faces1=new FaceLink(packData,Fl1); //TODO use incoming border vertex to build holonomy
		
		double err1 = overlapFlowerInShuffle(packData,n,v,faces1);
		msg("shuffle_in Ahflors error, "+err1); //TEMP only for Ahflors exp
		cpCommand(packData,"repack");
		cpCommand(packData,"layout");
		cpCommand(packData,"disp -w -c -R");
		return 1;
	}
	public int a1_overlap_shuffle_out(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v;
		int n;
		try {
			items=flagSegs.get(0);
			n = Integer.parseInt((String)items.get(0));
			v = Integer.parseInt((String)items.get(1));
		} catch (Exception ex) {
			fbState =0;
			throw new ParserException("failed to get n or v");
		}
//		String Fl2 = "13 14 15 16 17 18 19 20 21 22 23 24 13";
		String Fl1 = "7 6 5 4 3 2 1 12 11 10 9 8 7"; //holonomy face list
		FaceLink faces1=new FaceLink(packData,Fl1); //TODO use incoming border vertex to build holonomy
		
		double err1 = overlapFlowerOutShuffle(packData,n,v,faces1);
		msg("shuffle_out Ahflors error, "+err1); //TEMP only for Ahflors exp
		cpCommand(packData,"repack");
		cpCommand(packData,"layout");
		cpCommand(packData,"disp -w -c -R");
		return 1;
	}
	public int a1_path_error(Vector<Vector<String>> flagSegs, Vector<String> items) {
		try {
			items = flagSegs.get(0);
		} catch(Exception ex) {
			fbState=0;
			throw new ParserException("failed to get any vertices");
		}
		double error=path_error(packData,flagSegs.elementAt(0));
		msg("Euclidean curve error, "+error);
		return 1;
	}
	public int a1_pos_diff(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1;
		int v2;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
		} catch (Exception ex) {
			throw new ParserException("failed to get 2 vertices");
		}
		Complex c1 = packData.getCenter(v1);
		Complex c2 = packData.getCenter(v2);
		double dist = genPtDist(c1, c2);
		msg("distance between vertices "+v1+"&"+v2+"= "+dist);
		return 1;
	}
	public int a1_rad_diff(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int v1;
		int v2;
		try {
			items=flagSegs.get(0);
			v1 = Integer.parseInt((String)items.get(0));
			v2 = Integer.parseInt((String)items.get(1));
		} catch (Exception ex) {
			throw new ParserException("failed to get 2 vertices");
		}
		double r1 = xRadToH(packData.getRadius(v1));
		double r2 = xRadToH(packData.getRadius(v2));
		//getRad converts x-radius to Hyperbolic radius used in CP display
		double diff = Math.abs(r1-r2);
		msg("difference of radii at "+v1+"&"+v2+"= "+diff);
		return 1;
	}
	
	/**
	 * Iteration for shift branching
	 * @param flagSegs
	 * @param items
	 * @return
	 */
	public int a1_rif(Vector<Vector<String>> flagSegs, Vector<String> items) {
		int passes=100*packData.nodeCount;
		try {
			items=flagSegs.get(0);
			passes= Integer.parseInt((String)items.get(0));
		} catch(Exception ex) {}

		double error=shuffle(passes);
		msg("iterated for shiftbranching, "+passes+" passes; error was "+error);
		return 1;
	}
	public int a1_sdisp(Vector<Vector<String>> flagSegs, Vector<String> items) {
		cpCommand(packData,"disp "+StringUtil.reconstitute(flagSegs));
		Iterator<CranePoint> cps=cranePts.iterator();
		CranePoint spt=null;
		CircleSimple sc1= null;
		CircleSimple sc2= null;
		int count=0;
		while (cps.hasNext()) {
			spt=cps.next();
			double R = spt.radius; //big circle
			double r = spt.radius*spt.shiftratio; // little circle
			int P = packData.nghb(spt.vert,spt.alpha);
			DispFlags dflags=new DispFlags("fc180",packData.cpScreen.fillOpacity);
			if (packData.hes<1) {
				Complex sptN = new Complex(0,0); // R normalized at origin
				double Rotate = 0+spt.jumpAng; 
				if (R-r<0) Rotate =-1*Math.PI+spt.jumpAng; //rotate pos if r is bigger
				
				Complex littleN = HES_Norm(xRadToH(R)-xRadToH(r),Rotate); //r's normalized center
				Complex alphaN = HES_Norm(xRadToH(packData.getRadius(packData.kData[spt.vert].flower[P]))
						+xRadToH(spt.radius), spt.theta); // alpha circle's normalized center
				Mobius mob=genMob(sptN,alphaN,packData.getCenter(spt.vert),
						packData.getCenter(packData.kData[spt.vert].flower[P]));
				//mobius map that takes sptN and alphaN to their respective laid out centers
				Complex littleC =spt.littleCenter = mob.apply(littleN);
				packData.cpScreen.drawCircle(littleC,spt.shiftratio*packData.getRadius(spt.vert),dflags);
				spt.littleHES =packData.hes;
				count++;
			}
			if (packData.hes==1) {
//TODO find this circles instead with overlap of big circle.
//spt.petalI is NOT carried over with a copy. 
				int a=0,b=0,c=0,d=0;
				for (int i=0;i<spt.num-1;i++) {
					if (Math.abs(spt.petalI[i]-1)<1.0E-10 && 
							Math.abs(spt.petalI[i+1]-1)<1.0E-10) {
						a=packData.kData[spt.vert].flower[i];
						b=packData.kData[spt.vert].flower[i+1];
						for (int j=i+1;j<spt.num-1;j++) {
							if (Math.abs(spt.petalI[j]-1)<1.0E-10 && 
									Math.abs(spt.petalI[j+1]-1)<1.0E-10) {
								c=packData.kData[spt.vert].flower[j];
								d=packData.kData[spt.vert].flower[j+1];
								i=spt.num;//kicks out
							}//if j
						}//for j
					}//if i
				}//for i
			double lcR=packData.getRadius(spt.vert);
			double x=0.1;
			int n=0;
			double ra =packData.getRadius(a);
			double rb =packData.getRadius(b);
			double rc =packData.getRadius(c);
			double rd =packData.getRadius(d);
			Complex Ca =packData.getCenter(a);
			Complex Cb =packData.getCenter(b);
			Complex Cc =packData.getCenter(c);
			Complex Cd =packData.getCenter(d);
			sc1 =SphericalMath.s_compcenter(Ca, Cb, ra, rb, lcR);
			sc2 =SphericalMath.s_compcenter(Cc, Cd, rc, rd, lcR);
			double low_err = SphericalMath.s_dist(sc1.center,sc2.center);
			while(Math.abs(low_err)>OKERR && n<10000) {
				double new_lcR =lcR-lcR*x;
				sc1 =SphericalMath.s_compcenter(Ca, Cb, ra, rb, new_lcR);
				sc2 =SphericalMath.s_compcenter(Cc, Cd, rc, rd, new_lcR);
				double new_err = SphericalMath.s_dist(sc1.center,sc2.center);
				if (new_err>low_err) {
					x=-x*0.5;
				} else {
					low_err=new_err;
					lcR=new_lcR;
				}
				n++;
			}//end find littlecircle while			
			packData.cpScreen.drawCircle(sc1.center,sc1.rad,dflags);
			count++;
			}//if hes==1
		}//end of spt while
		repaintMe();
		return count;
	}
	
	public int a1_spt(Vector<Vector<String>> flagSegs, Vector<String> items) {
		double t;
		double s;
		int v; int P;
		try {
			items=flagSegs.get(0);
			t= Double.parseDouble((String)items.get(0));
			s= Double.parseDouble((String)items.get(1));
			P= Integer.parseInt((String)items.get(2));
			v=NodeLink.grab_one_vert(packData,(String)items.get(3));
		} catch(Exception ex) {
			fbState=0;
			throw new ParserException("failed to get t=theta, s=ratio, " +
					"P=petal, or v=vertex");
		}
		if (P==0) P=packData.kData[v].flower[0]; 
		if (v<1 || v>packData.nodeCount+1 || packData.isBdry(v)
				|| packData.getNum(v)<3 || s<-1 || s>1 
				|| packData.nghb(v, P)==-1) {
			Oops("spt usage:  v <1, v>nodecount, or v is on boundary");
			return 0;
		}		
		
		CranePoint spt=new CranePoint(packData,v,t,s,P);
		spt.radius=packData.getRadius(spt.vert);
		// put this at front of vector (updateStatus will eliminate the later ones)
		cranePts.insertElementAt(spt,0);  
		updateStatus();
		return 1;
	}
	public int a1_turn_sum(Vector<Vector<String>> flagSegs, Vector<String> items) {
		//turning angle sum
		try {
			items = flagSegs.get(0);
			Integer.parseInt((String)items.get(0)); // ?? don't know what this does
		} catch(Exception ex) {
			fbState=0;
			throw new ParserException("failed to get a border vertex");
		}
		double totalsum = 0.0;
		double totalerr =0.0; ////TEMP only for Ahflors exp
		Iterator<String> itr = items.iterator();
		while (itr.hasNext()) {
			int n = Integer.parseInt((String)itr.next()); //convert elements to integer
			double sum1 = turnAngleSum(packData,n);
			double err = Math.pow(sum1-2*Math.PI,2); //TEMP only for Ahflors exp
			totalerr += err; //TEMP only for Ahflors exp
			totalsum += sum1;
		}
		msg("turining angle sum, "+totalsum);
		totalerr = Math.sqrt(totalerr);//TEMP only for Ahflors exp
		msg("Ahflors err, "+totalerr); //TEMP only for Ahflors exp
		return 1;
	}
	public int a1_weigh(Vector<Vector<String>> flagSegs, Vector<String> items) {
		if (fbState<2)
			throw new ParserException("branch vertices have not been chosen");
		double x1,x2;
		try {
			items=flagSegs.get(0);
			x1= Double.parseDouble((String)items.get(0));
			x2= Double.parseDouble((String)items.get(1));
		} catch (Exception ex) {
			throw new ParserException("usage: weight <w1,w2>");
		}
		packData.set_aim_default();
		packData.setAim(branchVert[0][0],
				packData.getAim(branchVert[0][0])+x1*Math.PI);
		packData.setAim(branchVert[0][1],
				packData.getAim(branchVert[0][1])+x2*Math.PI);
		packData.setAim(branchVert[0][2],
				packData.getAim(branchVert[0][2])+excessBranching-(x1+x2)*Math.PI); // remainder
		return 1;
	}
	//End cmd.starts

//Back to normal structure	
	
    /**
     * store info on the commands this extender will be getting from the user.
     */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("branchAt","v1 v2 v3",null,"Set 3 interior branch vertices"));
		cmdStruct.add(new CmdStruct("weight","w1 w2",null,"Set weights: of v1, v2 to w1*pi and w2*pi"));
		cmdStruct.add(new CmdStruct("compute","n",null,"iterate recomputing at most n times"));
		cmdStruct.add(new CmdStruct("data",null,null,"report on branch vertices, their weights"));
		cmdStruct.add(new CmdStruct("cur_error",null,null,"report the current error"));
		cmdStruct.add(new CmdStruct("spt","t s v",null,"set shiftvertex data: t=theta/Pi, s=small/large ratio, vert v"));
		cmdStruct.add(new CmdStruct("rif","N",null,"riffle routine with shiftpoints, N passes"));
		cmdStruct.add(new CmdStruct("lay",null, null, "layout with center cirle only"));
		cmdStruct.add(new CmdStruct("sdisp",null, null, "displays the small shift circle"));
			}

	/**
	 * Measures error (using Frobenius norm or distance) of a provide Flist or String 
	 * of closed chain of faces. It then repacks for inv dist, fracturing, or standard
	 * as appropiate.  
	 * Note If packing includes shiftpoints then they are converted to inversive distances.
	 * @param items closed chain of faces.
	 * @param n number of iterations.
	 * @param type Type of measurement, 0 =Frobenius Norm and 1 =distance. 
	 * @return: Error
	 */
	public double fundChainError(Vector<String> items,int n, int type,int repack) {
		//For shiftoints
		String flist =vectorToString(items," ");
		return fundChainError(flist, n, type,repack);
	}
	
	/**
	 * Measures error (using Frobenius norm or distance) of a provide Flist or String 
	 * of closed chain of faces. It then repacks for inv dist, fracturing, or standard
	 * as appropiate.  
	 * Note If packing includes shiftpoints then they are converted to inversive distances.
	 * @param items closed chain of faces.
	 * @param n number of iterations.
	 * @param type Type of measurement, 0 =Frobenius Norm and 1 =distance.
	 * @param repack 1=yes, 2=no. 
	 * @return Error
	 */
		//For shiftoints
	public double fundChainError(String items,int n,int type,int repack) {
		int shffd=0;
		if (cranePts.isEmpty()==false && repack==1) {
			double shuff_err =shuffle(n);
			if (shuff_err >=0.000001) {
				msg("WARNING: shuffle error greater than 0.000001");
			}
			for (int v=1;v<=packData.nodeCount;v++) {
				if (vertStatus[v]==2) {
					shiftToOverlaps(v);
					shffd=1;
					//necess only if chain passes through shiftpoints
				}
			}
			if (shffd==1) { //repacked below
//				fullLayout(packData);
//				cpCommand(packData,"repack -o");
				cpCommand(packData,"layout");//TODO: Remove?
//				cranePts.clear();
			}
		}
		else if (cranePts.isEmpty()==true && repack==1 && shffd==0) {
			if (!packData.overlapStatus) cpCommand(packData,"repack");
			else cpCommand(packData,"repack -o");
			cpCommand(packData,"layout");
			//warning if run twice data might be lost and repack might be 
			//done with non-shiftpoints and non-overlaps
		}
		FaceLink faces1=new FaceLink(packData,items);
		if (faces1==null || faces1.size()==0) {
			throw new ParserException("failed to get facelist");
		}
//		cpCommand(packData,"repack"); //repack -o
		double error =-1.0;
		if (type ==1) {
			cpCommand(packData,"disp -F "+items+""); //layout
			cpCommand(packData,"disp -F "+items+""); //layout
			Complex z0 =packData.getCenter(packData.faces[faces1.getFirst()].vert[0]);
			Complex z1 =packData.getCenter(packData.faces[faces1.getFirst()].vert[1]);
			cpCommand(packData,"disp -F "+items+""); //layout
			Complex w0 =packData.getCenter(packData.faces[faces1.getFirst()].vert[0]);
			Complex w1 =packData.getCenter(packData.faces[faces1.getFirst()].vert[1]);
			error =genPtDist(z0,w0)+genPtDist(z1,w1);
		}
		else if (type ==0) {
			Mobius mob1 =holonomy_mob(faces1);
//			mob1 =holonomy_mob(faces1);
//			FNorm =frobNorm(mob1); //need to run twice?
//			double diff =Math.sqrt(2)-FNorm;
//			double sqr =Math.pow(diff,2);
//			error =Math.sqrt(sqr);
			error=frobNorm(mob1);
		}
		else if (type!=1||type!=0) {
			Oops("error type needs to be 0 (frob.) or 1 (dist.)");
		}
		return error;
	}

	/**
	 * Creates a list of border faces on b's border element
	 * @param b border vertex
	 * @param pd incoming packData
	 * @return face list as a String
	 */
	public String findBorderChain(int b) {
		if (!packData.isBdry(b) || b<0) Oops("b must be a boundary circle");
		int first_b =b;
		int stop=0;
		String Flist ="";
		int skip_first =0;
		while (stop ==0) {
			int count=0;
			while (count+skip_first<packData.getNum(b)) {
				Flist =Flist+""+packData.kData[b].faceFlower[count+skip_first]+" ";
				count++;
			}
			b =packData.kData[b].flower[packData.getNum(b)]; //next bdry vertex
			skip_first =1; //avoids repetitive faces in Flist
			if (b ==first_b) stop=1;
		}
		return Flist;
	}
	
	/**
	 * Finds a neighboring border vertex if given a border vertex. 
	 * Need to add incoming last border vertex to avoid redundancies
	 * Should use 'kData[b1].nextVert' instead. 
	 * @param p
	 * @param b1
	 * @return
	 */
	public int getNextBdry(PackData p,int b1) {
		if (!p.isBdry(b1)) {
			Oops("b1 must be a boundary vertex"); 
		}
		int i=0; //counter vertex
		int next=0;
		while (i<p.getNum(b1)) {
			if (i==p.getNum(b1)) {
				Oops("b1's border element in not connected");
			}
			
			if (p.isBdry(p.kData[b1].flower[i])) {
				next=p.kData[b1].flower[i]; //next vertex in border element
				i=p.getNum(b1)+1; //ends while
			}
			
			else if (!p.isBdry(p.kData[b1].flower[i])) {
				i++; //moves to next vertex in b1's flower
			}
		}
			return next;
	}

	public double expDWborderAdj(int v1, int v2, int v3, int n,String Fl1,String Fl2,
			double x, int new_or_old) {
		//finds border vertex
		int bd =0; for (int num=1;num<=packData.nodeCount;num++) { //finds a border vertex
			if (packData.isBdry(num)) {
				bd=num;num=1+packData.nodeCount;
			}
		}
		if (packData.hes!=0) {
			cpCommand(packData,"geom_to_e");
		}
		//Error checks
		//Done with error checks

		//finds unadjusted error
		cpCommand(packData,"set_aim 4.0 "+v1+" "+v2+" "+v3);
		if (new_or_old==0) {
			cpCommand(packData,"repack -o");
		}
		if (new_or_old==1) {
			cpCommand(packData,"repack");
		}
		cpCommand(packData,"layout");
		cpCommand(packData,"norm_scale -c "+bd+" 1.0");
		double err =DWFerror(Fl1,Fl2,0,500);

		//adjusts border circles
		double newerr=-1.0;
		int counter=0;
		int b1=bd;
		while (err>TOLER && counter<n) {
			int next_b =packData.kData[b1].flower[packData.kData[b1].nextVert];
			if (!packData.isBdry(next_b)) {
				Oops(""+next_b+" is not a border circle");//TODO delete check?
			}
			cpCommand(packData,"norm_scale -c "+bd+" 1.0");
			double oldRad =packData.getRadius(b1);
			Random generator = new Random();
			double lim =0.0;
			while (lim<0.005 || lim>50.0) {//controls growth of border radii.
				lim =oldRad*(generator.nextDouble()*x+(1-x/2));//sets b1 to new rad
				packData.setRadius(b1, lim);
			}
			//pack with new border
			if (new_or_old==0) {
				cpCommand(packData,"repack -o");
			}
			if (new_or_old==1) {
				cpCommand(packData,"repack");
			}
			cpCommand(packData,"layout");
			cpCommand(packData,"norm_scale -c "+bd+" 1.0");

			newerr =DWFerror(Fl1,Fl2,0,500);

			if (newerr<err || err==-1.0) {
				err =newerr; //new low error and keep border radii change
			} else packData.setRadius(b1,oldRad); //reset border change
			b1 =next_b;
			counter++;
			if (counter % 10 ==0) {
				System.out.println(""+counter+": lowest_err = "+err+";");
			}
		}//end while
		System.out.println(""+counter+": lowest_err = "+err+";");
		return err;
	}
	
	
	public double borderAdj(PackData pd,int v1,int v2,int b1,int n,double x) {
		if (pd.hes!=0) {
			Oops("Packing must be Euclidean");
		}
		if (!pd.isBdry(b1)) {
			Oops("b1 must be a border circle");
		}
		int counter=0;
		cpCommand(pd,"repack");
		cpCommand(pd,"norm_scale -c "+v1+" 1.0");
		cpCommand(pd,"layout");
		cpCommand(pd,"fix");
//		normScale(pd,v1,1.0,b1);
//		double err1 =genPtDist(pd.rData[v1].center,pd.rData[v2].center);
		double err =Math.sqrt(Math.pow(1-pd.getRadius(v1)/pd.getRadius(v2),2)
				+Math.pow(1-pd.getRadius(v2)/pd.getRadius(v1),2));
//		double err=Math.sqrt(err1*err1+err2*err2);
		double newerr =0.0;
		while (err>TOLER && counter<n) {
			int next =pd.kData[b1].flower[pd.kData[b1].nextVert];//next vertex in border element
			double oldRad =pd.getRadius(b1);
			double lim =0.0;
			while (lim<0.05 || lim>5.0) { //bound border adjustments
				Random generator = new Random();
				pd.setRadius(b1,oldRad*(generator.nextDouble()*x+(1-x/2)));//sets b1 to new rad
				cpCommand(pd,"repack");
				cpCommand(pd,"norm_scale -c "+v1+" 1.0");
				cpCommand(pd,"layout");
				cpCommand(pd,"fix");
//				cpCommand(pd,"norm_scale -c "+b1+" 1.0");
				//lim =pd.rData[b1].rad;
				lim=1.0;
				if (lim>5.0) {
					pd.setRadius(b1,oldRad);
					lim=1.0;
				}
				if (lim<0.05) {
					pd.setRadius(b1,oldRad);
					lim=1.0;
				}
			}
			//pd.repack_call(1000); //repacks with b1 set to newRad
//			Random generator = new Random();
//			pd.rData[b1].rad =oldRad*(generator.nextDouble()*0.5+0.75);//sets b1 to new rad
//			normScale(pd,v1,1.0,b1);
			newerr =Math.sqrt(Math.pow(1-pd.getRadius(v1)/pd.getRadius(v2),2)
					+Math.pow(1-pd.getRadius(v2)/pd.getRadius(v1),2));
//			double newerr2 =genPtDist(pd.rData[v1].center,pd.rData[v2].center); //finds error with new rad
//				newerr =Math.sqrt(newerr1*newerr1+newerr2*newerr2);
				if (newerr>err) {
					pd.setRadius(b1,oldRad);
				}
				else if (newerr<=err) {
					err = newerr;
				}
				b1 =next; //gets next border vertex
				counter++;
				if (counter % 10 ==0) msg(""+counter+", error="+err);
			}
		packData = pd;
		return err;
	}

	/**
	 * Randomly adjusts border radii of a simple connected Euclidean packing so that the
	 * Frobenius norm of a mobius map between two circles, v1 and v2, gets closer to the 
	 * identity while keeping the radii within a given difference. 
	 * @param pd incoming packing
	 * @param v1 vertex number of first center
	 * @param v2 vertex number of second center
	 * @param b1 a border circle 
	 * @param n number of iterations 
	 * @param pre_err bound to keep v1 and v2's radii difference
	 * @return normalized distance of v1 and v2's centers
	 */
	public double borderAdjPos(int v1,int v2,int b1,int n,
			int new_or_old, double x) {
		//Error checks
		if (packData.hes!=0) Oops("Packing must be Euclidean");
		if (!packData.isBdry(b1)) Oops("b1 must be a border circle");
		if (new_or_old!=1 & new_or_old!=0) Oops("bol must be 1 or 0");
		//Done with error checks
		if (new_or_old==0) {
			cpCommand(packData,"repack -o");
		}
		if (new_or_old==1) {
			cpCommand(packData,"repack");
		}
		cpCommand(packData,"layout");
		cpCommand(packData,"norm_scale -c "+v1+" 1.0");
		//note this doesnt work for hes=-1.
//		Mobius mob =genMob(packData.rData[v1].center, 
//				packData.rData[v1].center.add(packData.rData[v1].rad),
//				packData.rData[v2].center,
//				packData.rData[v2].center.add(packData.rData[v2].rad));
//		double err =frobNorm(mob);
//////		double err1 = Math.sqrt(2)-frobNorm(mob);
//////		double err =Math.sqrt(err1*err1);
		double err1 =genPtDist(packData.getCenter(v1),packData.getCenter(v2));
		double err2 =Math.abs(packData.getRadius(v1)-packData.getRadius(v2));
		double err= err1+err2*Math.sqrt(2);
		double newerr =0.0;
		int counter=0;
		while (err>TOLER && counter<n) {
			int next =packData.kData[b1].flower[packData.kData[b1].nextVert];//next vertex in border element
			double oldRad =packData.getRadius(b1);
			Random generator = new Random();
			packData.setRadius(b1,oldRad*(generator.nextDouble()*x+(1-x/2)));//sets b1 to new rad
			if (new_or_old==0) {
				cpCommand(packData,"repack -o");
			}
			if (new_or_old==1) {
				cpCommand(packData,"repack");
			}
			cpCommand(packData,"layout");
			cpCommand(packData,"norm_scale -c "+v1+" 1.0");
//			mob =genMob(packData.rData[v1].center, 
//					packData.rData[v1].center.add(packData.rData[v1].rad),
//					packData.rData[v2].center,
//					packData.rData[v2].center.add(packData.rData[v2].rad));
//			newerr =frobNorm(mob);
////			double newerr1 = Math.sqrt(2)-frobNorm(mob);
////			newerr =Math.sqrt(newerr1*newerr1);
			double newerr1 =genPtDist(packData.getCenter(v1),packData.getCenter(v2));
			double newerr2 =Math.abs(packData.getRadius(v1)-packData.getRadius(v2));
			newerr= newerr1+newerr2*Math.sqrt(2);
			if (newerr>=err || java.lang.Double.isNaN(newerr)==true) {
				packData.setRadius(b1,oldRad);
			}
			else {
				err = newerr;
				msg("New lowest error ="+err);
			}
			b1 =next; //gets next border vertex
			counter++;
			if (counter % 100 ==0) {
				System.out.println(""+counter+": lowest_err = "+err+";");
			}
		}
		return err;
	}

	/**
	 * Randomly adjusts border radii of a simple connected Euclidean packing so that the
	 * Frobenius norm of a mobius map between two circles, v1 and v2, gets closer to the 
	 * identity while keeping the radii within a given difference. 
	 * @param pd incoming packing
	 * @param v1 vertex number of first center
	 * @param v2 vertex number of second center
	 * @param b1 a border circle 
	 * @param n number of iterations 
	 * @param pre_err bound to keep v1 and v2's radii difference
	 * @return normalized distance of v1 and v2's centers
	 */
	public double borderAdjRad(int v1,int v2,int b1,int n,
			int new_or_old, double x) {
		//Error checks
		if (packData.hes!=0) Oops("Packing must be Euclidean");
		if (!packData.isBdry(b1)) Oops("b1 must be a border circle");
		if (new_or_old!=1 & new_or_old!=0) Oops("bol must be 1 or 0");
		//Done with error checks
		if (new_or_old==0) {
			cpCommand(packData,"repack -o");
		}
		if (new_or_old==1) {
			cpCommand(packData,"repack");
		}
		cpCommand(packData,"layout");
		cpCommand(packData,"norm_scale -c "+v1+" 1.0");
		//note this doesnt work for hes=-1.
//		Mobius mob =genMob(packData.rData[v1].center, 
//				packData.rData[v1].center.add(packData.rData[v1].rad),
//				packData.rData[v2].center,
//				packData.rData[v2].center.add(packData.rData[v2].rad));
//		double err =frobNorm(mob);
//////		double err1 = Math.sqrt(2)-frobNorm(mob);
//////		double err =Math.sqrt(err1*err1);
//		double err =genPtDist(packData.rData[v1].center,packData.rData[v2].center);
		double err =Math.abs(packData.getRadius(v1)-packData.getRadius(v2));
//		double err= err1+err2*Math.sqrt(2);
		double newerr =0.0;
		int counter=0;
		while (err>TOLER && counter<n) {
			int next =packData.kData[b1].flower[packData.kData[b1].nextVert];//next vertex in border element
			double oldRad =packData.getRadius(b1);
			Random generator = new Random();
			packData.setRadius(b1,oldRad*(generator.nextDouble()*x+(1-x/2)));//sets b1 to new rad
			if (new_or_old==0) {
				cpCommand(packData,"repack -o");
			}
			if (new_or_old==1) {
				cpCommand(packData,"repack");
			}
			cpCommand(packData,"layout");
			cpCommand(packData,"norm_scale -c "+v1+" 1.0");
//			mob =genMob(packData.rData[v1].center, 
//					packData.rData[v1].center.add(packData.rData[v1].rad),
//					packData.rData[v2].center,
//					packData.rData[v2].center.add(packData.rData[v2].rad));
//			newerr =frobNorm(mob);
			newerr =Math.abs(packData.getRadius(v1)-packData.getRadius(v2));
////			double newerr1 = Math.sqrt(2)-frobNorm(mob);
////			newerr =Math.sqrt(newerr1*newerr1);
//			double newerr1 =genPtDist(packData.rData[v1].center,packData.rData[v2].center);
//			double newerr2 =Math.abs(packData.rData[v1].rad-packData.rData[v2].rad);
//			newerr= newerr1+newerr2*Math.sqrt(2);
			if (newerr>=err || java.lang.Double.isNaN(newerr)==true) {
				packData.setRadius(b1,oldRad);
			}
			else {
				err = newerr;
				msg("New lowest error ="+err);
			}
			b1 =next; //gets next border vertex
			counter++;
			if (counter % 100 ==0) {
				System.out.println(""+counter+": lowest_err = "+err+";");
			}
		}
		return err;
	}
	
	public double craneAdjPos(PackData pd,int v0, int v1,int v2,int n, double x) {
		//Set shiftpoint parameters and create Cranepoint constructor
		double s0= 1.0; //shift ratio
		double t0= 0.0;
		int P =0;
		CranePoint spt0=new CranePoint(pd,v0,t0,s0,P);
		spt0.radius=pd.getRadius(spt0.vert); // put this at front of vector (updateStatus will eliminate the later ones)
		updateStatus();
//		cranePts.insertElementAt(spt0,0);  
		
		shuffle(10000);
		fullLayout(pd);
		cpCommand(pd,"center_vert "+v1);
//		Mobius mob =genMob(pd.rData[v1].center, 
//				pd.rData[v1].center.add(pd.rData[v1].rad),
//				pd.rData[v2].center,
//				pd.rData[v2].center.add(pd.rData[v2].rad));
//		double err1 = 1000000*Math.abs(2-Math.pow(frobNorm(mob),2));
		double err = Math.abs(pd.getRadius(v1)-pd.getRadius(v2));
//		double err =genPtDist(pd.rData[v1].center,pd.rData[v2].center); //finds error with new rad
		double newerr =0.0;
		int counter=0;

		while (err>TOLER && counter<n) {
			double old_s0=s0;
			double old_t0=t0;
			Random generator = new Random();
			s0= generator.nextDouble(); //shift ratio
			t0= 0.0; //generator.nextDouble();
//			spt0.shiftratio =s0; spt0.theta =t0;
			spt0=new CranePoint(pd,v0,t0,s0,P);
			spt0.radius=pd.getRadius(spt0.vert); // put this at front of vector (updateStatus will eliminate the later ones)
			updateStatus();
			double rif_err =1.0; int counter2 =0; double lay_err =1.0;
			while (rif_err>OKERR && counter2<1000 && lay_err>0.0001) {
				rif_err =shuffle(1000); //TODO adjust
				fullLayout(pd);
				lay_err =totalLayoutError()/pd.nodeCount;
				counter2++;
			}
			fullLayout(pd);
//			Mobius newmob =genMob(pd.rData[v1].center, 
//					pd.rData[v1].center.add(pd.rData[v1].rad),
//					pd.rData[v2].center,
//					pd.rData[v2].center.add(pd.rData[v2].rad));
//			double newerr1 =1000000*Math.abs(2-Math.pow(frobNorm(newmob),2));
//			newerr =genPtDist(pd.rData[v1].center,pd.rData[v2].center); //finds error with new rad
			newerr = Math.abs(pd.getRadius(v1)-pd.getRadius(v2));
//			newerr =Math.sqrt(newerr1*newerr1);
				if (newerr>=err || rif_err>OKERR || lay_err>0.0001) {
					spt0.shiftratio =old_s0; spt0.theta =old_t0;
				}
				
				else if (newerr<err) {
					err = newerr;
				}
				counter++;
				if (counter % 10 ==0) msg(""+counter+", error="+err);
			}
		msg("shiftratio ="+spt0.shiftratio+" & theta ="+spt0.theta);
		return err;
	}
	
	/**
	 * Finds the mobius trasnformation which maps z1,z2 to w1,w2
	 * @param p incoming Packdata
	 * @param z1 Complex numbers
	 * @param z2
	 * @param w1
	 * @param w2
	 * @return mobius transformation
	 */
	public Mobius getMob(PackData p, Complex z1,Complex z2, Complex w1, Complex w2) { 
		Mobius mob=new Mobius(); // initialize transformation 
		if (p.hes<0) mob=Mobius.auto_abAB(z1,z2,w1,w2);
		else {
			Complex denom=z1.minus(z2);
			if (denom.abs()<.00000000001) {
				CirclePack.cpb.myErrorMsg("There were IOExceptions");
				return null;
	    }
	    mob.a=w1.minus(w2).divide(denom);
	    mob.b=w1.minus(mob.a.times(z1));
		}
	  return mob;
	}
	
	/**
	 * Scales a simply connected Euclidean packing	
	 * @param p, incoming packing
	 * @param v, vertex to be set to norm
	 * @param norm,
	 * @param b, border vertex 
	 */
	public void normScale(PackData p, int v, double norm, int b) {
		cpCommand(p,"repack");
		cpCommand(p,"layout");
		double scale =norm/p.getRadius(v);
		p.setRadius(v,norm);
		int next =p.kData[b].flower[p.kData[b].nextVert];
		while (next!=b) {
			double rrad=p.getRadius(next);
			p.setRadius(next,scale*rrad);
			next =p.kData[next].flower[p.kData[next].nextVert];
		}
	}
	
	/**
	 * Finds and sets the inversive distances for an existing placed circle and its petals.
	 * The intention is to preserve an existing structure by using overlaps
	 * @param pd incoming packing.
	 * @param v vertex of placed (should be unpacked) circle.
	 */
	public void findSetInv(int v) {
		int p = packData.kData[v].flower[0];
		if (!packData.overlapStatus) { //alocates space for overlaps
			packData.alloc_overlaps();
		}
		int np =packData.getNum(v);
		p =0; //petal counter
		while (p<=np) {
			int w=packData.kData[v].flower[p];
			double idist =findInv(v,w);
			packData.set_single_invDist(v,w,idist);
			p++;
		}
		cpCommand(packData,"repack -o");
//		cpCommand(packData,"layout");
    }

	/**
	 * Returns inversive distance for current position of two vertices
	 * @param pd
	 * @param v1
	 * @param v2
	 * @return 
	 */
	public double findInv(int v1,int v2) {
		double inv=-10;
		double v1_rad =packData.getRadius(v1);
		double v2_rad =packData.getRadius(v2);
		Complex v1_cent =packData.getCenter(v1);
		Complex v2_cent =packData.getCenter(v2);
		double dist =genPtDist(v1_cent,v2_cent);
		if (packData.hes==1) { //Spherical Case
			Oops("spherical case not complete");
		}
		if (packData.hes==-1) { //hyperbolic case
			inv =(Math.cosh(dist)-Math.cosh(v1_rad)*Math.cosh(v2_rad))
				/(Math.sinh(v1_rad)*Math.sinh(v2_rad));
		} 
		if (packData.hes==0) {
			inv =(dist*dist-v1_rad*v1_rad-v2_rad*v2_rad)/(2*v1_rad*v2_rad);
		}
		return inv;
	}
	
	public double findInv(Complex z1,Complex z2, double r1, double r2) {
		double inv=-10;
		double dist =genPtDist(z1,z2);
		if (packData.hes==1) { //Spherical Case
			Oops("spherical case not complete");
		}
		if (packData.hes==-1) { //hyperbolic case
			inv =(Math.cosh(dist)-Math.cosh(xRadToH(r1))*Math.cosh(xRadToH(r2)))
				/(Math.sinh(xRadToH(r1))*Math.sinh(xRadToH(r2)));
		} 
		if (packData.hes==0) {
			dist =Math.sqrt(Math.pow(z1.real()-(z2.real()), 2)+Math.pow(z1.imag()-z2.imag(), 2));
			inv =(dist*dist-r1*r1-r2*r2)/(2*r1*r2);
		}
		return inv;
	}

	public double DWFerror(String FL1,String FL2,int type,int n) {
		int bd =0; for (int num=1;num<=packData.nodeCount;num++) { //finds a border vertex
			if (packData.isBdry(num)) {
				bd=num;num=1+packData.nodeCount;
			}
		}
		double err =fundChainError(FL1,n,type,1);
		err =err + fundChainError(FL2,n,type,0);
		err =err + fundChainError(findBorderChain(bd),n,type,0);
		return err;
	}
	public double DWFerror(String FL1,String FL2,int type) {
		int bd =0; for (int num=1;num<=packData.nodeCount;num++) { //finds a border vertex
			if (packData.isBdry(num)) {
				bd=num;num=1+packData.nodeCount;
			}
		}
		double err =fundChainError(FL1,250,type,1);
		err =err + fundChainError(FL2,250,type,0);
		err =err + fundChainError(findBorderChain(bd),250,type,0);
		return err;
	}
	
	/**
	 * Experiments on the annulus using randomly found inv distances on tri-fracturings.
	 * @param v0 branch vertices
	 * @param u0 
	 * @param b any border vertex
	 * @param n number of iterations
	 * @param type 0 =Frobenius Norm; 1 =distance
	 * @return
	 */
	public double[][] expWeirFracInv(PackData pd, int v1, int v2, int v3,
			int u1, int u2, int u3, String FL1, String FL2, int n) {
		double[][] A =new double[n][7];
		Random generator = new Random();
		if (pd.what_face(v1, v2, v3)==0 || pd.what_face(u1, u2, u3)==0) {
			Oops("{"+v1+","+v2+","+v3+"}, {"+u1+","+u2+","+u3+"} must form faces.");
		}
		int V =v1; int U =u1;
		branchVert[V][0] =v1;branchVert[V][1]=v2;branchVert[V][2]=v3;
		branchVert[U][0] =u1;branchVert[U][1]=u2;branchVert[U][2]=u3;
		
		if (!pd.overlapStatus) pd.alloc_overlaps(); //Allocate space for overlaps in packData
			
		double err =1.0;double iv1=0,iv2=0,iv3=0, iu1=0,iu2=0,iu3=0, lowest_err =-1.0;
//		double best_iv1=0,best_iv2=0,best_iv3=0, best_iu1=0, best_iu2=0, best_iu3=0;
		for (int j=1;j<n;j++) {
			cpCommand(packData,"Cleanse");
			cpCommand(packData,"infile_read test.p");
			cpCommand(packData,"repack"); //aims and repacking lost in read

			//first frac face
			double x1 =generator.nextDouble(),x2 =generator.nextDouble(),x3 =generator.nextDouble();
			double sum=x1+x2+x3;
			iv1=2*x1/sum-1; iv2=2*x2/sum-1; iv3=2*x3/sum-1;
			pd.set_single_invDist(v1,v2,iv1); //set overlaps across branch point
			pd.set_single_invDist(v2,v3,iv2); //set overlaps across branch point
			pd.set_single_invDist(v3,v1,iv3); //set overlaps across branch point
			
			//second frac face
			x1 =generator.nextDouble(); x2 =generator.nextDouble(); x3 =generator.nextDouble();
			sum=x1+x2+x3;
			iu1=2*x1/sum-1; iu2=2*x2/sum-1; iu3=2*x3/sum-1;
			pd.set_single_invDist(u1,u2,iu1); //set overlaps across branch point
			pd.set_single_invDist(u2,u3,iu2); //set overlaps across branch point
			pd.set_single_invDist(u3,u1,iu3); //set overlaps across branch point
			
			double loc_err=1.0; int count2=1;
			//bounces back and forth computing fracbranching at v1 and v2.
			while (loc_err>OKERR && count2<=100) {
				loc_err =errorTotal();
				count2++;
			}
			
			err =DWFerror(FL1,FL2,300);int i=0;
			
			A[j][i]= iv1;i++;//Double.toString(r1); 
			A[j][i]= iv2;i++;//Double.toString(r2);
			A[j][i]= iv3;i++;//Double.toString(err);
			A[j][i]= iu1;i++;
			A[j][i]= iu2;i++;
			A[j][i]= iu3;i++;
			A[j][i]= err;
			
			if ((err<lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
				lowest_err =err;
//				best_iv1=iv1;best_iu1=iu1;
//				best_iv2=iv2;best_iu2=iu2;
//				best_iv3=iv3;best_iu3=iu3;
				System.out.println(""+j+": Lowest Error = "+lowest_err);
				System.out.println("edge assignments "+iv1+" "+iv2+" "+iv3);
				System.out.println("edge assignments "+iu1+" "+iu2+" "+iu3);				
//			aimS1=best_s1;aimT1=best_t1; //Drop to make random
//			aimS2=best_s2;aimT2=best_t2; //drop to make random
//			aimS3=best_s3;aimT3=best_t3; //drop to make random
			}
			if (j%20==0 || j==1) {
				System.out.println(""+j+": Lowest Error = "+lowest_err);
				System.out.println("edge assignments "+iv1+" "+iv2+" "+iv3);
				System.out.println("edge assignments "+iu1+" "+iu2+" "+iu3);
			}
		}
	return A; //returns a nx3 matrix
}
	
	/**
	 * Experiments on the annulus using randomly found inv distances on tri-fracturings.
	 * @param v0 branch vertices
	 * @param u0 
	 * @param b any border vertex
	 * @param n number of iterations
	 * @param type 0 =Frobenius Norm; 1 =distance
	 * @return
	 */
	public int expWeirFracInvFind( int v0, int u0, int n,int type) {
		Random generator = new Random();
		@SuppressWarnings("unused")
		int bd=-1;
		if (packData.isBdry(v0) || packData.isBdry(u0)) {
			Oops(""+v0+" and "+u0+" must be interior vertices");
		}
		double lowest_err =-1.0, best_invV =-2.0, best_invU =-2.0;
		int best_v1 =-1, best_v2 =-1,best_u1 =-1,best_u2 =-1,V =0, U =1;
		branchVert=new int[2][3];
		branchVert[V][0]= v0;branchVert[U][0]=u0;//TODO: try with 3 branch vertices.
		//finds a border vertex
		cpCommand(packData,"infile_read test.p");
		for (int num=1;num<=packData.nodeCount;num++) { 
			if (packData.isBdry(num)) {
				bd=num;num=1+packData.nodeCount;
			}
		}
		
		int total =0; //total iterartions
		for(int i=0;i<packData.getNum(v0);i++) {
			int v1 =branchVert[V][1] =packData.kData[v0].flower[i];
			int v2 =branchVert[V][2] =packData.kData[v0].flower[i+1];
			if (!packData.isBdry(v1) || !packData.isBdry(v2)) {
				for(int j=0;j<packData.getNum(u0);j++) {
					int u1 =branchVert[U][1] =packData.kData[u0].flower[j];
					int u2 =branchVert[U][2] =packData.kData[u0].flower[j+1];
					if (!packData.isBdry(u1) || !packData.isBdry(u2)) {						
						for(int count=0;count<n;count++) {
							cpCommand(packData,"infile_read test.p");
							if (!packData.overlapStatus) packData.alloc_overlaps(); //Allocate space for overlaps in packData
							double invV= generator.nextDouble(); //overlap
							double invU= generator.nextDouble();
							packData.set_single_invDist(v0,v2,invV); //set overlaps across branch point
							packData.set_single_invDist(u0,u2,invU);

							double fracerr =fracShuffle3(300,0);
							if (fracerr>OKERR) msg("WARNING: fracShuffle error = "+fracerr);
							
							String FL1 = "10 7 2 3 4 16 23 24 10";
							String FL_alt = "12 13 14 15 22 25 26 11 12";
							//avoids running through fractured triangle
							if (packData.what_face(u0,u1,u2)==10||packData.what_face(u0,u1,u2)==24
									||packData.what_face(v0,v1,v2)==10||packData.what_face(v0,v1,v2)==24) {
								FL1 =FL_alt;
							}
							String FL2 ="1";
							
							double err =DWFerror(FL1,FL2,type);
							
							total++;
							if ((err<=lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
								lowest_err =err;best_v1=v1;best_v2=v2;best_u1=u1;best_u2=u2;
								best_invV =invV;best_invU =invU;
								msg(""+total+" of "+packData.getNum(v0)*packData.getNum(u0)*n+
										": new lowest Error = "+lowest_err);
								msg("["+v0+" "+best_v1+" "+best_v2+"] inv dist "+best_invV+" "+v0+" "+best_v2);
								msg("["+u0+" "+best_u1+" "+best_u2+"] inv dist "+best_invU+" "+u0+" "+best_u2);
							}
							if (count%5==0) {
								msg(""+(count+1)+": on "+total+" of "+packData.getNum(v0)*packData.getNum(u0)*n);
							}
						}//end for count
					}//end bdry check
				}
				msg("Lowest Error = "+lowest_err+" after "+total+" trials");
				msg("["+v0+" "+best_v1+" "+best_v2+"] inv dist "+best_invV+" "+v0+" "+best_v2);
				msg("["+u0+" "+best_u1+" "+best_u2+"] inv dist "+best_invU+" "+u0+" "+best_u2);
				msg("["+packData.getAim(v0)+" "+packData.getAim(best_v1)+" "+packData.getAim(best_v2)+"]");
				msg("["+packData.getAim(u0)+" "+packData.getAim(best_u1)+" "+packData.getAim(best_u2)+"]");
			}//end for i
		}//end bdry check
		msg("DONE: expDWfracfind "+v0+" "+u0+" "+n+" "+type);
		return 1;
	}
	
	/**
	 * returns error for a DAF 
	 * @param type 0 =frob; 1 =dist
	 * @return error on the two border chains
	 */
	public double DAFerror(int type,int n) {
//		cpCommand(packData,"layout");
		int bd1 =packData.bdryStarts[1]; int bd2 =packData.bdryStarts[2];
		//bdry vertices from bdry components
		String FL1 =findBorderChain(bd1);String FL2 =findBorderChain(bd2);
		double err1 =fundChainError(FL1,n,type,1);
		double err2 =fundChainError(FL2,n,type,0);
		if (Math.abs(err1-err2)>TOLER) {
			msg("difference of error on chain exceeds TOLER");
		}
		return err1+err2;
	}
	
	public double DAFerror(int type) {
//		cpCommand(packData,"layout");
		int n = 250;//default iterations
		int bd1 =packData.bdryStarts[1]; int bd2 =packData.bdryStarts[2];
		//bdry vertices from bdry components
		String FL1 =findBorderChain(bd1);String FL2 =findBorderChain(bd2);
		double err1 =fundChainError(FL1,n,type,1);
		double err2 =fundChainError(FL2,n,type,0);
		if (Math.abs(err1-err2)>TOLER) {
			msg("difference of error on chain exceeds TOLER");
		}
		return err1;
	}
	
	/**
	 * Experiments on the annulus using randomly found inv distances on tri-fracturings.
	 * @param v0 branch vertices
	 * @param u0 
	 * @param b any border vertex
	 * @param n number of iterations
	 * @param type 0 =Frobenius Norm; 1 =distance
	 * @return
	 */
	public int expDAFfracInvFind( int v0, int u0, int n, int type) {
		Random generator = new Random();
		if (packData.isBdry(v0) || packData.isBdry(u0)) {
			Oops(""+v0+" and "+u0+" must be interior vertices");				
		}
		double lowest_err =-1.0, best_invV =-2.0, best_invU =-2.0; 
		int V =0, U =1, best_v1 =-1, best_v2 =-1,best_u1 =-1,best_u2 =-1;
		branchVert=new int[2][3];
		branchVert[V][0]=v0;branchVert[U][0]=u0;
		int total =0;
		for(int i=0;i<packData.getNum(v0);i++) {
			int v1 =branchVert[V][1] =packData.kData[v0].flower[i];
			int v2 =branchVert[V][2] =packData.kData[v0].flower[i+1];
			if (!packData.isBdry(v1) && !packData.isBdry(v2)) {
				for(int j=0;j<packData.getNum(u0);j++) {
					int u1 =branchVert[U][1] =packData.kData[u0].flower[j];
					int u2 =branchVert[U][2] =packData.kData[u0].flower[j+1];
					if (!packData.isBdry(u1) && !packData.isBdry(u2)) {
						for(int count=0;count<n;count++) {
							cpCommand(packData,"infile_read test.p");
							if (!packData.overlapStatus) packData.alloc_overlaps(); //Allocate space for overlaps in packData
							double invV= generator.nextDouble(); //overlap
							double invU= generator.nextDouble();
							packData.set_single_invDist(v0,v2,invV); //set overlaps across branch point
							packData.set_single_invDist(u0,u2,invU);
							
							fracShuffle3(250,0);
							double err =DAFerror(type);
							
							total++;
							if ((err<=lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
								lowest_err =err;best_v1=v1;best_v2=v2;best_u1=u1;best_u2=u2;
								best_invV =invV;best_invU =invU;
								msg("New lowest Error = "+lowest_err+" after "+total+" trials");
								msg("["+v0+" "+v1+" "+v2+"] inv dist "+best_invV+" "+v0+" "+best_v2);
								msg("["+u0+" "+u1+" "+u2+"] inv dist "+best_invU+" "+u0+" "+best_u2);
											}
							if (count%10==0) {
								msg(""+i+":"+count+"");
							}
						}//end for count
					}//end if bdry check
					msg("Lowest Error = "+lowest_err+" after "+total+" trials");
					msg("["+v0+" "+v1+" "+v2+"] inv dist "+best_invV+" "+v0+" "+best_v2);
					msg("["+u0+" "+u1+" "+u2+"] inv dist "+best_invU+" "+u0+" "+best_u2);
					msg("["+packData.getAim(v0)+" "+packData.getAim(best_v1)+" "+packData.getAim(best_v2)+"]");
					msg("["+packData.getAim(u0)+" "+packData.getAim(best_u1)+" "+packData.getAim(best_u2)+"]");
				}//end for j
			}//end if bdryFlag check
		}//end for i
		msg("DONE: expDAFfracInvFind "+v0+" "+u0+" "+n);
		return 1;
	}

	public int expDAFfracInv( int f1, int f2, int n, int type) {
		Random generator = new Random();
		if (packData.faces[f2].isBdryFace(packData)==true || packData.faces[f1].isBdryFace(packData)==true) {
			Oops("both "+f2+" and "+f1+" must be interior faces");
		}
		branchVert=new int[2][3];
		branchVert[0][0]=packData.faces[f1].vert[0];
		branchVert[0][1]=packData.faces[f1].vert[1];
		branchVert[0][2]=packData.faces[f1].vert[2];
		branchVert[1][0]=packData.faces[f2].vert[0];
		branchVert[1][1]=packData.faces[f2].vert[1];
		branchVert[1][2]=packData.faces[f2].vert[2];

		double lowest_err=-1.0, best_invV =-2.0,best_invU =-2.0; 
		int count =0, best_v1=-1,best_v2=-1,best_u1=-1,best_u2=-1;
		while (count<n && (lowest_err == -1 || lowest_err >TOLER)) {
			cpCommand(packData,"cleanse");
			cpCommand(packData,"infile_read test.p");
			if (!packData.overlapStatus) packData.alloc_overlaps(); //Allocate space for overlaps in packData
			int v1= generator.nextInt(3);int v2= generator.nextInt(2)+1;
			int u1= generator.nextInt(3);int u2= generator.nextInt(2)+1;
			double invV= 2*generator.nextDouble()-1; //overlap
			double invU= 2*generator.nextDouble()-1;
			packData.set_single_invDist(packData.faces[f1].vert[v1],
			         packData.faces[f1].vert[(v1+v2)%3],
			         invV); //set overlaps across branch point
			packData.set_single_invDist(packData.faces[f2].vert[u1],
                      packData.faces[f2].vert[(u1+u2)%3],
                      invV); //set overlaps across branch point
			
			fracShuffle3(250,0);
			double err =DAFerror(type);

			if ((err<=lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
				lowest_err =err;best_v1=v1;best_v2=v2;best_u1=u1;best_u2=u2;
				best_invV =invV;best_invU =invU;
				msg("New lowest Error = "+lowest_err+" after "+count+" trials");
				msg("set_over "+invV+" "+packData.faces[f1].vert[v1]+" "
						+packData.faces[f1].vert[(v1+v2)%3]+" " +
						"; set_over "+invU+" "+packData.faces[f2].vert[u1]+" "
						+packData.faces[f1].vert[(u1+u2)%3]+
						"; |fb| branchat "+branchVert[0][0]+" "+branchVert[0][1]+" "+branchVert[0][2]+
						"; |fb| branchat "+branchVert[1][0]+" "+branchVert[1][1]+" "+branchVert[1][2]);
			}
			if (count%10==0) msg(""+count);
			count++;
		}//end while
		msg("New lowest Error = "+lowest_err+" after "+count+" trials");
		msg("set_over "+best_invV+" "+packData.faces[f1].vert[best_v1]+" "
				+packData.faces[f1].vert[(best_v1+best_v2)%3]+" " +
				"; set_over "+best_invU+" "+packData.faces[f2].vert[best_u1]+" "
				+packData.faces[f2].vert[(best_u1+best_u2)%3]+
				"; |fb| branchat "+branchVert[0][0]+" "+branchVert[0][1]+" "+branchVert[0][2]+
				"; |fb| branchat "+branchVert[1][0]+" "+branchVert[1][1]+" "+branchVert[1][2]);
		return 1;
	}
	
	public void findBestFracDAF() {
		int num =packData.faceCount, best_face1=-1, best_face2=-1;
		double lowest_err =-1.0;
		branchVert=new int[2][3];
		for (int i=1;i<=num;i++) { //first vertex i
			int count=1;
			if (packData.faces[i].isBdryFace(packData)==false) {
				for (int j=1;j<=num;j++) {
					if (packData.faces_incident(i, j)==false && packData.faces[j].isBdryFace(packData)==false) {
						cpCommand(packData,"cleanse");
						cpCommand(packData,"infile_read test.p");
						branchVert[0][0]=packData.faces[i].vert[0];
						branchVert[0][0]=packData.faces[i].vert[1];
						branchVert[0][0]=packData.faces[i].vert[2];
						branchVert[1][0]=packData.faces[j].vert[0];
						branchVert[1][0]=packData.faces[j].vert[1];
						branchVert[1][0]=packData.faces[j].vert[2];

						fracShuffle3(250,0);
						double err =DAFerror(0);

						if ((err<lowest_err || lowest_err<0.0) && java.lang.Double.isNaN(err)==false) {
							lowest_err =err; best_face1=i; best_face2=j;
							msg("New lowest Error = "+lowest_err+ " on faces "+i+" and "+j);
						}
						if (count%10==0) {
							msg(""+count+": lowest Error = "+lowest_err+ " on faces "+i+" and "+j);
							msg("|fb| branchat "+packData.faces[best_face1].vert[0]+
									" "+packData.faces[best_face1].vert[1]+"" +
									" "+packData.faces[best_face1].vert[2]);
							msg("|fb| branchat "+packData.faces[best_face2].vert[0]+
									" "+packData.faces[best_face2].vert[1]+"" +
									" "+packData.faces[best_face2].vert[2]);
						}
					}//end if in j					
				}//end for j
			}// end if in i
		}// end for i
		if (best_face1==-1 || best_face2 ==-1) {
			Oops("Not enough interior faces");
		}
		msg("DONE: find_frac_best. Lowest Error = "+lowest_err+ " on faces "+best_face1+" and "+best_face2);
		msg("|fb| branchat "+packData.faces[best_face1].vert[0]+
				" "+packData.faces[best_face1].vert[1]+"" +
				" "+packData.faces[best_face1].vert[2]);
		msg("|fb| branchat "+packData.faces[best_face2].vert[0]+
				" "+packData.faces[best_face2].vert[1]+"" +
				" "+packData.faces[best_face2].vert[2]);
	}
	
	/**
	 * Finds two interior branch points which result in lowest error on border chain of 
	 * an annulus.
	 * @return an array of length 3, [0],[1] are two vertices; [2] is the error.
	 */
	public double[] findBestBranchDAF() {
		double[] Vert_best = new double[3];int num =packData.nodeCount;
		double lowest_err =-1.0;
		for (int i=1;i<=num;i++) { //first vertex i
			int count=1;
			if (!packData.isBdry(i)) {
				for (int j=1;j<=num;j++) {
					if (!packData.isBdry(j) && i!=j && packData.nghb(i, j)==-1 && j!=i) {
						cpCommand(packData,"infile_read test.p");
						cpCommand(packData,"set_aim 4.0 "+i+" "+j);
						cpCommand(packData,"repack");
						cpCommand(packData,"layout");
						double err =DAFerror(0,500);//TODO change type
						if (err<lowest_err||lowest_err==-1.0) {
							Vert_best[0]=i; Vert_best[1]=j;
							Vert_best[2]=err;lowest_err=err;
						}
					count++;if (count%10==0) msg(""+count);
					} //end j checks
				}//end j
			}//end bdry check i
		}//end for i
		return Vert_best;
	}

	/**
	 * Finds two interior branch points which result in lowest error on border chain of 
	 * an annulus.
	 * @return an array of length 3, [0],[1] are two vertices; [2] is the error.
	 */
	public int findBestBorderDAF() {
		double[] Vert_best = new double[3];
		int num =packData.nodeCount;
		double lowest_err =-1.0;
		for (int B1=1;B1<=num;B1++) { //first vertex i
			int count=1;
			if (!packData.isBdry(B1)) {
				for (int B2=1;B2<=num;B2++) {
					if (!packData.isBdry(B2) && B1!=B2 && packData.nghb(B1, B2)==-1 && B2!=B1) {
						Complex z1=new Complex(0,0),z2=new Complex(0,0);double r1=0,r2=0;
						if (packData.hes==-1) {
							CircleSimple sC1=HyperbolicMath.h_to_e_data(packData.getCenter(B1),
									packData.getRadius(B1));
							CircleSimple sC2=HyperbolicMath.h_to_e_data(packData.getCenter(B2),
									packData.getRadius(B2));
							z1 =sC1.center;r1 =sC1.rad;
							z2 =sC2.center;r2 =sC2.rad;
						}
						if (packData.hes==0) {
							z1 =packData.getCenter(B1);r1 =packData.getRadius(B1);
							z2 =packData.getCenter(B2);r2 =packData.getRadius(B2);
						}
						Mobius mob =genMob(z1, z1.add(r1),z2, z2.add(r2));
						double err = frobNorm(mob);
						if (err<lowest_err||lowest_err==-1.0) {
							Vert_best[0]=B1; Vert_best[1]=B2;
							Vert_best[2]=err;lowest_err=err;
						}
					count++;if (count%10==0) msg(""+count);
					} //end j checks
				}//end j
			}//end bdry check i
		}//end for i
		msg("Closet two vertices "+Vert_best[0]+" and "+Vert_best[1]+" ; error ="+Vert_best[2]);
		return 1;
	}	
	
	

	public double[] findBestBranchDW(String FL1,String FL2,int type) {
		double[] Vert_best = new double[4];
		@SuppressWarnings("unused")
		int bd=-1;
		int num =packData.nodeCount;
		double lowest_err =-1.0;
		cpCommand(packData,"infile_read test.p");
		for (int count=1;count<=num;count++) { //finds a border vertex
			if (packData.isBdry(count)) {
				bd=count;count=1+num;
			}
		}//end find border vertex
		int count=1;
		for (int i=1;i<=num;i++) { //first vertex i
			if (!packData.isBdry(i)) {
				for (int j=2;j<=num;j++) {//second vertex j 
					if (!packData.isBdry(j) && i!=j && packData.nghb(i, j)==-1) {
						for (int k=2;k<=num;k++) {//third vertex k //TODO back to k=2
							if (!packData.isBdry(k) && k!=j && k!=i &&
									packData.nghb(k, j)==-1 && packData.nghb(i, k)==-1) {
								cpCommand(packData,"cleanse");
								cpCommand(packData,"infile_read test.p");
								cpCommand(packData,"set_aim 4.0 "+i+" "+j+" "+k);
								cpCommand(packData,"repack");
								double err =DWFerror(FL1,FL2,type);
								if (err<lowest_err||lowest_err==-1.0) {
									Vert_best[0]=i; Vert_best[1]=j;Vert_best[2]=k;
									Vert_best[3]=err;lowest_err=err;
								}
							}//end k checks
							count++;if (count%50==0) msg(""+count);
						} //end for k
					} //end j checks
					count++;if (count%50==0) msg(""+count);
				}//end for j
			}//end bdry check i
			count++;if (count%50==0) msg(""+count);
		}//end for i
		return Vert_best;
	}
	
	/**
	 * Experiments on the annulus using randomly found varaibles 
	 * for incoming shiftpoints
	 * @param v1 branch vertices
	 * @param v2 
	 * @param b0 any border vertex
	 * @param n number of iterations
	 * @param type 0 =Frobenius Norm; 1 =distance
	 * @return
	 */
	public int expDAFShiftFind(int p1, int v1, int p2, int v2, int n,int type) {
		double lowest_err =-1.0;
		int[] V = {v1, v2}; int m =V.length;
		int[] P = {p1, p2};
		double[] T = {1.0, 1.0};
		double[] S= {1.0, 1.0};
		
		//best values thus far
		double[]aimT = { 1.0, 1.0};
		double[]eT ={.9999, .9999};
		Random generator = new Random();
		for (int i=0;i<m;i++) {
			double best_t=-1.0,best_s=-1.0;
			for (int count=0;count<n;count++) {
				cpCommand(packData,"Cleanse");
				cpCommand(packData,"infile_read test.p");
				cpCommand(packData, "set_aim 4.0 "+v1+" "+v2+"");
				cpCommand(packData,"repack"); 
				//Variables
				double et=2*eT[i],a=aimT[i]-eT[i];
				T[i]=generator.nextDouble()*et+a;
				
				
//				double t =2.0,a=-1.0,s=1.0,b=0,aimT=0,aimS,eT=0.0,eS=0.0;
//				if (i==0) { //narrows scope of search
//					aimT =aimv1[0]; eT=.99999;
//					aimS =aimv1[1]; eS=.499;
//					t =2*eT; a=aimT-eT; s =2*eS; b=aimS-eS; 
//				}
//				if (i==1) {
//					aimT =aimv2[0]; eT=.99999;
//					aimS =aimv2[1]; eS=.499;
//					t =2*eT; a=aimT-eT; s =2*eS; b=aimS-eS; 
//				}
//				double et1 =2*eT1, a1=aimT1-eT1, es1 =2*eS1, b1=aimS1-eS1;
//				t1= generator.nextDouble()*et1+a1;//theta ratio
//				if (Math.abs(2-t1)<.0001) t1=1.9999;
//				//shuffle gets an error for t approx 2.0; this needs to be fixed
//				s1= generator.nextDouble()*es1+b1;//shift ratio
//
//				T[i]= generator.nextDouble()*t+a;
//				S[i]= generator.nextDouble()*s+b;
				//Set shiftpoint parameters and create Cranepoint constructor
				CranePoint spt1=new CranePoint(packData,V[i],T[i],S[i],P[i]);
				spt1.radius=packData.getRadius(spt1.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt1,0);  
				updateStatus();
				//Set 2nd shiftpoint
				CranePoint spt2=new CranePoint(packData,V[(i+1)%m],T[(i+1)%m],S[(i+1)%m],P[(i+1)%m]);
				spt2.radius=packData.getRadius(spt2.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt2,0);  
				updateStatus();

				double err =DAFerror(type);
				
				if ((err<lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
					lowest_err =err; best_s=S[i];best_t=T[i];
					msg(""+count+": Lowest Error = "+lowest_err+
							" |fb| spt "+best_t+" "+best_s+" "+V[i]+" ;" +
							" |fb| spt "+T[(i+1)%m]+" "+S[(i+1)%m]+" "+V[(i+1)%m]+";");
				}
				if (count%10==0) {
					msg(""+count+" trials. Lowest error ="+lowest_err);
				}
				if (best_t==-1.0||best_s==-1.0) {
					best_t=1.0;best_s=1.0;
				}
				T[i] =best_t;S[i] =best_s;
			} //end for count
			msg(" Lowest Error = "+lowest_err+
					" |fb| spt "+best_t+" "+best_s+" "+V[i]+" ;" +
					" |fb| spt "+T[(i+1)%m]+" "+S[(i+1)%m]+" "+V[(i+1)%m]+";");
		}//end for i
		msg("DONE "+n+" trials with expDAFShiftFind; Lowest Error = "+lowest_err+
				" |fb| spt "+S[0]+" "+S[0]+" "+V[0]+" ;" +
				" |fb| apt "+T[1%m]+" "+S[1%m]+" "+V[1%m]+";");
		return 1;
	}
	
	/**
	 * Experiments on the annulus using randomly found varaibles for incoming shiftpoints
	 * @param v1 branch vertices.
	 * @param v2 
	 * @param b0 any border vertex
	 * @param P alpha petal vertices. Values of 0 will skip shifting at the vertex, 
	 * and will instead place normal branching there. 
	 * @return
	 */
	public double[][] expDAFshift(int v1,int P1,int v2,int P2,int n) {
		double[][] A =new double[n][5];
		Random generator = new Random();
		double lowest_err =10.0;double best_t1=-1.0,best_s1 =-1.0,best_t2=-1.0,best_s2=-1.0;
		double t1=1.0,s1=1.0,t2=1.0,s2=1.0;
		double aimT1 =1.0, eT1=0.99999;double aimS1 =0.5, eS1=0.499999;
		double aimT2 =1.0, eT2=0.99999;double aimS2 =0.5, eS2=0.499999;

//		lowest_err = 8.900872929707518E-8;
//		lowest_err = 0.0013028641628226763;
//		aimT1=1.0; aimS1=0.8769713295565075;
//		aimT2=1.0; aimS2=1.0;
//		reflex symmetric case. make sure petal for other BP is 0.
		
//		lowest_err = 0.07801956681220501;
//		aimT1=1.0; aimS1=0.8843523166323006;
//		aimT2=1.0; aimS2=1.0;//geom_e
//		reflex symmetric case. trying to find other solution.

		
		
//		lowest_err = 0.2689626085821538;
//		aimT1=1.6864260300175606; aimS1=0.6649620561853068;
//		aimT2=1.0166895875935096; aimS2=0.5126924100870485;
//		aimT2=1.0; aimS2=1.0;
//		aimT2=1.3807992838439267; aimS2=0.8578974380791486;
//		eT1=0.00002; eS1=0.00001;
//		eT2=0.00002; eS2=0.00001;
//		non-symmetirc case from reflex symmetry on 3 1 and 47 84

//		lowest_err = 0.32306041533810753;
//		aimT1=1.9999; aimS1=0.779852412548281;
//		aimT2=1.0; aimS2=1.0;
//		eT1=0.0002; eS1=0.0001;
//		eT2=0.0; eS2=0.0;
//		non-symmetirc case from reflex symmetry on 3 1 and 47 84

//		lowest_err = 0.6066545205457082;
//		aimT1=0.9503902594975862; aimS1=0.04861488288679518;
//		aimT2=1.0; aimS2=1.0;

//		lowest_err = 0.03892154161487858;8 9 and 12 11 on simple
//		aimT1=0.14129057068748857; aimS1=0.0956455501614699;
//		aimT2=1.4657424243066786; aimS2=0.22717399494555282;
//		eT1=0.00002; eS1=0.00001;
//		eT2=0.00002; eS2=0.00001;
		
//		lowest_err = 6.047460828633109E-14;
//		lowest_err = 1.4390871732466674E-13;
//		aimT1=1.6778531032482762; aimS1=0.05128573796914261;
//		aimT2=1.521621910303056; aimS2=0.1921294473873591;

//		lowest_err = 1.0799407134156864;
//		aimT1=1.7867262874694645; aimS1=-0.0967433436832939;
//		aimT2=1.3109897808506876; aimS2=0.05970180206512193;
//		eT1=0.000001; eS1=0.000001;
//		eT2=0.000001; eS2=0.000001;
		
//		1 7 and 4 10
		
//		lowest_err = 0.9147338858489873;
//		aimT1=1.9695725084375928; aimS1=0.3902112358686192;
//		aimT2=0.5712144510907676; aimS2=0.959361290001435;
//		lowest_err = 1.034012355841448;
//		aimT1=0.06334872693284022; aimS1=0.2105536678716894;
//		aimT2=-0.36394195709314614; aimS2=0.8658113815952393;
//		  eT1=0.99999; 
//	  	  eS1=0.0;
//		  eT2=0.99999; 
//		  eS2=0.0;
		// 10 9 12 7 on small annulus
		
		//lowest_err = 0.5069195034962845;
//		lowest_err = 0.5114285067636912;
//		aimT1=1.5497603559812534; aimS1=0.16456258982905858;
//		aimT2=1.6923348225989665; aimS2=0.454173661849172;
//		lowest_err = 0.4847688212967193;
//		aimT1=1.3415841670069666; aimS1=0.1602873109591381;
//		aimT2=1.7700617067201794; aimS2=0.46924911184782225;

//		lowest_err = 1.5318095808632508;
//		aimT1=1.3926542807103066; aimS1=0.02725730749413329;
//		aimT2=1.7694662355356385; aimS2=0.08386971573267113;
//		
		
//		lowest_err = 0.11594690763687863;
		aimT1=1.0; aimS1=0.8723109526755483;
		aimT2=1.0; aimS2=1.0;
		eT1=0.9999;eS1=.15;
//
//		eT1=0.0001; eS1=0.0001;
		
//		lowest_err =-1.0;
//		aimT1=1.0; aimS1=0.5;//note the changed this for graphing
//		aimT2=1.0; aimS2=0.0;
//		eT1=0.9999; eS1=0.4999;
//		eT2=0.0; eS2=0.0;
//		eT2=0.9999; eS2=0.4999;
		
//		lowest_err = 0.5753635487411514;
//		aimT1=0.2565751976011325; aimS1=-0.41273328945982424;
//		aimT2=1.2644819195095882; aimS2=0.31340021186091904;
//		eT1=0.00001; eS1=0.00001;
//		eT2=0.00001; eS2=0.00001;
		
		for (int j=0;j<n;j++) {
			cpCommand(packData,"Cleanse");
			cpCommand(packData,"infile_read test.p");
			cpCommand(packData,"set_aim 4.0 "+v1+" "+v2);
//			cpCommand(packData, "geom_to_e");
//			cpCommand(packData,"norm_scale -u "+bd);
//			cpCommand(packData,"repack");

			//Set shiftpoint parameters and create Cranepoint constructor
			if (P1!=0) {
				double et1 =2*eT1, a1=aimT1-eT1, es1 =2*eS1, b1=aimS1-eS1;
				t1= generator.nextDouble()*et1+a1;//theta ratio
				if (Math.abs(2-t1)<.0001) t1=1.9999;
				//shuffle gets an error for t approx 2.0; this needs to be fixed
				s1= generator.nextDouble()*es1+b1;//shift ratio
				CranePoint spt1=new CranePoint(packData,v1,t1,s1,P1);
				spt1.radius=packData.getRadius(spt1.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt1,0); 
				updateStatus();
			}
			
			//Set 2nd shiftpoint
			if (P2!=0) {
				double et2 =2*eT2, a2=aimT2-eT2, es2 =2*eS2, b2=aimS2-eS2; 
				t2= generator.nextDouble()*et2+a2;//theta angle
				if (Math.abs(2-t2)<.0001) t2=1.9999;
				s2= generator.nextDouble()*es2+b2;//shift angle
				if (s2>1.0) s2=1.0;
				
				CranePoint spt2 =new CranePoint(packData,v2,t2,s2,P2);
				spt2.radius=packData.getRadius(spt2.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt2,0);  
				updateStatus();
			}
			
//			cpCommand("geom_to_e"); //TODO remove
			double err =DAFerror(0,500);//type=0;
			int i =0;
			A[j][i]= t1;i++;
			A[j][i]= s1;i++;//Double.toString(r2);
			A[j][i]= t2;i++;
			A[j][i]= s2;i++;
			A[j][i]= err;//Double.toString(err);
			if (err<lowest_err || lowest_err <0.0) {
				lowest_err =err; 
				best_t1=t1;best_s1=s1;
				best_t2=t2;best_s2=s2;
				System.out.println(""+j+" lowest_err = "+lowest_err+";");
				System.out.println("aimT1="+best_t1+"; aimS1="+best_s1+";");
				System.out.println("aimT2="+best_t2+"; aimS2="+best_s2+";");
//				System.out.println("|fb| spt "+best_t1+" "+best_s1+" "+P1+" "+v1);
//				System.out.println("|fb| spt "+best_t2+" "+best_s2+" "+P2+" "+v2);
//				aimS1=best_s1;aimT1=best_t1; //Drop to make random
//				aimS2=best_s2;aimT2=best_t2; //drop to make random
			}
			if (j%100==0) {
				System.out.println(""+j+" lowest_err = "+lowest_err+";");
				System.out.println("aimT1="+best_t1+"; aimS1="+best_s1+";");
				System.out.println("aimT2="+best_t2+"; aimS2="+best_s2+";");
				System.out.println("|fb| spt "+best_t1+" "+best_s1+" "+P1+" "+v1);
				System.out.println("|fb| spt "+best_t2+" "+best_s2+" "+P2+" "+v2);
			}
		}
		msg("DONE!: "+n+" trials with exp_DAF_shift "+v1+" "+P1+" "+v2+" "+P2);
		msg("lowest Error = "+lowest_err+" " +
				"|fb| spt "+aimT1+" "+aimS1+" "+P1+" "+v1+";" +
				"|fb| spt "+aimT2+" "+aimS2+" "+P2+" "+v2+";");
		return A; //returns a nxm matrix
	}

	/**
	 * Experiments on the Annulus using shiftpoints placed in the map inverted 
	 * on a branch point.The borders are represented with 2 auxillary circles. 
	 * Error is measured according to the difference in position and/or 
	 * rad of these auxillary circls.
	 * @param v1 branch vertex in the interior. The other branch vertex is the border 
	 * @param P1 alpha petal for shiftpoint.
	 * @param type error measured as 1) positon, 2) rad, or 3) both.
	 * @param b1,b2 the two auxillary circles.
	 * @param n number of iterations
	 * @return 1; error is outputted in message box.
	 */
	public double[][] expDAFshiftInv(int v1, int P1, int B1, int B2,int n) {
		double[][] A =new double[n][3];
		Random generator = new Random();
		double lowest_err =10.0;double best_t1=-1.0,best_s1 =-1.0;
		double t1=1.0,s1=1.0;
		double aimT1 =1.0, eT1=1.0;double aimS1 =0.5, eS1=0.5;
		
//		aimT1=1.0; aimS1=0.854004382428032;
//		lowest_err = 0.003109321821877173;
//		aimT1=-0.152040568242361; aimS1=0.9792822280084938;		
//		lowest_err = 6.114273066775068E-6;
//		aimT1=1.9297394879553826; aimS1=0.8433666071662855;
//		eT1=0.00002; eS1=0.00001; //single for reflex symmetry
		lowest_err = 0.001388695214810748;
		aimT1=1.8105457859356955; aimS1=0.42413844016509855;
		eT1=0.00000002; eS1=0.00000001; //dom4.p
		for (int j=0;j<n;j++) {
			cpCommand(packData,"Cleanse");
			cpCommand(packData,"infile_read test.p");
			cpCommand(packData,"set_aim 4.0 "+v1);

			//Set shiftpoint parameters and create Cranepoint constructor
			double et1 =2*eT1, a1=aimT1-eT1, es1 =2*eS1, b1=aimS1-eS1;
			t1= generator.nextDouble()*et1+a1;//theta ratio
			if (Math.abs(2-t1)<.0001) t1=1.9999;
			//shuffle gets an error for t approx 2.0; this needs to be fixed
			s1= generator.nextDouble()*es1+b1;//shift ratio

			CranePoint spt1=new CranePoint(packData,v1,t1,s1,P1);
			spt1.radius=packData.getRadius(spt1.vert); // put this at front of vector (updateStatus will eliminate the later ones)
			cranePts.insertElementAt(spt1,0); 
			updateStatus();
			
			shuffle(300);fullLayout(packData);
			Complex z1=new Complex(0,0),z2=new Complex(0,0);double r1=0,r2=0;
			if (packData.hes==-1) {
				CircleSimple sC1=HyperbolicMath.h_to_e_data(packData.getCenter(B1),
						packData.getRadius(B1));
				CircleSimple sC2=HyperbolicMath.h_to_e_data(packData.getCenter(B2),
						packData.getRadius(B2));
				z1 =sC1.center;r1 =sC1.rad;
				z2 =sC2.center;r2 =sC2.rad;
			}
			if (packData.hes==0) {
				z1 =packData.getCenter(B1);r1 =packData.getRadius(B1);
				z2 =packData.getCenter(B2);r2 =packData.getRadius(B2);
			}
			Mobius mob =genMob(z1, z1.add(r1),z2, z2.add(r2));
			double err = frobNorm(mob);

			if (err<lowest_err || lowest_err <0.0) {
				lowest_err =err; 
				best_t1=t1;best_s1=s1;
				System.out.println(""+j+" lowest_err = "+lowest_err+";");
				System.out.println("aimT1="+best_t1+"; aimS1="+best_s1+";");
//				System.out.println("|fb| spt "+best_t1+" "+best_s1+" "+P1+" "+v1);
				aimS1=best_s1;aimT1=best_t1; //Drop to make random
			}
			if (j%100==0) {
				System.out.println(""+j+" lowest_err = "+lowest_err+";");
				System.out.println("aimT1="+best_t1+"; aimS1="+best_s1+";");
				System.out.println("|fb| spt "+best_t1+" "+best_s1+" "+P1+" "+v1);
			}
			int i =0;
			A[j][i]= t1;i++;
			A[j][i]= s1;i++;//Double.toString(r2);
			A[j][i]= err;//Double.toString(err);
		}
		msg("DONE!: "+n+" trials with exp_DAF_shift "+v1+" "+P1);
		msg("lowest Error = "+lowest_err+" " +
				"|fb| spt "+aimT1+" "+aimS1+" "+P1+" "+v1+";");
		return A;
	}
	
	/**
	 * Experiments on the Torus using randomly found varaibles for incoming shiftpoints
	 * @param v1 branch vertices
	 * @param v2
	 * @param v3
	 * @param n number of iterations
	 * @param FL closed chain of faces
	 * @param type 0 =Frobenius Norm; 1 =distance
	 * @return
	 */
	public double[][] expWeirShift(int P1,int P2, int P3, int v1, int v2, int v3, int n,String FL1,
			String FL2,int type) {
		double[][] A=new double[n][7];
		double lowest_err =-1.0;
		double best_s1 =-1.0,best_s2=-1.0,best_s3 =-1.0;
		double best_t1=-1.0,best_t2=-1.0,best_t3 =-1.0;
		double s1=0,s2=0,s3=0,t1=0,t2=0,t3=0;

		double aimT1 =1.0, eT1=0.999999;double aimS1 =0.5, eS1=0.499999;
		double aimT2 =1.0, eT2=0.999999;double aimS2 =0.5, eS2=0.499999;
		double aimT3 =1.0, eT3=0.999999;double aimS3 =0.5, eS3=0.499999;
		
//		lowest_err = 0.05348348321773898;
//		aimT1=1.2447114403969242; aimS1=0.7809855332321464;
//		aimT2=1.0424815690969278; aimS2=0.8074817502896324;
//		aimT3=0.0; aimS3=0.0;
		
//		lowest_err = 0.027403205904685146;
//		aimT1=0.9359756362899827; aimS1=0.5969014935783525;
//		aimT2=1.0880522327115405; aimS2=0.0637774612308702;
//		aimT3=1.5348302723473393; aimS3=-0.7336053854677335;

//		lowest_err = 0.050269458536176134;
//		aimT1=0.998683610196136; aimS1=0.620138481835278;
//		aimT2=1.1818684326926523; aimS2=0.05171592757227275;
//		aimT3=1.2506562130432681; aimS3=-0.7648798392452221;
		
//		lowest_err = 0.027567502279835242;
//		aimT1=0.9359690267587017; aimS1=0.5966442926220027;
//		aimT2=1.0880062092155085; aimS2=0.06349984717869837;
//		aimT3=1.5348302836644678; aimS3=-0.7317961474285587;

////		lowest_err = 0.027403205904685146;
//		aimT1=0.9359756362899827; aimS1=0.5969014935783525;
//		aimT2=1.0880522327115405; aimS2=0.0637774612308702;
//		aimT3=1.5348302723473393; aimS3=-0.7336053854677335;

//		lowest_err = 0.027400723673568498;
//		aimT1=0.9360493442342155; aimS1=0.5965169591994726;
//		aimT2=1.0880197952550819; aimS2=0.06379809074903033;
//		aimT3=1.5348722902296035; aimS3=-0.7332598682853982; //E-6
		
//		lowest_err = 0.054580557076707385;
//		aimT1=0.8960751887973095; aimS1=0.7773688850665732;
//		aimT2=0.5407607449487402; aimS2=0.8285862475804957;
//		aimT3=0.0; aimS3=0.0;

		aimT1=1.0; aimS1=0.16885;
		
		eT1 =0.0000; eS1 =0.0001;
//		eT2 =0.0000; eS2 =0.0000;
//		eT3 =0.0000; eS3 =0.0000;
//		
		Random generator = new Random();
		for (int j=1;j<n;j++) {
			cpCommand(packData,"Cleanse");
			cpCommand(packData,"infile_read test.p");
			cpCommand(packData,"set_aim 4.0 "+v1+" "+v2+" "+v3+"");
			cpCommand(packData,"repack"); //aims and repacking lost in read

			if (P1!=0) {
				double et1 =2*eT1, a1=aimT1-eT1, es1 =2*eS1, b1=aimS1-eS1;
				t1= generator.nextDouble()*et1+a1;//theta ratio
				if (Math.abs(2-t1)<.0001) t1=1.9999;
				//shuffle gets an error for t approx 2.0; this needs to be fixed
				s1= generator.nextDouble()*es1+b1;//shift ratio
				if (s1>1) s1=1.0;if (s1<1E-8) s1=1E-8;
				CranePoint spt1=new CranePoint(packData,v1,t1,s1,P1);
				spt1.radius=packData.getRadius(spt1.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt1,0); 
				updateStatus();
			}

			if (P2!=0) {
				double et2 =2*eT2, a2=aimT2-eT2, es2 =2*eS2, b2=aimS2-eS2; 
				t2= generator.nextDouble()*et2+a2;//theta angle
				if (Math.abs(2-t2)<.0001) t2=1.9999;
				s2= generator.nextDouble()*es2+b2;//shift angle
				if (s2>1) s2=1.0;if (s2<1E-8) s2=1E-8;
				CranePoint spt2 =new CranePoint(packData,v2,t2,s2,P2);
				spt2.radius=packData.getRadius(spt2.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt2,0);  
				updateStatus();
			}

			if (P3!=0) {
				double et3 =2*eT3, a3=aimT3-eT3, es3 =2*eS3, b3=aimS3-eS3;
				t3= generator.nextDouble()*et3+a3;//theta ratio
				if (Math.abs(2-t1)<.0001) t1=1.9999;
				//shuffle gets an error for t approx 2.0; this needs to be fixed
				s3= generator.nextDouble()*es3+b3;//shift ratio
				if (s3>1) s3=1.0;if (s3<1E-8) s3=1E-8;
				CranePoint spt3=new CranePoint(packData,v3,t3,s3,P3);
				spt3.radius=packData.getRadius(spt3.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt3,0); 
				updateStatus();
			}

			double err =DWFerror(FL1,FL2,type,300);int i=0;
			
			A[j][i]= t1;i++;//Double.toString(r1); 
			A[j][i]= s1;i++;//Double.toString(r2);
			A[j][i]= t2;i++;//Double.toString(err);
			A[j][i]= s2;i++;
			A[j][i]= t3;i++;
			A[j][i]= s3;i++;
			A[j][i]= err;
			
			if ((err<lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
				lowest_err =err;
				best_t1=t1;best_s1=s1;
				best_t2=t2;best_s2=s2;
				best_t3=t3;best_s3=s3;
				System.out.println(""+j+": Lowest Error = "+lowest_err);
				System.out.println("|fb| spt "+t1+" "+s1+" "+P1+" "+v1);
				System.out.println("|fb| spt "+t2+" "+s2+" "+P2+" "+v2);
				System.out.println("|fb| spt "+t3+" "+s3+" "+P3+" "+v3);

//				aimS1=best_s1;aimT1=best_t1; //Drop to make random
//				aimS2=best_s2;aimT2=best_t2; //drop to make random
//				aimS3=best_s3;aimT3=best_t3; //drop to make random
				System.out.println(""+j+" lowest_err = "+lowest_err+";");
				System.out.println("aimT1="+best_t1+"; aimS1="+best_s1+";");
				System.out.println("aimT2="+best_t2+"; aimS2="+best_s2+";");
				System.out.println("aimT3="+best_t3+"; aimS3="+best_s3+";");
			}
			if (j%20==0 || j==1) {
				System.out.println(""+j+": Lowest Error = "+lowest_err);
				System.out.println("|fb| spt "+best_t1+" "+best_s1+" "+P1+" "+v1);
				System.out.println("|fb| spt "+best_t2+" "+best_s2+" "+P2+" "+v2);
				System.out.println("|fb| spt "+best_t3+" "+best_s3+" "+P3+" "+v3);

				System.out.println(j+" trials; lowest error ="+lowest_err);
				System.out.println(""+j+" lowest_err = "+lowest_err+";");
				System.out.println("aimT1="+best_t1+"; aimS1="+best_s1+";");
				System.out.println("aimT2="+best_t2+"; aimS2="+best_s2+";");
				System.out.println("aimT3="+best_t3+"; aimS3="+best_s3+";");
//				errorMsg(j+" trials; lowest error ="+lowest_err);
			}
		}//for j
		msg(""+n+": Lowest Error = "+lowest_err+
				" |fb| spt "+best_t1+" "+best_s1+" "+P1+" "+v1+";" +
				" |fb| spt "+best_t2+" "+best_s2+" "+P2+" "+v2+";" +
				" |fb| spt "+best_t3+" "+best_s3+" "+P3+" "+v3+";");
		return A; //returns a nx3 matrix
	}
	
	/**
	 * Experiments on the Torus using randomly found varaibles for incoming shiftpoints
	 * @param v1 branch vertices
	 * @param v2
	 * @param v3
	 * @param n number of iterations
	 * @param FL closed chain of faces
	 * @param type 0 =Frobenius Norm; 1 =distance
	 * @return
	 */
	public int expWeirShiftFind(int v1, int v2, int v3, int n, String FL1,
			String FL2, int type) {
		double lowest_err =-1.0;
		int[] V = {v1, v2, v3};
		int m =V.length;
		@SuppressWarnings("unused")
		int bd=-1;
		cpCommand(packData,"infile_read test.p");
		for (int num=1;num<=packData.nodeCount;num++) { //finds a border vertex
			if (packData.isBdry(num)) {
				bd=num;num=1+packData.nodeCount;
			}
		}
		//best values thus far
//		double[] T = {0.889795834027023, 0.7241221562768865, 1.0};
//		double[] S= {0.6561307412519077, 0.951813515089002, 1.0};
		double[] T = {1.0, 1.0, 1.0};
		double[] S= {1.0, 1.0, 1.0};
		
		Random generator = new Random();
		for (int i=0;i<m;i++) {
			double best_t=-1.0,best_s=-1.0;
			for (int count=0;count<n;count++) {
				cpCommand(packData,"infile_read test.p");
				cpCommand(packData, "set_aim 4.0 "+v1+" "+v2+" "+v3+"");
				cpCommand(packData,"repack"); 
				//Variables
				double t =2.0,a=0,s=1.0,b=0;
//				double aimT=0,aimS,eT=0.0,eS=0.0;
//				if (i==0) { //narrows scope of search
//					aimT =0.88979583402702; eT=.1;
//					aimS =0.6561307412519077; eS=.1;
//					t =2*eT; a=aimT-eT; s =2*eS; b=aimS-eS; 
//				}
//				if (i==1) {
//					aimT =0.7241221562768865; eT=.1;
//					aimS =0.951813515089002; eS=.045;
//					t =2*eT; a=aimT-eT; s =2*eS; b=aimS-eS; 
//				}
//				if (i==2) {
//					aimT =1.0; eT=1.0;
//					aimS =1.0; eS=1.0;
//					t =2*eT; a=aimT-eT; s =2*eS; b=aimS-eS; 
//				}
				T[i]= generator.nextDouble()*t+a;
				S[i]= generator.nextDouble()*s+b;
				int P =0;
				//Set shiftpoint parameters and create Cranepoint constructor
				CranePoint spt1=new CranePoint(packData,V[i],T[i],S[i],P);
				spt1.radius=packData.getRadius(spt1.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt1,0);  
				updateStatus();
				//Set 2nd shiftpoint
				CranePoint spt2=new CranePoint(packData,V[(i+1)%m],T[(i+1)%m],S[(i+1)%m],P);
				spt2.radius=packData.getRadius(spt2.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt2,0);  
				updateStatus();
				//Set 3rd shiftpoint
				CranePoint spt3=new CranePoint(packData,V[(i+2)%m],T[(i+2)%m],S[(i+2)%m],P);
				spt3.radius=packData.getRadius(spt3.vert); // put this at front of vector (updateStatus will eliminate the later ones)
				cranePts.insertElementAt(spt3,0);  
				updateStatus();
				//Find shiftpoint label
				double err =DWFerror(FL1,FL2,type);
				
				if ((err<lowest_err || lowest_err <0.0) && java.lang.Double.isNaN(err)==false ) {
					lowest_err =err; best_s=S[i];best_t=T[i];
					msg(""+count+": Lowest Error = "+lowest_err+
							" "+V[i]+": [ "+best_t+" "+best_s+" ]," +
							" "+V[(i+1)%m]+": [ "+T[(i+1)%m]+" "+S[(i+1)%m]+" ]," +
							" "+V[(i+2)%m]+": [ "+T[(i+2)%m]+" "+S[(i+2)%m]+" ]");
				}
				
				if (count%10==0) {
					msg(""+count+" trials");
				}
			}
			if (best_t==-1.0||best_s==-1.0) {
				best_t=1.0;best_s=1.0;
			}
			T[i] =best_t;S[i] =best_s;
		}
		msg("DONE: exp_findweirshift; Lowest Error = "+lowest_err+
				" [ "+T[0]+" "+S[0]+" "+V[0]+"]," +
				" [ "+T[1%m]+" "+S[1%m]+" "+V[1%m]+"]," +
				" [ "+T[2%m]+" "+S[2%m]+" "+V[2%m]+"]");
		return 1;
	}
		
	/**
	 * Experiment for setting overlaps over edge path(s)
	 * @param p, PackData
	 * @param n, int number of experiments
	 * @param b1, int border vertex
	 * @param b2, int border vertex (error is measured via turn_sum)
	 * @return A, a 2D array
	 */
	public double[][] expSetOver(PackData p, int n, int b1, int v1, int v2) {
		//TODO: this method should check that elist1,elist2, and flist are non-empty
		//and throw an exception if they are.
		double[][] A = new double[n][3]; 
		Random generator = new Random();
		for (int j=0;j<n;j++) {
			double r1= generator.nextDouble();//*.025+0.25;///10+0.35;
			double r2= generator.nextDouble();//*.595+.035;///10+0.35;
			cpCommand(p,"set_over -d");
			cpCommand(p,"set_over " +r1+ " Elist");
			cpCommand(p, "set_over " +r2+ " elist");
			cpCommand(p,"repack");
			cpCommand(p,"norm_scale -c "+v1+" 2.0");
			cpCommand(p,"layout");
			cpCommand(p,"fix");
			Mobius mob =getMob(p, p.getCenter(v1), 
					p.getCenter(v1).add(p.getRadius(v1)),
					p.getCenter(v2),
					p.getCenter(v2).add(p.getRadius(v2)));

			double err =1000000*Math.abs(2-Math.pow(frobNorm(mob),2));
			A[j][0]= r1;//Double.toString(r1); 
			A[j][1]= r2;//Double.toString(r2);
			A[j][2]= err;//Double.toString(err);
			int count = j+1;
			msg(""+count);
			//TODO: create method to automatically generate elist(n) and flist(n)
		}
		return A; //returns a nx3 matrix
	}

	public double overlapFlowerInRandom(int n,int v,FaceLink faces1) {
		int n_p = packData.getNum(v);
		double err =DAFerror(0);
		double newerr =0.0;
		int counter=0;
		while (err>TOLER && counter<n) {
			int i = 0; //counter for v's petals
			while (i<n_p) {
				int w=packData.kData[v].flower[i];
				if (!packData.overlapStatus) { //alocates space for overlaps
					packData.alloc_overlaps();
				}
				if (packData.getInvDist(v,w)<0) { //checks for deep overlaps
					packData.set_single_invDist(v, w,0);
				}
				Random generator = new Random();
				double newInv = generator.nextDouble();
				double oldInv = packData.getInvDist(v,w);
				packData.set_single_invDist(v,w,newInv);
				cpCommand(packData,"repack");
//				cpCommand(packData,"layout");

				newerr =DAFerror(0);
				if (newerr>err) {
					packData.set_single_invDist(v,w,oldInv);
					cpCommand(packData,"repack");
//					packData.repack_call(1000);
					}
				else err = newerr;
				i++;
			}
			counter++;
			if (counter % 10 ==0) msg(""+counter+", error="+err);
		}
		return err;
	}

	public double overlapFlowerOutRandom(int n,int v,int b1,
			int b2,FaceLink faces1,FaceLink faces2) {//!!this is broken
		int n_p = packData.getNum(v);
		// TODO: what was this to be?
		double err =.000000001; // expDAFerror(1000,faces1);
		double newerr =0.0;
		int counter=0;
		while (err>TOLER && counter<n) {
			int i = 0; //counter for v's petals
			while (i<n_p) {
				int v_0 =packData.kData[v].flower[i];//vertex of v's ith petal
				if (packData.kData[v].invDist==null) { //poor method for creating overlap arrays
					cpCommand(packData,"set_over 0.5 "+v+" "+v_0);
					cpCommand(packData,"repack");
					cpCommand(packData,"set_over 0.0 "+v+" "+v_0);
					cpCommand(packData,"repack");
				}
				if (packData.getInvDist(v,v_0)<0) { //checks for deep overlaps
					packData.set_single_invDist(v,v_0,0.0);
				}
				Random generator = new Random();
				int v_1=packData.kData[v].flower[i+1];
				double oldInv = packData.getInvDist(v_0,v_1);
//				double newInv =oldInv*(generator.nextDouble()*x+(1-x/2));
				double newInv= generator.nextDouble();//*.025+0.25;///10+0.35;
				newerr =DAFerror(0);
				if (newerr>err) {
					packData.set_single_invDist(v_0,v_1,oldInv);
					packData.repack_call(n);
				}
				else if (newerr<=err) {
					packData.set_single_invDist(v_0,v_1,newInv);
					err = newerr;
				}
				i++;
			}
			counter++;
			if (counter % 10 ==0) msg(""+counter+", error="+err);
		}
		return err;
	}

	/**
	 * Randomly adjusts border edge overlaps of a simply connected complex.
	 *  Improving an error measuring distance/rad of two circles
	 * @param pd incoming packData
	 * @param n number of iterations
	 * @param v1 first interior vertex
	 * @param v2 second interior vertex
	 * @param b1 any border vertex
	 * @return error
	 */
	public double overlapBorderOutRandom(PackData pd,int v1,int v2,int b1,int n) {
		if (!pd.isBdry(b1)) {
			Oops("b1 must be a border circle");
		}
		cpCommand(pd,"repack");
//		pd.repack_call(1000);
		cpCommand(pd,"norm_scale -c "+v1+" 1.0");
		cpCommand(pd,"layout");
		int next =pd.kData[b1].flower[pd.kData[b1].nextVert];//next vertex in border element
		if (pd.kData[b1].invDist==null) { //poor method for creating overlap arrays
			cpCommand(pd,"set_over 0.5 "+b1+" "+next);
			cpCommand(pd,"repack -o");
			cpCommand(pd,"set_over 0.0 "+b1+" "+next);
			cpCommand(pd,"repack -o");
		}
//		Mobius mob =genMob(pd.rData[v1].center, 
//				pd.rData[v1].center.add(pd.rData[v1].rad),
//				pd.rData[v2].center,
//				pd.rData[v2].center.add(pd.rData[v2].rad));
//		double err1 = 1000000*Math.abs(2-Math.pow(frobNorm(mob),2));
		double err1 = 1000000*Math.abs(pd.getRadius(v1)-pd.getRadius(v2));
		double err2 = 1000000*genPtDist(pd.getCenter(v1),pd.getCenter(v2)); //finds error with new rad
		double err =Math.sqrt(err1*err1+err2*err2);
		double newerr =0.0;
		int counter=0;
		while (err>TOLER && counter<n && pd.isBdry(b1)) {
			next =pd.kData[b1].flower[pd.kData[b1].nextVert];//next vertex in border element
			int next_f =pd.nghb(b1, next);
			Random generator = new Random();
			double newInv = generator.nextDouble(); //includes deep overlaps
			double oldInv = pd.getInvDist(b1,next);
			pd.set_single_invDist(b1,next,newInv);
			if (next_f==0 & pd.kData[b1].flower[0]==pd.kData[b1].flower[pd.getNum(b1)]) {
				pd.set_single_invDist(b1,pd.kData[b1].flower[pd.getNum(b1)],newInv);
					//matches first and last overlap in flower index
			}
			pd.set_single_invDist(next,b1,newInv);
			if (pd.nghb(next,b1)==0 
					& pd.kData[next].flower[0]==pd.kData[next].flower[pd.getNum(next)]) {
				pd.set_single_invDist(next,pd.kData[next].flower[pd.getNum(next)],newInv); 
					//matches first and last overlap in flower index
			}
			cpCommand(pd,"repack -o");
//			pd.repack_call(1000);
			cpCommand(pd,"norm_scale -c "+v1+" 1.0");
			cpCommand(pd,"layout");
//			Mobius newmob =genMob(pd.rData[v1].center, 
//					pd.rData[v1].center.add(pd.rData[v1].rad),
//					pd.rData[v2].center,
//					pd.rData[v2].center.add(pd.rData[v2].rad));
//			double newerr1 =1000000*Math.abs(2-Math.pow(frobNorm(newmob),2));
			double newerr1 = 1000000*Math.abs(pd.getRadius(v1)-pd.getRadius(v2));
			double newerr2 =1000000*genPtDist(pd.getCenter(v1),pd.getCenter(v2)); //finds error with new rad
			newerr =Math.sqrt(newerr1*newerr1+newerr2*newerr2);
			if (newerr>=err) {
				pd.set_single_invDist(b1,pd.kData[b1].flower[next_f],oldInv);
				if (next_f==0 & pd.kData[b1].flower[0]==pd.kData[b1].flower[pd.getNum(b1)]) {
					pd.set_single_invDist(b1,pd.kData[b1].flower[pd.getNum(b1)],oldInv);
						//matches first and last overlap in flower index
				}
				pd.set_single_invDist(next,b1,oldInv); //matches first and last overlap in flower index
				if (pd.nghb(next,b1)==0 
						& pd.kData[next].flower[0]==pd.kData[next].flower[pd.getNum(next)]) {
					pd.set_single_invDist(next,pd.kData[next].flower[pd.getNum(next)],oldInv);
				}
//				pd.repack_call(1000);
				cpCommand(pd,"repack -o");
				cpCommand(pd,"norm_scale -c "+v1+" 1.0");
				cpCommand(pd,"layout");
			}
			else if (newerr<err) {
				err = newerr;
			}
			counter++;
			if (counter % 10 ==0) msg(""+counter+", error="+err);
			}
		return err;
	}
	
	public double overlapBorderShuffle(PackData pd,int n,int v1,int v2,int b1,double x) {
//		pd.overlapStatus=true;
		int counter=0;
		double err=1.0;
		while (err>TOLER && counter<n) {
			int next =pd.kData[b1].flower[pd.kData[b1].nextVert];//next vertex in border element
			if (pd.kData[b1].invDist==null) {
				cpCommand(pd,"set_over 0.5 "+b1+" "+next);
				cpCommand(pd,"repack");
				cpCommand(pd,"set_over 0.0 "+b1+" "+next);
				cpCommand(pd,"repack");
				cpCommand(pd,"norm_scale -c "+v1+" 2.0");
			}
			if (pd.getInvDist(b1,next)<0) { //checks for deep overlaps
				pd.set_single_invDist(b1,next,0.0);
			}
			err = overlapEdgeShuffle(pd,1000,b1,next,v1,v2,x);
			b1 =next;
			counter++;
			if (counter % 11 ==0) msg(""+counter+", error="+err);
		}
		packData = pd;
		return err;
	}
	
	public double overlapFlowerInShuffle(PackData pd,int n,int v,FaceLink faces1) {
//		pd.overlapStatus=true;
		int n_p = pd.getNum(v);
		int i = 0; //counter for v's petals
		double err =DAFerror(0);
		int counter=0;
		int p =pd.kData[v].flower[i];//vertex of v's ith petal
		if (!packData.overlapStatus) { //alocates space for overlaps
			packData.alloc_overlaps();
		}
		while (err>TOLER && counter<n) {
			while (i<n_p) {
				msg("shuffling edge "+v+"and"+p);
				err = overlapEdgeShuffle(pd,n,v,i,faces1);
				i++;
			}
			counter++;
			if (counter % 11 ==0) msg(""+counter+", error="+err);
		}
		packData = pd;
		return err;
	}

	public double overlapFlowerOutShuffle(PackData pd,int n,int v,FaceLink faces1) {
//		pd.overlapStatus=true;
		int n_p = pd.getNum(v);
		int i = 0; //counter for v's petals
		double err =DAFerror(0);
		int counter=0;
		int p=pd.kData[v].flower[0];
		if (pd.kData[v].invDist==null) {
			cpCommand(pd,"set_over 0.5 "+v+" "+p);
			cpCommand(pd,"repack");
			cpCommand(pd,"set_over 0.0 "+v+" "+p);
			cpCommand(pd,"repack");
		}
//		if (pd.kData[v].overlaps[i]<0) { //checks for deep overlaps
//			pd.kData[v].overlaps[i]=0;
//		}
		while (err>TOLER && counter<n) {
			while (i<n_p) {
				p =pd.kData[v].flower[i];//vertex of v's ith petal
				int p_0 =pd.nghb(p,pd.kData[v].flower[i+1]);
					if (p_0==-1) {
						Oops(p+ " and "+p_0+ " are not neighbors");
					}
				msg("shuffling edge "+p+"and"+pd.kData[v].flower[i+1]);
				err = overlapEdgeShuffle(pd,n,v,i,faces1);
				i++;
			}
			counter++;
			if (counter % 10 ==0) msg(""+counter+", error="+err);
		}
		packData = pd;
		return err;
	}

	public double overlapEdgeShuffle(PackData pd, int n, int e1, int e2, int v1, int v2, double x) {
		Mobius mob =getMob(pd, pd.getCenter(v1),
				pd.getCenter(v1).add(pd.getRadius(v1)),
				pd.getCenter(v2),
				pd.getCenter(v2).add(pd.getRadius(v2)));
		cpCommand(pd,"repack");
		cpCommand(pd,"norm_scale -c "+v1+" 2.0");
		cpCommand(pd,"layout");
		cpCommand(pd,"fix");
		double err =1000000*Math.abs(2-Math.pow(frobNorm(mob),2));
		int counter =0;
		double inc=x;
		while (err>TOLER && counter < n && inc>0.000000000001 
				&& pd.getInvDist(e1,e2)>=0.0) {
			pd.set_single_invDist(e1,e2,pd.getInvDist(e1,e2)-inc);
			//keep adjusting until sign changes
			Mobius newmob =getMob(pd, pd.getCenter(v1),
					pd.getCenter(v1).add(pd.getRadius(v1)),
					pd.getCenter(v2),
					pd.getCenter(v2).add(pd.getRadius(v2)));
			cpCommand(pd,"repack");
			cpCommand(pd,"norm_scale -c "+v1+" 2.0");
			cpCommand(pd,"layout");
			cpCommand(pd,"fix");
			double newerr =1000000*Math.abs(2-Math.pow(frobNorm(newmob),2));
			if (err<=newerr || pd.getInvDist(e1,e2)<0) {
				pd.set_single_invDist(e1,e2,pd.getInvDist(e1,e2)+inc); 
				//went too far;resets overlap to prev
				inc=inc/2; //chooses a smaller increment
				newerr=err;//resets overlap error
			}
			if (pd.getInvDist(e1,e2)<-1.0) {
				pd.set_single_invDist(e1,e2,-1.0);
			}
			err=newerr;
			counter++;
			if (counter%10==0) msg("edge shuffle: "+counter+", error="+err);
		}
	return err;
	}
	
	/**
	 * algorithm to find a deeper overlap on an edge which improves total turning angle
	 * on an annulus. Note, this only increases the overlap.
	 * @param pd incoming packData
	 * @param n number of iterations
	 * @param v first vertex on the edge
	 * @param i second vertex on the edge as indexed in v1's flower
	 * @param b0 border vertex on one border element
	 * @param b1 border vertex on the other border element
	 * @return
	 */
	public double overlapEdgeShuffle(PackData pd, int n, int v, int i,FaceLink faces1) {
		double err =DAFerror(0);
		int w=pd.kData[v].flower[i];
		int n_p = pd.getNum(v);
		int u=pd.kData[v].flower[n_p];
		double inc=pd.getInvDist(v,w)/10;
		int counter =0;
		while (err>TOLER && counter < n && inc>0.00000000001 && pd.getInvDist(v,w)>=0.0) {
			double oldInv =pd.getInvDist(v,w);
			double newInv=oldInv-inc;//keep adjusting until sign changes
			pd.set_single_invDist(v,w,newInv);
			if (i==0 & pd.kData[v].flower[0]==u) {
				pd.set_single_invDist(v,u,newInv); //matches first and last overlap in flower index
			}
			int vi =pd.kData[v].flower[i]; //vertex of i
			pd.set_single_invDist(vi,v,newInv); //matched overlap in vi's flower
			if (pd.nghb(vi,v)==0 & pd.kData[vi].flower[0]==pd.kData[vi].flower[pd.getNum(vi)]) {
				pd.set_single_invDist(vi,pd.kData[vi].flower[pd.getNum(vi)],newInv); 
				//matches first and last overlap in flower index
			}
			double newerr =DAFerror(0);
			if (err<=newerr) {
				pd.set_single_invDist(v,w,oldInv); //went too far;resets overlap to prev
				if (i==0 & pd.kData[v].flower[0]==u) {
					pd.set_single_invDist(v,u,oldInv); //matches first and last overlap in flower index
				}
				vi =pd.kData[v].flower[i]; //vertex of i
				pd.set_single_invDist(vi,v,oldInv); //matched overlap in vi's flower
				if (pd.nghb(vi,v)==0 & pd.kData[vi].flower[0]==pd.kData[vi].flower[pd.getNum(vi)]) {
					pd.set_single_invDist(vi,pd.kData[vi].flower[pd.getNum(vi)],oldInv); 
						//matches first and last overlap in flower index
				}
				pd.repack_call(1000);
				cpCommand(pd,"layout");
				inc=inc/2; //chooses a smaller increment
				newerr=err;//resets overlap error
			}
			err=newerr;
			counter++;
			if (counter%10==0) msg ("edge shuffle: "+counter+", error="+err);
		}
	return err;
	}
	
	/**
	 * Writes 2D array to a text file
	 * @param data: double[][] array
	 */
	public void writeToFile(double[][] data,Writer baseWriter) throws 
	java.io.IOException {
		int rows = data.length;
		if (rows==0) {return;}
		int cols = data[0].length;
		BufferedWriter writer=new BufferedWriter(baseWriter);
		for(int row=0;row<rows;row++) {
				writer.write(""+data[row][0]);
			for(int col=1;col<cols;col++) {
				writer.write("	"+data[row][col]);
				}
			writer.newLine();
			}
			writer.flush();
		}
			
	public void writeToFile(double[][] data,String fileName) throws 
	java.io.IOException {
		Writer writer = new FileWriter(fileName);
		writeToFile(data,writer);
		writer.close();		
	}
	
	/**
	 * Finds sum of absolute difference between correct distance 
	 * between vertices and actual distances.
	 * 
	 * NOTE: Only makes sense for eucl and hyp geometries
	 * 
	 * NOTE: this counts the error as 0 for shiftpoints
	 * and their neighbors as this should already be placed within the TOLER.
	 * @return double
	 */
	public double totalLayoutError() {
        int n = packData.nodeCount;
        int i=1; //counter for vertices as flower centers
        double sumErr = 0.0;
        while (i<n+1) {
        	int j = packData.getNum(i); //number of petals at i
        	int k = 0; //counter for petals at i
        	while (k<j+1) {
        		int p =packData.kData[i].flower[k]; //vertex of petal on center i
        		double act_dist = genPtDist(packData.getCenter(i),
        				packData.getCenter(p));
        		double cor_dist=0.0;
        		if (packData.overlapStatus==true) {
        			cor_dist = genInvDist(packData.getRadius(i),
        					packData.getRadius(p),
        					packData.getInvDist(i,p));
        		} else {
        			cor_dist = genInvDist(packData.getRadius(i),
        					packData.getRadius(p),1);
        		}
        		double err = Math.sqrt(Math.pow(act_dist-cor_dist,2));
        		if (isSP(i)!=null || isSP(p)!=null) {
        			err =0.0;
        		}
        		sumErr =+ err;
        		k=k+1;
        	}
        	i=i+1; //moves to next vertex
        }
        return sumErr;
    }
	
	/**
	 * Returns length of edge between circles of radius r1, r2, and 
	 * invDist 'ivd'. Only makes sense for hyp and eucl packings.
	 * @param r1 double
	 * @param r2 double
	 * @param ivd double 
	 * @return double, -1 on error, 
	 */
    public double genInvDist(double r1,double r2,double ivd) {
		if (packData.hes<0) // hyp, x-radii
			return HyperbolicMath.acosh(HyperbolicMath.h_invdist_cosh(r1, r2, ivd));
		else if (packData.hes>0) { // sph; ambiguous
			CirclePack.cpb.errMsg("Inversive distance on the sphere is ambiguous");
			return -1.0;
		}
		return EuclMath.e_invdist_length(r1, r2, ivd); 
	}
    
	public double holonomyBorder(FaceLink facelist) {
		  if (packData.hes!=-1) {
			  CirclePack.cpb.myErrorMsg("method only applies to Hyperbolic setting");
		    return 0;
		  }
		  int face1=(Integer)facelist.get(0);
		  int i=0; //counter for vertices in face1
		  int j=0; //boundary flag indicator
		  int v_int=0; //vertex of first circle placed
		  while (i<3 & j!=1) {
			  v_int = packData.faces[face1].vert[i]; 
			  j = packData.getBdryFlag(v_int);
			  i = i+1;
			  	if (i==2 & !packData.isBdry(2)) {
			  		throw new ParserException("face"+face1+" is not a boundary face");
			  	}
		  }
		  Complex z1=packData.getCenter(v_int); //initial location of v_int
		  
		  String opts=null;
		  DispFlags dflags=new DispFlags(opts);
		  int last_face=packData.layout_facelist(null,facelist,dflags,
				  null,true,false,face1,-1.0);
		  if (last_face!=face1) {
			  throw new DataException("last face not equal to first face");
		  }
		  
		  Complex z2=packData.getCenter(v_int); //final location of v_int
		  double err = (z2.arg()-z1.arg());
		  err = err*err;
		  err = Math.sqrt(err);
		  return err;
	}

	/**
	 * returns normalized Frobenius norm of a mobius transformation
	 * @param mob mobius transformation
	 * @return Frobenius norm 
	 */
	public double frobNorm(Mobius mob) {
		mob.normalize();

		Complex dett=mob.det();
		if (dett.abs()<.000000001) {
		msg("WARNING: Mobius det too small to trust");
			return 10000000; //too big 
		}
		mob.a=mob.a.minus(new Complex(1.0,0.0));
		mob.d=mob.d.minus(new Complex(1.0,0.0));
			  
		double fN = Math.sqrt(Math.pow(mob.a.abs(),2)+Math.pow(mob.b.abs(),2)
				  +Math.pow(mob.c.abs(),2)+Math.pow(mob.d.abs(),2));
		return fN;
	}

	/**
	 * Given a facelist, this method finds and prints the associated mobius transformations,
	 * the trace squared, and Frobenius norm.
	 * @param p, packData
	 * @param fp, 
	 * @param facelist
	 * @param fix
	 * @return
	 */
	public int holonomy_mob_print(FaceLink facelist1) {
		  Mobius mob =holonomy_mob(facelist1);
		  Complex tr2=mob.getTraceSqr();
		  double fN=frobNorm(mob);
		  msg(
				  "tr^2 ("+String.format("%.8e",tr2.x)+","+
				  String.format("%.8e",tr2.y)+
				  "), \nMobius is: \n"+
				  "  a = ("+String.format("%.8e",mob.a.x)+","+
				  String.format("%.8e",mob.a.y)+
				  ")   b = ("+String.format("%.8e",mob.b.x)+","+
				  String.format("%.8e",mob.b.y)+")\n"+
				  "  c = ("+String.format("%.8e",mob.c.x)+","+
				  String.format("%.8e",mob.c.y)+
				  ")   d = ("+String.format("%.8e",mob.d.x)+","+
				  String.format("%.8e",mob.d.y)+")");
		  msg("Frobenius norm "+fN);
		  double err =DAFerror(0);
		  msg("Ahlfors error, "+err);
		  return 1;
	}
	
	/**
	 * Finds the inversive distance from the shift points's little circle and petals. Then 
	 * sets the packing's edges to these overlap values. 
	 * @param pd
	 * @param v
	 */
	public void shiftToOverlaps(int v) {
		if (isSP(v)==null) {
			Oops("Error: "+v+" is not a shiftpoint");
		}
		CranePoint spt =isSP(v); int p=0; int np=spt.num;

		if (!packData.overlapStatus) { //alocates space for overlaps
			packData.alloc_overlaps();
		}

		while (p<np) {
			if (spt.shiftratio<=1 || spt.shiftratio>1) {
				double inv =spt.petalI[p];
				packData.set_single_invDist(v,packData.kData[v].flower[p],inv);
//			} else {
//				double inv =findInv(v,packData.kData[v].flower[p]);
//				packData.set_single_overlap(v,p,inv);
			} //use to be spt.shiftratio>1 case
			p++;
		}
	}
	
	/**
	 * Layout_facelist procedure modified to accomidate shift points.
	 * WARNING. Correct faces are found by finding the inversive distance between petals
	 * and the shift point's little circle. The original overlap distances are NOT preserved.
	 * @param pd incoming packData
	 * @param facelist a chain of faces 
	 * @return last face (usually also the first face) in the chain
	 */
	public int shiftLayoutFacelist(PackData pd, int b) {
//		if (vertStatus[b]>0 || !pd.isBdry(b)) {
//			Oops("Error: incoming vertex must be a border circle " +
//					"NOT neigboring a shiftpoint");
//		}
		int next_face=0;
		int j, jj, v0 = 0, v1 = 1, v2 = 2;

		FaceLink facelist =new FaceLink(pd,findBorderChain(b));
		Iterator<Integer> flist = facelist.iterator();
		int last_face = facelist.getLast();
		int faceCount =facelist.size();
		
		while (flist.hasNext()) {
			next_face = (Integer) flist.next();
			// skip repeated and illegal indices
			if (next_face != last_face && next_face > 0
					&& next_face <= faceCount) {
					// if next_face/last_face share edge, place contiguously
					if ((jj = pd.face_nghb(last_face, next_face)) >= 0)
						j = jj;
					else // default to placing 'indexFlag' vert
						j = pd.faces[next_face].indexFlag;
					v0 = pd.faces[next_face].vert[j];
					v1 = pd.faces[next_face].vert[(j + 1) % 3];
					v2 = pd.faces[next_face].vert[(j + 2) % 3]; // this is one computed
					
					if (vertStatus[v2]==2) {
						CranePoint spt =isSP(v2);
						Mobius mob=getMob(pd,spt.petalZ[pd.nghb(v2,v0)], spt.petalZ[pd.nghb(v2, v1)],
								pd.getCenter(v0), pd.getCenter(v1));
						pd.setCenter(v2,mob.apply(new Complex(spt.radius*spt.shiftratio-spt.radius,0.0)));
						pd.setRadius(v2,spt.shiftratio*spt.radius);
						packData.setCenter(v2,mob.apply(new Complex(spt.radius*spt.shiftratio-spt.radius,0.0)));
						packData.setRadius(v2,spt.shiftratio*spt.radius);
						//set overlaps according to shiftpoint layout
						if (!pd.overlapStatus) { //alocates space for overlaps
							pd.alloc_overlaps();
						}
						pd.set_single_invDist(v2,v0,findInv(v2,v0));
						pd.set_single_invDist(v2,v1,findInv(v2,v1));
					}
					
					CircleSimple sc;
					if (pd.overlapStatus) { // oj for edge opposite vj
						double o0 = pd.getInvDist(v1,v2);
						double o1 = pd.getInvDist(v2,v0);
						double o2 = pd.getInvDist(v0,v1);
						sc = CommonMath.comp_any_center(pd.getCenter(v0), pd.getCenter(v1),
								pd.getRadius(v0), pd.getRadius(v1),
								pd.getRadius(v2), o0, o1, o2,pd.hes);
					} else
						sc = CommonMath.comp_any_center(pd.getCenter(v0), pd.getCenter(v1),
								pd.getRadius(v0), pd.getRadius(v1),pd.getRadius(v2),pd.hes);
					
					
					// compute and store new center
					sc.save(pd, v2);
			}
			last_face=next_face;
		} // end of while through facelist
		return last_face;
	}

	/**
	 * Returns the mobius transformation for the holonomy 
	 * generated by the given facelist
	 * @param p packData
	 * @param facelist chain of faces; first and last face must be identical
	 * @return mobius transformation
	 */
	public Mobius holonomy_mob(FaceLink facelist) {
		  Complex z1,z2,w1,w2,denom;
		  if (packData.hes>0) {
			  CirclePack.cpb.myErrorMsg("tr^2: not yet available in spherical setting.");
		    return null;
		  }
		  /* We are assuming:
		   *  -  face1 is in geometric location where we want it
		   *  -  its 0, 1 verts used for defining the Mobius
		   *  -  last_face is the same as face1.
		   */
		  int face1=(Integer)facelist.get(0);
		  int fIndex =1;
		  if (packData.isBdry(packData.faces[face1].vert[1])) fIndex =2; //avoid using two boundry circles
		  if (packData.hes<0) {
			  int vM =packData.faces[face1].vert[fIndex];
			  if (packData.isBdry(vM)) vM =packData.faces[face1].vert[0];
			  //avoids normalizing boundary vertex
			  if (packData.isBdry(vM)) {
				  Oops(""+vM+" is a boundray circle. Need to debug holonomy_mob");
			  }
			  cpCommand(packData,"center_vert "+vM);
		  }
		  z1=packData.getCenter(packData.faces[face1].vert[0]);
		  z2=new Complex(packData.getCenter(packData.faces[face1].vert[fIndex]));
		  String opts=null;
//		  if (fix) opts=new String("-ff"); // draw the colored faces as we go
		  DispFlags dflags=new DispFlags(opts);
		  int last_face=packData.layout_facelist(null,facelist,
				  dflags,null,true,false,face1,-1.0);
//		  int last_face=shiftLayoutFacelist(p,facelist);
		  if (last_face!=face1) {
			  throw new DataException("last face not equal to first face");
		  }
//		  cpCommand(p,"disp -F "+Fl);
		  
		  w1=packData.getCenter(packData.faces[last_face].vert[0]);
		  w2=packData.getCenter(packData.faces[last_face].vert[fIndex]);
		  Mobius mob=new Mobius(); // initialize transformation 
		  if (packData.hes<0) {
			  mob=Mobius.auto_abAB(z1,z2,w1,w2);
		  } //Case when both hyperbolic vertices are interior
		  else if (packData.hes==0) {
		    denom=z1.minus(z2);
		    if (denom.abs()<.00000000001) {
		    	CirclePack.cpb.myErrorMsg("There were IOExceptions");
		    	return null;
		    } //Euclidean case
		    mob.a=w1.minus(w2).divide(denom);
		    mob.b=w1.minus(mob.a.times(z1));
		  }
		  return mob;
	}
	
	/**
     * Computes the turning angle for the borders of given vertices
     * @return turning angle sum.
     */    
	public double turnAngleSum(PackData p,int n) {
		//UtilPacket utpk1=new UtilPacket();
		int g = p.hes;
		if (g!=0) {
			cpCommand(p, "geom_to_e");
		}
		int s = n; //starting vertex
		if (!p.isBdry(n)) {
			return -1;
		}
		else {
			double sum =0.0;
			sum = Math.PI-p.getCurv(s); //first turning angle
			int i = 0;
			int v = p.kData[s].flower[i];
			while (!p.isBdry(v) & i<p.nodeCount+1) { //finds next border vertex
				i=i+1;
				v=p.kData[s].flower[i];
			}
			n = v;
			while (v!=s) { //adds turning angle and finds next border vertex
				n=v;
				sum += Math.PI-p.getCurv(n);
				i = 0;
				v = p.kData[n].flower[i];
				while (!p.isBdry(v) & v!=n & i<p.nodeCount+1) {
					i=i+1; 
					v=p.kData[n].flower[i];
				}
			}
			if (g!=0) {
				if (g==-1) {
					cpCommand(p, "geom_to_h");
				}
				else if (g==1) {
					cpCommand(p, "geom_to_s");
				}
			}
			return sum;
		}
	}
	
	/**
	 * Layout routine for circle packing which may have shift
	 * points. (See 'PackData.comp_pack_centers' for overall strategy.)
	 * @param p, PackData
	 * @return, int, count of placements 
	 */
		// Strategy: use data already in PackData regarding
	public int fullLayout(PackData p) {
		// 'firstface', 'nextface', and 'indx'

		int count=0;
		int nf = packData.firstFace; 
		
		// set all plotFlag's to zero
		for (int j=1;j<=packData.nodeCount;j++)	
			packData.kData[j].plotFlag = 0;

		// bomb out if first face fails to lay out properly
		count += place_firstFace(nf, packData.faces[nf].indexFlag);
		if (count==0) {
			Oops("Layout error in placing the initial face.");
			return 0;
		}

		// assume simply connected for now

		while ((nf = packData.faces[nf].nextFace) != packData.firstFace
				&& nf > 0 && nf <= packData.faceCount) {
			int indx=packData.faces[nf].indexFlag;
			int v=packData.faces[nf].vert[(indx+2)%3];
			int ans=0;
			if (packData.kData[v].plotFlag==0) {
				ans=place_nextFace(nf,indx);
				
				if (ans==0) {
					Oops("problem with placement for v="+v);
					return 0;
				}
			}
			count += ans;
		} // end of first pass in simply connected case
		return count;
	}
	
	/**
	 * Find centers of circles for the 'firstface' in layout process.
	 * @param ff, firstface index (generally from packData)
	 * @param indx, which of three circles is determined by the other two
	 * @return count of circles placed
	 */
	public int place_firstFace(int ff,int indx) {
		int count=0;
		int v=packData.faces[ff].vert[indx]; // first circle at origin.
		packData.setCenter(v,new Complex(0,0)); 
		packData.kData[v].plotFlag=1;
		count++;
		UtilPacket uPff = new UtilPacket();
		CranePoint spt=null;
		// Is v is a CranePoint? Normalization here is that
		//    its petal 0 is centered on the positive x-axis.
		// NOTE: with shifted layouts, a neighbor could possibly
		//    be centered at the same center as the shift circle!
		if ((spt=isSP(v))!=null) { 
			SPuF(spt,2);
			//storeSPpetalZ(spt); // store all the centers; v is at origin
			// rotate so petal 0 is centered on positive x-axis
			int alphaX=packData.nghb(spt.vert, spt.alpha);//alpha spt flower index
			Complex rot=spt.petalZ[alphaX].divide(spt.petalZ[alphaX].abs()); 
			int num =packData.getNum(v)+packData.getBdryFlag(v);
			for (int j=0;j<num;j++) {
				int k=packData.kData[v].flower[(alphaX+j)%num];
				packData.setCenter(k,spt.petalZ[(alphaX+j)%num].divide(rot));
				packData.kData[k].plotFlag=1;
				count++;
			}
			return count;
		} // done with CranePoint
		
		// shift-point neighbor; Need a normalization for shift neighbors. Added 8/4/11; 
		//seems to fix layout issue when shiftpoint is neighbor to the first vertex placed.  
		if (vertStatus[v]==1) { 
			//find which neighbor is the shiftpoint
			for (int i=0;i<(packData.getNum(v));i++) {
				if ((spt=isSP(packData.kData[v].flower[i]))!=null) {
					SPuF(spt,2);
					spt.center=new Complex(0,0);
					packData.setCenter(spt.vert,spt.center);
					Complex z=spt.petalZ[0];
					Complex rot=z.divide(z.abs()); 
					for (int j=0;j<(packData.getNum(spt.vert)+packData.getBdryFlag(spt.vert));j++) {
						int k=packData.kData[spt.vert].flower[j];
						packData.setCenter(k,spt.petalZ[j].divide(rot));
						packData.kData[k].plotFlag=1;
						count++;
					}
					return count;
				}
			}
		} // 
		
		// non-shift point; normalization is different. Now
		//   the second circle is placed on the positive x-axis
		if (vertStatus[v]==0) {
			int u=packData.faces[ff].vert[(indx+1)%3]; //second circle to place
			packData.setCenter(u,HES_Norm(xRadToH(packData.getRadius(v))
					+xRadToH(packData.getRadius(u)),0));
			packData.kData[u].plotFlag=1;
			count++;
			
			// compute third point
			int w=packData.faces[ff].vert[(indx+2)%3];

			double ang = Math.acos(genCosOverlap(packData.getRadius(v),packData.getRadius(w),
					packData.getRadius(u),1.0,1.0,1.0));
			packData.setCenter(w,HES_Norm(xRadToH(packData.getRadius(v))
					+xRadToH(packData.getRadius(w)), ang));
			packData.kData[w].plotFlag=1;
			count++;
		}
		
		return count;
	}
	
	/**
	 * Place the circle for (indx+2)%3 in the next face
	 * @param face, int face index
	 * @param indx, indxFlag for face
	 * @return count of placement (may be more if circle being
	 * placed is a shiftpoint). Return 0 on error.
	 */
	public int place_nextFace(int face,int indx) {
		int count=0;
		// face is <w,u,v>. w and u should already have
		//   centers, we are placing v.
		int w=packData.faces[face].vert[indx]; 
		int u=packData.faces[face].vert[(indx+1)%3]; 
		int v=packData.faces[face].vert[(indx+2)%3]; // circle to be place
		Complex zw=packData.getCenter(w);
		Complex zu=packData.getCenter(u);
		if (packData.kData[w].plotFlag==0 || packData.kData[u].plotFlag==0)
			return 0;
		CranePoint spt=null;
		// Is v is a CranePoint? Compute centers of v and all petals
		//    in normalized situation, then apply Mobius to move
		//    them so centers of u, w agree with existing values.
		if ((spt=isSP(v))!=null) { 
			SPuF(spt,2);
//			storeSPpetalZ(spt); // store all the centers; v is at origin
			// rotate so petal 0 is centered on positive x-axis
			int jw=packData.nghb(v,w);
			int ju=packData.nghb(v,u);
			Complex Cw=spt.petalZ[jw];
			Complex Cu=spt.petalZ[ju];

			// affine map taking Cw --> cw and Cu --> cu
			Mobius mob=genMob(Cw,Cu,zw,zu);
			//Mobius mob=Mobius.affine_mob(Cw,Cu,zw,zu);

			packData.setCenter(v,mob.apply(new Complex(0.0,0.0)));
			packData.kData[v].plotFlag=1;
			for (int j=0;j<(packData.getNum(v)+packData.getBdryFlag(v));j++) {
				int k=packData.kData[v].flower[j];
				packData.setCenter(k,mob.apply(spt.petalZ[j]));
				packData.kData[k].plotFlag=1;
				count++;
			}
			return count;
		} // done with CranePoint
		
		// Note: If neighbor of CranePoint, it should already be plotted.

		// compute third point
		double rv=packData.getRadius(v);
		double rw=packData.getRadius(w);
		double ru=packData.getRadius(u);
		Complex Cw = packData.getCenter(w);
		Complex Cu = packData.getCenter(u);
		double dist = genPtDist(Cu,Cw); //nsote the small difference
		// ???? not used genCosOverlap(rw,ru,rv);
		double ang =genAngleSides(dist,xRadToH(rv)+xRadToH(ru),xRadToH(rw)+xRadToH(rv));
		
		//alternative prev
//		Complex tmp=new Complex(Math.log(ru+rv), (zw.minus(zu)).arg()-ang);
//		Complex e_vu=tmp.exp();
//		Complex zuzv=e_vu.add(zu);
//		packData.rData[v].center=zuzv;
		
		Complex zv_N = HES_Norm(xRadToH(ru)+xRadToH(rv),-ang);
		Mobius mobN=genMob(new Complex(0,0),HES_Norm(xRadToH(ru)+xRadToH(rw),0),zu,zw);
		packData.setCenter(v,mobN.apply(zv_N));
		
		//original 
//		Complex tmp=new Complex(0.0,-ang);
//		tmp=tmp.exp();
			//unit vector from w to u
//		Complex wu=zw.minus(zu);
//		wu=wu.divide(wu.abs()); // unit vector in direction zu --> zw
//		Complex zuzv=tmp.times(wu); // unit vector in direction zu --> zv
//		zuzv=zuzv.times(ru+rv); // displacement vector from zu to zv
//	    packData.rData[v].center=zu.add(zuzv);
		packData.kData[v].plotFlag=1;
		count++;
		return count;
	}

	/**
	 * Layout when assuming only the alpha vert is shift point
	 * @param spt @see CranePoint
	 * @return double giving the shift angle sum error as
	 * compared to 4pi.
	 */
	public double storeSPpetalZ(CranePoint spt) {
		int v = spt.vert;
		spt.center = new Complex(0.0);
		UtilPacket utpk = new UtilPacket();
	
		double radius = spt.pd.getRadius(v); //TODO change rif to update spt.radius
											
		Complex c1 = HES_to_Ecent(radius*(1-spt.shiftratio),Math.PI);
		spt.center = new Complex(c1); //little circle center

		double baseAng = spt.theta;
		double accum = baseAng;
		int num = spt.pd.getNum(v);
		int j = 0;
		int vv = spt.pd.kData[v].flower[0];
		spt.petalZ[0]=HES_to_Ecent(radius+spt.pd.getRadius(vv),baseAng);
		
		while (accum < Math.PI && j <= spt.pd.getNum(v)) {
			try {
				genCosOverlap(radius,spt.pd.getRadius(spt.pd.kData[v].flower[j]),
					spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]),1.0,1.0,1.0);
			} catch (Exception ex) {
				Oops("error computing cos");
			}
			double ang = genAngleRad(radius,
					spt.pd.getRadius(spt.pd.kData[v].flower[j]),
					spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]));
			
			if ((accum + ang) > Math.PI)
				break;
			accum += ang;
			j++;
			vv = spt.pd.kData[v].flower[j];
			spt.petalZ[j]=HES_to_Ecent(radius+spt.pd.getRadius(vv),accum);
			
		}// end of pi while

		if (j != packData.getNum(v)) {
			accum = Math.PI
					+ jasheRoutine(radius, radius * spt.shiftratio, accum,
							spt.pd.getRadius(spt.pd.kData[v].flower[j]),
							spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]));
			j++;
			vv = spt.pd.kData[v].flower[j];
			if (spt.pd.hes==-1) {
				Mobius mob_Lc=Mobius.auto_abAB(new Complex(0,0),
						HES_to_Ecent(radius*spt.shiftratio,Math.PI), c1,
						HES_to_Ecent(radius,Math.PI)); 
				spt.petalZ[j]=mob_Lc.apply(HES_to_Ecent(radius*spt.shiftratio
						+spt.pd.getRadius(vv),accum));
			} else spt.petalZ[j]=(new Complex(radius*spt.shiftratio+spt.pd.getRadius(vv),0))
				.times((new Complex(0,accum)).exp()).add(c1);
			
			while ((accum) < 3.0 * Math.PI && j <= spt.pd.getNum(v)) {
				try {
					genCosOverlap(radius * spt.shiftratio,spt.pd.getRadius(spt.pd.kData[v].flower[j]),
						spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]),1.0,1.0,1.0);
				} catch (Exception ex) {
					Oops("error computing cos");
				}
				double ang = genAngleRad(radius * spt.shiftratio,
						spt.pd.getRadius(spt.pd.kData[v].flower[j]),
						spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]));
				
				if ((accum + ang) > 3.0 * Math.PI)
					break;
				accum += ang;
				j++;
				vv = spt.pd.kData[v].flower[j];
				if (spt.pd.hes==-1) {
					Mobius mob_Lc=Mobius.auto_abAB(new Complex(0,0),
							HES_to_Ecent(radius*spt.shiftratio,Math.PI), c1,
							HES_to_Ecent(radius,Math.PI)); 
					spt.petalZ[j]=mob_Lc.apply(HES_to_Ecent(radius*spt.shiftratio
							+spt.pd.getRadius(vv),accum));
				} else spt.petalZ[j]=(new Complex(radius*spt.shiftratio+spt.pd.getRadius(vv),0))
					.times((new Complex(0,accum)).exp()).add(c1);
			} //end of 3pi while

			if (j != spt.pd.getNum(v)) {
				accum = 3.0* Math.PI
						+ jasheRoutine(spt.shiftratio * radius, radius, accum
								- 2.0 * Math.PI,
								spt.pd.getRadius(spt.pd.kData[v].flower[j]),
								spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]));
				j++;
				vv = spt.pd.kData[v].flower[j];
				spt.petalZ[j]=HES_to_Ecent(radius+spt.pd.getRadius(vv),accum);
				
				while (j <= spt.pd.getNum(v)) {
					try {
						genCosOverlap(radius,spt.pd.getRadius(spt.pd.kData[v].flower[j]),
							spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]),1.0,1.0,1.0);
					} catch (Exception ex) {
						Oops("error computing cos");
					}
					accum += genAngleSides(radius,
							spt.pd.getRadius(spt.pd.kData[v].flower[j]),
							spt.pd.getRadius(spt.pd.kData[v].flower[(j + 1)%num]));
					
					j++;
					if (j<=num) {
						vv = spt.pd.kData[v].flower[j];
						spt.petalZ[j]=HES_to_Ecent(radius+spt.pd.getRadius(vv),accum);
					}
				}
			}
		}
	
		return Math.abs(accum - spt.theta - spt.pd.getAim(v));
	}
	
	/**
	 * Compute angles of SP neighbors for ghostRad
	 * @param PackData p, the packing data
	 * @param v, Shift point vertex
	 * @return array of angles
	 */
	public double NghbSPangle(int k) {
		packData.setCenter(k,new Complex(0.0));
		UtilPacket utpk=new UtilPacket();
		Iterator<CranePoint> cps=cranePts.iterator();
		CranePoint spt=null;
		while (spt==null && cps.hasNext()) {
			spt=cps.next();
			if (spt.vert!=k)
				spt=null;
		}
				
		// no, v is a normal vertex
		if (spt==null) {
			cpCommand(packData,"layout -p"+packData.packNum);		
			return 1.0;
		}
		
		// yes, v is a 'CranePoint'
		double radius=packData.getRadius(k);
		Complex c1=new Complex(radius*(spt.shiftratio-1.0));
		spt.center=new Complex(c1);
		double baseAng=spt.theta;
		double accum=baseAng;
		int j=0;
		int a=packData.kData[k].flower[0];
		packData.setCenter(k,(new Complex(radius+packData.getRadius(a),0))
			.times((new Complex(0,baseAng)).exp()));
		
		while(accum<Math.PI && j<=packData.getNum(k)) {
			try {
				genCosOverlap(radius,packData.getRadius(packData.kData[k].flower[j]),
					packData.getRadius(packData.kData[k].flower[j+1]),1.0,1.0,1.0);
			} catch (Exception ex) {
				Oops("error computing cos");
			}
			//prev double ang=Math.acos(utpk.value);
			double ang = genAngleRad(radius,
					packData.getRadius(packData.kData[k].flower[j]),
					packData.getRadius(packData.kData[k].flower[j+1]));
			
			if ((accum+ang)>Math.PI)
				break;
			accum += ang;
			j++;
			k=packData.kData[k].flower[j];
			packData.setCenter(k,(new Complex(radius+packData.getRadius(a),0))
				.times((new Complex(0,accum)).exp()));
		}

		if (j!=packData.getNum(k)) {
			accum = Math.PI+jasheRoutine(radius,radius*spt.shiftratio,accum,
					packData.getRadius(packData.kData[k].flower[j]),
					packData.getRadius(packData.kData[k].flower[j+1]));
			j++;
			k=packData.kData[k].flower[j];
			packData.setCenter(a,(new Complex(radius*spt.shiftratio+packData.getRadius(a),0))
				.times((new Complex(0,accum)).exp()).add(c1));
		
			
			while((accum)<3.0*Math.PI && j<packData.getNum(k)) {
				try {
					genCosOverlap(radius*spt.shiftratio,packData.getRadius(packData.kData[k].flower[j]),
					packData.getRadius(packData.kData[k].flower[j+1]),1.0,1.0,1.0);
				} catch (Exception ex) {
					Oops("error computing cos");
				}
			//prev double ang=Math.acos(utpk.value);
			double ang = genAngleRad(radius*spt.shiftratio,
					packData.getRadius(packData.kData[k].flower[j]),
					packData.getRadius(packData.kData[k].flower[j+1]));
				
			if ((accum+ang)>3.0*Math.PI)
				break;
			accum += ang;
			j++;
			k=packData.kData[k].flower[j];
			packData.setCenter(a,(new Complex(radius*spt.shiftratio+packData.getRadius(a),0))
				.times((new Complex(0,accum)).exp()).add(c1));
		}
		
		if (j!=packData.getNum(k)) {
			accum = 3.0*Math.PI+jasheRoutine(spt.shiftratio*radius,radius,accum-2.0*Math.PI,
					packData.getRadius(packData.kData[k].flower[j]),
					packData.getRadius(packData.kData[k].flower[j+1]));
			j++;
			a=packData.kData[k].flower[j];
			packData.setCenter(a,(new Complex(radius+packData.getRadius(a),0))
				.times((new Complex(0,accum)).exp()));

			while (j<packData.getNum(k)) {
				try {
					genCosOverlap(radius,packData.getRadius(packData.kData[k].flower[j]),
						packData.getRadius(packData.kData[k].flower[j+1]),1.0,1.0,1.0);
				} catch (Exception ex) {
					Oops("error computing cos");
				}
				//prev accum +=Math.acos(utpk.value);
				accum += genAngleRad(radius,
						packData.getRadius(packData.kData[k].flower[j]),
						packData.getRadius(packData.kData[k].flower[j+1]));
				
				j++;
				a=packData.kData[k].flower[j];
				packData.setCenter(a,(new Complex(radius+packData.getRadius(a),0))
				.times((new Complex(0,accum)).exp()));
			}
		}
		}
		return Math.abs(accum-spt.theta-packData.getAim(k));
	}
	
	/**
	 * Compute "angle sum" at a shiftpoint neighbor and writes results in uP
	 * @param v, neighbor vertex
	 * @param radius, radius of circle at vertex v from pd
	 * @param uP, incoming packdatat
	 * @return
	 */
    public boolean shiftNghbSum(int v, double radius, UtilPacket uP) {
    	int N = packData.getNum(v)+packData.getBdryFlag(v);
    	//for boundary vertices kData[v].num is number of neighbors-1
    	double []rad=new double[N+1]; 
    	double []oldrad=new double[N+1]; 
    	// go through the petals of v
    	for (int j=0; j<N; j++) {
    		int k = packData.kData[v].flower[j];
    		// if this petal is a shift point, compute/store a 'ghost' radius
    		double rr=-1;
    		double rl=-1;
    		if (vertStatus[k]==2) {
    			CranePoint spt=isSP(k); // get the shift-point
    			if (spt==null)
    				Oops("conflict identifying CranePoint for "+v);
    			
    			// check first and last j to see if they have just one face
    			if (j==0) {
    				if (packData.isBdry(v)) {
    					rl=-1;
    					rr=-1;
    				}
    				else {
    					rl=packData.getRadius(packData.kData[v].flower[1]);
    					rr=packData.getRadius(packData.kData[v].flower[N-1]);
    				}
    			}
    			else if (j==N) {
    				if (packData.isBdry(v)) {
    					rl=-1;
    					rr=-1;
    				}
    				else {
    					rl=packData.getRadius(packData.kData[v].flower[1]);
    					rr=packData.getRadius(packData.kData[v].flower[N-1]);
    				}
    			}
    			
    			// now the rest of the petals
    			else {
    				rr=packData.getRadius(packData.kData[v].flower[j-1]);
    				rl=packData.getRadius(packData.kData[v].flower[j+1]);
    			}
    			
    			//in storeSPangles spt.ang[0]=spt.ang[N]=two faces
    			double gr = -1;
    			gr = ghostrad(packData.getRadius(v), rl, rr, spt.radius, 
    					spt.petalAng[packData.nghb(spt.vert,v)]); 
    				//TODO make certain spt.radius gets updated from shiftRadCalc
    			rad[j] = gr;
    		}
    		// petal k not 'CranePoint'? just store usual radius. 
    		else { 
    			rad[j]=packData.getRadius(k);
    		}    		
    	}
    	
    	// now we have the neighboring radii in rad[]. We substitute these
    	//   into packData and compute the angle sum at v, then restore the radii
    		
    	try {
        	for (int j=0; j< N; j++) {
        		int k = packData.kData[v].flower[j];
        		oldrad[j]=packData.getRadius(k);
        		packData.setRadius(k,rad[j]);
    		}
         	boolean dah=genAngleSumOverlap(v, radius, uP); //shift angle sum is stored in uP.value
           	for (int j=0; j< N; j++) {
        		int k = packData.kData[v].flower[j];
        		packData.setRadius(k,oldrad[j]);
    		}
           	return dah;
    	} catch (Exception ex) { // on exception, want to restore the old radii
        	for (int j=0; j< N; j++) {
        		int k = packData.kData[v].flower[j];
        		packData.setRadius(k,oldrad[j]);
    		}
        	return false;
    	}
    }
    
    /**
     * This computes a new radius at a vertex with one or more petals
     * which are shift points.
     * @param v
     * @param radius
     * @param aim
     * @param its, int number of iterations
     * @param uP, 'UtilPacket' to retrieve resulting best radius
     * @return
     */
    public boolean nghbRadCalc(int v,double radius,double aim,int its,UtilPacket uP) {
    	int N = packData.getNum(v)+packData.getBdryFlag(v);
    	double []rad=new double[N];
    	double []oldrad=new double[N];
    	// go through the petals of v
    	for (int j=0; j< N; j++) {
    		int k = packData.kData[v].flower[j];
    		// if this petal is a shift point, compute/store a 'ghost' radius
    		double rr=-1;
    		double rl=-1;
    		if (vertStatus[k]==2) {
    			CranePoint spt=isSP(k); // get the shiftpoint
    			if (spt==null)
    				Oops("conflict identifying CranePoint for "+v);
    			
    			// bdry case: check first and last j to see if they have just one face
    			if (j==0) {
    				if (packData.isBdry(v)) {
    					rl=-1;
    					rr=-1;
    				}
    				else {
    					rl=packData.getRadius(packData.kData[v].flower[1]);
    					rr=packData.getRadius(packData.kData[v].flower[N-1]);
    				}
    			}
    			
    			else if (j==N) {
    				if (packData.isBdry(v)) {
    					rl=-1;
    					rr=-1;
    				}
    				else {
    					rl=packData.getRadius(packData.kData[v].flower[1]);
    					rr=packData.getRadius(packData.kData[v].flower[N-1]);
    				}
    			}
    			
    			// now the rest of the petals
    			else {
    				rr=packData.getRadius(packData.kData[v].flower[j-1]);
    				rl=packData.getRadius(packData.kData[v].flower[j+1]);
    			}
    			
    			// find the ghost radius that serves as radius for petal k
    			rad[j]=ghostrad(packData.getRadius(v),
    					rl, rr, spt.radius,spt.petalAng[packData.nghb(spt.vert,v)]);
    		}
    		// petal k not 'CranePoint'? just store usual radius. 
    		else { 
    			rad[j]=packData.getRadius(k);
    		}
    		
    	}
    	
    	// now we have the neighboring radii in rad[]. We substitute these
    	//   into packData and compute the angle sum at v, then restore the radii
    		
    	try {
        	for (int j=0; j< N; j++) {
        		int k = packData.kData[v].flower[j];
        		oldrad[j]=packData.getRadius(k);
        		packData.setRadius(k,rad[j]);
    		}
        	//before changes
        	HES_RadCalc(v,radius,aim,its,uP);//stores newrad in uP.value;
        	for (int j=0; j< N; j++) { 
        		int k = packData.kData[v].flower[j];
        		packData.setRadius(k,oldrad[j]);
    		}
        	return true;

    	} catch (Exception ex) { 
        	for (int j=0; j< N; j++) { 
        		int k = packData.kData[v].flower[j];
        		packData.setRadius(k,oldrad[j]);
    		}
        	return false;
    	}
    }
    
	/**
	 * Compute angle sum at 'CranePoint' for given; 'UtilPacket' must
	 * be created by calling routine. Result is 'value' entry.
	 * @param sPt, CranePoint
	 * @param radius, radius of large shiftpoint circle
	 * @param uP, UtilPacket for gathering results
	 * @return 0 on error
	 */
	public boolean shiftAngleSum(CranePoint sPt,double radius,UtilPacket uP) {
		int v0=sPt.vert;
		double baseAng=sPt.theta;
		double accum=baseAng;
		int j=0;
		double ang;
		while(accum<Math.PI && j<=packData.getNum(v0)) {
			try {
				genCosOverlap(radius,packData.getRadius(packData.kData[v0].flower[j]),
					packData.getRadius(packData.kData[v0].flower[j+1]),1.0,1.0,1.0);
			} catch (Exception ex) {
					return false;
			}
			//prev ang=Math.acos(utpk.value);
			ang = genAngleRad(radius,
					packData.getRadius(packData.kData[v0].flower[j]),
					packData.getRadius(packData.kData[v0].flower[j+1]));
			
			if ((accum+ang)>Math.PI)
				break;
			accum += ang;
			j++;
		}
		
		if (j!=packData.getNum(v0)) {
		accum = Math.PI+jasheRoutine(radius,radius*sPt.shiftratio,accum,
				packData.getRadius(packData.kData[v0].flower[j]),
				packData.getRadius(packData.kData[v0].flower[j+1]));
		j++;
		
		while((accum)<3.0*Math.PI && j<packData.getNum(v0)) {
			try {
				genCosOverlap(radius*sPt.shiftratio,packData.getRadius(packData.kData[v0].flower[j]),
					packData.getRadius(packData.kData[v0].flower[j+1]),1.0,1.0,1.0);
			} catch (Exception ex) {
				return false;
			}
			//prev ang=Math.acos(utpk.value);
			ang = genAngleRad(radius*sPt.shiftratio,
					packData.getRadius(packData.kData[v0].flower[j]),
					packData.getRadius(packData.kData[v0].flower[j+1]));
			
			if ((accum+ang)>3.0*Math.PI)
				break;
			accum += ang;
			j++;
		}
		
		if (j!=packData.getNum(v0)) {
			accum = 3.0*Math.PI+jasheRoutine(sPt.shiftratio*radius,radius,accum-2.0*Math.PI,
					packData.getRadius(packData.kData[v0].flower[j]),
					packData.getRadius(packData.kData[v0].flower[j+1]));
			j++;

			while (j<packData.getNum(v0)) {
				try {
					genCosOverlap(radius,packData.getRadius(packData.kData[v0].flower[j]),
						packData.getRadius(packData.kData[v0].flower[j+1]),1.0,1.0,1.0);
				} catch (Exception ex) {
					return false;
				}
				//prev accum+=Math.acos(utpk.value);
				accum+=genAngleRad(radius,
						packData.getRadius(packData.kData[v0].flower[j]),
						packData.getRadius(packData.kData[v0].flower[j+1]));
				//
				j++;
			}
		}
		}
		
		uP.value=accum-sPt.theta;
		return true;
	}
	
	/**
	 * routine to improve radius at a shift point during repacking
	 * computations.
	 * @param spt
	 * @param r
	 * @param aim
	 * @param N
	 * @param uP
	 * @return
	 */
	public boolean shiftRadCalc(CranePoint spt,double R,double aim,int N,UtilPacket uP) {
		UtilPacket utpk=new UtilPacket();
		
		// 'bestR' will always be our latest/best estimate of the radius
		double bestR=spt.radius=R;
		if (!SPuF(spt,utpk,0))
			return false;
		double bestAS=utpk.value;
		double diff=bestAS-aim;
		int counter=0;
		while (Math.abs(diff)>TOLER && counter < N) {
			double factor=.1;  // TODO: adjust
			if (diff<0) factor *= -1.0;
			double newdiff=diff;
			double newR=bestR;
			
			// keep adjusting until change sign
			while (diff/newdiff>0) {
				newR=spt.radius=bestR*(1.0+factor);
				if (!SPuF(spt,utpk,0))
					return false;
				double newAS=utpk.value;
				newdiff=newAS-aim;
				counter++;
				if (Math.abs(newdiff)<TOLER || counter>=N) {
					uP.value=newR;
					return true;
				}
				if (diff/newdiff>0) {
					bestR=newR;
					bestAS=newAS;
				}
			}
			
			// binary search
			double lowR;
			double highR;
			if (factor<0) {
				lowR=newR;
				highR=bestR;
			}
			else {
				lowR=bestR;
				highR=newR;
			}
			bestR=spt.radius=(lowR+highR)/2.0;
			if (!SPuF(spt,utpk,0))
				return false;
			bestAS=utpk.value;
			diff=bestAS-aim;
			int counter2 =0;
			while(Math.abs(diff)>TOLER && counter2<N) {
				if (diff>0) {
					lowR=bestR;
				}
				else {
					highR=bestR;
				}
				bestR=spt.radius=(lowR+highR)/2.0;
				if (!SPuF(spt,utpk,0))
					return false;
				bestAS=utpk.value;
				diff=bestAS-aim;
				counter2++;
			} // end of inner while
		} // end of outer while
		
		// return in the 'UtilPacket.value'
		spt.anglesum=bestAS;
		uP.value=bestR;
		spt.radius=bestR;
		uP.errval=diff;
		return true;
	}
	
	/**
     * Modified version of 'EuclPacker.oldReliable'. Works for packing
     * eucl or hyp shifted branch points.
     * @return -1 on error, 0 if nothing to repack, otherwise return
     * accumulated error at shiftpoints.
 	 * TODO: move to new code
     */
    public double shuffle(int passes) {
      int v;
      int count = 0;
      double verr,r;
      UtilPacket uP=new UtilPacket();
  
      int aimNum = 0;
      int []inDex =new int[packData.nodeCount+1];
      for (int j=1;j<=packData.nodeCount;j++) {
    	  if (packData.getAim(j)>0) {
    		  inDex[aimNum]=j;
    		  aimNum++;
    	  }
      }
      if (aimNum==0) return 0; // nothing to repack
      //prepare to compute angle sums
      
      Iterator<CranePoint> cps = cranePts.iterator();
	  while (cps.hasNext()) {
		  CranePoint spt=cps.next();
		  v=spt.vert;
		  if (spt.shiftratio>1 && packData.hes==-1) {
			  spt.radius=spt.radius/spt.shiftratio;
			  packData.setRadius(v,spt.radius);
			  packData.setRadius(v,spt.radius);
		  }
		  UtilPacket upTemp=new UtilPacket();
		  SPuF(spt,upTemp,1);
//finds layout for each shift pt and saves //the petal angle state in CranePoint spt 
//petal angle at vertex j associated with shift point at vertex v can
	  }
		  
      // compute angle sums cutoff value
      double accum = totalCurvError();
      double recip=.333333/aimNum;
      double cut=accum*recip;

      //********MAIN LOOP
      while ((cut > TOLER && count<passes)) {
    	  cps = cranePts.iterator();
		  while (cps.hasNext()) {
			  CranePoint spt=cps.next();
			  v=spt.vert;
			  int N = passes; //TODO adjust		  
			  genRadCalc(v, packData.getRadius(v), packData.getAim(v), N, uP);
			  spt.radius=uP.value;
			  packData.setRadius(v,spt.radius);//prev null

			  SPuF(spt,uP,1);
		  } 
     	  
		  //loop for vertices
    	  for (int j=0;j<aimNum;j++) {
    		  v=inDex[j];
    		  r=packData.getRadius(v);
    		  uP=new UtilPacket();
    		  
    		  if (!genAngleSum(v,r,uP)) 
    			  return -1.0;
    		  packData.setCurv(v,uP.value);
    		  verr=packData.getCurv(v)-packData.getAim(v);
    		  
    		  if (Math.abs(verr)>cut && vertStatus[v]!=2) {
    			  if (genRadCalc(v,packData.getRadius(v),packData.getAim(v),passes,uP)) //TODO adjust N {
    				  packData.setRadius(v,uP.value);	//records new radius
    			  if (!genAngleSum(v,uP.value,uP)) 
    					  return -1.0;
    			  packData.setCurv(v,uP.value);
    		  }
          }
    	  
    	  // recompute total accumuated curvature error
          accum=totalCurvError();
          cut=accum*recip;
          count++;
      } // end of ****MAIN LOOP while 

      accum=0.0;
      cps=cranePts.iterator();
      while (cps.hasNext()) {
    	  CranePoint spt=cps.next();
    	  v=spt.vert;
    	  accum += Math.abs(packData.getCurv(v)-packData.getAim(v));
      }
      return accum;
    } 
    
	/**
     * Computes difference between angle sums and their targets for all vertices
     * @return accum, total error
     */
    public double totalCurvError() {
        int v;
        double accum=0.0;
        double err;
        UtilPacket uP=new UtilPacket();
    
        int aimNum = 0;
        int []inDex =new int[packData.nodeCount+1];
        for (int j=1;j<=packData.nodeCount;j++) {
      	  if (packData.getAim(j)>0) {
      		  inDex[aimNum]=j;
      		  aimNum++;
      	  }
        }
        if (aimNum==0) return 0.0; // no error
        
        // Do shiftpoints first
        for (int j=0;j<aimNum;j++) {
        	  v=inDex[j];
      	  CranePoint spt=null;
      	  if (vertStatus[v]==2 && (spt=isSP(v))!=null) { 
      		  if (!genAngleSum(v,packData.getRadius(v),uP)) 
      			  return -1.0;
      		  packData.setCurv(v,uP.value);
      		  err=packData.getCurv(v)-packData.getAim(v);
      		  // store angs for use by neighbors
      		  spt.radius=packData.getRadius(v);
      		  SPuF(spt,uP,1);
//    		  storeSPangles(spt,spt.pd.rData[v].rad,uP);
      		  accum += (err<0) ? (-err) : err;
      		  //TODO delete: System.err.println("  curv "+err);
      	  }
        }
        
        for (int j=0;j<aimNum;j++) {
        	  v=inDex[j];
        	  if (vertStatus[v]!=2) {
        		  if (!genAngleSum(v,packData.getRadius(v),uP)) 
          			  return -1.0;
          		  packData.setCurv(v,uP.value);
          		  err=packData.getCurv(v)-packData.getAim(v);
          		  accum += (err<0) ? (-err) : err;
        	  }
        }
        return accum;
    }
    
    //method in progress. need a way to quantify the closeness of 
    //the quadratic function to its boundary.
    
    public double path_error(PackData p, Vector<String> V) {    	
//	    public Complex path(double x) { //this the function of the path for x=0..1
//			Complex z = ((new Complex(0,2*Math.PI*x)).exp()).minus(.25);
//			Complex Z = z.mult(z);
//			return Z;
//		}
//	    public Complex slope(double x) {//this is the slope perp to path's deriv
//			double re = -Math.sin(2*Math.PI*x)*Math.PI*(8*Math.cos(2*Math.PI*x)-1);
//			double im = Math.PI*(Math.pow(8*Math.cos(2*Math.PI*x),2)-Math.cos(2*Math.PI*x)-4);
//	    	Complex M = new Complex(-1/re,-1/im);
//	    	return M;
//	    }
//
//	    for (Enumeration e = V.elements() ; e.hasMoreElements() ;) {
//    		Object v = e.nextElement();
//    		int i =Integer.parseInt(e.nextElement().toString()); 
//    		double diff=0.0000001;
//    		
//    		int counter=0;
//    			if (i<2) {
//    				Complex C1=p.rData[i].center; //center of first circle
//    			}
//    		else while (Math.abs(diff)>TOLER && counter < 100000) {
//    			double factor=0.5;
//    	}
//    		
//    		Complex c = p.rData[i].center;
//    		double diff=0.0000001;
//    		int counter=0;
//    		while (Math.abs(diff)>TOLER && counter < 100000) {
//    			double factor=.5;
//    			if (diff<0) factor=1/factor;
//    			double newdiff=diff;
//    				
//    		}
//    	}
    	return 3.14;
    }
    
    	//(Enumeration e = V.elements() ; e.hasMoreElements() ;) {
    		//Object v = e.nextElement();
    		//Complex c = p.rData['v'].center;
    		//int i = Integer.parseInt((String) e.nextElement());
    		//double accum;
    		//accum += c.abs();
    	//}
		//return accum;
    //}
        
    /**
     * Compute angle at shift point in face where shift from large
     * to small circle is made.
     * @param Rf, rad of circle moving 'from'
     * @param Rt, rad of circle moving 'to'
     * @param Af, accumulated angle up to circle c2
     * @param R2, radius of c2
     * @param R3, radius of c3
     * @return
     */
    public double jasheRoutine(double Rf,double Rt, double Af,double R2,double R3) {
    			Complex cRt=HES_to_Ecent(Rt-Rf,0); //normalized center of Rt
    			Complex c2=HES_to_Ecent(Rf+R2,Af); //normalized center of R2
    			Complex xPt=new Complex(-Rf-Rt,0); //arbitrary pt on the x-axis 
    			double d = genPtDist(cRt,c2);
    			double u=Rt+R3;
    			double op=R2+R3;
    			double At=genAngleSides(d,u,op);
    			return -1*genAngleSides(genPtDist(cRt,c2),genPtDist(cRt,xPt)
    					,genPtDist(c2,xPt))+At; 
    		}
    
    /**
     * Compute angle at shift point in face where shift from large
     * to small circle is made.
     * This method replaces 'jasheRoutin'
     * @param Rf, rad of circle moving 'from'
     * @param Rt, rad of circle moving 'to'
     * @param Af, accumulated angle up to circle c2, assuming tangency pt of
     * Rt and Rf is at angle 0.
     * @param R2, radius of c2
     * @param R3, radius of c3
     * @return
     */
    public double jumpAngle(double Rf,double Rt, double Af,double R2,double R3) {
//  !!!accum=PI+jasheroutine!!!
//    		Complex cRt_smaller=HES_Norm(xRadToH(Rt)-xRadToH(Rf),Math.PI); 
//    		//when Rt is smaller
//    		Complex cRt_larger=HES_Norm(xRadToH(Rt)-xRadToH(Rf),(Rt-Rf)/Math.abs(Rt-Rf)*Math.PI); 
//    		//when Rf is bigger
//    		Complex cRt = (Rt>=Rf) ? cRt_larger : cRt_smaller;
    		double Rotate = 0; if (Rf-Rt<0) Rotate =-1*Math.PI; 
    		//determines rotate pt. is 0 or Pi. 
    		Complex cRt = HES_Norm(xRadToH(Rt)-xRadToH(Rf),
    				Rotate); 
    		//Rf needs to be centered at (0,0). If Rf<Rt, Rt is at (-|Rf-Rt|,0); 
    		// else it is (|Rf-Rt|,0).
    		Complex c2=HES_Norm(xRadToH(Rf)+xRadToH(R2),Af); 
    		//normalized center of R2. Af should be given assuming p is at 0.
    		Complex xPt=new Complex(.5,0); //arbitrary pt on the x-axis 
    		double d = genPtDist(cRt,c2);
    		double u=xRadToH(Rt)+xRadToH(R3);//TODO allow for inv distances her 
   			double op=xRadToH(R2)+xRadToH(R3);// and here
   			double At=genAngleSides(d,u,op);
   			return At-1*genAngleSides(d,genPtDist(cRt,xPt),genPtDist(c2,xPt)); 
    		}

    /**
     * Compute angle at shift point in face where shift from large
     * to small circle is made.
     * This method replaces 'jumpAngle'
     * @param Rf, rad of circle moving 'from'
     * @param Rt, rad of circle moving 'to'
     * @param Af, accumulated angle up to circle c2
     * @param R2, radius of c2
     * @param R3, radius of c3
     * @return
     */
    public double jumpAngle2(double Rf,double Rt, double Af,double R2,double R3) {
//  !!!accum=PI+jasheroutine!!!
    		Complex cRt_smaller=new Complex((HES_Norm(xRadToH(Rt)-xRadToH(Rf),
    				2*Math.PI).real()),0); 
    		//when Rt is little circle
    		Complex cRt_bigger=new Complex(-(HES_Norm(xRadToH(Rt)-xRadToH(Rf),
    				2*Math.PI).real()),0); 
    		//when Rf is big circle
    		Complex cRt = (Rt>=Rf) ? cRt_bigger : cRt_smaller;
    		
    		Complex c2=HES_Norm(xRadToH(Rf)+xRadToH(R2),Af); //normalized center of R2
    		Complex xPt=new Complex(0.5,0); //arbitrary pt on the x-axis 
    		double d = genPtDist(cRt,c2);
    		double u=xRadToH(Rt)+xRadToH(R3);
   			double op=xRadToH(R2)+xRadToH(R3);
   			double At=genAngleSides(d,u,op);
   			double ang= -1*genAngleSides(genPtDist(cRt,c2),genPtDist(cRt,xPt)
    					,genPtDist(c2,xPt))+At; 
   			return ang;
    		}
    
    /**
     * Computes the 'ghost radius'. A radius which replaces the shiftpoint
     * to a shiftpoint neighbor's flower so that it can be computed like a
     * normal CP.
     * @param Rv, center radius, normally a shift neighbor.
     * @param rl, petal before shift point.
     * @param rr, petal after shift point.
     * @param gr, best guess for intial ghost radius.
     * @param theta, desired angle for (Rv;rl,gr,rr)
     * @return
     */
    public double ghostrad(double Rv, double rl, double rr, double gr, double theta) {
        double bestR=gr;
        double bestAS=quadfind(Rv, rl, rr, bestR);
		double diff=bestAS-theta;
		int counter=0;
		while (Math.abs(diff)>TOLER && counter < 100000) { //TODO: adjust
			double factor=.5;  // TODO: adjust
			if (diff<0) factor = 1/factor;
			double newdiff=diff;
			double newR=bestR;
			// keep adjusting until change sign
			while (diff/newdiff>0) { 
				newR = bestR*(factor);
				newdiff=quadfind(Rv, rl, rr, newR)-theta;
				counter++;
				if (Math.abs(newdiff)<TOLER || counter>=10) {
					return newR;
				}
				if (diff/newdiff>0) {
					bestR=newR;
				}
			}
			
			double lowR;
			double highR;
			if (factor<1) {   
				lowR=newR;
				highR=bestR;
			}
			else {
				lowR=bestR; 
				highR=newR;
			}
			bestR=(lowR+highR)/2.0;
			diff=quadfind(Rv, rl, rr, bestR)-theta;
			while(Math.abs(diff)>TOLER && counter< 10000) { //TODO adjust 
				if (diff<0) { //prev diff>0
					lowR=bestR;
				}
				else {
					highR=bestR;
				}
				bestR=(lowR+highR)/2.0;
				diff=quadfind(Rv, rl, rr, bestR)-theta;
			} // end of inner while
		} // end of outer while
        return bestR;
    }
    
    /**
     * Compute the angle at Rv in two faces {Rv,rr,gr} and
     * {Rv,gr,rr}. If rr or rl is negative, then there is just
     * the other face. 
     * @param Rv, rad whose angle we're computing
     * @param rl
     * @param rr
     * @param gr (ghost radius)
     * @return double, angle
     */
    public double quadfind(double Rv, double rl, double rr, double gr) {
    	double angl=0.0;
    	double angr=0.0;
    	if (rl>0.0) {
    		angl=Math.acos(genCosOverlap(Rv, rl, gr,1.0,1.0,1.0));
    	}
    	if (rr>0.0) {
    		angr=Math.acos(genCosOverlap(Rv,rr,gr,1.0,1.0,1.0));
    	}
    	return angl+angr;
    }
    
    /**
     * Identify shift points from 'cranePts'; for duplicates, remove all
     * but the first (lastest should be first), refill 'vertStatus' array.
     */
    public void updateStatus() {
    	vertStatus=new int[packData.nodeCount+1];
    	Iterator<CranePoint> cps=cranePts.iterator();
		while (cps.hasNext()) {
			vertStatus[cps.next().vert]+=2;
		}
		for(int v=1;v<=packData.nodeCount; v++) {
			if (vertStatus[v]==0) {
				for (int j=0;j<packData.getNum(v)+packData.getBdryFlag(v);j++ ) {
					int k=packData.kData[v].flower[j];
					if (vertStatus[k]>=2)
						vertStatus[v]=1;
				}
			}
		}
		int N=cranePts.size()-1;
		for (int j=N;j>0;j--) {
			CranePoint sp=cranePts.get(j);
			if (vertStatus[sp.vert]>=4) {
				vertStatus[sp.vert] -= 2;
				cranePts.remove(j);
			}
		}
    }
    
    /**
     * If v is a shiftpoint, this returns its 'CranePoint' class.
     * @param v
     * @return, 'CranePoint' 
     */
    public CranePoint isSP(int v) {
    	Iterator<CranePoint> cps=cranePts.iterator();
    	CranePoint spt=null;
    	while (spt==null && cps.hasNext()) {
    		spt=cps.next();
    		if (spt.vert!=v)
    			spt=null;
    		else return spt;
    	}
    	return null;
	}
    
    public boolean SPuF(CranePoint spt, int F) {
    	UtilPacket uPdummy = new UtilPacket();
    	return SPuF(spt, uPdummy,F);
    }
    
    /**
     * Shiftpoint general purpose method to do the following:
     * 0:measure shift angle sum (replacing shiftAngleSum)
     * 1:store angle at shiftpoint neighbors (replacing storeSPangles)
     * 2:store location of shiftpoint neighbor vertices (replacing storeSPpetalZ)
     * @param spt incoming Cranepoint utilpacket
     * @param inUp utilPacket
     * @param F=0: just 0, 1: 0&1, 2: 0&1&2 These aren't currently being used, but could speed up riff.
     * @return true
     * TODO: move to new code
     */
	public boolean SPuF(CranePoint spt, UtilPacket inUp, int F) {
		int v = spt.vert;
		int P = packData.nghb(v,spt.alpha);
		double R = spt.radius;
		double r = R * spt.shiftratio;
		double accum = spt.theta;
		int j = 0;
		double ang = 0;
		int num =packData.getNum(v);
		double jumpAng = spt.jumpAng;//argument of jump-point. Old value was PI.
		// F=1: find shift-neighbors vertex coords
		Complex[] cents = new Complex[num];
		double Rotate = 0+jumpAng; if (R-r<0) Rotate =-1*Math.PI+jumpAng; //rotate pos if r is bigger

		Complex littleCent = HES_Norm(xRadToH(r) - xRadToH(R),Rotate);
		spt.petalZ[P] = cents[P] = HES_Norm(xRadToH(R)
				+ xRadToH(packData.getRadius(packData.kData[v].flower[P])),spt.theta);
		spt.petalI[P] =findInv(littleCent,spt.petalZ[P],r
				,packData.getRadius(packData.kData[v].flower[0])); 
		// F=0: measure shiftangle
		while (accum < jumpAng && j < num) {
			try {
				genCosOverlap(R,packData.getRadius(packData.kData[v].flower[mod(j+P,num)]),
					packData.getRadius(packData.kData[v].flower[mod(j+1+P,num)]),1.0,1.0,1.0);
			} catch(Exception ex) {
				return false;
			}
			ang = Math.acos(inUp.value);
			if ((accum + ang) > jumpAng)
				break;
			accum += ang;
			j++;
			// F=1
			spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = HES_Norm(xRadToH(R)
					+ xRadToH(packData.getRadius(packData.kData[v].flower[mod(j+P,num)])),
					accum);
			spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
					,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv
		} // end of Pi while

		if (j != num) {
			accum = jumpAng + jumpAngle(R, r, accum-jumpAng, 
							packData.getRadius(packData.kData[v].flower[mod(j+P,num)]),
							packData.getRadius(packData.kData[v].flower[mod(j+1+P,num)]));//TODO just add accum to itself
			//NOTE: jumpangle expects x-radii, and expects jump-point to be at (R,0).
			// Hence 'accum+Math.PI'. Jump-angle calcs are in normalized positions.
			j++;
			// F=1
			if (packData.hes == -1) {
				Mobius mob_Lc = Mobius.auto_abAB(
						new Complex(0, 0), HES_Norm(xRadToH(r),jumpAng), 
						littleCent, HES_Norm(xRadToH(R),jumpAng)); 
				//mob taking normalized littlecent back to standard
				spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = mob_Lc.apply(HES_Norm(xRadToH(r)
								+ xRadToH(packData.getRadius(packData.kData[v].flower[mod(j+P,num)])),
								accum));//to set angles in H at origin.
				spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
						,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv

			} else
				spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = (new Complex(r
						+ packData.getRadius(packData.kData[v].flower[mod(j+P,num)]), 0))
						.times((new Complex(0, accum)).exp()).add(littleCent);
				spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
						,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO remove this repeats spt.petalI above
			// F=0
			while ((accum) < 2.0*Math.PI+jumpAng && j < num) {
				try {
					genCosOverlap(r,packData.getRadius(packData.kData[v].flower[mod(j+P,num)]),
						packData.getRadius(packData.kData[v].flower[mod(j+P+1,num)]),1.0,1.0,1.0);
				} catch(Exception ex) {
					return false;
				}
				ang = Math.acos(inUp.value);

				if ((accum + ang) > 2.0*Math.PI+jumpAng)
					break;
				accum += ang;
				j++;
				// F=1
				if (packData.hes == -1) {
					Mobius mob_Lc = Mobius.auto_abAB(
							new Complex(0, 0),HES_Norm(xRadToH(r), jumpAng),
							littleCent, HES_Norm(xRadToH(R), jumpAng)); 
					// mob taking normalized littlecent back to standard
					spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = mob_Lc.apply(HES_Norm(xRadToH(r)
									+ xRadToH(packData.getRadius(packData.kData[v].flower[mod(j+P,num)])),
									accum));
					spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
							,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv
				} else
					spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = (new Complex(r
							+ packData.getRadius(packData.kData[v].flower[mod(j+P,num)]),
							0)).times((new Complex(0, accum)).exp()).add(
							littleCent);
					spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
							,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv
			} // end of 3pi while
			// F=0
			if (j != packData.getNum(v)) {
				accum = 2.0*Math.PI+jumpAng
						+ jumpAngle(r,R, accum-jumpAng,
								packData.getRadius(packData.kData[v].flower[mod(j+P,num)]),
								packData.getRadius(packData.kData[v].flower[mod(j+P+1,num)]));
				j++;
				// F=1
				spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = HES_Norm(xRadToH(R)
						+ xRadToH(packData.getRadius(packData.kData[v].flower[mod(j+P,num)])),
						accum);
				spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
						,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv
				// F=0
				while (j < packData.getNum(v)) {
					try {
						genCosOverlap(R,packData.getRadius(packData.kData[v].flower[mod(j+P,num)]),
							packData.getRadius(packData.kData[v].flower[mod(j+P+1,num)]),1.0,1.0,1.0);
					} catch(Exception ex) {
						return false;
					}
					ang = Math.acos(inUp.value);
					accum += ang;
					j++;
					// F=1
					if (j == num) {
						spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = HES_Norm(xRadToH(R)
								+ xRadToH(packData.getRadius(packData.kData[v].flower[mod(j+P,num)])),
								accum);
						spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
								,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv
						break;
					}
					spt.petalZ[mod(j+P,num)] = cents[mod(j+P,num)] = HES_Norm(xRadToH(R)
							+ xRadToH(packData.getRadius(packData.kData[v].flower[mod(j+P,num)])),
							accum);
					spt.petalI[mod(j+P,num)] =findInv(littleCent,spt.petalZ[mod(j+P,num)],r
							,packData.getRadius(packData.kData[v].flower[mod(j+P,num)])); //TODO Inv
				} // end of while
			}
		}
		// F=0
		inUp.value = accum - spt.theta;
		// F=1
		for (j = 0; j < num; j++) { //prev j=1
			spt.petalAng[mod(j+P,num)] = genAngleSides(genPtDist(cents[mod(j+P,num)], cents[mod(j+P+1,num)]),
					genPtDist(cents[mod(j+P,num)], cents[mod(j+P-1,num)]), 
					genPtDist(cents[mod(j+P-1,num)],	cents[mod(j+P+1,num)]));
		}
		spt.anglesum = accum - spt.theta;
		return true;
	}
    
	/**
	 * Iterate to find weights giving a coherent packing; idea is James Ashe's.
	 * Weights need to be twice the angles in the triangle formed by the 
	 * fractured branch points.
	 * @param n, int, max iterations
	 * @param report, int 0/1 turns off/on msg report
	 * @return number of iterations
	 * TODO: move to new code
	 */
    public double fracShuffle3(int n, int report) {
		int count=0;
		double offBy=1.0, ang =-1.0;
		while (offBy>LOC_TOLER && count<n) {
			for (int i=0;i<branchVert.length;i++) { //TODO: correct length?
				for (int j=0;j<3;j++) {
					if (!packData.isBdry(branchVert[i][j])) {
						//skip boundary circles
						ang =triAngle(i,j);
						packData.setAim(branchVert[i][j],pi2+2.0*ang);
					}
				}
			}
			try {
				if (packData.overlapStatus) {
					cpCommand(packData,"repack -o");
				} else {
					cpCommand(packData,"repack");
				}
			} catch (Exception ex) {
				throw new ParserException("repack, layout failed");
			}
			offBy=errorTotal();
			count++;
		}
		if (report==1) {
			CirclePack.cpb.debugMsg("FracBranching 3: error="+offBy+" after "+count+" iterations");
		}
		return offBy;
	}

	/**
	 * Iterate to find weights giving a coherent packing over 4 pts;
	 * fractured branch points.
	 * @param n, int, max iterations
	 * @param inv, double, inversive overlap for edge (branchVert[0],branchVert[2])
	 * @return number of iterations
	 */
	public int fracShuffle4(int n,double inv) {
		int count =0;double err=1.0;double ang;
		//branchVert[0] and [2] have the overlapping edge
		if (!packData.overlapStatus) { //alocates space for overlaps
			packData.alloc_overlaps();
		}
		
		UtilPacket uP =new UtilPacket();
		packData.set_single_invDist(branchVert[0][0],branchVert[0][2],inv);
		while (err>LOC_TOLER && count<n) {
			//branchVert[1] and [3]
			for (int j=1;j<4;j=j+2) {
				ang =Math.acos(
						genCosOverlap(packData.getRadius(branchVert[0][j]),
						packData.getRadius(branchVert[0][(j-1)%4]),//TODO inv rotates!!
						packData.getRadius(branchVert[0][(j+1)%4]),1.0,1.0,inv));
				packData.setAim(branchVert[0][j],pi2+2.0*ang);
			}
			//branchVert[0] and [2]
			for (int j=0;j<4;j=j+2) {
				ang =Math.acos(genCosOverlap(packData.getRadius(branchVert[0][j]),
						packData.getRadius(branchVert[0][(j+2)%4]),
						packData.getRadius(branchVert[0][(j*2+3)%4]),inv,1.0,1.0));
				packData.setAim(branchVert[0][j],pi2+2.0*ang);
			}
			try {
				cpCommand(packData,"repack -o;layout");
			} catch (Exception ex) {
				throw new ParserException("repack, layout failed");
			}
			err =1.0;//TODO add an error function
			count++;
			msg("count ="+count);
		}
		msg("FracBranching 4: error="+err+" after "+count+" iterations");
		return count;
	}

	/**
	 * This computes the sum of squares of (2*angle-weight) for the current
	 * weights and angles. Caution: be sure curvatures are updated.
	 * @return
	 */
	public double errorTotal() {
		double w;
		double accum=0.0;
		double diff;
		for (int i=0;i<branchVert.length;i++) {
			for (int j=0;j<3;j++) {
				if (!packData.isBdry(branchVert[i][j])) {
					w=packData.getAim(branchVert[i][j])-pi2;
					diff=2.0*triAngle(i, j)-w;
					accum += diff*diff;
				}
			}
		}
		return Math.sqrt(accum);
	}
	
	/**
	 * Compute angle at branchVert[i][j] in the branched triangle
	 * @param j
	 * @return
	 */
	public double triAngle(int i,int j) {
		UtilPacket uP =new UtilPacket();
		int R=branchVert[i][j];int r=branchVert[i][(j+1)%3];int l=branchVert[i][(j+2)%3];
		if (!packData.overlapStatus) {
			return Math.acos(genCosOverlap(packData.getRadius(R), packData.getRadius(1),packData.getRadius(r),
					1,1,1));
		} 
		else return genAngleSides(genDistInv(R,r),genDistInv(R,l),genDistInv(r,l));
//			genCosOverlap(packData.rData[R].rad, packData.rData[l].rad, packData.rData[r].rad
//				,packData.kData[R].overlaps[packData.nghb(R,l)]
//				,packData.kData[R].overlaps[packData.nghb(R,r)]
//				,packData.kData[r].overlaps[packData.nghb(r,l)], uP);
	}
	
	public double genDistInv(int v1, int v2) {
		if (packData.nghb(v1, v2)==-1) {
			msg(""+v1+" and "+v2+" are not neighbors");
		}
		double inv =1;
		if (packData.overlapStatus==true) {
			inv =packData.getInvDist(v1,v2);
		}
		int G = packData.hes; //geometry, 0=Euc -1=Hyp 1=Sphr
		double r1 =packData.getRadius(v1);
		double r2 =packData.getRadius(v2);
		switch (G) {
			case -1: { //Hyperbolic case 
				double x =Math.cosh(r1)*Math.cosh(r2)+inv*Math.sinh(r1)*Math.sinh(r2);
				return acosh(x);
			}
			case 1: { //Spherical case
				Oops("no spherical case");
			}
			default: { //Euclidean case
				return Math.sqrt(r1*r1+r2*r2+2*r1*r2*inv);
			}
		}
	}
	
	static double asinh(double x)	{
		return Math.log(x + Math.sqrt(x*x + 1.0));
	}

	static double acosh(double x)	{
		return Math.log(x + Math.sqrt(x*x - 1.0));
	}

	static double atanh(double x)	{
		return 0.5*Math.log( (x + 1.0) / (x - 1.0) );
	}
	
		/**
		 * Determines how a given vertex's angles sum is to be computed according to its vertstatus
		 * @param v, given vertex
		 * @param r, radius of circle at v
		 * @param uP, UtilPacket for gathering results
		 * @return angle sum from method dependending on vertstatus
		 */
		public boolean genAngleSum(int v,double r,UtilPacket uP) {
			switch(vertStatus[v]) {
				case 1: {
					return shiftNghbSum(v,r,uP);
				}
				case 2: {
					return SPuF(isSP(v),uP,0);
//					return shiftAngleSum(isSP(v),r,uP);
				}
				default: {
					return genAngleSumOverlap(v,r,uP); // prev return packData.e_anglesum_overlap(v, r, uP);
				}
			} // end of switch
		}
			
		/**
		 * Determines how a given circle's radius is to be computed according to its vertstatue
		 * @param v, given vertex
		 * @param r, radius of circle at v
		 * @param aim, given vertices set angle sum
		 * @param N, max number of algorithm iterations
		 * @param uP, UtilPackect for gathering results
		 * @return radius from method depending on its verstatus. result recorded in uP
		 */
		public boolean genRadCalc(int v, double r, double aim, int N, UtilPacket uP) {
			switch(vertStatus[v]) {
			case 1: {
				return nghbRadCalc(v,r,aim,N,uP);
			}
			case 2: {
				return shiftRadCalc(isSP(v),r,aim,N,uP);
			}
			default: {
				return HES_RadCalc(v,r,aim,N,uP); //return packData.e_radcalc(v,r,aim,N,uP);
			}
			} //end of switch
		}
				
		/**
		 * Finds face angle using three circle radii
		 * @param R, radius at vertex
		 * @param rn, neg oriented petal
		 * @param rp, pos oriented petal
		 * @param uP, utilpacket to recieve values
		 * @return 
		 */
		public double genAngleRad(double R, double rn, double rp) {
			int G = packData.hes; //geometry, 0=Euc -1=Hyp 1=Sphr
			switch (G) {
				case -1: { //Hyperbolic case 
					return Math.acos((Math.cosh(R+rn)*Math.cosh(R+rp)-Math.cosh(rn+rp))
						/(Math.sinh(R+rn)*Math.sinh(R+rp)));
				}
				case 1: { //Spherical case
					return Math.acos((Math.cos(R+rn)*Math.cos(R+rp)-Math.cos(rn+rp))
						/(Math.sin(R+rn)*Math.sin(R+rp)));
				}
				default: { //Euclidean case
					return Math.acos(((R+rn)*(R+rn)+(R+rp)*(R+rp)-(rp+rn)*(rp+rn))
						/(2*(R+rn)*(R+rp)));
				}
			}
		}
		
		/**
		 * method for finding distance between points (as complex numbers) in given geometry
		 * @param x, pt 1
		 * @param y, pt 2
		 * @return distance as real number
		 */
		public double genPtDist(Complex x, Complex y) {
			int G = packData.hes;// geometry, 0=E -1=H 1=S
				switch (G) {
				case -1: { //Hyperbolic case
					return HyperbolicMath.h_dist(x, y);
				}
				case 1: { //Spherical case
					Oops("Spherical case is unfinished");
					return -1;
				}
				default: { //Euclidean case
				}
				return Math.sqrt(Math.pow(x.real()-(y.real()), 2)+Math.pow(x.imag()-y.imag(), 2));
			}
		}
		
	/**
	 	* Finds face a angle given lengths of sides
	 * @param right, side right of angle
	 * @param left, side left of angle
	 * @param opp, side opposite of angle
	 * @param uP, Utilpacket
	 * @return angle depending on geometry
	 */
		public double genAngleSides(double right, double left, double opp) {
			int G = packData.hes; //geometry, 0=Euc -1=Hyp 1=Sphr
			switch (G) {
				case -1: { //Hyperbolic case 
					return Math.acos((Math.cosh(right)*Math.cosh(left)-Math.cosh(opp))
						/(Math.sinh(right)*Math.sinh(left)));
				}
				case 1: { //Spherical case
					return Math.acos((Math.cos(right)*Math.cos(left)-Math.cos(opp))
						/(Math.sin(right)*Math.sin(left)));
				}
				default: { //Euclidean case
					return Math.acos((right*right+left*left-opp*opp)
						/(2*right*left));
				}
			}
		}
				
		/**
		 * Calculate radii in given geometry according to aim
		 * @param v
		 * @param r
		 * @param aim
		 * @param N
		 * @param uP
		 * @return, tue/false new radii recorded in uP.value
		 */
		public boolean HES_RadCalc(int v,double r,double aim,int N,UtilPacket uP) {
			int G = packData.hes; //geometry, 0=E -1=H 1=S
			switch (G) {
				case -1: { //Hyperbolic case
					return packData.h_radcalc(v,r,aim,N,uP); //TODO there are small differences
					//(due to roundoff?) between overlap method and genAngle.
				}
				case 1: { //Spherical case
					this.Oops("no spherical algorithm");
					return false;
				}
				default: { //Euclidean case
					return packData.e_radcalc(v, r, aim, N, uP);
				}
			}
		}
				
		/**
	     * Directs cos overlap checks according to geometry
	     * @param R double, radius at vertex
	     * @param rp double, positively oriented petal
	     * @param rn double, negatively oriented petal
	     * @return return cos(angle)
	     */
	    public double genCosOverlap(double R, double rn, double rp, double ivd1, double ivdRr, 
	    		double ivdlr) {
	    	if (packData.hes<0) { // hyp, x-radii
	    		return HyperbolicMath.h_comp_cos(R, rn, rp, ivd1, ivdRr, ivdlr);
	    	}
	    	if (packData.hes>1) { // sph  
	   			throw new DataException("cannot compute spherical case; need to insert spherical case.");
	    	}
	 		return EuclMath.e_cos_overlap(R, rn, rp, ivd1, ivdRr, ivdlr);
	    }
	    	
	/**
	 	* finds angles sum at v for given geometry    
	 * @param v
	 * @param r
	 * @param uP
	 * @return stores angle sum in uP.value 
	 */
	    public boolean genAngleSumOverlap(int v, double r, UtilPacket uP) {
		int G = packData.hes; //geometry, 0=E -1=H 1=S
		switch (G) {
			case -1: { //Hyperbolic case
				return packData.h_anglesum_overlap(v,r,uP);
			}
			case 1: { //Spherical case
				return packData.s_anglesum(v,r,uP); // no inv dist case yet
			}
			default: { //Euclidean case
				return packData.e_anglesum_overlap(v,r,uP);
			}
		}
	 }
	    	    
	    /**
	     * Return automorphism of disc or plane carryinh a->A and b->B
	     * @param a interior points stored as complex numbers
	     * @param b
	     * @param A
	     * @param B
	     * @return
	     */
	    public Mobius genMob(Complex a, Complex b, Complex A, Complex B) {
	    	int G = packData.hes; 
	    	switch (G) {
	    		case -1: { //Hyperbolic case
//	    			Complex C = new Complex(2.0,0);
//	    			return Mobius.trans_abAB(a, b, A, B, C, C); //if |c| or |C|>1 than its ignored  
	    			return Mobius.auto_abAB(a,b,A,B);
	    		}
	    		case 1: { //Spherical case
	    			Oops("spherical case not yet complete");
	    			return Mobius.affine_mob(a,b,new Complex(0,0)
	    										,new Complex(0,0));
	    		}
	    		default: { //Euclidean Case
	    			return Mobius.affine_mob(a, b, A, B);
	    		}
	    	}
	    }
	    	    
	    /**
	     * Gives the Complex coords on the unit disc for that is the provided 
	     * Hyperbolic distance from center and positively oriented angle from x-axis
	     * @param D = Hyperbolic distance from origin
	     * @param A = Pos. angle from x-axis
	     * @return comlex coord
	     */
	    public Complex HES_to_Ecent(double D, double A) {
	    	int G = packData.hes;
	    	switch (G) {
	    		case -1: { //Hyperbolic case
	    			Complex r = new Complex((Math.exp(D)-1)/(Math.exp(D)+1),0);
	    			return r.times((new Complex(0,A)).exp());
	    		}
	    		case 1: { //Spherical case
	    			Oops("spherical case not complete");
	    			return new Complex(2,2);
	    		}
	    		default: { //Euclidean Case
	    			Complex r = new Complex(D,0);
	    			return r.times((new Complex(0,A)).exp());
	    		}
	    	}
	    }
	    
	    /**
	     * Gives the Complex coords on the unit disc for the provided 
	     * distance from center and positively oriented angle from x-axis
	     * @param D = distance from origin
	     * @param A = Pos. angle from x-axis
	     * @return comlex coord
	     */
	    public Complex HES_Norm(double D, double A) {
	    	D = Math.abs(D);
	    	int G = packData.hes;
	    	switch (G) {
	    		case -1: { //Hyperbolic case
	    			Complex r = new Complex((Math.exp(D)-1)/(Math.exp(D)+1),0);
	    			return r.times((new Complex(0,A)).exp());
	    		}
	    		case 1: { //Spherical case
	    			Oops("spherical case not complete");
	    			return new Complex(2,2);
	    		}
	    		default: { //Euclidean Case
	    			Complex r = new Complex(D,0);
	    			return r.times((new Complex(0,A)).exp());
	    		}
	    	}
	    }
	    
		/**
	     * Return value of z after applying a translation
	     * @param z
	     * @param a 
	     * @return
	     */
	    public Complex genTrans(Complex z, Complex a) {
	    	int G = packData.hes; 
	    	switch (G) {
	    		case -1: { //Hyperbolic case
	    			return Mobius.mob_trans(z,a); 
	    			//returns value of z under (a-z)/(1-z*conj(a)
	    		}
	    		case 1: { //Spherical case
	    			Oops("spherical case not yet complete");
	    			return new Complex(0,0);
	    		}
	    		default: { //Euclidean Case
	    			return z.add(a);
	    		}
	    	}
	    }
	    
		 /** 
		 * Return x-radius of a vertex. Converts "x-radius" to 
		 * actual hyperbolic radius (s radius?), which is what outside world should see.
		 * Converted from PackData.getRadius
		 */
	    public double xRadToH(double x) {
		    if (packData.hes<0 && x> 0.0) {
		      if (x>.0001) return ((-0.5)*Math.log(1.0-x));
		      else return (x*(1.0+x*(0.5+x/3))/2);
		    }
		    return x;
	    }
	    
		/**
		 * Enter radius in rData; in hyp case, convert hyp. This is taken from
		 * PacData.setRadius
		 * radius to "x-radius".
		 * @param vert
		 * @param myRadii
		 */
	    public double hRadToX(double x_rad) {
	    	double h_rad=0.0;
			if(packData.hes < 0) { // hyperbolic: store as x-radii
				if(x_rad > 0.0) {
					if(x_rad > 0.0001) h_rad = 1-Math.exp(-2.0*x_rad);
					else h_rad = 2.0*x_rad*(1.0 - x_rad*(1.0-2.0*x_rad/3.0));
				}
				// can be negative (useful as euclidean radius for horocycles)
				else if (x_rad<=0.0) h_rad = x_rad;
				else h_rad=1-Math.exp(-1.0);// default
				return h_rad;
			}
			else if (packData.hes>0) if (x_rad>=Math.PI) h_rad=Math.PI-OKERR;
			else if (x_rad<=0.0) h_rad=OKERR;
			return h_rad;
	    }
	    
	    public int mod(int x,int y) {
//	    	if (x%y<0) x=x%y+y;
//	    	return x%y;
//	    	static int mod(int a, int b)
	    	return ((x%y)+y)%y;
	    }
	    
	    public String vectorToString(Vector<String> vector, String delimiter) {
	        StringBuilder vcTostr = new StringBuilder();
	        if (vector.size() > 0) {
	            vcTostr.append(vector.get(0));
	            for (int i=1; i<vector.size(); i++) {
	                vcTostr.append(delimiter);
	                vcTostr.append(vector.get(i));
	            }
	        }
	        return vcTostr.toString();
	        
}

}

