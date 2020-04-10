package exceptions;

public class PackingException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public PackingException() {
		super();
	}
	
	public PackingException(String msg) {
		super(msg);
	}
	
}
