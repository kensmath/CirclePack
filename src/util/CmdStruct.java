package util;

/**
 * Class to organize info on 'PackExtender' commands
 * @author kens
 *
 */
public class CmdStruct {
	public String xCmd;
	public String xFlags;
	public String xHint;
	public String xDescription;
	
	// Constructor
	public CmdStruct(String name,String flags,String hint,String description) {
		xCmd=new String(name);
		xFlags=flags;
		xHint=hint;
		xDescription=description;
	}
	
	public String getxCmd() {
		return xCmd;
	}
	
	public String getxFlags() {
		return xFlags;
	}
	
	public String getxDescrip() {
		return xDescription;
	}
	
}
