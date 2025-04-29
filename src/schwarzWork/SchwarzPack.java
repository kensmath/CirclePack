package schwarzWork;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import exceptions.CombException;
import geometry.CircleSimple;
import input.CommandStrParser;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
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

	Complex[] currentErrors; // latest errors for all vertices
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
			this.cpCommand("set_sch");
			currentErrors=setCurrentErrors();
		} catch (Exception ex) {
			Oops("Failed in computing errors");
			running=false;
		}
		if (running) {
			extenderPD.packExtensions.add(this);
		}
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		// ============ set test mode =================
		if (cmd.startsWith("test_m")) {
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
			NodeLink vlink=null;
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0)
				vlink=new NodeLink(extenderPD,items);
			else
				vlink=new NodeLink(extenderPD,"i");
			Iterator<Integer> vlt=vlink.iterator();
			int count=0;
			while (vlt.hasNext()) {
				Vertex vert=extenderPD.packDCEL.vertices[vlt.next()];
				this.msg("status of vert "+vert.vertIndx+
						": error is "+currentErrors[vert.vertIndx]);
				count++;
			}
			return count;
		}
	
		// show eucl flower in other window; form eflo -q{q} v
		else if (cmd.startsWith("eflo")) { 

			// choose packing and vertex: 
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
		else if (cmd.startsWith("cons")) { // constraints
			HalfEdge target=extenderPD.packDCEL.
					vertices[extenderPD.getAlpha()].halfedge;
			try {
				HalfLink flink=new HalfLink(extenderPD,
						flagSegs.get(0));
				if (flink!=null && flink.size()>0)
					target=flink.get(0);
			} catch (Exception ex) {
				Oops("usage: constraints {v w} to specify edge");
			}
			CompResults cresults=compEdgeUzians(target);
			msg("edge "+target+": uzA = "+cresults.uzA+
					" and uzB = "+cresults.uzB);
			return 1;
		}
		else if (cmd.startsWith("one")) { // one edge
			HalfEdge he=null;
			try {
				items=flagSegs.get(0);
				HalfLink nlink=new HalfLink(extenderPD,items);
				he=nlink.get(0);
			} catch(Exception ex) {
				return 0;
			}
			CompResults cresults=compEdgeUzians(he);
			msg("one: "+he+": original = "+he.getSchwarzian()+
					" computed = "+(1.0-cresults.newUzian(test_mode)));
			return 1;
		}
		
		// recompute entries for currentErrors. This may be due
		//   to computational drift or to the fact that new
		//   schwarzians have been set in 'packData'.
		else if (cmd.startsWith("reset")) { 
			currentErrors=setCurrentErrors();
			return 1;
		}

		else if (cmd.startsWith("worst")) {
			int[] errV=worstErrors();
			double rplus=0.0;
			double rminus=0.0;
			double aplus=0.0;
			double aminus=0.0;
			if (errV[0]!=0)
				rplus=currentErrors[errV[0]].x;
			if (errV[1]!=0)
				rminus=currentErrors[errV[1]].x;
			if (errV[2]!=0)
				aplus=currentErrors[errV[2]].y;
			if (errV[3]!=0)
				aminus=currentErrors[errV[3]].y;
			msg("Worst errors, radii/angles, positive/negative:");
			if (errV[0]>0)
				msg("Positive radius error = "+rplus+" at v"+errV[0]);
			if (errV[1]>0)
				msg("Negative radius error = "+rminus+" at v"+errV[1]);
			if (errV[2]>0)
				msg("Positive angle error = "+aplus+" at v"+errV[2]);
			if (errV[3]>0)
				msg("Negative angle error = "+aminus+" at v"+errV[3]);
			return 1;
		}
		return 0;
	}
	
	public int runTrials(int N) {
		int count=0;
		for (int j=1;j<=N;j++) {
			HalfEdge target=getRandEdge(extenderPD);
			CompResults cresults=compEdgeUzians(target);
			target.setSchwarzian(cresults.newUzian(test_mode));
			count++;
		}
		return count;
	}
	
	/**
	 * Initiate persistent array of "errors" at
	 * the vertices based on current schwarzians.
	 * This should be called on loading and if
	 * necessary when lots of new schwarzians are
	 * set. Otherwise, it should be updated for the
	 * two end vertices whenever an edge has its
	 * schwarzian adjusted. Entry is null for bdry
	 * vertices.
	 * @return ArrayList<Complex>
	 */
	public Complex[] setCurrentErrors() {
		NodeLink nlink=new NodeLink(extenderPD,"a");
		Complex[] cE=new Complex[extenderPD.nodeCount+1];
		Iterator<Integer> nls=nlink.iterator();
		while (nls.hasNext()) {
			int v=nls.next();
			Vertex vert=extenderPD.packDCEL.vertices[v];
			if (vert.isBdry())
				cE[v]=null;
			else {
				CircleSimple cs=new CircleSimple();
				Complex err=new Complex(0.0);
				PackData.schFlowerErr(vert, err, cs);
				cE[v]=err;
			}
		}
		return cE;
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
			Complex err=currentErrors[v];
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
			
	/**
	 * Given edge, compute for each end Vertex the 
	 * edge's uzian based on the previous n-3 flower
	 * uzians. Results are in 'CompResults' and a
	 * computed uzian will be null if that end is a
	 * boundary vertex. At least one end should be
	 * interior and if both are interior, degrees
	 * should both be > 3.
	 * 
	 * @param edge HalfEdge
	 * @return CompResults for holding results
	 */
	public static CompResults compEdgeUzians(HalfEdge edge) {
		CompResults cresults=new CompResults(edge);
		cresults.initialUzian=1.0-edge.getSchwarzian();
		int nA=cresults.degA;
		int nB=cresults.degB;
		ArrayList<Double> uarray;
		if (!cresults.endA.isBdry()) {
			// get uzians starting 3 edges cclw
			HalfEdge he=edge.prev.twin.prev.twin.prev.twin;
			double[] uz=new double[nA+1];
			int tick=1; // uzians indexed from 1
			while (tick<=nA) {
				uz[tick++]=1.0-he.getSchwarzian();
				he=he.prev.twin;
			}
			uarray=SchFlowerData.constraints(uz);
			cresults.uzA=(Double)((1.0+uarray.get(nA-3))/
					(sqrt3*uarray.get(nA-2)));
		}
		if (!cresults.endB.isBdry()) {
			// get uzians starting 3 edges cclw
			HalfEdge he=edge.twin.prev.twin.prev.twin.prev.twin;
			double[] uz=new double[nB+1];
			int tick=1; // uzians indexed from 1
			while (tick<nB) {
				uz[tick++]=1.0-he.getSchwarzian();
				he=he.prev.twin;
			}
			uarray=SchFlowerData.constraints(uz);
			cresults.uzB=(Double)((1.0+uarray.get(nB-3))/
					(sqrt3*uarray.get(nB-2)));
		}
		return cresults;
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
		cmdStruct.add(new CmdStruct("test_mode","[mode]",null,
				"modes will (hopefully) emerge."));
		cmdStruct.add(new CmdStruct("rand","[type]",null,
				"Initiate random edge schwarzians"));
		cmdStruct.add(new CmdStruct("cons","{v w}",null,
				"compute uzians of ends of {v w}."));
		cmdStruct.add(new CmdStruct("run","[N}",null,
				"Run trial with N iterations, default to 1000"));
		cmdStruct.add(new CmdStruct("status","{v ..}",null,
				"check the packing status of give vertices."));
		cmdStruct.add(new CmdStruct("eflow","-q{p} v",null,
				"construct flower for vertex v from schwarzians, put in "+
						"pack p and display --- note that indices will not "+
						"agree with original"));
		cmdStruct.add(new CmdStruct("reset",null,null,
				"recompute the 'currentErrors' for all interior vertices"));
		cmdStruct.add(new CmdStruct("worst",null,null,
				"Report worst pos/neg radius and angle errors."));
//		cmdStruct.add(new CmdStruct(""))
	}
	
}

class CompResults {
	public double initialUzian;
	boolean branched=false;
	public Vertex endA;
	public Vertex endB;
	int degA;
	int degB;
	public int indxA;
	public int indxB;
	public Double uzA;
	public Double uzB;
	
	public CompResults(HalfEdge he) {
		endA=he.origin;
		endB=he.twin.origin;
		degA=endA.getNum();
		degB=endB.getNum();
		indxA=(int)CombDCEL.indxEdgeOfVert(he,endA);
		indxB=(int)CombDCEL.indxEdgeOfVert(he,endB);
	}
	
	public double newUzian(int mode) {
		double diffA=initialUzian-uzA;
		double diffB=initialUzian-uzB;
		double threshold;
		switch(mode) {
		case 1:{ // default, 10% at most
			threshold=0.1; // how much change to allow
			if (diffA>=0 && diffB>=0) { // both lower, so decrease
				double smallest=(diffA<diffB) ? diffA : diffB;
				return initialUzian-threshold*smallest;
			}
			if (diffA<=0 && diffB<=0) { // both lower, so increase
				double largest=(diffA>diffB) ? diffA : diffB;
				return initialUzian+threshold*largest;
			}
			if (Math.abs(diffA)<.001 && Math.abs(diffB)<.001)
				return initialUzian;
			if ((diffA<=0 && diffB>=0) || (diffB<=0 && diffB<=0)) {
				double avgdiff=initialUzian-((diffA+diffB)/2.0);
				if (Math.abs(avgdiff)<.001)
					return initialUzian;
				if (avgdiff<0) // avg lower, so decrease
					return initialUzian-threshold*avgdiff;
				if (avgdiff>0) // avg lower, so increase
					return initialUzian+threshold*avgdiff;
			}
			break;
		}
		} // end of switch
		return initialUzian;
	}
	
}


