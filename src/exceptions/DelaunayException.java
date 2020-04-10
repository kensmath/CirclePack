package exceptions;

/**
 * For exceptions send by Native code using 'triangle' and 'qhull'
 * to generate Delaunay triangulations.
 * @author kens
 *
 */
public class DelaunayException extends Exception {
	
	private static final long 
	serialVersionUID = 1L;

	DelaunayException(String reason)
    {
        super(reason);
    }
}
