package panels;

import java.awt.Color;
import java.awt.dnd.DropTarget;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import circlePack.PackControl;
import dragdrop.ToolDropListener;
import packing.PackData;

/**
 * The panel for displaying the packing. 
 * 
 * TODO: moving GUI stuff from CPdrawing
 * 
 * @author kstephe2, August 2022
 *
 */
public class CPcanvas extends JPanel implements MouseListener {

	private static final long 
	serialVersionUID = 1L;

	public PackData packData;
	int screenNum; // normally aligns with packData.packNum
    static String []geomAbbrev={" (hyp)"," (eucl)"," (sph)"};  

	// Constructors
	public CPcanvas(int screennum) {
		if (screennum<0 || screennum>=CPBase.NUM_PACKS) 
			screennum=0;
		screenNum=screennum;

		this.setFocusable(true);
		this.setBorder(new LineBorder(Color.BLACK,2,false));
		this.addMouseListener(this);
		
		new DropTarget(this,new ToolDropListener(this,screenNum,false));
	}
		
	public CPcanvas() {
		this(0);
	}
	
	/**
	 * Give string for geometry to label canvas
	 * @return
	 */
	public String getGeomAbbrev(){
		switch (packData.hes) {
			case -1: return new String(" (hyp)");
			case 0:	return new String(" (eucl)");
			default: return new String(" (sph)");
		}
	}
	
	/** 
	 * Return the 'packData' pack index, or -1 if not set
	 * @return int
	 */
	public int getPackNum() {
		if (packData==null)
			return -1;
		return packData.packNum;
	}
	
	// Some mouse events may first be picked off by parents of this CPcanvas
	public void mouseReleased(MouseEvent e) {
		if (e.getClickCount() >= 2) { // double click to change active packing
			try {
			  PackControl.switchActivePack(this.getPackNum());
			} catch (Exception ex) {return;}
		}
		// TODO: doesn't seem to work to bring activeFrame to the top
//		else if (e.getClickCount()>=1) { // click active screen to top
//			PackControl.activeFrame.toFront();
//		}
	}

	// methods required for MouseListener 
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}


}
