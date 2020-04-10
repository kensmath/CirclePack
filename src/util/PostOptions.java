package util;

import allMains.CirclePack;
import circlePack.PackControl;
import panels.CPScreen;

/**
 * PostOptions simply holds the default PostScript options for a given circle 
 * packing and supplies it to 'post' commands when specific flags are lacking. 
 * The boolean data elements here correspond with checkboxes in the PSPanel, 
 * 'tailored' is  the user-specified text string in PSPanel (for advanced users).
 * @author kens
 *
 */public class PostOptions {
	 
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
		public boolean popup;
		public boolean save;
		public boolean append;
		public boolean print;
		public boolean jpg;

		public boolean usetext;
		private CPScreen parent;

		// Constructor
		public PostOptions(CPScreen par) {
			parent=par;
			reset();
		}

		/**
		 * Set post options to their defaults
		 */
		public void reset() {
			// defaults
			circles=popup=true;
			cirfill=circolor=false;
			faces=facefill=facecolor=false;
			cirlabels=facelabels=usetext=false;
			tailored="";
		}

		/**
		 * 
		 * @param name
		 * @param state
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
			if (name.equals("unitcircle")) unitcir=state;
			if (name.equals("path")) path=state;
			if (name.equals("popup")) popup=state;
			if (name.equals("save")) save=state;
			if (name.equals("append")) append=state;
			if (name.equals("print")) print=state; // implies save
			if (name.equals("jpg")) jpg=state; // implies print
			if (name.equals("usetext")) usetext=state;
		}
		
		/**
		 * returns the state of the various booleans
		 * @return
		 */
		public Boolean[] getSavedStates() {
			Boolean[] bools=new Boolean[16];
			bools[0]=Boolean.valueOf(circles);
			bools[1]=Boolean.valueOf(cirfill);
			bools[2]=Boolean.valueOf(circolor);
			bools[3]=Boolean.valueOf(faces);
			bools[4]=Boolean.valueOf(facefill);
			bools[5]=Boolean.valueOf(facecolor);
			bools[6]=Boolean.valueOf(cirlabels);
			bools[7]=Boolean.valueOf(facelabels);
			bools[8]=Boolean.valueOf(unitcir);
			bools[9]=Boolean.valueOf(path);
			bools[10]=Boolean.valueOf(popup);
			bools[11]=Boolean.valueOf(save);
			bools[12]=Boolean.valueOf(append);
			bools[13]=Boolean.valueOf(print);
			bools[14]=Boolean.valueOf(jpg);
			bools[15]=Boolean.valueOf(usetext);
			return bools;
		}
		
		/**
		 * Store the string of post flags from the PSPanel;
		 * e.g., when active pack changes, have to save what was
		 * appearing here so it can be reestablished.
		 * @param flagstr
		 */
		public void storeTailored(String flagstr) {
			tailored=flagstr.trim();
		}

		/**
		 * Converts the current circle options into strings to be concatenated 
		 * with 'post' calls. Can return null if there is nothing checked for posting. 
		 * @return String
		 */
		public String toString() {
			int hits=0;
			StringBuilder flags=new StringBuilder("");
			
			// use the tailored info?
			if (parent.getPackNum()==CirclePack.cpb.getActivePackNum()) {
				tailored=PackControl.outputFrame.postPanel.getFlags().trim();
			}
			
			// if flags are specified in the advanced user 'flagField', use them
			if (tailored.trim().length()>0)
				return tailored.trim();
			
			// else, use flags
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
