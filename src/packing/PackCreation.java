package packing;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import allMains.CirclePack;
import complex.Complex;
import complex.MathComplex;
import dcel.CombDCEL;
import dcel.DcelCreation;
import dcel.PackDCEL;
import dcel.RawDCEL;
import dcel.RedHEdge;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import ftnTheory.GenModBranching;
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

		PackData p = DcelCreation.seed(2 * (n1 + 1), -1);
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
		PackData p = DcelCreation.seed(6, 0); // create euclidean hex flower
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
			pdcel.setAlpha(growWheel.vertexMap.findW(tempReverse.alpha),null,false);
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
			growWheel.tileData=TileData.paveMe(growWheel,growWheel.alpha);
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
		double sidesize=0.5;
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

