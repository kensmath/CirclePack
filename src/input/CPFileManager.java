package input;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.Vector;

import math.Point3D;
import packing.PackData;
import util.StringUtil;
import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.InOutException;
import exceptions.ParserException;

/**
 * This manager is created on startup for managing directory paths, 
 * opening BufferedReader/Writers, etc. 
 * Reading/writing routines themselves are generally in appropriate 
 * classes.
 * 
 * Have some static file routines, such as copyFile.
 * 
 * In searching for resources (e.g., 'mytool' files), we generally
 * have to search the home directory, since currentDirectory via the
 * jar may be variable.
 */
public class CPFileManager {

	// Directories to be maintained
	// user's home directory (meaning of '~/').
	public static File HomeDirectory = new File(System.getProperty("user.home"));

	// Additional directory preferences set in 'CPPreferences' stored in
	//   Home/myCirclePack/cpprefrc
	public static File CurrentDirectory=new File(System.getProperty("user.home"));
	
	  // starts same as HomeDirectory, but user can change
	public static File ScriptDirectory; // where scripts are kept
	public static File PackingDirectory; // where packings are kept
	public static File ImageDirectory; // for output files, like 'jpg's
	public static File ToolDirectory = new File(HomeDirectory+
			File.separator+"Resources"+File.separator+"mytools/"); 
	public static File ExtenderDirectory=new File(System.getProperty("user.home"));
	  // starts same as HomeDirectory, but user can change
	public static URL ToolURL=null;
	
	// Constructor
	public CPFileManager() {
//		System.out.println("'HomeDirectory' is "+HomeDirectory+";  "+
//				"'UserDirectory' is "+UserDirectory);
		if (HomeDirectory==null)
			HomeDirectory=CurrentDirectory;
	}
	
	public void setCurrentDirectory(String directory) {
		directory=directory.trim();
		if (directory.length()==0) {
			try {
				CurrentDirectory=new File(HomeDirectory.getCanonicalPath());
			} catch(Exception ex) {
				CurrentDirectory=new File(System.getProperty("java.io.tmpdir"));
			}
		}
		else if (directory.startsWith("~")) {
			String pl=null;
			if (directory.length()>1)
				pl=directory.substring(1);
			else pl="";
			try {
				directory=new String(HomeDirectory.getCanonicalPath()+
						File.separator+pl);
			} catch(Exception ex) {
				CurrentDirectory=new File(System.getProperty("java.io.tmpdir"));
			} 
		}
		CurrentDirectory=new File(directory);
	}

	/**
	   * (See 'ckTrailingFileName' also)
	   * Convention on files names at end of command options should
	   * be '-f' for file, '-a' append (for writing only), and/or 's'
	   * for script, plus '<filename>' (possibly with directory). 
	   * But other conventions are still in use.
	   * 
	   * This method sees if last segment is -[fs] flag; if so,
	   * it finds the filename and then removes this whole segment 
	   * from 'fseg'.
	   * 
	   * If there is no -[fs] flag, take the strings as filename
	   * and remove it from its segment. CAUTION: if there is no
	   * filename, this might mistakenly take the last entry anyway.
	   *  
	   * @param fseg, Vector<Vector<String>>
	   * @return BufferedReader
	   * throw exceptions in case of error.
	   */
	  public static BufferedReader openReadTail(Vector<Vector<String>> fseg) {
		  StringBuilder strbuf=new StringBuilder("");
		  int code=trailingFile(fseg,strbuf);
		  if (code==0 || strbuf.length()==0)
			  throw new InOutException("No filename found");
		  if ((code & 02)==02)
			  throw new InOutException("'append' option inappropriate for reading");
		  boolean script_flag=false;
		  if ((code & 04)==04)
			  script_flag=true;

		  // get returned string as a 'File'.
		  File file=new File(strbuf.toString());

		  // get the dir/name
		  String dir=file.getParent();
		  String filename=file.getName();
		  File theFile=null;
		  if (script_flag) {
			  if (!PackControl.scriptManager.isScriptLoaded() || (theFile=
				  (File)PackControl.scriptManager.getTrueIncluded(filename))==null) {
				  throw new InOutException("script doesn't contain '"+filename+"'");
			  }
		  }
		  else {
			  if (dir==null)
				  dir=CurrentDirectory.toString();
			  theFile=new File(dir,filename);
		  }
		  
		  // should have 'theFile' now
		  BufferedReader fp=null;
		  try {
			  fp = new BufferedReader(new FileReader(theFile));
		  } catch(FileNotFoundException ex) {
			  throw new InOutException("Failed to open '"+theFile.toString()+"'");
		  }
		  return fp;
	  }
	  
	  /**
	   * (See 'StringUtil.ckTrailingFileName' also)
	   * Convention on files names at end of command options should
	   * be '-f' for file, '-a' append (for writing only), and/or 's'
	   * for script, plus '<filename>' (possibly with directory). 
	   * 
	   * If filename contains bracketed '[<string>]' then it tries to 
	   * interpret <string> as a double and converts to an integer, 
	   * else takes it as a literal. E.g. "pack[_n].p" will try to 
	   * interpret the string for variable 'n' as an integer; so if 
	   * n:=3.0, then would get "pack3.p". On the other hand, 
	   * "pack[bob].p" would yield the name "packbob.p".
	   * 
	   * Older syntax conventions (e.g., without flags) are still in use.
	   * This method checks if last segment is -[fas] flag; if so,
	   * it finds the filename and then removes this whole segment 
	   * from 'fseg'. Replaces leading '~' by home directory.
	   * Can have just '-s', in which case filename "" is returned.
	   * 
	   * If the last segment has no flag or an inappropriate flag, 
	   * then everything (after the flag, if there is one) is 
	   * consumed and taken as a filename; if there's an 
	   * inappropriate flag, it is left as a segment on its own. 
	   * (There can be a problem if there is in fact no file name, 
	   * as the last string is consumed.)
	   * segment is 
	   * 
	   * Throw exceptions in case of error.
	   * 
	   * @param fseg Vector<Vector<String>>
	   * @param strbld StringBuilder: instance = "" (created 
	   *        in parent) to hold the file name (may or may not have 
	   *        a directory).
	   * @return int: 1-bit=read/overwrite; 2-bit=append (writing only); 
	   *        3-bit=from/to script. remove the final trailing string.
	   *        0 on error
	   */
	  public static int trailingFile(Vector<Vector<String>> fseg,StringBuilder strbld) {
		  if (strbld==null || strbld.length()>0 || fseg==null || fseg.size()==0) 
			  return 0;
		  
		  int code=01;
		  
		  // get the last flag sequence in 'fsa'
		  Vector<String> tems=(Vector<String>)fseg.remove(fseg.size()-1);
		  if (tems.size()==0) 
			  return 0;
		  String flag=(String)tems.get(0); 
		  
		  // check for flag(s); if unexpected, return null
		  if (StringUtil.isFlag(flag)) {
			  tems.remove(0); // remove the flag string
			  // inappropriate flag?
			  if (!flag.startsWith("-s") && !flag.startsWith("-f") && !flag.startsWith("-a")) {
				  Vector<String> vecstr=new Vector<String>(1);
				  vecstr.add(flag); // put this flag back in as the last segment
			  }
			  else {
				  // script file?
				  if (flag.contains("s"))  
					  code |= 04;
				  if (flag.contains("a")) 
					  code |=02;
			  }
			  
			  // rest is interpreted as the name, possibly with directory
			  if (tems.size()>0) {
				  strbld.append(StringUtil.reconItem(tems));
			  }
			  else if ((code & 04)==04) // no name, but want 'script' mode set
				  return code;
		  }
		  // no flag? take as the name
		  else 
			  strbld.append(StringUtil.reconItem(tems));

		  // name may include '~' and/or directory
		  if (strbld.length()==0)  
			  return 0;
		  if (strbld.charAt(0)=='~') {
			  strbld.deleteCharAt(0);
			  strbld.insert(0,HomeDirectory);
		  }
		  
		  // name may include "[_n]" structure; find/insert number or literal
		  int k=-1;
		  if ((k=strbld.indexOf("["))>=0 && (k+1)<strbld.length()) {
			  int kk=-1;
			  Double nbr=null;
			  if ((kk=strbld.indexOf("]",k+1))>0) {
				  String vstr=strbld.substring(k+1,kk);
				  if (vstr!=null && vstr.length()>0) {
					  try {
						  nbr=Double.parseDouble(vstr);
					  } catch (Exception ex) {
						  // attempt failed, just return what we have
						  strbld.replace(k,kk+1,vstr);
						  return code; 
					  }
					  Integer intnum=Integer.valueOf((int)Math.floor(nbr.doubleValue()));
					  strbld.replace(k,kk+1,intnum.toString());
				  }
			  }
		  }
		  
		  return code;
	  }

	  /**
	   * For getting filename at end of user string -- where we don't
	   * have 'File' and 'URL' functionality.
	   * Problems with file names we hope to fix:
	   *   * file separator character: not only do windows and linux
	   *     differ, but some files are in the jar already with one
	   *     or the other character.
	   *   * file names might have blanks, possibly escaped,"\ ".
	   *   * I don't know if the character set (e.g., ascii) will 
	   *     ever cause a problem, so I ignore that for now.
	   * @param strbuf, StringBuilder (should be 'trimmed' already)
	   * @return String, null on error or if empty
	   */
	  public static String getFileName(StringBuilder strbuf) {
		  int length=strbuf.length();
		  if (length<=0)
			  return null;
		  
		  // work backwards to find important characters
		  int spot=length;
		  boolean hit=false; // set to true when some legitimate char is found
		  while (spot>0) {
			  spot--;
			  char c=strbuf.charAt(spot);
			  switch(c) {
			  case ' ': {
				  if (spot==0) {
					  if (length>0) // should have been trimmed 
						  return strbuf.substring(1);
					  else
						  return null;
				  }
				  if (strbuf.charAt(spot-1)=='\\')
					  spot--; // continue, this was escaped space
				  break;
			  }
			  case '\\': { // should be separation character
				  if (hit && spot<length-1)
					  return strbuf.substring(spot+1);
				  return null; // shouldn't be '\' at end
			  }
			  case '/': { // should be separation character
				  if (hit && spot<length-1)
					  return strbuf.substring(spot+1);
				  return null; // shouldn't be '\' at end
			  }
			  default: {
				  hit=true;
			  }
			  } // end of switch
		  } // end of while
		  
		  if (hit) 
			  return strbuf.substring(0).trim();
		  return null; // no legitimate character found
	  }

	/**
	   * Return the file extension
	   * @param File file
	   * @return String or null
	   */
	  public static String getFileExt(File file) {
		  String fileName;
		  String extension;
		  int dotIndex;
		
		  fileName = file.getName();
		  dotIndex = fileName.lastIndexOf(".");
		  if (dotIndex == -1) return null;
		  extension = fileName.substring(dotIndex+1, fileName.length());
		  return extension;
	  }
	  
	/**
	 * Given a string purporting to be a filename, return the
	 * directory (see if it's 'home') and the filename.
	 * @param str
	 * @return String[2]: return null on error
	 * * String[0]=directory (may be null)
	 * * String[1]=filename
	 */
	public static String []getFileDirNames(String str) {
		StringBuilder strbuf=new StringBuilder(str.trim());
		if (strbuf.charAt(0)=='~') {
			strbuf.deleteCharAt(0);
			strbuf.insert(0,HomeDirectory);
		}
		File file=new File(strbuf.toString());
		String []names=new String[2];
		names[0]=file.getParent();
		names[1]=file.getName();
		if (names[1]==null || names[1].length()==0)
			return null;
		return names;
	}

	/**
	 * Read various types of data from files or the script. Currently
	 * only 'xyz' data reading into 'p.xyzpoint' is implemented.
	 * @param p, packing
	 * @param filename; assume 'PackingDirectory' or tmp directory (for script files)
	 * @param script_flag, true implies try to read from script first
	 * @param mode, type of data: 1 ==> 'xyz' data.
	 * @return int count of successes
	 */
	public static int readDataFile(PackData p,String filename,boolean script_flag,int mode) {
		BufferedReader fp=openReadFP(filename,script_flag);
		if (fp==null) return 0;
		String line;
		  
		// ====================== various modes =============================
		
		if (mode==1) {  // read xyz location data for all vertices
			int N=0;
			try {  
			  // Find keyword 'POINTS:' and number 'N' first
			  while((line=StringUtil.ourNextLine(fp))!=null) {
				  StringTokenizer tok = new StringTokenizer(line);
				  while(tok.hasMoreTokens()) {
					  String mainTok = tok.nextToken();
					  if(mainTok.equals("POINTS:")) {
						  try {
							  N=Integer.parseInt((String)tok.nextToken());
							  if (N<1 || N>p.nodeCount) throw new ParserException();
						  } catch(Exception ex) {
							  N=p.nodeCount;
						  }
					  }
				  }
			  }
			  
			  // reaching here, must be ready to read N xy[z] locations for
			  if (p.xyzpoint==null) 
				  p.xyzpoint=new Point3D[p.nodeCount+1];
			  int count=0;
			  try{
				  while((line=StringUtil.ourNextLine(fp))!=null && count<N) {
					  StringTokenizer tok = new StringTokenizer(line);
					  String str=(String)tok.nextToken();
					  double X=Double.parseDouble(str);
					  double Y=Double.parseDouble((String)tok.nextToken());
					  
					  // may have just 2 coords
					  double Z;
					  try {
						  Z=Double.parseDouble((String)tok.nextToken());
					  } catch(Exception ex) {
						  Z=0.0;
					  }
					  
					  count++;
					  p.xyzpoint[count]=new Point3D(X,Y,Z);
				  }
				  return count;
			  } catch (Exception ex) {
				  if (count>0) {
					  CirclePack.cpb.msg(
							  "read "+count+" xyz points from file "+filename);
					  return count;
				  }
				  else throw new InOutException();
			  }

		  } catch (Exception ex) {
			  CirclePack.cpb.myErrorMsg("Exception in reading xyz data from file.");
    			return 0;
		  }
		} // end of mode 1, (xyz points)
		return 0;
	}

	/**
	 * Open a BufferedReader for a named file; if script_flag is true,
	 * then it MUST come from the script, else from the RunDirectory.
	 * Return null on error.
	 * @param filename
	 * @param script_flag
	 * @return
	 */
	public static BufferedReader openReadFP(String filename,boolean script_flag) {
		return openReadFP(CurrentDirectory,filename,script_flag);
	}
	
	/**
	 * 
	 * @param dir File, ignored if reading from script
	 * @param filename String
	 * @param script_flag boolean: true look in script
	 * @return BufferedReader, null on error
	 */
	public static BufferedReader openReadFP(File dir,String filename,boolean script_flag) { 
		BufferedReader fp=null;
		File file=null;
		if (script_flag) {
			if (!PackControl.scriptManager.isScriptLoaded() ||
				(file=(File)PackControl.scriptManager.getTrueIncluded(filename))==null) {
				CirclePack.cpb.myErrorMsg("Error: script doesn't contain '"+filename+"'");
				return null;
			}
		}
		else 
			file=new File(dir,filename);
		try {
	    	fp = new BufferedReader(new FileReader(file));
		} catch(FileNotFoundException ex) {
			CirclePack.cpb.myErrorMsg("Failed to open "+file.getPath());
			return null;
		}
		return fp;
	}
	
	public static BufferedWriter openWriteFP(String filename,boolean script_flag) {
		return openWriteFP(CurrentDirectory,false,filename,script_flag);
	}

	/**
	 * Open a BufferedWriter for a named file; if it exists, it will be overwritten.
	 * If script_flag is true, then it is written to the script (if it's open), 
	 * else to the given directory or to CurrentDirectory.
	 * Return null on error.
	 * @param File dir (specified directory)
	 * @param filename String
	 * @param script_flag (file is for script)
	 * @return
	 */
	public static BufferedWriter openWriteFP(File dir,String filename,boolean script_flag) {
		return openWriteFP(dir,false,filename,script_flag);
	}
	
	/**
	 * Open a BufferedWriter for a named file; append if it exists.
	 * If script_flag is true, then it is written to the script (if it's open), 
	 * else to the CurrentDirectory.
	 * Return null on error.
	 * @param File dir (specified directory)
	 * @param append (append to file)
	 * @param filename
	 * @param script_flag (file is for script)
	 * @return
	 */
	public static BufferedWriter appendToFP(String filename,boolean script_flag) {
		return openWriteFP(CurrentDirectory,true,filename,script_flag);
	}
		
	/**
	 * Open a BufferedWriter for a named file; append if it exists.
	 * If script_flag is true, then it is written to the script (if it's open), 
	 * else to the given directory or the CurrentDirectory.
	 * Return null on error.
	 * @param File dir (specified directory)
	 * @param append (append to file)
	 * @param filename
	 * @param script_flag (file is for script)
	 * @return
	 */
	public static BufferedWriter appendToFP(File dir,String filename,boolean script_flag) {
		return openWriteFP(dir,true,filename,script_flag);
	}

	/** 
	 * Open when given the full path, as when using a file dialog.
	 * @param fullpath
	 * @param append
	 * @param script_flag
	 * @return
	 */
	public static BufferedWriter openWriteFP(File fullpath,boolean append,
			boolean script_flag) {
		File dir;
		String filename=null;
		try {
			dir=new File(fullpath.getParent());
			filename=fullpath.getName();
		} catch (Exception ex) {return null;}
		return openWriteFP(dir,append,filename,script_flag);
	}
	
	/**
	 * Open a BufferedWriter for a named file, either append or overwrite.
	 * If script_flag is true, then it is written to the script (if it's open), 
	 * else to the given directory or to CurrentDirectory.
	 * Return null on error.
	 * @param File dir (specified directory); if null (and !script_flag), currentDirectory
	 * @param append (append to file)
	 * @param filename
	 * @param script_flag (file is for script)
	 * @return
	 */
	public static BufferedWriter openWriteFP(File dir,boolean append,String filename,
			boolean script_flag) { 
		BufferedWriter fp=null;
		File file=null;
		if (script_flag) {
			if (!PackControl.scriptManager.isScriptLoaded()) {
				CirclePack.cpb.myErrorMsg("Error: no script is loaded; '"+
						filename+"' will be saved to a file");
				file=new File(CurrentDirectory,filename);
			}
			
			// For appending in script, have to do more: 
			//  * copy stored faux file to 'filename' in tmp directory
			//  * remove node in cpDataNode 
			//  * remove from scriptManager list
			// 'includeNewFile' must be called later
			else if (append && (file=(File)PackControl.scriptManager.
							getTrueIncluded(filename))!=null) {
				file=CPFileManager.renameTmpFile(file.getName(),filename);
				PackControl.scriptManager.removeIncludedFile(filename);
			}
			
			// Put file in tmp directory; 'includeNewFile' must be called later
			else {
				file=new File(System.getProperty("java.io.tmpdir"),filename);
			}
			
		}
		else { // replace ~/ in directory
			file=new File(dir,filename);
		}
			
		try {
	    	fp = new BufferedWriter(new FileWriter(file,append));
		} catch(Exception ex) {
			CirclePack.cpb.myErrorMsg("Failed to open "+filename+" for writing data");
			return null;
		}
		return fp;
	}

	/** 
	 * Look in standard locations for 'MyTool' files: first in 'toolDirectory',
	 * then in 'homeDirectory', then in jar 'Resources/mytools'.
	 * @param mytName
	 * @return URL or null
	 * 
	 * TODO: What's going on here??
	 */
	public static File getMyTFile(String mytName) {
		File file=null;
		try {
			try {
//				if ((file=new File(CPFileManager.ToolDirectory+File.separator+mytName))!=null) // local version
//					return file;
			} catch (Exception e) {
				if ((file=new File(CPFileManager.HomeDirectory+File.separator+
						"mytools"+File.separator+mytName))!=null) // default
					return file;
			}
			if (file==null) {
				try {
					if ((file=new File(CPBase.getResourceURL("/mytools/"+mytName).toString()))!=null)
						return file;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Copy one file to another in tmp directory, overwriting destination.
	 * NOTE: 'renameTo' can fail in Windows, so use this or 'copyFile' instead.
	 * @param srFile String, source file name (no directory)
	 * @param dtFile String, destination file name (no directory)
	 * @return destination File, null on error
	 */
	public static File renameTmpFile(String srFile, String dtFile) {
		File f1=null;
		File f2=null;
		try{
			f1 = new File(System.getProperty("java.io.tmpdir"),srFile);
			f2 = new File(System.getProperty("java.io.tmpdir"),dtFile);
		} catch (Exception ex) {
			return null;
		}
		return copyFile(f1,f2);
	}
	
	/**
	 * Copy one file to another, overwriting destination. If f2 is
	 * same name as f1, modify f2 by adding '1' to its name.
	 * @param f1 File, source, including directory
	 * @param f2 File, destination, including directory
	 * @return destination File, null on error
	 */
	public static File copyFile(File f1, File f2) {
		if (f1==null || f2==null)
			return null;
		
		// if name is the same, append a "1" to destination name
		if (f2.equals(f1)) {
			String path=f2.getPath()+"1";
			f2=new File(path);
		}
		try{
			InputStream in = new FileInputStream(f1);
		      
			//For Overwrite the file.
			OutputStream out = new FileOutputStream(f2);

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0){
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
			return f2;
		}
		catch(FileNotFoundException ex){
			CirclePack.cpb.errMsg(ex.getMessage());
			return null;
		}
		catch(IOException e){
		    throw new InOutException("error while renaming file: "+e.getMessage());      
		}
	}

	
}
