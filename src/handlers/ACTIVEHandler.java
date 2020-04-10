package handlers;

import images.CPIcon;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.io.File;

import listeners.ACTIVEListener;
import mytools.MyTool;
import mytools.MyToolHandler;
import util.PopupBuilder;
import canvasses.ActiveWrapper;
import canvasses.MyCanvasMode;

/**
 * Active canvasses are those which show packings and allow 
 * user interaction --- toolbars, menus, canvas modes, etc. 
 * Currently there are three: the main canvas and the two 
 * canvasses in 'PairedFrame'. Note that the static variable 
 * 'instance' helps distinguish these for hashing 'MyTool's.
 * @author kens
 *
 */
public class ACTIVEHandler extends MyToolHandler { 

	private static int instance=1;
	public ActiveWrapper activeWrapper;

	// Hold variables for 'HANDmode' 
	public int handStartX;
	public int handStartY;
	public int handX;
	public int handY;
	
	// Hold variables for 'DRAGRECTmode'
	public Rectangle dragRect;
	public Point dragCent;
	public boolean dragStarted;
	
	// Hold polygon for 'PATHmode'
	public Path2D.Double polygonalPath;
	public Path2D.Double polyAppendPath;
	
	// Constructor 
	public ACTIVEHandler(File toolFile,ActiveWrapper aWrapper) {
		super(toolFile,new String("MAIN:["+instance+"]"));
		activeWrapper=aWrapper;		
		toolListener=new ACTIVEListener(this);
		if (toolFile!=null) appendFromFile(toolFile);
		instance++;
	}

	public void setCanvasMode(MyCanvasMode mcm) {
		activeWrapper.activeMode=mcm;
		activeWrapper.setCursor(mcm.modeCursor);
		// TODO: change icon in Cursor button??
	}
	
	public MyTool createTool(CPIcon cpIcon,String cmdtext,
			String nametext,String mnem,String tiptext,
			boolean dropit,PopupBuilder popUpMenu) {
		MyTool mytool=super.createTool(cpIcon,cmdtext,nametext,
				mnem, tiptext, dropit,popUpMenu);
		if (mytool!=null && !dropit) {  // not dropable, need target
			mytool.activeWrapper=activeWrapper;
			return mytool;
		}
		
		return null;
	}

}
