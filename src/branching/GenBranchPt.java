package branching;

import java.util.ArrayList;
import java.util.Vector;

import allMains.CirclePack;
import canvasses.DisplayParser;
import circlePack.PackControl;
import complex.Complex;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RawDCEL;
import dcel.RedHEdge;
import exceptions.CombException;
import exceptions.PackingException;
import exceptions.ParserException;
import geometry.CommonMath;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.TriData;
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
	public int myIndex;	  // may be face index or vert index, depending on type
	double myAim;		  // target angle sum; e.g. 4*pi.
	PackData packData;    // who's your daddy?
	PackData myPackData;  // packing with complex local to this branch point.
	public HalfEdge lastLayoutEdge; // for combined layout process
	public HalfLink layoutAddons;  // adjoined to parent layout for this branch
	public HalfLink myHoloBorder;  // for local holonomy 
	Mobius myHolonomy;    // Can update this after each layout
	Mobius placeMe;       // Mobius that aligns layout with parent packing

	// ***** these are generated in each 'createMyPack'
	public VertexMap vertexMap;  // pairs (l n): local index l, parent index n
	public int matchCount;       // local verts 1 to matchCount match to parent; 
								 //   may have other aux verts
	public int []transData;		 // n=transData[i] positive means get data for 
								 //   i from n in parent; negative means put data
								 //   of i into n in parent (typically, radii). 
	// NOTE: 'myPackData.rData[]' points to 'packData.rData[]' area.
	
	// TODO: converting to dcel structure, build temporary parallel data 
	public EdgeLink parentPoison;   // traditional: edges of parent to become poison
	public HalfLink parentHPoison;  // migrate to this
	
	HalfEdge myAttachEdge;  // local, for aligning with parent
	HalfEdge parentAttachEdge;  // parent edge (same orientation)

	EventHorizon evtHorizon; 	// OBE: storage of bdry combinatorics
	public HalfLink eventHorizon;  // edges surrounding the face or vertex

	PackDCEL pdc; // convenience
	
	// Constructor
	public GenBranchPt(PackData p,int bID,FaceLink blink,double aim) {
		packData=p;
		pdc=p.packDCEL;
		branchID=bID;
		myAim=aim;
		placeMe=null;
		myHolonomy=null;
		myAttachEdge=null;
		parentAttachEdge=null;
		evtHorizon=null;
		lastLayoutEdge=null;
		layoutAddons=null;
	}
	
	// ************** abstract methods ******************
	
	// create the local packing based on type of branch point
	abstract PackData createMyPack();
	
	// prepare to delete by resetting any parent info
	abstract public void delete();
	
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
		
		// each type has its way of creating/modifying the packing
		myPackData=createMyPack();
		if (myPackData==null)
			throw new CombException(
					"Failed to create packing for general branch "+
							"point, type "+myType);
		
		// set 'myHoloBorder'
		HalfLink myred=new HalfLink();
		RedHEdge rtrace=myPackData.packDCEL.redChain;
		do {
			myred.add(rtrace.myEdge);
			rtrace=rtrace.nextRed;
		} while (rtrace!=myPackData.packDCEL.redChain);
		myHoloBorder=RawDCEL.leftsideLink(myPackData.packDCEL,myred);
		
		// create horizon data
		evtHorizon=new EventHorizon();
	}

	/**
	 * Event horizon vertices (mostly interior) have neighbors 
	 * inside the branch points and outside in the parent. We use
	 * an old-reliable riffle, but must compute angle sums 
	 * in a special way based on 'EventHorizon' structure
	 * @param passes int 
	 * @return UtilPacket, value is cummulative angle sum error
	 */
	public UtilPacket horizonRiffle(int passes) {
		UtilPacket uP=new UtilPacket();
		int count=0;
		boolean flip=false; // if error goes up
		EventHorizon ev=evtHorizon;
		double faim=pi2; // all have aim 2pi
		double origError=ev.angleSumError();
		double curvError=origError;
				
			
		double recip=.333333/ev.hitCount;
	    double cut=curvError*recip;
		while ((cut > rePack.RePacker.RP_TOLER && count < passes)) {
		    double c1=0.0;
		    
			// make one adjustment at each adjustable vertex
			for (int j = 0; j < ev.hitCount; j++) {
				double r = ev.getRadius(j);
				int N = 2 * (ev.innerTriData[j].length + ev.outerTriData[j].length);

				if (packData.hes > 0) { // no sph algorithm yet
					throw new ParserException("for general branching, no sph algorithm yet");
				}

				if (packData.hes<0) { // hyp uniform model
					// Remember, we use squared s-radius here
					double x_rad = ev.getRadius(j);
					r = 1.0 - x_rad;
					if (r >= 1.0)
						r = 0;
					double sr = Math.sqrt(r);

					// compute anglesum: ?????
					double fbest = ev.getAngleSum(j, x_rad);

					// set up for model
					double del = Math.sin(faim / N);
					double bet = Math.sin(fbest / N);
					double r2 = (bet - sr) / (bet * r - sr); // reference radius
					if (r2 > 0) { // calc new label
						double t1 = 1 - r2;
						double t2 = 2 * del;
						double t3 = t2 / (Math.sqrt(t1 * t1 + t2 * t2 * r2) + t1);
						r2 = t3 * t3;
					} else
						r2 = del * del; // use lower limit
					ev.setVertRadius(j, 1.0 - r2); // store new x-radii
					fbest = ev.getAngleSum(j, 1.0 - r2);
					fbest -= faim;
					c1 += fbest * fbest; // accumulate error
				} // end of hyp case
				else { // eucl case
					double fbest = ev.getAngleSum(j, ev.getRadius(j));

					// use the model to predict the next value
					double del = Math.sin(faim / N);
					double bet = Math.sin(fbest / N);
					double r2 = r * bet * (1 - del) / (del * (1 - bet));
					// store as new radius label
					if (r2 < 0)
						throw new PackingException();
					ev.setVertRadius(j, r2);
					fbest = ev.getAngleSum(j, r2);
					fbest -= faim;
					c1 += fbest * fbest; // accumulate error
				}
			} // done with all vertices
			c1=Math.sqrt(c1); // l2-error

			double newError=ev.angleSumError();
			if (newError>curvError)
				flip=true;
			curvError=newError;
			cut = curvError * recip;
			count++;
			
			boolean debug=false;
			if (debug) // debug=true;
				System.out.println("repack count = "+count+"; error at "+curvError);
		} // end of while

		// store horizon radii in both parent and branch point
		for (int j=0;j<ev.hitCount;j++) {
			int v=ev.innerAdjust[j];
			int V=ev.outerAdjust[j];
			double r=ev.getRadius(j);
			myPackData.setRadius(v,r);
			packData.setRadius(V, r);
		}

		curvError=ev.angleSumError();
		uP.value=curvError;
		if (flip) {
			uP.rtnFlag=-1; // indicates error not monotone
		}
		uP.rtnFlag=1;
		return uP;
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
	 * @return double, <0 on problem.
	 */ 
	public double myHolonomyError() {
		myPackData.packDCEL.layoutPacking();
		myHolonomy=PackData.holonomyMobius(myPackData,myHoloBorder);
		return Mobius.frobeniusNorm(myHolonomy);
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
	 * Display on screen of parent packing. Typically overridden 
	 * by derived classes so tailored commands can be picked off.
	 * @param flagSegs flag sequences
	 * @return int count of display actions
	 */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		int n=DisplayParser.dispParse(myPackData,packData.cpScreen,flagSegs);
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
		return n;
	}
	
	/**
	 * Private inner class to hold combinatorics of event horizon, mainly
	 * for use in repacking. Note that "inner" refers to data from inside
	 * the branch region, "outer" refers to corresponding data from parent
	 * packing, and that their indexing is synchronized.
	 * @author kstephe2, 9/2021
	 *
	 */
	private class EventHorizon {
		
		// branch point red chain halfedges (cclw wrt the branch region)
		HalfLink innerHorizon;  
		HalfLink outerHorizon;  
		
		// horizon vertices with adjustable radii (typically all) 
		int hitCount=0; 
		int[] innerAdjust;
		int[] outerAdjust;
		
		// face data (radii/invDist) for faces incident to horizon;
		//   e.g., innerTriData[j] is list of inner faces incident 
		//   to vertex innerAdjust[j]. 
		TriData[][] innerTriData; 
		TriData[][] outerTriData; 
		
		// indexes into tridata: e.g. innerDexes[j] is a list of
		//    indices for v=innerAdjust[j] in innerTriData[j]
		public int[][] innerDexes;
		public int[][] outerDexes;
		
		// Constructor
		public EventHorizon() {
			
			// set up horizon 'HalfLink's
			innerHorizon=new HalfLink();
			outerHorizon=new HalfLink();
			RedHEdge rtrace=myPackData.packDCEL.redChain;
			do {
				HalfEdge he=rtrace.myEdge;
				innerHorizon.add(he); // cclw wrt branch region
				outerHorizon.add(packData.packDCEL.findHalfEdge(he)); // clw
				rtrace=rtrace.nextRed;
			} while (rtrace!=myPackData.packDCEL.redChain);

			ArrayList<EdgeSimple> vhits=new ArrayList<EdgeSimple>();
			for (int j=0;j<innerHorizon.size();j++) {
				HalfEdge he=innerHorizon.get(j);
				int v=he.origin.vertIndx;
				int V=vertexMap.findW(v); // parent's index
				if (!packData.packDCEL.vertices[V].isBdry())
					vhits.add(new EdgeSimple(v,V));
			}
			hitCount=vhits.size();
			innerAdjust=new int[hitCount];
			outerAdjust=new int[hitCount];
			innerTriData=new TriData[hitCount][];
			outerTriData=new TriData[hitCount][];
			innerDexes=new int[hitCount][];
			outerDexes=new int[hitCount][];
			
			// now go around the horizon to set up data
			int horizonCount=innerHorizon.size();
			int tick=0;
			for (int j=0;j<horizonCount;j++) {
				HalfEdge he=innerHorizon.get(j);
				HalfEdge hE=outerHorizon.get(j);
				if (hE.origin.isBdry())
					continue;
				int v=he.origin.vertIndx;
				int V=hE.origin.vertIndx;
				innerAdjust[tick]=v;
				outerAdjust[tick]=V;
				
				// get cclw list of inner face at v
				HalfEdge previnner=
						innerHorizon.get((j-1+horizonCount)%horizonCount).twin;
				ArrayList<Integer> innerFaces=new ArrayList<Integer>();
				HalfEdge edge=he;
				int safety=100;
				do {
					innerFaces.add(edge.face.faceIndx);
					edge=edge.prev.twin; // cclw
					safety--;
				} while (edge!=previnner && safety>0);

				// get cclw list of outer faces at V
				ArrayList<Integer> outerFaces=new ArrayList<Integer>();
				HalfEdge prevouter=
						outerHorizon.get((j-1+horizonCount)%horizonCount).twin;
				edge=prevouter;
				do {
					outerFaces.add(edge.face.faceIndx);
					edge=edge.prev.twin; // cclw
					safety--;
				} while (edge!=hE && safety>0);
				if (safety==0)
					throw new CombException("safety exit in branch pt angle sums");

				innerTriData[tick]=new TriData[innerFaces.size()];
				innerDexes[tick]=new int[innerFaces.size()];
				outerTriData[tick]=new TriData[outerFaces.size()];
				outerDexes[tick]=new int[outerFaces.size()];

				for (int k=0;k<innerFaces.size();k++) {
					int f=innerFaces.get(k);
					TriData trid=new TriData(myPackData.packDCEL,f);
					innerDexes[tick][k]=trid.vertIndex(v);
					innerTriData[tick][k]=trid;
				}
				for (int k=0;k<outerFaces.size();k++) {
					int f=outerFaces.get(k);
					TriData trid=new TriData(packData.packDCEL,f);
					outerDexes[tick][k]=trid.vertIndex(V);
					outerTriData[tick][k]=trid;
				}
				tick++;
			}
		}
		
		public void updateTriRadii() {
			for (int k=0;k<hitCount;k++) {
				TriData[] innerdata=innerTriData[k];
				TriData[] outerdata=outerTriData[k];
				for (int j=0;j<innerdata.length;j++)
					innerdata[j].uploadRadii();
				for (int j=0;j<outerdata.length;j++)
					outerdata[j].uploadRadii();
			}
		}
		
		/**
		 * Get full anglesum for vertex of index j using
		 * its stored radius.
		 * @param j int
		 * @return double
		 */
		public double getAngleSum(int j) {
			double rad=getRadius(j);
			return getAngleSum(j,rad);
		}
		
		/**
		 * Get full anglesum for vertex of index j if its
		 * radius is 'rad'. 
		 * @param j int
		 * @param rad double
		 * @return double
		 */
		public double getAngleSum(int j,double rad) {
//			if (j<0 || j>=hitCount)
//				throw new CombException("index not in [0,hitCount)");
			double innerAS=0;
			TriData[] tridata=innerTriData[j];
			for (int k=0;k<tridata.length;k++) {
				innerAS +=tridata[k].compOneAngle(innerDexes[j][k],rad);
			}
			double outerAS=0;
			tridata=outerTriData[j];
			for (int k=0;k<tridata.length;k++) {
				outerAS +=tridata[k].compOneAngle(outerDexes[j][k],rad);
			}
			return innerAS+outerAS;
		}
		
		/**
		 * get cumulative anglesum error at all adjustable vertices
		 * assuming aims are 2Pi.
		 * @return double
		 */
		public double angleSumError() {
			double err=0.0;
			for (int j=0;j<hitCount;j++)
				err +=Math.abs(getAngleSum(j)-pi2);
			return err;
		}
		
		/**
		 * get radius from first 'innerTriData' associated with 
		 * v=innerAdjust[j]. 
		 * @param j int
		 * @return double, 
		 */
		public double getRadius(int j) {
			return innerTriData[j][0].getRadius(innerDexes[j][0]);
		}
		
		/**
		 * Adjust radius for jth vertex in every inner/outer 
		 * TriData for which it is a vertex.
		 * @param j int
		 * @param r double
		 */
		public void setVertRadius(int j,double r) {
			for (int k=0;k<hitCount;k++) {
				TriData[] tridata=innerTriData[k];
				int v=innerAdjust[j];
				for (int m=0;m<tridata.length;m++) 
					tridata[m].setVertRadius(v, r);
				tridata=outerTriData[j];
				int V=outerAdjust[j];
				for (int m=0;m<tridata.length;m++) 
					tridata[m].setVertRadius(V, r);
			}
		}
		
	} // end of inner class EventHorizon
}
