package panels;

import input.CPFileManager;
import input.FileDialogs;
import input.TrafficCenter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;

public class PostPanel extends JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	// three panels
	private JPanel actionPanel;
	private JPanel flagPanel;
	private JPanel jpgPanel;

	private JButton dumpButton;
	private JButton aboutButton;
	private JButton goCkButton;
	private JPanel Misc;
	private JButton goButton;
	private JTextField flagField;
	private JRadioButton printButton;
	private JRadioButton appendButton;
	private JRadioButton fileButton;
	private ButtonGroup optionGroup;
	private JCheckBox popupBox;
	private JCheckBox jpgBox;
	private JCheckBox facelabelBox;
	private JCheckBox cirlabelBox;
	private JCheckBox pathBox;
	private JCheckBox unitcirBox;
	private JCheckBox facefillBox;
	private JCheckBox facecolorBox;
	private JCheckBox faceBox;
	private JCheckBox cirfillBox;
	private JCheckBox circcolorBox;
	private JCheckBox cirBox;
	
	protected StringBuilder buildCmd;

	private HashMap<String,Boolean> checks;

	// Constructor
	public PostPanel() {
		super();
		initGUI();

		// map for checkboxes and their boolean states
	    checks=new HashMap<String,Boolean>();
	    checks.put("circles",Boolean.valueOf(true));
	    checks.put("cirfill",Boolean.valueOf(false));
	    checks.put("circolor",Boolean.valueOf(false));
	    checks.put("faces",Boolean.valueOf(false));
	    checks.put("facefill",Boolean.valueOf(false));
	    checks.put("facecolor",Boolean.valueOf(false));
	    checks.put("cirlabels",Boolean.valueOf(false));
	    checks.put("facelabels",Boolean.valueOf(false));
	    checks.put("unitcir",Boolean.valueOf(false));
	    checks.put("path",Boolean.valueOf(false));
	    checks.put("popup",Boolean.valueOf(true));
	    checks.put("save",Boolean.valueOf(true));
	    checks.put("append",Boolean.valueOf(false));
	    checks.put("print",Boolean.valueOf(false));
	    checks.put("jpg",Boolean.valueOf(false));
	}
	
	private void initGUI() {
		setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		
		// create parts for top panel
		JPanel topPanel=getCheckPanel(); // put circle/face choices in
//		topPanel.setPreferredSize(new Dimension(PackControl.ControlDim1.width-2,400));
		add(topPanel);
		
		// advanced flag panel
		flagPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		flagPanel.setBorder(BorderFactory.createTitledBorder("PostScript: explicit flags (advanced)"));
		flagField = new JTextField();
		flagField.setPreferredSize(new Dimension(PackControl.ControlDim1.width-120,24));

		goCkButton = new JButton("GO");
		goCkButton.setActionCommand("useFlags");
		goCkButton.setToolTipText("PostScript using the flags given to the right (see 'post' in Help)");
		goCkButton.setBackground(new Color(0,255,255));
		goCkButton.setFont(new java.awt.Font("TrueType",Font.ITALIC,14));
		goCkButton.setBorder(new LineBorder(Color.black,1,false));
		goCkButton.setPreferredSize(new Dimension(30,24));
		goCkButton.addActionListener(this);
		
		flagPanel.add(goCkButton);
		flagPanel.add(flagField);
		flagPanel.setPreferredSize(new Dimension(PackControl.ControlDim1.width-2,60));  // 45));
		add(flagPanel);
		
		// panel with buttons for JPG output
		jpgPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		dumpButton = new JButton("ScreenDump (JPG)");
		dumpButton.addActionListener(this);
		dumpButton.setActionCommand("canvas2JPG");
		dumpButton.setToolTipText("Dump the current screen as *.jpg");
		dumpButton.setPreferredSize(new Dimension(165,20));
//		dumpButton.setBounds(2,66,130,20);
		
		aboutButton = new JButton("AboutImage (JPG)");
		aboutButton.addActionListener(this);
		aboutButton.setActionCommand("aboutJPG");
		aboutButton.setToolTipText("Save the 'About' image as *.jpg");
		aboutButton.setPreferredSize(new Dimension(165,20));
//		aboutButton.setBounds(2,46,130,20);
		
		jpgPanel.add(dumpButton);
		jpgPanel.add(aboutButton);		
		jpgPanel.setPreferredSize(new Dimension(PackControl.ControlDim1.width-2,35));
		add(jpgPanel);
		
		// options button group, action panel
		actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		actionPanel.setBorder(new LineBorder(Color.red,2,false));
		
		JLabel actLabel=new JLabel("Action:");
		actLabel.setPreferredSize(new Dimension(80,20));
		
		fileButton = new JRadioButton();
		fileButton.setText("Save");
		fileButton.setFont(new Font("TrueType",Font.ITALIC,12));
		fileButton.setSelected(true);
		fileButton.setActionCommand("save");
		fileButton.setPreferredSize(new Dimension(100,20));

		appendButton = new JRadioButton();
		appendButton.setText("Append");
		appendButton.setFont(new Font("TrueType",Font.ITALIC,12));
		appendButton.setActionCommand("append");
		appendButton.setPreferredSize(new Dimension(100,20));

		printButton = new JRadioButton();
		printButton.setText("Print");
		printButton.setFont(new Font("TrueType",Font.ITALIC,12));
		printButton.setActionCommand("print");
		printButton.setPreferredSize(new Dimension(100,20));

		// form button group
		optionGroup = new ButtonGroup();
		optionGroup.add(fileButton);
		optionGroup.add(appendButton);
		optionGroup.add(printButton);
		
		actionPanel.add(actLabel);
		actionPanel.add(fileButton);
		actionPanel.add(appendButton);
		actionPanel.add(printButton);
		actionPanel.setPreferredSize(new Dimension(400,30));
		
		add(actionPanel);

	}
	
	private JPanel getCheckPanel() {
		int yval = 2;
		JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		checkPanel.setBorder(BorderFactory
				.createTitledBorder("PostScript: choose objects"));

		JPanel circleData = new JPanel(null);
		circleData.setBorder(new LineBorder(Color.black, 1, false));
		{
			cirBox = new JCheckBox();
			cirBox.setText("Circles");
			cirBox.setFont(new Font("TrueType", Font.BOLD, 12));
			cirBox.setSelected(true);
			cirBox.setActionCommand("circles");
			cirBox.addActionListener(this);
			cirBox.setToolTipText("include all circles");
			cirBox.setBounds(2, yval, 80, 20);
			yval += 20;
		}
		{
			circcolorBox = new JCheckBox();
			circcolorBox.setText("color?");
			circcolorBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			circcolorBox.setActionCommand("circolor");
			circcolorBox.addActionListener(this);
			circcolorBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		{
			cirfillBox = new JCheckBox();
			cirfillBox.setText("filled?");
			cirfillBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			cirfillBox.setActionCommand("cirfill");
			cirfillBox.addActionListener(this);
			cirfillBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		{
			cirlabelBox = new JCheckBox();
			cirlabelBox.setText("label?");
			cirlabelBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			cirlabelBox.setActionCommand("cirlabels");
			cirlabelBox.addActionListener(this);
			cirlabelBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		circleData.add(cirBox);
		circleData.add(circcolorBox);
		circleData.add(cirfillBox);
		circleData.add(cirlabelBox);
		circleData.setPreferredSize(new Dimension(100,90)); //88));
		checkPanel.add(circleData);

		JPanel faceData = new JPanel(null);
		yval = 2;
		faceData.setBorder(new LineBorder(new java.awt.Color(0, 0, 0), 1, false));
		{
			faceBox = new JCheckBox();
			faceBox.setText("Faces");
			faceBox.setFont(new Font("TrueType", Font.BOLD, 12));
			faceBox.setActionCommand("faces");
			faceBox.addActionListener(this);
			faceBox.setToolTipText("include all faces of the carrier");
			faceBox.setBounds(2, yval, 70, 20);
			yval += 20;
		}
		{
			facecolorBox = new JCheckBox();
			facecolorBox.setText("color?");
			facecolorBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			facecolorBox.setActionCommand("facecolor");
			facecolorBox.addActionListener(this);
			facecolorBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		{
			facefillBox = new JCheckBox();
			facefillBox.setText("filled?");
			facefillBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			facefillBox.setActionCommand("facefill");
			facefillBox.addActionListener(this);
			facefillBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		{
			facelabelBox = new JCheckBox();
			facelabelBox.setText("label?");
			facelabelBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			facelabelBox.setActionCommand("facelabels");
			facelabelBox.addActionListener(this);
			facelabelBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		faceData.add(faceBox);
		faceData.add(facecolorBox);
		faceData.add(facefillBox);
		faceData.add(facelabelBox);
		faceData.setPreferredSize(new Dimension(100, 90));  //88));
		checkPanel.add(faceData);

		Misc = new JPanel(null);
		yval = 2;
		Misc.setBorder(new LineBorder(Color.black, 1, false));
		{
			unitcirBox = new JCheckBox();
			unitcirBox.setText("Unit circle");
			unitcirBox.setFont(new Font("TrueType", Font.PLAIN, 10));
			unitcirBox.setActionCommand("unitcir");
			unitcirBox.addActionListener(this);
			unitcirBox.setBounds(5,yval, 110, 18);
			yval+=20;
		}
		yval+=20;
		{
			pathBox = new JCheckBox();
			pathBox.setText("Path");
			pathBox.setFont(new Font("TrueType",Font.BOLD,12));
			pathBox.setActionCommand("path");
			pathBox.addActionListener(this);
			pathBox.setBounds(5,yval,110,20);
			yval+=20;
		}
//		{
//			popupBox = new JCheckBox();
//			popupBox.setText("GV popup");
//			popupBox.setFont(new Font("TrueType",Font.PLAIN,12));
//			popupBox.setActionCommand("popup");
//			popupBox.addActionListener(this);
//			popupBox.setBounds(5,yval,110,20);
//			yval+=20;
//		}
		{
			jpgBox = new JCheckBox();
			jpgBox.setText("JPG output");
			jpgBox.setFont(new Font("TrueType",Font.BOLD,12));
			jpgBox.setActionCommand("jpg");
			jpgBox.addActionListener(this);
			jpgBox.setBounds(5,yval,110,20);
		}
		Misc.add(pathBox);
//		Misc.add(popupBox);
		Misc.add(jpgBox);
		Misc.add(unitcirBox);
		Misc.setPreferredSize(new Dimension(130,90)); // 88));

		checkPanel.add(Misc);
		
		// PostScript options
		JPanel goPanel=new JPanel(null);
		goButton = new JButton();
		goButton.setText("GO");
		goButton.setActionCommand("GO");
		goButton.setToolTipText("Go with PostScript, options as specified in checkboxes");
		goButton.addActionListener(this);
		goButton.setFont(new java.awt.Font("TrueType",Font.ITALIC,14));
		goButton.setBackground(new Color(255,255,0));
		goButton.setBorder(new LineBorder(Color.black,1,false));
		goButton.setBounds(2,2,30,24);
		goPanel.add(goButton);
		goPanel.setPreferredSize(new Dimension(40,90)); // (134,88));
		
		checkPanel.add(goPanel);
		
		return checkPanel;
	}

	/**
	 * Use 'checks' data to form flag string
	 * 
	 * @return
	 */
	public String formPostFlags() {
		StringBuilder flags=new StringBuilder("");
		StringBuilder frag=null;
		if (((Boolean)(checks.get("unitcir"))).booleanValue()) 
			flags.append(" -u");
		if (((Boolean)(checks.get("circles"))).booleanValue()) {
			frag=new StringBuilder("");
			if (((Boolean)(checks.get("circolor"))).booleanValue()) 
				frag.append("c");
			if (((Boolean)(checks.get("cirfill"))).booleanValue()) 
				frag.append("f");
			flags.append(" -c"+frag.toString()+" ");
		}
		if (((Boolean)(checks.get("faces"))).booleanValue()) {
			frag=new StringBuilder("");
			if (((Boolean)(checks.get("facecolor"))).booleanValue()) 
				frag.append("c");
			if (((Boolean)(checks.get("facefill"))).booleanValue()) 
				frag.append("f");
			flags.append(" -f"+frag.toString()+" ");
		}
		frag=new StringBuilder("");
		if (((Boolean)(checks.get("cirlabels"))).booleanValue()) 
			frag.append(" -cn");
		if (((Boolean)(checks.get("facelabels"))).booleanValue()) 
			frag.append(" -fn");
		if (((Boolean)(checks.get("path"))).booleanValue()) 
			flags.append(" -g");
		if (flags.length()==0) return null; // didn't get any flags
		
		return (flags.toString());
	}
	
	/**
	 * String for closing a 'post' command
	 * @return String
	 */
	public String createSuffix() {
		StringBuilder frag=new StringBuilder("");
		/* 'g' popup isn't working
		if (((Boolean)(checks.get("popup"))).booleanValue()) 
			frag.append("g");*/
		if (((Boolean)(checks.get("print"))).booleanValue()) 
			frag.append("l");
		if (((Boolean)(checks.get("jpg"))).booleanValue()) 
			frag.append("jpg");
		return new String(" -x"+frag.toString());
	}
	
	/**
	 * Create the flag segment for opening a postscript file.
	 * @param name
	 * @return
	 */
	public StringBuilder createPrefix(String name) {
		StringBuilder bc=new StringBuilder("post ");
		if (((Boolean)(checks.get("append"))).booleanValue()) 
			bc.append("-oa "+name);
		else bc.append("-o "+name);
		return bc;
	}
	
	public void actionPerformed(ActionEvent e){
	 	String command = e.getActionCommand();
	 	File aboutFile=null;
	 	
	 	if (command.equals("canvas2JPG")) {
	 		String []jpgFileName=jpgOutputDialog();
	 		if (jpgFileName==null) return;
	 	    try {
	 	        File file = new File(CPFileManager.ImageDirectory,jpgFileName[1]);
	 	        ImageIO.write(PackControl.getActiveCPScreen().packImage, "jpg", file);
	 	    } catch (Exception exc) {
	 	    	return;
	 	    }
	 	    return;
	 	}
	 	if (command.equals("aboutJPG") && 
	 			(aboutFile=PackControl.scriptManager.getAboutTmpFile())!=null) {
	 		String []jpgFileName=aboutOutputDialog();
	 		if (jpgFileName==null) 
	 			return;
	 		File file=null;
	 	    try {
	 	        file = new File(CPFileManager.ImageDirectory,jpgFileName[1]);
	 	        CPFileManager.copyFile(aboutFile, file);
	 	    } catch (Exception exc) {
	 	    	CirclePack.cpb.errMsg("problem copying 'AboutImage' to "+file);
	 	    }
	 	    return;
	 	}
	  	if (command.equals("GO")
	  			|| (command.equals("useFlags") && flagField.getText().trim().length()>0)) {
	  		String []psFileInfo=new String[2];
	  		psFileInfo[1]=new String("");
	  		// some commands need us to open file
	  		if (((Boolean)(checks.get("save"))).booleanValue()
	  			|| ((Boolean)(checks.get("append"))).booleanValue()
	  		  	|| ((Boolean)(checks.get("print"))).booleanValue()) {
	  			psFileInfo=postOutputDialog();
	  			if (psFileInfo==null) return;
	  		}
	  		
	  		// psFileInfo[1] should have just the file name; 'PostDirectory' should have
	  		//   been set in chooser call.
	  		buildCmd=new StringBuilder(createPrefix(psFileInfo[1]));
	  		String flags;
	  		if (command.equals("useFlags"))	flags=flagField.getText();
	  		else flags=formPostFlags();
	  		if (flags.length()<1) return;
	  		buildCmd.append(" "+flags+" "+createSuffix());
	  		try {
	  			TrafficCenter.cmdGUI(buildCmd.toString());
	  		} catch (Exception ex) {return;}
	  		return;
	  	}
  		Object obj=e.getSource();
  		if (obj instanceof JCheckBox) {
  			JCheckBox jbox=(JCheckBox)e.getSource();
  			checks.put(jbox.getActionCommand(),Boolean.valueOf(jbox.isSelected()));
  		}
	}

	/** 
	 * Uses file chooser for postscript output.
	 * Returns string[0]=directory, string[1]=filename.
	 * Note this sets 'PostDirectory', which is used in parsing 'post' 
	 * command. This should prevent conflict between what a chooser
	 * provides and what a user types in in other cases.
	 * @return
	 */
	private String []postOutputDialog() {
		File theFile;
		if ((theFile=FileDialogs.saveDialog(FileDialogs.POSTSCRIPT,true))!=null) {
   			String []fileInfo=new String[2];
   			fileInfo[0]=null;
   			fileInfo[1]=null;
   			fileInfo[0]=theFile.getParent();
   			if (fileInfo[0]!=null && fileInfo[0].length()>0)
   				CPFileManager.ImageDirectory=new File(fileInfo[0]);
   			fileInfo[1]=theFile.getName();
   			if (fileInfo[1].length()==0) 
   				throw new ParserException("error: post: no file name given");
   			return fileInfo;
	   	}
	   	else return null;
	}

	/** 
	 * Uses file chooser for jpg output.
	 * Returns string[0]=directory, string[1]=filename.
	 * Note this sets 'PostDirectory', which is used in parsing 'post' 
	 * command. This should prevent conflict between what a chooser
	 * provides and what a user types in in other cases.
	 * @return
	 */
	private String []jpgOutputDialog() {
		File theFile;
		if ((theFile=FileDialogs.saveDialog(FileDialogs.JPG,true))!=null) {
   			String []fileInfo=new String[2];
   			fileInfo[0]=null;
   			fileInfo[1]=null;
   			fileInfo[0]=theFile.getParent();
   			if (fileInfo[0]!=null && fileInfo[0].length()>0)
   				CPFileManager.ImageDirectory=new File(fileInfo[0]);
   			fileInfo[1]=theFile.getName();
   			if (fileInfo[1].length()==0) 
   				throw new ParserException("error: jpg: no file name given");
   			return fileInfo;
	   	}
	   	else return null;
	}

	/** 
	 * Uses file chooser for 'AboutImage' jpg output.
	 * Returns string[0]=directory, string[1]=filename.
	 * Note this sets 'PostDirectory', which is used in parsing 'post' 
	 * command. This should prevent conflict between what a chooser
	 * provides and what a user types in in other cases.
	 * @return
	 */
	private String []aboutOutputDialog() {
		File theFile;
		if ((theFile=FileDialogs.saveDialog(FileDialogs.ABOUT,true))!=null) {
   			String []fileInfo=new String[2];
   			fileInfo[0]=null;
   			fileInfo[1]=null;
   			fileInfo[0]=theFile.getParent();
   			if (fileInfo[0]!=null && fileInfo[0].length()>0)
   				CPFileManager.ImageDirectory=new File(fileInfo[0]);
   			fileInfo[1]=theFile.getName();
   			if (fileInfo[1].length()==0) 
   				throw new ParserException("error: jpg: no file name given");
   			return fileInfo;
	   	}
	   	else return null;
	}

	/**
	 * Get postscript options from the active packing's postOptions 
	 */
	public void update(int old_pnum) {
		PackControl.cpScreens[old_pnum].postOptions.storeTailored(flagField.getText());
		flagField.setText(PackControl.getActiveCPScreen().postOptions.tailored);
		Boolean[] bools= PackControl.getActiveCPScreen().postOptions.getSavedStates();
		cirBox.setSelected(bools[0].booleanValue());
		circcolorBox.setSelected(bools[1].booleanValue());
		cirfillBox.setSelected(bools[2].booleanValue());
		faceBox.setSelected(bools[3].booleanValue());
		facecolorBox.setSelected(bools[4].booleanValue());
		facefillBox.setSelected(bools[5].booleanValue());
		cirlabelBox.setSelected(bools[6].booleanValue());
		facelabelBox.setSelected(bools[7].booleanValue());		
		unitcirBox.setSelected(bools[8].booleanValue());
		pathBox.setSelected(bools[9].booleanValue());
		popupBox.setSelected(bools[10].booleanValue());
		jpgBox.setSelected(bools[14].booleanValue());
		
		// ?? not sure what will happen here, only one can be true
		fileButton.setSelected(bools[11].booleanValue());
		appendButton.setSelected(bools[12].booleanValue());
		printButton.setSelected(bools[13].booleanValue());
		
	}
	
	public JButton getJButton2() {
		if(dumpButton == null) {
		}
		return dumpButton;
	}
	
	public String getFlags() {
		return flagField.getText();
	}

}
