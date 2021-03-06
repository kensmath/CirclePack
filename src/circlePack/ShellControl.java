package circlePack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import cpTalk.sockets.CPMultiServer;
import input.CPFileManager;
import input.SocketSource;
import packing.PackData;
import panels.CPPreferences;
import panels.CPScreen;
import util.CPTimer;

/**
 * Need a standalone version of CirclePack, to be run from a shell or remotely.
 * 
 * @author kens, May 2019
 *
 */

public class ShellControl extends CPBase {
	
	static Date date=new Date();
	public static String CPVersion= new String("CirclePack, "+circlePack.Version.version+", "+
			DateFormat.getDateInstance(DateFormat.MEDIUM).format(date));
	public static CPTimer cpTimer; // for crude timings
	public static CPPreferences preferences; // user preferences

	// Constructor
	public ShellControl() {
		socketActive=true;  // means that socket server will be started
		cpSocketPort=3736;
		cpSocketHost=null;
		cpMultiServer=null;
		socketSources=new Vector<SocketSource>();
		
		NUM_PACKS=3; // number of packings to maintain
		cpTimer=new CPTimer();
	}
	
	/**
	 * This actually starts ShellControl: initiate preferences,
	 * start C libraries, create the pack[] vector of packings,
	 */
	public void initShellControl() {
		// look for preference directory/file in 'homeDirectory/myCirclePack';
		try {
			File prefDir = new File(CPFileManager.HomeDirectory, "myCirclePack"); // .circlepack");
			if (!prefDir.exists()) {
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
					writer.write("EXTENDER_DIR myCirclePack/bin/");
					writer.newLine();
					writer.write("PRINT_COMMAND lpr");
					writer.newLine();
//				writer.write("POSTSCRIPT_VIEWER gv");
//				writer.newLine();
					writer.write("WEB_URL_FILE web_URLs/");
					writer.newLine();
					writer.write("XMD_URL_FILE xmd_URLs/");
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
		
		runSpinner=new ShellSpinner();
	}
	
	// ================== abstract methods required by CPBase =============

	/** do not instantiate 'frame'
	 */
	public boolean startHead() {
		return false;
	}
		
	/**
	 * @param msgstr String
	 */
	public void myMsg(String msgstr) {
		System.out.println(msgstr);
	}

	/**
	 * @param msgstr String
	 */
	public void myErrorMsg(String msgstr) {
		System.err.println(msgstr);
	}

	/**
	 * put debug message in shell
	 * @param msgstr String
	 */
	public void myDebugMsg(String msgstr) {
		System.err.println(msgstr);
	}
	
	/*
	 * TODO: have to fix this
	 */
	public PackData getActivePackData() {
		return null;
	}
	
	/*
	 * TODO: have to finish this
	 */
	public int getActivePackNum() {
		return 0;
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
		
}
