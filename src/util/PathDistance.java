package util;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Iterator;

import allMains.CPBase;
import complex.Complex;

/**
 * Class stores segments of a Path2D.Double for efficient
 * identification of points within a given distance from the
 * path.
 * @author kstephe2, June 2016
 *
 */
public class PathDistance { 

	Path2D.Double myPath;
	ArrayList<Line2D.Double> polySegments;
	double thresh2; // square of the threshold (saves root, sign problems, etc.)
	
	// constructor(s)
	public PathDistance(Path2D.Double thePath,double thd) {
		myPath=thePath;
		if (myPath==null) {
			myPath=CPBase.ClosedPath;
		}
		thresh2=thd*thd;
		initData();
	}
	
	public PathDistance(double thd) {
		this(null,thd);
	}
	
	public PathDistance() {
		this(null,.01);
	}
	
	/** 
	 * initiate 
	 */
	public void initData() {
		if (myPath==null) {
			polySegments=null;
			return;
		}
		
		polySegments = new ArrayList<Line2D.Double>();
		ArrayList<double[]> polyPoints = new ArrayList<double[]>();
		double[] coords = new double[6];

		for (PathIterator pi = myPath.getPathIterator(null); !pi.isDone(); pi.next()) {
		    // The type will be SEG_LINETO, SEG_MOVETO, or SEG_CLOSE
		    // Because our path is composed of straight lines
		    int type = pi.currentSegment(coords);
		    // We record a double array of {segment type, x coord, y coord}
		    double[] pathIteratorCoords = {type, coords[0], coords[1]};
		    polyPoints.add(pathIteratorCoords);
		}

		double[] start = new double[3]; // To record where each polygon starts

		for (int i = 0; i < polyPoints.size(); i++) {
		    // If we're not on the last point, return a line from this point to the next
		    double[] currentElement = polyPoints.get(i);

		    // We need a default value in case we've reached the end of the ArrayList
		    double[] nextElement = {-1, -1, -1};
		    if (i < polyPoints.size() - 1) {
		        nextElement = polyPoints.get(i + 1);
		    }

		    // Make the lines
		    if (currentElement[0] == PathIterator.SEG_MOVETO) {
		        start = currentElement; // Record where the polygon started to close it later
		    } 
 
		    if (nextElement[0] == PathIterator.SEG_LINETO) {
		        polySegments.add(
		                new Line2D.Double(
		                    currentElement[1], currentElement[2],
		                    nextElement[1], nextElement[2]
		                )
		            );
		    } else if (nextElement[0] == PathIterator.SEG_CLOSE) {
		        polySegments.add(
		                new Line2D.Double(
		                    currentElement[1], currentElement[2],
		                    start[1], start[2]
		                )
		            );
		    }
		}
	}
	
	/**
	 * Check if z is within distance sqrt(thresh2) of myPath
	 * (compare thres2 to distance squared to avoid extra computation)
	 * @param z Complex
	 * @return true/false
	 */
	public boolean distance(Complex z) {

		try {
			Iterator<Line2D.Double> pS=polySegments.iterator();
			while (pS.hasNext()) {
				Line2D.Double line=pS.next();
				if (Line2D.Double.ptSegDistSq(line.getX1(),line.getY1(),line.getX2(),
						line.getY2(),z.x,z.y)<thresh2)
					return true;
			}
			return false;
		} catch(Exception ex) {
			return false;
		}
	}
	
}

