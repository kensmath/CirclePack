package script;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * This is the base panel containing all the 'stackBox' objects which
 * display script elements. There are basically only three objects
 * directly added to this in 'PackControl.initScriptArea': the 'scriptSBox'
 * containing CPScript elements, a rigid spacer, and the 'dataSBox'
 * containing CPData elements (i.e., files). 
 * (Width used to be determined in 'initScriptArea' by a rigid element, but
 * I don't know what's happening now.)
 * @author kens
 *
 */
public class StackArea extends JPanel implements Scrollable {

	private static final long 
	serialVersionUID = 1L;
	
	// Constructor
	public StackArea() {
		super();
		setBackground(Color.blue); // this only shows through on spacer
		setOpaque(true);
	}

	public Dimension getPreferredScrollableViewportSize() {
		return new Dimension(550,400);
	}

	public int getScrollableUnitIncrement(Rectangle visibleRect,
			int orientation, int direction) {
		return 10; // number of pixels to shift
	}

	public int getScrollableBlockIncrement(Rectangle visibleRect,
			int orientation, int direction) {
		if (orientation == SwingConstants.HORIZONTAL)
			return (int)(.75*visibleRect.width);
		else
			return (int)(.75*visibleRect.height);
	}

	public boolean getScrollableTracksViewportWidth() {
		// TODO Auto-generated method stub
		return true;
	}

	public boolean getScrollableTracksViewportHeight() {
		// TODO Auto-generated method stub
		return false;
	}

}
