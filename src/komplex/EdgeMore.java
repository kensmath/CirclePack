package komplex;


/**
 * Extends 'EdgeSimple' to contain more data: overlaps (inversive distances),
 * and in future perhaps colors, linethicknesses, etc.
 * This is generally more than we need for routine overlap information, but
 * we may use it for that in the future. Recall: default overlap is 1.0,
 * corresponding to tangency.
 * @author kens
 *
 */
public class EdgeMore extends EdgeSimple {
	
	public double overlap; // assigned angle of overlap or inversive distance

	// Constructors
	public EdgeMore(int v,int w) {
		super(v,w);
		overlap=1.0; // default
	}
	
	public EdgeMore(int v,int w,double olap) {
		super(v,w);
		overlap=olap;
	}
	
}
