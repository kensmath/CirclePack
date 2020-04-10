package exceptions;

/**
 * For catching exceptions when attempting to convert strings
 * to values. Should be caught locally where the parsing is
 * taking place.
 * @author kstephe2
 *
 */
public class VarException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public VarException() {
		super();
	}
	
	public VarException(String msg) {
		super(msg);
	}
	
}
