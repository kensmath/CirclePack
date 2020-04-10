package exceptions;

/**
 * Exceptions thrown by 'PackExtender's using 'Oops'.
 * Intended to be more transparent for users writing
 * extenders.
 * @author kens
 */
public class ExtenderException extends RuntimeException {
	private static final long 
	serialVersionUID = 1L;
	
	public ExtenderException() {
		super();
	}
	
	public ExtenderException(String msg) {
		super(msg);
	}
}
