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
	 * Compute Mobius associated with holonomy along 
	 * a closed chain of faces. The faces are defined
	 * by given 'HalfLink', the first assumed to be 
	 * in the desired geometric location. Centers are
	 * then recomputed iteratively until the final
	 * locations of the original three are found. Note
	 * that data in the packing is not changed.
	 * The transformation and its Frobenius norm (how 
	 * close to identity) are displayed in 'Messages' and
	 * written to a file if fp!=null. Return Frobenius norm.
	 * @param p PackData,
	 * @param fp BufferedWriter (or null)
	 * @param hlink HalfLink, gives associated chain of faces
	 * @param fix boolean: true, then draw colored faces as we go
	 * @return double, -1 on error 
	 */
	public static double holonomy_trace(PackData p,
			BufferedWriter fp,HalfLink hlink,boolean fix) {

		  if (p.hes>0) {
			  CirclePack.cpb.myErrorMsg(
					  "holonomy not yet available in spherical setting.");
		    return -1;
		  }

		  PackDCEL pdcel=p.packDCEL;
		  HalfEdge startedge=hlink.getFirst();
		  HalfEdge endedge=hlink.getLast();
		  
		  Iterator<HalfEdge> his=hlink.iterator();
		  
		  // locate the first face
		  HalfEdge currhe=his.next();
		  HalfEdge nexthe=currhe;
		  CircleSimple cs0=pdcel.getVertData(currhe);
		  CircleSimple cs1=pdcel.getVertData(currhe.next);
		  double rad=pdcel.getVertRadius(currhe.next.next);
		  CircleSimple cs2=CommonMath.comp_any_center(cs0,cs1,rad,
				  currhe.next.getSchwarzian(),
				  currhe.next.next.getSchwarzian(),
				  currhe.getSchwarzian(),p.hes);
		  Complex[] startZ=new Complex[2];
		  startZ[0]=new Complex(cs0.center);
		  startZ[1]=new Complex(cs1.center);
		  while (his.hasNext()) {
			  currhe=nexthe;
			  nexthe=his.next();
			  
			  if (nexthe.twin.prev==currhe) // head-to-head?
 				  cs0=cs2;
			  else // tail-to-tail
				  cs1=cs2;

			  rad=pdcel.getVertRadius(nexthe.next.next);
			  cs2=CommonMath.comp_any_center(cs0,cs1,rad,
				  nexthe.next.getSchwarzian(),
				  nexthe.next.next.getSchwarzian(),
				  nexthe.getSchwarzian(),p.hes);
		  }
		  
		  // line up triples
		  Complex[] endZ=new Complex[2];
		  if (endedge.next==startedge) { // head-to-tail
			  endZ[0]=cs1.center;
			  endZ[1]=cs2.center;
		  }
		  else { // tail-to-head
			  endZ[0]=cs2.center;
			  endZ[1]=cs0.center;
		  }
		  
		  String opts=null;
		  if (fix) opts=new String("-ff"); // draw the colored faces as we go
		  DispFlags dflags=new DispFlags(opts,p.cpScreen.fillOpacity);
		  Mobius mob=new Mobius(); // initialize transformation 
		  if (p.hes<0) 
			  mob=Mobius.auto_abAB(startZ[0],startZ[1],endZ[0],endZ[1]);
		  else {
			  Complex denom=startZ[1].minus(startZ[0]);
			  if (denom.abs()<.00000001) 
				  return 0;
			  mob.a=endZ[0].minus(endZ[1]).divide(denom);
			  mob.b=endZ[0].minus(mob.a.times(startZ[0]));
		  }
		  double frobNorm=Mobius.frobeniusNorm(mob);
		  CirclePack.cpb.msg(
				  "Frobenius norm "+String.format("%.8e",frobNorm)+
				  ", \nMobius is: \n"+
				  "  a = ("+String.format("%.8e",mob.a.x)+","+
				  String.format("%.8e",mob.a.y)+
				  ")   b = ("+String.format("%.8e",mob.b.x)+","+
				  String.format("%.8e",mob.b.y)+")\n"+
				  "  c = ("+String.format("%.8e",mob.c.x)+","+
				  String.format("%.8e",mob.c.y)+
				  ")   d = ("+String.format("%.8e",mob.d.x)+","+
				  String.format("%.8e",mob.d.y)+")");
		  if (fp!=null) { // print to file also 
			  try {
		    fp.write("\nFrobenius norm:\n  ");
		    fp.write(frobNorm+" \n");
		    // print mobius 
		    fp.write("Mobius:\n  a= "+mob.a.x+" + i*("+mob.a.y+")\n");
		    fp.write("  b= "+mob.b.x+" + i*("+mob.b.y+")\n");
		    fp.write("  c= "+mob.c.x+" + i*("+mob.c.y+")\n");
		    fp.write("  d= "+mob.d.x+" + i*("+mob.d.y+")\n\n");
			  } catch(Exception ex) {
				  CirclePack.cpb.myErrorMsg("There were IOExceptions");
			  }
		  }
		  return frobNorm;
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
