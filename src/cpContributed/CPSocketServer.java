package cpContributed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import allMains.CPBase;
import allMains.CirclePack;
//import cpTalk.sockets.CPSocketProtocol;
import input.CommandStrParser;

/**
 * 
 * @author Frank Martin (Freiberg) started this, 2012
 *
 */
public class CPSocketServer extends Thread {
	
	 private Socket socket = null;
	 public String socketName; // TODO: need unique name

	 public CPSocketServer(Socket sckt) {
		super("CPSocketServer");
		this.socket = sckt;
		socketName=socket.getInetAddress().getHostName();
		CirclePack.cpb.msg("Socket connection from "+socketName);
	 }

	 public void run() {

		try {
		    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
		    out.flush();
		    BufferedReader in = new BufferedReader(
					    new InputStreamReader(
					    socket.getInputStream()));

		    String inputLine, outputLine;
		    //CPSocketProtocol cpSP = new CPSocketProtocol();

		    // watch for input, respond
		    do {
		    	inputLine = in.readLine();
		    	// FM
		    	if (inputLine == null)
		    	{// the client has closed the connection, so stop listening to the client
		    		outputLine = "bye";
		    	}
		    	else if (inputLine.equalsIgnoreCase("SendPack"))
		    	{// we receive a packing from the client
		    		CirclePack.cpb.getActivePackData().readpack(in, "inputStream");
		    		if (CirclePack.cpb.getActivePackData().getDispOptions!=null)
		    			CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(),"disp -wr");
		    		outputLine = "read Packing succesful";
		    	}
		    	else if (inputLine.equalsIgnoreCase("GetPack"))
		    	{// client wants the active packing; send it
		    		BufferedWriter packout = new BufferedWriter(
		    				new OutputStreamWriter(socket.getOutputStream()));
		    		// 95 says that we want to write the flowers, radii and centers
		    		CirclePack.cpb.getActivePackData().writePack(packout, 95, false);
		    		outputLine = "send Packing succesful";
		    	}
		    	else if (inputLine.equalsIgnoreCase("quit") || inputLine.equalsIgnoreCase("exit"))
		    	{// the client want to exit CirclePack but it has not the privilege to do this 
		    		out.println("You cannot exit CirclePack!");
		    		outputLine = "Client tried to close CirclePack!";
		    	}
		    	else
		    	{// we have a command which we execute and wait until CirclePack finished his work 
		    	 // and send then the answer
		    		int result = CPBase.trafficCenter.parseWrapper(inputLine,
		    				CirclePack.cpb.getActivePackData(), true, false, 0, null);
		            outputLine = "cmd result: " + result;
		    	}
		    	
		    	out.println(outputLine);

		    	// debug
System.err.println("server in: "+inputLine);
System.err.println("server out: "+outputLine);

		    } while (!outputLine.equalsIgnoreCase("bye"));

		    out.close();
		    in.close();
		    socket.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
	 }
}