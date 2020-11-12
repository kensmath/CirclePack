package auxFrames;

import geometry.SphericalMath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import packing.PackData;
import panels.CPScreen;
import util.UtilPacket;
import allMains.CirclePack;

public class SphWidget extends JFrame implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	CPScreen cpScreen;
	PackData packData;
	
	public static final int BAR_FOOTPRINT=25; // horizontal footprint of each bar
	public static final int TEXT_PADDING=60; // padding on left for scale text
	public static final int BAR_PADDING=50; // space above/below bars
	public static final int BAR_DROP=30; // how far bar top is below panel top
	public static final int RAD_BAR_HEIGHT=270;
	public static final int ANG_BAR_HEIGHT=200;
	public static final int INDX_HEIGHT=50;
	public static final double RAD_BAR_POWER=9.0;

	JPanel controlPanel; // option buttons, readouts
	JScrollPane mainScroll; // contains barsPanel, scroll for larger packings
	JPanel barsPanel; // barsPanel has rad, angsum, and index panels 
	JPanel radPanel;
	JPanel angsumPanel;
	JPanel indexPanel;
	
	public DisplayBar []radBars; 
	public DisplayBar []angsumBars;
	double []holdRadii;
	
	boolean []lock; // hold radius lock status
	
	// controlPanel stuff
	numberField radiusField;
	numberField angsumField;
	numberField areaField;
	numberField angError; 
	JTextField fileField;
	
	public SphWidget(PackData p) {
		super();
		packData=p;
		setTitle("Sphere Packing Widget, Pack "+packData.packNum);
		lock=new boolean[packData.nodeCount+1];
		for (int v=1;v<=packData.nodeCount;v++)
			lock[v]=false;
		initGUI();
		installData();
		setLocation(new Point(200,100));
	}
	
	public void initGUI() {
		this.setLayout(null);
		
		// Create control/data display area
		controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));// new GridBagLayout());

		// Update button
		JButton button = new JButton("Update");
		button.addActionListener(this);
		button.setActionCommand("Recompute");
		button.setPreferredSize(new Dimension(90, 22));
		button.setToolTipText("Recompute all values");
		controlPanel.add(button);

		// value displays
		angError = new numberField("Sum |Ang Error|/Pi");
		angError.setToolTipText("Sum of abs(angle sum errors)/Pi ");
		angError.setPreferredSize(new Dimension(100, 45));
		controlPanel.add(angError);

		radiusField = new numberField("radius/Pi");
		radiusField.setPreferredSize(new Dimension(100, 40));
		radiusField.setToolTipText("Spherical radius of active vertex/Pi ");
		controlPanel.add(radiusField);

		angsumField = new numberField("Angle/Pi");
		angsumField.setToolTipText("Angle sum of active vertex/Pi ");
		angsumField.setPreferredSize(new Dimension(100, 40));
		controlPanel.add(angsumField);

		areaField = new numberField("Area Error/Pi");
		areaField
				.setToolTipText("Current (spherical) area minus target area/Pi ");
		areaField.setPreferredSize(new Dimension(100, 40));
		controlPanel.add(areaField);

		button = new JButton("Help");
		button.addActionListener(this);
		button.setActionCommand("Help");
		button.setPreferredSize(new Dimension(80, 22));
		button.setToolTipText("Help window for 'SphWidget'");
		controlPanel.add(button);

		// Create panel with rad/ang/index bars
		barsPanel = new JPanel();
		
		// vectors containing data sliders; index from 1
		radBars = new DisplayBar[packData.nodeCount + 1];
		angsumBars = new DisplayBar[packData.nodeCount + 1];

		// panel showing vertical radii bars
		int wide=BAR_FOOTPRINT*packData.nodeCount+TEXT_PADDING+5;
		int high=ANG_BAR_HEIGHT+2*BAR_PADDING+RAD_BAR_HEIGHT+INDX_HEIGHT;
		
		radPanel = new RadBarPanel(wide,RAD_BAR_POWER);
		radPanel.setSize(new Dimension(wide,RAD_BAR_HEIGHT+BAR_PADDING));
		radPanel.setPreferredSize(new Dimension(wide,RAD_BAR_HEIGHT+BAR_PADDING));
		radPanel.setBorder(new TitledBorder(new EtchedBorder(Color.red,
				Color.red), "Radii (using log scale)"));

		// panel showing vertical angle sum bars
		angsumPanel = new AngBarPanel(wide);
		angsumPanel.setSize(new Dimension(wide,ANG_BAR_HEIGHT+BAR_PADDING));
		angsumPanel.setPreferredSize(new Dimension(wide,ANG_BAR_HEIGHT+BAR_PADDING));
		angsumPanel.setBorder(new TitledBorder(new EtchedBorder(Color.blue,
				Color.blue), "Angle Sums"));

		indexPanel = new JPanel();
		indexPanel.setLayout(null);
		indexPanel.setSize(new Dimension(wide,INDX_HEIGHT));
		indexPanel.setPreferredSize(new Dimension(wide,INDX_HEIGHT));
		indexPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
		
		barsPanel.add(radPanel);
		barsPanel.add(angsumPanel);
		barsPanel.add(indexPanel);
		
		barsPanel.setSize(new Dimension(wide,high));
		barsPanel.setPreferredSize(new Dimension(wide,high));
		mainScroll = new JScrollPane(barsPanel);

		// put in frame
		setLayout(new BorderLayout());
		controlPanel.setBounds(1,1,wide,50);
		controlPanel.setSize(new Dimension(wide,50));
		controlPanel.setPreferredSize(new Dimension(wide,50));
		add(controlPanel, BorderLayout.NORTH);
		mainScroll.setBounds(1,51,wide+20,high+5);
		mainScroll.setSize(new Dimension(wide+20,high+5));
		mainScroll.setPreferredSize(new Dimension(wide+20,high+5));
		add(mainScroll,BorderLayout.CENTER);

		pack();
	}
	
	/**
	 * on startup, layout bar panels/put data in place
	 */
	public void installData() {
		radPanel.removeAll();
		radPanel.setLayout(null);
		angsumPanel.removeAll();
		angsumPanel.setLayout(null);
		indexPanel.removeAll();
		indexPanel.setLayout(null);
		JLabel indexLabel;
		// set radius bars (checking for illegal situations)
		for (int j=1;j<=packData.nodeCount;j++) {
			double rad=packData.getRadius(j);
			double mx=SphericalMath.sph_rad_max(packData,j);
			if (rad>=mx) {
				CirclePack.cpb.msg("Illegal radius encountered");
			}
			int startX=TEXT_PADDING+BAR_FOOTPRINT*(j-1);
			int startY=BAR_DROP;
			// set radius
			radBars[j]=new DisplayBar(this,j,true,packData.getRadius(j));
			radBars[j].setBounds(startX,startY,BAR_FOOTPRINT,RAD_BAR_HEIGHT+BAR_PADDING);
			radPanel.add(radBars[j]);
			radBars[j].barArea.setVisible(true);
			radBars[j].placePointer(SphericalMath.sph_rad_max(packData,j));
			if (lock[j]) radBars[j].barArea.setBackground(Color.blue);//new Color(220,220,255));
			
			// compute, set angle sum
			UtilPacket uP=new UtilPacket();
			if (packData.s_anglesum(j,packData.getRadius(j),uP)) {
				packData.rData[j].curv=uP.value;
			}
			else packData.rData[j].curv=0.0;
			angsumBars[j]=new DisplayBar(this,j,false,packData.rData[j].curv);
			angsumBars[j].setBounds(startX,startY,BAR_FOOTPRINT,ANG_BAR_HEIGHT+BAR_PADDING);
			angsumPanel.add(angsumBars[j]);
			angsumBars[j].barArea.setVisible(true);
			if (!lock[j]) // only set initially for unlocked vertices 
				angsumBars[j].placePointer(packData.rData[j].aim);
			
			// enter indices
			indexLabel=new JLabel(Integer.toString(j),JLabel.LEFT);
			indexLabel.setBounds(startX,0,16,15);
			indexPanel.add(indexLabel);
			
		}
		
		// set total area and error
		angError.setText(String.format("%.6e",(double)(packData.angSumError()/Math.PI)));
		radPanel.repaint();
		angsumPanel.repaint();
		indexPanel.repaint();
		
		// decide if area is relevant, compute area target
		int hit=0;
		double aimSum=0.0;
		for (int k=1;k<=packData.nodeCount;k++) {
			if (packData.kData[k].bdryFlag!=0) hit++;
			aimSum+=packData.rData[k].aim-2*Math.PI;
		}
		if (hit>0) areaField.setVisible(false);
		else {
			double targetArea=4*Math.PI+aimSum;
			double curArea=packData.complexArea();
			areaField.setText(String.format("%.6e",(double)((curArea-targetArea)/Math.PI)));
			areaField.setVisible(true);
		}
	}
	
	/**
	 * update the radii and angle sum bars; e.g., after changing radii
	 */
	public void updateBars() {
		for (int v=1;v<=packData.nodeCount;v++) {
			radBars[v].setBarHeight(packData.getRadius(v));
			radBars[v].placePointer(SphericalMath.sph_rad_max(packData,v));
			if (lock[v]) radBars[v].barArea.setBackground(Color.blue);//new Color(220,220,255));
			UtilPacket uP=new UtilPacket();
			packData.s_anglesum(v,packData.getRadius(v),uP);
			angsumBars[v].setBarHeight(uP.value);
			angsumBars[v].placePointer(packData.rData[v].aim);
		}
	}
	
	public void displayRad(int vert) {
		radiusField.setText(String.format("%.6e",
				(double)(packData.getRadius(vert)/Math.PI)));
	}
	
	public void displayAngSum(int vert) {
		angsumField.setText(String.format("%.6e",
				(double)(packData.rData[vert].curv/Math.PI)));
		// display sum of signed errors of (interior) curvatures
		double accum=0.0;
		if (packData.kData[vert].bdryFlag==0) 
			accum=packData.rData[vert].curv-packData.rData[vert].aim;
		for (int j=0;j<packData.kData[vert].num;j++) {
			int k=packData.kData[vert].flower[j];
			if (packData.kData[k].bdryFlag==0)
				accum += Math.abs(packData.rData[k].curv-packData.rData[k].aim);
		}
		angError.setText(String.format("%.6e",(double)(accum/Math.PI)));
	}
	
	public void displayArea() {
		double area=packData.complexArea();
		double aimSum=0.0;
		for (int k=1;k<=packData.nodeCount;k++) {
			aimSum+=packData.rData[k].aim-2*Math.PI;
		}
		areaField.setText(String.format("%.6e",
				(double)(area-4*Math.PI-aimSum/Math.PI)));
	}
	
	public void displayAngError() {
		angError.setText(String.format("%.6e",
				(double)(packData.angSumError()/Math.PI)));
	}
	
	
	public void lightupFlower(int vertnum) {
		for (int j=1;j<=packData.nodeCount;j++) {
			radBars[j].setBarGray();
			angsumBars[j].setBarGray();
		}
		
		if (packData==null || vertnum>packData.nodeCount) return;
		
		radBars[vertnum].setBarRed();
		angsumBars[vertnum].setBarRed();
		displayRad(vertnum);
		displayAngSum(vertnum);
		
		for (int j=0;j<=packData.kData[vertnum].num;j++) {
			int v=packData.kData[vertnum].flower[j];
			radBars[v].setBarGreen();
			angsumBars[v].setBarGreen();
		}			
	}

	public void setLock(int vert) {
		lock[vert]=true;
	}
	
	public void unLock(int vert) {
		lock[vert]=false;
	}
		
	public void setValue(double value,int vert,boolean mode) {
		if (mode) { // radii; new: treat value as logarithmic factor.
			double angsum;
                            // was value*radius -- multiplier each time? 
			packData.setRadius(vert,value);
			UtilPacket uP=new UtilPacket();
			if (packData.s_anglesum(vert,packData.getRadius(vert),uP)) {
				packData.rData[vert].curv=angsum=uP.value;
				angsumBars[vert].setBarHeight(angsum);
				radBars[vert].placePointer(SphericalMath.sph_rad_max(packData,vert));
			}
			int num=packData.kData[vert].num;
			// update petals
			for (int k=0;k<=num;k++) {
				int v=packData.kData[vert].flower[k];
				uP=new UtilPacket();
				if (packData.s_anglesum(v,packData.getRadius(v),uP)) {
					packData.rData[v].curv=angsum=uP.value;
					angsumBars[v].setBarHeight(angsum);
					radBars[v].placePointer(SphericalMath.sph_rad_max(packData,v));
				}
			}	
			displayRad(vert);
			displayAngSum(vert);
		}
		else { // just record chosen aim
			packData.rData[vert].aim=value;
		}
	}
	
	// Process button pressing events
	public void actionPerformed(ActionEvent evt) {
		String cmd=evt.getActionCommand();

		if (cmd.equals("Help")) {
			JFrame auxHelpFrame=new JFrame();
			auxHelpFrame.setTitle("Help for SpherePack");
			JTextArea helpText=new JTextArea();
			helpText.setText(" Mouse and mouse-button action are the main mode of control:\n\n\n"+
			"  * Radii bars in top panel (logarithmic scale, 'stars' indicate max allowed)\n\n"+
			"  * Angle sums are in lower panel\n\n"+
			"  * Active vertex bars are red, its neighbors' green\n\n"+
			"  * Adjust radius bar with left-mouse: grab/move or click to set\n\n"+
			"  * Click right-mouse on radius bar to toggle between lock/unlock\n\n"+
			"  * Click left-mouse or right-mouse inside angle sum bar to position 'aim' target icon\n\n"+
			"  * Packings are limited to 32 vertices\n\n"+
			"  * Buttons let you 'cache' and 'reset' radii\n\n"+
			"  * There are available buttons: any actions you want to propose?\n\n");
			auxHelpFrame.add(helpText);
			helpText.setLineWrap(true);
			helpText.setWrapStyleWord(true);
			util.EmacsBindings.addEmacsBindings(helpText);
			helpText.setPreferredSize(new Dimension(500,500));
			auxHelpFrame.pack();
			auxHelpFrame.setVisible(true);
		}

		else if (cmd.equals("cache radii")) {
			for (int j=1;j<=packData.nodeCount;j++)
				holdRadii[j]=packData.getRadius(j);
		}
		else if (cmd.equals("reset radii") && holdRadii!=null) {
			for (int j=1;j<=packData.nodeCount;j++) {
				packData.setRadius(j,holdRadii[j]);
				radBars[j].setBarHeight(packData.getRadius(j));
				UtilPacket uP=new UtilPacket();
				if (packData.s_anglesum(j,packData.getRadius(j),uP)) {
					packData.rData[j].curv=uP.value;
					angsumBars[j].setBarHeight(packData.rData[j].curv);
				}
			}
		}
		
		else if (cmd.startsWith("Increase") || cmd.startsWith("Decrease")) {
			for (int j=1;j<=packData.nodeCount;j++) {
				if (!lock[j]) {
					double jrad=packData.getRadius(j);
					if (cmd.equals("Increase1"))
						jrad *=1.01;
					else if (cmd.equals("Increase1.1"))
						jrad *=1.001;
					else if (cmd.equals("Increase1.01"))
						jrad *=1.0001;
					else if (cmd.equals("Decrease1"))
						jrad *=.99;
					else if (cmd.equals("Decrease1.1"))
						jrad *=.999;
					else if (cmd.equals("Decrease1.01"))
						jrad *=.9999;
					packData.setRadius(j, jrad);
					
					radBars[j].setBarHeight(packData.getRadius(j));
					UtilPacket uP=new UtilPacket();
					if (packData.s_anglesum(j,packData.getRadius(j),uP)) {
						packData.rData[j].curv=uP.value;
						angsumBars[j].setBarHeight(uP.value);
					}
				}
			}
			displayAngError();
			displayArea();
		}
			
		else if (cmd.equals("Recompute")) {
			updateBars();
			displayAngError();
			displayArea();
		}
		
	}
	/**
	 * internal convenience class
	 */
	public class numberField extends JTextField {

		private static final long 
		serialVersionUID = 1L;
		
		// Constructor
		public numberField(String title) {
			setEditable(false);
			setBorder(new TitledBorder(new EtchedBorder(Color.black,Color.gray),title));
			setMaximumSize(new Dimension(170,20));
			setMinimumSize(new Dimension(170,20));
			setText("--");
		}
		
		public numberField(String title,double value) {
			this(title);
			setText(Double.toString(value));
		}
	}
	
}


