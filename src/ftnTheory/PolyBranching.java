package ftnTheory;

import java.io.BufferedWriter;
import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import dcel.HalfEdge;
import dcel.PackDCEL;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.HyperbolicMath;
import listManip.FaceLink;
import listManip.HalfLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import util.DispFlags;

public class PolyBranching extends PackExtender {

	Vector<Integer> branchVerts;
	PackData rangePack;
	
	// Constructor
	public PolyBranching(PackData p) {
		super(p);
		packData=p;
		extensionType="POLYBRANCHING";
		extensionAbbrev="PB";
		toolTip="'POLYBRANCHING': for manipulation of discrete "+
			"polynomials via their branch values";
		registerXType();
		
		rangePack=null;
		setBranching();
		running=true;
		packData.packExtensions.add(this);
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		if (cmd.startsWith("set_br")) {
			setBranching();
		}
		if (cmd.startsWith("copy")) { // copy 'rangeData' to some pack
			if (rangePack==null || !rangePack.status ||
					rangePack.nodeCount!=packData.nodeCount) {
				errorMsg("abort copy: 'rangePack' doesn't agree with parent packing");
				return 0;
			}
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				CirclePack.cpb.swapPackData(rangePack,pnum,false);
			} catch (Exception ex) {
				return 0;
			}
		}	
		if (cmd.startsWith("report")) {
			if (branchVerts==null || branchVerts.size()==0) {
				errorMsg("No branching is known: try 'set_branching'");
				return 0;
			}
			
			try {

				// copy packData (which may have been changed) to 'rangePack'
				rangePack=packData.copyPackTo();
				
				// make 'rangePack' into branched max packing
				cpCommand(rangePack,"set_aim -d");
				for (int j=0;j<branchVerts.size();j++) {
					rangePack.setAim(branchVerts.get(j),
							rangePack.getAim(branchVerts.get(j))+2.0*Math.PI);
				}
				cpCommand(rangePack,"geom_to_h");
				cpCommand(rangePack,"set_rad 5.0 b");
				cpCommand(rangePack,"repack 10000");
				cpCommand(rangePack,"layout");

				// max pack the domain
				cpCommand(packData,"max_pack");
			} catch (Exception ex) {
				throw new ParserException(ex.getMessage());
			}

			// accumulate the data
			CircleSimple sc =HyperbolicMath.h_to_e_data(
					rangePack.getCenter(rangePack.getAlpha()),
					rangePack.getRadius(rangePack.getAlpha()));
			double imageScale=sc.rad;
				
			sc =HyperbolicMath.h_to_e_data(
					packData.getCenter(packData.getAlpha()),
					packData.getRadius(packData.getAlpha()));
			double domainScale=sc.rad;
				
			Vector<Double> objectives=new Vector<Double>(branchVerts.size());
			for (int j=0;j<branchVerts.size();j++) {
				int v=branchVerts.get(j);
				sc=HyperbolicMath.h_to_e_data(
						rangePack.getCenter(v),rangePack.getRadius(v));
				double rangeAbs=sc.center.abs()/imageScale;
				sc=HyperbolicMath.h_to_e_data(
						packData.getCenter(v),packData.getRadius(v));
				double domainAbs=sc.center.abs()/domainScale;
				objectives.add(rangeAbs/domainAbs);
			}
				
			// Compare and report
			double minObjective=objectives.get(0);
			int best=0;
			for (int j=1;j<branchVerts.size();j++) {
				double obj=objectives.get(j);
				if (obj<minObjective) {
					best=j;
					minObjective=obj;
				}
			}
				
			StringBuilder strb= new StringBuilder("Objective values: "+
					"min is "+String.format("%.8e",objectives.get(best))+" at v="+branchVerts.get(best)+"\n");
			for (int j=0;j<branchVerts.size();j++) {
				strb.append("  v "+branchVerts.get(j)+":  "+String.format("%.6e",objectives.get(j))+"\n");
			}
				
			// report
			msg(strb.toString());
			return 1;
		}
		return super.cmdParser(cmd, flagSegs);
	}

	/**
	 * Determine branching from aims of parent 'packData'.
	 * Normally this is done only on startup, but can be 
	 * revisited.
	 */
	public void setBranching() {
		branchVerts=new Vector<Integer>(5);
		for (int v=1;v<=packData.nodeCount;v++) {
			int i=1;
			while (!packData.isBdry(v) && 
					packData.getAim(v)>(2*i+1)*Math.PI) {
				branchVerts.add(v);
				i++;
			}
		}
		int cnt=branchVerts.size();
		if (cnt==0) {
			errorMsg("No branch points were specified in the 'aim's");
			return;
		}
		StringBuilder strb=new StringBuilder("PolyBranching p"+packData.packNum+": vertices ");
		for (int j=0;j<cnt;j++) {
			strb.append(" "+branchVerts.get(j));
		}
		msg(strb.toString());
	}
	
	public void helpInfo() {
		helpMsg("Info on PackExtender "+extensionAbbrev+" (Poly branching)");
		helpMsg("Commands:\n"+
				"  set_branching:  determine the branching from the parent packing\n"+
				"  report:    compute the objective values and their minimum\n"+
				"  copy {n}   copy 'rangePack' to pack n\n");
	}
	
}
