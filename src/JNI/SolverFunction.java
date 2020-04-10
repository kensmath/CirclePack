package JNI;

import java.util.function.Function;

/**
 * SolverFunction is C code for using sparse matrix algorithms in 
 * repacking computations, see 'GOpacker'. The former C code was
 * in 'HeavyC' libraries, but 'HeavyC.java' was retired 6/2015.
 * @author kstephe2, 5/2015. Thanks to Chris Brumgard
  */
public class SolverFunction implements Function<SolverData, SolverData>
{
	// call this after loading the 'SolverFunction' library
    public native static void initialize();
    
    public native SolverData apply(SolverData solverData);
}