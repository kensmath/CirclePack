package util;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import com.jimrolf.functionparser.FunctionParser;

import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.SideData;
import exceptions.CombException;
import exceptions.DCELException;
import geometry.EuclMath;
import komplex.EdgeSimple;
import listManip.HalfLink;
import listManip.NodeLink;

/**
 * Utility routines for working with 'Path2D.Double' objects: 
 * finding length, max distance from the origin, output for 
 * postscript, scaling, etc. Creating closed circle paths,
 * 
 * These are not so sophisticated. A key is 'getPathIterator'. If we get
 * a 'FlatteningPathIterator', it gives lists of points to give a polygonal
 * path approximating the path; we will just guess at a 'flatness' parameter
 * to bound how far it drifts from the actual path.
 * 
 * @author kens
 *
 */
public class PathUtil {
	public static final double FLAT_FACTOR=0.01; // typical: flatness=extent*FLAT_FACTOR

	/**
	 * Find largest of length and width; e.g., we use it to choose appropriate
	 * 'flatness' parameter.
	 * @param gpath
	 * @return double
	 */
	public static double gpExtent(Path2D.Double gpath) {
		   Rectangle2D rect2D=gpath.getBounds2D();
		   double wide=rect2D.getWidth();
		   double high=rect2D.getHeight();
		   return (wide>high) ? wide : high;
	}
	
	/**
	 * get 'flatness' for interpolating with polygonal path.
	 * @param gpath Path2D.Double
	 * @return double
	 */
	public static double gpFlatness(Path2D.Double gpath) {
		if (gpath==null) return 0.0;
		double extent=gpExtent(gpath);
		return extent*FLAT_FACTOR;
	}
	
	/** 
	 * Returns length of 'Path2D.Double', flatness computed 
	 * by 'gpFlatness'.
	 * @param Path2D.Double gpath
	 * @return
	 */
	public static double gpLength(Path2D.Double gpath) {
		double flatness=gpFlatness(gpath);
		return gpLength(gpath,flatness);
	}
	
	/** 
	 * Returns length of 'Path2D.Double' based on polygonal approximation
	 * for given flatness parameter.
	 * @param Path2D.Double gpath
	 * @param double flatness
	 * @return
	 */
	public static double gpLength(Path2D.Double gpath,double flatness) {
		if (gpath==null) return 0.0;
		PathIterator pit = gpath.getPathIterator(null, flatness);
		double[] coords = new double[6]; // I think we only use first 2 entries
		double lastX = 0, lastY = 0;
		pit.currentSegment(coords); // the first point
		double lastMovetoX=coords[0],lastMovetoY=coords[1];
		double length = 0;
		while(!pit.isDone()) {
			int type = pit.currentSegment(coords);
			switch(type) {
			case PathIterator.SEG_MOVETO:
				lastX = lastMovetoX=coords[0];
				lastY = lastMovetoY=coords[1];
				break;
			case PathIterator.SEG_LINETO:
				length += Point2D.distance(lastX, lastY, coords[0], coords[1]);
				lastX = coords[0];
				lastY = coords[1];
				break;
			case PathIterator.SEG_CLOSE: // closes up subpath to last 'moveto' segment
				length += Point2D.distance(lastMovetoX,lastMovetoY,coords[0],coords[1]);
				break;
			default:
				System.out.println("Unexpected type in 'PathIterator': " + type);
			}
			pit.next();
		}
		return length;
	}
	
	/**
	 * Return distance from 'z' to the 'Path2D.Double', positive if 'z'
	 * is inside, negative if 'z' is outside.
	 * @param gpath
	 * @param flatness
	 * @return
	 */
	public static double gpDistance(Path2D.Double gpath,Complex z) {
		double[] coords = new double[6]; // I think we only use first 2 entries
		double lastX = 0, lastY = 0;
		
		double minDist=100000;
		double dist;
		PathIterator pit = gpath.getPathIterator(null, gpFlatness(gpath));
		double lastMovetoX=coords[0],lastMovetoY=coords[1];
		while(!pit.isDone()) {
			int type = pit.currentSegment(coords);
			switch(type) {
			case PathIterator.SEG_MOVETO:
				lastX = lastMovetoX=coords[0];
				lastY = lastMovetoY=coords[1];
				break;
			case PathIterator.SEG_LINETO:
				dist=EuclMath.dist_to_segment(z,new Complex(lastX,lastY),new Complex(coords[0],coords[1]));
				minDist = (dist<minDist) ? dist:minDist;
				lastX = coords[0];
				lastY = coords[1];
				break;
			case PathIterator.SEG_CLOSE: // closes up subpath to last 'moveto' segment
				dist=EuclMath.dist_to_segment(z,new Complex(lastX,lastY),
						new Complex(lastMovetoX,lastMovetoY));
				minDist = (dist<minDist) ? dist:minDist;
				lastX = lastMovetoX;
				lastY = lastMovetoY;
				break;
			default:
//				System.out.println("Unexpected type: " + type);
			}
			pit.next();
		}
		
		if (gpath.contains(z.x,z.y)) return minDist;
		return -minDist;
	}
	
	/**
	 * Find the center and radius of the smallest euclidean disc containing
	 * the bounding rectangle to a polygon approximating a Path2D.Double,
	 * with 'flatness' parameter.
	 * @param gpath
	 * @param flatness
	 * @return double[3] = [x,y,rad]
	 */
	public static double []gpCentRad(Path2D.Double gpath,double flatness) {
		double []results=new double[3];
		PathIterator pit = gpath.getPathIterator(null, flatness);
		double[] coords = new double[6]; // I think we only use first 2 entries
		double maxDist,dist;

		Rectangle2D rect2D=gpath.getBounds2D();
		double x=rect2D.getCenterX();
		double y=rect2D.getCenterY();
		maxDist=0.0; //rect2D.getHeight()+rect2D.getWidth();
		
		while(!pit.isDone()) {
			pit.currentSegment(coords);
			dist= Point2D.distance(x,y,coords[0],coords[1]);
			maxDist=(dist>maxDist) ? dist : maxDist;
			pit.next();
		}
		
		results[0]=x;
		results[1]=y;
		results[2]=maxDist;
		return results;
	}
	
	/**
	 * Return a vector of polygons (each a vector of Complex's) approximating 
	 * the given 'Path2D.Double'. May have more than one polygon if path has 
	 * components. Use this, e.g. when we want to put the path into postscript output.
	 * @param Path2D.Double gpath, double flatness
	 * @return Vector<Vector<Point2D.Double>>
	 */
	public static Vector<Vector<Complex>> gpPolygon(Path2D.Double gpath) {
		double flatness = PathUtil.gpFlatness(gpath);
		return gpPolygon(gpath,flatness);
	}
	
	/**
	 * Return a vector of polygons (each a vector of Complex's) approximating 
	 * the given 'Path2D.Double'. May have more than one polygon if path has 
	 * components. Use this, e.g. when we want to put the path into postscript output.
	 * @param gpath Path2D.Double
	 * @param flatness double
	 * @return Vector<Vector<Point2D.Double>>
	 */
	public static Vector<Vector<Complex>> gpPolygon(Path2D.Double gpath,double flatness) {
		Vector<Vector<Complex>> vec=new Vector<Vector<Complex>>(3);
		Vector<Complex> poly=new Vector<Complex>();
        double[] coords = new double[6];
        boolean newpoly=false;  // to get through first pass; afterwards, true
        						// means each SEG_MOVETO triggers a new poly vector
        						// TODO: may not need this: perhaps first type
        						// is always SEG_MOVETO.
        
        // get a FlatteningPathIterator.
        PathIterator pit = gpath.getPathIterator(null, flatness);
        if (pit.isDone()) return null;
        
        // find first point
        int type=pit.currentSegment(coords);
		double lastMovetoX=coords[0],lastMovetoY=coords[1];

        while(!pit.isDone()) {
            type = pit.currentSegment(coords);
            if (type!=PathIterator.SEG_MOVETO && type!=PathIterator.SEG_LINETO
            		&& type!=PathIterator.SEG_CLOSE)
                System.out.println("Unexpected type: " + type);
            else if (!newpoly) { // get started first time through only
            	newpoly=true;
            	poly.add(new Complex(coords[0],coords[1]));
            }
            else if (type==PathIterator.SEG_CLOSE) { // segment to last 'moveto' point
            	if (lastMovetoX!=coords[0] || lastMovetoY!=coords[1])
            		poly.add(new Complex(lastMovetoX,lastMovetoY));
            }
            else if (type==PathIterator.SEG_MOVETO) {
            	vec.add(poly); // finished with this polygonal path; save and start the next
            	poly=new Vector<Complex>();
            	poly.add(new Complex(coords[0],coords[1]));
            	lastMovetoX=coords[0];
            	lastMovetoY=coords[1];
            }
            else {
            	poly.add(new Complex(coords[0],coords[1]));
            }            	
            pit.next();
        }
        vec.add(poly);
        return vec;
	}
	
	/**
	 * Return a Path2D.Double representing eucl circle (z,r) with
	 * N (at least 4) segments. See 'CPCircle' for code source.
	 * @param rad
	 * @param z Complex
	 * @param N
	 * @return
	 */
	public static Path2D.Double getCirclePath(double radius,Complex z,int N) {
		// hands-on drawing
		Path2D.Double path= new Path2D.Double();
		if (N<4) N=4;
		path.moveTo(radius+z.x,z.y);
		for (int i=1;i<N;i++) {
			double ang=(double)i*2.0*Math.PI/(double)N;
			path.lineTo(z.x+radius*Math.cos(ang),
					z.y+radius*Math.sin(ang));
		}
		path.closePath();
		return path;
	}
	
	/**
     * Create a Path2D.Double in the complex plane by parsing 
     * the complex function described in 'path_text' in terms of
     * real variable 't' for t in [0,1]. Default to 'N' points.
     * description using real variable 't' for t in [0,1]. 
     * @param path_text String
     * @param N int
     * @return Path2D.Double, null on error
     */
    public static Path2D.Double path_from_text(String path_text,int N) {
    	if (path_text==null || path_text.length()==0)
    		return null;
		Path2D.Double closedPath=new Path2D.Double();
		FunctionParser utilParser=new FunctionParser();
		utilParser.setComplex(true);
		utilParser.removeVariable("x");
		utilParser.setVariable("t");
		utilParser.parseExpression(path_text);
		if (utilParser.funcHasError()) {
			CirclePack.cpb.errMsg("Path description could not be parsed");
			return null;
		}
		if (N<10)
			N=10;
		// create path, 200 segments
		try {
			for (int i=0;i<=N;i++) {
				com.jimrolf.complex.Complex z=
					new com.jimrolf.complex.Complex(((double)(i))/(double)N,0.0);
				com.jimrolf.complex.Complex w=utilParser.evalFunc(z);
				if (i==0)
					closedPath.moveTo(w.re(),w.im());
				else
					closedPath.lineTo(w.re(),w.im());
			}
			closedPath.closePath();
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("Failed creation of closed path");
			return null;
		}
    	return closedPath;
    }
	
    public static Path2D.Double path_from_text(String path_text) {
    	return path_from_text(path_text,200);
    }
    
	/**
	 * Return the point on the (flattened) path which is closest to
	 * the given point z.
	 * @param z Complex
	 * @param path Path2D.Double
	 * @return Complex, null on error
	 */
	public static Complex getClosestPoint(Complex pt, Path2D.Double gpath) {
		
		Vector<Vector<Complex>> vec=gpPolygon(gpath);
		Iterator<Vector<Complex>> polys=vec.iterator();
		if (!polys.hasNext())
			return null;
		Complex bestz=null;
		double bestsq=Double.MAX_VALUE; // best squared distance
		while(polys.hasNext()) {
			Vector<Complex> poly=polys.next();
			Complex newz=getClosestPoint(pt,poly);
			double dist2pt=newz.minus(pt).absSq();
			if (dist2pt<bestsq) {
				bestz=newz;
				bestsq=dist2pt;
			}
		} // done with while through polygons
			
		return bestz;
	}
	
	/**
	 * Return the point on the polygonal path which is closest to
	 * the given point z. The polygon is assumed to be closed.
	 * @param z Complex
	 * @param poly Vector<Complex>, the polygon
	 * @return Complex, null on error
	 */
	public static Complex getClosestPoint(Complex pt, Vector<Complex> poly) {
		Complex bestz=null;
		double bestsq=Double.MAX_VALUE; // best squared distance
		for (int i=0;i<poly.size();i++) {
			Complex z1=poly.get(i);
			Complex z2=poly.get((i+1)%poly.size());
			Complex projz=EuclMath.proj_to_seg(pt, z1, z2);
			double dist2pt=projz.minus(pt).absSq();
			if (dist2pt<bestsq) {
				bestz=projz;
				bestsq=dist2pt;
			}
		} 
		return bestz;
	}

	/**
	   * Return the shortest closed interior edge path which
	   * misses 'path' except for starting/ending at the origin
	   * of 'seededge' (which must lie in 'path'). Note: 'path' 
	   * may have multiple connected components, but it 'path' 
	   * separates the complex, we get an exception. 
	   * Normally 'path' either closed or with bdry endpoints;
	   * its edges must be interior. We work by counting 
	   * generations of interiors from 'seededge' on the left 
	   * and right of 'path'.  
	   * @param pdcel PackDCEL
	   * @param path HalfLink
	   * @param HalfEdge seededge, and edge of 'path'
	   * @return PackDCEL or null on error
	   */
	  public static HalfLink getCutPath(PackDCEL pdcel,
			  HalfLink path,HalfEdge seededge) {
	
		  if (path==null || path.size()==0 || seededge==null)
			  return null;
	
		  // set all 'vutil' to bound = "untouched"
		  int bound=pdcel.vertCount+1;
		  for (int v=1;v<=pdcel.vertCount;v++) {
			  pdcel.vertices[v].vutil=bound;
		  }
		  
		  // zero 'vutil' on 'path', check for bdry edges
		  Iterator<HalfEdge> pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfEdge he=pis.next();
			  if (pdcel.isBdryEdge(he))
				  throw new DCELException("'getCutPath' error: edge "+
						  he+" is a bdry edge");
			  he.origin.vutil=0;
		  }
		  
		  // zero out bdry 'vutil' so we don't try to cross bdry
		  for (int i=1;i<=pdcel.idealFaceCount;i++) {
			  HalfEdge stpe=pdcel.idealFaces[i].edge;
			  HalfEdge he=stpe;
			  do {
				  he.origin.vutil=0;
				  he=he.next;
			  } while (he!=stpe);
		  }
	
		  // find 'left/rightfan's of 'seededge'; used to get
		  //   'vl' and 'vr'; also needed at to consider shortening
		  ArrayList<Integer> leftfan=new ArrayList<Integer>(0);
		  ArrayList<Integer> rightfan=new ArrayList<Integer>(0);
		  HalfEdge he=seededge.prev.twin;
		  while (he.twin.origin.vutil>0) {
			  leftfan.add(he.twin.origin.vertIndx);
			  he=he.prev.twin; // cclw
		  }
		  he=seededge.twin.next;
		  while (he.twin.origin.vutil>0) {
			  rightfan.add(he.twin.origin.vertIndx);
			  he=he.twin.next; // clw
		  }
		  
		  if (leftfan.size()==0 || rightfan.size()==0)
			  throw new CombException("blue edge?? can't move to nghb on "+
					  		"one side or other");
		  int vl=leftfan.get(0); // left = plus side
		  int vr=rightfan.get(0); // right = minus side
		  int seedOrigin=seededge.origin.vertIndx;
	
		  // generation 1: 'vutil' +/- 1 on left/right of 'seededge'
		  // two-list method to count successive generations (+/-)
		  NodeLink currv=new NodeLink();
		  NodeLink nextv=new NodeLink();
		  boolean lhit=false;
		  boolean rhit=false;
		  if (pdcel.vertices[vl].vutil==bound) {
			  pdcel.vertices[vl].vutil=1;
			  nextv.add(vl);
			  lhit=true;
		  }
		  if (pdcel.vertices[vr].vutil==bound) {
			  pdcel.vertices[vr].vutil=-1;
			  nextv.add(vr);
			  rhit=true;
		  }
		  if (!lhit || !rhit) {
			  throw new CombException("failed to get started with + or - vertices");
		  }
		  
		  int safety=2*bound;
		  int hitvert=0; // first vert with both + and - nghbs
		  while (nextv.size()>0 && hitvert==0 && safety>0) {
			  currv=nextv;
			  nextv=new NodeLink();
			  Iterator<Integer> cis=currv.iterator();
			  while (cis.hasNext() && hitvert==0) {
				  Vertex vert=pdcel.vertices[cis.next()];
				  int myUtil=vert.vutil; // must already be on left or right
				  int[] petals=vert.getPetals();
				  for (int j=0;(j<petals.length && hitvert==0);j++) {
					  Vertex wert=pdcel.vertices[petals[j]];
					  if (wert.vutil>=bound) { // first touch?
						  if (myUtil<0) // right side hit
							  wert.vutil=myUtil-1;
						  else { // left side hit
							  wert.vutil=myUtil+1;
						  }
						  nextv.add(wert.vertIndx);
					  }
					  // are we done?
					  else if (wert.vutil!=0) {  
						  if ((myUtil<0 && wert.vutil>0) ||
								  (myUtil>0 && wert.vutil<0)) { 
							  hitvert=vert.vertIndx;
						  }
					  }
				  } // done with 'petals'
			  } // done with while on currv
			  safety--;
		  } // done with while on nextv
		  
		  if (safety<=0 || hitvert==0) {
			  throw new DCELException("hum...? no collision "+
					  "or overran safety in 'ShortCut'");
		  }
		  
		  // build 'cutPath' from left side of 'seededge' to
		  //   right side: get from 'hitvert' to 'vl', then
		  //   from 'hitver' to 'vr'.
		  HalfLink cutPath=new HalfLink();
		  
		  // find first + vert, first - vert.
		  Vertex vert=pdcel.vertices[hitvert];
		  int plus1=0;
		  int minus1=0;
		  if (vert.vutil<0) {
			  minus1=vert.vertIndx;
			  int[] petals=vert.getPetals();
			  for (int j=0;(j<petals.length && plus1==0);j++) {
				  if (pdcel.vertices[petals[j]].vutil>0) {
					  plus1=petals[j];
					  break;
				  }
			  }
		  }
		  else {
			  plus1=vert.vertIndx;
			  int[] petals=vert.getPetals();
			  for (int j=0;(j<petals.length && minus1==0);j++) {
				  if (pdcel.vertices[petals[j]].vutil<0) {
					  minus1=petals[j];
					  break;
				  }
			  }
		  }
	
		  // get the plus side back to 'vl'.
		  int newv=plus1;
		  while (newv!=vl) {
			  vert=pdcel.vertices[newv];
			  int myUtil=vert.vutil; // should be positive
			  int[] petals=vert.getPetals();
			  for (int j=0;j<petals.length;j++) {
				  int nutil=pdcel.vertices[petals[j]].vutil;
				  if (nutil>0 && nutil<myUtil) {
					  cutPath.add(pdcel.
							  findHalfEdge(new EdgeSimple(petals[j],newv)));
					  newv=petals[j];
					  break;
				  }
			  }
		  }
		  if (newv!=vl) {
			  throw new CombException("didn't reach 'vl' as expected");
		  }
		  cutPath.add(pdcel.findHalfEdge(
				  new EdgeSimple(seedOrigin,vl)));
		  
		  // 'HalfEdges' are going right way, just reverse the list
		  cutPath=HalfLink.reverseLink(cutPath);
		  
		  // here's the edge connecting plus to minus halves
		  cutPath.add(pdcel.findHalfEdge(new EdgeSimple(plus1,minus1)));
		  
		  // now get path to 'vr'; make sure hitvert vutil is <0
		  newv=minus1;
		  HalfLink minushalf=new HalfLink();
		  vert=pdcel.vertices[newv];
		  while (newv!=vr) {
			  vert=pdcel.vertices[newv];
			  int myUtil=vert.vutil; // this is negative
			  int[] petals=vert.getPetals();
			  for (int j=0;j<petals.length;j++) {
				  int nutil=pdcel.vertices[petals[j]].vutil;
				  if (nutil<0 && nutil>myUtil) {
					  cutPath.add(pdcel.
							  findHalfEdge(new EdgeSimple(newv,petals[j])));
					  newv=petals[j];
					  break;
				  }
			  }
		  }
		  if (newv!=vr) {
			  throw new CombException("didn't reach 'vr' as expected");
		  }
	
		  minushalf.add(pdcel.findHalfEdge(
				  new EdgeSimple(vr,seedOrigin)));
		  
		  cutPath.abutMore(minushalf);
		  
		  // May be able to shorten at beginning (plus side)
		  int ed=cutPath.get(1).twin.origin.vertIndx;
		  if (leftfan.contains(ed)) { // yes, can shortcut
			  cutPath.remove(1); // remove first two
			  cutPath.remove(0); 
			  // replace first two steps by <seedOrigin,ed>
			  cutPath.add(0,pdcel.findHalfEdge(seedOrigin,ed)); 
		  }
		  
		  // and/or at end
		  int sz=cutPath.size();
		  ed = cutPath.get(sz-2).origin.vertIndx;
		  if (rightfan.contains(ed)) { // yes, can shortcut
			  cutPath.removeLast(); // remove last two
			  cutPath.removeLast();
			  // replace last two steps by <ed,seedOrigin>
			  cutPath.add(pdcel.findHalfEdge(ed,seedOrigin));
		  }
		  
		  // 'cutPath should start/stop at 'seedOrigin'
		  return cutPath;
	  }

	/**
	   * Using the red chain and 'pairLink', find a short 
	   * non-separating path of interior edges which either
	   * is closed or starts/ends on distinct boundary 
	   * components. CAN NOT guarantee that it is minimal 
	   * length in its homotopy class.
	   * @param pdcel PackDCEL
	   * @return HalfLink or null on failure
	   */
	  public static HalfLink getNonSeparating(PackDCEL pdcel) {
		  
		  if (pdcel.p.isSimplyConnected())
			  throw new CombException("this complex is simply connected");
		  
		  // we depend on the red chain
		  if (pdcel.redChain==null) {
			  int ans=CombDCEL.redchain_by_edge(pdcel, null, null, false);
			  if (ans==0)
				  throw new CombException("No red chain, and failed to create one");
			  CombDCEL.fillInside(pdcel); // this sets side-pairings
		  }
		  
		  // first, check pairLink for shortest side which is either:
		  //   * paired and closed, or
		  //   * paired and starts ends on distinct bdry comps.
		  int bestSide=-1;
		  int shortest=10*pdcel.vertCount;
		  int[] lengths=new int[pdcel.pairLink.size()]; // find lengths
		  Iterator<SideData> dsp=pdcel.pairLink.iterator();
		  dsp.next(); // first is empty
		  while (dsp.hasNext()) {
			  SideData sdata=dsp.next();
			  int mIndx=sdata.mateIndex;
			  if (mIndx>0) { // is paired
				  RedEdge oppStart=pdcel.pairLink.get(mIndx).startEdge;
				  int end1=sdata.startEdge.myEdge.origin.vertIndx;
				  int end2=oppStart.myEdge.origin.vertIndx;
				  
				  // closed?
				  if (end1==end2) {
					  lengths[sdata.spIndex]=sdata.sideCount();
					  if (lengths[sdata.spIndex]<shortest) {
						  shortest=lengths[sdata.spIndex];
						  bestSide=sdata.spIndex;
					  }
				  }
				  
				  // both ends on bdry?
				  else if (oppStart.myEdge.origin.bdryFlag!=0 &&
						  sdata.startEdge.myEdge.origin.bdryFlag!=0 &&
						  !CombDCEL.onSameBdryComp(pdcel, end1, end2)) { 
					  lengths[sdata.spIndex]=sdata.sideCount();
					  if (lengths[sdata.spIndex]<shortest) {
						  shortest=lengths[sdata.spIndex];
						  bestSide=sdata.spIndex;
					  }
				  }
			  }
		  } // done finding qualifying lengths
		  
		  // found one?
		  if (bestSide!=-1)
			  return pdcel.pairLink.get(bestSide).sideHalfLink();
		  
		  // else, cycle through side 'startEdge's origins and find
		  //    the one for which the count of edges between it
		  //    and its first cclw repeat is shortest and those are
		  //    all interior edges.
		  dsp=pdcel.pairLink.iterator();
		  dsp.next(); // first is null
		  while (dsp.hasNext()) {
			  SideData sdata=dsp.next();
			  RedEdge rtrace=sdata.startEdge;
			  int end1=rtrace.myEdge.origin.vertIndx;
			  rtrace=rtrace.nextRed;
			  int tick=0;
			  RedEdge stopRed=sdata.startEdge.prevRed;
			  INNER_WHILE: while (rtrace!=stopRed && tick>=0 && 
					  rtrace.myEdge.origin.vertIndx!=end1) {
				  HalfEdge he=rtrace.myEdge;
				  if (he.twin.face!=null && he.twin.face.faceIndx<0) {
					  tick=0;
					  break INNER_WHILE;
				  }
				  tick++;
				  rtrace=rtrace.nextRed;
			  }
			  if (tick>0) { // will this always happen? I think so
				  lengths[sdata.spIndex]=tick;
			  }
		  } // done getting lengths between repeats
	
		  for (int j=1;j<lengths.length;j++) {
			  if (lengths[j]<shortest) {
				  shortest=lengths[j];
				  bestSide=j;
			  }
		  }
		  
		  // I don't believe this can fail because one of the
		  //   earlier paths would have necessarily succeeded
		  //   (I think).
		  if (bestSide==-1) {
			  throw new DCELException("'getNonSeparating' error failed");
		  }
		  
		  // return 
		  HalfLink hlink=new HalfLink();
		  RedEdge rtrace=pdcel.pairLink.get(bestSide).startEdge;
		  int end1=rtrace.myEdge.origin.vertIndx;
		  hlink.add(rtrace.myEdge);
		  rtrace=rtrace.nextRed;
		  while (rtrace.myEdge.origin.vertIndx!=end1) {
			  hlink.add(rtrace.myEdge);
			  rtrace=rtrace.nextRed;
		  }
		  
		  return hlink;
	  }

	/**
	   * Return 'HalfLink' path of interior edges which 
	   * is among the shortest combinatorially starting 
	   * and ending at an end of 'seed' edge without 
	   * crossing 'path'. Make a small shift of ends
	   * if it will close the path without lengthening it
	   * else end with 'seed' itself to close up.
	   * @param pdcel PackDCEL
	   * @param path HalfLink
	   * @param seed HalfLink
	   * @return HalfLink
	   */
	  public static HalfLink getShortPath(PackDCEL pdcel,
			  HalfLink path,HalfLink seed) {
		  HalfLink link1=new HalfLink();
		  int bound=pdcel.vertCount+1;
		  
		  // set all 'vutil' to bound = "untouched"
		  for (int v=1;v<=pdcel.vertCount;v++) {
			  pdcel.vertices[v].vutil=bound;
		  }
		  
		  // two-list method to count generations (+/-)
		  NodeLink currv=new NodeLink();
		  NodeLink nextv=new NodeLink();
	
		  // set 'vutil' 0 on 'path'
		  Iterator<HalfEdge> pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfEdge he=pis.next();
			  he.origin.vutil=0;
			  he.twin.origin.vutil=0;
		  }
		  
		  // set 'vutil' +/- on left/right of 'seed' edges
		  boolean lhit=false;
		  boolean rhit=false;
		  Iterator<HalfEdge> sis=seed.iterator();
		  while (sis.hasNext()) {
			  HalfEdge he=sis.next();
			  int vl=he.next.next.origin.vertIndx;
			  int vr=he.twin.next.next.origin.vertIndx;
			  if (pdcel.vertices[vl].vutil==bound) {
				  pdcel.vertices[vl].vutil=1;
				  nextv.add(vl);
				  lhit=true;
			  }
			  if (pdcel.vertices[vr].vutil==bound) {
				  pdcel.vertices[vr].vutil=-1;
				  nextv.add(vr);
				  rhit=true;
			  }
		  }
		  if (!lhit || !rhit) {
			  throw new CombException("failed to get started with + or - vertices");
		  }
		  
		  int safety=2*bound;
		  int hitvert=0; // first hit (has both + and - nghb)
		  while (nextv.size()>0 && hitvert==0 && safety>0) {
			  currv=nextv;
			  nextv=new NodeLink();
			  Iterator<Integer> cis=currv.iterator();
			  while (cis.hasNext() && hitvert==0) {
				  Vertex vert=pdcel.vertices[cis.next()];
				  int myUtil=vert.vutil; // already on left or right
				  int[] petals=vert.getPetals();
				  for (int j=0;(j<petals.length && hitvert==0);j++) {
					  Vertex wert=pdcel.vertices[petals[j]];
					  if (wert.vutil>=bound) { // first touch?
						  if (myUtil<0) // right side hit
							  wert.vutil=myUtil-1;
						  else { // left side hit
							  wert.vutil=myUtil+1;
						  }
						  nextv.add(wert.vertIndx);
					  }
					  else { // wert was alreadly left or right
						  
						  // are we done?
						  if ((myUtil<0 && wert.vutil>0) ||
								  (myUtil>0 && wert.vutil<0)) { 
							  hitvert=vert.vertIndx;
						  }
						  else
							  nextv.add(wert.vertIndx);
					  }
				  } // done with 'petals'
			  } // done with while on currv
			  safety--;
		  } // done with while on nextv
		  
		  if (safety<=0 || hitvert==0) {
			  throw new DCELException("hum...? no collision "+
					  "or overran safety in 'ShortCut'");
		  }
		  
		  // +/- generations first collide at 'hitvert'
		  // for 'HalfLink' from left side to right side 
		  if (hitvert!=0) {
			  
			  // find +/- petals
			  Vertex hitVert=pdcel.vertices[hitvert];
			  int vneg=0;
			  int vpos=0;
			  int[] petals=hitVert.getPetals(); // open flower
			  for (int j=0;(j<petals.length && (vneg==0 || vpos==0));j++) {
				  int val=pdcel.vertices[petals[j]].vutil;
				  if (vneg==0 && val<0) 
					  vneg=petals[j];
				  if (vpos==0 && val>0 && val<bound)
					  vpos=petals[j];
			  }
			  if (vneg==0 || vpos==0) {
				  throw new DCELException("not collision at "+hitvert+"??");
			  }
		  
			  // walk back through increasingly smaller + generations
			  link1.add(pdcel.findHalfEdge(new EdgeSimple(hitvert,vpos)));
			  while (pdcel.vertices[vpos].vutil!=0) {
				  HalfLink eflower=pdcel.vertices[vpos].getEdgeFlower();
				  int myindx=pdcel.vertices[vpos].vutil;
				  HalfEdge hhedge=null;
				  Iterator<HalfEdge> eis=eflower.iterator();
				  while (eis.hasNext() && hhedge==null) {
					  HalfEdge he=eis.next();
					  if (he.twin.origin.vutil==myindx-1)
						  hhedge=he;
				  }
				  if (hhedge==null) {
					  throw new DCELException("lost + generational link");
				  }
				  link1.add(hhedge);
				  vpos=hhedge.twin.origin.vertIndx;
			  }
			  link1=HalfLink.reverseElements(link1);
			  link1=HalfLink.reverseLink(link1);
			  
			  // now walk through increasingly less - generations
			  while (pdcel.vertices[vneg].vutil!=0) {
				  HalfLink eflower=pdcel.vertices[vneg].getEdgeFlower();
				  int myindx=pdcel.vertices[vneg].vutil;
				  HalfEdge hhedge=null;
				  Iterator<HalfEdge> eis=eflower.iterator();
				  while (eis.hasNext() && hhedge==null) {
					  HalfEdge he=eis.next();
					  if (he.twin.origin.vutil==(myindx+1))
						  hhedge=he;
				  }
				  if (hhedge==null) {
					  throw new DCELException("lost - generational link");
				  }
				  link1.add(hhedge);
				  vneg=hhedge.twin.origin.vertIndx;
			  }
		  }
		  
		  // closed already?
		  HalfEdge edgefirst=link1.get(0);
		  HalfEdge edgelast=link1.getLast();
		  int v=edgefirst.origin.vertIndx;
		  int w=edgelast.twin.origin.vertIndx;
		  if (v==w)
			  return link1;
		  
		  // simple adjustment?
		  if (edgefirst.next.next.origin.vertIndx==w) {
			  link1.add(0,edgefirst.next.twin);
			  return link1;
		  }
		  if (edgelast.prev.origin.vertIndx==w) {
			  link1.add(0,edgefirst.next.next);
			  return link1;
		  }
		  int lastIndx=link1.size()-1;
		  if (edgelast.twin.prev.origin.vertIndx==v) {
			  link1.add(lastIndx,edgelast.twin.next);
			  return link1;
		  }
		  if (edgelast.next.origin.vertIndx==v) {
			  link1.add(lastIndx,edgelast.twin.prev.twin);
			  return link1;
		  }
	
		  return link1;
	  }
	
}
