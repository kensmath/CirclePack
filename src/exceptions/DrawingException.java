package exceptions;

/**
 * For errors in creating paths, graphic objects, postscript objects, etc.
 * @author kens
 *
 */
public class DrawingException extends Exception {

	private static final long 
	serialVersionUID = 1L;
	
	public DrawingException() {
		super();
	}
	
	public DrawingException(String msg) {
		super(msg);
	}
	
}
