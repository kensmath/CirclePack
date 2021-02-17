package ftnTheory;

import java.util.Vector;

import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import allMains.CPBase;
import allMains.CirclePack;
import exceptions.ParserException;

/**
 * This may be miss-named. The idea, suggested by Ed Crane, is to reduce
 * the complexity in conformal mapping by trying to tie the combinatorics
 * to the hyperbolic metric (or a related measure).
 * @author Stephenson and Ed Crane, started in Bristol, October 2011
 *
 */
public class HypDensity extends PackExtender {
	PackData outputData; // for holding intermediate packings
	NodeLink theChosen; // hold resampled verts
	
	// convenient parameters to store
	int mode=1; // mode for random resampling; we define this
	int complexity; // the number of random vertices in a standard 'rand_tri' packing;
					// working in the unit disc, this is roughly the Poisson density.
	double maxThin; // the thinnest Poisson density (relative to the complexity)
	
	// Constructor
	public HypDensity(PackData p) {
		super(p);
		extensionType="HYPERBOLIC_DENSITY";
		extensionAbbrev="HD";
		toolTip="'Hyperbolic Density': experiment with reductions in combinatorical" +
				"complexity in conformal mapping";
		registerXType();
		if (packData.hes>0) {
			CirclePack.cpb.errMsg("SC Warning: packing should not be spherical");
		}
		if (CPBase.ClosedPath==null) {
			CirclePack.cpb.msg("HD Warning: you should fill 'ClosedPath'");
		}
		if (running) {
			packData.packExtensions.add(this);
		}
		
		// default parameters
		mode=1;
		complexity=100;
		maxThin=.25;
	}	
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		// TODO: have problems in changing underlying packData; so for now,
		//       experiments should start with desired packing.
		// ----- init_random 
		if (cmd.startsWith("????init_rand")) {
			if (CPBase.ClosedPath==null)
				throw new ParserException("You need to defined 'ClosedPath'");
			int n;
			try {
				n=Integer.parseInt(flagSegs.get(0).get(0));
			} catch (Exception ex) {
				n=complexity; // default
			}
			complexity=n; // update (if changed)

			cpCommand(packData,"random_triangulation -N "+complexity+" -g");
			cpCommand(packData,"disp -w -t");
			return 1;
		}
		
		// ----- hex ---- cookie Gamma from a regular hex, radii x
		else if (cmd.startsWith("????hex")) {
			double r;
			try {
				r=Double.parseDouble(flagSegs.get(0).get(0));
			} catch(Exception ex) {
				throw new ParserException("usage: hex {x} for radii size");
			}
			int n=(int)(((1/r)-1.0)/2.0); // number of generations to fill unit disc
			
			// cookie out from the regular hex and display
			return cpCommand(packData,"seed;add_gen "+n+" 6;gamma 10;"+
					"set_rad "+r+" a;layout;cookie;disp -w -c -g");
		}
		
		// ----- choose ---
		else if (cmd.startsWith("choose")) {
			theChosen=PackData.resample(packData,CPBase.ClosedPath,mode, maxThin);
			if (theChosen==null)
				throw new ParserException("filed to choose 'theChosen'");
			packData.vlist=theChosen.makeCopy();
			return theChosen.size();
		}
		
		// ------ pack
		else if (cmd.startsWith("pack")) {
			outputData=PackData.sampledSubPack(packData,theChosen);
			if (outputData==null)
				return 0;
			return outputData.nodeCount;
		}
		
		// ========== copy <pnum> 
		else if (cmd.startsWith("copy")) { // copy 'outputData' in some pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				if (pnum==packData.packNum)
					return 0;
				CirclePack.cpb.swapPackData(outputData,pnum,false);
			} catch (Exception ex) {
				return 0;
			}
			return 1;
		}	
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("copy","{p}",null,"copy 'outputPack' to pack p."));
		cmdStruct.add(new CmdStruct("setParam","{N} {m} {t}",null,"Set the N=complexity, "+
				"m=mode (1 only for now), and t=thinness parameters"));
		cmdStruct.add(new CmdStruct("choose",null,null,"fill 'theChosen' using current parameters, store in vlist as well"));
		cmdStruct.add(new CmdStruct("pack","{x}",null,"build 'outputPack' from resampled verts"));
		cmdStruct.add(new CmdStruct("copy","{q}",null,"copy 'tmpPack' into packing q"));
	}
}
