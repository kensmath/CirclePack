package exceptions;

/**
 * For exceptions send by Native code using UMFPack libraries
 * @author kens
 *
 */
public class UMFPackException extends Exception {
	
	private static final long 
	serialVersionUID = 1L;

	UMFPackException(String reason)
    {
        super(reason);
    }
}