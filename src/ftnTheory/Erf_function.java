package ftnTheory;

import packing.PackData;

import complex.Complex;

import exceptions.ParserException;

public class Erf_function {

	/** 
	 * Create a new packing with boundary radii based on the
	 * indefinite integral of 2/sqrt{pi}e^{-z^n} (so 
	 * 1/sqrt{pi}e^{-z^n} is the derivative).
	 * Case n=1 is the exponential, n=2 is "error function". 
	 * Domain packing p (normally univalent) is copied into 
	 * new packing and each bdry radius is set to e^{z^n}*r, 
	 * where r,z are radius, center in p. 
	 * Return pointer to packData, with 'CPScreen' set to null.
	 * Calling routine sets 'cpScreen' and installs new packing.
	 * @param p domain 'PackData'
	 * @return 'PackData', null on error.
	*/
	public static PackData erf_ftn(PackData p,int n) {
	    int v,endv;
	    Complex z,w;
	    double C=2.0/Math.sqrt(Math.PI);

	    if (p.hes!=0) 
	    	throw new ParserException("packing must be euclidean");
	    if (n<1 || n>3 || !p.status || p.getBdryCompCount()==0 || !p.isSimplyConnected()) {
	    	throw new ParserException("packing must be simply connected, with bdry");
	    }
    	PackData packData=p.copyPackTo(); // make p2 a copy of p1
	    v=endv=p.bdryStarts[1];
	    boolean keepon=true;
	    while (v!=endv || keepon) {
	    	keepon=false;
	    	z=p.getCenter(v);
	    	w=p.getCenter(v);
	    	int i=1;
	    	while (i<n) { // z^n
	    		w=z.times(z); 
	    		i++;
	    	}
	    	// modulus of derivative is |e^{w^n}| = e^{Re(w)}.
	    	packData.setRadius(v,C*Math.exp(-w.x)*p.getRadius(v));
	    	v=p.kData[v].flower[0];
	    }
	    return packData;
	}

}
