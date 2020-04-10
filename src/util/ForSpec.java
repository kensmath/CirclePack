package util;

import allMains.CPBase;

/**
 * Utility class to store "for" loop specifications.
 * @author kens
 *
 */
public class ForSpec {
	
	public String varName; // if loop variable is a 'CirclePack' variable
	public double start; // start of loop
	public double end; // end of loop
	public double delta; // increment of loop
	public int itNum; // number of iterations to get from start to end.

	// Constructor
	public ForSpec() {
		varName=null;
		start=end=delta=0.0;
		itNum=0;
	}
	
	/**
	 * Set (and return) 'itNum' based on data
	 */
	public int setItNum() {
		if (Math.abs(delta)<CPBase.GENERIC_TOLER) { 
			itNum=0;
		}
		else {
			double diff=end-start;
			if (delta<0) 
				diff *=-1.0;
			itNum=(int)Math.floor(diff/delta);
		}
		return itNum;
	}
	
}
