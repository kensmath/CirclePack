package util;

import allMains.CirclePack;
import circlePack.PackControl;
import packing.CPdrawing;

/**
 * DispOptions simply holds the default display options for a given circle 
 * packing and supplies it to 'disp' commands when specific flags are lacking. 
 * ('disp' and 'disp -wr' calls in 'PackControl.nakedDisp'). The boolean data 
 * elements here correspond with checkboxes in the DisplayPanel, 'tailored' is 
 * the user-specified text string in DisplayPanel (for advanced users).
 * @author kens
 *
 */
public class DispOptions {
	public String tailored;
	
	public boolean circles;
	public boolean cirfill;
	public boolean circolor;
	public boolean faces;
	public boolean facefill;
	public boolean facecolor;
	public boolean cirlabels;
	public boolean facelabels;
	public boolean unitcir;
	public boolean path;
	
	public boolean usetext;
	private CPdrawing parent;

	// Constructor
	public DispOptions(CPdrawing par) {
		parent=par;
		reset();
	}
	
	/**
	 * Set display options for circles/faces to their defaults
	 */
	public void reset() {
		// defaults
		circles=true;
		cirfill=circolor=false;
		faces=facefill=facecolor=false;
		cirlabels=facelabels=usetext=false;
		tailored=new String("");
	}

	/**
	 * Set individual display options
	 * @param name = one of: circles, cirfill, circolor,
	 * faces, facefill, facecolor, cirlabels, facelabels,
	 * or usetext
	 * @param state true or false (on or off)
	 */
	public void setOnOff(String name,boolean state) {
		if (name.equals("circles"))	circles=state;
		if (name.equals("cirfill"))	cirfill=state;
		if (name.equals("circolor")) circolor=state;
		if (name.equals("faces"))	faces=state;
		if (name.equals("facefill")) facefill=state;
		if (name.equals("facecolor")) facecolor=state;
		if (name.equals("cirlabels")) cirlabels=state;
		if (name.equals("facelabels")) facelabels=state;
		if (name.equals("usetext")) usetext=state;
	}
	
	/**
	 * Returns a vector with the states of the various display
	 * boolean switches.
	 * @return
	 */
	public Boolean[] getSavedStates() {
		Boolean[] bools=new Boolean[9];
		bools[0]=Boolean.valueOf(circles);
		bools[1]=Boolean.valueOf(cirfill);
		bools[2]=Boolean.valueOf(circolor);
		bools[3]=Boolean.valueOf(faces);
		bools[4]=Boolean.valueOf(facefill);
		bools[5]=Boolean.valueOf(facecolor);
		bools[6]=Boolean.valueOf(cirlabels);
		bools[7]=Boolean.valueOf(facelabels);
		bools[8]=Boolean.valueOf(usetext);
		return bools;
	}
	
	/**
	 * Store the string of display flags from the DisplayPanel;
	 * e.g., when active pack changes, have to save what was
	 * appearing here so it can be reestablished.
	 * @param flagstr
	 */
	public void storeTailored(String flagstr) {
		tailored=flagstr.trim();
	}

	/**
	 * Determines the current display options and converts into strings 
	 * to be concatenated with 'disp' calls. Starts with 'tailored'
	 * specification, else looks for options that have been checked
	 * for display. Can return null.
	 * @return String
	 */
	public String toString() {
		int hits=0;
		StringBuilder flags=new StringBuilder("");
		
		// use the tailored info?
		if (parent.getPackNum()==CirclePack.cpb.getActivePackNum()) {
			tailored=PackControl.screenCtrlFrame.displayPanel.flagField.getText().trim();
			usetext=PackControl.screenCtrlFrame.displayPanel.useText(); 
		}
		if (usetext && tailored.trim().length()>0)
			return tailored.trim();
		
		// else, use flags
		flags.append("-w ");
		if (unitcir) {
			hits++;
			flags.append(" -u");
		}
		if (circles) {
			hits++;
			flags.append(" -c");
			if (circolor) flags.append("c");
			if (cirfill) flags.append("f");
		}
		if (cirlabels) {
			hits++;
			flags.append(" -cn");
		}
		if (faces) {
			hits++;
			flags.append(" -f");
			if (facecolor) flags.append("c");
			if (facefill) flags.append("f");
		}
		if (facelabels) {
			hits++;
			flags.append(" -fn");
		}
		if (path) {
			hits++;
			flags.append(" -g");
		}
		if (hits==0) return null;
		return flags.toString();
	}
	
	/**
	 * Pick of bits from cf and ff to set display options for
	 * circles and faces, respectively.
	 * @param cf
	 * @param ff
	 */
	public void setOptions(int cf,int ff) {
		// circle stuff
		if ((cf & 1)!=1) circles=false;
		else circles=true;
		if ((cf & 2)!=2) cirfill=false;
		else cirfill=true;
		if ((cf & 8)!=8) circolor=false;
		else circolor=true;
		if ((cf & 16)==16) cirfill=true;
		if ((cf & 32)==32) cirlabels=true;
		else cirlabels=false;

		// face stuff
		if ((ff & 1)!=1) faces=false;
		else faces=true;
		if ((ff & 2)!=2) facefill=false;
		else facefill=true;
		if ((ff & 8)!=8) facecolor=false;
		else facecolor=true;
		if ((ff & 16)==16) facefill=true;
		if ((ff & 32)==32) facelabels=true;
		else facelabels=false;
	}

	
}
