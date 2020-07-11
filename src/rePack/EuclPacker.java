package rePack;

import input.CommandStrParser;
import komplex.KData;
import listManip.NodeLink;
import packing.PackData;
import packing.RData;
import util.UtilPacket;
import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import exceptions.DataException;
import exceptions.PackingException;
import geometry.EuclMath;

public class EuclPacker extends RePacker {
	
    public static final double AIM_THRESHOLD=.0001;  // aim less than this, treat as horocycle
    public static final int EUCL_GOPACK_THRESHOLD=501;  // for smaller packs, default to Java

    // Constructors
    public EuclPacker(PackData pd,int pass_limit) { // pass_limit suggests using Java methods
    	super(pd,pass_limit,false);
    }
    
    public EuclPacker(PackData pd,boolean useC) {
    	super(pd,CPBase.RIFFLE_COUNT,useC);
    }
    
    public EuclPacker(PackData pd) {
    	super(pd,CPBase.RIFFLE_COUNT,true);
    }
    
	public void setSparseC(boolean useC) {
		useSparseC=false;
		if (useC) { // requested to use GOpacker routines if possible
			if (p.nodeCount<EUCL_GOPACK_THRESHOLD) { // for smaller packing, use Java
				useSparseC=false;
				return;
			}
			if (JNIinit.SparseStatus())
				useSparseC=true;
		}
		return;
	}
  
    /**
     * Load relevant radius data
     * @return LOADED =1 or FAILURE = -1
     */
    public int load() {
    	aimnum = 0;
    	index =new int[p.nodeCount+1];
    	for (int i=1;i<=p.nodeCount;i++) {
    		if (p.rData[i].aim>0) {
    			index[aimnum]=i;
    			aimnum++;
    		}
    	}
    	if (aimnum==0) return FAILURE; // nothing to repack
    	if (super.genericLoad()<0) return FAILURE;
    	return LOADED; 
    }
    
    public double l2quality(double crit) {
    	// TODO: need quality measurement tools (presumably based purely
    	//   on radii and curvature, since centers are not available locally.
    	return 1.0;
    }
    
    /**
     * Store newly computed radii in 'rData'
     */
    public void reapResults() {
    	for (int i=0;i<aimnum;i++) {
    		int j=index[i];
    		p.rData[j].rad= rdata[j].rad; 
    	}
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
	int j, j1, j2, N;
	double r, r1, r2,fbest, faim, del, bet;
	double m2,o1,o2;
	UtilPacket utilPacket=new UtilPacket();
	
	if (status!=LOADED) throw new PackingException();
	
	maxBadCuts = 0;
	minBadCuts = 0;
	sumBadCuts = 0;
	cntBadCuts = 0;
	
	// set up parameters
	
	ttoler = 3*aimnum*TOLER;              // adjust tolerance 
	key = 1;                              // initial superstep type 
	m = 1;                                // Type 1 multiplier 
	sct = 1;                              // Type 1 count 
	fct = 2;                              // Type 2 minimum count 
	
	// do one iteration to get started 
	c0 = 0;                           
	try {
		double m1,ovlp;
		int v;
	    for (j=0;j<aimnum;j++) {
	    	v = index[j];
	    	faim = rdata[v].aim;         // get target sum 
	    	r = rdata[v].rad;            // get present label
	    	// compute anglesum inline (using local data)
	    	fbest=0.0;
	    	j2=kdata[v].flower[0];
	    	r2=rdata[j2].rad;
	    	if (!p.overlapStatus) {
	    		m2 = r2/(r+r2);
	    		for (int n=1;n<=kdata[v].num;n++) {
	    			m1 = m2;
	    			r2 = rdata[kdata[v].flower[n]].rad;
	    			m2 = r2/(r+r2);
	    			fbest += Math.acos(1-2*m1*m2);
	    		}
	    	}
	    	else  {
	    		o2=kdata[v].overlaps[0];
			    for (int n=1;n<=kdata[v].num;n++) {
			    	j1=j2;r1=r2;o1=o2;
			    	j2=kdata[v].flower[n];
			    	r2=rdata[j2].rad;
			    	o2=kdata[v].overlaps[n];
			    	ovlp=kdata[j1].overlaps[p.nghb(j1,j2)];
			    	// note: we don't check for errors in utilPacket
			    	EuclMath.e_cos_overlap(r,r1,r2,ovlp,o2,o1,utilPacket);
			    	fbest += Math.acos(utilPacket.value);
			    }
	    	}

	    	// use the model to predict the next value 
	    	N = 2*kdata[v].num;
	    	del = Math.sin(faim/N);
	    	bet = Math.sin(fbest/N);
	    	r2 = r*bet*(1-del)/(del*(1-bet));
	    	// store as new radius label 
	    	if (r2<0) 
	    		throw new PackingException();
	    	rdata[v].rad = r2;
	    	rdata[v].curv = fbest;  // store new angle sum
	    	fbest -= faim;
	    	c0 += fbest*fbest;   // accum abs error 
	    }
	    c0 = Math.sqrt(c0);

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
	int N,j1,j2,numBadCuts,v;
	double c1,fbest,faim,m1,m2;
	double r,r1,r2,o1,o2,ovlp;
	double del,bet,fact0=0.0,ftol=0.0,pred,act;
	double factor,lmax,rat,tr,lambda,mmax,mm;
	UtilPacket utilPacket=new UtilPacket();
	
	if (status!=RIFFLE) throw new PackingException();
	localPasses=0;
	passLimit=passL;
	
	// Begin Main Loop 
	while ((c0 >ttoler && localPasses<passLimit)) {
	    
	    for (int i=1;i<=p.nodeCount;i++) R1[i] = rdata[i].rad;
	    
	    numBadCuts = 0;
	    do {   // Make sure factor < 1.0
	    	c1 = 0.0;
	    	for (int j=0;j<aimnum;j++) {
		  
            v = index[j];   // point to active node
            faim = rdata[v].aim; // get target sum 
            r = rdata[v].rad;    // get present label
            
	    	// compute anglesum inline (using local data)
	    	fbest=0.0;
	    	j2=kdata[v].flower[0];
	    	r2=rdata[j2].rad;
	    	if (!p.overlapStatus) {
	    		m2 = r2/(r+r2);
	    		for (int n=1;n<=kdata[v].num;n++) {
	    			m1 = m2;
	    			r2 = rdata[kdata[v].flower[n]].rad;
	    			m2 = r2/(r+r2);
	    			fbest += Math.acos(1-2*m1*m2);
	    		}
	    	}
	    	else  {
	    		o2=kdata[v].overlaps[0];
			    for (int n=1;n<=kdata[v].num;n++) {
			    	j1=j2;r1=r2;o1=o2;
			    	j2=kdata[v].flower[n];
			    	r2=rdata[j2].rad;
			    	o2=kdata[v].overlaps[n];
			    	ovlp=kdata[j1].overlaps[p.nghb(j1,j2)];
			    	// note: we don't check for errors in utilPacket
			    	EuclMath.e_cos_overlap(r,r1,r2,ovlp,o2,o1,utilPacket);
			    	fbest += Math.acos(utilPacket.value);
			    }
	    	}
            
            // use the model to predict the next value 
            N = 2*kdata[v].num;
            del = Math.sin(faim/N);
            bet = Math.sin(fbest/N);
            r2 = r*bet*(1-del)/(del*(1-bet));
            // store as new radius label 
            if (r2<0) 
            	throw new PackingException();
            rdata[v].rad = r2;
            rdata[v].curv = fbest;       // store new angle sum
            fbest -= faim;
            c1 += fbest*fbest;   // accum abs error 
          }
        c1 = Math.sqrt(c1);
	
		factor = c1/c0;
		if (factor >= 1.0) {
		    c0 = c1;
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
	    
	    // superstep calculation 
	    
	    // save values 
	    for (int i=1;i<=p.nodeCount;i++) R2[i] = rdata[i].rad;
	    
	    // find maximum step one can safely take 
	    lmax = 10000;
	    for (int j=0;j<aimnum;j++) {       // find max step 
		v = index[j];
		r = rdata[v].rad;
		rat = r - R1[v];
		if (rat < 0)
		    lmax = (lmax < (tr= (-r/rat))) ? lmax : tr; // to keep R>0
	    }
	    lmax = lmax/2;
	    
	    // do super step 
	    if (key==1) {            //  type 1  SS 
		lambda = m*factor;
		mmax = 0.75/(1-factor);               // upper limit on m
		m = (mmax < (mm=(1+0.8/(sct+1))*m)) ? mmax : mm;
	    }
	    else  {               //  type 2 SS 
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
	    	v = index[j];
	    	rdata[v].rad += lambda*(rdata[v].rad-R1[v]);
	    	if(rdata[v].rad<0)
	    		throw new PackingException();
	    }
	    sct++;
	    fact0 = factor;
	    
	    // end of superstep 
	    
	    // do step/check superstep 
	    c0 = 0;                             
	    for (int j=0;j<aimnum;j++) {
		fbest = 0;
		v = index[j];

        faim = rdata[v].aim; // get target sum 
        r = rdata[v].rad;    // get present label
        
    	// compute anglesum inline (using local data)
    	fbest=0.0;
    	j2=kdata[v].flower[0];
    	r2=rdata[j2].rad;
    	if (!p.overlapStatus) {
    		m2 = r2/(r+r2);
    		for (int n=1;n<=kdata[v].num;n++) {
    			m1 = m2;
    			r2 = rdata[kdata[v].flower[n]].rad;
    			m2 = r2/(r+r2);
    			fbest += Math.acos(1-2*m1*m2);
    		}
    	}
    	else  {
    		o2=kdata[v].overlaps[0];
		    for (int n=1;n<=kdata[v].num;n++) {
		    	j1=j2;r1=r2;o1=o2;
		    	j2=kdata[v].flower[n];
		    	r2=rdata[j2].rad;
		    	o2=kdata[v].overlaps[n];
		    	ovlp=kdata[j1].overlaps[p.nghb(j1,j2)];
		    	// note: we don't check for errors in utilPacket
		    	EuclMath.e_cos_overlap(r,r1,r2,ovlp,o2,o1,utilPacket);
		    	fbest += Math.acos(utilPacket.value);
		    }
    	}
        
        // use the model to predict the next value
        N = 2*kdata[v].num;
		// set up for model 

		del = Math.sin(faim/N);
		bet = Math.sin(fbest/N);
		
        r2 = r*bet*(1-del)/(del*(1-bet));
        // store as new radius label 
        if (r2<0) 
        	throw new PackingException();
        rdata[v].rad = r2;
        rdata[v].curv = fbest;       /* store new angle sum */
        fbest -= faim;
        c0 += fbest*fbest;   /* accum abs error */
	    }
        c0 = Math.sqrt(c0);
        
	    // check results 
	    pred = Math.exp(lambda*Math.log(factor)); // predicted improvement
	    act = c0/c1;                   // actual improvement 
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
		for (int i=1;i<=p.nodeCount;i++) rdata[i].rad  = R2[i];
		c0 = c1;
		if (key==2) key = 1;
	    }
	    
	    // show activity 
	    if ((localPasses % 10)==0) repack_activity_msg();
	    
	    localPasses++;
	} // end of main while loop 
	
	reapResults();
	totalPasses+=localPasses;
	return RIFFLE;
    }
    
    /**
     * Original repack algorithm implemented in Java. Used, e.g., with 
     * overlap packings, where the more sophisticated Java routines and
     * C methods of Orick may fail. Note that this manipulates radii 
     * directly in the packing data, not using local data; in particular, 
     * don't need to call 'load'.
     * @param pd PackData
     * @param passes int (may be old meaning, hence too small)
     * @return int
     * 
     */
    public static int oldReliable(PackData pd,int passes) {
      int v;
      int count = 0;
      double verr,err,r;
      UtilPacket uP=null;
  
      int aimNum = 0;
      int []inDex =new int[pd.nodeCount+1];
      for (int j=1;j<=pd.nodeCount;j++) {
    	  if (pd.rData[j].aim>0) {
    		  inDex[aimNum]=j;
    		  aimNum++;
    	  }
      }
      if (aimNum==0) return FAILURE; // nothing to repack
      
      // set cutoff value
      double accum=0.0;
      for (int j=0;j<aimNum;j++) {
    	  v=inDex[j];
    	  err=pd.rData[v].curv-pd.rData[v].aim;
              accum += (err<0) ? (-err) : err;
      }
      double recip=.333333/aimNum;
      double cut=accum*recip;

      while ((cut > TOLER && count<passes)) {
    	  for (int j=0;j<aimNum;j++) {
    		  v=inDex[j];
    		  r=pd.rData[v].rad;
    		  
    		  uP=new UtilPacket();
    		  if (!pd.e_anglesum_overlap(v,r,uP)) 
    			  return 0;
    		  pd.rData[v].curv=uP.value;
    		  verr=pd.rData[v].curv-pd.rData[v].aim;
    		  if (Math.abs(verr)>cut) {
    			  if (pd.e_radcalc(v,pd.rData[v].rad,pd.rData[v].aim,5,uP)) {
    				  pd.rData[v].rad=uP.value;	
    				  if (!pd.e_anglesum_overlap(v,r,uP)) 
    					  return 0;
    				  pd.rData[v].curv=uP.value;
    			  }
    		  }
          }
          accum=0;
          for (int j=0;j<aimNum;j++) {
        	  v=inDex[j];
        	  err=pd.rData[v].curv-pd.rData[v].aim;
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
    		if (p.kData[crns.get(i)].bdryFlag==0)
    			throw new DataException("corners must be bdry vertices");
    	}
    	int v=crns.get(1); // last 
    	int w=crns.get(0); // first

    	CommandStrParser.jexecute(p,"geom_to_e");
  	  	p.set_aim_default();
  	  	
  	  	// use traditional java packing routine
  	  	if (!okayC || p.nodeCount<EUCL_GOPACK_THRESHOLD || !JNIinit.SparseStatus()) {
  	  		
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
      	  if (p.rData[k].aim>0) {
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
			double diff2=dirSign*(rdata[v].curv-rdata[v].aim);
			if (diff2>0.0)
				discrepency += diff2*diff2;
        }
        	
        // Perron until we have a super/subpacking (or pass out)
        int safety=0;
        while (safety<passes && discrepency>TOLER) {
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
    			double diff2=dirSign*(rdata[v].curv-rdata[v].aim);
    			if (diff2>TOLER) { // uniform neighbor computation
    				int N = 2*kdata[v].num;
    				double del = Math.sin(rdata[v].aim/N);
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
        			diff2=dirSign*(rdata[v].curv-rdata[v].aim);
        			if (diff2<-(100*TOLER)) {
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
			double diff=rdata[v].curv-rdata[v].aim;
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
        while (both && (cut > TOLER) && safety<passes) {
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
        		double diff2=dirSign*(rdata[v].curv-rdata[v].aim);
        		if (diff2<-(100*TOLER)) {
        			// uniform neighbor computation
        			int N = 2*kdata[v].num;
        			double del = Math.sin(rdata[v].aim/N);
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
       			diff2=dirSign*(rdata[v].curv-rdata[v].aim);
       			double sqdiff=diff2*diff2;
        		if (diff2<-(100*TOLER)) {
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
			double diff=rdata[v].curv-rdata[v].aim;
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
    
}