package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import complex.Complex;
import dcel.CombDCEL;
import dcel.D_SideData;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedHEdge;
import dcel.Vertex;
import exceptions.CombException;
import exceptions.ParserException;
import komplex.EdgeSimple;
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
		if ((edge.v>0 && edge.w>0) && edge.v<=packData.nodeCount && 
				edge.w<=packData.nodeCount) 
			return super.add(edge);
		else return false;
	}
	
	public boolean add(HalfEdge edge) {
		if (edge==null)
			return false;
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		if (packData==null)
			return super.add(new EdgeSimple(v,w));
		if ((v>0 && w>0) && v<=packData.nodeCount && w<=packData.nodeCount) 
			return super.add(new EdgeSimple(v,w));
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
				&& v<=packData.nodeCount && w<=packData.nodeCount)) || 
				packData==null)
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
		if (packData==null) 
			return -1;
		int count=0;
		int nodeCount=packData.nodeCount;
		
		// If a single numerical vertex is given, then
		//   add it's 'halfedge' 
		if (items.size()==1) {
			try {
				int v=Integer.parseInt(items.get(0));
				if (v>0 && v<=packData.nodeCount) {
					Vertex vert=packData.packDCEL.vertices[v];
					int w=vert.halfedge.next.origin.vertIndx;
					add(new EdgeSimple(v,w));
					return 1;
				}
				else 
					return -1;
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
							if (edge.v<=packData.nodeCount && 
									edge.w<=packData.nodeCount) {
									add(edge);
									count++;
							}
						}
						else if (brst.startsWith("l")) { // last
							edge=(EdgeSimple)elink.getLast();
							if (edge.v<=packData.nodeCount && 
									edge.w<=packData.nodeCount) {
								add(edge);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<elink.size()) {
									edge=(EdgeSimple)elink.get(n);
									if (edge.v<=packData.nodeCount && 
											edge.w<=packData.nodeCount) {
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
							if (edge.v<=packData.nodeCount && 
									edge.w<=packData.nodeCount) {
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
							if (edge.v<=packData.nodeCount && 
									edge.w<=packData.nodeCount) {
									add(edge);
									count++;
							}
						}
						if (brst.startsWith("l")) { // last
							edge=(EdgeSimple)glink.getLast();
							if (edge.v<=packData.nodeCount && 
									edge.w<=packData.nodeCount) {
								add(edge);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<glink.size()) {
									edge=(EdgeSimple)glink.get(n);
									if (edge.v<=packData.nodeCount && 
											edge.w<=packData.nodeCount) {
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
							if (edge.v<=packData.nodeCount && 
									edge.w<=packData.nodeCount) {
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
				HalfLink newelist=HalfLink.getCombGeo(packData.packDCEL,
						new NodeLink(packData,v),new NodeLink(packData,w),null);
				if (newelist!=null && newelist.size()>0) {
					abutHalfLink(newelist);
					count +=newelist.size();
				}
				return count;
			}
			case 'R': // red edges, outer edges of "sides" of red chain; 
				      // absorb rest of 'items'
			{
			  int numSides=-1;
			  String itstr=null;
			  if (packData.getSidePairs()==null || 
					  (numSides=packData.getSidePairs().size())==0) {
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
			  Iterator<D_SideData> sp=packData.getSidePairs().iterator();
			  D_SideData ep=null;
			  RedHEdge rlst=null;
			  int tick=0;
			  while (sp.hasNext()) {
				  ep=(D_SideData)sp.next();
				  if (tag[tick++]) { // yes, do this one
					  rlst=ep.startEdge;
					  do {
						  add(rlst.myEdge);
						  count++;
						  rlst=rlst.nextRed;
					  } while(rlst!=ep.endEdge.nextRed);
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
							add(new EdgeSimple(v,w));
							count++;
						}
					}
				}
				break;
			}
			case 'e': // find edges: 'ee' for hex-extended edges,
				// or 'eh' for hex-extrapolated
				// do the work in 'HalfLink'
			{
				StringBuilder strbld=new StringBuilder("-"+str+" "+
						StringUtil.reconItem(items));
				HalfLink hlink=new HalfLink(packData,strbld.toString());
				if (hlink==null || hlink.size()==0)
					break;
				count +=abutHalfLink(hlink);
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
								vlist=new NodeLink(packData,
										new String("{c:d.gt."+deg[i]+"}"));
							}
						}
						else if (c=='l' || c=='L') { // less than
							crit[i]=1;
							strr=strr.substring(1);
							deg[i]=MathUtil.MyInteger(strr);
							if (i==0) {
								vlist=new NodeLink(packData,
										new String("{c:d.lt."+deg[i]));
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
								vlist=new NodeLink(packData,
										new String("{c:d.eq."+deg[i]+"}"));
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
						// if w < v, is edge already included?
						else if (incld && elist.isThereVW(v,w)<0) 
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
							throw new CombException(
									"no common verts to edge "+v+" "+w);
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
			case 'I': // incident to verts/edges/faces; redundancies not checked
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
					while (flist.hasNext()) {
						int f=flist.next();
						int[] verts=packData.packDCEL.faces[f].getVerts();
						for (int j=0;j<3;j++) {
							add(new EdgeSimple(verts[j],verts[(j+1)%3]));
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
					throw new ParserException(
							"'G' option only in euclidean cases");
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
					count +=this.abutMore(
							HalfLink.path2edgepath(packData,pInt,startVert));
				} catch (Exception ex) {
					throw new ParserException(
							"failed to get or convert path");
				}
				break;
			}
			// TODO: should we accept non-neighbors????
			case 'P': // edges from 'poisonEdges'
			{
				if (packData.poisonEdges!=null && 
						packData.poisonEdges.size()>0) {
					Iterator<EdgeSimple> elist=packData.poisonEdges.iterator();
					while (elist.hasNext()) {
						add((EdgeSimple)elist.next());
					}
				}
				break;
			}
			case 'q': // quality: edges with visual error worse 
					  //  than given number
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
					HalfEdge edge=eit.next();
					double verr=QualMeasures.edge_vis_error(packData, edge);
					if (verr>thresh) {
						add(edge);
						count++;
					}
				}
				break;
			}
			default: // if nothing else, look for edge (maybe extended edges)
			{
				int v,w;
				try{
					// Note: if <v w> not an edge, may look for hex-extended
					if ((v=MathUtil.MyInteger(str))>0 && its.hasNext() 
							&& (w=MathUtil.MyInteger((String)its.next()))>0) {
						if (xtd) { // allow hex-extended edges
							HalfLink hlink=CombDCEL.shootExtended(
									packData.packDCEL.vertices[v],w,
									XTD_LINKS,true);
							if (hlink!=null && hlink.size()>0) {
								this.abutHalfLink(hlink);
								count +=hlink.size();
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

	 * CAREFUL: convention is that 'vmap' is {new,old} for
	 * translation, so old entry v in 'nlink' and entry 
	 * <v,V> in 'vmap' leads to new entry V. 
	 *  
	 * @param oldnew VertexMap
	 * @return new EdgeLink (with null PackData)
	 */
	public static EdgeLink translate(EdgeLink elink,VertexMap oldnew) {
		if (elink==null || elink.size()==0)
			return null;
	    EdgeLink qtmp=new EdgeLink();
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
	public static EdgeSimple grab_one_edge(PackData p,
			Vector<Vector<String>> flagsegs) {
		String str=StringUtil.reconstitute(flagsegs);
		return grab_one_edge(p,str);
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
	 * @return EdgeLink, possibly empty or null
	 */
	public static EdgeLink verts2edges(PackDCEL pdcel,
			NodeLink vertlist,boolean hexflag) {
		EdgeLink ans=new EdgeLink();
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
						ans.add(HalfEdge.getEdgeSimple(ee));
					else {
						Vertex basevert=pdcel.vertices[endv];
						HalfLink hlink=
							CombDCEL.shootExtended(basevert,nextv,16,hexflag);
						if (hlink!=null && hlink.size()>0) 
							ans.abutHalfLink(hlink);
					}
				}
				else if ((ee=pdcel.findHalfEdge(endv,nextv))!=null) {
					ans.add(HalfEdge.getEdgeSimple(ee));
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
     * traditional my be able to remove
     * Return random entry from edgelist; caution, does not adjust
     * for repeat entries.
     * @param edgelist
     * @return null on empty list
     */
    public static EdgeSimple randEdge(EdgeLink edgelist) {
    	if (edgelist==null || edgelist.size()==0) 
    		return null;
    	int n=new Random().nextInt(edgelist.size());
    	EdgeSimple edge=edgelist.get(n);
    	return new EdgeSimple(edge.v,edge.w);
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
		
}
