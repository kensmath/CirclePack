package widgets;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;

import util.xNumField;

/**
 * Part of the structure for general purpose groups of sliders.
 * This JPanel contains an object label, a slider, and a value field.
 */
public class ActiveSlider extends JPanel implements MouseListener,
		MouseMotionListener {

	private static final long 
	serialVersionUID = 1L;

	static final int SLIDER_WIDTH=150;

	public SliderFrame sfparent;   
	int index;       // index in 'mySliders' of object for this bar (e.g., vert index)
	
	String label;    // string describing the object: v, v w, f
	double value;    // current value is kept here only

	boolean active;  // true if mouse movement over slider triggers action
	
	// components
	JPanel sliderArea;        // panel slider is in
	IndexedJSlider slider;    // slider itself (with index)
	JTextField labelField;    // holds label
	xNumField valueField;     // holds value

	// Constructor (default)
	public ActiveSlider(SliderFrame sfp, int indx, String lbl,double val,boolean actv) {
		setBorder(BorderFactory.createLineBorder(Color.blue));
		setLayout(new FlowLayout(FlowLayout.LEADING));
		sfparent = (SliderFrame)sfp;
		index=indx;
		label=lbl;
		value=val;
		active=actv;
		
		slider=new IndexedJSlider(sfparent,val,index);
		slider.addChangeListener(sfparent.listener);
		labelField=new JTextField(label,6);
		labelField.setEditable(false);
		valueField=new xNumField("",8);
		valueField.setValue(val);
		
		add(labelField);
		add(slider);
		add(valueField);
		
		if (active) {
			addMouseListener(this);
			addMouseMotionListener(this);
		}
	}
	
	/**
	 * Read the value from the slider
	 * @return double
	 */
	public double getValue() {
		value=slider.getCurrentValue();
		return value;
	}
	
	/**
	 * Just refresh the slider location, don't trigger 
	 * chgCmd; e.g., when min or max changes
	 */
	public void refreshValue() {
		boolean holdck=sfparent.changeCheck.isSelected();
		sfparent.changeCheck.setSelected(false);
		slider.setMyValue(value);
		sfparent.changeCheck.setSelected(holdck);
	}
	
	/**
	 * set value for slider 
	 * @param val
	 */
	public void setValue(double val) {
		value=val;
		slider.setMyValue(val);  // set slider
		valueField.setValue(val); // set field
	}
	
	public String getLabel() {
		return label;
	}
	
	public void setIndex(int newIndx) {
		index=newIndx;
	}
	
	/**
	 * Handle a slider change event
	 * @param event
	 */
	public void changeReaction() {
		double val=slider.getCurrentValue();
		setValue(val);
		sfparent.upValue(index); // change PackData after 'this.value' is set 
		sfparent.changeAction(index); // there may be commands to execute
	}
	
	/**
	 * Notify parent when mouse enters this slider's zone
	 */
	public void mouseEntered(MouseEvent evt) {
		sfparent.mouse_entry_action(index);
	}

	// rest of needed mouse calls
	public void mouseReleased(MouseEvent evt) {
	}

	public void mousePressed(MouseEvent evt) {
	}

	public void mouseExited(MouseEvent evt) {
	}
	
	public void mouseDragged(MouseEvent evt) {
	}

	public void mouseMoved(MouseEvent evt) {
//		sfparent.mouse_entry_action(index);
	}

	public void mouseClicked(MouseEvent arg0) {
	}

}
