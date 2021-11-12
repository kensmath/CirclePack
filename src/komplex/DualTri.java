package komplex;

import math.Mobius;

import complex.Complex;

import exceptions.DataException;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;

/**
 * A 'dual triangle' in a tangency packing is that formed by
 * the three points of intersection of the three circles forming
 * a face. This class computes and stores data on dual triangles.
 * The dual triangle is computed based solely on the vertices of
 * the containing face --- namely, the resulting three points are the
 * points where the incircle is tangent to the edges. 
 * 
 * Note: TangPts[j] is on edge {v(j), v((j+1)%3)}.
 * @author kens, 4/2010
  */
public class DualTri {
	
	public static double OKERR=.0000000001; 
	public Complex []corners; // vertices
	public Complex []TangPts;  // incircle tangency points
	int hes;

	public DualTri(int hs,Complex z0,Complex z1,Complex z2) {
		hes=hs;
		corners=new Complex[3];
		corners[0]=new Complex(z0);
		corners[1]=new Complex(z1);
		corners[2]=new Complex(z2);
		TangPts=new Complex[3]; // filled in other calls
		setInCirclePts();
	}
	
	/**
	 * Compute/store the tangency points based on 'corners' 
	 * only; intended for cases when radii are not available.
	 * The hyperbolic case is somewhat complicated if one or 
	 * more 'corners' is on the unit circle. Set 'TangPts' to 
	 * null if situation is illegal.
	 * In the sph case, can get the wrong distance between
	 * 'corners'.
	 */
	public void setInCirclePts() {
		double []len=new double[3];
		double []r=new double[3];
		double mindist;
		
		if (hes<0) { // hyp 
			int tick=0;

			// check legality, number on unit circle
			for (int j=0;j<3;j++) { 
				if (corners[j].abs()>1.0001) { // clearly illegal, error 
					TangPts=null;
					return;
				}
				if (corners[j].abs()>1.0) { // roundoff? proj to unit circle
					corners[j]=corners[j].divide(corners[j].abs());
					tick++;
				}
				if (corners[j].abs()>.99999)
					tick++;
			}
			if (tick>0) { // some point(s) on the unit circle
				
				try {
				// first case: all three on unit circle
				if (tick==3) {
					for (int j=0;j<3;j++)
						corners[j]=corners[j].divide(corners[j].abs());

					// If verts were at 1, +-i, then tangency points
					// are t1=origin, t0=(c)+(c)i, t2=(c)-(c)i, where
					// c=1-1/sqrt(2).
					// Strategy: find Mobius m mapping verts to 1, i, -i,
					//  tangency points are inverses of tj's.
					// 
					Complex z1=corners[1].divide(corners[0]);
					Complex z2=corners[2].divide(corners[0]);
					double v1=z1.y/(1-z1.x);
					double v2=z2.y/(1-z2.x);
					// phi(z)=(1+z)/(1-z) maps 1 to inf, zj to (vj)i
					double a=2/(v1-v2);
					double b=(v2+v1)/(v2-v1);
					// psi(z)=(a)(i)z + b(i): phi(z1)=i, phi(z2)=-i
					Mobius rot=new Mobius(corners[0].reciprocal(),
							new Complex(0.0),new Complex(0.0),
							new Complex(1.0));
					Mobius phi=new Mobius(new Complex(1.0),new Complex(1.0),
							new Complex(-1.0),new Complex(1.0));
					Mobius psi=new Mobius(new Complex(0,a),new Complex(0,b),
							new Complex(0.0),new Complex(1.0));
					// Here's m
					Mobius m=(Mobius)rot.lmult(phi.lmult(psi).lmult(phi.inverse()));
					Mobius m_inv=(Mobius)m.inverse();
					double c=1-1/Math.sqrt(2);
					TangPts[0]=m_inv.apply(new Complex(c,c));
					TangPts[1]=m_inv.apply(new Complex(0.0));
					TangPts[2]=m_inv.apply(new Complex(c,-c));
					return;
				}
				
				// handle second case: two points on unit circle
				int offset=-1;
				if (tick==2) { 
					for (int j=0;j<3;j++) 
						if (offset<0 && corners[j].abs()<=.99999)
							offset=j;
					if (offset<0)
						throw new DataException("error with points on unit circle");
					Complex z0=corners[offset];
					Complex z1=corners[(offset+1)%3];
					Complex z2=corners[(offset+2)%3];
					
					// first map z0 to origin z1 to 1
					Complex lad=new Complex(1.0);
					lad=lad.minus(z0.conj().times(z1));
					lad=lad.divide(new Complex(z1.x-z0.x,z1.y-z0.y));
					Mobius phi=new Mobius(lad,lad.times(z0).times(-1.0),
							z0.conj().times(-1.0),new Complex(1.0));
					Complex nz2=phi.apply(z2);
					double ag=nz2.arg()/2.0;
					Mobius rot=new Mobius(new Complex(Math.cos(ag),Math.sin(-ag)),
							new Complex(0.0),new Complex(0.0),new Complex(1.0));
					Mobius m=(Mobius)phi.lmult(rot);
					Mobius m_inv=(Mobius)m.inverse();
					// Mobius m maps z0 to origin, z1, z2 equidistant from 1
					
					double h=Math.sin(ag);
					double s=h/(1+h);
					double rad=1-2*s; // radius of circle at origin
					
					// In this standard position, tangency points are
					double rr=Math.pow(rad,1.5);
					Complex t0=new Complex(rr/(rad+s),-rad*h);
					Complex t2=t0.conj();
					Complex t1=new Complex(Math.sqrt(rad)); // this is real
					
					// Set tangency points
					TangPts[offset]=m_inv.apply(t0);
					TangPts[(offset+1)%3]=m_inv.apply(t1);
					TangPts[(offset+2)%3]=m_inv.apply(t2);
					return;
				}
				
				// last case, one on boundary
				if (tick==1) { 
					for (int j=0;j<3;j++) 
						if (offset<0 && corners[j].abs()>.99999)
							offset=j;
					if (offset<0)
						throw new DataException("error with points on unit circle");
					Complex z0=corners[offset];
					Complex z1=corners[(offset+1)%3];
					Complex z2=corners[(offset+2)%3];

					// Mobius m maps disc to upper half plane, z0 to infinity
					Mobius m=new Mobius(new Complex(0.0,1.0),new Complex(-z0.y,z0.x),
							new Complex(-1.0),z0);
					double h_len=HyperbolicMath.h_dist(z1,z2);
					Complex w1=m.apply(z1);
					Complex w2=m.apply(z2);
					double lg=Math.log(w1.y/w2.y);
					// hyperbolic radii
					double r1=(0.5)*(h_len-lg); 
					double r2=(0.5)*(h_len+lg);
					double Y1=Math.exp(r1)*w1.y;
					double Y2=Math.exp(r2)*w2.y;
					Complex t0=new Complex(w1.x,Y1);
					Complex t2=new Complex(w2.x,Y2);

					Mobius m_inv=(Mobius)m.inverse();
					// Set tangency points
					TangPts[offset]=m_inv.apply(t0);
					TangPts[(offset+2)%3]=m_inv.apply(t2);
					
					// For tangency between finite points, need x-radii
					if(r1 > 0.0001) 
						r1 = 1-Math.exp(-2.0*r1); // x=1-exp(-2h)
					else 
						r1 = 2.0*r1*(1.0 - r1*(1.0-2.0*r1/3.0));
					if(r2 > 0.0001) 
						r2 = 1-Math.exp(-2.0*r2);
					else 
						r2 = 2.0*r2*(1.0 - r2*(1.0-2.0*r2/3.0));
					TangPts[(offset+1)%3]=HyperbolicMath.hyp_tangency(z1, z2, r1, r2);
					return;
				}
				} catch(Exception ex) {
					throw new DataException("Mobius processing error:"+ex.getMessage());
				}
			}
			len[0]=mindist=HyperbolicMath.h_dist(corners[0],corners[1]);
			len[1]=mindist=HyperbolicMath.h_dist(corners[1],corners[2]);
			len[2]=mindist=HyperbolicMath.h_dist(corners[2],corners[0]);
		}
		
		else if (hes>0) { // sph
			len[0]=mindist=SphericalMath.s_dist(corners[0],corners[1]);
			len[1]=mindist=SphericalMath.s_dist(corners[1],corners[2]);
			len[2]=mindist=SphericalMath.s_dist(corners[2],corners[0]);
		}
			
		else { // eucl
			len[0]=mindist=corners[1].minus(corners[0]).abs();
			len[1]=corners[2].minus(corners[1]).abs();
			len[2]=corners[0].minus(corners[2]).abs();
		}
		
		// are some points too close?
		mindist=(mindist<len[1]) ? mindist : len[1];
		mindist=(mindist<len[2]) ? mindist : len[2];
		if (mindist<OKERR) {
			TangPts=null;
			return;
		}

		// find "effective" radii at vertices: radii of circles
		//   that would form this triangles.
		for (int j=0;j<3;j++) {
			double op=len[(j+1)%3];
			r[j]=(len[j]+len[(j+2)%3]-op)/2.0;
			if (r[j]<0.000001*op) { // extremely thin triangle
				TangPts=null;
				return;
			}
		}
		
		// TODO: work on case of one or more points on unit circle
		if (hes<0) {
			// hyperbolic: need these as x-radii
			for (int j=0;j<3;j++) {
				if(r[j] > 0.0001) 
					r[j] = 1-Math.exp(-2.0*r[j]); // x=1-exp(-2h)
				else 
					r[j] = 2.0*r[j]*(1.0 - r[j]*(1.0-2.0*r[j]/3.0));
			}
		}

		// here's the actual computation
		for (int j=0;j<3;j++) {
				TangPts[j]=CommonMath.get_tang_pt(corners[j],corners[(j+1)%3],
						r[j],r[(j+1)%3],hes);
		}			
		return;
	}		
		
	/**
	 * Get the tangency point between centers {v(j),v((j+1)%3)}.
	 * @return new Complex, null if not set
	 */
	public Complex getTP(int j) {
		if (TangPts==null) return null;
		Complex z=new Complex(TangPts[j]);
		return z;
	}
	
}
