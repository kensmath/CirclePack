package util;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.Vector;

import panels.CPScreen;

import complex.Complex;

/**
 * 'RH' refers to 'Riemann-Hilbert'. This class stores "restriction"
 * curves for the 'RiemHilbert' PackExtender class. Initially these
 * curves will be circles, so methods are included specific to that
 * case.
 * @author kens, 9/07
  */
public class RH_curve {
	public boolean isCircle;    // special case: curve is eucl circle
	public Complex center;
	public double rad;
	public Color color;
	
	public Path2D.Double restCurve;      // general case: closed curve
	
	// Constructors
	public RH_curve(Complex z,double rd) {
		isCircle=true;
		center=new Complex(z);
		rad=rd;
		restCurve=null;
		color=CPScreen.getFGColor();
	}
	
	public RH_curve(Vector<Complex> path) {
		isCircle=false;
		center=null;
		rad=0.0;
		Complex z=null;
		if (path.size()<3) restCurve=null;
		else {
			Iterator<Complex> pt=path.iterator();
			restCurve=new Path2D.Double();
			z=(Complex)pt.next();
			restCurve.moveTo(z.x,z.y);
			while (pt.hasNext()) {
				z=(Complex)pt.next();
				restCurve.lineTo(z.x,z.y);
			}
			restCurve.closePath();
		}
		color=CPScreen.getFGColor();
	}
	
	public RH_curve(Path2D.Double gpath) {
		isCircle=false;
		center=null;
		rad=0.0;
		restCurve=new Path2D.Double(gpath);
		color=CPScreen.getFGColor();
	}
	
	/**
	 * Create copy of a restriction curve; color is cloned.
	 */
	public RH_curve clone() {
		RH_curve rhc=null;
		if (isCircle) rhc=new RH_curve(center,rad);
		else rhc=new RH_curve(restCurve);
		rhc.color=new Color(color.getRed(),color.getGreen(),color.getBlue());
		return rhc;
	}
	
	public void drawMe(CPScreen cps) {
		DispFlags dflags=new DispFlags("cfg");
		if (isCircle) 
			cps.drawCircle(center,rad,dflags);
		else 
			cps.drawShape((Shape)restCurve,color,
				cps.imageContextReal.getStroke());
	}
 
}
