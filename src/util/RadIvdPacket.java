package util;

/**
 * Utility class to hold radius and opposite side inversive distance
 * data for a face, irrespective of geometry.
 * @author kstephe2
  */
public class RadIvdPacket {
	public double[] rad;
	public double[] oivd;
	
	// Constructor
	public RadIvdPacket() {
		rad=new double[3];
		oivd=new double[3];
	}
}