package dcel;

import java.awt.Color;
import java.util.Iterator;

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
 * A linked list 'PackDCEL.pairLink' of 'D_SideData's encodes the 
 * side-pairing information. 
 * 
 * Caution: 'mob' element can easily be out of date. Likewise, the 
 * 'center' information on the circles along the red chain are kept
 * in the 'RedList' objects, and these are updated during 'layout'
 * operations. To update 'mob' one generally has to start by updating
 * the 'RedList.center' information.
 * @author kens
 */
public class D_SideData {
	
	public int hes;           // geometry from parent packing
	public int spIndex;       // index in 'D_PairLink' linked list (indexed from 1)
	public int mateIndex;     // 'spIndex' of paired side (-1 if no mate)
	public RedHEdge startEdge; // the first 'RedEdge' of this "side"
	public RedHEdge endEdge;   // the final 'RedEdge' of this "side"
	public Mobius mob;        // Mobius transform when side is paired (TODO: for spherical) 
	public double mobErr;     // error in the Mobius transform (mainly for hyp
                        	  //   case; mob is always an automorphism, but roundoff 
                        	  //   can prevent it from providing exact match).
	public Color color;	      // paired segments are color-coded for visual matching
	public String label;	  // 'a', 'b', etc. for paired sides, ('A', 'B', etc) for
							  //   other side. '1', '2', etc. for unpaired sides.
							  //   Default is null. Note: due to Windows treatment
							  //   of case, label 'A' is stored as 'aa', etc.

    // Constructor
    public D_SideData() {
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
    	RedHEdge rtrace=startEdge;
    	do {
    		count++;
    		rtrace=rtrace.nextRed;
    	} while (rtrace!=endEdge);
    	return count;
    }
    
    /**
     * Update side-pair 'mob' based on latest 'center' data in relevant 
     * 'RedList' faces. Return 0 on error, check 'mobErr' for accuracy
     * TODO: not much error checking. (I think this is old 
     * 'pair_mobius' code.)
     * @return, 0 on error
     */
    public int set_sp_Mobius() {
    	if (mateIndex<0) 
    		return 0; // this is a "border" side
    	Complex A=startEdge.center;
    	Complex a=startEdge.twinRed.nextRed.center;
    	Complex B=endEdge.nextRed.center;
    	Complex b=endEdge.twinRed.center;
    	
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
    			RedHEdge etrace=startEdge;
    			while (etrace!=endEdge) {
    				count++;
    				etrace=etrace.nextRed;
    			}
    			
    			if (count<2) return 0; // side is too short
    			// move to center edge-pairing and use ends
    			etrace=startEdge;
    			for (int i=0;i<=(int)(count/2);i++)
    				etrace=etrace.nextRed;
    	    	Complex C=etrace.center;
    	    	Complex c=etrace.twinRed.nextRed.center;

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
    		mob=Mobius.affine_mob(a,b,A,B);
    		mobErr=mob.error;
    	}
    	// TODO: sph not yet implemented 
    	else return 0; 
    	return 1;
    }
    
    /**
     * Search 'D_PairLink' to find the 'D_SideData' for the 
     * "side" of the complex which contains 'RedHEdge' redge
     * return null on failure. 
     * @param pairLink PairLink
     * @param redface RedList (or may be 'RedEdge')
     * @return 'D_SideData' of containing side or null on failure
     */
    public static D_SideData which_side(D_PairLink pairLink,RedHEdge redge) {
    	if (pairLink==null || pairLink.size()==0) return null;
    	Iterator<D_SideData> pl=pairLink.iterator();
    	D_SideData ep=null;
    	while (pl.hasNext()) {
    		ep=(D_SideData)pl.next();
    		RedHEdge rdl=ep.startEdge;
    		if (rdl==null) return null;
    		do {
    			if (redge==(RedHEdge)rdl) return ep;
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
     * Return 'HalfLink' with the cclw 'myEdge's 
     * from 'this' side.
     * @return HalfLink, null on failure
     */
    public HalfLink sideLink() {
    	if (startEdge==null || endEdge==null)
    		return null;
    	HalfLink hlink=new HalfLink();
    	RedHEdge rtrace=startEdge;
    	do {
    		hlink.add(rtrace.myEdge);
    		rtrace=rtrace.nextRed;
    	} while(rtrace!=endEdge);
    	hlink.add(rtrace.myEdge);
    	return hlink;
    }

	/**
	 * clone: CAUTION: pointers are likely in conflict or outdated.
	 * @return new D_SideData
	 */
    public D_SideData clone() {
    	D_SideData sd=new D_SideData();
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