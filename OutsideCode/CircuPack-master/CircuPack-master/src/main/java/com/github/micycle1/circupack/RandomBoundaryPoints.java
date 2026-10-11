package com.github.micycle1.circupack;

import java.util.Random;

/**
 * Port of rand_bdry_pts.m Given a closed polygonal path (graphX,graphY) and M,
 * pick M random points uniformly on total arclength and return two arrays
 * (x,y). The list is not closed.
 */
public final class RandomBoundaryPoints {
	private RandomBoundaryPoints() {
	}

	public static double[][] sample(double[] graphX, double[] graphY, int M, Random rnd) {
		if (rnd == null) {
			rnd = new Random();
		}
		if (graphX == null || graphY == null || graphX.length != graphY.length || graphX.length < 3 || M < 3) {
			return new double[][] { new double[0], new double[0] };
		}
		int gc = graphX.length;
		// ensure closed
		boolean closed = Math.hypot(graphX[0] - graphX[gc - 1], graphY[0] - graphY[gc - 1]) < 1e-3;
		int N = closed ? gc : gc + 1;

		double[] gx = new double[N];
		double[] gy = new double[N];
		System.arraycopy(graphX, 0, gx, 0, gc);
		System.arraycopy(graphY, 0, gy, 0, gc);
		if (!closed) {
			gx[N - 1] = graphX[0];
			gy[N - 1] = graphY[0];
		}

		double[] marks = new double[N];
		for (int i = 1; i < N; i++) {
			double dx = gx[i] - gx[i - 1];
			double dy = gy[i] - gy[i - 1];
			marks[i] = marks[i - 1] + Math.hypot(dx, dy);
		}
		double L = marks[N - 1];

		double[] arclocs = new double[M];
		for (int i = 0; i < M; i++) {
			arclocs[i] = rnd.nextDouble() * L;
		}
		java.util.Arrays.sort(arclocs);

		double[] bx = new double[M];
		double[] by = new double[M];
		int seg = 0;
		for (int i = 0; i < M; i++) {
			double t = arclocs[i];
			while (seg + 1 < N && marks[seg + 1] < t) {
				seg++;
			}
			double s0 = marks[seg];
			double s1 = marks[seg + 1];
			double ratio = (t - s0) / Math.max(1e-16, (s1 - s0));
			bx[i] = gx[seg] + ratio * (gx[seg + 1] - gx[seg]);
			by[i] = gy[seg] + ratio * (gy[seg + 1] - gy[seg]);
		}
		return new double[][] { bx, by };
	}
}