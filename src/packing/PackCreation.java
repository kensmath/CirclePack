package packing;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import allMains.CirclePack;
import dcel.CombDCEL;
import dcel.DcelCreation;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.NodeLink;
import tiling.TileData;
import util.ColorUtil;

/**
 * traditional: now see 'DcelCreation.java' 
 * 
 * For creation of packings from scratch, such as seeds, 
 * Cayley graphs of triangle groups {a b c}, tilings, etc.
 * 
 * 
 * TODO: add creation calls for soccerball? perhaps other tiling patterns?
 * 
 * @author kens
 *
 */
public class PackCreation {

	/** 
	 * Implementation of Scott Scheffield's 'necklace' construction
	 * for random planar triangulations (at least as described by Gill
	 * and Rohde).
	 * 
	 * Unfortunately, these triangulations will often have multiple
	 * edges. Therefore, we make each face a hex flower, i.e., a
	 * barycentrically subdivided face, so we get legal triangulations
	 * at each step. 
	 * 
	 * Each new hex face has 1 as barycenter 2,4,6 as vertices, and
	 * 3, 5, 7 as edge barycenters. (These, of course, are renumbered
	 * as the construction goes on.) 7 will play a special role.  
	 * 
	 * Reinterpreted: the "active" edge is always the edge which
	 * contained the last "7" edge barycenter. In the Rohde/Gill
	 * terminology, "2" is the "blue" end, "6" is the "red" end.
	 * 
	 * At each stage we'll add a new face based on two coin flips:
	 *   rbCoin: true = reset 'red' end; false = reset 'blue' end.
	 *   newCoin: true = add new vertex; false = connect existing vertices
	 * Relative to original terminology, rbCoin corresponds to 'red'
	 * or 'blue' action (r/R or b/B); newCoin indicates whether to use
	 * the capital or lower case symbol.
	 *   
	 * Also must maintain 'blueVert' and 'redVert' designation,
	 * our version of the integers on the negative and positive, 
	 * resp., real axis in the original terminology.
	 * 
	 * We keep track of these things:
	 *   * Linked list giving the oriented boundary.
	 *   * First in bdry list is always the current 'S'. This
	 *       corresponds to the "7" of the last appended face.
	 *   * 'redVert' and 'blueVert' 
	 *   * list of face center vertices -- associate with face number
	 *   
	 * We have only few situations, indicated by {i,j,k}, for the
	 * adjoin operation of p1 (the growing complex) and p2 (the
	 * hex face):
	 *   * i: go i steps from current S (i=1 or 3)
	 *   * j: attach j vertex of new hex face (j=2 or 4)
	 *   * k: attach k edges (k=2 or 4)
	 * Also, when newCoin is false (hence adding to an 'old' vertex), 
	 *   * if S-1 = 'redVert' and coin flip specifies red,
	 *     then 'redVert' is set to new "6".
	 *   * if S+1 = 'blueVert' and coin flip specifies blue,
	 *     then 'blueVert' is set to new "2".
	 *   
	 * Outline: Assume flips give R/B (red/blue) and N/O (new/old).
	 *  For R-O or B-O, have to check S-1 (clockwise 'red', end of 
	 *  bdry list) or S+1 (counterclockwise 'blue') to see if they 
	 *  are the 'redVert', 'blueVert', respectively. In this case, 
	 *  instead treat as though vert is N (new) and we add new vertex 
	 *  to the list.
	 *  
	 *    R/B   N/O   in list?   {i,j,k}  add?
	 * ____________________________________________
	 *    R     N	             {1,2,2}    
	 *    R     O     S-1=y      {1,2,2}  add "6"
	 *    R	    O     S-1=n      {1,2,4}
	 *    B     N                {1,4,2}
	 *    B     O     S+1=y      {1,4,2}  add "2"
	 *    B     O     S-1=n      {3,2,4}
	 *    
	 * Set packing radii and aims to default.   
	 *    
	 * @param n, number of faces, n>=1.
	 * @param mode: =1 implies "one-end" construction, 
	 *            else (default), "two-end" construction
	 * @param randSeed, Integer. If null, no seed (true random).            
	 * @return PackData. Note: vertices 1-n are face centers; they
	 *  have mark 1 and color red. Vertices associated with nodes 
	 *  have mark 2 and color blue. Other verts are white.
	 *  Edges defining graph are in 'elist'. In mode==2 case, 
	 *  PackData.util_A/.util_B ints record the red/blue (resp) boundary
	 *  vertices. Counterclockwise bdry from blue to red should be the
	 *  "real axis" portion of the boundary in Rohde/Gill terminology.
	 *  'gamma' is set to vert in middle of bdry edge of face 1. 
	 */
	public static PackData randNecklace(int n,int mode,Integer randSeed) {
		
		// some defaults
		if (n<1)
			n=1;
		if (mode!=1)
			mode=2;
		
		// This is the packing we are growing.
		PackData myPacking=DcelCreation.seed(6,-1);
		myPacking.setVertMark(1,1);
		myPacking.setVertMark(2,2);
		myPacking.setVertMark(4,2);
		myPacking.setVertMark(6,2);
		myPacking.setCircleColor(1,ColorUtil.cloneMe(ColorUtil.coLor(190)));
		myPacking.setCircleColor(2,ColorUtil.cloneMe(ColorUtil.coLor(10)));
		myPacking.setCircleColor(4,ColorUtil.cloneMe(ColorUtil.coLor(10)));
		myPacking.setCircleColor(6,ColorUtil.cloneMe(ColorUtil.coLor(10)));
		myPacking.setCircleColor(3,ColorUtil.cloneMe(ColorUtil.coLor(100)));
		myPacking.setCircleColor(5,ColorUtil.cloneMe(ColorUtil.coLor(100)));
		myPacking.setCircleColor(7,ColorUtil.cloneMe(ColorUtil.coLor(100)));
		
		// new faces added by adjoining this hex face
		PackData hexFace=DcelCreation.seed(6,-1);
		
		// The oriented boundary of myPacking is held here:
		//    the first element is always the 'S' element
		LinkedList<Integer> bdryLink=new LinkedList<Integer>();
		bdryLink.add(7); // 'S' is always the 0th element
		for (int k=2;k<7;k++)
			bdryLink.add(k);
		
		Random rand;
		if (randSeed !=null) 
			rand=new Random(randSeed); // use seed for debugging
		else 
			rand=new Random(); // no seed for normal runs
		boolean newCoin=rand.nextBoolean();
		boolean redCoin=rand.nextBoolean();

		// Depending on 'mode', keep track of "blue" (mode 1) or
		//   "red/blue" (mode 2) vertices: these are the pos/neg 
		//   "integers" of Rohde/Gill's paper or negative integers
		//   in the one-end mode.
		int blueVert=0;
		int redVert=0;
				
		// ------------------ start -----------------------------
		// only thing to get started is coin flip for 'red' or 'blue',
		//    and all this effects is the start of 'rlist' 'blist'
		int gamma_indx=1;
		if (redCoin) {
			blueVert=2;
			redVert=4;
		}
		else {
			blueVert=4;
			redVert=6;
			gamma_indx=3;
		}
		
		// in one-end case, randomly choose which side of S
		if (mode==1) {
			if (rand.nextBoolean())
				blueVert=6;
			else 
				blueVert=2;
		}
		
		// ---------------- loop -----------------------------
		//     successively adding new faces 
		for (int f=2;f<=n;f++) {
			
			// randomize next action with 2 coin flips
			newCoin=rand.nextBoolean();
			redCoin=rand.nextBoolean();
			
			if (redCoin) { // adding to get new red end
				if (newCoin) {
					adjoinFace(myPacking,hexFace,1,2,2,bdryLink);
				}
				else {
					int redChk=bdryLink.get(bdryLink.size()-1);
					if (redChk==redVert || (mode==1 && redChk==blueVert)) {
						adjoinFace(myPacking,hexFace,1,2,2,bdryLink);
						redVert=myPacking.vertexMap.findW(6); // new vert
						if (mode==1)
							blueVert=redVert;
					}
					else {
						adjoinFace(myPacking,hexFace,1,2,4,bdryLink);
					}
				}
			}
			else { // adding to get new blue end
				if (newCoin) {
					adjoinFace(myPacking,hexFace,1,4,2,bdryLink);
				}
				else {
					int blueChk=bdryLink.get(1);
					if (blueChk==blueVert) {
						adjoinFace(myPacking,hexFace,1,4,2,bdryLink);
						blueVert=myPacking.vertexMap.findW(2); // new vert
						if (mode==1) // keep these in sync
							redVert=blueVert;
					}
					else {
						adjoinFace(myPacking,hexFace,3,2,4,bdryLink);
					}
				}
			}
			
			// mark center 1, red; nodes 2, blue
			myPacking.setVertMark(myPacking.vertexMap.findW(1),1);
			myPacking.setCircleColor(myPacking.vertexMap.findW(1),ColorUtil.cloneMe(ColorUtil.coLor(190))); // red
			myPacking.setVertMark(myPacking.vertexMap.findW(2),2);
			myPacking.setCircleColor(myPacking.vertexMap.findW(2),ColorUtil.cloneMe(ColorUtil.coLor(10))); // blue
			myPacking.setVertMark(myPacking.vertexMap.findW(4),2);
			myPacking.setCircleColor(myPacking.vertexMap.findW(4),ColorUtil.cloneMe(ColorUtil.coLor(10))); // blue
			myPacking.setVertMark(myPacking.vertexMap.findW(6),2);
			myPacking.setCircleColor(myPacking.vertexMap.findW(6),ColorUtil.cloneMe(ColorUtil.coLor(10))); // blue
			// others white
			myPacking.setCircleColor(myPacking.vertexMap.findW(3),ColorUtil.cloneMe(ColorUtil.coLor(100))); 
			myPacking.setCircleColor(myPacking.vertexMap.findW(5),ColorUtil.cloneMe(ColorUtil.coLor(100))); 
			myPacking.setCircleColor(myPacking.vertexMap.findW(7),ColorUtil.cloneMe(ColorUtil.coLor(100))); 

		} // end of 'for'

		// -------------- fix up the packing -----------------------
		
		// temporary alpha
		myPacking.alpha=1;
		
		// gamma points to middle vertex of bdry edge in first face
 		myPacking.gamma=myPacking.kData[1].flower[gamma_indx];
 		
 		// create 'elist' to hold edges not connected to
 		//  face center vertices --- i.e. the graph edges
 		myPacking.elist=new EdgeLink(myPacking);
 		for (int v=1;v<=myPacking.nodeCount;v++) {
 			if (myPacking.getVertMark(v)!=1) {
 				int[] petals=myPacking.getPetals(v);
 				for (int j=0;j<petals.length;j++) {
 					int k=petals[j];
 					if (k>v && myPacking.getVertMark(k)!=1)
 						myPacking.elist.add(new EdgeSimple(v,k));
 				}
 			}
 				
 		}

 		// record blue/red boundary vertices in util_B/util_A, resp.,
 		//   so calling routine can find them.
 		myPacking.util_A=redVert;
 		myPacking.util_B=blueVert;
 		
		// update; choose random face to center at origin
		myPacking.setCombinatorics();
		myPacking.setAlpha(rand.nextInt(n+1));
		
		// set default radii, aims, plot flags
		myPacking.set_rad_default();
		myPacking.set_aim_default();
		myPacking.set_plotFlags();
		
		return myPacking;
	}
	
	/**
	 * Specialty routine for 'randNecklace'. Attach hex face and
	 * adjust blink. Note that p1.vertexMap should have {old,new}
	 * pairs, old=index in p2, new=index in new p1.
	 * @param p1, growing packing
	 * @param p2, hex face
	 * @param i, shift in blist to find v1 (vertex of p1): 1 or 3
	 * @param v2, vertex of p2 (2 or 4)
	 * @param n, number of edges to adjoin (2 or 4)
	 * @param blink, linked list of boundary vertices, 'S' is first
	 * @return int, index (in new p1) of center of new face
	 */
	public static int adjoinFace(PackData p1,PackData p2,int i,int v2,int n,
			LinkedList<Integer> blink) {
		
		if ((i!=1 && i!=3) || (v2!=2 && v2!=4) || (n!=2 && n!=4)) {
			throw new ParserException("adjoinFace usage: i=1 or 3, v2=2 or 4, n=2 or 4");
		}
		
		int v1=blink.get(i);
		p1.packDCEL=CombDCEL.d_adjoin(p1.packDCEL,
				p2.packDCEL,v1,v2,n);
		p1.packDCEL.fixDCEL_raw(p1);
		
		int centerV=p1.vertexMap.findW(1);

		// adjust blink
		int S=p1.vertexMap.findW(7);
		blink.remove(0); // remove old 'S'
		if (n==2) { // don't remove anything else
			if (v2==2) {
				blink.add(0,S); // new 'S' at beginning
				// two new at end
				blink.add(p1.vertexMap.findW(5));
				blink.add(p1.vertexMap.findW(6));
			}
			else {
				blink.add(0,p1.vertexMap.findW(3));
				blink.add(0,p1.vertexMap.findW(2));
				blink.add(0,S); // new 'S' at beginning
			}
		}
		else { // n==4, remove 2 more, depending on direction
			if (i==1) { // remove last 2
				int last=blink.size()-1;
				blink.remove(last);
				blink.remove(last-1);
			}
			else { // i==3, remove first two
				blink.remove(0);
				blink.remove(0);
			}
			blink.add(0,S); // put new S at beginning
		}
		
		return centerV; 
	}

	public static PackData pentTiling(int N) {
		PackData pent=DcelCreation.seed(5,0);
		pent.swap_nodes(1,6);
		
		int sidelength=1;
		int count=1;
		
		while (count<N) {
			// expand 
			pent=adjoin5(pent,sidelength);
			sidelength *= 2;
			count++;
		}
		
		pent.alpha=6;
		pent.gamma=1;
		pent.setCombinatorics();
		pent.set_aim_default();
		for (int v=1;v<=pent.nodeCount;v++) {
			if (pent.isBdry(v))
				pent.setAim(v,Math.PI);
		}
		for (int v=1;v<=5;v++)
			pent.setAim(v,3.0*Math.PI/5.0);
		pent.repack_call(1000);

		try {
			pent.packDCEL.layoutPacking();
		} catch(Exception ex) {}
		
		double mod=pent.getCenter(2).abs();
		for (int v=1;v<=pent.nodeCount;v++) {
			pent.setCenter(v,pent.getCenter(v).divide(mod));
			pent.setRadius(v,pent.getRadius(v)/mod);
		}
		
		try {
			pent.tileData=TileData.paveMe(pent,pent.alpha);
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create pent 'TileData'");
		}

		return pent;
	}

	public static PackData pentHypTiling(int N) {
		PackData pentBase=DcelCreation.seed(5,0);
		pentBase.packDCEL.swapNodes(1,6);
		
		PackData heap=pentBase.copyPackTo();
		PackDCEL pdcel=heap.packDCEL;
		
		int generation=0;
		
		while (generation<N) {
			
			// expand 
			heap=doublePent(heap,generation);
			pdcel=heap.packDCEL;
			//	DebugHelp.debugPackWrite(heap,"doubleheap.p");
			
			pdcel=CombDCEL.d_adjoin(pdcel,pentBase.packDCEL,4,5,2);
			heap.vertexMap=pdcel.oldNew;
			int new4=heap.vertexMap.findW(4);
			int new3=heap.vertexMap.findW(3);
			pdcel.swapNodes(new3,3);
			pdcel.swapNodes(new4,4);
			pdcel.fixDCEL_raw(heap);
			generation++;
		}
		
		pdcel.setAlpha(6, null,false);
		pdcel.setGamma(1);
		heap.set_aim_default();
		for (int v=1;v<=heap.nodeCount;v++) {
			heap.setRadius(v,0.1);
			if (heap.isBdry(v))
				heap.setAim(v,Math.PI);
		}
		for (int v=2;v<=5;v++)
			heap.setAim(v,Math.PI/2.0);
		heap.repack_call(1000);

		try {
			heap.packDCEL.layoutPacking();
		} catch(Exception ex) {}
		
		double mod=heap.getCenter(3).abs();
		for (int v=1;v<=heap.nodeCount;v++) {
			heap.setCenter(v,heap.getCenter(v).divide(mod));
			heap.setRadius(v,heap.getRadius(v)/mod);
		}

		try {
			heap.tileData=TileData.paveMe(heap,6);
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create dyadic 'TileData'");
		}

		return heap;
	}

	/**
	 * Create N generations of pentagonal tiling meeting at triple
	 * point. 
	 * 
	 * TODO: need to run 'paveMe' for 'TileData', but don't know what
	 * vertex to use.
	 * @param N
	 * @return
	 */
	public static PackData pent3Expander(int N) {
		PackData pent=DcelCreation.seed(5,0);
		pent.swap_nodes(1,6);
		int sidelength=1;
		PackData triPent=adjoin3(pent,sidelength);
		int count=1;
		
		while (count<N) {
			
			// expand 
			pent=adjoin5(pent,sidelength);
			sidelength *= 2;
			
			// put together
			triPent=adjoin3(pent,sidelength);
			count++;
		}
		
		triPent.set_aim_default();
		for (int v=1;v<=triPent.nodeCount;v++) {
			if (triPent.isBdry(v))
				triPent.setAim(v,Math.PI);
		}
		triPent.setAim(2, 3.0*Math.PI/5.0);
		triPent.setAim(3, 3.0*Math.PI/5.0);
		triPent.setAim(5, 3.0*Math.PI/5.0);
		triPent.setAim(6, 3.0*Math.PI/5.0);
		triPent.setAim(8, 3.0*Math.PI/5.0);
		triPent.setAim(9, 3.0*Math.PI/5.0);
		triPent.setAim(1, 17.0*Math.PI/15.0);
		triPent.setAim(4, 17.0*Math.PI/15.0);
		triPent.setAim(7, 17.0*Math.PI/15.0);
		
		triPent.repack_call(1000);

		try {
			triPent.packDCEL.layoutPacking();  
		} catch(Exception ex) {}
		
		double mod=triPent.getCenter(2).abs();
		for (int v=1;v<=triPent.nodeCount;v++) {
			triPent.setCenter(v,triPent.getCenter(v).divide(mod));
			triPent.setRadius(v,triPent.getRadius(v)/mod);
		}
			
		return triPent;
	}
	
	/**
	 * Create N generations of pentagonal tiling meeting at quadruple point.
	 * 
	 * TODO: need to run 'paveMe' for 'TileData', but don't know what
	 * vertex to use.
	 * 
	 * @param N
	 * @return
	 */
	public static PackData pent4Expander(int N) {
		PackData pent=DcelCreation.seed(5,0);
		pent.swap_nodes(1,6);
		int sidelength=1;
		PackData quadPent=adjoin4(pent,sidelength);
		int count=1;
		
		while (count<N) {
			
			// expand 
			pent=adjoin5(pent,sidelength);
			sidelength *= 2;
			
			// put together
			quadPent=adjoin4(pent,sidelength);
			count++;
		}
		
		quadPent.set_aim_default();
		for (int v=1;v<=quadPent.nodeCount;v++) {
			if (quadPent.isBdry(v))
				quadPent.setAim(v,Math.PI);
		}
		for (int v=1;v<=8;v++)
			quadPent.setAim(v,0.75*Math.PI);
		
		quadPent.repack_call(1000);

		try {
			quadPent.packDCEL.layoutPacking();
		} catch(Exception ex) {}
		
		double mod=quadPent.getCenter(2).abs();
		for (int v=1;v<=quadPent.nodeCount;v++) {
			quadPent.setCenter(v,quadPent.getCenter(v).divide(mod));
			quadPent.setRadius(v,quadPent.getRadius(v)/mod);
		}
			
		return quadPent;
	}
	
	/**
	 * adjoin three pentagons. 
	 * @param p PackData, existing seed 5 with 1 on bdry
	 * @param sidelength
	 * @return
	 */
	public static PackData adjoin3(PackData p,int sidelength) {
		
		PackData triPent=p.copyPackTo();

		// adjoin 2
		triPent.packDCEL=CombDCEL.d_adjoin(triPent.packDCEL,
				p.packDCEL,1,1,sidelength);
		triPent.packDCEL.fixDCEL_raw(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		int newv=triPent.vertexMap.findW(3);
		triPent.swap_nodes(newv,7);
		newv=triPent.vertexMap.findW(4);
		triPent.swap_nodes(newv,8);
		newv=triPent.vertexMap.findW(5);
		triPent.swap_nodes(newv,9);
		
		// adjoin 3
		triPent.packDCEL=CombDCEL.d_adjoin(triPent.packDCEL,
				p.packDCEL,7,1,2*sidelength);
		triPent.packDCEL.fixDCEL_raw(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		int new5=triPent.vertexMap.findW(4);
		int new6=triPent.vertexMap.findW(5);
		triPent.swap_nodes(new5,5);
		triPent.swap_nodes(new6,6);
		triPent.swap_nodes(new5,10); // put 10 at center
		
		triPent.alpha=10;
		triPent.gamma=7;
		
		return triPent;
	}
	
	/**
	 * adjoin three pentagons. 
	 * @param p @see PackData, initial pentagon
	 * @param sidelength int
	 * @return
	 */
	public static PackData adjoin4(PackData p,int sidelength) {
		
		PackData triPent=p.copyPackTo();

		// adjoin 2
		triPent.packDCEL=CombDCEL.d_adjoin(triPent.packDCEL,
				p.packDCEL,5,1,sidelength);
		triPent.packDCEL.fixDCEL_raw(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		
		boolean debug=false; // debug=true;
		if (debug) {
			Iterator<EdgeSimple> tPit=triPent.vertexMap.iterator();
			System.err.println("vertexMap after 2: ");
			while (tPit.hasNext()) {
				EdgeSimple edge=tPit.next();
				System.err.println(" old, new: "+edge.v+","+edge.w);
			}
		}
		int new7=triPent.vertexMap.findW(4);
		int new8=triPent.vertexMap.findW(5);
		int newCorner=triPent.vertexMap.findW(3); 
		
		// adjoin 3
		triPent.packDCEL=CombDCEL.d_adjoin(triPent.packDCEL,
				p.packDCEL,newCorner,1,sidelength);
		triPent.packDCEL.fixDCEL_raw(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		if (debug) {
			Iterator<EdgeSimple> tPit=triPent.vertexMap.iterator();
			System.err.println("vertexMap after 3: ");
			while (tPit.hasNext()) {
				EdgeSimple edge=tPit.next();
				System.err.println(" old, new: "+edge.v+","+edge.w);
			}
		}
		int new5=triPent.vertexMap.findW(4);
		int new6=triPent.vertexMap.findW(5);
		int newGamma=triPent.vertexMap.findW(3);

		// adjoin 4
		triPent.packDCEL=CombDCEL.d_adjoin(triPent.packDCEL,
				p.packDCEL,newGamma,1,2*sidelength);
		triPent.packDCEL.fixDCEL_raw(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		if (debug) {
			Iterator<EdgeSimple> tPit=triPent.vertexMap.iterator();
			System.err.println("vertexMap after 4: ");
			while (tPit.hasNext()) {
				EdgeSimple edge=tPit.next();
				System.err.println(" old, new: "+edge.v+","+edge.w);
			}
		}
		int new4=triPent.vertexMap.findW(5);
		int new3=triPent.vertexMap.findW(4);

		// now establish the new indices
		triPent.swap_nodes(new3,3);
		triPent.swap_nodes(new4,4);
		triPent.swap_nodes(new5,5);
		triPent.swap_nodes(new6,6);
		triPent.swap_nodes(new7,7);
		triPent.swap_nodes(new8,8);
		triPent.swap_nodes(new4,9);
		
		triPent.setAlpha(9);
		triPent.setGamma(newGamma);

		return triPent;
	}
	
	/**
	 * Specialized routine to expand a pentagonal complex having 
	 * equally spaced vertices 1 2 3 4 5 on its bdry and 6 at its
	 * center by adjoining 5 copies of itself to form a new complex 
	 * with the same property (after renumbering).
	 * @param p @see PackData, 
	 * @param sidelength int, number of edges in each side
	 * @return @see PackData
	 */
	public static PackData adjoin5(PackData p,int sidelength) {
		PackData base=p.copyPackTo();
		PackDCEL pdcel=base.packDCEL;
		PackData temp=p.copyPackTo();
		
		// adjoin 2
		pdcel=CombDCEL.d_adjoin(pdcel,temp.packDCEL,3,5,sidelength);
		base.vertexMap=pdcel.oldNew;
		int newv=base.vertexMap.findW(2);
		pdcel.swapNodes(newv,2);
		base.setBdryFlags();
		
		// adjoin 3
		pdcel=CombDCEL.d_adjoin(pdcel,temp.packDCEL,4,1,2*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(6);
		pdcel.swapNodes(newv,6); // new center
		newv=base.vertexMap.findW(4);
		pdcel.swapNodes(newv,7); // temp for later use
		base.setBdryFlags();

		// adjoin 4
		pdcel=CombDCEL.d_adjoin(pdcel,temp.packDCEL,5,1,2*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(5);
		pdcel.swapNodes(newv,5);
		newv=base.vertexMap.findW(4);
		pdcel.swapNodes(newv,8); // temp for later use
		base.setBdryFlags();

		// adjoin 5
		pdcel=CombDCEL.d_adjoin(pdcel,temp.packDCEL,7,5,2*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(3);
		pdcel.swapNodes(newv,3);
		base.setBdryFlags();
		
		// adjoin 6
		pdcel=CombDCEL.d_adjoin(pdcel,temp.packDCEL,8,5,3*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(4);
		pdcel.swapNodes(newv,4);
		
		base.packDCEL.fixDCEL_raw(base);
		return base;
	}

	/**
	 * Subdivision rule of Bill Floyd (I think) that is said to be
	 * a hyperbolic Penrose tiling. This is, again, p is a pentagonal tile,
	 * but treated as a square with extra vertex in bottom. Number of sides 
	 * across the top is 1, sides are 2^N, bottom 2^(N+1). Center vert is 6,
	 * 1 is center of bottom.
	 * @param p @see PackData
	 * @param N int, which stage we are at, starting at N=0
	 * @return
	 */
	public static PackData doublePent(PackData p,int N) {
		PackData newPack=p.copyPackTo();
		PackDCEL pdcel=newPack.packDCEL;
		PackData temp=p.copyPackTo();
		int sidelength=N+1;
		
		// adjoin on left
		
		pdcel=CombDCEL.d_adjoin(pdcel,temp.packDCEL,5,2,sidelength); 
		//	DebugHelp.debugPackWrite(temp,"dyadicLeft.p");
		newPack.vertexMap=pdcel.oldNew;

		int new5=newPack.vertexMap.findW(5);
		int new4=newPack.vertexMap.findW(4);
		pdcel.swapNodes(6,4);
		pdcel.swapNodes(new5,5);
		pdcel.swapNodes(new4,4);
		pdcel.swapNodes(new5,1);

		pdcel.fixDCEL_raw(newPack);
		return newPack;
	}
	
}

