package allMains;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Path2D;
import java.io.File;
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
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.TileLink;
import math.Mobius;
import mytools.MyTool;
import packing.PackData;
import panels.CPScreen;
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
	public static File LibDirectory=new File(System.getProperty("java.io.tmpdir"));
	
	// Some useful stuff for schwarzian work
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
	public abstract int swapPackData(PackData p,int pnum,boolean keepX); // change packings[pnum]

	// main data container is 'packings', while 'cpScreens' hold 
	// backing plane for images, even in non-GUI situations.
	public static PackData []packings; // 'PackData' instances
	public static CPScreen []cpScreens; 

	public static int GUImode; // 0=standalone, else GUI
	
	// ------------- generic stuff
	public static String directory="~";
	public static boolean attachCcode=true; // as of 3/22 no longer trying this 
	public static String initialScript=null;
	public static CPBase sharedinstance=null;
	public static File XinfoFile = null; // hold help info for 'PackExtender's
	public static CPTimer cpTimer; // for crude timings
	public static RunProgress runSpinner;  // progress indicator
	
	// ------------- debug: commands to strerr
	public static boolean cmdDebug=false;

	// TODO: various 'toler' and 'okerr' terms are a mess. interim value
	//   (see also 'PackData.OKERR' and 'PackData.TOLER'.)
	public static final double GENERIC_TOLER=.0000000001;   
	public static int NUM_PACKS;
	public static double FAUX_RAD; // eucl rad for circles through infinity
	public static File CPprefFile;

	// static, should (eventually) be adjustable in preferences;
	//   may not be needed in some programs.
	public static int RIFFLE_COUNT=1000; // default iterations for repacking
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
	public static PointLink Zlink;
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
	public static String XMD_URL_FILE="xmd_URLs/";
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

	// modes for add_gen, add_lay
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

	}
	
	public boolean setFtnSpec(String ftnstr) {
		  this.FtnSpecification=new StringBuilder(ftnstr);
		  this.FtnParser.parseExpression(
				  this.FtnSpecification.toString());
		  if (this.FtnParser.funcHasError()) {
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
    
	public boolean setParamSpec(String paramstr) {
		  this.ParamSpecification=new StringBuilder(paramstr);
		  this.ParamParser.parseExpression(
				  this.ParamSpecification.toString());
		  if (this.ParamParser.funcHasError()) {
			  return false;
		  }
		  return true;
		
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
