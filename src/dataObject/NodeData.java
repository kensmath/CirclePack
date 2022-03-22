package dataObject;

import complex.Complex;
import dcel.PackDCEL;
import dcel.Vertex;
import packing.PackData;
import util.ColorUtil;

/**
 * Gathers data on a vertex, as needed for inquiries or for
 * the 'Pack Info' window in GUI mode.
 * @author kstephe2
 *
 */
public class NodeData {
	PackData parent;
	public int vindx;
	public int degree;
	public String flowerStr;
	public boolean bdryflag;
	public int colorCode;
	public int mark;
	public double aim;
	public double angsum;
	public double rad;
	public Complex center;
	
	public NodeData(PackData p,int indx) {
		parent=p;
		PackDCEL pdcel=p.packDCEL;
		vindx=indx;
		int v=vindx;
		degree=p.countFaces(v);
		Vertex vert=pdcel.vertices[v];
		StringBuilder flowerBuilder = new StringBuilder();
		int[] flwr=p.getFlower(v);
		for (int i = 0; i < flwr.length; i++) {
			flowerBuilder.append(Integer.toString(flwr[i]));
			if (i < (flwr.length-1)) 
				flowerBuilder.append(" ");
		}
		bdryflag=vert.isBdry();
		colorCode=ColorUtil.col_to_table(vert.getColor());
		mark=vert.mark;
		aim=vert.aim;
		angsum=vert.curv;
		rad=p.getActualRadius(v); // in hyp case, hyp radius
		center=new Complex(vert.center);
	}

}
