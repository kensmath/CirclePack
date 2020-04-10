package dragdrop;

import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;

/**
 * For MyTool drag/drop operation. This tells source MyTool what 
 * to do after sucessful drop; currently, it does nothing.
 * @author kens
 *
 */
public class ToolDragSourceListener extends DragSourceAdapter {

	// Constructor
	public ToolDragSourceListener() {
		
	}
	
	public void dragDropEnd(DragSourceDropEvent event) {
		// currently don't do anything to source end of drag/drop.
	}
}
