package allMains;

import java.awt.EventQueue;

import circlePack.PackControl;

/**
 * The 'main' here is called by 'SplashMain' after it displays
 * the splash screen. This class processes any command line 
 * arguments. 'CirclePack' is initiated in a static call, but
 * then 'startCirclePack' gets things going.
 */
public class CP_after_Splash {
	
	// static start for 'CirclePack'
	public static CirclePack circlePack=new CirclePack(1);
	
	public static void main(String[] args) {
		// parse command line argument
		if (args.length>=1) {
			for (int j=0;j<args.length;j++) {
				if (args[j].equals("-dir") && args.length>j+1) {
					CPBase.directory=args[j+1];
					j++;
				}
				else if (args[j].startsWith("-scr") && args.length>j+1) {
					CPBase.initialScript=args[j+1];
					j++;
				}
				else if (args[j].equals("-socket")) { // want a command socket 
					CPBase.socketActive=true;
					int prt=3736;
					try {
						prt=Integer.parseInt(args[j+1]);
						CPBase.cpSocketPort=prt; // port for command socket
						j++;
					} catch (Exception ex) {
						prt=3736;
					}
				}
				else if (j==args.length-1) { // last string, no flag, take as script
					CPBase.initialScript=args[j];
				}
			}
		}
		
		EventQueue.invokeLater(new Runnable(){
			public void run(){
				System.out.println("CirclePack started\n");
				circlePack.startCirclePack();
				PackControl.scriptManager.populateDisplay();
			}
		});
	}
}
