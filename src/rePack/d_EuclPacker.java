package rePack;

import JNI.JNIinit;
import allMains.CirclePack;
import exceptions.DataException;
import exceptions.PackingException;
import input.CommandStrParser;
import komplex.KData;
import listManip.NodeLink;
import packing.PackData;
import packing.RData;

/**
 * Euclidean repacking with DCEL structures
 * @author kstephe2 1/2021
 *
 */
public class d_EuclPacker extends RePacker {
	
    // Constructors
    public d_EuclPacker(PackData pd,int pass_limit) { // pass_limit suggests using Java methods
    	p=pd;
		oldReliable=false;
    	if (pass_limit<0) passLimit=PASSLIMIT;
		else passLimit=pass_limit;
		status=load(); 
		if (status!=LOADED) 
			throw new PackingException("'d_EuclPacker' failed to load");
		totalPasses=0;
		localPasses=0;
		R1=new double[p.nodeCount+1];
		R2=new double[p.nodeCount+1];
    }
  
    /**
     * Load relevant radius data
     * @return LOADED =1 or FAILURE = -1
     */
    public int load() {
    	if ((pdcel=p.packDCEL)==null)
    		throw new DataException("usage: d_repackers need DCEL");
    	aimnum = 0;
    	index =new int[p.nodeCount+1];
    	for (int i=1;i<=p.nodeCount;i++) {
    		if (p.getAim(i)>0) {
    			index[aimnum]=i;
    			aimnum++;
    		}
    	}
    	if (aimnum==0)  // nothing to repack
    		return FAILURE;
    	
    	// load the 'TriData'; true if inv distances involved
   		oldReliable=triDataLoad();
   		
    	return LOADED; 
    }
    
    public double l2quality(double crit) {
    	// TODO: need quality measurement tools (presumably based purely
    	//   on radii and curvature, since centers are not available locally.
    	return 1.0;
    }
    
    /**
     * Store any newly computed radii (from 'triData') and
     * recompute curvatures.
     */
    public void reapResults() {
    	for (int i=0;i<aimnum;i++) {
    		int v=index[i];
    		int findx=p.vData[v].findices[0];
    		int vindx=p.vData[v].myIndices[0];
    		p.setRadius(v,pdcel.triData[findx].radii[vindx]);
    	}
    	p.fillcurves();
    }
        
    public int reStartRiffle(int passes) {    // continue the packing computation
    	return 1;
    }
    
    /** Compute radii of packing to meet angle_sum targets, specified as
     * "aims". A negative "aim" means that that circle should not have its
     * radius adjusted. aim of zero is possible only for boundary
     * circles in hyp setting.
     * 
     * All routines are iterative, inspired by routine suggested
     * by Thurston. In hyperbolic case, we use 'x-radii', see
     * description elsewhere. If h is infinite, store eucl radius with
     * negative value for use in graphing its horocycle.
     * 
     * Overlap angles specified > Pi/2 can lead to incompatibilities. To
     * avoid domain errors, routines computing angles in such cases return
     * angle Math.PI 
     */
    
    /* Added arguments are positional: if omitted, they take default values 
       as given in the header. Namely,
       
       passNo = PASS_FIRST | PASS_MIDDLE | PASS_LAST
       cutp = null
       state = null
       
       This tells riffle to first allocate memory, initialize superstep
       variables (PASS_FIRST), do specified number of iterations
       (PASS_MIDDLE), and clean up (PASS_LAST).  The two null pointers tell
       riffle not to store any of the state variables from this call to
       riffle, so that superstepping will have to start over the next time
       riffle is called. (The aim was to be able to use in a parallel
       and/or interruptable environment.) So, the default values for these 
       last three parameters should make modified routines behave exactly like the
       original routines.
    */
    
    /* CRC - modified 8/5/02 from h_riffle; uses uniform neighbor model 
       (UNM) and two levels of super steps, with safety measures to avoid 
       bad steps. Anglesum calculations are in-line. */
    
    public int startRiffle() throws PackingException { // initiate packing computation
	
	if (status!=LOADED) throw new PackingException();
	
	maxBadCuts = 0;
	minBadCuts = 0;
	sumBadCuts = 0;
	cntBadCuts = 0;
	
	// set up parameters
	ttoler = 3*aimnum*RP_TOLER;              // adjust tolerance 
	key = 1;                              // initial superstep type 
	m = 1;                                // Type 1 multiplier 
	sct = 1;                              // Type 1 count 
	fct = 2;                              // Type 2 minimum count 
	
	// do one iteration to get started 
	accumErr2 = 0;                           
	try {
	    for (int j=0;j<aimnum;j++) {
	    	int v = index[j];
	    	double faim = p.vData[v].aim;         // get target sum 
	    	double r = getTriRadius(v);            // get present label

	    	// compute anglesum (using local data)
	    	double fbest=compTriCurv(v,r);

	    	// use the model to predict the next value 
	    	int N = 2*p.vData[v].num;
	    	double del = Math.sin(faim/N);
	    	double bet = Math.sin(fbest/N);
	    	double r2 = r*bet*(1-del)/(del*(1-bet));
	    	// store as new radius label 
	    	if (r2<0) 
	    		throw new PackingException();
	    	setTriRadius(v,r2);
	    	p.vData[v].curv = fbest;  // store new angle sum
	    	fbest -= faim;
	    	accumErr2 += fbest*fbest;   // accum abs error 
	    }
	    accumErr2 = Math.sqrt(accumErr2);

	} catch (Exception ex) {
	    status=FAILURE;
	    throw new PackingException();
	}
	status=RIFFLE;
	totalPasses=localPasses=1;
	return RIFFLE;
    }
    
    /**
     * Continue riffling if status is RIFFLE; reset the passLimit.
     * (We don't change 'totalPasses'.)
     */
    public int continueRiffle(int passL) throws PackingException {
	double fbest;
	double faim;
	double c1;
	
	if (status!=RIFFLE) throw new PackingException();
	localPasses=0;
	passLimit=passL;

	// Begin Main Loop 
	while ((accumErr2 >ttoler && localPasses<passLimit)) {
	    
	    for (int i=1;i<=p.nodeCount;i++) 
	    	R1[i] = getTriRadius(i);
	    
	    int numBadCuts = 0;
	    double factor=0.0;
	    do {   // Make sure factor < 1.0
	    	c1 = 0.0;
	    	for (int j=0;j<aimnum;j++) {
	  		  
	            int v = index[j];   // point to active node
	            faim = p.vData[v].aim; // get target sum 
	            double ra = getTriRadius(v);    // get present label
	            
		    	// compute anglesum inline (using local data)
		    	fbest=compTriCurv(v,ra);
	            
	            // use the model to predict the next value 
	            int N = 2*p.vData[v].num;
	            double del = Math.sin(faim/N);
	            double bet = Math.sin(fbest/N);
	            double r2 = ra*bet*(1-del)/(del*(1-bet));
	            // store as new radius label 
	            if (r2<0) 
	            	throw new PackingException();
	            setTriRadius(v,r2);
	            p.vData[v].curv = fbest;       // store new angle sum
	            fbest -= faim;
	            c1 += fbest*fbest;   // accum abs error 
	    	}
	    	c1 = Math.sqrt(c1);
	
	    	factor = c1/accumErr2;
	    	if (factor >= 1.0) {
	    		accumErr2 = c1;
	    		key = 1;
	    		numBadCuts++;
	    	}
	    	if(numBadCuts > MAX_ALLOWABLE_BAD_CUTS) {
	    		throw new PackingException();
	    	}
	    } while(factor >= 1.0);
	    
	    if(cntBadCuts == 0)  {
	    	maxBadCuts = numBadCuts;
	    	minBadCuts = numBadCuts;
	    	sumBadCuts = numBadCuts;
	    }
	    else  {
	    	maxBadCuts = (numBadCuts > maxBadCuts) ? numBadCuts : maxBadCuts;
	    	minBadCuts = (numBadCuts < minBadCuts) ? numBadCuts : minBadCuts;
	    	sumBadCuts += numBadCuts;
	    }
	    cntBadCuts++;
	    
	    // ================= superstep calculation ==================== 
	    
	    // new values 
	    for (int i=1;i<=p.nodeCount;i++) 
	    	R2[i] = getTriRadius(i);
	    
	    // find maximum step one can safely take
	    double lmax = 10000;
	    double fact0=0.0;
	    for (int j=0;j<aimnum;j++) {       // find max step 
		int v = index[j];
		double rat = R2[v] - R1[v];
		double tr=0.0;
		if (rat < 0)
		    lmax = (lmax < (tr= (-R2[v]/rat))) ? lmax : tr; // to keep R>0
	    }
	    lmax = lmax/2;
	    
	    // do super step
	    double lambda=0.0;
	    if (key==1) {            //  type 1  SS 
	    	lambda = m*factor;
	    	double mmax = 0.75/(1-factor);               // upper limit on m
	    	double mm=0.0;
	    	m = (mmax < (mm=(1+0.8/(sct+1))*m)) ? mmax : mm;
	    }
	    else  {               //  type 2 SS
	    	fact0=0.0;
	    	double ftol=0.0;
	    	if (sct>fct && Math.abs(factor-fact0)<ftol) { // try SS-2 
	    		lambda = factor/(1-factor);
		    sct = -1;
		}
		else
		    lambda = factor;               // do something 
	    }
	    lambda = (lambda>lmax) ? lmax : lambda;
	    
	    // interpolate new labels
	    for (int j=0;j<aimnum;j++) {
	    	int v = index[j];
	    	double nwr=R2[v]+lambda*(R2[v]-R1[v]);
	    	if(nwr<0)
	    		throw new PackingException("negative rad at "+v);
	    	setTriRadius(v,nwr);
	    }
	    sct++;
	    fact0 = factor;
	    
	    // end of superstep 
	    
	    // do step/check superstep 
	    accumErr2 = 0;                             
	    for (int j=0;j<aimnum;j++) {
			int v = index[j];

	        faim = p.vData[v].aim; // get target sum 
	        double rc = getTriRadius(v);    // get present label
	        
	    	// compute anglesum inline (using local data)
	        fbest=compTriCurv(v,rc);

	        // use the model to predict the next value
	        int N = 2*p.vData[v].num;
			// set up for model 

			double del = Math.sin(faim/N);
			double bet = Math.sin(fbest/N);
			
	        double r2 = rc*bet*(1-del)/(del*(1-bet));
	        // store as new radius label 
	        if (r2<0) 
	        	throw new PackingException();
	        setTriRadius(v,r2);
	        p.vData[v].curv = fbest;       /* store new angle sum */
	        fbest -= faim;
	        accumErr2 += fbest*fbest;   /* accum abs error */
	    }
        accumErr2 = Math.sqrt(accumErr2);
        
	    // check results 
	    double pred = Math.exp(lambda*Math.log(factor)); // predicted improvement
	    double act = accumErr2/c1;                   // actual improvement 
	    if (act<1) {                   // did some good 
	    	if (act>pred) {          // not as good as expected: reset 
	    		m = 1;
	    		sct = 0;
	    		if (key==1) key = 2;
	    	}                       // implied else: accept result 
	    }
	    else {                           // reset to before superstep 
	    	m = 1;
	    	sct =0;
	    	for (int i=1;i<=p.nodeCount;i++) 
	    		setTriRadius(i,R2[i]);
	    	accumErr2 = c1;
	    	if (key==2) key = 1;
	    }
	    
	    // show activity 
	    if ((localPasses % 10)==0) 
	    	repack_activity_msg();
	    
	    localPasses++;
	} // end of main while loop 
	
	totalPasses+=localPasses;
	return RIFFLE;
    }

    /**
     * DCEL version of original repack algorithm implemented in Java. 
     * Accommodates inversive distances, where other methods may fail.
     * This manipulates radii in 'pdcel.triData' structure, so user must 
     * call 'load' first and then 'reapResults' after. 
     * @param passes int 
     * @return int count, -1 on error
     */
    public int d_oldReliable(int passes) {
      int count = 0;
      double accum=0.0;
      int N=5; // iterations in each radius comp

      // find vertices to work on and set cutoff value
      int []inDex =new int[p.nodeCount+1];
      aimnum=0;
      for (int v=1;v<=p.nodeCount;v++) {
    	  if (p.getAim(v)>0) { //   p.getAim(j)>0) {
    		  inDex[aimnum++]=v;
        	  double curv=compTriCurv(v,getTriRadius(v));
        	  double err=curv-p.getAim(v);
    		  accum += (err<0) ? (-err) : err;
    	  }
      }
      if (aimnum==0) return FAILURE; // nothing to repack
      
      double recip=.333333/aimnum;
      double cut=accum*recip;

      while ((cut > RP_TOLER && count<passes)) {
    	  double r=.5; // to help with debugging
    	  double rr=.5;
    	  for (int j=0;j<aimnum;j++) {
    		  int v=inDex[j];
    		  r=getTriRadius(v);
    		  
    		  if (Math.abs(compTriCurv(v,r)-p.getAim(v))>cut) {
    			  rr=e_RadCalc(v,r,p.getAim(v),N);
    			  setTriRadius(v,rr);
    		  }
          }
          accum=0;
          for (int j=0;j<aimnum;j++) {
        	  int v=inDex[j];
        	  double err=compTriCurv(v,getTriRadius(v))-p.getAim(v);
        	  accum += (err<0) ? (-err) : err;
          }
          cut=accum*recip;
        
          // show activity 
          if ((count % 10)==0) repack_activity_msg();
                
          count++;
      } /* end of while */
      return count;
    }
    
    /**
     * Copied from '*_radcalc'. This uses data in
     * 'TriData' structure, so it knows the geometry.
     * @param v int
     * @param r double
     * @param aim double
     * @param N int
     * @return double (radius)
     */
    public double e_RadCalc(int v,double r, double aim,int N) {
    	double lower=0.5;
  	  	double upper=0.5;
  	  	double factor=0.5;
  	  	double upcurv;
  	  	double lowcurv;
  	  	
  	  	// compute initial curvature
  	  	double curv=compTriCurv(v,r); 
  	    double bestcurv=lowcurv=upcurv=curv;
  	    
  	    // may hit upper/lower bounds on radius
  	    if (bestcurv>(aim+RP_OKERR)) {
  	    	upper=r/factor;
    	    upcurv=compTriCurv(v,upper);
    	    if (upcurv>aim) { // return max possible
    	    	return upper;
    	    }
    	}
    	else if (bestcurv<(aim-RP_OKERR)) {
    		lower=r*factor;
    	    lowcurv=compTriCurv(v,lower);
  	      	if (lowcurv<aim) { // return min possible
  	      		return lower;
  	      	}
    	}
  	    
  	    // iterative secand method
  	    for (int n=1;n<=N;n++) {
  	    	if (bestcurv>(aim+RP_OKERR)) {
  	    		lower=r;
  	    		lowcurv=bestcurv;
  	    		r += (aim-bestcurv)*(upper-r)/(upcurv-bestcurv);
  	    	}
  	    	else if (bestcurv<(aim-RP_OKERR)) {
  	    		upper=r;
  	    		upcurv=bestcurv;
  	    		r -= (bestcurv-aim)*(lower-r)/(lowcurv-bestcurv);
  	    	}
  	    	else 
	    	  return r;
  	    	
  	    	bestcurv=compTriCurv(v,r);
	    }
  	    
  	    return r;
    }
    
    /**
     * Pack as euclidean to form a polgon with equal corner angles.
     * Normalize so the edge from first to second corners 
     * is horizontal (right to left).
     * @param p PackData, 
     * @param crns NodeLink
     * @return
     */
    public static int polyPack(PackData p,NodeLink crns,boolean okayC) {
    	int n=crns.size();
    	for (int i=0;i<n;i++) {
    		if (!p.isBdry(crns.get(i)))
    			throw new DataException("corners must be bdry vertices");
    	}
    	int v=crns.get(1); // last 
    	int w=crns.get(0); // first

    	CommandStrParser.jexecute(p,"geom_to_e");
  	  	p.set_aim_default();
  	  	
  	  	// use traditional java packing routine
  	  	if (!okayC || p.nodeCount<GOPACK_THRESHOLD || !JNIinit.SparseStatus()) {
  	  		
  	  		// set the aims
  	  	  	CommandStrParser.jexecute(p,"set_aim 1.0 b");
  	  	  	double angles=1-2.0/((double)n);
  	  	  	StringBuilder strbld=new StringBuilder("set_aim "+angles+" ");
  	  	  	for (int i=0;i<n;i++)
  	  	  		strbld.append(crns.get(i)+" ");
  	  	  	CommandStrParser.jexecute(p,strbld.toString());
  	  	  	
  	  		CommandStrParser.jexecute(p,"repack 1000");
  	  		CommandStrParser.jexecute(p,"layout");
  	  		CommandStrParser.jexecute(p,"norm_scale -u "+v);
  	  		CommandStrParser.jexecute(p,"norm_scale -h "+v+" "+w);
  	  		return 1;
  	  	}
  	  	
  	  	// from command
  	  	StringBuilder strbld=new StringBuilder("GOpack -b"+n+" ");
  	  	for (int i=0;i<n;i++) 
  	  		strbld.append(crns.get(i)+" ");
  	  	
  	  	CommandStrParser.jexecute(p,strbld.toString());
  	  	return CommandStrParser.jexecute(p,"GOpack -c 30");
    }
  	  	
    /**
     * Static up/down (or down/up) Perron version of oldReliable.
     * Not intended for speed, rather to try to ensure monotone 
     * behavior of radii. We use the uniform neighbor model (which 
     * theoretically avoids overshooting); this also allows us to
     * easily convert computation to euclidean.
	 *
     * 'direction': radii go down only, down/up in turn, up only,
     * or up/down in turn. 
     * 
     * E.g., up/down means increase radii (of adjustable circles) 
     * until anglesum <= aim. Then subsequent downward adjustments will, in
     * theory, producing a packing label (at adjustable circles). This
     * 'up/down' would be considered our standard "downward" Perron: the up
     * stage leads to a superpacking, then the downward Perron. CAUTION:
     * may not have enough passes to reach superpacking, then packing.
     * 
     * Return double[3]: s=anglesum, a=aim
     * 	  [0]: count of iterations, -1 on FAILURE
     *    [1]: l^2 deficiency on upward, sqrt(sum (s-a)^2) when (s-a)>0
     *    [2]: l^2 deficiency on downward, sqrt(sum (s-a)^2) when (s-a)<0
     *    [3]: total l^2 error: sqrt(sum(s-a)^2)
     * 
     * Note: This code could serve as base for an arbitrary precision 
     * version in future.
     * 
     * @param p PackData
     * @param direction int: -1 down only, -2 down/up, 1 up only, 2 up/down
     * @param passes int, maximal number of full passes
     * @return double[]: [0]<0 on failure.
     */
    public static double []euclPerron(PackData p,int direction,int passes) {
    	double []results=new double[4];
    	results[0]=results[1]=results[2]=results[3]=FAILURE;
    	double dirSign=-1.0;
        if (direction>=0) dirSign=1.0;
        boolean both=false;
        if (direction==2 || direction==-2 || direction==0)
        	both=true;
    	KData []kdata=p.kData;
    	RData []rdata=p.rData;
    	int count=0;

        // whom to repack?
        int aimNum = 0;
        int []inDex =new int[p.nodeCount+1];
        for (int k=1;k<=p.nodeCount;k++) {
      	  if (p.getAim(k)>0) {
      		  inDex[aimNum]=k;
      		  aimNum++;
      	  }
        }
        if (aimNum==0) { // nothing to repack
        	return results;
        }
        
        // =============== First stage ======================
        // radii going up if direction>=0 or else down: 
        // compute discrepency from being super/subpacking 
    	double discrepency=0.0;
        double fbest=0.0;
        for (int j=0;j<aimNum;j++) {
        	int v=inDex[j];
        	double r=rdata[v].rad;
    		fbest = 0.0;
    		double r2 = rdata[kdata[v].flower[0]].rad;
  	      	double m2 = r2/(r+r2);
  	      	for (int n=1;n<=kdata[v].num;n++) {
  	      		double m1 = m2;
  	      		r2 = rdata[kdata[v].flower[n]].rad;
  	      		m2 = r2/(r+r2);
  	      		fbest += Math.acos(1-2*m1*m2);
  	      	}
			rdata[v].curv=fbest;
			double diff2=dirSign*(rdata[v].curv-p.getAim(v));
			if (diff2>0.0)
				discrepency += diff2*diff2;
        }
        	
        // Perron until we have a super/subpacking (or pass out)
        int safety=0;
        while (safety<passes && discrepency>RP_TOLER) {
        	safety++;
        	discrepency=0.0;
            for (int j=0;j<aimNum;j++) {
            	int v=inDex[j];
            	double r=rdata[v].rad;

            	// update anglesum for initial r (nghb's may have changed)
        		fbest = 0.0;
        		double r2 = rdata[kdata[v].flower[0]].rad;
      	      	double m2 = r2/(r+r2);
      	      	for (int n=1;n<=kdata[v].num;n++) {
      	      		double m1 = m2;
      	      		r2 = rdata[kdata[v].flower[n]].rad;
      	      		m2 = r2/(r+r2);
      	      		fbest += Math.acos(1-2*m1*m2);
      	      	}
    			rdata[v].curv=fbest;
    			
    			// sub/superpacking at v? want to adjust radius
    			double diff2=dirSign*(rdata[v].curv-p.getAim(v));
    			if (diff2>RP_TOLER) { // uniform neighbor computation
    				int N = 2*kdata[v].num;
    				double del = Math.sin(p.getAim(v)/N);
    				double bet = Math.sin(fbest/N);
    				r = r*bet*(1-del)/(del*(1-bet));
    				rdata[v].rad=r;
    				count++;
    				
    				// update the anglesum for new r
            		fbest = 0.0;
            		r2 = rdata[kdata[v].flower[0]].rad;
          	      	m2 = r2/(r+r2);
          	      	for (int n=1;n<=kdata[v].num;n++) {
          	      		double m1 = m2;
          	      		r2 = rdata[kdata[v].flower[n]].rad;
          	      		m2 = r2/(r+r2);
          	      		fbest += Math.acos(1-2*m1*m2);
          	      	}
        			rdata[v].curv=fbest;
    			
        			// get squared discrepency
        			diff2=dirSign*(rdata[v].curv-p.getAim(v));
        			if (diff2<-(100*RP_TOLER)) {
        				throw new PackingException("uniform neighbor computation overshoots at v="+v);
        			}
    			}
    			if (diff2>0.0)
    				discrepency += diff2*diff2;
            }
        } // end of while loop
        if (dirSign>=0) // upward
        	results[1]=Math.sqrt(discrepency);
        else 
        	results[2]=Math.sqrt(discrepency);
        
        // failed?
        if (safety==passes) {
        	if (dirSign>0)
        		CirclePack.cpb.errMsg("failed to get a superpacking in euclPerron");
        	else
        		CirclePack.cpb.errMsg("failed to get a subpacking in euclPerron");
        }

        // to prepare for stage 2 or return, update discrepency and accum
        double accum=0.0;
        discrepency=0.0;
        for (int j=0;j<aimNum;j++) {
        	int v=inDex[j];
        	double r=rdata[v].rad;
    		fbest = 0.0;
    		double r2 = rdata[kdata[v].flower[0]].rad;
  	      	double m2 = r2/(r+r2);
  	      	for (int n=1;n<=kdata[v].num;n++) {
  	      		double m1 = m2;
  	      		r2 = rdata[kdata[v].flower[n]].rad;
  	      		m2 = r2/(r+r2);
  	      		fbest += Math.acos(1-2*m1*m2);
  	      	}
			rdata[v].curv=fbest;
			double diff=rdata[v].curv-p.getAim(v);
			double sqdiff=diff*diff;
			if (diff>0.0)
				discrepency+=sqdiff;
			accum +=sqdiff;
        }
        double sqError=Math.sqrt(accum);

        if (!both) { // return after the first stage
        	results[0]=count;
        	results[3]=sqError;
        	return results;
        }
        
        // =============== Second stage (optional) =================
        // radii going down if direction>=0 or else up: 
        // set cutoff value
        double recip=.333333/aimNum;
        double cut=sqError*recip;
        
        safety=0;
        while (both && (cut > RP_TOLER) && safety<passes) {
        	safety++;
        	accum=0.0;
        	discrepency=0.0;
        	for (int j = 0; j < aimNum; j++) {
        		int v = inDex[j];
        		double r=rdata[v].rad;

        		// update the anglesum
        		fbest = 0.0;
        		double r2 = rdata[kdata[v].flower[0]].rad;
      	      	double m2 = r2/(r+r2);
      	      	for (int n=1;n<=kdata[v].num;n++) {
      	      		double m1 = m2;
      	      		r2 = rdata[kdata[v].flower[n]].rad;
      	      		m2 = r2/(r+r2);
      	      		fbest += Math.acos(1-2*m1*m2);
      	      	}
      	      	rdata[v].curv=fbest;
        		double diff2=dirSign*(rdata[v].curv-p.getAim(v));
        		if (diff2<-(100*RP_TOLER)) {
        			// uniform neighbor computation
        			int N = 2*kdata[v].num;
        			double del = Math.sin(p.getAim(v)/N);
        			double bet = Math.sin(fbest/N);
        			r = r*bet*(1-del)/(del*(1-bet));
        			rdata[v].rad=r;
        			count++;

        			// update the anglesum for new r
        			fbest=0;
        			r2 = rdata[kdata[v].flower[0]].rad;
        			m2 = r2/(r+r2);
        			for (int n=1;n<=kdata[v].num;n++) {
        				double m1 = m2;
        				r2 = rdata[kdata[v].flower[n]].rad;
        				m2 = r2/(r+r2);
        				fbest += Math.acos(1-2*m1*m2);
        			}
        			rdata[v].curv=fbest;
        		}
        		
        		// update diff2 and gather data
       			diff2=dirSign*(rdata[v].curv-p.getAim(v));
       			double sqdiff=diff2*diff2;
        		if (diff2<-(100*RP_TOLER)) {
        			discrepency+=sqdiff;
        		}
        		accum += sqdiff;
        	} // end of for loop

        	sqError = Math.sqrt(accum);
            cut=sqError*recip;
        	// note that 'cut' is old data; in downward Perron, the
            //    accumulated error should be less than accum.
        } // end of while loop
        
        // update accum and discrepency before returning
        accum=0.0;
        discrepency=0.0;
        for (int j=0;j<aimNum;j++) {
        	int v=inDex[j];
        	double r=rdata[v].rad;
    		fbest = 0.0;
    		double r2 = rdata[kdata[v].flower[0]].rad;
  	      	double m2 = r2/(r+r2);
  	      	for (int n=1;n<=kdata[v].num;n++) {
  	      		double m1 = m2;
  	      		r2 = rdata[kdata[v].flower[n]].rad;
  	      		m2 = r2/(r+r2);
  	      		fbest += Math.acos(1-2*m1*m2);
  	      	}
			rdata[v].curv=fbest;
			double diff=rdata[v].curv-p.getAim(v);
			diff=diff*diff;
			if (diff<0.0)
				discrepency+=diff;
			accum +=diff;
        }
        sqError=Math.sqrt(accum);
        
        results[0]=count;
        if (dirSign>=0) // up/down
        	results[2]=Math.sqrt(discrepency);
        else // down/up
        	results[1]=Math.sqrt(discrepency);
    	results[3]=sqError;
    	return results;
    }
  
	public void setSparseC(boolean useC) {
		useSparseC=false;
		if (useC) { // requested to use GOpacker routines if possible
			if (p.nodeCount<GOPACK_THRESHOLD) { // for smaller packing, use Java
				useSparseC=false;
				return;
			}
			if (JNIinit.SparseStatus())
				useSparseC=true;
		}
		return;
	}
	
	/**
	 * The 'uniform' model (developed by Collins and Stephenson)
	 * computes the radius to get angle sum 'aim' under the
	 * assumption that all neighbors have same radius. In eucl
	 * case, given angle sum 'asum' and number of faces 'num', 
	 * one can instead find the factor by which to multiply the 
	 * current radius. This fits with a face-by-face computation,
	 * as in affine packing cases, where radii are attached to
	 * faces and same vertex may have several associated radii.
	 * (coded 3/2021)
	 * @param num int
	 * @param asum double
	 * @param aim double
	 * @return double
	 */
	public static double uniFactor(int num,double asum,double aim) {
			int N = 2*num;
			// assume all petals have radius 1.0
			double del = Math.sin(aim/N);
			double bet = Math.sin(asum/N);
			// current radius is r=(1/bet)-1.0
			// desired radius is R=(1/del)-1.0
			// return R/r
			return ((1.0/del)-1.0)/((1.0/bet)-1.0);
	}

}