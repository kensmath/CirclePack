package script;

import images.CPIcon;

import javax.swing.JPanel;

import mytools.MyTool;
import allMains.CPBase;

/**
 * Panel containing a "next" button as 'MyTool' and a
 * button for returning to top of script. This can be
 * used various places, scriptbar, 'MainFrame', 'PairFrame',
 * etc. 
 * @author kens
 *
 */
public class NextBundle extends JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	MyTool nexttool;
	MyTool toptool;

	public NextBundle() {
		// "next" button
		nexttool=new MyTool(new CPIcon("script/next_box.png"),null,
				"Next script cmd",null,"Execute the next script command",
				"SCRIPT:",false,CPBase.scriptManager);
		nexttool.setEnabled(false);
	
		// reset to top of script
		toptool=new MyTool(new CPIcon("script/top.png"),null,
			"Reset script",null,"Reset to the start of the script",
			"SCRIPT:",false,CPBase.scriptManager);
		toptool.setEnabled(false);
		
		add(nexttool);
		add(toptool);
	}
	
	public void enableNext(boolean ok) {
		nexttool.setEnabled(ok);
	}
	
	public void enableTop(boolean ok) {
		toptool.setEnabled(ok);
	}
	
}
