package input;

import java.awt.Component;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;


/**
 * Takes care of all dialogs for file (and eventually, web) in/output.
 * Should rewrite so these also keep track of the directories when they
 * are changed.
 * @author kens
 *
 */
public class FileDialogs {

	public static final int SCRIPT=1; // script file load/writes
	public static final int FILE=2; // for CPdata section:
	public static final int TOOLS=3; // for myTool files
	public static final int POSTSCRIPT =4;
	public static final int JPG =5;
	public static final int ADD2SCRIPT=6; 
	public static final int EXTENDER=7; // to find 'PackExtender' classes
	public static final int ABOUT=8; // for saving the 'AboutImage'
	public static final int DATAFILE=9; // 
	public static final int SUBRULEFILE=10; // subdivision rules file, a la Floyd
	
	/**
	 * Open a dialog
	 * @param type int: 1=SCRIPT, 2=FILE, 3=TOOLS, 4=POSTSCRIPT, 5=JPG, 7=EXTENDER, 8=ABOUT
	 * @param swDir boolean: true, then reset relevant directory on success
	 * @return File
	 */
	public static File loadDialog(int type,boolean swDir) {
		return loadDialog(type,swDir,null);
	}
	
	/**
	 * Open a dialog
	 * @param type int: 1=SCRIPT, 2=FILE, 3=TOOLS, 4=POSTSCRIPT, 5=JPG, 7=EXTENDER, 8=ABOUT
	 *        9=DATAFILE, 10=SUBRULEFILE
	 * @param swDir boolean: true, then reset relevant directory on success
	 * @param actStr String, dialog message string  
	 * @return File
	 */
	public static File loadDialog(int type,boolean swDir,String actStr) {
		int result;
		JFileChooser dbox = new JFileChooser();
		File f;
		String actionStr=actStr;
		
		switch (type) {
			case SCRIPT: {
				if (actStr==null) actionStr="Read script file";
				dbox.setCurrentDirectory(CPFileManager.ScriptDirectory);
				dbox.setFileFilter(new ScriptFilter());
				result = dbox.showDialog(
					(Component) PackControl.scriptBar,actionStr);
				if (result == JFileChooser.APPROVE_OPTION) {
					f = dbox.getSelectedFile();
					if (swDir)
						CPFileManager.ScriptDirectory = dbox.getCurrentDirectory();
					return f;
				}
				return null;
			}
			case ADD2SCRIPT: {
				actionStr="Append to script";
			    dbox.setCurrentDirectory(new File(System.getProperty("java.io.tmpdir")));
				dbox.setFileFilter(new ScriptFilter());
				result = dbox.showDialog(
					(Component) PackControl.scriptBar,actionStr);
				if (result == JFileChooser.APPROVE_OPTION) {
					f = dbox.getSelectedFile();
					return f;
				}
				return null;
			}
			case FILE: {
				if (actStr==null) actionStr="Read packing file";
				dbox.setCurrentDirectory(CPFileManager.PackingDirectory);
				dbox.setFileFilter(new PackingFilter());
				result = dbox.showDialog(
						(Component) PackControl.activeFrame,actionStr);
				if (result == JFileChooser.APPROVE_OPTION) {
					f = dbox.getSelectedFile();
					if (swDir)
						CPFileManager.PackingDirectory = dbox.getCurrentDirectory();
					return f;
				}
				return null;
			}
			case TOOLS: {
				if (actStr==null) actionStr="Read a tool file";
				dbox.setCurrentDirectory(CPFileManager.ToolDirectory);
				dbox.setFileFilter(new ToolFilter());
				result = dbox.showDialog(
					(Component) PackControl.userHandler.toolBar,actionStr);
				if (result == JFileChooser.APPROVE_OPTION) {
					f = dbox.getSelectedFile();
					if (swDir)
						CPFileManager.ToolDirectory = dbox.getCurrentDirectory();
					return f;
				}
				return null;
			}
			case EXTENDER: {
				if (actStr==null) actionStr="Find 'PackExtender' .class file";
				dbox.setCurrentDirectory(CPFileManager.ExtenderDirectory);
				dbox.setFileFilter(new ExtenderFilter());
				result = dbox.showDialog(
					(Component) PackControl.userHandler.toolBar,actionStr);
				if (result == JFileChooser.APPROVE_OPTION) {
					f = dbox.getSelectedFile();
					if (swDir)
						CPFileManager.ExtenderDirectory = dbox.getCurrentDirectory();
					return f;
				}
				return null;
			}			
			case SUBRULEFILE: {
				if (actStr==null) actionStr="Find subdivision rule file";
				dbox.setCurrentDirectory(CPFileManager.PackingDirectory);
				dbox.setFileFilter(new SubRulesFilter());
				result = dbox.showDialog(
					(Component) PackControl.userHandler.toolBar,actionStr);
				if (result == JFileChooser.APPROVE_OPTION) {
					f = dbox.getSelectedFile();
					if (swDir)
						CPFileManager.PackingDirectory = dbox.getCurrentDirectory();
					return f;
				}
				return null;
			}			
			} // end of switch
		return null;
	}
	
	public static File saveDialog(int type,boolean swDir) {
		return saveDialog(type,swDir,null);
	}
	
	public static File saveDialog(int type,boolean swDir,String actStr) {
		JFileChooser dbox = new JFileChooser();
		int result;
		File targetFile;
		String actionStr=actStr;
				
		switch (type) {
		case SCRIPT: {
			if (actStr==null) actionStr="Save current script";
			dbox.setCurrentDirectory(CPFileManager.ScriptDirectory);
			dbox.setSelectedFile(new File(CPFileManager.ScriptDirectory+File.separator+
					PackControl.scriptManager.scriptName));
			dbox.setFileFilter(new ScriptFilter());
			result = dbox.showDialog(
					(Component) PackControl.activeFrame,actionStr);
			if (result == JFileChooser.APPROVE_OPTION) {
				targetFile = dbox.getSelectedFile();
				if (swDir)
					CPFileManager.ScriptDirectory=dbox.getCurrentDirectory();
				return targetFile;
			}
			return null;
		}
		case JPG: {
			if (actStr==null) actionStr="Save JPG File";
			dbox.setCurrentDirectory(new File(CPFileManager.PostScriptDirectory.getPath()));
			dbox.setSelectedFile(new File(CPFileManager.PostScriptDirectory+File.separator+
				"generic.jpg")); 
			dbox.setFileFilter(new JPGFilter());
			result = dbox.showDialog(
					(Component) PackControl.activeFrame,actionStr);
			if (result == JFileChooser.APPROVE_OPTION) {
				targetFile = dbox.getSelectedFile();
				if (swDir)
					CPFileManager.PostScriptDirectory=dbox.getCurrentDirectory();
				return targetFile;
			}
			return null;
		}
		case ABOUT: {
			if (actStr==null) actionStr="Save AboutImage";
			dbox.setCurrentDirectory(CPFileManager.PostScriptDirectory);
			
			// get the script name (without .xmd)
			String sName=null;
			int k=PackControl.scriptManager.scriptName.indexOf(".");
			if (k<0) k=PackControl.scriptManager.scriptName.length();
			sName=PackControl.scriptManager.scriptName.substring(0,k);
			dbox.setSelectedFile(new File(CPFileManager.PackingDirectory+File.separator+
					sName+".jpg")); 
			dbox.setFileFilter(new JPGFilter());
			result = dbox.showDialog(
					(Component) PackControl.activeFrame,actionStr);
			if (result == JFileChooser.APPROVE_OPTION) {
				targetFile = dbox.getSelectedFile();
				if (swDir)
					CPFileManager.PostScriptDirectory=dbox.getCurrentDirectory();
				return targetFile;
			}
			return null;
		}
		case POSTSCRIPT: {
			if (actStr==null) actionStr="Save PostScript file";
			dbox.setCurrentDirectory(CPFileManager.PostScriptDirectory);
			dbox.setSelectedFile(new File(CPFileManager.PackingDirectory+File.separator+
					CirclePack.cpb.getActivePackData().getName())); 
			dbox.setFileFilter(new PostScriptFilter());
			result = dbox.showDialog(
					(Component) PackControl.activeFrame,actionStr);
			if (result == JFileChooser.APPROVE_OPTION) {
				targetFile = dbox.getSelectedFile();
				if (swDir)
					CPFileManager.PostScriptDirectory=dbox.getCurrentDirectory();
				return targetFile;
			}
			return null;
		}
		case FILE: {
			if (actStr==null) actionStr="Save the packing";
			dbox.setCurrentDirectory(CPFileManager.PackingDirectory);
			dbox.setSelectedFile(new File(CPFileManager.PackingDirectory+File.separator+
					CPBase.pack[CirclePack.cpb.getActivePackNum()].getPackData().fileName));
			dbox.setFileFilter(new PackingFilter());
			result = dbox.showDialog(
					(Component) PackControl.activeFrame,actionStr);
			if (result == JFileChooser.APPROVE_OPTION) {
				targetFile = dbox.getSelectedFile();
				if (swDir)
					CPFileManager.PackingDirectory = dbox.getCurrentDirectory();
				return targetFile;
			}
			return null;
		}
		case TOOLS: {
			if (actStr==null) actionStr="Save a tool file";
			dbox.setCurrentDirectory(CPFileManager.ToolDirectory);
			dbox.setFileFilter(new ToolFilter());
			result = dbox.showDialog(
				(Component) PackControl.userHandler.toolBar,actionStr);
			if (result == JFileChooser.APPROVE_OPTION) {
				targetFile = dbox.getSelectedFile();
				if (swDir)
					CPFileManager.ToolDirectory = dbox.getCurrentDirectory();
				return targetFile;
			}
			return null;
		}
		} // end of switch
		return null;
	}

	// Various filters for reading/writing

	/**
	 * CirclePack script write file filter
	 */
	public static class ScriptFilter extends FileFilter {
		public boolean accept(File f) {
			return f.getName().toLowerCase().endsWith(".cmd")
					|| f.getName().toLowerCase().endsWith(".xmd")
					|| f.isDirectory();
		}
		public String getDescription() {
			return "CirclePack Script files";
		}
	}


	/**
	 * Subdivision rules file, a la Bill Floyd's software
	 */
	public static class SubRulesFilter extends FileFilter {
		public boolean accept(File f) {
			return f.getName().toLowerCase().endsWith(".r")
					|| f.isDirectory();
		}
		public String getDescription() {
			return "Subdivision Rules .r files";
		}
	}


	/**
	 * PostScript write file filter
	 */
	public static class PostScriptFilter extends FileFilter{
	    public boolean accept(File f){
	      return f.getName().toLowerCase().endsWith(".ps") || f.isDirectory();
	    }

	    public String getDescription(){
	      return "PostScript files *.ps";
	    }
	}
	
	/**
	 * jpg write file filter
	 */
	public static class JPGFilter extends FileFilter{
	    public boolean accept(File f){
	      return f.getName().toLowerCase().endsWith(".jpg") || f.isDirectory();
	    }

	    public String getDescription(){
	      return "JPG files *.jpg";
	    }
	}
	
	/**
	 * Packing read file filter
	 */
	public static class PackingFilter extends FileFilter{
	    public boolean accept(File f){
	      return f.getName().toLowerCase().endsWith(".p") ||
	         f.getName().toLowerCase().endsWith(".q") ||
	         f.getName().toLowerCase().endsWith(".off") ||
	         f.getName().toLowerCase().endsWith(".pl") ||
	         f.isDirectory();
	    }

	    public String getDescription(){
	      return "Packing files *.p, *.q, *.pl, *.off";
	    }
	}
	
	/**
	 * Packing read file filter
	 */
	public static class DataFileFilter extends FileFilter{
	    public boolean accept(File f){
	      return true;
	    }

	    public String getDescription(){
	      return "all files";
	    }
	}
	
	/** 
	 * File filter for ToolBox files (i.e., MyTool's)
	 */
	public static class ToolFilter extends FileFilter{
		public boolean accept(File f){
			return f.getName().toLowerCase().endsWith(".myt") || f.isDirectory();
	    }

	    public String getDescription(){
	      return "CirclePack ToolBox's, *.myt";
	    }
	}
	
	/** 
	 * File filter for 'PackExtender' .class files
	 */
	public static class ExtenderFilter extends FileFilter{
		public boolean accept(File f){
			return f.getName().toLowerCase().endsWith(".class") || f.isDirectory();
	    }

	    public String getDescription(){
	      return "'PackExtender' *.class";
	    }
	}
}
