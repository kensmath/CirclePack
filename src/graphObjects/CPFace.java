package graphObjects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Vector;

import complex.Complex;
import complex.MathComplex;
import exceptions.DrawingException;
import geometry.HypGeodesic;
import geometry.SphGeodesic;
import geometry.SphericalMath;
import math.Point3D;
import panels.CPScreen;
import posting.PostFactory;
import util.ColorUtil;

/**
 * CPFace. This is a persistent object, one for each pack; it is
 * reused by changing the data as needed. 
 * CPFace handles triangles (N=3) or more general polygons. 
 * The various (x,y) values for vertices are set from outside. 
 * 'drawIt(boolean fill)' is main call for filled or open. 
 * Color settings in graphic context are handled elsewhere; generally
 * filled requires parent to make two calls because of different colors.
 * 
 * Spherical data is adjusted by parent to act as though sphere is in 
 * standard orientation (i.e., looking straight down x-axis toward origin,
 * z-axis vertical). 
 */
public class CPFace extends Complex {
	private static final double rad2deg=180.0/Math.PI;

	// persistent objects for re-use
	public Path2D.Double path;

	public CPScreen parent;
	private int geometry;

	// parent sets colors in context, adjusts data, calls draw or fill.
	public int N; // count of number of edges in the face; N>=3.
	public double[] corners;

	// Constructor
	public CPFace() {
		N=3;
		corners=new double[6];
		for (int i=0;i<6;i++) corners[i]=0.0;
		x = y = 0.0;
		geometry = 0;
		path=new Path2D.Double();
	}

	public void setParent(CPScreen par) {
		parent = par;
	}

	public void resetGeom(int geom) {
		geometry = geom;
	}

	public int getGeometry() {
		return geometry;
	}

	/**
	 * set the 'x', 'y' face data
	 * @param n
	 * @param cnrs
	 */
	public void setData(int n,double[] cnrs) {
		int len=cnrs.length;
		if (len<2*n) return;
		N=n;
		corners=new double[2*N];
		for (int i=0;i<2*N;i++) corners[i]=cnrs[i];
		x=corners[0];
		y=corners[1];
	}
	
	/**
	 * Draw face, possibly filled, colored, etc.
	 * @param draw boolean
	 * @param bcolor Color
	 * @param fill boolean
	 * @param fcolor Color
	 */
	public void drawIt(boolean draw,Color bcolor,boolean fill,Color fcolor) {
		drawIt(draw,bcolor,fill,fcolor,parent.imageContextReal);
	}
	
	/**
	 * Draw face, possibly filled, colored, etc.
	 * @param draw boolean
	 * @param bcolor Color
	 * @param fill boolean
	 * @param fcolor Color
	 * @param g2 Graphics2D
	 */
	public void drawIt(boolean draw,Color bcolor,
			boolean fill,Color fcolor,Graphics2D g2) {
				// handle sphere
		path.reset();
		if (geometry >0) {
			
			// do faces partially or wholly on front 
			try {
				sphDrawIt(draw,bcolor,fill,fcolor,g2);
			} catch(Exception ex){ }
			return;
		}

		// hyp, convert to euclidean
		if (geometry<0) { // hyp
			// first, find number of steps to use in approximations
			double footprint=0.0;
			double dif=0.0;
			for (int i=0;i<N;i++) {
				dif=Math.abs(corners[(2*i+2)%N]-corners[2*i]);
				footprint = (dif>footprint) ? dif : footprint;
				dif=Math.abs(corners[(2*i+3)%N]-corners[2*i+1]);
				footprint = (dif>footprint) ? dif : footprint;
			}
			footprint=footprint/parent.XWidth;
			int Nsteps=128;
			if (footprint<.0001) Nsteps=3;
			else if (footprint<.001) Nsteps=6;
			else if (footprint<.005) Nsteps=8;
			else if (footprint<.01) Nsteps=16;
			else if (footprint<.05) Nsteps=32;
			else if (footprint<.25) Nsteps=64;
			int m=0;
			try {
				m=hypCreateFace(path,corners,N,fill,Nsteps,parent);
			} catch(Exception ex) {}
			if (m==0) return; 
			path.closePath();
		}
		else { // eucl 
			path.append(new Line2D.Double(parent.toPixX(corners[0]),
					parent.toPixY(corners[1]),
					parent.toPixX(corners[2]),
					parent.toPixY(corners[3])),false);
			for (int i=1;i<N-1;i++) path.append(new Line2D.Double(
					parent.toPixX(corners[2*i]),
					parent.toPixY(corners[2*i+1]),
					parent.toPixX(corners[2*i+2]),
					parent.toPixY(corners[2*i+3])),true);
			path.closePath();
		}
		if (!path.intersects(g2.getClipBounds())) {  // view test
			return;
		}
		
		// will we change colors?
		Color origcolor=null;
		if ((draw && bcolor!=null) || (fill && fcolor!=null))
			origcolor=g2.getColor();
		
		// fill option
		if (fill) {
			if (fcolor!=null)
				g2.setColor(fcolor);
			g2.fill(path);
		}
		// draw option
		if (draw) {
			if (bcolor!=null)
				g2.setColor(bcolor);
			else if (origcolor!=null)
				g2.setColor(origcolor);
			g2.draw(path);
		}
		// reset color?
		if (origcolor!=null)
			g2.setColor(origcolor);
		return;
	} // end of drawIt
		
	/**
	 * If face has all or part of it on front, then draw both
	 * front and back (if sphere is not too opaque).
	 * @param draw boolean
	 * @param bcolor Color
	 * @param fill boolean
	 * @param fcolor Color
	 * @return int: 0 implies on front or sphere is opaque; 
	 * don't have to call again for back. -1 means you should call 
	 * for drawing on back.
	 */
	public int sphDrawIt(boolean draw,Color bcolor,boolean fill,Color fcolor) 
	throws DrawingException {
		return sphDrawIt(draw,bcolor,fill,fcolor,parent.imageContextReal);
	}
	
	/**
	 * If face has all or part of it on front, then draw both
	 * front and back (if sphere is not too opaque).
	 * @param draw boolean
	 * @param bcolor Color
	 * @param fill boolean
	 * @param fcolor Color
	 * @param g2 Graphic2D
	 * @return int: 0 implies on front or sphere is opaque; don't have to call
	 * again for back. -1 means you should call for drawing on back.
	 */
	public int sphDrawIt(boolean draw,Color bcolor,boolean fill,Color fcolor,Graphics2D g2) 
	throws DrawingException {
		int j=PostFactory.sphClosedPath(path,corners,parent);
		// all on back?
//		if (j<0) return -1;
		
		// will we change colors?
		boolean backtoo=parent.sphereOpacity<252;
		Color holdColor=null;
		if ((draw && bcolor!=null) || (fill && fcolor!=null) || backtoo)
			holdColor=g2.getColor();
		
		if (path.intersects(g2.getClipBounds())) {
			if (fill) { 
				if (fcolor!=null)
					g2.setColor(fcolor);
				g2.fill(path);
			}
			if (draw) { 
				if (bcolor!=null)
					g2.setColor(bcolor);
				else
					g2.setColor(holdColor);
				g2.draw(path);
			}
		}
		
		// if everything was on front or sphere opaque, don't draw back
		if (j==0 || !backtoo) {
			if (holdColor!=null)
				g2.setColor(holdColor);
			return 0;
		}
		
		// display on back; have to swap vert order, replace x by PI-x.
		double []cnrs=new double[2*N];
		for (int i=0;i<N;i++) {
			cnrs[2*i]=Math.PI-corners[2*(N-1-i)];
			cnrs[2*i+1]=corners[2*(N-1-i)+1];
		}
		j=PostFactory.sphClosedPath(path,cnrs,parent);
		if (j<0 || !path.intersects(g2.getClipBounds()))  // view test
			return 1;
		path.closePath();
		
		if (fill) { 
			if (fcolor!=null)
				g2.setColor(ColorUtil.ColorWash(fcolor,parent.sphereOpacity/255.0));
			g2.fill(path);
		}
		if (draw) { 
			if (bcolor!=null)
				g2.setColor(ColorUtil.ColorWash(bcolor,parent.sphereOpacity/255.0));
			else g2.setColor(ColorUtil.ColorWash(holdColor,parent.sphereOpacity/255.0));
			g2.draw(path);
		}

		// reset color
		if (holdColor!=null)
			g2.setColor(holdColor);
		return 1;
	}		
	
	/**
	 * Append a hyperbolic geodesic segment to existing path
	 * @param path, Path2D.Double
	 * @param geo, HypGeodesic
	 * @param cpS, CPScreen
	 */
	public static void hypCreateEdge(Path2D.Double path,
			HypGeodesic geo,CPScreen cpS) {
		if (geo.lineFlag) {
			path.append((Shape)new Line2D.Double(
					cpS.toPixX(geo.z1.x),cpS.toPixY(geo.z1.y),
					cpS.toPixX(geo.z2.x),cpS.toPixY(geo.z2.y)),true);
			return;
		}
		path.append((Shape)new Arc2D.Double(
			cpS.toPixX(geo.center.x-geo.rad),
			cpS.toPixY(geo.center.y+geo.rad),
			2.0*geo.rad*cpS.pixFactor,2.0*geo.rad*cpS.pixFactor,
			Math.toDegrees(geo.startAng),Math.toDegrees(geo.extent),
			Arc2D.OPEN),true);
	}


	
	public static int hypCreateFace(Path2D.Double gpath,
			double []cnrs,int N,boolean cp,int Nsteps,CPScreen cpS) 
	throws DrawingException {
		// first edge
		HypGeodesic geo = new HypGeodesic(
				new Complex(cnrs[0],cnrs[1]),new Complex(cnrs[2],cnrs[3]));
		hypCreateEdge(gpath,geo,cpS);
		// rest of edges
		for (int i=1;i<N-1;i++) {
			geo = new HypGeodesic(
				new Complex(cnrs[2*i],cnrs[2*i+1]),
				new Complex(cnrs[2*i+2],cnrs[2*i+3]));
			hypCreateEdge(gpath,geo,cpS);
		}
		// last edge, closes up
		geo = new HypGeodesic(
				new Complex(cnrs[2*N-2],cnrs[2*N-1]),
				new Complex(cnrs[0],cnrs[1]));
		hypCreateEdge(gpath,geo,cpS);
		return 1;
	}
	
}