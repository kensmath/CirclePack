package ftnTheory;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PairLink;
import dcel.Schwarzian;
import dcel.SideData;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedEdge;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.EuclMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.NodeLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import rePack.EuclPacker;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;
import util.TriData;

/**
 * Projective structures on triangulated surfaces (such as
 * affine structures on tori) require face-based data 
 * structures. Though these are partially available in 
 * our DCEL structures, we need more full face-based for
 * working with 'labels' and schwarzians. 
 * 
 * First setting was tori, thanks to Chris Sass. Those methods
 * are affine, however, so they don't extend to higher genus.
 * Since then I've introduced two things: starting in 2018 I
 * began studying discrete Schwarzians, generalizing ideas due
 * to Gerald Orick. In 2019, I began the major conversion to
 * DCEL combinatorial structures.
 * 
 * I will start converting this code to advance the use of 
 * Schwarzians, the first goal being to replicate what this 
 * code did for tori.
 * 
 * Our approach in the affine setting is to replace radii 
 * as the parameters, using localized geometry instead. In 
 * particular, to each face we attached 'labels' r1:r2:r3 
 * of radii (in eucl case, the radii are not important, only 
 * their ratios). The angle sum at a vertex v is obtained 
 * face-by-face. 
 * 
 * Initial scenario is tori: Arrange as combinatorial 
 * quadrilaterals, noting identifications of top/bottom, 
 * left/right end vertices. Assign radii, assuring that 
 * those on top are 'A' times those at the bottom and 
 * those on the right are 'B' times those on the left. 
 * Now record all face labels. Run an iterative routine 
 * to get angle sums 2pi at all vertices. With luck,
 * we get an affine torus, with the arguments of the side 
 * pairings determined by the process (so, 2 real parameters 
 * result in 2 complex parameters).
 * 
 * Ken Stephenson
 */
public class ProjStruct extends PackExtender {
	public TriAspect[] aspects;
	public static double TOLER=.00000001;
	public static double OKERR=.0000000001; 
	public static int PASSES=10000;
	
	// Constructor
	public ProjStruct(PackData p) {
		super(p);
		packData=p;
		extensionType="PROJSTRUCT";
		extensionAbbrev="PS";
		toolTip="'ProjStruct' is for handling discrete projective structures, "+
			"that is, projective structures associated with circle packings.";
		registerXType();
		if (running) {
			aspects=setupAspects(packData);
			packData.packExtensions.add(this);
		}
	}
	
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		boolean debug=false;
		int count=0;
		
		// ======== torAB ===========
		if (cmd.startsWith("torAB")) {
			// this routine is tailored for tori: specify side-pair
			// scaling in an attempt to build general affine tori
			if (aspects==null || aspects.length!=(packData.faceCount+1))
				aspects=setupAspects(packData);

			if (packData.genus != 1 || packData.getBdryCompCount()>0) {
				int cnt=0;
				msg("Simply connected case: 'affine' defaults to all 'labels' 1");
				for (int f=1;f<=packData.faceCount;f++) {
					for (int j=0;j<3;j++) 
						aspects[f].labels[j]=1.0;
					cnt++;
				}
				return cnt;
			}

			// get the user-specified
			double A = 1.2; // default
			double B = .75;
			try {
				items = flagSegs.get(0);
				A = Double.parseDouble((String) items.get(0));
				B = Double.parseDouble((String) items.get(1));
			} catch (Exception ex) {
			}

			boolean result = affineSet(packData,aspects,A, B);
			if (!result)
				Oops("torAB has failed");
			msg("Affine data set: A = " + A + " B = " + B);
			return 1;
		}
		
		// ======== affpack ===========
		else if (cmd.startsWith("affpack")) {
			int passes=-1;
			try {
				items=flagSegs.get(0);
				passes=Integer.parseInt(items.get(0));
			} catch(Exception ex) {
				passes=-1;
			}
			EuclPacker e_packer=new EuclPacker(packData,-1);
			
			// set 'triData' to 'aspects' and repack using 'labels'
			e_packer.pdcel.triData=aspects;
			EuclPacker.affinePack(packData,passes);
			
			// store results as radii
			NodeLink vlist=new NodeLink();
		   	for (int i=0;i<e_packer.aimnum;i++) {
	    		vlist.add(e_packer.index[i]);
		   	}
		    // store local 'radii' in 'PackDCEL.triData' as radii 
			TriData.reapRadii(packData,vlist,1);
			return packData.packDCEL.layoutPacking();
		}
		
		// ======== weak_rif ===========
		else if (cmd.startsWith("weak_rif")) {
			NodeLink vlink=null;
			
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				vlink=new NodeLink(packData,items);
			} catch (Exception ex) {
				vlink=null;
			}
			
			// first, riffle to get weak consistency
			count = ProjStruct.vertRiffle(packData, aspects,2,PASSES,vlink);
			if (count < 0) {
				Oops("weak riffle seems to have failed");
				return 0;
			}
			
			// next, riffle to get angle sums (which should preserve weak consistency)
			count = ProjStruct.vertRiffle(packData, aspects,1,PASSES,vlink);
			if (count < 0) {
				Oops("riffle for aims seems to have failed");
				return 0;
			}
			
			if (debug) { // debug=true;
				BufferedWriter dbw = CPFileManager.openWriteFP(new File(System
						.getProperty("java.io.tmpdir")), new String("anglesum_"
						+ CPBase.debugID + "_log"), false);
				try {
					dbw.write("anglesum:\n\n");
					for (int v = 1; v <= packData.nodeCount; v++) {
						dbw.write("vertex " + v + ": " + 
								ProjStruct.angSumTri(packData,v,1.0,aspects)[0] + "\n");
					}
					dbw.flush();
					dbw.close();
				} catch (Exception ex) {
					throw new InOutException("anglesum_log output error");
				}

			}
			msg("affpack count = " + count);
			return count;
		}

		// ======== TorusData =========
		else if (cmd.startsWith("tD")) {
			TorusData torusData=getTorusData();
			if (torusData==null) 
				throw new CombException("failed to compute 'TorusData'; "+
						"is it in 2-sidepair form?");

			// print out torusData
			try {
				if (torusData.flat) { 
					CirclePack.cpb.msg("Flat Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
					}
				else {
					CirclePack.cpb.msg("Affine Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
				}
				for (int j=0;j<4;j++) 
					CirclePack.cpb.msg(torusData.cornerPts[j].toString());
				CirclePack.cpb.msg("Teich = "+torusData.teich.toString());
				CirclePack.cpb.msg("tau = "+torusData.tau.toString());
				CirclePack.cpb.msg("cross_ratio = "+torusData.x_ratio.toString());

				if (!torusData.flat)
					CirclePack.cpb.msg("Affine parameter 'c' = "+
						torusData.affCoeff.toString());
			} catch(Exception ex){}
			return 1;
		}
		
		// ======== status ==========
		else if (cmd.startsWith("stat")) {
			NodeLink vlist=null;
			double Angsum_err=0.0;
			double TLog_err=0.0;
			double SLog_err=0.0;
			count=0;
			
			// if one or more flags, report for just first object only
			if (flagSegs!=null && flagSegs.size()>0) {
				Iterator<Vector<String>> flgs=flagSegs.iterator();
				while (flgs.hasNext()) {
					items=flgs.next();
					String str=items.remove(0);
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						switch(c) {
						case 's': // strong consistency: (t.t') for edges
						{
							HalfLink hlist=new HalfLink(packData,items);
							if (hlist!=null && hlist.size()>0) {
								HalfEdge edge=hlist.get(0);
								msg("Edge <"+edge+">, t*t' = "+
									String.format("%.8e",
									edgeRatioError(packData,aspects,edge)));
								return 1;
							}
							break;
						}
						case 'c': // curvature error (angle sum-aim)
						{
							vlist=new NodeLink(packData,items);
							if (vlist!=null && vlist.size()>0) {
								int v=(int)vlist.get(0);
								msg("Angle sum error of "+v+" is "+
										String.format("%.8e",angsumError(v)));
								return 1;
							}
							break;
						}
						} // end of switch
					}
				}
				return 0; // didn't find valid flag??
			}
			
			// if no flags?
			
			// find sum[angsum-aim]^2 (for verts with aim>0)
			for (int v=1;v<=packData.nodeCount;v++) {
				double diff=angsumError(v);
				Angsum_err += diff*diff;
			}
				
			// report
			msg("Status: anglesum error norm = "+
					String.format("%.8e",Math.sqrt(Angsum_err)));
			msg("Edge ratio (Log(t.t')) error norm = "+
					String.format("%.8e",Math.sqrt(TLog_err)));
			msg("Weak consistency (Log(ll../rr..)) error norm = "+
					String.format("%.8e",Math.sqrt(SLog_err)));
			return count;
		}
		
		// ======== draw ============
		else if (cmd.startsWith("draw")) {
			boolean circs = false;
			boolean facs = false;
			String str = null;
			
			// no flags? default to '-fn'
			if (flagSegs==null || flagSegs.size()==0) {
				flagSegs=StringUtil.flagSeg("-fn");
			}
			try {
				Iterator<Vector<String>> fls = flagSegs.iterator();
				while (fls.hasNext()) {
					items = fls.next();
					str = items.remove(0);
					// check for faces or circles
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						switch(c){
						case 'c': {
							circs = true;
							break;
						}
						case 'f': {
							facs = true;
							break;
						}
						case 'B': 
						case 'b': {
							circs = true;
							facs = true;
							break;
						}
						case 'w': {
							cpCommand("disp -w");
							count++;
							break;
						}
						} // end of switch
					} // end of flag case
					
					// do what's ordered
					if (circs || facs) {
						DispFlags dispFlags=new DispFlags(str.substring(2),
								packData.cpScreen.fillOpacity); // cut out -?
						FaceLink facelist;
						if (items==null || items.size()==0) // do all
							facelist = new FaceLink(packData, "F");
						else 
							facelist=new FaceLink(packData,items);
						try {
							Iterator<Integer> flst = facelist.iterator();
							boolean first_face = true;
							while (flst.hasNext()) {
								int fnum = flst.next();
								TriAspect tasp = aspects[fnum];
								if (circs) {
									for (int j = 0; j < 3; j++) {
										if (!first_face)
											j = 2; // just one circle
										Complex z = tasp.getCenter(j);
										double rad = tasp.labels[j];
										int v = tasp.vert[j];
							
										if (!dispFlags.colorIsSet && 
												(dispFlags.fill || dispFlags.colBorder))
											dispFlags.setColor(packData.getCircleColor(v));
										if (dispFlags.label)
											dispFlags.setLabel(Integer.toString(v));
										packData.cpScreen.drawCircle(z, rad,dispFlags);
										count++;
									}
								}
								if (facs) {
									if (!dispFlags.colorIsSet && 
											(dispFlags.fill || dispFlags.colBorder))
										dispFlags.setColor(packData.getFaceColor(fnum));
									if (dispFlags.label)
										dispFlags.setLabel(Integer.toString(fnum));
									packData.cpScreen.drawFace(tasp.getCenter(0),
											tasp.getCenter(1),tasp.getCenter(2),
											null,null,null,dispFlags);
									count++;
								}
								first_face=false;
							} // end of while 

							PackControl.canvasRedrawer.
								paintMyCanvasses(packData,false);
						} catch (Exception ex) {
							Oops("affine drawing error");
						}
					}
				} // end of while on flagSegs
			} catch (Exception ex) {}
			return count;
		}

		// ======== error ============
		else if (cmd.startsWith("error")) {
			double []wsa_error=getErrors(packData,aspects);
			msg("Errors: weak, strong, angle sum: (l^2 and max):");
			msg(" weak: ("+String.format("%.6e",wsa_error[0])+
					", "+String.format("%.6e",wsa_error[1])+")");
			msg(" strong: ("+String.format("%.6e",wsa_error[2])+
					", "+String.format("%.6e",wsa_error[3])+")");
			msg(" angle sum: ("+String.format("%.6e",wsa_error[4])+
					", "+String.format("%.6e",wsa_error[5])+")");
			return 1;
		}

		// ======== log (in temp log file) ============
		else if (cmd.startsWith("log_rad")) {
			File logfile=new File(System.getProperty("java.io.tmpdir"),
				new String("labels_"+ CPBase.debugID + "_log"));
			BufferedWriter dbw = CPFileManager.openWriteFP(logfile,false,false);
			try {
				dbw.write("labels:\n\n");
				for (int f = 1; f <= packData.faceCount; f++) {
					int[] verts=packData.packDCEL.faces[f].getVerts();
					dbw.write("face " + f + ": <" + verts[0] + ","
							+ verts[1] + "," + verts[2] + ">   "
							+ "labels: <" + (double) aspects[f].labels[0] + ","
							+ aspects[f].labels[1] + "," + aspects[f].labels[2]
							+ ">\n");
				}
				dbw.flush();
				dbw.close();
				this.msg("Wrote labels_log to "+logfile.getCanonicalPath());
			} catch (Exception ex) {
				throw new InOutException("labels_log output error");
			}
			return 1;
		}
		
		// ======== TorusData =========
		if (cmd.startsWith("tD")) {
			TorusData torusData=getTorusData(packData,false);
			if (torusData==null) 
				throw new CombException("failed to compute 'TorusData'; "+
						"is it in 2-sidepair form?");

			// print out torusData
			try {
				if (torusData.flat) { 
					CirclePack.cpb.msg("Flat Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
					}
				else {
					CirclePack.cpb.msg("Affine Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
				}
				for (int j=0;j<4;j++) 
					CirclePack.cpb.msg(torusData.cornerPts[j].toString());
				CirclePack.cpb.msg("Teich = "+torusData.teich.toString());
				CirclePack.cpb.msg("tau = "+torusData.tau.toString());
				CirclePack.cpb.msg("cross_ratio = "+torusData.x_ratio.toString());

				if (!torusData.flat)
					CirclePack.cpb.msg("Affine parameter 'c' = "+
						torusData.affCoeff.toString());
			} catch(Exception ex){}
			
			return 1;
		}
		
		// ========== equiSides ==========
		
		else if (cmd.startsWith("equiSid")) {
			for (int f=1;f<=packData.faceCount;f++) {
				for (int j=0;j<3;j++)
					aspects[f].sides[j]=1.0;
			}
			return 1;
		}
		
		// ======== set_eff =========
		else if (cmd.startsWith("set_eff")) {
			if (setEffective(packData,aspects)<0)
				Oops("Error in setting effective radii.");
			return 1;
		}
		
		// ========== ccode ===========
		else if (cmd.startsWith("ccod")) {
			// currently we only color/draw all edges
			
			// TODO: we can now use 'HalfEdge.color'.
			
			// store data for qualifying edges in vector
			Vector<Double> edata=new Vector<Double>();
			for (int v=1;v<=packData.nodeCount;v++) {
				HalfLink spokes=packData.packDCEL.vertices[v].getEdgeFlower();
				Iterator<HalfEdge> sis=spokes.iterator();
				while (sis.hasNext()) {
					HalfEdge he=sis.next();
					int w=he.twin.origin.vertIndx;
					if (w>v && !he.isBdry()) 
						edata.add(ProjStruct.logEdgeTs(packData,
								he,aspects));
				}
			}
			
			Vector<Integer> ccodes=ColorUtil.blue_red_diff_ramp(edata);
			
			// draw (same order)
			int spot=0;
			for (int v=1;v<=packData.nodeCount;v++) {
				HalfLink spokes=packData.packDCEL.vertices[v].getEdgeFlower();
				Iterator<HalfEdge> sis=spokes.iterator();
				while (sis.hasNext()) {
					HalfEdge he=sis.next();
					int w=he.twin.origin.vertIndx;
					if (w>v) {
						if (packData.isBdry(v) && packData.isBdry(w))
							cpCommand("disp -e "+v+" "+w);
						else {
							cpCommand("disp -ec"+(int)ccodes.get(spot)+" "+v+" "+w);
							spot++;
						}
					}
				}
			}
			return 1;
		}
		
		// ======== Lface ==========
		else if (cmd.startsWith("Lface")) {
			DispFlags dflags=new DispFlags("");
			for (int f=1;f<=packData.faceCount;f++) {
				packData.cpScreen.drawFace(aspects[f].getCenter(0),
						aspects[f].getCenter(1),
						aspects[f].getCenter(2),null,null,null,dflags);
			}
			repaintMe();
			return 1;
		}
		
		// ======== LinCircs ========
		if (cmd.startsWith("LinC")) {
			for (int f=1;f<=packData.faceCount;f++) {
				CircleSimple sc=EuclMath.eucl_tri_incircle(aspects[f].getCenter(0),
						aspects[f].getCenter(1),aspects[f].getCenter(2));
				DispFlags dflags=new DispFlags("cc20");
				packData.cpScreen.drawCircle(sc.center,sc.rad,dflags); // blue
			}
			repaintMe();
			return 1;
		}
		
		// ======== Ltree (dual spanning tree) ===========
		else if (cmd.startsWith("Ltree")) {
			Iterator<HalfEdge> ft=pdc.layoutOrder.iterator();
			while (ft.hasNext()) {
				HalfEdge edge=ft.next();
				int f=edge.face.faceIndx;
				int g=edge.twin.face.faceIndx;
				CircleSimple sc;
				sc=EuclMath.eucl_tri_incircle(aspects[f].getCenter(0),
							aspects[f].getCenter(1),aspects[f].getCenter(2));
				Complex vc=sc.center;
				sc=EuclMath.eucl_tri_incircle(aspects[g].getCenter(0),
						aspects[g].getCenter(1),aspects[g].getCenter(2));
				Complex wc=sc.center;
				DispFlags df=new DispFlags(null);
				df.setColor(Color.green);
				packData.cpScreen.drawEdge(vc,wc,df);
			}
			repaintMe();
			return 1;
		}
		
		// ========= set_labels =======
		else if (cmd.startsWith("set_lab")) {
			// no flags? default to '-r', based on radii
			if (flagSegs==null || flagSegs.size()==0) {
				flagSegs=StringUtil.flagSeg("-r"); 
			}
			FaceLink facelist;
			count=0;
			try {
				Iterator<Vector<String>> fls = flagSegs.iterator();
				while (fls.hasNext()) {
					items = fls.next();
					// get option
					String str = items.remove(0);
					if (!StringUtil.isFlag(str))
						return -1;
					char c=str.charAt(1);
					// get facelist iterator
					if (items==null || items.size()==0) // do all
						facelist = new FaceLink(packData, "a");
					else 
						facelist=new FaceLink(packData,items);
					Iterator<Integer> flt=facelist.iterator();
					
					switch(c) {
					case 'r':  { // use current radii
						while (flt.hasNext()) {
							int f=flt.next();
							HalfEdge he=aspects[f].baseEdge;
							int j=0;
							do {
								aspects[f].labels[j]=packData.packDCEL.getVertRadius(he);
								j++;
								he=he.next;
							} while (j<3);
							count++;
						}
						break;
					}
					case 's': { // random
						while (flt.hasNext()) {
							int f=flt.next();
							aspects[f].randomRatio();
							count++;
						}
						break;
					}
					case 'z': { // use stored centers 
						while (flt.hasNext()) {
							int f=flt.next();
							aspects[f].centers2Labels();
							count++;
						}
						break;
					}
					} // end of switch
				} // end of while
			} catch (Exception ex) {
				Oops("Error setting 'labels': "+ex.getMessage());
			}
			return count;
		}

		// ============= update ===============
		else if (cmd.startsWith("updat")) {
			Iterator<Vector<String>> flgs=flagSegs.iterator();
			while (flgs.hasNext()) {
				items=flgs.next();
				String str=items.get(0);
				if (StringUtil.isFlag(str)) {
					char c=str.charAt(1);
					items.remove(0);
					FaceLink flist=new FaceLink(packData,items);
					Iterator<Integer> fls=flist.iterator();
					switch(c) {
					case 's': // update sides using packData centers  
					{
						while (fls.hasNext()) {
							int f=fls.next();
							aspects[f].centers2Sides();
						}
						break;
					}
					case 'l': // update labels using sides
					{
						while (fls.hasNext()) {
							int f=fls.next();
							aspects[f].sides2Labels();
						}
						break;
					}
					} // end of switch
				}
			} // end of while
			return 1;
		}
		
		// ========= sideRif =============
		
		else if (cmd.startsWith("sideRif")) {
			NodeLink vlink=null;
			
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				vlink=new NodeLink(packData,items);
			} catch (Exception ex) {}
			
			// riffle to get side lengths
			int its=sideRiffle(packData,aspects,2000,vlink);
			msg("'sideRif' iterations: "+its);
			
			// reset 'labels' vector from 'sides'
			for (int f=1;f<=packData.faceCount;f++) 
				aspects[f].sides2Labels();
			return 1;
		}

		// ========== set_screen ======
		else if (cmd.startsWith("set_scre")) {
			double mnX=100000.0;
			double mxX=-100000.0;
			double mnY=100000.0;
			double mxY=-100000.0;
			double pr;
			for (int f = 1; f <= packData.faceCount; f++)
				for (int j = 0; j < 3; j++) {
					pr=aspects[f].getCenter(j).x-aspects[f].labels[j];
					mnX = (pr<mnX) ? pr : mnX; 
					mxX = (pr>mxX) ? pr : mxX; 
					mnY = (pr<mnY) ? pr : mnY; 
					mxY = (pr>mxY) ? pr : mxY; 
				}
			cpCommand("set_screen -b "+mnX+" "+mnY+" "+mxX+" "+mxY);
			packData.cpScreen.repaint();
			return 1;
		}
				
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Compute skew and its derivative at 'v' face-by-face using 'labels'
	 * with labels at v multiplied by factor t>0. Also, return
	 * the derivative w.r.t. t.
	 * @param p @see PackData
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @param asps[] @see TriAspect
	 * @return double[2]: [0]=skew, [1]=deriv
	 */
	public static double []skewTri(PackData p, int v,double t,
			TriAspect[] asps) {
		double []ans = new double[2];
		int[] faceFlower=p.getFaceFlower(v);
		for (int j = 0; j < p.countFaces(v); j++) {
			int f = faceFlower[j];
			double []sd= asps[f].skew(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}
	
	/** 
	 * Return weak consistency error for interior 'v'.
	 * This is product of leftlength/rightlength for all 
	 * faces in star(v).
	 * @param p @see PackData
	 * @param aspects []TriAspect
	 * @param v int, vertex
	 * @return double, 1.0 if v not interior.
	 */  
	public static double weakConError(PackData p,
			TriAspect []aspects,int v) {
		if (p.isBdry(v))
			return 1.0;
		double rtio=1.0;
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int ff=faceFlower[j];
			int k=aspects[ff].vertIndex(v);
			rtio *= aspects[ff].sides[(k+2)%3]; // left sidelength
			rtio /= aspects[ff].sides[k]; // right sidelength
		}
		return rtio;
	}	

	/**
	 * Compute weak, strong consistency, and angle sum errors, both in l^2
	 * and sup norm.
	 * @return double[6]: 
	 *   [0]=weak l^2;[1]=weak max (among vertices);
	 *   [2]=strong l^2;[3]=strong max (among edge); 
	 *   [4]=angle sum l^2;[5]=angle sum max
	 */
	public static double []getErrors(PackData p,TriAspect []aspects) {
		double weak_err=0.0;
		double weak_max=0.0;
		double TLog_err=0.0;
		double TLog_max=0.0;
		double ang_err=0.0;
		double ang_max=0.0;
		double []ans=new double[6];

		for (int v=1;v<=p.nodeCount;v++) {
			// Strong: find sum[|Log(t.t')|]^2 for interior edges
			HalfLink vspokes=p.packDCEL.vertices[v].getEdgeFlower();
			Iterator<HalfEdge> sis=vspokes.iterator();
			while (sis.hasNext()) {
				HalfEdge he=sis.next();
				int w=he.twin.origin.vertIndx;
				if (w>v && !he.isBdry()) {
					double prd=Math.abs(Math.log(Math.abs(
							edgeRatioError(p,aspects,he))));
					TLog_max=(prd>TLog_max) ? prd:TLog_max;
					TLog_err += prd*prd;
				}
			}

			// weak;
			double werr=Math.abs(Math.log(
					ProjStruct.weakConError(p,aspects,v)));
			weak_err += werr*werr;
			weak_max=(werr>weak_max) ? werr:weak_max;
			
			// angle sum
			double ang=Math.abs(angSumTri(p,v,1.0,aspects)[0]-p.getAim(v));
			ang_err += ang*ang;
			ang_max=(ang>ang_max) ? ang:ang_max;
		}
		ans[0]=Math.sqrt(weak_err);
		ans[1]=weak_max;
		ans[2]=Math.sqrt(TLog_err);
		ans[3]=TLog_max;
		ans[4]=Math.sqrt(ang_err);
		ans[5]=ang_max;
		
		return ans;
	}

	/**
	 * Return angle sum error at 'v' based on TriAspect 'labels'.
	 * @param v int, vertex index in packing
	 * @return double, abs(error); 0 if 'aim' <=0
	 */
	public double angsumError(int v) {
		if (packData.getAim(v)<=0)
			return 0;
		return Math.abs(angSumTri(packData,v,1.0,
				aspects)[0]-packData.getAim(v));
	}
	
	/**
	 * For debugging.
	 * @param p PackData
	 * @param fnum int
	 * @param asp TriAspect[]
	 */
	public static void printRadRatios(PackData p,int fnum,TriAspect []asp) {
		int[] vts=p.packDCEL.faces[fnum].getVerts();
		double r0=p.getRadius(vts[0]);
		double r1=p.getRadius(vts[1]);
		double r2=p.getRadius(vts[2]);
		System.out.println("face "+fnum+": <"+vts[0]+","+
				vts[1]+","+vts[2]+">");
		System.out.println("   labels:   "+1+",  "+r1/r0+",  "+r2/r0);
		double rat0=asp[fnum].labels[0];
		System.out.println("   labels:  "+1+",  "+
				asp[fnum].labels[1]/rat0+",  "+
				asp[fnum].labels[2]/rat0);
	}
	
	/**
	 * Compute angle sum at 'v' face-by-face using 'labels'
	 * with labels at v multiplied by factor t>0. Also, return
	 * the derivative w.r.t. t.
	 * @param p @see PackData
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @param asps[] @see TriAspect
	 * @return double
	 */
	public static double []angSumTri(PackData p, int v, double t,
			TriAspect[] asps) {
		double []ans=new double[2];
		int[] faceFlower=p.getFaceFlower(v);
		for (int j = 0; j < p.countFaces(v); j++) {
			int f = faceFlower[j];
			double []sd= asps[f].angleV(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}

	/**
	 * Return 'edge' consistency error computed from 'labels'. 
	 * For interior edge, this is t*t', where t is the ratio 
	 * of 'labels' for 'edge' in lefthand face, t' is that of
	 * righthand face. 
	 * @param edge HalfEdge
	 * @param p PackData,
	 * @param asps TriAspect[]
	 * @return 1.0 if not interior edge.
	 */
	public static double edgeRatioError(PackData p,
			TriAspect []asps,HalfEdge edge) {
		if (edge.isBdry()) // bdry edge 
			return 1.0;
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		int lface=edge.face.faceIndx;
		int rface=edge.twin.face.faceIndx;
		int lj=asps[lface].vertIndex(v);
		int rj=asps[rface].vertIndex(w);
		double prd=asps[lface].labels[(lj+1)%3];
		prd /=asps[lface].labels[lj];
		prd *=asps[rface].labels[(rj+1)%3];
		prd /=asps[rface].labels[rj];
		return prd;
	}
	

	/**
	 * For setting prescribed parameters for affine torus
	 * construction.
	 * @param p PackData
	 * @param A double
	 * @param B double
	 * @return boolean true
	 */
	public static boolean affineSet(PackData p,TriAspect[] asps,
			double A,double B) {
		if (p.getSidePairs().size()>5) { // want just 2 side-pairings
			CombDCEL.torus4Sides(p.packDCEL);
			p.packDCEL.fixDCEL(p);
		}
		if (p.getSidePairs().size()!=5) {
			throw new CombException("failed to layout 2-side paired edges");
		}
		
		if (asps==null) {
			asps=setupAspects(p);
		}
		
		// first set all 'label's to 1.0
		for (int j=1;j<=p.faceCount;j++) {
			for (int k=0;k<3;k++)
				asps[j].labels[k]=1.0;
		}
		
		/* Idea: prescribe (via A and B) the 'labels' for vertices 
		 * along the outside of the red chain. Use these and 
		 * existing interior labels in 'aspects'.
		 * When there are just two side-pairings (preferable),
		 * want A to be scale factor from #1 to #3, and B to be 
		 * scale factor from #2 to #4. TODO: ??? verify that this
		 * lines up with the generic (3 side-pairing) situation.
		 */
		SideData side=p.getSidePairs().get(3);
		RedEdge rtrace=side.startEdge;
		do {
			int f=rtrace.myEdge.face.faceIndx;
			int j=asps[f].vertIndex(rtrace.myEdge.origin.vertIndx);
			asps[f].labels[j] *=A;
			rtrace=rtrace.nextRed;
		} while(rtrace!=side.endEdge.nextRed);
		
		side=p.getSidePairs().get(4);
		rtrace=side.startEdge;
		do {
			int f=rtrace.myEdge.face.faceIndx;
			int j=asps[f].vertIndex(rtrace.myEdge.origin.vertIndx);
			asps[f].labels[j] *=B;
			rtrace=rtrace.nextRed;
		} while(rtrace!=side.endEdge.nextRed);
		return true;
	}
	
	/**
	 * Compute angle sum at 'v' face-by-face using 'labels'
	 * stored in 'triData' with current label at v multiplied 
	 * by factor t>0. Also, return the derivative w.r.t. t.
	 * TODO: also use packData inv distances.
	 * @param p PackData
	 * @param asp TriData[], case to 'TriAspect'
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @return double
	 */
	public static double[] labelAngSum(PackData p,TriData[] trid,
			int v, double t) {
		double []ans=new double[2];
		int[] faceFlower=p.getFaceFlower(v);
		for (int j = 0; j < faceFlower.length; j++) {
			int f = faceFlower[j];
			double[] sd= ((TriAspect)trid[f]).angleV(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}
	
	/**
	 * Change label for v by multiplicative 'factor' in 
	 * all faces containing 'v' 
	 * @param p PackData 
	 * @param v int
	 * @param factor double
	 * @param trid TriData[], cast to 'TriAspect'
	 * @return int 1
	 */
	public static int adjustLabel(PackData p,TriData[] trid,
			int v,double factor) {
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=trid[f].vertIndex(v);
			trid[f].labels[k] *=factor;
		}
		return 1;
	}
	/**
	 * Change side lengths at v by 'factor' in every face containing v.
	 * @param p PackData 
	 * @param v vertex
	 * @param factor double
	 * @param asp []TriAspect
	 * @return 1
	 */
	public static int adjustSides(PackData p,int v,double factor,
			TriAspect []asp) {
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<p.countFaces(v);j++) {
			int f=faceFlower[j];
			int k=asp[f].vertIndex(v);
			asp[f].sides[k] *=factor;
			asp[f].sides[(k+2)%3] *=factor;
		}
		return 1;
	}
	
	
	/** 
	 * Eucl iterative routines copied from 'oldReliable', but
	 * now applied to 'labels' face-by-face. For each vertex, 
	 * a factor t>0 is computed and the label at 'v' is 
	 * multiplied by t in every face containing v. There are 
	 * two goals with this same process, differing only in the
	 * objective functions we want to minimize.
	 * 
     * mode == 2: This adjusts to get "weak" consistency. A triangulation 
     * is weakly consistent at 'v' if there is a scaling one can
     * apply to the faces around 'v' so that successive shared edges are the 
     * same. (They could be consistently laid out so the first edge
	 * equals the last edge.) This does not mean that these scaled faces
	 * would close up, since the angle sum may not be a multiple of 2pi. The
	 * labels are weakly consistent if weakly consistent at every interior
	 * vertex 'v'.
	 * 
	 * If f is a face with homogeneous ordered triple (a,b,c) of local labels, 
	 * then consider h(f) = h(a,b,c) = log((a+c)/(a+b)) (which is homogeneous). 
	 * The "skew" at 'v' is Sum(h(f)) over all faces f at v and where a 
	 * designates the label at v. The labels are weakly consistent at v
	 * iff the skew is 0. We get this by finding scale value t>0 so that 
	 * Sum(h(ta,b,c) = 0, for which we use Newton iterations. 
	 * 
	 * I think this process is convex (if run correctly and with possible
	 * boundary considerations). In fact, it seems related to the "conformal" 
	 * notion of Liu and others where weights are put on the vertices.
	 * 
	 * mode == 1 (default): This adjusts to get the specified aims at the
	 * vertices: We assume that the label is weakly consistent. As in mode=1,
	 * we find factor t>0 so that multiplying the 'labels' at v in all faces
	 * containing v, we get the angle sum at v to equal the prescribed 'aim'. 
	 * NOTE: It is a pleasant fact that such adjustments preserve weak
	 * consistency. Currently, in mode 1 we do all vertices with aim>0, 
	 * whether bdry or interior.
	 * (See also 'sideRiffle' in @see AffinePack.)
	 * 
     * @param p PackData (should be eucl)
     * @param aspts []TriAspects, face data
     * @param mode int: type of riffle, 1 = angsum, 2 = weak consistency
     * @param passes int, limit to iterations.
     * @param myList @see LinkedList, vertices to be adjusted
     * @return count of iterations, -1 on error
     */
	public static int vertRiffle(PackData p,TriAspect []aspts,int mode,int passes,
			LinkedList<Integer> myList) {
		int v;
		int count = 0;
		NodeLink vlist=null;
		if (myList!=null && (myList instanceof NodeLink))
			vlist=(NodeLink)myList;
		boolean debug=false;
	      
		int aimNum = 0;
		int[] inDex =new int[p.nodeCount+1];
		// only verts with aim>0 for mode 1 
		//    or interior for mode 2 and in the list
		for (int vv=1;vv<=p.nodeCount;vv++) {
			if (((mode==1 && p.getAim(vv)>0) || 
					(mode==2 && !p.packDCEL.vertices[vv].isBdry())) 
					&& (vlist==null || vlist.contains(Integer.valueOf(vv)))) {
				inDex[aimNum]=vv;
				aimNum++;
			}
		}
		if (aimNum==0) 
			return -1; // nothing to repack
	      
	    // compute initial errors and set cutoff value
	    double accum=0.0;
	    for (int j=0;j<aimNum;j++) {
	    	v=inDex[j];
	    	if (mode==1)  // mode=1, default
	    		accum += Math.abs(ProjStruct.angSumTri(p,v,1.0,aspts)[0]-p.getAim(v));
	    	else if (mode==2) 
	    		accum += Math.abs(ProjStruct.skewTri(p,v,1.0,aspts)[0]);
	    }
	    double recip=.333333/aimNum;
	    double cut=accum*recip;
	    if (cut<TOLER) return 1;

	    // now cycle through adjustments --- riffle
	    while ((cut > TOLER && count<passes)) {
	    	double verr=0.0;
	    	try {
	    		if(debug) {
	    			HalfEdge he=p.packDCEL.findHalfEdge(new EdgeSimple(17,24));
	    			if (he!=null)
	    				System.out.println(Math.log(Math.abs(
	    						ProjStruct.edgeRatioError(p,aspts,he))));
	    		}
	    	} catch (Exception ex){}
	    	
	    	for (int j=0;j<aimNum;j++) {
	    		v=inDex[j];
	    		double vAim=p.getAim(v);
	    		  
	    		// find/apply factor to labels at 'v' if error 
	    		//    is bad enough to riffle
	    		if (mode==1)  // mode=1, default
	    			verr = Math.abs(ProjStruct.angSumTri(p,v,1.0,aspts)[0]-p.getAim(v));
	    		else if (mode==2) 
	    			verr = Math.abs(ProjStruct.skewTri(p,v,1.0,aspts)[0]);
	    		if (Math.abs(verr)>cut) {
	    			double []valder=new double[2];
	    			if (mode==1) {
	    				valder=ProjStruct.angSumTri(p,v,1.0,aspts);
	    				valder[0] -= vAim;
	    			}
	    			else if (mode==2) {
	    				valder=ProjStruct.skewTri(p,v,1.0,aspts);
	    			}
	    			
	    			// start error
    				if (debug) {
    					System.err.println(" v="+v+", start error = "+
    							Math.abs(valder[0]));
    				}
    				  
	    			// use one Newton step, but restrict to [.5,2].
	    			double factor = 1.0 - valder[0]/valder[1];
	    			if (factor<.5)
	    				factor=.5;
	    			if (factor>2.0)
	    				factor=2.0;
    				  
	    			// make the change
	    			ProjStruct.adjustRadii(p,v,factor,aspts);
	    			
	    			// new error
    				if (debug) {
    					double []vd=new double[2];
    	    			if (mode==1) {
    	    				vd=ProjStruct.angSumTri(p,v,1.0,aspts);
    	    				vd[0] -= vAim;
    	    			}
    	    			else if (mode==2) {
    	    				vd=ProjStruct.skewTri(p,v,1.0,aspts);
    	    			}
    					System.err.println("   v="+v+", new error = "+
    							Math.abs(vd[0]));
    				}
	    		}
	    	}
    			  
	    	// update states, accum error
	    	accum=0;
	    	for (int jj=0;jj<aimNum;jj++) {
	    		int V=inDex[jj];
	    		v=Math.abs(V);
	    		if (mode==1) { // mode=1, default
	    			accum += Math.abs(ProjStruct.angSumTri(p,v,1.0,aspts)[0]-p.getAim(v));
	    		}
	    		else if (mode==2) {
	    			accum += Math.abs(ProjStruct.skewTri(p,v,1.0,aspts)[0]);
	    		}
	    	}
	    	cut=accum*recip;
	    	count++;
	    } /* end of while */
	      
	    return count;
	} 


	/** 
	 * Eucl riffle of side lengths to get aims: for each vertex v, 
	 * find factor f>0 so that multiplying all 'sides' from v by f 
	 * in all faces containing v gives the 'aim' anglesum at v. 
	 * Currently, do all vertices with aim>0, whether bdry or 
	 * interior.
	 * 
     * Routines are patterned after 'oldReliable' euclidean
     * routine, but computation of angle sums and adjustment
     * of radii are done face-by-face, data is kept in 'aspects'.
     * 
     * Note: calling routine responsible for adjusting face 'labels'
     * according to new 'sides'.
     * 
     * @param p PackData (should be eucl)
     * @param aspts []TriAspects 
     * @param passes int, limit to iterations.
     * @param myList LinkedList: if 'NodeLink', riffle only these verts
     * @return int count of iterations, -1 on error
     */
	public static int sideRiffle(PackData p, TriAspect[] aspts,int passes,
			LinkedList<Integer> myList) {
		int v;
		int count = 0;
		double verr, err;
		double[] curv = new double[p.nodeCount + 1];
		NodeLink vlist = null;
		if (myList != null && (myList instanceof NodeLink))
			vlist = (NodeLink) myList;

		int aimNum = 0;
		int[] inDex = new int[p.nodeCount + 1];
		for (int vv = 1; vv <= p.nodeCount; vv++) {
			// TODO: can speed up with temp matrix instead of search of vlist
			if (p.getAim(vv) > 0
					&& (vlist == null || 
					vlist.contains(Integer.valueOf(vv)))) {
				inDex[aimNum] = vv;
				aimNum++;
			}
		}
		if (aimNum == 0)
			return -1; // nothing to repack

		// compute initial curvatures
		for (int j = 0; j < aimNum; j++) {
			v = inDex[j];
			curv[v] = ProjStruct.angSumSide(p, v, 1.0,aspts);
		}

		// set cutoff value
		double accum = 0.0;
		for (int j = 0; j < aimNum; j++) {
			v = inDex[j];
			err = curv[v] - p.getAim(v);
			accum += (err < 0) ? (-err) : err;
		}
		double recip = .333333 / aimNum;
		double cut = accum * recip;

		// now cycle through adjustments --- riffle
		while ((cut > TOLER && count < passes)) {
			for (int j = 0; j < aimNum; j++) {
				v = inDex[j];
				curv[v] = ProjStruct.angSumSide(p, v,1.0,aspts);
				verr = curv[v] - p.getAim(v);

				// find/apply factor to radius or sides at v
				if (Math.abs(verr) > cut) {
					double sideFactor = sideCalc(p,v, p.getAim(v), 5,
							aspts);
					ProjStruct.adjustSides(p,v, sideFactor,aspts);
					curv[v] = ProjStruct.angSumSide(p, v,1.0, aspts);
				}
			}
			accum = 0;
			for (int j = 0; j < aimNum; j++) {
				int V = inDex[j];
				v = Math.abs(V);
				curv[v] = ProjStruct.angSumSide(p, v, 1.0,aspts);
				err = curv[v] - p.getAim(v);
				accum += (err < 0) ? (-err) : err;
			}
			cut = accum * recip;

			count++;
		} /* end of while */

		return count;
	}

	/** 
	 * Find adjustment to 'sides' in faces at 'v' to move
	 * anglesum closer to aim; use secant method, with limit
	 * on max increase or decrease (so repeated calls may be 
	 * needed). Calling routine responsible for actually 
	 * implementing changes in 'sides' stored in 'asps'.
	 * @param p PackData
	 * @param v vertex
	 * @param aim at v
	 * @param N int limit on iterations
	 * @param asps []TriAspect
	 * @return best double
	*/
	public static double sideCalc(PackData p,int v,double aim,int N,
			TriAspect []asps) {
		double bestcurv,upcurv,lowcurv;
		double lower,upper;
		double limit=0.5;
		double best=1.0;

		// starting curvature
		bestcurv=lowcurv=upcurv=ProjStruct.angSumSide(p,v,best,asps);
		
		if (Math.abs(bestcurv-aim)<=OKERR)
			return 1.0;
		
		// set upper/lower limits on possible factors due to triangle inequality
		double []bds=ProjStruct.sideBounds(p,v,asps);
		lower=1.0-(1-bds[0])*limit; // interpolate between bds[0] and 1
		upper=1.0+(bds[1]-1.0)*limit; // interpolate between 1 and bds[1]
		
		// does lowest allowed factor undershoot?
		if (bestcurv<(aim-OKERR)) { 
			lowcurv=ProjStruct.angSumSide(p,v,lower,asps);
			if (lowcurv<aim) { // still not enough, but return
				return lower;
			}
		}

		// does largest allowed factor overshoot?
		else if (bestcurv>(aim+OKERR)) {  
			upcurv=ProjStruct.angSumSide(p,v,upper,asps);
			if (upcurv>aim) { // still not enough, but return
				return upper; 
			}
		}
		
		// successive interpolation adjustments 
		for (int n=1;n<=N;n++) {
			if (bestcurv>(aim+OKERR)) {
				lower=best;
				lowcurv=bestcurv;
				best += (aim-bestcurv)*(upper-best)/(upcurv-bestcurv);
			}
			else if (bestcurv<(aim-OKERR)) {
				upper=best;
				upcurv=bestcurv;
				best -= (bestcurv-aim)*(lower-best)/(lowcurv-bestcurv);
			}
			else {
				return best;
			}
			
			// angle sum with current 'best' factor
			bestcurv=ProjStruct.angSumSide(p,v,best,asps);
		}
		return best;
	}
	
	/**
	 * Compute angle sum at v face-by-face using the TriAspect
	 * 'sides' data, with sides ending at v multiplied by 'factor'.
	 * @param p PackData
	 * @param v int
	 * @param factor double
	 * @param asps []TriAspect
	 * @return angsum double
	 */
	public static double angSumSide(PackData p,int v,double factor,
			TriAspect []asps) {
		double angsum=0.0;
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=asps[f].vertIndex(v);
			double s0=factor*asps[f].sides[k];
			double s1=asps[f].sides[(k+1)%3];
			double s2=factor*asps[f].sides[(k+2)%3];
			angsum += Math.acos((s0*s0+s2*s2-s1*s1)/(2.0*s0*s2));
		}
		return angsum;
	}
	
	/**
	 * Compute "effective" radii (Gerald Orick's term) from centers
	 * and store as packing radii: NOTE: use centers because 'labels'
	 * are in homogeneous coordinates; this doesn't have much meaning
	 * under side-pairing in multiply-connected situations.
	 * 
	 * Radius r=r(v) at v satisfies theta(v)*r^2/2 = sum of areas of 
	 * sectors of faces at v and theta(v) is their sum.
	 * @param PackData p
	 * @param asp[], TriAspect
	 * @return, count of circles, -1 on error
	 */
	public static int setEffective(PackData p,TriAspect []asp) {
		int count=0;
		int []cck=new int[p.nodeCount+1];
		try {
			for (int f=1;f<=p.faceCount;f++) {
				int j=0;
				HalfEdge he=asp[f].baseEdge;
				do {
					int v=asp[f].vert[j];
					if (cck[v]==0) { // have to process this vertex
						double areaSum=0.0;
						double angSum=0.0;
						HalfLink spokes=p.packDCEL.vertices[v].getEdgeFlower();
						Iterator<HalfEdge> sis=spokes.iterator();
						while (sis.hasNext()) {
							int fv=sis.next().face.faceIndx;
							areaSum += asp[fv].sectorAreaZ(v);
							angSum +=asp[fv].angleV(v,1.0)[0];
						}
						p.packDCEL.setRad4Edge(he,Math.sqrt(2.0*areaSum/angSum));
						cck[v]=1;
					}
					j++;
					he=he.next;
				} while(j<3);
				count++;
			} 
		} catch (Exception ex) {
			throw new DataException("Error in 'effective rad' comp: "+
		ex.getMessage());
		}
		return count;
	}
	
	/**
	 * create the 'aspects' array for 'p', putting current
	 * radii/centers from packData into 'TriData.labels'
	 * and 'TriAspect.center' slots.
	 * @param p PackData
	 */
	public static TriAspect[] setupAspects(PackData p) {
		TriAspect[] asp=new TriAspect[p.faceCount+1];
		for (int f=1;f<=p.faceCount;f++) {
			asp[f]=new TriAspect(p.hes);
			TriAspect tas=asp[f];
			tas.baseEdge=p.packDCEL.faces[f].edge;
			tas.face=tas.baseEdge.face.faceIndx;
			tas.vert=p.packDCEL.faces[f].getVerts();
			HalfEdge he=tas.baseEdge;
			int tick=0;
			do {
				tas.labels[tick]=p.packDCEL.getVertRadius(he);
				tas.center[tick++]=p.packDCEL.getVertCenter(he);
				he=he.next;
			} while (he!=tas.baseEdge);

			// set 'sides' from 'center's
			tas.centers2Sides();
		}
		return asp;
	}
		
	/**
	 * Put a torus in normalized position and compute 
	 * various data for 'TorusData' structure. Calling
	 * routine should have arranged 2 side-pairings and 
	 * repacked and laid out the affine packing.
	 * @param p PackData 
	 * @param writ boolean; false, don't write output messages.
	 * @return TorusData object or null on error
	 */
	public static TorusData getTorusData(PackData p,boolean writ) {
		TorusData torusData=new TorusData();
		Complex[] Z=new Complex[4];

		PairLink plink=p.packDCEL.pairLink;
		if (plink==null || plink.size()!=5) 
			throw new ParserException("torus appears to have no "+
					"'pairLink' or there are not two side-pairings.");
		for (int j=1;j<=4;j++) {
			SideData sdata=plink.get(j);
			Z[j-1]=sdata.startEdge.getCenter();
		}
		
		// make sure the torus is normalized
		Mobius mob=Mobius.mob_NormQuad(Z);
		for (int j=0;j<4;j++)
			Z[j]=mob.apply(Z[j]);
		torusData.cornerPts=Z;
		
		torusData.x_ratio=torusData.cornerPts[0].minus(torusData.cornerPts[1]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[3])).
				divide(torusData.cornerPts[0].minus(torusData.cornerPts[3]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[1])));
				// cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
		
		Mobius.mobiusDirect(p,mob);
		
		torusData.mean=new Complex(0.0);
		for (int i=0;i<4;i++) {
			torusData.mean.add(torusData.cornerPts[i].times(.25));
		}

		// compute Teichmuller parameter 'T' and the modulus 'tau'
		//   and the affine parameter 'c' of Sass.
		torusData.a=Math.log(torusData.cornerPts[1].abs());
		torusData.M=Math.log(torusData.cornerPts[3].abs());
		torusData.affCoeff=new Complex(torusData.a,torusData.b);
		double x=(torusData.a*torusData.M+torusData.b*torusData.N)/torusData.affCoeff.absSq();
		double y=(torusData.a*torusData.N-torusData.b*torusData.M)/torusData.affCoeff.absSq();
		torusData.teich=new Complex(x,y);
		torusData.tau=TorusModulus.Teich2Tau(torusData.teich);
	
		return torusData;
	}
	
	/**
	 * Given an interior edge <v,w> find log of ratio of
	 * its t values: If f,g are the left/right faces,
	 * t_f is labels[w]/labels[v] in f, t_g is 
	 * labels[v]/labels[w] in g.
	 * @param p PackData 
	 * @param asp TriAspect
	 * @param edge HalfEdge
	 * @return abs(log(t_f/t_g)), -1 on error, 0 for bdry edge.
	 */
	public static double logEdgeTs(PackData p,HalfEdge edge,
			TriAspect []asp) {
		if (edge.isBdry())
			return 0;
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		int f=edge.face.faceIndx;
		int g=edge.twin.face.faceIndx;
		int j=asp[f].vertIndex(v);
		int k=asp[g].vertIndex(w);
		double lg=Math.log(asp[f].labels[(j+1)%3]*asp[g].labels[(k+1)%3])-
				Math.log(asp[f].labels[j]*asp[g].labels[k]);
		return Math.abs(lg);
	}

	/**
  	 * Return upper/lower bounds on factor by which sides 
  	 * in 'asps' can be adjusted at v while preserving the 
  	 * triangle inequality for all faces containing v.
  	 * @param p PackData
  	 * @param v vertex
  	 * @param asps TriAspect[]
  	 * @return int[2]: [0]=lower; [1]=upper
  	 */
  	public static double []sideBounds(PackData p,int v,TriAspect []asps) {
  		double lower=0.0;
  		double upper=100000000;
  		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=asps[f].vertIndex(v);
			double rSide=asps[f].sides[k];
			double lSide=asps[f].sides[(k+2)%3];
			double oppSide=asps[f].sides[(k+1)%3];
			if ((rSide+lSide)<oppSide || oppSide<Math.abs(rSide-lSide))
				throw new DataException(
						"Triangle inequality fails for face "+f);
			double a=oppSide/(lSide+rSide);
			lower=(a>lower) ? a : lower;
			double b=oppSide/Math.abs(rSide-lSide);
			upper=(b<upper) ? b : upper;
		}
  		double []ans=new double[2];
  		ans[0]=lower;
  		ans[1]=upper;
  		return ans;
  	}
 	  
	/**
	 * Change label for v in all faces containing 'v' 
	 * by multiplicative 'factor'.
	 * 
	 * TODO: Is this OBE? do I want to change radius in packing?
	 * @param p PackData 
	 * @param v int, parent vertex
	 * @param factor double
	 * @param asp TriAspect[]
	 * @return int -1 on error
	 */
	public static int adjustRadii(PackData p,int v,double factor,
			TriAspect []asp) {
//		p.setRadius(v,factor*p.getRadius(v));
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=asp[f].vertIndex(v);
			asp[f].labels[k] *=factor;
		}
		return 1;
	}
	
	/**
	 * Normalize an existing torus packing, update rad/cents,
	 * update side pair mobius. 
	 * @param p PackData
	 * @return Mobius
	 */
	public static Mobius normalizeTorus(PackData p) {
		PackDCEL pdcel=p.packDCEL;
		Mobius mob=null;
		if (pdcel.pairLink==null || pdcel.pairLink.size()!=5 ||
				pdcel.idealFaceCount!=0 || p.genus!=1) {
			CirclePack.cpb.msg("packing is not a 2-sidepair torus");
			return null;
		}
		try {
			Complex[] Z=new Complex[4];
			for (int j=1;j<=4;j++) {
				SideData sd=pdcel.pairLink.get(j);
				Z[j-1]=sd.startEdge.getCenter();
			}
			
			mob=Mobius.mob_NormQuad(Z);
			Mobius.mobiusDirect(p,mob);
			pdcel.updatePairMob();
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("Error normalizing torus: msg "+ex.getMessage());
			return null;
		}
		return mob;
	}
	
	/**
	 * Put a torus in normalized position and fill 'TorusData'
	 * object. Calling routine must arranged 2 side-pairings 
	 * and should have repacked and laid out the affine packing. 
	 * @return TorusData object or null on error
	 */
	public TorusData getTorusData() {
		TorusData torusData=new TorusData();
		
		// normalize and store new rad/cent, update mobius
		Mobius mob=normalizeTorus(packData);
		if (mob==null)
			throw new ParserException("error processing this torus");

		torusData.cornerPts=new Complex[4];
		for (int j=1;j<=4;j++) {
			SideData sdata=pdc.pairLink.get(j);
			torusData.cornerPts[j-1]=sdata.startEdge.getCenter();
		}
		torusData.cornerVert=pdc.pairLink.get(1).startEdge.
				myEdge.origin.vertIndx;
		
		// cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
		torusData.x_ratio=torusData.cornerPts[0].minus(torusData.cornerPts[1]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[3])).
				divide(torusData.cornerPts[0].minus(torusData.cornerPts[3]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[1])));
		
		// for viewing purposes
		torusData.mean=new Complex(0.0);
		for (int i=0;i<4;i++) {
			torusData.mean.add(torusData.cornerPts[i].times(.25));
		}
		
		// is this a flat torus? (vs. affine)
		if (torusData.cornerPts[0].abs()<.5) {
			torusData.flat=true;
			torusData.affCoeff=new Complex(0.0);
			torusData.teich=torusData.cornerPts[3];
			torusData.tau=TorusModulus.Teich2Tau(torusData.teich);
			torusData.a=torusData.b=torusData.M=torusData.N=0.0;
			return torusData;
		}
		
		// else, affine: compute Teichmuller parameter 'T'
		//   Sass affCoeff parameter c. Corners of affine 
		//   fundamental domain are <1, exp(c), exp(cT+c), exp(T)>, 
		//   which is image of parallelogram <0,1,T+1,T> under 
		//   z -> exp(cz). Multi-valued nature of logs requires
		//   finding change in argument along edges.
		torusData.flat=false;
		torusData.a=Math.log(torusData.cornerPts[1].abs());
		torusData.b=0.0; // arg change along [z1,z2] edge
		SideData sd=pdc.pairLink.get(1);
		RedEdge rtrace=sd.startEdge;
		do {
			torusData.b += rtrace.nextRed.getCenter().
					divide(rtrace.getCenter()).arg();
			rtrace=rtrace.nextRed;
		} while (rtrace!=sd.endEdge.nextRed);
		
		torusData.M=Math.log(torusData.cornerPts[3].abs());
		torusData.N=0.0;  // arg change along [z1,z4] edge 
		sd=pdc.pairLink.get(4);
		rtrace=sd.startEdge;
		do {
			torusData.N -= rtrace.nextRed.getCenter().
					divide(rtrace.getCenter()).arg();
			rtrace=rtrace.nextRed;
		} while (rtrace!=sd.endEdge.nextRed);

		torusData.affCoeff=new Complex(torusData.a,torusData.b);
		torusData.teich=new Complex(torusData.M,torusData.N).
				divide(torusData.affCoeff);
		torusData.tau=TorusModulus.Teich2Tau(torusData.teich);

		return torusData;
	}
	
	/**
	 * Given face 'f', "propogate" to face g across 'edge'.
	 * Assume the data for f is in place. Reset the data in g
	 * for the shared edge (centers, radii) and then recompute 
	 * the rest of the data for g. Computation are based on 
	 * one of: radii, labels, schwarzian, or sides of g. 
	 * 'mode' is:
	 *   1=radii, 
	 *   2=labels (assume eucl), 
	 * 	 3=schwarzian (with radii),
	 *   4=schwarzian (with labels, assume eucl)
	 *   5=side lengths (assume eucl)
	 * For schwarzian cases, assume tanPts for f are in place
	 * and set them in g. Also, update other info in g.
	 * 
	 * Note: that we always copy centers of 'edge' to g,
	 *  
	 * @param asps TriAspect[]
	 * @param f int
	 * @param edge HalfEdge
	 * @param mode int
	 * @return -1 on error
	 */
	public static int propogate(TriAspect[] asps,int f,
			HalfEdge edge,int mode) {
		int j=asps[f].edgeIndex(edge);
		int g=edge.twin.face.faceIndx;
		if (j<0 || g<0)
			throw new CombException("can't propagate, data problem");
		int J=asps[g].edgeIndex(edge.twin.next);
		// index j, j+1 in f correspond to J and (J+2)%3 in g
		int hes=asps[f].hes;
		if (hes!=0 && (mode==2 || mode==4 || mode==5))
			throw new ParserException("propogation modes 2, 4, 5 must be eucl");
		
		// always update rad/cents for 'edge' in g.
		// Note: typically, radii and labels will be in sync as we
		//       propogate.
		CircleSimple cs_v=asps[f].getCircleData(j);
		CircleSimple cs_w=asps[f].getCircleData((j+1)%3);
		asps[g].setCenter(cs_v.center,J);
		asps[g].setCenter(cs_w.center,(J+2)%3);
		
		if (mode==2) { // using labals as the eucl radii
			// scale all g's labels so that g's at J is 
			//   f's at j (we assume other end label will also agree)
			double scale=asps[f].labels[j]/asps[g].labels[J];
			for (int k=0;k<3;k++)
				asps[g].labels[k] *=scale;
			asps[g].labels2Sides();
			
			// now compute remaining center and label.
			CircleSimple cs=CommonMath.comp_any_center(cs_w.center,cs_v.center,
				asps[g].getLabel((J+2)%3),asps[g].getLabel(J),
				asps[g].getLabel((J+1)%3),asps[g].getInvDist((J+2)%3),
				asps[g].getInvDist(J),asps[g].getInvDist((J+1)%3),hes);
			asps[g].setCenter(cs.center,(J+1)%3);
			asps[g].setLabel(cs.rad,(J+1)%3);
			return 1;
		}
		if (mode==3) { // schwarzian with radii
			Mobius bm_f=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					asps[f].tanPts[0],asps[f].tanPts[1],asps[f].tanPts[2],
					0,asps[f].hes);
				
			// compute the target circle
			double s=asps[g].schwarzian[(J+2)%3];
			CircleSimple sC = 
				Schwarzian.getThirdCircle(s,(J+2)%3, bm_f,hes);

			asps[g].setCircleData((J+1)%3,sC);
				
			// reset the tangency points
			asps[g].setTanPts();
			return 1;
			
		}
		if (mode==4) { // schwarzian with labels
			// scale all g's labels so that at J is f's at j
			//  (we assume other end label will also agree)
			double scale=asps[f].labels[j]/asps[g].labels[J];
			for (int k=0;k<3;k++)
				asps[g].labels[k] *=scale;
			asps[g].labels2Sides();

			// compute map from base equilateral
			Mobius bm_f=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					asps[f].tanPts[0],asps[f].tanPts[1],asps[f].tanPts[2],
					0,asps[f].hes);
				
			// compute the target circle
			double s=asps[g].schwarzian[(J+2)%3];
			CircleSimple sC = Schwarzian.getThirdCircle(s,(J+2)%3, bm_f,hes);

			asps[g].setLabel(sC.rad,(J+1)%3);
			asps[g].setCenter(sC.center,(J+1)%3);
				
			// reset the tangency points
			asps[g].setTanPts();
			return 1;
		}
		if (mode==5) { // side lengths
			// scale all g's side lengths so that at J is f's at j
			//  (we assume other end side length will also agree)
			double scale=asps[f].sides[j]/asps[g].sides[J];
			for (int k=0;k<3;k++)
				asps[g].sides[k] *=scale;
			asps[g].sides2Labels();
			
			// now compute remaining center 
			CircleSimple cs=CommonMath.comp_any_center(cs_w.center,cs_v.center,
					asps[g].getLabel((J+2)%3),asps[g].getLabel(J),
					asps[g].getLabel((J+1)%3),asps[g].getInvDist((J+2)%3),
					asps[g].getInvDist(J),asps[g].getInvDist((J+1)%3),hes);
			asps[g].setCenter(cs.center,(J+1)%3);
			asps[g].setLabel(cs.rad,(J+1)%3);
			asps[g].labels2Sides();
			return 1;
		}
		
		// else, asssume mode==1
		CircleSimple cs=CommonMath.comp_any_center(cs_w,cs_v,
				asps[g].getRadius((J+1)%3),asps[g].getInvDist((J+2)%3),
				asps[g].getInvDist(J),asps[g].getInvDist((J+1)%3),hes);
		asps[g].setCircleData((J+1)%3,cs);
		return 1;
	}
	
	
	/**
	 * Given 'edge' of face f, compute a new 'TriAspect' 
	 * for face g of 'edge.twin' which aligns the centers
	 * in g for the ends of 'edge' with those in face f 
	 * and then computes the resulting position for the 
	 * vertex opposite edge in g. We use stored 'labels' 
	 * as homogeneous eucl radii.
	 * @param p PackData
	 * @param asps []TriAspect
	 * @param edge (faces f=edge.v, g=edge.w)
	 * @return new TriAspect to replace asps[g], null on error
	 */
	public static TriAspect plopAcrossEdge(TriAspect[] asps,HalfEdge edge) {
		int f=edge.face.faceIndx;
		int g=edge.twin.face.faceIndx;
		TriAspect newasp=new TriAspect(asps[g]); // clone
		// compute normalized eudl centers for g
		newasp.setCents_by_label();
		int mode=2; // use 'labels'
		Mobius mob=newasp.alignMe(asps[f],edge.twin,2);
		newasp.mobiusMe(mob); // this adjusts rad/cents
		
		// assume 'mob' is complex linear
		double factor=mob.a.divide(mob.d).abs();
		for (int j=0;j<3;j++)
			newasp.labels[j] *=factor;
		newasp.labels2Sides();
		return newasp;
	}
	
	/**
	 * Given a linked list of faces, find successive locations
	 * and draw the faces, and/or circles. Idea is to have
	 * 'last_face' already in place at front of list, then layout
	 * a closed face chain ending with 'last_face'; this can
	 * then be repeated for 'analytic' continuation.
	 * @param facelist
	 * @return LinkedList<TriAspect>, new TriAspect objects
	 */
	public static LinkedList<TriAspect> layout_facelist(PackData p,
			TriAspect []asp, FaceLink facelist) {
		if (facelist == null || facelist.size() == 0)
			return null;

		// iterate through the given face list
		Iterator<Integer> flist = facelist.iterator();
		
		// start linked list; clone the first, assume its centers
		//   are set and radii are scaled as desired.
		LinkedList<TriAspect> aspList=new LinkedList<TriAspect>();
		int first_face=flist.next();
		int last_face=first_face;
		TriAspect last_asp=new TriAspect(asp[first_face]);
		aspList.add(last_asp);
		while (flist.hasNext()) {
			int next_face=flist.next();
			// skip repeated and illegal indices
			if (next_face != last_face && next_face > 0
				&& next_face <= p.faceCount) {

				TriAspect next_asp=new TriAspect(asp[next_face]);
				next_asp.setCents_by_label(); // put centers in normalized position
				int jj = p.face_nghb(last_face,next_face);
				if (jj<0) { // error, stop adding to the list
					throw new ParserException(
							"disconnect in chain of faces.");
				}
				int v2=next_asp.vert[(jj+2)%3];
				next_asp.adjustData(v2,last_asp);
				aspList.add(next_asp);
				last_face=next_face;
				last_asp=next_asp;
			}
		} // end of while
		return aspList;
	}
	
	/**
	 * Draw a linked list faces
	 * @param p PackData
	 * @param aspList LinkedList<TriAspect>, precomputed list
	 * @param drawFirst; if true, draw the first face
	 * @param faceFlags DispFlags
	 * @param circFlags DispFlags
	 * @return int count
	 */
	public static int dispFaceChain(PackData p,LinkedList<TriAspect> aspList,
			boolean drawfirst, DispFlags faceFlags,DispFlags circFlags) {
		int count=0;
		if (aspList==null || aspList.size()==0)
			return count;
		// faces and/or circles ?
		boolean faceDo=false;
		if (faceFlags.draw)
			faceDo=true;
		boolean circDo=false;
		if (circFlags.draw)
			circDo=true;
		if (!faceDo && !circDo) 
			return 0;

		TriAspect first_asp=aspList.get(0);
		Iterator<TriAspect> aspit=aspList.iterator();
		TriAspect asp=first_asp;
		int past_face=asp.face;
		int next_face=asp.face;
		boolean firstasp=true; // for first face, may draw all circles
		if (!drawfirst) { // skip the first one
			asp=aspit.next();
			next_face=asp.face;
			firstasp=false;
		}
		while (aspit.hasNext()) {
			asp=aspit.next();
			past_face=next_face;
			next_face=asp.face;
			int j=p.face_nghb(past_face,next_face);
			if (j<0) 
				j=0;
			int v0=asp.vert[j];
			int v1=asp.vert[(j+1)%3];
			int v2=asp.vert[(j+2)%3];
			Complex c0=asp.getCenter(asp.vertIndex(v0));
			Complex c1=asp.getCenter(asp.vertIndex(v1));
			Complex c2=asp.getCenter(asp.vertIndex(v2));
			if (faceDo) { // draw the faces
				if (!faceFlags.colorIsSet && 
						(faceFlags.fill || faceFlags.colBorder))
					faceFlags.setColor(p.getFaceColor(asp.face));
				if (faceFlags.label)
					faceFlags.setLabel(Integer.toString(asp.face));
				p.cpScreen.drawFace(c0, c1, c2,null,null,null,faceFlags);
				count++;
			}
			if (circDo) { // also draw the circles
				if (!circFlags.colorIsSet && 
						(circFlags.fill || circFlags.colBorder))
					circFlags.setColor(p.getCircleColor(v2));
				if (circFlags.label)
					circFlags.setLabel(Integer.toString(v2));
				p.cpScreen.drawCircle(c2,
						asp.labels[asp.vertIndex(v2)],circFlags);
				count++;
				if (drawfirst && firstasp) { // draw all circles of first face
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder))
						circFlags.setColor(p.getCircleColor(v0));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(v0));
					p.cpScreen.drawCircle(c0,asp.labels[asp.vertIndex(v0)],
							circFlags);
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder))
						circFlags.setColor(p.getCircleColor(v1));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(v1));
					p.cpScreen.drawCircle(c1,
							asp.labels[asp.vertIndex(v1)],circFlags);
					count++;
				}
			}
			firstasp=false;
		}
		return count;
	}
 
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("torAB","{A B}",null,
				"initialize data for affine torus, side scaling factors A, B"));
		cmdStruct.add(new CmdStruct("corners","v1 v2 v3 v4",null,
				"vertices at corners, v1,v2=bottom, v2,v3=right"));
		cmdStruct.add(new CmdStruct("affpack","{v..}",null,
				"run iterative affine packing method"));
		cmdStruct.add(new CmdStruct("afflayout",null,null,
				"layout a fundamental domain using computed ratios"));
		cmdStruct.add(new CmdStruct("set_labels","-[rzst] f..",null,
				"face label data using: -r = radii, -z = centers, -s= random"));
		cmdStruct.add(new CmdStruct("draw","-[cfB]flags",null,
				"faces, f, circles, c, both B, plus normal flags"));
		cmdStruct.add(new CmdStruct("set_screen",null,null,
				"set screen to get the full fundamental domain"));
		cmdStruct.add(new CmdStruct("log_radii",null,null,
				"write /tmp file with labels"));
		cmdStruct.add(new CmdStruct("status",null,null,
				"No flags? error norms: curvatures, strong consistency\n"+
				"With flags: return single vert info"));
		cmdStruct.add(new CmdStruct("set_eff",null,null,
				"Using centers, set packing rad to the 'effective' radii"));
		cmdStruct.add(new CmdStruct("ccode","-[cfe] -m m j..",null,
				"Color code faces, vertices, or edges, mode m"));
		cmdStruct.add(new CmdStruct("Lface",null,null,
				"draw faces using TriAspect centers, spanning tree"));
		cmdStruct.add(new CmdStruct("Ltree",null,null,
				"draw dual spanning tree using TriAspect centers"));
		cmdStruct.add(new CmdStruct("LinCircs",null,null,
				"Draw the incircles of the faces, using aspects 'center's"));
		cmdStruct.add(new CmdStruct("equiSides",null,null,
				"set 'sides' to 1; faces are equilateral"));
		cmdStruct.add(new CmdStruct("sideRif","v..",null,
				"Riffle by adjusting 'sides'"));
		cmdStruct.add(new CmdStruct("update","-[sl] f..",null,
				"Update: -s centers to sides; -l sides to labels"));
		cmdStruct.add(new CmdStruct("sI",null,null,
				"Side information: corners, angles, etc."));

	}
	
}

/** 
 * Specialized class for accumulating torus data. Torus
 * may be flat or affine; these use different normalizations.
 * Torus must have been given layout in 2-sidepair form; 
 * side-pair Mobius maps are stored as usual.
 */
class TorusData {
	boolean flat;   // yes for regular torus, no for affine 
	int cornerVert; // corner vertex
	Complex []cornerPts; // four locations for single corner vertex
	Complex x_ratio; // cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
	Complex mean;  // average of four corners --- for display use
	Complex teich;  // Teichmuller parameter
	Complex tau;    // conformal modulus
	Complex affCoeff; // affine coefficient, 0.0 for flat
	
	// for affine case only
	double a;   // log(|z2|)
	double b;   // argument change on [z1 z2]
	double M;   // log(|z4|)
	double N;   // argument change on [z1,z4]
}