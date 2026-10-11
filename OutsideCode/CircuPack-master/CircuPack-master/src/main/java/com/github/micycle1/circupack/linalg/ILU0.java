package com.github.micycle1.circupack.linalg;

import java.util.Arrays;

public final class ILU0 implements Preconditioner {
	private final int n;
	private final int[] rowPtr;
	private final int[] colIdx;
	private final double[] LU; // in-place L (strict lower) and U (diag+upper)
	private final int[] diagPtr; // position of diagonal in each row
	private final double pivotEps;

	// Build ILU(0) from CSR. Does not modify (val); copies it.
	public ILU0(int n, int[] rowPtr, int[] colIdx, double[] val) {
		this(n, rowPtr, colIdx, val, 1e-14);
	}

	public ILU0(int n, int[] rowPtr, int[] colIdx, double[] val, double pivotEps) {
		this.n = n;
		this.rowPtr = rowPtr;
		this.colIdx = colIdx;
		this.LU = val.clone(); // work and result in one buffer
		this.diagPtr = new int[n];
		this.pivotEps = pivotEps;
		factor();
	}

	private void factor() {
		// find diagonal positions
		for (int i = 0; i < n; i++) {
			int rs = rowPtr[i], re = rowPtr[i + 1];
			int d = -1;
			for (int p = rs; p < re; p++) {
				if (colIdx[p] == i) {
					d = p;
					break;
				}
			}
			if (d < 0) {
				throw new IllegalStateException("ILU0: missing diagonal at row " + i);
			}
			diagPtr[i] = d;
		}

		// column position map for the active row
		int[] pos = new int[n];
		Arrays.fill(pos, -1);

		for (int i = 0; i < n; i++) {
			int rs = rowPtr[i], re = rowPtr[i + 1];

			// map columns in row i -> position
			for (int p = rs; p < re; p++) {
				pos[colIdx[p]] = p;
			}

			// process strictly lower entries in row i (columns j < i)
			for (int p = rs; p < re; p++) {
				int j = colIdx[p];
				if (j >= i) {
					continue; // lower only
				}

				double Ujj = LU[diagPtr[j]];
				if (Math.abs(Ujj) < pivotEps) {
					Ujj = (Ujj >= 0 ? pivotEps : -pivotEps);
				}
				double Lij = (LU[p] /= Ujj); // store L_ij in place

				// A_i,* -= L_ij * U_j,*
				int js = rowPtr[j], je = rowPtr[j + 1];
				for (int q = js; q < je; q++) {
					int k = colIdx[q];
					if (k <= j) {
						continue; // only U part (diag+upper; skip lower)
					}
					int ik = pos[k];
					if (ik != -1) {
						LU[ik] -= Lij * LU[q];
					}
				}
			}

			// guard diagonal
			int di = diagPtr[i];
			if (Math.abs(LU[di]) < pivotEps) {
				LU[di] = (LU[di] >= 0 ? pivotEps : -pivotEps);
			}

			// clear map
			for (int p = rs; p < re; p++) {
				pos[colIdx[p]] = -1;
			}
		}
	}

	// Apply M^{-1}: solve L U z = r (unit-diagonal L)
	@Override
	public void apply(double[] r, double[] z) {
		// forward solve: L y = r (reuse z as y)
		for (int i = 0; i < n; i++) {
			double sum = r[i];
			int rs = rowPtr[i], re = rowPtr[i + 1];
			for (int p = rs; p < re; p++) {
				int j = colIdx[p];
				if (j < i) {
					sum -= LU[p] * z[j]; // L_ij
				}
			}
			z[i] = sum; // since diag(L)=1
		}

		// backward solve: U z = y
		for (int i = n - 1; i >= 0; i--) {
			double sum = z[i];
			int rs = rowPtr[i], re = rowPtr[i + 1];
			// subtract strictly upper
			for (int p = rs; p < re; p++) {
				int j = colIdx[p];
				if (j > i) {
					sum -= LU[p] * z[j];
				}
			}
			double Uii = LU[diagPtr[i]];
			if (Math.abs(Uii) < pivotEps) {
				Uii = (Uii >= 0 ? pivotEps : -pivotEps);
			}
			z[i] = sum / Uii;
		}
	}
}