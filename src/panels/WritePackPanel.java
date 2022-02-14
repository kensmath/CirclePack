package panels;

import input.FileDialogs;
import input.TrafficCenter;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.LayoutStyle;
import javax.swing.border.BevelBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import circlePack.PackControl;
import exceptions.InOutException;

/**
* This code was edited or generated using CloudGarden's Jigloo
* SWT/Swing GUI Builder, which is free for non-commercial
* use. If Jigloo is being used commercially (ie, by a corporation,
* company or business for any purpose whatever) then you
* should purchase a license for each developer using Jigloo.
* Please visit www.cloudgarden.com for details.
* Use of Jigloo implies acceptance of these licensing terms.
* A COMMERCIAL LICENSE HAS NOT BEEN PURCHASED FOR
* THIS MACHINE, SO JIGLOO OR THIS CODE CANNOT BE USED
* LEGALLY FOR ANY CORPORATE OR COMMERCIAL PURPOSE.
*/
public class WritePackPanel extends javax.swing.JPanel {
	
	private static final long 
	serialVersionUID = 1L;
	
	private JCheckBox comBox;
	private JCheckBox geomBox;
	private JCheckBox vefBox;
	private JCheckBox plotBox;
	private JCheckBox edgeBox;
	private JCheckBox xyzBox;
	private JCheckBox colorBox;
	private JCheckBox vertMapBox;
	private JCheckBox dispFlagsBox;
	private JCheckBox tileBox;
	private JCheckBox angsumBox;
	private JCheckBox aimBox;
	private JRadioButton writeButton;
	private JPanel jPanel3;
	private AbstractAction writeAction;
	private JButton jButton1;
	private JPanel jPanel2;
	private ButtonGroup optionGroup;
	private JRadioButton scriptButton;
	private JRadioButton appendButton;
	private JCheckBox centBox;
	private JCheckBox radBox;

	// Constructor
	public WritePackPanel() {
		super();
		initGUI();
		getOptionGroup();
	}
	
	private void initGUI() {
		try {
			GroupLayout thisLayout = new GroupLayout((JComponent)this);
			this.setLayout(thisLayout);
			thisLayout.setVerticalGroup(thisLayout.createSequentialGroup()
				.addContainerGap(5,5) // 19, 19)
				.addGroup(thisLayout.createParallelGroup()
				    .addComponent(getJPanel3(), GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 120, GroupLayout.PREFERRED_SIZE) // 186
				    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
				        .addGap(0, 3, 8)
				        .addComponent(getJPanel2(), GroupLayout.PREFERRED_SIZE, 90, GroupLayout.PREFERRED_SIZE) 
				        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
				        .addComponent(getJButton1(), GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
				        .addGap(6))) 
				.addContainerGap(15, 15)); 
			thisLayout.setHorizontalGroup(thisLayout.createSequentialGroup()
				.addContainerGap()
				.addComponent(getJPanel3(), GroupLayout.PREFERRED_SIZE, 390, GroupLayout.PREFERRED_SIZE) 
				.addGap(2) 
				.addGroup(thisLayout.createParallelGroup()
				    .addComponent(getJPanel2(), GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 127, GroupLayout.PREFERRED_SIZE)
				    .addComponent(getJButton1(), GroupLayout.Alignment.LEADING, 0, 127, Short.MAX_VALUE))
				.addContainerGap(2,2)); 
			setPreferredSize(new Dimension(400, 200)); // 300
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private JRadioButton getWriteButton() {
		if(writeButton == null) {
			writeButton = new JRadioButton();
			writeButton.setText("Write as File");
			writeButton.setSelected(true);
			writeButton.setActionCommand("tofile");
			writeButton.setBackground(new java.awt.Color(70,235,231));
		}
		return writeButton;
	}
	
	private JRadioButton getAppendButton() {
		if(appendButton == null) {
			appendButton = new JRadioButton();
			appendButton.setText("Append to File");
			appendButton.setActionCommand("appendtofile");
			appendButton.setBackground(new java.awt.Color(70,235,231));
		}
		return appendButton;
	}
	
	private JRadioButton getScriptButton() {
		if(scriptButton == null) {
			scriptButton = new JRadioButton();
			scriptButton.setText("Append Script");
			scriptButton.setActionCommand("appendtoscript");
			scriptButton.setBackground(new java.awt.Color(70,235,231));
		}
		return scriptButton;
	}

	private ButtonGroup getOptionGroup() {
		if(optionGroup == null) {
			optionGroup = new ButtonGroup();
			optionGroup.add(writeButton);
			optionGroup.add(appendButton);
			optionGroup.add(scriptButton);
		}
		return optionGroup;
	}
	
	/**
	 * panel of write options: write/append/script
	 * @return
	 */
	private JPanel getJPanel2() {
		if(jPanel2 == null) {
			jPanel2 = new JPanel();
			GroupLayout jPanel2Layout = new GroupLayout((JComponent)jPanel2);
			jPanel2.setLayout(jPanel2Layout);
			jPanel2.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			jPanel2.setBackground(new java.awt.Color(70,235,231));
			jPanel2Layout.setVerticalGroup(jPanel2Layout.createSequentialGroup()
				.addGroup(jPanel2Layout.createSequentialGroup()
				    .addComponent(getWriteButton(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
				.addGap(3) // 13
				.addGroup(jPanel2Layout.createSequentialGroup()
				    .addComponent(getAppendButton(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
				.addGap(3) // 15
				.addGroup(jPanel2Layout.createSequentialGroup()
				    .addComponent(getScriptButton(), GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)));
			jPanel2Layout.setHorizontalGroup(jPanel2Layout.createSequentialGroup()
				.addGroup(jPanel2Layout.createParallelGroup()
				    .addGroup(jPanel2Layout.createSequentialGroup()
				        .addComponent(getScriptButton(), GroupLayout.PREFERRED_SIZE, 118, GroupLayout.PREFERRED_SIZE))
				    .addGroup(jPanel2Layout.createSequentialGroup()
				        .addComponent(getAppendButton(), GroupLayout.PREFERRED_SIZE, 118, GroupLayout.PREFERRED_SIZE))
				    .addGroup(jPanel2Layout.createSequentialGroup()
				        .addComponent(getWriteButton(), GroupLayout.PREFERRED_SIZE, 118, GroupLayout.PREFERRED_SIZE))));
		}
		return jPanel2;
	}
	
	/**
	 * This polls the checkboxes and returns the string with
	 * the corresponding flags. See 'write' in 'CmdDetails'
	 * help file.
	 *
	 */
	public String formWriteFlags() {
		StringBuilder flags=new StringBuilder("-");
		if (comBox.isSelected()) flags.append("c");
		if (geomBox.isSelected()) flags.append("g");
		if (radBox.isSelected()) flags.append("r");
		if (centBox.isSelected()) flags.append("z");
		if (aimBox.isSelected()) flags.append("i");
		if (angsumBox.isSelected()) flags.append("a");
		if (vertMapBox.isSelected()) flags.append("v");
		if (dispFlagsBox.isSelected()) flags.append("d");
		if (tileBox.isSelected()) flags.append("T");
		if (colorBox.isSelected()) flags.append("o");
		if (vefBox.isSelected()) flags.append("l");
		if (edgeBox.isSelected()) flags.append("e");
		if (plotBox.isSelected()) flags.append("f");
		if (xyzBox.isSelected()) flags.append("x");
		
		if (flags.length()>1) return new String(flags.toString());
		else return null;
	}
	
	private JButton getJButton1() {
		if(jButton1 == null) {
			jButton1 = new JButton();
			jButton1.setText("Write");
			jButton1.setAction(getWriteAction());
		}
		return jButton1;
	}
	
	private AbstractAction getWriteAction() {
		if(writeAction == null) {
			writeAction = new AbstractAction("writeData", null) {

				private static final long 
				serialVersionUID = 1L;

				public void actionPerformed(ActionEvent evt) {
					if (displayWriteDialog()>0)
						PackControl.outputFrame.setVisible(false);
				}
			};
		}
		return writeAction;
	}

	protected int displayWriteDialog(){

	  	String flags=this.formWriteFlags();
	  	if (flags==null) return -1;
	  	
		String action=optionGroup.getSelection().getActionCommand();
		if (action.equals("tofile") || action.equals("appendtofile")) {
			try {
				File theFile=null;
				if (action.equals("tofile") &&
						(theFile=FileDialogs.saveDialog(FileDialogs.FILE,true))!=null) {
					TrafficCenter.cmdGUI("Write "+flags+" "+theFile);
					return 1;
				}
				else if (action.equals("appendtofile") && 
						(theFile=FileDialogs.saveDialog(FileDialogs.FILE,
								true,"Append data to file"))!=null) {
					TrafficCenter.cmdGUI("Write A"+flags+" "+theFile);
					return 1;
				}
				return -1;
			} catch (Exception ex) {
				throw new InOutException("failed to open file: "+ex.getMessage());
			}
		}
		else if (action.equals("appendtoscript")) {
			if (!(PackControl.scriptManager.isScriptLoaded())) {
				String nsl="ERROR: No script is loaded";
				PackControl.consoleCmd.dispConsoleMsg(nsl);
				PackControl.shellManager.recordError(nsl);
				return -1;
			}
			File theFile;
			if ((theFile=FileDialogs.loadDialog(FileDialogs.ADD2SCRIPT,false))!=null) {
	    		try {
	    			TrafficCenter.cmdGUI("Write "+flags+"s "+theFile);
	    		} catch (Exception ex) {return 0;}
	    		PackControl.scriptManager.includeNewFile(theFile.getName());
	    		PackControl.scriptManager.redisplayCPdataSB();
	    		return 1;
	    	}
	    	return -1;
		}
		return -1;
	}

	/**
	 * panel of checkboxes
	 * @return
	 */
	private JPanel getJPanel3() {
		if(jPanel3 == null) {
			jPanel3 = new JPanel();
			GridLayout jPanel3Layout = new GridLayout(4,4);//(6, 2);
			jPanel3Layout.setColumns(4); //(2);
			jPanel3Layout.setHgap(5);
			jPanel3Layout.setVgap(5);
			jPanel3Layout.setRows(4); // (6);
			jPanel3.setLayout(jPanel3Layout);
			jPanel3.setBorder(BorderFactory.createTitledBorder(new LineBorder(new java.awt.Color(255,0,0), 1, false), "Data to Include", TitledBorder.LEADING, TitledBorder.TOP));
			{
				comBox = new JCheckBox();
				jPanel3.add(comBox);
				comBox.setText("Complex");
				comBox.setSelected(true);
			}
			{
				geomBox = new JCheckBox();
				jPanel3.add(geomBox);
				geomBox.setText("Geometry");
				geomBox.setSelected(true);
			}
			{
				radBox = new JCheckBox();
				jPanel3.add(radBox);
				radBox.setText("Radii");
				radBox.setSelected(true);
			}
			{
				centBox = new JCheckBox();
				jPanel3.add(centBox);
				centBox.setText("Centers");
				centBox.setSelected(true);
			}
			{
				aimBox = new JCheckBox();
				jPanel3.add(aimBox);
				aimBox.setText("Aims");
			}
			{
				angsumBox = new JCheckBox();
				jPanel3.add(angsumBox);
				angsumBox.setText("AngleSums");
			}
			{
				xyzBox = new JCheckBox();
				jPanel3.add(xyzBox);
				xyzBox.setText("XYZ coords");
			}
			{
				plotBox = new JCheckBox();
				jPanel3.add(plotBox);
				plotBox.setText("PlotFlags");
			}
			{
				colorBox = new JCheckBox();
				jPanel3.add(colorBox);
				colorBox.setText("Colors");
			}
			{
				edgeBox = new JCheckBox();
				jPanel3.add(edgeBox);
				edgeBox.setText("SideDescriptions");
			}
			{
				vefBox = new JCheckBox();
				jPanel3.add(vefBox);
				vefBox.setText("V/F/E Lists");
			}
			{
				vertMapBox = new JCheckBox();
				jPanel3.add(vertMapBox);
				vertMapBox.setText("VertexMap");
			}
			{
				dispFlagsBox = new JCheckBox();
				jPanel3.add(dispFlagsBox);
				dispFlagsBox.setText("DispFlags");
			}
			{
				tileBox = new JCheckBox();
				jPanel3.add(tileBox);
				tileBox.setText("Tiling");
				tileBox.setSelected(false);
			}
		}
		return jPanel3;
	}

}
