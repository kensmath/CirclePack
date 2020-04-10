package exceptions;

/**
 * Exceptions relating to the native calls to 'HeavyC' library.
 */

public class JNIException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public JNIException() {
		super();
	}
	
	public JNIException(String msg) {
		super(msg);
	}
	
}
