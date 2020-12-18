package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import exceptions.CombException;
import exceptions.DataException;
import komplex.DualGraph;
import komplex.SideDescription;
import komplex.EdgeSimple;
import komplex.GraphSimple;
import komplex.RedList;
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
		if (datastr!=null) addDualLinks(datastr);
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
	 * Extract a GraphLink of the connected component containing
	 * 'base', then build a directed spanning tree.
	 * @param base, face for root of tree
	 * @return GraphLink, a directed spanning tree or null on 
	 * error. Note that "tree" may have only <0,base>.
	 */
	public GraphLink extractSpanTree(int root) {
		return extractSpanTree(root,(NodeLink)null);
	}
		
	/**
	 * Extract a GraphLink of the connected component containing
	 * 'base', then build a directed spanning tree. Indices may 
	 * be excluded; resulting tree will have no edges with an
	 * end in 'exclude'.
	 * @param base, node for root of tree
	 * @param exclude, NodeLink (may be null)
	 * @return GraphLink, a directed spanning tree or null on 
	 * error. Note that "tree" may have only <0,base>.
	 */
	public GraphLink extractSpanTree(int base,NodeLink exclude) {

		// first get the component containing root
		GraphLink glink=extractComponent(base);
		int []gen=new int[glink.maxIndex+1];
		
		// check for exclusions
		if (exclude!=null) {
			Iterator<Integer> xlist=exclude.iterator();
			while (xlist.hasNext()) {
				int j=xlist.next();
				if (j<=glink.maxIndex)
					gen[j]=-1;
			}
		}
		
		// is base excluded?
		if (gen[base]<0)
			throw new CombException("base "+base+" can't be among excluded");
		
		// create the new graph
		GraphLink ans=new GraphLink();
		int tick=1;
		gen[base]=tick;
		ans.add(new EdgeSimple(0,base)); // add the root
		NodeLink currentGen=new NodeLink();
		NodeLink nextGen=new NodeLink();
		nextGen.add(base);
		while (nextGen.size()>0) {
			tick++;
			currentGen=nextGen;
			nextGen=new NodeLink();
			Iterator<Integer> nl=currentGen.iterator();
			while (nl.hasNext()) {
				int n=nl.next();
				NodeLink plist=glink.findPartners(n);
				Iterator<Integer> pl=plist.iterator();
				while (pl.hasNext()) {
					int g=pl.next();
					if (gen[g]==0) {
						ans.add(new EdgeSimple(n,g));
						nextGen.add(g);
						gen[g]=tick;
					}
				}
			}
		}
		return ans;
	}

	/**
	 * Extract a GraphLink of the connected component of 'this'
	 * which contains'base', then build a directed spanning tree. 
	 * Edges may be excluded; resulting tree will have no edges in 
	 * 'xedges'.
	 * @param base int, node for root of tree
	 * @param xedges EdgeLink, (may be null)
	 * @return GraphLink, a directed spanning tree or null on 
	 *  error. Note that "tree" may have only <0,base>.
	 */
	public GraphLink extractSpanTree(int base,EdgeLink xedges) {

		// first get the component containing root
		GraphLink glink=extractComponent(base);
		int []gen=new int[glink.maxIndex+1];
		
		// create the new graph
		GraphLink ans=new GraphLink();
		int tick=1;
		gen[base]=tick;
		ans.add(new EdgeSimple(0,base)); // add the root
		NodeLink currentGen=new NodeLink();
		NodeLink nextGen=new NodeLink();
		nextGen.add(base);
		while (nextGen.size()>0) {
			tick++;
			currentGen=nextGen;
			nextGen=new NodeLink();
			Iterator<Integer> nl=currentGen.iterator();
			while (nl.hasNext()) {
				int n=nl.next();
				NodeLink plist=glink.findPartners(n);
				Iterator<Integer> pl=plist.iterator();
				while (pl.hasNext()) {
					int g=pl.next();
					if (gen[g]==0 && !EdgeLink.ck_in_elist(xedges,n,g)) {
						ans.add(new EdgeSimple(n,g));
						nextGen.add(g);
						gen[g]=tick;
					}
				}
			}
		}
		return ans;
	}

	/**
	 * Given a tree/forest, this returns the component containing
	 * 'newRoot' as a tree with root <0,newRoot>.
	 * @param tree
	 * @param newRoot
	 * @return, null on error.
	 */
	public GraphLink reRoot(GraphLink tree,int newRoot) {
		int []gen=tree.graphDistance(newRoot,maxIndex);
		if (gen==null) return null;
		GraphLink ans=new GraphLink();
		Iterator<EdgeSimple> tl=tree.iterator();
		while (tl.hasNext()) {
			EdgeSimple edge=tl.next();
			if (edge.v!=0)
				ans.add(new EdgeSimple(edge));
		}
		ans.add(new EdgeSimple(0,newRoot));
		return ans;
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
	 * Checks if node is a root (ie., in <0,f> pair). This is
	 * legal only if graph is a tree/forest, but that is not
	 * confirmed here.
	 * @param node
	 * @return true if a root 
	 */
	public boolean isRoot(int node) {
		Iterator<EdgeSimple> dlk=this.iterator();
		while (dlk.hasNext()) {
			EdgeSimple edge=dlk.next();
			if (edge.v==0 && edge.w==node)  
				return true;
		}
		return false;
	}
	
	/**
	 * Check if 'this' is a directed tree/forest -- meaning acyclic, 
	 * has a root <0,n> for each component, and edges are directed
	 * away from that root.
	 * @return Vector<Integer>, vector containing 'root' (or -root for
	 * a component which is not a tree).
	 */
	public Vector<Integer> isForest() {
		int []mark=componentize();
		if (mark==null) return null;
		Vector<Integer> comps=new Vector<Integer>(0);
		for (int j=1;j<=maxIndex;j++) {
			if (mark[j]==j) {
				int root=isInTree(j,mark);
				if (root==0) 
					comps.add(-j);
				else
					comps.add(root);
			}
		}
		if (comps.size()==0) return null;
		return comps;
	}
	
	/**
	 * Check to see if n belongs to a tree in this GraphLink.
	 * if yes, return r where <0,r> is the root. If there's a
	 * root but component is not acyclic or is not directed edges,
	 * return -root. Return 0 if some other problem.
	 * @param n
	 * @param mark, pass if computed already in 'isForest' 
	 * @return, root, -root, or 0 on some error
	 */
	public int isInTree(int n,int []mark) {
		if (mark==null)
			mark=componentize();
		if (mark==null) return 0; // some error
		int root=mark[n];
		if (root<=0) return 0; // n doesn't occur
		boolean hit=false;
		for (int j=1;(j<=maxIndex && !hit);j++) {
			if (mark[j]==root && isRoot(j)) { // found root
				hit=true;
				int []gdist=graphDistance(j,maxIndex);
				Iterator<EdgeSimple> gl=this.iterator();
				while (gl.hasNext()) {
					EdgeSimple edge=gl.next();
					if (gdist[edge.v]>0 && gdist[edge.w]!=gdist[edge.v]+1)
						return -root; // not acyclic or not directed
				}
				return root;
			}
		}
		return 0;
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
	 * Return linked list of successor nodes to given node
	 * @param node
	 * @return NodeLink: null if none found, or node is improper,
	 * or node is (only) a root. User has to check via 'isRoot'.
	 */
	public NodeLink findSuccessors(int node) {
		if (node<=0)
			return null;
		NodeLink nlink=new NodeLink();
		
		Iterator<EdgeSimple> dlk=this.iterator();
		while (dlk.hasNext()) {
			EdgeSimple edge=dlk.next();
			if (edge.v==node) {
				if (edge.w<=0)
					throw new CombException("illegal GraphLink pair");
				nlink.add(edge.w);
			}
		}
		if (nlink.size()==0) return null;
		return nlink;
	}
	
	/**
	 * Find first index of directed edge [g,f]
	 * @param g
	 * @param f
	 * @return int index in 'this' for [g,f] or its
	 *   negative if [g,f] is missing but [f,g] is there.
	 *   return 0 if neither is found
	 */
	public int findDirEdge(int g,int f) {
		EdgeSimple edge=new EdgeSimple(g,f);
		for (int j=0;j<this.size();j++) {
			if (edge.equals(this.get(j)))
				return j;
		}
		edge.v=f;
		edge.w=g;
		for (int j=0;j<this.size();j++) {
			if (edge.equals(this.get(j)))
				return -j;
		}
		return 0;
	}
	
	/**
	 * Return predecessor nodes to given node. There should
	 * be at most one in case of tree/forest.
	 * @param node
	 * @return NodeLink, null if none found or node improper.
	 */
	public NodeLink findPredesessor(int node) {
		if (node<=0)
			return null;
		NodeLink nlink=new NodeLink();
		
		Iterator<EdgeSimple> dlk=this.iterator();
		while (dlk.hasNext()) {
			EdgeSimple edge=dlk.next();
			if (edge.w==node) 
				nlink.add(edge.v);
		}
		if (nlink.size()==0) return null;
		return nlink;
	}
	
	/**
	 * If 'this' is a directed tree and [g,f] is an edge (g>0, f>0), 
	 * then sever at this edge. Return the tree outside the edge [g,f], 
	 * so one of <0,f> or <0,g> becomes its root. Also create new tree
	 * of the leftovers.
	 * @param g
	 * @param f
	 * @return ans=GraphLink[2] or null, ans[0]=trunk with [g,f] and 
	 * branch removed, ans[1] is the branch itself.
	 */
	public GraphLink []severBranch(int g,int f) {
		int root=isInTree(f,null);
		if (root<=0) 
			throw new CombException("<"+g+","+f+"> not part of tree");
		int []gen=graphDistance(root,maxIndex); // generations in component of root
		if (gen==null || gen[g]<=0) return null;
		int origRoot=findRoot(0);
		
		// find branch from [g,f]
		int newRoot=f;
		int dropit=g;
		if (gen[g]>gen[f]) {
			newRoot=g;
			dropit=f;
		}
		int []fromNR=new int[maxIndex+1];
		fromNR[newRoot]=1;
		int tick=1;
		NodeLink currlist=new NodeLink();
		NodeLink nextlist=new NodeLink();
		nextlist.add(newRoot);
		while (nextlist.size()>0) {
			tick++;
			currlist=nextlist;
			nextlist=new NodeLink();
			Iterator<Integer> clst=currlist.iterator();
			while (clst.hasNext()) {
				int gg=clst.next();
				NodeLink suc=findPartners(gg);
				Iterator<Integer> sucl=suc.iterator();
				while (sucl.hasNext()) {
					int ff=sucl.next();
					nextlist.add(ff);
					fromNR[ff]=tick;
				}
			}
		}
		
		GraphLink branch=new GraphLink();
		GraphLink trunk=new GraphLink();
		
		// form trunk and branch
		branch.add(0,newRoot);
		Iterator<EdgeSimple> gl=this.iterator();
		while (gl.hasNext()) {
			EdgeSimple edge=gl.next();
			if (fromNR[edge.v]>0 && fromNR[edge.w]>0) {
				branch.add(new EdgeSimple(edge));
			}
			else if (edge.v!=newRoot && edge.w!=newRoot) {
				trunk.add(new EdgeSimple(edge));
			}
		}
		// may have to establish new root for leftovers
		if (newRoot==origRoot)
			trunk.add(new EdgeSimple(0,dropit));
		
		// package results
		GraphLink []ans=new GraphLink[2];
		if (trunk.size()==0) 
			trunk=null;
		if (branch.size()==0) 
			branch=null;
		ans[0]=trunk;
		ans[1]=branch;
		return ans;
	}
	
	/**
	 * If this is a tree with node 't' and branch is a tree
	 * with root 'b', then return new tree by grafting branch
	 * on using [t,b] as bridge.
	 * 
	 * TODO: not yet completed and debugged; need for this is
	 * no longer clear.
	 * 
	 * @param branch GraphLink
	 * @param t int
	 * @param b int
	 * @return GraphLink
	 */
	public GraphLink graftBranch(GraphLink branch,int t,int b) {
		
		// set up the trunk
		int trunkRoot=findRoot(0);
		if (trunkRoot<0) 
			throw new DataException("trunk doesn't have a root");
		int []trunkMark=graphDistance(trunkRoot,maxIndex);
		if (trunkMark[t]<=0)
			throw new DataException(t+" is not a node in trunk");
		
		// set up the branch
		int branchRoot=branch.findRoot(0);
		// may need to reRoot the branch
		if (branchRoot!=b) { // reRoot branch
			branch=reRoot(branch,b);
			if (branch==null)
				throw new CombException("couldn't reRoot branch");
		}
		int []branchMark=branch.graphDistance(branchRoot,maxIndex);
		if (trunkMark[t]==0 || branchMark[b]==0)
			throw new DataException("bridge edge <"+t+","+b+"> does not connect");
		for (int j=1;j<=branch.maxIndex;j++) {
			if (branchMark[j]>0 && j<=maxIndex && trunkMark[j]>0)
				throw new DataException("branch and trunk overlap");
		}
		
		// copy in the current tree
		GraphLink ans=new GraphLink();
		Iterator<EdgeSimple> gl=this.iterator();
		while (gl.hasNext()) {
			ans.add(new EdgeSimple(gl.next()));
		}
		
		// add the bridge
		this.add(new EdgeSimple(t,b));
		
		// add the branch (except its root)
		gl=branch.iterator();
		while (gl.hasNext()) {
			EdgeSimple edge=gl.next();
			if (edge.v>0) { // avoid root
				ans.add(new EdgeSimple(edge));
			}
		}
		
		if (ans.size()==0) return null;
		return ans;
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
	 * Is (f,g) an entry? (with same order)
	 * @param e @see EdgeSimple
	 * @return int, index of (f,g) or -1 on error or not found
	 */
	public int containsFG(EdgeSimple e) {
		for (int j=0;j<this.size();j++) {
			EdgeSimple edge=this.get(j);
			if (edge.v==e.v && edge.w==e.w)
				return j;
		}
		return -1;
	}
	
	/**
	 * Make a distinct copy of this @see GraphLink; no check on validity of the entries.
	 * @return @see GraphLink
	 */
	public GraphLink makeCopy() {
		Iterator<EdgeSimple> glist=this.iterator();
		GraphLink newlink=new GraphLink();
		while (glist.hasNext()) {
			EdgeSimple edge=glist.next();
			newlink.add(new EdgeSimple(edge.v,edge.w));
		}
		return newlink;
	}
		

	/**
	 * If 'this' is a tree/forest, find the unique list of nodes starting 
	 * at 'node' and ending at the root of its component.
	 * @param node
	 * @return, null if this is not a forest or 'node' is not in it.
	 */
	public NodeLink Route2Root(int node) {
		int preroot=isInTree(node,null);
		if (preroot<=0)
			throw new CombException("node "+node+"; error in tree");
		NodeLink llist=new NodeLink();
		llist.add(node);
		try {
			int cur=findPredesessor(node).get(0);
			int safety=10000;
			while (cur!=preroot && safety>0) {
				safety--;
				cur=findPredesessor(cur).get(0);
				llist.add(cur);
			}
			if (cur!=preroot) 
				System.err.println("Seem to have missed root");
		} catch (Exception ex) {
			throw new CombException("error following to root: "+ex.getMessage());
		}
		return llist;
	}
	
	/**
	 * Check if this node belongs to some edge of the graph
	 * @param node
	 * @return boolean
	 */
	public boolean nodeExists(int node) {
		Iterator<EdgeSimple> lst=this.iterator();
		while (lst.hasNext()) {
			EdgeSimple edge=lst.next();
			if (edge.v==node || edge.w==node)
				return true;
		}
		return false;
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
		if (edge.v<0 || edge.w<0 || edge.v==edge.w || (packData!=null &&
				(edge.v>packData.faceCount || edge.w>packData.faceCount))) 
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
			if (f==0) return false;
			return add(edge);
		}
		if (f==0) {
			if (g==0) return false;
			return add(new EdgeSimple(0,g));
		}
		return add(new EdgeSimple(f,g));
	}
	
	/**
	 * A 'root' is added, a pair of form <0,f>, f>0.
	 * @param f, node index
	 * @return boolean
	 */
	public boolean addRoot(int f) {
		return add(new EdgeSimple(0,f));
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
		if (packData==null) return -1;

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
			
			// check for '?list' first
			else if (str.substring(1).startsWith("list")) {
				GraphLink glink=null;
				FaceLink flink=null;
				
				// glist or Glist
				if ((str.startsWith("g") && (glink=packData.glist)!=null
						&& glink.size()>0) ||
						(str.startsWith("G") && (glink=CPBase.Glink)!=null
								&& CPBase.Glink.size()>0)) {
					EdgeSimple edge=null;

					// check for brackets first
					String brst=StringUtil.brackets(str);
					if (brst!=null) {
						if (brst.startsWith("r")) { // return first and move it to end
							EdgeSimple edg=glink.getFirst();
							try {
								if (edg.v==0) // root
									glink.remove(0);
								edg=glink.getFirst();
							} catch(Exception ex) {
								edg=null;
							}
							if (edg!=null) {
								glink.add(new EdgeSimple(edg)); // make copy at end
							}
						}
						
						// for 'r' or 'n', remove first and add it to 'this'
						if (brst.startsWith("r") || brst.startsWith("n")) { // use up first
							edge=(EdgeSimple)glink.remove(0);
							try {
								if (edge.v==0) { // ignore a root, try next
									edge=(EdgeSimple)glink.remove(0);
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
							edge=(EdgeSimple)glink.getLast();
							if (edge.v<=packData.faceCount && edge.w<=packData.faceCount) {
								add(edge);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n==0 && glink.get(0).v==0) // don't add if a root
									n=1; 
								if (n>=0 && n<glink.size()) {
									edge=(EdgeSimple)glink.get(n);
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
				} // end of handling glist/Glist
				
				// flist or Flist --- convert to dual edges or roots as possible
				else if (((str.startsWith("f") && (flink=packData.flist)!=null) ||
						(str.startsWith("F") && (flink=CPBase.Flink)!=null)) &&
						flink.size()>0) {
					
					// find last face in current list
					int lastf = 0;
					EdgeSimple edge=null;
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
									add(new EdgeSimple(lastf,nextf));
								else 
									add(new EdgeSimple(0,nextf));
								lastf=nextf;
								count++;
							}
							else {
								add(new EdgeSimple(0,nextf));
								lastf=nextf;
								count++;
							}
						}
					} // end of 'while'
				}

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

			case 'c': // empty, then current plot tree from the packing face drawing order
			{
				emptyMe();
				count += abutMore(DualGraph.easySpanner(packData,true));
				break;
			}
			
			case 's': // empty, then dual spanning tree, avoid 'poisonEdges'
			{
				emptyMe();
				
				// optional: {f} first face
				int f=0;
				try {
					f=Integer.parseInt(its.next());
				} catch (Exception ex) {
					f=0;
				}
				if (f>0 && f<packData.faceCount) {
					GraphLink gl=DualGraph.buildDualGraph(packData,f,packData.poisonEdges);
					count += abutMore(DualGraph.drawSpanner(packData,gl,f));
					break;
				}
				
				// default: spanning tree based on face drawing order
				count += abutMore(DualGraph.easySpanner(packData,false));
				break;
			}
			
			case 'R': // empty, then faces from red chain, or side
			{
				emptyMe();
				if (packData.redChain==null) break;
				
				// is there a side indicated (indexed from 0)?
				int sideNum=-1;
				try {
					sideNum=Integer.parseInt(its.next());
				} catch (Exception ex) {
					sideNum=-1;
				}
				if (sideNum<0 || sideNum>=packData.getSidePairs().size())
					sideNum=-1;
				
				// no side indicated, default to full redchain
				RedList rlst=(RedList)packData.redChain;
				if (sideNum==-1) { 
					add(new EdgeSimple(0,rlst.face));
					int curr=rlst.face;
					RedList trace=rlst.next;
					while (trace!=(RedList)packData.redChain) {
						if (curr!=trace.face)
							add(new EdgeSimple(curr,trace.face));
						curr=trace.face;
						trace=trace.next;
						count++;
					}
					// 'Ra' want to close the list				
					if (trace==(RedList)packData.redChain && 
							(str.length()>1 && str.charAt(1)=='a')) {
						if (curr!=trace.face)
							add(new EdgeSimple(curr,trace.face));
						count++;
					}
					break;
				}

				// else, do the chosen side
				SideDescription ep=packData.getSidePairs().get(sideNum);
				rlst=(RedList)ep.startEdge;
				add(new EdgeSimple(0,rlst.face)); // root
				int curr=rlst.face;
				RedList trace=rlst.next;
				// just one face?
				if (ep.startEdge==ep.endEdge) {
					count++;
					break;
				}
				while (trace!=ep.endEdge) {
					if (curr!=trace.face) {
						add(new EdgeSimple(curr,trace.face));
						count++;
					}
					curr=trace.face;
					trace=trace.next;
					System.out.println("c,f = "+curr+" "+trace.face);
				} 
				if (trace==ep.endEdge)
					if (curr!=trace.face) {
						add(new EdgeSimple(curr,trace.face));
						count++;
						System.out.println("c,f = "+curr+" "+trace.face);
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
	  * Create a new EdgeLink that eliminates duplicate edges.
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
}
