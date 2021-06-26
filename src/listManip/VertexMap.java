package listManip;

import java.util.Iterator;

import komplex.EdgeSimple;
import packing.PackData;


/**
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
	 * Used, e.g., to convert 'newOld' to 'oldNew'. 
	 */
	public VertexMap flipEachEntry() {
		EdgeLink el=(EdgeLink)this;
		return (VertexMap)el.flipEachEntry();
	}
	
	/**
	 * Return the composition: 'this' followed by given 'maP'. So if
	 * 'this' has (a,b) and 'maP' has (b,c), then result contains (a,c).
	 * @param maP
	 * @return new VertexMap; null on error or empty map
	 */
	public VertexMap followedBy(VertexMap maP) {
		if (maP==null || maP.size()==0) return null;
		VertexMap nmap=new VertexMap();
		Iterator<EdgeSimple> vml=this.iterator();
		while (vml.hasNext()) {
			EdgeSimple edge=(EdgeSimple)vml.next();
			int w=maP.findW(edge.w);
			if (w>0) nmap.add(new EdgeSimple(edge.v,w));
		}
		if (nmap.size()==0) return null;
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
