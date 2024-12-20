package variables;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import util.StringUtil;

/**
 * For processing and storage of information on CirclePack variables 
 * of the "slider" type. This goes in the "Pack Info -> Variables" tab
 * in the lower panel. They are specified, when setting variables, by
 * starting with "[SLIDER {optional}]" and ending with their value,
 * which must be a double. See 'SliderControlPanel' and 'SliderPanel'.
 * @author kstephe2
 *
 */
public class SliderPacket {
	
	String varName;		// for reference; maintained in 'SliderPanel'
	double minValue;	// minimum in of value range
	double maxValue;	// maximum of value range
	String function;    // function to apply; default "z*pi"
	boolean functionActive;  // whether or not to apply the function
	String command;		// command to apply, generally named command '[.]'
	boolean commandActive;  // whether or not to apply the command
	
	// Constructor
	public SliderPacket(String vName,String initStr) {
		varName=vName.trim();
		minValue=0.0;
		maxValue=1.0;
		function="j*pi";
		functionActive=false;
		command="";
		commandActive=false;
		adjustParameters(initStr);
	}

	/**
	 * Processing the 'specification' string that came as "[SLIDER {spec}]".
	 * (The 'value' is specified separately, maintained by 'SliderPanel'.)
	 * @param stString String, spec string
	 */
	public void adjustParameters(String specStr) {
		
		// process 'specs' string: -m {min} -M {max} -ftn {ftn} -Pi *pi -cmd {str}
		Vector<Vector<String>> specSegs=StringUtil.flagSeg(specStr);
		Iterator<Vector<String>> segs=specSegs.iterator();
		Vector<String> items=null;
		while (segs.hasNext()) {
			try {
				items=segs.next();
				String str=items.remove(0);
				if (str.equals("-m")) { // minimum
					minValue=Double.parseDouble(items.get(0));
				}
				else if (str.equals("-M")) { // max
					maxValue=Double.parseDouble(items.get(0));
				}
				else if (str.equals("-cmd")) { // command (only if nonempty)
					commandActive=true;
					// can turn off by setting without string
					if (items.size()>0 && items.get(0).length()>0) {
						command=StringUtil.reconItem(items);
					}
					else
						commandActive=false;
				}
				else if (str.equals("-ftn")) { // function
					functionActive=true;
					
					// may leave 'function' at default 'z*pi'
					if (items.size()>0)
						function=StringUtil.reconItem(items);
				}
				else if (str.equals("-Pi")) { // set function to z*pi
					functionActive=true;
					function="z*pi";
				}
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("slider spec problem with '"+items.get(0));
			}
			
		} // end of while to get specs

	}

	/** 
	 * name maintained in 'SliderPanel' class
	 * @return String
	 */
	public String getVarName() {
		return new String(varName);
	}

	public double getMin() {
		return minValue;
	}
	
	public void setMin(double min) {
		minValue=min;
	}

	public double getMax() {
		return maxValue;
	}
	
	public void setMax(double max) {
		maxValue=max;
	}
	
	public boolean getFunctionActive() {
		return functionActive;
	}
	
	public void setFunctionActive(boolean fA) {
		functionActive=fA;
	}

	public void setCommandActive(boolean cA) {
		commandActive=cA;
	}
	
	public boolean getCommandActive() {
		return commandActive;
	}
	
	public String getFunction() {
		return new String(function).trim();
	}
	
	public void setFunction(String ftn) {
		function=new String(ftn).trim();
	}

	public String getCommand() {
		return new String(command).trim();
	}
	
	public void setCommand(String cmd) {
		command=new String(cmd).trim();
	}
	
}
