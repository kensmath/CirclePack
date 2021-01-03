package rePack;

import input.CommandStrParser;
import komplex.KData;
import packing.PackData;
import packing.RData;
import util.UtilPacket;
import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import exceptions.PackingException;
import geometry.HyperbolicMath;

public class HypPacker extends RePacker {
	
    public static final double AIM_THRESHOLD=.0001;  // aim less than this, treat as horocycle
    public static final int HYP_GOPACK_THRESHOLD=501;  // for smaller packs, default to Java

    // Constructors
    public HypPacker(PackData pd,int pass_limit) { // pass_limit suggests using Java methods
    	super(pd,pass_limit,false);
    }
    
    public HypPacker(PackData pd,boolean useC) {
    	super(pd,CPBase.RIFFLE_COUNT,useC);
    }
    
    public HypPacker(PackData pd) {
    	super(pd,CPBase.RIFFLE_COUNT,true);
    }
    
    /**
     * set 'useSparseC' flag to use GOpacker routines when appropriate
     */
	public void setSparseC(boolean useC) {
		useSparseC=false;
		if (useC) { // request to use GOpacker routines if possible
			if (p.nodeCount<HYP_GOPACK_THRESHOLD) { // for smaller packing, use Java
				useSparseC=false;
				return;
			}
			// else use GOpacker routines if library is loaded
			if (JNIinit.SparseStatus())
					useSparseC=true;
			return;
		}
		return;
	}

    /**
     * Load relevant data: NOTE!! that x-radii are converted to 
     * (s-radii)^2 for computations (converted back in 'reapRadii').
     * @return int - LOADED or FAILURE
     */
	public int load() {
		if (super.genericLoad() < 0)
			return FAILURE;
		aimnum = 0;
		index = new int[p.nodeCount + 1];
		for (int i = 1; i <= p.nodeCount; i++) {
			if (p.isBdry(i) && rdata[i].aim >= 0
					&& rdata[i].aim < AIM_THRESHOLD)
				rdata[i].rad = (-.2); // treat interior as horocycle (e.g.,
										// 'cusp')
			else if (rdata[i].aim > 0) {
				if (rdata[i].rad <= 0 && rdata[i].aim > AIM_THRESHOLD)
					rdata[i].rad = .01; // default starting value
				index[aimnum] = i;
				aimnum++;
			}
			if (rdata[i].rad > 0) // convert all x-radius to (s-radius)^2
				rdata[i].rad = 1.0 - rdata[i].rad;
		}
		if (aimnum == 0)
			return FAILURE; // nothing to
		return LOADED;
	}
    
    public double l2quality(double crit) {
	// TODO: need quality measurement tools (presumably based purely
	//   on radii and curvature, since centers are not available locally.
	return 1.0;
    }
    
    /**
     * Some computations use (s-radius)^2 in place of x-radius; have to convert back.
     * Note: we assume none of these are horocycles, so s-radius is > 0.
     */
    public void reapResults() {
    	for (int i=0;i<aimnum;i++) {
    		int j=index[i];
    		p.rData[j].rad= 1.0-(rdata[j].rad); // convert back from s^2 to x-radius for storage
    	}
    }
    
    // continue the packing computation
    public int reStartRiffle(int passes) {   
    	return 1;
    }
    
    /** Compute radii of packing to meet angle_sum targets, specified as
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
		int i, j, k, j1, j2, N;
		double r, r1, r2, r3, fbest, faim, del, bet;
		double denom, twor;
		double m2, m3, sr, t1, t2, t3, o1, o2, o3;
		UtilPacket utilPacket = new UtilPacket();

		if (status != LOADED)
			throw new PackingException();

		maxBadCuts = 0;
		minBadCuts = 0;
		sumBadCuts = 0;
		cntBadCuts = 0;

		// set up parameters

		ttoler = 3 * aimnum * RP_TOLER; // adjust tolerance
		key = 1; // initial superstep type
		m = 1; // Type 1 multiplier
		sct = 1; // Type 1 count
		fct = 2; // Type 2 minimum count

		// do one iteration to get started
		accumErr2 = 0;
		try {
			for (j = 0; j < aimnum; j++) {
				fbest = 0;
				i = index[j];
				if ((r = rdata[i].rad) < 0)
					r = 0;
				sr = Math.sqrt(r);
				N = kdata[i].num;
				if (!p.overlapStatus) { // no overlaps
					twor = 2 * r;
					r2 = rdata[kdata[i].flower[0]].rad;
					m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
					for (k = 1; k <= N; k++) { // loop through petals
						r3 = rdata[kdata[i].flower[k]].rad;
						m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
						fbest += Math.acos(1 - twor * m2 * m3); // angle calc
						m2 = m3;
					}
				}

				// with overlaps, use old routine
				else {
					j2 = kdata[i].flower[0];
					if ((r2 = rdata[j2].rad) < 0)
						r2 = (double) 0;
					o2 = kdata[i].overlaps[0];
					for (k = 1; k <= N; k++) {
						r1 = r2;
						o1 = o2;
						j1 = j2;
						j2 = kdata[i].flower[k];
						if ((r2 = rdata[j2].rad) < 0)
							r2 = 0.0;
						o2 = kdata[i].overlaps[k];
						o3 = kdata[j1].overlaps[p.nghb(j1, j2)];
						HyperbolicMath.h_cos_s_overlap(1 - r, 1 - r1, 1 - r2,
								o3, o2, o1, utilPacket);
						fbest += Math.acos(utilPacket.value);
						// TODO: these routines waste time converting back/forth
						// between
						// x-radii and s-radii.
					}
				}

				faim = rdata[i].aim; // get target sum
				// set up for uniform neighbor model
				denom = 1.0 / (2.0 * ((double) N));
				del = Math.sin(faim * denom);
				bet = Math.sin(fbest * denom);
				r2 = (bet - sr) / (bet * r - sr); // reference radius
				if (r2 > 0) { // calc new label
					t1 = 1 - r2;
					t2 = 2 * del;
					t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
					r2 = t3 * t3;
				} else
					r2 = del * del; // use lower limit
				rdata[i].rad = r2; // store new label
				rdata[i].curv = fbest; // store new anglesum
				fbest = fbest - faim;
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
		int N, j1, j2, numBadCuts, v;
		double c1, fbest, faim, m2, m3, twor;
		double r, r1, r2, r3, o1, o2, o3, sr, t1, t2, t3;
		double denom, del, bet, fact0 = 0.0, ftol = 0.0, pred, act;
		double factor, lmax, rat, tr, lambda, mmax, mm;
		if (status != RIFFLE)
			throw new PackingException();
		UtilPacket utilPacket = new UtilPacket();
		localPasses = 0;
		passLimit = passL;

		// Begin Main Loop
		while ((accumErr2 > ttoler && localPasses < passLimit)) {

			for (int i = 1; i <= p.nodeCount; i++)
				R1[i] = rdata[i].rad;

			numBadCuts = 0;
			do { // Make sure factor < 1.0
				c1 = 0.0;
				for (int j = 0; j < aimnum; j++) { // anglesum is computed in
													// line
					fbest = 0;
					v = index[j];
					if ((r = rdata[v].rad) < 0)
						r = 0;
					sr = Math.sqrt(r);
					N = kdata[v].num;
					if (!p.overlapStatus) { // no overlaps
						twor = 2 * r;
						r2 = rdata[kdata[v].flower[0]].rad;
						m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
						for (int k = 1; k <= N; k++) { // loop through petals
							r3 = rdata[kdata[v].flower[k]].rad;
							m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3)
									: (double) 1;
							fbest += Math.acos(1 - twor * m2 * m3); // angle
																	// calc
							m2 = m3;
						}
					} else { // with overlaps, old routine
						j2 = kdata[v].flower[0];
						if ((r2 = rdata[j2].rad) < 0)
							r2 = 0.0;
						o2 = kdata[v].overlaps[0];
						for (int k = 1; k <= N; k++) {
							r1 = r2;
							o1 = o2;
							j1 = j2;
							j2 = kdata[v].flower[k];
							if ((r2 = rdata[j2].rad) < 0)
								r2 = (double) 0;
							o2 = kdata[v].overlaps[k];
							o3 = kdata[j1].overlaps[p.nghb(j1, j2)];
							HyperbolicMath.h_cos_s_overlap(1 - r, 1 - r1,
									1 - r2, o3, o2, o1, utilPacket);
							fbest += Math.acos(utilPacket.value);
						}
					}
					faim = rdata[v].aim; // get target sum
					// set up for model
					denom = 1.0 / (2.0 * ((double) N));
					del = Math.sin(faim * denom);
					bet = Math.sin(fbest * denom);
					r2 = (bet - sr) / (bet * r - sr); /* reference radius */
					if (r2 > 0) { // calc new label
						t1 = 1 - r2;
						t2 = 2 * del;
						t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
						r2 = t3 * t3;
					} else
						r2 = del * del; // use lower limit
					rdata[v].rad = r2; // store new label
					rdata[v].curv = fbest; // store new anglesum
					fbest = fbest - faim;
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

			// superstep calculation

			// save values
			for (int i = 1; i <= p.nodeCount; i++)
				R2[i] = rdata[i].rad;

			// find maximum step one can safely take
			lmax = 10000;
			for (int j = 0; j < aimnum; j++) { // find max step
				v = index[j];
				r = rdata[v].rad;
				rat = r - R1[v];
				if (rat > 0)
					lmax = (lmax < (tr = ((1 - r) / rat))) ? lmax : tr; // to
																		// keep
																		// R<1
				else if (rat < 0)
					lmax = (lmax < (tr = (-r / rat))) ? lmax : tr; // to keep
																	// R>0
			}
			lmax = lmax / 2;

			// do super step
			if (key == 1) { // type 1 SS
				lambda = m * factor;
				mmax = 0.75 / (1 - factor); // upper limit on m
				m = (mmax < (mm = (1 + 0.8 / (sct + 1)) * m)) ? mmax : mm;
			} else { // type 2 SS
				if (sct > fct && Math.abs(factor - fact0) < ftol) { // try SS-2
					lambda = factor / (1 - factor);
					sct = -1;
				} else
					lambda = factor; // do something
			}
			lambda = (lambda > lmax) ? lmax : lambda;

			// interpolate new labels
			for (int j = 0; j < aimnum; j++) {
				v = index[j];
				rdata[v].rad += lambda * (rdata[v].rad - R1[v]);
			}
			sct++;
			fact0 = factor;

			// end of superstep

			// do step/check superstep
			accumErr2 = 0;
			for (int j = 0; j < aimnum; j++) {
				fbest = 0;
				v = index[j];
				if ((r = rdata[v].rad) < 0)
					r = 0;
				sr = Math.sqrt(r);
				N = kdata[v].num;
				if (!p.overlapStatus) { // no overlaps
					twor = 2 * r;
					r2 = rdata[kdata[v].flower[0]].rad;
					m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : 1.0;
					for (int k = 1; k <= N; k++) { // loop through petals
						r3 = rdata[kdata[v].flower[k]].rad;
						m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : 1.0;
						fbest += Math.acos(1 - twor * m2 * m3); /* angle calc */
						m2 = m3;
					}
				} else { // with overlaps, old routine
					j2 = kdata[v].flower[0];
					if ((r2 = rdata[j2].rad) < 0)
						r2 = 0.0;
					o2 = kdata[v].overlaps[0];
					for (int k = 1; k <= N; k++) {
						r1 = r2;
						o1 = o2;
						j1 = j2;
						j2 = kdata[v].flower[k];
						if ((r2 = rdata[j2].rad) < 0)
							r2 = 0.0;
						o2 = kdata[v].overlaps[k];
						o3 = kdata[j1].overlaps[p.nghb(j1, j2)];
						HyperbolicMath.h_cos_s_overlap(1 - r, 1 - r1, 1 - r2,
								o3, o2, o1, utilPacket);
						fbest += Math.acos(utilPacket.value);
					}
				}
				faim = rdata[v].aim; // get target sum
				// set up for model
				denom = 1.0 / (2.0 * ((double) N));
				del = Math.sin(faim * denom);
				bet = Math.sin(fbest * denom);
				r2 = (bet - sr) / (bet * r - sr); // reference radius
				if (r2 > 0) { // calc new label
					t1 = 1 - r2;
					t2 = 2 * del;
					t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
					r2 = t3 * t3;
				} else
					r2 = del * del; // use lower limit
				rdata[v].rad = r2; // store new label
				rdata[v].curv = fbest; // store new anglesum
				fbest = fbest - faim;
				accumErr2 += fbest * fbest; // accumulate error
			}
			accumErr2 = Math.sqrt(accumErr2);

			// check results
			pred = Math.exp(lambda * Math.log(factor)); // predicted improvement
			act = accumErr2 / c1; // actual improvement
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
				for (int i = 1; i <= p.nodeCount; i++)
					rdata[i].rad = R2[i];

				accumErr2 = c1;
				if (key == 2)
					key = 1;
			}

			// show activity
			if ((localPasses % 10) == 0)
				repack_activity_msg();

			localPasses++;
		} // end of main while loop

		reapResults();
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
				p.fillcurves();
				p.comp_pack_centers(false,false,2,CommandStrParser.LAYOUT_THRESHOLD);
			} catch (Exception ex) {
				throw new PackingException("error in Java repack computation"); 
			}
		}
		else { // use GOpack
			count=maxPackC();
			// normalize to put alpha at the origin, gamma on imaginary axis.
			p.center_point(p.getCenter(p.alpha));
			p.rotate((-1.0)*p.getCenter(p.gamma).arg()+Math.PI/2.0);
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
     * Original repack algorithm implemented in Java. Used, e.g., with 
     * overlap packings, where the more sophisticated Java routines and
     * C methods of Orick may fail. Note that this manipulates radii 
     * directly in the packing data, not using local data; in particular, 
     * you don't need to call 'load'.
     * @param pd PackData
     * @param passes int (may be old meaning, hence too small)
     */
    public static int oldReliable(PackData pd,int passes) {
      int v;
      int count = 0;
      double verr,err,r;
      UtilPacket uP=null;
  
      // whom to repack?
      int aimNum = 0;
      int []inDex =new int[pd.nodeCount+1];
      for (int j=1;j<=pd.nodeCount;j++) {
    	  if (pd.getAim(j)>0) {
    		  inDex[aimNum]=j;
    		  aimNum++;
    	  }
      }
      if (aimNum==0) return FAILURE; // nothing to repack
      
      // set cutoff value
      double accum=0.0;
      for (int j=0;j<aimNum;j++) {
    	  v=inDex[j];
    	  err=pd.getCurv(v)-pd.getAim(v);
              accum += (err<0) ? (-err) : err;
      }
      double recip=.333333/aimNum;
      double cut=accum*recip;

      while ((cut > RP_TOLER && count<passes)) {
    	  for (int j=0;j<aimNum;j++) {
    		  v=inDex[j];
    		  r=pd.rData[v].rad;
    		  
    		  uP=new UtilPacket();
    		  if (!pd.h_anglesum_overlap(v,r,uP)) 
    			  return 0;
    		  pd.setCurv(v,uP.value);
    		  verr=pd.getCurv(v)-pd.getAim(v);
    		  if (Math.abs(verr)>cut) {
    			  if (pd.h_radcalc(v,pd.rData[v].rad,pd.getAim(v),5,uP)) {
    				  pd.rData[v].rad=uP.value;	
    				  if (!pd.h_anglesum_overlap(v,r,uP)) 
    					  return 0;
    				  pd.setCurv(v,uP.value);
    			  }
    		  }
          }
          accum=0;
          for (int j=0;j<aimNum;j++) {
        	  v=inDex[j];
        	  err=pd.getCurv(v)-pd.getAim(v);
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
    	KData []kdata=p.kData;
    	RData []rdata=p.rData;
    	int count=0;
    	
    	// Note: for uniform neighbor computations use (s-radius)^2 
    	double []rad=new double[p.nodeCount+1];
    	for (int k=1;k<=p.nodeCount;k++) 
    		rad[k]=(rdata[k].rad<0) ? 0.0: 1.0-rdata[k].rad;
    	
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
        	double r=rad[v];

        	fbest=0.0;
        	double r2 = rad[kdata[v].flower[0]];
        	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
        	for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
				double r3 = rad[kdata[v].flower[k]];
				double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
				fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
				m2 = m3;
			}
			rdata[v].curv=fbest;
			double diff2=dirSign*(rdata[v].curv-rdata[v].aim);
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
            	double r=rad[v];
            	
            	// update anglesum for initial r (nghb's may have changed)
               	fbest=0.0;
            	double r2 = rad[kdata[v].flower[0]];
            	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
            	for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
    				double r3 = rad[kdata[v].flower[k]];
    				double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
    				fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
    				m2 = m3;
    			}
    			rdata[v].curv=fbest;
    			
    			// sub/superpacking at v? want to adjust radius
    			double diff2=dirSign*(rdata[v].curv-rdata[v].aim);
    			if (diff2>RP_TOLER) { 
    				
    				// get current anglesum at v
    				fbest=0.0;
    				r2 = rad[kdata[v].flower[0]];
    				m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
    				for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
    					double r3 = rad[kdata[v].flower[k]];
    					double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
    					fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
    					m2 = m3;
    				}

    				// set up for uniform neighbor model
    				double denom = 1.0 / (2.0 * ((double)kdata[v].num));
    				double del = Math.sin(rdata[v].aim * denom);
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
    				r2 = rad[kdata[v].flower[0]];
    				m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
    				for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
    					double r3 = rad[kdata[v].flower[k]];
    					double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
    					fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
    					m2 = m3;
    				}
        			rdata[v].curv=fbest;
    			
        			// get squared discrepency
        			diff2=dirSign*(rdata[v].curv-rdata[v].aim);
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
        	double r=rad[v];
        	
        	// update anglesum
        	fbest=0.0;
        	double r2 = rad[kdata[v].flower[0]];
        	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
        	for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
        		double r3 = rad[kdata[v].flower[k]];
        		double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
        		fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
        		m2 = m3;
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
            for (int j=0;j<aimNum;j++) 
            	rdata[inDex[j]].rad=1.0-rad[inDex[j]];
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
        		double r=rad[v];
        		if (r < 0.0)
        			r = 0.0;

        		// update the anglesum
            	fbest=0.0;
            	double r2 = rad[kdata[v].flower[0]];
            	double m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
            	for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
            		double r3 = rad[kdata[v].flower[k]];
            		double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
            		fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
            		m2 = m3;
            	}
            	rdata[v].curv=fbest;
        		double diff2=dirSign*(rdata[v].curv-rdata[v].aim);

                // Change radii in one direction only: down if direction>0 or else up: 
        		if (diff2<-(100*RP_TOLER)) {
        			
        			// set up for uniform neighbor model
    				double denom = 1.0 / (2.0 * ((double)kdata[v].num));
    				double del = Math.sin(rdata[v].aim * denom);
    				double bet = Math.sin(rdata[v].curv * denom);
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
    				rdata[v].curv = fbest; // store new anglesum
        			count++;
        			
        			// update the anglesum for new r
                  	fbest=0.0;
                	r2 = rad[kdata[v].flower[0]];
                	m2 = (r2 > 0) ? (1 - r2) / (1 - r * r2) : (double) 1;
                	for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
        				double r3 = rad[kdata[v].flower[k]];
        				double m3 = (r3 > 0) ? (1 - r3) / (1 - r * r3) : (double) 1;
        				fbest += Math.acos(1 - 2.0*r * m2 * m3); // angle calc
        				m2 = m3;
        			}
        			rdata[v].curv=fbest;
        		}
        		
        		// update diff2 and gather data
    			diff2=dirSign*(rdata[v].curv-rdata[v].aim);
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

        	fbest=0.0;
        	double r3 = rdata[kdata[v].flower[0]].rad;
        	for (int k = 1; k <= kdata[v].num; k++) { // loop through petals
				double r2 = r3;
				r3=rdata[kdata[v].flower[k]].rad;
				fbest += Math.acos(HyperbolicMath.h_comp_x_cos(r,r2,r3));
			}
			rdata[v].curv=fbest;
			double diff=rdata[v].curv-rdata[v].aim;
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
        	rdata[inDex[j]].rad=1.0-rad[inDex[j]];
    	return results;
    }
    
}
