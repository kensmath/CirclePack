package tiling;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.RawManip;
import deBugging.DebugHelp;
import exceptions.CombException;
import komplex.EdgeSimple;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;

/**
 * This class supports creation, storage, and manipulations for 
 * combinatorial data on 'tilings'. This data may be associated
 * with a circle packing parent; often, the packing is created 
 * from initial tiling data. 
 *
 * An array of 'Tile' type is created to maintain the tile information. 
 * Each tile will know its vertices (though indices depend on 'mode'
 * and may be changed in various operations). The 'tileIndex' is 
 * maintained by this class and is independent of the vertices. 
 * Indexing starts at 1. Each tile has a 'tileFlower' with the 
 * ccw list of neighbor indices, -1 being 'no neighbor'. Vertices 
 * are kept in 'Face.vert'.
 * 
 * The tiling and circle packing data are rather independent; we
 * manipulate the tiling combinatorics, for example, with various 
 * subdivision processes and only then generate the associated
 * canonical circle packing. 
 * 
 * In creating circle packings, tiling data is created depending
 * on 'mode'. For the default 'mode=3', tiling data is augmented
 * so n-gons are formed by 2n barycentrically subdivided triangles,
 * which is the basic combinatorial structure laid out in our
 * "conformal tiling" definitions. Mode 1 and 2 have less structure.
 * 
 * Since subdivision is one of our main interests, we can build 
 * Tiledata to some subdivision depth using subdivision rules. 
 * The construction then gives a hierarchy of tilings, stored 
 * in 'gradedTileData'. This depends on 'buildMode', which records 
 * the 'mode' setting of the stored structure, and must be redone if 
 * mode is changed. 'gradedTileData' is kept ONLY in 
 * 'canonicalPack.tileData'; other instances of 'TileData' will 
 * have 'gradedTileData' set to null. When some depth of a tiling
 * tree is needed, it is put in 'packData.tilingData', but it 
 * does not contain full information. In particular, the full 
 * hierarchy cannot be saved to a file, so it must be rebuilt if 
 * canonicalPack is lost.
 *  
 * 'gradedTileData' is generated to some depth in the 'build_sub' 
 * and 'subtile' commands of 'ConformalTiling'; so 
 * 'canonicalPack.tileData.myTiles' has pointers to the leaf 
 * tiles in the hierarchy. In general, however, 
 * 'packData.TileData' is pulled out of the hierarchy to some 
 * specified depth. If not full depth, then its 'myTiles' are 
 * copies (see 'copyBareBones') only at the specified tile depth.
 * All this depends on 'mode' and must be rebuilt if mode changes.

 * @author kens
 *
 */
public class TileData {
	
	// parent? either 'PackData' or 'Tile' (not both) or null
	public PackData packData;    // parent 'PackData'
	public Tile parentTile;		 // parent 'Tile'
	
	// see info above about full tiling hierarchy, kept only by 'canonicalPack'.
	public int builtMode;   	 // the 'mode' in effect when constructed
	public Vector<TileData> gradedTileData; // tilings in hierarchy;
								 // only for 'canonicalPack', 'deepestTD'
	public SubdivisionRules subRules;  // subdivision rules; may be null
	public int tileCount;        // number of tiles
	public Tile[] myTiles;       // array of tiles, index starts at 1
	public int wgTileCount; 	 // number of white/grey tiles (2n for each n-gon tile)
	public Tile[] wgTiles;		 // array of white/grey tiles, index starts at 1
	public TileData dualTileData;  // data for the dual tiling (null until created)
	public TileData quadTileData;  // data for the quad tiling (null until created)
	
	// this TileData may be piece of larger TileData (as with subdivision rules);
	//   may want <t,T> data,  t = local tile index, TaugVert = index in parent.
	public VertexMap tileMap;	 // generally null
	
	// Constructor(s)
	public TileData(PackData p,Tile t,int tcount,int mode) {
		if (p!=null) 
			packData=p;
		if (t!=null) 
			parentTile=t;
		myTiles=null;
		if (tcount>0) {
			tileCount=tcount;
			myTiles=new Tile[tileCount+1];
		}
		wgTiles=null;
		tileMap=null;
		subRules=null;
		builtMode=mode;
	}
	
	public TileData(PackData p,int mode) { // 'myTiles' remains null
		this(p,(Tile)null,0,mode);
	}
	
	public TileData(PackData p,int tcount,int mode) {
		this(p,null,tcount,mode);
	}
	
	public TileData(Tile t,int tcount,int mode) {
		this(null,t,tcount,mode);
	}
	
	public TileData(Tile t,int mode) {
		this(null,t,0,mode);
	}
	
	public TileData(int tcount,int mode) {
		this(null,null, tcount,mode);
	}
	
	/**
	 * Set the parent packing
	 * @param p PackData
	 */
	public void setParent(PackData p) {
		packData=p;
	}
	
	/**
	 * Converts tiling via 'tileFlowers' to a simple PackData; 
	 * in particular, there is just one circle for each tile 
	 * (not a circle for each vertex of each tile). This was 
	 * needed in looking at simulated 2D 'glasses'. 
	 * TODO: we assume the tiling has no boundary.
	 * @param td TileData
	 * @return PackData
	 */
	public static PackData tiles2simpleCP(TileData td) {
		PackData newPack=new PackData(null);
		boolean seemsOK=true;
		if (td==null || td.myTiles==null)
			return null;
		newPack.packDCEL.alloc_vert_space(td.tileCount+100,false);
		newPack.nodeCount=td.tileCount;
		int[][] bouquet=new int[td.tileCount+1][];
		for (int t=1;(t<=td.tileCount && seemsOK);t++) {
			Tile tile=td.myTiles[t];
			int num=tile.vertCount;
			if (tile.tileFlower==null)
				seemsOK=false;
			else {
				bouquet[t]=new int[num+1];
				for (int j=0;(j<num && seemsOK);j++) {
					int pet=tile.tileFlower[j][0];
					if (pet<=0) {
						CirclePack.cpb.errMsg(
								"in 'tiles2simpleCP', seems the tilings has boundary");
						seemsOK=false;
					}
					bouquet[t][j]=pet;
				}
				bouquet[t][num]=bouquet[t][0]; // close up
			}
		}
		if (!seemsOK) {
			throw new CombException("");
		}
		PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet);
		pdcel.fixDCEL(newPack);
		newPack.status=true;
		
		// all radii to .5
		for (int v=1;v<=newPack.nodeCount;v++) 
			newPack.setRadius(v,0.5);
		
		newPack.set_aim_default();
		newPack.fillcurves();
		return newPack;
	}

	/**
	 * Create the minimal packing based on 'myTiles' data, 
	 * as when reading from a TILECOUNT file. There are 
	 * two types of data input files: 'TILES:' and 'TILEFLOWERS:' (mutually exclusive). 
	 * Normally, need 'TILEFLOWERS:' if there are unigons, 
	 * digons, slits, and/or self-neighboring (so some vertex 
	 * occurs multiple times around a tile).
	 * 
	 * Under 'TILES:', vertices are built from the tile corners,
	 * then a baryCenter vert is added for each tile. A clone of
	 * the tileData is attached to the packing. 
	 * @param td TileData
	 * @return PackData
	 */
	public static PackData tiles2packing(TileData td) {
		
		// check if we have enough data
		if (td==null || td.tileCount<=0 || td.myTiles==null)
			throw new CombException("Tile data is missing");
		if (td.myTiles[1].vert[0]<=0 && td.myTiles[1].tileFlower==null)
			throw new CombException(
					"Tile data doesn't seem to have vertices or tileFlowers");

		// in 'TILEFLOWERS' case, need to set consistent vert lists first
		if (td.myTiles[1].vert[0]<=0) {
			try {
				if (TileBuilder.tileflowers2verts(td)<=0)
					throw new CombException();
			} catch (Exception ex) {
				throw new CombException("failed using 'tileFlower'");
			}
		}
		
		// Are there unigons, digons, slits, self-pasting? 
		boolean special=false;
		for (int t=1;t<=td.tileCount && !special;t++) {
			Tile tile=td.myTiles[t];
			for (int j=0;(j<tile.vertCount && !special);j++) {
				int v=tile.vert[j];
				if (tile.vertCount==1 || tile.vertCount==2) // uni/digon?
					special=true;
			}
			// any self-pasting, repeated verts?
			if (!special) { 
				int vcnt=tile.vertCount;
				for (int k=0;(k<vcnt && !special);k++) {
					// slit? i.e., successive edges self-pasting
					if (tile.tileFlower[k][0]==tile.tileIndex)
							special=true;
				}
				if (!special) {
					for (int k=0;(k<vcnt && !special);k++) {
						int v=tile.vert[k];
						for (int j=1;(j<vcnt && !special);j++)
							if (tile.vert[(k+j)%vcnt]==v)
								special=true;
					}
				}
			}
		}

		// if yes, then need full barycentric refinement;
		// NOTE: If there are unigons, this will fail, as we
		//    have no consistent way to handle them.
		if (special) { 
			TileBuilder tileBuilder=new TileBuilder(td);
			PackData p=tileBuilder.newfromFlowers();
			if (p==null || p.tileData==null || p.nodeCount<=0)
				throw new CombException(
					"'tileBuilder' seems to have failed in 'tile2packing'");
			return p;
		}
		
		// find vertices that occur among tile 'vert' lists;
		//   not guaranteed to start at 1 or be contiguous.
		int maxnode=0;
		for (int f=1;f<=td.tileCount;f++) {
			Tile tile=td.myTiles[f];
			for (int k=0;k<tile.vertCount;k++)
				maxnode = (tile.vert[k]>maxnode) ? tile.vert[k]:maxnode;
		}
		int tmpNodeCount=maxnode+td.tileCount+1;
		
		// building the bouquet takes several steps:
		//  1. build unordered 'rawflower' of tiles for each vert.
		//  2. then create ordered 'prequet' of petals
		//  3. then create 'midquet': add flowers for tile
		//     barycenters and intersperse in 'prequet' to 
		//     get 'midquet'
		//  4. there are some gaps, so reorder to end up with 'bouquet'

		// 1. build 'rawflower': 
		
		// 'tilehits' counts tiles for each vert to allocate space
		int []tilehits=new int[maxnode+1];
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			for (int j=0;j<tile.vertCount;j++) {
				tilehits[tile.vert[j]]++;
			}
		}
		int [][]rawflower=new int[maxnode+1][];
		for (int v=1;v<=maxnode;v++) 
			rawflower[v]=new int[tilehits[v]+1];
		
		// 0-entry accumulates the number of entries
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			for (int j=0;j<tile.vertCount;j++) {
				int v=tile.vert[j];
				rawflower[v][0] +=1; // increment
				rawflower[v][rawflower[v][0]]=t; // add t to list
			}
		}
		
		// 2. go vert-by-vert to build 'prequet'
		int[][] prequet=new int[tmpNodeCount+1][];
		for (int v=1;v<=maxnode;v++) {
			int numt=rawflower[v][0];
			if (numt>0) {
				// catalog edges from v in its tiles: edge = (u, w) 
				//   means (v,u) and (w,v) are cclw edges of the tile
				EdgeSimple[] nextprev=new EdgeSimple[numt];
				for (int j=1;j<=numt;j++) {
					Tile tile=td.myTiles[rawflower[v][j]];
					int vj=tile.vertIndx(v);
					int u=tile.vert[(vj+1)%tile.vertCount];
					int w=tile.vert[(vj-1+tile.vertCount)%tile.vertCount];
					nextprev[j-1]=new EdgeSimple(u,w);
				}
			
				// build a vector putting nghb'ing tiles in order
				Vector<Integer> flow=new Vector<Integer>();
				int[] tickoff=new int[numt+1];
			
				// add first cclw pair of petals
				int first=nextprev[0].v;
				int last=nextprev[0].w;
				flow.add(first);
				flow.add(last);
				tickoff[0]=1;
			
				// find contiguous petal
				int hits=1;
				int safety=1000;
				while (hits<numt && safety>0) {
					safety--;
					for (int pj=1;pj<numt;pj++) {
						if (tickoff[pj]==0) {
							EdgeSimple edge=nextprev[pj];
							if (edge.v==last) {
								flow.add((last=edge.w));
								tickoff[pj]=1;
								hits++;
							}
							else if (edge.w==first) {
								flow.insertElementAt((first=edge.v),0);
								tickoff[pj]=1;
								hits++;
							}
						}
					}
				} // end of while
				
				// might fail to use all tiles if the faces around
				//   v form more than one linked component
				if (hits<numt) {
					CirclePack.cpb.errMsg(
							"tile flower for v="+v+" is incomplete");
				}
				
				// did we bomb?
				if (safety==0)
					throw new CombException(
							"safety'ed out in building flower for v="+v);
			
				// build 'prequet'
				prequet[v]=new int[numt+1];
				for (int k=0;k<=numt;k++) {
					prequet[v][k]=flow.get(k);
				}
				
//				if (prequet[v][0]==prequet[v][numt])
//					thePack.setBdryFlag(v,0);
//				else 
//					thePack.setBdryFlag(v,1);
//				thePack.rData[v]=new RData();
//				thePack.rData[v].rad=.5;
			}
			
			// TODO: we do not check that we get a connected pattern or
			//   that we use all tiles.
			
		} // end of loop for 'prequet'
		
		
		// 3. Create 'midquet': add flowers for tile barycenters
		//    and interpose these barycenters in vertex flowers.
		//    Note that skipped indices are left with null array
		int[][] midquet=prequet;
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			int tnode=t+maxnode; // barycenter index
			midquet[tnode]=new int[tile.vertCount+1];
			for (int j=0;j<tile.vertCount;j++) {
				int v=tile.vert[j];
				midquet[tnode][j]=v;
				int preV=tile.vert[(j+1)%tile.vertCount];
				midquet[v]=insert_index(midquet[v],tnode,preV);
			}
			midquet[tnode][tile.vertCount]=midquet[tnode][0];
		} // end of 'while' for tiles
		
		// 4. create 'bouquet': toss empty arrays, re-index
		int[] newIndx=new int[tmpNodeCount+1];
		int tick=0;
		for (int v=1;v<=tmpNodeCount;v++) 
			if (midquet[v]!=null) 
				newIndx[v]=++tick;
		int newNodeCount=tick;

		int[][] bouquet=new int[newNodeCount+1][];
		for (int v=1;v<=tmpNodeCount;v++) {
			int newv=newIndx[v];
			if (newv!=0) {
				for (int j=0;j<midquet[v].length;j++)
					midquet[v][j]=newIndx[midquet[v][j]];
			}
			bouquet[newv]=midquet[v];
		}
		
		// complete the packing
		PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet);
		if (pdcel==null) 
			throw new CombException(
					"Failed to create using 'bouquet'");
		PackData thePack=new PackData(null);
		pdcel.fixDCEL(thePack);
		thePack.status=true;
		
		// duplicate tile data, but duals, quads, wgTiles, and 
		//   augmented vertices are set to null
		thePack.tileData=td.copyMyTileData();
		TileData newtd=thePack.tileData; // point to new copy
		newtd.dualTileData=newtd.quadTileData=null;
		newtd.wgTiles=null;
		for (int t=1;t<=newtd.tileCount;t++) 
			newtd.myTiles[t].augVert=null;
		
		// reset tile vertices to match its barycenter's flower
		for (int t=1;t<=newtd.tileCount;t++) {
			Tile tile=newtd.myTiles[t];
			tile.baryVert=newIndx[t+maxnode];
			tile.vert=thePack.getFlower(tile.baryVert);
		}
		
		// allocate 'tileFlowers' 
		for (int t=1;t<=newtd.tileCount;t++) {
			Tile tile=newtd.myTiles[t];
			tile.tileFlower=new int[tile.vertCount][2];
		}
		
		// catalog the tiles
		int tbase=thePack.nodeCount-newtd.tileCount; // barycenter indices
		for (int t=1;t<=newtd.tileCount;t++) {
			Tile tile=newtd.myTiles[t];
			HalfLink spokes=
					thePack.packDCEL.vertices[tile.baryVert].getSpokes(null);
			// go around vertices, see if there's a tile across edge.
			for (int k=0;k<spokes.size();k++) {
				HalfEdge spoke=spokes.get(k);
				HalfEdge he=spoke.next;
				if (!he.isBdry()) { // should be a tile s across edge
					int w=he.twin.origin.vertIndx;
					he=he.twin.next.next;
					int s=he.origin.vertIndx-tbase; // opposite tile
					if (s<=0) 
						throw new CombException("should be an opposite tile");
					Tile nghbtile=newtd.myTiles[s];
					tile.tileFlower[k][0]=s;
					int nghbind=thePack.nghb(nghbtile.baryVert,w); // index of w in s
					tile.tileFlower[k][1]=nghbind;
					nghbtile.tileFlower[nghbind][0]=t;
					nghbtile.tileFlower[nghbind][1]=k;
				}
				else { // on bdry?
					tile.tileFlower[k][0]=0;
					tile.tileFlower[k][1]=0;
				}
			}
		} // done cataloging

		// wrap up and return
		thePack.setAlpha(newtd.myTiles[1].baryVert);
		thePack.setGamma(newtd.myTiles[1].vert[0]);
		thePack.hes=thePack.intrinsicGeom;
		thePack.set_aim_default();

		return thePack;
	}
	
	/**
	 * Create a new integer array by inserting 'newV' after 'preV'
	 * in 'array'. Typically 'array' is a closed petal flower, so
	 * if 'preV' is both first and last, 'newV' should become 
	 * second petal.
	 * @param array int[]
	 * @param newV int
	 * @param preV int
	 * @return int[], null on error
	 */
	public static int[] insert_index(int[] array,int newV, int preV) {
		int n=array.length;
		int spot=-1;
		for (int j=0;(j<n && spot<0);j++)
			if (array[j]==preV)
				spot=j;
		if (spot<0)
			return null;
		int[] newarray=new int[n+1];
		for (int k=0;k<=spot;k++)
			newarray[k]=array[k];
		newarray[spot+1]=newV;
		for (int k=spot+2;k<=n;k++)
			newarray[k]=array[k-1];
		return newarray;
	}

	/**
	 * Create a fully realized (builtMode 3) packing for 
	 * given 'TileData'. This is a hex refinement of the 
	 * barycentric refinement of the original tiles: that is,
	 * 
	 * (1) start with tiles as ordered lists of vertices;
	 * (2) get triangulation by adding a barycenter to each 
	 *     tile and a barycenter to each tile edge.
	 * (3) bary_refine: hexrefine the resulting 
	 *     triangles.
	 *  
	 * This seems complicated, but contends with two problems:
	 * 
	 * (I)  Otherwise some vertices would have only 2 neighbors.
	 *      Now we can handle all tilings from "drawings", 
	 *      e.g. dessins
	 * (II) We now have enough data to follow the tile edges; 
	 *      without the extra vertices, combinatorial geodesics 
	 *      are ambiguous due to the bary_refine of the faces 
	 *      in step (3).
	 * 
	 * A clone of 'tileData' with augmented vertices is attached 
	 * to the final packing. 
	 * 
	 * Three construction stages: (1) build a packing having a 
	 * barycenter vert for each tile and for each tile edge;
	 * (2) barycentrically subdivide every face; (3) create
	 * 'dual', 'quad', and 'wgTiles' tileData. 
	 * 
	 * Vertex 'mark's in the new packing are set to {1,2,3} for 
	 *    {tile baryVert,original tile corner, new edge vert}, resp. 
	 * Each face is becomes a hex flower, and its center is
	 * given mark = -1.
	 * 
	 * wgTiles are created for each original tile, also for 
	 * 'dualTileData' and 'quadTileData'. 'wgTiles' are 
	 * indexed from 0, type is 1=original, 2=dual, 3=quad. 
	 * Each has vert[3] positively oriented, starting with 
	 * baryVert (vertex mark=1); the 'tile.mark' +- sign shows 
	 * whether positively or negatively oriented.
	 *  
	 * @param td TileData
	 * @return PackData, null on error or if no tiledata is given
	 */
	public static PackData tiles2FullBary(TileData td) {
		if (td==null || td.tileCount<=0) 
			return null;

		// do we have vertices that are indexed? 
		//    May not, eg., when data was read in 
		//    'TILEFLOWERS:' form.
		// This call indexes the vertices from 'tileFlowers'. 
		//   Throw exception if these don't exist or give 
		//   exception
		if (td.myTiles[1].vert[0]<=0) {
			try {
				if (TileBuilder.tileflowers2verts(td)<=0)
					throw new CombException();
			} catch (Exception ex) {
				throw new CombException("Failed in using 'tileFlowers'");
			}
		}
		
		// check for: 
		//   * any tile that is unigon or bigon  
		//   * any tile with two edges identified
		boolean special=false;
		for (int t=1;(t<=td.tileCount && !special);t++) {
			Tile tile=td.myTiles[t];
			if (tile.vertCount<3)
				special=true;
			for (int j=0;(j<(tile.vertCount-1) && !special);j++)
				for (int k=j+1;(k<tile.vertCount && !special);k++)
					if (tile.vert[j]==tile.vert[k])
						special=true;
		}
		if (special) {
			TileBuilder tileBuilder=new TileBuilder(td);
			PackData p=tileBuilder.newfromFlowers();
			if (p==null || p.tileData==null || p.nodeCount<=0)
				throw new CombException("'tileBuilder' seems to have failed");
			// see 'slitSpecial' for one case
//			throw new CombException("Not yet ready for these 'special' tile combinatorics");
		}

		// create minimal packing for given 'TileData'
		PackData newPD=TileData.tiles2packing(td); // minimal packing
		TileData workingTD=newPD.tileData;

		// clear 'mark's
		for (int i=1;i<=newPD.nodeCount;i++)
			newPD.setVertMark(i,0);
		
		// go through tiles, split edges; splitting edge 
		//   should prevent it being selected to be split 
		//   again from the other side.
		int origcount=newPD.nodeCount;
		for (int t=1;t<=workingTD.tileCount;t++) {
			Tile tile=workingTD.myTiles[t];
			newPD.setVertMark(tile.baryVert,1);
			int w=tile.vert[0];
			int v=w;
			for (int j=1;j<=tile.vertCount;j++) {
				v=w;
				if (j==tile.vertCount)
					w=tile.vert[0];
				else 
					w=tile.vert[j];
				HalfEdge he=newPD.packDCEL.findHalfEdge(v, w);
				if (he!=null) 
					RawManip.splitEdge_raw(newPD.packDCEL,he); 
				else {  // old edge; already split
//					DCELdebug.printBouquet(newPD.packDCEL);
					continue;
				}
			}
		}
		newPD.packDCEL.fixDCEL(newPD); 
		// DCELdebug.printBouquet(newPD.packDCEL);

		// set marks: 1=barycenters, 2=tile verts, 3=new vertices
		for (int t=1;t<=newPD.tileData.tileCount;t++) {
			Tile tile=newPD.tileData.myTiles[t];
			newPD.setVertMark(tile.baryVert,1);
			for (int j=0;j<tile.vertCount;j++)
				newPD.setVertMark(tile.vert[j],2);
		}
		for (int v=origcount+1;v<=newPD.nodeCount;v++) 
			newPD.setVertMark(v,3);
		
		// prepare for 'quadTile' and 'dualTile'
		int dcount=0;
		int qcount=0;
		for (int v=1;v<=newPD.nodeCount;v++) {
			int k=newPD.getVertMark(v);
			if (k==2) 
				dcount++;
			else if (k==3)
				qcount++;
		}
		workingTD.dualTileData=new TileData(dcount,td.builtMode);
		workingTD.quadTileData=new TileData(qcount,td.builtMode);

		// bary_refine: each face in barycentric subdivision of
		//   tiling is bary_refined, hence is a 6-flower: these 
		//   new barycenters are numbered starting at 'bcbase'.
		// Save lots of work by building master list 'wgTiles' as
		//   as they are created, indexed from 1 by using the index
		//   of the barycenter circle and subtracting 'bcbase'.
		workingTD.wgTileCount=newPD.faceCount;
		RawManip.hexBaryRefine_raw(newPD.packDCEL,true);
		newPD.packDCEL.fixDCEL(newPD);
		int bcbase=newPD.nodeCount-workingTD.wgTileCount;
		// indexed from 1 to wgTileCount
		workingTD.wgTiles=new Tile[workingTD.wgTileCount+1];

		// for each tile create 'augVert' list for tile, 
		//    create and save 'wgTiles' 
		int wgTick=0; // for indices of wgTiles in master list
		for (int t=1;t<=workingTD.tileCount;t++) {
			Tile tile=workingTD.myTiles[t];
			tile.wgIndices=new int[2*tile.vertCount];
			int num=newPD.countFaces(tile.baryVert);
			
			// check consistency
			if (newPD.isBdry(tile.baryVert) || num!=(4*tile.vertCount)) {
				CirclePack.cpb.errMsg("Tile "+t+" (center "+tile.baryVert+") "+
						"flower has problem");
				break; // out of 'for' loop
			}
			
			// arrange that halfedge of baryVert is direction to vert[0].
			// TODO: to handle dual tilings consistently, have to consider 
			//       case that baryVert is on the boundary (and tile bdry 
			//       goes through it. Best is vert[0] is required to be 
			//       flower[0] in this case from the beginning.
			Vertex vert=newPD.packDCEL.vertices[tile.baryVert];
			HalfLink spokes=vert.getSpokes(null);
			vert.halfedge=null;
			Iterator<HalfEdge> sis=spokes.iterator();
			while (sis.hasNext()) {
				HalfEdge he=sis.next();
				int m=he.twin.origin.vertIndx;
				if (newPD.countFaces(m)==4 && newPD.nghb(m, tile.vert[0])>=0) {
					vert.halfedge=he;
					break;
				}
			}
			if (vert.halfedge==null) {
				CirclePack.cpb.errMsg("Tile "+t+" (center "+tile.baryVert+") "+
						"refined flower has some problem");
				break; // out of 'for' loop
			}

			// build augmented vertices by visiting sectors in succession
			int newAugCount=4*tile.vertCount;
			int []augVs=new int[newAugCount];
			int [] myflower=vert.getFlower(true);
			for (int j=0;j<tile.vertCount;j++) {
				
				// two hex flower subfaces, first is white (positively oriented)
				Tile wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=1; // positively oriented 
				int baryV=myflower[4*j+1]; // its barycenter
				
				// put wgTile in master list and record its index
				tile.wgIndices[2*j]=++wgTick; // point to wgTile index
				workingTD.wgTiles[tile.wgIndices[2*j]]=wgtile; // put wgTile into master list

				// set halfedge of baryV to point to tile.baryVert
				newPD.packDCEL.vertices[baryV].halfedge=
						newPD.packDCEL.findHalfEdge(baryV,tile.baryVert);
				int[] itsflower=newPD.getFlower(baryV);
				for (int k=0;k<3;k++) {
					wgtile.vert[k]=wgtile.augVert[2*k]=itsflower[2*k];
					wgtile.augVert[2*k+1]=itsflower[2*k+1];
				}
				augVs[4*j]=itsflower[2];
				augVs[4*j+1]=itsflower[3];
				
				// second is grey (negatively oriented)
				wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=-1; // negatively oriented 
				baryV=newPD.getFlower(tile.baryVert)[4*j+3]; // its barycenter
				tile.wgIndices[2*j+1]=++wgTick; // point to wgTile index
				workingTD.wgTiles[tile.wgIndices[2*j+1]]=wgtile; // put wgTile into master list
				
				// set halfedge of baryV to point to tile.baryVert
				newPD.packDCEL.vertices[baryV].halfedge=
						newPD.packDCEL.findHalfEdge(baryV,tile.baryVert);
				itsflower=newPD.getFlower(baryV);
				for (int k=0;k<3;k++) {
					wgtile.vert[k]=wgtile.augVert[2*k]=itsflower[2*k];
					wgtile.augVert[2*k+1]=itsflower[2*k+1];
				}
				augVs[4*j+2]=itsflower[2];
				augVs[4*j+3]=itsflower[3];
			}
			
			tile.augVertCount=newAugCount;
			tile.augVert=augVs;
		} // end of 'for' loop on tiles
		
		// Now build the dual tiling
		try {
			dcount=1;
			for (int v=1;v<=newPD.nodeCount;v++) {

				// dual tile for each original corner vertex
				if (newPD.getVertMark(v)==2) {
					int num=newPD.countFaces(v);
					int[] flower=newPD.getFlower(v);
					
					// bdry tile?
					if (newPD.isBdry(v)) { // this is bdry tile
						
						// create the dual tile
						Tile dtile=workingTD.dualTileData.myTiles[dcount]= 
								new Tile(workingTD.dualTileData,num/4+2);
						dtile.wgIndices=new int[num/2];
						
						// vert list starts downstream edge barycenter, then
						//   encircles wg faces until it reaches upstream
						//   edge barycenter, then closes up to start (v is not
						//   a corner of the dual, but acts as barycenter).
						// Use sides of wgtile tiles opposite to v
						int []augvert=new int[num+4];
						int []vert=new int[num/4+2];
						
						int tick=0; // counts wgTile's
						// first: edge from bdry edge barycenter to first tile barycenter
						int cv=flower[1];
						int myindx=newPD.nghb(cv,v);
						int[] cvflower=newPD.getFlower(cv);
						vert[tick]=augvert[2*tick]=cvflower[(myindx+2)%6];
						augvert[2*tick+1]=cvflower[(myindx+3)%6];
						dtile.wgIndices[tick++]=cv-bcbase;
						
						// then pairs of grey/white to get edges between tile barycenters
						// TODO: obe due to 'prepCanonical', but seems this iteration is in error
						for (int i=1;i<(num/4);i++) {
							cv=flower[2*i+1];
							myindx=newPD.nghb(cv,v);
							cvflower=newPD.getFlower(cv);
							vert[(tick+1)/2]=augvert[2*tick]=cvflower[(myindx+2)%6]; // tile barycenter
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
							cv=flower[2*i+3];
							myindx=newPD.nghb(cv,v);
							cvflower=newPD.getFlower(cv);
							augvert[2*tick]=cvflower[(myindx+2)%6];
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
						}							
						
						// then last tile barycenter to and including upstream edge barycenter
						cv=flower[num-1];
						myindx=newPD.nghb(cv,v);
						cvflower=newPD.getFlower(cv);
						vert[(tick+1)/2]=augvert[2*tick]=cvflower[(myindx+2)%6]; // get last tile barycenter
						augvert[2*tick+1]=cvflower[(myindx+3)%6];
						dtile.wgIndices[tick++]=cv-bcbase;
						vert[tick/2+1]=augvert[2*tick]=cvflower[(myindx+4)%6]; // last vert is edge barycenter
						
						// finish with augmented boundary 
						augvert[2*tick+1]=flower[num];
						augvert[2*tick+2]=v;
						augvert[2*tick+3]=flower[0];
						workingTD.dualTileData.myTiles[dcount].vert=vert;
						workingTD.dualTileData.myTiles[dcount].augVertCount=num+4;
						workingTD.dualTileData.myTiles[dcount].augVert=augvert;
					}

					// interior?
					else {
						// create the dual tile
						Tile dtile=workingTD.dualTileData.myTiles[dcount]= 
								new Tile(workingTD.dualTileData,num/4);
						dtile.wgIndices=new int[num/2];
						int []augvert=new int[num];
						int []vert=new int[num/4];
						
						// find a grey tile barycenter to start
						int gdir=-1;
						for (int j=0;(j<num && gdir<0);j++) {
							int cv=flower[j];
							if (cv>bcbase && workingTD.wgTiles[cv-bcbase].mark==-1) // grey
								gdir=j;
						}
						if (gdir<0)
							throw new CombException();
						
						// do in pairs, grey then white: get one 'vert', 4 'augverts' 
						int tick=0; // counts wgTile's
						for (int i=0;i<num;i=i+4) {
							int ii=(gdir+i)%num;
							int cv=flower[ii];
							int myindx=newPD.nghb(cv,v);
							int[] cvflower=newPD.getFlower(cv);
							vert[tick/2]=augvert[2*tick]=cvflower[(myindx+2)%6];
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
							cv=flower[(ii+2)%num];
							myindx=newPD.nghb(cv,v);
							cvflower=newPD.getFlower(cv);
							augvert[2*tick]=cvflower[(myindx+2)%6];
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
						}							
						workingTD.dualTileData.myTiles[dcount].vert=vert;
						workingTD.dualTileData.myTiles[dcount].augVertCount=num;
						workingTD.dualTileData.myTiles[dcount].augVert=augvert;
					}						
					
					workingTD.dualTileData.myTiles[dcount].baryVert=v; 
					dcount++;
				}
			} // end of 'for' on vertices for dual
		} catch (Exception ex) {
			workingTD.dualTileData=null;
			CirclePack.cpb.errMsg("Failed to complete dual tile data");
		}
			
		// next, build the quad tiling, one for each edge
		try {
			qcount=1;
			for (int v=1;v<=newPD.nodeCount;v++) {

				if (newPD.getVertMark(v)==3) { // v is edge barycenter
											
					// create the dual tile
					Tile qtile=workingTD.quadTileData.myTiles[qcount]= 
							new Tile(workingTD.quadTileData,4);
					int[] flower=newPD.getFlower(v);
					int []vert=new int[4];
					int []augvert=new int[8];

					if (newPD.isBdry(v)) { // bdry? 
						qtile.wgIndices=new int[2];

						// first is grey face
						int cv=flower[1];
						int myindx=newPD.nghb(cv,v);
						int[] cvflower=newPD.getFlower(cv);

						// these go at end
						augvert[6]=cvflower[myindx];
						augvert[7]=cvflower[(myindx+1)%6];
						
						vert[0]=augvert[0]=cvflower[(myindx+2)%6];
						augvert[1]=cvflower[(myindx+3)%6];
						vert[1]=augvert[2]=cvflower[(myindx+4)%6];
						qtile.wgIndices[0]=cv-bcbase;
						
						// next is white
						cv=flower[3];
						myindx=newPD.nghb(cv,v);
						cvflower=newPD.getFlower(cv);
						augvert[3]=cvflower[(myindx+3)%6];
						vert[2]=augvert[4]=cvflower[(myindx+4)%6];
						augvert[5]=cvflower[(myindx+5)%6];
						qtile.wgIndices[1]=cv-bcbase;
						vert[3]=v;

						workingTD.quadTileData.myTiles[qcount].vert=vert;
						workingTD.quadTileData.myTiles[qcount].augVertCount=8;
						workingTD.quadTileData.myTiles[qcount].augVert=augvert;
					}
					
					else { // interior? num=4
						qtile.wgIndices=new int[4];
						int num=newPD.countFaces(v);

						// find a grey tile barycenter to start
						int gdir=-1;
						for (int j=0;(j<num && gdir<0);j++) {
							int cv=flower[j];
							if (cv>bcbase && workingTD.wgTiles[cv-bcbase].mark==-1) // grey
								gdir=j;
						}
						if (gdir<0)
							throw new CombException();
						
						for (int j=0;j<4;j++) {
							int cv=flower[(gdir+2*j)%num];
							int myindx=newPD.nghb(cv,v);
							int[] cvflower=newPD.getFlower(cv);
							vert[j]=augvert[2*j]=cvflower[(myindx+2)%6];
							augvert[2*j+1]=cvflower[(myindx+3)%6];
							qtile.wgIndices[j]=cv-bcbase;
						}
						
						workingTD.quadTileData.myTiles[qcount].vert=vert;
						workingTD.quadTileData.myTiles[qcount].augVertCount=8;
						workingTD.quadTileData.myTiles[qcount].augVert=augvert;
							
					}
					
					workingTD.quadTileData.myTiles[qcount].baryVert=v; 
					qcount++;
				}
			
			} // end of 'for' on vertices for quads

		} catch (Exception ex) {
			workingTD.quadTileData=null;
			CirclePack.cpb.errMsg(
					"Failed to complete dual or quad tile data");
		}
		
		newPD.tileData=workingTD;
		newPD.tileData.builtMode=3;
		return newPD;
	}
	
	/**
	 * Check if given ordered list equals the 'vert' vector 
	 * for some tile.
	 * @param vlist NodeLink
	 * @return int, tile index or -1 if not found
	 */
	public int whichTile(NodeLink vlist) {
		if (vlist==null || vlist.size()==0)
			return -1;
		int v=vlist.get(0);
		int vcount=vlist.size();
		for (int t=0;t<=tileCount;t++) {
			int j=-1;
			if (myTiles[t].vertCount==vcount && (j=myTiles[t].vertIndx(v))>=0) {
				Iterator<Integer> vlst=vlist.iterator();
				int k=-1;
				for (k=0;k<tileCount;k++)
					if (vlst.next()!=myTiles[t].vert[(j+k)%vcount])
						k=tileCount+1;
				if (k==tileCount) // got a match
					return t;
			}
		}
		return -1;
	}
	
	/**
	 * TODO: not used currently, needs to be checked
	 * Using provisional 'nodeCount' and 'HalfEdge's already
	 * in place, decern tiling information by finding closed 
	 * loops of vertices defining the tiles. (Triangles are 
	 * included like any other tiles.)  
	 * @return int, count of tiles (including triangles) stored in 'tiles'
	 */
	public int identifyTiles() {
		int nextTile=0;
		int []vtrack=new int[packData.nodeCount+1];
		int [][]tflower=new int[packData.nodeCount+1][];
		
		// paralleling 'flower': keeps track of hits
		for (int vv=1;vv<=packData.nodeCount;vv++)
			tflower[vv]=new int[packData.countFaces(vv)+1];
		
		// cycle between two lists
		NodeLink curr=new NodeLink(packData);
		NodeLink next=new NodeLink(packData);
		next.add(1);
		
		// go through list of verts and identify tiles
		while (next!=null && next.size()>0) {
			curr=next;
			next=new NodeLink(packData);
			Iterator<Integer> crt=curr.iterator();
			
			// process this vertex
			while (crt.hasNext()) {
				int v=crt.next();
				int num=packData.countFaces(v);
				int[] vflower=packData.getFlower(v);
				if (vtrack[v]!=num) { // not done with this vert
					for (int j=0;j<num;j++) {
						
						// start a tile in this direction?
						if (tflower[v][j]==0) {
							Vector<Integer> tileVerts=new Vector<Integer>(3);
							tileVerts.add(v);
							int u=vflower[j];
							int safety=1000;
							int w=v;
							
							// go around edges to find the tile vertices
							while (u!=v && safety>0) {
								safety--;
								tileVerts.add(u);
								int indx_uw=packData.nghb(u,w);
								int nm=packData.countFaces(u);
								tflower[u][indx_uw] += 1;
								tflower[w][packData.nghb(w,u)] += 1;
								
								// add this to next list
								if (vtrack[u]==0)
									next.add(u);
								vtrack[u]++;

								// shift
								w=u;
								if (indx_uw==0) // must be closed flower
									indx_uw=nm-1;
								indx_uw=(indx_uw-1+nm)%nm;
								u=packData.getFlower(v)[indx_uw];
							} // while around tile
							
							// probably an error
							if (safety==0) {
								throw new CombException("error finding tile for "+v);
							}
							// create the tile vertex list
							int sz=tileVerts.size();
							Tile tile=new Tile(packData,this,sz);
							for (int ii=0;ii<sz;ii++)
								tile.vert[ii]=tileVerts.get(ii);
						
							// counting
							vtrack[v]++; // have new tile for v
							tile.tileIndex=++nextTile;
							myTiles[nextTile]=tile;
						}
								
					} // done with petals of v
				} // done with v
			} // end of 'while' through 'curr'
		}
		
		tileCount=nextTile;
		return tileCount;
	}
	
	/**
	 * Given a circle packing p and an interior vertex V, find a
	 * maximal paving of p by interior flowers, starting with that
	 * of V itself. A paving is a disjoint union of interior flowers 
	 * sharing edges.
	 * 
	 * This construction can be ambiguous or give malformed results
	 * in general, I think. However, if p was built as the  packing 
	 * of some tiling whose tiles have barycenters, then this may 
	 * reconstruct that tiling.
	 * @param p PackData
	 * @param V int, interior vertex
	 * @return new TileData (its packData is null)
	 */
	public static TileData paveMe(PackData p,int V) {
		if (p==null || V<1 || V>p.nodeCount || p.isBdry(V))
			return null;
		
		// track flowers we use, note the petals 
		NodeLink finalList=new NodeLink(p);
		int []util=new int[p.nodeCount+1]; // 0=open, 1=used, -1=excluded
		
		// cycle between two lists
		NodeLink curr=null;
		NodeLink next=new NodeLink(p,V);
		int safety=2*p.nodeCount;
		while(next!=null && next.size()>0 && next.size()>0 && safety>0) {
			safety--;
			curr=next;
			next=new NodeLink(p);
			Iterator<Integer> cl=curr.iterator();
			while (cl.hasNext() && safety>0) {
				safety--;
				int v=cl.next();
				if (util[v]==0 && !p.isBdry(v)) {
					// want this v
					finalList.add(v);
					util[v]=1;
					
					// find vertices across its flower edges
					HalfLink outlink=p.packDCEL.vertices[v].getOuterEdges();
					Iterator<HalfEdge> ois=outlink.iterator();
					while (ois.hasNext()) {
						HalfEdge he=ois.next();
						// petal is excluded
						util[he.origin.vertIndx]=-1;
						// opposite vert may be put in next
						int a=he.twin.next.twin.origin.vertIndx;
						if (a>0 && util[a]==0 && !p.isBdry(a)) 
							next.add(a);
					}
				}
			} // end of inner while
		} // end of outer while
	
		if (safety<=0) {
			throw new CombException("bombed in 'paveMe'");
		}
		
		return viaFlowers(p,finalList);
	}
	
	/**
	 * Given a packing and a list of its vertices, create tiling
	 * consisting of the flowers of the given vertices. We check
	 * that vertices in the list are interior and non-neighboring,
	 * but the tile pattern may be disconnected, incomplete, etc.
	 * @param p
	 * @param vlist
	 * @return new TileData (its packData is null)
	 */
	public static TileData viaFlowers(PackData p,NodeLink vlist) {
		if (p==null || vlist==null)
			return null;
		
		int []util=new int[p.nodeCount+1]; // p.vlist=vlist;
		
		TileData td=new TileData(0,1); // default to coarsest mode
		td.myTiles=new Tile[p.packDCEL.sizeLimit+1];

		Iterator<Integer> vlt=vlist.iterator();
		int stop=0;
		int tcount=0;
		while (vlt.hasNext() && stop==0) {
			int v=vlt.next();
			if (util[v]!=0 || p.isBdry(v)) {
				stop=v;
				break;
			}
			int num=p.countFaces(v);
			Tile tile=td.myTiles[++tcount]=new Tile(td,num);
			tile.tileIndex=tcount;
			tile.baryVert=v;
			util[v]=-tcount;
			int[] flower=p.getFlower(v);
			for (int j=0;j<num;j++) {
				int k=flower[j];
				tile.vert[j]=k;
				util[k]=1;
			}
		}
		
		if (stop!=0) {
			throw new CombException("error, vert "+stop+", in 'viaFlowers'");
		}
		
		// build tileFlowers
		for (int t=1;t<=tcount;t++) {
			Tile tile=td.myTiles[t];
			tile.tileFlower=new int[tile.vertCount][2];
			for (int j=0;j<tile.vertCount;j++) {
				int ww=tile.vert[j];
				int pw=tile.vert[(j+1)%tile.vertCount];
				int ov=p.getOppVert(tile.baryVert,ww);
				if (util[ov]<0) {
					tile.tileFlower[j][0]=-util[ov];
					tile.tileFlower[j][1]=p.nghb(ov,pw);
				}
			}
		}
		
		td.tileCount=tcount; 
		
		boolean debug=false;
		if (debug) // debug=true;
			DebugHelp.printtileflowers(td);
		
		return td;
	}
	
	/** 
	 * Recursively copy 'TileData' tree: go recursively through 'myTiles' and their 
	 * 'myTileData's, recursively set 'packData' if 'parentPD' is given, set 'parentTile' 
	 * if given (e.g., when copying 'myTileData'). 'dualTileData' and 'quadTileData' are
	 * set to null.
	 * @param tData TileData, original
	 * @param parentPD PackData, original PackData, passed recursively, may be null
	 * @param parentTile Tile, may be null at top level
	 * @return new TileData
	 */
	public static TileData recursiveTDcopy(TileData tData,PackData parentPD,Tile parentTile) {
		if (tData==null || tData.tileCount<=0)
			return null;
		TileData newTD=new TileData(parentPD,parentTile,tData.tileCount,tData.builtMode);
		for (int t=1;t<=tData.tileCount;t++) {
			Tile tile=tData.myTiles[t];
			Tile newTile=tile.clone(newTD);
			if (tile.myTileData!=null) {
				newTile.myTileData=recursiveTDcopy(tile.myTileData,parentPD,newTile);
			}
			newTD.myTiles[t]=newTile;
		}
		newTD.subRules=tData.subRules;
		return newTD;
	}

	/**
	 * Clone 'tileData'; 'packData' and 'parentTile' 
	 * set to null and may need to be set by calling 
	 * routine. Also, 'subRule' and 'vertMap' must be 
	 * updated separately.
	 * @return new TileData
	 */
	public TileData copyMyTileData() {
		TileData newTD = TileData.recursiveTDcopy(this,null,null);
		if (dualTileData!=null) {
			dualTileData.dualTileData=null;
			dualTileData.quadTileData=null;
			newTD.dualTileData=dualTileData.copyMyTileData();
		}
		if (quadTileData!=null) {
			quadTileData.dualTileData=null;
			quadTileData.quadTileData=null;
			newTD.quadTileData=quadTileData.copyMyTileData();
		}
		if (wgTiles!=null) {
			newTD.wgTiles=new Tile[wgTileCount+1];
			for (int j=1;j<=wgTileCount;j++)
				newTD.wgTiles[j]=wgTiles[j].clone(newTD);
		}
		return newTD;
	}
	
	/**
	 * Create new 'TileData' with copies of 'this' tiles,
	 * but only copy 'vert', 'vertCount','augVert','augVertCount,
	 * 'tileType', 'baryVert', 'mark'.
	 * Thus, no 'myTileData', 'TDparent', 'utilFlag', or 'wgInidces'.
	 * @return TileData
	 */
	public TileData copyBareBones() {
		TileData outTD=new TileData(packData,tileCount,builtMode);
		for (int t=1;t<=tileCount;t++) {
			Tile tile=myTiles[t];
			Tile newTile=new Tile(tile.vertCount);
			for (int j=0;j<tile.vertCount;j++)
				newTile.vert[j]=tile.vert[j];
			if (tile.augVert!=null) {
				newTile.augVert=new int[tile.augVertCount];
				for (int j=0;j<tile.augVertCount;j++)
					newTile.augVert[j]=tile.augVert[j];
			}
			newTile.augVertCount=tile.augVertCount;
			newTile.tileType=tile.tileType;
			newTile.tileIndex=t;
			newTile.baryVert=tile.baryVert;
			newTile.mark=tile.mark;
			newTile.tileFlower=tile.tileFlower;
			outTD.myTiles[t]=newTile;
		}
		return outTD;
	}
	
	/**
	 * Given vertex, see if it's baryVert for some 'myTiles' entry
	 * @param bc int, baryVert
	 * @return Tile, null if not found
	 */
	public Tile amIaTile(int bc) {
		if (tileCount==0 || myTiles==null)
			return null;
		for (int t=1;t<=tileCount;t++)
			if (myTiles[t].baryVert==bc)
				return myTiles[t];
		return null;
	}
	
	/**
	 * Return list of petal indices of tile s in the flower of tile t.
	 * Careful, not the tile indices, just petal indices in the flower.
	 * Note: tile may NOT neighbor itself.
	 * May be empty if s not a neighbor at all; null on error.
	 * @param t int, tileIndex
	 * @param s int, tileIndex	
	 * @return Vector<Integer> petal indices of s in flower of t.
	 */
	public Vector<Integer> nghb_tile_indices(int t,int s) {

		// check for bad data
		if (t==s || tileCount<=0 || t<1 || t>tileCount || 
				s<1 || s>tileCount)
			return null;
		if (myTiles[t]==null || myTiles[t].tileIndex<1 || 
				myTiles[t].vertCount<=0)
			return null;
		
		Vector<Integer> petals=new Vector<Integer>(3);
		for (int j=0;j<myTiles[t].vertCount;j++)
			if (myTiles[t].vert[j]==s)
				petals.add(j);
		return petals;
	}
	
	/**
	 * Use 'subdivisionRule' info to tile 'vert' and 
	 * 'augVert' data for the specified tile
	 * @param tIndx int, tile index
	 * @return int
	 */
	public int newVertAug(int tIndx) {
		Tile tile=myTiles[tIndx];
		if (subRules==null || tile.myTileData==null || subRules.tileRules==null)
			throw new CombException("Info missing for "+
					"'vert'/'augVert' for tile "+tIndx);
		TileRule myRule=subRules.tileRules.get(subRules.type2Rule.findW(tile.tileType));
		NodeLink bdrylink=new NodeLink();
		
		tile.vert=new int[tile.vertCount];
		for (int ej=0;ej<myRule.edgeCount;ej++) {
			EdgeRule myEdgeRule=myRule.edgeRule[ej];
			int mec=myEdgeRule.subEdgeCount-1;
			// recall tileedge are listed clockwise
			for (int k=mec;k>=0;k--) {
				int[] tileedge=myEdgeRule.tileedge[k];
				NodeLink sublink=tile.myTileData.getEdgeVerts(tileedge[0],tileedge[1]);
				sublink.remove(sublink.size()-1); // remove entry
				if (k==mec)
					tile.vert[ej]=sublink.get(0);
				bdrylink.abutMore(sublink);
			}
		}
		tile.augVertCount=bdrylink.size();
		tile.augVert=new int[tile.augVertCount];
		int tick=0;
		Iterator<Integer> bls=bdrylink.iterator();
		while (bls.hasNext())
			tile.augVert[tick++]=bls.next();
		
		return tile.augVertCount;
	}

	/**
	 * Given 'tIndx' tile index and 'eIndx' edge index, 
	 * return the 'augVert' list cclw along that edge, including
	 * both first and last vertex.
	 * 
	 * If this tile is not subdivided, then 'augVert' should 
	 * already be in place. If it is subdivided, make
	 * recursive call along edge subedges.
	 * 
	 * @param tIndx int, tile index 
	 * @param eIndx int, edge index in that tile
	 * @return NodeLink
	 */
	public NodeLink getEdgeVerts(int tIndx,int eIndx) {
		
		Tile tile=myTiles[tIndx];
		if ((subRules==null || tile.myTileData==null) && tile.augVert==null) {
			throw new CombException("'augVert' is missing for tile "+tIndx);
		}
		
		// no subdivisions
		if (tile.myTileData==null) 
			return tile.findAugEdge(eIndx);
		
		// else recursively process the tiles along this edge
		NodeLink list=new NodeLink();
		TileRule tRule=subRules.tileRules.get(subRules.type2Rule.findW(tile.tileType));
		EdgeRule eRule=tRule.edgeRule[eIndx];
		int catchlast=-1;
		for (int e=eRule.subEdgeCount-1;e>=0;e--) {
			int subindx=eRule.tileedge[e][0];
			int subeindx=eRule.tileedge[e][1];
			NodeLink nlk=tile.myTileData.getEdgeVerts(subindx,subeindx);
			catchlast=nlk.get(nlk.size()-1);
			nlk.remove(nlk.size()-1);
			list.abutMore(nlk);
		}
		list.add(catchlast);
		return list;
	}

	/**
	 * Recursively set 'packData' element of 'TileData' and of its
	 * tile's 'myTileData'. Also do same for dual and quad TileData. 
	 * @param td TileData
	 * @param p PackData, may be null
	 * @return int 0 if 'td' is null 
	 */
	public static int setPackings(TileData td,PackData p) {
		if (td==null)
			return 0;
		int ans=1;
		td.packData=p;
		if (td.dualTileData!=null) {
			ans += TileData.setPackings(td.dualTileData,p);
		}
		if (td.quadTileData!=null) {
			ans += TileData.setPackings(td.quadTileData,p);
		}
		return ans;
	}

	/**
	 * Fill in the tileFlower information. Main 
	 * complications are due to possible unigons 
	 * and digons.
	 * @param tData TileData
	 * @return int count
	 */
	public static int setTileFlowers(TileData tData) {

		// wipe out current 'tileFlower' data
		for (int t = 1; t <= tData.tileCount; t++) {
			Tile tile = tData.myTiles[t];
			tile.tileFlower = null;
		}
		
		int[] done=new int[tData.tileCount+1];
		
		// Start by listing any unigons/digons (vertCount==1/2)
		ArrayList<Integer> unigons=new ArrayList<Integer>(0);
		ArrayList<Integer> digons=new ArrayList<Integer>(0);
		for (int t = 1; t <= tData.tileCount; t++) {
			Tile tile=tData.myTiles[t];
			if (tile.vertCount==1)
				unigons.add(tile.tileIndex);
			else if (tile.vertCount==2)
				digons.add(tile.tileIndex);
		}
		
		// handle unigons, which have only one nghb
		if (unigons.size()>0) {
			Iterator<Integer> uis=unigons.iterator();
			while(uis.hasNext()) {
				int ug=uis.next();
				Tile unitile=tData.myTiles[ug];
				int node=unitile.vert[0];
				
				// search for nghb, must have two successive 'node' entries
				for (int t=1;t<=tData.tileCount;t++) {
					Tile tile=tData.myTiles[t];
					int spot=-1;
					if ((spot=tile.isTileEdge(node, node))>0) {
						tile.tileFlower=new int[tile.vertCount][2];
						tile.tileFlower[spot][0]=unitile.tileIndex;
						tile.tileFlower[spot][1]=0;
						unitile.tileFlower=new int[1][2];
						unitile.tileFlower[0][0]=tile.tileIndex;
						unitile.tileFlower[0][1]=spot;
					}
					if (spot==-1)
						throw new CombException(
							"Unigon tile "+unitile.tileIndex+
							" has no neighboring tile.");
				}
				done[unitile.tileIndex]=1;
			} // done with while
		}  // done with unigons
		
		if (digons.size()>0) {
			Iterator<Integer> dis=digons.iterator();
			while(dis.hasNext()) {
				int dg=dis.next();
				Tile ditile=tData.myTiles[dg];
				int v=ditile.vert[0];
				int w=ditile.vert[1];

				if (ditile.tileFlower==null) {
					if (v==w)
						throw new CombException(
								"digon "+ditile.tileIndex+" unigon nghb should"
										+ " have been picked up already");
					ditile.tileFlower=new int[2][2];
				}
				
				if (ditile.tileFlower[0][0]==0) {
					for (int t=1;t<=tData.tileCount;t++) {
						Tile tile=tData.myTiles[t];
						int thit=-1;
						if ((thit=tile.isTileEdge(w,v))>0) {
							if (tile.tileFlower==null) 
								tile.tileFlower=new int[tile.vertCount][2];
							tile.tileFlower[thit][0]=t;
							tile.tileFlower[thit][1]=0;
							ditile.tileFlower[0][0]=tile.tileIndex;
							ditile.tileFlower[0][1]=thit;
						}
					}
				}
				if (ditile.tileFlower[1][0]==0) {
					for (int t=1;t<=tData.tileCount;t++) {
						Tile tile=tData.myTiles[t];
						int thit=-1;
						if ((thit=tile.isTileEdge(v,w))>0) {
							if (tile.tileFlower==null) 
								tile.tileFlower=new int[tile.vertCount][2];
							tile.tileFlower[thit][0]=t;
							tile.tileFlower[thit][1]=1;
							ditile.tileFlower[1][0]=tile.tileIndex;
							ditile.tileFlower[1][1]=thit;
						}
					}
				}
				done[dg]=1;
			}
		} // done with digons
				
		// go through each edge of each tile
		for (int t = 1; t <= tData.tileCount; t++) {
			if (done[t]!=0)
				continue;
			Tile tile = tData.myTiles[t];
			if (tile.tileFlower==null)
				tile.tileFlower=new int[tile.vertCount][2];
			
			for (int j=0;j<tile.vertCount;j++) {

				// if this edge is not already settled
				if (tile.tileFlower[j][0] == 0) {
					int v = tile.vert[j];
					int w = tile.vert[(j+1)%tile.vertCount];

					// check only tiles with larger indices
					boolean hit=false;
					for (int tj=t;(tj<=tData.tileCount && !hit);tj++) {
						Tile petile = tData.myTiles[tj];
						int indx = petile.isTileEdge(w,v);
						if (indx >= 0) {
							if (petile.tileFlower==null)
								petile.tileFlower=new int[petile.vertCount][2];
							int[] tfind = petile.tileFlower[indx];
							if ((tfind[0] > 0 && tfind[0] != t)
									|| (tfind[0] == t && tfind[1] != j))
								throw new CombException("mismatching of tiles "
										+ tile.tileIndex + " and "
										+ petile.tileIndex);
							petile.tileFlower[indx][0] = t;
							petile.tileFlower[indx][1] = j;
							tile.tileFlower[j][0] = tj;
							tile.tileFlower[j][1] = indx;
							hit = true;
						}
					}
				}
			}
			done[tile.tileIndex]=1;
		} // with all tiles
		// tData.flowerConsistency(); DebugHelp.printtileflowers(tData);
		return 1;
	}

	/**
	 * Are the 'tileFlower's consistent? 
	 * @return boolean
	 */
	public boolean flowerConsistency() {
		try {
			for (int t=1;t<=tileCount;t++) {
				Tile tile=myTiles[t];
				for (int e=0;e<tile.vertCount;e++) {
					int nghb=tile.tileFlower[e][0];
					if (nghb>0) {
						int nei=tile.tileFlower[e][1];
						
						// do you think that you share edge 'e' with
						//   nghb tile index 'nghb' across its 'nei' edge? 
						//   Does it agree?
						if (myTiles[nghb].tileFlower[nei][0]!=t)
							return false;
					}
						
				}
			}
		} catch(Exception ex) {
			System.err.println("flowerConsistency exception: "+ex.getMessage());
			return false;
		}
		return true;
	}
	
}
