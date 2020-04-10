package images;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.Timer;

import allMains.CPBase;
import circlePack.PackControl;
import circlePack.RunProgress;

/**
 * This creates timer and owl/progress bar in several buttons.
 * @author kens
 *
 */
public class OwlSpinner extends RunProgress {

	// 'progressIcon' is 150x14, 'progressIconFat' is 78x20. 
	static ImageIcon progressIcon = 
		new ImageIcon(CPBase.getResourceURL("/Icons/main/progressBar.gif")); // OwlSpinner.gif")); 
	static ImageIcon progressIconFat=
		new ImageIcon(CPBase.getResourceURL("/Icons/main/progressBarFat.gif") );
	static ImageIcon owlBaseIcon = 
		new ImageIcon(CPBase.getResourceURL("/Icons/main/baseOwl.gif") );
	static Timer runTimer;
	static int running;   // should be available for all times at once
	
	// progress buttons created here for console, activeframe, pairframe
	public static JButton frameOwl;
	public static JButton activeOwlButton;
	public static JButton pairOwlButton;
	public static Dimension owlDim=new Dimension(22,22);
	public static Dimension progressDim=new Dimension(150,14);
	public static Dimension progressDimFat=new Dimension(78,20);
	
	// Constructor
	public OwlSpinner() {
		activeOwlButton=new JButton(owlBaseIcon);
		activeOwlButton.setPreferredSize(owlDim);
		pairOwlButton=new JButton(owlBaseIcon);
		pairOwlButton.setPreferredSize(owlDim);
		frameOwl=new JButton(owlBaseIcon);
		frameOwl.setPreferredSize(owlDim);
		running = 0;


		// create timer, progress bar animation 
	    runTimer = new Timer(300, new ActionListener() {
	        public void actionPerformed(ActionEvent e) {
	            if(running > 0) {
		              frameOwl.setIcon(progressIcon);
		              frameOwl.setPreferredSize(progressDim);
		              activeOwlButton.setIcon(progressIconFat);
		              activeOwlButton.setPreferredSize(progressDimFat);
		              PackControl.activeFrame.swapProgBar();
		              pairOwlButton.setIcon(progressIconFat);
		              pairOwlButton.setPreferredSize(progressDimFat);
		              PackControl.mapPairFrame.swapProgBar();
	            } 
	            else { 
		              frameOwl.setIcon(owlBaseIcon);
		              frameOwl.setPreferredSize(owlDim);
		              activeOwlButton.setIcon(owlBaseIcon);
		              activeOwlButton.setPreferredSize(owlDim);
		              PackControl.activeFrame.swapProgBar();
		              pairOwlButton.setIcon(owlBaseIcon);
		              pairOwlButton.setPreferredSize(owlDim);
		              PackControl.mapPairFrame.swapProgBar();
		              runTimer.stop();
	            }
	        }
	    });
		runTimer.setInitialDelay(200); 
	}
	
	synchronized public void setProgressBar(boolean ok) {
		
		running += (ok) ? 1 : -1;
		
		if (running > 0 && !runTimer.isRunning())
			runTimer.start();
	}
	
	/**
	 * Get the current progress button for the 'MainFrame';
	 * may be still owl, or progress bar
	 * @return JButton
	 */
	public JButton getActiveProgButton() {
		return activeOwlButton;
	}

	/**
	 * Get the current progress button for the 'PairFrame';
	 * may be still owl, or progress bar
	 * @return JButton
	 */
	public JButton getPairProgButton() {
		return pairOwlButton;
	}

	public JButton getFrameButton() {
		return frameOwl;
	}

	// abstract methods
	public boolean isRunning() {
		return runTimer.isRunning();
	}
	
	public void startstop(boolean ok) {
		setProgressBar(ok);
	}
	
}
