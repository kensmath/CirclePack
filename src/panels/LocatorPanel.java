package panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import util.MathUtil;
import canvasses.MainFrame;

import complex.Complex;

/**
 * Locator Panel displays the (x,y) or (theta,phi) coordinates
 * of a cursor location.
 * @author kens
 *
 */
public class LocatorPanel extends JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	public JTextField xField; // x or theta
	public JTextField yField; // y or phi
	public JTextField what; // description
	
	public static int locatorWidth=78;
	public static int locatorHeight=60;

	public LocatorPanel() {
		super();
		setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		Dimension dim=new Dimension(locatorWidth,20);
		
		// label
		what=new JTextField("  (x,y) ");
		what.setToolTipText("Show coords of cursor: (x,y) or (theta,phi)");
//		what.setBorder(new EmptyBorder(0,0,0,0));
	    what.setBackground(new Color(180,180,255));
        what.setAlignmentX(Component.CENTER_ALIGNMENT);
        what.setMaximumSize(dim);
        what.setPreferredSize(dim);
		add(what);
		
		xField = new JTextField("x=");
		xField.setBorder(new EmptyBorder(0,0,0,0));
		xField.setToolTipText("x coord of mouse (theta, in spherical geom)");
	    xField.setFont(new Font(xField.getFont().toString(),Font.ROMAN_BASELINE,12));
//	    xField.setBackground(new Color(180,180,255));
        xField.setAlignmentX(Component.CENTER_ALIGNMENT);
        xField.setMaximumSize(dim);
        xField.setPreferredSize(dim);
        add(xField);

		yField = new JTextField("y=");
		yField.setBorder(new EmptyBorder(0,0,0,0));
		yField.setToolTipText("y coord of mouse (phi, in spherical geom)");
	    yField.setFont(new Font(yField.getFont().toString(),Font.ROMAN_BASELINE,12));
//	    yField.setBackground(new Color(180,180,255));
        yField.setAlignmentX(Component.CENTER_ALIGNMENT);
        yField.setMaximumSize(dim);
        yField.setPreferredSize(dim);
        add(yField);
        
        setPreferredSize(new Dimension(MainFrame.scriptWidth-2,locatorHeight));
	}
	
	/**
	 * Update the 
	 * @param hes, hes>0, spherical
	 * @param z, x,y or theta, phi
	 */
	public void upDate(int hes,Complex z) {
		if (hes<=0) { // eucl, hyp
			what.setText("  (x,y) ");
//			what.setText(" (x,y) coords ");
			xField.setText("x="+MathUtil.d2String(z.x));
			yField.setText("y="+MathUtil.d2String(z.y));
		}
		else {
			what.setText(" (theta,phi) ");
			xField.setText("t="+MathUtil.d2String(z.x));
			yField.setText("p="+MathUtil.d2String(z.y));
		}
	}

}
