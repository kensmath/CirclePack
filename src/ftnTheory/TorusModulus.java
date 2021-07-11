package ftnTheory;

import java.util.Iterator;

import complex.Complex;
import dcel.D_SideData;
import komplex.SideDescription;
import math.Mobius;
import packing.PackData;

public class TorusModulus {

	/** 
	 * Computes the 'modulus' of a torus based on its already computed
	 * side pairings. Return double[3]: [0]=x, [1]=y, for 'tau'
	 * and [3] = 'flag'. flag = -1 if not a torus by topology or by
	 * side-pairings (must be translations). flag = -2 if side-pairings
	 * are not computed. flag=0 on error. flag = 1 on apparent success.
	 * Computation involves applying inversions in unit circle and 
	 * translations by +-1  until 'tau' is in the fundamental domain 
	 * (namely, tau.x in [-1,1] and |tau| >=1).
	*/
	public static double []torus_tau(PackData p) {
	  int j;
	  double []ans=new double[3];
	  ans[0]=ans[1]=ans[2]=0.0;
	  Complex r1,r12,r2,r22,r3,r32,w;  
	  Complex []W=new Complex[7];
	  Mobius mob;
	  
	  // Determine if this is a torus. 
	  if (p.getBdryCompCount()>0 || p.genus!=1) {
	      ans[2]=-1.0;
	      return ans;
	  }

	  // Compute array of values mob.a/mob.b for the side-pairings.
	  if (p.packDCEL!=null) {
		  Iterator<D_SideData> pdpl=p.packDCEL.pairLink.iterator();
		  D_SideData epair=null;
		  epair=pdpl.next(); // first slot is empty
		  j=1;
		  while(pdpl.hasNext()) {
			  epair=pdpl.next();
		      mob=epair.mob;
		      W[j]=mob.b.divide(mob.a);
		      j++;
		   }
	  }
	  
	  // traditional packing
	  else {
		  Iterator<SideDescription> pl=p.getSidePairs().iterator();
		  SideDescription epair=null;
		  j=1;
		  while(pl.hasNext()) {
			  epair=pl.next();
			  mob=epair.mob;
			  W[j]=mob.b.divide(mob.a);
			  j++;
		  }
	  }
	   if (j!=5 && j!=7) {
	      ans[2]=-2.0;
	      return ans;
	    }

	   //----Calculate ratios and select candidate in upper half plane ---

	  r1=W[1].divide(W[2]);
	  r12=W[2].divide(W[1]);
	  r2=W[3].divide(W[2]);
	  r22=W[2].divide(W[3]);
	  r3=W[3].divide(W[4]);
	  r32=W[4].divide(W[3]);

	  // get initial tau in upper half plane
	  
	  if (r1.y>0) w=r1;
	  
	  else if (r12.y>0) w=r12;
	  
	  else if (r2.y>0) w=r2;
	  
	  else if (r22.y>0) w=r22;
	  
	  else if (r3.y>0) w=r3;
	  
	  else if (r32.y>0) w=r32;
	  
	  else { // error
	      ans[2]=0.0;
	      return ans;
	  }

	  Complex tau=Teich2Tau(w);
	  if (tau==null) {
		  ans[2]=0.0;
		  return ans;
	  }
	  ans[0]=tau.x;
	  ans[1]=tau.y;
	  ans[2]=1.0;
	  return ans;
	} 
	    
	/** Given a complex 'teigh' Teichmuller parameter in the
	 * upper half plane, return its representative point 'tau'
	 * in moduli space (the set z, Im(z)>0 so that Re(z) in 
	 * (-1/2,1/2) and |z| \ge 1, z.y>0
	 * Use modular group actions PSL2Z.
	 * @param teich; should have Im(teich)>0
	 * @return tau, null on error
	 */
	public static Complex Teich2Tau(Complex teich) {
		if (teich.y<=0.0) 
			return null;
		Complex w=new Complex(teich);
		w.x=w.x-Math.floor(w.x)-
		    Math.floor(2*(w.x-Math.floor(w.x)));
		int count=0;
		Complex m_one=new Complex(-1.0);
		while (count<1000 && (w.y<Math.sqrt(1.0-w.x*w.x))) {
			count++;
			w=m_one.divide(w);
			w.x=w.x-Math.floor(w.x)-
			Math.floor(2*(w.x-Math.floor(w.x)));
		}
		if (count==1000) { // error
			return null;
		}
		return w;
	}

}
