package com.github.micycle1.circupack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import com.github.micycle1.circupack.linalg.AMG;
import com.github.micycle1.circupack.linalg.AndersonAccelerator;
import com.github.micycle1.circupack.linalg.BiCGStabSolver;
import com.github.micycle1.circupack.linalg.BiCGStabSolver.SparseCSR;
import com.github.micycle1.circupack.linalg.CGSolver;
import com.github.micycle1.circupack.triangulation.Triangulation;

/**
 * <p>
 * Core repacking engine for circle packings — a Java port of Gerald Orick's
 * GOPacker MATLAB code. Derives combinatorics (interior component, boundary
 * loop, orphans) from a {@link Triangulation}, then iteratively alternates
 * boundary placement, Tutte-style interior embedding (sparse linear solve) and
 * effective-radii updates until the packing converges.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 * <ol>
 * <li>Construct with a {@code Triangulation} (0-based indices; see its javadoc
 * for the flower/boundary conventions).</li>
 * <li>Optionally configure the target shape: {@link #setPolygonPack(int[])},
 * {@link #setRectanglePack()}, etc. The default is {@link Mode#MAX_PACK}
 * (packing the unit disc).</li>
 * <li>Run {@link #riffle(double)} to iterate until the maximum relative visual
 * error falls below the given threshold.</li>
 * <li>Retrieve results via {@link #getRadii()}, {@link #getCentersX()},
 * {@link #getCentersY()}.</li>
 * </ol>
 *
 * <p>
 * The class is not thread-safe; callers should synchronize externally if
 * needed.
 * </p>
 */
public class CircuPacker {

	/**
	 * Packing modes, mirroring GOPack: MAX_PACK packs the unit disc (boundary
	 * circles become horocycles); POLYGONAL packs so the boundary forms an n-gon
	 * with prescribed corner angles (side lengths emerge from the packing).
	 */
	public enum Mode {
		MAX_PACK, POLYGONAL
	}

	private static final int MAX_ITER = 500;

	// Inner linear-solve tolerance bounds: during riffle the tolerance tracks the
	// outer visual error (solving tighter than the current geometry warrants is
	// wasted work); the final solve always uses TOL_FLOOR.
	private static final double TOL_FLOOR = 1e-7;
	private static final double TOL_CEIL = 1e-3;
	// Rebuild the AMG preconditioner after this many passes even if iteration
	// counts stay healthy.
	private static final int AMG_MAX_AGE = 15;

	// Anderson acceleration of the outer radii fixed point: window size, and the
	// per-component cap (in log-radius units, so 1.0 ≈ factor e) beyond which an
	// extrapolated step is rejected in favour of the plain one.
	private static final int AA_DEPTH = 5;
	private static final double AA_MAX_STEP = 1.0;

	// External triangulation (combinatorics)
	private final Triangulation tri;

	// Geometry flag: -1 hyp, 0 eucl, +1 sph; we operate in Euclidean throughout
	// (only convert on save or additional logic if needed).
	private int hes = 0;

	// Preferred normalizing vertices
	private int alpha = -1;
	private int gamma = -1;

	// All vertices: 0..n-1
	private int n;

	// combinatoric cached info
	// interior component containing alpha
	private int[] intVerts = new int[0];
	private int intCount = 0;

	// boundary loop CCW (closed in our storage with last == first)
	private int[] bdryListClosed = new int[0];
	private int bdryCount = 0;

	// orphan verts (neither in alpha-component interiors nor boundary loop)
	private int[] orphanVerts = new int[0];
	private int orphanCount = 0;

	// mapping convenience
	private boolean[] isBoundary; // boundary marking
	private boolean hasBoundary; // whether triangulation has a boundary

	// Radii and centers (Euclidean), both working (local) and final (best-known)
	private double[] radii; // final radii (after riffle)
	private double[] centersX; // final centers
	private double[] centersY;

	private double[] localRadii; // working radii during iteration
	private double[] localCentersX; // working centers
	private double[] localCentersY;

	// aims (angle-sum targets); by default interior 2*pi, boundary negative
	// (MAX_PACK)
	private double[] vAims;

	// layout/rim sets and indexing for linear system
	private int[] layoutVerts; // interior vertices whose centers are unknowns
	private int[] rimVerts; // boundary/rim vertices with fixed centers
	private int[] v2indx; // maps original vertex -> index in (layout+rims) space (1..m or m+1..m+n_?); 0
							// means not used
	private int[] indx2v; // inverse mapping: index -> original vertex
	private int layCount; // layout vertex count
	private int rimCount; // rim vertex count (not including closure repetition)

	// Flower cache: per-vertex petal arrays copied out of the Triangulation once
	// (int[] instead of List<Integer> — the flowers are walked in every hot loop).
	private int[][] flowers;

	// Linear system for layout centers, in symmetrized (SPD graph-Laplacian)
	// form: row i of the GO transition system is scaled by conduct(v) so that
	// A = D - W with D_ii = total conductance of v and W_vw = (t1+t2)/(r_v+r_w)
	// symmetric edge weights (t's are the incircle radii of the two faces
	// sharing edge vw). RHS_i = sum over rim neighbors w of W_vw * center(w).
	// This is the same solution as the original -1-diagonal transition system
	// but symmetric positive definite, so CG applies.
	//
	// The sparsity pattern is fixed while the layout/rim sets are fixed, so the
	// structure (csrRowPtr/csrColIdx, diagonal stored first per row) is built
	// once in buildSolverPattern(); each pass only refreshes csrVal and the RHS.
	private int[] csrRowPtr; // layCount+1
	private int[] csrColIdx; // nnz
	private double[] csrVal; // nnz, refreshed each pass
	private double[] csrDiagInv; // 1/diag, refreshed each pass
	private double[] rhsX, rhsY; // refreshed each pass
	private double[] solX, solY; // solution buffers (warm-started)
	private double[] faceScratch; // incircle radii around one vertex (max degree)

	private Mode mode = Mode.MAX_PACK;

	// For polygonal mode
	private int[] corners = null; // CCW corners
	private List<int[]> sides = null; // each side: list of vertices including endpoints; CCW order

	// Monitoring
	private final List<Double> visErrMonitor = new ArrayList<>();

	// Solver state carried across riffle passes: the AMG preconditioner is reused
	// while the matrix values drift slowly (staleness only affects iteration
	// count, never correctness), and the last outer visual error drives the
	// adaptive inner tolerance.
	private AMG cachedAMG;
	private int amgAge;
	private int amgBaselineIters = -1;
	private double lastMaxVisErr = Double.MAX_VALUE;

	// Anderson acceleration toggle (on by default); see riffle()
	private boolean andersonAcceleration = true;

	// Direct Newton solve of the angle-sum equations for polygonal packings (on
	// by default); see riffle(). Its tolerances scale with the requested error:
	// angle sums to within NEWTON_ANGLE_TOL_FACTOR * error radians, and the final
	// center solve to NEWTON_LAYOUT_TOL_FACTOR * error relative residual (the
	// center solve is what limits the returned visual error; this leaves roughly
	// a 10x margin).
	private boolean newtonSolver = true;
	private static final double NEWTON_ANGLE_TOL_FACTOR = 1e-2;
	private static final double NEWTON_LAYOUT_TOL_FACTOR = 1e-4;
	private static final double NEWTON_MIN_ERROR = 1e-9;
	private static final int NEWTON_MAX_ITER = 100;

	/**
	 * Builds the engine from the given triangulation: classifies
	 * interior/boundary/orphan vertices, seeds working geometry, and defaults to
	 * {@link Mode#MAX_PACK} aims. Configure a polygonal target afterwards via
	 * {@link #setPolygonPack(int[])} or {@link #setRectanglePack()} if desired,
	 * then call {@link #riffle(double)}.
	 */
	public CircuPacker(Triangulation tri) {
		this.tri = Objects.requireNonNull(tri, "tri must not be null");

		n = tri.getVertexCount();
		if (n <= 0) {
			throw new IllegalStateException("Triangulation has no vertices");
		}

		// boundary presence
		List<Integer> bdryLoop = tri.getBoundaryLoop();
		hasBoundary = bdryLoop != null && !bdryLoop.isEmpty();

		isBoundary = new boolean[n];
		for (int v = 0; v < n; v++) {
			isBoundary[v] = tri.isBoundaryVertex(v);
		}

		alpha = pickAnInteriorVertexOrFallback();

		// Derive intVerts, bdryListClosed, and orphanVerts, similar to complex_count.m
		// logic
		deriveInteriorBoundaryOrphans();

		// Seed geometry: uniform radii, centers at origin (both are recomputed by
		// riffle; radii serve as the initial guess)
		radii = new double[n];
		Arrays.fill(radii, 0.5);
		centersX = new double[n];
		centersY = new double[n];

		localRadii = radii.clone();
		localCentersX = centersX.clone();
		localCentersY = centersY.clone();

		vAims = new double[n];
		setMaxPack();

		// Default layoutVerts = intVerts; rimVerts = boundary loop (closed)
		layoutVerts = intVerts.clone();
		layCount = layoutVerts.length;
		rimVerts = new int[bdryCount];
		System.arraycopy(bdryListClosed, 0, rimVerts, 0, bdryCount);
		rimCount = rimVerts.length;

		// indexing arrays
		buildIndexing();

		// flower cache + fixed CSR sparsity pattern for the interior solve
		cacheFlowers();
		buildSolverPattern();
	}

	private void cacheFlowers() {
		flowers = new int[n][];
		for (int v = 0; v < n; v++) {
			List<Integer> fl = tri.getFlower(v);
			int m = fl.size();
			int[] a = new int[m];
			for (int j = 0; j < m; j++) {
				a[j] = fl.get(j);
			}
			flowers[v] = a;
		}
	}

	/**
	 * Builds the fixed structural part of the layout linear system: CSR sparsity
	 * pattern (diagonal first in each row, then interior petals in flower order,
	 * matching the fill order in {@code assembleSystem}) and the per-pass value/
	 * RHS/solution buffers. Must be re-run whenever the layout/rim sets change;
	 * invalidates the cached preconditioner.
	 */
	private void buildSolverPattern() {
		csrRowPtr = new int[layCount + 1];
		int maxDeg = 1;
		for (int i = 0; i < layCount; i++) {
			int[] fl = flowers[layoutVerts[i]];
			maxDeg = Math.max(maxDeg, fl.length);
			int cnt = 1; // diagonal
			for (int w : fl) {
				int idxW = v2indx[w];
				if (0 <= idxW && idxW < layCount) {
					cnt++;
				}
			}
			csrRowPtr[i + 1] = csrRowPtr[i] + cnt;
		}

		int nnz = csrRowPtr[layCount];
		csrColIdx = new int[nnz];
		csrVal = new double[nnz];
		csrDiagInv = new double[layCount];
		rhsX = new double[layCount];
		rhsY = new double[layCount];
		solX = new double[layCount];
		solY = new double[layCount];
		faceScratch = new double[maxDeg];

		for (int i = 0; i < layCount; i++) {
			int p = csrRowPtr[i];
			csrColIdx[p++] = i; // diagonal first
			for (int w : flowers[layoutVerts[i]]) {
				int idxW = v2indx[w];
				if (0 <= idxW && idxW < layCount) {
					csrColIdx[p++] = idxW;
				}
			}
		}

		// pattern (and dimension) may have changed: cached hierarchy is invalid
		cachedAMG = null;
		amgAge = 0;
		amgBaselineIters = -1;
	}

	/**
	 * Configure classic max packing (the default): interior angle sums aim at
	 * 2&pi;, boundary radii adjust GO-style so the boundary hugs the unit circle.
	 */
	public void setMaxPack() {
		mode = Mode.MAX_PACK;
		corners = null;
		sides = null;
		Arrays.fill(vAims, 2.0 * Math.PI);
		for (int v = 0; v < n; v++) {
			if (isBoundary[v]) {
				vAims[v] = -1.0;
			}
		}
	}

	/**
	 * Configure polygonal packing with {@code numSides} corners chosen evenly
	 * spaced along the boundary loop, all corner angles equal.
	 */
	public void setPolygonPack(int numSides) {
		setPolygonPack(chooseSpacedCorners(numSides), null);
	}

	/**
	 * Configure polygonal packing with the given corner vertices (boundary
	 * vertices, in any order — they are sorted CCW along the boundary anchored at
	 * the first). Corner angles default to those of a regular n-gon,
	 * &pi;(1&minus;2/n).
	 */
	public void setPolygonPack(int[] cornersIn) {
		setPolygonPack(cornersIn, null);
	}

	/**
	 * Configure polygonal packing: the boundary is packed into an n-gon whose
	 * corners are the given boundary vertices with the given interior angles (side
	 * lengths emerge from the packing).
	 *
	 * @param cornersIn    at least 3 distinct boundary vertices; sorted CCW along
	 *                     the boundary anchored at the first supplied corner
	 * @param cornerAngles interior angle per corner in (0, &pi;), summing to
	 *                     (n&minus;2)&pi;; null for equal angles
	 */
	public void setPolygonPack(int[] cornersIn, double[] cornerAngles) {
		if (hes > 0 || !hasBoundary || bdryCount < 3) {
			throw new IllegalStateException("Polygon mode requires a boundary with at least 3 vertices.");
		}
		if (cornersIn == null || cornersIn.length < 3) {
			throw new IllegalArgumentException("Polygon mode requires at least 3 corners");
		}

		// Validate boundary membership + uniqueness
		boolean[] seen = new boolean[n];
		for (int c : cornersIn) {
			if (c < 0 || c >= n || !isBoundary[c]) {
				throw new IllegalArgumentException("Corner " + c + " is not a boundary vertex");
			}
			if (seen[c]) {
				throw new IllegalArgumentException("Duplicate corner vertex: " + c);
			}
			seen[c] = true;
		}

		// Validate corner angles if given: turning angles (pi - angle) sum to 2*pi
		if (cornerAngles != null) {
			if (cornerAngles.length != cornersIn.length) {
				throw new IllegalArgumentException("Corner angles length mismatch");
			}
			double sum = 0.0;
			for (double a : cornerAngles) {
				if (!(a > 0.0 && a < Math.PI)) {
					throw new IllegalArgumentException("Each corner angle must be in (0, pi): " + a);
				}
				sum += a;
			}
			double expected = cornersIn.length * Math.PI - 2.0 * Math.PI;
			if (Math.abs(sum - expected) > 1e-2) {
				throw new IllegalArgumentException("Corner angles inconsistent with polygon turning angles; expected sum " + expected + " but got " + sum);
			}
		}

		// Reorder corners into CCW boundary order, preserving the first supplied
		// corner as anchor
		OrderedCorners ordered = orderCornersCCW(cornersIn.clone(), cornerAngles);
		this.corners = ordered.corners;
		this.sides = derivePolygonSidesFromCorners(this.corners);
		this.mode = Mode.POLYGONAL;

		// Aims: interior 2*pi; boundary pi; corners get their angles
		Arrays.fill(vAims, 2.0 * Math.PI);
		for (int w : rimVerts) {
			vAims[w] = Math.PI;
		}
		if (ordered.angles != null) {
			for (int i = 0; i < this.corners.length; i++) {
				vAims[this.corners[i]] = ordered.angles[i];
			}
		} else {
			int m = this.corners.length;
			double equalCorner = Math.PI * (1.0 - 2.0 / m);
			for (int c : this.corners) {
				vAims[c] = equalCorner;
			}
		}
	}

	/**
	 * Configure rectangular packing with 4 corners chosen evenly spaced along the
	 * boundary loop. See {@link #setRectanglePack(int[])}.
	 */
	public void setRectanglePack() {
		setRectanglePack(chooseSpacedCorners(4));
	}

	/**
	 * Configure rectangular packing: polygonal packing with 4 corners and all
	 * corner angles &pi;/2. The aspect ratio of the resulting rectangle emerges
	 * from the packing; query it with {@link #getAspect()}.
	 */
	public void setRectanglePack(int[] fourCorners) {
		if (fourCorners == null || fourCorners.length != 4) {
			throw new IllegalArgumentException("Rectangle packing requires exactly 4 corners");
		}
		double[] rightAngles = new double[4];
		Arrays.fill(rightAngles, Math.PI / 2.0);
		setPolygonPack(fourCorners, rightAngles);
	}

	/** The current packing mode. */
	public Mode getMode() {
		return mode;
	}

	/**
	 * Enables/disables Anderson acceleration of the riffle iteration (on by
	 * default). Acceleration extrapolates the radii between passes to cut the
	 * pass count; it never affects the converged geometry (extrapolation is
	 * safeguarded and convergence is measured on unextrapolated passes), so
	 * disabling is mainly useful for benchmarking or debugging.
	 */
	public void setAndersonAcceleration(boolean enabled) {
		this.andersonAcceleration = enabled;
	}

	/**
	 * Enables/disables the direct Newton solver for polygonal packings (on by
	 * default). When enabled, {@link #riffle(double)} in {@link Mode#POLYGONAL}
	 * solves the angle-sum equations for the radii directly (Newton in
	 * log-radii) and lays out centers once at the end, instead of iterating
	 * GOPack's layout/effective-radii fixed point. Besides converging in a
	 * handful of iterations, this solves the stated problem exactly: corner
	 * angles are as prescribed and side lengths are whatever the packing makes
	 * them, whereas the fixed-point layout forces equal sides on odd polygons
	 * and equal opposite sides on even ones. It also honours requested errors
	 * below the fixed-point iteration's 1e-4 floor. Max packings, spheres and
	 * triangulations with orphan vertices always use the fixed-point
	 * iteration.
	 */
	public void setNewtonSolver(boolean enabled) {
		this.newtonSolver = enabled;
	}

	/**
	 * The CCW-ordered corner vertices in use for polygonal packing, or null in
	 * MAX_PACK mode. Useful when corners were auto-chosen.
	 */
	public int[] getCorners() {
		return corners == null ? null : corners.clone();
	}

	/**
	 * Aspect ratio (top+bottom)/(left+right) of a rectangular packing, computed
	 * from the current centers. Requires POLYGONAL mode with exactly 4 corners;
	 * call after {@link #riffle(double)}.
	 */
	public double getAspect() {
		if (mode != Mode.POLYGONAL || corners == null || corners.length != 4) {
			throw new IllegalStateException("Aspect requires polygonal mode with 4 corners");
		}
		double top = hyp(corners[1], corners[0]);
		double rend = hyp(corners[3], corners[0]);
		double lend = hyp(corners[2], corners[1]);
		double bot = hyp(corners[3], corners[2]);
		return (top + bot) / (rend + lend);
	}

	/**
	 * Iterate boundary layout, interior embedding, and effective-radii updates
	 * until the maximum relative visual error falls below the threshold (or an
	 * iteration cap is hit). Returns the number of passes run.
	 * <p>
	 * Polygonal packings use the direct Newton solver instead (see
	 * {@link #setNewtonSolver(boolean)}); the return value is then the number of
	 * Newton iterations.
	 *
	 * @param maxRelativeError in practice a lot visually lower!
	 */
	public int riffle(double maxRelativeError) {
		if (newtonSolver) {
			int iters = newtonPack(maxRelativeError);
			if (iters >= 0) {
				return iters;
			}
		}
		maxRelativeError = Math.max(1e-4, maxRelativeError);
		int pass = 0;
		double maxVis = Double.MAX_VALUE;

		// Anderson acceleration of the radii fixed point r <- g(r), where g is
		// one riffle pass. The state is log-radii: extrapolated radii stay
		// positive, and the per-pass renormalisation (all radii divided by a
		// common factor) becomes an additive shift the least-squares handles.
		// Extrapolation only alters the radii the NEXT pass starts from;
		// convergence is always measured on the plain (unextrapolated) state, so
		// the loop exits with genuine riffle geometry.
		final int nr = localRadii.length;
		AndersonAccelerator aa = andersonAcceleration ? new AndersonAccelerator(AA_DEPTH, AA_MAX_STEP) : null;
		double[] u = aa != null ? new double[nr] : null;
		double[] gu = aa != null ? new double[nr] : null;

		while (maxVis > maxRelativeError && pass < MAX_ITER) {
			if (aa != null) {
				copyLogRadii(u);
			}

			layoutBoundary();
			layoutCentersSolve(adaptiveTol());
			setEffectiveRadii();

			maxVis = updateVisErrorMonitor();
			lastMaxVisErr = maxVis;
			pass++;

			if (aa != null && maxVis > maxRelativeError && pass < MAX_ITER) {
				copyLogRadii(gu);
				double[] acc = aa.next(u, gu);
				if (acc != gu) { // identity return means "take the plain step"
					for (int v = 0; v < nr; v++) {
						localRadii[v] = Math.exp(acc[v]);
					}
				}
			}
		}

		// final consistent geometry, solved to full precision
		layoutBoundary();
		layoutCentersSolve(TOL_FLOOR);

		radii = localRadii.clone();
		centersX = localCentersX.clone();
		centersY = localCentersY.clone();
		return pass;
	}

	/**
	 * Newton path of {@link #riffle(double)} for polygonal packings: Euclidean
	 * Newton over all radii (interior aims 2&pi;, side vertices &pi;, corners
	 * their angles). The boundary is then laid out from the solved radii alone —
	 * at a solution the sides close up by themselves — and interior centers come
	 * from one GO center solve, which is exact for exact radii. Tolerances scale
	 * with the requested error, and the returned geometry is checked against it
	 * (tightening and re-solving if it falls short). Returns the Newton
	 * iteration count, or -1 if this input is not handled (the caller then falls
	 * back to the fixed-point iteration).
	 */
	private int newtonPack(double maxRelativeError) {
		if (mode != Mode.POLYGONAL || hes != 0 || !hasBoundary || orphanCount > 0 || intCount == 0) {
			return -1;
		}
		final double target = Math.max(NEWTON_MIN_ERROR, maxRelativeError);
		boolean[] active = new boolean[n];
		for (int v : intVerts) {
			active[v] = true;
		}
		for (int i = 0; i < bdryCount; i++) {
			active[bdryListClosed[i]] = true;
		}
		// every radius unknown; the solver handles the scale gauge
		CurvatureNewton cn = new CurvatureNewton(flowers, isBoundary, active, active, vAims);
		double[] r = new double[n];
		for (int v = 0; v < n; v++) {
			double rv = localRadii[v];
			r[v] = (rv > 0.0 && Double.isFinite(rv)) ? rv : 1.0;
		}

		double angleTol = NEWTON_ANGLE_TOL_FACTOR * target;
		double layoutTol = NEWTON_LAYOUT_TOL_FACTOR * target;
		int iters = 0;
		for (int attempt = 0; attempt < 3; attempt++) {
			if (!cn.solve(r, Math.max(1e-12, angleTol), NEWTON_MAX_ITER) && !(cn.residual <= 1e-6)) {
				return -1;
			}
			iters += cn.newtonIters;
			System.arraycopy(r, 0, localRadii, 0, n);
			if (isRightRectangle()) {
				setRectCenters();
			} else {
				setPolygonCentersFromRadii();
			}
			double lt = Math.max(1e-13, layoutTol);
			layoutCentersSolve(lt, lt);
			lastMaxVisErr = updateVisErrorMonitor();
			if (lastMaxVisErr <= target) {
				break;
			}
			angleTol *= 1e-2;
			layoutTol *= 1e-2;
		}

		radii = localRadii.clone();
		centersX = localCentersX.clone();
		centersY = localCentersY.clone();
		return iters;
	}

	private boolean isRightRectangle() {
		if (corners == null || corners.length != 4) {
			return false;
		}
		double bendErr = 0.0;
		for (int c : corners) {
			bendErr += Math.abs(vAims[c] - Math.PI / 2.0);
		}
		return bendErr <= 1e-5;
	}

	/**
	 * Polygon boundary layout from converged radii alone: walk the boundary with
	 * edge lengths {@code r_i + r_j}, turning by {@code π − aim} at each corner.
	 * At an exact solution the walk closes; the (roundoff-level) closure gap is
	 * spread linearly by arc length, which keeps every side straight. Then
	 * normalized like {@link #setPolygonCenters()}: corner centroid at the origin,
	 * first corner at x = 1 (even n) or y = 1 (odd n).
	 */
	private void setPolygonCentersFromRadii() {
		int numSides = corners.length;
		boolean even = numSides % 2 == 0;
		double edgeArg = even ? Math.PI : Math.PI + (Math.PI - vAims[corners[0]]) / 2.0;

		double[] arc = new double[n];
		double x = 0.0, y = 0.0, len = 0.0;
		localCentersX[corners[0]] = 0.0;
		localCentersY[corners[0]] = 0.0;
		for (int k = 0; k < numSides; k++) {
			if (k > 0) {
				edgeArg += Math.PI - vAims[corners[k]];
			}
			double dx = Math.cos(edgeArg), dy = Math.sin(edgeArg);
			int[] side = sides.get(k);
			for (int i = 0; i < side.length - 1; i++) {
				double step = localRadii[side[i]] + localRadii[side[i + 1]];
				x += step * dx;
				y += step * dy;
				len += step;
				if (k < numSides - 1 || i < side.length - 2) { // last step returns to corner 0
					localCentersX[side[i + 1]] = x;
					localCentersY[side[i + 1]] = y;
					arc[side[i + 1]] = len;
				}
			}
		}
		double gapX = x - localCentersX[corners[0]], gapY = y - localCentersY[corners[0]];
		for (int i = 0; i < bdryCount; i++) {
			int b = bdryListClosed[i];
			double f = arc[b] / len;
			localCentersX[b] -= f * gapX;
			localCentersY[b] -= f * gapY;
		}

		double avgx = 0, avgy = 0;
		for (int c : corners) {
			avgx += localCentersX[c];
			avgy += localCentersY[c];
		}
		avgx /= numSides;
		avgy /= numSides;
		double scal = even ? localCentersX[corners[0]] - avgx : localCentersY[corners[0]] - avgy;
		if (!(Math.abs(scal) > 1e-12)) {
			scal = 1.0;
		}
		scal = Math.abs(scal);
		for (int i = 0; i < bdryCount; i++) {
			int b = bdryListClosed[i];
			localCentersX[b] = (localCentersX[b] - avgx) / scal;
			localCentersY[b] = (localCentersY[b] - avgy) / scal;
		}
		for (int v = 0; v < n; v++) {
			localRadii[v] /= scal;
		}
	}

	/****
	 * <p>
	 * Computes, for each interior vertex, the maximum "relative visual error" (RVE)
	 * across all incident edges and returns those maxima in an array.
	 * </p>
	 *
	 * <p>
	 * For a given interior vertex <code>v = layoutVerts[i]</code>, each neighbor
	 * <code>w</code> in v's flower is inspected and the edge RVE is computed as:
	 * </p>
	 *
	 * <pre>
	 * RVE(v,w) = | |z_v - z_w| - (r_v + r_w) | / r_v
	 * </pre>
	 *
	 * <ul>
	 * <li><code>z_v, z_w</code> are Euclidean centers
	 * (<code>localCentersX</code>/<code>localCentersY</code>).</li>
	 * <li><code>r_v, r_w</code> are Euclidean radii (<code>localRadii</code>).</li>
	 * <li><code>|z_v - z_w|</code> is the center-to-center distance (computed with
	 * <code>Math.hypot</code>).</li>
	 * </ul>
	 *
	 * <p>
	 * The per-vertex value is the maximum <code>RVE(v,w)</code> over all neighbors
	 * <code>w</code> of <code>v</code>. The returned array contains those
	 * per-vertex maxima in the same order as <code>layoutVerts</code>.
	 * </p>
	 *
	 * <h4>Units</h4>
	 * <p>
	 * Dimensionless (ratio). Interpretation examples:
	 * </p>
	 * <ul>
	 * <li><code>0.00</code> = perfect tangency on all incident edges (ideal).</li>
	 * <li><code>0.01</code> ≈ 1% of v's radius (very good).</li>
	 * <li><code>0.10</code> ≈ 10% of v's radius (noticeable).</li>
	 * <li><code>1.00</code> ≈ mismatch comparable to v's radius (poor).</li>
	 * </ul>
	 *
	 * <h4>Notes</h4>
	 * <ul>
	 * <li>Only vertices listed in <code>layoutVerts</code> (interior/layout
	 * vertices) are processed.</li>
	 * <li>Boundary-to-boundary edges are not directly included unless the boundary
	 * vertex is a neighbor of an interior vertex.</li>
	 * <li>To avoid division by zero when <code>r_v</code> is extremely small, the
	 * denominator uses <code>Math.max(1e-16, r_v)</code>. Very small radii can
	 * cause the ratio to spike.</li>
	 * </ul>
	 *
	 * @return an array of length <code>layCount</code> where element <code>i</code>
	 *         is the maximum relative visual error for vertex
	 *         <code>layoutVerts[i]</code> (dimensionless).
	 */
	public double[] visualErrors() {
		double[] errs = new double[layCount];
		for (int i = 0; i < layCount; i++) {
			int v = layoutVerts[i];
			double zvx = localCentersX[v], zvy = localCentersY[v];
			double rv = localRadii[v];
			int[] flower = flowers[v];
			double maxErr = 0.0;
			for (int w : flower) {
				double dx = zvx - localCentersX[w];
				double dy = zvy - localCentersY[w];
				double cdiff = Math.sqrt(dx * dx + dy * dy);
				double rdiff = rv + localRadii[w];
				double me = Math.abs(cdiff - rdiff) / Math.max(1e-16, rv);
				if (me > maxErr) {
					maxErr = me;
				}
			}
			errs[i] = maxErr;
		}
		return errs;
	}

	public double[] angleSumErrors() {
		// diffs(k) = anglesum(v)-2*pi for interior layout vertices
		double target = -2.0 * Math.PI;
		double[] diffs = new double[layCount];
		for (int i = 0; i < layCount; i++) {
			int v = layoutVerts[i];
			double r = localRadii[v];
			int[] flower = flowers[v];
			double diff = target;
			int m = flower.length;
			for (int j = 0; j < m; j++) {
				int w = flower[j];
				int u = flower[(j + 1) % m];
				double cosang = MathUtil.cosAngle(r, localRadii[w], localRadii[u]);
				diff += Math.acos(Math.max(-1.0, Math.min(1.0, cosang)));
			}
			diffs[i] = diff;
		}
		return diffs;
	}

	/**
	 * <p>
	 * Remove orphan vertices (those not in the interior component and not on the
	 * rim).
	 * </p>
	 *
	 * <p>
	 * What:
	 * </p>
	 * <ul>
	 * <li>Constructs a trimmed mapping that preserves the alpha-component interiors
	 * and the boundary, discarding orphan vertices and updating radii/centers
	 * arrays accordingly.</li>
	 * <li>Recomputes combinatorics and index mappings after removal.</li>
	 * </ul>
	 *
	 * <p>
	 * Why:
	 * </p>
	 * <p>
	 * Orphans complicate layout and may produce artifacts; pruned complexes are
	 * smaller and lead to simpler linear systems. This is an optional
	 * transformation that mutates internal arrays to operate on the reduced
	 * complex.
	 * </p>
	 */
	public void pruneOrphans() {
		// If no orphans, nothing to do
		if (orphanCount == 0) {
			return;
		}

		// Remove orphan vertices from triangulation view within the engine
		// We don't mutate Triangulation; we adjust our working sets and geometry
		// arrays.
		int newN = intCount + bdryCount; // keep alpha-component interiors and boundary
		boolean[] keep = new boolean[n];
		for (int v : intVerts) {
			keep[v] = true;
		}
		for (int i = 0; i < bdryCount; i++) {
			keep[bdryListClosed[i]] = true;
		}

		int[] old2new = new int[n];
		Arrays.fill(old2new, -1);
		int kk = 0;
		for (int v = 0; v < n; v++) {
			if (keep[v]) {
				old2new[v] = kk++;
			}
		}

		// Rebuild arrays
		double[] nr = new double[newN];
		double[] nx = new double[newN];
		double[] ny = new double[newN];
		for (int v = 0; v < n; v++) {
			if (keep[v]) {
				int nv = old2new[v];
				nr[nv] = localRadii[v];
				nx[nv] = localCentersX[v];
				ny[nv] = localCentersY[v];
			}
		}
		localRadii = nr;
		localCentersX = nx;
		localCentersY = ny;
		radii = nr.clone();
		centersX = nx.clone();
		centersY = ny.clone();

		// Rebuild interior/boundary indices
		int[] nInt = new int[intCount];
		for (int i = 0; i < intCount; i++) {
			nInt[i] = old2new[intVerts[i]];
		}
		int[] nBdryClosed = new int[bdryCount + 1];
		for (int i = 0; i < bdryCount + 1; i++) {
			nBdryClosed[i] = old2new[bdryListClosed[i]];
		}

		intVerts = nInt;
		intCount = nInt.length;
		bdryListClosed = nBdryClosed;
		bdryCount = nBdryClosed.length - 1;
		orphanVerts = new int[0];
		orphanCount = 0;

		// Layout sets
		layoutVerts = intVerts.clone();
		layCount = layoutVerts.length;
		rimVerts = new int[bdryCount];
		System.arraycopy(bdryListClosed, 0, rimVerts, 0, bdryCount);
		rimCount = rimVerts.length;

		buildIndexing(); // rebuild v2indx/indx2v
		buildSolverPattern(); // layout set changed: rebuild pattern, drop preconditioner
	}

	/** Final radii (valid after {@link #riffle(double)}). */
	public double[] getRadii() {
		return radii;
	}

	/** Final center x-coordinates (valid after {@link #riffle(double)}). */
	public double[] getCentersX() {
		return centersX;
	}

	/** Final center y-coordinates (valid after {@link #riffle(double)}). */
	public double[] getCentersY() {
		return centersY;
	}

	/** Max relative visual error recorded after each riffle pass. */
	public List<Double> getVisErrMonitor() {
		return Collections.unmodifiableList(visErrMonitor);
	}

	// Getters for testing
	public double[] getLocalRadii() {
		return localRadii;
	}

	public double[] getLocalCentersX() {
		return localCentersX;
	}

	public double[] getLocalCentersY() {
		return localCentersY;
	}

	public int[] getLayoutVerts() {
		return layoutVerts;
	}

	public int[] getRimVerts() {
		return rimVerts;
	}

	public int[] getIntVerts() {
		return intVerts;
	}

	public int[] getBdryLoopClosed() {
		return bdryListClosed;
	}

	private int[] chooseSpacedCorners(int sideN) {
		if (bdryCount < 3) {
			throw new IllegalStateException("Boundary must have at least 3 vertices");
		}
		sideN = Math.max(3, Math.min(sideN, bdryCount));

		int[] crn = new int[sideN];
		int step = Math.max(1, bdryCount / sideN);

		int start = 0;
		if (gamma >= 0) {
			for (int i = 0; i < bdryCount; i++) {
				if (bdryListClosed[i] == gamma) {
					start = i;
					break;
				}
			}
		}

		for (int e = 0; e < sideN; e++) {
			crn[e] = bdryListClosed[(start + e * step) % bdryCount];
		}
		return crn;
	}

	private int pickAnInteriorVertexOrFallback() {
		// Try provided interior vertices list
		List<Integer> ints = tri.getInteriorVertices();
		if (ints != null && !ints.isEmpty()) {
			return ints.get(0);
		}
		// Else pick any vertex whose flower is cyclic (interior)
		for (int v = 0; v < n; v++) {
			if (!isBoundary[v]) {
				return v;
			}
		}
		// fallback: just 0 if nothing else (we'll treat as sphere)
		return 0;
	}

	private void deriveInteriorBoundaryOrphans() {
		List<Integer> bdryLoop = tri.getBoundaryLoop();
		if (bdryLoop == null || bdryLoop.isEmpty()) {
			// Sphere case: no boundary
			hasBoundary = false;
			hes = 1; // spherical
			// Choose a "faux" boundary of 3 vertices: take far vertex a from alpha (BFS
			// depth),
			// then its first two neighbors as b and c
			int a = farVertex(Collections.singletonList(alpha));
			List<Integer> fl = tri.getFlower(a);
			int b = fl.get(1 % fl.size());
			int c = fl.get(0);
			bdryListClosed = new int[] { a, b, c, a };
			bdryCount = 3;

			// All others are interiors
			List<Integer> ints = new ArrayList<>();
			for (int v = 0; v < n; v++) {
				if (v != a && v != b && v != c) {
					ints.add(v);
				}
			}
			intVerts = ints.stream().mapToInt(Integer::intValue).toArray();
			intCount = intVerts.length;

			// orphans empty
			orphanVerts = new int[0];
			orphanCount = 0;
			gamma = a;
			return;
		}

		hasBoundary = true;
		hes = 0;

		// Use BFS to find interior component containing alpha
		boolean[] touchedInterior = new boolean[n];
		Queue<Integer> q = new ArrayDeque<>();
		if (!isBoundary[alpha]) {
			q.add(alpha);
			touchedInterior[alpha] = true;
		}

		while (!q.isEmpty()) {
			int v = q.poll();
			for (int w : tri.getFlower(v)) {
				if (!isBoundary[w] && !touchedInterior[w]) {
					touchedInterior[w] = true;
					q.add(w);
				}
			}
		}

		// Collect intVerts (alpha component)
		List<Integer> ints = new ArrayList<>();
		for (int v = 0; v < n; v++) {
			if (!isBoundary[v] && touchedInterior[v]) {
				ints.add(v);
			}
		}
		intVerts = ints.stream().mapToInt(Integer::intValue).toArray();
		intCount = intVerts.length;

		// Build bdry loop closed (from Triangulation)
		bdryCount = tri.getBoundaryLoop().size();
		bdryListClosed = new int[bdryCount + 1];
		for (int i = 0; i < bdryCount; i++) {
			bdryListClosed[i] = tri.getBoundaryLoop().get(i);
		}
		bdryListClosed[bdryCount] = bdryListClosed[0];

		// Orphans: not in intVerts and not in boundary loop
		boolean[] isInInt = new boolean[n];
		for (int v : intVerts) {
			isInInt[v] = true;
		}
		boolean[] isInB = new boolean[n];
		for (int i = 0; i < bdryCount; i++) {
			isInB[bdryListClosed[i]] = true;
		}

		List<Integer> orph = new ArrayList<>();
		for (int v = 0; v < n; v++) {
			if (!isInInt[v] && !isInB[v]) {
				orph.add(v);
			}
		}
		orphanVerts = orph.stream().mapToInt(Integer::intValue).toArray();
		orphanCount = orphanVerts.length;

		// pick gamma if not provided
		if (gamma < 0 || gamma >= n || !isBoundary[gamma]) {
			gamma = bdryListClosed[0];
		}
	}

	private int farVertex(List<Integer> seeds) {
		if (seeds == null || seeds.isEmpty()) {
			return (n > 0 ? 0 : -1);
		}
		int[] marks = new int[n]; // 0 = unvisited
		Queue<Integer> q = new ArrayDeque<>();
		for (int s : seeds) {
			q.add(s);
			marks[s] = 1;
		}
		int far = seeds.get(0);
		int depth = 1;
		while (!q.isEmpty() && depth < 10) {
			int sz = q.size();
			for (int i = 0; i < sz; i++) {
				int v = q.poll();
				far = v;
				for (int w : tri.getFlower(v)) {
					if (marks[w] == 0) {
						marks[w] = marks[v] + 1;
						q.add(w);
					}
				}
			}
			depth++;
		}
		return far;
	}

	/**
	 * <p>
	 * Build index mappings used for matrix assembly and neighbor lookups.
	 * </p>
	 *
	 * <p>
	 * What:
	 * </p>
	 * <ul>
	 * <li>Creates {@code indx2v} (sequential indices: layout verts then rim
	 * verts).</li>
	 * <li>Creates {@code v2indx} mapping original vertex -> index in the combined
	 * list.</li>
	 * <li>Sets {@code layCount} and {@code rimCount} used by solver and update
	 * routines.</li>
	 * </ul>
	 *
	 * <p>
	 * Why:
	 * </p>
	 * <p>
	 * Sparse matrix assembly and RHS construction require a compact contiguous
	 * indexing; this method centralizes that translation so the rest of the code
	 * works with small dense/sparse structures indexed 0..m-1.
	 * </p>
	 */
	private void buildIndexing() {
		// indx2v: layout first, then rim vertices
		indx2v = new int[layCount + rimCount];
		v2indx = new int[n];
		Arrays.fill(v2indx, -1);
		for (int i = 0; i < layCount; i++) {
			indx2v[i] = layoutVerts[i];
			v2indx[layoutVerts[i]] = i;
		}
		for (int j = 0; j < rimCount; j++) {
			indx2v[layCount + j] = rimVerts[j];
			v2indx[rimVerts[j]] = layCount + j;
		}
	}

	/**
	 * <p>
	 * Place boundary centers according to the current packing mode.
	 * </p>
	 *
	 * <p>
	 * What:
	 * </p>
	 * <ul>
	 * <li>Dispatches to {@code setHoroCenters}, {@code setPolyCenters}, or
	 * {@code setRectCenters} depending on the engine {@code mode} and polygon
	 * metadata.</li>
	 * <li>May also scale {@code localRadii} to match a normalized boundary (e.g.,
	 * unit circle).</li>
	 * </ul>
	 *
	 * <p>
	 * Why:
	 * </p>
	 * <p>
	 * Boundary placement is mode-specific: max-pack uses horocycles in the unit
	 * disc, polygonal mode places centers on a normalized n-gon. Centralizing the
	 * choice avoids duplicated logic and ensures consistent scaling before interior
	 * layout.
	 * </p>
	 */
	private void layoutBoundary() {
		if (mode == Mode.POLYGONAL) {
			setPolygonCenters();
		} else {
			setHoroCenters();
		}
	}

	/**
	 * <p>
	 * Lay out boundary circles as horocycles inside the unit circle for MAX_PACK.
	 * </p>
	 *
	 * <p>
	 * What:
	 * </p>
	 * <ul>
	 * <li>If only three boundary vertices: place them symmetrically and set aims to
	 * zero.</li>
	 * <li>Otherwise: compute a scalar {@code R} by Newton iteration so that the
	 * angles formed by tangent circles sum to 2π, then scale radii by 1/R.</li>
	 * <li>Position boundary centers consecutively along the unit circle using the
	 * computed turning angles; the first center is placed on the positive imaginary
	 * axis.</li>
	 * </ul>
	 *
	 * <p>
	 * Why:
	 * </p>
	 * <p>
	 * This is the standard initialization for disc-style max packing: boundary
	 * circles are placed so that when interpreted as horocycles their tangency
	 * geometry matches the intended discrete turning angles, providing a stable
	 * starting layout for interior Tutte-style embedding.
	 * </p>
	 */
	private void setHoroCenters() {
		// Special case: only 3 boundary vertices => equilateral symmetric
		if (bdryCount <= 3) {
			double s3 = Math.sqrt(3);
			double brad = s3 / (2.0 + s3);
			int v1 = bdryListClosed[0];
			int v2 = bdryListClosed[1];
			int v3 = bdryListClosed[2];
			localRadii[v1] = brad;
			localRadii[v2] = brad;
			localRadii[v3] = brad;
			vAims[v1] = 0;
			vAims[v2] = 0;
			vAims[v3] = 0;
			// centers equally spaced on circle radius (1-brad)
			double R = 1.0 - brad;
			localCentersX[v1] = 0.0;
			localCentersY[v1] = +R;
			localCentersX[v2] = -R * Math.sqrt(3) / 2.0;
			localCentersY[v2] = -R * 0.5;
			localCentersX[v3] = +R * Math.sqrt(3) / 2.0;
			localCentersY[v3] = -R * 0.5;
			return;
		}

		// Newton iteration to solve for circle around unit circle
		// r[j] = localRadii[bdryList[j]]
		double[] r = new double[bdryCount + 1];
		double sum = 0.0, minrad = 0.0;
		for (int i = 0; i < bdryCount; i++) {
			r[i] = localRadii[bdryListClosed[i]];
			minrad = Math.max(minrad, r[i]);
			sum += r[i];
		}
		r[bdryCount] = r[0];
		double R = sum / Math.PI;
		if (R < 2.0 * minrad) {
			R = 3.0 * minrad;
		}

		int trys = 0;
		while (trys < 100) {
			trys++;
			double f = -2.0 * Math.PI;
			double fp = 0.0;
			for (int j = 0; j < bdryCount; j++) {
				double r1 = r[j], r2 = r[j + 1];
				double Rrr = R - r1 - r2;
				double RRrr = R * Rrr;
				double ab = r1 * r2;
				double val = (RRrr - ab) / (RRrr + ab);
				val = Math.max(-1.0, Math.min(1.0, val));
				f += Math.acos(val);
				// derivative approx (as in MATLAB)
				double denom = (RRrr + ab);
				double num = -1.0 * (R + Rrr) * Math.sqrt(Math.max(1e-16, ab / RRrr));
				fp += num / denom;
			}
			double newR = R - f / fp;
			newR = Math.max(R / 2.0, Math.min(2.0 * R, newR));
			if (Math.abs(newR - R) < 1e-5) {
				R = newR;
				break;
			}
			R = newR;
		}

		// Scale all radii by 1/R
		for (int v = 0; v < n; v++) {
			localRadii[v] /= R;
		}
		for (int j = 0; j <= bdryCount; j++) {
			r[j] /= R;
		}

		// Place boundary centers around unit circle, first on +i axis
		int v1 = bdryListClosed[0];
		localCentersX[v1] = 0.0;
		localCentersY[v1] = 1.0 - r[0];
		double arg = Math.PI / 2.0;
		double rPrev = r[0];
		for (int k = 1; k < bdryCount; k++) {
			double r2 = r[k];
			double RRrr = 1.0 - rPrev - r2;
			double ab = rPrev * r2;
			double val = (RRrr - ab) / (RRrr + ab);
			val = Math.max(-1.0, Math.min(1.0, val));
			double delta = Math.acos(val);
			arg += delta;
			double d = 1.0 - r2;
			int vk = bdryListClosed[k];
			localCentersX[vk] = d * Math.cos(arg);
			localCentersY[vk] = d * Math.sin(arg);
			rPrev = r2;
		}
	}

	/**
	 * <p>
	 * Place boundary centers and scale radii for polygonal packing (general n-gon).
	 * </p>
	 *
	 * <p>
	 * What:
	 * </p>
	 * <ul>
	 * <li>Computes current side lengths from {@code localRadii}, derives target
	 * side lengths (triangles by law of sines, even-odd strategies otherwise), and
	 * scales radii.</li>
	 * <li>Computes edge directions from corner aims and lays out each side
	 * incrementally, then recenters and rescales so a normalization condition holds
	 * (first corner at 1+i or i).</li>
	 * </ul>
	 *
	 * <p>
	 * Why:
	 * </p>
	 * <p>
	 * Polygonal packing requires boundary centers to sit on a specific polygonal
	 * shape whose side lengths are consistent with circle diameters; this method
	 * enforces those proportions and normalization so interior embedding solves
	 * behave predictably.
	 * </p>
	 */
	private void setPolygonCenters() {
		if (corners == null || sides == null) {
			throw new IllegalStateException("Polygonal mode without corners; configure via setPolygonPack/setRectanglePack");
		}

		// if rectangle (4 corners) with all right angles, do rectangular layout
		if (corners.length == 4) {
			double bendErr = 0.0;
			for (int c : corners) {
				bendErr += Math.abs(vAims[c] - Math.PI / 2.0);
			}
			if (bendErr <= 1e-5) {
				setRectCenters();
				return;
			}
		}

		// general polygon: set side lengths from radii; compute target side lengths
		int numSides = corners.length;

		double[] sideLengths = new double[numSides];
		double fullLength = 0.0;
		double[] targetLength = new double[numSides];

		for (int i = 0; i < numSides; i++) {
			int[] side = sides.get(i);
			double L = localRadii[side[0]] + localRadii[side[side.length - 1]];
			for (int j = 1; j < side.length - 1; j++) {
				L += 2.0 * localRadii[side[j]];
			}
			sideLengths[i] = L;
			fullLength += L;
		}

		int halfn = numSides / 2;

		if (numSides == 3) {
			// triangle with corner aims provided in vAims at corners
			double opp1 = vAims[corners[2]];
			double opp2 = vAims[corners[0]];
			if (opp1 <= 0 || opp2 <= 0 || (opp1 + opp2) >= Math.PI) {
				throw new IllegalStateException("Invalid triangle corner aims: " + opp2 + ", " + vAims[corners[1]] + ", " + opp1);
			}
			double opp3 = Math.PI - (opp1 + opp2);
			targetLength[0] = 1.0;
			targetLength[1] = Math.sin(opp2) / Math.sin(opp1);
			targetLength[2] = Math.sin(opp3) / Math.sin(opp1);
			double sum = targetLength[0] + targetLength[1] + targetLength[2];
			double factor = sum / fullLength;
			scaleAllLocalBy(factor);
			for (int i = 0; i < numSides; i++) {
				sideLengths[i] *= factor;
			}
		} else if (2 * halfn == numSides) {
			// even: pair opposite sides, target total per pair equals average; set scale so
			// sum target ~ 6
			double factor = 6.0 / fullLength;
			scaleAllLocalBy(factor);
			for (int i = 0; i < numSides; i++) {
				sideLengths[i] *= factor;
			}
			for (int j = 0; j < halfn; j++) {
				targetLength[j] = (sideLengths[j] + sideLengths[halfn + j]) / 2.0;
				targetLength[halfn + j] = targetLength[j];
			}
		} else {
			// odd: make sides equal to 2*sin(pi/n)
			double spn = 2.0 * Math.sin(Math.PI / numSides);
			double factor = numSides * spn / fullLength;
			scaleAllLocalBy(factor);
			for (int i = 0; i < numSides; i++) {
				sideLengths[i] *= factor;
				targetLength[i] = spn;
			}
		}

		// Layout rules:
		// - Odd: first corner at (0,1), first edge direction is pi + (pi - aim1)/2
		// - Even: first corner at (1,1), first edge horizontal left (pi)
		double[] edgeArg = new double[numSides];
		if (2 * halfn != numSides) {
			edgeArg[0] = Math.PI + (Math.PI - vAims[corners[0]]) / 2.0;
		} else {
			edgeArg[0] = Math.PI;
		}
		for (int j = 1; j < numSides; j++) {
			edgeArg[j] = edgeArg[j - 1] + Math.PI - vAims[corners[j]];
		}

		double[] dirX = new double[numSides];
		double[] dirY = new double[numSides];
		for (int i = 0; i < numSides; i++) {
			dirX[i] = Math.cos(edgeArg[i]);
			dirY[i] = Math.sin(edgeArg[i]);
		}

		if (2 * halfn == numSides) {
			localCentersX[corners[0]] = 1.0;
			localCentersY[corners[0]] = 1.0;
		} else {
			localCentersX[corners[0]] = 0.0;
			localCentersY[corners[0]] = 1.0;
		}

		for (int k = 0; k < numSides; k++) {
			int[] side = sides.get(k);
			double factor = targetLength[k] / Math.max(1e-16, sideLengths[k]);
			int start = corners[k];
			localCentersX[side[0]] = localCentersX[start];
			localCentersY[side[0]] = localCentersY[start];

			double prev = localRadii[start];
			double spotX = localCentersX[start];
			double spotY = localCentersY[start];

			for (int i = 0; i < side.length - 1; i++) {
				int nxt = side[i + 1];
				double next = localRadii[nxt];
				spotX += factor * dirX[k] * (prev + next);
				spotY += factor * dirY[k] * (prev + next);
				localCentersX[nxt] = spotX;
				localCentersY[nxt] = spotY;
				prev = next;
			}
		}

		// center polygon: translate by centroid of corners
		double avgx = 0, avgy = 0;
		for (int c : corners) {
			avgx += localCentersX[c];
			avgy += localCentersY[c];
		}
		avgx /= corners.length;
		avgy /= corners.length;
		for (int v = 0; v < n; v++) {
			localCentersX[v] -= avgx;
			localCentersY[v] -= avgy;
		}

		// scale: even => make corner[0].x = 1; odd => make corner[0].y = 1
		double scal = (2 * halfn == numSides) ? Math.max(1e-16, localCentersX[corners[0]]) : Math.max(1e-16, localCentersY[corners[0]]);
		for (int v = 0; v < n; v++) {
			localCentersX[v] /= scal;
			localCentersY[v] /= scal;
			localRadii[v] /= scal;
		}
	}

	/**
	 * <p>
	 * Specialized layout for rectangular packings (four sides, right-angled
	 * corners).
	 * </p>
	 *
	 * <p>
	 * What:
	 * </p>
	 * <ul>
	 * <li>Computes averaged top/bottom and left/right side lengths and an aspect
	 * ratio.</li>
	 * <li>Scales {@code localRadii} to fit a rectangle centered at origin with y =
	 * ±1 and x = ±aspect.</li>
	 * <li>Distributes boundary centers proportionally along each rectangle
	 * edge.</li>
	 * </ul>
	 *
	 * <p>
	 * Why:
	 * </p>
	 * <p>
	 * Rectangles are a common practical target; handling them explicitly yields
	 * better aspect preservation and more stable side-length matching than the
	 * general polygon routine.
	 * </p>
	 */
	private void setRectCenters() {
		// sides.size() == 4; corners count == 4
		double[] sideLen = new double[4];
		for (int i = 0; i < 4; i++) {
			int[] side = sides.get(i);
			double L = localRadii[side[0]] + localRadii[side[side.length - 1]];
			for (int j = 1; j < side.length - 1; j++) {
				L += 2.0 * localRadii[side[j]];
			}
			sideLen[i] = L;
		}
		double width = (sideLen[0] + sideLen[2]) / 2.0;
		double height = (sideLen[1] + sideLen[3]) / 2.0;
		double aspect = height / Math.max(1e-16, width);

		// scale radii so that total fits rectangle of width 2*aspect and height 2
		double factor = 2.0 * (aspect + 1.0) / Math.max(1e-16, (width + height));
		scaleAllLocalBy(factor);
		for (int i = 0; i < 4; i++) {
			sideLen[i] *= factor;
		}

		// rectangle corners: (1,aspect), (-1,aspect), (-1,-aspect), (1,-aspect)
		double[] crx = { 1.0, -1.0, -1.0, 1.0 };
		double[] cry = { aspect, aspect, -aspect, -aspect };
		double[] dirx = { -1.0, 0.0, 1.0, 0.0 };
		double[] diry = { 0.0, -1.0, 0.0, 1.0 };
		double[] slength = { 2.0, 2.0 * aspect, 2.0, 2.0 * aspect };

		for (int k = 0; k < 4; k++) {
			int[] side = sides.get(k);
			double sf = slength[k] / Math.max(1e-16, sideLen[k]);

			int start = corners[k];
			double spotX = crx[k];
			double spotY = cry[k];
			localCentersX[side[0]] = spotX;
			localCentersY[side[0]] = spotY;

			// place only the side's internal vertices: each corner stays exactly at
			// its rectangle corner (set when its own side starts)
			double prev = localRadii[start];
			for (int i = 0; i < side.length - 2; i++) {
				int nxt = side[i + 1];
				double next = localRadii[nxt];
				spotX += sf * dirx[k] * (prev + next);
				spotY += sf * diry[k] * (prev + next);
				localCentersX[nxt] = spotX;
				localCentersY[nxt] = spotY;
				prev = next;
			}
		}
	}

	private void scaleAllLocalBy(double factor) {
		for (int v = 0; v < n; v++) {
			localRadii[v] *= factor;
		}
	}

	private List<int[]> derivePolygonSidesFromCorners(int[] orderedCorners) {
		if (orderedCorners == null || orderedCorners.length < 3) {
			throw new IllegalArgumentException("Need at least 3 corners");
		}

		List<Integer> loop = Arrays.stream(bdryListClosed, 0, bdryCount).boxed().collect(Collectors.toList());
		List<int[]> result = new ArrayList<>(orderedCorners.length);

		for (int i = 0; i < orderedCorners.length; i++) {
			int a = orderedCorners[i];
			int b = orderedCorners[(i + 1) % orderedCorners.length];

			int ia = boundaryPosition(a);
			if (ia < 0) {
				throw new IllegalStateException("Corner " + a + " not found in boundary loop");
			}

			List<Integer> side = new ArrayList<>();
			int k = ia;
			side.add(loop.get(k));

			while (loop.get(k) != b) {
				k = (k + 1) % bdryCount;
				side.add(loop.get(k));
				if (side.size() > bdryCount + 1) {
					throw new IllegalStateException("Side extraction overflow");
				}
			}

			result.add(side.stream().mapToInt(Integer::intValue).toArray());
		}

		return result;
	}

	private void rebuildAMG(SparseCSR A) {
		cachedAMG = new AMG(A.n, A.rowPtr, A.colIdx, A.val);
		amgAge = 0;
		amgBaselineIters = -1;
	}

	/**
	 * Inner-solve tolerance for the current pass: proportional to the outer
	 * visual error (loose while the radii are still far off), clamped to
	 * [{@code TOL_FLOOR}, {@code TOL_CEIL}].
	 */
	private double adaptiveTol() {
		return Math.max(TOL_FLOOR, Math.min(TOL_CEIL, 0.01 * lastMaxVisErr));
	}

	private void copyLogRadii(double[] dst) {
		for (int v = 0; v < dst.length; v++) {
			dst[v] = Math.log(localRadii[v]);
		}
	}

	/** Seed the solve with the previous pass's interior centers (warm start). */
	private void seedWarmStart(double[] solx, double[] soly) {
		for (int i = 0; i < layCount; i++) {
			int v = layoutVerts[i];
			solx[i] = localCentersX[v];
			soly[i] = localCentersY[v];
		}
	}

	private void layoutCentersSolve(double tol) {
		layoutCentersSolve(tol, TOL_FLOOR);
	}

	private void layoutCentersSolve(double tol, double tolFloor) {
		assembleSystem();

		SparseCSR A = new SparseCSR(layCount, csrRowPtr[layCount], csrRowPtr, csrColIdx, csrVal, csrDiagInv);

		// Reuse the AMG hierarchy across passes: matrix values drift slowly, so a
		// slightly stale preconditioner still works; rebuild on age or when
		// iteration counts creep up (flagged below).
		if (cachedAMG == null || amgAge >= AMG_MAX_AGE) {
			rebuildAMG(A);
		}

		int maxIters = Math.max(1000, 10 * layCount);
		seedWarmStart(solX, solY);

		// writes to solX+solY
		var r = CGSolver.solve2(A, rhsX, rhsY, solX, solY, tol, tolFloor, maxIters, cachedAMG);

		if (!r.converged()) {
			// A stale preconditioner may be at fault (or a breakdown polluted the
			// iterate): rebuild from the current matrix and retry once from the
			// warm start.
			rebuildAMG(A);
			seedWarmStart(solX, solY);
			r = CGSolver.solve2(A, rhsX, rhsY, solX, solY, tol, tolFloor, maxIters, cachedAMG);
		}
		if (!r.converged()) {
			// last resort: BiCGStab tolerates numerical loss of definiteness
			seedWarmStart(solX, solY);
			BiCGStabSolver.solve(A, rhsX, solX, tol, maxIters, cachedAMG);
			BiCGStabSolver.solve(A, rhsY, solY, tol, maxIters, cachedAMG);
		}

		if (amgBaselineIters < 0) {
			amgBaselineIters = Math.max(1, r.iters);
		} else if (r.iters > 2 * amgBaselineIters + 5) {
			amgAge = AMG_MAX_AGE; // convergence degrading: force rebuild next pass
		}
		amgAge++;

		// Copy results back
		for (int i = 0; i < layCount; i++) {
			int v = layoutVerts[i];
			localCentersX[v] = solX[i];
			localCentersY[v] = solY[i];
		}
	}

	/**
	 * <p>
	 * Refreshes {@code csrVal}, {@code csrDiagInv} and the RHS from the current
	 * {@code localRadii}/{@code localCenters}, writing into the fixed pattern
	 * built by {@code buildSolverPattern()} (diagonal first per row, then
	 * interior petals in flower order).
	 * </p>
	 *
	 * <p>
	 * For each layout vertex v with petals w_j: the incircle radius of face j is
	 * {@code t_j = sqrt(r_v*r_wj*r_wj+1 / (r_v+r_wj+r_wj+1))}, and the edge
	 * weight to petal w_j is {@code W_vwj = (t_{j-1}+t_j)/(r_v+r_wj)} — symmetric
	 * in v,w because the incircle sum belongs to the edge. Row v of the system is
	 * {@code diag = sum_j W_vwj} (all petals), {@code offdiag = -W_vw} for
	 * interior petals, and {@code rhs = sum W_vw * center(w)} over rim petals:
	 * the SPD form of the GO transition system (each row is the original row
	 * scaled by -conduct(v)).
	 * </p>
	 */
	private void assembleSystem() {
		final double[] t = faceScratch;
		for (int i = 0; i < layCount; i++) {
			int v = layoutVerts[i];
			int rowStart = csrRowPtr[i];
			double rv = localRadii[v];
			int[] fl = flowers[v];
			int m = fl.length;
			if (m == 0) {
				// isolated vertex: pin its center to the origin
				csrVal[rowStart] = 1.0;
				csrDiagInv[i] = 1.0;
				rhsX[i] = 0.0;
				rhsY[i] = 0.0;
				continue;
			}

			// incircle radii of the m faces around v (face j = petals j, j+1)
			double rw = localRadii[fl[0]];
			for (int j = 0; j < m; j++) {
				double ru = localRadii[fl[(j + 1) % m]];
				t[j] = Math.sqrt(Math.max(0.0, rv * ru * rw / Math.max(1e-16, rv + ru + rw)));
				rw = ru;
			}

			double diag = 0.0;
			double bx = 0.0, by = 0.0;
			int p = rowStart + 1; // slot 0 is the diagonal
			double prevT = t[m - 1];
			for (int j = 0; j < m; j++) {
				int w = fl[j];
				double coeff = (prevT + t[j]) / Math.max(1e-16, rv + localRadii[w]);
				prevT = t[j];
				diag += coeff;

				int idxW = v2indx[w];
				if (0 <= idxW && idxW < layCount) {
					csrVal[p++] = -coeff; // interior neighbor
				} else {
					// rim (fixed-center) neighbor contributes to RHS
					bx += coeff * localCentersX[w];
					by += coeff * localCentersY[w];
				}
			}
			csrVal[rowStart] = Math.max(1e-16, diag);
			csrDiagInv[i] = 1.0 / csrVal[rowStart];
			rhsX[i] = bx;
			rhsY[i] = by;
		}
	}

	/**
	 * <p>
	 * Set {@code localRadii} to effective radii derived from current centers (port
	 * of {@code setEffective.m}).
	 * </p>
	 *
	 * <ul>
	 * <li>Positive aim (interiors; boundary vertices in polygonal mode): compute
	 * sector areas from neighboring centers and set r_eff = sqrt(area /
	 * (vAims(v)/2)).</li>
	 * <li>Negative aim (boundary in max packing): GO-style update r_eff =
	 * sqrt(2*area/angsum), averaged with the old radius to moderate
	 * oscillation.</li>
	 * <li>Aims near zero (e.g. the 3-boundary-vertex disc case): radius left
	 * unchanged.</li>
	 * </ul>
	 */
	private void setEffectiveRadii() {
		// ---------------- interior ----------------
		for (int i = 0; i < layCount; i++) {
			int v = layoutVerts[i];
			double targetArea = vAims[v] / 2.0;
			if (targetArea <= 1e-3) {
				continue;
			}

			double area = sectorAreaAt(v);
			if (!(area > 0.0) || !Double.isFinite(area)) {
				continue;
			}

			double newr = Math.sqrt(area / targetArea);
			if (newr > 0.0 && Double.isFinite(newr)) {
				localRadii[v] = newr;
			}
		}

		// ---------------- boundary ----------------
		for (int k = 0; k < bdryCount; k++) {
			int w = bdryListClosed[k];
			double targetArea = vAims[w] / 2.0;

			int[] fl = flowers[w]; // open flower
			int m = fl.length;
			if (m < 2) {
				continue;
			}

			double area = 0.0;
			double angsum = 0.0;
			double zx = localCentersX[w], zy = localCentersY[w];

			for (int j = 0; j < m - 1; j++) {
				int jr = fl[j];
				int jl = fl[j + 1];

				double ang = MathUtil.angleAtCorner(zx, zy, localCentersX[jr], localCentersY[jr], localCentersX[jl], localCentersY[jl]);

				double r = 0.5 * (hyp(jr, w) + hyp(jl, w) - hyp(jr, jl));
				if (r > 0.0 && ang > 0.0 && Double.isFinite(r) && Double.isFinite(ang)) {
					area += 0.5 * r * r * ang;
					angsum += ang;
				}
			}

			if (!(area > 0.0) || !(angsum > 0.0) || !Double.isFinite(area) || !Double.isFinite(angsum)) {
				continue;
			}

			// Positive aim (polygonal mode): match sector area to the target.
			// Negative aim (max packing): GO-style update, averaged to moderate.
			double newr;
			if (targetArea > 1e-3) {
				newr = Math.sqrt(area / targetArea);
				if (newr > 0.0 && Double.isFinite(newr)) {
					localRadii[w] = newr;
				}
			} else if (targetArea < -1e-3) {
				newr = Math.sqrt(2.0 * area / Math.max(1e-16, angsum));
				if (newr > 0.0 && Double.isFinite(newr)) {
					localRadii[w] = 0.5 * (newr + localRadii[w]);
				}
			}
		}
	}

	private double hyp(int a, int b) {
		double dx = localCentersX[a] - localCentersX[b];
		double dy = localCentersY[a] - localCentersY[b];
		return Math.sqrt(dx * dx + dy * dy);
	}

	private double sectorAreaAt(int v) {
		int[] fl = flowers[v];
		int m = fl.length;
		double zx = localCentersX[v], zy = localCentersY[v];
		double area = 0.0;
		for (int j = 0; j < m; j++) {
			int jr = fl[j];
			int jl = fl[(j + 1) % m];

			double xjr = localCentersX[jr], yjr = localCentersY[jr];
			double xjl = localCentersX[jl], yjl = localCentersY[jl];

			double dxr = xjr - zx, dyr = yjr - zy;
			double dxl = xjl - zx, dyl = yjl - zy;
			double dxrl = xjr - xjl, dyrl = yjr - yjl;

			double ang = MathUtil.angleAtCorner(zx, zy, xjr, yjr, xjl, yjl);

			double a = Math.sqrt(dxr * dxr + dyr * dyr);
			double b = Math.sqrt(dxl * dxl + dyl * dyl);
			double c = Math.sqrt(dxrl * dxrl + dyrl * dyrl);

			double r = 0.5 * (a + b - c);
			area += 0.5 * r * r * ang;
		}
		return area;
	}

	private double updateVisErrorMonitor() {
		double[] ve = visualErrors();
		double max = 0.0;
		for (double x : ve) {
			max = Math.max(max, x);
		}
		visErrMonitor.add(max);
		return max;
	}

	private static final class OrderedCorners {
		final int[] corners;
		final double[] angles;

		OrderedCorners(int[] corners, double[] angles) {
			this.corners = corners;
			this.angles = angles;
		}
	}

	private OrderedCorners orderCornersCCW(int[] rawCorners, double[] rawAngles) {
		final int m = rawCorners.length;

		int[] pos = new int[m];
		for (int i = 0; i < m; i++) {
			pos[i] = boundaryPosition(rawCorners[i]);
			if (pos[i] < 0) {
				throw new IllegalArgumentException("Corner " + rawCorners[i] + " is not on the boundary loop");
			}
		}

		Integer[] ord = new Integer[m];
		for (int i = 0; i < m; i++) {
			ord[i] = i;
		}
		Arrays.sort(ord, Comparator.comparingInt(i -> pos[i]));

		// Rotate so the first user-supplied corner remains first
		int anchor = 0;
		for (int k = 0; k < m; k++) {
			if (rawCorners[ord[k]] == rawCorners[0]) {
				anchor = k;
				break;
			}
		}

		int[] outCorners = new int[m];
		double[] outAngles = (rawAngles == null) ? null : new double[m];

		for (int t = 0; t < m; t++) {
			int src = ord[(anchor + t) % m];
			outCorners[t] = rawCorners[src];
			if (outAngles != null) {
				outAngles[t] = rawAngles[src];
			}
		}

		return new OrderedCorners(outCorners, outAngles);
	}

	private int boundaryPosition(int v) {
		for (int i = 0; i < bdryCount; i++) {
			if (bdryListClosed[i] == v) {
				return i;
			}
		}
		return -1;
	}
}