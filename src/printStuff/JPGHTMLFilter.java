package printStuff;

import input.CPFileManager;

import java.io.File;
import javax.swing.filechooser.FileFilter;


//File filter for JFileChooser, set to JPG and HTML.
public class JPGHTMLFilter extends FileFilter {
	public boolean accept(File file) {
		//Display directories.
		if (file.isDirectory()) {
			return true;
		}
		
		//Display JPGs and HTML files.
		String extension = CPFileManager.getFileExt(file);
        if (extension != null) {
        	if (extension.equalsIgnoreCase("jpg") ||
            		extension.equalsIgnoreCase("jpeg") ||
            		extension.equalsIgnoreCase("html") ||
            		extension.equalsIgnoreCase("htm"))
        	{
                return true;
            }
        }
		
        //Don't display anything else.
		return false;
	}
	
    public String getDescription() {
        return "JPGs and HTML";
    }
}
