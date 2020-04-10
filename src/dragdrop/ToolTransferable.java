package dragdrop;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import mytools.MyTool;


/**
 * For MyTool drag/drop operation. This sets up the transferable
 * data, which currently is just the command string of the MyTool.
 * @author kens
 *
 */
public class ToolTransferable implements Transferable {
	MyTool theTool;
	String theKey;

	// Constructor
	public ToolTransferable(MyTool myTool) {
		theTool=myTool;
	}
	
	public DataFlavor[] getTransferDataFlavors() {
		return flavors;
	}
	
	/**
	 * Can this type of data be dropped?
	 */
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		if (!theTool.isDropable()) return false;
		if (flavor!=DataFlavor.stringFlavor) return false;
		return true;
	}
	
	public Object getTransferData(DataFlavor flavor) throws
	  UnsupportedFlavorException {
		if (flavor==DataFlavor.stringFlavor) return theTool.getKey();
		else 
			throw new UnsupportedFlavorException(flavor);
	}
	
	// Just one flavor we're preparing for transfer
	public static DataFlavor[] flavors={DataFlavor.stringFlavor};
}
