package com.github.micycle1.circupack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.micycle1.circupack.triangulation.PackFileTriangulation;

public class PackFileTriangulationTest {

	@TempDir
	Path tempDir;

	@Test
	void readsPackFileFlowersAndBoundaryLoop() throws IOException {
		Path packFile = tempDir.resolve("sample.p");
		Files.writeString(packFile, SAMPLE_PACK_FILE);

		PackFileTriangulation tri = PackFileTriangulation.fromFile(packFile);

		assertEquals(21, tri.getVertexCount());
		assertIterableEquals(List.of(1, 2, 3, 4, 5, 6), tri.getFlower(0));
		assertFalse(tri.isBoundaryVertex(0));

		assertTrue(tri.isBoundaryVertex(7));
		assertIterableEquals(List.of(14, 6, 5, 8), tri.getFlower(7));
		assertIterableEquals(List.of(7, 8, 18, 17, 16, 20, 19, 11, 10, 9, 15, 14), tri.getBoundaryLoop());

		assertIterableEquals(List.of(0, 1, 2, 3, 4, 5, 6, 12, 13), tri.getInteriorVertices());
	}

	@Test
	void multipleBoundaryComponentsUsesLargestLoop() throws IOException {
		Path packFile = tempDir.resolve("multiple-boundaries.p");
		Files.writeString(packFile, MULTIPLE_BOUNDARY_COMPONENTS_PACK_FILE);

		PackFileTriangulation tri = PackFileTriangulation.fromFile(packFile);

		assertEquals(7, tri.getVertexCount());
		assertIterableEquals(List.of(3, 4, 5, 6), tri.getBoundaryLoop());
		for (int v = 0; v < tri.getVertexCount(); v++) {
			assertTrue(tri.isBoundaryVertex(v));
		}
		assertTrue(tri.getInteriorVertices().isEmpty());
	}

	private static final String SAMPLE_PACK_FILE = """
			NODECOUNT:   21

			GEOMETRY:   euclidean

			ALPHA/BETA/GAMMA:   1  8  2

			FLOWERS:

			1 6   2 3 4 5 6 7 2
			2 7   3 1 7 10 11 12 13 3
			3 5   14 4 1 2 13 14
			4 5   17 5 1 3 14 17
			5 7   9 6 1 4 17 18 19 9
			6 5   8 7 1 5 9 8
			7 7   10 2 1 6 8 15 16 10
			8 3   15 7 6 9
			9 3   8 6 5 19
			10 3   11 2 7 16
			11 2   12 2 10
			12 3   20 13 2 11
			13 5   14 3 2 12 20 14
			14 6   17 4 3 13 20 21 17
			15 2   16 7 8
			16 2   10 7 15
			17 4   18 5 4 14 21
			18 2   19 5 17
			19 2   9 5 18
			20 3   21 14 13 12
			21 2   17 14 20

			RADII:
			1.166739800000000e-01

			CENTERS:
			 0.000000000000000e+00 0.000000000000000e+00

			END
			""";

	private static final String MULTIPLE_BOUNDARY_COMPONENTS_PACK_FILE = """
			NODECOUNT:   7

			FLOWERS:

			1 2   3 2 2
			2 2   1 3 3
			3 2   2 1 1
			4 2   7 5 5
			5 2   4 6 6
			6 2   5 7 7
			7 2   6 4 4

			END
			""";
}
