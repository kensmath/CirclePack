package com.github.micycle1.circupack.linalg;

public class SSOR implements Preconditioner {
	final int n;
	final int[] rp, ci;
	final double[] a;
	final double[] Dinv;
	final double omega; // e.g., 1.25

	public SSOR(int n, int[] rp, int[] ci, double[] a, double omega) {
		this.n = n;
		this.rp = rp;
		this.ci = ci;
		this.a = a;
		this.omega = omega;
		this.Dinv = new double[n];
		for (int i = 0; i < n; i++) {
			double d = 0.0;
			for (int p = rp[i]; p < rp[i + 1]; p++) {
				if (ci[p] == i) {
					d = a[p];
					break;
				}
			}
			if (Math.abs(d) < 1e-14) {
				d = (d >= 0 ? 1e-14 : -1e-14);
			}
			Dinv[i] = 1.0 / d;
		}
	}

	// z = M^{-1} r approximating (D/ω + L) D^{-1} (D/ω + U)
	@Override
	public void apply(double[] r, double[] z) {
		double[] y = z; // reuse output buffer
		// Forward: (D/ω + L) y = r
		for (int i = 0; i < n; i++) {
			double sum = r[i];
			for (int p = rp[i]; p < rp[i + 1]; p++) {
				int j = ci[p];
				if (j < i) {
					sum -= a[p] * y[j];
				}
			}
			y[i] = sum * (omega * Dinv[i]);
		}
		// Backward: (D/ω + U) z = y
		for (int i = n - 1; i >= 0; i--) {
			double sum = y[i];
			for (int p = rp[i]; p < rp[i + 1]; p++) {
				int j = ci[p];
				if (j > i) {
					sum -= a[p] * z[j];
				}
			}
			z[i] = sum * (omega * Dinv[i]);
		}
	}
}