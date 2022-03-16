/*
 * Convert.java
 *
 * Created on September 9, 2005, 11:57 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.jimrolf.convert;

/**
 *
 * @author jimrolf
 * @version 1.0
 */
public class Convert {
    
    /** Creates a new instance of Convert */
    public Convert() {
    }
   

    public static double[] toDouble(Object object) {
        return (double[]) object;
    }
    
    public static long toLong(String snum) {
        long lnum = (new Long(snum)).longValue();
        return lnum;
    }
    
    public static float toFloat(String snum) {
        float dnum = (new Float(snum)).floatValue();
        return dnum;
    }
    
    public static float toFloat(int inum) {
        float dnum = (new Float(inum)).floatValue();
        return dnum;
    }
    
    public static double toDouble(String snum) {
        double dnum = (new Double(snum)).doubleValue();
        return dnum;
    }
    
    public static double toDouble(int inum) {
        double dnum = (new Integer(inum)).doubleValue();
        return dnum;
    }
    
    public static int toInt(String snum) {
        int inum = (new Integer(snum)).intValue();
        return inum;
    }
    
    // Truncates double.
    public static int toInt(double dnum) {
        int inum = (new Double(dnum)).intValue();
        return inum;
    }
    
    //Rounds double.
    public static int toRoundedInt(double dnum) {
        int inum=(int)Math.floor(dnum+0.5d);//assymetrical rounding used here
        /*int inum = 0;
        if (dnum >= 0.0) {
            inum = (new Double(dnum + 0.5)).intValue();
        } else {
            inum = (new Double(dnum - 0.5)).intValue();
        }*/
        return inum;
    }
    
    //Converts double to a String
    public static String toString(double dnum) {
        return Double.toString(dnum);
    }
    
    //Converts int to a String
    public static String toString(int inum) {
        return Integer.toString(inum);
    }
    
    //Converts float to a String
    public static String toString(float fnum) {
        return Float.toString(fnum);
    }
    
    //Tests to see if a string is a double
    public static boolean isDouble(String snum) {
        boolean returnVal = true;
        try {
            Double.parseDouble(snum);
        } catch (NumberFormatException e) {
            returnVal = false;
        }
        return returnVal;
    }
    
    public static boolean isInt(String snum) {
        boolean returnVal = true;
        try {
            Integer.parseInt(snum);
        } catch (NumberFormatException e) {
            returnVal = false;
        }
        return returnVal;
    }
    
      public static boolean isFloat(String snum) {
        boolean returnVal = true;
        try {
            Float.parseFloat(snum);
        } catch (NumberFormatException e) {
            returnVal = false;
        }
        return returnVal;
    }
    
}
