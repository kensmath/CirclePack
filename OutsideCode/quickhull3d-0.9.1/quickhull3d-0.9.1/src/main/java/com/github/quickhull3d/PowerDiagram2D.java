package com.github.quickhull3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Builds clipped polygonal power cells using: - lifting (x,y,w) -> (x,y,
 * x^2+y^2-w) - then finding QuickHull3D lower hull.
 */
public class PowerDiagram2D {

	/**
	 * Computes the clipped 2D power diagram (a.k.a. weighted Voronoi / Laguerre
	 * diagram) for the given sites and weights.
	 *
	 * <p>
	 * The result is a list of {@link Cell} objects where each cell contains:
	 * <ul>
	 * <li>the originating site index ({@link Cell#siteIndex()})</li>
	 * <li>a CCW polygon ({@link Cell#polygon()}) clipped to {@code clipOrNull}</li>
	 * </ul>
	 *
	 * <p>
	 * <b>Validity / empties:</b> this method returns <b>only valid, non-empty</b>
	 * cells. Sites that are hidden (not on the lower hull of the lifted points) or
	 * whose clipped polygon becomes empty are <b>omitted</b> from the returned
	 * list. Therefore, the list size may be less than {@code x.length}.
	 *
	 * @param x          x-coordinates of sites
	 * @param y          y-coordinates of sites
	 * @param w          weights of sites (same length as {@code x} and {@code y})
	 * @param clipOrNull clipping rectangle; if {@code null}, a default rectangle is
	 *                   chosen
	 * @return a list of valid cells (each polygon has at least 3 vertices), in no
	 *         guaranteed order other than construction order; no empty cells are
	 *         included
	 * @throws IllegalArgumentException if {@code x}, {@code y}, and {@code w} do
	 *                                  not have the same length
	 */
	public static List<Cell> computeCells(double[] x, double[] y, double[] w, Rect clipOrNull) {
		int n = x.length;
		if (y.length != n || w.length != n) {
			throw new IllegalArgumentException("x,y,w must have same length");
		}

		Rect clip = (clipOrNull != null) ? clipOrNull : autoClip(x, y);

		// 1) Lift and hull
		Point3d[] lifted = new Point3d[n];
		for (int i = 0; i < n; i++) {
			lifted[i] = new Point3d(x[i], y[i], x[i] * x[i] + y[i] * y[i] - w[i]);
		}
		QuickHull3D hull = new QuickHull3D();
		hull.build(lifted);
//		hull.triangulate();

		// Mapping: hull-vertex-index -> input-site-index
		int[] hullToSite = hull.getVertexPointIndices();

		// 2) Extract regular adjacency from LOWER hull triangles
		@SuppressWarnings("unchecked")
		HashSet<Integer>[] nbr = new HashSet[n];
		for (int i = 0; i < n; i++) {
			nbr[i] = new HashSet<>();
		}
		boolean[] active = new boolean[n];

		for (Face f : hull.getFaceObjects()) {
			// lower hull: outward normal has negative z
			if (!(f.getNormal().z < 0)) {
				continue;
			}

			int[] hidx = new int[f.numVertices()];
			f.getVertexIndices(hidx);
			if (hidx.length != 3) {
				continue; // after triangulate(), should be 3
			}

			int a = hullToSite[hidx[0]];
			int b = hullToSite[hidx[1]];
			int c = hullToSite[hidx[2]];

			active[a] = active[b] = active[c] = true;

			addEdge(nbr, a, b);
			addEdge(nbr, b, c);
			addEdge(nbr, c, a);
		}

		// 3) For each site, clip the rectangle by all neighbor bisector half-planes
		List<Cell> cells = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (!active[i]) {
				continue; // hidden site => no returned cell
			}

			List<Pt> poly = rectPolyCCW(clip);
			for (int j : nbr[i]) {
				poly = clipByPowerHalfPlane(poly, x, y, w, i, j);
				if (poly.isEmpty()) {
					break;
				}
			}

			if (!poly.isEmpty()) {
				cells.add(new Cell(i, poly));
			}
		}

		return cells;
	}

	/** Convenience overload taking Site objects. */
	public static List<Cell> computeCells(List<Site> sites, Rect clipOrNull) {
		int n = sites.size();
		double[] x = new double[n];
		double[] y = new double[n];
		double[] w = new double[n];

		for (int i = 0; i < n; i++) {
			Site site = sites.get(i);
			x[i] = site.x();
			y[i] = site.y();
			w[i] = site.w();
		}

		return computeCells(x, y, w, clipOrNull);
	}

	private static void addEdge(HashSet<Integer>[] nbr, int a, int b) {
		if (a == b) {
			return;
		}
		nbr[a].add(b);
		nbr[b].add(a);
	}

	/** Auto clip rectangle around sites (simple default). */
	private static Rect autoClip(double[] x, double[] y) {
		double xmin = x[0], xmax = x[0], ymin = y[0], ymax = y[0];
		for (int i = 1; i < x.length; i++) {
			xmin = Math.min(xmin, x[i]);
			xmax = Math.max(xmax, x[i]);
			ymin = Math.min(ymin, y[i]);
			ymax = Math.max(ymax, y[i]);
		}
		double dx = xmax - xmin, dy = ymax - ymin;
		double pad = 0.25 * Math.max(dx, dy) + 1e-6; // heuristic
		return new Rect(xmin - pad, ymin - pad, xmax + pad, ymax + pad);
	}

	private static List<Pt> rectPolyCCW(Rect r) {
		return new ArrayList<>(List.of(new Pt(r.xmin, r.ymin), new Pt(r.xmax, r.ymin), new Pt(r.xmax, r.ymax), new Pt(r.xmin, r.ymax)));
	}

	/**
	 * Keep points p with power_i(p) <= power_j(p), i.e. 2(xj-xi) * px + 2(yj-yi) *
	 * py <= (xj^2+yj^2-wj) - (xi^2+yi^2-wi)
	 */
	private static List<Pt> clipByPowerHalfPlane(List<Pt> poly, double[] x, double[] y, double[] w, int i, int j) {
		if (poly.isEmpty()) {
			return poly;
		}

		double xi = x[i], yi = y[i], wi = w[i];
		double xj = x[j], yj = y[j], wj = w[j];

		double A = 2.0 * (xj - xi);
		double B = 2.0 * (yj - yi);
		double C = (xj * xj + yj * yj - wj) - (xi * xi + yi * yi - wi);

		// Sutherland–Hodgman polygon clipping against line A*x + B*y = C
		ArrayList<Pt> out = new ArrayList<>();
		int m = poly.size();

		// scale-aware epsilon
		double eps = 1e-12 * (Math.abs(C) + Math.abs(A) + Math.abs(B) + 1.0);

		for (int k = 0; k < m; k++) {
			Pt P = poly.get(k);
			Pt Q = poly.get((k + 1) % m);

			double sP = A * P.x + B * P.y - C;
			double sQ = A * Q.x + B * Q.y - C;

			boolean inP = sP <= eps;
			boolean inQ = sQ <= eps;

			if (inP && inQ) {
				out.add(Q);
			} else if (inP && !inQ) {
				Pt I = intersect(P, Q, sP, sQ);
				if (I != null) {
					out.add(I);
				}
			} else if (!inP && inQ) {
				Pt I = intersect(P, Q, sP, sQ);
				if (I != null) {
					out.add(I);
				}
				out.add(Q);
			}
			// else: both out => add nothing
		}

		// clean near-duplicate consecutive points
		return cleanup(out, 1e-12);
	}

	// line intersection with s(t)=0 where s is linear along segment
	private static Pt intersect(Pt P, Pt Q, double sP, double sQ) {
		double denom = (sP - sQ);
		if (denom == 0) {
			return null;
		}
		double t = sP / denom; // in [0,1] for proper crossings
		double x = P.x + t * (Q.x - P.x);
		double y = P.y + t * (Q.y - P.y);
		return new Pt(x, y);
	}

	private static List<Pt> cleanup(List<Pt> poly, double tol) {
		if (poly.size() < 3) {
			return List.of();
		}
		ArrayList<Pt> out = new ArrayList<>();
		for (Pt p : poly) {
			if (out.isEmpty()) {
				out.add(p);
			} else {
				Pt last = out.get(out.size() - 1);
				double dx = p.x - last.x;
				double dy = p.y - last.y;
				if (Math.sqrt(dx * dx + dy * dy) > tol) {
					out.add(p);
				}
			}
		}
		// close-check first/last
		if (out.size() >= 2) {
			Pt a = out.get(0), b = out.get(out.size() - 1);
			double dx = a.x - b.x;
			double dy = a.y - b.y;
			if (Math.sqrt(dx * dx + dy * dy) <= tol) {
				out.remove(out.size() - 1);
			}
		}
		return (out.size() >= 3) ? out : List.of();
	}

	public static class Pt {
		private final double x;
		private final double y;

		public Pt(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Pt pt = (Pt) obj;
			return Double.compare(pt.x, x) == 0 && Double.compare(pt.y, y) == 0;
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y);
		}

		@Override
		public String toString() {
			return "Pt[x=" + x + ", y=" + y + "]";
		}
	}

	public static class Rect {
		private final double xmin;
		private final double ymin;
		private final double xmax;
		private final double ymax;

		public Rect(double xmin, double ymin, double xmax, double ymax) {
			this.xmin = xmin;
			this.ymin = ymin;
			this.xmax = xmax;
			this.ymax = ymax;
		}

		public double xmin() {
			return xmin;
		}

		public double ymin() {
			return ymin;
		}

		public double xmax() {
			return xmax;
		}

		public double ymax() {
			return ymax;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Rect rect = (Rect) obj;
			return Double.compare(rect.xmin, xmin) == 0 && Double.compare(rect.ymin, ymin) == 0 && Double.compare(rect.xmax, xmax) == 0
					&& Double.compare(rect.ymax, ymax) == 0;
		}

		@Override
		public int hashCode() {
			return Objects.hash(xmin, ymin, xmax, ymax);
		}

		@Override
		public String toString() {
			return "Rect[xmin=" + xmin + ", ymin=" + ymin + ", xmax=" + xmax + ", ymax=" + ymax + "]";
		}
	}

	public static class Site {
		private final double x;
		private final double y;
		private final double w;

		public Site(double x, double y, double w) {
			this.x = x;
			this.y = y;
			this.w = w;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		public double w() {
			return w;
		}
	}

	/** One cell per site index. Polygons are CCW. */
	public static final class Cell {
		private final int siteIndex;
		private final List<Pt> polygon;

		public Cell(int siteIndex, List<Pt> polygon) {
			this.siteIndex = siteIndex;
			this.polygon = (polygon == null) ? List.of() : polygon;
		}

		public int siteIndex() {
			return siteIndex;
		}

		public List<Pt> polygon() {
			return polygon;
		}

		@Override
		public String toString() {
			return "Cell[siteIndex=" + siteIndex + ", polygonSize=" + polygon.size() + "]";
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			Cell other = (Cell) obj;
			return siteIndex == other.siteIndex && Objects.equals(polygon, other.polygon);
		}

		@Override
		public int hashCode() {
			return Objects.hash(siteIndex, polygon);
		}
	}

}