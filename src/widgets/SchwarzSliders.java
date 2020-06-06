package widgets;

import java.awt.Color;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import allMains.CirclePack;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import packing.PackData;

/**
 * A Frame for sliders controlling specified edge schwarzians.
 * @author kstephe2, June 2020
 *
 */
public class SchwarzSliders extends SliderFrame {
	
	private static final long serialVersionUID = 1L;
	
	EdgeLink edges;  // objects for the widget
	int N;           // number of objects
	int V;           // holds the vertex index, for communicating objects of sliders
	
	// constructors
	public SchwarzSliders(PackData p,EdgeLink elist) {
		this(p,"","",elist);
	}

	public SchwarzSliders(PackData p,String chgcmd,String movcmd,EdgeLink elist) {
		super(p,chgcmd,movcmd);
		if (packData.kData[1].schwarzian==null) {
			CirclePack.cpb.errMsg("slider usage: -S, packing needs to have schwarzians");
		}
		edges=elist;
		N=edges.size();
		setTitle("Selected edge Schwarzians for packing "+packData.packNum);
		setHelpText(new StringBuilder("These sliders control selected edge "
				+ "real schwarzians. The user can specify two command strings, "
				+ "marked 'motion cmd' and change cmd'. When checked to "
				+ "activate, the associated command string will be executed "
				+ "when the mouse enters a slider or changes a slider value, "
				+ "respectively.\n\n"
				+ "Implement with, e.g.\n\n"
				+ "sliders -S -c \"rld\" -m \"disp -wr -c _Obj\" {v...}.\n\n"
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
			EdgeSimple edge=edges.get(j);
			String str=new String(edge.v+" "+edge.w);
			mySliders[j]=new ActiveSlider(this,j,str,getSchwarzian(j),true);
			sliderPanel.add(mySliders[j]);
		}
	}
	
	/**
	 * Update slider values from PackData
	 */
	public void downloadData() {
		for (int j=0;j<N;j++) {
			int n=mySliders[j].slider.convertDouble(getSchwarzian(j));
			mySliders[j].slider.setValue(n);
		}
		this.repaint();
	}

	/**
	 * Get the schwarzian for given edge
	 * @param indx integer
	 * @return
	 */
	public double getSchwarzian(int indx) {
		EdgeSimple edge=edges.get(indx);
		int k=packData.nghb(edge.v,edge.w);
		return packData.kData[edge.v].schwarzian[k];
	}
	
	/**
	 * when a slider changes, it stores the new value in packData
	 */
	public void captureValue(double value,int indx) {
		EdgeSimple edge=edges.get(indx);
		int k=packData.nghb(edge.v,edge.w);
		packData.kData[edge.v].schwarzian[k]=value;
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
		val_max=-1000000;
		Iterator<EdgeSimple> elst=edges.iterator();
		while (elst.hasNext()) {
			EdgeSimple edge=elst.next();
			int k=packData.nghb(edge.v,edge.w);
			double sch=packData.kData[edge.v].schwarzian[k];
			val_min= (sch<val_min) ? sch :val_min;
			val_max= (sch>val_max) ? sch :val_max;
		}
		val_min /=2.0;
		val_max *=2.0;
	}
	
}

