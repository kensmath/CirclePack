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
 * IntegerField is a panel-contained text field for displaying integer
 * values and optionally includes a text label title. It is intended as a
 * re-sizable replacement for the <code>intNumField</code> class.
 * 
 * @author kens
 * @author Alex Fawkes
 *
 */
public class IntegerField extends JPanel {
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
	private static final long serialVersionUID = 6873576610896669987L;
	
	protected JTextField integerField;
	protected String title;
	protected boolean titled; // Whether or not this instance has a title.

	/**
	 * Create a non-titled instance.
	 */
	public IntegerField() {
		this(null);
	}

	/**
	 * Create a titled instance.
	 * 
	 * @param title the title of the instance; <code>null</code> for no title
	 */
	public IntegerField(String title) {
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
		
		integerField = new JTextField();
		integerField.setAlignmentX(Box.LEFT_ALIGNMENT);
		// Let the field grow horizontally without bounds, but cap the height. We don't want a tall field.
		integerField.setMaximumSize(new Dimension(integerField.getMaximumSize().width, integerField.getPreferredSize().height));
		this.add(integerField);
	}

	/** 
	 * Set the integer value to be displayed by this instance.
	 * 
	 * @param value the new <code>int</code> value
	 */
	public void setValue(int value) {
		integerField.setText(Integer.toString(value));
	}
	
	/**
	 * Clear the associated text field.
	 */
	public void clear() {
		integerField.setText(null);
	}
	
	/**
	 * Get the integer value currently displayed by this instance; any 
	 * variables are interpreted.
	 * 
	 * @return the <code>int</code> 
	 */
	public int getValue() {
		return Integer.parseInt(StringUtil.varSub(integerField.getText()));
	}

	/**
	 * Returns the uninterpreted string
	 * @return String
	 */
	public String getText() {
		return integerField.getText();
	}
	
	/**
	 * Set whether or not the associated text field is editable.
	 * 
	 * @param editable whether or not the text field is editable
	 */
	public void setEditable(boolean editable) {
		integerField.setEditable(editable);
	}

	/**
	 * Determine whether or not the associated text field is editable.
	 * 
	 * @return a <code>boolean</code> value representing whether or not the text field is editable
	 */
	public boolean isEditable() {
		return integerField.isEditable();
	}
	
	/**
	 * Set the action command of the associated text field.
	 * 
	 * @param actionCommand the action command to set
	 */
	public void setActionCommand(String actionCommand) {
		integerField.setActionCommand(actionCommand);
	}
	
	/**
	 * Add an action listener to the associated text field.
	 * 
	 * @param actionListener the ActionListener to add
	 */
	public void addActionListener(ActionListener actionListener) {
		integerField.addActionListener(actionListener);
	}
}
