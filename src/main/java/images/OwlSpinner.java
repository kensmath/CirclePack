package images;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import allMains.CPBase;
import circlePack.PackControl;
import circlePack.RunProgress;
import input.TrafficCenter;

/**
 * This creates timer and owl/progress bar in several buttons, plus an
 * emergency-stop button that appears next to the spinner once a
 * computation has been running longer than 'STOP_BUTTON_DELAY_MS' --
 * often a sign that something has gone wrong (e.g. a packing that will
 * never converge) -- so the user can abandon it and get the console
 * back rather than waiting indefinitely or killing the whole program.
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

	// How long (millis) a computation must have been running before the
	// emergency-stop button appears. Checked on the existing 300ms
	// timer tick, so no extra timer is needed.
	static final long STOP_BUTTON_DELAY_MS = 20000;
	// wall-clock time (millis) when 'running' most recently went 0->positive
	static volatile long runStartTime = 0L;

	// progress buttons created here for console, activeframe, pairframe
	public static JButton frameOwl;
	public static JButton activeOwlButton;
	public static JButton pairOwlButton;
	public static Dimension owlDim=new Dimension(22,22);
	public static Dimension progressDim=new Dimension(150,14);
	public static Dimension progressDimFat=new Dimension(78,20);

	// Emergency-stop button; hidden until a computation has run long
	// enough to trip 'STOP_BUTTON_DELAY_MS'. Drawn to look like a stop
	// sign rather than loaded from a resource file (see 'StopSignIcon').
	public static JButton stopButton;

	// Constructor
	public OwlSpinner() {
		activeOwlButton=new JButton(owlBaseIcon);
		activeOwlButton.setPreferredSize(owlDim);
		pairOwlButton=new JButton(owlBaseIcon);
		pairOwlButton.setPreferredSize(owlDim);
		frameOwl=new JButton(owlBaseIcon);
		frameOwl.setPreferredSize(owlDim);
		running = 0;

		stopButton=new JButton(new StopSignIcon(18));
		stopButton.setPreferredSize(new Dimension(22,22));
		stopButton.setToolTipText(
			"Emergency stop: abort the running computation and return to the console");
		stopButton.setBorderPainted(false);
		stopButton.setContentAreaFilled(false);
		stopButton.setFocusPainted(false);
		stopButton.setVisible(false);
		stopButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// immediate feedback; the aborted worker thread's own
				// cleanup calls 'forceOff()' to actually hide things
				stopButton.setEnabled(false);
				TrafficCenter.emergencyStop();
			}
		});

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

		              if (!stopButton.isVisible()
		            		  && System.currentTimeMillis()-runStartTime >= STOP_BUTTON_DELAY_MS) {
		            	  stopButton.setEnabled(true);
		            	  stopButton.setVisible(true);
		              }
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
		              stopButton.setVisible(false);
		              stopButton.setEnabled(true); // re-arm for the next run
	            }
	        }
	    });
		runTimer.setInitialDelay(200);
	}

	synchronized public void setProgressBar(boolean ok) {

		boolean wasIdle = (running<=0);
		running += (ok) ? 1 : -1;

		if (ok && wasIdle)
			runStartTime = System.currentTimeMillis();

		if (running > 0 && !runTimer.isRunning())
			runTimer.start();
	}

	/**
	 * Unconditionally reset to idle -- used by the emergency-stop path
	 * (and safe to use after any other abnormal unwind) so a mismatched
	 * nesting of 'startstop(true)' calls can't leave the spinner (or
	 * the stop button) showing forever. Safe to call from any thread;
	 * marshals the actual Swing updates onto the EDT.
	 */
	public void forceOff() {
		Runnable r = new Runnable() {
			public void run() {
				running = 0;
				if (runTimer!=null)
					runTimer.stop();
				frameOwl.setIcon(owlBaseIcon);
				frameOwl.setPreferredSize(owlDim);
				activeOwlButton.setIcon(owlBaseIcon);
				activeOwlButton.setPreferredSize(owlDim);
				PackControl.activeFrame.swapProgBar();
				pairOwlButton.setIcon(owlBaseIcon);
				pairOwlButton.setPreferredSize(owlDim);
				PackControl.mapPairFrame.swapProgBar();
				stopButton.setVisible(false);
				stopButton.setEnabled(true);
			}
		};
		if (SwingUtilities.isEventDispatchThread())
			r.run();
		else
			SwingUtilities.invokeLater(r);
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

	/**
	 * The emergency-stop button, hidden until a computation has been
	 * running longer than 'STOP_BUTTON_DELAY_MS'. Callers place it next
	 * to whichever spinner button they display (see
	 * 'PackControl.buildFrameButtons()').
	 * @return JButton
	 */
	public JButton getStopButton() {
		return stopButton;
	}

	// abstract methods
	public boolean isRunning() {
		return runTimer.isRunning();
	}

	public void startstop(boolean ok) {
		setProgressBar(ok);
	}

	/**
	 * Small self-drawn red stop-sign (octagon with a white bar) so this
	 * doesn't need a bundled image resource just for one button.
	 */
	static class StopSignIcon implements Icon {
		private final int size;
		StopSignIcon(int size) {
			this.size = size;
		}
		public int getIconWidth() {
			return size;
		}
		public int getIconHeight() {
			return size;
		}
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			int s = size;
			int cut = s/3;
			int[] xs = { x+cut, x+s-cut, x+s,    x+s,      x+s-cut, x+cut, x,     x       };
			int[] ys = { y,     y,       y+cut,  y+s-cut,  y+s,     y+s,   y+s-cut, y+cut };
			g2.setColor(Color.RED.darker());
			g2.fillPolygon(xs, ys, 8);
			g2.setColor(Color.WHITE);
			int barH = Math.max(2, s/5);
			g2.fillRect(x+cut, y+(s-barH)/2, s-2*cut, barH);
			g2.dispose();
		}
	}

}
