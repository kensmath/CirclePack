package widgets;

import javax.swing.JSlider;

/**
 * This extension of JSLider holds an index and uses doubles for
 * min, max, and value. 
 * ken, 6/2020
 */
public class IndexedJSlider extends JSlider {
	
	private static final long serialVersionUID = 1L;
	static final int MIN_VALUE=0;
	static final int MAX_VALUE=1000;
	
	int myIndx; // holds the user-rescribed index 
	double myMin; 
	double myMax;
	
	public IndexedJSlider(double min, double max,int indx) {
		super(MIN_VALUE,MAX_VALUE);
		myMin=min;
		myMax=max;
		myIndx=indx;
	}
	
	public IndexedJSlider(double min,double max,double val, int indx) {
		super(MIN_VALUE,MAX_VALUE);
		myMin=min;
		myMax=max;
		myIndx=indx;
		setMyValue(val);
		this.fireStateChanged();
	}
	
	public int getIndex() {
		return myIndx;
	}
	
	public double getCurrentValue() {
		double f=((double)getValue())/(double)MAX_VALUE;
		return (f*(myMax-myMin)+myMin);
	}
	
	public int convertDouble(double x) {
		if (x<=myMin) return MIN_VALUE;
		if (x>=myMax) return MAX_VALUE;
		return (int)((x-myMin)/(myMax-myMin)*(MAX_VALUE-MIN_VALUE))+MIN_VALUE;
	}
	
	public void setMyValue(double x) {
		setValue(convertDouble(x));
	}
}
