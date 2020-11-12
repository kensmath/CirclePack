package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import allMains.CPBase;
import circlePack.PackControl;

import complex.Complex;
/**
 * Routine for experimenting with the Beurling-Riemann Mapping 
 * Theorem. (An earlier version was 'Beurling_flow.java'.)
 * The goal is to use an ambient function Phi, continuous and
 * (typically) non-zero on the plane.
 * 
 * Idea is to set up iterative process. Beurling's generalization 
 * of Riemann mapping theorem says that	given bounded, continuous 
 * positive function h on the plane, there exists a univalent 
 * analytic function f so that |f'(z)| - h(f(z)) --> 0 as z --> e^{it} 
 * (on unit circle), f(0)=0 and f'(0)>0. In some cases, f is unique.
 * Wegert, Roth, et al have extended this to allow branching as well. 
 */
public class BeurlingFlow extends PackExtender {
	PackData domainData; // usually the max packing (eucl) for parent
	int ftn_mode; // which Beurling ftn 'h'? default 0 is 'Function' tab 
	
	// Constructor
	public BeurlingFlow(PackData p) {
		super(p);
		extensionType="BEURLINGFLOW";
		extensionAbbrev="BF";
		toolTip="'BeurlingFlow': for experiments with "+
			"discrete Beurling-Riemann Mapping Theorem";
		registerXType();
		
		ftn_mode=0; // default
		try {
			domainData=p.copyPackTo();
			// Note: convert parent to euclidean
			cpCommand(packData,"geom_to_e"); // 
			cpCommand(domainData,"max_pack 10000");
			cpCommand(domainData,"geom_to_e");
			domainData.fillcurves();
		} catch (Exception ex) {
			errorMsg("CurvFlow: error in preparing 'domainPack'");
			running=false;
		}
		if (running) { 
			packData.packExtensions.add(this);
		}
	}
			
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		if (cmd.startsWith("getDom")) { // get/reset domain pack
			CPScreen cpS;
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				cpS=CPBase.pack[pnum];
				if (cpS.getPackData().nodeCount!=domainData.nodeCount) {
					errorMsg("getDom: range complex must match domain");
					return 0;
				}
				domainData=cpS.getPackData().copyPackTo();
			} catch (Exception ex) {
				return 0;
			}
			int rslt;
			try {
				rslt=cpCommand(domainData,"geom_to_e");
			} catch(Exception ex) {
				rslt=0;
			}
			if (rslt==0) {
				errorMsg("BF: failed to convert new domain to euclidean");
			}
		}
		if (cmd.startsWith("ftn")) {
			try {
				ftn_mode=Integer.parseInt((String)(flagSegs.get(0).get(0)));
				setFunction(ftn_mode);
				return 1;
			} catch(Exception ex) {
				errorMsg("BF: error setting function mode");
			}
		}
		if (cmd.startsWith("flow")) {
			
			// default values
			double t=1.0;
			double xp=1.0;
			int option=0;
			
			// any flags?
			if (flagSegs!=null && flagSegs.size()>0) {
				try {
					Iterator<Vector<String>> fsi=flagSegs.iterator();
					while (fsi.hasNext()) {
						items=(Vector<String>)fsi.next();
						String str=items.get(0);
						if (str.startsWith("-t")) {
							t=Double.parseDouble(items.get(1));
						}
						else if (str.startsWith("-x")) {
							xp=Double.parseDouble(items.get(1));
						}
						else if (str.startsWith("-o")) {
							option=Integer.parseInt(items.get(1));
						}
					}
				} catch(Exception ex) {
					errorMsg("flow usage: flow -t <t> -x <x> -o <o>");
				}
			}
			return beurling_flow(t,xp,option);
		}
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Set the function mode: 0 (default) means use that specified
	 * in 'Function' tab; others must be defined explicitly in
	 * 'evaluate_h'.
	 * TODO: more general methods for specifying, interpreting functions. 
	 */
	public void setFunction(int mode) {
		ftn_mode=mode;
	}
	
	/**
	 * evaluate the Beurling function 'h(z)' for given z. Default (0)
	 * to use 'Function' tab. Others are put in explicitly.
	 * @param z
	 * @return
	 */
	public double evaluate_h(Complex z) {
		double ans=1.0;
		if (ftn_mode==1) { // recip of Poincare density is unit disc
			ans=(1.0-z.times(z).abs())/2.0;
		}
		else if (ftn_mode==2) { // Example 5.4, Bauer/Kraus/Roth/Wegert.
			double absz=z.abs();
			if (absz<=2.0) 
				ans=Math.sqrt(2*absz*absz+1.0);
			else if (absz<=3.0)
				ans=3.0;
			else if (absz<=6.0)
				ans=absz;
			else ans=6.0;
		}
		else if (ftn_mode==3) { 
			ans=2-(z.abs()-1)*(z.abs()-1);
		}
		else { // use 'Function' tab in main GUI.
			ans=PackControl.functionPanel.getFtnValue(z).abs();
		}
	
		return ans;
	}
	
	/**
	 * Here's the guts: adjust the radii of 'packData' to move
	 * toward the Beurling map. defaults: t=1, xpnt=1,option=0
	 * @param t, double, relaxation parameter, usually in [0,1]
	 * @param xpnt, double, exponent: replaces h(z) by h^{xpnt}(z).
	 * @param option, int, adjust: 0=all, 1=up only, 2=down only
	 * @return count of adjustments, negative on error
	 */
	public int beurling_flow(double t,double xpnt,int option) {
		int count=0;
		double CPhz,e_rad,cur,factor=1.0;
		Complex z;

		// cycle through bdry pr 
		for (int v=1;v<=packData.nodeCount;v++) {
			if (packData.kData[v].bdryFlag!=0) {
				z=packData.getCenter(v);
				e_rad=domainData.getRadius(v);
				CPhz=evaluate_h(z);
	  
				// Set the new radius 
				if (xpnt!=1.0) CPhz=Math.exp(xpnt*Math.log(CPhz));
				cur=packData.getRadius(v)/e_rad;
				factor=Math.exp(t*Math.log(CPhz/cur));
				double vrad=packData.getRadius(v);
				if (option==1) {
					if (factor>1.0) // increases only
						packData.setRadius(v,factor*vrad);
				}
				else if (option==2) {
					if (factor<1.0) // decreases only 
						packData.setRadius(v,factor*vrad);
				}
				else
					packData.setRadius(v,factor*vrad);
				count++;
			}
		} // end of for loop
		return count;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("getDomain","p",null,"load pack 'p' as the domain, convert to euclidean"));
		cmdStruct.add(new CmdStruct("flow","-t {t} -x {x} -o {j}",null,"set radii using h:"+
				"relaxation t (default 1), exponent x for h (default 1), "+
				"options: adjust 0=all,1=upward,2=downward"));
		cmdStruct.add(new CmdStruct("ftn","{text(z)}",null,"set function 'h', variable must be 'z': default to 'Function' tab entry"));
	}
	
}
