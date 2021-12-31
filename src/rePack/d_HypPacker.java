package rePack;

import JNI.JNIinit;
import allMains.CirclePack;
import exceptions.DataException;
import exceptions.PackingException;
import geometry.HyperbolicMath;
import komplex.KData;
import packing.PackData;
import packing.RData;

/**
 * First attempt to modify methods to accommodate DCEL data. Begin
 * with oldReliable. Other routines remain here but are not used (1/2021)
 * @author kstephe2 1/2021
 *
 */
public class d_HypPacker extends RePacker {
	
    // Constructors
    public d_HypPacker(PackData pd,int pass_limit) { // pass_limit suggests using Java methods
    	p=pd;
		oldReliable=false;
    	if (pass_limit<0) passLimit=PASSLIMIT;
		else passLimit=pass_limit;
		status=load(); 
		if (status!=LOADED) 
			throw new PackingException("'d_HypPacker' failed to load");
		totalPasses=0;
		localPasses=0;
		R1=new double[p.nodeCount+1];
		R2=new double[p.nodeCount+1];
    }
    
    /**
     * Load relevant data: NOTE!! that x-radii are converted to 
     * (s-radii)^2 for computations (converted back in 'reapRadii').
     * @return int - LOADED or FAILURE
     */
	public int load() {
	   	if ((pdcel=p.packDCEL)==null)
    		throw new DataException("usage: d_repackers need DCEL");
		aimnum = 0;
		index = new int[p.nodeCount + 1];
    	for (int i=1;i<=p.nodeCount;i++) {
    		if (p.getAim(i)>0) {
    			index[aimnum]=i;
    			aimnum++;
    			if (p.getRadius(i)<=0)
    				p.setRadius(i, 0.5);
    		}
    	}
    	if (aimnum==0) return FAILURE; // nothing to repack
    	oldReliable=super.triDataLoad();
    	return LOADED; 

 /* TODO: have to convert this old code designed, I guess,
 * to handle cusps.
 
		for (int i = 1; i <= p.nodeCount; i++) {
			double myaim=p.getAim(i);
			if (p.isBdry(i) && myaim >= 0
					&& myaim < AIM_THRESHOLD) {
				p.setRadius(i,-0.2); // treat interior as horocycle (e.g.,
										// 'cusp')
				rdata[i].rad=-0.2;
			}
			
			else if (myaim > 0) {
				if (rdata[i].rad <= 0 && rdata[i].aim > AIM_THRESHOLD)
					rdata[i].rad = .01; // default starting value
				index[aimnum] = i;
				aimnum++;
			}
			if (rdata[i].rad > 0) // convert all x-radius to (s-radius)^2
				rdata[i].rad = 1.0 - rdata[i].rad;
		}
*/

	}
    
    public double l2quality(double crit) {
	// TODO: need quality measurement tools (presumably based purely
	//   on radii and curvature, since centers are not available locally.
	return 1.0;
    }
    
    /**
     * Store any newly computed radii; get from 'triData'
     */
    public void reapResults() {
    	for (int i=0;i<aimnum;i++) {
    		int v=index[i];
    		int findx=p.vData[v].findices[0];
    		int vindx=p.vData[v].myIndices[0];
    		p.setRadius(v,pdcel.triData[findx].radii[vindx]);
    	}
    }
    
    // continue the packing computation
    public int reStartRiffle(int passes) {   
    	return 1;
    }
    
    /** 
    Compute radii of packing to meet angle_sum targets, specified as
	"aims". A negative "aim" means that that circle should not have its
	radius adjusted. aim of zero is possible only for boundary 
	circles in hyp setting. 
	
	All routines are iterative, inspired by routine suggested
	by Thurston. In hyperbolic case, we use 'x-radii', see 
	description elsewhere. If h is infinite, store eucl radius with 
	negative value for use in graphing its horocycle.
	
	Overlap angles specified > Pi/2 can lead to incompatibilities. To
	avoid domain errors, routines computing angles in such cases return
	angle Math.PI */
    
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
    
    // abstract method for initiating packing computation
	public int startRiffle() throws PackingException { 

		if (status != LOADED) 
			throw new PackingException();

		maxBadCuts = 0;
		minBadCuts = 0;
		sumBadCuts = 0;
		cntBadCuts = 0;

		// set up parameters
		ttoler = 3 * aimnum * RP_TOLER; // adjust tolerance
		key = 1; 			// initial superstep type
		m = 1; 				// Type 1 multiplier
		sct = 1; 			// Type 1 count
		fct = 2; 			// Type 2 minimum count

		// do one iteration to get started
		accumErr2 = 0;
		try {
			for (int j = 0; j < aimnum; j++) {
				int v = index[j];
				double faim = p.vData[v].aim; // get target sum
				double x_rad=getTriRadius(v);
				double r = 1.0-x_rad; // NOTE: computations expect squared s-radii.
				if (r>=1.0)  // infinite?
					r = 0;
				double sr = Math.sqrt(r);
			
		    	// compute anglesum using 'TriData' (which has x-radii)
				double fbest=compTriCurv(v,x_rad);

		    	// use the model to predict the next value 
		    	int N = 2*p.vData[v].num;
				double del = Math.sin(faim/N);
				double bet = Math.sin(fbest/N);
				double r2 = (bet-sr)/(bet*r-sr); // reference radius
				if (r2 > 0) { // calc new label
					double t1 = 1 - r2;
					double t2 = 2 * del;
					double t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
					r2 = t3 * t3;
				} 
				else
					r2 = del * del; // use lower limit
				setTriRadius(v,1-r2); // store new label as x-radius

				// TODO: is this right?
				fbest=compTriCurv(v,1-r2);
				
				p.vData[v].curv = fbest; // store new anglesum
				fbest -= faim;
				accumErr2 += fbest * fbest; // accumulate error
			} // end of for loop
			accumErr2 = Math.sqrt(accumErr2);
			
		} catch (Exception ex) {
			status = FAILURE;
			throw new PackingException();
		}
		status = RIFFLE;
		totalPasses = localPasses = 1;
		return RIFFLE;
	}
    
	// continue riffle computation if status is RIFFLE
	public int continueRiffle(int passL) throws PackingException {

		double fact0 = 0.0;
		double ftol = 0.0;  // not sure we need this
		double c1;

		if (status != RIFFLE)
			throw new PackingException();
		localPasses = 0;
		passLimit = passL;

		// Begin Main Loop
		while ((accumErr2 > ttoler && localPasses < passLimit)) {

			// routines use squared s-radii
			for (int j = 0; j < aimnum; j++) {
				int v=index[j];
				R1[v] = 1.0-getTriRadius(v);
			}

			int numBadCuts = 0;
			double factor=0.0;
			do { // Make sure factor < 1.0
				c1 = 0.0;
				for (int j=0;j<aimnum;j++) { 

					int v = index[j];
					double faim=p.vData[v].aim;
					// Remember, computations depend on using squared s-radius
					double x_rad=getTriRadius(v);
					double r=1.0-x_rad;
					if (r>=1.0)
						r = 0;
					double sr = Math.sqrt(r);

					// compute anglesum: ????? 
					double fbest=compTriCurv(v,x_rad);

					// set up for model
					int N=2*p.vData[v].num;
					double del = Math.sin(faim/N);
					double bet = Math.sin(fbest/N);
					double r2 = (bet - sr) / (bet * r - sr); // reference radius
					if (r2 > 0) { // calc new label
						double t1 = 1 - r2;
						double t2 = 2 * del;
						double t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
						r2 = t3 * t3;
					} else
						r2 = del * del; // use lower limit
					setTriRadius(v,1-r2); // store new x-radii
					p.vData[v].curv = fbest; // store new anglesum
					fbest -= faim;
					c1 += fbest * fbest; // accumulate error
				}
				c1 = Math.sqrt(c1);

				factor = c1 / accumErr2;
				if (factor >= 1.0) {
					accumErr2 = c1;
					key = 1;
					numBadCuts++;
				}
				if (numBadCuts > MAX_ALLOWABLE_BAD_CUTS) {
					throw new PackingException();
				}
			} while (factor >= 1.0);

			if (cntBadCuts == 0) {
				maxBadCuts = numBadCuts;
				minBadCuts = numBadCuts;
				sumBadCuts = numBadCuts;
			} else {
				maxBadCuts = (numBadCuts > maxBadCuts) ? numBadCuts
						: maxBadCuts;
				minBadCuts = (numBadCuts < minBadCuts) ? numBadCuts
						: minBadCuts;
				sumBadCuts += numBadCuts;
			}
			cntBadCuts++;

		    // ================= superstep calculation ==================== 

			// store squared s-radii
			for (int j = 0; j < aimnum; j++) {
				int v=index[j];
				R2[v] = 1.0-getTriRadius(v);
			}

			// find maximum step one can safely take
			double lmax = 10000;
			for (int j=0;j<aimnum;j++) { // find max step
				int v = index[j];
				double r = R2[v];
				double rat = r - R1[v];
				double tr;
				if (rat > 0)
					lmax=(lmax<(tr=((1 - r)/rat))) ? lmax:tr; // to keep R<1
				else if (rat < 0)
					lmax=(lmax<(tr = (-r / rat))) ? lmax : tr; // to keep R>0
			}
			lmax = lmax / 2.0;

			// do super step
			double lambda=0.0;
			if (key == 1) { // type 1 SS
				lambda = m*factor;
				double mmax=0.75/(1-factor);   // upper limit on m
				double mm;
				m = (mmax < (mm = (1+0.8/(sct+1))*m)) ? mmax : mm;
			} else { // type 2 SS
				if (sct>fct && Math.abs(factor-fact0)<ftol) { // try SS-2
					lambda = factor / (1 - factor);
					sct = -1;
				} else
					lambda = factor; // do something
			}
			lambda = (lambda>lmax) ? lmax : lambda;

			// interpolate new labels
			for (int j=0;j<aimnum;j++) {
				int v = index[j];
				double nsr=R2[v]+lambda*(R2[v]-R1[v]); // new squared s-radius
				setTriRadius(v,1-nsr); // store as x-radius
			}
			sct++;
			fact0 = factor;

			// end of superstep

			// do step/check superstep
			accumErr2 = 0;
			for (int j=0;j<aimnum;j++) {
				int v = index[j];

				double faim = p.vData[v].aim; // get target sum
				double x_rad=getTriRadius(v);
				double r=1.0-x_rad;  // routines use squared s-radius
				if (r >=1.0)
					r = 0;
				double sr = Math.sqrt(r);
				double fbest=compTriCurv(v,x_rad);
				

				// set up for model
		        int N = 2*p.vData[v].num;
				double del = Math.sin(faim/N);
				double bet = Math.sin(fbest/N);
				double r2 = (bet - sr) / (bet * r - sr); // reference radius
				if (r2 > 0) { // calc new label
					double t1 = 1 - r2;
					double t2 = 2 * del;
					double t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
					r2 = t3 * t3;
				} else
					r2 = del * del; // use lower limit
				setTriRadius(v,1.0-r2); // store new x-radius
				p.vData[v].curv = fbest; // store new anglesum
				fbest -=faim;
				accumErr2 += fbest * fbest; // accumulate error
			}
			accumErr2 = Math.sqrt(accumErr2);

			// check results
			double pred = Math.exp(lambda * Math.log(factor)); // predicted improvement
			double act = accumErr2 / c1; // actual improvement
			if (act < 1) { // did some good
				if (act > pred) { // not as good as expected: reset
					m = 1;
					sct = 0;
					if (key == 1)
						key = 2;
				} // implied else: accept result
			} else { // reset to before superstep
				m = 1;
				sct = 0;
				for (int j = 0; j < aimnum; j++) {
					int v=index[j];
					setTriRadius(v,1.0-R2[v]);  // store as x-radius
				}

				accumErr2 = c1;
				if (key == 2)
					key = 1;
			}

			// show activity
			if ((localPasses % 10) == 0)
				repack_activity_msg();

			localPasses++;
		} // end of main while loop

		totalPasses += localPasses;
		return RIFFLE;
	}
	
	/**
	 * Generic call; computes both radii and centers (use 'repack' if
	 * you want centers only). Depending on 'useSparseC', use Java or Orick's  
	 * method, which by its nature computes radii and centers in unison. 
	 * @param cycles, int, limit on recompute cycles; no effect in Orick's method
	 * @return int; may be number of cycles used.
	 */
	public int maxPack(int cycles) {
		int count=0;
		if (!useSparseC) {
			try {
				count=genericRePack(cycles);
				if (count!=0)
					reapResults();
				p.fillcurves();
				p.packDCEL.layoutPacking();
			} catch (Exception ex) {
				throw new PackingException("error in Java DCEL repack computation"); 
			}
		}
		else { // use GOpack
			count=maxPackC();
			// normalize to put alpha at the origin, gamma on imaginary axis.
			p.center_point(p.getCenter(p.getAlpha()));
			p.rotate((-1.0)*p.getCenter(p.getGamma()).arg()+Math.PI/2.0);
		}
		return count;
	}
	
	/**
	 * Call to Orick's code in GOpacker using 'SolverFunction' C code.
	 * For maximal packing in disc computes centers and radii in concert. 
	 * @return 1 on success, exceptions thrown on error
	 */
	public int maxPackC() {
		GOpacker goPack=new GOpacker(p);
		goPack.setMode(1); // max pack mode
		goPack.startRiffle();
		int cnt=goPack.continueRiffle(30);
		goPack=null;
		
		p.fillcurves();
		return cnt;
	}
	
	/**
     * Original repack algorithm. Used, e.g., with 
     * overlap packings, where the more sophisticated Java routines and
     * C methods of Orick may fail.
     * This manipulates radii in 'pdcel.triData' structure, so user must 
     * call 'load' first and then 'reapResults' after. 
     * @param passes int (may be old meaning, hence too small)
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
    			  rr=h_RadCalc(v,r,p.getAim(v),N);
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
    public double h_RadCalc(int v,double r, double aim,int N) {
    	double lower=0.5;
  	  	double upper=0.5;
  	  	double factor=0.5;
  	  	double upcurv;
  	  	double lowcurv;
  	  	
  	  	// compute initial curvature
  	  	double curv=compTriCurv(v,r); 
  	    double bestcurv=lowcurv=upcurv=curv;
  	    
  	    // may hit upper/lower bounds on radius change
  	    if (bestcurv>(aim+RP_OKERR)) {
  	    	lower=1.0-factor+r*factor; // interpolate 
  	    	lowcurv=compTriCurv(v,lower);
    	    if (lowcurv>aim) { 
    	    	return lower;
    	    }
    	}
    	else if (bestcurv<(aim-RP_OKERR)) {
    		upper=r*factor;
    	    upcurv=compTriCurv(v,upper);
  	      	if (upcurv<aim) { 
  	      		return upper;
  	      	}
    	}
  	    else 
  	    	return r;
  	    
  	    // iterative secand method
  	    for (int n=1;n<=N;n++) {
  	    	if (bestcurv>(aim+RP_OKERR)) {
  	    		upper=r;
  	    		upcurv=bestcurv;
  	    		r -= (bestcurv-aim)*(lower-r)/(lowcurv-bestcurv);
  	    	}
  	    	else if (bestcurv<(aim-RP_OKERR)) {
  	    		lower=r;
  	    		lowcurv=bestcurv;
  	    		r += (aim-bestcurv)*(upper-r)/(upcurv-bestcurv);
  	    	}
  	    	else 
	    	  return r;
  	    	
  	    	bestcurv=compTriCurv(v,r);
	    }
  	    
  	    return r;
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
    public static double []hypPerron(PackData p,int direction,int passes) {
    	double []results=new double[4];
    	results[0]=results[1]=results[2]=results[3]=FAILURE;
        double dirSign=-1.0;
        if (direction>=0) dirSign=1.0;
        boolean both=false;
        if (direction==2 || direction==-2 || direction==0)
        	both=true;
    	int count=0;
    	
    	// Note: for uniform neighbor computations use (s-radius)^2 
    	double []rad=new double[p.nodeCount+1];
    	for (int k=1;k<=p.nodeCount;k++) 
    		rad[k]=(p.getRadius(k)<0) ? 0.0: 1.0-p.getRadius(k);
    	
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
        // compute discrepency from being super/subpacking 
    	double discrepency=0.0;
        double fbest=0.0;
        for (int j=0;j<aimNum;j++) {
        	int v=inDex[j];
        	double vaim=p.getAim(v);
        	double r=rad[v];

        	fbest=0.0;
        	int[] flower=p.packDCEL.vertices[v].getFlower(true);
        	int num=flower.length-1;
        	double r2 = rad[flower[0]];
        	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
        	for (int k = 1; k <= num; k++) { // loop through petals
				double r3 = rad[flower[k]];
				double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
				fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
				m2 = m3;
			}
			p.setCurv(v,fbest);
			double diff2=dirSign*(fbest-vaim);
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
            	double vaim=p.getAim(v);
            	double r=rad[v];
            	
            	// update anglesum for initial r (nghb's may have changed)
               	fbest=0.0;
               	int[] flower=p.packDCEL.vertices[v].getFlower(true);
               	int num=flower.length;
            	double r2 = rad[flower[0]];
            	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
            	for (int k = 1; k <= num; k++) { // loop through petals
    				double r3 = rad[flower[k]];
    				double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
    				fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
    				m2 = m3;
    			}
    			p.setCurv(v,fbest);
    			
    			// sub/superpacking at v? want to adjust radius
    			double diff2=dirSign*(fbest-vaim);
    			if (diff2>RP_TOLER) { 
    				
    				// get current anglesum at v
    				fbest=0.0;
    				r2 = rad[flower[0]];
    				m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
    				for (int k = 1; k <= num; k++) { // loop through petals
    					double r3 = rad[flower[k]];
    					double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
    					fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
    					m2 = m3;
    				}

    				// set up for uniform neighbor model
    				double denom = 1.0 / (2.0 * ((double)num));
    				double del = Math.sin(vaim * denom);
    				double bet = Math.sin(fbest * denom);
                	double sr=Math.sqrt(r);
    				r2 = (bet - sr) / (bet * r - sr); // reference radius
    				if (r2 > 0) { // calc new label
    					double t1 = 1.0 - r2;
    					double t2 = 2.0 * del;
    					double t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
    					r2 = t3 * t3;
    				} else
    					r2 = del * del; // use lower limit
    				rad[v] = r2; // store new label
    				count++;
    				
    				// update the anglesum for new r
    				fbest=0.0;
    				r2 = rad[flower[0]];
    				m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
    				for (int k = 1; k <= num; k++) { // loop through petals
    					double r3 = rad[flower[k]];
    					double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
    					fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
    					m2 = m3;
    				}
        			p.setCurv(v,fbest);
    			
        			// get squared discrepency
        			diff2=dirSign*(fbest-vaim);
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
        		CirclePack.cpb.errMsg("failed to get a superpacking in hypPerron");
        	else
        		CirclePack.cpb.errMsg("failed to get a subpacking in hypPerron");
        }

        // to prepare for stage 2 or return, compute accumulated error
        double accum=0.0;
        discrepency=0.0;
        for (int j=0;j<aimNum;j++) {
        	int v=inDex[j];
        	double vaim=p.getAim(v);
        	double r=rad[v];
        	
        	// update anglesum
        	fbest=0.0;
        	int[] flower=p.packDCEL.vertices[v].getFlower(true);
        	int num=flower.length-1;
        	double r2 = rad[flower[0]];
        	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
        	for (int k = 1; k <= num; k++) { // loop through petals
        		double r3 = rad[flower[k]];
        		double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
        		fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
        		m2 = m3;
        	}
        	p.setCurv(v,fbest);

			double diff=fbest-vaim;
			double sqdiff=diff*diff;
			if (diff>0.0)
				discrepency+=sqdiff;
			accum +=sqdiff;
        }
        double sqError=Math.sqrt(accum);

        if (!both) { // return after the first stage
        	results[0]=count;
            results[3]=sqError;
            for (int j=0;j<aimNum;j++) 
            	p.setRadius(inDex[j],1.0-rad[inDex[j]]);
        	return results;
        }
        
        // =============== Second stage (optional) =================
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
        		double vaim=p.getAim(v);
        		double r=rad[v];
        		if (r < 0.0)
        			r = 0.0;

        		// update the anglesum
            	fbest=0.0;
            	int[] flower=p.packDCEL.vertices[v].getFlower(true);
            	int num=flower.length-1;
            	double r2 = rad[flower[0]];
            	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
            	for (int k = 1; k <= num; k++) { // loop through petals
            		double r3 = rad[flower[k]];
            		double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
            		fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
            		m2 = m3;
            	}
            	p.setCurv(v,fbest);
        		double diff2=dirSign*(fbest-vaim);

                // Change radii in one direction only: down if direction>0 or else up: 
        		if (diff2<-(100*RP_TOLER)) {
        			
        			// set up for uniform neighbor model
    				double denom = 1.0 / (2.0 * ((double)num));
    				double del = Math.sin(vaim * denom);
    				double bet = Math.sin(p.getCurv(v) * denom);
                	double sr=Math.sqrt(r);
    				r2 = (bet - sr) / (bet * r - sr); // reference radius
    				if (r2 > 0) { // calc new label
    					double t1 = 1.0 - r2;
    					double t2 = 2.0 * del;
    					double t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
    					r2 = t3 * t3;
    				} else
    					r2 = del * del; // use lower limit
    				rad[v] = r2; // store new label
    				p.setCurv(v,fbest); // store new anglesum
        			count++;
        			
        			// update the anglesum for new r
                  	fbest=0.0;
                	r2 = rad[flower[0]];
                	m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
                	for (int k = 1; k <= num; k++) { // loop through petals
        				double r3 = rad[flower[k]];
        				double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
        				fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
        				m2 = m3;
        			}
        			p.setCurv(v,fbest);
        		}
        		
        		// update diff2 and gather data
    			diff2=dirSign*(p.getCurv(v)-vaim);
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
        	double vaim=p.getCurv(v);
        	double r=p.getRadius(v);

        	fbest=0.0;
        	int[] flower=p.packDCEL.vertices[v].getFlower(true);
        	int num=flower.length-1;
        	double r3 = p.getRadius(flower[0]);
        	for (int k = 1; k <= num; k++) { // loop through petals
				double r2 = r3;
				r3=p.getRadius(flower[k]);
				fbest += Math.acos(HyperbolicMath.h_comp_x_cos(r,r2,r3));
			}
			p.setCurv(v,fbest);
			double diff=fbest-vaim;
			double sqdiff=diff*diff;
			if (diff>0.0)
				discrepency+=sqdiff;
			accum +=sqdiff;
        }
        sqError=Math.sqrt(accum);
        
        results[0]=count;
        if (dirSign>=0) // up/down
        	results[2]=Math.sqrt(discrepency);
        else // down/up
        	results[1]=Math.sqrt(discrepency);
    	results[3]=sqError;
        for (int j=0;j<aimNum;j++) 
        	p.setRadius(inDex[j],1.0-rad[inDex[j]]);
    	return results;
    }
    
    // not yet implemented
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

}
