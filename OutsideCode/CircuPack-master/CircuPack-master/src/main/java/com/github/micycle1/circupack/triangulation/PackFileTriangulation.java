package com.github.micycle1.circupack.triangulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link Triangulation} backed by a CirclePack/GOPack {@code .p} pack file.
 * <p>
 * Pack files use 1-based vertex labels. This adapter converts them to the
 * project-wide 0-based convention. Only the combinatorics are used: the
 * {@code FLOWERS} section is parsed, while saved radii and centers are ignored.
 * In pack files, interior flowers repeat their first petal at the end; boundary
 * flowers are open and therefore identify the boundary loop.
 */
public class PackFileTriangulation implements Triangulation {

	private static final Logger LOGGER = Logger.getLogger(PackFileTriangulation.class.getName());

	private final int vertexCount;
	private final List<List<Integer>> flowers;
	private final List<Integer> boundary;
	private final BitSet isBoundary;
	private final List<Integer> interiorVertices;

	private PackFileTriangulation(int vertexCount, List<List<Integer>> flowers, List<Integer> boundary, BitSet isBoundary) {
		this.vertexCount = vertexCount;
		this.flowers = flowers;
		this.boundary = boundary;
		this.isBoundary = isBoundary;

		List<Integer> interior = new ArrayList<>();
		for (int v = 0; v < vertexCount; v++) {
			if (!isBoundary.get(v)) {
				interior.add(v);
			}
		}
		this.interiorVertices = Collections.unmodifiableList(interior);
	}

	/**
	 * Reads a CirclePack/GOPack {@code .p} file from the supplied path.
	 *
	 * @param filepath path to the {@code .p} file
	 * @return parsed triangulation
	 * @throws IOException if the file cannot be read
	 */
	public static PackFileTriangulation fromFile(String filepath) throws IOException {
		return fromFile(Path.of(filepath));
	}

	/**
	 * Reads a CirclePack/GOPack {@code .p} file from the supplied path.
	 *
	 * @param path path to the {@code .p} file
	 * @return parsed triangulation
	 * @throws IOException if the file cannot be read
	 */
	public static PackFileTriangulation fromFile(Path path) throws IOException {
		Objects.requireNonNull(path, "path is null");
		return parse(Files.readAllLines(path));
	}

	@Override
	public int getVertexCount() {
		return vertexCount;
	}

	@Override
	public List<Integer> getFlower(int v) {
		return flowers.get(v);
	}

	@Override
	public List<Integer> getBoundaryLoop() {
		return boundary;
	}

	@Override
	public boolean isBoundaryVertex(int v) {
		return isBoundary.get(v);
	}

	@Override
	public List<Integer> getInteriorVertices() {
		return interiorVertices;
	}

	private static PackFileTriangulation parse(List<String> lines) {
		int nodeCount = parseNodeCount(lines);
		if (nodeCount <= 0) {
			throw new IllegalArgumentException("Pack file NODECOUNT must be positive");
		}

		int flowersStart = findSection(lines, "FLOWERS");
		if (flowersStart < 0) {
			throw new IllegalArgumentException("Pack file does not contain a FLOWERS section");
		}

		List<List<Integer>> flowers = new ArrayList<>(Collections.nCopies(nodeCount, null));
		BitSet seen = new BitSet(nodeCount);
		BitSet isBoundary = new BitSet(nodeCount);
		Map<Integer, Integer> nextBoundary = new HashMap<>();
		Map<Integer, Integer> previousBoundary = new HashMap<>();

		for (int i = flowersStart + 1; i < lines.size(); i++) {
			String line = stripComment(lines.get(i)).trim();
			if (line.isEmpty()) {
				continue;
			}
			if (isSectionHeader(line)) {
				break;
			}

			int[] values = parseInts(line, i + 1);
			if (values.length < 4) {
				throw new IllegalArgumentException("Invalid FLOWERS line " + (i + 1) + ": expected vertex, degree, and petals");
			}

			int v = toIndex(values[0], nodeCount, i + 1);
			int degree = values[1];
			if (degree < 0) {
				throw new IllegalArgumentException("Invalid negative degree on FLOWERS line " + (i + 1));
			}
			if (seen.get(v)) {
				throw new IllegalArgumentException("Duplicate FLOWERS entry for vertex " + values[0]);
			}
			seen.set(v);

			int petalCount = values.length - 2;
			if (petalCount != degree + 1) {
				throw new IllegalArgumentException(
						"Invalid FLOWERS line " + (i + 1) + ": expected " + (degree + 1) + " petals, found " + petalCount);
			}

			List<Integer> petals = new ArrayList<>(petalCount);
			for (int j = 2; j < values.length; j++) {
				petals.add(toIndex(values[j], nodeCount, i + 1));
			}

			boolean closedInteriorFlower = petals.get(0).equals(petals.get(petals.size() - 1));
			if (closedInteriorFlower) {
				petals.remove(petals.size() - 1);
			} else {
				isBoundary.set(v);
				int previous = petals.get(0);
				int next = petals.get(petals.size() - 1);
				putUniqueBoundaryNeighbor(previousBoundary, v, previous, "previous");
				putUniqueBoundaryNeighbor(nextBoundary, v, next, "next");
			}
			flowers.set(v, Collections.unmodifiableList(petals));
		}

		for (int v = 0; v < nodeCount; v++) {
			if (!seen.get(v)) {
				throw new IllegalArgumentException("Missing FLOWERS entry for vertex " + (v + 1));
			}
		}

		List<Integer> boundary = buildBoundaryLoop(nodeCount, isBoundary, nextBoundary, previousBoundary);
		return new PackFileTriangulation(nodeCount, Collections.unmodifiableList(flowers), boundary, isBoundary);
	}

	private static int parseNodeCount(List<String> lines) {
		for (String raw : lines) {
			String line = stripComment(raw).trim();
			if (line.toUpperCase(Locale.ROOT).startsWith("NODECOUNT:")) {
				String value = line.substring(line.indexOf(':') + 1).trim();
				try {
					return Integer.parseInt(value);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid NODECOUNT: " + value, e);
				}
			}
		}
		throw new IllegalArgumentException("Pack file does not contain NODECOUNT");
	}

	private static int findSection(List<String> lines, String section) {
		String header = section.toUpperCase(Locale.ROOT) + ":";
		for (int i = 0; i < lines.size(); i++) {
			String line = stripComment(lines.get(i)).trim().toUpperCase(Locale.ROOT);
			if (line.equals(header)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isSectionHeader(String line) {
		if ("END".equalsIgnoreCase(line.trim())) {
			return true;
		}
		int colon = line.indexOf(':');
		if (colon < 0) {
			return false;
		}
		String name = line.substring(0, colon).trim();
		return !name.isEmpty() && name.chars().anyMatch(Character::isLetter);
	}

	private static String stripComment(String line) {
		int hash = line.indexOf('#');
		return hash >= 0 ? line.substring(0, hash) : line;
	}

	private static int[] parseInts(String line, int lineNumber) {
		String[] parts = line.trim().split("\\s+");
		int[] values = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				values[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid integer on line " + lineNumber + ": " + parts[i], e);
			}
		}
		return values;
	}

	private static int toIndex(int label, int nodeCount, int lineNumber) {
		if (label < 1 || label > nodeCount) {
			throw new IllegalArgumentException("Vertex label out of range on line " + lineNumber + ": " + label);
		}
		return label - 1;
	}

	private static void putUniqueBoundaryNeighbor(Map<Integer, Integer> map, int vertex, int neighbor, String side) {
		Integer existing = map.put(vertex, neighbor);
		if (existing != null && existing != neighbor) {
			throw new IllegalArgumentException("Boundary vertex " + (vertex + 1) + " has conflicting " + side + " neighbors");
		}
	}

	private static List<Integer> buildBoundaryLoop(int nodeCount, BitSet isBoundary, Map<Integer, Integer> nextBoundary,
			Map<Integer, Integer> previousBoundary) {
		if (isBoundary.isEmpty()) {
			return Collections.emptyList();
		}

		for (int v = isBoundary.nextSetBit(0); v >= 0; v = isBoundary.nextSetBit(v + 1)) {
			Integer previous = previousBoundary.get(v);
			Integer next = nextBoundary.get(v);
			if (previous == null || next == null) {
				throw new IllegalArgumentException("Boundary vertex " + (v + 1) + " is missing an open flower endpoint");
			}
			if (!isBoundary.get(previous) || !isBoundary.get(next)) {
				throw new IllegalArgumentException("Boundary flower for vertex " + (v + 1) + " does not start and end at boundary vertices");
			}
		}

		List<List<Integer>> components = new ArrayList<>();
		BitSet visited = new BitSet(nodeCount);
		for (int start = isBoundary.nextSetBit(0); start >= 0; start = isBoundary.nextSetBit(start + 1)) {
			if (!visited.get(start)) {
				components.add(walkBoundaryComponent(start, isBoundary, nextBoundary, visited));
			}
		}

		List<Integer> largest = components.get(0);
		for (List<Integer> component : components) {
			validateBoundaryPreviousLinks(component, previousBoundary);
			if (component.size() > largest.size()) {
				largest = component;
			}
		}

		if (components.size() > 1) {
			warnMultipleBoundaryComponents(components, largest, isBoundary.cardinality());
		}

		if (largest.size() > nodeCount) {
			throw new IllegalArgumentException("Boundary loop is longer than NODECOUNT");
		}
		return Collections.unmodifiableList(largest);
	}

	private static List<Integer> walkBoundaryComponent(int start, BitSet isBoundary, Map<Integer, Integer> nextBoundary, BitSet visited) {
		List<Integer> loop = new ArrayList<>();
		Set<Integer> localVisited = new HashSet<>();
		int current = start;
		while (true) {
			if (!isBoundary.get(current)) {
				throw new IllegalArgumentException("Boundary walk from vertex " + (start + 1) + " reached non-boundary vertex " + (current + 1));
			}
			if (!localVisited.add(current)) {
				if (current == start) {
					break;
				}
				throw new IllegalArgumentException(
						"Boundary flowers do not form a simple loop; walk from vertex " + (start + 1) + " revisited vertex " + (current + 1));
			}
			if (visited.get(current)) {
				throw new IllegalArgumentException(
						"Boundary component starting at vertex " + (start + 1) + " joins an already visited component at vertex " + (current + 1));
			}

			loop.add(current);
			visited.set(current);
			Integer next = nextBoundary.get(current);
			if (next == null) {
				throw new IllegalArgumentException("Boundary vertex " + (current + 1) + " has no next boundary neighbor");
			}
			current = next;
		}
		return loop;
	}

	private static void validateBoundaryPreviousLinks(List<Integer> loop, Map<Integer, Integer> previousBoundary) {
		for (int i = 0; i < loop.size(); i++) {
			int v = loop.get(i);
			int previous = loop.get(Math.floorMod(i - 1, loop.size()));
			if (previousBoundary.get(v) != previous) {
				throw new IllegalArgumentException("Boundary previous/next endpoints are inconsistent at vertex " + (v + 1));
			}
		}
	}

	private static void warnMultipleBoundaryComponents(List<List<Integer>> components, List<Integer> largest, int boundaryVertexCount) {
		List<String> smallerStarts = new ArrayList<>();
		for (List<Integer> component : components) {
			if (component != largest) {
				smallerStarts.add(Integer.toString(component.get(0) + 1));
			}
		}
		int ignoredCount = boundaryVertexCount - largest.size();
		LOGGER.warning(() -> "Pack file contains " + components.size() + " boundary components; using largest component starting at vertex "
				+ (largest.get(0) + 1) + " with " + largest.size() + " vertices and leaving " + ignoredCount
				+ " vertices on smaller boundary components as orphans. Smaller components start at vertices: " + String.join(", ", smallerStarts));
	}
}
