package ftnTheory;

import java.awt.Color;
import java.util.Vector;

import komplex.PackCreation;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.StringUtil;
import exceptions.CombException;

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
	public int T,L,B,R; // top, left, bottom, right
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
	 * This creates an initial 4-flower as the basis for later changes,
	 * swap so the center is the largest index, it's the 'plug'.
	 */
	public void initQuad() {
		CPScreen cps=packData.cpScreen;
		PackData newData=PackCreation.seed(4,0);
		if (newData==null)
			Oops("Failed to build initial seed.");
		cps.swapPackData(newData,true);
		packData=cps.packData;
		packData.swap_nodes(1,5);
		for (int v=1;v<=4;v++) {
			packData.kData[v].color=CPScreen.coLor(209); // yellow
		}
		packData.kData[5].color=CPScreen.getBGColor();
		packData.alpha=5;
		packData.gamma=1;
		packData.setCombinatorics();
		packData.set_aim_default();
		normalize();
		cpCommand("set_screen -d");
		cpCommand("set_disp_text -w -cf");
		draw();
	}
	
	/**
	 * A new 'vertical' circle is one which is tangent to T and B (and L or R, 
	 * depending). We do various interchanges of T/B and L/R depending on the
	 * mode and on the sequence of preceeding additions.
	 * Vertical circles are colored red, but code is 149 if added on the
	 * left, 151 if in alternating mode and added on the right.
	 */
	public void addVertical() {
		int dum;
		P++;
		if (P>packData.sizeLimit) packData.alloc_pack_space(P+1,true);
		packData.nodeCount=P;
		if (v_mode==0) // beginning vertical flips 
			v_flips=0;
		h_mode=0;
		v_mode=1;
		Color cLrCode=CPScreen.coLor(149);
		  // Alternating mode, even number of h_flips, exchange T/B, L/R 
		  if (!Brooks_mode && (h_flips%2)==0) { // alternating mode and even
			/* Flip (exchange labels) each V (vertical). However, for first 
			   V step, may have to compensate for flips done while in previous 
			   sequence of horizontal steps. */
		      dum=T;T=B;B=dum; // exchange T and B 
		      dum=L;L=R;R=dum; // exchange L and R 
		      v_flips++;
		      cLrCode=CPScreen.coLor(151);
		  }
		  brooks_insert(packData,P,N,T,-1);
		  brooks_insert(packData,P,N,B,1);
		  
		  // fix flower of R 
		  for (int i=0;i<=packData.kData[R].num;i++)
		    if (packData.kData[R].flower[i]==N) 
			packData.kData[R].flower[i]=P;
		  
		  // fix flower of N 
		  packData.kData[N].flower[0]=T;
		  packData.kData[N].flower[1]=L;
		  packData.kData[N].flower[2]=B;
		  packData.kData[N].flower[3]=P;
		  packData.kData[N].flower[4]=T;
		  packData.kData[N].color=new Color(cLrCode.getRed(),cLrCode.getGreen(),cLrCode.getBlue()); // red 149 or 151 
		  
		  // add flower of new temporary 'plug' vert 
		  packData.kData[P].flower=new int[5];
		  packData.kData[P].num=4;
		  packData.kData[P].bdryFlag=0;
		  packData.rData[P].rad=.5;
		  packData.rData[P].rad=2.0*Math.PI;	  
		  packData.kData[P].flower[0]=N;
		  packData.kData[P].flower[1]=B;
		  packData.kData[P].flower[2]=R;
		  packData.kData[P].flower[3]=T;
		  packData.kData[P].flower[4]=N;
		  packData.kData[P].color=CPScreen.getBGColor();
		  
		  L=N; // N is the new 'left' of interstice
		  N=P;
		  packData.setCombinatorics();
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
		int dum;
		P++;
		if (P>packData.sizeLimit) packData.alloc_pack_space(P+1,true);
		packData.nodeCount=P;
		Color cLrCode=CPScreen.coLor(49);
		
		  if (h_mode==0) h_flips=0;
		  if (!Brooks_mode && (v_flips%2)==0) {
			/* Flip (exchange labels) each H. However, for first 
			   H step, may have to compensate for flips done in sequence
			   of previous vertical steps. */
		      dum=T;T=B;B=dum; // exchange T and B 
		      dum=L;L=R;R=dum; // exchange L and R 
		      h_flips++;
		      cLrCode=CPScreen.coLor(51);
		    }
		  v_mode=0;
		  h_mode=1;
		  brooks_insert(packData,P,N,R,-1);
		  brooks_insert(packData,P,N,L,1);
		  
		  // fix flower of 3 
		  for (int i=0;i<=packData.kData[B].num;i++)
		    if (packData.kData[B].flower[i]==N) 
			packData.kData[B].flower[i]=P;
		  
		  // fix flower of N 
		  packData.kData[N].flower[0]=T;
		  packData.kData[N].flower[1]=L;
		  packData.kData[N].flower[2]=P;
		  packData.kData[N].flower[3]=R;
		  packData.kData[N].flower[4]=T;
		  packData.kData[N].color=new Color(cLrCode.getRed(),cLrCode.getGreen(),cLrCode.getBlue()); // blue, 49 or 51 

		  // add flower of new P vert 
		  packData.kData[P].flower=new int[5];
		  packData.kData[P].num=4;
		  packData.kData[P].bdryFlag=0;
		  packData.rData[P].rad=.5;
		  packData.rData[P].rad=2.0*Math.PI;	  
		  packData.kData[P].flower[0]=N;
		  packData.kData[P].flower[1]=L;
		  packData.kData[P].flower[2]=B;
		  packData.kData[P].flower[3]=R;
		  packData.kData[P].flower[4]=N;
		  packData.kData[P].color=CPScreen.getBGColor();
		  
		  T=N; // N is the new 'top' of interstice
		  N=P;
		  packData.setCombinatorics();
		  packData.set_aim_default();
		  hvList.append("h");
	}

	/** 
	 * Insert new vertex P in flower of v before (flag=1) or after 
	 * (flag=-1) N 
	 */
	public int brooks_insert(PackData p,int P,int N,int v,int flag) {
	  int num;
	  int []newflower;
	  boolean done=false;

	  num=packData.kData[v].num;
	  newflower=new int[num+2];
	  for (int i=0;i<=num;i++)
	    newflower[i]=packData.kData[v].flower[i];
	  if (flag==1) { // insert P before N 
	      for (int i=0;(i<=num) && !done;i++) {
		  if (packData.kData[v].flower[i]==N) {
		      for (int j=i;j<=num;j++)
			newflower[j+1]=packData.kData[v].flower[j];
		      newflower[i]=P;
		      done=true;
		    }
		}
	      if (!done) {
		  throw new CombException(); // error: should have found M 
	      }
	  }
	  else { // insert P after N 
	      for (int i=0;(i<=num) && !done;i++) {
		  if (packData.kData[v].flower[i]==N) {
		      for (int j=i+1;j<=num;j++)
			newflower[j+1]=packData.kData[v].flower[j];
		      newflower[i+1]=P;
		      done=true;
		    }
		}
	      if (!done) {
		  throw new CombException(); // error: should have found M 
	      }
	  }
	  packData.kData[v].num++;
	  packData.kData[v].flower=newflower;
	  return 1;
	} 

	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
//		Vector<String> items=null;

		if (cmd.startsWith("HV") || cmd.startsWith("VH")) {
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
	}

}
