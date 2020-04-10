package frames;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * The FluidHoverPanel component is a container class that initially appears
 * as a tool tip for its parent component, but locks into a standard frame
 * when moused over. If the frame is currently locked, mousing over the
 * parent component will restore and focus on the frame. This class is
 * intended to be instantiated around a parent component and treated as a
 * standard JPanel for containing and displaying components.
 * 
 * The FluidHoverPanel is a re-implementation of the standard HoverPanel
 * that is able to fluidly resize.
 * 
 * @author kens
 * @author Alex Fawkes
 *
 */
public class FluidHoverPanel extends JPanel {
	/*
	 * Regenerate serialVersionUID whenever the nature of this class's fields change
	 * so that this class may be flattened. As background, serialization provides a
	 * unified interface for writing and reading an instance's current state to and
	 * from the file system. The value of serialVersionUID is used to ensure that an
	 * instance state being read from the file system is compatible.
	 * 
	 * Note that we are sub-classing a class that implements serialization (JPanel),
	 * so we must respect that in our implementation.
	 */
	private static final long serialVersionUID = 5256155314562457494L;
	
	protected final static int APPEAR_DELAY = 1000; // Delay on tool tip appearance in milliseconds.
	protected final static int DISAPPEAR_DELAY = 100; // Delay on tool tip disappearance in milliseconds.
	protected final static int X_OFFSET = 15; // Offset to show tool tip relative to cursor.
	protected final static int Y_OFFSET = 15;

	protected boolean locked = false; // Whether or not this instance is locked.

	protected JFrame lockedFrame; // Standard decorated frame displayed when this instance is locked.
	protected JFrame hoverFrame; // Tool tip style undecorated frame displayed when the parent component is moused over.

	// We instantiate the threads here so we can interrupt them later without
	// checking if they've been instantiated.
	protected Thread appearSleeper = new Thread(); // Sleeper thread to count down until displaying tool tip.
	protected Thread disappearSleeper = new Thread(); // Sleeper thread to count down until hiding tool tip.

	protected final FluidHoverPanel currentInstance = this; // For referencing from threads and subclasses.

	/**
	 * The FluidHoverPanel must be constructed with a reference to its parent
	 * component. Do not use this constructor.
	 */
	@SuppressWarnings("unused")
	private FluidHoverPanel() {}

	/**
	 * The FluidHoverPanel must be constructed with a reference to its parent
	 * component. The passed parent component will display this FluidHoverPanel
	 * when it is moused over.
	 * 
	 * @param parent the component to attach this instance to
	 */
	public FluidHoverPanel(JComponent parent) {
		super();

		hoverFrame = new JFrame();
		hoverFrame.setUndecorated(true);
		hoverFrame.addMouseListener(new MouseAdapter() {
			// If the mouse enters the hover frame, lock it.
			@Override
			public void mouseEntered(MouseEvent e) {
				setLocked(true);
			}
		});
		// We pack the frames so we can get sizing information from them
		// before they are drawn to the screen for the first time.
		hoverFrame.pack();

		lockedFrame = new JFrame();
		lockedFrame.addWindowListener(new WindowAdapter() {
			// If the user closes the locked frame, unlock this instance.
			@Override
			public void windowClosing(WindowEvent we) {
				setLocked(false);
			}
		});
		lockedFrame.pack();

		// Add the hover adapter for controlling mouse over behavior.
		parent.addMouseListener(new HoverAdapter());
	}

	/**
	 * Set the title of this instance. The title will display
	 * in the title bar of the locked frame.
	 * 
	 * @param title the title of the locked frame
	 */
	public void setTitle(String title) {
		lockedFrame.setTitle(title);
	}

	/**
	 * Set whether or not this instance is currently locked. A locked
	 * instance will display the panel in a visible and decorated frame.
	 * 
	 * @param lock whether or not to lock this instance
	 */
	public void setLocked(boolean lock) {
		if (lock) {
			// Interrupt any queued appearance or disappearance events so that we
			// don't have pending behavior that conflicts with what we do here.
			appearSleeper.interrupt();
			disappearSleeper.interrupt();

			// Hide the hover frame and note that this instance is now locked.
			hoverFrame.setVisible(false);
			locked = true;

			/*
			 * Calculate where to put the locked frame, accounting for the size of the
			 * frame. We want the frame to visually pop into place around the hover
			 * panel without it seeming to move, so we subtract the height of the title
			 * bar from the x location and the width of the left frame border from the
			 * y location.
			 */
			int x = hoverFrame.getLocation().x;
			int y = hoverFrame.getLocation().y;
			x -= lockedFrame.getInsets().left;
			y -= lockedFrame.getInsets().top;

			// Move the frame if it would be jammed up in the top left corner.
			// This happens if the hover frame hasn't displayed before the instance is locked.
			if (x < 0 || y < 0) {
				Point mousePoint = MouseInfo.getPointerInfo().getLocation();
				x = mousePoint.x;
				y = mousePoint.y;
			}

			// Prepare and show the locked frame.
			lockedFrame.setLocation(new Point(x, y));
			lockedFrame.add(this);
			lockedFrame.pack();
			lockedFrame.setVisible(true);
		} else {
			// If we are unlocking, just mark it and hide the locked frame.
			locked = false;
			lockedFrame.setVisible(false);
		}
	}

	/**
	 * Determines whether or not this instance is currently locked. If the
	 * instance is locked, the panel is currently displayed in a visible
	 * and decorated frame.
	 * 
	 * @return whether or not the instance is currently locked.
	 */
	public boolean isLocked() {
		return locked;
	}
	
	/**
	 * Set the state of lockedFrame if it's visible. E.g., to
	 * Frame.NORMAL
	 */
	public void setState(int state) {
		if (lockedFrame.isVisible())
			lockedFrame.setState(state);
	}

	/**
	 * HoverAdapter is the internal class of FluidHoverPanel that controls
	 * the appearance of the tooltip panel. It is a MouseAdapter that gets
	 * attached to the parent component when FluidHoverPanel is instantiated,
	 * and reacts to MouseEvents received by the parent component.
	 * 
	 * @author Alex Fawkes
	 *
	 */
	protected class HoverAdapter extends MouseAdapter {
		@Override
		public void mouseEntered(MouseEvent me) {
			// The user has moused over the parent component.
			// Create the thread to show the hover frame after a delay.
			appearSleeper = new Thread() {
				@Override
				public void run() {
					try {
						if (locked) Thread.sleep(2 * APPEAR_DELAY); // Sleep for double time if the frame is locked.
						else Thread.sleep(APPEAR_DELAY);
					} catch (InterruptedException e) {return;} // If we get interrupted, just return.

					// If we haven't been interrupted, pop up the appropriate frame.
					if (locked) {
						// The frame is currently locked, so restore and focus on the locked frame.
						// We are in a separate thread, and all GUI changes must be made from the EventQueue.
						EventQueue.invokeLater(new Runnable() {
							@Override
							public void run() {
								lockedFrame.setExtendedState(Frame.NORMAL);
								lockedFrame.requestFocus();
							}
						});
					} else {
						// The frame is currently unlocked, so show the hover frame.
						// We are in a separate thread, and all GUI changes must be made from the EventQueue.
						EventQueue.invokeLater(new Runnable() {
							@Override
							public void run() {
								// Get the absolute mouse location.
								Point mousePoint = MouseInfo.getPointerInfo().getLocation();

								// Calculate the location for the hover frame to appear, accounting for offsets.
								int x = mousePoint.x + X_OFFSET;
								int y = mousePoint.y + Y_OFFSET;

								// Move the frame if it would appear off the screen.
								Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
								if (x + hoverFrame.getWidth() > screenSize.width) x -= hoverFrame.getWidth() + X_OFFSET * 2;
								if (y + hoverFrame.getHeight() > screenSize.height) y -= hoverFrame.getHeight() + Y_OFFSET * 2;

								// Prepare and show the hover frame.
								Point hoverLocation = new Point(x, y);
								hoverFrame.setLocation(hoverLocation);
								hoverFrame.add(currentInstance);
								hoverFrame.pack();
								hoverFrame.setVisible(true);
							}
						});
					}
				}
			};
			appearSleeper.start();
		}

		@Override
		public void mouseExited(MouseEvent me) {
			// If the mouse leaves the parent component, stop the hover frame appearance process.
			appearSleeper.interrupt();

			// If the panel isn't locked and the hover frame is visible, count down to make it disappear.
			if (!locked && hoverFrame.isVisible()) {
				disappearSleeper = new Thread() {
					@Override
					public void run() {
						try {Thread.sleep(DISAPPEAR_DELAY);}
						catch (InterruptedException e) {return;} // If we get interrupted, just return.

						/*
						 * Manually check to see if the mouse is in the hover frame. If it is, lock this instance.
						 * We do this because sometimes components displayed in this panel will consume the mouse
						 * entered event that normally locks this instance.
						 */
						Point mousePoint = MouseInfo.getPointerInfo().getLocation();
						Point hoverLocation = hoverFrame.getLocation();
						Dimension hoverSize = hoverFrame.getSize();
						
						if (mousePoint.x >= hoverLocation.x && mousePoint.y >= hoverLocation.y
								&& mousePoint.x <= hoverLocation.x + hoverSize.width && mousePoint.y <= hoverLocation.y + hoverSize.width) {
							// The mouse is in the hover frame. Lock this instance.
							// We are in a separate thread, and all GUI changes must be made from the EventQueue.
							EventQueue.invokeLater(new Runnable() {
								@Override
								public void run() {
									currentInstance.setLocked(true);
								}
							});
						} else {
							// The mouse is not in the hover frame. Hide the hover frame.
							// We are in a separate thread, and all GUI changes must be made from the EventQueue.
							EventQueue.invokeLater(new Runnable() {
								@Override
								public void run() {
									hoverFrame.setVisible(false);
								}
							});
						}
					}
				};
				disappearSleeper.start();
			}
		}

		@Override
		public void mousePressed(MouseEvent me) {
			/*
			 * If the user presses the mouse button on the parent component, interrupt the appearance
			 * process. We do this because the parent component is often a button, and that button is
			 * frequently set to show the locked frame. We don't want to show the locked frame and have
			 * the hover frame queued to appear at the same time.
			 */
			appearSleeper.interrupt();
		}
	}
}