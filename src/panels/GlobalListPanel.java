package panels;
import input.TrafficCenter;

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

import komplex.EdgeSimple;
import allMains.CPBase;
import circlePack.PackControl;
import exceptions.ParserException;

public class GlobalListPanel extends JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;
	
	private JTabbedPane VFETabbing;
	private JTextArea VlistArea;
	private JTextArea ElistArea;
	private JTextArea FlistArea;
	private JPanel edgeTab;
	private JPanel faceTab;
	private JPanel vertTab;

	public GlobalListPanel() {
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
					VFETabbing.addTab("Global Vertices", null, vertTab, null);
					GroupLayout jPanel0Layout = new GroupLayout((JComponent)vertTab);
					vertTab.setLayout(jPanel0Layout);
					{
						VlistArea = new JTextArea();
						util.EmacsBindings.addEmacsBindings(VlistArea);
						vertTab.add(VlistArea);
						VlistArea.setBorder(BorderFactory.createTitledBorder(null,"Vlist (global)",TitledBorder.LEADING,TitledBorder.TOP,new java.awt.Font("Dialog",1,12),new java.awt.Color(0,0,255)));
					}
					jPanel0Layout.setVerticalGroup(jPanel0Layout.createSequentialGroup());
					jPanel0Layout.setHorizontalGroup(jPanel0Layout.createSequentialGroup());
				}
				{
					faceTab = new JPanel();
					VFETabbing.addTab("Global Faces", null, faceTab, null);
					GroupLayout jPanel1Layout = new GroupLayout((JComponent)faceTab);
					faceTab.setLayout(jPanel1Layout);
					{
						FlistArea= new JTextArea();
						util.EmacsBindings.addEmacsBindings(FlistArea);
						FlistArea.setBorder(BorderFactory.createTitledBorder(null, "Flist (global)", TitledBorder.LEADING, TitledBorder.TOP, new java.awt.Font("Dialog",1,12), new java.awt.Color(0,0,255)));
					}
					jPanel1Layout.setVerticalGroup(jPanel1Layout.createSequentialGroup());
					jPanel1Layout.setHorizontalGroup(jPanel1Layout.createSequentialGroup());
				}
				{
					edgeTab = new JPanel();
					VFETabbing.addTab("Global Edges", null, edgeTab, null);
					GroupLayout jPanel2Layout = new GroupLayout((JComponent)edgeTab);
					edgeTab.setLayout(jPanel2Layout);
					{
						ElistArea = new JTextArea();
						util.EmacsBindings.addEmacsBindings(ElistArea);
						ElistArea.setBorder(BorderFactory.createTitledBorder(null, "Elist (global)", TitledBorder.LEADING, TitledBorder.TOP, new java.awt.Font("Dialog",1,12), new java.awt.Color(0,0,255)));
					}
					jPanel2Layout.setVerticalGroup(jPanel2Layout.createSequentialGroup());
					jPanel2Layout.setHorizontalGroup(jPanel2Layout.createSequentialGroup());
				}
				
				// buttons across bottom
				JPanel buttonPanel=new JPanel();
				buttonPanel.setPreferredSize(new Dimension(-1,30));
				{
					JButton button=new JButton("Get from CirclePack");
					button.setActionCommand("getGlobal");
					button.addActionListener(this);
					button.setSize(160,24);
					buttonPanel.add(button);
					
					button=new JButton("Put to CirclePack");
					button.setActionCommand("putGlobal");
					button.addActionListener(this);
					button.setSize(160,24);
					buttonPanel.add(button);
					
					button=new JButton("Append to CirclePack");
					button.setActionCommand("appendGlobal");
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
		
		if (cmd.equals("getGlobal")) {
			if (cmpt==(Component)vertTab) {
				try {
					Iterator<Integer> vlist=CPBase.Vlink.iterator();
				while (vlist.hasNext() && theCmd.length()<2000) {
					theCmd.append(Integer.toString((Integer)vlist.next())+" ");
				}
				VlistArea.setText(theCmd.toString());
				} catch (Exception ex) {
					VlistArea.setText("");
					return;
				}
			}
			else if (cmpt==(Component)edgeTab) {
				try {
					Iterator<EdgeSimple> elist=CPBase.Elink.iterator();
					while (elist.hasNext() && theCmd.length()<2000) {
						EdgeSimple edge=(EdgeSimple)elist.next();
						theCmd.append(Integer.toString(edge.v)+" "+
								Integer.toString(edge.w)+"  ");
					}
					ElistArea.setText(theCmd.toString());
				} catch (Exception ex) {
					ElistArea.setText("");
					return;
				}
			}
			if (cmpt==(Component)faceTab) {
				try {
					Iterator<Integer> flist=CPBase.Flink.iterator();
					while (flist.hasNext() && theCmd.length()<2000) {
						theCmd.append(Integer.toString((Integer)flist.next())+" ");
					}
					FlistArea.setText(theCmd.toString());
				} catch (Exception ex) {
					FlistArea.setText("");
					return;
				}
			}
			return;
		}
		
		boolean append=false;
		if (cmd.startsWith("append")) 
			append=true;
		
		if (cmpt==(Component)vertTab) {
			theCmd.append("set_Vlist ");
			if (append) theCmd.append("Vlist ");
			theCmd.append(VlistArea.getText());
		}
		else if (cmpt==(Component)edgeTab) {
			theCmd.append("set_Elist ");
			if (append) theCmd.append("Elist ");
			theCmd.append(ElistArea.getText());
		}
		else if (cmpt==(Component)faceTab) {
			theCmd.append("set_Flist ");
			if (append) theCmd.append("Flist ");
			theCmd.append(FlistArea.getText());
		}
		
		try {
			TrafficCenter.cmdGUI(theCmd.toString());
		} catch (Exception ex) {
			throw new ParserException("Error processing list tab");
		}
	}

}
