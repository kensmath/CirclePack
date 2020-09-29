package dcel;

import java.util.Iterator;
import java.util.LinkedList;

import packing.PackData;

/**
 * Linked list of 'SideDescription' objects; these contain data on side-pairings 
 * and on non-pairing border segments for non-simply connected complexes.
 * (Note: formerly, indexing was from 1, but that changed 9/07.) 
 * @author kens
 */
public class D_PairLink extends LinkedList<D_SideData> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public D_PairLink(PackData p) {
		super();
		packData=p;
	}

	/**
	 * Search through 'pairLink' to the index of the 'SideData' which
	 * is a mate to 'sideDes'. Match involves 'startEdge' of one equal
	 * to 'endEdge' of other and vice verse. Return -1 if not found; may
	 * indicate an error.
	 * @param pairLink PairLink, list of side pairings
	 * @param sideDes SideDescription
	 * @return index of mate to sideDes, -1 if none found
	 */
	public static int find_mate(D_PairLink pairLink,D_SideData sideDes) {
		 int indx=0;
		 Iterator<D_SideData> plink=pairLink.iterator();
		 while (plink.hasNext()) {
			 D_SideData ep=(D_SideData)plink.next();
			 if (sideDes.startEdge.twinRed==ep.endEdge
					 && ep.startEdge.twinRed==sideDes.endEdge)
				 return indx;
			 indx++;
		 }
		 return -1;
	}
	 
	/**
	 * Find 'SideDescription' with given label.
	 * @param labelStr 
	 * @return SideDescription, or null if not found
	 */
	public static D_SideData findLabeled(D_PairLink pairLink, String labelStr) {
		if (pairLink==null || pairLink.size()==0) return null;
		Iterator<D_SideData> sides=pairLink.iterator();
		D_SideData edge=null;
		while (sides.hasNext()) {
			edge=(D_SideData)sides.next();
			if (edge.label.equals(labelStr)) return edge;
		}
		return null;
	}
	
}
