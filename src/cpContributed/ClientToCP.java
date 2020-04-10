package cpContributed;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

/**
 * 
 * @author Frank Martin (Freiberg) started this, 2012
 *
 */
public class ClientToCP implements Runnable
{
	// socket to CP
	Socket sock = null;
    PrintWriter out;
    BufferedReader in;
    // name of the client
    String client_name;
    // determine if the socket has started
    boolean isRun = false;
    // if true, we are waiting for a packing
    boolean isGetPack = false;
    // not needed anymore
    //boolean isCmd = false;
    // run a thread which listen to answers from CP
    Thread runThread = null;
    // variable where the packing is stored
    // it is stored as a double vector with the form [num_vert, alpha, beta, gamma, flowers, radii, centers]
    double[] pack = null;
    
    public ClientToCP()
    {
    	// default constructor
    	sock = null;
    	out = null;
    	in = null;
    	isRun = false;
    	runThread = null;
    	client_name = "";
    }
    
    public ClientToCP(String host, int port, String name)
    {
    	// try to initiate a socket to CP
        sock = null;
        out = null;
        in = null;
        try
        {
            sock = new Socket(host, port);
            out = new PrintWriter(sock.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        }
        catch(UnknownHostException e)
        {
            System.out.println("Could not connect to " + host);
        }
        catch(IOException e)
        {
            System.out.println("No I/O for " + host);
        }
        
        // send CP the name of the client
        client_name = name;
        out.println("MYNAME " + name); 
        
        // start listening to CP
        isRun = true;
        runThread = new Thread(this);
        runThread.start();
    }
    
    public void run()
    {
        String msg = null;
        while(isRun && in != null) 
        {// the thread is running and we have an inputstream to CP
            try
            {
            	// read from CP
                msg = in.readLine();
                //System.err.println(msg);
                
                if (msg.startsWith("NODECOUNT") && isGetPack)
                {// get a packing from CP
                	String str = msg;
                	boolean isNodecount = false;
                	boolean isAlphaBetaGamma = false;
                	boolean isFlower = false;
                	boolean isRadii = false;
                	boolean isCenter = false;
                	int num_vert = -1;
                	StringTokenizer tok = new StringTokenizer(str);
                	String actToken = "";
                	
                	int k = 0;
                	do
                	{// read the packing from CP
                		while(tok.hasMoreElements())
                		{
                			actToken = tok.nextToken();
                			actToken = actToken.replace(",", ".");
                			
                			if (actToken.equals("NODECOUNT:"))
                			{
                				isNodecount = true;
                				isAlphaBetaGamma = false;
                            	isFlower = false;
                            	isRadii = false;
                            	isCenter = false;
                			}
                			else if (actToken.equals("ALPHA/BETA/GAMMA:"))
                			{
                				isNodecount = false;
                				isAlphaBetaGamma = true;
                            	isFlower = false;
                            	isRadii = false;
                            	isCenter = false;
                			}
                			else if (actToken.equals("FLOWERS:"))
                			{
                				isNodecount = false;
                				isAlphaBetaGamma = false;
                            	isFlower = true;
                            	isRadii = false;
                            	isCenter = false;
                			}
                			else if (actToken.equals("RADII:"))
                			{
                				isNodecount = false;
                				isAlphaBetaGamma = false;
                            	isFlower = false;
                            	isRadii = true;
                            	isCenter = false;
                			}
                			else if (actToken.equals("CENTERS:"))
                			{
                				isNodecount = false;
                				isAlphaBetaGamma = false;
                            	isFlower = false;
                            	isRadii = false;
                            	isCenter = true;
                			}
                			else if (actToken.equals("PACKNAME:"))
                			{
                				isNodecount = false;
                				isAlphaBetaGamma = false;
                            	isFlower = false;
                            	isRadii = false;
                            	isCenter = false;
                			}
                			else if (actToken.equals("ANGLE_AIMS:"))
                			{
                				isNodecount = false;
                				isAlphaBetaGamma = false;
                            	isFlower = false;
                            	isRadii = false;
                            	isCenter = false;
                			}
                			else if (isNodecount)
                			{
                				num_vert = Integer.parseInt(actToken);
                				pack = new double[100 * num_vert];
                				pack[k++] = num_vert;
                				
                				isNodecount = false;
                			}
                			else if (isAlphaBetaGamma)
                				pack[k++] = Integer.parseInt(actToken);
                			else if (isFlower)
                				pack[k++] = Integer.parseInt(actToken);
                			else if (isRadii)
                				pack[k++] = Double.parseDouble(actToken);
                			else if (isCenter)
                				pack[k++] = Double.parseDouble(actToken);
                		}
                		
                		// read next line until we get "END"
                		str = in.readLine();
                		tok = new StringTokenizer(str);
                	}
                	while (!str.equalsIgnoreCase("END"));
                	
                	// we are ready with reading the packing
                	isGetPack = false;
                }
                
                if (msg.equals("Client must start with 'MYNAME <name>'"))
                {
                	out.println("MYNAME " + client_name);
                }
                
                // no more needed
                /*if (msg.startsWith("cmd") && isCmd)
                {
                	// get an answer from CirclePack
                	isCmd = false;
                }*/
                
                if(msg.equalsIgnoreCase("bye"))
                {// CP has closed the connection to the client, so we are closing the connection too
                    closeConnection();
                }
            }
            catch(IOException e)
            {// error, so close the connection
                System.out.println("Could not read from CirclePack!");
                System.out.println("Close the connection!");
                closeConnection();
            }
        }
    }
    
    public void sendCommand(String cmd)
    {
        if(out != null)
        {// send a command to CP
            out.println(cmd);
        }
    }
    
    public void sendPacking(int flwrs[][], double rad[], double cz_re[], double cz_im[], 
    		int alpha, int beta, int gamma)
    {
        if(out != null)
        {// send a packing to CP
            int num_vert = rad.length;
            String flwr = "";
            out.println("PutPack");
            out.println("NODECOUNT: " + Integer.toString(num_vert));
            out.println("GEONETRY: euclidean");
            out.println("ALPHA/BETA/GAMMA: " + Integer.toString(alpha) + " " + 
            		Integer.toString(beta) + " " + Integer.toString(gamma));
            out.println("FLOWERS:");
            for(int i = 0; i < num_vert; i++)
            {
                flwr = "";
                for(int j = 0; j < flwrs[i].length; j++)
                {
                    if(flwrs[i][j] > 0)
                    {
                        flwr += Integer.toString(flwrs[i][j]);
                        flwr += " ";
                    }
                }

                out.println(flwr);
            }

            out.println("RADII:");
            for(int i = 0; i < num_vert; i++)
            {
                out.println(Double.toString(rad[i]));
            }

            out.println("CENTERS:");
            for(int i = 0; i < num_vert; i++)
            {
                out.println(Double.toString(cz_re[i]) + " " + Double.toString(cz_im[i]));
            }

            // say CP that we are finished with sending the data of the packing
            out.println("END");
        }
    }
    
    public void notifyGetPacking()
    {
        if(out != null)
        {// say CP that we want the actual packing
        	isGetPack = true;
        	pack = null;
            out.println("GetPack");
        }
    }
    
    public double[] getPacking()
    {// return the packing we've got from CP
    	if (isGetPack)
    	{// we are still reading the packing from CP
    		return null;
    	}
    	else // we have finished reading
    		return pack;
    }
    
    public void closeConnection()
    {// closes the connection to CP
        isRun = false;
        
        try
        {// closes the socket and the streams
            if(out != null)
            {
                out.close();
            }
            if(in != null)
            {
                in.close();
            }
            if(sock != null)
            {
                sock.close();
            }
            sock = null;
            out = null;
            in = null;

            if (runThread != null) // wait until the thread has stopped
            	runThread.join(2000);
            runThread = null;
        }
        catch(IOException e)
        {
            System.out.println("Could not close the connection!");
        }
        catch (InterruptedException e) 
        {
			System.out.println("Thread is not going to die!");
		}
    }
    
    public boolean reconnect(String host, int port, String name)
    {// reconnect to CP    	
    	// first close the connection
    	closeConnection();
    	
    	// start a new socket to CP
        try
        {
            sock = new Socket(host, port);
            out = new PrintWriter(sock.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        }
        catch(UnknownHostException e)
        {
            System.out.println("Could not connect to " + host);
            return false;
        }
        catch(IOException e)
        {
            System.out.println("No I/O for " + host);
            return false;
        }
        
        // send CP the name of the client
        client_name = name;
        out.println("MYNAME " + name);
        
        // start listening to CP
        isRun = true;
        runThread = new Thread(this);
        runThread.start();
    	
    	return true;
    }
    
    public boolean isConnected()
    {// check if we have a connection to CP
    	if (runThread == null)
    		return false;
    	else
    		return true;
    }
}
