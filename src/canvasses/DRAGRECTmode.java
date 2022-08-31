package canvasses;

import handlers.ACTIVEHandler;
import images.CPIcon;
import input.TrafficCenter;
import packing.CPdrawing;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import complex.Complex;

/**
 * ===== active canvas cursor mode ====
 * This is drags a rectangle to zoom in on a local area.
 */
public class DRAGRECTmode extends MyCanvasMode {

	private static final long 
	serialVersionUID = 1L;
	
	// Constructor
	public DRAGRECTmode(String name,String cursorname,Point hotPt,
			String tool_type) {
		super(name,new CPIcon(cursorname),hotPt,
				null,null,null,"Drag for view",
				"Click left and drag to zoom in on square area",
				tool_type,false);
		updateMenuItem();
	}
	
	public void release3(ActiveWrapper aW,MouseEvent e) { 
		CPdrawing cpS=aW.getCPDrawing();
		ACTIVEHandler mH=aW.activeHandler;
		if (mH.dragRect.width > 0) {
			Dimension dim=aW.getSize();
			Point2D.Double ll=cpS.pt2RealPt(new Point(mH.dragRect.x,mH.dragRect.y+mH.dragRect.height),
					dim.width,dim.height);
			Point2D.Double ur=cpS.pt2RealPt(new Point(mH.dragRect.x+mH.dragRect.width,mH.dragRect.y),
					dim.width,dim.height);
			try {
				cpS.realBox.setView(new Complex(ll.x,ll.y),new Complex(ur.x,ur.y));
				TrafficCenter.cmdGUI(cpS.getPackData(),"disp -wr"); 
			} catch (Exception ex) {return;}
		}
		moreReset();
		aW.setDefaultMode(); // deactivate when done
	}
	
	public void release1(ActiveWrapper aW,MouseEvent e) {
		rePaint(aW);
	}
	
	public void pressed3(ActiveWrapper aW,MouseEvent e) {}
	
	public void pressed1(ActiveWrapper aW,MouseEvent e) {
		ACTIVEHandler mH=aW.activeHandler;
		Point point=e.getPoint();
		mH.dragCent=new Point(point);
		mH.dragRect=new Rectangle();
		mH.dragStarted=true;
	}
	
	public int dragged(ActiveWrapper aW,MouseEvent e) {
		ACTIVEHandler mH=aW.activeHandler;
		if(!mH.dragStarted) return 0;
		Point point=e.getPoint();
		int dx = Math.abs(point.x - mH.dragCent.x);
		int dy = Math.abs(point.y - mH.dragCent.y);
		int mx=Math.max(dx,dy);
		mH.dragRect.x=mH.dragCent.x-mx;
		mH.dragRect.y=mH.dragCent.y-mx;
		mH.dragRect.width = mH.dragRect.height= 2*mx;
		return 1;
	}
	
	/**
	 * If a rectangular viewport has been initiated, this 
	 * returns the rectangle.
	 * @return
	 */
	public Rectangle getDragRect(ActiveWrapper aW) {
		ACTIVEHandler mH=aW.activeHandler;
		if (mH.dragRect.width>0) return mH.dragRect;
		else return null;
	}
	
}
