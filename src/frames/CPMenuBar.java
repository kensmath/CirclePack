package frames;

import input.TrafficCenter;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import allMains.CPBase;
import circlePack.PackControl;
import exceptions.InOutException;

/**
 * For the general menu bar that appears on 'MainFrame' 
 * and 'PairedFrame' left and right.
 * @author kens
 *
 */
public class CPMenuBar extends JMenuBar implements ActionListener {

	private static final long 
	serialVersionUID = 1L;

	JMenu fileMenu;
	JMenu saveMenu;
	JMenu screenMenu;
	JMenu scriptMenu;
	JMenu advancedMenu;
	JMenu infoMenu;

	public CPMenuBar() {
		super();
		fileMenu = new JMenu("File");
		saveMenu = new JMenu("Save");
		screenMenu = new JMenu("Screen");
		infoMenu = new JMenu("Help");
		advancedMenu = new JMenu("Advanced");

		// === file
		JMenuItem loadAction = new JMenuItem("Load Packing");
		loadAction.setActionCommand("loadpack");
		loadAction.addActionListener(this);

		JMenuItem newScriptAction = new JMenuItem("Load Script");
		newScriptAction.setActionCommand("loadnewScript");
		newScriptAction.addActionListener(this);

		JMenuItem exitAction = new JMenuItem("Exit CirclePack");
		exitAction.setActionCommand("exitCP");
		exitAction.addActionListener(this);

		fileMenu.add(loadAction);
		fileMenu.add(newScriptAction);
		fileMenu.add(exitAction);

		add(fileMenu);

		// === save
		JMenuItem savePackAction=new JMenuItem("Save Packing");
		savePackAction.setActionCommand("savePack");
		savePackAction.addActionListener(this);

		JMenuItem savePostAction=new JMenuItem("Export Image");
		savePostAction.setActionCommand("savePost");
		savePostAction.addActionListener(this);

		JMenuItem outputAction=new JMenuItem("Output");
		outputAction.setActionCommand("saveOutput");
		outputAction.addActionListener(this);

		saveMenu.add(savePackAction);
		saveMenu.add(outputAction);
		saveMenu.add(savePostAction);

		add(saveMenu);

		// === screen
		JMenuItem dispAction = new JMenuItem("Display Options");
		dispAction.setActionCommand("screenDisplay");
		dispAction.addActionListener(this);

		JMenuItem shotAction = new JMenuItem("Screen Shots");
		shotAction.setActionCommand("screenShots");
		shotAction.addActionListener(this);

		JMenuItem screenAction = new JMenuItem("Screen Settings");
		screenAction.setActionCommand("screenSettings");
		screenAction.addActionListener(this);

		screenMenu.add(dispAction);
		screenMenu.add(shotAction);
		screenMenu.add(screenAction);

		add(screenMenu);

		// ==== info
		JMenuItem helpAction = new JMenuItem("Toggle Help Frame");
		helpAction.setActionCommand("helpFrame");
		helpAction.addActionListener(this);

		infoMenu.add(helpAction);
		add(infoMenu);

		// === advanced
		JMenuItem advAction=new JMenuItem("Toggle Advanced GUI");
		advAction.setActionCommand("advanced");
		advAction.addActionListener(this);

		advancedMenu.add(advAction);
		add(advancedMenu);

	}

	public void actionPerformed(ActionEvent evt) {
		String acmd=evt.getActionCommand();

		if (acmd.equals("loadpack")) {
			try {
				TrafficCenter.cmdGUI("load_pac");
				return;
			} catch (Exception ex) {
				throw new InOutException("error in choosing pack: "
						+ ex.getMessage());
			}
		}

		if (acmd.equals("loadnewScript")) {
			try {
				// TODO: SCRIPTLOAD problem
				CPBase.scriptManager.getScript(null,null,true);
				return;
			} catch (Exception ex) {
				throw new InOutException("error in loading script: "
						+ ex.getMessage());
			}
		}

		if (acmd.equals("exitCP")) {
			try {
				TrafficCenter.cmdGUI("exit");
				return;
			} catch (Exception ex) {}
		}

		if (acmd.startsWith("save")) {
			int tab=0; // default, "savePack"
			if (acmd.equals("saveOutput")) 
				tab=1;
			if (acmd.equals("savePost")) 
				tab=2;
			PackControl.outputFrame.setTab(tab);
			PackControl.outputFrame.setVisible(true);
			PackControl.outputFrame.setState(java.awt.Frame.NORMAL);
			return;
		}

		if (acmd.startsWith("screen")) {
			int tab=0; // default, "screenDisplay"
			if (acmd.equals("screenShots")) 
				tab=1;
			if (acmd.equals("screenSettings"))
				tab=2;
			PackControl.screenCtrlFrame.setTab(tab);
			PackControl.screenCtrlFrame.setVisible(true);
			PackControl.screenCtrlFrame.setState(java.awt.Frame.NORMAL);
			return;
		}

		if (acmd.equals("helpFrame")) {
			if (PackControl.helpHover.isLocked()) {
				PackControl.helpHover.lockedFrame.setState(Frame.NORMAL);
			}
			PackControl.helpHover.lockframe();
			return;
		}

		if (acmd.equals("advanced")) {
			if (PackControl.frame.isVisible())
				PackControl.frame.setVisible(false);
			else
				PackControl.frame.setVisible(true);
			PackControl.screenCtrlFrame.setState(java.awt.Frame.NORMAL);
			return;
		}
	}
}
