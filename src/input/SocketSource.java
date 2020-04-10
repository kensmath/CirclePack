package input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import allMains.CPBase;
import allMains.CirclePack;
import cpTalk.sockets.CPSocketProtocol;

/**
 * Create a socket source when some client attaches to the
 * CirclePack socket server.
 * @author kens
 *
 */
public class SocketSource extends CmdSource implements Runnable {
	
	public Socket socket = null;
	public PrintWriter out;
	public BufferedReader in;

	public SocketSource(Socket sokt) {
		super(CmdSource.CMD_SOCKET);
		socket=sokt;
		CirclePack.cpb.msg("Socket client from "+socket.getInetAddress().getCanonicalHostName());
	}
	
	// this runs in a thread that watches for input from the socket
	public void run() {

		 try {
			 CPBase.socketSources.add(this);
			 out = new PrintWriter(socket.getOutputStream(), true);
			 out.flush();
			 in = new BufferedReader(new InputStreamReader(
						    socket.getInputStream()));
			 
			 CPSocketProtocol cpSP = new CPSocketProtocol(this);

			 // watch for input, respond
			 String inputLine, outputLine;
			 boolean haveName=false;
			 do {
				 inputLine = in.readLine().trim();
				 if (inputLine == null) { // client closed connection; stop listening
					 outputLine = "bye";
				 }
				 
				 // demand a name before accepting other info
				 if (haveName) {
					 outputLine = cpSP.processInput(inputLine);
				 }
				 
				 else {
					 String name;
					 if (inputLine.startsWith("MYNAME") && inputLine.length()>7 &&
							 (name=inputLine.substring(7).trim()).length()>0) {
						 if (name.length()>25)
							 name.substring(0,25);
						 haveName=true;
						 String theName=new String(socket.getInetAddress().getCanonicalHostName()+
								 " "+name);
						 this.setName(theName);
						 outputLine=new String("Your name is '"+theName);
					 }
					 else 
						 outputLine="Client must start with 'MYNAME <name>'";
				 }

				 // debug
out.println(outputLine); // echo to client
System.err.println("server in: "+inputLine);
System.err.println("server out: "+outputLine);

			 } while (!outputLine.equalsIgnoreCase("bye"));

			 out.close();
			 in.close();
			 socket.close();
			 CPBase.socketSources.remove(this);
		 } catch (IOException e) {
			 e.printStackTrace();
		 }
	 }	
}
