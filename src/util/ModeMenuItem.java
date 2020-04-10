package util;

import images.CPIcon;

import javax.swing.JMenuItem;

import canvasses.MyCanvasMode;

/**
 * 'MyCanvasMode' objects are created along with a popup
 * menu item; this class allows us to keep track of 
 * the parent mode.
 * @author kens
 *
 */
public class ModeMenuItem extends JMenuItem {

	private static final long 
	serialVersionUID = 1L;
	
	public MyCanvasMode parentMode;
	
	// Constructor
	public ModeMenuItem(MyCanvasMode myM,String text,CPIcon cpIcon) {
		super(text,cpIcon.getImageIcon());
		parentMode=myM;
	}
		
}
