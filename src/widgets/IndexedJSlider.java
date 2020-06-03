package widgets;

import javax.swing.JSlider;

/**
 * 
 * @param indx
 */
public class IndexedJSlider extends JSlider {
	
	private static final long serialVersionUID = 1L;
	
	int myIndx; 
	
	public IndexedJSlider(int indx) {
		super();
		myIndx=indx;
	}
	
	public IndexedJSlider(int min,int max,int indx) {
		super(min,max);
		myIndx=indx;
	}
	
	public int getIndex() {
		return myIndx;
	}
}
