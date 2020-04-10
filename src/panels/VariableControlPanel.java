package panels;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Scanner;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import allMains.CPBase;
import allMains.CirclePack;

/**
 * VariableControlPanel is a panel for manipulating CirclePack variables. It displays
 * the current state of CirclePack variables in a table and provides an interface to add
 * and remove variables.
 * 
 * @author Alex Fawkes
 *
 */
public class VariableControlPanel extends JPanel {
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
	private static final long serialVersionUID = 5199506647863934696L;
	
	protected JTable variableTable;
	protected JTextField nameEntry;
	protected JTextField valueEntry;
	protected JButton addButton;

	// TODO: Add support for editing variables in place through the table. 
	public VariableControlPanel() {
		super();

		JButton removeButton = new JButton("Remove");
		removeButton.setFont(removeButton.getFont().deriveFont((float) removeButton.getFont().getSize() - 1.0F));
		removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				int selectedRowIndex = variableTable.getSelectedRow();
				if (selectedRowIndex == -1) return; // Nothing selected.

				// Remove the variable.
				CPBase.varControl.removeVariable((String) variableTable.getValueAt(selectedRowIndex, 0));
			}
		});

		addButton = new JButton("Add");
		addButton.setFont(addButton.getFont().deriveFont((float) addButton.getFont().getSize() - 1.0F));
		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				String nameText = null;
				String valueText = null;
				try {
					// These may throw NullPointerExceptions if the underlying text is null.
					nameText = nameEntry.getText().trim();
					valueText = valueEntry.getText().trim();
				} catch (NullPointerException npe) {return;}

				// There must be text at this point, though it may be empty.
				if (nameText.equals("") || valueText.equals("")) return;

				// We have a name string and a value string. Construct a Vector<Vector<String>> for VarControl.
				Scanner scanner = new Scanner(valueText);
				Vector<String> valueSubVector = new Vector<String>();
				while (scanner.hasNext()) valueSubVector.add(scanner.next());
				scanner.close();
				
				if (valueSubVector.size() == 0) return; // Probably got some whitespace.
				
				Vector<Vector<String>> valueVector = new Vector<Vector<String>>();
				valueVector.add(valueSubVector);

				// Clear the text fields and refocus on the name field.
				nameEntry.setText(null);
				valueEntry.setText(null);
				nameEntry.requestFocusInWindow();

				// Add the variable.
				// TODO: In retrospect, it would be nice to divorce this from VarControl - somehow, VarControl
				// and the table should share some sort of strictly-separated model and both operate on the model
				// through some simple methods. But this works.
				CPBase.varControl.putVariable(CirclePack.cpb.getActivePackData(), nameText, valueVector);
			}
		});

		// These respond to enter key presses.
		final JButton addButtonReference = addButton;
		KeyAdapter enterListener = new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					addButtonReference.doClick();
				}
			}
		};
		nameEntry = new JTextField();
		nameEntry.addKeyListener(enterListener);
		valueEntry = new JTextField();
		valueEntry.addKeyListener(enterListener);

		// This is the add and remove control panel below the table.
		JPanel addRemovePanel = new JPanel();
		addRemovePanel.setLayout(new BoxLayout(addRemovePanel, BoxLayout.LINE_AXIS));
		addRemovePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		addRemovePanel.add(removeButton);
		addRemovePanel.add(Box.createHorizontalStrut(6));
		addRemovePanel.add(new JSeparator(SwingConstants.VERTICAL));
		addRemovePanel.add(Box.createHorizontalStrut(6));
		addRemovePanel.add(nameEntry);
		addRemovePanel.add(Box.createHorizontalStrut(2));
		addRemovePanel.add(valueEntry);
		addRemovePanel.add(Box.createHorizontalStrut(2));
		addRemovePanel.add(addButton);
		addRemovePanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

		// Get the table model from the variable control instance and assign it to our table.
		// Any changes made to this underlying model will be reflected dynamically in our table.
		variableTable = new JTable(CPBase.varControl.getVarTableModel());
		variableTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Put the table in a scroll pane in case we have many variables.
		JScrollPane tableScroller = new JScrollPane(variableTable);
		tableScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		tableScroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		// Add everything to this panel.
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		this.add(tableScroller);
		this.add(addRemovePanel);
	}
}
