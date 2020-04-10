package fauxScript;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/* This is a simple custom JPanel. When added to a 
 * JScrollPane, it will automatically resize itself
 * to the width of the view port of the JScrollPane.
 */
public class FWSJPanel extends JPanel implements Scrollable {
	private static final long serialVersionUID = 1L;

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		/* This dimension is totally arbitrary. I have no idea
		 * what it does, but it worked fine so long as this function
		 * returns something.
		 */
		return new Dimension(100, 100);
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		/* How many pixels to scroll for large scrolls, like when
		 * clicking a position along the scroll bar track.
		 */
		if (orientation == SwingConstants.VERTICAL) return 60;
		else return 120;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		/* Returning true in this function causes the JPanel
		 * to automatically resize itself to the width of the
		 * view port of the JScrollPane it is in.
		 */
		return true;
	}
	
	@Override
	public boolean getScrollableTracksViewportHeight() {
		/* Return false so it won't do the same for height. */
		return false;
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		/* How many pixels to scroll for small scrolls, like mouse wheel
		 * scrolling or clicking the arrows on the scroll bar.
		 */
		if (orientation == SwingConstants.VERTICAL) return 20;
		else return 40;
	}
}