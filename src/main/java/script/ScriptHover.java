package script;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import circlePack.PackControl;
import fauxScript.FWSJPanel;
import handlers.SCRIPTHandler;
import images.CPIcon;

/**
 * Plain-frame window for the script, replacing the earlier hover/locked
 * design built on 'frames.HoverPanel'. A single 'Script' (or
 * 'Open Script'/'Close Script') button calls 'toggleShow()': (1) if
 * 'scriptFrame' is closed, open it on top; (2) if it's open but hidden
 * (iconified, or covered by another CirclePack window), raise it; (3)
 * if it's visible on top, close it.
 * @author kens
 *
 */
public class ScriptHover extends JPanel {

	private static final long
	serialVersionUID = 1L;

	public JScrollPane stackScroll;
	public FWSJPanel stackArea;
	public SCRIPTHandler scriptToolHandler;
	public JPanel scriptPanel; // for 'PackControl.scriptBar' when open
	public JFrame scriptFrame;

	// Track when 'scriptFrame' was last deactivated. A click on the
	// 'Script' button (which lives in a different window) necessarily
	// deactivates 'scriptFrame' first if it was on top, so a deactivation
	// timestamp from just now means "this click closed a frame that was
	// on top"; an older timestamp (or none) means the frame has been
	// sitting behind another window and should be raised instead.
	private volatile long lastDeactivatedAt = 0L;
	private static final long RECENTLY_ACTIVE_MS = 400;

	// Constructor
	public ScriptHover() {
		super();
		scriptFrame = new JFrame("CirclePack Script:");
		scriptFrame.setResizable(true);
		scriptFrame.setPreferredSize(new Dimension(PackControl.ControlDim1.width,400));
		scriptFrame.setLocation(120,60);
		scriptFrame.setIconImage(CPIcon.CPImageIcon("GUI/CP_Owl_22x22.png").getImage());
		// STACK: seems better on its own
		scriptFrame.addComponentListener(new ResizeAdapter());
		scriptFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				closeScript();
			}
			@Override
			public void windowDeactivated(WindowEvent we) {
				lastDeactivatedAt = System.currentTimeMillis();
			}
		});
		initComponents();
		this.add(stackScroll);
		scriptFrame.add(this);
		scriptFrame.pack();
		scriptFrame.setVisible(false);
	}

	public void initComponents() {

		// Layout is a big, big problem. See 'fauxScript' for design
		//   attempts. I've saved the old 'fauxScript/' and 'script/'
		//   directories in '~/holdScriptStuff.tgz'.
		//   'this' contains 'scriptPanel' for the 'scriptBar' and
		//       'stackScroll' for 'stackArea'
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

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

	/**
	 * Open the script window: embed 'scriptPanel' (the script bundle bar)
	 * above 'stackScroll' in this panel, move 'PackControl.scriptBar' up
	 * into 'scriptPanel', and raise 'scriptFrame'.
	 */
	public void openScript() {
		this.removeAll();
		this.add(scriptPanel);
		PackControl.scriptBar.swapScriptBar(true);
		this.add(stackScroll);

		// Opening can let Swing's focus machinery request focus for some
		// component buried in the script display, dragging the scroll
		// pane away from the top before the user ever sees it. Lock the
		// viewport for the whole open sequence, then release it and
		// force the scroll back to the top once things have settled.
		//AF>>>//
		final LockableJViewport viewport = (LockableJViewport) stackScroll.getViewport();
		viewport.setLocked(true);
		scriptFrame.pack();
		scriptFrame.setState(Frame.NORMAL);
		scriptFrame.setVisible(true);
		scriptFrame.toFront();
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				viewport.setLocked(false);
				viewport.setViewPosition(new Point(0,0));
			}
		});
		//<<<AF//
	}

	/**
	 * Close the script window: drop 'scriptPanel' from this panel and
	 * move 'PackControl.scriptBar' back down to the bottom of the main
	 * control frame.
	 */
	public void closeScript() {
		this.removeAll();
		PackControl.scriptBar.swapScriptBar(false);
		this.add(stackScroll);
		scriptFrame.setVisible(false);
	}

	/**
	 * The single trigger for the 'Script'/'Open Script'/'Close Script'
	 * buttons: (1) if closed, open it on top; (2) if open but hidden
	 * (iconified, or covered by another CirclePack window), raise it;
	 * (3) if visible on top, close it.
	 */
	public void toggleShow() {
		if (!scriptFrame.isVisible())
			openScript();
		else if (scriptFrame.getState()==Frame.ICONIFIED
				|| System.currentTimeMillis()-lastDeactivatedAt >= RECENTLY_ACTIVE_MS) {
			scriptFrame.setState(Frame.NORMAL);
			scriptFrame.toFront();
		}
		else
			closeScript();
	}

	/**
	 * Set title on the Script Frame.
	 * @param title
	 * @param hasChanged: true, add star to indicated editing
	 */
	public void scriptTitle(String title,boolean hasChanged) {
		if (hasChanged)
			scriptFrame.setTitle("CirclePack Script: "+title+"*");
		else {
			if (!title.startsWith("new_script"))
				scriptFrame.setTitle("CirclePack Script: "+title);
			else
				scriptFrame.setTitle(ScriptBundle.m_locator.getScriptURL(0));
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
			  scriptFrame.setPreferredSize(new Dimension(scriptFrame.getWidth(),scriptFrame.getHeight()));

			  int n=stackArea.getWidth();
//System.err.println("scriptHover resize adapter called: size "+n);
			  PackControl.scriptManager.cpScriptNode.stackBox.redisplaySB(n);
			  PackControl.scriptManager.repopulateRecurse(PackControl.scriptManager.cpScriptNode);
			  PackControl.scriptManager.cpDataNode.stackBox.redisplaySB(n);
			  PackControl.scriptManager.repopulateRecurse(PackControl.scriptManager.cpDataNode);
		  }
	}

}
