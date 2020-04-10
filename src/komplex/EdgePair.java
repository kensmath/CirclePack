package komplex;

import java.awt.Color;
import java.util.Iterator;

import listManip.PairLink;
import math.Mobius;
import packing.PackData;
import panels.CPScreen;

import complex.Complex;

/**
 * A "side" of a complex is a segment of faces in 'redChain'. 
 * A "side-pairing" occurs only when a complex is not simply 
 * connected and then it refers to a pair of segments which are
 * maximal with the property that their lists of red edges are 
 * identified in the complex. In the non-simply connected case 
 * there may also be "border" segments (maximal unpaired segments).
 * 
 * A linked list 'sidePairs' of 'EdgePairs' encodes the side-pairing 
 * information. 
 * 
 * Caution: 'mob' element can easily be out of date. Likewise, the 
 * 'center' information on the circles along the red chain are kept
 * in the 'RedList' objects, and these are updated during 'layout'
 * operations. To update 'mob' one generally has to start by updating
 * the 'RedList.center' information.
 * @author kens
 */
public class EdgePair{
	
	public PackData packData;
	public int spIndex;       // index in 'sidePairs' linked list
	public int mateIndex;     // index of paired side (-1 if no mate)
	public RedEdge startEdge; // the first 'RedEdge' of this "side"
	public RedEdge endEdge;   // the final 'RedEdge' of this "side"
	public EdgePair pairedEdge;  // if side-paired, this is the mate (null if not paired)
	public Mobius mob;        // Mobius transform when side is paired (not yet for spherical) 
	public double mobErr;     // error in the Mobius transform (mainly for hyp
                        	  //   case; mob is always an automorphism, but roundoff 
                        	  //   can prevent it from providing exact match).
	public Color color;	      // paired segments are color-coded for visual matching
	public String label;	  // 'a', 'b', etc. for paired sides, ('A', 'B', etc) for
							  //   other side. '1', '2', etc. for unpaired sides.
							  //   Default is null. Note: due to Windows treatment
							  //   of case, label 'A' is stored as 'aa', etc.

    // Constructor
    public EdgePair(PackData p) {
    	packData=p;
    	startEdge=null;
    	endEdge=null;
    	pairedEdge=null;
    	mob=new Mobius();
    	mobErr=0.0;
    	spIndex=-1;
    	mateIndex=-1;
    	label=null;
    	color=CPScreen.getFGColor();
    }
    
    /**
     * Update side-pair 'mob' based on latest 'center' data in relevant 
     * 'RedList' faces. Return 0 on error, check 'mobErr' for accuracy
     * TODO: not much error checking. (I think this is old 'pair_mobius' code.)
     * @return, 0 on error
     */
    public int set_sp_Mobius() {
    	if (pairedEdge==null) return 0; // this is a "border" side
    	Complex A=RedList.whos_your_daddy(
    			startEdge,startEdge.startIndex).center;
    	Complex a=RedList.whos_your_daddy(
    			startEdge.crossRed,(startEdge.crossRed.startIndex+1)%3).center;
    	Complex B=RedList.whos_your_daddy(
    			endEdge,(endEdge.startIndex+1)%3).center;
    	Complex b=RedList.whos_your_daddy(
    			endEdge.crossRed,endEdge.crossRed.startIndex).center;
    	
    	mob=new Mobius(); // start with identity
    	mobErr=0.0;
	  
    	/* Now have everything for mobius transformation. (The mobius
    	 * for 'pairedEdge' is set separately (should be inverse to this)). */
    	if (packData.hes<0) { // hyp 
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
    	    	Complex C=RedList.whos_your_daddy(
    	    			etrace,etrace.startIndex).center;
    	    	Complex c=RedList.whos_your_daddy(
    	    			etrace.crossRed,(etrace.crossRed.startIndex+1)%3).center;

    	    	if (c.abs()>=Mobius.MOD1 || C.abs()>=Mobius.MOD1)
    	    		return 0; /* didn't get matching centers or one/both these
				 	           * centers were too close to unit circle. */
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
    	else if (packData.hes==0) { // eucl 
    		mob=Mobius.affine_mob(a,b,A,B);
    		mobErr=mob.error;
    	}
    	else return 0; // sph not implemented 
    	return 1;
    }
    
    /**
     * Search 'pairLink' to find the 'EdgePair' for the 
     * "side" of the complex which contains 'RedList' redface;
     * return null on failure. 
     * @param pairLink, 'PairLink'
     * @param redface, 'RedList' (or 'RedEdge')
     * @return 'EdgePair' of containing side or null on failure
     */
    public static EdgePair which_side(PairLink pairLink,RedList redface) {
    	if (pairLink==null || pairLink.size()==0) return null;
    	Iterator<EdgePair> pl=pairLink.iterator();
    	EdgePair ep=null;
    	while (pl.hasNext()) {
    		ep=(EdgePair)pl.next();
    		RedEdge rdl=ep.startEdge;
    		if (rdl==null) return null;
    		do {
    			if (redface==(RedList)rdl) return ep;
    			rdl=rdl.nextRed;
    		} while (rdl!=ep.endEdge);
    	}
    	return null; // didn't find it
    }

    /**
     * Return the vertex that starts this side
     * @return
     */
    public int sideFirstVert() {
    	return startEdge.myFirstVert();
    }

}