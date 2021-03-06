package listManip;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import complex.Complex;
import dcel.D_SideData;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedHEdge;
import dcel.Vertex;
import exceptions.CombException;
import komplex.EdgeSimple;
import komplex.Face;
import packQuality.QualMeasures;
import packing.PackData;
import util.FaceParam;
import util.PathInterpolator;
import util.StringUtil;

/**
 * Linked list for 'HalfEdge's for DCEL sturctures.
 * @author kens, September 2020
 *
 */
public class HalfLink extends LinkedList<HalfEdge> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	static final int XTD_LINKS=16; // how many links to look for in 'extended' edges.

	// Constructors
	public HalfLink(PackData p,HalfEdge edge) {
		super();
		packData=p;
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
		if (items==null || items.size()==0) { // default to 'a' (all edges)
			items=new Vector<String>(1);
			items.add("a");
		}
		addHalfLink(items,xtd);
	}

	/**
	 * not necessarily extended edges
	 * @param p
	 * @param items
	 */
	public HalfLink(PackData p,Vector<String> items) {
		this(p,items,false);
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
		if (packData==null)
			return super.add(edge);
		else return false;
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
	 * Remove occurances of 'edge', irrespective of order
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
	}
	
	/**
	 * Add links to this list (if it is associated with PackData).
	 * Don't have much to do now, since we don't have string representation
	 * of 'HalfEdge's.
	 * @param items Vector<String>
	 * @param xtd boolean, true==>allow 'extended' edges
	 * @return int count
	 */
	public int addHalfLink(Vector<String> items,boolean xtd) {
		int count=0;
		if (packData==null) return -1;

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
				count+=addHalfLink(CPBase.varControl.getValue(str),xtd);
			}
			
			// check for '?list' first
			else if (str.substring(1).startsWith("list")) {

			}
			
			else {
			switch(str.charAt(0)) {
			
			// bdry; check for braces (a,b)
			case 'b':
			{
				// TODO: see 'EdgeLink'
				break;
			}
			case 'g': // combinatorial geodesic path from v to w
			{
				return count;
			}
			case 'R': // designated "sides" of redChain; absorb rest of 'items'
			{
			  int numSides=-1;
			  String itstr=null;
			  if (packData.packDCEL.pairLink==null || (numSides=packData.packDCEL.pairLink.size())==0) {
				  while (its.hasNext()) itstr=(String)its.next(); // toss rest of items
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
			  Iterator<D_SideData> sp=packData.packDCEL.pairLink.iterator();
			  D_SideData ep=null;
			  RedHEdge rlst=null;
			  int tick=0;
			  while (sp.hasNext()) {
				  ep=(D_SideData)sp.next();
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
			case 'm': // marked
			{
				boolean not_m=false;
				// 'mc'? want those NOT marked?
				if (str.length()>1 && str.charAt(1)=='c') not_m=true;
				int m;
				for (int e=1;e<=packData.packDCEL.edgeCount;e++) {
					m=packData.packDCEL.edges[e].mark;
					if ((not_m && m==0) || (!not_m && m!=0)) { // this end is marked
						add(packData.packDCEL.edges[e]);
					}
				}
				break;
			}
			case 'o': // edges having non-trivial overlaps
			{
				for (int e=1;e<=packData.packDCEL.edgeCount;e++) {
					if (packData.packDCEL.edges[e].getInvDist()!=1.0)
						add(packData.packDCEL.edges[e]);
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

				// TODO: see 'EdgeLink'
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

					Iterator<Integer> vlist=vertlist.iterator();
					// Searching vertlist is too slow; create temp data array.
					int []vs=new int[packData.nodeCount+1];
					int v;
					while (vlist.hasNext()) {
						v=(Integer)vlist.next();
						vs[v]=1;
					}
					vlist=vertlist.iterator();
					while (vlist.hasNext()) {
						v=(Integer)vlist.next();
						ArrayList<HalfEdge> arylst=packData.packDCEL.vertices[v].getEdgeFlower();
						Iterator<HalfEdge> ait=arylst.iterator();
						while (ait.hasNext()) {
							HalfEdge he=ait.next();
							int w=he.twin.origin.vertIndx;
							if (!bothvw || (v<w && vs[w]==1)) {
								add(he);
								count++;
							}
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
						dcel.Face face=packData.packDCEL.faces[(Integer)flist.next()];
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
					double verr=QualMeasures.d_edge_vis_error(packData, he);
					if (verr>thresh) {
						add(he);
						count++;
					}
				}
				break;
			}
			default: // if nothing else, see if there is an edge (or extended edges)
			{
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
	 * Make a distinct copy of this linked list, checking against
	 * the current edgelist's packData setting.
	 * @return new @see EdgeLink
	 */
	public HalfLink makeCopy() {
		Iterator<HalfEdge> elist=this.iterator();
		HalfLink newlist=new HalfLink(packData);
		while (elist.hasNext()) {
			newlist.add(new HalfEdge(elist.next()));
		}
		return newlist;
	}
	
	/**
	 * Abut an 'EdgeLink' to the end of this one.
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
			add(new HalfEdge(edge));
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
	 * Rotate EdgeLink so it starts with 'indx'.
	 * @param link @see EdgeLink
	 * @param indx new starting index
	 * @return @see EdgeLink, null if empty or on error.
	 */
	public static HalfLink rotateMe(HalfLink link,int indx) {
		int sz=link.size();
		if (link==null || sz<=indx)
			return null;
		HalfLink nlink=new HalfLink();
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
    public static HalfEdge randEdge(HalfLink edgelist) {
    	if (edgelist==null || edgelist.size()==0) return null;
    	int n=new Random().nextInt(edgelist.size());
    	return (edgelist.get(n));
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
	  * Create an EdgeLink that eliminates duplicate edges.
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
	 * Add 'HalfEdge's which separate 'nlink' vertices from 
	 * 'alphaIndx'.
	 * @param pdcel packDCEL
	 * @param nlink noddeLink
	 * @return int, count of edges, -1 on error
	 */
	public int separatingLinks(PackDCEL pdcel,NodeLink nlink,int alphaIndx) {
		int count=0;
		int[] vhits=new int[pdcel.vertCount+1];
		
		// mark the vertices to be excluded with -1
		Iterator<Integer> vst=nlink.iterator();
		while(vst.hasNext()) 
			vhits[vst.next()]=-1;
		
		// keep track with edge 'util'
		for (int e=1;e<=pdcel.edgeCount;e++) {
			pdcel.edges[e].util=0;
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
				vhits[v]=1;
				Vertex vert=pdcel.vertices[v];
				HalfEdge[] spokes=vert.getSpokes();
				int num=spokes.length;
				for (int j=0;j<num;j++) {
					HalfEdge he=spokes[j];
					int w=he.next.origin.vertIndx;
					if (vhits[w]<0) {
						HalfEdge sp=spokes[(j+num-1)%num];
						if (sp.util==0) {
							add(sp);
							sp.util=1;
							count++;
						}
						sp=spokes[(j+1)%num];
						if (sp.util==0) {
							add(sp);
							sp.util=1;
							count++;
						}
					}
					else if (vhits[w]==0){
						nxt.add(w);
					}
				}
			}
		}
			
		return count;
	}
		
}
