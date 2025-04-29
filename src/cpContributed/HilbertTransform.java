package cpContributed;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;

public class HilbertTransform extends PackExtender 
{
	private int[] bndry; // number of vertices which belong to the boundary
	private int sz_bndry; // how many boundary circles
	private Complex[] cz_hyper; // hyperbolic centers of boundary circles
	
	@SuppressWarnings("unused")
	private String var; // independent variable
	private String fct; // function to be transformed
	
	private double[] tau; // angle of the hyperbolic centers
	private double[] values_fct; // store the values of the function "fct"
	private double[] values_HilbTransform; // store the values of the Hilbert transform
	
	private ShowFrame frame = null; // frame for showing the Hilbert transform
	
	public HilbertTransform(PackData p) 
	{
		super(p);
		extensionType = "Hilbert_Transform";
		extensionAbbrev = "HT";
		toolTip = "'Hilbert Transform' for computing the Hilbert transform of a given function";
		registerXType();
		
		// max pack the packing p
		cpCommand(extenderPD, "max_pack");
		// store the boundary centers in hyperbolic metric
		int[] temp_bndry = new int[extenderPD.nodeCount + 1]; // not the right size
		Complex[] temp_cz = new Complex[extenderPD.nodeCount + 1]; // not the right size
		sz_bndry = 0;
		for(int k = 1; k <= extenderPD.nodeCount; k++)
		{
			if (extenderPD.isBdry(k))
			{
				temp_bndry[sz_bndry] = k; 
				temp_cz[sz_bndry] = extenderPD.getCenter(k);
				sz_bndry++;
			}
		}
		bndry = new int[sz_bndry]; // right size
		cz_hyper = new Complex[sz_bndry]; // right size
		for(int k = 0; k < sz_bndry; k++)
		{
			bndry[k] = temp_bndry[k];
			cz_hyper[k] = temp_cz[k];
		}
		// sort the vertices such that bndry[k] and bndry[k+1] are neighbors
		sortBoundary();
		// convert to euclidean metric
		cpCommand(extenderPD, "geom_to_e");
		// show maximal packing
		cpCommand(extenderPD, "disp -w -c");
		
		// default function
		var = "t";
		fct = "re(t)";
		
		// allocate space for tau, the function values and its Hilbert transform
		tau = new double[sz_bndry];
		values_fct = new double[sz_bndry];
		values_HilbTransform = new double[sz_bndry];
		
		if (running)
		{
			extenderPD.packExtensions.add(this);
		}
	}
	
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs)
	{
		String str;
		
		if (cmd.startsWith("set_fct"))
		{
			// set the function to be transformed
			Iterator<Vector<String>> iter = flagSegs.iterator();
			
			while(iter.hasNext())
			{
				Vector<String> items = iter.next();
				str = items.remove(0);
				char c = str.charAt(1);
				
				switch (c)
				{
				case 'z':
				{
					var = "z";
					fct = items.remove(0);
				}
				case 't':
				{
					var = "t";
					fct = items.remove(0);
				}
				}
			}
			
			CirclePack.cpb.FtnSpecification=new StringBuilder(fct);
			return 1;
		}
		
		else if (cmd.startsWith("calc_Hilbert_transform")
				|| cmd.startsWith("calc_HT"))
		{
			// calculate the Hilbert transform
			// is there a lambda given?
			double lambda = 1;
			if (flagSegs != null)
			{
				Vector<String> item = flagSegs.get(0);
				Iterator<String> items = item.iterator();
				String option = items.next();
				if (option.compareToIgnoreCase("-l") == 0)
				{
					if (items.hasNext())
						lambda = Double.valueOf(items.next());
					else
						lambda = 1;
				}
			}
			
			// create a copy of the packing
			PackData newPack = extenderPD.copyPackTo();
			CirclePack.cpb.swapPackData(newPack, 1, false);
			
			// modify the radii of the boundary circles
			if (CPBase.GUImode!=0)
				PackControl.newftnFrame.setFunctionText();
			Complex val;
			for(int k = 0; k < sz_bndry; k++)
			{
				val = CirclePack.cpb.getFtnValue(cz_hyper[k]);
				double krad=newPack.getRadius(k);
				newPack.setRadius(bndry[k],krad* Math.exp(lambda * val.real()));
				values_fct[k] = val.real();
			}
			
			// copy the new packing to the panel 1 and then repack it
			cpCommand(newPack, "repack");
			cpCommand(newPack, "layout");
			cpCommand(newPack, "set_screen -a");
			cpCommand(newPack, "disp -w -c");
			cpCommand(extenderPD, "Map 0 1 -o");
			
			// calculate the change of the angle of the boundary edges
			Complex z1, z2, z3, w1, w2, w3, z, w;
			double arg1, arg2, shift;
			double mean = 0;
			double tau1, tau2;
			//values_HilbTransform = new double[sz_bndry];
			//tau = new double[sz_bndry];
			for(int k = 0; k < sz_bndry; k++)
			{
				if (k <= 0)
					z1 = extenderPD.getCenter(bndry[sz_bndry - 1]);
				else
					z1 = extenderPD.getCenter(bndry[k - 1]);
				z2 = extenderPD.getCenter(bndry[k]);
				z3 = extenderPD.getCenter(bndry[(k + 1) % sz_bndry]);
				
				z = z1.minus(z2);
				w = z3.minus(z2);
				z = z.divide(w);
				shift = Math.atan2(z.y, z.x);
				if (shift < 0)
					shift += 2 * Math.PI;
				arg1 = Math.atan2(w.y, w.x) + shift / 2;
				arg1 = Math.atan2(Math.sin(arg1), Math.cos(arg1)); // normalize to [-pi, pi]
				
				if (k <= 0)
					w1 = newPack.getCenter(bndry[sz_bndry - 1]);
				else
					w1 = newPack.getCenter(bndry[k - 1]);
				w2 = newPack.getCenter(bndry[k]);
				w3 = newPack.getCenter(bndry[(k + 1) % sz_bndry]);
				
				z = w1.minus(w2);
				w = w3.minus(w2);
				z = z.divide(w);
				shift = Math.atan2(z.y, z.x);
				if (shift < 0)
					shift += 2 * Math.PI;
				arg2 = Math.atan2(w.y, w.x) + shift / 2;
				arg2 = Math.atan2(Math.sin(arg2), Math.cos(arg2)); // normalize to [-pi, pi]
				
				// elimate a possible jump in the argument
				if ((arg1 >= 3 * Math.PI / 4) && (arg2 <= - 3 * Math.PI / 4))
					arg2 += 2 * Math.PI;
				if ((arg2 >= 3 * Math.PI / 4) && (arg1 <= - 3 * Math.PI / 4))
					arg1 += 2 * Math.PI;
				
				values_HilbTransform[k] = (arg2 - arg1) / lambda;
				
				tau1 = Math.atan2(cz_hyper[k].y, cz_hyper[k].x);
				tau[k] = tau1;
				tau2 = Math.atan2(cz_hyper[(k + 1) % sz_bndry].y, cz_hyper[(k + 1) % sz_bndry].x);
				// mean value of the Hilbert transform
				if ((tau2 - tau1) < 0)
					mean += (arg2 - arg1) * (tau2 - tau1 + 2 * Math.PI) / 2 / Math.PI;
				else
					mean += (arg2 - arg1) * (tau2 - tau1) / (2 * Math.PI);
			}
			for(int k = 0; k < sz_bndry; k++)
				values_HilbTransform[k] -= mean / lambda;
			
			return 1;
		}
		
		else if (cmd.startsWith("show"))
		{		
			// show the function and its Hilbert transform
			Dimension scrsz = Toolkit.getDefaultToolkit().getScreenSize();
			int left = (scrsz.width - 670) / 2;
			int top = (scrsz.height - 670) / 2;
			
			if (frame == null)
				frame = new ShowFrame(tau, values_fct, values_HilbTransform);
			else
				frame.setValues(tau, values_fct, values_HilbTransform);
			
			frame.setTitle("Hilbert transform (red) and original function (blue)");
			frame.setSize(670, 670);
			frame.setLocation(left, top);
			frame.setResizable(false);
			frame.setVisible(true);
			frame.repaint();
			
			return 1;
		}
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	public void initCmdStruct() 
	{
		// description of the commands in the help window
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("set_fct", "-[tz] {fct}", null,
				"Define the function to be transformed. It depends either on t or on z."));
		cmdStruct.add(new CmdStruct("calc_HT", "[-l lambda]", null,
				"Calculate the Hilbert transform. With the option -l the function is scaled by lambda."));
		cmdStruct.add(new CmdStruct("show", "", null,
				"Show the function and its Hilbert transform in a new window."));
	}
		
	public void StartUpMsg() 
	{
		// display in the message box if the user types help
		helpMsg("\nOverview of PackExtender " + extensionAbbrev + " (Hilbert transform):");
		helpMsg("Given a maximal packing and a function f the goal is to compute " + 
				"the Hilbert transform of f. It is done by multiplying the boundary radii " + 
				"with exp(f(t)) where t is the hyperbolic center of the boundary circle. " + 
				"Then repack the packing and determine the difference of the argument of " +
				"the boundary edges.");
		helpMsg("Commands for PackExtender " + extensionAbbrev + " (Hilbert transform)");
		helpMsg("  set_fct -[tz] {fct}    Set the function f\n" +
				"  \n" + 
				"  \n" +
				"  calc_HT [-l lambda]    Determine the Hilbert transform of lambda*f\n" + 
				"  \n" + 
				"  \n" +
				"  show                   Show the function f and its Hilbert transform");
	}
	
	private void sortBoundary()
	{
		// sort the boundary such that bndry[k] and bndry[k+1] are neighbors
		
		int[] nb;
		for(int k = 1; k < sz_bndry; k++)
		{// the first is unchanged
			nb = BoundaryNeighbors(bndry[k - 1]);
			
			if (k <= 1)
				// decide for one direction
				bndry[k] = nb[0];
			else if (Math.abs(bndry[k - 2] -  nb[0]) <= 0)
				// check which of the neighbors is not yet considered
				bndry[k] = nb[1];
			else
				bndry[k] = nb[0];
		}
	}
	
	private int[] BoundaryNeighbors(int vert)
	{
		// determine which neighbor of vert belongs to the boundary
		int[] nb = new int[2];
		int sz_flwr;
		int count = 0;
		
		int[] flwr = extenderPD.getFlower(vert);
		if (extenderPD.isBdry(vert))
			// vert is a boundary vertex
			sz_flwr = flwr.length;
		else
			sz_flwr = flwr.length - 1;
		
		for(int k = 0; k < sz_flwr; k++) {
			if (extenderPD.isBdry(flwr[k])) {
				nb[count] = flwr[k];
				count++;
			}
		}
		return nb;
	}
	
	public class ShowFrame extends Frame implements WindowListener
	{
		// class to show the function and its Hilbert transform
		private static final long serialVersionUID = 1L;
		
		private double[][] values; // the values to display
		
		public ShowFrame(double[] tau, double[] val_f, double[] val_Hf)
		{
			super();
			
			setValues(tau, val_f, val_Hf);
			
			//repaint();
			addWindowListener(this);
		}
		
		public void setValues(double[] tau, double[] val_f, double[] val_Hf)
		{
			// the values to display
			values = new double[3][tau.length];
			for(int k = 0; k < tau.length; k++)
			{
				values[0][k] = tau[k];
				values[1][k] = val_f[k];
				values[2][k] = val_Hf[k];
			}
		}
		
		public void paint(Graphics g)
		{
			Dimension scrsz = Toolkit.getDefaultToolkit().getScreenSize();
			int left = (scrsz.width - 670) / 2;
			int top = (scrsz.height - 670) / 2;
			
			double max_f = 0;
			double max_Hf = 0;
			double max = 0;
			
			for(int k = 0; k < values[0].length; k++)
			{
				if (Math.abs(values[1][k]) > max_f)
					max_f = Math.abs(values[1][k]);
				
				if (Math.abs(values[2][k]) > max_Hf)
					max_Hf = Math.abs(values[2][k]);
			}
			
			if (max_f > max_Hf)
				max = max_f;
			else
				max = max_Hf;
			
			int width = 630;
			setSize(width + 40, width + 40);
			setLocation(left, top);
			
			g.setColor(Color.black);
			g.drawLine(width / 2 + 20, width + 30, width / 2 + 20, 30);
			g.drawLine(20, width / 2 + 30, width + 20, width / 2 + 30);
			g.drawString("0", width / 2 + 10, width / 2 + 45);
			g.drawLine(20, width / 2 + 25, 20, width / 2 + 35);
			g.drawString("-pi", 15, width / 2 + 45);
			g.drawLine(width + 20, width / 2 + 25, width + 20, width / 2 + 35);
			g.drawString("pi", width + 15, width / 2 + 45);
			
			g.setColor(Color.blue);
			for(int k = 0; k < values[0].length; k++)
			{
				g.drawOval((int)(100 * values[0][k]) + 315 + 15, 
						315 + 25 - (int)(315 * values[1][k] / max), 10, 10);
			}
			
			g.setColor(Color.red);
			for(int k = 0; k < values[0].length; k++)
			{
				g.drawOval((int)(100 * values[0][k]) + 315 + 15, 
						315 + 25 - (int)(315 * values[2][k] / max), 10, 10);
			}
		}

		public void windowActivated(WindowEvent e) {}

		public void windowClosed(WindowEvent e) {}

		public void windowClosing(WindowEvent e) 
		{
			setVisible(false);
			dispose();
		}

		public void windowDeactivated(WindowEvent e){}

		public void windowDeiconified(WindowEvent e) {}

		public void windowIconified(WindowEvent e) {}

		public void windowOpened(WindowEvent e) {}
	}
}
