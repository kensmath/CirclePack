package branching;

import java.util.Vector;

import allMains.CirclePack;
import canvasses.DisplayParser;
import circlePack.PackControl;
import complex.Complex;
import dcel.HalfEdge;
import exceptions.CombException;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.UtilPacket;

/**
 * Parent class for various types of "generalized branch points". Within
 * a complex, a generalized branch point is a simply connected subcomplex
 * on whose interior circles we impose some type of local behavior having
 * a global effect like that of a classical branch point. The "local
 * structure" varies. The "boundary chain" of the subcomplex is the oriented
 * chain of boundary faces, which may be closed or not (but we'll assume it 
 * is one connected list). 
 * 
 * Normally with closed boundary, the goal of the local repack process 
 * is to make the holonomy on the boundary vanish. This may involve various
 * adjustments to combinatorics, overlaps, etc. in the internal structure.
 * The outside vertices along 'bdryLink' are put in the @see NodeLink
 * 'joinVerts'. These are ones shared by 'myPackData' and the parent 
 * 'packData'. (If 'bdryLink' is closed, these are the boundary vertices 
 * of 'myPackData'.) These are important: they are not adjusted in the 
 * local repack computations, but rather in the repack of the parent, 
 * after which they must be updated in the local packing. So the 
 * global repacking requires bouncing back and forth between local 
 * and parent repack cycles.
 * 
 * For example:
 * 
 * (1) For a "traditional" discrete branch point v, the subcomplex is
 * just it flower and an angle sum condition is placed at the center v,
 * e.g. aim = 4 pi. 
 * 
 * (2) For "singular" and "fractured" branching the subcomplex consists of
 * a face {v,u,w} and the chain of faces surrounding it; the structure 
 * involves distributing an extra 2 pi angle sum among v, u, and w and/or
 * distributing overlaps on the edges of the face.
 * 
 * (3) A "quad" branch point involves fracturing two contiguous faces.
 * 
 * (4) For "shift" branching at v, the subcomplex is the flower augmented
 * with one more row of faces. In one form, the structure involves 
 * associating two 'sister' circles with v and controlling their radii 
 * ratio and/or where petals start. A second form uses chaperone 
 * circles to accomplish similar structures.
 * 
 * In all cases there is basic functionality that has to be defined
 * and implemented: get/set control parameters, riffle, normalized
 * layout procedure, Mobius to align with parent, error and holonomy,
 * display actions, etc.
 * 
 * For now, all local branch computations will be eucl or hyp and
 * layouts will be in normalized positions.
 * 
 * @author kstephe2
 *
 */
public abstract class GenBranchPt {

	static final double RIFF_TOLER=.00000001;
	static final double pi2=Math.PI*2.0;
	
	// branch type
	public static final int TRADITIONAL=1;
	public static final int FRACTURED=2;
	public static final int QUADFACE=3;
	public static final int SINGULAR=4; // singular face, using chaperones
	public static final int SHIFTED=5;  // shifted, using ratio/phase parameters
	public static final int CHAPERONE=6; // shifted, using chaperone circles
	public int myType;	  // type (see above)
	
	public int branchID;  // id number, starting from 0 as created by parent
	PackData packData;    // who's your daddy?
	PackData myPackData;  // packing with complex local to this branch point.
	double myAim;		  // target angle sum; e.g. 4*pi.
	Mobius myHolonomy;    // Can update this after each layout
	Mobius placeMe;       // Mobius that aligns layout with parent packing
	public int myIndex;	  // may be face index or vert index, depending on type

	// ***** these are generated in each 'createMyPack'
	public VertexMap vertexMap;  // pairs (l n): local index l, parent index n
	public int matchCount;       // local verts 1 to matchCount match to parent; 
								 //   may have other aux verts
	public int []transData;		 // n=transData[i] positive means get data for 
								 //   i from n in parent; negative means put data
								 //   of i into n in parent (typically, radii). 
	// NOTE: 'myPackData.rData[]' points to 'packData.rData[]' area.
	
	// TODO: converting to dcel structure, build temporary parallel data 
	GraphLink myLayoutTree; // ordered list of dual edges for layout 
	public FaceLink bdryLink;    // oriented bdry face chain (parent indices)
	public EdgeLink parentPoison;   // edges of parent to become poison
	
	HalfLink myLinkTree;  // local layout tree
	HalfEdge myAttachEdge;  // local, for aligning with parent
	HalfEdge parentAttachEdge;  // parent edge (same orientation)
	
	// Constructor
	public GenBranchPt(PackData p,int bID,FaceLink blink,double aim) {
		packData=p;
		branchID=bID;
		bdryLink=blink;
		myAim=aim;
		placeMe=null;
		myHolonomy=null;
		myAttachEdge=null;
		parentAttachEdge=null;
	}
	
	// ************** abstract methods ******************
	
	// create the local packing based on type of branch point
	abstract PackData createMyPack();
	
	// prepare to delete by resetting any parent info
	abstract public void delete();
	
	// set parent edges as poison to be used in layout
	abstract public int setPoisonEdges();
	
	// compute local radii; return remaining error.
	abstract public UtilPacket riffleMe(int cycles);
	
	// compute isolated local layout (also updates myHolonomy)
	abstract public double layout(boolean norm);
	
	// place circles not in the parent
	abstract public int placeMyCircles();
	
	// current
	abstract public double currentError();
	
	// report existence and main data
	abstract public String reportExistence();
	
	// report status: id, vert/face, holonomy error, etc.
	abstract public String reportStatus();

	// report parameters in String
	abstract public String getParameters();

	// set parameters (list depends on branch type)
	abstract public int setParameters(Vector<Vector<String>> flagSegs);
	
	// ************************************************
	
	/**
	 * This invokes creation of myPackData 
	 */
	public void initLocalPacking() {
		
		// each type has its way of creating the packing
		myPackData=createMyPack();
		if (myPackData==null)
			throw new CombException(
					"Failed to create packing for general branch "+
							"point, type "+myType);
		myPackData.fillcurves();
	}

	/**
	 * Set attachment edges for aligning this branch point
	 * with the parent. 
	 * @param edge HalfEdge: if improper, then clear current values
	 */
	public void setAttachments(HalfEdge edge) {
		if (edge==null) {
			myAttachEdge=null;
			parentAttachEdge=null;
			return;
		}
		if (myAttachEdge.myRedEdge==null)
			throw new CombException(
					"attachment edge "+edge+" is not in local red chain ");
		parentAttachEdge=packData.packDCEL.findHalfEdge(new EdgeSimple(myAttachEdge));
		if (parentAttachEdge==null)
			throw new CombException(
					"local attachment edge "+edge+" not found in parent"); 
	}
	
	/**
	 * For the first 'matchCount' vertices, update parent radii from 
	 * local radii.
	 * @return int count, 0 on error
	 */
	public int sendMyRadii() {
		int count=0;
		for (int v=1;v<=this.matchCount;v++) {
			int V=vertexMap.findW(v);
			packData.setRadius(V,myPackData.getRadius(v));
			count++;
		}
		return count;
	}

	/**
	 * For the first 'matchCount' vertices, update parent 
	 * centers from local centers
	 * @return int count, 0 on error
	 */
	public int sendMyCenters() {
		int count=0;
		for (int v=1;v<=this.matchCount;v++) {
			int V=vertexMap.findW(v);
			packData.setCenter(V,new Complex(myPackData.getCenter(v)));
			count++;
		}
		return count;
	}

	/**
	 * Given parent vertex w, return corresponding local index
	 * @param w parent index
	 * @return local index, 0 on failure (e.g., not a local vertex)
	 */
	public int getMyIndex(int w) {
		return vertexMap.findV(w);
	}

	/**
	 * Given local vertex v, return corresponding parent index
	 * @param v my index
	 * @return parent index, 0 on failure (e.g., none exists)
	 */
	public int getParentIndex(int v) {
		return vertexMap.findW(v);
	}
	
	/**
	 * error in holonomy is the Frobenius norm of M-I,
	 * where M is the holonomy with det(M)=1. This is
	 * square root of sum of squares of absolute values
	 * of the entries of M-I; how close to the identity
	 */
	public double myHolonomyError() {
		return (Mobius.frobeniusNorm(myHolonomy));
	}
	
	/**
	 * Return latest holonomy (usually updated after layout)
	 * @return
	 */
	public Mobius getHolonomy() {
		return myHolonomy;
	}
	
	/**
	 * Return the local packing
	 * @return PackData
	 */
	public PackData getMyPackData() {
		return myPackData;
	}
	
	/**
	 * Return the l^2 anglesum error for this branch point
	 * @return double,
	 */
	public double getAngSumError() {
		return myPackData.angSumError();
	}
		
	/**
	 * Apply given Mobius transformation (assumed appropriate 
	 * to the geometry) to any local circles which belong to 
	 * the branch point but not to the parent.
	 * (Note: SHIFTED overrides this method, must plot vert 0.)
	 * @param mob @see Mobius
	 */
	public void applyMob(Mobius mob) {
		if (myPackData.nodeCount>matchCount)
			for (int v=(matchCount+1);v<=myPackData.nodeCount;v++)
				myPackData.setCenter(v,mob.apply(myPackData.getCenter(v)));
	}
	
	/**
	 * Find two 'joinVerts' to use in aligning this branch point with 
	 * the parent. Use first of 'joinVerts', then using its
	 * local center, find second of 'joinVerts' whose center is
	 * farthest away. Note this may change as layout changes. 
	 * (Want to use locations versus combinatorics because, e.g.
	 * branching may put combinatorially distance points close
	 * together. This has the disadvantage that the choice may
	 * change as the local layout changes.)
	 * @return int[2], local vertex indices
	 */
/*	public int []choose2RefPts() {
		if (joinVerts==null || joinVerts.size()<2)
			throw new CombException("joinVerts is in error or too small");
		double dist=0.0;
		Iterator<Integer> jv=joinVerts.iterator();
		int v1=jv.next();
		Complex z1=myPackData.rData[v1].center;
		int v2=-1;
		while (jv.hasNext()) {
			int v=jv.next();
			Complex z2=myPackData.rData[v].center;
			double abs12=z1.minus(z2).abs();
			if (abs12>dist) {
				v2=v;
				dist=abs12;
			}
		}
		if (v2<0)
			throw new ParserException("choose2RefPts failed.");
		int []ans=new int[2];
		ans[0]=v1;
		ans[1]=v2;
		return ans;
	}
*/
	
	/**
	 * Find the @see Mobius which best maps two local bdry 
	 * vertices (@see choose2RefPts) to the corresponding 
	 * vertices in the parent.
	 * @return Mobius, null in spherical case 
	 */
/*	public Mobius match2Pts() {
		if (myPackData.hes>0)
			return null;
		int []refPts=choose2RefPts();
		Complex a=myPackData.rData[refPts[0]].center;
		Complex b=myPackData.rData[refPts[1]].center;
		Complex A=packData.rData[vertexMap.findW(refPts[0])].center;
		Complex B=packData.rData[vertexMap.findW(refPts[1])].center;
		if (myPackData.hes<0) 
			return Mobius.auto_abAB(a,b,A,B);
		else 
			return Mobius.affine_mob(a,b,A,B);
	} */
	
	/**
	 * Compute and apply a Mobius to 'myPackData' that aligns it with
	 * the parent. Assume the local 'myPackData' has been laid out, we
	 * just want to move it. Two possible methods, depending on how 
	 * complicated the grand layout of the parent is.
	 * @param mode int: 
	 *   1=try 'match2Pts' (currently disabled), 
	 *   2=use 'layoutFace' (default)
	 * @return int, -1 on error
	 */
	public int alignMe(int mode) {
		int count=0;

/*		
		// use 'match2Pts' (should be more accurate)
		if (mode==1) {
			Mobius mob=match2Pts();
			if (mob==null) 
				return 0;
			for (int v=0;v<=myPackData.nodeCount;v++) {
				myPackData.rData[v].center=
					mob.apply(myPackData.rData[v].center);
				count++;
			}
			return count;
		}
*/		
		// else, attach using 'attachEdge'
		if (myAttachEdge==null || parentAttachEdge==null) {
			CirclePack.cpb.errMsg(
					"error in edge attachments edges for branch point "+branchID);
			return -1;
		}
		
		// local centers
		Complex a=myPackData.packDCEL.getVertCenter(myAttachEdge);
		Complex b=myPackData.packDCEL.getVertCenter(myAttachEdge.next);
		
		// parent centers
		Complex A=packData.packDCEL.getVertCenter(parentAttachEdge);
		Complex B=packData.packDCEL.getVertCenter(parentAttachEdge.next);
		
		// compute/apply mobius
		Mobius mb=null;
		if (myPackData.hes<0) 
			mb= Mobius.auto_abAB(a,b,A,B);
		else 
			mb =Mobius.affine_mob(a,b,A,B);
		for (int v=1;v<=myPackData.nodeCount;v++) {
			myPackData.setCenter(v,mb.apply(myPackData.getCenter(v)));
			count++;
		}
		return count;
	}
	
	/**
	 * Distance between points (as complex numbers) in given geometry
	 * @param z Complex
	 * @param w Complex
	 * @return double distance
	 */
	public double genPtDist(Complex z, Complex w) {
		return CommonMath.get_pt_dist(z, w,myPackData.hes);
	}
	
	/**
	 * Display on screen of parent packing. May be overridden by classes.
	 * @param flagSegs flag sequences
	 * @return int count of display actions
	 */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		int n=DisplayParser.dispParse(myPackData,packData.cpScreen,flagSegs);
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
		return n;
	}
}
