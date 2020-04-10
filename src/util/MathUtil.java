package util;
import java.awt.Color;

/**
 * Miscellaneous math utilities. 
 * As of December 2012, many of number formatting methods seem to be OBE and are not called.
 * @author kstephe2
 */

public class MathUtil {
	public static String Error = "";
	public static double accuracy = 10E-4;

	
	// ========================== formatting utilities (many not called) =================
	
	public static double parseDouble(String s) throws NumberFormatException {
		try {
			Double d = Double.parseDouble(s);
			return d.doubleValue();
		} catch (NumberFormatException e) {
			Error = "Number Format Exception";
			throw e;
		}
	}

	public static String d2String(double d, double accuracy) {
		// double tol = complex.MathComplex.getTolerance();
		int nPower = 1;
		for (int i = 1; i < 16; i++)
			if (accuracy > Math.pow(10, -i)) {
				nPower = i;
				break;
			}
		Double D = Double.valueOf(Math.round(d * Math.pow(10, nPower))
				/ Math.pow(10, nPower));
		String t = D.toString();
		if (t.equalsIgnoreCase("-0"))
			t = "0";
		return t;
	}

	public static String d2String(double d) {
		// double tol = complex.MathComplex.getTolerance();
		int nPower = 1;
		for (int i = 1; i < 16; i++)
			if (accuracy > Math.pow(10, -i)) {
				nPower = i;
				break;
			}
		Double D = Double.valueOf(Math.round(d * Math.pow(10, nPower))
				/ Math.pow(10, nPower));
		String t = "";
		boolean isInteger = Math.abs(Math.round(D.doubleValue())
				- D.doubleValue()) < accuracy;
		boolean isBig = Math.abs(D.doubleValue()) > 1E7;
		if (isInteger && !isBig)
			t = Integer.toString((int) D.intValue());
		else
			t = D.toString();
		if (t.equalsIgnoreCase("-0"))
			t = "0";
		if (t.length() > 1 && t.charAt(0) == '0' && t.charAt(1) == '.')
			t = t.replaceFirst("0.", ".");
		if ((t.compareTo("0") == 0 || t.compareTo("0.0") == 0) && d != 0.0)
			t = Double.toString(D.doubleValue());
		return t;
	}

	public static String d2StringNew(double d) {
		if (Math.abs(d) < accuracy)
			return "0";
		if (d < 0)
			return "-" + d2StringNew(-d);
		String t = d2String(d);// Double.toString(d);
		if (t.length() < 5)
			return t;
		int m = (int) (Math.floor(Math.log(Math.abs(d)) / Math.log(10)));
		double rho = d * Math.pow(10, -m);
		if (Math.abs(rho) > 10)
			System.err.println("Error computing mantissa for " + d);
		String mantiss = Double.toString(rho);
		if (mantiss.length() >= 6)
			mantiss = mantiss.substring(0, 6);
		return mantiss + "E" + Integer.toString(m);
	}

	public static String d2StringSign(double d, double accuracy) {
		// double tol = complex.MathComplex.getTolerance();
		if (Math.abs(d) < accuracy)
			return "0";
		int nPower = 1;
		for (int i = 1; i < 16; i++)
			if (accuracy > Math.pow(10, -i)) {
				nPower = i;
				break;
			}
		Double D = Double.valueOf(Math.round(d * Math.pow(10, nPower))
				/ Math.pow(10, nPower));
		String t = D.toString();
		if (d > 0)
			t = "+" + t;
		if (t.equalsIgnoreCase("-0"))
			t = "0";
		return t;
	}

	public static String d2StringSign(double d) {
		// double tol = complex.MathComplex.getTolerance();
		if (Math.abs(d) < accuracy)
			return "0";
		int nPower = 1;
		for (int i = 1; i < 16; i++)
			if (accuracy > Math.pow(10, -i)) {
				nPower = i;
				break;
			}
		Double D = Double.valueOf(Math.round(d * Math.pow(10, nPower))
				/ Math.pow(10, nPower));
		String t = D.toString();
		if (d > 0)
			t = "+" + t;
		if (t.equalsIgnoreCase("-0"))
			t = "0";
		return t;
	}

	public static Color interpolateColor(Color c1, Color c2, int j, int N) {
		int i = j % N;
		int r1 = c1.getRed();
		int g1 = c1.getGreen();
		int b1 = c1.getBlue();
		int r2 = c2.getRed();
		int g2 = c2.getGreen();
		int b2 = c2.getBlue();
		// int t;
		// if (r2 < r1) {
		// t = r1;
		// r1 = r2;
		// r2 = t;
		// }
		// if (b2 < b1) {
		// t = b1;
		// b1 = b2;
		// b2 = t;
		// }
		// if (g2 < g1) {
		// t = g1;
		// g1 = g2;
		// g2 = t;
		// }
		double dr = (r2 - r1) / N;
		double dg = (g2 - g1) / N;
		double db = (b2 - b1) / N;
		int r = r1 + (int) (dr * i);
		int g = g1 + (int) (dg * i);
		int b = b1 + (int) (db * i);
		r %= 256;
		g %= 256;
		b %= 256;
		if (r < 0)
			r += 256;
		if (g < 0)
			g += 256;
		if (b < 0)
			b += 256;

		return new Color(r, g, b);
	}
	
	public static String putCoeff(String Eq, String coeff, String exp) {
		if (exp.equals("")) {
			if (coeff.equalsIgnoreCase("0") || coeff.equalsIgnoreCase("-0"))
				return Eq;
			return Eq + coeff;
		}
		String temp = Eq.trim();
		if ((temp.charAt(temp.length() - 1) == '=')
				|| (temp.charAt(temp.length() - 1) == '<')
				|| (temp.charAt(temp.length() - 1) == '>')
				|| (temp.charAt(temp.length() - 1) == '(')) {
			if (coeff.equalsIgnoreCase("0") || coeff.equalsIgnoreCase("-0"))
				return Eq;
			if (coeff.equalsIgnoreCase("1") || coeff.equalsIgnoreCase("+1"))
				return Eq + exp;
			if (coeff.equalsIgnoreCase("-1"))
				return Eq + "-" + exp;
			return Eq + coeff + exp;
		} else {
			if (coeff.equalsIgnoreCase("0") || coeff.equalsIgnoreCase("-0"))
				return Eq;
			if (coeff.equalsIgnoreCase("1") || coeff.equalsIgnoreCase("+1"))
				return Eq + "+" + exp;
			if (coeff.equalsIgnoreCase("-1"))
				return Eq + "-" + exp;
			return Eq + coeff + exp;
		}
	}

	/**
	 * Generate a random Color object
	 * @return Color
	 */
	public static Color randomColor() {
		int r = (int) (255 * Math.random());
		int g = (int) (255 * Math.random());
		int b = (int) (255 * Math.random());
		return new Color(r, g, b);
	}

	/**
	 * @param b boolean
	 * @return 1 if b, else 0
	 */
	public static int boolToInt(boolean b) {
		if (b)
			return 1;
		else
			return 0;
	}

	/**
	 * @param k int
	 * @return boolean: true if k!=0, else false
	 */
	public static boolean intToBool(int k) {
		if (k != 0)
			return true;
		else
			return false;
	}

	/**
	 * Converting string to int when string might be a double
	 * @param s String
	 * @return int
	 * @throws NumberFormatException
	 */
	public static int MyInteger(String s) throws NumberFormatException {
		try {
			int i = Integer.parseInt(s);
			return i;
		} catch (NumberFormatException nfe) {
			double d = Double.parseDouble(s);
			int i = (int) (d + .5); // round to integer
			return i;
		}
	}

}
