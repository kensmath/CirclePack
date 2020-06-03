package widgets;

import java.util.Iterator;
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

	public RadiiSliders(PackData p,NodeLink vlist) {
		super(p);
		verts=vlist;
		N=verts.size();
		setTitle("Selected Radii of packing "+packData.packNum);
		setHelpText(new StringBuilder("Sliders control selected radii. The user can specify "+
				"two command strings, marked 'motion cmd' and 'change cmd'. "+
				"When checked, the associated command string will be executed when the mouse "+
				"enters a slider or changes a slider value, respectively."));
		mySliders=new ActiveSlider[N];
		initGUI();
	}
	
	public RadiiSliders(PackData p,NodeLink vlist,String chgcmd,String movcmd) {
		this(p,vlist);
		setChangeField(chgcmd);
		setMotionField(movcmd);
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
	public void downLoad() {
		for (int j=0;j<N;j++) {
			int v=verts.get(j);
			mySliders[j].setValue(packData.rData[v].rad);
		}
		this.repaint();
	}
	
	public void captureValue(double value,int indx) {
		
	}
	
	public JPanel createSliderPanel() {
		return new JPanel();
	}
	
	public void setChangeField(String cmd) {
		changeCmdField.setText(cmd);
	}
	
	public void setMotionField(String cmd) {
		motionCmdField.setText(cmd);
	}
	
	public void mouse_entry_action(int indx) {
		
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

