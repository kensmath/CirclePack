package tiling;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import exceptions.CombException;
import komplex.EdgeSimple;
import komplex.KData;
import listManip.EdgeLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackCreation;
import packing.PackData;
import packing.RData;

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
 * Tiledata to some subdivision depth using subdivision rules. The
 * construction then gives a hierarchy of tilings, stored in 'gradedTileData'.
 * This depends on 'buildMode', which records the 'mode' setting of the
 * stored structure and be redone if mode is changed. 'gradedTileData'
 * is kept ONLY in 'canonicalPack.tileData'; other instances of 'TileData'
 * will have 'gradedTileData' set to null. When some depth of a tiling tree
 * is needed, it is put in 'packData.tilingData', but it does not contain full 
 * information. In particular, the full hierarchy cannot be saved to a file,
 * so it must be rebuilt if canonicalPack is lost.
 *  
 * 'gradedTileData' is generated to some depth in the 'build_sub' and 'subtile' 
 * commands of 'ConformalTiling'; so 'canonicalPack.tileData.myTiles' has pointers 
 * to the leaf tiles in the hierarchy. In general, however, 'packData.TileData' is
 * pulled out of the hierarchy to some specified depth. If not full depth, then
 * its 'myTiles' are copies (see 'copyBareBones') only at the specified tile depth.
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
	public Tile []myTiles;       // array of tiles, index starts at 1
	public int wgTileCount; 	 // number of white/grey tiles (2n for each n-gon tile)
	public Tile []wgTiles;		 // array of white/grey tiles, index starts at 1
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
	 * Converts tiling via 'tileFlowers' to a simple PackData; in 
	 * particular, there is just one circle for each tile (not a circle
	 * for each vertex of each tile). This was needed in looking at
	 * simulated 2D 'glasses'. 
	 * TODO: we assume the tiling has no boundary.
	 * @param td TileData
	 * @return PackData
	 */
	public static PackData tiles2simpleCP(TileData td) {
		PackData newPack=new PackData(null);
		boolean seemsOK=true;
		if (td==null || td.myTiles==null)
			return null;
		newPack.alloc_pack_space(td.tileCount+100,false);
		newPack.nodeCount=td.tileCount;
		for (int t=1;(t<=td.tileCount && seemsOK);t++) {
			Tile tile=td.myTiles[t];
			int num=tile.vertCount;
			if (tile.tileFlower==null)
				seemsOK=false;
			else {
				int []flower=new int[num+1];
				for (int j=0;(j<num && seemsOK);j++) {
					int pet=tile.tileFlower[j][0];
					if (pet<=0) {
						CirclePack.cpb.errMsg("in 'tiles2simpleCP', seems the tilings has boundary");
						seemsOK=false;
					}
					flower[j]=pet;
				}
				flower[num]=flower[0]; // close up
				newPack.kData[t].flower=flower;
				newPack.kData[t].num=num;
				newPack.rData[t].rad=.5;
			}
		}
		if (!seemsOK) {
			throw new CombException("");
		}
		newPack.status=true;
		try {
			int sc=newPack.setCombinatorics();
			if (sc<=0)
				CirclePack.cpb.errMsg("Comb. error: check if tiling is trivalent");
			newPack.set_aim_default();
			newPack.fillcurves();
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("combinatorial problem with simple packing: check if tiling is not trivalent.");
		}
		return newPack;
	}

	/**
	 * Create a packing based on 'myTiles' data, as when reading
	 * from a TILECOUNT file. There are two types of data input files: 
	 * 'TILES:' and 'TILEFLOWERS:' (mutually exclusive). Normally,
	 * need 'TILEFLOWERS:' if there are unigons, digons, slits, and/or
	 * self-neighboring (so some vertex occurs twice around the tile).
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
			throw new CombException("Tile data doesn't seem to have vertices or tileFlowers");

		// in 'TILEFLOWERS' case, we need to set consistent vert lists first
		if (td.myTiles[1].vert[0]<=0) {
			try {
				if (TileBuilder.tileflowers2verts(td)<=0)
					throw new CombException();
			} catch (Exception ex) {
				throw new CombException("failed using 'tileFlower'");
			}
		}
		
		// Are there unigons, digons, slits, self-neighboring? 
		boolean special=false;
		for (int t=1;t<=td.tileCount && !special;t++) {
			Tile tile=td.myTiles[t];
			for (int j=0;j<tile.vertCount;j++) {
				int v=tile.vert[j];
				if (tile.vertCount==1)
					special=true;
				else {
					for (int k=0;k<j;k++)
						if (tile.vert[k]==v)
							special=true;
				}
			}
		}
		
		if (special) {
			TileBuilder tileBuilder=new TileBuilder(td);
			PackData p=tileBuilder.fullfromFlowers();
			if (p==null || p.tileData==null || p.nodeCount<=0)
				throw new CombException("'tileBuilder' seems to have failed");
			p.complex_count(false);
			return p;
		}
		
		// find vertices that occur among tile 'vert' lists
		int maxnode=0;
		for (int f=1;f<=td.tileCount;f++) {
			Tile tile=td.myTiles[f];
			for (int k=0;k<tile.vertCount;k++)
				maxnode = (tile.vert[k]>maxnode) ? tile.vert[k]:maxnode;
		}
		
		// count in how many tiles each vert index is used
		int []ticks=new int[maxnode+1];
		for (int f=1;f<=td.tileCount;f++) {
			Tile tile=td.myTiles[f];
			for (int k=0;k<tile.vertCount;k++) {
				int vindx=tile.vert[k];
				
				// avoid double counting if vert re-occurs in 'tile.vert'
				boolean rehit=false;
				for (int jk=0;jk<k && !rehit;jk++) {
					if (vindx==tile.vert[jk])
						rehit=true;
				}
				if (!rehit)
					ticks[vindx]++;
			}
		}

		PackData thePack=new PackData(null);
		thePack.alloc_pack_space(maxnode+td.tileCount,false);
		
		// may need to adjust count later
		thePack.nodeCount=maxnode+td.tileCount;
		
		// build up unordered 'rawflower' for each vertex
		int [][]rawflower=new int[maxnode+1][];
		// allocate space: the 0-entry stores the number of entries so far
		for (int v=1;v<=maxnode;v++) 
			rawflower[v]=new int[ticks[v]+1];
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			for (int j=0;j<tile.vertCount;j++) {
				int v=tile.vert[j];
				rawflower[v][0] +=1; // increment
				rawflower[v][rawflower[v][0]]=t; // add t to list
			}
		}
		
		// build up flower for each vertex
		for (int v=1;v<=maxnode;v++) {
			int numt=rawflower[v][0];
			if (numt>0) {
				// catalog edges from v in this tile bdry
				// edge = (u, w) means (v,u) and (w,v) are ccw edges of this tile
				EdgeSimple []nextprev=new EdgeSimple[numt];
				for (int j=1;j<=numt;j++) {
					Tile tile=td.myTiles[rawflower[v][j]];
					int vj=tile.vertIndx(v);
					int u=tile.vert[(vj+1)%tile.vertCount];
					int w=tile.vert[(vj-1+tile.vertCount)%tile.vertCount];
					nextprev[j-1]=new EdgeSimple(u,w);
				}
			
				// build a vector putting in order
				Vector<Integer> flow=new Vector<Integer>(6);
				int []tickoff=new int[numt+1];
			
				// add first pair of petals
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
								flow.add(last=edge.w);
								tickoff[pj]=1;
								hits++;
							}
							else if (edge.w==first) {
								flow.insertElementAt(first=edge.v,0);
								tickoff[pj]=1;
								hits++;
							}
						}
					}
				} // end of while
				
				// might fail to use all tiles if the faces around
				//   v form more than one linked component
				if (hits<numt) {
					CirclePack.cpb.errMsg("some tile flowers incomplete");
				}
				
				// did we bomb?
				if (safety==0)
					throw new CombException("safety'ed out in 'buildVertFlowers'");
			
				// build 'kData' and 'rData'.
				thePack.kData[v]=new KData();
				thePack.kData[v].num=numt;
				thePack.kData[v].flower=new int[numt+1];
				for (int k=0;k<=numt;k++)
					thePack.kData[v].flower[k]=flow.get(k);
				if (thePack.kData[v].flower[0]==thePack.kData[v].flower[numt])
					thePack.setBdryFlag(v,0);
				else 
					thePack.setBdryFlag(v,1);
				thePack.rData[v]=new RData();
				thePack.rData[v].rad=.5;
			}
			
			// TODO: we do not check that we get a connected pattern or
			//   that we use all tiles.
		} // end of for loop
		
		// signal indices we skip via null settings
		for (int v=1;v<=thePack.nodeCount;v++) 
			if (v>maxnode || ticks[v]==0) {
				thePack.kData[v]=null;
				thePack.rData[v]=null;
			}
		
		// Using kData create the barycentric triangulation
		VertexMap indmap=new VertexMap(); // keep (tileIndex,baryVert)
		
		// iterate through the tiles to add new vertices, fix flowers
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			int nextgap=0;
			for (int g=1;(g<=thePack.nodeCount && nextgap==0);g++)
				if (thePack.kData[g]==null)
					nextgap=g;
			tile.baryVert=nextgap;
			indmap.add(new EdgeSimple(t,nextgap));
			int []tmpflower=new int[tile.vertCount+1];
			Complex cent=new Complex(0.0);
			double rad=0.0;
			for (int j=0;j<tile.vertCount;j++) {
				int v=tile.vert[j];
				tmpflower[j]=v;
				int w=tile.vert[(j-1+tile.vertCount)%tile.vertCount];
				int indx=thePack.nghb(v,w);
				thePack.insert_petal(v, indx, nextgap);
				cent=cent.add(thePack.getCenter(v));
				rad +=thePack.rData[v].rad;
			}
			tmpflower[tile.vertCount]=tmpflower[0];
			
			// put into the packing
			thePack.kData[nextgap]=new KData();
			thePack.kData[nextgap].num=tile.vertCount;
			thePack.kData[nextgap].flower=tmpflower;
			thePack.setBdryFlag(nextgap,0);
			thePack.setVertMark(nextgap,nextgap); // marked with vert index
			thePack.rData[nextgap]=new RData();
			thePack.setCenter(nextgap,cent.divide((double)(tile.vertCount)));
			thePack.setAim(nextgap,Math.PI*2.0);
			thePack.rData[nextgap].rad=rad/((double)tile.vertCount);
		} // end of 'while' for tiles
		
		// adjust indices if there are numbering gaps: everything should
		//    be contiguous through tileCount, but there may be gaps above.
		//    indices are moved down to fill gaps, but maintain relative order.
		int []newIndx=new int[thePack.nodeCount+1];
		int spot=0;
		for (int v=1;v<=thePack.nodeCount;v++) {
			if (thePack.kData[v]!=null) 
				newIndx[v]=++spot;
		}
		
		// were there gaps?
		if (spot<thePack.nodeCount) {
			
			// first, renumber kData and rData
			for (int v=1;v<=thePack.nodeCount;v++) {
				int w=newIndx[v];
				if (w!=0 && w!=v) {
					thePack.kData[w]=thePack.kData[v];
					thePack.kData[v]=null;
					thePack.rData[w]=thePack.rData[v];
					thePack.rData[v]=null;
				}
			}
			
			// new nodeCount
			thePack.nodeCount=spot;
			
			// fix vertex flowers
			for (int v=1;v<=thePack.nodeCount;v++) {
				for (int j=0;j<=thePack.countFaces(v);j++) {
					int k=thePack.kData[v].flower[j];
					thePack.kData[v].flower[j]=newIndx[k];
				}
			}
			
			// fix tile 'vert' lists
			for (int t=1;t<=td.tileCount;t++) {
				Tile tile=td.myTiles[t];
				for (int j=0;j<tile.vertCount;j++) {
					int k=tile.vert[j];
					tile.vert[j]=newIndx[k];
				}
				
				// tileIndex should not have changed, but .....
				int ti=tile.tileIndex;
				tile.tileIndex=newIndx[ti];
			}
			
			// fix 'indmap' baryVerts
			Iterator<EdgeSimple> im=indmap.iterator();
			while (im.hasNext()) {
				EdgeSimple edge=im.next();
				edge.w=newIndx[edge.w];
			}
		}
		
		// recreate missing KData/RData spots
		for (int v=thePack.nodeCount+1;v<thePack.sizeLimit+1;v++) {
			thePack.kData[v]=new KData();
			thePack.rData[v]=new RData();
		}
		
		// allocate 'tileFlowers' first
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			tile.tileFlower=new int[tile.vertCount][2];
		}
		
		// catalog the tiles
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
						
			// go around vertices, see if there's a tile across edge.
			for (int k=0;k<tile.vertCount;k++) {
				int v=tile.vert[k];
				int w=tile.vert[(k+1)%tile.vertCount];
				int idx=thePack.nghb(v,w);
				int dirx=idx-1;
				if (dirx<0 && thePack.kData[v].flower[0]==thePack.kData[v].flower[thePack.countFaces(v)])
					dirx=thePack.countFaces(v)-1;
				if (dirx>=0) {
					int s=indmap.findV(thePack.kData[v].flower[dirx]);
					
					// if this is a tile, set 'tileFlower' of t and neighbor s
					if (s>0) {
						Tile nghbtile=td.myTiles[s];
						tile.tileFlower[k][0]=s;
						int nghbind=thePack.nghb(nghbtile.baryVert,w); // index of w in s
						tile.tileFlower[k][1]=nghbind;
						nghbtile.tileFlower[nghbind][0]=t;
						nghbtile.tileFlower[nghbind][1]=k;
					}
					else {
						tile.tileFlower[k][0]=0;
						tile.tileFlower[k][1]=0;
					}
				}
			}
		} // done cataloging

		// wrap up and return
		thePack.status=true;
		thePack.setAlpha(td.myTiles[1].baryVert);
		thePack.gamma=td.myTiles[1].vert[0];
		thePack.chooseAlpha();
		thePack.chooseGamma();
		thePack.setCombinatorics();
		thePack.hes=thePack.intrinsicGeom;
		thePack.set_aim_default();
		
		// want tile data, but duals, quads, wgTiles, and augmented vertices
		//   must be removed
		thePack.tileData=td.copyMyTileData();
		thePack.tileData.dualTileData=thePack.tileData.quadTileData=null;
		thePack.tileData.wgTiles=null;
		for (int t=1;t<=thePack.tileData.tileCount;t++) 
			thePack.tileData.myTiles[t].augVert=null;
		
		return thePack;
	}
	

	/**
	 * NOTE: this is being replaced (10/22/13) by 'tiles2FullBary'.
	 * 
	 * Create the "barycentric tiling" for given triangulation. This 
	 * is the usual topological barycentric triangulation of the tiling (as a
	 * cell complex). A clone of 'tileData' is attached to the packing. 
	 * 
	 * Three construction stages: (1) build a packing having a 
	 * barycenter vert for each tile; (2) then split every tile edge 
	 * with a new vertex, create 'dual', 'quad', and 'wgTiles' 
	 * tileData; (3) barycentrically subdivide every face.
	 * 
	 * The vertex 'mark's in the new packing are set to {1,2,3} for
	 * values under Belyi map {tile baryVert (value infty), original tile 
	 * corner (value 0), and newvert (value 1)}, resp. Each face is now 
	 * a hex flower, and its center is given mark = -1.
	 * 
	 * wgTiles are created for each original tile, also for 'dualTileData' 
	 * and 'quadTileData'. 'wgTiles' are indexed from 0, type is set
	 * 1=original, 2=dual, 3=quad. Each has vert[3] positively oriented, 
	 * starting with baryVert (vertex mark=1); this tile 'mark' sign shows 
	 * whether positively or negatively oriented.
	 *  
	 * @param td TileData
	 * @return PackData, null on error
	 */
	public static PackData tiles2canonical(TileData td) {

		// do we have tiles
		if (td==null || td.tileCount<=0) {
			return null;
		}

		// check for: 
		//   * any tile that is uni-gon or bi-gon  
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
		// TODO: need new routine to create the packing 
		if (special) {
			throw new CombException("Not yet ready for 'special' tile combinatorics");
		}
			
		// create minimal packing for given 'TileData'
		PackData newPD=TileData.tiles2packing(td);
		TileData workingTD=newPD.tileData;

		// clear 'mark's
		for (int i=1;i<=newPD.nodeCount;i++)
			newPD.setVertMark(i,0);
		
		// go through tiles, split edges, set marks;
		//    splitting edge should prevent it being selected
		//    to be split again from the other side.
		for (int t=1;t<=workingTD.tileCount;t++) {
			Tile tile=workingTD.myTiles[t];
			newPD.setVertMark(tile.baryVert,1);
			int w=tile.vert[0];
			newPD.setVertMark(w,2);
			int v=w;
			for (int j=1;j<=tile.vertCount;j++) {
				v=w;
				if (j==tile.vertCount)
					w=tile.vert[0];
				else 
					w=tile.vert[j];
				newPD.setVertMark(w,2);
				if (newPD.split_edge(v,w)>0)
					newPD.setVertMark(newPD.nodeCount,3);
			}
		}
		
		newPD.setCombinatorics();

/*		
		// create the 'wgTile' (white/grey) array for each tile
		for (int t=1;t<=workingTD.tileCount;t++) {
			Tile tile=workingTD.myTiles[t];
//			tile.wgTiles=new Tile[2*tile.vertCount];
			int num=newPD.getNum(tile.baryVert);
			if (newPD.isBdry(tile.baryVert) || num!=(2*tile.vertCount)) {
				CirclePack.cpb.errMsg("Tile "+t+" (center "+tile.baryVert+") "+
						"flower has problem");
				break; // out of 'for' loop
			}
			
			// adjust flower of baryVert so flower[0] = vert[0] of tile
			int offset=newPD.nghb(tile.baryVert,tile.vert[0]);
			if (offset>0) {
				int []newflower=new int[num+1];
				for (int k=0;k<num;k++)
					newflower[k]=newPD.kData[tile.baryVert].flower[(k+offset)%num];
				newflower[num]=newflower[0];
				newPD.kData[tile.baryVert].flower=newflower;
			}
			
			for (int j=0;j<tile.vertCount;j++) {
				// two subFaces for each edge of tile
				tile.wgTiles[2*j]=new Tile(3);
				tile.wgTiles[2*j+1]=new Tile(3);
				tile.wgTiles[2*j].type=1;
				tile.wgTiles[2*j+1].type=1;
				
				// first is positively oriented
				tile.wgTiles[2*j].vert[0]=tile.baryVert;
				tile.wgTiles[2*j].vert[1]=newPD.kData[tile.baryVert].flower[2*j];
				tile.wgTiles[2*j].vert[2]=newPD.kData[tile.baryVert].flower[2*j+1];
				tile.wgTiles[2*j].mark=1;
				
				// second is negatively oriented
				tile.wgTiles[2*j+1].vert[0]=tile.baryVert;
				tile.wgTiles[2*j+1].vert[1]=newPD.kData[tile.baryVert].flower[2*j+1];
				tile.wgTiles[2*j+1].vert[2]=newPD.kData[tile.baryVert].flower[(2*j+2)%num];
				tile.wgTiles[2*j+1].mark=-1;
			}
		}
*/
		
		try {
			// fill 'quadTile' and 'dualTile' 
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
			dcount=1;
			qcount=1;
			for (int v=1;v<=newPD.nodeCount;v++) {
				
				// dual tile for each original corner vertex
				if (newPD.getVertMark(v)==2) {
					int []vert=null;
					if (newPD.isBdry(v)) { // this is bdry tile
						
						// vert list starts with v, then bary center of 
						//    bdry edge from v, around through tiles to
						//    end with bary center of last bdry edge from v
						int num=newPD.countFaces(v)/2;
						vert=new int[num+3];
						vert[0]=v;
						vert[1]=newPD.kData[v].flower[0];
						for (int j=0;j<num;j++) 
							vert[2+j]=newPD.kData[v].flower[1+2*j];
						vert[num+2]=newPD.kData[v].flower[newPD.countFaces(v)];
						workingTD.dualTileData.myTiles[dcount]=new Tile(workingTD.dualTileData,num+3);
					}
					else {
						int num=newPD.countFaces(v)/2;
						vert=new int[num];
						int tick=0;
						for (int j=0;j<newPD.countFaces(v);j++) {
							int k=newPD.kData[v].flower[j];
							if (newPD.getVertMark(k)==1)
								vert[tick++]=k;
						}
						workingTD.dualTileData.myTiles[dcount]=new Tile(workingTD.dualTileData,num);
					}						
					
					workingTD.dualTileData.myTiles[dcount].baryVert=v; 
					workingTD.dualTileData.myTiles[dcount].vert=vert;
					dcount++;
				} 
				
				// quad tile for each edge
				else if (newPD.getVertMark(v)==3) {
					
					int []vert=new int[4];
						
					if (newPD.isBdry(v)) { // bdry? num=2
						vert[0]=v;
						for (int j=0;j<=2;j++)
							vert[j+1]=newPD.kData[v].flower[j];
					}
					else { // interior? num=4
						for (int j=0;j<4;j++)
							vert[j]=newPD.kData[v].flower[j];
					}
						
					workingTD.quadTileData.myTiles[qcount]=new Tile(workingTD.quadTileData,4);
					workingTD.quadTileData.myTiles[qcount].baryVert=v;
					workingTD.quadTileData.myTiles[qcount].vert=vert;

					qcount++;
				}
			
			} // done with for

/*			
			// create 'wgTiles' for 'dualTileData'
			for (int t=1;t<=workingTD.dualTileData.tileCount;t++) {
				Tile tile=workingTD.dualTileData.myTiles[t];
				int v=tile.baryVert;
				
				// create the wgtiles
				tile.wgTiles=new Tile[newPD.getNum(v)]; // number of wgTiles
				for (int j=0;j<newPD.getNum(v);j++) {
					tile.wgTiles[j]=new Tile(3);
					tile.wgTiles[j].type=2;
				}
				
				// alternate grey/white, start with grey, correct with offset
				int offset=1;
				if (!newPD.isBdry(v)) {
					if (newPD.getVertMark(newPD.kData[v].flower[0])==1)
						offset=-1; // to get started with grey
				}
				// define 2 at a time, grey/white
				for (int j=0;j<(newPD.getNum(v)/2);j++) {
					tile.wgTiles[2*j].vert[0]=v;
					tile.wgTiles[2*j].vert[1]=newPD.kData[v].flower[2*j];
					tile.wgTiles[2*j].vert[2]=newPD.kData[v].flower[2*j+1];
					tile.wgTiles[2*j].mark=offset;
					tile.wgTiles[2*j+1].vert[0]=v;
					tile.wgTiles[2*j+1].vert[1]=newPD.kData[v].flower[2*j+1];
					tile.wgTiles[2*j+1].vert[2]=newPD.kData[v].flower[2*j+2];
					tile.wgTiles[2*j+1].mark=-1*offset;
				}
			}
			
			// create 'wgTiles' for 'quadTileData'
			for (int t=1;t<=workingTD.quadTileData.tileCount;t++) {
				Tile tile=workingTD.quadTileData.myTiles[t];
				int v=tile.baryVert;
				
				// create the wgtiles
				tile.wgTiles=new Tile[newPD.getNum(v)]; // should be 2 or 4 wgTiles
				for (int j=0;j<newPD.getNum(v);j++) {
					tile.wgTiles[j]=new Tile(3);
					tile.wgTiles[j].type=3;
				}
				
				// alternate grey/white, start with grey, correct with offset
				int offset=1;
				if (!newPD.isBdry(v)) {
					if (newPD.getVertMark(newPD.kData[v].flower[0])==1)
						offset=-1; // to get started with grey
				}
				// define 2 at a time, grey/white
				for (int j=0;j<(newPD.getNum(v)/2);j++) {
					tile.wgTiles[2*j].vert[0]=v;
					tile.wgTiles[2*j].vert[1]=newPD.kData[v].flower[2*j];
					tile.wgTiles[2*j].vert[2]=newPD.kData[v].flower[2*j+1];
					tile.wgTiles[2*j].mark=-1*offset;
					tile.wgTiles[2*j+1].vert[0]=v;
					tile.wgTiles[2*j+1].vert[1]=newPD.kData[v].flower[2*j+1];
					tile.wgTiles[2*j+1].vert[2]=newPD.kData[v].flower[2*j+2];
					tile.wgTiles[2*j+1].mark=offset;
				}
			}
*/			
			
		} catch (Exception ex) {
			workingTD.dualTileData=null;
			workingTD.quadTileData=null;
			CirclePack.cpb.errMsg("Failed to complete dual and quad tile data");
		}
		
		return newPD;
	}
	
	
	/**
	 * 10/22/13: Data structures have been inadequate. We need
	 * a canonical packing which is a barycentric refinement of
	 * the barycentric refinement of the original tiles: that is,
	 * 
	 * (1) start with tiles as ordered lists of vertices;
	 * (2) get packing by adding a barycenter to each tile and a 
	 *     barycenter to each tile edge.
	 * (3) bary_refine: barycentrically subdivide resulting triangles.
	 *  
	 * This complicates the data, but contends with two problems:
	 * 
	 * (I)  Otherwise some vertices would have only 2 neighbors.
	 *      Now we can handle all tilings from "drawings", e.g. dessins
	 * (II) We now have enough data to follow the tile edges; without
	 *      the extra vertices, combinatorial geodesics are ambiguous
	 *      due to the bary_refine of the faces in step (3).
	 * 
	 * A clone of 'tileData' is attached to the packing. 
	 * 
	 * Three construction stages: (1) build a packing having a 
	 * barycenter vert for each tile and for each tile edge;
	 * (2) barycentrically subdivide every face; (3) create
	 * 'dual', 'quad', and 'wgTiles' tileData. 
	 * 
	 * The vertex 'mark's in the new packing are set 
	 * to {1,2,3} for {tile baryVert,original tile corner, newvert}, 
	 * resp. Each face is now a hex flower, and its center is
	 * given mark = -1.
	 * 
	 * wgTiles are created for each original tile, also for 'dualTileData' 
	 * and 'quadTileData'. 'wgTiles' are indexed from 0, type is set
	 * 1=original, 2=dual, 3=quad. Each has vert[3] positively oriented, 
	 * starting with baryVert (vertex mark=1); the 'tile.mark' +- sign shows 
	 * whether positively or negatively oriented.
	 *  
	 * @param td TileData
	 * @return PackData, null on error or if no tiledata is given
	 */
	public static PackData tiles2FullBary(TileData td) {

		// do we have tiles?
		if (td==null || td.tileCount<=0) {
			return null;
		}
		
		// do we have vertices that are indexed? May not, eg., when
		//   data was read in 'TILEFLOWERS:' form.
		// This call indexes the vertices from 'tileFlowers'. Throw
		//   exception if these don't exist or give exception
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
		
		// TODO: need new routines to create the packing: see 'TileBuilder', 7/2014
		if (special) {
			TileBuilder tileBuilder=new TileBuilder(td);
			PackData p=tileBuilder.fullfromFlowers();
			if (p==null || p.tileData==null || p.nodeCount<=0)
				throw new CombException("'tileBuilder' seems to have failed");
			// see 'slitSpecial' for one case
//			throw new CombException("Not yet ready for these 'special' tile combinatorics");
		}

		// create minimal packing for given 'TileData'
		PackData newPD=TileData.tiles2packing(td);
		TileData workingTD=newPD.tileData;

		// clear 'mark's
		for (int i=1;i<=newPD.nodeCount;i++)
			newPD.setVertMark(i,0);
		
		// go through tiles, split edges, set marks;
		//    splitting edge should prevent it being selected
		//    to be split again from the other side.
		for (int t=1;t<=workingTD.tileCount;t++) {
			Tile tile=workingTD.myTiles[t];
			newPD.setVertMark(tile.baryVert,1);
			int w=tile.vert[0];
			newPD.setVertMark(w,2);
			int v=w;
			for (int j=1;j<=tile.vertCount;j++) {
				v=w;
				if (j==tile.vertCount)
					w=tile.vert[0];
				else 
					w=tile.vert[j];
				newPD.setVertMark(w,2);
				if (newPD.split_edge(v,w)>0)
					newPD.setVertMark(newPD.nodeCount,3);
			}
		}
		newPD.setCombinatorics();

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
		//   barycenters are numbered starting at 'bcbase'.
		// Save lots of work by building master list 'wgTiles' as
		//   as they are created, indexed from 1 by using the index
		//   of the barycenter circle and subtracting 'bcbase'.
		workingTD.wgTileCount=newPD.faceCount;
		newPD.bary_refine();
		int bcbase=newPD.nodeCount-workingTD.wgTileCount;
		workingTD.wgTiles=new Tile[workingTD.wgTileCount+1]; // indexed from 1 to wgCount

		// for each tile create 'augVert' list for tile, create and save 'wgTiles' 
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
			
			// adjust flower of baryVert so flower[0] is direction of vert[0]
			// TODO: to handle dual tilings consistently, have to consider 
			//       case that baryVert is on the boundary (and tile bdry 
			//       goes through it. Best is vert[0] is required to be 
			//       flower[0] in this case from the beginning.
			int offset=-1;
			int []myflower=newPD.kData[tile.baryVert].flower;
			for (int k=0;(k<newPD.kData[tile.baryVert].num && offset<0);k++) {
				int m=myflower[k];
				if (newPD.countFaces(m)==4 && newPD.nghb(m, tile.vert[0])>=0)
						offset=k;
			}
			if (offset<0) {
				CirclePack.cpb.errMsg("Tile "+t+" (center "+tile.baryVert+") "+
						"refined flower has some problem");
				break; // out of 'for' loop
			}
			if (offset>0) {
				int []newflower=new int[num+1];
				for (int k=0;k<num;k++)
					newflower[k]=newPD.kData[tile.baryVert].flower[(k+offset)%num];
				newflower[num]=newflower[0];
				newPD.kData[tile.baryVert].flower=newflower;
			}
			
			// build augmented vertices
			int newAugCount=4*tile.vertCount;
			int []augVs=new int[newAugCount];

			// visit sectors in succession
			for (int j=0;j<tile.vertCount;j++) {
				
				// two hex flower subfaces, first is white (positively oriented)
				Tile wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=1; // positively oriented 
				int baryV=newPD.kData[tile.baryVert].flower[4*j+1]; // its barycenter
				tile.wgIndices[2*j]=baryV-bcbase; // point to wgTile index
				workingTD.wgTiles[tile.wgIndices[2*j]]=wgtile; // put wgTile into master list
				int []itsflower=newPD.kData[baryV].flower;
				int indx=newPD.nghb(baryV,tile.baryVert);
				for (int k=0;k<3;k++) {
					wgtile.vert[k]=wgtile.augVert[2*k]=itsflower[(indx+2*k)%6];
					wgtile.augVert[2*k+1]=itsflower[(indx+2*k+1)%6];
				}
				augVs[4*j]=itsflower[(indx+2)%6];
				augVs[4*j+1]=itsflower[(indx+3)%6];
				
				
				// second is grey (negatively oriented)
				wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=-1; // negatively oriented 
				baryV=newPD.kData[tile.baryVert].flower[4*j+3]; // its barycenter
				tile.wgIndices[2*j+1]=baryV-bcbase; // point to wgTile index
				workingTD.wgTiles[tile.wgIndices[2*j+1]]=wgtile; // put wgTile into master list
				itsflower=newPD.kData[baryV].flower;
				indx=newPD.nghb(baryV,tile.baryVert);
				for (int k=0;k<3;k++) {
					wgtile.vert[k]=wgtile.augVert[2*k]=itsflower[(indx+2*k)%6];
					wgtile.augVert[2*k+1]=itsflower[(indx+2*k+1)%6];
				}
				augVs[4*j+2]=itsflower[(indx+2)%6];
				augVs[4*j+3]=itsflower[(indx+3)%6];
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
						vert[tick]=augvert[2*tick]=newPD.kData[cv].flower[(myindx+2)%6];
						augvert[2*tick+1]=newPD.kData[cv].flower[(myindx+3)%6];
						dtile.wgIndices[tick++]=cv-bcbase;
						
						// then pairs of grey/white to get edges between tile barycenters
						// TODO: obe due to 'prepCanonical', but seems this iteration is in error
						for (int i=1;i<(num/4);i++) {
							cv=flower[2*i+1];
							myindx=newPD.nghb(cv,v);
							vert[(tick+1)/2]=augvert[2*tick]=newPD.kData[cv].flower[(myindx+2)%6]; // tile barycenter
							augvert[2*tick+1]=newPD.kData[cv].flower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
							cv=flower[2*i+3];
							myindx=newPD.nghb(cv,v);
							augvert[2*tick]=newPD.kData[cv].flower[(myindx+2)%6];
							augvert[2*tick+1]=newPD.kData[cv].flower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
						}							
						
						// then last tile barycenter to and including upstream edge barycenter
						cv=flower[num-1];
						myindx=newPD.nghb(cv,v);
						vert[(tick+1)/2]=augvert[2*tick]=newPD.kData[cv].flower[(myindx+2)%6]; // get last tile barycenter
						augvert[2*tick+1]=newPD.kData[cv].flower[(myindx+3)%6];
						dtile.wgIndices[tick++]=cv-bcbase;
						vert[tick/2+1]=augvert[2*tick]=newPD.kData[cv].flower[(myindx+4)%6]; // last vert is edge barycenter
						
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
							int cv=newPD.kData[v].flower[j];
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
							vert[tick/2]=augvert[2*tick]=newPD.kData[cv].flower[(myindx+2)%6];
							augvert[2*tick+1]=newPD.kData[cv].flower[(myindx+3)%6];
							dtile.wgIndices[tick++]=cv-bcbase;
							cv=flower[(ii+2)%num];
							myindx=newPD.nghb(cv,v);
							augvert[2*tick]=newPD.kData[cv].flower[(myindx+2)%6];
							augvert[2*tick+1]=newPD.kData[cv].flower[(myindx+3)%6];
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
						
						// these go at end
						augvert[6]=newPD.kData[cv].flower[myindx];
						augvert[7]=newPD.kData[cv].flower[(myindx+1)%6];
						
						vert[0]=augvert[0]=newPD.kData[cv].flower[(myindx+2)%6];
						augvert[1]=newPD.kData[cv].flower[(myindx+3)%6];
						vert[1]=augvert[2]=newPD.kData[cv].flower[(myindx+4)%6];
						qtile.wgIndices[0]=cv-bcbase;
						
						// next is white
						cv=flower[3];
						myindx=newPD.nghb(cv,v);
						augvert[3]=newPD.kData[cv].flower[(myindx+3)%6];
						vert[2]=augvert[4]=newPD.kData[cv].flower[(myindx+4)%6];
						augvert[5]=newPD.kData[cv].flower[(myindx+5)%6];
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
							int cv=newPD.kData[v].flower[j];
							if (cv>bcbase && workingTD.wgTiles[cv-bcbase].mark==-1) // grey
								gdir=j;
						}
						if (gdir<0)
							throw new CombException();
						
						for (int j=0;j<4;j++) {
							int cv=flower[(gdir+2*j)%num];
							int myindx=newPD.nghb(cv,v);
							vert[j]=augvert[2*j]=newPD.kData[cv].flower[(myindx+2)%6];
							augvert[2*j+1]=newPD.kData[cv].flower[(myindx+3)%6];
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
			CirclePack.cpb.errMsg("Failed to complete dual or quad tile data");
		}
		
		newPD.tileData=workingTD;
		return newPD;
	}
	
	/**
	 * TODO: under development (10/24/13)
	 * 
	 * Given a tile as an oriented edgelist, create a twice barycentrically 
	 * subdivided packing. Create a barycenter for the tile, one for each
	 * edge, and then 'bary_refine' the faces. The vertex 'mark's are set 
	 * to {1,2,3} for {tile baryVert (infty), original tile corner (0), 
	 * and edge barycenter (1)}.
	 * 
	 * Difficulties arise in 'special' circumstances:
	 *  * tile has sides which are identified
	 *  * tile is uni-gon or a bi-gon
	 *  
	 * A particular difficulties -- may need global info to decide:
	 *  * if edge a->b and edge b->a are in the
	 *    given edgelist, we don't know (locally) whether these edges are
	 *    identified or if there is a bi-gon in between.
	 *  * an edge a->a represents a loop. If the edgelist is this single
	 *    edge, then this is a uni-gon. If the edgelist has more, then 
	 *    this tile shares this edge with a uni-gon, and our packing 
	 *    would have a problem (depending on what else happens around a).
	 *  * a bi-gon could be next to a loop, so edges are a->a, a->a.
	 * 
	 * Returned packing 'vertexMap' give old2new index conversions
	 * @param elist, positively oriented boundary edgelist
	 * @return PackData or null on error
	 */
	public static PackData augmentTile(EdgeLink elist) {
		
		// check the data, close elist if necessary
		if (elist==null || elist.size()==0)
			return null;
		int v=elist.get(elist.size()-1).w;
		int w=elist.get(0).v;
		if (v!=w)
			elist.add(new EdgeSimple(v,w));
		
		// is this a uni-gon?
		if (elist.size()==1) {
			PackData p=new PackData(null);
			p.nodeCount=9;
			p.directAlpha(1);
			p.gamma=2;
			p.status=true;
			p.locks=0;
			p.activeNode=1;
			p.hes=0;

			// create flowers, etc.
			p.kData[1].num=4;
			p.setBdryFlag(1,0);
			p.kData[1].flower=new int[5];
			for (int i=0;i<4;i++) p.kData[1].flower[i]=i+2;
			p.kData[1].flower[4]=2;
			p.rData[1].rad=.5;

			// fix the 4 flowers
			p.kData[2].flower=new int[5];
			p.kData[2].num=4;
			p.kData[2].flower[0]=1;
			p.kData[2].flower[1]=5;
			p.kData[2].flower[2]=6;
			p.kData[2].flower[3]=3;
			p.kData[2].flower[4]=1;
			p.setBdryFlag(2,0);
			p.kData[2].utilFlag=0;
			p.setVertMark(2,0);
			p.rData[2].rad=2.5/(double)4;
			
			p.kData[3].flower=new int[7];
			p.kData[3].num=6;
			p.kData[3].flower[0]=1;
			p.kData[3].flower[1]=2;
			p.kData[3].flower[2]=6;
			p.kData[3].flower[3]=7;
			p.kData[3].flower[4]=8;
			p.kData[3].flower[5]=4;
			p.kData[3].flower[6]=1;
			p.setBdryFlag(3,0);
			p.kData[3].utilFlag=0;
			p.setVertMark(3,0);
			p.rData[3].rad=2.5/(double)6;
			
			p.kData[4].flower=new int[5];
			p.kData[4].num=4;
			p.kData[4].flower[0]=1;
			p.kData[4].flower[1]=3;
			p.kData[4].flower[2]=8;
			p.kData[4].flower[3]=5;
			p.kData[4].flower[4]=1;
			p.setBdryFlag(4,0);
			p.kData[4].utilFlag=0;
			p.setVertMark(4,0);
			p.rData[4].rad=2.5/(double)4;
			
			p.kData[5].flower=new int[7];
			p.kData[5].num=6;
			p.kData[5].flower[0]=1;
			p.kData[5].flower[1]=4;
			p.kData[5].flower[2]=8;
			p.kData[5].flower[3]=8;
			p.kData[5].flower[4]=6;
			p.kData[5].flower[5]=2;
			p.kData[5].flower[6]=1;
			p.setBdryFlag(5,0);
			p.kData[5].utilFlag=0;
			p.setVertMark(5,0);
			p.rData[5].rad=2.5/(double)6;
			
			// fix boundary flower
			p.kData[6].flower=new int[5];
			p.kData[5].num=5;
			p.kData[5].flower[0]=7;
			p.kData[5].flower[1]=3;
			p.kData[5].flower[2]=2;
			p.kData[5].flower[3]=5;
			p.kData[5].flower[4]=9;
			p.setBdryFlag(5,1);
			p.kData[5].utilFlag=0;
			p.setVertMark(5,0);
			p.rData[5].rad=2.5/(double)4;

			p.kData[7].flower=new int[3];
			p.kData[7].num=3;
			p.kData[7].flower[0]=8;
			p.kData[7].flower[1]=3;
			p.kData[7].flower[2]=6;
			p.setBdryFlag(7,1);
			p.kData[7].utilFlag=0;
			p.setVertMark(7,0);
			p.rData[7].rad=2.5/(double)4;

			p.kData[8].flower=new int[5];
			p.kData[8].num=5;
			p.kData[8].flower[0]=9;
			p.kData[8].flower[1]=5;
			p.kData[8].flower[2]=4;
			p.kData[8].flower[3]=3;
			p.kData[8].flower[4]=7;
			p.setBdryFlag(8,1);
			p.kData[8].utilFlag=0;
			p.setVertMark(8,0);
			p.rData[8].rad=2.5/(double)4;

			p.kData[9].flower=new int[3];
			p.kData[9].num=3;
			p.kData[9].flower[0]=6;
			p.kData[9].flower[1]=5;
			p.kData[9].flower[2]=8;
			p.setBdryFlag(9,1);
			p.kData[9].utilFlag=0;
			p.setVertMark(9,0);
			p.rData[9].rad=2.5/(double)4;
				
			// process the combinatorics 
			p.complex_count(true);
			p.facedraworder(false);
			p.set_aim_default();

			// mark vertices and store info
			p.setVertMark(1,1);
			p.setVertMark(6,2);
			p.setVertMark(8,1);
			p.vertexMap=new VertexMap();
			p.vertexMap.add(new EdgeSimple(elist.get(0).v,6));

			p.setCombinatorics();
			return p;
		}

		// check if not contiguous
		Iterator<EdgeSimple> eit=elist.iterator();
		EdgeSimple curr=eit.next();
		while (eit.hasNext()) {
			EdgeSimple nxtedge=eit.next();
			if (nxtedge.v!=curr.w)
				return null;
			curr=nxtedge;
		}
		
		// is this a bi-gon?
		if (elist.size()==2) {
			
			PackData p=PackCreation.seed(4,0);
			p.bary_refine();
			
			p.vertexMap=new VertexMap();
			p.vertexMap.add(new EdgeSimple(elist.get(0).v,2));
			p.vertexMap.add(new EdgeSimple(elist.get(0).w,4)); // Note: could be same
			p.setVertMark(1,1);
			p.setVertMark(2,2);
			p.setVertMark(4,2);
			p.setVertMark(3,3);
			p.setVertMark(5,3);
			
			p.setCombinatorics();
			return p;
		}			
		
		// count original vertices
		eit=elist.iterator();
		int vmax=0;
		while (eit.hasNext()) {
			EdgeSimple edge=eit.next();
			vmax=(edge.v>vmax) ? edge.v : vmax;
		}
		int []old2new=new int[vmax+1];

		// reindex elist from 2 (1 will be tile barycenter)
		int tick=2;
		int num=elist.size(); // number of edges
		for (int j=0;j<num;j++) {
			EdgeSimple edge=elist.get(j);
			int vv=edge.v;
			if (old2new[vv]==0)
				old2new[vv]=tick++;
			edge.v=old2new[vv];
			int ww=edge.w;
			if (old2new[ww]==0)
				old2new[ww]=tick++;
			edge.w=old2new[ww];
			edge=elist.get(j);
		}
		
		// TODO: handle other special cases
		
		return null;
		
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
	 * Using provisional 'nodeCount' and 'kData' information already
	 * in place, tiling information by finding closed loops of vertices 
	 * defining the tiles. (Triangles are included like any other tiles.)  

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
				if (vtrack[v]!=num) { // not done with this vert
					for (int j=0;j<num;j++) {
						
						// start a tile in this direction?
						if (tflower[v][j]==0) {
							Vector<Integer> tileVerts=new Vector<Integer>(3);
							tileVerts.add(v);
							int u=packData.kData[v].flower[j];
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
								u=packData.kData[u].flower[indx_uw];
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
	 * in general, I think. However, if p was built as the barycentric 
	 * packing of some tiling, then this may reconstruct that tiling.
	 * @param p PackData
	 * @param V int, interior vertex
	 * @return new TileData (its packData is null)
	 */
	public static TileData paveMe(PackData p,int V) {
		if (p==null || V<1 || V>p.nodeCount || p.isBdry(V))
			return null;
		
		// keep track flowers we use, note the petals 
		NodeLink finalList=new NodeLink(p);
		int []util=new int[p.nodeCount+1]; // 0=open, 1=used, -1=in next
		
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
				if (util[v]<=0 && !p.isBdry(v)) {
					// want this v
					finalList.add(v);
					util[v]=1;
					
					// find vertices across its flower edges
					int num=p.countFaces(v);
					for (int j=0;j<num;j++) {
						int w=p.kData[v].flower[j];
						int u=p.kData[v].flower[j+1];
						util[w]=util[u]=1;
						int a=p.cross_edge_vert(v,j);
						if (a>0 && util[a]==0 && !p.isBdry(a)) {
							next.add(a);
							util[a]=-1;
						}
					}
				}
			} // end of inner while
		} // end of outer while
	
		if (safety<=0) {
			throw new CombException("bombed in 'paveMe'");
		}
		
		return viaFlowers(p,finalList);
	}

	/* put into 'ConformalTiling.java' instead
	public static int writeEuclTiling(TileData tileData,BufferedWriter fp) {
		int tilesdrawn=0;
		try {
			fp.write("%!PS-Adobe-2.0 EPSF-2.0\n%%Title: Traditional Eucl Tiling\n");
			fp.write("%%Creator: "+PackControl.CPVersion+
					"\n%%CreationDate: "+new Date().toString()+"\n");
			fp.write("%%For: "+System.getProperty("user.name")+"\n%%Orientation: Portrait\n");

			fp.write("Magnification: 1.0000\n%%EndComments\n");
			TileRule topRule=tileData.subRules.tileRules.get(4);
	    
			// compute/set bounding box based on toptile
			double minx=0.0;
			double maxx=0.0;
			double miny=0.0;
			double maxy=0.0;
			for (int j=1;j<topRule.edgeCount;j++) {
				Complex z=topRule.stdCorners[j];
				minx=(z.x<minx) ? z.x : minx;
				maxx=(z.x>maxx) ? z.x : maxx;
				miny=(z.y<miny) ? z.y : miny;
				minx=(z.y>maxx) ? z.y : maxy;
			}
			double sz=maxx-minx;
			double lng=maxy-miny;
			sz=(lng>sz) ? lng:sz;
	    
			int bblx=(int)(72*(minx))-10;
			int bbly=(int)(72*(miny))-10;
			int bbrx=(int)(72*(minx+sz))+10;
			int bbry=(int)(72*(miny+sz))+10;
			fp.write("%%BoundingBox: "+bblx+" "+bbly+" "+bbrx+" "+bbry+"\n");
		
			Complex []topBase=new Complex[2];
			topBase[0]=topRule.stdCorners[0];
			tilesdrawn=printTilePS(tileData,0,topBase,fp); 
		
			fp.write("\nend\nshowpage\n");
			fp.flush();
			fp.close();
		} catch(Exception ex) {
			throw new InOutException("problem: ps file for 'write_eucl'");
		}

		return tilesdrawn;
	}
	
	
	public static int printTilePS(TileData tdata,int tileIndx,Complex []base,BufferedWriter fp) {
		Tile tile=tdata.myTiles[tileIndx];
		Complex bvec=base[1].minus(base[0]);
		Complex []stdC=null;
		Complex []mybase=new Complex[2];
		
		// first, draw yourself, then position and recursively draw any children
		try {
			stdC=tdata.subRules.tileRules.get(tileIndx).stdCorners;
			fp.write(base[0].x+" "+base[0].y+"newpath\nmoveto\n");
			for (int j=1;j<stdC.length;j++) {
				Complex z=stdC[j].times(bvec).add(base[0]);
				fp.write(z.x+" "+z.y+"lineto\n");
			}
			fp.write("closepath\n");
		} catch(Exception ex) {
			throw new InOutException("failed in writing a tile.");
		}
		
		// recurse through children
		int count=1;
		if (tile.myTileData!=null) {
			TileData mytile=tile.myTileData;
			for (int t=0;t<mytile.tileCount;t++) {
				Complex []subtileBase=tdata.subRules.tileRules.get(tileIndx).tileBase[t];
				subtileBase[0]=subtileBase[0].times(bvec).add(base[0]);
				subtileBase[1]=subtileBase[1].times(bvec).add(base[0]);
				int rslt=printTilePS(mytile,t,subtileBase,fp);
				if (rslt<=0)
					return 0;
				count +=rslt;
			}
		}
		return count;
	}
	*/
	
	/**
	 * Given a packing and a list of its vertices, create tiling
	 * consisting of the flowers of the given vertices. We check
	 * that vertices in the list are interior and non-neighboring,
	 * but the tiles may be disconnected, etc.
	 * @param p
	 * @param vlist
	 * @return new TileData (its packData is null)
	 */
	public static TileData viaFlowers(PackData p,NodeLink vlist) {
		if (p==null || vlist==null)
			return null;
		
		int []util=new int[p.nodeCount+1]; // p.vlist=vlist;
		
		TileData td=new TileData(0,3); // default to tiling mode 3
		td.myTiles=new Tile[p.sizeLimit+1];

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
			for (int j=0;j<num;j++) {
				int k=p.kData[v].flower[j];
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
				int jj=p.nghb(tile.baryVert,ww);
				int ov=p.cross_edge_vert(tile.baryVert,jj);
				if (util[ov]<0)
					tile.tileFlower[j][0]=-util[ov];
				else
					tile.tileFlower[j][0]=0;
			}
		}
		
		td.tileCount=tcount;
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
	 * Clone 'tileData'; 'packData' and 'parentTile' set to null and 
	 * may need to be set by calling routine. Also, 'subRule' and 
	 * 'vertMap' must be updated separately.
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
	 * Given 'tIndx', create the 'augVert' list around the full
	 * tile boundary. The initial vert does NOT get repeated at 
	 * the end. 
	 * 
	 * If this tile is not subdivided, then 'augVert' should 
	 * already be in place. If it is subdivided, assume the 
	 * subtiles have 'augVert'
	 * 
	 * Return 0 on failure: e.g., if there's no subdivision
	 * but 'augVert' is not set
	 * 
	 * @param tIndx int, index of the tile
	 * @return int, count, 0 on error, -1 if augVert is missing
	 */
	public int createAugBorder(int tIndx) {

		Tile tile=myTiles[tIndx];
		
		// if not subdivided, 'augVert' should be in place, 'vert' should be OK.
		if ((subRules==null || tile.myTileData==null) && tile.augVert==null) {
			throw new CombException("'augVert' is missing for tile "+tIndx);
		}
		
		int n=myTiles[tIndx].vertCount;
		int []newVerts=new int[n];
		NodeLink list=new NodeLink();
		for (int j=0;j<n;j++) {
			NodeLink elink=getEdgeVerts(tIndx,j);
			elink.remove(elink.size()-1); // remove last index
			list.abutMore(elink);
			newVerts[j]=elink.get(0); // first goes into 'vert'
		}
		
		tile.augVert=new int[list.size()];
		Iterator<Integer> lst=list.iterator();
		int tick=0;
		while (lst.hasNext()) {
			tile.augVert[tick++]=lst.next();
		}
		
		tile.augVertCount=tick;
		tile.vert=newVerts;
		return tick;
	}
	
	/**
	 * Given 'tIndx' tile index and 'eIndx' edge index, 
	 * return the 'augVert' list along that edge, including
	 * both first and last vertex.
	 * 
	 * If this tile is not subdivided, then 'augVert' should 
	 * already be in place. If it is subdivided, make
	 * recursive call along edge subtiles.
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
	 * Fill in the tileFlower information. 
	 * Caution: This can fail due to unigons and digons.
	 * @param tData
	 * @return int count
	 */
	public static int setTileFlowers(TileData tData) {

		// wipe out current 'tileFlower' data
		for (int t = 1; t <= tData.tileCount; t++) {
			Tile tile = tData.myTiles[t];
			tile.tileFlower = new int[tile.vertCount][2];
		}

		// go through each edge of each tile
		for (int t = 1; t <= tData.tileCount; t++) {
			Tile tile = tData.myTiles[t];
			for (int j = 0; j < tile.vertCount; j++) {

				// if this edge is not already settled
				if (tile.tileFlower[j][0] == 0) {
					int w = tile.vert[(j - 1 + tile.vertCount) % tile.vertCount];
					int v = tile.vert[j];

					// check only tile with larger indices
					boolean hit = false;
					for (int tj = t; (tj <= tData.tileCount && !hit); tj++) {
						Tile petile = tData.myTiles[tj];
						int indx = petile.isTileEdge(v, w);
						if (indx >= 0) {
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
		}

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
