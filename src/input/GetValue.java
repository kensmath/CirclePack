package input;

import allMains.CPBase;
import complex.Complex;
import exceptions.VarException;
import util.StringUtil;

public class GetValue {
	
	/**
	 * Find the double value specified by a string. It may be a normal
	 * signed numerical value (included an integer) or a variable.
	 * @param str, trimmed String
	 * @return Double, null on error.
	 * @throws VarException 
	 */
	public static Double get1Double(String str) throws VarException {
		if (str==null) 
			throw new VarException("string was null");
		if (str.charAt(0)=='-' && StringUtil.isFlag(str))
			throw new VarException("string is a flag not a double");
		if (str.charAt(0)=='+') // this precludes a variable
			str=str.substring(1).trim();
		else if (str.charAt(0)=='_') {
			String val=CPBase.varControl.getValue(str.substring(1));
			if (val==null)
				throw new VarException("no variable with name '"+str.substring(1)+"'");
			return Double.valueOf(val);
		}
		
		return Double.valueOf(str);
	}
	
	public static Integer get1Integer(String str) throws VarException {
		if (str==null) 
			throw new VarException("string was null");
		if (str.charAt(0)=='-' && StringUtil.isFlag(str))
			throw new VarException("string is a flag not an integer");
		if (str.charAt(0)=='+') // this precludes a variable
			str=str.substring(1).trim();
		else if (str.charAt(0)=='_') {
			String val=CPBase.varControl.getValue(str.substring(1));
			if (val==null)
				throw new VarException("no variable with name '"+str.substring(1)+"'");
			return Integer.valueOf(val);
		}
		if (str.contains(".") || str.contains("e") || str.contains("E")) {
			throw new VarException("appears to be a double, not an integer");
		}
		
		return Integer.valueOf(str);
	}

	public static Complex get1Complex(String re,String im) throws VarException {
		Double x=null;
		Double y=null;
		if (re==null) 
			throw new VarException("real part was null");
		
		// flag, by mistake?
		if (re.charAt(0)=='-' && StringUtil.isFlag(re))
			throw new VarException("first string is a flag not an double");
		
		// variable name? might actually give both re/im parts
		if (re.charAt(0)=='_') {
			String val=CPBase.varControl.getValue(re.substring(1));
			if (val==null)
				throw new VarException("no variable with name '"+re.substring(1)+"'");
			
			// seems that the variable value contains both real and complex parts
			if (val.contains(" ") || val.contains("i") || val.contains("I")) {
				return Complex.string2Complex(val);
			}
			else
				x=Double.valueOf(val);
		}
		else
			x=Double.valueOf(re);
		
		// variable name?
		if (im.charAt(0)=='_') {
			String val=CPBase.varControl.getValue(re.substring(1));
			if (val==null)
				throw new VarException("no variable with name '"+re.substring(1)+"'");
			y=Double.valueOf(val);
		}
		else
			y=Double.valueOf(im);

		return new Complex(x,y);
	}
	
}
