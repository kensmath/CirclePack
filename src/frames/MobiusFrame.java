package frames;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;
import handlers.MOBIUSHandler;
import handlers.PACKMOBHandler;
import images.CPIcon;
import input.CPFileManager;
import input.CommandStrParser;
import komplex.EdgePair;
import mytools.MyTool;
import packing.PackData;
import util.PopupBuilder;

/**
 * This frame displays Mobius transformation icons for the active pack
 * (its "side pairings") and general purpose Mobius transformations.
 * @author kens
 */
public class MobiusFrame extends JFrame implements ActionListener {

	private static final long 
	serialVersionUID = 1L;
	
	public PACKMOBHandler sidePairHandler; // sidepairing mobius for active pack
	public MOBIUSHandler mobiusHandler; // General mobius transform tools

	private JPanel genMobPanel;

	public MobiusFrame() {
		super();
		this.addWindowListener(new WAdapter());
		try {
			File mobLoc=CPFileManager.getMyTFile(".mobiustools.myt");
			if (mobLoc.exists()) mobiusHandler = new MOBIUSHandler(mobLoc);
			else mobiusHandler = new MOBIUSHandler(null);
		} catch (Exception ex){}
		try {
			File mobLoc=CPFileManager.getMyTFile(".sidepairtools.myt");
			if (mobLoc.exists()) sidePairHandler = new PACKMOBHandler(mobLoc);
			else sidePairHandler = new PACKMOBHandler(null);
		} catch (Exception ex){}
		initGUI();
	}
	
	private void initGUI() {
		this.setTitle("Mobius Transformations");
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		Container pane=getContentPane();
		pane.setLayout(null);
			
		// side-pair panel
		JPanel sidePairs=new JPanel(null);
		sidePairs.setBorder(BorderFactory.
				createTitledBorder("Active Pack Side Pairings"));
		sidePairHandler.toolBar.setBounds(5,20,PackControl.ControlDim1.width-20,30);
		sidePairs.add(sidePairHandler.toolBar);
			
		JButton button = new JButton();
		button.setText("Update (only)");
		button.setActionCommand("reset");
		button.addActionListener(this);
		button.setBounds(5,55,150,20);
		sidePairs.add(button);
		
		button=new JButton();
		button.setText("layout and update");
		button.setActionCommand("layout_reset");
		button.addActionListener(this);
		button.setBounds(160,55,180,20);
		sidePairs.add(button);

		sidePairs.setBounds(0,0,PackControl.ControlDim1.width-5,80);
		pane.add(sidePairs);
			
		// bottom panel	
		genMobPanel = new JPanel(null);
		genMobPanel.setBorder(BorderFactory.
				createTitledBorder("General Transformations"));
		mobiusHandler.toolBar.setBounds(5,20,PackControl.ControlDim1.width-20,30);
		genMobPanel.add(mobiusHandler.toolBar);
		genMobPanel.setBounds(0,85,PackControl.ControlDim1.width-10,55);
		pane.add(genMobPanel);

		pack();
		this.setSize(new Dimension(PackControl.ControlDim1.width,180));
	}
	
	/**
	 * Put side-pair icons (for the active pack) on the sidepair toolbar.
	 * @return 0 if there are no 'sidePairs' (or 'redChain' or 'firstRedEdge')
	 */
	public int loadSidePairs() {
		int count=0;
		PackData packData=CirclePack.cpb.getActivePackData();
		if (packData.redChain==null || packData.firstRedEdge==null 
				|| packData.getSidePairs()==null || packData.getSidePairs().size()==0) return 0;
		sidePairHandler.wipeoutTools();
		sidePairHandler.clearToolBar();
		Iterator<EdgePair> sides=packData.getSidePairs().iterator();
		EdgePair ep=null;
		while (sides.hasNext()) {
			  ep=(EdgePair)sides.next();
			  // yes, this is part of a side-pairing, add its tool 
			  if (ep.label!=null && ep.pairedEdge!=null) {
				  String letterIcon=new String("mobius/mob_"+ep.label+".png");
				  String mobCmd=new String("appMob "+ep.mob.a.x+" "+ep.mob.a.y+" "+ep.mob.b.x+" "+ep.mob.b.y+" "+
						  ep.mob.c.x+" "+ep.mob.c.y+" "+ep.mob.d.x+" "+ep.mob.d.y);
				  MyTool but=new MyTool(new CPIcon(letterIcon),mobCmd,null,null,
						  new String("Drop: Mobius transformation for side-pairing '"+ep.label+"'"),
						  "MOBIUS:",true,this,(PopupBuilder)null);
				  sidePairHandler.addTool(but);
				  count++;
			  }
		}		
		sidePairHandler.repopulateTools();
		return count;
	}
	
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		try {
			if (!command.equals("reset")) 
				loadSidePairs();
			else if (command.equals("layout_reset")) {
				CirclePack.cpb.getActivePackData().fillcurves();
				CirclePack.cpb.getActivePackData().comp_pack_centers(false,false,2,
						CommandStrParser.LAYOUT_THRESHOLD);
				loadSidePairs();
			}
		} catch(Exception ex) {
				throw new ParserException("'layout' failed: "+ex.getMessage());
		}
	}
	
    class WAdapter extends WindowAdapter {
    	public void windowClosing(WindowEvent wevt) {
    		if (wevt.getID()==WindowEvent.WINDOW_CLOSING)
    			PackControl.mobiusFrame.setVisible(false);
    	}
    }
    
}
