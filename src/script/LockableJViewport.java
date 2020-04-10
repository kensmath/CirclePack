package script;

import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JViewport;

/** 
 * LockableJViewport is a JViewport with the added functionality of view locking.
 * When locked, the viewport will ignore requests to change the view position.
 * 
 * @see JViewport
 * @see LockableJViewport#setLocked(boolean)
 * @author Alex Fawkes
 */
@SuppressWarnings("serial") // Serialization not implemented.
public class LockableJViewport extends JViewport {
	protected boolean locked = false;
	protected int lockCount = 0;

	/**
	 * Sets whether or not the viewport is currently locked. While the viewport is
	 * locked, it will ignore requests to change the view position. The viewport may
	 * be locked multiple times, in which case it will not actually unlock until it
	 * is called to unlock at least as many times as it has been called to lock.
	 * 
	 * @param locked <code>true</code> to lock the viewport or <code>false</code> to unlock
	 */
	public void setLocked(boolean locked) {
		if (locked) {
			lockCount++;
			this.locked = locked;
		}
		else {
			lockCount--;
			if (lockCount <= 0) this.locked = locked;
		}
	}
	
	@Override
	public void scrollRectToVisible(Rectangle contentRect) {
		if (locked) return;
		super.scrollRectToVisible(contentRect);
	}
	
	@Override
	public void setViewPosition(Point p) {
		if (locked) return;
		super.setViewPosition(p);
	}
}
