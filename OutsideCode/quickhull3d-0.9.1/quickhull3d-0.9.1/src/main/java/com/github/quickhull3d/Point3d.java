package com.github.quickhull3d;

/**
 * A three-element spatial point. The only difference between a point and a
 * vector is in the the way it is transformed by an affine transformation. Since
 * the transform method is not included in this reduced implementation for
 * QuickHull3D, the difference is purely academic.
 *
 * @author John E. Lloyd, Fall 2004
 */
public class Point3d extends Vector3d {

	/**
	 * Creates a Point3d and initializes it to zero.
	 */
	public Point3d() {
	}

	/**
	 * Creates a Point3d by copying a vector
	 *
	 * @param v vector to be copied
	 */
	public Point3d(Vector3d v) {
		set(v);
	}

	/**
	 * Creates a Point3d with the supplied element values.
	 *
	 * @param x first element
	 * @param y second element
	 * @param z third element
	 */
	public Point3d(double x, double y, double z) {
		set(x, y, z);
	}
}
