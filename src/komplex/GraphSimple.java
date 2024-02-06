package komplex;

import packing.PackData;

/**
 * Essentially the same as 'EdgeSimple', but allows us to
 * distinguish pairs associated with dual graphs.
 * @author kstephe2, 6/2020, plan to introduce as needed
 *
 */
public class GraphSimple extends EdgeSimple {
	
	public GraphSimple() {
		super(0,0);
	}
	
	/**
	 * form a root (0,f)
	 * @param root int
	 */
	public GraphSimple(int f) {
		super(0,f);
	}

	public GraphSimple(int f,int g) {
		v=f;
		w=g;
	}

	// use in place of cast
	public GraphSimple(EdgeSimple es) {
		v=es.v;
		w=es.w;
	}
	
	/** Convert <v,w> edge to dual edge <f,g>;
	 * note <f,g> points clockwise to <v,w>. 
	 * @param p PackData
	 * @param es EdgeSimple
	 */
	public GraphSimple(PackData p,EdgeSimple es) {
		EdgeSimple de=p.dualEdge(es.v,es.w);
		v=de.v;
		w=de.w;
	}
		
	/**
	 * does this equal given gs
	 * @param es
	 * @param orient
	 * @return
	 */
	public boolean isEqual(GraphSimple gs,boolean orient) {
		if (orient)
			return (v==gs.v && w==gs.w);
		else
			return ((v==gs.v && w==gs.w) || (v==gs.w && w==gs.v));
	}
	
	/** 
	 * Convert type to 'EdgeSimple'.
	 * @return EdgeSimple
	 */
	public EdgeSimple convert() {
		return new EdgeSimple(this.v,this.w);
	}
	                            

}
