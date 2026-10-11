package com.github.micycle1.circupack.linalg;

import java.util.ArrayDeque;

/**
 * Windowed Anderson acceleration AA(m) for fixed-point iterations
 * {@code u ← g(u)}.
 * <p>
 * Plain fixed-point iteration converges linearly and discards all history.
 * Anderson acceleration keeps the last {@code m} iterate/residual differences
 * and extrapolates through the least-squares combination of recent residuals
 * {@code f = g(u) − u} that best cancels — a multi-dimensional secant
 * (quasi-Newton) step built purely from iterates that were computed anyway.
 * For smooth, linearly convergent maps this typically reduces the iteration
 * count severalfold at the cost of {@code O(m·n)} extra arithmetic per step.
 * <p>
 * Usage: once per outer iteration call {@link #next(double[], double[])} with
 * the state {@code u} the pass started from and its image {@code g(u)}; use
 * the returned vector as the next iterate. The accelerator is conservative:
 * whenever extrapolation looks unreliable it returns {@code gu} itself (the
 * plain step) and/or drops its history, so the worst case is the
 * unaccelerated iteration. Fallback triggers:
 * <ul>
 * <li>non-finite state or residual (history dropped);</li>
 * <li>residual norm jumping by more than 2× between consecutive calls —
 * indicates the underlying map changed regime, so history is stale;</li>
 * <li>a near-singular least-squares system (ridge-regularized normal
 * equations failing to solve);</li>
 * <li>the extrapolated step deviating from the plain image by more than
 * {@code maxStepInf} in any component.</li>
 * </ul>
 * Callers may reuse the {@code u}/{@code gu} buffers between calls; the
 * accelerator copies what it retains.
 */
public final class AndersonAccelerator {

	private final int depth;
	private final double maxStepInf;

	private final ArrayDeque<double[]> dU = new ArrayDeque<>();
	private final ArrayDeque<double[]> dF = new ArrayDeque<>();
	private double[] prevU;
	private double[] prevF;
	private double prevFNorm = Double.POSITIVE_INFINITY;

	/**
	 * @param depth      window size m (number of stored differences); 3–10
	 *                   typical, 5 is a good default
	 * @param maxStepInf per-component cap on |accelerated − plain| before the
	 *                   step is rejected as a wild extrapolation, in the units
	 *                   of the state vector
	 */
	public AndersonAccelerator(int depth, double maxStepInf) {
		this.depth = Math.max(1, depth);
		this.maxStepInf = maxStepInf;
	}

	/** Drops all history; the next call behaves like the first. */
	public void reset() {
		dU.clear();
		dF.clear();
		prevU = null;
		prevF = null;
		prevFNorm = Double.POSITIVE_INFINITY;
	}

	/**
	 * Computes the accelerated next iterate from {@code u} and {@code gu = g(u)}.
	 *
	 * @return the next iterate; this is the {@code gu} array itself (not a copy)
	 *         when falling back to the plain step, so callers can detect
	 *         fallback by identity if they wish to skip a redundant copy-back
	 */
	public double[] next(double[] u, double[] gu) {
		final int n = u.length;

		double[] f = new double[n];
		double fNorm2 = 0.0;
		for (int i = 0; i < n; i++) {
			double fi = gu[i] - u[i];
			f[i] = fi;
			fNorm2 += fi * fi;
		}
		if (!Double.isFinite(fNorm2)) {
			reset();
			return gu;
		}
		double fNorm = Math.sqrt(fNorm2);

		if (fNorm > 2.0 * prevFNorm) {
			// the map changed regime (renormalisation kick, mode switch): the
			// stored differences no longer describe the local behaviour
			dU.clear();
			dF.clear();
			prevU = null;
			prevF = null;
		}

		if (prevU != null) {
			double[] du = new double[n];
			double[] df = new double[n];
			for (int i = 0; i < n; i++) {
				du[i] = u[i] - prevU[i];
				df[i] = f[i] - prevF[i];
			}
			dU.addLast(du);
			dF.addLast(df);
			if (dU.size() > depth) {
				dU.removeFirst();
				dF.removeFirst();
			}
		}
		prevU = u.clone();
		prevF = f; // freshly allocated above, safe to retain
		prevFNorm = fNorm;

		final int m = dU.size();
		if (m == 0 || fNorm == 0.0) {
			return gu;
		}

		// theta = argmin || f - dF*theta ||_2 via ridge-regularised normal
		// equations (m is tiny, conditioning handled by the ridge + fallbacks)
		double[][] Fm = dF.toArray(new double[0][]);
		double[][] G = new double[m][m];
		double[] rhs = new double[m];
		double maxDiag = 0.0;
		for (int j = 0; j < m; j++) {
			for (int k = j; k < m; k++) {
				double s = dot(Fm[j], Fm[k]);
				G[j][k] = s;
				G[k][j] = s;
			}
			rhs[j] = dot(Fm[j], f);
			maxDiag = Math.max(maxDiag, G[j][j]);
		}
		double ridge = 1e-12 * Math.max(maxDiag, 1e-300);
		for (int j = 0; j < m; j++) {
			G[j][j] += ridge;
		}

		double[] theta = solveSmall(G, rhs);
		if (theta == null) {
			return gu;
		}

		// uNext = gu - sum_j theta_j * (dU_j + dF_j)
		double[][] Um = dU.toArray(new double[0][]);
		double[] out = gu.clone();
		for (int j = 0; j < m; j++) {
			double tj = theta[j];
			if (tj == 0.0) {
				continue;
			}
			double[] duj = Um[j];
			double[] dfj = Fm[j];
			for (int i = 0; i < n; i++) {
				out[i] -= tj * (duj[i] + dfj[i]);
			}
		}

		// reject wild extrapolations component-wise
		for (int i = 0; i < n; i++) {
			double d = out[i] - gu[i];
			if (!Double.isFinite(d) || Math.abs(d) > maxStepInf) {
				return gu;
			}
		}
		return out;
	}

	private static double dot(double[] a, double[] b) {
		double s = 0.0;
		for (int i = 0; i < a.length; i++) {
			s += a[i] * b[i];
		}
		return s;
	}

	// Gaussian elimination with partial pivoting; null if numerically singular
	private static double[] solveSmall(double[][] a, double[] b) {
		final int m = b.length;
		double[] x = b.clone();
		for (int k = 0; k < m; k++) {
			int piv = k;
			double best = Math.abs(a[k][k]);
			for (int i = k + 1; i < m; i++) {
				double v = Math.abs(a[i][k]);
				if (v > best) {
					best = v;
					piv = i;
				}
			}
			if (best < 1e-300) {
				return null;
			}
			if (piv != k) {
				double[] tr = a[k];
				a[k] = a[piv];
				a[piv] = tr;
				double tb = x[k];
				x[k] = x[piv];
				x[piv] = tb;
			}
			double pivot = a[k][k];
			for (int i = k + 1; i < m; i++) {
				double lik = a[i][k] / pivot;
				if (lik == 0.0) {
					continue;
				}
				a[i][k] = 0.0;
				for (int j = k + 1; j < m; j++) {
					a[i][j] -= lik * a[k][j];
				}
				x[i] -= lik * x[k];
			}
		}
		for (int i = m - 1; i >= 0; i--) {
			double s = x[i];
			for (int j = i + 1; j < m; j++) {
				s -= a[i][j] * x[j];
			}
			x[i] = s / a[i][i];
		}
		return x;
	}
}
