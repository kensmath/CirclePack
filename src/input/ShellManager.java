package input;

import frames.MessageHover;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;

import util.ResultPacket;

import allMains.CPBase;
import circlePack.PackControl;

/**
 * This class manages the static command history buffer, 'runHistory'
 * I am trying to run the CirclePack GUT via a unix style 'shell'. 
 * Successive user commands and resulting messages and error messages 
 * are put into the 'runHistory' StringBuffer by this manager or some other
 * method. 
 * 
 * Details:
 * * 'runHistory' is in html format
 * * end of the header lines is "<body>\n"
 * * the StringBuffer has a max size to which text is 
 *   trimmed when necessary by removing full lines
 *   at the top, after <body>\n.
 * * Many commands (e.g. drawing ops, some tools) are
 *   not recorded in the history, so it can't duplicate
 *   a saved run.
 * 
 * @author kens
 *
 */
public class ShellManager {

	public static StringBuffer runHistory;
	public final int MAXHISTORY=20000;
	static int histHeadEnd; // keeps track of old end for pruning
	// TODO: in future, may want to store contents
	static BufferedWriter bufWriter; 
	
	// command strings are kept in 'cmdHistory' for shell up/down action
	public static List<String> cmdHistory = new ArrayList<String>(); 
	public static int cmdHistoryIndex;

	// Constructors
	public ShellManager() {
		runHistory=new StringBuffer("<html><head><style>{ font-family: courier; font-size: 8px; }CirclePack run, ID "+
				Integer.toString(CPBase.debugID)+"</style></head>\n");
		runHistory.append("<body>\n");
		runHistory.append("History of commands and messages will be displayed here.\n");
		runHistory.append("<!--HEAD BDRY-->\n"); // top anchor string, may be needed to find head
		histHeadEnd=runHistory.length()+1;
		
		cmdHistoryIndex=-1;
	}
	
	/**
	 * Command execution passes info in 'ResultPacket'. 
     * @param rP ResultPacket
     * @param mycon MyConsole, which console issued this command?        
	 */
	public static void processCmdResults(ResultPacket rP,MyConsole mycon) {
		MyConsole myconsole=PackControl.consoleCmd; // default source
		if (mycon!=null) myconsole=mycon;
		int retCount=rP.cmdCount;
		PackControl.shellManager.recordCmd(rP.origCmdString,retCount);
		if (rP.msgs!=null) PackControl.shellManager.recordMsg(rP.msgs);
		if (rP.errorMsgs!=null) PackControl.shellManager.recordError(rP.errorMsgs);
		
		if (retCount<=0) { // error/err msg ?
			if (rP.errorMsgs!=null && rP.errorMsgs.trim().length()>0) 
				myconsole.dispConsoleMsg(rP.errorMsgs.trim());
		}

		// add command to history and update position pointer
		if (rP.memoryFlag) {
			cmdHistory.add(rP.origCmdString);
			// I think this used to keep one at location of history recall
//			if( histPos==cmdNum ) histPos++; 
		}

		myconsole.showCmdCount(retCount);

		MessageHover.updateShellPane();
	}
	
	/**
	 * Add command string and possibly return "count" from command
	 * execution.
	 * @param cmd
	 * @param count, gives (+-) success count as a string
	 */
	public void recordCmd(String cmd,Integer count) {
		if (cmd==null || cmd.length()==0)
			return;
		checkLength();
		if (count!=null) {
			int n=count.intValue();
			if (n<0) 
				runHistory.append("> "+cmd+"    <font color=\"red\">["+n+"]</font><br>");
			else 
				runHistory.append("> "+cmd+"    ["+n+"]<br>");
		}
		else
			runHistory.append("> "+cmd+"<br>");
	}
	
	/**
	 * Add msg in blue
	 * @param msg
	 */
	public void recordMsg(String msg) {
		if (msg==null || msg.length()==0)
			return;
		checkLength();
		runHistory.append("<font color=\"blue\"> "+msg+"</font><br>");
	}
	
	/**
	 * Add error msg in red
	 * @param errmsg String
	 */
	public void recordError(String errmsg) {
		if (errmsg==null || errmsg.length()==0)
			return;
		checkLength();
		runHistory.append("<font color=\"red\"> "+errmsg+"</font><br>");
	}
	
	/**
	 * Add debug msg in green
	 * @param errmsg String
	 */
	public void recordDebug(String bugmsg) {
		if (bugmsg==null || bugmsg.length()==0)
			return;
		checkLength();
		runHistory.append("<font XScolor=\"green\"> "+bugmsg+"</font><br>");
	}
	
	/**
	 * If 'runHistory' StringBuffer is too long, prune to about half.
	 *  (1) maintain top format lines
	 *  (2) delete to beginning of some command line
	 * 
	 */
	public void checkLength() {
		if (runHistory.length()>MAXHISTORY) {
			synchronized (runHistory) {
				int cutat = runHistory.indexOf("<br>>", MAXHISTORY/2);
				if (cutat > runHistory.length())
					runHistory.delete(histHeadEnd, cutat);
			}
		}
		// TODO: in future, may want to save deleted stuff in a file
	}
}
