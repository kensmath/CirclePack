package widgets;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import images.CPIcon;

/**
 * Part of the structure for general purpose bar graphs.
 * This JPanel contains a vertex label, a bar, and optionally an icon. 
 * Bar height, color, perhaps icon placement are controlled from outside. 
 * The bar may be adjustable via the mouse. 
 */
public class ActiveBar extends JPanel implements MouseListener,
		MouseMotionListener {

	private static final long 
	serialVersionUID = 1L;

	static final int COL_WIDTH=14;
	static final int COL_HEIGHT=300;

	BarGraphFrame parent;   
	int index;       // index of object for this bar (e.g., vert index)
	
	// data values
	double val_min;
	double val_max;
	double value;    // current value
	int col_width;
	int col_height;
	double bar_factor;   // col_height/(val_max-val.min)
	
	Color bar_color;
	ImageIcon icon;
	JLabel pointer; // for movable icon
	boolean active;  // true if mouse can move 
	
	// components
	public JPanel barArea; // region bar floats in
	public JPanel bar; // bar itself

	// Constructor (default)
	public ActiveBar(BarGraphFrame bgf, int indx, boolean actv) {
		setOpaque(false);
		setLayout(null);
		parent = bgf;
		index=indx;
		active=actv;

		val_min=0.0;
		val_max=100.0;
		value=0.0;
		bar_color=Color.blue;
		col_width=COL_WIDTH;
		col_height=COL_HEIGHT;
		if (active) 
			icon = CPIcon.CPImageIcon("main/astrisk.png");
		else
			icon = CPIcon.CPImageIcon("main/l_drop.png");
		pointer=null;

		// the outlined bar area
		barArea = new JPanel();
		barArea.setLayout(null);
		barArea.setBorder(new LineBorder(Color.BLACK));
		barArea.setBackground(Color.CYAN);
		barArea.setOpaque(true);
		barArea.addMouseListener(this);
		if (active)
			barArea.addMouseMotionListener(this);
//		barArea.setSize(new Dimension(col_width,col_height));
		barArea.setBounds(0,0,col_width,col_height);
		add(barArea);
		barArea.setVisible(true);

		// the bar itself
		bar = new JPanel();
		bar.setBorder(new LineBorder(Color.DARK_GRAY));
		bar.setBackground(Color.GRAY);
		bar.setOpaque(true);
		setBarValue(value);
		barArea.add(bar);
	}
	
	/**
	 * Set sizes, both visual and data, and compute 'bar_factor'
	 * @param col_wide int
	 * @param col_high int
	 * @param v_min double
	 * @param v_max double
	 * @return
	 */
	public void bar_size_settings(int col_wide,int col_high,double v_min,double v_max) {
		col_width=col_wide;
		col_height=col_high;
		val_min=v_min;
		val_max=v_max;
		bar_factor=((double)col_high)/(val_max-val_min);
	}

	/**
	 * reset the height of this bar
	 * @param newvalue
	 */
	public void setBarValue(double newvalue) {
		if (newvalue<val_min)
			value=val_min;
		else if (newvalue>val_max)
			value=val_max;
		else
			value=newvalue;
		int pixheight=(int)((value-val_min)/(val_max-val_min)*bar_factor);
		bar.setBounds(0, col_height-pixheight, col_width+2, col_height);
	}

	public void setBarGray() {
		bar.setBackground(Color.gray);
	}

	public void setBarRed() {
		bar.setBackground(Color.red);
	}

	public void setBarGreen() {
		bar.setBackground(Color.green);
	}

	/**
	 * Where to place the 'pointer' to reflect a value 
	 * @param location double 
	 */
	public void placePointer(double location) {
		if (pointer == null) {
			pointer = new JLabel(icon);
			pointer.setPreferredSize(new Dimension(14, 14));
			pointer.setBounds(14, col_height - 6, 14, 14);
			add(pointer);
		}

		int pixlocation=(int)((location-val_min)/(val_max-val_min)*bar_factor);
		int y = col_height - pixlocation;
		if (y < 0) y=0;

		pointer.setBounds(14, y - 6, 14, 14);
	}

	// left/right click to move bar.
	public void mouseClicked(MouseEvent evt) {
		
		// get value with mouse1 click
		if (evt.getButton() == MouseEvent.BUTTON1) {
			int y = evt.getPoint().y;
			double newValue = val_min+(double) (col_height - y) / bar_factor+val_min;
			mouse1action(newValue);
		}

	}
	
	/**
	 * Default: set this new value selected by mouse. (Can be overriden)
	 * @param val double new value
	 * @param indx int, object's index
	 */
	public void mouse1action(double val) {
		parent.captureValue(val,index);
		placePointer(val);
	}

	/**
	 * Notify parent 
	 */
	public void mouseEntered(MouseEvent evt) {
		parent.mouse_entry_action(index);
	}

	// rest of needed mouse calls
	public void mouseReleased(MouseEvent evt) {
	}

	public void mousePressed(MouseEvent evt) {
	}

	public void mouseExited(MouseEvent evt) {
	}

	// I believe this interprets a drag like it would a mouse 1 click
	public void mouseDragged(MouseEvent evt) {
		mouseClicked(new MouseEvent(evt.getComponent(), 0, 0, 0, evt.getX(),
				evt.getY(), 1, false, MouseEvent.BUTTON1));
	}

	public void mouseMoved(MouseEvent evt) {
	}

}
