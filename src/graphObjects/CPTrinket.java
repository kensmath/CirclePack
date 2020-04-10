package graphObjects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Vector;

import panels.CPScreen;

/**
 * Class for "trinket" images (all euclidean) for canvases, colored 
 * shapes like stars, crosses, etc.. To mark locations; size is relative
 * to canvas (not real world) and adjusts with text.  
 */
public class CPTrinket {
	private static Vector<Path2D.Double> trinkets=
		new Vector<Path2D.Double>(10);
	
// ----------- create static shapes, put in vector --------
	// dot 
	private static Path2D.Double dot;
	private static Ellipse2D.Double dotcirc;
	static {
		dotcirc=new Ellipse2D.Double(.5,.5,9.0,9.0);
		dot=new Path2D.Double(dotcirc);
		trinkets.add(dot);
	}
	// square
	private static Path2D.Double box;
	static {
		box=new Path2D.Double();
		box.moveTo(9.0,1.0);
		box.lineTo(1.0,1.0);
		box.lineTo(1.0,9.0);
		box.lineTo(9.0,9.0);
		box.closePath();
		trinkets.add(box);
	}
	// triangle
	private static Path2D.Double triangle;
	static {
		triangle=new Path2D.Double();
		triangle.moveTo(5.5,.5);
		triangle.lineTo(4.5,.5);
		triangle.lineTo(.5,9.5);
		triangle.lineTo(9.5,9.5);
		triangle.closePath();
		trinkets.add(triangle);
	}
	//plus
	private static Path2D.Double plus;
	static {
		plus=new Path2D.Double();
		plus.moveTo(9.5,3.5);
		plus.lineTo(6.5,3.5);
		plus.lineTo(6.5,.5);
		plus.lineTo(3.5,.5);
		plus.lineTo(3.5,3.5);
		plus.lineTo(.5,3.5);
		plus.lineTo(.5,6.5);
		plus.lineTo(3.5,6.5);
		plus.lineTo(3.5,9.5);
		plus.lineTo(6.5,9.5);
		plus.lineTo(6.5,6.5);
		plus.lineTo(9.5,6.5);
		plus.closePath();
		trinkets.add(plus);
	}
	// diamond
	private static Path2D.Double diamond;
	static {
		diamond=new Path2D.Double();
		diamond.moveTo(5,1);
		diamond.lineTo(1,5);
		diamond.lineTo(5,9);
		diamond.lineTo(9,5);
		diamond.closePath();
		trinkets.add(diamond);
	}
	// x shape
	private static Path2D.Double xshape;
	static {
		xshape=new Path2D.Double();
		xshape.moveTo(9.0,2);
		xshape.lineTo(8,1.0);
		xshape.lineTo(5.0,4);
		xshape.lineTo(2,1.0);
		xshape.lineTo(1.0,2);
		xshape.lineTo(4,5.0);
		xshape.lineTo(1.0,8);
		xshape.lineTo(2,9.0);
		xshape.lineTo(5.0,6);
		xshape.lineTo(8,9.0);
		xshape.lineTo(9.0,8);
		xshape.lineTo(6,5.0);
		xshape.closePath();
		trinkets.add(xshape);
	}
	// TODO: star
//	private static Path2D.Double star;
//	static {
//		star=new Path2D.Double();
//		star.moveTo(4.5,0.5);
//		double inc=Math.PI/5;
//		for (int i=1;i<10;i=i+2) {
//			double ninc=i*inc;
//			star.lineTo(4.5-4.5*Math.sin(ninc),4.5+4.5*Math.cos(ninc));
//			ninc=(i+1)*inc;
//			star.lineTo(4.5-3.0*Math.sin(ninc),4.5+3.0*Math.cos(ninc));
//		}
//		star.closePath();
//		trinkets.add(star);
//	}
	
//	private static Path2D.Double plus;
//	private static Path2D.Double xmark;
//	private static Path2D.Double tri;
//	private static Path2D.Double diamond;

	// instance variables
	private CPScreen parent;

	private int geometry; 

	// x,y are real world coordinates. Parent sets colors in imageContextReal, 
	// adjusts x,y, calls 'drawIt()' for draw or fill, etc.
	public double x;
	public double y;

	// Constructor
	public CPTrinket() {
		x=0.0;
		y=0.0;
		geometry=0;
	}
	
	public void setParent(CPScreen par) {
		parent=par;
	}
	
	// start over: e.g., when changing geometries
	public void resetGeom(int geom) {
		geometry=geom;
		x=y=0.0;
	}

	public int getGeometry() {
		return geometry;
	}

	/** 
	 * In drawing, calling program sets x, y point in correct geometry.
	 * @param trink, index of desired trinket among those available
	 * @param Color
	 * @param scale, factor (if >1) by which to scale default size (10 pixel)
	 */
	public void drawIt(boolean fill,int trink,Color colr,int scale) {
		int side=10; // default pixel size of trinket image rectangle
		double factor=1.0;
		if (scale>1) factor=(double)scale;
		side = (int)(side*factor);
		double pix_x,pix_y;
		BufferedImage bufImage=new BufferedImage(side+1,side+1,BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d=bufImage.createGraphics();
		if (colr==null)
			colr=CPScreen.getFGColor();
		g2d.setColor(colr);
		if (trink<0) trink=1;
		else if (trink>=trinkets.size()) 
			trink=trinkets.size()-1;
		Path2D.Double gpath=new Path2D.Double(trinkets.get(trink));
		gpath.closePath();
		gpath.transform(new AffineTransform(factor,0,0,factor,0,0));
//		g2d.fill(gpath);
		Color old_color=g2d.getColor();
		if (colr==null) 
			g2d.setColor(Color.BLACK);
		else 
			g2d.setColor(colr);
		if (fill) {
			g2d.fill(gpath);
		}
		g2d.draw(gpath);
		g2d.setColor(old_color);
		if (geometry>0) { // spherical, convert (theta,phi) to (y,z) of screen
			pix_x=parent.toPixX(Math.sin(y)*Math.sin(x));
			pix_y=parent.toPixY(Math.cos(y));			
		}
		else {
			pix_x=parent.toPixX(x);
			pix_y=parent.toPixY(y);
		}
		// put in image buffer and check if it intersects the view
		Graphics2D g2=parent.imageContextReal;
		Rectangle imgrect=new Rectangle((int)(pix_x-side/2),(int)(pix_y-side/2),side+1,side+1);
		if (imgrect.intersects(g2.getClipBounds())) // view check
			g2.drawImage(bufImage,new AffineTransform(1,0,0,1,pix_x-side/2,pix_y-side/2),null);
	}
	
}
