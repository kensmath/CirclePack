package exceptions;

/**
 * Exceptions for actions involving open/closing, writing/reading from 
 * various files; often thrown after an IOException is caught.
 * @author kens
  */
public class InOutException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public InOutException() {
		super();
	}
	
	public InOutException(String msg) {
		super(msg);
	}
	
}
