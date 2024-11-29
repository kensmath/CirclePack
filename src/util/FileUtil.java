package util;

import java.io.File;
import java.io.IOException;

import exceptions.InOutException;

/**
 * For utility routines associated with files
 * and directories.
 */
public class FileUtil {

	/**
	 * Return the path to a local file when given
	 * a url starting with 'file'.
	 * @param url String
	 * @return String name for file
	 */
	public static String clearFileProtocol(String url) {
		String filepath=url;
		if (url.startsWith("file")) {
			filepath = url.substring(4);
			while (filepath.startsWith("/") || url.startsWith(":")) 
				filepath = filepath.substring(1).trim();
			if (filepath==null || filepath.length()==0)
				return null;
			return filepath;
		}
		return new String(url);
	}
	
	/**
	 * Often need to strip 'file:\' or 'http:\\' or
	 * 'https:\\' in directory name before it can
	 * be used
	 * @param directory File
	 * @return File, possibly null
	 */
	public static File clearPreName(File directory) {
		String str=directory.getPath();
		if (str.startsWith("file:\\")) 
			str=str.substring(6);
		else if (str.startsWith("http:\\"))
			str=str.substring(7);
		else if (str.startsWith("https:\\"))
			str=str.substring(8);
		
		File newfile;
		try {
			newfile=new File(str);
		} catch(Exception iox) {
			throw new InOutException(
				"problem clearing file prename. "+iox.getMessage());
		}
		return newfile;
	}

	/**
	 * Return list of files in the given directory.
	 */
	public static File[] getFileList(File directory) {
		File nfile=clearPreName(directory);
		return nfile.listFiles();
	}
	
	
	/**
	 * Find characters before and after the last '.' 
	 * in 'filename'. Main name and, E.g., 'html', 'jpg', etc.
	 * @param filename String
	 * @return String[2]: {main string, ext (possibly null)}
	 */
	public static String[] getFilenameExtension(String filename) {
		int n=-1;
		int nn=0;
		while ((nn=filename.indexOf('.',(n+1)))>n) {
			n=nn;
		}
		String[] ans=new String[2];
		  
		// no '.' 
		if (n<0) {
			ans[0]=filename;
			ans[1]=null;
			return ans;
		}
		if (n==filename.length()-1) {
			ans[0]=filename.substring(0,filename.length());
			ans[1]=null;
			return ans;
		}
		ans[0]=filename.substring(0,n);
		ans[1]=filename.substring(n+1);
		return ans;
	}

}
