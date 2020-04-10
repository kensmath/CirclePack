package cpContributed;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.Date;
import javax.swing.JScrollBar;

/** 
 * AFVocalAdjustmentListener is an adjustment listener used to debug JScrollBar
 * behavior. Each time an AdjustmentEvent is fired (i.e., the JScrollBar changes),
 * this listener will output status information for the JScrollBar and the
 * AdjustmentEvent.
 * 
 * Usage:
 *     JScrollPane jScrollPane = new JScrollPane();
 *     JScrollBar jScrollBar = jScrollPane.getVerticalScrollBar();
 *     AFVocalAdjustmentListener afVocalAdjustmentListener = new afVocalAdjustmentListener();
 *     jScrollBar.addAdjustmentListener(afVocalAdjustmentListener);
 *     afVocalAdjustmentListener.setJScrollBar(jScrollBar);
 *     
 * @author Alex Fawkes
 */
public class AFVocalAdjustmentListener implements AdjustmentListener {
	private JScrollBar jScrollBar = null;
	
	public void adjustmentValueChanged(AdjustmentEvent ae) {
		// Output block header.
		System.out.println("ADJUSTMENT EVENT:");
		
		// Output current time from epoch in milliseconds.
		System.out.println("\tTime:\t\t\t\t" + (new Date()).getTime());
		
		// Output name of current thread.
		System.out.println("\tThread:\t\t\t\t" + Thread.currentThread().getName());
		
		// Figure out what type of AdjustmentEvent this is, and output its name.
		int adjustmentTypeInt = ae.getAdjustmentType();
		String adjustmentTypeString;
		switch (adjustmentTypeInt) {
		case AdjustmentEvent.UNIT_INCREMENT:
			adjustmentTypeString = "UNIT_INCREMENT";
			break;
		case AdjustmentEvent.UNIT_DECREMENT:
			adjustmentTypeString = "UNIT_DECREMENT";
			break;
		case AdjustmentEvent.BLOCK_INCREMENT:
			adjustmentTypeString = "BLOCK_INCREMENT";
			break; 
		case AdjustmentEvent.BLOCK_DECREMENT:
			adjustmentTypeString = "BLOCK_DECREMENT";
			break;
		case AdjustmentEvent.TRACK:
			adjustmentTypeString = "TRACK";
			break;
		default:
			adjustmentTypeString = "UNKNOWN";
			break;
		}
		System.out.println("\tAdjustment Type:\t\t" + adjustmentTypeString);
		
		// Output the value of the adjustment event.
		// Commented out; duplicate of jScrollBar.getValue()
		//System.out.println("\tAdjustment Value:\t\t" + ae.getValue());
		
		// If a JScrollBar is set:
		if (jScrollBar != null) {
			// Output its current value.
			System.out.println("\tJScrollBar Current Value:\t" + jScrollBar.getValue());
			
			// Output its maximum value.
			System.out.println("\tJScrollBar Maximum Value:\t" + jScrollBar.getMaximum());
		}
		
		// Output true if additional adjustments are queued, false otherwise.
		System.out.println("\tValue Is Adjusting:\t\t" + ae.getValueIsAdjusting());
		
		// Output a unique hash code for the event.
		System.out.println("\tHash Code:\t\t\t" + ae.hashCode());
		
		// Additional event information.
		//System.out.println("ID:\t\t\t\t" + ae.getID());
		//System.out.println("Adjustable:\t\t\t" + ae.getAdjustable());
		//System.out.println("Param String:\t\t\t" + ae.paramString());
		//System.out.println("String:\t\t\t\t" + ae.toString());
		//System.out.println("Source:\t\t\t\t" + ae.getSource());
		//System.out.println("Class:\t\t\t\t" + ae.getClass());
	}
	
	// Bind a JScrollBar to the listener to output additional status information.
	public void setJScrollBar(JScrollBar jScrollBar) {
		this.jScrollBar = jScrollBar;
	}
}
