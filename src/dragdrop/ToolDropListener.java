package dragdrop;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.geom.Point2D;

import javax.swing.JPanel;

import allMains.CPBase;
import allMains.CirclePack;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import mytools.MyTool;
import panels.CPScreen;


/**
 * For MyTool drag/drop operation. This is the listener for the targets, 
 * which are currently the active canvas, the three smaller canvasses,
 * and the canvasses of PairFrame. 
 * WhichPackFlag true means that the packing number must be determined 
 * from the target panel, so we have to search for which panel.
 * @author kens
 *
 */
public class ToolDropListener implements DropTargetListener {

	private JPanel theCanvas;
	private String theKey;
	private int thePackNum;
	private boolean whichPackFlag;

	// Constructor
	public ToolDropListener(JPanel canvas,int packnum,boolean active) {
		theCanvas=canvas;
		thePackNum=packnum;
		whichPackFlag=active;
	}
	
	public void dragEnter(DropTargetDragEvent event) {}
	
	public void dragExit(DropTargetEvent event) {}
	
	public void dragOver(DropTargetDragEvent event) {}
	
	public void dropActionChanged(DropTargetDragEvent event) {}
	
	public void drop(DropTargetDropEvent event) {
		if (!isDropOK(event)) {
			event.rejectDrop();
			return;
		}
		event.acceptDrop(DnDConstants.ACTION_LINK);
		Transferable transferable = event.getTransferable();
		theKey=null;
		try {
			theKey=(String)transferable.getTransferData(DataFlavor.stringFlavor);
		} catch(Exception e) {}
		if (theKey==null) return; // some failure
		
		MyTool mytool=(MyTool)CPBase.hashedTools.get(theKey);
		if (mytool!=null) {
			if (mytool instanceof MyCanvasMode) { // just change canvas mode
				if (theCanvas.equals(PackControl.activeFrame.activeScreen)) {
				PackControl.activeFrame.activeScreen.
						activeMode=(MyCanvasMode)mytool;
				}
				else if (theCanvas.equals(PackControl.mapPairFrame.getDomainCPS())) {
					PackControl.mapPairFrame.domainScreen.activeMode=(MyCanvasMode)mytool;
				}
				else if (theCanvas.equals(PackControl.mapPairFrame.getRangeCPS())) {
					PackControl.mapPairFrame.rangeScreen.activeMode=(MyCanvasMode)mytool;
				}
				return;
			}
			// have to find the packing number
			if (whichPackFlag) { 
				if (theCanvas.equals(PackControl.activeFrame.activeScreen)) {
					thePackNum=CirclePack.cpb.getActivePackData().packNum;
				}
				else if (theCanvas.equals(PackControl.mapPairFrame.getDomainCPS())) {
					thePackNum=PackControl.mapPairFrame.getDomainNum();
				}
				else if (theCanvas.equals(PackControl.mapPairFrame.getRangeCPS())) {
					thePackNum=PackControl.mapPairFrame.getRangeNum();
				}
			}
			// check command for variables '#..': Currently check only ' #XY'
			if (mytool.getCommand().contains(" #XY") || mytool.getCommand().contains(" #xy")) {
				Point pt=event.getLocation();
				CPScreen cpS=CPBase.cpScreens[thePackNum];
				Point2D.Double pot=cpS.pt2RealPt(pt, theCanvas.getWidth(),theCanvas.getHeight());
				String subxy=new String(" "+pot.x+" "+pot.y+" ");
				String newCmd=mytool.getCommand().replaceAll(" #XY",subxy).replaceAll(" #xy",subxy);
//				System.err.println("got here: newCmd "+newCmd);
				// NOTE: for spherical packing, parser must convert to real point 
				CPBase.trafficCenter.parseWrapper(newCmd,
						CPBase.cpScreens[thePackNum].getPackData(),false,false,0,null);
				return;
			}
			mytool.execute(CPBase.cpScreens[thePackNum].getPackData());
		}
	}
	
	public boolean isDropOK(DropTargetDropEvent event) {
		return (event.getDropAction() & DnDConstants.ACTION_LINK)!=0;
	}
}
