package ftnTheory;

import java.util.Random;
import java.util.Vector;

import allMains.CirclePack;
import exceptions.CombException;
import geometry.EuclMath;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import packing.PackData;
import packing.PackExtender;
import packing.TorusData;
import util.CmdStruct;

public class TorusEnergy extends PackExtender {
	
	static double mycon=Math.sqrt(1/Math.sqrt(3));
	
	PackData homePack;  // the original torus for reference
	PackData tmpPack;   // pack for speculative energy computations
	double temp;		// temperature for simulated annealling
	EdgeLink edgeList;  // ordered edges <v,w>, v < w; entries change with flips
	EdgeSimple nedge;   // store in prep for flipping
	Random rand;        // random number generator
	boolean dispMode;   // if true, then redraw on 'reset'
	double energy;      // current energy: sqrt(sum of edge lengths squared)
	double cutoff;      // probability threshold for accepting switch energy-losing switch 
	public TorusData torusData;
	
	public TorusEnergy(PackData p) {
		super(p);
		extenderPD=p;
		try {
			torusData=new TorusData(p);
		} catch (Exception ex) {
			throw new CombException("Error 'TorusEnergy': 'TorusData' failed");
		}
		extensionType="TORUS ENERGY";
		extensionAbbrev="TE";
		toolTip="Manipulate torus combinatorics to study packing energy ";
		registerXType();
		if (running) {
			homePack=extenderPD.copyPackTo();
			extenderPD.packExtensions.add(this);
		}
		temp=100; // starting temp
		
		// create oriented edge list (will get shuffled)
		edgeList=new EdgeLink(extenderPD);
		for (int v=1;v<=extenderPD.nodeCount;v++) {
			int[] flower=extenderPD.getFlower(v);
			for (int j=1;j<=extenderPD.countFaces(v);j++)
				if (flower[j]>v)
					edgeList.add(new EdgeSimple(v,flower[j]));
		}
		rand=new Random();
		tmpPack=null;
		cutoff=.95;
	}
	  
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
//		Vector<String> items=null;
		
		if (cmd.startsWith("status")) {
			reset(false);
			StringBuilder stbld=new StringBuilder("TE status, p"+extenderPD.packNum+":\n");
			stbld.append("  Energy ="+String.format("%.10f",getEnergy(extenderPD))+
					"; Temp = "+String.format("%.4f",temp)+"; Cutoff = "+String.format("%.4f", cutoff)+";\n");

			// list the various degrees (counts)
			int top=0;
			int []bin=new int[extenderPD.nodeCount+1];
			for (int v=1;v<=extenderPD.nodeCount;v++) {
				int num=extenderPD.countFaces(v);
				bin[num]=bin[num]+1;
				top=(num>top)?num:top;
			}
			for (int n=1;n<=top;n++) {
				if (bin[n]!=0) 
					stbld.append(" "+n+" ("+bin[n]+"); ");
			}
			
			// Conformal modulus
			stbld.append("\n  Modulus = "+String.format("%.6f",torusData.tau)+". \n Degrees: ");

			CirclePack.cpb.msg(stbld.toString());
			return 1;
		}
		else if (cmd.startsWith("reset")) {
			try {
				int fg=Integer.parseInt(flagSegs.get(0).get(0));
				if (fg==1) // yes, redraw
					return reset(true);
				else
					return reset(false);
			} catch(Exception ex) {
				return reset();
			}
		}
		else if (cmd.startsWith("norm")) {
			return reset();
		}
		else if (cmd.startsWith("rande")) {
			int indx=rand.nextInt(edgeList.size());
			nedge=edgeList.get(indx);
			return 1;
		}
		else if (cmd.startsWith("set_T")) {
			try {
				double newT=Double.parseDouble(flagSegs.get(0).get(0));
				if (newT>0) {
					temp=newT;
					return 1;
				}
			} catch(Exception ex) {
				return 0;
			}
		}
		else if (cmd.startsWith("set_C")) {
			try {
				double newC=Double.parseDouble(flagSegs.get(0).get(0));
				if (newC<=1.0) {
					cutoff=newC;
					return 1;
				}
			} catch(Exception ex) {
				return 0;
			}
		}
		else if (cmd.startsWith("flip")) {
			int fcount=1;
			try {
				fcount=Integer.parseInt(flagSegs.get(0).get(0));
			} catch(Exception ex) {
				fcount=1;
			}
			int tick=0;
			int hits=0;
			while (tick<2*fcount && hits<fcount) {
				EdgeSimple edge=edgeList.get(rand.nextInt(edgeList.size()));
				int rslt=cpCommand(extenderPD,"flip "+edge.v+" "+edge.w);
				if (rslt>0)
					hits++;
				tick++;
			}
			return hits;
		}
		else if (cmd.startsWith("Mon")) {
			int fcount=1;
			try {
				fcount=Integer.parseInt(flagSegs.get(0).get(0));
			} catch(Exception ex) {
				fcount=1;
			}
			energy=getEnergy(extenderPD);
			int tick=0;
			int hits=0;
			while (tick<2*fcount && hits<fcount) {
				EdgeSimple edge=edgeList.get(rand.nextInt(edgeList.size()));
				double compEnergy=compareEnergy(edge.v,edge.w);
				if (tmpPack!=null && (compEnergy<energy || Math.exp(-(compEnergy-energy)/temp)>cutoff)) {
					int pnum=extenderPD.packNum;
					extenderPD=CirclePack.cpb.swapPackData(tmpPack,pnum,true);
					hits++;
				}
				tick++;
			}
			reset(true); // this lays out, etc., and displays
			return hits;
		}
		
		return 0;
	}
		
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("norm",null,null,
				"Repack/layout and normalize so the area of the torus is one"));
		cmdStruct.add(new CmdStruct("rflip","n",null,
				"Do n successful random flips, up to 2n attempts"));
		cmdStruct.add(new CmdStruct("randedge",null,null,
				"Set 'nedge' as a uniformly random edge, 'null' on error"));
		cmdStruct.add(new CmdStruct("pick","n",null,
				"Use procedure 'n' to choose next flip"));
		cmdStruct.add(new CmdStruct("status",null,null,
				"print temperature, nodecount, discrepancies from degree 6"));
		cmdStruct.add(new CmdStruct("set_T","t",null,
				"Set temperature to 't'"));
		cmdStruct.add(new CmdStruct("set_C","x",null,
				"Set cutoff probability at 'x'"));
		cmdStruct.add(new CmdStruct("Monte","n",null,
				"Run n random Monte Carlo moves"));
		cmdStruct.add(new CmdStruct("reset","[d]",null,
				"Recompute radii, layout, color by degree; optional 'd' 1/0, draw/don't draw"));
	}

	/**
	 * 
	 * @param flag boolean; true means force redisplay
	 * @return 1
	 */
	public int reset(boolean flag) {
//		packData.setCombinatorics(); // do we need this?
		extenderPD.repack_call(1000,true,false); // use oldreliable
		normalize();
		cpCommand(extenderPD,"color -c d");
		energy=getEnergy(extenderPD);
		if (flag) {
			extenderPD.fillcurves(); 
			try {
				extenderPD.packDCEL.layoutPacking(); 
			} catch(Exception ex) {}
			cpCommand("set_screen -a");
			cpCommand("disp -wr");
			return 1;
		}
		return 1;
	}
	
	public int reset() {
		return reset(dispMode);
	}
	
	/**
	 * Try different notions of energy: 
	 *   + sum of squared lengths of all edges
	 *   + sum of conductances squared
	 * @param p PackData
	 * @return double
	 */
	public static double getEnergy(PackData p) {
		double erg=0.0;
//		double [][]conductance=ComplexAnalysis.setConductances(p);
		double groundLength=2.0*(TorusEnergy.mycon/Math.sqrt(p.faceCount));
		for (int v=1;v<=p.nodeCount;v++) {
			int[] petals=p.packDCEL.vertices[v].getPetals();
			for (int j=0;j<petals.length;j++)
				if (petals[j]>v) {
					double len=groundLength-(p.getRadius(v)+
							p.getRadius(petals[j]));
					erg += len*len;
				}
		}
		return erg;
	}

	/**
	 * normalize so area is 1. Note this only requires radii, not layout or angle sums
	 * @return 1
	 */
	public int normalize() {
		double area=0;
		for (int f=1;f<=extenderPD.faceCount;f++) {
			int []verts=extenderPD.packDCEL.faces[f].getVerts();
		
			// assume tangency
			double r0=extenderPD.getRadius(verts[0]);
			double r1=extenderPD.getRadius(verts[1]);
			double r2=extenderPD.getRadius(verts[2]);
			area += EuclMath.eArea(r0,r1,r2,1.0,1.0,1.0);
		}
		double factor=Math.sqrt(area);
		for (int v=1;v<=extenderPD.nodeCount;v++) 
			extenderPD.setRadius(v,extenderPD.getRadius(v)/factor);
		return 1;
	}

	/**
	 * Given an edge, copy the packing to 'tmpPack', flip the edge, repack/layout/normalize,
	 * and return the resulting energy. Then one can decide whether to replace packData
	 * with result.
	 * @param v int
	 * @param w int
	 * @return double, -1 on failure
	 */
	public double compareEnergy(int v,int w) {
		tmpPack=extenderPD.copyPackTo();
		int rslt=cpCommand(tmpPack,"flip "+v+" "+w);
		if (rslt<=0) {
			tmpPack=null;
			return -1.0;
		}
		tmpPack.repack_call(1000,true,false); // use oldreliable
//		tmpPack.fillcurves(); 
//		try {
//			tmpPack.comp_pack_centers(false,false,2,.0000001);
//		} catch(Exception ex) {
//			throw new PackingException("layout failed in TorusEnergy");
//		}
		return getEnergy(tmpPack);
	}
	
}
