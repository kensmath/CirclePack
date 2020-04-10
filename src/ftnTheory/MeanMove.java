package ftnTheory;

import geometry.EuclMath;
import input.CPFileManager;

import java.io.BufferedWriter;
import java.util.Vector;

import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.StringUtil;

public class MeanMove extends PackExtender {
	double quality;
	int []bdryVerts;
	double []bdryRads;
	double []bdryAngSums;
	double pro_K;

	public MeanMove(PackData p) {
		super(p);
		packData=p;
		extensionType="MOTION_BY_MEAN_CURVATURE";
		extensionAbbrev="MMC";
		toolTip="Motion by mean curvature test bench";
		registerXType();
		@SuppressWarnings("unused")
		int rslt;
		try {
			rslt=cpCommand(packData,"geom_to_e");
			normalize(0);
		} catch(Exception ex) {
			rslt=0;
		}		
		if (running) {
			packData.packExtensions.add(this);
		}
		quality=-1;
		pro_K=.05;
	}

	/* ======================================================================
	 *         here is the place to put radii adjustment strategies
	 * ================================================================= */
	/** 
	 * Routine called in an adjustment cycle;, then apply the strategy
	 * specified in "mode" string.
	 */
	public int moveIt(String mode) {
		// update boundary data vectors (just in case it will be useful)
		if (update()==0)
			return 0;
		
		if (mode.equals("default")) {
			
			
			return 1;
		}
		
		if (mode.equals("kissing")) {
			int N=bdryVerts.length;
			int count=0;
			for (int i=0;i<N;i++) {
				int v=bdryVerts[i];
				double []kiss=kissingCurv(packData,v,bdryRads[i]);
				if (kiss==null)
					Oops("kissingCurv failed");
				double C=kiss[0];
				double dCdr=kiss[1];
				double dr=C*pro_K/dCdr;
				double rad=bdryRads[i];
				if (rad>2.0*dr) {
					bdryRads[i]=rad-dr;
					count++;
				}
			}
			
			// only now do we store the new radii
			for (int i=0;i<N;i++) {
				int v=bdryVerts[i];
				packData.rData[v].rad=bdryRads[i];
			}
			
			return count;
		}
		
		// normalization ?????
		
		// repack/layout/display
		packData.fillcurves();
//		cpCommand(packData,"rld");
		return 1;
	}
	
	/* ====================================================================*/
	
	/**
	 * Load the lists 'bdryVerts', 'bdryRads', 'bdryAngSums' with
	 * current information on boundary vertices.
	 * @return
	 */
	public int update() {
		NodeLink bdry=new NodeLink(packData,"b");
		int N=0;
		if (bdry==null || (N=bdry.size())<3)
			return 0;
		bdryVerts=new int[N];
		bdryRads=new double[N];
		bdryAngSums=new double[N];
		
		packData.fillcurves();
		for (int j=0;j<N;j++) {
			int v=bdry.get(j);
			bdryVerts[j]=v;
			bdryRads[j]=packData.rData[v].rad;
			bdryAngSums[j]=packData.rData[v].curv;
		}
		
		return N;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		if (cmd.startsWith("test")) {
			cpCommand("disp -w -ff");
			return 1;
		}
		
		if (cmd.startsWith("update")) {
			return update();
		}
		
		if (cmd.startsWith("norm")) {
			int mode=0;
			try {
				items=flagSegs.remove(0);
				mode=Integer.parseInt(items.get(0));
			} catch (Exception ex) {
				mode=0;
			}
			normalize(mode);
			return 1;
		}
		
		if (cmd.startsWith("set_K")) {
			try {
				items=flagSegs.remove(0);
				pro_K=Double.parseDouble(items.get(0));
			} catch (Exception ex){
				Oops("error setting increment");
			}
			return 1;
		}
		
		if (cmd.startsWith("toML")) {
			if (bdryVerts==null || bdryRads==null || bdryAngSums==null)
				Oops("call 'update' to put the data in place");
			try {
				items=flagSegs.remove(0);
				if (items.get(0).equals("-f"))
					items.remove(0);
				String name=StringUtil.reconItem(items);
				
				BufferedWriter fp=CPFileManager.openWriteFP(name,false);
				int N=bdryVerts.length;
				
				fp.write("bdryVerts=[\n");
				for (int j=0;j<N;j++)
					fp.write(Integer.toString(bdryVerts[j]));
				fp.write("];");
				fp.write("bdryRads=[\n");
				for (int j=0;j<N;j++)
					fp.write(Double.toString(bdryRads[j]));
				fp.write("];");
				fp.write("bdryAngSums=[\n");
				for (int j=0;j<N;j++)
					fp.write(Double.toString(bdryAngSums[j]));
				fp.write("];");
				
				fp.flush();
				fp.close();
				
				msg("wrote data to '"+name+"'");
			} catch(Exception ex) {
				Oops("error writing matlab file");
			}
			
			return 1;
			
		}
		
		if (cmd.startsWith("quality")) {
			quality=computeQuality();
			return 1;
		}
		
		if (cmd.startsWith("move")) {
			String mode=null;
			try {
				items=flagSegs.remove(0);
				mode=items.get(0);
			} catch(Exception ex) {
				Oops("specify a mode for 'move'");
			}
			
			int reslt=moveIt(mode);
			
			return reslt;
		}
		
		// else default to superclass
		return super.cmdParser(cmd,flagSegs);
	}
	
	/**
	 * For a boundary vertex v in eucl packing, the kissing circle 
	 * (orthogonal to circle for v and going through the points where
	 * v intersects the neighboring boundary circles) has radius R
	 * given by R = tan(theta/2)*r, where theta is anglesum at v.
	 * Thus, the curvature is C = 1/R with + sign if theta < pi, and
	 * sign - if > pi, and zero if theta=pi.
	 * 
	 * Here we compute C and dC/dr, where r is the radius at v. 
	 * Note that dC/dr = (-1/R^2)dR/dr, and 
	 *     dR/dr=tan(theta/2)+r*sec^2(theta/2)*(d(theta)/dr)/2
	 * 
	 * @param p
	 * @param v
	 * @return null on error 
	 */
	static double []kissingCurv(PackData p,int v,double r) {
		if (p.kData[v].bdryFlag==0 || p.hes!=0)
			return null;
		double t2=p.rData[v].curv/2.0;
		double tant2=Math.tan(t2);
		double cost2=Math.cos(t2);
		double sect22=1.0/(cost2*cost2);
		
		double R=tant2*r;
		double dtdr=0.0;
		
		// compute the derivative of theta w.r.t. r
		for (int j=0;j<p.kData[v].num;j++) {
			int k=p.kData[v].flower[j];
			double y=p.rData[k].rad;
			double z=p.rData[k].rad;
			dtdr +=EuclMath.Fx(r,y,z);
		}
		
		double []results=new double[2];
		
		// curvature zero?
		if (Math.abs(t2-Math.PI/2.0)<.0001) {
			results[0]=0.0;
			results[1]=0.0;
			return results;
		}
			
		// sign negative if anglesum > pi
		results[0]=1.0/R;
		results[1]=Double.valueOf((-1.0/(R*R))*(tant2+r*sect22*dtdr/2.0));
		if (t2>Math.PI/2.0) {
			results[0] *= (-1);
			results[1] *= (-1.0);
		}
		return results;
	}
	
	/**
	 * Normalize the packing using optional mode.
	 * @param mode
	 */
	public void normalize(int mode) {
		switch(mode){
		case 1:{
			
		}
		default: {
			double maxdist=0.0;
			int N=bdryVerts.length;
			for (int i=0;i<N;i++) {
				int v=bdryVerts[i];
				double dist=packData.rData[v].center.abs();
				maxdist=(dist>maxdist) ? dist: maxdist;
			}
			cpCommand("scale "+1/maxdist);
		}
		} // end of switch
	}
	
	/**
	 * As yet unspecified method for measuring quality of the
	 * current boundary curve.
	 * @return
	 */
	double computeQuality() {
		return 2.0;
	}
	
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("move","{strategy}",null,"Adjust boundary radii using named strategy"));
		cmdStruct.add(new CmdStruct("update",null,null,"update 'bdryVerts', 'bdryRads', 'bdryAngSums'"));
		cmdStruct.add(new CmdStruct("toML","-f {filename}",null,"write the bdry data to a matlab file"));
		cmdStruct.add(new CmdStruct("quality",null,null,"returns current 'quality'."));
		cmdStruct.add(new CmdStruct("set_K","{x}",null,"set curvature proportion K"));
		cmdStruct.add(new CmdStruct("norm","[m]",null,"normalize packing using mode 'm'"));
	}
	
}
