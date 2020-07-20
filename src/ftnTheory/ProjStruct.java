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
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.LayoutException;
import exceptions.ParserException;
import geometry.EuclMath;
import geometry.CircleSimple;
import input.CPFileManager;
import komplex.DualGraph;
import komplex.SideDescription;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.RedEdge;
import komplex.RedList;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import packing.RedChainer;
import util.BuildPacket;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;
/**
 * Projective structures on triangulated surfaces (such as
 * affine structures on tori) require new data structures.
 * Namely, computations are face-based rather than vertex-based.
 * This class is used to develop and test manipulations, and
 * provides static functionality for use by other classes.
 * 
 * The idea is to replace radii as the parameters, using 
 * localized geometry. In particular, to each face is attached
 * 'labels' r1:r2:r3 of radii (in eucl case, the radii are not
 * important, only their ratios). The angle sum at a vertex v is
 * obtained face-by-face. 
 * 
 * Initial scenario was for tori: First open as a combinatorial quadrilateral, 
 * noting identifications of top/bottom, left/right end vertices.
 * Assign radii, assuring that those on top are 'a' times those
 * at the bottom and those on the right are 'b' times those on
 * the left. Now record all face labels. Run an iterative 
 * routine to get angle sums 2pi at all vertices. With luck,
 * we get an affine torus, with the arguments of the side pairings 
 * determined by the process (so, 2 real parameters result in 2 complex
 * parameters).
 * 
 * As of summer 2020, I'm trying to generalize whatever I can to
 * accommodate surfaces other than tori, geometries other than euclidean,
 * and possibly branch points. I also hope to introduce schwarzians, 
 * though how to create and manipulate them are still open issues.
 *
 * kens
 */

public class ProjStruct extends PackExtender {
	public TriAspect []aspects;
	public GraphLink dTree; // dual spanning tree
	public static double TOLER=.00000001;
	public static double OKERR=.0000000001; 
	public static int PASSES=10000;
	public boolean debug=true;
	
	/* 8/2019. Plan to implement construction of portions of
	 * universal cover for a surface in attempt to compute 
	 * projective structures. 
	 * First example is to be 'Bolza' genus 2 surface based 
	 * on triangle group. However, I hope to set up dcel structure
	 * for more general cases.
	 */
	
	public dcel.Face FDcell=null; // this is the fundamental domain 
		
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
			dTree= DualGraph.easySpanner(packData,false);
			setupAspects();
			packData.packExtensions.add(this);
		}
	}
	
	/**
	 * create the 'aspects' array
	 */
	public void setupAspects() {
		for (int v=1;v<=packData.nodeCount;v++)
			packData.kData[v].utilFlag=0;
		NodeLink redVert=new NodeLink(packData,"r");
		if (redVert==null || redVert.size()==0) {
			msg("No list of red vertices was found");
			return;
		}
		
		// indicate red vertices with utilFlag=1
		Iterator<Integer> rit=redVert.iterator();
		while(rit.hasNext())
			packData.kData[rit.next()].utilFlag=1;
		
		// create vector of 'TriAspect's, one for each face
		aspects=new TriAspect[packData.faceCount+1];
		for (int f=1;f<=packData.faceCount;f++) {
			aspects[f]=new TriAspect(packData.hes);
		}
		
		// those for redChain faces, get a redList pointer
		RedList rtrace=(RedList)packData.redChain;
		boolean done=false;
		while (rtrace!=(RedList)packData.redChain || !done) {
			done=true;
			aspects[rtrace.face].redList=rtrace;
			rtrace=rtrace.next;
		}
		
		// initiate aspects
		for (int f=1;f<=packData.faceCount;f++) {
			TriAspect tas=aspects[f];
			tas.face=f;
			for (int j=0;j<3;j++) {
				int v=packData.faces[f].vert[j];
				tas.vert[j]=v;
				// set 'labels'
				tas.labels[j]=packData.rData[v].rad;
				// set 'centers'
				tas.setCenter(new Complex(packData.rData[v].center),j);
				if (packData.kData[v].utilFlag==1)
					tas.redFlags[j]=true;
				else tas.redFlags[j]=false;
			}
			// set 'sides' from 'center's
			tas.centers2Sides();
		}
	}
	
	/** 
	 * Euclidean iterative adjustment routines patterned after 'oldReliable' 
	 * euclidean riffle routines, but now computation are done to local
	 * labels face-by-face. For each vertex, a factor t>0 is computed and
	 * the local label at 'v' is multiplied by t in every face containing v.
	 * There are two goals with this same process, differing only in the
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
		int []inDex =new int[p.nodeCount+1];
		// only verts with aim>0 for mode 1 or interior for mode 2 and in the list
		for (int vv=1;vv<=p.nodeCount;vv++) {
			if (((mode==1 && p.rData[vv].aim>0) || (mode==2 && p.kData[vv].bdryFlag==0)) 
					&& (vlist==null || vlist.contains(Integer.valueOf(vv)))) {
				inDex[aimNum]=vv;
				aimNum++;
			}
		}
		if (aimNum==0) return -1; // nothing to repack
	      
	    // compute initial errors and set cutoff value
	    double accum=0.0;
	    for (int j=0;j<aimNum;j++) {
	    	v=inDex[j];
	    	if (mode==1)  // mode=1, default
	    		accum += Math.abs(angSumTri(p,v,1.0,aspts)[0]-p.rData[v].aim);
	    	else if (mode==2) 
	    		accum += Math.abs(skewTri(p,v,1.0,aspts)[0]);
	    }
	    double recip=.333333/aimNum;
	    double cut=accum*recip;
	    if (cut<TOLER) return 1;

	    // now cycle through adjustments --- riffle
	    while ((cut > TOLER && count<passes)) {
	    	double verr=0.0;
	    	try {
	    		if(debug) System.out.println(Math.log(Math.abs(
	    			edgeRatioError(p,aspts,new EdgeSimple(17,24)))));}
	    	catch (Exception ex){}
	    	for (int j=0;j<aimNum;j++) {
	    		v=inDex[j];
	    		double vAim=p.rData[v].aim;
	    		  
	    		// find/apply factor to labels at 'v' if error is bad enough to riffle
	    		if (mode==1)  // mode=1, default
	    			verr = Math.abs(angSumTri(p,v,1.0,aspts)[0]-p.rData[v].aim);
	    		else if (mode==2) 
	    			verr = Math.abs(skewTri(p,v,1.0,aspts)[0]);
	    		if (Math.abs(verr)>cut) {
	    			double []valder=new double[2];
	    			if (mode==1) {
	    				valder=angSumTri(p,v,1.0,aspts);
	    				valder[0] -= vAim;
	    			}
	    			else if (mode==2) {
	    				valder=skewTri(p,v,1.0,aspts);
	    			}
	    			
	    			// start error
    				if (debug) {
    					System.err.println(" v="+v+", start error = "+Math.abs(valder[0]));
    				}
    				  
	    			// use one Newton step, but restrict to [.5,2].
	    			double factor = 1.0 - valder[0]/valder[1];
	    			if (factor<.5)
	    				factor=.5;
	    			if (factor>2.0)
	    				factor=2.0;
    				  
	    			// make the change
	    			adjustRadii(p,v,factor,aspts);
	    			
	    			// new error
    				if (debug) {
    					double []vd=new double[2];
    	    			if (mode==1) {
    	    				vd=angSumTri(p,v,1.0,aspts);
    	    				vd[0] -= vAim;
    	    			}
    	    			else if (mode==2) {
    	    				vd=skewTri(p,v,1.0,aspts);
    	    			}
    					System.err.println("   v="+v+", new error = "+Math.abs(vd[0]));
    				}
	    		}
	    	}
    			  
	    	// update states, accum error
	    	accum=0;
	    	for (int jj=0;jj<aimNum;jj++) {
	    		int V=inDex[jj];
	    		v=Math.abs(V);
	    		if (mode==1) { // mode=1, default
	    			accum += Math.abs(angSumTri(p,v,1.0,aspts)[0]-p.rData[v].aim);
	    		}
	    		else if (mode==2) {
	    			accum += Math.abs(skewTri(p,v,1.0,aspts)[0]);
	    		}
	    	}
	    	cut=accum*recip;
	    	count++;
	    } /* end of while */
	      
	    return count;
	} 

	/** 
	 * Euclidean riffle of side lengths to get aims: for each vertex v, find 
	 * factor f>0 so that multiplying all 'sides' from v by f 
	 * in all faces containing v gives the 'aim' anglesum at v. 
	 * Currently, do all vertices with aim>0, whether bdry or interior.
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
     * @param myList @see LinkedList: if 'NodeLink', riffle only these verts
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
			if (p.rData[vv].aim > 0
					&& (vlist == null || vlist.contains(Integer.valueOf(vv)))) {
				inDex[aimNum] = vv;
				aimNum++;
			}
		}
		if (aimNum == 0)
			return -1; // nothing to repack

		// compute initial curvatures
		for (int j = 0; j < aimNum; j++) {
			v = inDex[j];
			curv[v] = angSumSide(p, v, 1.0,aspts);
		}

		// set cutoff value
		double accum = 0.0;
		for (int j = 0; j < aimNum; j++) {
			v = inDex[j];
			err = curv[v] - p.rData[v].aim;
			accum += (err < 0) ? (-err) : err;
		}
		double recip = .333333 / aimNum;
		double cut = accum * recip;

		// now cycle through adjustments --- riffle
		while ((cut > TOLER && count < passes)) {
			for (int j = 0; j < aimNum; j++) {
				v = inDex[j];
				curv[v] = angSumSide(p, v,1.0,aspts);
				verr = curv[v] - p.rData[v].aim;

				// find/apply factor to radius or sides at v
				if (Math.abs(verr) > cut) {
					double sideFactor = sideCalc(p,v, p.rData[v].aim, 5,
							aspts);
					adjustSides(p,v, sideFactor,aspts);
					curv[v] = angSumSide(p, v,1.0, aspts);
				}
			}
			accum = 0;
			for (int j = 0; j < aimNum; j++) {
				int V = inDex[j];
				v = Math.abs(V);
				curv[v] = angSumSide(p, v, 1.0,aspts);
				err = curv[v] - p.rData[v].aim;
				accum += (err < 0) ? (-err) : err;
			}
			cut = accum * recip;

			count++;
		} /* end of while */

		return count;
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
	public static double angSumSide(PackData p,int v,double factor,TriAspect []asps) {
		double angsum=0.0;
		for (int j=0;j<p.kData[v].num;j++) {
			int f=p.kData[v].faceFlower[j];
			int k=asps[f].vertIndex(v);
			double s0=factor*asps[f].sides[k];
			double s1=asps[f].sides[(k+1)%3];
			double s2=factor*asps[f].sides[(k+2)%3];
			angsum += Math.acos((s0*s0+s2*s2-s1*s1)/(2.0*s0*s2));
		}
		return angsum;
	}
	
	/**
	 * Change side lengths at v by 'factor' in every face containing v.
	 * @param p PackData 
	 * @param v vertex
	 * @param factor double
	 * @param asp []TriAspect
	 * @return 1
	 */
	public static int adjustSides(PackData p,int v,double factor,TriAspect []asp) {
		for (int j=0;j<p.kData[v].num;j++) {
			int f=p.kData[v].faceFlower[j];
			int k=asp[f].vertIndex(v);
			asp[f].sides[k] *=factor;
			asp[f].sides[(k+2)%3] *=factor;
		}
		return 1;
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
	public static double sideCalc(PackData p,int v,double aim,int N,TriAspect []asps) {
		double bestcurv,upcurv,lowcurv;
		double lower,upper;
		double limit=0.5;
		double best=1.0;

		// starting curvature
		bestcurv=lowcurv=upcurv=ProjStruct.angSumSide(p,v,best,asps);
		
		if (Math.abs(bestcurv-aim)<=OKERR)
			return 1.0;
		
		// set upper/lower limits on possible factors due to triangle inequality
		double []bds=sideBounds(p,v,asps);
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
  	 * Return upper/lower bounds on factor by which sides can 
  	 * be adjusted at v while preserving the triangle inequality
  	 * for all faces containing v.
  	 * @param p PackData
  	 * @param v vertex
  	 * @param asps []TriAspect
  	 * @return int[2]: [0], lower; [1], upper
  	 */
  	public static double []sideBounds(PackData p,int v,TriAspect []asps) {
  		double lower=0.0;
  		double upper=100000000;
		for (int j=0;j<p.kData[v].num;j++) {
			int f=p.kData[v].faceFlower[j];
			int k=asps[f].vertIndex(v);
			double rSide=asps[f].sides[k];
			double lSide=asps[f].sides[(k+2)%3];
			double oppSide=asps[f].sides[(k+1)%3];
			if ((rSide+lSide)<oppSide || oppSide<Math.abs(rSide-lSide))
				throw new DataException("Triangle inequality fails for face "+f);
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
	 * Compute angle sum at 'v' face-by-face using 'labels'
	 * with labels at v multiplied by factor t>0. Also, return
	 * the derivative w.r.t. t.
	 * @param p @see PackData
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @param asps[] @see TriAspect
	 * @return double
	 */
	public static double []angSumTri(PackData p, int v, double t,TriAspect[] asps) {
		double []ans=new double[2];
		for (int j = 0; j < p.kData[v].num; j++) {
			int f = p.kData[v].faceFlower[j];
			double []sd= asps[f].angleV(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
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
	public static double []skewTri(PackData p, int v,double t, TriAspect[] asps) {
		double []ans = new double[2];
		for (int j = 0; j < p.kData[v].num; j++) {
			int f = p.kData[v].faceFlower[j];
			double []sd= asps[f].skew(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}
	  	
	/**
	 * Change label for v in all faces containing 'v' and change 'rData[v].rad'
	 * by multiplicative 'factor'.
	 * @param p @see PackData 
	 * @param v int, parent vertex
	 * @param factor double
	 * @param asp[] @see TriAspect
	 * @return int -1 on error
	 */
	public static int adjustRadii(PackData p,int v,double factor,TriAspect []asp) {
		p.rData[v].rad *=factor;
		for (int j=0;j<p.kData[v].num;j++) {
			int f=p.kData[v].faceFlower[j];
			int k=asp[f].vertIndex(v);
			asp[f].labels[k] *=factor;
		}
		return 1;
	}

	/**
	 * For setting prescribed parameters for affine torus
	 * construction.
	 * @param p, PackData
	 * @param A,B, double side-pairing parameters
	 */
	public static boolean affineSet(PackData p,TriAspect []asps,double A,double B) {
		// how many torus side-pairings? 3 (generic) or 2?
		boolean hexGram=true; // 3 
		if (p.getSidePairs().size()<6) // 2 
			hexGram=false; 
		
		/* Idea: prescribe (via A and B) the radii for vertices 
		 * along the outside of the red chain. Use these and 
		 * interior radii 1.0 to set 'labels' in vector 'aspects'.
		 * Went there are just two side-pairings (preferable),
		 * want A to be scale factor from #1 to #3, and B to be 
		 * scale factor from #2 to #4. TODO: verify that this
		 * lines up with the generic (3 side-pairing) situation.
		 */
		
		// Create list of vertices on the outside edge of the redchain.
		NodeLink redVerts=new NodeLink(p,"Ra");
		int rvSize=redVerts.size();
		if (redVerts==null || rvSize<4)
			throw new ParserException("error in getting outside of redchain");
		
		// rotate to start at first red edge <v,w> of first side
		RedEdge re=p.getSidePairs().get(0).startEdge;
		int vindx=re.vIndex;
		if (re.prev.face==re.next.face && re.prevRed.face!=re.face) // blue face, first edge 
			vindx = (vindx+1)%3;
		int v=p.faces[re.face].vert[(vindx+2)%3];
		int w=p.faces[re.face].vert[vindx];
		int y=redVerts.findVW(v,w);
		if (y==-1)
			throw new ParserException("error in finding first edge");
		else if (y==-2) // v is last, w first
			y=redVerts.size()-1;
		redVerts=NodeLink.rotateMe(redVerts,y);

		// Get vector of indices in 'redVerts' of the corners
		int []cornIndx=new int[p.getSidePairs().size()+1]; 
		for (int j=0;j<p.getSidePairs().size();j++) {
			try {
				re=p.getSidePairs().get(j).startEdge;
				vindx=re.vIndex;
				if (re.prev.face==re.next.face && re.prevRed.face!=re.face) // first edge is blue face?
					vindx = (vindx+1)%3;
				v=p.faces[re.face].vert[(vindx+2)%3];
				w=p.faces[re.face].vert[vindx];
				cornIndx[j]=redVerts.findVW(v,w);
				if (cornIndx[j]<0) 
					throw new ParserException();
			} catch (Exception ex) {
				throw new ParserException("error finding corners: "+ex.getMessage());
			}
		}
		cornIndx[p.getSidePairs().size()]=redVerts.size()-1;
		
		// set up radii. Want A to be scale factor from #1 to #3,
		//   B to be scale factor from #2 to #4
		// ====== 2 pairings --- parallelogram case ======
		//   Side 1: radii run from 1.0 to B,
		//   Side 3: (paired with 1) radii from A*B to A.
		//   Side 2: radii run from B to A*B,
		//   Side 4: (paired with 2) radii from A to 1.0.
		double []redRad=new double[redVerts.size()+1];
		if (!hexGram) { // parallelogram case
			int j;
			
			// bottom (#1); Starts with radius 1.0 at first corner
			int n13=cornIndx[1]-cornIndx[0];
			double inc13=(B-1.0)/n13;
			for (j=0;j<n13;j++) {
				redRad[j]=1.0+j*inc13;
			}
			// right end (#2)
			int n24=cornIndx[2]-cornIndx[1];
			double inc24=(A-1.0)/n24;
			for (j=0;j<n24;j++) {
				redRad[cornIndx[1]+j]=(1.0+j*inc24)*B;
			}
			// top (#3)
			if (cornIndx[3]-cornIndx[2]!=n13) {
				throw new CombException("top and bottom counts don't match");
			}
			for (j=0;j<n13;j++) {
				redRad[cornIndx[2]+j]=A*(B-inc13*j);
			}
			// left end (#4)
			if (cornIndx[4]-cornIndx[3]!=n24) {
				throw new CombException("left and right counts don't match");
			}
			for (j=0;j<=n24;j++) {
				redRad[cornIndx[3]+j]=A-j*inc24;
			}
		}
		else { // hex fundamental domain
			// ====== 3 pairings --- hex fundamental domain =====
			//  Side 1: all radii 1
			//  Side 2: radii from 1 to B
			//  Side 3: radii from B to A
			//  Side 4: all radii A
			//  Side 5: radii from A to C=A/B
			//	Side 6: radii from C to 1
			int j;
			redRad[0]=1.0;
			// first side
			int n=cornIndx[1]-cornIndx[0];
			for (j=1;j<=n;j++) {
				redRad[cornIndx[0]+j]=1.0;
			}
			// second
			n=cornIndx[2]-cornIndx[1];
			double inc=(B-1.0)/n;
			for (j=1;j<=n;j++) {
				redRad[cornIndx[1]+j]=1.0+j*inc;
			}
			// third
			n=cornIndx[3]-cornIndx[2];
			inc=(A-B)/n;
			for (j=1;j<=n;j++) {
				redRad[cornIndx[2]+j]=B+j*inc;
			}
			// fourth
			n=cornIndx[4]-cornIndx[3];
			for (j=1;j<=n;j++) {
				redRad[cornIndx[3]+j]=A;
			}
			// fifth
			n=cornIndx[5]-cornIndx[4];
			inc=(A/B-A)/n;
			for (j=1;j<=n;j++) {
				redRad[cornIndx[4]+j]=A+j*inc;
			}
			// sixth
			n=cornIndx[6]-cornIndx[5]; // extra count here
			inc=(1.0-A/B)/n;
			for (j=1;j<=n;j++) {
				redRad[cornIndx[5]+j]=A/B+j*inc;
			}
		}
		
		// set 'labels' vectors for all faces to 1.0:1.0:1.0
		for (int f=1;f<=p.faceCount;f++) {
			for (int j=0;j<3;j++)
				asps[f].labels[j]=1.0;
		}
		
		// Along each edge, reset face 'labels' for the red verts on the
		//  outside of that edge.
		for (int i=0;i<p.getSidePairs().size();i++) {

			SideDescription thisSide=p.getSidePairs().get(i);

			// go through outer red edges and their initial red
			//    verts 'vi'; find fans of ref faces about vi and
			//    set labels just for vi in these faces.
			int h=cornIndx[i];
			RedEdge rl=thisSide.startEdge;
			while (h<cornIndx[i+1]) {
				int vi=redVerts.get(h);
				int []fanInfo=p.red_fan((RedList)rl,vi);
				if (fanInfo[0]<0)
					throw new CombException("error setting red radii");
				for (int j=0;j<fanInfo[1];j++) {
					int fc=p.kData[vi].faceFlower[(fanInfo[0]+j)%p.kData[vi].num];
					Face face=p.faces[fc];
					asps[fc].labels[face.vertIndx(vi)]=redRad[h];
				}
				h++;
				rl=rl.nextRed;
			}
		} // done with various sides

		return true;
	}
	
	/**
	 * Given TriAspect, vert v2, and oriented centers for opposite 
	 * edge vertices, return center and rad of v2's circle.
	 * Note: radii and normalized centers should be recorded in
	 * 'asp', use vector w=c1-c0 to scale/rotate and c0 to
	 * translate so v0 and v1 centers are at c0, c1. Return
	 * center c2 and radius r2 (which may be scaled).
	 * @param TriAspect
	 * @param v2
	 * @param c0
	 * @param c1
	 * @return null on error
	 */
	public static CircleSimple compThird(TriAspect asp,int v2,
			Complex c0,Complex c1) {
		int k0,k1,k2;
		if ((k2=asp.vertIndex(v2))<0 || asp.getCenter((k0=(k2+1)%3))==null ||
				asp.getCenter((k1=(k2+2)%3))==null) return null;
		Complex inc=c1.minus(c0);
		Complex dis=asp.getCenter(k1).minus(asp.getCenter(k0));
		Complex Z=inc.divide(dis);
		Complex cent=asp.getCenter(k2).minus(asp.getCenter(k0)).times(Z).add(c0);
		double newRad=asp.labels[k2]*Z.abs();
		return new CircleSimple(cent,newRad,0);
	}
	
	/**
	 * Compute the PackData eucl radii based on TriAspect 'labels' 
	 * for faces by going trough the faces in drawing order. 
	 * Store in 'rData', but because those on paired boundaries 
	 * may be multi-valued, store them in 'radii' also, indexed 
	 * by face. (radii[f] is the radius of the circle that f 
	 * is responsible for drawing.) 
	 * 
	 * After this is done, use 'radii' and redchain to set 'rad' 
	 * for the redChain.
	 * @param p PackData
	 * @param asp TriAspect[]
	 * @return -1 on error
	 */
	public static int affineLayout(PackData p,TriAspect []asp) {
		boolean debug=false;
		
		if (debug) LayoutBugs.log_faceOrder(p);
		FaceLink facelist = new FaceLink(p, "F");
		if (facelist==null || facelist.size()<1) {
			return 0;
		}
		
		// need to compute radii by using the face order,
		//   and save by face index so we can put correct
		//   radii in rData and in the redchain.
		double []radii=new double[p.faceCount+1];
		
		// compute radii to store by following facelist
		Iterator<Integer> flist = facelist.iterator();

		// store the first face radius in 'radii' and 'rData'
		//   (and put other two radii of first face in 'rData')
		int fIndx=flist.next();
		Face nf=p.faces[fIndx];
		int myVert=nf.vert[(nf.indexFlag+2)%3]; // vertex for this face
		// typically, 'baseVert' here is alpha; make it 1/10
		int baseVert=nf.vert[nf.indexFlag]; // vert with known radius
		double baseRad=0.1; // use 'baseVert' known radius as base for scaling 'labels'
		double prevRad=baseRad; // always hold the previous radius (many need for 'blue')
		double baseFactor=baseRad/asp[fIndx].labels[nf.indexFlag];
		
		// store first two radii in rData: these arn't associated
		//   with any face and they shouldn't change
		p.rData[baseVert].rad=0.1;
		p.rData[nf.vert[(nf.indexFlag+1)%3]].rad=
			baseFactor*asp[fIndx].labels[(nf.indexFlag+1)%3];
		
		// store myVert radius in 'rData' and radii
		radii[fIndx]=p.rData[myVert].rad=
			baseFactor*asp[fIndx].labels[(nf.indexFlag+2)%3];
		
		// now set rest of radii entries (one for each face in drawing order)
		while (flist.hasNext()) {
			fIndx=flist.next();
			nf=p.faces[fIndx];
			myVert=nf.vert[(nf.indexFlag+2)%3]; // vert this face is responsible for
			baseVert=p.faces[fIndx].vert[nf.indexFlag];
			// Except when face is blue, 'baseVert' should not be on
			//   redchain and its radius should have been placed in rData
			baseRad=p.rData[baseVert].rad;
			// However, baseVert may be in redchain; this should happen iff 
			//    face is 'blue', have to get radius from preceeding face.
			if (p.kData[baseVert].utilFlag==1) {  
				// safety check: is this face 'blue'?
				int nextVert=p.faces[fIndx].vert[(nf.indexFlag+1)%3];
				if (p.kData[nextVert].utilFlag!=1) 
					throw new DataException("error: "+fIndx+" should be 'blue'");
				// correct radius for baseVert should be that just layed out.
				baseRad=prevRad; 
			}
			prevRad=baseRad;
			
			// use baseRad (known) and labels (known) to compute 'myVert' radius
			baseFactor=baseRad/asp[fIndx].labels[nf.indexFlag]; 
			radii[fIndx]=p.rData[myVert].rad=
				baseFactor*asp[fIndx].labels[(nf.indexFlag+2)%3];
			
			if (debug)
				System.out.println("myVert: "+myVert+" rad "+radii[fIndx]+
						" from "+nf.vert[nf.indexFlag]+" and "+nf.vert[(nf.indexFlag+1)%3]);
		} // end of while

		// go back to store the correct radii in 'rData' (some may have been
		//   corrupted) and then in redChain
		facelist = new FaceLink(p, "F");
		flist = facelist.iterator();
		while (flist.hasNext()) {
			fIndx=flist.next();
			nf=p.faces[fIndx];
			myVert=nf.vert[(nf.indexFlag+2)%3]; // vert this face is responsible for
			p.rData[myVert].rad=radii[fIndx];
		
			if (debug) {
				printRadRatios(p,fIndx,asp);
			}
		}		
		RedList rtrace=(RedList)p.redChain;
		boolean done=false;
		while (rtrace!=(RedList)p.redChain || !done) {
			done=true;
			myVert=p.faces[rtrace.face].vert[rtrace.vIndex];
			// only store the radius if vert is on outside of redChain
//			if (packData.kData[myVert].utilFlag==1) 
				rtrace.rad=radii[rtrace.face];
			p.rData[myVert].rad=radii[rtrace.face];
			if (debug)
				printRadRatios(p,rtrace.face,asp);
			rtrace=(RedList)rtrace.next;
		}

		// now ready to do normal 'layout' for centers.
		return p.reLayList(facelist,0);
	}
	
	/**
	 * Layout face-by-face using current 'labels' information
	 * and using 'dTree'. Centers are entered only in 'asp'
	 * @param p PackData
	 * @param dtree GraphLink
	 * @param asp []TriAspect
	 */
	public static void treeLayout(PackData p,GraphLink dtree,TriAspect []asp) {
		// log for debugging in /tmp/redface ...
		DispFlags dispflags=null;
		boolean debug=false;
		
		if (debug) { // debug=true;
			System.err.println("Debugging on for 'afflay' in AffineStruct");
			dispflags=new DispFlags("n");
		}

		if (dtree==null || dtree.size()==0) 
			throw new DataException("GraphLink error");
		if (debug) LayoutBugs.log_GraphLink(p, dtree);
		Iterator<EdgeSimple> dlk=dtree.iterator();
		while (dlk.hasNext()) {
			EdgeSimple dedge=dlk.next();
			
			// root faces just get laid in place
			if (dedge.v==0) {
				asp[dedge.w].setCenters();
				for (int j=0;j<3;j++) {
					int k=asp[dedge.w].vert[j];
					p.rData[k].center=asp[dedge.w].getCenter(j);
				}
			}
			else {
				int k=p.face_nghb(dedge.v,dedge.w);
				int v2=p.faces[dedge.w].vert[(k+2)%3];
				TriAspect newasp=plopAcrossEdge(p,asp,dedge);
				if (newasp==null) 
					return;
				asp[dedge.w]=new TriAspect(newasp);
				p.rData[v2].center=asp[dedge.w].getCenter((k+2)%3);
			}
			
			// draw the new face
			if (debug) {
				int []vts=p.faces[dedge.w].vert;
				for (int j=0;j<3;j++)
				p.cpScreen.drawCircle(asp[dedge.w].getCenter(j), p.rData[vts[j]].rad, dispflags);
				PackControl.canvasRedrawer.paintMyCanvasses(p,false);
			}
				
		} // end of while
	}

	/**
	 * Given dual edge <f,g> with faces f=edge.v and g=edge.w,
	 * use stored 'label's of g and the centers of f to find
	 * the centers of g. 
	 * @param p PackData
	 * @param asps []TriAspect
	 * @param edge (faces f=edge.v, g=edge.w)
	 * @return new TriAspect, null on error
	 */
	public static TriAspect plopAcrossEdge(PackData p,TriAspect []asps,EdgeSimple edge) {
		int j;
		if (edge==null || edge.v<=0 || edge.w<=0 || edge.v>p.faceCount ||
				edge.w>p.faceCount || (j=p.face_nghb(edge.v,edge.w))<0)
			return null;
		TriAspect newasp=new TriAspect(asps[edge.w]);
		// compute normalized centers for face edge.w 
		newasp.setCenters();
		// we plot v2 of face edge.w, so edge lines up with face edge.v data
		int v0=p.faces[edge.w].vert[j];
		int v1=p.faces[edge.w].vert[(j+1)%3];
		int v2=p.faces[edge.w].vert[(j+2)%3];
		Face face=p.faces[edge.v];
		Complex c0=asps[edge.v].getCenter(face.vertIndx(v0));
		Complex c1=asps[edge.v].getCenter(face.vertIndx(v1));
		newasp.adjustData(v2, c0, c1);
		return newasp;
	}
	
	/**
	 * Transfer the 'center' and 'label' data from 'asp', where it is held 
	 * face-by-face, to the packing. Updates 'kData' centers and 
	 * also 'center' and 'rad' info in the redchain. Update the side-pairings.
	 * @param p, PackData
	 * @param dtree, GraphLink
	 * @param asp[], TriAspect
	 */
	public static void storeCenters(PackData p,GraphLink dtree,TriAspect []asp) {
		int []cck=new int[p.nodeCount+1];
		
		boolean debug=false;
		DispFlags dispflags=null;
		
		// debug=true;
		if (debug) {
			LayoutBugs.log_GraphLink(p,dtree);
			LayoutBugs.pfacered(p);
			LayoutBugs.print_drawingorder(p);
			dispflags=new DispFlags("n");
		}
		
		// set centers following 'dtree' 
		Iterator<EdgeSimple> dlk=dtree.iterator();
		
		while (dlk.hasNext()) {
			EdgeSimple edge=dlk.next();
			int f=edge.w;
			int []verts=p.faces[f].vert;
			for (int j=0;j<3;j++) {
				int v=verts[j];
				if (cck[v]==0) {
					p.rData[v].center=new Complex(asp[f].getCenter(j));
					p.rData[v].rad=asp[f].labels[j];
					cck[v]=1; // mark as set
				if (debug) {// draw the circle	
					p.cpScreen.drawCircle(p.rData[v].center, p.rData[v].rad, dispflags);
					PackControl.canvasRedrawer.paintMyCanvasses(p,false);
				}
				}
			}
		}
		
		// debug=true;
		if (debug) 
			LayoutBugs.log_RedCenters(p);
		
		// Now, go back and set redchain centers
		RedList red=p.redChain;
		boolean hit=true;
		while (red!=p.redChain || hit) {
			hit=false;
			
			// blue face? Divert to set the center of its duplicate blue
			if (red.next.face==red.prev.face) {
				RedEdge re=(RedEdge)red; // cast to RedEdge
				RedEdge tred=re.nextRed;
				int v=p.faces[red.face].vert[tred.vIndex];
				int j=asp[red.next.face].vertIndex(v);
				tred.center=new Complex(asp[red.next.face].getCenter(j));
			}
			red.center=new Complex(asp[red.face].getCenter(red.vIndex));
			if (debug) {
				System.out.println("Face "+red.face+"=<"+p.faces[red.face].vert[0]+","+p.faces[red.face].vert[1]+","+
						p.faces[red.face].vert[2]+">, vert v="+p.faces[red.face].vert[red.vIndex]+", center=("+red.center.x+","+red.center.y+")");
				}
			red.rad=asp[red.face].labels[red.vIndex];
			red=red.next;
		}

		// debug=true;
		if (debug) 
			LayoutBugs.log_RedCenters(p);

		// update the mobius pairs
		p.update_pair_mob();
		PackControl.mobiusFrame.loadSidePairs();
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
				next_asp.setCenters(); // put centers in normalized position
				int jj = p.face_nghb(last_face,next_face);
				if (jj<0) { // error, stop adding to the list
					throw new ParserException("disconnect in chain of faces.");
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
		if (!faceDo && !circDo) return 0;

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
			if (j<0) j=0;
			int v0=asp.vert[j];
			int v1=asp.vert[(j+1)%3];
			int v2=asp.vert[(j+2)%3];
			Complex c0=asp.getCenter(asp.vertIndex(v0));
			Complex c1=asp.getCenter(asp.vertIndex(v1));
			Complex c2=asp.getCenter(asp.vertIndex(v2));
			if (faceDo) { // draw the faces
				if (!faceFlags.colorIsSet && (faceFlags.fill || faceFlags.colBorder))
					faceFlags.setColor(p.faces[asp.face].color);
				if (faceFlags.label)
					faceFlags.setLabel(Integer.toString(asp.face));
				p.cpScreen.drawFace(c0, c1, c2,null,null,null,faceFlags);
				count++;
			}
			if (circDo) { // also draw the circles
				if (!circFlags.colorIsSet && (circFlags.fill || circFlags.colBorder))
					circFlags.setColor(p.kData[v2].color);
				if (circFlags.label)
					circFlags.setLabel(Integer.toString(v2));
				p.cpScreen.drawCircle(c2,asp.labels[asp.vertIndex(v2)],circFlags);
				count++;
				if (drawfirst && firstasp) { // draw all circles of first face
					if (!circFlags.colorIsSet && (circFlags.fill || circFlags.colBorder))
						circFlags.setColor(p.kData[v0].color);
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(v0));
					p.cpScreen.drawCircle(c0,asp.labels[asp.vertIndex(v0)],circFlags);
					if (!circFlags.colorIsSet && (circFlags.fill || circFlags.colBorder))
						circFlags.setColor(p.kData[v1].color);
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(v1));
					p.cpScreen.drawCircle(c1,asp.labels[asp.vertIndex(v1)],circFlags);
					count++;
				}
			}
			firstasp=false;
		}
		return count;
	}
	
	/**
	 * For debugging.
	 * @param p
	 * @param fnum
	 * @param asp
	 */
	public static void printRadRatios(PackData p,int fnum,TriAspect []asp) {
		int []vts=p.faces[fnum].vert;
		double r0=p.rData[vts[0]].rad;
		double r1=p.rData[vts[1]].rad;
		double r2=p.rData[vts[2]].rad;
		System.out.println("face "+fnum+": <"+vts[0]+","+vts[1]+","+vts[2]+">");
		System.out.println("   rad labels:   "+1+",  "+r1/r0+",  "+r2/r0);
		double rat0=asp[fnum].labels[0];
		System.out.println("   face labels:  "+1+",  "+asp[fnum].labels[1]/rat0+",  "+
				asp[fnum].labels[2]/rat0);
	}
	
	/**
	 * Given an interior edge <v,w> find the ratio of
	 * its t values: If f,g are the left/right faces,
	 * t_f is labels[w]/labels[v] in f, t_g is 
	 * labels[v]/labels[w] in g.
	 * @param p, PackData 
	 * @param asp, TriAspect
	 * @param edge, EdgeSimple
	 * @return abs(log(t_f/t_g)), -1 on error, 0 for bdry edge.
	 */
	public static double logEdgeTs(PackData p,EdgeSimple edge,TriAspect []asp) {
		int v=edge.v;
		int w=edge.w;
		int f=p.left_face(edge)[0];
		int g=p.left_face(new EdgeSimple(w,v))[0];
		if (f<=0 || g<=0) return 0; // bdry edge
		int j=asp[f].vertIndex(v);
		int k=asp[g].vertIndex(w);
		double lg=Math.log(asp[f].labels[(j+1)%3]*asp[g].labels[(k+1)%3])-
				Math.log(asp[f].labels[j]*asp[g].labels[k]);
		return Math.abs(lg);
	}

	public double ratioErr() {
		double err=0;
		for (int f=1;f<=packData.faceCount;f++) {
			Face face=packData.faces[f];
			for (int j=0;j<3;j++) {
				int v=face.vert[j];
				int w=face.vert[(j+1)%3];
				int g=packData.face_right_of_edge(v,w);
				int k=packData.faces[g].vertIndx(w);
				double er=aspects[f].labels[j] - aspects[g].labels[k];
				err += er*er;
			}
		}
		return err;
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
				for (int j=0;j<3;j++) {
					int v=asp[f].vert[j];
					if (cck[v]==0) { // have to process this vertex
						int num=p.kData[v].num;
						double areaSum=0.0;
						double angSum=0.0;
						for (int vj=0;vj<num;vj++) { // iterate over faces
							int fv=p.kData[v].faceFlower[vj];
							areaSum += asp[fv].sectorAreaZ(v);
							angSum +=asp[fv].angleV(v,1.0)[0];
						}
						p.rData[v].rad=Math.sqrt(2.0*areaSum/angSum);
						cck[v]=1;
					}
				}
				count++;
			} 
		} catch (Exception ex) {
			throw new DataException("Error in 'effective rad' comp: "+ex.getMessage());
		}
		return count;
	}
	
	/**
	 * Return 'edge' consistency error computed from 'labels'. 
	 * For interior edge, this is t*t', where t is the ratio 
	 * of 'labels' for 'edge' in lefthand face, t' is that of
	 * lefthand face. 
	 * @param edge, EdgeSimple
	 * @param p, PackData,
	 * @param asps, TriAspect[]
	 * @return 1.0 if not interior edge.
	 */
	public static double edgeRatioError(PackData p,TriAspect []asps,EdgeSimple edge) {
		if (p.kData[edge.v].bdryFlag!=0 && 
				p.kData[edge.w].bdryFlag!=0) // bdry edge 
			return 1.0;
		int []lf=p.left_face(edge.v,edge.w);
		int lface=lf[0];
		lf=p.left_face(edge.w,edge.v);
		int rface=lf[0];
		int lj=asps[lface].vertIndex(edge.v);
		int rj=asps[rface].vertIndex(edge.w);
		double prd=asps[lface].labels[(lj+1)%3];
		prd /=asps[lface].labels[lj];
		prd *=asps[rface].labels[(rj+1)%3];
		prd /=asps[rface].labels[rj];
		return prd;
	}

	/**
	 * Return angle sum error at 'v' based on TriAspect 'labels'.
	 * @param v int, vertex index in packing
	 * @return double, abs(error); 0 if 'aim' <=0
	 */
	public double angsumError(int v) {
		if (packData.rData[v].aim<=0)
			return 0;
		return Math.abs(angSumTri(packData,v,1.0,aspects)[0]-packData.rData[v].aim);
	}
	
	/** 
	 * Return weak consistency error for interior 'v'.
	 * This is product of leftlenght/rightlength for all 
	 * faces in star(v).
	 * @param p @see PackData
	 * @param aspects []TriAspect
	 * @param v int, vertex
	 * @return double, 1.0 if v not interior.
	 */  
	public static double weakConError(PackData p,TriAspect []aspects,int v) {
		if (p.kData[v].bdryFlag!=0)
			return 1.0;
		double rtio=1.0;
		for (int j=0;j<p.kData[v].num;j++) {
			int ff=p.kData[v].faceFlower[j];
			int k=aspects[ff].vertIndex(v);
			rtio *= aspects[ff].sides[(k+2)%3]; // left sidelength
			rtio /= aspects[ff].sides[k]; // right sidelength
		}
		return rtio;
	}

	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;

		// ======== testTree =========
		// for testing new 'DualGraph' routines
		if (cmd.startsWith("tT")) {
			int mode=1;
			// mode specified?
			try {
				items=flagSegs.get(0);
				mode=Integer.parseInt(items.get(0));
			} catch (Exception ex) {
				mode=1;
			}
			
			if (mode==1) {
				dTree=torus4layout(packData,0); 
				if (dTree==null)
					errorMsg("torus4layout failed");
			}

			else { // older method
				// create the full dual graph
				GraphLink fullDual=DualGraph.buildDualGraph(packData,packData.firstFace,null);
		
				// extract the dual tree
				dTree=fullDual.extractSpanTree(packData.firstFace);
			
				boolean debug=false;
				if (!debug) {
					// get RedList from dual tree
					RedList newRedList=DualGraph.graph2red(packData,dTree,packData.firstFace);
				
					// process to get redChain
					RedChainer newRC=new RedChainer(packData);
					BuildPacket bP=new BuildPacket();
					bP=newRC.redface_comb_info(newRedList, false);
					if (!bP.success) {
						throw new LayoutException("Layout error, simply connected case ");
					}
					packData.setSidePairs(bP.sidePairs);
					packData.labelSidePairs(); // establish 'label's
					packData.redChain=packData.firstRedEdge=bP.firstRedEdge;
				}
			}
			
			return 1;
		}
		
		// ======== sideInfo =========
		if (cmd.startsWith("sI")) {
			Vector<Integer> sideStartVerts=new Vector<Integer>(6);
			Vector<Double> sideArgChanges=new Vector<Double>(6);
			Vector<Complex> cornerPts=new Vector<Complex>(6);
			Vector<Mobius> sideMobius=new Vector<Mobius>(6);
			int theCorner=sideInfo(packData,aspects,sideStartVerts,cornerPts,
					sideArgChanges,sideMobius);
//System.err.println("'theCorner' found was "+theCorner);
			if (theCorner>0) {
				CirclePack.cpb.msg("Torus layout corner vert = "+theCorner+" and locations are:");
				Iterator<Complex> corners=cornerPts.iterator();
				while (corners.hasNext()) 
					CirclePack.cpb.msg(corners.next().toString());
			}
			return 1;
		}
		
		// ======== Lface ==========
		if (cmd.startsWith("Lface")) {
			DispFlags dflags=new DispFlags("");
			for (int f=1;f<=packData.faceCount;f++) {
				packData.cpScreen.drawFace(aspects[f].getCenter(0),
						aspects[f].getCenter(1),aspects[f].getCenter(2),null,null,null,dflags);
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
		
		// ======== Ltree ==========
		else if (cmd.startsWith("Ltree")) {
			Iterator<EdgeSimple> ft=dTree.iterator();
			while (ft.hasNext()) {
				EdgeSimple edge=ft.next();
				if (edge.v!=0) {
					CircleSimple sc;
					sc=EuclMath.eucl_tri_incircle(aspects[edge.v].getCenter(0),
							aspects[edge.v].getCenter(1),aspects[edge.v].getCenter(2));
					Complex vc=sc.center;
					sc=EuclMath.eucl_tri_incircle(aspects[edge.w].getCenter(0),
							aspects[edge.w].getCenter(1),aspects[edge.w].getCenter(2));
					Complex wc=sc.center;
					DispFlags df=new DispFlags(null);
					df.setColor(Color.green);
					packData.cpScreen.drawEdge(vc,wc,df);
				}
			}
			repaintMe();
			return 1;
		}
		
		// ======== affine ===========
		else if (cmd.startsWith("affine")) {
			// this routine is tailored for tori: specify side-pair
			// scaling in an attempt to build general affine tori

			if (packData.genus != 1 || packData.bdryCompCount > 0) {
				int count=0;
				msg("Simply connected case: 'affine' defaults to all 'labels' 1");
				for (int f=1;f<=packData.faceCount;f++) {
					for (int j=0;j<3;j++) 
						aspects[f].labels[j]=1.0;
					count++;
				}
				return count;
			}

			if (aspects==null || aspects.length!=(packData.faceCount+1))
				setupAspects();
			
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
				Oops("affine has failed");
			msg("Affine data set: A = " + A + " B = " + B);
			return 1;
		}
		
		// ======== affpack ===========
		else if (cmd.startsWith("affpack")) {
			NodeLink vlink=null;
			
			// are vertices specified?
			try {
				items=flagSegs.get(0);
				vlink=new NodeLink(packData,items);
			} catch (Exception ex) {}
			
			// first, riffle to get weak consistency
			int count = vertRiffle(packData, aspects,2,PASSES,vlink);
			if (count < 0) {
				Oops("weak riffle seems to have failed");
				return 0;
			}
			
			// next, riffle to get angle sums (which should preserve weak consistency)
			count = vertRiffle(packData, aspects,1,PASSES,vlink);
			if (count < 0) {
				Oops("riffle for aims seems to have failed");
				return 0;
			}
			
			if (debug) {
				BufferedWriter dbw = CPFileManager.openWriteFP(new File(System
						.getProperty("java.io.tmpdir")), new String("anglesum_"
						+ CPBase.debugID + "_log"), false);
				try {
					dbw.write("anglesum:\n\n");
					for (int v = 1; v <= packData.nodeCount; v++) {
						dbw.write("vertex " + v + ": " + angSumTri(packData,v,1.0,aspects)[0] + "\n");
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

		// ========= afflayout ========
		else if (cmd.startsWith("afflay")) { // use
			treeLayout(packData,dTree,aspects);
			storeCenters(packData,dTree,aspects);
			return 1;
		}

		// ======== status ==========
		else if (cmd.startsWith("stat")) {
			NodeLink vlist=null;
			EdgeLink elist=null;
			double Angsum_err=0.0;
			double TLog_err=0.0;
			double SLog_err=0.0;
			int count=0;
			
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
							elist=new EdgeLink(packData,items);
							if (elist!=null && elist.size()>0) {
								EdgeSimple edge=elist.get(0);
								msg("Edge <"+edge.v+" "+edge.w+">, t*t' = "+
										String.format("%.8e",edgeRatioError(packData,aspects,edge)));
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
		
		// ======== set_eff =========
		else if (cmd.startsWith("set_eff")) {
			if (setEffective(packData,aspects)<0)
				Oops("Error in setting effective radii.");
			return 1;
		}
		
		// ======== draw ============
		else if (cmd.startsWith("draw")) {
			boolean circs = false;
			boolean facs = false;
			int count = 0;
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
						DispFlags dispFlags=new DispFlags(str.substring(2),packData.cpScreen.fillOpacity); // cut out -?
						FaceLink facelist;
						if (items==null || items.size()==0) // do all
							facelist = new FaceLink(packData, "F");
						else facelist=new FaceLink(packData,items);
						try {
							Iterator<Integer> flst = facelist.iterator();
							boolean first_face = true;
							while (flst.hasNext()) {

								int fnum = flst.next();
								TriAspect tasp = aspects[fnum];
								if (circs) {
									int k = packData.faces[fnum].indexFlag;
									for (int j = 0; j < 3; j++) {
										int kk = (k + 2 + j) % 3;
										if (!first_face)
											j = 2; // just one circle
										Complex z = tasp.getCenter(kk);
										double rad = tasp.labels[kk];
										int v = tasp.vert[kk];
							
										if (!dispFlags.colorIsSet && (dispFlags.fill || dispFlags.colBorder))
											dispFlags.setColor(packData.kData[v].color);
										if (dispFlags.label)
											dispFlags.setLabel(Integer.toString(v));
										packData.cpScreen.drawCircle(z, rad,dispFlags);
										count++;
									}
								}
								if (facs) {
									if (!dispFlags.colorIsSet && (dispFlags.fill || dispFlags.colBorder))
										dispFlags.setColor(packData.faces[fnum].color);
									if (dispFlags.label)
										dispFlags.setLabel(Integer.toString(fnum));
									packData.cpScreen.drawFace(tasp.getCenter(0),tasp.getCenter(1),tasp.getCenter(2),
											null,null,null,dispFlags);
									count++;
								}
							} // end of while 

							PackControl.canvasRedrawer.paintMyCanvasses(packData,false);
						} catch (Exception ex) {
							Oops("affine drawing error");
						}
					}
				} // end of while on flagSegs
			} catch (Exception ex) {}
			return count;
		}

		// ======== log (in temp log file) ============
		else if (cmd.startsWith("log_rad")) {
			File logfile=new File(System.getProperty("java.io.tmpdir"),
				new String("labels_"+ CPBase.debugID + "_log"));
			BufferedWriter dbw = CPFileManager.openWriteFP(logfile,false,false);
			try {
				dbw.write("labels:\n\n");
				for (int f = 1; f <= packData.faceCount; f++) {
					Face face = packData.faces[f];
					dbw.write("face " + f + ": <" + face.vert[0] + ","
							+ face.vert[1] + "," + face.vert[2] + ">   "
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
		
		// ========= dTree ============
		else if (cmd.startsWith("dTree")) {
			dTree=DualGraph.easySpanner(packData,false);
			return 1;
		}
		
		// ========= set_labels =======
		else if (cmd.startsWith("set_lab")) {
			// no flags? default to '-r', based on radii
			if (flagSegs==null || flagSegs.size()==0) {
				flagSegs=StringUtil.flagSeg("-r"); 
			}
			FaceLink facelist;
			int count=0;
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
					else facelist=new FaceLink(packData,items);
					Iterator<Integer> flt=facelist.iterator();
					
					switch(c) {
					case 'r':  { // use current radii
						while (flt.hasNext()) {
							int f=flt.next();
							for (int j = 0; j < 3; j++)
								aspects[f].labels[j]=
									packData.rData[packData.faces[f].vert[j]].rad;
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

		// ========== ccode ===========
		else if (cmd.startsWith("ccod")) {
//			int mode=0; // default to edge color
			
			// currently we only color/draw all edges
			// NOTE: we cannot store edge colors.
			
			// store data for qualifying edges in vector
			Vector<Double> edata=new Vector<Double>();
			for (int v=1;v<=packData.nodeCount;v++) {
				int num=packData.kData[v].num+packData.kData[v].bdryFlag;
				for (int j=0;j<num;j++) {
					int w=packData.kData[v].flower[j];
					if (w>v) {
						if (packData.kData[v].bdryFlag==0 || packData.kData[w].bdryFlag==0)
							edata.add(logEdgeTs(packData,new EdgeSimple(v,w),aspects));
					}
				}
			}
			
			Vector<Integer> ccodes=ColorUtil.blue_red_diff_ramp(edata);
			
			// draw (same order)
			int spot=0;
			for (int v=1;v<=packData.nodeCount;v++) {
				int num=packData.kData[v].num+packData.kData[v].bdryFlag;
				for (int j=0;j<num;j++) {
					int w=packData.kData[v].flower[j];
					if (w>v) {
						if (packData.kData[v].bdryFlag!=0 && packData.kData[w].bdryFlag!=0)
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
		
		// ========== set_screen ======
		else if (cmd.startsWith("set_scre")) {
			double mnX=100000.0;
			double mxX=-100000.0;
			double mnY=100000.0;
			double mxY=-100000.0;
			double pr;
			for (int f = 1; f <= packData.faceCount; f++)
				for (int j = 0; j < 3; j++) {
					mnX = ((pr=aspects[f].getCenter(j).x-aspects[f].labels[j])<mnX) ? pr : mnX; 
					mxX = ((pr=aspects[f].getCenter(j).x+aspects[f].labels[j])>mxX) ? pr : mxX; 
					mnY = ((pr=aspects[f].getCenter(j).y-aspects[f].labels[j])<mnY) ? pr : mnY; 
					mxY = ((pr=aspects[f].getCenter(j).y+aspects[f].labels[j])>mxY) ? pr : mxY; 
				}
			cpCommand("set_screen -b "+mnX+" "+mnY+" "+mxX+" "+mxY);
			packData.cpScreen.repaint();
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
			int its=SassStuff.sideRiffle(packData,aspects,2000,vlink);
			msg("'sideRif' iterations: "+its);
			
			// reset 'labels' vector from 'sides'
			for (int f=1;f<=packData.faceCount;f++) 
				aspects[f].sides2Labels();
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
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Given torus, change the drawing order to get a 2-side-pairing 
	 * layout. Idea: start with spanning tree and its redchain; find 
	 * one short closed edgepath (use vert with large degree as alpha); 
	 * then find second short edgepath; build spanning tree not crossing 
	 * the edges in these paths. This spanning tree leads (via face_order) 
	 * to layout.
	 * @param p @see PackData
	 * @param baseVert (potential corner); if 0, search for largest 
	 * 	degree vert
	 * @return GraphLink, dual spanning tree, null on error or if not a torus.
	 */
	public static GraphLink torus4layout(PackData p,int baseVert) {

		// is this a torus?
		if (p.bdryCompCount>0 || p.genus!=1) { 
			return null;
		}
		
		// set baseVert: use given or choose largest degree vert
		if (baseVert<1) {  
			baseVert=p.alpha;
			int fs=p.kData[baseVert].num;
			for (int v=1;v<=p.nodeCount;v++) {
				if (p.kData[v].bdryFlag==0 ||
						p.kData[v].num>fs) {
					baseVert=v;
					fs=p.kData[baseVert].num;
				}
			}
		}
		
		// set alpha and set usual redChain via 'facedraworder'
		if (p.alpha!=baseVert)
			p.setAlpha(baseVert);
		// LayoutBugs.print_drawingorder(p);
		// get closed, ordered 'NodeLink' of vertices on outside red chain
		NodeLink outverts=new NodeLink(p,"Ra");
		outverts.add(outverts.get(0)); // repeat to close
		
		// get closed paths and create EdgeLink 
		NodeLink firstPath=p.findShortPath(p.alpha,outverts);
		
		// Want to get seed far from the first path
		for (int v=1;v<=p.nodeCount;v++)
			p.kData[v].utilFlag=0;
		Iterator<Integer> fplst=firstPath.iterator();
		while (fplst.hasNext()) 
			p.kData[fplst.next()].utilFlag=1;
		util.UtilPacket uP=new util.UtilPacket();
		int []fp_gens=p.label_generations(-1, uP);
		if (fp_gens!=null && uP.rtnFlag>0)
			baseVert=uP.rtnFlag;
		else baseVert=p.alpha;
		
		// now find second closed path
		NodeLink secondPath=p.findShortPath(baseVert,firstPath);
		EdgeLink cutEdges=new EdgeLink(p);
		int fst=firstPath.get(0);
		int nxt=0;
		for (int k=1;k<firstPath.size();k++) {
			cutEdges.add(new EdgeSimple(fst,(nxt=firstPath.get(k))));
			fst=nxt;
		}
		fst=secondPath.get(0);
		for (int k=1;k<secondPath.size();k++) {
			cutEdges.add(new EdgeSimple(fst,(nxt=secondPath.get(k))));
			fst=nxt;
		}
		
		// Want 'alpha' far from the union of these paths
		for (int v=1;v<=p.nodeCount;v++)
			p.kData[v].utilFlag=0;
		Iterator<Integer> fpl=firstPath.iterator();
		while (fpl.hasNext()) 
			p.kData[fpl.next()].utilFlag=1;
		fpl=secondPath.iterator();
		while (fpl.hasNext()) 
			p.kData[fpl.next()].utilFlag=1;
		uP=new util.UtilPacket();
		fp_gens=p.label_generations(-1, uP);
		if (fp_gens!=null && uP.rtnFlag>0)
			p.alpha=uP.rtnFlag; // one of largest generation
		
		// firstFace is one containing 'alpha'
		p.firstFace=p.kData[p.alpha].faceFlower[0];
		
		// build list 'cutDuals' of dual edges
		EdgeLink cutDuals=new EdgeLink();
		Iterator<EdgeSimple> cutlst=cutEdges.iterator();
		while (cutlst.hasNext()) {
			EdgeSimple edg=cutlst.next();
			cutDuals.add(p.dualEdge(edg.v,edg.w));
		}
		
		// create the full dual graph, except don't cross 'cutEdges'
		GraphLink fullDual=DualGraph.buildDualGraph(p,p.firstFace,cutEdges); 
		
		// LayoutBugs.log_GraphLink(p,fullDual); // graphLink_xx_log.txt
		
		// extract a drawing tree 
		// to see edge DualGraph.showGraph(fullDual); 
		GraphLink tree=DualGraph.drawSpanner(p,fullDual,p.firstFace);
		 
		// LayoutBugs.log_GraphLink(p,tree);
		
		// convert to drawing order for p  // DualGraph.printGraph(tree);
		DualGraph.graph2Order(p,tree,p.firstFace);
		
		// extract the dual tree 
		tree=DualGraph.easySpanner(p,false);
		
		return tree;
	}
	
	/**
	 * Assuming 'sidePairs' exists, return info for the
	 * sides (in the red chain) based on current 'TriAspect'
	 * data. Note that argument vectors must be allocated by 
	 * the calling routine and should be empty.
	 * @param p PackData
	 * @param asps TriAspcet[]
	 * @param sideStarts Vector<Integer) (must be allocated)
	 * @param cornerZs Vector<Complex> (can be null)
	 * @param sideArgs Vector<Double> (can be null)
	 * @param sideMobs Vector<Mobius>, SidePair Mobius transformations (can be null)
	 * @return index of corner vert if there is only one
	 *  (as obtained in 'torus4layout'), else return 0 or
	 *  -1 on error.
	 */
	public static int sideInfo(PackData p,TriAspect []asps,Vector<Integer> sideStarts,
			Vector<Complex> cornerZs,Vector<Double> sideArgs,Vector<Mobius> sideMobs) {
		
		if (p.getSidePairs()==null || p.getSidePairs().size()==0)
			throw new ParserException("side pairs not set or nonexistent");
		
		Iterator<SideDescription> sides=p.getSidePairs().iterator();
		SideDescription epair=null;
		int theCorner=-1;
		while (sides.hasNext()) {
				epair=sides.next();
				int vert=epair.sideFirstVert();
				if (theCorner==-1)
					theCorner=vert;
				else if (theCorner>0 && theCorner!=vert)
					theCorner=0;
				sideStarts.add(vert);
				
				if (cornerZs!=null || sideArgs!=null) {
					int findx=epair.startEdge.face;
					Face face=p.faces[findx];
					Complex z1=new Complex(asps[findx].getCenter(face.vertIndx(vert)));
					if (cornerZs!=null)
						cornerZs.add(z1);

					if (sideArgs!=null) {
						Complex z2=new Complex(asps[findx].getCenter((face.vertIndx(vert)+1)%3));
				
						// find change in argument along this side
						double argSum =z2.divide(z1).arg();
						RedEdge currRe=epair.startEdge;
						RedEdge nextRe=currRe.nextRed;
						while (currRe!=epair.endEdge) { 
							currRe=nextRe;
							nextRe=currRe.nextRed;
							face=p.faces[currRe.face];
							z1=new Complex(asps[currRe.face].getCenter(currRe.startIndex));
							z2=new Complex(asps[currRe.face].getCenter((currRe.startIndex+1)%3));
							argSum +=z2.divide(z1).arg();
						}
						sideArgs.add(argSum);
					}
				}
		} // end of 'while'
		
		return theCorner;
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
			for (int j=0;j<p.kData[v].num;j++) {
				int w=p.kData[v].flower[j];
				// if w>v and edge is interior
				if (w>v) {
					double prd=Math.abs(Math.log(Math.abs(edgeRatioError(p,aspects,new EdgeSimple(v,w)))));
					TLog_max=(prd>TLog_max) ? prd:TLog_max;
					TLog_err += prd*prd;
				}
			}
			
			// weak;
			double werr=Math.abs(Math.log(weakConError(p,aspects,v)));
			weak_err += werr*werr;
			weak_max=(werr>weak_max) ? werr:weak_max;
			
			// angle sum
			double ang=Math.abs(angSumTri(p,v,1.0,aspects)[0]-p.rData[v].aim);
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
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("affine","{a b}",null,"set face ratio data for torus, side pairing factors a, b"));
		cmdStruct.add(new CmdStruct("corners","v1 v2 v3 v4",null,"vertices at corners, v1,v2=bottom, v2,v3=right"));
		cmdStruct.add(new CmdStruct("affpack","{v..}",null,"run iterative affine packing method"));
		cmdStruct.add(new CmdStruct("afflayout",null,null,"layout a fundamental domain using computed ratios"));
		cmdStruct.add(new CmdStruct("set_labels","-[rzst] f..",null,"face label data using: -r = radii, -z = centers, -s= random"));
		cmdStruct.add(new CmdStruct("draw","-[cfB]flags",null,"faces, f, circles, c, both B, plus normal flags"));
		cmdStruct.add(new CmdStruct("set_screen",null,null,"set screen to get the full fundamental domain"));
		cmdStruct.add(new CmdStruct("log_radii",null,null,"write /tmp file with labels"));
		cmdStruct.add(new CmdStruct("status",null,null,"No flags? error norms: curvatures, strong consistency\n"+
				"With flags: return single vert info"));
		cmdStruct.add(new CmdStruct("set_eff",null,null,"Using centers, set packing rad to the 'effective' radii"));
		cmdStruct.add(new CmdStruct("ccode","-[cfe] -m m j..",null,"Color code faces, vertices, or edges, mode m"));
		cmdStruct.add(new CmdStruct("dTree",null,null,"Update the dual spanning tree, e.g., after edge flip."));
		cmdStruct.add(new CmdStruct("Lface",null,null,"draw faces using TriAspect centers, spanning tree"));
		cmdStruct.add(new CmdStruct("Ltree",null,null,"draw dual spanning tree using TriAspect centers"));
		cmdStruct.add(new CmdStruct("LinCircs",null,null,"Draw the incircles of the faces, using aspects 'center's"));
		cmdStruct.add(new CmdStruct("equiSides",null,null,"set 'sides' to 1; faces are equilateral"));
		cmdStruct.add(new CmdStruct("sideRif","v..",null,"Riffle by adjusting 'sides'"));
		cmdStruct.add(new CmdStruct("update","-[sl] f..",null,"Update: -s centers to sides; -l sides to labels"));
		cmdStruct.add(new CmdStruct("tT","m",null,"Experimental layouts: m=1 is latest, m>1 older"));
		cmdStruct.add(new CmdStruct("sI",null,null,"Side information: corners, angles, etc."));

	}
	
}
