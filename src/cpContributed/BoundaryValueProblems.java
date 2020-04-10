package cpContributed;

import java.util.Vector;

import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;

import PackCircle.*;
import com.mathworks.toolbox.javabuilder.*;

public class BoundaryValueProblems extends PackExtender
{
	BVP bvp = null;
	
	public BoundaryValueProblems(PackData p) 
	{
		super(p);
		// TODO Auto-generated constructor stub
		
		extensionType = "BVP";
		extensionAbbrev = "BV";
		toolTip = "'Boundary value problem' for computing the solution" +
				" of discrete Boundary value problems in circle packing";
		registerXType();
		
		if (running)
		{
			packData.packExtensions.add(this);
		}
	}
	
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs)
	{
		//String str;
		
		if (cmd.startsWith("start"))
		{
			try 
			{
				if (bvp == null)
					bvp = new BVP();
			
				bvp.GUI_Bvp();
			} 
			catch (MWException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return 1;
		}

		return super.cmdParser(cmd, flagSegs);
	}
	
	public void initCmdStruct() 
	{
		// description of the commands in the help window
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("start", "", null,
				"Starts a graphical user interface where a discrete boundary value problem can be defined and solved."));
	}
		
	public void StartUpMsg() 
	{
		// display in the message box if the user types help
		helpMsg("\nOverview of PackExtender " + extensionAbbrev + " (Boundary value problem):");
		helpMsg("This pack extender defines a graphical user interface where different" +
				" kinds of boundary value problems can be considered and solved.");
		helpMsg("Commands for PackExtender " + extensionAbbrev + " (Boundary value problem)");
		helpMsg("  start    Starts the graphical user interface\n");
	}
}
