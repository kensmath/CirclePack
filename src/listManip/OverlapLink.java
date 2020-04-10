package listManip;

import java.util.LinkedList;

import komplex.EdgeMore;
import packing.PackData;


/**
 * Linked list for edges for circle packings. This replaces Node_link_parse.
 * @author kens
 *
 */
public class OverlapLink extends LinkedList<EdgeMore> {

	private static final long 
	
	serialVersionUID = 1L;
	PackData packData;
	public int count;  // generally less reliable than 'size()')

	// Constructors
	public OverlapLink(PackData p) { // empty list
		super();
		packData=p;
	}
	
}
