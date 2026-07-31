package cpTalk.sockets;

/**
 * A socketServer for CirclePack which should allow multiple clients
 * to initiate socket connections, setting up each with its own thread.
 */

import input.SocketSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class CPMultiServer extends Thread {

		ServerSocket serverSocket = null;
		public boolean listening = true;
		int port;

		public CPMultiServer(int prt) {
			port=prt;
			
			/*
			 *  AF: This is now a daemon thread and not a user thread. The difference is
			 *  that Java will kill all daemon threads and exit if only daemon threads are
			 *  left running. If this were left as a user thread, CirclePack would not exit
			 *  correctly when Swing exited, as the application would still have this
			 *	user thread running.
			 */
			setDaemon(true);
		}

		/**
		 * Stops the accept loop and closes the listening socket so the port
		 * is released immediately, rather than relying on the OS to clean
		 * it up whenever this daemon thread eventually dies. Safe to call
		 * even if the server never finished starting up.
		 */
		public void shutdownServer() {
			listening = false;
			try {
				if (serverSocket != null && !serverSocket.isClosed()) {
					serverSocket.close(); // unblocks a pending accept() below
				}
			} catch (IOException e) {
				// closing anyway - nothing to do with this
			}
		}

		public void run() {
			try {
				// Built unbound, then SO_REUSEADDR set before bind(), so a
				// port left in TCP's post-close TIME_WAIT state from a prior
				// run doesn't cause "Address already in use" on restart.
				serverSocket = new ServerSocket();
				serverSocket.setReuseAddress(true);
				serverSocket.bind(new InetSocketAddress(port));
			} catch (IOException e) {
				System.err.println("Could not listen on port: "+port);
				try {
					serverSocket=new ServerSocket(0);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					System.err.println("Seemed to be no free port");
					System.exit(1);
				}
			}

			// Server waits for clients to attach
			try {
				while (listening) {
					Socket newClient=serverSocket.accept();
					new SocketSource(newClient).run();
				}
				serverSocket.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
}
