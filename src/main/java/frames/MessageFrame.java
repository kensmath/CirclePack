package frames;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;

import input.CmdSource;
import input.MyConsole;
import input.ShellManager;

/**
 * Provides shell/scratch/cmdline panel.
 *  
 * This class maintains the command history, for usual
 * up/down command recall. The shell shows command 
 * lines as executed, also messages, errors, etc. 
 * 
 * TODO: split history, command processing, etc. from this GUI
 * 
 * @author kens, based on sample by Alex Fawkes
 */
 public class MessageFrame extends JPanel implements MouseListener{
		private static final long serialVersionUID = 1L;
		
		final static int WIDE = 600; 
		final static int HIGH = 150; 

		public JFrame msgFrame;
		String header = "<html><body bgcolor=fdfde0><font face=\"Segoe UI\" size=-2>";
		String footer = "</font></body></html>";
		JSplitPane shellScratchPane;
		MyConsole msgCmdLine; // command line at bottom

		// shell stuff
		public static JTextPane shellPane;
		public static int cmdNum;
		public static int histPos;
		static StringBuffer shellBuffer;
		static int shellHeadEnd; // keeps track of old end 
		// for pruning display msg in tooltip before a 
		// command is entered.
		static String initShellText = "History of commands and messages will be displayed here.\n"; 

		// command strings are kept in 'cmdHistory' for shell up/down action
		public static List<String> cmdHistory = new ArrayList<String>(); 

		public static JTextArea scratchArea;

		JScrollPane msgScroller;
		JScrollPane scratchScroller;

		// Constructor
		public MessageFrame() {
			msgFrame=new JFrame("Shell/Scratch");
			msgFrame.setLocation(220,420);
			msgFrame.setResizable(true); 
			msgFrame.setPreferredSize(new Dimension(WIDE,HIGH));
			msgFrame.setSize(new Dimension(WIDE,HIGH));
			msgFrame.setVisible(false);
			initComponents();
			this.add(shellScratchPane);
			this.add(msgCmdLine.box);
			msgFrame.add(this);
			msgFrame.pack();
		}

		/**
		 * Create all the components
		 */
		public void initComponents() {
			this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			shellBuffer=new StringBuffer("shell: ");
			
			// shell pane, contains past commands, msgs, etc.
			shellPane = new JTextPane();
			shellPane.setBackground(new Color(253, 253, 224));
			shellPane.setContentType("text/html");
			shellPane.setEditable(false);
			shellPane.addFocusListener(new util.NavFocusListener(shellPane));
			// TODO: want to get cursor to work, but not edit
			// this doesn't work: shellPane.setFocusable(true);
			shellPane.setText(initShellText);

			msgScroller = new JScrollPane(shellPane);
			msgScroller
					.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			msgScroller
					.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			msgScroller.setBackground(new java.awt.Color(229, 245, 183));

			// scratch text area
			scratchArea = new JTextArea();
			scratchArea.setLineWrap(true);
			util.EmacsBindings.addEmacsBindings(scratchArea);
			scratchArea.setText("Scratch Area: \n");
			scratchScroller = new JScrollPane(scratchArea);
			scratchScroller
					.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scratchScroller
					.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

			shellScratchPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
					msgScroller, scratchScroller);
			shellScratchPane.setOneTouchExpandable(true);
			shellScratchPane.setDividerLocation(450);

			// Provide minimum sizes for the two components in the split pane
			msgScroller.setMinimumSize(new Dimension(WIDE/4, 50));
			scratchScroller.setMinimumSize(new Dimension(WIDE/4, 50));

			msgCmdLine = new MyConsole(CmdSource.MESSAGE_FRAME,"messageFrame");
			msgCmdLine.initGUI(WIDE);
			msgCmdLine.box.setBackground(new Color(253, 253, 224));
			msgCmdLine.box.setMinimumSize(new Dimension(0, 20));
			msgCmdLine.box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		}

		public void setText(String passedText) {
			// update shell text
			shellPane.setText(header + passedText + footer);
		}
		
		/**
		 * Arranged like this to try to avoid 
		 * timing/thread conflicts
		 * 
		 * TODO: when history gets too long, this may be 
		 * causing time delays (6/2013)
		 */
		public static void updateShellPane() {
			try {
				synchronized(shellPane) {
					shellPane.setText(ShellManager.runHistory.toString());
					shellPane.setCaretPosition(
						shellPane.getDocument().getLength());
					shellPane.revalidate();
				}
			} catch (Exception ex) {
				System.err.println("shell writing problem: "+ex.getMessage());
			}
		}
				
		public void mouseClicked(MouseEvent me) {
			System.out.println("clicked");
			if(msgFrame.isVisible())
				msgFrame.setVisible(false);
			else
				msgFrame.setVisible(true);
		}

		public void mouseReleased(MouseEvent me) {}
		public void mousePressed(MouseEvent me) {}
		public void mouseEntered(MouseEvent me) {}
		public void mouseExited(MouseEvent me) {}

}
