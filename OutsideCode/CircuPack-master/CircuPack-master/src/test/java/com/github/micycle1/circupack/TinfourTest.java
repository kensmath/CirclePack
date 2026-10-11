package com.github.micycle1.circupack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import com.github.micycle1.circupack.triangulation.TinfourTriangulation;

public class TinfourTest {

	@Test
	public void testRingOrientation() {
		// CW
		Vertex v1 = new Vertex(0, 0, 0);
		Vertex v2 = new Vertex(1, 0, 0);
		Vertex v3 = new Vertex(0, -1, 0);
		assertFalse(TinfourTriangulation.isCCW(List.of(v1, v2, v3)));
		assertTrue(TinfourTriangulation.isCCW(List.of(v1, v3, v2)));
	}

	@Test
	public void testVertexCountBoundaryAndInterior() {
		TinfourTriangulation tri = buildHexagonTIN();

		// 7 vertices total (6 boundary + 1 interior)
		assertEquals(7, tri.getVertexCount());

		List<Integer> boundary = tri.getBoundaryLoop();
		List<Integer> interior = tri.getInteriorVertices();

		assertEquals(6, boundary.size());
		assertEquals(1, interior.size());

		// Boundary flags must match membership
		for (int v = 0; v < tri.getVertexCount(); v++) {
			boolean isB = tri.isBoundaryVertex(v);
			assertEquals(boundary.contains(v), isB);
			assertEquals(interior.contains(v), !isB);
		}

		// Boundary should have unique indices
		assertEquals(boundary.size(), boundary.stream().distinct().count());

		// Boundary ∪ Interior should cover all vertices and be disjoint
		List<Integer> all = new ArrayList<>();
		all.addAll(boundary);
		all.addAll(interior);
		assertEquals(tri.getVertexCount(), all.stream().distinct().count());
	}

	@Test
	public void testCenterFlowerMatchesBoundaryLoop() {
		TinfourTriangulation tri = buildHexagonTIN();

		List<Integer> boundary = tri.getBoundaryLoop();
		List<Integer> interior = tri.getInteriorVertices();
		assertEquals(1, interior.size());
		assertEquals(6, boundary.size());
		int center = interior.get(0);

		List<Integer> centerFlower = tri.getFlower(center);

		// Center should be connected to all 6 boundary vertices
		assertEquals(6, centerFlower.size());
		assertTrue(boundary.containsAll(centerFlower));
		assertTrue(centerFlower.containsAll(boundary));

		// The order of the neighbors around the center (CCW) should match the boundary
		// loop up to a circular rotation.
		assertTrue(circularlyEqual(centerFlower, boundary));
	}

	@Test
	public void testBoundaryFlowersDegreeAndAdjacency() {
		TinfourTriangulation tri = buildHexagonTIN();

		List<Integer> boundary = tri.getBoundaryLoop();
		int n = boundary.size();
		int center = tri.getInteriorVertices().get(0);

		for (int i = 0; i < n; i++) {
			int v = boundary.get(i);
			List<Integer> flower = tri.getFlower(v);

			// degree 3: two boundary neighbors + center
			assertEquals(3, flower.size());
			assertTrue(flower.contains(center));

			// extract the two boundary neighbors
			List<Integer> boundaryNeighbors = new ArrayList<>();
			for (int u : flower) {
				if (u != center) {
					assertTrue(tri.isBoundaryVertex(u));
					boundaryNeighbors.add(u);
				}
			}
			assertEquals(2, boundaryNeighbors.size());

			// they should be the previous and next on the boundary relative to v
			int prev = boundary.get(Math.floorMod(i - 1, n));
			int next = boundary.get((i + 1) % n);

			assertTrue(boundaryNeighbors.contains(prev), "iter " + i);
			assertTrue(boundaryNeighbors.contains(next), "iter " + i);

			// Optional: verify the circular order around v is prev-center-next or
			// next-center-prev
			int pPrev = flower.indexOf(prev);
			int pNext = flower.indexOf(next);
			int pCenter = flower.indexOf(center);
			assertTrue(isCircularOrder(flower.size(), pPrev, pCenter, pNext) || isCircularOrder(flower.size(), pNext, pCenter, pPrev), "order iter " + i);
		}
	}

	// Builds a small TIN from a regular hexagon + center.
	private TinfourTriangulation buildHexagonTIN() {
		IIncrementalTin tin = new IncrementalTin();

		List<Vertex> vs = new ArrayList<>();
		vs.add(new Vertex(-1.0, 0.0, 0.0));
		vs.add(new Vertex(0.0, 0.0, 0.0)); // center
		vs.add(new Vertex(1.0, 0.0, 0.0));
		vs.add(new Vertex(-0.5, 0.8660254, 0.0));
		vs.add(new Vertex(-0.5, -0.8660254, 0.0));
		vs.add(new Vertex(0.5, 0.8660254, 0.0));
		vs.add(new Vertex(0.5, -0.8660254, 0.0));

		tin.add(vs, null);
		return new TinfourTriangulation(tin);
	}

	// a,b,c appear in this circular order in a list of length n?
	private static boolean isCircularOrder(int n, int a, int b, int c) {
		if (a < 0 || b < 0 || c < 0) {
			return false;
		}
		return (Math.floorMod(b - a, n) > 0) && (Math.floorMod(c - b, n) > 0) && (Math.floorMod(a - c, n) > 0);
	}

	// Helper: Compare lists representing circular sequences up to rotation (same
	// orientation).
	private static boolean circularlyEqual(List<Integer> a, List<Integer> b) {
		if (a.size() != b.size()) {
			return false;
		}
		int n = a.size();
		if (n == 0) {
			return true;
		}
		int start = b.indexOf(a.get(0));
		if (start < 0) {
			return false;
		}
		for (int i = 0; i < n; i++) {
			if (!a.get(i).equals(b.get((start + i) % n))) {
				return false;
			}
		}
		return true;
	}
}
