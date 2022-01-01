package komplex;

import java.util.Iterator;

import listManip.EdgeLink;
import listManip.GraphLink;
import listManip.NodeLink;
import packing.PackData;

/**
 * For creating/manipulating combinatoric information for
 * the dual graphs G for circle packing complexes K, i.e., the
 * vertices of G correspond to faces of K, an edge of G, <f,g>
 * indicates that faces f and g share an edge, and the 
 * (polygonal) faces of G correspond to the vertices of K.
 * 
 * * Now (2/2009) the aim is building spanning trees for G
 *   with static methods.
 *   
 * * Also (6/2010) building plottrees, dual trees showing the
 * order in which faces are laid down in drawing order.
 * (Note that a plottree will generally not pick up all the
 * nodes of the dual graph; some nodes are not needed since
 * the three circles of that face have already been laid out
 * by other faces.)
 * 
 * * Also (12/2010), treeTrim, which modifies a tree by removing
 * designated edges, and reattaching the cutoff tree by 
 * introducing some non-proscribed edge. Main idea is to be 
 * able to easily modify a plottree to get a new plot tree with
 * particular properties --- eg. to get a particularly nice
 * layout of a torus.
 * @author kens
 *
 */
public class DualGraph {

	/**
	 * Build dual graph of the packing p starting with 'startface'.
	 * Should "spiral" out by generations, should have no repeats,
	 * doesn't cross poison edges if any are specified 
	 * @param p PackData
	 * @param startface int
	 * @param poison EdgeLink, poison, don't cross (may be null)
	 * @return GraphLink
	 */
	public static GraphLink buildDualGraph(PackData p,int startface,EdgeLink poison) {
		GraphLink dlink=new GraphLink();
		if (startface<=0 || startface>p.faceCount)
			startface=p.firstFace;
		if (startface==0) // default to 1
			startface=1;
		int startvert=p.packDCEL.faces[startface].getVerts()[0];
		NodeLink nextNodes=new NodeLink(p,startvert);
		NodeLink currNodes=new NodeLink(p);
		int []gen=new int[p.nodeCount+1];
		gen[startvert]=1;
		int tick=1;
		while (nextNodes.size()>0) {
			tick++;
			currNodes=nextNodes;
			nextNodes=new NodeLink(p);
			Iterator<Integer> cl=currNodes.iterator();
			while (cl.hasNext()) {
				int v=cl.next();
				for (int j=0;j<(p.countFaces(v)+p.getBdryFlag(v));j++) {
					int w=p.kData[v].flower[j];
					if (gen[w]==0) {
						gen[w]=tick;
						nextNodes.add(w);
					}
					if ((gen[w]==gen[v] && w>v || gen[w]>gen[v]) &&
							!EdgeLink.ck_in_elist(poison,v,w)) {
						EdgeSimple edge=p.dualEdge(v,w);
						if (edge!=null) 
							dlink.add(edge);
					}
				}
			}
		}
		if (dlink.size()==0) return null;
		return dlink;
	}
}
