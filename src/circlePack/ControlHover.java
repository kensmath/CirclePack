package circlePack;

import frames.HoverPanel;
import input.CPFileManager;
import allMains.CPBase;


public class ControlHover extends HoverPanel {

	private static final long 
	serialVersionUID = 1L;
	
//	private static CPBase packControl; // parent 
	
	public ControlHover(CPBase pc,int wide,int high) {
		super(wide,high,"CirclePack (dir; "+CPFileManager.CurrentDirectory+")");
//		packControl=pc;
	}

}
