package cpTalk.sockets;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import allMains.CPBase;
import allMains.CirclePack;
import input.CommandStrParser;
import input.SocketSource;

/**
 * This processes incoming strings to CirclePack from a socket. 
 * To begin, we will assume these are strings of commands to be 
 * processed in the usual way. 
 * 
 * Eventually want meaningful feedback: certainly the usual number 
 * indicating successes, but should consider a full "packet" like
 * what now goes to the history file. This packet would go to the
 * source of the command (e.g. a socket) via some structured message
 * system (perhaps also to history). This way, someone like Ed Crane
 * could easily build their own shell to attach, maybe a "sage"
 * attachment, etc. 
 * 
 * May also want to introduce a flag indicating that the processing 
 * is for commands from a socket; then might optionally redirect output 
 * to the  socket -- e.g. a packing file. Alternately, can just report 
 * if data is saved in a file (which limits things to machines accessing 
 * the same file space). 
 * 
 * @author kens and Frank Martin
 *
 */
public class CPSocketProtocol {
	
	SocketSource socketSource;
	
	public CPSocketProtocol(SocketSource sS) {
		socketSource=sS;
	}

	public String processInput(String theInput) {
        String theOutput = null;
        
        // attempts to exit CirclePack; currently, this is not allowed
    	if (theInput.equalsIgnoreCase("quit") || theInput.equalsIgnoreCase("exit")) {
    		CirclePack.cpb.errMsg("Socket "+socketSource.sourceName+
    				" tried to close 'CirclePack'");
    		theOutput = "Clients are not allowed to close 'CirclePack'";
    	}

    	// request for CirclePack to read a packing from client
    	else if (theInput.equalsIgnoreCase("PutPack")) {
    		CirclePack.cpb.getActivePackData().readpack(socketSource.in, "inputStream");
    		if (CirclePack.cpb.getActivePackData().getDispOptions!=null)
    			CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(),"disp -wr");
    		theOutput = "read Packing succesful";
    	}
        // request for CirclePack to send a packing to client
    	else if (theInput.equalsIgnoreCase("GetPack")) {
    		try {
    			BufferedWriter packout = new BufferedWriter(
    				new OutputStreamWriter(socketSource.socket.getOutputStream()));
    			// 95 says that we want to write the flowers, radii and centers
    			CirclePack.cpb.getActivePackData().writePack(packout, 95, false);
    			theOutput = "send packing successful";
    		} catch(Exception ex) {
    			CirclePack.cpb.errMsg("error in sending packing to socket");
    		}
    	}       
        
        // default to processing as CirclePack commands
    	else {
    		int result=CPBase.trafficCenter.parseWrapper(theInput,
    				CirclePack.cpb.getActivePackData(),true,true,0,null);
    		theOutput="cmd result: "+result;
    	}
        
    	// send the output to the socket
        return theOutput;
	}
	
}
