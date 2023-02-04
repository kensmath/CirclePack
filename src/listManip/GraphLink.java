package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import exceptions.CombException;
import komplex.DualGraph;
import komplex.EdgeSimple;
import komplex.GraphSimple;
import packing.PackData;
import util.MathUtil;
import util.StringUtil;

/**
 * Describes a graph with nodes in linked list of 'EdgeSimple's.
 * Nodes indexed by natural numbers (zero not included, though
 * 0 can be used to denote a root) and we must keep track of 
 * maximum node index. Our graphs do not allow multiple edges 
 * or loops (i.e., of form <f,f>). Examples of use are graphs 
 * and dual graphs of circle packings.
 * 
 * Acyclic graphs have no closed edge-paths. Tree's are special 
 * case: only a tree/forest (union of disjoint trees) can have
 * 'roots', which are pairs of the form <0,f>. A tree/forest is
 * not in proper form until each tree has "directed" edges 
 * (orientation <g,f> from g to f) from a unique root.
 * @author kens
 */

public class GraphLink extends LinkedList<EdgeSimple> {

	private static final long 
	serialVersionUID = 1L;
	
	public PackData packData;  // if associated with a packing
	public int maxIndex;       // max index not necessarily equal to 'size'
	
	// Constructors
	public GraphLink() {
		super();
		maxIndex=0;
		packData=null;
	}
	
	public GraphLink(PackData p) {
		this();
		packData=p;
	}
	
	public GraphLink(PackData p,EdgeSimple pair) {
		this();
		packData=p;
		add(pair);
	}
	
	public GraphLink(PackData p,Vector<String> items) {
		packData=p;
		if (items==null || items.size()==0) // default to spanning tree
			addDualLinks("s");
		else 
			addDualLinks(items);
	}
	
	public GraphLink(PackData p,String datastr) {
		packData=p;
		if (datastr!=null) 
			addDualLinks(datastr);
	}
	
	/**
	 * recompute 'maxIndex' 
	 */
	public void computeMI() {
		Iterator<EdgeSimple> gl=this.iterator();
		int newMI=0;
		while (gl.hasNext()) {
			EdgeSimple edge=gl.next();
			newMI=(edge.v>newMI) ? edge.v : newMI;
			newMI=(edge.w>newMI) ? edge.w : newMI;
		}
		maxIndex=newMI;
	}

	/**
	 * Extract connected component containing 'root'.
	 * @param root int, >0
	 * @return GraphLink or null on empty or error
	 */
	public GraphLink extractComponent(int root) {
		int realroot=0;
		int []mark=null;
		try {
			mark=componentize();
			if (root<1 || (realroot=mark[root])<=0 || realroot>maxIndex)
				throw new Exception("root?");
		} catch (Exception ex) {
			throw new CombException("problem with components: "+ex.getMessage());
		}
		int []hit=new int[maxIndex+1];
		for (int j=1;j<=maxIndex;j++) {
			if (mark[j]==realroot)
				hit[j]=j;
		}
		GraphLink glink=new GraphLink();
		Iterator<EdgeSimple> gl=this.iterator();
		while (gl.hasNext()) {
			EdgeSimple edge=gl.next();
			if (edge.v!=0) {
				if (hit[edge.v]!=0 || hit[edge.w]!=0)
					if (hit[edge.v]*hit[edge.w]==0)
						throw new CombException("error in component");
				glink.add(new EdgeSimple(edge));
			}
			else if (hit[edge.w]!=0) // get the root 
				glink.add(0,new EdgeSimple(edge));
		}
		return glink;
	}
	
	/**
	 * Search (starting at 'spot') for the next root, <0,f>.
	 * This should only be allowed for tree/forest.
	 * @param spot int starting point for search.
	 * @return int , root f or -1 on failure.
	 */
	public int findRoot(int spot) {
		if (spot>=this.size()) return -1;
		if (spot<=0) spot=0;
		for (int j=spot;j<size();j++) {
			EdgeSimple edge=get(j);
			if (edge.v==0) {
				if (edge.w>0) return edge.w;
				throw new CombException("illegal pair in GraphLink");
			}
		}
		return -1;
	}
	
	/**
	 * Search (starting at 'spot') for index of next entry 
	 * that is a root, <0,f>
	 * @param int spot index
	 * @return int, index of <0,f> entry or -1 on failure.
	 */
	public int findRootSpot(int spot) {
		if (spot>=this.size()) return -1;
		if (spot<=0) spot=0;
		for (int j=spot;j<size();j++) {
			EdgeSimple edge=get(j);
			if (edge.v==0) {
				if (edge.w>0) return j;
				throw new CombException("illegal pair in GraphLink");
			}
		}
		return -1;
	}
	
	/**
	 * Identify connected components of the graph and determine
	 * if it's acyclic -- no closed edge paths. Return integer
	 * array 'mark'. mark[node]=-1 if node is not in graph,
	 * mark[node]=s if s is the smallest index in the connected
	 * component with node. mark[0]=component count if graph 
	 * acyclic, else negative of component count.
	 * @return int[maxIndex+1], null if empty
	 */
	public int []componentize() {
		boolean acyclic=true;
		
		// First, set up 'mark' vector: =-1 if index isn't used, 
		//   otherwise, smallest node it's connected to.
		int []mark=new int[maxIndex+1];
		for (int i=0;i<=maxIndex;i++)
			mark[i]=-1;  // -1 indicates i doesn't appear as node
		Iterator<EdgeSimple> git=this.iterator();
		EdgeSimple edge=null;
		while (git.hasNext()) {
			edge=new EdgeSimple(git.next());
			int min=(edge.v<edge.w) ? edge.v :edge.w;
			if (min==0) // have to watch for (0,v) root.
				min=edge.w;
			if (mark[edge.v]<0) mark[edge.v]=min;
			if (mark[edge.w]<0) mark[edge.w]=min;
			int mm=(mark[edge.v]<mark[edge.w]) ? mark[edge.v]:mark[edge.w];
			mark[edge.v]=mark[edge.w]=mark[mm];
		}
		// pass through while some mark is adjusted down
		boolean hit=true;
		while (hit) {
			hit=false;
			for (int i=1;i<=maxIndex;i++) {
				if (mark[i]>0 && mark[i]<=i) {
					int cur=mark[i];
					while (mark[cur]<cur) { 
						hit=true;
						mark[i]=cur=mark[cur];
					}
				}
			}
			git=this.iterator();
			while (git.hasNext()) {
				edge=git.next();
				if (mark[edge.v]!=mark[edge.w]) {
					hit=true;
					mark[edge.v]=mark[edge.w]=(mark[edge.v]<mark[edge.w]) ? mark[edge.v] : mark[edge.w];
				}
			}
		}

		// 'gen' builds generations of nodes from their roots.
		int []gen=new int[maxIndex+1];
		int compCount=0;
		// look for smallest marks; these are all roots
		for (int i=1;i<=maxIndex;i++) {
			if (mark[i]==i) {
				gen[i]=1;
				compCount++;
			}
		}
		
		// check acyclic: pass through edges as long as 'gen' is being reset
		hit=true;
		while (hit) {
			hit=false;
			git=this.iterator();
			while (git.hasNext()) {
				edge=git.next();
				if (gen[edge.v]>0 || gen[edge.w]>0) {
					if (gen[edge.v]==gen[edge.w] || 
							(gen[edge.v]>0 && gen[edge.w]>0 && 
									Math.abs(gen[edge.v]-gen[edge.w])>1))
						acyclic=false;
					if (gen[edge.v]==0) {
						gen[edge.v]=gen[edge.w]+1;
						hit=true;
					}
					else if (gen[edge.w]==0) { 
						gen[edge.w]=gen[edge.v]+1;
						hit=true;
					}
				}
			}
		} // done with while
		
		// return results
		mark[0]=compCount;
		if (!acyclic) mark[0] *= -1;
		return mark;
	}

	/**
	 * Get linked list of nodes (nonzero) in some pair with 'node'.
	 * If 'node' is 0, looking for root.
	 * @param node
	 * @return @see NodeLink, possible empty
	 */
	public NodeLink findPartners(int node) {
		if (node<0 || node>maxIndex)
			throw new CombException("node in error");
		NodeLink nlink=new NodeLink();
		Iterator<EdgeSimple> gl=this.iterator();
		while (gl.hasNext()) {
			EdgeSimple edge=gl.next();
			if (edge.v==node) {
				if (edge.w!=0)
					nlink.add(edge.w);
			}
			else if (edge.w==node) {
				if (edge.v!=0)
					nlink.add(edge.v);
			}
		}
		return nlink;
	}

	/**
	 * Return array indicating combinatorial distance to 'node'.
	 * Entry = -1 for nodes not in the graph or not connected 
	 * to 'node'. 'node' is 1 (first generation).
	 * @param node int, >0
	 * @param maxsize int (may want more than 'maxIndex' array size)
	 * @return  int[maxIndex+1] or null if 'node' not in there.
	 */
	public int []graphDistance(int node,int maxsize) {
		if (node<=0 || node>maxIndex)
			throw new CombException("node "+node+" is in error");
		int sizeit=maxIndex+1;
		if (maxsize>maxIndex)
			sizeit=maxsize+1;
		int []ans=new int[sizeit+1];
		for (int i=0;i<=sizeit;i++)
			ans[i]=-1;
		ans[node]=1;
		int tick=1;
		NodeLink currlist=new NodeLink();
		NodeLink nextlist=new NodeLink();
		nextlist.add(node);
		while (nextlist.size()>0) {
			currlist=nextlist;
			nextlist=new NodeLink();
			Iterator<Integer> clst=currlist.iterator();
			tick++;
			while (clst.hasNext()) {
				int n=clst.next();
				NodeLink suc=findPartners(n);
				// error or 'node' not in graph?
				if (suc==null || suc.size()==0) return null; 
				Iterator<Integer> sucl=suc.iterator();
				while (sucl.hasNext()) {
					int g=sucl.next();
					if (ans[g]<=0) {
						ans[g]=tick;
						nextlist.add(g);
					}
				}
			}
		}
		return ans;
	}
	
	/**
	 * Abut GraphLink to end of 'this' GraphLink. If non-empty, and new
	 * elements start with a root, then don't include the root.
	 * @param moreGL GraphLink
	 * @return int, count of new elements (some may be improper, some redundant)
	 */
	public int abutMore(GraphLink moreGL) {
		if (moreGL==null || moreGL.size()==0)
			return 0;
		boolean noroots=true;
		// if empty, allow roots among new elements
		if (this.size()==0)
			noroots=false;
		int ticks=0;
		Iterator<EdgeSimple> mit=moreGL.iterator();
		EdgeSimple edge=null;
		while (mit.hasNext()) {
			edge=mit.next();
			if (!noroots || edge.v!=0) {
				add(new EdgeSimple(edge.v,edge.w));
				ticks++;
			}
		}
		return ticks;
	}

	/**
     * Return random entry from GraphLink; caution, does not adjust
     * for repeat entries.
     * @param graphlist GraphLink
     * @return EdgeSimple, null is graphlist is null or empty
     */
    public static EdgeSimple randLink(GraphLink graphlist) {
    	if (graphlist==null || graphlist.size()==0) return null;
    	int n=new Random().nextInt(graphlist.size());
    	EdgeSimple edge=graphlist.get(n);
    	return new EdgeSimple(edge.v,edge.w);
    }

	/** 
	 * Note: allow edges <0,f> as roots.
	 */
	public boolean add(EdgeSimple edge) {
		if (edge==null || edge.v<0 || edge.w<0 || edge.v==edge.w || 
				(packData!=null && (edge.v>packData.faceCount || 
						edge.w>packData.faceCount))) 
			return false;
		if ((edge.v>0 || edge.w>0)) {
			maxIndex= (edge.v>maxIndex) ? edge.v : maxIndex;
			maxIndex= (edge.w>maxIndex) ? edge.w : maxIndex;
			return super.add(edge);
		}
		// if one entry is 0, it should be first entry
		else if (edge.v==0) {
			maxIndex= (edge.w>maxIndex) ? edge.w : maxIndex;
			return super.add(edge);
		}
		else if (edge.w==0) {
			maxIndex= (edge.v>maxIndex) ? edge.v : maxIndex;
			return super.add(new EdgeSimple(edge.w,0));
		}
		return false;
	}

	/** 
	 * Note: allow edges <0,f> as roots.
	 */
	public boolean add(int f, int g) {
		EdgeSimple edge=new EdgeSimple(f,g);
		if (g==0) {
			if (f==0) 
				return false;
			return add(edge);
		}
		if (f==0) {
			if (g==0) 
				return false;
			return add(new EdgeSimple(0,g));
		}
		return add(new EdgeSimple(f,g));
	}
	
	/**
	 * Add links to this graph (if it is associated with PackData). 
	 * @param datastr String
	 * @return int count
	 */	
	public int addDualLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addDualLinks(items);
	}
	
	/**
	 * Add links to this graph (if it is associated with PackData)
	 * @param items, a vector of strings
	 * @return int count
	 */
	public int addDualLinks(Vector<String> items) {
		if (packData==null) 
			return -1;
		int count=0;

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
				count+=addDualLinks(CPBase.varControl.getValue(str));
			}
			
			// check for glist, elist, hlist, flist
			else if (str.substring(1).startsWith("list")) {
				GraphLink glink=null;
				FaceLink flink=null;
				EdgeLink elink=null;
				HalfLink hlink=null;
		
				// what type of list?
				if ((str.startsWith("e") && 
						(elink=packData.elist)!=null && elink.size()>0) ||
					(str.startsWith("E") && 
						(elink=CPBase.Elink)!=null && CPBase.Elink.size()>0) || 
					(str.startsWith("g") && 
						(glink=packData.glist)!=null && glink.size()>0) ||
					(str.startsWith("G") && 
						(glink=CPBase.Glink)!=null && CPBase.Glink.size()>0) ||
					(str.startsWith("h") &&
						(hlink=packData.hlist)!=null && hlink.size()>0) ||
					(str.startsWith("H") && 
						(hlink=CPBase.Hlink)!=null && CPBase.Hlink.size()>0) ||
					(str.startsWith("f") && 
						(flink=packData.flist)!=null && flink.size()>0) ||
					(str.startsWith("F") && 
						(flink=CPBase.Flink)!=null &&	flink.size()>0)) {

					String strdata=str.substring(5).trim(); // remove '?list'	
					EdgeSimple edge=null;

					// convert others to elink
					if (hlink!=null) {
						elink=new EdgeLink(hlink);
						hlink=null;
					}
					else if (glink!=null) {
						elink=new EdgeLink(glink);
						glink=null;
					}
					else if (flink!=null) { // try to convert to dual edge list
						
						elink=new EdgeLink();
						// find last face in current list
						int lastf = 0;
						if (this.size()>0 && (edge=this.getLast())!=null && 
								edge.w>0 && edge.w<=packData.faceCount)
							lastf=edge.w;
						
						// now go through list: if nextf is contiguous to lastf, 
						//     add {lastf,nextf}, else add root {0,nextf}.
						Iterator<Integer> flst=flink.iterator();
						while (flst.hasNext()) {
							int nextf=flst.next();
							if (nextf>0 && nextf<=packData.faceCount) {
								if (lastf>0) {
									if (packData.face_nghb(lastf,nextf)>=0)
										elink.add(new EdgeSimple(lastf,nextf));
									else 
										elink.add(new EdgeSimple(0,nextf));
									lastf=nextf;
									count++;
								}
								else {
									elink.add(new EdgeSimple(0,nextf));
									lastf=nextf;
									count++;
								}
							}
						} // end of 'while'
					}

					// check for parens listing range of indices 
					int lsize=elink.size()-1;
					int[] irange=StringUtil.get_int_range(strdata, 0,lsize);
					if (irange!=null) {
						int a=irange[0];
						int b=(irange[1]>lsize) ? lsize : irange[1]; 
						if (a==0 && elink.get(a).v==0) // first is a root
								a=1;
						if (b>=a) {
							for (int j=a;j<=b;j++) {
								edge=elink.get(j);
								if (edge.v<=packData.faceCount && edge.w<=packData.faceCount) {
									add(edge);
									count++;
								}
							}
						}
					}
					
					// check for brackets first
					String brst=StringUtil.get_bracket_strings(str)[0];
					if (brst!=null) {
						if (brst.startsWith("r")) { // return first and move it to end
							EdgeSimple edg=elink.getFirst();
							try {
								if (edg.v==0) // root
									elink.remove(0);
								edg=elink.getFirst();
							} catch(Exception ex) {
								edg=null;
							}
							if (edg!=null) {
								elink.add(new EdgeSimple(edg)); // copy to end
							}
						}
						
						// for 'r' or 'n', remove first and add it to 'this'
						if (brst.startsWith("r") || brst.startsWith("n")) { // use up first
							edge=(EdgeSimple)elink.remove(0);
							try {
								if (edge.v==0) { // ignore a root, try next
									edge=(EdgeSimple)elink.remove(0);
								}
							} catch (Exception ex) {
								edge=null;
							}
							if (edge!=null && edge.v<=packData.faceCount && edge.w<=packData.faceCount) {
								add(edge);
								count++;
							}
						}
						else if (brst.startsWith("l")) { // last
							edge=(EdgeSimple)elink.getLast();
							if (edge.v<=packData.faceCount && edge.w<=packData.faceCount) {
								add(edge);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n==0 && elink.get(0).v==0) // don't add if a root
									n=1; 
								if (n>=0 && n<elink.size()) {
									edge=(EdgeSimple)elink.get(n);
									if (edge.v<=packData.faceCount && edge.w<=packData.faceCount) {
										add(edge);
										count++;
									}
								}
							} catch (NumberFormatException nfe) {}
						}
					}
					
					// else just adjoin the lists, including any roots
					else { 
						Iterator<EdgeSimple> glst=glink.iterator();
						while (glst.hasNext()) {
							edge=(EdgeSimple)glst.next();
							if (edge.v<=packData.faceCount && edge.w<=packData.faceCount) {
								add(edge);
								count++;
							}
						}
					}
				} // end of handling '?list'
				else // no appropriate list
					return count;
			}
			
			/* Now parse remaining options based on first character;
	 		default case, just try to read off pairs of numbers. */

			// TODO: issue -- when we 'abut', do we want to remove a root?
			else {
			switch(str.charAt(0)) {
			
			case 'a': // all: empty, then build full dual graph
			{
				// optional {f} starting face
				int ff=-1;
				try {
					ff=FaceLink.grab_one_face(packData,StringUtil.reconItem(items));
				} catch(Exception ex) {
					ff=-1;
				}
				
				emptyMe();
				count += abutMore(DualGraph.buildDualGraph(packData,ff,null));
				break;
			}
			
			case 'r': // add one random dual edge from list
			{
				GraphLink glk=null;
				if (items==null || items.size()==0)
					glk=new GraphLink(packData,"a");
				else glk=new GraphLink(packData,items);
				EdgeSimple es=randLink(glk);
				if (es!=null) {
					add(es);
					count++;
				}
				break;
			}

			// ----------- TODO: many other future options --------------
			
			default: // if nothing else, see if it's face index or 0
			{
				int nextf=-1;
				try {
					nextf=Integer.parseInt(str);
				} catch(Exception ex) {
					break;
				}
				
				// if 0, check next entry for positive integer, else done.
				//    (0 cannot not appear as second entry in edge)
				if (nextf==0) {
					if (!its.hasNext())
						return count;
					str=its.next();
					// a variable?
					if (str.startsWith("_")) 
						str=CPBase.varControl.getValue(str);
					try {
						nextf=Integer.parseInt(str);
						if (nextf<=0 || nextf>packData.faceCount)
							return count;
						add(new EdgeSimple(0,nextf));
						count++;
					} catch (Exception ex) {
						return count;
					}
					break;
				}
				
				if (nextf<0 || nextf>packData.faceCount)
					break;

				// reaching here, nextf > 0 is face number
				// find last face in current list
				int lastf = 0;
				EdgeSimple edge=null;
				if (this.size()>0 && (edge=this.getLast())!=null && 
						edge.w>0 && edge.w<=packData.faceCount)
					lastf=edge.w; // should never be 0
				if (lastf<=0 || lastf>packData.faceCount) {
					add(new EdgeSimple(0,nextf)); // root
					count++;
				}
				if (packData.face_nghb(lastf,nextf)>=0) {
					add(new EdgeSimple(lastf,nextf));
					count++;
				}
			} // done with 'default'
			
			} // end of 'switch'
			} // end of else
		} // end of main 'while'
		return count;
	}
	
	 /**
	  * Create a new GraphLink that eliminates duplicate edges.
	  * @param el
	  * @param orient boolean, true, then take account of orientation
	  * @return new EdgeLink
	  */
	 public static GraphLink removeDuplicates(GraphLink gl,boolean orient) {
		 GraphLink newGL=new GraphLink(gl.packData);
		 Iterator<EdgeSimple> gls=gl.iterator(); // 'GraphLink' still has 'EdgeSimple' type
		 while (gls.hasNext()) {
			 GraphSimple gedge=new GraphSimple(gls.next());
			 if (newGL.containsFG(gedge, orient))
				 continue;
			 newGL.add(gedge);
		 }
		 return newGL;
	 }
	 
	 /**
	   * Find index of <f,g> or <g,f> in the list
	   * @param glist GraphLink
	   * @param f int
	   * @param g int
	   * @return -1 on error
	   */
	 public static int getFG(GraphLink glist,int f,int g) {
	   	if (g>f) {
	   		int hold=g;
	   		g=f;
	   		f=hold;
	   	}
	   	for (int i=0;i<glist.size();i++) {
	   		GraphSimple edge=new GraphSimple(glist.get(i));
	   		if (edge.v==f && edge.w==g)
	   			return i;
	   	}
	   	return -1;
	 }
	 
	 /**
	  * Check if this list contains (f,g) ( or (g,f) if orient=false)
	  * @param f int
	  * @param g int
	  * @param orient boolean, true, enforce orientation
	  * @return boolean
	  */
	 public boolean containsFG(int f,int g,boolean orient) {
		 return containsFG(new GraphSimple(f,g),orient);
	 }
	 
	 /**
	  * Check if this list contains (f,g) ( or (g,f) if orient=false)
	  * TODO: convert temporarily to 'EdgeSimple
	  * @param edge GraphSimple
	  * @param orient boolean, true, enforce orientation
	  * @return boolean
	  */
	 public boolean containsFG(GraphSimple gedge,boolean orient) {
		 Iterator<EdgeSimple> els=this.iterator(); // this is still list of 'EdgeSimple'
		 while (els.hasNext()) {
			 EdgeSimple edge=new EdgeSimple(gedge.v,gedge.w); // tmp convert
			 if (els.next().isEqual(edge,orient))
				 return true;
		 }
		 return false;
	 }
	 
	/**
	 * Clear out this linked list.
	 */
	public void emptyMe() {
		this.removeAll(this);
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
	 public GraphLink clone() {
		 GraphLink gl=new GraphLink();
		 gl.packData=packData;
		 gl.maxIndex=maxIndex;
		 Iterator<EdgeSimple> tis=this.iterator();
		 while (tis.hasNext()) 
			 gl.add(tis.next().clone());
		 return gl;
	 }
}
