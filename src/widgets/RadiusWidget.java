package widgets;

import java.util.ArrayList;

import javax.swing.JPanel;

import geometry.HyperbolicMath;
import listManip.NodeLink;
import packing.PackData;

public class RadiusWidget extends BarGraphFrame {

	private static final long serialVersionUID = 1L;
	
	NodeLink verts;  // objects for the widget
	int N;           // number of objects
	int framewidth;
	int frameheight;
	double val_max;
	double val_min;
	
	
	// constructor(s)
	public RadiusWidget(PackData p,NodeLink vlist) {
		super(p);
		N=vlist.size();
		myBar=new ActiveBar[N];
		framewidth=BarGraphFrame.FRAME_WIDE;
		frameheight=FRAME_HIGH;
		// set max/min radii
		val_min=val_max=0.0;
		if (packData.hes>0) 
			val_max=Math.PI;
		if (packData.hes==0)
			for (int v=1;v<=packData.nodeCount;v++) {
				val_max=(packData.rData[v].rad>val_max) ? packData.rData[v].rad : val_max;
			}
		else
			val_max=1.0;

		initGUI();
		setTitle("Radius Manipuation Widget");
	}
	
	public RadiusWidget(PackData p,NodeLink vlist,int wde,int hgh) {
		this(p,vlist);
		framewidth=wde;
		frameheight=hgh;
	}
	
	/* ============== abstract methods ================ */
	
	public void populate() {
		for (int j=0;j<N;j++) {
			myBar[j]=new ActiveBar(this,j,true);
			barPanel.add(myBar[j]);
		}
	}
	
	/**
	 * update the bar values from packData 
	 */
	public void globalUpdate() {
		for (int j=0;j<N;j++) 
			myBar[j].setBarValue(packData.rData[verts.get(j)].rad);
	}
	
	public void captureValue(double value,int indx) {
		int v=verts.get(indx);
		double rad=value;
		if (packData.hes<0) { // convert x-radius to hyperbolic
			rad=HyperbolicMath.x_to_h_rad(value);
		}
		packData.setRadius(v,rad);
	}
	
	public JPanel createScalePanel() {
		ArrayList<String> ticks=new ArrayList<String>(5);
		if (packData.hes>0) { // sphere
			ticks.add(0,"0");
			ticks.add(1,"Pi/4");
			ticks.add(2,"Pi/2");
			ticks.add(3,"3 Pi/4");
			ticks.add(4,"Pi");
		}
		else if (packData.hes==0) { // eucl
			ticks.add(0,"0");
			ticks.add(1,String.format("%.4f",val_max/4.0));
			ticks.add(2,String.format("%.4f",val_max/2.0));
			ticks.add(3,String.format("%.4f",3.0*val_max/4.0));
			ticks.add(4,String.format("%.4f",val_max));
		}
		else { // hyp
			ticks.add(0,"0");
			ticks.add(1,"1/4");
			ticks.add(2,"1/2");
			ticks.add(3,"3/4");
			ticks.add(4,"1");
		}
		return new ScalePanel(framewidth,frameheight,val_min,val_max,ticks);
	}
	
	public void mouse_entry_action(int indx) {
		
	}
}
