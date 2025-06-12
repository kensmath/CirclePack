package packing;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import complex.MathComplex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.RawManip;
import deBugging.DCELdebug;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import geometry.CircleSimple;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.NodeLink;
import schwarzWork.SchFlowerData;
import schwarzWork.Schwarzian;
import tiling.Tile;
import tiling.TileData;
import util.ColorUtil;
import util.TriAspect;

/**
 * Various static routines for building new packings.
 * @author kens
 */
public class PackCreation {

	/**
	 * Create a symmetric tetrahedron on the sphere,
	 * all radii arcsin(sqrt(2/3)).
	 * @return PackData
	 */
	public static PackData tetrahedron() {
		PackData p=new PackData(null);
		int[][] bouquet= {
				{0,0,0,0},
				{2,4,3,2},
				{1,3,4,1},
				{1,4,2,1},
				{1,2,3,1}
		};
	
		PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet);
		pdcel.redChain=null;
		pdcel.fixDCEL(p);
		p.hes=1;
	
		// When all radii are equal, every face is equilateral 
		// with angles 2pi/3. Using spherical half-side formula, 
		// we get radii=arcsin(sqrt(2/3)).
		double sphrad=Math.asin(Math.sqrt(2.0/3.0));
		for (int i=1;i<=4;i++) {
			p.setVertMark(i,0);
			p.setRadius(i,sphrad);
			p.setCurv(i,2.0*Math.PI);
			p.setAim(i,2.0*Math.PI);
		}
		// set centers, 1 at origin
		sphrad *=2.0; // edge length
		p.setCenter(1,new Complex(0.0));
		p.setCenter(2,new Complex(Math.PI/2.0,sphrad));
		p.setCenter(3,new Complex(Math.PI*(7.0/6.0),sphrad));
		p.setCenter(4,new Complex(Math.PI*(11.0/6.0),sphrad));
			
		p.setName("Tetrahedron");
		return p;
	}

	/**
	 * Create a HxW hex torus, 
	 * @param H int, height
	 * @param W int, width
	 * @return PackData
	 */
	public static PackData hexTorus(int H,int W) {
	
		boolean debug=false;
		int mx=H;
		int mn=W;
		if (W>H) {
			mx=W;
			mn=H;
		}
		
		// start with usual hex flower, make base 3x3 parallelogram
		PackData workPack=PackCreation.seed(6, 0);
		PackDCEL pdcel=workPack.packDCEL;
		HalfEdge he=pdcel.findHalfEdge(new EdgeSimple(5,4));
		RawManip.addVert_raw(pdcel,he);
		he=pdcel.findHalfEdge(new EdgeSimple(2,7));
		RawManip.addVert_raw(pdcel,he);
		// Note: "pointy" verts are the new ones: 
		//    top right = nodeCount, bottom left = nodeCount-1. 
		int top=pdcel.vertCount;
		int bottom=pdcel.vertCount-1;

		// Add mn-2 layers from bottom cclw to top
		int sz=2; // start with the 2x2 already built

		if (debug) { // debug=true;
			StringBuilder stbld=new StringBuilder("layer_0_"+sz+".p");
			pdcel.fixDCEL(workPack);
			DebugHelp.debugPackWrite(workPack,stbld.toString());
		}
		
		while (sz<mn) {
			int w=workPack.getLastPetal(top);
			int v=workPack.getFirstPetal(bottom);
			CombDCEL.addlayer(workPack.packDCEL,1,6,v,w);
			
			if (debug) { // debug=true;
				StringBuilder stbld=new StringBuilder("layer_1_"+sz+".p");
				pdcel.fixDCEL(workPack);
				DebugHelp.debugPackWrite(workPack,stbld.toString());
			}
			
			// add the new pointy ends
			he=pdcel.vertices[top+1].halfedge.twin.next;
			RawManip.addVert_raw(pdcel,he); // new bottom
			bottom=pdcel.vertCount;
			he=pdcel.vertices[top].halfedge.twin.next;
			RawManip.addVert_raw(pdcel,he); // new top
			top=pdcel.vertCount;
			
			if (debug) { // debug=true;
				StringBuilder stbld=new StringBuilder("points_"+sz+".p");
				pdcel.fixDCEL(workPack);
				DebugHelp.debugPackWrite(workPack,stbld.toString());
			}
			
			sz++;
		}
		
		// now, add mx-mn additional layers along the right edge
		int tick=0;
		while (tick<(mx-mn)) {
			int th=workPack.getLastPetal(top);
			int w=th;
			for (int j=2;j<mn;j++) {
				w=workPack.getLastPetal(w);
			}
			CombDCEL.addlayer(workPack.packDCEL,1,6,w,th);
			he=pdcel.vertices[top].halfedge.twin.next;
			RawManip.addVert_raw(pdcel,he); // new pointy top
			top=pdcel.vertCount;
			tick++;
		}
		
		// count off from bottom to find top left corner:
		//   'bottom' and 'topleft' should maintain their
		//   indices after the first 'adjoin'
		int topleft=bottom;
		for (int j=1;j<=mn;j++) {
			topleft=workPack.getLastPetal(topleft);
		}
		
		if (debug) { // debug=true;
			StringBuilder stbld=new StringBuilder("finish"+sz+".p");
			pdcel.fixDCEL(workPack);
			DebugHelp.debugPackWrite(workPack,stbld.toString());
			debug=false;
		}
	
		if (!debug) { // debug=true;
			// adjoin right edge to left edge for annulus
			pdcel=CombDCEL.adjoin(pdcel, pdcel, top, topleft, mn);
			top=pdcel.oldNew.findW(top);
			bottom=pdcel.oldNew.findW(bottom);
		}
		
		if (!debug) { // debug=true;
			// adjoin top and bottom edges
			pdcel=CombDCEL.adjoin(pdcel,pdcel,top,bottom,mx);
		}
		
		pdcel.fixDCEL(workPack);
		workPack.set_aim_default();
		return workPack;
	}

	/**
	 * Create a packing of hex generations, spiraling out. 
	 * Calling routine to set radii/centers. (See also 
	 * 'GridMethods.HexByHand' for generating a uniform 
	 * hex grid based on coord locations instead of spiraling.) 
	 * @param n int, number of generations (seed is 1 generation)
	 * @return @see PackData
	 */
	public static PackData hexBuild(int n) {
		if (n == 0)
			n = 1;
		double rad = 1.0 / (2.0 * n);
		PackDCEL pdcel = RawManip.seed_raw(6);
		PackData p = new PackData(null);
		pdcel.p = p;
		pdcel.fixDCEL(p);
		CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha, false);
		for (int k = 2; k <= n; k++) {
			int m = pdcel.vertCount;
			int ans = CombDCEL.addlayer(pdcel, 1, 6, m, m);
			if (ans <= 0)
				return null;
		}

		boolean debug = false; // debug=true;
		if (debug)
			DCELdebug.printRedChain(pdcel.redChain);

		pdcel.fixDCEL(p);
		for (int v = 1; v <= p.nodeCount; v++)
			p.setRadius(v, rad);
		p.set_aim_default();
		return p;
	}

	/** 
	 * Create a 'seed' packing from scratch 
	 * (with CPDrawing=null)
	 * NOTE: 'PackData.seed' call does the same, but 
	 * within existing PackData object.
	 * @param n int, number of petals
	 * @param heS int, final geometry
	 * @return @see PackData or null on error
	 */
	public static PackData seed(int n,int heS) {
		if (n<3)
			throw new ParserException("'seed' usage: n must be at least 3");
		PackDCEL pdcel=RawManip.seed_raw(n);
		pdcel.gamma=pdcel.alpha.twin;
		PackData p=new PackData(null);
		pdcel.fixDCEL(p);
		p.activeNode=pdcel.alpha.origin.vertIndx;
		p.set_aim_default();
		
		// start out hyperbolic
		p.hes=-1;
		CommandStrParser.jexecute(p,"max_pack");
		
		if (heS>0) 
			p.geom_to_s();
		if (heS==0)
			p.geom_to_e();
		p.setName("Seed "+n);
	
		return p;
	}
	
	/** 
	 * Create a euclidean 'seed' packing but using given 
	 * n intrinsic schwarzians. Normalize combinatorics so 
	 * center has max index and gamma=1; scale and rotate 
	 * so gamma is centered at z=i.
	 * 
	 * TODO: Additional normalization is an issue. 
	 * I hope vertex "error" to help guide schwarzian 
	 * adjustment. Our error is z=x+iy, where x is
	 * the change in radius of the first/last as a
	 * fraction of the initial radius, while y is 
	 * the anglesum error as a proportion of the aim. 
	 * Note that if anglesum error is not 0, it is
	 * not invariant. If less than 2pi, for example,
	 * one can get just about any anglesum error:
	 * consider the tangency points of the first and
	 * last petals on the unit circle: if not equal 
	 * they can be to any two points via a Mobius
	 * of the unit disc.
	 * 
	 * Must be given 'schvec' of n intrinsic schwarzians. 
	 * Rotate until first entry is closest to the schwarzian 
	 * for a uniform n-flower. Start with petals c1, cn
	 * in place with radii of a uniform n-flower.
	 * Note, we only use schwarzians s_1,...,s_{n-1}.
	 * This means there may be problems: computed c_1
	 * may not match c_n at start, petals may wrap too 
	 * far, etc. We recompute c_n at end and return it in
	 * 'homecs' for the user. 'layoutErr' is a complex
	 * error in two layouts of c_n.
	 * @param schvec ArrayList<Double>, intrinsic schwarzians, indexed from 0.
	 * @param homecs CircleSimple, instantiated by calling routine
	 * @param order int, =0 if unbranched (default)
	 * @param layoutErr Complex, instantiated by calling routine
	 * @return PackData, new c_n is in homecs and 
	 *         complex "error" is in 'layoutError'
	 */
	public static PackData seed(ArrayList<Double> schvec,
			CircleSimple homecs,Complex layoutErr,int order) {
		int n=schvec.size();
		PackDCEL pdcel=RawManip.seed_raw(n);
		PackData p=new PackData(null);
		pdcel.fixDCEL(p);
		  	
	  	// set up structure, center of max index, spokes,
		//   record schwarzians, etc.
	  	p.swap_nodes(1,n+1);
	  	for (int j=1;j<n;j++)
	  		p.swap_nodes(j, j+1);
	  	HalfEdge nhe=p.packDCEL.findHalfEdge(new EdgeSimple(n+1,1));
		// set up 'spokes', indexed from 1
		HalfEdge[] spokes=new HalfEdge[n+1]; // indexed from 1
		spokes[1]=nhe;
		HalfEdge he=spokes[2]=nhe.prev.twin;
		int tick=2;
		while (he!=nhe) {
			spokes[tick++]=he;
			he=he.prev.twin;
		}
	  	p.packDCEL.vertices[n+1].halfedge=nhe;
	  	p.packDCEL.setAlpha(n+1, null,false);
	  	p.packDCEL.setGamma(1);
	  	
	  	// get schwarzian closest to uniform n-flower
	  	double aim=(order+1)*2.0*Math.PI;
		double unifS=SchFlowerData.uniformS(n,aim); // uniform schwarzian
		// find edge with schwarzian closest to uniform
		tick=0;
		double bestdiff=Math.abs(schvec.get(0)-unifS);
		for (int j=1;j<n;j++) {
			double diff=Math.abs(schvec.get(j)-unifS);
			if (diff<bestdiff) {
				bestdiff=diff;
				tick=j;
			}
		}
		int B=tick;
		if (B==0) // need room for petal clw from B
			B=1;
		
		// We're indexing everything from 0 during
		//   layout; will adjust for vert indices 
		//   as needed.
		// start layout with B edge centered on 
		//   positive axis, B-1 edge clw next to it,
		//   both with radius of uniform n-flower.
		// Then layout clw until 0 edge is in place,
		//   then cclw until new 0 edge is computed.

		// initialize 2 TriAspect's
		TriAspect ftri=new TriAspect();
		TriAspect gtri=new TriAspect();
		ftri.hes=gtri.hes=0;
		ftri.allocCenters();
		gtri.allocCenters();
		double a=aim/(2.0*(double)n);
		double b=Math.sin(a);
		double r=b/(1.0-b); // uniform radius
		double len=(1+r);

		// these won't change
		ftri.center[0]=new Complex(0.0);
		gtri.center[0]=new Complex(0.0);
		ftri.radii[0]=1.0;
		gtri.radii[0]=1.0;

		// these will be repeatedly updated
		ftri.center[1]=new Complex(len*Math.cos(2.0*a),len*Math.sin(-2.0*a));
		gtri.center[1]=new Complex(len*Math.cos(2.0*a),len*Math.sin(-2.0*a));
		ftri.center[2]=new Complex(len); // on x-axis
		gtri.center[2]=new Complex(len); // on x-axis
		ftri.radii[1]=ftri.radii[2]=r;
		gtri.radii[2]=gtri.radii[2]=r;
		ftri.setTanPts();
		gtri.setTanPts();
		ftri.setBaseMobius();
		gtri.setBaseMobius();
		
		// store center, B, and B-1 data in p
		p.packDCEL.setVertCenter(n+1,new Complex(0.0));
		p.packDCEL.setVertRadii(n+1,1.0);
		p.packDCEL.setVertCenter(B+1,new Complex(ftri.center[2]));
		p.packDCEL.setVertRadii(B+1,ftri.radii[2]);
		p.packDCEL.setVertCenter(B,new Complex(ftri.center[1]));
		p.packDCEL.setVertRadii(B,ftri.radii[1]);

		CircleSimple cs=null;
		double anglesum=2.0*a; // for accumulating total angle
		int clwMoves=B-1;

		// cross clwMoves edges clockwise
		if (B>1) {
			for (int j=1;j<=clwMoves;j++) {
				double s=schvec.get(B-j);
				// get next face angle
				cs=Schwarzian.getThirdCircle(s,0,gtri.getBaseMobius(),0);
				int v=B-j;
				p.packDCEL.setVertCenter(v,cs.center);
				p.packDCEL.setVertRadii(v,cs.rad);
				double faceangle=gtri.center[1].divide(cs.center).arg();
				anglesum +=faceangle;
			
				// shift data preparing for next edge
				gtri.radii[2]=gtri.radii[1];
				gtri.center[2]=gtri.center[1];
				gtri.radii[1]=cs.rad;
				gtri.center[1]=cs.center;
				gtri.setTanPts();
				gtri.setBaseMobius();
			} 
		}
		
		// next cross edge 2 of ftri until done
		//   with edge n-2.
		if (B<(n-1)) {
			for (int j=B;j<n-1;j++) {
				double s=schvec.get(j);
				// get next face angle
				cs=Schwarzian.getThirdCircle(s,2,ftri.getBaseMobius(),0);
				int v=j+2;
				p.packDCEL.setVertCenter(v,cs.center);
				p.packDCEL.setVertRadii(v,cs.rad);
				double faceangle=cs.center.divide(ftri.center[2]).arg();
				anglesum +=faceangle;
			
				// shift data preparing for next edge
				ftri.radii[1]=ftri.radii[2];
				ftri.center[1]=ftri.center[2];
				ftri.radii[2]=cs.rad;
				ftri.center[2]=cs.center;
				ftri.setTanPts();
				ftri.setBaseMobius();
			} 
		}
		// layout c_1 again, based on c_{n-2} and c_{n-1},
		//     but do not record in packdata
		double s=schvec.get(n-1); // cs.center.arg() ftri.center[2].arg();
		CircleSimple tmpcs=Schwarzian.getThirdCircle(s,2,ftri.getBaseMobius(),0);
		double lastangle=tmpcs.center.divide(ftri.center[2]).arg();
		anglesum +=lastangle;
		
		// the 'error' is complex:
		//   * x = relative change in radius
		//   * y= relative change angle sum
		layoutErr.x=(tmpcs.rad-r)/r;
		layoutErr.y=(anglesum-aim)/aim;
		
		// rotate by pi/2 to put c_1 on y-axis, center 
		//   on unit circle
		double rot=p.packDCEL.vertices[1].center.arg();
		r=p.packDCEL.vertices[1].rad;
		CommandStrParser.jexecute(p,"rotate "+(0.5-rot/Math.PI));
		CommandStrParser.jexecute(p,"scale "+1/(1+r));
		tmpcs.center=tmpcs.center.times(new Complex(0.0,Math.PI/2.0-rot).exp());
		homecs.center=tmpcs.center.times(1.0/(1.0+r));
		homecs.rad=tmpcs.rad/(1.0+r);
		return p;
	}

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
		PackData myPacking=PackCreation.seed(6,-1);
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
		PackData hexFace=PackCreation.seed(6,-1);
		
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
		myPacking.setAlpha(1);
		
		// gamma points to middle vertex of bdry edge in first face
 		myPacking.setGamma(myPacking.getFlower(1)[gamma_indx]);
 		
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
		myPacking.setAlpha(rand.nextInt(n+1));
		
		// set default radii, aims, plot flags
		myPacking.set_rad_default();
		myPacking.set_aim_default();
		myPacking.set_plotFlags();
		
		return myPacking;
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
		triPent.packDCEL=CombDCEL.adjoin(triPent.packDCEL,
				p.packDCEL,1,1,sidelength);
		triPent.packDCEL.fixDCEL(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		int newv=triPent.vertexMap.findW(3);
		triPent.swap_nodes(newv,7);
		newv=triPent.vertexMap.findW(4);
		triPent.swap_nodes(newv,8);
		newv=triPent.vertexMap.findW(5);
		triPent.swap_nodes(newv,9);
		
		// adjoin 3
		triPent.packDCEL=CombDCEL.adjoin(triPent.packDCEL,
				p.packDCEL,7,1,2*sidelength);
		triPent.packDCEL.fixDCEL(triPent);
		triPent.vertexMap=triPent.packDCEL.oldNew;
		int new5=triPent.vertexMap.findW(4);
		int new6=triPent.vertexMap.findW(5);
		triPent.swap_nodes(new5,5);
		triPent.swap_nodes(new6,6);
		triPent.swap_nodes(new5,10); // put 10 at center
		
		triPent.setGamma(7);
		
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
		triPent.packDCEL=CombDCEL.adjoin(triPent.packDCEL,
				p.packDCEL,5,1,sidelength);
		triPent.packDCEL.fixDCEL(triPent);
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
		triPent.packDCEL=CombDCEL.adjoin(triPent.packDCEL,
				p.packDCEL,newCorner,1,sidelength);
		triPent.packDCEL.fixDCEL(triPent);
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
		triPent.packDCEL=CombDCEL.adjoin(triPent.packDCEL,
				p.packDCEL,newGamma,1,2*sidelength);
		triPent.packDCEL.fixDCEL(triPent);
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
	 * Specialized routine to expand a pentagonal 
	 * complex having equally spaced vertices 
	 * 1 2 3 4 5 on its bdry and 6 at its center by 
	 * adjoining 5 copies of itself to form a new 
	 * complex with the same property (after 
	 * renumbering).
	 * @param p @see PackData, 
	 * @param sidelength int, number of edges in each side
	 * @return @see PackData
	 */
	public static PackData adjoin5(PackData p,int sidelength) {
		PackData base=p.copyPackTo();
		PackDCEL pdcel=base.packDCEL;
		PackData temp=p.copyPackTo();
		
		// adjoin 2
		pdcel=CombDCEL.adjoin(pdcel,temp.packDCEL,3,5,sidelength);
		base.vertexMap=pdcel.oldNew;
		int newv=base.vertexMap.findW(2);
		pdcel.swapNodes(newv,2);
		base.setBdryFlags();
		
		// adjoin 3
		pdcel=CombDCEL.adjoin(pdcel,temp.packDCEL,4,1,2*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(6);
		pdcel.swapNodes(newv,6); // new center
		newv=base.vertexMap.findW(4);
		pdcel.swapNodes(newv,7); // temp for later use
		base.setBdryFlags();

		// adjoin 4
		pdcel=CombDCEL.adjoin(pdcel,temp.packDCEL,5,1,2*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(5);
		pdcel.swapNodes(newv,5);
		newv=base.vertexMap.findW(4);
		pdcel.swapNodes(newv,8); // temp for later use
		base.setBdryFlags();

		// adjoin 5
		pdcel=CombDCEL.adjoin(pdcel,temp.packDCEL,7,5,2*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(3);
		pdcel.swapNodes(newv,3);
		base.setBdryFlags();
		
		// adjoin 6
		pdcel=CombDCEL.adjoin(pdcel,temp.packDCEL,8,5,3*sidelength);
		base.vertexMap=pdcel.oldNew;
		newv=base.vertexMap.findW(4);
		pdcel.swapNodes(newv,4);
		
		base.packDCEL.fixDCEL(base);
		return base;
	}

	public static PackData pentTiling(int N) {
		PackData pent=PackCreation.seed(5,0);
		pent.swap_nodes(1,6);
		
		int sidelength=1;
		int count=1;
		
		while (count<N) {
			// expand 
			pent=adjoin5(pent,sidelength);
			sidelength *= 2;
			count++;
		}
		
		pent.setAlpha(6);
		pent.setGamma(1);
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
			pent.tileData=TileData.paveMe(pent,pent.getAlpha());
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create pent 'TileData'");
		}

		return pent;
	}

	public static PackData pentHypTiling(int N) {
		PackData pentBase=PackCreation.seed(5,0);
		pentBase.packDCEL.swapNodes(1,6);
		
		PackData heap=pentBase.copyPackTo();
		PackDCEL pdcel=heap.packDCEL;
		
		int generation=0;
		
		while (generation<N) {
			
			// expand 
			heap=doublePent(heap,generation);
			pdcel=heap.packDCEL;
			//	DebugHelp.debugPackWrite(heap,"doubleheap.p");
			
			pdcel=CombDCEL.adjoin(pdcel,pentBase.packDCEL,4,5,2);
			heap.vertexMap=pdcel.oldNew;
			int new4=heap.vertexMap.findW(4);
			int new3=heap.vertexMap.findW(3);
			pdcel.swapNodes(new3,3);
			pdcel.swapNodes(new4,4);
			pdcel.fixDCEL(heap);
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
		PackData pent=PackCreation.seed(5,0);
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
		PackData pent=PackCreation.seed(5,0);
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
		
		pdcel=CombDCEL.adjoin(pdcel,temp.packDCEL,5,2,sidelength); 
		//	DebugHelp.debugPackWrite(temp,"dyadicLeft.p");
		newPack.vertexMap=pdcel.oldNew;

		int new5=newPack.vertexMap.findW(5);
		int new4=newPack.vertexMap.findW(4);
		pdcel.swapNodes(6,4);
		pdcel.swapNodes(new5,5);
		pdcel.swapNodes(new4,4);
		pdcel.swapNodes(new5,1);

		pdcel.fixDCEL(newPack);
		return newPack;
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
		p1.packDCEL=CombDCEL.adjoin(p1.packDCEL,
				p2.packDCEL,v1,v2,n);
		p1.packDCEL.fixDCEL(p1);
		
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
	
	/**
	 * Create an n-tile that has been barycentrically
	 * subdivided and then its faces hex subdivided.
	 * The center vert is 1, the n tile vertices are 
	 * indexed 2,4,..2n, the mid vertices of the bdry
	 * edges are 3,5,7,.. 
	 * @param n int, n>=2
	 * @return PackData
	 */
	public static PackData tileHexed(int n) {
		if (n<2)
			return null;
		Tile tile=new Tile(n);
		PackData p=tile.singleCanonical(3);
		p.setAlpha(1);
		p.setGamma(2);
		p.setGeometry(0);;
		p.set_aim_default();
		CommandStrParser.jexecute(p,"set_rad .05 a");
		CommandStrParser.jexecute(p,"set_aim 1.0 b");
		double tang=Math.PI*(1.0-2.0/((double)n));
		for (int m=1;m<=n;m++)
			p.packDCEL.vertices[2*m].aim=tang;
		CommandStrParser.jexecute(p,"repack");
		CommandStrParser.jexecute(p,"layout");
		return p;
	}
	
	/**
	 * DCEL version of specialized routine to create a 
	 * 'gens' generations of a pentagonal tiling in a
	 * DCEL having equally spaced vertices 1 2 3 4 5 
	 * on its bdry and 6 at its center. Calling routine
	 * only needs to attachDCEL, set aims, bdry radii,
	 * repack, etc.
	 * @param gens int, number of generations.
	 * @return PackDCEL
	 */
	public static PackDCEL pentagonal_dcel(int gens) {
		PackDCEL base=RawManip.seed_raw(5);
		RawManip.swapNodes_raw(base,1,6);
		CombDCEL.redchain_by_edge(base, null,null,false);
		CombDCEL.fillInside(base);
		PackDCEL pdcel=CombDCEL.cloneDCEL(base); 
		if (gens==0) // single pentagon?
			return pdcel;
		// DCELdebug.printRedChain(btrfly.redChain);
		
		// This is iterative, each from new 'base'
		double sidesize=0.5; // gens=3;
		for (int g=1;g<=gens;g++) {
			sidesize=2*sidesize;
			int sidelength=(int)sidesize;

			// attach first copy; note where old1 ended up
			PackDCEL temp=CombDCEL.cloneDCEL(base);
			pdcel=CombDCEL.adjoin(pdcel,temp,1,3,sidelength);
			int new5=pdcel.oldNew.findW(5); // new index
			CombDCEL.fillInside(pdcel); 

			// DCELdebug.redindx(btrfly);
		
			// these two form a butterfly
			PackDCEL btrfly=CombDCEL.cloneDCEL(pdcel);
			PackDCEL btrfly2=CombDCEL.cloneDCEL(pdcel); // copy for next step
			pdcel=CombDCEL.adjoin(pdcel,btrfly,3,4,3*sidelength);
			
			// adjoin this for two more faces
			pdcel=CombDCEL.adjoin(pdcel,btrfly2,new5,3,4*sidelength);
			CombDCEL.fillInside(pdcel);
		
			// find 5 cclw bdry vertices with degree 3.
			NodeLink corners=new NodeLink();
			RedEdge rtrace=pdcel.redChain;
			
			do {
				int num=rtrace.myEdge.origin.getNum();
				if (num==2)
					corners.add(rtrace.myEdge.origin.vertIndx);
				rtrace=rtrace.nextRed;
			} while(rtrace!=pdcel.redChain);
			// DCELdebug.printRedChain(pdcel.redChain);
			
			if (corners==null || corners.size()!=5)
				throw new DCELException("failed to find 5 corners");
			
			for (int v=1;v<=5;v++) {
				int oldv=corners.get(v-1);
				RawManip.swapNodes_raw(pdcel,v,oldv);
			}
			
			base=pdcel;
		} // done with construction

		return pdcel;
	}
	
	/**
	 * This builds several generations of packing for the famous Kagome lattice,
	 * popular in theoretical physics and material science. For us, this is a
	 * tiling, a pattern of hexagons and triangles. We build the tile data by hand
	 * and then get the packing.
	 * 
	 * To index vertices forming the tiles, we use the regular hex grid, e.g.,
	 * identified with span of independent vectors u=<1/2,-sqrt(3)/2> and
	 * w=<1/2,sqrt(3)/2>, so <i,j> is interpreted as i*u+j*w. We start with a
	 * fundamental domain consisting of a hex tile and two attached triangle tiles
	 * which fill the "square" with corners (-1,-1), (1,-1), (1,1), (-1,1) We can
	 * then shift it by (2i,2j) for i=0,1,...,n and j=0,1,....,n to fill out a large
	 * "square" in u,v.
	 * 
	 * @param n int
	 * @return PackData, null on error
	 */
	public static PackData buildKagome(int n) {
		// prepare 'TileData', mode 1
		int numtiles = (2 * n + 1) * (2 * n + 1) * 3;
		int mode = 1;
		TileData tileData = new TileData((PackData) null, numtiles, mode);

		// first task: set vertex indices for all locations
		// in a (4n+2)x(4n+2) array that will be used.
		// This is basically a square in the Gaussian
		// lattice, but we don't use (i,j) if i and j are
		// both even.
		int[][] indices = new int[3 + 4 * n][3 + 4 * n];
		int offset = 2 * n + 1; // origin at (2n+1,2n+1)
		int tick = 0;

		// do first generation
		int J = -1 + offset; // bottom edge
		for (int i = -1; i <= 1; i++)
			indices[i + offset][J] = ++tick;
		int I = 1 + offset; // right edge
		indices[I][1] = ++tick;
		J = 1 + offset; // top edge
		for (int i = 1; i >= -1; i--)
			indices[i + offset][J] = ++tick;
		I = -1 + offset; // left edge
		indices[I][1] = ++tick;

		// iterate: 2n further generations of paths about
		// the origin, each forming a cclw "square"
		for (int g = 1; g <= n; g++) {

			// next generation, omit when i, j both even
			J = -2 * g + offset;
			for (int i = 1; i < 2 * g; i = i + 2)
				indices[-2 * g + i + offset][J] = ++tick;
			I = 2 * g + offset;
			for (int j = 1; j < 2 * g; j = j + 2)
				indices[I][-2 * g + j + offset] = ++tick;
			J = 2 * g + offset;
			for (int i = 1; i < 2 * g; i = i + 2)
				indices[2 * g - i + offset][J] = ++tick;
			I = -2 * g + offset;
			for (int j = 1; j < 2 * g; j = j + 2)
				indices[I][2 * g - j + offset] = ++tick;

			// next generation, take all vertices
			J = -2 * g - 1 + offset;
			for (int i = 0; i <= 4 * g + 1; i++)
				indices[-2 * g - 1 + i + offset][J] = ++tick;
			I = 2 * g - 1 + offset;
			for (int j = 1; j < 4 * g + 1; j++)
				indices[I][-2 * g - 1 + j + offset] = ++tick;
			J = 2 * g + 1 + offset;
			for (int i = 0; i <= 4 * g + 1; i++)
				indices[2 * g + 1 - i + offset][J] = ++tick;
			I = -2 * g - 1 + offset;
			for (int j = 1; j < 4 * g + 1; j++)
				indices[I][2 * g - 1 - j + offset] = ++tick;
		}

		// build 3 prototype tiles: a hex and two opposite
		// triangles form a combinatorial square, which
		// we replicate in an (2n+1)x(2n+1) pattern.
		int[][] hextile = new int[6][2];
		hextile[0][0] = -1;
		hextile[0][1] = -1;
		hextile[1][0] = 0;
		hextile[1][1] = -1;
		hextile[2][0] = 1;
		hextile[2][1] = 0;
		hextile[3][0] = 1;
		hextile[3][1] = 1;
		hextile[4][0] = 0;
		hextile[4][1] = 1;
		hextile[5][0] = -1;
		hextile[5][1] = 0;

		int[][] lowtri = new int[3][2];
		lowtri[0][0] = 0;
		lowtri[0][1] = -1;
		lowtri[1][0] = 1;
		lowtri[1][1] = -1;
		lowtri[2][0] = 1;
		lowtri[2][1] = 0;

		int[][] uptri = new int[3][2];
		uptri[0][0] = 0;
		uptri[0][1] = 1;
		uptri[1][0] = -1;
		uptri[1][1] = 1;
		uptri[2][0] = -1;
		uptri[2][1] = 0;

		// Create shifted copies of the fundamental
		// tiles, iterating over their center locations
		int count = 0;
		int vtick = 0;
		for (int a = -2 * n; a <= 2 * n; a++)
			for (int b = -2 * n; b <= 2 * n; b++) {
				I = a + offset;
				J = b + offset;

				// hexagon tile first, type=4
				tileData.myTiles[++count] = new Tile(null, tileData, 6);
				tileData.myTiles[count].tileType=4;
				tileData.myTiles[count].tileIndex = count;
				for (int k = 0; k < 6; k++) {
					int v = indices[I + hextile[k][0]][J + hextile[k][1]];
					vtick = (v > vtick) ? v : vtick;
					tileData.myTiles[count].vert[k] = v;
				}

				// lower triangle, type=5
				tileData.myTiles[++count] = new Tile(null, tileData, 3);
				tileData.myTiles[count].tileType=5;
				tileData.myTiles[count].tileIndex = count;
				for (int k = 0; k < 3; k++) {
					int v = indices[I + lowtri[k][0]][J + lowtri[k][1]];
					vtick = (v > vtick) ? v : vtick;
					tileData.myTiles[count].vert[k] = v;
				}

				// upper triangle, type=5
				tileData.myTiles[++count] = new Tile(null, tileData, 3);
				tileData.myTiles[count].tileType=5;
				tileData.myTiles[count].tileIndex = count;
				for (int k = 0; k < 3; k++) {
					int v = indices[I + uptri[k][0]][J + uptri[k][1]];
					vtick = (v > vtick) ? v : vtick;
					tileData.myTiles[count].vert[k] = v;
				}
			} // done with double for loops

		tileData.tileCount = count;
		PackData newPack = TileData.tiles2packing(tileData);
		return newPack;
	}

	/**
	 * Create square grid packing with N vertices on each edge.
	 * We proceed by building the bouquet.
	 * @param N int
	 * @return new @see PackData
	 */
	public static PackData squareGrid(int N) {
	
		if (N<2)
			N=2;
		
		// Grid points in N-by-N array, barycenters in
		//   (N-1)-by-(N-1) shifted array. Positions
		//   given by (i,j) as with a matrix. Corners
		//   are A=(1,1), upper left, B=(N,1) lower
		//   left, C=(N,N), and D=(1,N). Similar with
		//   the barycenter grid. 
		// Have 10 types of nodes: 
		//   * cclw corners A-D,
		//   * cclw edge relative interiors, L, B, R, T,
		//   * interior grid
		//   * interior barycenters
		// Based on (i,j) locations, we can assign index
		//   and find flowers.
		int Nsq=N*N;
		int vertcount=2*(Nsq-N)+1; // total 
		
		// build bouquet according to the 10 types
		int[][] bouquet=new int[vertcount+1][];
		
		// 4 corner flowers: A=1,B=N,C=Nsq,D=Nsq-N+1
		int[] cnr=new int[3]; // upper left
		cnr[0]=2;cnr[1]=Nsq+1;cnr[2]=N+1;
		bouquet[1]=cnr;
		
		cnr=new int[3]; // lower left
		cnr[0]=2*N;cnr[1]=Nsq+N-1;cnr[2]=N-1;
		bouquet[N]=cnr;
		 
		cnr=new int[3]; // lower right
		cnr[0]=Nsq-1;cnr[1]=Nsq+(N-1)*(N-1);cnr[2]=Nsq-N;
		bouquet[Nsq]=cnr;
		
		cnr=new int[3]; // upper right
		cnr[0]=N*(N-2)+1;cnr[1]=Nsq+(N-1)*(N-2)+1;cnr[2]=Nsq-N+2;
		bouquet[Nsq-N+1]=cnr;
		
		// L and R edges, 2 <= i <= N-1
		for (int i=2;i<N;i++) {
			int[] fan=new int[5];
	
			// left edge
			int spot=i;
			fan[0]=spot+1;
			fan[1]=Nsq+i;
			fan[2]=spot+N;
			fan[3]=fan[1]-1;
			fan[4]=spot-1;
			bouquet[spot]=fan;
			
			// right edge
			fan=new int[5];
			spot=Nsq-N+i;
			fan[0]=spot-1;
			fan[1]=2*Nsq-3*N+i+1;
			fan[2]=spot-N;
			fan[3]=fan[1]+1;
			fan[4]=spot+1;
			bouquet[spot]=fan;
			
		} 
		
		// B and T edges, 2 <= j <= N-1
		for (int j=2;j<N;j++) {
			int[] fan=new int[5];
	
			// bottom edge
			int spot=N*j;
			fan[0]=spot+N;
			fan[1]=Nsq+(N-1)*j;
			fan[2]=spot-1;
			fan[3]=fan[1]-N+1;
			fan[4]=spot-N;
			bouquet[spot]=fan;
			
			// top edge
			fan=new int[5];
			spot=N*(j-1)+1;
			fan[0]=spot-N;
			fan[1]=Nsq+(N-1)*(j-2)+1;
			fan[2]=spot+1;
			fan[3]=fan[1]+N-1;
			fan[4]=spot+N;
			bouquet[spot]=fan;
		}
		
		// the generic grid location (i,j), 2 <= i,j <= N-1
		for (int i=2;i<N;i++) {
			for (int j=2;j<N;j++) {
				int[] petals=new int[9];
				int spot=N*(j-1)+i;
				int bspot=Nsq+(N-1)*(j-2)+i-1; // upper left of spot
				petals[0]=spot-1;
				petals[1]=bspot;
				petals[2]=spot-N;
				petals[3]=bspot+1;
				petals[4]=spot+1;
				petals[5]=bspot+N;
				petals[6]=spot+N;
				petals[7]=bspot+N-1;
				petals[8]=petals[0];
				bouquet[spot]=petals;
			}
		}
		
		// barycenter locations (u,v), 1 <= u <= N-1
		for (int u=1;u<N;u++) {
			for (int v=1;v<N;v++) {
				int[] flower=new int[5];
				int bspot=Nsq+(N-1)*(v-1)+u;
				int spot=N*(v-1)+u;
				flower[0]=spot;
				flower[1]=spot+1;
				flower[2]=spot+N+1;
				flower[3]=spot+N;
				flower[4]=flower[0];
				bouquet[bspot]=flower;
			}
		}
		
		// identify alpha, gamma
		int alp,gam;
		if (N!=2*(int)(N/2.0)) { // N is odd, use grid point
			int hlf=(N+1)/2;
			alp=N*(hlf-1)+hlf; // index of center
			gam=alp-hlf+1;   // middle of top
		}
		else { // use central barycenter
			int hlf=(int)(N/2);
			alp=Nsq+(N-1)*(hlf-1)+hlf;
			gam=alp-hlf+1;
		}
		
		PackDCEL newDCEL=CombDCEL.getRawDCEL(bouquet,alp);
		PackData p=new PackData(null);
		newDCEL.fixDCEL(p);
		p.set_aim_default();
		p.setAlpha(alp);
		p.setGamma(gam);
		
		// preset radii for 2x2 square carrier
		double R=1.0/((double)N-1.0);
		double r=R*(Math.sqrt(2.0)-1.0);
		for (int v=1;v<=Nsq;v++)
			p.setRadius(v, R);
		for (int v=(Nsq+1);v<=p.nodeCount;v++)
			p.setRadius(v, r);
		p.fillcurves();
		p.packDCEL.layoutPacking();
		
		return p;
	}

	/**
	 * Create N generations of a 2D fusion tiling related
	 * to Fibonnacci numbers. I learned this from Natalie Frank.
	 * There are four tile types, A, B, C, D, each has next
	 * fusion stage made from a certain combination. Here is
	 * the pattern ('/' means horizontally below):
	 * A -> [C A / D B]; B -> [A C]; C -> [B/A]; D -> [A]
	 * H, W, X are integer height/width parameters, >= 1.
	 * A is W x H, B is W x X, C is X x H, D is X x X.
	 * @param W int
	 * @param H int
	 * @param X int
	 * @param N int, number of generations
	 * @return new PackData, null on error 
	 */
	public static PackData fibonacci2D(int N,int W,int H,int X) {

		int generation=1; // number of generations in current build
		int currentWidth=W;
		int currentHeight=H;
		int startBase=X;
		int baseWidth=X;
		int baseHeight=X;

		// We start with base tile of each type:
		PackData fusionA = PackCreation.seed(2*W+2*H,0); // A is W x H
		PackData fusionB = PackCreation.seed(2*W+2*X,0); // B is W x X
		PackData fusionC = PackCreation.seed(2*H+2*X,0); // C is X x H
		PackData fusionD = PackCreation.seed(4*X,0);     // D is X x X

		// keep track of tiles using mark of barycenter vertex
		fusionA.setVertMark(1,1);
		fusionB.setVertMark(2,2);
		fusionC.setVertMark(1,3);
		fusionD.setVertMark(1,4);
				
		// Corners are 1,2,3,4 at every stage in every tile, upper left is 1
		// reset the corner numbers, starting at '2' (first petal for seed)
		if (!reNumBdry(fusionA,2,currentWidth,currentHeight) ||
				!reNumBdry(fusionB,2,currentWidth,startBase) ||
				!reNumBdry(fusionC,2,startBase,currentHeight) ||
				!reNumBdry(fusionD,2,startBase,startBase))
			throw new CombException("problem with first renumbering");

		// iterate construction
		while (generation < N) {
			generation++;
			
			// hold old copies
			PackData holdA=fusionA.copyPackTo();
			PackData holdB=fusionB.copyPackTo();
			PackData holdC=fusionC.copyPackTo();
			PackData holdD=fusionD.copyPackTo();

			// new level of A = [C A/D B], (X+W) x (H+X)
			// top part, [C A] first, (X+W) x H
			fusionA=holdC.copyPackTo();
			fusionA.packDCEL=CombDCEL.adjoin(fusionA.packDCEL,
					holdA.packDCEL,4,1,currentHeight);
			fusionA.vertexMap=fusionA.packDCEL.oldNew;
			if (!reNumBdry(fusionA,1,baseWidth+currentWidth,currentHeight))
				throw new CombException("failed [C A]");
			// transfer non-zero marks
			for (int v=1;v<=holdA.nodeCount;v++) {
				if(holdA.getVertMark(v)!=0) {
					int w=fusionA.vertexMap.findW(v);
					if (w>0)
						fusionA.setVertMark(w,holdA.getVertMark(v));
				}
			}
			// lower part, [D B], (X+W) x X
			PackData lower=holdD.copyPackTo();
			lower.packDCEL=CombDCEL.adjoin(lower.packDCEL,
					holdB.packDCEL,4,1,baseHeight);
			lower.vertexMap=lower.packDCEL.oldNew;
			if (!reNumBdry(lower,1,baseWidth+currentWidth,baseHeight))
				throw new CombException("failed [D B]");
			// transfer non-zero marks
			for (int v=1;v<=holdB.nodeCount;v++) {
				if(holdB.getVertMark(v)!=0) {
					int w=lower.vertexMap.findW(v);
					if (w>0)
						lower.setVertMark(w,holdB.getVertMark(v));
				}
			}
			// adjoin them, (X+W) x (H+X)
			fusionA.packDCEL=CombDCEL.adjoin(fusionA.packDCEL,
					lower.packDCEL,3,4,baseWidth+currentWidth);
			fusionA.vertexMap=fusionA.packDCEL.oldNew;
			if (!reNumBdry(fusionA,1,baseWidth+currentWidth,baseHeight+currentHeight))
				throw new CombException("failed [C A/D B]");
			// transfer non-zero marks
			for (int v=1;v<=lower.nodeCount;v++) {
				if(lower.getVertMark(v)!=0) {
					int w=fusionA.vertexMap.findW(v);
					if (w>0)
						fusionA.setVertMark(w,lower.getVertMark(v));
				}
			}

			// new level of B = [A C], (W+X) x H
			fusionB=holdA.copyPackTo();
			fusionB.packDCEL=CombDCEL.adjoin(fusionB.packDCEL,
					holdC.packDCEL, 4, 1,currentHeight);
			fusionB.vertexMap=fusionB.packDCEL.oldNew;
			if (!reNumBdry(fusionB,1,currentWidth +baseWidth,currentHeight))
				throw new CombException("failed [A C]");
			// transfer non-zero marks
			for (int v=1;v<=holdC.nodeCount;v++) {
				if(holdC.getVertMark(v)!=0) {
					int w=fusionB.vertexMap.findW(v);
					if (w>0)
						fusionB.setVertMark(w,holdC.getVertMark(v));
				}
			}

			// new level of C = [B/A], W x (X+H)
			fusionC=holdB.copyPackTo();
			fusionC.packDCEL=CombDCEL.adjoin(fusionC.packDCEL,
					holdA.packDCEL,3,4,currentWidth);
			fusionC.vertexMap=fusionC.packDCEL.oldNew;
			if (!reNumBdry(fusionC,1,currentWidth,baseHeight+currentHeight))
				throw new CombException("failed [B/A]");
			// transfer non-zero marks
			for (int v=1;v<=holdA.nodeCount;v++) {
				if(holdA.getVertMark(v)!=0) {
					int w=fusionC.vertexMap.findW(v);
					if (w>0)
						fusionC.setVertMark(w,holdA.getVertMark(v));
				}
			}

			// new level D = old level A; on first pass only,
			//     reset the mark at barycenter vertex to 4
			fusionD=holdA;
			if (generation==2)
				fusionD.setVertMark(fusionD.getAlpha(),4);
				
			// debug options to see specified piece, default to 'A'
			char c='A'; // c='B'    c='C'    c='D'
			switch(c) {
			case 'B':
			{
				fusionA=fusionB;
				break;
			}
			case 'C':
			{
				fusionA=fusionC;
				break;
			}
			case 'D':
			{
				fusionA=fusionD;
				break;
			}
			} // end of switch
			
			// continue
			int oldBaseHeight=baseHeight;
			int oldBaseWidth=baseWidth;
			baseWidth=currentWidth;
			baseHeight=currentHeight;
			currentWidth += oldBaseWidth;
			currentHeight += oldBaseHeight;

		} // end of while
		
		// set the aims
		fusionA.set_aim_default();
		for (int v=1;v<=fusionA.nodeCount;v++) {
			if (fusionA.isBdry(v))
				fusionA.setAim(v,1.0*Math.PI);
		}
		fusionA.setAim(1,0.5*Math.PI);
		fusionA.setAim(2,0.5*Math.PI);
		fusionA.setAim(3,0.5*Math.PI);
		fusionA.setAim(4,0.5*Math.PI);
				
		// repack, layout
//		double crit=GenModBranching.LAYOUT_THRESHOLD;
//		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		fusionA.fillcurves();
		fusionA.repack_call(1000);
		try {
			fusionA.packDCEL.layoutPacking(); 
		} catch (Exception ex) {
			throw new CombException("'fib2D' creation failed");
		}
		
		// normalize: 3 on unit circle, 5 7 horizontal
		double ctr=fusionA.getCenter(1).abs();
		double factor=1.0/ctr;
		fusionC.eucl_scale(factor);
		Complex z=fusionA.getCenter(3).minus(fusionA.getCenter(2));
		double ang=(-1.0)*(MathComplex.Arg(z));
		fusionA.rotate(ang);
		
		try {
			fusionA.tileData=TileData.paveMe(fusionA,fusionA.getAlpha());
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create Fibonacci 'TileData'");
		}

		return fusionA;
	}
	
	/**
	 * Renumber the four corners of current fusionA
	 * @param p PackData, current stage 
	 * @param corner1, bdry vert to become '1'.
	 * @param w int, width
	 * @param h int, height
	 * @return false on error
	 */
	public static boolean reNumBdry(PackData p,int corner1,int w,int h) {
		String bstr=new String("b("+corner1+" "+corner1+")");
		NodeLink blist=new NodeLink(p,bstr);
		if (blist==null || blist.get(0)!=corner1)
			return false;
		
		int swp=blist.get(0);
		p.packDCEL.swapNodes(swp,1);
		blist=blist.swapVW(swp, 1);
		
		swp=blist.get(h);
		p.packDCEL.swapNodes(swp,2);
		blist=blist.swapVW(swp, 2);
		
		swp=blist.get(h+w);
		p.packDCEL.swapNodes(swp,3);
		blist=blist.swapVW(swp, 3);
		
		swp=blist.get(h+w+h);
		p.packDCEL.swapNodes(swp,4);
		
		return true;
	}

	
	/**
	 * Given PackData, add to its 'vlist' and 'elist' from
	 * the lists 'nl' and 'el', resp., but using the EdgeLink 
	 * of {old,new} pairs to translate the indices.
	 * @param p PackData; we'll change the lists here
	 * @param nl NodeLink, new vertices ('nl' remains unchanged)
	 * @param el EdgeLink, new edges ('el' remains unchanged)
	 * @param vertMap EdgeLink, (should remain unchanged)
	 */
	public static void updateLists(PackData p,NodeLink nl,EdgeLink el,EdgeLink vertMap) {
		if (nl!=null && nl.size()>0) {
			if (p.vlist==null)
				p.vlist=new NodeLink(p);
			Iterator<Integer> cV=nl.iterator();
			while (cV.hasNext()) {
				int v=cV.next();
				p.vlist.add(vertMap.findW(v));
			}
		}
		if (el!=null && el.size()>0) {
			if (p.elist==null)
				p.elist=new EdgeLink(p);
			Iterator<EdgeSimple> cE=el.iterator();
			EdgeSimple edge=null;
			while (cE.hasNext()) {
				edge=cE.next();
				int V=vertMap.findW(edge.v);
				int W=vertMap.findW(edge.w);
				p.elist.add(new EdgeSimple(V,W));
			}
		}
	}
		
	/**
	 * Utility for bdry inspection. Find count and min/max 
	 * of intended degrees of circles that would be added
	 * to the boundary in 'triGroup' construction. 'vutil'
	 * is 0, 1, 2, for degree designation A, B, C in 'triGroup' 
	 * Return null if there is no 'redChain'.
	 * @param pdc PackDCEL
	 * @return ans[3]: 0=bdry count, 1=min deg desig, 2=max deg desig
	 */
	public static int []ck_redchain(PackDCEL pdc) {
		int []ans=new int[3];
		if (pdc.redChain==null)
			return null;

		int bcount=0;
		ans[1]=100000; // minimum new mark
		ans[2]=0; // maximum new mark
		int mx=0;
		int mn=100000;
		RedEdge rtrace=pdc.redChain;
		do {
			bcount++;
			int w=rtrace.myEdge.origin.vertIndx;
			int next=rtrace.nextRed.myEdge.origin.vertIndx;
			int vec=(pdc.vertices[w].vutil-pdc.vertices[next].vutil+3)%3;
			int nxmk=(pdc.vertices[w].vutil+vec)%3; // mark added vert would have
			mx=(nxmk>mx) ? nxmk : mx;
			mn=(nxmk<mn) ? nxmk : mn;
			rtrace=rtrace.nextRed;
		} while (rtrace!=pdc.redChain);
		ans[0]=bcount;
		ans[1]=mn;
		ans[2]=mx;
		return ans;
	}
	
	/**
	 * Create 'maxgens' generations of the Cayley graph of 
	 * the triangle group {A/2, B/2, C/2}. A, B, C are the
	 * degrees of the vertices, which are marked as 1, 2, 3,
	 * resp. One and only one may be zero, indicating a type
	 * of j-function with logarithmic branching.
	 * 
	 * Typical classical cases: 
	 *    A=2, B=3, C=5: Schwarz triangles tiling the sphere
	 *    A=0, B=2, C=3: the classical j-function in hyp plane
	 *    A=B=C=7: constant 7-degree packing in hyp plane
	 * 
	 * Geometry is determined by r=2*(1/A+1/B+1/C). if r>1 sph;
	 * r=1 eucl; and r<1 hyp. If one of A,B,C is odd, then the
	 * others must be equal.The calling routine has checked 
	 * validity of parameters. We compute/set radii using trig,
	 * but the calling routine repacks. We set face colors,
	 * blue for oriented, red for reverse oriented.
	 * 
	 * Our method is to continually expand a bdry chain of 'growbdry'
	 * objects by proceeding cclw and completing flowers as we go.
	 * Each completed flow adds to growing 'bouvec' vector that will
	 * lead to the final 'bouquet'.
	 * 
	 * @param maxgens int
	 * @param A int
	 * @param B int
	 * @param C int
	 * @return PackData, null on error
	 */
	public static PackData buildTriGroup(int maxgens,int A,int B,int C) {

		// what geometry?
		int hees=-1; // default: hyp
		if (A!=0 && B!=0 && C!=0) {
			double recipsum=2.0/(double)(A)+2.0/(double)(B)+2.0/(double)(C);
			if (Math.abs(recipsum-1)<.0001)
				hees=0; // eucl
			else if (recipsum>1.0) 
				hees=1; // sph
		}
		else { // rotate
			if (A==0) {
				A=B;
				B=C;
				C=0;
			}
		}

		// maintain various info on vertices during construction
		ArrayList<TmpVert> tmpVerts=new ArrayList<TmpVert>();
		int generation=1;
		
		// initial vertex has degree A (1-type)
		int[] seedflower=new int[A+1];
		for (int j=0;j<A;j++)
			seedflower[j]=2+j;
		seedflower[A]=2; // close up
		tmpVerts.add(new TmpVert(1,1,seedflower));
		
		// start the first bdry node, 2-type
		int nodecount=2; // this counts nodes as they are created
		BdryNode baseNode=new BdryNode(2,2,B,generation,null,null);
		baseNode.petals=new ArrayList<Integer>();
		baseNode.petals.add(3);
		baseNode.petals.add(1); // seed
		baseNode.petals.add(A+1);

		BdryNode preNode=baseNode;
		for (int j=1;j<A;j++) {
			nodecount=j+2;
			// alternate B/C, mark 2/3
			int AB;
			int tp;
			if (nodecount%2==0) {
				AB=B;
				tp=2;
			}
			else {
				AB=C;
				tp=3;
			}
			BdryNode newNode=new BdryNode(nodecount,tp,AB,generation,preNode,null);
			preNode.next=newNode;
			newNode.petals=new ArrayList<Integer>(3);
			newNode.petals.add(2+(nodecount-1)%A);
			newNode.petals.add(1);
			newNode.petals.add(nodecount-1);
			preNode=newNode;
		}
		preNode.next=baseNode; // close up
		baseNode.prev=preNode; // baseNode.printBdry();
		
		// now we circulate cclw about the bdryNodes
		while (generation<(maxgens+2) && baseNode!=null) {
			
			int mygen=baseNode.generation;
			if (mygen==generation) {
				generation++;
				if (generation>=(maxgens+2)) { // to agree with old version
					continue;
				}
			}
			
			// skip degree 0 types
			if (baseNode.degree==0) {
				baseNode=baseNode.next;
				continue;
			}
		
			// recursively check if previous BdryNode has all its petals
			while (baseNode.prev.petals.size()==baseNode.prev.degree) {
				
				// add edge
				baseNode.prev.prev.petals.add(0,baseNode.indx);
				baseNode.petals.add(baseNode.prev.prev.indx);
				
				// close up/remove prev from bdry chain
				baseNode.prev.petals.add(baseNode.prev.petals.get(0));
				tmpVerts.add(bdry2tmp(baseNode.prev));

				// check for closure
				if (baseNode.next.next.next==baseNode) {
					
					// store the last two vertices
					tmpVerts.add(bdry2tmp(baseNode));
					tmpVerts.add(bdry2tmp(baseNode.next));

					baseNode=null;
					break;
				}
				
				// else fix pointers, but baseNode is unchanged
				baseNode.prev=baseNode.prev.prev;
				baseNode.prev.next=baseNode;
			}
			
			if (baseNode==null)
				break;
			
			// now see if new petals are needed
			int facecount=baseNode.petals.size()-1;
			if (facecount>baseNode.degree)
				throw new CombException(
						"seems to be inconsistency at node "+baseNode.indx);
			int newnum=baseNode.degree-facecount-1; // number of new petals needed
			
// debugging
//			System.out.println("base is "+baseNode.indx+"; newnum="+newnum);

			// add new nodes/bdryNodes, adjusting chain.
			if (newnum>0) {
				
				// does baseNode.next have all its petals? 
				boolean lastclose=
					(baseNode.next.petals.size()==baseNode.next.degree);
				
				for (int j=1;j<=newnum;j++) {
					
					// last may need to be closed up
					if (j==newnum && lastclose) {
						baseNode.petals.add(baseNode.next.next.indx);
						baseNode.next.next.petals.add(baseNode.indx);
						baseNode.next.next.petals.add(baseNode.prev.indx);
						
						// close up next/remove next from bdry chain
						baseNode.prev.petals.add(0,baseNode.next.next.indx);
						baseNode.next.petals.add(baseNode.next.petals.get(0));
						tmpVerts.add(bdry2tmp(baseNode.next));

						// fix pointers, but baseNode is unchanged
						baseNode.next=baseNode.next.next;
						baseNode.next.prev=baseNode.prev;
						break;
					}

					// otherwise, add petals as needed
					nodecount++;
					int presize=baseNode.prev.petals.size();
					if (presize==baseNode.prev.degree)
						throw new CombException(
							"previous node already has all its petals");
		
					baseNode.prev.petals.add(0,nodecount);
					baseNode.petals.add(nodecount);
					int tp=baseNode.type;
					int ptp=baseNode.prev.type;
					int newdegree=A;
					int newtype=1;
					if ((tp==2 && ptp==1) || (tp==1 && ptp==2)) {
						newdegree=C;
						newtype=3;
					}
					else if ((tp==1 && ptp==3) || (tp==3 && ptp==1)) {
						newdegree=B;
						newtype=2;
					}
					else if ((tp==2 && ptp==3) || (tp==3 && ptp==2)) {
						newdegree=A;
						newtype=1;
					}
					else
						throw new CombException(
							"error, nodecount "+nodecount+
							" sees types: deg/pdeg="+tp+"/"+ptp);
				
					BdryNode newNode=new BdryNode(nodecount,newtype,newdegree,generation,
						baseNode.prev,baseNode);
		
					// new petal
					newNode.petals=new ArrayList<Integer>(2);
					newNode.petals.add(baseNode.indx);
					newNode.petals.add(baseNode.prev.indx);
						// fix pointers
					baseNode.prev.next=newNode;
					baseNode.prev=newNode;

					// didn't have to close next? then add last edge
					if (j==newnum) {
						baseNode.next.petals.add(nodecount);
						baseNode.prev.petals.add(0,baseNode.next.indx);
					}
				}
			}
			
			// no new petals to add? finish last edge 
			else {
				baseNode.next.petals.add(baseNode.prev.indx);
				baseNode.prev.petals.add(0,baseNode.next.indx);
			}
			
			// now close/save the flower
			baseNode.petals.add(baseNode.petals.get(0));
			tmpVerts.add(bdry2tmp(baseNode));
				
			// check for final completion; not clear if this can happen
			if (baseNode.prev==baseNode.next) {
				baseNode.petals.add(baseNode.petals.get(0));
				baseNode.next.petals.add(baseNode.next.petals.get(0));
				tmpVerts.add(bdry2tmp(baseNode));
				tmpVerts.add(bdry2tmp(baseNode.next));
				baseNode=null;
			}
			else { // shift 'baseNode' cclw
				baseNode.prev.next=baseNode.next;
				baseNode.next.prev=baseNode.prev;
				baseNode=baseNode.next;  // baseNode.printBdry();
			}
		
		} // done with all generations
		
		// get remaining partial flowers
		if (baseNode!=null) {
			BdryNode bnode=baseNode;
			do {
				tmpVerts.add(bdry2tmp(bnode));
				bnode=bnode.next;
			} while(bnode!=baseNode);
		}
		
		int[][] bouquet=new int[nodecount+1][];
		int[] vertTypes=new int[nodecount+1];
		Iterator<TmpVert> tvis=tmpVerts.iterator();
		int tick=0;
		while (tvis.hasNext()) {
			TmpVert tv=tvis.next();
			int v=tv.indx;
			bouquet[v]=tv.flower;
			vertTypes[v]=tv.type;
			tick++;
		}
		
		// check that counts agree
		if (tick!=nodecount)
			throw new CombException(
				"nodecount and tmpVerts size don't agree");
		
		// create the packing // bouquet[84][0]=16;
		PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet,1);
		PackData newPack=new PackData(null);
		newPack.hes=hees;
		pdcel.fixDCEL(newPack);
		
		// mark the vertices 1,2,3 for A,B,C, resp.
		for (int v=1;v<=newPack.nodeCount;v++) {
			newPack.setVertMark(v,vertTypes[v]);
		}
		
		// set data
		newPack.set_aim_default();

		// sph, can compute 
		if (newPack.hes>0) { 
			double[] angs=new double[3];
			double[] coss=new double[3];
			double[] sins=new double[3];
			double[] len=new double[3];
			double[] rad=new double[3];

			angs[0]=2.0*Math.PI/(double)A; // angs[0]+angs[1]+angs[2]-Math.PI;
			angs[1]=2.0*Math.PI/(double)B; 
			angs[2]=2.0*Math.PI/(double)C;
			for (int j=0;j<3;j++) {
				coss[j]=Math.cos(angs[j]);
				sins[j]=Math.sin(angs[j]);
			}
			
			// from law of cosines: get lengths, then radii
			for (int j=0;j<3;j++) {
				int m=(j+1)%3;
				int n=(j+2)%3;
				len[j]=Math.acos(
					(coss[j]+coss[m]*coss[n])/(sins[m]*sins[n]));
			}
			rad[0]=(len[1]+len[2]-len[0])/2.0;
			rad[1]=(len[0]+len[2]-len[1])/2.0;
			rad[2]=(len[1]+len[0]-len[2])/2.0;
			for (int v=1;v<=newPack.nodeCount;v++) {
				int j=newPack.getVertMark(v)-1;
				newPack.setRadius(v,rad[j]);
			}
		}
		
		// eucl, can compute
		else if (newPack.hes==0) {
			double[] rad=new double[3];
			double[] angs=new double[3];
			angs[0]=Math.PI/(double)A;
			angs[1]=Math.PI/(double)B;
			angs[2]=Math.PI/(double)C;
			// law of sines for opposite lengths 
			double []len=new double[3];
			len[0]=1.0;
			double sina=Math.sin(angs[0]);
			len[1]=Math.sin(angs[1])/sina;
			len[2]=Math.sin(angs[2])/sina;
			// get radii and scale by .2
			rad[0]=.2*(len[1]+len[2]-len[0])/2.0;
			rad[1]=.2*(len[0]+len[2]-len[1])/2.0;
			rad[2]=.2*(len[1]+len[0]-len[2])/2.0;
			for (int v=1;v<=newPack.nodeCount;v++) {
				int j=newPack.getVertMark(v)-1;
				newPack.setRadius(v,rad[j]);
			}
		}
		  
		// hyp, set up for max_pack
		else { 
			for (int v=1;v<=newPack.nodeCount;v++) {
				if (newPack.isBdry(v))
					newPack.setRadius(v,5.0); // horocycles
				else
					newPack.setRadius(v,.1);
			}
		}
		
		// set face colors blue/red oriented/reverse-oriented
		for (int f=1;f<=newPack.faceCount;f++) {
			combinatorics.komplex.DcelFace face=newPack.packDCEL.faces[f];
			HalfEdge he=face.edge;
			int a=newPack.getVertMark(he.origin.vertIndx);
			int b=newPack.getVertMark(he.twin.origin.vertIndx);
			if ((a==1 && b==2) || (a==2 && b==3) || (a==3 && b==1))
				face.color=ColorUtil.cloneMe(Color.blue);
			else
				face.color=ColorUtil.cloneMe(Color.red);
		}
		
		
		return newPack;
	}
	
	/**
	 * Store a BdryNode as a TmpVert
	 */
	public static TmpVert bdry2tmp(BdryNode bnode) {
		int[] flower=new int[bnode.petals.size()];
		for (int j=0;j<bnode.petals.size();j++)
			flower[j]=bnode.petals.get(j);
		return new TmpVert(bnode.indx,bnode.type,flower);
	}
	
}

/**
 * inner class for holding vertices as their flowers
 * are completed.
 * @author kstephe2
 */
class TmpVert {
	public int indx;
	public int type;
	public int[] flower;
	
	// Constructon
	public TmpVert(int idx,int tp,int[] fwr) {
		indx=idx;
		type=tp;
		flower=fwr;
	}
	

}
 
/**
 * inner class for holding boundary nodes during triangle group
 * constructions. 
 * @author kstephe2
 *
 */
class BdryNode {
	int indx;
	int type; // 1, 2, or 3
	int degree; // A, B, or C
	int generation;
	BdryNode prev;
	BdryNode next;
	ArrayList<Integer> petals; // cclw order petals
	
	// Constructor
	public BdryNode(int idx,int tp,int deg,int gen,BdryNode prv,BdryNode nxt) {
		indx=idx;
		type=tp;
		degree=deg;
		generation=gen;
		prev=prv;
		next=nxt;
		petals=new ArrayList<Integer>();
	}
	
	public void printBdry() {
		int safety=1000;
		BdryNode btrace=this;
		StringBuilder strbld=new StringBuilder("Bdry indx's\n");
		do {
			safety--;
			strbld.append(" "+btrace.indx+" ("+btrace.degree+")");
			btrace=btrace.next;
		} while (btrace!=null && btrace!=this && safety>0);
		if (btrace==this)
			strbld.append(" "+btrace.indx+" ("+btrace.degree+") = closed up");
		System.out.println(strbld.toString());
	}
	
}

