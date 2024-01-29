package ftnTheory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import dcel.Schwarzian;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.MiscException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.NodeLink;
import math.Mobius;
import packing.PackCreation;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;
import widgets.RadiiSliders;
import widgets.SchwarzSliders;

/** 
 * This is code for exploring discrete Schwarzian derivatives.
 * One goal is to handle packings on the sphere, another is to
 * study projective structures on more general Riemann surfaces. 
 * Motivated by Gerald Orick's thesis, I am pursuing a slightly
 * modified version of his "directed Mobius edge derivative"
 * along with an "intrinsic" schwarzian associated with maps
 * from a standardized "base equilateral". 
 * 
 * The concern is with maps between tangency circle packings 
 * sharing the same combinatorics. A "circle packing quadrangle" 
 * refers to two faces sharing an edge e. Given map phi:P --> P' 
 * between circle packings, for each face f and f'=phi(f), M(f) 
 * is the "face Mobius map" of f onto f' which identifies 
 * corresponding tangency points. (These face mappings were 
 * pieced together, along with extensions to circle interiors,
 * by He/Schramm to define point mappings between circle packing
 * carriers and was used to prove convergence results.) 
 * 
 * **** CONVENTION: Given directed edge {v,w} shared by faces f and g, 
 * we reverse Orick's convention and let f be the face on the LEFT of 
 * {v,w}, g the face on the RIGHT. Note that the ordered pair {f,g} is 
 * the "dual" edge to {v,w} and geometrically ends up clockwise 
 * perpendicular to {v,w}, so it points out of f.
 * 
 * For each interior edge e sharing faces f and g, define the "directed 
 * Mobius edge derivative" by dM(e)=M(g)^{-1}.M(f). This is invariant: 
 * dM(e) is unchanged if we replace P' by P" = m(P'), m a Mobius map. 
 * 
 * The Mobius maps dM(e) fix the tangency point within e and are always 
 * parabolic, and we normalize so trace(dM(e))=2 and det(dM(e))=1. If
 * dM(e) ~ [a b | c d], then the complex entry c is the "(complex 
 * discrete) Schwarzian" as defined by Orick. More precisely, if 
 * z is the tangency point in the edge e then dM(e) has the form
 *      dM(e)= [1 + c*z,-c*z^2; c, 1-c*z]
 * Moreover, c = s*i*conj(delta), where delta = exp{i theta} gives the
 * direction of the directed edge e and s is some real scalar. If
 * eta is the normal to e directed outward from f (so eta=-i*delta),
 * then c=s*conj(eta). Note that the complex schwarzian for -e is -c.
 * (This real number s is distinct from the intrinsic schwarzian we 
 * generally work with;see below.) 
 * 
 * In contrast to complex schwarzians related to mappings between
 * TWO packings, I am introducing intrinsic schwarzians associated 
 * with edges of a SINGLE tangency packing P. The "schwarzian" 
 * element of an edge of P is this intrinsic schwarzian, a real 
 * number defined as above for maps from a "base equilateral". 
 * This base equilateral is centered at the origin and has its edge 
 * midpoints (tangency points of its circles) at the cube roots of 
 * unity.
 * 
 * One of our main goals is to understand conditions on those
 * assignments of schwarzians which correspond to circle packings.
 * Also, to understand the relationship between the intrinsic
 * schwarzians of P and P' when considering a mapping. See
 * 'dcel.schwarzian.java'. 
 * 
 * This extender is mainly about the mapping case and we 
 * store data for both domain and range packing, each having 
 * a 'TriAspect' for each face: domain data stored on 
 * initiation (or adjusted later), range data stored on 
 * demand. Given {f,g} in domain and {F,G} in range, we 
 * have stored the complex schwarzian for the map in 
 * 'domainTri.mobDeriv[.]'. The intrinsic schwarzians are
 * stored locally in each TriAspect 'schwarzian' and are 
 * only moved to 'DcelFace.schwarzian' in the packing 
 * when requested. Note that the range packing is a global 
 * Mobius image of domain packing iff all 'mobDeriv' are 
 * zero iff all intrinsic schwarzians are zero.
 * 
 * A second use of this extender is to experiment with
 * flower layouts using intrinsic schwarzians: see 'flower'
 * command and commands when 'flowerDegree' is positive.
 * Note that indexing is ticklish.
 * 
 * TODO: fix 'layOrder' so that its 'HalfEdge's are from the 
 * correct PackDCEL.
 * 
 * @author kens, November 2018; more work 3/2022.
 * */
public class SchwarzMap extends PackExtender {
	
	public TriAspect[] domainTri; // domain faces
	public TriAspect[] rangeTri;  // created with 'set_range' or filled by 'go'
	public int rangeHes;          // range geometry
	public int rangePackNum;

	public Mobius[] mobDerivs;    // edge Mobius derivatives by edge index
	
	public HalfLink layOrder;  // drawing order, default to full 'layoutOrder' 
	
	public RadiiSliders radSliders; // for opening radius slider window
	public SchwarzSliders schSliders; // for opening schwarzian slider window
	public int flowerDegree; // 0 if not in flower mode
	public double[] schvector; // in flower mode, vector of schwarzians
	public int[] petalticks; // which petals are in place
	
	// Constructor
	public SchwarzMap(PackData p) {
		super(p);
		extensionType="SCHWARZIAN";
		extensionAbbrev="SM";
		toolTip="'Schwarz Mapping' is for developing and "+
				"exploiting a discrete Schwarzian derivative "+
				"proposed by Gerald Orick";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}
		// default: look at 'set_domain' call
		domainTri=setupTri(packData);
		Schwarzian.comp_schwarz(packData,new HalfLink(packData,"i"));
		
		// range default: look at 'set_range' call
		rangeTri=setupTri(packData);
		rangePackNum=packData.packNum;
		// may be reset when rangeTri is filled.
		rangeHes=packData.hes;
		
		// are we working in flower mode?
		flowerDegree=0;
	}
	
	/**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// separate command options once in flower mode
		if (flowerDegree>0 && cmd.startsWith("sch")) {
			if (flagSegs==null || flagSegs.size()==0)
				Oops("no 'sch' command strings given");
			int fd=flowerDegree;
			
			int hit=0;
			Iterator<Vector<String>> its=flagSegs.iterator();
			while (its.hasNext()) {
				items=its.next();
				String str=items.get(0);
				if (StringUtil.isFlag(str)) {
					switch (str.charAt(1)) {
					case 'c': // cycle the sequence of schwarzians
					{
						double hold=schvector[1];
						for (int j=1;j<fd;j++)
							schvector[j]=schvector[j+1];
						schvector[fd]=hold;
						hit++;
						break;
					}
					case 'f': // full: layout using initial flowerDegree-3
						// schwarzians, then compute the final 3 schwarzians.
					{
						double[] ts=new double[fd+1]; // tangency of petal
						double[] rads=new double[fd+1]; // radius of petal
						ts[1]=0;
						rads[1]=1.0; // radius of c_1 is 1
						
						// compute c_2
						double[] sit2=
								Schwarzian.situationInitial(schvector[1]);
						ts[2]=sit2[0];
						rads[2]=sit2[1];
						packData.packDCEL.setVertCenter(2,
							new Complex(ts[2],-rads[2]));
						packData.packDCEL.setVertRadii(2,rads[2]);
						hit++;
						
						// lay out c_3 through c_{fd-1}
						for (int j=3;j<fd-1;j++) {
							double t=packData.packDCEL.vertices[j-1].center.x;
							double r=packData.packDCEL.vertices[j-2].rad;
							double R=packData.packDCEL.vertices[j-1].rad;
							double[] sit3=
									Schwarzian.situationGeneric(schvector[j-1],r,R);
							ts[j]=t+sit3[0];
							rads[j]=sit3[1];
							packData.packDCEL.setVertCenter(j,
									new Complex(ts[j],-rads[j]));
							packData.packDCEL.setVertRadii(j,rads[j]);
							if (rads[j]>=1.0)
								msg("Petal "+j+" has radius > 1; rad = "+rads[j]);
							hit++;
						}
						
						// place the last petal, c_{fd-1}, forcing radius 1,
						double r=packData.packDCEL.vertices[fd-3].rad;
						double R=packData.packDCEL.vertices[fd-2].rad;
						
						// sidetrack: see computed value first using s_{fd-2}
						double[] sit3=
								Schwarzian.situationGeneric(schvector[fd-2],r,R);
						if (Math.abs(1.0-sit3[1])>.01)
							msg("Final petal computed radius is not 1; rad = "+sit3[1]);

						// force rad of fd-1 to be 1; place c_{fd-2}
						rads[fd-1]=1.0;
						double delt=2.0*Math.sqrt(rads[fd-2]);
						ts[fd-1]=ts[fd-2]+delt;
						packData.packDCEL.setVertCenter(fd-1,
								new Complex(ts[fd-1],-rads[fd-1]));
						packData.packDCEL.setVertRadii(fd-1,1.0); // force rad 1

						// compute s_{fd-2} based on previous radii, 
						//   formula (R3)
						r=rads[fd-3];
						R=rads[fd-2];
						schvector[fd-2]=1.0-(Math.sqrt(R*r)+Math.sqrt(R))/
								(CPBase.sqrt3*Math.sqrt(r));
						// store schwarzian in edge
						HalfEdge he=packData.packDCEL.
								findHalfEdge(new EdgeSimple(fd+1,fd-2));
						packData.setSchwarzian(he,schvector[fd-2]);
						
						// compute s_[fd-1] using delt; formula (S2)
						schvector[fd-1]=1.0-2/(CPBase.sqrt3*delt);
						// store schwarzian in edge
						he=packData.packDCEL.
								findHalfEdge(new EdgeSimple(fd+1,fd-1));
						packData.setSchwarzian(he,schvector[fd-1]);
						
						// compute s_{fd} using t_{fd-1}; formula (S1)
						schvector[fd]=1.0-ts[fd-1]/(2*CPBase.sqrt3);
						// store schwarzian in edge
						he=packData.packDCEL.
								findHalfEdge(new EdgeSimple(fd+1,fd));
						packData.setSchwarzian(he,schvector[fd]);
						cpCommand(packData,"disp -wr");
						hit++;
						break;
					}
					case 's': // vector of schwarzians;
					{
						items.remove(0);
						int len=items.size();
						if (len>flowerDegree) {
							Oops("Too many schwarzians");
						}
						try { // Note: s_j is j entry
							for (int j=1;j<=len;j++) {
								schvector[j]=Double.parseDouble(items.get(j-1));
								HalfEdge he=packData.packDCEL.findHalfEdge(new EdgeSimple(flowerDegree+1,j));
								packData.setSchwarzian(he,schvector[j]);
								hit++;
							}
						} catch(Exception ex) {
							throw new ParserException("problem reading schwarzian");
						}
						break;
					}
					case 'r': // 
					{
						petalticks=new int[flowerDegree+1]; // indexed from 1
						petalticks[1]=petalticks[2]=1;
						// set rest along real axis, radius .025
						for (int v=3;v<=flowerDegree;v++) {
							double x=(v-2)*.2;
							packData.packDCEL.setVertCenter(v, new Complex(x));
							packData.packDCEL.setVertRadii(v, .025);
						}
						cpCommand(packData,"disp -wr");
						hit++;
						break;
					}
					case 'n': // draw next {n} petals 
					{
						// The 'indx>=3' petal is the first not computed and
						//    needs schvector[indx-1].
						int vertindx=0;
						int tick=3;
						while (tick<=flowerDegree) {
							if(petalticks[tick]==0) {
								vertindx=tick;
								break;
							}
							tick++;
						}
						if (vertindx==0) // didn't find petal to place
							break;
						
						items.remove(0);
						
						// is preceeding schwarzian given? 
						//    else try schvector[indx-1]; 
						if(items.size()>0) {
							try {
								double sch=Double.parseDouble(items.get(0));
								schvector[vertindx-1]=sch;
							} catch(Exception ex) {
								Oops("failed to get schwarzian");
							}
						}
						
						// compute new petal c_{vertindx}
						if (vertindx==3) { // initial case for c_3
							double[] sit2=
								Schwarzian.situationInitial(schvector[vertindx-1]);
							packData.packDCEL.setVertCenter(vertindx,
									new Complex(sit2[0],-sit2[1]));
							packData.packDCEL.setVertRadii(vertindx,sit2[1]);
							petalticks[vertindx]=1;
							hit++;
						}
						else if (vertindx<=flowerDegree) { // generic case
							double t=packData.packDCEL.vertices[vertindx-1].center.x;
							double r=packData.packDCEL.vertices[vertindx-2].rad;
							double R=packData.packDCEL.vertices[vertindx-1].rad;
							double[] sit3=
									Schwarzian.situationGeneric(schvector[vertindx-1],r,R);
							packData.packDCEL.setVertCenter(vertindx,
									new Complex(t+sit3[0],-sit3[1]));
							packData.packDCEL.setVertRadii(vertindx,sit3[1]);
							petalticks[vertindx]=1;
							hit++;
						}
						
						// plot this new circle, Disp for circles up to this one
						if (hit>0) {
							cpCommand(packData,"disp -wr");
						}
						break;
					}
					case 'm': // Place last petal based on s_1
					{
						int vertindx=flowerDegree;
						double tn=Schwarzian.situationMax(schvector[1]); // petal c_1 schwarzian
						packData.packDCEL.setVertCenter(vertindx,new Complex(tn,-1.0));
						packData.packDCEL.setVertRadii(vertindx,1.0);
						
						// display in red
						cpCommand(packData,"disp -cc190 "+ vertindx);
						hit++;
						break;
					}
					case 'l': // show schvector in copyable form
					{
						StringBuilder strbld=new StringBuilder();
						for (int v=1;v<=fd;v++) 
							strbld.append(schvector[v]+"  ");
						msg("schwarzians: "+ strbld.toString());
						hit++;
						break;
					}
					case 'x': // exit flower mode
					{
						flowerDegree=0;
						schvector=null;
						petalticks=null;
						hit++;
						break;
					}
					} // end of switch
				} // done with next flag
			} // end of while
			return hit;
		} // end of commands while in flowerMode
		
		// ======= enter flower mode ============
		else if (cmd.startsWith("flower")) {
			flowerDegree=6; // intended degree, default to 6
			int M=flowerDegree+1; // center's index
			
			if (flagSegs!=null && flagSegs.size()>0) {
				String str=flagSegs.get(0).get(0);
				try {
					int n=Integer.parseInt(str);
					if (n<3)
						throw new ParserException();
					flowerDegree=n;
					M=flowerDegree+1;
				}catch(Exception ex) {
					Oops("didn't get legal degree, not in flower mode");
				}
			}
			
			// create new flower; for sake of indexing petals
			//    as c_1,c_2, ...,c_n, we put max index at center
			PackData newData=PackCreation.seed(flowerDegree,0);
   		  	if (newData==null) 
   		  		throw new CombException("seed has failed");
   		  	packData=CirclePack.cpb.swapPackData(newData,packData.packNum,true);
   		  	packData.swap_nodes(1,M);
   		  	for (int j=1;j<flowerDegree;j++)
   		  		packData.swap_nodes(j,j+1);
   		  	
   		  	cpCommand(packData,"disp -w");
   			schvector=new double[flowerDegree+1]; // indexed from 1
			petalticks=new int[flowerDegree+1]; // indexed from 1
			petalticks[1]=petalticks[2]=1;
			
			// put in normalized setup; 
			//   * center is upper half plane
			//   * last petal, c_n is half plane {y<=-2*i}
			//   * first petal, c_1 has radius 1, center -i
			//   * remaining petals small, spread out along x-axis
			packData.packDCEL.setVertCenter(M,
					new Complex(1.8019377358e+00,1.7355349565e+04));
			packData.packDCEL.setVertCenter(flowerDegree,
					new Complex(1.8019377359e+00,-1.7355349565e+04));
			packData.packDCEL.setVertCenter(1,
					new Complex(0.0,-1.0));
			packData.packDCEL.setVertRadii(M,1.735534956e+04);
			packData.packDCEL.setVertRadii(flowerDegree,1.735334956e+04);
			packData.packDCEL.setVertRadii(1,1.0);
			
			// set rest to center at origin, radius .025
			for (int v=2;v<flowerDegree;v++) {
				double x=(v-1)*.2;
				packData.packDCEL.setVertCenter(v, new Complex(x));
				packData.packDCEL.setVertRadii(v, .025);
			}
			
			// color
			packData.setCircleColor(M,ColorUtil.cloneMe(ColorUtil.coLor(80)));
			packData.setCircleColor(flowerDegree,ColorUtil.cloneMe(ColorUtil.coLor(100)));
			packData.setCircleColor(1,ColorUtil.cloneMe(ColorUtil.coLor(205)));
			
			// set colors
			cpCommand(packData,"color -c s a");
			cpCommand(packData,"color -c 80 M");
			cpCommand(packData,"color -c 100 "+flowerDegree+" 1");

			// initial display
			cpCommand(packData,"Disp -w -c "+flowerDegree+" 1 -cf a(2,100)");
			
			return 1;
		}
		
		// ======= s_layout =============
		
		else if (cmd.startsWith("s_lay")) {

			// copy from "s_map"

			PackData qData=packData;
			DispFlags cirFlags=null;
			DispFlags faceFlags=null;
			int qnum=packData.packNum; // default to this packing itself
			
			// normally there's a -q{} flag; it has to be first
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				if (!items.isEmpty() &&	items.get(0).startsWith("-q")) {
					if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
						items.remove(0);
						flagSegs.remove(0);
						
						// minimal compatibility check or hyp check:
						//   default is copy of packData
						if (qnum!=packData.packNum) {
							qData=CPBase.cpDrawing[qnum].getPackData();
							if ((qData.faceCount!=packData.faceCount) || 
									qData.hes<0) {
								CirclePack.cpb.msg("Copy domain packing "+
									"into target, p"+qData.packNum+
									", with spherical geometry "+
									"because original target does "+
									"not match nodeCount or is hyperbolic.");
								cpCommand(packData,"copy "+qnum);
								// make range spherical
								cpCommand(qData,"geom_to_s"); 
							}
						}
					}
					else
						Oops("failed to parse '-q' flag");
					
					if (flagSegs.size()>0 && items.size()>0) { 
						Oops("There shouldn't be items left if "+
								"there are more segments");
					}
				}
				
				// there are other flag segments: must be -c or -f
				if (flagSegs!=null && flagSegs.size()>0) {
					Iterator<Vector<String>> flst=flagSegs.iterator();
					while (flst.hasNext()) {
						items=flst.next();
						if (StringUtil.isFlag(items.get(0))) {
							String str=items.remove(0);
							char c=str.charAt(1);
							if (c=='c') { // draw circles
								if (str.length()>2) {
									str=str.substring(2);
									cirFlags=new DispFlags(str);
								}
								else
									cirFlags=new DispFlags("");
							}
							else if (c=='f') { // draw faces
								if (str.length()>2) {
									str=str.substring(2);
									faceFlags=new DispFlags(str);
								}
								else
									faceFlags=new DispFlags("");
							}
						}
					}
				}

				// get list of face pairs, convert to HalfLink of edges
				if (items.size()>0) {
					HalfLink hlink=HalfLink.glist_to_hlink(packData,items);
					if (hlink.size()>0)
						layOrder=hlink;
				}
			} // done with flags
			
			// default to 'layoutOrder'
			if (layOrder==null || layOrder.size()==0)
				layOrder=packData.packDCEL.layoutOrder;
			
			// keeping track of processed faces
			int[] hitfaces=new int[packData.faceCount+1];
			
			// minimal check that radii/centers are in 'rangeTri' 
			//   (in geometry of 'qData')
			if (rangeTri==null || rangeTri.length!=packData.faceCount+1 || 
					rangeTri[1].hes!=qData.hes)
				rangeTri=PackData.getTriAspects(qData);
			
			// Do we need to place the first face?
			// TODO: for now, always layout first edge
			HalfEdge leadedge=layOrder.get(0);
			packData.packDCEL.placeFirstEdge(leadedge);
			int baseface=leadedge.face.faceIndx;
			int count=0;
			
			// If we need to place the base face, we make 
			//   it a 'baseEquilateral', as in 'dcel.Schwarzian.java'.
			if (baseface>0) {
				TriAspect tri=TriAspect.baseEquilateral(qData.hes);
				TriAspect myTri=rangeTri[baseface];
				for (int j=0;j<3;j++) {
					myTri.setRadius(tri.getRadius(j), j);
					myTri.setCenter(tri.getCenter(j), j);
					
					// put in qData as well (though may be changed later)\
					// TODO: here and later, put new data in red chain, 
					//   if appropriate
					qData.setRadius(myTri.vert[j],myTri.getRadius(j));
					qData.setCenter(myTri.vert[j],myTri.getCenter(j));
				}
				rangeTri[baseface].setTanPts();
			
				// Draw  using 'TriAspect' data
				if (cirFlags!=null)
					for (int j=0;j<3;j++) {
						qData.cpDrawing.drawCircle(
							myTri.getCenter(j), myTri.getRadius(j),
							cirFlags);
					}
				if (faceFlags!=null) 
					qData.cpDrawing.drawFace(myTri.getCenter(0),
							myTri.getCenter(1),myTri.getCenter(2),
							myTri.getRadius(0),myTri.getRadius(1),
							myTri.getRadius(2),faceFlags);
				qData.cpDrawing.repaint();
				
				hitfaces[baseface]=1;
				count=1;
			}
			
			// proceed, propagating from face to face. 
			// Note: each 'TriAspect' has rad/center and data 
			// for a given circle that may differ as the layout 
			// progresses. Nevertheless, we store latest rad/cent 
			// into qData
			Iterator<HalfEdge> lit=layOrder.iterator();
			lit.next(); // skip first, already laid
			while (lit.hasNext()) {
				HalfEdge he=lit.next();
				int g=he.face.faceIndx;
				int f=he.twin.face.faceIndx;
				double s=he.getSchwarzian();
				int mode=1; // use 'radii' not 'labels'
		    	int ans=-1;
	    		ans= workshops.LayoutShop.schwPropogate(rangeTri[f],rangeTri[g],
					he.twin,s,mode);
	    		if (ans>0) { // Now, draw this face using 'TriAspect' data
					int J=(rangeTri[g].edgeIndex(he.twin)+1)%3;
					if (cirFlags!=null)  
						qData.cpDrawing.drawCircle(
								rangeTri[g].getCenter(J),
								rangeTri[g].getRadius(J), cirFlags);
					if (faceFlags!=null) 
						qData.cpDrawing.drawFace(rangeTri[g].getCenter(0),
								rangeTri[g].getCenter(1),
								rangeTri[g].getCenter(2),
								rangeTri[g].getRadius(0),
								rangeTri[g].getRadius(1),
								rangeTri[g].getRadius(2),faceFlags);
					qData.cpDrawing.repaint();
					
					hitfaces[g]=1;
					count++;
				}
			} // end of while through dTree
			return count;
		}

		// ======= open radSlider ============
		else if (cmd.startsWith("radS")) {
			PackData qData=CPBase.packings[rangePackNum];
			NodeLink wlist;
			try {
				items=flagSegs.get(0);
				wlist=new NodeLink(qData,items);
			} catch(Exception ex) {
				wlist=new NodeLink(qData,"a");
			}
			qData.radiiSliders=new RadiiSliders(qData,"","",wlist);
			if (qData.radiiSliders==null)
				return 0;
			qData.radiiSliders.setVisible(true);
		}
		
		// ======= open schSlider ============
		else if (cmd.startsWith("schS")) {
			PackData qData=CPBase.packings[rangePackNum];
			HalfLink hlink=null;
			try {
				items=flagSegs.get(0);
				hlink=new HalfLink(qData,items);
			} catch(Exception ex) {
				hlink=new HalfLink(qData,"i");
			}
			qData.schwarzSliders=new SchwarzSliders(qData,"","",hlink);
			if (qData.schwarzSliders==null)
				return 0;
			qData.schwarzSliders.setVisible(true);
		}

		// ======= put ===========
		else if (cmd.startsWith("put")) {
			PackData qData=null;
			if (flagSegs!=null && (items=flagSegs.get(0))!=null) {
				int qnum=rangePackNum;
				if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
					items.remove(0);
					// Want map to develop in specified packing 
					//   (versus back in packData). minimal check 
					//   that packing is same and not hyperbolic; 
					//   else copy packData into qdata.
					if (qnum!=packData.packNum) {
						qData=CPBase.cpDrawing[qnum].getPackData();
						if ((qData.faceCount!=packData.faceCount) || 
								qData.hes<0) {
							CirclePack.cpb.msg("Copy domain packing "+
								"into target, p"+qData.packNum+
								", because current packing does "+
								"not match nodeCount. "+
								"Convert to spherical.");
							cpCommand(packData,"copy "+qnum);
							qData=CPBase.cpDrawing[qnum].getPackData();
							// make range spherical
							cpCommand(qData,"geom_to_s"); 
						}
						rangeTri=PackData.getTriAspects(qData);
					}
				}
			}
			
			// get desired dual edges, default to layoutOrder
			HalfLink layOrder=null;
			if (items.size()>0) 
				layOrder=HalfLink.glist_to_hlink(packData,items);
			if (layOrder==null)
				layOrder=packData.packDCEL.layoutOrder;
			
			// Do we need to place the first face? Only if
			// TODO: for now always lay out first face
			HalfEdge he=layOrder.get(0);
			int baseface=he.face.faceIndx;

			// keep track of how many times a center is reset, error
			int []verthits=new int[packData.nodeCount+1];
			double maxError=0.0;

			if (baseface>0) {
				TriAspect mytri=rangeTri[baseface];
				for (int j=0;j<3;j++) {
					int v=mytri.vert[j];
					qData.setCenter(v,new Complex(mytri.getCenter(j)));
					qData.setRadius(v,mytri.getRadius(j));
					verthits[v]++;
				}
			}
			
			// Now proceed through 'graph'
			Iterator<HalfEdge> lit=layOrder.iterator();
			while (lit.hasNext()) {
				he=lit.next();
				int f=he.face.faceIndx;
				int g=he.twin.face.faceIndx;
				TriAspect mytri=rangeTri[g];
				int indx=(packData.face_nghb(f,g)+2)%3;
				int v=mytri.vert[indx]; // opposite edge with f
				Complex z=mytri.getCenter(indx);
				if (verthits[v]>0) {
					double diff=0.0;
					if (qData.hes>0) 
						diff=SphericalMath.s_dist(z,qData.getCenter(v));
					else
						diff=z.minus(qData.getCenter(v)).abs();
					maxError=(diff>maxError) ? diff:maxError;
				}
				qData.setCenter(v,new Complex(z));
				qData.setRadius(v,mytri.getRadius(indx));
				verthits[v]++;
			}
			
			this.msg("Data for p"+qData.packNum+
					" was set from 'rangeTri' data; "+
					"max swap error in centers was "+
					String.format("%.6f",maxError));
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
			int count=0;
			HalfLink hlink=HalfLink.glist_to_hlink(packData,items);
			Iterator<HalfEdge> his=hlink.iterator();
			while(his.hasNext()) {
				HalfEdge he=his.next();
				he.setSchwarzian(factor*he.getSchwarzian());
				count++;
			}
			return count;
		}

		// ======= s_set ============
		else if (cmd.startsWith("s_set")) {
			if (flagSegs==null || flagSegs.size()<1 || 
					(items=flagSegs.get(0)).get(0).length()<1) {
				CirclePack.cpb.errMsg("usage: s_set {v w s ....}");
			}
			int v;
			int w;
			double s_coeff;
			int tick=0;
			while (items.size()>2) {
				try {
					v=Integer.parseInt(items.remove(0));
					w=Integer.parseInt(items.remove(0));
					s_coeff=Double.parseDouble(items.remove(0));
					if (!setTriSchwarzians(v,w,s_coeff)) 
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
			if (domainTri[1].schwarzian==null) {
				CirclePack.cpb.errMsg("'seems there are no "+
						"schwarz coefficients");
				return 0;
			}
			if (flagSegs==null || flagSegs.size()<1) {
				CirclePack.cpb.errMsg("'seems there are no "+
						"schwarz coefficients");
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
			CirclePack.cpb.msg("Schwarzian data for edge ("+v+
					","+w+"): s_coeff = "+
					String.format("%.8e",rslts[0])+
					"; tang Pt = "+z.toString());
			return 1;
		}

		// ======= s_load ============
		else if (cmd.startsWith("s_lo")) {
			if (domainTri==null) {
				throw new DataException(
					"packing have no 'domainTri' for the Schwarzian");
			}
			StringBuilder strbld =new StringBuilder("");
			int ans=CPFileManager.trailingFile(flagSegs,strbld);
			String filename=strbld.toString();
			if (ans==0 || filename.length()==0) {
				CirclePack.cpb.errMsg("'s_load' requires a file name");
				return 0;
			}

			BufferedReader fp=CPFileManager.openReadFP(
					CPFileManager.PackingDirectory,filename,false);
			if (fp==null) { 
				CirclePack.cpb.errMsg("'s_load' did not find the "+
						"Schwarzian file '"+filename+"' in directory '"+
						CPFileManager.PackingDirectory.toString());
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
                    if (!setTriSchwarzians(v,w,s_coeff)) 
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
			boolean debug=false; // debug=true;

			PackData qData=packData;
			DispFlags dispFlags=new DispFlags("");
			// pink range circles for f
			DispFlags oldFlags=new DispFlags("cc241t6"); 
			// green range circles for new g
			DispFlags newFlags=new DispFlags("cc218t2"); 
			
			// should be at most one item: look for -q{} flag to designate 
			//   image packing first, then display flags for color, 
			//   thickness, fill, etc
			if (flagSegs!=null && (items=flagSegs.get(0))!=null) {
				flagSegs.remove(0); // toss the -s flag
				int qnum=packData.packNum;
				if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
					items.remove(0);
					// Want map to develop in specified packing (versus 
					//   back in packData). Minimal check that packing is 
					//   same, not hyp; else copy packData into qdata.
					if (qnum!=packData.packNum) {
						qData=CPBase.cpDrawing[qnum].getPackData();
						if ((qData.faceCount!=packData.faceCount) || 
								qData.hes<0) {
							CirclePack.cpb.msg("Copy domain packing into "+
								"target, p"+qData.packNum+
								", with spherical "+
								"geometry because original target does "+
								"not match nodeCount or is hyperbolic.");
							cpCommand(packData,"copy "+qnum);
							// make range spherical
							cpCommand(qData,"geom_to_s");
						}
					}
				}
				
				// display flag info; may be separate item
				if (items.size()>0 || flagSegs.size()>0) {
					if (items.size()>0)
						dispFlags=new DispFlags(items.remove(0),
								qData.cpDrawing.fillOpacity);
					// only other flag should be display (can go without flag)
					else { 
						items=flagSegs.get(0);
						if (StringUtil.isFlag(items.get(0))) {
							items.remove(0);
							dispFlags=new DispFlags(items.get(0),
									qData.cpDrawing.fillOpacity);
						}
					}
				}
				
				// Now look for list of face pairs; 
				//    default is spanning tree
				if (items.size()>0) {
					layOrder=HalfLink.glist_to_hlink(qData,items);
				}
			}
			
			if (layOrder==null || layOrder.size()==0)
				layOrder=qData.packDCEL.layoutOrder;
			
			// keeping track of processed faces
			int []hitfaces=new int[packData.faceCount+1];
			
			// ensure radii/centers are in 'rangeTri' 
			//    (in geometry of 'qData')
			if (rangeTri==null || rangeTri.length!=packData.faceCount+1 || 
					rangeTri[1].hes!=qData.hes)
				rangeTri=PackData.getTriAspects(qData);
			
			// Do we need to place the first face?
			HalfEdge he=layOrder.get(0);
			int baseface=he.face.faceIndx;
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
						sC=HyperbolicMath.h_to_e_data(packData.getCenter(v),
								packData.getRadius(v));
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				else if (dom_hes<0 && rangeHes>0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC = HyperbolicMath.h_to_e_data(packData.getCenter(v),
								packData.getRadius(v));
						sC = SphericalMath.e_to_s_data(sC.center, sC.rad);
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				else if (dom_hes==0 && rangeHes>0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC=SphericalMath.e_to_s_data(packData.getCenter(v),
								packData.getRadius(v));
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
					if (debug) {
						qData.cpDrawing.drawFace(mytri.getCenter(0),
								mytri.getCenter(1),mytri.getCenter(2),
								mytri.getRadius(0),
								mytri.getRadius(1),
								mytri.getRadius(2),dispFlags);
						qData.cpDrawing.repaint();
					}
				}
				
				// range should not be hyperbolic 
//				else if (dom_hes==0 && rangeHes<0) {
//					for (int j=0;j<3;j++) {
//						int v=mytri.vert[j];
//						sC=HyperbolicMath.e_to_h_data(packData.rData[v].center,
//							packData.rData[v].rad);
//						mytri.setRadius(sC.rad,j);
//						mytri.setCenter(sC.center,j);
//					}
//				}
//				else if (dom_hes>0 && rangeHes<0) {
//					for (int j=0;j<3;j++) {
//						int v=mytri.vert[j];
//						sC = SphericalMath.s_to_e_data(
//							packData.rData[v].center, packData.rData[v].rad);
//						sC=HyperbolicMath.e_to_h_data(sC.center,sC.rad);
//						mytri.setCenter(sC.center,j);
//						mytri.setRadius(sC.rad,j);
//					}
//				}
				
				else if (dom_hes>0 && rangeHes==0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC = SphericalMath.s_to_e_data(packData.getCenter(v),
								packData.getRadius(v));
						if (sC.flag!=-1) { // normal case
							mytri.setCenter(sC.center,j);
							mytri.setRadius(sC.rad,j);
						}
						else { // encircles infinity?
							mytri.setCenter(sC.center,j);
							mytri.setRadius(-sC.rad,j);
						}
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
				qData.cpDrawing.drawFace(mytri.getCenter(0),
						mytri.getCenter(1),mytri.getCenter(2),
						mytri.getRadius(0),mytri.getRadius(1),
						mytri.getRadius(2),dispFlags);
				qData.cpDrawing.repaint();
				count=1;
				
				// debug?
				if (debug) {
					packData.cpDrawing.drawFace(domainTri[baseface].getCenter(0),
							domainTri[baseface].getCenter(1),
							domainTri[baseface].getCenter(2),
							domainTri[baseface].getRadius(0),
							domainTri[baseface].getRadius(1),
							domainTri[baseface].getRadius(2),dispFlags);
					packData.cpDrawing.repaint();
				}
				
			}
			
			// Now proceed through 'graph'
			Iterator<HalfEdge> lit=layOrder.iterator();
			while (lit.hasNext()) {
				he=lit.next();
				int f=he.face.faceIndx; // should already have its data
				int g=he.twin.face.faceIndx;
				
				// --------- get face f ready --------------------
				// We assume domainTri[f] and rangeTri[f] have radii/centers.
				// Update both 'tanPts', since these faces might have been moved
				// (even in domain, if it's not simply connected)
				rangeTri[f].setTanPts();

				// ------- get face g ready in domain - align with f -------
				int gfindx=packData.face_nghb(f,g);
				
				// using 'alignMe'
				DcelFace gface=packData.packDCEL.faces[g];
				DcelFace fface=packData.packDCEL.faces[f];
				HalfEdge hedge=gface.faceNghb(fface);
				int mode=1; // use 'radii'
				Mobius aligng=domainTri[g].alignMe(domainTri[f],hedge,mode);
				domainTri[g].mobiusMe(aligng);
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
					qData.cpDrawing.drawCircle(rangeTri[f].getCenter(fgindx),
							rangeTri[f].getRadius(fgindx),oldFlags);
					qData.cpDrawing.drawCircle(rangeTri[f].getCenter((fgindx+1)%3),
							rangeTri[f].getRadius((fgindx+1)%3),oldFlags);
					qData.cpDrawing.repaint();
					qData.cpDrawing.drawCircle(gtri.getCenter(gfindx),
							gtri.getRadius(gfindx),newFlags);
					qData.cpDrawing.drawCircle(gtri.getCenter((gfindx+1)%3),
							gtri.getRadius((gfindx+1)%3),newFlags);
					qData.cpDrawing.repaint();
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
				Mobius mobg=(Mobius)fmob.rmult(
						domainTri[f].MobDeriv[fgindx].inverse());
				
				// Either way, apply to domainTri[g] to get last 
				//   circle for rangeTri[g]
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
					qData.cpDrawing.drawCircle(gtri.getCenter((gfindx+2)%3),
							gtri.getRadius((gfindx+2)%3),newFlags);
					qData.cpDrawing.repaint();
				}
				
				// Now, draw this face
				if (dispFlags.label)
					dispFlags.setLabel(Integer.toString(g));
				qData.cpDrawing.drawFace(gtri.getCenter(0),
						gtri.getCenter(1),gtri.getCenter(2),
						gtri.getRadius(0),gtri.getRadius(1),
						gtri.getRadius(2),dispFlags);
				qData.cpDrawing.repaint();
				count++;
			} // end of while through dTree
			return count;
		}
		
		// ======= get_tree (copy it into 'glist')
		else if (cmd.startsWith("get_tre")) {
			if (layOrder==null || layOrder.size()==0)
				return 0;
			return packData.hlist.abutMore(layOrder);
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
								
						// Mobius of g to align it with f along <v,w> 
						//   in the domain
						Mobius domainalign=new Mobius();
						Complex fvz=domainTri[f].getCenter(
								domainTri[f].vertIndex(v));
						Complex fwz=domainTri[f].getCenter(
								domainTri[f].vertIndex(w));
						Complex gvz=domainTri[g].getCenter(
								domainTri[g].vertIndex(v));
						Complex gwz=domainTri[g].getCenter(
								domainTri[g].vertIndex(w));
						double eror=fvz.minus(gvz).abs()+fwz.minus(gwz).abs();
						if (eror>0.001*domainTri[f].getRadius(
								domainTri[f].vertIndex(v))) {
							Complex ftanz=
								domainTri[f].tanPts[domainTri[f].vertIndex(v)];
							Complex gtanz=
								domainTri[g].tanPts[domainTri[g].vertIndex(w)];
							domainalign=Mobius.mob_xyzXYZ(gvz, gtanz, gwz,
									fvz, ftanz,fwz,packData.hes,packData.hes);
						}
						
						// Mobius of image of g to align with image of f 
						//    along <v,w> (in range)
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
						System.out.println("Edge <"+v+","+w+
							"> ; Schwarzian trace is "+
							String.format("%.6f",edgeMob.c.x)+" "+
							String.format("%.6f", edgeMob.c.y)+"; det(dM) = "+
							String.format("%.6f",detdM.x)+" "+
							String.format("%.6f", detdM.y));
					}
					
					count=count+1;
				} catch(Exception ex) {
					Oops("failed to compute Schwarzian for edge <"+
							v+","+w+">");
				}
			}
			
			return count;
		}
		
		// ======= field ===============
		else if (cmd.startsWith("field")) {
			if (domainTri==null || rangeTri==null) {
				Oops("'domainTri', 'rangeTri' must have data to "+
						"construct face Mobius's");
			}
			
			// TODO:
			
			return 1;
		}
		
		// ======== set_domain/range ===========
		else if (cmd.startsWith("set_domain") || 
				cmd.startsWith("set_range")) {
			PackData pd=packData;
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				int qnum=StringUtil.qItemParse(items);
				if (qnum>=0) {
					pd=CPBase.cpDrawing[qnum].getPackData();
					rangePackNum=pd.packNum;
				}
			}
			
			// initiate the TriAspects (or get from an extender)
			TriAspect[] ourTri=setupTri(pd);

			// which is it, domain or range?
			if (cmd.startsWith("set_d"))
				domainTri=ourTri;
			else {
				rangeTri=ourTri;
				rangeHes=pd.hes;
			}
						
			// update intrinsic schwarzians
			Schwarzian.comp_schwarz(pd,new HalfLink(pd,"i"));

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
				myasp.schwarzian=new double[3];
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
							
							// Recall that the packings may not be planar, 
							//   so we want to align the neighboring faces 
							//   before computing
							
							// Mobius of g to align it with f along <v,w>
							Mobius domainalign=new Mobius(); // identity
							Complex fvz=domainTri[f].getCenter(jf);
							Complex fwz=domainTri[f].getCenter(jf1);
							Complex gvz=domainTri[g].getCenter(jg1);
							Complex gwz=domainTri[g].getCenter(jg);
							
							// misaligned? (compare centers, don't worry 
							//    about radii)
							if ((fvz.minus(gvz).abs()+
									fwz.minus(gwz).abs()>
										0.001*domainTri[f].getRadius(jf))) {
								domainalign=Mobius.mob_MatchCircles(gvz,
										domainTri[g].getRadius(jg1), 
										gwz, domainTri[g].getRadius(jg),
										fvz,domainTri[f].getRadius(jf), 
										fwz, domainTri[f].getRadius(jf1),
										packData.hes,packData.hes);
								// old method
//								Complex ftanz=domainTri[f].tanPts[jf];
//								Complex gtanz=domainTri[g].tanPts[jg];
//								domainalign=Mobius.
//									mob_xyzXYZ(gvz, gtanz, gwz,fvz, ftanz,
//										fwz,packData.hes,packData.hes);
							}
							
							// Mobius to align image of g with image of f along <v,w>
							Mobius galign=new Mobius();
							fvz=rangeTri[f].getCenter(jf);
							fwz=rangeTri[f].getCenter(jf1);
							gvz=rangeTri[g].getCenter(jg1);
							gwz=rangeTri[g].getCenter(jg);
							
							// misaligned? (compare centers, don't worry about radii)
							if ((fvz.minus(gvz).abs()+
									fwz.minus(gwz).abs()>
										0.001*rangeTri[f].getRadius(jf))) {
								domainalign=Mobius.mob_MatchCircles(gvz,
										rangeTri[g].getRadius(jg1), 
										gwz, rangeTri[g].getRadius(jg),
										fvz,rangeTri[f].getRadius(jf), 
										fwz, rangeTri[f].getRadius(jf1),
										rangeHes,rangeHes);
							}
							
							Mobius newg=(Mobius)(faceMobs[g]).
									lmult(galign).rmult(domainalign.inverse());
							
							// By our notation conventions, dM(e)=mob_g^{-1}*mob_f
							myasp.MobDeriv[jf]=(Mobius)newg.inverse().
									rmult(faceMobs[f]);
							
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
								CircleSimple sC1=SphericalMath.s_to_e_data(ctr1,
									domainTri[f].getRadius(jf1));
								ctr1=sC1.center;
								CircleSimple sC2=SphericalMath.s_to_e_data(ctr,
									domainTri[f].getRadius(jf));
								ctr=sC2.center;
								if (sC1.flag==-1 || sC2.flag==-1)
									throw new MiscException("A disc contains "+
											"infinity: Schwarz not ready "+
											"for this.");
							}
							if (packData.hes<0) { // hyp
								CircleSimple sC=HyperbolicMath.h_to_e_data(ctr1,
										domainTri[f].getRadius(jf1));
								ctr1=sC.center;
								sC=HyperbolicMath.h_to_e_data(ctr,
										domainTri[f].getRadius(jf));
								ctr=sC.center;
							}
							
							// get inward unit normal
							Complex vw_perp=ctr1.minus(ctr).times(new Complex(0,1));
							vw_perp=vw_perp.divide(vw_perp.abs());
							
							// compute s
							Complex sder=myasp.MobDeriv[jf].c.divide(vw_perp);
							myasp.schwarzian[jf]=sder.x;
							
							if (Math.abs(sder.y)>.001) {
								CirclePack.cpb.errMsg(
									"schwarz coeff should be real; imag = "+sder.y
									+": f="+f+" and j="+j);
								if (debug) {
									faceMobs[f].MobPrint("faceMob_f");
									faceMobs[g].MobPrint("faceMob_g");
									myasp.MobDeriv[jf].MobPrint("MobDeriv");
								}
							}
						} catch(Exception ex) {
							Oops("failed to compute dM for face "+f+
									" index "+jf);
						}
					}
					else 
						myasp.MobDeriv[jf]=null;
				}

				if (debug) {
					Complex Z0=faceMobs[f].apply(domainTri[f].getCenter(0));
					Complex Z1=faceMobs[f].apply(domainTri[f].getCenter(1));
					Complex Z2=faceMobs[f].apply(domainTri[f].getCenter(2));
					System.out.println("\nFace "+f+
						", centers (domain,range): ("+
						domainTri[f].getCenter(0).toString()+", "+
						Z0.toString()+")\n        = ("+
						domainTri[f].getCenter(1).toString()+", "+
						Z1.toString()+")\n        = ("+
						domainTri[f].getCenter(2).toString()+", "+
						Z2.toString()+")\n");
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
			if (domainTri[1].schwarzian==null) {
				CirclePack.cpb.errMsg("Schwarzians are not computed "
						+ "in 'domainTri'");
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
						if (v<w && (!packData.isBdry(v) || 
								!packData.isBdry(w))) { 
							fp.write(v+" "+w+"  "+String.format("%.8f",
									ftri.schwarzian[j])+"\n");
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
						if (v<w && (!packData.isBdry(v) || 
								!packData.isBdry(w))) {
							Mobius mob=domainTri[f].MobDeriv[j];
							fp.write(v+" "+w+"  "+String.format("%.6f",
									domainTri[f].tanPts[j].x)+
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
	} // end of 'cmdParser'

	/**
	 * Get the schwarzian coefficient and tangency point for edge <v,w>
	 * @param v int
	 * @param w int
	 * @return double[] = {coeff, x, y} 
	 */
	public double []get_s_coeff(int v,int w) {
		HalfEdge he=packData.packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (he==null)
			CirclePack.cpb.errMsg("something wrong with edge v or w");
		int f=he.face.faceIndx;
		if (domainTri[f].schwarzian==null || domainTri[f].tanPts==null)
			CirclePack.cpb.errMsg("'sch_coeffs' of 'tanPts' do not exist");
		double []ans=new double[3];
		int j=domainTri[f].vertIndex(v);
		ans[0]=domainTri[f].schwarzian[j];
		ans[1]=domainTri[f].tanPts[j].x;
		ans[2]=domainTri[f].tanPts[j].y;
		return ans;
	}
	
	/**
	 * Store schwarzian for oriented edge <v,w> and <w,v> in
	 * their face TriAspect's. Note that faces f/g are 
	 * left/right of <v,w>, resp.
	 * @param v int
	 * @param w int
	 * @param schw double
	 * @return boolean, false on failure
	 */
	public boolean setTriSchwarzians(int v,int w,double schw) {
		HalfEdge he=packData.packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (he==null)
			return false;
		int f=he.face.faceIndx;
		int g=he.twin.face.faceIndx;
        if (f<=0 || g<=0)
        	return false;
        TriAspect ftri=domainTri[f];
        TriAspect gtri=domainTri[g];		
        int jf=ftri.vertIndex(v);
        ftri.schwarzian[jf]=schw;
        int jg=gtri.vertIndex(w);
        gtri.schwarzian[jg]=schw;
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
	 * Get 'TriAspect's for given packing so tanPts are updated
	 * and 'baseMobius' are computed.
	 * @param pd PackData
	 * @return TriAspet[]
	 */
	public TriAspect[] setupTri(PackData pd) {
	
		TriAspect[] ourTri=PackData.getTriAspects(pd);
		
		// set Mobius maps FROM "base equilateral" to faces
		for (int f=1;f<=pd.faceCount;f++) {
			ourTri[f].baseMobius=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					ourTri[f].tanPts[0],ourTri[f].tanPts[1],ourTri[f].tanPts[2],
					0,pd.hes);
		}
		
		return ourTri;
	}

	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("flower","{n}",null,
				"go into 'flower' mode, degree n"));
		cmdStruct.add(new CmdStruct("sch","-[cflmnsx]",null,
				"c=cycle list,f=full layout,l=list schwarzians,"+
						"m=layout max petal,n=compute next,"+
						"s=set schwarzians,x=exit 'flower' mode"));
		cmdStruct.add(new CmdStruct("radS",
				"{v..}",null,"Create and display a widget for "
						+ "adjusting radii"));
		cmdStruct.add(new CmdStruct("schS",
				"{v w ..}",null,"Create and display a widget for "
						+ "adjusting selected schwarzians"));
		cmdStruct.add(new CmdStruct("put",
				"[-q{n} [{v w .. }]",null,"Put rangeTri "
						+ "radii/centers into packing {n} "
						+ "(default to image). First check "
						+ "p{n} geometry and combinatorics. Determine "
						+ "for edges (default to 'layOrder' tree)"));
		cmdStruct.add(new CmdStruct("set_domain",
				"[-q{}]",null,"Fill 'domainTri' data; default to "
						+ "this packing"));
		cmdStruct.add(new CmdStruct("set_range",
				"[-q{}]",null,"Fill 'rangeTri' data; default "
						+ "to this packing"));
		cmdStruct.add(new CmdStruct("go",
				null,null,"Compute and store Schwarzians; "
						+ "call 'set_range' using current packing if "
						+ "necessary"));
		cmdStruct.add(new CmdStruct("get",
				"{v w ..}",null,"Compute Schwarzians "
						+ "for designated edges"));
		cmdStruct.add(new CmdStruct("get_tree",
				null,null,"copy spanning tree into 'glist'"));
		cmdStruct.add(new CmdStruct("s_load",
				"<filename>",null,"Read Schwarzians "
						+ "from a file into 'rangeTri'"));
		cmdStruct.add(new CmdStruct("s_map",
				"-q{q} [{v w ..}]",null,"Use Schwarzians stored in "
						+ "'rangeTri' to successively insert radii "
						+ "and centers into 'rangeTri'. Edges {v,w} "
						+ "are given: default to 'layOrder'. New "
						+ "faces are laid out in q canvas. "));
		cmdStruct.add(new CmdStruct("s_out",
				"-[fa] <filename>",null,"Write Schwarzians to "
						+ "file: f g  <sch_coeff>"));
		cmdStruct.add(new CmdStruct("s_set",
				"{v w s ...}",null,"Set Schwarzians to 's' for "
						+ "oriented edges (v,w) and (w,v)"));
		cmdStruct.add(new CmdStruct("s_inc",
				"x {f g ..}",null,"Increment schwarzian by factor "
						+ "x for given dual edges"));
		cmdStruct.add(new CmdStruct("output",
				"-[fa] <filename>",null,"Write dM data to matlab-style "
						+ "file: 'v w tangPt dM'"));
		cmdStruct.add(new CmdStruct("field",
				null,null,"display vector field of Schwarzian derivatives, "
						+ "color/length encoded."));
		cmdStruct.add(new CmdStruct("s_layout",
				"[-d{flags}] {v w ...}",null,"Us the schwarzians stored "
						+ "with the domain packing to compute/layout "
						+ "faces for edge {v,w}, placing data in the "
						+ "domain's 'TriAspect's."));
		cmdStruct.add(new CmdStruct("ratio_color",
				null,null,"Color domain interior edges based on ratio "
						+ "s'/s, range schwarzian/domain schwarzian"));
	}
}
