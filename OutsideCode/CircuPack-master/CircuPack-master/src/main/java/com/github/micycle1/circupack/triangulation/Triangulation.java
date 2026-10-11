package com.github.micycle1.circupack.triangulation;

import java.util.ArrayList;
import java.util.List;

/**
 * The combinatorial input to CircuPack: which circles are tangent to which.
 * <p>
 * A triangulation is described purely combinatorially — vertex count,
 * per-vertex neighbor lists ("flowers"), and the boundary loop. No geometry is
 * required; the packer computes all radii and centers itself, and results are
 * retrieved from {@code CircuPacker} getters.
 * <p>
 * Conventions:
 * <ul>
 * <li>Vertex indices are 0..n-1 (0-based).</li>
 * <li>{@link #getFlower(int)} returns neighbors in COUNTER-CLOCKWISE order.
 * <ul>
 * <li>interior vertex: flower is cyclic (length = degree). Do NOT repeat the
 * first neighbor at the end.</li>
 * <li>boundary vertex: flower is open, running from the CCW-previous boundary
 * neighbor, through the interior wedge, to the CCW-next boundary neighbor. The
 * packer sums over consecutive pairs (j, j+1) and intentionally does not wrap,
 * so the flower must cover exactly the interior wedge.</li>
 * </ul>
 * </li>
 * <li>{@link #getBoundaryLoop()} returns CCW-ordered boundary vertices, each
 * exactly once; the closing edge (last to first) is implied, not repeated. Null
 * or empty indicates a sphere (no boundary).</li>
 * </ul>
 */
public interface Triangulation {

	/** Number of vertices (n), indexed 0..n-1. */
	int getVertexCount();

	/**
	 * The neighbor list ("flower") for vertex v in CCW order. For interior vertices
	 * the list is cyclic (no repeated first element); for boundary vertices it is
	 * open, running from the CCW-previous boundary neighbor to the CCW-next
	 * boundary neighbor through the interior wedge.
	 */
	List<Integer> getFlower(int v);

	/**
	 * CCW-ordered boundary loop, each boundary vertex exactly once (the closure
	 * between last and first is implied). Null/empty means sphere/no boundary.
	 */
	List<Integer> getBoundaryLoop();

	/**
	 * True if v is a boundary vertex. The default derives this from
	 * {@link #getBoundaryLoop()}; implementations should override with an O(1)
	 * lookup.
	 */
	default boolean isBoundaryVertex(int v) {
		List<Integer> loop = getBoundaryLoop();
		return loop != null && loop.contains(v);
	}

	/**
	 * Convenience: list of interior vertices. The default derives this from
	 * {@link #isBoundaryVertex(int)}.
	 */
	default List<Integer> getInteriorVertices() {
		int n = getVertexCount();
		List<Integer> interior = new ArrayList<>();
		for (int v = 0; v < n; v++) {
			if (!isBoundaryVertex(v)) {
				interior.add(v);
			}
		}
		return interior;
	}
}
