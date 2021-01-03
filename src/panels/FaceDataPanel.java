package panels;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;

import allMains.CirclePack;
import komplex.Face;
import listManip.FaceLink;
import packing.PackData;

public class FaceDataPanel extends javax.swing.JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	private JLabel jLabel1;
	private JLabel jLabel2;
	private JTextField chooseField;
	private AbstractAction setFaceAction;
//	private AbstractAction updateAction;
	private JTextField nextRedField;
	private JCheckBox redCkBox;
	private JLabel jLabel5;
	private JLabel jLabel6;
	private JTextField nextField;
	private JCheckBox bdryCkBox;
	private JTextField vertsField;
	private JLabel jLabel4;
	private JTextField faceColorField;
	private JLabel jLabel3;
	
	public FaceDataPanel() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		try {
			GroupLayout thisLayout = new GroupLayout((JComponent)this);
			this.setLayout(thisLayout);
			this.setPreferredSize(new java.awt.Dimension(314, 300));
			this.setFont(new java.awt.Font("AR PL ShanHeiSun Uni",1,14));
			{
				jLabel1 = new JLabel();
				jLabel1.setText("Face Data for Active Packing");
				jLabel1.setFont(new java.awt.Font("AR PL ShanHeiSun Uni",1,14));
			}
			{
				jLabel2 = new JLabel();
				jLabel2.setText("Choose Face:");
			}
			{
				chooseField = new JTextField();
				chooseField.setAction(getSetFaceAction());
			}
			{
				jLabel3 = new JLabel();
				jLabel3.setText("Color:");
			}
			{
				faceColorField = new JTextField();
				faceColorField.setEditable(false);
			}
			{
				jLabel4 = new JLabel();
				jLabel4.setText("Vertices:");
			}
			{
				vertsField = new JTextField();
				vertsField.setEditable(false);
			}
			{
				bdryCkBox = new JCheckBox();
				bdryCkBox.setText("Boundary?");
			}
			{
				redCkBox = new JCheckBox();
				redCkBox.setText("RedChain?");
			}
			{
				jLabel5 = new JLabel();
				jLabel5.setText("Next Face");
			}
			{
				nextField = new JTextField();
				nextField.setEditable(false);
			}
			{
				jLabel6 = new JLabel();
				jLabel6.setText("Next Red:");
			}
			{
				nextRedField = new JTextField();
				nextRedField.setEditable(false);
			}
				thisLayout.setVerticalGroup(thisLayout.createSequentialGroup()
					.addContainerGap()
					.addComponent(jLabel1, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					.addGap(20)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(chooseField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel2, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(vertsField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel4, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addComponent(bdryCkBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(faceColorField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel3, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(nextField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel5, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
					.addComponent(redCkBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(nextRedField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel6, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addContainerGap(68, 68));
				thisLayout.setHorizontalGroup(thisLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(thisLayout.createParallelGroup()
					    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					        .addComponent(jLabel2, GroupLayout.PREFERRED_SIZE, 116, GroupLayout.PREFERRED_SIZE)
					        .addGroup(thisLayout.createParallelGroup()
					            .addComponent(faceColorField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 72, GroupLayout.PREFERRED_SIZE)
					            .addComponent(nextField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 72, GroupLayout.PREFERRED_SIZE))
					        .addGap(26))
					    .addComponent(jLabel1, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addGroup(thisLayout.createSequentialGroup()
					        .addPreferredGap(jLabel2, jLabel3, LayoutStyle.ComponentPlacement.INDENT)
					        .addGroup(thisLayout.createParallelGroup()
					            .addGroup(thisLayout.createSequentialGroup()
					                .addGroup(thisLayout.createParallelGroup()
					                    .addComponent(redCkBox, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 109, GroupLayout.PREFERRED_SIZE)
					                    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                        .addComponent(jLabel3, GroupLayout.PREFERRED_SIZE, 72, GroupLayout.PREFERRED_SIZE)
					                        .addGap(37))
					                    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                        .addComponent(jLabel5, GroupLayout.PREFERRED_SIZE, 99, GroupLayout.PREFERRED_SIZE)
					                        .addGap(10))
					                    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                        .addComponent(jLabel4, GroupLayout.PREFERRED_SIZE, 72, GroupLayout.PREFERRED_SIZE)
					                        .addGap(37))
					                    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                        .addGap(28)
					                        .addComponent(jLabel6, GroupLayout.PREFERRED_SIZE, 81, GroupLayout.PREFERRED_SIZE)))
					                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					                .addGroup(thisLayout.createParallelGroup()
					                    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                        .addComponent(nextRedField, GroupLayout.PREFERRED_SIZE, 34, GroupLayout.PREFERRED_SIZE)
					                        .addGap(38))
					                    .addComponent(vertsField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 72, GroupLayout.PREFERRED_SIZE)
					                    .addComponent(chooseField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 72, GroupLayout.PREFERRED_SIZE)))
					            .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                .addComponent(bdryCkBox, GroupLayout.PREFERRED_SIZE, 122, GroupLayout.PREFERRED_SIZE)
					                .addGap(71)))
					        .addGap(9)))
					.addContainerGap(88, 88));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void update() {
		PackData p=CirclePack.cpb.getActivePackData();
		if (p==null || !p.status) return;
		int f=FaceLink.grab_one_face(p,chooseField.getText());
		if (f<=0) f=1;
		
		// set index field
		chooseField.setText(Integer.toString(f));

		Face face=p.faces[f];
		int []verts=face.vert;
		

		// list corner vertices
		vertsField.setText(verts[0]+" "+verts[1]+" "+verts[2]);
		
		// is it bdry?
		bdryCkBox.setSelected(false);
		if (p.isBdry(verts[0]) || p.isBdry(verts[1]) || p.isBdry(verts[2]))
			bdryCkBox.setSelected(true);
		
		// color?
		faceColorField.setText(Integer.toString(CPScreen.col_to_table(face.color)));
		
		// next face?
		nextField.setText(Integer.toString(face.nextFace));
		
		// red?
		redCkBox.setSelected(false);
		nextRedField.setText("");
		if (face.rwbFlag>0) {
			redCkBox.setSelected(true);
			nextRedField.setText(Integer.toString(face.nextRed));
		}
		
		
 
		
	}
	
	private AbstractAction getSetFaceAction() {
		if(setFaceAction == null) {
			setFaceAction = new AbstractAction("setFace", null) {

				private static final long 
				serialVersionUID = 1L;

				public void actionPerformed(ActionEvent evt) {
					update();
				}
			};
		}
		return setFaceAction;
	}

}
