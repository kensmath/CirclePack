package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import complex.Complex;
import complex.MathComplex;
import deBugging.DCELdebug;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import ftnTheory.GenModBranching;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.NodeLink;
import packing.PackData;
import tiling.Tile;
import tiling.TileData;

/**
 * Move things from 'PackCreation' as they are converted
 * to be based on DCEL structures.
 * @author kens, July 2021
 *
 */
public class DcelCreation {

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
		pdcel.fixDCEL_raw(p);
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
		PackData workPack=DcelCreation.seed(6, 0);
		PackDCEL pdcel=workPack.packDCEL;
		RawDCEL.addVert_raw(pdcel,5);
		RawDCEL.addVert_raw(pdcel,2);
		// Note: "pointy" verts: 
		//    top right = nodeCount, bottom left = nodeCount-1. 
		int top=pdcel.vertCount;
		int bottom=pdcel.vertCount-1;
		
		// Add mn-2 layers from bottom cclw to top
		int sz=2; // start with the 2x2 already built
		while (sz<mn) {
			int w=workPack.getLastPetal(top);
			int v=workPack.getFirstPetal(bottom);
			workPack.add_layer(1,6,v,w);
			
			// add the new pointy ends
			RawDCEL.addVert_raw(pdcel,top+1); // new bottom
			bottom=pdcel.vertCount;
			RawDCEL.addVert_raw(pdcel,top); // new top
			top=pdcel.vertCount;
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
			workPack.add_layer(1,6,w,th);
			RawDCEL.addVert_raw(pdcel,top); // new pointy top
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
	
		if (!debug) { // debug=true;
			// adjoin right edge to left edge for annulus
			pdcel=CombDCEL.d_adjoin(pdcel, pdcel, top, topleft, mn);
			top=pdcel.oldNew.findW(top);
			bottom=pdcel.oldNew.findW(bottom);
		}
		
		if (!debug) { // debug=true;
			// adjoin top and bottom edges
			pdcel=CombDCEL.d_adjoin(pdcel,pdcel,top,bottom,mx);
		}
		
		pdcel.fixDCEL_raw(workPack);
		return workPack;
	}

	/**
	 * Create a packing of hex generations, spiraling out. 
	 * Calling routine to set radii/centers. (See also 
	 * 'GridMethods.HexByHand' for a generating a uniform 
	 * hex grid based on coord locations.) 
	 * @param n int, number of generations (seed is 1 generation)
	 * @return @see PackData
	 */
	public static PackData hexBuild(int n) {
		if (n == 0)
			n = 1;
		double rad = 1.0 / (2.0 * n);
		PackDCEL pdcel = CombDCEL.seed_raw(6);
		PackData p = new PackData(null);
		pdcel.p = p;
		CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha, false);
		for (int k = 2; k <= n; k++) {
			int m = pdcel.vertCount;
			int ans = RawDCEL.addlayer_raw(pdcel, 1, 6, m, m);
			if (ans <= 0)
				return null;
		}

		boolean debug = false; // debug=true;
		if (debug)
			DCELdebug.printRedChain(pdcel.redChain);

		pdcel.fixDCEL_raw(p);
		for (int v = 1; v <= p.nodeCount; v++)
			p.setRadius(v, rad);
		return p;
	}

	/** 
	 * Create a 'seed' packing from scratch (with CPScreen=null)
	 * NOTE: 'PackData.seed' call does the same, but within existing
	 * PackData object.
	 * @param n int, number of petals
	 * @param heS int, final geometry
	 * @return @see PackData or null on error
	 */
	public static PackData seed(int n,int heS) {
		if (n<3)
			throw new ParserException("'seed' usage: n must be at least 3");
		PackDCEL pdcel=CombDCEL.seed_raw(n);
		pdcel.gamma=pdcel.alpha.twin;
		PackData p=new PackData(null);
		pdcel.fixDCEL_raw(p);
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
		PackDCEL base=CombDCEL.seed_raw(5);
		RawDCEL.swapNodes_raw(base,1,6);
		CombDCEL.redchain_by_edge(base, null,null,false);
		CombDCEL.d_FillInside(base);
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
			pdcel=CombDCEL.d_adjoin(pdcel,temp,1,3,sidelength);
			int new5=pdcel.oldNew.findW(5); // new index
			CombDCEL.d_FillInside(pdcel); 

			// DCELdebug.redindx(btrfly);
		
			// these two form a butterfly
			PackDCEL btrfly=CombDCEL.cloneDCEL(pdcel);
			PackDCEL btrfly2=CombDCEL.cloneDCEL(pdcel); // copy for next step
			pdcel=CombDCEL.d_adjoin(pdcel,btrfly,3,4,3*sidelength);
			
			// adjoin this for two more faces
			pdcel=CombDCEL.d_adjoin(pdcel,btrfly2,new5,3,4*sidelength);
			CombDCEL.d_FillInside(pdcel);
		
			// find 5 cclw bdry vertices with degree 3.
			NodeLink corners=new NodeLink();
			RedHEdge rtrace=pdcel.redChain;
			
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
				RawDCEL.swapNodes_raw(pdcel,v,oldv);
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
		newDCEL.fixDCEL_raw(p);
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
	 * Create generations of the Cayley graph of a triangle 
	 * group; A, B, C are the degrees of the vertices. Geometry
	 * is determined by r=2*(1/A+1/B+1/C). if r>1 sph;
	 * r=1 eucl; and r<1 hyp. If one of A/B/C is odd, then the
	 * others must be equal.
	 * @param A int
	 * @param B int
	 * @param C int
	 * @param maxgen int
	 * @return PackData
	 */
	public static PackData triGroup(int A, int B, int C, int maxgen) {
		int []degs=new int[3];
		degs[0]=A;
		degs[1]=B;
		degs[2]=C;
		
		// what geometry?
		int hees=-1; // default: hyp
		double recipsum
			=2.0/(double)(A)+2.0/(double)(B)+2.0/(double)(C);
		if (Math.abs(recipsum-1)<.0001)
			hees=0; // eucl
		else if (recipsum>1.0) 
			hees=1; // sph
		  
		PackData pdata=new PackData(null); // eventually need this
		PackDCEL pdcel=CombDCEL.seed_raw(A);
		CombDCEL.redchain_by_edge(pdcel, null, null, false);
		CombDCEL.d_FillInside(pdcel);
		int gencount=1;
	
		// mark bdry vertices of first flower; alternate, B,C
	   	for (int j=2;j<=pdcel.vertCount;j++) {
	   		Vertex vert=pdcel.vertices[j];
	   		if (vert.bdryFlag>0)
	   			vert.vutil=(j)%2+1;
	   	}
	
	   	while (gencount<=maxgen) { // DCELdebug.printRedChain(pdcel.redChain);
   			int []bdryinfo=ck_redchain(pdcel); 
	   		if (bdryinfo==null)
	   			throw new CombException("no 'redChain' exists at gencount = "+
	   					gencount);
	   		// sph? need to check to close bdry 
	   		if (hees>0) {
	   			// one more vert needed; shortage of bdry vertices, so close up 
	   			if (bdryinfo[1]==bdryinfo[2] && bdryinfo[2]>=bdryinfo[0]) {
	   				int rslt=RawDCEL.addIdeal_raw(pdcel);
	   				if (rslt==0)
	   					throw new DCELException("in 'triGroup', 'addIdeal' failed");
	   				pdcel.fixDCEL_raw(pdata);
	   				CirclePack.cpb.msg("triG: closed as sphere:");
	   				if (bdryinfo[0]<bdryinfo[2] || bdryinfo[1]!=bdryinfo[2])
	   					CirclePack.cpb.errMsg(" final degree, "+
	   							pdata.countFaces(pdata.nodeCount)+
	   							" is inconsistent");
	   				else 
	   					CirclePack.cpb.msg("last degree "+
	   							pdata.countFaces(pdata.nodeCount));
	   				return pdata;
	   			}
	   			//	three bdry edge? May need one last face.
	   			if (bdryinfo[0]==3) { 
	   				RedHEdge rtrace=pdcel.redChain;
	   				int[] uhits=new int[3];
	   				do {
	   					int k = pdcel.vertices[rtrace.myEdge.origin.vertIndx].vutil;
	   					uhits[k]=1;
	   					rtrace=rtrace.nextRed;
	   				} while (rtrace!=pdcel.redChain);
	   				// vertices, one mark for each of A, B, C
	   				if (uhits[0]==1 && uhits[1]==1 && uhits[2]==1) {
	   					int rslt=RawDCEL.addIdealFace_raw(pdcel,
	   							pdcel.redChain.myEdge.twin.face);
	   					if (rslt!=1)
	   						throw new CombException(
	   								"Failed with final triangular ideal face");
		   				pdcel.fixDCEL_raw(pdata);
		   				CirclePack.cpb.msg("triG: closed as sphere:");
		   				return pdata;
	   				}
	   			}
	   		}
	   		
	   		RedHEdge rtrace=pdcel.redChain;
	   		int lastVert=rtrace.prevRed.myEdge.origin.vertIndx;
//	   		RedHEdge stopred=rtrace.prevRed.prevRed;
	   		RedHEdge nxtred=rtrace.nextRed;
	   		boolean wflag=false; // stop signal
	   		int count=1;
	   		while (!wflag && count<10000) {
	   			nxtred=rtrace.nextRed;
	   			int w=rtrace.myEdge.origin.vertIndx;
	   			if (w==lastVert)
	   				wflag=true;
	   			int prev=rtrace.prevRed.myEdge.origin.vertIndx;
	   			int n=degs[pdcel.vertices[w].vutil]-pdcel.vertices[w].getNum()-1;
	   			if (n<-1)
	   				throw new CombException("violated degree at vert "+w);
	
	   			// add the n circles; two marks alternate around w
	   			int []alt=new int[2];
	   			alt[0]=pdcel.vertices[prev].vutil;
	   			int vec=(alt[0]-pdcel.vertices[w].vutil+3)%3;
	   			alt[1]=(alt[0]+vec)%3;
	   			for (int i=1;i<=n;i++) {
	   				// for sph case, check added vert to see if it's last
	   				if (hees>0) {
	   		   			bdryinfo=ck_redchain(pdcel);
	   					if (bdryinfo==null) {
	   						pdcel.fixDCEL_raw(pdata);
	   						CirclePack.cpb.errMsg("triG, improper wrapup");
	   						return pdata;
	   					}
	   					if (bdryinfo[1]==bdryinfo[2] && bdryinfo[2]>=bdryinfo[0]) {
	   						int rslt=RawDCEL.addIdeal_raw(pdcel);
	   		   				if (rslt==0)
	   		   					throw new DCELException(
	   		   							"in 'triGroup', 'addIdeal' failed");
	   						pdcel.fixDCEL_raw(pdata);
	   						CirclePack.cpb.msg("triG: closed as sphere:");
	   						if (bdryinfo[0]<bdryinfo[1])
	   							CirclePack.cpb.errMsg(" final degree of "+
	   									pdcel.vertices[w].getNum()+" is too small");
	   						else 
	   							CirclePack.cpb.msg(" final degree is correct, "+
	   									bdryinfo[1]);
	   						return pdata;
	   					}
	   				} // done with sphere check
	   				
	   				Vertex newV=RawDCEL.addVert_raw(pdcel,w);
	   				newV.vutil=alt[i%2];
	   				// for sph, check whether to close up
	   				if (hees>0) {
	   		   			bdryinfo=ck_redchain(pdcel);
	   		   			if (bdryinfo[1]==bdryinfo[2] && bdryinfo[2]>=bdryinfo[0]) {
	   		   				int rslt=RawDCEL.addIdeal_raw(pdcel);
	   		   				if (rslt==0)
	   		   					throw new DCELException(
	   		   							"in 'triGroup', 'addIdeal' failed");
	   		   				pdcel.fixDCEL_raw(pdata);
	   		   				CirclePack.cpb.msg("triG: closed as sphere:");
	   		   				if (bdryinfo[0]<bdryinfo[2] || bdryinfo[1]!=bdryinfo[2])
	   		   					CirclePack.cpb.errMsg(" final degree, "+
	   		   							pdata.countFaces(pdata.nodeCount)+
	   		   							" is inconsistent");
	   		   				else 
	   		   					CirclePack.cpb.msg("last degree "+
	   		   							pdata.countFaces(pdata.nodeCount));
	   		   				return pdata;
	   		   			}
	   				}
	   			}
	   			if (n==-1) {
	   				
// debugging
//	   				System.out.println("next zip is "+w);
	   				
	   				int rslt=CombDCEL.zipEdge(pdcel,pdcel.vertices[w]);
	   				if (rslt==0) {
	   					pdcel.fixDCEL_raw(pdata);
	   					CirclePack.cpb.errMsg("problem zipping at vertex "+w);
	   					return pdata;
	   				}
	   				else if (rslt>0) { // 'rslt' is orphaned vertex
	   					ArrayList<Vertex> newVs=new ArrayList<Vertex>();
	   					for (int v=1;v<=pdcel.vertCount;v++) {
	   						Vertex vert=pdcel.vertices[v];
	   						if (vert.halfedge!=null) {
	   							newVs.add(vert);
	   						}
	   					}
	   					if (newVs.size()!=pdcel.vertCount-1)
	   						throw new CombException(
	   								"count of remaining vertices is off");
	   					Iterator<Vertex> vis=newVs.iterator();
	   					int vtick=0;
	   					while (vis.hasNext()) {
	   						Vertex vert=vis.next();
	   						vert.vertIndx=++vtick; 
	   						pdcel.vertices[vtick]=vert;
	   					}
	   					pdcel.vertCount=vtick;
	   				}
		   			
	   				if (pdcel.redChain==null) { // closed up?
	   					pdcel.fixDCEL_raw(pdata);
	   					CirclePack.cpb.errMsg("With 'zip' at vert "+w+
	   							" we have closed up the complex");
	   					return pdata;
	   				}
	   			}
	   			
	   			else {
	   				RawDCEL.enfold_raw(pdcel,w);
	   				if (pdcel.redChain==null) {// closed up?
	   					pdcel.fixDCEL_raw(pdata);
	   					CirclePack.cpb.errMsg("With 'enfold' at vert "+w+
	   							" we have closed up the complex");
	   					return pdata;
	   				}
	   			}
	   			count++;
	   			rtrace=nxtred;
	   		} // end of while around bdry
	   		gencount++;
	   	} // end of while on generations 

	   	pdcel.fixDCEL_raw(pdata);
	
		// eucl cases, can compute radii
	   	if (hees==0) {
		   	double []rad=new double[3];
	   		// law of sines for opposite lengths 
	   		double []len=new double[3];
	   		double m2pi=Math.PI*2.0;
	   		len[0]=1.0;
	   		double sina=Math.sin(m2pi/(double)degs[0]);
	   		len[1]=Math.sin(m2pi/(double)degs[1])/sina;
	   		len[2]=Math.sin(m2pi/(double)degs[2])/sina;
	   		// get radii and scale by .2
	   		rad[0]=.2*(len[1]+len[2]-len[0])/2.0;
	   		rad[1]=.2*(len[0]+len[2]-len[1])/2.0;
	   		rad[2]=.2*(len[1]+len[0]-len[2])/2.0;
	   		for (int v=1;v<=pdata.nodeCount;v++) {
	   			Vertex vert=pdata.packDCEL.vertices[v];
	   			pdata.setRadius(v,rad[vert.vutil]);
	   		}
	   	}
	
	   	return pdata;
	}

	/** 
	 * Create N generations of Conway's "pinwheel" combinatorics.
	 * Need to specify number of edges of "end" (short) leg, 
	 * number for "long" leg is twice this, number on "hypotenuse"
	 * is independent.
	 * 
	 * The packing is made into euclidean right triangle, with
	 * other angles 
	 * atan(.5) = 0.463647609 = .14758362*pi and 
	 * atan(2) = 1.107148717794 =.352416382*pi
	 * 
	 * @param N int: generations N>=1, 1 means single flower.
	 * @param e int, edge count on "end" leg
	 * @param h int, edge count on "hypotenuse"
	 * @return PackData with vlist of tile centers.
	 */
	public static PackData pinWheel(int N,int e,int h) {
		int generation=1; // number of generations in current build
		boolean debug=false; 
		// pinwheel starts as n-flower, where n=e+2*e+h, with v=1 swapped to
		//     be on the boundary; vertices to keep track of are 1, 2, 3. 
		//     Distance from 1 to 2 is e, from 2 to 3 is h, 
		//     hence from 3 to 1 is 2*e. Long leg is always twice the end leg
		
		PackData growWheel = DcelCreation.seed(3*e+h, 0);
		PackDCEL pdcel=growWheel.packDCEL;
		pdcel.swapNodes(3*e+h+1, 1);
		pdcel.setAlpha(3*e+h+1, null,false);
		growWheel.vlist=new NodeLink();
		growWheel.vlist.add(3*e+h+1); // list center verts of tiles added
		pdcel.swapNodes(1+e,2);
		pdcel.swapNodes(1+e+h,3);
		growWheel.elist=new EdgeLink(growWheel,"b");
		
		// mark the boundary
		for (int vi=1;vi<growWheel.nodeCount;vi++) {
			int om=growWheel.getVertMark(vi);
			growWheel.setVertMark(vi,om+1);
		}
		
		// want to mark the smallest level "core" (middle triangle)
		//    with -1 and it's rotated neighbor with -2;
		if (N<=1) { // at first level, just the core
			growWheel.setVertMark(growWheel.nodeCount,-2);
			N=1;
		}
		
		// keep track of number of edges in 'end', 'long', 'hypotenuse'
		int endcount=e;
		int longcount=2*e;
		int hypcount=h;

		while (generation < N) {
			
			if (N==1) // unmark the center on the first run
				growWheel.setVertMark(growWheel.nodeCount,0);
			
			// 5 copies of tempPack and tempReverse are adjoined
			PackData tempPack=growWheel.copyPackTo();
			PackData tempReverse=growWheel.copyPackTo();
			tempReverse.packDCEL.reverseOrientation();
			
			tempPack.vlist=growWheel.vlist.makeCopy();
			tempReverse.vlist=growWheel.vlist.makeCopy();
			
			tempPack.elist=new EdgeLink(tempPack,"b");
			tempReverse.elist=new EdgeLink(tempReverse,"b");
						
			// A serves as the base:
			// adjoin B^ to A along end 
			//        2 on A to 2 on B^, endcount edges
			//        don't need to mark any vertices
			pdcel=CombDCEL.d_adjoin(pdcel,tempReverse.packDCEL,2,2,endcount);
			growWheel.vertexMap=pdcel.oldNew;
			updateLists(growWheel,tempReverse.vlist,tempReverse.elist,
					growWheel.vertexMap);
			
			// transfer all the marks as each piece is adjoined
			Iterator<EdgeSimple> vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempReverse.getVertMark(edge.v));
			}
			
			// this new part is the rotated core;
			//    first pass only, mark its center vert with -2
			if (generation==1) 
				growWheel.setVertMark(growWheel.vertexMap.findW(tempReverse.nodeCount),-2);

			// for debugging edge lists: debug=false;
			if (debug) {
				Iterator<EdgeSimple> es=growWheel.elist.iterator();
				System.err.println("\n add upper left");
				while (es.hasNext()) {
					EdgeSimple edge=es.next();
					System.err.println("("+edge.v+" "+edge.w+")");
				}
				DebugHelp.debugPackWrite(growWheel,"growWheel.p");
			}
			
			// adjoin C^ to B^ along hyp 
			//		  2 on A+B^ to 3 on C^, hypcount edges
			//        don't need to mark any vertices
			//        identify 'alpha' of 'growWheel' as alpha of C
			pdcel=CombDCEL.d_adjoin(pdcel,tempReverse.packDCEL,2,3,hypcount);
			growWheel.vertexMap=pdcel.oldNew;
			updateLists(growWheel,tempReverse.vlist,tempReverse.elist,growWheel.vertexMap);
			pdcel.setAlpha(growWheel.vertexMap.findW(tempReverse.getAlpha()),null,false);
			vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempReverse.getVertMark(edge.v));
			}
			
			// this new part is the core, mark its center with -1 (first pass only)
			if (generation==1)
				growWheel.setVertMark(growWheel.vertexMap.findW(tempReverse.nodeCount),-1);

						
			// adjoin D to C^ along long 
			//        2 on A+B^+C^ to 3 on D, longcount edges
			//        X keeps track of old 2.
			pdcel = CombDCEL.d_adjoin(pdcel,tempPack.packDCEL,2,3,longcount);
			growWheel.vertexMap=pdcel.oldNew;
			updateLists(growWheel,tempPack.vlist,tempPack.elist,growWheel.vertexMap);
			int X=growWheel.vertexMap.findW(2);
			vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempPack.getVertMark(edge.v));
			}

			// adjoin E ends of C^ and D, endcount each
			//        X on A+B^+C^+D to 3 on E
			//        Y keeps track of old 2.
			pdcel=CombDCEL.d_adjoin(pdcel,tempPack.packDCEL,X,3,longcount);
			growWheel.vertexMap=pdcel.oldNew;
			updateLists(growWheel,tempPack.vlist,tempPack.elist,growWheel.vertexMap);
			int Y=growWheel.vertexMap.findW(2);
			vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempPack.getVertMark(edge.v));
			}
			
			
			// renumber: X --> 1, Y --> 2
			pdcel.swapNodes(X,1);
			pdcel.swapNodes(Y,2);
			
			// side lengths follow recursion formula
			int holdhyp=hypcount;
			hypcount=5*endcount;
			endcount=holdhyp;
			longcount=2*endcount;
			
			// increment marks on boundary
			for (int vi=1;vi<=growWheel.nodeCount;vi++)
				if (growWheel.isBdry(vi)) {
					int om=growWheel.getVertMark(vi);
					growWheel.setVertMark(vi,om+1);
				}
			
			// new generation is reverse oriented
			growWheel.packDCEL.reverseOrientation();
			pdcel.fixDCEL_raw(growWheel);

			generation++;

		} // end of while
		
		// set the aims to make it a right triangle
		growWheel.set_aim_default();
		for (int v=1;v<=growWheel.nodeCount;v++) {
			if (growWheel.isBdry(v))
				growWheel.setAim(v,1.0*Math.PI);
		}
		growWheel.setAim(1,.5*Math.PI); 
		growWheel.setAim(3,Math.atan(.5)); // 0.463647609 
		growWheel.setAim(2,.5*Math.PI-growWheel.getAim(3)); // 0.463647609, 1.107148717794

		// repack, layout
		double crit=GenModBranching.LAYOUT_THRESHOLD;
		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		growWheel.fillcurves();
		growWheel.repack_call(1000);
		try {
			growWheel.packDCEL.layoutPacking(); 
		} catch (Exception ex) {
			throw new CombException("'pinWheel' creation failed");
		}
		
		// normalize: 2 3 horizontal, 3 on unit circle.
		Complex z=growWheel.getCenter(3).minus(growWheel.getCenter(2));
		double ang=(-1.0)*(MathComplex.Arg(z));
		growWheel.rotate(ang);
		double scl=growWheel.getCenter(3).abs();
		if (scl>.000001)
			growWheel.eucl_scale(1.0/scl);

		try {
			growWheel.tileData=TileData.paveMe(growWheel,growWheel.getAlpha());
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create pinWheel 'TileData'");
		}
		return growWheel;
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
		PackData fusionA = DcelCreation.seed(2*W+2*H,0); // A is W x H
		PackData fusionB = DcelCreation.seed(2*W+2*X,0); // B is W x X
		PackData fusionC = DcelCreation.seed(2*H+2*X,0); // C is X x H
		PackData fusionD = DcelCreation.seed(4*X,0);     // D is X x X

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
			fusionA.packDCEL=CombDCEL.d_adjoin(fusionA.packDCEL,
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
			lower.packDCEL=CombDCEL.d_adjoin(lower.packDCEL,
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
			fusionA.packDCEL=CombDCEL.d_adjoin(fusionA.packDCEL,
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
			fusionB.packDCEL=CombDCEL.d_adjoin(fusionB.packDCEL,
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
			fusionC.packDCEL=CombDCEL.d_adjoin(fusionC.packDCEL,
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
		RedHEdge rtrace=pdc.redChain;
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
	
}

