package widgets;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import allMains.CPBase;
import allMains.CirclePack;
import input.CommandStrParser;
import packing.PackData;
import panels.CPScreen;
import util.ResultPacket;
import util.xNumField;

/**
 * Frame for multiple sliders.
 * Has a control panel for buttons, e.g., "Update", 'Help"
 * and a 'sliderPanel' that holds multiple horizontal slides in
 * a scrollPane. 
 * @author kstephe2, 5/2020
 *
 */

public abstract class SliderFrame extends JFrame implements ActionListener {
	
	private static final long 
	serialVersionUID = 1L;

	public PackData packData;
	
	// try to starting with these sizes
	public static final int DEFAULT_WIDTH=420; 
	public static final int DEFAULT_HEIGHT=300; 
	
	// abstract methods that must be implemented by derived classes
	public abstract void populate(); // create and add the 'ActiveBar's
	public abstract void downValue(int indx); // from packing to slider
	public abstract void upValue(int indx); // send slider value to packing
	public abstract void createSliderPanel(); // may want, e.g., special border
	public abstract void setChangeField(String cmd);   // set optional command with value change
	public abstract void setMotionField(String cmd);   // set optional command on motion into slider 
	public abstract void mouse_entry_action(int indx); 
	public abstract int addObject(String obj);  // add object(s) 
	public abstract int removeObject(String obj); // remove object(s)
	public abstract void killMe(); // to call CirclePack to kill this frame
	public abstract void initRange(); // set the initial slider ranges
	
	public int sliderCount;
	public double val_min;
	public double val_max;
	public String holdChangeCmd;
	public String holdMotionCmd;
	
	JPanel controlPanel; // option buttons, readouts
	JPanel topPanel;  // top of controlPanel
	JPanel midPanel;  // middle of controlPanel
	JPanel bottomPanel;  // bottom of controlPanel
	JPanel sliderPanel;  // scale lines and 'myBars' go here
	JScrollPane sliderScroll;   // contains sliderPanel
	public xNumField minValue;  // 
	public xNumField maxValue;
	public JTextField changeCmdField; // optional command: execute on slider change
	public JCheckBox changeCheck;  // whether to apply change command
	public JTextField motionCmdField; // optional command: execute when mouse enters 
	public JCheckBox motionCheck;  // whether to apply motion command
	public JTextField optCmdField; // optional command 
	public JTextField addField;
	public JTextField removeField;
	public ActiveSlider[] mySliders; // number depends on what's being displayed
	public ChangeListener listener; // listener for all the ActiveSlider's
	public StringBuilder helpInfo;

	public SliderFrame(PackData p) {
		super();
		
		// throw back to CirclePack to kill this window
		this.addWindowListener(new WindowAdapter(){  
	        public void windowClosing(WindowEvent e) {
	        	killMe();
	        }
		});

		packData=p;
		setLocation(new Point(200,100));
		helpInfo=new StringBuilder("Put information here when instantiated");
		holdChangeCmd="";
		holdMotionCmd="";
		
		// common listener for all sliders
		listener = new ChangeListener() {
			public void stateChanged(ChangeEvent event) {
				// update text field when the slider value changes
				IndexedJSlider source = (IndexedJSlider) event.getSource();
				if (!source.getValueIsAdjusting()) {  // is this last in a chain of mouse actions?
					int indx=source.getIndex();
					mySliders[indx].changeReaction();// pass on to 'ActiveSlider'
				}
			}
		};
	}
	
	public SliderFrame(PackData p,String chgcmd,String movcmd) {
		this(p);
		// strings should have been processed to remove quotes, 
		//    convert 'Obj' to '_Obj', and
		// TODO: need to catch semicolons which occur between quotes
		if (chgcmd.length()>0) {
			holdChangeCmd=chgcmd;
		}
		else 
			holdChangeCmd="";
		if (movcmd.length()>0) {
			holdMotionCmd=movcmd;
		}
		else
			holdMotionCmd="";
	}
	
	/**
	 * Basic GUI start: data has been initiated by instantiating code
	 */
	public void initGUI() {
		
		this.setBounds(50,350,DEFAULT_WIDTH,200);//DEFAULT_HEIGHT);
		setLayout(new BorderLayout());

		initRange(); 

		// Create control/data display area
		controlPanel = new JPanel(new BorderLayout());
		controlPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,120));
		controlPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		// three panels
		topPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		midPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		midPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,60));
		bottomPanel=new JPanel(new BorderLayout());

		// top has buttons 
		JButton button = new JButton("Info");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(45,20));
		button.addActionListener(this);
		button.setActionCommand("Slider Info");
		button.setToolTipText("Help window");
		topPanel.add(button);

		button = new JButton("Update");
		button.setBorder(null);
		button.setMargin(new Insets(1,5,1,5));
		button.setPreferredSize(new Dimension(60,20));
		button.addActionListener(this);
		button.setActionCommand("Update");
		button.setToolTipText("Recompute all values");
		topPanel.add(button);
		
		JPanel addremovePanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		
		button = new JButton("+");
		button.setBorderPainted(false);
		button.setMargin(new Insets(2,6,2,6));
		button.addActionListener(this);
		button.setPreferredSize(new Dimension(25,20));
		button.setActionCommand("add object");
		button.setToolTipText("Add a new object");
		addField=new JTextField(3);
		addField.setEditable(true);
		addremovePanel.add(button);
		addremovePanel.add(addField);
		
		button = new JButton("-");
		button.setBorder(null);
		button.setMargin(new Insets(2,2,2,2));
		button.addActionListener(this);
		button.setPreferredSize(new Dimension(25,20));
		button.setActionCommand("remove object");
		button.setToolTipText("Remove an object");
		removeField=new JTextField(3);
		removeField.setEditable(true);
		addremovePanel.add(button);
		addremovePanel.add(removeField);
		
		topPanel.add(addremovePanel);
		
		// middle panel has three panels for commands
		JPanel midleftPanel=new JPanel(new BorderLayout());
		changeCheck=new JCheckBox("change cmd");
		changeCheck.setSelected(false);
		if (holdChangeCmd.length()>0)
			changeCheck.setSelected(true);
		changeCmdField=new JTextField("",13);
		midleftPanel.add(changeCheck,BorderLayout.NORTH);
		midleftPanel.add(changeCmdField,BorderLayout.CENTER);
		
		JPanel midmidPanel=new JPanel(new BorderLayout());;
		motionCheck=new JCheckBox("motion cmd");
		motionCheck.setSelected(false);
		if (holdMotionCmd.length()>0)
			motionCheck.setSelected(true);
		motionCmdField=new JTextField("",13);
		midmidPanel.add(motionCheck,BorderLayout.NORTH);
		midmidPanel.add(motionCmdField,BorderLayout.CENTER);

		JPanel midrightPanel=new JPanel(new BorderLayout());
		button=new JButton("optional cmd");
//		button.setBorder(null);
//		button.setMargin(new Insets(10,2,8,2));
		button.setPreferredSize(new Dimension(25,22));
		button.addActionListener(this);
		button.setActionCommand("optional cmd");
		button.setToolTipText("Execute this optional command");
//		JCheckBox jbox=new JCheckBox("optional cmd"); // just used for dispaly
//		jbox.setSelected(true);
		optCmdField=new JTextField("",13);
		optCmdField.setEditable(true);
		midrightPanel.add(button,BorderLayout.NORTH);
		midrightPanel.add(optCmdField,BorderLayout.CENTER);

		midPanel.add(midleftPanel);
		midPanel.add(midmidPanel);
		midPanel.add(midrightPanel);

		// bottom panel has min/max values
		JPanel bottomleftPanel = new JPanel();

		button = new JButton("min");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(45,18));
		button.addActionListener(this);
		button.setActionCommand("set minimum");
		bottomleftPanel.add(button);
		minValue=new xNumField("",6);
		minValue.setValue(val_min);
		bottomleftPanel.add(minValue);
		
		JPanel bottomrightPanel=new JPanel();

		button = new JButton("Max");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(45,18));
		button.addActionListener(this);
		button.setActionCommand("set maximum");
		bottomrightPanel.add(button);
		maxValue=new xNumField("",6);
		maxValue.setValue(val_max);
		bottomrightPanel.add(maxValue);
		
		bottomPanel.add(bottomleftPanel,BorderLayout.WEST);
		bottomPanel.add(bottomrightPanel,BorderLayout.EAST);

		controlPanel.add(topPanel,BorderLayout.NORTH);
		controlPanel.add(midPanel,BorderLayout.CENTER);
		controlPanel.add(bottomPanel,BorderLayout.SOUTH);
		
		add(controlPanel, BorderLayout.NORTH);

		// Create sliderPanel
		createSliderPanel();
//		sliderPanel.setSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
		sliderPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
		populate();
		
		sliderScroll=new JScrollPane(sliderPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(sliderScroll,BorderLayout.CENTER);
		
		pack();
		setChangeField(holdChangeCmd);
		setMotionField(holdMotionCmd);
	}

	/**
	 * Set the "Help" popup information
	 * @param strbld StringBuilder
	 */
	public void setHelpText(StringBuilder strbld) {
		helpInfo=strbld;
	}
	
	/**
	 * Execute a command
	 * @param cmdstr String
	 * @return int
	 */
	public int cpCommand(String cmdstr) {
		return CommandStrParser.jexecute(packData,cmdstr);
	}
	
	public int changeAction(int indx) {
		setObjVariable(mySliders[indx].getLabel()); // set variable 'Obj' in any case
		String chgstr=changeCmdField.getText();
		if (changeCheck.isSelected() && chgstr.length()>0) {
			ResultPacket rP=new ResultPacket(packData,chgstr);
			CPBase.trafficCenter.parseCmdSeq(rP,0,null);
			return Integer.valueOf(rP.cmdCount);
		}
		return 0;
	}
	
	public int motionAction(int indx) {
		setObjVariable(mySliders[indx].getLabel()); // set variable 'Obj' in any case
		String mvstr=motionCmdField.getText();
		if (motionCheck.isSelected() && mvstr.length()>0) {
			ResultPacket rP=new ResultPacket(packData,mvstr);
			CPBase.trafficCenter.parseCmdSeq(rP,0,null);
			return Integer.valueOf(rP.cmdCount);
		}
		return 0;
	}
	
	/**
	 * Reset the minimum value common to all sliders; should
	 * not trigger change commands
	 * @param minval double
	 */
	public void resetMin(double minval) {
		if (minval>=val_max) {
			CirclePack.cpb.errMsg("usage: trying to set slider min too large");
			return;
		}
		val_min=minval;
		minValue.setValue(val_min); // display in the value window
		for (int j=0;j<sliderCount;j++)
			mySliders[j].refreshValue();
	}

	/**
	 * Reset the maximum value common to all sliders; should
	 * not trigger change commands
	 * @param minval double
	 */
	public void resetMax(double maxval) {
		if (maxval<=val_min) {
			CirclePack.cpb.errMsg("usage: trying to set slider max too small");
			return;
		}
		val_max=maxval;
		maxValue.setValue(val_max); // display in the value window
		for (int j=0;j<sliderCount;j++)
			mySliders[j].refreshValue();
	}

	/**
	 * Update all slider values from PackData
	 */
	public void downloadData() {
		for (int j=0;j<sliderCount;j++) {
			downValue(j);
		}
		this.repaint();
	}

	/**
	 * Set variable "Obj" to given string value
	 * @param obj
	 */
	public void setObjVariable(String obj) {
		Vector<Vector<String>> fseg=new Vector<Vector<String>>(1);
		Vector<String> itm=new Vector<String>(0);
		itm.add(obj);
		fseg.add(itm);
		CPBase.varControl.putVariable(packData,"Obj",fseg); 
	}

	// Process button pressing events
	public void actionPerformed(ActionEvent evt) {

		String cmd=evt.getActionCommand();

		if (cmd.equals("Info")) {
			JFrame auxHelpFrame=new JFrame();
			auxHelpFrame.setTitle("Help for SliderFrame");
			JTextArea helpText=new JTextArea();
			helpText.setText(helpInfo.toString());
			auxHelpFrame.add(helpText);
			helpText.setLineWrap(true);
			helpText.setWrapStyleWord(true);
			util.EmacsBindings.addEmacsBindings(helpText);
			helpText.setPreferredSize(new Dimension(500,500));
			auxHelpFrame.pack();
			auxHelpFrame.setVisible(true);
		}
		else if (cmd.equals("Update")) {
			downloadData();
		}
		else if (cmd.equals("add object")) {
			String obj=addField.getText().trim();
			addObject(obj);
		}
		else if (cmd.equals("remove object")) {
			String obj=removeField.getText().trim();
			removeObject(obj);
		}
		else if (cmd.equals("set minimum")) {
			double min=minValue.getValue();
			this.resetMin(min);
		}
		else if (cmd.equals("set maximum")) {
			double max=maxValue.getValue();
			this.resetMax(max);
		}
		else if (cmd.equals("optional cmd")) {
			String cmdStr=optCmdField.getText();
			if (cmdStr.length()>0) {
				ResultPacket rP=new ResultPacket(packData,cmdStr);
				CPBase.trafficCenter.parseCmdSeq(rP,0,null);
			}
		}

	}
	
} 

