package allMains;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import com.jimrolf.functionparser.FunctionParser;

import circlePack.RunProgress;
import complex.Complex;
import cpTalk.sockets.CPMultiServer;
import exceptions.DataException;
import geometry.CircleSimple;
import input.CPFileManager;
import input.SocketSource;
import input.TrafficCenter;
import listManip.BaryCoordLink;
import listManip.BaryLink;
import listManip.DoubleLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.TileLink;
import math.Mobius;
import mytools.MyTool;
import packing.CPdrawing;
import packing.PackData;
import panels.CPcanvas;
import posting.PostManager;
import script.ScriptManager;
import util.CPTimer;
import util.CallPacket;
import variables.VarControl;

/**
 * This is abstract class intended to serve as the head for 
 * programs that do circle packing. It allows some generic 
 * flexibility (i.e., 'msg()'), sets up the key general 
 * variables, the packing data objects, and models for data
 * managers, etc. Not all needed in every case; in particular,
 * I am working toward a "standalone" version along with the
 * usual GUI version.
 */
public abstract class CPBase {
	
	// directory for codes such as 'triangle', 'qhull'
	public static File TempDirectory=new File(System.getProperty("java.io.tmpdir"));
	public static File ScreenShotDirectory=new File(System.getProperty("java.io.tmpdir"));
		
	// Useful constants
	public static final double pi2=Math.PI*2.0;
	public static final double piby2=Math.PI/2.0;
	public static final double sqrt3=Math.sqrt(3);           // sqrt{3}
	public static final double sqrt3by2=Math.sqrt(3)/2.0;    // sqrt{3}/2
	// 3rd roots of unity; unit normals to base equilateral triangle edges
	public static final Complex []omega3= 
		{new Complex(1.0),new Complex(-.5,CPBase.sqrt3by2),new Complex(-.5,-CPBase.sqrt3by2)};
	public static CircleSimple C_b=
			new CircleSimple(new Complex(4-Math.sqrt(3)),Math.sqrt(3));
	
	// abstract methods that must be implemented by derived classes
	public abstract void myMsg(String str); // generic message
	public abstract void myErrorMsg(String str); // generic error message
	public abstract void myDebugMsg(String str); // generic debug message
	public abstract PackData getActivePackData(); // which PackData is active?
	public abstract int getActivePackNum(); // which pack
	public abstract PackData swapPackData(PackData p,int pnum,boolean keepX); // change packings[pnum]
	
	// main data container is 'packings', while 'cpDrawing's hold 
	// backing plane for images, even in non-GUI implementations.
	public static PackData []packings; // 'PackData' instances
	public static CPdrawing []cpDrawing;
	public static CPcanvas []cpCanvas; // GUI panel

	public static int GUImode; // 0=standalone, else GUI
	
	// ------------- generic stuff
	public static String directory="~";
	public static boolean attachCcode=true; // as of 3/22 no longer trying this 
	public static String initialScript=null;
	public static CPBase sharedinstance=null;
	public static File XinfoFile = null; // hold help info for 'PackExtender's
	public static CPTimer cpTimer; // for crude timings
	public static RunProgress runSpinner;  // progress indicator
	public static String IMG="jpg"; // extension for screen shot images
	public static File ScriptList; // html file, see 'ScriptLister.java'  
	
	// ------------- debug: commands to strerr
	public static boolean cmdDebug=false;

	// TODO: various 'toler' and 'okerr' terms are a mess. interim value
	//   (see also 'PackData.OKERR' and 'PackData.TOLER'.)
	public static final double GENERIC_TOLER=.0000000001;   
	public static int NUM_PACKS;
	public static int activePackNum; // commands applied to this packing
	public static double FAUX_RAD; // eucl rad for circles through infinity
	public static File CPprefFile;

	// static, should (eventually) be adjustable in preferences;
	//   may not be needed in some programs.
	public static int RIFFLE_COUNT=2000; // default iterations for repacking
	public static int DEFAULT_GEOMETRY = -1;

	// keep 'MyTool' objects in a hashtable
	public static Hashtable<String,MyTool> hashedTools; 
	
	// global objects: list, path, etc.
	public static NodeLink Vlink;
	public static FaceLink Flink;
	public static EdgeLink Elink;
	public static HalfLink Hlink; 
	public static TileLink Tlink; 
	public static GraphLink Glink; 
	public static DoubleLink Dlink; // doubles
	public static PointLink Zlink; // complexes
	public static BaryLink Blink;
	public static Path2D.Double ClosedPath; // closed path (real world data, transform to draw) 
	public static Vector<BaryCoordLink> gridLines; // bary-coord encoded paths, usually for grids
	public static Vector<BaryCoordLink> streamLines; // bary-coord encoded paths, usually streamlines
	public static CallPacket CPcallPacket;  // general use 'CallPacket' to hold computed values
	public static Mobius Mob; // Global Mobius transformation
	public static int debugID; // for labeling debug files
	
	// holds global function and parameter function descriptions
	public StringBuilder FtnSpecification; 
	public StringBuilder ParamSpecification;
	public FunctionParser FtnParser;
	public FunctionParser ParamParser;
	
	// Managers for various functions; 
	public static CPFileManager fileManager;  // directories, opening for read/write, etc.
	public static PostManager postManager;    // PostScript file manager
	public static ScriptManager scriptManager; // managing a 'script' frame and files
	public static VarControl varControl;  // managing 'variable' strings 

	// Start command parser pieces in order. 
	public static TrafficCenter trafficCenter;
	
	// ---------- may want one or more sockets, e.g., commands from matlab
	public static boolean socketActive; 
	public static int cpSocketPort=3736;
	public static String cpSocketHost;
	public static CPMultiServer cpMultiServer;
	public static Vector<SocketSource> socketSources;  
	
	// ---------- Preferences
	public static String PACKINGS_DIR="packings/";
	public static String SCRIPT_DIR="scripts/";
	public static String IMAGE_DIR="pics/";
	public static String TOOL_DIR="mytools/";
	public static String EXTENDER_DIR="myCirclePack/bin/";
	public static String PRINT_COMMAND="lpr ";
	public static String POSTSCRIPT_VIEWER="gv ";
	public static String WEB_URL_FILE="web_URLs/";
	public static String SCRIPT_URL_FILE="script_URLs/";
	public static String ACTIVE_CANVAS_SIZE="650";
	public static String PAIR_CANVAS_SIZE="400";
	public static String FONT_INCREMENT="0";

	// various default colors
	public static Color DEFAULT_HORIZON_COLOR = Color.BLACK; // drawing sphere
	public static Color DEFAULT_SD_COLOR = Color.BLUE; // unit circle/equator
	public static Color DEFAULT_CANVAS_BACKGROUND = Color.WHITE;
	// For background surrounding disc and sphere
	public static Color DEFAULT_SphDisc_BACKGROUND = new Color(230,230,230);
	
	// canvas settings
	public static int DEFAULT_FILL_OPACITY = 125;
	public static int DEFAULT_SPHERE_OPACITY = 255;
	public static Font DEFAULT_INDEX_FONT = new Font("Sarif",Font.ITALIC,11);
	public static int DEFAULT_LINETHICKNESS = 2;
	
	public static int DEFAULT_PS_PAGE_SIZE=7; // PostScript page size in inches 
	public static Color defaultCircleColor;
	public static Color defaultFillColor; // basic fill color

	// modes for add_gen, add_layer calls
	public static final int TENT=0;
	public static final int DEGREE=1;
	public static final int DUPLICATE=2;
	
	// These are the recognized 'tool types' prefixes for creating MyTool's, 
	//   editors, lists of icons, etc. There may be additional characters
	//   needed to form keys for 'hashedTools'.
	public static String[] tooltypes = 
		{"MAIN:","BASIC:","MYTOOL:","SCRIPT:","MOBIUS:","SIDEPAIR:"};


	/**
	 * For finding correct path to 'Resources' directory in jar file
	 * @param path, String
	 * @return URL
	 */
	public static URL getResourceURL(String path) {
		try {
			ClassLoader cll = CPBase.sharedinstance.getClass().getClassLoader();
			
			// null ClassLoader? look in user directory
			if( cll==null || cll.getResource("Resources"+path)==null ) {
				File file=new File(System.getProperty("user.dir")
						+File.separator+"Resources"+path);
				return file.toURI().toURL();
			} 
			
			// got ClassLoader? look there
			else { 
				return cll.getResource("Resources"+path);
			}
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	// Constructor
	public CPBase() {
		if( CPBase.sharedinstance==null )
			CPBase.sharedinstance=this;
		
		// load *.exe files into TempDirectory
//	    System.out.println("temp directory is: "+System.getProperty("java.io.tmpdir"));
		boolean goodtogo=
				(extractExeFiles(System.getProperty("java.io.tmpdir"),"triangle.exe")
						&& extractExeFiles(System.getProperty("java.io.tmpdir"),"qhull.exe"));
		if (!goodtogo) {
			System.err.println("Failed to load all the executables");
		}
		
		scriptManager=null;
		defaultCircleColor = Color.BLACK;
		defaultFillColor = new Color(Color.ORANGE.getRed(),Color.ORANGE.getGreen(),
				Color.ORANGE.getBlue(),DEFAULT_FILL_OPACITY);
		debugID= new Random().nextInt(32000);
		hashedTools = new Hashtable<String,MyTool>(100);
		varControl = new VarControl();
		
		// initiate expression parsers
		FtnParser=new FunctionParser();
		FtnParser.setComplex(true);
		FtnParser.removeVariable("x");
		FtnParser.setVariable("z");
		ParamParser=new FunctionParser();
		ParamParser.setComplex(true); // even though argument t is double
		ParamParser.removeVariable("x");
		ParamParser.setVariable("t");
		// start with identity 
		FtnSpecification=new StringBuilder("z"); 
		ParamSpecification=new StringBuilder("t");

		activePackNum=0;
	}
	
	/* Extracts files in Resources/executables to the TempDirectory. 
     * E.g. qhull.exe and triangle.exe used for Delaunay triangulations.
     * TODO: have to generate and load executables for other operating
     * systems, e.g., mac.
     * @param destDir destination directory.
     * @param execName filename
     * @return boolean
     */
    private boolean extractExeFiles(String destDir,String execName) {
    	
		InputStream ins=null;
		try {
			ins = getClass().getResourceAsStream("/Resources/executables/"+execName);
		} catch (Exception ex) {
			System.err.println("Didn't get InputStream for executables in 'CPBase'");
			return false;
		}
		
        try {
            java.io.File f = new java.io.File(destDir + java.io.File.separator + execName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
        	while (ins.available() > 0) {  // write contents of 'ins' to 'fos'
        		fos.write(ins.read());
        	}
        	fos.close();
        } catch (Exception iox) {
        	System.err.println("exception in writing an executable");
        	return false;
        }
        
        return true;
    }

	/**
	 * Set the screendump image format: choices are "jpg", "png",
	 * "gif", "bmp", "wbmp".
	 * @param img
	 * @return 1 on success
	 */
	public int setIMG(String img) {
		if (img.equalsIgnoreCase("JPG"))
			IMG="jpg";
		else if (img.equalsIgnoreCase("PNG"))
			IMG="png";
		else if (img.equalsIgnoreCase("GIF"))
			IMG="gif";
		else if (img.equalsIgnoreCase("BMP"))
			IMG="bmp";
		else if (img.equalsIgnoreCase("WBMP"))
			IMG="wbmp";
		else 
			return 0;
		return 1;
	}
	
	/**
	 * Put user function text in 'FtnSpecification' if it
	 * parses correctly.
	 * @param ftnstr
	 * @return boolean, false for function parsing error
	 */
	public boolean setFtnSpec(String ftnstr) {
		  this.FtnSpecification=new StringBuilder(ftnstr);
		  this.FtnParser.parseExpression(
				  this.FtnSpecification.toString());
		  if (this.FtnParser.funcHasError()) {
			  this.FtnSpecification=new StringBuilder();
			  return false;
		  }
		  return true;
	}

    /**
	 * Put user function text in 'ParamSpecification' if it
	 * parses correctly.
     * @param paramstr
     * @return boolean, false on parsing error
     */
	public boolean setParamSpec(String paramstr) {
		  this.ParamSpecification=new StringBuilder(paramstr);
		  this.ParamParser.parseExpression(
				  this.ParamSpecification.toString());
		  if (this.ParamParser.funcHasError()) {
			  this.ParamSpecification=new StringBuilder();
			  return false;
		  }
		  return true;
		
	}

    /**
     * The parser treats 'z' as denoting a complex variable. 
     * This tells parser to set z to a specific value and evaluate 
     * the function.
     * @param z, Complex
     * @return Complex
     */
    public Complex getFtnValue(Complex z) {
    	try {
    		com.jimrolf.complex.Complex w=CirclePack.cpb.
    				FtnParser.evalFunc(new com.jimrolf.complex.Complex(z.x,z.y));
    		return new Complex(w.re(),w.im());
    	} catch (Exception ex) {
    		throw new DataException("Function Parser error: "+ex.getMessage());
    	}
    }
    
    /**
     * TODO: have to figure out how to designate variable character 't'
     * 
     * The parser treats 't' as denoting a double variable. 
     * This tells parser to set t to a specific value and evaluate 
     * the parameter expression.
     */
    public Complex getParamValue(double t) {
    	try {
    		com.jimrolf.complex.Complex w=CirclePack.cpb.ParamParser.evalFunc(
    				new com.jimrolf.complex.Complex(t,0.0));
    		return new Complex(w.re(),w.im());
    	} catch (Exception ex) {
    		throw new DataException("Path evaluation error: "+ex.getMessage());
    	}
    }
    
	/**
	 * Call the subclass for generic message
	 */
	public void msg(String str) {
		myMsg(str);
	}

	/**
	 * Pass to subclass for generic message
	 */
	public void errMsg(String str) {
		myErrorMsg(str);
	}

	/**
	 * Pass to subclass for generic message
	 */
	public void debugMsg(String str) {
		myDebugMsg(str);
	}

}
