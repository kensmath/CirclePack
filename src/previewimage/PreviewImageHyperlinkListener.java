package previewimage;

import java.awt.EventQueue;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * <code>PreviewImageHyperlinkListener</code> is a <code>HyperlinkListener</code>
 * that displays XMD script preview images in tool tips when hyperlinks to XMD
 * scripts are moused over. Typically, an instance of this class would be added as
 * a <code>HyperlinkListener</code> to a <code>JEditorPane</code> that contains
 * hyperlinks to XMD scripts.
 * 
 * @author Alex Fawkes
 *
 */
public class PreviewImageHyperlinkListener implements HyperlinkListener {
	protected static final int MINIMUM_DELAY = 600; // Minimum delay to display tooltip, in milliseconds.
	protected static final int OFFSET = 20; // Distance from mouse to display tooltip, in pixels.
	protected JFrame frame = new JFrame(); // Tooltip frame, tracked for recycling.
	protected volatile HyperlinkEvent currentHyperlinkEvent; // Current hyperlink event, explanation given on first assignment.
	
	/**
	 * Create a new <code>PreviewImageHyperlinkListener</code> instance. Add this
	 * instance to components that display hyperlinks to show XMD script preview
	 * images in tooltips when XMD script hyperlinks are moused over.
	 */
	public PreviewImageHyperlinkListener() {}
	
	// Called whenever the component this listener is attached to detects that a hyperlink
	// has been entered, exited, or activated.
	public void hyperlinkUpdate(final HyperlinkEvent e) {
		// Old versions of Java don't support getting the time of a hyperlink event from
		// the object itself, so use the time that this function was called instead.
		final long eventTime = new Date().getTime();
		
		/*
		 * Dispose of the previous frame and note that the current hyperlink event has changed.
		 * 
		 * Clearly, if a hyperlink has been exited (moused out of), we would like to dipose
		 * the tooltip. If a hyperlink has been activated (clicked), we would also like
		 * to dispose the tooltip. If a hyperlink has been entered (moused over), the
		 * tooltip should already be disposed from a previous exit or never displayed. The
		 * redundancy doesn't hurt and is faster than checking.
		 * 
		 * We track the current hyperlink event to handle thread timing issues. Later, if
		 * the user mouses over an XMD script hyperlink, we'll spin a thread to parse out
		 * the preview image, which may take a long time over the net. Once it has the
		 * preview image, it will post code to the Swing EventQueue to display it in a
		 * tooltip near the mouse. However, the user may have moused out of the hyperlink
		 * and be across the screen at this point.
		 * 
		 * Thus, we compare the hyperlink event we are responding to and this instance's
		 * most current received hyperlink event. If they are not the same, we cancel the
		 * tooltip display. This behavior occurs below, in the thread definition.
		 */
		frame.dispose();
		currentHyperlinkEvent = e;

		// If a hyperlink has been entered (moused over)...
		if (e.getEventType().equals(HyperlinkEvent.EventType.ENTERED)) {
			// If the hyperlink points to an XMD script...
			if (e.getURL().getPath().endsWith(".xmd")) {
				// Spin a thread to parse out the image and display it in a tooltip.
				new Thread() {
					@Override
					public void run() {
						// Extract the image from the URL of the hyperlink event. If we get any errors
						// or fail to find an image, just give up and return.
						PreviewImageExtractor pie = new PreviewImageExtractor(e.getURL());
						final Image image;
						try {image = pie.getImage();}
						catch (IOException e) {return;}
						if (image == null) return;

						/*
						 * We don't want instant tooltips. Figure out how much time has elapsed since
						 * the user moused over the hyperlink. If retrieving the image has taken less
						 * than the minimum delay for the tooltip, sleep for the difference. If we
						 * get interrupted while sleeping, continue to display the tooltip.
						 */
						long currentTime = new Date().getTime();
						long sleepTime = MINIMUM_DELAY - (currentTime - eventTime);
						if (sleepTime > 0) {
							try {Thread.sleep(sleepTime);}
							catch (InterruptedException e) {}
						}
						
						// Always do GUI work on the EventQueue (we are in a spun thread).
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								/*
								 * If this thread's hyperlink event is not the same as the parent instance's
								 * most recent hyperlink event, cancel the tooltip by returning.
								 * 
								 * For example, the parent instance might have received an exited hyperlink
								 * event since this thread spun to grab the image in response to a previous
								 * entered hyperlink event. Since the user has moused out of the hyperlink,
								 * we don't display the tooltip.
								 */
								if (e != currentHyperlinkEvent) return;
								
								// Get the mouse position for tooltip placement.
								Point mousePoint = MouseInfo.getPointerInfo().getLocation();

								// Initialize the frame. It should have no title or border, dispose when
								// closed, and dispose when moused over.
								frame = new JFrame();
								frame.setUndecorated(true);
								((JComponent) frame.getContentPane()).setBorder(BorderFactory.createCompoundBorder(
										BorderFactory.createRaisedBevelBorder(),
										BorderFactory.createLoweredBevelBorder()));
								frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
								frame.addMouseListener(new MouseAdapter() {
									// This listener should always dispose the tooltip when the corresponding hyperlink
									// is moused out of. In case that fails, dispose the tooltip if it is moused over.
									@Override
									public void mouseEntered(MouseEvent e) {frame.dispose();}
								});
								// Add the most recent image, place it near the mouse, lay it out, then show it.
								// TODO: Add functionality to detect off screen display and adjust appropriately.
								frame.add(new JLabel(new ImageIcon(image)));
								frame.setLocation(new Point(mousePoint.x + OFFSET, mousePoint.y + OFFSET));
								frame.pack();
								frame.setVisible(true);
							}
						});
					}
				}.start();
			}
		}
	}
}