package input;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import images.CPIcon;
import mytools.MyTool;
import util.StringUtil;

/**
 * Create consoles which handle user entry of CirclePack commands:
 *  (1) Package commands to send to 'commandStrParser',
 *  (2) Maintain a "Command:" line for user entry of commands, 
 *    with command completion, emacs keys.
 *  (3) If desired, maintain the one-line success count/message line 
 *    below the command window.
 *  (4) if desired, call 'LockingShell' hover (developed by Alex Fawkes)
 * @author kens
 */
public class MyConsole extends CmdSource implements KeyListener { // ActionListener,
	public JTextField cmdline;
	public JTextField numberShow;
	public JTextField consoleMsgs;
	private JLabel cmdLabel;
	private Popup compList;
	private Timer compTimer;
	private MyTool tipHover;
	private boolean fullConsole;
	public Box box;
	
	// for command completion when typing ('tab')
	private static HashMap<String,String> commandMap;
	static File completionFile;

	// commandMap created only once; depends on 'CmdCompletion.txt', which
	//    is created in the java program 'infoProcessing'.
	static {
		BufferedReader fp;
		try {
			URL urlFile = CPBase.getResourceURL("/doc/CmdCompletion.txt");
			fp = new BufferedReader(new InputStreamReader(urlFile.openStream()));
		} catch (IOException iox) {
			fp = null;
		}
		commandMap = new HashMap<String, String>();
		if (fp != null) {
			try {
				String line = null;
				while ((line = StringUtil.ourNextLine(fp)) != null) {
					add2CmdCompletion(line);
				}
				fp.close();
			} catch (IOException eio) {
			}
		}
	}

	// static objects; there may be more than one 
	static String prompt = "> "; // command prompt for history (could have, eg, icon)
	static String shellText = ""; //shell history string
		
	public static int myConsoleHeight=48;
	public static int myCmdLineHeight=24;
	
	// Constructor
	public MyConsole(int type,String name) {
		super(type,name);
		box = new Box(BoxLayout.PAGE_AXIS);
		
		// These command lines also have a message line and count feedback
		if (type==PACKCONTROL || type==ACTIVE_FRAME || type==PAIR_FRAME)
			fullConsole=true;
		else
			fullConsole=false;
		
		// create 'cmdline' for typing commands
		cmdline = new JTextField();
		cmdline.setFont(new Font(cmdline.getFont().toString(),
				Font.ROMAN_BASELINE+Font.PLAIN,11+PackControl.fontIncrement));
		cmdline.addKeyListener(this);
		cmdline.setFocusTraversalKeysEnabled(false); // catch TABs		
		util.EmacsBindings.addEmacsBindings(cmdline);
		
		compList=null;
		compTimer = new Timer(5000, new ActionListener() {
			public void actionPerformed(ActionEvent e) { 
				if(compList!=null) compList.hide();
			}
		});
		compTimer.setRepeats(false);
		
		// if specified, create one-line 'msgLine' and count
		if (fullConsole) {
			numberShow=new JTextField();
			numberShow.setBackground(new Color(255,180,180));
			consoleMsgs = new JTextField();
			consoleMsgs.setEditable(false);
			consoleMsgs.setFont(new Font(consoleMsgs.getFont().toString(),Font.PLAIN,12));
			consoleMsgs.setForeground(Color.red);
		}
		else {
			numberShow=null;
			consoleMsgs=null;
		}
	}

	/**
	 * Create the panel with a command line, and possibly progress indicator,
	 * command return count, and one line message window.
	 * @param wide int
	 */
	public void initGUI(int wide) {
		// Upper panel.
		Box upperPanel = Box.createHorizontalBox();
		
		// Prepare MyTool for getting the shell to hover (note: used to use "hoverH.png").
		if (fullConsole) {
			tipHover = new MyTool(new CPIcon("GUI/commandIcon.png"), null, null, null, null, "MISC", false, null, null);
			// TODO: Would like to catch for "toggle" command.
			tipHover.setPreferredSize(new Dimension(tipHover.cpIcon.getImageIcon().getIconWidth(), tipHover.cpIcon.getImageIcon().getIconHeight()));
			tipHover.setMaximumSize(new Dimension(22, 22));
			upperPanel.add(tipHover);
			// AF: Add spacer after icon.
			upperPanel.add(Box.createHorizontalStrut(4));
		}

		// Prepare command label and entry line.
		cmdLabel = new JLabel("Commands:");
		cmdLabel.setFont(cmdLabel.getFont().deriveFont(Font.BOLD, 11.0F));
		// AF: Let the label size itself here, so different fonts don't get clipped.
		//cmdLabel.setMinimumSize(new Dimension(82, 24));
		//cmdLabel.setPreferredSize(new Dimension(82, 24));
		//cmdLabel.setMaximumSize(new Dimension(82, 24));

		cmdline.setMinimumSize(new Dimension(200, 24));
		cmdline.setPreferredSize(new Dimension(wide - 100, 24));
		cmdline.setMaximumSize(new Dimension(wide, 24));
		
		upperPanel.add(cmdLabel);
		// AF: Add spacer after command label.
		upperPanel.add(Box.createHorizontalStrut(4));
		upperPanel.add(cmdline);

		// Add upper panel.
		Component filler = Box.createRigidArea(new Dimension(2, 2));
		box.add(filler);
		box.add(upperPanel);

		// Add lower panel, if needed.
		if (fullConsole) {
			Box lowerPanel = Box.createHorizontalBox();
			numberShow.setPreferredSize(new Dimension(45, 20));
			lowerPanel.add(numberShow);
			consoleMsgs.setPreferredSize(new Dimension(1000, 20));
			lowerPanel.add(consoleMsgs);
			lowerPanel.setPreferredSize(new Dimension(wide, 21));
			lowerPanel.setMaximumSize(new Dimension(1000, 21));
			lowerPanel.setMinimumSize(new Dimension(200, 21));
			box.add(lowerPanel);
		}
			
		// Set overall size.
		upperPanel.setMinimumSize(new Dimension(200, 21));
		upperPanel.setPreferredSize(new Dimension(wide, 21));
		upperPanel.setMaximumSize(new Dimension(1000, 21));
		if (fullConsole) box.setPreferredSize(new Dimension(wide, myConsoleHeight));
		else box.setPreferredSize(new Dimension(wide, 28));
	}

	/**
	 * Have to wait for 'ssPanel' to be created to add listener
	 */
	public void setMouseLtnr() {
		tipHover.addMouseListener(PackControl.msgHover);
	}
	
	public void keyReleased(KeyEvent ke) {
		
		// hide completion list if a key is hit
		if (compList != null)
			compList.hide();

		int code=ke.getKeyCode();
		// send command string for processing
		switch(code) {

		case KeyEvent.VK_ENTER: { // send command string for processing
			String cmd = cmdline.getText().trim();
			if(cmd.length()==0) return;
	
			CPBase.trafficCenter.parseWrapper(cmd,CirclePack.cpb.getActivePackData(),
					true,true,0,this);

			// leave and select the command for changes or to replace with new typing
			cmdline.selectAll();
			break;
		}
		
		// set the entry field text to next command
		case KeyEvent.VK_UP: {
			if (ShellManager.cmdHistoryIndex < ShellManager.cmdHistory.size()-1) 
				ShellManager.cmdHistoryIndex++;
			cmdline.setText(ShellManager.cmdHistory.get(ShellManager.cmdHistoryIndex));
			break;
		}

		// set the entry field text to previous command
		case KeyEvent.VK_DOWN: {
			if (ShellManager.cmdHistoryIndex > -1) ShellManager.cmdHistoryIndex--;
			if (ShellManager.cmdHistoryIndex == -1) cmdline.setText("");
			else cmdline.setText(ShellManager.cmdHistory.get(ShellManager.cmdHistoryIndex));
			break;
		}

		// do command completion with TAB
		case KeyEvent.VK_TAB: {
			StringBuilder cmdbuf = new StringBuilder(cmdline.getText());
			int caret=cmdline.getCaretPosition();
			if (caret<1) 
				break;
			String preCaret=cmdbuf.substring(0,caret-1);
			int lastsemi=preCaret.lastIndexOf(';');
			if (lastsemi==caret-1)
				break;
			lastsemi=(lastsemi<0) ? 0:lastsemi;
			// 'hold' is for reinstating later; add a ';'
			String hold=cmdbuf.substring(0,lastsemi);
			if (hold==null)
				hold="";
			else if (hold.length()>0 && hold.charAt(hold.length()-1)!=';')
				hold=hold+";";
			String leadchars=null;
			if (hold.length()==0)
				leadchars=cmdbuf.substring(lastsemi,caret).trim();
			else 
				leadchars=cmdbuf.substring(lastsemi+1,caret).trim();
			// is there anything to complete?
			if (leadchars.length()==0)
				break;
			String postCaret=cmdbuf.substring(caret);
			String resp[] = complete(leadchars);
			String cmd=resp[0];
			if (cmd==null)
				cmd="";
			// build completed command with pre/post included
			cmdline.setText(hold+cmd+postCaret);
			cmdline.setCaretPosition(hold.length()+cmd.length());

			// creates a tooltip and places it directly below the cmdline box
			JToolTip tip = cmdline.createToolTip();
			tip.setTipText("<html>" + resp[1] + "</html>");
			Rectangle rect = cmdline.getBounds();
			Point loc = cmdline.getLocationOnScreen();
			compList = PopupFactory.getSharedInstance().getPopup(cmdline, tip,
					loc.x, loc.y + rect.height - 2);
			compList.show();

			// hide tip after 5 secs
			compTimer.start();

			break;
		}
		} // end of switch
	}
	
	// ignore these events (req'd for KeyListener)
	public void keyPressed(KeyEvent evt) {}
	public void keyTyped(KeyEvent evt) {}

	/** 
	 * try tab-completion on substring starting 
	 * at last ';'. For strings starting with '|', 
	 * modify the first 5 characters, e.g., '|CA| '.
	 * @param command string; containing command to complete
	 * @returns str[0] = command string with new text 
	 * 					 appended (if found)
	 *          str[1] = text list of possible commands
	 */
	public String []complete(String command) {
		  int nFound=0;
		  String newcmd=null;
		  String possible="";
		  String complist[] = new String[commandMap.size()];
		  commandMap.keySet().toArray(complist);
		  int cmdLength=command.length();
		  
		  // modify 'PackExtender' call prefixes to caps for lookup
		  if (cmdLength>1 && command.charAt(0)=='|') {
			  StringBuilder strbuf=new StringBuilder(command);
			  if (cmdLength>2)
				  strbuf.setCharAt(1,Character.toUpperCase(strbuf.charAt(1)));
			  if (cmdLength>3)
				  strbuf.setCharAt(2,Character.toUpperCase(strbuf.charAt(2)));
			  command=strbuf.toString();
		  }
		  
		  // cut down length until you get a match
		  while (cmdLength>0 && nFound==0) {
			  command=command.substring(0,cmdLength);
			  for(int i=0; i<complist.length; i++) {
				  if(complist[i].startsWith(command)) {
					  possible+=" "+complist[i];
					  nFound++;
	        
					  if( newcmd == null ) {
						  newcmd=complist[i];
					  } 
					  else { // already found a match, now we only want max substring
						  int sz=complist[i].length();
						  int j=0;
						  if( newcmd.length()<sz ) sz=newcmd.length();
						  for(j=0; j<sz; j++) {
							  if( newcmd.charAt(j) != complist[i].charAt(j) )
								  break;
						  }
						  newcmd=complist[i].substring(0,j);
					  }
				  }
			  } // end of for
			  cmdLength--;
		  } // end of while 
			  
		  if( nFound==1 ) {
			  // gets a brief usage message
			  possible = commandMap.get(newcmd);
		  } 
		  else if(possible.length()!=0) {
			  possible = possible.replace(" "+newcmd," <b>"+newcmd+"</b>").substring(1);
		  }
		  else {
			  possible = "No completions available";
		  }
		  String ret[] = new String[2];
		  ret[0]=newcmd;
		  ret[1]=possible;
		  return ret;
	  }
	
	  /**
	   * Add to the command completion hash table
	   * 'commandMap'
	   * @param str
	   */
	  public static void add2CmdCompletion(String str) {
		  if (str==null || str.trim().length()==0) 
			  return;
		  String newstr=str.trim();
		  
		  int k=str.indexOf(' ');
		  
		  // no spaces? then no complications
		  if (k<0) {
			  commandMap.put(newstr,new String("<b>"+newstr+"</b> "));
			  return;
		  }
		  
		  // pick off possible prefix and 'info'
		  String pref=null;
		  // Is this a 'PackExtender' command: eg. "|as| dah"?
		  if (k>0 && newstr.charAt(k-1)=='|') { // get prefix
			  pref=newstr.substring(0,k);
			  newstr=newstr.substring(k,newstr.length()).trim();
		  }
		  
		  k=newstr.indexOf(' ');
		  if (k<0) { // 
			  if (pref==null)
				  commandMap.put(new String(newstr),"<b>"+newstr+"</b> ");
			  else 
				  commandMap.put(new String(pref+" "+newstr),"<b>"+newstr+"</b> ");
			  return;
		  }
		  String cmd=newstr.substring(0,k);
		  String info=newstr.substring(k,newstr.length());
		  if (pref==null) 
			  commandMap.put(cmd,new String("<b>"+cmd+"</b> "+info));
		  else 
			  commandMap.put(new String(pref+" "+cmd),new String("<b>"+
					  pref+" "+cmd+"</b>"+info));
		  return;
	  }
		
	  /**
	   * request focus for 'cmdline'
	   */
	  public void focusToCmdline() {
			cmdline.requestFocus();
	  }
	  
	  /**
	   * set background color of 'cmdline'
	   * @param color Color
	   */
	  public void setBackgroundColor(Color color) {
			cmdline.setBackground(color);
	  }
	  
	  /** 
	   * empty border for 'cmdline'
	   */
	  public void fixBorder() {
		  //cmdline.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 1, new Color(112, 112, 99)));
		  cmdline.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
	  }
			
	  /**
	   * if there's a numberShow window, show how many commands were executed.
	   * @param count int
	   */
	  public void showCmdCount(int count) {
		  if (numberShow!=null)
			  numberShow.setText(new String(Integer.toString(count)));
	  }

	  /**
	   * If there's a consoleMsgs line, show messages.
	   * @param msg String
	   */
	  public void dispConsoleMsg(String msg) {
		  if (consoleMsgs!=null)
			  consoleMsgs.setText(msg);
//		  Toolkit.getDefaultToolkit().beep();
	  }
	  
}
