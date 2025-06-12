package graphObjects;

import geometry.HypGeodesic;
import geometry.SphGeodesic;
import geometry.SphericalMath;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import math.Point3D;
import packing.CPdrawing;
import util.ColorUtil;

import complex.Complex;
import complex.MathComplex;

/**
 * CPEdge. x,y,x2,y2 set from outside. 'drawIt()' is main call. Color settings
 * in graphic context handled elsewhere.
 * 
 * Spherical data is adjusted by parent to act as though sphere is in 
 * standard orientation (i.e., looking straight down x-axis toward origin,
 * z-axis vertical).
 */

public class CPEdge extends Complex {

	private static final double rad2deg=180.0/Math.PI;
	
	// persistent instance object
	private Line2D.Double line;
	public Color old_color;
	private CPdrawing parent;
	private static Path2D.Double path = new Path2D.Double();
	private int geometry; // eucl or hyperbolic; see SphCPCircle also

	// parent sets colors in context, adjusts data, calls draw or fill.
	public double x, y, x2, y2;

	// Constructor

	public CPEdge() {
		x = x2 = 0.0;
		y = y2 = 0.0;
		geometry = 0;
		line = new Line2D.Double();
	}

	public void setParent(CPdrawing par) {
		parent = par;
	}

	public void resetGeom(int geom) {
		geometry = geom;
	}

	public int getGeometry() {
		return geometry;
	}

	/**
	 * Draw an edge in the appropriate geometry, calling
	 * routine sets color, thickness
	 */
	public void drawIt() {
		drawIt(parent.imageContextReal);
	}

	/**
	 * Draw an edge in the appropriate geometry, calling
	 * routine sets color, thickness
	 */
	public void drawIt(Graphics2D g2) {
		// handle sphere first
		if (geometry > 0) {
			// front first
			path.reset();
			SphGeodesic sg=new SphGeodesic(new Point3D(x,y),new Point3D(x2,y2));
			if (sg.isVisible())	{
				sphCreateEdge(path,sg,parent);
				if (path.intersects(g2.getClipBounds()))  // view test
					g2.draw(path);
			}
			// back next (if not all on front and not opaque)
			if (parent.sphereOpacity<252 && !(sg.isVisible() && !sg.hitHorizon())) { 
				old_color=g2.getColor();
				g2.setColor(ColorUtil.ColorWash(old_color,parent.sphereOpacity/255.0));
				path.reset();
				sg=new SphGeodesic(new Point3D(Math.PI-x,y),new Point3D(Math.PI-x2,y2));
				if (sg.isVisible()) {
					sphCreateEdge(path,sg,parent);
					if (path.intersects(g2.getClipBounds()))  // view test
						g2.draw(path);
				}
				g2.setColor(old_color);
			}
			return;
		}
		if (geometry < 0) { // hyperbolic
			HypGeodesic geo = new HypGeodesic(new Complex(x,y),
					new Complex(x2,y2));
			if (geo.lineFlag || Math.abs(geo.extent)<.01) {
				line.setLine(parent.toPixX(geo.z1.x),
						parent.toPixY(geo.z1.y),
						parent.toPixX(geo.z2.x),
						parent.toPixY(geo.z2.y));
				if (line.intersects(g2.getClipBounds()))  // view test
					g2.draw(line);
				return;
			}
			Arc2D.Double a2d=new Arc2D.Double(
					parent.toPixX(geo.center.x-geo.rad),
					parent.toPixY(geo.center.y+geo.rad),
					2.0*geo.rad*parent.pixFactor,2.0*geo.rad*parent.pixFactor,
					Math.toDegrees(geo.startAng),Math.toDegrees(geo.extent),
					Arc2D.OPEN);
			if (a2d.intersects(g2.getClipBounds()))  // view test
				g2.draw(a2d);
		} 
		else { // euclidean
			line.setLine(parent.toPixX(x),parent.toPixY(y),
					parent.toPixX(x2),parent.toPixY(y2));
			if (line.intersects(g2.getClipBounds()))  // view test
				g2.draw(line);
		}
	}
	
	public void setData(double X1, double Y1, double X2, double Y2) {
		x = X1;
		y = Y1;
		x2 = X2;
		y2 = Y2;
	}
	
	/**
	 * Adds a spherical geodesic to an existing path
	 * @param sg, SphGeodesic 
	 * @param gpath, existing Path2D.Double
	 * @param cpS, CPDrawing
	 */
	public static void sphCreateEdge(Path2D.Double gpath,
			SphGeodesic sg,CPdrawing cpS) {
		if (!sg.isVisible()) return; // both are on back
		if (sg.lineFlag) { // handle a straight line
			Complex a=SphericalMath.sphToVisualPlane(sg.z1.x,sg.z1.y);
			Complex b=SphericalMath.sphToVisualPlane(sg.z2.x,sg.z2.y);
			gpath.moveTo(cpS.toPixX(a.x),cpS.toPixY(a.y));
			gpath.lineTo(cpS.toPixX(b.x),cpS.toPixY(b.y));
			return;
		}
		if (sg.followHorizon()!=0) { // handle geodesic along the horizon
			double ang1=rad2deg*Math.atan2(sg.z1.y,sg.z1.x);
			double extent=rad2deg*sg.z2.divide(sg.z1).arg();
			gpath.append((Shape)(new Arc2D.Double(cpS.toPixX(-1.0),
					cpS.toPixY(1.0),2.0*cpS.pixFactor,
					2.0*cpS.pixFactor,ang1,extent,Arc2D.OPEN)),true);
			return;
		}
		
		// part of great circle
		Point3D C=new Point3D(sg.center.x,sg.center.y);
		double plen=Math.sqrt(C.y*C.y+C.z*C.z);
		Point3D A=new Point3D(0,-C.z/plen,C.y/plen); // unit orthog to yz-proj of C

		// Creation of 'sg' should have made both ends visible
		Point3D end1=new Point3D(sg.z1.x,sg.z1.y);
		Point3D end2=new Point3D(sg.z2.x,sg.z2.y);

		double dp=Point3D.DotProduct(A,end1);
		double a1=Math.acos(dp);
		if (dp>=1.0) a1=0.0;
		else if (dp<=-1.0) a1=Math.PI;
		dp=Point3D.DotProduct(A,end2);
		double a2=Math.acos(dp);
		if (dp>=1.0) a2=0.0;
		else if (dp<=-1.0) a2=Math.PI;
		double extent=MathComplex.radAngDiff(a1,a2);
		if (C.x <= 0) { // we're seeing the ellipse from the back
			a1 *=-1.0;
			extent *=-1.0;
		}
		a1+=Math.PI/2.0;
		double origx=cpS.toPixX(0.0);
		double origy=cpS.toPixY(0.0);
		double wide=Math.abs(C.x)*cpS.pixFactor;
		Arc2D.Double tmpArc= new Arc2D.Double(origx-wide,origy-cpS.pixFactor,
				2.0*wide,2.0*cpS.pixFactor,a1*rad2deg,extent*rad2deg,Arc2D.OPEN);

		// Rotate to true orientation
		AffineTransform rot=new AffineTransform();
		rot.translate(origx,origy);
		rot.rotate(-Math.atan2(C.z,C.y));
		rot.translate(-origx,-origy);
		Path2D.Double tmpPath=new Path2D.Double((Shape)tmpArc,rot);
		gpath.append(tmpPath,true);
		return;
	}	
	
}
