package script;

import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import circlePack.PackControl;
import fauxScript.FWSJPanel;
import frames.HoverPanel;
import handlers.SCRIPTHandler;
import images.CPIcon;

/**
 * Hover/locked from for the script.
 * @author kens
 *
 */
public class ScriptHover extends HoverPanel {

	private static final long 
	serialVersionUID = 1L;

	public JScrollPane stackScroll;
	public FWSJPanel stackArea;
	public SCRIPTHandler scriptToolHandler;
	public JPanel scriptPanel; // for 'PackControl.scriptBar' when open
	
	// Constructor
	public ScriptHover() {
		super(PackControl.ControlDim1.width,400,"CirclePack Script:");
		lockedFrame.setResizable(true);
		// STACK: seems better on its own
		lockedFrame.addComponentListener(new ResizeAdapter());
	}

	public void initComponents() {
		
		// Layout is a big, big problem. See 'fauxScript' for design
		//   attempts. I've saved the old 'fauxScript/' and 'script/'
		//   directories in '~/holdScriptStuff.tgz'.
		//   'this' contains 'scriptPanel' for the 'scriptBar' and 
		//       'stackScroll' for 'stackArea'
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		lockedFrame.setTitle("CirclePack Script:");  // add name when a script is read in
		lockedFrame.setIconImage(CPIcon.CPImageIcon("GUI/CP_Owl_22x22.png").getImage());
			
		// This panel is filled by 'ScriptBar' (later)
		scriptPanel = new JPanel(); //1 
		scriptPanel.setLayout(new BoxLayout(scriptPanel, BoxLayout.PAGE_AXIS));
		scriptPanel.setAlignmentX(0);
		// STACK
//		scriptPanel.setBorder(new LineBorder(Color.magenta)); 
		scriptPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,80));

		stackArea=new FWSJPanel();
		stackArea.setLayout(new BoxLayout(stackArea, BoxLayout.PAGE_AXIS));
		// STACK
//		stackArea.setBorder(new LineBorder(Color.red,3,false)); 
//		stackArea.setBackground(Color.yellow);//white);

		// stack scroll contains the script 'StackBox's
		//AF>>>//
		// Give stackScroll a LockableJViewport set to stackArea.
		//stackScroll = new JScrollPane(stackArea);
		LockableJViewport lockableJViewport = new LockableJViewport();
		lockableJViewport.setView(stackArea);
		stackScroll = new JScrollPane();
		stackScroll.setViewport(lockableJViewport);
		//<<<AF//
		stackScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		stackScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		stackScroll.setAlignmentX(0);

//		stackScroll.setBackground(Color.green); // STACK

		// create scriptToolHandler: side of canvas, dropable/named icons
		scriptToolHandler = new SCRIPTHandler(null); // CPFileManager.getMyTFile("scriptctrl.myt"));
		
		// Why is this here? I think this is outdated, but must be overriden somewhere
//		scriptToolHandler.toolBar.setBounds(0,0,PackControl.getActiveCanvasSize(),34);
//		scriptToolHandler.toolBar.setBorder(new EmptyBorder(0,0,0,0));
		
		initScriptArea();
	}
	
	public void setInitPanel() {
		// TODO: have to figure out how to choose size.
		this.add(stackScroll);
	}
	
	public void loadHover() {
		this.removeAll();
		lockedFrame.setVisible(false);
		PackControl.scriptBar.swapScriptBar(false);
		this.add(stackScroll);
	}
	
	public void loadLocked() {
		this.removeAll();
		this.add(scriptPanel);
		PackControl.scriptBar.swapScriptBar(true);
		this.add(stackScroll);
	}
	
	/**
	 * Set title on the Script Frame.
	 * @param title 
	 * @param hasChanged: true, add star to indicated editing
	 */
	public void scriptTitle(String title,boolean hasChanged) {
		if (hasChanged)
			lockedFrame.setTitle("CirclePack Script: "+title+"*");  
		else {
			if (!title.startsWith("new_script"))
				lockedFrame.setTitle("CirclePack Script: "+title);
			else
				lockedFrame.setTitle(ScriptBundle.m_locator.getScriptURL(0));
		}
	}
	
	/** 
	 * At startup, this initiates scriptArea with default width.
	 */
	public void initScriptArea() {
		initScriptArea(PackControl.ControlDim1.width);
	}
	
	/**
	 * This initiates persistent 'rootNode', 'cpScriptNode', 
	 * and 'cpDataNode' nodes: these stay until closing the
	 * application. It puts their 'stackBox's in the 
	 * 'StackArea' for the script window.
	 */
	public void initScriptArea(int initWidth) {
		PackControl.scriptManager.WIDTH=initWidth;

		PackControl.scriptManager.rootNode = new CPTreeNode("Error: should have loaded starter script",
				CPTreeNode.ROOT, false, null);

		// CPScript node and 'stackBox'
		PackControl.scriptManager.cpScriptNode= new CPTreeNode("",CPTreeNode.CPSCRIPT,null);
		PackControl.scriptManager.cpScriptNode.stackBox.setAlignmentX(0);
		PackControl.scriptManager.cpScriptNode.stackBox.myWidth=PackControl.scriptManager.WIDTH; // *2; 
		PackControl.scriptManager.rootNode.add(PackControl.scriptManager.cpScriptNode);
		stackArea.add(PackControl.scriptManager.cpScriptNode.stackBox);

		// CPData node and 'stackBox'
		PackControl.scriptManager.cpDataNode=new CPTreeNode(new String("Files: 0"),CPTreeNode.CPDATA, null);
		PackControl.scriptManager.cpDataNode.stackBox.setAlignmentX(0);

		// put them in 'StackArea'
		PackControl.scriptManager.rootNode.add(PackControl.scriptManager.cpDataNode);
		stackArea.add(PackControl.scriptManager.cpDataNode.stackBox);
		stackArea.add(Box.createVerticalGlue());
		
		PackControl.scriptManager.hasChanged=false;
	}

	public SCRIPTHandler getHandler() {
		return scriptToolHandler;
	}
	
	/**
	 * Reset 'myWidth's of 'StackBox's when script window is resized
	 * @param wide
	 */
	class ResizeAdapter extends ComponentAdapter {
		  public void componentResized(ComponentEvent e) {
			  lockedFrame.setPreferredSize(new Dimension(lockedFrame.getWidth(),lockedFrame.getHeight()));

			  int n=stackArea.getWidth();
//System.err.println("scriptHover resize adapter called: size "+n);
			  PackControl.scriptManager.cpScriptNode.stackBox.redisplaySB(n);
			  PackControl.scriptManager.repopulateRecurse(PackControl.scriptManager.cpScriptNode);
			  PackControl.scriptManager.cpDataNode.stackBox.redisplaySB(n);
			  PackControl.scriptManager.repopulateRecurse(PackControl.scriptManager.cpDataNode);
		  }
	}
	
}
