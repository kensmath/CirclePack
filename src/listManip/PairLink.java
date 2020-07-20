package listManip;

import java.util.Iterator;
import java.util.LinkedList;

import komplex.SideDescription;
import packing.PackData;

/**
 * Linked list of 'SideDescription' objects; these contain data on side-pairings 
 * and on non-pairing border segments for non-simply connected complexes.
 * (Note: formerly, indexing was from 1, but that changed 9/07.) 
 * @author kens
 */
public class PairLink extends LinkedList<SideDescription> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public PairLink(PackData p) {
		super();
		packData=p;
	}

	/**
	 * Search through 'pairLink' to the index of the 'SideDescription' which
	 * is a mate to 'sideDes'. Match involves 'startEdge' of one equal
	 * to 'endEdge' of other and vice verse. Return -1 if not found; may
	 * indicate an error.
	 * @param pairLink PairLink, list of side pairings
	 * @param sideDes SideDescription
	 * @return index of mate to sideDes, -1 if none found
	 */
	public static int find_mate(PairLink pairLink,SideDescription sideDes) {
		 int indx=0;
		 Iterator<SideDescription> plink=pairLink.iterator();
		 while (plink.hasNext()) {
			 SideDescription ep=(SideDescription)plink.next();
			 if (sideDes.startEdge.crossRed==ep.endEdge
					 && ep.startEdge.crossRed==sideDes.endEdge)
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
	public static SideDescription findLabeled(PairLink pairLink, String labelStr) {
		if (pairLink==null || pairLink.size()==0) return null;
		Iterator<SideDescription> sides=pairLink.iterator();
		SideDescription edge=null;
		while (sides.hasNext()) {
			edge=(SideDescription)sides.next();
			if (edge.label.equals(labelStr)) return edge;
		}
		return null;
	}
	
}
