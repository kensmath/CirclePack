package exceptions;

/** 
 * Exception thrown when reading or processing script actions.
 */
public class ScriptException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;
	
	public ScriptException() {
		super();
	}
	
	public ScriptException(String msg) {
		super(msg);
	}
	
}
