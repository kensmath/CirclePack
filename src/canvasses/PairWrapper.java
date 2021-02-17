package canvasses;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.File;

import allMains.CPBase;
import circlePack.PackControl;
import frames.PairedFrame;

/**
 * An 'ActiveWrapper' for canvases in the "PairedFrame".
 * We override many mouse operations.
 * @author kens
 */
public class PairWrapper extends ActiveWrapper {

	private static final long 
	serialVersionUID = 1L;
		
	final int LEFTCANVAS=0;
	final int RIGHTCANVAS=1;
	private int callerType; // 0=left, 1=right
	public PairedFrame mapFrame;
	
	// Constructor
	public PairWrapper(File myToolFile,int cT) {
		// cheap trick: 'callerType' used as packnum on startup
		super(myToolFile,CPBase.cpScreens[cT]); 
		callerType=cT;
	}
	
	/**
	 * Is the map pair connection on?
	 * @return, boolean
	 */
	private boolean areConnected() {
		return PairedFrame.mapConnection;
	}
	
	private void getSource() {
		if (callerType==LEFTCANVAS) cpScreen=PackControl.mapPairFrame.getDomainCPS();
		else this.cpScreen=PackControl.mapPairFrame.getRangeCPS();
	}

	/**
	 * Throw in correct 'CPScreen' image
	 */
	public void paintComponent(Graphics g) {
		getSource();
		g.drawImage(cpScreen.packImage.getScaledInstance(getWidth(),getHeight(), 
				Image.SCALE_SMOOTH),0,0,getWidth(),getWidth(),null);
	}
	
	// override mouse 1/2 clicks in case 'mapConnection' is set,
	//   mode is default, and other packing exists; else default
	//   to 'ActiveWrapper' behavior.
	public void mouseClicked(MouseEvent e) {
		if (e.getID()==MouseEvent.MOUSE_CLICKED && areConnected() && 
				activeMode==defaultMode && mapFrame.otherExists(this) &&
				(e.getButton()==MouseEvent.BUTTON1 
						|| e.getButton()==MouseEvent.BUTTON2)) {
			Point2D.Double pt2D=cpScreen.pt2RealPt(e.getPoint(),getWidth(),getHeight());
			if (e.getButton() == MouseEvent.BUTTON2 || (e.getButton() == MouseEvent.BUTTON1 &&
					(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
					ActionEvent.CTRL_MASK)) 
				mapFrame.drawCall(this,false,pt2D.x,pt2D.y);
			else if (e.getButton() == MouseEvent.BUTTON1) 
				mapFrame.drawCall(this,true, pt2D.x, pt2D.y);
//			repaint();
		}
		else super.mouseClicked(e); // let 'ActiveWrapper' handle
	}

}

