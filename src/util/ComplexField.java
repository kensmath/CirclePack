package util;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import complex.Complex;

/**
 * ComplexField is a panel-contained text field for displaying complex
 * values and optionally includes a text label title. It is intended
 * as a re-sizable replacement for the <code>zNumField</code> class.
 * 
 * @author kens
 * @author Alex Fawkes
 *
 */
public class ComplexField extends JPanel {
	/*
	 * Regenerate serialVersionUID whenever the nature of this class's fields change
	 * so that this class may be flattened. As background, serialization provides a
	 * unified interface for writing and reading an instance's current state to and
	 * from the file system. The value of serialVersionUID is used to ensure that an
	 * instance state being read from the file system is compatible.
	 * 
	 * Note that we are sub-classing a class that implements serialization (JPanel),
	 * so we must respect that in our implementation.
	 */
	private static final long serialVersionUID = -6931936483682273094L;
	
	protected JTextField realField;
	protected JTextField imaginaryField;
	protected String title;
	protected boolean titled; // Whether or not this instance has a title.
	
	/**
	 * Create a non-titled instance.
	 */
	public ComplexField() {
		this(null);
	}
	
	/**
	 * Create a titled instance.
	 * 
	 * @param title the title of the instance; <code>null</code> for no title
	 */
	public ComplexField(String title) {
		if (title == null) {
			titled = false;
		} else {
			titled = true;
			this.title = title;
			createGUI();
		}
	}
	
	/**
	 * Lay out the interface. The title is displayed as a label over the text
	 * field below.
	 */
	protected void createGUI() {	
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		
		if (titled) {
			JLabel titleLabel = new JLabel(title);
			titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 10.0F));
			// Both components must be left aligned. They align with respect to each other.
			titleLabel.setAlignmentX(Box.LEFT_ALIGNMENT);
			this.add(titleLabel);
		}
		
		realField = new JTextField();
		imaginaryField = new JTextField();
		// Let the fields grow horizontally without bounds, but cap the heights. We don't want tall fields.
		realField.setMaximumSize(new Dimension(realField.getMaximumSize().width, realField.getPreferredSize().height));
		imaginaryField.setMaximumSize(new Dimension(imaginaryField.getMaximumSize().width, imaginaryField.getPreferredSize().height));
		
		JPanel complexRowPanel = new JPanel();
		complexRowPanel.setLayout(new BoxLayout(complexRowPanel, BoxLayout.LINE_AXIS));
		complexRowPanel.setAlignmentX(Box.LEFT_ALIGNMENT);
		complexRowPanel.add(realField);
		complexRowPanel.add(imaginaryField);
		
		this.add(complexRowPanel);
	}

	/**
	 * Empty the field so it is just blank
	 */
	public void setEmpty() {
		realField.setText("");
		imaginaryField.setText("");
	}
	
	/** 
	 * Set the complex value to be displayed by this instance.
	 * 
	 * @param value the new <code>Complex</code> value
	 */
	public void setValue(Complex value) {
		// Format in scientific notation with 8 decimal places.
		realField.setText(String.format("%." + 8 + "e", value.x));
		imaginaryField.setText(String.format("%." + 8 + "e", value.y));
	}
	
	/**
	 * Get the complex value currently displayed by this instance, with
	 * any variables interpreted.
	 * 
	 * @return the <code>Complex</code>
	 */
	public Complex getValue() {
		return new Complex(Double.parseDouble(StringUtil.varSub(realField.getText())), 
				Double.parseDouble(StringUtil.varSub(imaginaryField.getText())));
	}
	
	/**
	 * Return the uninterpreted 'real' text
	 * @return String
	 */
	public String getTextReal() {
		return realField.getText();
	}

	/**
	 * Return the uninterpreted 'real' text
	 * @return String
	 */
	public String getTextImag() {
		return imaginaryField.getText();
	}
	
	/**
	 * Set whether or not the associated text fields are editable.
	 * 
	 * @param editable whether or not the text fields are editable
	 */
	public void setEditable(boolean editable) {
		realField.setEditable(editable);
		imaginaryField.setEditable(editable);
	}

	/**
	 * Determine whether or not the associated text fields are editable.
	 * 
	 * @return a <code>boolean</code> value representing whether or not both text fields are editable
	 */
	public boolean isEditable() {
		return realField.isEditable() && imaginaryField.isEditable();
	}
	
	/**
	 * Set the action command of the associated text fields.
	 * 
	 * @param actionCommand the action command to set
	 */
	public void setActionCommand(String actionCommand) {
		realField.setActionCommand(actionCommand);
		imaginaryField.setActionCommand(actionCommand);
	}
	
	/**
	 * Add an action listener to the associated text fields.
	 * 
	 * @param actionListener the ActionListener to add
	 */
	public void addActionListener(ActionListener actionListener) {
		realField.addActionListener(actionListener);
		imaginaryField.addActionListener(actionListener);
	}
}
