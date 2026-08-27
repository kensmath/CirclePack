package ftnTheory;

import java.util.ArrayList;
import java.util.Vector;

import allMains.CirclePack;
import combinatorics.komplex.DcelFace;
import complex.Complex;
import geometry.SphericalMath;
import listManip.FaceLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;

/**
 * @brief Directly solve the spherical angle-sum system by damped Gauss-Newton.
 *
 * CirclePack has no in-situ spherical repacker: {@code SphPacker} only builds
 * MAXIMAL packings (puncture, pack in the hyperbolic plane, project).  Naive
 * spherical Perron does not converge because angle-sum monotonicity fails in
 * positive curvature.  This extender instead solves F(r)=0 directly, where
 * <pre>
 *     F_v(r) = anglesum_v(r) - aim_v        (v interior)
 * </pre>
 * using Newton's method.  The spherical angle sum is computed here in the
 * numerically stable half-angle form
 *     alpha = 2*atan( sqrt( sin(b) sin(c) / ( sin(a) sin(a+b+c) ) ) )
 * (angle at center circle radius a, tangent petals b,c), with a closed-form
 * analytic Jacobian.  This avoids the cancellation / acos ill-conditioning of
 * the law-of-cosines form on near-degenerate triples, which is what lets the
 * solve converge from a cold seed rather than stalling on the approach path.
 *
 * The radius-only angle-sum system is conformally degenerate: its Jacobian has
 * nullity exactly 3 (radii are invariant under the rotation subgroup SO(3), so
 * only the non-isometric conformal directions PSL(2,C)/SO(3) move radii).  We
 * remove that freedom by holding THREE "big circles" fixed; the remaining
 * square system is nonsingular and Newton converges quadratically.  Different
 * seeds converge to the distinct branched packings (e.g. the two degree-3
 * Blaschke/polar solutions of Crane's example).
 *
 * Commands:
 *   bigcircles [-n k] [-l v1 v2 ...]  choose/report the fixed circles
 *   solve      [-p maxits] [-t tol]   run Gauss-Newton from current radii
 *   residual                          report current max |anglesum - aim|
 *
 * @author Jim Ashe (with Claude Opus 4.8)
 */
public class SphBranchNewton extends PackExtender {

	int[] bigCircles;          // the fixed "big circle" vertices
	int numFixed = 3;          // conformal nullity is 3 -> fix 3

	public SphBranchNewton(PackData p) {
		super(p);
		extensionType = "SPH_BRANCH_NEWTON";
		extensionAbbrev = "sn";
		toolTip = "'SphBranchNewton': direct damped-Newton (Levenberg-Marquardt) "
				+ "solve of the spherical angle-sum system";
		registerXType();
		if (extenderPD.hes <= 0)
			errorMsg("packing is not spherical; use 'geom_to_s' first");
		msg("ready [build: STABLE angle-sum + analytic Jacobian, 2026-08-07]. "
				+ "'|sn| solve' runs damped Newton from the current radii; "
				+ "seed from a packing near a branched solution.");
		running = true;
		extenderPD.packExtensions.add(this);
	}

	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items;

		// ========= bigcircles =========
		if (cmd.startsWith("bigcirc")) {
			try {
				if (flagSegs != null)
				for (Vector<String> seg : flagSegs) {
					if (seg.isEmpty())
						continue;
					String f = seg.get(0);
					if (f.equals("-n"))
						numFixed = Integer.parseInt(seg.get(1));
					else if (f.equals("-l")) {
						ArrayList<Integer> vs = new ArrayList<Integer>();
						for (int i = 1; i < seg.size(); i++)
							vs.add(Integer.parseInt(seg.get(i)));
						bigCircles = new int[vs.size()];
						for (int i = 0; i < vs.size(); i++)
							bigCircles[i] = vs.get(i);
						numFixed = bigCircles.length;
					}
				}
				if (bigCircles == null || bigCircles.length != numFixed)
					bigCircles = chooseBigCircles(numFixed);
				reportBigCircles();
			} catch (Exception ex) {
				errorMsg("bigcircles: bad arguments");
				return 0;
			}
			return 1;
		}

		// ========= residual =========
		if (cmd.startsWith("resid")) {
			double e = maxResidual();
			msg(String.format("max |anglesum - aim| = %.6e", e));
			return 1;
		}

		// ========= winding =========
		if (cmd.startsWith("wind")) {
			int[] ring = defaultBallBearings();
			boolean relayout = false;
			try {
				if (flagSegs != null)
				for (Vector<String> seg : flagSegs) {
					if (seg.isEmpty())
						continue;
					String f = seg.get(0);
					if (f.equals("-L"))
						relayout = true;
					else if (f.equals("-r")) {
						ring = new int[seg.size() - 1];
						for (int i = 1; i < seg.size(); i++)
							ring[i - 1] = Integer.parseInt(seg.get(i));
					}
				}
			} catch (Exception ex) {
				errorMsg("winding: bad arguments");
				return 0;
			}
			if (ring == null) {
				errorMsg("winding: give the ball-bearing ring with -r v1 v2 ...");
				return 0;
			}
			if (relayout)
				cpCommand("layout");
			StringBuilder rb = new StringBuilder();
			for (int rv : ring)
				rb.append(rv + " ");
			msg("winding ring = the ball-bearing circles [" + rb.toString().trim()
					+ "] (their centers form the ring; use -r to change)");
			double w = computeWinding(ring);
			msg(String.format("ball-bearing winding = %+.4f  (rounded %d)",
					w, Math.round(w)));
			return 1;
		}

		// ========= windfaces (set the winding face-chain as CP's Flist) =========
		if (cmd.startsWith("windface") || cmd.startsWith("wface")) {
			int[] ring = defaultBallBearings();
			if (ring == null) {
				errorMsg("windfaces: needs the default ball bearings 77..84");
				return 0;
			}
			FaceLink fl = new FaceLink(extenderPD);
			int L = ring.length;
			for (int i = 0; i < L; i++) {
				int b = ring[i], nb = ring[(i + 1) % L];
				int[] sh = sharedNeighbors(b, nb);
				if (sh.length < 2)
					continue;
				addFace(fl, b, sh[0], sh[1]);
				addFace(fl, nb, sh[0], sh[1]);
			}
			extenderPD.flist = fl;
			msg("winding face-chain set as 'Flist' (" + fl.size() + " faces). "
					+ "Display filled with:  Disp -f -ffc218 Flist");
			return 1;
		}

		// ========= solve =========
		// ========= newton (damped Newton / LM only) =========
		if (cmd.startsWith("newton")) {
			int[] its = { 60 };
			double[] tl = { 1e-10 };
			ArrayList<Integer> fixed = parseNewtonFlags(flagSegs, its, tl);
			return newton(its[0], tl[0], fixed);
		}

		// ========= perron (develop only; container held fixed, no polish) =========
		if (cmd.startsWith("perron")) {
			int[] kp = { 24, 5000 };
			ArrayList<Integer> clist = parsePerronFlags(flagSegs, kp);
			return perron(kp[0], kp[1], clist);
		}

		// ========= solve / discover (combined: Perron develop + Newton polish) =========
		if (cmd.startsWith("solve") || cmd.startsWith("discover")) {
			int[] kp = { 24, 5000 };
			int[] its = { 80 };
			double[] tl = { 1e-10 };
			ArrayList<Integer> clist = parsePerronFlags(flagSegs, kp);
			parseNewtonFlags(flagSegs, its, tl);   // -l here is the Perron container, not a Newton anchor
			msg("solve = Perron (develop) + Newton (polish)");
			perron(kp[0], kp[1], clist);
			return newton(its[0], tl[0], null);
		}

		return super.cmdParser(cmd, flagSegs);
	}

	/** Parse -i maxits, -t tol into its[0], tl[0]; -l returns circles to HOLD FIXED (or null). */
	ArrayList<Integer> parseNewtonFlags(Vector<Vector<String>> flagSegs, int[] its, double[] tl) {
		if (flagSegs == null)
			return null;
		ArrayList<Integer> fixed = null;
		try {
			for (Vector<String> seg : flagSegs) {
				if (seg.isEmpty())
					continue;
				String f = seg.get(0);
				if (f.equals("-i"))
					its[0] = Integer.parseInt(seg.get(1));
				else if (f.equals("-t"))
					tl[0] = Double.parseDouble(seg.get(1));
				else if (f.equals("-l")) {   // hold these circles fixed (anchor)
					Vector<String> sub = new Vector<String>(seg.subList(1, seg.size()));
					NodeLink nl = new NodeLink(extenderPD, sub);
					fixed = new ArrayList<Integer>();
					if (nl != null)
						for (int v : nl)
							fixed.add(v);
				}
			}
		} catch (Exception ex) {
			errorMsg("bad -i/-t/-l argument");
		}
		return fixed;
	}

	/** Parse -k K, -n passes into kp[0],kp[1]; -l returns an explicit container (or null). */
	ArrayList<Integer> parsePerronFlags(Vector<Vector<String>> flagSegs, int[] kp) {
		if (flagSegs == null)
			return null;
		ArrayList<Integer> clist = null;
		try {
			for (Vector<String> seg : flagSegs) {
				if (seg.isEmpty())
					continue;
				String f = seg.get(0);
				if (f.equals("-k"))
					kp[0] = Integer.parseInt(seg.get(1));
				else if (f.equals("-n"))
					kp[1] = Integer.parseInt(seg.get(1));
				else if (f.equals("-l")) {   // -l vlist | v1 v2 ... | a(i j)
					Vector<String> sub = new Vector<String>(seg.subList(1, seg.size()));
					NodeLink nl = new NodeLink(extenderPD, sub);
					clist = new ArrayList<Integer>();
					if (nl != null)
						for (int v : nl)
							clist.add(v);
				}
			}
		} catch (Exception ex) {
			errorMsg("bad -k/-n/-l argument");
		}
		return clist;
	}

	// ---------------------------------------------------------------
	// Gauss-Newton
	// ---------------------------------------------------------------

	/**
	 * @brief Levenberg-Marquardt on F(r)=0 over all interior radii.
	 *
	 * No circles are fixed: the damping term lambda*I regularizes the 3-dim
	 * conformal nullity (radii are SO(3)-invariant), so the step is well-defined
	 * and flows to the nearest branched solution on the 3-manifold.  Seed from a
	 * packing near a solution (a univalent packing is NOT in a branched basin).
	 * The 'bigcircles' command remains available to ISOLATE a solution for
	 * uniqueness studies.
	 * @return 1 if converged below tol, else 0
	 */
	public int newton(int maxits, double tol, ArrayList<Integer> fixedList) {
		if (extenderPD.hes <= 0) {
			errorMsg("packing is not spherical");
			return 0;
		}
		int N = extenderPD.nodeCount;

		// all interior constrained vertices are unknowns
		ArrayList<Integer> rows = new ArrayList<Integer>();
		int[] rowIndex = new int[N + 1];
		for (int v = 1; v <= N; v++)
			rowIndex[v] = -1;
		for (int v = 1; v <= N; v++) {
			if (!extenderPD.isBdry(v) && extenderPD.getAim(v) > 0.0) {
				rowIndex[v] = rows.size();
				rows.add(v);
			}
		}
		int n = rows.size();
		if (n == 0) {
			errorMsg("no interior vertices with positive aim");
			return 0;
		}
		msg("newton: " + n + " equations/unknowns, Levenberg-Marquardt");

		// columns (radii) to HOLD FIXED (anchor): their Newton step is forced to 0,
		// which removes conformal-nullity directions and greatly stabilizes the solve.
		// The fixed circles keep their current (e.g. set_rad) radii; all rows remain
		// as residual equations, so the free radii solve to satisfy them.
		boolean[] colFixed = new boolean[n];
		int nfix = 0;
		if (fixedList != null) {
			for (int v : fixedList) {
				if (v >= 1 && v <= N && rowIndex[v] >= 0 && !colFixed[rowIndex[v]]) {
					colFixed[rowIndex[v]] = true;
					nfix++;
				}
			}
			if (nfix > 0)
				msg("  holding " + nfix + " circle(s) fixed (anchor): " + fixedList);
		}

		double lam = 1e-3;
		double normF = Double.MAX_VALUE;
		int it;
		for (it = 1; it <= maxits; it++) {
			double[] F = residualVector(rows);
			normF = infNorm(F);
			if (it <= 3 || it % 5 == 0)
				msg(String.format("  it %2d: max|F| = %.3e  (lam=%.1e)",
						it, normF, lam));
			if (normF < tol)
				break;

			double[][] J = jacobian(rows, rows, rowIndex);   // cols = rows (all free)
			// normal equations A = J^T J,  g = J^T F
			double[][] A = new double[n][n];
			double[] g = new double[n];
			for (int i = 0; i < n; i++) {
				double[] Ji = J[i];
				double Fi = F[i];
				for (int a = 0; a < n; a++) {
					double Jia = Ji[a];
					if (Jia == 0.0)
						continue;
					g[a] += Jia * Fi;
					double[] Aa = A[a];
					for (int b = 0; b < n; b++)
						Aa[b] += Jia * Ji[b];
				}
			}

			// hold anchored circles fixed: zero their row/col so the step dx = 0
			if (nfix > 0) {
				for (int c = 0; c < n; c++) {
					if (colFixed[c]) {
						for (int a = 0; a < n; a++) {
							A[c][a] = 0.0;
							A[a][c] = 0.0;
						}
						A[c][c] = 1.0;
						g[c] = 0.0;
					}
				}
			}

			double base = twoNorm(F);
			double[] r0 = new double[n];
			for (int a = 0; a < n; a++)
				r0[a] = extenderPD.getRadius(rows.get(a));

			// adaptive damping: raise lambda until a valid step reduces ||F||
			boolean stepped = false;
			for (int tries = 0; tries < 40; tries++) {
				double[] dx = choleskyDamped(A, negate(g), lam);
				if (dx != null) {
					for (int a = 0; a < n; a++)
						extenderPD.setRadius(rows.get(a), r0[a] + dx[a]);
					if (validTriples(rows)
							&& twoNorm(residualVector(rows)) < base) {
						lam = Math.max(lam * 0.5, 1e-12);
						stepped = true;
						break;
					}
					for (int a = 0; a < n; a++) // undo
						extenderPD.setRadius(rows.get(a), r0[a]);
				}
				lam *= 3.0;
			}
			if (!stepped) {
				msg(String.format("  no productive step at it %d (max|F|=%.3e); "
						+ "seed may be outside a solution basin", it, normF));
				break;
			}
		}

		if (normF < tol) {
			msg(String.format("CONVERGED in %d iters, max|F| = %.3e", it, normF));
			return 1;
		}
		msg(String.format("did NOT converge (max|F| = %.3e)", normF));
		return 0;
	}

	/**
	 * @brief Perron DISCOVERY of a branched packing (Thurston fixed-boundary style).
	 *
	 * Unlike solve() (damped Newton), which only CERTIFIES a packing already near a
	 * solution, this can FIND a branched packing from a generic seed (e.g. the
	 * unbranched maximal packing).  The K largest circles are held fixed as a
	 * "container"; every other circle is relaxed by the classic per-vertex Perron
	 * step: set r(v) to the FIRST radius whose spherical angle sum crosses its aim
	 * from above (angsum(r) starts at deg*pi as r->0 and decreases).  At a branch
	 * vertex (aim 4*pi) this actively DRIVES the circle to collapse, developing the
	 * cone point -- exactly what Newton's global least-squares step will not do.
	 * Sweeps (Gauss-Seidel, in place) until every free angle sum meets its aim.
	 * @return 1 if converged, else 0
	 */
	public int perron(int K, int maxpass, ArrayList<Integer> explicit) {
		if (extenderPD.hes <= 0) {
			errorMsg("packing is not spherical");
			return 0;
		}
		int N = extenderPD.nodeCount;
		boolean[] fixed = new boolean[N + 1];
		int cnt = 0;
		if (explicit != null && !explicit.isEmpty()) {
			// explicit container from -l (a vlist or vertex spec)
			for (int v : explicit)
				if (v >= 1 && v <= N && !fixed[v]) {
					fixed[v] = true;
					cnt++;
				}
		} else {
			// default container = the K largest interior circles, held fixed
			Integer[] order = new Integer[N];
			for (int i = 0; i < N; i++)
				order[i] = i + 1;
			java.util.Arrays.sort(order, (a, b) ->
				Double.compare(extenderPD.getRadius(b), extenderPD.getRadius(a)));
			for (int i = 0; i < N && cnt < K; i++) {
				if (!extenderPD.isBdry(order[i])) {
					fixed[order[i]] = true;
					cnt++;
				}
			}
		}
		ArrayList<Integer> free = new ArrayList<Integer>();
		for (int v = 1; v <= N; v++)
			if (!fixed[v] && !extenderPD.isBdry(v) && extenderPD.getAim(v) > 0)
				free.add(v);
		if (free.isEmpty()) {
			errorMsg("perron: no free vertices (container too large)");
			return 0;
		}
		msg("perron: container = " + cnt + " largest circles fixed; "
				+ free.size() + " free (Thurston fixed-boundary style)");

		double tol = 1e-9, err = Double.MAX_VALUE;
		int pass;
		for (pass = 1; pass <= maxpass; pass++) {
			for (int v : free) {
				double r = firstCross(v, extenderPD.getAim(v));
				if (r > 0)
					extenderPD.setRadius(v, r);
			}
			err = 0;
			for (int v : free) {
				err = Math.max(err, Math.abs(sphAngleSum(v) - extenderPD.getAim(v)));
			}
			if (pass <= 3 || pass % 200 == 0)
				msg(String.format("  pass %d: max|F| = %.3e", pass, err));
			if (err < tol)
				break;
		}
		// Perron only relaxed the FREE vertices; the fixed container no longer
		// fits the developed packing, so a residual remains at the container
		// circles.  Follow with 'newton' (or use 'solve') to release + polish.
		msg(String.format("perron: developed branch structure -- free-vertex "
				+ "residual %.2e in %d passes. Container circles are now off; "
				+ "run '|sn| newton' (or use '|sn| solve') to polish to a solution.",
				err, pass));
		return 1;
	}

	/** Smallest radius where angsum(v,r) crosses target from above (the packing branch). */
	double firstCross(int v, double target) {
		double hm = SphericalMath.sph_rad_max(extenderPD, v) * (1 - 1e-9);
		if (hm <= 1e-9)
			return extenderPD.getRadius(v);
		double lo = 1e-9, r = Math.min(0.005, hm), hi;
		while (true) {
			if (sphAngleSum(v, r) - target <= 0) {
				hi = r;
				break;
			}
			lo = r;
			if (r >= hm * (1 - 1e-12))
				return extenderPD.getRadius(v);       // no crossing (frustrated)
			r = Math.min(r * 1.3, hm * (1 - 1e-12));
		}
		for (int i = 0; i < 60; i++) {
			double m = 0.5 * (lo + hi);
			if (sphAngleSum(v, m) - target <= 0)
				hi = m;
			else
				lo = m;
		}
		return 0.5 * (lo + hi);
	}

	/** Residual F_i = anglesum(rows[i]) - aim(rows[i]). */
	double[] residualVector(ArrayList<Integer> rows) {
		double[] F = new double[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			int v = rows.get(i);
			F[i] = sphAngleSum(v) - extenderPD.getAim(v);
		}
		return F;
	}

	// ---------------------------------------------------------------
	// Spherical angle sum -- STABLE half-angle form + analytic Jacobian.
	//
	// For the angle at the center circle (radius a) between two petals tangent
	// to it and to each other (radii b, c), the spherical half-angle identity
	// gives   alpha = 2*atan( sqrt( sin(b) sin(c) / ( sin(a) sin(a+b+c) ) ) ).
	// This avoids the catastrophic cancellation and acos ill-conditioning of the
	// law-of-cosines form near-degenerate triples (a big circle beside tiny ones,
	// or a+b+c -> pi), so the residual and its derivative stay accurate along the
	// whole Newton path -- which is what lets discovery converge from a cold seed.
	// (Verified against the law-of-cosines form at the balanced solution and via
	// finite differences for the Jacobian.)
	// ---------------------------------------------------------------

	/** Stable spherical angle at center circle (radius a) between tangent petals b,c. */
	static double faceAngle(double a, double b, double c) {
		double sig = a + b + c;
		double sa = Math.sin(a), ss = Math.sin(sig);
		if (sa <= 0.0 || ss <= 0.0)
			return Math.PI;                    // degenerate (a+b+c >= pi): angle -> pi
		double T = (Math.sin(b) * Math.sin(c)) / (sa * ss);
		if (T <= 0.0)
			return Math.PI;
		return 2.0 * Math.atan(Math.sqrt(T));
	}

	/** {alpha, d/da, d/db, d/dc} for faceAngle (closed-form analytic gradient). */
	static double[] faceAngleGrad(double a, double b, double c) {
		double sig = a + b + c;
		double sa = Math.sin(a), sb = Math.sin(b), sc = Math.sin(c), ss = Math.sin(sig);
		if (sa <= 0.0 || ss <= 0.0)
			return new double[] { Math.PI, 0.0, 0.0, 0.0 };
		double T = (sb * sc) / (sa * ss);
		if (T <= 0.0)
			return new double[] { Math.PI, 0.0, 0.0, 0.0 };
		double sqT = Math.sqrt(T);
		double alpha = 2.0 * Math.atan(sqT);
		double f = sqT / (1.0 + T);
		double cota = 1.0 / Math.tan(a), cotb = 1.0 / Math.tan(b),
				cotc = 1.0 / Math.tan(c), cots = 1.0 / Math.tan(sig);
		return new double[] { alpha,
				f * (-cota - cots),      // d/da  (center circle)
				f * (cotb - cots),       // d/db
				f * (cotc - cots) };     // d/dc
	}

	/** Distinct petals of v in cyclic order (drops any closing repeat). */
	int[] petalsOf(int v) {
		int[] fl = extenderPD.packDCEL.vertices[v].getFlower(false);
		int d = fl.length;
		if (d > 1 && fl[0] == fl[d - 1]) {
			int[] out = new int[d - 1];
			System.arraycopy(fl, 0, out, 0, d - 1);
			return out;
		}
		return fl;
	}

	/** Angle sum at v, using rvOverride for r(v) (petals at current radii). */
	double sphAngleSum(int v, double rvOverride) {
		int[] pet = petalsOf(v);
		int np = pet.length;
		double sum = 0.0;
		for (int i = 0; i < np; i++) {
			double rp = extenderPD.getRadius(pet[i]);
			double rw = extenderPD.getRadius(pet[(i + 1) % np]);
			sum += faceAngle(rvOverride, rp, rw);
		}
		return sum;
	}

	/** Angle sum at v at its current radius. */
	double sphAngleSum(int v) {
		return sphAngleSum(v, extenderPD.getRadius(v));
	}

	/**
	 * @brief Analytic Jacobian J[i][j] = dF_{rows[i]}/dr_{cols[j]}. Row v's angle
	 * sum depends on r(v) and its petals; each face (v, a, b) adds d/da to column v
	 * and d/db, d/dc to columns a, b (accumulated -- a petal appears in two faces).
	 */
	double[][] jacobian(ArrayList<Integer> rows, ArrayList<Integer> cols,
			int[] rowIndex) {
		int N = extenderPD.nodeCount;
		int[] colIndex = new int[N + 1];
		for (int v = 0; v <= N; v++)
			colIndex[v] = -1;
		for (int j = 0; j < cols.size(); j++)
			colIndex[cols.get(j)] = j;
		double[][] J = new double[rows.size()][cols.size()];
		for (int i = 0; i < rows.size(); i++) {
			int v = rows.get(i);
			double rv = extenderPD.getRadius(v);
			int[] pet = petalsOf(v);
			int np = pet.length;
			for (int k = 0; k < np; k++) {
				int a = pet[k], b = pet[(k + 1) % np];
				double[] gc = faceAngleGrad(rv, extenderPD.getRadius(a),
						extenderPD.getRadius(b));
				if (colIndex[v] >= 0)
					J[i][colIndex[v]] += gc[1];
				if (colIndex[a] >= 0)
					J[i][colIndex[a]] += gc[2];
				if (colIndex[b] >= 0)
					J[i][colIndex[b]] += gc[3];
			}
		}
		return J;
	}

	// ---------------------------------------------------------------
	// big-circle selection
	// ---------------------------------------------------------------

	/** Pick the k largest interior circles, pairwise >= 2 combinatorial hops. */
	int[] chooseBigCircles(int k) {
		int N = extenderPD.nodeCount;
		Integer[] order = new Integer[N];
		for (int i = 0; i < N; i++)
			order[i] = i + 1;
		java.util.Arrays.sort(order, (a, b) ->
			Double.compare(extenderPD.getRadius(b), extenderPD.getRadius(a)));
		ArrayList<Integer> chosen = new ArrayList<Integer>();
		for (int idx = 0; idx < N && chosen.size() < k; idx++) {
			int v = order[idx];
			if (extenderPD.isBdry(v))
				continue;
			boolean ok = true;
			for (int c : chosen)
				if (hopsLE(v, c, 2)) {
					ok = false;
					break;
				}
			if (ok)
				chosen.add(v);
		}
		int[] out = new int[chosen.size()];
		for (int i = 0; i < chosen.size(); i++)
			out[i] = chosen.get(i);
		return out;
	}

	/** True if b is within maxhops of a (small BFS over flowers). */
	boolean hopsLE(int a, int b, int maxhops) {
		if (a == b)
			return true;
		boolean[] seen = new boolean[extenderPD.nodeCount + 1];
		ArrayList<Integer> frontier = new ArrayList<Integer>();
		seen[a] = true;
		frontier.add(a);
		for (int h = 0; h < maxhops; h++) {
			ArrayList<Integer> next = new ArrayList<Integer>();
			for (int u : frontier)
				for (int w : extenderPD.packDCEL.vertices[u].getFlower(false)) {
					if (w == b)
						return true;
					if (!seen[w]) {
						seen[w] = true;
						next.add(w);
					}
				}
			frontier = next;
		}
		return false;
	}

	void reportBigCircles() {
		StringBuilder sb = new StringBuilder("fixed big circles: ");
		for (int b : bigCircles)
			sb.append(b + "(r=" + String.format("%.4f", extenderPD.getRadius(b)) + ") ");
		msg(sb.toString());
	}

	// ---------------------------------------------------------------
	// small utilities: validity, norms, Cholesky
	// ---------------------------------------------------------------

	/** True if every incident triple of every row vertex sums to < pi. */
	boolean validTriples(ArrayList<Integer> rows) {
		for (int v : rows) {
			double rv = extenderPD.getRadius(v);
			if (rv <= 0)
				return false;
			int deg = extenderPD.countFaces(v);
			int prev = extenderPD.getFirstPetal(v);
			double rp = extenderPD.getRadius(prev);
			for (int k = 1; k <= deg; k++) {
				int w = extenderPD.getPetal(v, k);
				double rw = extenderPD.getRadius(w);
				if (rv + rp + rw >= Math.PI)
					return false;
				rp = rw;
			}
		}
		return true;
	}

	double maxResidual() {
		double mx = 0.0;
		for (int v = 1; v <= extenderPD.nodeCount; v++) {
			if (extenderPD.isBdry(v) || extenderPD.getAim(v) <= 0)
				continue;
			double e = Math.abs(sphAngleSum(v) - extenderPD.getAim(v));
			if (e > mx)
				mx = e;
		}
		return mx;
	}

	static double infNorm(double[] x) {
		double m = 0;
		for (double v : x)
			m = Math.max(m, Math.abs(v));
		return m;
	}

	static double twoNorm(double[] x) {
		double s = 0;
		for (double v : x)
			s += v * v;
		return Math.sqrt(s);
	}

	static double[] negate(double[] x) {
		double[] y = new double[x.length];
		for (int i = 0; i < x.length; i++)
			y[i] = -x[i];
		return y;
	}

	/**
	 * @brief Solve SPD system A x = b by Cholesky, with a growing ridge
	 * fallback if A is not quite positive-definite. Returns null on failure.
	 */
	static double[] choleskySolve(double[][] A, double[] b) {
		int n = A.length;
		for (double ridge = 0.0, bump = 1e-12; ; ridge = (ridge == 0 ? bump : ridge * 10)) {
			double[][] L = new double[n][n];
			boolean ok = true;
			for (int i = 0; i < n && ok; i++) {
				for (int j = 0; j <= i; j++) {
					double sum = A[i][j] + (i == j ? ridge : 0.0);
					for (int k = 0; k < j; k++)
						sum -= L[i][k] * L[j][k];
					if (i == j) {
						if (sum <= 0) {
							ok = false;
							break;
						}
						L[i][j] = Math.sqrt(sum);
					} else
						L[i][j] = sum / L[j][j];
				}
			}
			if (!ok) {
				if (ridge > 1e-2)
					return null;   // hopeless
				continue;
			}
			// forward/back substitution
			double[] y = new double[n];
			for (int i = 0; i < n; i++) {
				double s = b[i];
				for (int k = 0; k < i; k++)
					s -= L[i][k] * y[k];
				y[i] = s / L[i][i];
			}
			double[] x = new double[n];
			for (int i = n - 1; i >= 0; i--) {
				double s = y[i];
				for (int k = i + 1; k < n; k++)
					s -= L[k][i] * x[k];
				x[i] = s / L[i][i];
			}
			return x;
		}
	}

	/** Solve (A + lam*I) x = b by Cholesky; returns null if not pos-def. */
	static double[] choleskyDamped(double[][] A, double[] b, double lam) {
		int n = A.length;
		double[][] L = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j <= i; j++) {
				double sum = A[i][j] + (i == j ? lam : 0.0);
				for (int k = 0; k < j; k++)
					sum -= L[i][k] * L[j][k];
				if (i == j) {
					if (sum <= 0)
						return null;
					L[i][j] = Math.sqrt(sum);
				} else
					L[i][j] = sum / L[j][j];
			}
		}
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			double s = b[i];
			for (int k = 0; k < i; k++)
				s -= L[i][k] * y[k];
			y[i] = s / L[i][i];
		}
		double[] x = new double[n];
		for (int i = n - 1; i >= 0; i--) {
			double s = y[i];
			for (int k = i + 1; k < n; k++)
				s -= L[k][i] * x[k];
			x[i] = s / L[i][i];
		}
		return x;
	}

	// ---------------------------------------------------------------
	// ball-bearing winding number (Mobius invariant: Blaschke 3, polar 1)
	// ---------------------------------------------------------------

	/** Default ball-bearing ring for Crane's doubled complex: verts 77..84. */
	int[] defaultBallBearings() {
		if (extenderPD.nodeCount < 84)
			return null;
		int[] r = new int[8];
		for (int i = 0; i < 8; i++)
			r[i] = 77 + i;
		return r;
	}

	/**
	 * @brief Winding number of the ball-bearing chain about its own ring axis.
	 * Frame-independent: the axis is the smallest-variance direction of the
	 * ring's center-vectors, so the count is Mobius-frame independent.
	 * @param ring ball-bearing vertices in cyclic order
	 * @return signed winding (near an integer: 3 Blaschke, 1 polar)
	 */
	double computeWinding(int[] ring) {
		int L = ring.length;
		// Chain of FACES (triangles), each including a ball bearing: for consecutive
		// ball bearings sharing mains m1,m2, take faces (b,m1,m2) and (nb,m1,m2), and
		// use each face's centroid (mean of its three circle centers).  This is the
		// ball-bearing FACE-chain (how CP lays out the carrier), not the bare centers.
		ArrayList<double[]> pts = new ArrayList<double[]>();
		for (int i = 0; i < L; i++) {
			int b = ring[i], nb = ring[(i + 1) % L];
			int[] sh = sharedNeighbors(b, nb);
			if (sh.length < 2)
				continue;
			pts.add(faceCentroid(b, sh[0], sh[1]));
			pts.add(faceCentroid(nb, sh[0], sh[1]));
		}
		int m = pts.size();
		if (m < 3)
			return 0.0;
		double[][] V = new double[m][3];
		double[] mean = new double[3];
		for (int i = 0; i < m; i++) {
			V[i] = pts.get(i);
			for (int k = 0; k < 3; k++)
				mean[k] += V[i][k] / m;
		}
		double[][] C = new double[3][3];
		for (int i = 0; i < m; i++)
			for (int a = 0; a < 3; a++)
				for (int b = 0; b < 3; b++)
					C[a][b] += (V[i][a] - mean[a]) * (V[i][b] - mean[b]);
		double[] axis = smallestEigvec3(C);
		double[] ref = (Math.abs(axis[0]) < 0.9)
				? new double[] { 1, 0, 0 } : new double[] { 0, 1, 0 };
		double[] e1 = normalize(cross(axis, ref));
		double[] e2 = cross(axis, e1);
		double[] ang = new double[m];
		for (int i = 0; i < m; i++)
			ang[i] = Math.atan2(dot(V[i], e2), dot(V[i], e1));
		double tot = 0.0;
		for (int i = 0; i < m; i++) {
			double d = ang[(i + 1) % m] - ang[i];
			while (d > Math.PI)
				d -= 2 * Math.PI;
			while (d < -Math.PI)
				d += 2 * Math.PI;
			tot += d;
		}
		return tot / (2 * Math.PI);
	}

	/** Center of vertex v as a unit 3-vector. */
	double[] vec(int v) {
		Complex c = extenderPD.getCenter(v);
		return new double[] { Math.sin(c.y) * Math.cos(c.x),
				Math.sin(c.y) * Math.sin(c.x), Math.cos(c.y) };
	}

	/** Unit centroid of the triangular face (a,b,c). */
	double[] faceCentroid(int a, int b, int c) {
		double[] pa = vec(a), pb = vec(b), pc = vec(c);
		return normalize(new double[] { pa[0] + pb[0] + pc[0],
				pa[1] + pb[1] + pc[1], pa[2] + pb[2] + pc[2] });
	}

	/** Common neighbors (shared main circles) of vertices b and nb. */
	int[] sharedNeighbors(int b, int nb) {
		int[] fb = extenderPD.packDCEL.vertices[b].getFlower(false);
		int[] fnb = extenderPD.packDCEL.vertices[nb].getFlower(false);
		ArrayList<Integer> sh = new ArrayList<Integer>();
		for (int x : fb)
			for (int y : fnb)
				if (x == y && !sh.contains(x))
					sh.add(x);
		int[] out = new int[sh.size()];
		for (int i = 0; i < sh.size(); i++)
			out[i] = sh.get(i);
		return out;
	}

	/** Add the DCEL face (a,b,c) to a FaceLink by its face index. */
	void addFace(FaceLink fl, int a, int b, int c) {
		DcelFace f = extenderPD.packDCEL.whatFace(a, b, c);
		if (f != null && f.faceIndx > 0)
			fl.add(f.faceIndx);
	}

	/** Eigenvector of the smallest eigenvalue of symmetric 3x3 (cyclic Jacobi). */
	static double[] smallestEigvec3(double[][] A0) {
		double[][] A = { A0[0].clone(), A0[1].clone(), A0[2].clone() };
		double[][] V = { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
		for (int sweep = 0; sweep < 60; sweep++) {
			int p = 0, q = 1;
			double mx = Math.abs(A[0][1]);
			if (Math.abs(A[0][2]) > mx) { mx = Math.abs(A[0][2]); p = 0; q = 2; }
			if (Math.abs(A[1][2]) > mx) { mx = Math.abs(A[1][2]); p = 1; q = 2; }
			if (mx < 1e-15)
				break;
			double phi = 0.5 * Math.atan2(2 * A[p][q], A[q][q] - A[p][p]);
			double c = Math.cos(phi), s = Math.sin(phi);
			for (int k = 0; k < 3; k++) {
				double akp = A[k][p], akq = A[k][q];
				A[k][p] = c * akp - s * akq;
				A[k][q] = s * akp + c * akq;
			}
			for (int k = 0; k < 3; k++) {
				double apk = A[p][k], aqk = A[q][k];
				A[p][k] = c * apk - s * aqk;
				A[q][k] = s * apk + c * aqk;
			}
			for (int k = 0; k < 3; k++) {
				double vkp = V[k][p], vkq = V[k][q];
				V[k][p] = c * vkp - s * vkq;
				V[k][q] = s * vkp + c * vkq;
			}
		}
		int idx = 0;
		double mn = A[0][0];
		if (A[1][1] < mn) { mn = A[1][1]; idx = 1; }
		if (A[2][2] < mn) { mn = A[2][2]; idx = 2; }
		return new double[] { V[0][idx], V[1][idx], V[2][idx] };
	}

	static double[] cross(double[] a, double[] b) {
		return new double[] { a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}

	static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	static double[] normalize(double[] a) {
		double n = Math.sqrt(dot(a, a));
		return new double[] { a[0] / n, a[1] / n, a[2] / n };
	}

	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("bigcircles", "[-n k] [-l v1 v2 ...]", null,
				"choose/report 'big' circles to fix for ISOLATING a solution "
						+ "(uniqueness studies). -n sets count, -l an explicit list."));
		cmdStruct.add(new CmdStruct("newton", "[-i maxits] [-t tol] [-l v1 v2 ...]", null,
				"damped Newton (Levenberg-Marquardt) on the angle-sum system from the "
						+ "current radii; CERTIFIES/polishes a near-solution. -l HOLDS those "
						+ "circles fixed (anchor) at their current radii -- set 3 big circles "
						+ "with set_rad first to select/pin a solution's basin (nullity=3)."));
		cmdStruct.add(new CmdStruct("perron", "[-k K] [-l list] [-n passes]", null,
				"Perron DEVELOP phase only: fix K largest circles (or -l vlist) as a "
						+ "container, per-vertex-relax the rest (branch circles collapse). "
						+ "Leaves the container off; follow with 'newton'. Default K=24."));
		cmdStruct.add(new CmdStruct("solve", "[-k K] [-l list] [-n passes] [-i its] [-t tol]", null,
				"COMBINED discovery = perron (develop) then newton (polish). 'discover' "
						+ "is an alias. Finds a branched packing from a generic seed."));
		cmdStruct.add(new CmdStruct("residual", null, null,
				"report current max |anglesum - aim| over interior vertices."));
		cmdStruct.add(new CmdStruct("winding", "[-L] [-r v1 v2 ...]", null,
				"ball-bearing winding number about its own ring axis "
						+ "(Blaschke 3, polar 1). Default ring 77..84; -L relayouts first."));
	}

	public void helpInfo() {
		helpMsg("PackExtender |sn| SphBranchNewton: direct spherical "
				+ "angle-sum solve by damped Newton (Levenberg-Marquardt).");
		helpMsg("  newton [-i maxits] [-t tol] [-l v1 v2 ...] : Newton only; -l holds circles fixed (anchor a basin)");
		helpMsg("  perron [-k K] [-l list] [-n p]   : Perron develop only (container = K largest, or -l vlist)");
		helpMsg("  solve  [-k K] [-l list] ...      : COMBINED perron+newton -- discover from a generic seed");
		helpMsg("  residual                         : report max |anglesum - aim|");
		helpMsg("  winding [-L] [-r v1 v2 ...]      : ball-bearing face-chain winding (Blaschke 3, polar 1)");
		helpMsg("  bigcircles [-n k] [-l v1 v2 ...] : fix circles to isolate a solution");
	}
}
