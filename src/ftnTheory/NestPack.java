package ftnTheory;

import allMains.CirclePack;
import packing.PackData;
import packing.PackExtender;

public class NestPack extends PackExtender {

//	Vector<PackData> packTree;
	
	// Constructor
	public NestPack(PackData p) {
		super(p);
		extensionType="NEST_PACKER";
		extensionAbbrev="NP";
		toolTip="'NestPack': for building nested packing in hope of"+
			"getting results for especially tough max packings";
		registerXType();
		int rslt;
		try {
			rslt=cpCommand(packData,"geom_to_e");
		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			CirclePack.cpb.errMsg("CA: failed to convert to euclidean");
			running=false;
		}
		if (running) {
			packData.packExtensions.add(this);
		}
	}
	
//	class PackTreeNode {
		
//		Pack
		
//		public PackTreeNode(Pack p) {
		
//	}
}
