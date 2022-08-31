package posting;

import input.CPFileManager;
import input.CommandStrParser;
import packing.CPdrawing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Random;

import allMains.CPBase;
import circlePack.PackControl;
import exceptions.InOutException;

/** For opening/augmenting/closing postscript output files.
 * Status of 'fp' is important for opening/closing/adding
 * decisions, etc. This manager persists, but 'fp' changes.
 *
 * This is all rather complicated since stuff can be added to the
 * postscript file in increments, pages can be defined, material
 * inserted, etc. The final file is created only when it is
 * about to be closed: then we open the file and
 *
 * 1. add prefix material (where, eg. 'BoundingBox' may be 
 *    adjusted for text at end); see 'initialize'
 * 2. copy in the main body, temporary in file, 'bodyFile';
 *    this is where the 'fp' file pointer is set and where
 *    'postFactory' writes stuff.
 * 3. copy in trailer stuff accummulated in 'tail' file.
 * 4. close with any text accummulated in 'textBuffer'.
 *   
 * Also, there are questions of interaction with user and "PostScript"
 * tab, errors to catch, filename, where to store.

 * See http://www.cs.otago.ac.nz/cosc463/random-access.htm for hints
 */
public class PostManager {

	public static final double PS_UNIT_LINEWIDTH=1.0;
	
	public String postFilename;
	public File psUltimateFile;  // The finished postscript file  
	BufferedWriter fp; 
	public PostFactory pF;
	static int id; // unique identifier for temp files.
	double ps_linewidth;
	public StringBuilder textBuffer; // accum text for bottom of postscript file
	public int textLineCount; // counts lines of text at bottom of file 
	
	// Constructor
	public PostManager() {
		ps_linewidth=PS_UNIT_LINEWIDTH;
		id = new Random().nextInt(32000);
		psUltimateFile=null;
		fp=null;
		pF=null;
		textBuffer=null;
		textLineCount=0;
	}
	
	/**
	 * Open postscript file 'psFilename' for new data from given packing;
	 * may/may not be open/initialized already. If adding new material,
	 * position file after 'targStr'. The 'mode' specifies:
	 *    1 = open the file (previous contents discarded!)
	 *    2 = insert in file after specified text (open if necessary)
	 *    3 = append to file as new page (open if necessary) 
	 * @param cpDrawing
   	 * @param mode: 1,2,3 
	 * @param psFilename filename (if null, use 'cpDrawing.customPS')
	 * @param targStr target string when inserting
	 * @return int
	 * @throws InOutException
	 */
	public int open_psfile(CPdrawing cpDrawing,int mode,String psFilename,String targStr) {

		// choose ultimate file name for use when creating the final postscript
		if (psFilename==null) { // none given? use default
			if (cpDrawing.customPS==null) postFilename=new String(CPdrawing.customGlobal);
			else postFilename=new String(cpDrawing.customPS);
		}
		else postFilename=psFilename;
		if (!postFilename.endsWith(".ps")) // user may forget .ps
			postFilename=new String(postFilename+".ps");
		psUltimateFile=new File(CPFileManager.ImageDirectory,postFilename);
		
		// temporarily, the main contents are put in 'bodyFile'
		File bodyFile = new File( System.getProperty("java.io.tmpdir"),
			    new String(File.separator+id+"_"+postFilename));

		// close (losing contents) and reopen 'fp' to 'bodyFile'
		if (mode==1) { 
			if (isOpen()) { 
				try {
					fp.close();
				} catch (IOException ex) {
					throw new InOutException("trying to close PS file: "+ex.getMessage());
				}
			}
			
			// now reopen // TODO: Do we want to be sure to overwrite here if file exists?
			try {
				fp = new BufferedWriter(new FileWriter(bodyFile)); 
	        } catch (Exception ex) {
	        	fp=null;
				throw new InOutException("failed to open tmp 'bodyFile': "+ex.getMessage());
	        }
		}
		if (fp==null) {
			throw new InOutException("failed to open tmp 'bodyFile'");
		}
		
		// open 'postFactory' to accept input to 'bodyFile'
		pF=new PostFactory();
		pF.fp=fp;
		CommandStrParser.pF=pF;
		textBuffer=null; 
		return 1;
	}
	
	/**
	 * This wraps up the postscript file: 
	 * a) put in preample stuff, including size info
	 *    NOTE: size info depends on cpDrawing data at time file is completed
	 *       instead of when it was started.
	 * b) include the accumulated postscript in 'bodyFile'
	 * c) put in the trailer, including any text at the bottom.
	 *  
	 * Reset everything
	*/
	public int close_psfile(CPdrawing cpd) {
	  if (!isOpen()) // writer to 'bodyFile' is not open 
		  return 0;
	  try {
		  fp.flush();
		  fp.close(); // close 'bodyFile' (temp storage of main postscript)
	  } catch (Exception ex) {
		  throw new InOutException("problem closing postscript: "+ex.getMessage());
	  }
	  fp=null;
	  pF=null; // close 'PostFactory'
	  CommandStrParser.pF=null;
	  	  
	  try {
		  BufferedWriter finalPS=new BufferedWriter(new FileWriter(psUltimateFile));
		  
		  // preamble text
		  preAmble(finalPS,cpd);
		  
		  // main body
		  File bodyFile = new File( System.getProperty("java.io.tmpdir"),
				    new String(File.separator+id+"_"+postFilename));
		  BufferedReader bodyFP=new BufferedReader(new FileReader(bodyFile));
	      int c;
	      while ((c=bodyFP.read())>=0) 
	    	  finalPS.write(c);
	      bodyFP.close();
	      
	      // trailer to close
    	  if (PackControl.postManager.textBuffer==null ||
    			  PackControl.postManager.textBuffer.length()==0) // no bottom text
    		  finalPS.write("\ngrestore\nend\nshowpage\n"); // page wrapup
    	  else {
    		  finalPS.write("\ngrestore\n"); 
    		  finalPS.write(PackControl.postManager.textBuffer.toString());
    		  finalPS.write("\nend\nshowpage\n");
    	  }
    	  finalPS.flush();
    	  finalPS.close();
	  } catch (Exception ex) {
		  throw new InOutException("closing postscript file: "+ex.getMessage());
	  }
	  postFilename=null;
	  textBuffer=null;
	  textLineCount=0;
	  return 1;
	} 

	public void preAmble(BufferedWriter bw, CPdrawing cpDrawing) throws IOException {
		/* -------- preamble: standard eps-type header info -------------- */
	    bw.write("%!PS-Adobe-2.0 EPSF-2.0\n%%Title: "+postFilename+"\n");
	    bw.write("%%Creator: "+PackControl.CPVersion+
		     "\n%%CreationDate: "+new Date().toString()+"\n");
	    bw.write("%%For: "+System.getProperty("user.name")+"\n%%Orientation: Portrait\n");
	    post_size_settings(bw,cpDrawing,1); // BoundingBox
	    bw.write("%%Pages: 1\n%%BeginSetup\n%%EndSetup\n%%"+
		    "Magnification: 1.0000\n%%EndComments\n");
	    bw.write("\n% CirclePack dictionary ================\n");

	/* -------- define CPdict ------------------------------------------ */
	    bw.write("/CPdict 256 dict def\nCPdict begin");

	    bw.write("\n% --- Standard abbreviations\n");
	    bw.write("\t/cp {closepath} bind def\n\t/ef {eofill} bind "+
		    "def\n\t/gr {grestore} bind def\n\t/gs {gsave} bind "+
		    "def\n\t/sa {save} bind def\n\t/rs {restore} bind "+
		    "def\n\t/l {lineto} bind def\n\t/m {moveto} bind "+
		    "def\n\t/rm {rmoveto} bind def\n");
	    bw.write("\t/n {newpath} bind def\n\t/s {stroke} bind "+
		    "def\n\t/sh {show} bind def\n\t/slc {setlinecap} bind "+
		    "def\n\t/slj {setlinejoin} bind def\n\t/slw "+
		    "{setlinewidth} bind def\n\t/srgb {setrgbcolor} bind "+
		    "def\n\t/rot {rotate} bind def\n\t/sc {scale} bind def\n");
	    bw.write("\t/sd {setdash} bind def\n\t/ff {findfont} bind "+
		    "def\n\t/sf {setfont} bind def\n\t/scf {scalefont} bind "+
		    "def\n\t/sw {stringwidth} bind def\n\t/tr {translate} "+
		    "bind def\n");
	/* -------- Unique to CirclePack abbreviations, etc. -------------- */
	    bw.write("\n% --- Special abbreviations\n");
	    bw.write("   /sg {setgray} bind def\n   /a {arc} bind "+
		    "def\n   /an {arcn} bind def\n");
	    bw.write("   /c { 0 360 a s} bind def\t\t\t% circle\n");
	    bw.write("   /cc {0 360 a gs srgb s gr n} bind def\t\t% color circle\n");
	    bw.write("   /d { 0 360 a gs sg fill gr s} bind def\t% disc\n");
	    bw.write("   /cd {0 360 a gs srgb fill gr s} bind def\t% color disc\n");
	    bw.write("   /mark {gs 0 360 a srgb fill gr n} bind "+
		    "def\t%default mark symbol\n");
	    bw.write("   /wht {1.0} bind def\t\t\t\t% gray levels\n   "+
		    "/gry {0.8} bind def\n  /drk {0.5} bind def\n   /blck "+
		    "{0.0} bind def\n");
	    bw.write("   /ourlinewidth {.002 mul setlinewidth} bind "+
		    "def\n");
	    bw.write("   /getlinewidth {currentlinewidth 500.0 mul} bind "+
	    	"def\n");
	    bw.write("\nend\n%% end CirclePack dictionary "+
		    "=================\n%%EndProlog\n");
	    
/* -------------- begin first page ----------------------- */

	    bw.write("\n%%Page: "+1+" "+1+"\nCPdict begin\ngsave\n");
	    bw.write("   72 72 sc % inches\n   "+4.25+" "+5.5+" tr\n"+
	    "   1 slc  1 slj\n");
	    post_size_settings(bw,cpDrawing,6); // BoundingBox, size info, clip window
	    if (cpDrawing.getGeom()!=0) // sph/hyp case: draw sphere/disc first
	    	bw.write("n 0 0 1 c\n\n");
	}

	/** 
	 * Put screen-dependent size data in postscript file. 'mode' is bit coded:
	 *        1 = BoundingBox; 2 = size info; 4 = clip window.
	 * Typically open with mode=6.
	 */
	public int post_size_settings(BufferedWriter bw,CPdrawing cpDrawing,int mode) 
	throws IOException {
	      double size=(double)CPBase.DEFAULT_PS_PAGE_SIZE;
	      if((mode & 1)==1) { // BoundingBox 
	          int bblx=(int)(72*(4.25-size/2))-10;
	          int bbly=(int)(72*(5.5-size/2))-10;
	          if (textBuffer!=null) // need to lower bottom for text 
	        	  bbly -= textLineCount*18; 
	          int bbrx=(int)(72*(4.25+size/2))+10;
	          int bbry=(int)(72*(5.5+size/2))+10;
	          bw.write("%%BoundingBox: "+bblx+" "+bbly+" "+bbrx+" "+bbry+"\n");
	      }
	      if ((mode & 2)==2) { // basic size info 
	          bw.write("% ---------- pack size settings\n");
	          double factor= size/(cpDrawing.XWidth);
	          /* -------- scaling/translation for screen */
	          bw.write("     "+factor+" "+(double)((1.01)*factor)+" sc \n     "+
	    	       (double)(-(cpDrawing.realBox.lz.x+cpDrawing.realBox.rz.x)/2.0)+" "+
	    	       (double)(-(cpDrawing.realBox.lz.y+cpDrawing.realBox.rz.y)/2.0)+" tr\n");
	          /*  -------- define 'ourlinewidth' at 1 point */
	          bw.write("      /ourlinewidth\n      { 72 div "+factor+" div "+
	        		  ps_linewidth+" mul setlinewidth}  def\n");
	          /*  -------- pre-defined thickness factors */
	          bw.write("      /onetk\n      {1 ourlinewidth} def\n");
	          bw.write("      /twotk\n      {2 ourlinewidth} def\n");
	          bw.write("      /threetk\n      {3 ourlinewidth} def\n");
	          bw.write("      /fourtk\n      {4 ourlinewidth} def\n");
	          bw.write("      /fivetk\n      {5 ourlinewidth} def\n");
	          bw.write("      /sixtk\n      {6 ourlinewidth} def\n");
	          bw.write("      /seventk\n      {7 ourlinewidth} def\n");
	          bw.write("      /eighttk\n      {8 ourlinewidth} def\n");
	          bw.write("      /ninetk\n      {9 ourlinewidth} def\n");
	          bw.write("      /tentk\n      {10 ourlinewidth} def\n");
	    	  bw.write("    "+(double)ps_linewidth+" ourlinewidth\n     0 sg\n");
	          bw.write("/Times-Roman ff "+(double)(.15/factor)+
	    	       " scf sf\n% ------------\n"); // scale font
	        }
	      if ((mode & 4)==4) /* clip window */
	        {
	    /* ----- now scaled based on screens size and desired output size */
	    	  double lx=cpDrawing.realBox.lz.x;
	    	  double ly=cpDrawing.realBox.lz.y;
	    	  double rx=cpDrawing.realBox.rz.x;
	    	  double ry=cpDrawing.realBox.rz.y;
	          bw.write("n\n"+lx+" "+ry+" m\n"+lx+" "+ly+" l\n"+rx+" "+ly+" l\n"+
	    	       rx+" "+ry+" l\ncp\n%gs s gr\nclip\nn\n"); // clip box
	        }
	      return 1;
	}

	/**
	 * Check if a 'PostFactory' exists and 'fp' is non-null, meaning the
	 * temporary 'bodyFile' of postscript commands is open.
	 * @return boolean
	 */
	public boolean isOpen() {
		return (pF!=null && fp!=null);
	}

}
