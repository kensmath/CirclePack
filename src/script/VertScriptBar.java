package script;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import canvasses.MainFrame;
import circlePack.PackControl;
import frames.PairedFrame;

/**
 * There is a vertical script bar that is attached to
 * 'MainFrame' or to 'PairFrame', whichever is open.
 * We prepare it once and move it to the appropriate
 * frame (perhaps changing length if necessary --- hope
 * width stays the same).
 * 
 * TODO: may want way to show which icon is next command.
 * @author kens
 *
 */
public class VertScriptBar extends JPanel {
	
	private static final long 
	serialVersionUID = 1L;
	
	public JPanel scriptTools;
	public NextBundle nextBundle;
	
	public VertScriptBar() {
		setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));
		setBorder(new LineBorder(Color.blue,1,false));
		JButton scriptButton=new JButton("Script");
		scriptButton.addMouseListener(PackControl.scriptHover);
		ScriptBundle.scriptButton.addMouseListener(PackControl.scriptHover);
		scriptButton.setFont(new Font(scriptButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
        scriptButton.setAlignmentX(Component.CENTER_ALIGNMENT);
//        scriptButton.setToolTipText("Toggle the 'Script' window for "+
//        		"commands/details");
		scriptButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (PackControl.scriptHover.isLocked()) { 
					// if iconified, bring it up
					if (PackControl.scriptHover.lockedFrame.getState()==
						JFrame.ICONIFIED)
						PackControl.scriptHover.lockedFrame.setState(Frame.NORMAL);
					else { // else load hover
						PackControl.scriptHover.loadHover();
						PackControl.scriptHover.locked=false;
					}
				}
				else { 
					PackControl.scriptHover.lockframe();
					PackControl.scriptHover.locked=true;
				}
			}
		});
		add(scriptButton);
		nextBundle=new NextBundle();
		add(nextBundle);
		scriptTools=new JPanel();
		add(scriptTools);
	}
	
	/**
	 * A single 'VertScriptBar' is created in PackControl but used
	 * both in 'PairedFrame' and 'MainFrame'. Move from one to
	 * the other when 'MapCanvasMode' has changed (and perhaps adjust
	 * length). 
	 */
	public void swapVertScriptBar() {
		if (PackControl.MapCanvasMode) { // into 'PairedFrame' mode
			PackControl.activeFrame.remove((Component)PackControl.vertScriptBar);
			PackControl.scriptHover.scriptToolHandler.toolBar.
		      setPreferredSize(new Dimension(70,PairedFrame.getCanvasDim().height-76));
//			PackControl.scriptFrame.scriptToolHandler.toolBar.
//		     setBorder(new LineBorder(Color.cyan,1,false));
			PackControl.mapPairFrame.layMeOut();
			PackControl.mapPairFrame.pack();
		}
		else { // into 'MainFrame' mode
			PackControl.mapPairFrame.remove((Component)PackControl.vertScriptBar); 
			PackControl.scriptHover.scriptToolHandler.toolBar.
		      setPreferredSize(new java.awt.Dimension(70,MainFrame.getCanvasDim().height-90));
//			PackControl.scriptFrame.scriptToolHandler.toolBar.
//		     setBorder(new LineBorder(Color.cyan,1,false));
			PackControl.activeFrame.layMeOut();
			PackControl.activeFrame.pack();
		}
	}
	
}
