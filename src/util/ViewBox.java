package util;

import panels.CPScreen;

import complex.Complex;

/**
 * This holds real world info on what a canvas is viewing, using 
 * lz=(lx,ly), rz=(rx,ry) as lower left and upper right corners.
 * Note: after changes, calling routine responsible: 'update', 'repaint'.
 * TODO: Currently, only squares are supported. 
 * @author kens
 */
public class ViewBox {

	CPScreen parent;    // parent canvas
	public double Aspect; // aspect ratio, length/height
	public Complex lz;    // lower left corner
	public Complex rz;    // upper right corner
	
	// Constructor
	public ViewBox() { // default initiation
		reset();
	}

	public void setParent(CPScreen cps) {
		parent=cps;
	}

	public void reset() {
		Aspect=1.0;
		lz=new Complex(-1.1,-1.1);
		rz=new Complex(1.1,1.1);
	}
	/**
	 * Set the real world view box from lz to rz. 
	 * Note: square views only (for now)
	 * @param lz Complex
	 * @param rz Complex
	 * @return
	 */
	public int setView(Complex llz,Complex rrz) {
		double length=rrz.x-llz.x;
		double height=rrz.y-llz.y;
		if (length<=0 || height<=0) return 0;
		Aspect=1.0; // for now, must be square; use center and larger dimension
		double dim=(length>=height) ? length/2.0 : height/2.0;
		Complex cent=rrz.add(llz).divide(2.0); // center of desired box
		lz.x=cent.x-dim;
		lz.y=cent.y-dim;
		rz.x=cent.x+dim;
		rz.y=cent.y+dim;
		if (parent!=null)
			parent.update(2);
		return 1;
	}
	
	/**
	 * Set view width/height to 'wh', retaining center. 
	 * @param f
	 * @return
	 */
	public int setWidthHeight(double wh) {
		double f=wh/getWidth();
		return scaleView(f);
	}
	
	/**
	 * Scale view by factor f, retaining center. 
	 * @param f
	 * @return
	 */
	public int scaleView(double f) {
		if (f<=0.0) return 0;
		double width=rz.x-lz.x;
		double height=rz.y-lz.y;
		double centx=(rz.x+lz.x)/2.0; // we will keep original center
		double centy=(rz.y+lz.y)/2.0;
		double dim=(width>=height) ? f*width/2.0 : f*height/2.0;
		lz.x=centx-dim;
		lz.y=centy-dim;
		rz.x=centx+dim;
		rz.y=centy+dim;
		parent.update(2);
		return 1;
	}
	
	/**
	 * center screen on z
	 * @param z
	 * @return
	 */
	public int focusView(Complex z) {
		double width=rz.x-lz.x;
		double height=rz.y-lz.y;
		double dim=(width>=height) ? width/2.0 : height/2.0;
		lz.x=z.x-dim;
		lz.y=z.y-dim;
		rz.x=z.x+dim;
		rz.y=z.y+dim;
		parent.update(2);
		return 1;
	}
	
	/**
	 * Translate the view by complex z and 'update' screen
	 * @return
	 */
	public int transView(Complex z) {
		lz=lz.add(z);
		rz=rz.add(z);
		parent.update(1);
		return 1;
	}

	/**
	 * Translate the view by doubles x,y and 'update' screen
	 * @return 
	 */
	public int transView(double x,double y) {
		lz.x+=x;
		lz.y+=y;
		rz.x+=x;
		rz.y+=y;
		parent.update(1);
		return 1;
	}
	
	
	/**
	 * Returns width. (Currently same as height)
	 * @return
	 */
	public double getWidth() {
		return (rz.x-lz.x);
	}

	/**
	 * Returns height. (Currently same as width)
	 * @return
	 */
	public double getHeight() {
		return (rz.y-lz.y);
	}
}
