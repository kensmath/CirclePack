package script;

import images.CPIcon;

import java.awt.Dimension;

import javax.swing.ImageIcon;
import javax.swing.JButton;

/** For small operation buttons in the script display window. 
 * Depending on type, get icon, tooltip text, action command.
 * Generally this is created, put in panel, and then forgotten.
 * (The calling routine sets the actionListener.)
 * @author kens
 */
public class LongLabel extends JButton {

	private static final long 
	serialVersionUID = 1L;
	
	static final int ACCEPT=3;
	static final int CANCEL=4;
	static final int DELETE=5;
	static final int INFO=6;
	
	ImageIcon icon;
	String toolTip;
	String actionCmd;

	public LongLabel(int type) {
		super();
		switch (type) {
		case ACCEPT: {
			icon=CPIcon.CPImageIcon("script/accept_label.png");
			toolTip=new String("accept editing");
			actionCmd=new String("accept_edit");
			break;
		}
		case CANCEL: {
			icon=CPIcon.CPImageIcon("script/cancel_label.png");
			toolTip=new String("cancel the editing");
			actionCmd=new String("cancel_edit");
			break;
		}
		case DELETE: {
			icon=CPIcon.CPImageIcon("script/kill_16x16.png");
			toolTip=new String("Delete this element");
			actionCmd=new String("delete_node");
			break;
		}
		
		default: {
			icon=CPIcon.CPImageIcon("script/info.png");
			toolTip=new String("Info");
			actionCmd=new String("info request");
			break;
		}
		} // end of switch
		setIcon(icon);
		setOpaque(false);
		setBorderPainted(false);
		setToolTipText(toolTip);
		setActionCommand(actionCmd);
		setSize(new Dimension(icon.getIconWidth(),icon.getIconHeight()));
	}
}
