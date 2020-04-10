package exceptions;

/**
 * Exceptions caused by errors in command string formats.
 */

public class ParserException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;

	public ParserException() {
		super();
	}
	
	public ParserException(String msg) {
		super(msg);
	}
	
}
