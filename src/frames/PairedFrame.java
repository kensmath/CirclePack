package frames;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import allMains.CirclePack;
import canvasses.CursorCtrl;
import canvasses.PairWrapper;
import circlePack.PackControl;
import dragdrop.ToolDropListener;
import exceptions.LayoutException;
import handlers.ACTIVEHandler;
import images.CPIcon;
import images.OwlSpinner;
import input.CPFileManager;
import input.TrafficCenter;
import listManip.FaceLink;
import listManip.NodeLink;
import listeners.ACTIVEListener;
import mytools.MyTool;
import packing.PackData;
import panels.CPScreen;
import util.PopupBuilder;

/**
 * This frame is holds side-by-side canvasses to display 'map'
 * type behavior. The user/script can shift between 'PairedFrame'
 * and 'MainFrame' display, not both.
 */
public class PairedFrame extends JFrame implements ActionListener {

	private static final long 
	serialVersionUID = 1L;	

	private static Box fullPane;
	public static boolean mapConnection=true;
	
	// layout sizes; currently, set to square
	static Dimension canvasDim=new Dimension(PackControl.getPairedCanvasSize()+2,
			PackControl.getPairedCanvasSize()+2);
	static int scriptWidth=80;
	static int topHeight=36; // height of top panel with tool area
	static int bottomHeight=44; // height of bottom panels with command line or user tools
	
	// various children
	private static CPScreen domainCPS;
	private static CPScreen rangeCPS;
	private JMenuBar pairBar;
	public JPanel domTopPanel;
	public JPanel ranTopPanel;
	public Box midBox;
	public JPanel telePanel;
	public PairWrapper domainScreen;
	public PairWrapper rangeScreen;
	private JComboBox<String> domainCB;
	private JComboBox<String> rangeCB;
	private JButton progressBar;
	
	static MyTool teleToolYES;
	static MyTool teleToolNO;
	static CPIcon teleNO=new CPIcon("/GUI/teleNO.png");
	static CPIcon teleYES=new CPIcon("/GUI/teleYES.png");

	static String []pstrs={"P0","P1","P2"};
	
	// Constructor
	public PairedFrame(int dnum,int rnum) {
		super();
		setJMenuBar((pairBar=new CPMenuBar()));
		fullPane=Box.createHorizontalBox();
		this.add(fullPane);
		
		// augment menubar for screendump
		JMenu dumpMenu=new JMenu("Screendump");
		JMenuItem dumpAction=new JMenuItem("Screendump");
	    dumpAction.setActionCommand("pairDump");
	    dumpAction.addActionListener(this);
	    dumpMenu.add(dumpAction);
		pairBar.add(dumpMenu);

		addWindowListener(new WPAdapter());
		setTitle("Mapping Window:  P"+dnum+"  P"+rnum);
		domainCPS=CPBase.pack[dnum];
		rangeCPS=CPBase.pack[rnum];
		PackControl.canvasRedrawer.changeDomain(dnum);
		PackControl.canvasRedrawer.changeRange(rnum);
		initGUI();
		setResizable(false);
	}
	
	/** 
	 * GUI initiation called only during 'CirclePack' startup:
	 * create the panels, etc. here, but add them in 'layMeOut'.
	 */
	private void initGUI() {
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		Dimension pcDim=new Dimension(45,26);
		
		// ***************	left side: domain  **************
		
		// build 'domTopPanel'
		domTopPanel=new JPanel(new BorderLayout());
		domTopPanel.setBorder(new LineBorder(Color.black));
	
		// create domain canvas (needed below)
		domainScreen = new PairWrapper(CPFileManager.getMyTFile("canvas.myt"),0);
		domainScreen.mapFrame=this;
		// this is a drop target, but the listener will have to update
		//   with the current occupying packing.
		new DropTarget(domainScreen,
				new ToolDropListener(domainScreen,domainCPS.getPackNum(),true));

		// domain 'ActiveHandler' toolBar
		final ACTIVEHandler domToolHandler=domainScreen.getToolHandler();
		if (domToolHandler.toolBar!=null) {
			domToolHandler.toolBar.setBorder(new EmptyBorder(0,0,0,0));
			domTopPanel.add(domToolHandler.toolBar,BorderLayout.WEST);
		}

		// Button to bring up cursor options
		JButton dcursorButton=new JButton("Cursors",new ImageIcon(CPBase
				.getResourceURL("/Icons/main/menuPop.png")));
		dcursorButton.setFont(new Font(dcursorButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		dcursorButton.setToolTipText("Optional active cursors and modes");
		dcursorButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Component cpt=(Component)e.getSource();
				JPopupMenu jpm=CursorCtrl.cursorMenu((ACTIVEListener)(domToolHandler.toolListener));
				jpm.show(cpt,40,30); // x,y relative to cpt
			}
		});
		
		// pack choice in topPanel
		Box domainInfo =Box.createHorizontalBox();
		
		// cursor choice
		domainInfo.add(dcursorButton);
		
		// pack choice
		domainCB = new JComboBox<String>(pstrs);
		domainCB.setSelectedIndex(0);
		domainCB.addActionListener(this);
		domainCB.setToolTipText("Choose which packing");
		domainCB.setPreferredSize(pcDim);
		domainCB.setMaximumSize(pcDim);
		domainInfo.add(domainCB,BorderLayout.EAST);

		domainInfo.setAlignmentX((float)1.0);
		domTopPanel.add(domainInfo,BorderLayout.EAST);

		// ***************	right side: range	**************

		// build 'domTopPanel'
		ranTopPanel=new JPanel(new BorderLayout());
		ranTopPanel.setBorder(new LineBorder(Color.black)); //blue,2,false));

		// create range canvas (needed below)
		rangeScreen = new PairWrapper(CPFileManager.getMyTFile("canvas.myt"),1);
		rangeScreen.mapFrame=this;
		// this is a drop target, but the listener will have to update
		//   with the current occupying packing.
		new DropTarget(rangeScreen,
				new ToolDropListener(rangeScreen,rangeCPS.getPackNum(),true));

		// range 'ActiveHandler' toolBar
		final ACTIVEHandler ranToolHandler=rangeScreen.getToolHandler();
		if (ranToolHandler.toolBar!=null) {
			ranToolHandler.toolBar.setBorder(new EmptyBorder(0,0,0,0));
			ranTopPanel.add(ranToolHandler.toolBar,BorderLayout.WEST);
		}
		
		// Button to bring up cursor options
		JButton rcursorButton=new JButton("Cursors",new ImageIcon(CPBase
				.getResourceURL("/Icons/main/menuPop.png")));
		rcursorButton.setFont(new Font(rcursorButton.getFont().toString(),Font.ROMAN_BASELINE+Font.BOLD,10));
		rcursorButton.setToolTipText("Optional active cursors and modes");
		rcursorButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Component cpt=(Component)e.getSource();
				JPopupMenu jpm=CursorCtrl.cursorMenu((ACTIVEListener)(ranToolHandler.toolListener));
				jpm.show(cpt,40,30); // x,y relative to cpt
			}
		});
		
		// range info panel
		Box rangeInfo = Box.createHorizontalBox();
		
		// cursor choice
		rangeInfo.add(rcursorButton);
		
		// pack choice 
		rangeCB = new JComboBox<String>(pstrs);
		rangeCB.setSelectedIndex(1);
		rangeCB.addActionListener(this);
		rangeCB.setToolTipText("Choose which packing");
		rangeCB.setPreferredSize(pcDim);
		rangeCB.setMaximumSize(pcDim);
		rangeInfo.add(rangeCB,BorderLayout.EAST);

		rangeInfo.setAlignmentX((float)1.0);
		ranTopPanel.add(rangeInfo,BorderLayout.EAST);

		// *************** prepare teleTool ***************8
		teleToolYES=new MyTool(new CPIcon("GUI/teleYES.png"),null,
				"toggleTele",null,"Toggle coupling of packings on/off",
				"MISC:",false,this,(PopupBuilder)null);
		teleToolNO=new MyTool(new CPIcon("GUI/teleNO.png"),null,
				"toggleTele",null,
				"Toggle coupling of packings on/off","MISC:",
				false,this,(PopupBuilder)null);
	}
	
	/**
	 * Called when we need to layout the frame anew, as after
	 * size change. Reset 'pairKeyDim' and 'mapCanvasSize' to
	 * 'canvasSize'
	 * @param canvasSize Dimension,
	 */
	public void layMeOut(Dimension canDim) {
		setCanvasDim(canDim.width,canDim.height);
		colorBorders();
		layMeOut();
	}
	
	/**
	 * This is where the layout is created or recreated (as after a
	 * size change).
	 * 
	 * Overall, its a horizontal Box layout: domBox, midBox, ranBox.
	 * 
	 * domBox is a vertical Box layout, with domTopPanel, domCanvas,
	 * and 'pairConsole'.
	 * 
	 * ranBox is a vertical Box layout, with ranTopPanel, ranCanvas,
	 * and 'movableToolHandler' (which is shared with MainFrame).
	 */
	public void layMeOut() {

		// clear out the old
		fullPane.removeAll();
//		Container pane=this.getContentPane();

		// Overall, 3 horizontal boxes
//		fullPane.setLayout(new BoxLayout(fullPane,BoxLayout.LINE_AXIS));
		
		Dimension topDims=new Dimension(canvasDim.width,topHeight);
		Dimension bottomDims=new Dimension(canvasDim.width,bottomHeight);
		
		// *********** domain stuff is in vertical box
		Box domStack = Box.createVerticalBox();
		
		// domain top panel
		domTopPanel.setPreferredSize(topDims);
		domTopPanel.setMaximumSize(topDims);
		domStack.add(domTopPanel);

		// domain canvas
		domainScreen.setPreferredSize(canvasDim);
		domainScreen.setMaximumSize(canvasDim);
		domStack.add(domainScreen);
		
		// 'pairConsole' (below the domain canvas)
		PackControl.consolePair.box.setPreferredSize(new Dimension(canvasDim.width,bottomHeight));
		PackControl.consolePair.box.setMaximumSize(new Dimension(canvasDim.width,bottomHeight));
		domStack.add(PackControl.consolePair.box);

		// *********** midBox has progress, script, teleTool
		midBox=Box.createVerticalBox();
		
		// owl/progress button
		progressBar=((OwlSpinner)CPBase.runSpinner).getPairProgButton();
		progressBar.setToolTipText("Progress bar to show when busy");
		progressBar.setAlignmentX(CENTER_ALIGNMENT);
		progressBar.setPreferredSize(new Dimension(scriptWidth,topHeight-1)); // 24
		progressBar.setMaximumSize(new Dimension(scriptWidth,topHeight-1));
		progressBar.setMinimumSize(new Dimension(scriptWidth,24));
		midBox.add(progressBar);
		
		// script area
		PackControl.vertScriptBar.setPreferredSize(
				new Dimension(scriptWidth,canvasDim.height));
		PackControl.vertScriptBar.setPreferredSize(new Dimension(scriptWidth,canvasDim.height));
		PackControl.vertScriptBar.setMaximumSize(new Dimension(scriptWidth,canvasDim.height));
		PackControl.vertScriptBar.setAlignmentX(CENTER_ALIGNMENT);
		midBox.add(PackControl.vertScriptBar);
		
		// teleTool
		telePanel=new JPanel();
		if (mapConnection)
			telePanel.add(teleToolYES);
		else
			telePanel.add(teleToolNO);
		telePanel.setPreferredSize(new Dimension(scriptWidth,bottomHeight));
		telePanel.setMaximumSize(new Dimension(scriptWidth,bottomHeight));
		telePanel.setAlignmentX(CENTER_ALIGNMENT);
		midBox.add(telePanel);

		// *********** range stuff is in vertical box
		Box rangeStack = Box.createVerticalBox();

		// top panel
		ranTopPanel.setPreferredSize(topDims);
		ranTopPanel.setMaximumSize(topDims);
		rangeStack.add(ranTopPanel);

		// canvas
		rangeScreen.setPreferredSize(canvasDim);
		rangeScreen.setMaximumSize(canvasDim);
		rangeScreen.setMinimumSize(canvasDim);
		rangeStack.add(rangeScreen);
		
		// 'movableToolHandler' 
		PackControl.movableToolHandler.toolBar.
			setToolTipText("Load/create user-defined mytools");
		PackControl.movableToolHandler.toolBar.
			setPreferredSize(bottomDims);
		PackControl.movableToolHandler.toolBar.
			setMaximumSize(bottomDims);
		PackControl.movableToolHandler.toolBar.setBorder(new LineBorder(Color.BLACK));
		rangeStack.add(PackControl.movableToolHandler.toolBar);

		// ************* put the three boxed together
		// TODO: try adjusting high to get rid of wrinkles, get better border
		int high=topDims.height+canvasDim.height+bottomDims.height;
		domStack.setPreferredSize(new Dimension(canvasDim.width,high));
		domStack.setMaximumSize(new Dimension(canvasDim.width,high));
		fullPane.add(domStack);
		
		midBox.setPreferredSize(new Dimension(scriptWidth,high));
		midBox.setMaximumSize(new Dimension(scriptWidth,high));
		fullPane.add(midBox);
		
		rangeStack.setPreferredSize(new Dimension(canvasDim.width,high));
		rangeStack.setMaximumSize(new Dimension(canvasDim.width,high));
		fullPane.add(rangeStack);
		
		// TODO-layout: something different here between first time this
		//   frame is opened and subsequent times.
		Dimension dim=new Dimension(2*canvasDim.width+scriptWidth+2,high);
		fullPane.setPreferredSize(dim);
		fullPane.setSize(dim);
		fullPane.validate();
		
		this.add(fullPane);
		
		pack();
	}

	public void colorBorders() {
//System.err.println("colorBorders");
		int pnum=CirclePack.cpb.getActivePackNum();
		if (pnum==domainCPS.getPackNum())
			domainScreen.setBorder(new LineBorder(Color.green,3,false));
		else
			domainScreen.setBorder(new LineBorder(Color.gray,3,false));
		if (pnum==rangeCPS.getPackNum())
			rangeScreen.setBorder(new LineBorder(Color.green,3,false));
		else 
			rangeScreen.setBorder(new LineBorder(Color.gray,3,false));
	}
	
	/**
	 * Given Dimension of display, what is max 'mapCanvasSize'?
	 * @param displayDim, Dimension of display area
	 * @return Dimension
	 */	
	public Dimension getMaxDim(Dimension displayDim) {
		int maxWidth=(displayDim.width-10-scriptWidth-100)/2; // 100 leaves room for PackControl
		int maxHeight=displayDim.height-88-PackControl.HeightBuffer;
		int size=(maxWidth<maxHeight) ? maxWidth : maxHeight;
		if (size<100)
			throw new LayoutException("display area too small for 'PairedFrame'");
		return (new Dimension(2*size+scriptWidth+10,size+88));
	}
	
	/**
	 * Given 'PairedFrame' height, what is associated 'mapCanvasSize'?
	 * @param height
	 * @return int
	 */
	public int getWouldBeSize(int height) {
		return height-88;
	}
	
	/**
	 * Currently, only allow square canvasses, so us max of wide/high.
	 * Domain/range have same dimension.
	 * @param wide, int pixels
	 * @param high, int pixels
	 */
	public void setCanvasDim(int wide,int high) {
		if (wide<PackControl.MinMapSize) wide=PackControl.MinMapSize;
		if (high<PackControl.MinMapSize) high=PackControl.MinMapSize;
		if (wide>PackControl.MaxMapSize) wide=PackControl.MaxMapSize;
		if (high>PackControl.MaxMapSize) high=PackControl.MaxMapSize;
		
		// currently, only allow square
		if (wide>high) wide=high;
		else high=wide;
		
		canvasDim=new Dimension(wide,high);
	}
	
	/**
	 * Get the dimension of the domain/range canvasses.
	 * @return
	 */
	public static Dimension getCanvasDim() {
		return canvasDim;
	}
	
	public void swapProgBar() {
		progressBar=((OwlSpinner)CPBase.runSpinner).getPairProgButton();
//		int width=progressBar.getIcon().getIconWidth();
//		if (width>78) width=78;
//		int height=progressBar.getIcon().getIconHeight();
		midBox.remove(0);
		progressBar.setToolTipText("Progress bar to show when busy");
		progressBar.setAlignmentX(CENTER_ALIGNMENT);
		progressBar.setPreferredSize(new Dimension(scriptWidth-2,topHeight-1)); // 24
		progressBar.setMaximumSize(new Dimension(scriptWidth-2,topHeight-1));
		progressBar.setMinimumSize(new Dimension(scriptWidth-2,24));
		midBox.add(progressBar,0);
//		progressBar.setBounds(canvasDim.width+42-width/2,6,width,height);
	}
	
	/**
	 * Check if 'PairWrapper' is domain or range wrapper
	 * @param pw
	 * @return 0 for domain, 1 for range, -1 for neither
	 */
	public int pwIsDomain(PairWrapper pW) {
		if (pW.equals((Object)domainScreen)) return 0;
		if (pW.equals((Object)rangeScreen)) return 1;
		return -1;
	}
	
	/**
	 * Check status of the other 'PairWrapper' packing.
	 * @param pW
	 * @return 'status' of other packing
	 */
	public boolean otherExists(PairWrapper pW) {
		int me=pwIsDomain(pW);
		if (me==0)
			return rangeCPS.getPackData().status;
		if (me==1)
			return domainCPS.getPackData().status;
		else return false;
	}
	
	/**
	 * Called due to mouse click in pWrapper if 'mapConnection' is true 
	 * and mode is 'defaultMode'. 
	 * We find circles (cf_flag=true) (or faces, cf_flag=false) in one packing and 
	 * display them as filled in both packings. NOTE: these draws are done in the 
	 * underlying 'packImage's, but are not repainted in the small canvasses.
	 * @param range
	 * @param cp_flag, boolean: true=circles, false=faces
	 * @param x
	 * @param y
	 */
	public void drawCall(PairWrapper pWrapper,boolean cf_flag,double x,double y) {
		String zpt=new String("z "+x+" "+y);
		PackData p=domainCPS.getPackData();
		PackData q=rangeCPS.getPackData();
		if (pwIsDomain(pWrapper)!=0) { // else swap
			PackData hold=q;
			q=p;
			p=hold;
		}
		if (cf_flag) { // circles
			NodeLink vertlist=new NodeLink(p,zpt);
			Iterator<Integer> vlist=vertlist.iterator();
			int v;
			while (vlist.hasNext()) {
			  v=(Integer)vlist.next();
			  p.circle_map_action(q,v,true);
			}
		}
		else { // faces
			FaceLink facelist=new FaceLink(p,zpt);
			Iterator<Integer> flist=facelist.iterator();
			int f;
			while (flist.hasNext()) {
				f=(Integer)flist.next();
				p.face_map_action(q,f,true);
			}
		}
		domainScreen.repaint();
		rangeScreen.repaint();
	}
	
	public void removeScriptTools() {
		PackControl.vertScriptBar.scriptTools.removeAll();
	}
	
	public PackData getDomainPack() {
		return domainCPS.getPackData();
	}
	
	public PackData getRangePack() {
		return rangeCPS.getPackData();
	}
	
	public CPScreen getDomainCPS() {
		return domainCPS;
	}
	
	public CPScreen getRangeCPS() {
		return rangeCPS;
	}
	 
	public int getDomainNum() {
		return domainCPS.getPackNum();
	}
	 
	public int getRangeNum() {
		return rangeCPS.getPackNum();
	}

	/** set 'tele' state: communicate actions between the
	 * domain/range canvasses.
	 * @param setON, boolean: true, then turn on, false, off
	 */
	public void setTeleState(boolean setON) {
		telePanel.removeAll();
		telePanel.revalidate();
	  	if (!setON && mapConnection) { // turn off
  			mapConnection=false;
  			telePanel.add(teleToolNO);
	  	}
	  	else if (setON && !mapConnection) {
  			mapConnection=true;
  			telePanel.add(teleToolYES);
	  	}
	  	telePanel.repaint();
	}
	
	public void actionPerformed(ActionEvent e){
		
		// one of combo boxes??
		if (e.getSource() instanceof JComboBox<?>) {
			JComboBox<?> cb = (JComboBox<?>)e.getSource();
			int i=cb.getSelectedIndex();
			if (cb==domainCB) {
				domainCPS=CPBase.pack[i];
				PackControl.canvasRedrawer.changeDomain(i);
				colorBorders();
				setTitle("Mapping Window:  P"+i+"  P"+getRangeNum());
		  		domainScreen.repaint();
			}
			else if (cb==rangeCB) {
				rangeCPS=CPBase.pack[i];
				PackControl.canvasRedrawer.changeRange(i);
				colorBorders();
				setTitle("Mapping Window:  P"+getDomainNum()+"  P"+i);
		  		rangeScreen.repaint();
			}
			return;
		}
		
	 	String command = e.getActionCommand();

	  	if (command.endsWith("pairDump")) {
	    	PackControl.screenCtrlFrame.imagePanel.storeCPImage(null);
	  	}
	  	else if (command.endsWith("toggleTele")) {
	  		setTeleState(!mapConnection);
	  	}

	}

	/**
	 * Select packing number in domain ComboBox, triggers action
	 * @param dnum
	 */
	public void setDomainNum(int dnum) {
		domainCB.setSelectedIndex(dnum);
	}
	
	/**
	 * Select packing number in range ComboBox, triggers action
	 * @param dnum
	 */
	public void setRangeNum(int rnum) {
		rangeCB.setSelectedIndex(rnum);
	}

	/**
     * For closing the pairedFrame; if 'PackControl' isn't open,
     * then call for exit; else just make pairedFrame invisible.
     * This what a 'minimal' user would expect.
     */
	class WPAdapter extends WindowAdapter {
	   	public void windowClosing(WindowEvent wevt) {
	   		if (wevt.getID()==WindowEvent.WINDOW_CLOSING) {
	   			if (PackControl.frame.isVisible())
	   				PackControl.mapPairFrame.setVisible(false);
	   			else {
	   				try {
	   					TrafficCenter.cmdGUI("exit");
	   				} catch (Exception ex){
	   					CirclePack.cpb.errMsg("Problem closing PairedFrame");
	   				}
	   			}
	   			return;
	   		}
	   	}
	}

}
