package schwarzWork;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import exceptions.ParserException;
import listManip.HalfLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
/**
 * This code if for experimenting with packing
 * algorithms for the sphere using (intrinsic) 
 * schwarzians. (I've copied 'SpherePack', which
 * has an auxiliary frame for radii, but which
 * may be used somehow with schwarzians.)
 * 
 * Will start small and work on small (nodecount < 26)
 * triangulations of the sphere or of s topological
 * disc. We insure that there are no interior vertices 
 * of degree 3 and no bdry vertices without interior
 * neighbor.
 * 
 * We build a 'HalfLink' of edges that are adjustable;
 * these are all interior edges for a top sphere, but
 * for a disc, the interior edges shared between
 * successive bdry vertices are not adjustable and
 * will be initialized as boundary conditions. 
 */

public class SchwarzAdjust extends PackExtender {

	// mode names
	final static int STANDARD=1;
	
	final static double UNIFORM3=1.0-1.0/Math.sqrt(3.0);
	
	public HalfLink adjEdges; // edges subject to adjustment
	public int mode; // mode of repacking
	
	// Constructor
	public SchwarzAdjust(PackData p) {
		super(p);
		packData=p;
		extensionType="SCHWARZADJUST";
		extensionAbbrev="SA";
		toolTip="'SchwarzAdjust' is a test bench "+
			"for using (intrinsic) schwarzians to "+
				"circle pack on the sphere.";
		registerXType();
		
		
		int rslt=1;
		try {
			if (!packData.status || packData.nodeCount<4)
				rslt=0;
			else if (packData.hes<=0) 
				rslt=cpCommand(packData,"geom_to_s");
		} catch(Exception ex) {
			errorMsg("SA: failed converting to sph geom, or "
					+ "other problem");
			running=false;
		}
		if (rslt==0)
			running=false;
		
		// start with some basic checking:
		if (running) {
			if (rslt!=0 && packData.nodeCount>25) {
				CirclePack.cpb.errMsg("SchwarzAdjust: "
						+"currently limited to nodecounts < 26)");
				rslt=0;
			}
			if (rslt!=0) {
				initEdgeList("a");
			}
			packData.packExtensions.add(this);
		}
	}

	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		if (flagSegs!=null && flagSegs.size()>0) {
			items=flagSegs.get(0);
		}
		
		int count=0;

		if (cmd.startsWith("init")) {
			if (items!=null) {
				String str=items.get(0);
				initEdgeList(str);
				count+=adjEdges.size();
			}
		}
		if (cmd.startsWith("restart")) {
			mode=1; // default
			if (items!=null) {
				int tmode=1;
				try {
					tmode=Integer.parseInt(items.get(0));
				} catch (Exception ex) {
					throw new ParserException("didn't get 'mode'. "+ex.getMessage());
				}
				mode=tmode;
			}
			for (int j=1;j<=packData.nodeCount;j++) {
				Vertex v=packData.packDCEL.vertices[j];
				if (mode==STANDARD) {
					if (!v.isBdry()) {
						HalfLink flower=v.getEdgeFlower();
						Iterator<HalfEdge> fits=flower.iterator();
						while (fits.hasNext()) {
							HalfEdge he=fits.next();
							he.setSchwarzian(0.0);
						}
					}
				}
				// TODO: other modes? May want bdry conditions
				//       on some edges.
				count++;
			}
			for (int j=1;j<=packData.nodeCount;j++) {
				Vertex v=packData.packDCEL.vertices[j];
				if (v.getNum()==3) {
					HalfLink flower=v.getEdgeFlower();
					Iterator<HalfEdge> fits=flower.iterator();
					while (fits.hasNext()) {
						HalfEdge he=fits.next();
						he.setSchwarzian(UNIFORM3);
					}
				}
			}
		}

		return count;
	}
	
	/**
	 * Check a vertex's packing condition vis-a-vis
	 * intrinsic schwarzians. 
	 * Main concern is difference between recorded schwarzian s_{n-2} 
	 * and its computed value from {s_1,....,s_{n-3}}.
	 * Want the worst +,  
	 * @param vert Vertex
	 * @return SchVertStatus, internal class
	 */
//	public static SchVertStatus vertSchStatus(PackData p,Vertex vert) {
//		double[] ans=new double[2];
//	}
	
	/**
	 * Determine list 'adjEdges' of edges to be visited
	 * for possible adjustment. Avoid bdry edges and
	 * edges having a degree 3 vertex. 
	 * @param spec String
	 * @return HalfLink
	 */
	public void initEdgeList(String spec) {
		HalfLink tmpnl=new HalfLink(packData,spec);
		adjEdges=null;
		Iterator<HalfEdge> hel=tmpnl.iterator();
		while (hel.hasNext()) {
			HalfEdge he=hel.next();
			if (he.isBdry())
				continue;
			if (he.origin.getNum()<=3 || he.twin.origin.getNum()<=3)
				continue;
			adjEdges.add(he);
		}
	}
	
	public void killMe() {
		super.killMe();
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("init","{spec}",null,
			"creates list of edges subject to adjustment; spec defaults to 'a'"));
		cmdStruct.add(new CmdStruct("restart","m",null,"restart by initiating the schwarzians"));
		cmdStruct.add(new CmdStruct("iter","{N}",null,
			"start a cycle of N adjustments"));
		cmdStruct.add(new CmdStruct("quality",null,null,"Display the status of interior vertices."));
	}
	
}
