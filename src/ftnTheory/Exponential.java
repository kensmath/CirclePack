package ftnTheory;

import allMains.CirclePack;
import complex.Complex;
import exceptions.CombException;
import exceptions.DataException;
import geometry.CircleSimple;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import packing.PackData;

public class Exponential {

	/** 
	 * Compute radii of spiral pack with parameters a,b > 0. Unit circle 
	 * at 0, tangent circle, radius a, on x-axis, circle, rad b, in first 
	 * quad. Underlying Rule: product of radii at ends of edge equals product 
	 * of radii for vertices on two sides of that edge. This generates 
	 * "Doyle" spirals, which can be used, eg., to mimic the exponential
	 * or natural phylotaxis.
	 * @param p PackData
	 * @param a double
	 * @param b double
	 * @return 1
	 */
	public static int spiral(PackData p,double a,double b) {
//		int j,m,k0=0,k1,k2,k,alpha,l;
//		Complex z1,z2,cent=null;
//		double r0,r1,r2,scale,Maxx,minx,Maxy,miny,x,y,r,factor;

		for (int i=1;i<=p.nodeCount;i++) 
			if (!p.isBdry(i) && p.countFaces(i)!=6) { 
				CirclePack.cpb.myErrorMsg("usage: in 'spiral', packing must be hexagonal");
				throw new CombException();
			}
		int[] util=new int[p.nodeCount+1];

		CircleSimple sc;
		int alp=p.getAlpha();
		int[] flower=p.getFlower(alp); // just need first two
		
		// alpha the unit circle
		p.setCenter(alp, new Complex(0.0));
		p.setRadius(alp,1.0);
		util[alp]=1;
		
		// tangent nghbs
		p.setCenter(flower[0],1.0+a,0.0);
		p.setRadius(flower[0],a);
		util[flower[0]]=1;
		Complex z1=p.getCenter(alp);
		Complex z2=p.getCenter(flower[0]);
		sc=EuclMath.e_compcenter(z1,z2,1.0,a,b);
		p.setCenter(flower[1],new Complex(sc.center));
		p.setRadius(flower[1],sc.rad);
		util[flower[1]]=1;

		int count=3;
		int j=0;
		boolean flag=false;
		while (count<p.nodeCount) {
			flag=false;
			do {
				j++;
				if (j>p.nodeCount) j=1;
				if (util[j]==0) { /* if not plotted, see if
					       there are appropriate neighbors */
					int k=0;
					int k0=0;
					flower=p.getFlower(j);
					do {
						int k1=flower[k];
						int k2=flower[k+1];
						if (util[k1]!=0 && util[k2]!=0) {
							int m=0;
							int cF2=p.countFaces(k2);
							int[] flower2=p.getFlower(k2);
							while (m<=cF2 && flower2[m]!=j) 
								m++;
							if (m==(cF2-1)
									&& !p.isBdry(k2)
									&& util[flower2[0]]!=0) {
								k0=flower2[1];
								flag=true;
							}
							else if (m<(cF2 -1)
									&& util[flower2[m+2]]!=0) {
							
								k0=flower2[m+2];
								flag=true;
							}
							if (flag) {
								z1=p.getCenter(k1);
								double r1=p.getRadius(k1);
								z2=p.getCenter(k2);
								double r2=p.getRadius(k2);
								double r0=p.getRadius(k0);
								sc=EuclMath.e_compcenter(z1,z2,r1,r2,(r1*r2/r0));
								p.setCenter(j,new Complex(sc.center));
								p.setRadius(j,sc.rad);
								util[j]=1;
							}
							else k++;
						} // end of if 
						else k++;
					} // end of do loop 
					while (!flag && k<=(p.countFaces(j)-1) );
				} // end of if 
			} while (!flag); // end of do 
			count++;
		} // end of while 
		// should have all centers and radii now 

		if (p.hes < 0) {
			double Maxx = 1;
			double minx = -1;
			double Maxy = 1;
			double miny = -1;
			for (int i = 1; i <= p.nodeCount; i++) {
				double x = p.getCenter(i).x;
				double y = p.getCenter(i).y;
				double r = p.getRadius(i);
				Maxx = (x + r > Maxx) ? x + r : Maxx;
				minx = (x - r < minx) ? x - r : minx;
				Maxy = (y + r > Maxy) ? y + r : Maxy;
				miny = (y - r < miny) ? y - r : miny;
			}
			z1.x = (Maxx + minx) * 0.5;
			z1.y = (Maxy + miny) * 0.5;
			double scale = ((Maxx - minx > Maxy - miny) ? Maxx - minx : Maxy - miny) * 0.5;
			double factor = (0.9) / (scale * 1.4142136);
			for (int i = 1; i <= p.nodeCount; i++) {
				Complex cent = p.getCenter(i).minus(z1).times(factor);
				double r = p.getRadius(i) * factor;
				sc = HyperbolicMath.e_to_h_data(cent, r);
				p.setCenter(i,new Complex(sc.center));
				p.setRadius(i,sc.rad);
			}
		}
		CirclePack.cpb.msg("Created spiral with parameters a="+a+" and b="+b);
		return 1;
	}

	/** 
	 * A circle C=C(r,z) whose disc is in the puncture plane has aspect 
	 * ratio AR(C)= r/|z|. Any triple of eucl circles generates a Doyle 
	 * spiral which winds around some point p in the plane. This computes 
	 * the spiral point 'p' and the two doyle parameters 'a', and 'b'
	 * (see 'spiral') associated with its circles. Return 0 on error.
	 * Note, we do not check that the given circles are in fact tangent.
	 * @param p @see PackData
	 * @param r0 double, eucl radii, etc.
	 * @param r1 double
	 * @param r2 double
	 * @param z0 Complex, eucl centers, etc.
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param ans double[4], for results: must be created by the calling routine
	 * @return ans[4]: a=ans[0], b=ans[1], point=ans[2]+ians[3]. 
	*/
	public static int doyle_point(PackData p,double r0,double r1,double r2,
			Complex z0,Complex z1,Complex z2,double []ans) {
		if (ans == null) 
			throw new DataException("must create 'ans[4]'");
		double []rad=new double[3];
		Complex []cent=new Complex[3];
		rad[0]=r0;
		cent[0]=new Complex(z0);
		rad[1]=r1;
		cent[1]=new Complex(z1);
		rad[2]=r2;
		cent[2]=new Complex(z2);
		double least=r0;
		int J=0;
		for (int j=1;j<3;j++) 
			if (rad[j]<least) {least=rad[j];J=j;}
		if (least<=.00000000001) {
			CirclePack.cpb.myErrorMsg("doyle_point: the circle is too small to work with");
			return 0;
		}
		// shift so 0 index has smallest rad 
		double holdr;
		Complex holdz=null;
		if (J==1) {
			holdz=new Complex(cent[1]);
			cent[1]=new Complex(cent[2]);
			cent[2]=new Complex(cent[0]);
			cent[0]=new Complex(holdz);
			holdr=rad[1];
			rad[1]=rad[2];
			rad[2]=rad[0];
			rad[0]=holdr;
		}
		else if (J==2) {
			holdz=new Complex(cent[2]);
			cent[2]=new Complex(cent[1]);
			cent[1]=new Complex(cent[0]);
			cent[0]=new Complex(holdz);
			holdr=rad[2];
			rad[2]=rad[1];
			rad[1]=rad[0];
			rad[0]=holdr;
		}
		double a,b;
		ans[0]=a=rad[1]/rad[0];
		ans[1]=b=rad[2]/rad[0];

		// Computation via truncated infinite series is thanks to Matt Cathey.
		double theta=Math.acos((1 + a + b - a*b) / (1 + a + b + a*b))
				+ Math.acos((a + a*b + b - b*b) / (a + a*b + b + b*b))
				+ Math.acos((a*a + a + a*b - b) / (a*a + a + a*b + b));
		Complex accum=new Complex(0.0);
		for (int n=1;n<=100;n++) {
			double N=(double)n;
			double coef=Math.pow(a,(1-N))+Math.pow(a,-N);
			Complex w=new Complex(N*theta-(N-1)*Math.PI).exp();
			accum = accum.add(w.times(coef));
		}
		// scale back to original size, translate to reestablish center cent[0].
		accum=accum.times(rad[0]).add(cent[0]);
		ans[2]=accum.x;
		ans[3]=accum.y;
		return 1;
	} 

}
