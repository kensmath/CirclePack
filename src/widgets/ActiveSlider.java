package widgets;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;

import util.xNumField;

/**
 * Part of the structure for general purpose bar graphs.
 * This JPanel contains a vertex label, a bar, and optionally an icon. 
 * Bar height, color, perhaps icon placement are controlled from outside. 
 * The bar may be adjustable via the mouse. 
 */
public class ActiveSlider extends JPanel implements MouseListener,
		MouseMotionListener {

	private static final long 
	serialVersionUID = 1L;

	static final int SLIDER_WIDTH=150;
	static final int MIN_VALUE=0;
	static final int MAX_VALUE=1000;

	SliderFrame parent;   
	int index;       // index in 'mySliders' of object for this bar (e.g., vert index)
	
	String label;    // string describing the object: v, v w, f
	double value;    // current double value

	boolean active;  // true if mouse movement over slider triggers action
	
	// components
	JPanel sliderArea;        // panel slider is in
	IndexedJSlider slider;    // slider itself (with index)
	JTextField labelField;    // holds label
	xNumField valueField;     // holds value

	// Constructor (default)
	public ActiveSlider(SliderFrame slf, int indx, String lbl,double val,boolean actv) {
		setOpaque(false);
		setLayout(null);
		parent = slf;
		index=indx;
		value=val;
		active=actv;
		
		slider=new IndexedJSlider(MIN_VALUE,MAX_VALUE,index);
		slider.addChangeListener(parent.listener);
		labelField=new JTextField(label,8);
		valueField=new xNumField(String.format("%.6f",value),8);
		setValue(value);
		
		if (active)
			addMouseMotionListener(this);

	}
	
	public double getValue() {
		double f=(double)slider.getValue()/(double)MAX_VALUE;
		return (f*(parent.val_max-parent.val_min)+parent.val_min);
	}
	
	/**
	 * JSlider.setValue takes an integer, so convert. Set slider
	 * and value field
	 * @param val
	 */
	public void setValue(double val) {
		double x=val;
		x=(val<parent.val_min) ? parent.val_min : x;
		x=(val>parent.val_max) ? parent.val_max : x; 
		slider.setValue((int)((val-parent.val_min)/(parent.val_max-parent.val_min)));
		valueField.setField(val);
	}

	/**
	 * Handle a slider change event
	 * @param event
	 */
	public void changeReaction(ChangeEvent event) {
		JSlider source=null;
		try {
			source=(JSlider)event.getSource();
		} catch (Exception ex) {
			return;
		}
		if (!source.getValueIsAdjusting()) {  // is this last in a chain of mouse actions?
			setValue(source.getValue());
		}
	}
	
	/**
	 * Notify parent when mouse enters this slider's zone
	 */
	public void mouseEntered(MouseEvent evt) {
		parent.mouse_entry_action(index);
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
	}

	public void mouseClicked(MouseEvent arg0) {
	}

}
