package com.github.micycle1.circupack;

import java.util.Arrays;

/**
 * Port of affineNormalizer.m and Centroid.m Given tangency points T in complex
 * plane (as arrays x,y), find real A and complex B = b + i c for affine map z'
 * = A*z + (b + i c) such that spherical centroid of projected points is near
 * origin. Useful when writing/normalizing spherical packings.
 */
public final class SphericalNormalize {

	private SphericalNormalize() {
	}

	// Compute affine parameters [A, b, c] where A real, B = b + i c
	public static AffineResult affineNormalizer(double[] tx, double[] ty) {
		double[] M = { 1.0, 0.0, 0.0 }; // A,b,c
		double bestsq = centroidNormSq(tx, ty, M);
		double N_TOL = 0.001;
		int CYCLES = 20;

		int outer = 0;
		double[] Tm = Arrays.copyOf(M, 3);
		while (bestsq > N_TOL && outer < CYCLES) {
			double delt = 2.0;
			double[] m = { 1.0, 0.0, 0.0 };
			int count = 0;

			while (bestsq > N_TOL && count < CYCLES) {
				int gotOne = 0;
				for (int j = 0; j < 3; j++) {
					double hold = m[j];
					m[j] = m[j] + delt;
					double newnorm = centroidNormSq(tx, ty, m);
					m[j] = hold;
					if (newnorm < bestsq) {
						bestsq = newnorm;
						gotOne = j + 1;
					} else {
						m[j] = m[j] - delt;
						newnorm = centroidNormSq(tx, ty, m);
						m[j] = hold;
						if (newnorm < bestsq) {
							bestsq = newnorm;
							gotOne = -(j + 1);
						}
					}
				}
				if (gotOne == 0) {
					delt /= 2.0;
				} else {
					int idx = Math.abs(gotOne) - 1;
					m[idx] += (gotOne > 0 ? delt : -delt);
				}
				count++;
			}

			if (bestsq < N_TOL) {
				// apply m to M (composition)
				M[0] = m[0] * M[0];
				M[1] = m[0] * M[1] + m[1];
				M[2] = m[0] * M[2] + m[2];
				return new AffineResult(M[0], M[1], M[2]);
			} else {
				// apply m to T and accumulate in M
				for (int i = 0; i < tx.length; i++) {
					tx[i] = m[0] * tx[i] + m[1];
					ty[i] = m[0] * ty[i] + m[2];
				}
				M[0] = m[0] * M[0];
				M[1] = m[0] * M[1] + m[1];
				M[2] = m[0] * M[2] + m[2];
			}
			outer++;
		}
		return new AffineResult(M[0], M[1], M[2]);
	}

	// Returns squared norm of spherical centroid for transformed points
	// M = [a,b,c] => z' = a*z + (b + i c)
	private static double centroidNormSq(double[] tx, double[] ty, double[] M) {
		int n = tx.length;
		double a = M[0], b = M[1], c = M[2];
		double sumX = 0, sumY = 0, sumZ = 0;
		for (int i = 0; i < n; i++) {
			double mu = a * tx[i] + b;
			double mv = a * ty[i] + c;
			double sq = mu * mu + mv * mv;
			double denom = 1.0 + sq;
			double x = 2.0 * mu / denom;
			double y = 2.0 * mv / denom;
			double z = (1.0 - sq) / denom;
			sumX += x;
			sumY += y;
			sumZ += z;
		}
		double X = sumX / n, Y = sumY / n, Z = sumZ / n;
		return X * X + Y * Y + Z * Z;
	}

	public static final class AffineResult {
		public final double A; // real scale
		public final double b; // real part of B
		public final double c; // imag part of B

		public AffineResult(double a, double b, double c) {
			this.A = a;
			this.b = b;
			this.c = c;
		}
	}
}