package com.github.quickhull3d;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class QuickHull3DTest {

	private static final double DOUBLE_PREC = 2.2204460492503131e-16;
	private static final PrintStream NULL_OUT = new PrintStream(new OutputStream() {
		@Override
		public void write(int b) {
			// discard
		}
	});
	private static final int NO_DEGENERACY = 0;
	private static final int EDGE_DEGENERACY = 1;
	static final int VERTEX_DEGENERACY = 2;

	private boolean triangulate = false;
	private boolean testRotation = true;
	private int degeneracyTest = VERTEX_DEGENERACY;
	private double epsScale = 2.0;

	private Random rand;

	@BeforeEach
	void setUp() {
		rand = new Random(0x1234);
	}

	private boolean faceIndicesEqual(int[] indices1, int[] indices2) {
		if (indices1.length != indices2.length) {
			return false;
		}
		int len = indices1.length;
		int j;
		for (j = 0; j < len; j++) {
			if (indices1[0] == indices2[j]) {
				break;
			}
		}
		if (j == len) {
			return false;
		}
		for (int i = 1; i < len; i++) {
			if (indices1[i] != indices2[(j + i) % len]) {
				return false;
			}
		}
		return true;
	}

	private double[] randomPoints(int num, double range) {
		double[] coords = new double[num * 3];
		for (int i = 0; i < num; i++) {
			for (int k = 0; k < 3; k++) {
				coords[i * 3 + k] = 2 * range * (rand.nextDouble() - 0.5);
			}
		}
		return coords;
	}

	private void randomlyPerturb(Point3d pnt, double tol) {
		pnt.x += tol * (rand.nextDouble() - 0.5);
		pnt.y += tol * (rand.nextDouble() - 0.5);
		pnt.z += tol * (rand.nextDouble() - 0.5);
	}

	private double[] randomDegeneratePoints(int num, int dimen) {
		double[] coords = new double[num * 3];
		Point3d pnt = new Point3d();

		Point3d base = new Point3d();
		base.setRandom(-1, 1, rand);

		double tol = DOUBLE_PREC;

		if (dimen == 0) {
			for (int i = 0; i < num; i++) {
				pnt.set(base);
				randomlyPerturb(pnt, tol);
				coords[i * 3] = pnt.x;
				coords[i * 3 + 1] = pnt.y;
				coords[i * 3 + 2] = pnt.z;
			}
		} else if (dimen == 1) {
			Vector3d u = new Vector3d();
			u.setRandom(-1, 1, rand);
			u.normalize();
			for (int i = 0; i < num; i++) {
				double a = 2 * (rand.nextDouble() - 0.5);
				pnt.scale(a, u);
				pnt.add(base);
				randomlyPerturb(pnt, tol);
				coords[i * 3] = pnt.x;
				coords[i * 3 + 1] = pnt.y;
				coords[i * 3 + 2] = pnt.z;
			}
		} else { // dimen == 2
			Vector3d nrm = new Vector3d();
			nrm.setRandom(-1, 1, rand);
			nrm.normalize();
			for (int i = 0; i < num; i++) {
				Vector3d perp = new Vector3d();
				pnt.setRandom(-1, 1, rand);
				perp.scale(pnt.dot(nrm), nrm);
				pnt.sub(perp);
				pnt.add(base);
				randomlyPerturb(pnt, tol);
				coords[i * 3] = pnt.x;
				coords[i * 3 + 1] = pnt.y;
				coords[i * 3 + 2] = pnt.z;
			}
		}
		return coords;
	}

	private double[] randomSphericalPoints(int num, double radius) {
		double[] coords = new double[num * 3];
		Point3d pnt = new Point3d();

		for (int i = 0; i < num;) {
			pnt.setRandom(-radius, radius, rand);
			if (pnt.norm() <= radius) {
				coords[i * 3] = pnt.x;
				coords[i * 3 + 1] = pnt.y;
				coords[i * 3 + 2] = pnt.z;
				i++;
			}
		}
		return coords;
	}

	double[] randomCubedPoints(int num, double range, double max) {
		double[] coords = new double[num * 3];

		for (int i = 0; i < num; i++) {
			for (int k = 0; k < 3; k++) {
				double x = 2 * range * (rand.nextDouble() - 0.5);
				if (x > max) {
					x = max;
				} else if (x < -max) {
					x = -max;
				}
				coords[i * 3 + k] = x;
			}
		}
		return coords;
	}

	private double[] shuffleCoords(double[] coords) {
		int num = coords.length / 3;

		for (int i = 0; i < num; i++) {
			int i1 = rand.nextInt(num);
			int i2 = rand.nextInt(num);
			for (int k = 0; k < 3; k++) {
				double tmp = coords[i1 * 3 + k];
				coords[i1 * 3 + k] = coords[i2 * 3 + k];
				coords[i2 * 3 + k] = tmp;
			}
		}
		return coords;
	}

	private double[] randomGridPoints(int gridSize, double width) {
		int num = gridSize * gridSize * gridSize;
		double[] coords = new double[num * 3];

		int idx = 0;
		for (int i = 0; i < gridSize; i++) {
			for (int j = 0; j < gridSize; j++) {
				for (int k = 0; k < gridSize; k++) {
					coords[idx * 3] = (i / (double) (gridSize - 1) - 0.5) * width;
					coords[idx * 3 + 1] = (j / (double) (gridSize - 1) - 0.5) * width;
					coords[idx * 3 + 2] = (k / (double) (gridSize - 1) - 0.5) * width;
					idx++;
				}
			}
		}
		shuffleCoords(coords);
		return coords;
	}

	private void explicitFaceCheck(QuickHull3D hull, int[][] checkFaces) {
		// Clone so we don't mutate hull-owned arrays:
		int[][] faceIndices = Arrays.stream(hull.getFaces()).map(int[]::clone).toArray(int[][]::new);

		assertEquals(checkFaces.length, faceIndices.length, () -> "Expected " + checkFaces.length + " faces but got " + faceIndices.length);

		int[] vtxIndices = hull.getVertexPointIndices();

		// translate face indices back into original indices
		for (int[] idxs : faceIndices) {
			for (int k = 0; k < idxs.length; k++) {
				idxs[k] = vtxIndices[idxs[k]];
			}
		}

		for (int[] expected : checkFaces) {
			boolean found = false;
			for (int j = 0; j < faceIndices.length; j++) {
				if (faceIndices[j] != null && faceIndicesEqual(expected, faceIndices[j])) {
					faceIndices[j] = null;
					found = true;
					break;
				}
			}
			assertTrue(found, () -> "Expected face not found: " + Arrays.toString(expected));
		}
	}

	private void rotateCoords(double[] res, double[] xyz, double roll, double pitch, double yaw) {
		double sroll = Math.sin(roll);
		double croll = Math.cos(roll);
		double spitch = Math.sin(pitch);
		double cpitch = Math.cos(pitch);
		double syaw = Math.sin(yaw);
		double cyaw = Math.cos(yaw);

		double m00 = croll * cpitch;
		double m10 = sroll * cpitch;
		double m20 = -spitch;

		double m01 = croll * spitch * syaw - sroll * cyaw;
		double m11 = sroll * spitch * syaw + croll * cyaw;
		double m21 = cpitch * syaw;

		double m02 = croll * spitch * cyaw + sroll * syaw;
		double m12 = sroll * spitch * cyaw - croll * syaw;
		double m22 = cpitch * cyaw;

		for (int i = 0; i < xyz.length - 2; i += 3) {
			res[i] = m00 * xyz[i] + m01 * xyz[i + 1] + m02 * xyz[i + 2];
			res[i + 1] = m10 * xyz[i] + m11 * xyz[i + 1] + m12 * xyz[i + 2];
			res[i + 2] = m20 * xyz[i] + m21 * xyz[i + 1] + m22 * xyz[i + 2];
		}
	}

	private void singleTest(double[] coords, int[][] checkFaces) throws Exception {
		QuickHull3D hull = new QuickHull3D();

		hull.build(coords, coords.length / 3);
		if (triangulate) {
			hull.triangulate();
		}

		assertTrue(hull.check(NULL_OUT), "Hull check failed");

		if (checkFaces != null) {
			explicitFaceCheck(hull, checkFaces);
		}
		if (degeneracyTest != NO_DEGENERACY) {
			degenerateTest(hull, coords);
		}
	}

	double[] addDegeneracy(int type, double[] coords, QuickHull3D hull) {
		int numv = coords.length / 3;
		int[][] faces = hull.getFaces(); // hull-relative indices
		int[] hullToInput = hull.getVertexPointIndices();

		double[] coordsx = new double[coords.length + faces.length * 3];
		System.arraycopy(coords, 0, coordsx, 0, coords.length);

		double[] lam = new double[3];
		double eps = hull.getDistanceTolerance();

		for (int i = 0; i < faces.length; i++) {
			lam[0] = rand.nextDouble();
			lam[1] = 1 - lam[0];
			lam[2] = 0.0;

			if (type == VERTEX_DEGENERACY && (i % 2 == 0)) {
				lam[0] = 1.0;
				lam[1] = lam[2] = 0.0;
			}

			for (int j = 0; j < 3; j++) {
				int hullVid = faces[i][j];
				int inputIdx = hullToInput[hullVid]; // critical fix
				for (int k = 0; k < 3; k++) {
					coordsx[numv * 3 + k] += lam[j] * coords[inputIdx * 3 + k] + epsScale * eps * (rand.nextDouble() - 0.5);
				}
			}
			numv++;
		}
		shuffleCoords(coordsx);
		return coordsx;
	}

	private void degenerateTest(QuickHull3D hull, double[] coords) throws Exception {
		double[] coordsx = addDegeneracy(degeneracyTest, coords, hull);

		QuickHull3D xhull = new QuickHull3D();
		xhull.build(coordsx, coordsx.length / 3);
		if (triangulate) {
			xhull.triangulate();
		}

		assertTrue(xhull.check(NULL_OUT), "Degenerate hull check failed");
	}

	private void testWithOptionalRotations(double[] coords, int[][] checkFaces) throws Exception {
		double[][] rpyList = new double[][] { { 0, 0, 0 }, { 10, 20, 30 }, { -45, 60, 91 }, { 125, 67, 81 } };

		singleTest(coords, checkFaces);

		if (testRotation) {
			double[] xcoords = new double[coords.length];
			for (double[] rpy : rpyList) {
				rotateCoords(xcoords, coords, Math.toRadians(rpy[0]), Math.toRadians(rpy[1]), Math.toRadians(rpy[2]));
				singleTest(xcoords, checkFaces);
			}
		}
	}

	@Test
	void rejectsDegenerateInputs() {
		for (int dimen = 0; dimen < 3; dimen++) {
			for (int i = 0; i < 10; i++) {
				double[] coords = randomDegeneratePoints(10, dimen);

				final String expectedMsg;
				if (dimen == 0) {
					expectedMsg = "Input points appear to be coincident";
				} else if (dimen == 1) {
					expectedMsg = "Input points appear to be colinear";
				} else if (dimen == 2) {
					expectedMsg = "Input points appear to be coplanar";
				} else {
					throw new IllegalArgumentException("Unexpected dimen: " + dimen);
				}

				QuickHull3D hull = new QuickHull3D();
				Exception ex = assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() throws Throwable {
						hull.build(coords);
					}
				}, "Expected build() to fail");

				assertEquals(expectedMsg, ex.getMessage());
			}
		}
	}

	@Test
	void explicitCase_fromMarianoZelke_1() throws Exception {
		double[] coords = new double[] { 21, 0, 0, 0, 21, 0, 0, 0, 0, 18, 2, 6, 1, 18, 5, 2, 1, 3, 14, 3, 10, 4, 14, 14, 3, 4, 10, 10, 6, 12, 5, 10, 15, };
		testWithOptionalRotations(coords, null);
	}

	@Test
	void explicitCase_fromMarianoZelke_2() throws Exception {
		double[] coords = new double[] { 0.0, 0.0, 0.0, 21.0, 0.0, 0.0, 0.0, 21.0, 0.0, 2.0, 1.0, 2.0, 17.0, 2.0, 3.0, 1.0, 19.0, 6.0, 4.0, 3.0, 5.0, 13.0, 4.0,
				5.0, 3.0, 15.0, 8.0, 6.0, 5.0, 6.0, 9.0, 6.0, 11.0, };
		testWithOptionalRotations(coords, null);
	}

	@Test
	@Tag("slow")
	void randomPointClouds() throws Exception {
		for (int n = 20; n < 200; n += 10) {
			for (int i = 0; i < 10; i++) {
				testWithOptionalRotations(randomPoints(n, 1.0), null);
			}
		}
	}

	@Test
	@Tag("slow")
	void randomPointClouds_inSphere() throws Exception {
		for (int n = 20; n < 200; n += 10) {
			for (int i = 0; i < 10; i++) {
				testWithOptionalRotations(randomSphericalPoints(n, 1.0), null);
			}
		}
	}

	@Test
	@Tag("slow")
	void randomPointClouds_clippedToCube() throws Exception {
		for (int n = 20; n < 200; n += 10) {
			for (int i = 0; i < 10; i++) {
				testWithOptionalRotations(randomCubedPoints(n, 1.0, 0.5), null);
			}
		}
	}

	@Test
	@Tag("slow")
	void randomGridPoints() throws Exception {
		for (int n = 2; n <= 5; n++) {
			for (int i = 0; i < 10; i++) {
				testWithOptionalRotations(randomGridPoints(n, 4.0), null);
			}
		}
	}

	@Test
	void hullShouldWorkAcrossScales() throws Exception {
		double[] base = randomSphericalPoints(200, 1.0);

		double[] scales = { 1e-9, 1.0, 1e9, 1e12 };
		for (double s : scales) {
			double[] scaled = base.clone();
			for (int i = 0; i < scaled.length; i++)
				scaled[i] *= s;
			testWithOptionalRotations(scaled, null);
		}
	}

	@Test
	void hullShouldWorkWithLargeTranslations() throws Exception {
		double[] coords = randomPoints(200, 1.0);

		double tx = 1e9, ty = -1e9, tz = 5e8;
		double[] shifted = coords.clone();
		for (int i = 0; i < shifted.length; i += 3) {
			shifted[i] += tx;
			shifted[i + 1] += ty;
			shifted[i + 2] += tz;
		}
		testWithOptionalRotations(shifted, null);
	}

	@Test
	void hullShouldHandleMixedMagnitudeCoordinates() throws Exception {
		double[] coords = randomPoints(15, 1.0);
		for (int i = 0; i < coords.length; i += 3) {
			coords[i] *= 1e8; // x huge
			// y,z stay ~1
		}
		testWithOptionalRotations(coords, null);
	}

	@Test
	void duplicatePointsShouldBeHandledDeterministically() {
		double[] coords = randomPoints(100, 1.0);

		// append exact duplicates of first 10 points
		double[] withDup = Arrays.copyOf(coords, coords.length + 10 * 3);
		System.arraycopy(coords, 0, withDup, coords.length, 10 * 3);

		QuickHull3D hull = new QuickHull3D();
		assertDoesNotThrow(() -> hull.build(withDup));
	}

	@Test
	@Tag("stress")
	void horizonShouldNotStackOverflow() {
		double[] coords = randomSphericalPoints(50_000, 1.0);
		QuickHull3D hull = new QuickHull3D();
		assertDoesNotThrow(() -> hull.build(coords));
		assertTrue(hull.check(NULL_OUT));
	}

	@Test
	@Disabled("Performance test; run manually")
	void timingTest() {
	    int n = 10;
	    final int warmupSize = 10_000;
	    final int warmupRuns = 2;
	    final int cnt = 10;

	    // Warm-up (JIT + class loading)
	    double[] warmupCoords = randomSphericalPoints(warmupSize, 1.0);
	    for (int i = 0; i < warmupRuns; i++) {
	        new QuickHull3D().build(warmupCoords);
	    }

	    for (int i = 0; i < 4; i++) {
	        n *= 10;
	        double[] coords = randomSphericalPoints(n, 1.0);

	        long t0 = System.nanoTime();
	        for (int k = 0; k < cnt; k++) {
	            new QuickHull3D().build(coords);
	        }
	        long t1 = System.nanoTime();

	        double avgMs = (t1 - t0) / (double) cnt / 1_000_000.0;
	        System.out.printf("%,d points: %.3f ms%n", n, avgMs);
	    }
	}
}