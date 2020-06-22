package canvasses;

import allMains.CPBase;
import circlePack.PackControl;
import packing.PackData;
import panels.CPScreen;

/**
 * I need a manager for canvas repaint events: which packings are showing
 * in which canvasses and what needs to be redrawn after various actions/events.
 * Note that simple single repaints can be called directly; use this manager
 * for more complicated situations.
 *  
 * Each canvas type knows which packing it holds and how to repaint itself.
 * For each packing, we register bitwise which canvasses are displaying it
 *   bit 1: small canvas (at this time, this is automatic)
 *   bit 2: Active canvas (eventually, full-screen or normal mode)
 *   bit 3: pair domain
 *   bit 4: pair range
 * (Depending on efficiency, may eventually prescribe here whether
 * to draw "image" or "scaled image".)
 * @author kens
 *
 */
public class CanvasReDrawManager {

	private static int []canvasRegistry;
	static int SMALLCAN=1;
	static int ACTIVECAN=2;
	static int DOMAINCAN=4;
	static int RANGECAN=8;
	
	// Constructor
	public CanvasReDrawManager(int pnum) {
		canvasRegistry=new int[CPBase.NUM_PACKS];
		for (int i=0;i<CPBase.NUM_PACKS;i++)
			canvasRegistry[i] =1; // each has small canvas
		canvasRegistry[pnum] |= ACTIVECAN; // initially active packing
	}
	
	// reflect a change in which pack is active
	public void changeActive(int pnum) {
		for (int i=0;i<CPBase.NUM_PACKS;i++) 
			canvasRegistry[i] &= ~ACTIVECAN;
		canvasRegistry[pnum] |= ACTIVECAN;
		// DEGUB
//		System.err.println("changeActive,p"+pnum+" ACTIVECAN"+ACTIVECAN);
	}
	
	public void changeActive(PackData p) {
		changeActive(p.packNum);
	}
	
	public void changeActive(CPScreen cps) {
		changeActive(cps.getPackData().packNum);
	}
	
	// After initiation, only called through ComboBox action
	public void changeDomain(int pnum) {
		for (int i=0;i<CPBase.NUM_PACKS;i++) 
			canvasRegistry[i] &= ~DOMAINCAN;
		canvasRegistry[pnum] |= DOMAINCAN;
	}
	
	// After initiation, only called through ComboBox action
	public void changeRange(int pnum) {
		for (int i=0;i<CPBase.NUM_PACKS;i++) 
			canvasRegistry[i] &= ~RANGECAN;
		canvasRegistry[pnum] |= RANGECAN;
	}
	
	/**
	 * Paint all canvasses (or active only) for given packing
	 * @param pnum int, pack number
	 * @param activeOnly boolean: yes means active pack only 
	 */
	public void paintMyCanvasses(int pnum,boolean activeOnly) {
		// in future may need to check: if ((canvasRegistry[pnum] & SMALLCAN)==SMALLCAN)
		if (activeOnly) { 
			if ((canvasRegistry[pnum] & ACTIVECAN) != ACTIVECAN) return;
			PackControl.activeFrame.activeScreen.repaint();
			return;
		}
		if (PackControl.activeFrame.activeScreen.isVisible() && 
				(canvasRegistry[pnum] & ACTIVECAN)==ACTIVECAN)
			PackControl.activeFrame.activeScreen.repaint();
		if (PackControl.mapPairFrame.isVisible()) {
			if ((canvasRegistry[pnum] & DOMAINCAN)==DOMAINCAN) {
				PackControl.mapPairFrame.domainScreen.repaint();
			}
			if ((canvasRegistry[pnum] & RANGECAN)==RANGECAN) {
				PackControl.mapPairFrame.rangeScreen.repaint();
			}
		}
		PackControl.pack[pnum].repaint();
	}
	
	/**
	 * Paint all canvasses (or active only) for given packing
	 * @param p @see packData
	 * @param aO boolean: yes means active pack only 
	 */
	public void paintMyCanvasses(PackData p,boolean aO) {
		paintMyCanvasses(p.packNum,aO);
	}
	
	/**
	 * Paint all canvasses (or active only) for given packing
	 * @param cps @see CPScreen
	 * @param aO boolean: yes means active pack only 
	 */
	public void paintMyCanvasses(CPScreen cps,boolean aO) {
		paintMyCanvasses(cps.getPackNum(),aO);
	}
	
}
