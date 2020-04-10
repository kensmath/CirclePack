package input;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

import allMains.CPBase;
import packing.PackData;

/**
 * Running threads for timed execution or delay loops of commands
 * @author kens
 *
 */
public class TimedExec {
	
	Timer runTimer;
	int counter;
	int end;
	String cmdString;
	PackData packData;
	int deep;
	
	public TimedExec(PackData p,int startN,int endN,double delay,String cmds) {
		packData=p;
		counter=startN;
		end=endN;
		if (cmds==null) cmdString=null;
		else cmdString=new String(cmds);
		if (end<=counter) end=counter; // do single iteration
		
		// create timer, ex every 'delay' seconds
	    try {
	    	runTimer = new Timer((int)(delay*1000), new ActionListener() {
		        public void actionPerformed(ActionEvent e) {
		        	try {
		        		if (counter>end || (cmdString!=null)) {
//		        			&& 
//		        				!TrafficCenter.parseWrapper(cmdString,
//		        						packData,false,false,0))) {
		        			counter=end+1; // kick out
			        		runTimer.stop();
			        		CPBase.runSpinner.startstop(false);
		        		}
		        		else counter++;
		        	} catch (Exception ex) {
		        		runTimer.stop();
		        		CPBase.runSpinner.startstop(false);
		        	}
		        }
		    });
	    	runTimer.start();
	    } catch (Exception ex) {
	    	runTimer.stop();
    		CPBase.runSpinner.startstop(false);
	    }	    	
	}
	
	public void stop() {
		if (runTimer!=null) runTimer.stop();
	}
	

	
}
