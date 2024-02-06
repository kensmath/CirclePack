package util;

/**
 * Utility class to hold radius and edge inversive distance
 * data for a face, irrespective of geometry.
 * 
 * TODO: can probably use TriAspect for this now (2021)
 * 
 * @author kstephe2
  */
public class RadIvdPacket {
	public double[] rad;
	public double[] ivd;
	
	// Constructor
	public RadIvdPacket() {
		rad=new double[3];
		ivd=new double[3];
	}
}
