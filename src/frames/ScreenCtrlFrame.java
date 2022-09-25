package frames;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;

import allMains.CirclePack;
import circlePack.PackControl;
import input.TrafficCenter;
import panels.DispPanel;
import panels.ScreenShotPanel;
import panels.ScreenPanel;

/**
 * This is the tabbed panel with various info on packing, screen settings,
 * lists, etc., initiated on startup. The pack[]'s keep their info, so on change
 * of active packing, have to establish the various values; changes then only
 * affect the active packing.
 */

public class ScreenCtrlFrame extends JFrame implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	// Instance Variables
	JTree dataTree;
	JPanel dataTreePanel;
	JScrollPane dataTreeScrollPane;
	public JTabbedPane tabbedPane;

	public ScreenShotPanel imagePanel;
	public DispPanel displayPanel;
	public ScreenPanel screenPanel;

	// Constructor
	public ScreenCtrlFrame() { 
		this.setAlwaysOnTop(false);
		addWindowListener(new WAdapter());
		setSize(new Dimension(PackControl.ControlDim1.width,350));
		setTitle("CirclePack Screen Options, p"+CirclePack.cpb.getActivePackNum());
		tabbedPane = new JTabbedPane();

// ----- next tab is "Display Options"
		displayPanel = new DispPanel();
		displayPanel.setToolTipText("default Display options");
		tabbedPane.add(displayPanel,"Display");
		
// ----- next is panel with thumbnail images
		imagePanel=new ScreenShotPanel();
		tabbedPane.add(imagePanel,"ScreenShots");

// ----- next is "Screen" panel
		screenPanel=new ScreenPanel();
		screenPanel.setToolTipText("Screen settings, opacity, lines, etc");
		tabbedPane.add(screenPanel,"Screen");
		
		this.add(tabbedPane);
		
	}
	
	/*
	 * see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		if (command.equals("Repaint")) {
	  		try {
	  			TrafficCenter.cmdGUI("disp -wr");
	  		} catch (Exception ex) {}
			PackControl.activeFrame.reDisplay();
		}

	}
	
	public void setTab(int tab) {
		if (tab<0 || tab>tabbedPane.getComponentCount()) 
			return;
		tabbedPane.setSelectedIndex(tab);
	}
	
	class WAdapter extends WindowAdapter {
		public void windowClosing(WindowEvent wevt) {
			if (wevt.getID()==WindowEvent.WINDOW_CLOSING)
				PackControl.screenCtrlFrame.setVisible(false);
		}
	}
} 
