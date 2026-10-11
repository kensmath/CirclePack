package com.github.micycle1.circupack.linalg;

import java.util.Arrays;

public final class Jacobi implements Preconditioner {
	private final int n;
	private final double[] Minv; // Minv[i] = 1 / A[i,i]

	private Jacobi(int n, double[] Minv) {
		this.n = n;
		this.Minv = Minv;
	}

	// Build from CSR by scanning the diagonal
	public static Jacobi fromCSR(int n, int[] rowPtr, int[] colIdx, double[] val) {
		double[] Minv = new double[n];
		final double eps = 1e-14;
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
			if (!found || Math.abs(d) < eps) {
				Minv[i] = 1.0; // fallback
			} else {
				Minv[i] = 1.0 / d;
			}
		}
		return new Jacobi(n, Minv);
	}

	// Build when you know the diagonal is a constant (e.g., always -1.0)
	public static Jacobi fromConstantDiagonal(int n, double diagValue) {
		final double eps = 1e-14;
		double inv = Math.abs(diagValue) < eps ? 1.0 : 1.0 / diagValue;
		double[] Minv = new double[n];
		Arrays.fill(Minv, inv);
		return new Jacobi(n, Minv);
	}

	// Build from an explicit diagonal vector
	public static Jacobi fromDiagonal(int n, double[] diag) {
		double[] Minv = new double[n];
		final double eps = 1e-14;
		for (int i = 0; i < n; i++) {
			double d = diag[i];
			Minv[i] = Math.abs(d) < eps ? 1.0 : 1.0 / d;
		}
		return new Jacobi(n, Minv);
	}

	@Override
	public void apply(double[] r, double[] z) {
		for (int i = 0; i < n; i++) {
			z[i] = Minv[i] * r[i];
		}
	}
}