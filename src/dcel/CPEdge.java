package dcel;

import java.awt.Color;

/**
 * Part of effort to migrate to DCEL combinatorial structure.
 * @author kstephe2, June 2016
 *
 */
public class CPEdge extends HalfEdge {
	public double overlap; // overlap/inv dist, default to 1.0 (tangency)
	public Color color; 
}
