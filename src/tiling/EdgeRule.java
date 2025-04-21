package tiling;

/**
 * A parent tile edge is broken into some number of 
 * sub-edges. These are listed cclw. For example if 
 * the edge is broken into 3 subedges we have data such as
 * "1   1 2   1 1   0 3" which indicates that as you go
 * cclw along edge 1, you encounter edge 2 of subtile 1,
 * then edge 1 of subtile 1, and finally edge 3 of subtile 0. 
 * CAUTION: subtile indices in the original rules file have 
 * been incremented to start at 1.
 * 
 * Store: subedges are indexed from 0.
 *  * 'tileedge[j][0]' = tile index of 'j'_th child tile t 
 *    counterclockwise along this edge.
 *  * 'tileedge[j][1]' = the index in 't.vert' of the 
 *     first leading vertex in the side of t which lies 
 *     along this parent edge.
 *     
 * @author kens
 */
public class EdgeRule {
	public int subEdgeCount;		// how many edges this is broken into
	public int[][] tileedge;		// for each subedge, gives child tile index/edge index
	public int mark;        // integer mark, new data info in *.r files
		
	public EdgeRule(int count) {
		subEdgeCount=count;
		tileedge=new int[count][2];
		mark=0;
	}

}
