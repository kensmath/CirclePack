package rePack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import JNI.SolverData;
import JNI.SolverFunction;
import allMains.CirclePack;
import complex.Complex;
import complex.MathComplex;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.MiscException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.NSpole;
import geometry.SphericalMath;
import listManip.NodeLink;
import packing.PackData;
import packing.PackLite;

/**
 * This is intended to provide an implementation through java of 
 * Gerald Orick's method for computing circle packings via
 * an iterative routine using Tutte embeddings. These are being 
 * moved from matlab and C++ to mainly java, with calls to C++ sparse
 * matrix routines. (I can't seem to find a good java routine;
 * e.g., it appears that la4j is more about compact storage; it doesn't 
 * use the sparseness in solving systems.)
 * 
 * When this class is instantiated, 'load' creates various 
 * persistent data objects. A key design feature is creation on
 * startup of PackLite 'myPLite'. This allows packings of portions
 * of larger complexes and it lines up the indexing for sparse
 * matrices. Indexing is now that of 'myPLite'. 
 * 
 * During computation, data is kept in 'localradii' and 'localcenters'.
 * Latest reliable values are put in myPLite 'radii' and 'centers'.
 * When done, transfer data from 'myPLite' to 'p', parent PackData.

 * System has block form  | A  B | * | Z0 | = | 0  |
 *                        | 0  I |   | Zb |   | Zb |
 *                        
 * A is transition matrix, interior to interior,
 * B is transition matrix, interior to bdry,
 * Z0 is vector of interior radii
 * Zb is vector of bdry radii
 * (do this system once for real, once for imaginary)
 * 
 * We create the right-hand-side -B*Zb, then solve A*Z0=-B*Zb for Z0 
 *
 * Calls: 
 * * 'startRiffle' initiates 'radii' to 1 and fills edge weights, 
 *   but doesn't riffle (i.e., does not do any adjustments).
 * * 'restartRiffle' sets radii to current values from myPLite.
 * * 'continueRiffle' continues with 'localradii' (even if the
 *   packing data has been updated).
 * * 'quality' returns L2 error in abstract (i.e., computed from radii,
 *   versus embedded (which is from Tutte-type layout).
 * * 'reapRadii' puts 'localradii' and 'localcenters' into the myPLite
 * 
 * @author kstephe2, January 2014
 *
 */
public class GOpacker extends RePacker {
	
	public int mode;  
	public static final int NOT_YET_SET=0; 
	public static final int MAX_PACK=1; // 1: default, max pack for unit disc. (for sphere, use POLY_PACK)
	public static final int POLY_PACK=2; // 2: polygon, side count = size of 'corners' (once it's set up)
	public static final int ORTHO_BDRY=3; // 3: bdry circles orthogonal to unit circle.
	public static final int FIXED_CORNERS=4; // fix corner locations, let rest of bdry float
	public static final int FIXED_BDRY=5;  // boundary radii and centers are not changed
	
	public double currentCrit;  // keep track of last value of 'crit' requested 
	
	// persistent objects
	PackLite myPLite;	    // create on startup, holds radii/centers
	int origNodeCount;		// original parent nodeCount for weak check of consistency 
	
	// these are null unless the goal is a polygonal packing
	int []parentCorners; // corner vertices in original indices
	int []corners;  // null until 'parentCorners' are translated to local indices.
	ArrayList<NodeLink> sides;  // list of bdry verts along sides, include both ends
	double []cornerAngles; // for POLY_PACK mode only, default to pi-2pi/n = pi(1-2/n)
	Complex []cornerLocations; // for FIXED_CORNER mode only
	
	// sparse matrix structural stuff for linear system
	SolverData sData;
	
	VWJ []vwjA;
	VWJ []vwjRHS;
	// for filling righthand side of linear system
	int rhsIJcount;
	int []rhsV;
	int []rhsWindx;

	
	double [][]sectorTans; 
	/* for interior v, sectorTans[v] are based on radii;
	 * is array of tan(angle/2) for faces about v; entry j is 
	 * for face {v,v_j,v_{j+1}}. */
	double []conductances;
	/* total node conductances for interior v; computed using
	 * sectorTans and radii.  */
	
	public Vector<Double> myPLiteError; // successive error after set cycles are done
	public double localError; // error in localradii/centers during riffles
	
	boolean debug=false;

	int passes;
	double errtol;
	
	// constructor(s)
	public GOpacker(PackData pd) { // default to 10 passes
		this(pd,10);
	}
	
	public GOpacker(PackData pd,int pass_limit) { // default: max pack 
		super(pd,pass_limit,true);
		passes=pass_limit;
		errtol=.001;
		realLoad(null);
		setMode(0);  // NOT_YET_SET;
	}
	
	public GOpacker(PackData pd,NodeLink vint) { // specify interior
		super(pd,30,true);
		setMode(0); // NOT_YET_SET;
		realLoad(vint);
	}

	public void setSparseC(boolean useC) {}; // not applicable for GOpacker
	
	/**
	 * fake, do-nothing 'RePacker' abstract method. Have to make the 'realLoad' call
	 * depending on circumstances. 
	 */
	public int load() {
		return 1;
	}
	
	/**
	 * Special 'load' version for GOPacker to tailor the associated 
	 * persistent 'PackLite', with info on what to repack: default 
	 * repacks interior based on boundary, but we may instead send
	 * NodeLink of vertices, e.g., use poison vertices to prescribe 
	 * a subcomplex to pack. 
	 * 
	 * Indexing is taken from 'myPLite', where the
	 * interior are listed first, boundary in ccw order last. 'myPLite'
	 * has 'v2parent' and 'parent2v' methods to translate indices.
	 * 
	 * Next we create a 'SolverData' and initiate its persistent members. 
	 * This is used to communicate with the C++ code for solving systems.
	 * 
	 * @param v_int NodeLink, vertices to treat as interior.
	 * @return int = LOADED or FAILURE
	 */
	public int realLoad(NodeLink v_int) {
		
		status=RePacker.FAILURE;
		setCorners(null,null);
	
		// create persistent 'PackLite', 'SolverData', and 'rhs' stuff
		PackLite myPLite=new PackLite(p);
		origNodeCount=p.nodeCount;
		
		// what to do about aims? Let's reset to default, then change as
		//   appropriate to mode.
		myPLite.aims=new double[myPLite.vertCount+1];
		for (int v=1;v<=myPLite.intVertCount;v++) 
			myPLite.aims[v]=2.0*Math.PI;
		for (int v=myPLite.intVertCount+1;v<=myPLite.vertCount;v++)
			myPLite.aims[v]=-.1;

		// initiate SolverData
		sData=initSolverData(myPLite);
		
		// ready to go
		status=RePacker.LOADED;
		return myPLite.vertCount;
	}
	
	/**
	 * Set the mode
	 * @param mode
	 */
	public void setMode(int md) {
		switch(md) {
		case 0: // not yet set
		{
			mode=NOT_YET_SET;
			break;
		}
		case 2: // polygonal region
		{
			mode=POLY_PACK;
			break;
		}
		case 3: // bdry centers on unit circle
		{
			mode=ORTHO_BDRY;
			break;
		}
		case 4: // fixed corner
		{
			mode=FIXED_CORNERS;
			break;
		}
		case 5: // fixed bdry
		{
			mode=FIXED_BDRY;
			break;
		}
		default:
		{
			mode=MAX_PACK;
		}
		} // end of switch
	}
	
	/**
     * Initiate a 'SolverData' class with persistent data that
     * is to be sent to the native code. 
     * 
     * Also set persistent 'righthand side' data elements 
     * maintained in this class.
     * 
     * NOTE: matrix operations are indexed from zero, so one
     * must always translate between matrix index and vert index
     * by adding/subtracting 1.
     *   
     * @return SolverData
     */
    public SolverData initSolverData(PackLite pL) {

    	int ijCount=0;
    	for (int v=1;v<=pL.intVertCount;v++)
    	    ijCount +=pL.vNum[v]+1; // larger than needed, but will be adjusted later

    	// create Ap and Ai data for compressed column format
    	// Ap has 1+number of column (=intVertCount+1) entries, with Ap[0]=0.
    	SolverData sdata=new SolverData();
    	
    	VWJ []tmp_vwj=new VWJ[ijCount];
    	VWJ []tmp_rhs=new VWJ[ijCount];
    	sdata.nz_entries=0;
    	rhsIJcount=0; // separate pointer to non-zero entries

    	// first, find and list (possibly) non-zero entry spots 
    	//    in the square matrix, interior-to-interior
    	for (int v=1;v<=pL.intVertCount;v++) {
    		tmp_vwj[sdata.nz_entries++]=new VWJ(v,v,-1);
    		int []flower=pL.flowers[v];
    		int num=pL.vNum[v];
    		if (flower[0]!=flower[num]) // this shouldn't happen
    			num++;
    		for (int j=0;j<num;j++) {
    			int w=flower[j];
    			if (w<=pL.intVertCount) // w interior
    				tmp_vwj[sdata.nz_entries++]=new VWJ(v,w,j);
    			else 
    				tmp_rhs[rhsIJcount++]=new VWJ(v,w,j);
    		}
    	}

    	// non-zero spots are in vwjA; sort these and store in 'Ai',
    	//   put counts in 'Ap'
    	vwjA=new VWJ[sdata.nz_entries];
    	for (int i=0;i<sdata.nz_entries;i++)
    		vwjA[i]=tmp_vwj[i];
    	Arrays.sort(vwjA);
    	sdata.Ai=new int[sdata.nz_entries];
    	sdata.Ap=new int[pL.intVertCount+1];
    	for (int i=0;i<sdata.nz_entries;i++) {
    		sdata.Ap[vwjA[i].w]++;
    		sdata.Ai[i]=vwjA[i].v-1; 
    	}
    	for (int w=2;w<=pL.intVertCount;w++) // Ap entries are cummulative
    		sdata.Ap[w] += sdata.Ap[w-1];

    	vwjRHS=new VWJ[rhsIJcount];
    	for (int j=0;j<rhsIJcount;j++)
    		vwjRHS[j]=tmp_rhs[j];
    	
    	sdata.intNum=pL.intVertCount;
    	sdata.bdryNum=pL.bdryCount;

    	return sdata;
    }
    
	/**
	 * Use 'startRiffle' to initialize all radii to 1.0.
	 * Note: this does no riffles; one can call 'continueRiffle'
	 */
	public int startRiffle() {
		myPLite.radii=new double[myPLite.vertCount+1];
		for (int k=1;k<=myPLite.vertCount;k++) {
			myPLite.radii[k]=1.0;
		}
		totalPasses=localPasses=0;
		status=RePacker.RIFFLE; // ready to continue riffling
		myPLiteError=new Vector<Double>(1);
		return 1;
	}

	/**
	 * This is only for eucl packings: 'restart' sets 'myPLite.radii' 
	 * based on parent radii. 
	 * If you want to pick up computation where it stopped, then
	 * call 'continueRiffle' instead.
	 * @param passNum int, how many passes? if <0, use 'passLimit'
	 * @return int, count of 'continue' passes.
	 */
	public int reStartRiffle(int passNum) {
		if (p.hes!=0) {
			CirclePack.cpb.errMsg("'reStartRiffle' applies only to eucl packings");
			return 0;
		}
		for (int k=1;k<=myPLite.vertCount;k++) 
			myPLite.radii[k]=p.getRadius(myPLite.v2parent[k]);
		if (passNum<0)
			passNum=passLimit;
		else
			passLimit=passNum;

		localPasses=0; // leave 'totalPasses' unchanged
		status=RePacker.RIFFLE; // ready to continue riffling
		myPLiteError=new Vector<Double>(1);
		return continueRiffle(passNum);
	}

	/**
	 * Continue riffling using 'myPLite.radii'.
	 * @param passNum int, may be 0, if < 0, use 'passLimit'
	 * @return int, number of passes completed
	 */
	public int continueRiffle(int passNum) {
		
		if (passNum<0)
			passNum=passLimit;
		else
			passLimit=passNum;

		// first time through?
		if (mode==NOT_YET_SET) {
			  setMode(GOpacker.MAX_PACK); // default
			  
			  // but is this a sphere with punctured face?
			  if (setSphBdry()>0) // these sets bdry rad/centers (if there are 3 bdry verts)
				  setMode(GOpacker.FIXED_BDRY);
		}
		
		// ************************ main loop ****************************
			
		int pass=0;
		if (passNum==0) { // want layout only
			layoutBdry();
			layoutCenters();
		}
		
		while (pass<passNum) {
			
			// set centers for bdry circles based on current radii
			layoutBdry();
			
			// layout interior via Tutte embedding

			layoutCenters();
			
			// reset radii to effective radii based on new centers
			setEffective();
			
			pass++;
			
			// TODO: judge results. good enough, then exit
			localError= l2quality(.001); 

			if (localError < .0001) {
				if (myPLiteError==null)
					myPLiteError=new Vector<Double>(1);
				myPLiteError.addElement(Double.valueOf(localError));
				status=RePacker.RIFFLE; // ready to continue riffling
				localPasses += pass;
				totalPasses += pass;
				reapResults();
				return pass;// kick out
			}
			
		} // end of outer while
		// ********************************************************
		
		// store results in parent packing
		reapResults();
		
		status=RePacker.RIFFLE; 
		return pass;
	}
	
	/**
	 * depending on mode
	 * @return
	 */
	public int layoutBdry() {
		switch(mode) {
		case FIXED_BDRY: // nothing to do
		{
			break;
		}
		case FIXED_CORNERS:
		{
			setFixedShape();
			break;
		}
		case POLY_PACK: // polygon packing
		{
			// TODO: not yet implemented
			setPolyCenters();
			break;
		}
		case ORTHO_BDRY: // bdry circles ortho to unit circle
		{
			setOrthoCenters();
			break;
		}
		default: // MAX_PACKING 
		{
			setHoroCenters(); // for max packing, place around inside of unit circle
		}
		} // end of switch
		return 1;
	}

	/**
	 * Once radii and bdry centers are set, this carries out
	 * the Tutte-type layout: it updates the matrix non-zero entries and
	 * the right-hand side entries, asks the native code to solve the system,
	 * and stores the new interior centers in myPLite.
	 * @return int, intVertCount
	 */
	public int layoutCenters() {
		
		// update linear system data based on localradii
		updateMatrices();
		
		// Compute z: system is A*z=b 
		int stat=getTutteCenters(sData);
		
		// store the new interior centers
		if (stat>0)
			storeLocalZ();
		
		return myPLite.intVertCount; // anything meaningful to return?
	}
	
	/**
	 * This calls native code to solve the linear system associated
	 * with the given SolverData.
	 * @param sD
	 * @return int status (perhaps sD.status)
	 */
	public int getTutteCenters(SolverData sD) {

		try {
			sData = new SolverFunction().apply(sData);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("SolverFunction UMFpackException occurred: "+ex.getMessage());
			return 0;
		}
		

//  here's the way to do this in separate thread:
		
//		try {
//			CompletableFuture<SolverData> future=
//				CompletableFuture.completedFuture(sData)
//				.thenApply(new SolverFunction())
//				.thenApply((solverData1)->{System.out.printf("Done.\n");return solverData1;});
		
//			sData=future.get();
			
//		} catch (InterruptedException ex) {
//		CirclePack.cpb.errMsg("SolverFunction interruption occurred: "+ex.getMessage());
//			return 0;
//		} catch (ExecutionException ex) {
//			CirclePack.cpb.errMsg("SolverFunction execution error occurred: "+ex.getMessage());
//			return 0;
//		}
		
		return 1;
	}
	
	/**
	 * Set the matrix info based on current radii and centers.
	 * We have the non-zero entries of the n x n coefficient matrix
	 * and the system righthand side data. First we update the
	 * 'sectorTans' and total 'conductances'. Fill entries of 'sData'.
	 */
	public int updateMatrices() {
		
		// update 
    	sectorTans=new double[myPLite.intVertCount+1][];
    	for (int v=1;v<=myPLite.intVertCount;v++) {
//    		double ckangsum=0.0; // for debugging
    	    int []flower=myPLite.flowers[v];
    	    double []data=new double[myPLite.vNum[v]];
    	    for (int j=0;j<myPLite.vNum[v];j++) {
    	        int w=flower[j];
    	        int u=flower[j+1];
    	        double cang=EuclMath.e_cos_overlap(myPLite.radii[v],myPLite.radii[w],myPLite.radii[u]);
//    	        ckangsum +=Math.acos(cang); // debugging: check angle sum
    	        data[j]=Math.sqrt((1.0-cang)/(1.0+cang)); // half angle formula
    	    }
    	    sectorTans[v]=data;
    	}

    	// store total conductances for variable vertices
    	conductances=new double[myPLite.intVertCount+1];
    	for (int v=1;v<=myPLite.intVertCount;v++) {
    	    double []st=sectorTans[v];
    	    int num=myPLite.vNum[v];
    	    int []flower=myPLite.flowers[v];
    	    if (flower[0]==flower[num]) {  // interior vertex
    	    	int w=flower[0];
    	    	conductances[v]=(st[num-1]+st[0])/(myPLite.radii[v]+myPLite.radii[w]);
    	    	for (int j=1;j<num;j++) {
    	    		double t1=st[j-1];
    	    		double t2=st[j];
    	    		w=flower[j];
    	    		conductances[v] +=(t1+t2)/(myPLite.radii[v]+myPLite.radii[w]);
    	    	}
    	    }
    	    else { // bdry vertex
    	    	int w=flower[0];
    	    	conductances[v]=st[0]/(myPLite.radii[v]+myPLite.radii[w]);
    	    	for (int j=1;j<num;j++) {
    	    		double t1=st[j-1];
    	    		double t2=st[j];
    	    		w=flower[j];
    	    		conductances[v] +=(t1+t2)/(myPLite.radii[v]+myPLite.radii[w]);
    	    	}
    	    	w=flower[num];
    	    	conductances[v] +=st[num-1]/(myPLite.radii[v]+myPLite.radii[w]);
    	    }
    	}

    	// set 'Aentries'
		int n=sData.nz_entries;
		sData.Aentries=new double[n];
		for (int k=0;k<n;k++) {
			VWJ vwj=vwjA[k];
		    int v=vwj.v;
		    int w=vwj.w;
		    int j=vwj.j;
		    int num=myPLite.vNum[v];
		    double []st=sectorTans[v];
		    int []flower=myPLite.flowers[v];
		    if (j<0) { // edge to self if j=-1
		        sData.Aentries[k]=-1.0;
		    }
		    else if (flower[0]==flower[num]) { // interior vertex
		    	double t1;
		    	if (j==0)
		    		t1=st[num-1];
		    	else
		    		t1=st[j-1];
		    	double t2=st[j];
		    	
		    	// this is transition probability from v to w. Conductance on an edge is ratio
		    	//   of dual circle center distance divided by circle center distance. rad[v]
		    	//   doesn't appear in t1, t2 because it cancels with rad[v] that would appear
		    	//   in conductance.
		    	sData.Aentries[k]=((t1+t2)/(myPLite.radii[v]+myPLite.radii[w]))/conductances[v];
		    }
		    else { // bdry vertex
		    	double t1;
		    	if (j==0 || j==num)
		    		t1=0.0;
		    	else
		    		t1=st[j-1];
		    	double t2=st[j];
		    	sData.Aentries[k]=((t1+t2)/(myPLite.radii[v]+myPLite.radii[w]))/conductances[v];
		    }
		}
		
		// set rhs stuff, which is -B*zb, with B the m x n matrix
		//   of transition, interior-to-bdry, m=intVertCount, n=bdryCount, and
		//   zb is the vertor of bdry centers.
		sData.rhsX=new double[myPLite.intVertCount];
		sData.rhsY=new double[myPLite.intVertCount];
		for (int k=0;k<rhsIJcount;k++) {
			VWJ vwj=vwjRHS[k];
		    int v=vwj.v;
		    int w=vwj.w;
		    int j=vwj.j;
		    int num=myPLite.vNum[v];
		    double []st=sectorTans[v];
		    int []flower=myPLite.flowers[v];
	    	double tranprob=0.0;
	    	boolean bdry=false;
		    if (flower[0]!=flower[num]) { // v is bdry
		    	bdry=true;
		    	num++;
		    }
		    double t=0.0;
		    if (bdry) {
		    	if (j==0)
		    		t=st[0];
		    	if (j==num)
		    		t=st[j-1];
		    	else
		    		t=st[j-1]+st[j];
		    }
		    else {
		    	t=st[j];
		    	if (j==0)
		    		t +=st[num-1];
		    	else
		    		t+=st[j-1];
		    }
		    tranprob=1.0*(t/(myPLite.radii[v]+myPLite.radii[w]))/conductances[v];
		    
		    // have to introduce negative sign
		    sData.rhsX[v-1] -= tranprob*myPLite.centers[w].x;
		    sData.rhsY[v-1] -= tranprob*myPLite.centers[w].y;
		}

		return 1;
	}
	
    /**
     * The center real/imag parts were computed and stored in 'SolverData.Zx/Zy'
     * by the native solver code. Transfer these to myPLite.centers.
     * @return int count
     */
    public int storeLocalZ() {
    	try {
    		for (int k=0;k<myPLite.intVertCount;k++) 
    			myPLite.centers[k+1]=new Complex(sData.Zx[k],sData.Zy[k]);
    		return myPLite.intVertCount;
    	} catch(Exception ex) {
    		throw new MiscException("error getting new centers: "+ex.getMessage());
    	}
    }
    
    /**
     * Use 'centers' to reset 'radii' to "effective" radii. 
     * TODO: there are other options and probably improvements here.
     * 
     * I am moderating the adjustments of bdry radii, since they seem to
     * oscillate a lot. 2/2014
     * 
     * @return
     */
	public int setEffective() {
		
		// handle interior first: their anglesum and aim are 2*pi
		for (int v = 1; v <= myPLite.intVertCount; v++) {
			double area = 0;
			int num = myPLite.vNum[v];
			Complex z = new Complex(myPLite.centers[v]);
			int[] flower = myPLite.flowers[v];
			for (int j = 0; j < num; j++) {
				int jr = flower[j];
				int jl = flower[j + 1];
				Complex zr = new Complex(myPLite.centers[jr]);
				Complex zl = new Complex(myPLite.centers[jl]);
				// radius of sector at v
				double r = 0.5 * (zr.minus(z).abs() + zl.minus(z).abs() - zr.minus(zl).abs());
				double cC = EuclMath.e_cos_corners(myPLite.centers[v],
						myPLite.centers[jr], myPLite.centers[jl]);
				if (cC < -1.0)
					cC = -1.0;
				if (cC > 1.0)
					cC = 1.0;
				double ang = Math.acos(cC);
				area += 0.5 * r * r * ang; // add area of sector
			}

			// effective radii at interior if it is adjustable
			if (myPLite.aims[v]>0.0)
				myPLite.radii[v] = Math.sqrt(area / Math.PI); 
		} // finished with interiors
		
		if (mode==FIXED_BDRY)
			return 1;

		// now do boundary, where, if there's not specified angle sum,
		//    we try to maintain the current angle sum;
		for (int v = myPLite.intVertCount+1; v <= myPLite.vertCount; v++) {
			double area = 0;
			double angsum=0;
			Complex z = new Complex(myPLite.centers[v]);
			int[] flower = myPLite.flowers[v];
			int num = myPLite.vNum[v]-1; // num faces
			for (int j = 0; j <= num; j++) {
				int jr = flower[j];
				int jl = flower[j + 1];
				Complex zr = new Complex(myPLite.centers[jr]);
				Complex zl = new Complex(myPLite.centers[jl]);
				// radius of sector at v
				double r = 0.5 * (zr.minus(z).abs() + zl.minus(z).abs() - zr.minus(zl).abs());
				double cC = EuclMath.e_cos_corners(myPLite.centers[v],
						myPLite.centers[jr], myPLite.centers[jl]);
				if (cC < -1.0)
					cC = -1.0;
				if (cC > 1.0)
					cC = 1.0;
				double ang = Math.acos(cC);
				angsum += ang;
				area += 0.5 * r * r * ang; // add area of sector
			}

			// for positive aim, want effective radii to give same area as aim would
			if (myPLite.aims[v]>0.001) { // 
				angsum=myPLite.aims[v];
				myPLite.radii[v] = (Math.sqrt(2.0*area/angsum)); // + myPLite.radii[v]) / 2.0;
			}
			// for negative aim, want area as current angle sum would give, except we
			//    average radius with current value to prevent oscillations (can test if
			//    this is helpful later
			else if (myPLite.aims[v]<-.001) { // or same 
				myPLite.radii[v] = (Math.sqrt(2.0*area/angsum) + myPLite.radii[v]) / 2.0;
			}
			
			// Note: if 'targetArea' is between -.001 and .001, then the
			// local radius does not change. (E.g., with 3 bdry circles
			// in the spherical case.)
			
		} // finished with boundary	
		
		return 1;
	}
    
    public double l2quality(double crit) {
    	double []ans=packQuality(crit);
    	return ans[0];
    }

    /**
     * Return the L2 and sup error in the angle sums at interiors.
     * The layout is in 'centers' as computed via Tutte-type embedding, 
     * so its interior angle sums should all be 2*pi.
     * However, we see the discrepancy when they are computed from radii.
     * @param crit double; what's this for? threshold? I don't recall 
     * @return double[2], L2 norm of error and sup norm of error, resp.
     */
    public double []packQuality(double crit) {
    	double []ans=new double[2];
    	double sas=0.0;
    	for (int k=1;k<=myPLite.intVertCount;k++) {
    		double r=myPLite.radii[k];
    		int num=myPLite.vNum[k];
			int []flower=myPLite.flowers[k];
    		sas=-myPLite.aims[k];
    		for (int j=0;j<num;j++) {
    			sas += Math.acos(EuclMath.e_cos_overlap(r,
						myPLite.radii[flower[j]],myPLite.radii[flower[j+1]]));
			}
    		sas=Math.abs(sas);
    		if (sas>ans[0]) ans[0]=sas;
			ans[1] += sas*sas;
		}
    	return ans;
	}
    
    /**
     * In this instance, reap both radii and centers from myPLite
     * and put them in p itself. This may involve conversions for
     * hyp and sph cases.
     */
	public void reapResults() {
		if (p.hes==0) { // eucl case
			for (int v=1;v<=p.nodeCount;v++) {
				int k=myPLite.parent2v[v];
				if (k>0) {
					p.setRadius(v,myPLite.radii[k]);
					p.setCenter(v,new Complex(myPLite.centers[k]));
				}
			}
			return;
		}
		if (p.hes>0) { // sph case
			for (int v=1;v<=p.nodeCount;v++) {
				int k=myPLite.parent2v[v];
				CircleSimple sc=SphericalMath.e_to_s_data(myPLite.centers[k],myPLite.radii[k]);
				if (k>0) {
					p.setCenter(v,new Complex(sc.center));
					p.setRadius(v,sc.rad);
				}
			}
			
			NSpole nsPoler=new NSpole(p);  // routines are here
			nsPoler.parseNSpole(null);
			return;
		}
		else { // hyp case
			for (int v=1;v<=p.nodeCount;v++) {
				int k=myPLite.parent2v[v];
				CircleSimple sc=HyperbolicMath.e_to_h_data(myPLite.centers[k],myPLite.radii[k]);
				if (k>0) {
					p.setCenter(v,new Complex(sc.center));
					p.setRadius(v,sc.rad);
				}
			}
			return;
		}
	}

	/* ******************************************************
     * Methods for positioning boundary circles
     ****************************************************** */
    
    /** 
     * Position bdry circles around interior of unit circle. Data is euclidean;
     * find circle radius R by Newton method, scale all 'radii' to fit the unit
     * circle, set only bdry 'centers'.
     * @return double, computed R
     */
    public double setHoroCenters() {
    	double fvalue=0.0;
    	double fprime=0.0;
    	
    	// initial guess: (sum of bdry radii)/pi
    	double R=0.0;
    	double minrad=0.0;
    	double []r=new double[myPLite.bdryCount+1]; // closed list of radii
    	for (int j=0;j<myPLite.bdryCount;j++) {
    		R += r[j]=myPLite.radii[j+myPLite.intVertCount+1];
    		minrad= (r[j]>minrad) ? r[j] : minrad;
    		if (java.lang.Double.isNaN(R))
    			throw new DataException("R is NaN ???");
    	}
    	r[myPLite.bdryCount]=r[0]; // to close up
    	R /= Math.PI;
    	if (R<2.0*minrad)
    		R=3.0*minrad;

    	// Newton iteration to find R
    	boolean keepon=true;
    	while (keepon) {
    		
    		fvalue=-2.0*Math.PI;
    		fprime=0.0;
    		for (int j=0;j<myPLite.bdryCount;j++) {
    			double Rrr=R-r[j]-r[j+1];
    			double RRrr=R*Rrr;
    			double ab=r[j]*r[j+1];
    			fvalue += Math.acos((RRrr-ab)/(RRrr+ab));
    			if (java.lang.Double.isNaN(fvalue))
    				throw new DataException("acos failed: R="+R+"; r[j]="+r[j]);
    			fprime += -1.0*(R+Rrr)*Math.sqrt(ab/RRrr)/(RRrr+ab);
    		}
    		
    		// is this working?
    		double newR=R-fvalue/fprime;
    		if (newR<R/2.0 || newR>2.0*R) {
//    			System.err.println("big change in R="+R+"; newR="+newR);
    			if (newR<R/2.0) { 
    				newR=R/2;
    				if (newR<2.0*minrad)
    					newR=(2.0*minrad+R)/2.0;
    			}
    			if (newR>2.0*R)
    				newR=2.0*R;
    		}
    		
    		if (Math.abs(newR-R)<.00001)
    			keepon=false;
    		R=newR;
    	} // end of while
    	
    	// scale all radii by 1/R
    	for (int k=1;k<=myPLite.vertCount;k++)
    		myPLite.radii[k] /= R;
    	
    	// set boundary centers, first on y-axis.
    	myPLite.centers[myPLite.intVertCount+1]=new Complex(0.0,1.0-myPLite.radii[myPLite.intVertCount+1]);
//    	double chkarg=0.0; // for debugging
		for (int k=myPLite.intVertCount+2;k<=myPLite.vertCount;k++) {
			double rj=myPLite.radii[k-1];
			double rj1=myPLite.radii[k];
   			double RRrr=1.0-rj-rj1;
			double ab=rj*rj1;
			double delta = Math.acos((RRrr-ab)/(RRrr+ab));
			double arg=myPLite.centers[k-1].arg()+delta;
//			chkarg +=delta;
			double d=1.0-myPLite.radii[k];
			myPLite.centers[k]=new Complex(d*Math.cos(arg),d*Math.sin(arg));
		} 
//		chkarg /=2.0*Math.PI;
//		System.out.println("R is "+R+" and chkarg is "+chkarg);
    	return R;
    }
    
    /** 
     * Position bdry circles as chain of circle orthogonal to unit circle. 
     * Data is euclidean; find circle radius R by Newton method, scale all 
     * 'radii' to fit the unit circle, set only bdry 'centers'.
     * @return double, computed R
     */
    public double setOrthoCenters() {
    	double fvalue=0.0;
    	double fprime=0.0;
    	
    	// initial guess: (sum of bdry radii)/pi
    	double R=0.0;
    	double minrad=0.0;
    	double []r=new double[myPLite.bdryCount+1]; // closed list of radii
    	for (int j=0;j<myPLite.bdryCount;j++) {
    		R += r[j]=myPLite.radii[j+myPLite.intVertCount+1];
    		minrad= (r[j]>minrad) ? r[j] : minrad;
    		if (java.lang.Double.isNaN(R))
    			throw new DataException("R is NaN ???");
    	}
    	r[myPLite.bdryCount]=r[0]; // to close up
    	R /= 2.0*Math.PI;
    	if (R<minrad)
    		R=2.0*minrad;

    	// Newton iteration to find R
    	boolean keepon=true;
    	while (keepon) {
    		
    		double RR=R*R;
    		fvalue=-2.0*Math.PI;
    		fprime=0.0;
    		for (int j=0;j<myPLite.bdryCount;j++) {
    			fvalue += 2.0*Math.atan(r[j]/R); // angle each boundary circle intercepts at origin
    			if (java.lang.Double.isNaN(fvalue))
    				throw new DataException("acos failed: R="+R+"; r[j]="+r[j]);
    			fprime += -2.0*r[j]/(RR+r[j]*r[j]);
    		}
    		
    		// is this working?
    		double newR=R-fvalue/fprime;
    		if (newR<R/2.0 || newR>2.0*R) {
//    			System.err.println("big change in R="+R+"; newR="+newR);
    			if (newR<R/2.0) { 
    				newR=R/2;
    				if (newR<minrad)
    					newR=(minrad+R)/2.0;
    			}
    			if (newR>2.0*R)
    				newR=2.0*R;
    		}
    		
    		if (Math.abs(newR-R)<.00001)
    			keepon=false;
    		R=newR;
    	} // end of while
    	
    	// scale all radii by 1/R
    	for (int k=1;k<=myPLite.vertCount;k++)
    		myPLite.radii[k] /= R;
    	
    	// set boundary centers, first on y-axis; center is distance sqrt(r^2+1^2) from origin

    	double rad=myPLite.radii[myPLite.intVertCount+1];
    	myPLite.centers[myPLite.intVertCount+1]=new Complex(0.0,Math.sqrt(rad*rad+1.0));
    	double accumArg=Math.atan(rad)+Math.PI/2.0; // pi/2 + half of angle subtended by first bdry circle 
		for (int k=myPLite.intVertCount+2;k<=myPLite.vertCount;k++) {
			rad=myPLite.radii[k];
			double delta=Math.atan(rad);
			accumArg +=delta; // increment by half the angle subtended
			double d=Math.sqrt(rad*rad+1.0);
			myPLite.centers[k]=new Complex(d*Math.cos(accumArg),d*Math.sin(accumArg));
			accumArg +=delta; // increment by the other half
		} 
    	return R;
    }
    
    /**
     * For packings of polygons, this places the centers in the
     * appropriate normalized positions.
     * TODO: for now, just taking care of rectangles
     * @return int, count of vertices places (should be bdryCount)
     */
    public int setPolyCenters() {

    	int count=0;
    	int N=0;
    	if (sides==null || (N=sides.size())<3)
    		throw new ParserException("No sides or too few sides given");
    	
    	// record the side lengths based on radii
    	double []sidelengths=new double[N];
    	for (int i=0;i<N;i++) {
    		NodeLink myside=sides.get(i);
    		int n=myside.size();
    		
    		// first and last radii
    		double length=myPLite.radii[myside.get(0)];
    		length +=myPLite.radii[myside.get(n-1)];
    		
    		// plus diameters of circle in between
    		for (int j=1;j<n-1;j++)
    			length +=2.0*myPLite.radii[myside.get(j)];
    		sidelengths[i]=length;
    	}
    	
    	// for rectangle (or even number of sides), average opposites
    	double []avglengths=new double[N];
    	if (2*(int)(((double)N)/2.0)==N) {
    		int no2=N/2;
    		for (int i=0;i<no2;i++) {
    			avglengths[i]=avglengths[i+no2]=(sidelengths[i]+sidelengths[i+no2])/2.0;
    		}
    	}
    	
    	switch(N) {
    	case 4: // rectangle
    	{
    		double width=avglengths[0];
    		double height=avglengths[1];
    		double aspect=height/width;
    		double factor=2.0*(aspect+1)/(width+height);
    		for (int v=1;v<=myPLite.vertCount;v++)
    			myPLite.radii[v] *= factor;
    		for (int i=0;i<4;i++) {
    			sidelengths[i] *=factor;
    			avglengths[i] *=factor;
    		}
    		
        	// lowerleft (-1,-aspect), upper right (1,aspect).
        	//   On each edge, spread vertices proportially to cover
        	// unit vector edge directions, based on arguments
        	// convention: 0'th edge is horizontal, right to left
        	Complex []edgedir=new Complex[4];
        	double turn=Math.PI;
        	edgedir[0]=new Complex(0.0,turn).exp(); // 
        	
        	// successive directions computed from turning angles
        	for (int i=1;i<4;i++) {
        		turn += Math.PI-cornerAngles[(i+1)%4];
        		edgedir[i]=new Complex(0.0,turn).exp();
        	}

    	    // plant the initial corner, by convention at 1+aspect*i
    	    Complex spot=new Complex(1.0,aspect);

        	// do the edges in turn
        	for(int k=0;k<4;k++) {
        		double sidefactor=avglengths[k]/sidelengths[k];
        	    NodeLink side=sides.get(k);
        	    int n=side.size();
        	    
        	    myPLite.centers[side.get(0)]=new Complex(spot);
        	    double prev=sidefactor*myPLite.radii[side.get(0)];
        	    count++;
        	    for (int m=1;m<n;m++) {
        	    	int v=side.get(m);
        	    	double next=sidefactor*myPLite.radii[v];
        	        spot=spot.add(edgedir[k].times(prev+next));
        	        myPLite.centers[v]=new Complex(spot);
        	        prev=next;
        	        count++;
        	    }
        	}
        	break;
    	}
    	case 3: // triangle
    	{
    		// TODO: fix this up. Must be given 'cornerAngles'
    		break; 
    	}
    	
    	} // end of switch
    	
    	return count;
    }

    /**
     * For packings of polygons, this places the centers in the
     * appropriate normalized positions.
     * TODO: for now, just taking care of rectangles
     * @return int, count of vertices places (should be bdryCount)
     */
    public int setFixedShape() {

    	int count=0;
    	int N=0;
    	if (sides==null || (N=sides.size())<3)
    		throw new ParserException("No sides or too few sides given");
    	
    	// record the side lengths based on radii
    	double []sidelengths=new double[N];
    	for (int i=0;i<N;i++) {
    		NodeLink myside=sides.get(i);
    		int n=myside.size();
    		
    		// first and last radii
    		double length=myPLite.radii[myside.get(0)];
    		length +=myPLite.radii[myside.get(n-1)];
    		
    		// plus diameters of circle in between
    		for (int j=1;j<n-1;j++)
    			length +=2.0*myPLite.radii[myside.get(j)];
    		sidelengths[i]=length;
    	}
    	
    	// get intended lengths and unit vector directions of the sides
    	double []givenlength=new double[N];
    	Complex []edgedir=new Complex[N];
    	for (int i=0;i<N;i++) {
    		NodeLink myside=sides.get(i);
    		Complex fz=myPLite.centers[myside.getFirst()];
    		Complex lz=myPLite.centers[myside.getLast()];
    		Complex vec=fz.minus(lz);
    		givenlength[i]=vec.abs();
    		edgedir[i]=vec.divide(givenlength[i]);
    	}
    		
    	for (int s=0;s<N;s++) {
    		double sidefactor=givenlength[s]/sidelengths[s];
    	    NodeLink myside=sides.get(s);
    	    int n=myside.size();
    	    Complex spot=new Complex(myPLite.centers[myside.getFirst()]);
    	    double prev=sidefactor*myPLite.radii[myside.get(0)];
    	    count++;
    	    for (int m=1;m<n;m++) {
    	    	int v=myside.get(m);
    	    	double next=sidefactor*myPLite.radii[v];
    	        spot=spot.add(edgedir[s].times(prev+next));
    	        myPLite.centers[v]=new Complex(spot);
    	        prev=next;
    	        count++;
    	    }
    	}
    	
    	return count;
    }
 
    /**
     * Given the corners, set up the 'sides', cclw lists of vertices 
     * forming the sides, including first and last vertex.
     * @param crnrs[] int, corner vertices, local indices
     * @return
     */
    public int sideSetup(int []crnrs) {
    	if (myPLite.vertCount  <=1)
    		throw new CombException("GOpack failed: no interior vertices");

    	// get/check the corners
    	int n=crnrs.length;
    	if (n<3)
    		throw new ParserException("GOpack failed: less than 3 corners specified");
    	
    	// put corners in cclw 'neworder'
    	sides=new ArrayList<NodeLink>(n);
    	int []neworder=new int[n+1];
    	int first=crnrs[0];
    	
    	// create a knockout list
    	int []kolst=new int[n];
    	for (int i=1;i<n;i++)
    		kolst[i]=crnrs[i];
    	neworder[0]=neworder[n]=first; // first one remains first
    	int tick=1;
    	for (int i=1;(i<myPLite.bdryCount && tick<n);i++) {
    		int indx=(first+i-myPLite.intVertCount-1)%myPLite.bdryCount+myPLite.intVertCount+1;
    		for (int j=1;j<n;j++) {
    			if (indx==kolst[j]) {
    				neworder[tick]=indx;
    				kolst[j]=0;
    				tick++;
    			}
    		}
    	}
    	if (tick<n) {
    		throw new ParserException("didn't get all the corners in order");
    	}
    	
    	// now find the segments
    	int current=first;

        for (int i=0;i<n;i++) {
        	NodeLink nlk=new NodeLink();
        	nlk.add(current);
        	for (int j=1;j<myPLite.bdryCount;j++) {
        		int k=(current+j-myPLite.intVertCount-1)%myPLite.bdryCount+myPLite.intVertCount+1;
        		if (k==neworder[i+1]) {
        			nlk.add(k);
        			current=k;
        			j=myPLite.bdryCount; // kickout
        		}
        		else
        			nlk.add(k);
        	}
        	sides.add(nlk);
        }
        
        // if no cornerAngles, set to default pi-2pi/n.
        if (cornerAngles==null || cornerAngles.length!=n) {
        	cornerAngles=new double[n];
        	for (int i=0;i<n;i++)
    			cornerAngles[i]=Math.PI*(1-2.0/(double)n);
        }
        
        // set aims: 2*pi in interior, pi on bdry, angles at corners
        myPLite.aims=new double[myPLite.vertCount+1];
        for (int i=1;i<=myPLite.intVertCount;i++)
        	myPLite.aims[i]=2.0*Math.PI;
        for (int i=myPLite.intVertCount+1;i<=myPLite.vertCount;i++)
        	myPLite.aims[i]=Math.PI;
        for (int i=0;i<n;i++)
        	myPLite.aims[corners[i]]=cornerAngles[i]; 

        return n;
    }
    
    /**
     * Given eucl boundary radii, form bdry circles into a triangle
     * <u,v,w>, with given angle at v.
     * 
     * Position v at origin, w on positive x-axis, u ccw
     * @param v int, principal corner
     * @param u int
     * @param w int
     * @param v_ang double, designated angle at v
     * @return double, stretch required in edge <w,u>
     */
    public double triSet(int v,int u,int w,double v_ang) {
    	if (!p.isBdry(v) || !p.isBdry(u) ||	!p.isBdry(w) || 
    			v_ang<.01 || (v_ang-Math.PI)<.1)
    		throw new DataException("corner or angle problem in 'triSet'");
    	
    	// get length of edges <v w>
    	double leg_vw=myPLite.radii[myPLite.parent2v[v]]+myPLite.radii[myPLite.parent2v[w]];
    	int next=v;
    	while((next=p.kData[next].flower[0])!=w) {
    		leg_vw += 2.0*myPLite.radii[myPLite.parent2v[next]];
    	}
    	
    	// get length of edges <u v>
    	double leg_uv=myPLite.radii[myPLite.parent2v[v]]+myPLite.radii[myPLite.parent2v[u]];
    	next=u;
    	while((next=p.kData[next].flower[0])!=v) {
    		leg_uv += 2.0*myPLite.radii[myPLite.parent2v[next]];
    	}
    	
    	// get length of opposite side (strictly between u and w) from radii
    	double opp_mid=0.0;
    	next=w;
    	while((next=p.kData[next].flower[0])!=u) {
    		opp_mid += 2.0*myPLite.radii[myPLite.parent2v[next]];
    	}
    	double addon=myPLite.radii[myPLite.parent2v[w]]+myPLite.radii[myPLite.parent2v[u]];
    	
    	// get intended length based on leg lengths and ang_v
    	Complex wu_vector=MathComplex.exp(new Complex(Math.log(leg_uv),v_ang)).
    			minus(new Complex(leg_vw,0.0));
    	double d=wu_vector.abs()-addon;
    	wu_vector=wu_vector.divide(wu_vector.abs()); // unit vector
    	if (d<(0.5*(leg_uv+leg_vw)))
    		throw new DataException("opposite side too restricted to adjust");
    	
    	// adjust 'radii' between to get intended length
    	double factor=d/opp_mid;
    	next=w;
    	while ((next=p.kData[next].flower[0])!=u)
    		myPLite.radii[myPLite.parent2v[next]] *= factor;
    	
    	// lay out the circles: 
    	// v at origin, extend vw along x-axis
    	myPLite.centers[myPLite.parent2v[v]]=new Complex(0.0);
    	next=v;
    	double dist=myPLite.radii[myPLite.parent2v[next]];
    	while ((next=p.kData[next].flower[0])!=w) {
    		int ni=myPLite.parent2v[next];
    		dist +=myPLite.radii[ni];
    		myPLite.centers[ni]=new Complex(dist);
    		dist +=myPLite.radii[ni];
    	
    		myPLite.centers[myPLite.parent2v[w]]=new Complex(leg_vw);
    	
    		// u to v in opposite direction of v_ang
    		Complex uv_vector=MathComplex.exp(new Complex(0.0,v_ang)); // unit vector
    		myPLite.centers[myPLite.parent2v[u]]=new Complex(uv_vector).times(leg_uv);
    		next=u;
    		dist=leg_uv-myPLite.radii[myPLite.parent2v[u]];
    		while ((next=p.kData[next].flower[0])!=v) {
    			int nid=myPLite.parent2v[next];
    			dist -=myPLite.radii[nid];
    			myPLite.centers[nid]=uv_vector.times(dist);
    			dist -=myPLite.radii[nid];
    		}
    
    		// layout opposite side
    		next=w;
    		dist=myPLite.radii[myPLite.parent2v[next]];
    		while ((next=p.kData[next].flower[0])!=u) {
    			int nid=myPLite.parent2v[next];
    			dist +=myPLite.radii[nid];
    			myPLite.centers[nid]=wu_vector.times(dist);
    			dist +=myPLite.radii[nid];
    		}
    	}
   		return factor;
    }
    
    /**
     * Reset corner info for polygonal packings. This nulls out old info,
     * but new is set up elsewhere when the code sees that 'parentCorners' 
     * is not null but 'corners' and 'sides' are.
     * @param pCorners int[] of parent corner vertices or null
     * @return int
     */
    public int setCorners(int []pCorners,double []pAngles) {
		setMode(NOT_YET_SET); // default (eventually max pack); should reset later
    	corners=null;
    	sides=null;
    	parentCorners=null;
    	cornerAngles=null;
    	cornerLocations=null;
    	if (pCorners==null) {
    		return 0;
    	}
    	int n=0;
    	if ((n=pCorners.length)<3)
    		throw new ParserException("GOpack needs at least 3 corners");
    	
    	// set corners, both parent and local 
    	parentCorners=new int[n];
    	corners=new int[n];
    	cornerAngles=new double[n];
    	for (int i=0;i<n;i++) {
    		parentCorners[i]=pCorners[i];
    		corners[i]=myPLite.parent2v[parentCorners[i]];
    	}
    	
    	// set corner angles, default is pi*(1-2/n)
    	if (pAngles!=null && pAngles.length==n) {
    		for (int i=0;i<n;i++)
    			cornerAngles[i]=Math.PI*pAngles[i];
    	}
    	else { // default
    		for (int i=0;i<n;i++)
    			cornerAngles[i]=Math.PI*(1-2.0/((double)n));
    	}
    	setMode(POLY_PACK);
    	
    	return sideSetup(corners);
    }
    
    /**
     * Typically used in FIXED_BDRY case when the bdry radii and centers 
     * are to remain fixed. Alternately, to initialize bdry data in a particular way.
     * @return int count
     */
    public int setBdrys() {
    	int count=0;
    	for (int j=0;j<myPLite.bdryCount;j++) {
    		int k=myPLite.intVertCount+1+j; // local index
    		int w=myPLite.v2parent[k]; // parent index
    		myPLite.centers[k]=p.getCenter(w);
    		myPLite.radii[k]=p.rData[w].rad;
    		count++;
    	}
    	return count;
    }
    
    /**
     * Spherical packings involve three fixed bdry circles; this
     * sets their radii/centers. The three circles are radius 2*sqrt(3)-3,
     * form tangent triple, and are tangent to the unit circle, first at z=i.
     * @return 0 if bdry does not have 3 vertices
     */
    public int setSphBdry() {
    	if (myPLite.bdryCount!=3)
    		return 0;
    	double rad=2*Math.sqrt(3)-3.0; // radius so circles are
    	double ca=1.0-rad;
    	int k=myPLite.intVertCount+1;
		myPLite.radii[k]=rad;
    	myPLite.centers[k]=new Complex(0.0,ca);
    	k++;
		myPLite.radii[k]=rad;
    	myPLite.centers[k]=new Complex(-Math.sqrt(3),-1.0).times(ca*.5);
    	k++;
		myPLite.radii[k]=rad;
    	myPLite.centers[k]=new Complex(Math.sqrt(3),-1.0).times(ca*.5);
    	return 3;
    }
    
    /**
     * Return string message on the RePacker status. 
     * @return String
     */
    public String getStatus() {
    	StringBuilder strb=new StringBuilder("GOpack for p"+p.packNum+" ");
    	if (status==RePacker.FAILURE)
    		strb.append("is in FAILURE state");
    	else if (status==RePacker.RIFFLE)
    		strb.append("is ready to RIFFLE");
    	else if (status==RePacker.LOADED)
    		strb.append("is LOADED and ready");
    	else if (status==RePacker.IN_THREAD)
    		strb.append("is working IN_THREAD (in a separate thread)");
    	return strb.toString();
    }
    
    /**
     * Get the original parent's nodeCount as saved on startup
     * @return origNodeCount
     */
    public int getOrigNodeCount() {
    	return origNodeCount;
    }
    
    /**
     * Specifically for sph max packing; if the boundary exists and
     * has three vertices, return their parent indices. Else, return
     * null.
     * @return int[3] or null
     */
    public int []getSphCorners() {
    	if (myPLite.bdryCount!=3)
    		return null;
    	int []ans=new int[3];
    	for (int i=0;i<3;i++)
    		ans[i]=myPLite.v2parent[myPLite.intVertCount+i+1];
    	return ans;
    }
    
}

/**
 * Internal class: Triple of integers {v, w, j}, wherein vertex w is a 
 * neighbor of vertex v having index j in the flower of v. We want to
 * keep an array of these which we can sort into ascending order on w
 * first, then within w, on v.
 * @author kstephe2
 */
class VWJ implements Comparable<Object> {
	public int v;
	public int j;
	public int w;

	public VWJ(int nv,int nw,int nj) {
		v=nv;
		w=nw;
		j=nj; // often j = -1 when v == w
	}

	/**
	 * Compare 'this' to argument: return 1 if this is greater, 
	 * -1 if this is less, 0 if equal
	 */
	public int compareTo(Object o1) {
		int vv=((VWJ)o1).v;
		int ww=((VWJ)o1).w;
		if (this.w<ww)
			return -1;
		else if (this.w==ww) {
			if (this.v==vv)
				return 0;
			else if (this.v>vv)
				return 1;
			else
				return -1;
		}
		return 1;
	}

}
