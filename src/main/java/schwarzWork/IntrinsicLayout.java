package schwarzWork;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.PackDCEL;
import packing.PackData;

/**
 * Develop a spherical circle packing layout from the edge schwarzian
 * field ALONE, in the intrinsic conventions of SchwarzianSolve (|sz|).
 *
 * Method (ported from the validated Python reference
 * 'schwarzian_layout.py' in the branched_sphere_example project):
 *
 *  1. BFS over flowers, advancing base-to-face Mobius frames by the
 *     exact per-spoke transfer  m_g = m_f . M_s^{-1},  m_f' = m_g . D
 *     (the same maps whose product is the |sz| monodromy). Every frame
 *     is a PRODUCT of exact edge matrices, so error does not feed back
 *     through laid-out geometry -- unlike 'dual_layout', whose
 *     face-by-face re-derivation of frames from already-placed (hence
 *     drifted) tangency points amplifies error exponentially and can
 *     silently pick the COMPLEMENTARY spherical circle.
 *  2. Record every tangency point (possibly several times; the spread
 *     measures holonomy/closeness to a true packing).
 *  3. Fit each vertex's circle through its own tangency points on the
 *     sphere (plane fit), choosing the cap orientation by an
 *     outside-witness vote: petal-petal tangency points must lie
 *     OUTSIDE the flower's center circle.
 *
 * All computations are local; no GUI dependencies.
 */
public class IntrinsicLayout {

	static final double SQ3 = Math.sqrt(3.0);
	// BASE_F: tangency points of the base equilateral f_Delta (cube roots
	// of unity); BASE_G: its neighbor across the edge tangent at 1.
	static final Complex[] BASE_F = new Complex[] {
			new Complex(1.0, 0.0),
			new Complex(-0.5, SQ3 / 2.0),
			new Complex(-0.5, -SQ3 / 2.0) };
	static final Complex[] BASE_G = new Complex[] {
			new Complex(1.0, 0.0),
			new Complex(2.5, SQ3 / 2.0),
			new Complex(2.5, -SQ3 / 2.0) };
	// universal base advance map D (same as SchwarzianSolve.D_BASE)
	static final Complex[] D = new Complex[] {
			new Complex(SQ3 / 2.0, 0.5), new Complex(SQ3, -1.0),
			new Complex(0.0, 0.0), new Complex(SQ3 / 2.0, -0.5) };

	// ---------------- 2x2 complex matrix helpers ----------------
	// matrices are Complex[4] = {a,b,c,d} for [[a,b],[c,d]]

	static Complex[] mul(Complex[] A, Complex[] B) {
		return new Complex[] {
			A[0].times(B[0]).plus(A[1].times(B[2])),
			A[0].times(B[1]).plus(A[1].times(B[3])),
			A[2].times(B[0]).plus(A[3].times(B[2])),
			A[2].times(B[1]).plus(A[3].times(B[3])) };
	}

	static Complex[] adjugate(Complex[] A) {
		return new Complex[] { A[3], A[1].times(-1.0),
				A[2].times(-1.0), A[0] };
	}

	/** scale so det=1 (numerical hygiene along long products) */
	static Complex[] nrm(Complex[] A) {
		Complex det = A[0].times(A[3]).minus(A[1].times(A[2]));
		Complex rt = det.sqrt().reciprocal();
		return new Complex[] { A[0].times(rt), A[1].times(rt),
				A[2].times(rt), A[3].times(rt) };
	}

	static Complex apply(Complex[] A, Complex z) {
		Complex den = A[2].times(z).plus(A[3]);
		if (den.abs() < 1e-14)
			return new Complex(1e12, 0.0); // treat as infinity marker
		return A[0].times(z).plus(A[1]).divide(den);
	}

	/** M_s^{-1} = [[1-s, s],[-s, 1+s]] */
	static Complex[] msInv(double s) {
		return new Complex[] { new Complex(1 - s, 0), new Complex(s, 0),
				new Complex(-s, 0), new Complex(1 + s, 0) };
	}

	/** Mobius matrix sending z1->0, z2->1, z3->inf */
	static Complex[] to01inf(Complex z1, Complex z2, Complex z3) {
		Complex d23 = z2.minus(z3);
		Complex d21 = z2.minus(z1);
		return new Complex[] { d23, z1.times(d23).times(-1.0),
				d21, z3.times(d21).times(-1.0) };
	}

	/** unique Mobius sending src[i] -> dst[i], i=0,1,2 */
	static Complex[] mobius3(Complex[] src, Complex[] dst) {
		return mul(adjugate(to01inf(dst[0], dst[1], dst[2])),
				to01inf(src[0], src[1], src[2]));
	}

	// ---------------- development ----------------

	static long ekey(int a, int b) {
		return (a < b) ? (((long) a << 32) | b) : (((long) b << 32) | a);
	}

	/** result bundle */
	public static class Result {
		public int placed;          // circles written to the packing
		public double spread;       // holonomy: max same-point spread
		public double fitResidual;  // max circle-fit residual
	}

	/**
	 * Lay out the packing from schwarzians. 'eIndex' maps BOTH half-edge
	 * twins of each interior edge to an index into 'svar' (exactly the
	 * structures SchwarzianSolve maintains). Writes centers/radii into
	 * the packing (spherical). Gauge: identity seed frame at 'alpha'.
	 */
	public static Result layout(PackData p, HashMap<HalfEdge, Integer> eIndex,
			double[] svar) {
		PackDCEL pd = p.packDCEL;
		int N = p.nodeCount;

		// petal lists and spoke half-edges, cclw (same traversal as the
		// |sz| monodromy: he = he.prev.twin)
		int[][] pet = new int[N + 1][];
		HalfEdge[][] spoke = new HalfEdge[N + 1][];
		for (int v = 1; v <= N; v++) {
			Vertex vert = pd.vertices[v];
			ArrayList<HalfEdge> sp = new ArrayList<HalfEdge>();
			HalfEdge he = vert.halfedge;
			do {
				sp.add(he);
				he = he.prev.twin;
			} while (he != vert.halfedge);
			int n = sp.size();
			pet[v] = new int[n];
			spoke[v] = new HalfEdge[n];
			for (int k = 0; k < n; k++) {
				spoke[v][k] = sp.get(k);
				pet[v][k] = sp.get(k).twin.origin.vertIndx;
			}
		}

		// recorded tangency points per undirected edge
		HashMap<Long, ArrayList<Complex>> tang =
				new HashMap<Long, ArrayList<Complex>>();

		// BFS
		boolean[] entered = new boolean[N + 1];
		ArrayDeque<Object[]> queue = new ArrayDeque<Object[]>();
		int v0 = pd.alpha.origin.vertIndx;
		queue.add(new Object[] { Integer.valueOf(v0), Integer.valueOf(0),
				new Complex[] { new Complex(1, 0), new Complex(0, 0),
						new Complex(0, 0), new Complex(1, 0) } });
		entered[v0] = true;

		while (!queue.isEmpty()) {
			Object[] item = queue.poll();
			int v = ((Integer) item[0]).intValue();
			int k0 = ((Integer) item[1]).intValue();
			Complex[] mf = nrm((Complex[]) item[2]);
			int n = pet[v].length;
			Complex[][] mfs = new Complex[n][];

			for (int j = 0; j < n; j++) {
				int k = (k0 + j) % n;
				mfs[k] = mf;
				int a = pet[v][(k - 1 + n) % n];
				int c = pet[v][k];
				int d = pet[v][(k + 1) % n];
				Integer idx = eIndex.get(spoke[v][k]);
				double s = (idx == null) ? 0.0 : svar[idx.intValue()];
				record(tang, v, c, apply(mf, BASE_F[0]));
				record(tang, a, v, apply(mf, BASE_F[1]));
				record(tang, a, c, apply(mf, BASE_F[2]));
				Complex[] mg = mul(mf, msInv(s));
				record(tang, v, d, apply(mg, BASE_G[1]));
				record(tang, c, d, apply(mg, BASE_G[2]));
				mf = nrm(mul(mg, D));
			}

			// enqueue unvisited petals: entry frame fit through the
			// (exactly computed) tangency points of a shared face
			for (int k = 0; k < n; k++) {
				int a = pet[v][(k - 1 + n) % n];
				int c = pet[v][k];
				int[] pair = new int[] { a, c };
				for (int t = 0; t < 2; t++) {
					int other = pair[t];
					if (entered[other])
						continue;
					int third = (other == a) ? c : a; // {a,v,c} minus other,v
					int n2 = pet[other].length;
					int k2 = -1;
					for (int kk = 0; kk < n2; kk++) {
						int aa = pet[other][(kk - 1 + n2) % n2];
						int cc = pet[other][kk];
						if ((aa == v && cc == third)
								|| (aa == third && cc == v)) {
							k2 = kk;
							break;
						}
					}
					if (k2 < 0)
						continue;
					int a2 = pet[other][(k2 - 1 + n2) % n2];
					int c2 = pet[other][k2];
					Complex[] dst = new Complex[] {
							meanPt(tang, other, c2),
							meanPt(tang, a2, other),
							meanPt(tang, a2, c2) };
					if (dst[0] == null || dst[1] == null || dst[2] == null)
						continue;
					queue.add(new Object[] { Integer.valueOf(other),
							Integer.valueOf(k2),
							nrm(mobius3(BASE_F, dst)) });
					entered[other] = true;
				}
			}
		}

		// ---------------- circles from tangency points ----------------
		Result res = new Result();

		// per-edge mean point and holonomy spread
		HashMap<Long, Complex> meanOf = new HashMap<Long, Complex>();
		for (java.util.Map.Entry<Long, ArrayList<Complex>> me :
				tang.entrySet()) {
			ArrayList<Complex> finite = new ArrayList<Complex>();
			for (Complex z : me.getValue())
				if (z.abs() < 1e8)
					finite.add(z);
			if (finite.isEmpty())
				continue;
			Complex mean = new Complex(0, 0);
			for (Complex z : finite)
				mean = mean.plus(z);
			mean = mean.divide(finite.size());
			for (Complex z : finite)
				res.spread = Math.max(res.spread, z.minus(mean).abs());
			meanOf.put(me.getKey(), mean);
		}

		// conformally center: apply a sphere Mobius making the centroid
		// of the tangency points (on the sphere) the origin, so the
		// laid-out packing is in a balanced gauge (no artificially tiny
		// circles, which would wreck downstream schwarzian extraction)
		center(meanOf);

		for (int v = 1; v <= N; v++) {
			// this circle's own tangency points, on the sphere
			ArrayList<double[]> pts = new ArrayList<double[]>();
			int n = pet[v].length;
			for (int k = 0; k < n; k++) {
				Complex z = meanOf.get(ekey(v, pet[v][k]));
				if (z != null)
					pts.add(invStereo(z));
			}
			if (pts.size() < 3)
				continue;

			// plane fit: normal = smallest-eigenvalue direction of the
			// centered covariance
			double[] mean = new double[3];
			for (double[] q : pts)
				for (int i = 0; i < 3; i++)
					mean[i] += q[i];
			for (int i = 0; i < 3; i++)
				mean[i] /= pts.size();
			double[][] C = new double[3][3];
			for (double[] q : pts)
				for (int i = 0; i < 3; i++)
					for (int jj = 0; jj < 3; jj++)
						C[i][jj] += (q[i] - mean[i]) * (q[jj] - mean[jj]);
			double[] nvec = smallestEigvec(C);
			double dd = nvec[0] * mean[0] + nvec[1] * mean[1]
					+ nvec[2] * mean[2];
			if (dd < 0) {
				for (int i = 0; i < 3; i++)
					nvec[i] = -nvec[i];
				dd = -dd;
			}
			for (double[] q : pts) {
				double r = Math.abs((q[0] - mean[0]) * nvec[0]
						+ (q[1] - mean[1]) * nvec[1]
						+ (q[2] - mean[2]) * nvec[2]);
				res.fitResidual = Math.max(res.fitResidual, r);
			}
			double rad = Math.acos(Math.max(-1.0, Math.min(1.0, dd)));

			// cap orientation: petal-petal tangency points lie OUTSIDE
			int outVotes = 0, votes = 0;
			for (int k = 0; k < n; k++) {
				Complex z = meanOf.get(
						ekey(pet[v][k], pet[v][(k + 1) % n]));
				if (z == null)
					continue;
				double[] q = invStereo(z);
				double ang = Math.acos(Math.max(-1.0, Math.min(1.0,
						q[0] * nvec[0] + q[1] * nvec[1] + q[2] * nvec[2])));
				votes++;
				if (ang > rad)
					outVotes++;
			}
			if (votes > 0 && outVotes * 2 < votes) {
				for (int i = 0; i < 3; i++)
					nvec[i] = -nvec[i];
				rad = Math.PI - rad;
			}

			// write to packing: spherical center (theta, phi)
			double theta = Math.atan2(nvec[1], nvec[0]);
			double phi = Math.acos(Math.max(-1.0, Math.min(1.0, nvec[2])));
			pd.setVertCenter(v, new Complex(theta, phi));
			pd.setRad4Edge(pd.vertices[v].halfedge, rad);
			res.placed++;
		}
		return res;
	}

	/**
	 * Iteratively apply sphere Mobius maps (rotation-to-north +
	 * pole-fixing dilation, both realized on the plane) until the
	 * spherical centroid of the points is ~0. Modifies 'meanOf' in place.
	 */
	static void center(HashMap<Long, Complex> meanOf) {
		for (int iter = 0; iter < 100; iter++) {
			double[] c = new double[3];
			int cnt = 0;
			for (Complex w : meanOf.values()) {
				if (w.abs() > 1e8)
					continue;
				double[] q = invStereo(w);
				for (int i = 0; i < 3; i++)
					c[i] += q[i];
				cnt++;
			}
			if (cnt == 0)
				return;
			for (int i = 0; i < 3; i++)
				c[i] /= cnt;
			double clen = Math.sqrt(c[0] * c[0] + c[1] * c[1]
					+ c[2] * c[2]);
			if (clen < 1e-10)
				return;
			if (clen > 0.999)  // degenerate cloud (non-solution seed):
				return;        // no balanced gauge exists, keep as is
			// plane coordinate of the centroid direction (same projection
			// convention as invStereo, so the y-mirror cancels out)
			double[] u = new double[] { c[0] / clen, c[1] / clen,
					c[2] / clen };
			Complex a = new Complex(u[0] / (1 + u[2]),
					-u[1] / (1 + u[2]));
			// push mass AWAY from the cluster direction (now at the north
			// pole, i.e. near w=0): dilation with lam>1 moves the
			// centroid toward the origin
			double lam = Math.sqrt((1 + clen) / (1 - clen));
			Complex one = new Complex(1, 0);
			for (java.util.Map.Entry<Long, Complex> me :
					meanOf.entrySet()) {
				Complex w = me.getValue();
				if (w.abs() > 1e8) {
					// infinity maps to 1/conj(a) under z->(z-a)/(1+conj(a)z)
					w = a.conj().reciprocal();
				} else {
					w = w.minus(a).divide(
							one.plus(a.conj().times(w)));
				}
				me.setValue(w.times(lam));
			}
		}
	}

	static void record(HashMap<Long, ArrayList<Complex>> tang, int a, int b,
			Complex z) {
		Long key = Long.valueOf(ekey(a, b));
		ArrayList<Complex> lst = tang.get(key);
		if (lst == null) {
			lst = new ArrayList<Complex>();
			tang.put(key, lst);
		}
		lst.add(z);
	}

	static Complex meanPt(HashMap<Long, ArrayList<Complex>> tang, int a,
			int b) {
		ArrayList<Complex> lst = tang.get(Long.valueOf(ekey(a, b)));
		if (lst == null || lst.isEmpty())
			return null;
		Complex mean = new Complex(0, 0);
		int cnt = 0;
		for (Complex z : lst)
			if (z.abs() < 1e8) {
				mean = mean.plus(z);
				cnt++;
			}
		if (cnt == 0)
			return null;
		return mean.divide(cnt);
	}

	/**
	 * inverse stereographic projection to the unit sphere, CONJUGATED:
	 * the development plane's orientation is opposite to CP's spherical
	 * face orientation, so mirror (y -> -y) to make the laid-out packing
	 * positively oriented in CP's convention.
	 */
	static double[] invStereo(Complex w) {
		double aa = w.absSq();
		return new double[] { 2 * w.x / (1 + aa), -2 * w.y / (1 + aa),
				(1 - aa) / (1 + aa) };
	}

	/** smallest-eigenvalue unit eigenvector of a symmetric 3x3 (Jacobi) */
	static double[] smallestEigvec(double[][] A) {
		double[][] a = new double[3][3];
		for (int i = 0; i < 3; i++)
			a[i] = A[i].clone();
		double[][] V = new double[][] { { 1, 0, 0 }, { 0, 1, 0 },
				{ 0, 0, 1 } };
		for (int sweep = 0; sweep < 50; sweep++) {
			double off = Math.abs(a[0][1]) + Math.abs(a[0][2])
					+ Math.abs(a[1][2]);
			if (off < 1e-15)
				break;
			for (int pp = 0; pp < 2; pp++)
				for (int q = pp + 1; q < 3; q++) {
					if (Math.abs(a[pp][q]) < 1e-18)
						continue;
					double theta = 0.5 * Math.atan2(2 * a[pp][q],
							a[q][q] - a[pp][pp]);
					double cs = Math.cos(theta), sn = Math.sin(theta);
					for (int i = 0; i < 3; i++) {
						double aip = a[i][pp], aiq = a[i][q];
						a[i][pp] = cs * aip - sn * aiq;
						a[i][q] = sn * aip + cs * aiq;
					}
					for (int i = 0; i < 3; i++) {
						double api = a[pp][i], aqi = a[q][i];
						a[pp][i] = cs * api - sn * aqi;
						a[q][i] = sn * api + cs * aqi;
					}
					for (int i = 0; i < 3; i++) {
						double vip = V[i][pp], viq = V[i][q];
						V[i][pp] = cs * vip - sn * viq;
						V[i][q] = sn * vip + cs * viq;
					}
				}
		}
		int m = 0;
		for (int i = 1; i < 3; i++)
			if (a[i][i] < a[m][m])
				m = i;
		double[] out = new double[] { V[0][m], V[1][m], V[2][m] };
		double len = Math.sqrt(out[0] * out[0] + out[1] * out[1]
				+ out[2] * out[2]);
		for (int i = 0; i < 3; i++)
			out[i] /= len;
		return out;
	}
}
