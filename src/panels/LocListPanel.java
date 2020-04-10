package panels;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;
import input.TrafficCenter;
import komplex.EdgeSimple;
import packing.PackData;

public class LocListPanel extends JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;
	
	private JTabbedPane VFETabbing;
	private JTextArea VlistArea;
	private JTextArea ElistArea;
	private JTextArea FlistArea;
	private JPanel edgeTab;
	private JPanel faceTab;
	private JPanel vertTab;

	public LocListPanel() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		try {
			this.setLayout(new BorderLayout());
			this.setPreferredSize(new java.awt.Dimension(PackControl.ControlDim1.width, 260));
			{
				VFETabbing = new JTabbedPane();
				add(VFETabbing, BorderLayout.CENTER);
//				VFETabbing.setPreferredSize(new java.awt.Dimension(403, 300));
				{
					vertTab = new JPanel();
					GroupLayout jPanel0Layout = new GroupLayout((JComponent)vertTab);
					vertTab.setLayout(jPanel0Layout);
					{
						VlistArea = new JTextArea();
						util.EmacsBindings.addEmacsBindings(VlistArea);
						VlistArea.setBorder(BorderFactory.createTitledBorder(null,"vlist (global)",TitledBorder.LEADING,TitledBorder.TOP,new java.awt.Font("Dialog",1,12),new java.awt.Color(0,0,255)));
					}
					jPanel0Layout.setVerticalGroup(jPanel0Layout.createSequentialGroup());
					jPanel0Layout.setHorizontalGroup(jPanel0Layout.createSequentialGroup());
					vertTab.add(VlistArea);
					VFETabbing.addTab("Pack vertices", null, vertTab, null);
				}
				{
					faceTab = new JPanel();
					GroupLayout jPanel1Layout = new GroupLayout((JComponent)faceTab);
					faceTab.setLayout(jPanel1Layout);
					{
						FlistArea= new JTextArea();
						util.EmacsBindings.addEmacsBindings(FlistArea);
						FlistArea.setBorder(BorderFactory.createTitledBorder(null, "flist (global)", TitledBorder.LEADING, TitledBorder.TOP, new java.awt.Font("Dialog",1,12), new java.awt.Color(0,0,255)));
					}
					jPanel1Layout.setVerticalGroup(jPanel1Layout.createSequentialGroup());
					jPanel1Layout.setHorizontalGroup(jPanel1Layout.createSequentialGroup());
					faceTab.add(FlistArea);
					VFETabbing.addTab("Pack faces", null, faceTab, null);
				}
				{
					edgeTab = new JPanel();
					GroupLayout jPanel2Layout = new GroupLayout((JComponent)edgeTab);
					edgeTab.setLayout(jPanel2Layout);
					{
						ElistArea = new JTextArea();
						util.EmacsBindings.addEmacsBindings(ElistArea);
						ElistArea.setBorder(BorderFactory.createTitledBorder(null, "elist (global)", TitledBorder.LEADING, TitledBorder.TOP, new java.awt.Font("Dialog",1,12), new java.awt.Color(0,0,255)));
					}
					jPanel2Layout.setVerticalGroup(jPanel2Layout.createSequentialGroup());
					jPanel2Layout.setHorizontalGroup(jPanel2Layout.createSequentialGroup());
					edgeTab.add(ElistArea);
					VFETabbing.addTab("Pack Edges", null, edgeTab, null);
				}
				
				// buttons across bottom
				JPanel buttonPanel=new JPanel();
				buttonPanel.setPreferredSize(new Dimension(-1,30));
				{
					JButton button=new JButton("Get from Active");
					button.setActionCommand("getLoc");
					button.addActionListener(this);
					button.setSize(160,24);
					buttonPanel.add(button);
					
					button=new JButton("Put to Active");
					button.setActionCommand("putLoc");
					button.addActionListener(this);
					button.setSize(160,24);
					buttonPanel.add(button);
					
					button=new JButton("Append to Active");
					button.setActionCommand("appendLoc");
					button.addActionListener(this);
					button.setSize(160,24);
					buttonPanel.add(button);
				}
				
				add(buttonPanel,BorderLayout.SOUTH);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void actionPerformed(ActionEvent aev) {
		String cmd=aev.getActionCommand();
		Component cmpt=VFETabbing.getSelectedComponent();
		if (cmpt==null) return;
		StringBuilder theCmd=new StringBuilder();
		PackData p=CirclePack.cpb.getActivePackData();
		
		if (cmd.equals("getLoc")) {
			if (cmpt==(Component)vertTab) {
				Iterator<Integer> vlist=p.vlist.iterator();
				while (vlist.hasNext() && theCmd.length()<2000) {
					theCmd.append(Integer.toString((Integer)vlist.next())+" ");
				}
				VlistArea.setText(theCmd.toString());
			}
			else if (cmpt==(Component)edgeTab) {
				Iterator<EdgeSimple> elist=p.elist.iterator();
				while (elist.hasNext() && theCmd.length()<2000) {
					EdgeSimple edge=(EdgeSimple)elist.next();
					theCmd.append(Integer.toString(edge.v)+" "+
							Integer.toString(edge.w)+"  ");
				}
				ElistArea.setText(theCmd.toString());
			}
			if (cmpt==(Component)faceTab) {
				Iterator<Integer> flist=p.flist.iterator();
				while (flist.hasNext() && theCmd.length()<2000) {
					theCmd.append(Integer.toString((Integer)flist.next())+" ");
				}
				FlistArea.setText(theCmd.toString());
			}
			return;
		}
		
		boolean append=false;
		if (cmd.startsWith("append")) 
			append=true;
		
		if (cmpt==(Component)vertTab) {
			theCmd.append("set_vlist ");
			if (append) theCmd.append("vlist ");
			theCmd.append(VlistArea.getText());
		}
		else if (cmpt==(Component)edgeTab) {
			theCmd.append("set_elist ");
			if (append) theCmd.append("elist ");
			theCmd.append(ElistArea.getText());
		}
		else if (cmpt==(Component)faceTab) {
			theCmd.append("set_flist ");
			if (append) theCmd.append("flist ");
			theCmd.append(FlistArea.getText());
		}
		
		try {
			TrafficCenter.cmdGUI(CirclePack.cpb.getActivePackData(),theCmd.toString());
		} catch (Exception ex) {
			throw new ParserException("Error processing list tab");
		}
	}

}
