package ftnTheory;

import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import dcel.HalfEdge;
import dcel.RawDCEL;
import exceptions.CombException;
import exceptions.DataException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.FlipBot;
import util.StringUtil;

/**
 * Developing edge flip strategies in various settings.
 * 
 * (e.g., algorithm to flip edges of triangulation of
 * the sphere until there are two poles and all others
 * are pearls on the boundary developed by David Zach
 * and Richard Gustavson in 2009 REU program.)
 * 
 * @author kstephe2
 */
public class FlipStrategy extends PackExtender {
	
	Vector<FlipBot> flipBots;

	int northPole;
	int southPole;
	int NStoggle;
	Random rand;
	HalfEdge baseEdge;
	
	// Constructor
	public FlipStrategy(PackData p) {
		super(p);
		rand = new Random(); // random; DEBUG: add seed for debugging help
		extensionType="FLIPSTRATEGY";
		extensionAbbrev="FS";
		toolTip="'FlipStrategy' is for trying automatic "+
			"edge flip choice strategies";
		registerXType();
		if (packData.nodeCount<5) 
			throw new DataException("packing must have >= 5 vertices");
		if (running) {
			packData.packExtensions.add(this);
		}	

		// for flipbot use
		flipBots=new Vector<FlipBot>(1);
		
		// following if for 'flip algorithm' use
		northPole=packData.nodeCount;
		// check if northPole is tangent to all the others
		if (packData.countFaces(northPole)==packData.nodeCount-1) {
			for (int i=1;i<packData.nodeCount;i++)
				if (packData.countFaces(i)==packData.nodeCount-1)
					northPole=i;
			if (northPole==packData.nodeCount)
				throw new DataException("can't find north pole candidate");
			else { // nodeCount index didn't work; swap one that does with it
				if (cpCommand("swap M "+northPole)!=0)
					northPole=packData.nodeCount;
				else
					throw new CombException("failed to swap M for "+northPole);
			}
		}
		southPole=0;
		for (int i=1;(i<packData.nodeCount && southPole==0);i++) {
			if (packData.nghb(i,northPole)<0) 
				southPole=i;
		}
		if (southPole==0) 
			throw new DataException("can't find pole candidates");
		cpCommand("alpha "+southPole); // make southPole the alpha vert
		NStoggle=0;
		packData.vlist=null;
		packData.vlist=new NodeLink(packData);
		packData.vlist.add(northPole); // use 'max_pack -r vlist[0]' 
		packData.vlist.add(southPole);
		baseEdge=null;
	}
	
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// ===================== setedge ================
		if (cmd.startsWith("setedg")) {
			HalfEdge baseedge=HalfLink.grab_one_edge(packData, flagSegs);
			if (baseedge==null)
				return 0;
			baseEdge=baseedge;
			return 1;
		}
		
		// ===================== aflip =================
		// Given HalfEdge <v.w>, do two things: (1) advance in half-hex
		// direction (pass two edges on left) for new 'baseEdge' and 
		// (2) flip the next clockwise edge, if possible. return value is
		// 0 if neither is possible, -1 if only advance is possible, and 1 if
		// both are possible.
		else if (cmd.startsWith("aflip")) {
			if (baseEdge==null)
				return 0;
			
			HalfEdge[] ans=RawDCEL.flipAdvance_raw(packData.packDCEL,baseEdge);
			if (ans==null)
				return 0;
			baseEdge=ans[0]; // the new hedge
			if (ans[1]!=null) { // there was a flip
				packData.packDCEL.fixDCEL_raw(packData);
				return 1;
			}
			else // only advanced
				return -1;
		}
		
		// ======================= bot ==================
		// 'bot' command requires {name}; related to creating/controlling
		//       individual flipbots.
		else if (cmd.startsWith("bot")) {
			FlipBot flipBot;
			
			// name always first
			items=flagSegs.remove(0);
			String name=items.remove(0);
			if (StringUtil.isFlag(name)) { 
				Oops("'FlipBot' command needs {name} first");
			}
			
			// existing flipbot?
			flipBot=getNamedBot(name);
			
			// or do we create?
			if (flipBot==null) {
				items=flagSegs.remove(0);
				if (items.get(0).equals("-c")) { // create new one
					flipBot=new FlipBot(packData,name);
					flipBot.setColor(flipBots.size()); // set color based on index in vector
					flipBots.add(flipBot);
				}
			}
			
			if (flipBot==null)
				return 0;
			
			// flipBot exists, process remaining flags
			while (flagSegs.size()>0 && (items=flagSegs.remove(0))!=null) {
				String flag=items.remove(0);
				if (!StringUtil.isFlag(flag)) {
					Oops("'FlipStrategy' command missing a flag");
				}
				try {
					char c=flag.charAt(1);
					switch(c) {
					case 'f': { // set flipStrategy
						flipBot.setFlipStrategy(items.get(0));
						break;
					}
					case 'm': { // set moveStrategy
						flipBot.setMoveStrategy(items.get(0));
						break;
					}
					case 'v': { // set vertex
						int v=Integer.parseInt(items.get(0));
						flipBot.setHomeVert(v);
						
						// do we need to set 'previousHome'?
						int pre=flipBot.getPrevious();
						if (pre<=0 || packData.nghb(flipBot.getHomeVert(),pre)<0) {
							flipBot.setPrevious(packData.getLastPetal(v));
						}
						
						break;
					}
					case 'e': { // setedge (previous, home)
						EdgeSimple edge=EdgeLink.grab_one_edge(packData,StringUtil.reconItem(items));
						flipBot.setHomeVert(edge.w);
						flipBot.setPrevious(edge.v);
						break;
					}
					case 'd': { // draw current edge (previousHome,homeVert)
						int colindx=ColorUtil.col_to_table(flipBot.getColor());
						cpCommand(packData,"disp -ec"+colindx+"t8 "+flipBot.getPrevious()+" "+flipBot.getHomeVert()+" -cc"+colindx+"t8 "+flipBot.getHomeVert());
						break;
					}
					case 'l': { // draw the last edge flipped
						int colindx=ColorUtil.col_to_table(flipBot.getColor());
						EdgeSimple edge=flipBot.getLastFlipped();
						if (edge!=null)
							cpCommand(packData,"disp -ec"+colindx+"t4 "+edge.v+" "+edge.w);
						break;
					}
					case 't': { // tick of the clock: i.e. go
						StringBuilder mg=new StringBuilder(flipBot.getName()+" at "+flipBot.getHomeVert()+": ");
						
						// ============= choose and try the flip
						flipBot.setOtherEnd(0);
						EdgeSimple edge=flipBot.chooseFlip();
						EdgeSimple outEdge=null;
						
						if (edge!=null) {
							flipBot.setOtherEnd(edge.w);
							int rslt=0; 
							// TODO: should be more efficient way
							int[] flower=packData.getFlower(edge.v);
							int lv=flower[packData.nghb(edge.v,edge.w)+1];
							flower=packData.getFlower(edge.w);
							int rv=flower[packData.nghb(edge.w,edge.v)+1];
							outEdge=new EdgeSimple(lv,rv);
							rslt=cpCommand("flip "+edge.v+" "+edge.w);
							if (rslt!=0) { //
								flipBot.setLastFlipped(new EdgeSimple(lv,rv));
								rslt=cpCommand("max_pack");
								rslt=cpCommand("color -c d");
								// update all the flipbots
								Iterator<FlipBot> fbt=flipBots.iterator();
								while (fbt.hasNext()) 
									fbt.next().update();
							
								mg.append("flipped edge with "+edge.w+"; ");
							}
							else
								this.msg("chosen edge flip ("+edge.v+" "+edge.w+") failed");
						}
						
						// ============= now choose the move
						int oldVert=flipBot.getHomeVert();
						
// debug						
//System.out.println(outEdge.toString());

						int newVert=flipBot.chooseMove(flipBot.getHomeVert(),flipBot.getPrevious(),outEdge);
						
						// do the move
						if (newVert>0) {
							flipBot.setHomeVert(newVert);
							flipBot.setPrevious(oldVert);
							mg.append(flipBot.getName()+" now at v="+flipBot.getHomeVert());
						}
						msg(mg.toString());
						break;
					}
					case 's': { // mark the homeVert
						cpCommand("mark -c -cw "+flipBot.getHomeVert()); // clear marks, set mark 
						break;
					}
					
					} // end of switch
				} catch(Exception ex) {
					Oops("bot: processing problem: "+ex.getMessage());
				}
			}
			
			return 1;
		}
		
		// ======================= setNS =========================
		if (cmd.startsWith("setNS")) {
			items=flagSegs.get(0);
			NodeLink vlist=new NodeLink(packData,items);
			int n=1;
			int s=packData.nodeCount;
			if (vlist==null || vlist.size()<2 || (n=vlist.get(0))==(s=vlist.get(1))
					|| packData.nghb(n,s)>=0)
				Oops("supposed to give distinct non-neighbors n and s");
			northPole=n;
			southPole=s;
			return 1;
		}
		
		// ========================= randF ================
		// execute number of random flips to randomize the packing
		if (cmd.startsWith("randF")) {
			items=flagSegs.get(0);
			int N=Integer.parseInt((String)items.get(0));
			N=Math.abs(N);
			int count=0;
			for (int i=1;i<=N;i++)
				count+=this.cpCommand("flip -r");
			msg("Did "+count+" edge flips");
			return count;
		}
		
		// ========================= status =============
		// degree, utility function 
		if (cmd.startsWith("status")) {
			
			
			int Ndegs=0;
			int Nfours=0;
			int[] petals=packData.getPetals(northPole);
			for (int j=0;j<petals.length;j++) {
				int k=petals[j];
				int knum=packData.countFaces(k);
				Ndegs+=knum;
				if (knum==4) Nfours++;
			}
			int Sdegs=0;
			int Sfours=0;
			petals=packData.getPetals(southPole);
			for (int j=0;j<packData.countFaces(southPole);j++) {
				int k=petals[j];
				int knum=packData.countFaces(k);
				Sdegs+=knum;
				if (knum==4) Sfours++;
			}
			
			int notPolish=0;
			int twicePolish=0;
			int twoPoleCount=0;
			for (int v=1;v<=packData.nodeCount;v++) {
				if (v!=northPole && v!=southPole) {
					if (packData.nghb(v,northPole)<0 &&
							packData.nghb(v,southPole)<0)
						notPolish++; // doesn't nghb either pole
					else if (packData.nghb(v,northPole)>=0
							&& packData.nghb(v,southPole)>=0) {
						twicePolish++; // nghbs both poles
						twoPoleCount +=packData.countFaces(v);
					}
				}
			}
			msg("\nStatus: N=v"+northPole+", degree "+
					packData.countFaces(northPole)+"; S=v"+southPole+
					", degree "+packData.countFaces(southPole)+"\n"+
					"   Total petal degrees, N/S: "+Ndegs+"/"+Sdegs+"\n"+
					"   Degree 4 neighbors, N/S: "+Nfours+"/"+Sfours+"\n"+
					"   Number neighboring neither/both poles: "+notPolish+"/"+twicePolish+"\n"+
					"   Total deg of verts nghb'ing both poles: "+twoPoleCount);
			return 1;
		}
		
		// =========================== doFlip ============
		// Attempt N random flips (default to N=1) of edges 
		//   between petals of a pole
		if (cmd.startsWith("doFlip") || cmd.startsWith("doN") ||
				cmd.startsWith("doS")) {
			
			// are we already done?
			if (packData.countFaces(northPole)==(packData.nodeCount-2) &&
					packData.countFaces(southPole)==(packData.nodeCount-2)) {
				msg("Finished: the combinatorics are in final form");
				return 1;
			}
			
			// default, one flip
			int N=1;
			
			// but check if the command is followed by a number
			try {
				items=flagSegs.get(0);
				N=Math.abs(Integer.parseInt((String)items.get(0)));
			} catch (Exception ex) {}
			
			// try N times, (or until both poles have nodeCount-2 petals)
			int flipCount=0;
			for (int i=1;(i<=N && (packData.countFaces(northPole)!=(packData.nodeCount-2) ||
					packData.countFaces(southPole)!=(packData.nodeCount-2)));i++) {
				
//System.out.println("i ="+i);		
				int pole=northPole;
				int unpole=southPole;
				if (cmd.startsWith("doS")) {
					pole=southPole;
					unpole=northPole;
				}

				//	if "doFlips", toggle north/south (even)/(odd)
				if (cmd.startsWith("doFli") && 2*(NStoggle/2)!=NStoggle) {
					pole=southPole;
					unpole=northPole;
				}
				
				// start with random petal of pole
				int num=packData.countFaces(pole);
				int j=rand.nextInt(num);
				
				// I'm setting a saftey counter, so this 'while'
				//    statement can't go on forever
				int count=num+1;
				
				// go around petals: skip degree <= 3 and
				//   degree 4's tangent to unpole
				int v=0;
				int jjnum=0;
				int[] flower=packData.getFlower(pole);
				while (count>0 && ((jjnum=packData.countFaces((v=flower[j])))<4 ||
						(jjnum==4 && packData.nghb(v,unpole)>=0))) {
					j=(j+1)%num;
					count--;
				}

				// found one? 
				v=0;
				if (count!=0) {
					v=flower[j];
//					System.out.println("v = "+v);
				}
				
				// process while we can keep flipping about v (interior)
				boolean outerflip=true;
				int vnum;
				flower=packData.getFlower(v);
				while (outerflip && v!=0 && 
						(vnum=packData.countFaces(v))>4 &&
						!packData.isBdry(v)) {
					msg("target petal: v="+v+", pole="+pole);
					outerflip=false;
					int k=(packData.nghb(v,pole)-1+vnum)%vnum;
					int w=flower[k];
					int m=(k-1+vnum)%vnum;
					int nextw=flower[m];
					// try flipping first edge, if it doesn't connect poles
					if (nextw!=unpole && cpCommand("flip "+v+" "+w)!=0) {
						msg("  first flip succeeded: <"+v+" "+w+">");
						outerflip=true;
					}

					
					// else, search clockwise around v for flippable
					//   edge whose other end is petal of pole and
					//   whose co-neighbors are not both petals of pole.
					int tick=vnum+1;
					while (!outerflip && tick>0) {
						k=(k-1+vnum)%vnum;
						w=flower[k];
						int cnl=flower[(k+1)%vnum];
						int cnr=flower[(k-1+vnum)%vnum];
//						m=(k-1+vnum)%vnum;
//						nextw=flower[m];
						if (w!=unpole && packData.nghb(w,pole)>=0 &&
								(cnl!=pole || cnl!=unpole || cnr!=pole || cnr!=unpole) && 
								(packData.nghb(cnl,pole)<0 || packData.nghb(cnr,pole)<0) &&
								cpCommand("flip "+v+" "+w)!=0) {
							msg("  another flip succeeded: <"+v+" "+w+">");
							outerflip=true;
						}
						tick--;
					} // end of while
					
					// if it worked, fix up combinatorics
					if (outerflip) {
						// these operations should have been done in 'flip' call
//						packData.setCombinatorics(); 
//						packData.fillcurves();
						flipCount++;
					}
					
					// Want to STOP looking at this v after one flip?????
					outerflip=false;
					
				} // end of while

				// increment toggle so next attempt is with other pole
				NStoggle++;

			} // end of 'for' loop

			// report in Message window
			if (packData.countFaces(northPole)==(packData.nodeCount-2) &&
					packData.countFaces(southPole)==(packData.nodeCount-2)) {
						if (flipCount>0)
							msg("Did "+flipCount+" edge flips");
						msg("Finished: the combinatorics are in final form");
						return 1;
			}
			
			if (flipCount==0) {
				errorMsg("no flips succeeded");
				return 0;
			}
			else {
				msg("Did "+flipCount+" edge flips");
				msg("Northpole has degree "+packData.countFaces(northPole)+
						"; SouthPole, degree "+packData.countFaces(southPole));
				return flipCount;
			}
		} // end of 'doFlip'
		
		// ==================== NS_to_v =============================
		// put N and S in vlist so they are available
		if (cmd.startsWith("NS_to_v")) {
			return cpCommand("set_vlist "+northPole+" "+southPole);
		}
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Search for named 'FlipBot'
	 * @param botName
	 * @return FlipBot, null if not found
	 */
	public FlipBot getNamedBot(String botName) {
		Iterator<FlipBot> fbit=flipBots.iterator();
		while (fbit.hasNext()) {
			FlipBot fbot=fbit.next();
			if (fbot.getName().equalsIgnoreCase(botName))
				return fbot;
		}
		return null;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("setedge","v w",null,"Set directed edge as 'base'"));
		cmdStruct.add(new CmdStruct("aflip",null,null,"auto flip: project baseEdge "+
		"one half hex step, then flip clw edge"));
		cmdStruct.add(new CmdStruct("setNS","n s",null,"Set north/south poles"));
		cmdStruct.add(new CmdStruct("randFlips","N",null,"Do N random edge flips"));
		cmdStruct.add(new CmdStruct("status",null,null,"show status of combinatorics, degree, etc."));
		cmdStruct.add(new CmdStruct("doFlips (doN, doS)","N",null,"Do N flips toward poles"));
		cmdStruct.add(new CmdStruct("NS_to_vlist",null,null,
				"clear vlist, put poles in it so you can work with them"));
		cmdStruct.add(new CmdStruct("bot","<name> [-c] -f {str} -m {str} -v {v} -e {v,w} -d -t ",
				null,"Set flipbot properties: -c for create, then other flags: "+
				"flip/move strategies f/m, -v vert, -e edge, -d {prev,home} edge, -l last edge "+
						"flipped, -t 'do it'"));

//		cmdStruct.add(new CmdStruct(""))
	}
}
