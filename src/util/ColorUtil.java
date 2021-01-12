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
	
	// set up static color info
	public static final int NUMCOLORS=255;
    public static final int color_ramp_size = 200;
	public static final int []red;
    public static final int []green;
    public static final int []blue;
    // Foreground, background colors
    public static final int FG_COLOR = 0;
    public static final int BG_COLOR = 255;
    public static final Color FG_Color;
    public static final Color BG_Color;

    static {
    	red = new int[NUMCOLORS+1];
    	green = new int[NUMCOLORS+1];
    	blue = new int[NUMCOLORS+1];

    	// set up colors data
    	blue_to_red_ramp(0);

    	// convert fore/background to 'Color' objects
    	FG_Color=new Color(red[FG_COLOR],green[FG_COLOR],blue[FG_COLOR]); 
    	BG_Color=new Color(red[BG_COLOR],green[BG_COLOR],blue[BG_COLOR]);
    }
    
	/**
	 * background color
	 * @return new Color
	 */
	public static Color getBGColor() {
		return ColorUtil.cloneMe(BG_Color);
	}

	/**
	 * foreground color
	 * @return new Color
	 */
	public static Color getFGColor() {
		return ColorUtil.cloneMe(FG_Color);
	}

    /** 
     * Note user has to set color_ramp_size, default generally 
     * COLOR_RAMP. Here we set up color ramp, starting with dark 
     * blue, ending with dark red. Index color_ramp_size/2 is white.
     * @param flag, int. If not zero do ramp only, and not the 
     *    'other' colors; caution, other colors are specified based 
     *    on color_ramp_size=200. 
     */
 	public static void blue_to_red_ramp(int flag) {
      int i,mid,col_factor=0;
     
      if ((mid=(int)(color_ramp_size/2.0))>0) {
    	  col_factor=(int)(255.0/mid);
    	  // blue ramp 
    	  for (i=0;i<mid;i++)
    	  {blue[i]=255;red[i]=green[i]=i*col_factor;}
    	  blue[0]=red[0]=green[0]=0;
    	  // middle is white 
    	  blue[mid]=red[mid]=green[mid]=255;
    	  // red ramp 
    	  for (i=mid+1;i<2*mid;i++)
    	  {red[i]=255;blue[i]=green[i]=255-(i-mid)*col_factor;}
    	  if (flag != 0) return;
     }	
     else if (flag != 0) return;
     
     if (mid<1) mid=1;
     
     // Some mixed colors for filler 
     for (i=2*mid;i<210;i++) 
         {red[i]=green[i]=blue[i]=255-255*((int)((i-2*mid)/(210-2*mid)));}
     for (i=231;i<255;i++)
     {red[i]=0;green[i]=blue[i]=255-10*(i-230);}
     /* additional colors by Monica Hurdal */
     
     red[201]=255;green[201]=200;blue[201]=200; /* light pink */
     red[202]=200;green[202]=225;blue[202]=255; /* light blue */
     red[203]=200;green[203]=255;blue[203]=200; /* light green */
     red[204]=255;green[204]=230;blue[204]=200; /* light orange */
     red[205]=180;green[205]=180;blue[205]=240; /* light purple */
     red[206]=50;green[206]=128;blue[206]=50;   /* dark green */
     red[207]=128;green[207]=50;blue[207]=128;  
     red[208]=203;green[208]=255;blue[208]=102;
     red[209]=255;green[209]=255;blue[209]=0;   /* yellow */
     red[210]=255;green[210]=0;blue[210]=0;     /* red */
     red[211]=245;green[211]=94;blue[211]=0;    /* orange ramp: very dark */
     red[212]=255;green[212]=146;blue[212]=29;  /* orange ramp: dark */
     red[213]=255;green[213]=159;blue[213]=56;  /* orange ramp: medium */
     red[214]=255;green[214]=172;blue[214]=84;  /* orange ramp: light */
     red[215]=255;green[215]=185;blue[215]=110; /* orange ramp: lighter */
     red[216]=255;green[216]=204;blue[216]=152; /* orange ramp: lighter */
     red[217]=255;green[217]=218;blue[217]=178; /* orange ramp: very light */
     red[218]=0;green[218]=255;blue[218]=0;     /* bright green */
     red[219]=34;green[219]=139;blue[219]=34;
     red[220]=64;green[220]=224;blue[220]=208;
     red[221]=0;green[221]=255;blue[221]=255;   /* cyan */
     red[222]=135;green[222]=206;blue[222]=250;
     red[223]=95;green[223]=158;blue[223]=160;
     red[224]=205;green[224]=133;blue[224]=63;
     red[225]=160;green[225]=82;blue[225]=45;
     red[226]=235;green[226]=110;blue[226]=100;
     red[227]=255;green[227]=140;blue[227]=0;   /* orange */
     red[228]=221;green[228]=160;blue[228]=221;
     red[229]=185;green[229]=48;blue[229]=185;
     red[230]=205;green[230]=205;blue[230]=205; /* light grey */
     red[231]=50;green[231]=50;blue[231]=50;    /* dark grey */
     
     // 16 "spread" colors (see 'ColorUtil.spreadColorCode/spreadColor')
     // TODO: might analyze these to get clearer distinctions
     red[232]=255;green[232]=0;blue[232]=0;     /* red */
     red[233]=0;green[233]=255;blue[233]=0;     /* bright green */
     red[234]=0;green[234]=0;blue[234]=255;     /* blue */
     red[235]=255;green[235]=blue[235]=125;
     red[236]=0;green[237]=255;blue[237]=255;   /* cyan */
     red[237]=234;green[236]=138;blue[236]=0;   /* orange */
     red[238]=178;green[238]=0;blue[238]=255;   /* purple */
     red[239]=66;green[239]=138;blue[239]=66;   /* forest green */
     red[240]=255;green[240]=140;blue[240]=0;   /* orange */
     red[241]=255;green[241]=0;blue[241]=255;   /* magenta (pink) */
     red[242]=0;green[242]=blue[242]=155;
     red[243]=255;green[243]=255;blue[243]=0;   /* yellow (doesn't show up well)*/
     red[244]=green[244]=125;blue[244]=255;
     red[245]=blue[245]=125;green[245]=255;
     red[246]=255;green[246]=172;blue[246]=84;  /* orange ramp: light */
     red[247]=180;green[247]=180;blue[247]=240; /* light purple */
     
     // misc. 
     red[248]=128;green[248]=128;blue[248]=128; /* grey */
     red[249]=160;green[249]=80;blue[249]=0;    /* brown */
     red[250]=0;green[250]=0;blue[250]=0;       /* black */

     red[255]=blue[255]=green[255]=255;
     
     return;
	} /* blue_to_red_ramp */
 
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
		return new Color(red[j],green[j],blue[j]);
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
	    int mid=(int)(color_ramp_size/2);
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
	    		output.add(ColorUtil.coLor(Integer.valueOf(100))); // white
	    	else if (dta<0) 
	    		output.add(ColorUtil.coLor(Integer.valueOf((int)(100.0-95.0*(dta/miN)))));
	    	else 
	    		output.add(ColorUtil.coLor(Integer.valueOf((int)(100.0+95.0*(dta/maX)))));
		}

		return output;
	}

	/**
	 * Convert 'Color' object to integer index in 'CirclePack' color tables.
	 * @param color Color
	 * @return int, color index
	 */
	public static int col_to_table(Color color) {
		return col_to_table(color.getRed(),color.getGreen(),color.getBlue());
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
	    		output.add(ColorUtil.getBGColor()); 
	    	}
	    	return output;
	    }
	    double denom=Math.log(M+1);
		for (int i=1;i<dsize;i++) {
	    	double newdata=Math.log(1+data.get(i)-miN)/denom;
	    	if (newdata<.01) 
	    		newdata=0.0;
	    	int indx=(int)(101.0+95.0*(newdata));
	    		output.add(new Color(red[indx],green[indx],blue[indx]));
		}
		return output; 
	}
	
	/** 
	 * For converting r g b (0-255) colors into index of "closest" 
	 * color in current colortable. 
	 *
	 * TODO: This is temporary (9/05) until I get rid of colortables 
	 * 
	 * @param rd int
	 * @param gn int
	 * @param bl int
	 * @return int, index to CirclePack colortable
	*/
	public static int col_to_table(int rd, int gn, int bl) {
	  int dist;
	  int idx=0;
	  dist=Math.abs((int)red[idx]-rd)+
	  Math.abs((int)green[idx]-gn)+
	  Math.abs((int)blue[idx]-bl);
	  if (dist==0) return idx;
	  int mndist=1000;
	  for (int i=0;i<=NUMCOLORS;i++) {
	    dist=Math.abs((int)red[i]-rd)+
		Math.abs((int)green[i]-gn)+
		Math.abs((int)blue[i]-bl);
	    if (dist==0) {
	      idx=i;
	      return idx;
	    }
	    if(dist<mndist) {
	      mndist=dist;
	      idx=i;
	    }
	  }
	  return idx;
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
		  {return cloneMe(coLor(230));}
		  case 3:
		  {return cloneMe(coLor(245));}
		  case 4:
		  {return cloneMe(coLor(242));}
		  case 5:
		  {return cloneMe(coLor(20));}
		  case 6:
		  {return cloneMe(coLor(100));}
		  case 7:
		  {return cloneMe(coLor(180));}
		  case 8:
		  {return cloneMe(coLor(209));}
		  case 9:
		  {return cloneMe(coLor(241));}
		  case 10:
		  {return cloneMe(coLor(227));}
		  case 11:
		  {return cloneMe(coLor(229));}
		  case 12:
		  {return cloneMe(coLor(240));}
		  default:
		  {return cloneMe(coLor(248));}
		  } // end of switch
	}
	
	/**
	 * Compare rgb components of two 'Color's; 'alpha' is ignored.
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

	/**
	 * Create Color object using index to color table and opacity
	 * @param indx int, in [0,255]
	 * @param opacity
	 * @return Color
	 */
	public static Color coLor(int indx,int opacity) {
		if (indx<0 || indx>255)
			return new Color(red[FG_COLOR],green[FG_COLOR],blue[FG_COLOR],opacity);
		return new Color(red[indx],green[indx],blue[indx],opacity);
	}

	/**
	 * Create actual 'Color' object using CirclePack index to color table 
	 * @param indx int, in [0,255]
	 * @return Color
	 */
	public static Color coLor(int indx) {
		if (indx<0 || indx>255)
			return getFGColor();
		return new Color(red[indx],green[indx],blue[indx]);
	}

	/**
	 * Create 'Color' using Integer (versus int) index to color table 
	 * @param indx, Integer 
	 * @return Color, default to FG_Color
	 */
	public static Color coLor(Integer indx) {
		int val=indx.intValue();
		return coLor(val);
	}
	
} 
