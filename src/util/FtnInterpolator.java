package util;

import complex.Complex;
import exceptions.DataException;

/**
 * Routines for interpolating a real or complex function 
 * defined on the unit circle, [0,2pi]. We are given
 * a vector of values of s (closed up) and an associated 
 * vector of values f(s). Given s in [0,2pi], we want to
 * linearly interpolate to get f(s).
 * @author kens, April 2016
 *
 */
public class FtnInterpolator {
	public double[] s_points;   // closed list of values s(mod 2pi), increasing
	public Complex[] ftnValues;   // corresponding list of ftn values
	public int N; // count of sample points (including closing point)
	public int smallindx; // index of smallest entry
	double m2pi=2.0*Math.PI;
			
	// Constructor
	public FtnInterpolator() {
	}
	
	/**
	 * must initiate with persistent data arrays
	 * @param spts
	 * @param fvalues
	 */
	public void valuesInit(double[] spts,Complex[] fvalues) {
		N=spts.length;
		if (N==0 || N!=fvalues.length)
			throw new DataException("given data vectors have different lengths");
		s_points=new double[N];
		ftnValues=new Complex[N];
		smallindx=0;
		for (int n=0;n<N;n++) {
			s_points[n]=(spts[n]+m2pi)%m2pi;
			if (s_points[n]<s_points[smallindx])
				smallindx=n;
			ftnValues[n]=fvalues[n];
		}
	}

	public Complex interpValue(double s) {
		s=(s+m2pi)%m2pi;
		int indx=entryIndex(s);
		double span=edist(s_points[indx],s_points[(indx+1)%N]);
		double diff=edist(s_points[indx],s);
		double ratio=diff/span;
		double x=ftnValues[indx].x+(ftnValues[(indx+1)%N].x-ftnValues[indx].x)*ratio;
		double y=ftnValues[indx].y+(ftnValues[(indx+1)%N].y-ftnValues[indx].y)*ratio;
		return new Complex(x,y);
	}
		
	/**
	 * Return index i so s is between ith and (i+1)st entry
	 * of parameter vector.
	 * @param s double
	 * @return int
	 */
	public int entryIndex(double s) {
		s=(s+m2pi)%(m2pi); // modulo 2pi
		if (s<s_points[smallindx])
			return (smallindx-1+N)%N;
		int hit=-1;
		for (int i=0;(i<N && hit<0);i++) {
			if (s>=s_points[i]) { 
				if (s<s_points[(i+1)%N] || s_points[(i+1)%N]<s_points[i])
					hit=i;
			}
		}
		if (hit<0)
			throw new DataException("failed to find parameter s");
		return hit;
	}
	
	/**
	 * distance from s1 to s2 modulo 2pi
	 * @param s1
	 * @param s2
	 * @return double
	 */
	public double edist(Double s1,Double s2) {
		return (double)(((double)s2-(double)s1+m2pi)%(m2pi));
	}

}
