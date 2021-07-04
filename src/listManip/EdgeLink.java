package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import complex.Complex;
import dcel.CombDCEL;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.Vertex;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import komplex.DualGraph;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.RedEdge;
import komplex.SideDescription;
import packQuality.QualMeasures;
import packing.PackData;
import util.FaceParam;
import util.MathUtil;
import util.PathInterpolator;
import util.SelectSpec;
import util.SphView;
import util.StringUtil;
import util.UtilPacket;

/* fixup: might want an abstract parent class because of common methods:
 * However, I don't know how to do this with and still set the 'type' of elements.
 */

/**
 * Linked list for edges of circle packings. 
 * @author kens
 *
 */
public class EdgeLink extends LinkedList<EdgeSimple> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	static final int XTD_LINKS=16; // how many to look for in 'extended' edges.
	PackDCEL pdc; // utility pointer, may be null
	
	// Constructors
	public EdgeLink(PackData p,EdgeSimple edge) {
		super();
		packData=p;
		if (p!=null)
			pdc=p.packDCEL; 
		add(edge);
	}
	
	/**
	 * @param p @see PackData
	 * @param datastr String
	 * @param xtd, boolean, yes for extended
	 */
	public EdgeLink(PackData p,String datastr,boolean xtd) {
		super();
		packData=p;
		if (p!=null)
			pdc=p.packDCEL; 
		if (datastr!=null && datastr.length()>0) 
			addEdgeLinks(datastr,xtd);
	}
	
	/**
	 * not necessarily extended edges
	 * @param p PackData
	 * @param datastr String
	 */
	public EdgeLink(PackData p,String datastr) {
		this(p,datastr,false);
	}
	
	/**
	 * Allow extended edges
	 * @param p
	 * @param items
	 * @param xtd, boolean, yes for extended
	 */
	public EdgeLink(PackData p,Vector<String> items,boolean xtd) {
		super();
		packData=p;
		if (p!=null)
			pdc=p.packDCEL; 
		if (items==null || items.size()==0) { // default to 'a' (all edges)
			items=new Vector<String>(1);
			items.add("a");
		}
		addEdgeLinks(items,xtd);
	}

	/**
	 * not necessarily extended edges
	 * @param p
	 * @param items
	 */
	public EdgeLink(PackData p,Vector<String> items) {
		this(p,items,false);
	}
	
	/**
	 * Not associated with any PackData
	 * @param datastr
	 */
	public EdgeLink(String datastr) {
		this(null,datastr);
	}
	
	/**
	 * empty list, no packing
	 */
	public EdgeLink() {
		super();
		packData=null;
	}
	
	/**
	 * Initiate empty list
	 * @param p
	 */
	public EdgeLink(PackData p) {
		this(p,(String)null);
	}
	
	/** 
	 * Enforce legality of vertex indices if 'packData' is not null. 'edge.v' 
	 * and 'edge.w' must be positive.
	 * @param edge EdgeSimple
	 * @return boolean, true if added
	 */
	public boolean add(EdgeSimple edge) {
		if (edge==null)
			return false;
		if (packData==null)
			return super.add(edge);
		if ((edge.v>0 && edge.w>0) && edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) 
			return super.add(edge);
		else return false;
	}
	
	/** 
	 * Enforce legality of vertex indices if and only 'packData' is not null
	 * @param v int
	 * @param w int
	 * @return boolean, true if added
	 */
	public boolean add(int v, int w) {
		if ((packData==null || (v>0 && w>0 
				&& v<=packData.nodeCount && w<=packData.nodeCount)) || packData==null)
			return super.add(new EdgeSimple(v,w));
		else return false;
	}
	
	/**
	 * Remove occurances of 'edge' if in same order
	 * @param edge EdgeSimple
	 * @return int count
	 */
	public int removeOrdered(EdgeSimple edge) {
		int count=0;
		for (int k=this.size();k>0;k--) {
			EdgeSimple thisedge=this.get(k-1);
			if (thisedge.v==edge.v && thisedge.w==edge.w) {
				this.remove(k-1);
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Remove redundant edges in the given list (with the 
	 * same order).
	 * @param edgelist EdgeLink, can be null
	 * @return int count
	 */
	public int removeOrdered(EdgeLink edgelist) {
		int count=0;
		if (edgelist==null)
			return count;
		Iterator<EdgeSimple> elt=edgelist.iterator();
		while (elt.hasNext()) 
			count+=removeOrdered(elt.next());
		return count;
	}
	
	/**
	 * Remove occurances of 'edge', irrespective of order
	 * @param edge EdgeSimple
	 * @return int count
	 */
	public int removeUnordered(EdgeSimple edge) {
		int count=0;
		for (int k=this.size();k>0;k--) {
			EdgeSimple thisedge=this.get(k-1);
			if ((thisedge.v==edge.v && thisedge.w==edge.w) ||
					(thisedge.v==edge.w&& thisedge.w==edge.v)) {
				this.remove(k-1);
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Remove all edges in the given list, either order.
	 * @param edgelist EdgeLink, can be null
	 * @return int count
	 */
	public int removeUnordered(EdgeLink edgelist) {
		int count=0;
		if (edgelist==null)
			return count;
		Iterator<EdgeSimple> elt=edgelist.iterator();
		while (elt.hasNext()) 
			count+=removeUnordered(elt.next());
		return count;
	}
	
	/** 
	 * Return VertexMap (i.e., same list without knowledge of 'packData'
	 * or null if there are no entries.)
	 * */
	public VertexMap toVertexMap() {
		Iterator<EdgeSimple> el=this.iterator();
		VertexMap vM=new VertexMap();
		while (el.hasNext()) vM.add((EdgeSimple)el.next());
		if (vM.size()>0) return vM;
		return null;
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
	public int addEdgeLinks(String datastr,boolean xtd) {
		if (packData==null) return -1;
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addEdgeLinks(items,xtd);
	}
	
	/**
	 * Add links to this list (if it is associated with PackData)
	 * @param items Vector<String>
	 * @param xtd boolean, true==>allow 'hex_extended' edges
	 * @return int count
	 */
	public int addEdgeLinks(Vector<String> items,boolean xtd) {
		if (packData==null) return -1;
		int count=0;
		int nodeCount=packData.nodeCount;
		
		// In DCEL case, if a single numerical vertex is given, then
		//   add it's 'halfedge' 
		if (packData.packDCEL!=null && items.size()==1) {
			try {
				int v=Integer.parseInt(items.get(0));
				if (v>0 && v<=packData.nodeCount) {
					Vertex vert=packData.packDCEL.vertices[v];
					int w=vert.halfedge.next.origin.vertIndx;
					add(new EdgeSimple(v,w));
					return 1;
				}
				else return -1;
			} catch(NumberFormatException ex) {
				// non integer? continue processing
			}
		}
		
		Iterator<String> its=items.iterator();
		while (its!=null && its.hasNext()) {

			/* =============== here's the work ==================
	 		parse the various options based on first character */

			String str=(String)its.next(); 
			// it's easy to use '-' flags by mistake
			if (str.startsWith("-")) { 
				str=str.substring(1);
			}
			
			its.remove(); // shuck this entry (though still in 'str')
			
			// self call if str is a variable
			if (str.startsWith("_")) {
				count+=addEdgeLinks(CPBase.varControl.getValue(str),xtd);
			}
			
			// check for '?list' first
			else if (str.substring(1).startsWith("list")) {
				EdgeLink elink=null;
				GraphLink glink=null;
				
				// elist or Elist
				if ((str.startsWith("e") && (elink=packData.elist)!=null
						&& elink.size()>0) ||
						(str.startsWith("E") && (elink=CPBase.Elink)!=null
								&& CPBase.Elink.size()>0)) {
					EdgeSimple edge=null;

					// check for brackets first
					String brst=StringUtil.brackets(str);
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate list
							elink.add(elink.getFirst());
						}
						if (brst.startsWith("r") 
								|| brst.startsWith("n")) { // use up first
							edge=(EdgeSimple)elink.remove(0);
							if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
									add(edge);
									count++;
							}
						}
						else if (brst.startsWith("l")) { // last
							edge=(EdgeSimple)elink.getLast();
							if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
								add(edge);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<elink.size()) {
									edge=(EdgeSimple)elink.get(n);
									if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
										add(edge);
										count++;
									}
								}
							} catch (NumberFormatException nfe) {
								
							}
						}
					}
					// else just adjoin the lists
					else { 
						Iterator<EdgeSimple> elst=elink.iterator();
						while (elst.hasNext()) {
							edge=(EdgeSimple)elst.next();
							if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
								add(edge);
								count++;
							}
						}
					}
				} // end of handling elist/Elist
				
				// glist or Glist
				else if ((str.startsWith("g") && (glink=packData.glist)!=null
						&& glink.size()>0) ||
						(str.startsWith("G") && (glink=CPBase.Glink)!=null
								&& CPBase.Glink.size()>0)) {
					EdgeSimple edge=null;

					// check for brackets first
					String brst=StringUtil.brackets(str);
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate list
							glink.add(glink.getFirst());
						}
						if (brst.startsWith("r") 
								|| brst.startsWith("n")) { // use up first
							edge=(EdgeSimple)glink.remove(0);
							if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
									add(edge);
									count++;
							}
						}
						if (brst.startsWith("l")) { // last
							edge=(EdgeSimple)glink.getLast();
							if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
								add(edge);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<glink.size()) {
									edge=(EdgeSimple)glink.get(n);
									if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
										add(edge);
										count++;
									}
								}
							} catch (NumberFormatException nfe) {}
						}
					}
					// else just adjoin the lists
					else { 
						Iterator<EdgeSimple> glst=glink.iterator();
						while (glst.hasNext()) {
							edge=(EdgeSimple)glst.next();
							if (edge.v<=packData.nodeCount && edge.w<=packData.nodeCount) {
								add(edge);
								count++;
							}
						}
					}
				} // end of handling glist/Glist
			}
			
			// For 'random', 2 steps: get edge list, then make selection
			else if (str.equals("r")) {
				EdgeLink elk=null;
				if (items==null || items.size()==0)
					elk=new EdgeLink(packData,"a");
				else elk=new EdgeLink(packData,items);
				EdgeSimple es=randEdge(elk);
				if (es!=null) {
					add(es);
					count++;
				}
			}

			/* Now parse remaining options based on first character;
	 		default case, just try to read off pairs of numbers. */

			else {
			switch(str.charAt(0)) {
			
			// all (undirected, so if v < w, then (v,w) is included but not (w,v))
			case 'a':
			{
				if (pdc!=null) { // organize via vertices -- more rational order?
					for (int v=1;v<=pdc.vertCount;v++) {
						Vertex vert=pdc.vertices[v];
						HalfLink hlink=vert.getSpokes(null);
						Iterator<HalfEdge> hits=hlink.iterator();
						while (hits.hasNext()) {
							HalfEdge he=hits.next();
							if (he.myRedEdge!=null) {
								add(new EdgeSimple(he.origin.vertIndx,
										he.twin.origin.vertIndx));
								count++;
							}
							else {
								int ev=he.origin.vertIndx;
								int ew=he.twin.origin.vertIndx;
								if (ev<ew) {
									add(new EdgeSimple(ev,ew));
									count++;
								}
							}
						}
					}
				}
				
				// traditional
				else {
					for (int v=1;v<=nodeCount;v++) {
						int w;
						int[] petals=packData.getPetals(v);
						for (int j=0;j<petals.length;j++) 
							if ((w=petals[j])>v) {
								add(new EdgeSimple(v,w));
								count++;
							}
					}
				}
				break;
			}

			// bdry; check for braces (a,b)
			case 'b':
			{
				int next;
				int first=1;
				int last=packData.nodeCount;
				String []pair_str=StringUtil.parens_parse(str); // get two strings
				if (pair_str!=null) { // got two strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))==0
							|| (b=NodeLink.grab_one_vert(packData,pair_str[1]))==0
							|| !packData.isBdry(a) || !packData.isBdry(b))
						return count;
					first=a;
					last=b;
					
					if (!packData.onSameBdryComp(first, last))
						return count;
							
					// on same component, so take all edges from first, ending at last
					next=packData.getFirstPetal(first);
					add(new EdgeSimple(first,next));
					count++;
					while (next!=last) {
						int oldnext=next;
						next=packData.getFirstPetal(next);
						add(new EdgeSimple(oldnext,next));
						count++;
					}
				}
				else { // whole boundary; note 'starts' is indexed from 1
					for (int i=1;i<=packData.getBdryCompCount();i++) {
						int strt=packData.getBdryStart(i);
						next=packData.getFirstPetal(strt);
						add(new EdgeSimple(strt,next));
						count++;
						while (next!=strt) {
							int oldnext=next;
							next=packData.getFirstPetal(next);
							add(new EdgeSimple(oldnext,next));
							count++;
						}
					}
				}
				break;
			}
			case 'g': // combinatorial geodesic path from v to w
			{
				NodeLink vertlist=new NodeLink(packData,items);
				if (vertlist==null || vertlist.size()<2) break;
				int v=vertlist.get(0);
				int w=vertlist.get(1);
				if (v==w) break;
				EdgeLink newelist=EdgeLink.getCombGeo(packData,
						new NodeLink(packData,v),new NodeLink(packData,w),null);
				if (newelist!=null && newelist.size()>0) {
					abutMore(newelist);
					count +=newelist.size();
				}
				return count;
			}
			case 'R': // red edges, outer edges of "sides" of red chain; 
				      // absorb rest of 'items'
			{
			  int numSides=-1;
			  String itstr=null;
			  if (packData.getSidePairs()==null || (numSides=packData.getSidePairs().size())==0) {
				  while (its.hasNext()) itstr=(String)its.next(); // use up
				  break;
			  }
			  boolean []tag=new boolean[numSides];
			  for (int i=0;i<numSides;i++) tag[i]=false;
			  if (!its.hasNext()) { // default to 'all'
				  for (int i=0;i<numSides;i++) tag[i]=true;
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
			  Iterator<SideDescription> sp=packData.getSidePairs().iterator();
			  SideDescription ep=null;
			  RedEdge rlst=null;
			  int tick=0;
			  while (sp.hasNext()) {
				  ep=(SideDescription)sp.next();
				  if (tag[tick++]) { // yes, do this one
					  rlst=ep.startEdge;
					  int v=rlst.vert(rlst.startIndex);
					  int w=rlst.vert((rlst.startIndex+1)%3);
					  add(new EdgeSimple(v,w));
					  count++;
					  while (rlst!=ep.endEdge) {
						  rlst=rlst.nextRed;
						  v=rlst.vert(rlst.startIndex);
						  w=rlst.vert((rlst.startIndex+1)%3);
						  add(new EdgeSimple(v,w));
						  count++;
					  } 
				  }
			  }
			  break;
			} // end of 'R'
			case 'm': // marked
			{
				boolean not_m=false;
				// 'mc'? want those NOT marked?
				if (str.length()>1 && str.charAt(1)=='c') not_m=true;
				int m,w;
				for (int v=1;v<=packData.nodeCount;v++) {
					m=packData.getVertMark(v);
					if ((not_m && m==0) || (!not_m && m!=0)) { // this end is marked
						int[] petals=packData.getPetals(v);
						for (int j=0;j<petals.length;j++) {
							w=petals[j];
							m=packData.getVertMark(w);
							// add only if w>v
							if (w>v && ((not_m && m==0) || (!not_m && m!=0))) {
								add(new EdgeSimple(v,w));
								count++;
							}
						}
					}
				}
				break;
			}
			case 'o': // edges having non-trivial overlaps
			{
				if (!packData.overlapStatus) break; 
				int w;
				for (int v=1;v<=packData.nodeCount;v++) {
					int[] petals=packData.getPetals(v);
					for (int j=0;j<petals.length;j++) {
						w=petals[j];
						// add only if w>v
						if (w>v && Math.abs(packData.getInvDist(v,petals[j])-1.0)>PackData.TOLER) {
							add(new EdgeSimple(v,w));
							count++;
						}
					}
				}
				break;
			}
			case 'e': // find edges, 'ee' hex-extended edges, or 'eh' 
				// hex extrapolated, loop from vertlist.
				/* attempt to make edgelist from raw vert list. Move down the list
				 * and see if next vert (ignoring repeats) is neighbor. If yes, 
				 * add to edge list. Disregard duplication, eat rest of 'items'. 
				 * 'ee' form means to look for 'hex extended' edges, 'eh' means to 
				 * use 'hex_extrapolate' and get hex loop of edges. */
			{
				if (str.length()>=2 && str.charAt(1)=='h') { // hex extrapolated (as in 'NodeLink')
					// need just first edge to get started
					EdgeLink edgelist=new EdgeLink(packData,items);
					its=null; // eat rest of items
					if (edgelist==null || edgelist.size()==0) break;
					EdgeSimple edge=(EdgeSimple)edgelist.get(0);
					int v=edge.v;
					int w=edge.w;
					int indx=-1;
					// v, w must be interior and hex and form an edge
					if (packData.isBdry(v) || packData.countFaces(v)!=6
							|| packData.isBdry(w) || packData.countFaces(w)!=6
							|| (indx=packData.nghb(v,w))<0) break; // no hex edges to w
					EdgeLink hex_loop=packData.hex_extrapolate(v,indx,v,1025);
					if (hex_loop==null || hex_loop.size()==0) break;
					count+=hex_loop.size();
					addAll(hex_loop);
				}
				else {
					boolean extended=false;
					if (str.length()>=2 && str.charAt(1)=='e') extended=true;
					NodeLink vertlist=new NodeLink(packData,items);
					if (vertlist==null || vertlist.size()==0) break;
					its=null; // eat rest of 'items'
					count+=abutMore(verts2edges(packData,vertlist,extended));
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
				String strr=null;
				NodeLink vlist=null;
				int []crit;
				int []deg;
				try {
					int N=items.size();
					if (N<2)
						throw new ParserException("example: -d >5 6 [<6] [?]");
					crit=new int[4]; // holds the 4 criteria  
					crit[2]=crit[3]=-1;
					deg=new int[4]; // holds the 2, 3, or 4 degrees
					if (N>4) N=4;
					for (int i=0;i<N;i++) {
						strr=new String(items.get(i)).trim();
						char c=strr.charAt(0);
						if (c=='g' || c=='G') { // greater than
							crit[i]=2;
							strr=strr.substring(1);
							deg[i]=MathUtil.MyInteger(strr);
							if (i==0) {
								vlist=new NodeLink(packData,new String("{c:d.gt."+deg[i]+"}"));
							}
						}
						else if (c=='l' || c=='L') { // less than
							crit[i]=1;
							strr=strr.substring(1);
							deg[i]=MathUtil.MyInteger(strr);
							if (i==0) {
								vlist=new NodeLink(packData,new String("{c:d.lt."+deg[i]));
							}
						}
						else if (c=='?') { // anything
							crit[i]=-1;
							if (i==0) {
								vlist=new NodeLink(packData,"a");
							}
						}
						else {
							crit[i]=0; // equality
							deg[i]=Integer.parseInt(strr);
							if (i==0) {
								vlist=new NodeLink(packData,new String("{c:d.eq."+deg[i]+"}"));
							}
						}
					} 
				} catch (Exception ex) {
					throw new ParserException("example: d g5 6 l6 ?");
				}
				if (vlist==null || vlist.size()==0)
					return 0;

				// vlist has the vertices satisfying the first criterion
				// find edges with other end satisfying the second criterion
				EdgeLink elist=new EdgeLink(packData);
				Iterator<Integer> vlst=vlist.iterator();
				while (vlst.hasNext()) {
					int v=vlst.next();
					int[] petals=packData.getPetals(v);
					for (int j=0;j<petals.length;j++) {
						int w=petals[j];
						int wdeg=packData.countFaces(w)+packData.getBdryFlag(w);
						boolean incld=false;
						if (crit[1]<0)
							incld=true;
						else if (crit[1]==2 && wdeg>deg[1]) 
							incld=true;
						else if (crit[1]==1 && wdeg<deg[1]) 
							incld=true;
						else if (crit[1]==0 && wdeg==deg[1]) 
							incld=true;
						if (incld && w>v)
							elist.add(new EdgeSimple(v,w));
						else if (incld && elist.isThereVW(v,w)<0) // if w < v, is edge already included?
							elist.add(new EdgeSimple(v,w));
					}
				} // end of while
				if (elist==null || elist.size()==0)
					return 0;
				
				// go through elist, select on remaining two criteria
				// can one/both be anything (or nothing)?
				if (crit[2]==-1) { // swap so crit[3] is open one
					crit[2]=crit[3];
					crit[3]=-1;
					int h=deg[2];
					deg[2]=deg[3];
					deg[3]=h;
				}

				// go through, add qualifiers to 'this'
				Iterator<EdgeSimple> elst=elist.iterator();
				while (elst.hasNext()) {
					boolean okay=false;
					EdgeSimple edge=elst.next();
					if (crit[2]<0 && crit[3]<0) // open to all (or nothing)
						okay=true;
					else {
						int v=edge.v;
						int w=edge.w;
						int s=-1;
						int t=-1;
						int k=packData.nghb(v,w);
						if (!packData.isBdry(v) || k<packData.countFaces(v)) {
							int[] vflower=packData.getFlower(v);
							s=vflower[(k+1)%packData.countFaces(v)];
						}
						k=packData.nghb(w,v);
						if (!packData.isBdry(w) || k<packData.countFaces(w)) {
							int[] wflower=packData.getFlower(w);
							t=wflower[(k+1)%packData.countFaces(w)];
						}
						if (s==-1 && t==-1) 
							throw new CombException("no common verts to edge "+v+" "+w);
						int sdeg,tdeg;
						if (s==-1) {
							s=t;
							t=-1;
							sdeg=packData.countFaces(s)+packData.getBdryFlag(s);
							tdeg=-1;
						}
						else {
							sdeg=packData.countFaces(s)+packData.getBdryFlag(s);
							tdeg=packData.countFaces(t)+packData.getBdryFlag(t);
						}

						// bdry edge?
						if (tdeg==-1) {
							if (crit[3]==-1) { // only one crit; check both s and t
								if (crit[2]==2 && (sdeg>deg[2] || tdeg>deg[2]))	
										okay=true;
								else if (crit[2]==1 && (sdeg<deg[2] || tdeg<deg[2]))	
										okay=true;
								else if (crit[2]==0 && (sdeg==deg[2] || tdeg==deg[2]))	
										okay=true;
							}
						}
						else { // not bdry edge; s must satisfy one, t the other
							// check if either satifies crit[2]
							int s_or_t=0; // s or t (resp. -1 or 1)
							if (crit[2]==2) {
								if (sdeg>deg[2]) 
									s_or_t=-1;
								else if (tdeg>deg[2]) 
									s_or_t=1;
							}
							else if (crit[2]==1) {
								if (sdeg<deg[2]) 
									s_or_t=-1;
								else if (tdeg<deg[2]) 
									s_or_t=1;
							}
							else if (crit[2]==0) {
								if (sdeg==deg[2]) 
									s_or_t=-1;
								else if (tdeg==deg[2]) 
									s_or_t=1;
							}
							int loser=0;
							int loserDeg=0;
							if (s_or_t==-1) { // winner is s
								loser=t;
								loserDeg=tdeg;
							}
							else if (s_or_t==1) { // winner is t
								loser=s;
								loserDeg=sdeg;
							}
							
							// do we have a winner with crit[2]?
							//    then check other against crit[3]
							if (loser!=0) {
								if (crit[3]==-1 || 
										(crit[3]==2 && loserDeg>deg[3]) ||
										(crit[3]==1 && loserDeg<deg[3]) ||
										(crit[3]==0 && loserDeg==deg[3]))
									okay=true;
							}
						}
					}
					if (okay) {
						add(edge);
						count ++;
					}
				} // end of while loop through elist
				while (its.hasNext()) its.next(); // eating rest of 'items'
				break;
			}
			case 'I': // incident to vertices/edges/faces; redundancies not checked
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
					if (vertlist==null || vertlist.size()==0) break;
					int v;
					int []vs=new int[packData.nodeCount+1];
					Iterator<Integer> vlist=vertlist.iterator();
					// Searching vertlist is too slow; create temp data array.
					while (vlist.hasNext()) {
						v=(Integer)vlist.next();
						vs[v]=1;
					}
					vlist=vertlist.iterator();
					while (vlist.hasNext()) {
						v=(Integer)vlist.next();
						int[] petals=packData.getPetals(v);
						for (int j=0;j<petals.length;j++) {
							int w=petals[j];
							if (!bothvw || (v<w && vs[w]==1)) {
								add(new EdgeSimple(v,w));
								count++;
							}
						}
					}
					break;
				}
				case 'f': // add three edges from given faces
				{
					FaceLink facelist=new FaceLink(packData,items);
					its=null; // eat rest of items
					if (facelist==null || facelist.size()==0) break;
					Iterator<Integer> flist=facelist.iterator();
					Face face=null;
					while (flist.hasNext()) {
						face=(Face)packData.faces[(Integer)flist.next()];
						for (int j=0;j<3;j++) {
							add(new EdgeSimple(face.vert[j],face.vert[(j+1)%3]));
							count++;
						}
					}
					break;
				}
				case 'e': // share an end with given edges
				{
					EdgeLink edgelist=new EdgeLink(packData);
					its=null; // eat rest of items
					if (edgelist==null || edgelist.size()==0) break;
					Iterator<EdgeSimple> elist=edgelist.iterator();
					EdgeSimple edge=null;
					int v,w;
					while (elist.hasNext()) {
						edge=(EdgeSimple)elist.next();
						v=edge.v;
						int[] petals=packData.getPetals(v);
						for (int j=0;j<petals.length;j++) {
							w=petals[j];
							if (w!=edge.w) add(new EdgeSimple(v,w));
							count++;
						}
						w=edge.w;
						petals=packData.getPetals(w);
						for (int j=0;j<petals.length;j++) {
							v=petals[j];
							if (v!=edge.v) add(new EdgeSimple(w,v));
							count++;
						}
					}
					break;
				}
				} // end of switch
				break;
			}
			case 't': // associated with dual 'PlotTree' from drawingorder
			{
				GraphLink dlink=DualGraph.plotTree(packData);
				count +=dlink.size();
				abutMore(packData.reDualEdges(dlink));
				break;
			}
			case 'z': // closest edge to complex number z
			case 'Z': // for sphere, use actual (theta,phi)	
			{
				try {
					Complex z=new Complex(Double.parseDouble((String)its.next()),
							Double.parseDouble((String)its.next()));
					if (packData.hes>0 && str.charAt(0)=='z') {
						z=SphView.visual_plane_to_s_pt(z);
						z=packData.cpScreen.sphView.toRealSph(z);
					}
					EdgeLink zsearch=packData.edge_search(z);
					Iterator<EdgeSimple> nl=zsearch.iterator();
					while (nl.hasNext()) {
						add((EdgeSimple)nl.next());
						count++;
					}
				} catch(Exception ex) {}
				break;
			}
			case 'G': // edgelist approximating the given curve (x,y),...
			{
				if (packData.hes!=0) {
					throw new ParserException("'G' option only in euclidean cases");
				}
				int startVert=0;
				// option 'Gv': start with given vert
				if (str.length()>1 && str.charAt(1)=='v') {
					try {
						startVert=Integer.parseInt((String)items.remove(0));
						if (startVert<1 || startVert>packData.nodeCount)
							throw new ParserException("usage: Gf <v>");
					} catch (Exception ex) {
						throw new ParserException(ex.getMessage());
					}
				}
			
				try { // should default to current 'ClosedPath', if it exists
					PathLink pLink=new PathLink(packData.hes,items);
					PathInterpolator pInt=new PathInterpolator(packData.hes);
					pInt.pathInit(pLink);
					count +=this.abutMore(path2edgepath(packData,pInt,startVert));
				} catch (Exception ex) {
					throw new ParserException("failed to get or convert path");
				}
				break;
			}
			// TODO: should we accept non-neighbors????
			case 'P': // edges from 'poisonEdges'
			{
				if (packData.poisonEdges!=null && packData.poisonEdges.size()>0) {
					Iterator<EdgeSimple> elist=packData.poisonEdges.iterator();
					while (elist.hasNext()) {
						add((EdgeSimple)elist.next());
					}
				}
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
				EdgeLink elist=new EdgeLink(packData,items);
				Iterator<EdgeSimple> eit=elist.iterator();
				while (eit.hasNext()) {
					EdgeSimple edge=eit.next();
					double verr=QualMeasures.edge_vis_error(packData, edge);
					if (verr>thresh) {
						add(edge);
						count++;
					}
				}
				break;
			}
			default: // if nothing else, see if there is an edge (or extended edges)
			{
				EdgeSimple edge=null;
				int v,w;
				try{
					if ((v=MathUtil.MyInteger(str))>0 && its.hasNext() 
							&& (w=MathUtil.MyInteger((String)its.next()))>0) {
						if (xtd) { // allow extended edges
							if (packData.packDCEL!=null) {
								HalfLink hlink=CombDCEL.shootExtended(
										packData.packDCEL.vertices[v],w,
										XTD_LINKS,true);
								if (hlink!=null && hlink.size()>0) {
									this.abutHalfLink(hlink);
									count +=hlink.size();
								}
							}
							
							// traditional
							else {
								EdgeLink newlinks=packData.get_extended_edge(v,w,XTD_LINKS);
								if (newlinks!=null && newlinks.size()>0) {
									this.addAll(newlinks);
									count++;
								}
							}
						}
						else if (packData.nghb(v, w)>=0) {
							add(new EdgeSimple(v,w));
							count++;
						}
					}
				} catch (NumberFormatException nfe) {}
			}
				
			} // end of switch
			} // end of else
		} // end of main 'while'
		return count;
	}
	
	/**
	 * Make a distinct copy of this linked list, checking against
	 * the current edgelist's packData setting.
	 * @return new @see EdgeLink
	 */
	public EdgeLink makeCopy() {
		Iterator<EdgeSimple> elist=this.iterator();
		EdgeLink newlist=new EdgeLink(packData);
		while (elist.hasNext()) {
			newlist.add(new EdgeSimple(elist.next()));
		}
		return newlist;
	}
	
	/**
	 * Abut an 'EdgeLink' to the end of this one.
	 * @param moreEL
	 * @return count of new edges (some may be improper, some redundant)
	 */
	public int abutMore(EdgeLink moreEL) {
		if (moreEL==null || moreEL.size()==0)
			return 0;
		int ticks=0;
		Iterator<EdgeSimple> mit=moreEL.iterator();
		EdgeSimple edge=null;
		while (mit.hasNext()) {
			edge=(EdgeSimple)mit.next();
			add(new EdgeSimple(edge.v,edge.w));
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Abut a 'HalfLink' to the end of this one.
	 * @param hlink HalfLink
	 * @return count
	 */
	public int abutHalfLink(HalfLink hlink) {
		if (hlink==null || hlink.size()==0)
			return 0;
		int ticks=0;
		Iterator<HalfEdge> hit=hlink.iterator();
		EdgeSimple edge=null;
		while (hit.hasNext()) {
			HalfEdge he=(HalfEdge)hit.next();
			add(new EdgeSimple(he.origin.vertIndx,he.twin.origin.vertIndx));
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Given v, return w if this list contains (v,w) (use first occurrence).
	 * Return -1 if not found.
	 * @param v
	 * @return w or -1
	 */
	public int findW(int v) {
		Iterator<EdgeSimple> ed=this.iterator();
		EdgeSimple edge=null;
		while (ed.hasNext()) {
			edge=(EdgeSimple)ed.next();
			if (edge.v==v) return edge.w;
		}
		return -1;
	}
	
	/**
	 * Given w, return v if this list contains (v,w) (use first occurrence).
	 * Return -1 if not found.
	 * @param w
	 * @return v or -1
	 */
	public int findV(int w) {
		Iterator<EdgeSimple> ed=this.iterator();
		EdgeSimple edge=null;
		while (ed.hasNext()) {
			edge=(EdgeSimple)ed.next();
			if (edge.w==w) return edge.v;
		}
		return -1;
	}
	
	/**
	 * Given NodeLink and EdgeLink, return NodeLink of entries v so (v,w)
	 * is in EdgeLink for some w in NodeLink (without repeats)
	 * @param el, EdgeLink
	 * @param nl, NodeLink
	 * @return NodeLink or null if no instances found
	 */
	public static NodeLink findAllV(EdgeLink el,NodeLink nl) {
		if (el==null || el.size()==0 || nl==null || nl.size()==0) 
			return null;
		NodeLink vlist=new NodeLink((PackData)null);
		Iterator<Integer> nlk=nl.iterator();
		while (nlk.hasNext()) {
			int w=nlk.next();
			Iterator<EdgeSimple> elk=el.iterator();
			while (elk.hasNext()) {
				EdgeSimple edge=elk.next();
				if (edge.w==w && vlist.containsV(edge.v)<0)
					vlist.add(edge.v);
			}
		}
		if (vlist==null || vlist.size()==0)
			return null;
		return vlist;
	}

	/**
	 * Given NodeLink and EdgeLink, return NodeLink of entries w so (v,w)
	 * is in EdgeLink for some v in NodeLink (without repeats)
	 * @param el, EdgeLink
	 * @param nl, NodeLink 
	 * @return NodeLink or null if no instances found
	 */
	public static NodeLink findAllW(EdgeLink el,NodeLink nl) {
		if (el==null || el.size()==0 || nl==null || nl.size()==0) 
			return null;
		NodeLink vlist=new NodeLink((PackData)null);
		Iterator<Integer> nlk=nl.iterator();
		while (nlk.hasNext()) {
			int v=nlk.next();
			Iterator<EdgeSimple> elk=el.iterator();
			while (elk.hasNext()) {
				EdgeSimple edge=elk.next();
				if (edge.v==v && vlist.containsV(edge.w)<0)
					vlist.add(edge.w);
			}
		}
		if (vlist==null || vlist.size()==0)
			return null;
		return vlist;
	}

	/**
	 * Is <v,w> an edge in the list?
	 * @param v int
	 * @param w int
	 * @return int, first index for edge or -1 if not found
	 */
	public int isThereVW(int v,int w) {
		for (int j=0;j<this.size();j++) {
			EdgeSimple edge=this.get(j);
			if (edge.v==v && edge.w==w) return j;
		}
		return -1;
	}
	
	/** 
	 * count unoriented edges, without repeats.
	 */
/* TODO: need to finish this, counting is tough 
	public static int countMe(EdgeLink el) {
		int count=0;
		if (el.size()==0) return count;
		int max=1;
		if (el.packData!=null) max=el.packData.nodeCount;
		// if no packing, have to calculate max
		else {
			Iterator<EdgeSimple> it=el.iterator();
			while (it.hasNext()) {
				EdgeSimple es=it.next();
				max=(es.v>max) ? es.v:max;
				max=(es.w>max) ? es.w:max;
			}
		}
		// for each v, first find max of w's
		int []vMaxs=new int[max+1];
		Iterator<EdgeSimple> it=el.iterator();
		while (it.hasNext()) {
			EdgeSimple es=it.next();
			int v=es.v;
			int w=es.w;
			if (w<v) { // swap, so smallest is first
				int hold=v;
				v=w;
				w=hold;
			}
			vMaxs[v]=max+1;
			// for packings, will check flower index of neighbor
			if (el.packData!=null) 
				vMaxs[v]=el.packData.getNum(v)+el.packData.getBdryFlag(v);
		}
		
		
		return count;
	}
*/
	
	/**
	 * Rotate EdgeLink so it starts with 'indx'.
	 * @param link @see EdgeLink
	 * @param indx new starting index
	 * @return @see EdgeLink, null if empty or on error.
	 */
	public static EdgeLink rotateMe(EdgeLink link,int indx) {
		int sz=link.size();
		if (link==null || sz<=indx)
			return null;
		EdgeLink nlink=new EdgeLink();
		int i=indx;
		while (i<sz) { // 
			nlink.add(link.get(i));
			i++;
		}
		i=0;
		while (i<=indx) {
			nlink.add(link.get(i));
			i++;
		}
		return nlink;
	}	
	
	/**
	 * Return a new 'EdgeLink' with same order, but edges 
	 * are each reversed.
	 * @return new 'EdgeLink', possibly empty.
	 */
	public EdgeLink flipEachEntry() {
		EdgeLink rL=new EdgeLink(packData);
	    if (this.size()==0) return rL;
		Iterator<EdgeSimple> el=this.iterator();
		EdgeSimple edge=null;
		while (el.hasNext()) {
			edge=(EdgeSimple)el.next();
			super.add(new EdgeSimple(edge.w,edge.v));
		}
		return rL;
	}
	
	/**
	 * Return a new 'EdgeLink' whose order is the reverse of this,
	 * and whose edges are reverse of this.
	 * @return new 'EdgeLink', possibly empty
	 */
	public EdgeLink reverseMe() {
	    EdgeLink qtmp=new EdgeLink(packData);
	    if (this.size()==0) return qtmp;
	    EdgeSimple edge=null;
	    Iterator<EdgeSimple> it=this.iterator();
	    while (it.hasNext()) {
	    	edge=(EdgeSimple)it.next();
	    	qtmp.add(0,new EdgeSimple(edge.w,edge.v));
	    }
	    return qtmp;
	}

	/**
	 * Given 'VertexMap' with <old, new>, translate 'elink' 
	 * from old to the new indices.
	 * @param oldnew VertexMap
	 * @return new EdgeLink (with null PackData)
	 */
	public static EdgeLink translate(EdgeLink elink,VertexMap oldnew) {
	    EdgeLink qtmp=new EdgeLink();
	    if (elink.size()==0) 
	    	return qtmp;
	    EdgeSimple edge=null;
	    Iterator<EdgeSimple> it=elink.iterator();
	    while (it.hasNext()) {
	    	edge=(EdgeSimple)it.next();
	    	int v=oldnew.findW(edge.v);
	    	int w=oldnew.findW(edge.w);
	    	if (v>0 && w>0)
	    		qtmp.add(new EdgeSimple(v,w));
	    }
	    return qtmp;
	}

	/**
	 * Return a combinatorial geodesic between two sets of
	 * vertices. This can fail in various ways, so exceptions
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
	 * @return EdgeLink, null when empty.
	 */
	public static EdgeLink getCombGeo(PackData p,
			NodeLink seeds,NodeLink targets,NodeLink nonos) 
					throws CombException {
		int []book=new int[p.nodeCount+1];
		if (seeds==null || seeds.size()==0 
				|| targets==null || targets.size()==0) 
			throw new CombException("no 'seeds' and/or no 'targets'");
		int maxdist=p.nodeCount;
		
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
		int safty=p.nodeCount*10;
		boolean hit=true;
		int theOne=0;
		while (hit && (safty--)>0 && currgen<maxdist) {
			hit=false;
			currgen++; 
			nextGen=new NodeLink(p);
			Iterator<Integer> prevG=prevGen.iterator();
			while (prevG.hasNext() && safty>0) {
				int v=prevG.next();
				int[] petals=p.getPetals(v);
				for (int j=0;j<petals.length;j++) {
					int k=petals[j];
					
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
						maxdist=currgen;  // won't need to search further, but continue to find competitors
						if (theOne==0)
							theOne=k;
					}
				}
			} // inner while
			safty--;
			prevGen=nextGen;
		} // outer while
		if (safty<=0) {
			throw new CombException("problem creating combinatorial geodesic");
		}
		if (currgen<maxdist) {
			throw new CombException("'seed' and 'target' separated, no geodesic");
		}
	
		// create the edge path from 'theOne' to some seed
		EdgeLink elink=new EdgeLink(p);
		int start=theOne;
		int gen=book[theOne]-1;
		while (gen>0) {
			int ed=-1;
			int[] petals=p.getPetals(start);
			for (int j=0;j<petals.length;j++) {
				int k=petals[j];
				if (book[k]==gen) {
					ed=k;
					j=p.nodeCount+1; // kick out
				}
			}
			if (ed==-1)
				throw new CombException("error in generation "+gen);
			elink.add(start,ed);
			start=ed;
			gen--;
		}
		
		if (elink.size()==0) 
			throw new CombException("Failed to get comb geodesic");
		return elink.reverseMe();
	}

	/**
	 * Pick first edge off a string. Return null on failure.
	 * @param p PackData
	 * @param str String
	 * @return EdgeSimple, null if none found
	 */
	public static EdgeSimple grab_one_edge(PackData p,String str) {
//		int idx=str.trim().indexOf(' ');
//		if (idx>0) {
//			str=str.substring(0,idx+1);
//		}
		EdgeLink elist=new EdgeLink(p,str);
		if (elist.size()>0) {
			EdgeSimple edge=(EdgeSimple)elist.get(0);
			return new EdgeSimple(edge.v,edge.w);
		}
		return null;
	}
		
	/**
	 * Pick first edge off a string. Return null on failure.
	 * @param p PackData
	 * @param flagsegs Vector<Vector<String>>
	 * @return EdgeSimple, null if none found
	 */
	public static EdgeSimple grab_one_edge(PackData p,Vector<Vector<String>> flagsegs) {
		String str=StringUtil.reconstitute(flagsegs);
		return grab_one_edge(p,str);
	}
	
	/**
	 * Given a vertex list, convert it (to the extent possible) to an edge list.
	 * If 'extended' flag true, do "hex-extended" edges, which pass through
	 * interior vertices so same number of edges are on each side. See 
	 * 'axis_extend' call. Else, convert to edge geodesic.
	 * @param p PackData
	 * @param vlist NodeLink
	 * @param hexflag boolean, true, hex extend
	 * @return EdgeLink, possibly empty or null
	 */
	public static EdgeLink verts2edges(PackData p,NodeLink vertlist,boolean hexflag) {
		EdgeLink ans=new EdgeLink(p);
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
				if (hexflag) { // look for/use axis-extended edges
					if (p.packDCEL!=null) {
						HalfEdge petal=p.packDCEL.findHalfEdge(endv,nextv);
						if (petal!=null)
							ans.add(HalfEdge.getEdgeSimple(petal));
						else {
							Vertex basevert=p.packDCEL.vertices[endv];
							HalfLink hlink=
								CombDCEL.shootExtended(basevert,nextv,16,hexflag);
							if (hlink!=null && hlink.size()>0) 
								ans.abutHalfLink(hlink);
						}
					}
					
					// traditional
					else {
						if (p.nghb(nextv,endv)>=0)
							ans.add(new EdgeSimple(nextv,endv));
						else {
							int dir=0;
							if ((dir=p.axis_extend(endv,nextv,16))>=0) {
								EdgeLink newedges=p.axis_extrapolate(endv, dir,nextv,16);
								if (newedges!=null && newedges.size()>0)  
									ans.addAll(newedges);
							}
						}
					}
				}
				else if (p.nghb(endv,nextv)>=0) {
					ans.add(new EdgeSimple(endv,nextv));
				}
				
				// TODO: do I want to default to this? have to do DCEL versin
				else { 
					ans.abutMore(EdgeLink.getCombGeo(p, new NodeLink(p,endv),new NodeLink(p,nextv),null));
				}
				endv=nextv;
			}
		} // end of while
		return ans;
	}
	
	/** 
     * Check if edge {v,w} (or {w,v}) is in given edge list.
    */
    public static boolean ck_in_elist(EdgeLink edgelist,int v,int w) {
    	if (edgelist==null || edgelist.size()==0) 
    		return false;
    	Iterator<EdgeSimple> elist=edgelist.iterator();
    	EdgeSimple edge=null;
    	while (elist.hasNext()) {
    		edge=(EdgeSimple)elist.next();
            if ((edge.v==v && edge.w==w) || (edge.v==w && edge.w==v) )
              	return true;
    	}
    	return false;
    }
    
    /**
     * Find index of <v,w> or <w,v> in the list
     * @param edgelist EdgeLink
     * @param v int
     * @param w int
     * @return -1 on error
     */
    public static int getVW(EdgeLink edgelist,int v,int w) {
    	if (v>w) {
    		int hold=w;
    		w=v;
    		v=hold;
    	}
    	for (int i=0;i<edgelist.size();i++) {
    		EdgeSimple edge=edgelist.get(i);
    		if (edge.v==v && edge.w==w)
    			return i;
    	}
    	return -1;
    }
    
    /**
     * Return random entry from edgelist; caution, does not adjust
     * for repeat entries.
     * @param edgelist
     * @return null on empty list
     */
    public static EdgeSimple randEdge(EdgeLink edgelist) {
    	if (edgelist==null || edgelist.size()==0) return null;
    	int n=new Random().nextInt(edgelist.size());
    	EdgeSimple edge=edgelist.get(n);
    	return new EdgeSimple(edge.v,edge.w);
    }

    /**
     * Convert a polygonal path into a linked list of edges; first convert
     * to a 'FaceLink', then go down the right side of this chain of faces.
     * May have a preferred starting vertex. 
     * TODO: currently, eucl only
     * @param p
     * @param pInt, PathInterpolator
     * @param startVert (or 0 if non specified)
     * @return
     */
	public static EdgeLink path2edgepath(PackData p,PathInterpolator pInt,int startVert) {
		FaceParam startFP=FaceLink.pathProject(p,pInt,startVert);
		if (startFP==null || startFP.next==null) return null;
		int startFace=startFP.face;
		FaceParam ftrace=startFP;
		while (ftrace.next!=null) ftrace=ftrace.next;
		int endFace=ftrace.face;
		
		// get start/end complex points
		Complex startZ=pInt.sToZ(0.0);
		Complex endZ=null;
		if (pInt.closed) endZ=new Complex(startZ);
		else endZ=new Complex(pInt.pathZ.lastElement());
		
		// start/end vertices are those closest to startZ/endZ
		double smin=10000.0;
		double emin=10000.0;
		Face sface=p.faces[startFace];
		Face eface=p.faces[endFace];
		int sindx=0;
		int eindx=0;
		double ut=0.0;
		for (int i=0;i<3;i++) {
			ut=startZ.minus(p.getCenter(sface.vert[i])).abs();
			if (ut<smin) {
				smin=ut;
				sindx=i;
			}
			ut=endZ.minus(p.getCenter(eface.vert[i])).abs();
			if (ut<emin) {
				emin=ut;
				eindx=i;
			}
		}
		int startV=sface.vert[sindx];
		int endV=eface.vert[eindx];
		if (startV==endV) return null;
		
		// Remove any unneeded faces at beginning (i.e, next face has startV)
		while (startFP.next!=null && p.face_index(startFP.next.face,startV)>=0)
			startFP=startFP.next;
		
		// Remove any unneeded faces at end (i.e., previous face has endV)
		ftrace=startFP; 
		while (ftrace!=null && p.face_index(ftrace.face,endV)<0) // find first face containing endV
			ftrace=ftrace.next;
		if (ftrace==null) return null; // should not happen
		// ftrace is first 'FaceParam' whose face contains endV; do the rest?
		boolean done=false;
		FaceParam ntrace=ftrace;
		while (!done) {
			while (ntrace.next!=null && p.face_index(ntrace.next.face,endV)>=0)
				ntrace=ntrace.next;
			if (ntrace.next==null) done=true; 
			else ftrace=ntrace.next; // a face that doesn't have endV
			while (ftrace!=null && p.face_index(ftrace.face,endV)<0) // find next face with endV
				ftrace=ftrace.next; 
			if (ftrace==null) return null; // should not happen
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
					nextedge.v!=edge.w || nextedge.w==edge.v) && ftrace.next!=null) {

				// create putative next edge
				int nt=p.face_nghb(ftrace.face,ftrace.next.face);
				if (nt<0) throw new CombException("broken chain in 'FaceParam' list");
				nextv=p.faces[ftrace.next.face].vert[nt];
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
					&& p.face_index(ftrace.face,endV)>=0 && p.face_index(ftrace.face,v)>=0) {
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
	  * Does the given list of edges have vertices that separate the complex? 
	  * If the return is 0, then answer is NO.
	  * @param p
	  * @param edgelist, EdgeLink
	  * @return lowest index of circles not reachable from first non-green,
	  * else 0, and complex is NOT separated.
	  *   
	  */
	 public static int separates(PackData p,EdgeLink edgelist) {
		 NodeLink nodes=new NodeLink(p);
		 Iterator<EdgeSimple> elst=edgelist.iterator();
		 while (elst.hasNext()) {
			 EdgeSimple edge=elst.next();
			 nodes.add(edge.v);
			 nodes.add(edge.w);
		 }
		 return NodeLink.separates(p,nodes);
	 }
	 
	 /**
	  * Create a new EdgeLink that eliminates duplicate edges.
	  * @param el
	  * @param orient boolean, true, then take account of orientation
	  * @return new EdgeLink
	  */
	 public static EdgeLink removeDuplicates(EdgeLink el,boolean orient) {
		 EdgeLink newEL=new EdgeLink(el.packData);
		 Iterator<EdgeSimple> els=el.iterator();
		 while (els.hasNext()) {
			 EdgeSimple edge=els.next();
			 if (newEL.containsVW(edge, orient))
				 continue;
			 newEL.add(edge);
		 }
		 return newEL;
	 }
	 
	 /**
	  * Check if this list contains (v,w) ( or (w,v) if orient=false)
	  * @param v int
	  * @param w int
	  * @param orient boolean, true, enforce orientation
	  * @return boolean
	  */
	 public boolean containsVW(int v,int w,boolean orient) {
		 return containsVW(new EdgeSimple(v,w),orient);
	 }
	 
	 /**
	  * Check if this list contains (v,w) ( or (w,v) if orient=false)
	  * @param edge EdgeSimple
	  * @param orient boolean, true, enforce orientation
	  * @return boolean
	  */
	 public boolean containsVW(EdgeSimple edge,boolean orient) {
		 Iterator<EdgeSimple> els=this.iterator();
		 while (els.hasNext()) {
			 if (els.next().isEqual(edge,orient))
				 return true;
		 }
		 return false;
	 }
	 
	 public void swapVW(int v,int w) {
		 if (v==w) 
			 return;
		Iterator<EdgeSimple> etrace = this.iterator();
		while (etrace.hasNext()) {
			EdgeSimple es = (EdgeSimple) etrace.next();
			if (es.v == v)
				es.v = w;
			else if (es.v == w)
				es.v = v;
			if (es.w == v)
				es.w = w;
			else if (es.w == w)
				es.w = v;
		}
	 }

	 /**
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }
	 
	 /**
	  * Clone with the same 'PackData'
	  */
	 public EdgeLink clone() {
		 EdgeLink el=new EdgeLink();
		 el.packData=packData;
		 Iterator<EdgeSimple> tis=this.iterator();
		 while (tis.hasNext()) 
			 el.add(tis.next().clone());
		 return el;
	 }
	 
	/**
	 * Create a list of entries as a string
	 * @return String, null on error
	 */
	public String toString() {
		if (this.size()==0)
			return null;
		StringBuilder sb=new StringBuilder();
		Iterator<EdgeSimple> myit=this.iterator();
		while (myit.hasNext()) {
			EdgeSimple edge=myit.next();
			sb.append(" "+edge.v+" "+edge.w);
		}
		return sb.toString();
	}
		
	 
	/**
	 * Make up list by looking through SetBuilder specs 
	 * (from {..} set-builder notation). Use 'tmpUtil' to 
	 * collect information before creating the HalfLink for 
	 * return. (Have not yet implemented edge selections)
	 * @param p PackData (with DCEL structure)
	 * @param specs Vector<SelectSpec>
	 * @return HalfLink list of specified edges
	 */
	public static HalfLink edgeSpecs(PackData p,Vector<SelectSpec> specs) {
		if (p.packDCEL==null) {
			throw new DCELException("'edgeSpecs' require a DCEL structure");
		}
		if (specs==null || specs.size()==0) 
			return null;
		SelectSpec sp=null;
		int count=0;
			// will store results in 'eutil'
		
		int[] tmpUtil=new int[p.packDCEL.edgeCount+1];
		// loop through all the specifications: these should alternate
		//   between 'specifications' and 'connectives', starting 
		//   with the former, although typically there will be just 
		//   one specification in the vector and no connective.
		UtilPacket uPx=null;
		UtilPacket uPy=null;
		boolean isAnd=false; // true for '&&' connective, false for '||'.
		for (int j=0;j<specs.size();j++) {
			sp=(SelectSpec)specs.get(j);
			if (sp.object!='e') 
				throw new ParserException(); // spec must be edges/halfedges
			try {
				for (int e=1;e<=p.packDCEL.edgeCount;e++) {
					
					// success?
					boolean outcome=false;
					uPx=sp.node_to_value(p,e,0);
					if (sp.unary) {
						if (uPx.rtnFlag!=0)
							outcome=sp.comparison(uPx.value,0);
					}
					else {
						uPy=sp.node_to_value(p,e,1);
						if (uPy.rtnFlag!=0)
							outcome=sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && tmpUtil[e]==0) { // 'or' situation
							tmpUtil[e]=1;
							count++;
						}
					}
					else { // no, fails this condition
						if (isAnd && tmpUtil[e]!=0) { // 'and' situation
							tmpUtil[e]=0;
							count--;
						}
					}
				}
			} catch (Exception ex) {
				throw new ParserException();
			}
			
			// if specs has 2 or more additional specifications, the next must
			//    be a connective. Else, finish loop.
			if ((j+2)<specs.size()) {
				sp=(SelectSpec)specs.get(j+1);
				if (!sp.isConnective) 
					throw new ParserException();
				isAnd=sp.isAnd; 
				j++;
			}
			else j=specs.size(); // kick out of loop
		}
	
		if (count>0) {
			HalfLink hl=new HalfLink(p);
			for (int e=1;e<=p.packDCEL.edgeCount;e++)
				if (tmpUtil[e]!=0) 
					hl.add(p.packDCEL.edges[e]);
			return hl;
		}
		else 
			return null;
	}
		
}
