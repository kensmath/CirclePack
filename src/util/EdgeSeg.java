package util;

import listManip.VertexMap;
import packing.PackData;

/**
 * Store and maintain information on a chain (segment) of 
 * edges. (Used, e.g., in 'RationalMap.java'.) Just need 
 * the first and last vertices, which should be boundary 
 * vertices, defining the cclw edge segment. Keep number of 
 * edges for compatibility checks. Have routines to use
 * 'VertexMap' to modify the segment indices when vertices 
 * get renumbered.
 * @author kens
 *
 */
public class EdgeSeg {
	
	public int slitNumber; 		// index for this edgeSeg
	public int sheetNumber;		// which sheet this is on
	public int startV;
	public int endV;
	private int length;			// number of edges

	public EdgeSeg(int id,int sV,int eV) {
		slitNumber=id;
		startV=sV;
		endV=eV;
		length=0;
	}
	
	/**
	 * Set the final length. (Done in calling routine because this
	 * requires 'PackData')
	 * @param len
	 */
	public void setLength(int len) {
		length=len;
	}
	
	/** 
	 * Make a copy with given 'sheetNumber'. Generally done when 
	 * a new copy of 'slitPack' is attached to the growing 'domainPack' 
	 * and corresponding edge segments are listed in 'masterESlist'.
	 * @param sheet, desired sheet number
	 * @return EdgeSeg
	 */ 
	public EdgeSeg clone(int sheet) {
		EdgeSeg es=new EdgeSeg(slitNumber,startV,endV);
		es.sheetNumber=sheet;
		es.length=length;
		return es;
	}
	
	/** 
	 * Check if this edge segment seems to be valid for p. If it 
	 * has length, this compares and returns -count if count!=length.
	 * If length not set, this sets it. 
	 * NOTE: <startV,endV> is a CLOCKWISE (negatively oriented) bdry segment.
	 * @param p PackData, to check against
	 * @return count of edges, 0 on error, or -count if count!=length.
	 */
	public int validate(PackData p) {
		int initlength=length;
		if (startV<1 || startV>p.nodeCount || endV<1 || endV>p.nodeCount
				|| !p.isBdry(startV) || !p.isBdry(endV)) {
			return 0;
		}
		int count=1;
		int next=p.getFirstPetal(endV);
		while (next!=startV && count<p.nodeCount) {
			next=p.getFirstPetal(next);
			count++;
		}
		if (count>=p.nodeCount) 
			return 0;
		if (initlength==0) 
			length=count;
		else if (length!=count) 
			return -count;
		return count;
	}
	
	/**
	 * Convert indices using given vertex map.
	 * @param vmap, VertexMap <orig, new>
	 * @return boolean, true if okay, false if problem.
	 */
	public boolean convertIndices(VertexMap vmap) {
		int sV=vmap.findW(startV);
		int eV=vmap.findW(endV);
		if (sV==0 || eV==0) 
			return false;
		startV=sV;
		endV=eV;
		return true;
	}

}
