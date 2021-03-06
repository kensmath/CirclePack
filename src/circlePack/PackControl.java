package circlePack;

import handlers.MYTOOLHandler;
import handlers.SCRIPTHandler;
import images.CPIcon;
import images.OwlSpinner;
import input.CPFileManager;
import input.CmdSource;
import input.FileDialogs;
import input.MyConsole;
import input.ShellManager;
import input.SocketSource;
import input.TrafficCenter;
import interfaces.IMessenger;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import listManip.BaryCoordLink;
import listManip.BaryLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.TileLink;
import math.Mobius;
import mytools.MyTool;
import packing.PackData;
import panels.CPPreferences;
import panels.CPScreen;
import panels.SmallCanvasPanel;
import posting.PostManager;
import script.ScriptBundle;
import script.ScriptHover;
import script.ScriptManager;
import script.VertScriptBar;
import util.CPTimer;
import util.PopupBuilder;
import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import browser.BrowserFrame;
import canvasses.CanvasReDrawManager;
import canvasses.CursorCtrl;
import canvasses.MainFrame;
import cpTalk.sockets.CPMultiServer;
import frames.AboutFrame;
import frames.FunctionHover;
import frames.HelpHover;
import frames.HoverPanel;
import frames.MessageHover;
import frames.MobiusFrame;
import frames.OutputFrame;
import frames.PairedFrame;
import frames.ScreenCtrlFrame;
import frames.TabbedPackDataHover;

/**
 * 'PackControl' populates the principal JFrame 'CPBase.frame' for 
 * the CirclePack package.
 */
public class PackControl extends CPBase implements MouseMotionListener, 
FocusListener {

	// get computer's display dimensions
	public static Dimension displayDimension=Toolkit.getDefaultToolkit().getScreenSize();

	// GUI stuff
	public static JFrame frame=null;
	public static MainFrame activeFrame=null; // contains activeCanvas
	public static PairedFrame mapPairFrame=null;
	public static HoverPanel controlPanel;
	public static CanvasReDrawManager canvasRedrawer; // for repainting various canvasses
	static Date date=new Date();
	public static String CPVersion= new String("CirclePack, "+circlePack.Version.version+", "+
			DateFormat.getDateInstance(DateFormat.MEDIUM).format(date));
	public static boolean MapCanvasMode; // true: 'PairedFrame" shows, else 'MainFrame'
	public static boolean AdvancedMode; // true: 'PackControl' open 
	
	// ********* window layout variables
	public static Point ControlLocation=new Point(10,20); // subject to adjustment
	public static int ControlWidth=580;
	public static int ControlHeight=352; // determined by trial/error.
	public static Dimension ControlDim1=new Dimension(ControlWidth,ControlHeight+58); // frame with scriptBundle
	public static Dimension ControlDim2=new Dimension(ControlWidth,ControlHeight); // frame without
	
	public static int PopupFrameWidth=ControlWidth; // for function, output, mobius frames, etc.
	public static int smallSide=190; // size of small canvasses in 'PackControl' frame
	// TODO: allow non-square?
	// Set preferred main canvas size; can be changed in config file.
	// Caution: current size is maintained in 'MainFrame.canvasDim'
	private static Dimension actCanvasDim=new Dimension(600,600); 
	public static int MinActiveSize=200;
	public static int MaxActiveSize=1800;
	
	// Set preferred paired canvas sizes; can be changed in config file
	// Caution: current size is maintained in 'PairedFrame.canvasDim'
	private static Dimension pairCanvasDim=new Dimension(400,400);
	public static int MinMapSize=200;
	public static int MaxMapSize=1800;
	
	// Script font size can be changed by this increment
	public static int fontIncrement=0;
	
	// need buffer on top/bottom to accommodate frame border and window taskbar
	public static int HeightBuffer=60;
	
	// various panels in the top frame
	private static JPanel frameButtonPanel;
	public static ScriptBundle scriptBar;
	public static VertScriptBar vertScriptBar; // moves between active/paired frames
	public static MYTOOLHandler movableToolHandler; // moves between active/paired frames

	public static JPanel mbarPanel;   // main MenuBar
	public static SmallCanvasPanel smallCanvasPanel; // pictures of 3 packings

	// MyTool stuff: handlers,
	public static MYTOOLHandler userHandler;
	public static SCRIPTHandler scriptToolHandler;
	
	// files for 'main' and 'basic' 'MyTool' bars
	public File mainMyTFile; 
	public File mainCursorFile;
	public File basicMyTFile;

	// default icon "?"; see 'default_icon.jpg' 
	public static CPIcon defaultCPIcon;

	// Cursor
	public static CursorCtrl cursorCtrl;
	
	// msgButton
	public JButton msgButton;
	public static MessageHover msgHover;
	
	// Various auxiliary frames
	private AboutDialog aboutDialog;
	public static CPPreferences preferences; // user preferences
	public static JFrame prefFrame; 
	public static AboutFrame aboutFrame;
	public static HelpHover helpHover;
	public static ScriptHover scriptHover;
	public static MobiusFrame mobiusFrame;
	public static FunctionHover functionPanel;
	public static BrowserFrame browserFrame;
	public static OutputFrame outputFrame;
	public static TabbedPackDataHover packDataHover; 
	public static ScreenCtrlFrame screenCtrlFrame;
	public static ShellManager shellManager;
	public static MyConsole consoleActive;
	public static MyConsole consolePair;
	public Point framesPoint;
	public boolean browserStart;  // if true, open browser with "Welcome" at start
	
	// console
	public static MyConsole consoleCmd;
	
	// Constructor
	public PackControl() {
		socketActive=true;  // means that socket server will be started
		cpSocketPort=3736;
		cpSocketHost=null;
		cpMultiServer=null;
		socketSources=new Vector<SocketSource>();

		MapCanvasMode=false; // default to main canvas only
		AdvancedMode=true; // default to 'advanced' mode (so 'frame' shows)
		prefFrame=null; // only when called
		NUM_PACKS=3; // number of packings to maintain
		cpTimer=new CPTimer();
		defaultCPIcon=new CPIcon("GUI/default_icon.jpg");
	}
	
	/**
	 * This actually starts PackControl: initiate preferences,
	 * start C libraries, create the pack[] vector of packings,
	 * create the interfaces frames and windows, pack 'frame',
	 * call initGUI, etc.
	 */
	public void initPackControl() {
		// look for preference directory/file in 'homeDirectory/myCirclePack';
		try {
			File prefDir = new File(CPFileManager.HomeDirectory, "myCirclePack");
			browserStart = false;
			if (!prefDir.exists()) {
				browserStart = true;
				prefDir.mkdir();
			}
			if (prefDir.exists()) {
				String myPrefFilename = "cpprefrc";
				CPBase.CPprefFile = new File(prefDir, myPrefFilename);

				// TODO: would rather read this from default file in "/Resources",
				// but ran into problems.
				if (!CPBase.CPprefFile.exists()) {
					BufferedWriter writer = new BufferedWriter(new FileWriter(CPBase.CPprefFile));
					writer.write("PACKINGS_DIR packings/");
					writer.newLine();
					writer.write("SCRIPT_DIR scripts/");
					writer.newLine();
					writer.write("TOOL_DIR mytools/");
					writer.newLine();
					writer.write("EXTENDER_DIR myCirclePack/bin");
					writer.newLine();
					writer.write("PRINT_COMMAND lpr");
					writer.newLine();
//				writer.write("POSTSCRIPT_VIEWER gv");
//				writer.newLine();
					writer.write("WEB_URL_FILE web_URLs");
					writer.newLine();
					writer.write("XMD_URL_FILE xmd_URLs");
					writer.newLine();
					writer.write("ACTIVE_CANVAS_SIZE 650");
					writer.newLine();
					writer.write("PAIR_CANVAS_SIZE 400");
					writer.newLine();
					writer.write("FONT_INCREMENT 0");
					writer.newLine();
					writer.flush();
					writer.close();
				}
			}
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("failed to find/create 'myCirclePack/cpprefrc'");
		}

		preferences = new CPPreferences(); // pref stuff set here

		// Init 'genericMain' C code for JNI calls to 'HeavyC_lib'
		// TODO: how to catch exception if C calls fail in the native library
		if (CPBase.attachCcode) {
			try {
//				System.out.println(System.getProperty("java.library.path"));
//				java.io.File f=new File(System.getProperty("java.library.path")+
//						File.separatorChar + "libHeavyC_lib.so");
//				System.out.println(System.getenv().toString());

				new JNIinit(); // try to start 'DelaunayBuild', 'SolverFunction' libs

			} catch (Exception ex) {
				System.err.println("Exception starting some shared C library;"
						+ " this does not necessarily affect the use of 'CirclePack'");
				System.err.println("System java library path is :" + System.getProperty("java.library.path"));
			} catch (Error e) {
				System.err.println("Error in starting some shared C library;"
						+ " this does not necessarily affect the use of 'CirclePack'");
			}
		}

		// Create the packing data memory storage areas
		pack = new CPScreen[NUM_PACKS];
		for (int i = 0; i < NUM_PACKS; i++) {
			pack[i] = new CPScreen(i);
			pack[i].circle.setParent(pack[i]);
			pack[i].face.setParent(pack[i]);
			pack[i].edge.setParent(pack[i]);
			pack[i].trinket.setParent(pack[i]);
			pack[i].realBox.setParent(pack[i]);
			pack[i].sphView.setParent(pack[i]);
		}

		// Create the screen thumbnail panel
		smallCanvasPanel = new SmallCanvasPanel(pack);

		// Create the 'ShellManager' to handle history
		shellManager = new ShellManager();

		// Set up for command parsing and shell
		trafficCenter = new TrafficCenter();

		// Set up progress indicator
		runSpinner = new OwlSpinner();

		// create the command and other consoles
		consoleCmd = new MyConsole(CmdSource.PACKCONTROL, "packcontrol");
		consoleCmd.initGUI(ControlDim1.width);
		consoleActive = new MyConsole(CmdSource.ACTIVE_FRAME, "activeframe");
		consoleActive.initGUI(MainFrame.getCanvasDim().width);
		consolePair = new MyConsole(CmdSource.PAIR_FRAME, "pairframe");
		consolePair.initGUI(PairedFrame.getCanvasDim().width);

		// start various managers
		fileManager = new CPFileManager(); // initiates/manages directories
		fileManager.setCurrentDirectory(directory); // from command-line arguments
		postManager = new PostManager(); // manages postscript output
		canvasRedrawer = new CanvasReDrawManager(0); // initially pack[0] is active

		// start canvas mode controller
		cursorCtrl = new CursorCtrl();

		// Note: 'basic.myt' and 'main.myt' are loaded from 'Resources'
		// if local user versions have not been found
		mainMyTFile = CPFileManager.getMyTFile("main.myt");
		basicMyTFile = CPFileManager.getMyTFile("basic.myt");

		// Start the active canvas window (and listener for size changes?)
		activeFrame = new MainFrame(pack[0], mainMyTFile, mainCursorFile);

		// create the script stuff: manager, bar, frame, vertical bar
		scriptManager = new ScriptManager();
		scriptBar = new ScriptBundle();
		// STACK
//		scriptBar.setPreferredSize(new Dimension(-1,60));
//		scriptBar.setMinimumSize(new Dimension(PackControl.ControlDim.width-2,60));
//		scriptBar.setMaximumSize(new Dimension(PackControl.ControlDim.width-2,60));
//		scriptBar.setPreferredSize(new Dimension(PackControl.ControlDim.width-2,60));

		scriptHover = new ScriptHover();

		scriptToolHandler = scriptHover.getHandler();
		// 'VertScriptBar' moves between 'MainFrame' and 'PairedFrame'
		vertScriptBar = new VertScriptBar();

		// Create row of buttons to open frames: messages, browser, function
		// frame, configuration, Mobius, etc., ssPanel.
		frameButtonPanel = buildFrameButtons();
		frameButtonPanel.setPreferredSize(new Dimension(ControlDim1.width - 2, 28));
		consoleCmd.setMouseLtnr();
		consoleActive.setMouseLtnr();
		consolePair.setMouseLtnr();

		// There is a basic tool set in 'basic.myt' at startup. I expect
		// others to be read in and replace it at bottom right.
		userHandler = new MYTOOLHandler(basicMyTFile);
		userHandler.toolBar.setToolTipText("toolbox of basic commands ");

		Vlink = (NodeLink) null;
		Flink = (FaceLink) null;
		Elink = (EdgeLink) null;
		Tlink = (TileLink) null;
		Glink = (GraphLink) null;
		Zlink = (PointLink) null;
		Blink = (BaryLink) null;
		ClosedPath = (Path2D.Double) null;
		gridLines = new Vector<BaryCoordLink>(1);
		streamLines = new Vector<BaryCoordLink>(1);
		Mob = new Mobius(); // start with identity Mobius transformation

		// location for 'activeFrame'; some aux frames need middle
		int xl = ControlLocation.x + 2 + ControlDim1.width;
		int yl = ControlLocation.y;
		int aS = 12;// (int)((double)PackControl.getActiveCanvasSize()/6.0);
		framesPoint = new Point(xl + aS, yl + aS);

		// start auxiliary frames/panels
		startFramesPanels();

		// Start the 'PackControl' GUI, adjust to display area
		initGUI();

		frame.pack();
		frame.setLocation(ControlLocation);
		if (browserStart) {
			browserFrame.setWelcomePage();
			browserStart = false;
		}

		// initial location of scriptFrame: bottom of 'frame' minus 'scriptBar'
		int high = frame.getHeight();
		scriptHover.XLoc = ControlLocation.x;
		scriptHover.YLoc = ControlLocation.y + high - 78;
//		scriptFrame.setLocation(,ControlLocation.y+high-78);
		vertScriptBar.scriptTools.add(scriptHover.scriptToolHandler.toolBar);
		frame.setVisible(false);
		resetDisplay(-1.0);

	}
	
	/**
	 * Initiate the GUI for 'frame', the principal PackControl JFrame; 
	 * other things have had to be started first.
	 */
	private void initGUI() {
		try {
			frame=new JFrame();
			Container pane=frame.getContentPane();
			frame.setTitle("CirclePack (dir; "+CPFileManager.CurrentDirectory+")");
			frame.setIconImage(CPIcon.CPImageIcon("GUI/CP_Owl_22x22.png").getImage());
			frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			frame.addWindowListener(new FAdapter());
			pane.setLayout(null); // hard code 
	
			int pcheight=0; // for positioning along the way
			pane.addFocusListener(this);
			{
				mbarPanel.setBounds(0,pcheight,ControlWidth,30);
				pcheight+=32;
				pane.add(mbarPanel);
			}
			{
				smallCanvasPanel.setBounds(0,pcheight,ControlWidth,smallSide+20);
				pcheight+=smallSide+21;
				pane.add(smallCanvasPanel);
			}
			{
				userHandler.toolBar.setBounds(0,pcheight,ControlWidth,34);
				pcheight+=36;
				pane.add(userHandler.toolBar);
			}
			{
				consoleCmd.box.setBounds(0,pcheight,ControlWidth,44);
				pcheight+=46;
				pane.add(consoleCmd.box);
			}
			{
				frameButtonPanel.setBounds(0,pcheight,ControlWidth,24);
				pcheight+=26;
				pane.add(frameButtonPanel);
			}
			{
				scriptBar.setBounds(0,pcheight,ControlWidth,60);
				pcheight+=62;
				pane.add(scriptBar);
			}
			pane.setPreferredSize(ControlDim1);
			frame.pack();
			frame.setResizable(false);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Set/reset canvas sizes w.r.t. display size. 'PackControl'
	 * frame size is currently preset and fixed via 'ControlDim'. 
	 * 
	 * Find max dimensions of 'activeFrame' and 'mapPairFrame';
	 * e.g. fit in window, avoid getting blocked by taskbar,
	 * leave at least 100 pixels on left for 'PackControl'.
	 * If 'fracMax' is > 0, set size to fracMax*max (subject to our
	 * upper limits in 'PackControl'), else set to minimum of the 
	 * max/current sizes.
	 * @param fracMax double: if > 0, then use this fraction of max size, 
	 * else use the lesser less of max and current.
	 */
	public void resetDisplay(double fracMax) {
		
		// what are actual display dimensions?
		PackControl.displayDimension=Toolkit.getDefaultToolkit().getScreenSize();
		
		// set active canvas size
		Dimension dimMax=activeFrame.getMaxDim(PackControl.displayDimension);
		int maxSize=activeFrame.getWouldBeSize(dimMax.height);
		int curSize=MainFrame.getCanvasDim().height;
		int newSize=curSize;
		if (fracMax>0.0) {
			if (fracMax>1.0)
				fracMax=1.0;
			newSize=(int)(fracMax*(double)maxSize);
		}
		else if (maxSize<curSize)
			newSize=maxSize;
		activeFrame.layMeOut(new Dimension(newSize,newSize));

		// set pair canvas size
		dimMax=mapPairFrame.getMaxDim(displayDimension);
		maxSize=mapPairFrame.getWouldBeSize(dimMax.height);
		newSize=curSize=PairedFrame.getCanvasDim().height;
		if (fracMax>0.0) {
			if (fracMax>1.0)
				fracMax=1.0;
			newSize=(int)(fracMax*(double)maxSize);
		}
		else if (maxSize<curSize)
			newSize=maxSize;
		mapPairFrame.layMeOut(new Dimension(newSize,newSize));

		// set active location
		int rightedge=frame.getLocation().x+frame.getWidth()+6;
		int X=PackControl.displayDimension.width-activeFrame.getWidth()-4;
		if (X>rightedge && rightedge>100) X=rightedge;
		int Y=PackControl.displayDimension.height-activeFrame.getHeight()-HeightBuffer;
		if (Y>0) Y=Y/2;
		else Y=2;
		activeFrame.setLocation(new Point(X,Y));
		
		// set pair locatiion
		X=PackControl.displayDimension.width-mapPairFrame.getWidth()-4;
		if (X>rightedge && rightedge>100) X=rightedge;
		Y=PackControl.displayDimension.height-mapPairFrame.getHeight()-HeightBuffer;
		if (Y>0) Y=Y/2;
		else Y=2;
		mapPairFrame.setLocation(new Point(X,Y));
	}
	
	/**
	 * Incoming script may change canvas layouts by changing
	 * 'MapCanvasMode' and 'AdvancedMode' choice.
	 */
	public void resetCanvasLayout() {
		// user 'level'? leave in advanced mode if already there.
		if (AdvancedMode)
			frame.setVisible(true);
		else
			frame.setVisible(false);
		
		// layout of initial frame
		if (MapCanvasMode) {
			scriptHover.scriptToolHandler.toolBar.
		    setPreferredSize(new Dimension(70,PairedFrame.getCanvasDim().width-30));
			mapPairFrame.layMeOut();
			mapPairFrame.pack();
			mapPairFrame.setVisible(true);
			activeFrame.setVisible(false);
		}
		else { 
			scriptHover.scriptToolHandler.toolBar.
		    setPreferredSize(new java.awt.Dimension(70,MainFrame.getCanvasDim().height-30));
			activeFrame.layMeOut();
			activeFrame.pack();
			activeFrame.setVisible(true);
			mapPairFrame.setVisible(false);
		}
		if (!AdvancedMode) {
			int X=20,Y=20;
			if (MapCanvasMode) {
				X=mapPairFrame.getLocation().x;
				Y=mapPairFrame.getLocation().y+80;
			}
			else {
				X=activeFrame.getLocation().x;
				Y=activeFrame.getLocation().y+80;
			}
			if (scriptManager.isScriptLoaded() && 
					scriptManager.scriptDescription!=null &&
					scriptManager.scriptDescription.trim().length()>0) {
				aboutFrame.openAbout(X,Y);
			}
			else {
				aboutFrame.setVisible(false);
			}
		}
	}
	
	/**
	 * Create auxiliary frames/panels: 'helpFrame, 'mapPairFrame',
	 * 'mobiusFrame','functionFrame','packDataFrame','mbarPanel',
	 * 'outputFrame','packdataFrame','screenCtrlFrame','messageFrame',
	 * 'browserFrame','PrefFrame'. 
	 */
	private void startFramesPanels() {
		Point mainUL=new Point(ControlLocation);
		int ptX=mainUL.x;
		int ptY=mainUL.y;
		
		aboutFrame=new AboutFrame();
		
		helpHover=new HelpHover("cp_help.info");
		
//		helpFrame = new HelpFrame("cp_help.info");
//		helpFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 
//		helpFrame.setLocation(ptX,ptY+ControlDim.height-60);
//		helpFrame.setVisible(false);
		
		
		
		mapPairFrame= new PairedFrame(0,1);
		mapPairFrame.setVisible(false);
	
		mobiusFrame=new MobiusFrame();
		mobiusFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mobiusFrame.setLocation(ptX+20,ptY+ControlDim2.height+20);
		mobiusFrame.setVisible(false);
		
		// AF: Initialize necessary arguments for BrowserFrame construction.
		IMessenger messenger = new IMessenger() {
			@Override
			public void sendDebugMessage(String message) {
				myDebugMsg(message);
			}
			@Override
			public void sendErrorMessage(String message) {
				myErrorMsg(message);
			}
			@Override
			public void sendOutputMessage(String message) {
				myMsg(message);
			}
		};
		
		String historyFile = preferences.getWebURLfile();
		if (historyFile.startsWith("~/"))
			historyFile = CPFileManager.HomeDirectory + File.separator + historyFile.substring(2);
		
		browserFrame = new BrowserFrame(messenger, historyFile);
		browserFrame.setLocation(ptX, ptY + ControlDim2.height + 90);
		browserFrame.setVisible(browserStart);

		outputFrame=new OutputFrame();
		outputFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		outputFrame.setLocation(framesPoint);
		outputFrame.setVisible(false);
		
		screenCtrlFrame=new ScreenCtrlFrame();
		screenCtrlFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		screenCtrlFrame.setLocation(framesPoint);
		screenCtrlFrame.setVisible(false);
	
		mbarPanel=OurMenuBar();	
		// STACK
//		mbarPanel.setMaximumSize(new Dimension(ControlDim.width,32));
//		mbarPanel.setPreferredSize(new Dimension(ControlDim.width,32));
//		mbarPanel.setBorder(new LineBorder(Color.orange,2,false));
	}
	
	/**
	 * Buttons for opening various support frames.
	 */
	public JPanel buildFrameButtons() {
		
		// Button to bring up the side-by-side canvasses
		JButton pairButton=new JButton("MapPair");
		pairButton.setFont(new Font(pairButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		pairButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (mapPairFrame.isVisible()) {
					mapCanvasAction(false);
				}
				else {
					mapCanvasAction(true);
				}
			}
		});

		// Button to bring up mobius frame
		JButton mobButton=new JButton("Mobius");
		mobButton.setFont(new Font(mobButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		mobButton.setToolTipText("Frame to manage Mobius transformation");
		mobButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (mobiusFrame.isVisible()) 
					mobiusFrame.setVisible(false); 
				else { 
					mobiusFrame.setVisible(true);
					mobiusFrame.setState(Frame.NORMAL);
				}
			}
		});

		// Button to bring up Browser
		JButton wwwButton=new JButton("Browser");
		wwwButton.setFont(new Font(wwwButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		wwwButton.setToolTipText("Open/Close web browser window");
		wwwButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (browserFrame.isVisible()) 
					browserFrame.setVisible(false); 
				else 
					browserFrame.setVisible(true);
			}
		});
		
		// Button to bring up function frame
		JButton ftnButton=new JButton("Function");
		ftnButton.setFont(new Font(ftnButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		functionPanel=new FunctionHover();
		ftnButton.addMouseListener(functionPanel);
		ftnButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (functionPanel.isLocked()) {
					functionPanel.lockedFrame.setVisible(false);
					functionPanel.loadHover();
					functionPanel.locked=false;
				}
				else {
					functionPanel.lockframe();
				}
			}
		});
		
		// Button to bring up configure window
		JButton configButton=new JButton("Configure");
		configButton.setFont(new Font(configButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		configButton.setToolTipText("Open Frame for Configuration settings");
		configButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (prefFrame!=null) {
					prefFrame.setVisible(false);
					prefFrame=null;
				}
				else { // recreate each time
					prefFrame=preferences.displayPreferencesWindow();
					prefFrame.setVisible(true);
				}
			}
		});

		// Button to bring up configure window
		msgHover=new MessageHover();
		msgButton=new JButton("Messages");
		msgButton.addMouseListener(msgHover);
		msgButton.setFont(new Font(msgButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		msgButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (msgHover.lockedFrame.isVisible())
					msgHover.lockedFrame.setVisible(false);
				
				// TODO disable to try to clear up timing problems
/*				if (msgHover.isLocked()) { 
					msgHover.lockedFrame.setVisible(false);
					msgHover.loadHover();
					msgHover.locked=false;
				}
*/				
				else 
					msgHover.lockframe();
				
				// TODO: may be unneeded, but for timing problems, keep in locked state
				msgHover.locked=true; 
			}
		});

		JPanel callStack=new JPanel(new GridLayout(1,6));
		callStack.add(msgButton);
		callStack.add(mobButton);
		callStack.add(ftnButton);
		callStack.add(configButton);
		callStack.add(wwwButton);
//		callStack.add(pairButton);
		
		return callStack;
	}
	
	/**
	 * 'OurMenuBar' is main menu bar in 'frame', the principal JFrame.
	 * @return JPanel
	 */
	public JPanel OurMenuBar() {
		JPanel ourBar = new JPanel();
		ourBar.setLayout(new FlowLayout(FlowLayout.LEFT));
//		ourBar.setBorder(new EtchedBorder(Color.blue,Color.blue));

		// Bring up help frame
		JButton button=new JButton("Help");
		button.addMouseListener(helpHover);
		button.setFont(new Font(button.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (helpHover.isLocked()) { 
					helpHover.lockedFrame.setVisible(false);
					helpHover.loadHover();
					helpHover.locked=false;
				}
				else 
					helpHover.lockframe();
			}
		});
		ourBar.add(button);

		// About
		button=new JButton("About");
		button.setFont(new Font(button.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (aboutDialog==null) // first time
					aboutDialog=new AboutDialog(frame);
				aboutDialog.setVisible(true);
			}
		});
		ourBar.add(button);
		
		button = new JButton("Pack Info");
		// Instantiate TabbedPackDataHover around its parent component.
		packDataHover = new TabbedPackDataHover(button);
		button.setFont(new Font(button.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// TODO: Might be nice to add a toggleLock() method to TabbedPackDataHover.
				if (packDataHover.isLocked()) packDataHover.setLocked(false);
				else packDataHover.setLocked(true);
			}
		});
		ourBar.add(button);
		
		// Exit button
		button=new JButton("Exit");
		button.setFont(new Font(button.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				queryUserForQuit();
			}
		});
		ourBar.add(button);
		MyTool mt=new MyTool(new CPIcon("main/main_screens.png"),
				null,"mainscreen",null,"Switch to Active pack, single screen mode",
				"MISC:",false,null,(PopupBuilder)null);
		mt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					TrafficCenter.cmdGUI("Map -x");
				} catch (Exception ex) {}
			}
		});
		ourBar.add(mt);
		mt=new MyTool(new CPIcon("main/mapping_pair.png"),null,
				"pairscreen",null,"Switch to dual screen mode",
				"MISC:",false,null,(PopupBuilder)null);
		mt.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					TrafficCenter.cmdGUI("Map -o");
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("Map -o failed "+ex.getMessage());
				}
			}
		});
		ourBar.add(mt);
		
		JButton owlProg=((OwlSpinner)runSpinner).getFrameButton();
		owlProg.setPreferredSize(new Dimension(24,24));
		owlProg.setToolTipText("Progress bar to show when busy");
		ourBar.add(owlProg);
		
		return ourBar;
		
	}
	
	/**
	 * Return pointer to currently active pack
	 * @return CPScreen
	 */
	public static CPScreen getActiveCPScreen() {
		return activeFrame.getCPScreen();
	}

	
	/**
	 * Get preferred height of active canvas, config file.
	 * Caution: this may not be current height in 'MainFrame.canvasDim'.
	 * @return int
	 */
	public static int getActiveCanvasSize() {
		return actCanvasDim.height;
	}

	/**
	 * Set startup canvas dimension (from config file)
	 * @param size int
	 */
	public static void setActiveCanvasDim(int size) {
		if (size<PackControl.MinActiveSize) size=PackControl.MinActiveSize;
		if (size>PackControl.MaxActiveSize) size=PackControl.MaxActiveSize;
		actCanvasDim=new Dimension(size,size);
	}
	
	/**
	 * Set startup paired canvas dimension (from config file)
	 * @param size int
	 */
	public static void setPairedCanvasDim(int size) {
		if (size<PackControl.MinMapSize) size=PackControl.MinMapSize;
		if (size>PackControl.MaxMapSize) size=PackControl.MaxMapSize;
		pairCanvasDim=new Dimension(size,size);
	}
	
	/**
	 * Get preferred height of paired canvasses.
	 * Caution: this may not be current height in 'PairedFrame.canvasDim'.
	 * @return int
	 */
	public static int getPairedCanvasSize() {
		return pairCanvasDim.height;
	}
	
	/**
	 * General font size for various windows can be adjusted by
	 * increments of the base size (generally 11). 
	 * @param size int, adjusted to range 0-6.
	 */
	public static void setFontIncrement(int size) {
		if (size<0) size=0;
		if (size>6) size=6;
		fontIncrement=size;
	}
	
	/**
	 * Get 'fontIncrement'
	 * @return int
	 */
	public static int getFontIncrement() {
		return fontIncrement;
	}
	
	
	/**
	 * switch the currently active pack and update various
	 * settings: e.g., mainFrame pack, small canvas border
	 * color, mainFrame modes, various display panels,
	 * Mobius panel, etc. 
	 * @param packnum int, 
	 */
	public static void switchActivePack(int packnum) {
		int old_pack = activeFrame.getActivePackNum();
		if (packnum<0 || packnum>2 || old_pack==packnum) return;
		smallCanvasPanel.changeActive(packnum);
		canvasRedrawer.changeActive(packnum);
		screenCtrlFrame.displayPanel.update(old_pack,packnum);
		screenCtrlFrame.setTitle("CirclePack Screen Options, p"+packnum);
		outputFrame.outPanel.update(old_pack);
		activeFrame.setCPScreen(CPBase.pack[packnum]);
		activeFrame.activeScreen.setDefaultMode();
		activeFrame.updateTitle();
		activeFrame.activeScreen.repaint();
		screenCtrlFrame.screenPanel.setSliders();
		mapPairFrame.colorBorders();
		mobiusFrame.sidePairHandler.changeActivePack();
		mobiusFrame.repaint();
		activeFrame.repaint();
		
		// AF: Send the new pack data to the pack info frame.
		packDataHover.update(activeFrame.getPackData());
	}
	
	/** 
	 * Append given string to 'scratch' text area
	 * @param s String
	 */
	public static void displayScratch(String s) {
		try {
			MessageHover.scratchArea.append(s + " ");
		} catch (NullPointerException e) {
		}
	}

	/**
	 * Check how many of the key windows are visible (even if
	 * iconified). When closing windows, need to make sure that
	 * at least one gives access to CirclePack functionality.
	 * The key windows are: active, advanced, mappair, message. 
	 * @return int
	 */
	public static int anybodyOpen() {
		int count=0;
		if (activeFrame.isVisible())
			count++;
		if (mapPairFrame.isVisible())
			count++;
		if (msgHover.isLocked())
			count++;
		if (frame.isVisible())
			count++;
		return count;
	}
	
	/**
	 * On true, open the "mapping pair" canvasses and close 'mainFrame',
	 * else vice verse.
	 * @param activate boolean
	 */
	public static void mapCanvasAction(boolean activate) {
		if (activate) {
			if (!MapCanvasMode) { // map pair not open? open
				MapCanvasMode=true;
				activeFrame.setVisible(false);
				vertScriptBar.swapVertScriptBar();
			}
			mapPairFrame.setVisible(true);
//			mapPairFrame.repaint();
		}
		else {
			MapCanvasMode=false;
			mapPairFrame.setVisible(false);
			vertScriptBar.swapVertScriptBar();
			activeFrame.setVisible(true);
		}
		// TODO: Slight glitch: suppose map pair window is active but iconified,
		//       we deactive (go to main window), then go back to map pair, then
		//       map pair remains iconified, despite the command to go to NORMAL.
		activeFrame.setState(Frame.NORMAL);
		mapPairFrame.setState(Frame.NORMAL);
//		activeFrame.repaint();
		return;
	}

	/**
	 * Open the side-by-side canvasses
	 * @param pnum int
	 * @param qnum int
	 */
	public static void openMap(int pnum,int qnum) {
		PackControl.mapPairFrame.setDomainNum(pnum);
		PackControl.mapPairFrame.setRangeNum(qnum);
		mapCanvasAction(true);
	}
		
	// ================== abstract methods required by CPBase =============

	/**
	 * put message in shell
	 * @param msgstr String
	 */
	public void myMsg(String msgstr) {
		shellManager.recordMsg(msgstr);
		// clear MyConsole displays
		consoleCmd.dispConsoleMsg("");
		consoleActive.dispConsoleMsg("");
		consolePair.dispConsoleMsg("");
	}

	/**
	 * put error message in shell
	 * @param msgstr String
	 */
	public void myErrorMsg(String msgstr) {
		consoleCmd.dispConsoleMsg(msgstr);
		shellManager.recordError(msgstr);
	}

	/**
	 * put debug message in shell
	 * @param msgstr String
	 */
	public void myDebugMsg(String msgstr) {
		shellManager.recordDebug(msgstr);
	}

	/**
	 * Return active pack number
	 * @return int
	 */
	public int getActivePackNum() {
		return activeFrame.getActivePackNum();
	}
	
	/**
	 * Returns pointer to active packing 'PackData'
	 * @return PackData
	 */
	public PackData getActivePackData() {
		return activeFrame.getPackData();
	}

	// done with abstract methods
	
	/** 
	 * Open a command socket at a given port, local host.
	 * In future, may change host, may search for unused port,
	 * etc.
	 * 
	 * @param port int
	 * @return int: 0 on error, else port number
	 */
	public static int startCPSocketServer(int port) {
		try {
			cpSocketHost=InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) {
			System.err.println("Could not find the local host for the socket ");
			e.printStackTrace();
			cpSocketHost=null;
			cpSocketPort=port;
		}
		cpMultiServer = new CPMultiServer(port);
		try {
			cpMultiServer.start();
		} catch (Exception ex) {
			System.err.println("Failed to start cpMultiServer: "+ex.getMessage());
		}
		CirclePack.cpb.msg("CirclePack has a socket server: host = "+
				cpSocketHost+", port = "+port);
		return cpSocketPort=port;
	}
	
	/**
	 * Prompt the user to exit CirclePack. If the user confirms the exit,
	 * CirclePack will exit. The user may also cancel the process, in which
	 * case this function will return. If the current script has not been
	 * saved since the last edit, the user will be prompted to save the
	 * script.
	 */
	public void queryUserForQuit() {
		if (scriptManager.hasChanged) {
			// Should the script be saved?
			Object[] options = {"Save", "Discard", "Cancel"};
			String message = "Script contents may have changed. Would you like to save before exiting?";
			int result = JOptionPane.showOptionDialog(null, message, "Save?", JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

			if (result == JOptionPane.YES_OPTION) {
				File scriptFile;
				if ((scriptFile = FileDialogs.saveDialog(FileDialogs.SCRIPT, true)) != null) {
					scriptManager.tnWriter.Write_from_TN(scriptFile);
					if (scriptFile.getName().equals("new_script.xmd")) {
						CirclePack.cpb.errMsg("Not allowed to save as \"new_script.xmd\".");
						return;
					}
					
					try {
						ScriptBundle.m_locator.add2List(scriptFile.getCanonicalPath(), true);
					} catch (Exception e) {}
				} else return;
				
				scriptManager.hasChanged = false;
				scriptHover.scriptTitle(scriptManager.scriptName, false);
				exit();
			} else if (result == JOptionPane.NO_OPTION) exit();
		} else {
			// The script hasn't changed.
			int result = JOptionPane.showConfirmDialog(null, "Exit CirclePack?", "Exit?", JOptionPane.YES_NO_OPTION);
			if (result == JOptionPane.YES_OPTION) exit();
		}
	}
	
	/**
	 * Gracefully exit CirclePack.
	 */
	protected void exit() {
		// AF: Dispose of all frames, at which point Swing will exit.
		Frame[] frames = Frame.getFrames();
		for (Frame frame : frames) frame.dispose();
		
		/*
		 * AF: If any user threads need to be notified that they should exit when
		 * possible, do it here. The JVM will not exit until all user threads have
		 * exited. Generally, you should avoid this situation by refactoring
		 * long-running threads as daemon threads, and have those daemon threads
		 * spawn short-running user threads to accomplish tasks that should not
		 * be interrupted.
		 */
	}
 
	// MouseMotionListener 
	public void mouseDragged(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {
		consoleCmd.cmdline.requestFocusInWindow(); // move focus to command line
	}
	public void mouseExited(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {}
		
	public void focusLost(FocusEvent evt) {
		debugFocusMsg("lost focus",evt);
	}
	public void focusGained(FocusEvent evt) {
		//  switch focus to cmdline always
		consoleCmd.cmdline.requestFocusInWindow();      
		debugFocusMsg("gained focus",evt);
	}
    public void debugFocusMsg(String prefix, FocusEvent e) {
    	System.out.println(prefix
                           + (e.isTemporary() ? " (temporary):" : ":")
                           +  e.getComponent().getClass().getName()
                           + "; Opposite component: " 
                           + (e.getOppositeComponent() != null ?
                              e.getOppositeComponent().getClass().getName() : "null")
    		       + "\n"); 
    }
    
    /**
     * for closing PackControl, the main frame, and exiting
     */
    class FAdapter extends WindowAdapter {
	   	public void windowClosing(WindowEvent wevt) {
	   		if (wevt.getID()==WindowEvent.WINDOW_CLOSING) {
				queryUserForQuit();
	   		}
	   	}
	}
	
    /**
     * for closing the activeFrame; just make it invisible
     */
	class WAdapter extends WindowAdapter {
	   	public void windowClosing(WindowEvent wevt) {
	   		if (wevt.getID()==WindowEvent.WINDOW_CLOSING)
	   			PackControl.activeFrame.setVisible(false);
	   	}
	}
	   
}

/**
A modal dialog that displays the "About" message and
waits for the user to click the Ok button.
*/
class AboutDialog extends JDialog {

	private static final long 
	serialVersionUID = 1L;
	
	public AboutDialog(JFrame owner)
	{
		super(owner, "About CirclePack Software", true);
		Container contentPane = getContentPane();

		// add HTML label to center

		contentPane.add(new JLabel(
				"<HTML><center><H3><I>"+PackControl.CPVersion+"</I></H3></center><HR>"
				+ "<center>By Ken Stephenson, copyright 1995--</center></HTML>"),
				BorderLayout.CENTER);

		// Ok button closes the dialog

		JButton ok = new JButton("Ok");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				setVisible(false);
			}
		});

		// add Ok button to southern border

		JPanel panel = new JPanel();
		panel.add(ok);
		contentPane.add(panel, BorderLayout.SOUTH);

		setSize(300, 150);
	}
	
}

