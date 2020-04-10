package exceptions;

public class MobException extends RuntimeException {

	private static final long 
	serialVersionUID = 1L;

	public MobException() {
		super();
	}
	
	public MobException(String msg) {
		super(msg);
	}
	
}
