package frames;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import circlePack.PackControl;

/**
 * HoverPanel is a JPanel which resides in one of two frames 
 * that it creates, one for hovering, one for locking. 
 * Typically, buttons or other objects create this for its
 * contents and add this as a mouse listener for hover/lock 
 * actions. E.g., a mouse hover displays a frame without border 
 * having this as its sole panel. Moving the mouse into the 
 * hovering frame locks it in place; this panel is moved (and 
 * possibly changed) to that frame and the frame gets a border. 
 * If the frame is already locked (or iconified), the hover 
 * will bring it to the top (or open it). Clicking the button 
 * may toggle the frame between locked/visible and 
 * unlocked/invisible.
 * @author kstephe2 and Alex Fawkes
 *
 */
public class HoverPanel extends JPanel implements MouseListener {
	
	private static final long 
	serialVersionUID = 1L;

	Component parent; // component this is tethered to

	public JFrame lockedFrame; // decorated frame 
	public JFrame hoverFrame; // undecorated frame
	public boolean locked; // status

	final static int APPEAR_DELAY = 1000; // delay on tooltip appearance in milliseconds
	final static int DISAPPEAR_DELAY = 100; // delay on tooltip disappearance in milliseconds
	
	// default starting location
	public int XLoc=120; 
	public int YLoc=60;
	
	// location offsets from mouse
	public int XOffset=15;
	public int YOffset=15;
	
	public int myWidth;
	public int myHeight;
	Thread appearSleeper; //sleeper thread, to count down until displaying tooltip
	Thread disappearSleeper; //sleeper thread, to count down until hiding tooltip
	Insets insets; //frame border size
	
	// Constructor
	public HoverPanel(int wide,int high,String title) {
		super();
		myWidth=wide;
		myHeight=high;
		locked=false; // user must call 'lockframe' to show
		
		hoverFrame = new JFrame();
		hoverFrame.setLocation(XLoc,YLoc);
		hoverFrame.setResizable(true); // ?? false ??
		hoverFrame.setPreferredSize(new Dimension(myWidth,myHeight));
		hoverFrame.setUndecorated(true);
		hoverFrame.setVisible(false);	
		
		lockedFrame = new JFrame();
		lockedFrame.setLocation(XLoc,YLoc);
		lockedFrame.setResizable(false);
		lockedFrame.setPreferredSize(new Dimension(myWidth,myHeight));
		lockedFrame.addWindowListener(new LockAdapter()); 
		lockedFrame.setUndecorated(false);
		lockedFrame.setTitle(title);
		lockedFrame.setVisible(false);
		
		initComponents();
		setInitPanel();
	}
	
	// ==================== methods to override =================
	/**
	 * Create the components for both frames
	 */
	public void initComponents() {}
	
	/**
	 * Call for initial formating of 'this' panel (may depend on locked state)
	 */
	public void setInitPanel() {
		loadHover();
		// .pack() will be called when hover is started
	}
	
	/**
	 * Reformat 'this' panel (if necessary)
	 */
	public void loadHover() {}
	
	/**
	 * if there's some action to take before hovering
	 */
	public void hoverCall() {}
	
	/**
	 * Reformat 'this' panel (if necessary)
	 */
	public void loadLocked() {}

	// ==================
	
	public void reloadHover() {
		loadHover();
	}
	
	/**
	 * Locks frame with decorations and makes visible
	 */
	public void lockframe() {
		hoverFrame.setVisible(false);
		loadLocked();
		lockedFrame.add(this);
		locked=true;
		lockedFrame.pack();
		lockedFrame.setVisible(true);
	}
	
	/**
	 * Is the frame locked? (i.e., visible (or iconified) and decorated)
	 * @return
	 */
	public boolean isLocked() {
		return locked;
	}
	
	/**
	 * For calling from other thread (where 'this' means the thread)
	 * @param jF
	 */
	public void addThis(JFrame jF) {
		jF.add(this);
	}
	
	/**
	 * Set the x and y value just off the mouse location
	 * @param me, MouseEvent (or null)
	 * @return Point for locating 
	 */
	public Point setXY(MouseEvent me) {
		// if the user mouses over the component
		Point mousePoint=null;
		if (me==null)
			new Point(XLoc,YLoc);
		else
			mousePoint = new Point(me.getX(), me.getY());
		SwingUtilities.convertPointToScreen(mousePoint, me.getComponent());
		int x = (int) mousePoint.getX() + XOffset;
		int y = (int) mousePoint.getY() + YOffset;

		// if the hoverFrame would display off the screen
		// move it to other side of mouse
		if (x + myWidth > PackControl.displayDimension.width) x = x - myWidth - 10;
		if (y + myHeight > PackControl.displayDimension.height) y = y - myHeight - 10;
		
		return new Point(x,y);
	}
	
	public void mouseReleased(MouseEvent me) {}
	public void mouseClicked(MouseEvent me) {}

	public void mousePressed(MouseEvent me) {
/* TODO: Need method to disable: I'm getting deep error of some sort as of 6/2020		// if the user presses the mouse, interrupt hoverFrame waiting to lock
		appearSleeper.interrupt();
*/		
	}

	public void mouseEntered(MouseEvent me) {
	
/* TODO: Need method to disable: I'm getting deep error of some sort as of 6/2020
         final Point pt=setXY(me);
			
        // create a new thread
        appearSleeper = new Thread(new Runnable() {
        	public void run() {
        		boolean interrupted = false;

        		try {
        			// sleep before hover or before bringing locked to top
        			if (!locked)
        				Thread.sleep(APPEAR_DELAY);
        			else
        				Thread.sleep(2*APPEAR_DELAY);
        		} catch (InterruptedException ie) {
        			// if interrupted, flag it
        			interrupted = true;
        		}

        		// if not interrupted, show hoverFrame near mouse
        		if (!interrupted && !locked) {
        			hoverCall();
        			addThis(hoverFrame);
        			hoverFrame.pack();
        			hoverFrame.setLocation(pt);
        			hoverFrame.setVisible(true);
        		}
        		// if locked, bring lockedFrame to top
        		else if (!interrupted) {
        			lockedFrame.setState(Frame.NORMAL);
        			lockedFrame.toFront();
        			// TODO: how to un-iconify if iconified?
        		}
        	}
        });

        //run the thread
        appearSleeper.start();
        */
	}
	
	public void mouseExited(MouseEvent me) {
		
/*  TODO: Need method to disable: I'm getting deep error of some sort as of 6/2020		
		// if mouse leaves the parent component
		// (no need to run this code if frame is locked)
		appearSleeper.interrupt();

		if (!locked) {
			// interrupt the appearance sleeper thread
			// (hoverFrame may or may not be already visible, doesn't matter)
			me.consume();

			// start a disappearance sleeper thread if hoverFrame is visible;
			//   this allows time for the mouse to enter the hover and lock it
			if (hoverFrame.isVisible()) {
				final Point pt=hoverFrame.getLocation();
				disappearSleeper = new Thread(new Runnable() {
					public void run() {
						boolean interrupted = false;

						try {
							Thread.sleep(DISAPPEAR_DELAY);
						} catch (InterruptedException e) {
							interrupted = true;
						}

						// if mouse is in hoverFrame, thread is not interrupted, 
						//   and frame is not currently locked, then lock it
						if (isMouseInHover() && !interrupted && !isLocked()) { 
							lockedFrame.setLocation(pt);
							lockframe();
						}
						// otherwise hide the hoverFrame
						else hoverFrame.setVisible(false);
					}
				});

				//run the thread
				disappearSleeper.start();
			}
		}
*/		
	}
	
	private boolean isMouseInHover() {
/*  TODO: Need method to disable: I'm getting deep error of some sort as of 6/2020				// return true if mouse is in hoverFrame, false otherwise
		int curX = (int) MouseInfo.getPointerInfo().getLocation().x;
		int curY = (int) MouseInfo.getPointerInfo().getLocation().y;

		Point pt=hoverFrame.getLocation();
		if (curX >= pt.x && curX <= pt.x + myWidth && curY >= pt.y && curY <= pt.y + myHeight) {
			return true;
		}
*/		
		return false;
	}

	/**
	 * Close the lockedFrame and load the hoverFrame.
	 */
	class LockAdapter extends WindowAdapter {
		public void windowClosing(WindowEvent wevt) {
			if (wevt.getID() == WindowEvent.WINDOW_CLOSING) {
				lockedFrame.setVisible(false);
				locked=false;
				hoverFrame.setVisible(false);
				reloadHover();
			}
		}
	}

}