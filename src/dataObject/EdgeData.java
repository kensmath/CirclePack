package dataObject;

import combinatorics.komplex.HalfEdge;
import complex.Complex;
import exceptions.ParserException;
import packing.PackData;
import packing.QualMeasures;

/**
 * Gathers data on an edge, as needed for inquiries or for
 * the 'Pack Info' window in GUI mode.
 * @author kstephe2
 *
 */
public class EdgeData {
	PackData parent;
	HalfEdge hedge;
	public String edgeStr;
	public double invDist;
	public double schwarzian;
	public double intended; // intended length
	public double edgelength; // actual length
	
	// red edge info
	public boolean isRed;
	public boolean isTwinned; // does it have a twin that's red
	public Complex redCenter;
	public double redRad;
	
	public EdgeData(PackData p,HalfEdge he) {
		if (he==null)
			throw new ParserException("faulty edge");
		hedge=he;
		int ev=he.origin.vertIndx;
		int ew=he.twin.origin.vertIndx;
		if (ev<=0 || ev>p.nodeCount || ew<=0 || ew>p.nodeCount)
			throw new ParserException("improper end points");
		edgeStr=new String(ev+" "+ew);
		invDist=he.getInvDist();
		schwarzian=he.getSchwarzian();
		edgelength=QualMeasures.edge_length(p, ev, ew);
		intended=QualMeasures.desired_length(p, ev, ew);
		isRed=(hedge.myRedEdge!=null);
		isTwinned=false;
		if (isRed) {
			redCenter=new Complex(hedge.myRedEdge.getCenter());
			redRad=hedge.myRedEdge.getRadius();
			isTwinned=(hedge.myRedEdge.twinRed!=null);
		}
	}
	

}
