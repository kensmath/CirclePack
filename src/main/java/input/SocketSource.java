package input;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

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

	//AF>>>//
	// Close an idle connection after this long so an abandoned or
	// stalled client can't hold a thread/socket open forever.
	private static final int IDLE_TIMEOUT_MS=30*60*1000; // 30 minutes
	//<<<AF//

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
			 //AF>>>//
			 // Don't let one connection sit open (and hold its thread)
			 // forever; an idle client gets disconnected instead.
			 socket.setSoTimeout(IDLE_TIMEOUT_MS);
			 //<<<AF//
			 out = new PrintWriter(socket.getOutputStream(), true);
			 out.flush();
			 in = new BufferedReader(new InputStreamReader(
						    socket.getInputStream()));

			 CPSocketProtocol cpSP = new CPSocketProtocol(this);

			 // watch for input, respond
			 String inputLine, outputLine;
			 boolean haveName=false;
			 do {
				 //AF>>>//
				 // Check for null (client closed the connection) before
				 // calling .trim() on it -- calling .trim() on null threw
				 // an uncaught NullPointerException here every time a
				 // client just disconnected without sending "bye" first.
				 inputLine = in.readLine();
				 if (inputLine == null) { // client closed connection; stop listening
					 outputLine = "bye";
					 continue;
				 }
				 inputLine = inputLine.trim();
				 //<<<AF//

				 // demand a name before accepting other info
				 if (haveName) {
					 outputLine = cpSP.processInput(inputLine);
				 }

				 else {
					 String name;
					 if (inputLine.startsWith("MYNAME") && inputLine.length()>7 &&
							 (name=inputLine.substring(7).trim()).length()>0) {
						 if (name.length()>25)
							 name=name.substring(0,25); // (was a no-op before: result was discarded)
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

		 } catch (SocketTimeoutException ste) {
			 //AF>>>//
			 // Expected once in a while -- an idle client, not an error.
			 CirclePack.cpb.msg("Socket client "+sourceName+" timed out (idle)");
			 //<<<AF//
		 } catch (IOException e) {
			 e.printStackTrace();
		 } finally {
			 //AF>>>//
			 // Clean up however the loop above ended (normal "bye",
			 // timeout, or some other IOException) so a connection can
			 // never leak a thread, an open socket, or a stale entry in
			 // 'CPBase.socketSources'.
			 try { if (out!=null) out.close(); } catch (Exception e) {}
			 try { if (in!=null) in.close(); } catch (Exception e) {}
			 try { if (socket!=null && !socket.isClosed()) socket.close(); } catch (Exception e) {}
			 CPBase.socketSources.remove(this);
			 //<<<AF//
		 }
	 }
}
