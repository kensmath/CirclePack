package packing;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import allMains.CirclePack;
import complex.Complex;
import complex.MathComplex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.RawDCEL;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.ParserException;
import ftnTheory.GenBranching;
import input.CommandStrParser;
import komplex.EdgeSimple;
import komplex.KData;
import listManip.EdgeLink;
import listManip.NodeLink;
import tiling.TileData;
import util.ColorUtil;

/**
 * For creation of packings from scratch, such as seeds, Cayley graphs
 * of triangle groups {a b c}, tilings, etc.
 * 
 * TODO: add creation calls for soccerball? perhaps other tiling patterns?
 * 
 * @author kens
 *
 */
public class PackCreation {

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
		RawDCEL.addVert_raw(pdcel,5);
		RawDCEL.addVert_raw(pdcel,2);
		// Note: "pointy" verts: 
		//    top right = nodeCount, bottom left = nodeCount-1. 
		int top=pdcel.vertCount;
		int bottom=pdcel.vertCount-1;
		
/*		workPack.add_vert(5);
		workPack.add_vert(2);
		workPack.setCombinatorics();
		// Note: "pointy" verts: 
		//    top right = nodeCount, bottom left = nodeCount-1. 
		int top=workPack.nodeCount;
		int bottom=workPack.nodeCount-1;
*/
		
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
			top=pdcel.newOld.findV(top);
			bottom=pdcel.newOld.findV(bottom);
//			CombDCEL.d_FillInside(pdcel);
		}
		
		if (!debug) { // debug=true;
//			top=pdcel.newOld.findV(top);
//			bottom=pdcel.newOld.findV(bottom);
			
			// TODO: side-pair problem when top=9 and bottom=6
			top=9;
			bottom=6;
			
			// adjoin top and bottom edges
			pdcel=CombDCEL.d_adjoin(pdcel,pdcel,top,bottom,mx);
		}
		
		pdcel.fixDCEL_raw(workPack);
		return workPack;

	}
	
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

		// When all radii are equal, every face is equilateral with angles 2pi/3.
		// Using spherical half-side formula, we get radii=arcsin(sqrt(2/3)).
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
	 * Create the Cayley graph of a triangle group; A, B, C are
	 * the degrees of the vertices. 
	 * @param A
	 * @param B
	 * @param C
	 * @param maxgen
	 * @return
	 */
	public static PackData triGroup(int A, int B, int C, int maxgen) {
		int []degs=new int[3];
		degs[0]=A;
		degs[1]=B;
		degs[2]=C;
		// geometry
		int hees=-1; // default: hyp
		double recipsum
			=2.0/(double)(degs[0])+2.0/(double)(degs[1])+2.0/(double)(degs[2]);
		if (Math.abs(recipsum-1)<.0001)
			hees=0; // eucl
		else if (recipsum>1.0) hees=1; // sph
		  
		PackData p=PackCreation.seed(A,hees);
		if (p==null) return null;
		
		int gencount=1;

		// mark vertices of first flower
	   	p.setVertMark(1,0);
	   	for (int j=2;j<=p.nodeCount;j++) {
	   		  p.setVertMark(j,(j)%2+1);
	   	}

   		double []rad=new double[3];
   		rad[0]=rad[1]=rad[2]=.2; // default

   		// eucl cases, can compute radii
	   	if (hees==0) {
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
	   		for (int k=1;k<=p.nodeCount;k++)
	   			p.setRadius(k,rad[p.getVertMark(k)]);
	   	}

	   	while (gencount<=maxgen) {
	   		NodeLink blink=new NodeLink(p,"b");
	   		if (blink==null || blink.size()==0)
	   			throw new CombException("no boundary verts at gencount = "+
	   					gencount);
	   		// sph? need to check to close bdry 
	   		if (hees>0) {
	   			int []bdryinfo=ck_bdry(p);
	   			
	   			// one more vert needed; shortage of bdry vertices, so close up 
	   			if (bdryinfo[1]==bdryinfo[2] && bdryinfo[2]>=bdryinfo[0]) {
	   				p.add_ideal(new NodeLink(p,"b"));
	   				p.setCombinatorics();
	   				CirclePack.cpb.msg("triG: closed as sphere:");
	   				if (bdryinfo[0]<bdryinfo[2] || bdryinfo[1]!=bdryinfo[2])
	   					CirclePack.cpb.errMsg(" final degree, "+
	   							p.countFaces(p.nodeCount)+" is inconsistent");
	   				else CirclePack.cpb.msg("last degree "+p.countFaces(p.nodeCount));
	   				return p;
	   			}
	   		}
	   		int w=p.bdryStarts[1];
	   		int stopv=p.kData[w].flower[p.countFaces(w)];
	   		int next=p.kData[w].flower[0];
	   		boolean wflag=false; // stop signaler
	   		int count=1;
	   		while (!wflag && count<10000) {
	   			if (w==stopv) wflag=true;
	   			int prev=p.kData[w].flower[p.countFaces(w)];
	   			int n=degs[p.getVertMark(w)]-p.countFaces(w)-1;
	   			if (n<-1)
	   				throw new CombException("violated degree at vert "+w);

	   			// add the n circles; two marks alternate around w
	   			int []alt=new int[2];
	   			alt[0]=p.getVertMark(prev);
	   			int vec=(alt[0]-p.getVertMark(w)+3)%3;
	   			alt[1]=(alt[0]+vec)%3;
	   			for (int i=1;i<=n;i++) {
	   				// for sph case, check added vert to see if it's last
	   				if (hees>0) {
	   					p.complex_count(true);
	   		   			int []bdryinfo=ck_bdry(p);
	   					if (bdryinfo[0]==0) {
	   						p.setCombinatorics();
	   						CirclePack.cpb.errMsg("triG, improper wrapup");
	   						return p;
	   					}
	   					if (bdryinfo[1]==bdryinfo[2] && bdryinfo[2]>=bdryinfo[0]) {
	   						p.add_ideal(new NodeLink(p,"b"));
	   						p.setCombinatorics();
	   						CirclePack.cpb.msg("triG: closed as sphere:");
	   						if (bdryinfo[0]<bdryinfo[1])
	   							CirclePack.cpb.errMsg(" final degree of "+
	   									p.countFaces(w)+" is too small");
	   						else CirclePack.cpb.msg(" final degree is correct, "+bdryinfo[1]);
	   						return p;
	   					}
	   				} // done with sphere check				   		
	   				p.add_vert(w);
	   				p.setVertMark(p.nodeCount,alt[i%2]);
	   				p.setRadius(p.nodeCount,rad[alt[i%2]]);
	   				// for sph, check whether to close up
	   				if (hees>0) {
	   					p.complex_count(false);
	   		   			int []bdryinfo=ck_bdry(p);
	   		   			if (bdryinfo[1]==bdryinfo[2]&& bdryinfo[2]>=bdryinfo[0]) {
	   		   				p.add_ideal(new NodeLink(p,"b"));
	   		   				p.setCombinatorics();
	   		   				CirclePack.cpb.msg("triG: closed as sphere:");
	   		   				if (bdryinfo[0]<bdryinfo[2] || bdryinfo[1]!=bdryinfo[2])
	   		   					CirclePack.cpb.errMsg(" final degree, "+
	   		   							p.countFaces(p.nodeCount)+" is inconsistent");
	   		   				else CirclePack.cpb.msg("last degree "+p.countFaces(p.nodeCount));
	   		   				return p;
	   		   			}
	   				}
	   			}
	   			if (n==-1) { // identify edges from w
	   				int xv=p.close_up(w); // if >0, a vertex was removed
	   				if (xv>0 && xv<=stopv) // then, reset stopv
	   					stopv--;
	   				if (xv>0 && xv<=next) // may have to reset next, too
	   					next--;
	   				p.complex_count(true);
	   				if (p.getBdryCompCount()==0) // closed up?
	   					return p;
	   			}
	   			else p.enfold(w);
	   			p.complex_count(true);
	   			w=next;
	   			next=p.kData[w].flower[0];
	   			count++;
	   		} // end of while
	   		gencount++;
		  } 
		  p.setCombinatorics();
		  return p;
	}

	/**
	 * Utility for bdry inspection. Find count and min/max 
	 * of intended degrees of circles that would be added.
	 * @param p
	 * @return ans[3]: 0=bdry count, 1=min deg, 2=max deg
	 */
	public static int []ck_bdry(PackData p) {
		int []ans=new int[3];
		NodeLink blink=new NodeLink(p,"b");
		if (blink==null || blink.size()==0)
			return ans;
		ans[0]=blink.size();
		ans[1]=100000; // minimum new mark
		ans[2]=0; // maximum new mark
		int mx=0;
		int mn=100000;
		Iterator<Integer> blk=blink.iterator();
		while (blk.hasNext()) {
			int w=blk.next();
			int next=p.kData[w].flower[0];
			int vec=(p.getVertMark(w)-p.getVertMark(next)+3)%3;
			int nxmk=(p.getVertMark(w)+vec)%3; // mark an added vert would have
			mx=(nxmk>mx) ? nxmk : mx;
			mn=(nxmk<mn) ? nxmk : mn;
		} 
		ans[1]=mn;
		ans[2]=mx;
		return ans;
	}
	
	/** 
	 * Create a 'seed' packing from scratch (with CPScreen=null)
	 * NOTE: 'PackData.seed' call does the same, but within existing
	 * PackData object.
	 * @param n int, number of petals
	 * @param heS int, geometry
	 * @return @see PackData or null on error
	 */
	public static PackData seed(int n,int heS) {
		PackDCEL pdcel=CombDCEL.seed_raw(n);
		pdcel=CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha);
		CombDCEL.d_FillInside(pdcel);
		pdcel.gamma=pdcel.alpha.twin;
		PackData p=new PackData(null);
		p.attachDCEL(pdcel);
		p.status=true;
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
	 * Create a packing of hex generations
	 * @param n int, number of generations (seed is 1 generation)
	 * @return @see PackData
	 */
	public static PackData hexBuild(int n) {
		PackDCEL pdcel=CombDCEL.seed_raw(6);
		PackData p=new PackData(null);
		pdcel.p=p;
		pdcel=CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha);
		for (int k=2;k<=n;k++) {
			int m=pdcel.vertCount;
			int ans=RawDCEL.addlayer_raw(pdcel,1,6,m,m);
//			pdcel=CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha);
			if (ans<=0)
				return null;
		}
		pdcel.fixDCEL_raw(p);
		return p;
	}
			
/*		}
		PackData newPack=PackCreation.seed(6,0);
		for (int j=1;j<n;j++) {
			int v=newPack.bdryStarts[1];
			newPack.add_layer(1,6,v,v);
			newPack.setBdryFlags();
		}
		newPack.setCombinatorics();
		
		// I prefer that the real line be a hex axis, so choose 'gamma'
		if (newPack.nodeCount>50)
			newPack.gamma=43;
		else if (newPack.nodeCount>10)
			newPack.gamma=10;
		
		CommandStrParser.jexecute(newPack,"layout");
		
		return newPack;
	}
*/
	
	/**
	 * TODO: this code isn't called yet, should replace 'hexbuild' sometimes.
	 * Create hex packing as in 'hexBuild', but by direct build
	 * rather than adding generations in succession. Will be faster
	 * and for large packings more accurate layout. Vert numbering 
	 * does not spiral out so nicely.
	 * The hex grid here is identified with span of independent vectors
	 * u=<1/2,-sqrt(3)/2> and w=<1/2,sqrt(3)/2>, so <i,j> is i*u+j*w.
	 * We also set up translation info in 'micro2v' and 'v2micro', but
	 * that is difficult to pass back to the calling routine.
	 * Alpha is set to center vertex and gamma so positive x-axis goes through u+w.
	 * @param n int, number of generations (seed is 1 generation)
	 * @return @see PackData
	*/
	public PackData hexByHand(int n) {
		PackData newPack=new PackData(null);
		newPack.status=true;
		newPack.locks=0;
		newPack.activeNode=1;
		newPack.hes=0;
		
		// prepare KData and RData
		KData []kData;
		RData []rData;
		int nodecount=3*n*n+3*n+1;
		kData=new KData[nodecount+1];
		rData=new RData[nodecount+1];
		for (int v=1;v<=nodecount;v++) {
			kData[v]=new KData();
			rData[v]=new RData();
			rData[v].rad=0.5;
			rData[v].aim=2*Math.PI;
		}
		
		// allocate 'micro2v' and 'v2micro'
		int [][]micro2v=new int[2*n+1][];
		for (int i=0;i<=2*n;i++)
			micro2v[i]=new int[2*n+1];
		int [][]v2micro=new int[nodecount+1][];
		for (int kv=0;kv<=nodecount;kv++)
			v2micro[kv]=new int[2];

		int vtick=0;
		double sq32=Math.sqrt(3.0)/2.0;
		Complex uz=new Complex(0.5,-sq32);
		Complex wz=new Complex(0.5,sq32);
		for (int u=0;u<=n;u++) 
			for (int w=0;w<=n+u;w++) {
				micro2v[u][w]=++vtick;
				v2micro[vtick][0]=u-n;
				v2micro[vtick][1]=w-n;
				rData[vtick].center=uz.times(u-n).add(wz.times(w-n));
			}
		for (int u=1;u<=n;u++)
			for (int w=u;w<=2*n;w++) {
				micro2v[n+u][w]=++vtick;
				v2micro[vtick][0]=u;
				v2micro[vtick][1]=w-n;
				rData[vtick].center=uz.times(u).add(wz.times(w-n));
			}
		
		// prepare flowers
		// helpful stencil
		EdgeSimple []hexstencil=new EdgeSimple[7];
		hexstencil[0]=hexstencil[6]=new EdgeSimple(1,0);
		hexstencil[1]=new EdgeSimple(1,1);
		hexstencil[2]=new EdgeSimple(0,1);
		hexstencil[3]=new EdgeSimple(-1,0);
		hexstencil[4]=new EdgeSimple(-1,-1);
		hexstencil[5]=new EdgeSimple(0,-1);
		
		// interiors
		int v=0;
		for (int u=1;u<=n;u++) 
			for (int w=1;w<n+u;w++) {
				v=micro2v[u][w];
				kData[v].num=6;
				kData[v].flower=new int[7];
				for (int j=0;j<6;j++) {
					EdgeSimple edge=hexstencil[j];
					int uu=u+edge.v;
					int ww=w+edge.w;
					kData[v].flower[j]=micro2v[uu][ww];
				}
				kData[v].flower[6]=kData[v].flower[0];
			}
		for (int u=1;u<n;u++) 
			for (int w=u+1;w<2*n;w++) {
				v=micro2v[n+u][w];
				kData[v].num=6;
				kData[v].flower=new int[7];
				for (int j=0;j<6;j++) {
					EdgeSimple edge=hexstencil[j];
					int uu=n+u+edge.v;
					int ww=w+edge.w;
					kData[v].flower[j]=micro2v[uu][ww];
				}
				kData[v].flower[6]=kData[v].flower[0];
			}
		
		// corners
		v=micro2v[0][0]; // lower left
		kData[v].num=2;
		kData[v].bdryFlag=1;
		kData[v].flower=new int[3];
		kData[v].flower[0]=micro2v[1][0];
		kData[v].flower[1]=micro2v[1][1];
		kData[v].flower[2]=micro2v[0][1];
		
		v=micro2v[n][0]; // bottom
		kData[v].num=2;
		kData[v].bdryFlag=1;
		kData[v].flower=new int[3];
		kData[v].flower[0]=micro2v[n+1][1];
		kData[v].flower[1]=micro2v[n][1];
		kData[v].flower[2]=micro2v[n-1][0];

		v=micro2v[2*n][n]; // lower right
		kData[v].num=2;
		kData[v].bdryFlag=1;
		kData[v].flower=new int[3];
		kData[v].flower[0]=micro2v[2*n][n+1];
		kData[v].flower[1]=micro2v[2*n-1][n];
		kData[v].flower[2]=micro2v[2*n-1][n-1];

		v=micro2v[2*n][2*n]; // upper right
		kData[v].num=2;
		kData[v].bdryFlag=1;
		kData[v].flower=new int[3];
		kData[v].flower[0]=micro2v[2*n-1][2*n];
		kData[v].flower[1]=micro2v[2*n-1][2*n-1];
		kData[v].flower[2]=micro2v[2*n][2*n-1];

		v=micro2v[n][2*n]; // top
		kData[v].num=2;
		kData[v].bdryFlag=1;
		kData[v].flower=new int[3];
		kData[v].flower[0]=micro2v[n-1][2*n-1];
		kData[v].flower[1]=micro2v[n][2*n-1];
		kData[v].flower[2]=micro2v[n+1][2*n];

		v=micro2v[0][n]; // upper left
		kData[v].num=2;
		kData[v].bdryFlag=1;
		kData[v].flower=new int[3];
		kData[v].flower[0]=micro2v[0][n-1];
		kData[v].flower[1]=micro2v[1][n];
		kData[v].flower[2]=micro2v[1][n+1];

		// edges
		for (int j=1;j<n;j++) {
			
			v=micro2v[0][j]; // left
			kData[v].num=3;
			kData[v].bdryFlag=1;
			kData[v].flower=new int[4];
			kData[v].flower[0]=micro2v[0][j-1];
			kData[v].flower[1]=micro2v[1][j];
			kData[v].flower[2]=micro2v[1][j+1];
			kData[v].flower[3]=micro2v[0][j+1];
			
			v=micro2v[j][0]; // bottom left
			kData[v].num=3;
			kData[v].bdryFlag=1;
			kData[v].flower=new int[4];
			kData[v].flower[0]=micro2v[j+1][0];
			kData[v].flower[1]=micro2v[j+1][1];
			kData[v].flower[2]=micro2v[j][1];
			kData[v].flower[3]=micro2v[j-1][0];
			
			v=micro2v[n+j][j]; // bottom right
			kData[v].num=3;
			kData[v].bdryFlag=1;
			kData[v].flower=new int[4];
			kData[v].flower[0]=micro2v[n+j+1][j+1];
			kData[v].flower[1]=micro2v[n+j][j+1];
			kData[v].flower[2]=micro2v[n+j-1][j];
			kData[v].flower[3]=micro2v[n+j-1][j-1];
			
			v=micro2v[2*n][n+j]; // right
			kData[v].num=3;
			kData[v].bdryFlag=1;
			kData[v].flower=new int[4];
			kData[v].flower[0]=micro2v[2*n][n+j+1];
			kData[v].flower[1]=micro2v[2*n-1][n+j];
			kData[v].flower[2]=micro2v[2*n-1][n+j-1];
			kData[v].flower[3]=micro2v[2*n][n+j-1];
			
			v=micro2v[n+j][2*n]; // top right
			kData[v].num=3;
			kData[v].bdryFlag=1;
			kData[v].flower=new int[4];
			kData[v].flower[0]=micro2v[n+j-1][2*n];
			kData[v].flower[1]=micro2v[n+j-1][2*n-1];
			kData[v].flower[2]=micro2v[n+j][2*n-1];
			kData[v].flower[3]=micro2v[n+j+1][2*n];
			
			v=micro2v[j][n+j]; // top left
			kData[v].num=3;
			kData[v].bdryFlag=1;
			kData[v].flower=new int[4];
			kData[v].flower[0]=micro2v[j-1][n+j-1];
			kData[v].flower[1]=micro2v[j][n+j-1];
			kData[v].flower[2]=micro2v[j+1][n+j];
			kData[v].flower[3]=micro2v[j+1][n+j+1];
		}
		
		newPack.kData=kData;
		newPack.rData=rData;
		newPack.nodeCount=nodecount;
		newPack.alpha=micro2v[n][n];
		newPack.gamma=micro2v[(int)(n*3/2)][(int)(n*3/2)];
		
		newPack.setCombinatorics();
		return newPack;
	}
	
	public static PackData build_j_function(int n0, int n1, int maxsize) {
		int next_bdry, aft_bdry, fore_bdry, cur_bdry, num;
		int N, alive = 0, dead = 0, vert;
		int[] util = null;
		int[] new_flower = null;
		int[] aft_flower = null;
		int[] fore_flower = null;

		PackData p = seed(2 * (n1 + 1), -1);
		// expand pack to hold maxsize
		if (maxsize < 10 || n0 < 1 || n1 < 1
				|| p.alloc_pack_space(maxsize + 10 * (n0 + 1) * (n1 + 1),
						false) == 0)
			throw new CombException("allocation failed");

		p.kData[1].utilFlag = 2; // 1-type vert at center
		for (int i = 1; i <= n1 + 1; i++) {
			p.kData[2 * i].utilFlag = 1; // 0-type vert
			p.kData[2 * i + 1].utilFlag = 3; // inf-type vert
		}
		cur_bdry = next_bdry = 2; /*
									 * get started traveling around the
									 * ever-expanding bdry, adding faces at 0-
									 * and 1-type bdry verts.
									 */
		// find the next bdry subject to added faces for later use.
		while (p.kData[(next_bdry = p.kData[next_bdry].flower[0])].utilFlag == 3) {
			if (next_bdry == cur_bdry) // error, bomb
				throw new CombException();
		}
		// set intended flower multiplicities
		if (p.kData[cur_bdry].utilFlag == 1) // 0-type vert
			N = n0 + 1;
		else
			N = n1 + 1; // 1-type vert

		// main while loop

		while (p.nodeCount < maxsize) {
			if (!p.isBdry(cur_bdry) || p.kData[cur_bdry].utilFlag == 3) { // done
																					// with
																					// this
																					// one
				cur_bdry = next_bdry;
				if (p.kData[cur_bdry].utilFlag == 1) // 0-type vert
					N = n0 + 1;
				else
					N = n1 + 1; // 1-type vert

				// TODO: 'j_ftn 2 1 400' was in infinite loop here.
				// find the next bdry subject to added faces for later use.
				while (p.kData[(next_bdry = p.kData[next_bdry].flower[0])].utilFlag == 3) {
					if (next_bdry == cur_bdry) // error, bomb
						throw new CombException();
				}
			}

			// cur_bdry shouldn't be inf-type
			if (p.kData[cur_bdry].utilFlag == 3)
				break; // goto BACK_TO_WHILE;

			if (p.kData[cur_bdry].utilFlag == 1) // 0-type vert
				N = n0 + 1;
			else
				N = n1 + 1; // 1-type vert
			fore_bdry = p.kData[cur_bdry].flower[0];
			aft_bdry = p.kData[cur_bdry].flower[p.countFaces(cur_bdry)];

			// break into cases depending on face count at cur_bdry */

			if (p.countFaces(cur_bdry) > 2 * N) /* too many faces already */
				throw new CombException();
			if (p.countFaces(cur_bdry) == 2 * N) { /*
												 * have all the necessary faces,
												 * just close up and check
												 * neighbors
												 */
				int[] ans = identify_nghbs(p, cur_bdry);
				if (ans[0] == 0)
					throw new CombException("failed to identify neighbors");
				alive = ans[1];
				dead = ans[2];
				if (next_bdry == dead)
					next_bdry = alive;
				else if (next_bdry > dead)
					next_bdry--;
				if (cur_bdry > dead)
					cur_bdry--;
				p.kData[cur_bdry].bdryFlag = 0;

				// too many faces at the consolidated neighbor
				if ((p.kData[alive].utilFlag == 1 && p.countFaces(alive) > 2 * (n0 + 1))
						|| (p.kData[alive].utilFlag == 2 && p.countFaces(alive) > 2 * (n1 + 1)))
					throw new CombException();
				break; // goto BACK_TO_WHILE;
			}
			if (p.countFaces(cur_bdry) == 2 * N - 1) { // only identify existing
													// nghbs
				// create new flower space for cur_bdry
				new_flower = new int[2 * N + 1];
				for (int i = 0; i <= p.countFaces(cur_bdry); i++)
					new_flower[i] = p.kData[cur_bdry].flower[i];
				new_flower[2 * N] = new_flower[0];
				p.kData[cur_bdry].flower = new_flower;
				p.kData[cur_bdry].num++;
				p.kData[cur_bdry].bdryFlag = 0;

				// fix fore_bdry
				fore_flower = new int[p.countFaces(fore_bdry) + 2];
				for (int j = 0; j <= p.countFaces(fore_bdry); j++)
					fore_flower[j] = p.kData[fore_bdry].flower[j];
				fore_flower[p.countFaces(fore_bdry) + 1] = aft_bdry;
				p.kData[fore_bdry].flower = fore_flower;
				p.kData[fore_bdry].num++;

				/* fix aft_bdry */
				aft_flower = new int[p.countFaces(aft_bdry) + 2];
				for (int j = 0; j <= p.countFaces(aft_bdry); j++)
					aft_flower[j + 1] = p.kData[aft_bdry].flower[j];
				aft_flower[0] = fore_bdry;
				p.kData[aft_bdry].flower = aft_flower;
				p.kData[aft_bdry].num++;

				// too many faces at neighbors?
				if ((p.kData[fore_bdry].utilFlag == 1 && p.countFaces(fore_bdry) > 2 * (n0 + 1))
						|| (p.kData[fore_bdry].utilFlag == 2 && p.countFaces(fore_bdry) > 2 * (n1 + 1))
						|| (p.kData[aft_bdry].utilFlag == 1 && p.countFaces(aft_bdry) > 2 * (n0 + 1))
						|| (p.kData[aft_bdry].utilFlag == 2 && p.countFaces(aft_bdry) > 2 * (n1 + 1)))
					throw new CombException();
				break; // goto BACK_TO_WHILE;
			} else { // have to add one face and check aft_bdry
				// create new vert, flower
				vert = p.nodeCount + 1;
				p.nodeCount++;
				p.kData[vert].num = 1;
				p.kData[vert].bdryFlag = 1;
				p.kData[vert].flower = new int[2];
				p.kData[vert].flower[0] = cur_bdry;
				p.kData[vert].flower[1] = aft_bdry;

				// fix cur_bdry flower
				new_flower = new int[2 * N + 1];
				for (int i = 0; i <= p.countFaces(cur_bdry); i++)
					new_flower[i] = p.kData[cur_bdry].flower[i];
				new_flower[p.countFaces(cur_bdry) + 1] = vert;
				p.kData[cur_bdry].flower = new_flower;
				p.kData[cur_bdry].num++;

				// set utilFlag's
				if (p.kData[cur_bdry].utilFlag == 1
						&& p.kData[aft_bdry].utilFlag == 2)
					p.kData[vert].utilFlag = 3;
				else if (p.kData[cur_bdry].utilFlag == 2
						&& p.kData[aft_bdry].utilFlag == 1)
					p.kData[vert].utilFlag = 3;
				else if (p.kData[cur_bdry].utilFlag == 3
						&& p.kData[aft_bdry].utilFlag == 2)
					p.kData[vert].utilFlag = 1;
				else if (p.kData[cur_bdry].utilFlag == 2
						&& p.kData[aft_bdry].utilFlag == 3)
					p.kData[vert].utilFlag = 1;
				else if (p.kData[cur_bdry].utilFlag == 1
						&& p.kData[aft_bdry].utilFlag == 3)
					p.kData[vert].utilFlag = 2;
				else if (p.kData[cur_bdry].utilFlag == 3
						&& p.kData[aft_bdry].utilFlag == 1)
					p.kData[vert].utilFlag = 2;

				// fix up aft_bdry
				num = p.countFaces(aft_bdry);
				aft_flower = new int[num + 2];
				for (int i = 0; i <= num; i++)
					aft_flower[i + 1] = p.kData[aft_bdry].flower[i];
				aft_flower[0] = vert;
				p.kData[aft_bdry].flower = aft_flower;
				p.kData[aft_bdry].num++;

				// too many faces at the consolidated neighbor
				if ((p.kData[aft_bdry].utilFlag == 1 && p.countFaces(aft_bdry) > 2 * (n0 + 1))
						|| (p.kData[aft_bdry].utilFlag == 2 && p.countFaces(aft_bdry) > 2 * (n1 + 1)))
					throw new CombException();

			}
		} // end of main while

		// set radii, etc
		p.hes = -1;
		for (int j = 1; j <= p.nodeCount; j++) {
			if (p.kData[j].bdryFlag != 0)
				p.setRadius(j,10.0);
			// bdry radii essentially infinite
			else
				p.setRadius(j,0.5);
		}
		p.alpha = 1;
		p.gamma = 2;

		// save utilFlags
		util = new int[p.nodeCount + 1];
		for (int j = 1; j <= p.nodeCount; j++)
			util[j] = p.kData[j].utilFlag;

		// fix packing up
		p.setName("j_ftn");
		p.setCombinatorics();
		p.set_aim_default();

		// shade alternate faces

		for (int j = 1; j <= p.faceCount; j++) {
			int i = util[p.faces[j].vert[0]];
			int k = util[p.faces[j].vert[1]];
			if ((i == 1 && k == 2) || (i == 2 && k == 3) || (i == 3 && k == 1))
				p.setFaceColor(j,ColorUtil.getFGColor());
			else
				p.setFaceColor(j,ColorUtil.getBGColor());
		}

		return p;
	}

	/**
	 * Simply identify two bdry neighbors of bdry vert v to close up flower at
	 * v; return int[3] with int[0] being return integer (1 on success),
	 * int[1]='alive', int[2]='dead'. plan to keep fore_vert, adjusting it's
	 * flower, then throwing out aft_vert as a node number and making required
	 * adjustments
	 */
	public static int[] identify_nghbs(PackData p, int v) {
		int fore_num, fore_vert, aft_vert, alive, dead;
		int[] new_flower = null;
//		KData[] kData = p.kData;

		int[] ans = new int[3];
		if (v < 1 || v > p.nodeCount || !p.isBdry(v)
				|| p.countFaces(v) < 3) {
			ans[0] = 0;
			return ans;
		}
		alive = fore_vert = p.kData[v].flower[0];
		dead = aft_vert = p.kData[v].flower[p.countFaces(v)];

		// make new flower for fore_vert
		fore_num = p.countFaces(fore_vert) + p.countFaces(aft_vert);
		new_flower = new int[fore_num + 1];
		for (int i = 0; i <= p.countFaces(fore_vert); i++)
			new_flower[i] = p.kData[fore_vert].flower[i];
		for (int i = p.countFaces(fore_vert) + 1; i <= fore_num; i++)
			new_flower[i] = p.kData[aft_vert].flower[i - p.countFaces(fore_vert)];

		// fix flower of v
		p.kData[v].flower[p.countFaces(v)] = fore_vert;
		p.setBdryFlag(v,0);

		// go to flowers of nghbs of aft_vert, replace aft_vert by fore_vert
		for (int j = 0; j <= p.countFaces(aft_vert); j++) {
			int k = p.kData[aft_vert].flower[j];
			for (int i = 0; i <= p.countFaces(k); i++)
				if (p.kData[k].flower[i] == aft_vert)
					p.kData[k].flower[i] = fore_vert;
		}

		// shift all higher index info
		for (int k = aft_vert; k < p.nodeCount; k++)
			p.kData[k] = p.kData[k + 1];

		// all references to aft_vert should be gone now; just have
		// to shift all the node indices to fill the hole
		for (int i = 1; i < p.nodeCount; i++)
			for (int j = 0; j <= p.countFaces(j); j++)
				if (p.kData[i].flower[j] > aft_vert)
					p.kData[i].flower[j]--;
		p.nodeCount--;
		if (alive > dead)
			alive = alive - 1;
		ans[0] = 1;
		ans[1] = alive;
		ans[2] = dead;
		return ans;
	} 
		
	/** 
	 * Construct hex packings of annuli. Parameters in data string 
	 * are p, q, steps forming the fundamental loop for this Doyle
	 * spiral, and n for the number of additional loops to be added 
	 * to each side of the initial loop; so at end should have 2n+1 
	 * copies of the fundamental loop, symmetric about the 
	 * original loop. 
	 * @param pp, qq, integers 
	 * @param n
	 * @return PackData or null on error
	 */
	public static PackData doyle_annulus(int pp, int qq, int n) {
		PackData p = seed(6, 0); // create euclidean hex flower
		int nvert, lvert, rvert, v, w, num;
		int[] newflower;

		if (qq == 2 && pp == 2) { // this is a special case 
			for (int i = 1; i <= 3; i++)
				p.add_vert(7);
			p.enfold(7);
			p.add_vert(9);
			p.add_vert(9);

			// fix 5 
			newflower = new int[7];
			newflower[0] = newflower[6] = 1;
			newflower[1] = 4;
			newflower[2] = 10;
			newflower[3] = 9;
			newflower[4] = 12;
			newflower[5] = 6;
			p.kData[5].flower = newflower;
			p.kData[5].num = 6;
			p.setBdryFlag(5,0);

			// fix up 9 
			newflower = new int[7];
			newflower[0] = newflower[6] = 5;
			newflower[1] = 10;
			newflower[2] = 7;
			newflower[3] = 8;
			newflower[4] = 11;
			newflower[5] = 12;
			p.kData[9].flower = newflower;
			p.kData[9].num = 6;
			p.setBdryFlag(9,0);

			/* fix up 10 */
			newflower = new int[5];
			newflower[0] = 2;
			newflower[1] = 7;
			newflower[2] = 9;
			newflower[3] = 5;
			newflower[4] = 4;
			p.kData[10].flower = newflower;
			p.kData[10].num = 4;
			p.setBdryFlag(10,1);

			/* fix up 4 */
			newflower = new int[4];
			newflower[0] = 10;
			newflower[1] = 5;
			newflower[2] = 1;
			newflower[3] = 3;
			p.kData[4].flower = newflower;
			p.kData[4].num = 3;
			p.setBdryFlag(4,1);

			/* fix up 6 */
			newflower = new int[5];
			newflower[0] = 8;
			newflower[1] = 7;
			newflower[2] = 1;
			newflower[3] = 5;
			newflower[4] = 12;
			p.kData[6].flower = newflower;
			p.kData[6].num = 4;
			p.setBdryFlag(6,1);

			/* fix up 12 */
			newflower = new int[4];
			newflower[0] = 6;
			newflower[1] = 5;
			newflower[2] = 9;
			newflower[3] = 11;
			p.kData[12].flower = newflower;
			p.kData[12].num = 3;
			p.setBdryFlag(12,1);
		}

		/* another special case, but there are problems in the
		   combinatorics when trying to add generations, so I 
		   have temporarily disabled this section. */

		/*
		else if (pp==1 && qq==2) { 
		  add_vert(p,7);
		  add_vert(p,7);
		 */
		/* fix 7 */
		/*
		  newflower=(int *)calloc(7,sizeof(int));
		  newflower[0]=newflower[6]=1;
		  newflower[1]=6;
		  newflower[2]=8;
		  newflower[3]=9;
		  newflower[4]=5;
		  newflower[5]=2;
		  p.kData[7].flower=newflower;
		  p.kData[7].num=6;
		  p.setBdryFlag(7,0);
		 */
		/* fix 5 */
		/*
		  newflower=(int *)calloc(7,sizeof(int));
		  newflower[0]=newflower[6]=1;
		  newflower[1]=4;
		  newflower[2]=2;
		  newflower[3]=7;
		  newflower[4]=9;
		  newflower[5]=6;
		  p.kData[5].flower=newflower;
		  p.kData[5].num=6;
		  p.setBdryFlag(5,0);
		 */
		/* fix 4 */
		/*
		  newflower=(int *)calloc(4,sizeof(int));
		  newflower[0]=2;
		  newflower[1]=5;
		  newflower[2]=1;
		  newflower[3]=3;
		  p.kData[4].flower=newflower;
		  p.kData[4].num=3;
		  p.setBdryFlag(4,1);
		 */
		/* fix 2 */
		/*
		  newflower=(int *)calloc(5,sizeof(int));
		  newflower[0]=3;
		  newflower[1]=1;
		  newflower[2]=7;
		  newflower[3]=5;
		  newflower[4]=4;
		  p.kData[2].flower=newflower;
		  p.kData[2].num=4;
		  p.setBdryFlag(2,1);
		 */
		/* fix 6 */
		/*
		  newflower=(int *)calloc(5,sizeof(int));
		  newflower[0]=8;
		  newflower[1]=7;
		  newflower[2]=1;
		  newflower[3]=5;
		  newflower[4]=9;
		  p.kData[6].flower=newflower;
		  p.kData[6].num=4;
		  p.setBdryFlag(6,1);
		 */
		/* fix 8 */
		/*
		  newflower=(int *)calloc(3,sizeof(int));
		  newflower[0]=9;
		  newflower[1]=7;
		  newflower[2]=6;
		  p.kData[8].flower=newflower;
		  p.kData[8].num=2;
		  p.setBdryFlag(8,1);
		 */
		/* fix 9 */
		/*
		  newflower=(int *)calloc(4,sizeof(int));
		  newflower[0]=6;
		  newflower[1]=5;
		  newflower[2]=7;
		  newflower[3]=8;
		  p.kData[9].flower=newflower;
		  p.kData[9].num=3;
		  p.setBdryFlag(9,1);
		  p->alpha=7;
		}
		end of disabled section */

		/* In general, start with pp steps in orig direction. Note: I know what
		   happens at each step: three circles are added counterclockwise
		   to enclose nvert and make it a hex flower. Except for the first
		   step, their vertex numbers are always nvert+2, nvert+3, nvert+4,
		   this last equaling the new nodecount. So to continue on a straight
		   line we let the new nvert be nodecount-1. */
		else {
			try {
				if (pp != 0) {
					nvert = 7;
					for (int j = 1; j <= pp; j++) {
						for (int i = 1; i <= 3; i++)
							p.add_vert(nvert);
						p.enfold(nvert);
						nvert = p.nodeCount - 1;
					}
					nvert = p.nodeCount; // set up for shallow left turn 
				} else
					nvert = 2;
				// now, q-3 steps in this direction. 
				if (qq > 3)
					for (int j = 1; j <= qq - 3; j++) {
						for (int i = 1; i <= 3; i++)
							p.add_vert(nvert);
						p.enfold(nvert);
						nvert = p.nodeCount - 1;
					}
				else
					nvert = p.nodeCount;
			} catch (Exception ex) {
				return null;
			}

			/* now paste up the end/beginning; nvert comes in next to petal 5,
			   opposite from vertex 2. Start with two new vertices, rvert, then 
			   lvert (right/left of nvert as one looks from nvert toward 1) */
			p.add_vert(nvert);
			p.add_vert(p.kData[nvert].flower[0]);

			// fix up vert 5 
			newflower = new int[7];
			newflower[0] = newflower[6] = 6;
			newflower[1] = 1;
			newflower[2] = 4;
			lvert = newflower[3] = p.kData[nvert].flower[0];
			newflower[4] = nvert;
			rvert = newflower[5] = p.kData[nvert].flower[4];
			p.kData[5].flower = newflower;
			p.kData[5].num = 6;
			p.setBdryFlag(5,0);

			// fix up vert nvert 
			newflower = new int[7];
			for (int j = 0; j < 5; j++)
				newflower[j] = p.kData[nvert].flower[j];
			newflower[5] = 5;
			newflower[6] = newflower[0];
			p.kData[nvert].flower = newflower;
			p.kData[nvert].num = 6;
			p.setBdryFlag(nvert,0);

			// fix up rvert
			newflower = new int[4];
			newflower[0] = 6;
			newflower[1] = 5;
			newflower[2] = nvert;
			newflower[3] = nvert - 1;
			p.kData[rvert].flower = newflower;
			p.kData[rvert].num = 3;
			p.setBdryFlag(rvert,1);

			// fix up lvert 
			newflower = new int[4];
			newflower[0] = p.kData[lvert].flower[0];
			newflower[1] = nvert;
			newflower[2] = 5;
			newflower[3] = 4;
			p.kData[lvert].flower = newflower;
			p.kData[lvert].num = 3;
			p.setBdryFlag(lvert,1);

			// fix up 4 
			newflower = new int[4];
			newflower[0] = lvert;
			for (int j = 0; j < 3; j++)
				newflower[j + 1] = p.kData[4].flower[j];
			p.kData[4].flower = newflower;
			p.kData[4].num = 3;
			p.setBdryFlag(4,1);

			// fix up 6 
			num = p.countFaces(6);
			newflower = new int[num + 2];
			newflower[num + 1] = rvert;
			for (int j = 0; j <= num; j++)
				newflower[j] = p.kData[6].flower[j];
			p.kData[6].flower = newflower;
			p.kData[6].num = num + 1;
			p.setBdryFlag(6,1);
		}

		// need to organize combinatorics 
		p.setCombinatorics();

		// store the interior vertices as vlist 
		p.vlist = new NodeLink(p, "i");

		/* Want 2n+1 loops symmetrically about the original, so have
		   to add n-1 hex generations to each boundary. */
		for (int j = 1; j <= n - 1; j++) {
			v = p.bdryStarts[1];
			w = p.bdryStarts[2];
			p.add_layer(1, 6, v, v); // mode=1, DEGREE
			p.add_layer(1, 6, w, w);
		}
		double newrad = 1.0 / ((double) (pp + qq));
		for (int j = 1; j <= p.nodeCount; j++)
			p.setRadius(j,newrad);

		// fix packing up
		p.setName("Doyle_annulus");
		p.setCombinatorics();
		p.set_aim_default();
		p.fillcurves();
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
		myPacking.alpha=1;
		
		// gamma points to middle vertex of bdry edge in first face
 		myPacking.gamma=myPacking.kData[1].flower[gamma_indx];
 		
 		// create 'elist' to hold edges not connected to
 		//  face center vertices --- i.e. the graph edges
 		myPacking.elist=new EdgeLink(myPacking);
 		for (int v=1;v<=myPacking.nodeCount;v++) {
 			if (myPacking.getVertMark(v)!=1)
 				for (int j=0;j<(myPacking.countFaces(v)+myPacking.getBdryFlag(v));j++) {
 					int k=myPacking.kData[v].flower[j];
 					if (k>v && myPacking.getVertMark(k)!=1)
 						myPacking.elist.add(new EdgeSimple(v,k));
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
		int rslt=p1.adjoin(p2,v1,v2,n);
		if (rslt<=0) 
			return 0;
		
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
		
		PackData growWheel = PackCreation.seed(3*e+h, 0);
		growWheel.swap_nodes(3*e+h+1, 1);
		growWheel.alpha=3*e+h+1;
		growWheel.complex_count(false);
		growWheel.vlist=new NodeLink();
		growWheel.vlist.add(3*e+h+1); // list center verts of tiles added
		growWheel.swap_nodes(1+e,2);
//		growWheel.complex_count(false);
		growWheel.swap_nodes(1+e+h,3);
		growWheel.complex_count(false);
		growWheel.elist=new EdgeLink(growWheel,"b");
		
		// mark the boundary
		for (int vi=1;vi<growWheel.nodeCount;vi++) {
			int om=growWheel.getVertMark(vi);
			growWheel.setVertMark(vi,om+1);
		}
		
		// want to mark the smallest level "core" (middle triangle)
		//    with -1 and it's rotated neighbor with -2;
		if (N==1) // at first level, just the core
			growWheel.setVertMark(growWheel.nodeCount,-2);
		
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
			tempReverse.reverse_orient();
			tempReverse.complex_count(true);
			
			tempPack.vlist=growWheel.vlist.makeCopy();
			tempReverse.vlist=growWheel.vlist.makeCopy();
			
			tempPack.elist=new EdgeLink(tempPack,"b");
			tempReverse.elist=new EdgeLink(tempReverse,"b");

						
			// A serves as the base:
			// adjoin B^ to A along end 
			//        2 on A to 2 on B^, endcount edges
			//        don't need to mark any vertices
			growWheel.adjoin(tempReverse,2,2,endcount);
			updateLists(growWheel,tempReverse.vlist,tempReverse.elist,growWheel.vertexMap);
			growWheel.complex_count(false);
			
			// transfer all the marks as each piece is adjoined
			Iterator<EdgeSimple> vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempReverse.getVertMark(edge.v));
			}
			
			// this new part is the rotated core, mark its center vert with -2 (first pass only)
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
			growWheel.adjoin(tempReverse,2,3,hypcount);
			updateLists(growWheel,tempReverse.vlist,tempReverse.elist,growWheel.vertexMap);
			growWheel.alpha=growWheel.vertexMap.findW(tempReverse.alpha);
			growWheel.complex_count(false);
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
			growWheel.adjoin(tempPack,2,3,longcount);
			updateLists(growWheel,tempPack.vlist,tempPack.elist,growWheel.vertexMap);
			int X=growWheel.vertexMap.findW(2);
			growWheel.complex_count(false);
			vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempPack.getVertMark(edge.v));
			}

			// adjoin E ends of C^ and D, endcount each
			//        X on A+B^+C^+D to 3 on E
			//        Y keeps track of old 2.
			growWheel.adjoin(tempPack,X,3,longcount);
			growWheel.complex_count(false);
			updateLists(growWheel,tempPack.vlist,tempPack.elist,growWheel.vertexMap);
			int Y=growWheel.vertexMap.findW(2);
			growWheel.complex_count(false);
			vM=growWheel.vertexMap.iterator();
			while (vM.hasNext()) {
				EdgeSimple edge=vM.next();
				growWheel.setVertMark(edge.w,tempPack.getVertMark(edge.v));
			}
			
			
			// renumber: X --> 1, Y --> 2
			growWheel.swap_nodes(X,1);
			growWheel.swap_nodes(Y,2);
			
			// side lengths follow recursion formula
			int holdhyp=hypcount;
			hypcount=5*endcount;
			endcount=holdhyp;
			longcount=2*endcount;
			
			// new generation is reverse oriented
			growWheel.reverse_orient();
			growWheel.complex_count(false);

			// increment marks on boundary
			for (int vi=1;vi<=growWheel.nodeCount;vi++)
				if (growWheel.isBdry(vi)) {
					int om=growWheel.getVertMark(vi);
					growWheel.setVertMark(vi,om+1);
				}
			
			generation++;

		} // end of while
		
		growWheel.facedraworder(false);
		
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
		double crit=GenBranching.LAYOUT_THRESHOLD;
		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		growWheel.fillcurves();
		growWheel.repack_call(1000);
		try {
			growWheel.comp_pack_centers(false,false,opt,crit);
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
			growWheel.tileData=TileData.paveMe(growWheel,growWheel.alpha);
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create pinWheel 'TileData'");
		}
		return growWheel;
	}
	
	
	/**
	 * Create N generations of a naive circle packing of the "chair" 
	 * substitution tiling.
	 * @param N int, number of generations; N=1 is basic, single flower chair
	 * @return new @see PackData
	 */
	public static PackData chairTiling(int N) {

		int generation=1; // number of generations in current build
		int edgeNumber=1; // count of vertices along each edge

		// chair starts as 8-seed, but 1 is put back on the boundary
		PackData growChair = PackCreation.seed(8, 0);
		growChair.swap_nodes(9, 1);
		growChair.complex_count(false);
		growChair.vlist=new NodeLink();
		growChair.vlist.add(9);
		growChair.elist=new EdgeLink(growChair,"b");

		boolean debug=false; // for debugging edge lists

		while (generation < N) {

			// tempPack is unit we adjoin 3 times
			PackData tempPack=growChair.copyPackTo();
			tempPack.vlist=growChair.vlist.makeCopy();
			tempPack.elist=new EdgeLink(tempPack,"b");

			// add the chair above left
			growChair.adjoin(tempPack, 4,8,2*edgeNumber);
			updateLists(growChair,tempPack.vlist,tempPack.elist,growChair.vertexMap);
			int new3=growChair.vertexMap.findW(3);
			int new5=growChair.vertexMap.findW(5);
			int new7=growChair.vertexMap.findW(7);
			growChair.swap_nodes(new3,2);
			growChair.swap_nodes(new5,3);
			growChair.swap_nodes(new7,4);
			growChair.complex_count(false);
			
			if (debug) {
				Iterator<EdgeSimple> es=growChair.elist.iterator();
				System.err.println("\n add upper left");
				while (es.hasNext()) {
					EdgeSimple edge=es.next();
					System.err.println("("+edge.v+" "+edge.w+")");
				}
				DebugHelp.debugPackWrite(growChair,"growChair1.p");
			}
			
			// add the chair below right
			growChair.adjoin(tempPack, 8,8,2*edgeNumber);
			updateLists(growChair,tempPack.vlist,tempPack.elist,growChair.vertexMap);
			new3=growChair.vertexMap.findW(3);
			new5=growChair.vertexMap.findW(5);
			new7=growChair.vertexMap.findW(7);
			growChair.swap_nodes(new3,6);
			growChair.swap_nodes(new5,7);
			growChair.swap_nodes(new7,8);
			growChair.complex_count(false);
			if (debug) {
				System.err.println("\n add lower right");
				Iterator<EdgeSimple> es=growChair.elist.iterator();
				while (es.hasNext()) {
					EdgeSimple edge=es.next();
					System.err.println("("+edge.v+" "+edge.w+")");
				}
				DebugHelp.debugPackWrite(growChair,"growChair2.p");
			}
	  		

			// add the chair between --- lower left
			growChair.adjoin(tempPack,6,7,4*edgeNumber);
			updateLists(growChair,tempPack.vlist,tempPack.elist,growChair.vertexMap);
			new5=growChair.vertexMap.findW(5);
			growChair.swap_nodes(new5,5);
			growChair.complex_count(false);
			if (debug) {
				System.err.println("\n add lower left");
				Iterator<EdgeSimple> es=growChair.elist.iterator();
				while (es.hasNext()) {
					EdgeSimple edge=es.next();
					System.err.println("("+edge.v+" "+edge.w+")");
				}
				DebugHelp.debugPackWrite(growChair,"FullChair.p");
			}
		
			generation++;
			edgeNumber= 2*edgeNumber;
			
		} // end of while
		
		growChair.facedraworder(false);
		
		// set the aims
		growChair.set_aim_default();
		for (int v=1;v<=growChair.nodeCount;v++) {
			if (growChair.isBdry(v))
				growChair.setAim(v,1.0*Math.PI);
		}
		growChair.setAim(1,1.5*Math.PI);
		growChair.setAim(2,0.5*Math.PI);
		growChair.setAim(3,0.5*Math.PI);
		growChair.setAim(5,0.5*Math.PI);
		growChair.setAim(7,0.5*Math.PI);
		growChair.setAim(8,0.5*Math.PI);
		
		// repack, layout
		double crit=GenBranching.LAYOUT_THRESHOLD;
		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		growChair.fillcurves();
		growChair.repack_call(1000);
		try {
			growChair.comp_pack_centers(false,false,opt,crit);
		} catch (Exception ex) {
			throw new CombException("'chair' creation failed");
		}
		
		// normalize: 3 on unit circle, 5 7 horizontal
		double ctr=growChair.getCenter(3).abs();
		double factor=1.0/ctr;
		growChair.eucl_scale(factor);
		Complex z=growChair.getCenter(7).minus(growChair.getCenter(5));
		double ang=(-1.0)*(MathComplex.Arg(z));
		growChair.rotate(ang);
		
		try {
			growChair.tileData=TileData.paveMe(growChair,growChair.alpha);
		} catch(Exception ex) {
			CirclePack.cpb.errMsg("Failed to create chair 'TileData'");
		}
		return growChair;
	}
	
	/**
	 * Create N generations of a 2D fusion tiling related
	 * to Fibonnacci numbers. I learned this from Natalie Frank.
	 * There are four tile types, A, B, C, D, each has next
	 * fusion stage make from a certain combination. Here is
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
		fusionA.complex_count(false);
		fusionB.complex_count(false);
		fusionC.complex_count(false);
		fusionD.complex_count(false);

		// keep track of tiles by marking barycenter vertex
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
			if (fusionA.adjoin(holdA,4,1,currentHeight)!=1 ||
					!reNumBdry(fusionA,1,baseWidth+currentWidth,currentHeight))
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
			if (lower.adjoin(holdB,4,1,baseHeight)!=1 ||
					!reNumBdry(lower,1,baseWidth+currentWidth,baseHeight))
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
			if (fusionA.adjoin(lower,3,4,baseWidth+currentWidth)!=1 ||
					!reNumBdry(fusionA,1,baseWidth+currentWidth,baseHeight+currentHeight))
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
			if (fusionB.adjoin(holdC, 4, 1,currentHeight)!=1 ||
					!reNumBdry(fusionB,1,currentWidth +baseWidth,currentHeight))
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
			if (fusionC.adjoin(holdA,3,4,currentWidth)!=1 ||
					!reNumBdry(fusionC,1,currentWidth,baseHeight+currentHeight))
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
				fusionD.setVertMark(fusionD.alpha,4);
				
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
			fusionA.setCombinatorics();
			
			int oldBaseHeight=baseHeight;
			int oldBaseWidth=baseWidth;
			baseWidth=currentWidth;
			baseHeight=currentHeight;
			currentWidth += oldBaseWidth;
			currentHeight += oldBaseHeight;

		} // end of while
		
		fusionA.facedraworder(false);
		
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
		double crit=GenBranching.LAYOUT_THRESHOLD;
		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		fusionA.fillcurves();
		fusionA.repack_call(1000);
		try {
			fusionA.comp_pack_centers(false,false,opt,crit);
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
			fusionA.tileData=TileData.paveMe(fusionA,fusionA.alpha);
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
		p.swap_nodes(swp,1);
		blist=blist.swapVW(swp, 1);
		
		swp=blist.get(h);
		p.swap_nodes(swp,2);
		blist=blist.swapVW(swp, 2);
		
		swp=blist.get(h+w);
		p.swap_nodes(swp,3);
		blist=blist.swapVW(swp, 3);
		
		swp=blist.get(h+w+h);
		p.swap_nodes(swp,4);

		p.complex_count(false);
		
		return true;
	}
	
	/**
	 * Create square grid packing with 2^N vertices on each edge
	 * @param N int, number of generations; N=1 is basic
	 * @return new @see PackData
	 */
	public static PackData squareGrid(int N) {

		int generation=1; // number of generations in current build
		int edgeNumber=1; // count of vertices along each edge

		// start as 4-seed, but 1 is swapped to boundary
		PackData sgPack = PackCreation.seed(4, 0);
		sgPack.swap_nodes(5, 1);
		sgPack.complex_count(false);

		boolean debug=false; // for debugging edge lists

		while (generation < N) {

			// tempPack is unit, we adjoin 3 copies
			PackData tempPack=sgPack.copyPackTo();

			// add the square above
			sgPack.adjoin(tempPack, 2,3,edgeNumber);
			int new1=sgPack.vertexMap.findW(1);
			sgPack.swap_nodes(new1,1);
			
			// add square upper left
			sgPack.adjoin(tempPack,2,4,edgeNumber);
			int new2=sgPack.vertexMap.findW(2);
			sgPack.swap_nodes(new2,2);
			int newAlpha=new2; // this will be set later

			// add square to left
			sgPack.adjoin(tempPack,3,4,2*edgeNumber);
			int new3=sgPack.vertexMap.findW(3);
			sgPack.swap_nodes(new3,3);
			
			sgPack.alpha=newAlpha;
			sgPack.setCombinatorics();
			
			if (debug) {
				Iterator<EdgeSimple> es=sgPack.elist.iterator();
				System.err.println("\n add upper left");
				while (es.hasNext()) {
					EdgeSimple edge=es.next();
					System.err.println("("+edge.v+" "+edge.w+")");
				}
				DebugHelp.debugPackWrite(sgPack,"sgPack1.p");
			}
		
			generation++;
			edgeNumber= 2*edgeNumber;
			
		} // end of while
		
		sgPack.facedraworder(false);
		
		// set the aims
		sgPack.set_aim_default();
		for (int v=1;v<=sgPack.nodeCount;v++) {
			if (sgPack.isBdry(v))
				sgPack.setAim(v,1.0*Math.PI);
		}
		for (int j=1;j<=4;j++)
			sgPack.setAim(j,0.5*Math.PI);

		// repack, layout
		double crit=GenBranching.LAYOUT_THRESHOLD;
		int opt=2; // 2=use all plotted neighbors, 1=use only those of one face 
		sgPack.fillcurves();
		sgPack.repack_call(1000);
		try {
			sgPack.comp_pack_centers(false,false,opt,crit);
		} catch (Exception ex) {
			throw new CombException("'chair' creation failed");
		}
		
		// normalize: 1 on unit circle, 3 4 horizontal
		double ctr=sgPack.getCenter(1).abs();
		double factor=1.0/ctr;
		sgPack.eucl_scale(factor);
		Complex z=sgPack.getCenter(4).minus(sgPack.getCenter(3));
		double ang=(-1.0)*(MathComplex.Arg(z));
		sgPack.rotate(ang);
		
		return sgPack;
	}
	
	/**
	 * Given @see PackData, add to its 'vlist' and 'elist' from
	 * the lists 'nl' and 'el', resp., but using the @see EdgeLink 
	 * of {old,new} pairs to translate the indices.
	 * @param p @see PackData; we'll change the lists here
	 * @param nl @see NodeLink, new vertices ('nl' remains unchanged)
	 * @param el @see EdgeLink, new edges ('el' remains unchanged)
	 * @param vertMap @see EdgeLink (should remain unchanged)
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
	
	public static PackData pentTiling(int N) {
		PackData pent=seed(5,0);
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
			pent.comp_pack_centers(false,false,2,.00001);
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
		PackData pentBase=seed(5,0);
		pentBase.swap_nodes(1,6);
		
		PackData heap=pentBase.copyPackTo();
		
		int generation=0;
		
		while (generation<N) {
			
			// expand 
			heap=doublePent(heap,generation);
			//	DebugHelp.debugPackWrite(heap,"doubleheap.p");
			
			heap.adjoin(pentBase,4,5,2);
			
			int new4=heap.vertexMap.findW(4);
			int new3=heap.vertexMap.findW(3);
			heap.swap_nodes(new3,3);
			heap.swap_nodes(new4,4);
			heap.complex_count(false);
			generation++;
		}
		
		heap.alpha=6;
		heap.gamma=1;
		heap.setCombinatorics();
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
			heap.comp_pack_centers(false,false,2,.00001);
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
		PackData pent=seed(5,0);
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
			triPent.comp_pack_centers(false,false,2,.00001);
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
		PackData pent=seed(5,0);
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
			quadPent.comp_pack_centers(false,false,2,.00001);
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
	 * @param p
	 * @param sidelength
	 * @return
	 */
	public static PackData adjoin3(PackData p,int sidelength) {
		
		PackData triPent=p.copyPackTo();

		// adjoin 2
		triPent.adjoin(p,1,1,sidelength);
		int newv=triPent.vertexMap.findW(3);
		triPent.swap_nodes(newv,7);
		newv=triPent.vertexMap.findW(4);
		triPent.swap_nodes(newv,8);
		newv=triPent.vertexMap.findW(5);
		triPent.swap_nodes(newv,9);
		triPent.setBdryFlags();
		
		// adjoin 3
		triPent.adjoin(p,7,1,2*sidelength);
		int new5=triPent.vertexMap.findW(4);
		int new6=triPent.vertexMap.findW(5);
		triPent.swap_nodes(new5,5);
		triPent.swap_nodes(new6,6);
		triPent.swap_nodes(new5,10); // put 10 at center
		
		triPent.alpha=10;
		triPent.gamma=7;
		triPent.setCombinatorics();
		
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
		triPent.adjoin(p,5,1,sidelength);
		
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
		triPent.setBdryFlags();
		
		// adjoin 3
		triPent.adjoin(p,newCorner,1,sidelength);
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
		triPent.setBdryFlags();

		// adjoin 4
		triPent.adjoin(p,newGamma,1,2*sidelength);
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
		
		triPent.alpha=9;
		triPent.gamma=newGamma;
		triPent.setCombinatorics();
		
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
		PackData temp=p.copyPackTo();
		base.complex_count(false);
		temp.complex_count(false);
		
		// adjoin 2
		base.adjoin(temp,3,5,sidelength);
		int newv=base.vertexMap.findW(2);
		base.swap_nodes(newv,2);
		base.setBdryFlags();
		
		// adjoin 3
		base.adjoin(temp,4,1,2*sidelength);
		newv=base.vertexMap.findW(6);
		base.swap_nodes(newv,6); // new center
		newv=base.vertexMap.findW(4);
		base.swap_nodes(newv,7); // temp for later use
		base.setBdryFlags();

		// adjoin 4
		base.adjoin(temp,5,1,2*sidelength);
		newv=base.vertexMap.findW(5);
		base.swap_nodes(newv,5);
		newv=base.vertexMap.findW(4);
		base.swap_nodes(newv,8); // temp for later use
		base.setBdryFlags();

		// adjoin 5
		base.adjoin(temp,7,5,2*sidelength);
		newv=base.vertexMap.findW(3);
		base.swap_nodes(newv,3);
		base.setBdryFlags();
		
		// adjoin 6
		base.adjoin(temp,8,5,3*sidelength);
		newv=base.vertexMap.findW(4);
		base.swap_nodes(newv,4);
		
		base.setCombinatorics();
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
		PackData temp=p.copyPackTo();
		int sidelength=N+1;
		
		// adjoin on left
		newPack.adjoin(temp,5,2,sidelength); //	DebugHelp.debugPackWrite(temp,"dyadicLeft.p");

		int new5=newPack.vertexMap.findW(5);
		int new4=newPack.vertexMap.findW(4);
		newPack.swap_nodes(6,4);
		newPack.swap_nodes(new5,5);
		newPack.swap_nodes(new4,4);
		newPack.swap_nodes(new5,1);

		newPack.setCombinatorics();
		return newPack;
	}
	
}

