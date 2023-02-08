package random;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import JNI.DelaunayData;
import JNI.ProcessDelaunay;
import allMains.CirclePack;
import combinatorics.komplex.Face;
import complex.Complex;
import dcel.CombDCEL;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import geometry.SphericalMath;
import komplex.EdgeSimple;
import komplex.Triangulation;
import listManip.EdgeLink;
import math.Point3D;
import packing.PackData;
import packing.TorusData;
import util.PathUtil;
import util.StringUtil;
import util.UtilPacket;

public class RandomTriangulation {
	public static boolean setSeed=false; // for debugging, set seed=(long)1
	
	/**
	 * Distribute M random ordered points along (a polygonal approximation 
	 * to) the given Path2D.Double, return a Complex vector of the points, 
	 * or null on error.
	 * 
	 * Points start with path(0). Choose M consecutive random values from
	 * uniform distribution on [0,1], accumulate the lengths and scale to 
	 * length of gpath. (The Mth point is not used, but ensures random 
	 * spacing with regard to the path's endpoint.)
	 * 
	 * Note: 'arclength' along gpath is used for positioning the Complex 
	 * points; it starts with index 0 and last point is index M-1.
	 * 	
	 * @param gpath
	 * @param M
	 * @param sS
	 * @return Comple[]
	 * @throws DataException
	 */
	public static Complex[] rand_bdry_pts(Path2D.Double gpath,
			int M,boolean sS)
			throws DataException {
		setSeed=sS;
		// need length and polynomial approx of Gamma (should be 
		//    just one component)
		double flatness = PathUtil.gpExtent(gpath) * PathUtil.FLAT_FACTOR;
		double length = PathUtil.gpLength(gpath, flatness);
		Vector<Complex> poly;
		Vector<Vector<Complex>> polyGamma = PathUtil.gpPolygon(gpath,
				flatness);
		try {
			poly = (Vector<Complex>) polyGamma.get(0);
		} catch (Exception ex) {
			throw new DataException("problem with specified path");
		}
		if (gpath == null || M < 2)
			throw new DataException("Problem: path empty or too short");

		// find M random ordered arclength param spots in [0,length]
		Random CPrand = new Random();
		if (setSeed) // for debugging
			CPrand.setSeed((long)1);
		double[] arc_spots = new double[M];
		for (int j = 0; j < M; j++) {
			arc_spots[j] = CPrand.nextDouble()*length;
		}
		Arrays.sort(arc_spots); // put in increasing order

		// convert by interpolation to Complex points on polygonal 'path'
		double last_length = 0.0;
		double spot;
		Complex[] bdry_pts = new Complex[M];

		Iterator<Complex> gamma = poly.iterator();
		Complex initPt = new Complex((Complex) gamma.next());
		Complex nextPt = new Complex((Complex) gamma.next());
		double next_length = nextPt.minus(initPt).abs();
		int indx = 0;
		while (indx < M) {
			spot = arc_spots[indx];
			while (gamma.hasNext() && spot > next_length) {
				initPt = nextPt;
				nextPt = (Complex) gamma.next();
				last_length = next_length;
				next_length += nextPt.minus(initPt).abs();
			}
			if (spot <= next_length) { // interpolate x,y
				double t = (spot - last_length) / (next_length - last_length);
				bdry_pts[indx] = nextPt.times(t).add(initPt.times(1 - t));
				indx++;
			} else {
				throw new DataException();
			}
		}
		return bdry_pts;
	}

	/**
	 * This code generates random triangulations of various regions/types. 
	 * Currently allow rectangles, 1-tori, regions bounded by curves, or
	 * determined by geometry. 
	 *
	 * Want to convert these to packings, keeping not only the 
	 * combinatorics, but also the centers, so that we can generate 
	 * images, do mappings, etc, based on the triangulation itself. 
	 * Therefore, it is up to the user to apply further manipulations to the resulting packing. 
	 *
	 * Arguments (parsed from command in calling routine):
	 * 
	 * (1) N number of interior vertices: in non-tori case, 4*sqrt(N) 
	 *   additional vertices are added to the boundary of the region or 
	 *   rectangle. If N<12 then set N=12. 
	 *
	 * (2) debug: if true, then don't call random seed (though it may
	 *   have been called elsewhere in CirclePack).
	 *
	 * (3) hes: geometry: if >0, we triangulate the sphere; choose 
	 *   points randomly in [0 1]x[0 1] and use theta = 2*Pi*x
	 *   and phi=acos(2*y-1)
	 *
	 * (4) Aspect: Random triangulation of rectangle of this aspect; this
	 *   takes precedence over hex>0. If Aspect negative, use only interior
	 *   points, do NOT create additional bdry points.
	 *
	 * (5) Gamma: Pathlist defining region.(if non-NULL, takes precedence 
	 *   over Aspect. Can be on sphere, but don't yet know how
	 * 	 (but Gamma always given in 2D cartesian coordinates).
	 *
	 * (6) Tau: random triangulation of torus with complex modulus Tau. 
	 * 	 If z is non-zero, this takes precedence over hes, Gamma, and 
	 *   Aspect.
	 *
	 * @param N int; minimum of 12 points
	 * @param debug_flag boolean, set to get debug info
	 * @param hes int, geometry
	 * @param Aspect double
	 * @param Gamma Path2D.Double
	 * @param Tau Complex
	 * @return 'PackData' or null on error
	 */
	public static Triangulation random_Triangulation(int N,
			boolean debug_flag,int hes,
			double Aspect,Path2D.Double Gamma,Complex Tau) {
		Vector<Complex> Z_list=new Vector<Complex>(0);
		Vector<Complex> Z_bdry=new Vector<Complex>(0);
		DelaunayData dData=null;	  
		Random random=new Random();
		if (debug_flag) 
			random.setSeed(1); // specific seed only when debugging
		if (N<12) 
			N=12;
	  
		// check for appropriate 'hes'
		if (Tau!=null || Gamma!=null || Aspect!=0.0) {
			hes=0;
		}
	  
	  // ****** handle sphere first ***********
	  // we can only do triangulation of full sphere --- 
	  //   can't yet handle subregions. By Archimedes, 
	  //   randomize with respect to spherical area by 
	  //   choosing theta uniformly in [0,2pi) and z uniformly 
	  //   in [-1,1] to give phi=acos(z).
	  if (hes>0) {
		  Z_bdry=null;
		  if (debug_flag) random.setSeed(1); // for debugging
		  for (int j=1;j<=N;j++) {
			  Z_list.add(new Complex(2*Math.PI*random.nextDouble(),
					  Math.acos(2.0*random.nextDouble()-1.0)));
		  }
		  dData=new DelaunayData(hes,Z_list,null);
		  try {
			  ProcessDelaunay.sphDelaunay(dData);
		  } catch (Exception ex) {
			  CirclePack.cpb.errMsg(
					  "'ProcessDelaunay' exception for sphere: "+ex.getMessage());
			  return null;
		  }
		  return dData.getTriangulation();
	  }

	  // ******* other cases: setup depending on situation *****
	  boolean torus_flag=false;
	  boolean noBdryPts=false;
	  // usual number of added verts on bdry, if needed
	  int bdryN=(int)(4.0*Math.sqrt((double)N)); 
	  double Asp_x,Asp_y;
	  Complex scaled_Tau=new Complex(1.0);
	  double scaled_1=1.0;
	  AffineTransform atrans=new AffineTransform();

	  // torus is highest precedence
	  if (Tau!=null) { 
		  if (Tau.y<=0.0)
			  throw new DataException("Tau must be in upper half plane");
		  torus_flag=true;
		  Gamma=null;
		  
		  // First, normalize: Want Tau to be in the fundamental 
		  //   domain for the moduli space of 1-tori: hence, its
		  //   real part is between -.5 and .5, and outside the 
		  //   unit disc.
		  Tau=TorusData.Teich2Tau(Tau);
		  
		  // create Gamma as fundamental parallelogram in unit disc;
		  //   translate/scale so center is at origin, result is
		  //   <c1, c2, c3, c4> cclw from upper left corner.
		  Complex htau=new Complex(Tau.x*.5,Tau.y*.5);
		  Complex hf=new Complex(.5);
		  Complex c1=htau.minus(hf);
		  Complex c4=htau.add(hf);
		  Complex c3=hf.minus(htau);
		  Complex c2=new Complex(-c4.x,-c4.y);
		  
		  // scale to put in unit disc
		  double maxdist=c1.abs();
		  maxdist=(c2.abs()>maxdist) ? c2.abs() : maxdist;
		  c1=c1.divide(maxdist);
		  c2=c2.divide(maxdist);
		  c3=c3.divide(maxdist);
		  c4=c4.divide(maxdist);
		  scaled_Tau=Tau.divide(maxdist);
		  scaled_1=1.0/maxdist;

		  Gamma=new Path2D.Double();
		  Gamma.moveTo(c1.x,c1.y);
		  Gamma.lineTo(c2.x,c2.y);
		  Gamma.lineTo(c3.x,c3.y);
		  Gamma.lineTo(c4.x,c4.y);
		  Gamma.closePath();
		  bdryN=8*N; // will be replicated to get 9 copies
	  }
	  // Gamma case is next: Reset default affine transform to 
	  //   later map random points of unit disc to random points 
	  //   in disc centered at bounding box of Gamma containing 
	  //   Gamma.
	  else if (Gamma!=null) {
		      double flatness=PathUtil.gpExtent(Gamma)*PathUtil.FLAT_FACTOR;
		      double []cr=PathUtil.gpCentRad(Gamma,flatness);
		      atrans= new AffineTransform(cr[2],0.0,0.0,cr[2],cr[0],cr[1]);
	  }
	  
	  // not torus, not path, not sphere: want rectangle; 
	  else { 
		  hes=0;
		  if (Aspect<0.0) { // do without bdry
			  bdryN=0;
			  noBdryPts=true;
			  Aspect=-1.0*Aspect; 
		  }

		  // define associated cclw Gamma in unit disc, centered 
		  //    at origin, starting at upper left
		  double a2=Aspect*Aspect;
		  Asp_x=Math.sqrt((a2)/(a2+1.0));
		  Asp_y=Math.sqrt(1.0/(a2+1.0));
		  Gamma=new Path2D.Double();
		  Gamma.moveTo(-Asp_x,Asp_y);
		  Gamma.lineTo(-Asp_x,-Asp_y);
		  Gamma.lineTo(Asp_x,-Asp_y);
		  Gamma.lineTo(Asp_x,Asp_y);
		  Gamma.closePath();
	  }

	  // ============= create random points
	  
	  // add bdryN random points ON Gamma
	  if (!torus_flag && Gamma!=null && !noBdryPts) { 
		  Complex []bdry_points=null;
	      try {
	    	  bdry_points=RandomTriangulation.rand_bdry_pts(Gamma,bdryN,debug_flag);
	      } catch(DataException dex) {
	    	  CirclePack.cpb.myErrorMsg("Random triangulation: error in "+
			     "placing random points on region boundary.");
	    	  return null;
	      }
	      bdryN=bdry_points.length;
	      for (int i=0;i<bdryN;i++) {
	    	  Z_bdry.add(bdry_points[i]);
	      }
	      bdryN=Z_bdry.size(); // these should already be equal
	  }
	  
	  // torus case has generated Gamma without poknts on Gamma itself.
	  // Note: 'atrans' should be set whether Gamma was provided or created.
	  if (Gamma!=null) { 

		  // Create N random points IN region
		  int safety=0;
		  int hits=1;
		  Random CPrand=new Random();
		  if (debug_flag) 
			  CPrand.setSeed(1); // for debugging
		  Point2D.Double pt,apt;
		  while (hits<=N && safety < 20*N) {
			  Complex z=new Complex(2.0*CPrand.nextDouble()-1.0,2.0*CPrand.nextDouble()-1.0);
			  pt=new Point2D.Double(z.x,z.y);
			  apt=(Point2D.Double)atrans.transform(pt,null);
			  if (Gamma.contains(apt.x,apt.y)) { // does Gamma contain transformed point? 
				  Z_list.add(z);
				  hits++;
			  }
			  safety++;
		  }
		  if (hits<=N) {
			  CirclePack.cpb.myErrorMsg("rand_tri: failed to get "+N+" points inside Gamma.");
			  return null;
		  }
	  }
	  
	  // for torus, we replicate to get 9 copies of the original points;
	  // translated points have indices differing by 0(mod N).
	  if (torus_flag) { 
		  Z_bdry=null;
		  // now translate a copy left by scaled_1 
		  for (int i=1;i<=N;i++) {
			  Complex z=Z_list.get(i-1);
			  Z_list.add(new Complex(z.x-scaled_1,z.y));
		  }
		  // and right by scaled_1 
		  for (int i=1;i<=N;i++) {
			  Complex z=Z_list.get(i-1);
			  Z_list.add(new Complex(z.x+scaled_1,z.y));
		  }
		  // now translate all three copies up by scaled_Tau
		  for (int i=1;i<=3*N;i++) {
			  Complex z=Z_list.get(i-1);
			  Z_list.add(z.add(scaled_Tau));
		  }
		  // and down by scaled_Tau
		  for (int i=1;i<=3*N;i++) {
			  Complex z=Z_list.get(i-1);
			  Z_list.add(z.minus(scaled_Tau));
		  }
	  }
	  
	  // interior points indexed from 1 to intCount; any bdry
	  //   points indexed from iCount+1
	  int iCount=Z_list.size();
	  EdgeLink elink=null;
	  if (Z_bdry!=null && Z_bdry.size()>0) {
		  elink=new EdgeLink();
		  Iterator<Complex> zb=Z_bdry.iterator();
		  Complex w=zb.next();
		  Z_list.add(w);
		  int tickv=iCount+1;
		  while (zb.hasNext()) {
			  Complex z=zb.next();
			  Z_list.add(z);
			  elink.add(new EdgeSimple(tickv,++tickv));
		  }
		  // have to close up
		  elink.add(new EdgeSimple(tickv,iCount+1));
	  }

	  // ============= create dData and the triangulation
	  try {
		  dData=new DelaunayData(hes,Z_list,elink);
		  ProcessDelaunay.planeDelaunay(dData);
	  } catch (Exception ex) {
		  CirclePack.cpb.errMsg(
				  "'ProcessDelaunay' exception in planeDelaunay: "+ex.getMessage());
		  return null;
	  }
	  
	  Triangulation Tri = dData.getTriangulation();
	  
	  // For torus, remove triangles involving only added vertices 
	  //   (i.e., all indices > N). Leave other faces, even though
	  //   this may leave some boundary vertices with no interior face.
	  int tick=0;
	  if (torus_flag) {
		  for (int i=1;i<=Tri.faceCount;i++) {
			  if (Tri.faces[i].vert[0]>N // all added vertices
					  && Tri.faces[i].vert[1]>N 
					  && Tri.faces[i].vert[2]>N) 
				  tick++; 
		  }
		  // found some? create new faces without them.
		  if (tick>0) {
			  tick=0;
			  Face []newfaces=new Face[Tri.faceCount+1];
			  for (int i=1;i<=Tri.faceCount;i++) {
				  if (Tri.faces[i].vert[0]>N && Tri.faces[i].vert[1]>N 
						  && Tri.faces[i].vert[2]>N) 
					  tick++;
				  else 
					  newfaces[i-tick]=Tri.faces[i];
			  }
			  
			  Point3D []old_nodes=Tri.nodes;
			  int facecount=Tri.faceCount;
			  Tri=new Triangulation();
			  Tri.faceCount=facecount-tick;
			  Tri.faces=newfaces;
			  // Now, must also reindex verts mod(N). Redundancies okay because
			  //   they should be ignored by 'tri_to_pack' 
			  int v;
			  for (int i=1;i<=Tri.faceCount;i++) {
				  for (int j=0;j<3;j++)
					  if ((v=Tri.faces[i].vert[j])>N)
						  Tri.faces[i].vert[j]=1+(v-1)%N;
			  }
			  
			  // throw away redundant point locations
			  Point3D []newNodes=new Point3D[N+1];
			  for (int i=1;i<=N;i++) {
				  newNodes[i]=new Point3D(old_nodes[i]);
			  }
			  Tri.nodes=newNodes;
		  }
	  } // end of torus adjustment

	  return Tri;
	}

	/**
	 * Given a polygon (non-closed list of corners), use a Poisson 
	 * Point process to choose N points inside, and use these along 
	 * with the given vertices defining the polygon (no additional 
	 * vertices added along the boundary) to form a Delaunay 
	 * triangulation. 
	 * @param N int, N>=4
	 * @param sS boolean, setSeed flag, not used yet
	 * @param poly Complex[], list of corners, not closed (close it here)
	 * @return Triangulation or null on error
	 */
	public static Triangulation randomPolyPts(int N,boolean sS,Complex[] poly) {
		setSeed=sS; // not sure this does anything yet
		if (N<4 || poly==null || poly.length<3) 
			return null;
		
		Vector<Complex> Z_list=new Vector<Complex>();
		
		// create Path2D.Double and also store
		Path2D.Double polypath=new Path2D.Double();
		polypath.moveTo(poly[0].x,poly[0].y);
		for (int j=1;j<poly.length;j++) {
			polypath.lineTo(poly[j].x,poly[j].y);
		}
		polypath.closePath();
		
		// get N points inside
		/* Reset default affine transform to map random 
		 * points of unit disc to random points in disc centered at bounding
	  	 * box of polypath and containing polypath.*/
		double flatness=PathUtil.gpExtent(polypath)*PathUtil.FLAT_FACTOR;
		double []cr=PathUtil.gpCentRad(polypath,flatness);
		AffineTransform atrans= new AffineTransform(cr[2],0.0,0.0,cr[2],cr[0],cr[1]);
		Random CPrand=new Random();
		int hits=1;
		int safety=0;
		while (hits<=N && safety < 20*N) {
			Point2D.Double pt=new Point2D.Double(2.0*CPrand.nextDouble()-1.0,2.0*CPrand.nextDouble()-1.0);
			pt=(Point2D.Double)atrans.transform(pt,null);
			if (polypath.contains(pt.x,pt.y)) { // does polygon contain transformed point? 
				Z_list.add(new Complex(pt.x,pt.y));
				hits++;
			}
			safety++;
		}
		if (hits<=N) {
			CirclePack.cpb.myErrorMsg(
					"randomPolyPts: failed to get "+N+" points inside polygon.");
			return null;
		}

		// add bdry indexes start at iCount+1
		int iCount=Z_list.size();
		int tickv=iCount+1;
		EdgeLink elink=new EdgeLink();
		Complex w=poly[0];
		Z_list.add(w);
		for (int i=1;i<poly.length;i++) {
			Complex z=poly[i];
			Z_list.add(z);
			elink.add(new EdgeSimple(tickv,tickv++));
		}
		// have to close up
		elink.add(new EdgeSimple(tickv,iCount+1));

		DelaunayData dData=null;
		try {
			dData=new DelaunayData(0,Z_list,elink);
			ProcessDelaunay.planeDelaunay(dData);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("randomHypTriangulation failed: "+ex.getMessage());
			return null;
		}
		  
		// ============= create the triangulation
		return dData.getTriangulation();
	}
	
	/**
	 * Call for a random packing in the unit disc with N vertices; use
	 * sqrt(N) random points on the regular sqrt(N)-gon with vertices
	 * on the unit circle as bdry points. Difference here is that triangles
	 * with bdry points are removed after the triangulation is created.
	 * 'alpha' set as closest point to the origin. To avoid bdry problems,
	 * vertices are 'pruned' from the boundary if they don't have an interior
	 * neighbor; in expectation of some loss, we boost N by sqrt(N)/50.
	 * Calling routine is responsible for any repacking.
	 * @param packData
	 * @param N int; number of interior vertices
	 * @param sS boolean; if true, use (long)1 as seed (for debugging)
	 * @return nodecount or 0 on error
	 */
	public static PackData randomHypKomplex(int N,boolean sS) {
		setSeed=sS;
		
		if (N<4) 
			return null;
		
		// set polygonal path enclosing disc for call
		int bdryN=(int)(Math.PI*Math.sqrt((double)N));
		N=N+(int)(.25*bdryN); // extra to cover pruning losses
		Path2D.Double cirGamma=new Path2D.Double();
		double inc=2.0*Math.PI/(double)bdryN;
		cirGamma.moveTo(1.0,0.0);
		double ta;
		for (int i=1;i<bdryN;i++) {
			ta=i*inc;
			cirGamma.lineTo(Math.cos(ta),Math.sin(ta));
		}
		cirGamma.closePath();
		
		Triangulation Tri=null;
		try {
			Tri=random_Triangulation(N,sS,-1,0.0,cirGamma,null); // call with circle as bdry
			if (Tri==null) {
				CirclePack.cpb.errMsg("random_Triangulation failed");
				return null;
			}
		} catch (Exception ex) {
			throw new CombException(
					"random_Triangulation error: "+ex.getMessage());
		}

		// eliminate faces containing points of cirGamma (indices > N)
		Triangulation newTri=new Triangulation();
		newTri.faces=new Face[Tri.faceCount+1];
		int count=0;
		int v,u,w;
		try {
		for (int f=1;f<=Tri.faceCount;f++) {
			Face face=Tri.faces[f];
			v=face.vert[0];
			u=face.vert[1];
			w=face.vert[2];
			if (v<=N && u<=N && w<=N) { // keep this face 
				newTri.faces[++count]=new Face(3);
				newTri.faces[count].vert=new int[3];
				newTri.faces[count].vert[0]=v;
				newTri.faces[count].vert[1]=u;
				newTri.faces[count].vert[2]=w;
			}
		}
		} catch (Exception ex){
			ex.printStackTrace();
		}
		newTri.faceCount=count;
		newTri.nodeCount=Tri.nodeCount;
		newTri.nodes=Tri.nodes;
		
		PackData p=null;
		try {
			if ((p=Triangulation.tri_to_Complex(newTri,-1))==null) {
				CirclePack.cpb.errMsg("tri_to_Complex has failed.");
				return null;
			}
			
			// prune
			CombDCEL.pruneDCEL(p.packDCEL);
			p.packDCEL.fixDCEL(p);
		} catch (Exception ex) {
			throw new DataException("tri_to_Complex failed: "+ex.getMessage());
		}
		return p;
	}
	
	/**
	 * Alternate type of random triangulation of a Jordan region: (Jan 2017)
	 * Idea is to randomly triangulate a larger square about the target region,
	 * then do a "zigzag" cookie, which means that we basically keep only
	 * those faces whose centroids are in the region.
	 * @param N int, density of Poisson Point Process 
	 * @param Gamma Path2D.Double, closed curve, assumed Jordan
	 * @return Triangulation or null on error
	 */
	public static Triangulation zigzag_cookie(int N,Path2D.Double Gamma) {
		
		Rectangle bbox=Gamma.getBounds(); // based on upper left corner (x,y) 
		double wide=bbox.getWidth();
		double high=bbox.getHeight();
		double centx=bbox.getX()+wide/2.0;
		double centy=bbox.getY()-high/2.0;
		double side=(wide>high)? 2.0*wide : 2.0*high;
		
		// use lower left corner of larger surrounding square box
		double X=centx-side/2.0;
		double Y=centy-side/2.0;
		
		// random triangulation will be in square centered at same
		//   point, side length double of max(wide,high)
		// number of points is density times area 
		int intN=(int)(N*3.0*bbox.getWidth()*bbox.getHeight());
		Random rand=new Random();
		Vector<Complex> zvec=new Vector<Complex>(intN);
		for (int j=0;j<intN;j++) {
			double x=X+side*rand.nextDouble();
			double y=Y+side*rand.nextDouble();
			zvec.add(new Complex(x,y));
		}
		
		// get the Delaunay triangulation of these point in the square
		DelaunayData dData=new DelaunayData(0,zvec);
		Triangulation SqTri= dData.getTriangulation();
		
		return Triangulation.zigzag_cutter(SqTri,Gamma);
	}
	
    /**
     * Reads a file giving points on the sphere, the plane, or in
     * the unit square [0 1]x[0 1]. We must infer the form of the data:
     * 
     * (1) format for plane (2D) or sphere (3D):
     *     3 {text} (input format for 'qhull'; this line may not be present)
     *     n (this line may not be present)
     *     x1 y1 (z1)
     *     ...
     *     xn yn (zn)
     *    
     * (2) 3D data (e.g. from Thomson's web site); have to toss the lead term
     * 
     *     0: x0 y0 z0 
     *     ...
     *     n: xn yn zn 
     *    
     * (3) 2D data in unit square: keyword UNIT_SQUARE: then points x y, 
     *     (for now) just one per line.
     * 
     * 'rtnFlag' = 1 (unit square), 2=2D, 3=3D; in 3D case, the points 
     * are projected to the unit sphere and put in (theta,phi) form.
     * 
     * @param fp open BufferedReader
     * @param uP, UtilPacket: contains vector of points, rtnFlag for type,
     *        and errval < 0 on error.
     */
    public static void readPoints(BufferedReader fp,UtilPacket uP) {
    	int format=1; // 1, 2, or 3 as listed above
    	String line;
    	String str;
    	StringTokenizer tok;
    	int tcount=0;
    	uP.errval=0.0;
    	try {
    		// toss empty and comment lines
    		str=StringUtil.ourNextLine(fp,true);
    		if (str==null) throw new InOutException("didn't find any points");
    		if (str.startsWith("UNIT_SQ")) format=3;
    		
    		// have to determine format --- may have to toss some lines
			tok = new StringTokenizer(str);
			tcount=tok.countTokens();
			
			while (tcount<2) { // toss (e.g., this may have number of points)
				str=StringUtil.ourNextLine(fp,true);
	    		if (str==null) 
	    			throw new InOutException("didn't find any points");
	    		tok=new StringTokenizer(str);
	    		tcount=tok.countTokens();
			}
			tcount=tok.countTokens();
			if (tcount==4) // format 2, 3D only, need to toss first entry 
				format=2; 
			else { // have to toss first line if it has a non-numeric string
				boolean allnumerical=true;
				while (tok.hasMoreTokens()) {
					try {  // just a check to see if it's numeric
						Double.parseDouble(tok.nextToken());
					} catch (Exception ex) {
						allnumerical=false;
					}
				} 
				if (!allnumerical) { // toss, get new str
					str=StringUtil.ourNextLine(fp,true);
					if (str==null) throw new InOutException("didn't find any points");
					tok=new StringTokenizer(str);
					tcount=tok.countTokens();
					if (tcount<2) { // toss this line, too, get new str
						str=StringUtil.ourNextLine(fp,true);
						if (str==null) throw new InOutException("didn't find any points");
						tok=new StringTokenizer(str);
						tcount=tok.countTokens();
					}
				}
				else { // reestablish tokenizer
					tok=new StringTokenizer(str);
					tcount=tok.countTokens();
				}
			}
    		
			// 'str' should now be first data line
			uP.z_vec=new Vector<Complex>(20);
			if (format==2) tok.nextToken(); // toss the first token
			double x=Double.parseDouble(tok.nextToken());
			double y=Double.parseDouble(tok.nextToken());
			if ((format==1 && tcount==2) || format==3) { // 2D
				uP.z_vec.add(new Complex(x,y));
				uP.rtnFlag=2;
				if (format==3)
					uP.rtnFlag=1;
			}
			else { // must be 3D
				double z=Double.parseDouble(tok.nextToken());
				double d=Math.sqrt(x*x+y*y+z*z);
				if (d<.000001)
					throw new DataException("origin not a valid point");
				uP.z_vec.add(SphericalMath.proj_vec_to_sph(x,y,z)); // 3D, (theta,phi) form
				uP.rtnFlag=3;
			}

    		while ((line=StringUtil.ourNextLine(fp,true))!=null) {
    			tok = new StringTokenizer(line);
    			tcount=tok.countTokens();
    			if (format==2) tok.nextToken(); // toss first token
    			x=Double.parseDouble(tok.nextToken());
    			y=Double.parseDouble(tok.nextToken());
    			if ((format==1 && tcount==2) || format==3) // 2D
    				uP.z_vec.add(new Complex(x,y));
    			else { // must be 3D
    				double z=Double.parseDouble(tok.nextToken());
    				double d=Math.sqrt(x*x+y*y+z*z);
    				if (d<.000001)
    					throw new DataException("origin not a valid point");
    				uP.z_vec.add(SphericalMath.proj_vec_to_sph(x,y,z)); // 3D point
    			}
    		}
    		return;
    	} catch (Exception ex) {
    		CirclePack.cpb.errMsg("reading of point data failed: "+ex.getMessage());
    		uP.errval=-1.0;
    	}
    }
    
}