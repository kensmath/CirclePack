package widgets;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

import allMains.CirclePack;
import exceptions.DataException;
import listManip.GraphLink;
import listManip.NodeLink;
import packing.PackData;
import util.StringUtil;

/**
 * Create a slider of given type frame for packing p. Processing
 * takes extra work because optional command strings may occur as
 * quoted strings.
 * @author kstephe2, 6/2020
 *
 */
public class CreateSliderFrame {

	/**
	 * Create with specified objects, default to all
	 * @param p PackData
	 * @param type int; 0=radii, 1=schwarzians
	 * @param items Vector<String>, may be null or empty
	 * @return -1 on error
	 */
	public static int createSliderFrame(PackData p,int type,Vector<String> items) {

		if (type == 0) { // radii
			if (p.radiiSliders != null) {
				p.radiiSliders.dispose();
				p.radiiSliders=null;
			}
			p.radiiSliders = new RadiiSliders(p,"","",new NodeLink(p,items));
			p.radiiSliders.setVisible(true);
			return p.radiiSliders.sliderCount;
		}
		if (type == 1) { // schwarzians
			if (p.schwarzSliders != null) {
				p.schwarzSliders.dispose();
				p.schwarzSliders=null;
			}
			p.schwarzSliders = new SchwarzSliders(p,"","", new GraphLink(p,items));
			p.schwarzSliders.setVisible(true);
			return p.schwarzSliders.sliderCount;
		}
		if (type == 2) { // angle sums
			if (p.angSumSliders != null) {
				p.angSumSliders.dispose();
				p.angSumSliders=null;
			}
			p.angSumSliders = new AngSumSliders(p,"","", new NodeLink(p,items));
			p.angSumSliders.setVisible(true);
			return p.angSumSliders.sliderCount;
		}
		return 0;
	}

	/** Note: because there may be command strings in quotes
	 * and these may contain semicolons and flags and 'Obj',
	 * we have to process flagSegs by hand.
	 * TODO: Because of semicolons, command parser may have
	 * incorrectly broken up the command strings. Have to
	 * look into this.
	 * @param p PackData
	 * @param type int
	 * @param strbld StringBuilder, flagSegs reconstituted by calling routine
	 * @return count of sliders created, 0 on error
	 */
	public static int createSliderFrame(PackData p,int type,StringBuilder strbld) {

		// break input into maximal un-trimmed segments defined by quotes '"'.
		Vector<StringBuilder> segments = StringUtil.quoteAnalyzer(strbld);
		String chgCmd="";
		String mvCmd="";
		String optCmd="";
		NodeLink vlist=null;
		GraphLink glist=null;
		
		if (segments==null || segments.size()<2) {
			CirclePack.cpb.errMsg("usage: slider: looking for '-[cmo] {cmd}' flags");
			return 0;
		}
		
		// get firstStr
		String firstStr=segments.get(0).toString().trim();
		if (firstStr.length()==0 || firstStr.charAt(0)=='"') {
			CirclePack.cpb.errMsg("usage: slider: looking for -[cmo] flag first);");
			return 0;
		}
		
		// get lastStr and process as object list
		int s = segments.size();
		String lastStr = segments.get(s - 1).toString().trim();
		if (lastStr.length() > 0 && lastStr.charAt(0) != '"') 
			segments.remove(s - 1); // yes, this should be specifying objects
		else 
			lastStr="";
		
		try {
			if (type == 0) { // vertices
				if (lastStr.length() == 0)
					vlist = new NodeLink(p, "a"); // default to all
				else
					vlist = new NodeLink(p, lastStr);
				if (vlist == null || vlist.size() == 0) {
					CirclePack.cpb.errMsg("usage: malformed 'slider' object list");
					return 0;
				}
			}
		// Note: for schwarzians, 'lastStr' should be face pairs <f,g>
		else if (type == 1) { // schwarzians
			if (lastStr.length() == 0)
				glist = new GraphLink(p, "s"); // spanning tree
			else
				glist = new GraphLink(p, lastStr);
			if (glist == null || glist.size() == 0) {
				CirclePack.cpb.errMsg("usage: slider ..... {glist}");
				return 0;
			}
		}
		else if (type == 2) { // vertices
				if (lastStr.length() == 0)
					vlist = new NodeLink(p, "i"); // default to interior
				else
					vlist = new NodeLink(p, lastStr);
				if (vlist == null || vlist.size() == 0) {
					CirclePack.cpb.errMsg("usage: malformed 'slider' object list");
					return 0;
				}
		}
		} catch (ArrayIndexOutOfBoundsException aox) {
			CirclePack.cpb.errMsg("usage: malformed 'slider' command");
			return 0;
		}

		// now go through the segments
		try {
			Iterator<StringBuilder> sit = segments.iterator();
			while (sit.hasNext()) {
				String leadStr = sit.next().toString().trim();
				String quoteStr = sit.next().toString().trim();
				if (leadStr.contains("-c")) {
					if (quoteStr.charAt(0) != '"')
						throw new DataException();
					chgCmd = quoteStr.substring(1, quoteStr.length() - 1); // get change cmd, removing '"'
				} else if (leadStr.contains("-m")) {
					if (quoteStr.charAt(0) != '"')
						throw new DataException();
					mvCmd = quoteStr.substring(1, quoteStr.length() - 1); // get move cmd, removing '"'
				} else if (leadStr.contains("-o")) {
					if (quoteStr.charAt(0) != '"')
						throw new DataException();
					optCmd = quoteStr.substring(1, quoteStr.length() - 1); // get optional cmd, removing '"'
				}
			}
		} catch (NoSuchElementException nse) {
		// just continue with what we have
		} catch (DataException dex) {
			CirclePack.cpb.errMsg("usage: malformed 'slider' command");
			return 0;
		}
		
		if (chgCmd.length()>0)
			chgCmd=StringUtil.replaceSubstring(chgCmd,"Obj","_Obj");
		if (mvCmd.length()>0)
			mvCmd=StringUtil.replaceSubstring(mvCmd,"Obj","_Obj");
		if (optCmd.length()>0)
			optCmd=StringUtil.replaceSubstring(optCmd,"Obj","_Obj");

		// ready to start slider
		if (type == 0) { // radii
			if (p.radiiSliders != null) {
				p.radiiSliders.dispose();
				p.radiiSliders=null;
			}
			p.radiiSliders = new RadiiSliders(p, chgCmd, mvCmd, vlist);
			if (optCmd.length()>0)
				p.radiiSliders.optCmdField.setText(optCmd);
			p.radiiSliders.setVisible(true);
			return p.radiiSliders.sliderCount;
		}
		if (type == 1) { // schwarzians
			if (p.schwarzSliders != null) {
				p.schwarzSliders.dispose();
				p.schwarzSliders=null;
			}
			p.schwarzSliders = new SchwarzSliders(p, chgCmd, mvCmd, glist);
			if (optCmd.length()>0)
				p.schwarzSliders.optCmdField.setText(optCmd);
			p.schwarzSliders.setVisible(true);
			return p.schwarzSliders.sliderCount;
		}
		if (type == 2) { // angle sums
			if (p.angSumSliders != null) {
				p.angSumSliders.dispose();
				p.angSumSliders=null;
			}
			p.angSumSliders = new AngSumSliders(p, chgCmd, mvCmd, vlist);
			if (optCmd.length()>0)
				p.angSumSliders.optCmdField.setText(optCmd);
			p.angSumSliders.setVisible(true);
			return p.angSumSliders.sliderCount;
		}
		
		return -1;
	}
	
}
