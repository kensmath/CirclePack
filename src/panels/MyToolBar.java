package panels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import mytools.MyPopupMenu;

/**
 * This is a panel of buttons for user-defined (or script-defined, etc.)
 * commands. They are created, managed, etc. by a MyToolHandler.
 *
 * @kens
 */
public class MyToolBar extends JPanel implements MouseListener {

	private static final long 
	serialVersionUID = 1L;
	
	private MyPopupMenu myToolMenu;

	// Constructor
	public MyToolBar() {
		setBorder(new LineBorder(Color.black));
	    addMouseListener(this);
		setLayout(new FlowLayout(FlowLayout.LEFT));
		// this.setBackground(Color.WHITE);
	    myToolMenu=null;
	}

	public void setPopupMenu(MyPopupMenu pum) {
		myToolMenu=pum;
	}

	/**
	 * Just for popup menu on the bar's background; mouse events on
	 * the tools themselves are handled elsewhere.
	 */
	public void mouseReleased(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {
		if (myToolMenu!=null && e.getButton() == MouseEvent.BUTTON3) {
			myToolMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
}
