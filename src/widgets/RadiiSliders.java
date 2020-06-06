package widgets;

import java.awt.Color;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import listManip.NodeLink;
import packing.PackData;

/**
 * A Frame for sliders controlling specified radii.
 * @author kstephe2, June 2020
 *
 */
public class RadiiSliders extends SliderFrame {
	
	private static final long serialVersionUID = 1L;
	
	NodeLink verts;  // objects for the widget
	int N;           // number of objects
	int V;           // holds the vertex index, for communicating objects of sliders
	
	// constructors
	public RadiiSliders(PackData p,NodeLink vlist) {
		this(p,"","",vlist);
	}

	public RadiiSliders(PackData p,String chgcmd,String movcmd,NodeLink vlist) {
		super(p,chgcmd,movcmd);
		verts=vlist;
		N=verts.size();
		setTitle("Selected Radii of packing "+packData.packNum);
		setHelpText(new StringBuilder("These sliders control selected radii. The "
				+ "user can specify two command strings, marked 'motion cmd' and "
				+ "'change cmd'. When checked to activate, the associated command "
				+ "string will be executed when the mouse enters a slider or "
				+ "changes a slider value, respectively.\n\n"
				+ "Implement with, e.g.\n\n"
				+ "sliders -Rad -cc 'rld' -mc 'disp -wr -c_Obj' {v...}.\n\n"
				+ "The variable 'Obj' is set to an object when the commands are"
				+ "executed."));
		mySliders=new ActiveSlider[N];
		initGUI();

		setChangeField(holdChangeCmd);
		setMotionField(holdMotionCmd);
	}

	// ============= abstract methods ==================
	public void populate() {
		for (int j=0;j<N;j++) {
			int v=verts.get(j);
			mySliders[j]=new ActiveSlider(this,j,Integer.toString(v),packData.rData[v].rad,true);
			sliderPanel.add(mySliders[j]);
		}
	}
	
	/**
	 * Update slider values from PackData
	 */
	public void downloadData() {
		for (int j=0;j<N;j++) {
			int v=verts.get(j);
			int n=mySliders[j].slider.convertDouble(packData.rData[v].rad);
			mySliders[j].slider.setValue(n);
		}
		this.repaint();
	}
	
	/**
	 * when a slider changes, it sends the new value
	 */
	public void captureValue(double value,int indx) {
		packData.rData[verts.get(indx)].rad=value;
	}
	
	public void createSliderPanel() {
		sliderPanel=new JPanel();
		sliderPanel.setBorder(BorderFactory.createLineBorder(Color.green));
	}
	
	public void setChangeField(String cmd) {
		changeCmdField.setText(cmd);
	}
	
	public void setMotionField(String cmd) {
		motionCmdField.setText(cmd);
	}
	
	public void mouse_entry_action(int indx) {
		motionAction(indx); // see if there's a motion command to execute
	}
	
	public void setRange() {
		val_min=1000000;
		val_max=-1;
		Iterator<Integer> vlst=verts.iterator();
		while (vlst.hasNext()) {
			int v=vlst.next();
			double rad=packData.rData[v].rad;
			val_min= (rad<val_min) ? rad :val_min;
			val_max= (rad>val_max) ? rad :val_max;
		}
		val_min /=2.0;
		val_max *=2.0;
	}
	
}

