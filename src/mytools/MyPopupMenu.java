package mytools;


import javax.swing.JPopupMenu;

/**
 * Wrapper for JPopupMenus associated with MyToolHandlers;
 * needed just to be able to identify which handler responds 
 * to menu actions.  
 * @author kens
 *
 */
public class MyPopupMenu extends JPopupMenu {

	private static final long 
	serialVersionUID = 1L;
	
	public MyToolHandler parentHandler; // which handler responds?

	// Constructor
	public MyPopupMenu(MyToolHandler mth,String heading) {
		super(heading);
		parentHandler=mth;
	}
}
