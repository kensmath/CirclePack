package script;

import input.CPFileManager;

import java.io.File;

/** 
 * Class containing info on files included in CPdata portion of scripts.
 * A linked list of these is maintained as 'ScriptManager.includeFiles'.
 * @author kens
 */
public class IncludedFile {
	
	// types of data files so far; see "format" info in one of help files.
	public static int PACKING=1;
	public static int PATH=2;
	public static int XYZ=3;
	public static int CMDS=4;
	public static int IMAGE=5; // file extension should tell the type
	public static int ABOUT_IMAGE=6; // reserved for 'AboutImage' (extension tells type)
	public static int RAW=10; // default

	public int dataType; 
	public String origName; // does NOT contain the 'id' prefix
	public File tmpFile; // actual file (can get the actual file name from here)
	
	// Constructor
	public IncludedFile(int type,String orig_name,File tmp_file) {
		dataType=type;
		origName=orig_name;
		tmpFile=tmp_file;
	}
	
	/**
	 * Return 'dataType' based on tmpFile extension; careful, no binary
	 * files can yet be included in a CirclePack script file. I pass the
	 * original name just to catch 'AboutImage' (it is in file name, but
	 * complicated by the pid).
	 * @param name String, original name
	 * @param file (File is created/checked by calling routine)
	 * @return int, IncludedFile datatype
	 */
	public static int setDataType(String name,File file) {
		int datatype=IncludedFile.RAW; // default
		String ext=CPFileManager.getFileExt(file);
		if (ext!=null) {
			boolean gotit=false;
			if (ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg") ||
					ext.equalsIgnoreCase("png")) {
				// TODO: how to handle binary files?
//				throw new DataException("binary files not yet allowed");
				if (name.startsWith("AboutImage"))
					datatype=IncludedFile.ABOUT_IMAGE;
				else datatype=IncludedFile.IMAGE;
				gotit=true;
			}
			if (!gotit) {
				char li=ext.charAt(0);
				gotit=true;
				if (li=='p' || li=='q' || li=='P' || li=='Q') datatype=IncludedFile.PACKING;
				else if (li=='g' || li=='G') datatype=IncludedFile.PATH;
				else if (li=='c' || li=='C') datatype=IncludedFile.CMDS;
				else if (li=='x' || li=='X') datatype=IncludedFile.XYZ;
				else gotit=false; // no hits, leave it as 'raw'
			}
		}
		return datatype;
	}
	
	/**
	 * Return string describing the type of this data file; e.g. for
	 * attaching tool tip to 'fileButton'
	 * @return
	 */
	public String getTypeString() {
		switch(dataType) {
		case 1:	return "PACKING data";
		case 2: return "PATH data";
		case 3: return "XYZ data";
		case 4: return "CMDS: string of commands";
		case 5: return "IMAGE data (jpg, png)";
		case 6: return "AboutImage (jpg, png)";
		default: return "RAW data, arbitrary ASCII";
		}
	}
	
}

