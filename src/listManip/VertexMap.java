package listManip;

import java.util.Iterator;

import komplex.EdgeSimple;
import packing.PackData;


/**
 * Convention is that entries are {old,new}, that is, pairing
 * an old index with its new index; for mapping, {domain,range},
 * so index in range identified with index in domain.
 * 
 * This is like EdgeLink, but it doesn't check whether edge.v 
 * and edge.w are legal indices for any particular packing.
 * @author kens
 */
public class VertexMap extends EdgeLink {

	private static final long 
	serialVersionUID = 1L;

	// Constructor
	public VertexMap() {
		super((PackData)null);
		packData=null;
	}

	public boolean add(EdgeSimple edge) {
		if (edge==null)
			return false;
		return super.add(edge);
	}
	
	public boolean add(int a,int b) {
		return super.add(new EdgeSimple(a,b));
	}
	
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the indices.
	 * @return NodeLink
	 */
	public VertexMap makeCopy() {
		Iterator<EdgeSimple> vm=this.iterator();
		VertexMap newlist=new VertexMap();
		while (vm.hasNext()) {
			newlist.add((EdgeSimple)vm.next());
		}
		return newlist;
	}
	
	/** interchange entries of each 'EdgeSimple'. 
	 * Used, e.g., to convert 'oldnew' to 'newold'
	 * or vice-verse. 
	 */
	public VertexMap flipEachEntry() {
		EdgeLink el=(EdgeLink)this;
		return (VertexMap)el.flipEachEntry();
	}
	
	/**
	 * In many situations, we do repeated adjustments in DCEL and
	 * need to connect a final new index with its original from 
	 * several steps back. Start with null VertexMap, and each stage
	 * compose with VertexMap from that stage. Return the composition: 
	 * 'domainMap' followed by given 'rangeMap'. Typically, for (b,c) 
	 * in 'rangeMap', look for (a,b) in 'domainMap'. If found, put 
	 * (a,c) in output map. 
	 * Some details:
	 *   * if (b,c) is in rangeMap and (*,b) does not exist in domainMap,
	 *     then (b,c) is inserted in output. (I.e., as though (b,b) were
	 *     in domainMap).
	 *   * may be ambiguous: if (a1,b) before (a2,b) in 'domainMap', then 
	 *     (a1,c) is the result (i.e., the first occurrance of (*.b)). 
	 *   * However, if (b,c1) before (b,c2) in 'rangeMap', then (*,c2) 
	 *     is the outcome (i.e., the last occurrance of (b,*)).
	 *   * if 'domainMap' null, return 'rangeMap'
	 *   * if 'rangeMap' null, return 'domainMap'
	 *   * if (b,c) in 'rangeMap' and b==c, then ignore.
	 * Note: typically, not including (x,x) entries in 'VertexMap's
	 * @param domainMap VertexMap
	 * @param rangeMap VertexMap
	 * @return new VertexMap; null on error or empty map
	 */
	public static VertexMap followedBy(VertexMap domainMap,VertexMap rangeMap) {
		if (domainMap==null)
			return rangeMap;
		if (rangeMap==null || rangeMap.size()==0) 
			return domainMap;
		VertexMap nmap=new VertexMap();
		Iterator<EdgeSimple> rmis=rangeMap.iterator();
		while (rmis.hasNext()) {
			EdgeSimple edge=(EdgeSimple)rmis.next();
			if (edge.v<=0 || edge.w<=0 || edge.v==edge.w)
				continue;
			int b=edge.v;
			int v=domainMap.findV(b);
			if (v>0) 
				nmap.add(new EdgeSimple(v,edge.w));
			else if (v==0) // nothing matching?
				nmap.add(new EdgeSimple(edge.v,edge.w)); // add edge copy
		}
		if (nmap.size()==0) 
			return null;
		return nmap;
	}
	
	/**
	 * Given w, return v if this list contains (v,w) (use first occurrence).
	 * @param w second entry
	 * @return v, 0 if not found
	 */
	public int findV(int w) {
		Iterator<EdgeSimple> ed=this.iterator();
		EdgeSimple edge=null;
		while (ed.hasNext()) {
			edge=(EdgeSimple)ed.next();
			if (edge.w==w) return edge.v;
		}
		return 0;
	}
	
	/**
	 * Given v, return w if this list contains (v,w) (use first occurrence).
	 * @param v first entry
	 * @return w, 0 if not found
	 */
	public int findW(int v) {
		Iterator<EdgeSimple> ed=this.iterator();
		EdgeSimple edge=null;
		while (ed.hasNext()) {
			edge=(EdgeSimple)ed.next();
			if (edge.v==v) return edge.w;
		}
		return 0;
	}
	
	 /**
	  * Clone with the same 'PackData'
	  */
	 public VertexMap clone() {
		 VertexMap vm=new VertexMap();
		 vm.packData=packData;
		 Iterator<EdgeSimple> tis=this.iterator();
		 while (tis.hasNext()) 
			 vm.add(tis.next().clone());
		 return vm;
	 }
	
}
