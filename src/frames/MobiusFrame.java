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
import dcel.SideData;
import dcel.PackDCEL;
import exceptions.ParserException;
import handlers.MOBIUSHandler;
import handlers.PACKMOBHandler;
import images.CPIcon;
import input.CPFileManager;
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
	 * @return 0 if 'sidePairs' (or 'redChain' or 'firstRedEdge') is missing
	 */
	public int loadSidePairs() {
		int count=0;
		PackData p=CirclePack.cpb.getActivePackData();
		PackDCEL pdcel=p.packDCEL;
		sidePairHandler.wipeoutTools();
		sidePairHandler.clearToolBar();
		
		if (pdcel.redChain==null || pdcel.pairLink==null ||
				pdcel.pairLink.size()<2)
			return 0;
		Iterator<SideData> sides=pdcel.pairLink.iterator();
		SideData sd=sides.next(); // first spot empty
		while (sides.hasNext()) {
			sd=(SideData)sides.next();
			// yes, this is part of a side-pairing, add its tool 
			if (sd.label!=null && sd.mateIndex>0) {
				String letterIcon=new String("mobius/mob_"+sd.label+".png");
				String mobCmd=new String("appMob "+sd.mob.a.x+" "+
						sd.mob.a.y+" "+sd.mob.b.x+" "+sd.mob.b.y+" "+
						sd.mob.c.x+" "+sd.mob.c.y+" "+sd.mob.d.x+" "+
						sd.mob.d.y);
				MyTool but=new MyTool(new CPIcon(letterIcon),mobCmd,null,null,
						new String("Drop: Mobius transformation for side-pairing '"+sd.label+"'"),
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
			if (command.equals("reset")) 
				loadSidePairs();
			else if (command.equals("layout_reset")) {
				PackData p=CirclePack.cpb.getActivePackData();
				p.packDCEL.layoutPacking();
				p.fillcurves();
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
