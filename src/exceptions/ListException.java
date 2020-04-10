package exceptions;

/**
 * Exceptions when processing various lists, e.g., vlist, elist, etc.
 * @author kens
 *
 */
public class ListException extends Exception {

	private static final long 
	serialVersionUID = 1L;

	public ListException() {
		super();
	}
	
	public ListException(String msg) {
		super(msg);
	}
	
}
