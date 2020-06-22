package canvasses;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import dragdrop.ToolDropListener;
import exceptions.LayoutException;
import frames.CPMenuBar;
import handlers.ACTIVEHandler;
import handlers.MYTOOLHandler;
import images.OwlSpinner;
import input.TrafficCenter;
import listeners.ACTIVEListener;
import packing.PackData;
import panels.CPScreen;
import panels.LocatorPanel;

/**
 * Panel containing the active packing canvas and associated 
 * tool bars. Size currently fixed. 
 * (Note: in old versions search on 'DISP_CHOICE' to find 
 * some former attempts. Some issues:
 * 
 * 1. Fixed size: (Current setup, 7/08) the frame size is 
 * 'CPBase.imageBufferSize' (possibly adjusted on startup), 
 * so the 'packImage's, pix sizes, etc., can be fixed. 
 * This has display speed advantage, since 'packImage' and 
 * 'activeScreen' sizes are the same, allowing direct copy.
 * 
 * 2. Variable size: If the 'activeScreen' size is allowed 
 * to change, there are several adjustments: 'resize()' keeps 
 * canvas square, have 'canvasPackage' as container for
 * 'activeScreen' because of layout problems, but still can't
 * seem to get 'activeScreen' and 'packImage' in sync. Size
 * adjustments are slow.
 * 
 * There two options for adjusting:
 * 
 * 2a. Change 'packImage' buffer sizes (for all CPScreen's) 
 * to match 'activeScreen' size. This requires call to 
 * 'CPScreen.resetCanvasSize()', which copies 'packImage' to
 * new buffer, etc.. Also have to adjust 'pixWidth' etc.
 * Displays do not look good when small because of pixelation.
 * 
 * 2b. Keep image buffers the same size but repaint using 
 * 'getScaledInstance' in 'ActiveWrapper'. Pixel sizes don't change, but 
 * have to keep 'ActiveWrapper.displayWidth/Height' updated, and
 * 'adjustPt' routine to coordinate mouse clicks with the pixels.
 * Also, would need to adjust font size. Displays look nice, but
 * repaints are too slow. Also, have to adjust 'CPScreen.drawX/YAxis'
 * to get the axes right.  
 *
 */
public class MainFrame extends JFrame {

	private static final long 
	serialVersionUID = 1L;

	// layout sizes
	private static Dimension canvasDim=new Dimension(PackControl.getActiveCanvasSize(),
			PackControl.getActiveCanvasSize());
	public static int scriptWidth=80;
	public static int topHeight=36; // height of tap panel with tool area
	public static int bottomHeight=60; // height of bottom with command line

	// JMenuBar, 
	public JMenuBar mBar; 
	
	// keeping track of current associated 'CPScreen' (and its 'PackData')
	public CPScreen cpScreen;

	public ACTIVEHandler mainToolHandler;
	public ActiveWrapper activeScreen;
	public JPanel scriptTools; // script 'MyTools' here if 'mainFrame' visible
	public JPanel userTools; // TODO: add a user tool bar
	public JPanel topPanel;
	public LocatorPanel locatorPanel;
	public JButton progressBar;
	public File mainMytFile;
	public File mainCursorFile;
	JTextField xyLabel;
	static JPopupMenu cursorMenu; // cursor selection menu

	// Constructor
	/**
	 * Create the principal canvas of an application, used for
	 * displaying circle packings.
	 * @param cps, CPScreen
	 * @param File mainMyT, for loading main toolbar on top
	 */
	public MainFrame(CPScreen cps,File mytFile,File cursorFile) {
		setJMenuBar((mBar=new CPMenuBar()));
		cpScreen=cps;
		mainMytFile=mytFile;
		mainCursorFile=cursorFile;

		setLayout(null); // GUI layout is hard coded
		
		// create 'activeScreen', the actual canvas
		createActiveScreen(); 
		mainToolHandler=activeScreen.getToolHandler();
		PackControl.movableToolHandler=new MYTOOLHandler(null);

		createMainToolBar(); // top toolbar, canvas modes, etc.
		locatorPanel=new LocatorPanel();
		
		cursorMenu=null;
		
		int x1=PackControl.ControlLocation.x+2+
			PackControl.ControlDim1.width;
		int y1=PackControl.ControlLocation.y;
		setLocation(x1,y1);

		addWindowListener(new AFdapter());
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		// in future, want to resize
		setResizable(false);
		updateTitle();
	
		// create owl/progress bar
		progressBar=((OwlSpinner)CPBase.runSpinner).getActiveProgButton();
		progressBar.setToolTipText("Progress bar to show when busy");
		
		this.setSize(new Dimension(canvasDim.width+scriptWidth,
				canvasDim.height+bottomHeight));
	}

	public void layMeOut(Dimension dim) {
		canvasDim=dim;
		layMeOut();
	}
	
	/**
	 * Lays out 'MainFrame' with the active canvas anew, as after
	 * a size change. Adjust 'packImage' size (buffered image).
	 * Uses 'canvasDim' as canvas size.
	 */
	public void layMeOut() {
		this.getContentPane().removeAll();
		int cwide=canvasDim.width;
		int chigh=canvasDim.height;

		// canvas itself
//		activeScreen.setMinimumSize(new Dimension(mainKeyDim,mainKeyDim));
//		activeScreen.setMaximumSize(new Dimension(mainKeyDim,mainKeyDim));
		activeScreen.setBounds(scriptWidth,topHeight,cwide,chigh);
		add(activeScreen);

		// need to adjust stored image areas?
		int curwide=CPBase.pack[0].packImage.getWidth();
		int curhigh=CPBase.pack[0].packImage.getHeight();
		if (cwide!=curwide || chigh!=curhigh) {
			for (int i=0;i<CPBase.NUM_PACKS;i++) {
				CPBase.pack[i].packImage=
					CPBase.pack[i].resetCanvasSize(cwide,chigh);
			}
		}

		// ***** across top: progress bar, top panel 

		// progress bar
		progressBar.setBounds(28,6,24,24);
		add(progressBar);
		
		// top bar
		topPanel.setBounds(scriptWidth,0,cwide,topHeight-1);
		add(topPanel);
		
		// ***** down left side: 'vertScriptBar', toolbar, locator

		// script
		PackControl.vertScriptBar.setBounds(1,topHeight,scriptWidth-2,(chigh-LocatorPanel.locatorHeight)/2-2);
		add(PackControl.vertScriptBar);

		// tool bar (shares left side with script bar)
		PackControl.movableToolHandler.toolBar.setToolTipText("Load/create user-defined mytools");
		PackControl.movableToolHandler.toolBar.setBounds(1,canvasDim.height/2+4,scriptWidth-2,(canvasDim.height-LocatorPanel.locatorHeight)/2-4);
		add(PackControl.movableToolHandler.toolBar);

		// locator
		locatorPanel.setBounds(3,topHeight+chigh-LocatorPanel.locatorHeight,scriptWidth-4,LocatorPanel.locatorHeight);
		add(locatorPanel);
		
		// ***** across bottom, 'activeConsole'  
		PackControl.consoleActive.box.setBounds(2,chigh+topHeight,cwide+scriptWidth-4,bottomHeight);
		add(PackControl.consoleActive.box);
		
		Dimension dim=new Dimension(cwide+scriptWidth+8,chigh+topHeight+topHeight+72); // pad due to fram, menubar, etc.
		setPreferredSize(dim);
		
		// TODO-layouts: should be here?
		pack();
	}
	
	/**
	 * update title bar of 'activeFrame'
	 */
	public void updateTitle() {
		int pnum=getActivePackNum();
		setTitle("Active Packing p"+pnum+": "+
				CPBase.pack[pnum].getPackData().fileName);
	}
	
	/**
	 * Given Dimension of display, what is max 'activeSize' we can set?
	 * @param displayDim, Dimension of display area
	 * @return Dimension
	 */
	public Dimension getMaxDim(Dimension displayDim) {
		int maxWidth=displayDim.width-100-scriptWidth-6;
		int maxHeight=displayDim.height-121-2-PackControl.HeightBuffer;
		int size=(maxWidth<maxHeight) ? maxWidth : maxHeight;
		if (size<100)
			throw new LayoutException("display area too small for 'MainFrame'");
		return (new Dimension(size+scriptWidth+6,size+121));
	}
	
	/**
	 * Given 'MainFrame' height, what is associated 'activeSize'?
	 * @param height
	 * @return int
	 */
	public int getWouldBeSize(int height) {
		return height-121;
	}
	
	/**
	 * Currently, only allow square canvasses, so us max of wide/high.
	 * @param wide, int pixels
	 * @param high, int pixels
	 */
	public static void setCanvasDim(int wide,int high) {
		if (wide<PackControl.MinActiveSize) wide=PackControl.MinActiveSize;
		if (high<PackControl.MinActiveSize) high=PackControl.MinActiveSize;
		if (wide>PackControl.MaxActiveSize) wide=PackControl.MaxActiveSize;
		if (high>PackControl.MaxActiveSize) high=PackControl.MaxActiveSize;
		
		// currently, only allow square
		if (wide>high) wide=high;
		else high=wide;
		
		canvasDim=new Dimension(wide,high);
	}
	
	/**
	 * Get the dimension of the main canvas.
	 * @return
	 */
	public static Dimension getCanvasDim() {
		return canvasDim;
	}
		
	/**
	 * Main menu bar for canvas operations, setting modes
	 * like deletion, zoom, axes, etc., and region for cursor
	 * icons.
	 */
	public void createMainToolBar() {
		topPanel=new JPanel();
		topPanel.setToolTipText("Tools obtained from 'canvas.myt'");
		topPanel.setLayout(new BorderLayout());
		topPanel.setBorder(new LineBorder(Color.black)); //blue,2,false));

		// main mytools (see 'main.myt')
		if (mainToolHandler.toolBar!=null) {
			mainToolHandler.toolBar.setBorder(new EmptyBorder(0,0,0,0));
			topPanel.add(mainToolHandler.toolBar,BorderLayout.WEST);
		}
		
		// Button to bring up cursor options
		JButton cursorButton=new JButton("Active cursors",new ImageIcon(CPBase
				.getResourceURL("/Icons/main/menuPop.png")));
		cursorButton.setFont(new Font(cursorButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		cursorButton.setToolTipText("Optional active cursors and modes");
		cursorButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Component cpt=(Component)e.getSource();
//				if (cursorMenu==null) { // create and show
					cursorMenu=CursorCtrl.cursorMenu((ACTIVEListener)(mainToolHandler.toolListener));
					cursorMenu.show(cpt,40,30);
//				}
//				else if (cursorMenu.isVisible()) // if showing, remove
//					cursorMenu.setVisible(false);
//				else cursorMenu.show(cpt,40,30); // x,y relative to cpt
			}
		});
		topPanel.add(cursorButton,BorderLayout.EAST);
	}

	/**
	 * create the panel containing the canvas itself
	 */
	public void createActiveScreen() {
		activeScreen = new ActiveWrapper(mainMytFile,CPBase.pack[0]);  
		activeScreen.setBorder(new LineBorder(Color.blue,2,false));
		// Note: the canvas itself if drop target to get correct coords.
		new DropTarget(activeScreen,
				new ToolDropListener(activeScreen,cpScreen.getPackNum(),true));
	}	
	
	public void removeScriptTools() {
		PackControl.vertScriptBar.scriptTools.removeAll();
	}
	
	/**
	 * update the label showing the xy coords
	 * @param z
	 */
	public void updateLocPanel(int hes,Complex z) {
		locatorPanel.upDate(hes,z);
	}
	
	/**
	 * Return the active screen
	 * @return 'CPScreen'
	 */
	public CPScreen getCPScreen() {
		return activeScreen.getCPScreen();
	}
	
	/**
	 * Set/change the 'CPScreen' associated with this 'ActiveFrame' 
	 */
	public void setCPScreen(CPScreen cps) {
		activeScreen.setCPScreen(cps);
	}
	
	/**
	 * Which 'PackData' is currently associated? It's help by 'CPScreen'
	 * @return PackData
	 */
	public PackData getPackData() {
		return activeScreen.getCPScreen().getPackData();
	}
	
	/**
	 * What is the active pack number?
	 * @return int
	 */
	public int getActivePackNum() {
		return activeScreen.getCPScreen().getPackNum();
	}

	/**
	 * This puts new image in the activeScreen and updates its 
	 * small version.
	 */
	public void reDisplay() {
		activeScreen.repaint();
		activeScreen.getCPScreen().repaint(); // draw any other canvasses, as well.
	}
	
	/**
	 * update owl/progress button to new state
	 */
	public void swapProgBar() {
		progressBar=((OwlSpinner)CPBase.runSpinner).getActiveProgButton();
		int width=progressBar.getIcon().getIconWidth();
		if (width>78) width=78;
		int height=progressBar.getIcon().getIconHeight();
		progressBar.setBounds(40-width/2,6,width,height);
	}
	
	/**
	 * Start the listener for frame size changes
	 */
	public void initComponentListener() {
		this.addComponentListener(new ResizeAdapter());
	}
	
	/**
     * For closing the activeFrame; if 'PackControl' isn't open,
     * then call for exit; 'no' response, then make PackControl visible
     * and make activeFrame invisible.
    */
	class AFdapter extends WindowAdapter {
	   	public void windowClosing(WindowEvent wevt) {
	   		if (wevt.getID()==WindowEvent.WINDOW_CLOSING) {
	   			if (PackControl.frame.isVisible())
	   				PackControl.activeFrame.setVisible(false);
	   			else {
	   				try {
	   					TrafficCenter.cmdGUI("exit");
	   				} catch (Exception ex){
	   					CirclePack.cpb.errMsg("Problem closing MainFrame");
	   				}
	   				
	   				// reaching here, must not have exited
	   				PackControl.frame.setVisible(true); // open PackControl
	   			}
	   		}
	   	}
	}
	 	
	class ResizeAdapter extends ComponentAdapter {
		  public void componentResized(ComponentEvent e) {
			  try { // in case this is first pass
				  // DEBUG RESIZE
				  PackControl.activeFrame.layMeOut();
				  PackControl.activeFrame.pack();
				  System.err.println("in main resize");
			  } catch (NullPointerException ex) {
				  System.err.println("'MainFrame' resize failed: "+ex.getMessage());
				  
			  }
		  }
	}
 
}