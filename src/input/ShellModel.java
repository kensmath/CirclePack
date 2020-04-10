package input;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import allMains.CPBase;
import circlePack.PackControl;
import frames.MessageHover;
import util.ResultPacket;

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
public abstract class ShellModel {

	public final int MAXHISTORY=2000;
	
	// command strings are kept in 'cmdHistory' for shell up/down action
	public static List<String> cmdHistory;
	public static int cmdHistoryIndex;

	// Constructor
	public ShellModel() {
		cmdHistory = new ArrayList<String>();
		cmdHistoryIndex=-1;
	}
	
	/**
	 * This was one timing bottleneck; messages and results of commands,
	 * errors, etc, are sent to and handled by separate thread.
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
		if (count!=null) {
			int n=count.intValue();
//			if (n<0) 
//				runHistory.append("> "+cmd+"    <font color=\"red\">["+n+"]</font><br>");
//			else 
//				runHistory.append("> "+cmd+"    ["+n+"]<br>");
		}
//		else
//			runHistory.append("> "+cmd+"<br>");
	}
	
	/**
	 * Add msg in blue
	 * @param msg
	 */
	public void recordMsg(String msg) {
		if (msg==null || msg.length()==0)
			return;
	}
	
	/**
	 * Add error msg in red
	 * @param errmsg String
	 */
	public void recordError(String errmsg) {
		if (errmsg==null || errmsg.length()==0)
			return;
	}
	
	/**
	 * Add debug msg in green
	 * @param errmsg String
	 */
	public void recordDebug(String bugmsg) {
		if (bugmsg==null || bugmsg.length()==0)
			return;
	}
	
}
