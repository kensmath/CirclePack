package cpTalk.sockets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

/**
 * Test Client for CirclePack sockets.
 * @author kstephe2
 *
 */
public class EchoTestClient extends JFrame implements WindowListener, 
ActionListener {

	private static final long 
	serialVersionUID = 1L;
	
	static JTextArea display;
	static JTextField userInput;
    static Socket echoSocket = null;
    static PrintWriter out = null;
    static BufferedReader in = null;
    static String inMsg;
	
    public static void main(String[] args) throws IOException {
        	new EchoTestClient("Socket Test Client");
    }
    
    // Constructor
	public EchoTestClient(String title) {
		super(title);
        String host=null;
        int port=3736;

    	// set up the client socket
        try {
        	// TODO: determine "host" CirclePack is running on; 'kstephe2.utk.edu'
//        	host=InetAddress.getLocalHost()();
        	host=InetAddress.getByName("agnesi.math.utk.edu").getCanonicalHostName();
        	

        	// set up socket in/out
            echoSocket = new Socket(host, 3736);
            out = new PrintWriter(echoSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(
                                        echoSocket.getInputStream()));
            
            // client is forced to give name: 'MYNAME' is keyword.
            out.println("MYNAME EchoTest");
            
            // debug
System.err.println("socket server started, host/port ="+host+" "+port);
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host: "+host);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for "
                               + "the connection to: "+host);
            System.exit(1);
        }
		createAndShowFrame();
		
		Thread socketListener=new Thread(new Runnable() {
			public void run() {
		        try {
		            do {
		            	inMsg=(String)in.readLine();
		            	display.append(inMsg+"\n");
		            	
		            	// debug
		System.err.println("client in: "+inMsg);
		if (inMsg.equalsIgnoreCase("bye"))
		System.err.println("got 'bye' msg");

		            } while(!inMsg.equalsIgnoreCase("bye"));
		            
		            try {
		            	in.close();
		            	out.close();
		            	echoSocket.close();
		            } catch (IOException iox) {
		            	iox.printStackTrace();
		            }

					// debug
		System.err.println("Client closed down");

		        } catch (IOException e) {
		        	e.printStackTrace();
		        }
				
			}
		});
		
		socketListener.start();
		
//		listenToSocket();
	}
	
    private void createAndShowFrame() {
    	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    	this.getContentPane().add(panelStuff());
		this.pack();
		this.setVisible(true);
    }
    
	public JPanel panelStuff() {
		JPanel panel=new JPanel();
		display=new JTextArea();
		display.setEditable(false);
		display.append("messages go here\n");
		JScrollPane scrollPane=new JScrollPane(display);
		scrollPane.setPreferredSize(new Dimension(600,300));

		userInput=new JTextField(50);
		userInput.setBorder(new LineBorder(Color.blue));
		userInput.addActionListener(this);
		userInput.setActionCommand("getCommand");
		
		panel.add(scrollPane,BorderLayout.CENTER);
		panel.add(userInput,BorderLayout.SOUTH);
		panel.setPreferredSize(new Dimension(600,340));
		
		this.addWindowListener(this);
		return panel;
	}
	
	/**
	 * This should keep listening until 'bye'
	 */
	public static void listenToSocket() {
        try {
            do {
            	inMsg=(String)in.readLine();
            	display.append(inMsg+"\n");
            	
            	// debug
System.err.println("client in: "+inMsg);
if (inMsg.equalsIgnoreCase("bye"))
System.err.println("got 'bye' msg");

            } while(!inMsg.equalsIgnoreCase("bye"));
            
            try {
            	in.close();
            	out.close();
            	echoSocket.close();
            } catch (IOException iox) {
            	iox.printStackTrace();
            }

			// debug
System.err.println("Client closed down");

        } catch (IOException e) {
        	e.printStackTrace();
        }
	}
	
    // here's where command is sent to CirclePack
    public void actionPerformed(ActionEvent e) {
    	String command=e.getActionCommand();
    	if (command.equals("getCommand")) {
    		String cmdInput=userInput.getText().trim();
    		display.append("> "+cmdInput+"\n");
    		
    		// reset input text field
    		userInput.setText(""); 
    		
    		// send user input to socket server
    	    out.println(cmdInput);
System.err.println("client out: " + cmdInput);
    	}
    }
    
	@Override
	public void windowClosing(WindowEvent e) {
		
		// TODO: locks here because it's waiting for other end to close.
		try {
			in.close();
			out.close();
			echoSocket.close();
		} catch (IOException iox) {
			iox.printStackTrace();
		}
        this.dispose();    
        System.exit(1);
	}
	
	@Override
	public void windowOpened(WindowEvent e) {}
	@Override
	public void windowClosed(WindowEvent e) {}
	@Override
	public void windowIconified(WindowEvent e) {}
	@Override
	public void windowDeiconified(WindowEvent e) {}
	@Override
	public void windowActivated(WindowEvent e) {}
	@Override
	public void windowDeactivated(WindowEvent e) {}
 
}
