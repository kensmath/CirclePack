package ftnTheory;

import java.util.Vector;

import listManip.PathLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.PathUtil;
import allMains.CPBase;
import allMains.CirclePack;

import complex.Complex;

/**
 * The idea is to perturb circle packings with bdry centers on
 * a given polygonal curve; so we want to perturb the curve
 * and be able to adjust the packing so the centers lie on
 * the new curve. This was started with Gerald Orick and Elias
 * Wegert, but development stopped, and the calls to C code will
 * need to be moved to newer C libraries.
 * 
 * @author kens
 */
public class ShapeShifter extends PackExtender {
	PackData baseData; // where the initial euclidean packing is kept
	PathLink pathList;
	
	// Constructor
	public ShapeShifter(PackData p) {
		super(p);
		packData=p;
		extensionType="ShapeShifter";
		extensionAbbrev="SS";
		toolTip="'ShapeShifter': for creating eucl packings with "+
		"centers on given polygon";
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
			baseData=packData.copyPackTo();
			pathList=setPathList();
			packData.packExtensions.add(this);
		}
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		// ========== copy <pnum> 
		if (cmd.startsWith("copy")) { // copy 'baseData' somewhere (e.g. for inspection)
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS!=null) {
					cpS.swapPackData(baseData,false);
				}
			} catch (Exception ex) {
				return 0;
			}
		}	
		if (cmd.startsWith("getPath")) { // convert ClosedPath
			pathList=setPathList();
		}
		if (cmd.startsWith("getDom")) { // get/reset domain pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS.getPackData().nodeCount!=baseData.nodeCount) {
					errorMsg("getDom: range packing complex must match domain");
					return 0;
				}
				baseData=cpS.getPackData().copyPackTo();
				int rslt;
				try {
					rslt=cpCommand(baseData,"geom_to_e");
				} catch(Exception ex) {
					rslt=0;
				}
				if (rslt==0) {
					errorMsg("SS: failed to convert new domain to euclidean");
				}
			} catch (Exception ex) {
				return 0;
			}
		}
		
		/* This code was just started under the old HeavyC library
		 * setup and used calls to Orick's "cpi" routines. This is OBE.

		if (cmd.startsWith("fit")) {
			PackLite pLite=new PackLite(packData);
			if (HeavyC.putLite(pLite.counts, pLite.varIndices, pLite.origIndices, 
					pLite.radii,pLite.aimIndices,pLite.aims,pLite.invDistEdges,
					pLite.invDistances,pLite.centerRe,pLite.centerIm)==0) {
				throw new JNIException("C call to 'putLite' failed in 'shape shifting'");
			}
			if (pathList==null || pathList.size()<3) {
				setPathList();
			}
			if (pathList==null || pathList.size()<3) {
				errorMsg("fit: path list is not set; 'setPath' sets it to active path");
				return 0;
			}
			int length=pathList.size();
			double []px=new double[length];
			double []py=new double[length];
			Iterator<Complex> pit=pathList.iterator();
			Complex z=null;
			int count=0;
			while (pit.hasNext()) {
				z=(Complex)pit.next();
				px[count]=Double.valueOf(z.x);
				py[count]=Double.valueOf(z.y);
				count++;
			}
			int rslt=HeavyC.fitPath(count,px,py,baseData.nodeCount);
			// capture the radii/centers: this is euclidean data
			double []radii=HeavyC.sendRadii(packData.nodeCount,1);
			double []centerReIm=HeavyC.sendCenterReIm(packData.nodeCount,1);
			for (int v=1;v<=packData.nodeCount;v++) {
				packData.rData[v].rad=radii[v];
				packData.rData[v].center=new Complex(centerReIm[2*v-1],centerReIm[2*v]);
			}
			return rslt;
		}
*/
		return super.cmdParser(cmd, flagSegs);
	} 
	
	public void helpInfo() {
		helpMsg("Commands for PackExtender "+extensionType+"(ShapeShifter)\n"+
				"getDomain:   read a packing into baseData, convert it to eucl\n"+
				"copy <pnum>:   write 'baseData' into designated packing\n"+
				"fit    OBE, not working: try to repack to get bdry circles centered on curve\n"+
				"getPath    set 'pathList' to represent the current closed path\n");
	}
	
	/**
	 * Convert Path2D.Double to PathLink.
	 *
	 * @return PathLink, null on error
	 */
	PathLink setPathList() {
		if (CPBase.ClosedPath==null) return null;
		Vector<Vector<Complex>> cpath=
			PathUtil.gpPolygon(CPBase.ClosedPath);
		if (cpath==null || cpath.size()==0) return null;
		Vector<Complex> comp1=(Vector<Complex>)cpath.get(0);
		PathLink plink=new PathLink();
		for (int i=0;i<comp1.size();i++)
			plink.add((Complex)comp1.get(i));
		return plink;
	}
}
