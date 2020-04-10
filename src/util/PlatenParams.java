package util;

/** 
 * Utility class for holding and manipulating the various parameters
 * associated with 'MicroGrids'.
 * @author kstephe2
 *
 */
public class PlatenParams {

	public Double minR;
	public Double maxR;
	public Double ratioQ;	
	public boolean intensityUp;     // is an intensity method installed?
	public boolean chgTrigger;		// set true if there's been a change requiring platen reset
								// only changed in reset call
		
	// Constructor(s)
	public PlatenParams() {
		minR=Double.valueOf(.1);
		maxR=Double.valueOf(1.0);
		ratioQ=Double.valueOf(1.5);
		intensityUp=false;
		chgTrigger=false;
	}
		
	public PlatenParams(Double mnR,Double mxR,Double ratQ) {
//		super();
		if (mnR!=null)
			minR=Double.valueOf(mnR);
		if (mxR!=null)
			maxR=Double.valueOf(mxR);
		if (ratQ!=null)
			ratioQ=Double.valueOf(ratQ);
	}
		
	/*
	 * set 'minR' (and possibly adjust 'maxR') but with constraints
	 *  * minR>=microRad/200
	 *  * maxR>minR*1.1  (at least 10% difference)
	 *  * maxR<2*minR
	 * @param enc_Rad double, circle enclosing whole region
	 * @param mR double
	 */
	public void set_minR(double enc_Rad,double mR) {
		if (mR<=0) 
			return;
			
		// not too small: TODO
		if (mR<enc_Rad/250.0)
			mR=enc_Rad/250.0;
			
		// is change significant enough to actually do?
		if (Math.abs(minR.doubleValue()-mR)/mR>.01) { 
			minR=Double.valueOf(mR);
			chgTrigger=true;
		}
		else
			return;
			
		// don't let 'maxR' be less than 1.1*minR
		if (maxR<1.1*minR) {
			maxR=1.1*minR;
			chgTrigger=true;
		}
	}

	/**
	 * should 'set_minR' first, then 'maxR' should have
	 * some constraints: maxR>1.1*minR
	 * @param mxR
	 */
	public void set_maxR(double mxR) {
		if (mxR<=0) 
			return;
			if (mxR<1.1*minR) {
				mxR=1.1*minR;
			}

		// is change significant enough to actually do?
		if (Math.abs(maxR.doubleValue()-mxR)/mxR>.01) { 
			maxR=Double.valueOf(mxR);
			chgTrigger=true;
		}
		else return;
	}

	public void set_Q(double Q) {
		if (Q<.05)
			Q=.05;
		if (Q>2.0)
			Q=2.0;
		if (ratioQ!=null && Math.abs(ratioQ.doubleValue()-Q)/Q<.01)
			return;   // didn't really change enough
		ratioQ=Double.valueOf(Q);
		chgTrigger=true;
	}
		
	public boolean get_trigger() {
		return chgTrigger;
	}
		
	public void set_trigger(boolean trig) {
		chgTrigger=trig;
	}
		
	public double get_minR() {
		return (double)minR;
	}
		
	public double get_maxR() {
		return (double)maxR;
	}
		
	public double get_Q() {
		return (double)ratioQ;
	}

}