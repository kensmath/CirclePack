package util;

import komplex.RedEdge;
import komplex.RedList;
import listManip.PairLink;
import listManip.VertList;

/**
 * Exclusively for passing information during 'build_redchain' and
 * 'red_comb_info' calls.
 * Caution: in 'build_redchain' the face order and red chain information is
 * held in global variable. 
 * @author kens
 *
 */
public class BuildPacket {
	public boolean success;
	public String buildMsg;
	public VertList faceOrdering; // A list (possibly preliminary) of order for drawing faces
	public RedList redList;       // a red chain
	public PairLink sidePairs;  
	public RedEdge firstRedEdge;

}
