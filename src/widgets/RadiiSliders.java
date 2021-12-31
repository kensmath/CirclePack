package widgets;

import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;

import javax.swing.JPanel;

import input.CommandStrParser;
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
//	int N;           // number of objects

	
	// constructors
	public RadiiSliders(PackData p,NodeLink vlist) {
		this(p,"","",vlist);
	}

	public RadiiSliders(PackData p,String chgcmd,String movcmd,NodeLink vlist) {
		super(p,chgcmd,movcmd);
		type=0;
		
		// throw back to CirclePack to kill this window
		this.addWindowListener(new WindowAdapter(){  
	        public void windowClosing(WindowEvent e) {
	        	killMe();
	        }
		});

		verts=NodeLink.removeDuplicates(vlist);
		sliderCount=verts.size();
		setTitle("Radii from p"+packData.packNum);
		setHelpText(new StringBuilder("These sliders control selected radii. The "
				+ "user can specify two active command strings, 'change cmd' and "
				+ "'motion cmd'. When checked to activate, the associated command "
				+ "string will be executed when the mouse changes a slider or "
				+ "enters slider label, respectively.\n\n"
				+ "Implement with, e.g.\n\n"
				+ "sliders -R -c ''rld'' -m ''disp -wr -c_Obj'' "
				+ "-o ''dual_layout'' {v...}.\n\n"
				+ "The variable 'Obj' is set to an object when the commands are"
				+ "executed."));
		mySliders=new ActiveSlider[sliderCount];
		initGUI();

		setChangeField(holdChangeCmd);
		setMotionField(holdMotionCmd);
	}

	// ============= abstract methods ==================
	public void populate() {
		mySliders = new ActiveSlider[sliderCount];
		Iterator<Integer> vlst=verts.iterator();
		int tick=0;
		while (vlst.hasNext()) {
			int v=vlst.next();
			mySliders[tick]=new ActiveSlider(this,tick,Integer.toString(v),
					packData.getRadius(v),true);
			sliderPanel.add(mySliders[tick]);
			tick++;
		}
	}
	
	public int addObject(String objstr) {
		NodeLink nl=new NodeLink(packData,objstr);
		if (nl==null || nl.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[+sliderCount+nl.size()];
		for (int j=0;j<sliderCount;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<Integer> nls=nl.iterator();
		int hit=0;
		while (nls.hasNext()) {
			Integer v=nls.next();
			if (verts.contains(v))
				continue;
			String str=Integer.toString(v);
			double rad=packData.getRadius(v);
			tmpSliders[sliderCount+hit]=
					new ActiveSlider(this,sliderCount+hit,str,rad,true);
			sliderPanel.add(tmpSliders[sliderCount+hit]);
			verts.add(v);
			hit++;
		}
		if (hit>0) {
			sliderCount += hit;
			mySliders=new ActiveSlider[sliderCount];
			for (int j=0;j<sliderCount;j++)
				mySliders[j]=tmpSliders[j];
		}
		this.pack();
		return hit;
	}
	
	public int removeObject(String objstr) {
		NodeLink nl=new NodeLink(packData,objstr);
		if (nl==null || nl.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[sliderCount];
		for (int j=0;j<sliderCount;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<Integer> vls=nl.iterator();
		int hit=0;
		while (vls.hasNext()) {
			int v=vls.next();
			int vindx=-1;
			if ((vindx=verts.containsV(v))<0) // no such object
				continue;
			for (int j=(vindx+1);j<(sliderCount-hit);j++) {
				tmpSliders[j-1]=tmpSliders[j];
				tmpSliders[j-1].setIndex(j-1);
			}	
			verts.remove(vindx);
			sliderPanel.remove(mySliders[vindx]);
			mySliders[vindx]=null;
			hit++;
		}
		
		if (hit>0) {
			sliderCount -= hit;
			mySliders=new ActiveSlider[sliderCount];
			for (int j=0;j<sliderCount;j++)
				mySliders[j]=tmpSliders[j];
		}
		this.pack();
		this.repaint();
		return hit;
	}
		
	/**
	 * when a slider changes, it sends the new value to packData
	 */
	public void upValue(int indx) {
		int v=verts.get(indx);
		packData.setRadius(v,mySliders[indx].value);
	}
	
	/**
	 * Set slider value from packing data
	 */
	public void downValue(int indx) {
		int v=verts.get(indx);
		mySliders[indx].setValue(packData.getRadius(v));
	}
	
	/**
	 * Done here in case there are embellishments to the panel
	 */
	public void createSliderPanel() {
		sliderPanel=new JPanel();
        sliderPanel.setBackground(new Color(200,230,255));
	}
	
	public void setChangeField(String cmd) {
		changeCmdField.setText(cmd);
	}
	
	public void setMotionField(String cmd) {
		motionCmdField.setText(cmd);
	}
	
	public void setOptCmdField(String cmd) {
		optCmdField.setText(cmd);
	}
	
	public void mouse_entry_action(int indx) {
		motionAction(indx); // see if there's a motion command to execute
	}
	
	public void changeValueField_action(double val, int indx) {
		valueField_action(val,indx);
	}

	public void initRange() {
		val_min=1000000;
		val_max=-1;
		Iterator<Integer> vlst=verts.iterator();
		while (vlst.hasNext()) {
			int v=vlst.next();
			double rad=packData.getRadius(v);
			val_min= (rad<val_min) ? rad :val_min;
			val_max= (rad>val_max) ? rad :val_max;
		}
		val_min /=2.0;
		val_max *=2.0;
	}

	public void killMe() {
		CommandStrParser.jexecute(packData,"slider -R -x");
	}

}

