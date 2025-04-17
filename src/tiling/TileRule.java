package tiling;

import complex.Complex;
import math.Point3D;

/**
 * For subdivision manipulations. We follow Bill Floyd here: 
 * 'targetType' starts at 4 (because 0-3 are standard 'end' types, 
 * used to form rectangles in the original work of Cannon, 
 * Floyd, and Parry). 
 * 
 * However, we will (someday) introduce "generic" rules as well,
 * and for these, 'targetType' can be anything.
 * 
 * The number of vertices is 'edgeCount', number of edges.
 * Edges are counterclockwise; the first starts with the
 * so-called 'principal' corner, as used in our tiling context.
 * 
 * @author kens, November 2013
 *
 */
public class TileRule {

	public int targetType;  // 'type' (>= 4) of tile this rule applies to
		
	// broken into child tiles
	public int childCount;    			// number of tiles in subdivision
	public int[] childType;   			// types start at 4; indexed from 1
	// rules file may give a marking to some subtiles.
	// CAUTION: independent of type; used as 'Tile.mark' (i.e., inherited from 'Face.mark')
	//     at creation; could be corrupted if marks are reset, then can't be recovered.
	int[] childMark; 
	
	// note: children are indexed starting at 1 (indexing of Floyd starts with 0)
	int[][][] childFlower;  	// for each child, indices of nghbs (-1 if none) and
								//   indices within that nghb of corresponding edges.
								//   temp standing for 'Tile.tileFlower'.
	// e.g. childFlower[3][2][0]=4 means child 3 is pasted to child 4 across edge 2
	//      childFlower[3][2][1]=5 means that this pasting is edge 5 of child 4.
	
	// edges broken into child edges 
	public int edgeCount; 		// number of edges
	public EdgeRule[] edgeRule;	// number each original edge is broken into, indexed from 0
	// Optional: corners when in 'standard' position: first edge = [0,1].
	public Complex[] stdCorners;
	// further option: normal to plane of the face in 3D: face lies in plane
	//    determined by first edge and this normal.
	public Point3D stdNormal;
	// Optional: base edges of subtiles vis-a-vis standard position.
	public Complex[][] tileBase; // indexed from 1 (as with 'childType')
	
	// constructor
	public TileRule(int tt,int ecount) {
		targetType=tt; 
		edgeCount=ecount;
		edgeRule=new EdgeRule[ecount];
		
		// children info will be set later
		childType=null;
		childFlower=null;
		childMark=null;
		stdCorners=null;
	}

}
