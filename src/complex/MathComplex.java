package complex;
import allMains.CPBase;
import graphObjects.CPCircle;
/**
 * <p>Title: Class for Complex Numbers with Static Functions</p>
 * <p>Description: Complex Arithmetic</p>
 * Implements all the static functions used with complex numbers.
 * <p>Copyright: Fedor Andreev, Copyright (c) 2003</p>
 * <p>Company: WIU </p>
 * @author Fedor Andreev
 * @version 1.1
 */

/* TODO: should define many operations by overloading. */
public class MathComplex {
  /** The variable indicates how small things
  * should be to qualify for "small"
  * when dividing by a small number */
  private static double tol=1E-7;
  /** Shows errors if any */
  private static String Error="";
  /** Imaginary unit */
  public static final Complex IM = new Complex(0d,1d);
  /**
   * Zero represented as a complex value
   */
  public static final Complex ZERO = new Complex(0d,0d);
  /**
   * Unity (number 1) represented as a complex value
   */
  public static final Complex ID = new Complex(1d,0d);

  /**
   * Inverse hyperbolic cosine for real values
   * @param x
   * @return
   */
  public static double aCosh(double x) {
	  return Math.log(x+Math.sqrt(x*x-1.0));
  }
  
  /**
   * Adds two complex numbers
   *
   * @param t1 Complex
   * @param t2 Complex
   * @return Complex
   */
  public static Complex add(Complex t1, Complex t2) {
    return t1.add(t2);
  }

  /**
   * Subtracts two complex numbers
   * @param t1 Complex
   * @param t2 Complex
   * @return Complex
   */
  public static Complex sub(Complex t1, Complex t2) {
    return t1.sub(t2);
  }

  /**
   * Subtracts a real number from the complex one
   * @param t1 Complex
   * @param t2 double
   * @return Complex
   */
  public static Complex sub(Complex t1, double t2) {
    return t1.sub(t2);
  }
  public static Complex sub(Complex t1, CPCircle t2) {
    return new Complex(t1.x-t2.x,t1.y-t2.y);
  }
  public static Complex sub(CPCircle t1, CPCircle t2) {
    return new Complex(t1.x-t2.x,t1.y-t2.y);
  }

  /**
   * Subtracts a complex number from the real one
   * @param t1 double
   * @param t2 Complex
   * @return Complex
   */
  public static Complex sub(double t1, Complex t2) {
    return new Complex(t1 - t2.real(), -t2.imag());
  }

  /**
   * Returns the additive inverse of the argument
   * @param t Complex
   * @return Complex
   */
  public static Complex uminus(Complex t) {
    return new Complex( -t.real(), -t.imag());
  }

  /**
   * Multiplies a real number by a complex one
   * @param t double
   * @param c Complex
   * @return Complex
   */
  public static Complex mult(double t,Complex c) {
    return c.times(t);
  }

  /**
   * Divides a by b
   * @param a Complex
   * @param b Complex
   * @return Complex
   */
  public static Complex divide(Complex a, Complex b) {
    return a.divide(b);
  }

  /**
   * Returns the square of the absolute value of the provided complex number. Use it, for instance,
   * to check for proximity. Works faster than abs(), because there is no need to take the square
   * root.
   * @param c Complex
   * @return double
   */
  public static double absSq(Complex c) {
    return (c.real() * c.real() + c.imag() * c.imag());
  }

  /**
   * Returns the square of the absolute value of the provided complex number. Use it, for instance,
   * to check for proximity. Works faster than abs(), because there is no need to take the square
   * root. The same as absSq()
   * @param c Complex
   * @return double
   */
  public static double normSq(Complex c) {
    return (c.real() * c.real() + c.imag() * c.imag());
  }

  /**
   * Returns the norm (that is the absolute value) of the provided complex number
   * @param c Complex
   * @return double
   */
  public static double norm(Complex c) {
    return Math.sqrt(normSq(c));
  }

  /**
   * Finds the distance between two given complex numbers
   * @param a Complex
   * @param b Complex
   * @return double
   */
  public static double distance(Complex a, Complex b) {
    return norm(sub(a,b));
  }

  /**
   * Finds the square of the distance between two given complex numbers Use it, for instance, to
   * check for proximity. Works faster than distance(), because there is no need to take the square
   * root.
   * @param a Complex
   * @param b Complex
   * @return double
   */
  public static double distanceSq(Complex a, Complex b) {
    return normSq(sub(a,b));
  }
  
  /**
   * Returns the square of the distance from (x,y) to a
   * @param x double
   * @param y double
   * @param a Complex
   * @return double
   */
  public static double distanceSq(double x, double y, Complex a) {
    return (x-a.real())*(x-a.real())+(y-a.imag())*(y-a.imag());
  }
  
  /**
   * Returns the square of the distance from (x,y) to (x1,y1)
   * @param x double
   * @param y double
   * @param x1 double
   * @param y1 double
   * @return double
   */
  public static double distanceSq(double x, double y, double x1, double y1) {
    return (x-x1)*(x-x1)+(y-y1)*(y-y1);
  }

  /**
   * Returns the absolute value of the given complex number. The same as norm()
   * @param c Complex
   * @return double
   */
  public static double abs(Complex c) {
    return norm(c);
  }
  
  /**
   * Finds exp(t), that is the exponential function of complex number t
   * @param t Complex
   * @return Complex
   */
  public static Complex exp(Complex t) {
    return new Complex(Math.exp(t.real()) * Math.cos(t.imag()),
                       Math.exp(t.real()) * Math.sin(t.imag()));
  }
  
  /**
   * Finds ln(t), that is the natural logarithm of complex number t
   * @param t Complex
   * @return Complex
   */
  public static Complex ln(Complex t) {
    double phi = 0d;
    double rho = abs(t);
    double tempx = Math.log(rho);
    if (Math.abs(t.real()) > tol)
      if(t.real()>0)
        if(t.imag()>=0)
          phi = Math.atan(t.imag() / t.real());
        else
          phi = 2*Math.PI+Math.atan(t.imag() / t.real());
      else
          phi = Math.atan(t.imag()/t.real()) + Math.PI;
    else {
      if (t.real() > 0)
        phi = CPBase.piby2;
      if (t.real() < 0)
        phi = 3 * CPBase.piby2;
    }
    return new Complex(tempx, phi);
  }
  
  /**
   * Finds a^b, that is complex number a raised to a complex power b
   * @param a Complex
   * @param b Complex
   * @return Complex
   */
  public static Complex pow(Complex a, Complex b) {
    // a^b = e^{ln(a)*b}
    return exp(ln(a).times(b));
  }
  
  /**
   * Finds a^l, that is complex number a raised to an integer l
   * @param a Complex
   * @param l int
   * @return Complex
   */
  public static Complex pow(Complex a, int l) {
    Complex s = new Complex(1.0);
    if (l==0) return s;
    if (l<0) {
    	for (int i = 0; i < -l; i++)
    		s = s.divide(a);
    }
    else { 
    	for (int i = 0; i < l; i++)
    		s = s.times(a);
    }
    return s;
  }
  
  /**
   * Finds the cosine of the given complex number
   * @param c Complex
   * @return Complex
   */
  public static Complex cos(Complex c) {
    Complex d = exp(c.mult(MathComplex.IM));
    return (d.add(d.reciprocal())).divide(2.0);
  }
  
  /**
   * Finds the sine of the given complex number
   * @param c Complex
   * @return Complex
   */
  public static Complex sin(Complex c) {
    Complex d = exp(c.mult(MathComplex.IM));
    return (d.sub(d.reciprocal())).divide(MathComplex.IM.mult(2d));
  }
  
  /**
   * Returns true if the complex number is an integer. Actually, true is returned even
   * if the number is close to an integer (within specified tolerance)
   * @param c Complex
   * @return boolean
   */
  public static boolean isInteger(Complex c) {
    if(!isSmall(c.imag()))
      return false;
    return MathComplex.isInteger(c.real());
  }
  
  /**
   * Returns true if the provided real number is close to an integer.
   * @param d double
   * @return boolean
   */
  public static boolean isInteger(double d) {
    return isSmall(d-Math.round(d));
  }
  
  public static double getTolerance() {
    return tol;
  }
  
  /**
   * Checks if the provided real number is "small", that is less than the tolerance (=tol).
   * @param x double
   * @return boolean
   */
  public static boolean isSmall(double x) {
    if (Math.abs(x) < tol)
      return true;
    else
      return false;
  }
  
  /**
   * Checks if the provided complex number is "small", that is less than the tolerance (=tol).
   * @param x Complex
   * @return boolean
   */
  public static boolean isSmall(Complex x) {
    if (x.abs() < tol)
      return true;
    else
      return false;
  }

  // distance between a point, p0, and a line, given by p1 and p2
  /**
   * An utility function that finds the distance between the point (the first argument)
   * and the line (passing through the second and the third arguments)
   * @param p0 Complex
   * @param p1 Complex
   * @param p2 Complex
   * @return double
   */
  public static double distancePL(Complex p0, Complex p1, Complex p2) {
    double ax = p2.real() - p1.real();
    double ay = p2.imag() - p1.imag();
    double t = ( (p0.real() - p1.real()) * ax + (p0.imag() - p1.imag()) * ay) /
        (ax * ax + ay * ay);
    double xopt = p1.real() + ax * t;
    double yopt = p1.real() + ay * t;
    // is (xopt,yopt) between p1 and p2
    // atemp1 from p1 to opt
    double atemp1x = xopt - p1.real();
    double atemp1y = yopt - p1.real();
    // atemp2 from p2 to opt
    double atemp2x = xopt - p2.real();
    double atemp2y = yopt - p2.imag();

    // atemp1 and atemp1 should be opposite sign
    if ( (atemp1x * atemp2x <= 0) && (atemp1y * atemp2y <= 0))
      return Math.sqrt( (p0.real() - xopt) * (p0.real() - xopt) +
                       (p0.imag() - yopt) * (p0.imag() - yopt));
    else
      return Math.min(Math.sqrt( (p0.real() - p1.real()) * (p0.real() - p1.real()) +
                                (p0.imag() - p1.imag()) * (p0.imag() - p1.imag())),
                      Math.sqrt( (p0.real() - p2.real()) * (p0.real() - p2.real()) +
                                (p0.imag() - p2.imag()) * (p0.imag() - p2.imag())));
  }
  /**
   * Returns the error message if any. The function is no longer in use.
   * @return String
   */
  public static String getError() {
    return Error;
  }
  
  /**
   * Sets the error message. The function is no longer in use.
   * @param s String
   */
  public static void setError(String s) {
    Error = s;
  }
  
  /**
   * Returns new Complex that is square root of the argument.
   * @param c Complex
   * @return Complex
   */
  public static Complex sqrt(Complex c) {
    return c.sqrt();
  }
  
  /**
   * Determines if the given complex number (c) is in the rectangle
   * determined by the leftTop corner, the width, and the height
   * @param c Complex
   * @param leftTop Complex
   * @param width double
   * @param height double
   * @return boolean
   */
  public static boolean isInRectangle(Complex c, Complex leftTop, double width,
                                      double height) {
    if ( (c.real() >= leftTop.real() + width) ||
        (c.real() <= leftTop.real()) ||
        (c.imag() <= leftTop.imag() - height) ||
        (c.imag() >= leftTop.imag()))
      return false;
    else
      return true;
  }
  
  /** Returns new Complex with random x,y in [0,1] */
  public static Complex smallRandomComplex() {
    return new Complex(Math.random(),Math.random());
  }
  
  /**
   * returns argument in radians in range -pi to pi.
   * @param c Complex
   * @return double in -pi to pi
   */
  public static double Arg (Complex c) {
	  return Math.atan2(c.getImaginary(),c.getReal());
  }
  
  /** given two angles (in radians), find the positive angle
   * from start to end. Normally, angles are between -pi and pi
   * (as from an atan2 call)
   * @param start
   * @param end
   */
  public static double radAngDiff(double start,double end) {
	  double diff=end-start;
	  if (diff<0) diff += 2*Math.PI;
	  return diff;
  }
  
  public static double abs(double x, double y) {
    return Math.sqrt(x*x+y*y);
  }
}
