package util;

import geometry.EuclMath;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import complex.Complex;

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
public class GenPathUtil {
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
	 * @param gpath
	 * @return
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
	 * the bounding rectangle to a polynomial approximating a Path2D.Double,
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
		double flatness = GenPathUtil.gpExtent(gpath) * GenPathUtil.FLAT_FACTOR;
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
            	vec.add(poly); // finished with this poly path
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
	
}
