package com.github.quickhull3d;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class ExactJavaPredicatesTest {

	@Test
	public void testOrient() {
		Vertex p0 = new Vertex(1, 1, 1, 0);
		Vertex p1 = new Vertex(2, 1, 1, 1);
		Vertex p2 = new Vertex(1, 2, 1, 2);
		Vertex p3 = new Vertex(1, 1, 2, 3);
		Vertex p4 = new Vertex(2, 2, 1, 4);

		/*
		 * From http://www.cs.berkeley.edu/~jrs/papers/robustr.pdf: orient(a,b,c,d)
		 * returns a positive value if d lies below the oriented plane passing through
		 * a, b and c. By oriented plane, I mean that a, b and c appear in
		 * counterclockwise order when viewed from above the plane.
		 */
		assertTrue(ExactJavaPredicates.orient(p0, p1, p2, p3) < 0);
		assertTrue(ExactJavaPredicates.orient(p0, p2, p1, p3) > 0);
		assertTrue(ExactJavaPredicates.orient(p0, p1, p2, p4) == 0);

		Vertex pa = new Vertex(1.0, 0.0, 1.0, 10);
		Vertex pb = new Vertex(-1.0, 0.0, -1.0, 11);
		Vertex pc = new Vertex(-1.0, 0.0, 0.0, 12);

		Vertex abovePlane = new Vertex(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, 13); // expected negative
		Vertex belowPlane = new Vertex(-Double.MIN_VALUE, -Double.MIN_VALUE, -Double.MIN_VALUE, 14); // expected positive
		Vertex onPlane = new Vertex(0.0, 0.0, 0.0, 15); // expected zero

		Object[][] cases = new Object[][] { { abovePlane, -1.0 }, { belowPlane, 1.0 }, { onPlane, 0.0 } };

		for (Object[] c : cases) {
			Vertex p = (Vertex) c[0];
			double expectedSign = (double) c[1];

			double det = ExactJavaPredicates.orient(pa, pb, pc, p);
			assertTrue(det == expectedSign || Math.signum(det) == Math.signum(expectedSign));
		}
	}

	@Test
	public void testOrientFixtures() throws Exception {
		// fixtures from https://github.com/mourner/robust-predicates
		InputStream in = getClass().getClassLoader().getResourceAsStream("orient3d.txt");
		assertNotNull(in, "Could not find orient3d.txt on the test classpath");

		try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			int lineNo = 0;

			while ((line = br.readLine()) != null) {
				lineNo++;
				line = line.trim();

				// skip blanks / comments
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String[] t = line.split("\\s+");
				// format: id + 12 coords (ax ay az bx by bz cx cy cz dx dy dz) + expectedSign
				if (t.length != 14) {
					fail("Bad fixture at line " + lineNo + ": expected 14 columns, got " + t.length + "\n" + line);
				}

				long id = Long.parseLong(t[0]);

				double ax = Double.parseDouble(t[1]);
				double ay = Double.parseDouble(t[2]);
				double az = Double.parseDouble(t[3]);

				double bx = Double.parseDouble(t[4]);
				double by = Double.parseDouble(t[5]);
				double bz = Double.parseDouble(t[6]);

				double cx = Double.parseDouble(t[7]);
				double cy = Double.parseDouble(t[8]);
				double cz = Double.parseDouble(t[9]);

				double dx = Double.parseDouble(t[10]);
				double dy = Double.parseDouble(t[11]);
				double dz = Double.parseDouble(t[12]);

				int expectedSign = Integer.parseInt(t[13]); // typically -1, 0, or +1

				Vertex a = new Vertex(ax, ay, az, 0);
				Vertex b = new Vertex(bx, by, bz, 1);
				Vertex c = new Vertex(cx, cy, cz, 2);
				Vertex d = new Vertex(dx, dy, dz, 3);

				double det = ExactJavaPredicates.orient(a, b, c, d);

				boolean ok = (det == expectedSign) || (Math.signum(det) == Math.signum(expectedSign));

				if (!ok) {
					fail("Fixture failed (id=" + id + ", line=" + lineNo + "): expected sign " + expectedSign + " but det=" + det + "\n" + line);
				}
			}
		}
	}

}