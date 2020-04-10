package util;

import java.util.Random;

import complex.Complex;

/**
 * Class with static routines to create random paths of various
 * types. It was started (8/09) to generate random points on the
 * unit circles.
 * @author kens
 *
 */
public class RandPaths {

	/**
	 * Create array of N random points on the unit circle with 
	 * arguments increasing in [0,2Pi]. If 'homeo' is given, then
	 * points are chosend randomly with respect to interpolation 
	 * of this homeomorphism.
	 * @param N
	 * @param homeo: homeomorphism of circle given [0,1] to [0,1]
	 * @param anchored, boolean: true, then first point is z=1.
	 * @return Complex[], null if error or less than N points
	 */
	public static Complex []homeoCirclePath(int N,double []hX,
			double []hY,boolean anchored) {
		double []increments=new double[N];
		double length=0.0;
		Random rand=new Random(); // can put seed for debugging
		for (int n=0;n<N;n++) 
			length +=increments[n]=rand.nextDouble();
		for (int n=1;n<N;n++)
			increments[n] /=length;
		if (anchored) 
			increments[0]=0.0;
		for (int n=1;n<N;n++)  
			increments[n] += increments[n-1];

		/* If homeomorphism is given as hX, hY vectors,
		 * want random points chosen in balanced way, in essence 
		 * replacing the path x-->y by x-->(x+y)/2. This should
		 * maintain monotonicity, but not favor places where slope
		 * of homeomorphism is small versus those where's its large.
		 * We simultaneously do the linear interpolation.
		 */
		if (hX!=null && hY!=null && hX.length==hY.length) {
			double []ticks=new double[hX.length];
			for (int j=0;j<hX.length;j++)
				ticks[j]=(hX[j]+hY[j])/2.0;
			int indx=0;
			double start=0.0; // should be ticks[0]
			double end=ticks[1];
			for (int n=0;n<N;n++) {
				start=ticks[indx];
				while(indx<(ticks.length-1) && increments[n]>ticks[indx])
					indx++;
				start=ticks[indx];
				if (indx==ticks.length-1) // reached end of homeo
					end=1.0;
				else end=ticks[indx+1];
				double lambda=(increments[n]-start)/(end-start);
				increments[n]=(1.0-lambda)*hY[indx]+lambda*hY[indx+1];
			}
		}
		
		Complex []path=new Complex[N];
		for (int n=0;n<N;n++) {
			double arg=increments[n]*2.0*Math.PI;
			path[n]=new Complex(Math.cos(arg),Math.sin(arg));
		}
		return path;
	}
	
	/**
	 * Create array of N random points on the unit circle with 
	 * arguments increasing in [0,2Pi].
	 * @param N
	 * @param anchored, boolean: true, then first point is z=1.
	 * @return Complex[], null if error or less than N points
	 */
	public static Complex []unitCirclePath(int N,boolean anchored) {
		return homeoCirclePath(N,null,null,anchored);
	}

}
