package tiling;

import java.util.Iterator;

import listManip.EdgeLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import allMains.CirclePack;
import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.ParserException;

/**
 * TileBuilder is intended for building 'TileData' structures and their packings.
 * 
 * NOTE: We assume our tiling is simply connected --- there are too many 
 * problems to consider for the multiply connected cases.
 *  
 * Basic method here is build tile-by-tile so we can handle situations that
 * come up when applying subdivision rules, building dessins, fusion tilings, etc.
 * Namely, we need to handle unigons, digons, and various self-pastings.
 * 
 * For recursive use, we need to allow 'origTD' to have some "null" tiles:
 * E.g. 'origTD' may be a connected subset of tiles from a larger tiling, but
 * it keeps the original tile indices and inherits original 'myTiles' length.
 * 
 * Idea: start with rootTile and keep adding tiles. There are many complications.
 * E.g., a loop is an edge with same vertex at its ends, and to paste along a
 * loop in 'masterPack' requires special 'adjoin' method: do all but one of the 
 * sub-edge pastings with usual adjoin, fix up the packing, then do one last 
 * self-adjoin to close the last edge. However, this may require recursive 
 * calls so the stuff being adjoined, if not just a unigon, has already been
 * consolidated in a simply connected packing.
 * 
 * Issues:
 *  * tiles may attach along edges with multiple vertices
 *  * tiles may attach to themselves (more than once, even) along edges or at vertices
 *  * two tiles may be attached along more than one, perhaps non-contiguous edges.
 *  * tiles may be unigons or digons
 *  * tiles must have 'tileflower' data: 
 *  *    t=tile.tileflower[i][0] is the index of the tile across edge i (if t=0, this is
 *  *    a bdry edge) and tile.tileflower[i][1] is the index of the shared edge in t.
 *  
 * @author kstephe2, started November 2013, restarted 7/2014
 *
 */
public class TileBuilder {
	
	PackData masterPack; // the packing we start and add to as we go
	// incoming TileData, remains unchanged for reference
	TileData origTD; // 'origTD.myTiles' should be of size 'origCount+1', but may have missing tiles
	int origCount;  // original count of tiles.
	TileData growTD; // build new full tiles as we go along, keeping original 'tileIndex's
	int []tileAdded; // set when tile has been added to growTD
	int []tileFull;  // set when tile's edges all pasted
	
	// Constructor
	public TileBuilder(TileData td) {
		masterPack=null;
		origTD=td;
		origCount=origTD.myTiles.length-1;
		growTD=new TileData(origCount,td.builtMode);
		
		// keep track: tiles added, tiles full
		tileAdded=new int[origCount+1];;  
		tileFull=new int[origCount+1];;  
	}

	/**
	 * Build associated full barycentric complex for origTD: that is,
	 * every n-tile is barycentrically subdivided to form 2n triangles, and
	 * these are then barycentrically subdivided. (This is required, e.g., 
	 * to handle unigons, digons, and slits.) Vertices get marks {1,2,3}
	 * if they are, resp, baryVert, original corner, edge barycenter (
	 * for Belyi map values {infty, 0,1}. Also, barycenter of each hex
	 * face is marked -1.
	 * 
	 * Strategy:
	 * * we work purely from 'tileFlower' data, so we discard and reconstitute
	 *   all vert indices using 'tileflowers2verts'.
	 * * We create a packing for each tile as needed: barycentrically subdivided
	 *   2n-gon, recording original corner indices from tile (tile_v,pack_v) in 
	 *   its 'VertexMap'.
	 * * the first tile is chosen as rootTile; we may need to make some choice 
	 *   so we have an easy base.
	 * * Each tile, including the first, is processed for slit-type self-pastings,
	 *   surrounded unigons, etc. (E.g. suppose an n-gon shares one edge with a unigon; then
	 *   we create and paste in that unigon.) (we'll see what other circumstances show up)
	 * * We have the growing 'masterPack'. We search through unpasted, non-bdry
	 *   tile edges to find one which can be unambiguously pasted.
	 * * We create the packing for a new tile (processed as above), paste it in, 
	 *   then look for any self-pastings this may lead to for 'masterPack' itself.
	 * * We past, adjust the 'VertexMap', and iterate until all tiles are in and all non-bdry
	 *   tile edges are pasted.
	 * @return PackData with attached TileData, null on error
	 */
	public PackData fullfromFlowers() {

		boolean debug=false; // debug=true;
		
		// check that all tiles have 'tileFlower's
		boolean gotflowers=true;
		for (int t=1;(t<=origTD.tileCount && gotflowers);t++) {
			Tile otile = origTD.myTiles[t];
			if (otile.tileFlower==null)
				gotflowers=false;
		}
		
		if (!gotflowers)
			return null;

		// throw out the original vertex indices and rebuild from flowers
		for (int t=1;t<=origTD.tileCount;t++) {
			Tile otile = origTD.myTiles[t];
			if (otile!=null) {
				otile.vert=new int[otile.vertCount];
				otile.augVert=null;
				otile.augVertCount=-1;
			}
		}
		
		try {
			if (tileflowers2verts(origTD)<=0)
				throw new CombException();
		} catch (Exception ex) {
			throw new CombException("'TileBuilder' could not create vertices from 'tileFlowers'");
		}
		
		// mark missing tiles as "done".
		for (int t=1;t<=origCount;t++)
			if (origTD.myTiles[t]==null)
				tileAdded[t]=1;
		
		// root tile to use: find first that exists.
		int root=1;
		while (root<origCount && tileAdded[root]==1) // find one that exists
			root++;

		// now try to find first with at least 3 edges, else use original
		int tmproot=-1;
		while (tmproot<0 && root<origCount && tileAdded[root]==1) {
			if (origTD.myTiles[root].vertCount>2)
				tmproot=root;
			else
				root++;
		}
		
		if (tmproot>1)
			root=tmproot;

		while (root<origCount && tileAdded[root]==1)
			root++;
		
		// create the initial packing
		masterPack=origTD.myTiles[root].singleCanonical(3); 
		masterPack.tileData.myTiles[1].tileIndex=root;

		// add tile to growTD
		growTD.myTiles[root]=masterPack.tileData.myTiles[1];
		growTD.myTiles[root].tileIndex = root;
		if (copyFlowers(origTD.myTiles[root],growTD.myTiles[root])<=0)
			throw new ParserException("failed to copy tileFlower");
		masterPack.tileData=null; // all tiles are kept in growTD
		tileAdded[root]=1;

		// do any slit-sewing, unigon swallowing that's needed
		masterPack.complex_count(true);
		boolean keepup=true;
		while (keepup) {
			keepup=false;
			keepup=false;
			boolean newslit=sewOneSlit();
			boolean newunigon=swallowOneUnigon();
			if (newslit || newunigon) {
				masterPack.complex_count(true);
				keepup=true;
			}
		}
		
		if (debug) { // debug=true;
//			DebugHelp.debugPackWrite(masterPack,"startPack"+root+".p");
			int ntc=growTD.myTiles[root].augVertCount;
			StringBuilder strbld=new StringBuilder("roottile "+root+": augverts:");
			for (int jj=0;jj<ntc;jj++)
				strbld.append(" "+growTD.myTiles[root].augVert[jj]);
			System.out.println(strbld.toString());
		}
		
		// main loop for unattached
		boolean hit=true;
		int safety=10000;
		while (hit && safety>0) {
			hit=false;
			safety--;
			
			// look for unattached tile
			for (int t=1;t<=origTD.tileCount;t++) {
				if (origTD.myTiles[t]!=null && tileAdded[t]==0) {
					Tile otile=origTD.myTiles[t];
					int ecount=otile.vertCount;
					for (int j=0;(j<ecount && tileAdded[t]==0);j++) {
						int nghb=0;
						if ((nghb=otile.tileFlower[j][0])>0 && tileAdded[nghb]>0) {
							
							// OK, found one to paste this onto
							Tile tile=growTD.myTiles[nghb];
							int edge=otile.tileFlower[j][1];
							int mcount=tile.vertCount;
							int v=tile.vert[(edge+1)%mcount]; // vert at far end
							
							PackData newp = otile.singleCanonical(3);
							int w=newp.tileData.myTiles[1].vert[j];
							if (masterPack.adjoin(newp, v,w,4) != 1)
								throw new ParserException("didn't adjoin 'newp' correctly");
							
							// transfer 'mark'
							for (int k=1;k<=newp.nodeCount;k++) {
								masterPack.setVertMark(masterPack.vertexMap.findW(k),newp.getVertMark(k));
							}
							
							tileAdded[t] = 1;
							masterPack.complex_count(true);

							// fix the new tile's data
							updateTileVerts(newp.tileData,masterPack.vertexMap);
							growTD.myTiles[t] = newp.tileData.myTiles[1];
							if (copyFlowers(origTD.myTiles[t],growTD.myTiles[t])<=0)
								throw new ParserException("failed to copy tileFlower");
							growTD.myTiles[t].tileIndex = t;

							// fix any slit-sewing, loop pasting that's needed on new masterPack
							keepup=true;
							while (keepup) {
								keepup=false;
								boolean newslit=sewOneSlit();
								boolean newunigon=swallowOneUnigon();
								if (newslit || newunigon) {
									masterPack.complex_count(true);
									keepup=true;
								}
							}
										
							if (debug) { // debug=true;
								DebugHelp.debugPackWrite(newp,"addTilePack"+t+".p"); // DebugHelp.debugPackWrite(masterPack,"newMaster.p");
								int ntc=growTD.myTiles[t].augVertCount;
								StringBuilder strbld=new StringBuilder("tile "+t+" augverts:");
								for (int jj=0;jj<ntc;jj++)
									strbld.append(" "+growTD.myTiles[t].augVert[jj]);
								System.out.println(strbld.toString());
							}
							
							hit=true;
						}
					}
				}
			} // done with for for unattached files
		} // end of while to get unattached files
			
		if (safety<=0)
			throw new ParserException("ran through unattached safety fence in building growTD");

		// check for missing tiles and set 'hit' if there are unpasted edges
		hit=false;
		for (int t=1;t<=origTD.tileCount;t++) {
			if (tileAdded[t]<=0) {
				CirclePack.cpb.errMsg("Missing tile "+t+" in 'fullfromFlowers'");
				return masterPack;
			}
			Tile tile=growTD.myTiles[t];
			int ecount=tile.vertCount;
			for (int j=0;(j<ecount && !hit);j++) {
				int eb=tile.augVert[j*4+2];
				if (tile.tileFlower[j][0]>0 && masterPack.isBdry(eb))
					hit=true; // here's one that's not yet pasted
			}
		}
		
		// at this point, should be simply connected; else make bdry poison
		masterPack.vlist=new NodeLink(masterPack,"b");
		masterPack.elist=new EdgeLink(masterPack,"b");
						
		// hit indicates unpasted edges (e.g., in closing up a surface)
		int debugPass=-1; // debugPass=0;
		if (debugPass==0) // see the tileFlowers
			DebugHelp.debugTileFlowers(origTD);
		while (hit && safety > 0) {
			hit = false;
			safety--;
//			updateTileVerts(growTD, masterPack.vertexMap);
			
			// debug??
			if (debugPass==0) {
				DebugHelp.debugPackWrite(masterPack, "MasterStart.p");
				System.out.println("\nMasterStart.p");
				for (int tt=1;tt<=growTD.tileCount;tt++) 
					DebugHelp.debugTileVerts(growTD.myTiles[tt]);
				debugPass++;
			}
			
			// look for unpasted edge
			for (int t = 1; t <= origTD.tileCount; t++) {
				Tile tile = growTD.myTiles[t];
				int ecount = tile.vertCount;
				int myedge = -1;
				int nghbindex = -1;
				for (int j = 0; (j < ecount && myedge<0); j++) {
					int eb = tile.augVert[j * 4 + 2];
					if (tile.tileFlower[j][0] > 0
							&& masterPack.isBdry(eb)) {
						nghbindex = tile.tileFlower[j][0];
						myedge = j;
					}
				}

				if (myedge>=0) {
// debug
//System.out.println("\n Paste ("+tile.tileIndex+","+myedge+") to ("+nghbindex+","+tile.tileFlower[myedge][1]+")\n");
					int v = tile.vert[(myedge + 1) % ecount]; // upstream due to
																// orientation
					int w = growTD.myTiles[nghbindex].vert[tile.tileFlower[myedge][1]];

					// shouldn't happen, but do these edges share an endpoint?
					if (v == w)
						w = v;
					else if (w == tile.vert[myedge])
						v = w;
					if (debugPass>=0)
						System.out.println("Adjoin v = "+v+" to "+w);
					if (masterPack.adjoin(masterPack, v, w, 4) != 1)
						throw new ParserException(
								"didn't adjoin 'masterPack' to itself for unpasted edge");
					masterPack.complex_count(true);
					updateTileVerts(growTD, masterPack.vertexMap);
					if (debugPass>0) {
						DebugHelp.debugPackWrite(masterPack, "Master_"+debugPass+".p");
						System.out.println("\nMaster_"+debugPass+".p");
						for (int tt=1;tt<=growTD.tileCount;tt++) 
							DebugHelp.debugTileVerts(growTD.myTiles[tt]);
						debugPass++;
					}

					// fix any slit-sewing
					keepup = true;
					while (keepup) {
						keepup = false;
						boolean newslit = sewOneSlit();
						boolean newunigon = swallowOneUnigon();
						if (newslit && debugPass>0) {
							DebugHelp.debugPackWrite(masterPack, "Master_"+debugPass+".p");
							System.out.println("\nMaster_"+debugPass+".p");
							for (int tt=1;tt<=growTD.tileCount;tt++) 
								DebugHelp.debugTileVerts(growTD.myTiles[tt]);
							debugPass++;
						}
						if (newslit || newunigon) {
							masterPack.complex_count(true);
							keepup = true;
						}
					}

					if (debug) { // debug=true;
						DebugHelp.debugPackWrite(masterPack, "wrapupMaster.p");
						int ntc = growTD.myTiles[t].augVertCount;
						StringBuilder strbld = new StringBuilder("tile " + t
								+ " augverts:");
						for (int jj = 0; jj < ntc; jj++)
							strbld.append(" " + growTD.myTiles[t].augVert[jj]);
						System.out.println(strbld.toString());
					}
					
				} // done with this edge
			} // done going through tiles

		} // end of outer while for unpasted
		
		if (safety<=0)
			throw new ParserException("ran through unpasted edge safety fence in building growTD");
	
		masterPack.tileData=growTD;
		if (debug) { // debug=true;
			DebugHelp.debugPackWrite(masterPack,"masterPack");
		}
		masterPack.set_aim_default();
	    masterPack.complex_count(true);
	    masterPack.facedraworder(false);
	    masterPack.fillcurves();
	    return masterPack;
	}
	
	/**
	 * Start with packing for barycentric subdivision of tiling. The packing has
	 * 'tileData' and vertices are marked: 1 = tile baryVert, 2 = tile corner,
	 * 3 = tile edge barycenter. Barycenters of hex-refined faces are marked -1.
	 * All tiles have 'augVert's.
	 * 
	 * Here we build the 'dual', 'quad', and all the white and grey tilings. 
	 * 
	 * When done, should have the full 'canonicalPack' for 'ConformalTiling' extender
	 * @param p PackData
	 * @param mode int, tiling mode
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
		p.complex_count(true);
		
		p.tileData.dualTileData=new TileData(dcount,p.tileData.builtMode);
		p.tileData.quadTileData=new TileData(qcount,p.tileData.builtMode);
		p.tileData.wgTiles=new Tile[wgcount+1];
		int wgtick=1;

		// go through tiles, create wgtiles
		for (int t=1;t<=p.tileData.tileCount;t++) {
			Tile tile=p.tileData.myTiles[t];
			int bv=tile.baryVert;
			int num=p.getNum(bv);

			// processing requries adjusting flower of baryVert so flower[0] 
			// is in direction of vert[0]
			// TODO: to handle dual tilings consistently, have to consider 
			//       case that baryVert is on the boundary (and tile bdry 
			//       goes through it. Best is vert[0] is required to be 
			//       flower[0] in this case from the beginning.
			int offset=-1;
			int []myflower=p.kData[bv].flower;
			for (int k=0;(k<num && offset<0);k++) {
			int m=myflower[k];
			if (p.getNum(m)==4 && p.nghb(m, tile.vert[0])>=0)
					offset=k;
			}
			if (offset<0) {
				throw new CombException("Tile "+t+" (center "+bv+") "+
					"refined flower has some problem");
			}
			if (offset>0) {
				int []newflower=new int[num+1];
				for (int k=0;k<num;k++)
					newflower[k]=p.kData[bv].flower[(k+offset)%num];
				newflower[num]=newflower[0];
				p.kData[bv].flower=newflower;
			}
			
			tile.wgIndices=new int[2*tile.vertCount];
			
			// visit sectors of the tile in succession
			for (int j=0;j<tile.vertCount;j++) {
				
				// two hex flower subfaces, first is white (positively oriented)
				Tile wgtile=new Tile(3);
				wgtile.augVertCount=6;
				wgtile.augVert=new int[6];
				wgtile.mark=1; // positively oriented 
				int baryV=p.kData[bv].flower[4*j+1]; // wgtile baryVert
				wgtile.tileIndex=wgtick++;
				p.setVertMark(baryV,-wgtile.tileIndex); // store neg of index in baryVert.mark
				tile.wgIndices[2*j]=wgtile.tileIndex; // point to wgTile index
				p.tileData.wgTiles[wgtile.tileIndex]=wgtile; // put wgTile into master list
				int []itsflower=p.kData[baryV].flower;
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
				baryV=p.kData[bv].flower[4*j+3]; // its barycenter
				wgtile.tileIndex=wgtick++;
				p.setVertMark(baryV,-wgtile.tileIndex); // store neg of index in baryVert.mark
				tile.wgIndices[2*j+1]=wgtile.tileIndex; // point to wgTile index
				p.tileData.wgTiles[wgtile.tileIndex]=wgtile; // put wgTile into master list
				itsflower=p.kData[baryV].flower;
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
					int num=p.getNum(v);
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
						int []augvert=new int[num+4];
						int []vert=new int[num/4+1];
						dtile.wgIndices=new int[num/2];
						dtile.baryVert=v;
						
						int tick=0; // counts wgTile's
						// first: edge from bdry edge barycenter to first tile barycenter
						int cv=flower[1];
						int myindx=p.nghb(cv,v);
						vert[tick]=augvert[2*tick]=p.kData[cv].flower[(myindx+2)%6];
						augvert[2*tick+1]=p.kData[cv].flower[(myindx+3)%6];
						dtile.wgIndices[tick++]= -p.getVertMark(cv); // index is temp stored in 'mark'
						
						// then pairs of grey/white to get edges between tile barycenters
						for (int i=1;i<(num/4);i++) {
							cv=flower[4*i-1];
							myindx=p.nghb(cv,v);
							augvert[2*tick]=p.kData[cv].flower[(myindx+2)%6]; 
							augvert[2*tick+1]=p.kData[cv].flower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
							cv=flower[4*i+1];
							myindx=p.nghb(cv,v);
							vert[tick/2]=augvert[2*tick]=p.kData[cv].flower[(myindx+2)%6];
							augvert[2*tick+1]=p.kData[cv].flower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
						}							
						
						// then finish
						cv=flower[num-1];
						myindx=p.nghb(cv,v);
						augvert[2*tick]=p.kData[cv].flower[(myindx+2)%6]; 
						augvert[2*tick+1]=p.kData[cv].flower[(myindx+3)%6];
						dtile.wgIndices[tick++]= -p.getVertMark(cv);
						vert[tick/2]=augvert[2*tick]=p.kData[cv].flower[(myindx+4)%6]; // last vert is edge barycenter
						
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
							int cv=p.kData[v].flower[j];
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
							vert[tick/2]=augvert[2*tick]=p.kData[cv].flower[(myindx+2)%6];
							augvert[2*tick+1]=p.kData[cv].flower[(myindx+3)%6];
							dtile.wgIndices[tick++]= -p.getVertMark(cv);
							cv=flower[(ii+2)%num];
							myindx=p.nghb(cv,v);
							augvert[2*tick]=p.kData[cv].flower[(myindx+2)%6];
							augvert[2*tick+1]=p.kData[cv].flower[(myindx+3)%6];
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
						
						// these go at end
						augvert[6]=p.kData[cv].flower[myindx];
						augvert[7]=p.kData[cv].flower[(myindx+1)%6];
						
						vert[0]=augvert[0]=p.kData[cv].flower[(myindx+2)%6];
						augvert[1]=p.kData[cv].flower[(myindx+3)%6];
						vert[1]=augvert[2]=p.kData[cv].flower[(myindx+4)%6];
						qtile.wgIndices[0]= -p.getVertMark(cv);
						
						// next is white
						cv=flower[3];
						myindx=p.nghb(cv,v);
						augvert[3]=p.kData[cv].flower[(myindx+3)%6];
						vert[2]=augvert[4]=p.kData[cv].flower[(myindx+4)%6];
						augvert[5]=p.kData[cv].flower[(myindx+5)%6];
						qtile.wgIndices[1]= -p.getVertMark(cv);
						vert[3]=v;

						p.tileData.quadTileData.myTiles[qcount].vert=vert;
						p.tileData.quadTileData.myTiles[qcount].augVertCount=8;
						p.tileData.quadTileData.myTiles[qcount].augVert=augvert;
					}
					
					else { // interior? num=4
						qtile.wgIndices=new int[4];
						int num=p.getNum(v);

						// find a grey tile barycenter to start
						int gdir=-1;
						for (int j=0;(j<num && gdir<0);j++) {
							int cv=p.kData[v].flower[j];
							int mark=p.getVertMark(cv);
							if (mark<0 && p.tileData.wgTiles[-mark].mark==-1) // grey
								gdir=j;
						}
						if (gdir<0)
							throw new CombException("quad tiling has no grey tile next to "+v);
						
						for (int j=0;j<4;j++) {
							int cv=flower[(gdir+2*j)%num];
							int myindx=p.nghb(cv,v);
							vert[j]=augvert[2*j]=p.kData[cv].flower[(myindx+2)%6];
							augvert[2*j+1]=p.kData[cv].flower[(myindx+3)%6];
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
	 * After doing some pasting, we need to update the tile.vert[] vectors
	 * of tileData 'td'. 
	 * @param td TileData
	 * @param vmap VertexMap
	 */
	public void updateTileVerts(TileData td,VertexMap vmap) {
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			if (tile==null)
				break;
			
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
	 * Look through masterPack for tile edges which are in the boundary,
	 * contiguous, and are to be attached to one another. Sew up the slit 
	 * and adjust accordingly. (So, we're looking for a tile vert which is
	 * in the bdry, and tile edges on each side are to be identified according
	 * to origTD.)
	 * @return boolean, false if none sewn up
	 */
	public boolean sewOneSlit() {
		
		Tile tile=null;
		Tile otile=null;

		for (int t=1;t<=growTD.tileCount;t++) {
			tile=growTD.myTiles[t];
			otile=null;
		
			if (tile!=null) { // this should imply corresponding origTD tile is not null
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

					if (tileAdded[nghb]>0 && v ==growTD.myTiles[nghb].vert[oe]) {

// debug
//System.out.println(" attach slit: (tile,edge)=("+otile.tileFlower[e][0]+" "+otile.tileFlower[e][1]+")");

						int rslt = masterPack.adjoin(masterPack, v, v, 4);
						if (rslt != 1)
							throw new ParserException("didn't sew slit correctly, tile "+ tile.tileIndex);
						masterPack.complex_count(false);
						
						// pasted to self, so fix the tile data
						updateTileVerts(growTD, masterPack.vertexMap);
						
						return true;
					}
				}	
			}
		}
		
		return false;
	}

	/**
	 * Look through masterPack for tile edges which are in the boundary
	 * and can swallow an unattached tile. Adjoin and adjust accordingly. 
	 * (So, looking for edge having same vert at each end in origTD.)
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
							if (masterPack.adjoin(newp, v,w,4) != 1)
								throw new ParserException("didn't adjoin filled loop correctly");
							// fix the new tile data
							updateTileVerts(newp.tileData,masterPack.vertexMap);
							growTD.myTiles[t] = newp.tileData.myTiles[1];
							if (copyFlowers(origTD.myTiles[t],growTD.myTiles[t])<=0)
								throw new ParserException("failed in loop work to copy tileFlower");
							growTD.myTiles[t].tileIndex = 1;
							tileAdded[t] = 1;
						}
						else { // must be done in two steps:
							//   (1) adjoin along 3 edges only: update tile verts, add to growTD, transfer flowers, etc.
							//   (2) then zip last edge: update growTD
							int tip=newp.tileData.myTiles[1].augVert[3]; // will become tip of final slit
							if (masterPack.adjoin(newp, v,w,3) != 1)
								throw new ParserException("didn't adjoin a loop correctly");
							tip=masterPack.vertexMap.findW(tip);

							// fix the new tile data
							updateTileVerts(newp.tileData,masterPack.vertexMap);
							growTD.myTiles[t] = newp.tileData.myTiles[1];
							if (copyFlowers(origTD.myTiles[t],growTD.myTiles[t])<=0)
								throw new ParserException("failed in loop work to copy tileFlower");
							growTD.myTiles[t].tileIndex = 1;
							tileAdded[t] = 1;
							
							if (masterPack.adjoin(masterPack,tip,tip,1)!=1)
								throw new ParserException("didn't get last edge of adjoin on loop");
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
	 * Given a tile and edge of origTD, determine if the ends of the edge are 
	 * the same vertex.
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
	 * Both t1 and t2 must have 'tileFlower's. (This follows 
	 * routine follows the orientation convention of 'adjoin' 
	 * operations, adjoining t2 to t1.)
	 *   
	 * Note that t1 and t2 may be the same tile, but we can handle
	 * only the limited case of pasting associated with a single slit.
	 * 
	 * TODO: we are not ready for cases of ambiguity, which may
	 * have to be resolved using 'tData' and topological 
	 * considerations. E.g. t1=t2 and sharing non-contiguous
	 * edges, t1 and t2 hitting multiple times, etc.
	 * 
	 * If problems come up, throw 'DataException' or return null.
	 * 
	 * @param tData TileData
	 * @param t1 Tile
	 * @param t2 Tile
	 * @return int[4], null if t1/t2 don't share any edges,
	 *         negative entries in problem cases. Null if 'tileFlower'
	 *         info is not set.
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
	 * This routine creates tile vertices based entirely on tileFlowers. 
	 * In special cases, such as existence of uniqons, digons, slits, 
	 * or self-pastings (e.g., perhaps non-simply connected surfaces), 
	 * we must specify the tiling using 'tileFlower' information, as when 
	 * it was read from a file with data in form 'TILEFLOWERS:'.
	 * Return nodecount of vertex indices, -1 on failure (e.g., if 
	 * 'tileflowers' are not set);

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
			td.myTiles[t].vert=new int[tile.vertCount]; // throw out old indices
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

							// here's the goal: set correct index in
							// nghbtile.vert
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

								// here's the goal: set correct index in
								// nghbtile.vert
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
	
}
