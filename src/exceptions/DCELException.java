package exceptions;

/**
 * Exceptions relating to the combinatorics of DCEL structures for use
 * with circle packing.
 */

public class DCELException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public DCELException() {
		super();
	}
	
	public DCELException(String msg) {
		super(msg);
	}
	
}
