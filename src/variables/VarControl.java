package variables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.DataException;
import exceptions.ParserException;
import input.CommandStrParser;
import input.QueryParser;
import packing.PackData;
import util.CallPacket;
import util.StringUtil;

/**
 * The 'variables' package is for handling variables in 
 * 'CirclePack'. Basically, any string can be treated as a 
 * named variable and associated with a string 'value'. 
 * Initially, definitions are limited (e.g., 'X:= 4.5'), 
 * but additional ways are anticipated, as via queries, 
 * 'r:=?rad 12'.
 * 
 * On executing commands, variable name 'X' is specified via 
 * '_X ' (note the trailing whitespace to delineate the 
 * variable name string) and is simply replaced by its current 
 * value string (which is then processed in the usual way, so 
 * it may be a number, a string, etc.) 
 * 
 * I am splitting off the GUI components, which allow for
 * input/display and for slider control. For example, the
 * keyword "[SLIDER..] " at beginning of value string means 
 * the variable is under slider control, see 
 * 'SliderControlPanel'. CirclePack will then look for or 
 * adjust the value via the SliderPanel in Variables tab 
 * of the 'Pack Info' frame. 
 * 
 * Only restriction on names/values: they cannot contain ';'.
 * 
 * @author kens
 *
 */
public class VarControl {

	// 'variables' is hashmap for the current variables.
	public LinkedHashMap<String, String> variables;
	
	// 'sliderVariables' is hashmap for the subset of variables 
	//     currently under slider control. Slider variables 
	//     added/removed must also be added/removed in 'variables' 
	//     list of 'VariableControlTableModel', (identified by 
	//     having value string starting with "[SLIDER]".)
	public LinkedHashMap<String, SliderPanel> sliderVariables;
	
	protected VariableControlTableModel varModel;
	
	// Constructor
	public VarControl() {
		variables = new LinkedHashMap<String, String>();
		sliderVariables = new LinkedHashMap<String, SliderPanel>();
		varModel = new VariableControlTableModel();
	}

	/**
	 * Get the 'String' associated with 'vkey'. (Note: 'vkey' may
	 * contain leading '_'s, which we simply strip off.)
	 * @param vkey String, variable name
	 * @return String value or null on failure.
	 */
	public String getValue(String vkey) {
		if (vkey==null || vkey.length()==0)
			return null;
		int k=0;
		while(k<vkey.length() && vkey.charAt(k)=='_')
			k++;
		if (k==vkey.length())
			return null;
		vkey=vkey.substring(k);

		return varModel.get(vkey);
	}

	/**
	 * In hash table, set 'vkey' string to value given in 'flagSegs'.
	 * If 'vkey' already in the table, this replaces its value.
	 * Routine case, the value is a string, usually 'reconstituted' 
	 * flagSeqs. (Note that we may even want vkey to be associated 
	 * with command string (though ';'s would be a problem).)
	 * 
	 * But also, want various options:
	 * TODO: to be implemented as necessary and as time permits:
	 *    
	 *    ** If data starts with '?' or with bracketed string '{..}',
	 *       then this is a call for finding or computing some value. 
	 *       Have to write a separate parser for this.
	 *       
	 *    ** If data starts with '&', this means we want to store
	 *       specific current value (as string) of some quantity 
	 *       (rather than storing the string describing it.) 
	 *       E.g. if data='M', then would store "M" (as used, e.g., 
	 *       to get maximum index); this value would vary depending on 
	 *       when '_M' was used. On the other hand, data='&M' would 
	 *       store the current value (as a string) of maximum index,
	 *       e.g. 112 (as a String).
	 *       Have to write a separate parser for this, as well.    
	 *       
	 * @param p PackData   
	 * @param vkey String, the name to attach to the quantity
	 * @param flagSegs Vector<Vector<String>>, usual flagSegs
	 * 	contain the description of String to associate with vkey.
	 * 	Should be nonempty, check first character of first string 
	 * 	for '?' or '&', else store reconstitute(flagSegs).
	 * @return boolean: false on failure or error.
	 */
	public boolean putVariable(PackData p,String vkey,
			Vector<Vector<String>> flagSegs) {
		Vector<String> items=flagSegs.get(0);
		String theStuff=null;
		
		String firstStr=items.get(0);
		char c=firstStr.charAt(0);

		// this is form '{..cmd..}', so do a 'valueExecute', 
		if (c=='{') {
			theStuff=StringUtil.
					getBracesString(StringUtil.reconstitute(flagSegs));
			CallPacket cP=CommandStrParser.valueExecute(p,theStuff);
			if (cP==null || cP.error)
				return false;
			
			// expect double value or else int value
			try {
				if (cP.double_vec!=null)
					varModel.put(vkey,cP.double_vec.get(0).toString());
				else if (cP.int_vec!=null)
					varModel.put(vkey,cP.int_vec.get(0).toString());
				return true;
			} catch (Exception ex) {
				return false;
			}
		}
		
		// routine case
		if (c!='?' && c!='&') {
			theStuff=StringUtil.reconstitute(flagSegs);
		}
		
		// otherwise, need to find 'query' and remaining flagSegs
		else {
			String query=null;
			// have to worry about incidental white space after it
			if (firstStr.length()>1) {
				query=firstStr.substring(1);
				items.remove(0);
			}
			else { // remove white space
				try {
					items.remove(0); // toss '?' or '&'
					query=items.remove(0);
				} catch (Exception ex) {
					throw new ParserException("query seems to be messed up");
				}
			}
			if (items.size()==0)
				flagSegs.remove(0); // used all of first segment
			
			// now, get theStuff
			if (c=='?')
				theStuff=QueryParser.queryParse(p,query,flagSegs,false);
			else 
				theStuff=QueryParser.curValueParse(p,query,flagSegs);
		}
				
		// finally, store 'theStuff' under 'vkey'
		try {
			varModel.put(vkey,theStuff); // in case it's displacing something
			varModel.fireTableDataChanged();
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("failed to store variable");
			return false;
		}
		return true;
	}
	
	/**
	 * Remove a CirclePack variable.
	 * 
	 * @param variableName name of variable to remove
	 */
	public void removeVariable(String variableName) {
		varModel.remove(variableName);
		varModel.fireTableDataChanged();
	}
	
	/**
	 * Returns a model of CirclePack variable state. In GUI
	 * mode, tables may use this model to display names and values 
	 * of current variables.
	 * @return AbstractTableModel representing state of CirclePack variables.
	 */
	public AbstractTableModel getVarTableModel() {
		return varModel;
	}
	
	/**
	 * VariableControlTableModel encapsulates CirclePack variable 
	 * state information. External classes must get this instance from 
	 * 'VarControl'.
	 * 
	 * @author Alex Fawkes
	 */
	protected class VariableControlTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1308193035517680144L;
		
		@Override
		public int getColumnCount() {
			return 2; // One column each for names and values.
		}

		@Override
		public int getRowCount() {
			return variables.size(); // One row for each variable entry.
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (rowIndex<0 || rowIndex>=variables.size()) 
				return null; // Out of bounds.
			
			//TODO: Memoize for performance.
			// First column contains keys. Get an ordered list of keys from the 
			//    map and return the rowIndexth key.
			if (columnIndex == 0) 
				return new ArrayList<String>(variables.keySet()).get(rowIndex);
			// Second column contains values. Get an ordered list of values 
			//    from the map and return the rowIndexth value.
			else if (columnIndex == 1) 
				return new ArrayList<String>(variables.values()).get(rowIndex);
			else 
				return null; // Out of bounds.
		}
		
		@Override
        public String getColumnName(int columnIndex) {
            if (columnIndex == 0) return "Name";
            else if (columnIndex == 1) return "Value";
            else return null;
        }
		
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			if (rowIndex<1 || rowIndex>=variables.size()) 
				return false; // Out of bounds.
			else if (columnIndex < 0 || columnIndex > 1) 
				return false; // Out of bounds.
			else return true;
		}
		
		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			if (rowIndex < 0 || rowIndex >= variables.size()) 
				return; // Out of bounds.
			
			// First, check if the new entry is a string. We can only work
			//   with strings, so return if something else has been passed.
			String newEntry;
			if (aValue instanceof String) 
				newEntry = (String) aValue;
			else 
				return;
			newEntry = newEntry.trim(); // Remove leading/trailing whitespace.
			if (newEntry.isEmpty()) 
				return; // Ignore empty submissions.
		
			if (columnIndex == 0) {
				// We want to change a name. First, check if a variable 
				//     already has that name. If so, just return;
				if (variables.containsKey(newEntry)) 
					return;
				
				// Now, get the old name and old value from the hash map. 
				//   Remove the old variable entry, and resubmit it with 
				//   the new name and old value. We can't change the
				// variable name in place, since it is the key in the hash map.
				String oldName = 
						new ArrayList<String>(variables.keySet()).get(rowIndex);
				
				// is this a slider as well? 
				SliderPanel sp=sliderVariables.get(oldName);
				if (sp!=null) { // must be in GUImode
					sliderVariables.remove(oldName);
					sliderVariables.put(newEntry,sp);
					sp.varNameButton.setText(newEntry);
				}
				
				String oldValue = new ArrayList<String>(
						variables.values()).get(rowIndex);
				variables.remove(oldName);
				variables.put(newEntry, oldValue);

				// underlying data has changed so listeners may be updated.
				fireTableDataChanged();
				if (CPBase.GUImode>0)
					PackControl.packDataHover.sliderControlPanel.revalidate();
			} else if (columnIndex == 1) {
				// We want to change a value. Get the name of the variable 
				//   and resubmit it to the hash map with the new value.
				String oldName = new ArrayList<String>(variables.keySet()).
						get(rowIndex);
				SliderPanel sp=sliderVariables.get(oldName); // null is not GUImode
				
				// should we create a special type variable?
				String []slidestrs=null;
				if ((slidestrs=isSpecVariable(newEntry))!=null) {
					if (!slidestrs[0].equals("SLIDER") && sp!=null) 
						sliderVariables.remove(oldName);
					// SLIDER is the only special type so far
					else if (slidestrs[0].equals("SLIDER")) { 
						
						newEntry=new String("[SLIDER"+slidestrs[1]+"] "+slidestrs[2]);

						// should be in 'sliderVariables'; if not, create as new
					
						// if exists, may just want to change value without 
						//    messing with other parameters; syntax for user 
						//    is "[SLIDER] <double as string>".
						if (sp!=null && newEntry.startsWith("[SLIDER")) { 
							// plan to just change value
							double newdoub=0.0;
							try {
								newdoub=Double.parseDouble(newEntry.substring(8));
							} catch (Exception ex) {
								throw new DataException(
										"bad 'value' for slider '"+oldName+"'");
							}
							sp.resetValue(newdoub);
							return;
						}
					
						// else create in 'sliderVariables'; this will check for double
						PackControl.packDataHover.sliderControlPanel.
							putSlider(oldName,slidestrs[1],slidestrs[2]);
					}	
				}
				
				variables.put(oldName, newEntry);

				// Mark that underlying data has changed so listeners may be updated.
				fireTableDataChanged();
				if (CPBase.GUImode>0)
					PackControl.packDataHover.sliderControlPanel.revalidate();
			}
		}

		/**
		 * Get the table row in which variable 'key'
		 * @param key String, should be trimmed
		 * @return int row number
		 */
		public int getVarRow(String key) {
			int N=getRowCount();
			for (int j=1;j<N;j++) {
				if (key.equalsIgnoreCase((String)getValueAt(j,1)))
					return j;
			}
			return -1;
		}
		
		/**
		 * Returns the value string of the specified CirclePack variable;
		 * string rep of double if a slider variable
		 * 
		 * @param key the name of the CirclePack variable
		 * @return a <code>String</code> representation of the value of 
		 *     the CirclePack variable, or <code>null</code> if not found
		 */
		protected String get(String key) {
			String value=variables.get(key);
			if (value==null)
				throw new DataException("No variable named '"+key+"'");

			if (value.startsWith("[SLIDER]")) {
				SliderPanel slider=sliderVariables.get(key);
				if (slider==null) {
					CirclePack.cpb.errMsg("'"+key+"' missing among slider variables");
					return value; // send back full string
				}
				return slider.toString(); // this gives string rep of double
			}
			
			// else return full 'value'
			return value; 
		}
		
		/**
		 * Adds to or updates a CirclePack variable in the model. On
		 * creation, 'value' may contain specs (e.g., for sliders).
		 * 
		 * @param key String, name 
		 * @param value String, the value of the CirclePack variable to add
		 * @return String representation of previous value, or null if new 
		 */
		protected String put(String key, String value) {
			key = key.trim(); // Remove leading and trailing whitespace.
			if (value==null || value.length()==0)
				throw new DataException("missing 'value' for variable '"+key+"'");
			value = value.trim();
			
			// does variable already exist?
			String oldvalue=variables.get(key);
			
			// if doesn't exist 
			if (oldvalue==null) {
				try {
					
					// is special type (e.g. slider)
					String []specStuff=isSpecVariable(value);
					if (specStuff!=null) {
						
						// if it is (accidentally) in 'sliderVariables', adjust it
						SliderPanel sp=sliderVariables.get(key);
						if (sp!=null) { 
							sp.sliderPacket.adjustParameters(specStuff[1]);
							sp.adjustValue4Range();
						}
						// else create; this will check if value is a double
						else {
							PackControl.packDataHover.
								sliderControlPanel.putSlider(key,
										specStuff[1],specStuff[2]);
						}
						
						// also put in 'variables' (but w/o sliders 'spec' string)
						variables.put(key, new String(
								"["+specStuff[0]+"] "+specStuff[2]));
			            PackControl.varControl.variables.put(
			            		key,new String("[SLIDER] "+specStuff[2]));
			            varModel.fireTableDataChanged();
					}
					
					// else a regular variable
					else {
						variables.put(key, value);
					}
				} catch (Exception ex) {
					throw new DataException("bad specs or value' for '"+key+"'");
				}
			}
			// already exists
			else {
				
				// try sliders first
				try {
					
					// is special type (e.g. slider)
					String []specStuff=isSpecVariable(oldvalue);
					if (specStuff!=null) {
						// if it is in 'sliderVariables', adjust it
						SliderPanel sp=sliderVariables.get(key);
						if (sp!=null) { 
							sp.sliderPacket.adjustParameters(oldvalue);
							sp.adjustValue4Range();
							sp.resetValue(Double.parseDouble(value));
						}
						// else create; this will check if value is a double
						else {
							PackControl.packDataHover.
								sliderControlPanel.putSlider(key,
										specStuff[1],value);
						}
						
						// also put in 'variables' (but without sliders 'spec' string)
						variables.put(key, 
								new String("["+specStuff[0]+"] "+specStuff[2]));
			            PackControl.varControl.variables.put(
			            		key,new String("[SLIDER] "+value));
					}
					// else a regular variable
					else 
						variables.put(key, value);
				} catch (Exception ex) {
					throw new DataException("bad specs or value' for '"+key+"'");
				}
			}

			// The variables have changed (probably). Fire notice to listeners.
			// TODO: Calculate exactly what changed, and only update that 
			//       instead of updating everything.
			fireTableDataChanged();
			PackControl.packDataHover.sliderControlPanel.revalidate();
			
			return oldvalue;
		}
		
		/**
		 * Remove a CirclePack variable from the model.
		 * 
		 * @param key the name of the CirclePack variable to remove
		 * @return a <code>String</code> representation of the value 
		 *    removed by the key, or <code>null</code> if nothing removed
		 */
		protected String remove(String key) {
			String returnValue = variables.remove(key);
			
			// check to remove from slider list as well
			SliderPanel sp=sliderVariables.get(key);
			if (sp!=null) {
				sliderVariables.remove(key);
				PackControl.packDataHover.sliderControlPanel.removeSliderPanel(sp);
			}
					
			// The variables have changed (probably). Fire a notice to the listeners.
			// TODO: Calculate exactly what has changed, and only update that 
			//       instead of updating everything.
			fireTableDataChanged();
			PackControl.packDataHover.sliderControlPanel.validate();
			PackControl.packDataHover.sliderControlPanel.repaint();
			
			return returnValue;
		}
		
	} // end of VariableControlTableModel
	
	   
    /**
     * Given a variable string, determine if it specifies
     * a slider variable. The syntax (at this moment) is
     * "[SLIDER <string1>] <string2>", string1 being the slider
     * specification, string2 the rest. 
     * If not a slider, return null.  
     * @param str String, initial trimmed string
     * @return String[3]: null if not slider, else
     *   [0]="SLIDER", 
     *   [1]=slider specification string (empty or starting with a space), 
     *   [2]=rest of initial string.
     */
    public static String []isSpecVariable(String strbld) {
    	if (strbld==null || strbld.charAt(0)!='[')
    		return null;
    	int leftbs=1;
    	int rightbs=0;
    	int j=1;
    	int length=strbld.length();
    	while (rightbs<leftbs && j<length) {
    		if (strbld.charAt(j)==']')
    			rightbs++;
    		if (strbld.charAt(j)=='[')
    			leftbs++;
    		j++;
    	}
    	
    	// malformed? -- unmatched [ 
    	if (j==length && rightbs<leftbs)
    		return null;
    
    	String inbrackets=strbld.substring(1,j-1).trim();
    	String []results=new String[3];
    	
    	// is this a slider?
    	if (inbrackets.substring(0,6).toUpperCase().startsWith("SLIDER")) {
    		results[0]=new String("SLIDER");
    		String afterR=inbrackets.substring(6,inbrackets.length()).trim();
    		if (afterR.length()>0)
    			results[1]=new String(" "+afterR); // add a space
    		else
    			results[1]="";
    		results[2]=strbld.substring(j,strbld.length()).trim();
    		return results;
    	}
    	
    	// TODO: in future, may want other specifications in brackets
    	return null;
    }
    
}
