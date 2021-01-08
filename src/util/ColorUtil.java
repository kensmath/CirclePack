package util;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import packing.PackData;
import panels.CPScreen;

/**
 * This handles various color-handling utilities, such as the creation 
 * of 'washed-out' colors, selection from a pallet of "distinct" colors,
 * colors associated with complex arguments (a la Wegert), etc. 
 * @author kens
 *
 */
public class ColorUtil {
	
	/**
	 * There's a standard color wheel for the complex argument
	 * (used, eg., by Elias Wegert). This code returns that color.
	 * @param tha double, argument in radians
	 * @param alpha double, 
	 * @return new Color object
	 */
	public static Color ArgWheel(double tha,double alpha) {
		float dial=(float)(3.0*tha/Math.PI); // parameter in [0,6]
		float a=(float)alpha;
		while (dial<0)
			dial += (float)6.0;
		while (dial>6)
			dial -= (float)6.0;
		float r=(float)0.0;
		float g=(float)0.0;
		float b=(float)0.0;
		try {
		if (0<=dial && dial<=1.0) {
			r=(float)1.0;
			g=dial;
			return new Color(r,g,b,a);
		}
		if (dial<=2.0) {
			r=(float)2.0-dial;
			g=(float)1.0;
			return new Color(r,g,b,a);
		}
		if (dial<=3.0) {
			g=(float)1.0;
			b=dial-(float)2.0;
			return new Color(r,g,b,a);
		}
		if (dial<=4.0) {
			g=(float)4.0-dial;
			b=(float)1.0;
			return new Color(r,g,b,a);
		}
		if (dial<=5) {
			r=dial-(float)4.0;
			b=(float)1.0;
			return new Color(r,g,b,a);
		}
		else {
			r=(float)1.0;
			b=(float)6.0-dial;
			return new Color(r,g,b,a);
		}
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * There's a standard color wheel for the complex argument
	 * (used, eg., by Elias Wegert). This code returns that color
	 * with alpha set to 1.0.
	 * @param tha double, argument in radians
	 * @return new Color object
	 */
	public static Color ArgWheel(double tha) {
		return ArgWheel(tha,1.0);
	}

	/**
	 * Converts normal to "washed out" colors for use on back of the sphere 
	 * when it is not completely opaque. 
	 * @param color_in Color
	 * @param lambda double, 'lambda' is typically 'cpScreen.sphereOpacity/255'.
	 * @return 'Color' with 'alpha' set.
	 */
	public static Color ColorWash(Color color_in,double lambda) {
		int alpha=color_in.getAlpha();
		int prod=(int)(alpha*(1-lambda));
		return new Color(color_in.getRed(),color_in.getGreen(),color_in.getBlue(),prod);
	}
	
	/**
	 * Color tables have 16 fairly distinguishable colors to use
	 * as a spread. Return new 'Color' object for one of colors 
	 * indexed in [232,248).
	 * @param i int
	 * @return new Color
	 */
	public static Color spreadColor(int i) {
		int j=232+(i%16);
		return new Color(CPScreen.red[j],CPScreen.green[j],CPScreen.blue[j]);
	}
	
	/**
	 * given a vector of real ratios between positive quantities
	 * (e.g., radii of p compared to q) determine color ramp and 
	 * return an equal sized vector of color indices.
	 * TODO: should one take log and then interpolate?
	 * @param data Vector<Double>
	 * @return Vector<Integer>
	 */
	public static Vector<Integer> blue_red_ratio_ramp(Vector<Double> data) {
		if (data==null || data.size()==0) 
			return null;
		Vector<Integer> rslt=new Vector<Integer>(data.size());
		
		// groom slightly to handle any zeros
		double max=-1.0;
		double min=100000.0;
	    int mid=(int)(CPScreen.color_ramp_size/2);
		double boost=0.0; // boost all values to avoid zero if necessary
	    
		Iterator<Double> vits=data.iterator();
		while (vits.hasNext()) {
			double dta=(double)vits.next();
			if (dta<=PackData.OKERR)
				min=0.0;
			else if (dta<min) min=dta;
			if (dta>max) max=dta;
		}
		
		// some entries are very small
		if (min<=PackData.OKERR) { // all so small, return all middle color
			if (max<10000*PackData.OKERR) { 
			    for (int i=0;i<data.size();i++) 
			    	rslt.add(mid);
			    return rslt;
			}
			boost=max*.0001;
		}
		
		
		// determine b for scaling purposes
		double b=0.0; 
	    for (int i=0;i<data.size();i++) {
	    	double dta=data.get(i)+boost;
	    	double mldta=Math.log(dta);
	    	if (mldta>b) b=mldta;
	    	else if (mldta<-b) b=-1*mldta;
	    } 
	    if (b<PackData.OKERR) { // all ratios about 1, return all middle color
		    for (int i=0;i<data.size();i++) 
		    	rslt.add(mid);
	    }
	    else {
	    	for (int i=0;i<data.size();i++) {
	    		double dta=data.get(i)+boost;
	    		double mldta=Math.log(dta);
	    		if (mldta>0.0) 
	    			rslt.add((int)(mid+(mid-1)*mldta/b));
	    		else if (mldta<0.0)
	    			rslt.add(2+(int)((mid-2)*(1.0+mldta/b)));
	    		else rslt.add(mid);
	    	}
	    }
	    return rslt;
/*		double b=1.0;
	    for (int i=0;i<data.size();i++) {
	    	double dta=data.get(i);
	        if (dta>b) b=dta;
	        else if ((1/dta)>b) b=1/dta;
	    } // determine b for scaling purposes 
	    if (Math.abs(b-1.0)<PackData.OKERR) { 
		    for (int i=0;i<data.size();i++) 
		    	rslt.add(mid);
	    }
	    else {
	    	for (int i=0;i<data.size();i++) {
	    		double dta=data.get(i);
	    		if (dta>1.0) 
	    			rslt.add((int)(mid+(mid-1)*(1.0-dta)/(1.0-b)));
	    		else if (dta<1.0)
	    			rslt.add(1+(int)((mid-2)*(1.0-(1.0-1.0/dta)/(1.0-b))));
	    		else rslt.add(mid);
	    	}
	    }
	    return rslt;
*/	    
	}
	
	/**
	 * Given vector of doubles, create blue ramp for negative values,
	 * red ramp for positive values: more negative = more blue (down
	 * to 5), more positive = more red (up to 195). Closer to 0,
	 * color closer to white.
	 * 
	 * @param data Vector of Double
	 * @return Vector of Integer in same order
	 */
	public static Vector<Integer> blue_red_diff_ramp(Vector<Double> data) {
		if (data==null || data.size()==0) return null;
		Vector<Integer> output=new Vector<Integer>(data.size());
		// determine most negative, most positive for scaling purposes
		double miN=0.0;
		double maX=0.0;
	    for (int i=0;i<data.size();i++) {
	    	double dta=data.get(i);
	    	miN = (dta<miN) ? dta : miN;
	    	maX = (dta>maX) ? dta : maX;
	    } 
		for (int i=0;i<data.size();i++) {
	    	double dta=data.get(i);
	    	if (dta==0)
	    		output.add(Integer.valueOf(100)); // white
	    	else if (dta<0) 
	    		output.add(Integer.valueOf((int)(100.0-95.0*(dta/miN))));
	    	else 
	    		output.add(Integer.valueOf((int)(100.0+95.0*(dta/maX))));
		}

		return output;
	}
	
	/**
	 * Given vector of doubles, create blue ramp for negative values,
	 * red ramp for positive values: more negative = more blue (down
	 * to 5), more positive = more red (up to 195). Closer to 0,
	 * color closer to white.
	 * 
	 * @param data Vector of Double
	 * @return Vector of Color objects in same order
	 */
	public static Vector<Color> blue_red_diff_ramp_Color(Vector<Double> data) {
		if (data==null || data.size()==0) return null;
		Vector<Color> output=new Vector<Color>(data.size());
		// determine most negative, most positive for scaling purposes
		double miN=0.0;
		double maX=0.0;
	    for (int i=0;i<data.size();i++) {
	    	double dta=data.get(i);
	    	miN = (dta<miN) ? dta : miN;
	    	maX = (dta>maX) ? dta : maX;
	    } 
		for (int i=0;i<data.size();i++) {
	    	double dta=data.get(i);
	    	if (dta==0)
	    		output.add(CPScreen.coLor(Integer.valueOf(100))); // white
	    	else if (dta<0) 
	    		output.add(CPScreen.coLor(Integer.valueOf((int)(100.0-95.0*(dta/miN)))));
	    	else 
	    		output.add(CPScreen.coLor(Integer.valueOf((int)(100.0+95.0*(dta/maX)))));
		}

		return output;
	}
	
	/**
	 * Richter-type (logarithmic) red color ramp for real data.
	 * Transform data to x in [0,M], ramp on values log(x+1)/log(M+1). 
	 * Outputs [0.0,0.01] set to white, 1.0 set to full red. 
	 * NOTES: 
	 *  * If M<0.0001, return all white (not enough variation?).
	 *  * vector(0) entry is ignored, but all other entries of
	 *    data should have valid data.
	 * @param data Vector<Double> (first spot unused)
	 * @return Vector<Integer>, codes in [100,195] (white to red) (first unused)
	 */
	public static Vector<Color> richter_red_ramp(Vector<Double> data) {
		int dsize;
		if (data==null || (dsize=data.size())==0) return null;
		Vector<Color> output=new Vector<Color>(dsize);
		output.add(0,null); // first spot is background
		
		// determine most negative, most positive for scaling purposes
		double miN=0.0;
		double maX=0.0;
	    for (int i=1;i<dsize;i++) {
	    	double dta=data.get(i);
	    	miN = (dta<miN) ? dta : miN;
	    	maX = (dta>maX) ? dta : maX;
	    } 
	    double M=maX-miN;
	    if (M<.0001) { // data too clustered, return all background
	    	for (int i=1;i<dsize;i++) {
	    		output.add(CPScreen.getBGColor()); 
	    	}
	    	return output;
	    }
	    double denom=Math.log(M+1);
		for (int i=1;i<dsize;i++) {
	    	double newdata=Math.log(1+data.get(i)-miN)/denom;
	    	if (newdata<.01) 
	    		newdata=0.0;
	    	int indx=(int)(101.0+95.0*(newdata));
	    		output.add(new Color(CPScreen.red[indx],CPScreen.green[indx],CPScreen.blue[indx]));
		}
		return output; 
	}
	
	/**
	 * Colors chosen for each integer n=2,....,12 (e.g. for degree
	 * or number of sides of tile, etc.)
	 * TODO: should extend the range beyond 12
	 * @param n int
	 * @return new Color
	 */
	public static Color colorByDegree(int n) {
		  switch(n) {
		  case 2:
		  {return ColorUtil.cloneMe(CPScreen.coLor(230));}
		  case 3:
		  {return ColorUtil.cloneMe(CPScreen.coLor(245));}
		  case 4:
		  {return ColorUtil.cloneMe(CPScreen.coLor(242));}
		  case 5:
		  {return ColorUtil.cloneMe(CPScreen.coLor(20));}
		  case 6:
		  {return ColorUtil.cloneMe(CPScreen.coLor(100));}
		  case 7:
		  {return ColorUtil.cloneMe(CPScreen.coLor(180));}
		  case 8:
		  {return ColorUtil.cloneMe(CPScreen.coLor(209));}
		  case 9:
		  {return ColorUtil.cloneMe(CPScreen.coLor(241));}
		  case 10:
		  {return ColorUtil.cloneMe(CPScreen.coLor(227));}
		  case 11:
		  {return ColorUtil.cloneMe(CPScreen.coLor(229));}
		  case 12:
		  {return ColorUtil.cloneMe(CPScreen.coLor(240));}
		  default:
		  {return ColorUtil.cloneMe(CPScreen.coLor(248));}
		  } // end of switch
	}
	
	/**
	 * Compare rgb compoenents of two 'Color's.
	 * 'alpha' is ignored.
	 * @param c1 Color
	 * @param c2 Color
	 * @return true if rgb all equal.
	 */
	public static boolean equalColors(Color c1,Color c2) {
		if (c1.getRed()!=c2.getRed() || c1.getGreen()!=c2.getGreen() || c1.getBlue()!=c2.getBlue())
			return false;
		return true;
	}
	
	/**
	 * Clone; return null if 'c' is null.
	 * @param c Color
	 * @return nrw Color
	 */
	public static Color cloneMe(Color c) {
		if (c==null)
			return null;
		return new Color(c.getRed(),c.getGreen(),c.getBlue(),c.getAlpha());
	}
	
} 
