package util;

import images.CPIcon;

import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * This provides a progress indicator
 * @author kens
 */
public class AnimatedLabel extends JLabel implements Runnable {

	private static final long 
		serialVersionUID = 1L;

	protected Icon[] m_icons;
	protected int m_index = 0;
	protected boolean m_isRunning;
	
	public AnimatedLabel(String gifName, int numGifs) {
		m_icons = new Icon[numGifs];
		for (int k=0; k<numGifs; k++) 
			m_icons[k]=CPIcon.CPImageIcon(gifName+k+".gif");
		setIcon(m_icons[0]);
		Thread tr = new Thread(this);
		tr.setPriority(Thread.MAX_PRIORITY);
		tr.start();
	}
	public void setRunning(boolean isRunning) {
		m_isRunning = isRunning;
	}
	public boolean getRunning() {
		return m_isRunning;
	}

	public void run() {
		while(true) {
			if (m_isRunning) {
				m_index++;
				if (m_index >= m_icons.length)
					m_index = 0;
				setIcon(m_icons[m_index]);
				Graphics g = getGraphics();
				m_icons[m_index].paintIcon(this, g, 0, 0);
			}
			else {
				if (m_index > 0) {
					m_index = 0;
					setIcon(m_icons[0]);
				}
			}
			try { Thread.sleep(500); } 
			catch (InterruptedException ex) {}
		}
	}
}
