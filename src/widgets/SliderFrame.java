package widgets;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

import exceptions.DataException;
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
	public static final int DEFAULT_WIDTH=400; 
	public static final int DEFAULT_HEIGHT=400; 
	
	// abstract methods that must be implemented by derived classes
	public abstract void populate(); // create and add the 'ActiveBar's
	public abstract void downLoad(); // updating sliders from packData
	public abstract void captureValue(double value,int indx); // individual update from mouse action
	public abstract JPanel createSliderPanel(); // may want, e.g., special border
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
		setRange();
		holdChangeCmd="";
		holdMotionCmd="";
		
		// common listener for all sliders
		listener = new ChangeListener() {
			public void stateChanged(ChangeEvent event) {
				// update text field when the slider value changes
				IndexedJSlider source = (IndexedJSlider) event.getSource();
				int indx=source.getIndex();
				mySliders[indx].changeReaction(event);// pass on to 'ActiveSlider'
			}
		};
	}
	
	public SliderFrame(PackData p,String chgcmd,String movcmd) {
		this(p);
		holdChangeCmd=chgcmd;
		holdMotionCmd=movcmd;
	}
	
	/**
	 * Basic GUI start, adjusted by instantiating code
	 */
	public void initGUI() {
		setLayout(new BorderLayout());
		
		// Create control/data display area
		controlPanel = new JPanel();
		controlPanel.setBounds(1,1,DEFAULT_WIDTH,60);
		controlPanel.setSize(new Dimension(DEFAULT_WIDTH,60));
		controlPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,60));
		controlPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		// three panels
		JPanel leftPanel=new JPanel();
		BoxLayout bleft=new BoxLayout(leftPanel,BoxLayout.PAGE_AXIS);
		leftPanel.setLayout(bleft);
		JPanel midPanel=new JPanel();
		BoxLayout blmid=new BoxLayout(midPanel,BoxLayout.PAGE_AXIS);
		midPanel.setLayout(blmid);
		JPanel rightPanel=new JPanel();
		BoxLayout blright=new BoxLayout(rightPanel,BoxLayout.PAGE_AXIS);
		rightPanel.setLayout(blright);

		JButton button = new JButton("Update");
		button.addActionListener(this);
		button.setActionCommand("Update");
		button.setPreferredSize(new Dimension(90, 22));
		button.setToolTipText("Recompute all values");
		leftPanel.add(button);

		button = new JButton("Help");
		button.addActionListener(this);
		button.setActionCommand("Help");
		button.setPreferredSize(new Dimension(80, 22));
		button.setToolTipText("Help window for some 'Widget'");
		leftPanel.add(button);
		
		minValue=new xNumField("Min",8);
		maxValue=new xNumField("Max",8);
		midPanel.add(minValue);
		midPanel.add(maxValue);
		
		// third panel has two panels
		JPanel righttopPanel=new JPanel();
		motionCheck=new JCheckBox("motion cmd");
		motionCheck.setSelected(false);
		motionCmdField=new JTextField("",30);
		righttopPanel.add(motionCmdField);
		
		JPanel rightbottomPanel=new JPanel();
		changeCheck=new JCheckBox("chamge cmd");
		changeCheck.setSelected(false);
		changeCmdField=new JTextField("",30);
		rightbottomPanel.add(changeCmdField);

		controlPanel.add(leftPanel);
		controlPanel.add(midPanel);
		controlPanel.add(rightPanel);
		
		add(controlPanel, BorderLayout.NORTH);

		// Create sliderPanel
		sliderPanel=createSliderPanel();
		sliderPanel.setSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
		sliderPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
		populate();
		
		sliderScroll=new JScrollPane(sliderPanel,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
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

		if (cmd.equals("Help")) {
			JFrame auxHelpFrame=new JFrame();
			auxHelpFrame.setTitle("Help for BarGraph");
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
			downLoad();
		}
	}
	
} 


