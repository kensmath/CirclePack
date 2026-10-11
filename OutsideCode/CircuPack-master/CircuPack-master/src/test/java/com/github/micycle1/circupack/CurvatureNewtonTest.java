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

public class CurvatureNewtonTest {

	private static Triangulation randomTIN(int pts, long seed) {
		IIncrementalTin tin = new IncrementalTin();
		Random rnd = new Random(seed);
		List<Vertex> vs = new ArrayList<>();
		for (int i = 0; i < pts; i++) {
			vs.add(new Vertex(rnd.nextDouble(), rnd.nextDouble(), 0.0));
		}
		tin.add(vs, null);
		return new TinfourTriangulation(tin);
	}

	private static int[][] flowers(Triangulation t) {
		int[][] f = new int[t.getVertexCount()][];
		for (int v = 0; v < f.length; v++) {
			f[v] = t.getFlower(v).stream().mapToInt(Integer::intValue).toArray();
		}
		return f;
	}

	// the Jacobian of the angle-sum residual is the GO-weighted Laplacian:
	// check it against central differences, and its symmetry
	@Test
	void jacobianMatchesFiniteDifferences() {
		Triangulation t = randomTIN(60, 5);
		int n = t.getVertexCount();
		boolean[] bd = new boolean[n], active = new boolean[n], unknown = new boolean[n];
		double[] aims = new double[n];
		double[] x = new double[n];
		Random rnd = new Random(3);
		for (int v = 0; v < n; v++) {
			bd[v] = t.isBoundaryVertex(v);
			active[v] = true;
			aims[v] = bd[v] ? Math.PI : 2 * Math.PI;
			unknown[v] = v != 0; // a fixed vertex: no scale-gauge projection of K
			x[v] = 0.2 + rnd.nextDouble();
		}
		CurvatureNewton cn = new CurvatureNewton(flowers(t), bd, active, unknown, aims);
		int nu = cn.unknownCount();
		int[] unk = cn.unknownVertices();
		double[][] J = cn.denseJacobian(x);

		double h = 1e-6;
		double[] Kp = new double[nu], Km = new double[nu];
		for (int j = 0; j < nu; j++) {
			int v = unk[j];
			double x0 = x[v];
			x[v] = x0 * Math.exp(h);
			cn.evaluate(x, Kp, false);
			x[v] = x0 * Math.exp(-h);
			cn.evaluate(x, Km, false);
			x[v] = x0;
			for (int i = 0; i < nu; i++) {
				double fd = (Kp[i] - Km[i]) / (2 * h);
				assertEquals(fd, J[i][j], 1e-6 * Math.max(1, Math.abs(fd)), "J[" + i + "][" + j + "]");
			}
		}
		for (int i = 0; i < nu; i++) {
			for (int j = 0; j < nu; j++) {
				assertEquals(J[i][j], J[j][i], 1e-12 * Math.max(1, Math.abs(J[i][j])), "asymmetric at " + i + "," + j);
			}
			assertTrue(J[i][i] > 0, "non-positive diagonal");
		}
	}
}
