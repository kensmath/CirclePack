package input;

/**
 * This is an abstract class encapsulating the various mechanisms for
 * acquisition, execution, and reporting of CirclePack command strings
 * and for external data sockets. Various sources can be accommodated: 
 * 'MyConsole's, sockets, tools, 'fexec' files, etc.
 * (It will be introduced slowly, as time permits and features demand.)
 * @author kstephe2
 * December 2011.
 */
public class CmdSource {
	
	// These are 'CirclePack's own 'MyConsoles'
	public final static int PACKCONTROL=1; 
	public final static int ACTIVE_FRAME=2; 
	public final static int PAIR_FRAME=3; 
	public final static int MESSAGE_FRAME=4; 
	public final static int SHELLCONTROL=5; // for standalone 'ShellControl' version

	// For future use
	public final static int CMD_TOOL=5;
	public final static int CMD_FILE=7;
	
	// Sockets
	public final static int CMD_SOCKET=6; // may also pass data
	public final static int DATA_SOCKET=8; // purely data socket
	
	public int sourceType;      
	public String sourceName;	// Identify the source of the commands to be handled
	
	// Constructors
	public CmdSource(int type,String name) {
		sourceType=type;
		sourceName=name;
	}
	
	public CmdSource(int type) {
		sourceType=type;
		sourceName="not set";
	}
	
	/**
	 * Set the name of the source, often a window name for
	 * 'MyConsole's or a Inet address for sockets.
	 * @param name
	 */
	public void setName(String name) {
		sourceName=name;
	}

}
