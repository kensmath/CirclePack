package JNI;

import java.util.function.Function;

/**
 * For passing data between Java and C libraries for building Delaunay
 * triangulations.
 * @author kstephe2 5/2015, thanks to Chris Brumgard
 *
 */
public class DelaunayBuilder implements Function<DelaunayData, DelaunayData>
{
	// call this after loading the 'DelaunayBuilder' library
    public native static void initialize();
    
    public native DelaunayData apply(DelaunayData delaunayData);
}