package canvasses;

import handlers.ACTIVEHandler;
import images.CPIcon;
import input.TrafficCenter;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import math.Matrix3D;
import math.Point3D;
import mytools.MyTool;
import packing.CPdrawing;
import util.ModeMenuItem;
import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;

/**
 * MyCanvasMode is used to associate modes/cursors/icons to
 * 'ActiveWrapper's. It holds the icon, 'modeType' of creation,
 * (e.g., from 'MyTool'), and catches mouse events.
 * Canvas modes are kept in vectors in 'ActiveWrapper' and 
 * persist. Mouse events are caught in 'ActiveWrapper', but 
 * processed in 'ActiveMode', including decisions about repaints.
 * 
 * NOTE: if created in script or myTool bar, there may be an
 * associated 'MyTool' which appears on tool bars.
 * @author kens
 *
 */
public class MyCanvasMode extends MyTool {
	
	private static final long 
	serialVersionUID = 1L;

	public Cursor modeCursor; // canvas active cursor
	public CPIcon cursorIcon;
	public boolean handy; // if true, mouse drag translations is on

	// there may be a command associated with each mouse button
	public String cmd2,cmd3; //
	public String shortTip; // short version of tip for popup menu
	public Point hotPoint;  // cursor hot point: null unless explicitly 
		// set for a special cursor
	
	public ModeMenuItem menuItem; // to appear in cursor choice menus
	
	// Constructor
	public MyCanvasMode() {
		super();
		cursorIcon=null;
		modeCursor=null;
		handy=true;
		cmd2=null;
		cmd3=null;
		shortTip=null;
		hotPoint=null;
	}
	                       
	public MyCanvasMode(String modename,CPIcon cpIc,Point hotpt,
			String cmdstr,String cd2,String cd3,
			String shorttip,String tip,String tool_type,boolean hndy) {
		super(cpIc,cmdstr,null,modename,tip,tool_type,true,null,null);
		
		if (modename==null || modename.trim().length()==0)
			nameString=Integer.toString(CursorCtrl.N++);
		else nameString=new String(modename);

		// get cursor icon; default is to copy that of cpIcon
		setCursor(hotpt);

		// Are there commands?  
		setCmd(cmdstr);
		setCmd2(cd2);
		setCmd3(cd3);
		setShortTip(shorttip);
		setToolTip(tip);		
		menuItem=null;
		handy=hndy;
	}

	/**
	 * update 'menuItem' for this mode, replace it in the
	 * appropriate modes vector for the mode popup menu.
	 */
	public void updateMenuItem() {
		CursorCtrl.canvasModes.remove(this);
		CursorCtrl.scriptModes.remove(this);
		CursorCtrl.userModes.remove(this);
		menuItem=new ModeMenuItem(this,shortTip,cursorIcon);
		if (toolType.startsWith("MAIN"))
			CursorCtrl.canvasModes.add(this);
		else if (toolType.startsWith("SCRIPT"))
			CursorCtrl.scriptModes.add(this);
		else 
			CursorCtrl.userModes.add(this);
	}		
		
	public void moreReset() {} // can be overriden, e.g., 'PATHmode'

	
	/**
	 * Create the cursor Icon and set its "hot" point (the relative x,y
	 * location where a mouse click applies).
	 * @param tmpHotPt
	 */
	public void setCursor(Point tmpHotPt) {
		cursorIcon=new CPIcon(cpIcon.getIconName());
		// make sure hotpoint is set
		if (tmpHotPt==null) {
			tmpHotPt = new Point(4,
					cursorIcon.getImageIcon().getImage().getHeight(null)-4);
		}
		modeCursor=CursorCtrl.createScaledCursor(cpIcon.getBaseIcon(),tmpHotPt);
	}

	public void setCmd(String cmdstr) {
		if (cmdstr!=null) {
		try {
			cmdstr=cmdstr.trim();
			if (cmdstr.length()==0)
				cmdstr=null;
			this.setCommand(cmdstr);
		} catch (Exception ex) {
			this.setCommand(null);
		}
		}
	}
	
	public void setCmd2(String cd2) {
		if (cd2!=null) {
		try {
			cmd2=new String(cd2.trim());
			if (cmd2.length()==0)
				cmd2=null;
		} catch (Exception ex) {
			cmd2=null;
		}
		}
	}
	
	public void setCmd3(String cd3) {
		if (cd3!=null) {
		try {
			cmd3=new String(cd3.trim());
			if (cmd3.length()==0)
				cmd3=null;
		} catch (Exception ex) {
			cmd3=null;
		}
		}
	}
	
	/**
	 * ShortTip is what occurs in active mode popup menu.
	 * When created in script, generally use 'nameString'.
	 * @param shorttip
	 */
	public void setShortTip(String shorttip) {
		if (shorttip==null) shortTip="";
		else if (shorttip.length()>20) 
			shortTip=shorttip.substring(0,19);
		else shortTip=new String(shorttip);
	}
	
	public CPIcon getCursorIcon() {
		return cursorIcon;
	}
	
	/**
	 * This calls for execution of 'cmd' string associated with
	 * this canvas mode.
	 * @param cmd, command string
	 * @param aW, ActiveWrapper
	 * @param point, Point if there is mouse location, or null
	 * @param rep, boolean, true, then repaint (on success)
	 * @return 1, calling routine should repaint; 0, neutral; -1 error
	 */
	public int execute(String cmd,ActiveWrapper aW,Point point,boolean rep) {
//		String []rst; // what was this to be for?
		int ans=0;
		if (cmd==null || cmd.length()==0)
			return 0;
		if (cmd.contains("#XY") || cmd.contains("#xy")) {
			if (point==null) {
				throw new ParserException("No 'point' for mode execution");
			}
			CPdrawing cpS=aW.getCPDrawing();
			Dimension dim=aW.getSize();
			Point2D.Double pt2D=cpS.pt2RealPt(point,dim.width,dim.height);
			cmd=cmd.replace("#XY"," "+pt2D.x+" "+pt2D.y+" ");
			cmd=cmd.replace("#xy"," "+pt2D.x+" "+pt2D.y+" ");
		}
		try {
			ans=CPBase.trafficCenter.parseWrapper(cmd,aW.cpDrawing.getPackData(),false,false,0,null);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("myCursor execution failed");
			return -1;
		}
		rePaint(aW);
		return ans;
	}
	
	// rotate sphere: 'ACTIVEHandler' holds data; this rotates so 
	//    handStartX/Y becomes handX/Y
	public static void rotate(ActiveWrapper aW) {
		try {
			CPdrawing cpS=aW.getCPDrawing();
			ACTIVEHandler mH=aW.activeHandler;
			Dimension dim=aW.getSize();
			Point2D.Double pt1=cpS.pt2RealPt(new Point(mH.handStartX,mH.handStartY), dim.width,dim.height);
			Point2D.Double pt2=cpS.pt2RealPt(new Point(mH.handX,mH.handY), dim.width,dim.height);
			if (pt1.x * pt1.x + pt1.y * pt1.y > 1 || pt2.x * pt2.x + pt2.y * pt2.y > 1)
				return;
			double x1 = Math.sqrt(1 - pt1.x * pt1.x - pt1.y * pt1.y);
			double x2 = Math.sqrt(1 - pt2.x * pt2.x - pt2.y * pt2.y);
			// double theta1=Math.atan2(y1,x1);
			// double theta2=Math.atan2(y2,x2);
			// rotate by
			Point3D pnt1 = new Point3D(x1, pt1.x, pt1.y);
			Point3D pnt2 = new Point3D(x2, pt2.x, pt2.y);
			Point3D n = math.Point3D.CrossProduct(pnt1, pnt2);
			n = n.divide(n.norm());
			double theta = n.getTheta();
			double phi = n.getPhi();
			double alpha = Math.acos(Point3D.DotProduct(pnt1, pnt2));
			Matrix3D matrixTheta = Matrix3D.FromEulerAnglesXYZ(0, 0, theta);
			Matrix3D matrixPhi = Matrix3D.FromEulerAnglesXYZ(0, phi, 0);
			Matrix3D part2 = Matrix3D.times(matrixTheta, matrixPhi);
			Matrix3D part1 = Matrix3D.Inverse(part2);
			Matrix3D rot = Matrix3D.times(part2, Matrix3D.times(Matrix3D
				.FromEulerAnglesXYZ(0, 0, alpha), part1));
			Matrix3D matrix=Matrix3D.times(rot,cpS.sphView.viewMatrix);
			if (!Matrix3D.isNaN(matrix)) {
				cpS.sphView.viewMatrix=new Matrix3D(matrix);
				TrafficCenter.cmdGUI(cpS.getPackData(),"disp -wr");
			}
		} catch (Exception ex) {return;}
	}

	/**
	 * Execute and (on success) repaint
	 * @param cmd, command string
	 * @param aW
	 * @param point
	 * @return
	 */
	public int execute(String cmd,ActiveWrapper aW,Point point) {
		return execute(cmd,aW,point,true);
	}

	/**
	 * Default mouse1 action: execute 'cpCommand'. 
	 * If this is null, display circle number(s). 
	 */
	public void clicked1(ActiveWrapper aW,MouseEvent e) {
		String cmdstr=this.getCommand();
		Point point=e.getPoint();
		if (cmdstr!=null && cmdstr.length()>0) {
			execute(cmdstr,aW,point);
			return;
		}
		Dimension dim=aW.getSize();
		Point2D.Double pt=aW.cpDrawing.pt2RealPt(point, dim.width,dim.height);
		int ans=0;
		try {
			ans=TrafficCenter.cmdGUI(aW.cpDrawing.getPackData(),"locate -c " + pt.x + " " + pt.y);
		} catch (Exception ex) {return;}
		if (ans>0) 
			rePaint(aW);
	}

	/**
	 * Default mouse2 action is to display face indices.
	 * If 'cmd2' is non-empty, then execute that.
	 */
	public void clicked2(ActiveWrapper aW,MouseEvent e) {
		Point point=e.getPoint();
		if (cmd2!=null) {
			execute(cmd2,aW,point);
			return;
		}
		Dimension dim=aW.getSize();
		Point2D.Double pt=aW.cpDrawing.pt2RealPt(point, dim.width,dim.height);
		int ans=0;
		try {
			ans=TrafficCenter.cmdGUI(
					aW.cpDrawing.getPackData(),"locate -f " + pt.x + " " + pt.y);
		} catch (Exception ex) {return;}
		if (ans>0) 
			rePaint(aW);
	}

	/**
	 * Default mouse3 action: main.myt and canvas.myt tool
	 * files may specify a popup menu for button 3;else 
	 * generally nothing, but if 'cmd3' is non-empty, execute it
	 */
	public void clicked3(ActiveWrapper aW,MouseEvent e) {
		Point point=e.getPoint();
		if (cmd3!=null) {
			execute(cmd3,aW,point);
			return;
		}
		if (aW.button3Popup!=null) {
			aW.button3Popup.show(aW,point.x,point.y);
		}
	}
	
	public void pressed1(ActiveWrapper aW,MouseEvent e) {
		if (handy) {
			Point point=e.getPoint();
			ACTIVEHandler mH=aW.activeHandler;
			mH.handStartX=point.x;
			mH.handStartY=point.y;
		}
	}
	
	public void pressed2(ActiveWrapper aW,MouseEvent e) {}

	public void pressed3(ActiveWrapper aW,MouseEvent e) {}

	public int dragged(ActiveWrapper aW,MouseEvent e) {
		if (!handy) return 0;
		CPdrawing cpS=aW.getCPDrawing();
		ACTIVEHandler mH=aW.activeHandler;
		Point point=e.getPoint();
		if (cpS.getGeom()>0) { // sphere
			mH.handX=point.x;
			mH.handY=point.y;
			rotate(aW);
		}
		else { // hyp and eucl, convert to real world translation.
			cpS.realBox.transView((
					mH.handStartX-point.x)*cpS.XWidth/aW.getWidth(),
					(point.y-mH.handStartY)*cpS.YHeight/aW.getHeight());
	  		try {
	  			TrafficCenter.cmdGUI(cpS.getPackData(),"disp -wr"); // repaint only active screen
	  		} catch (Exception ex) {}
		}
		mH.handStartX = point.x;
		mH.handStartY= point.y;
		return 0;
	}

	public void release1(ActiveWrapper aW,MouseEvent e) {}

	public void release2(ActiveWrapper aW,MouseEvent e) {}

	public void release3(ActiveWrapper aW,MouseEvent e) {}

	/**
	 * Call for repainting all the canvasses for this packing.
	 * @param aW
	 */
	public void rePaint(ActiveWrapper aW) {
		PackControl.canvasRedrawer.paintMyCanvasses(aW.cpDrawing,false);
	}

}
