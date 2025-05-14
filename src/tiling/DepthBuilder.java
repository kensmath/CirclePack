package tiling;

import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import dcel.CombDCEL;
import dcel.PackDCEL;
import deBugging.DCELdebug;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.DataException;
import ftnTheory.ConformalTiling;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;

/**
 * This class is for building subdivision tilings
 * to specified depths. It is a separate class because
 * of the complications involved, particularly the
 * need for recursion. In order to debug, subtasks
 * have been split off into their own routines.
 */
public class DepthBuilder {
	TileData tData;
	int depth;
	int mode; // Typically 3
	public boolean buildDeBug;
	
	// point to 'ConformalTiling.topTileData'
	Vector<TileData> topTD;
	
	// point to 'ConformalTiling.depthPackings'
	Vector<Vector<PackData>> depthPD;

	// constructor
	public DepthBuilder(TileData td, int d,int m,
			Vector<TileData> tTD,Vector<Vector<PackData>> dPD) {
		tData=td;
		depth=d;
		mode=m;
		topTD=tTD;
		depthPD=dPD; // 'ConformalTiling.depthPackings'
		buildDeBug=false;
	}
    
	/**
	 * If 'tData' has 'subRules' and appropriate 
	 * 'tileType's, then build the canonical circle 
	 * packing for 'tData' recursively to the given 
	 * 'depth', attaching 'tData' as its 'tileData'. 
	 * Note that depth=0 is just the canonical tiling 
	 * for 'tData'.
	 * 
	 * This builds a new PackData and updates and 
	 * attaches 'tData' based on 'tData's initial 
	 * TileData structure. 'tData' must have the tiles, 
	 * with consistent 'tile.vert's, and must have 
	 * 'tileFlower's. (Note, eg, that for subdivision 
	 * rules, may have to run 
	 * 'SubdivsionRules.getRulesTD' to set consistent
	 * vertex indices.) If the tiles have non-null 
	 * 'myTileData', then they themselves represent 
	 * tilings (as when we build from subdivision rules).
	 *
	 * Therefore, this is a recursive construction: At 
	 * a given level, a tiling is created one tile at a 
	 * time with its associated packing; this is pasted 
	 * onto a growing global packing (for this level) and
	 * the TileData tiles are updated, with 'augmented' 
	 * vertices from the local packing; as that is absorbed 
	 * into the growing global packing, we update 'vert' 
	 * and 'augVert' indices to the parent packing and 
	 * then discard the local packing to save space (but the 
	 * local 'TileData' is now part of the parent's TileData). 
	 *
	 * We do not build a global 'TileData' that has tiles 
	 * down to the finest level. Instead, each 'Tile' at 
	 * level j>0 contains 'TileData' for j-1 level tiles.
	 * 
	 * TODO: build a "collapse" method that builds the 
	 * tiling down to the finest level for purposes of 
	 * saving it. 
	 * 
	 * When 'depth' is 0, return canonical packing for 
	 * 'tData' with 'tData' as its 'tileData'. This 
	 * happens, e.g., at the bottom of every recursion cycle.
	 * 
	 * We save/use prepared packings as we go in 'depthPD'. 
	 * When this is not null, then if depthPD(tt)(d) exists, 
	 * it holds PackData for tile type 'tt' at subdivision 
	 * depth d (in mode=3 form).
	 * @return PackData (holding also its 'tileData') or 
	 * 			null on error
	 */
	public PackData build2Depth() {
		
// debugging
System.out.println("build2Depth: tData.tileCount="+tData.tileCount+"; depth="+depth);		

		if (buildDeBug) { // buildDeBug=true;
			int pTt=-1;
			if (tData.parentTile!=null)
				pTt=tData.parentTile.tileType;
			System.out.println("enter 'build2Depth': tileCount = "+
				tData.tileCount+", depth ="+depth+", parent tile type = "+pTt);
		}
		
		if (depth>0 && tData.subRules==null) // buildDeBug=true;
			throw new DataException("no subdivision rules are specified");
		
		// start with first tile to given depth
		PackData p=null;
		Tile tile=tData.myTiles[1];
		int tt=tData.myTiles[1].tileType;
		// type not set? choose first one that has right size
		if (tt==0) {
			tt=tData.subRules.getPossibleType(tData.myTiles[1].vertCount);
			if (tt<0) {
				CirclePack.cpb.myErrorMsg("No tile type with "+
					tData.myTiles[1].vertCount+" edges");
				return null;
			}
			tile.tileType=tt;
		}

		// depth 0?
		if (depth==0) {
			p=oneTileDepth0(tile,tt);
		}
		// else depth>0
		else {
			p=oneTileDepthPos(tile,tt);
		}
		
		// ************************ only 1 tile?
		if (tData.tileCount==1) {
			return p;
		}
		
		// *************** else, multiple tile types **********
		
		// track tiles having packings and those whose 
		//   edges have all been pasted.
		int []tilehaspack=new int[tData.tileCount+1];
		tilehaspack[1]=1;
		int []tiledone=new int[tData.tileCount+1];
		
		// keep track of which edges are already pasted:
		//   [tileIndex][edge number]=0
		//   [tileIndex][edge_number]=1 if already pasted
		int[][] pastingStatus=new int[tData.tileCount+1][];
		for (int k=1;k<=tData.tileCount;k++) {
			Tile tk=tData.myTiles[k];
			pastingStatus[tk.tileIndex]=new int[tk.vertCount];
		}
		
		// ************* have started with p, now add on ****************
		// Note that 'p' is the growing parent packing we are 
		//   building. Pastings may happen between edges of p.
		//   When a new tile is added, 'tilePack' holds its
		//   packing.
		PackData tilePack=null;
		
// debugging
System.out.println("enter multi-tile");		
		
		// maintain 2 lists; indices of current tiles, new ones 
		//    that have been touched
		Vector<Integer> curr=null; 
		Vector<Integer> next= new Vector<Integer>();
		next.add(1); // put first tile in list 
		int safety=100*tData.tileCount;
		while (next.size()>0) {
			curr=next;
			next=new Vector<Integer>();
			while (curr.size()>0 && safety>0) {
				safety--;
				int t=curr.remove(0); // get the next tile
				tile=tData.myTiles[t];
				
				// entries go into 'next' (and later into 'curr') as their 
				//    packings are generated and pasted to growing pack 'p'.
				
				// go around tileFlower to paste (or create and paste) tiles;
				//    self-pastings of edges may occur. Identification of 
				//    vertices is not needed, as they will eventuate when 
				//    further pasting is done.
				if (tiledone[t]!=0) 
					break;

				// go through as-yet-unpasted edges, create new 
				//   packings when needed
				for (int ti=0;ti<tile.vertCount;ti++) {
					int nghbJ=tile.tileFlower[ti][0];
					if (nghbJ>0 && pastingStatus[tile.tileIndex][ti]==0) {
						Tile nghbTile=tData.myTiles[nghbJ]; // nghbTile.debugPrint();
						int tty=tData.myTiles[nghbJ].tileType;
						
						// type not set? error
						if (tty==0) 
							throw new CombException("should have tile type at this point");
						
						boolean isnew=false;
						
						// do we need to create?
						if (tilehaspack[nghbJ]==0) {
							tilePack=null;
							isnew=true;

							if (depth==0) { 
								tilePack=oneTileDepth0(nghbTile,tty);
							}
							else {
								tilePack=oneTileDepthPos(nghbTile,tty);
							}
							tilehaspack[nghbJ]=1;
						}
						// else already attached, so this is self-pasting 
						else { 
							tilePack=p;
						}
						
						// find how to paste 'nghbTile' to 'tile' (may be self-pasting)
						int nti=tile.tileFlower[ti][1];
						if (tile.tileFlower[ti][0]>0 && tile.tileFlower[ti][0]!=nghbJ)
							throw new CombException("not the nghb tile expected");
						
						// get the list of vertices defining the edges
						NodeLink tedge=tile.findAugEdge(ti);
						NodeLink nghbedge=nghbTile.findAugEdge(nti);
						int n=tedge.size()-1;
						if ((nghbedge.size()-1)!=n)
							throw new CombException("edge sizes don't match");

						int v=tedge.get(n);
						int w=nghbedge.get(0);
			
						PackDCEL pdc1=p.packDCEL;
						PackDCEL pdc2=tilePack.packDCEL;
						
						boolean debug=false; // debug=true;
						if (debug) {
							DCELdebug.printRedChain(pdc1.redChain);
							DCELdebug.printRedChain(pdc2.redChain);
							pdc1.fixDCEL(CPBase.packings[1]);
							CPBase.packings[1].attachDCEL(pdc1);
							pdc2.fixDCEL(CPBase.packings[2]);
							CPBase.packings[2].attachDCEL(pdc2);

							debug=false;
						} // return null;
						
						if (buildDeBug) {
							System.out.println("pasting "+v+" to "+w+" along "+n+" edges");
						}

// debugging  before pasting
						if (debug) {  // debug=true;
						  debug=false;
						  System.out.println("\nGetting ready to paste 'debugpack_"+
								  p.nodeCount+".p', pasting v="+v+" to w="+w+" along "+n+" edges");
						  String pnme="debugpack_"+p.nodeCount+".p";
						  System.out.println("Info on "+pnme);
						  DebugHelp.debugPackWrite(p,pnme);
						  DCELdebug.printRedChain(p.packDCEL.redChain);
						  System.out.println("tedge=");
						  tedge.printMe();
						  System.out.println("nghbedge=");
						  nghbedge.printMe();
						  tile.printAugVert();
						  nghbTile.printAugVert();
						  if (pdc1!=pdc2) {
							  pnme="secondtile.p";
							  System.out.println("Info on "+pnme);
							  DebugHelp.debugPackWrite(tilePack,pnme);
							  DCELdebug.printRedChain(tilePack.packDCEL.redChain);
						  }
						}

						PackDCEL pdcel=CombDCEL.adjoin(pdc1, pdc2, v, w, n);
						VertexMap oldnew=pdcel.oldNew;
						pdcel.redChain=null;
						pdcel.fixDCEL(null);
// debugging
DCELdebug.printRedChain(pdcel.redChain);
DCELdebug.redConsistency(pdcel);

p.vertexMap=pdcel.oldNew=oldnew;
						p.attachDCEL(pdcel);
						
// debugging
						if (buildDeBug) { // buildDeBug=true;
							int qnum=2; // qnum=1;
							DebugHelp.debugPackDisp(p,qnum,"max_pack;Disp -w -c -cc5t4 {c:m.eq.2}");
							buildDeBug=false;
						}
						
						// record pastings
						pastingStatus[tile.tileIndex][ti]=1;
						pastingStatus[nghbJ][nti]=1;
						
						if (debug) { // debug=true;
							CPBase.packings[2].attachDCEL(pdcel);
							debug=false;
							return null;
						}

						// recursively update vertices
						if (isnew) { // reset just for this new tile
							next.add(nghbJ);
							tData.myTiles[nghbJ].updateMyVerts(p.vertexMap);
						}
						else { // self pasting of p; adjust all attached tiles
							for (int j=1;j<=tData.tileCount;j++) {
								if (tilehaspack[j]==1)
									tData.myTiles[j].updateMyVerts(p.vertexMap);
							}
						}
					}
				} // done pasting tile's edges
				tiledone[tile.tileIndex]=1;
			} // end of while on 'curr'
		} // end of while on 'next'
		
		// attach updated 'tData'
		p.packDCEL.fixDCEL(p);
		p.tileData=tData;
		return p;
	}
	
	public PackData oneTileDepth0(Tile tile,int tt) {
		
// debugging
System.out.println("oneTileDepth0: tileIndex="+tile.tileIndex);		
		
		PackData tmpPD=null;
		boolean needsave=false;

		// this packing should have been created when 
		// the rules were loaded
		if (depthPD!=null) {
			tmpPD=ConformalTiling.copyDepthPacking(depthPD,tt,depth);
		}
		// if not, then build the packing
		if (tmpPD==null) {
			tmpPD=tData.myTiles[1].singleCanonical(mode);
			needsave=true;
		}
		
		// transfer new data to original tile
		tile.augVertCount=tmpPD.tileData.myTiles[1].augVertCount;
		tile.augVert=new int[tile.augVertCount];
		for (int si=0;si<tile.augVertCount;si++)
			tile.augVert[si]=tmpPD.tileData.myTiles[1].augVert[si];
		tile.vert=new int[tile.vertCount];
		for (int si=0;si<tile.vertCount;si++)
			tile.vert[si]=tmpPD.tileData.myTiles[1].vert[si];
		tile.baryVert=tmpPD.tileData.myTiles[1].baryVert;

		// Do we need a copy of that just built (should 
		//    not at depth=0)
		Vector<PackData> vpd=null;
		if (needsave && tt>=4 && depthPD!=null && 
				(vpd=depthPD.get(tt))!=null && (vpd.size()<=depth || 
				vpd.get(depth)==null)) {
			if (vpd.size()<=depth)
				vpd.setSize(depth+1);
			PackData savePD=tmpPD.copyPackTo();
			vpd.setElementAt(savePD,depth);
			needsave=false;
		}
		tile.TDparent=tData;
		tmpPD.tileData=tData;
		
		if (buildDeBug) { // buildDeBug=true;
			DebugHelp.debugPackDisp(tmpPD,2,"max_pack");
		}
		
		return tmpPD;
	}

	public PackData oneTileDepthPos(Tile tile,int tt) {
		
// debugging
System.out.println("oneTileDepthPos: tileIndex="+tile.tileIndex+"; depth="+depth);
		PackData tmpPD=null;
		boolean needsave=false;
		TileData tmpTD=topTD.get(tt).copyMyTileData();
		tmpTD.subRules=tData.subRules;
		
		// if not stored, then recurse to build it
		if (depthPD!=null && depthPD.get(tt).size()>depth) {
			tmpPD=ConformalTiling.copyDepthPacking(depthPD,tt,depth);
			needsave=false;
		}
		if (tmpPD==null) {
			boolean debug=false; // debug=true;
			tmpPD=packAtDepth(tmpTD,depth-1,mode,debug);
			if (tmpPD==null) {
				System.out.println("failed 'build2Depth' at "+(depth-1));
				return null;
			}
			needsave=true;
		}
		
		// fix up our one tile, create augmented vertices
		tData.myTiles[1].myTileData=tmpPD.tileData;
		tData.myTiles[1].myTileData.parentTile=tData.myTiles[1];
		for (int j=1;j<=tData.myTiles[1].myTileData.tileCount;j++)
			tData.myTiles[1].myTileData.myTiles[j].TDparent=tmpPD.tileData;
		tData.newVertAug(1);
		
		// Do we need a copy of the one just built
		Vector<PackData> vpd=null;
		if (needsave && depthPD!=null &&
				(vpd=depthPD.get(tt))!=null && 
				(vpd.size()<=depth || vpd.get(depth)==null)) {
			if (vpd.size()<=depth)
				vpd.setSize(depth+1);
			PackData savePD=tmpPD.copyPackTo();
			vpd.setElementAt(savePD,depth);
			needsave=false;
			
			if (buildDeBug) {
				System.out.println("stored depth "+depth+", type "+tt);
			}

		}
		
		// return new packing with original 'tData' attached
		tmpPD.tileData=tData;
		
		if (buildDeBug) { // buildDeBug=true;
			int qnum=2; // qnum=1;
			DebugHelp.debugPackDisp(tmpPD,qnum,"max_pack");
		}
		return tmpPD;
	}
	
	/**
	 * Create a packing via 'DepthPacking' with 'debug' option
	 * @param td TileData
	 * @param d int, depth
	 * @param m int, mode
	 * @param debug boolean
	 * @return PackData or null
	 */
	public PackData packAtDepth(TileData td,int d,int m,boolean debug) {
		
// debugging
System.out.println("packAtDepth: tileCount="+td.tileCount+"; depth="+d);

		DepthBuilder dBuilder=new DepthBuilder(td,d,m,topTD,depthPD);
		dBuilder.buildDeBug=debug;
		PackData pd=dBuilder.build2Depth();
		return pd;
	}
	
}
