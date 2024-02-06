package geometry;
import complex.Complex;
import math.Matrix3D;
import math.Point3D;
/**
 * Spherical geodesic for use in plotting. Set up data for the visual
 * portion of the geodesic only. CAUTION: positions are relevant to the
 * 'apparent' sphere (depends on 'spherical view'). 
 * Geodesic considered 'visible' iff:
 *    * at least one end is ON_FRONT
 *    * it follows horizon counterclockwise
 *    * its ends are approximately antipodals ON_HORIZON 
 *      (so 'lineFlag' is set and use geodesic towards the viewer).
 * Note that 'invisible' geodesic can start or end ON_HORIZON
 */
public class SphGeodesic{

	public final static int ON_BACK=0;
	public final static int ON_FRONT=1;
	public final static int ON_HORIZON=2;
	
	public Complex center;  // center (theta,phi) of great circle containing geodesic
	public Complex z1;  // start (theta,phi)
	public Complex z2;  // ent (theta,phi)
	public boolean lineFlag; // represent as a straight line (may depend on resolution) 

	public int z1Status; // z1 is ON_FRONT, ON_BACK, ON_HORIZON
	public int z2Status;   // z2 is ON_FRONT, ON_BACK, ON_HORIZON
	
	// entering/leaving front of sphere; null if not applicable
	public boolean horizonStart; // is z1 on horizon
	public boolean horizonEnd;   // is z2 on horizon
  
	// Constructor
	public SphGeodesic(Point3D A, Point3D B){
		lineFlag = false;
		horizonStart=false;
		horizonEnd=false;
		
		// z1 
		z1=A.getAsSphPoint();
		if (Math.abs(A.x)<.000001) { // on horizon
			z1=A.projToHorizon();
			z1Status=ON_HORIZON;
			horizonStart=true;
		}
		else if (A.x<0.0) z1Status=ON_BACK;
		else z1Status=ON_FRONT;

		// z2
		z2=B.getAsSphPoint();
		if (Math.abs(B.x)<.000001) {
			z2=B.projToHorizon();
			z2Status=ON_HORIZON;
			horizonEnd=true;
		}
		else if (B.x<0.0) z2Status=ON_BACK;
		else z2Status=ON_FRONT;

		// more processing if 'visible'
		if (z1Status==ON_HORIZON && z2Status==ON_BACK) { // just see start point
			z2=new Complex(z1);
			horizonStart=horizonEnd=true;
			lineFlag=true;
		}
		else if (z2Status==ON_HORIZON && z1Status==ON_BACK) { // just see end
			z1=new Complex(z2);
			horizonStart=horizonEnd=true;
			lineFlag=true;
		}
		else if (z1Status!=ON_BACK || z2Status!=ON_BACK) { // see more than one point
			// get direction to center of great circle via cross product.
		    Point3D n = Point3D.CrossProduct(A, B);
		    double n_nm = n.norm();

		    // In several cases, want a straight line:
		    //  * very small angular spread (TODO: might want to fine tune this)
		    //  * essentially identical or antipodal; ambiguous, use geodesic 
		    //      directly toward the viewer, i.e., straight line, seen edge-on.
		    if (Point3D.distance(A,B)<.005 || n_nm < 0.000001) { 
		    	lineFlag=true;
		    	if (z1Status==ON_BACK) { // B on front ==> A near horizon
		    		z1=A.projToHorizon();
		    		horizonStart=true;
		    	}
		    	else if (z2Status==ON_BACK) {  // A on front ==> B near horizon
		    		z2=B.projToHorizon();
		    		horizonEnd=true;
		    	}
		    }
		    else if (Math.abs(n.x/n_nm)<0.0001) { // see it edge-on 
		    	lineFlag=true;
		    	// n essentially equal to its projection N in yz-plane.
			    double modN=Math.sqrt(n.y*n.y+n.z*n.z);
			    double Ny = n.y/modN;
			    double Nz =n.z/modN;
			    Point3D pt;
			    // starts on back? come over horizon 90 deg cclwise from N
		   		if (z1Status==ON_BACK) {
		   			pt=new Point3D(0,-Nz,Ny);
		   			z1=pt.projToHorizon();
		   			horizonStart=true;
		   		}
		   		// starts on front; go over at 90 deg clwise from N
		   		else if (z2Status==ON_BACK){
		   			pt=new Point3D(0,Nz,-Ny);
		   			z2=pt.projToHorizon();
		   			horizonEnd=true;
		   		}
		    }
		    // else, geodesic must be an arc of a great circle
		    else { 
		    	// n essentially equal to its projection N in yz-plane.
			    double modN=Math.sqrt(n.y*n.y+n.z*n.z);
			    center=new Complex(n.getTheta(),n.getPhi());

			    // If geo crosses horizon, have to replace A or B by horizon spot. 
			    // Use perpendicular to n, which we can get from the coordinates of 
			    // its normalized projection into the yz-plane.
			    if (modN<.00001) { // n essentially parallel to x-axis, so
			    	// geodesic is part of horizon. 
			    	horizonStart=horizonEnd=true;
			    }
			    else {
			    	double Ny = n.y/modN;
			    	double Nz =n.z/modN;
			    	Point3D pt;
			    	if (z1Status==ON_BACK) { // starts on back
			    		pt=new Point3D(0,-Nz,Ny);
			    		z1=pt.projToHorizon();
			    		horizonStart=true;
			    	}
			    	if (z2Status==ON_BACK) { // ends on back
			    		pt=new Point3D(0,Nz,-Ny);
			    		z2=pt.projToHorizon();
			    		horizonEnd=true;
			    	}
			    }
		    }
		}
	}
	
	/**
	 * Visible if one of ends is ON_FRONT or if it follows the
	 * horizon counterclockwise.
	 * @return true if visible
	 */
	public boolean isVisible() {
		if (z1Status==ON_FRONT || z2Status==ON_FRONT || followHorizon()==1) 
			return true;
		return false;
	}
	
	/**
	 * Return true if geodesic is visible and hits horizon
	 * @return
	 */
	public boolean hitHorizon() {
		if (horizonStart || horizonEnd)
			return true;
		return false;
	}
	
	/**
	 * Return true if geodesic is visible and crosses front to back
	 * @return boolean
	 */
	public boolean cross2Back() {
		if (!hitHorizon()) return false;
		if (z1Status!=ON_BACK && z2Status!=ON_FRONT) return true;
		return false;
	}
	
	/**
	 * Return true if geodesic is visible and crosses back to front
	 * @return boolean
	 */
	public boolean cross2Front() {
		if (!hitHorizon()) return false;
		if (z1Status!=ON_FRONT && z1Status!=ON_BACK) return true;
		return false;
	}
	
	/**
	 * Check if geodesic follows horizon; return value gives direction. 
	 * NOTE: if this is edge-on great circle, the 'lineFlag' should 
	 * be set and 0 is returned.
	 * @return 0 if not; +-1 if counterclockwise/clockwise
	 * 
	 */
	public int followHorizon() {
		if (!hitHorizon() || lineFlag) return 0;
		if (horizonStart && horizonEnd) {
			if (z2.divide(z1).y>=0) return 1;
			return -1;
		}
		return 0;
	}
	
	/**
	 * @return oriented visible arc as string to concatenate for postscript.
	 * NOTE: PostFactory.sphEdgePath is already available and better.
	 * Note: calling routine must provide, 'n', 'gs', 's gr', etc.
	 */
	public String arc4Posting() {
		StringBuffer strbuf=new StringBuffer();
		
		if (!isVisible()) 
			return " ";
		
		Complex xy1=SphericalMath.sphToVisualPlane(z1);
		Complex xy2=SphericalMath.sphToVisualPlane(z2);

		// lives in horizon?
		int fh=followHorizon();
		double degPI=180/Math.PI;
		if (fh!=0) {
			double arg1=xy1.arg()*degPI;
			double arg2=xy2.arg()*degPI;
			strbuf.append("0 0 1 "+String.format("%.6e", arg1)+" "+String.format("%.6e", arg2)+" ");			
			return strbuf.toString();
		}

		// straight line cases? antipodal horizon points or containing plane is xz-plane.
		if (lineFlag || Math.abs(center.x-Math.PI/2.0)<.00001 || Math.abs(center.x+Math.PI/2.0)<.00001) {
			strbuf.append(String.format("%.6e", xy1.x)+" "+String.format("%.6e", xy1.y)+" l ");
			strbuf.append(String.format("%.6e", xy2.x)+" "+String.format("%.6e", xy2.y)+" l ");
			return strbuf.toString();
		}
		
		// generic cases: transform to o.n. basis u, v, w, where u=center,
		//   v=z1, and w = uXv.
		Point3D u=new Point3D(center.x,center.y);
		Point3D v=new Point3D(z1.x,z1.y);
		Point3D w=Point3D.CrossProduct(u, v);
		Matrix3D m3d=new Matrix3D(u.x,u.y,u.z,v.x,v.y,v.z,w.x,w.y,w.z);
		Matrix3D minv=Matrix3D.Inverse(m3d);
		Point3D zP2=Matrix3D.times(new Point3D(z2.x,z2.y),minv);
		double span=Math.atan2(zP2.z,zP2.y);
		int n=(int)Math.floor(span)+1;
		double ang_inc=span/(double)n;
		for (int j=0;j<=n;j++) {
			double ang=j*ang_inc;
			Point3D newpt=new Point3D(0,Math.cos(ang),Math.sin(ang));
			Point3D realpt=Matrix3D.times(newpt, minv);
			strbuf.append(String.format("%.6e", realpt.y)+" "+String.format("%.6e", realpt.z)+" l ");
		}
		return strbuf.toString();	
	}
	
}
