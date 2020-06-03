package ftnTheory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.CPFileManager;
import komplex.DualGraph;
import komplex.DualTri;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.GraphLink;
import listManip.NodeLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;
import widgets.RadiiSliders;

/** 
 * This is code for exploring discrete Schwarzian derivatives, 
 * following up on ideas in Gerald Orick's thesis. Note, however, that
 * I have started mainlining a slightly different notion, one identifying
 * faces with the "base equilateral". I expect to use this to replace
 * Orick's original approach, at which time I will revamp this 
 * PackExtender. In the meantime, I'm moving many methods to 
 * "packing.schwarzian". (This extender was originally called
 * "Schwarzian.java", "SZ".)
 * 
 * The original idea concerned maps between tangency circle packings 
 * sharing the same combinatorics. Mechanics involve the "dual" 
 * triangles, one for each face, formed by its 3 tangency points, 
 * enclosing the interstice and inscribed in the dual circle for the face. 
 * 
 * A "circle packing quadrangle" refers to two faces sharing edge e. 
 * Given map F:P --> P', for each face f and f'=F(f), M(f) is the
 * "face Mobius map" of f onto f' identifying corresponding tangency 
 * points. These maps were used by He/Schramm to define maps between 
 * circle packings and used to prove convergence. 
 * 
 * **** CONVENTION: Given directed edge <v,w> shared by faces f and g, we
 * reverse Orick's convention and let f be the face on the LEFT of <v,w>,
 * g the face on the RIGHT. Note that the ordered pair <f,g> is the "dual"
 * edge to <v,w>. 
 * 
 * For each interior edge e sharing faces f and g, define the "directed 
 * Mobius edge derivative" by dM(e)=M(g)^{-1}.M(f). This is invariant: 
 * dM(e) is unchanged if we replace P' by P" = phi(P'), phi a Mobius. 
 * 
 * The Mobius maps dM(e) fix the tangency point within e and are always 
 * parabolic, and we normalize so trace(dM(e))=2 and det(dM(e))=1. If
 * dM(e) ~ [a b | c d], then the complex entry c is the "discrete
 * Schwarzian" as defined by Orick. More precisely, if z is the tangency
 * point in the edge e then dM(e) has the form
 *      dM(e)= [1 + c*z,-c*z^2; c, 1-c*z]
 * Moreover, c = s*i*conj(delta), where delta = exp{i theta} gives the
 * direction of the directed edge e and s is some real scalar. 
 * 
 * Note that the sign of 's' is a matter of convention. With
 * our notation, let eta = i*delta, so 'eta' is the unit normal 
 * to edge <v,w> that points INTO f and c = -s*conj(eta). We generally
 * find s via s=-c/conj(eta). We will store s, the "real" schwarzian. 
 * Note that -s is the 'sch_coeff' for the reverse edge -e. 
 * 
 * We have two types of schwarzians. This PackExtender stores data 
 * for both domain and range packing, with a 'TriAspect'
 * for each face: domain data stored on initiation (or adjusted 
 * later), range data stored on demand. Given <f,g> in domain
 * and <F,G> in range, we have stored the schwarzians for the map
 * in 'TriAspect.sch_coeff'. So the range packing is Mobius image 
 * of domain packing iff all 'sch_coeff' are zero.
 * 
 * However, I am moving to a different model: see 
 * "packing.Schwarzian.java".
 * 
 * @author kens, November 2018
 *
 */
public class SchwarzMap extends PackExtender {
	
	public TriAspect []domainTri; // domain face data; 'MobDeriv's are stored here
	public TriAspect []rangeTri;  // created with 'set_range' or filled by 'go'
	public int rangeHes;          // range geometry
	public int rangePackNum;
	public GraphLink dTree;       // dual spanning tree for layout (root is removed) 
	
	public RadiiSliders radSliders;

	// Constructor
	public SchwarzMap(PackData p) {
		super(p);
		extensionType="SCHWARZIAN";
		extensionAbbrev="SM";
		toolTip="'Schwarz Mapping' is for developing and exploiting a discrete "
				+ "Schwarzian derivative proposed by Gerald Orick";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}
		domainTri=PackData.getTriAspects(packData); // default: look at 'set_domain' call
		rangeTri=null;
		rangeHes=0; // resent when rangeTri is filled.
		dTree=DualGraph.easySpanner(packData,false);
		if (dTree.get(0).v==0) // prune root
			dTree.remove(0);
	}
	
	/**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// ======= open radSlider ============
		if (cmd.startsWith("radS")) {
			NodeLink wlist;
			try {
				items=flagSegs.get(0);
				wlist=new NodeLink(packData,items);
			} catch(Exception ex) {
				wlist=new NodeLink(packData,"a");
			}
			radSliders=new RadiiSliders(packData,wlist);
			if (radSliders==null)
				return 0;
			radSliders.setVisible(true);
		}

		// ======= put ===========
		else if (cmd.startsWith("put")) {
			PackData qData=null;
			if (flagSegs!=null && (items=flagSegs.get(0))!=null) {
				int qnum=rangePackNum;
				if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
					items.remove(0);
					// Want map to develop in specified packing (versus back in packData).
					//   minimal check that packing is same and not hyperbolic; else copy 
					//   packData into qdata.
					if (qnum!=packData.packNum) {
						qData=CPBase.pack[qnum].packData;
						if ((qData.faceCount!=packData.faceCount) || qData.hes<0) {
							CirclePack.cpb.msg("Copy domain packing into target, p"+qData.packNum+", "+
									"because current packing does not match nodeCount. Convert to spherical.");
							cpCommand(packData,"copy "+qnum);
							qData=CPBase.pack[qnum].packData;
							qData.setCombinatorics();
							cpCommand(qData,"geom_to_s"); //  make range spherical
						}
						rangeTri=PackData.getTriAspects(qData);
					}
				}
			}
			
			// get desired dual edges, default to dTree
			GraphLink graph=null;
			if (items.size()>0)
				graph=new GraphLink(packData,items);
			else graph=dTree;
			
			// Do we need to place the first face? Only if
			//   there is a root.
			EdgeSimple edge=graph.get(0);
			int baseface=0;
			if (edge.v==0) { // root? yes, then have to place
				graph.remove(0); 
				baseface=edge.w;
			}

			// keep track of how many times a center is reset, error
			int []verthits=new int[packData.nodeCount+1];
			double maxError=0.0;

			if (baseface>0) {
				TriAspect mytri=rangeTri[baseface];
				for (int j=0;j<3;j++) {
					int v=mytri.vert[j];
					qData.rData[v].center=new Complex(mytri.getCenter(j));
					qData.rData[v].rad=mytri.getRadius(j);
					verthits[v]++;
				}
			}
			
			// Now proceed through 'graph'
			Iterator<EdgeSimple> dit=graph.iterator();
			while (dit.hasNext()) {
				edge=dit.next();
				int g=edge.w;
				TriAspect mytri=rangeTri[g];
				int indx=(packData.face_nghb(edge.v,g)+2)%3;
				int v=mytri.vert[indx]; // opposite edge with f
				Complex z=mytri.getCenter(indx);
				if (verthits[v]>0) {
					double diff=0.0;
					if (qData.hes>0) 
						diff=SphericalMath.s_dist(z,qData.rData[v].center);
					else
						diff=z.minus(qData.rData[v].center).abs();
					maxError=(diff>maxError) ? diff:maxError;
				}
				qData.rData[v].center=new Complex(z);
				qData.rData[v].rad=mytri.getRadius(indx);
				verthits[v]++;
			}
			
			this.msg("Data for p"+qData.packNum+" was set from 'rangeTri' data; "+
					"max swap error in centers was "+String.format("%.6f",maxError));
			return 1;
		}
		
		// ======= s_inc ==========
		else if (cmd.startsWith("s_inc")) {
			items=flagSegs.get(0);
			double factor=1.0;
			try {
				factor=Double.parseDouble(items.remove(0));
			} catch (Exception ex) {
				Oops("usage: s_inc {x} {f g ...}");
			}
			GraphLink glk=new GraphLink(packData,items);
			Iterator<EdgeSimple> gls=glk.iterator();
			while(gls.hasNext()) {
				EdgeSimple dedge=gls.next();
			
				// TODO: finish coding this
			}
		}

		// ======= s_set ============
		else if (cmd.startsWith("s_set")) {
			if (flagSegs==null || flagSegs.size()<1 || 
					(items=flagSegs.get(0)).get(0).length()<1) {
				CirclePack.cpb.errMsg("usage: s_set {v w s ....}");
			}
			int count=0;
			int v;
			int w;
			double s_coeff;
			int tick=0;
			while (items.size()>2) {
				try {
					v=Integer.parseInt(items.remove(0));
					w=Integer.parseInt(items.remove(0));
					s_coeff=Double.parseDouble(items.remove(0));
					if (!set_s_coeff(v,w,s_coeff)) 
						return 0;
					tick++;
				} catch (Exception ex) {
					return 0;
				}
			}
			return tick;
		}
		
		// =========== s_get =============
		else if (cmd.startsWith("s_get")) {
			if (domainTri[1].sch_coeffs==null) {
				CirclePack.cpb.errMsg("'seems there are no schwarz coefficients");
				return 0;
			}
			if (flagSegs==null || flagSegs.size()<1) {
				CirclePack.cpb.errMsg("'seems there are no schwarz coefficients");
				return 0;
			}
			items=flagSegs.get(0);
			int v;
			int w;
			try {
				v=Integer.parseInt(items.get(0));
				w=Integer.parseInt(items.get(1));
			} catch(Exception ex) {
				CirclePack.cpb.errMsg("usage: s_get v w");
				return 0;
			}
			double []rslts=get_s_coeff(v,w);
			Complex z=new Complex(rslts[1],rslts[2]);
			CirclePack.cpb.msg("Schwarzian data for edge ("+v+","+w+"): s_coeff = "+
					String.format("%.8e",rslts[0])+"; tang Pt = "+z.toString());
			return 1;
		}

		// ======= s_load ============
		else if (cmd.startsWith("s_lo")) {
			if (domainTri==null) {
				throw new DataException("packing have no 'domainTri' for the Schwarzian");
			}
			StringBuilder strbld =new StringBuilder("");
			int ans=CPFileManager.trailingFile(flagSegs,strbld);
			String filename=strbld.toString();
			if (ans==0 || filename.length()==0) {
				CirclePack.cpb.errMsg("'s_load' requires a file name");
				return 0;
			}

			BufferedReader 	fp=CPFileManager.openReadFP(CPFileManager.PackingDirectory,filename,false);
			if (fp==null) { 
				CirclePack.cpb.errMsg("'s_load' did not find the Schwarzian file '"+filename+
					"' in directory '"+CPFileManager.PackingDirectory.toString());
				return 0;
			}
			
			int count=0;
			String line;
            int v;
            int w;
            double s_coeff;
            while((line=StringUtil.ourNextLine(fp))!=null) {
                StringTokenizer tok = new StringTokenizer(line);
                while(tok.hasMoreTokens()) {
                    try {
                    	v=Integer.parseInt(tok.nextToken());
                    	w=Integer.parseInt(tok.nextToken());
                    	s_coeff=Double.parseDouble(tok.nextToken());
                    } catch (Exception ex) {
                    	break;
                    }
                    if (!set_s_coeff(v,w,s_coeff)) 
						break;
                    count++;
                } // done with this line
            } // end of reading lines

            try {
            	fp.close();
            } catch(Exception ex) {
            	CirclePack.cpb.errMsg("problem closing file");
            }
            
            CirclePack.cpb.msg(count+" Schwarzians from "+filename+
            		" are stored in 'domainTri' of p"+packData.packNum);
            return count;
		}
		
		// ======= s_map =============
		else if (cmd.startsWith("s_map")) {
			
			// debugging the layouts
			boolean debug=false;
			
			if (domainTri[1].sch_coeffs==null) {
				CirclePack.cpb.errMsg("seems 'domainTri' doesn't have 'sch_coeffs' data");
				return 0;
			}
			PackData qData=packData;
			DispFlags dispFlags=new DispFlags("");
			DispFlags oldFlags=new DispFlags("cc241t6"); // pink range circles for face f
			DispFlags newFlags=new DispFlags("cc218t2"); // green range circles for new face g
			GraphLink graph=null;
			
			// should be at most one item: look for -q{} flag to designate 
			// image packing first, then display flags for color, thickness, fill, etc
			if (flagSegs!=null && (items=flagSegs.get(0))!=null) {
				flagSegs.remove(0); // toss the -s flag
				int qnum=packData.packNum;
				if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
					items.remove(0);
					// Want map to develop in specified packing (versus back in packData).
					//   minimal check that packing is same and not hyperbolic; else copy 
					//   packData into qdata.
					if (qnum!=packData.packNum) {
						qData=CPBase.pack[qnum].packData;
						if ((qData.faceCount!=packData.faceCount) || qData.hes<0) {
							CirclePack.cpb.msg("Copy domain packing into target, p"+qData.packNum+", "+
									" with spherical geometry because original target does "+
									"not match nodeCount or is hyperbolic.");
							cpCommand(packData,"copy "+qnum);
							cpCommand(qData,"geom_to_s"); //  make range spherical
						}
					}
				}
				
				// display flag info; may be separate item
				if (items.size()>0 || flagSegs.size()>0) {
					if (items.size()>0)
						dispFlags=new DispFlags(items.remove(0),qData.cpScreen.fillOpacity);
					else { // only other flag should be display (can be done without flag)
						items=flagSegs.get(0);
						if (StringUtil.isFlag(items.get(0))) {
							items.remove(0);
							dispFlags=new DispFlags(items.get(0),qData.cpScreen.fillOpacity);
						}
					}
				}
				
				// Now look for list of face pairs; default is spanning tree
				if (items.size()>0) {
					graph=new GraphLink(packData,items);
				}
			}
			
			if (graph==null || graph.size()==0)
				graph=dTree;
			
			// keeping track of processed faces
			int []hitfaces=new int[packData.faceCount+1];
			
			// ensure radii/centers are in 'rangeTri' (in geometry of 'qData')
			if (rangeTri==null || rangeTri.length!=packData.faceCount+1 || 
					rangeTri[1].hes!=qData.hes)
				rangeTri=PackData.getTriAspects(qData);
			
			// Do we need to place the first face?
			EdgeSimple edge=graph.get(0);
			int baseface=0;
			if (edge.v==0) { // root? yes, then have to place
				graph.remove(0); 
				baseface=edge.w;
			}
			else if (graph==dTree) { // yes, place the first face
				baseface=edge.v;
			}
			
			int dom_hes=packData.hes;
			int rangeHes=qData.hes;
			boolean geo_switch=(dom_hes!=rangeHes);
			int count=0;
			CircleSimple sC=new CircleSimple();
			
			// if we need to place the base face, put in same spot as domain
			if (baseface>0) {
				TriAspect mytri=rangeTri[baseface];
				if (geo_switch) {
				if (dom_hes<0 && rangeHes==0) { // domain is hyp, range eucl
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC=HyperbolicMath.h_to_e_data(packData.rData[v].center,packData.rData[v].rad);
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				else if (dom_hes<0 && rangeHes>0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC = HyperbolicMath.h_to_e_data(packData.rData[v].center, packData.rData[v].rad);
						sC = SphericalMath.e_to_s_data(sC.center, sC.rad);
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				else if (dom_hes==0 && rangeHes>0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC=SphericalMath.e_to_s_data(packData.rData[v].center,packData.rData[v].rad);
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
					if (debug) {
						qData.cpScreen.drawFace(mytri.getCenter(0),
								mytri.getCenter(1),mytri.getCenter(2),
								mytri.getRadius(0),
								mytri.getRadius(1),mytri.getRadius(2),dispFlags);
						qData.cpScreen.repaint();
					}
				}
				
				// range should not be hyperbolic 
//				else if (dom_hes==0 && rangeHes<0) {
//					for (int j=0;j<3;j++) {
//						int v=mytri.vert[j];
//						sC=HyperbolicMath.e_to_h_data(packData.rData[v].center,packData.rData[v].rad);
//						mytri.setRadius(sC.rad,j);
//						mytri.setCenter(sC.center,j);
//					}
//				}
//				else if (dom_hes>0 && rangeHes<0) {
//					for (int j=0;j<3;j++) {
//						int v=mytri.vert[j];
//						sC = SphericalMath.s_to_e_data(packData.rData[v].center, packData.rData[v].rad);
//						sC=HyperbolicMath.e_to_h_data(sC.center,sC.rad);
//						mytri.setCenter(sC.center,j);
//						mytri.setRadius(sC.rad,j);
//					}
//				}
				
				else if (dom_hes>0 && rangeHes==0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC = SphericalMath.s_to_e_data(packData.rData[v].center, packData.rData[v].rad);
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				hitfaces[baseface]=1;
				} // done if converting
				else {
					for (int j=0;j<3;j++) {
						mytri.setCenter(domainTri[baseface].getCenter(j),j);
						mytri.setRadius(domainTri[baseface].getRadius(j),j);
					}
					hitfaces[baseface]=1;
				}
			
				// Draw
				if (dispFlags.label)
					dispFlags.setLabel(Integer.toString(baseface));
				qData.cpScreen.drawFace(mytri.getCenter(0),mytri.getCenter(1),mytri.getCenter(2),
						mytri.getRadius(0),mytri.getRadius(1),mytri.getRadius(2),dispFlags);
				qData.cpScreen.repaint();
				count=1;
				
				// debug?
				if (debug) {
					packData.cpScreen.drawFace(domainTri[baseface].getCenter(0),
							domainTri[baseface].getCenter(1),domainTri[baseface].getCenter(2),
							domainTri[baseface].getRadius(0),domainTri[baseface].getRadius(1),
							domainTri[baseface].getRadius(2),dispFlags);
					packData.cpScreen.repaint();
				}
				
			}
			
			// Now proceed through 'graph'
			Iterator<EdgeSimple> dit=graph.iterator();
			while (dit.hasNext()) {
				edge=dit.next();
				int f=edge.v; // should already have its data
				int g=edge.w;
				
				// --------- get face f ready --------------------
				// We assume domainTri[f] and rangeTri[f] have radii/centers.
				// Update both 'tanPts', since these faces might have been moved
				// (even in domain, if it's not simply connected)
				rangeTri[f].setTanPts();

				// ------- get face g ready in domain - align with f -------
				int gfindx=packData.face_nghb(f,g);
				Mobius aligng=domainTri[g].alignMe(gfindx,domainTri[f]);
				if (Mobius.frobeniusNorm(aligng)>.00001) {
					sC=new CircleSimple();
					for (int j=0;j<3;j++) {
						Mobius.mobius_of_circle(aligng, packData.hes,
							domainTri[g].getCenter(j),domainTri[g].getRadius(j), 
								sC,true);
						domainTri[g].setCenter(sC.center, j);
						domainTri[g].setRadius(sC.rad, j);
					}
				}
				domainTri[g].setTanPts();					

				// find rangeTri[g]
				TriAspect ftri=rangeTri[f];
				int fgindx=packData.face_nghb(g, f);
				TriAspect gtri=rangeTri[g]; 
				gtri.setCenter(new Complex(ftri.getCenter(fgindx)),(gfindx+1)%3);
				gtri.setRadius(ftri.getRadius(fgindx),(gfindx+1)%3);
				gtri.setCenter(new Complex(ftri.getCenter((fgindx+1)%3)),gfindx);
				gtri.setRadius(ftri.getRadius((fgindx+1)%3),gfindx);
				
				// draw
				if (debug) { // draw shared circles in range faces
					qData.cpScreen.drawCircle(rangeTri[f].getCenter(fgindx),
							rangeTri[f].getRadius(fgindx),oldFlags);
					qData.cpScreen.drawCircle(rangeTri[f].getCenter((fgindx+1)%3),
							rangeTri[f].getRadius((fgindx+1)%3),oldFlags);
					qData.cpScreen.repaint();
					qData.cpScreen.drawCircle(gtri.getCenter(gfindx),gtri.getRadius(gfindx),newFlags);
					qData.cpScreen.drawCircle(gtri.getCenter((gfindx+1)%3),gtri.getRadius((gfindx+1)%3),newFlags);
					qData.cpScreen.repaint();
				}

				// ----------- set to compute final circle for rangeTri[g] -----
				// Final circle is image of circle from g under 'gmob'. Two ways:
				
				// Get invfmob = (mu_f)^{-1}, rangeTri[f] to domainTri[f]. Then 
				//    gmob=inverse((mobderiv f -> g) * (invfmob)).
				// Instead, compute fmob from domainTri[f] to rangeTri[f]. Then
				//    gmob=fmob*(mobderiv f->g)^{-1}
				Mobius fmob=Mobius.mob_xyzXYZ(domainTri[f].tanPts[0],
						domainTri[f].tanPts[1],domainTri[f].tanPts[2],
						ftri.tanPts[0],ftri.tanPts[1],ftri.tanPts[2],
						packData.hes,rangeHes);
				Mobius mobg=(Mobius)fmob.rmult(domainTri[f].MobDeriv[fgindx].inverse());
				
				// Either way, apply to domainTri[g] to get last circle for rangeTri[g]
				Mobius.mobius_of_circle(mobg, packData.hes,
						domainTri[g].getCenter((gfindx+2)%3),
						domainTri[g].getRadius((gfindx+2)%3), sC,true);
				Complex z=sC.center;
				double r=sC.rad;
				if (geo_switch) {
					if (dom_hes<0) { // domain hyperbolic, range not
						sC=HyperbolicMath.h_to_e_data(z, r);
						if (rangeHes>0)
							sC=SphericalMath.e_to_s_data(sC.center, sC.rad);
					}
					else if (dom_hes==0) {  // range must be spherical
						sC=SphericalMath.e_to_s_data(z,r);
					}
					// range should not be hyperbolic 
//					else if (dom_hes==0 && rangeHes<0) {
//						sC=HyperbolicMath.e_to_h_data(z, r);
//					}
					else if (dom_hes>0) { // range must be euclidean
						sC=SphericalMath.s_to_e_data(z, r);
//						if (rangeHes<0)
//							sC=HyperbolicMath.e_to_h_data(sC.center,sC.rad);
					}
					z=sC.center;
					r=sC.rad;
				}
				gtri.setCenter(z,(gfindx+2)%3);
				gtri.setRadius(r,(gfindx+2)%3);
				
				if (debug) { // draw the new circle
					qData.cpScreen.drawCircle(gtri.getCenter((gfindx+2)%3),gtri.getRadius((gfindx+2)%3),newFlags);
					qData.cpScreen.repaint();
				}
				
				// Now, draw this face
				if (dispFlags.label)
					dispFlags.setLabel(Integer.toString(g));
				qData.cpScreen.drawFace(gtri.getCenter(0),gtri.getCenter(1),gtri.getCenter(2),
						gtri.getRadius(0),gtri.getRadius(1),gtri.getRadius(2),dispFlags);
				qData.cpScreen.repaint();
				count++;
			} // end of while through dTree
			return count;
		}
		
		// ======= get_tree (copy it into 'glist')
		else if (cmd.startsWith("get_tre")) {
			if (dTree==null || dTree.size()==0)
				return 0;
			packData.glist=new GraphLink();
			return packData.glist.abutMore(dTree);
		}

		// ======= get ===============
		else if (cmd.startsWith("get")) {
			Mobius []faceMobs=setFaceMobs();
			if (faceMobs==null || flagSegs==null || flagSegs.size()==0) {
				Oops("problem with 'faceMobs' or missing specified edges");
			}
			EdgeLink elink=new EdgeLink(packData,flagSegs.get(0));
			if (elink==null || elink.size()==0)
				Oops("usage: get {v w ..}");
			int count=0;
			Iterator<EdgeSimple> elst=elink.iterator();
			while (elst.hasNext()) {
				EdgeSimple edge=elst.next();
				int v=edge.v;
				int w=edge.w;
				try {
					int f=packData.face_right_of_edge(w,v);
					int g=-1;
					if ((g=packData.face_right_of_edge(v,w))>=0) {
								
						// Mobius of g to align it with f along <v,w> in the domain
						Mobius domainalign=new Mobius();
						Complex fvz=domainTri[f].getCenter(domainTri[f].vertIndex(v));
						Complex fwz=domainTri[f].getCenter(domainTri[f].vertIndex(w));
						Complex gvz=domainTri[g].getCenter(domainTri[g].vertIndex(v));
						Complex gwz=domainTri[g].getCenter(domainTri[g].vertIndex(w));
						double eror=fvz.minus(gvz).abs()+fwz.minus(gwz).abs();
						if (eror>0.001*domainTri[f].getRadius(domainTri[f].vertIndex(v))) {
							Complex ftanz=domainTri[f].tanPts[domainTri[f].vertIndex(v)];
							Complex gtanz=domainTri[g].tanPts[domainTri[g].vertIndex(w)];
							domainalign=Mobius.mob_xyzXYZ(gvz, gtanz, gwz,
									fvz, ftanz,fwz,packData.hes,packData.hes);
						}
						
						// Mobius of image of g to align with image of f along <v,w> (in range)
						fvz=rangeTri[f].getCenter(rangeTri[f].vertIndex(v));
						fwz=rangeTri[f].getCenter(rangeTri[f].vertIndex(w));
						gvz=rangeTri[g].getCenter(rangeTri[g].vertIndex(v));
						gwz=rangeTri[g].getCenter(rangeTri[g].vertIndex(w));
						Complex ftanz=rangeTri[f].tanPts[rangeTri[f].vertIndex(v)];
						Complex gtanz=rangeTri[g].tanPts[rangeTri[g].vertIndex(w)];
								
						Mobius galign=Mobius.mob_xyzXYZ(gvz, gtanz, gwz,
								fvz, ftanz,fwz,rangeHes,rangeHes);
						Mobius newg=(Mobius)(faceMobs[g]).
								lmult(galign).rmult(domainalign.inverse());
						
						Mobius edgeMob=(Mobius)newg.lmult(faceMobs[f].inverse());
								
						// seem to need this normalization to get trace=+2.0;
						Complex tc=edgeMob.a.plus(edgeMob.d);
						if (tc.x<0.0) {
							edgeMob.a=edgeMob.a.times(-1.0);
							edgeMob.b=edgeMob.b.times(-1.0);
							edgeMob.c=edgeMob.c.times(-1.0);
							edgeMob.d=edgeMob.d.times(-1.0);
							tc=tc.times(-1.0);
						}
								
						// show results
						Complex detdM=edgeMob.det();
						System.out.println("Edge <"+v+","+w+"> ; Schwarzian trace is "+
							String.format("%.6f",edgeMob.c.x)+" "+
							String.format("%.6f", edgeMob.c.y)+"; det(dM) = "+
							String.format("%.6f",detdM.x)+" "+
							String.format("%.6f", detdM.y));
					}
					
					count=count+1;
				} catch(Exception ex) {
					Oops("failed to compute Schwarzian for edge <"+v+","+w+">");
				}
			}
			
			return count;
		}
		
		// ======= field ===============
		else if (cmd.startsWith("field")) {
			if (domainTri==null || rangeTri==null) {
				Oops("'domainTri', 'rangeTri' must have data to construct face Mobius's");
			}
			
			// TODO:
			
			return 1;
		}
		
		// ======== set_domain/range ===========
		else if (cmd.startsWith("set_domain") || cmd.startsWith("set_range")) {
			PackData pd=packData;
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				int qnum=StringUtil.qItemParse(items);
				if (qnum>=0) {
					pd=CPBase.pack[qnum].packData;
					rangePackNum=pd.packNum;
				}
			}
			
			// Now check 'pd' for 'AffinePack' (first) or 'ProjStruct'
			//    extender; if there, try to use its 'aspects' data.
			TriAspect []ourTri=null;
			boolean hitap=false;
			TriAspect []aspect=null;
			Iterator<PackExtender> pXs=pd.packExtensions.iterator();
			while (pXs.hasNext() && !hitap) {
				PackExtender pe=(PackExtender)pXs.next();
				if (pe.extensionAbbrev.equalsIgnoreCase("ap")) {
					AffinePack afpex=(AffinePack)pe;
					if (afpex.aspects!=null || afpex.aspects.length==packData.faceCount+1) { 
						aspect=afpex.aspects;
						hitap=true;
					}
				}
				
				// only look for 'ProjStruct' if as yet no 'AffinePack'.
				if (!hitap && pe.extensionAbbrev.equalsIgnoreCase("ps")) {
					ProjStruct pspex=(ProjStruct)pe;
					if (pspex.aspects!=null || pspex.aspects.length==pd.faceCount+1) { 
						aspect=pspex.aspects;
					}
				}
			}
			
			// If already exists, update tangency points
			if (aspect!=null) { // create and fill ourTri
				ourTri=new TriAspect[pd.faceCount+1];
				for (int f=1;f<=pd.faceCount;f++) {
					ourTri[f]=new TriAspect(aspect[f]);
					DualTri dtri=new DualTri(pd.hes,
						aspect[f].getCenter(0),aspect[f].getCenter(1),aspect[f].getCenter(2));
					ourTri[f].tanPts=new Complex[3];
					for (int j=0;j<3;j++)
						ourTri[f].tanPts[j]=new Complex(dtri.TangPts[j]);
				}
			}
			
			// else create 'TriAspect's from scratch using dual tree.
			else {
				ourTri=PackData.getTriAspects(pd);
			}
			
			// which is it?
			if (cmd.startsWith("set_d"))
				domainTri=ourTri;
			else {
				rangeTri=ourTri;
				rangeHes=pd.hes;
			}
			
			return 1;
		}
		
		// ========== go ===== 
		// Compute schwarzian data for all faces f; following our notation
		//   conventions, for each positively oriented edge e of f, the 
		//   derivative is dM(e)= mob_g^{-1}(mob_f), g the face across e. 
		if (cmd.startsWith("go")) {
			
			boolean debug=false;
			
			Mobius []faceMobs=setFaceMobs();
			if (faceMobs==null) {
				CirclePack.cpb.errMsg("failed to set face Mobius maps");
				return 0;
			}
			
			for (int f=1;f<=packData.faceCount;f++) {
				TriAspect myasp=domainTri[f];
				myasp.MobDeriv=new Mobius[3];
				myasp.sch_coeffs=new double[3];
				for (int j=0;j<3;j++) {
					int g=-1;
					int jf=j;
					int jf1=(jf+1)%3;
					int v=myasp.vert[jf];
					int w=myasp.vert[jf1];
					if ((g=packData.face_right_of_edge(v,w))>=0) {
						try {
							// <v,w> has f on its left, g on its right
							int jg=domainTri[g].vertIndex(w);
							int jg1=(jg+1)%3;
							
							// Recall that the packings may not be planar, so we
							//   want to align the neighboring faces before computing
							
							// Mobius of g to align it with f along <v,w>
							Mobius domainalign=new Mobius(); // identity
							Complex fvz=domainTri[f].getCenter(jf);
							Complex fwz=domainTri[f].getCenter(jf1);
							Complex gvz=domainTri[g].getCenter(jg1);
							Complex gwz=domainTri[g].getCenter(jg);
							
							// misaligned? (compare centers, don't worry about radii)
							if ((fvz.minus(gvz).abs()+fwz.minus(gwz).abs()>0.001*domainTri[f].getRadius(jf))) {
								domainalign=Mobius.mob_tang_circles(gvz, domainTri[g].getRadius(jg1), 
										gwz, domainTri[g].getRadius(jg),
										fvz,domainTri[f].getRadius(jf), 
										fwz, domainTri[f].getRadius(jf1),
										packData.hes,packData.hes);
								// old method
//								Complex ftanz=domainTri[f].tanPts[jf];
//								Complex gtanz=domainTri[g].tanPts[jg];
//								domainalign=Mobius.
//									mob_xyzXYZ(gvz, gtanz, gwz,fvz, ftanz,fwz,packData.hes,packData.hes);
							}
							
							// Mobius to align image of g with image of f along <v,w>
							Mobius galign=new Mobius();
							fvz=rangeTri[f].getCenter(jf);
							fwz=rangeTri[f].getCenter(jf1);
							gvz=rangeTri[g].getCenter(jg1);
							gwz=rangeTri[g].getCenter(jg);
							
							// misaligned? (compare centers, don't worry about radii)
							if ((fvz.minus(gvz).abs()+fwz.minus(gwz).abs()>0.001*rangeTri[f].getRadius(jf))) {
								domainalign=Mobius.mob_tang_circles(gvz, rangeTri[g].getRadius(jg1), 
										gwz, rangeTri[g].getRadius(jg),
										fvz,rangeTri[f].getRadius(jf), 
										fwz, rangeTri[f].getRadius(jf1),
										rangeHes,rangeHes);
							}
							
							Mobius newg=(Mobius)(faceMobs[g]).
									lmult(galign).rmult(domainalign.inverse());
							
							// By our notation conventions, dM(e)=mob_g^{-1}*mob_f
							myasp.MobDeriv[jf]=(Mobius)newg.inverse().rmult(faceMobs[f]);
							
							// may need this normalization to get trace=+2.0;
							Complex tc=myasp.MobDeriv[jf].a.plus(myasp.MobDeriv[jf].d);
							if (tc.x<0.0) {
								myasp.MobDeriv[jf].a=myasp.MobDeriv[jf].a.times(-1.0);
								myasp.MobDeriv[jf].b=myasp.MobDeriv[jf].b.times(-1.0);
								myasp.MobDeriv[jf].c=myasp.MobDeriv[jf].c.times(-1.0);
								myasp.MobDeriv[jf].d=myasp.MobDeriv[jf].d.times(-1.0);
								tc=tc.times(-1.0);
							}
							
							// now, find the real Schwarzian s: recall that the 
							//   (2,1) entry (i.e., c) of the Mobius is s times the 
							//   inward unit normal vw_perp of the edge; therefore,
							//    we have s=c/vw_perp.
							Complex ctr1=domainTri[f].getCenter(jf1);
							Complex ctr=domainTri[f].getCenter(jf);
							if (packData.hes>0) { // sph
								CircleSimple sC=SphericalMath.s_to_e_data(ctr1,domainTri[f].getRadius(jf1));
								ctr1=sC.center;
								sC=SphericalMath.s_to_e_data(ctr,domainTri[f].getRadius(jf));
								ctr=sC.center;
							}
							if (packData.hes<0) {
								CircleSimple sC=HyperbolicMath.h_to_e_data(ctr1,domainTri[f].getRadius(jf1));
								ctr1=sC.center;
								sC=HyperbolicMath.h_to_e_data(ctr,domainTri[f].getRadius(jf));
								ctr=sC.center;
							}
							
							// get inward unit normal
							Complex vw_perp=ctr1.minus(ctr).times(new Complex(0,1));
							vw_perp=vw_perp.divide(vw_perp.abs());
							
							// compute s
							Complex sder=myasp.MobDeriv[jf].c.divide(vw_perp);
							myasp.sch_coeffs[jf]=sder.x;
							
							if (Math.abs(sder.y)>.001) {
								CirclePack.cpb.errMsg("schwarz coeff should be real; imag = "+sder.y);
								if (debug) {
									faceMobs[f].MobPrint("faceMob_f");
									faceMobs[g].MobPrint("faceMob_g");
									myasp.MobDeriv[jf].MobPrint("MobDeriv");
								}
							}
						} catch(Exception ex) {
							Oops("failed to compute dM for face "+f+" index "+jf);
						}
					}
					else 
						myasp.MobDeriv[jf]=null;
				}

				if (debug) {
					Complex Z0=faceMobs[f].apply(domainTri[f].getCenter(0));
					Complex Z1=faceMobs[f].apply(domainTri[f].getCenter(1));
					Complex Z2=faceMobs[f].apply(domainTri[f].getCenter(2));
					System.out.println("\nFace "+f+", centers (domain,range): ("+
						domainTri[f].getCenter(0).toString()+", "+Z0.toString()+")\n"+
						"        = ("+
						domainTri[f].getCenter(1).toString()+", "+Z1.toString()+")\n"+
						"        = ("+
						domainTri[f].getCenter(2).toString()+", "+Z2.toString()+")\n");
				}
			}
		}
		
		// ========== s_out ================
		else if (cmd.startsWith("s_out")) {
			if (!StringUtil.ckTrailingFileName(flagSegs)) 
				throw new ParserException("usage: 'output -f <filename>'");
			if (domainTri==null) {
				CirclePack.cpb.errMsg("'domainTri' does not exist");
				return 0;
			}
			if (domainTri[1].sch_coeffs==null) {
				CirclePack.cpb.errMsg("Schwarzians are not computed in 'domainTri'");
				return 0;
			}

			StringBuilder strbuf=new StringBuilder();
			int code=CPFileManager.trailingFile(flagSegs,strbuf);
			File file=new File(strbuf.toString());
			boolean append=false;
			if ((code & 02) == 02) // append
				append=true;
			BufferedWriter fp=CPFileManager.openWriteFP(
					(File)CPFileManager.PackingDirectory,append,
					file.getName(),false);
			
			// write data on each interior edge <v,w>, where w > v
			try {
				if (!append)
					fp.write("CHECKCOUNT: "+packData.nodeCount+"\n");
				fp.write("SCHWARZIAN COEFF: v w s   \n");
				for (int f=1;f<=packData.faceCount;f++) {
					TriAspect ftri=domainTri[f];
					for (int j=0;j<3;j++) {
						int v=ftri.vert[j];
						int w=ftri.vert[(j+1)%3];
						if (v<w && (packData.kData[v].bdryFlag==0 || 
								packData.kData[w].bdryFlag==0)) { 
							fp.write(v+" "+w+"  "+String.format("%.8f",
									ftri.sch_coeffs[j])+"\n");
						}
					}
				}
				fp.write("(done)\n");
				fp.flush();
				fp.close();
			} catch (Exception ex) {
				try {
					fp.close();
				} catch (Exception iox) {}
				return 0;
			}
				
			CirclePack.cpb.msg("Wrote Schwarzians coeffss to '"+file.getName()+
					"' in "+CPFileManager.PackingDirectory);
			return 1;
		}
		
		// ========== output ===============
		else if (cmd.startsWith("outp")) {
			
			if (!StringUtil.ckTrailingFileName(flagSegs)) {
				throw new ParserException("usage: 'output -f <filename>'");
			}
			if (domainTri==null) {
				CirclePack.cpb.errMsg("'domainTri' does not exist");
			}
				
			StringBuilder strbuf=new StringBuilder();
			int code=CPFileManager.trailingFile(flagSegs,strbuf);
			File file=new File(strbuf.toString());
			boolean append=false;
			if ((code & 02) == 02) // append
				append=true;
			BufferedWriter fp=CPFileManager.openWriteFP(
					(File)CPFileManager.PackingDirectory,append,
					file.getName(),false);
			
			// write data on each interior edge <v,w>, w>v
			try {
				fp.write("% Discrete Schwarzian data: v w  z.x z.y "+
						"mobius dM \ndM_data = [\n");
				for (int f=1;f<=packData.faceCount;f++) {
					for (int j=0;j<3;j++) {
						int v=domainTri[f].vert[j];
						int w=domainTri[f].vert[(j+1)%3];
						if (v<w && (packData.kData[v].bdryFlag==0 || 
								packData.kData[w].bdryFlag==0)) {
							Mobius mob=domainTri[f].MobDeriv[j];
							fp.write(v+" "+w+"  "+String.format("%.6f",domainTri[f].tanPts[j].x)+
									" "+String.format("%.6f",domainTri[f].tanPts[j].y)+
									" "+String.format("%.6f",mob.a.x)+" "+
									String.format("%.6f",mob.a.y)+
									" "+String.format("%.6f",mob.b.x)+" "+
									String.format("%.6f",mob.b.y)+
									" "+String.format("%.6f",mob.c.x)+" "+
									String.format("%.6f",mob.c.y)+
									" "+String.format("%.6f",mob.d.x)+" "+
									String.format("%.6f",mob.d.y)+"\n");
						}
					}
				}
				fp.write("]\n");
				fp.flush();
				fp.close();
			} catch (Exception ex) {
				try {
					fp.close();
				} catch (Exception iox) {}
			}
			
			CirclePack.cpb.msg("Schwarzian data to '"+file.getName()+"' in "+
					CPFileManager.PackingDirectory);
		}
		return 1;
	}

	/**
	 * Get the schwarzian coefficient and tangency point for edge <v,w>
	 * @param v int
	 * @param w int
	 * @return double[] = {coeff, x, y} 
	 */
	public double []get_s_coeff(int v,int w) {
		if (v<1 || v>packData.nodeCount || w<1 || w>packData.nodeCount)
			CirclePack.cpb.errMsg("something wrong with v or w");
		int f=packData.face_right_of_edge(w,v);
		int j=packData.find_index(f, v);
		if (domainTri[f].sch_coeffs==null || domainTri[f].tanPts==null)
			CirclePack.cpb.errMsg("'sch_coeffs' of 'tanPts' do not exist");
		double []ans=new double[3];
		ans[0]=domainTri[f].sch_coeffs[j];
		ans[1]=domainTri[f].tanPts[j].x;
		ans[2]=domainTri[f].tanPts[j].y;
		return ans;
	}
	
	/**
	 * Store a new schwarzian coefficient for oriented edge <v,w> 
	 * and its negative for <w,v>; also set associated MobDeriv's.
	 * Note that faces f/g are left/right of <v,w>, respectively.
	 * @param v int
	 * @param w int
	 * @param s_coeff double
	 * @return boolean, false on failure
	 */
	public boolean set_s_coeff(int v,int w,double s_coeff) {
		if (v<1 || v>packData.nodeCount || w<1 || w>packData.nodeCount)
			return false;
        int f=packData.face_right_of_edge(w,v);
        int g=packData.face_right_of_edge(v,w);
        if (f<=0 || g<=0)
        	return false;
        TriAspect ftri=domainTri[f];
        TriAspect gtri=domainTri[g];		
        int jf=ftri.vertIndex(v);
        ftri.sch_coeffs[jf]=s_coeff;
        int jg=gtri.vertIndex(w);
        gtri.sch_coeffs[jg]=-1.0*s_coeff;
    
        // store the Mobius edge derivatives for f and g
        Mobius mobf=new Mobius();
        Complex tpt=ftri.tanPts[jf];
        Complex vw_perp=ftri.getCenter((jf+1)%3).minus(ftri.getCenter(jf)).times(new Complex(0,1));
        vw_perp=vw_perp.divide(vw_perp.abs());
        Complex c=vw_perp.conj().times(s_coeff);
        mobf.a=c.times(tpt).add(new Complex(1,0));
        mobf.b=c.times(tpt).times(tpt).times(-1.0);
        mobf.c=new Complex(c);
        mobf.d=c.times(tpt).times(-1.0);
	
        ftri.MobDeriv[jf]=mobf;
        gtri.MobDeriv[jg]=(Mobius) mobf.inverse();
        return true;
	}
	
	/**
	 * If 'domainTri' and 'rangeTri' have data, then use the tangency 
	 * points to compute the Mobius maps for all the faces. So faceMobs[f]
	 * maps domain face f to range face f. 
	 * TODO: in process 1/2020 in converting to use 'baseMobius' instead.
	 * @return Mobius[] or null on error
	 */
	public Mobius []setFaceMobs() {
		if (domainTri==null || rangeTri==null) {
			CirclePack.cpb.errMsg("'domainTri', 'rangeTri' must "+
					"have data to construct face Mobius maps");
			return null;
		}
		
		Mobius []faceMobs=new Mobius[packData.faceCount+1];
		for (int f=1;f<=packData.faceCount;f++) {
			try {
				faceMobs[f]=Mobius.mob_xyzXYZ(domainTri[f].tanPts[0],
					domainTri[f].tanPts[1],domainTri[f].tanPts[2],
					rangeTri[f].tanPts[0],rangeTri[f].tanPts[1],
					rangeTri[f].tanPts[2],packData.hes,rangeHes);
			} catch(Exception ex) {
				return null;
			}
		} 
		
		return faceMobs;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("radW","{v..}",null,"Create and display a widget "+ 
				"for adjusting radii"));
		cmdStruct.add(new CmdStruct("put","[-q{n} [{f g .. }]",null,"Put rangeTri "+
				"radii/centers into packing {n} (default to image). First check "+
				"p{n} geometry and combinatorics. Determine for dual edge pairs (f,g) "+
				"(default to spanning tree); add root to guarantee placing of first face."));
		cmdStruct.add(new CmdStruct("set_domain","[-q{}]",null,"Fill 'domainTri' "+
				"data; default to this packing"));
		cmdStruct.add(new CmdStruct("set_range","[-q{}]",null,"Fill 'rangeTri' "+
				"data; default to this packing"));
		cmdStruct.add(new CmdStruct("go",null,null,"Compute and store Schwarzians; "+
				"call 'set_range' using current packing if necessary"));
		cmdStruct.add(new CmdStruct("get","{v w ..}",null,"Compute Schwarzian "+
				"for designated edges"));
		cmdStruct.add(new CmdStruct("get_tree",null,null,"copy spanning tree into 'glist'"));
		cmdStruct.add(new CmdStruct("s_load","<filename>",null,"Read Schwarzian "+
				"derivates from a file into 'rangeTri'"));
		cmdStruct.add(new CmdStruct("s_map","-q{q} [{g f ..}]",null,"Use Schwarzians "+
				"stored in 'rangeTri' to successively insert radii and centers into "+
				"'rangeTri'. Pairs {f,g} defining dual edges are given: default to "+
				"spanning tree. New faces are laid out in q canvas. "));
		cmdStruct.add(new CmdStruct("s_out","-[fa] <filename>",null,"Write Schwarzian "+
				"scalars to file: f g  sch_coeff"));
		cmdStruct.add(new CmdStruct("s_set","{v w s ...}",null,"Set the Schwarzian "+
				"scalar for oriented edge v,w to 's' (and w,v to -s)"));
		cmdStruct.add(new CmdStruct("s_inc","x {f g ..}",null,"Increment "+
				"schwarzian by factor x for given dual edges"));
		cmdStruct.add(new CmdStruct("output","-[fa] <filename>",null,"Write dM data to "+
				"matlab-style file: 'v w tangPt dM'"));
		cmdStruct.add(new CmdStruct("field",null,null,"display vector field of "+
				"Schwarzian derivatives, color/length encoded."));
	}


}
