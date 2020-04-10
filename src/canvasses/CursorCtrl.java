package canvasses;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;

import exceptions.MiscException;
import images.CPIcon;
import listeners.ACTIVEListener;
import util.ModeMenuItem;

/**
 * The class maintains the vectors of cursors and active modes
 * from which any of the 'ActiveWrapper's can chose. On startup, 
 * the default mode, hand mode, viewbox mode, and a few others
 * are started. Additional modes can be defined in myTools and
 * scripts. 
 * @author kens
 *
 */
public class CursorCtrl {

	public static int N=0; // index
	public static Vector<MyCanvasMode> canvasModes = 
		new Vector<MyCanvasMode>(10);
	public static Vector<MyCanvasMode> userModes = 
		new Vector<MyCanvasMode>(10);
	public static Vector<MyCanvasMode> scriptModes = 
		new Vector<MyCanvasMode>(10);
	
	public static MyCanvasMode defaultMode; 
	public static Cursor defaultCursor = 
		Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
	
	// Constructors
	public CursorCtrl() {
		this(null);
	}
	
	/**
	 * Read the cursor modes from a file
	 * @param modeFile (not yet used)
	 */
	public CursorCtrl(File modeFile) {

		// Canvas ('MAIN') modes:
		defaultMode=new MyCanvasMode(Integer.toString(N++),
				new CPIcon("main/defaultCursor.png"),new Point(0,0),
				null,null,null,"default",
				"Default mode: circle/face indices (l/r) and drag for view",
				"MAIN:",true);
		defaultMode.updateMenuItem();
				
		// reset cursor to actual Java version (rather than my copy)
		defaultMode.modeCursor=Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
		
		new PATHmode(Integer.toString(N++),"main/pencil.png",
				new Point(2,18),"MAIN:");
		new MULTImode(Integer.toString(N++),"main/pencil_plus.png",
				new Point(2,18),"MAIN:");

		// TODO: have to get the mode working again: one issue is MacOS,
		//       another is how to prevent mouse presses from passing through
		//       to the canvas
//		new DRAGRECTmode(Integer.toString(N++),"main/viewmag.png",
//				new Point(10,10),"MAIN:");
	}
	
	public static JPopupMenu cursorMenu(ACTIVEListener mL) {
		JPopupMenu theMenu=new JPopupMenu();
		
		for (int j=0;j<canvasModes.size();j++) {
			ModeMenuItem mItem=canvasModes.get(j).menuItem;
			mItem.addActionListener(mL);
			theMenu.add(mItem);
		}
		theMenu.addSeparator();
		for (int j=0;j<userModes.size();j++) {
			ModeMenuItem mItem=userModes.get(j).menuItem;
			mItem.addActionListener(mL);
			theMenu.add(mItem);
		}
		theMenu.addSeparator();
		for (int j=0;j<scriptModes.size();j++) {
			ModeMenuItem mItem=scriptModes.get(j).menuItem;
			mItem.addActionListener(mL);
			theMenu.add(mItem);
		}
		
		theMenu.pack();
		return theMenu;
	}
	
	/**
	 * Not all systems support cursors of arbitrary size. The
	 * desired image may have to be scaled to fit in the allowed
	 * footprint or, when the system's cursor size is too large
	 * (e.g. Windows 32x32), make a transparent area and position
	 * the actual image in upper left corner. Also have to adjust
	 * hotspot.
	 * @param cursorImageFile, String
	 * @param hotPt, Point
	 * @return Cursor
	 */
	public static Cursor createScaledCursor(ImageIcon imageIcon, Point hotPt) {
		Toolkit toolkit;
//		MediaTracker mediaTracker;
		BufferedImage scaledCursorImage;
		Graphics2D g2;
		Point cursorHotSpot;
		int origWidth;
		int origHeight;
		Image origImage=imageIcon.getImage();
		origWidth = origImage.getWidth(null);
		origHeight = origImage.getHeight(null);

		toolkit = Toolkit.getDefaultToolkit();


/* see if we can do without this

		//load a MediaTracker and add the cursor image, then
		//wait for it to load. getImage(String imagePath) may
		//return before loading the image finishes, and often
		//causes strange behavior if operations are attempted on
		//the image before it successfully loads.
		mediaTracker = new MediaTracker(CPBase.frame);
		mediaTracker.addImage(origImage, 0);
		try {
			mediaTracker.waitForAll();
		} catch (InterruptedException ie) {
			ie.printStackTrace();
		}
*/ 

		// get closest allowed dimension (e.g. Windows is 32x32 only
		Dimension dim=toolkit.getBestCursorSize(origWidth,origHeight); // (22,22);
		
		// if system does not allow custom cursors, default to crosshair
		if (dim.width==0) { 
			return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
		}
		
		// create pixel transparent image dimension dim
		scaledCursorImage = new BufferedImage(dim.width,dim.height, 
				BufferedImage.TYPE_INT_ARGB);
		g2 = (Graphics2D) scaledCursorImage.getGraphics();
		g2.setColor(new Color(0, 0, 0, 0)); // transparent
		g2.drawRect(0, 0, dim.width,dim.height);
		
		// if cursor image fit in area (I assume area and cursor are square)
		if (origWidth <= dim.width && origHeight <= dim.height) {
			// draw the cursor over the image starting in the top left corner
			g2.drawImage(origImage, 0, 0, origWidth, origHeight, null);
			cursorHotSpot = new Point(hotPt.x,hotPt.y);
		}
		
		// else rescale width and/or height
		else {
			double scaleX=1.0;
			double scaleY=1.0;
		
			if (origWidth > dim.width) 
				scaleX = (double)dim.width / (double) origWidth;
			if (origHeight> dim.height)
				scaleY = (double)dim.height/ (double)origHeight;
			// draw the scaled-down image over the dim transparent
			//image and recalculate the hot spot.
			g2.drawImage(origImage,0,0,
					(int)(scaleX * (double) origWidth),
					(int)(scaleY * (double) origHeight), null);
			cursorHotSpot = new Point((int)(scaleX * (double)hotPt.x),
					(int)(scaleY * (double) hotPt.y));
		}
		g2.dispose();
		
		try {
			return toolkit.createCustomCursor(scaledCursorImage,cursorHotSpot,null); // cursorImageFile);
		} catch (Exception ex) {
			throw new MiscException("problem creating custom cursor"+ex.getMessage());
		}
	}
	
}
