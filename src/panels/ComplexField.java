package panels;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import complex.Complex;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

public class ComplexField extends JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	private static String defaultValue = "1.";
	private static String defaultLabel = "Enter complex number:";
	private static int defaultSize = 12;
	private double accuracy = 1E-3;
	private int fieldSize;
	public JTextField tf = new JTextField();
	private JLabel l = new JLabel();
	private static parser.Parser parser = new parser.Parser();
	private Complex value;
	private boolean directSet = true;

	// Constructors

	public ComplexField() {
		try {
			jbInit();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public ComplexField(String value) {
		try {
			jbInit();
			tf.setText(value);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public ComplexField(String value, String label) {
		try {
			jbInit();
			tf.setText(value);
			l.setText(label);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void jbInit() {
		l.setText(defaultLabel);
		tf.setText(defaultValue);
		tf.setColumns(defaultSize);
		this.add(l);
		this.add(tf);
	}

	public String getString() {
		return tf.getText();
	}

	public void setString(String s) {
		directSet = false;
		tf.setText(s);
	}

	public complex.Complex getValue() {
		if (!directSet || (value == null)) {
			parser.setExpression(tf.getText());
			return parser.evaluate();
		} else
			return value;
	}

	public void setValue(double v) {
		value = new Complex(v);
		directSet = true;
		tf.setText(util.MathUtil.d2String(v, accuracy));
	}

	public void setValue(complex.Complex c) {
		value = c;
		directSet = true;
		tf.setText(c.toString());
	}

	public String getLabel() {
		return l.getText();
	}

	public void setLabel(String s) {
		l.setText(s);
	}

	public static String getDefaultLabel() {
		return defaultLabel;
	}

	public static void setDefaultLabel(String s) {
		defaultLabel = s;
	}

	public static String getDefaultValue() {
		return defaultValue;
	}

	public static void setDefaultValue(String s) {
		defaultValue = s;
	}

	public static int getDefaultSize() {
		return defaultSize;
	}

	public static void setDefaultSize(int n) {
		defaultSize = n;
	}

	public int getFieldSize() {
		return fieldSize;
	}

	public void setFieldSize(int s) {
		fieldSize = s;
		tf.setColumns(s);
	}

//	private void setAccuracy(double ac) {
//		accuracy = ac;
//	}

//	private double getAccuracy() {
//		return accuracy;
//	}
	
}
