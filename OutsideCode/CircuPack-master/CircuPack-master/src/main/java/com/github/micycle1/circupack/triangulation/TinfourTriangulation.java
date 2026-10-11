package com.github.micycle1.circupack.triangulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IQuadEdge;
import org.tinfour.common.Vertex;

public class TinfourTriangulation implements Triangulation {

	protected final IIncrementalTin tin;
	protected final List<Vertex> vertices;
	protected final List<Integer> boundary;
	protected final List<Integer> interiorVertices;
	protected final BitSet isBoundary;
	protected final HashMap<Vertex, Integer> indexOf;
	protected final List<List<Integer>> flowers;

	final IConstraint constraint;

	public TinfourTriangulation(IIncrementalTin tin) {
		this.tin = tin;
		constraint = tin.getConstraints().stream().filter(c -> c.isPolygon()).findFirst().orElse(null);

		indexOf = new HashMap<>();
		vertices = new ArrayList<>();

		// 1) collect vertices
		tin.vertices().forEach(v -> {
			if (v.isSynthetic()) {
				// just in case...
				return;
			}
			if (indexOf.putIfAbsent(v, vertices.size()) == null) {
				vertices.add(v); // id is the index in the List
			}
		});

		// 2) populate flowers
		Set<Vertex> processed = new HashSet<>();
		flowers = new ArrayList<>(Collections.nCopies(vertices.size(), null));

		tin.edges().forEach(e -> {
			Vertex A = e.getA();
			Integer ai = indexOf.get(A);
			if (ai != null && processed.add(A)) {
				var fA = buildFlower(e);
				flowers.set(ai, fA);
			}

			// do B here in case it's not caught in future iter
			Vertex B = e.getB();
			Integer bi = indexOf.get(B);
			if (bi != null && processed.add(B)) {
				var fB = buildFlower(e.getDual());
				flowers.set(bi, fB);
			}
		});

		// 3) compute CCW boundary
		boundary = buildBoundary();
		isBoundary = new BitSet(vertices.size());
		boundary.forEach(i -> {
			isBoundary.set(i);
		});

		interiorVertices = new ArrayList<>(vertices.stream().map(v -> indexOf.get(v)).toList());
		interiorVertices.removeAll(boundary);

		postprocessBoundaryFlowersToInteriorOpen();
	}

	/*
	 * In other words, don’t hardcode “CCW from prev to next.” Instead, “from prev
	 * to next, choose the arc that runs through interior neighbors.” <p> boundary
	 * loop is CCW; “prev” is the CCW predecessor on the loop, “next” is the CCW
	 * successor. The interior wedge is CCW from prev to next.
	 */
	private void postprocessBoundaryFlowersToInteriorOpen() {
		int m = boundary.size();
		// position of each boundary vertex in the loop
		int[] pos = new int[vertices.size()];
		Arrays.fill(pos, -1);
		for (int i = 0; i < m; i++) {
			pos[boundary.get(i)] = i;
		}

		for (int i = 0; i < m; i++) {
			int w = boundary.get(i);
			List<Integer> nbrs = new ArrayList<>(flowers.get(w)); // currently CCW-sorted
			int deg = nbrs.size();
			if (deg < 2) {
				continue;
			}

			int prev = boundary.get((i - 1 + m) % m);
			int next = boundary.get((i + 1) % m);

			int ip = nbrs.indexOf(prev);
			int in = nbrs.indexOf(next);
			if (ip < 0 || in < 0) {
				// Something off with neighbor set; skip
				continue;
			}

			// Build two arcs between prev and next along the CCW neighbor order:
			// arcCCW: ip -> in stepping +1; arcCW: ip -> in stepping -1
			List<Integer> arcCCW = collectArc(nbrs, ip, in, +1);
			List<Integer> arcCW = collectArc(nbrs, ip, in, -1);

			int intCountCCW = countInterior(arcCCW);
			int intCountCW = countInterior(arcCW);

			List<Integer> chosen = (intCountCCW >= intCountCW) ? arcCCW : arcCW;

			// Ensure it is open with first=prev and last=next
			if (!chosen.isEmpty() && chosen.get(0) != prev) {
				Collections.reverse(chosen); // put prev at front, next at end
			}
			flowers.set(w, chosen);
		}
	}

	private List<Integer> collectArc(List<Integer> nbrs, int from, int to, int stepSign) {
		int n = nbrs.size();
		int step = (stepSign >= 0) ? 1 : -1;
		List<Integer> arc = new ArrayList<>(n + 1);
		int k = from;
		arc.add(nbrs.get(k)); // include 'from'
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

	/**
	 * The packer index of a Tinfour vertex, e.g. for specifying polygon corner
	 * vertices.
	 */
	public int indexOf(Vertex v) {
		Integer i = indexOf.get(v);
		if (i == null) {
			throw new IllegalArgumentException("Vertex not found in triangulation: " + v);
		}
		return i;
	}

	@Override
	public int getVertexCount() {
		return vertices.size();
	}

	// NOTE
	/*
	 * For each boundary vertex w in the hex, getFlower(w) must be the open CCW
	 * neighbor list that goes [prevBoundary, interiorCenter, nextBoundary] (or the
	 * same three in correct CCW order).
	 */
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

	private List<Integer> buildBoundary() {
		List<Vertex> boundary;
		if (constraint == null) {
			// unconstrained
			boundary = boundaryFromPerimeter();
		} else {
			boundary = constraint.getVertices();
		}

		// drop the last if it closes the loop (shouldn't)
		if (boundary.size() > 1 && boundary.get(0).equals(boundary.get(boundary.size() - 1))) {
			boundary.remove(boundary.size() - 1);
		}

		if (!isCCW(boundary)) {
			Collections.reverse(boundary);
		}

		return boundary.stream().map(v -> indexOf.get(v)).toList();
	}

	private List<Vertex> boundaryFromPerimeter() {
		List<IQuadEdge> perimeter = new ArrayList<>();
		tin.getPerimeter().forEach(perimeter::add);

		List<Vertex> boundary = new ArrayList<>();
		boundary.add(perimeter.get(0).getA());
		for (IQuadEdge e : perimeter) {
			Vertex last = boundary.get(boundary.size() - 1);
			if (last.equals(e.getA())) {
				boundary.add(e.getB());
			} else if (last.equals(e.getB())) {
				boundary.add(e.getA());
			} else {
				throw new IllegalStateException("Perimeter edges are not contiguous");
			}
		}

		return boundary;
	}

	private List<Integer> buildFlower(IQuadEdge e) {
		Vertex A = e.getA();
		// Collect neighbors around A via pinwheel
		List<Vertex> neighbors = new ArrayList<>();
		e.pinwheel().forEach(ne -> {
			Vertex B = ne.getB();
			if (B == null || B.isSynthetic() || !indexOf.containsKey(B)) {
				return;
			}
			neighbors.add(B);
		});

		double ax = A.getX();
		double ay = A.getY();
		// Sort neighbors CCW by angle around A
		neighbors.sort(Comparator.comparingDouble(b -> Math.atan2(b.getY() - ay, b.getX() - ax)));

		return neighbors.stream().map(v -> indexOf.get(v)).toList();
	}

	public static boolean isCCW(List<Vertex> ring) {
		int n = ring.size();

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

}
