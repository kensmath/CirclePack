package util;

import packing.PackData;

/**
 * An object for carrying results both ways in command string 
 * execution calls.
 * 
 * @author kstephe2
 *
 */
public class ResultPacket {

	public PackData packData;       // active packing (cmds can still override '-p')
	public String origCmdString;	// holds the ';' separated command string
	public String errorMsgs;		// accumulate error message strings, ';' separated
	public String msgs;				// accumulate message strings, ';' separated
	public int cmdCount;			// usual count of successful commands, negative if command fails
	public boolean memoryFlag;		// should the command be put in repeat memory
	public boolean interrupt;	    // set if 'break' command encountered (e.g., within 'for' loop)
//	public int recDepth;			// recursion depth
	
	// Constructor
	public ResultPacket(PackData p,String cmds) {
		packData=p;
		origCmdString=new String(cmds);
		errorMsgs=null;
		msgs=null;
		memoryFlag=false;
		interrupt=false;
		cmdCount=0;
	}
}
