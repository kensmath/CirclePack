package ftnTheory;

import allMains.CirclePack;
import complex.Complex;
import exceptions.CombException;
import exceptions.DataException;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
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
		int j,m,k0=0,k1,k2,count,k,alpha,l;
		boolean flag=false;
		Complex z1,z2,cent=null;
		double r0,r1,r2,scale,Maxx,minx,Maxy,miny,x,y,r,factor;

		for (int i=1;i<=p.nodeCount;i++) {
			if (p.kData[i].bdryFlag==0 && p.kData[i].num!=6) flag=true;
			p.kData[i].utilFlag=0; 
		}
		if (flag) {
			CirclePack.cpb.myErrorMsg("usage: in 'spiral', packing must be hexagonal");
			throw new CombException();
		}
		CircleSimple sc;
		alpha=p.alpha;
		p.rData[alpha].center.x=0.0;p.rData[alpha].center.y=0.0;
		p.rData[alpha].rad=1.0;p.kData[alpha].utilFlag=1;
		k=p.kData[alpha].flower[0];
		l=p.kData[alpha].flower[1];
		p.rData[k].center.x=1.0+a;p.rData[k].center.y=0;
		p.rData[k].rad=a;
		p.kData[k].utilFlag=1;
		z1=p.rData[alpha].center;z2=p.rData[k].center;
		sc=EuclMath.e_compcenter(z1,z2,1.0,a,b);
		p.rData[l].center=new Complex(sc.center);
		p.rData[l].rad=sc.rad;
		p.kData[l].utilFlag=1;

		count=3;
		j=0;
		while (count<p.nodeCount) {
			flag=false;
			do {
				j++;
				if (j>p.nodeCount) j=1;
				if (p.kData[j].utilFlag==0) { /* if not plotted, see if
					       there are appropriate neighbors */
					k=0;
					do {
						k1=p.kData[j].flower[k];
						k2=p.kData[j].flower[k+1];
						if (p.kData[k1].utilFlag!=0 && p.kData[k2].utilFlag!=0) {
							m=0;
							while (m<=p.kData[k2].num 
									&& p.kData[k2].flower[m]!=j) m++;
							if (m==(p.kData[k2].num -1)
									&& p.kData[k2].bdryFlag==0
									&& p.kData[p.kData[k2].flower[0]].utilFlag!=0)
							{k0=p.kData[k2].flower[1];flag=true;}
							else if (m<(p.kData[k2].num -1)
									&& p.kData[p.kData[k2].flower[m+2]].utilFlag!=0)
							{k0=p.kData[k2].flower[m+2];flag=true;}
							if (flag) {
								z1=p.rData[k1].center;r1=p.rData[k1].rad;
								z2=p.rData[k2].center;r2=p.rData[k2].rad;
								r0=p.rData[k0].rad;
								sc=EuclMath.e_compcenter(z1,z2,r1,r2,(r1*r2/r0));
								p.rData[j].center=new Complex(sc.center);
								p.rData[j].rad=sc.rad;
								p.kData[j].utilFlag=1;
							}
							else k++;
						} // end of if 
						else k++;
					} // end of do loop 
					while (!flag && k<=(p.kData[j].num-1) );
				} // end of if 
			} while (!flag); // end of do 
			count++;
		} // end of while 
		// should have all centers and radii now 

		if (p.hes < 0) {
			Maxx = 1;
			minx = -1;
			Maxy = 1;
			miny = -1;
			for (int i = 1; i <= p.nodeCount; i++) {
				x = p.rData[i].center.x;
				y = p.rData[i].center.y;
				r = p.rData[i].rad;
				Maxx = (x + r > Maxx) ? x + r : Maxx;
				minx = (x - r < minx) ? x - r : minx;
				Maxy = (y + r > Maxy) ? y + r : Maxy;
				miny = (y - r < miny) ? y - r : miny;
			}
			z1.x = (Maxx + minx) * 0.5;
			z1.y = (Maxy + miny) * 0.5;
			scale = ((Maxx - minx > Maxy - miny) ? Maxx - minx : Maxy - miny) * 0.5;
			factor = (0.9) / (scale * 1.4142136);
			for (int i = 1; i <= p.nodeCount; i++) {
				cent = p.rData[i].center.minus(z1).times(factor);
				r = p.rData[i].rad * factor;
				sc = HyperbolicMath.e_to_h_data(cent, r);
				p.rData[i].center = new Complex(sc.center);
				p.rData[i].rad = sc.rad;
			}
		}
		p.free_overlaps(); // outdated.
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
