package frames;

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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import packQuality.QualMeasures;
import packing.PackData;
import panels.CPScreen;
import panels.DataTree;
import util.intNumField;
import util.xNumField;
import util.zNumField;

/**
 * TODO: I think this is overtaken by "TabbedPackDataHover"
 * Hover panel for pack data frame.
 * @author kens
 *
 */
public class PackDataHover extends HoverPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	// panels
	public JButton updateButton;
	public JPanel vertContainer;
	public JPanel faceContainer;
	public JPanel edgeContainer;
	public JPanel tileContainer;
	public JPanel dataTreePanel;
	
	private JTextField vertChoice;
	private JTextField faceChoice;
	private JTextField tileChoice;
	private JTextField edgeChoice;
	private intNumField colorFieldV;
	private intNumField degreeField;
	private xNumField angleSumField;
	private xNumField aimField;
	private zNumField centerField;
	private xNumField radField;
	private xNumField overlapField;
	private xNumField edgelenField;
	private JTextField flowerField;
	private JLabel jLabel6;
	private JLabel jLabel1;
	private intNumField nextRedField;
	private JCheckBox redCkBox;
	private intNumField nextField;
	private JCheckBox bdryCkBoxV;
	private JTextField vertsField;
	private intNumField colorFieldF;
	
	// heights of containers
	private int vhigh;
	private int ehigh;
	private int fhigh;
	private int treehigh;
	private int totalhigh;

	private DataTree dataTree1;
	
	// Constructor
	public PackDataHover() {
		super(600,400,"Data for Packings");
		hoverFrame.setPreferredSize(new Dimension(myWidth,vhigh));
		lockedFrame.setPreferredSize(new Dimension(myWidth,totalhigh));
		
	}
	
	public void initComponents() {
		this.setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));
		
		// update button
		updateButton=new JButton("Update");
		updateButton.setActionCommand("updateData");
		updateButton.addActionListener(this);
		updateButton.setPreferredSize(new Dimension(-1,24));
		
		// ========== vertex data panel
		vertContainer=new JPanel();
		vertContainer.setLayout(null);
		vertContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.red, 2, false),
				"Vertex Data",TitledBorder.LEADING, TitledBorder.TOP));

		vhigh=14;
		FlowLayout nfl=new FlowLayout(FlowLayout.LEFT);
		JPanel selectArea=new JPanel(nfl);
		jLabel1 = new JLabel("Choose vertex:");
		vertChoice = new JTextField(15);
		vertChoice.setActionCommand("vert_update");
		vertChoice.addActionListener(this);
		vertChoice.setText("");
		vertChoice.setToolTipText("Enter index (or legal description) of vertex");
		selectArea.add(jLabel1);
		selectArea.add(vertChoice);
		selectArea.setBounds(5,vhigh,300,25);
		vhigh += 25;
		vertContainer.add(selectArea);
		
		// combinatoric info
		JPanel combArea=new JPanel(new FlowLayout(FlowLayout.LEFT));
		degreeField = new intNumField("Degree",5);
		degreeField.setEditable(false);
		bdryCkBoxV = new JCheckBox();
		bdryCkBoxV.setFont(new Font("TrueType",Font.PLAIN,10));
		bdryCkBoxV.setText("Bdry?");
		bdryCkBoxV.setPreferredSize(new Dimension(70,20));
		angleSumField = new xNumField("AngleSum/Pi",10);
		angleSumField.setEditable(false); // don't edit
		// flower subpanel
		JPanel flowerList=new JPanel(null);
		jLabel6 = new JLabel();
		jLabel6.setFont(new Font("TrueType",Font.PLAIN,10));
		jLabel6.setText("Flower");
		jLabel6.setBounds(2,1,70,14);
		flowerField = new JTextField(30);
		flowerField.setEditable(false);
		flowerField.setBounds(2,15,260,16);
		flowerList.add(jLabel6);
		flowerList.add(flowerField);
		flowerList.setPreferredSize(new Dimension(262,32));
		combArea.add(degreeField);
		combArea.add(flowerList);
		combArea.add(angleSumField);
		combArea.add(bdryCkBoxV);
		
		combArea.setBounds(5,vhigh,PackControl.ControlDim1.width-20,36);
		vhigh+= 36;
		vertContainer.add(combArea);
		
		JPanel dutyP=new JPanel(new FlowLayout(FlowLayout.LEFT));
		radField = new xNumField("Radius");
		radField.setActionCommand("vert_radius");
		radField.addActionListener(this);
		dutyP.add(radField);
		aimField = new xNumField("Aim/pi");
		aimField.setActionCommand("vert_aim");
		aimField.addActionListener(this);
		dutyP.add(aimField);
		colorFieldV = new intNumField("Color",5);
		colorFieldV.setActionCommand("vert_color");
		colorFieldV.addActionListener(this);
		dutyP.add(colorFieldV);
		centerField = new zNumField("Center");
		centerField.setEditable(true);
		centerField.setActionCommand("vert_center");
		centerField.addActionListener(this);
		dutyP.add(centerField);
		dutyP.setBounds(5,vhigh,PackControl.ControlDim1.width-20,36);
		vhigh +=36;
		vertContainer.add(dutyP);
		
		vhigh+=14; // fixed by trial/error
		vertContainer.setPreferredSize(new Dimension(myWidth,vhigh)); 
		add(vertContainer);
		
		// ============ edge data
		edgeContainer=new JPanel();
		edgeContainer.setLayout(null);
		edgeContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.green, 2,false),
				"Edge Data",TitledBorder.LEADING, TitledBorder.TOP));
		
		ehigh=14;
		nfl=new FlowLayout(FlowLayout.LEFT);
		selectArea=new JPanel(nfl);
		jLabel1 = new JLabel("Choose edge:");
		edgeChoice = new JTextField(10);
		edgeChoice.setActionCommand("edge_update");
		edgeChoice.addActionListener(this);
		edgeChoice.setText("");
		edgeChoice.setToolTipText("Enter index (or legal description) of edge");
		selectArea.add(jLabel1);
		selectArea.add(edgeChoice);
		selectArea.setBounds(5,ehigh,PackControl.ControlDim1.width/2-72,25);
		ehigh += 5; // +=25
		edgeContainer.add(selectArea);

		overlapField = new xNumField("Inv distance");
		overlapField.setBounds(10,ehigh+4,120,32);
		overlapField.setActionCommand("edge_overlap");
		overlapField.addActionListener(this);
		
		edgelenField=new xNumField("edge length");
		edgelenField.setEditable(false);
		edgelenField.setBounds(140,ehigh+4,120,32);
		
		ehigh +=32;
		edgeContainer.add(overlapField);
		edgeContainer.add(edgelenField);
		
		ehigh+=14; // trial/error
		edgeContainer.setPreferredSize(new Dimension(myWidth,ehigh));
			
		// =========== face data
		faceContainer=new JPanel();
		faceContainer.setLayout(null);
		faceContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.blue, 2, false),
				"Face Data",TitledBorder.LEADING, TitledBorder.TOP));
		
		fhigh=14;
		nfl=new FlowLayout(FlowLayout.LEFT);
		selectArea=new JPanel(nfl);
		jLabel1 = new JLabel("Choose face:");
		faceChoice = new JTextField(14);
		faceChoice.setActionCommand("face_update");
		faceChoice.addActionListener(this);
		faceChoice.setText("");
		faceChoice.setToolTipText("Enter index (or legal description) of face");
		selectArea.add(jLabel1);
		selectArea.add(faceChoice);
		selectArea.setBounds(5,fhigh,PackControl.ControlDim1.width/2-20,25);
		fhigh += 25;
		faceContainer.add(selectArea);
		
		//  combinatoric info
		combArea=new JPanel(new FlowLayout(FlowLayout.LEFT));
		colorFieldF = new intNumField("Color",5);
		colorFieldF.setEditable(true);
		colorFieldF.setActionCommand("face_color");
		colorFieldF.addActionListener(this);
		redCkBox = new JCheckBox();
		redCkBox.setFont(new Font("TrueType",Font.PLAIN,10));
		redCkBox.setText("Red?");
		redCkBox.setPreferredSize(new Dimension(60,20));
		nextField=new intNumField("Next",5);
		nextField.setEditable(false);
		nextRedField=new intNumField("Next red",5);
		nextRedField.setEditable(false);
		
		// vertices
		JPanel faceList=new JPanel(null);
		jLabel6 = new JLabel();
		jLabel6.setFont(new Font("TrueType",Font.PLAIN,10));
		jLabel6.setText("Vertices");
		jLabel6.setBounds(2,1,70,14);
		vertsField = new JTextField(30);
		vertsField.setEditable(false);
		vertsField.setBounds(2,15,80,16);
		faceList.add(jLabel6);
		faceList.add(vertsField);
		faceList.setPreferredSize(new Dimension(90,32));
		
		combArea.add(faceList);
		combArea.add(colorFieldF);
		combArea.add(nextField);
		combArea.add(nextRedField);
		combArea.add(redCkBox);

		combArea.setBounds(5,fhigh,PackControl.ControlDim1.width/2+20,36);
		fhigh += 36;
		faceContainer.add(combArea);

		fhigh+=14; // trial/error
		faceContainer.setPreferredSize(new Dimension(myWidth,fhigh));
		
		// =============== tile data
		tileContainer=new JPanel();
		tileContainer.setLayout(null);
		tileContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.orange, 2, false),
				"Tile Data",TitledBorder.LEADING, TitledBorder.TOP));
		
		fhigh=14;
		nfl=new FlowLayout(FlowLayout.LEFT);
		selectArea=new JPanel(nfl);
		jLabel1 = new JLabel("Choose face:");
		tileChoice = new JTextField(14);
		tileChoice.setActionCommand("tile_update");
		tileChoice.addActionListener(this);
		tileChoice.setText("");
		tileChoice.setToolTipText("Enter index (or legal description) of tile");
		selectArea.add(jLabel1);
		selectArea.add(tileChoice);
		selectArea.setBounds(5,fhigh,PackControl.ControlDim1.width/2-20,25);
		fhigh += 25;
		tileContainer.add(selectArea);
		
		// combinatoric info
		combArea=new JPanel(new FlowLayout(FlowLayout.LEFT));
		degreeField = new intNumField("Degree",5);
		degreeField.setEditable(false);
		// flower subpanel
		flowerList=new JPanel(null);
		jLabel6 = new JLabel();
		jLabel6.setFont(new Font("TrueType",Font.PLAIN,10));
		jLabel6.setText("tile Flower");
		jLabel6.setBounds(2,1,70,14);
		flowerField = new JTextField(30);
		flowerField.setEditable(false);
		flowerField.setBounds(2,15,260,16);
		flowerList.add(jLabel6);
		flowerList.add(flowerField);
		flowerList.setPreferredSize(new Dimension(262,32));
		combArea.add(degreeField);
		combArea.add(flowerList);
		
		combArea.setBounds(5,vhigh,PackControl.ControlDim1.width-20,36);
		vhigh+= 15;
		tileContainer.add(combArea);
		tileContainer.setPreferredSize(new Dimension(myWidth,vhigh));
		
		// ============== Pack data tree 
		
		dataTreePanel=new JPanel(null);
		dataTreePanel.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.magenta, 2, false),
				"Pack Data",TitledBorder.LEADING, TitledBorder.TOP));
		dataTree1 = new DataTree();
		dataTree1.setBounds(15,20,myWidth-30,220);
		dataTreePanel.add(dataTree1);
		treehigh=250;
		dataTreePanel.setPreferredSize(new Dimension(myWidth,treehigh));//setBounds(6,15,PackControl.ControlDim1.width-20,175);

		totalhigh=vhigh+ehigh+fhigh+treehigh+20; // 20 is trial/error
	}
	
	public void loadHover() {
		this.removeAll();
		this.add(vertContainer);
	}
	
	public void hoverCall() {
		update_vert(CirclePack.cpb.getActivePackData(),true);
	}
	
	public void loadLocked() {
		this.removeAll();
		updateData(CirclePack.cpb.getActivePackData());
		this.add(updateButton);
		this.add(vertContainer);
		this.add(edgeContainer);
//		this.add(faceContainer);
		this.add(tileContainer);
		this.add(dataTreePanel);
	}
	
	// =========================== update actions ===========================

	/**
	 * Update the "Pack Data" info panels for given
	 * packing (normally, the active pack).
	 * @param p. PackData.
	 */
	public void updateData(PackData p) {
		lockedFrame.setTitle("Data for Packing p"+p.packNum);
		dataTree1.updatePackingData(p);
		update_vert(p,false);
		update_face(p);
		update_edge(p);
	}

	/**
	 * 
	 * @param p
	 * @param useActiveVert, boolean: true, use packings active vert
	 */
	public void update_vert(PackData p,boolean useActive) {
		if (p==null || !p.status) return;
		int v=NodeLink.grab_one_vert(p,vertChoice.getText());
		
		// update for vertChoice, or use the pack active
		if ((v<=0 || v>p.nodeCount) && useActive) {
			int vv=p.activeNode;
			if (vv<=0 || vv>p.nodeCount) 
				v=0;
			else v=p.activeNode;
		}
		
		// reset index field
		if (v<=0 || v>p.nodeCount)
			return;
		vertChoice.setText(Integer.toString(v));
		
		// set radius
		radField.setValue(p.getActualRadius(v));
		
		// set center
		centerField.setValue(new Complex(p.getCenter(v)));
		
		// set aim
		aimField.setValue(p.getAim(v)/Math.PI);
		
		// set angle sum
		angleSumField.setValue(p.getCurv(v)/Math.PI);
		
		// bdry?
		bdryCkBoxV.setSelected(false);
		if (p.kData[v].bdryFlag>0) bdryCkBoxV.setSelected(true);
		
		// degree
		degreeField.setField(p.getNum(v));
		
		// flower
		StringBuilder flower=new StringBuilder();
		for (int j=0;j<=p.kData[v].num;j++)
			flower.append(Integer.toString(p.kData[v].flower[j])+" ");
		flowerField.setText(flower.toString());
		
		// color
		// TODO: color_conversion task, need new GUI method
		colorFieldV.setField(CPScreen.col_to_table(p.getCircleColor(v)));
	}
	
	public void update_face(PackData p) {
		if (p==null || !p.status) return;
		int f=FaceLink.grab_one_face(p,faceChoice.getText());
		if (f<=0) f=1;
		
		// set index field
		faceChoice.setText(Integer.toString(f));

		Face face=p.faces[f];
		int []verts=face.vert;
		

		// list corner vertices
		vertsField.setText(verts[0]+" "+verts[1]+" "+verts[2]);
		
		// color?
		colorFieldF.setField(CPScreen.col_to_table(face.color));
		
		// next face?
		nextField.setField(face.nextFace);
		
		// red?
		redCkBox.setSelected(false);
		if (face.rwbFlag>0) {
			redCkBox.setSelected(true);
			nextRedField.setField(face.nextRed);
		}
	}

	/**
	 * Update the 'edgelength' data in the Info panel.
	 * @param p
	 */
	public void update_edge(PackData p) {
		if (p==null || !p.status) return;
		EdgeSimple edge=EdgeLink.grab_one_edge(p,edgeChoice.getText());
		int j=-1;
		if (edge==null || (j=p.nghb(edge.v,edge.w))<0) 
			return;
		double invDist=1.0;
		if (p.overlapStatus) {
			double iD=p.getInvDist(edge.v,edge.w);
			if (j>=0 && Math.abs(1.0-iD)>.0000001)
				invDist=iD;
		}
		edgeChoice.setText(edge.v+" "+edge.w);
		overlapField.setValue(invDist);
		try {
			double el=QualMeasures.edge_length(p,edge.v,edge.w);
			edgelenField.setValue(el);
		} catch (Exception ex) {}
	}
	
	public void actionPerformed(ActionEvent evt) {
		String cmd=evt.getActionCommand();
		PackData p=CirclePack.cpb.getActivePackData();
		if (cmd.equals("updateData")) 
			updateData(p);
		else if (cmd.equals("edge_update"))
			update_edge(p);
		else if (cmd.equals("face_update"))
			update_face(p);
		else if (cmd.equals("vert_update"))
			update_vert(p,true);
	}

}
