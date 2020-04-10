package util;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * RealField is a panel-contained text field for displaying real
 * values and optionally includes a text label title. It is intended
 * as a re-sizable replacement for the <code>xNumField</code> class.
 * 
 * @author kens
 * @author Alex Fawkes
 *
 */
public class RealField extends JPanel {
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
	private static final long serialVersionUID = 5668707914695592480L;
	
	public JTextField realField;
	protected String title;
	protected boolean titled; // Whether or not this instance has a title.

	/**
	 * Create a non-titled instance.
	 */
	public RealField() {
		this(null);
	}
	
	/**
	 * Create a titled instance.
	 * 
	 * @param title the title of the instance; <code>null</code> for no title
	 */
	public RealField(String title) {
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
		realField.setAlignmentX(Box.LEFT_ALIGNMENT);
		// Let the field grow horizontally without bounds, but cap the height. We don't want a tall field.
		realField.setMaximumSize(new Dimension(realField.getMaximumSize().width, realField.getPreferredSize().height));
		this.add(realField);
	}

	/**
	 * Empty the field so it is just blank
	 */
	public void setEmpty() {
		realField.setText("");
	}
	
	/** 
	 * Set the real value to be displayed by this instance.
	 * @param value the new <code>double</code> value
	 */
	public void setValue(double value) {
		// Format in scientific notation with 8 decimal places.
		realField.setText(String.format("%." + 8 + "e", value));
	}
	
	/**
	 * Get the real value currently displayed by this instance; variable '_{varname}'
	 * are interpreted first.
	 * 
	 * @return <code>double</code> 
	 */
	public double getValue() {
		return Double.parseDouble(StringUtil.varSub(realField.getText()));
	}
	
	/**
	 * Returns the uninterpreted string
	 * @return String
	 */
	public String getText() {
		return realField.getText();
	}
	
	/**
	 * Set whether or not the associated text field is editable.
	 * 
	 * @param editable whether or not the text field is editable
	 */
	public void setEditable(boolean editable) {
		realField.setEditable(editable);
	}

	/**
	 * Determine whether or not the associated text field is editable.
	 * 
	 * @return a <code>boolean</code> value representing whether or not the text field is editable
	 */
	public boolean isEditable() {
		return realField.isEditable();
	}
	
	/**
	 * Set the action command of the associated text field.
	 * 
	 * @param actionCommand the action command to set
	 */
	public void setActionCommand(String actionCommand) {
		realField.setActionCommand(actionCommand);
	}
	
	/**
	 * Add an action listener to the associated text field.
	 * 
	 * @param actionListener the ActionListener to add
	 */
	public void addActionListener(ActionListener actionListener) {
		realField.addActionListener(actionListener);
	}
}
