package complex;

import exceptions.VarException;
import graphObjects.CPCircle;

import java.awt.geom.Point2D;

import math.Point3D;
import util.MathUtil;
import util.StringUtil;
import allMains.CPBase;

/**
 * <p>
 * Title: Class for Complex Numbers
 * </p>
 * <p>
 * Description: Complex Arithmetic
 * </p>
 * Implements the complex numbers.
 * <p>
 * Copyright: Fedor Andreev, Copyright (c) 2003
 * </p>
 * <p>
 * Company: WIU
 * </p>
 * 
 * @author Fedor Andreev.
 * @author Ken Stephenson (some additions/modifications)
 * @version 1.1
 */

public class Complex {

	public double x; // Real part
	public double y; // Imaginary part

	/** Constructs Complex, sets it to 0 */
	public Complex() {
		x = 0.;
		y = 0.;
	}

	/** Constructs Complex from the given double */
	public Complex(double t) {
		x = t;
		y = 0.;
	}

	/** Constructs Complex from the given int */
	public Complex(int t) {
		x = (double) t;
		y = 0.;
	}

	/** Constructs Complex from the given real and imaginary parts */
	public Complex(double t1, double t2) {
		x = t1;
		y = t2;
	}
	
	/** Constructs Complex from two Double types */
	public Complex(Double a,Double b) {
		x=y=0.0;
		if (a!=null) x=a.doubleValue();
		if (b!=null) y=b.doubleValue();
	}

	/** Constructs Complex from the given real and imaginary parts (integers) */
	public Complex(int t1, int t2) {
		x = (double) t1;
		y = (double) t2;
	}

	/** Constructs Complex from a float */
	public Complex(float t) {
		x = (double) t;
		y = 0.;
	}

	/** Constructs Complex from a long */
	public Complex(long t) {
		x = (double) t;
		y = 0.;
	}

	/** Constructs Complex from 2D point pt; if pt=null, return 0 */
	public Complex(Point2D pt) {
		if (pt==null) 
			x=y=0.0;
		else {
			x = pt.getX();
			y = pt.getY();
		}
	}

	/** 
	 * Project Point3D to (theta,phi). If null, return (0,0)
	 * Caution, some ambiguity if point is already (theta, phi) 
	 */
	public Complex(Point3D pt3) {
		if (pt3==null) 
			x=y=0.0;
		// this was cheap method to see if already in (theta, phi) form,
		//    and gave problems
//		else if (Math.abs(pt3.z)<.00000001) { // use x,y
//			x = pt3.x;
//			y = pt3.y;
//		}
		else { // project to unit sphere, return (theta,phi) form
			x=pt3.getTheta();
			y=pt3.getPhi();
		}
	}
	
	/**
	 * Copying constructor; if p=null, return 0
	 * @param p Complex
	 */
	public Complex(Complex p) {
		if (p == null)
			x=y=0.0;
		else {
			x = p.x;
			y = p.y;
		}
	}
	
	/** Returns the real part of this complex number */
	public double real() {
		return x;
	}

	public double getReal() {
		return x;
	}

	/** Returns the imaginary part of this complex number */
	public double imag() {
		return y;
	}

	public double getImaginary() {
		return y;
	}

	/** Sets the real part of this complex number */
	public void setX(double x) {
		this.x = x;
	}

	public void setReal(double x) {
		this.x = x;
	}

	/** Sets the imaginary part of this complex number */
	public void setY(double y) {
		this.y = y;
	}

	public void setImaginary(double y) {
		this.y = y;
	}

	public void set(CPCircle c) {
		x = c.x;
		y = c.y;
	}
	
	/** return true if NaN */
	public static boolean isNaN(Complex z) {
		if (java.lang.Double.isNaN(z.x) || java.lang.Double.isNaN(z.y))
			return true;
		return false;
	}

	/** Returns the absolute value of this complex number */
	public double abs() {
		return Math.sqrt(x * x + y * y);
	}

	/** Convenience routine: abs value of x+iy */
	public static double abs(double nx, double ny) {
		return Math.sqrt(nx * nx + ny * ny);
	}

	/** Returns the square of the absolute value of this complex number. */
	public double absSq() {
		return x * x + y * y;
	}

	/** Returns the "argument" of the complex number in -pi to pi.*/
	public double arg() {
		  return Math.atan2(y,x);
	}
	
	/** Returns new Complex sum of complex argument and this complex number. */
	public Complex plus(Complex t) {
		return new Complex(x + t.x, y + t.y);
	}

	/** Returns new Complex sum of complex argument and this complex number. */ 
	public Complex add(Complex t) {
		return this.plus(t);
	}

	/** Returns new Complex sum of real number argument and this complex number */
	public Complex plus(double t) {
		return new Complex(x + t, y);
	}

	/** Returns new Complex sum of real number argument and this complex number */
	public Complex add(double t) {
		return this.plus(t);
	}

	/** Returns new Complex subtracting the complex argument from this complex number */
	public Complex minus(Complex t) {
		return new Complex(x - t.x, y - t.y);
	}

	/** Returns new Complex subtracts the real number from this complex number */
	public Complex minus(double d) {
		return new Complex(x - d, y);
	}

	/** Returns new Complex subtracts the complex number from this complex number */
	public Complex sub(Complex t) {
		return this.minus(t);
	}

	/** Returns new Complex subtracts the real number from this complex number */
	public Complex sub(double d) {
		return this.minus(d);
	}

	/** Returns new Complex multiplying this complex number by complex argument */
	public Complex times(Complex t) {
		return new Complex(x * t.x - y * t.y, x * t.y + y * t.x);
	}

	/** Returns new Complex multiplying this complex number by complex argument */
	public Complex mult(Complex t) {
		return this.times(t);
	}

	/** Returns new Complex multiplying this complex number by real argument */
	public Complex times(double t) {
		Complex t1 = new Complex(x * t, y * t);
		return t1;
	}

	/** Returns new Complex multiplying this complex number by real argument */
	public Complex mult(double t) {
		return this.times(t);
	}

	/** Returns new Complex dividing this complex number by real argument */
	public Complex divide(double t) {
		Complex t1 = new Complex(x / t, y / t);
		return t1;
	}

	/** Returns new Complex dividing this complex number by complex argument */
	public Complex divide(Complex t) {
		if (MathComplex.isSmall(t.x) && MathComplex.isSmall(t.y))
			MathComplex.setError("The denominator is too small!");
		double f = 1 / (t.x * t.x + t.y * t.y);
		return new Complex((x * t.x + y * t.y) * f, (y * t.x - x * t.y) * f);
	}

	/** Returns new Complex, conjugate of this complex number */
	public Complex conj() {
		return new Complex(x, -y);
	}
	
	/** 
	 * complex exponential of this complex number 
	 * @return new Complex
	 */
	public Complex exp() {
		Complex tmp=new Complex(Math.cos(y),Math.sin(y));
		return tmp.times(Math.exp(x));
	}

	/** 
	 * Returns new Complex algebraic inverse of this complex number 
	 */
	public Complex reciprocal() {
		if (MathComplex.isSmall(x) && MathComplex.isSmall(y))
			MathComplex.setError("The denominator is too small!");
		double f = 1 / (x * x + y * y);
		return new Complex(x * f, -y * f);
	}

	/** 
	 * Returns new Complex additive inverse of this complex number 
	 */
	public Complex uminus() {
		return new Complex(-x, -y);
	}

	/** 
	 * Get String of this complex number in form x+yi 
	 * @return String
	 */
	public String toString() {
		
		// essentially real?
		if (MathComplex.isSmall(y))
			return MathUtil.d2String(x);
		
		// essentially pure imaginary?
		if (MathComplex.isSmall(x)) {
			if (y == 1d)
				return "i";
			if (y == -1d)
				return "-i";
			else
				return MathUtil.d2String(y) + "i";
		}
		

		if (y == 1d) // y==1?
			return MathUtil.d2String(x) + "+i";
		if (y == -1d) // y== -1?
			return MathUtil.d2String(x) + "-i";

		// which sign?
		if (y > 0)
			return MathUtil.d2String(x) + "+" + MathUtil.d2String(y) + "i";
		else
			return MathUtil.d2String(x) + "-" + MathUtil.d2String(Math.abs(y))
					+ "i";
	}

	/** 
	 * Returns a String representation of this 
	 * complex number in form (x,y) 
	 */
	public String toString2() {
		return "(" + MathUtil.d2String(x) + "," + MathUtil.d2String(y) + ")";
	}

	/**
	 * Returns a String representation in form x y
	 * @return String
	 */
	public String toString3() {
		return x + " " + y;
	}

	/** Returns new Complex square root of this complex number */
	public Complex sqrt() {
		double r = this.abs();
		double phi = this.arg();
		return new Complex(Math.sqrt(r) * Math.cos(phi / 2.0), Math.sqrt(r)
				* Math.sin(phi / 2.0));
	}

	// paints the complex number
	// public void paint(Graphics g) {
	// g.setColor(theColor);
	// int xc = (int) (World.M2Gx(this));
	// int yc = (int) (World.M2Gy(this));
	// g.drawLine(xc - 3, yc - 3, xc + 3, yc + 3);
	// g.drawLine(xc - 3, yc + 3, xc + 3, yc - 3);
	// }

	// public Complex rotate(double theta) {
	// double xt = x * Math.cos(theta) - y * Math.sin(theta);
	// double yt = x * Math.sin(theta) + y * Math.cos(theta);
	// return new Complex(xt, yt);
	// }
	public Complex rotate(double theta) {
		double xt = x * Math.cos(theta) - y * Math.sin(theta);
		double yt = x * Math.sin(theta) + y * Math.cos(theta);
		return new Complex(xt, yt);
	}

	public double dotProduct(Complex c) {
		return x * c.x + y * c.y;
	}

	/** Convenience routine: treating this complex number as
	 * (theta,phi) for the sphere, return the associated 3D point.
	 */
	public Point3D getAsPoint() {
		return new Point3D(Math.sin(y) * Math.cos(x),
				Math.sin(y) * Math.sin(x), Math.cos(y));
	}
	
	/**
	 * Attempt to interpret given string as a representation of
	 * a complex number. Annoying complexity. Problems: 
	 *   * may be just a real number (or integer)
	 *   * may have 'i' or 'I' in various possible locations
	 *   * will have signs for both real and imaginary parts,
	 *     but that for imaginary part could be in various 
	 *     locations --- may have no space before or after,
	 *     there may or may not be a '+' sign
	 *   * whole or one or both parts may be 
	 *     variables ('_' designation)
	 *   * lead '-' may indicate a flag instead of a sign
	 *   * '-' may occur for exponent in scientific notation
	 *   * may need to eat '+' or '*' signs
	 *   
	 * NOT YET allowing any special formats, such as (<x>,<y>), 
	 * etc. Recall that variable notation is '_<name> ' with
	 *    no spaces, but ending with blank or end of string.   
	 * TODO: I'm building this as I go; will add more processing
	 *    as I find it is needed. 
	 *     
	 * @param str String, assume trimmed
	 * @return Complex
	 * @throws VarException
	 */
	public static Complex string2Complex(String str) 
			throws VarException {
		if (str.charAt(0)=='-' && StringUtil.isFlag(str))
			throw new VarException(
				"this seems to be a flag, not a complex number");
		if (str.charAt(0)=='+') // this should preclude '_' at start
			str=str.substring(1).trim();
		boolean minusflag=false;
		int spot_i=str.indexOf('i');
		int spot_I=str.indexOf('I');
		
		// break into pieces at spaces
		String []pieces=str.split(" ");
		int numPieces=pieces.length;
		if (numPieces==0)
			throw new VarException("empty string");
		if ((spot_i<0 && spot_I<0 && numPieces>2) || (spot_i>=0 && spot_I>=0) )
			throw new VarException("improper complex number format");
		
		// one piece? check for split at '+' '-' between parts
		//    don't be fooled by "E-01" type scientific notation.
		if (numPieces==1) {
			int splitspot=-1;
			int tick=1;
			while (splitspot<0 && tick<str.length()-2) {
				if (str.charAt(tick)=='+') {
					char c=str.charAt(tick-1);
					if (c!='e' && c!='E') {
						splitspot=tick;
						break;
					}
				}
				if (str.charAt(tick)=='-') {
					char c=str.charAt(tick-1);
					if (c!='e' && c!='E') {
						splitspot=tick;
						break;
					}
				}
				tick++;
			}
			
			if (splitspot>0) {
				pieces=new String[2];
				pieces[0]=str.substring(0,splitspot);
				pieces[1]=str.substring(splitspot);
				if (pieces[1].charAt(0)=='+') // shuck '+' sign
					pieces[1]=pieces[1].substring(1);
				numPieces=2;
			}
			
			// single string: might be real or pure imaginary
			else {
				if (spot_i>=0 || spot_I>=0) {
					Double imag=getImagPart(pieces[0]); // this also looks for variable
					if (imag==null)
						throw new VarException("pure imaginary format error");
					return new Complex(0.0,(double)imag);
				}
				if (str.charAt(0)=='_') {
					Double real=null;
					try {
						real=Double.parseDouble(CPBase.varControl.getValue(str));
						return new Complex((double)real);
					} catch (Exception ex) {
						throw new VarException("not read: "+ex.getMessage());
					}
				}
				try {
					return new Complex(Double.parseDouble(str));
				} catch (Exception ex) {
					throw new VarException("not a number :"+ex.getMessage());
				}
			}
		}
		
		// number > 2 implies must involve " +/- " or " I/i "; must reduce to 2 
		while (numPieces>2) {
//			String endone=pieces[numPieces-1];
			
			// unneeded '*'
			if (pieces[1].equals("*")) {
				for (int j=1;j<numPieces-1;j++)
					pieces[j]=pieces[j+1];
				numPieces--;
			}
			
			// unneeded ' + '
			if (pieces[1].equals("+")) {
				for (int j=1;j<numPieces-1;j++)
					pieces[j]=pieces[j+1];
				numPieces--;
			}
			
			// ' - ', hold for later
			else if (pieces[1].equals("-")) {
				minusflag=true;
				for (int j=1;j<numPieces-1;j++)
					pieces[j]=pieces[j+1];
				numPieces--;
			}

			else if (pieces[1].startsWith("i") || pieces[1].startsWith("I")) {
				// put 'i' at end
				if (pieces[1].charAt(1)=='*') {
					if (pieces[1].length()>2) 
						pieces[1]=new String(pieces[1].substring(2)+"i");
				}
				else if (pieces[1].length()>1) {
					pieces[1]=new String("-"+pieces[1].substring(1)+"i");
				}
				else { // or remove this piece and put 'i' at end of next one
					for (int j=1;j<numPieces-1;j++)
						pieces[j]=pieces[j+1];
					numPieces--;
					pieces[1]=new String(pieces[1]+"i");
				}
			}
					
			// if last string is just 'i' or '*i', add 'i' to previous (should be number)
			else if (pieces[numPieces-1].equalsIgnoreCase("*i") || 
					pieces[numPieces-1].equalsIgnoreCase("i")) {
				pieces[numPieces-2]=new String(pieces[numPieces-1]+"i");
				pieces[numPieces-1]=null;
				numPieces--;
			}
			
			else
				throw new VarException("improper format");
		}

		// Now down to two pieces
		Double realpart=null;
		Double impart=null;

		spot_i=pieces[0].indexOf("i");
		spot_I=pieces[0].indexOf("I");
		
		// imaginary part could be first
		if (spot_i>=0 || spot_I>=0) {
			impart=getImagPart(pieces[0]);
			if (impart==null) {
				throw new VarException("error in getting first segment as imaginary part");
			}
		}
		// else try to get as real part
		else { 
			try {
				if (pieces[0].charAt(0)=='_')
					realpart=Double.parseDouble(CPBase.varControl.getValue(pieces[0]));
				else realpart=Double.parseDouble(pieces[0]);
			} catch (Exception ex) {
				throw new VarException("error in real part: "+ex.getMessage());
			}
		}
		
		spot_i=pieces[1].indexOf("i");
		spot_I=pieces[1].indexOf("I");
		if ((spot_i>=0 || spot_I>=0)) {
			if (impart!=null)
				throw new VarException("both parts think they are the imaginary part");
			impart=getImagPart(pieces[1]);
			if (impart==null) {
				throw new VarException("missing real or imaginary part");
			}
		}
		
		if (realpart==null || impart==null) {
			try {
				if (realpart!=null)
					impart=Double.parseDouble(pieces[1]);
				else if (impart!=null) {
					if (pieces[1].charAt(0)=='_')
						realpart=Double.parseDouble(CPBase.varControl.getValue(pieces[1]));
					else realpart=Double.parseDouble(pieces[1]);
				}
			} catch (Exception ex) {
				throw new VarException("failed to get real or imaginary part");
			}
		}
		
		if (realpart==null || impart==null)
			throw new VarException("missing real or imaginary part");

		double real=(double)realpart;
		double img=(double)impart;
		if (minusflag) img *=-1.0;
		return new Complex(real,img);
	}

	/**
	 * Parse string with 'i' or 'I' (purporting to be imaginary). May be number
	 * (or variable) preceded by 'i*' or 'I*' or succeeded by '*i' or '*I'.
	 * @param String, no spaces, has 'i' or 'I'
	 * @return Double, or null on error
	 */
	public static Double getImagPart(String imag) {
		
		// first, check for leading negative sign
		boolean negSign=false;
		if (imag.startsWith("-")) {
			negSign=true;
			imag=new String(imag.substring(1));
		}
		
		int spot_i=imag.indexOf('i');
		int spot_I=imag.indexOf('I');
			// just 'i' or 'I' alone?
		if (imag.length()==1) {
			if (negSign) 
				return Double.valueOf(-1.0);
			return Double.valueOf(1.0);
		}
		
		// check for '*'
		int st=imag.indexOf("*");
		if (st<0) { // no '*'
			// starts with 'i' or 'I'
			if (spot_i==0 || spot_I==0) {
				try {
					if (imag.charAt(1)=='_') // variable?
						return Double.parseDouble(CPBase.varControl.getValue(imag.substring(1)));
					return Double.parseDouble(imag.substring(1));
				} catch (Exception ex){
					return null;
				}
			}
			// ends with 'i' or 'I'
			if (spot_i==imag.length()-1 || spot_I==imag.length()-1) {
				try {
					return Double.parseDouble(imag.substring(0,imag.length()-1));
				} catch (Exception ex){
					return null;
				}
			}
		}
		
		// there is a '*'; should be 'i*', 'I*', '*i', or '*I'
		if (imag.startsWith("i*") || imag.startsWith("I*")) {
			try {
				double nd;
				if (imag.charAt(2)=='_')  // variable?
					nd=Double.parseDouble(CPBase.varControl.getValue(imag.substring(2)));
				else nd=Double.parseDouble(imag.substring(2));
				if (negSign)
					nd *= -1.0;
				return Double.valueOf(nd);
			} catch (Exception ex){
				return null;
			}
		}
		if (imag.endsWith("*i") || imag.endsWith("*I")) {
			double nd;
			try {
				nd=Double.parseDouble(imag.substring(0,imag.length()-2));
			} catch (Exception ex){
				return null;
			}
			if (negSign)
				nd *= -1.0;
			return Double.valueOf(nd);
		}

		// should have exhausted all properly formatted cases
		return null;
	}
	
	 /**
	   * Inverse hyperbolic cosine for real values
	   * @param x
	   * @return
	   */
	  public static double aCosh(double x) {
		  return Math.log(x+Math.sqrt(x*x-1.0));
	  }
	  
	
}
