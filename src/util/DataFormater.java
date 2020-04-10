package util;

/**
 * This stores strings (for a particular packing) describing data 
 * formating for items on the 'Output' tab so they can be set when
 * the active pack is changed. See Help regarding 'output' command.
 * @author kens
 *
 */
public class DataFormater {
	public String prefixText;
	public String suffixText;
	public String dataTypes;
	public String objList;

	public DataFormater(String pref,String datat,String olist,String suf) {
		update(pref,datat,olist,suf);
	}
	
	public void update(String pref,String datat,String olist,String suf) {
		prefixText=pref;
		dataTypes=datat;
		objList=olist;
		suffixText=suf;
	}
	
	public DataFormater() {
		this("","","","");
	}
}
