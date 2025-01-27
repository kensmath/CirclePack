package allMains;

import circlePack.PackControl;
import circlePack.ShellControl;

/**
 * Note: all 'CirclePack' actually does is start 'PackControl'
 * or 'ShellControl', which extend 'CPBase' and add additional 
 * variables. 
 * 
 * 'PackControl' in turn kicks off various other objects. 
 * This is necessary, e.g., so the script frame is ready, and
 * only then is 'startCirclePack' called to load the initial
 * script.
 */
public class CirclePack {

	@SuppressWarnings("unused")
	private static final long 
	serialVersionUID = 1L;
	
	public static CPBase cpb;   // the head of this program
	
	// Constructor(s)
	public CirclePack(int mode) {
		
		if (mode==0) { // standalone, no GUI
			
			cpb=new ShellControl();
			CPBase.GUImode=0;
			ShellControl sC=(ShellControl)cpb;
			
			sC.initShellControl();
		}
		else {		// must initiate 'PackControl' first
			cpb=new PackControl();
			CPBase.GUImode=1;
			PackControl mW=(PackControl)cpb;
		
			// after instantiated, we can initialize GUI, etc.
			mW.initPackControl();
		}
	}
	
	/** 
	 * This is actual start of 'CirclePack'. We had to wait
	 * for the script frame and other things to get started
	 * so an initial script could be loaded (if called for).
	*/
	public void startCirclePack() {
		try {	
			CPBase.scriptManager.defaultTag=CPBase.scriptManager.getTagImage("myCPtag.jpg");

			// initial script? load and carry out any execute-on-load command 
			if (CPBase.initialScript!=null) {
				int reslt=CPBase.scriptManager.getScript(CPBase.initialScript,
						CPBase.initialScript,true);
				if (reslt==0) {// load default script
					String tmpname=CPBase.scriptManager.createDefaultScript();
					reslt=CPBase.scriptManager.loadNamedScript(tmpname,tmpname,false); 
				}
			}

			// else load default script
			else {
				String tmpname=CPBase.scriptManager.createDefaultScript();
				CPBase.scriptManager.loadNamedScript(tmpname,tmpname,false); 
			}
			
			// start socket?
			if (CPBase.socketActive) {
				PackControl.startCPSocketServer(CPBase.cpSocketPort);
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	
  }
}
