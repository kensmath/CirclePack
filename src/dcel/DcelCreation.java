package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import complex.Complex;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import input.CommandStrParser;
import listManip.NodeLink;
import packing.PackData;

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
		 * Create a packing of hex generations. Calling routine
		 * to set radii/centers.
		 * @param n int, number of generations (seed is 1 generation)
		 * @return @see PackData
		 */
		public static PackData hexBuild(int n) {
			if (n==0)
				n=1;
			double rad=Math.pow(2.0,n-2);
			PackDCEL pdcel=CombDCEL.seed_raw(6);
			PackData p=new PackData(null);
			pdcel.p=p;
			CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha,false);
			for (int k=2;k<=n;k++) {
				int m=pdcel.vertCount;
				int ans=RawDCEL.addlayer_raw(pdcel,1,6,m,m);
	//			pdcel=CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha);
				if (ans<=0)
					return null;
			}
			
			boolean debug=false; // debug=true;
			if (debug)
				DCELdebug.printRedChain(pdcel.redChain);
			
			pdcel.fixDCEL_raw(p);
			for (int v=1;v<=p.nodeCount;v++)
				p.setRadius(v,rad);
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
		CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha,false);
		CombDCEL.d_FillInside(pdcel);
		pdcel.gamma=pdcel.alpha.twin;
		PackData p=new PackData(null);
		p.attachDCEL(pdcel);
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
		p.packDCEL.dcelCompCenters();
		
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

