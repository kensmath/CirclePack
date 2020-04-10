package util;

import exceptions.DataException;
import geometry.HyperbolicMath;
import geometry.SphericalMath;

import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.Vector;

import listManip.PathLink;

import complex.Complex;

/**
 * Here a "path" means a polygonal path in one of the geometries,
 * parameterized by arc length. It may be closed or open. Stored
 * using []complex for the path's vertex locations and []double for 
 * the corresponding arclength parameter values. The path is
 * closed by declaration or observation.
 * @author kens
 *
 */
public class PathInterpolator {
	public int hes; // geometry
	public Vector<Double> domain;   // arclength parameter domain, [0,x]
	public Vector<Complex> pathZ;   // path point locations
	public double length;           // end x of parameter domain
	public boolean closed;
	public int N; // count of vertices on the path

	// Constructor
	public PathInterpolator(int heS) {  // just set geometry
		hes=heS;
		domain=null;
		length=0;
		pathZ=null;
		closed=false;
		N=0;
	}
	
	public void pathInit(Vector<Complex> pathZs) {
		if (pathZs==null || pathZs.size()==0) return;
		N=pathZs.size();
		pathZ=new Vector<Complex>(N);
		for (int i=0;i<N;i++)
			pathZ.add(new Complex(pathZs.get(i)));
		domain=setParameters();
		if (dist((Complex)pathZ.firstElement(),pathZ.lastElement())<
				0.0001*domain.lastElement()) { // ends so close: closed
			closed=true;
		}
		length=domain.lastElement();
	}
	
	public void pathInit(Complex []nodes) {
		if (nodes==null || nodes.length==0) return;
		Vector<Complex> pz=new Vector<Complex>(nodes.length);
		for (int i=0;i<nodes.length;i++)
			pz.add(new Complex(nodes[i]));
		pathInit(pz);
	}
	
	public void pathInit(PathLink plink) {
		if (plink==null || plink.size()==0) return;
		Vector<Complex> pz=new Vector<Complex>(2);
		Iterator<Complex> plst=plink.iterator();
		while (plst.hasNext()) {
			pz.add(new Complex(plst.next()));
		}
		pathInit(pz);
	}
	
	public void pathInit(Path2D.Double gpath) {
		// Note: we just want the first entry in vector of Vector<Complex>
		Vector<Complex> pz;
		try {
			pz=(Vector<Complex>)GenPathUtil.gpPolygon(gpath).get(0);
		} catch (Exception ex) {
			return;
		}
		pathInit(pz);
	}

	/**
	 * Geometry dependent distance between complex numbers
	 * @param z
	 * @param w
	 */
	public double dist(Complex z,Complex w) {
		if (hes==0)
			return z.sub(w).abs();
		if (hes>0)
			return SphericalMath.s_dist(z,w);
		else // NOTE: < 0 (negative of eucl distance) if |z|>=1 (or w) 
			return HyperbolicMath.h_dist(z,w);
	}
	
	public void closeUp() { // close the path if not already closed
		if (closed) return;
		closed=true;
		domain.add(domain.lastElement()+dist(pathZ.lastElement(),pathZ.firstElement())); 
		pathZ.add(new Complex(pathZ.firstElement())); // close up
		N++;
	}
	
	/**
	 * Determine the arclength parameters for the nodes of the path.
	 * @return Vector<Double> of length N
	 */
	public Vector<Double> setParameters() {
		if (N<=0) return null;
		Vector<Double> ans=new Vector<Double>(N);
		double spot=0.0;
		ans.add(spot);
		double inc=0.0;
		for (int i=1;i<N;i++) {
			inc=dist(pathZ.get(i),pathZ.get(i-1));
			if (inc<0) throw new DataException("illegal path segment length");
			spot += inc;
			ans.add(spot);
		}
		return ans;
	}
	
	/**
	 * Find parameter t for s+inc; i.e., if closed, t=(s+inc)mod(length).
	 * If not closed and s+inc > length, then return -1.0.
	 * @param s, double
	 * @param inc, double
	 * @return, double or -1 on failure
	 */
	public double newParam(double s,double inc) {
		double t=s+inc;
		if (t<=length) return t;
		if (!closed) return -1; // failure
		while (t>length) t-=length;
		return t;
	}
	
	/**
	 * Given arclength parameter s, find the point Z on the curve.
	 * If closed and s > length or s < 0, then continue around path,
	 * else exception.
	 * TODO: not ready for hyperbolic case
	 * @param s arclength parameter
	 * @return Complex or null on error
	 */
	public Complex sToZ(double s) {
		if (hes<0) return null;
		
		// get length and adjust s if necessary
		double pathLength=domain.lastElement();
		if (s<=0.0 || pathLength==0) return pathZ.firstElement();
		if (pathLength<0.0) throw new DataException("path length is <= 0.0");
		if (!closed && (s<0.0 || s>pathLength))
				throw new DataException("Improper arclenth parameter");
		while (s<0.0) s +=pathLength;
		while (s>pathLength) s-=pathLength;

		// find indices [i-1,i] for location of s in 'domain' and 
		//   interpolating factor 'fac'
		int i=1;
		while (i<N && s>domain.get(i)) i++;
		if (i==N) throw new DataException("arclength parameter greater than length");
		double fac=(s-domain.get(i-1))/(domain.get(i)-domain.get(i-1));
		Complex startZ=pathZ.get(i-1);
		Complex endZ=pathZ.get(i);
		
		if (hes==0) { // euclidean
			return endZ.sub(startZ).times(fac).add(startZ);
		}
		double ang=fac*SphericalMath.s_dist(startZ,endZ);
		return SphericalMath.s_shoot(startZ,endZ,ang);
	}
	
	/**
	 * See below: between 'minDist' and 'minDist' + 10%.
	 * @param s
	 * @param minDist
	 * @return
	 */
	public double jumpParam(double s,double minDist) {
		return jumpParam(s,minDist,1.1*minDist);
	}
	
	/**
	 * Given a parameter s, find the smallest parameter t > s (mod(length) 
	 * if path is closed) so that the distance of the point for t from 
	 * that for s is in ['minDist','maxDist']. Return -1 on failure.
	 * @param s, double, base parameter location
	 * @param targetDist, double
	 * @return
	 */
	public double jumpParam(double s,double minDist,double maxDist) {
		Complex z=null;
		if (s<0 || s>length ||  // bad parameter 
				minDist>length ||  // path too short to reach
				(!closed && (s+minDist)>length) || // remaining path too short
				(hes>0 && minDist>Math.PI) || // too far for sphere
				(z=sToZ(s))==null) // failure to find point on path
			return -1;

		// jump ahead in 'maxDist' size steps until we:
		//   * find t, 
		//   * exceed length for non-closed path
		//   * go fully around and pass s for clsoed path
		
		
		double inc=maxDist;
		double old_inc=s;
		double t;
		while (inc<length && (t=newParam(s,inc))>=0) {
			Complex w=sToZ(t);
			double dist=dist(z,w);
			if (dist>=minDist && dist<=maxDist) return t;
			while (dist>=minDist) { // must have gone too far
				inc=(old_inc+inc)/2.0;
				t=newParam(s,inc); // should be >=0
				w=sToZ(t);
				dist=dist(z,w);
				if (dist<=maxDist) return t;
			}
			// take another step
			old_inc=inc;
			inc+=maxDist;
		} // end of while
		return -1.0; // failure
	}

}
