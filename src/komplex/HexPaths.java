package komplex;

import java.util.Iterator;

import listManip.EdgeLink;
import packing.PackData;

/**
 * Hex and hex related paths are important in combinatorics considerations.
 * Various static utility routines will be built here.
 * @author kens
 *
 */
public class HexPaths {
	static PackData p;

	/**
	 * A 'half-hex' path is a directed edgepath with 2 edges incident on the 
	 * left at every interior vertex. It is determined by an initial edge,
	 * certain necessary start/stop conditions, and possibly optional "stop"
	 * conditions. Return the number of edges in the path or 0 on error.
	 * 
	 * Typically, intention is to "flip" edges along this path, so we apply
	 * 'flipable' criteria: namely, for each intended 'edge' (including given
	 * edge1), the counterclockwise edge 'e' (ie., around initial end of 'edge')
	 * must be "flipable".
	 * 
	 * Stop conditions, bitwise: (Default is 2 | 4 | 8 = 14)
	 *  1 bit: when reaching max edge count N (set if argument N>0)
	 *  2 bit: when next edge would end at vertex already on existing path
	 *    (e.g., prevents "doubling back" at degree 3 vertex)
	 *  4 bit: allow edge ending at very first vert if next edge would = edge1
	 *    (a "closed" helf-hex path: doing all the flips will "slide" left forward
	 *    along right. See 'hex_slide'.)
	 *    (If 4 and 2 are set, then stop if you hit existing vertex other than
	 *    lining up with first.)
	 *  8 bit: when next edge would be a bdry edge
	 *  16 bit: append to given edgepath
	 *  
	 * NOTE: Always stop when last edge ends at very first vertex and
	 *  the next edge would equal edge1. This guarantees that the path
	 *  stops eventually. (If any later edge equals an earlier one, then
	 *  tracing backwards one can see that an earlier edge coincided with
	 *  edge1.
	 *  
	 * NOTE: Local checks of flipability may be easily defeated globally, there
	 *  are too many conditions to check and they change as flips are made. One 
	 *  can only be sure by doing the successive flips.
	 * 
	 * @param pd
	 * @param edge1 EdgeSimple
	 * @param stopCon bit-encoded "stop" condition 
	 * @param int N, stop with at most N edges if N>0
	 * @return EdgeLink, or null (also check size()==0)
	 */
	public static EdgeLink halfHexPath(PackData pd,EdgeLink edgelist,int stopCon,int N) {
		p=pd;
		
		// get first edge
		if (edgelist==null || edgelist.size()==0) return null;
		Iterator<EdgeSimple> elist=edgelist.iterator();
		EdgeSimple cur_edge=(EdgeSimple)elist.next();
		if (!p.flipable(cur_edge.v,cur_edge.w)) return null;
		
		EdgeLink epath=new EdgeLink(p,cur_edge); // first edge goes in
		int firstv=cur_edge.v; // save for later comparison
		int firstw=cur_edge.w;
		
		// use 'utilFlag' to mark vertices along the edge path
		for (int i=1;i<=p.nodeCount;i++) p.kData[i].utilFlag=0;
		p.kData[cur_edge.v].utilFlag=1;
		p.kData[cur_edge.w].utilFlag=1;
		
		int count=1;
		if ((stopCon & 1)==1) { // ignore stop options
			if ((stopCon & 16)==16) stopCon=17; 
			else stopCon=1;
		}
		if ((stopCon & 16)==16) { // add to end edgelist
			// check flipability, etc
			while (elist.hasNext()) {
				cur_edge=(EdgeSimple)elist.next();
				if (!p.flipable(cur_edge.v,cur_edge.w)) 
					return null;
				epath.add(cur_edge);
				p.kData[cur_edge.v].utilFlag=1;
				p.kData[cur_edge.w].utilFlag=1;
				count++;
			}
		}


		int v,w,nextv,indx,nidx;
		while (N<=0 || count<N) {
			v=cur_edge.v;
			w=cur_edge.w;
			indx=p.nghb(w,v);
			
			// next edge would encounter boundary?
			if (p.kData[w].bdryFlag!=0 && (indx<3 || (indx==3 && (stopCon & 8)==8)))
				return epath;
			
			// doubling back at degree 3 (self intersection)
			if (p.kData[w].num==3 && (stopCon & 2)==2)
				return epath;
			
			// find the next edge end vert
			if (p.kData[w].bdryFlag!=0)
				nextv=p.kData[w].flower[indx-3];
			else 
				nextv=p.kData[w].flower[(indx-3+p.kData[w].num)%p.kData[w].num];
			
			// if nextv is already on the path
			if (p.kData[nextv].utilFlag!=0) {
				if (nextv!=firstv && (stopCon & 2)==2) // stop, don't add this edge
					return epath;
				if (nextv==firstv) { // if it hits firstv
					// 4 bit not set, 2 bit is set
					if ((stopCon & 4)!=4 && ((stopCon & 2)==2))
						return epath; // stop, don't add this edge
					
					// check if NEXT edge would be edge1
					indx=p.nghb(firstv,w);
					nidx=p.nghb(firstv,firstw);
					boolean lineup=false;
					if (p.kData[firstv].bdryFlag==0) { // interior
						if ((nidx+3)%p.kData[firstv].num==indx)
							lineup=true;
					}
					else { // bdry
						if (nidx+3==indx) lineup=true;
					}
					
					// if yes, then must stop in any case to prevent infinite loop 
					if (lineup) {
						epath.add(w,nextv);
						return epath;
					}
					
					// else if 2 is set, don't add this edge
					if ((stopCon & 2)==2) 
						return epath;

					// else, fall through and continue as usual
				}
			}

			cur_edge=new EdgeSimple(w,nextv);
			epath.add(w,nextv);
			p.kData[nextv].utilFlag=1;
			count++;
		} // end of while
		return epath;
	}
	
}
