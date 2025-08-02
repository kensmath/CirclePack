package schwarzWork;

import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import exceptions.CombException;
import geometry.CircleSimple;
import input.CommandStrParser;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.StringUtil;

/**
 * This class is for experimenting with use of
 * intrinsic schwarzians in computing circle
 * packings, particularly on the sphere or 
 * on projective surfaces. The intrinsic schwarzians
 * and the aims are maintained in the packing 
 * (though schwarzians are sometimes converted 
 * to 'uzians' for computations).
 * 
 * The code maintains 'currentErrors' which is one way
 * to measure the quality of a potential packing. Each
 * time an adjustment to an edge schwarzian is made,
 * the errors of its two ends must be updated.
 * 
 * I am first going to try a randomized approach:
 * random edge, apply adjustment strategy (modes?),
 * check status (perhaps look for worst edges), etc.
 * 
 * @author ken stephenson, February 2025
 * 
 */
public class SchwarzPack extends PackExtender {
	final static double sqrt3=Math.sqrt(3);
	final static double S3=1.0-1.0/sqrt3; // schwarzian of degree 3.

	// store 'SchEdgeData' array for edges
	//    subject to adjustment; 'theEdges[j]' holds
	//    index of edge j in 'edgeData' or -1.
	SchEdgeData[] edgeData; // first entry null

	// maintain updated lists of "errors", but only
	//   for vertices/edges subject ot adjustment
	Complex[] vertErrors; 
	double[][] edgeErrors; // cclw/clw errors, on
	
	int test_mode; // mode for making adjustments

	// Constructors
	public SchwarzPack(PackData p) {
		super(p);
		extenderPD=p;
		extensionType="SCHWARZPACK";
		extensionAbbrev="SP";
		toolTip="'SchwarzPack' allows for experiments in using "+
			"intrinsic schwarzians to compute packings of the "+
				"sphere or of projective surfaces.";
		registerXType();
		test_mode=1; // default
		try {
			int ecount=extenderPD.packDCEL.edgeCount;
			vertErrors=new Complex[extenderPD.nodeCount+1];
			edgeErrors=new double[ecount+1][2];
			edgeData=new SchEdgeData[ecount+1];
			initiateEdgeData();
			updateErrors();
		} catch (Exception ex) {
			Oops("Failed in computing errors");
			running=false;
		}
		if (running) {
			extenderPD.packExtensions.add(this);
		}
	}
	
	/**
	 * Set up 'edgeData' array. Note that if edge's
	 * index is j, then 'theEdges[j]' is either 0 
	 * or the edge's array index within 'edgeData'.
	 */
	public void initiateEdgeData() {
		NodeLink intv=new NodeLink(extenderPD,"a");
		Iterator<Integer> ilst=intv.iterator();
		while (ilst.hasNext()) {
			Vertex vert=extenderPD.packDCEL.vertices[ilst.next()];
			if (vert.isBdry()) {
				continue;
			}
			HalfEdge htrace=vert.halfedge;
			if (vert.getNum()==3) { // not subject to adjustment
				htrace.setSchwarzian(S3);
				htrace=htrace.prev.twin;
				htrace.setSchwarzian(S3);
				htrace=htrace.prev.twin;
				htrace.setSchwarzian(S3);
				continue;
			}
			do {
				Vertex oppv=htrace.twin.origin;
				if (oppv.getNum()!=3 || oppv.isBdry()) {
					SchEdgeData sed=new SchEdgeData(htrace);
					edgeData[htrace.edgeIndx]=sed;
				}
				htrace=htrace.prev.twin; // cclw
			} while (htrace!=vert.halfedge);
		} // done with all interior vertices
	}
	
	/**
	 * Update errors and colors for 'vert' and its
	 * edges.
	 * @param vert Vertex
	 * @param print boolean; print errors
	 * @return int
	 */
	public int updateVert(Vertex vert,boolean print) {
		int count=0;
		HalfEdge he=vert.halfedge;
		StringBuilder strbld=null;
		if (print)
			strbld=new StringBuilder(" Errors for vert "+vert.vertIndx+":\n");
		do {
			SchEdgeData sed=edgeData[he.edgeIndx];
			sed.colorEdge(test_mode);
			edgeErrors[he.edgeIndx][0]=sed.newSch_cclw[0];
			edgeErrors[he.edgeIndx][1]=sed.newSch_clw[0];
			if (print)
				strbld.append("edge errors"+sed.myHEdge+
						": cclw="+sed.newSch_cclw[0]+"; clw="+
						sed.newSch_clw[0]+"\n");
			he=he.prev.twin;
			count++;
		} while(he!=vert.halfedge);
		
		// error for the vertex itself
		vertErrors[vert.vertIndx]=getVertErr(vert);
				
		if (print) {
			strbld.append("\n  vert error = "+vertErrors[vert.vertIndx]);
			System.out.println(strbld.toString());
		}
		
		return count;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		int count=0;
		
		// ============ analyze edge origin ========
		if (cmd.startsWith("anal")) {
			HalfEdge edge=HalfLink.grab_one_edge(extenderPD, flagSegs);
			Vertex vert=edge.origin;
			double eSch=edge.getSchwarzian();
			int eindx=edge.edgeIndx;
			SchEdgeData sed=edgeData[eindx];
			StringBuilder strbld=
				new StringBuilder("Analyze "+edge+
					" with schwarzian "+eSch+"\n");
			
			// show computed schwarzians, cclw/clw
			strbld.append("\n  Computed schwarzians: cclw="
					+String.format("%.8e",eSch+(sed.newSch_cclw[0]))+"; clw="
					+String.format("%.8e",eSch+(sed.newSch_clw[0])));
			
			// n-3 previous schwarzians
			strbld.append("\n  "+(sed.myN-3)+" cclw schwarzians: ");
			HalfEdge he=sed.startCCLW;
			for (int j=1;j<=sed.myN-3;j++) {
				strbld.append(String.format("%.8e",he.getSchwarzian())+"; ");
				he=he.prev.twin;
			}
			
			// n-3 following schwarzians
			he=sed.startCLW;
			strbld.append("\n  "+(sed.myN-3)+" clw schwarzians: ");
			for (int j=1;j<=sed.myN-3;j++) {
				strbld.append(String.format("%.8e",he.getSchwarzian())+"; ");
				he=he.twin.next;
			}
			
			// show error at vertex
			strbld.append("\n  Vertex error = "+vertErrors[vert.vertIndx]);
			
			// ??? other stuff to show?
			
			System.out.println(strbld.toString());
			return 1;
		}
		
		// ============ set test mode =================
		// 1: random edge, move part way toward average of
		//    2 computations for each end.
		// 2: ??
		//
		else if (cmd.startsWith("test_m")) {
			
			test_mode=1;
			try {
				test_mode=Integer.parseInt(flagSegs.remove(0).get(0));
			} catch(Exception ex) {
				errorMsg("usage: test_mode [m] ");
				return 0;
			}
			return 1;
		}

		else if (cmd.startsWith("status")) {
			
			// find list of edges
			NodeLink vlink=null;
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0)
				vlink=new NodeLink(extenderPD,items);
			else
				vlink=new NodeLink(extenderPD,"i");
	
			// color vert and its edges, give vert error
			Iterator<Integer> vlt=vlink.iterator();
			while (vlt.hasNext()) {
				Vertex vert=extenderPD.packDCEL.vertices[vlt.next()];
				updateVert(vert,false);
				this.msg("status of vert "+vert.vertIndx+
						": error is "+vertErrors[vert.vertIndx]);
				count++;
			}
			return count;
		}
		
		// draw halfedges from 'edgeData' in color
		else if (cmd.startsWith("edge")) {
			for (int e=1;e<=extenderPD.packDCEL.edgeCount;e++) {
				SchEdgeData sed=edgeData[e];
				if (sed!=null) {
					CommandStrParser.jexecute(extenderPD,"disp -rft8 "+sed.myHEdge);
					count++;
				}
			}
			return count;
		}
	
		// show eucl flower in other window; form eflo -q{q} v
		else if (cmd.startsWith("eflo")) { 

			// choose packing for image and vertex: 
			//    default to next pack, mod 3 and first interior
			//    vertex
			int qnum=(extenderPD.packNum+1)%CPBase.NUM_PACKS;
			int vindx=NodeLink.grab_one_vert(extenderPD,"i");
			try {
				items=flagSegs.get(0);
				String str=items.get(0);
				if (StringUtil.isFlag(str)) {
					 int qq=StringUtil.qFlagParse(str);
					 if (qq>0 && qq!=extenderPD.packNum)
						 qnum=qq;
					 items.remove(0);
				}
				vindx=NodeLink.grab_one_vert(extenderPD,items.get(0));
			} catch(Exception ex) {
				Oops("eflower: |sm| sch -q{x} v"+ex.getMessage());
			}

			Vertex vert=extenderPD.packDCEL.vertices[vindx];
			if (vert.isBdry())
				return 0;
			Complex err=new Complex(0.0);
			CircleSimple cs=new CircleSimple();
			PackData newData=PackData.schFlowerErr(vert, err, cs);
			vertErrors[vert.vertIndx]=new Complex(err);
  		  	if (newData==null) 
   		  		throw new CombException("|sp| eflower has failed");
  		  	int nDn=newData.nodeCount;
   		  	CPBase.packings[qnum]=CirclePack.cpb.swapPackData(newData,qnum,false);
   		  	CommandStrParser.jexecute(CPBase.packings[qnum],"Disp -w -c");
   		  	// also draw new last petal.
   		  	CPBase.packings[qnum].packDCEL.vertices[nDn].setCircleSimple(cs);
   		  	CommandStrParser.jexecute(CPBase.packings[qnum],"disp -cc195 "+nDn);
			msg("Layout error in p"+qnum+" for flower "+vindx+" is "+err); // this contains the error
			return 1;
		}
		else if (cmd.startsWith("run")) {
			int N=1000; // default number of runs
			try {
				N=Integer.parseInt(flagSegs.get(0).get(0));
			} catch (Exception ex) {
				Oops("usage: run [N] to specify number of iterations");
			}
			int ans=runTrials(N);
			return ans;
		}
		else if (cmd.startsWith("one")) { // one edge
			HalfEdge he=null;
			try {
				he=HalfLink.grab_one_edge(extenderPD,flagSegs);
			} catch(Exception ex) {
				return 0;
			}
			double factor=.1;
			he.setSchwarzian(newSchwarzian(he,factor,true));
			vertErrors[he.origin.vertIndx]=
					new Complex(getVertErr(he.origin));
			vertErrors[he.twin.origin.vertIndx]=
					new Complex(getVertErr(he.twin.origin));
			return 1;
		}
		
		// update all vert/edge errors and colors.
		else if (cmd.startsWith("reset")) { 
			updateErrors();
			return 1;
		}

		else if (cmd.startsWith("worst")) {
			int[] errV=worstErrors();
			double rplus=0.0;
			double rminus=0.0;
			double aplus=0.0;
			double aminus=0.0;
			if (errV[0]!=0)
				rplus=vertErrors[errV[0]].x;
			if (errV[1]!=0)
				rminus=vertErrors[errV[1]].x;
			if (errV[2]!=0)
				aplus=vertErrors[errV[2]].y;
			if (errV[3]!=0)
				aminus=vertErrors[errV[3]].y;
			msg("Worst errors, radii/angles, positive/negative:");
			if (errV[0]>0)
				msg("Positive radius error = "+rplus+" at v="+errV[0]);
			if (errV[1]>0)
				msg("Negative radius error = "+rminus+" at v="+errV[1]);
			if (errV[2]>0)
				msg("Positive angle error = "+aplus+" at v="+errV[2]);
			if (errV[3]>0)
				msg("Negative angle error = "+aminus+" at v="+errV[3]);
			return 1;
		}
		return 0;
	}
	
	public int runTrials(int N) {
		int count=0;
		for (int j=1;j<=N;j++) {
			HalfEdge target=getRandEdge(extenderPD);
			double factor=.1;
			target.setSchwarzian(newSchwarzian(target,factor,false));
			count++;
		}
		updateErrors(); // this also sets vert colors
		return count;
	}
	
	/**
	 * If 'target' is an adjustable edge, return
	 * a new schwarzian that moves towards a
	 * computed schwarzian by the given factor 
	 * times the average of the cclw and clw 
	 * computed errors the edge and its twin.
	 *    new = current + factor * error
	 * @param target HalfEdge
	 * @param factor double
	 * @param print boolean, print errors
	 * @return Double, null for non-adjustable edge
	 */
	public double newSchwarzian(HalfEdge target,
			double factor,boolean print) {
		double currSch=target.getSchwarzian();
		SchEdgeData sed1=edgeData[target.edgeIndx];
		SchEdgeData sed2=edgeData[target.twin.edgeIndx];
		if (sed1==null && sed2==null)
			return currSch;
		
		// scherr will be thw average of cclw and clw
		//   errors for one or both HalfEdges.
		double scherr=0.0;
		double denom=0.0;
		if (sed1!=null) {
			scherr=sed1.newSch_cclw[0]+sed1.newSch_clw[0];
			denom=2.0;
		}
		
		if (sed2!=null) {
			scherr=scherr+sed1.newSch_cclw[0]+sed1.newSch_clw[0];
			if (denom>0.0)
				denom=4.0;
			else
				denom=2.0;
		}
		
		double newSch=currSch+factor*scherr/denom;
		StringBuilder strbld=null;
		
//debugging
if (print) {		
	strbld=new StringBuilder(" Next adjustment:");
	strbld.append("  target="+target);
	strbld.append(": original="+currSch+"; new="+newSch);
}

		// too small to change?
		if (Math.abs(scherr)<.0000000001)
			return currSch;
		
		// else, return new value
		
// debugging		
if (print)
	System.out.println(strbld.toString());

		return newSch;
	}

	/**
	 * Recompute all vertex and edge errors and set
	 * appropriate colors.
	 */
	public void updateErrors() {
		NodeLink vlist=new NodeLink(extenderPD,"i");
		Iterator<Integer> vlt=vlist.iterator();
		while (vlt.hasNext()) 
			updateVert(extenderPD.packDCEL.vertices[vlt.next()],false);
	}
		
	/**
	 * Compute the "error" for the given 'vert' and
	 * set the vertex color based on the anglesum 
	 * (imaginary part) of the error; error is 
	 * anglesum-aim. Red for anglesum too large,
	 * blue for too small.
	 * @param vert Vertex
	 * @return Complex error
	 */
	public Complex getVertErr(Vertex vert) {
		if (vert.isBdry())
			return null;
		CircleSimple cs=new CircleSimple();
		Complex err=new Complex(0.0);
		PackData.schFlowerErr(vert, err, cs);
		// color vert based on anglesum error
		//   based on [-pi/2,pi/2]
		double x=err.y/CPBase.piby2;
		if (x>0.0)
			vert.color=ColorUtil.red_interp(x);
		else
			vert.color=ColorUtil.blue_interp(-1.0*x);
		return err;
	}

	/**
	 * Identify the vertices with the worst errors in
	 * four categories: 
	 *   ans[0]: most positive radius error
	 *   ans[1]: most negative radius error
	 *   ans[2]: most positive angle error
	 *   ans[3]: most negative angle error
	 * ans[.]=0 means there were no instances of that
	 * comparison.
	 * @return int[4]
	 */
	public int[] worstErrors() {
		int[] ans=new int[4];
		double radplus=0.0;
		double radminus=0.0;
		double angplus=0.0;
		double angminus=0.0;
		for (int v=1;v<=extenderPD.nodeCount;v++) {
			Complex err=vertErrors[v];
			if (err==null)
				continue;
			double r=err.x;
			double a=err.y;
			
			if (r>radplus) {
				ans[0]=v;
				radplus=r;
			}
			if (r<radminus) {
				ans[1]=v;
				radminus=r;
			}
			if (a>angplus) {
				ans[2]=v;
				angplus=a;
			}
			if (a<angminus) {
				ans[3]=v;
				angminus=a;
			}
		}
		return ans;
	}
	
	// ---------------- static routines ----------------------

	/**
	 * Get a random interior edge, but don't
	 * accept if it has at least one interior
	 * end of degree 3, since that schwarzian
	 * will necessarily be 1-1/sqrt(3).
	 * @return Halfedge or null on failure
	 */
	public static HalfEdge getRandEdge(PackData p) {
		HalfLink hlk=new HalfLink(p,"ri");
		if (hlk==null || hlk.size()==0)
			return null;
		HalfEdge he=hlk.get(0); // there should be just one edge
		if ((he.origin.getNum()==3 && !he.origin.isBdry()) ||
				he.twin.origin.getNum()==3 && !he.twin.origin.isBdry())
			return null;
		return he;
	}

	public static double[] getUzians(Vertex vert) {
		if (vert.isBdry())
			return null;
		int n=vert.getNum();
		double[] uzians=new double[n];
		HalfEdge he=vert.halfedge;
		uzians[0]=1.0-he.getSchwarzian();
		he=he.prev.twin;
		int tick=1;
		while (he!=vert.halfedge) {
			uzians[tick++]=1.0-he.getSchwarzian();
			he=he.prev.twin;
		}
		return uzians;
	}
		
	// -------------------- command explanations --------
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("analyze","v w",null,
				"update and show data on origin of edge v w"));
		cmdStruct.add(new CmdStruct("test_mode","[mode]",null,
				"modes will (hopefully) emerge."));
		cmdStruct.add(new CmdStruct("rand","[type]",null,
				"Initiate random edge schwarzians"));
		cmdStruct.add(new CmdStruct("edge",null,null,
				"update computed schwarzians and display "+
						"in color"));
		cmdStruct.add(new CmdStruct("one","{v w}",null,
				"adjust schwarzian of a single edge, "+
				" given the current mode"));
		cmdStruct.add(new CmdStruct("run","[N}",null,
				"Run trial with N iterations, default to 1000"));
		cmdStruct.add(new CmdStruct("status","{v ..}",null,
				"check the packing status of give vertices."));
		cmdStruct.add(new CmdStruct("eflow","-q{p} v",null,
				"construct flower for vertex v from schwarzians, put in "+
						"pack p and display --- note that indices will not "+
						"agree with original"));
		cmdStruct.add(new CmdStruct("reset",null,null,
				"update all vert/edge errors and colors"));
		cmdStruct.add(new CmdStruct("worst",null,null,
				"Report worst pos/neg radius and angle errors."));
//		cmdStruct.add(new CmdStruct(""))
	}
	
}



