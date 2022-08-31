package panels;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import allMains.CirclePack;
import circlePack.PackControl;
import input.TrafficCenter;

public class DispPanel extends javax.swing.JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	public JTextField flagField;
	private JCheckBox facelabelBox;
	private JCheckBox cirlabelBox;
	private JCheckBox facefillBox;
	private JCheckBox facecolorBox;
	private JCheckBox faceBox;
	private JCheckBox cirfillBox;
	private JCheckBox circcolorBox;
	private JCheckBox cirBox;
	private JCheckBox FlagBox;
	
	protected StringBuilder buildCmd;

	// Constructor
	public DispPanel() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		
		JPanel topPanel=new JPanel(new FlowLayout(FlowLayout.LEFT));
		topPanel.add(getJPanel1());
		
		// two buttons
		JPanel butPanel=new JPanel(null);
		JButton redrawButton=new JButton("Redraw");
		redrawButton.setActionCommand("redraw");
		redrawButton.addActionListener(this);
		redrawButton.setBounds(2,20,100,20);
		
		JButton resetButton=new JButton("Reset to Default");
		resetButton.setActionCommand("reset");
		resetButton.addActionListener(this);
		resetButton.setBounds(2,82,160,20);
		
		butPanel.add(redrawButton);
		butPanel.add(resetButton);
		butPanel.setPreferredSize(new Dimension(180,104));
		
		topPanel.add(butPanel);
		add(topPanel);
		
		JPanel altPanel = new JPanel(null);
		int yval=20;
		altPanel.setBorder(BorderFactory.createTitledBorder("Explicit flags (advanced)"));
		FlagBox = new JCheckBox();
		FlagBox.setText("Use Flags");
		FlagBox.setBounds(5,yval,100,20);
		altPanel.add(FlagBox);
		
		flagField = new JTextField();
		flagField.setBounds(112,yval,PackControl.ControlDim1.width-140,20);
		yval+=20;
		altPanel.add(flagField);

		JButton goFlag=new JButton("Redraw with flags");
		goFlag.setActionCommand("redraw_flags");
		goFlag.addActionListener(this);
		goFlag.setBounds(112,yval,170,20);
		altPanel.add(goFlag);
		altPanel.setPreferredSize(new Dimension(PackControl.ControlDim1.width-20,62));
		
		add(altPanel);
	}
	
	public void actionPerformed(ActionEvent e){
	 	String command = e.getActionCommand();
	  	if (command.equals("reset")) {
	  		PackControl.getActiveCPDrawing().dispOptions.reset();
	  		flagField.setText("");
	  		update(CirclePack.cpb.getActivePackNum(),CirclePack.cpb.getActivePackNum());
	  	}
	  	else if (command.equals("redraw")) {
	  		try {
	  			TrafficCenter.cmdGUI("disp -wr");
	  		} catch (Exception ex) {}
	  		PackControl.activeFrame.activeScreen.repaint();
	  	}
	  	else if (command.equals("redraw_flags")) {
	  		try {
	  			TrafficCenter.cmdGUI("disp "+ flagField.getText());
	  		} catch (Exception ex) {}
	  		PackControl.activeFrame.activeScreen.repaint();
	  	}
	  	else { // rest of actions can be used in call
	  		Object obj=e.getSource();
	  		if (obj instanceof JCheckBox) {
  			  JCheckBox box=(JCheckBox)e.getSource();
  			  PackControl.getActiveCPDrawing().dispOptions.setOnOff(command,box.isSelected());
	  		}
	  	}
	}

	public String getFlags() {
		return flagField.getText();
	}
	
	private JPanel getJPanel1() {
		int yval = 2;
		JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		checkPanel.setBorder(BorderFactory
				.createTitledBorder("Display objects"));

		JPanel circleData = new JPanel(null);
		circleData.setBorder(new LineBorder(Color.black,1,false));
		{
			cirBox = new JCheckBox();
			cirBox.setText("Circles");
			cirBox.setActionCommand("circles");
			cirBox.setFont(new Font("TrueType",Font.BOLD, 12));
			cirBox.setSelected(true);
			cirBox.addActionListener(this);
			cirBox.setBounds(2,yval,80,20);
			yval += 20;
		}
		{
			circcolorBox = new JCheckBox();
			circcolorBox.setText("color?");
			circcolorBox.setFont(new Font("TrueType",Font.PLAIN, 10));
			circcolorBox.setActionCommand("circolor");
			circcolorBox.addActionListener(this);
			circcolorBox.setBounds(15, yval,70,18);
			yval += 20;
		}
		{
			cirfillBox = new JCheckBox();
			cirfillBox.setText("filled?");
			cirfillBox.setFont(new Font("TrueType",Font.PLAIN, 10));
			cirfillBox.setActionCommand("cirfill");
			cirfillBox.addActionListener(this);
			cirfillBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		{
			cirlabelBox = new JCheckBox();
			cirlabelBox.setText("label?");
			cirlabelBox.setFont(new Font("TrueType",Font.PLAIN, 10));
			cirlabelBox.setActionCommand("cirlabels");
			cirlabelBox.addActionListener(this);
			cirlabelBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		circleData.add(cirBox);
		circleData.add(circcolorBox);
		circleData.add(cirfillBox);
		circleData.add(cirlabelBox);
		circleData.setPreferredSize(new Dimension(100,88));
		checkPanel.add(circleData);

		JPanel faceData = new JPanel(null);
		yval = 2;
		faceData
				.setBorder(new LineBorder(new java.awt.Color(0, 0, 0), 1, false));
		{
			faceBox = new JCheckBox();
			faceBox.setText("Faces");
			faceBox.setFont(new Font("TrueType",Font.BOLD, 12));
			faceBox.setActionCommand("faces");
			faceBox.addActionListener(this);
			faceBox.setBounds(2,yval,70,20);
			yval += 20;
		}
		{
			facecolorBox = new JCheckBox();
			facecolorBox.setText("color?");
			facecolorBox.setFont(new Font("TrueType",Font.PLAIN, 10));
			facecolorBox.setActionCommand("facecolor");
			facecolorBox.addActionListener(this);
			facecolorBox.setBounds(15, yval, 70, 18);
			yval += 20;
		}
		{
			facefillBox = new JCheckBox();
			facefillBox.setText("filled?");
			facefillBox.setFont(new Font("TrueType",Font.PLAIN, 10));
			facefillBox.setActionCommand("facefill");
			facefillBox.addActionListener(this);
			facefillBox.setBounds(15,yval,70,18);
			yval += 20;
		}
		{
			facelabelBox = new JCheckBox();
			facelabelBox.setText("label?");
			facelabelBox.setFont(new Font("TrueType",Font.PLAIN, 10));
			facelabelBox.setActionCommand("facelabels");
			facelabelBox.addActionListener(this);
			facelabelBox.setBounds(15,yval,70,18);
			yval += 20;
		}
		faceData.add(faceBox);
		faceData.add(facecolorBox);
		faceData.add(facefillBox);
		faceData.add(facelabelBox);
		faceData.setPreferredSize(new Dimension(100,88));
		checkPanel.add(faceData);

		return checkPanel;
	}
	
	public void setFlagBox(boolean flip) {
		FlagBox.setSelected(flip);
	}
	
	public boolean useText() {
		if (FlagBox.isSelected()) return true;
		return false;
	}
	
	/**
	 * Get display options from the new active packing's 'dispOptions'
	 * (after saving the options of the previous packing). 
	 * @param old_num
	 * @param new_num
	 */
	public void update(int old_pnum,int new_pnum) {
		// store current data for old_pnum
		PackControl.cpDrawing[old_pnum].dispOptions.storeTailored(flagField.getText());
		PackControl.cpDrawing[old_pnum].dispOptions.usetext=FlagBox.isSelected();
		
		// get/set new data
		flagField.setText(PackControl.cpDrawing[new_pnum].dispOptions.tailored);
		Boolean[] bools= PackControl.cpDrawing[new_pnum].dispOptions.getSavedStates();
		cirBox.setSelected(bools[0].booleanValue());
		circcolorBox.setSelected(bools[1].booleanValue());
		cirfillBox.setSelected(bools[2].booleanValue());
		faceBox.setSelected(bools[3].booleanValue());
		facecolorBox.setSelected(bools[4].booleanValue());
		facefillBox.setSelected(bools[5].booleanValue());
		cirlabelBox.setSelected(bools[6].booleanValue());
		facelabelBox.setSelected(bools[7].booleanValue());
		FlagBox.setSelected(bools[8].booleanValue());
	}
	
}
