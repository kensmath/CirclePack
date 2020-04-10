package panels;

import java.util.Iterator;
import java.util.LinkedHashMap;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import allMains.CPBase;
import circlePack.PackControl;
import exceptions.DataException;
import exceptions.ParserException;

/**
 * SliderControlPanel contains 'SliderPanel's used to manipulate 
 * CirclePack variables via sliders and an area for adding new
 * sliders.
 *  
 * @author Ken Stephenson, copied from 'VariableControlPanel' by Alex Fawkes
 *
 */
public class SliderControlPanel extends JPanel {
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

	protected JPanel sliderStack;    // panel containing all the 'sliderPanel's
	protected JTextField nameEntry;  // for adding new variables
	protected JButton addButton;
	
	// convenience local pointers to the hashmaps
	LinkedHashMap<String, String> variables;
	LinkedHashMap<String, SliderPanel> sliderVariables;
	
	public SliderControlPanel() {
		super();
		variables=CPBase.varControl.variables;
		sliderVariables=CPBase.varControl.sliderVariables;
		
		// Create stack, put in scroll in case we have many variables.
		sliderStack=new JPanel();
		sliderStack.setLayout(new BoxLayout(sliderStack,BoxLayout.PAGE_AXIS));
		JScrollPane sliderScroller = new JScrollPane(sliderStack);
		sliderScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sliderScroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		// Add everything to this panel.
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		this.add(sliderScroller);
	}
	
	public int getSliderCount() {
		return sliderVariables.size(); // One sliderPanel for each variable entry.
	}

	/**
	 * Returns the value of the specified CirclePack slider variable 
	 * 
	 * @param key the name of the CirclePack variable
	 * @return a <code>String</code> representation of the value or <code>null</code> if not found
	 */
	protected String getSliderValue(String key) {
		JPanel sliderPanel=sliderVariables.get(key);
		if (sliderPanel!=null) {
				// TODO: find and query the appropriate slider for current value
			String value="";
			return value;
		}
		return null;
	}
		
	/**
	 * Adds a variable slider to 'sliderVariables'. It should already have
	 * created a 'variables' entry. This should only be called if 'value'
	 * starts with "[SLIDER..] ". If value it is not a double, then exception.
	 * 
	 * @param key String, name of CirclePack variable
	 * @param specs String, string following 'SLIDER' in brackets.
	 * @param value String, contains the double value (should be checked)
	 * @return String representation of the variable's double value 
	 */
	public String putSlider(String key,String specs,String value) {
		key = key.trim(); // Remove leading and trailing whitespace.
		double doubleVal=0.0;
		try {
			doubleVal=Double.valueOf(value);
		} catch (Exception ex) {
			throw new DataException("slider attempt failed: can't convert '"+value+"' to double");
		}
		SliderPanel sliderPanel=null;
		try {
			sliderPanel=new SliderPanel(key,specs,Double.toString(doubleVal));
		} catch (Exception ex) {
			throw new ParserException("failed to parse SLIDER specs: "+ex.getMessage());
		}
		sliderVariables.put(key, sliderPanel);
		sliderStack.add(sliderPanel);
		PackControl.packDataHover.sliderControlPanel.revalidate();
		return sliderPanel.toString(); // string version of value
	}
		
	/**
	 * Revert a slider to a regular variable with latest value (can
	 * be removed in separate operation).
	 * 
	 * TODO: not yet implemented
	 * 
	 * @param key the name of the CirclePack variable to revert
	 * @return a <code>String</code> representation of the value removed by the key, or <code>null</code> if nothing removed
	 */
	protected String revertSlider(String key) {
		SliderPanel returnValue = sliderVariables.remove(key);
		return returnValue.toString();
	}

	public void removeSliderPanel(SliderPanel sp) {
		sliderStack.remove(sp);
		sp=null; // sliderStack.repaint();
		PackControl.packDataHover.sliderControlPanel.revalidate();
	}
	
	/**
	 * Go through 'sliderVariables' and put their panels in the sliderStack panel.
	 * @return count
	 */
	public int resetStack() {
		int count=0;
		sliderStack.removeAll();
		Iterator<String> sPanels=sliderVariables.keySet().iterator();
		while (sPanels.hasNext()) {
			SliderPanel sp=sliderVariables.get(sPanels.next());
			sliderStack.add(sp);
			count++;
		}
		return count;
	}
	
	
}
