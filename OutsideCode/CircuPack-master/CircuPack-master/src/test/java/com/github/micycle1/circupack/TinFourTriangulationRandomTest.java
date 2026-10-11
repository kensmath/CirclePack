package com.github.micycle1.circupack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.Vertex;
import org.tinfour.standard.IncrementalTin;

import com.github.micycle1.circupack.triangulation.TinfourTriangulation;

public class TinFourTriangulationRandomTest {

	private TinfourTriangulation buildRandomTIN(int rows, int cols, long seed, double jitterAmp) {
		IIncrementalTin tin = new IncrementalTin();
		Random rnd = new Random(seed);

		List<Vertex> vs = new ArrayList<>(rows * cols);
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				double jx = (rnd.nextDouble() * 2 - 1) * jitterAmp;
				double jy = (rnd.nextDouble() * 2 - 1) * jitterAmp;
				double x = c + jx;
				double y = r + jy;
				vs.add(new Vertex(x, y, 0.0));
			}
		}
		tin.add(vs, null);
		return new TinfourTriangulation(tin);
	}

	@Test
	void pack() {
		var t = buildRandomTIN(15, 15, 1337, 0.25);
		CircuPacker e = new CircuPacker(t);
		e.riffle(0.01);

		// max packing: boundary circles internally tangent to the unit circle
		double[] r = e.getRadii();
		double[] cx = e.getCentersX();
		double[] cy = e.getCentersY();
		for (int v : t.getBoundaryLoop()) {
			double d = Math.hypot(cx[v], cy[v]) + r[v];
			assertEquals(1.0, d, 0.05, "boundary circle " + v + " not on unit circle");
		}
	}

	@ParameterizedTest
	@ValueSource(longs = { 1L, 42L, 12345L, 2024L, 999999L })
	void flowerEdgeSymmetryOnRandomTINs(long seed) {
		int rows = 20, cols = 15;
		double jitter = 0.3;

		TinfourTriangulation tri = buildRandomTIN(rows, cols, seed, jitter);
		int n = tri.getVertexCount();
		assertTrue(n > 3, "expected > 3 vertices");

		for (int v = 0; v < n; v++) {
			List<Integer> flower = tri.getFlower(v);
			assertNotNull(flower, "flower is null for v=" + v + " seed=" + seed);

			// no self-neighbor and no duplicates
			Set<Integer> seen = new HashSet<>();
			for (int u : flower) {
				assertNotEquals(v, u, "self-neighbor at v=" + v + " seed=" + seed);
				assertTrue(seen.add(u), "duplicate neighbor u=" + u + " for v=" + v + " seed=" + seed);
			}

			// symmetry: u in flower(v) => v in flower(u)
			for (int u : flower) {
				List<Integer> back = tri.getFlower(u);
				assertNotNull(back, "flower is null for neighbor u=" + u + " seed=" + seed);
				assertTrue(back.contains(v), "missing reciprocal edge v=" + v + " <-> u=" + u + " seed=" + seed);
			}
		}
	}
}