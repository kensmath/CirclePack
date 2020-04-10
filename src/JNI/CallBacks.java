package JNI;

import packing.PackLite;
import exceptions.JNIException;

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

	// for temporary 'PackLite' object. 
	public static PackLite tmp_pl;
	
	public static void getTestData(int []ccounts) {
		int feedback1=ccounts[0];
		int feedback2=ccounts[1];
		System.out.println("Got integers "+feedback1+" and "+feedback2+" from CCode2015");
	}
	
	/** This is a C callback, normally responding to some Java call,
	 *    which processes the C data and puts it in tmp_pl result. */
	public static void getLite(int []ccounts,int []cvarIndices,
		  	int []corigIndices,double []cradii,
		  	int []caimIndices,double []caims,
		  	int []cinvDistEdges,double []cinvDistances) {
	
		tmp_pl=new PackLite(null);
		tmp_pl.counts=new int[21]; // as of 10/14, 20 lead integers, indexed from 1 in 'counts'.
		for (int i=1;i<21;i++) tmp_pl.counts[i]=ccounts[i];

		try {
			// have something?
			if (tmp_pl.counts[1]<=0) throw new JNIException("getLite: failed to get any vertices");
		    tmp_pl.varIndices= new int[tmp_pl.counts[5]+1];
		    // varIndices
		    for (int n=1;n<=tmp_pl.counts[5];n++) 
		    	tmp_pl.varIndices[n]=cvarIndices[n];
		    // origIndices
		    tmp_pl.v2parent= new int[tmp_pl.counts[3]+1];
		    for (int n=1;n<=tmp_pl.counts[3];n++) 
		    	tmp_pl.v2parent[n]=corigIndices[n];
		    // radii
		    tmp_pl.radii= new double[tmp_pl.counts[3]+1];
		    if (tmp_pl.counts[2]<0) {// hyperbolic
		    	for (int n=1;n<=tmp_pl.counts[3];n++) { 
		    		double rad=cradii[n];
		    		if (rad>0) tmp_pl.radii[n]=Math.exp(-rad);
		    		else tmp_pl.radii[n]=rad;
		    	}
		    }
		    else { // spherical/euclidean
		    	for (int n=1;n<=tmp_pl.counts[3];n++) { 
		    		tmp_pl.radii[n]=cradii[n];
		    	}
		    }
		    // aimIndices
		    tmp_pl.aimIndices= new int[tmp_pl.counts[7]+1];
		    for (int n=1;n<=tmp_pl.counts[7];n++)  
		    	tmp_pl.aimIndices[n]=caimIndices[n];
		    // aims
		    tmp_pl.aims=new double[tmp_pl.counts[7]+1];
		    for (int n=1;n<=tmp_pl.counts[7];n++)
		    	tmp_pl.aims[n]=caims[n];
		    // invDistEdge
		    tmp_pl.invDistEdges=new int[2*tmp_pl.counts[6]+1];
		    for (int n=1;n<=tmp_pl.counts[8];n++) {
		    	tmp_pl.invDistEdges[2*n-1]=cinvDistEdges[2*n-1];
		    	tmp_pl.invDistEdges[2*n]=cinvDistEdges[2*n];
		    }
		    // invDistances
		    tmp_pl.invDistances=new double[tmp_pl.counts[8]+1];
		    for (int n=1;n<=tmp_pl.counts[8];n++) 
		    	tmp_pl.invDistances[n]=cinvDistances[n];
		} catch (Exception ex) {
			throw new JNIException("getLite: some error in transferring data");
		}

	}
}
	
