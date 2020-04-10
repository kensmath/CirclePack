package exceptions;

/** 
 * This should be a temporary exception; go back later and set a more
 * appropriate one.
 */
public class MiscException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public MiscException() {
		super();
	}
	
	public MiscException(String msg) {
		super(msg);
	}
	
}
