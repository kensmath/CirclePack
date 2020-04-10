package script;

import images.CPIcon;
import input.CPFileManager;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;

import mytools.MyTool;
import util.MemComboBox;
import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import dragdrop.EditTransferable;
import dragdrop.ToolDragSourceListener;

/**
 * This is the bundle of buttons, file chooser, edit bar
 * which moves between the bottom of 'PackControl' and the
 * top of the script frame.
 * @author kstephe2
 */
public class ScriptBundle extends JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;
	
	protected JEditorPane   m_browser;
	public static MemComboBox   m_locator;
	protected JLabel        m_status;
	protected boolean isInProcess=false; // help prevent extraneous load actions
	public String loadedXmd=""; // currently loaded script

	public static ScriptManager manager;
	public static JPanel scriptEditBar; // contains add_above/below buttons
	public static JButton scriptButton;
	public NextBundle nextBundle;
	
	// for openAllButton
	public static JButton openAllButton;
	static Icon openAllIcon=CPIcon.CPImageIcon("script/small_plus.png");
	static Icon closeAllIcon=CPIcon.CPImageIcon("script/small_minus.png");

	public static boolean firstInit=false; // true when scriptFrame first opened
	
	// Constructor
	public ScriptBundle() {
		super();
		this.setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));
		this.setAlignmentX(0);
		scriptEditBar = buildEditBar();
		scriptEditBar.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(0, 5, 0, 0),
				new EtchedBorder()));
		
		
		manager=CPBase.scriptManager;

		// STACK
		setBorder(new EmptyBorder(1,1,1,1));
//		setBorder(new LineBorder(Color.orange,3,false));

		// create/add 'MemComboBox' for history of scripts loaded
		String cmdURLs=PackControl.preferences.getCmdURLfile();
		if (cmdURLs.startsWith("~/")) 
			cmdURLs=new String(CPFileManager.HomeDirectory+File.separator+cmdURLs.substring(2));
		File file=new File(cmdURLs);
		try {
			file.createNewFile();
		} catch (IOException iox) {
			CirclePack.cpb.errMsg("failed to open xmd file");
		}
		m_locator = new MemComboBox(file);

		// STACK: TODO: layout too rigid, m_locator very sensitive to PreferredSize
		m_locator.setMinimumSize(new Dimension(150,22));
		m_locator.setMaximumSize(new Dimension(600,22));
		m_locator.setPreferredSize(new Dimension(600,22));
		
		m_locator.addActionListener(this);
		
		// top panel for loading options and "script" chooser
		JPanel topPanel=new JPanel();//new FlowLayout(FlowLayout.LEADING));
		topPanel.setLayout(new BoxLayout(topPanel,BoxLayout.LINE_AXIS));

		// "www" icon
		MyTool mytool=new MyTool(new CPIcon("script/www_icon.png"),null,
				"open browser",null,"Open browser to load scripts/packings from the web",
				"SCRIPT:",false,manager);
		StackBox.setFixedSizes(mytool,30,24);
		topPanel.add(mytool);

		// "open" folder
		mytool=new MyTool(new CPIcon("script/folder_yellow_open.png"),null,
				"Load script file",null,"Load new script from a file",
				"SCRIPT:",false,manager);
		StackBox.setFixedSizes(mytool,30,24);
		topPanel.add(mytool);
		
		// "save" folder
		mytool=new MyTool(new CPIcon("script/save.png"),null,
				"Save script",null,"Save script to a file",
				"SCRIPT:",false,manager);
		StackBox.setFixedSizes(mytool,30,24);
		topPanel.add(mytool);

		// tool to open fresh script
		mytool=new MyTool(new CPIcon("script/new.png"),null,
		"New script",null,"Open a fresh script",
		"SCRIPT:",false,manager);
		StackBox.setFixedSizes(mytool,30,24);
		topPanel.add(mytool);
		
		// add MemComboBox
		topPanel.add(m_locator);

		topPanel.add(Box.createHorizontalGlue());
		topPanel.setBorder(new EmptyBorder(1,1,1,1));
		
		// bottomPanel
		JPanel bottomPanel=new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel,BoxLayout.LINE_AXIS));
		
		// 'NextBundle' for "next" and top buttons
		nextBundle=new NextBundle();

		// 'next' tool should be draggable to script icons
		mytool=(MyTool)(nextBundle.getComponent(0));
		DragSource dragSource=DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(mytool,DnDConstants.ACTION_LINK,
		  new DragGestureListener() {
			public void dragGestureRecognized(DragGestureEvent event) {
				Transferable transferable=
					new EditTransferable((MyTool)event.getComponent());
				event.startDrag(null,transferable,new ToolDragSourceListener());
			}
		  });
		

		nextBundle.setBorder(new EmptyBorder(1,1,1,1));

		// Button to bring up script frame
		scriptButton=new JButton("Open Script");
		scriptButton.setFont(new Font(scriptButton.getFont().
				toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
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
		
		// Button to "open" all command tools
		openAllButton=new JButton();
		openAllButton.setIcon(openAllIcon);
		openAllButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//AF>>>//
				// Lock the viewport so GUI changes don't jitter and relocate.
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
				//<<<AF//
				
				PackControl.scriptManager.toggleCmdOpenMode();
				cmdRecurseOC(PackControl.scriptManager.cpScriptNode,PackControl.scriptManager.cmdOpenMode);
				PackControl.scriptManager.repopulateRecurse(PackControl.scriptManager.cpScriptNode);
				
				//AF>>>//
				// Unlock the viewport.
				EventQueue.invokeLater(new Runnable() {
					public void run() {
						((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
					}
				});
				//<<<AF//
			}
		});
		openAllButton.setToolTipText("toggle: open/close command tools");
		StackBox.setFixedSizes(openAllButton,22,22);
		
		// The edit bar and openAll button used in ScriptFrame (on),
		//    but are off in PackControl Frame.
		scriptEditBar.setVisible(false);
		openAllButton.setVisible(false); 
		
		StackBox.setFixedSizes(nextBundle,80,30);
		StackBox.setFixedSizes(scriptEditBar,300,28);
		StackBox.setFixedSizes(scriptButton,110,24);
//		nextBundle.setBounds(1,30,80,30);
//		scriptButton.setBounds(90,34,110,24); 
		bottomPanel.add(nextBundle);
		bottomPanel.add(scriptButton);
		bottomPanel.add(scriptEditBar);
		bottomPanel.add(openAllButton);
		bottomPanel.add(Box.createHorizontalGlue());
		
		topPanel.setAlignmentX(0);
		bottomPanel.setAlignmentX(0);
		this.add(topPanel);
		this.add(bottomPanel);
		
		// STACK
//		this.setBackground(Color.green);
//		setPreferredSize(new Dimension(CPBase.pcWidth-4,54));
	}
	
	/**
	 * Recursive go through the tree to open or close any command nodes
	 * (which are not currently in EDIT state). See 'ScriptManager.cmdOpenMode'
	 * flag.
	 * @param treeNode
	 * @param open; boolean: true means to open, false to close.
	 */
	public void cmdRecurseOC(CPTreeNode treeNode,boolean open) {
		if (treeNode==null) return;
		if (treeNode.stackBox.isOpen && treeNode.getChildCount()>0) {
			int j=0;
			while (j<treeNode.getChildCount()) {
				CPTreeNode child=(CPTreeNode)treeNode.getChild(j);
				j++;
				if (child.tntype==CPTreeNode.COMMAND || child.tntype==CPTreeNode.MODE) {
					if (open && !child.stackBox.isOpen)
//						child.stackBox.open();
						child.stackBox.isOpen=true;
					if (!open && child.stackBox.isOpen)
//						child.stackBox.close();
						child.stackBox.isOpen=false;
				}
				if (child.tntype==CPTreeNode.LINEUP && open) {
					child.stackBox.open();
					cmdRecurseOC(treeNode,open);
				}
				else cmdRecurseOC(child,open);
			}
		}
	}
				
				
	/**
	 * Build the scriptEditBar of The actions here end up calling 'StackBox.editAction'.
	 */
	public JPanel buildEditBar() {
		
		JPanel panel=new JPanel();
		panel.setLayout(new BoxLayout(panel,BoxLayout.LINE_AXIS));
		panel.add(new JLabel("Edit (d&d)"));
		panel.add(new editTool("script/e_edit.png","edit",
			"Drop on script element to begin editing "));
		panel.add(new editTool("script/commandAbove.png","add_above_command",
			"Drop on script element to add a command above it "));
		panel.add(new editTool("script/commandBelow.png","add_below_command",
			"Drop on script element to add a command below it "));
		panel.add(new editTool("script/textAbove.png","add_above_text",
			"Drop on script element to add text (or file in Data section) above it "));
		panel.add(new editTool("script/textBelow.png","add_below_text",
			"Drop on script element to add text (or file in Data section) below it "));
		panel.add(new editTool("script/sectionAbove.png","add_above_section",
			"Drop on script element to add a section above it "));
		panel.add(new editTool("script/sectionBelow.png","add_below_section",
			"Drop on script element to add a section below it "));
		panel.setToolTipText("Drad and drop tools");
		return panel;
	}

	/**
	 * For comboBox change events in 'scriptBar' (bottom of 'PackControl'
	 * or top of 'ScriptFrame')
	 */
	public void actionPerformed(ActionEvent evt) { 
		if (evt.getActionCommand().equals("comboBoxChanged")
				|| evt.getActionCommand().equals("comboBoxEdited")) {
			// already handling an event, 
			if (isInProcess) // || m_runner.getRunning())
				return;
			
			// 'addOKflag' is false, so don't want to load this script
			if (!MemComboBox.addOKflag) 
				return;
			
			// else, load the script
			isInProcess=true;
			String sUrl = (String)m_locator.getSelectedItem();
			// this file is already loaded -- likely, an erroneous duplicate event
			if (sUrl == null || sUrl.trim().length() == 0 || sUrl.trim().equals(loadedXmd)) {
				isInProcess=false;
				return;
			}

			// if www
			if (sUrl.startsWith("www.")) {
				sUrl=new String("http://"+sUrl);
				sUrl.replace("%7E","~");		
			}

			// load the script
			if (manager.getScript(sUrl,sUrl,false)!=0) { // success
				isInProcess=false;
				m_locator.setSuccess();
			}
			else {
				isInProcess=false;
				m_locator.setFailure();
			}
		}
	}
	
	/**
	 * setting openAllButton icon to +/-
	 * @param open, boolean. open=true, then want 'small_minus' to show
	 */
	public void setOpenAllButton(boolean open) {
		if (open)
			openAllButton.setIcon(closeAllIcon);
		else
			openAllButton.setIcon(openAllIcon);
	}

	/**
	 * A single ScriptBar is created in PackControl but used
	 * both in the PackControl and Script frames. Move from
	 * frame contentPane to 'scriptPanel' of scriptFrame.
	 * In the former, the editBar is invisible.
	 * @param attach2Script, boolean: editBar into Script frame
	 */
	public void swapScriptBar(boolean attach2Script) {
		Container pane=PackControl.frame.getContentPane();
		if (attach2Script) { // 'scriptBar' to top of scriptFrame
			// remove from control frame
			pane.remove((Component)PackControl.scriptBar);
			pane.setPreferredSize(PackControl.ControlDim2);
			PackControl.frame.pack();

			// redo settings
			scriptEditBar.setVisible(true);
			openAllButton.setVisible(true);
			setPreferredSize(new Dimension(PackControl.ControlDim1.width-2,58));
			ScriptBundle.scriptButton.setText("Close Script");
			
			// add to scriptPanel in script frame
			PackControl.scriptHover.scriptPanel.add(PackControl.scriptBar);
			if (!firstInit) {
				firstInit=true;
				Point pt=PackControl.frame.getLocation();
				pt.y=pt.y+PackControl.frame.getHeight();
				PackControl.scriptHover.lockedFrame.setLocation(pt);
			}
		}
		else { // 'scriptBar' to bottom of PackControl

			// remove from scriptPanel
			scriptEditBar.setVisible(false);
			openAllButton.setVisible(false);
			ScriptBundle.scriptButton.setText("Open Script");
			PackControl.scriptHover.scriptPanel.remove((Component)PackControl.scriptBar);
			
			// reset hard coded bounds and add to control frame
			PackControl.scriptBar.setBounds(0,PackControl.ControlDim2.height,PackControl.ControlDim1.width,60);
			setPreferredSize(new Dimension(PackControl.ControlDim1.width-2,58));
			pane.add(PackControl.scriptBar);
			pane.setPreferredSize(PackControl.ControlDim1);
			PackControl.frame.pack();
		}
	}

}

/**
 * Convenience class to build edit bar mytools and set drag action
 */
class editTool extends MyTool {

	private static final long 
	serialVersionUID = 1L;
	
	// Constructor
	public editTool(String iconname,String name,String tooltip) {
	super(new CPIcon(iconname),null,name,null,
			tooltip,"SCRIPT:",false,null);
	DragSource dragSource=DragSource.getDefaultDragSource();
	dragSource.createDefaultDragGestureRecognizer(this,DnDConstants.ACTION_LINK,
	  new DragGestureListener() {
		public void dragGestureRecognized(DragGestureEvent event) {
			Transferable transferable=
				new EditTransferable((MyTool)event.getComponent());
			event.startDrag(null,transferable,new ToolDragSourceListener());
		}
	  });
	}
}



