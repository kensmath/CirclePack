package ftnTheory;

import java.awt.Color;
import java.util.Vector;

import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import dcel.RawManip;
import exceptions.DataException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;

public class iGame extends PackExtender {
	public int []corner;
	public double GameAspect; // width/height of display screen 
	public int []targets;     // target vertices
	public int playerCount;   // 
	public int []player;      // 
	public Color []playerCOlor;
	public int gameMode;
	public String []gameStr={"EAT"};
	public int currentPlayer; // index into 'player'

	// Constructor
	public iGame(PackData p) {
		super(p);
		extenderPD=p;
		extensionType="IGAME";
		extensionAbbrev="IG";
		toolTip="'iGame' for developing iPhone games ";
		registerXType();
		int rslt;
		try {
			rslt=cpCommand(extenderPD,"geom_to_e");
		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			CirclePack.cpb.errMsg("CA: failed to convert to euclidean");
			running=false;
		}
		if (running) {
			GameAspect=.75;  //  9.0/16.0; 
			extenderPD.packExtensions.add(this);
		}
		player=null;
		playerCOlor=null;
		targets=null;
	}
	
	/** 
	 * Set up the data for default mode
	 */
	public void initMode() {
		playerCount=1;
		player=new int[1];
		player[0]=randNonPlayer(-1); // random interior
		currentPlayer=0;
		playerCOlor=new Color[1];
		playerCOlor[0]=ColorUtil.spreadColor(0);
		targets=new int[1];
		targets[0]=randNonPlayer(0);
		gameMode=0; // default is eat
	}
	
	/**
	 * Assume vertical normalization already applied, so
	 * bottom left is corner[0]. Scale height so 
	 * width/height = GameAspect and translate to center
	 * on the origin.
	 */
	public void set4Aspect() {
		// get 'factor' for multiplying imaginary part
		double wide=(extenderPD.getCenter(corner[1])).minus(extenderPD.getCenter(corner[0])).abs();
		double high=(extenderPD.getCenter(corner[2])).minus(extenderPD.getCenter(corner[1])).abs();
		double factor=wide/(high*GameAspect);
		
		// find average of 4 corner locations
		Complex c0=extenderPD.getCenter(corner[0]);
		Complex c1=extenderPD.getCenter(corner[1]);
		Complex c2=extenderPD.getCenter(corner[2]);
		Complex c3=extenderPD.getCenter(corner[3]);
		Complex mid=c0.add(c1).add(c2).add(c3);
		mid=mid.times(.25);
		
		// adjust all centers
		for (int v=1;v<=extenderPD.nodeCount;v++) {
			Complex z=extenderPD.getCenter(v).minus(mid);
			z.y*=factor;
			extenderPD.setCenter(v,z);
		}

	}

	/**
	 * Carry out actions to repack and layout with current corners.
	 * @return 1
	 */
	public int fixUp() {
		cpCommand("set_aim 1.0 b");
		cpCommand("set_aim .5 "+corner[0]+" "+corner[1]+" "+corner[2]+" "+corner[3]);
		cpCommand("repack");
		cpCommand("layout");
		cpCommand(new String("norm_scale -h "+corner[0]+" "+corner[1]));
		set4Aspect(); // normalize
		cpCommand(new String("norm_scale -u "+corner[0]));
		return 1;
	}
	
	/**
	 * Choose a random interior vertex: distinct from player 
	 * if playerNum>=0.
	 * @return player index or -1 on failure
	 */
	public int randNonPlayer(int playerNum) {
		int safety=5000;
		while (safety>0) {
			int v=(int)(1+Math.random()*extenderPD.nodeCount+.4);
			if (v>extenderPD.nodeCount) v=extenderPD.nodeCount;
			if (!extenderPD.isBdry(v) && 
					(playerNum<0 || v!=player[playerNum])) 
				return v;
			safety--;
		}
		return -1;
	}
	
	/**
	 * If player shares edge with target, player eats target,
	 * that is, we collapse the edge. The new vertex has the
	 * smaller index, we remove larger index, so have to update 
	 * various data.
	 * @param playerNum
	 * @param targetNum
	 * @return -1 on error
	 */
	public int eatTarget(int playerNum,int targetNum) {
		int p=player[playerNum];
		int t=targets[targetNum];
		int oldV=t;
		if (p>t) 
			oldV=p;
		if (extenderPD.nghb(p,t)>=0) {
			HalfEdge edge=extenderPD.packDCEL.findHalfEdge(p,t);
			int rslt=RawManip.meldEdge_raw(extenderPD.packDCEL,edge);
			if (rslt<=0) 
				return -1;
			extenderPD.packDCEL.fixDCEL(extenderPD);
			extenderPD.fillcurves();
			
			// which vertex survives?
			int fused=t; 
			if (rslt==t)
				fused=p;
			player[playerNum]=fused;
			for (int i=0;i<playerCount;i++) {
				if (i!=playerNum && player[i]>oldV) player[i]--;
				if (targets[i]>oldV) targets[i]--;
			}
			for (int i=0;i<4;i++)
				if (corner[i]>oldV) corner[i]--;
			Color col=playerCOlor[playerNum];
			extenderPD.setCircleColor(player[playerNum],new Color(col.getRed(),col.getGreen(),col.getBlue()));
			return fused;
		}
		return -1;
	}
	
	/**
	 * Modify the complex so edge <v,u> is collapsed.
	 * @param v int
	 * @param u int
	 * @return int, 0 on error
	 */
	public int crunchEdge(int v,int u) {
		HalfEdge edge=extenderPD.packDCEL.findHalfEdge(v,u);
		if (edge==null || 
			(extenderPD.isBdry(v) && extenderPD.isBdry(u)))
			return 0;
		int rslt=RawManip.meldEdge_raw(extenderPD.packDCEL,edge);
		if (rslt!=0)
			extenderPD.packDCEL.fixDCEL(extenderPD);
		return rslt;
	}
	
	/**
	 * Redisplay the screen depending on mode. 'guy' is the
	 * index of the player, or if guy=-1, use currentPlayer.
	 * @return -1 on error
	 */
	public int display(int guy) {
		int thePlayer=currentPlayer;
		if (guy>=0 && guy<playerCount) thePlayer=guy;
		
		// show the graph
		cpCommand("disp -w -f");
		
		switch(gameMode) {
		default: { // eat mode
			
			// show player (small circle)
			int v=player[thePlayer];
			cpCommand("disp -cf "+v);
			
			// show the target
			int t=targets[thePlayer];
			cpCommand("disp -cffg "+t);
			
			return v;

		}
		}
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		if (cmd.startsWith("help")) {
			helpInfo();
			return 1;
		}
		
		// ========= init =========
		if (cmd.startsWith("init")) {
			initMode();
			return 1;
		}
		
		// ========= corners =========
		if (cmd.startsWith("corn")) {
			try {
				items=flagSegs.get(0);
				corner=new int[4];
				for (int j=0;j<4;j++) 
					corner[j]=Integer.parseInt((String)items.get(j));
			} catch (Exception ex) {
				Oops("didn't get corners: "+ex.getMessage());
			}
			msg("corners = "+corner[0]+","+corner[1]+","+corner[2]+","+corner[3]);
			return 1;
		}
		
		// ========= fixUp ===========
		if (cmd.startsWith("fix")) {
			return fixUp();
		}
		
		// ======== randomize =========
		if (cmd.startsWith("rand")) {
			int n=20;
			try {
				items=flagSegs.get(0);
				n=Integer.parseInt((String)items.get(0));
			} catch (Exception ex) { }
			for (int j=0;j<n;j++)
				cpCommand(new String("flip -r "+j));
			return n;
		}
		
		// ======== rmTri ========
		if (cmd.startsWith("rmT")) {
			int v=0;
			try {
				v=NodeLink.grab_one_vert(extenderPD,flagSegs);
				if (v==0 || extenderPD.isBdry(v) || extenderPD.getVertMark(v)>=0)
					throw new ParserException("vertex is one of designated");
			} catch (Exception ex) {
				Oops("not valid for removal: "+ex.getMessage());
			}
			if (v<=0 || v>extenderPD.nodeCount || !extenderPD.isBdry(v) ||
					extenderPD.countFaces(v)!=3)
				Oops("invalid choice");
			cpCommand("rm_cir "+v);
			return 1;
		}
		
		// ======== addTri ========
		if (cmd.startsWith("addT")) {
			int f=0;
			try {
				f=FaceLink.grab_one_face(extenderPD,flagSegs);
			} catch (Exception ex) {}
			if (f>0)
				cpCommand("add_bary "+f);
			return 1;
		}
		
		// ========= move corner ======
		if (cmd.startsWith("move")) {
			int v=0;
			try {
				v=NodeLink.grab_one_vert(extenderPD,flagSegs);
			} catch (Exception ex) {
				Oops("need index of bdry vert next to a corner");
			}
			if (v<=0 || v>extenderPD.nodeCount || !extenderPD.isBdry(v))
				Oops("invalid choice");
			boolean gotit=false;
			for (int j=0;(!gotit && j<4);j++) {
				if (extenderPD.nghb(v,corner[j])>=0) {
					corner[j]=v;
					gotit=true;
				}
			}
			return fixUp();
		}
		
		// ======== set_game ==========
		if (cmd.startsWith("set_game")) {
			try {
				items=flagSegs.get(0);
				gameMode=Integer.parseInt((String)items.get(0));
			} catch (Exception ex) {
				gameMode=0;
				Oops("didn't get valid game mode: "+ex.getMessage());
			}
			return gameMode;
		}
		
		// ======== eat ===========
		if (cmd.startsWith("eat")) {
			try {
				items=flagSegs.get(0);
				EdgeSimple edge=EdgeLink.grab_one_edge(extenderPD,flagSegs);
				if (edge==null)
					throw new DataException("");
			} catch (Exception ex) {
				Oops("didn't get valid edge: "+ex.getMessage());
			}
			return eatTarget(0,0);
		}
		
		// ======== jump ==========
		if (cmd.startsWith("jump")) {
			EdgeSimple edge=null;
			try {
				items=flagSegs.get(0);
				edge=EdgeLink.grab_one_edge(extenderPD,flagSegs);
				if (edge==null)
					throw new DataException("");
			} catch (Exception ex) {
				Oops("didn't get valid edge: "+ex.getMessage());
			}
			if (edge.v==player[currentPlayer])
				player[currentPlayer]=edge.w;
			else if (edge.w==player[currentPlayer])
				player[currentPlayer]=edge.v;
			Color col=playerCOlor[currentPlayer];
			extenderPD.setCircleColor(player[currentPlayer],new Color(col.getRed(),col.getGreen(),col.getBlue()));
			return 1;
		}
		
		// ======== set_player =======
		if (cmd.startsWith("set_play")) {
			try{
				items=flagSegs.get(0);
				int cp=Integer.parseInt((String)items.get(0));
				if (cp<0 && cp>=playerCount) {
					currentPlayer=cp;
					return cp;
				}
				return -1;
			} catch (Exception ex) {
				Oops("illegal player number: "+ex.getMessage());
			}
			return -1;
		}
		
		// ======== players ========
		if (cmd.startsWith("play")) {
			for (int vv=1;vv<=extenderPD.nodeCount;vv++)
				extenderPD.setVertMark(vv,-1);
			try {
				items=flagSegs.get(0);
				playerCount=items.size();
				if (playerCount>5) playerCount=5;
				player=new int[playerCount];
				playerCOlor=new Color[playerCount];
				targets=new int[playerCount];
				for (int j=0;j<playerCount;j++) {
					player[j]=Integer.parseInt((String)items.get(j));
					playerCOlor[j]=ColorUtil.spreadColor(j%16);
					targets[j]=randNonPlayer(player[j]);
					if (player[j]<=0 || player[j]>=extenderPD.nodeCount || 
							extenderPD.isBdry(player[j])) {
						player=null;
						playerCOlor=null;
						targets=null;
						Oops("improper player specified");
					}
					extenderPD.setVertMark(player[j],j);
				}
			} catch (Exception ex) {
				player=null;
				playerCOlor=null;
				targets=null;
				Oops("problem in specifying 'player'");
			}
			return playerCount;
		}
		
		// ======== display =========
		if (cmd.startsWith("disp")) {
			return display(-1);
		}
		
		return 0;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("init",null,null,"initialize to default 'eat' mode"));
		cmdStruct.add(new CmdStruct("corners","v1 v2 v3 v4",null,"vertices at corners, v1,v2=bottom, v2,v3=right"));
		cmdStruct.add(new CmdStruct("set_game","<m>",null,"game mode: 0 = eat "));
		cmdStruct.add(new CmdStruct("fix",null,null,"repack, layout, normalize"));
		cmdStruct.add(new CmdStruct("rand","<n>",null,"do n random flips"));
		cmdStruct.add(new CmdStruct("move","<v>",null,"make v (next to a corner) a corner"));
		cmdStruct.add(new CmdStruct("disp",null,null,"display the graph, color vertices and marked spots"));
		cmdStruct.add(new CmdStruct("players","<v..>",null,"designate up to 5 interior vertices as players"));
		cmdStruct.add(new CmdStruct("addTri","<f>",null,"add a trivalent vertex in face f"));
		cmdStruct.add(new CmdStruct("set_player","<n>",null,"activate player n"));
		cmdStruct.add(new CmdStruct("eat","<u,w>",null,"flip edge, eat target (if neighbors), choose new target"));
		cmdStruct.add(new CmdStruct("jump","<u,w>",null,"if player is one end of edge, then switch ends"));
		cmdStruct.add(new CmdStruct("rmTri","<v>",null,"remove v if it is interior, trivalent, not designated"));
	}
	
	
	public void helpInfo() {
		super.helpInfo();
		helpMsg("Current mode is: "+gameStr[gameMode]);
	}

}
