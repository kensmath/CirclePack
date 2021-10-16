package ftnTheory;

import java.util.Vector;

import dcel.DcelCreation;
import dcel.RawDCEL;
import packing.PackData;
import packing.PackExtender;

/**
 * This extender is intended for constructing combinatorial 
 * tilings specified via matching rules. (It should also be 
 * useful in the future for reimplementing the dessin d'enfant 
 * methods of the old "DesPack" C code.)
 * 
 * A number of tile types are given along with information 
 * on how one type can attach to another via edge 
 * identifications. Each tile type has a number of "tile edges"; 
 * information is maintained for each tile type on its edge 
 * structure, perhaps with attached labels, and on the possible 
 * attachments: which? how many edges? orientation? etc. 
 * 
 * Also, a set of "matching" rules is given and some procedure
 *  --- perhaps random, perhaps strictly defined, perhaps at the 
 *  users command --- for growing a complex by matching new copies 
 *  of tiles to the growing pattern. Outer edge information is 
 *  maintained on tiles as the are attached.
 * 
 * Each tile is (for now) a regular n-gon. To allow 
 * identification of more than one (contiguous) edge and to allow 
 * triangles as tiles, each n-gon is a flower of n barycentrically 
 * subdivided triangles. As a consequence, each edge has a 
 * midvertex, which we'll be used to hold info on the edge.
 * 
 * @author kstephe2
 *
 */
public class MatchingRules extends PackExtender {
	
	PackData packData;
	
	public MatchingRules(PackData p) {
		super(p);
		extensionType="MATCHING_RULES";
		extensionAbbrev="MR";
		toolTip="'Matching Rules' is for generating combinatorial tilings";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
			packData.poisonEdges=null;
			packData.poisonVerts=null;
		}
	}

	/**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
//		Vector<String> items = null;
		int count=0;
			
		return count;
	}
		

	class TileType {
		PackData tile;
		int ngon;
			
		public TileType(int type,int N) {
				
			// N seed, bdry 1,2,..,N
			tile=DcelCreation.seed(N,0);
			RawDCEL.swapNodes_raw(tile.packDCEL,N+1,1);
			RawDCEL.hexBaryRefine_raw(tile.packDCEL,true);
		}
	}
		
}
