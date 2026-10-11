package com.github.micycle1.circupack.linalg;

import java.util.Arrays;

import com.github.micycle1.circupack.linalg.BiCGStabSolver.SparseCSR;

/**
 * Preconditioned conjugate gradient solver for symmetric positive-definite CSR
 * systems, solving two right-hand sides against the same matrix in lockstep.
 * <p>
 * Two design points, both motivated by the circle-packing layout solve:
 * <ul>
 * <li><b>Fused two-RHS kernels.</b> The x- and y-coordinate systems share the
 * matrix, and SpMV is memory-bound: streaming the matrix once for both vectors
 * makes the second solve nearly free compared to two independent solves.</li>
 * <li><b>Flexible (Polak–Ribière) beta.</b> The AMG V-cycle used as
 * preconditioner is not exactly symmetric (its pre/post smoothing is
 * unbalanced), which violates classic PCG assumptions. The flexible beta
 * {@code (z·(r_new − r_old)) / (z_old·r_old)} restores robustness at the cost
 * of one extra dot product and one saved residual per system.</li>
 * </ul>
 * Each system converges independently against {@code ‖r‖ ≤ tol·‖b‖}; once one
 * side converges its iterate is frozen while the other finishes (the fused
 * kernels still stream both, which is cheap since the two systems have nearly
 * identical conditioning and finish within an iteration or two of each other).
 * Supports warm starts: the initial contents of {@code x}/{@code y} are used
 * as the starting guess.
 */
public final class CGSolver {

	private CGSolver() {
	}

	public static final class Result {
		public boolean convergedX;
		public boolean convergedY;
		public int iters;
		public double relResX;
		public double relResY;
		String breakdown; // null if OK

		public boolean converged() {
			return convergedX && convergedY;
		}
	}

	// Forcing factor: every solve must shrink the incoming (warm-start) residual
	// by at least this factor, even when it already satisfies the ‖b‖-relative
	// test. Without this, a warm-started solve inside an outer fixed-point loop
	// can exit at 0 iterations, freezing the outer iteration at a spurious
	// self-consistent state.
	private static final double FORCED_REDUCTION = 0.1;

	/**
	 * Solves {@code A x = bx} and {@code A y = by} for SPD {@code A}, starting
	 * from the given {@code x}/{@code y} (warm start) and writing the solutions
	 * back into them.
	 * <p>
	 * Each system stops at {@code ‖r‖ ≤ max(min(tol·‖b‖, 0.1·‖r0‖), tolFloor·‖b‖)}:
	 * the {@code tol}-relative test, tightened so a warm start must still make
	 * a 10× residual reduction (guaranteeing outer-loop progress), but never
	 * below the {@code tolFloor}-relative bound (so a genuinely converged warm
	 * start is not over-solved).
	 */
	public static Result solve2(SparseCSR A, double[] bx, double[] by, double[] x, double[] y, double tol, double tolFloor, int maxIters,
			Preconditioner precond) {
		final int n = A.n;
		Result res = new Result();
		if (n == 0) {
			res.convergedX = true;
			res.convergedY = true;
			return res;
		}

		double[] rx = new double[n], ry = new double[n];
		double[] zx = new double[n], zy = new double[n];
		double[] px = new double[n], py = new double[n];
		double[] qx = new double[n], qy = new double[n];
		double[] rxOld = new double[n], ryOld = new double[n];

		// r = b - A*x (warm start honoured)
		matVec2(A, x, y, qx, qy);
		for (int i = 0; i < n; i++) {
			rx[i] = bx[i] - qx[i];
			ry[i] = by[i] - qy[i];
		}

		double bnx = norm2(bx);
		double bny = norm2(by);
		// zero RHS: for nonsingular A the solution is exactly zero
		if (bnx == 0.0) {
			Arrays.fill(x, 0.0);
			Arrays.fill(rx, 0.0);
		}
		if (bny == 0.0) {
			Arrays.fill(y, 0.0);
			Arrays.fill(ry, 0.0);
		}
		double rnx = norm2(rx);
		double rny = norm2(ry);
		final double tolX = Math.max(Math.min(tol * bnx, FORCED_REDUCTION * rnx), tolFloor * bnx);
		final double tolY = Math.max(Math.min(tol * bny, FORCED_REDUCTION * rny), tolFloor * bny);
		boolean convX = rnx <= tolX;
		boolean convY = rny <= tolY;

		if (!(convX && convY)) {
			applyPrecond(precond, rx, ry, zx, zy, n);
			System.arraycopy(zx, 0, px, 0, n);
			System.arraycopy(zy, 0, py, 0, n);
			double rhoX = dot(rx, zx);
			double rhoY = dot(ry, zy);

			for (int k = 1; k <= maxIters; k++) {
				res.iters = k;
				matVec2(A, px, py, qx, qy);

				if (!convX) {
					double pq = dot(px, qx);
					if (!finite(pq) || pq <= 0.0 || !finite(rhoX) || Math.abs(rhoX) < 1e-300) {
						res.breakdown = "x: loss of positive-definiteness (p·Ap=" + pq + ")";
						break;
					}
					double alpha = rhoX / pq;
					System.arraycopy(rx, 0, rxOld, 0, n);
					for (int i = 0; i < n; i++) {
						x[i] += alpha * px[i];
						rx[i] -= alpha * qx[i];
					}
					rnx = norm2(rx);
					convX = rnx <= tolX;
				}
				if (!convY) {
					double pq = dot(py, qy);
					if (!finite(pq) || pq <= 0.0 || !finite(rhoY) || Math.abs(rhoY) < 1e-300) {
						res.breakdown = "y: loss of positive-definiteness (p·Ap=" + pq + ")";
						break;
					}
					double alpha = rhoY / pq;
					System.arraycopy(ry, 0, ryOld, 0, n);
					for (int i = 0; i < n; i++) {
						y[i] += alpha * py[i];
						ry[i] -= alpha * qy[i];
					}
					rny = norm2(ry);
					convY = rny <= tolY;
				}
				if (convX && convY) {
					break;
				}

				applyPrecond(precond, rx, ry, zx, zy, n);

				if (!convX) {
					double rhoNew = dot(rx, zx);
					double beta = (rhoNew - dot(rxOld, zx)) / rhoX; // flexible PR beta
					if (!finite(beta) || beta < 0.0) {
						beta = 0.0; // restart
					}
					for (int i = 0; i < n; i++) {
						px[i] = zx[i] + beta * px[i];
					}
					rhoX = rhoNew;
				}
				if (!convY) {
					double rhoNew = dot(ry, zy);
					double beta = (rhoNew - dot(ryOld, zy)) / rhoY;
					if (!finite(beta) || beta < 0.0) {
						beta = 0.0;
					}
					for (int i = 0; i < n; i++) {
						py[i] = zy[i] + beta * py[i];
					}
					rhoY = rhoNew;
				}
			}
		}

		res.convergedX = convX;
		res.convergedY = convY;
		res.relResX = bnx == 0.0 ? 0.0 : rnx / bnx;
		res.relResY = bny == 0.0 ? 0.0 : rny / bny;
		return res;
	}

	/**
	 * Single right-hand-side flexible PCG for SPD {@code A}: solves
	 * {@code A x = b} from the warm start in {@code x}, stopping at
	 * {@code ‖r‖ ≤ tol·‖b‖}. Same flexible beta as {@link #solve2}.
	 */
	public static Result solve(SparseCSR A, double[] b, double[] x, double tol, int maxIters, Preconditioner precond) {
		final int n = A.n;
		Result res = new Result();
		res.convergedY = true; // single system: Y unused
		if (n == 0) {
			res.convergedX = true;
			return res;
		}

		double[] r = new double[n], z = new double[n], p = new double[n], q = new double[n], rOld = new double[n];
		matVec(A, x, q);
		for (int i = 0; i < n; i++) {
			r[i] = b[i] - q[i];
		}
		double bn = norm2(b);
		if (bn == 0.0) {
			Arrays.fill(x, 0.0);
			res.convergedX = true;
			return res;
		}
		final double tolAbs = tol * bn;
		double rn = norm2(r);
		boolean conv = rn <= tolAbs;
		if (!conv) {
			applyPrecond(precond, r, z, n);
			System.arraycopy(z, 0, p, 0, n);
			double rho = dot(r, z);
			for (int k = 1; k <= maxIters; k++) {
				res.iters = k;
				matVec(A, p, q);
				double pq = dot(p, q);
				if (!finite(pq) || pq <= 0.0 || !finite(rho) || Math.abs(rho) < 1e-300) {
					res.breakdown = "loss of positive-definiteness (p·Ap=" + pq + ")";
					break;
				}
				double alpha = rho / pq;
				System.arraycopy(r, 0, rOld, 0, n);
				for (int i = 0; i < n; i++) {
					x[i] += alpha * p[i];
					r[i] -= alpha * q[i];
				}
				rn = norm2(r);
				if (rn <= tolAbs) {
					conv = true;
					break;
				}
				applyPrecond(precond, r, z, n);
				double rhoNew = dot(r, z);
				double beta = (rhoNew - dot(rOld, z)) / rho;
				if (!finite(beta) || beta < 0.0) {
					beta = 0.0;
				}
				for (int i = 0; i < n; i++) {
					p[i] = z[i] + beta * p[i];
				}
				rho = rhoNew;
			}
		}
		res.convergedX = conv;
		res.relResX = rn / bn;
		return res;
	}

	private static void applyPrecond(Preconditioner precond, double[] r, double[] z, int n) {
		if (precond != null) {
			precond.apply(r, z);
		} else {
			System.arraycopy(r, 0, z, 0, n);
		}
	}

	static void matVec(SparseCSR A, double[] x, double[] y) {
		final int[] rp = A.rowPtr;
		final int[] ci = A.colIdx;
		final double[] a = A.val;
		for (int i = 0, n = A.n; i < n; i++) {
			double s = 0.0;
			for (int p = rp[i], pe = rp[i + 1]; p < pe; p++) {
				s += a[p] * x[ci[p]];
			}
			y[i] = s;
		}
	}

	private static void applyPrecond(Preconditioner precond, double[] rx, double[] ry, double[] zx, double[] zy, int n) {
		if (precond != null) {
			precond.apply2(rx, ry, zx, zy);
		} else {
			System.arraycopy(rx, 0, zx, 0, n);
			System.arraycopy(ry, 0, zy, 0, n);
		}
	}

	// y1 = A*x1, y2 = A*x2 with the matrix streamed once
	static void matVec2(SparseCSR A, double[] x1, double[] x2, double[] y1, double[] y2) {
		final int n = A.n;
		final int[] rp = A.rowPtr;
		final int[] ci = A.colIdx;
		final double[] a = A.val;
		for (int i = 0; i < n; i++) {
			double s1 = 0.0, s2 = 0.0;
			for (int p = rp[i], pe = rp[i + 1]; p < pe; p++) {
				double av = a[p];
				int c = ci[p];
				s1 += av * x1[c];
				s2 += av * x2[c];
			}
			y1[i] = s1;
			y2[i] = s2;
		}
	}

	private static double dot(double[] a, double[] b) {
		double s = 0.0;
		for (int i = 0; i < a.length; i++) {
			s += a[i] * b[i];
		}
		return s;
	}

	private static double norm2(double[] a) {
		double s = 0.0;
		for (double v : a) {
			s += v * v;
		}
		return Math.sqrt(s);
	}

	private static boolean finite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}
}
