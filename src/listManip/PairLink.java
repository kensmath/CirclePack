package listManip;

import java.util.Iterator;
import java.util.LinkedList;

import komplex.EdgePair;
import packing.PackData;

/**
 * Linked list of 'EdgePair' objects; these contain data on side-pairings 
 * and on non-pairing border segments for non-simply connected complexes.
 * (Note: formerly, indexing was from 1, but that changed 9/07.) 
 * @author kens
 */
public class PairLink extends LinkedList<EdgePair> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public PairLink(PackData p) {
		super();
		packData=p;
	}

	/**
	 * Search through 'pairLink' to the index of the 'EdgePair' which
	 * is a mate to 'edgePair'. Match involves 'startEdge' of one equal
	 * to 'endEdge' of other and vice verse. Return -1 if not found; may
	 * indicate an error.
	 * @param pairLink, 'PairLink' list of side pairings
	 * @param edgePair, 'EdgePair'
	 * @return index of mate to edgePair, -1 on failure.
	 */
	public static int find_mate(PairLink pairLink,EdgePair edgePair) {
		 int indx=0;
		 Iterator<EdgePair> plink=pairLink.iterator();
		 while (plink.hasNext()) {
			 EdgePair ep=(EdgePair)plink.next();
			 if (edgePair.startEdge.crossRed==ep.endEdge
					 && ep.startEdge.crossRed==edgePair.endEdge)
				 return indx;
			 indx++;
		 }
		 return -1;
	}
	 
	/**
	 * Find 'EdgePair' with given label.
	 * @param labelStr 
	 * @return EdgePair, or null if not found
	 */
	public static EdgePair findLabeled(PairLink pairLink, String labelStr) {
		if (pairLink==null || pairLink.size()==0) return null;
		Iterator<EdgePair> sides=pairLink.iterator();
		EdgePair edge=null;
		while (sides.hasNext()) {
			edge=(EdgePair)sides.next();
			if (edge.label.equals(labelStr)) return edge;
		}
		return null;
	}
	
}
