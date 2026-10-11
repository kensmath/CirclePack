package com.github.micycle1.circupack.linalg;

import java.util.Arrays;
import java.util.HashMap;

public final class AMG implements Preconditioner {
	// Params
	private final int minCoarseSize;
	private final int maxLevels;
	private final int preSmooth; // per non-top level
	private final int postSmooth; // per non-top level
	private final int topPostSmooth; // only at top
	private final double omega; // Jacobi weight
	private final double diagEps = 1e-14;

	/** Largest coarsest level that still gets a dense LU. */
	private static final int MAX_DENSE_COARSE = 512;
	/** Coarsening stops once a level shrinks by less than this factor of the previous one. */
	private static final double STALL_RATIO = 0.9;
	/** Damped-Jacobi sweeps used as the coarse solve when the coarsest level is too large for dense LU. */
	private static final int COARSE_SWEEPS = 8;

	private final Level top;

	public AMG(int n, int[] rowPtr, int[] colIdx, double[] val) {
		this(n, rowPtr, colIdx, val, 60, 20, 1, 0, 1, 0.8);
	}

	/**
	 * Constructs an algebraic multigrid (AMG) preconditioner for a square sparse
	 * matrix given in CSR (compressed sparse row) format.
	 * <p>
	 * The AMG implementation builds a multilevel hierarchy using greedy pairwise
	 * aggregation and unsmoothed aggregation prolongation (P). Coarse-level
	 * operators are formed via A_c = P^T A P. Smoothing on each level is performed
	 * with damped Jacobi. The coarsest level is solved directly with a small dense
	 * LU factorization. The resulting object implements Preconditioner.apply, which
	 * performs a single V-cycle per call.
	 * <p>
	 * Notes and guarantees: - The constructor builds and stores the full multilevel
	 * hierarchy; construction cost may be nontrivial for large matrices but apply()
	 * is then faster. - The provided CSR arrays (rowPtr, colIdx, val) are not
	 * modified by this constructor and must remain valid for the lifetime of the
	 * AMG instance. - The matrix is assumed to be square of size n × n. Diagonal
	 * zeros are guarded with a small pivot epsilon internally.
	 * <p>
	 * Parameters and tuning: - minCoarseSize: the minimum number of unknowns on a
	 * coarse level. Coarsening stops once a level has size <= minCoarseSize.
	 * Increasing this reduces the number of levels (and V-cycle depth) at the cost
	 * of a larger coarse solver. - maxLevels: maximum number of multigrid levels to
	 * build (>= 1). - preSmooth: number of damped-Jacobi pre-smoothing iterations
	 * performed on non-top levels (typically 0–2). - postSmooth: number of
	 * damped-Jacobi post-smoothing iterations performed on non-top levels
	 * (typically 0–2). - topPostSmooth: number of damped-Jacobi post-smoothing
	 * iterations performed on the top (finest) level only. A common choice is 0 or
	 * 1. - omega: Jacobi relaxation weight (0 < omega <= 1). Values 0.7–0.9 often
	 * work well for unsmoothed aggregation.
	 * <p>
	 * Typical recommended settings: - For fast apply(): minCoarseSize ≈ 40–100,
	 * maxLevels ≈ 10–25, preSmooth = 0 or 1, postSmooth = 0 or 1, topPostSmooth =
	 * 1, omega ≈ 0.7–0.9.
	 * <p>
	 * Complexity and memory: - Build: depends on number of levels and sparsity
	 * pattern; coarse operator assembly uses hashing (O(nnz) to O(nnz log nnz)
	 * depending on collisions). - Apply: a V-cycle with cost roughly equal to a few
	 * SpMV's on each level plus cheap restriction/prolongation; cost and
	 * convergence depend on smoothing choices and coarsening quality.
	 * <p>
	 * Example: AMG amg = new AMG(n, rowPtr, colIdx, val, 60, 20, 1, 0, 1, 0.8); //
	 * then pass `amg` as the Preconditioner to your iterative solver
	 *
	 * @param n             number of rows/columns of the square matrix A
	 * @param rowPtr        CSR row pointer array of length n+1
	 * @param colIdx        CSR column indices array of length nnz
	 * @param val           CSR nonzero values array of length nnz
	 * @param minCoarseSize minimal allowed size of the coarsest (or last) coarse
	 *                      level; must be >= 2 (recommended 40–100)
	 * @param maxLevels     maximal number of multigrid levels to construct (>= 1)
	 * @param preSmooth     number of damped-Jacobi pre-smoothing iterations on
	 *                      non-top levels (>= 0)
	 * @param postSmooth    number of damped-Jacobi post-smoothing iterations on
	 *                      non-top levels (>= 0)
	 * @param topPostSmooth number of damped-Jacobi post-smoothing iterations on the
	 *                      top (finest) level (>= 0)
	 * @param omega         Jacobi relaxation weight (0 < omega <= 1; typical
	 *                      0.7–0.9)
	 *
	 * @throws IllegalArgumentException if n <= 0 or if the CSR arrays do not
	 *                                  describe a valid n×n matrix (e.g., rowPtr
	 *                                  length != n+1), or if sensible bounds are
	 *                                  violated (e.g., minCoarseSize < 2 or
	 *                                  maxLevels < 1).
	 */
	public AMG(int n, int[] rowPtr, int[] colIdx, double[] val, int minCoarseSize, int maxLevels, int preSmooth, int postSmooth, int topPostSmooth,
			double omega) {
		this.minCoarseSize = Math.max(2, minCoarseSize);
		this.maxLevels = Math.max(1, maxLevels);
		this.preSmooth = Math.max(0, preSmooth);
		this.postSmooth = Math.max(0, postSmooth);
		this.topPostSmooth = Math.max(0, topPostSmooth);
		this.omega = omega;
		this.top = buildHierarchy(n, rowPtr, colIdx, val);
	}

	@Override
	public void apply(double[] r, double[] z) {
		Arrays.fill(z, 0.0); // x=0 at top ⇒ residual = r
		vcycleTop(top, r, z);
	}

	/**
	 * Fused two-vector V-cycle: identical to calling {@link #apply} twice, but
	 * every level's matrix is streamed once for both vectors (SpMV-type kernels
	 * are memory-bound, so the second vector is nearly free).
	 */
	@Override
	public void apply2(double[] r1, double[] r2, double[] z1, double[] z2) {
		Arrays.fill(z1, 0.0);
		Arrays.fill(z2, 0.0);
		vcycleTop2(top, r1, r2, z1, z2);
	}

	// Top level: skip pre-smooth and residual SpMV
	private void vcycleTop(Level L, double[] b, double[] x) {
		if (L.coarse == null) {
			coarseSolve(L, b, x);
			return;
		}

		// Restrict r=b (since x=0)
		restrictSum(L, b, L.rc);

		// Coarse correction
		Arrays.fill(L.ec, 0.0);
		vcycle(L.coarse, L.rc, L.ec);

		// Prolongate and correct x
		prolongAdd(L, L.ec, x);

		// Single post-smooth on the largest grid
		if (topPostSmooth > 0) {
			jacobiSmooth(L, b, x, topPostSmooth, omega);
		}
	}

	// Inner levels
	private void vcycle(Level L, double[] b, double[] x) {
		if (preSmooth > 0) {
			jacobiSmooth(L, b, x, preSmooth, omega);
		}

		// r = b - A x
		residual(L, b, x, L.res);

		if (L.coarse == null) {
			coarseSolve(L, L.res, x);
		} else {
			restrictSum(L, L.res, L.rc);
			Arrays.fill(L.ec, 0.0);
			vcycle(L.coarse, L.rc, L.ec);
			prolongAdd(L, L.ec, x);
			if (postSmooth > 0) {
				jacobiSmooth(L, b, x, postSmooth, omega);
			}
		}
	}

	// ---------- Fused two-vector cycle (mirrors vcycleTop/vcycle exactly) ----------

	private void vcycleTop2(Level L, double[] b1, double[] b2, double[] x1, double[] x2) {
		if (L.coarse == null) {
			coarseSolve2(L, b1, b2, x1, x2);
			return;
		}

		restrictSum2(L, b1, b2, L.rc, L.rc2);

		Arrays.fill(L.ec, 0.0);
		Arrays.fill(L.ec2, 0.0);
		vcycle2(L.coarse, L.rc, L.rc2, L.ec, L.ec2);

		prolongAdd2(L, L.ec, L.ec2, x1, x2);

		if (topPostSmooth > 0) {
			jacobiSmooth2(L, b1, b2, x1, x2, topPostSmooth, omega);
		}
	}

	private void vcycle2(Level L, double[] b1, double[] b2, double[] x1, double[] x2) {
		if (preSmooth > 0) {
			jacobiSmooth2(L, b1, b2, x1, x2, preSmooth, omega);
		}

		residual2(L, b1, b2, x1, x2, L.res, L.res2);

		if (L.coarse == null) {
			coarseSolve2(L, L.res, L.res2, x1, x2);
		} else {
			restrictSum2(L, L.res, L.res2, L.rc, L.rc2);
			Arrays.fill(L.ec, 0.0);
			Arrays.fill(L.ec2, 0.0);
			vcycle2(L.coarse, L.rc, L.rc2, L.ec, L.ec2);
			prolongAdd2(L, L.ec, L.ec2, x1, x2);
			if (postSmooth > 0) {
				jacobiSmooth2(L, b1, b2, x1, x2, postSmooth, omega);
			}
		}
	}

	// x += A^{-1} b. Dense LU when the coarsest level is small; otherwise a fixed
	// number of damped-Jacobi sweeps from zero (a symmetric, positive operator, so
	// still a valid CG preconditioner).
	private void coarseSolve(Level L, double[] b, double[] x) {
		if (L.lu != null) {
			System.arraycopy(b, 0, L.tmp, 0, L.n);
			L.lu.solveInPlace(L.tmp);
		} else {
			Arrays.fill(L.tmp, 0.0);
			jacobiSmooth(L, b, L.tmp, COARSE_SWEEPS, omega);
		}
		axpyInPlace(x, L.tmp, 1.0);
	}

	// x += A^{-1} b for both vectors (no fusion needed on the direct path)
	private void coarseSolve2(Level L, double[] b1, double[] b2, double[] x1, double[] x2) {
		if (L.lu != null) {
			System.arraycopy(b1, 0, L.tmp, 0, L.n);
			L.lu.solveInPlace(L.tmp);
			axpyInPlace(x1, L.tmp, 1.0);
			System.arraycopy(b2, 0, L.tmp, 0, L.n);
			L.lu.solveInPlace(L.tmp);
			axpyInPlace(x2, L.tmp, 1.0);
		} else {
			Arrays.fill(L.tmp, 0.0);
			Arrays.fill(L.tmp2, 0.0);
			jacobiSmooth2(L, b1, b2, L.tmp, L.tmp2, COARSE_SWEEPS, omega);
			axpyInPlace(x1, L.tmp, 1.0);
			axpyInPlace(x2, L.tmp2, 1.0);
		}
	}

	private void jacobiSmooth2(Level L, double[] b1, double[] b2, double[] x1, double[] x2, int steps, double w) {
		final int[] rp = L.rowPtr, ci = L.colIdx;
		final double[] a = L.val, Dinv = L.Dinv;
		for (int s = 0; s < steps; s++) {
			for (int i = 0; i < L.n; i++) {
				double s1 = 0.0, s2 = 0.0;
				for (int p = rp[i], pe = rp[i + 1]; p < pe; p++) {
					double av = a[p];
					int c = ci[p];
					s1 += av * x1[c];
					s2 += av * x2[c];
				}
				double wd = w * Dinv[i];
				x1[i] += wd * (b1[i] - s1);
				x2[i] += wd * (b2[i] - s2);
			}
		}
	}

	private void residual2(Level L, double[] b1, double[] b2, double[] x1, double[] x2, double[] r1, double[] r2) {
		final int n = L.n;
		final int[] rp = L.rowPtr, ci = L.colIdx;
		final double[] a = L.val;
		for (int i = 0; i < n; i++) {
			double s1 = 0.0, s2 = 0.0;
			for (int p = rp[i], pe = rp[i + 1]; p < pe; p++) {
				double av = a[p];
				int c = ci[p];
				s1 += av * x1[c];
				s2 += av * x2[c];
			}
			r1[i] = b1[i] - s1;
			r2[i] = b2[i] - s2;
		}
	}

	private void restrictSum2(Level L, double[] rF1, double[] rF2, double[] rC1, double[] rC2) {
		final int[] ptr = L.childPtr, idx = L.childIdx;
		final int nc = L.nc;
		for (int k = 0; k < nc; k++) {
			double s1 = 0.0, s2 = 0.0;
			for (int p = ptr[k], pe = ptr[k + 1]; p < pe; p++) {
				int i = idx[p];
				s1 += rF1[i];
				s2 += rF2[i];
			}
			rC1[k] = s1;
			rC2[k] = s2;
		}
	}

	private void prolongAdd2(Level L, double[] eC1, double[] eC2, double[] xF1, double[] xF2) {
		final int[] ptr = L.childPtr, idx = L.childIdx;
		final int nc = L.nc;
		for (int k = 0; k < nc; k++) {
			double a1 = eC1[k];
			double a2 = eC2[k];
			for (int p = ptr[k], pe = ptr[k + 1]; p < pe; p++) {
				int i = idx[p];
				xF1[i] += a1;
				xF2[i] += a2;
			}
		}
	}

	private static void axpyInPlace(double[] y, double[] x, double alpha) {
		for (int i = 0; i < y.length; i++) {
			y[i] += alpha * x[i];
		}
	}

	private void jacobiSmooth(Level L, double[] b, double[] x, int steps, double w) {
		final int[] rp = L.rowPtr, ci = L.colIdx;
		final double[] a = L.val, Dinv = L.Dinv;
		for (int s = 0; s < steps; s++) {
			for (int i = 0; i < L.n; i++) {
				double sum = 0.0;
				for (int p = rp[i], pe = rp[i + 1]; p < pe; p++) {
					sum += a[p] * x[ci[p]];
				}
				x[i] += w * Dinv[i] * (b[i] - sum);
			}
		}
	}

	private void residual(Level L, double[] b, double[] x, double[] r) {
		final int n = L.n;
		final int[] rp = L.rowPtr, ci = L.colIdx;
		final double[] a = L.val;
		for (int i = 0; i < n; i++) {
			double sum = 0.0;
			for (int p = rp[i], pe = rp[i + 1]; p < pe; p++) {
				sum += a[p] * x[ci[p]];
			}
			r[i] = b[i] - sum;
		}
	}

	// r_c[k] = sum_{i in agg(k)} r_f[i]
	private void restrictSum(Level L, double[] rFine, double[] rCoarse) {
		final int[] ptr = L.childPtr, idx = L.childIdx;
		final int nc = L.nc;
		for (int k = 0; k < nc; k++) {
			double s = 0.0;
			for (int p = ptr[k], pe = ptr[k + 1]; p < pe; p++) {
				s += rFine[idx[p]];
			}
			rCoarse[k] = s;
		}
	}

	// x_f[i] += e_c[agg(i)] grouped by aggregates to reuse e_c
	private void prolongAdd(Level L, double[] eCoarse, double[] xFine) {
		final int[] ptr = L.childPtr, idx = L.childIdx;
		final int nc = L.nc;
		for (int k = 0; k < nc; k++) {
			double add = eCoarse[k];
			for (int p = ptr[k], pe = ptr[k + 1]; p < pe; p++) {
				xFine[idx[p]] += add;
			}
		}
	}

	// ---------- Build hierarchy ----------
	private Level buildHierarchy(int n0, int[] rp0, int[] ci0, double[] a0) {
		Level head = makeLevel(n0, rp0, ci0, a0);
		Level cur = head;
		int built = 1;

		while (built < maxLevels && cur.n > minCoarseSize) {
			Agg agg = pairwiseAggregate(cur.n, cur.rowPtr, cur.colIdx, cur.val);
			if (agg.nc >= cur.n * STALL_RATIO) {
				break; // coarsening has stalled; more levels would not pay for themselves
			}

			// children lists for fast restrict/prolong
			int[] childPtr = new int[agg.nc + 1];
			for (int i = 0; i < cur.n; i++) {
				childPtr[agg.map[i] + 1]++;
			}
			for (int k = 0; k < agg.nc; k++) {
				childPtr[k + 1] += childPtr[k];
			}
			int[] childIdx = new int[cur.n];
			int[] next = childPtr.clone();
			for (int i = 0; i < cur.n; i++) {
				childIdx[next[agg.map[i]]++] = i;
			}

			CSR coarse = buildCoarseMatrix(cur.n, cur.rowPtr, cur.colIdx, cur.val, agg);

			Level nextL = makeLevel(coarse.n, coarse.rowPtr, coarse.colIdx, coarse.val);

			cur.agg = agg.map;
			cur.nc = agg.nc;
			cur.childPtr = childPtr;
			cur.childIdx = childIdx;
			cur.rc = new double[agg.nc];
			cur.ec = new double[agg.nc];
			cur.rc2 = new double[agg.nc];
			cur.ec2 = new double[agg.nc];
			cur.coarse = nextL;

			cur = nextL;
			built++;
		}

		if (cur.n <= MAX_DENSE_COARSE) {
			cur.lu = DenseLU.fromCSR(cur.n, cur.rowPtr, cur.colIdx, cur.val, diagEps);
		} // else: coarse solve falls back to Jacobi sweeps (dense LU is O(n^3) to build, O(n^2) per apply)
		return head;
	}

	private Level makeLevel(int n, int[] rowPtr, int[] colIdx, double[] val) {
		Level L = new Level();
		L.n = n;
		L.rowPtr = rowPtr;
		L.colIdx = colIdx;
		L.val = val;
		L.Dinv = new double[n];
		for (int i = 0; i < n; i++) {
			double d = 0.0;
			boolean found = false;
			for (int p = rowPtr[i]; p < rowPtr[i + 1]; p++) {
				if (colIdx[p] == i) {
					d = val[p];
					found = true;
					break;
				}
			}
			if (!found || Math.abs(d) < diagEps) {
				d = (d >= 0 ? diagEps : -diagEps);
			}
			L.Dinv[i] = 1.0 / d;
		}
		L.res = new double[n];
		L.res2 = new double[n];
		L.tmp = new double[n];
		L.tmp2 = new double[n];
		return L;
	}

	private static final class Level {
		int n;
		int[] rowPtr, colIdx;
		double[] val;
		double[] Dinv;
		double[] res, tmp, tmp2;
		double[] res2; // second-vector workspace for apply2

		// aggregation
		int[] agg; // fine -> coarse (for completeness)
		int nc; // coarse size
		int[] childPtr; // P^T structure (coarse -> fine list)
		int[] childIdx;
		double[] rc, ec;
		double[] rc2, ec2; // second-vector workspace for apply2
		Level coarse;

		// coarsest
		DenseLU lu;
	}

	private static final class Agg {
		final int[] map;
		final int nc;

		Agg(int[] map, int nc) {
			this.map = map;
			this.nc = nc;
		}
	}

	private Agg pairwiseAggregate(int n, int[] rp, int[] ci, double[] a) {
		int[] agg = new int[n];
		Arrays.fill(agg, -1);
		int nc = 0;

		for (int i = 0; i < n; i++) {
			if (agg[i] != -1) {
				continue;
			}
			int bestJ = -1;
			double bestW = -1.0;
			for (int p = rp[i]; p < rp[i + 1]; p++) {
				int j = ci[p];
				if (j == i || agg[j] != -1) {
					continue;
				}
				double w = Math.abs(a[p]);
				if (w > bestW) {
					bestW = w;
					bestJ = j;
				}
			}
			if (bestJ >= 0) {
				agg[i] = nc;
				agg[bestJ] = nc;
				nc++;
			} else {
				agg[i] = nc++;
			}
		}

		for (int i = 0; i < n; i++) {
			if (agg[i] == -1) {
				agg[i] = nc++;
			}
		}
		return new Agg(agg, nc);
	}

	private static final class CSR {
		final int n, nnz;
		final int[] rowPtr, colIdx;
		final double[] val;

		CSR(int n, int[] rowPtr, int[] colIdx, double[] val) {
			this.n = n;
			this.rowPtr = rowPtr;
			this.colIdx = colIdx;
			this.val = val;
			this.nnz = rowPtr[n];
		}
	}

	// A_c = P^T A P (unsmoothed aggregation). Build-time only.
	private CSR buildCoarseMatrix(int n, int[] rp, int[] ci, double[] a, Agg agg) {
		int nc = agg.nc;
		int[] map = agg.map;
		@SuppressWarnings("unchecked")
		HashMap<Integer, Double>[] rows = new HashMap[nc];
		for (int k = 0; k < nc; k++) {
			rows[k] = new HashMap<>();
		}

		for (int i = 0; i < n; i++) {
			int k = map[i];
			HashMap<Integer, Double> row = rows[k];
			for (int p = rp[i]; p < rp[i + 1]; p++) {
				int j = ci[p];
				int l = map[j];
				row.put(l, row.getOrDefault(l, 0.0) + a[p]);
			}
		}

		int[] rowPtrC = new int[nc + 1];
		int nnz = 0;
		for (int k = 0; k < nc; k++) {
			rowPtrC[k] = nnz;
			nnz += rows[k].size();
		}
		rowPtrC[nc] = nnz;

		int[] colIdxC = new int[nnz];
		double[] valC = new double[nnz];

		int pos = 0;
		for (int k = 0; k < nc; k++) {
			for (var e : rows[k].entrySet()) {
				colIdxC[pos] = e.getKey();
				valC[pos] = e.getValue();
				pos++;
			}
		}
		return new CSR(nc, rowPtrC, colIdxC, valC);
	}

	// Small dense LU on the coarsest level
	private static final class DenseLU {
		final int n;
		final double[] lu; // row-major n x n
		final int[] piv;

		private DenseLU(int n, double[] lu, int[] piv) {
			this.n = n;
			this.lu = lu;
			this.piv = piv;
		}

		static DenseLU fromCSR(int n, int[] rp, int[] ci, double[] a, double eps) {
			double[] M = new double[n * n];
			for (int i = 0; i < n; i++) {
				for (int p = rp[i]; p < rp[i + 1]; p++) {
					M[i * n + ci[p]] += a[p];
				}
			}
			int[] piv = new int[n];
			for (int i = 0; i < n; i++) {
				piv[i] = i;
			}

			for (int k = 0; k < n; k++) {
				int pivRow = k;
				double max = Math.abs(M[k * n + k]);
				for (int i = k + 1; i < n; i++) {
					double v = Math.abs(M[i * n + k]);
					if (v > max) {
						max = v;
						pivRow = i;
					}
				}
				if (pivRow != k) {
					for (int j = 0; j < n; j++) {
						double t = M[k * n + j];
						M[k * n + j] = M[pivRow * n + j];
						M[pivRow * n + j] = t;
					}
					int tp = piv[k];
					piv[k] = piv[pivRow];
					piv[pivRow] = tp;
				}
				double pivot = M[k * n + k];
				if (Math.abs(pivot) < eps) {
					pivot = (pivot >= 0 ? eps : -eps);
					M[k * n + k] = pivot;
				}
				for (int i = k + 1; i < n; i++) {
					double lik = M[i * n + k] / pivot;
					M[i * n + k] = lik;
					for (int j = k + 1; j < n; j++) {
						M[i * n + j] -= lik * M[k * n + j];
					}
				}
			}
			return new DenseLU(n, M, piv);
		}

		void solveInPlace(double[] x) {
			// apply pivots
			double[] rhs = x.clone();
			for (int i = 0; i < n; i++) {
				x[i] = rhs[piv[i]];
			}

			// forward
			for (int i = 0; i < n; i++) {
				double sum = x[i];
				for (int j = 0; j < i; j++) {
					sum -= lu[i * n + j] * x[j];
				}
				x[i] = sum;
			}
			// backward
			for (int i = n - 1; i >= 0; i--) {
				double sum = x[i];
				for (int j = i + 1; j < n; j++) {
					sum -= lu[i * n + j] * x[j];
				}
				double d = lu[i * n + i];
				if (Math.abs(d) < 1e-14) {
					d = (d >= 0 ? 1e-14 : -1e-14);
				}
				x[i] = sum / d;
			}
		}
	}
}