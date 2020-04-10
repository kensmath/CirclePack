package util;

import java.awt.Color;

import allMains.CPBase;
import panels.CPScreen;

/**
 * Commands for displaying objects in CirclePack canvasses: encode
 * draw, fill, thickness, and possibly depth data in compact form.
 * 
 * Colors=complicated: at most one color can be set, colorIsSet=true.
 * E.g., 'c25' is taken as a color code for fill. However, 'cc25'
 * would do both --- the 'c' without a trailing number means 
 * to draw with colored bdry, colBorder=true. If color is called
 * for but colorIsSet=false, then color is set elsewhere -- e.g. 
 * its own 'color' data. 
 *  
 * @author kstephe2
 *
 */
public class DispFlags {
	String dispStr;	    // compact display string
	public boolean draw;		// draw or don't draw?
	public boolean fill;		// object filled vs open?
	public boolean colBorder;   // border in color? vs foreground
	public boolean label;		// show index?
	private String labelStr;    // label (generally index, set later)
	public boolean colorIsSet;  // true -> 'color' provided in the string, else as stored
	private Color color; 		// color (set later, if !colorIsSet)
	public Integer thickness;	// line thickness
	public Integer depth;		// depth for subdivision tilings
	public Integer fillOpacity; // may be null --- set from CPScreen for 'fill' operations  
	
	// Constructor
	public DispFlags(String str) {
		if (str!=null)
			dispStr = new String(str);
		else 
			dispStr=new String("");
		labelStr = null;
		draw = true; // default to true
		fill = false;
		label = false;
		colBorder = false;
		colorIsSet = false;
		color = null;
		thickness = Integer.valueOf(0);
		depth = 0;
		fillOpacity=Integer.valueOf(CPBase.DEFAULT_FILL_OPACITY);
		parseDispStr(str);
	}
	
	public DispFlags(String str,int opacity) {
		this(str);
		fillOpacity=Integer.valueOf(opacity);
	}

	/**
	 * Initial parsing of display string
	 * @param str
	 */
	public void parseDispStr(String str) {

		if (str != null && str.length() > 0) {

			if (str.startsWith("-"))
				str=str.substring(1);
			
			// need buffer to manipulate
			StringBuilder strbuf = new StringBuilder(str.trim());

			// truncate anything after first whitespace
			int k = 0;
			while (k < strbuf.length() && !Character.isWhitespace(strbuf.charAt(k))) {
				k++;
			}
			if (k < strbuf.length())
				strbuf.delete(k, strbuf.length());

			// =========== searches ================

			// foreground fill?
			if ((k = strbuf.indexOf("fg")) >= 0) {
				color = CPScreen.getFGColor();
				colorIsSet = true;
				strbuf.delete(k, k + 2);
			}
			// else background fill?
			else if ((k = strbuf.indexOf("bg")) >= 0) {
				color = CPScreen.getBGColor();
				colorIsSet = true;
				strbuf.delete(k, k + 2);
			}

			// fill object? ('fg' would have been removed)
			if ((k = strbuf.indexOf("f")) >= 0)
				fill = true;

			// label? remove the 'n' so it doesn't interfere
			if ((k = strbuf.indexOf("n")) >= 0) {
				label = true;
				strbuf.delete(k, k + 1);
			}

			// 'c' can mean draw object in color; but if
			// followed by digit, must be color code
			if ((k = strbuf.indexOf("c")) >= 0
					&& (strbuf.length() == (k + 1) || 
					!Character.isDigit(strbuf.charAt(k + 1)))) {
				colBorder = true;
				strbuf.delete(k, k + 1);
			}

			// Can get at most one color code:
			// might be 'fc' or 'cf' for fill color or just 'c'
			// for coloring the object itself.
			String digits = null;
			if ((k = strbuf.indexOf("fc")) >= 0 || (k = strbuf.indexOf("cf")) >= 0)
				digits = StringUtil.getDigitStr(strbuf, k + 2);
			// 'c' with digits would have been caught earlier
			else if ((k = strbuf.indexOf("c")) >= 0) {
				colBorder = true;
				digits = StringUtil.getDigitStr(strbuf, k + 1);
			}
			int K = 0;
			if (digits != null) {
				K = digits.length();
				color = CPScreen.coLor(Integer.parseInt(digits));
				colorIsSet = true;
				strbuf.delete(k, k + K + 1);
			}

			// 't' for thickness?
			if ((k = strbuf.indexOf("t")) >= 0) {
				digits = StringUtil.getDigitStr(strbuf, k + 1);
				K = 0;
				if (digits != null) {
					K = digits.length();
					thickness = Integer.parseInt(digits);
					if (thickness < 0)
						thickness = Integer.valueOf(0);
					if (thickness > 15)
						thickness = Integer.valueOf(15);
				}
				strbuf.delete(k, k + K + 1);
			}

			// 'd' for depth? (1/2014)
			// (intended for use with tiling tree depth -- may have other uses)
			if ((k = strbuf.indexOf("d")) >= 0) {
				digits = StringUtil.getDigitStr(strbuf, k + 1);
				K = 0;
				if (digits != null) {
					K = digits.length();
					depth = Integer.parseInt(digits);
				}
				strbuf.delete(k, k + K + 1);
			}
		}
	}
	
	/**
	 * @return Color
	 */
	public Color getColor() {
		return CPScreen.cloneColor(color);
	}
	
	/** 
	 * Fill uses 'fillOpacity' to modify color
	 * @return Color
	 */
	public Color getFillColor() {
		return new Color(color.getRed(),color.getGreen(),color.getBlue(),fillOpacity);
	}
	
	/**
	 * @param col Color
	 */
	public void setColor(Color col) {
		color=CPScreen.cloneColor(col);
	}
	
	/**
	 * Only get this if boolean 'label' is true
	 * @return String
	 */
	public String getLabel() {
		if (label)
			return labelStr;
		return null;
	}
	
	/**
	 * This sets boolean 'label' true and sets 'labelStr'
	 * @param lab String
	 */
	public void setLabel(String lab) {
		label=true;
		labelStr=new String(lab);
	}
	
	/**
	 * Put info back in compact string form to be included
	 * in 'disp' command
	 * 
	 * Samples: 
	 * * 'fc' fill with object's own color (e.g. face.color); 
	 * * 'fc80' fill with color 80; 
	 * * 'fcc80' fill and draw object itself with color 80;
	 * * 'cbg' color object itself using background
	 * * 'c80' object itself with color 80.
	 * @return String
	 */
	public String reconstitute() {
		StringBuilder stb=new StringBuilder("");
		boolean bg_set=false;
		boolean fg_set=false;
		if (color!=null && color==CPScreen.getBGColor())
			bg_set=true;
		else if (color!=null && color==CPScreen.getFGColor())
			fg_set=true;
		
		// special case: draw thing itself in background color
		if (!fill && color==null && colBorder && bg_set) 
			stb.append("cbg");
		
		// typical
		else {
			if (fill)
				stb.append('f');
			if (fg_set || bg_set) { 
				if (fg_set)
					stb.append("fg");
				else 
					stb.append("bg");
			}
			else { // 'fg' or 'bg' preempts 'color'
				if (colBorder && (fill || color==null))
					stb.append('c');
			}
			if (color!=null) 
				stb.append("c"+CPScreen.col_to_table(color));
		}
		if (label)
			stb.append('n');
		if (thickness!=1)
			stb.append("t"+thickness);
		if (depth!=0)
			stb.append("d"+Math.abs(depth));
		
		return stb.toString();
	}

	/**
	 * clone this set of flags
	 */
	public DispFlags clone() {
		DispFlags rslt=new DispFlags("");
		rslt.dispStr=new String(this.dispStr);
		rslt.draw=this.draw;
		rslt.fill=this.fill;
		rslt.label=this.label;
		rslt.colBorder=this.colBorder;
		rslt.color=CPScreen.cloneColor(this.color);
		rslt.colorIsSet=this.colorIsSet;
		rslt.thickness=this.thickness;
		rslt.depth=this.depth;
		rslt.fillOpacity=Integer.valueOf(this.fillOpacity);
		return rslt;
	}

}
