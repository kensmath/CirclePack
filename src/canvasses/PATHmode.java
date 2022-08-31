package canvasses;

import handlers.ACTIVEHandler;
import images.CPIcon;
import packing.CPdrawing;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import allMains.CPBase;

/**
 * ===== active canvas cursor mode ====
 * This is for using pencil icon to draw a closed path
 */
public class PATHmode extends MyCanvasMode {

	private static final long 
	serialVersionUID = 1L;	
	
	// Constructor
	public PATHmode(String name,String cursorname,Point hotPt,String tool_type) {
		super(name,new CPIcon(cursorname),hotPt,
				null,null,null,"Draw curve",
				"Draw curve with left-mouse, close with right-mouse",
				tool_type,false);
		updateMenuItem();
	}
	
	// start or add to segment
	public void pressed1(ActiveWrapper aW,MouseEvent e) { // override
		CPdrawing cpS=aW.getCPDrawing();
		ACTIVEHandler mH=aW.activeHandler;
		Point point=e.getPoint();
		Point2D.Double pt2D=(Point2D.Double)cpS.pt2RealPt(point,
				aW.getWidth(),aW.getHeight());
		// start new path
		if (mH.polygonalPath==null) {
			mH.polyAppendPath=null;
			mH.polygonalPath=new Path2D.Double();
			mH.polygonalPath.moveTo(pt2D.x,pt2D.y);
		}
		else if (mH.polygonalPath!=null) { // add new point (real world data)
			mH.polygonalPath.lineTo(pt2D.x,pt2D.y);
			cpS.drawPath(mH.polygonalPath);
		}
	}
	
	public void pressed3(ActiveWrapper aW,MouseEvent e) {} // override

	public void clicked1(ActiveWrapper aW,MouseEvent e) {} // override

	// close path and display 
	public void clicked3(ActiveWrapper aW,MouseEvent e) { // override
		ACTIVEHandler mH=aW.activeHandler;
		if (mH.polygonalPath!=null) {
			mH.polygonalPath.closePath();
			CPdrawing cpS=aW.getCPDrawing();
			cpS.drawPath(mH.polygonalPath);
			storeGlobalPath(aW);
			mH.polygonalPath=null;
			mH.polyAppendPath=null;
		}
		mH.polygonalPath=null;
		mH.polyAppendPath=null;
		rePaint(aW);
		e.consume();
		e=null;
		aW.setDefaultMode();
	}
	
	
	public void released3(ActiveWrapper aW,MouseEvent e) {} // override
	
	public int dragged(ActiveWrapper aW,Point point) { // override
		CPdrawing cpS=aW.getCPDrawing();
		ACTIVEHandler mH=aW.activeHandler;
		Point2D.Double pt2D=(Point2D.Double)cpS.pt2RealPt(point,
				aW.getWidth(),aW.getHeight());
		if (mH.polygonalPath!=null) { // add new point (real world data)
			mH.polygonalPath.lineTo(pt2D.x,pt2D.y);
			cpS.drawPath(mH.polygonalPath);
		}
		else {
			mH.polygonalPath=new Path2D.Double();
			mH.polygonalPath.moveTo(pt2D.x,pt2D.y);
		}
		rePaint(aW); // call for repaint
		return 1;
	}
	
	/**
	 * Store clone in the global Path2D.Double 'ClosedPath'. 
	 */
	public int storeGlobalPath(ActiveWrapper aW) {
		if (aW.activeHandler.polygonalPath!=null) 
			CPBase.ClosedPath=(Path2D.Double)aW.activeHandler.
			polygonalPath.clone();
		else return 0;
		return 1;
	}

	// TODO: Eventually want to allow for appending other subpaths.
}
