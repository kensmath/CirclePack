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
import combinatorics.komplex.HalfEdge;
import exceptions.ParserException;
import input.CommandStrParser;
import listManip.HalfLink;
import packing.PackData;
import util.TriAspect;
import util.intNumField;

/**
 * A Frame for sliders controlling specified edge schwarzians.
 * @author kstephe2, June 2020
 *
 */
public class SchwarzSliders extends SliderFrame {
	
	private static final long serialVersionUID = 1L;
	
	HalfLink hedges; // objects for the widget
	int root; 	   // root face (if given) is generally laid out first  
	intNumField rootField;  
	
	// constructors
	public SchwarzSliders(PackData p,HalfLink hlist) {
		this(p,"","",hlist);
	}

	public SchwarzSliders(PackData p,String chgcmd,String movcmd,HalfLink hlist) {
		super(p,chgcmd,movcmd);
		type=1;
		root=p.packDCEL.alpha.face.faceIndx;
		if (hlist==null || hlist.size()==0)
			throw new ParserException("usage: slider -S {v w ....}; missing edgelist");
		
		// Note: schwarzians are same for edge and their twins
		hedges=HalfLink.removeDuplicates(hlist,false);
		sliderCount=hedges.size();
		setTitle("Schwarzians for p"+packData.packNum);
		setHelpText(new StringBuilder("These sliders control selected edge "
				+ "intrinsic schwarzians. The user can specify two active command "
				+ "strings, marked 'change cmd' and 'motion cmd'. When checked to "
				+ "activate, the associated command string will be executed "
				+ "when the mouse changes a slider value or enters a slider's label, "
				+ "respectively.\n\n"
				+ "Implement with, e.g.\n\n"
				+ "sliders -S -c \"rld\" -m \"disp -wr -c _Obj"
				+ "\" -o \"layout\" {e...}.\n\n"
				+ "The variable 'Obj' is set to an object when the commands are"
				+ "executed."));
		mySliders=new ActiveSlider[sliderCount];
		initGUI();

		// add extra button and integer field for root
		JButton button = new JButton("Lay Base face");
		button.setBorder(null);
		button.setMargin(new Insets(10,25,10,25));
		button.setPreferredSize(new Dimension(95,20));
			
		rootField=new intNumField("",4);
		rootField.setField(1); // default to face 1
			
		button.addActionListener(new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent evt) {
		    	int rf=rootField.getValue(); // this may have been changed
		    	if (rf>0 && rf<=packData.faceCount)
		    		root=rf;
		    	rootAction();
		    }
		});
		topPanel.add(button);
		topPanel.add(rootField);
		setChangeField(holdChangeCmd);
		setMotionField(holdMotionCmd);
	}
	
	public int getRoot() {
		return rootField.getValue();
	}
	
	/**
	 * Action for root button? Lay out the 'base equilateral' triangle.
	 * Depends on geometry: prefer euclidean or spherical, so tangency
	 * points are on the unit circle at cube roots of unity, with tangency
	 * point of 0th edge at z=1. For hyperbolic, shrink by eucl factor
	 * of .05 first.
	 * @return int, 0 on error
	 */
	public int rootAction() {
		if (root<=0 || root>packData.faceCount) {
			CirclePack.cpb.errMsg("slider usage: specify a valid 'root'");
			return 0;
		}

		// create as 'TriAspect', then transfer to 'packData'
		TriAspect tri=TriAspect.baseEquilateral(packData.hes);
		int[] verts=packData.getFaceVerts(root);
		for (int j=0;j<3;j++) {
			packData.setRadius(verts[j],tri.getRadius(j));
			packData.setCenter(verts[j],tri.getCenter(j));
		}
		
		// display the root face
		return cpCommand("disp -w -ffc90 "+root); 
	}

	// ============= abstract methods ==================
	
	public void populate() {
		ActiveSlider[] tmpSliders = new ActiveSlider[sliderCount];
		Iterator<HalfEdge> his=hedges.iterator();
		int tick=0;
		while (his.hasNext()) {
			HalfEdge edge=his.next();
			// arrange so v < w.
			int vv=edge.origin.vertIndx;
			int ww=edge.twin.origin.vertIndx;
			if (vv>ww) {
				int hld=vv;
				vv=ww;
				ww=hld;
			}
			String str=new String(vv+" "+ww);
			double sch=packData.getSchwarzian(edge);
			tmpSliders[tick]=new ActiveSlider(this,tick,str,sch,true);
			tick++;
		}
		sliderCount=hedges.size();
		mySliders = new ActiveSlider[sliderCount];
		for (int j=0;j<sliderCount;j++) { 
			mySliders[j]=tmpSliders[j];
			sliderPanel.add(mySliders[j]);
		}
	}
	
	public int addObject(String objstr) {
		HalfLink el=new HalfLink(packData,objstr);
		if (el==null || el.size()==0)
			return 0;
		el=HalfLink.removeDuplicates(el, false);
		ActiveSlider[] tmpSliders=new ActiveSlider[sliderCount+el.size()];
		for (int j=0;j<sliderCount;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<HalfEdge> els=el.iterator();
		int hit=0;
		while (els.hasNext()) {
			HalfEdge he=els.next();
			if (hedges.containsVW(he) || hedges.containsVW(he.twin))
				continue;
			if (he.twin.origin.vertIndx<he.origin.vertIndx)
				he=he.twin;
			hedges.add(he);
			double sch=packData.getSchwarzian(he);
			String str=new String(he.toString());
			tmpSliders[sliderCount+hit]=new ActiveSlider(this,sliderCount+hit,str,sch,true);
			sliderPanel.add(tmpSliders[sliderCount+hit]);
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
		HalfLink el=new HalfLink(packData,objstr);
		if (el==null || el.size()==0)
			return 0;
		ActiveSlider[] tmpSliders=new ActiveSlider[sliderCount];
		for (int j=0;j<sliderCount;j++) 
			tmpSliders[j]=mySliders[j];
		Iterator<HalfEdge> els=el.iterator();
		int hit=0;
		while (els.hasNext()) {
			HalfEdge he=els.next();
			int eindx=-1;
			// is this edge in the list?
			if ((eindx=hedges.indexOf(he))<0 && (eindx=hedges.indexOf(he.twin))<0)
				continue;
			for (int j=(eindx+1);j<(sliderCount-hit);j++) {
				tmpSliders[j-1]=tmpSliders[j];
				tmpSliders[j-1].setIndex(j-1);
			}	
			hedges.remove(eindx);
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
		HalfEdge edge=hedges.get(indx);
		double val= packData.getSchwarzian(edge); 
		mySliders[indx].setValue(val);
	}
	
	/**
	 * Stores the slider's value in packData
	 */
	public void upValue(int indx) {
		HalfEdge edge=hedges.get(indx);
		packData.setSchwarzian(edge,mySliders[indx].value);
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
	
	public void setOptCmdField(String cmd) {
		optCmdField.setText(cmd);
	}
	
	public void mouse_entry_action(int indx) {
		motionAction(indx); // see if there's a motion command to execute
	}
	
	public void changeValueField_action(double val, int indx) {
		valueField_action(val,indx);
	}

	/**
	 * Set the initial values of val_min and val_max. 
	 */
	public void initRange() {
		val_min=1000000;
		val_max=-1000000;
		Iterator<HalfEdge> his=hedges.iterator();
		while (his.hasNext()) {
			HalfEdge he=his.next();
			double sch=packData.getSchwarzian(he);
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

