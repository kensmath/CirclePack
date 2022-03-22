package dataObject;

import complex.Complex;
import dcel.HalfEdge;
import exceptions.ParserException;
import packQuality.QualMeasures;
import packing.PackData;

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
