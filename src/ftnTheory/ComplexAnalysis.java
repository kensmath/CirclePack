package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.EuclMath;
import geometry.CircleSimple;
import geometry.SphericalMath;
import komplex.DualTri;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import math.Mobius;
import math.group.GroupElement;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.FtnInterpolator;
import util.StringUtil;
/**
 * Compute complex analytic-like data for discrete analytic function
 * f: domainData --> rangeData. Computations put discrete complex 
 * derivative or other results into 'outputData'. For instance,
 * 'diff' stores centers of the complex derivative of f.
 * 
 * On startup, parent packing is converted to euclidean and copied
 * into 'domainData'.
 * 
 * @author kens, based on ideas of Gerald Orick, Stephan Ruscheweyh,
 * and others
 *
 */
public class ComplexAnalysis extends PackExtender {
	PackData domainData;
	PackData rangeData;
	PackData outputData;
	double [][]conductance;  // [v][j] is for edge from v to flower entry j.
	Mobius []faceMobs=null; // face Mobius trans between domain/range
	DualTri []domTPs=null; // info on domain tangency points
	DualTri []ranTPs=null; // info on range tangency points
	
	// for discrete |f'| data
	double []circlePts; // arguments in [0,2pi] for max pack bdry centers
	double []moddiv; // corresponding values of |f'|
	
	// Constructor
	public ComplexAnalysis(PackData p) {
		super(p);
		packData=p;
		extensionType="COMPLEXANALYSIS";
		extensionAbbrev="CA";
		toolTip="'ComplexAnalysis' provides discrete versions of "+
			"some standard function theory notions";
		registerXType();

		rangeData=null;
		circlePts=null;
		moddiv=null;
		int rslt=1;
		try {
			if (packData.hes!=0)
				rslt=cpCommand(packData,"geom_to_e");
		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			CirclePack.cpb.errMsg("CA: failed to convert to euclidean");
			running=false;
		}
		if (running) {
			domainData=packData.copyPackTo();
			outputData=packData.copyPackTo();
			conductance=setConductances(domainData);
			packData.packExtensions.add(this);
		}
	}
	
	/**
	 * Given array of double values for vertices, return an
	 * array of its discrete Laplacian values --- that is, 
	 * at v, compute average of petal values (weighted by
	 * conductances) minus center value. 
	 * @param values, double array of size nodeCount+1
	 * @return
	 */
	public double []LaplaceIt(double []values) {
		double []lp=new double[packData.nodeCount+1];
		for (int v=1;v<=packData.nodeCount;v++) {
			
			double tot=0.0;
			for (int j=0;j<packData.getNum(v)+packData.getBdryFlag(v);j++) 
				tot += conductance[v][j];
			double avg=0.0;
			for (int j=0;j<packData.getNum(v)+packData.getBdryFlag(v);j++) 
				avg += values[packData.kData[v].flower[j]]*conductance[v][j]/tot;
			lp[v]=avg-values[v];
		}
		return lp;
	}

	public boolean fillTangPts() {
		if (!domainData.status || !rangeData.status ||
				domainData.nodeCount != rangeData.nodeCount) 
				Oops("domain/range not loaded or not same complex");
		
		// compute the tangency points, domain/range
		domTPs=new DualTri[domainData.faceCount+1];
		ranTPs=new DualTri[rangeData.faceCount+1];
		for (int f=1;f<=domainData.faceCount;f++) {
			int []verts=domainData.faces[f].vert;
			domTPs[f]=new DualTri(domainData.hes,
					domainData.getCenter(verts[0]),
					domainData.getCenter(verts[1]),
					domainData.getCenter(verts[2]));
			ranTPs[f]=new DualTri(rangeData.hes,
					rangeData.getCenter(verts[0]),
					rangeData.getCenter(verts[1]),
					rangeData.getCenter(verts[2]));
		}
		return true;
	}
	
	// Compute the face Mobius transformations; update tang pts first.
	public boolean compFaceMobs() {
		if (!domainData.status || !rangeData.status ||
				domainData.nodeCount != rangeData.nodeCount ||
				domTPs==null || ranTPs==null) 
			Oops("domain/range incompatible or tangency points not updated");
		faceMobs=new Mobius[domainData.faceCount+1];
		for (int f=1;f<=domainData.faceCount;f++) {
			Complex a=domTPs[f].getTP(0);
			Complex b=domTPs[f].getTP(1);
			Complex c=domTPs[f].getTP(2);
			Complex A=ranTPs[f].getTP(0);
			Complex B=ranTPs[f].getTP(1);
			Complex C=ranTPs[f].getTP(2);
			
			Mobius m=Mobius.standard3Point(a, b, c);
			Mobius M=Mobius.standard3Point(A,B,C);
			GroupElement  MInverse=(GroupElement)M.inverse();
			faceMobs[f]=(Mobius)m.lmult(MInverse);
		}
		return true;
	}	
		
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		// ===================== fMo ====================
		// Construct the 'face Mobius' maps. Domain/range must exist
		//   and have same size. Given face f={v1,v2,v3}, find the
		//   Mobius of determinant 1 which maps the three tangency
		//   points in domain to the corresponding three tangency 
		//   points in range.
		if (cmd.startsWith("fMo")) { 
			if (!fillTangPts() || !this.compFaceMobs())
				Oops("domain/range not loaded or not same complex");
			msg("Have stored the face Mobius maps");
			return 1;
		}
		
		// ==================== ddtris ==============
		// Draw Mobius images of domain dual triangles in packData 
		//   window using 'faceMobs' (full, numerators, or denominators).
		if (cmd.startsWith("ddtr")) {
			int count=0;
			CPScreen cpScreen=packData.cpScreen; // default
			boolean dots=false;
			FaceLink facelist=null;
			DispFlags dflags=null;
			
			int mflag=1; // 1=full mob, 2=num, 3=donom
			int qnum=packData.packNum; // default screen
			try {
				Iterator<Vector<String>> fls = flagSegs.iterator();
				while (fls.hasNext()) {
					items = fls.next();
					String str = items.get(0);
					
					// parse flags
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						String sub_cmd=str.substring(2); // cut the flag
						
						switch(c){
						case 'q': // in which pack to display? 
						{
							if ((qnum=StringUtil.qFlagParse(str))<0)
								qnum=packData.packNum;
							cpScreen=CPBase.pack[qnum];
							break;
						}
						case 'n': // numerator: if Mob=[a b;c d], then use az+b
						{
							mflag=2;
							break;
						}
						case 'd': // denominator: if Mob=[a b;c d], then use cz+d
						{
							mflag=3;
							break;
						}
						case 't': // include 'dot' trinket at tangency points
						{
							dots=true;
							break;
						}
						case 'f': // faces, with compact display options
						{
							dflags=new DispFlags(sub_cmd);
							  
							// look for face indices
							items.remove(0); // remove the flag
							if (items.size()>0) // list remains?
								facelist=new FaceLink(domainData,items);
							else // default to 'all'
								facelist=new FaceLink(domainData,"a");
							  
							break;
						}
						} // end of switch
					} // end of flag processing
					
					// if not a flag, must be list of face indices, default to 'all'
					else {
						  if (items.size()>0)
							  facelist=new FaceLink(domainData,items);
						  else 
							  facelist=new FaceLink(domainData,"a");
					}
				} // end of while
			} catch (Exception ex) {
				Oops("need flag");
			}
			
/*			if (packData.hes<=0) { // set parent window size
				double xmin,xmax,ymin,ymax;
				xmin=ymin=1000000;
				xmax=ymax=-1000000;
				for (int f=1;f<=domainData.faceCount;f++) {
					double hd;
					for (int j=0;j<3;j++) {
						Complex w=ranTPs[f].getTP(j);
						xmin=(xmin<(hd=w.x)) ? xmin : hd;
						xmax=(xmax>hd) ? xmax : hd;
						ymin=(ymin<(hd=w.y)) ? ymin : hd;
						ymax=(ymax>hd) ? ymax : hd;
					}
				}
				cpCommand(CPBase.pack[qnum].packData,"set_screen -b "+xmin+" "+ymin+" "+xmax+" "+ymax);
			}
*/

			Iterator<Integer> flist=facelist.iterator();
			while (flist.hasNext()) {
				int f=flist.next();
				count++;
				int hes=cpScreen.getGeom(); // use geom of target screen
				Complex []tps=new Complex[3]; 
				for (int j=0;j<3;j++) {
					tps[j]=domTPs[f].getTP(j);
					if (domainData.hes>0) { // proj to plane to apply mobius
						tps[j]=SphericalMath.s_pt_to_plane(tps[j]);
					}
					if (mflag==2) { // apply numerator
						Mobius mb=new Mobius();
						mb.a=faceMobs[f].a;
						mb.b=faceMobs[f].b;
						mb.c=new Complex(0.0);
						mb.d=new Complex(1.0);
						tps[j]=mb.apply(tps[j]);
					}
					else if (mflag==3) { // apply denominator
						Mobius mb=new Mobius();
						mb.a=faceMobs[f].c;
						mb.b=faceMobs[f].d;
						mb.c=new Complex(0.0);
						mb.d=new Complex(1.0);
						tps[j]=mb.apply(tps[j]);
					}
					else { // apply full mobius
						tps[j]=faceMobs[f].apply(tps[j]);
					}
				}
				if (hes>0) { // convert image points to sph coords
					for (int j=0;j<3;j++) {
						tps[j]=SphericalMath.proj_pt_to_sph(tps[j]);
					}
				}

				// draw dots at tangency points?
				if (dots) {
					DispFlags tmpflags=new DispFlags("");
					cpScreen.drawTrinket(0,tps[0],tmpflags);
					cpScreen.drawTrinket(0,tps[1],tmpflags);
					cpScreen.drawTrinket(0,tps[2],tmpflags);
				}
				
				// actually draw the triangles
				if (!dflags.colorIsSet)
					dflags.setColor(domainData.getFaceColor(f));
				if (dflags.label)
					dflags.setLabel(Integer.toString(f));
				cpScreen.drawFace(tps[0],tps[1],tps[2],null,null,null,dflags);
				count++;
			} // end of while

			// repaint the canvas
			CPBase.pack[qnum].repaint();
			return count;
		}
		
		// ==================== diff =============
		if (cmd.startsWith("diff")) {
			int mode=1;
			int ans=discreteDeriv(domainData,rangeData,mode);
			return ans;
		}
		
		// ==================== set_div ===========
		if (cmd.startsWith("set_div")) {
			if (!domainData.status || !rangeData.status || 
					domainData.nodeCount!=rangeData.nodeCount)
				Oops("packings not sync'ed");
			NodeLink bdry=new NodeLink(domainData,"b");
			int cnt=bdry.size();
			circlePts=new double[cnt];
			moddiv=new double[cnt];
			Iterator<Integer> blst=bdry.iterator();
			int tick=0;
			while(blst.hasNext()) {
				int b=blst.next();
				circlePts[tick]=domainData.getCenter(b).arg();
				moddiv[tick]=rangeData.getRadius(b)/domainData.getRadius(b);
				tick++;
			}
			return cnt;
		}
		
		// ================== apply_div ================
		if (cmd.startsWith("apply_d")) {
			int cnt=0;
			if (circlePts==null || (cnt=circlePts.length)==0)
				Oops("perhaps 'set_div' first?");
			
			// what packing to apply to? default to active
			PackData toPack=CPBase.pack[PackControl.activeFrame.getActivePackNum()].getPackData();
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.valueOf((String)items.get(0));
				toPack=CPBase.pack[pnum].getPackData();
			} catch(Exception ex) {}

			// set up complex ftn vector and interpolator
			Complex []complexdiv=new Complex[cnt];
			for (int j=0;j<cnt;j++)
				complexdiv[j]=new Complex(moddiv[j]);
			FtnInterpolator finterp = new FtnInterpolator();
			finterp.valuesInit(circlePts,complexdiv);
			
			// find bdry
			NodeLink bdry=new NodeLink(toPack,"b");
			Iterator<Integer> blst=bdry.iterator();
			while (blst.hasNext()) {
				int v=blst.next();
				double s=toPack.getCenter(v).arg();
				double ftnvalue=finterp.interpValue(s).x;
				toPack.setRadius(v,toPack.getRadius(v)*ftnvalue);
			}
			return cnt;
		}
		
		// =============== SR ===========
		if (cmd.startsWith("SR")) { // Stephan Ruscheweyh parameterization
			int ans=0;
			try {
				items=(Vector<String>)flagSegs.get(0);
				double aParam=Double.valueOf((String)items.get(0));
				ans=SR_parameterize(aParam);
			} catch (Exception ex) {
				errorMsg("CA "+ex.getMessage());
				return 0;
			}
			return ans;
		}
		
		// ========== logR ========
		if (cmd.startsWith("logR")) {
			if (rangeData==null || rangeData.nodeCount!=domainData.nodeCount) {
				errorMsg("rangeData has not been loaded");
				return 0;
			}
			double []logr=new double[packData.nodeCount+1];
			double []laplace=new double[packData.nodeCount+1];
			for (int v=1;v<=packData.nodeCount;v++) 
				logr[v]=Math.log(rangeData.getRadius(v))-Math.log(domainData.getRadius(v));
			laplace=LaplaceIt(logr);
			Vector<Double> data=new Vector<Double>(packData.nodeCount);
			for (int v=1;v<=packData.nodeCount;v++)
				data.add(Double.valueOf(laplace[v]));
			Vector<Integer> colors=new Vector<Integer>(packData.nodeCount);
			colors=ColorUtil.blue_red_diff_ramp(data);
			
			// record in 'rData' of 'packData'
			for (int v=1;v<=packData.nodeCount;v++)
				packData.setFaceColor(v,CPScreen.coLor((int)colors.get(v-1)));
			
			double miN=0.0;
			double maX=0.0;
			for (int v=1;v<=packData.nodeCount;v++) {
				miN = (laplace[v]<miN) ? laplace[v] : miN;
				maX = (laplace[v]>maX) ? laplace[v] : maX;
			}
			msg("Laplacian: min = "+miN+" and max = "+maX);
			return 1;
		}
		
		// ========== copy <pnum> 
		if (cmd.startsWith("copy")) { // copy 'outputData' in some pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.valueOf((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS!=null) {
					return cpS.swapPackData(outputData,false);
				}
			} catch (Exception ex) {
				return 0;
			}
		}	
		
		// ===================== getDom ===============
		if (cmd.startsWith("getDom")) { // get/reset domain pack, compute conductances
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.valueOf((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS.getPackData().nodeCount!=domainData.nodeCount) {
					errorMsg("getDom: range packing complex must match domain");
					return 0;
				}
				domainData=cpS.getPackData().copyPackTo();
				outputData=cpS.getPackData().copyPackTo();
				conductance=setConductances(domainData);
			} catch (Exception ex) {
				return 0;
			}
			int rslt=1;
			try {
				if (domainData.hes!=1)
					rslt=cpCommand(domainData,"geom_to_e");
			} catch(Exception ex) {
				rslt=0;
			}
			if (rslt==0) {
				errorMsg("CA: failed to convert new domain to euclidean");
			}
			return 1;
		}
	
		// ================== getRange ==============
		if (cmd.startsWith("getRan")) { // get a range packing
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.valueOf((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS.getPackData().nodeCount!=domainData.nodeCount) {
					errorMsg("getRan: range packing complex must match domain");
					return 0;
				}
				rangeData=cpS.getPackData().copyPackTo();
			} catch (Exception ex) {
				return 0;
			}
			int rslt=1;
			try {
				if (rangeData.hes!=0)
					rslt=cpCommand(rangeData,"geom_to_e");
			} catch(Exception ex) {
				rslt=0;
			}
			if (rslt==0) {
				errorMsg("CA: failed to convert range to euclidean");
			}
			return 1;
		}
	
		// ================== getConduct ==============
		if (cmd.startsWith("getCond")) {
			int count=0;
			EdgeLink elist=null;
			try {
				elist=new EdgeLink(domainData,(Vector<String>)flagSegs.get(0));
			} catch (Exception ex) {
				return 0;
			}
			if (elist==null || elist.size()==0) 
				return 0;
			Iterator<EdgeSimple> elt=elist.iterator();
			while (elt.hasNext()) {
				EdgeSimple edge=elt.next();
				int v=edge.v;
				int j=domainData.nghb(v,edge.w);
				msg("("+v+","+edge.w+"): "+String.format("%.6e",conductance[v][j]));
				count++;
			}
			return count;
		}
		
		// else default to superclass
		return super.cmdParser(cmd,flagSegs);
	}
		
	/**
	 * Stephan Ruschewyeh (6/08) told me this conjecture: 
	 * Assume f is in class S on the disc. Define 
	 *     F_a(z)=integral_0^z f'(t)(t/f(t))^a dt
	 * Conj: for a in [0,1], F_a(z) is in class S.
	 * 
	 * Eucl max pack, radii r, is in 'domainPack'.
	 * f(z) with radii R is in parent 'packData'. 
	 * compute all radii s for F_a(z), put in 'outputData',
	 *    by s = r * exp{log(R/r) - a*log(|f(z)/z|)}
	 *    or s = R/exp{a*log(|f(z)/z|)}; 
	 * 
	 * @param aparam, double, parameter typically in [0,1]
	 * @return 0 on error
	 */
	public int SR_parameterize(double aparam) {
		for (int v=1;v<=packData.nodeCount;v++) {
			double r=domainData.getRadius(v);
			double expal;
			//  at z=0 replace |f(z)/z| by |f'(z)| (typically, 1)
			if (domainData.getCenter(v).abs()<.0000001) {
				expal=Math.exp(aparam*Math.log(packData.getRadius(v)/r)); 
			}
			else {
				double rc_dc=packData.getCenter(v).
					divide(domainData.getCenter(v)).abs();
				expal=Math.exp(aparam*Math.log(rc_dc));
			}
			outputData.setRadius(v,packData.getRadius(v)/expal);
		}
		return 1;
	}
	
	/**
	 * Compute conductances of the triangulation based on packing centers. 
	 * If 'domData' is a packing, this is usual set of eucl conductances 
	 * (a la Dubejko). However, in general, use ratio of 
	 * distance between incircle centers of bounding faces to length of
	 * their common edge. (For bdry edges, use inRadius/edgelength.)
	 * Compute transition probability from v to u by dividing edge <v,u>
	 * conductance by sum of conductances of all edges from v. 
	 * @param 'PackData' domData
	 * @return
	 */
	public static double [][]setConductances(PackData domData) {
		if (domData==null || domData.nodeCount<=0 || domData.hes!=0 
				|| domData.status==false) {
			throw new ParserException("packing not set or not suitable");
		}
		double []spokes=null;
		Complex []inCenters=null;
		double [][]conductance=new double[domData.nodeCount+1][];
		Complex f1=null;
		Complex f2=null;
		for (int v=1;v<=domData.nodeCount;v++) {
			int num=domData.getNum(v);
			Complex z=domData.getCenter(v);
			spokes=new double[num+1];
			inCenters=new Complex[num];

			conductance[v]=new double[num+1];

			// store edge lengths, incenters
			f2=domData.getCenter(domData.kData[v].flower[0]);
			spokes[0]=z.minus(f2).abs();
			CircleSimple sc=null;
			for (int j=1;j<=num;j++) {
				f1=f2;
				f2=domData.getCenter(domData.kData[v].flower[j]);
				sc=EuclMath.eucl_tri_incircle(z,f1,f2);
				spokes[j]=z.minus(f2).abs();
				inCenters[j-1]=sc.center;
			}

			// store conductances
			
			// for bdry, use ratio of inRad/length for first and last edges
			if (domData.isBdry(v)) {
				f1=domData.getCenter(domData.kData[v].flower[0]);
				f2=domData.getCenter(domData.kData[v].flower[1]);
				double inRad=EuclMath.eucl_tri_inradius(spokes[0],spokes[1],f1.minus(f2).abs());
				conductance[v][0]=inRad/spokes[0];
				f1=domData.getCenter(domData.kData[v].flower[num-1]);
				f2=domData.getCenter(domData.kData[v].flower[num]);
				inRad=EuclMath.eucl_tri_inradius(spokes[num-1],spokes[num],f1.minus(f2).abs());
				conductance[v][num]=inRad/spokes[num];
			}
			else { // interior: first conductance repeated in last
				conductance[v][0]=
					conductance[v][num]=inCenters[num-1].minus(inCenters[0]).abs()/spokes[0];
			}
			
			// now the rest
			for (int j=1;j<num;j++) {
				conductance[v][j]=inCenters[j-1].minus(inCenters[j]).abs()/spokes[j];
			}
		} // end of loop on v
		return conductance;
	}
	
	/**
	 * This is derivative the DAF f: packData --> rangeData, but uses
	 * the conductances stored for the last 'domainData'. The resulting
	 * centers are put in 'outputData'. The effective radii are also
	 * stored in 'outputData'. 'mode' is for future use if there are
	 * options.
	 * @param domainData, rangeData
	 * @param mode (int for future options)
	 * @return 0 on error
	 */
	public int discreteDeriv(PackData dData,PackData rData, int mode) {
		if (rData==null || rData.nodeCount!=dData.nodeCount) {
			errorMsg("rangeData has not been loaded");
			return 0;
		}
		if (conductance==null) {
			errorMsg("conductances are not set");
			return 0;
		}
		Complex z,w,deriv;
		Complex []domSpokes=null;
		Complex []ranSpokes=null;
		try {
		for (int v=1;v<=dData.nodeCount;v++) {
			
			// data at v
			int num=dData.getNum(v);
			z=dData.getCenter(v);
			w=rData.getCenter(v);

			// check if packings match here
			if (num!=rData.getNum(v) || num!=dData.getNum(v)
					|| dData.getBdryFlag(v)!=rData.getBdryFlag(v)
					|| dData.getBdryFlag(v)!=rData.getBdryFlag(v))
				throw new DataException("combinatorics of packings do not agree");
			// for the data
			domSpokes=new Complex[num+1];
			ranSpokes=new Complex[num+1];

			double totalWeight=0.0;

			// store complex edge vectors
			for (int j=0;j<dData.getNum(v)+dData.getBdryFlag(v);j++) {
				domSpokes[j]=z.minus(dData.getCenter(dData.kData[v].flower[j]));
				ranSpokes[j]=w.minus(rData.getCenter(rData.kData[v].flower[j]));
				totalWeight+=conductance[v][j];
			}

			deriv=new Complex(0.0);
			for (int j=0;j<num+dData.getBdryFlag(v);j++) 
				deriv=deriv.add(ranSpokes[j].divide(domSpokes[j]).times(conductance[v][j]));
			outputData.setCenter(v,deriv.divide(totalWeight));
		}
		} catch(Exception ex) {
			errorMsg("error in computing derivative: "+ex.getMessage());
			return 0;
		}
		
		// Also compute the radii based solely on the computed centers.
		if (EuclMath.effectiveRad(outputData,null)==0) {
			errorMsg("error in setting 'effective' radii.");
		}
		return 1;
	}


	
	public int oldComputeDerivative(int mode) {
		for (int v=1;v<=packData.nodeCount;v++) {
			double totalWeight=0.0;
			
			// data at v
			int num=packData.getNum(v);
			double rad=packData.getRadius(v);
			Complex z=packData.getCenter(v);
			Complex w=rangeData.getCenter(v);

			// check if packings match here
			if (num!=rangeData.getNum(v)
					|| packData.getBdryFlag(v)!=rangeData.getBdryFlag(v))
				throw new DataException("combinatorics of packings are not the same");
			// for the data
			double []weights=new double[num+1];
			Complex []domSpokes=new Complex[num+1];
			Complex []ranSpokes=new Complex[num+1];

			// compute the 'weights'
			// compute for first edge
			double rad1=packData.getRadius(packData.kData[v].flower[0]);
			double rad2=packData.getRadius(packData.kData[v].flower[1]);
			double inRad=EuclMath.eucl_tri_inradius(rad+rad1,rad+rad2,rad1+rad2);
			domSpokes[0]=z.minus(packData.getCenter(packData.kData[v].flower[0]));
			domSpokes[1]=z.minus(packData.getCenter(packData.kData[v].flower[1]));
			ranSpokes[0]=w.minus(rangeData.getCenter(rangeData.kData[v].flower[0]));
			ranSpokes[1]=w.minus(rangeData.getCenter(rangeData.kData[v].flower[1]));
			weights[0]=inRad/domSpokes[0].abs();
			totalWeight=weights[0];
			weights[1]=inRad/domSpokes[1].abs();
			
			// compute for remaining edges
			// Note: for interior v, weight = weights[0]+weights[num]
			for (int j=1;j<packData.getNum(v);j++) {
				rad1=rad2;
				rad2=packData.getRadius(packData.kData[v].flower[j+1]);
				inRad=EuclMath.eucl_tri_inradius(rad+rad1,rad+rad2,rad1+rad2);
				domSpokes[j+1]=z.minus(packData.getCenter(packData.kData[v].flower[j+1]));
				ranSpokes[j+1]=w.minus(rangeData.getCenter(rangeData.kData[v].flower[j+1]));
				weights[j]+=inRad/domSpokes[j].abs(); // for this edge
				weights[j+1]=inRad/domSpokes[j+1].abs(); // add to next weight
				totalWeight+=weights[j];
			}
			totalWeight+=weights[num];

			Complex deriv=new Complex(0.0);
			for (int j=0;j<=num;j++) 
				deriv=deriv.add(ranSpokes[j].divide(domSpokes[j]).times(weights[j]));
			outputData.setCenter(v,deriv.divide(totalWeight));
		}
		
		EuclMath.effectiveRad(outputData,null);
		return	1;
	}
	
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("getRange","{pnum}",null,
				"read packing (for same complex) into 'rangeData', "+
				"convert to eucl"));
		cmdStruct.add(new CmdStruct("getDomain","{pnum}",null,
				"read a packing (for same complex) into 'domainData', "+
				"convert to eucl"));
		cmdStruct.add(new CmdStruct("diff",null,null,
				"put complex deriv centers/radii in 'outputData'"));
		cmdStruct.add(new CmdStruct("copy","{pnum}",null,
				"write 'outputData' into designated packing"));
		cmdStruct.add(new CmdStruct("SR","{t}",null,
				"set 'outputData' a la Ruschewyeh conjecture, paramter t"));
		cmdStruct.add(new CmdStruct("logR",null,null,
				"is log(R(v)) harmonic? Color verts by diff, print worst"));
		cmdStruct.add(new CmdStruct("fMob",null,null,"Generate "+
		        "the face Mobius transformations"));
		cmdStruct.add(new CmdStruct("ddtri","-q{q} {flag}",null,"Apply the "+
				"face Mobius transformations, draw them in pack q"));
		cmdStruct.add(new CmdStruct("set_div",null,null,"Set list of bdry center "+
				"arguments and list of euclidean radii ratios."));	
		cmdStruct.add(new CmdStruct("apply_div",null,null,"Multiply bdry radii "+
				"by 'moddiv' (apply set_div first)"));
		
	}

}

	