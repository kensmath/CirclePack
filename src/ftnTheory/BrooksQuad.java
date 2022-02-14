package ftnTheory;

import java.awt.Color;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import dcel.HalfEdge;
import dcel.RawManip;
import dcel.Vertex;
import komplex.EdgeSimple;
import math.Mobius;
import packing.PackCreation;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.StringUtil;

/** 
 * "Brooks" packings are patterns of circles inside quadrilateral interstices.
 * A "quad" interstice is one formed by 4 successively tangent circles with
 * mutually disjoint interiors, labeled T,L,B,R for 'top', 'left', 'bottom',
 * 'right'. As a Brooks pattern develops, there's always a 'plug' vertex P,
 * which is a 4-degree vertex whose petals form the current quadrilateral
 * interstice; so P isn't technically a part of the Brooks pattern, just
 * necessary for the mechanics, since we need a packing. Generically, Brooks 
 * packings go on ad infinitum, but at each finite stage the plug caps off
 * remaining quad interstice.
 * 
 * This code converts strings of H's and V's into Brooks packings by
 * modifying an initial 'quad' by adding successive 'horizontal' and 
 * 'vertical' circles a la Robert Brooks, Duke Math.J. 1985. The original
 * routine used here was conceived by Matt Cathey.
 *
 * There are two modes of operation indicated by flag: 
 *  (1) insert new circle in location as in Brooks' original scheme; 
 *  (2) insert new circle in alternating fashion: verticals alternate
 *     left/right, horizontals, up/down. */

public class BrooksQuad extends PackExtender {
	public static final int  MAX_LIST=4000; 
	public boolean Brooks_mode; // if true: Brooks mode, false: alternating mode
	public int T,L,B,R; // indices for top, left, bottom, right
	public int P;       // 'plug'; filler circle for interstice, not actually part of Brooks pattern
	public int N;       // Former plug, now the last new vertex
	public int v_flips; // number of 'vertical' flips
	public int h_flips; // number of 'horizontal' flips
	public int v_mode;  // keep track of parity for vertical adds
	public int h_mode;  // keep track of parity for horizontal adds
	public StringBuilder hvList;  // hold string of h/v's showing history
	
	// Constructors
	public BrooksQuad(PackData p,boolean mode) {
		super(p);
		Brooks_mode=mode;
		extensionType="BROOKS_QUAD";
		extensionAbbrev="BQ";
		toolTip="'BrooksQuad' creates/manipulates 'Brooks' packings "+
			"of circles in 'quad' interstices.";
		registerXType();

		initQuad();
		T=1;L=2;B=3;R=4;
		N=5;
		P=5;
		v_flips=1;
		h_flips=0;
		v_mode=0;
		h_mode=0;
		if (!Brooks_mode) { // alternating? preliminary interchange, T/B and L/R  
			int dum=T;T=B;B=dum;dum=L;L=R;R=dum;
		}
		hvList=new StringBuilder();
		packData.packExtensions.add(this);
	}
	
	public BrooksQuad(PackData p) {
		this(p,true);
	}
	
	/**
	 * This creates an initial 4-flower as the basis for later 
	 * changes, swap so the center is the largest index, it's 
	 * the 'plug'.
	 */
	public void initQuad() {
		PackData newData=PackCreation.seed(4,0);
		if (newData==null)
			Oops("Failed to build initial seed.");
		CirclePack.cpb.swapPackData(newData,packData.packNum,true);
		packData=newData;
		RawManip.swapNodes_raw(packData.packDCEL,1,5);
		for (int v=1;v<=4;v++) {
			packData.setCircleColor(v,ColorUtil.coLor(208)); // line green
		}
		packData.setCircleColor(5,ColorUtil.getBGColor());
		packData.setAlpha(5);
		packData.directGamma(1);
		packData.set_aim_default();
		normalize();
		cpCommand("set_screen -d");
		cpCommand("set_disp_text -w -cf");
		draw();
	}
	
	/**
	 * A new 'vertical' circle is one which is tangent to T and B (and L or R, 
	 * depending). We do various interchanges of T/B and L/R depending on the
	 * mode and on the sequence of preceding additions.
	 * Vertical circles are colored red, but code is 149 if added on the
	 * left, 151 if in alternating mode and added on the right.
	 */
	public void addVertical() {

//System.out.println("add vertical: T="+T+", L="+L+", B="+
//		B+", R="+R+". Plug is "+P);

		int dum;
		if (v_mode==0) // beginning sequence of vertical flips? 
			v_flips=0;
		Color cLrCode=ColorUtil.coLor(149);
		// Alternating mode, even number of h_flips, exchange T/B, L/R 
		if (!Brooks_mode && (h_flips%2)==0) { // alternating mode and even
		/* Flip (exchange labels) each V (vertical). However, for first 
			   V step, may have to compensate for flips done while in previous 
			   sequence of horizontal steps. */
			dum=T;T=B;B=dum; // exchange T and B 
			dum=L;L=R;R=dum; // exchange L and R 
			v_flips++;
			cLrCode=ColorUtil.coLor(151);
		}
		h_mode=0;
		v_mode=1;

		HalfEdge PL=packData.packDCEL.findHalfEdge(new EdgeSimple(P,L));
		
		// split the edge from P (plug) to L (left) and fix
		RawManip.splitEdge_raw(packData.packDCEL, PL);
				
		// switch new vertex with old plug
		int pnc=packData.packDCEL.vertCount;
		Vertex newv=packData.packDCEL.vertices[pnc]; 
		Vertex oldP=packData.packDCEL.vertices[pnc-1];
		newv.vertIndx=pnc-1;
		oldP.vertIndx=pnc;
		packData.packDCEL.vertices[pnc]=oldP;
		packData.packDCEL.vertices[pnc-1]=newv;

		// process and attach 
		packData.packDCEL.fixDCEL(packData);

		// set the colors
		packData.setCircleColor(packData.nodeCount-1,
				new Color(cLrCode.getRed(),cLrCode.getGreen(),
						cLrCode.getBlue())); // red 149 or 151 
		packData.setCircleColor(packData.nodeCount,ColorUtil.getBGColor());
		hvList.append("v");

		L=pnc-1; // N is the new 'left' of interstice
		P=pnc; // new plug index

		packData.set_aim_default();
		hvList.append("v");
	}
	
	/**
	 * A new 'horizontal' circle is one which is tangent to L and R (and T or B, 
	 * depending). We do various interchanges of T/B and L/R depending on the
	 * mode and on the sequence of preceding additions.
	 * Horizontal circles are colored blue, but code is 49 if added on the
	 * top, 51 if in alternating mode and added on the bottom.
	 */
	public void addHorizontal() {
		
//		System.out.println("add horizonatal: T="+T+", L="+L+", B="+
//				B+", R="+R+". Plug is "+P);

		int dum;
		Color cLrCode=ColorUtil.coLor(49);
		
		if (h_mode==0) // beginning sequence of horizontal flips?
			h_flips=0;
		if (!Brooks_mode && (v_flips%2)==0) {
			/* Flip (exchange labels) each H. However, for first 
			   H step, may have to compensate for flips done in sequence
			   of previous vertical steps. */
			dum=T;T=B;B=dum; // exchange T and B 
			dum=L;L=R;R=dum; // exchange L and R 
			h_flips++;
		    cLrCode=ColorUtil.coLor(51);
		}
		v_mode=0;
		h_mode=1;
		  
		HalfEdge PT=packData.packDCEL.findHalfEdge(new EdgeSimple(P,T));
		
		// split the edge from P (plug) to T (top) and fix
		RawManip.splitEdge_raw(packData.packDCEL, PT);
			
		// switch new vertex with old plug
		int pnc=packData.packDCEL.vertCount;
		Vertex newv=packData.packDCEL.vertices[pnc]; 
		Vertex oldP=packData.packDCEL.vertices[pnc-1];
		newv.vertIndx=pnc-1;
		oldP.vertIndx=pnc;
		packData.packDCEL.vertices[pnc]=oldP;
		packData.packDCEL.vertices[pnc-1]=newv;

		packData.packDCEL.fixDCEL(packData);

		// set the colors
		packData.setCircleColor(packData.nodeCount-1,
				new Color(cLrCode.getRed(),cLrCode.getGreen(),
						cLrCode.getBlue())); // blue 49 or 51
		packData.setCircleColor(packData.nodeCount,ColorUtil.getBGColor());

		T=pnc-1;
		P=pnc; // new plug index
		packData.set_aim_default();
		hvList.append("h");
	}

	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		if (cmd.startsWith("remat")) {
			if (flagSegs==null || flagSegs.size()==0 || (items=flagSegs.get(0)).size()<6)
				Oops("usage: rematch v u w V U W");
			Mobius mob=Mobius.mob_vuwVUW(packData,Integer.parseInt(items.get(0)),
					Integer.parseInt(items.get(1)),Integer.parseInt(items.get(2)),
					Integer.parseInt(items.get(3)),Integer.parseInt(items.get(4)),
									Integer.parseInt(items.get(5)));
			Mobius hold=CPBase.Mob;
			CPBase.Mob=mob;
			int ans=cpCommand(packData,"mobius");
			if (hold!=null) {
				CPBase.Mob=hold;
			}
			return ans;
		}
		
		else if (cmd.startsWith("HV") || cmd.startsWith("VH")) {
			String hvString=StringUtil.reconstitute(flagSegs);
			for (int i=0;i<hvString.length();i++) {
				char c=hvString.charAt(i);
				if (c=='h' || c=='H')
					addHorizontal();
				else if (c=='v' || c=='V')
					addVertical();
			}
			normalize();
			draw();
			return 1;
		}
		else if (cmd.startsWith("cfrac")) {
			String hvString=StringUtil.reconstitute(flagSegs);
			String []ns=hvString.split("\\s+");
			for (int i=0;i<ns.length;i++) {
				int n=Integer.parseInt(ns[i]);
				if (2*(int)(i/2)==i) // even, vertical
					for (int j=0;j<n;j++)
						addVertical();
				else 
					for (int j=0;j<n;j++)
						addHorizontal();
			}
			normalize();
			draw();
			return 1;
		}
		else if (cmd.length()>=4 && cmd.startsWith("add")) {
			if (cmd.charAt(3)=='V' || cmd.charAt(3)=='v') {
				addVertical();
				msg("Brooks: added 'vertical' circle");
			}
			else if (cmd.charAt(3)=='H' || cmd.charAt(3)=='h') {
				addHorizontal();
				msg("Brooks: added 'horizontal' circle");
			}
			normalize();
			draw();
			return 1;
		}
		else if (cmd.startsWith("norm")) {
			normalize();
			draw();
			return 1;
		}
		else if (cmd.startsWith("status")) {
			if (Brooks_mode) 
				msg("BQ: in Brooks mode");
			else
				msg("BQ: in Alternating mode");
			msg("BQ history: "+hvList.toString());
			return 1;
		}
		else if (cmd.startsWith("toggle")) {
			if (Brooks_mode) {
				Brooks_mode=false;
				msg("BQ: mode to 'alternating'");
			}
			else {
				Brooks_mode=true;
				msg("BQ: mode to 'Brooks'");
			}
			return 1;
		}
		return super.cmdParser(cmd, flagSegs);
	}

	public void normalize() {
		cpCommand("repack");
		cpCommand("layout");
		cpCommand("norm_scale -h 2 4");
		cpCommand("norm_scale -u 1");
	}
	
	public void draw() {
		try {
			String str=new String("disp -w -cf a(1,"+(packData.nodeCount-1)+") -c M");
			cpCommand(packData,str);
		} catch(Exception ex) {
			Oops(ex.getMessage());
		}
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("HV","<str>",null,"string of 'h', 'v' for adding horizontal, vertical circles"));
		cmdStruct.add(new CmdStruct("addV",null,null,"add a 'vertical' circle"));
		cmdStruct.add(new CmdStruct("addH",null,null,"add a 'horizontl' circle"));
		cmdStruct.add(new CmdStruct("norm",null,null,"normalize in standard position"));
		cmdStruct.add(new CmdStruct("toggle",null,null,"toggles between 'Brooks' and 'alternating' modes"));
		cmdStruct.add(new CmdStruct("cfrac","<n1 n2 ..>",null,"n1 v's followed by n2 h's, etc"));
		cmdStruct.add(new CmdStruct("rematch","uvwVUW",null,"apply Mobius to move 3 circles to 3 circles"));
	}

}
