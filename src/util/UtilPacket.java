package util;

import java.util.Vector;

import complex.Complex;

/**
 * Utility class to carry data to/from subroutines, especially, e.g., the angle
 * sum computations, lists of points, etc.
 * @author kens
 *
 */
public class UtilPacket {
	public int rtnFlag; // typically, int returned by routines, -1 on error or count
	public double value;
	public double errval;
	public Vector<Complex> z_vec;
	public Vector<Integer> int_vec;

	// Constructor
	public UtilPacket() {
		rtnFlag=0;
		value=0.0;
		errval=0.0;
		z_vec=null;
	}
	
	public UtilPacket(int flag,double val,double val2) {
		rtnFlag=flag;
		value=val;
		errval=val2;
		z_vec=null;
	}
}
