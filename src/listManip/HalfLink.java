package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.SideData;
import exceptions.CombException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import packing.PackData;
import packing.QualMeasures;
import tiling.Tile;
import tiling.TileData;
import util.FaceParam;
import util.MathUtil;
import util.PathInterpolator;
import util.SelectSpec;
import util.StringUtil;
import util.UtilPacket;

/**
 * Linked list for 'HalfEdge's for DCEL structures.
 * @author kens, September 2020
 *
 */
public class HalfLink extends LinkedList<HalfEdge> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	PackDCEL pdc;
	
	static final int XTD_LINKS=16; // link count limit in 'extended' edges.

	// Constructors
	public HalfLink(PackData p,HalfEdge edge) {
		super();
		packData=p;
		pdc=packData.packDCEL;
		add(edge);
	}

	/**
	 * @param p @see PackData
	 * @param datastr String
	 * @param xtd, boolean, yes for extended
	 */
	public HalfLink(PackData p,String datastr,boolean xtd) {
		super();
		packData=p;
		pdc=packData.packDCEL;
		if (datastr!=null && datastr.length()>0) 
			addHalfLink(datastr,xtd);
	}
	
	/**
	 * not necessarily extended edges
	 * @param p PackData
	 * @param datastr String
	 */
	public HalfLink(PackData p,String datastr) {
		this(p,datastr,false);
	}
	
	/**
	 * Allow extended edges
	 * @param p
	 * @param items
	 * @param xtd, boolean, yes for extended
	 */
	public HalfLink(PackData p,Vector<String> items,boolean xtd) {
		super();
		packData=p;
		pdc=p.packDCEL; 
		if (items==null || items.get(0).length()==0) { // default to 'a' (all edges)
			items=new Vector<String>(1);
			items.add("a");
		}
		addHalfLink(items,xtd);
	}

	/**
	 * not necessarily extended edges
	 * @param p PackData
	 * @param items Vector<String>
	 */
	public HalfLink(PackData p,Vector<String> items) {
		this(p,items,false);
	}

	/**
	 * Convert 'EdgeLink' to (legal) 'HalfLink'.
	 * @param elink
	 */
	public HalfLink(EdgeLink elink) {
		EdgeSimple edge=null;
		HalfEdge he=null;
		Iterator<EdgeSimple> elst=elink.iterator();
		while (elst.hasNext()) {
			edge=elst.next();
			if ((he=packData.packDCEL.findHalfEdge(edge))!=null)
				add(he);
		}
	}

	/**
	 * Convert 'FaceLink' to 'HalfLink'.
	 * @param p
	 * @param flink
	 */
	public HalfLink(PackData p,FaceLink flink) {
		Iterator<Integer> flst=flink.iterator();
		while (flst.hasNext()) {
			DcelFace face=p.packDCEL.faces[flst.next()];
			add(face.edge);
		}
	}
	
	/**
	 * Return closed redChain comprising the augmented
	 * edges of the given tile, starting with the 
	 * given tile vertex.
	 * @param p PackData
	 * @param tdata TileData
	 * @param tileindx int
	 * @param vertindx int
	 * @return RedEdge
	 */
	public static RedEdge tileAugChain(PackData p,
			TileData tdata,int tileindx,int vertindx) {
		Tile tile=tdata.myTiles[tileindx]; 
//		deBugging.DCELdebug.augVerts2Vlist(tile);
		int aindx=-1;
		int v=tile.vert[vertindx]; // starting vertex
		int[] newlist=new int[tile.augVertCount];
		for (int j=0;(j<tile.augVertCount && aindx<0);j++) 
			if (tile.augVert[j]==v)
				aindx=j;
		NodeLink nlink=new NodeLink(p);
		for (int j=0;j<tile.augVertCount;j++)
			nlink.add(tile.augVert[(j+aindx)%tile.augVertCount]);
		nlink.add(v); // to close up
		HalfLink hlink=verts2edges(p.packDCEL,nlink,false);
		
		Iterator<HalfEdge> hlt=hlink.iterator();
		RedEdge newRed=new RedEdge(hlt.next());
		newRed.myEdge.myRedEdge=newRed;
		RedEdge rtrace=newRed;
		RedEdge ntrace=null;
		while (hlt.hasNext()) {
			HalfEdge nhe=hlt.next();
			ntrace=new RedEdge(nhe);
			ntrace.myEdge.myRedEdge=ntrace;
			ntrace.prevRed=rtrace;
			rtrace.nextRed=ntrace;
			rtrace=ntrace;
		}
		rtrace.nextRed=newRed;
		newRed.prevRed=rtrace;
		return newRed;
	}
	
	/**
	 * Chain of successive 'HalfEdge's extracted from 'NodeLink'.
	 * Check if successive entries form halfedge's; stop when 
	 * there is a break.
	 * @param pdcel PackDCEL
	 * @param vlist NodeLink
	 * @return HalfLink, may be empty
	 */
	public static HalfLink getChain(PackDCEL pdcel,NodeLink vlist) {
		HalfLink hlink=new HalfLink();
		if (vlist==null || pdcel==null) 
			return hlink;
		Iterator<Integer> vst=vlist.iterator();
		int v=vst.next();
		int w=v;
		while (vst.hasNext()) {
			v=w;
			w=vst.next();
			HalfEdge he=pdcel.findHalfEdge(new EdgeSimple(v,w));
			if (he==null)
				return hlink;
			hlink.add(he);
		} // end of while
		return hlink;
	}
	
	/**
	 * Not associated with any PackData
	 * @param datastr
	 */
	public HalfLink(String datastr) {
		this(null,datastr);
	}
	
	/**
	 * empty list, no packing
	 */
	public HalfLink() {
		super();
		packData=null;
		pdc=null;
	}

	/**
	 * Initiate empty list
	 * @param p
	 */
	public HalfLink(PackData p) {
		this(p,(String)null);
	}
	
	/** 
	 * Enforce legality of vertex indices if 'packData' is not null. 'edge.v' 
	 * and 'edge.w' must be positive.
	 * @param edge EdgeSimple
	 * @return boolean, true if added
	 */
	public boolean add(HalfEdge edge) {
		if (edge==null)
			return false;
		if (packData==null ||
				(edge.origin.vertIndx<=packData.nodeCount &&
				edge.twin.origin.vertIndx<=packData.nodeCount))
			return super.add(edge);
		else 
			return false;
	}
	
	/**
	 * Remove occurances of <v,w> or <w,v>
	 * @param es EdgeSimple
	 * @return int count
	 */
	public int removeSimple(EdgeSimple es) {
		int count=0;
		for (int k=this.size();k>0;k--) {
			HalfEdge thisedge=this.get(k-1);
			int v=thisedge.origin.vertIndx;
			int w=thisedge.twin.origin.vertIndx;
			if ((v==es.v && w==es.w) || (v==es.w && w==es.v)) { 
				this.remove(k-1);
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Remove occurrences of 'edge', irrespective of order
	 * @param edge EdgeSimple
	 * @return int count
	 */
	public int removeEdge(HalfEdge edge) {
		int count=0;
		for (int k=this.size();k>0;k--) {
			HalfEdge thisedge=this.get(k-1);
			if (thisedge==edge) {
				this.remove(k-1);
				count++;
			}
		}
		return count;
	}
	
	public PackData getPackData() {
		return packData;
	}

	/**
	 * Add links to this list (if it is associated with PackData); 
	 * flag also permits 'extended' edges.
	 * @param datastr String
	 * @param xtd boolean, true==>allow 'extended' edges
	 * @return int count
	 */	
	public int addHalfLink(String datastr,boolean xtd) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addHalfLink(items,xtd);
	} // this.size();
	
	/**
	 * Add links to this list (if it is associated with PackData).
	 * Don't have much to do now, since we don't have string 
	 * representation of 'HalfEdge's.
	 * @param items Vector<String>
	 * @param xtd boolean, true ==> allow 'extended' edges
	 * @return int count
	 */
	public int addHalfLink(Vector<String> items,boolean xtd) {
		int count=0;
		if (packData==null) 
			return -1;
		Iterator<String> its=items.iterator();
		
		while (its!=null && its.hasNext()) {

			/* =============== here's the work ==================
	 		parse the various options based on first character */
			
/* most of these stolen from traditional, but not all yet converted. */			

			String str=(String)its.next(); 
			// it's easy to use '-' flags by mistake
			if (str.startsWith("-")) { 
				str=str.substring(1);
			}
			
			its.remove(); // shuck this entry (though still in 'str')
			
			// self call if str is a variable
			if (str.startsWith("_")) {
				count+=addHalfLink(CPBase.varControl.getValue(str),xtd);
			}
			
			// check for '?list' first
			else if (str.substring(1).startsWith("list")) {
				// three types possible, two have to convert to halfedges
				EdgeLink elink=null;
				GraphLink glink=null;
				HalfLink hlink=null;
				FaceLink flink=null;
				
				String[] b_string;
				String brst;
				
				// check for variuus face or edge lists
				if ((str.startsWith("e") && 
						(elink=packData.elist)!=null && elink.size()>0) ||
					(str.startsWith("E") && 
						(elink=CPBase.Elink)!=null && elink.size()>0) || 
					(str.startsWith("g") && 
						(glink=packData.glist)!=null && glink.size()>0) ||
					(str.startsWith("G") && 
						(glink=CPBase.Glink)!=null && glink.size()>0) ||
					(str.startsWith("h") &&
						(hlink=packData.hlist)!=null && hlink.size()>0) ||
					(str.startsWith("H") && 
						(hlink=CPBase.Hlink)!=null && hlink.size()>0) ||
					(str.startsWith("f") && 
						(flink=packData.flist)!=null && flink.size()>0) ||
					(str.startsWith("F") && 
						(flink=CPBase.Flink)!=null && flink.size()>0)) {
					
					String strdata=str.substring(5).trim(); // remove '?list'
					
					// check for parens listing range of indices, (n) or (n m) 
					EdgeSimple es=null;
					HalfEdge he=null;

					// check for parens listing range of indices
					int lsize=0;
					if (elink!=null)
						lsize=elink.size()-1;
					else if (glink!=null)
						lsize=glink.size()-1;
					else if (hlink!=null)
						lsize=hlink.size()-1;
					else if (flink!=null)
						lsize=flink.size()-1;
					int[] irange=StringUtil.get_int_range(strdata, 0,lsize);
					if (irange!=null) {
						int a=irange[0];
						int b=(irange[1]>lsize) ? lsize : irange[1]; 
						for (int j=a;j<=b;j++) {
							if (elink!=null) 
								es=elink.get(j);
							else if (glink!=null)
								es=glink.get(j);
							else if (hlink!=null)
								es=new EdgeSimple(hlink.get(j));
							if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
								add(he);
								count++;
							}
						}
					}
					
					// else check for brackets
					else if ((b_string=StringUtil.get_bracket_strings(strdata))!=null 
							&& (brst=b_string[0])!=null) {
						
						// 'r' means rotate: first of 'this' is moved to the end, 
						//     everyone bumps down
						if (brst.startsWith("r")) { 
							if (elink!=null)
								elink.add(elink.getFirst());
							else if (glink!=null)
								glink.add(glink.getFirst());
							else
								hlink.add(hlink.getFirst());
						}
						if (brst.startsWith("r") || brst.startsWith("n")) { // add and use up first
							if (elink!=null) 
								es=elink.removeFirst();
							else if (glink!=null)
								es=glink.removeFirst();
							else if (hlink!=null)
								es=new EdgeSimple(hlink.removeFirst());
							if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
								add(he);
								count++;
							}
						}
						if (brst.startsWith("l")) { // get last
							if (elink!=null) 
								es=elink.getLast();
							else if (glink!=null)
								es=glink.getLast();
							else if (hlink!=null)
								es=new EdgeSimple(hlink.getLast());
							if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
								add(he);
								count++;
							}
						}						
						else { // else specified index
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0) {
									if (elink!=null && n<elink.size()) 
										es=elink.get(n);
									else if (glink!=null && n<glink.size())
										es=glink.get(n);
									else if (hlink!=null && n<hlink.size())
										es=new EdgeSimple(hlink.get(n));
									if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
										add(he);
										count++;
									}
								}
							} catch (NumberFormatException nfe) {}
						}
					}				

					// else just add all from the input list
					else {
						if (elink!=null) {
							Iterator<EdgeSimple> elst=elink.iterator();
							while (elst.hasNext()) {
								es=(EdgeSimple)elst.next();
								if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
									add(he);
									count++;
								}
							}
						}
						else if (glink!=null) {
							Iterator<EdgeSimple> elst=glink.iterator();
							while (elst.hasNext()) {
								es=(EdgeSimple)elst.next();
								if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
									add(he);
									count++;
								}
							}
						}
						else if (hlink!=null) {
							Iterator<HalfEdge> hlst=hlink.iterator();
							while (hlst.hasNext()) {
								es=new EdgeSimple(hlst.next());
								if ((he=packData.packDCEL.findHalfEdge(es))!=null) {
									add(he);
									count++;
								}
							}
						}
						else if (flink!=null) {
							Iterator<Integer> flst=flink.iterator();
							while (flst.hasNext()) {
								int f=flst.next();
								if (f>=0 && f<=packData.packDCEL.faceCount) {
									add(packData.packDCEL.faces[f].edge);
									count++;
								}
							}
						}
					}
					
				} // end of handling various edge lists
				
				else // there was no valid list
					return count;
			} // end of "?list" search
			
			// ************* sort by first character ************* 
			 
			else {
			switch(str.charAt(0)) {
			
			// "all" includes all red edges (whether 
			//    twinned or not) and one of each pair of 
			//    edges, namely, the edge <v,w> with v<w.
			case 'a': 
			{
				// organize via vertices -- more rational order?
				for (int v=1;v<=pdc.vertCount;v++) {
					Vertex vert=pdc.vertices[v];
					HalfLink hlink=vert.getSpokes(null);
					Iterator<HalfEdge> hits=hlink.iterator();
					while (hits.hasNext()) {
						HalfEdge he=hits.next();
						if (he.myRedEdge!=null) {
							add(he);
							count++;
						}
						else if (he.twin.origin.vertIndx>v) {
							add(he);
							count++;
						}
					}
				}
				break;
			}
			
			// bdry; check for braces (a,b)
			case 'b':
			{
				HalfEdge firsthe=null;
				HalfEdge lasthe=null;
				String []pair_str=StringUtil.get_paren_range(str); // two strings
				if (pair_str!=null && pair_str.length==2) { // got two strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))==0
						|| (b=NodeLink.grab_one_vert(packData,pair_str[1]))==0
						|| !packData.isBdry(a) || !packData.isBdry(b))
						return count;
					firsthe=pdc.vertices[a].halfedge;
					lasthe=pdc.vertices[b].halfedge.twin.next.twin;
					
					// check if first/last are on same boundary component
					if (firsthe.twin.face!=lasthe.twin.face)
						return count;
					HalfEdge nexthe=firsthe;
					do {
						add(nexthe);
						count++;
						nexthe=nexthe.twin.prev.twin; // go cclw
					} while (nexthe!=lasthe.twin.prev.twin);
				}
				else { 
					for (int i=1;i<=packData.getBdryCompCount();i++) {
						combinatorics.komplex.DcelFace iface=pdc.idealFaces[i];
						HalfLink hlink=iface.getEdges();
						if (hlink!=null) {
							count+=hlink.size();
							this.abutMore(hlink);
						}
					}
				}
				break;
			}
			case 'r': // random edge
			{
				boolean interior=false;
				if (str.length()>1 && str.charAt(1)=='i')
					interior=true;
				NodeLink nlk=null;
				if (interior)
					nlk=new NodeLink(packData,"i");
				else 
					nlk=new NodeLink(packData,"a");
				Vertex vert=packData.packDCEL.vertices[NodeLink.randVert(nlk)];
				int m=vert.getNum();
				if (vert.isBdry())
					m++;
		    	int n=new Random().nextInt(m); // in (0,m-1)
		    	HalfEdge he=vert.halfedge;
		    	int tick=0;
		    	while (tick!=n) {
		    		tick++;
		    		he=he.prev.twin;
		    	}
		    	add(he);
		    	count++;
				break;
			}
			case 'F': // 'layoutOrder
			{
				count+=abutMore(packData.packDCEL.layoutOrder);
				break;
			}
			case 'g': // TODO: combinatorial geodesic path from v to w
			{
				break;
			}
			case 'i': // interior (at least one end interior)
			{
				for (int v=1;v<=pdc.vertCount;v++) {
					Vertex vert=pdc.vertices[v];
					if (vert.isBdry())
						continue;
					HalfLink hlink=vert.getSpokes(null);
					Iterator<HalfEdge> hits=hlink.iterator();
					while (hits.hasNext()) {
						HalfEdge he=hits.next();
						if (he.myRedEdge!=null) { // red interior
							add(he);
							count++;
						}
						else {
							Vertex wert=he.twin.origin;
							if (wert.isBdry()) { // other end is bdry
								add(he);
								count++;
							}
							else if (wert.vertIndx>v) {
								add(he);
								count++;
							}
						}
					}
				}
				break;
			}
			case 'L': // 'layoutOrder' for the packing
			{
				count +=this.abutMore(packData.packDCEL.layoutOrder);
				break;
			}
			case 'R': // designated "sides" of redChain; absorb rest of 'items'
			{
			  int numSides=-1;
			  String itstr=null;
			  if (packData.packDCEL.pairLink==null || 
					  (numSides=packData.packDCEL.pairLink.size())==0) {
				  while (its.hasNext()) itstr=(String)its.next(); // toss rest 
				  break;
			  }
			  boolean []tag=new boolean[numSides];
			  for (int i=0;i<numSides;i++) tag[i]=false;
			  if (!its.hasNext()) { // default to 'all'
				  for (int i=0;i<numSides;i++) 
					  tag[i]=true;
			  }
			  else do {
				  itstr=(String)its.next();
				  if (itstr.startsWith("a"))
					  for (int i=0;i<numSides;i++) tag[i]=true;
				  else {
					  try {
						  int n=Integer.parseInt(itstr);
						  if (n>=0 && n<numSides) tag[n]=true;
					  } catch (NumberFormatException nfx) {}
				  }
			  } while (its.hasNext()); // eating rest of 'items'

			  // now to traverse the 'RedEdge's in chosen segments
			  Iterator<SideData> sp=packData.packDCEL.pairLink.iterator();
			  SideData ep=sp.next(); // first is null
			  RedEdge rlst=null;
			  int tick=0;
			  while (sp.hasNext()) {
				  ep=(SideData)sp.next();
				  if (tag[tick++]) { // yes, do this one
					  rlst=ep.startEdge;
					  add(ep.startEdge.myEdge);
					  count++;
					  while (rlst!=ep.endEdge) {
						  rlst=rlst.nextRed;
						  add(rlst.myEdge);
						  count++;
					  } 
				  }
			  }
			  break;
			} // end of 'R'
			case 'm': // marked (or 'mv' both ends marked)
			{
				boolean not_m=false; // want those not marked?
				boolean byEnds=false; // with end vertices both marked?
				// 'mc'? want those NOT marked?
				if (str.length()>1) {
					if (str.contains("c")) 
						not_m=true;
					if (str.contains("v"))
						byEnds=true;
				}
				
				for (int e=1;e<=packData.packDCEL.edgeCount;e++) {
					if (byEnds) {
						HalfEdge he=packData.packDCEL.edges[e];
						int v=he.origin.vertIndx;
						int w=he.next.origin.vertIndx;
						if (packData.getVertMark(v)!=0 &&
								packData.getVertMark(w)!=0) {
							if (!not_m) { 
								add(packData.packDCEL.edges[e]);
							}
						}
						else if (not_m) {
							add(packData.packDCEL.edges[e]);
						}
					}
					else {
						int m=packData.packDCEL.edges[e].mark;
						if ((not_m && m==0) || (!not_m && m!=0)) {
							add(packData.packDCEL.edges[e]);
						}
					}
				}
				break;
			}
			case 'o': // edges having non-trivial inv dist
			{
				if (!packData.haveInvDistances()) 
					break; 
				for (int v=1;v<=packData.nodeCount;v++) {
					HalfLink spokes=packData.packDCEL.vertices[v].getSpokes(null);
					Iterator<HalfEdge> sis=spokes.iterator();
					while (sis.hasNext()) {
						HalfEdge he=sis.next();
						v=he.origin.vertIndx;
						int w=he.twin.origin.vertIndx;
						double ivd=he.getInvDist();
						if (ivd!=1.0 && v<w) {
							add(he);
							count++;
						}
					}
				}
				break;
			}
			case 'e': // find edges: may be 'ee' hex-extended edges, 
				// or 'eh', hex-extrapolated, which means a closed
				// hex loop (only one edge is used in 'eh' case)
				// Move down the list and see if next vert (ignoring 
				// repeats) is neighbor. If yes, add to edge list
				// (possibly extended). Disregard duplication, eat 
				// rest of 'items'.
			{
				// if hex extrapolated
				if (str.length()>=2 && str.charAt(1)=='h') { 
					// need first vert v or first edge <v,w>
					NodeLink vlink=new NodeLink(packData,items);
					its=null; // eat rest of items
					if (vlink==null || vlink.size()==0) 
						break;
					HalfLink hex_loop=null;
					
					// may get single vertex v or pair for edge <v,w>
					int v=vlink.get(0);
					if (packData.isBdry(v) || packData.countFaces(v)!=6)
						break;
					if (vlink.size()==1) { // shortest among all directions from v
						hex_loop=CombDCEL.shootExtended(pdc.vertices[v],v,1025,true);
					}
					else {
						int w=vlink.get(1);
						HalfEdge he=pdc.findHalfEdge(new EdgeSimple(v,w));
						// w must also be interior and hex
						if (packData.isBdry(w) || packData.countFaces(w)!=6)
							break;
						hex_loop=CombDCEL.axisExtended(he,v,1025,true);
					}
					if (hex_loop==null || hex_loop.size()==0) 
						break;
					count+=hex_loop.size();
					abutMore(hex_loop);
				}
				else {
					boolean extended=false;
					if (str.length()>=2 && str.charAt(1)=='e') 
						extended=true;
					NodeLink vertlist=new NodeLink(packData,items);
					if (vertlist==null || vertlist.size()==0) 
						break;
					its=null; // eat rest of 'items'
					count+=abutMore(CombDCEL.verts2Edges(
							packData.packDCEL,vertlist,extended));
				}
				break;
			}
			case 'd': // degrees of edge's quad vertices; eat rest of flagSeg's
				// Syntax: d {1} {2} [{3} [{4}]]
				//  each entry is integer, possibly proceeded by 'G' 
				//  or 'L', or '?'. '?' means no restriction on degree.
				//  missing {3} or {4} same as '?' entry.without 'L'/'G' 
				//  means "=". Conditions {1}/{2} apply
				//  to the ends of the edge, {3}/{4} apply to common
				//  neighbors without regard to which side; however,
				//  neither is '?', then edge must be interior and each 
				//  restriction must be met by one neighbor. 
			{
				// TODO: see 'EdgeLink'
				break;
			}
			case 's': // edges defining by side or by full red chain
			{
				int sideIndx=-1;
				if (its.hasNext()) {
					try {
						sideIndx=Integer.parseInt(its.next());
					} catch (Exception ex) {}
				}
				abutMore(HalfLink.HoloHalfLink(packData.packDCEL,sideIndx));
				break;
			}
			case 'I': // incident to vertices/halfedges/faces; redundancies not checked
			{
				if (str.length()<=1) break;
				switch(str.charAt(1)) {
				case 'c': // fall through to 'v'
				case 'v': // all edges from given vertices
				{
					boolean bothvw=false;
					// 'vw' means both ends must be from given vertices
					if (str.length()>2 && str.charAt(2)=='w') {
						bothvw=true;
					}
					NodeLink vertlist=new NodeLink(packData,items);
					its=null; // eat rest of items
					if (vertlist==null || vertlist.size()==0) 
						break;
					HalfLink hlk=HalfLink.getSpokes(packData.packDCEL,vertlist);
					if (hlk==null)
						break;
					if (!bothvw) {
						count+=hlk.size();
						this.abutMore(hlk);
						break;
					}

					// Searching vertlist is too slow; create temp data array.
					Iterator<Integer> vlist=vertlist.iterator();
					int []vs=new int[packData.nodeCount+1];
					while (vlist.hasNext()) 
						vs[vlist.next()]=1;

					Iterator<HalfEdge> his=hlk.iterator();
					while (his.hasNext()) {
						HalfEdge he=his.next();
						int v=he.origin.vertIndx;
						int w=he.twin.origin.vertIndx;
						if (vs[w]==1 && v<w) {
							this.add(he);
							count++;
						}
					}
					break;
				}
				case 'f': // add all edges of given faces
				{
					FaceLink facelist=new FaceLink(packData,items);
					its=null; // eat rest of items
					if (facelist==null || facelist.size()==0) break;
					Iterator<Integer> flist=facelist.iterator();
					while (flist.hasNext()) {
						combinatorics.komplex.DcelFace face=packData.packDCEL.faces[(Integer)flist.next()];
						HalfEdge fedge=face.edge;
						add(fedge);
						HalfEdge he=fedge;
						do {
							he=he.next;
							add(he);
							count++;
						} while (he!=fedge);
					}
					break;
				}
				case 'e': // share an end with given edges
				{
					EdgeLink edgelist=new EdgeLink(packData,items);
					its=null; // eat rest of items
					if (edgelist==null || edgelist.size()==0) break;
					int []vw=new int[packData.nodeCount+1];
					Iterator<EdgeSimple> elist=edgelist.iterator();
					while (elist.hasNext()) {
						EdgeSimple edge=(EdgeSimple)elist.next();
						vw[edge.v]=1;
						vw[edge.w]=1;
						for (int j=1;j<=packData.packDCEL.edgeCount;j++) {
							HalfEdge he=packData.packDCEL.edges[j];
							if (vw[he.origin.vertIndx]!=0 || vw[he.twin.origin.vertIndx]!=0)
								add(he);
							count++;
						}
					}
					break;
				}
				} // end of switch
				break;
			}
			case 'z': // closest edge to complex number z
			case 'Z': // for sphere, use actual (theta,phi)	
			{
				// TODO: see 'EdgeLink'
				break;
			}
			case 'G': // edgelist approximating the given curve (x,y),...
			{
				// TODO: see 'EdgeLink'
				break;
			}
			case 'q': // quality: edges with visual error worse that given number
			{
				double thresh=.01; // default threshold 
				try{
					thresh=StringUtil.getOneDouble(items.remove(0));
				} catch(Exception ex) {
					thresh=.01;
				}
				HalfLink elist=new HalfLink(packData,items);
				Iterator<HalfEdge> eit=elist.iterator();
				while (eit.hasNext()) {
					HalfEdge he=eit.next();
					double verr=QualMeasures.edge_vis_error(packData, he);
					if (verr>thresh) {
						add(he);
						count++;
					}
				}
				break;
			}
			default: // if nothing else, see if there is an edge (or extended edges)
			{
				int v,w;
				HalfEdge hvw=null;
				try{
					if ((v=MathUtil.MyInteger(str))>0 && its.hasNext() 
							&& (w=MathUtil.MyInteger((String)its.next()))>0) {
						if (xtd) { // allow extended edges
							HalfLink hlink=CombDCEL.shootExtended(
									packData.packDCEL.vertices[v],w,
									XTD_LINKS,true);
							if (hlink!=null && hlink.size()>0) {
								this.abutMore(hlink);
								count +=hlink.size();
							}
						}
						else if ((hvw=packData.packDCEL.
								findHalfEdge(new EdgeSimple(v, w)))!=null) {
							add(hvw);
							count++;
						}
					}
				} catch (NumberFormatException nfe) {}
				// TODO: don't know how to manage this yet.
			}
				
			} // end of switch
			} // end of else
		} // end of main 'while'
		return count;
	}
	
	public int addSimpleEdges(PackDCEL pdcel,EdgeLink elink) {
		int count=0;
		Iterator<EdgeSimple> eis=elink.iterator();
		while (eis.hasNext()) {
			add(pdcel.findHalfEdge(eis.next()));
			count++;
		}
		return count;
	}
	
	/**
	 * Make a distinct copy of this linked list, disregarding
	 * 'packData' setting.
	 * @return new HalfLink
	 */
	public HalfLink makeCopy() {
		Iterator<HalfEdge> elist=this.iterator();
		HalfLink newlist=new HalfLink();
		while (elist.hasNext()) {
			newlist.add(new HalfEdge(elist.next()));
		}
		return newlist;
	}
	
	/**
	 * Abut another 'HalfLink' to the end of this one.
	 * @param moreEL
	 * @return count of new edges (some may be improper, some redundant)
	 */
	public int abutMore(HalfLink moreEL) {
		if (moreEL==null || moreEL.size()==0)
			return 0;
		int ticks=0;
		Iterator<HalfEdge> mit=moreEL.iterator();
		HalfEdge edge=null;
		while (mit.hasNext()) {
			edge=mit.next();
			add(edge);
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Given v, return first edge starting at v.
	 * Return -1 if not found.
	 * @param v int
	 * @return edge starting at v or null
	 */
	public HalfEdge findW(int v) {
		Iterator<HalfEdge> ed=this.iterator();
		HalfEdge edge=null;
		while (ed.hasNext()) {
			edge=(HalfEdge)ed.next();
			if (edge.origin.vertIndx==v) return edge;
		}
		return null;
	}
	
	/**
	 * Given w, return first edge ending at w.
	 * @param w int
	 * @return edge ending at w or null
	 */
	public HalfEdge findV(int w) {
		Iterator<HalfEdge> ed=this.iterator();
		HalfEdge edge=null;
		while (ed.hasNext()) {
			edge=ed.next();
			if (edge.twin.origin.vertIndx==w) return edge;
		}
		return null;
	}
	
	/**
	 * Is <v,w> an edge in the list?
	 * @param v int
	 * @param w int
	 * @return int, first index for edge or -1 if not found
	 */
	public int isThereVW(int v,int w) {
		for (int j=0;j<this.size();j++) {
			HalfEdge edge=this.get(j);
			if (edge.origin.vertIndx==v && edge.twin.origin.vertIndx==w) return j;
		}
		return -1;
	}
	

	/**
	 * Return a combinatorial geodesic 'HalfLink' between two sets
	 * of vertices. This can fail in various ways, so exceptions
	 * must be caught. There may be multiple shortest paths,
	 * even with same endpoints; this returns the first encountered.
	 * 
	 * TODO: might look for the shortest with certain preference,
	 * for example, with most symmetry, closest to hex, etc.
	 * 
	 * @param p
	 * @param seeds NodeLink, starting point(s)
	 * @param targets NodeLink, target(s)
	 * @param nonos NodeLink, verts to avoid (except seeds or targets)
	 *    in this list
	 * @return HalfLink, null when empty.
	 */
	public static HalfLink getCombGeo(PackDCEL pdcel,
			NodeLink seeds,NodeLink targets,NodeLink nonos) 
					throws CombException {
		int []book=new int[pdcel.vertCount+1];
		if (seeds==null || seeds.size()==0 
				|| targets==null || targets.size()==0) 
			throw new CombException("no 'seeds' and/or no 'targets'");
		int maxdist=pdcel.vertCount;
		
		// mark the 'nonos' as -1, targets as -2
		if (nonos!=null && nonos.size()>0) { 
			Iterator<Integer> nlst=nonos.iterator();
			while(nlst.hasNext()) {
				book[nlst.next()]=-1;
			}
		}
		
		// mark targets as -2
		Iterator<Integer> tlst=targets.iterator();
		while(tlst.hasNext()) {
			int k=tlst.next();
			if (book[k]!=-1)
			book[k]=-2;
		}
		
		// label seeds as first generation
		int currgen=1;
		Iterator<Integer> slst=seeds.iterator();
		while (slst.hasNext()) {
			int v=slst.next();
			if (book[v]==-2)
				throw new CombException("'seed' and 'target' sets intersect");
			book[v]=currgen;
		}
		
		NodeLink prevGen=seeds.makeCopy();
		NodeLink nextGen=null;
		int safty=pdcel.vertCount*10;
		boolean hit=true;
		int theOne=0;
		while (hit && (safty--)>0 && currgen<maxdist) {
			hit=false;
			currgen++; 
			nextGen=new NodeLink();
			Iterator<Integer> prevG=prevGen.iterator();
			while (prevG.hasNext() && safty>0) {
				Vertex vert=pdcel.vertices[prevG.next()];
				HalfEdge he=vert.halfedge;
				do {
					int k=he.next.origin.vertIndx;
					
					// new vertex
					if (book[k]==0) {
						hit=true;
						book[k]=currgen;
						nextGen.add(k);
					}
					
					// hit a target?
					if (book[k]==-2) {
						hit=true;
						book[k]=currgen;
						maxdist=currgen;  // won't need to search further, 
										  // but continue to find competitors
						if (theOne==0)
							theOne=k;
					}
					he=he.prev.twin; // cclw
				} while (he!=vert.halfedge);
			} // inner while
			safty--;
			prevGen=nextGen;
		} // outer while
		if (safty<=0) {
			throw new CombException(
					"problem creating combinatorial geodesic");
		}
		if (currgen<maxdist) {
			throw new CombException(
					"'seed' and 'target' separated, no geodesic");
		}
	
		// create the edge path from 'theOne' to some seed
		HalfLink hlink=new HalfLink();
		int start=theOne;
		int gen=book[theOne]-1;
		while (gen>0) {
			int ed=-1;
			Vertex vert=pdcel.vertices[start];
			HalfEdge he=vert.halfedge;
			do {
				int k=he.next.origin.vertIndx;
				if (book[k]==gen) {
					ed=k;
					break;
				}
				he=he.prev.twin; // cclw
			} while (he!=vert.halfedge);
			if (ed==-1)
				throw new CombException("error in generation "+gen);
			hlink.add(he);
			start=ed;
			gen--;
		}
		
		if (hlink.size()==0) 
			throw new CombException("Failed to get comb geodesic");
		hlink=HalfLink.reverseElements(hlink);
		return HalfLink.reverseLink(hlink);
	}
	
	/**
	 * This is list of 'HalfEdge's whose faces form
	 * a closed contiguous chain along the full side 
	 * 'sideIndx' of the packing, or if 'sideIndx<0', 
	 * along the full red chain. Note that the first
	 * and last edges in the result should have the
	 * same face, so, e.g., we can layout along this chain
	 * of faces to compute holonomy.
	 * @param pdcel PackDCEL
	 * @param sideIndx int, possibly <=0
	 * @return HalfLink, null on error
	 */
	public static HalfLink HoloHalfLink(PackDCEL pdcel,int sideIndx) {
		
		HalfLink hlink=new HalfLink();
		RedEdge rtrace=pdcel.redChain;
		boolean debug=false; // debug=true;
		
		// for whole red chain, we don't include any red edges
		if (sideIndx<=0) {
			if (rtrace==null)
				return null;
		
			// add first
			hlink.add(rtrace.myEdge);
					
			// get clw edges until next red edge
			do {
				HalfEdge he=rtrace.myEdge.next;
				while (he.myRedEdge==null) {
					hlink.add(he.twin);
					he=he.twin.next;
				}
				rtrace=rtrace.nextRed;
			} while (rtrace!=pdcel.redChain);

			return hlink;
		}
		
		// however, if the given side is part of a closed loop,
		//    may cross paired reds as we go until closing up.
		if (pdcel.pairLink==null || sideIndx>=pdcel.pairLink.size()) 
			throw new CombException(
				"packing does not side with index "+sideIndx);
		SideData sdata=pdcel.pairLink.get(sideIndx);
		rtrace=sdata.startEdge;
		HalfEdge he=rtrace.myEdge;
		hlink.add(he);
		
		// Is this a bdry side? follow the bdry component
		if (rtrace.myEdge.isBdry()) {
			// get clw edges until next bdry edge
			do {
				he=he.next;
				while (!he.isBdry()) {
					hlink.add(he.twin);
					he=he.twin.next;
				}
			} while (he!=rtrace.myEdge);
			return hlink;
		}
		
		// otherwise should be closed interior loop;
		// finding it can take some work in general.
		
		// first check if side start/end at same vert
		int v=rtrace.myEdge.origin.vertIndx;
		int w=sdata.endEdge.myEdge.next.origin.vertIndx;
		
		if (v==w) {
			
			// should not encounter red edges along the side
			int safety=1000;
			do {
				he=rtrace.myEdge.next;
				while (he.myRedEdge==null) {
					safety--;
					hlink.add(he.twin);
					if (debug) 
						System.out.println("v=w case: added "+he.twin);
					he=he.twin.next;
				}
				rtrace=rtrace.nextRed;
				safety--;
			} while (rtrace!=sdata.endEdge && safety>0);
			if (safety==0)
				throw new CombException("safety exit in 'HoloHalfLink'");
			
			// finish connection to sdata.startEdge
			he=sdata.endEdge.myEdge.next;
			while (he!=sdata.startEdge.myEdge) { // he=rtrace.myEdge;
				hlink.add(he.twin);
				if (debug) 
					System.out.println("v=w case: added "+he.twin);
				he=he.twin.next;
			}
			
			return hlink;
		}
		
		// not so simple? start by following red until
		//   first return to v, then reprocess to 
		//   eliminate detours.     
		
		// zero out 'redutil'
		RedEdge rtr=pdcel.redChain;
		do {
			rtr.redutil=0;
			rtr=rtr.nextRed;
		} while (rtr!=pdcel.redChain);
		
		// preliminary link: follow and mark the reds.
		HalfLink prelink=new HalfLink(); // debug=true;
		int tick=0;
		rtrace.redutil=++tick;
		int safety=1000;
		do {
			he=rtrace.myEdge.next;
			while (he.myRedEdge==null && safety>0) {
				safety--;
				prelink.add(he.twin);
				if (debug) 
					System.out.println("prelink: added "+he.twin);
				he=he.twin.next;
			}
			rtrace=rtrace.nextRed;
			rtrace.redutil=++tick;
			safety--;
		} while (rtrace.myEdge.origin.vertIndx!=v && safety>0);
		if (safety==0)
			throw new CombException("safety exit in 'HoloHalfLink'");
		
		// now pass through the list again. 
		Iterator<HalfEdge> pis=prelink.iterator();
		while (pis.hasNext() && safety>0) {
			safety--;
			he=pis.next();
			hlink.add(he);
			if (debug) 
				System.out.println("hlink: added "+he);
			if (he.next.myRedEdge!=null) {
				if (he.next.origin.vertIndx==v) {
					HalfEdge firsthe=hlink.get(0);
					while (he.next!=firsthe && safety>0) { // firsthe=he.next;
						safety--;
						he=he.next.twin;
						hlink.add(he);
						if (debug) 
							System.out.println("hlink: added "+he);
					}
					if (safety==0)
						throw new CombException("safety exit in 'HoloHalfLink'");
					return hlink;
				}
				he=he.next; // this is red, should we include it and jump?
				if (he.twin.myRedEdge!=null && he.twin.myRedEdge.redutil!=0) {
					HalfEdge newhe=he.twin;
					hlink.add(newhe);
					if (debug) 
						System.out.println("hlink: added "+newhe);
					HalfEdge stophe=he.twin.prev;
					
					// jump over the detour
					do {
						safety--;
						if (!pis.hasNext())
							throw new CombException("some error in processin 'prelink'");
						he=pis.next();
					} while (he!=stophe && safety>0);
					if (safety==0)
						throw new CombException("safety exit in 'HoloHalfLink'");
				}
			}
		} // done with while

		// should exit before this
		return hlink;
	}

	/**
	 * Rotate EdgeLink so it starts with 'edge', if 'edge'
	 * is in the link.
	 * @param hlink EdgeLink
	 * @param edge HalfEdge
	 * @return HalfLink, null if empty or does not contain 'edge'
	 */
	public static HalfLink rotateMe(HalfLink hlink,HalfEdge edge) {
		int sz=hlink.size();
		if (hlink==null || sz==0)
			return null;
		HalfLink nlink=new HalfLink();
		int indx=hlink.lastIndexOf(edge);
		if (indx<0)
			return null;
		int i=indx;
		while (i<sz) { // 
			nlink.add(hlink.get(i));
			i++;
		}
		i=0;
		while (i<indx) {
			nlink.add(hlink.get(i));
			i++;
		}
		return nlink;
	}

	/**
	 * Reverse each element, but keep list in same
	 * order (see 'reverseLink' to reverse list order)
	 * @param hlink HalfLink
	 * @return HalfLink, possibly null
	 */

	public static HalfLink reverseElements(HalfLink hlink) {
		HalfLink newLink=new HalfLink();
		if (hlink==null || hlink.size()==0)
			return newLink;
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext()) 
			newLink.add(his.next().twin);
		return newLink;
	}
	
	/**
	 * Reverse list order (but elements not 
	 * individually reversed; see 'reverseElements')
	 * @param hlink HalfLink
	 * @return HalfLink, possibly null
	 */
	public static HalfLink reverseLink(HalfLink hlink) {
		HalfLink newLink=new HalfLink();
		if (hlink==null || hlink.size()==0)
			return newLink;
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext()) 
			newLink.add(0,his.next());
		return newLink;
	}

	/** 
     * Check if edge {v,w} (or {w,v}) is in given edge list.
     * @param halflist HalfLink
     * @param v int
     * @param w int
     * @return boolean
    */
    public static boolean ck_in_hlist(HalfLink halflist,int v,int w) {
    	if (halflist==null || halflist.size()==0) 
    		return false;
    	Iterator<HalfEdge> elist=halflist.iterator();
    	HalfEdge edge=null;
    	while (elist.hasNext()) {
    		edge=(HalfEdge)elist.next();
            if ((edge.origin.vertIndx==v && edge.twin.origin.vertIndx==w) || 
            		(edge.origin.vertIndx==w && edge.twin.origin.vertIndx==v) )
              	return true;
    	}
    	return false;
    }
    
    /**
     * Find index of edge <v,w> or <w,v> in the list
     * @param hlist HalfLink
     * @param v int
     * @param w int
     * @return -1 on error
     */
    public static int getVW(HalfLink hlist,int v,int w) {
    	if (v>w) {
    		int hold=w;
    		w=v;
    		v=hold;
    	}
    	for (int i=0;i<hlist.size();i++) {
    		HalfEdge he=hlist.get(i);
    		if (he.origin.vertIndx==v && he.twin.origin.vertIndx==w)
    			return i;
    	}
    	return -1;
    }
    
    /**
     * Return random entry from 'edgelist'; caution, does not adjust
     * for repeat entries.
     * @param edgelist
     * @return null on empty list
     */
    public static HalfEdge randEdge(HalfLink edgelist) {
    	if (edgelist==null || edgelist.size()==0) 
    		return null;
    	int n=new Random().nextInt(edgelist.size());
    	return (edgelist.get(n));
    }

    /**
     * Convert a polygonal path into a linked list of edges; first convert
     * to a 'FaceLink', then go down the right side of this chain of faces.
     * May have a preferred starting vertex. Note: sometimes an 'EdgeLink'
     * is better since in is based on indices.
     * TODO: currently, eucl only
     * @param p PackData
     * @param pInt PathInterpolator
     * @param startVert (or 0 if non specified)
     * @return EdgeLink
     */
	public static EdgeLink path2edgepath(PackData p,
			PathInterpolator pInt,int startVert) {
		FaceParam startFP=FaceLink.pathProject(p,pInt,startVert);
		if (startFP==null || startFP.next==null) 
			return null;
		int startFace=startFP.faceIndx;
		FaceParam ftrace=startFP;
		while (ftrace.next!=null) 
			ftrace=ftrace.next;
		int endFace=ftrace.faceIndx;
		
		// get start/end complex points
		Complex startZ=pInt.sToZ(0.0);
		Complex endZ=null;
		if (pInt.closed) 
			endZ=new Complex(startZ);
		else 
			endZ=new Complex(pInt.pathZ.lastElement());
		
		// start/end vertices are those closest to startZ/endZ
		double smin=10000.0;
		double emin=10000.0;
		int[] startverts=p.packDCEL.faces[startFace].getVerts();
		int[] endverts=p.packDCEL.faces[endFace].getVerts();
		int sindx=0;
		int eindx=0;
		double ut=0.0;
		for (int i=0;i<3;i++) {
			ut=startZ.minus(p.getCenter(startverts[i])).abs();
			if (ut<smin) {
				smin=ut;
				sindx=i;
			}
			ut=endZ.minus(p.getCenter(endverts[i])).abs();
			if (ut<emin) {
				emin=ut;
				eindx=i;
			}
		}
		int startV=startverts[sindx];
		int endV=endverts[eindx];
		if (startV==endV) 
			return null;
		
		// Remove any unneeded faces at beginning (i.e, next face has startV)
		while (startFP.next!=null && p.face_index(startFP.next.faceIndx,startV)>=0)
			startFP=startFP.next;
		
		// Remove any unneeded faces at end (i.e., previous face has endV)
		ftrace=startFP; 
		while (ftrace!=null && p.face_index(ftrace.faceIndx,endV)<0) // find first face containing endV
			ftrace=ftrace.next;
		if (ftrace==null)  // should not happen 
			return null;
		// ftrace is first 'FaceParam' whose face contains endV; do the rest?
		boolean done=false;
		FaceParam ntrace=ftrace;
		while (!done) {
			while (ntrace.next!=null && p.face_index(ntrace.next.faceIndx,endV)>=0)
				ntrace=ntrace.next;
			if (ntrace.next==null) 
				done=true; 
			else  // a face that doesn't have endV
				ftrace=ntrace.next;
			while (ftrace!=null && p.face_index(ftrace.faceIndx,endV)<0) // find next face with endV
				ftrace=ftrace.next; 
			if (ftrace==null) 
				return null; // should not happen
		}				
		ftrace.next=null; // stop chain here
		
		// Some ambiguity in converting from face chain to edgepath.
		// Take simple route: choose the edges along the left of the faces
		EdgeLink elink=new EdgeLink(p);
		int v=startV;
		int nextv=v;
		EdgeSimple edge=new EdgeSimple(v,v);
		EdgeSimple nextedge=new EdgeSimple(v,v);
		ftrace=startFP;
		while (ftrace.next!=null) {
			
			// continue until the putative 'nextedge' is satifactory:
			//  non-trivial, connected to previous, not reverse of previous
			while( (v==nextv || nextedge.v==nextedge.w ||
					nextedge.v!=edge.w || nextedge.w==edge.v) && 
					ftrace.next!=null) {

				// create putative next edge
				int nt=p.face_nghb(ftrace.faceIndx,ftrace.next.faceIndx);
				if (nt<0) 
					throw new CombException("broken chain in 'FaceParam' list");
				nextv=p.packDCEL.faces[ftrace.next.faceIndx].getVerts()[nt];
				nextedge=new EdgeSimple(v,nextv);
				ftrace=ftrace.next;
			}
			// success??
			if (v!=nextv && nextedge.v!=nextedge.w && nextedge.v==edge.w 
					&& nextedge.w!=edge.v) { 
				elink.add(nextedge);
				v=nextv;
				edge=nextedge;
			}
			// no success, but check last face: if v,endV are in it, add last edge 
			else if (ftrace!=null && ftrace.next==null && v!=endV
					&& p.face_index(ftrace.faceIndx,endV)>=0 && p.face_index(ftrace.faceIndx,v)>=0) {
				elink.add(new EdgeSimple(v,endV));
				v=endV;
			}
			else if (v!=endV)
				throw new CombException("seem to have error in finding next edge");
		} // end of while

		// there may be a last edge
		if (v!=endV) {
			if (p.nghb(v,endV)<0) 
				throw new CombException("failed to end at correct vertex");
			elink.add(new EdgeSimple(v,endV));
		}

		if (elink==null || elink.size()==0) return null;
		return elink;
	}
	
	 /**
	  * Does the given list of halfedges have vertices that 
	  * separate the complex? If the return is 0, then 
	  * answer is NO, else return the lowest index of circles
	  * not reachable.
	  * @param p PackData
	  * @param hlist HalfLink
	  * @return int, 0 if complex is NOT separated.
	  */
	 public static int separates(PackData p,HalfLink hlist) {
		 NodeLink nodes=new NodeLink(p);
		 Iterator<HalfEdge> hlst=hlist.iterator();
		 while (hlst.hasNext()) {
			 HalfEdge he=hlst.next();
			 nodes.add(he.origin.vertIndx);
			 nodes.add(he.next.origin.vertIndx);
		 }
		 return NodeLink.separates(p,nodes);
	 }
	 
	 /**
	  * Create an HalfLink that eliminates duplicate edges.
	  * @param el HalfLink
	  * @param orient boolean, true, then take account of orientation
	  * @return new HalfLink
	  */
	 public static HalfLink removeDuplicates(HalfLink el,boolean orient) {
		 HalfLink newEL=new HalfLink(el.packData);
		 Iterator<HalfEdge> els=el.iterator();
		 while (els.hasNext()) {
			 HalfEdge edge=els.next();
			 if (newEL.containsVW(edge))
				 continue;
			 newEL.add(edge);
		 }
		 return newEL;
	 }

	 /**
	  * Check if this list contains oriented edge (v,w) 
	  * @param es EdgeSimple
	  * @return boolean
	  */
	 public boolean containsVW(EdgeSimple es) {
		 Iterator<HalfEdge> els=this.iterator();
		 while (els.hasNext()) {
			 HalfEdge he=els.next();
			 int v=he.origin.vertIndx;
			 int w=he.twin.origin.vertIndx;
			 if (v==es.v && w==es.w)
				 return true;
		 }
		 return false;
	 }
	 
	 /**
	  * Check if this list contains oriented edge (v,w) 
	  * @param edge HalfEdge
	  * @return boolean
	  */
	 public boolean containsVW(HalfEdge he) {
		 EdgeSimple es=new EdgeSimple(he.origin.vertIndx,he.twin.origin.vertIndx);
		 return containsVW(es);
	 }
	 
	/**
	 * Create a list of entries as a string
	 * @return String, null on error
	 */
	public String toString() {
		if (this.size()==0)
			return null;
		StringBuilder sb=new StringBuilder();
		Iterator<HalfEdge> myit=this.iterator();
		while (myit.hasNext()) {
			HalfEdge edge=myit.next();
			sb.append(" "+edge); // calls 'toString' method
		}
		return sb.toString();
	}
	
	/**
	 * Add 'HalfEdge's which separates 'vlist' vertices from 
	 * 'alphaIndx' and from those vertices connected to 'alphaIndx'
	 * in the complement of 'vlist'. Vertices along this edge
	 * are not in 'vlist' and may include 'alphaIndx' itself.
	 * Exception if 'alphaIndx' is in 'vlist'.  
	 * @param pdcel packDCEL
	 * @param vlist nodeLink
	 * @return int, count of edges or exception on error
	 */
	public int separatingLinks(PackDCEL pdcel,NodeLink vlist,
			int alphaIndx) {
		int count=0;
		int[] vhits=new int[pdcel.vertCount+1]; 
		
		// mark vertices: -1=excluded, 1=added to nxt, 2=handled
		Iterator<Integer> vst=vlist.iterator();
		while(vst.hasNext()) 
			vhits[vst.next()]=-1;
		
		if (alphaIndx<1 || alphaIndx>pdcel.vertCount || vhits[alphaIndx]==-1)
			throw new ParserException(
					"improper alpha specified: "+alphaIndx);

		// set 'eutil' for edges that get chosen
		for (int e=1;e<=pdcel.edgeCount;e++) {
			// debug: are indices right?
//			if (pdcel.edges[e].edgeIndx!=e)
//				System.err.println("edge "+e+" doesn't have correct 'edgeIndx'");
			pdcel.edges[e].eutil=0;
		}

		// oscillate between two lists
		NodeLink curr=new NodeLink();
		NodeLink nxt=new NodeLink();
		nxt.add(alphaIndx);
		while (nxt.size()>0) {
			curr=nxt;
			nxt=new NodeLink();
			
			Iterator<Integer> cis=curr.iterator();
			while (cis.hasNext()) {
				int v=cis.next();
				vhits[v]=2;
				Vertex vert=pdcel.vertices[v]; 
				HalfLink slink=vert.getSpokes(null);
				int num=slink.size();
				
				// catalog the situation with spokes
				int[] edx=new int[num];
				for (int j=0;j<num;j++) {
					HalfEdge spoke=slink.get(j);
					int w=spoke.twin.origin.vertIndx; // other end
					if (vhits[w]==-1)
						edx[j]=-spoke.edgeIndx; // set negative
					else
						edx[j]=spoke.edgeIndx; // set positive
				}
				
				// include if edx is +, but one of ngbhs is -
				for (int j=0;j<num;j++) {
					if (edx[j]>0) {
						HalfEdge he=pdcel.edges[edx[j]].twin;
						if (he.eutil==0 && he.twin.eutil==0 && 
							(edx[(j+1)%num]<0 || edx[(j+num-1)%num]<0)) {
							add(pdcel.edges[edx[j]]);
							he.eutil=he.twin.eutil=1;
							count++;
						}
						if (vhits[he.origin.vertIndx]==0) {
							vhits[he.origin.vertIndx]=1;
							nxt.add(he.origin.vertIndx);
						}
					}
				}
			} // while for current list
		} // while for next list
			
		return count;
	}
	
	/**
	 * Pick first edge off a string. Return null on failure.
	 * @param p PackData
	 * @param str String
	 * @return HalfEdge, null if none found
	 */
	public static HalfEdge grab_one_edge(PackData p,String str) {
		HalfLink hlist=new HalfLink(p,str);
		if (hlist.size()>0) {
			HalfEdge he=(HalfEdge)hlist.get(0);
			return he;
		}
		return null;
	}
		
	/**
	 * Pick first edge off a string. Return null on failure.
	 * @param p PackData
	 * @param flagsegs Vector<Vector<String>>
	 * @return EdgeSimple, null if none found
	 */
	public static HalfEdge grab_one_edge(PackData p,Vector<Vector<String>> flagsegs) {
		String str=StringUtil.reconstitute(flagsegs);
		return grab_one_edge(p,str);
	}

	/**
	 * Return fresh EdgeLink with entries translated from 
	 * 'hlink' using 'vmap'. So, entry <v,w> in 'hlink' and 
	 * entries <v,V> and <w,W> in 'vmap' leads to EdgeSimple 
	 * <V,W> entry. If 'vmap' has no translation for v or w, 
	 * use the original. Note: calling routine with access to 
	 * parent DCEL may want to reinterpret EdgeLink as HalfLink.
	 * 
	 * @param hlink HalfLink
	 * @param vmap VertexMap, giving pairs <v,V> for translation
	 * @return EdgeLink, null if hlink is null.
	 */
	public static EdgeLink translate(HalfLink hlink, VertexMap vmap) {
		if (hlink == null)
			return null;
		EdgeLink out = new EdgeLink(hlink.packData);

		// if no 'vmap', return new copy of nlink
		if (vmap == null || vmap.size() == 0) {
			Iterator<HalfEdge> hl = hlink.iterator();
			while (hl.hasNext()) {
				out.add(HalfEdge.getEdgeSimple(hl.next()));
			}
			return out;
		}

		Iterator<HalfEdge> hl = hlink.iterator();
		while (hl.hasNext()) {
			HalfEdge he = hl.next();
			EdgeSimple es=translate(he,vmap);
			if (es!=null)
				out.add(es);
		}
		return out;
	}
	
	/**
	 * Return EdgeSimple with entries translated from 
	 * 'hlink' using 'vmap'. So, entry <v,w> in 'hlink' and 
	 * entries <v,V> and <w,W> in 'vmap' leads to EdgeSimple 
	 * <V,W> entry. If 'vmap' has no translation for v or w, 
	 * use the original.
	 * @param hlink HalfLink
	 * @param vmap VertexMap, giving pairs <v,V> for translation
	 * @return EdgeLink, null if edge is null.
	 */
	public static EdgeSimple translate(HalfEdge edge, VertexMap vmap) {
		if (edge==null)
			return null;
		int v = edge.origin.vertIndx;
		int w = edge.twin.origin.vertIndx;
		if (v <= 0 || w <= 0)
			return null;
		int V = vmap.findW(v);
		int W = vmap.findW(w);
		if (V == 0)
			V = v;
		if (W == 0)
			W = w;
		return new EdgeSimple(v,w);
	}
	
	/**
	 * Return all outward spokes from the given vertices.
	 * @param pdcel PackDCEL
	 * @param vlist NodeLink
	 * @return HalfLink, null on error or empty
	 */
	public static HalfLink getSpokes(PackDCEL pdcel,NodeLink vlist) {
		if (vlist==null || vlist.size()==0)
			return null;
		HalfLink hlink=new HalfLink();
		Iterator<Integer> vis=vlist.iterator();
		while (vis.hasNext()) {
			Vertex vert=pdcel.vertices[vis.next()];
			hlink.abutMore(vert.getSpokes(null));
		}
		if (hlink.size()==0)
			return null;
		return hlink;
	}
	
	/**
	 * Find the link of 'next' halfedges starting with 'edge':
	 * i.e. edge, edge.next,edge.next.next, etc. ending with
	 * 'edge.prev'. Thus these bound a face, usually there are
	 * 3, but for ideal face may be more. Set safety to avoid
	 * loop; return null if tripped.
	 * NOTE: use 'edge' because faces are ephemeral
	 * @param pdcel PackDCEL
	 * @param edge HalfEdge
	 * @return HalfLink, null on error
	 */
	public static HalfLink nextLink(PackDCEL pdcel,HalfEdge edge) {
		HalfLink hlink=new HalfLink();
		HalfEdge he=edge;
		int safety=100;
		do {
			safety--;
			hlink.add(he);
			he=he.next;
		} while (he!=edge && safety>0);
		if (safety==0)
			return null;
		return hlink;
	}
	
	/**
	 * Build the 'HalfLink' whose faces define the contiguous
	 * chain of faces to the left of 'hlink'; used, e.g., for 
	 * holonomy or layout purposes. 
	 * 
	 * Begin with the first edge of 'hlink', then proceed with 
	 * halfedges inward from the left into the end of successive
	 * edges of 'hlink'. Continue only as long as the edges of 
	 * 'hlink' remain contiguous, the incoming edges are not in
	 * 'hlink', and we don't encounter an incoming edge whose 
	 * face is ideal. 
	 *  + If 'hlink' is not closed, then we end with the last clw
	 *    incoming edge of the last halfedge's origin. Thus, the
	 *    contiguous faces of the resulting HalfLink start with 
	 *    the face for the first edge of 'hlink' and end with
	 *    the face for the last edge of 'hlink'. 
	 *  + If 'hlink' is closed, then we end with the last 
	 *    clw incoming edge at the origin of the first halfedge.
	 *    So the contiguous faces of the resulting HalfLink 
	 *    start and end with the face for the first edge of 
	 *    'hlink'.  
	 *    
	 * The user will have to determine how to specify 'hlink'
	 * depending on how the resulting HalfLink will be used.
	 *  
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink, should be contiguous, may be closed
	 * @return HalfLink, may be null or empty
	 */
	public static HalfLink leftsideLink(PackDCEL pdcel,HalfLink hlink) {
		if (hlink==null || hlink.size()==0)
			return null;
		boolean closed=false;
		
		// save first/last
		HalfEdge firsthe=hlink.getFirst();
		HalfEdge lasthe=hlink.getLast();

		// if just one edge in list
		HalfLink outlink=new HalfLink();
		outlink.add(firsthe); // single face left of this edge
		if (hlink.size()==1) 
			return outlink;

		// note if closed, make sure first edge does not repeat at end
		if (lasthe.twin.origin==firsthe.origin) // yes, closes up 
			closed=true;
		else if (lasthe==firsthe) { 
			closed=true;
			hlink.removeLast();
			lasthe=hlink.getLast();
		}
		 
		Iterator<HalfEdge> his=hlink.iterator();
		HalfEdge currhe=his.next();
		HalfEdge nexthe=currhe;
		while (his.hasNext()) {
			  currhe=nexthe;
			  nexthe=his.next();
			  
			  // not contiguous?
			  if (currhe.twin.origin!=nexthe.origin) {
				  return outlink;
			  }
			  
			  HalfEdge he=currhe.next.twin;
			  while (he!=nexthe.twin) {
				  
				  // hit an ideal face?
				  if (he.face!=null && he.face.faceIndx<0) 
					  return outlink;
				  
				  outlink.add(he);
				  he=he.next.twin; // clw
			  }
		}
		if (closed && nexthe==lasthe) {
			HalfEdge he=nexthe.next.twin;
			while (he!=firsthe.twin) {
				  
				// hit an ideal face?
				if (he.face!=null && he.face.faceIndx<0) 
					return outlink;
				  
				outlink.add(he);
				he=he.next.twin; // clw
			}
		}
		return outlink;
	}
	
	
	/**
	 * Given a vertex list, convert it (to the extent possible) to 
	 * an edge list. If 'extended' flag true, do "hex-extended" 
	 * edges, which pass through interior vertices so same number 
	 * of edges are on each side. See 'axis_extend' call. 
	 * Else, convert to edge geodesic.
	 * TODO: do I really mean "hex" extend, or more general axis
	 *       extend?
	 * @param pdcel PackDCEL
	 * @param vlist NodeLink
	 * @param hexflag boolean, true, hex extend
	 * @return HalfLink, possibly empty or null
	 */
	public static HalfLink verts2edges(PackDCEL pdcel,
			NodeLink vertlist,boolean hexflag) {
		HalfLink ans=new HalfLink();
		if (vertlist==null || vertlist.size()==0) 
			return ans;
		Iterator<Integer> vlist=vertlist.iterator();
		int endv=(Integer)vlist.next();
		while (vlist.hasNext()) {
			int nextv=(Integer)vlist.next();
			// eat any duplicates of 'endv'
			while (vlist.hasNext() && nextv==endv) {
				nextv=(Integer)vlist.next();
			}
			if (nextv!=endv) {
				HalfEdge ee;
				if (hexflag) { // look for/use axis-extended edges
					ee=pdcel.findHalfEdge(endv,nextv);
					if (ee!=null)
						ans.add(ee);
					else {
						Vertex basevert=pdcel.vertices[endv];
						HalfLink hlink=
							CombDCEL.shootExtended(basevert,nextv,16,hexflag);
						if (hlink!=null && hlink.size()>0) 
							ans.abutMore(hlink);
					}
				}
				else if ((ee=pdcel.findHalfEdge(endv,nextv))!=null) {
					ans.add(ee);
				}
				endv=nextv;
			}
		} // end of while
		return ans;
	}
	
	/**
	 * Expect items to specify a GraphLink of dual edges <f,g>;
	 * convert to associated halfedges <v,w>; so f is on left
	 * side of <v,w>, g is on right.
	 * @param p PackData
	 * @param items Vector<String>
	 * @return HalfLink, null if empty
	 */
	public static HalfLink glist_to_hlink(PackData p,Vector<String> items) {
		GraphLink glink=new GraphLink(p,items);
		HalfLink hlink=new HalfLink();
		Iterator<EdgeSimple> gis=glink.iterator();
		while (gis.hasNext()) 
			hlink.add(p.packDCEL.dualEdge_to_halfedge(gis.next()));
		if (hlink.size()>0)
			return hlink;
		return null;
	}
		
	/**
	 * Make up list by looking through SetBuilder specs 
	 * (from {..} set-builder notation). Use 'tmpUtil' to 
	 * collect information before creating the HalfLink
	 * for return. (Have not yet implemented many edge 
	 * selections) 
	 * @param p PackData
	 * @param specs Vector<SelectSpec>
	 * @return HalfLink list of specified edges
	 */
	public static HalfLink edgeSpecs(PackData p, Vector<SelectSpec> specs) {

		if (specs == null || specs.size() == 0)
			return null;
		SelectSpec sp = null;
		int count = 0;
		// will store results in 'eutil'

		int[] tmpUtil = new int[p.packDCEL.edgeCount + 1];
		// loop through all the specifications: these should alternate
		// between 'specifications' and 'connectives', starting
		// with the former, although typically there will be just
		// one specification in the vector and no connective.
		UtilPacket uPx = null;
		UtilPacket uPy = null;
		boolean isAnd = false; // true for '&&' connective, false for '||'.
		for (int j = 0; j < specs.size(); j++) {
			sp = (SelectSpec) specs.get(j);
			if (sp.object != 'e')
				throw new ParserException(); // spec must be edges/halfedges
			try {
				for (int e = 1; e <= p.packDCEL.edgeCount; e++) {

					// success?
					boolean outcome = false;
					uPx = sp.node_to_value(p, e, 0);
					if (sp.unary) {
						if (uPx.rtnFlag != 0)
							outcome = sp.comparison(uPx.value, 0);
					} else {
						uPy = sp.node_to_value(p, e, 1);
						if (uPy.rtnFlag != 0)
							outcome = sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && tmpUtil[e] == 0) { // 'or' situation
							tmpUtil[e] = 1;
							count++;
						}
					} else { // no, fails this condition
						if (isAnd && tmpUtil[e] != 0) { // 'and' situation
							tmpUtil[e] = 0;
							count--;
						}
					}
				}
			} catch (Exception ex) {
				throw new ParserException();
			}

			// if specs has 2 or more additional specifications, the next must
			// be a connective. Else, finish loop.
			if ((j + 2) < specs.size()) {
				sp = (SelectSpec) specs.get(j + 1);
				if (!sp.isConnective)
					throw new ParserException();
				isAnd = sp.isAnd;
				j++;
			} else
				j = specs.size(); // kick out of loop
		}

		if (count > 0) {
			HalfLink hl = new HalfLink(p);
			for (int e = 1; e <= p.packDCEL.edgeCount; e++)
				if (tmpUtil[e] != 0)
					hl.add(p.packDCEL.edges[e]);
			return hl;
		} else
			return null;
	}

}
