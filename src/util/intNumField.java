package util;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Utility panel with name and number field for integer quantities
 */

public class intNumField extends JPanel {

	private static final long 
	serialVersionUID = 1L;

	private JTextField intField;
	private boolean titled;
	private String title;
	private int width;
	private int charLength; // number of characters in field

	// Constructors
	public intNumField() {
		this("",10);
	}
	
	public intNumField(String title) {
		this(title,10);
	}

	public intNumField(String ttle,int charlen) {
		title=ttle.trim();
		titled=false;
		if (title.length()>0) titled=true;
		charLength=charlen;
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
		intField = new JTextField(charLength);
		intField.setText("");

		// find width of text (try '00000')
		StringBuilder str=new StringBuilder("00");
		for (int j=2;j<charLength;j++)
			str.append("0");
		width=6*charLength;


		// find width of label, if titled
		if (titled) {
			int nw=8*title.length();
			width=(width<nw) ? nw : width;
			fieldName.setBounds(0,0,width,14);
			add(fieldName);
		}

		add(intField);
		
		if (titled) {
			intField.setBounds(0,15,width,16);
			setPreferredSize(new Dimension(width+2,32));
		}
		else {
			intField.setBounds(0,0,width,16);
			setPreferredSize(new Dimension(width+2,18));
		}
			
	}

	/** 
	 * Enter an integer value
	 * @param n
	 */
	public void setField(int n) {
		intField.setText(Integer.toString(n));
	}
	
	/**
	 * Return the current integer value, with any variables
	 * interpreted.
	 * @return int, 0 on error
	 */
	public int getValue() {
		try {
			return Integer.parseInt(StringUtil.varSub(intField.getText()));
		} catch (Exception ex) {
			return 0;
		}
	}
	
	/**
	 * Return the uniterpreted text of the integer field
	 * @return String
	 */
	public String getText() {
		return intField.getText();
	}
	
	public void setEditable(boolean ed) {
		intField.setEditable(ed);
	}	

	public void setActionCommand(String ac) {
		intField.setActionCommand(ac);
	}
	
	public void addActionListener(ActionListener al) {
		intField.addActionListener(al);
	}
	
}
