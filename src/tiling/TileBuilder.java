package tiling;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.ParserException;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;

/**
 * TileBuilder is intended for building 'TileData' structures 
 * and their packings.
 * 
 * TODO: trying to fix multiply connected situations
 * 
 * Basic method here is build tile-by-tile so we can handle 
 * situations that come up when applying subdivision rules, 
 * building dessins, fusion tilings, etc. Namely, we need to 
 * handle unigons, digons, and various self-pastings.
 * 
 * For recursive use, we need to allow 'origTD' to have some 
 * "null" tiles: E.g. 'origTD' may be a connected subset of 
 * tiles from a larger tiling, but it keeps the original tile 
 * indices and inherits original 'myTiles' length.
 * 
 * Idea: start with rootTile and keep adding tiles. There are 
 * many complications. E.g., a loop is an edge with same vertex 
 * at its ends, and to paste along a loop in 'masterPack' 
 * requires special 'adjoin' method: do all but one of the 
 * sub-edge pastings with usual adjoin, fix up the packing, 
 * then do one last self-adjoin to close the last edge. However, 
 * this may require recursive calls so the stuff being adjoined, 
 * if not just a unigon, has already been consolidated in a 
 * simply connected packing.
 * 
 * Issues:
 *  * tiles may attach along edges with multiple vertices
 *  * tiles may attach to themselves (more than once, even) 
 *    along edges or at vertices
 *  * two tiles may be attached along more than one, perhaps 
 *    non-contiguous edges.
 *  * tiles may be unigons or digons
 *  * tiles must have 'tileflower' data: 
 *  *    t=tile.tileflower[i][0] is the index of the tile 
 *         across edge i (if t=0, this is a bdry edge) and 
 *         tile.tileflower[i][1] is the index of the shared 
 *         edge in t.
 *  
 * @author kstephe2, started November 2013, restarted 7/2014
 *
 */
public class TileBuilder {
	
	PackData masterPack; // packing we start and add to as we go
	// incoming TileData, remains unchanged for reference
	TileData origTD; // 'origTD.myTiles' should be size 'origCount+1', 
					 // but may have missing tiles
	int origCount;  // original count of tiles.
	TileData growTD; // build new full tiles as we go; keep orig 'tileIndex's
	int []tileAdded; // set when tile has been added to growTD
	int []tileFull;  // set when tile's edges all pasted
	
	// Constructor
	public TileBuilder(TileData td) {
		masterPack=null;
		origTD=td.copyBareBones();
		origCount=origTD.tileCount;
		growTD=new TileData(origCount,td.builtMode);
		
		// keep track: tiles added, tiles full
		tileAdded=new int[origCount+1];;  
		tileFull=new int[origCount+1];;  
	}

	/**
	 * Build associated full barycentric complex for origTD: 
	 * that is, every n-tile is barycentrically subdivided to 
	 * form 2n triangles, and then each of these is
	 * barycentrically subdivided. (This is required, e.g., 
	 * to handle, digons, slits, and some self-pastings. 
	 * We have no consistent way to handle uniqons, so things 
	 * will fail there.) 
	 * Vertices get 'mark's {1,2,3} if they 
	 * are, resp, baryVert, original corner, edge barycenter 
	 * (for Belyi map values {infty, 0,1}, resp). Also, 
	 * barycenter of each of the 2n barycentrically divided
	 * faces is degree 6 and is marked -1.
	 * 
	 * Strategy:
	 * * we work purely from 'tileFlower' data, so we discard and 
	 *   reconstitute all vertex indices using 'tileflowers2verts'.
	 *   
	 * TODO: See if this first step can be avoided and we can
	 *   restore the original indices for the vertices of the
	 *   original tiles.
	 *   
	 * * We create a packing for each tile as needed: this is a
	 *   barycentrically subdivided 2n-gon, recording original 
	 *   corner indices from tile (tile_v,pack_v) in its 'VertexMap'.
	 *   Note that each tile side has 4 edges.
	 * * the first tile is chosen as rootTile; we may need to make 
	 *   some choice so we have an easy base.
	 * * Each tile, including the first, is processed for slit-type 
	 *   self-pastings, surrounded unigons, etc. (E.g. suppose an 
	 *   n-gon shares one edge with a unigon; then we create and paste 
	 *   in that unigon.) (we'll see what other circumstances show up)
	 * * We have the growing 'masterPack'. We search through unpasted, 
	 *   non-bdry tile edges to find one which can be unambiguously pasted.
	 * * We create the packing for a new tile (processed as above), 
	 *   paste it in, then look for any self-pastings this may lead 
	 *   to for 'masterPack' itself.
	 * * We paste, adjust the 'VertexMap', and iterate until all tiles 
	 *   are included and all non-bdry tile edges are pasted.
	 * * We build 'dual', 'quad', and 'wg' tilings.
	 * 
	 * The resulting packing and its tile data should be the
	 * full deal, builtMode=3.
	 * @return PackData with attached TileData, null on error
	 */
	public PackData newfromFlowers() {

		boolean debug=false; // debug=true;
		
		// check that all tiles have 'tileFlower's
		boolean noflower=false;
		for (int t=1;(t<=origTD.tileCount && !noflower);t++) {
			Tile otile = origTD.myTiles[t];
			if (otile.tileFlower==null)
				noflower=true;
		}
		if (noflower) {
			TileData.setTileFlowers(origTD); // DebugHelp.printtileflowers(origTD);
		}

		// In case some tiles are null, we need to 
		//   reindex the tiles and put them in 
		//   order in 'tmpTiles'
		PackDCEL[] tileDCELs=new PackDCEL[origCount+1];
		int newindx=1;
		PackData pd=null;
		for (int j=1;j<=origCount;j++) {
			Tile tile=origTD.myTiles[j];
			if (tile!=null) {
				int[][] holdflower=tile.tileFlower;
				pd=tile.singleCanonical(3);
				tileDCELs[newindx]=pd.packDCEL;
				origTD.myTiles[j]=pd.tileData.myTiles[1];
				origTD.myTiles[j].tileIndex=newindx;
				origTD.myTiles[j].tileFlower=holdflower;
				newindx++;
			}
		}
		origTD.tileCount=newindx-1;

		// Problems working only with DCEL structures, so we
		//   keep recreating 'newPack' as we go
		PackData newPack=pd;

		// do pasting of all sides for each tile when it is
		//   attached to masterDCEL
		// 'touchedTiles[]' is 0 until tile has been
		//   attached. Then it is 1. After all its
		//   edges are done, it is set to 2.
		int[] touchedTiles=new int[origCount+1];
		PackDCEL masterDCEL=tileDCELs[1];
		touchedTiles[1]=1;
		
		// two lists of tiles: attach all from 'curr' 
		//   and complete all edge pastings. Add
		//   new times encountered to 'next'
		ArrayList<Integer> curr=new ArrayList<Integer>();
		ArrayList<Integer> next=new ArrayList<Integer>();
		next.add(1);
		while (!next.isEmpty()) {
			curr=next;
			next=new ArrayList<Integer>();
			
			while (!curr.isEmpty()) {
				Tile currT=origTD.myTiles[curr.remove(0)];
				if (touchedTiles[currT.tileIndex]==2)
					continue; // already done
				
				// find sides to paste; each has 4 edges.
				// after sides are pasted, indicate by 
				// setting tileFlower[c][0] negative.
				int cvc=currT.vertCount;
				int v=-1;
				int w=-1;
				for (int c=0;c<cvc;c++) {
					int[] cLeaf=currT.tileFlower[c];
					if (cLeaf[0]<=0)
						continue; // already pasted or bdry
					Tile nghbT=origTD.myTiles[cLeaf[0]];
					boolean selfadjoin=true;
					if (touchedTiles[nghbT.tileIndex]==0) // 'nghbT' not yet attached
						selfadjoin=false;
					int side=cLeaf[1];

					// we work with clones
					PackDCEL pdc1=CombDCEL.cloneDCEL(masterDCEL);
					int vertCount1=pdc1.vertCount;
					PackDCEL pdc2=CombDCEL.cloneDCEL(tileDCELs[nghbT.tileIndex]);
					int vertCount2=pdc2.vertCount;
					// for adjoining, need far end of side of currT
					v=currT.vert[(c+1)%currT.vertCount];
					w=nghbT.vert[side];
					
// debugging
//System.out.println("next pasting: "+currT.tileIndex+"/"+c
//		+" to "+nghbT.tileIndex+"/"+side+"; vertices v = "+v+" to w = "+w);
					if (debug) { // debug=true; 
						return newPack;
					}

					// do the pasting
					if (!selfadjoin) {
						masterDCEL=CombDCEL.adjoin(pdc1,pdc2,v,w,4);
					}
					else {
						masterDCEL=CombDCEL.adjoin(pdc1,pdc1,v,w,4);
					}
					
					VertexMap oldnew=masterDCEL.oldNew;
					CombDCEL.redchain_by_edge(masterDCEL, null, null, selfadjoin);
					CombDCEL.fillInside(masterDCEL);
					masterDCEL.oldNew=oldnew;
					newPack=new PackData(null);
					newPack.attachDCEL(masterDCEL);
					newPack.tileData=origTD;
					newPack.tileData.packData=newPack;
					PackDCEL pdcel=newPack.packDCEL;
					newPack.vertexMap=masterDCEL.oldNew;
					newPack.status=true;
					
// debugging
//DCELdebug.printVertexMap(newPack.vertexMap);

					// record touch?
					if (touchedTiles[nghbT.tileIndex]==0) {
						touchedTiles[nghbT.tileIndex]=1;
						next.add(nghbT.tileIndex);
					}

					// adjust various 'Tile.vert' entries using oldNew
					
					// if a new tile is being added, only adjust it
					if (!selfadjoin) { // DCELdebug.printVertexMap(newPack.vertexMap);
						
// debugging
//printTileVerts(nghbT);
		   				for (int k=0;k<nghbT.vertCount;k++) {
		   					int nv=newPack.vertexMap.findW(nghbT.vert[k]);
		   					if (nv!=0)
		   						nghbT.vert[k]=nv;
		   				}
		   				for (int k=0;k<nghbT.augVertCount;k++) {
		   					int nv=newPack.vertexMap.findW(nghbT.augVert[k]);
		   					if (nv!=0)
		   						nghbT.augVert[k]=nv;
		   				}
		   				int mv=newPack.vertexMap.findW(nghbT.baryVert);
		   				if (mv!=0)
		   					nghbT.baryVert=mv;
// debugging
//printTileVerts(nghbT);
					}
					
					// else, look through all touched tiles
					else {
						for (int t=1;t<=origTD.tileCount;t++) {
							Tile ttile=origTD.myTiles[t];
							if (touchedTiles[t]>0) {

// debugging
//printTileVerts(ttile);
								for (int k=0;k<ttile.vertCount;k++) {
									int nv=newPack.vertexMap.findW(ttile.vert[k]);
									if (nv!=0)
										ttile.vert[k]=nv;
								}
								for (int k=0;k<ttile.augVertCount;k++) {
									int nv=newPack.vertexMap.findW(ttile.augVert[k]);
									if (nv!=0)
										ttile.augVert[k]=nv;
								}
								int mv=newPack.vertexMap.findW(ttile.baryVert);
								if (mv!=0)
									ttile.baryVert=mv;
							}
						}
					}

// debugging					
//System.out.println("after pasting, tile baryverts "+currT.baryVert
//					+ " pasted to "+nghbT.baryVert+"\n");

					// indicate these sides are done
					cLeaf[0]=-cLeaf[0]; // show it's been used
					int[] nLeaf=nghbT.tileFlower[side];
					nLeaf[0]=-nLeaf[0];
				} // done with all sides of this tile
				touchedTiles[currT.tileIndex]=2;
			} // done with all tiles in 'curr'
		} // continue until 'next' is empty

		// make tileFlowers entries non-negative
		for (int t=1;t<=origTD.tileCount;t++) {
			Tile tile=origTD.myTiles[t];
			int[][] tf=tile.tileFlower;
			for (int j=0;j<tile.vertCount;j++)
				if (tf[j][0]<0)
					tf[j][0]=-tf[j][0];
		}
			
		newPack.tileData=origTD;
		newPack.tileData.packData=newPack;
		newPack.set_aim_default();
		newPack.status=true;
		return newPack;
	}
	
	/**
	 * Start with packing for barycentric subdivision of 
	 * tiling. The packing has 'tileData' and vertices 
	 * are marked: 1 = tile baryVert, 2 = tile corner, 
	 * 3 = tile edge barycenter. Barycenters of 
	 * bary-refined faces are marked -1. All tiles have 
	 * 'augVert's.
	 * 
	 * Here we build 'dual', 'quad', and all white/grey tilings. 
	 * 
	 * When done, should have the full 'canonicalPack' for 
	 * 'ConformalTiling' extender
	 * @param p PackData
	 * @return int count of wgtiles
	 */
	public static int prepCanonical(PackData p) {
		
		// get various counts based on vertex markings
		int wgcount=0;
		for (int t=1;t<=p.tileData.tileCount;t++)
			wgcount += p.tileData.myTiles[t].vertCount*2;
		
		int dcount=0;
		int qcount=0;
		
		for (int v=1;v<=p.nodeCount;v++) {
			int mark=p.getVertMark(v);
			if (mark==2)
				dcount++;
			else if (mark==3)
				qcount++;
		}
		
		p.tileData.dualTileData=new TileData(dcount,p.tileData.builtMode);
		p.tileData.quadTileData=new TileData(qcount,p.tileData.builtMode);
		p.tileData.wgTiles=new Tile[wgcount+1];
		int wgtick=1;

		// go through tiles, create wgtiles
		for (int t=1;t<=p.tileData.tileCount;t++) {
			Tile tile=p.tileData.myTiles[t];
			int bv=tile.baryVert;
			
			// want 'halfedge' to be that going toward vert[0].
			Vertex vert=p.packDCEL.vertices[bv];
			HalfEdge he=vert.halfedge;
			HalfEdge wanthe=null; // the edge we want
			do {
				HalfEdge left=he.next.twin.next;
				HalfEdge right=he.twin.prev.twin.prev.twin;
				if (left.twin.origin.vertIndx==tile.vert[0] && left==right) { 
					wanthe=he;
					break;
				}
				he=he.prev.twin; // cclw
			} while (wanthe==null && he!=vert.halfedge);
			if (wanthe==null) {
				CirclePack.cpb.errMsg("didn't find edge toward vert[0]");
				return 0;
			}
			vert.halfedge=wanthe;
			int[] myflower=p.getFlower(bv);

			tile.wgIndices=new int[2*tile.vertCount];
			
			// visit sectors of the tile in succession
			for (int j=0;j<tile.vertCount;j++) {
				
				// two hex flower subfaces, first is white (positively oriented)
				Tile wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=1; // positively oriented 
				int baryV=myflower[4*j+1]; // wgtile baryVert
				wgtile.tileIndex=wgtick++;
				p.setVertMark(baryV,-wgtile.tileIndex); // store neg of index in baryVert.mark
				tile.wgIndices[2*j]=wgtile.tileIndex; // point to wgTile index
				p.tileData.wgTiles[wgtile.tileIndex]=wgtile; // put wgTile into master list
				int[] itsflower=p.getFlower(baryV);
				int indx=p.nghb(baryV,bv);
				for (int k=0;k<3;k++) {	// wgtile's augVert[0] points to the tile baryVert
					wgtile.vert[k]=wgtile.augVert[2*k]=itsflower[(indx+2*k)%6];
					wgtile.augVert[2*k+1]=itsflower[(indx+2*k+1)%6];
				}
				
				
				// second is grey (negatively oriented)
				wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=-1; // negatively oriented 
				baryV=myflower[4*j+3]; // its barycenter
				wgtile.tileIndex=wgtick++;
				p.setVertMark(baryV,-wgtile.tileIndex); // store neg of index in baryVert.mark
				tile.wgIndices[2*j+1]=wgtile.tileIndex; // point to wgTile index
				p.tileData.wgTiles[wgtile.tileIndex]=wgtile; // put wgTile into master list
				itsflower=p.getFlower(baryV);
				indx=p.nghb(baryV,bv);
				for (int k=0;k<3;k++) {	// wgtile's augVert[0] points to the tile baryVert
					wgtile.vert[k]=wgtile.augVert[2*k]=itsflower[(indx+2*k)%6];
					wgtile.augVert[2*k+1]=itsflower[(indx+2*k+1)%6];
				}
			}

		} // end of 'for' loop on tiles

		// all the wgTiles are created and indexed
		// Now build the dual tiling
		try {
			dcount=1;
			for (int v=1;v<=p.nodeCount;v++) {

				// dual tile for each original corner vertex
				if (p.getVertMark(v)==2) {
					int num=p.countFaces(v);
					int[] flower=p.getFlower(v);
					
					// bdry tile?
					if (p.isBdry(v)) { // this is bdry tile
						
						// create the dual tile
						Tile dtile=p.tileData.dualTileData.myTiles[dcount]= 
								new Tile(p.tileData.dualTileData,num/4+1);
						
						// vert list starts downstream edge barycenter, then
						//   encircles wg faces until it reaches upstream
						//   edge barycenter, then closes up to start (v is not
						//   a corner of the dual, but acts as barycenter).
						// Use sides of wgtile tiles opposite to v
						int[] augvert=new int[num+4];
						int[] vert=new int[num/4+1];
						dtile.wgIndices=new int[num/2];
						dtile.baryVert=v;
						
						int tick=0; // counts wgTile's
						// first: edge from bdry edge barycenter to first tile barycenter
						int cv=flower[1];
						int[] cvflower=p.getFlower(cv);
						int myindx=p.nghb(cv,v);
						vert[tick]=augvert[2*tick]=cvflower[(myindx+2)%6];
						augvert[2*tick+1]=cvflower[(myindx+3)%6];
						dtile.wgIndices[tick++]= -p.getVertMark(cv); // index is temp stored in 'mark'
						
						// then pairs of grey/white to get edges between tile barycenters
						for (int i=1;i<(num/4);i++) {
							cv=flower[4*i-1];
							myindx=p.nghb(cv,v);
							cvflower=p.getFlower(cv);
							augvert[2*tick]=cvflower[(myindx+2)%6]; 
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
							cv=flower[4*i+1];
							myindx=p.nghb(cv,v);
							cvflower=p.getFlower(cv);
							vert[tick/2]=augvert[2*tick]=cvflower[(myindx+2)%6];
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
						}							
						
						// then finish
						cv=flower[num-1];
						myindx=p.nghb(cv,v);
						cvflower=p.getFlower(cv);
						augvert[2*tick]=cvflower[(myindx+2)%6]; 
						augvert[2*tick+1]=cvflower[(myindx+3)%6];
						dtile.wgIndices[tick++]= -p.getVertMark(cv);
						// last vert is edge barycenter
						vert[tick/2]=augvert[2*tick]=cvflower[(myindx+4)%6];
						
						// finish with augmented boundary 
						augvert[2*tick+1]=flower[num];
						augvert[2*tick+2]=v;
						augvert[2*tick+3]=flower[0];
						p.tileData.dualTileData.myTiles[dcount].vert=vert;
						p.tileData.dualTileData.myTiles[dcount].augVertCount=num+4;
						p.tileData.dualTileData.myTiles[dcount].augVert=augvert;
					}

					// interior?
					else {
						// create the dual tile
						Tile dtile=p.tileData.dualTileData.myTiles[dcount]= 
								new Tile(p.tileData.dualTileData,num/4);
						dtile.wgIndices=new int[num/2];
						int []augvert=new int[num];
						int []vert=new int[num/4];
						
						// find a grey tile barycenter to start
						int gdir=-1;
						for (int j=0;(j<num && gdir<0);j++) {
							int cv=flower[j];
							int cmark=p.getVertMark(cv);
							if (cmark<0 && p.tileData.wgTiles[-cmark].mark==-1) // grey
								gdir=j;
						}
						if (gdir<0)
							throw new CombException("no grey wg tile next to "+v);
						
						// do in pairs, grey then white: get one 'vert', 4 'augverts' 
						int tick=0; // counts wgTile's
						for (int i=0;i<num;i=i+4) {
							int ii=(gdir+i)%num;
							int cv=flower[ii];
							int myindx=p.nghb(cv,v);
							int[] cvflower=p.getFlower(cv);
							vert[tick/2]=augvert[2*tick]=cvflower[(myindx+2)%6];
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
							cv=flower[(ii+2)%num];
							myindx=p.nghb(cv,v);
							cvflower=p.getFlower(cv);
							augvert[2*tick]=cvflower[(myindx+2)%6];
							augvert[2*tick+1]=cvflower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
						}							
						p.tileData.dualTileData.myTiles[dcount].vert=vert;
						p.tileData.dualTileData.myTiles[dcount].augVertCount=num;
						p.tileData.dualTileData.myTiles[dcount].augVert=augvert;
					}						
					
					p.tileData.dualTileData.myTiles[dcount].baryVert=v; 
					p.tileData.dualTileData.myTiles[dcount].tileIndex=dcount;
					dcount++;
				}
			} // end of 'for' on vertices for dual
		} catch (Exception ex) {
			p.tileData.dualTileData=null;
			CirclePack.cpb.errMsg("Failed to complete dual tiling data");
		}
		
		// next, build the quad tiling, one for each edge
		try {
			qcount=1;
			for (int v=1;v<=p.nodeCount;v++) {

				if (p.getVertMark(v)==3) { // v is edge barycenter

					
					// create the quad tile
					// Note: quad tiles always quadrilaterals. For edge barycenters
					//   on the bdry, edge barycenter is both corner and baryVert
					Tile qtile=p.tileData.quadTileData.myTiles[qcount]= 
							new Tile(p.tileData.quadTileData,4);
					int[] flower=p.getFlower(v);
					int []vert=new int[4];
					int []augvert=new int[8];

					if (p.isBdry(v)) { // bdry? 
						qtile.wgIndices=new int[2];

						// first is grey face
						int cv=flower[1];
						int myindx=p.nghb(cv,v);
						int[] cvflower=p.getFlower(cv);
						
						// these go at end
						augvert[6]=cvflower[myindx];
						augvert[7]=cvflower[(myindx+1)%6];
						
						vert[0]=augvert[0]=cvflower[(myindx+2)%6];
						augvert[1]=cvflower[(myindx+3)%6];
						vert[1]=augvert[2]=cvflower[(myindx+4)%6];
						qtile.wgIndices[0]= -p.getVertMark(cv);
						
						// next is white
						cv=flower[3];
						myindx=p.nghb(cv,v);
						cvflower=p.getFlower(cv);
						augvert[3]=cvflower[(myindx+3)%6];
						vert[2]=augvert[4]=cvflower[(myindx+4)%6];
						augvert[5]=cvflower[(myindx+5)%6];
						qtile.wgIndices[1]= -p.getVertMark(cv);
						vert[3]=v;

						p.tileData.quadTileData.myTiles[qcount].vert=vert;
						p.tileData.quadTileData.myTiles[qcount].augVertCount=8;
						p.tileData.quadTileData.myTiles[qcount].augVert=augvert;
					}
					
					else { // interior? num=4
						qtile.wgIndices=new int[4];
						int num=p.countFaces(v);

						// find a grey tile barycenter to start
						int gdir=-1;
						for (int j=0;(j<num && gdir<0);j++) {
							int cv=flower[j];
							int mark=p.getVertMark(cv);
							if (mark<0 && p.tileData.wgTiles[-mark].mark==-1) // grey
								gdir=j;
						}
						if (gdir<0)
							throw new CombException("quad tiling has no grey tile next to "+v);
						
						for (int j=0;j<4;j++) {
							int cv=flower[(gdir+2*j)%num];
							int myindx=p.nghb(cv,v);
							int[] cvflower=p.getFlower(cv);
							vert[j]=augvert[2*j]=cvflower[(myindx+2)%6];
							augvert[2*j+1]=cvflower[(myindx+3)%6];
							qtile.wgIndices[j]= -p.getVertMark(cv);
						}
						
						p.tileData.quadTileData.myTiles[qcount].vert=vert;
						p.tileData.quadTileData.myTiles[qcount].augVertCount=8;
						p.tileData.quadTileData.myTiles[qcount].augVert=augvert;
							
					}
					
					p.tileData.quadTileData.myTiles[qcount].baryVert=v; 
					p.tileData.quadTileData.myTiles[qcount].tileIndex=qcount;
					qcount++;
				}
			
			} // end of 'for' on vertices for quads

		} catch (Exception ex) {
			p.tileData.quadTileData=null;
			CirclePack.cpb.errMsg("Failed to complete dual or quad tile data");
		}
		
		p.tileData.packData=p;
		p.tileData.dualTileData.packData=p;
		p.tileData.quadTileData.packData=p;
		
		return wgtick-1;
	}
	
	/**
	 * After doing some pasting, we need to update 
	 * the tile.vert[] vectors of tileData 'td'. 
	 * @param td TileData
	 * @param vmap VertexMap
	 */
	public void updateTileVerts(TileData td,VertexMap vmap) {
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			if (tile==null)
				continue;
			
			// adjust vert
			for (int j=0;j<tile.vertCount;j++) {
				int newv=vmap.findW(tile.vert[j]);
				if (newv>0)
					tile.vert[j]=newv;
			}
			
			// adjust augVert
			if (tile.augVert!=null) {
				for (int j=0;j<tile.augVertCount;j++) {
					int newv=vmap.findW(tile.augVert[j]);
					if (newv>0)
						tile.augVert[j]=newv;
				}
			}
			
			// adjust baryVert
			int newv=vmap.findW(tile.baryVert);
			if (newv>0)
				tile.baryVert=newv;
		}
	}
	
	/**
	 * Look through masterPack for first instance of bdry
	 * edges which are contiguous and to be attached to one 
	 * another. (So, we're looking for a tile vert which is in 
	 * the bdry, and tile edges on each side are to be 
	 * identified according to origTD.)
	 * @return int, index v of tip, 0 for failure.
	 */
	public int check_for_slit() {
		
		Tile tile=null;
		Tile otile=null;

		for (int t=1;t<=growTD.tileCount;t++) {
			tile=growTD.myTiles[t];
			otile=null;
		
			// this should imply corresponding origTD tile is not null
			if (tile!=null) { 
				otile=origTD.myTiles[tile.tileIndex];
				int ecount = tile.vertCount;
				for (int e = 0; e < ecount; e++) {

// debugging: the 2 edges that may be pasted 
//					int ege=e;
//					int crossege=otile.tileFlower[e][1];
					
					int v = tile.vert[e];
					int nghb = otile.tileFlower[e][0];
					if (nghb == 0 || tileAdded[nghb] == 0)
						continue;
					// 	skip if edge is not on bdry of tPack (check v and its nghb vv)
					int vv=tile.augVert[4*e+1];

					if (!masterPack.isBdry(v) || !masterPack.isBdry(vv)) 
						continue;

					int nec = origTD.myTiles[nghb].vertCount;
					int oe = (otile.tileFlower[e][1] + 1) % nec; // other end

					if (tileAdded[nghb]>0 && v ==growTD.myTiles[nghb].vert[oe])
						return v;
				}
			}
		}
		return 0;
	}
	
	/**
	 * Used 'check_for_slit' to locate tip of slit v to 
	 * close up. Sew up the slit and adjust accordingly. 
	 * @return boolean, false if none sewn up
	 */
	public boolean sewOneSlit(int v) {
		if (v>0) {
			masterPack.packDCEL=CombDCEL.adjoin(
					masterPack.packDCEL,
					masterPack.packDCEL, v, v, 4);
			masterPack.vertexMap=masterPack.packDCEL.oldNew;
			masterPack.packDCEL.fixDCEL(masterPack);
						
			// pasted to self, so fix the tile data
			updateTileVerts(growTD, masterPack.vertexMap);
			return true;
		}
		return false;
	}

	/**
	 * Look through masterPack for tile edges which 
	 * are in the boundary and can swallow an 
	 * unattached tile. Adjoin and adjust accordingly. 
	 * (So, looking for edge having same vert at each 
	 * end in origTD.)
	 * @return boolean, false if none swallowed
	 */
	public boolean swallowOneUnigon() {
		
		Tile tile=null;
		Tile otile=null;

		for (int t=1;t<=growTD.tileCount;t++) {
			tile=growTD.myTiles[t];
			otile=null;
			
			if (tile != null) { // this should imply corresponding origTD tile is not null
				otile=origTD.myTiles[tile.tileIndex];
				int ecount = tile.vertCount;
				for (int e = 0; e < ecount; e++) {

					// skip if edge barycenter vert is not on bdry of tPack
					int eb = tile.augVert[4*e+2];
					if (masterPack.isBdry(eb)) 
						continue;

					// nghb invalid or already incorporated?
					int nghb = otile.tileFlower[e][0];
					if (nghb == 0 || tileAdded[nghb] > 0)
						continue;

					// do edge ends match in origTD?
					if (otile.vert[e] == otile.vert[(e + 1) % ecount]) {
						PackData newp = otile.singleCanonical(3);
						int w=newp.tileData.myTiles[1].vert[0];
						int v=tile.vert[(e+1)%ecount]; // need the next vertex to go clockwise
							
						// 'adjoin' is complicated. If ends already match in 'tile'
						//   then just usual adjoin
						if (tile.vert[e]==v) {
							masterPack.packDCEL=CombDCEL.adjoin(
									masterPack.packDCEL,newp.packDCEL, v,w,4);
							masterPack.vertexMap=masterPack.packDCEL.oldNew;
							// fix the new tile data
							updateTileVerts(newp.tileData,masterPack.vertexMap);
							growTD.myTiles[t] = newp.tileData.myTiles[1];
							if (copyFlowers(origTD.myTiles[t],growTD.myTiles[t])<=0)
								throw new ParserException("failed in loop work to copy tileFlower");
							growTD.myTiles[t].tileIndex = 1;
							tileAdded[t] = 1;
						}
						else { // must be done in two steps:
							//   (1) adjoin along 3 edges only: update tile verts, 
							//       add to growTD, transfer flowers, etc.
							//   (2) then zip last edge: update growTD
							// this will become tip of final slit
							int tip=newp.tileData.myTiles[1].augVert[3];
							masterPack.packDCEL=CombDCEL.adjoin(
									masterPack.packDCEL,newp.packDCEL, v,w,3);
							masterPack.vertexMap=masterPack.packDCEL.oldNew;
							tip=masterPack.vertexMap.findW(tip);

							// fix the new tile data
							updateTileVerts(newp.tileData,masterPack.vertexMap);
							growTD.myTiles[t] = newp.tileData.myTiles[1];
							if (copyFlowers(origTD.myTiles[t],growTD.myTiles[t])<=0)
								throw new ParserException("failed in loop work to copy tileFlower");
							growTD.myTiles[t].tileIndex = 1;
							tileAdded[t] = 1;
							masterPack.packDCEL=CombDCEL.adjoin(
									masterPack.packDCEL,masterPack.packDCEL,tip,tip,1);
							masterPack.vertexMap=masterPack.packDCEL.oldNew;
							updateTileVerts(growTD,masterPack.vertexMap);
						}
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Copy tileFlower from tile 't1' to 't2
	 * @param t1 Tile
	 * @param t2 Tile
	 * @return vertCount, 0 on error
	 */
	public int copyFlowers(Tile t1,Tile t2) {
		if (t1.tileFlower==null)
			return 0;
		t2.tileFlower=new int[t1.vertCount][2];
		try {
			for (int e=0;e<t1.vertCount;e++) {
				t2.tileFlower[e][0]=t1.tileFlower[e][0];
				t2.tileFlower[e][1]=t1.tileFlower[e][1];
			}
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("error copying tileFlower "+t1.tileIndex);
			return 0;
		}
		return t1.vertCount;
	}
	
	/**
	 * Given a tile and edge of origTD, determine if 
	 * the ends of the edge are the same vertex.
	 * @param tileIndex
	 * @param edgeIndex
	 * @return boolean, true if this edge is a loop
	 */
	public boolean loopProblem(int tileIndex,int edgeIndex) {
		Tile otile=origTD.myTiles[tileIndex];
		if (otile==null)
			return false;
		int ecount=otile.vertCount;
		if (ecount==1)
			return true;
		int v=otile.vert[edgeIndex];
		int w=otile.vert[(edgeIndex+1)%ecount];
		if (v==w)
			return true;
		return false;
	}
	
	/**
	 * If tiles t1, t2 share an edge, then we need to know
	 * how to paste them. We return ans (int[]) with entries:
	 *   * ans[0] = index in 't1.vert' where pasting starts
	 *   * ans[1] = number of clockwise tile edges to paste
	 *   * ans[2] = index in 't2.vert' where pasting starts
	 *   * ans[3] = number of counterclockwise tile edges to paste.
	 * Both t1 and t2 must have 'tileFlower's. (This routine
	 * follows the orientation conventions of 'adjoin' when 
	 * adjoining t2 to t1.)
	 *   
	 * Note that t1 and t2 may be the same tile, but we 
	 * can handle only the limited case of pasting 
	 * associated with a single slit.
	 * 
	 * TODO: we are not ready for cases of ambiguity, 
	 * which may have to be resolved using 'tData' and 
	 * topological considerations. E.g. t1=t2 and 
	 * sharing non-contiguous edges, t1 and t2 hitting 
	 * multiple times, etc.
	 * 
	 * If problems come up, throw 'DataException' or 
	 * return null.
	 * 
	 * @param tData TileData
	 * @param t1 Tile
	 * @param t2 Tile
	 * @return int[4], null if t1/t2 don't share any edges,
	 *         negative entries in problem cases. Null 
	 *         if 'tileFlower' info is not set.
	 */
	public static int []tilePastings(TileData tData,Tile t1,Tile t2) {
		int []ans=new int[4];		
		if (t1.tileFlower==null || t2.tileFlower==null)
			return null;
		
		// check if t1 and t2 share full boundaries
		boolean t1full=true;
		boolean t2full=true;
		for (int j=0;(j<t1.vertCount && t1full);j++) {
			int ti=t1.vert[j];
			if (ti!=t2.tileIndex)
				t1full=false;
		}
		for (int j=0;(j<t2.vertCount && t2full);j++) {
			int ti=t2.vert[j];
			if (ti!=t1.tileIndex)
				t2full=false;
		}
		
		if (t1full && t2full) {
			
			// TODO: if t1 is single cell complementary to a tree on the sphere,
			//       then need more information to decide where to start pasting.
			if (t1.tileIndex==t2.tileIndex)
				throw new CombException("tile share all edges with itself; ambiguity, tree complement");
			if (t1.vertCount!=t2.vertCount)
				throw new CombException("tiles share all edges, but not equal");
			ans[0]=ans[2]=0;
			ans[1]=ans[3]=t1.vertCount;
			return ans;
		}
		else if (t1full || t2full) // one is full, but not both
			throw new CombException("ambiguity: where to attach if all edges involved?"); 
		
		// find largest index of edge t1 shares with t2
		int hit2=-1;
		for (int j=(t1.vertCount-1);(j>=0 && hit2<0);j--) {
			int ti=t1.vert[j];
			if (ti==t2.tileIndex)
				hit2=j;
		}
		// find largest index of edge t2 shares with t1
		int hit1=-1;
		for (int j=0;(j<t2.vertCount && hit2<0);j--) {
			int ti=t1.vert[j];
			if (ti==t2.tileIndex)
				hit2=j;
		}
		
		// do t1 analysis first.
		if (hit2==t1.vertCount) { // this is last edge, look ccw
			int tick=0;
			while ((tick<(t1.vertCount-1)) && Math.abs(t1.tileFlower[tick][0])==t2.tileIndex)
				tick++;
			hit2=tick;
		}

		// now count number of clockwise edges shared with t2
		int t1length=0;
		int ptr=(hit2-1+t1.vertCount)%t1.vertCount;
		while (Math.abs(t1.tileFlower[ptr][0])==t2.tileIndex) {
			t1length++;
			ptr=(ptr-1+t1.vertCount)%t1.vertCount;
		}
		
		
		// now do t2
		if (hit1==0 && Math.abs(t2.tileFlower[t2.vertCount-1][0])==t1.tileIndex) { // need to back up
			hit1=t2.vertCount-1;
			while (Math.abs(t2.tileFlower[(hit1-1+t2.vertCount)%t2.vertCount][0])==t1.tileIndex)
				hit1=(hit1-1+t2.vertCount)%t2.vertCount;
		}

		// count number of ccw edges shared with t1
		int t2length=0;
		ptr=hit1;
		while (Math.abs(t2.tileFlower[ptr][0])==t1.tileIndex) {
			t2length++;
			ptr=(ptr+1)%t2.vertCount;
		}
			
		
		// some checks are these the same tile in 'tData'
		// TODO: this only allows for pasting of a slit
		if (t1.tileIndex==t2.tileIndex) {
			// lengths must be even and the same
			if (t1length!=t2length || 2*(int)(t1length/2)!=t1length) {
				t1length=t1length/2;
				t2length=t2length/2;
			}
			else
				throw new CombException("self pasting problem");
		}
			
		// For t1, start at 'vert[ans[0]]' (end vertex for first edge shared with t2)
		//   and go clockwise ans[1] edges. 
		ans[0]=hit2;
		ans[1]=t1length;
		// For t2, start at 'vert[ans[2]]' (beginning vert for first edge shared with t1)
		//   and go ccw ans[3] edges.
		ans[2]=hit2;
		ans[3]=t1length;

		return ans;
	}
	
	/**
	 * This routine adds tile vertices to tiles of 'td'
	 * based entirely on 'td's tileFlowers. This accommodates
	 * cases, such as existence of digons, slits, 
	 * or self-pastings (e.g., perhaps non-simply connected 
	 * surfaces), where we must specify the tiling using 
	 * 'tileFlower' information (as when it was read from a 
	 * file with data in form 'TILEFLOWERS:'). 
	 * 
	 * Return nodecount, -1 on failure (e.g., if 'tileflowers' 
	 * are not set in 'td');

	 * @param td TileData
	 * @return int, nodecount, -1 on error
	 */
	public static int tileflowers2verts(TileData td) {
		
		// bad data?
		if (td.myTiles==null || td.tileCount<=0)
			return -1;
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			if (tile.tileFlower==null)
				return -1;
			td.myTiles[t].vert=new int[tile.vertCount]; // toss old indices
		}
		
		// process tile-by-tile, finishing all vertices of each
		//   before going on. 
		// Within a tile, proceed through the vertices in order, 
		//   labeling each vertex as its full tile neighborhood is 
		//   completed.
		int[] doneTiles = new int[td.tileCount + 1]; // 1 when tile is done

		// Alternate between curr/next lists of tiles visited.
		NodeLink next=new NodeLink();
		NodeLink curr = new NodeLink();
		int tick = 1;
		next.add(tick);
		while (next.size() > 0) {
			curr = next;
			next = new NodeLink();
			int vert = 0;
			Iterator<Integer> tls = curr.iterator();
			while (tls.hasNext()) {
				Tile tile = td.myTiles[tls.next()];
				if (doneTiles[tile.tileIndex] > 0)
					continue;
				int vcount = tile.vertCount;

				for (int i = 0; i < vcount; i++) {
					if (tile.vert[i]<=0) {
						vert = tile.vert[i] = tick++; // set the index

						// proceed ccw, starting with edge before vert
						int myedge = (i - 1 + vcount) % vcount;
						Tile prevtile = tile;
						int nextIndex = -1;

						// go until we get to bdry or return to 'tile' across edge 'i'.
						while (prevtile != null
								&& (nextIndex = prevtile.tileFlower[myedge][0]) != 0
								&& (nextIndex != tile.tileIndex || prevtile.tileFlower[myedge][1]!=i)) {
							int nghbedge = prevtile.tileFlower[myedge][1];
							int prevIndex = prevtile.tileIndex;
							Tile nghbtile = td.myTiles[nextIndex];
							next.add(nextIndex);
							int nghbcount = nghbtile.vertCount;
							// check compatibility
							if (nghbtile.tileFlower[nghbedge][0] != prevIndex
									|| nghbtile.tileFlower[nghbedge][1] != myedge)
								throw new CombException("faulty tile nghb relationship");

							// here's goal: set correct index in nghbtile.vert
							nghbtile.vert[nghbedge] = vert;
							// Is nghbtile a unigon? done
							if (nghbcount == 1)
								break;

							myedge = (nghbedge - 1 + nghbcount) % nghbcount;
							prevtile = nghbtile;
						} // end of while through counterclockwise

						// if not done, go clockwise about v
						if (nextIndex != tile.tileIndex) {
							myedge = i;
							prevtile = tile;
							
							// in this direction we must eventually hit the bdry
							while (prevtile != null
									&& (nextIndex = prevtile.tileFlower[myedge][0]) != 0) {
								int nghbedge = prevtile.tileFlower[myedge][1];
								int oldindex = prevtile.tileIndex;
								Tile nghbtile = td.myTiles[nextIndex];
								next.add(nextIndex);
								int nghbcount = nghbtile.vertCount;
							
								// check compatibility
								if (nghbtile.tileFlower[nghbedge][0] != oldindex
										|| nghbtile.tileFlower[nghbedge][1] != myedge)
									throw new CombException("faulty tile nghb relationship");

								// here's goal: set correct index in nghbtile.vert
								nghbtile.vert[(nghbedge + 1) % nghbcount] = vert;

								// Is nghbtile a unigon? done
								if (nghbcount == 1)
									break;

								myedge = (nghbedge + 1) % nghbcount;
								prevtile = nghbtile;
							} // end of while through clockwise
						}
					} // end of for through vertices

					doneTiles[tile.tileIndex] = 1;
				}
			} // end of while going through curr
		} // done, 'next' is now empty

		return tick-1;
	}
	
	public void printTileVerts(Tile tile) {
		StringBuilder strbld=new StringBuilder("Tile: "+tile.tileIndex+" baryVert = "+tile.baryVert+"; disp -cfn  ");
		for (int j=0;j<tile.vertCount;j++)
			strbld.append(tile.vert[j]+"  ");
		System.out.println(strbld.toString());
	}
}
