package circlePack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import cpTalk.sockets.CPMultiServer;
import input.CPFileManager;
import input.SocketSource;
import packing.PackData;
import panels.CPPreferences;
import util.CPTimer;

/**
 * Need a standalone version of CirclePack, to be run from a shell 
 * or remotely. It will still generate images in a backing plane
 * so it can generate an output jpg. 
 * 
 * @author kens, May 2019
 *
 */

public class ShellControl extends CPBase {
	
	public static String CPVersion= new String("CirclePack, "+circlePack.Version.version+", "+
			DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date()));
	public static CPTimer cpTimer; // for crude timings
	public static CPPreferences preferences; // user preferences

	// Constructor
	public ShellControl() {
		socketActive=true;  // means that socket server will be started
		cpSocketHost=null;
		cpMultiServer=null;
		socketSources=new Vector<SocketSource>();
		
		NUM_PACKS=3; // number of packings to maintain
		FAUX_RAD=20000;
		cpTimer=new CPTimer();
	}
	
	/**
	 * This actually starts ShellControl: initiate preferences,
	 * start C libraries, create the packings[] array,
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
					writer.write("WEB_URL_FILE web_URLs/");
					writer.newLine();
					writer.write("SCRIPT_URL_FILE script_URLs/");
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

		// Create the packing data memory storage areas
		packings=new PackData[NUM_PACKS];
		for (int i = 0; i < NUM_PACKS; i++) {
			packings[i]=new PackData(i);
		}

		// TODO: If we needed an image to use, we would instantiate it here
		runSpinner=new ShellSpinner();
	}
	
	// ============= abstract methods required by CPBase =============

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
	
	public PackData getActivePackData() {
		return packings[activePackNum];
	}
	
	public int getActivePackNum() {
		return activePackNum;
	}
	
	/**
	 * Replace 'packings[pnum]' with new packing; old packing
	 * is generally orphaned.
	 * @param p PackData
	 * @param pnum int
	 * @param keepX boolean, keep current extenders
	 * @return -1 on error, else nodeCount
	 */
	public int swapPackData(PackData p,int pnum,boolean keepX) {
		if (p==null)
			return -1;
		
		// before replacing, handle extension
		if (keepX) { 
			p.packExtensions=packings[pnum].packExtensions;
			for (int x=0;x<p.packExtensions.size();x++)
				p.packExtensions.get(x).packData=p;
		}
		
//		CPBase.packings[pnum].cpDrawing=null; // detach from cpDrawing
//		p.cpDrawing=CPBase.cpDrawing[pnum]; 
//		p.cpDrawing.setPackData(p);
		
		CPBase.packings[pnum]=p; 
		return p.nodeCount;
	}
	
	// done with abstract methods
	
	public static void switchActivePack(int packnum) {
		int old_pack = activePackNum;
		if (packnum<0 || packnum>2 || old_pack==packnum) 
			return;
		activePackNum=packnum;
	}
			
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
