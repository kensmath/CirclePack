package com.github.micycle1.circupack.linalg;

import java.util.Arrays;

/**
 * Iterative solver implementing the Bi-Conjugate Gradient Stabilized (BiCGStab)
 * method for solving nonsymmetric linear systems A x = b where A is stored in
 * compressed sparse row (CSR) format. This implementation is written to be
 * lightweight and dependency-free and is tuned for repeated solves on matrices
 * with a constant sparsity pattern (typical in circle-packing / Tutte-style
 * systems).
 * <p>
 * Supports right preconditioning through the {@code Preconditioner} interface.
 */
public final class BiCGStabSolver {

	public static final class SparseCSR {
		public final int n;
		public final int nnz;
		public final int[] rowPtr; // length n+1
		public final int[] colIdx; // length nnz
		public final double[] val; // length nnz
		public final double[] Minv; // Jacobi inverse = 1/diag(A)

		public SparseCSR(int n, int nnz, int[] rowPtr, int[] colIdx, double[] val, double[] Minv) {
			this.n = n;
			this.nnz = nnz;
			this.rowPtr = rowPtr;
			this.colIdx = colIdx;
			this.val = val;
			this.Minv = Minv;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("SparseCSR{");
			sb.append("n=").append(n).append(", ");
			sb.append("nnz=").append(nnz).append(", ");
			sb.append("rowPtr=").append(Arrays.toString(rowPtr)).append(", ");
			sb.append("colIdx=").append(Arrays.toString(colIdx)).append(", ");
			sb.append("val=").append(Arrays.toString(val)).append(", ");
			sb.append("Minv=").append(Arrays.toString(Minv));
			sb.append("}");
			return sb.toString();
		}
	}

	public static final class Result {
		public boolean converged;
		public int iters;
		public double relResidual;
		String breakdown; // null if OK
	}

	public static Result solve(SparseCSR A, double[] b, double[] x, double tol, int maxIters, Preconditioner precond) {

		final int n = A.n;
		Result res = new Result();

		double[] r = new double[n];
		double[] rHat = new double[n];
		double[] p = new double[n];
		double[] v = new double[n];
		double[] s = new double[n];
		double[] t = new double[n];
		double[] pHat = new double[n];
		double[] sHat = new double[n];
		double[] Ax = new double[n];

		// r = b - A*x
		matVec(A, x, Ax);
		for (int i = 0; i < n; i++) {
			r[i] = b[i] - Ax[i];
		}
		System.arraycopy(r, 0, rHat, 0, n);

		double bnorm = norm2(b);
		if (bnorm == 0.0) {
			Arrays.fill(x, 0.0);
			res.converged = true;
			res.iters = 0;
			res.relResidual = 0.0;
			return res;
		}

		double rnorm = norm2(r);
		if (rnorm / bnorm <= tol) {
			res.converged = true;
			res.iters = 0;
			res.relResidual = rnorm / bnorm;
			return res;
		}

		double rhoOld = 1.0, alpha = 1.0, omega = 1.0;
		Arrays.fill(v, 0.0);
		Arrays.fill(p, 0.0);

		for (int k = 1; k <= maxIters; k++) {

			double rhoNew = dot(rHat, r);
			if (!finite(rhoNew) || Math.abs(rhoNew) < 1e-300) {
				res.breakdown = "rho breakdown";
				break;
			}

			double beta;
			if (k == 1) {
				beta = 0.0;
				System.arraycopy(r, 0, p, 0, n);
			} else {
				beta = (rhoNew / rhoOld) * (alpha / omega);
				for (int i = 0; i < n; i++) {
					p[i] = r[i] + beta * (p[i] - omega * v[i]);
				}
			}

			// pHat = M^{-1} p
			if (precond != null) {
				precond.apply(p, pHat);
			} else {
				System.arraycopy(p, 0, pHat, 0, n);
			}

			// v = A * pHat
			matVec(A, pHat, v);

			double rHat_v = dot(rHat, v);
			if (!finite(rHat_v) || Math.abs(rHat_v) < 1e-300) {
				res.breakdown = "alpha breakdown";
				break;
			}
			alpha = rhoNew / rHat_v;

			for (int i = 0; i < n; i++) {
				s[i] = r[i] - alpha * v[i];
			}

			double snorm = norm2(s);
			if (snorm / bnorm <= tol) {
				for (int i = 0; i < n; i++) {
					x[i] += alpha * pHat[i];
				}
				res.converged = true;
				res.iters = k;
				res.relResidual = snorm / bnorm;
				return res;
			}

			// sHat = M^{-1} s
			if (precond != null) {
				precond.apply(s, sHat);
			} else {
				System.arraycopy(s, 0, sHat, 0, n);
			}

			// t = A * sHat
			matVec(A, sHat, t);

			double tt = dot(t, t);
			if (!finite(tt) || tt == 0.0) {
				res.breakdown = "omega breakdown (tt=0)";
				break;
			}
			double ts = dot(t, s);
			omega = ts / tt;
			if (!finite(omega) || omega == 0.0) {
				res.breakdown = "omega breakdown";
				break;
			}

			for (int i = 0; i < n; i++) {
				x[i] += alpha * pHat[i] + omega * sHat[i];
			}

			for (int i = 0; i < n; i++) {
				r[i] = s[i] - omega * t[i];
			}

			rnorm = norm2(r);
			if (rnorm / bnorm <= tol) {
				res.converged = true;
				res.iters = k;
				res.relResidual = rnorm / bnorm;
				return res;
			}
			if (Math.abs(omega) < 1e-300) {
				res.breakdown = "omega ~ 0";
				break;
			}

			rhoOld = rhoNew;
		}

		res.converged = false;
		res.iters = maxIters;
		res.relResidual = norm2(r) / Math.max(bnorm, 1e-300);
		return res;
	}

	static void matVec(SparseCSR A, double[] x, double[] y) {
		int n = A.n;
		int[] rp = A.rowPtr;
		int[] ci = A.colIdx;
		double[] a = A.val;
		for (int i = 0; i < n; i++) {
			double sum = 0.0;
			int start = rp[i], end = rp[i + 1];
			for (int p = start; p < end; p++) {
				sum += a[p] * x[ci[p]];
			}
			y[i] = sum;
		}
	}

	static double dot(double[] a, double[] b) {
		double s = 0.0;
		for (int i = 0; i < a.length; i++) {
			s += a[i] * b[i];
		}
		return s;
	}

	static double norm2(double[] a) {
		double s = 0.0;
		for (double v : a) {
			s += v * v;
		}
		return Math.sqrt(s);
	}

	static boolean finite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}
}