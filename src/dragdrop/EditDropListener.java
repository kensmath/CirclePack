package dragdrop;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

import script.StackBox;

/**
 * For MyTool edit tools drag/drop operation. This is the listener for
 * the StackBox targets. It gets the action command string, which is in
 * the mytool name, and sends it to the action listener of the stackbox.
 * @author kens
 *
 */
public class EditDropListener implements DropTargetListener {
	private StackBox theBox;
	private String theActionCmd;

	// Constructor
	public EditDropListener(StackBox box) {
		theBox=box;
	}
	
	public void dragEnter(DropTargetDragEvent event) {}
	
	public void dragExit(DropTargetEvent event) {}
	
	public void dragOver(DropTargetDragEvent event) {}
	
	public void dropActionChanged(DropTargetDragEvent event) {}
	
	public void drop(DropTargetDropEvent event) {
		if (!isDropOK(event)) {
			event.rejectDrop();
			return;
		}
		event.acceptDrop(DnDConstants.ACTION_LINK);
		Transferable transferable = event.getTransferable();
		theActionCmd=null;
		try {
			theActionCmd=(String)transferable.getTransferData(DataFlavor.stringFlavor);
		} catch(Exception e) {}
		if (theActionCmd==null) return; // some failure
		// send action command to stackbox, which creates event
		theBox.editAction(theActionCmd);
	}
	
	public boolean isDropOK(DropTargetDropEvent event) {
		return (event.getDropAction() & DnDConstants.ACTION_LINK)!=0;
	}
}
