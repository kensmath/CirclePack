package microLattice;

import complex.Complex;
import exceptions.CombException;
import komplex.EdgeSimple;
import komplex.KData;
import listManip.EdgeLink;
import packing.PackData;
import packing.RData;

/**
 * These static methods are for working with hex grids, 
 * the microGrid of the PackExtender "MicroGrid.java", 
 * various superlattices within that microGrid, etc. 
 * Wanted to spin these off as static.
 * @author kens, 2/2020
 *
 */
public class GridMethods {
	
	/**
	 * Create hex packing as in 'hexBuild', but by direct build
	 * rather than adding generations in succession. (Advantage should
	 * be speed, but vert numbering does not spiral out so nicely.)
	 * The hex grid here is identified with span of independent vectors
	 * u=<1/2,-sqrt(3)/2> and w=<1/2,sqrt(3)/2>, so <i,j> is i*u+j*w.
	 * We also set up translation info in 'micro2v' and 'v2micro'.
	 * Alpha is set to center vertex and gamma so positive x-axis goes through u+w.
	 * The faces are unit-sided equilateral triangles, radii = 1/2.
	 * @param n int, number of generations (seed is 1 generation)
	 * @param m2v [][]int, allocated with proper size by calling routing
	 * @param v2m [][]int, allocated with proper size by calling routing
	 * @return @see PackData
	*/
	public static PackData hexByHand(int n,int [][]m2v,int [][]v2m) {
		PackData newPack=new PackData(null);
		newPack.status=true;
		newPack.locks=0;
		newPack.activeNode=1;
		newPack.hes=0;
		
		// prepare KData and RData
		KData []kdata;
		RData []rdata;
		int nodecount=3*n*n+3*n+1;
		kdata=new KData[nodecount+1];
		rdata=new RData[nodecount+1];
		for (int v=1;v<=nodecount;v++) {
			kdata[v]=new KData();
			rdata[v]=new RData();
			rdata[v].rad=0.5;
			rdata[v].aim=2*Math.PI;
		}

		int vtick=0;
		Complex uz=new Complex(0.5,-Math.sqrt(3)/2.0);
		Complex wz=new Complex(0.5,Math.sqrt(3)/2.0);
		for (int u=0;u<=n;u++) 
			for (int w=0;w<=n+u;w++) {
				m2v[u][w]=++vtick;
				v2m[vtick][0]=u-n;
				v2m[vtick][1]=w-n;
				rdata[vtick].center=uz.times(u-n).add(wz.times(w-n));
			}
		for (int u=1;u<=n;u++)
			for (int w=u;w<=2*n;w++) {
				m2v[n+u][w]=++vtick;
				v2m[vtick][0]=u;
				v2m[vtick][1]=w-n;
				rdata[vtick].center=uz.times(u).add(wz.times(w-n));
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
				v=m2v[u][w];
				kdata[v].num=6;
				kdata[v].flower=new int[7];
				for (int j=0;j<6;j++) {
					EdgeSimple edge=hexstencil[j];
					int uu=u+edge.v;
					int ww=w+edge.w;
					kdata[v].flower[j]=m2v[uu][ww];
				}
				kdata[v].flower[6]=kdata[v].flower[0];
			}
		for (int u=1;u<n;u++) 
			for (int w=u+1;w<2*n;w++) {
				v=m2v[n+u][w];
				kdata[v].num=6;
				kdata[v].flower=new int[7];
				for (int j=0;j<6;j++) {
					EdgeSimple edge=hexstencil[j];
					int uu=n+u+edge.v;
					int ww=w+edge.w;
					kdata[v].flower[j]=m2v[uu][ww];
				}
				kdata[v].flower[6]=kdata[v].flower[0];
			}
		
		// corners
		v=m2v[0][0]; // lower left
		kdata[v].num=2;
		kdata[v].bdryFlag=1;
		kdata[v].flower=new int[3];
		kdata[v].flower[0]=m2v[1][0];
		kdata[v].flower[1]=m2v[1][1];
		kdata[v].flower[2]=m2v[0][1];
		
		v=m2v[n][0]; // bottom
		kdata[v].num=2;
		kdata[v].bdryFlag=1;
		kdata[v].flower=new int[3];
		kdata[v].flower[0]=m2v[n+1][1];
		kdata[v].flower[1]=m2v[n][1];
		kdata[v].flower[2]=m2v[n-1][0];

		v=m2v[2*n][n]; // lower right
		kdata[v].num=2;
		kdata[v].bdryFlag=1;
		kdata[v].flower=new int[3];
		kdata[v].flower[0]=m2v[2*n][n+1];
		kdata[v].flower[1]=m2v[2*n-1][n];
		kdata[v].flower[2]=m2v[2*n-1][n-1];

		v=m2v[2*n][2*n]; // upper right
		kdata[v].num=2;
		kdata[v].bdryFlag=1;
		kdata[v].flower=new int[3];
		kdata[v].flower[0]=m2v[2*n-1][2*n];
		kdata[v].flower[1]=m2v[2*n-1][2*n-1];
		kdata[v].flower[2]=m2v[2*n][2*n-1];

		v=m2v[n][2*n]; // top
		kdata[v].num=2;
		kdata[v].bdryFlag=1;
		kdata[v].flower=new int[3];
		kdata[v].flower[0]=m2v[n-1][2*n-1];
		kdata[v].flower[1]=m2v[n][2*n-1];
		kdata[v].flower[2]=m2v[n+1][2*n];

		v=m2v[0][n]; // upper left
		kdata[v].num=2;
		kdata[v].bdryFlag=1;
		kdata[v].flower=new int[3];
		kdata[v].flower[0]=m2v[0][n-1];
		kdata[v].flower[1]=m2v[1][n];
		kdata[v].flower[2]=m2v[1][n+1];

		// edges
		for (int j=1;j<n;j++) {
			
			v=m2v[0][j]; // left
			kdata[v].num=3;
			kdata[v].bdryFlag=1;
			kdata[v].flower=new int[4];
			kdata[v].flower[0]=m2v[0][j-1];
			kdata[v].flower[1]=m2v[1][j];
			kdata[v].flower[2]=m2v[1][j+1];
			kdata[v].flower[3]=m2v[0][j+1];
			
			v=m2v[j][0]; // bottom left
			kdata[v].num=3;
			kdata[v].bdryFlag=1;
			kdata[v].flower=new int[4];
			kdata[v].flower[0]=m2v[j+1][0];
			kdata[v].flower[1]=m2v[j+1][1];
			kdata[v].flower[2]=m2v[j][1];
			kdata[v].flower[3]=m2v[j-1][0];
			
			v=m2v[n+j][j]; // bottom right
			kdata[v].num=3;
			kdata[v].bdryFlag=1;
			kdata[v].flower=new int[4];
			kdata[v].flower[0]=m2v[n+j+1][j+1];
			kdata[v].flower[1]=m2v[n+j][j+1];
			kdata[v].flower[2]=m2v[n+j-1][j];
			kdata[v].flower[3]=m2v[n+j-1][j-1];
			
			v=m2v[2*n][n+j]; // right
			kdata[v].num=3;
			kdata[v].bdryFlag=1;
			kdata[v].flower=new int[4];
			kdata[v].flower[0]=m2v[2*n][n+j+1];
			kdata[v].flower[1]=m2v[2*n-1][n+j];
			kdata[v].flower[2]=m2v[2*n-1][n+j-1];
			kdata[v].flower[3]=m2v[2*n][n+j-1];
			
			v=m2v[n+j][2*n]; // top right
			kdata[v].num=3;
			kdata[v].bdryFlag=1;
			kdata[v].flower=new int[4];
			kdata[v].flower[0]=m2v[n+j-1][2*n];
			kdata[v].flower[1]=m2v[n+j-1][2*n-1];
			kdata[v].flower[2]=m2v[n+j][2*n-1];
			kdata[v].flower[3]=m2v[n+j+1][2*n];
			
			v=m2v[j][n+j]; // top left
			kdata[v].num=3;
			kdata[v].bdryFlag=1;
			kdata[v].flower=new int[4];
			kdata[v].flower[0]=m2v[j-1][n+j-1];
			kdata[v].flower[1]=m2v[j][n+j-1];
			kdata[v].flower[2]=m2v[j+1][n+j];
			kdata[v].flower[3]=m2v[j+1][n+j+1];
		}
		
		newPack.kData=kdata;
		newPack.rData=rdata;
		newPack.nodeCount=nodecount;
		newPack.alpha=m2v[n][n];
		int n2n=(int)(n/2);
		newPack.gamma=m2v[n-n2n][n+n2n];
//		newPack.gamma=m2v[(int)(n*3/2)][(int)(n*3/2)];
		
		newPack.setCombinatorics();
		return newPack;
	}
	
	/**
	 * Given equilateral cclw oriented face <a,b,c> in superlattice,
	 * find all parent signed coords (u,v) that are inside that
	 * triangle, on an edge, but not one of a,b,c. Here a, b, c are 
	 * the verts in the parent grid, so use v2m to translate.
	 * @param a int
	 * @param b int
	 * @param c int
	 * @param v2m int[][], translate parent vert index to parent grid coords
	 * @param d int, count along edge; (d+1) parent verts along each edge
	 * @return EdgeLink, null on error such as improper data
	 */
	public static EdgeLink equil2tri(int a,int b, int c,int [][]v2m,int d) {
		int []A=v2m[a];
		int []B=v2m[b];
		int []C=v2m[c];
		int []hold;
		
		// In our slanted hex arrangement, triangle <A,B,C> has one of
		//   two shapes and we can arrnage that A, B are horizontal, with
		//   C above or below B. Make it so.
		if (A[1]!=B[1]) { 
			if (A[1]==C[1]) { // rotate clw
				hold=C;
				C=B;
				B=A;
				A=hold;
			}
			else { // B, C form horizontal, rotate cclw
				hold=C;
				C=A;
				A=B;
				B=hold;
			}
		}

		// If C above A, all set; B to right of A, step increment = +1. 
		// But if C is below, then B is left of A, step increment = -1; 
		EdgeLink elink=new EdgeLink();
		if (C[1]>A[1]) {
			for (int i=1;i<d;i++) {
				for (int j=0;j<=i;j++) 
					elink.add(new EdgeSimple(A[0]+i,A[1]+j) );
				elink.add(new EdgeSimple(B[0],B[1]+i));
			}
		}
		else {
			for (int i=1;i<d;i++) {
				for (int j=0;j<=i;j++) 
					elink.add(new EdgeSimple(A[0]-i,A[1]-j) );
				elink.add(new EdgeSimple(B[0],B[1]-i));
			}
		}
		
		return elink;
	}

	/**
	 * Find oriented triangles in n generation superlattice 
	 * with edge length d which contain the signed coords (u,v)
	 * in parent. Can get at most 6, so store in 'trips' 6x6 
	 * array allocated by calling routine. Each row [a1,a2,b1,b2,c1,c2] 
	 * represents oriented triangle <a,b,c>. For example, (a1,a2)
	 * gives the parent's signed coords for a. Return count of
	 * triangles, 0 if (u,v) is outside the superlattice. Should have
	 * n*d <= pn.
	 * @param u int
	 * @param w int
	 * @param pn int, parent generations
	 * @param n int
	 * @param d int
	 * @param trips int[6][6] allocated in advance
	 * @return int, count of triangles, each a row
	 */
	public static int getGridTri(int u,int w,int pn,int n,int d,int [][]trips) {
		EdgeSimple up_wp=uw2UWBox(u,w,pn,n,d);
		if (up_wp==null)
			return 0;
		int up=up_wp.v;
		int wp=up_wp.w;
		
		// note that (up,wp) are signed coords in parent for a superlattice point
		int x=u-up;
		int y=w-wp;
		
// debug		
		if (x<0 || x>=d || y<0 || y>=d)
			throw new CombException("error in getting grid box corner.");

		int N=n*d;
		
		int tricount=-1;
		if (x>0 && y>0) { // typical interior (or upper or lower edge)
			// one or two faces -- careful about top and bottom edges
			if ((x+y)<=d && wp!=(up-N)) { // lower triangle
				trips[++tricount][0]=up; // 1
				trips[tricount][1]=wp;
				trips[tricount][2]=up+d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up+d;
				trips[tricount][5]=wp+d;
			}
			if ((x+y)>=d && wp!=(up+N)) { // upper triangle
				trips[++tricount][0]=up+d; // 2
				trips[tricount][1]=wp+d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp+d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
			}
		}
		// else on interior of some boundary
		else if (y>0) { // so x==0
			if (up<N) { // triangle to right
				trips[++tricount][0]=up+d; // 2
				trips[tricount][1]=wp+d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp+d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
			}
			if (up>-N) { // triangle to left
				trips[++tricount][0]=up-d; // 3
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp+d;
			}
		}
		else if (x>0) { // y=0
			if (wp>-N) { // triangle below
				trips[++tricount][0]=up+d; // 6
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp-d;
			}
			if (wp<N) { // triangle above
				trips[++tricount][0]=up; // 1
				trips[tricount][1]=wp;
				trips[tricount][2]=up+d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up+d;
				trips[tricount][5]=wp+d;
			}
		}
		
		// else, landed right on a superlattice vertex
		else if (up>-N && up<N && wp<up+N && wp>-N && wp<N && wp>up-N) { // interior, 6 faces
			trips[++tricount][0]=up; // 1
			trips[tricount][1]=wp;
			trips[tricount][2]=up+d;
			trips[tricount][3]=wp;
			trips[tricount][4]=up+d;
			trips[tricount][5]=wp+d;
			
			trips[++tricount][0]=up+d; // 2
			trips[tricount][1]=wp+d;
			trips[tricount][2]=up;
			trips[tricount][3]=wp+d;
			trips[tricount][4]=up;
			trips[tricount][5]=wp;
			
			trips[++tricount][0]=up-d;  // 3
			trips[tricount][1]=wp;
			trips[tricount][2]=up;
			trips[tricount][3]=wp;
			trips[tricount][4]=up;
			trips[tricount][5]=wp+d;
			
			trips[++tricount][0]=up;  // 4
			trips[tricount][1]=wp;
			trips[tricount][2]=up-d;
			trips[tricount][3]=wp;
			trips[tricount][4]=up-d;
			trips[tricount][5]=wp-d;
			
			trips[++tricount][0]=up-d; // 5
			trips[tricount][1]=wp-d;
			trips[tricount][2]=up;
			trips[tricount][3]=wp-d;
			trips[tricount][4]=up;
			trips[tricount][5]=wp;
			
			trips[++tricount][0]=up+d; // 6
			trips[tricount][1]=wp;
			trips[tricount][2]=up;
			trips[tricount][3]=wp;
			trips[tricount][4]=up;
			trips[tricount][5]=wp-d;
		}
		
		// remaining cases are on superlattice edges or corners
		else if (up==-N) { // upper left edge
			if (wp==-N) { // left corner
				trips[++tricount][0]=up-d; // 5
				trips[tricount][1]=wp-d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp-d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;

				trips[++tricount][0]=up;  // 4
				trips[tricount][1]=wp;
				trips[tricount][2]=up-d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up-d;
				trips[tricount][5]=wp-d;
			}
			else if (wp==0) { // upper left corner
				trips[++tricount][0]=up;  // 4
				trips[tricount][1]=wp;
				trips[tricount][2]=up-d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up-d;
				trips[tricount][5]=wp-d;

				trips[++tricount][0]=up-d;  // 3
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp+d;
			}
			else { // along interior of upper left edge 
				trips[++tricount][0]=up+d; // 6
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp-d;

				trips[++tricount][0]=up; // 1
				trips[tricount][1]=wp;
				trips[tricount][2]=up+d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up+d;
				trips[tricount][5]=wp+d;
				
				trips[++tricount][0]=up+d; // 2
				trips[tricount][1]=wp+d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp+d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
			}
		}
		else if (up==N) { // lower right edge
			if (wp==0) { // lower right corner
				trips[++tricount][0]=up-d;  // 3
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp+d;
				
				trips[++tricount][0]=up;  // 4
				trips[tricount][1]=wp;
				trips[tricount][2]=up-d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up-d;
				trips[tricount][5]=wp-d;
			}
			else if (wp==N) { // right corner
				trips[++tricount][0]=up;  // 4
				trips[tricount][1]=wp;
				trips[tricount][2]=up-d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up-d;
				trips[tricount][5]=wp-d;
				
				trips[++tricount][0]=up-d; // 5
				trips[tricount][1]=wp-d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp-d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
			}
			else { // interior of lower right edge
				trips[++tricount][0]=up-d;  // 3
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp+d;
				
				trips[++tricount][0]=up;  // 4
				trips[tricount][1]=wp;
				trips[tricount][2]=up-d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up-d;
				trips[tricount][5]=wp-d;
				
				trips[++tricount][0]=up-d; // 5
				trips[tricount][1]=wp-d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp-d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
			}
		}
		else if (wp==-N) {
			if (up==0) { // lower left corner
				trips[++tricount][0]=up-d;  // 3
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp+d;
				
				trips[++tricount][0]=up+d; // 2
				trips[tricount][1]=wp+d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp+d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
			}
			else { // interior of lower left edge
				trips[++tricount][0]=up; // 1
				trips[tricount][1]=wp;
				trips[tricount][2]=up+d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up+d;
				trips[tricount][5]=wp+d;
				
				trips[++tricount][0]=up+d; // 2
				trips[tricount][1]=wp+d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp+d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
				
				trips[++tricount][0]=up-d;  // 3
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp+d;
			}
		}
		else if (wp==N) {
			if (up==0) { // upper right corner
				trips[++tricount][0]=up-d; // 5
				trips[tricount][1]=wp-d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp-d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
				
				trips[++tricount][0]=up+d; // 6
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp-d;
			}
			else { // interior of upper right edge
				trips[++tricount][0]=up;  // 4
				trips[tricount][1]=wp;
				trips[tricount][2]=up-d;
				trips[tricount][3]=wp;
				trips[tricount][4]=up-d;
				trips[tricount][5]=wp-d;
				
				trips[++tricount][0]=up-d; // 5
				trips[tricount][1]=wp-d;
				trips[tricount][2]=up;
				trips[tricount][3]=wp-d;
				trips[tricount][4]=up;
				trips[tricount][5]=wp;
				
				trips[++tricount][0]=up+d; // 6
				trips[tricount][1]=wp;
				trips[tricount][2]=up;
				trips[tricount][3]=wp;
				trips[tricount][4]=up;
				trips[tricount][5]=wp-d;
			}
		}
		
		return tricount+1;
	}
	
	/**
	 * Given parent lattice signed coords (u,w) find parent lattice
	 * signed coords (us,ws) for the closest point to the lower 
	 * left of (u,v) that corresponds to a superlattice point.
	 * Here n is the number of superlattice generations, d its edge length,
	 * pn is the number of parent generations. 
	 * @param u int
	 * @param w int
	 * @param pn int, parent generations
	 * @param n int, superlattice generations
	 * @param d int, d>=1
	 * @return EdgeSimple, null if (u,w) not in the superlattice carrier
	 */
	public static EdgeSimple uw2UWBox(int u,int w,int pn,int n,int d) {
		int N=n*d;
		if (n<2 || d<1 || u<-N || u>N || w>(u+N) || w<-N || w>N || w<(u-N)) 
			return null;
		int uu=u;
		int ww=w;
		// find (uu,ww) relative to verts of form (d*x,d*y)
		int x=(int)Math.floor((double)uu/(double)d);
		int us=x*d;
		int y=(int)Math.floor((double)ww/(double)d);
		int ws=y*d;
		return new EdgeSimple(us,ws);
	}

	/** 
	 * Given (u,v) signed coords in the parent, return (U,V), signed 
	 * coords in the superlattice, or null if (u,v) does not 
	 * fall precisely on a superlattice location. The parent lattice
	 * is 'pn' generations, the superlattice n generations with edge 
	 * length d. 
	 * @param u int
	 * @param w int
	 * @param pn int
	 * @param n int
	 * @param d int, d>=1
	 * @return EdgeSimple, null if (u,v) not a superlattice grid pt
	 */
	public static EdgeSimple uw2UW(int u,int w,int pn,int n,int d) {
		int N=n*d;
		if (n<2 || d<1 || u<-N || u>N || w>(u+N) || w<-N || w>N || w<(u-N)) 
			return null;
		int U=(int)Math.floor((double)u/(double)d);
		int W=(int)Math.floor((double)w/(double)d);
		if ((U*d)!=u || (W*d)!=w) // not a superlattice grid point
			return null;
		return new EdgeSimple(U,W);
	}
	
	/** 
	 * Get signed coords (u,w) in the parent lattice. The parent lattice
	 * is 'pn' generations, the superlattice n generations with edge 
	 * length d. Given (U,W) signed coords in the superlattice, return (u,w), 
	 * signed coords in the parent. 
	 * @param U int
	 * @param W int
	 * @param pn int
	 * @param n int
	 * @param d int, d<=1
	 * @return EdgeSimple, null if (u,w) not in the parent or n or d illegal
	 */
	public static EdgeSimple UW2uw(int U,int W,int pn,int n,int d) {
		if (n<2 || d<1 || U<-n || U>n || W>(U+n) || W<-n || W>n || W<(U-n)) 
			return null;
		int u=U*d;
		int w=W*d;
		return new EdgeSimple(u,w);
	}

	/**
	 * are (U,V) legal signed coords for point in superlattice of 
	 * 'n' generations? Note that -n <= U,W <= n, so they may be negative.
	 * @param U int
	 * @param W int
	 * @param n int
	 * @return boolean
	 */
	public static boolean isLegalGridPt(int U,int W,int n) {
		if (U<-n || U> n || W<-n || W>n)
			return false;
		// note: due to hex nature vs square coords, two 
		//       corner regions are not legal.
		if (W>(U+n) || W<(U-n))
			return false;
		return true;
	}
	
	/**
	 * Relative grid coords for 6 neighbors at distance d in
	 * our slanted hex arrangement.
	 * @param d int
	 * @return Edgelink  
	 */
	public static EdgeLink hexStar(int d) {
		EdgeLink elink=new EdgeLink();
		elink.add(new EdgeSimple(d,0));
		elink.add(new EdgeSimple(d,d));
		elink.add(new EdgeSimple(0,d));
		elink.add(new EdgeSimple(-d,0));
		elink.add(new EdgeSimple(-d,-d));
		elink.add(new EdgeSimple(0,-d));
		return elink;
	}

	/**
	 * Given (us,ws), signed coords in parent grid, for a vert 
	 * which lies in a superlattice of n generations with edge 
	 * length d, return 'EdgeLink' of signed coords (u,w) in 
	 * parent (possibly illegal) for any superlattice 'ghost' nghbs, 
	 * that is, would-be superlattice neighbors that are off the 
	 * superlattice domain within the parent grid. Should have n*d <= pn.  
	 * @param us int
	 * @param ws int
	 * @param pn int, parent generations
	 * @param n int
	 * @param d int
	 * @return EdgeLink of (u,w)'s or null if no ghosts found
	 */
	public static EdgeLink getGhosts(int us,int ws,int pn, int n,int d) {
		EdgeSimple up_wp=uw2UWBox(us,ws,pn,n,d); 
		// signed coords in parent for superlattice central vert
		int u=up_wp.v;
		int w=up_wp.w;
		int N=n*d;
		
		EdgeLink elink=new EdgeLink();
		if (u==-N) {
			if (w==-N) { // left corner, 3 ghosts
				elink.add(new EdgeSimple(u-d,w));
				elink.add(new EdgeSimple(u-d,w-d));
				elink.add(new EdgeSimple(u,w-d));
			}
			else if (w==0) { // top left corner, 3 ghosts 
				elink.add(new EdgeSimple(u,w+d));
				elink.add(new EdgeSimple(u-d,w));
				elink.add(new EdgeSimple(u-d,w-d));
			}
			else if (w<0){ // upper left edge, 2 ghosts
				elink.add(new EdgeSimple(u-d,w));
				elink.add(new EdgeSimple(u-d,w-d));
			}
		}
		else if (w==-N) {
			if (u==0) { // lower left corner, 3 ghosts
				elink.add(new EdgeSimple(u-d,w-d));
				elink.add(new EdgeSimple(u,w-d));
				elink.add(new EdgeSimple(u+d,w));
			}
			else if (u<0) { // lower left edge, 2 ghosts
				elink.add(new EdgeSimple(u-d,w-d));
				elink.add(new EdgeSimple(u,w-d));
			}
		}
		else if (u==N) {
			if (w==0) { // lower right corner, 3 ghosts
				elink.add(new EdgeSimple(u,w-d));
				elink.add(new EdgeSimple(u+d,w));
				elink.add(new EdgeSimple(u+d,w+d));
			}
			else if (w==N) { // right corner, 3 ghosts
				elink.add(new EdgeSimple(u+d,w));
				elink.add(new EdgeSimple(u+d,w+d));
				elink.add(new EdgeSimple(u,w+d));
			}
			else if (w>0) { // lower right edge, 3 ghosts
				elink.add(new EdgeSimple(u+d,w));
				elink.add(new EdgeSimple(u+d,w+d));
			}
		}
		else if (w==N) {
			if (u==0) { // upper right corner, 3 ghosts
				elink.add(new EdgeSimple(u+d,w+d));
				elink.add(new EdgeSimple(u,w+d));
				elink.add(new EdgeSimple(u-d,w));
			}
			else if (u<N) { // upper right edge, 2 ghosts
				elink.add(new EdgeSimple(u+d,w+d));
				elink.add(new EdgeSimple(u,w+d));
			}
		}
		else if (u<0 && w==(u+N)) { // upper edge, 2 ghosts 
			elink.add(new EdgeSimple(u,w+d));
			elink.add(new EdgeSimple(u-d,w));
		}
		else if (u>0 && w==(u-N)) { // lower edge, 2 ghosts
			elink.add(new EdgeSimple(u,w-d));
			elink.add(new EdgeSimple(u+d,w));
		}
		
		if (elink.size()==0) 
			return null;
		return elink;
	}
	
}
