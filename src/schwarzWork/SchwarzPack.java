package schwarzWork;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;

/**
 * This class is for experimenting with use of
 * intrinsic schwarzians in computing circle
 * packings, particularly on the sphere or 
 * on projective surfaces. (We work with 'uzians'
 * instead, with u=1-s.) After initializing the
 * uzians, they are updated locally and in the
 * parent packing.
 * 
 * I am first going to try a randomized approach.
 * I need routines
 *  + Set initial uzian guess (modes?)
 *  + pick random interior edge
 *  + get preceeding n-3 schwarzians
 *  + check labels: un-branched? full? branched
 *  + compute new uzian.
 *  + apply adjustment strategy (modes?)
 *  + remove/replace degree 3 (iteratively)
 *  + need status computation: perhaps layout as euclidean
 *    and use angle sum.
 * 
 * @author ken stephenson, February 2025
 * 
 */
public class SchwarzPack extends PackExtender {
	final static double sqrt3=Math.sqrt(3);

	double[][] uzianArray; // uzians[node][petal]
	ArrayList<Integer> branchVertices; // for now, at most simple branching
	int test_mode;

	// Constructors
	public SchwarzPack(PackData p) {
		super(p);
		packData=p;
		extensionType="SCHWARZPACK";
		extensionAbbrev="SP";
		toolTip="'SchwarzPack' allows for experiments in using "+
			"intrinsic schwarzians to compute packings of the "+
				"sphere or of projective surfaces.";
		registerXType();
		test_mode=1; // default
		try {
			this.cpCommand("set_sch");
			uzianArray=uziansFromPack(packData);
		} catch (Exception ex) {
			Oops("Failed to form 'uzianArray'");
			running=false;
		}
		if (running) {
			packData.packExtensions.add(this);
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
				vlink=new NodeLink(packData,items);
			else
				vlink=new NodeLink(packData,"i");
			Iterator<Integer> vlt=vlink.iterator();
			int count=0;
			while (vlt.hasNext() && count<5) { // at most 5
				Vertex vert=packData.packDCEL.vertices[vlt.next()];
				Complex err=errorAtVert(vert);
				if (err==null)
					Oops("error in errorAtVert for v"+vert.vertIndx);
				this.msg("status of vert "+vert.vertIndx+
						": error is "+err.abs());
				count++;
			}
			return count;
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
			HalfEdge target=packData.packDCEL.
					vertices[packData.getAlpha()].halfedge;
			try {
				HalfLink flink=new HalfLink(packData,
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
		
		return 1;
	}
	
	public int runTrials(int N) {
		int count=0;
		for (int j=1;j<=N;j++) {
			HalfEdge target=getRandEdge(packData);
			CompResults cresults=compEdgeUzians(target);
			target.setSchwarzian(cresults.newUzian(test_mode));
			count++;
		}
		return count;
	}
	
	/**
	 * Find the complex error when this flower is laid out
	 * using uzians.
	 * @param vert Vertex
	 * @return Complex x+iy, x=error in radii, y=error in angle
	 */
	public Complex errorAtVert(Vertex vert) {
		if (vert.isBdry())
			return null;
		int order=0;
		if (branchVertices.contains(vert.vertIndx))
			order=1;
		double[] uz=getUzians(vert);
		return SchFlowerData.euclFlower(uz,order);
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
	 * Get the closed list of uzians for interior 
	 * vertices from the packing. Boundary vert will
	 * have null list. 
	 * @param packData PackData
	 * @return double[v][j] or null for bdry verts
	 */
	public static double[][] uziansFromPack(PackData packData) {
		double[][] uarray=new double[packData.nodeCount+1][];
		for (int v=1;v<=packData.nodeCount;v++) {
			Vertex vert=packData.packDCEL.vertices[v];
			int n=vert.getNum();
			if (vert.isBdry()) {
				uarray[v]=null;
				continue;
			}
			uarray[v]=new double[n+1];
			
			HalfEdge he=vert.halfedge;
			uarray[v][0]=1.0-he.getSchwarzian();
			he=he.prev.twin;
			int tick=1;
			while (he!=vert.halfedge) {
				uarray[v][tick++]=1.0-he.getSchwarzian();
				he=he.prev.twin;
			}
			uarray[v][n]=uarray[v][0]; // close up
		}
		return uarray;
	}
	
	/**
	 * Store schwarzians of interior vertices of
	 * the packing from given array. 
	 * @param packData PackData
	 * @param uarray double[][]
	 * @return int count
	 */
	public static int schwarzians2packing(PackData packData,double[][] uarray) {
		int tick=1;
		for (int v=1;v<=packData.nodeCount;v++) {
			Vertex vert=packData.packDCEL.vertices[v];
			if (vert.isBdry()) {
				uarray[v]=null;
				continue;
			}
			HalfEdge he=vert.halfedge;
			he.setSchwarzian(1.0-uarray[v][0]);
			he=he.prev.twin;
			while (he!=vert.halfedge) {
				he.setSchwarzian(1.0-uarray[v][tick++]);
				he=he.prev.twin;
			}
		}
		return tick;
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
			HalfEdge he=edge.prev.next.prev.next;
			double[] uz=new double[nA];
			int tick=0;
			while (tick<nA) {
				uz[tick++]=1.0-he.getSchwarzian();
				he=he.prev.twin;
			}
			uarray=SchFlowerData.constraints(uz);
			cresults.uzA=(Double)((1.0+uarray.get(nA-3))/
					(sqrt3*uarray.get(nA-2)));
		}
		if (!cresults.endB.isBdry()) {
			HalfEdge he=edge.twin.prev.next.prev.next;
			double[] uz=new double[nB];
			int tick=0;
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
			uzians[tick++]=he.getSchwarzian();
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


