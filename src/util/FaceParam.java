package util;

import complex.Complex;

/**
 * Utility class for building face chain following an arclength parameterized
 * polygonal path: contains a face index, a point on the path, its associated 
 * arclength parameter, and link for next. Some of links could be pruned
 * (e.g., in some cases when previous and next faces share an edge); if
 * 'firm' is true, then this link should not be pruned --- eg. if it's the
 * initial face in a path from a branched circle. (Generically, 'firm' 
 * should be false.)
 * @author kens
 */
public class FaceParam {
	public int faceIndx;  // face index
	public Complex Z;  // point on path
	public double param;  // associated real parameter value
	public boolean firm;  // if true, then this link should not be pruned
	public FaceParam next;  // pointer to next
}
