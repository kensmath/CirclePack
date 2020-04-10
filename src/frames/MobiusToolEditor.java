package frames;

import images.CPIcon;

import java.awt.BorderLayout;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.LayoutStyle;

import mytools.MyTool;
import mytools.MyToolEditor;
import mytools.MyToolHandler;
import util.xNumField;
import util.zNumField;
import allMains.CirclePack;

import complex.Complex;

import exceptions.ParserException;

/**
 * MobiusToolEditor is a popup frame to allow the user to create
 * tools to add to Mobius toolbar. Each tool involves a string of
 * commands for CirclePack, an Icon, and a tooltip. These can be
 * saved in XML format to be read in on startup (going into 
 * hashedTools), but the Mobius tools are generally created only
 * when packings are read in or modified. 
 * @author kens
 */
public class MobiusToolEditor extends MyToolEditor {

	private static final long 
	serialVersionUID = 1L;
	
	private JLabel digitLabel;
	private JSlider digitSlider;
	private JCheckBox orientBox;
	private JPanel so3Panel;
	private Box genPanel;
	private JPanel optionPanel;
	private xNumField thetaField;
	private zNumField alphaField;
	private JPanel discPanel;
	private zNumField BField;
	private zNumField AField;
	private JTabbedPane jTabbedPane1;
	private zNumField dField;
	private zNumField cField;
	private zNumField bField;
	private zNumField aField;
//	ComplexField a_entry;
//	ComplexField b_entry;
//	ComplexField c_entry;
//	ComplexField d_entry;
	// use lila-kde-white icons for mobius transformations
	private static String mobiusIcons[]={"default_icon.jpg","back.png","build.png","connect_no.png",
		"down.png","filefind.png","fork.png","forward.png","hold","properties.png",
		"reload.png","up.png","viewmag-.png","viewmag+.png","window_fullscreen.png",
		"cp_drop.png","rad_minus.png"};
	public boolean oriented;
	
	// Constructor
	public MobiusToolEditor(String tool_type,MyToolHandler par) {
		super(tool_type,par);
		iconDir=new String("mobius");
		for (int i=0;i<mobiusIcons.length;i++) {
			theCPIcons.addElement(new CPIcon(iconDir+"/"+mobiusIcons[i]));
		}
		oriented=true;
		resetIconList();
	}
	
	public JPanel topPanel() {
		JPanel panel=new JPanel();
		try {
			BorderLayout thisLayout = new BorderLayout();
			panel.setLayout(thisLayout);
			panel.setPreferredSize(new java.awt.Dimension(561, 241));
			{
				jTabbedPane1 = new JTabbedPane();
				panel.add(jTabbedPane1, BorderLayout.CENTER);
				jTabbedPane1.setPreferredSize(new java.awt.Dimension(561, 300));
				{
					so3Panel = new JPanel();
					jTabbedPane1.addTab("SO(3)", null, so3Panel, null);
					so3Panel.setBorder(BorderFactory.createTitledBorder("Enter A, B (matix [A, B, -conjB, conjA]"));
					{
						AField = new zNumField("A entry");
						so3Panel.add(AField);
					}
					{
						BField = new zNumField("B entry");
						so3Panel.add(BField);
					}
				}
				{
					discPanel = new JPanel();
					jTabbedPane1.addTab("Unit Disc", null, discPanel, null);
					discPanel.setBorder(BorderFactory.createTitledBorder("Enter alpha, theta"));
					{
						alphaField = new zNumField("alpha (complex)");
						discPanel.add(alphaField);
					}
					{
						thetaField = new xNumField("theta (real)");
						discPanel.add(thetaField);
					}
				}
				{
					genPanel = Box.createVerticalBox();
					jTabbedPane1.addTab("General", null, genPanel, null);
					genPanel.setBorder(BorderFactory.createTitledBorder("Enter a,b,c,d"));
					genPanel.setPreferredSize(new java.awt.Dimension(556, 171));
					{
						aField = new zNumField("a entry");
						genPanel.add(aField);
						aField.setPreferredSize(new java.awt.Dimension(546, 32));
					}
					{
						bField = new zNumField("b entry");
						genPanel.add(bField);
					}
					{
						cField = new zNumField("c_entry");
						genPanel.add(cField);
					}
					{
						dField = new zNumField("d_entry");
						genPanel.add(dField);
					}
				}
			}
			{
				optionPanel = new JPanel();
				panel.add(optionPanel, BorderLayout.SOUTH);
				GroupLayout optionPanelLayout = new GroupLayout((JComponent)optionPanel);
				optionPanel.setLayout(optionPanelLayout);
				optionPanel.setPreferredSize(new java.awt.Dimension(555, 60));
				{
					orientBox = new JCheckBox();
					orientBox.setText("Oriented");
				}
				{
					digitLabel = new JLabel();
					digitLabel.setText("digits");
				}
				{
					digitSlider = new JSlider();
					digitSlider.setToolTipText("How many significant digits");
					digitSlider.setSnapToTicks(true);
					digitSlider.setMaximum(15);
					digitSlider.setMinimum(3);
					digitSlider.setMajorTickSpacing(12);
					digitSlider.setMinorTickSpacing(1);
					digitSlider.setPaintLabels(true);
					digitSlider.setValue(8);
				}
				optionPanelLayout.setHorizontalGroup(optionPanelLayout.createSequentialGroup()
					.addGap(6)
					.addComponent(orientBox, GroupLayout.PREFERRED_SIZE, 94, GroupLayout.PREFERRED_SIZE)
					.addGap(0, 71, Short.MAX_VALUE)
					.addComponent(digitLabel, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addComponent(digitSlider, GroupLayout.PREFERRED_SIZE, 308, GroupLayout.PREFERRED_SIZE)
					.addContainerGap());
				optionPanelLayout.setVerticalGroup(optionPanelLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(optionPanelLayout.createParallelGroup()
					    .addGroup(GroupLayout.Alignment.LEADING, optionPanelLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					        .addComponent(orientBox, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
					        .addComponent(digitLabel, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 21, GroupLayout.PREFERRED_SIZE))
					    .addComponent(digitSlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addContainerGap(18, 18));
			}
			
		} catch(Exception ex) {
			ex.printStackTrace();
			return null;
		}
		topSize=panel.getHeight();
		return panel;
	}
	/** 
	 * default dropability for tools
	 */
	public boolean setDropDefault() {
		return true;
	}

	public String substanceText() {
		return new String("a legitimate Mobius transform.");
	}

	public void reset_abcd() {
		aField.setFields(new Complex(1.0));
		bField.setFields(new Complex(0.0));
		cField.setFields(new Complex(0.0));
		dField.setFields(new Complex(1.0));
	}
	
	public String formulateCmd() {
		Complex a=new Complex(1.0);
		Complex b=new Complex(0.0);
		Complex c=new Complex(0.0);
		Complex d=new Complex(1.0);
		try {
			a=aField.getValue();
			b=bField.getValue();
			c=cField.getValue();
			d=dField.getValue();
		} catch(Exception ex) {} 
		
		String form=null;
		if (orientBox.isSelected()) // orientation reversing 
			form=new String("appMob "
					+a.real()+" "+a.imag()+" "
					+b.real()+" "+b.imag()+" "
					+c.real()+" "+c.imag()+" "
					+d.real()+" "+d.imag()+" -1");
		else // orientation preserving
			form=new String("appMob "
					+a.real()+" "+a.imag()+" "
					+b.real()+" "+b.imag()+" "
					+c.real()+" "+c.imag()+" "
					+d.real()+" "+d.imag());
 		CirclePack.cpb.msg("Created Mobius tool: "+form);
		return form;
	}
	
	/**
	 * Want a checkbox? set default
	 */
	public void dropableCheckBox() {
		wantDropBox=true; 
		dropMode=setDropDefault();
	}

	public void resetMoreFields() {
		reset_abcd();
		iconCombo.iconBox.setSelectedIndex(randomCPIcon()); // indx<0 means no selection
	}

	public void initMoreFields(MyTool theTool) {
		reset_abcd();
		int indx=getCPIconIndx(theTool.getCPIcon());
		if (indx<0)	iconCombo.iconBox.setSelectedIndex(randomCPIcon());
		else iconCombo.iconBox.setSelectedIndex(indx);
		
		// parse the current command to set the fields:
		// Should be form 'appMob a.x a.y b.x b.y c.x c.y d.x d.y [-1]'
		oriented=true;
		String cmds[]=theTool.getCommand().split("\\s+");
		Vector<Double> parts=new Vector<Double>(10);
		try {
			for(int i=1;i<=8;i++) {
				parts.add(Double.parseDouble(cmds[i])); 
			}
			if (cmds.length>9) {
				double flip=Double.parseDouble(cmds[9]);
				if (flip<0) oriented=false;
			}
		} catch(Exception ex) {
			throw new ParserException("Error setting MobiusToolEditor");
		}
		orientBox.setSelected(oriented);
		
		try {
			aField.setFields(new Complex(parts.get(0),parts.get(1)));
			bField.setFields(new Complex(parts.get(2),parts.get(3)));
			cField.setFields(new Complex(parts.get(4),parts.get(5)));
			dField.setFields(new Complex(parts.get(6),parts.get(7)));
		} catch (NumberFormatException e) {
			reset_abcd();
			return;
		}
	}
}
