package dcel;

import java.awt.Color;

import complex.Complex;

/**
 * Part of effort to migrate to DCEL combinatorial structure.
 * With this 'Vertex' we can maintain enough information to
 * handle repack computations in detached code.
 * @author kstephe2, June 2016
 *
 */
class CPVertex extends Vertex {
	public int num; // count of faces
	public Complex center; // center of associated circle
	public double rad;	// radius 
	public int bdryFlag; // 0 if interior, 1 if bdry
	public double aim; // target angle sum
	public double curv; // angle sum
	public Color color;
}
