package exceptions;

/**
 * These are exceptions in computed data: radii, centers, curvatures, aims, etc.
 * as when dividing by zeros, infinite value, distances (eg. might run into 
 * invalid data from a center or radius), quantity that comes out <= 0 when
 * it shouldn't, etc.
 * @author kens
 *
 */
public class DataException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;

	public DataException() {
		super();
	}
	
	public DataException(String msg) {
		super(msg);
	}
	
}
