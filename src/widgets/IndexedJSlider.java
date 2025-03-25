package widgets;

import javax.swing.JSlider;

/**
 * This extension of JSlider holds an index and uses 
 * doubles for min, max, and value. 
 * ken, 6/2020
 */
public class IndexedJSlider extends JSlider {
	
	private static final long serialVersionUID = 1L;
	static final int MIN_VALUE=0;
	static final int MAX_VALUE=1000;
	
	int myIndx; // holds the user-prescribed index 
	SliderFrame sfparent; // slider frame parent
	
	public IndexedJSlider(SliderFrame sfp,int indx) {
		super(MIN_VALUE,MAX_VALUE);
		sfparent=sfp;
		myIndx=indx;
	}
	
	public IndexedJSlider(SliderFrame sfp,double val, int indx) {
		super(MIN_VALUE,MAX_VALUE);
		sfparent=sfp;
		myIndx=indx;
		setMyValue(val);
		this.fireStateChanged();
	}

	public int getIndex() {
		return myIndx;
	}
	
	public double getCurrentValue() {
		double f=((double)getValue())/(double)MAX_VALUE;
		return (f*(sfparent.val_max-sfparent.val_min)+sfparent.val_min);
	}
	
	/**
	 * Have to convert to integer, but restrict to slider range
	 * @param x double
	 * @return int
	 */
	public int convertDouble(double x) {
		if (x<=sfparent.val_min) return MIN_VALUE;
		if (x>=sfparent.val_max) return MAX_VALUE;
		return (int)((x-sfparent.val_min)/
				(sfparent.val_max-sfparent.val_min)*
				((double)(MAX_VALUE-MIN_VALUE)))+MIN_VALUE;
	}
	
	/**
	 * For JSlider, have to convert value to integer
	 * @param x double
	 */
	public void setMyValue(double x) {
		setValue(convertDouble(x)); 
	}
}
