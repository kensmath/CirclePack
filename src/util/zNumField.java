package util;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import complex.Complex;

/**
 * Utility panel with name and number field for complex number input
 * and display in scientific notation
 */
public class zNumField extends JPanel {

	private static final long 
	serialVersionUID = 1L;

	private JTextField xField;
	private JTextField yField;
	private boolean titled;
	private String title;
	private int width;
	private int digits; // number of digits

	// Constructors
	public zNumField() {
		this("",8);
	}
	
	public zNumField(String ttle) {
		this(ttle,8);
	}
	
	public zNumField(String ttle,int dgts) {
		title=ttle.trim();
		titled=false;
		if (title.length()>0) titled=true;
		if (dgts<3) dgts=3;
		if (dgts>15) dgts=15;
		digits=dgts;
		initGUI();
	}
	
	/**
	 * Layout in over/under form
	 */
	private void initGUI() {
		setLayout(null);
		JLabel fieldName=null;
		
		// titled?
		if (titled) {
			fieldName=new JLabel();
			fieldName.setFont(new Font("TrueType",Font.PLAIN,10));
			fieldName.setToolTipText(title);
			if (title.length()>20) title=title.substring(0,19);
			title.concat(" (x+iy)");
			fieldName.setText(title);
		}
		
		// fields for real/im parts
		xField = new JTextField(digits+7);
		xField.setText("");
		yField = new JTextField(digits+7);
		yField.setText("");

		// estimate width of each number
		width=8*(digits+7);

		// find width of label, if titled
		if (titled) {
			int nw=8*title.length();
			width=((2*width+5)<nw) ? nw : width;
			fieldName.setBounds(0,0,width,14);
			add(fieldName);
		}

		add(xField);
		add(yField);
		if (titled) {
			xField.setBounds(0,15,width,16);
			yField.setBounds(width+5,15,width,16);
			setPreferredSize(new Dimension(2*width+5+2,32));
		}
		else {
			xField.setBounds(0,0,width,16);
			yField.setBounds(width+5,0,width,16);
			setPreferredSize(new Dimension(width+2,18));
		}
			
	}

	/**
	 * Set parts to "0" or "-0" if too small
	 * @param z Complex
	 */
	public void setValue(complex.Complex z) {
		// real part
		if (z.x>0 && z.x<.00000000000001)
			xField.setText("0");
		else if (z.x<0 && z.x>-.00000000000001)
			xField.setText("-0");
		else 
			xField.setText(String.format("%."+digits+"e",z.x));
		if (z.y>0 && z.y<.00000000000001)
			yField.setText("0");
		else if (z.y<0 && z.y>-.00000000000001)
			yField.setText("-0");
		else 
			yField.setText(String.format("%."+digits+"e",z.y));
	}
	
	public Complex getValue() {
		try {
			return new Complex(Double.parseDouble(xField.getText()),Double.parseDouble(yField.getText()));
		} catch (Exception ex) {
			return null;
		}
	}
	
	public void setEditable(boolean ed) {
		xField.setEditable(ed);
		yField.setEditable(ed);
	}

	public void setActionCommand(String ac) {
		xField.setActionCommand(ac);
		yField.setActionCommand(ac);
	}
	
	public void addActionListener(ActionListener al) {
		xField.addActionListener(al);
		yField.addActionListener(al);
	}
}
