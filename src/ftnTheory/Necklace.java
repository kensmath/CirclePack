package ftnTheory;

import java.awt.Color;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackCreation;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.ColorUtil;
import util.StringUtil;

/**
 * Pack extender to experiment with Scott Sheffield 'necklace'
 * construction. Working with Joan Lind to investigate the
 * random triangulation construction as described by Steffen
 * Rohde and James Gill.
 * 
 * NOTE: also, started to include some stuff on question of Rick
 * Kenyon (while at Oberwolfach): Randomly triangulate an n-gon,
 * but only using new edges between existing vertices. Do this
 * for another n-gon, then randomly paste them to form a sphere.
 * What is the average combinatorial diameter of the result.
 * I'll have to barycentrically subdivide the triangles to get
 * any interiors and to avoid sharing two edges when pasting the 
 * two halves.
 *  
 * Note: main work carrier out in 'PackCreation.randNecklace'
 * @author kens
 */
public class Necklace extends PackExtender {
	
	PackData topPack;		// to hold a necklace packing
	PackData bottomPack;     // to hold a necklace packing
	int buildN;   // default size for each of top/bottom

	public Necklace(PackData p) {
		super(p);
		packData=p;
		extensionType="NECKLACE";
		extensionAbbrev="NK";
		toolTip="'Necklace' provides for creation of necklace-type random packings";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}
		buildN=10;
	}
	
	/**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// ========================== buildT (or B) =======================
		if (cmd.startsWith("build")) {
			int mode=2;
			int faceCount=buildN; 
			boolean top=true; // put result in 'topPack' or 'bottomPack'?
			if (cmd.charAt(5)=='B' || cmd.charAt(5)=='b')
				top=false; 
			if (flagSegs!=null && flagSegs.size()>0) {
				Iterator<Vector<String>> flgs=flagSegs.iterator();
				while (flgs.hasNext()) {
					items=flgs.next();
					String str=items.remove(0);
					  if (StringUtil.isFlag(str)) {
						  switch (str.charAt(1)) {
						  case 'm': // mode flag
						  {
							  try {
								  mode=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("usage: -m <m>");
							  }
							  break;						  
						  }
						  case 'N': // number of faces
							  try {
								  faceCount=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("usage: -N <n>");
							  }
							  break;						  
						  } // end of switch
					  }
					  else {
						  try {
							  faceCount=Integer.parseInt(str);
						  } catch (Exception ex) {
							  throw new ParserException("usage: <n>");
						  }
					  }
				} // end of while for flags
				
			}

			Integer debugSeed=null;
			// note: put in Integer if you want a seed for debugging
			if (top) {
				topPack=PackCreation.randNecklace(faceCount,mode,debugSeed);
				if (topPack==null)
					Oops("'topPack' failed.");
				else
					msg("'topPack' has "+topPack.nodeCount+" vertices, "+
							"real axis from "+topPack.util_B+" to "+
							topPack.util_A);
			}
			else {
				bottomPack=PackCreation.randNecklace(faceCount,mode,debugSeed);
				if (bottomPack==null)
					Oops("'bottomPack' failed.");
				else
					msg("'bottomPack' has "+bottomPack.nodeCount+" vertices, "+
							"real axis from "+bottomPack.util_B+" to "+
							bottomPack.util_A);
			}
			return 1;
		}
		
		// ================================== meld ==============================
		else if (cmd.startsWith("meld")) {
//			topPack=CirclePack.cpb.pack[1].packData;
//			bottomPack=CirclePack.cpb.pack[2].packData;
			
			boolean max_it=true;
			
			// check for flags: currently, only one is saying no to 'max_pack' call.
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				String str=items.get(0);
				
				// -no_max
				if (str.startsWith("-n"))
					max_it=false;
			}
			
			if (topPack==null || bottomPack==null)
				Oops("'topPack' or 'bottomPack' is null");
			
			// center vert of face #1 edge in axis (3 or 5)
			int topOrigin=topPack.gamma;
			int bottomOrigin=bottomPack.gamma;
			
			// check util_A, util_B
			if (!topPack.isBdry(topPack.util_A) ||
					!topPack.isBdry(topPack.util_A))
				Oops("Problem with 'topPack.util_A' or 'util_B'");
			if (!bottomPack.isBdry(bottomPack.util_A) ||
					!bottomPack.isBdry(bottomPack.util_A))
				Oops("Problem with 'bottomPack.util_A' or 'util_B'");
			
			// get NodeLinks for topBlue, topRed, bottomBlue, bottomRed
			NodeLink topBlue=new NodeLink(topPack,"b("+topPack.util_B+" "+topOrigin+")");
			topBlue=topBlue.reverseMe();
			NodeLink bottomBlue=new NodeLink(bottomPack,"b("+bottomPack.util_B+" "+bottomOrigin+")");
			bottomBlue=bottomBlue.reverseMe();
			NodeLink topRed=new NodeLink(topPack,"b("+topOrigin+" "+
					topPack.util_A+")");
			NodeLink bottomRed=new NodeLink(bottomPack,"b("+bottomOrigin+" "+
					bottomPack.util_A+")");
			int tleft=topBlue.size();
			int bleft=bottomRed.size();
			int tright=topRed.size();
			int bright=bottomBlue.size();
			
			// what vertices to use in adjoin?
			int vtop=topPack.util_A;
			int vbottom=bottomPack.util_B;
			if (tright>bright) { // bottom is shorter
				vtop=topRed.get(bright-1);
			}
			else if (tright<bright) {
				vbottom=bottomBlue.get(tright-1);
			}
			
			// how many edges to paste?
			int length=tright-1;
			if (tright>bright)
				length=bright-1;
			int leftlength=tleft-1;
			int topleftend=topPack.util_B;
			if (bleft<tleft) {
				leftlength=bleft-1;
				topleftend=topBlue.get(leftlength);
			}
			length +=leftlength;

			// save some information
			// the part of boundary that will be pasted
			NodeLink pasteVerts=new NodeLink(topPack,"b("+topleftend+" "+vtop+")");
			CPBase.Elink=new EdgeLink(topPack,"b("+topleftend+" "+vtop+")");

			// ------------------ now, do the adjoin 
			int rslt=topPack.adjoin(bottomPack,vtop,vbottom,length);
			if (rslt<=0)
				Oops("problem with adjoin");
			
			// save the resulting packing as the parent packing
			CPScreen cpS=packData.cpScreen;
			cpS.swapPackData(topPack,false);
			packData=cpS.getPackData();
			
			// do some fixup
			packData.setCombinatorics();
			packData.setAlpha(topOrigin);
			packData.vlist=pasteVerts;
			
			// by default, result is max_pack'ed
			if (max_it)
				cpCommand("max_pack");
			
			// transfer colors, marks
			for (int v=1;v<=bottomPack.nodeCount;v++) {
				int newv=packData.vertexMap.findW(v);
				Color col=bottomPack.getCircleColor(v);
				packData.setCircleColor(newv,new Color(col.getRed(),col.getGreen(),col.getBlue()));
				packData.setVertMark(newv,bottomPack.getVertMark(v));
			}

			// create 'elist' to hold edges not connected to
	 		//  face center vertices --- i.e. the graph edges
	 		packData.elist=new EdgeLink(packData);
	 		for (int v=1;v<=packData.nodeCount;v++) {
	 			if (packData.getVertMark(v)!=1)
	 				for (int j=0;j<packData.getNum(v)+packData.getBdryFlag(v);j++) {
	 					int k=packData.kData[v].flower[j];
	 					if (k>v && packData.getVertMark(k)!=1)
	 						packData.elist.add(new EdgeSimple(v,k));
	 			}
	 		}			
			
			cpCommand("disp -w -c -et0 elist -et4c209 Elist");
			
			return 1;
		}
		
		// ========== copy <pnum> 
		if (cmd.startsWith("copy")) { // copy 'outputData' in some pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS!=null) {
					if (cmd.length()==4 || cmd.charAt(4)=='B' || cmd.charAt(4)=='b')
						cpS.swapPackData(bottomPack,false);
					else
						cpS.swapPackData(topPack,false);
				}
			} catch (Exception ex) {
				return 0;
			}
			return 1;
		}	
		
		else if (cmd.startsWith("set_size")) {
			int N=0;
			try {
				items=(Vector<String>)flagSegs.get(0);
				N=Integer.parseInt((String)items.get(0));
			} catch (Exception ex) {
				throw new ParserException("usage: set_size N");
			}
			if (N>0)
				buildN=N;
			return N;
		}
		
		// ========================= save ========================
		else if (cmd.startsWith("save")) {
			CPScreen cpS=packData.cpScreen;
			if (cpS==null) 
				Oops("problem saving");
			if (cmd.charAt(4)=='B' || cmd.charAt(4)=='b') {
				cpS.swapPackData(bottomPack,false);
				packData=cpS.getPackData();
			}
			else {
				cpS.swapPackData(topPack,false);
				packData=cpS.getPackData();
			}
			return 1;
		}
		
		// =========================
		else if (cmd.startsWith("randRick")) {
			int N=0;
			try {
				items=(Vector<String>)flagSegs.get(0);
				N=Integer.parseInt((String)items.get(0));
			} catch (Exception ex) {
				throw new ParserException("usage: set_size N");
			}
			if (N<3) 
				N=3;
			
			// build the two hemispheres
System.err.println("starting topHemi:");			
			PackData topHemi=randn2Tri(N,null);
System.err.println("starting bottomHemi:");			
			PackData bottomHemi=randn2Tri(N,null);
			
			// have to randomize how these are pasted; each has 2 on bdry
			Random rand=new Random();
			int k=2*rand.nextInt(7);
			int b=2;
			for (int j=0;j<k;j++)
				b=bottomHemi.kData[b].flower[0];
			
			topHemi.adjoin(bottomHemi,2,b,N*2);
			topHemi.setCombinatorics();
			cpCommand(topHemi,"max_pack");
			
			// save the resulting packing as the parent packing
			CPScreen cpS=packData.cpScreen;
			cpS.swapPackData(topHemi,false);
			packData=cpS.getPackData();
			
			return packData.nodeCount;
		}

		return 0;
	}

	
	/**
	 * This is intended as recursive constructor. Given 'list' of
	 * indices (starting as 1....n), choose a random edge and 
	 * random opposite vert from 'list', add a single labeled
	 * triangle, break list into p and q pieces, p+q=n-1, each of size at 
	 * least 1, spawn two child processes with p and q until p=q=1 
	 * in each branch, attach their triangles to vector, return
	 * when done.
	 * @param n
	 * @param list int[]; null on top level call
	 * @return Vector<Vector<Integer>> 
	 */
	public PackData randn2Tri(int n,int []list) {
		if (list==null) System.err.println("Null list enter.");
		
		if (n<3 || (list!=null && list.length!=n)) 
			throw new DataException("list not length n");
		if (list==null) {
			list=new int[n];
			for (int j=0;j<n;j++)
				list[j]=j+1;
		}
		
		int []mylist=new int[n];
		for (int k=0;k<n;k++)
			mylist[k]=list[k];
		
		// This is the packing we are growing.
		PackData myPacking=PackCreation.seed(6,-1);
		myPacking.setVertMark(1,-1);
		myPacking.setVertMark(3,-2);
		myPacking.setVertMark(5,-2);
		myPacking.setVertMark(7,-2);
		myPacking.setCircleColor(1,ColorUtil.coLor(190));
		myPacking.setCircleColor(2,ColorUtil.coLor(10));
		myPacking.setCircleColor(4,ColorUtil.coLor(10));
		myPacking.setCircleColor(6,ColorUtil.coLor(10));
		myPacking.setCircleColor(3,ColorUtil.coLor(100));
		myPacking.setCircleColor(5,ColorUtil.coLor(100));
		myPacking.setCircleColor(7,ColorUtil.coLor(100));

		if (n==3) {
			// start single face
			myPacking.vertexMap=new VertexMap();
			myPacking.vertexMap.add(new EdgeSimple(2,mylist[0]));
			myPacking.vertexMap.add(new EdgeSimple(4,mylist[1]));
			myPacking.vertexMap.add(new EdgeSimple(6,mylist[2]));
			return myPacking;
		}
		
		Random rand=new Random();
		
		// first, find random bdry edge
		int j=rand.nextInt(n);
		int v=mylist[j];
		int w=mylist[(j+1)%n];
		
		// next, random vert in remainder of list, k in 1,...,n-2
		int k=rand.nextInt(n-2)+1;
		int u=mylist[(k+j+1)%n];
		

		StringBuilder listbuilder=new StringBuilder(" "+list[0]+" ");
		for (int li=1;li<n;li++)
			listbuilder.append(list[li]+" ");
		System.err.println("Enter: n="+n+"; list="+listbuilder.toString());
		System.err.println("v,w,u = "+v+" "+w+" "+u);		
		
		// start single face
		myPacking.vertexMap=new VertexMap();
		myPacking.vertexMap.add(new EdgeSimple(2,v));
		myPacking.vertexMap.add(new EdgeSimple(4,w));
		myPacking.vertexMap.add(new EdgeSimple(6,u));
		
		// build side packing for the recursion
		// Two pieces: length k+2 and n-k
		PackData rightPack=null;
		int []rightList=new int[k+1]; // [n-k];
		if (k>1) {//(k<(n-2)) {
			for (int jj=0;jj<k+1;jj++) // n-k;jj++)
				rightList[jj]=mylist[(j+1+jj)%n];
			rightPack=randn2Tri(k+1,rightList);
		}
		
		PackData leftPack=null; 
		int []leftList=new int[n-k]; // k+1];
		if (k<(n-2)) {
			for (int jj=0;jj<(n-k);jj++)
				leftList[jj]=mylist[(j+1+k+jj)%n];
			leftPack=randn2Tri(n-k,leftList);
		}
		
		if (rightPack==null && leftPack==null) {
			throw new CombException("Problem: recursion got now left or right packing");
		}
		
		// now put together for the result
		VertexMap holdIndices=new VertexMap();
		
		// attach right to myPacking along (u,v); find bdry with mark==u
		if (rightPack!=null) {
			NodeLink bdry=new NodeLink(rightPack,"b");
			Iterator<Integer> bit=bdry.iterator();
			int rightIndx=-1;
			while (bit.hasNext() && rightIndx<0) {
				int bn=bit.next();
				if (rightPack.vertexMap.findW(bn)==u)
					rightIndx=bn;
			}
System.err.println("adjoining right");			
			myPacking.adjoin(rightPack,2,rightIndx,2);
			
			// save original index info
			Iterator<EdgeSimple> vM=rightPack.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				// (j,edge.w) in child pack, (j,newVert) in myPacking, then
				//    put (newVert,edge.w) in holdIndices
				int newVert=0;
				if ((newVert=myPacking.vertexMap.findW(edge.v))!=0) 
					holdIndices.add(new EdgeSimple(newVert,edge.w));
			}
		}
		
		// attach left to myPacking along (v,u); find bdry with mark==v
		if (leftPack!=null) {
			NodeLink bdry=new NodeLink(leftPack,"b");
			Iterator<Integer> bit=bdry.iterator();
			int leftIndx=-1;
			while (bit.hasNext() && leftIndx<0) {
				int bn=bit.next();
				if (leftPack.vertexMap.findW(bn)==v)
					leftIndx=bn;
			}
			System.err.println("adjoining left");			

			myPacking.adjoin(leftPack,6,leftIndx,2);

			// save original index infor
			Iterator<EdgeSimple> vM=leftPack.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				// (j,edge.w) in child pack, (j,newVert) in myPacking, then
				//    put (newVert,edge.w) in holdIndices
				int newVert=0;
				if ((newVert=myPacking.vertexMap.findW(edge.v))!=0) 
					holdIndices.add(new EdgeSimple(newVert,edge.w));
			}
		}
		
		myPacking.vertexMap=holdIndices;
		return myPacking;
		
	}
	

		
	/**
	 * help info
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("buildT","[-m {m} -N] {n}",null,
		"fill 'topPack' with necklace construction, mode m, count n"));
		cmdStruct.add(new CmdStruct("buildB","[-m {m} -N] {n}",null,
		"fill 'bottomPack' with necklace construction, mode m, count n"));
		cmdStruct.add(new CmdStruct("meld","[-n]",null,"meld top/bottom packings along axis, "+
				"put in the parent. Flag '-n' suppresses the 'max_pack' call"));
		cmdStruct.add(new CmdStruct("saveT",null,null,"store 'topPack' in parent packing"));
		cmdStruct.add(new CmdStruct("saveB",null,null,"store 'bottomPack' in parent packing"));
		cmdStruct.add(new CmdStruct("copy{BT}","{pnum}",null,
		"write 'topPack' or 'bottomPack' into designated packing"));
		cmdStruct.add(new CmdStruct("set_size","{n}",null,"set default build size N"));
		cmdStruct.add(new CmdStruct("randRick","{n}",null,"build random sphere as suggested by Kenyon on n-gon"));
	}
}