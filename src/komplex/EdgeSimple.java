package komplex;

/**
 * Simply an ordered pair (v,w) of vertices for use in, eg, EdgeLink.java.
 * Added (12/2010) 'util' element for optional additional info. 
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
	
	public EdgeSimple() {
		this(0,0);
	}
	
	public void setEdgeUtil(int k) {
		util=k;
	}
	
	public int getEdgeUtil() {
		return util;
	}
	
	public boolean isEqual_ordered(EdgeSimple ed) {
		return (v==ed.v && w==ed.w); 
	}
	
	public boolean isEqual_unordered(EdgeSimple ed) {
		return ((v==ed.v && w==ed.w) || (v==ed.w && w==ed.v));
	}
}
