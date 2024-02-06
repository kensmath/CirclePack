package util;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A JTextField for entering/displaying formated real values. 
 * Can specify 3 to 15 digit length (default 8) and specify a
 * title that goes above the field. If no title is given, this
 * just serves to format a number.
 * @author kens
 */
public class xNumField extends JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	private JTextField xField;
	private boolean titled;
	private String title;
	private int width;
	private int digits; // number of digits

	public xNumField() {
		this("",8);
	}
	
	public xNumField(String ttle) {
		this(ttle,8);
	}
	
	public xNumField(String ttle,int dgts) {
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
			if (title.length()>16) title=title.substring(0,15);
			fieldName.setText(title);
		}
		
		// field for number
		xField = new JTextField(digits+7);
		xField.setText("");

		// estimate width of number
		width=8*(digits+4); 

		// find width of label, if titled
		if (titled) {
			int nw=8*title.length();
			width=(width<nw) ? nw : width;
			fieldName.setBounds(0,0,width,14);
			add(fieldName);
		}

		add(xField);
		
		if (titled) {
			xField.setBounds(0,15,width,16);
			setPreferredSize(new Dimension(width+2,32));
		}
		else {
			xField.setBounds(0,0,width,16);
			setPreferredSize(new Dimension(width+2,18));
		}
			
	}

	/**
	 * Enter double in scientific notation; if < e-14, set
	 * to "0" or "-0"; 
	 * @param x double
	 */
	public void setValue(double x) { // 
		if (x>0 && x<.00000000000001)
			xField.setText("0");
		else if (x<0 && x>-.00000000000001)
			xField.setText("-0");
		else
			xField.setText(String.format("%."+digits+"e",x));
	}
	
	public double getValue() {
		try {
			return Double.parseDouble(xField.getText());
		} catch (Exception ex) {
			return 1.0;
		}
	}
	
	/**
	 * @param ed boolean
	 */
	public void setEditable(boolean ed) {
		xField.setEditable(ed);
	}

	public void setActionCommand(String ac) {
		xField.setActionCommand(ac);
	}
	
	public void addActionListener(ActionListener al) {
		xField.addActionListener(al);
	}
}
