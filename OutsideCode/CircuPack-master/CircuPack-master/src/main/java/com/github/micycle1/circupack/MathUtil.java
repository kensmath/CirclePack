package com.github.micycle1.circupack;

final class MathUtil {

	private MathUtil() {
	}

	// Equivalent to cosAngle.m
	// Cosine of angle at circle with radius r in a triple of tangent circles with
	// radii r, r1, r2
	public static double cosAngle(double r, double r1, double r2) {
		double c = r1 * r2;
		double denom = r * r + r * (r1 + r2) + c;
		if (denom <= 1e-16) {
			return 1.0;
		}
		double val = 1.0 - 2.0 * c / denom;
		if (val > 1.0) {
			val = 1.0;
		}
		if (val < -1.0) {
			val = -1.0;
		}
		return val;
	}

	// Equivalent to cosCorner.m, but we return angle directly to avoid clip/acos
	// repeated.
	// Angle at (x1,y1) in triangle (x1,y1)-(x2,y2)-(x3,y3)
	public static double angleAtCorner(double x1, double y1, double x2, double y2, double x3, double y3) {
		double ax = x2 - x1, ay = y2 - y1;
		double bx = x3 - x1, by = y3 - y1;

		double n2 = ax * ax + ay * ay;
		double n3 = bx * bx + by * by;
		if (n2 == 0.0 || n3 == 0.0) {
			return 0.0;
		}

		double denom = Math.sqrt(n2) * Math.sqrt(n3);
		if (denom <= 5e-17) {
			return 0.0;
		}

		double cosang = (ax * bx + ay * by) / denom;
		if (cosang > 1.0) {
			cosang = 1.0;
		} else if (cosang < -1.0) {
			cosang = -1.0;
		}

		return Math.acos(cosang);
	}
}