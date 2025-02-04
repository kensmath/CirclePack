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
import javax.swing.BoxLayout;
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
import allMains.CirclePack;
import circlePack.PackControl;
import input.CommandStrParser;
import packing.PackData;
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
	public final int RADSLIDER=0;
	public final int SCHFLOWER=1;
	public final int ANGLESUM=2;
	
	// abstract methods that must be implemented by derived classes
	public abstract double getParentValue(int indx); // retrieve value held by parent
	public abstract void populate(); // create and add the 'ActiveBar's
	public abstract void downValue(int indx); // from packing to slider
	public abstract void upValue(int indx); // send slider value up to packing
	public abstract void createSliderPanel(); // may want, e.g., special border
	public abstract void setChangeField(String cmd); // set optional command with value change
	public abstract void setMotionField(String cmd); // set optional command on motion into slider
	public abstract void setOptCmdField(String cmd); // set optional 'OptCmd' command
	public abstract void mouse_entry_action(int indx); 
	public abstract void changeValueField_action(double val,int indx); 
	public abstract int addObject(String obj);  // add object(s) 
	public abstract int removeObject(String obj); // remove object(s)
	public abstract void killMe(); // to call CirclePack to kill this frame
	public abstract void initRange(); // set the initial slider ranges
	
	public Double[] parentValues; // parent may hold the values
	public int type;  // 0=radii, 1=intrinsic schwarzians, 2=angle sums
	public int sliderCount;
	public double val_min;
	public double val_max;
	public String holdChangeCmd;
	public String holdMotionCmd;
	
	public JPanel controlPanel; // option buttons, readouts
	public JPanel topPanel;  // top of controlPanel
	public JPanel bottomPanel;  // bottom of controlPanel
	public JPanel sliderPanel;  // scale lines and 'myBars' go here
	public JPanel optionalPanel; // null unless created by inherited class
	public JPanel commandPanel;  // bottom panel for command string
	public JScrollPane sliderScroll;   // contains sliderPanel
	public xNumField minValue;  
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
		optionalPanel=null;
		
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
	 * Basic GUI start: data has been initiated by instantiating code.
	 * They appear on the right of the screen, varying colors, positions
	 */
	public void initGUI() {
		
		int w=PackControl.displayDimension.width-DEFAULT_WIDTH-50*type+20*packData.packNum;
		// vary location depending on type and packNum
		this.setBounds(w,60+100*type,DEFAULT_WIDTH,200);//DEFAULT_HEIGHT);
		setLayout(new BorderLayout());

		initRange(); 

		// Create control/data display area
		controlPanel = new JPanel(new BorderLayout());
		controlPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,70));
		controlPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		// two panels
		topPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
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
		
		if (type!=SCHFLOWER) {
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
		}
		else { // SCHFLOWER: use this as error window
			addField=new JTextField(25);
			addField.setEditable(false);
			addremovePanel.add(addField);
		}
		
		topPanel.add(addremovePanel);
		
		// bottom panel has min/max values
		JPanel bottomleftPanel = new JPanel();

		button = new JButton("min");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(45,20));
		button.addActionListener(this);
		button.setActionCommand("set minimum");
		bottomleftPanel.add(button);
		minValue=new xNumField("",7);
		minValue.setValue(val_min);
		bottomleftPanel.add(minValue);
		
		JPanel bottomrightPanel=new JPanel();

		button = new JButton("Max");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(45,20));
		button.addActionListener(this);
		button.setActionCommand("set maximum");
		bottomrightPanel.add(button);
		maxValue=new xNumField("",7);
		maxValue.setValue(val_max);
		bottomrightPanel.add(maxValue);
		
		bottomPanel.add(bottomleftPanel,BorderLayout.WEST);
		bottomPanel.add(bottomrightPanel,BorderLayout.EAST);

		controlPanel.add(topPanel,BorderLayout.NORTH);
		controlPanel.add(bottomPanel,BorderLayout.SOUTH);
		
		add(controlPanel, BorderLayout.NORTH);

		// Create sliderPanel
		createSliderPanel();
		sliderPanel.setLayout(new BoxLayout(sliderPanel,BoxLayout.PAGE_AXIS));
		populate();

		// there may be an optional panel
		if (optionalPanel!=null)
			sliderPanel.add(optionalPanel);
		
		sliderScroll=new JScrollPane(sliderPanel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sliderScroll.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
		add(sliderScroll,BorderLayout.CENTER);
		
		// Command string options at bottom
		commandPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		commandPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,60));
		// command panel has three panels for commands
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

		commandPanel.add(midleftPanel);
		commandPanel.add(midmidPanel);
		commandPanel.add(midrightPanel);

		add(commandPanel,BorderLayout.SOUTH);
		
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
	 * Only for SCHFLOWER, print or clear error in 'addField'
	 * @param errstr String
	 */
	public void setErrorText(String errstr) {
		addField.setText(errstr);
	}
	
	public void clearError() {
		setErrorText("");
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
	
	public void valueField_action(double val, int indx) {
		mySliders[indx].value=val;
		upValue(indx); // send value up to packing
		mySliders[indx].refreshValue();
		changeAction(indx);
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
	 * Update all slider values and slider field
	 * values from PackData without triggering
	 * change action.
	 */
	public void downloadData() {
		for (int j=0;j<sliderCount;j++) {
			mySliders[j].refreshValue();
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

		if (cmd.equals("Slider Info")) {
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
		else if (type!=SCHFLOWER && cmd.equals("add object")) {
			addObject(addField.getText().trim());
		}
		else if (type!=SCHFLOWER && cmd.equals("remove object")) {
			removeObject(removeField.getText().trim());
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


