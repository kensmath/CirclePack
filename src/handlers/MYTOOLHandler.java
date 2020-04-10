package handlers;

import java.io.File;

import listeners.USERTOOLListener;
import mytools.MyToolHandler;

public class MYTOOLHandler extends MyToolHandler {

	// Constructor 
	public MYTOOLHandler(File toolFile) {
		super(toolFile,"MYTOOL:");
		toolListener=new USERTOOLListener(this);
		if (toolFile!=null) appendFromFile(toolFile);

	}
}
