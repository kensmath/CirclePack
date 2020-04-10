package frames;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;

import circlePack.PackControl;
import input.CmdSource;
import input.MyConsole;
import input.ShellManager;

/**
 * Created at startup. Provides hovering shell/scratch panel.
 * When locked, panes are scrollable and command entry line is
 * added. Hover occurs when mouse is in "Messages" button or
 * 'Hover' buttons in 'MyConsole's.
 * 
 * This class maintains the command history, for usual
 * up/down command recall. The shell shows command 
 * lines as executed, also messages, errors, etc. 
 * 
 * TODO: split history, command processing, etc. from this GUI
 * 
 * @author kens, based on sample by Alex Fawkes
 */
 public class MessageHover extends HoverPanel {

		private static final long 
		serialVersionUID = 1L;
		
		final static int WIDE = 600; 
		final static int HIGH = 150; 

		String header = "<html><body bgcolor=fdfde0><font face=\"Segoe UI\" size=-2>";
		String footer = "</font></body></html>";
		JSplitPane shellScratchPane;
		MyConsole lockedCmdLine; // command line at bottom of lockedFrame

		// shell stuff
		public static JTextPane shellPane;
		public static int cmdNum;
		public static int histPos;
		static StringBuffer shellBuffer;
		static int shellHeadEnd; // keeps track of old end for pruning
		// display msg in tooltip before a command is entered
		static String initShellText = "History of commands and messages will be displayed here.\n"; 

		// command strings are kept in 'cmdHistory' for shell up/down action
		public static List<String> cmdHistory = new ArrayList<String>(); 

		public static JTextArea scratchArea;

		JScrollPane msgScroller;
		JScrollPane scratchScroller;

		// Constructor
		public MessageHover() {
			super(WIDE,HIGH,"Shell/Scratch");
			lockedFrame.setResizable(true);
			hoverFrame.setResizable(true);
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

			lockedCmdLine = new MyConsole(CmdSource.MESSAGE_FRAME,"messageFrame");
			lockedCmdLine.initGUI(WIDE);
			lockedCmdLine.box.setBackground(new Color(253, 253, 224));
			lockedCmdLine.box.setMinimumSize(new Dimension(0, 20));
			lockedCmdLine.box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		}
		
		public void loadHover() {
			this.removeAll();
			this.add(shellScratchPane);
			

//			insets = shellFrame.getInsets();
//			shellFrame.setPreferredSize(new Dimension(WIDTH + insets.left
//					+ insets.right, HEIGHT + insets.top + insets.bottom));

		}
		
		public void loadLocked() {
			this.removeAll();
			msgScroller
			.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			scratchScroller
			.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			this.add(shellScratchPane);
			this.add(lockedCmdLine.box);
		}

		public void setText(String passedText) {
			// update shell text
			shellPane.setText(header + passedText + footer);
		}
		
		/**
		 * Arranged like this to try to avoid timing/thread conflicts
		 * 
		 * TODO: when history gets too long, this may be causing time delays (6/2013)
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
		
		/**
		 * TODO: Not implemented when 'MsgFrame' window was replaced by 'ShellManager'
		 * 
		 * When the user clicks on a previous command in the 'historyPane', copy
		 * that command to the 'MyConsole.cmdline'. TODO: this was copied from
		 * 'MsgFrame' and not yet adjusted
		 */
		public void hyperlinkUpdate(HyperlinkEvent evt) {
			if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				// use description instead of url in case its not a valid url
				String cmd = evt.getDescription();

				if (cmd.startsWith("cmd://")) {
					PackControl.consoleCmd.cmdline.setText(cmd.substring(6));
					PackControl.consoleCmd.cmdline.selectAll();
				}
				/*
				 * TODO: what was this for? else { // other links in output message,
				 * update separate browser URL url = evt.getURL(); // TODO: update
				 * separate browser window }
				 */
				// add these 2 if statements
			} else if (evt.getEventType() == HyperlinkEvent.EventType.ENTERED) {
				String tt = evt.getDescription();
				if (tt.startsWith("tooltip://")) {
					// strip 'tooltip://' and fix newlines
					tt = tt.substring(10).replace("\n", "<br />");
					shellPane.setToolTipText("<html><body>" + tt
							+ "</body></html>");
				}
			} else if (evt.getEventType() == HyperlinkEvent.EventType.EXITED) {
				shellPane.setToolTipText(null);
			}
		}


}
