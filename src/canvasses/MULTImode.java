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
 * This is for using pencil icon to draw paths, but allowing
 * for multiple closed components; see PATHmode for single components.
 * Note: when a path component is closed, the full path is put
 * into GlobalPath and polygonalPath and myPath are set to null.
 * When forming a new component, you can tell if there was a
 * preexisting path by checking polygonalPath!=null.
 */
public class MULTImode extends MyCanvasMode {

	private static final long 
	serialVersionUID = 1L;	
	
	Path2D.Double myPath;
	
	// Constructor
	public MULTImode(String name,String cursorname,Point hotPt,String tool_type) {
		super(name,new CPIcon(cursorname),hotPt,
				null,null,null,"Add curve",
				"Add a curve, start with left-mouse, close with right-mouse",
				tool_type,false);
		updateMenuItem();
	}
	
	// start or add to segment
	public void pressed1(ActiveWrapper aW,MouseEvent e) { // override
		CPdrawing cpS=aW.getCPDrawing();
		Point point=e.getPoint();
		Point2D.Double pt2D=(Point2D.Double)cpS.pt2RealPt(point,
				aW.getWidth(),aW.getHeight());
		// start new addon path or add new point to it
		if (myPath==null) { // start new path
			myPath=new Path2D.Double();
			myPath.moveTo(pt2D.x,pt2D.y);
		}
		else { // add to new component
			myPath.lineTo(pt2D.x,pt2D.y);
			cpS.drawPath(myPath);
			rePaint(aW);
		}
	}
	
	public void pressed3(ActiveWrapper aW,MouseEvent e) {} // override

	public void clicked1(ActiveWrapper aW,MouseEvent e) {} // override

	// close path and display 
	public void clicked3(ActiveWrapper aW,MouseEvent e) { // override
		ACTIVEHandler mH=aW.activeHandler;
		if (myPath!=null) {
			myPath.closePath();
			CPdrawing cpS=aW.getCPDrawing();
			cpS.drawPath(myPath);
			// add on to existing global path
			if (CPBase.ClosedPath!=null) {
				CPBase.ClosedPath.append(myPath.getPathIterator(null),false);
			}
		}
		mH.polygonalPath=null;
		myPath=null;
		rePaint(aW);
		e.consume();
		e=null;
		aW.setDefaultMode();
	}
		
	public void released3(ActiveWrapper aW,MouseEvent e) {} // override
	
	public int dragged(ActiveWrapper aW,Point point) { // override
		CPdrawing cpS=aW.getCPDrawing();
		Point2D.Double pt2D=(Point2D.Double)cpS.pt2RealPt(point,
				aW.getWidth(),aW.getHeight());
		if (myPath!=null) { // add new point (real world data)
			myPath.lineTo(pt2D.x,pt2D.y);
			cpS.drawPath(myPath);
		}
		else {
			myPath=new Path2D.Double();
			myPath.moveTo(pt2D.x,pt2D.y);
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

}
