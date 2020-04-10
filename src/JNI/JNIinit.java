package JNI;

/** 
 * This initiates calls to C/C++ libraries, updated in 2015, which
 * service calls for random triangulations using 'triangle' and 'qhull'
 * anf for sparse matrix computation using 'umfpack' and related 
 * libraries for implementing Orick's packing algorithm.
 * 
 * Here we try to load the libraries and set flags indicating
 * whether the routines are available. If not, most of CirclePack
 * should work without problem anyway.
 * 
 * @author kstephe2
 *
 */
public class JNIinit {
	
	static boolean SparseConnection=false;  // is SolverFunction available?	
	static boolean DelaunayConnection=false;	// is DelaunayBuild available?

	// Constructor
	public JNIinit() {
		StringBuilder stmsg=new StringBuilder("");
		
		try {
	        boolean inJar=JNIinit.class.getProtectionDomain().getCodeSource()
    				.getLocation().toURI().getPath().endsWith(".jar");

	        if(inJar) {
	        	
	        	try {
	        		NativeLib.loadLibrary("SolverFunction");
	        		SolverFunction.initialize();
	        		SparseConnection=true;
	        		stmsg.append("'SolverFunction' library in place. ");
	        	} catch(Throwable ex) {
	        		System.err.println("failed to load 'SolverFunction' library");
	        	}

	        	try {
	        		NativeLib.loadLibrary("DelaunayBuilder");
	        		DelaunayBuilder.initialize();
	        		stmsg.append("'DelaunayBuilder' library in place. ");
	        		DelaunayConnection=true;
	        	} catch(Throwable ex) {
	        		System.err.println("failed to load 'DelaunayBuilder' library");
	        		ex.printStackTrace();
	        	}

	        }
	        
	        else {
//	        	System.out.printf("java.library.path=%s\n", System.getProperty("java.library.path"));
//	        	System.out.printf("user.dir=%s\n", System.getProperty("user.dir"));
//	        	System.out.printf("LD_LIBRARY_PATH=%s\n", System.getenv("LD_LIBRARY_PATH"));
	        	
//	        	try {
//	        		System.loadLibrary("EDelaunay");
//	        		DelaunayBuilder.initialize();
//	        		DelaunayConnection=true;
//	        		stmsg.append("'EDelaunay' library in place. ");
//	        	} catch(Throwable ex) {
//	        		System.err.println("failed to load 'EDelaunay' library");
//	        		ex.printStackTrace();
//	        	}

/* these are held back until updated	        	
	        	try {
	        		System.loadLibrary("SolverFunction");
	        		SolverFunction.initialize();
	        		SparseConnection=true;
	        		stmsg.append("'SolverFunction' library in place. ");
	        	} catch(Throwable ex) {
	        		System.err.println("failed to load 'SolverFunction' library");
	        		ex.printStackTrace();
	        	}

	        	try {
	        		System.loadLibrary("DelaunayBuilder");
	        		DelaunayBuilder.initialize();
	        		stmsg.append("'DelaunayBuilder' library in place. ");
	        		DelaunayConnection=true;
	        	} catch(Throwable ex) {
	        		System.err.println("failed to load 'DelaunayBuilder' library");
	        		ex.printStackTrace();
	        	}
*/
	        	
	        }
	        stmsg.append("\n");
	        System.out.println(stmsg.toString());
		} catch (Throwable ex) { 
			System.err.println("Failed to load 'SolverFunction' and/or 'DelaunayBuilder' "+
					"shared libararies with error " + ex + "; "+
					" this should not affect mose users of 'CirclePack'");
		}
	}

	/**
	 * Is the 'SolverFunction' library started in the background?
	 * @return boolean
	 */
	public static boolean SparseStatus() {
		return SparseConnection;
	}

	/**
	 * Is the 'DelaunayBuild' library started in the background?
	 * @return boolean
	 */
	public static boolean DelaunayStatus() {
		return DelaunayConnection;
	}

	
}
