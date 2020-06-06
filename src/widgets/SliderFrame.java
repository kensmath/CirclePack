package widgets;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import allMains.CPBase;
import exceptions.DataException;
import input.CommandStrParser;
import packing.PackData;
import panels.CPScreen;
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

	CPScreen cpScreen;
	PackData packData;
	
	// try to starting with these sizes
	public static final int DEFAULT_WIDTH=420; 
	public static final int DEFAULT_HEIGHT=400; 
	
	// abstract methods that must be implemented by derived classes
	public abstract void populate(); // create and add the 'ActiveBar's
	public abstract void downloadData(); // updating sliders from packData
	public abstract void captureValue(double value,int indx); // individual update from mouse action
	public abstract void createSliderPanel(); // may want, e.g., special border
	public abstract void setChangeField(String cmd);   // set optional command with value change
	public abstract void setMotionField(String cmd);   // set optional command on motion into slider 
	public abstract void mouse_entry_action(int indx); 
	public abstract void setRange();   // compute val_min, val_max
	
	public double val_min;
	public double val_max;
	public String holdChangeCmd;
	public String holdMotionCmd;
	
	JPanel controlPanel; // option buttons, readouts
	JPanel sliderPanel;  // scale lines and 'myBars' go here
	JScrollPane sliderScroll;   // contains sliderPanel
	xNumField minValue;  // 
	xNumField maxValue;
	JTextField changeCmdField; // optional command: execute on slider change
	JCheckBox changeCheck;  // whether to apply change command
	JTextField motionCmdField; // optional command: execute when mouse enters 
	JCheckBox motionCheck;  // whether to apply motion command
	
	public ActiveSlider[] mySliders; // number depends on what's being displayed
	public ChangeListener listener; // listener for all the ActiveSlider's
	public StringBuilder helpInfo;

	public SliderFrame(PackData p) {
		super();
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
	 * Basic GUI start, adjusted by instantiating code
	 */
	public void initGUI() {
		setLayout(new BorderLayout());

		setRange();

		// Create control/data display area
		controlPanel = new JPanel(new BorderLayout());
		controlPanel.setBounds(1,1,DEFAULT_WIDTH,60);
		controlPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,120));
		controlPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		// three panels
		JPanel topPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		JPanel midPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		midPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,60));
		JPanel bottomPanel=new JPanel(new BorderLayout());

		// top has buttons, 2 for now
		JButton button = new JButton("Info");
		button.addActionListener(this);
		button.setActionCommand("Slider Info");
		button.setPreferredSize(new Dimension(80, 22));
		button.setToolTipText("Help window for some 'Widget'");
		topPanel.add(button);

		button = new JButton("Update");
		button.addActionListener(this);
		button.setActionCommand("Update");
		button.setPreferredSize(new Dimension(90, 22));
		button.setToolTipText("Recompute all values");
		topPanel.add(button);
		
		JPanel righttopPanel=new JPanel();
		motionCheck=new JCheckBox("motion cmd");
		motionCheck.setSelected(false);
		motionCmdField=new JTextField("",15);
		righttopPanel.add(motionCheck);
		righttopPanel.add(motionCmdField);

		// middle panel has two panels for commands
		JPanel midleftPanel=new JPanel(new BorderLayout());
		changeCheck=new JCheckBox("change cmd");
		changeCheck.setSelected(false);
		if (holdChangeCmd.length()>0)
			changeCheck.setSelected(true);
		changeCmdField=new JTextField("",15);
		midleftPanel.add(changeCheck,BorderLayout.NORTH);
		midleftPanel.add(changeCmdField,BorderLayout.CENTER);
		
		JPanel midrightPanel=new JPanel(new BorderLayout());;
		motionCheck=new JCheckBox("motion cmd");
		motionCheck.setSelected(false);
		if (holdMotionCmd.length()>0)
			motionCheck.setSelected(true);
		motionCmdField=new JTextField("",15);
		midrightPanel.add(motionCheck,BorderLayout.NORTH);
		midrightPanel.add(motionCmdField,BorderLayout.CENTER);

		midPanel.add(midleftPanel);
		midPanel.add(midrightPanel);

		// bottom panel has min/max values
		JPanel bottomleftPanel = new JPanel();
		JTextField minText=new JTextField("min value: "+String.format("%.5e",val_min));
		minText.setEditable(false);
		bottomleftPanel.add(minText);
		
		JPanel bottomrightPanel=new JPanel();
		JTextField maxText=new JTextField("Max value: "+String.format("%.5e",val_max));
		maxText.setEditable(false);
		bottomrightPanel.add(maxText);
		
		bottomPanel.add(bottomleftPanel,BorderLayout.WEST);
		bottomPanel.add(bottomrightPanel,BorderLayout.EAST);

		controlPanel.add(topPanel,BorderLayout.NORTH);
		controlPanel.add(midPanel,BorderLayout.CENTER);
		controlPanel.add(bottomPanel,BorderLayout.SOUTH);
		
		add(controlPanel, BorderLayout.NORTH);

		// Create sliderPanel
		createSliderPanel();
		sliderPanel.setSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
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
			return cpCommand(chgstr);
		}
		return 0;
	}
	
	public int motionAction(int indx) {
		setObjVariable(mySliders[indx].getLabel()); // set variable 'Obj' in any case
		String mvstr=motionCmdField.getText();
		if (motionCheck.isSelected() && mvstr.length()>0)
			return cpCommand(mvstr);
		return 0;
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
	
	/**
	 * Set min/max values for the slider
	 * @param min double
	 * @param max double
	 */
	public void setRange(double min, double max) {
		val_min=min;
		val_max=max;
		if (val_max<=val_min)
			throw new DataException("min/max reversed");
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
	}
	
} 


