package util;

import komplex.KData;
import packing.PackData;

/**
 * This contains combinatorical utility routines for circle packings.
 * @author kens
 *
 */
public class CombUtil {
	
	/**
	 * If w is neighbor of v, return its index in the flower of v; else return -1.
	 * @param p
	 * @param v
	 * @param w
	 * @return
	 */
	public int nghb(PackData p,int v,int w) {
		KData []kData=p.kData;

		if (v<1 || v>p.nodeCount || w<1 || w>p.nodeCount) return -1;
		for (int j=0;j<=kData[v].num;j++)
			if (kData[v].flower[j]==w) return j;
		return -1;
	}

}
