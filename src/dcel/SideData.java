package dcel;

import java.awt.Color;
import java.util.Iterator;

import combinatorics.komplex.RedEdge;
import complex.Complex;
import listManip.HalfLink;
import math.Mobius;
import util.ColorUtil;

/**
 * This is for DCEL structures; compare to 'SideDescription'.
 * A "side" of a complex is a segment of faces in 'redChain'. 
 * A "side-pairing" occurs only when a complex is not simply 
 * connected and then it refers to a pair of connected edge
 * segments, each maximal with the property that their lists 
 * of red edges are identified in the complex. In the 
 * non-simply connected case there may also be "border" 
 * segments (maximal connected but unpaired segments).
 * 
 * A linked list 'PackDCEL.pairLink' of 'SideData's encodes the 
 * side-pairing information. 
 * 
 * Caution: 'mob' element can easily be out of date. Likewise, the 
 * 'center' information on the circles along the red chain are kept
 * in the 'RedEdge's, and these are updated during 'layout'
 * operations. To update 'mob' one generally has to start by updating
 * the 'RedEdge's.
 * @author kens
 */
public class SideData {
	
	public int hes;           // geometry from parent packing
	public int spIndex;       // index in 'PairLink' linked list 
							  //   (indexed from 1)
	public int mateIndex;     // 'spIndex' of paired side (-1 if no mate)
	public RedEdge startEdge; // the first 'RedEdge' of this "side"
	public RedEdge endEdge;   // the final 'RedEdge' of this "side"
	public Mobius mob;        // Mobius transform when side is paired: map
							  //   of paired edge TO this edge 
							  //   (TODO: for spherical) 
	public double mobErr;     // error in the Mobius transform (mainly for hyp
                        	  //   case; mob is always an automorphism, but 
							  //   roundoff can prevent it from providing 
							  //   exact match).
	public Color color;	      // paired segments are color-coded for 
							  //   visual matching
	public String label;	  // 'a', 'b', etc. for paired sides, ('A', 'B', etc) for
							  //   other side. '1', '2', etc. for unpaired sides.
							  //   Default is null. Note: due to Windows treatment
							  //   of case, label 'A' is stored as 'aa', etc.

    // Constructor
    public SideData() {
    	startEdge=null;
    	endEdge=null;
    	mob=new Mobius();
    	mobErr=0.0;
    	spIndex=-1;
    	mateIndex=-1;
    	label=null;
    	color=ColorUtil.getFGColor();
    }
    
    /**
     * Count of edges along this side.
     * @return int
     */
    public int sideCount() {
    	int count=1;
    	RedEdge rtrace=startEdge;
    	do {
    		count++;
    		rtrace=rtrace.nextRed;
    	} while (rtrace!=endEdge);
    	return count;
    }
    
    /**
     * Update side-pair 'mob' based on latest 'center' data in 
     * relevant red edges. Return 0 on error, check 'mobErr' 
     * for accuracy. Note: this mob maps the matched edge TO
     * this edge.
     * TODO: not much error checking. (I think this is old 
     * 'pair_mobius' code.)
     * @return, 0 on error
     */
    public int set_sp_Mobius() {
    	if (mateIndex<0) 
    		return 0; // this is a "border" side
    	Complex A=startEdge.getCenter();
    	Complex a=startEdge.twinRed.nextRed.getCenter();
    	Complex B=endEdge.nextRed.getCenter();
    	Complex b=endEdge.twinRed.getCenter();
    	
    	mob=new Mobius(); // start with identity
    	mobErr=0.0;
	  
    	/* Now have everything for mobius transformation. (The mobius
    	 * for 'pairedEdge' is set separately (should be inverse to this)). */
    	if (hes<0) { // hyp 
    		if (a.abs()>=Mobius.MOD1 && b.abs()>=Mobius.MOD1 
    				&& A.abs()>=Mobius.MOD1 && B.abs()>=Mobius.MOD1) {
    			/* We have another real degree of freedom, so we go to a face
		         * midway along this paired side and match its centers.*/
    			
    			int count=0;
    			RedEdge etrace=startEdge;
    			while (etrace!=endEdge) {
    				count++;
    				etrace=etrace.nextRed;
    			}
    			
    			if (count<2) return 0; // side is too short
    			// move to center edge-pairing and use ends
    			etrace=startEdge;
    			for (int i=0;i<=(int)(count/2);i++)
    				etrace=etrace.nextRed;
    	    	Complex C=etrace.getCenter();
    	    	Complex c=etrace.twinRed.nextRed.getCenter();

    	    	if (c.abs()>=Mobius.MOD1 || C.abs()>=Mobius.MOD1)
    	    		return 0; // didn't get matching centers or one/both these
				 	          // centers were too close to unit circle. 
    	    	
    	    	mob=Mobius.trans_abAB(a,b,A,B,c,C);
    	    	mobErr=mob.error;
    		} 
    		else if (a.abs()>=Mobius.MOD1 || b.abs()>=Mobius.MOD1 
		       || A.abs()>=Mobius.MOD1 || B.abs()>=Mobius.MOD1)
    			return 0; /* fixup: don't have routine to handle some of points 
			               * "on" unit circle and others off. Also,
			     		   * may have too much error for either trans_abAB
			               * or auto_abAB. */
    		else mob=Mobius.auto_abAB(a,b,A,B);
    		mobErr=mob.error;
    	}
    	else if (hes==0) { // eucl 
    		mob=Mobius.mob_abAB(a,b,A,B);
    		mobErr=mob.error;
    	}
    	// TODO: sph not yet implemented, but may be set elsewhere,
    	//    e.g., under projection to the sphere.
    	else return 0; 
    	return 1;
    }
    
    /**
     * Search 'PairLink' to find the 'SideData' for the 
     * "side" of the complex which contains 'RedEdge' redge
     * return null on failure. 
     * @param pairLink PairLink
     * @param redge RedEdge
     * @return 'SideData' of containing side or null on failure
     */
    public static SideData which_side(PairLink pairLink,RedEdge redge) {
    	if (pairLink==null || pairLink.size()==0) return null;
    	Iterator<SideData> pl=pairLink.iterator();
    	SideData ep=null;
    	while (pl.hasNext()) {
    		ep=(SideData)pl.next();
    		RedEdge rdl=ep.startEdge;
    		if (rdl==null) return null;
    		do {
    			if (redge==(RedEdge)rdl) return ep;
    			rdl=rdl.nextRed;
    		} while (rdl!=ep.endEdge);
    	}
    	return null; // didn't find it
    }

    /**
     * Return the index of the vertex that starts this side
     * @return int
     */
    public int sideFirstVert() {
    	return startEdge.myEdge.origin.vertIndx;
    }
    
    /**
     * Return 'HalfLink' with successive 'myEdge's 
     * from 'this' cclw side.
     * @return HalfLink, null on failure
     */
    public HalfLink sideHalfLink() {
    	if (startEdge==null || endEdge==null)
    		return null;
    	HalfLink hlink=new HalfLink();
    	RedEdge rtrace=startEdge;
    	do {
    		hlink.add(rtrace.myEdge);
    		rtrace=rtrace.nextRed;
    	} while(rtrace!=endEdge);
    	hlink.add(rtrace.myEdge);
    	return hlink;
    }

	/**
	 * clone: CAUTION: pointers are likely in conflict or outdated.
	 * @return new SideData
	 */
    public SideData clone() {
    	SideData sd=new SideData();
    	sd.hes=hes;
    	sd.startEdge=startEdge;
    	sd.endEdge=endEdge;
    	sd.label=label;
    	sd.mateIndex=mateIndex;
    	sd.spIndex=spIndex;
    	sd.mob=mob.cloneMe();
    	sd.mobErr=mobErr;
    	sd.color=ColorUtil.cloneMe(color);
    	return sd;
    }
    
}