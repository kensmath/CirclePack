package dragdrop;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;

import mytools.MyTool;


/**
 * This starts the drag by recognizing a gesture (i.e., with mouse)
 * that a drag operation has been initiated. It packages up the
 * command (the 'transferable') and notifies the drag/drop system.
 * @author kens
 *
 */
public class ToolGestureListener implements DragGestureListener {
	MyTool theTool;
	
	// Constructor
	public ToolGestureListener(MyTool myTool) {
		theTool=myTool;
	}
	
	public void dragGestureRecognized(DragGestureEvent event) {
		Transferable transferable=new ToolTransferable(theTool);
System.err.println("dragGestureRecognized");		
		event.startDrag(null,transferable,new ToolDragSourceListener());
	}

}
