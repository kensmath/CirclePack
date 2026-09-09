package rePack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.Vertex;
import complex.Complex;
import geometry.EuclMath;
import input.CommandStrParser;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;

/**
 * The aim is to circle pack topological discs in the
 * eucl setting so that the boundary circles are all
 * orthogonal to the unit circle. This can be done
 * easily for non-branched packings by doubling and
 * max_pack on the sphere. However, with no spherical 
 * packing algorithm, this won't work for branched 
 * packings. Hence this effort.
 * 
 * NOTE: this is a PackExtender only until we can
 * find the key to an algorithm. The "cx_error" seems
 * to be the best to watch. 
 * 
 * @author Ken Stephenson, started 8/2026
 * 
 */
public class OrthoPack extends PackExtender {
	public int fails; // center not outside unit disc
	public ArrayList<Double> bdry_errors;
	public double avg_error;
	public double max_error;
	NodeLink bdry; // list of all bdry vertices
	NodeLink worst; // picking verts with worst errors
	Complex[] tangs;
	public double min_tang; // smallest |tang pt|
	public int status; // 0=needs update, 1=avg_error, 2=worst
	
	// Constructor
	public OrthoPack(PackData p) {
		super(p);
		extensionType="ORTHOPACKER";
		extensionAbbrev="OP";
		toolTip="'OrthoPack': for developing routines that "+
				"will created packings with boundary circles "+
				"on the unit circle";
		registerXType();
		int rslt;
		try {
			if (p.intrinsicGeom!=0 || p.genus>0) {
				CirclePack.cpb.errMsg("usage: OrthoPack is for topological discs");
				rslt=0;
			}				
			else {
				rslt=cpCommand(extenderPD,"geom_to_h");
				cpCommand(extenderPD,"set_rad 8.0 b");
				cpCommand(extenderPD,"repack 1000");
				cpCommand(extenderPD,"layout");
				cpCommand(extenderPD,"geom_to_e");
			}
		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			CirclePack.cpb.errMsg("OP: failed to initialize");
			running=false;
		}
		if (running) {
			extenderPD.packExtensions.add(this);
			bdry=new NodeLink(extenderPD,"b");
			tangs=new Complex[bdry.size()];
			updateData(1);
		}
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		if (cmd.startsWith("error")) {
			if (status==0)
				updateData(1);
			StringBuilder strbld=new StringBuilder("OP error:\n");
			strbld.append("  avg_error = "+String.format("%.6e",avg_error));
			strbld.append("  max_error = "+String.format("%.6e",max_error));
			msg(strbld.toString());
			return 1;
		}
		
		if (cmd.startsWith("tscale")) {
			if (status==0)
				updateData(1);
			cpCommand(extenderPD,"scale "+String.format("%.6e",1.0/min_tang));
			return cpCommand(extenderPD,"disp -wr");
		}
		
		if (cmd.startsWith("r_adj")) {
			NodeLink vlink;
			if (flagSegs!=null && flagSegs.size()>0)
				items=flagSegs.remove(0);
			vlink=new NodeLink(extenderPD,items);
			Iterator<Integer> vlst=vlink.iterator();
			while (vlst.hasNext()) {
				int v=vlst.next();
				Vertex vert=extenderPD.packDCEL.vertices[v];
				double rad=vert.rad;
				double x=Math.sqrt(rad*rad+1.0);
				double c=vert.center.abs();
				double newrad=makeAdjustment(1-x/c,rad);
				extenderPD.setRadius(v,newrad);
				status=0;
			}
			if (status==0)
				updateData(1);
			CommandStrParser.jexecute(extenderPD,"repack 2000");
			return CommandStrParser.jexecute(extenderPD,"layout");
		}
		
		if (cmd.startsWith("run")) {
			int iters=1;
			if (flagSegs!=null && flagSegs.size()>0 &&
				(items=flagSegs.remove(0)).size()>0) {
				try {
					iters=Integer.parseInt(items.get(0));
				} catch(Exception ex) {
					CirclePack.cpb.errMsg("usage: orthoPack run {n}");
					return 0;
				}
			}
			return runManager(iters);
		}
		
		return 0;
	}
	
	/**
	 * runManager schedules runs of 'runList' based
	 * on evolving 'avg_error', 'max_error', iteration
	 * count. It will switch between running through
	 * all bdry vertices or just a selected subset,
	 * typically those with the worst errors. It seems
	 * that the strategy used depends on the size of
	 * the packing and the size of the evolving avg_error 
	 * @param n int
	 * @return
	 */
	public int runManager(int n) {
		if (status==0)
			updateData(1); // to start, don't need 'worst'
		
		// first stage -- global adjustments
		// emergency-stop check -- once per outer pass.
		CPBase.checkCancel();
		
		int tick=0;
		while (avg_error>.001 && tick<n) 
			tick=runList(bdry,n);
		
		// Now work on worst
		updateData(2);
		int wtick=0;
		if (avg_error>.001 && max_error>.002 && wtick<n) {
			wtick=runList(worst,n);
		}
		
		// global adjustments again
		int ntick=0;
		while (avg_error>.03 && ntick<n)
			ntick=runList(bdry,n);
		
		System.out.println("run results: tick="+tick+"; wtick="+wtick+": ntick="+ntick);
		return tick+wtick+ntick;
	}
	
	public int runList(NodeLink alist,int n) {
		int m=alist.size();
		boolean hit=false;
		if (m<bdry.size() && status<2)
			updateData(2); // this updates bdry_errors
		else if (status==0)
			updateData(1);
		double errorin=avg_error;
		double errorout=avg_error/2.0;
		int tick=0;
		while (errorout<errorin && tick<n) {

			// emergency-stop check -- once per outer pass.
			CPBase.checkCancel();

			errorin=avg_error;
			for (int j=0;j<alist.size();j++) {
				int v=alist.get(j);
				Vertex vert=extenderPD.packDCEL.vertices[v];
				double rad=vert.rad;
				double err=bdry_errors.get(j);
				double newrad=makeAdjustment(err,rad);
				extenderPD.setRadius(v,newrad);
				hit=true;
			}
			if (hit) {
				CommandStrParser.jexecute(extenderPD,"repack 1000");
				CommandStrParser.jexecute(extenderPD,"layout");
				if (m<bdry.size() && status<2)
					updateData(2); // this updates bdry_errors
				else 
					updateData(1);
			}
			errorout=avg_error;
			tick++;
		} // end of one full pass
		msg("othopack run: avg_error = "+String.format("%.6e",avg_error));
		return tick;
	}

	/**
	 * make adjustment to radius rad; err = x/c-1.0
	 * @param err double
	 * @param rad double
	 * @return double, new radius
	 */
	public double makeAdjustment(double err,double rad) {
		return rad*(1.0+err); // multiply by x/c
	}

	/** 
	 * Update 'error', 'bdry_errors', 'fails', 'tangs', 'min_tang'. 
	 * The error at bdry vert v is x/c-1.0 where c=center.abs()
	 * x=sqrt(rad^2+1). (x is the distance the center should 
	 * be for this radius if orthogonal). These signed errors 
	 * are put in bdry_err array. "error" is the average of 
	 * |x/c-1.0| over all vertices. 
	 * 'fails' is the count of centers inside the unit disc.
	 * @param worsttoo boolean; true, then update 'worst'
	 * @return 
	 */
	public void updateData(int worsttoo) {
		int n=bdry.size();
		double accum=0.0;
		avg_error=0.0;
		max_error=0.0;
		fails=0;
		bdry_errors=new ArrayList<Double>(n);
		
		// Find all tangency points and 'min_tang'
		min_tang=1.0;
		Iterator<Integer> blst=bdry.iterator();
		int tick=0;
		while (blst.hasNext()) {
			Vertex vert=extenderPD.packDCEL.vertices[blst.next()];
			Vertex wert=vert.halfedge.twin.origin;
			Complex z=EuclMath.eucl_tangency(vert.center,wert.center,vert.rad,wert.rad);
			tangs[tick++]=z;
			min_tang=(z.abs()<min_tang) ? z.abs() : min_tang;
		}

		// fill 'fails', 'bdry_errors', 'error'
		for (int j=0;j<n;j++) {
			int v=bdry.get(j);
			Vertex vert=extenderPD.packDCEL.vertices[v];
			double c=vert.center.abs();
			if (c<=1.0)
				fails++;
			double x=Math.sqrt(vert.rad*vert.rad+1.0);
			double err=x/c-1.0;
			bdry_errors.add(j,err);
			double abserr=Math.abs(err);
			max_error = (abserr>max_error) ? abserr : max_error;
			accum+=Math.abs(abserr);
		}
		avg_error=accum/bdry.size(); // average |c-x|
		status=1;

		// update the list of 10% worst vertices
		if (worsttoo==2) {
		    int keep = (int) Math.ceil(0.10 * n); // 10%
		    keep = Math.max(1, Math.min(keep, n));

		    // sort positions 0..n-1 by error, descending, 
		    //   rather than sorting the lists themselves -- 
		    //   keeps 'bdry' <-> 'bdry_errors' in sync
		    NodeLink pos = new NodeLink(extenderPD);
		    for (int i = 0; i < n; i++)
		        pos.add(i);
		    	pos.sort((a, b) -> Double.compare(bdry_errors.get(b), bdry_errors.get(a)));

		    worst = new NodeLink(extenderPD);
		    for (int i = 0; i < keep; i++)
		        worst.add(bdry.get(pos.get(i)));
		    status=2;
		}
			
	}

}
