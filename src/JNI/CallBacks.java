package JNI;

/**
 * This is Java code that is called from the C library 'HeavyC_lib'. The
 * static routines are "registered" once and for all with the running
 * HeavyC library via a call to 'C2JinitIDs' here.
 * 
 * @author kens
 *
 */
public class CallBacks {
	
	/** This "registers" static calling information for C to call the Java
	 * routines here. See 'C_calls.cpp'. */
	public native static void C2JinitIDs();

}
	
