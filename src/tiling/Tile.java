package tiling;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.RawDCEL;
import dcel.RedHEdge;
import exceptions.CombException;
import exceptions.DataException;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;

/**
 * A "tile" is a combinatorial polygon with 'vertCount' vertices; the
 * class extends 'Face', so has additional information, such as color,
 * tile type, mark, etc. 'vert' is the counterclockwise oriented list of 
 * corner vertices; the first, 'vert[0]' is known as the 'principal'
 * vertex. 
 * 
 * The vertex list may have been extended to support drawing operations,
 * hence the 'augVert' list: this may be null, otherwise is used to avoid
 * combinatorial ambiguities in tracing the border after refinements. 
 * Vertices from subsequent 'hex_refine' operations are NOT added to these
 * lists, since they will be followed as hex-extended edges. 
 * 
 * Each tile gets a 'baryVert', the barycenter of the combinatorial face.
 * Note, however, that this is not saved when writing tilings to files,
 * so the information may not be set correctly when a tiling is read in
 * for an existing packing. (Consider using 'pave' command.)
 * 
 * A tile is identified by 'tileIndex' (starting with 1). 'vert' is 
 * list of its vertices (generally legitimate circle indices in a 
 * packing P), overriding the old limit of three vertices in 
 * 'Face.vert[]'. Its tile neighbors in ccw order are listed in 
 * 'tileFlower'; jth entry is index of tile across edge 
 * (vert[j],vert[j+1]) (though vert[j] and vert[j+1] may not be 
 * neighbors in P any longer). If no tile across that edge, entry is 0. 
 * Note: same neighbor may be listed more than once if tiles share more 
 * than one edge, perhaps even non-contiguous (which may cause problems).
 * 
 * As the associated 'dual' and 'quad' tilings are generated, we build
 * white/grey faces, a pair for each edge. The white/grey ('TileData.wgTiles')
 * are associated (a la dessins) with triples {0,1,infty} of vertices (0), 
 * midpoints of original polygon edges (1), and baryVert (infty).
 * 
 * There may be several stages of tilings, typically from multiple 
 * stages of subdivision. The 'TileData' to which this tile belongs 
 * is saved and if this tile has been subdivided, pointer 
 * 'myTileData' gives its 'TileData' object.
 * 
 * @author kstephe2
 */
public class Tile extends Face {
	
	public int tileIndex;	    // index generally independent of 
								//   associated packings 
	public int tileType;		// -1=unset: give the 'type', e.g., 
								//    for subdivision rules.
	public TileData TDparent; // 'TileData' structure to which this belongs
	public int baryVert;       	// barycenter vertex in canonical packing
	public int[][] tileFlower;  // ccw list (t_j,e_j), where 't_j' is the 
								//   index of the tile across edge 
								//   (vert[j],vert[j+1]) (may be 0 for no 
								//   nghb or this may be this same tile). 
								// In reading a tiling, the 't_j' are filled  
								//   first and may be set temporarily negative, 
								//   then the 'e_j' are determined and 
								//   consistency checked.
	
	// this tile may be a tiling itself --- as with subdivision rules
	public TileData myTileData; // null by default
	
	// for edge-tracing during displays, augment border vertex list;
	public int augVertCount; // -1 on startup
	// these are generally null by default
	public int []augVert;    // null until created: augVert[0] always 
							 //     equals vert[0]
	public int []wgIndices;  // null until created: each n-gon breaks 
							 //   into 2n wg (white/grey) 
							 //   triangular tiles stored in 'tileData.wgTiles'.
							 //   Each wg tile has 'mark' set: 
							 //   1=white, -1=grey, for orientation. vert[0]
							 //   (the 'principal' vertex) is the 0-corner,  
							 // Each wgTile is bary_refined, so its a hex flower.
	public int utilFlag;	 // utility for future use: e.g., mark specific edge
	
	// Constructors
	public Tile(PackData p,TileData tdparent,int vCount) {
		super(p,vCount);
		TDparent=tdparent;
		baryVert=-1; // depends on packing
		augVertCount=-1;
		augVert=null;
		wgIndices=null;
		myTileData=null;
		tileFlower=null;
	}

	public Tile(TileData tdparent,int vCount) {
		this(null,tdparent,vCount);
	}
	
	public Tile(int vCount) {
		this(null,null,vCount);
	}
	
	/**
	 * Create the canonical packing for this single tile in one
	 * of three modes: simple mode, mode=1, provides an n-flower
	 * for an n-sided tile; mode=2 adds a barycenter to each edge;
	 * mode=3 also barycentrically subdivides each triangle. 
	 * 
	 * In mode 3, vertex 'mark's are set to {1,2,3}: 1 = tile 
	 * baryVert (value infty); 2 = original tile corner (value 0); 
	 * 3 = edge barycenter (value 1). Hex centers of barycentrically
	 * subdivided faces are marked -1. 
	 * We attach corresponding 'TileData'. 
	 * 
	 * The 'p.vertexMap' is non-standard: 'vert' entries are changed,
	 * and <tindx,v> in 'vertexMap' shows that the tile corner 
	 * 'vert[tindx]' is now represented by the circle 'v' of the packing. 
	 * 
	 * We do NOT make any edge or vertex self-identifications, that 
	 * will be done elsewhere, if needed. Vertex indices are distinct.
	 * 
	 * @param mode int: 
	 * @return 'PackData' with new 'TileData'
	 */
	public PackData singleCanonical(int mode) {
		
		if (mode==1) { // simple tiling
			if(vertCount<3)
				throw new DataException(
						"unigons/digons not allowed in tile mode 1");
			PackData p=dcel.DcelCreation.seed(vertCount,0);
			p.tileData=new TileData(1,1);
			Tile tile=new Tile(p.tileData,vertCount);
			tile.tileType=tileType;
			tile.augVertCount=vertCount;
			tile.augVert=new int[tile.augVertCount];
			tile.baryVert=1;
			p.vertexMap=new VertexMap();
			p.setVertMark(1,1);
			for (int j=0;j<vertCount;j++) {
				int m=2+j;
				tile.vert[j]=tile.augVert[j]=m;
				p.setVertMark(m,2);
				p.vertexMap.add(new EdgeSimple(j,m));
			}
			p.tileData.myTiles[1]=tile;
			return p;
		}
		if (mode==2) { // edge barycenters added
			if(vertCount<2)
				throw new DataException("unigons not allowed in tile mode 2");
			PackData p=dcel.DcelCreation.seed(2*vertCount,0);
			p.tileData=new TileData(1,2);
			Tile tile=new Tile(p.tileData,vertCount);
			tile.tileType=tileType;
			tile.augVertCount=2*vertCount;
			tile.augVert=new int[tile.augVertCount];
			tile.baryVert=1;
			p.vertexMap=new VertexMap();
			p.setVertMark(1,1);
			for (int j=0;j<vertCount;j++) {
				int m=2+2*j;
				p.setVertMark(m,2);
				p.setVertMark(m+1,3);
				p.vertexMap.add(new EdgeSimple(j,m));
				tile.vert[j]=m;
				tile.augVert[2*j]=m;
				tile.augVert[2*j+1]=m+1;
			}	
			p.tileData.myTiles[1]=tile;
			return p;
		}

		// else mode==3, more complicated combinatorics
		
		// is this a uni-gon?
		if (vertCount==1) {
			
			// build bouquet
			int[][] bouquet=new int[10][];
			int a1[]= {6,7,8,9,6};
			bouquet[1]=a1;
			int a2[]={3,7,6,9,5}; 
			bouquet[2]=a2;
			int a3[]= {4,7,2};
			bouquet[3]= a3;
			int a4[]={5,9,8,7,3}; 
			bouquet[4]= a4;
			int a5[]={2,9,4}; 
			bouquet[5]= a5;
			int a6[]= {1,9,2,7,1}; 
			bouquet[6]=a6;
			int a7[]= {1,6,2,3,4,8,1}; 
			bouquet[7]=a7;
			int a8[]={1,7,4,9,1}; 
			bouquet[8]= a8;
			int a9[]={1,8,4,5,2,6,1}; 
			bouquet[9]= a9;
			
			PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet,1);
			PackData p=new PackData(null);
			pdcel.fixDCEL_raw(p);
			p.setGamma(2);
			p.activeNode=1;
			p.hes=0;

			p.setRadius(6,2.5/4.0);
			p.setRadius(7,2.5/6.0);
			p.setRadius(8,2.5/4.0);
			p.setRadius(9,2.5/6.0);
			p.setRadius(2,2.5/4.0);
			p.setRadius(3,2.5/4.0);
			p.setRadius(4,2.5/4.0);
			p.setRadius(5,2.5/4.0);
			p.set_aim_default();

			// mark vertices and store info
			p.setVertMark(1,1);
			p.setVertMark(2,2);
			p.setVertMark(4,3);
			p.setVertMark(7, -1); // hex refined face centers
			p.setVertMark(9, -1);
			p.vertexMap=new VertexMap();
			p.vertexMap.add(new EdgeSimple(0,2)); // vert[0] --> circle 2

			p.tileData=new TileData(1,3);
			Tile tile=new Tile(p,p.tileData,1);
			tile.tileType=tileType;
			tile.baryVert=1;
			tile.vert[0]=2;
			tile.augVertCount=4;
			tile.augVert=new int[4];
			tile.augVert[0]=2;
			tile.augVert[1]=3;
			tile.augVert[2]=4;
			tile.augVert[3]=5;
			p.tileData.myTiles[1]=tile;
			return p;
		}
		
		// general n-gon case, n>=2
		PackDCEL pdcel=CombDCEL.seed_raw(2*vertCount);
		CombDCEL.redchain_by_edge(pdcel, null, pdcel.alpha, false);
		CombDCEL.d_FillInside(pdcel);
		ArrayList<Integer> barycents=RawDCEL.hexBaryRefine_raw(pdcel, true);
		
		PackData p=new PackData(null);
		pdcel.fixDCEL_raw(p);
		
		// mark various vertices
		p.setVertMark(1,1);
		p.vertexMap=new VertexMap();
		for (int j=0;j<vertCount;j++) {
			int m=2+2*j;
			p.vertexMap.add(new EdgeSimple(j,m));
			p.setVertMark(m,2);
			p.setVertMark(m+1,3);
		}
		Iterator<Integer> bis=barycents.iterator();
		while (bis.hasNext()) 
			p.setVertMark(bis.next(),-1);
		
		p.tileData=new TileData(1,3);
		Tile tile=new Tile(p.tileData,vertCount);
		tile.tileType=tileType;
		tile.baryVert=1;
		for (int j=0;j<vertCount;j++) {
			tile.vert[j]=2+2*j;
		}
		tile.augVertCount=4*vertCount;
		tile.augVert=new int[tile.augVertCount];
		int tick=0;
		RedHEdge rtrace=p.packDCEL.redChain;
		do {
			tile.augVert[tick++]=rtrace.myEdge.origin.vertIndx;
			rtrace=rtrace.nextRed;
		} while (rtrace!=p.packDCEL.redChain);
		
		p.tileData.myTiles[1]=tile;
		return p;
	}
	
	/**
	 * Get closed linked list of border vertices: if not augmented, 
	 * just list of corner vertices.
	 * 
	 * @return NodeLink, null on error
	 */
	public NodeLink tileBorderLink() {
		NodeLink bdry=new NodeLink();
		if (augVert!=null) {
			for (int j=0;j<augVertCount;j++)
				bdry.add(augVert[j]);
			bdry.add(augVert[0]);
			return bdry;
		}
		for (int j=0;j<vertCount;j++) 
			bdry.add(vert[j]);
		bdry.add(vert[0]);
		return bdry;
	}
	
	/**
	 * Are v, w contiguous in 'vert'? Caution: does {v,w} form
	 * an edge of 'this' tile? Sending routine has to get order
	 * right. If yes, return index of the edge, else return -1; 
	 * @param v int
	 * @param w int
	 * @return int, index of edge, -1 on no match
	 */
	public int isTileEdge(int v,int w) {
		if (vert[0]==w && vert[vertCount-1]==v)
			return vertCount-1;
		for (int i=0;i<(vertCount-1);i++)
			if (vert[i]==v && vert[i+1]==w)
				return i;
		return -1;
	}
	
	/**
	 * This method depends on 'tileFlower's. Consider vert[indx]: 
	 * return |index| for the index of the tile across the 
	 * cclw edge (dir >= 0) or across the clw edge (dir < 0).
	 * Note: sometimes negative entries are stored during processing;
	 * this returns the absolute value, which should be the index.
	 * @param indx int, which edge to look at
	 * @param dir int, >= 0 then cclw; < 0 then clw 
	 * @return int, |index| of tile, 0 for "no tile", < 0 on error 
	 */
	public int nghb_tile(int indx,int dir) {
		if (indx<0)
			return -2;
		if (tileFlower==null)
			return -3;
		indx=indx%vertCount;
		if (indx==0 && dir<0)
			return Math.abs(tileFlower[vertCount-1][0]);
		if (dir<0)
			return Math.abs(tileFlower[indx-1][0]);
		return 
			Math.abs(tileFlower[indx][0]);
	}
	
	/**
	 * This method needs 'tileFlower', which lists the tiles 
	 * neighboring 'this' tile. 
	 * Find vector of contiguous cclw indices of edges shared
	 * with 't'. Each n-edge chain leads to n+1 indices, since
	 * we add in the last vertex.
	 * Return null on error, meaning 't' doesn't occur as neighbor 
	 * @param t int, tile index
	 * @return Vector<Vector<Integer>> or null on error
	 */
	public Vector<Integer> matchTileIndices(int t) {
		int hit=-1;
		int tick=0;
		
		// mark edge shared with 't'
		int []hits=new int[vertCount];
		for (int i=0;i<vertCount;i++)
			if (tileFlower[i][0]==t) {
				hits[i]=1;
				tick++;
			}
		
		// any hits?
		if (tick==0)
			return null;
		
		// find first hit
		while (hit<0 && tick<vertCount) {
			if (tileFlower[tick][0]==t)
				hit=tick;
			tick++;
		}
		if (hit<0) return null;
		
		// wrap around? check for earlier hit
		if (hit==0) {
			int bhit=hit;
			int safety=vertCount-1;
			while (tileFlower[(bhit=(hit-1+vertCount)%vertCount)][0]==t && safety>0) {
				hit=bhit;
				safety--;
			}
		}
		
		Vector<Integer> vec=new Vector<Integer>();
		int vindx=-1;
		int lasti=hit;
		for (int j=0;j<vertCount;j++) {
			vindx=(hit+j)%vertCount;
			if (tileFlower[vindx][0]==t) {
				vec.add(vindx);
				lasti=vindx;
			}
		}
		vec.add((lasti+1)%vertCount); // add the end of last edge
		return vec;
	}
	
	/**
	 * Find the minimum of the maximal depths of subdivisions.
	 * Indicate if all tiles reach the same depth. Return 
	 * n>=0 if all are the same depth n, but return -n if
	 * all reach depth n, but some go deeper.
	 * @return int
	 */
	public int checkDepth() {
		int n=0;
		
		// no 'TileData'? then depth is zero
		if (myTileData==null || myTileData.tileCount<=0)
			return n;
		boolean someneg=false;
		int min=myTileData.myTiles[1].checkDepth();
		if (min<0) 
			someneg=true;
		min=Math.abs(min);
		for (int t=2;t<=myTileData.tileCount;t++) {
			int m=myTileData.myTiles[t].checkDepth();
			if (m<0)
				someneg=true;
			m=Math.abs(m);
			if (m!=min)
				someneg=true;
			min=(m<min)?m:min;
		}
		
		// new depth is min+1
		min++;
		if (someneg)
			min *= -1;
		return min;
	}

	/**
	 * Return the cclw closed list of augmented vertices forming the boundary
	 * of this tile. If there are no augmented vertices, then just
	 * return the closed vertex list. 
	 * 
	 * @return NodeLink or null on error.
	 */
	public NodeLink getAugBdry(int indx) {
		// get first edge
		NodeLink bdrylist=findAugEdge(0);
		
		// string together the middle edges
		for (int j=1;j<(vertCount-1);j++) {
			NodeLink elist=findAugEdge(j);
			elist.remove(elist.size()-1);
			bdrylist.abutMore(elist);
		}
		
		// add in the last edge, this should close it up
		bdrylist.abutMore(findAugEdge(vertCount-1));
		
		return bdrylist;
	}
		
	/**
	 * Return the cclw list of augmented vertices associated with
	 * 'indx' edge of this tile, including first and last vert. 
	 * If the edge goes from v=vert[indx] to w=vert[indx+1], then 
	 * search 'augVert' for v and w to determine the segment of 
	 * vertices to use. If 'augVert' is not defined, just return {v,w}.
	 * 
	 * Note: no translation of vertex indices is done here,
	 * but perhaps in the calling routine.
	 * 
	 * @param indx int, index of desired edge
	 * @return NodeLink, null on error
	 */
	public NodeLink findAugEdge(int indx) {
		indx=Math.abs(indx)%vertCount;
		int v=vert[indx];
		int w=vert[(indx+1)%vertCount];
		NodeLink out=new NodeLink();
		
		// no 'augVert'?
		if (augVert==null) {
			out.add(v);
			out.add(w);
			return out;
		}
		
		// find first occurrence of 'v'
		int indV=-1;
		for (int j=0;(j<augVertCount && indV<0);j++)
			if (augVert[j]==v)
				indV=j;
		// start there and find first occurrence of 'w' (even if equal to v)
		int indW=-1;
		for (int j=indV+1;(j<augVertCount && indW<0);j++)
			if (augVert[j]==w)
				indW=j;
		
		// might wrap to the end, but shouldn't have to look further
		if (indW<0 && augVert[0]==w)
			indW=augVertCount;

		if (indV<0 || indW<0)
			throw new CombException("didn't find augmented edge from "+v+" to "+w);
		
		// add to list 
		for (int j=indV;j<indW;j++)
			out.add(augVert[j]);
		
		// add end vertex
		if (indW==augVertCount)
			indW=0;
		out.add(augVert[indW]);
		
		return out;
	}

    /** 
     * Build new 'Tile' object whose data duplicates this. However, 'myTileData' 
     * is set to null and, if needed, must be copied separately and attached.
     * (This prevents recursion).
     * @param tdparent TileData; set the parent TileData
     * @return new Tile
     */
    public Tile clone(TileData tdparent) {
    	Tile stile=new Tile(tdparent,vertCount);
   	 	stile.tileIndex=tileIndex;
   	 	stile.tileType=tileType;
   	 	stile.baryVert=baryVert;
   	 	stile.color=new Color(color.getRed(),color.getGreen(),color.getBlue());
   	 	stile.indexFlag=indexFlag;
   	 	stile.mark=mark;
   	 	stile.nextFace=nextFace;
   	 	stile.nextRed=nextRed;
   	 	stile.plotFlag=plotFlag;
   	 	stile.rwbFlag=rwbFlag;
		stile.vert=new int[vertCount];
		for (int j=0;j<vertCount;j++) {
			stile.vert[j]=vert[j];
		}
		stile.tileFlower=null;
		if (tileFlower!=null) {
			stile.tileFlower=new int[vertCount][2];
			for (int j=0;j<vertCount;j++) {
				stile.tileFlower[j][0]=tileFlower[j][0];
				stile.tileFlower[j][1]=tileFlower[j][1];
			}
		}
		if (augVert!=null) { // have augmented vertices
			stile.augVertCount=augVertCount;
			stile.augVert=new int[augVertCount];
			for (int j=0;j<augVertCount;j++) {
				stile.augVert[j]=augVert[j];
			}
		}
		if (wgIndices!=null) { // these are just indices into tileData.wgTiles
			int nb=wgIndices.length;
			stile.wgIndices=new int[nb];
			for (int j=0;j<nb;j++) 
				stile.wgIndices[j]=wgIndices[j];
		}
		
		stile.myTileData=null;
		return stile;
    }
    
    /**
     * Recursively translate 'vert' and 'augVert' arrays and 'baryVert'
     * according to 'vmap', which lists <old,new>.
     * @param vmap VertexMap
     * @return int 'vertCount', 0 if vmap is null
     */
    public int updateMyVerts(VertexMap vmap) {
    	if (vmap==null)
    		return 0;
    	for (int j=0;j<augVertCount;j++) {
    		int w=vmap.findW(augVert[j]);
    		if (w!=0)
    			augVert[j]=w;
    	}
    	for (int j=0;j<vertCount;j++) {
    		int w=vmap.findW(vert[j]);
    		if (w!=0)
    			vert[j]=w;
    	}
    	if (baryVert>0) {
    		int w=vmap.findW(baryVert);
    		if (w!=0)
    			baryVert=w;
    	}
    	if (myTileData!=null) {
    		for (int t=1;t<=myTileData.tileCount;t++)
    			myTileData.myTiles[t].updateMyVerts(vmap);
    	}
    	return vertCount;
    }
    
    /**
     * For debugging, call this from eclipse to see 'vert' and 'augVert' vertices.
     * @return int, count
     */
    public int debugPrint() {
    	int count=0;
    	StringBuilder strbld=new StringBuilder("tile: "+tileIndex+", baryVert "+baryVert+"\nvert: ");
    	for (int j=0;j<vertCount;j++) {
    		strbld.append(" "+vert[j]);
    		count++;
    	}
    	strbld.append("\naugVert: ");
    	for (int j=0;j<augVertCount;j++) {
    		strbld.append(" "+augVert[j]);
    	}
    	
    	System.out.println(strbld.toString());
    	return count;
    }
    
    /**
     * Given lists int[][2] 't' and 'tn', return vector of 
     * EdgeSimple's (myindex,nghbindex): entry 'myindex' of 't' 
     * is matched to entry 'nghbindex' of 'nt'.  
     * 
     * Ambiguity: how to find legitimate matches?
     * * Generally 't' and 'nt' match in a single 'edge'
     * * or a single chain of edges (contiguous and same length in both)
     * * or multiple chains which can be aligned due to counts
     * * The entries t[.][2] (default = -2) may tell what 'nghbindex'
     *   should be; we'll use and check that.
     * 
     * If there aren't errors, we might guess to resolve ambiguity, but 
     * it's best if the user provides the expected 'nghbindex'.
     * 
     * Errors lead to Exceptions (hopefully helpful ones). 
     * 
     * Note:
     * * 't' and 'nt' first components may be same (e.g. from same tile)
     * * matches may be contiguous in one list, but non-contiguous in
     *   the other
     * * whole list may be a match -- e.g. tile complementing a tree
     *   
     * TODO: I'm getting carried away with possibilities; this code will need
     *       more work for non-routine situations -- e.g. on surfaces,
     *       self-pastings, non-contiguous matching, etc.
     *       
     * @param t int[][2]
     * @param tindx int, index (e.g. tile index) associated with 't' list
     * @param nt int[][2]
     * @param ntindx int, index (e.g. tile index) associated with 'nt' list
     * @return Vector<int[][]>, null if none found, exceptions on error
     */
    public static Vector<EdgeSimple> tile2tileMatch(int [][]t,int tindx,int [][]nt,int ntindx) {
    	
    	// check hints for minimal consistency
    	boolean hints=false; 

    	int tnt=0;
    	int tfirstmiss=-1;
    	for(int j=0;j<t.length;j++) {
    		if (t[j][0]==ntindx) {
    			tnt++;
    			int dx;
    			if ((dx=t[j][1])>=0) {
    				if (!hints)
    					hints=true; // there's a matching hint
    				if (dx>=nt.length || nt[t[j][1]][0]!=tindx ||
    						(nt[dx][1]>=0 && nt[dx][1]!=j))
    					throw new DataException("inconsistency between entries and hint "+dx);
    				
    				// can set t[j][1] based on index given in nt
    				if (t[j][1]<0 && nt[dx][1]==j) 
    					t[j][1]=dx;
    			}
    		}
    		else if (tfirstmiss<0) // find first miss
				tfirstmiss=j;

    	}
    	
    	int ntt=0;
    	int ntfirstmiss=-1;
    	for(int j=0;j<nt.length;j++) {
    		if (nt[j][0]==tindx) {
    			ntt++;
    			int dx;
    			if ((dx=nt[j][1])>=0) {
    				if (!hints)
    					hints=true; // there's some hint for matchin
    				if (dx>=t.length || t[nt[j][1]][0]!=ntindx ||
    						(t[dx][1]>=0 && t[dx][1]!=j))
    					throw new DataException("inconsistency other way between entries and hints "+dx);
    				
    				// can set nt[j][1] based on index given in t
    				if (nt[j][1]<0 && t[dx][1]==j) 
    					nt[j][1]=dx;
    			}
    		}
    		else if (ntfirstmiss<0)
				ntfirstmiss=j;
    	}
    	
    	// inconsistency?
    	if (tnt!=ntt)
    		throw new DataException("match counts between lists are not equal");
    			
    	// no matches?
    	if (tnt==0)
    		return null;

    	// most common situation
    	if (tnt==1) {
    		int thit=-1;
    		for (int j=0;(j<t.length && thit<0);j++)
    			if (t[j][0]==ntindx)
    				thit=j;
    		int nthit=-1;
    		for (int j=0;(j<t.length && nthit<0);j++)
    			if (nt[j][0]==tindx)
    				nthit=j;
    		Vector<EdgeSimple> ans=new Vector<EdgeSimple>();
    		ans.add(new EdgeSimple(thit,nthit));
    		return ans;
    	}
    	
    	// complete string
    	if ((tnt==t.length || ntt==nt.length) && !hints) {
    		throw new DataException("all edges, but no hints to help match");
    	}
    	
    	// find 'tstarts' of starts of contiguous matches to 'nt' within 't'
    	Vector<EdgeSimple> tstarts=new Vector<EdgeSimple>();
    	for (int i=0;i<t.length;i++) {
    		if (t[i][0]==ntindx && t[(i-1+t.length)%t.length][0]!=ntindx)
    			tstarts.add(new EdgeSimple(i,0));
    	}
    	
    	// and 'ntstarts' of starts of contiguous matches to 't' within 'nt'
    	Vector<EdgeSimple> ntstarts=new Vector<EdgeSimple>();
    	for (int i=0;i<nt.length;i++) {
    		if (nt[i][0]==tindx && nt[(i-1+nt.length)%nt.length][0]!=tindx)
    			ntstarts.add(new EdgeSimple(i,0));
    	}
    	
    	// determine/store lengths of contiguous sets of matches
    	for (int k=0;k<tstarts.size();k++) {
    		int st=tstarts.get(k).v;
    		int tick=1;
    		while (t[(st+tick)%t.length][0]==ntindx)
    			tick++;
    		tstarts.get(k).w=tick;
    	}    		
    	for (int k=0;k<ntstarts.size();k++) {
    		int nst=ntstarts.get(k).v;
    		int tick=1;
    		while (nt[(nst+tick)%nt.length][0]==tindx)
    			tick++;
    		ntstarts.get(k).w=tick;
    	}    		

    	if (tstarts.size()==0 || ntstarts.size()==0) 
    		throw new DataException("screwed up one of 'tstarts' or 'ntstarts'");

    	// Now have segs for 't' and 'nt': need to match as best we can
    	Vector<EdgeSimple> ans=new Vector<EdgeSimple>();
    	
    	// most typical: single segment on each side (necessarily the same length)
    	if (tstarts.size()==1 && ntstarts.size()==1) {
    		EdgeSimple ts1=tstarts.get(0);
    		EdgeSimple nts1=ntstarts.get(0);
    		
    		// number of entries
    		int tcount=ts1.w;
    		int ntcount=nts1.w;
    		if (tcount!=ntcount || tcount<=0) 
    			throw new DataException("some inconsistency in 'tstarts' and 'ntstarts'");
    		for (int j=0;j<tcount;j++) {
    			ans.add(new EdgeSimple((ts1.v+j)%(t.length),(nts1.v+tcount-j-1+nt.length)%(nt.length)));
    		}
    		return ans;
    	}

    	// TODO: Here we need more sophisticated matching strategies; 
    	// For now, pass back pairs <j,-3> so we don't look at this matching again
    	else {
       		EdgeSimple ts1=tstarts.get(0);
    		for (int j=0;j<ts1.w;j++) {
    			ans.add(new EdgeSimple((ts1.v+j)%(t.length),-3));
    		}
    		return ans;
    	}
    	    	
    }
    
}
