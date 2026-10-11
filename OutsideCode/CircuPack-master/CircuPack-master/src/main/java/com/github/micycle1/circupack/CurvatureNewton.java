package com.github.micycle1.circupack;

import java.util.Arrays;

import com.github.micycle1.circupack.linalg.AMG;
import com.github.micycle1.circupack.linalg.BiCGStabSolver.SparseCSR;
import com.github.micycle1.circupack.linalg.CGSolver;
import com.github.micycle1.circupack.linalg.Preconditioner;

/**
 * <p>
 * Damped inexact Newton solver for the Euclidean circle-packing curvature
 * equations: find radii such that the angle sum at every vertex equals its
 * aim, {@code K_i(u) = aim_i − Σ_f θ_i^f(u) = 0}, in log-radii
 * {@code u = log r}. Centers play no part; they are laid out once the radii
 * have converged.
 * </p>
 *
 * <p>
 * A face with radii {@code r_i, r_j, r_k} has incircle radius
 * {@code t = sqrt(r_i r_j r_k / (r_i+r_j+r_k))}, angle
 * {@code θ_i = 2·atan2(t, r_i)} and {@code ∂θ_i/∂u_j = t/(r_i+r_j)}. The
 * Jacobian of {@code K} is therefore exactly the weighted graph Laplacian with
 * edge conductances {@code (t_1+t_2)/(r_i+r_j)} — the same GO weights the
 * Tutte-style center solve uses — so it is symmetric positive semidefinite.
 * </p>
 *
 * <p>
 * When every vertex is unknown, the Jacobian has the constant vector (global
 * scale) as nullspace. Instead of pinning a vertex — which leaves a
 * near-singular system that aggregation AMG preconditions badly — the constant
 * mode is projected out of the residual, the CG iterates and the
 * preconditioned vectors, and the AMG is built on a slightly diagonally shifted
 * Jacobian so its coarse levels stay nonsingular.
 * </p>
 *
 * <p>
 * Each Newton step solves {@code J Δu = −K} by AMG-preconditioned CG with a
 * forcing tolerance that tightens as {@code ‖K‖} falls, then backtracks on
 * {@code ‖K‖₂}. The AMG hierarchy from the first step is reused (the sparsity
 * pattern is fixed and the weights drift moderately); it is rebuilt only if
 * CG fails to converge with it.
 * </p>
 */
final class CurvatureNewton {

	private static final double MAX_STEP = 2.0; // per-component cap on |Δu| (log units)
	private static final double PRECOND_SHIFT = 1e-3; // relative diagonal shift (scale gauge only)

	private final int faceCount;
	private final int[] faceV; // 3 per face
	private final int[] slot; // 9 per face: CSR position of (row a, col b) for a,b in 0..2, or -1

	private final int[] idx; // vertex -> unknown index, -1 if fixed
	private final int[] unk; // unknown index -> vertex
	private final int nu;
	private final double[] aims; // per vertex
	private final boolean scaleGauge; // every active vertex unknown: J has the constant nullspace

	private final int[] rowPtr, colIdx;
	private final double[] val, diagInv;

	/** Statistics of the last {@link #solve}. */
	int newtonIters, cgIters;
	/** max |K| after the last {@link #solve}. */
	double residual;

	/**
	 * @param flowers    CCW flowers (open for boundary vertices)
	 * @param isBoundary boundary flags (selects open vs cyclic flowers)
	 * @param active     vertices taking part; faces touching an inactive vertex
	 *                   are ignored
	 * @param unknown    vertices whose radius is solved for (must be active);
	 *                   others keep their given radius
	 * @param aims       target angle sum per vertex (read for unknowns only)
	 */
	CurvatureNewton(int[][] flowers, boolean[] isBoundary, boolean[] active, boolean[] unknown, double[] aims) {
		final int nVerts = flowers.length;
		this.aims = aims;

		idx = new int[nVerts];
		int cnt = 0, activeCount = 0;
		for (int v = 0; v < nVerts; v++) {
			idx[v] = unknown[v] ? cnt++ : -1;
			activeCount += active[v] ? 1 : 0;
		}
		nu = cnt;
		scaleGauge = nu == activeCount;
		unk = new int[nu];
		for (int v = 0; v < nVerts; v++) {
			if (idx[v] >= 0) {
				unk[idx[v]] = v;
			}
		}

		// unique faces: each face appears in the flowers of all three of its
		// vertices; keep the occurrence at its smallest vertex. Faces without an
		// unknown vertex contribute nothing.
		int[] fv = new int[64];
		int f = 0;
		for (int v = 0; v < nVerts; v++) {
			if (!active[v]) {
				continue;
			}
			int[] fl = flowers[v];
			int m = fl.length;
			int faces = isBoundary[v] ? m - 1 : m;
			for (int j = 0; j < faces; j++) {
				int a = fl[j], b = fl[(j + 1) % m];
				if (a < v || b < v || !active[a] || !active[b] || (idx[v] < 0 && idx[a] < 0 && idx[b] < 0)) {
					continue;
				}
				if (3 * f + 3 > fv.length) {
					fv = Arrays.copyOf(fv, fv.length * 2);
				}
				fv[3 * f] = v;
				fv[3 * f + 1] = a;
				fv[3 * f + 2] = b;
				f++;
			}
		}
		faceCount = f;
		faceV = Arrays.copyOf(fv, 3 * f);

		// CSR pattern over unknowns: diagonal first, then unknown neighbors
		rowPtr = new int[nu + 1];
		for (int i = 0; i < nu; i++) {
			int c = 1;
			for (int w : flowers[unk[i]]) {
				if (idx[w] >= 0) {
					c++;
				}
			}
			rowPtr[i + 1] = rowPtr[i] + c;
		}
		colIdx = new int[rowPtr[nu]];
		val = new double[rowPtr[nu]];
		diagInv = new double[nu];
		for (int i = 0; i < nu; i++) {
			int p = rowPtr[i];
			colIdx[p++] = i;
			for (int w : flowers[unk[i]]) {
				if (idx[w] >= 0) {
					colIdx[p++] = idx[w];
				}
			}
		}

		// per-face slot table, so assembly scatters without searching
		slot = new int[9 * faceCount];
		for (int g = 0; g < faceCount; g++) {
			for (int a = 0; a < 3; a++) {
				int ia = idx[faceV[3 * g + a]];
				for (int b = 0; b < 3; b++) {
					int ib = idx[faceV[3 * g + b]];
					slot[9 * g + 3 * a + b] = (ia < 0 || ib < 0) ? -1 : find(ia, ib);
				}
			}
		}
	}

	private int find(int row, int col) {
		for (int p = rowPtr[row]; p < rowPtr[row + 1]; p++) {
			if (colIdx[p] == col) {
				return p;
			}
		}
		throw new IllegalStateException("edge missing from flower pattern: " + unk[row] + "-" + unk[col]);
	}

	int unknownCount() {
		return nu;
	}

	int[] unknownVertices() {
		return unk;
	}

	/**
	 * Solves in place: on entry {@code r} holds the initial radius of every
	 * vertex (fixed vertices keep theirs), on exit the solution.
	 *
	 * @param tol stop once {@code max |K_i| ≤ tol} (radians)
	 * @return true if converged
	 */
	boolean solve(double[] r, double tol, int maxIter) {
		final double[] K = new double[nu];
		final double[] Kt = new double[nu];
		final double[] du = new double[nu];
		final double[] u0 = new double[nu];
		final double[] rt = r.clone();
		final SparseCSR A = new SparseCSR(nu, rowPtr[nu], rowPtr, colIdx, val, diagInv);
		final int maxCg = Math.max(1000, 10 * nu);

		newtonIters = 0;
		cgIters = 0;
		Preconditioner pre = null;
		double kn = evaluate(r, K, true);
		residual = maxAbs(K);
		while (residual > tol && newtonIters < maxIter) {
			newtonIters++;

			// J du = -K, inexact: forcing term tightens with the residual
			for (int i = 0; i < nu; i++) {
				K[i] = -K[i];
			}
			double eta = Math.max(1e-12, Math.min(0.1, 0.1 * Math.sqrt(kn)));
			for (int attempt = 0; attempt < 2; attempt++) {
				if (pre == null || attempt > 0) {
					pre = buildPreconditioner();
				}
				Arrays.fill(du, 0.0);
				CGSolver.Result res = CGSolver.solve(A, K, du, eta, maxCg, pre);
				cgIters += res.iters;
				if (res.converged()) {
					break;
				}
			}

			double stepMax = maxAbs(du);
			double alpha = stepMax > MAX_STEP ? MAX_STEP / stepMax : 1.0;
			for (int i = 0; i < nu; i++) {
				u0[i] = Math.log(r[unk[i]]);
			}

			// backtracking on ||K||_2: the Newton direction is a descent direction
			double ktn = Double.POSITIVE_INFINITY;
			for (int ls = 0; ls < 30; ls++) {
				for (int i = 0; i < nu; i++) {
					rt[unk[i]] = Math.exp(u0[i] + alpha * du[i]);
				}
				ktn = evaluate(rt, Kt, true);
				if (ktn <= (1.0 - 1e-4 * alpha) * kn) {
					break;
				}
				alpha *= 0.5;
			}
			if (!(ktn < kn)) {
				break; // no progress possible (stagnated at roundoff level)
			}
			for (int i = 0; i < nu; i++) {
				r[unk[i]] = rt[unk[i]];
			}
			System.arraycopy(Kt, 0, K, 0, nu);
			kn = ktn;
			residual = maxAbs(K);
		}
		return residual <= tol;
	}

	/** AMG on the current Jacobian (shifted, with projection, under the scale gauge). */
	private Preconditioner buildPreconditioner() {
		if (!scaleGauge) {
			return new AMG(nu, rowPtr, colIdx, val.clone());
		}
		double[] shifted = val.clone();
		for (int i = 0; i < nu; i++) {
			shifted[rowPtr[i]] *= 1.0 + PRECOND_SHIFT;
		}
		AMG amg = new AMG(nu, rowPtr, colIdx, shifted);
		return (rr, z) -> {
			amg.apply(rr, z);
			removeMean(z);
		};
	}

	/**
	 * Computes {@code K = aims − angle sums} for the unknowns and (optionally)
	 * assembles the Jacobian {@code ∂K/∂u} into {@code val}. Returns ‖K‖₂.
	 */
	double evaluate(double[] r, double[] K, boolean assemble) {
		for (int i = 0; i < nu; i++) {
			K[i] = aims[unk[i]];
		}
		if (assemble) {
			Arrays.fill(val, 0.0);
		}
		for (int f = 0; f < faceCount; f++) {
			int a = faceV[3 * f], b = faceV[3 * f + 1], c = faceV[3 * f + 2];
			double ra = r[a], rb = r[b], rc = r[c];
			double t = Math.sqrt(ra * rb * rc / (ra + rb + rc));
			int ia = idx[a], ib = idx[b], ic = idx[c];
			if (ia >= 0) {
				K[ia] -= 2.0 * Math.atan2(t, ra);
			}
			if (ib >= 0) {
				K[ib] -= 2.0 * Math.atan2(t, rb);
			}
			if (ic >= 0) {
				K[ic] -= 2.0 * Math.atan2(t, rc);
			}
			if (assemble) {
				int s = 9 * f;
				addEdge(s, 0, 1, t / (ra + rb));
				addEdge(s, 0, 2, t / (ra + rc));
				addEdge(s, 1, 2, t / (rb + rc));
			}
		}
		if (assemble) {
			for (int i = 0; i < nu; i++) {
				diagInv[i] = 1.0 / val[rowPtr[i]];
			}
		}
		if (scaleGauge) {
			// the mean of K is unreachable (scale invariance); it is zero by
			// Gauss-Bonnet when the aims are consistent
			removeMean(K);
		}
		double s = 0.0;
		for (double k : K) {
			s += k * k;
		}
		return Math.sqrt(s);
	}

	// Laplacian contribution of weight w on face-local edge (p,q)
	private void addEdge(int s, int p, int q, double w) {
		int pp = slot[s + 4 * p], qq = slot[s + 4 * q]; // diagonals (3p+p)
		int pq = slot[s + 3 * p + q], qp = slot[s + 3 * q + p];
		if (pp >= 0) {
			val[pp] += w;
		}
		if (qq >= 0) {
			val[qq] += w;
		}
		if (pq >= 0) {
			val[pq] -= w;
			val[qp] -= w;
		}
	}

	// for tests: the assembled Jacobian at r, as a dense matrix over unknowns
	double[][] denseJacobian(double[] r) {
		evaluate(r, new double[nu], true);
		double[][] J = new double[nu][nu];
		for (int i = 0; i < nu; i++) {
			for (int p = rowPtr[i]; p < rowPtr[i + 1]; p++) {
				J[i][colIdx[p]] += val[p];
			}
		}
		return J;
	}

	private static void removeMean(double[] a) {
		double m = 0.0;
		for (double v : a) {
			m += v;
		}
		m /= a.length;
		for (int i = 0; i < a.length; i++) {
			a[i] -= m;
		}
	}

	private static double maxAbs(double[] a) {
		double m = 0.0;
		for (double v : a) {
			m = Math.max(m, Math.abs(v));
		}
		return m;
	}
}
