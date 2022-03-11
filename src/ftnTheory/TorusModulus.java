package ftnTheory;

import java.util.Iterator;

import complex.Complex;
import dcel.SideData;
import math.Mobius;
import packing.PackData;

public class TorusModulus {

	/** 
	 * Computes the 'modulus' of a torus based on its already 
	 * computed side pairings. Return double[3]: [0]=x, [1]=y, 
	 * for 'tau' and [2] = 'flag'. flag = -1 if not a torus by 
	 * topology or by number of side-pairings. 
	 * flag = -2 if side-pairings are not computed. flag=0 on 
	 * error. flag = 1 on apparent success. Computation involves 
	 * applying inversions in unit circle and translations by +-1 
	 * until 'tau' is in the fundamental domain of moduli space 
	 * (namely, tau.x in [-1/2,1/2] and |tau| >=1).
	 * 
	 * TODO: start including affine tori.
	*/
	public static double []torus_tau(PackData p) {
	  double []ans=new double[3];

	  // Determine if this is a torus. 
	  if (p.getBdryCompCount()>0 || p.genus!=1) { 
	      ans[2]=-1.0;
	      return ans;
	  }
	  // has side pairings
	  if (p.packDCEL.pairLink==null) {
		  ans[2]=-2.0;
		  return ans;
	  }
	  
	  // has the right
	  int N=p.packDCEL.pairLink.size()-1;
	  if (N!=4 && N!=6) {
		  ans[2]=-1.0;
		  return ans;
	  }

	  boolean affine=false;
	  
	  Complex r1,r12,r2,r22,r3,r32,w;  
	  Complex []W=new Complex[7];
	  Mobius mob;

	  // Compute array of values mob.a/mob.b for the side-pairings.
	  Iterator<SideData> pdpl=p.packDCEL.pairLink.iterator();
	  SideData epair=null;
	  epair=pdpl.next(); // first slot is empty
	  int j=1;
	  while(pdpl.hasNext()) {
		  epair=pdpl.next();
	      mob=epair.mob;
	      if (Math.abs(mob.a.abs()-1.0)>.0001)
	    	  affine=true;
	      W[j]=mob.b.divide(mob.a);
	      j++;
	  }
	  
	  // if affine, have to take log
	  if (N==4 && affine) {
		  Complex[] Z=new Complex[4];
		  Z[0]=W[1];
		  Z[1]=W[2];
		  Z[2]=W[3];
		  Z[3]=W[4];
		  mob=Mobius.mob_NormQuad(Z);
		  Mobius.mobiusDirect(p,mob);
		  
		  // if affine, take logarithm
		  if (mob.apply(Z[0]).abs()>.5) {
			  for(int k=1;k<=4;k++) {
				  SideData sd=p.packDCEL.pairLink.get(k);
				  W[j]=sd.startEdge.getCenter();
			  }
		  }
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
	    
	/** 
	 * Given a complex 'teich' Teichmuller parameter in the
	 * upper half plane, return its representative point 'tau'
	 * in moduli space (the set z, Im(z)>0 so that Re(z) in 
	 * (-1/2,1/2) and |z| \ge 1, z.y>0)
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
