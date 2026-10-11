package com.github.micycle1.circupack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import com.github.micycle1.circupack.triangulation.TinfourTriangulation;
import com.github.micycle1.circupack.triangulation.Triangulation;

public class PolygonPackTest {

	private static final double ANGLE_TOL = 0.15; // radians

	/**
	 * Disc-shaped TIN: dense evenly spaced boundary circle + jittered interior
	 * grid. The convex hull (= triangulation boundary) is exactly the circle
	 * points, giving a boundary loop with evenly distributed combinatorics — the
	 * setting GOPack's polygon mode is designed for.
	 */
	private TinfourTriangulation buildDiscTIN(int bdryN, long seed) {
		IIncrementalTin tin = new IncrementalTin();
		Random rnd = new Random(seed);

		List<Vertex> vs = new ArrayList<>();
		for (int i = 0; i < bdryN; i++) {
			double a = 2.0 * Math.PI * i / bdryN;
			vs.add(new Vertex(Math.cos(a), Math.sin(a), 0.0));
		}

		double spacing = 2.0 * Math.PI / bdryN;
		for (double x = -1; x <= 1; x += spacing) {
			for (double y = -1; y <= 1; y += spacing) {
				double jx = (rnd.nextDouble() * 2 - 1) * spacing * 0.3;
				double jy = (rnd.nextDouble() * 2 - 1) * spacing * 0.3;
				double px = x + jx, py = y + jy;
				if (Math.hypot(px, py) < 1.0 - spacing) {
					vs.add(new Vertex(px, py, 0.0));
				}
			}
		}
		tin.add(vs, null);
		return new TinfourTriangulation(tin);
	}

	@Test
	void rectanglePack() {
		TinfourTriangulation tri = buildDiscTIN(60, 1337);
		CircuPacker packer = new CircuPacker(tri);
		packer.setRectanglePack();
		packer.riffle(0.01);

		assertConverged(packer);

		int[] corners = packer.getCorners();
		assertEquals(4, corners.length);
		double[] cx = packer.getCentersX();
		double[] cy = packer.getCentersY();

		// setRectCenters puts corner centers exactly at (±1, ±aspect)
		assertEquals(1.0, cx[corners[0]], 1e-9);
		assertEquals(-1.0, cx[corners[1]], 1e-9);
		assertEquals(-1.0, cx[corners[2]], 1e-9);
		assertEquals(1.0, cx[corners[3]], 1e-9);
		assertEquals(cy[corners[0]], cy[corners[1]], 1e-9); // top horizontal
		assertEquals(cy[corners[2]], cy[corners[3]], 1e-9); // bottom horizontal
		assertEquals(cy[corners[0]], -cy[corners[3]], 1e-9); // symmetric about x-axis

		// right angles at corners
		for (int k = 0; k < 4; k++) {
			int prev = corners[(k + 3) % 4];
			int cur = corners[k];
			int next = corners[(k + 1) % 4];
			double dot = (cx[prev] - cx[cur]) * (cx[next] - cx[cur]) + (cy[prev] - cy[cur]) * (cy[next] - cy[cur]);
			assertEquals(0.0, dot, 1e-6, "corner " + k + " not a right angle");
		}

		// non-corner boundary circles sit on the rectangle sides
		assertBoundaryOnSides(tri, packer);

		// boundary angle sums match aims: pi/2 at corners, pi on sides
		assertBoundaryAngleSums(tri, packer, expectedCornerAims(packer, Math.PI / 2.0));

		double aspect = packer.getAspect();
		assertTrue(aspect > 0 && Double.isFinite(aspect), "bad aspect " + aspect);
		// disc combinatorics with evenly spaced corners => square-ish rectangle
		assertEquals(1.0, aspect, 0.2);
	}

	@Test
	void pentagonPack() {
		// finer mesh: for odd n >= 5 GOPack forces regular (equal-length) sides,
		// which is only an approximation to the true equiangular polygon, so a
		// small systematic residual remains and shrinks with refinement
		TinfourTriangulation tri = buildDiscTIN(120, 42);
		CircuPacker packer = new CircuPacker(tri);
		packer.setPolygonPack(5);
		packer.riffle(0.01);

		assertConverged(packer);
		assertEquals(5, packer.getCorners().length);
		assertBoundaryOnSides(tri, packer);
		assertBoundaryAngleSums(tri, packer, expectedCornerAims(packer, Math.PI * (1.0 - 2.0 / 5.0)));
	}

	@Test
	void trianglePack() {
		TinfourTriangulation tri = buildDiscTIN(60, 7);
		CircuPacker packer = new CircuPacker(tri);
		packer.setPolygonPack(3);
		packer.riffle(0.01);

		assertConverged(packer);
		int[] corners = packer.getCorners();
		assertEquals(3, corners.length);
		assertBoundaryOnSides(tri, packer);
		assertBoundaryAngleSums(tri, packer, expectedCornerAims(packer, Math.PI / 3.0));

		// equal corner aims => equilateral triangle: interior angles 60 degrees
		double[] cx = packer.getCentersX();
		double[] cy = packer.getCentersY();
		for (int k = 0; k < 3; k++) {
			int prev = corners[(k + 2) % 3];
			int cur = corners[k];
			int next = corners[(k + 1) % 3];
			double ang = angleAt(cx[cur], cy[cur], cx[prev], cy[prev], cx[next], cy[next]);
			assertEquals(Math.PI / 3.0, ang, 0.05, "corner " + k);
		}
	}

	@Test
	void customCornersAndAngles() {
		TinfourTriangulation tri = buildDiscTIN(60, 99);
		List<Integer> loop = tri.getBoundaryLoop();
		// hand-picked corners, deliberately supplied out of CCW order; angles
		// alternate pi/3, 2pi/3 in CCW order (a parallelogram, which the
		// opposite-side-pairing layout can close)
		int[] corners = { loop.get(0), loop.get(30), loop.get(15), loop.get(45) };
		double[] angles = { Math.PI / 3.0, Math.PI / 3.0, 2.0 * Math.PI / 3.0, 2.0 * Math.PI / 3.0 };

		CircuPacker packer = new CircuPacker(tri);
		packer.setPolygonPack(corners, angles);
		packer.riffle(0.01);

		assertConverged(packer);
		int[] used = packer.getCorners();
		assertEquals(corners[0], used[0], "first supplied corner should anchor the CCW order");
		assertBoundaryOnSides(tri, packer);
		// CCW order is loop(0), loop(15), loop(30), loop(45) with angles
		// pi/3, 2pi/3, pi/3, 2pi/3
		double[] ccwAims = { Math.PI / 3.0, 2.0 * Math.PI / 3.0, Math.PI / 3.0, 2.0 * Math.PI / 3.0 };
		assertBoundaryAngleSums(tri, packer, ccwAims);
	}

	@Test
	void newtonSolvesEquiangularPentagonExactly() {
		// the equiangular pentagon with free side lengths is the documented
		// problem; the fixed-point layout instead forces equal sides (and does not
		// converge on this mesh), the Newton solver satisfies every angle sum
		TinfourTriangulation tri = buildDiscTIN(60, 42);
		CircuPacker packer = new CircuPacker(tri);
		packer.setPolygonPack(5);
		packer.riffle(1e-6);

		assertMaxVisualError(tri, packer, 1e-6);
		assertBoundaryOnSides(tri, packer);
		assertRadiusAngleSums(tri, packer, Math.PI * (1.0 - 2.0 / 5.0), 1e-7);
	}

	@Test
	void newtonHonoursTightTolerance() {
		TinfourTriangulation tri = buildDiscTIN(60, 1337);
		CircuPacker packer = new CircuPacker(tri);
		packer.setRectanglePack();
		packer.riffle(1e-9);
		assertMaxVisualError(tri, packer, 1e-9);
		assertRadiusAngleSums(tri, packer, Math.PI / 2.0, 1e-9);
	}

	@Test
	void newtonMatchesFixedPointOnRectangle() {
		// a rectangle packing is unique up to similarity and both solvers
		// normalize it the same way, so their radii must agree to within the
		// fixed-point iteration's accuracy
		TinfourTriangulation tri = buildDiscTIN(60, 1337);
		CircuPacker newton = new CircuPacker(tri);
		newton.setRectanglePack();
		newton.riffle(1e-8);
		CircuPacker legacy = new CircuPacker(tri);
		legacy.setNewtonSolver(false);
		legacy.setRectanglePack();
		legacy.riffle(1e-4);

		double[] a = newton.getRadii(), b = legacy.getRadii();
		for (int v = 0; v < a.length; v++) {
			assertEquals(a[v], b[v], 1e-3 * a[v], "radius " + v);
		}
		assertEquals(newton.getAspect(), legacy.getAspect(), 1e-4);
	}

	// tangency error over EVERY edge (incl. boundary-boundary), relative to the
	// smaller radius
	private void assertMaxVisualError(Triangulation tri, CircuPacker packer, double tol) {
		double[] r = packer.getRadii(), x = packer.getCentersX(), y = packer.getCentersY();
		for (int v = 0; v < tri.getVertexCount(); v++) {
			for (int w : tri.getFlower(v)) {
				double e = Math.abs(Math.hypot(x[v] - x[w], y[v] - y[w]) - r[v] - r[w]) / Math.min(r[v], r[w]);
				assertTrue(e <= tol, "edge " + v + "-" + w + " error " + e);
			}
		}
	}

	// angle sums computed from the radii (the curvature condition itself): 2pi
	// inside, pi on sides, cornerAim at corners
	private void assertRadiusAngleSums(Triangulation tri, CircuPacker packer, double cornerAim, double tol) {
		double[] r = packer.getRadii();
		List<Integer> corners = java.util.Arrays.stream(packer.getCorners()).boxed().toList();
		for (int v = 0; v < tri.getVertexCount(); v++) {
			List<Integer> fl = tri.getFlower(v);
			boolean bd = tri.isBoundaryVertex(v);
			int k = fl.size();
			double sum = 0.0;
			for (int j = 0; j < (bd ? k - 1 : k); j++) {
				sum += Math.acos(MathUtil.cosAngle(r[v], r[fl.get(j)], r[fl.get((j + 1) % k)]));
			}
			double aim = !bd ? 2 * Math.PI : corners.contains(v) ? cornerAim : Math.PI;
			assertEquals(aim, sum, tol, "angle sum at " + v);
		}
	}

	// max relative visual error of the final packing must be small
	private void assertConverged(CircuPacker packer) {
		double max = 0.0;
		for (double e : packer.visualErrors()) {
			max = Math.max(max, e);
		}
		assertTrue(max < 0.05, "packing did not converge; max visual error " + max);
	}

	private double[] expectedCornerAims(CircuPacker packer, double aim) {
		double[] aims = new double[packer.getCorners().length];
		java.util.Arrays.fill(aims, aim);
		return aims;
	}

	// every non-corner boundary center must lie on the segment joining its
	// side's corner centers
	private void assertBoundaryOnSides(Triangulation tri, CircuPacker packer) {
		int[] corners = packer.getCorners();
		List<Integer> loop = tri.getBoundaryLoop();
		double[] cx = packer.getCentersX();
		double[] cy = packer.getCentersY();

		int m = loop.size();
		int nc = corners.length;
		for (int k = 0; k < nc; k++) {
			int a = corners[k];
			int b = corners[(k + 1) % nc];
			int ia = loop.indexOf(a);
			double ax = cx[a], ay = cy[a];
			double bx = cx[b], by = cy[b];
			double len = Math.hypot(bx - ax, by - ay);

			int i = ia;
			while (loop.get(i) != b) {
				int v = loop.get(i);
				// perpendicular distance from v's center to line a-b
				double d = Math.abs((bx - ax) * (ay - cy[v]) - (ax - cx[v]) * (by - ay)) / len;
				assertTrue(d < 1e-6 * Math.max(1.0, len), "boundary vertex " + v + " off side " + k + " by " + d);
				i = (i + 1) % m;
			}
		}
	}

	// angle sums at boundary vertices from the final geometry: the corner's aim
	// at corners (aims aligned with packer.getCorners()), pi elsewhere
	private void assertBoundaryAngleSums(Triangulation tri, CircuPacker packer, double[] cornerAims) {
		int[] corners = packer.getCorners();
		double[] cx = packer.getCentersX();
		double[] cy = packer.getCentersY();

		for (int w : tri.getBoundaryLoop()) {
			List<Integer> fl = tri.getFlower(w);
			double sum = 0.0;
			for (int j = 0; j < fl.size() - 1; j++) {
				int jr = fl.get(j);
				int jl = fl.get(j + 1);
				sum += angleAt(cx[w], cy[w], cx[jr], cy[jr], cx[jl], cy[jl]);
			}
			double aim = Math.PI;
			for (int k = 0; k < corners.length; k++) {
				if (corners[k] == w) {
					aim = cornerAims[k];
					break;
				}
			}
			assertEquals(aim, sum, ANGLE_TOL, "angle sum at boundary vertex " + w);
		}
	}

	private static double angleAt(double px, double py, double ax, double ay, double bx, double by) {
		double ux = ax - px, uy = ay - py;
		double vx = bx - px, vy = by - py;
		double c = (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy));
		return Math.acos(Math.max(-1.0, Math.min(1.0, c)));
	}
}
