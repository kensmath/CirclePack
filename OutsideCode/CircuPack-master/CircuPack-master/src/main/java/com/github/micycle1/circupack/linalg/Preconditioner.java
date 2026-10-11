package com.github.micycle1.circupack.linalg;

/**
 * <p>
 * Collection of light-weight preconditioner implementations for CSR sparse
 * matrices used with iterative solvers (e.g. BiCGStab). Each implementation
 * implements Preconditioner.apply(r,z) which computes
 * <code>z = M^{-1} r</code>.
 * </p>
 */
public interface Preconditioner {
	void apply(double[] r, double[] z);

	/**
	 * Applies the preconditioner to two residuals at once:
	 * <code>z1 = M^{-1} r1</code> and <code>z2 = M^{-1} r2</code>. The default
	 * simply calls {@link #apply} twice; implementations whose cost is dominated
	 * by streaming matrix data (e.g. {@link AMG}) override this with a fused
	 * version that reads the matrix once for both vectors.
	 */
	default void apply2(double[] r1, double[] r2, double[] z1, double[] z2) {
		apply(r1, z1);
		apply(r2, z2);
	}
}
