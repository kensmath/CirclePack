package exceptions;

/**
 * Exceptions relating to the combinatorics of "complexes" behind
 * circle packing.
 */

public class CombException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public CombException() {
		super();
	}
	
	public CombException(String msg) {
		super(msg);
	}
	
}
