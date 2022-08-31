package canvasses;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.io.File;

import javax.swing.JPanel;

import circlePack.PackControl;
import complex.Complex;
import handlers.ACTIVEHandler;
import input.TrafficCenter;
import packing.CPdrawing;
import script.ScriptManager;
import util.PopupBuilder;
import util.SphView;

/**
 * Simple wrapper for canvases displaying circle packings; catch key,
 * mouse, and mouse motion events. The 'ActiveWrapper' is used in the 
 * 'MainPanel'; derived class, 'PairWrapper' (which override most
 * mouse events) is used for side-by-side window mode.
 * @author kens
 *
 */
//AF>>>//
// Implement MouseWheelListener for mouse wheel zooming.
public class ActiveWrapper extends JPanel implements KeyListener, 
	MouseListener, MouseMotionListener, MouseWheelListener {
//<<<AF//

	private static final long serialVersionUID = 1L;
	//AF>>>//
	// Define how much to zoom in or out on mouse wheel movement.
	private static double mouseWheelZoomOutMultiplier = 1.05D; // mouseWheelZoomOutMultiplier > 1.0D
	private static double mouseWheelZoomInMultiplier = 1.0D/mouseWheelZoomOutMultiplier; // 1.0D > mouseWheelZoomInMultiplier > 0.0D
	//<<<AF//
	
	protected CPdrawing cpDrawing;
	ACTIVEHandler activeHandler;
	
	// 'MyCanvasMode's effect cursor and mouse operations.
	public static MyCanvasMode defaultMode;
	public MyCanvasMode activeMode;
	
	public PopupBuilder button3Popup;

	// Constructor
	public ActiveWrapper(File mainMytFile,CPdrawing cpd) {
		super();
		cpDrawing=cpd;
		setFocusable(true);
		addMouseListener(this);
		addMouseMotionListener(this);
		//AF>>>//
		addMouseWheelListener(this);
		//<<<AF//
		addKeyListener(this);
		
		// set up a tool handler 
		button3Popup=null;
		activeHandler = new ACTIVEHandler(mainMytFile,this);
		defaultMode = CursorCtrl.defaultMode;
		activeMode=defaultMode;
	}
	
	public void setCPDrawing(CPdrawing cpd) {
		cpDrawing=cpd;
	}
	
	public CPdrawing getCPDrawing() {
		return cpDrawing;
	}
	
	public ACTIVEHandler getToolHandler() {
		return activeHandler;
	}
	
	//AF>>>//
	// Changed zoomOut and zoomIn to allow zooming by specified value.
	// Calling the old versions (no arguments) will zoom by the old
	// default amounts.
	public void zoomOut() {
		zoomOut(2.0D);
	}

	public void zoomIn() {
		zoomIn(0.5D);
	}
	
	public void zoomOut(double zoomOutMultiplier) {
		try {
			cpDrawing.realBox.scaleView(zoomOutMultiplier);
			cpDrawing.update(2);
			TrafficCenter.cmdGUI(cpDrawing.getPackData(),"disp -wr");
		} catch (Exception ex) {return;}
		repaint();
	}

	public void zoomIn(double zoomInMultiplier) {
		try {
			cpDrawing.realBox.scaleView(zoomInMultiplier);
			cpDrawing.update(2);
			TrafficCenter.cmdGUI(cpDrawing.getPackData(),"disp -wr");
		} catch (Exception ex) {return;}
		repaint();
	}
	//<<<AF//
	
	public void setDefaultMode() {
		activeHandler.setCanvasMode(CursorCtrl.defaultMode);
	}
	
	// must have these keyListener methods
	public void keyReleased(KeyEvent e) {} 
	    // cuation: keyPressed desirable, since extraneous keyReleases might be caught. 
	public void keyPressed(KeyEvent e) {
		if (!(e.getComponent() instanceof ActiveWrapper)) // for correct window?
			return;
		char c = e.getKeyChar();
		ScriptManager mgr = PackControl.scriptManager;
		if (mgr.isScriptLoaded()) {
			if (c==KeyEvent.VK_ENTER) mgr.executeNextCmd(); // c == e.VK_ENTER, execute next
			else {
				String key=String.valueOf(c);
				mgr.executeCmdByKey(e,key); // use first character only
			}
		}
	}
	public void keyTyped(KeyEvent e) {}
	
	// Methods required for MouseListener/MouseMotionListener
	public void mouseClicked(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON2 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
						ActionEvent.CTRL_MASK)) {
			activeMode.clicked2(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON3 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.SHIFT_MASK)==
						ActionEvent.SHIFT_MASK)) {
			activeMode.clicked3(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON1) 
			activeMode.clicked1(this,e);
		e.consume();
	}
	
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON2 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
						ActionEvent.CTRL_MASK)) {
			activeMode.pressed2(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON3 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.SHIFT_MASK)==
						ActionEvent.SHIFT_MASK)) {
			activeMode.pressed3(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON1) 
			activeMode.pressed1(this,e);
		e.consume();
	}
	
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON2 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
						ActionEvent.CTRL_MASK)) {
			activeMode.release2(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON3 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.SHIFT_MASK)==
						ActionEvent.SHIFT_MASK)) {
			activeMode.release3(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON1) 
			activeMode.release1(this,e);
		e.consume();	
	}

	// MouseMotionListener 
	public void mouseDragged(MouseEvent e) {
		activeMode.dragged(this,e);
	}
	public void mouseEntered(MouseEvent e) {
		requestFocus(); // send focus to activeScreen so it gets key events
	}
	public void mouseExited(MouseEvent e) {
		PackControl.mbarPanel.requestFocusInWindow(); // move focus to innocuous place
	}

	// update cursor coordinate indicator in 'MainFrame'
	public void mouseMoved(MouseEvent e) {
		Point2D.Double pt2D=cpDrawing.pt2RealPt(e.getPoint(),getWidth(),getHeight());
		Complex z=new Complex(pt2D.x,pt2D.y);
		if (cpDrawing.getGeom()>0) { // sphere
			if (z.abs()>1.0) return;
  		  	z=cpDrawing.sphView.toRealSph(SphView.visual_plane_to_s_pt(z));
		}
		PackControl.activeFrame.updateLocPanel(cpDrawing.getGeom(),z);
	}
	
	//AF>>>//
	// This implements cursor position dependent mouse wheel zooming.
	// If the user rotates the mouse wheel away from them, the canvas
	// will zoom in towards the cursor. If the user rotates the mouse wheel
	// towards them, the canvas will zoom out away from the cursor.
	public void mouseWheelMoved(MouseWheelEvent mwe) {
		int mouseWheelClicks = mwe.getWheelRotation(); // Wheel rotation amount in clicks. Sign determines direction.
		int width = mwe.getComponent().getWidth(); // Width of the firing component.
		int height = mwe.getComponent().getHeight(); // Height of the firing component.
		int x = (int) mwe.getPoint().getX(); // Current X position of mouse.
		int y = (int) mwe.getPoint().getY(); // Current Y position of mouse.

		// Get the mouse position in real-world values.
		Point2D.Double realCurrentMousePoint = cpDrawing.pt2RealPt(new Point(x, y), width, height);
		// Convert the mouse position to a complex number.
		Complex complexCurrentMousePoint = new Complex(realCurrentMousePoint);
		// Calculate the center of the canvas screen as a complex number.
		Complex complexScreenCenter = cpDrawing.realBox.lz.add(cpDrawing.realBox.rz).divide(2.0D);
		// Initialize translation vector; this will hold how much to translate the screen.
		Complex scaledTranslationVector;

//System.out.println("clicks = "+mouseWheelClicks);			

		// NOTE: I've changed these to only do one zoom, irrespective
		//       of number of clicks
		// Mouse was rotated away from user; zoom in.
		if (mouseWheelClicks < 0) {
			// Calculate the translation vector and move the screen before zooming in.
			scaledTranslationVector = complexCurrentMousePoint.
					minus(complexScreenCenter).mult(1.0D - mouseWheelZoomInMultiplier);
			cpDrawing.realBox.transView(scaledTranslationVector);
			zoomIn(mouseWheelZoomInMultiplier);
		}
		// Mouse was rotated toward user; zoom out.
		else if (mouseWheelClicks > 0) {
			scaledTranslationVector = complexCurrentMousePoint.
					minus(complexScreenCenter).mult(1.0D - mouseWheelZoomOutMultiplier);
			cpDrawing.realBox.transView(scaledTranslationVector);
			zoomOut(mouseWheelZoomOutMultiplier);
		}
	}
	//<<<AF//

	/**
	 * Throw in correct 'CPDrawing' image 
	 */
	public void paintComponent(Graphics g) {
		g.drawImage(cpDrawing.packImage,0,0,getWidth(),getHeight(),null);
		if (cpDrawing.isAxisMode()) {
			Graphics2D g2=(Graphics2D)g;
			cpDrawing.drawXAxis(g2);
			cpDrawing.drawYAxis(g2);
		}
	}
	
}