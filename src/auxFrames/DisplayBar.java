package auxFrames;
import images.CPIcon;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import allMains.CirclePack;

/**
 * This JPanel contains a vertex label, a bar, and possibly an icon. If
 * mode=true, then this represents radius info and the height of bar can be
 * adjusted with the mouse. If mode=false, this is angle sum info provided from
 * outside. the bar color and the icon placement are controlled from outside.
 */
public class DisplayBar extends JPanel implements MouseListener,
		MouseMotionListener {

	private static final long 
	serialVersionUID = 1L;
	
	// top of radius bar is 1.5*pi
	static final double MAX_RADIUS_MULT = 1.5;
	static final int BAR_WIDTH=14;
	
	SphWidget parent;
	int vertNum;
	boolean mode; // true for radii, false for anglesums
	double factor; // for converting data value to pixel height

	public JPanel barArea; // region bar floats in
	public JPanel bar; // bar itself
	int bar_length;
	ImageIcon icon;
	JLabel pointer; // for movable icon

	// Constructor
	public DisplayBar(SphWidget sphTool, int vertnum, boolean type,
			double value) {
		setOpaque(false);
		parent = sphTool;
		vertNum = vertnum;
		mode = type;
		setLayout(null);
		pointer = null;
		if (mode) { // radii
			bar_length = SphWidget.RAD_BAR_HEIGHT;
			factor = ((double) (bar_length) / (DisplayBar.MAX_RADIUS_MULT * Math.PI));
			icon = CPIcon.CPImageIcon("main/astrisk.png");
		} else { // angle sums
			bar_length = SphWidget.ANG_BAR_HEIGHT;
			factor = (((double) (bar_length) / (5.0 * Math.PI))); // allow up
																	// to 5*Pi
			icon = CPIcon.CPImageIcon("main/l_drop.png");
		}

		// the outlined bar area
		barArea = new JPanel();
		barArea.setLayout(null);
		barArea.setBorder(new LineBorder(Color.BLACK));
		barArea.setBackground(Color.CYAN);
		barArea.setOpaque(true);
		barArea.addMouseListener(this);
		if (mode)
			barArea.addMouseMotionListener(this);
		if (mode)
			barArea.setBounds(0, 0, BAR_WIDTH, bar_length);
		else
			barArea.setBounds(0, 0, BAR_WIDTH, bar_length);
		add(barArea);

		// the bar itself
		bar = new JPanel();
		bar.setBorder(new LineBorder(Color.DARK_GRAY));
		bar.setBackground(Color.GRAY);
		bar.setOpaque(true);
		setBarHeight(value);
		barArea.add(bar);
	}

	public void setBarHeight(double newvalue) {
		int length = (int) (newvalue * factor);
		if (mode) { // convert to log factor
			double temp = (Math.log(1.0 / (newvalue / Math.PI)) / Math.log(2.0))
					/ SphWidget.RAD_BAR_POWER;
			length = bar_length - (int) Math.round(temp * (double) bar_length);
		}
		bar.setBounds(0, bar_length - length, 16, bar_length);
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

	public void placePointer(double location) {
		if (pointer == null) {
			pointer = new JLabel(icon);
			pointer.setPreferredSize(new Dimension(14, 14));
			pointer.setBounds(14, bar_length - 6, 14, 14);
			add(pointer);
		}

		int y = bar_length - (int) (location * factor);
		if (mode) { // convert to log factor
			double temp = (Math.log(1.0 / (location / Math.PI)) / Math.log(2.0))
					/ SphWidget.RAD_BAR_POWER;
			y = (int) Math.round(temp * (double) bar_length);
		}
		if (y < 0)
			y = 0;
		pointer.setBounds(14, y - 6, 14, 14);
	}

	// left/right click to move bar.
	public void mouseClicked(MouseEvent evt) {
		
		// for angsum bar, allow setting any aim up to 5*Pi.
		if (evt.getButton() == MouseEvent.BUTTON3 || (evt.getButton()==MouseEvent.BUTTON1 &&
				(evt.getModifiersEx() & ActionEvent.SHIFT_MASK)==
				ActionEvent.SHIFT_MASK)) {
			if (!mode) { // for aims
				if (parent.lock[vertNum]) { // rad is locked
					CirclePack.cpb.msg("Unlock radius to reset aim.");
					return;
				}
				int y = evt.getPoint().y;
				double newAim = (double) (bar_length - y) / factor;
				parent.setValue(newAim, vertNum, mode);
				placePointer(newAim);
				parent.displayAngError();
			} else { // for radii, this means to toggle lock state and color
				if (parent.lock[vertNum]) {
					parent.lock[vertNum] = false;
					barArea.setBackground(Color.blue);
				} else {
					parent.lock[vertNum] = true;
					barArea.setBackground(Color.magenta); // new
															// Color(220,220,255));
				}
			}
		}
		
		else if (evt.getButton() == MouseEvent.BUTTON1) {
			int y = evt.getPoint().y;
			double value;
			if (!mode) { // on left button, only allow 2*Pi or 4*Pi.
				if (parent.lock[vertNum]) { // rad is locked
					return;
				}
				value = (double) (bar_length - y) / factor;
				if (value > 3 * Math.PI)
					value = 4.0 * Math.PI;
				else
					value = 2.0 * Math.PI;
				parent.packData.rData[vertNum].aim=value;
				placePointer(value);
			} 
			else { // for radii
				// convert (reverse) to log factor
				double rbl = (double) SphWidget.RAD_BAR_HEIGHT;
				value = (double) (y * SphWidget.RAD_BAR_POWER * Math.log(2.0))
						/ rbl;
				// System.err.println("y = "+y+" and value =
				// "+String.format("%.8e",value));
				value = Math.PI * (1.0 / Math.exp(value));

				if (parent.lock[vertNum]) { // rad is locked
					CirclePack.cpb.msg("Vert "+vertNum+" is locked");
					return;
				}
				parent.setValue(value, vertNum, mode);
				setBarHeight(value);
				parent.displayArea();
			}
			parent.displayAngError();
		}

	}

	public void mouseEntered(MouseEvent evt) {
		parent.lightupFlower(vertNum);
	}

	// rest of needed mouse calls
	public void mouseReleased(MouseEvent evt) {
	}

	public void mousePressed(MouseEvent evt) {
	}

	public void mouseExited(MouseEvent evt) {
	}

	public void mouseDragged(MouseEvent evt) {
		mouseClicked(new MouseEvent(evt.getComponent(), 0, 0, 0, evt.getX(),
				evt.getY(), 1, false, MouseEvent.BUTTON1));
	}

	public void mouseMoved(MouseEvent evt) {
	}

}
