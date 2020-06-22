package ftnTheory;

import java.util.Random;
import java.util.Vector;

import allMains.CirclePack;
import geometry.EuclMath;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
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
	
	public TorusEnergy(PackData p) {
		super(p);
		packData=p;
		if (packData.bdryCompCount!=0 || packData.euler!=0 || packData.hes!=0) {
			CirclePack.cpb.errMsg("Error starting 'TorusEnergy': packing must be a torus.");
			return;
		}
		extensionType="TORUS ENERGY";
		extensionAbbrev="TE";
		toolTip="Manipulate torus combinatorics to study packing energy ";
		registerXType();
		if (running) {
			homePack=packData.copyPackTo();
			packData.packExtensions.add(this);
		}
		temp=100; // starting temp
		
		// create oriented edge list (will get shuffled)
		edgeList=new EdgeLink(packData);
		for (int v=1;v<=packData.nodeCount;v++) {
			int []flower=packData.kData[v].flower;
			for (int j=1;j<=packData.kData[v].num;j++)
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
			StringBuilder stbld=new StringBuilder("TE status, p"+packData.packNum+":\n");
			stbld.append("  Energy ="+String.format("%.10f",getEnergy(packData))+
					"; Temp = "+String.format("%.4f",temp)+"; Cutoff = "+String.format("%.4f", cutoff)+";\n");

			// list the various degrees (counts)
			int top=0;
			int []bin=new int[packData.nodeCount+1];
			for (int v=1;v<=packData.nodeCount;v++) {
				int num=packData.kData[v].num;
				bin[num]=bin[num]+1;
				top=(num>top)?num:top;
			}
			for (int n=1;n<=top;n++) {
				if (bin[n]!=0) 
					stbld.append(" "+n+" ("+bin[n]+"); ");
			}
			
			// Conformal modulus
			double []tor=TorusModulus.torus_tau(packData);
			double tau=(tor[0]>tor[1]) ? tor[0]:tor[1];
			stbld.append("\n  Modulus = "+String.format("%.6f",tau)+". \n Degrees: ");

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
				int rslt=cpCommand(packData,"flip "+edge.v+" "+edge.w);
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
			energy=getEnergy(packData);
			int tick=0;
			int hits=0;
			while (tick<2*fcount && hits<fcount) {
				EdgeSimple edge=edgeList.get(rand.nextInt(edgeList.size()));
				double compEnergy=compareEnergy(edge.v,edge.w);
				if (tmpPack!=null && (compEnergy<energy || Math.exp(-(compEnergy-energy)/temp)>cutoff)) {
					CPScreen cps=packData.cpScreen;
					cps.swapPackData(tmpPack,true);
					packData=cps.getPackData();
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
		packData.repack_call(1000,true,false); // use oldreliable
		normalize();
		cpCommand(packData,"color -c d");
		energy=getEnergy(packData);
		if (flag) {
			packData.fillcurves(); 
			try {
				packData.comp_pack_centers(false,false,2,.0000001);
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
			for (int j=0;j<p.kData[v].num;j++)
				if (j>v) {
					double len=groundLength-(p.rData[v].rad+p.rData[p.kData[v].flower[j]].rad);
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
		for (int f=1;f<=packData.faceCount;f++) {
			int []verts=packData.faces[f].vert;
		
			// assume tangency
			double r0=packData.rData[verts[0]].rad;
			double r1=packData.rData[verts[1]].rad;
			double r2=packData.rData[verts[2]].rad;
			area += EuclMath.eArea(r0,r1,r2,1.0,1.0,1.0);
		}
		double factor=Math.sqrt(area);
		for (int v=1;v<=packData.nodeCount;v++) 
			packData.rData[v].rad =packData.rData[v].rad/factor;
		return 1;
	}
	
	/**
	 * We call for an edge flip, but first we have to be ready
	 * to readjust 'edgeList'.
	 * @return 0 on error
	 */
	public int flipedge(int v,int w) {
		if (v==w || v<1 || w<1 || v>packData.nodeCount || 
				w>packData.nodeCount || packData.nghb(v, w)<0)
			return 0;
		
		// common neighbors are 'a' and 'b'; make sure v < w and a < b

		int indxvw=EdgeLink.getVW(edgeList,v,w);
		if (indxvw<0)
			return 0;
		int a=packData.kData[w].flower[packData.nghb(w, v)+1];
		int b=packData.kData[v].flower[packData.nghb(v,w)+1];
		int rslt=cpCommand(packData,"flip "+v+" "+w);
		if (rslt<=0)
			return 0;
		reset();
		
		// replace only edge by new edge
		if (a>b) {
			int hold=b;
			b=a;
			a=hold;
		}
		EdgeSimple ne=new EdgeSimple(a,b);
		edgeList.remove(indxvw);
		edgeList.add(indxvw,ne);
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
		tmpPack=packData.copyPackTo();
		int rslt=cpCommand(tmpPack,"flip "+v+" "+w);
		if (rslt<=0) {
			tmpPack=null;
			return -1.0;
		}
		tmpPack.setCombinatorics();
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
