package cpTalk.sockets;

/**
 * A socketServer for CirclePack which should allow multiple clients
 * to initiate socket connections, setting up each with its own thread.
 */

import allMains.CPBase;
import input.SocketSource;

import java.io.IOException;
import java.net.InetAddress;
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

		//AF>>>//
		/**
		 * Address to bind to. By default this is loopback-only
		 * (127.0.0.1), so only processes on this same machine can
		 * connect -- the socket protocol has no authentication and
		 * grants full CirclePack command execution to whoever can reach
		 * it, so there's no safe reason to expose it on the network
		 * unless the user explicitly opted in (see '-socket-remote'
		 * in 'CP_standalone'/'CirclePackMain').
		 * @param prt int
		 * @return InetSocketAddress
		 */
		private InetSocketAddress bindAddress(int prt) {
			if (CPBase.socketAllowRemote)
				return new InetSocketAddress(prt); // all interfaces
			return new InetSocketAddress(InetAddress.getLoopbackAddress(),prt);
		}
		//<<<AF//

		public void run() {
			try {
				// Built unbound, then SO_REUSEADDR set before bind(), so a
				// port left in TCP's post-close TIME_WAIT state from a prior
				// run doesn't cause "Address already in use" on restart.
				serverSocket = new ServerSocket();
				serverSocket.setReuseAddress(true);
				serverSocket.bind(bindAddress(port));
			} catch (IOException e) {
				System.err.println("Could not listen on port: "+port);
				try {
					//AF>>>//
					// Same binding policy as above, just on whatever free
					// port the OS hands back (port 0).
					serverSocket = new ServerSocket();
					serverSocket.setReuseAddress(true);
					serverSocket.bind(bindAddress(0));
					//<<<AF//
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
					//AF>>>//
					// Give each client its own daemon thread. This used to
					// call 'SocketSource.run()' directly, which executes
					// synchronously on *this* accept thread -- so a single
					// connected client (even one that just opens the
					// connection and never sends anything, since there's
					// no read timeout) blocked the server from accepting,
					// or even noticing, any other client. Despite the
					// class name, it was never actually serving multiple
					// clients concurrently.
					Thread clientThread=new Thread(new SocketSource(newClient),
							"CP-Socket-Client-"+newClient.getInetAddress().getHostAddress());
					clientThread.setDaemon(true);
					clientThread.start();
					//<<<AF//
				}
			} catch (IOException ioe) {
				//AF>>>//
				// 'shutdownServer()' closes 'serverSocket' to unblock a
				// pending accept() so this thread can end promptly; that
				// deliberately throws exactly this exception here. Only
				// report it if 'listening' is still true, meaning the
				// socket was closed some other, unexpected way.
				if (listening) {
					ioe.printStackTrace();
				}
				//<<<AF//
			} finally {
				//AF>>>//
				// Make sure the port is released either way -- normal
				// loop exit (rare race: 'listening' goes false between
				// iterations, no exception) or the accept()-unblocked
				// exception above both land here.
				try {
					if (serverSocket != null && !serverSocket.isClosed()) {
						serverSocket.close();
					}
				} catch (IOException e) {
					// closing anyway - nothing to do with this
				}
				//<<<AF//
			}
		}
}
