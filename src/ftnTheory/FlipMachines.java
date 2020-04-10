package ftnTheory;

import java.util.Random;
import java.util.Vector;

import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import exceptions.DataException;

public class FlipMachines extends PackExtender {
	Random rand;
	
	// Constructor
	public FlipMachines(PackData p) {
		super(p);
		rand = new Random(1); // random with seed; remove seed for truly random
		packData=p;
		extensionType="FLIPMACHINES";
		extensionAbbrev="BOT";
		toolTip="'FlipMachines' hosts flipbots that carry out autonomous "+
			"edge flip strategies";
		registerXType();
		if (packData.nodeCount<5) 
			throw new DataException("packing must have >= 5 vertices");
		if (running) {
			packData.packExtensions.add(this);
		}	
	}
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
//		Vector<String> items = null;
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("setNS","n s",null,"Set north/south poles"));
	}

}
