package frames;

import images.CPIcon;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import mytools.MyTool;
import mytools.MyToolEditor;
import mytools.MyToolHandler;

/**
 * ScriptToolEditor is a popup frame to allow the user to create
 * tools to add to the script. Each tool involves a string of
 * commands for CPack, an Icon, and a tooltip. These can be
 * saved in XML format and are then read in on startup,
 * going into hashedTools. 
 * @author kens
 */
public class ScriptToolEditor extends MyToolEditor {

	private static final long 
	serialVersionUID = 1L;
	
	public JCheckBox ckbox;
	private static String scriptIcons[]={"list.png","debugger.png","delete.png",
		"network.png","run.png","metacontact_offline.png","idea.png","bookmark.png",
		"button_ok.png","centrejust.png","dnd_multi.png","psi.png","xeyes.png",
		"kivio.png","kuickshow.png","mozilla.png","netbeans.png","klines.png",
		"kghostview.png","amarok.png","apollon.png","userconfig.png",
		"metacontact_online.png","icq_dnd.png","format_increaseindent.png",
		"editdelete.png","bookmark_add.png"};
	
	// Constructor
	public ScriptToolEditor(String tool_type,MyToolHandler par) {
		super(tool_type,par);
		iconDir=("script");
		for (int i=0;i<scriptIcons.length;i++) {
			theCPIcons.addElement(new CPIcon(iconDir+"/"+scriptIcons[i]));
		}
		resetIconList();
	}

	public JPanel topPanel() { // used 'prototypePanel' to create this
		JPanel panel=new JPanel();
		try {
			panel.setPreferredSize(new java.awt.Dimension(400, 137));
			panel.setBorder(BorderFactory.createTitledBorder("Command"));
			{
				JScrollPane jScrollPane1 = new JScrollPane();
				panel.add(jScrollPane1);
				jScrollPane1.setPreferredSize(new java.awt.Dimension(377, 72));
				jScrollPane1.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
				{
					cmdArea = new JTextArea();
				    cmdArea.setLineWrap(true);
				    util.EmacsBindings.addEmacsBindings(cmdArea);
					cmdArea.setToolTipText("Construct a command; see 'Help -> Command Details'. ");
					jScrollPane1.setViewportView(cmdArea);
					cmdArea.setPreferredSize(new java.awt.Dimension(377, 167));
				}
			}
			{
				ckbox = new JCheckBox();
				ckbox.setToolTipText("Is this executed inline? (versus only by name) ");
				panel.add(ckbox);
				ckbox.setText("Inline cmd");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return panel;
	}
	
	/** 
	 * default dropability for tools
	 */
	public boolean setDropDefault() {
		return false;
	}
	
	public String substanceText() {
		return new String("a legitimate Mobius transform.");
	}
	
	public String formulateCmd() {
		return cmdArea.getText();
	}
	
	public void dropableCheckBox() { // set 'dropBox' and 'dropMode'
		wantDropBox=true;
		dropMode=setDropDefault();
	}
	
	public void resetMoreFields() {
		cmdArea.setText("");
		dropMode=setDropDefault();
	}
	
	public void initMoreFields(MyTool theTool) {
		cmdArea.setText(theTool.getCommand());
	}
	
	/**
	 * This constructor is used by the getGUIBuilderInstance method to
	 * provide an instance of this class which has not had it's GUI elements
	 * initialized (ie, initGUI is not called in this constructor).
	 */
	public ScriptToolEditor(Boolean initGUI) {
		super();
	}
	
	/**
	 * Override super to check that this is not a letter icon (used
	 * only for named commands).
	 * 
	 */
//	public void addTool(CPIcon cpIc) {
//		String icname=cpIc.getIconName();
//		if (icname.contains("mnu_")) return;
//		super.addTool(cpIc);
//	}
	
}
