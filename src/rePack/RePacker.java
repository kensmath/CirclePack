package rePack;

import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import packing.RData;
import util.UtilPacket;
import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import exceptions.PackingException;

/**
 * An abstract class to manage repacking in hyperbolic, euclidean, and
 * (eventually) spherical situations. Depending on performance issues, 
 * goal is to clone initial data for computations: 
 *  (1) allows the machine to run in a separate thread; but CAUTION: 
 *      KData remains with the parent packing, so it must not change. 
 *  (2) allows original centers/radii to be retained in case of failure. 
 *  (3) allows interruption and restarting of computations. 
 * 
 * In general, 'RePack' objects are to be released as garbage after 
 * computations are finished. However, perhaps we can replace original
 * RData with the local rdata: the only things missing are centers (which will
 * be outdated anyway) and curv (which may be out of date, too).
 * @author kens
 *
 */
public abstract class RePacker {
	
	static final double mp2=2*Math.PI;
	
	// status
	public static final int FAILURE=-1;       // when something goes wrong
	public static final int DO_NOTHING=0;     // when no repacking is needed (not yet used, 10/2011)
	public static final int LOADED=1;         // ready to initiate riffling
	public static final int RIFFLE=2;         // ready to resume riffling
	public static final int IN_THREAD=3;      // packing underway in some thread (e.g., native code)

	public static int MAX_ALLOWABLE_BAD_CUTS=300;
	public static double TOLER=.00000000001;
	public static int PASSLIMIT=1000;    // default upper bound, may be changed
	
	public PackData p;      // parent packing
	public int chkCount;    // minimal check on whether packing has been switched
	
	// status information
	public int status;      // new? restarted? threaded (eventually)
	public int passLimit;
	public int totalPasses;		// cumulative since creation of this repacker
	public int localPasses;		// during this run.
	
    boolean useSparseC;  // if true, use 'SolverFunction' lib when available and applicable

	// main data
	public RData []rdata;
	public int []index;			// indices of adjustable radii
	public TmpData []kdata;     // locally copy of needed part of p.kData.

	// holding area for list
	NodeLink holdv=null;
	EdgeLink holde=null;
	FaceLink holdf=null;
	GraphLink holdg=null;
	VertexMap holdmap=null;

	// store info during repacking
	public int aimnum;
	public int key;
	public int maxBadCuts;
	public int minBadCuts;
	public int sumBadCuts;
	public int cntBadCuts;
	public int sct;
	public int fct;
	public double m;
	public double c0;
	public double ttoler;
	public double []R1;
	public double []R2;
	public UtilPacket utilPacket;
	
	// Constructor: 
	// NOTE: calling routine should kill if 'pd.status' is not positive
	public RePacker(PackData pd,int pass_limit,boolean useC) {
		p=pd;
		if (pass_limit<0) passLimit=PASSLIMIT;
		else passLimit=pass_limit;
		status=load();  
		if (status==LOADED) {
			totalPasses=0;
			localPasses=0;
			R1=new double[p.nodeCount+1];
			R2=new double[p.nodeCount+1];
		}
		utilPacket=new UtilPacket();
		setSparseC(useC);
	}
	
	// use default number of iterations
	public RePacker(PackData pd,boolean useC) { 
		this(pd,CPBase.RIFFLE_COUNT,useC);
	}
	
	public RePacker(PackData pd) { // use default number of iterations
		this(pd,CPBase.RIFFLE_COUNT,true);
	}

	// abstract methods -- these must be implemented in derived classes
	public abstract int load(); // load initial data into local storage
	
	/**
     * Start a repacking if status is LOADED; uses super-steps, uniform neighbor model.
     * This gets things set up, status to RIFFLE, followed by 'continurRiffle'.
     * CRC - modified 8/5/02 from h_riffle:
     * anglesum calculations are in-line. 
     * NOTE: global 'totalPasses' is reset from within this routine
     * @return count of local repack cycles
     * @throws PackingException
     */
	public abstract int startRiffle() throws PackingException; // initiate packing comp
	
	/**
	 * I think this means to start over with default initial radii.
	 * @param passNum
	 * @return 1
	 * @throws PackingException
	 */
	public abstract int reStartRiffle(int passNum) throws PackingException;  // continue, but with restart
	
    /**
     * Continue repacking riffle if status is RIFFLE; uses super-step, 
     * uniform neighbor model. Reset the local passLimit (but we don't change 
     * 'totalPasses'.)
     * NOTE: global 'totalPasses' is reset from within this routine
     * @param limit on local cycles
     * @return count of local repack cycles
     * @throws PackingException
     */
	public abstract int continueRiffle(int passNum) throws PackingException;
	
	/** 
	 * measure accuracy, compare to crit
	 * @param crit, double
	 * @return double
	 */
	public abstract double l2quality(double crit);
	
	/**
	 * pass newly computed radii back to parent
	 */
	public abstract void reapResults();
	
	/**
	 * Determine whether SolverFunction lib is available/appropriate/requested.
	 * NOTE: In repacking, GOpacker has code for Orick's methods, but also
	 * the stand-alone super-step code (parallel to the Java code)
	 * @param useC, true, then use the library if available
	 */
	public abstract void setSparseC(boolean useC);

	/**
	 * Check if GOpacker is appropriate. 'useC' is set
	 * in 'setSparseC' and means SolverFunction routines are available.
	 * @param useC, boolean; true = allow use of GOpacker if appropriate
	 * @return boolean
	 */
	public boolean useSparseC(boolean useC) {
		if (useC) { // requested to use 'SolverFunction' C lib routines if possible
			if (p.overlapStatus) {
//				CirclePack.cpb.msg("'SolverFunction' libs not used with inv. distances.");
				return false;
			}
			if (p.euler!=1 || p.genus!=0) {
//				CirclePack.cpb.msg("'SolverFunction' libs not applicable to multi-connected cases; "+
//						"use Java repack routines");
				return false;
			}
			if (!JNIinit.SparseStatus()) {
//				CirclePack.cpb.msg("'SolverFunction' lib is not available; use Java repack routines");
				return false;
			}

			// check for non-default aims
			boolean hit=false;
			for (int i=1;(!hit && i<=p.nodeCount);i++) {
				if (p.kData[i].bdryFlag>0) {
					if (p.rData[i].aim>0.0)	
						hit=true;
				}
				else if (Math.abs(p.rData[i].aim-mp2)>.00000001) 
					hit=true;
			}
			// encountered non-default aims, use Java routines
			if (hit) 
				return false;
			
			// seems we can go ahead based on general criteria
			return true;
		}
		else // said 'no' to using GOpacker
			return false;
	}
	
	/**
	 * Generic 'repack' call is for the (now) classical "riffle" 
	 * methods, typically with supersteps, etc. Originally in C,
	 * now implemented in Java. 
	 * 
	 * TODO: may want to reimplement in C library, could be part of standalone
	 * code (and might be faster??).
	 * 
	 * On success, reap resulting radii; normally centers are computed 
	 * in a separate call at user's discretion. 
	 * 
	 * Alternate methods: Orick's method and using GOpack for max 
	 * packings (which by nature also computes centers); also 
	 * 'oldReliable' is used, e.g., with inversive distances.
	 * 
	 * @param pass_limit
	 * @return
	 * @throws PackingException
	 */
	public int genericRePack(int pass_limit) throws PackingException {
		
		passLimit=pass_limit;

		/* OBE: as of 2015, have retired the old HeavyC stuff
		 * 
		// use sparse matrix riffle methods of SolverFunction if requested and OK (see 'setSparseC')
		// TODO: having some problem with this for hyperbolic packings: crash with
		//       various error message, e.g. 'WData'; don't know source. 
		if (useSparseC) {
			PackLite pLite=new PackLite(p);
			if (HeavyC.putLite(pLite.counts, pLite.varIndices, pLite.origIndices, 
					pLite.radii,pLite.aimIndices,pLite.aims,pLite.invDistEdges,
					pLite.invDistances,pLite.centerRe,pLite.centerIm)==0) {
				throw new JNIException("C call to 'putLite' failed in hyp 'maxPackC'");
			}

			// This is the original C version of the riffle methods.
			if (HeavyC.genericRePack(passLimit,p.nodeCount)<=0.0) 
				throw new PackingException("Hyperbolic 'maxPackC' call failed");
			
			// capture the radii: this is euclidean data
			double []radii=HeavyC.sendRadii(p.nodeCount,1);
			for (int v=1;v<=p.nodeCount;v++) {
				p.rData[v].rad=radii[v];
			}
			p.fillcurves();
			return 1;		
		} */

		
		// else use Java computations; should have been loaded in constructor 
		if (status!=LOADED && status!=RIFFLE) {
			CirclePack.cpb.myErrorMsg("genericRePack: not in prepared status");
			return 0;
		}
		if (status==LOADED) localPasses=startRiffle();
		else localPasses=0;
		totalPasses += localPasses;
		if (continueRiffle(pass_limit)!=0) {  // successful?
			reapResults();
			return totalPasses;
		}
		return 0;
	}

	// Convenience: this is first thing called in 'load()' calls.
	public int genericLoad() {
		int num;
		try {
			rdata=new RData[p.nodeCount+1];  // TODO: can we save time with block transfer??
			kdata=new TmpData[p.nodeCount+1];
			for (int i=1;i<=p.nodeCount;i++) {
				rdata[i]=new RData();
				kdata[i]=new TmpData();
				rdata[i].aim=p.rData[i].aim;
				rdata[i].rad=p.rData[i].rad;
				kdata[i].num=num=p.kData[i].num;
				kdata[i].flower=new int[kdata[i].num+1];
				for (int j=0;j<=num;j++) kdata[i].flower[j]=p.kData[i].flower[j];
				if (p.overlapStatus) {
					kdata[i].overlaps=new double[kdata[i].num+1];
					for (int j=0;j<=num;j++) kdata[i].overlaps[j]=p.kData[i].overlaps[j];
				}
			}
			// load 'petallaps' (vectors of overlaps between successive petals of flowers)
			if (p.overlapStatus) {
				for (int i=1;i<=p.nodeCount;i++) {
					kdata[i].petallaps=new double[kdata[i].num];
					for (int j=0;j<kdata[i].num;j++) {
						int v=kdata[i].flower[j];
						int w=kdata[i].flower[j+1];
						int k=p.nghb(w,v);
						kdata[i].petallaps[j]=p.kData[w].overlaps[k];
					}
				}
			}
		} catch (Exception ex) {
			CirclePack.cpb.myErrorMsg("The 'RePacker' has failed to load");
			return FAILURE;
		}
		return 1;
	}
	
	/**
	 * Convenience: hold various lists during repacking, see 'restoreLists' 
	 * @param pd, PackData
	 */
	public void holdLists(PackData pd) {
		if (pd.vlist!=null && pd.vlist.size()>0)
			  holdv=pd.vlist.makeCopy();
		  if (pd.elist!=null && pd.elist.size()>0)
			  holde=pd.elist.makeCopy();
		  if (pd.flist!=null && pd.flist.size()>0)
			  holdf=pd.flist.makeCopy();
		  if (pd.glist!=null && pd.glist.size()>0)
			  holdg=pd.glist.makeCopy();
		  if (pd.vertexMap!=null && pd.vertexMap.size()>0)
			  holdmap=pd.vertexMap.makeCopy();
		  pd.vlist=null;
		  pd.elist=null;
		  pd.flist=null;
		  pd.glist=null;
		  pd.vertexMap=null;
	}	
	
	/**
	 * Convenience; see also 'holdLists'
	 * @param pd, PackData
	 */
	public void restoreLists(PackData pd) {
		  pd.vlist=holdv;
		  pd.elist=holde;
		  pd.flist=holdf;
		  pd.glist=holdg;
		  pd.vertexMap=holdmap;
	}
	   
	/**
	 * Not yet implemented: to show activity during repacking
	 * 
	 */
	public static void repack_activity_msg() {
		// TODO: how to update GUI on packing activity.
	}
	
	public int getPassLimit() {
		return passLimit;
	}
	
	public int getTotalPasses() {
		return totalPasses;
	}

	public void setPassLimit(int pl) {
		if (pl>0) passLimit=pl;
	}
}

/**
 * Internal class: temporary storage of version of KData
 * @author kstephe2
 */
class TmpData {
	int num;
	int []flower;
	double []overlaps;
	double []petallaps;   // list of overlaps between petals
}
