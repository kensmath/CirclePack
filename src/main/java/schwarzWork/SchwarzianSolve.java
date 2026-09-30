package schwarzWork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import ftnTheory.SphBranchNewton;
import listManip.NodeLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;

/**
 * SchwarzianSolve, |sz|: solve the spherical circle packing problem directly
 * in intrinsic-schwarzian coordinates, then lay out via the intrinsic
 * Mobius-frame development (IntrinsicLayout).
 *
 * The unknowns are the edge schwarzians s_e (one per interior edge). The
 * constraint is the CLOSING condition per flower: the monodromy
 *
 *      P_v = prod_{spokes e of v, cclw} ( M_{s_e}^{-1} . D )
 *
 * must be scalar (identity in PSL2), where M_s = [[1+s,-s],[s,1-s]] is the
 * standard edge Mobius (Mobius.stdBaseMobius(s,0)) and D is the UNIVERSAL
 * base advance map, derived analytically from the flat hexagonal flower:
 *
 *      D = [[ sqrt3/2 + i/2 ,  sqrt3 - i ],
 *           [       0       ,  sqrt3/2 - i/2 ]],   tr D = sqrt3.
 *
 * Because M_s is affine in s and each s_e occurs in exactly one factor,
 * every entry of P_v is AFFINE in each s_e (multilinear jointly): the
 * Jacobian is computed EXACTLY by unit probes, each coordinate slice of the
 * residual is a straight line (fold-free -- the property radius coordinates
 * lack on the sphere), and Levenberg-Marquardt converges from the purely
 * combinatorial "degree seed"  s_e = (s_n(v) + s_n(w))/2,
 * s_n = 1 - (2/sqrt3)cos(pi/n).
 *
 * Structure (damping loop, Cholesky solve) mirrors ftnTheory.SphBranchNewton.
 * Validation and theory: SCHWARZIAN_MONOTONE_THEORY.md in the
 * branched_sphere_example project.
 *
 * BUDGET STAGE (stage 1, 'bsolve'): per flower the angular budget T_v --
 * the total projective advance of the spoke transfer recursion
 * v_k = sqrt3 u_k v_{k-1} - v_{k-2} (u = 1 - s), each bite canonically in
 * (0,pi) -- satisfies T_v = pi * wrap at any closed flower, and is monotone
 * nondecreasing in every own-spoke schwarzian. Prescribing aims pi*w_v
 * (w_v = 1 univalent, 2 at chosen branch vertices) and solving the
 * V-dimensional system beta = T - aim by damped Newton (M y = -beta with
 * M = J A^T, step s += lam * A^T y; M provably nonsingular -- Theorem C,
 * BUDGET_FLOW_PROOF.md) selects the solution BASIN before the LM stage:
 * the wrap vector is the basin coordinate. Production recipe:
 * seed -> bsolve -> solve. Budget balance is a strict relaxation of
 * closing (exactly the single polynomial condition per flower), so
 * 'solve' still finishes the job; 'bsolve' steers it and rescues seeds
 * stalled between basins.
 *
 * TAIL-COORDINATE SOLVER ('tsolve'): the closing conditions of a flower
 * triangularize in the pinned frame (FLOWER_LOCAL_CLOSING.md 7a): with the
 * fundamental pair (X, Y) of the spoke recursion, budget balance is the
 * Dirichlet condition Y_n = 0 (wrap = 1 + interior sign changes of Y),
 * the stretch is Lambda = (-1)^{w+1} Y_{n-1}, and the shear
 * mu = (-1)^w (sqrt3 u_1 Y_{n-1} + H_{n-1}) is affine in the base spoke.
 * 'tsolve' runs damped LM on the exact 3V-row system
 * (beta_v, Lambda_v - 1, mu_v) with the wrap vector as input; on the
 * univ_sphere test bed it converges to the prescribed twin from every
 * seed tried, including the classical residual's captured stall
 * (spurious J^T F = 0 point) -- one stall-free stage subsuming
 * bsolve + solve. 'residual'/'check' remain the certificate.
 *
 * RADIUS-SPACE CHAPTER: the full ftnTheory.SphBranchNewton solver
 * (damped Newton/Perron on the spherical angle-sum system) is embedded
 * here as well, so one extender carries every solver: 'newton',
 * 'perron', 'discover', 'bigcircles', 'winding', 'windfaces', 'basin1'
 * forward to a non-registered SphBranchNewton instance on the same
 * packing; the two colliding names are prefixed as 'rsolve' (radius
 * Perron+Newton) and 'rresidual' (max |anglesum - aim|). Bridges
 * between the chapters: 'seed -c' imports the schwarzians of the
 * current (e.g. newton-solved) geometry; 'layout' exports solved
 * schwarzians back to geometry, after which 'rresidual' certifies in
 * radius space.
 */
public class SchwarzianSolve extends PackExtender {

	/** universal base advance map (analytic; see class doc) */
	static final Mobius D_BASE = new Mobius(
			new Complex(CPBase.sqrt3by2, 0.5),
			new Complex(CPBase.sqrt3, -1.0),
			new Complex(0.0),
			new Complex(CPBase.sqrt3by2, -0.5));

	// variable set: one index per undirected interior edge
	HalfEdge[] eReps;                       // representative half-edge per variable
	HashMap<HalfEdge, Integer> eIndex;      // BOTH twins -> variable index
	double[] svar;                          // current schwarzian values
	boolean seeded = false;

	// embedded radius-space solver (lazily created; not registered as
	// a separate extender -- see the radius-chapter forwarding below)
	SphBranchNewton radiusSolver;

	SphBranchNewton radius() {
		if (radiusSolver == null)
			radiusSolver = new SphBranchNewton(extenderPD, true);
		return radiusSolver;
	}

	public SchwarzianSolve(PackData p) {
		super(p);
		extensionType = "SCHWARZIAN_SOLVE";
		extensionAbbrev = "sz";
		toolTip = "'SchwarzianSolve': solve the packing in intrinsic-schwarzian "
				+ "coordinates (fold-free closing system), then lay out";
		registerXType();
		try {
			rebuild();
		} catch (Exception ex) {
			Oops("failed to index edges");
			running = false;
		}
		msg("ready. Typical run: '|sz| seed -d' then '|sz| solve', then "
				+ "'|sz| layout'; '|sz| check' compares to geometry. To "
				+ "prescribe branching, insert '|sz| bsolve -b v1 v2 ...' "
				+ "before 'solve'.");
		running = true;
		extenderPD.packExtensions.add(this);
	}

	/** index the undirected interior edges; init svar from stored schwarzians */
	void rebuild() {
		eIndex = new HashMap<HalfEdge, Integer>();
		ArrayList<HalfEdge> reps = new ArrayList<HalfEdge>();
		for (int v = 1; v <= extenderPD.nodeCount; v++) {
			Vertex vert = extenderPD.packDCEL.vertices[v];
			HalfEdge he = vert.halfedge;
			do {
				if (!eIndex.containsKey(he) && !he.isBdry()) {
					int idx = reps.size();
					reps.add(he);
					eIndex.put(he, idx);
					eIndex.put(he.twin, idx);
				}
				he = he.prev.twin;   // cclw about v
			} while (he != vert.halfedge);
		}
		eReps = reps.toArray(new HalfEdge[0]);
		svar = new double[eReps.length];
		for (int j = 0; j < eReps.length; j++)
			svar[j] = eReps[j].getSchwarzian();
	}

	/**
	 * True if the cached edge index no longer matches the current DCEL --
	 * e.g. an 'infile_read' rebuilt the packing under the extender (which
	 * cannot re-attach on an already-occupied pack).  Cheap, identity-based
	 * check: a representative half-edge of the CURRENT DCEL should be a key
	 * in eIndex; after a rebuild it is a fresh object and is not.
	 */
	boolean cacheStale() {
		if (eReps == null || eIndex == null || extenderPD.nodeCount < 1)
			return true;
		HalfEdge he = extenderPD.packDCEL.vertices[1].halfedge;
		return !eIndex.containsKey(he);
	}

	// ------------------------------------------------------------------
	// monodromy residual
	// ------------------------------------------------------------------

	/** M_s^{-1} = M_{-s} (parabolic one-parameter group) */
	static Mobius msInverse(double s) {
		return Mobius.stdBaseMobius(-s, 0);
	}

	/**
	 * closing defect of flower v: 6 reals (Re/Im of P.b, P.c, P.a-P.d);
	 * zero iff the monodromy is scalar.
	 */
	void flowerDefect(int v, double[] out, int off) {
		Vertex vert = extenderPD.packDCEL.vertices[v];
		Mobius P = new Mobius();   // identity
		HalfEdge he = vert.halfedge;
		do {
			Integer j = eIndex.get(he);
			double s = (j == null) ? 0.0 : svar[j];
			Mobius step = (Mobius) msInverse(s).rmultby(D_BASE);
			P = (Mobius) P.rmultby(step);
			he = he.prev.twin;
		} while (he != vert.halfedge);
		Complex ad = P.a.minus(P.d);
		out[off] = P.b.x;
		out[off + 1] = P.b.y;
		out[off + 2] = P.c.x;
		out[off + 3] = P.c.y;
		out[off + 4] = ad.x;
		out[off + 5] = ad.y;
	}

	/** full residual: 6 rows per vertex */
	double[] residualVector() {
		double[] F = new double[6 * extenderPD.nodeCount];
		for (int v = 1; v <= extenderPD.nodeCount; v++)
			flowerDefect(v, F, 6 * (v - 1));
		return F;
	}

	// ------------------------------------------------------------------
	// commands
	// ------------------------------------------------------------------

	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {

		// self-heal: 're-extender sz' is a no-op on a pack that already has
		// this extender, so after an 'infile_read' the DCEL is rebuilt but our
		// cached edge index (eReps/eIndex) still points at the orphaned old
		// half-edges -- 'seed -c'/'set_sch' then read garbage (while 'winding',
		// which reads live centers, looks fine).  Detect the change, re-index,
		// and WARN, instead of silently running on stale parameters.
		if (running && cacheStale()) {
			errorMsg("|sz| WARNING: the packing changed under this extender "
					+ "(reloaded/rebuilt); re-indexing edges. Any prior seed or "
					+ "solution is discarded -- run 'seed' again before 'solve'.");
			try {
				rebuild();
			} catch (Exception ex) {
				errorMsg("|sz|: re-index failed: " + ex.getMessage());
				return 0;
			}
			seeded = false;
			radiusSolver = null;
		}

		// ========= seed =========
		if (cmd.startsWith("seed")) {
			boolean fromCurrent = false;
			boolean zero = false;
			double sigma = 0.0;
			try {
				if (flagSegs != null)
					for (Vector<String> seg : flagSegs) {
						if (seg.isEmpty())
							continue;
						String f = seg.get(0);
						if (f.equals("-c"))
							fromCurrent = true;
						else if (f.equals("-d"))
							fromCurrent = false;
						else if (f.equals("-z"))
							zero = true;
						else if (f.equals("-p"))
							sigma = Double.parseDouble(seg.get(1));
					}
			} catch (Exception ex) {
				errorMsg("seed: usage: seed [-d | -c | -z] [-p sigma]");
				return 0;
			}
			if (zero) {
				// the "blind" seed: no combinatorics, no geometry
				for (int j = 0; j < eReps.length; j++)
					svar[j] = 0.0;
				msg("seeded identically zero (blind seed; carries no "
						+ "information at all)");
			} else if (fromCurrent) {
				// recompute from current geometry, then copy stored values
				cpCommand("set_sch");
				for (int j = 0; j < eReps.length; j++)
					svar[j] = eReps[j].getSchwarzian();
				msg("seeded from geometry (set_sch)");
			} else {
				for (int j = 0; j < eReps.length; j++) {
					int nv = eReps[j].origin.getNum();
					int nw = eReps[j].twin.origin.getNum();
					svar[j] = 0.5 * (uniformS(nv) + uniformS(nw));
				}
				msg("seeded from vertex degrees (combinatorial seed)");
			}
			if (sigma > 0.0) {
				Random rng = new Random(1);
				for (int j = 0; j < eReps.length; j++)
					svar[j] += sigma * rng.nextGaussian();
				msg(String.format("perturbed by sigma=%.4g", sigma));
			}
			seeded = true;
			return 1;
		}

		// ========= residual =========
		if (cmd.startsWith("resid")) {
			ensureSeeded();
			double[] F = residualVector();
			int worst = 1;
			double wmax = 0.0;
			for (int v = 1; v <= extenderPD.nodeCount; v++) {
				double m = 0.0;
				for (int k = 0; k < 6; k++)
					m = Math.max(m, Math.abs(F[6 * (v - 1) + k]));
				if (m > wmax) {
					wmax = m;
					worst = v;
				}
			}
			msg(String.format("closing residual: ||R||_2=%.6e, worst flower v%d "
					+ "(%.3e); %d edge variables", twoNorm(F), worst, wmax,
					eReps.length));
			return 1;
		}

		// ========= solve =========
		if (cmd.startsWith("solve")) {
			ensureSeeded();
			int maxits = 40;
			double tol = 1e-10;
			try {
				if (flagSegs != null)
					for (Vector<String> seg : flagSegs) {
						if (seg.isEmpty())
							continue;
						String f = seg.get(0);
						if (f.equals("-i"))
							maxits = Integer.parseInt(seg.get(1));
						else if (f.equals("-t"))
							tol = Double.parseDouble(seg.get(1));
					}
			} catch (Exception ex) {
				errorMsg("solve: usage: solve [-i maxits] [-t tol]");
				return 0;
			}
			return solve(maxits, tol);
		}

		// ========= layout =========
		if (cmd.startsWith("layout")) {
			pushToEdges();
			// intrinsic development (NOT 'dual_layout', whose face-by-face
			// re-derivation of frames from already-placed circles both
			// amplifies error exponentially and can silently pick the
			// complementary spherical circle -- see IntrinsicLayout)
			try {
				IntrinsicLayout.Result res =
						IntrinsicLayout.layout(extenderPD, eIndex, svar);
				msg(String.format("intrinsic layout: %d circles; holonomy "
						+ "spread %.3e (near zero iff schwarzians close up); "
						+ "circle-fit residual %.3e", res.placed, res.spread,
						res.fitResidual));
				return res.placed > 0 ? res.placed : 0;
			} catch (Exception ex) {
				errorMsg("intrinsic layout failed: " + ex.getMessage());
				return 0;
			}
		}

		// ========= check =========
		if (cmd.startsWith("check")) {
			// compare internal values with schwarzians recomputed from the
			// CURRENT geometry (restores stored edge values afterwards)
			double[] hold = new double[eReps.length];
			for (int j = 0; j < eReps.length; j++)
				hold[j] = eReps[j].getSchwarzian();
			cpCommand("set_sch");
			double linf = 0.0;
			for (int j = 0; j < eReps.length; j++)
				linf = Math.max(linf,
						Math.abs(eReps[j].getSchwarzian() - svar[j]));
			for (int j = 0; j < eReps.length; j++)
				extenderPD.setSchwarzian(eReps[j], hold[j]);
			msg(String.format("Linf(s_solved - s_geometry) = %.6e", linf));
			return 1;
		}

		// ========= cont (continuant / branch detection) =========
		if (cmd.startsWith("cont")) {
			ensureSeeded();
			StringBuilder sb = new StringBuilder(
					"flowers with negative continuant (branched, Thm 4.5):\n");
			int hits = 0;
			for (int v = 1; v <= extenderPD.nodeCount; v++) {
				double cm = contMin(v);
				if (cm < 0.0) {
					sb.append(String.format("  v%d (deg %d): min C_j = %.4f%n",
							v, extenderPD.packDCEL.vertices[v].getNum(), cm));
					hits++;
				}
			}
			if (hits == 0)
				msg("all flowers have positive continuants (univalent regime)");
			else
				msg(sb.toString());
			return 1;
		}

		// ========= budget (report stage-1 state) =========
		if (cmd.startsWith("budget")) {
			ensureSeeded();
			double[] aims = budgetAims(parseBranchList(flagSegs));
			int Vn = extenderPD.nodeCount;
			double b2 = 0.0, binf = 0.0;
			StringBuilder sb = new StringBuilder();
			int hits = 0;
			for (int v = 1; v <= Vn; v++) {
				double T = flowerBudget(v, null);
				double beta = T - aims[v];
				b2 += beta * beta;
				binf = Math.max(binf, Math.abs(beta));
				long w = Math.round(T / Math.PI);
				if (w >= 2) {
					sb.append(String.format("  v%d (deg %d): T=%.4f, wrap %d%n",
							v, extenderPD.packDCEL.vertices[v].getNum(), T, w));
					hits++;
				}
			}
			msg(String.format("budget: ||beta||_2=%.6e, ||beta||_inf=%.6e "
					+ "(beta_v = T_v - aim_v)", Math.sqrt(b2), binf));
			if (hits == 0)
				msg("all wraps 1 (predicted univalent basin)");
			else
				msg("flowers with wrap >= 2 (predicted branch set):\n"
						+ sb.toString());
			return 1;
		}

		// ========= bsolve (stage-1 budget-Newton) =========
		if (cmd.startsWith("bsolve")) {
			ensureSeeded();
			int maxits = 30;
			double tol = 1e-10;
			try {
				if (flagSegs != null)
					for (Vector<String> seg : flagSegs) {
						if (seg.isEmpty())
							continue;
						String f = seg.get(0);
						if (f.equals("-i"))
							maxits = Integer.parseInt(seg.get(1));
						else if (f.equals("-t"))
							tol = Double.parseDouble(seg.get(1));
					}
			} catch (Exception ex) {
				errorMsg("bsolve: usage: bsolve [-i maxits] [-t tol] "
						+ "[-b v1 v2 ...]");
				return 0;
			}
			return bsolve(maxits, tol, budgetAims(parseBranchList(flagSegs)));
		}

		// ========= tsolve (one-stage tail-coordinate solver) =========
		if (cmd.startsWith("tsolve")) {
			ensureSeeded();
			int maxits = 60;
			double tol = 1e-10;
			try {
				if (flagSegs != null)
					for (Vector<String> seg : flagSegs) {
						if (seg.isEmpty())
							continue;
						String f = seg.get(0);
						if (f.equals("-i"))
							maxits = Integer.parseInt(seg.get(1));
						else if (f.equals("-t"))
							tol = Double.parseDouble(seg.get(1));
					}
			} catch (Exception ex) {
				errorMsg("tsolve: usage: tsolve [-i maxits] [-t tol] "
						+ "[-b v1 v2 ...]");
				return 0;
			}
			return tsolve(maxits, tol, budgetAims(parseBranchList(flagSegs)));
		}

		// ========= radius-space chapter (embedded |sn| SphBranchNewton) =========
		// One solver front-end: the radius-coordinate commands forward to
		// an embedded (non-registered) SphBranchNewton on this packing.
		// Names that collide with the schwarzian commands are prefixed:
		// 'rsolve' = |sn| solve (Perron develop + Newton polish),
		// 'rresidual' = max |anglesum - aim|.
		if (cmd.startsWith("rsolve"))
			return radius().cmdParser("solve", flagSegs);
		if (cmd.startsWith("rresid"))
			return radius().cmdParser("residual", flagSegs);
		if (cmd.startsWith("newton") || cmd.startsWith("perron")
				|| cmd.startsWith("discover") || cmd.startsWith("bigcirc")
				|| cmd.startsWith("wind") || cmd.startsWith("basin"))
			return radius().cmdParser(cmd, flagSegs);

		return super.cmdParser(cmd, flagSegs);
	}

	// ------------------------------------------------------------------
	// the LM solve (structure follows SphBranchNewton.newton)
	// ------------------------------------------------------------------

	int solve(int maxits, double tol) {
		int n = eReps.length;
		int m = 6 * extenderPD.nodeCount;
		double lam = 1e-3;
		double[] F = residualVector();
		msg(String.format("solve: %d edges, %d closing rows, seed ||R||_2=%.4e",
				n, m, twoNorm(F)));
		int it = 0;
		for (it = 1; it <= maxits; it++) {
			double normF = twoNorm(F);
			if (infNorm(F) < tol)
				break;
			// exact Jacobian by unit probes (residual affine per coordinate)
			double[][] J = new double[m][];
			{
				double[][] Jt = new double[n][];
				for (int j = 0; j < n; j++) {
					svar[j] += 1.0;
					double[] Fp = residualProbe(j, F);
					svar[j] -= 1.0;
					double[] col = new double[m];
					for (int i = 0; i < m; i++)
						col[i] = Fp[i] - F[i];
					Jt[j] = col;
				}
				for (int i = 0; i < m; i++) {
					J[i] = new double[n];
					for (int j = 0; j < n; j++)
						J[i][j] = Jt[j][i];
				}
			}
			// normal equations
			double[][] A = new double[n][n];
			double[] g = new double[n];
			for (int i = 0; i < m; i++) {
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
			// damped step with backtracking
			double[] s0 = svar.clone();
			boolean stepped = false;
			for (int tries = 0; tries < 40 && !stepped; tries++) {
				double[] dx = choleskyDamped(A, negate(g), lam);
				if (dx != null) {
					for (int a = 0; a < n; a++)
						svar[a] = s0[a] + dx[a];
					double[] Fn = residualVector();
					if (twoNorm(Fn) < normF) {
						F = Fn;
						lam = Math.max(lam * 0.5, 1e-13);
						stepped = true;
						break;
					}
					System.arraycopy(s0, 0, svar, 0, n);
				}
				lam *= 3.0;
			}
			if (!stepped) {
				msg(String.format("solve: no productive step at it %d "
						+ "(||R||_2=%.3e)", it, normF));
				break;
			}
		}
		pushToEdges();
		msg(String.format("solve: done after %d its, ||R||_2=%.6e "
				+ "(||R||_inf=%.3e); schwarzians stored on edges "
				+ "-- run '|sz| layout'", it, twoNorm(F), infNorm(F)));
		return 1;
	}

	/**
	 * residual after probing variable j: only the two endpoint flowers of
	 * edge j change, so copy F and recompute just those rows.
	 */
	double[] residualProbe(int j, double[] F) {
		double[] Fp = F.clone();
		int v = eReps[j].origin.vertIndx;
		int w = eReps[j].twin.origin.vertIndx;
		flowerDefect(v, Fp, 6 * (v - 1));
		flowerDefect(w, Fp, 6 * (w - 1));
		return Fp;
	}

	// ------------------------------------------------------------------
	// the budget stage (stage 1): angular budgets and budget-Newton
	// ------------------------------------------------------------------

	/** spoke variable indices of v, cclw (-1 where the edge is not a
	 * variable, e.g. boundary edges, which stay at s = 0) */
	int[] spokes(int v) {
		Vertex vert = extenderPD.packDCEL.vertices[v];
		ArrayList<Integer> js = new ArrayList<Integer>();
		HalfEdge he = vert.halfedge;
		do {
			Integer j = eIndex.get(he);
			js.add((j == null) ? -1 : j);
			he = he.prev.twin;
		} while (he != vert.halfedge);
		int[] out = new int[js.size()];
		for (int k = 0; k < out.length; k++)
			out[k] = js.get(k);
		return out;
	}

	/**
	 * Angular budget T_v: total projective advance of the tangency
	 * direction through the spoke transfer recursion
	 *    v_0 = (1,0), v_1 = (sqrt3 u_1, 1),
	 *    v_k = sqrt3 u_k v_{k-1} - v_{k-2},   u = 1 - s,
	 * each bite taken canonically in (0,pi). At a closed flower
	 * T_v = pi * wrap; T_v is monotone nondecreasing in every own-spoke
	 * schwarzian.
	 *
	 * If dTds is non-null (length deg v) it receives dT_v/ds per spoke,
	 * ANALYTICALLY: T is the continuous lift of the final direction's
	 * angle (theta_0 = 0, every bite < pi), so dT/du_j = dtheta_n/du_j,
	 * computed by the forward sensitivity recursion
	 *    p_j = sqrt3 v_{j-1},  p_k = sqrt3 u_k p_{k-1} - p_{k-2} (k > j),
	 *    dtheta_n/du_j = (x_n py_n - y_n px_n)/|v_n|^2,
	 * and dT/ds_j = -dT/du_j. (A finite difference would jump by pi/h
	 * whenever a bite crosses a wrap boundary; the sensitivity form is
	 * exact everywhere v_n != 0, which holds since the B factors are
	 * invertible.)
	 */
	double flowerBudget(int v, double[] dTds) {
		int[] jd = spokes(v);
		int n = jd.length;
		double[] ux = new double[n];
		for (int k = 0; k < n; k++)
			ux[k] = 1.0 - ((jd[k] < 0) ? 0.0 : svar[jd[k]]);
		double T = budgetU(ux, dTds);
		if (dTds != null)
			for (int k = 0; k < n; k++)
				dTds[k] = -dTds[k];   // dT/ds = -dT/du
		return T;
	}

	/**
	 * Static core of the budget computation on u = 1 - s directly; if
	 * dTdu is non-null it receives dT/du per spoke (note flowerBudget
	 * negates this to dT/ds). Kept static/self-contained so it can be
	 * validated headlessly against the Python reference.
	 */
	static double budgetU(double[] ux, double[] dTdu) {
		int n = ux.length;
		double[] X = new double[n + 1];
		double[] Y = new double[n + 1];
		X[0] = 1.0;
		Y[0] = 0.0;
		X[1] = CPBase.sqrt3 * ux[0];
		Y[1] = 1.0;
		for (int k = 2; k <= n; k++) {
			X[k] = CPBase.sqrt3 * ux[k - 1] * X[k - 1] - X[k - 2];
			Y[k] = CPBase.sqrt3 * ux[k - 1] * Y[k - 1] - Y[k - 2];
		}
		double T = 0.0, thPrev = 0.0;
		for (int k = 1; k <= n; k++) {
			double th = Math.atan2(Y[k], X[k]) % Math.PI;
			if (th < 0.0)
				th += Math.PI;
			double bite = (th - thPrev) % Math.PI;
			if (bite < 0.0)
				bite += Math.PI;
			T += bite;
			thPrev = th;
		}
		if (dTdu != null) {
			double den = X[n] * X[n] + Y[n] * Y[n];
			for (int j = 1; j <= n; j++) {
				double px = CPBase.sqrt3 * X[j - 1];
				double py = CPBase.sqrt3 * Y[j - 1];
				double pxPrev = 0.0, pyPrev = 0.0;
				for (int k = j + 1; k <= n; k++) {
					double nx = CPBase.sqrt3 * ux[k - 1] * px - pxPrev;
					double ny = CPBase.sqrt3 * ux[k - 1] * py - pyPrev;
					pxPrev = px;
					pyPrev = py;
					px = nx;
					py = ny;
				}
				dTdu[j - 1] = (X[n] * py - Y[n] * px) / den;
			}
		}
		return T;
	}

	/**
	 * Static core of the TAIL TRIPLE on u = 1 - s: the exact three closing
	 * conditions of one flower in the pinned (base-spoke) frame,
	 *    out = (T - pi*w,  Lambda - 1,  mu).
	 * Track the fundamental pair (X, Y) of the spoke recursion
	 * z_k = sqrt3 u_k z_{k-1} - z_{k-2} with X: (X_0, X_{-1}) = (1, 0) and
	 * Y: (Y_0, Y_{-1}) = (0, -1) (so Y_1 = 1), plus H: (H_0, H_1) = (1, 0).
	 * Then (see FLOWER_LOCAL_CLOSING.md 7a):
	 *   - Y never sees the base spoke u_1, and X = sqrt3 u_1 Y + H;
	 *   - budget balance T = pi*w is the Dirichlet condition Y_n = 0
	 *     (wrap = 1 + interior sign changes of Y -- Sturm oscillation);
	 *   - the stretch is Lambda = (-1)^{w+1} Y_{n-1} (Wronskian gives
	 *     lambda = 1/Lambda on the budget slice); closed <=> Lambda = 1;
	 *   - the shear is mu = (-1)^w (sqrt3 u_1 Y_{n-1} + H_{n-1}), affine in
	 *     the base spoke with slope sqrt3 |Y_{n-1}|.
	 * The flower closes with wrap w iff out = (0, 0, 0). Static so it can
	 * be validated headlessly against the Python reference
	 * (probe_tail_sensitivity.py / probe_staged_tail.py).
	 */
	static void tailU(double[] ux, int w, double[] out) {
		int n = ux.length;
		double[] X = new double[n + 1];
		double[] Y = new double[n + 1];
		double[] H = new double[n + 1];
		X[0] = 1.0;
		Y[0] = 0.0;
		H[0] = 1.0;
		X[1] = CPBase.sqrt3 * ux[0];
		Y[1] = 1.0;
		H[1] = 0.0;
		for (int k = 2; k <= n; k++) {
			double c = CPBase.sqrt3 * ux[k - 1];
			X[k] = c * X[k - 1] - X[k - 2];
			Y[k] = c * Y[k - 1] - Y[k - 2];
			H[k] = c * H[k - 1] - H[k - 2];
		}
		double T = 0.0, thPrev = 0.0;
		for (int k = 1; k <= n; k++) {
			double th = Math.atan2(Y[k], X[k]) % Math.PI;
			if (th < 0.0)
				th += Math.PI;
			double bite = (th - thPrev) % Math.PI;
			if (bite < 0.0)
				bite += Math.PI;
			T += bite;
			thPrev = th;
		}
		double sg = (w % 2 == 0) ? 1.0 : -1.0;
		out[0] = T - Math.PI * w;
		out[1] = -sg * Y[n - 1] - 1.0;
		out[2] = sg * (CPBase.sqrt3 * ux[0] * Y[n - 1] + H[n - 1]);
	}

	/** tail triple of flower v with prescribed wrap w (see tailU) */
	void flowerTail(int v, int w, double[] out) {
		int[] jd = spokes(v);
		double[] ux = new double[jd.length];
		for (int k = 0; k < jd.length; k++)
			ux[k] = 1.0 - ((jd[k] < 0) ? 0.0 : svar[jd[k]]);
		tailU(ux, w, out);
	}

	/** parse a '-b v1 v2 ...' segment into a branch-vertex list (or null) */
	ArrayList<Integer> parseBranchList(Vector<Vector<String>> flagSegs) {
		if (flagSegs == null)
			return null;
		ArrayList<Integer> blist = null;
		try {
			for (Vector<String> seg : flagSegs) {
				if (seg.isEmpty() || !seg.get(0).equals("-b"))
					continue;
				Vector<String> sub = new Vector<String>(
						seg.subList(1, seg.size()));
				NodeLink nl = new NodeLink(extenderPD, sub);
				blist = new ArrayList<Integer>();
				if (nl != null)
					for (int w : nl)
						blist.add(w);
			}
		} catch (Exception ex) {
			errorMsg("bad -b vertex list");
		}
		return blist;
	}

	/**
	 * Per-vertex budget aims pi*w_v. With a '-b' list: w = 2 at listed
	 * vertices, 1 elsewhere. Without: aims are read off the PACKING's
	 * aims where set (budget aim = aim/2, so the usual 'set_aim 4*pi v'
	 * branch prescription carries over from SphBranchNewton), default
	 * pi (univalent) where unset.
	 */
	double[] budgetAims(ArrayList<Integer> blist) {
		int Vn = extenderPD.nodeCount;
		double[] aims = new double[Vn + 1];
		for (int v = 1; v <= Vn; v++)
			aims[v] = Math.PI;
		if (blist != null) {
			for (int v : blist)
				if (v >= 1 && v <= Vn)
					aims[v] = 2.0 * Math.PI;
		} else {
			for (int v = 1; v <= Vn; v++) {
				double pa = extenderPD.getAim(v);
				if (pa > 0.0)
					aims[v] = pa / 2.0;
			}
		}
		return aims;
	}

	/**
	 * Stage-1 budget-Newton (Theorem C, BUDGET_FLOW_PROOF.md): solve the
	 * V-dimensional system beta_v = T_v - aim_v = 0 by damped Newton,
	 *    M y = -beta,  M = J A^T,  s <- s + lam A^T y,
	 * where J = DT (rows supported on own spokes, entries g_{v,e} =
	 * dT_v/ds_e >= 0) and A is the unsigned incidence matrix, so
	 * (A^T y)_e = y_v + y_w for e = (v,w). M is nonsingular whenever all
	 * g > 0 (Gershgorin + Taussky + the skeleton's triangles); it is NOT
	 * symmetric, hence the LU solve. Boundary vertices (disc case) carry
	 * no budget equation and are held out of y.
	 */
	int bsolve(int maxits, double tol, double[] aims) {
		int Vn = extenderPD.nodeCount;
		int[] rowOf = new int[Vn + 1];
		ArrayList<Integer> ivs = new ArrayList<Integer>();
		for (int v = 1; v <= Vn; v++) {
			if (extenderPD.packDCEL.vertices[v].isBdry()) {
				rowOf[v] = -1;
				continue;
			}
			rowOf[v] = ivs.size();
			ivs.add(v);
		}
		int Vi = ivs.size();
		if (Vi == 0) {
			errorMsg("bsolve: no interior vertices");
			return 0;
		}
		double[] beta = new double[Vi];
		for (int r = 0; r < Vi; r++)
			beta[r] = flowerBudget(ivs.get(r), null) - aims[ivs.get(r)];
		msg(String.format("bsolve: %d budget equations, seed ||beta||_2=%.4e",
				Vi, twoNorm(beta)));
		int it = 0;
		for (it = 1; it <= maxits; it++) {
			if (infNorm(beta) < tol)
				break;
			// assemble M = J A^T restricted to interior vertices
			double[][] M = new double[Vi][Vi];
			for (int r = 0; r < Vi; r++) {
				int v = ivs.get(r);
				int[] jd = spokes(v);
				double[] g = new double[jd.length];
				flowerBudget(v, g);
				// walk the spokes again for the neighbor vertices
				Vertex vert = extenderPD.packDCEL.vertices[v];
				HalfEdge he = vert.halfedge;
				int k = 0;
				do {
					if (jd[k] >= 0) {
						M[r][r] += g[k];
						int w = he.twin.origin.vertIndx;
						if (rowOf[w] >= 0)
							M[r][rowOf[w]] += g[k];
					}
					he = he.prev.twin;
					k++;
				} while (he != vert.halfedge);
			}
			double[] y = luSolve(M, negate(beta));
			if (y == null) {
				msg(String.format("bsolve: singular budget Jacobian at it %d "
						+ "(||beta||_2=%.3e)", it, twoNorm(beta)));
				break;
			}
			// damped step s += lam * A^T y
			double[] s0 = svar.clone();
			double normB = twoNorm(beta);
			double lam = 1.0;
			boolean stepped = false;
			for (int tries = 0; tries < 40 && !stepped; tries++) {
				for (int j = 0; j < eReps.length; j++) {
					int a = eReps[j].origin.vertIndx;
					int b = eReps[j].twin.origin.vertIndx;
					double dy = (rowOf[a] >= 0 ? y[rowOf[a]] : 0.0)
							+ (rowOf[b] >= 0 ? y[rowOf[b]] : 0.0);
					svar[j] = s0[j] + lam * dy;
				}
				double[] betaNew = new double[Vi];
				for (int r = 0; r < Vi; r++)
					betaNew[r] = flowerBudget(ivs.get(r), null)
							- aims[ivs.get(r)];
				if (twoNorm(betaNew) < normB) {
					beta = betaNew;
					stepped = true;
					break;
				}
				lam *= 0.5;
			}
			if (!stepped) {
				System.arraycopy(s0, 0, svar, 0, svar.length);
				msg(String.format("bsolve: no productive step at it %d "
						+ "(||beta||_2=%.3e)", it, twoNorm(beta)));
				break;
			}
		}
		pushToEdges();
		msg(String.format("bsolve: done after %d its, ||beta||_2=%.6e "
				+ "(||beta||_inf=%.3e); wraps selected -- run '|sz| solve' "
				+ "to close", it, twoNorm(beta), infNorm(beta)));
		return 1;
	}

	/**
	 * One-stage tail-coordinate solver (2026-09-13 experiment,
	 * FLOWER_LOCAL_CLOSING.md 7b): damped Levenberg-Marquardt on the EXACT
	 * three closing conditions per interior flower,
	 *    R_3 = (beta_v, Lambda_v - 1, mu_v),
	 * 3V rows against E = 3V - 6 unknowns (the six global monodromy
	 * redundancies), with the wrap vector as INPUT (aims, as in bsolve).
	 * On the univ_sphere test bed this converged to the PRESCRIBED twin
	 * from every seed tried -- including the captured lm-stall (the
	 * spurious J^T F = 0 point the 6-row residual gets caught on) and
	 * seeds from which the classical LM wanders to far solutions. It
	 * subsumes bsolve + solve in one stall-free stage; 'residual' /
	 * 'check' remain the independent certificate.
	 *
	 * Jacobian by central differences exploiting locality (an edge enters
	 * only its two endpoint flowers); the residual is NOT affine in s
	 * (unlike the 6-row closing system), so unit probes do not apply.
	 */
	int tsolve(int maxits, double tol, double[] aims) {
		int Vn = extenderPD.nodeCount;
		int[] rowOf = new int[Vn + 1];
		ArrayList<Integer> ivs = new ArrayList<Integer>();
		for (int v = 1; v <= Vn; v++) {
			if (extenderPD.packDCEL.vertices[v].isBdry()) {
				rowOf[v] = -1;
				continue;
			}
			rowOf[v] = ivs.size();
			ivs.add(v);
		}
		int Vi = ivs.size();
		if (Vi == 0) {
			errorMsg("tsolve: no interior vertices");
			return 0;
		}
		int[] wrap = new int[Vn + 1];
		for (int v = 1; v <= Vn; v++)
			wrap[v] = (int) Math.round(aims[v] / Math.PI);
		int n = eReps.length;
		int m = 3 * Vi;
		double[] R = new double[m];
		double[] tri = new double[3];
		for (int r = 0; r < Vi; r++) {
			int v = ivs.get(r);
			flowerTail(v, wrap[v], tri);
			System.arraycopy(tri, 0, R, 3 * r, 3);
		}
		msg(String.format("tsolve: %d tail equations (%d flowers x 3) on "
				+ "%d edges; seed ||beta||=%.3e ||Lam-1||=%.3e ||mu||=%.3e",
				m, Vi, n, blockNorm(R, 0), blockNorm(R, 1), blockNorm(R, 2)));
		double h = 1e-6;
		double lam = 1e-3;
		int it = 0;
		for (it = 1; it <= maxits; it++) {
			double normR = twoNorm(R);
			if (infNorm(R) < tol)
				break;
			// Jacobian: central differences, two endpoint flowers per edge
			double[][] J = new double[m][n];
			double[] trip = new double[3];
			double[] trim = new double[3];
			for (int j = 0; j < n; j++) {
				int a = eReps[j].origin.vertIndx;
				int b = eReps[j].twin.origin.vertIndx;
				double hold = svar[j];
				for (int vv : new int[] { a, b }) {
					if (rowOf[vv] < 0)
						continue;
					svar[j] = hold + h;
					flowerTail(vv, wrap[vv], trip);
					svar[j] = hold - h;
					flowerTail(vv, wrap[vv], trim);
					svar[j] = hold;
					int r0 = 3 * rowOf[vv];
					for (int t = 0; t < 3; t++)
						J[r0 + t][j] = (trip[t] - trim[t]) / (2.0 * h);
				}
				svar[j] = hold;
			}
			// normal equations
			double[][] A = new double[n][n];
			double[] g = new double[n];
			for (int i = 0; i < m; i++) {
				double[] Ji = J[i];
				double Ri = R[i];
				for (int a = 0; a < n; a++) {
					double Jia = Ji[a];
					if (Jia == 0.0)
						continue;
					g[a] += Jia * Ri;
					double[] Aa = A[a];
					for (int b = 0; b < n; b++)
						Aa[b] += Jia * Ji[b];
				}
			}
			// damped step with backtracking
			double[] s0 = svar.clone();
			boolean stepped = false;
			for (int tries = 0; tries < 40 && !stepped; tries++) {
				double[] dx = choleskyDamped(A, negate(g), lam);
				if (dx != null) {
					for (int a = 0; a < n; a++)
						svar[a] = s0[a] + dx[a];
					double[] Rn = new double[m];
					for (int r = 0; r < Vi; r++) {
						int v = ivs.get(r);
						flowerTail(v, wrap[v], tri);
						System.arraycopy(tri, 0, Rn, 3 * r, 3);
					}
					if (twoNorm(Rn) < normR) {
						R = Rn;
						lam = Math.max(lam * 0.5, 1e-13);
						stepped = true;
						break;
					}
					System.arraycopy(s0, 0, svar, 0, n);
				}
				lam *= 3.0;
			}
			if (!stepped) {
				msg(String.format("tsolve: no productive step at it %d "
						+ "(||R3||_2=%.3e)", it, normR));
				break;
			}
		}
		pushToEdges();
		double[] F = residualVector();
		msg(String.format("tsolve: done after %d its; ||beta||=%.3e "
				+ "||Lam-1||=%.3e ||mu||=%.3e; classical closing residual "
				+ "||R||_2=%.6e (the certificate); schwarzians stored on "
				+ "edges -- run '|sz| layout'", it, blockNorm(R, 0),
				blockNorm(R, 1), blockNorm(R, 2), twoNorm(F)));
		return 1;
	}

	/** two-norm of one tail block (0 = beta, 1 = Lambda-1, 2 = mu) */
	static double blockNorm(double[] R, int block) {
		double s = 0.0;
		for (int i = block; i < R.length; i += 3)
			s += R[i] * R[i];
		return Math.sqrt(s);
	}

	/**
	 * Solve A x = b by LU with partial pivoting; A and b are clobbered.
	 * Returns null if (numerically) singular. Needed because the budget
	 * matrix M = J A^T is nonsymmetric (M_vx = g_{v,(v,x)} vs
	 * M_xv = g_{x,(v,x)}) -- Cholesky does not apply.
	 */
	static double[] luSolve(double[][] A, double[] b) {
		int n = A.length;
		for (int col = 0; col < n; col++) {
			int best = col;
			double bmax = Math.abs(A[col][col]);
			for (int i = col + 1; i < n; i++)
				if (Math.abs(A[i][col]) > bmax) {
					bmax = Math.abs(A[i][col]);
					best = i;
				}
			if (bmax < 1e-14)
				return null;
			if (best != col) {
				double[] tr = A[col];
				A[col] = A[best];
				A[best] = tr;
				double tb = b[col];
				b[col] = b[best];
				b[best] = tb;
			}
			for (int i = col + 1; i < n; i++) {
				double f = A[i][col] / A[col][col];
				A[i][col] = f;
				for (int j = col + 1; j < n; j++)
					A[i][j] -= f * A[col][j];
				b[i] -= f * b[col];
			}
		}
		double[] x = new double[n];
		for (int i = n - 1; i >= 0; i--) {
			double s = b[i];
			for (int j = i + 1; j < n; j++)
				s -= A[i][j] * x[j];
			x[i] = s / A[i][i];
		}
		return x;
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	/** uniform n-flower schwarzian, s_n = 1 - (2/sqrt3)cos(pi/n) */
	static double uniformS(int n) {
		return 1.0 - (2.0 / CPBase.sqrt3) * Math.cos(Math.PI / n);
	}

	void ensureSeeded() {
		if (!seeded) {
			for (int j = 0; j < eReps.length; j++)
				svar[j] = eReps[j].getSchwarzian();
			seeded = true;
		}
	}

	/** write internal values onto the packing's half-edges (both twins) */
	void pushToEdges() {
		for (int j = 0; j < eReps.length; j++)
			extenderPD.setSchwarzian(eReps[j], svar[j]);
	}

	/**
	 * minimum continuant entry of flower v over all cyclic rotations, from
	 * u = 1 - s: C_2 = sqrt3 u_1, C_3 = 3u_1u_2 - 1,
	 * C_{j+1} = sqrt3 u_j C_j - C_{j-1}. Negative => branched flower.
	 */
	double contMin(int v) {
		Vertex vert = extenderPD.packDCEL.vertices[v];
		ArrayList<Double> us = new ArrayList<Double>();
		HalfEdge he = vert.halfedge;
		do {
			Integer j = eIndex.get(he);
			us.add(1.0 - ((j == null) ? 0.0 : svar[j]));
			he = he.prev.twin;
		} while (he != vert.halfedge);
		int n = us.size();
		double best = Double.MAX_VALUE;
		for (int rot = 0; rot < n; rot++) {
			double cPrev = CPBase.sqrt3 * us.get(rot % n);
			double cCur = 3.0 * us.get(rot % n) * us.get((rot + 1) % n) - 1.0;
			best = Math.min(best, Math.min(cPrev, cCur));
			for (int k = 3; k < n - 1; k++) {
				double cNext = CPBase.sqrt3 * us.get((rot + k - 1) % n) * cCur
						- cPrev;
				cPrev = cCur;
				cCur = cNext;
				best = Math.min(best, cCur);
			}
		}
		return best;
	}

	// dense linear algebra (copied from ftnTheory.SphBranchNewton)

	static double infNorm(double[] x) {
		double s = 0.0;
		for (double v : x)
			s = Math.max(s, Math.abs(v));
		return s;
	}

	static double twoNorm(double[] x) {
		double s = 0.0;
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

	// ------------------------------------------------------------------

	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("seed", "[-d|-c|-z] [-p sigma]", null,
				"seed edge schwarzians: -d from vertex degrees (combinatorial),"
				+ " -c from current geometry, -z identically zero (blind);"
				+ " -p adds Gaussian noise"));
		cmdStruct.add(new CmdStruct("residual", null, null,
				"norm of the flower-closing (monodromy) residual"));
		cmdStruct.add(new CmdStruct("solve", "[-i maxits] [-t tol]", null,
				"Levenberg-Marquardt on the closing system over edge "
				+ "schwarzians; exact Jacobian by unit probes"));
		cmdStruct.add(new CmdStruct("layout", null, null,
				"store solved schwarzians and develop circles via the "
				+ "intrinsic Mobius-frame development (IntrinsicLayout)"));
		cmdStruct.add(new CmdStruct("check", null, null,
				"compare solved schwarzians against those recomputed from the "
				+ "current geometry"));
		cmdStruct.add(new CmdStruct("cont", null, null,
				"per-flower continuant minima; negative = branched flower"));
		cmdStruct.add(new CmdStruct("budget", "[-b v1 v2 ...]", null,
				"report per-flower angular budgets T_v: ||T - aim|| and the "
				+ "predicted branch set (wraps = round(T/pi))"));
		cmdStruct.add(new CmdStruct("bsolve", "[-i maxits] [-t tol] [-b v..]",
				null, "stage-1 budget-Newton: drive T_v to pi*w_v to select "
				+ "the solution basin (w=2 at '-b' vertices, else from "
				+ "packing aims); then run 'solve'"));
		cmdStruct.add(new CmdStruct("tsolve", "[-i maxits] [-t tol] [-b v..]",
				null, "one-stage tail-coordinate solver: LM on the exact "
				+ "three closing conditions per flower (budget, stretch, "
				+ "shear) with the wrap vector as input (as bsolve); "
				+ "subsumes bsolve+solve, stall-free on the test bed"));
		cmdStruct.add(new CmdStruct("newton", "[-i maxits] [-t tol] [-l v..]",
				null, "radius chapter: damped Newton/LM on the spherical "
				+ "angle-sum system (forwards to embedded SphBranchNewton); "
				+ "-l anchors the listed circles"));
		cmdStruct.add(new CmdStruct("perron", "[-k K] [-n passes] [-l v..]",
				null, "radius chapter: Perron develop with fixed container"));
		cmdStruct.add(new CmdStruct("rsolve", "[flags of |sn| solve]", null,
				"radius chapter: Perron (develop) + Newton (polish); the "
				+ "radius-coordinate analogue of 'solve'"));
		cmdStruct.add(new CmdStruct("rresidual", null, null,
				"radius chapter: max |anglesum - aim| over interior vertices"));
		cmdStruct.add(new CmdStruct("bigcircles", "[-n k] [-l v1 v2 ...]",
				null, "radius chapter: choose/report the fixed gauge circles"));
		cmdStruct.add(new CmdStruct("winding", "[-r ring] [-L]", null,
				"radius chapter: ball-bearing winding number"));
		cmdStruct.add(new CmdStruct("basin1", "v min max N [-r ring] [-p pnum]",
				null, "radius chapter: sweep one circle's radius; watch the "
				+ "basin transition"));
	}

	public void helpInfo() {
		helpMsg("Commands for PackExtender " + extensionAbbrev
				+ " (SchwarzianSolve)");
		helpMsg("  seed [-d|-c] [-p sigma]   seed the edge schwarzians");
		helpMsg("  residual                  closing (monodromy) residual");
		helpMsg("  solve [-i its] [-t tol]   LM solve in schwarzian coords");
		helpMsg("  layout                    develop circles (intrinsic "
				+ "Mobius-frame development)");
		helpMsg("  check                     solved vs geometry schwarzians");
		helpMsg("  cont                      continuant branch detection");
		helpMsg("  budget [-b v..]           angular budgets / predicted wraps");
		helpMsg("  bsolve [-i its] [-t tol] [-b v..]  stage-1 budget-Newton "
				+ "(basin selection); follow with 'solve'");
		helpMsg("  tsolve [-i its] [-t tol] [-b v..]  one-stage tail solver "
				+ "(budget+stretch+shear; subsumes bsolve+solve)");
		helpMsg("radius chapter (embedded SphBranchNewton; same packing):");
		helpMsg("  newton [-i its] [-t tol] [-l v..]  damped Newton on the "
				+ "angle-sum system (-l anchors)");
		helpMsg("  perron [-k K] [-n passes] [-l v..] Perron develop");
		helpMsg("  rsolve / rresidual        radius-space solve / residual");
		helpMsg("  bigcircles | winding | windfaces | basin1   as in |sn|");
	}
}
