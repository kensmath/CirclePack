package rePack;

import java.util.Iterator;

import JNI.JNIinit;
import allMains.CirclePack;
import complex.Complex;
import dcel.Face;
import dcel.HalfEdge;
import exceptions.PackingException;
import geometry.CircleSimple;
import geometry.EuclMath;
import geometry.NSpole;
import geometry.SphericalMath;
import komplex.EdgeSimple;
import math.Mobius;
import packing.PackData;
import util.TriData;

/**
 * Spherical circle packing computations in DCEL setting. Currently
 * this is only for maximal packing of a sphere. Effectively we
 * "puncture out" the last face in drawing order, Set radii to 1 for 
 * its three vertices, do a eucl repacking and layout, project to 
 * sphere, then finish with NSPole normalization. 
 * 
 * @author kens 1/2021
 *
 */
public class d_SphPacker extends RePacker {
	
    public static final int SPH_GOPACK_THRESHOLD=501;  // for smaller packs, default to Java
    public static final double MPI2=2.0*Math.PI;

	// Constructor
	public d_SphPacker(PackData pd,int pass_limit) { // pass_limit suggests using Java methods
    	p=pd;
    	pdcel=p.packDCEL;
    	if (pass_limit<0) passLimit=PASSLIMIT;
		else passLimit=pass_limit;
		status=load(); 
		if (status!=LOADED) 
			throw new PackingException("'d_SphPacker' failed to load");
		totalPasses=0;
		localPasses=0;
		R1=new double[p.nodeCount+1];
		R2=new double[p.nodeCount+1];
	}

	/**
	 * Abstract methods not yet used here.
	 */
	public int reStartRiffle(int passNum) {return 1;}
	public double l2quality(double crit) {return 1;}
	
	public void setSparseC(boolean useC) {
		useSparseC=false;
		if (useC) { // requested to use GOpacker routines if possible
			if (p.nodeCount<SPH_GOPACK_THRESHOLD) { // for smaller packing, use Java
				useSparseC=false;
				return;
			}
			if (JNIinit.SparseStatus())
				useSparseC=true;
		}
		return;
	}

	/**
	 * Load 'TriData' with euclidean data after choosing
	 * a face to remove    
	 * @return int, cycle count.
	 */
	public int load() {
		
		// find face to remove: want one in 'faceOrder', else the
		//    last face in 'faceOrder' (so rest of it remains valid).
//		int[] fhits=new int[pdcel.faceCount+1];
//		Iterator<Integer> fis=pdcel.faceO
		
		int lastface=pdcel.faceOrder.getLast().w;
		Face outface=pdcel.faces[lastface];
		int[] bdryVerts=outface.getVerts();
		
		// set up 'TriData
		pdcel.triData=null;
		if (triDataLoad()==0) {
			throw new PackingException("failed to load 'TriData' in SphPacker");
		}
		for (int j=1;j<=pdcel.faceCount;j++) {
			TriData td=pdcel.triData[j];
			td.hes=0; // treat as euclidean
			for (int k=0;k<3;k++) 
				td.radii[k]=.05; // TODO: might choose better initialization
		}
		TriData outer=pdcel.triData[lastface];
		for (int k=0;k<3;k++) 
			setTriRadius(bdryVerts[k],1.0);
		aimnum=pdcel.vertCount-3;
		
		// identify the radii that can be adjusted
		index=new int[pdcel.vertCount+1];
		int tick=-1;
		for (int v=1;v<=pdcel.vertCount;v++) {
			if (v!=bdryVerts[0] && v!=bdryVerts[1] && v!=bdryVerts[2])
				index[++tick]=v;
		}
		return 1;
	}
	
	public void reapResults() {
		boolean debug=false;
		Complex[] z=new Complex[pdcel.vertCount+1];
		double[] radii=new double[pdcel.vertCount+1];
		
		// gather radii (held only in 'triDAta')
		for (int v=1;v<=pdcel.vertCount;v++) {
			radii[v]=pdcel.triData[p.vData[v].findices[0]].radii[p.vData[v].myIndices[0]];
		}
		
		// find and lay out first face
		Iterator<EdgeSimple> cis=pdcel.computeOrder.iterator();
		EdgeSimple edge=cis.next();
		if (edge.v!=0) {
			throw new PackingException("In sph packing; not root in 'computeOrder'");
		}
		Face firstface=pdcel.faces[edge.w];
		HalfEdge he=firstface.edge;
		int alph=he.origin.vertIndx;
		int v1=he.next.origin.vertIndx;
		int v2=he.next.next.origin.vertIndx;
		z[alph]=new Complex(0.0);
		double dist=EuclMath.e_ivd_length(radii[alph], radii[v1], he.getInvDist());
		z[v1]=new Complex(dist,0.0);
		CircleSimple cS=EuclMath.e_compcenter(z[alph],z[v1],
				radii[alph],radii[v1],radii[v2],
				he.getInvDist(),he.next.getInvDist(),he.next.next.getInvDist());
		z[v2]=cS.center;

		// debug=true;
		if (debug) { 
			deBugging.DCELdebug.drawEuclCircles(CirclePack.cpb.cpScreens[2], z, radii);
		}

		// lay out the rest of the eucl circles
		while(cis.hasNext()) {
			int w=cis.next().w;
			he=pdcel.faces[w].edge;
			int v0=he.origin.vertIndx;
			v1=he.next.origin.vertIndx;
			v2=he.next.next.origin.vertIndx;
			cS=EuclMath.e_compcenter(z[v0],z[v1],
					radii[v0],radii[v1],radii[v2],
					he.getInvDist(),he.next.getInvDist(),he.next.next.getInvDist());
			z[v2]=cS.center;
		}
		
		// normalization: reaching here, the euclidean packing and
		//    layout should be done. Use iterative routine to find 
		//    Mobius putting centroid of spherical centers near the 
		//    origin in 3D. 
		
		Complex[] pts=new Complex[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) 
			pts[v]=new Complex(z[v]); 

		// compute and apply the mobius
		Mobius mob=NSpole.sphNormalizer(pts,20,false,false);
		if (mob==null) 
			throw new PackingException("sph decel case: failed to get mobius");

		// this is still euclidean (since mobius is linear)
		double factor=mob.a.divide(mob.d).abs();
		for (int v=1;v<=p.nodeCount;v++) {
			radii[v] *=factor;
			z[v]=mob.apply(z[v]);
		}
		
		// debug=true;
		if (debug) { 
			deBugging.DCELdebug.drawEuclCircles(CirclePack.cpb.cpScreens[2], z, radii);
		}
		
		// find normalizing mobius
		int gam=p.packDCEL.alpha.next.origin.vertIndx;
		if (p.packDCEL.gamma!=null)
			gam=p.packDCEL.gamma.origin.vertIndx;
		Mobius rotMob=Mobius.rigidAlphaGamma(z[alph],z[gam]);

		// convert to spherical and save
		boolean oriented=true;
		for (int v=1;v<=pdcel.vertCount;v++) {
			// convert to sphere
			cS=SphericalMath.e_to_s_data(z[v],radii[v]);
			// apply normalizing
		    Mobius.mobius_of_circle(rotMob,1,cS.center,cS.rad,cS,oriented);
			p.vData[v].center=cS.center;
			p.vData[v].rad=cS.rad;
			p.vData[v].curv=MPI2;
		}
	}
	
	public int maxPack(int pass_limit) throws PackingException {
		passLimit=pass_limit; 
	
		if (status!=LOADED && status!=RIFFLE) {
			CirclePack.cpb.myErrorMsg("genericRePack: not in prepared status");
			return 0;
		}
		if (status==LOADED) localPasses=startRiffle();
		else localPasses=0;
		totalPasses += localPasses;
		if (continueRiffle(pass_limit)!=0) {  // successful?
			return totalPasses;
		}
		return 0;
	}
	
	/** 
	 * This  and 'continueRiffle' were stolen from EuclPacker with 
	 * some adjustments since all aims are 2pi.
	 */
	public int startRiffle() throws PackingException { // initiate packing computation

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
			for (int j = 0; j < aimnum; j++) {
				int v = index[j];
				double faim = MPI2;
				double r = getTriRadius(v); // get present label

				// compute anglesum (using local data)
				double fbest = compTriCurv(v, r);

				// use the model to predict the next value
				int N = 2 * p.vData[v].num;
				double del = Math.sin(faim / N);
				double bet = Math.sin(fbest / N);
				double r2 = r * bet * (1 - del) / (del * (1 - bet));
				// store as new radius label
				if (r2 < 0)
					throw new PackingException();
				setTriRadius(v, r2);
				fbest -= faim;
				accumErr2 += fbest * fbest; // accum abs error
			}
			accumErr2 = Math.sqrt(accumErr2);

		} catch (Exception ex) {
			status = FAILURE;
			throw new PackingException();
		}
		status = RIFFLE;
		totalPasses = localPasses = 1;
		return RIFFLE;
	}
		    
	/**
	 * Continue riffling if status is RIFFLE; reset 'passLimit'.
	 * (We don't change 'totalPasses'.)
	 * @param passL int
	 */
	public int continueRiffle(int passL) throws PackingException {
		double fbest;
		double faim;
		double c1;

		if (status != RIFFLE)
			throw new PackingException();
		localPasses = 0;
		passLimit = passL;

		// Begin Main Loop
		while ((accumErr2 > ttoler && localPasses < passLimit)) {

			for (int j = 0; j < aimnum; j++) 
				R1[index[j]] = getTriRadius(index[j]);

			int numBadCuts = 0;
			double factor = 0.0;
			do { // Make sure factor < 1.0
				c1 = 0.0;
				for (int j = 0; j < aimnum; j++) {
					int v = index[j]; // point to active node
					faim = MPI2;
					double ra = getTriRadius(v); // get present label

					// compute anglesum inline (using local data)
					fbest = compTriCurv(v, ra);

					// use the model to predict the next value
					int N = 2 * p.vData[v].num;
					double del = Math.sin(faim / N);
					double bet = Math.sin(fbest / N);
					double r2 = ra * bet * (1 - del) / (del * (1 - bet));
					// store as new radius label
					if (r2 < 0)
						throw new PackingException();
					setTriRadius(v, r2);
					fbest -= faim;
					c1 += fbest * fbest; // accum abs error
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
				maxBadCuts = (numBadCuts > maxBadCuts) ? numBadCuts : maxBadCuts;
				minBadCuts = (numBadCuts < minBadCuts) ? numBadCuts : minBadCuts;
				sumBadCuts += numBadCuts;
			}
			cntBadCuts++;

			// ================= superstep calculation ====================

			// new values
			for (int j = 0; j < aimnum; j++)
				R2[index[j]] = getTriRadius(index[j]);

			// find maximum step one can safely take
			double lmax = 10000;
			double fact0 = 0.0;
			for (int j = 0; j < aimnum; j++) { // find max step
				int v = index[j];
				double rat = R2[v] - R1[v];
				double tr = 0.0;
				if (rat < 0)
					lmax = (lmax < (tr = (-R2[v] / rat))) ? lmax : tr; // to keep R>0
			}
			lmax = lmax / 2;

			// do super step
			double lambda = 0.0;
			if (key == 1) { // type 1 SS
				lambda = m * factor;
				double mmax = 0.75 / (1 - factor); // upper limit on m
				double mm = 0.0;
				m = (mmax < (mm = (1 + 0.8 / (sct + 1)) * m)) ? mmax : mm;
			} else { // type 2 SS
				fact0 = 0.0;
				double ftol = 0.0;
				if (sct > fct && Math.abs(factor - fact0) < ftol) { // try SS-2
					lambda = factor / (1 - factor);
					sct = -1;
				} else
					lambda = factor; // do something
			}
			lambda = (lambda > lmax) ? lmax : lambda;

			// interpolate new labels
			for (int j = 0; j < aimnum; j++) {
				int v = index[j];
				double nwr = R2[v] + lambda * (R2[v] - R1[v]);
				if (nwr < 0)
					throw new PackingException("negative rad at " + v);
				setTriRadius(v, nwr);
			}
			sct++;
			fact0 = factor;

			// end of superstep

			// do step/check superstep
			accumErr2 = 0;
			for (int j = 0; j < aimnum; j++) {
				int v = index[j];

				faim = MPI2;
				double rc = getTriRadius(v); // get present label

				// compute anglesum inline (using local data)
				fbest = compTriCurv(v, rc);

				// use the model to predict the next value
				int N = 2 * p.vData[v].num;
				// set up for model

				double del = Math.sin(faim / N);
				double bet = Math.sin(fbest / N);

				double r2 = rc * bet * (1 - del) / (del * (1 - bet));
				// store as new radius label
				if (r2 < 0)
					throw new PackingException();
				setTriRadius(v, r2);
				fbest -= faim;
				accumErr2 += fbest * fbest; /* accum abs error */
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
				for (int j = 0; j <aimnum; j++) {
					int v=index[j];
					setTriRadius(v, R2[v]);
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

		reapResults();
		totalPasses += localPasses;
		return RIFFLE;
	}

    /**
     * Modified from euclidean version. This is currently not used.
     * DCEL version of original repack algorithm implemented in Java. 
     * Accommodates inversive distances, where other methods may fail.
     * This manipulates radii in 'pdcel.triData' structure, so user must 
     * call 'load' first and then 'reapResults' after. 
     * @param passes int 
     * @return int count, -1 on error
     */
	public int d_oldReliable(int passes) {
		int count = 0;
		double accum = 0.0;
		int N = 5; // iterations in each radius comp

		double recip = .333333 / aimnum;
		double cut = accum * recip;

		while ((cut > RP_TOLER && count < passes)) {
			for (int j = 0; j < aimnum; j++) {
				int v = index[j];
				double r = getTriRadius(v);

				if (Math.abs(compTriCurv(v, r) - MPI2) > cut) {
					double rr = e_RadCalc(v, r, MPI2, N);
					setTriRadius(v, rr);
				}
			}
			accum = 0;
			for (int j = 0; j < aimnum; j++) {
				int v = index[j];
				double err = compTriCurv(v, getTriRadius(v)) - MPI2;
				accum += (err < 0) ? (-err) : err;
			}
			cut = accum * recip;

			// show activity
			if ((count % 10) == 0)
				repack_activity_msg();

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
	public double e_RadCalc(int v, double r, double aim, int N) {
		double lower = 0.5;
		double upper = 0.5;
		double factor = 0.5;
		double upcurv;
		double lowcurv;

		// compute initial curvature
		double curv = compTriCurv(v, r);
		double bestcurv = lowcurv = upcurv = curv;

		// may hit upper/lower bounds on radius
		if (bestcurv > (aim + RP_OKERR)) {
			upper = r / factor;
			upcurv = compTriCurv(v, upper);
			if (upcurv > aim) { // return max possible
				return upper;
			}
		} else if (bestcurv < (aim - RP_OKERR)) {
			lower = r * factor;
			lowcurv = compTriCurv(v, lower);
			if (lowcurv < aim) { // return min possible
				return lower;
			}
		}

		// iterative secand method
		for (int n = 1; n <= N; n++) {
			if (bestcurv > (aim + RP_OKERR)) {
				lower = r;
				lowcurv = bestcurv;
				r += (aim - bestcurv) * (upper - r) / (upcurv - bestcurv);
			} else if (bestcurv < (aim - RP_OKERR)) {
				upper = r;
				upcurv = bestcurv;
				r -= (bestcurv - aim) * (lower - r) / (lowcurv - bestcurv);
			} else
				return r;

			bestcurv = compTriCurv(v, r);
		}

		return r;
	}
	
}
