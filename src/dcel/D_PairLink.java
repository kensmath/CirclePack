package dcel;

import java.util.Iterator;
import java.util.LinkedList;

import exceptions.CombException;
import packing.PackData;

/**
 * Linked list of 'D_SideData' objects; these contain data 
 * on side-pairings and non-pairing border segments for 
 * non-simply connected complexes with dcel structures.
 * @author kens
 */
public class D_PairLink extends LinkedList<D_SideData> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public D_PairLink() {
		super();
	}

	/**
	 * Search through 'pairLink' to the index of the 'SideData' which
	 * is a mate to 'sideDes'. Match involves 'startEdge' of one equal
	 * to 'endEdge' of other and vice verse. Return -1 if not found; may
	 * indicate an error.
	 * @param pairLink PairLink, list of side pairings
	 * @param sideDes D_SideData
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
	 * Find 'D_SideData' with given label.
	 * @param labelStr 
	 * @return D_SideData, or null if not found
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
	
	/**
	 * How many side-pairings are there?
	 * @return int
	 */
	public int countPairs() {
		int count=0;
		if (this.size()<2)
			return 0;
		Iterator<D_SideData> sides=iterator();
		D_SideData edge=sides.next(); // first is null
		while (sides.hasNext()) {
			edge=(D_SideData)sides.next();
			if (edge.mateIndex>0)
				count++;
		}
		int ans=count/2;
		if (ans*2!=count)
			throw new CombException("the number of paired sides is not even");
		return ans;
	}
	
	/**
	 * Get the nth side-pairing.
	 * @param n int
	 * @return D_SideData, null if doesn't exist
	 */
	public D_SideData getPair(int n) {
		if (n<=0)
			return null;
		int tick=0;
		Iterator<D_SideData> sides=iterator();
		D_SideData edge=null;
		while (sides.hasNext() && tick<n) {
			edge=sides.next();
			if (edge.mateIndex>0 && edge.mateIndex>edge.spIndex)
				tick++;
		}
		if (tick==n)
			return edge;
		return null;
	}
	
}
