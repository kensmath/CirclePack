package ftnTheory;

import input.CPFileManager;

import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.StringUtil;

import complex.Complex;

import exceptions.ParserException;

/**
 * 'Percolation' is at least a start at experiments in this
 * topic. A first target is Cardy's Theorem, though the route
 * via a 'hexamania' type approach is something I still don't
 * understand.
 * 
 * In any case, the setting will be a simply connected complex K
 * on whose boundary we mark four segments, two blue and the
 * intervening two red. We investigate open percolation paths 
 * from blue to blue, and red to red. We keep track of the state 
 * of vertices via their 'color': blue=1 and blue=2 for the blue
 * bdry arc and its opposite, red=198 and red=199 for the other
 * two. We randomly choose unmarked (color=0) interior vertices,
 * do a random walk to see which state it encounters first, then
 * mark it accordingly as follows:
 * 
 * vertices that are "infected":
 *    color 1: path connected by 1's to the blue=1 edge
 *    color 2: path connected by 2's to the blue=2 edge  
 *    color 199: path connected by 199's to the red=199 edge
 *    color 198: path connected by 198's to the red=198 edge
 *    
 * vertices that are "marked" (but not yet infected):
 *    color 244: random walk first encounters one of blue arcs
 *    color 243: random walk first encounters one of red arcs
 *    
 * Suppose a vertex v is just marked. You look for a petal
 * p that is infected with the corresponding color (1, 2, 
 * 199, or 198). Then you recursively start with p to 
 * infect neighbors: if petal u has corresponding mark,
 * then u becomes infected with same color as p. (Thus, 
 * v gets infected, e.g.). 
 * 
 * You follow infection recursively, thus building up 
 * connected components of vertices infected with the same
 * color. If a newly infected vert has a petal which is 
 * infected by the 'opposite' mark, then 'completed' is set
 * to show that a connecting path has been completed. E.g.
 * if infected is just set to 1 and a petal is 2, then set
 * completed to 1 (and vice verse); while if infected is just
 * set to 198 and petal is 199, then set completed to 198.
 * vert   
 * 
 * @author kstephe2
 *
 */

public class Percolation extends PackExtender {
	
	Vector<NodeLink> bdryArcs;  // 4 boundary arcs: trying to connect 0 and 2 or 1 and 4
	double [][]conductances;    // from eucl packing; conductance[v][j], j=petal index
	boolean simpleWalk;			// use simple (versus tailored) random walks.
	Vector<Integer> openVerts;  // keep track of vertices not yet hit
	PetalTrans []petalTrans;	// petalTrans hold transition prob. info
	Random rand;				// random number generator
	int completed;			    // + or -, depending on which path completed
	int successCount;
	int failureCount;
	int experimentMode;         // experimental mode: 1 = percolation, 2 = mania
	
	public Percolation(PackData p) {
		super(p);
		extensionType="PERCOLATION";
		extensionAbbrev="PR";
		toolTip="'Percolation' is intended for experiments related to "+
			"the probabilistic topic of percolation.";
		registerXType();
		
		// convert packData to euclidean, record its maximal packing
		try {
			cpCommand(packData,"geom_to_e");
			running=true;
		} catch (Exception ex) {
			errorMsg("Percolation: error in geom_to_e");
			running=false;
		}
		// yes, seems okay
		if (running) {
			packData.packExtensions.add(this);
		}

		// initialize colors
		for (int v=1;v<=packData.nodeCount;v++) {
			packData.kData[v].color=CPScreen.getFGColor();
		}
		
		bdryArcs=null;
		completed=0;
		openVerts=null;
		
//		rand=new Random(1); // with seed for debugging
		rand=new Random();
		
		// Create storage area for petal probabilities
		petalTrans=new PetalTrans[packData.nodeCount+1];
		successCount=failureCount=0;
		
		// modes:
		//   1=percolation (random vert, random choice of color, p=1/2)
		//   0=mania (random vert, random walk to encounter color)
		
		experimentMode=1;

		simpleWalk=false;
		prepConductances();
	}
	
	/**
	 * Process commands routed to 'PR'
	 */
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		// Run a string of experiments to collect data
		if (cmd.startsWith("RUN")) {
			simpleWalk=false;
			
			// get filename, remove from flagSegs
			if (!StringUtil.ckTrailingFileName(flagSegs))
				Oops("missing the file name");
			StringBuilder strbuf=new StringBuilder("");
			int code=CPFileManager.trailingFile(flagSegs, strbuf);
			File file=new File(strbuf.toString());
			boolean append=false;
			if ((code & 02) == 02) // append
				append=true;
			BufferedWriter fp=CPFileManager.openWriteFP((File)CPFileManager.PackingDirectory,append,
					file.getName(),false);
			
			// get other info
			NodeLink verts3=null;
			int N=100;
			try {
				while (flagSegs.size()>0) {
					items=flagSegs.remove(0);
					String str=items.remove(0);
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						switch(c) {
						case 'c': // give 3 corners
						{
							verts3=new NodeLink(packData,items);
							break;
						}
						case 'N': // number of iterations
						{
							N=Integer.parseInt(items.get(0));
							break;
						}
						case 's': // set simple walk
						{
							simpleWalk=true;
							break;
						}
						} // end of switch
					} // end of flags
				} // end of while
			} catch (Exception ex) {
				throw new ParserException("problem with 'RUN': "+ex.getMessage());
			}
			
			// set up the transition probabilities
			prepConductances();
			
			// right number of vertices?
			if (verts3.size()<3)
				Oops("don't have enough corner verts");
			if (verts3.size()>3)
				verts3=new NodeLink(packData,new String(verts3.get(0)+" "+verts3.get(1)+" "+verts3.get(2)));
			
			// run the trials
			StringBuilder strBuild=runBigData(packData,N,verts3);
			
			try {
				fp.append(strBuild.toString());
				fp.close();
			} catch (Exception iox) {
				return 0;
			}
			
			return 1;
		}
		
		else if (cmd.startsWith("set_mode")) {
			int k=1;
			try {
				items=flagSegs.remove(0);
				k=Integer.parseInt(items.get(0));
				if (k==0)
					experimentMode=0;
				return 1;
			} catch (Exception ex) {}
			experimentMode=1;
			return 1;
		}
		
		// iterative experiment
		else if (cmd.startsWith("exp")) {
			simpleWalk=false;
			String str=null;
			NodeLink corners=null;
			int N=100;
			try {
				while (flagSegs.size()>0) {
					items=flagSegs.remove(0);
					str=items.remove(0);
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						switch(c) {
						case 'c': // give corners
						{
							corners=getCornerX(StringUtil.reconItem(items));
							setCorners(corners);
							break;
						}
						case 'N': // number of iterations
						{
							N=Integer.parseInt(items.get(0));
							break;
						}
						case 's': // set simple walk
						{
							simpleWalk=true;
							break;
						}
						} // end of switch
					} // end of flags
				} // end of while
			} catch (Exception ex) {
				throw new ParserException("problem with 'experiment': "+ex.getMessage());
			}
			
			double successRate=experiment(N);
			
			msg("exp "+N+": "+successCount+" successes; "+failureCount+" failures: Success rate = "
					+successRate);

			return N;
		}
		
		if (cmd.startsWith("reset")) {
			successCount=failureCount=0;
			return 1;
		}
		
		if (cmd.startsWith("report")) {
			msg("Successes = "+successCount+": failures = "+failureCount);
			return successCount+failureCount;
		}

		if (cmd.startsWith("mania")) {
			simpleWalk=false;
			completed=0;
			for (int v=1;v<=packData.nodeCount;v++) {
				if (packData.kData[v].bdryFlag==0)
					packData.kData[v].color=CPScreen.getFGColor();
			}
			
			// see if corners are specified
			try {
				items=flagSegs.remove(0);
				NodeLink nlink=getCornerX(StringUtil.reconItem(items));
				setCorners(nlink);
			} catch(Exception ex) { }

			// are corners/arcs set?
			if (bdryArcs==null || bdryArcs.size()!=4)
				Oops("Must 'set_corners' first");

			// set up conductances, probabilities
			prepConductances();
			
			// set up vector of interiors
			NodeLink intV=new NodeLink(packData,"i");
			openVerts=new Vector<Integer>(intV.size());
			Iterator<Integer> iV=intV.iterator();
			while (iV.hasNext())
				openVerts.add(iV.next());
			
			// calculate conductances
			conductances=random.Conductance.setConductances(packData);
			
			// set petalTrans
			setPetalTrans();
			
			msg("everything is ready to go");
			return 1;
		}
		
		if (cmd.startsWith("go")) { // single pass
			int nextv=0;

			// see if a vertex is given
			try {
				items=flagSegs.remove(0);
				nextv=Integer.parseInt(items.remove(0));
			} catch (Exception ex) {}
			
			int ans=go(nextv);
//			System.out.println("go gave "+ans);
			if (completed>100)
				successCount++;
			if (completed>0 && completed<100)
				failureCount++;
			return ans;
		}
		
		if (cmd.startsWith("Go")) { // continue until done
			boolean debug=false;
			int count=openVerts.size();
			while (completed==0 && count>0) {
				count--;
				int ans=go(0);
				if (ans<0) {
					System.err.println("go failed");
					count=0;
				}
				if (debug)
					System.err.println("go for "+ans);
			}
			if (completed==0) {
				Oops("didn't get expected completion");
			}
			
			if (completed>100)
				successCount++;
			else if (completed>0 && completed<100)
				failureCount++;
			return completed;
		}

		// else default to superclass
		return super.cmdParser(cmd,flagSegs);
	}
	
	public NodeLink getCornerX(String str) {
		str=str.trim();
		NodeLink nlink=null;
		String []split=str.split(" ");
		String last=split[split.length-1];
		double x=.5;
		if (last.contains(".")) {
			try {
			x=Double.parseDouble(last);
			if (x<=0.0 || x>=1.0)
				return null;
			StringBuilder sbd=new StringBuilder();
			for (int j=0;j<(split.length-1);j++) {
				sbd.append(split[j]);
				sbd.append(" ");
			}
			nlink=new NodeLink(packData,sbd.toString());
			if (nlink==null || nlink.size()!=3)
				return null;
			Complex leftEnd=packData.getCenter(nlink.get(2));
			Complex rightEnd=packData.getCenter(nlink.get(0));
			Complex spot=leftEnd.times(1.0-x).add(rightEnd.times(x));
			NodeLink closest=new NodeLink(packData,"-c "+spot.x+" "+spot.y+" b");
			if (closest==null || closest.size()==0)
				return null;
			int v=closest.get(0);
			nlink.add(v);
			return nlink;
			} catch(Exception ex) {
				return null;
			}
		}
		return new NodeLink(packData,str);
	}
	
	/**
	 * Carry out a sequence of percolation experiments, N for each 
	 * vertex along the bottom edge (between last two verts3 entries),
	 * and prepare the data for writing.
	 * @param p 
	 * @param N number of runs for each vertex
	 * @param verts3 3 vertices, 4th will be between the last and first
	 * @return StringBuilder, Data, ready to write to a file
	 */
	public StringBuilder runBigData(PackData p,int N,NodeLink verts3) {
		
		int count=0;
		
		// two ends of the bottom edge
		int left=verts3.get(2);
		int right=verts3.get(0);
		Complex leftZ=p.getCenter(left);
		Complex rightZ=p.getCenter(right);
		double bottomLength=rightZ.minus(leftZ).abs();
		
		// linked list along bottom; need to toss first 2 and last 2
		NodeLink spots=new NodeLink(p,"b("+left+" "+right+")");
		spots.remove(0);
		spots.remove(0);
		spots.remove(spots.size()-1);
		spots.remove(spots.size()-1);
		
		// start data in matlab form: vector 'data', 'vertCount' rows, each with
		//    entry x,s, where x is barycentric coord of point along edge, s is
		//    proportion of successes (connections between blue).
		StringBuilder outputData=new StringBuilder("%% CirclePack, percolation trials: nodecount "+
				p.nodeCount+"\n\n"+
				"data = [\n");
		
		Iterator<Integer>  spts=spots.iterator();
		while (spts.hasNext()) {
			int v=spts.next();
			double x=p.getCenter(v).minus(leftZ).abs()/bottomLength;
			NodeLink corners=verts3.makeCopy();
			corners.add(v);
			setCorners(corners);
			if (experiment(N)==0) {
				outputData.append("]\n%% experiment "+(count+1)+" failed\n");
				return outputData;
			}
			double result=(double)successCount/(double)(successCount+failureCount);
			outputData.append(x+"  "+result+";\n");
			count++;
		}
		
		outputData.append("]\n");
		return outputData;
	}
	
	/**
	 * set conductances and petalTrans; should not change unless we
	 * do a repack
	 * @param true, use simple (versus tailored) random walk
	 */
	public void prepConductances() {
		if (simpleWalk)
			conductances=random.Conductance.setSimpleConductances(packData);
		else conductances=random.Conductance.setConductances(packData);
		setPetalTrans();
	}
	
	/**
	 * Carry out experiments
	 * @param corns
	 * @param N
	 * @return
	 */
	public double experiment(int N) {
		int count=0;
		successCount=failureCount=0;
//		int []corners=new int[4];
//		for (int i=0;i<4;i++)
//			corners[i]=corns.get(i);
//		if (setCorners(corners)==0)
//			Oops("failed to set corners");
		NodeLink intV=new NodeLink(packData,"i");
		int intCount=intV.size();
		

		// run trials
		boolean stop=false;
		while (count<N && !stop) {
			completed=0;
			for (int v=1;v<=packData.nodeCount;v++) {
				if (packData.kData[v].bdryFlag==0) 
					packData.kData[v].color=CPScreen.getFGColor();
			}
			openVerts=new Vector<Integer>(intV.size());
			Iterator<Integer> iV=intV.iterator();
			while (iV.hasNext())
				openVerts.add(iV.next());
			
			int locCount=intCount;
			boolean debug=false;
			while (completed==0 && locCount>0) {
				locCount--;
				int ans=go(0);
				if (debug)
					cpCommand("disp -wr -nc "+ans);
				if (ans<0) {
					System.err.println("go failed");
					count=0;
					stop=true;
				}
			}
			if (completed<=0) {
				Oops("Didn't complete as expected on pass "+count);
			}
				
			if (completed>100)
				failureCount++;
			else 
				successCount++;
			count++;
		}
		if (stop) 
			msg("'exp' stopped at count "+count);
		return (double)(successCount)/(double)(successCount+failureCount);
	}
	
	/**
	 * go with a random walk
	 * @param nextv if 0, choose random open vert
	 * @return chosen initial vert; 0 when no further verts available
	 */
	public int go(int nextv) {
		try {
			int N = 0;
			if (openVerts != null)
				N = openVerts.size();
			if (nextv == 0) {
				if (N == 0)
					return 0;
				if (N == 1) {
					nextv = openVerts.remove(0);
					openVerts = null;
				}
			}

			if (nextv == 0) { // get random vert from those remaining open
				nextv = openVerts.remove(rand.nextInt(openVerts.size()));
			}

			if (nextv == 0) {
				Oops("some problem getting nextv");
			}

			// now have a vertex, choose its color
			int ans=0;
			if (experimentMode==0) {
				ans = runWalker(nextv);
				if (ans == 0)
					Oops("walker " + nextv + " didn't succeed");
			}
			else { // if (experimentMode==1) {
				ans=243; // red
				if (rand.nextBoolean())
					ans=244; // blue
			}
			
			int myColor = ans;
			packData.kData[nextv].color=CPScreen.coLor(ans);

			// look to see if vert can be infected by a petal
			int infectedPetal = 0;
			for (int j = 0; (j < packData.kData[nextv].num && infectedPetal == 0); j++) {
				int k = packData.kData[nextv].flower[j];
				int m = CPScreen.col_to_table(packData.kData[k].color);
				// is this petal infected by same color
				if ((myColor == 244 && m > 0 && m < 100)
						|| (myColor == 243 && m > 100 && m < 200))
					infectedPetal = k;
			}

			if (infectedPetal != 0)
				spreadInfection(infectedPetal);
		} catch (Exception ex) {
		}
		return nextv;
	}

	/**
	 * Recursive routine: vert v is infected if color is between 0 and 200.
	 * If color < 100, it looks for neighboring petals with corresponding 
	 * marks 244 (light blue), while if it color > 100, it looks for 243
	 * (light red). If it finds such a neighbor, it recursively calls 
	 * 'spreadInfect'.
	 * @param v
	 * @return number of neighbors infected
	 */
	public int spreadInfection(int v) {
		if (areWeDone(v)!=0)
			return completed;
		int count=0;
		int m=CPScreen.col_to_table(packData.kData[v].color);
		int lookfor=0;
		if (m>0 && m <100)
			lookfor=244;
		else if (m>100 && m<200)
			lookfor=243;

		for (int j=0;(j<packData.kData[v].num && completed==0);j++) {
			int k=packData.kData[v].flower[j];
			if (packData.kData[k].color==CPScreen.coLor(lookfor)) {
				packData.kData[k].color=CPScreen.coLor(m);
				count +=spreadInfection(k);
			}
		}
		return count;
	}

	/** 
	 * Does this infected circle complete a path?
	 * @param v vertex which is marked 1, 2, 199, or 198
	 * @return 'completed' if yes; 0 if no.
	 */
	public int areWeDone(int v) {
		int mark=CPScreen.col_to_table(packData.kData[v].color);
		int opposite=0;
		switch (mark) {
		case 1: {opposite=2;break;}
		case 2: {opposite=1;break;}
		case 198: {opposite=199;break;}
		case 199: {opposite=198;break;}
		}
		for (int j=0;j<packData.kData[v].num;j++) {
			int k=packData.kData[v].flower[j];
			if (CPScreen.col_to_table(packData.kData[k].color)==opposite) {
				completed=mark;
				return completed;
			}
		}
		return 0;
	}
	
	/**
	 * Set corners based on NodeLink
	 * @param nlist
	 * @return last corner or 0 on error
	 */
	public int setCorners(NodeLink nlist) {
		if (nlist==null || nlist.size()!=4)
			return 0;
		int []corners=new int[4];
		corners[0]=nlist.get(0);
		corners[1]=nlist.get(1);
		corners[2]=nlist.get(2);
		corners[3]=nlist.get(3);
		return setCorners(corners);
	}

	/**
	 * Set marks on four boundary segments; each segment starts 
	 * with one of 'corners'. Set marks to 1, 199, 2, 198, in succession.
	 * The 1 and 2 are "blue" arcs, the 199 and 198 are "red" arcs.
	 * @return last corner on success, 0 on error
	 */
	public int setCorners(int []corners) {
		if (corners==null || corners.length!=4)
			return 0;
		bdryArcs=new Vector<NodeLink>(4);
		bdryArcs.add(new NodeLink(packData,"b("+corners[0]+","+corners[1]+")"));
		bdryArcs.add(new NodeLink(packData,"b("+corners[1]+","+corners[2]+")"));
		bdryArcs.add(new NodeLink(packData,"b("+corners[2]+","+corners[3]+")"));
		int v=packData.kData[corners[0]].flower[packData.kData[corners[0]].num];
		bdryArcs.add(new NodeLink(packData,"b("+corners[3]+","+v+")"));
		
		// mark the points on the various boundary arcs.
		Iterator<Integer> arc=bdryArcs.get(0).iterator();
		while (arc.hasNext()) {
			int w=arc.next();
			packData.kData[w].color=CPScreen.coLor(1);
		}
		arc=bdryArcs.get(1).iterator();
		while (arc.hasNext()) {
			int w=arc.next();
			packData.kData[w].color=CPScreen.coLor(199);
		}
		arc=bdryArcs.get(2).iterator();
		while (arc.hasNext()) {
			int w=arc.next();
			packData.kData[w].color=CPScreen.coLor(2);
		}
		arc=bdryArcs.get(3).iterator();
		while (arc.hasNext()) {
			int w=arc.next();
			packData.kData[w].color=CPScreen.coLor(198);
		}
		
		// set packing 'vlist'
		packData.vlist=new NodeLink(packData,
				new String(corners[0]+" "+corners[1]+" "+corners[2]+" "+corners[3]));
		return corners[3];
	}

	/**
	 * Start a random walker at v and go until it hits a marked
	 * vertex, then set v's mark accordingly. 
	 * @param v
	 * @return 0 on failure, 
	 */
	public int runWalker(int v) {
		int mySpot=v;

		// is initial vertex already pinned?
		if (packData.kData[mySpot].color!=CPScreen.FG_Color)
			return 0;
		
		// run the walk; inherit the mark of first hit
		while (packData.kData[mySpot].color==CPScreen.FG_Color) {
			double x=rand.nextDouble();
			mySpot=packData.kData[mySpot].flower[petalTrans[mySpot].whichPetal(x)];
		}
		int	hitColor=CPScreen.col_to_table(packData.kData[mySpot].color);
		
		// hit fresh vert? use same color
		if (hitColor>200) { 
			packData.kData[v].color=CPScreen.coLor(hitColor);
			return hitColor;
		}
			
		// else, determine what color to set
		int myColor=0;
		switch (hitColor) {
		case 1: {
			myColor=244;
			break;
		}
		case 2: {
			myColor=244;
			break;
		}
		case 199: {
			myColor=243;
			break;
		}
		case 198: {
			myColor=243;
			break;
		}
		} // end of switch

		packData.kData[v].color=CPScreen.coLor(myColor);
		return myColor;
	}
	
	/**
	 * Assume conductances are set, petalTrans are allocated; find the 
	 * transition probabilities and store in vector for easy random choice. 
	 * For vertex v with transition probabilities p_vj for petals j=1,2,...,n. 
	 * Store p_v1, p_v1+p_v2, ...,p_v1+...+p_vn. Get uniform random value x 
	 * in [0,1]; look through petals until you pass x, then backtrack 1. 
	 * @return
	 */
	public void setPetalTrans() {
		for (int v=1;v<=packData.nodeCount;v++) {
			int num=packData.kData[v].num+packData.kData[v].bdryFlag-1;
//System.out.println(" vert "+v+" num "+num);			
			petalTrans[v]=new PetalTrans(num);

			double totalCond=0.0;
			for (int j=0;j<=num;j++) {
				totalCond += conductances[v][j];
			}
			
			double accum=0.0;
			for (int j=0;j<num;j++) {
				accum+=conductances[v][j]/totalCond;
				petalTrans[v].petals[j]=accum;
			}
				
		}
		
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("reset",null,null,"reset success/failure counters"));
		cmdStruct.add(new CmdStruct("report",null,null,"report success/failure counts"));
		cmdStruct.add(new CmdStruct("mania","[{v..}]",null,"Prepare for run using given vertices for corners"));
		cmdStruct.add(new CmdStruct("go","[v]",null,"Do a random walk from a random un-set vert (or from v, if given)"));
		cmdStruct.add(new CmdStruct("Go",null,null,"Do a random walks from random un-set vertices until all are done"));
		cmdStruct.add(new CmdStruct("exp","-c {v..} -N {n}",null,"Run N trials using given corners"));
		cmdStruct.add(new CmdStruct("RUN","-c {v..} -N {n} -s -f {filename.m}",null,"N trials each for "+
				"vertices between corner[2] and corner[0], results to Matlab file. -s flag for simple "+
				"(versus tailored) random walk"));
		cmdStruct.add(new CmdStruct("color",null,null,"Color circles identified with 1/2 and -1/-2 blue and red," +
				"respectively and redraw the packing with these colors."));
		cmdStruct.add(new CmdStruct("set_mode","k",null,"Random choice mode: 0 = mania; 1 = percolation (default)"));
	}
	
	/**
	 * Class containing accumulated transition probabilities;
	 * 'num' is one less than the number of edges, since don't have
	 * to check the last one --- it is chosen by default.
	 * @author kstephe2
	 *
	 */
	class PetalTrans {
		int num;
		double []petals;
		
		public PetalTrans(int n) {
			num=n;
			petals=new double[n];
		}

		/**
		 * Which petal does random number indicate?
		 * @param x random in [0,1]
		 * @return petal index
		 */
		public int whichPetal(double x) {
			int j=0;
			while (j<num && x>petals[j])
				j++;
			return j;
		}
	}
}
