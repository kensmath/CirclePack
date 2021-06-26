package komplex;

import dcel.HalfEdge;

/**
 * Simply an ordered pair (v,w) of vertices for use in, eg, EdgeLink.java.
 * Added (12/2010) 'util' element for optional additional info. See
 * new 'GraphSimple' for, e.g., dual edges.
 * @author kens
 *
 */
public class EdgeSimple {
	
	public int v;
	public int w;
	public int util;
	
	// Constructor
	public EdgeSimple(int vv,int ww) {
		v=vv;
		w=ww;
		util=0;
	}
	
	public EdgeSimple(EdgeSimple es) {
		this(es.v,es.w);
	}
	
	public EdgeSimple(HalfEdge he) {
		this(0,0);
		if (he!=null) {
			v=he.origin.vertIndx;
			w=he.twin.origin.vertIndx;
		}
	}
	
	public EdgeSimple() {
		this(0,0);
	}
	
	public void setEdgeUtil(int k) {
		util=k;
	}
	
	public int getEdgeUtil() {
		return util;
	}
	
	/**
	 * does this equal given es
	 * @param es
	 * @param orient
	 * @return
	 */
	public boolean isEqual(EdgeSimple es,boolean orient) {
		if (orient)
			return (v==es.v && w==es.w);
		else
			return ((v==es.v && w==es.w) || (v==es.w && w==es.v));
	}
	
	/** is this actually a 'GraphSimple' (e.g., a face pair)?
	 * @param edge EdgeSimple
	 * @return boolean
	 */
	public boolean isGraphSimple(EdgeSimple edge) {
		if (edge instanceof GraphSimple)
			return true;
		return false;
	}
	
	public EdgeSimple clone() {
		return new EdgeSimple(v,w);
	}
	
	public String toString() {
		return new String("["+this.v+","+this.w+"]");
	}
	
}
