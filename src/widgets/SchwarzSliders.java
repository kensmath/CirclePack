package widgets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

import javax.swing.JButton;
import javax.swing.JPanel;

import allMains.CirclePack;
import input.CommandStrParser;
import komplex.EdgeSimple;
import komplex.GraphSimple;
import listManip.EdgeLink;
import listManip.GraphLink;
import packing.PackData;
import util.intNumField;

/**
 * A Frame for sliders controlling specified edge schwarzians.
 * @author kstephe2, June 2020
 *
 */
public class SchwarzSliders extends SliderFrame {
	
	private static final long serialVersionUID = 1L;
	
	GraphLink edges;  // objects for the widget
	int root; 		  // root face (if given) is generally laid out first  
	intNumField rootField;  
	
	// constructors
	public SchwarzSliders(PackData p,GraphLink glist) {
		this(p,"","",glist);
	}

	public SchwarzSliders(PackData p,String chgcmd,String movcmd,GraphLink glist) {
		super(p,chgcmd,movcmd);
		root=0;
		if (packData.kData[1].schwarzian==null) {
			CirclePack.cpb.errMsg("slider usage: -S, packing needs to have schwarzians");
		}
		// Note: schwarzians are independent of edge order
		edges=GraphLink.removeDuplicates(glist,false); 
		sliderCount=edges.size();
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
		mySliders=new ActiveSlider[sliderCount];
		initGUI();

		// add extra button and integer field for root
		JButton button = new JButton("Root");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(45,20));
			
		rootField=new intNumField("",4);
		rootField.setField(root);
			
		button.addActionListener(new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent evt) {
		    	int rf=rootField.getValue();
		    	if (rf>0 && rf<=packData.faceCount)
		    		root=rf;
		    	rootAction();
		    }
		});
		topPanel.add(button);
		topPanel.add(rootField);
			
//		this.pack();
		
		setChangeField(holdChangeCmd);
		setMotionField(holdMotionCmd);
	}
	
	public int getRoot() {
		return rootField.getValue();
	}
	
	/**
	 * Action for root button? just hold the index for now
	 * @return 0 for now
	 */
	public int rootAction() {
		return 0;
	}

	// ============= abstract methods ==================
	
	public void populate() {
		GraphLink newEdges= new GraphLink(packData);
		ActiveSlider[] tmpSliders = new ActiveSlider[sliderCount];
		Iterator<EdgeSimple> elst=edges.iterator();
		int tick=0;
		while (elst.hasNext()) {
			GraphSimple edge=new GraphSimple(elst.next());
			if (edge.v==0) { // this gives a root face
				if (edge.w>0 && root==0 && edge.w<=packData.faceCount)
					root=edge.w;
				continue;
			}
			if (GraphLink.getFG(newEdges,edge.v,edge.w)>-1) 
				continue;
			newEdges.add(edge);
			String str=new String(edge.v+" "+edge.w);
			double sch=packData.getSchwarzian(edge);
			tmpSliders[tick]=new ActiveSlider(this,tick,str,sch,true);
			sliderPanel.add(tmpSliders[tick]);
			tick++;
		}
		edges=newEdges;
		sliderCount=edges.size();
		mySliders = new ActiveSlider[sliderCount];
		for (int j=0;j<sliderCount;j++)
			mySliders[j]=tmpSliders[j];
	}
	
	public int addObject(String objstr) {
		GraphLink el=new GraphLink(packData,objstr);
		if (el==null || el.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[sliderCount+el.size()];
		for (int j=0;j<sliderCount;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<EdgeSimple> els=el.iterator();
		int hit=0;
		while (els.hasNext()) {
			GraphSimple edge=(GraphSimple)els.next();
			if (edges.containsFG(edge,false))
				continue;
			String str=new String(edge.v+" "+edge.w);
			EdgeSimple vw=packData.reDualEdge(edge.v,edge.w); // regular edge <v,w>
			int k=packData.nghb(vw.v,vw.w);
			double sch=packData.kData[vw.v].schwarzian[k];
			tmpSliders[sliderCount+hit]=new ActiveSlider(this,sliderCount+hit,str,sch,true);
			sliderPanel.add(tmpSliders[sliderCount+hit]);
			edges.add(edge);
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
		EdgeLink el=new EdgeLink(packData,objstr);
		if (el==null || el.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[sliderCount];
		for (int j=0;j<sliderCount;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<EdgeSimple> els=el.iterator();
		int hit=0;
		while (els.hasNext()) {
			GraphSimple edge=(GraphSimple)els.next();
			int eindx=-1;
			if ((eindx=GraphLink.getFG(edges, edge.v, edge.w))<0)
				continue;
			for (int j=(eindx+1);j<(sliderCount-hit);j++) {
				tmpSliders[j-1]=tmpSliders[j];
				tmpSliders[j-1].setIndex(j-1);
			}	
			edges.remove(eindx);
			sliderPanel.remove(mySliders[eindx]);
			mySliders[eindx]=null;
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
	 * Get the schwarzian for given edge from packData
	 * and set slider.
	 * @param indx integer
	 * @return
	 */
	public void downValue(int indx) {
		GraphSimple edge=(GraphSimple)edges.get(indx);
		double val= packData.getSchwarzian(edge); 
		mySliders[indx].setValue(val);
	}
	
	/**
	 * Stores the slider's value in packData
	 */
	public void upValue(int indx) {
		GraphSimple edge=(GraphSimple)edges.get(indx);
		// find regular edge <v,w>
		EdgeSimple vw=packData.reDualEdge(edge.v,edge.w);
		packData.setSchwarzian(vw,mySliders[indx].value);
	}
	
	/**
	 * Done here in case one wants embellishments
	 */
	public void createSliderPanel() {
		sliderPanel=new JPanel();
        sliderPanel.setBackground(new Color(255,230,200));
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

	/**
	 * Set the initial values of val_min and val_max
	 */
	public void initRange() {
		val_min=1000000;
		val_max=-1000000;
		Iterator<EdgeSimple> elst=edges.iterator();
		while (elst.hasNext()) {
			EdgeSimple es=elst.next();
			if (es.v==0 || es.w==0)
				continue;
			double sch=packData.getSchwarzian(es);
			val_min= (sch<val_min) ? sch :val_min;
			val_max= (sch>val_max) ? sch :val_max;
		}
		double val=Math.abs(val_min);
		double valmax=Math.abs(val_max);
		val= (valmax>val) ? valmax : val;
		if (val<.125) val=.125;
		val_min = -val*2;
		val_max = val*2;
	}
	
	public void killMe() {
		CommandStrParser.jexecute(packData,"slider -S -x");
	}

}

