package panels;

import input.CPFileManager;

import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import util.GenPathUtil;
import util.StringUtil;
import allMains.CPBase;
import allMains.CirclePack;

import complex.Complex;

/**
 * This manages paths in the plane, reading, writing, etc.
 * TODO: coordinate better with 'PathLink' class. E.g., might
 * read in as PathLink first, then convert.
 * @author kens
 *
 */
public class PathManager {
	
	static enum PathReadState {LOOK_FOR_PATH,START_SEG,READ_XY,CLOSE};
	
	/**
	 * Reads a file with 'PATH' data; look in script if 'script_flag' is true,
	 * else look first in the 'PackingDirectory', then in the 'CurrentDirectory'.
	 * @param filename
	 * @param script_flag
	 * @return Path2D.Double
	 */
	public static Path2D.Double readpath(String filename,boolean script_flag) {
		return readpath(null,filename,script_flag);
	}
	
	/**
	 * Reads a file with 'PATH' data; look in script if 'script_flag' is true,
	 * else look first in 'dir', then default to 'PackingDirectory' or 'CurrentDirectory'.
	 * @param dir File, directory or null to go to default 
	 * @param filename String, filename alone
	 * @param script_flag boolean, if true and 'dir' is null, look in script for the file
	 * @return Path2D.Double, null on error
	 */
	public static Path2D.Double readpath(File dir,String filename,boolean script_flag) {
		Path2D.Double gpath=null;
		BufferedReader fp=null;
		
		// look in script
		if (script_flag) {
			fp=CPFileManager.openReadFP(null,filename,script_flag);
			if (fp==null)
				return null;
		}
		
		// given directory
		else if (dir!=null) {
			fp=CPFileManager.openReadFP(dir,filename,script_flag);
		}
		
		// check default locations
		if (fp==null && (fp=CPFileManager.openReadFP(CPFileManager.PackingDirectory,filename,script_flag))==null &&
			(fp=CPFileManager.openReadFP(CPFileManager.CurrentDirectory,filename,script_flag))==null) {
			return null;
		}
		
	    String line;
	    
	    // Must find 'PATH:' before reading further
	    PathReadState state=PathReadState.LOOK_FOR_PATH;
	    try {
	    	while(state==PathReadState.LOOK_FOR_PATH 
	    			&& (line=StringUtil.ourNextLine(fp))!=null) {
	    		StringTokenizer tok = new StringTokenizer(line);
	    		while(tok.hasMoreTokens()) {
	    			String mainTok = tok.nextToken();
	    			if(mainTok.startsWith("PATH")) {
	    				gpath=new Path2D.Double();
	    				state=PathReadState.START_SEG;
	    			}
	    		}
	    	} // end of while for 'PATH'
	    	
		    // now expect next non-empty line to have two doubles
	    	while (state==PathReadState.START_SEG 
	    			&& ((line=StringUtil.ourNextLine(fp))!=null)) {
	    		try {
	    			StringTokenizer tok = new StringTokenizer(line);
	    			double x=Double.parseDouble((String)tok.nextToken());
	    			double y=Double.parseDouble((String)tok.nextToken());
	    			gpath.moveTo(x,y);
	    			state=PathReadState.READ_XY;
	    		} catch (Exception ex) {
	    			CirclePack.cpb.myErrorMsg("Exception in reading path from '"+filename+"'.");
	    			return null;
	    		}

	    		while(state==PathReadState.READ_XY 
	    			&& (line=StringUtil.ourNextLine(fp))!=null) {
	    			StringTokenizer tok = new StringTokenizer(line);
	    			String xstr=null;
	    			// expect two doubles per line
	    			try {
	    				xstr=(String)tok.nextToken();
	    				double x=Double.parseDouble(xstr);
	    				double y=Double.parseDouble((String)tok.nextToken());
	    				gpath.lineTo(x,y);
	    			} catch (NumberFormatException nfe) {
	    				if (xstr.contains("BREAK")) { // indicates a new component of path
	    					gpath.closePath();
	    					state=PathReadState.START_SEG;
	    				}
	    				else state=PathReadState.CLOSE;
	    			}
	    		}
	    	}
	    	if (state==PathReadState.CLOSE) {
	    		gpath.closePath();
	    		return gpath;
	    	}
	    	else return null;
	    } catch (Exception ex) {
	    	CirclePack.cpb.myErrorMsg("Exception in reading path from '"+filename+"'.");
	    	return null;
	    }
	}
	
	/** 
	 * Write the given path to a file.
	 * @param fp
	 * @param gpath
	 * @return
	 * @throws IOException
	 */
	  public static int writepath(BufferedWriter fp,Path2D.Double gpath) 
	  throws IOException {
		    if (fp==null) return 0;
		    fp.write("PATH:\n");
			double flatness = GenPathUtil.gpExtent(gpath) * GenPathUtil.FLAT_FACTOR;
			// get polynomial approximation 
			Vector<Vector<Complex>> polyGamma = GenPathUtil.gpPolygon(gpath,
					flatness);
			Iterator<Vector<Complex>> pvec=polyGamma.iterator();
			while (pvec.hasNext()) {
				Vector<Complex> piece=(Vector<Complex>)pvec.next();
				Iterator<Complex> ij=piece.iterator();
				Complex z;
				while (ij.hasNext()) {
					z=(Complex)ij.next();
					fp.write(new String(z.x+" "+z.y+"\n"));
				}
				
				// put delimiter if needed
				if (pvec.hasNext()) 
					fp.write("BREAK\n");
			}
			fp.write("END\n");
			fp.flush();
			fp.close();
			return 1;
	  }

	  /**
	   * Use 'ClosedPath', this returns true if z is
	   * inside the path, else it returns false.
	   * @param z Complex
	   * @return boolean
	   */
	  public static boolean path_wrap(Complex z) {
		  return path_wrap(z,CPBase.ClosedPath);
	  }

	  /**
	   * Return true if z is inside 'genpath'.
	   * @param z, Complex
	   * @param genpath, Path2D.Double
	   * @return boolean
	   */
	  public static boolean path_wrap(Complex z,Path2D.Double genpath) {
		  if (genpath==null) return false;
		  return genpath.contains(z.x,z.y);
	  }
	  
	  
	  
		
}
