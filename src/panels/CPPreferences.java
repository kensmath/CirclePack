package panels;

import frames.PairedFrame;
import input.CPFileManager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;

import allMains.CPBase;
import canvasses.MainFrame;
import circlePack.PackControl;

/** Various preferences, directory options, etc. are kept in the
 * preferences file '~/myCirclePack/cpprefrc'. This class helps 
 * manage these preferences.
 * @author kens
 *
 */
public class CPPreferences implements ActionListener, ItemListener{

  @SuppressWarnings("unused")
private static final long 
  serialVersionUID = 1L;

  public static boolean started=false;
  public static boolean draft;
  public static boolean displayDebug;
  public static boolean displayToolTips;
  private JTextField scriptDirText;
  private JTextField packingDirText;
  private JTextField imageDirText;
  private JTextField toolDirText;
  private JTextField extenderDirText;  
  private JTextField gvCmdText;
  private JTextField webURLField;
  private JTextField scriptURLField;
  private JTextField cmdHistoryFile;
  private JTextField printCmdText;
  private JTextField canvasSizeText;
  private JTextField pairSizeText;
  private JTextField fontSizeText;
  
  protected static JFrame locframe;

  /*  This static block guarantees that all values are initialized and
   *  usable.  This block is executed when the class is loaded as opposed
   *  to the constructor that is executed when an object is actually 
   *  instantiated.
   */
  static{
    draft = false;
    displayToolTips = true;
    displayDebug = false;
  }

  //  Constructor
  public CPPreferences(){
    File prefFile = CPBase.CPprefFile; // should have been set in 'initPackControl'

    // set defaults
    scriptDirText = new JTextField(CPBase.SCRIPT_DIR,20);
    scriptDirText.setToolTipText("directory for your scripts");
    packingDirText = new JTextField(CPBase.PACKINGS_DIR,20);
    packingDirText.setToolTipText("directory for your circle packing files");
    imageDirText = new JTextField(CPBase.IMAGE_DIR,20);
    imageDirText.setToolTipText("directory for your screen shots");
    printCmdText = new JTextField(CPBase.PRINT_COMMAND,20);
    printCmdText.setToolTipText("system command to print something");
    toolDirText = new JTextField(CPBase.TOOL_DIR,20);
    toolDirText.setToolTipText("directory of your 'tools', icon-encapsulated command strings");
    extenderDirText = new JTextField(CPBase.EXTENDER_DIR,20);
    extenderDirText.setToolTipText("directory for 'PackExtender' inherited Java class files");
    webURLField= new JTextField(CPBase.WEB_URL_FILE,20);
    webURLField.setToolTipText("file containing most recently visited URLs");
    scriptURLField= new JTextField(CPBase.SCRIPT_URL_FILE,20);
    scriptURLField.setToolTipText("file containing most recently loaded scripts");
    canvasSizeText=new JTextField(CPBase.ACTIVE_CANVAS_SIZE,10);
    canvasSizeText.setToolTipText("Main packing canvas size, 200 to 1200 pixels");
    pairSizeText=new JTextField(CPBase.PAIR_CANVAS_SIZE,10);
    pairSizeText.setToolTipText("Side-by-side packing canvas size, 200 to 800 pixels");
    fontSizeText=new JTextField(CPBase.FONT_INCREMENT,10);
    fontSizeText.setToolTipText("Font sizes (e.g., in script) can be increased by 1 to 6 increment");
    
	CPFileManager.ScriptDirectory = new File(System.getProperty("user.home"));
	CPFileManager.PackingDirectory = new File(System.getProperty("user.home"));
	CPFileManager.ImageDirectory = new File(System.getProperty("user.home"));
	
	// this will force use of jar 'Resources' if not explicitly reset
	CPFileManager.ToolDirectory= null; 

    if(prefFile.exists()){
      try{
        BufferedReader reader = new BufferedReader(new FileReader(prefFile));
        String s;

        while((s = reader.readLine()) != null) { 
          StringTokenizer tok = new StringTokenizer(s);
          
          // ignore blank lines or lines without at least a pair of tokens
          if(s.trim().length()>0 && s.charAt(0)!='#' && tok.countTokens() >= 2){
            String keyword = tok.nextToken().trim();
            StringBuilder value=new StringBuilder(tok.nextToken().trim());
            while (tok.hasMoreTokens()) value.append(" "+tok.nextToken().trim());

            if (keyword.equals("PACKINGS_DIR")) {
            	CPBase.PACKINGS_DIR=value.toString().trim();
       			packingDirText.setText(CPBase.PACKINGS_DIR);
       			CPFileManager.PackingDirectory= adjustFileHome(CPBase.PACKINGS_DIR);
       			if (CPFileManager.PackingDirectory==null) {
       				CPFileManager.PackingDirectory=new File(CPBase.PACKINGS_DIR);
       				if (!CPFileManager.PackingDirectory.exists()) // didn't find it? look in 'home'
       					CPFileManager.PackingDirectory=
       						new File(CPFileManager.CurrentDirectory,CPBase.PACKINGS_DIR);
       			}
            }
            
            else if (keyword.equals("IMAGE_DIR")) {
            	CPBase.IMAGE_DIR=value.toString().trim();
       			scriptDirText.setText(CPBase.IMAGE_DIR);
       			CPFileManager.ImageDirectory=adjustFileHome(CPBase.IMAGE_DIR);
       			if (CPFileManager.ImageDirectory==null) {
       				CPFileManager.ImageDirectory=new File(CPBase.IMAGE_DIR);
       				if (!CPFileManager.ImageDirectory.exists()) // didn't find it? look in 'home'
       					CPFileManager.ImageDirectory=
       						new File(CPFileManager.CurrentDirectory,CPBase.SCRIPT_DIR);
       			}
            }

            else if (keyword.equals("SCRIPT_DIR")) {
            	CPBase.SCRIPT_DIR=value.toString().trim();
       			scriptDirText.setText(CPBase.SCRIPT_DIR);
       			CPFileManager.ScriptDirectory=adjustFileHome(CPBase.SCRIPT_DIR);
       			if (CPFileManager.ScriptDirectory==null) {
       				CPFileManager.ScriptDirectory=new File(CPBase.SCRIPT_DIR);
       				if (!CPFileManager.ScriptDirectory.exists()) // didn't find it? look in 'home'
       					CPFileManager.ScriptDirectory=
       						new File(CPFileManager.CurrentDirectory,CPBase.SCRIPT_DIR);
       			}
            }

            else if (keyword.equals("TOOL_DIR")) {
            	CPBase.TOOL_DIR=value.toString().trim();
       			toolDirText.setText(CPBase.TOOL_DIR);
       			CPFileManager.ToolDirectory= adjustFileHome(CPBase.TOOL_DIR);
       			if (CPFileManager.ToolDirectory==null) {
       				CPFileManager.ToolDirectory= 
       					new File(CPFileManager.CurrentDirectory,CPBase.TOOL_DIR);
       			}
            }
            
            else if (keyword.equals("EXTENDER_DIR")) {
            	CPBase.EXTENDER_DIR=value.toString().trim();
       			extenderDirText.setText(CPBase.EXTENDER_DIR);
       			CPFileManager.ExtenderDirectory= adjustFileHome(CPBase.EXTENDER_DIR);
       			if (CPFileManager.ExtenderDirectory==null) {
       				CPFileManager.ExtenderDirectory= 
       					new File(CPFileManager.CurrentDirectory,CPBase.EXTENDER_DIR);
       			}
            }
            else if (keyword.equals("WEB_URL_FILE")) {
            	CPBase.WEB_URL_FILE=value.toString().trim();
            	File ff=adjustFileHome(CPBase.WEB_URL_FILE);
            	if (ff==null) ff=new File(CPFileManager.HomeDirectory,CPBase.WEB_URL_FILE);
            	webURLField.setText(ff.toString());
            }
            else if (keyword.equals("SCRIPT_URL_FILE")) {
            	CPBase.SCRIPT_URL_FILE=value.toString().trim();
            	File ff=adjustFileHome(CPBase.SCRIPT_URL_FILE);
            	if (ff==null) ff=new File(CPFileManager.HomeDirectory,CPBase.SCRIPT_URL_FILE);
            	scriptURLField.setText(ff.toString());
            }
            else if(keyword.equals("PRINT_COMMAND")){
            	CPBase.PRINT_COMMAND=value.toString().trim()+" ";
            	printCmdText.setText(CPBase.PRINT_COMMAND);
            }
            
//          else if (keyword.equals("POSTSCRIPT_VIEWER")) {
//              CPBase.POSTSCRIPT_VIEWER=value.toString().trim()+" ";
//            	gvCmdText.setText(CPBase.POSTSCRIPT_VIEWER);
//          }
            
            else if (keyword.equals("ACTIVE_CANVAS_SIZE")) {
            	int sz=Integer.parseInt(value.toString().trim());
            	if (sz<PackControl.MinActiveSize) sz=PackControl.MinActiveSize;
            	if (sz>PackControl.MaxActiveSize) sz=PackControl.MaxActiveSize;
            	CPBase.ACTIVE_CANVAS_SIZE=Integer.toString(sz);
            	PackControl.setActiveCanvasDim(sz);
            	canvasSizeText.setText(CPBase.ACTIVE_CANVAS_SIZE);
          	}
            
            else if (keyword.equals("PAIR_CANVAS_SIZE")) {
            	int sz=Integer.parseInt(value.toString().trim());
            	if (sz<PackControl.MinMapSize) sz=PackControl.MinMapSize;
            	if (sz>PackControl.MaxMapSize) sz=PackControl.MaxMapSize;
            	CPBase.PAIR_CANVAS_SIZE=Integer.toString(sz);
            	PackControl.setPairedCanvasDim(sz);
            	pairSizeText.setText(CPBase.PAIR_CANVAS_SIZE);
          	}
            
            else if (keyword.equals("FONT_INCREMENT")) {
            	CPBase.FONT_INCREMENT=value.toString().trim();
            	int sz=Integer.parseInt(CPBase.FONT_INCREMENT);
            	PackControl.setFontIncrement(sz);
            	fontSizeText.setText(CPBase.FONT_INCREMENT);
          	}
            
          }
        }
        reader.close();
      }
      
      catch(IOException e){
    	  e.printStackTrace();
      }
    }
    
    // popup warning message if display is too small
    if (PackControl.displayDimension.width<600 || PackControl.displayDimension.height<600) {
    	JFrame tmpframe=new JFrame();
    	tmpframe.setSize(200,300);
    	JLabel lab=new JLabel("CirclePack");
    	tmpframe.add(lab);
    	tmpframe.setVisible(true);
		JOptionPane.showMessageDialog(tmpframe,
				"Your display area is quite small and CirclePack will be "+
				"difficult to use. The CirclePack Owl suggests you use a "+
				"different computer.",
				"Warning", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Check string 'value' to see if it is intended to point to "home"
   * directory. If yes, return new 'File'. If not, return null; calling 
   * routine chooses initial directory path -- generally 'userDirectory'.
   * @param String value
   * @return 'File' or null if 'value' does not indicate "home"
   */
  public File adjustFileHome(String value) {
	  String home=CPFileManager.HomeDirectory.toString();
	  if (value==null || value.trim().length()==0)
		  return (new File(home));
	  value=value.trim();
	  if (value.startsWith("~")) {
		  return (new File(home,value.substring(1)));
	  }
	  if (value.startsWith(home)) return (new File(value));
	  else return null;
  }

  /**
   *  Use:  This class creates a preferences window, adds components, 
   *    sets itself as a listener and displays the frame on the screen.
   */
  public JFrame displayPreferencesWindow(){
    locframe = new JFrame("Preferences");
    locframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    JPanel overall = new JPanel();
    overall.setLayout(new BorderLayout());

    JPanel panel=new JPanel();
    String prefname=CPBase.CPprefFile.getAbsolutePath();
    String home=CPFileManager.HomeDirectory.getAbsolutePath()+File.separator;
    if (prefname.startsWith(home));
    	prefname=prefname.substring(home.length());
    JLabel topwords=new JLabel("Preferences file: \"~/"+prefname+"\"");
    panel.setBorder(new EtchedBorder());
    panel.add(topwords);
    overall.add(panel,BorderLayout.NORTH);

    JPanel mainPanel=new JPanel();

    mainPanel.setLayout(new GridLayout(0,1));

    panel=new JPanel();
    JLabel label = new JLabel("SCRIPT_DIR");
    panel.add(label);
    panel.add(scriptDirText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("PACKINGS_DIR");
    panel.add(label);
    panel.add(packingDirText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("IMAGE_DIR");
    panel.add(label);
    panel.add(imageDirText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("TOOL_DIR");
    panel.add(label);
    panel.add(toolDirText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("EXTENDER_DIR");
    panel.add(label);
    panel.add(extenderDirText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("PRINT_COMMAND");
    panel.add(label);
    panel.add(printCmdText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("WEB_URL_FILE");
    panel.add(label);
    panel.add(webURLField);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("CMD_URL_FILE");
    panel.add(label);
    panel.add(scriptURLField);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("ACTIVE_CANVAS_SIZE");
    panel.add(label);
    panel.add(canvasSizeText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("PAIR_CANVAS_SIZE");
    panel.add(label);
    panel.add(pairSizeText);
    mainPanel.add(panel);

    panel = new JPanel();
    label = new JLabel("FONT_INCREMENT");
    panel.add(label);
    panel.add(fontSizeText);
    mainPanel.add(panel);

    overall.add(mainPanel,BorderLayout.CENTER);

    JPanel bottomPanel=new JPanel();
    JButton confirm = new JButton("Apply");
    confirm.addActionListener(this);
    confirm.setActionCommand("accept");
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(this);
    cancel.setActionCommand("cancel");
    JButton applysave = new JButton("Apply/Save");
    applysave.addActionListener(this);
    applysave.setActionCommand("applysave");
    bottomPanel.add(confirm);
    bottomPanel.add(cancel);
    bottomPanel.add(applysave);
    overall.add(bottomPanel,BorderLayout.SOUTH);

    locframe.getContentPane().add(overall);
    locframe.pack();
    return locframe;
  }

  /**
   * write the preferences to the preference file
   */
  protected void savePrefs() {
	  File f=CPBase.CPprefFile;
	  try {
		  BufferedWriter writer = new BufferedWriter(new FileWriter(f));

		  writer.write("PACKINGS_DIR "+packingDirText.getText());
		  writer.newLine();
		  writer.write("SCRIPT_DIR "+scriptDirText.getText());
		  writer.newLine();
		  writer.write("IMAGE_DIR "+scriptDirText.getText());
		  writer.newLine();
		  writer.write("TOOL_DIR "+toolDirText.getText());
		  writer.newLine();
		  writer.write("EXTENDER_DIR "+extenderDirText.getText());
		  writer.newLine();
		  writer.write("PRINT_COMMAND "+printCmdText.getText());
		  writer.newLine();
		  writer.write("WEB_URL_FILE "+webURLField.getText());
		  writer.newLine();
		  writer.write("SCRIPT_URL_FILE "+scriptURLField.getText());
		  writer.newLine();	
		  writer.write("ACTIVE_CANVAS_SIZE "+canvasSizeText.getText());
		  writer.newLine();	
		  writer.write("PAIR_CANVAS_SIZE "+pairSizeText.getText());
		  writer.newLine();
		  writer.write("FONT_INCREMENT "+fontSizeText.getText());
		  writer.flush();
		  writer.close();
	  } catch (IOException ioe) {
		  String fto=new String("Failed to open "+CPBase.CPprefFile.getAbsolutePath());
		  PackControl.consoleCmd.dispConsoleMsg(fto);
		  PackControl.shellManager.recordError(fto);
	  }
  }

  /**
   * This applies the preferences that have been set by
   * the user in '~/myCirclePack/cpprefrc'.
   */
  protected void applyPrefs() {
	  File file=null;
	  try {
		  
		  file=new File(scriptDirText.getText());
		  if (file.exists()) 
			  CPFileManager.ScriptDirectory=file;
		  else CPFileManager.ScriptDirectory=CPFileManager.HomeDirectory;

		  file=new File(packingDirText.getText());
		  if (file.exists())
			  CPFileManager.PackingDirectory=file;
		  else CPFileManager.PackingDirectory=CPFileManager.HomeDirectory;

		  file=new File(imageDirText.getText());
		  if (file.exists())
			  CPFileManager.ImageDirectory=file;
		  else CPFileManager.ImageDirectory=CPFileManager.HomeDirectory;

		  file=new File(toolDirText.getText());
		  if (file.exists())
			  CPFileManager.ToolDirectory=file;
		  else CPFileManager.ToolDirectory=CPFileManager.HomeDirectory;

		  file=new File(extenderDirText.getText());
		  if (file.exists())
			  CPFileManager.ExtenderDirectory=file;
		  else CPFileManager.ExtenderDirectory=CPFileManager.HomeDirectory;

		  // set active canvas preferred size
		  int sz=Integer.parseInt(canvasSizeText.getText());
		  if (sz<PackControl.MinActiveSize) sz=PackControl.MinActiveSize;
		  if (sz>PackControl.MaxActiveSize) sz=PackControl.MaxActiveSize;
		  PackControl.setActiveCanvasDim(sz); // store
		  if (sz!=MainFrame.getCanvasDim().height)
			  PackControl.activeFrame.layMeOut(new Dimension(sz,sz));
		  PackControl.activeFrame.repaint();
		  
		  // set paired canvas preferred size
		  int psz=Integer.parseInt(pairSizeText.getText());
		  if (psz<PackControl.MinMapSize) psz=PackControl.MinMapSize;
		  if (psz>PackControl.MaxMapSize) psz=PackControl.MaxMapSize;
		  PackControl.setPairedCanvasDim(psz); // store 
		  if (psz!=PairedFrame.getCanvasDim().height)
			  PackControl.mapPairFrame.layMeOut(new Dimension(psz,psz));
		  PackControl.mapPairFrame.repaint();
		  
	  } catch (Exception ex) {
		  ex.printStackTrace();
		  return;
	  }
  }

  /**
   * Button clicks on the preferences window are handled here.
   */
  public void actionPerformed(ActionEvent e){
    String command = e.getActionCommand();

    if(command.equals("apply")){ // Any changes apply to current session
    	applyPrefs();
    }
    else if (command.equals("applysave")){ // apply changes and save to CPprefFile
    	applyPrefs();
    	savePrefs();
        locframe.setVisible(false);
        locframe.dispose();
    }
    else if(command.equals("cancel")){ // No changes accepted
        locframe.setVisible(false);
        locframe.dispose();
    }
  } 

  public void itemStateChanged(ItemEvent e){} 

  public String getPrintCmd() {
	  return printCmdText.getText();
  }

  public void setPrintCmd(String printCmd) {
	  printCmdText.setText(printCmd);
  }

  public String getGvCmd() {
	  return gvCmdText.getText();
  }

  public void setGvCmd(String gvCmd) {
	  gvCmdText.setText(gvCmd);
  }

  public String getWebURLfile() {
	  return webURLField.getText().trim();
  }

  public String getCmdURLfile() {
	  return scriptURLField.getText().trim();
  }

  public String getCmdHistoryFile() {
	  return cmdHistoryFile.getText().trim();
  }
  
  public void setWebURLfile(String webFile) {
	  webURLField.setText(webFile);
  }

  public void setCmdURLfile(String webFile) {
	  scriptURLField.setText(webFile);
  }

  public boolean getDisplayToolTips(){
  	return displayToolTips;
  }
  
}
