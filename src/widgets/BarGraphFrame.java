package widgets;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EtchedBorder;

import packing.PackData;
import panels.CPScreen;
import util.xNumField;

/**
 * Frame for general purpose bar graphs. (Started from 'SphWidget'.)
 * Has a control panel for buttons, e.g., "Update", 'Help"
 * and a 'BarPanel' that holds the vertical bars and labels.
 * @author kstephe2, 5/2020
 *
 */

public abstract class BarGraphFrame extends JFrame implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	CPScreen cpScreen;
	PackData packData;
	
	// try to starting with these sizes
	public static final int FRAME_WIDE=400; // likely to be adjusted
	public static final int FRAME_HIGH=300; 
	public static final int BAR_HEIGHT=270; //
	public static final int BAR_FOOTPRINT=25; // horizontal footprint of each bar
	public static final int SCALE_PADDING=60; // padding on left for scale text
	public static final int BAR_PADDING=25; // space above/below bars
	public static final int BAR_DROP=30; // how far bar top is below panel top
	public static final int INDX_HEIGHT=50; // height of index labels

	// abstract methods that must be implemented by derived classes
	public abstract void populate(); // create and add the 'ActiveBar's
	public abstract void globalUpdate(); // updating the display from packData
	public abstract void captureValue(double value,int indx); // individual update from mouse action
	public abstract JPanel createScalePanel(); 
	// may want to respond to mouse moving into a bar
	public abstract void mouse_entry_action(int indx); 
	
	// nested panels
	JPanel controlPanel; // option buttons, readouts
	JPanel barPanel;     // scale lines and 'myBars' go here
	xNumField valueDisplay; // for showing values
	
	public ActiveBar []myBar; // number depends on what's being displayed
	
	public StringBuilder helpInfo;

	public BarGraphFrame(PackData p) {
		super();
		packData=p;
		setLocation(new Point(200,100));
		helpInfo=new StringBuilder("This is a widget for adjusting certain radii.");
	}
	
	/**
	 * Basic GUI start, adjusted by instantiating code
	 */
	public void initGUI() {
		setLayout(new BorderLayout());
		
		// Create control/data display area
		controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));// new GridBagLayout());
		
		// "Update" and "Help" will be standard
		JButton button = new JButton("Update");
		button.addActionListener(this);
		button.setActionCommand("Update");
		button.setPreferredSize(new Dimension(90, 22));
		button.setToolTipText("Recompute all values");
		controlPanel.add(button);

		button = new JButton("Help");
		button.addActionListener(this);
		button.setActionCommand("Help");
		button.setPreferredSize(new Dimension(80, 22));
		button.setToolTipText("Help window for some 'Widget'");
		controlPanel.add(button);
		
		// value field
		valueDisplay=new xNumField("value",10);
		controlPanel.add(valueDisplay);
		
		controlPanel.setBounds(1,1,FRAME_WIDE,60);
		controlPanel.setSize(new Dimension(FRAME_WIDE,60));
		controlPanel.setPreferredSize(new Dimension(FRAME_WIDE,60));
		controlPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		add(controlPanel, BorderLayout.NORTH);

		// Create barPanel
		barPanel=createScalePanel();
		barPanel.setSize(new Dimension(FRAME_WIDE,FRAME_HIGH));
		barPanel.setPreferredSize(new Dimension(FRAME_WIDE,FRAME_HIGH));
//		populate();
		add(barPanel,BorderLayout.CENTER);

		pack();
	}
	
	/**
	 * Set the "Help" popup information
	 * @param strbld StringBuilder
	 */
	public void setHelpText(StringBuilder strbld) {
		helpInfo=strbld;
	}
	
	/**
	 * updates the value displayed in the controlPanel
	 * @param val
	 */
	public void valueUpdate(double val) {
		valueDisplay.setField(val);
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
			globalUpdate();
		}
		
	}
	
}


