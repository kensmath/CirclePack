package exceptions;

public class RedListException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;

	public RedListException() {
		super();
	}
	
	public RedListException(String msg) {
		super(msg);
	}
	
}
