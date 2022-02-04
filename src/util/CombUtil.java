package util;

import dcel.HalfEdge;
import packing.PackData;

/**
 * This contains combinatorical utility routines for circle packings.
 * @author kens
 *
 */
public class CombUtil {
	
	/**
	 * If w is neighbor of v, return its index in the flower of v; else return -1.
	 * @param p PackData
	 * @param v int
	 * @param w int
	 * @return int
	 */
	public int nghb(PackData p,int v,int w) {
		if (v<1 || v>p.nodeCount || w<1 || w>p.nodeCount) 
			return -1;
		HalfEdge hedge=p.packDCEL.vertices[v].halfedge;
		HalfEdge he=hedge;
		int tick=0;
		do {
			if (he.twin.origin.vertIndx==w)
				return tick;
			he=he.prev.twin;
			tick++;
		} while (he!=hedge);
		return -1;
	}

}
