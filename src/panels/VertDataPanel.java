package panels;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
import komplex.KData;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import packing.PackData;
import packing.RData;
import util.ColorUtil;
import util.intNumField;
import util.xNumField;
import util.zNumField;

public class VertDataPanel extends JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	private JTextField vertChoice;
	private JTextField faceChoice;
	private JTextField edgeChoice;
	private intNumField colorFieldV;
	private intNumField degreeField;
	private xNumField angleSumField;
	private xNumField aimField;
	private zNumField centerField;
	private xNumField radField;
	private xNumField overlapField;
	private JTextField flowerField;
	private JLabel jLabel6;
	private JLabel jLabel1;
	private intNumField nextRedField;
	private JCheckBox redCkBox;
	private intNumField nextField;
	private JCheckBox bdryCkBoxV;
	private JTextField vertsField;
	private intNumField colorFieldF;

	// Constructor
	public VertDataPanel() {
		initGUI();
	}
	
	private void initGUI() {
		setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		
		// ========== vertex data
		JPanel vertContainer=new JPanel();
		vertContainer.setLayout(null);
		vertContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.red, 2, false),
				"Vertex Data",TitledBorder.LEADING, TitledBorder.TOP));

		int yval=14;
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
		selectArea.setBounds(5,yval,300,25);
		yval += 25;
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
		
		combArea.setBounds(5,yval,PackControl.ControlDim1.width-20,36);
		yval += 36;
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
		dutyP.setBounds(5,yval,PackControl.ControlDim1.width-20,36);
		yval +=36;
		vertContainer.add(dutyP);
		
		vertContainer.setPreferredSize(new Dimension(PackControl.ControlDim1.width-10,yval+4));
		add(vertContainer);
		
		
		// --------- face/edge panel
		JPanel lowerPanel=new JPanel(new FlowLayout(FlowLayout.LEFT));

		// TODO: add fields showing angles/area
//		angle0Field = new xNumField("angle 0");
//		angle1Field = new xNumField("angle 1");
//		angle2pField = new xNumField("angle 2");

		// =========== face data
		JPanel faceContainer=new JPanel();
		faceContainer.setLayout(null);
		faceContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.blue, 2, false),
				"Face Data",TitledBorder.LEADING, TitledBorder.TOP));
		
		yval=14;
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
		selectArea.setBounds(5,yval,PackControl.ControlDim1.width/2-20,25);
		yval += 25;
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

		combArea.setBounds(5,yval,PackControl.ControlDim1.width/2+20,36);
		yval += 36;
		faceContainer.add(combArea);

		int YVal=yval+14;
		faceContainer.setPreferredSize(new Dimension(PackControl.ControlDim1.width/2+40,YVal));
		lowerPanel.add(faceContainer);		
		
		// edge data
		
		JPanel edgeContainer=new JPanel();
		edgeContainer.setLayout(new FlowLayout(FlowLayout.LEFT)); // null);
		edgeContainer.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.green, 2,false),
				"Edge Data",TitledBorder.LEADING, TitledBorder.TOP));
		
		yval=14;
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
//		selectArea.setBounds(5,yval,PackControl.ControlDim1.width/2-72,25);
		yval += 25;
		edgeContainer.add(selectArea);

		overlapField = new xNumField("Inv distance");
//		overlapField.setBounds(10,yval+4,120,32);
		overlapField.setActionCommand("edge_overlap");
		overlapField.addActionListener(this);
		yval +=32;
		edgeContainer.add(overlapField);
		
//		edgeContainer.setPreferredSize(new Dimension(PackControl.ControlDim1.width/2-62,YVal));
		
		lowerPanel.add(edgeContainer);
		
		add(lowerPanel);

	}
	
	public void actionPerformed(ActionEvent evt) {
		try {
		PackData p=CirclePack.cpb.getActivePackData();
		if (p==null || !p.status) return;
		String cmd=evt.getActionCommand();
		if (cmd.equals("vert_update"))
			update_vert(p);
		else if (cmd.equals("face_update"))
			update_face(p);
		else if (cmd.equals("edge_update"))
			update_edge(p);
		else if (cmd.equals("face_color")) {
			int f=FaceLink.grab_one_face(p,faceChoice.getText());
			int col=colorFieldF.getValue();
			if (f>0 && f<=p.faceCount && col>=0 && col<=255)
				p.faces[f].color=ColorUtil.coLor(col);
		}
		else if (cmd.equals("vert_color")) {
			int v=NodeLink.grab_one_vert(p,vertChoice.getText());
			int col=colorFieldV.getValue();
			if (v>0 && v<=p.nodeCount && col>=0 && col<=255)
				p.kData[v].color=ColorUtil.coLor(col);
		}
		else if (cmd.equals("vert_radius")) {
			int v=NodeLink.grab_one_vert(p,vertChoice.getText());
			if (v>0 && v<=p.nodeCount)
				p.setRadiusActual(v,radField.getValue());
		}
		else if (cmd.equals("vert_center")) {
			int v=NodeLink.grab_one_vert(p,vertChoice.getText());
			if (v>0 && v<=p.nodeCount) {
				p.setCenter(v,centerField.getValue());
			}
		}
		else if (cmd.equals("vert_aim")) {
			int v=NodeLink.grab_one_vert(p,vertChoice.getText());
			double val=aimField.getValue();
			if (v>0 && v<=p.nodeCount && val>0.0)
				p.setAim(v,val);
		}
		else if (cmd.equals("edge_overlap")) {
			EdgeSimple edge=EdgeLink.grab_one_edge(p,edgeChoice.getText());
			if (edge==null) return;
			double ovlp=overlapField.getValue();
			if (ovlp<=0.0) return;
			int v=edge.v;
			p.set_single_invDist(v,edge.w,ovlp);
		}
		} catch (Exception e) {
			CirclePack.cpb.errMsg("data update error: "+e.getMessage());
		}
		
	}

	public void update(PackData p) {
		update_vert(p);
		update_face(p);
		update_edge(p);
	}

	public void update_vert(PackData p) {
		if (p==null || !p.status) return;
		int v=NodeLink.grab_one_vert(p,vertChoice.getText());
		if (v<=0) {
			v=p.activeNode;
			if (v<=0 || v>p.nodeCount) v=1;
		}
		
		// reset index field
		vertChoice.setText(Integer.toString(v));
		
		KData kdata=p.kData[v];
		RData rdata=p.rData[v];
		
		// set radius
		radField.setValue(p.getActualRadius(v));
		
		// set center
		centerField.setValue(new Complex(rdata.center.x,rdata.center.y));
		
		// set aim
		aimField.setValue(rdata.aim/Math.PI);
		
		// set angle sum
		angleSumField.setValue(rdata.curv/Math.PI);
		
		// bdry?
		bdryCkBoxV.setSelected(false);
		if (kdata.bdryFlag>0) bdryCkBoxV.setSelected(true);
		
		// degree
		degreeField.setField(kdata.num);
		
		// flower
		StringBuilder flower=new StringBuilder();
		for (int j=0;j<=kdata.num;j++)
			flower.append(Integer.toString(kdata.flower[j])+" ");
		flowerField.setText(flower.toString());
		
		// color
		colorFieldV.setField(ColorUtil.col_to_table(kdata.color));
	}
	
	public void update_face(PackData p) {
		if (p==null || !p.status) return;
		int f=FaceLink.grab_one_face(p,faceChoice.getText());
		if (f<=0) f=1;
		
		// set index field
		faceChoice.setText(Integer.toString(f));

		// TODO: need to move to DCEL faces
		
		Face face=p.faces[f];
		int []verts=p.getFaceVerts(f);
		

		// list corner vertices
		vertsField.setText(verts[0]+" "+verts[1]+" "+verts[2]);
		
		// color?
		colorFieldF.setField(ColorUtil.col_to_table(face.color));
		
		// next face?
		nextField.setField(face.nextFace);
		
		// red?
		redCkBox.setSelected(false);
		if (face.rwbFlag>0) {
			redCkBox.setSelected(true);
			nextRedField.setField(face.nextRed);
		}
	}

	public void update_edge(PackData p) {
		if (p==null || !p.status) return;
		EdgeSimple edge=EdgeLink.grab_one_edge(p,edgeChoice.getText());
		if (edge==null) return;
		double invDist=1.0;
		if (p.overlapStatus) {
			int j=p.nghb(edge.v,edge.w);
			double iD=p.getInvDist(edge.v,p.kData[edge.v].flower[j]);
			if (j>=0 && Math.abs(1.0-iD)>.0000001)
				invDist=iD;
		}
		overlapField.setValue(invDist);
	}
}
