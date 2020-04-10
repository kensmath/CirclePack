package frames;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;

import allMains.CPBase;
import circlePack.PackControl;


/**
 * Animated flapping owl to serve as activity indicator
 */
public class ActiveOwl implements Runnable {
    private Cursor[] owlcursors;
    private int cursorCount;
    public Component glass; // cache PackControl's glasspane
    PackControl frame;

    // Constructor
    public ActiveOwl(PackControl cpframe) {
    	frame=cpframe;
        owlcursors = new Cursor[10];
		glass=PackControl.frame.getGlassPane();
		Point pt=new Point(0,0);
		Toolkit tk = Toolkit.getDefaultToolkit();
		try {
        owlcursors[0] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap0.png")),
        		pt,"flap0");
        owlcursors[1] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap1.png")),
        		pt,"flap1");
        owlcursors[2] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap2.png")),
        		pt,"flap2");
        owlcursors[3] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap3.png")),
        		pt,"flap3");
        owlcursors[4] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap4.png")),
        		pt,"flap4");
        owlcursors[5] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap5.png")),
        		pt,"flap5");
        owlcursors[6] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap6.png")),
        		pt,"flap6");
        owlcursors[7] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap7.png")),
        		pt,"flap7");
        owlcursors[8] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap8.png")),
        		pt,"flap8");
        owlcursors[9] = tk.createCustomCursor(tk.getImage(CPBase.getResourceURL("/Icons/GUI/Owl_flap9.png")),
        		pt,"flap9");
		} catch (Exception e) {
			e.printStackTrace();
		}
        cursorCount=owlcursors.length;
        
		Thread tr = new Thread(this);
		tr.setPriority(Thread.MAX_PRIORITY);
		tr.start();
    }
    
    public void run() {
    	int click=0;
    	while (glass!=null) {
    		try {
    			Thread.sleep(150);
    		} catch(InterruptedException ex) {}
    		if(glass.isVisible()) {
    	        glass.setCursor(owlcursors[click]);
    	        click=(click+1)%cursorCount;
    		}
    	}   
    }
    
}
