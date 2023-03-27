package ftnTheory;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import combinatorics.komplex.HalfEdge;
import geometry.EuclMath;
import input.CPFileManager;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.StringUtil;

/**
 * Motion by Mean Curvature is a classical topic in
 * differential geometry. Grayson's Theorem is a well
 * known result in 2D motion of curves: if a simple 
 * closed curve evolves by this motion then it remains
 * embedded, never crosses itself, and upon rescaling
 * will converge to a round circle. 
 * 
 * This is a test bench for experiments with a discrete
 * model of this motion. The 'mode' specifies the type of
 * "curvature" being used, currently "kiss" (default) or
 * "flat".
 * @author kensm
 *
 */
public class MeanMove extends PackExtender {
	int []bdryVerts;
	double []bdryRads;
	double []bdryAngSums;
	double pro_K; // increment factor
	double quality;
	String mode="kiss"; // currently "flat" or default "kiss" 
	
	public MeanMove(PackData p) {
		super(p);
		packData=p;
		extensionType="MOTION_BY_MEAN_CURVATURE";
		extensionAbbrev="MC";
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
	 * This routine is called in an adjustment cycle; and applies 
	 * the strategy specified in "mode" string.
	 */
	public int moveIt(String mode) {
		// update boundary data vectors (just in case it will be useful)
		if (update()==0)
			return 0;

		int N=bdryVerts.length;
		int count=0;
		for (int i=0;i<N;i++) {
			int v=bdryVerts[i];
			double []curvdata=getCurvature(packData,v,bdryRads[i]);
			if (curvdata==null)
				Oops("Curvature accumulsation failed");
			double C=curvdata[0];
			double dCdr=curvdata[1];
			double dr=C*pro_K/dCdr;
			double rad=bdryRads[i];
			if (rad>2.0*dr) { // reduce by at most half
				bdryRads[i]=rad-dr;
				count++;
			}
		}
			
		// only now do we store the new radii
		for (int i=0;i<N;i++) {
			int v=bdryVerts[i];
			packData.setRadius(v,bdryRads[i]);
		}
//		packData.fillcurves();

//      normalization ?????

//		repack/layout/display ?????
//		cpCommand(packData,"rld");

		return count;
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
			bdryRads[j]=packData.getRadius(v);
			bdryAngSums[j]=packData.getCurv(v);
		}
		
		return N;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		if (cmd.startsWith("test")) {
			cpCommand("disp -w -ff");
			return 1;
		}
		
		else if (cmd.startsWith("update")) {
			return update();
		}
		
		else if (cmd.startsWith("norm")) {
			int n_mode=0;
			try {
				items=flagSegs.remove(0);
				n_mode=Integer.parseInt(items.get(0));
			} catch (Exception ex) {
				n_mode=0;
			}
			normalize(n_mode);
			return 1;
		}
		
		else if (cmd.startsWith("set_K")) {
			try {
				items=flagSegs.remove(0);
				pro_K=Double.parseDouble(items.get(0));
			} catch (Exception ex){
				Oops("error setting increment");
			}
			return 1;
		}
		
		else if (cmd.startsWith("set_mo")) {
			String md=null;
			try {
				items=flagSegs.remove(0);
				md=items.get(0);
			} catch (Exception ex){
				Oops("error setting increment");
			}

			if (md.startsWith("fl"))
				mode="flat";
			else // default for now
				mode="kiss";
			
			return 1;
		}
		
		else if (cmd.startsWith("toML")) {
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
		
		if (cmd.startsWith("qual")) {
			qualColors();
			return this.cpCommand(packData,"disp -cf b");
		}
		
		if (cmd.startsWith("move")) {
			int reslt=moveIt(mode);
			return reslt;
		}
		
		// else default to superclass
		return super.cmdParser(cmd,flagSegs);
	}

	/**
	 * Get curvatures and derivatives depending on mode
	 * @param p PackData
	 * @param v int
	 * @param r double
	 * @param mode String
	 * @return double[], null on error
	 */
	public double[] getCurvature(PackData p, int v, double r) {
		
		if (mode.startsWith("fla"))
			return flatCurv(p,v,r);
		
		// default to kissing mode
		else if (mode.startsWith("kiss"))
			return kissingCurv(p,v,r);
		
		else
			return null;
	}

	/**
	 * For bdry vert v in eucl packing, flat curvature is
	 * C = pi - theta, theta = angle sum, and dC/dr is
	 * just -dtheta/dr.
	 * @param p PackData
	 * @param v int
	 * @param r double
	 * @return double[], null on error 
	 */
	static double[] flatCurv(PackData p,int v,double r) {
		if (!p.isBdry(v) || p.hes!=0)
			return null;
		double C=Math.PI-p.getCurv(v);
		
		// compute the derivative of theta w.r.t. r
		int[] petals=p.getPetals(v);
		double dtdr=0.0;
		for (int j=0;j<petals.length;j++) {
			int k=petals[j];
			double y=p.getRadius(k); // TODO: ?????
			double z=p.getRadius(k);
			dtdr +=EuclMath.Fx(r,y,z);
		}
		
		double[] results=new double[2];
		if (Math.abs(C)<.0000001)
			return results; // both zero
		results[0]=C;
		results[1]=-1.0*dtdr;

		return results;
	}
	
	/**
	 * For a boundary vertex v in eucl packing, the kissing circle 
	 * (orthogonal to circle for v and going through the points where
	 * v intersects the neighboring boundary circles) has radius R
	 * given by R = tan(theta/2)*r, where theta is anglesum at v.
	 * Thus, the curvature is C = 1/R with + sign if theta < pi, and
	 * - sign if > pi, and zero if theta=pi.
	 * 
	 * Here we compute C and its derivative dC/dr, where r is the 
	 * radius at v. Note that 
	 * 		dC/dr = (-1/R^2)dR/dr, and 
	 * 		dR/dr=tan(theta/2)+r*sec^2(theta/2)*(d(theta)/dr)/2
	 * 
	 * @param p PackData
	 * @param v int
	 * @param r double
	 * @return double[], null on error 
	 */
	static double[] kissingCurv(PackData p,int v,double r) {
		if (!p.isBdry(v) || p.hes!=0)
			return null;
		double t2=p.getCurv(v)/2.0;
		double tant2=Math.tan(t2);
		double cost2=Math.cos(t2);
		double sect22=1.0/(cost2*cost2);
		
		double R=tant2*r;
		double dtdr=0.0;
		
		// compute the derivative of theta w.r.t. r
		int[] petals=p.getPetals(v);
		double r2=p.getRadius(petals[0]);
		for (int j=1;j<petals.length;j++) {
			double r1=r2;
			int k=petals[j];
			r2=p.getRadius(k); 
			dtdr +=EuclMath.Fx(r,r1,r2);
		}
		
		double []results=new double[2];
		
		// curvature zero?
		if (Math.abs(t2-Math.PI/2.0)<.0001) {
			results[0]=0.0;
			results[1]=0.0;
		}
		else {
			results[0]=1.0/R-1.0;
			results[1]=-dtdr;
		}
			
		return results;
	}
	
	/**
	 * Normalize the packing using optional mode.
	 * @param normmode int
	 */
	public void normalize(int normmode) {
		if (bdryVerts==null) {
			update();
		}
		HalfLink hlink=new HalfLink(packData,"b");
		
		// TODO: what are alternative normalizations? 
		switch(normmode){
		case 1:{
			
		}
		default: { // put bdry tangency of largest modulus on unit circle
			double maxdist=0.0;
			Iterator<HalfEdge> hlst=hlink.iterator();
			while (hlst.hasNext()) {
				double dist=packData.tangencyPoint(hlst.next()).abs();
				maxdist=(dist>maxdist) ? dist: maxdist;
			}
			cpCommand("scale "+1/maxdist);
		}
		} // end of switch
	}
	
	/**
	 * Color code bdry circles according to current mode
	 * of curvature.
	 * @return
	 */
	public void qualColors() {
		update();
		int N=bdryVerts.length;

		ArrayList<Double> curvatures=new ArrayList<Double>(N);
		for (int i=0;i<N;i++) {
			int v=bdryVerts[i];
			curvatures.add((Double)getCurvature(packData,v,this.bdryRads[i])[0]);
		}
		
		ArrayList<Integer> curvIndx=ColorUtil.blue_red_color_ramp(curvatures);
		for (int i=0;i<N;i++)
			packData.packDCEL.vertices[bdryVerts[i]].setColor(ColorUtil.coLor(curvIndx.get(i)));
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
