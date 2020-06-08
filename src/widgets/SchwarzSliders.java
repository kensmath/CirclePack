package widgets;

import java.awt.Color;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import allMains.CirclePack;
import input.CommandStrParser;
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
		EdgeLink newEdges= new EdgeLink(packData);
		ActiveSlider[] tmpSliders = new ActiveSlider[edges.size()];
		Iterator<EdgeSimple> elst=edges.iterator();
		int tick=0;
		while (elst.hasNext()) {
			EdgeSimple edge=elst.next();
			if (EdgeLink.getVW(newEdges,edge.v,edge.w)>-1) 
				continue;
			newEdges.add(edge);
			String str=new String(edge.v+" "+edge.w);
			int k=packData.nghb(edge.v,edge.w);
			double sch=packData.kData[edge.v].schwarzian[k];
			tmpSliders[tick]=new ActiveSlider(this,tick,str,sch,true);
			sliderPanel.add(tmpSliders[tick]);
			tick++;
		}
		N=newEdges.size();
		mySliders = new ActiveSlider[N];
		for (int j=0;j<N;j++)
			mySliders[j]=tmpSliders[j];
	}
	
	public int addObject(String objstr) {
		EdgeLink el=new EdgeLink(packData,objstr);
		if (el==null || el.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[N+el.size()];
		for (int j=0;j<N;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<EdgeSimple> els=el.iterator();
		int hit=0;
		while (els.hasNext()) {
			EdgeSimple edge=els.next();
			if (EdgeLink.getVW(edges, edge.v, edge.w)>=0)
				continue;
			String str=new String(edge.v+" "+edge.w);
			int k=packData.nghb(edge.v,edge.w);
			double sch=packData.kData[edge.v].schwarzian[k];
			tmpSliders[N+hit]=new ActiveSlider(this,N+hit,str,sch,true);
			sliderPanel.add(tmpSliders[N+hit]);
			edges.add(edge);
			hit++;
		}
		if (hit>0) {
			N=N+hit;
			mySliders=new ActiveSlider[N];
			for (int j=0;j<N;j++)
				mySliders[j]=tmpSliders[j];
		}
		this.pack();
		return hit;
	}
	
	public int removeObject(String objstr) {
		EdgeLink el=new EdgeLink(packData,objstr);
		if (el==null || el.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[N];
		for (int j=0;j<N;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<EdgeSimple> els=el.iterator();
		int hit=0;
		while (els.hasNext()) {
			EdgeSimple edge=els.next();
			int eindx=-1;
			if ((eindx=EdgeLink.getVW(edges, edge.v, edge.w))<0)
				continue;
			for (int j=(eindx+1);j<(N-hit);j++) {
				tmpSliders[j-1]=tmpSliders[j];
				tmpSliders[j-1].setIndex(j-1);
			}	
			edges.remove(eindx);
			sliderPanel.remove(mySliders[eindx]);
			mySliders[eindx]=null;
			hit++;
		}
		
		if (hit>0) {
			N=N-hit;
			mySliders=new ActiveSlider[N];
			for (int j=0;j<N;j++)
				mySliders[j]=tmpSliders[j];
		}
		this.pack();
		this.repaint();
		return hit;
	}
	
	public int getCount() {
		return N;
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
	
	public void killMe() {
		CommandStrParser.jexecute(packData,"slider -S -x");
	}

}

