package schwarzWork;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.MiscException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.DoubleLink;
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

/** 
 * This is code for exploring discrete Schwarzian derivatives.
 * One goal is to handle packings on the sphere, another is to
 * study projective structures on more general Riemann surfaces. 
 * Motivated by Gerald Orick's thesis, I am pursuing a slightly
 * modified version of his "directed Mobius edge derivative"
 * along with an "intrinsic" schwarzian associated with maps
 * from a standardized "base equilateral". 
 * 
 * DISCRETE SCHWARZIAN DERIVATIVE:
 * The concern is with maps between tangency circle packings 
 * sharing the same combinatorics. A circle packing "patch" 
 * refers to two faces sharing an edge e, thus sharing two
 * neighboring circles Cv, Cw. Given map phi:P --> P' 
 * between circle packings, for each face f and f'=phi(f), M(f) 
 * is the "face Mobius map" of f onto f'. This identifies 
 * corresponding tangency points and hence maps one triple of
 * circles to the other. (These face mappings were 
 * pieced together, along with extensions to circle interiors,
 * by He/Schramm to define point mappings between circle packing
 * carriers and was used by them to prove convergence results.) 
 * 
 * **** CONVENTION: Given directed edge {v,w} shared by 
 * faces f and g, we reverse Orick's convention and let f 
 * be the face on the LEFT of {v,w}, g the face on the RIGHT. 
 * Note that the ordered pair {f,g} is the "dual" edge to 
 * {v,w} and geometrically ends up clockwise perpendicular 
 * to {v,w}, so it points out of f.
 * 
 * For each interior edge e sharing faces f and g, define 
 * the "directed Mobius edge derivative" by dM(e)=M(g)^{-1}.M(f). 
 * This is invariant: dM(e) is unchanged if we replace P' by 
 * P" = m(P'), m a Mobius map. 
 * 
 * The Mobius maps dM(e) fix the tangency point within e 
 * and are always parabolic, and we normalize so 
 * trace(dM(e))=2 and det(dM(e))=1. If dM(e) ~ [a b ; c d], 
 * then the entry c is the "(complex discrete) Schwarzian" 
 * as defined by Orick. More precisely, if z is the tangency 
 * point in the edge e then dM(e) has the form
 *      dM(e)= [1 + c*z,-c*z^2; c, 1-c*z]
 * Moreover, c = s*i*conj(delta), where delta = exp{i theta} 
 * gives the direction of the directed edge e and s is some 
 * real scalar. If eta is the normal to e directed outward 
 * from f (so eta=-i*delta), then c=s*conj(eta). Note that 
 * the complex schwarzian for -e is -c. (This real number s 
 * is distinct from the intrinsic schwarzian discussed below.)

 * INTRINSIC SCHWARZIANS:
 * In contrast to complex schwarzians related to mappings 
 * between TWO packings, I am introducing intrinsic schwarzians 
 * associated with edges of a SINGLE tangency packing P. 
 * The "schwarzian" element stored with an edge of P is this 
 * intrinsic schwarzian, a real number defined as above for 
 * maps from a "base equilateral". This base equilateral is 
 * centered at the origin and has its edge midpoints (tangency 
 * points of its circles) at the cube roots of unity. One of 
 * our main goals is to understand conditions on those 
 * assignments of schwarzians which correspond to circle 
 * packings. Also, to understand the relationship between 
 * the intrinsic schwarzians of P and P' when considering 
 * a mapping. See 'dcel.schwarzian.java'. 
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
	
	// for 'flower' mode:
	public int flowerDegree; // 0 if not in flower mode
	public int[] petalticks; // which petals are in place
	public HalfEdge[] spokes; // convenience 

	
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
			extenderPD.packExtensions.add(this);
		}
		// default: look at 'set_domain' call
		try {
			domainTri=PackData.getTriAspects(extenderPD);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("failed to set up domain triangle data");
		}
		
		Schwarzian.comp_schwarz(extenderPD,new HalfLink(extenderPD,"i"));
		
		rangeTri=null;
		rangePackNum=-1;
		rangeHes=extenderPD.hes;
		
		// are we working in flower mode?
		flowerDegree=0;
	}
	
	/**
	 * This is where the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// NOTE: separate command once in "flower" mode
		if (flowerDegree>0 && cmd.startsWith("sch") &&
				cmd.length()==3) {
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
						double hold=extenderPD.getSchwarzian(spokes[1]);
						for (int j=1;j<fd;j++) {
							extenderPD.setSchwarzian(spokes[j],
								(Double)(extenderPD.getSchwarzian(spokes[j+1])));
						}
						extenderPD.setSchwarzian(spokes[fd],(Double)hold);
						hit++;
						break;
					}
					case 'f': // full: recompute all using initial 
						// intrinsic schwarzians {s_1,...s_{fd-3}}.
						// schwarzians, and computing the final 3.
					{
						double[] uzian=new double[fd+1];
						for (int j=1;j<=fd;j++)
							uzian[j]=1.0-extenderPD.getSchwarzian(spokes[j]);
						SchFlowerData sfd=null;
						try {
							sfd=new SchFlowerData(uzian);
						} catch (DataException dex) {
							Oops(dex.getMessage());
							sfd=null;
							return hit;
						}

						// store radii/centers
						for (int j=1;j<=flowerDegree-1;j++) {
							extenderPD.packDCEL.setVertCenter(j,
									new Complex(sfd.t[j],-sfd.radius[j]));
								extenderPD.packDCEL.setVertRadii(j,sfd.radius[j]);
								hit++;
//							if (sfd.radius[j]>1.0)
//								msg("Petal "+j+" has radius > 1; rad = "+sfd.radius[j]);
						}

						// store last 3 schwarzians, possibly new from sfd
						for (int j=0;j<3;j++) {
							int k=flowerDegree-j;
							extenderPD.setSchwarzian(spokes[k],1.0-sfd.uzian[k]);
						}
						cpCommand(extenderPD,"disp -wr");
						break;
					}
					case 'e': // draw eucl flower based on schwarzians  
					{
						CircleSimple cs=new CircleSimple();
						Complex err=new Complex(0.0);
						PackData newP=PackData.schFlowerErr(
							extenderPD.packDCEL.vertices[extenderPD.nodeCount], err, cs);
						PackDCEL ncel=newP.packDCEL;
						PackDCEL dcel=extenderPD.packDCEL;
						int n=extenderPD.nodeCount-1;
						for (int v=1;v<=extenderPD.nodeCount;v++) {
							Vertex vert=ncel.vertices[v];
							Complex cent=vert.center;
							double rad=vert.rad;
							dcel.setVertCenter(v,cent);
							dcel.setVertRadii(v, rad);
						}
						
						// get two positions for c_1
						int nc=extenderPD.nodeCount;
						cpCommand(extenderPD,"Disp -w -c -cfc5 1 -ec5t3 1 "+nc);
						dcel.setVertCenter(1,cs.center);
						dcel.setVertRadii(1, cs.rad);
						cpCommand(extenderPD,"disp -cfc195 1 -ec195t2 1 "+nc);
						msg("Flower error:  radius "+String.format("%.6e",err.x)+";   angle "+String.format("%.6e",err.y));
						hit++;
						break;
					}
					case 's': // read/store schwarzians; see also 'u'
					{
						items.remove(0);
						DoubleLink dlink=new DoubleLink(extenderPD,items);
						if (dlink.size()>flowerDegree) {
							Oops("Too many s-variables");
						}
						Iterator<Double> dls=dlink.iterator();
						int tick=1;
						while(dls.hasNext()) {
							extenderPD.setSchwarzian(spokes[tick],dls.next());
							tick++;
							hit++;
						}
						
						// recompute
						double[] uzian=new double[fd+1];
						for (int j=1;j<=fd;j++)
							uzian[j]=1.0-extenderPD.getSchwarzian(spokes[j]);
						SchFlowerData sfd=null;
						try {
							sfd=new SchFlowerData(uzian);
						} catch (DataException dex) {
							Oops(dex.getMessage());
							sfd=null;
							return hit;
						}
						// store schwarzians of last 3
						for (int j=fd-2;j<=fd;j++)
							extenderPD.setSchwarzian(spokes[j],1.0-uzian[j]);
						
						// update sliders
						if (extenderPD.schFlowerSliders!=null) {
							extenderPD.schFlowerSliders.downloadData();
						}
						break;
					}
					case 'u': // read vector of u-variable for uzians; 
							  // see also 's'
					{
						items.remove(0);
						DoubleLink dlink=new DoubleLink(extenderPD,items);

						if (dlink.size()>flowerDegree) {
							Oops("Too many u-variables");
						}
						Iterator<Double> dls=dlink.iterator();
						int tick=1;
						while(dls.hasNext()) {
							extenderPD.setSchwarzian(spokes[tick],1.0-dls.next());
							tick++;
							hit++;
						}
						
						// recompute
						double[] uzian=new double[fd+1];
						for (int j=1;j<=fd;j++)
							uzian[j]=1.0-extenderPD.getSchwarzian(spokes[j]);
						SchFlowerData sfd=null;
						try {
							sfd=new SchFlowerData(uzian);
						} catch (DataException dex) {
							Oops(dex.getMessage());
							sfd=null;
							return hit;
						}
						// store schwarzians of last 3
						for (int j=fd-2;j<=fd;j++)
							extenderPD.setSchwarzian(spokes[j],1.0-uzian[j]);
						
						// update sliders
						if (extenderPD.schFlowerSliders!=null) {
							extenderPD.schFlowerSliders.downloadData();
						}
						break;
					}
					case 'r': // reset, showing just half planes and c1
					{
						petalticks=new int[flowerDegree+1]; // indexed from 1
						petalticks[1]=1;
						// set rest along real axis, radius .025
						for (int v=2;v<flowerDegree;v++) {
							double x=(v-2)*.2;
							extenderPD.packDCEL.setVertCenter(v, new Complex(x));
							extenderPD.packDCEL.setVertRadii(v, .025);
						}
						cpCommand(extenderPD,"disp -wr");
						hit++;
						break;
					}
					case 'n': // draw next {n} petals 
					{
						// Find the 'indx' of petal to compute next 
						int vindx=0;
						int tick=2;
						petalticks[1]=1;
						while (tick<flowerDegree) {
							if(petalticks[tick]==0) { // not yet computed
								vindx=tick;
								break;
							}
							tick++;
						}
						if (vindx==0) { // didn't find petal to place
							Oops("sch -n: all petals already laid out");
						}
						 
						// Is a schwarzian provided?
						items.remove(0);
						if(items.size()>0) {
							try {
								Double newSch=Double.parseDouble(items.get(0));
								extenderPD.setSchwarzian(spokes[vindx-1],newSch);
							} catch(Exception ex) {
								Oops("failed to get schwarzian");
							}
						}
						
						// compute new petal c_{indx}. There are
						//   several s situations for the results of
						//   the current petal; these are coded in
						//   'petalticks':
						//   + 0: not yet computed
						//   + 1: computed in typical way, sit2 or sit3
						//   + 2: displacement negative, but finite
						//   + 3: is a halfplane
						double u=1.0-extenderPD.getSchwarzian(spokes[vindx-1]); // uzian
						if (vindx==2) { // initial case for c_2
							double sit2[]=SchFlowerData.Sit2(u);
							double r=1/(sit2[1]*sit2[1]);
							extenderPD.packDCEL.setVertCenter(vindx,
									new Complex(sit2[0],-r));
							extenderPD.packDCEL.setVertRadii(vindx,r);
							petalticks[vindx]=1;
							hit++;
						}
						else if (vindx==(flowerDegree-1)) {
							extenderPD.packDCEL.setVertRadii(vindx,1.0);
							double displ=2.0*Math.sqrt(extenderPD.packDCEL.vertices[vindx-1].rad);
							double x=extenderPD.packDCEL.vertices[vindx-1].center.x;
							extenderPD.packDCEL.setVertCenter(vindx,new Complex(x+displ,-1.0));
							hit++;
						}

						else {
							double t=extenderPD.packDCEL.vertices[vindx-1].center.x;
							double isqr=Math.sqrt(1/extenderPD.packDCEL.vertices[vindx-2].rad);
							double isqR=Math.sqrt(1/extenderPD.packDCEL.vertices[vindx-1].rad);
							// need to check if nghb is in branch situation
							
							// if petal vindx-2 was a halfplane; use Sit1 scaled by R
							if (vindx>3 && petalticks[vindx-2]==3) {
								double R=extenderPD.packDCEL.vertices[vindx-1].rad;
								double[] sit1=SchFlowerData.Sit1(u);
								double dspmt=sit1[0]*R;
								double r=R;
								extenderPD.packDCEL.setVertCenter(vindx,
										new Complex(t+dspmt,-r));
								extenderPD.packDCEL.setVertRadii(vindx,r);
								petalticks[vindx]=1;
							}
							// else if previous was negative displacement (branching)
							else if (petalticks[vindx-1]>1) {
								if (petalticks[vindx-1]==3 && vindx==(flowerDegree-1)) // penultimate circle can not be half plane
									break; 
								isqR *=-1.0;
								double[] sit4=SchFlowerData.Sit4(u,isqr,isqR);
								double dspmt=t+sit4[0];
								double r=1/(sit4[1]*sit4[1]);
								extenderPD.packDCEL.setVertCenter(vindx,
										new Complex(dspmt,-r));
								extenderPD.packDCEL.setVertRadii(vindx, r);
								// Note: sit4[1] will be negative
								petalticks[vindx]=1;
								hit++;
							}
							else {
								double[] sit3=SchFlowerData.Sit3(u, isqr, isqR); 
								double r=1/(sit3[1]*sit3[1]);
								// Is this essentially a half plane?
								if (sit3[0]<-500) {
									double R=20000.0-2*extenderPD.packDCEL.vertices[vindx-1].rad;
									extenderPD.packDCEL.setVertCenter(vindx,
										new Complex(t-.1,-20000.0)); // left of previous t
									extenderPD.packDCEL.setVertRadii(vindx,R);
									petalticks[vindx]=3;
								}
								else if (sit3[0]>500) {
									double R=20000.0-2*extenderPD.packDCEL.vertices[vindx-1].rad;
									extenderPD.packDCEL.setVertCenter(vindx,
										new Complex(t+.1,-20000.0)); // right of previous t
									extenderPD.packDCEL.setVertRadii(vindx,R);
									petalticks[vindx]=3;
								}
								else {
									extenderPD.packDCEL.setVertCenter(vindx,new Complex(t+sit3[0],-r));
									extenderPD.packDCEL.setVertRadii(vindx,r);
									if (sit3[0]<0)
										petalticks[vindx]=2;
									else 
										petalticks[vindx]=1;
								}
							}
							hit++;
						}
						
						// plot this new circle, Disp for circles up to this one
						if (hit>0) {
							cpCommand(extenderPD,"disp -wr");
						}
						break;
					}
					case 'm': // Place last petal based on s_n
					{
						int vertindx=flowerDegree-1;
						double[] tn=SchFlowerData.Sit1(1.0-extenderPD.getSchwarzian(spokes[fd]));
						extenderPD.packDCEL.setVertCenter(vertindx,new Complex(tn[0],-1.0));
						extenderPD.packDCEL.setVertRadii(vertindx,1.0);
						
						// display in red
						cpCommand(extenderPD,"disp -cc190 "+ vertindx);
						hit++;
						break;
					}
					case 'v': // update sliders
					{
						if (extenderPD.schFlowerSliders!=null) {
							extenderPD.schFlowerSliders.downloadData();
						}
						hit++;
						break;
					}
					case 'l': // recompute and send schwarzians, tangencies,
						// and radii to "Messages" in copyable form.
						// also store schwarzians in 'Dlink' for possible
						// use.
					{
						CPBase.Dlink=new DoubleLink();
						double[] uzian=new double[fd+1];
						for (int j=1;j<=fd;j++) {
							uzian[j]=1.0-extenderPD.getSchwarzian(spokes[j]);
							CPBase.Dlink.add(uzian[j]);
						}
						
						// recompute data
						SchFlowerData sfd=null;
						try {
							sfd=new SchFlowerData(uzian);
						} catch (DataException dex) {
							Oops(dex.getMessage());
							sfd=null;
							return hit;
						}

						StringBuilder strbld=new StringBuilder();
						for (int v=1;v<=fd;v++) 
							strbld.append((1.0-sfd.uzian[v])+"  ");
						msg("schwarzians: ");
						msg(strbld.toString());
						strbld=new StringBuilder();
						for (int v=1;v<fd;v++)
							strbld.append(sfd.t[v]+"  ");
						msg("tangencies: ");
						msg(strbld.toString());
						strbld=new StringBuilder();
						for (int v=1;v<fd;v++)
							strbld.append(sfd.radius[v]+"  ");
						msg("radii: ");
						msg(strbld.toString());
						hit++;
						break;
					}
					case 'q': // construct eucl packing in px
					{
						// choose packing, default to next pack, mod 3
						int qnum=(extenderPD.packNum+1)%CPBase.NUM_PACKS;
						if (str.length()>2) {
							String substr=str.substring(2).trim();
							if (substr.length()==0)
								Oops("|sm| sch -q{x} needs packnumber {x}");
							try {
								int q=Integer.parseInt(substr);
								if (q>=0 && q<CPBase.NUM_PACKS &&
										q!=extenderPD.packNum)
									qnum=q;
							} catch (Exception iex) {}
						}
						StringBuilder strbld=new StringBuilder("create seed "+
								flowerDegree+" -s ");
						for (int j=1;j<=flowerDegree;j++)
							strbld.append(" "+extenderPD.getSchwarzian(spokes[j]));
						cpCommand(CPBase.packings[qnum],
							strbld.toString());
						cpCommand(CPBase.packings[qnum],"Disp -w -cn -cf A");
						hit++;
						break;
					}
					case 'x': // exit flower mode
					{
						flowerDegree=0;
						spokes=null;
						petalticks=null;
						extenderPD.schFlowerSliders=null;
						hit++;
						break;
					}
					case 't': // test: print out a bunch of info
					{
						// uzians indexed from 1 (so first entry ignored)
						double[] uz=new double[flowerDegree+1];
						for (int j=1;j<=flowerDegree;j++) 
							uz[j]=1.0-spokes[j].getSchwarzian();
						Boolean allPos=Boolean.valueOf(true);
						ArrayList<Double> conarray=SchFlowerData.constraints(uz,allPos);
						
						msg("Uzians: ");
						StringBuilder strbld=new StringBuilder("uz=[");
						for (int j=1;j<flowerDegree;j++) 
							strbld.append(uz[j]+" ");
						strbld.append(", "+uz[flowerDegree]+"];");
						msg(strbld.toString());
						
						msg("Constraints: ");
						strbld=new StringBuilder();
						Iterator<Double> cis=conarray.iterator();
						cis.next();
						while (cis.hasNext()) 
							strbld.append((double)cis.next()+", ");
						msg(strbld.toString());
						
						msg("Radii: r_1,...,r_{n-1}");
						ArrayList<Double> radii=new ArrayList<Double>();
						cis=conarray.iterator();
//						cis.next(); // shuck the first
						while (cis.hasNext()) {
							double con=cis.next();
							radii.add(1/(con*con));
						}
						radii.remove(radii.size()-1);
						strbld=new StringBuilder();
						Iterator<Double> ris=radii.iterator();
						ris.next();
						while (ris.hasNext())
							strbld.append(ris.next()+", ");
						msg(strbld.toString());
						
						msg("tangency pts: j=1,...,n-2: ");
						strbld=new StringBuilder();
						double tang=0.0;
						strbld.append("0.0, ");
						for (int j=1;j<flowerDegree-1;j++) {
							tang +=2.0/(conarray.get(j)*conarray.get(j+1));
							strbld.append(tang+", ");
						}
						msg(strbld.toString());
						
						// do we get uzian n-2 right?
						double comp=(1.0+conarray.get(flowerDegree-3))/(Math.sqrt(3.0)*conarray.get(flowerDegree-2));
						msg("Initial u_{n-2} = "+uz[flowerDegree-2]+
								"; computed by formula = "+comp);
						return 1;
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
   		  	extenderPD=CirclePack.cpb.swapPackData(newData,extenderPD.packNum,true);
   		  	extenderPD.intNodeCount=1;
   		  	extenderPD.swap_nodes(1,M);
   		  	for (int j=1;j<flowerDegree;j++)
   		  		extenderPD.swap_nodes(j,j+1);
   		  	
   		  	// set up 'spokes', indexed from 1
   		  	spokes=new HalfEdge[flowerDegree+1];
   		  	for (int j=1;j<M;j++) {
   		  		spokes[j]=extenderPD.packDCEL.findHalfEdge(new EdgeSimple(M,j));
   		  	}
   		  	
   		  	// compute/store initial schwarzians
   			Schwarzian.comp_schwarz(extenderPD,new HalfLink(extenderPD,"i"));
   		  	
   		  	cpCommand(extenderPD,"disp -w");
			petalticks=new int[M]; // indexed from 1
			
			// put in normalized setup; 
			//   * center is upper half plane
			//   * last petal, c_n is half plane {y<=-2*i}
			//   * first petal, c_1 has radius 1, center -i
			//   * rest small to start -- spread out along x-axis
			extenderPD.packDCEL.setVertCenter(M,
					new Complex(0.0,20000.0));
			extenderPD.packDCEL.setVertCenter(flowerDegree,
					new Complex(0.0,-20000.0));
			extenderPD.packDCEL.setVertRadii(M, 20000.0);
			extenderPD.packDCEL.setVertRadii(flowerDegree,19998.0);
			extenderPD.packDCEL.setVertCenter(1,
					new Complex(0.0,-1.0));
			extenderPD.packDCEL.setVertRadii(1,1.0);
			petalticks[1]=1;
			
			// set rest to start with centers on x-axis, radius .025
			for (int v=2;v<flowerDegree;v++) {
				double x=(v-1)*.2;
				extenderPD.packDCEL.setVertCenter(v, new Complex(x));
				extenderPD.packDCEL.setVertRadii(v, .025);
			}
			
			// color
			extenderPD.setCircleColor(M,ColorUtil.cloneMe(ColorUtil.coLor(80)));
			extenderPD.setCircleColor(flowerDegree,ColorUtil.cloneMe(ColorUtil.coLor(100)));
			extenderPD.setCircleColor(1,ColorUtil.cloneMe(ColorUtil.coLor(205)));
			
			// set colors
			cpCommand(extenderPD,"color -c s a");
			cpCommand(extenderPD,"color -c 80 M");
			cpCommand(extenderPD,"color -c 100 "+flowerDegree+" 1");

			// initial display
			cpCommand(extenderPD,"set_screen -b -2.4 -4.77 5.46 3.1");
			cpCommand(extenderPD,"Disp -w -c "+flowerDegree+" 1 -cf a(1,100)");
			
			return 1;
		} // done with flower construction and now in flower mode
		
		// ======= s_layout =============
		
		else if (cmd.startsWith("s_lay")) {

			// copy from "s_map"

			PackData qData=extenderPD;
			DispFlags cirFlags=null;
			DispFlags faceFlags=null;
			int qnum=extenderPD.packNum; // default to this packing itself
			
			// normally there's a -q{} flag; it has to be first
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				if (!items.isEmpty() &&	items.get(0).startsWith("-q")) {
					if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
						items.remove(0);
						flagSegs.remove(0);
						
						// minimal compatibility check or hyp check:
						//   default is copy of packData
						if (qnum!=extenderPD.packNum) {
							qData=CPBase.cpDrawing[qnum].getPackData();
							if ((qData.faceCount!=extenderPD.faceCount) || 
									qData.hes<0) {
								CirclePack.cpb.msg("Copy domain packing "+
									"into target, p"+qData.packNum+
									", with spherical geometry "+
									"because original target does "+
									"not match nodeCount or is hyperbolic.");
								cpCommand(extenderPD,"copy "+qnum);
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
					HalfLink hlink=HalfLink.glist_to_hlink(extenderPD,items);
					if (hlink.size()>0)
						layOrder=hlink;
				}
			} // done with flags
			
			// default to 'layoutOrder'
			if (layOrder==null || layOrder.size()==0)
				layOrder=extenderPD.packDCEL.layoutOrder;
			
			// keeping track of processed faces
			int[] hitfaces=new int[extenderPD.faceCount+1];
			
			// minimal check that radii/centers are in 'rangeTri' 
			//   (in geometry of 'qData')
			if (rangeTri==null || rangeTri.length!=extenderPD.faceCount+1 || 
					rangeTri[1].hes!=qData.hes)
				rangeTri=PackData.getTriAspects(qData);
			
			// Do we need to place the first face?
			// TODO: for now, always layout first edge
			HalfEdge leadedge=layOrder.get(0);
			extenderPD.packDCEL.placeFirstEdge(leadedge);
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
		    	try {
		    		ans= workshops.LayoutShop.schwPropogate(rangeTri[f],rangeTri[g],
		    				he.twin,s,mode);
		    	} catch (Exception ex) {
		    		throw new DataException(ex.getMessage());
		    	}
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
		
		// ======= open schFlowerSlider ============
		else if (cmd.startsWith("schS")) {
			
			// in 'flower' mode, we display intrinsic
			//    schwarzians in a slider widget
			if (this.flowerDegree==0) {
				Oops("Schwarzian sliders implemented only for flowers");
			}
		
			Double[] tmpsch=new Double[flowerDegree+1];
			for (int j=1;j<=flowerDegree;j++)
				tmpsch[j]=(Double)extenderPD.getSchwarzian(spokes[j]);
			extenderPD.schFlowerSliders=
				new SchFlowerSliders(extenderPD,tmpsch);
			if (extenderPD.schFlowerSliders==null)
				return 0;
			extenderPD.schFlowerSliders.setVisible(true);
			return 1;
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
					if (qnum!=extenderPD.packNum) {
						qData=CPBase.cpDrawing[qnum].getPackData();
						if ((qData.faceCount!=extenderPD.faceCount) || 
								qData.hes<0) {
							CirclePack.cpb.msg("Copy domain packing "+
								"into target, p"+qData.packNum+
								", because current packing does "+
								"not match nodeCount. "+
								"Convert to spherical.");
							cpCommand(extenderPD,"copy "+qnum);
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
				layOrder=HalfLink.glist_to_hlink(extenderPD,items);
			if (layOrder==null)
				layOrder=extenderPD.packDCEL.layoutOrder;
			
			// Do we need to place the first face? Only if
			// TODO: for now always lay out first face
			HalfEdge he=layOrder.get(0);
			int baseface=he.face.faceIndx;

			// keep track of how many times a center is reset, error
			int []verthits=new int[extenderPD.nodeCount+1];
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
				int indx=(extenderPD.face_nghb(f,g)+2)%3;
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
			HalfLink hlink=HalfLink.glist_to_hlink(extenderPD,items);
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
            	" are stored in 'domainTri' of p"+extenderPD.packNum);
            return count;
		}
		
		// ======= s_map =============
		else if (cmd.startsWith("s_map")) {
			
			boolean debug=false; // debug=true;

			PackData qData=extenderPD;
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
				int qnum=extenderPD.packNum;
				if ((qnum=StringUtil.qFlagParse(items.get(0)))>=0) {
					items.remove(0);
					// Want map to develop in specified packing (versus 
					//   back in packData). Minimal check that packing is 
					//   same, not hyp; else copy packData into qdata.
					if (qnum!=extenderPD.packNum) {
						qData=CPBase.cpDrawing[qnum].getPackData();
						if ((qData.faceCount!=extenderPD.faceCount) || 
								qData.hes<0) {
							CirclePack.cpb.msg("Copy domain packing into "+
								"target, p"+qData.packNum+
								", with spherical "+
								"geometry because original target does "+
								"not match nodeCount or is hyperbolic.");
							cpCommand(extenderPD,"copy "+qnum);
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
			int []hitfaces=new int[extenderPD.faceCount+1];
			
			// ensure radii/centers are in 'rangeTri' 
			//    (in geometry of 'qData')
			if (rangeTri==null || rangeTri.length!=extenderPD.faceCount+1 || 
					rangeTri[1].hes!=qData.hes)
				rangeTri=PackData.getTriAspects(qData);
			
			// Do we need to place the first face?
			HalfEdge he=layOrder.get(0);
			int baseface=he.face.faceIndx;
			int dom_hes=extenderPD.hes;
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
						sC=HyperbolicMath.h_to_e_data(extenderPD.getCenter(v),
								extenderPD.getRadius(v));
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				else if (dom_hes<0 && rangeHes>0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC = HyperbolicMath.h_to_e_data(extenderPD.getCenter(v),
								extenderPD.getRadius(v));
						sC = SphericalMath.e_to_s_data(sC.center, sC.rad);
						mytri.setCenter(sC.center,j);
						mytri.setRadius(sC.rad,j);
					}
				}
				else if (dom_hes==0 && rangeHes>0) {
					for (int j=0;j<3;j++) {
						int v=mytri.vert[j];
						sC=SphericalMath.e_to_s_data(extenderPD.getCenter(v),
								extenderPD.getRadius(v));
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
						sC = SphericalMath.s_to_e_data(extenderPD.getCenter(v),
								extenderPD.getRadius(v));
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
					extenderPD.cpDrawing.drawFace(domainTri[baseface].getCenter(0),
							domainTri[baseface].getCenter(1),
							domainTri[baseface].getCenter(2),
							domainTri[baseface].getRadius(0),
							domainTri[baseface].getRadius(1),
							domainTri[baseface].getRadius(2),dispFlags);
					extenderPD.cpDrawing.repaint();
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
				int gfindx=extenderPD.face_nghb(f,g);
				
				// using 'alignMe'
				DcelFace gface=extenderPD.packDCEL.faces[g];
				DcelFace fface=extenderPD.packDCEL.faces[f];
				HalfEdge hedge=gface.faceNghb(fface);
				int mode=1; // use 'radii'
				Mobius aligng;
				if ((aligng=domainTri[g].alignMe(domainTri[f],hedge,mode))==null)
					aligng=new Mobius(); // identity
				domainTri[g].mobiusMe(aligng);
				domainTri[g].setTanPts();					

				// find rangeTri[g]
				TriAspect ftri=rangeTri[f];
				int fgindx=extenderPD.face_nghb(g, f);
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
						extenderPD.hes,rangeHes);
				Mobius mobg=(Mobius)fmob.rmultby(
						domainTri[f].MobDeriv[fgindx].inverse());
				
				// Either way, apply to domainTri[g] to get last 
				//   circle for rangeTri[g]
				Mobius.mobius_of_circle(mobg, extenderPD.hes,
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
			return extenderPD.hlist.abutMore(layOrder);
		}

		// ======= get ===============
		else if (cmd.startsWith("get")) {
			EdgeLink elink=new EdgeLink(extenderPD,flagSegs.get(0));
			if (elink==null || elink.size()==0)
				Oops("usage: get {v w ..}");
			int count=0;
			Iterator<EdgeSimple> elst=elink.iterator();
			while (elst.hasNext()) {
				EdgeSimple edge=elst.next();
				int v=edge.v;
				int w=edge.w;
				int f=extenderPD.face_right_of_edge(w,v);
				int g=extenderPD.face_right_of_edge(v,w);
				try {
					SchwarzData sD=Schwarzian.getSchData(
							domainTri[f],domainTri[g],
							rangeTri[f],rangeTri[g]);
					if (sD.flag!=0)
						CirclePack.cpb.errMsg("schData flag not zero.");

					// show data
					CirclePack.cpb.msg("SchData ("+v+","+w+")");
					Complex sm=sD.Schw_Deriv.times(sD.dmf_deriv);
					CirclePack.cpb.msg("  sd = "+sD.Schw_Deriv+"; sd*m'(1) = "+sm+"; coeff = "+sD.Schw_coeff);
					CirclePack.cpb.msg("  s's = "+sD.domain_schwarzian+",   "+
							sD.range_schwarzian);
					
					// check relation s'=s+sigma*m'(1), m is baseMobius of f
					Mobius f_mob=domainTri[f].setBaseMobius();
					int indx_f=domainTri[f].nghb_Tri(domainTri[g]);
					f_mob.a=f_mob.a.times(CPBase.omega3[indx_f]);
					f_mob.c=f_mob.c.times(CPBase.omega3[indx_f]);
					f_mob.normalize();
					Complex cd2=f_mob.c.add(f_mob.d);
					// this gives m'(1)=(c+d)^2
					Complex mu_f_deriv=new Complex(1.0).divide(cd2.times(cd2));
					Complex smp=sD.Schw_Deriv.times(mu_f_deriv);
					// look at s+sigma*m'(1)-s', is it zero?
					Complex expression=new Complex(sD.domain_schwarzian);
					expression=expression.add(smp);
					expression=expression.minus(new Complex(sD.range_schwarzian));
					CirclePack.cpb.msg("s+SD*m'(1)-s'  should be zero: "+expression);
					count=count+1;
				} catch(Exception ex) {
					Oops("failed to compute Schwarzian for edge <"+
							v+","+w+">");
				}
			} // end of while
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
			PackData pd=extenderPD;
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				int qnum=StringUtil.qItemParse(items);
				if (qnum>=0) { // CPBase.packings
					pd=CPBase.cpDrawing[qnum].getPackData();
					rangePackNum=pd.packNum;
				}
			}
			
			// initiate the TriAspects (or get from an extender)
			TriAspect[] ourTri;
			try {
				ourTri=PackData.getTriAspects(pd);
			} catch(Exception ex) {
				CirclePack.cpb.errMsg("failed to set triangle data");
				return 0;
			}
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
		// Compute Schwarzian derivatives for all faces f; 
		//   following our notation conventions, for each 
		//   positively oriented edge e of f, the derivative 
		//   is dM(e)= mob_g^{-1}(mob_f),g the face across e. 
		if (cmd.startsWith("go")) {
			
			boolean debug=false;
			
			Mobius []faceMobs=setFaceMobs();
			if (faceMobs==null) {
				CirclePack.cpb.errMsg("failed to set face Mobius maps");
				return 0;
			}
			
			for (int f=1;f<=extenderPD.faceCount;f++) {
				TriAspect myasp=domainTri[f];
				myasp.MobDeriv=new Mobius[3];
				myasp.schwarzian=new double[3];
				for (int j=0;j<3;j++) {
					int g=-1;
					int jf=j;
					int jf1=(jf+1)%3;
					int v=myasp.vert[jf];
					int w=myasp.vert[jf1];
					if ((g=extenderPD.face_right_of_edge(v,w))>=0) {
						try {
							// <v,w> has f on its left, g on its right
							int jg=domainTri[g].vertIndex(w);
							int jg1=(jg+1)%3;
							
							// Recall that the packings may not be planar, 
							//   e.g., packing of a torus with faces on
							//   opposite sides of a cut.
							//   Thus we want to align as geometrically
							//   neighboring faces before computing
							
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
								domainalign=Mobius.mob_MatchCircles(
										new CircleSimple(gvz,domainTri[g].getRadius(jg1)), 
										new CircleSimple(gwz, domainTri[g].getRadius(jg)),
										new CircleSimple(fvz,domainTri[f].getRadius(jf)), 
										new CircleSimple(fwz, domainTri[f].getRadius(jf1)),
										extenderPD.hes,extenderPD.hes);
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
								domainalign=Mobius.mob_MatchCircles(
										new CircleSimple(gvz,rangeTri[g].getRadius(jg1)), 
										new CircleSimple(gwz, rangeTri[g].getRadius(jg)),
										new CircleSimple(fvz,rangeTri[f].getRadius(jf)), 
										new CircleSimple(fwz, rangeTri[f].getRadius(jf1)),
										rangeHes,rangeHes);
							}
							
							Mobius newg=(Mobius)(faceMobs[g]).
									lmultby(galign).rmultby(domainalign.inverse());
							
							// By our notation conventions, dM(e)=mob_g^{-1}*mob_f
							myasp.MobDeriv[jf]=(Mobius)newg.inverse().
									rmultby(faceMobs[f]);
							
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
							if (extenderPD.hes>0) { // sph
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
							if (extenderPD.hes<0) { // hyp
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
					fp.write("CHECKCOUNT: "+extenderPD.nodeCount+"\n");
				fp.write("SCHWARZIAN COEFF: v w s   \n");
				for (int f=1;f<=extenderPD.faceCount;f++) {
					TriAspect ftri=domainTri[f];
					for (int j=0;j<3;j++) {
						int v=ftri.vert[j];
						int w=ftri.vert[(j+1)%3];
						if (v<w && (!extenderPD.isBdry(v) || 
								!extenderPD.isBdry(w))) { 
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
				for (int f=1;f<=extenderPD.faceCount;f++) {
					for (int j=0;j<3;j++) {
						int v=domainTri[f].vert[j];
						int w=domainTri[f].vert[(j+1)%3];
						if (v<w && (!extenderPD.isBdry(v) || 
								!extenderPD.isBdry(w))) {
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
	 * Return the schwarzian coefficient and tangency point 
	 * for edge <v,w>
	 * @param v int
	 * @param w int
	 * @return double[] = {coeff, x, y} 
	 */
	public double[] get_s_coeff(int v,int w) {
		HalfEdge he=extenderPD.packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (he==null)
			CirclePack.cpb.errMsg("something wrong with edge v or w");
		int f=he.face.faceIndx;
		if (domainTri[f].schwarzian==null || domainTri[f].tanPts==null)
			CirclePack.cpb.errMsg("'sch_coeffs' of 'tanPts' do not exist");
		double[] ans=new double[3];
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
		HalfEdge he=extenderPD.packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (he==null)
			return false;
		int f=he.face.faceIndx;
		int g=he.twin.face.faceIndx;
        if (f<=0 || g<=0)
        	return false;
        TriAspect ftri=domainTri[f];
        TriAspect gtri=domainTri[g];		
        int jf=ftri.vertIndex(v);
        if (ftri.schwarzian==null)
        	ftri.schwarzian=new double[3];
        ftri.schwarzian[jf]=schw;
        int jg=gtri.vertIndex(w);
        if (gtri.schwarzian==null)
        	gtri.schwarzian=new double[3];
        gtri.schwarzian[jg]=schw;
        return true;
	}
	
	/**
	 * If 'domainTri' and 'rangeTri' have data, then for 
	 * each face, use the tangency points to compute the 
	 * Mobius map. So faceMobs[f] maps domain face f to 
	 * range face f. 
	 * TODO: in process 1/2020 in converting to use 'baseMobius' instead.
	 * @return Mobius[] or null on error
	 */
	public Mobius[] setFaceMobs() {
		if (domainTri==null || rangeTri==null) {
			CirclePack.cpb.errMsg("'domainTri', 'rangeTri' must "+
					"have data to construct face Mobius maps");
			return null;
		}
		
		Mobius []faceMobs=new Mobius[extenderPD.faceCount+1];
		for (int f=1;f<=extenderPD.faceCount;f++) {
			try {
				faceMobs[f]=Mobius.mob_xyzXYZ(domainTri[f].tanPts[0],
					domainTri[f].tanPts[1],domainTri[f].tanPts[2],
					rangeTri[f].tanPts[0],rangeTri[f].tanPts[1],
					rangeTri[f].tanPts[2],extenderPD.hes,rangeHes);
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
		cmdStruct.add(new CmdStruct("flower","{n}",null,
				"go into 'flower' mode, degree n"));
		cmdStruct.add(new CmdStruct("sch","-[cflmnsx]",null,
				"c=cycle list,f=full layout,l=list schwarzians,"+
						"m=layout max petal,n=compute next,"+
						"q{p}=eucl layout in p,r=reset"+
						"s=set schwarzians,u=u-variables,x=exit 'flower' mode"));
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
