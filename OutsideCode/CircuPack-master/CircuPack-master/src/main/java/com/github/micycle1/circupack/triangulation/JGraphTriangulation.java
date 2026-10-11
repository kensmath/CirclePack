package com.github.micycle1.circupack.triangulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

public class JGraphTriangulation implements Triangulation {

	protected final Graph<Vertex, IQuadEdge> graph;

	protected final List<Vertex> vertices;
	protected final HashMap<Vertex, Integer> indexOf;

	protected final List<List<Integer>> flowers;

	protected final List<Integer> boundary;
	protected final BitSet isBoundary;
	protected final List<Integer> interiorVertices;

	// Constructors

	// 1) Use convex hull as a fallback boundary
	public JGraphTriangulation(Graph<Vertex, IQuadEdge> graph) {
		this(graph, null, null);
	}

	// 2) Provide a boundary loop (preferred). Will be reoriented to CCW if needed.
	public JGraphTriangulation(Graph<Vertex, IQuadEdge> graph, List<Vertex> boundaryLoopCCW) {
		this(graph, boundaryLoopCCW, null);
	}

	// 3) Provide the set of boundary edges (assumed to form a single loop). Will be
	// reoriented to CCW if needed.
	public JGraphTriangulation(Graph<Vertex, IQuadEdge> graph, Set<IQuadEdge> boundaryEdges) {
		this(graph, null, boundaryEdges);
	}

	// Internal constructor handling all cases
	private JGraphTriangulation(Graph<Vertex, IQuadEdge> graph, List<Vertex> boundaryLoopOpt, Set<IQuadEdge> boundaryEdgesOpt) {
		this.graph = Objects.requireNonNull(graph, "graph is null");

		// Collect vertices (skip synthetic if available)
		this.indexOf = new HashMap<>();
		this.vertices = new ArrayList<>();
		for (Vertex v : graph.vertexSet()) {
			if (isSynthetic(v)) {
				continue;
			}
			if (indexOf.putIfAbsent(v, vertices.size()) == null) {
				vertices.add(v);
			}
		}

		// Build flowers: CCW-sorted neighbor indices
		this.flowers = new ArrayList<>(Collections.nCopies(vertices.size(), null));
		for (int i = 0; i < vertices.size(); i++) {
			Vertex v = vertices.get(i);
			List<Vertex> nbrs = Graphs.neighborListOf(graph, v).stream().filter(n -> !isSynthetic(n)).filter(indexOf::containsKey).collect(Collectors.toList());

			// sort neighbors by angle CCW around v
			double ax = v.getX();
			double ay = v.getY();
			nbrs.sort(Comparator.comparingDouble(b -> Math.atan2(b.getY() - ay, b.getX() - ax)));

			List<Integer> nbrIdx = nbrs.stream().map(indexOf::get).collect(Collectors.toList());
			flowers.set(i, nbrIdx);
		}

		// Build boundary
		List<Vertex> boundaryVerts;
		if (boundaryLoopOpt != null && !boundaryLoopOpt.isEmpty()) {
			boundaryVerts = new ArrayList<>(boundaryLoopOpt);
		} else if (boundaryEdgesOpt != null && !boundaryEdgesOpt.isEmpty()) {
			boundaryVerts = buildBoundaryFromEdges(boundaryEdgesOpt);
		} else {
			boundaryVerts = convexHullCCW(vertices);
		}

		// drop the last if it closes the loop
		if (boundaryVerts.size() > 1 && boundaryVerts.get(0).equals(boundaryVerts.get(boundaryVerts.size() - 1))) {
			boundaryVerts.remove(boundaryVerts.size() - 1);
		}
		// orient CCW
		if (!isCCW(boundaryVerts)) {
			Collections.reverse(boundaryVerts);
		}

		this.boundary = boundaryVerts.stream().map(indexOf::get).collect(Collectors.toList());
		this.isBoundary = new BitSet(vertices.size());
		this.boundary.forEach(isBoundary::set);

		this.interiorVertices = new ArrayList<>();
		for (int i = 0; i < vertices.size(); i++) {
			if (!isBoundary.get(i)) {
				interiorVertices.add(i);
			}
		}

		// Open boundary flowers to interior wedge [prevBoundary, ..., nextBoundary]
		postprocessBoundaryFlowersToInteriorOpen();
	}

	// Triangulation interface

	@Override
	public int getVertexCount() {
		return vertices.size();
	}

	@Override
	public List<Integer> getFlower(int v) {
		return flowers.get(v);
	}

	@Override
	public boolean isBoundaryVertex(int v) {
		return isBoundary.get(v);
	}

	@Override
	public List<Integer> getBoundaryLoop() {
		return boundary;
	}

	@Override
	public List<Integer> getInteriorVertices() {
		return interiorVertices;
	}

	// Boundary flower post-process: open to interior wedge
	private void postprocessBoundaryFlowersToInteriorOpen() {
		int m = boundary.size();
		int[] pos = new int[vertices.size()];
		Arrays.fill(pos, -1);
		for (int i = 0; i < m; i++) {
			pos[boundary.get(i)] = i;
		}

		for (int i = 0; i < m; i++) {
			int w = boundary.get(i);
			List<Integer> nbrs = new ArrayList<>(flowers.get(w)); // CCW circular
			int deg = nbrs.size();
			if (deg < 2) {
				continue;
			}

			int prev = boundary.get((i - 1 + m) % m);
			int next = boundary.get((i + 1) % m);

			int ip = nbrs.indexOf(prev);
			int in = nbrs.indexOf(next);
			if (ip < 0 || in < 0) {
				// If boundary neighbors are not both present, skip
				continue;
			}

			List<Integer> arcCCW = collectArc(nbrs, ip, in, +1);
			List<Integer> arcCW = collectArc(nbrs, ip, in, -1);

			int intCountCCW = countInterior(arcCCW);
			int intCountCW = countInterior(arcCW);

			List<Integer> chosen = (intCountCCW >= intCountCW) ? arcCCW : arcCW;

			// ensure first=prev and last=next
			if (!chosen.isEmpty() && chosen.get(0) != prev) {
				Collections.reverse(chosen);
			}
			flowers.set(w, chosen);
		}
	}

	/**
	 * The packer index of a vertex, e.g. for specifying polygon corner vertices.
	 */
	public int indexOf(Vertex v) {
		Integer i = indexOf.get(v);
		if (i == null) {
			throw new IllegalArgumentException("Vertex not found in triangulation: " + v);
		}
		return i;
	}

	private List<Integer> collectArc(List<Integer> nbrs, int from, int to, int stepSign) {
		int n = nbrs.size();
		int step = (stepSign >= 0) ? 1 : -1;
		List<Integer> arc = new ArrayList<>(n + 1);
		int k = from;
		arc.add(nbrs.get(k));
		int guard = 0;
		while (k != to && guard <= n + 2) {
			k = (k + step + n) % n;
			arc.add(nbrs.get(k));
			guard++;
		}
		if (guard > n + 2) {
			throw new IllegalStateException("Arc collection overflow");
		}
		return arc;
	}

	private int countInterior(List<Integer> arc) {
		int c = 0;
		for (int t = 1; t < arc.size() - 1; t++) {
			int u = arc.get(t);
			if (!isBoundary.get(u)) {
				c++;
			}
		}
		return c;
	}

	// Helpers

	private boolean isSynthetic(Vertex v) {
		// If Vertex has isSynthetic(), use it. Otherwise assume false.
		try {
			return (boolean) v.getClass().getMethod("isSynthetic").invoke(v);
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean isCCW(List<Vertex> ring) {
		int n = ring.size();
		if (n < 3) {
			return true;
		}
		double x0 = ring.get(0).getX();
		double sum = 0.0;
		for (int i = 0; i < n; i++) {
			double xi = ring.get(i).getX() - x0;
			double yPrev = ring.get((i == 0) ? n - 1 : i - 1).getY();
			double yNext = ring.get((i + 1) % n).getY();
			sum += xi * (yPrev - yNext);
		}
		double total = 0.5 * sum;
		return total < 0;
	}

	private List<Vertex> buildBoundaryFromEdges(Set<IQuadEdge> boundaryEdges) {
		if (boundaryEdges.isEmpty()) {
			throw new IllegalArgumentException("boundaryEdges empty");
		}

		// adjacency among boundary vertices
		Map<Vertex, List<Vertex>> adj = new HashMap<>();
		for (IQuadEdge e : boundaryEdges) {
			Vertex a = e.getA();
			Vertex b = e.getB();
			if (!indexOf.containsKey(a) || !indexOf.containsKey(b)) {
				throw new IllegalArgumentException("Boundary edge contains vertex not in graph vertex set");
			}
			adj.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
			adj.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
		}

		// pick start as lexicographically smallest (x, y)
		Vertex start = adj.keySet().stream().min(Comparator.<Vertex>comparingDouble(Vertex::getX).thenComparingDouble(Vertex::getY)).orElseThrow();

		// walk the loop
		List<Vertex> loop = new ArrayList<>();
		Vertex prev = null;
		Vertex curr = start;
		do {
			loop.add(curr);
			List<Vertex> neigh = adj.get(curr);
			if (neigh == null || neigh.isEmpty()) {
				throw new IllegalStateException("Boundary edges do not form a contiguous loop");
			}
			Vertex next;
			if (neigh.size() == 1) {
				next = neigh.get(0);
			} else if (neigh.size() == 2) {
				next = (Objects.equals(neigh.get(0), prev) ? neigh.get(1) : neigh.get(0));
			} else {
				// If more than 2, choose by smallest turning angle to ensure following the
				// boundary
				next = chooseNextByAngle(prev, curr, neigh);
			}
			prev = curr;
			curr = next;
		} while (!curr.equals(start));

		if (!isCCW(loop)) {
			Collections.reverse(loop);
		}
		return loop;
	}

	private Vertex chooseNextByAngle(Vertex prev, Vertex curr, List<Vertex> candidates) {
		if (prev == null) {
			// pick by smallest angle from +x axis
			double cx = curr.getX(), cy = curr.getY();
			return candidates.stream().min(Comparator.comparingDouble(n -> Math.atan2(n.getY() - cy, n.getX() - cx))).orElseThrow();
		}
		double vx = curr.getX() - prev.getX();
		double vy = curr.getY() - prev.getY();
		double cx = curr.getX(), cy = curr.getY();
		return candidates.stream().filter(n -> !n.equals(prev)).min(Comparator.comparingDouble(n -> {
			double wx = n.getX() - cx, wy = n.getY() - cy;
			double ang = Math.atan2(vx * wy - vy * wx, vx * wx + vy * wy); // signed angle
			return -ang; // prefer left turn (CCW)
		})).orElseThrow();
	}

	// Convex hull (Monotone chain), returns CCW loop
	private static List<Vertex> convexHullCCW(List<Vertex> pts) {
		if (pts.size() <= 3) {
			return new ArrayList<>(pts);
		}

		List<Vertex> sorted = new ArrayList<>(pts);
		sorted.sort(Comparator.<Vertex>comparingDouble(Vertex::getX).thenComparingDouble(Vertex::getY));

		List<Vertex> lower = new ArrayList<>();
		for (Vertex p : sorted) {
			while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0) {
				lower.remove(lower.size() - 1);
			}
			lower.add(p);
		}
		List<Vertex> upper = new ArrayList<>();
		for (int i = sorted.size() - 1; i >= 0; i--) {
			Vertex p = sorted.get(i);
			while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0) {
				upper.remove(upper.size() - 1);
			}
			upper.add(p);
		}
		lower.remove(lower.size() - 1);
		upper.remove(upper.size() - 1);
		List<Vertex> hull = new ArrayList<>(lower);
		hull.addAll(upper);
		return hull;
	}

	private static double cross(Vertex a, Vertex b, Vertex c) {
		double bx = b.getX() - a.getX();
		double by = b.getY() - a.getY();
		double cx = c.getX() - a.getX();
		double cy = c.getY() - a.getY();
		return bx * cy - by * cx;
	}

	public static Graph<Vertex, IQuadEdge> toGraph(IIncrementalTin tin) {
		Objects.requireNonNull(tin, "tin is null");

		Graph<Vertex, IQuadEdge> g = new org.jgrapht.graph.SimpleGraph<>(IQuadEdge.class);

		boolean hasPolygonConstraint = tin.getConstraints().stream().anyMatch(IConstraint::isPolygon);

		// When constrained: build graph strictly from triangles reported by
		// TriangleCollector.
		if (hasPolygonConstraint) {
			Set<IQuadEdge> seen = new HashSet<>();
			org.tinfour.utils.TriangleCollector.visitSimpleTriangles(tin, tri -> {
				IQuadEdge[] edges = new IQuadEdge[] { tri.getEdgeA(), tri.getEdgeB(), tri.getEdgeC() };
				for (IQuadEdge e : edges) {
					IQuadEdge base = e.getBaseReference();
					Vertex a = base.getA();
					Vertex b = base.getB();
					if (a == null || b == null) {
						continue;
					}
					if (a.isSynthetic() || b.isSynthetic()) {
						continue;
					}

					// Add vertices (idempotent in JGraphT)
					g.addVertex(a);
					g.addVertex(b);

					// Dedup by base reference identity
					if (seen.add(base)) {
						g.addEdge(a, b, base);
					}
				}
			});
		} else {
			// Unconstrained: include all non-synthetic base edges from TIN edge set
			Set<IQuadEdge> seen = new HashSet<>();
			tin.vertices().forEach(v -> {
				if (!v.isSynthetic()) {
					g.addVertex(v);
				}
			});

			tin.edges().forEach(e -> {
				IQuadEdge base = e.getBaseReference();
				Vertex a = base.getA();
				Vertex b = base.getB();
				if (a == null || b == null) {
					return;
				}
				if (a.isSynthetic() || b.isSynthetic()) {
					return;
				}

				// Ensure vertices exist
				if (!g.containsVertex(a)) {
					g.addVertex(a);
				}
				if (!g.containsVertex(b)) {
					g.addVertex(b);
				}

				if (seen.add(base)) {
					g.addEdge(a, b, base);
				}
			});
		}

		return g;
	}

	public static JGraphTriangulation fromTin(IIncrementalTin tin) {
		Graph<Vertex, IQuadEdge> g = toGraph(tin);
		List<Vertex> boundary = boundaryLoopFromTin(tin);
		return new JGraphTriangulation(g, boundary);
	}

	private static List<Vertex> boundaryLoopFromTin(IIncrementalTin tin) {
		// Prefer first polygon constraint as boundary
		IConstraint poly = tin.getConstraints().stream().filter(IConstraint::isPolygon).findFirst().orElse(null);

		if (poly != null) {
			List<Vertex> ring = new ArrayList<>(poly.getVertices());
			if (!ring.isEmpty() && ring.get(0).equals(ring.get(ring.size() - 1))) {
				ring.remove(ring.size() - 1);
			}
			return ring;
		}

		// Fallback: perimeter walk
		List<IQuadEdge> perim = new ArrayList<>();
		tin.getPerimeter().forEach(perim::add);
		if (perim.isEmpty()) {
			throw new IllegalStateException("TIN perimeter is empty; cannot build boundary loop");
		}

		List<Vertex> loop = new ArrayList<>();
		loop.add(perim.get(0).getA());
		for (IQuadEdge e : perim) {
			Vertex last = loop.get(loop.size() - 1);
			Vertex a = e.getA();
			Vertex b = e.getB();
			if (last.equals(a)) {
				loop.add(b);
			} else if (last.equals(b)) {
				loop.add(a);
			} else {
				throw new IllegalStateException("Perimeter edges are not contiguous");
			}
		}
		if (loop.size() > 1 && loop.get(0).equals(loop.get(loop.size() - 1))) {
			loop.remove(loop.size() - 1);
		}
		return loop;
	}

}