package graphObjects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

import allMains.CPBase;
import complex.Complex;
import complex.MathComplex;
import math.Point3D;
import packing.CPdrawing;
import util.ColorUtil;

/**
 * CPCircle extends Complex. x,y,radius set from outside. 
 * 'drawIt(boolean fill)' is main call for filled or open. 
 * Color settings in graphic context handled elsewhere.
 * 
 * Spherical data is adjusted by parent to act as though sphere is in 
 * standard orientation (i.e., looking straight down x-axis toward origin,
 * z-axis vertical).
 * 
 * NOTE: as of 12/2009, I've implemented use of 'Ellipse2D.Double'.
 * Because of a bug involving 'AffineTransform', I had to change all
 * my calls to use pixel (instead of real) data in the calls. This
 * avoids building my own lists for circles. The old code is in
 * 'CircleList.java'.
 * 
 * @author kens
 *
 */
public class CPCircle extends Complex {

	private static final double rad2deg=180.0/Math.PI;
	
	// Store static unit 128 point unit circle
	private static double[] sine=new double[128];
	private static double[] cosine=new double[128];
	static {
		double inc=Math.PI/64;
		for (int i=1;i<128;i++) {
			double ninc=i*inc;
			sine[i]=Math.sin(ninc);
			cosine[i]=Math.cos(ninc);
		}
	}
    
    //  Thresholds for circles: set by world based on screen size; 
    //  use number of points based on smallest threshold > radius.
	public double[] cir_sizes = new double[6]; 

	// persistent
	private Path2D.Double path;

	// utility
	private Color old_color;
	
	// instance variables
	private CPdrawing parent;

	private int geometry; 

	// parent sets colors in imageContextReal, adjusts x,y,radius, calls 
	// drawIt() for draw or fill, etc.
	public double x;
	public double y;
	public double radius;
	
	// drawing an arc only?  need 'start' angle, 'extent', in degrees. Null in general
	// Note: arc added, 12/2015, only used in euclidean case 
	public Double start; // null in general
	public Double extent; // null in general
	
	// Constructor
	public CPCircle() {
		path = new Path2D.Double();
		radius = .5;
		x = 0.0;
		y = 0.0;
		geometry=0;
		start=null;
		extent=null;
	}
	
	// start over: e.g., when changing geometries
	public void resetGeom(int geom) {
		geometry=geom;
		path.reset();
		x=y=0.0;
		radius=.5;
		start=null;
		extent=null;
	}
	
	public void setParent(CPdrawing par) {
		parent=par;
	}
	
	public int getGeometry() {
		return geometry;
	}
	
	/**
	 * When canvas cleared in non-euclidean cases need to 
	 * draw disc or sphere outline. Also fill flag if there's
	 * a default background color for the disc or sphere.
	 */
	public void drawSphDisc(boolean fill) { 
		if (geometry==0) return;
		Graphics2D g2=parent.imageContextReal;
		old_color=g2.getColor();
		Ellipse2D.Double shape = new Ellipse2D.Double(parent.toPixX(-1.0),
				parent.toPixY(1.0),2.0*parent.pixFactor,
				2.0*parent.pixFactor);
		if (fill) { // fill also
			g2.setColor(CPBase.DEFAULT_CANVAS_BACKGROUND);
			g2.fill(shape);
		}
		Stroke tmpstroke=g2.getStroke();
		g2.setStroke(parent.defaultStroke);
		g2.setColor(Color.BLACK);
		
		g2.draw(shape); 
		
		// do it by hand instead?
/*		path.reset();
		int indx_inc=1;
		double rad=1.0;
		if (rad<cir_sizes[0]) 
			indx_inc=32;
		else if (rad<cir_sizes[1]) indx_inc=16;
		else if (rad<cir_sizes[2]) indx_inc=8;
		else if (rad<cir_sizes[3]) indx_inc=4;
		else if (rad<cir_sizes[4]) indx_inc=2; 
		path.moveTo(1.0,0.0);
		for (int i=indx_inc;i<128;i += indx_inc)
			path.lineTo(cosine[i],sine[i]);
		path.closePath();
		g2.draw(path);
*/		
		g2.setStroke(tmpstroke);
		g2.setColor(old_color);
	}
	
	/** Draw circle: if fill is true, then fill with context color,
	 * before drawing with col2 if col2!=null (typically col2 is
	 * the foreground color). if fill is false, draw only using col2.
	 * @param fill, boolean
	 * @param col2, Color
	 */
	public void drawIt(boolean fill,Color col2) { // to imagebuffer
		drawIt(fill,col2,parent.imageContextReal);
	}
	
	/** Draw circle using preset context color */
	public void drawIt() { // unfilled circle to imagebuffer
		drawIt(false,null,parent.imageContextReal);
	}
		
	/**
	 * The principal color (col1) is that of graphics context. 
	 * There may be a secondary color. Possibilities for (fill,col2): 
	 *    (false,*): draw in principal color
	 *    (true,colr): fill with col1, draw bdry in col2
	 *    (true,null): fill with col1, don't draw bdry
	 * @param fill, boolean: true (fill), false (draw)
	 * @param col, Color
	 */
	public void drawIt(boolean fill,Color col2,Graphics2D g2) {
		
		// arc only? must be euclidean
		if (start!=null) {
			if (geometry!=0)
				return;
			
			// chord or pie?
			int arctype=Arc2D.OPEN;
			if (fill) 
				arctype=Arc2D.PIE;
			
			Arc2D.Double arc=new Arc2D.Double(parent.toPixX(x-radius),
					parent.toPixY(y+radius),
					2.0*radius*parent.pixFactor,2.0*radius*parent.pixFactor,
					(double)start,(double)extent,arctype);
			// TODO: might try "area" in Graphics2D.
			if (!arc.intersects(g2.getClipBounds())) {  // view test
//				System.out.println("no hit");
				return;
			}
			if (fill) { 
				g2.fill(arc);
				if (col2==null) return;
				old_color=g2.getColor();
				g2.setColor(col2);
				g2.draw(arc);
				g2.setColor(old_color);
				return;
			}
			// draw options
			g2.draw(arc);
			return;
		}
		
		// handle sphere separately 
		if (geometry > 0) { 
			sphDrawIt(true,null,fill,col2,g2);
			return;
		}
		
		// hyp? convert data to eucl 
		if (geometry<0) 
			hyp2eucl();

		// draw using Ellipse2D.Double for circles larger than 5 pixels in radius;
		//   by hand with varying numbers of points for smaller circles
		double visSize=radius*parent.pixFactor; // size in pixels
		if (visSize<5) { // do by hand
			path.reset();
			int indx_inc=8; // default: 128/8 pts in polygonal approx
			if (visSize<2) 
				indx_inc=32;
			else if (visSize<4) 
				indx_inc=16;

			path.moveTo(parent.toPixX(radius+x),parent.toPixY(y));
			for (int i=indx_inc;i<128;i += indx_inc)
				path.lineTo(parent.toPixX(x+radius*cosine[i]),
						parent.toPixY(y+radius*sine[i]));
			path.closePath();
			if (!path.intersects(g2.getClipBounds())) // view test
				return;
			// fill options
			if (fill) {
				path.closePath();
				g2.fill(path);
				if (col2==null) return;
				old_color=g2.getColor();
				g2.setColor(col2);
				g2.draw(path);
				g2.setColor(old_color);
				return;
			}
			// draw options
			g2.draw(path);
			return;
		} 
		
		// else, use Ellipse2D
		Ellipse2D.Double shape=new Ellipse2D.Double(parent.toPixX(x-radius),
				parent.toPixY(y+radius),
				2.0*radius*parent.pixFactor,2.0*radius*parent.pixFactor);
		// TODO: might try "area" in Graphics2D.
		if (!shape.intersects(g2.getClipBounds())) {  // view test
//			System.out.println("no hit");
			return;
		}
		if (fill) { 
			g2.fill(shape);
			if (col2==null) return;
			old_color=g2.getColor();
			g2.setColor(col2);
			g2.draw(shape);
			g2.setColor(old_color);
			return;
		}
		// draw options
		g2.draw(shape);
		
		
		// Revert to hands-on drawing: (12/2009) still bugs in Ellipse2D
/*		path.reset();
		int indx_inc=1;
		double visSize=radius*parent.pixFactor; // size in pixels
		if (visSize<cir_sizes[0]) // note problem (fixed above): using real world size 
			indx_inc=32;
		else if (visSize<cir_sizes[1]) 
			indx_inc=16;
		else if (visSize<cir_sizes[2]) indx_inc=8;
		else if (visSize<cir_sizes[3]) indx_inc=4;
		else if (visSize<cir_sizes[4]) indx_inc=2; 

		path.moveTo(parent.toPixX(radius+x),parent.toPixY(y));
		int i=indx_inc;
		for (i=indx_inc;i<128;i += indx_inc)
			path.lineTo(parent.toPixX(x+radius*cosine[i]),
					parent.toPixY(y+radius*sine[i]));
		path.closePath();
		// fill options
		if (fill) {
			path.closePath();
			g2.fill(path);
			if (col2==null) return;
			old_color=g2.getColor();
			g2.setColor(col2);
			g2.draw(path);
			g2.setColor(old_color);
			return;
		}
		// draw options
		g2.draw(path);
	*/	
	}
	
	/**
	 * Options of two colors, fill and bdry. If either is null, then
	 * default to the current graphics context color, usually fg. 
	 * @param draw boolean, draw object?
	 * @param bcolor Color, for object, may be null
	 * @param fill boolean, fill?
	 * @param fcolor Color, for fill, may be null
	 */
	public void drawIt(boolean draw,Color bcolor,boolean fill,Color fcolor) {
		drawIt(draw,bcolor,fill,fcolor,parent.imageContextReal);
	}
	
	/**
	 * Options of two colors, fill and bdry. If either is null, then
	 * default to the current graphics context color, usually fg. 
	 * @param draw boolean, draw object?
	 * @param bcolor Color, for object, may be null
	 * @param fill boolean, fill?
	 * @param fcolor Color, for fill, may be null
	 * @param g2 Graphics2D
	 */
	public void drawIt(boolean draw,Color bcolor,boolean fill,Color fcolor,Graphics2D g2) {
		
		// will we change colors?
		Color origcolor=null;
		if ((draw && bcolor!=null) || (fill && fcolor!=null))
			origcolor=g2.getColor();
		
		// When 'start' is not null, want arc only (only for euclidean packing)
		//    for drawing "sectors" of circles
		if (start!=null) {
			if (geometry!=0)
				return;
			
			// chord or pie?
			int arctype=Arc2D.OPEN;
			if (fill) 
				arctype=Arc2D.PIE;
			
			Arc2D.Double arc=new Arc2D.Double(parent.toPixX(x-radius),
					parent.toPixY(y+radius),
					2.0*radius*parent.pixFactor,2.0*radius*parent.pixFactor,
					(double)start,(double)extent,arctype);
			// TODO: might try "area" in Graphics2D.
			if (!arc.intersects(g2.getClipBounds())) {  // view test
//				System.out.println("no hit");
				return;
			}
			// fill option
			if (fill) {
				if (fcolor!=null)
					g2.setColor(fcolor);
				g2.fill(arc);
			}
			// draw option
			if (draw) {
				if (bcolor!=null)
					g2.setColor(bcolor);
				else if (origcolor!=null)
					g2.setColor(origcolor);
				g2.draw(arc);
			}
			// reset color?
			if (origcolor!=null)
				g2.setColor(origcolor);
			return;
		}
		
		// handle sphere separately 
		if (geometry > 0) { 
			sphDrawIt(draw,bcolor,fill,fcolor,g2);
			return;
		}
		
		// hyp? convert data to eucl 
		if (geometry<0) 
			hyp2eucl();

		// draw using Ellipse2D.Double for circles larger than 5 pixels in radius;
		//   by hand with varying numbers of points for smaller circles
		double abrad=Math.abs(radius); // in case radius is negative
		double visSize=abrad*parent.pixFactor; // size in pixels
		if (visSize<5) { // do by hand
			path.reset();
			int indx_inc=8; // default: 128/8 pts in polygonal approx
			if (visSize<2) 
				indx_inc=32;
			else if (visSize<4) 
				indx_inc=16;

			path.moveTo(parent.toPixX(abrad+x),parent.toPixY(y));
			for (int i=indx_inc;i<128;i += indx_inc)
				path.lineTo(parent.toPixX(x+abrad*cosine[i]),
						parent.toPixY(y+abrad*sine[i]));
			path.closePath();
			if (!path.intersects(g2.getClipBounds())) // view test
				return;
			// fill option
			if (fill) {
				// TOTO: if radius < 0, need to fill outside
				path.closePath();
				if (fcolor!=null)
					g2.setColor(fcolor);
				if (radius<0) {
					Area outdisc=new Area(
							new Rectangle(0,0,parent.pixWidth,parent.pixHeight));
					Area ell=new Area(path);
					outdisc.subtract(ell);
					g2.fill(outdisc);
				}
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
		} 
		
		// else, use Ellipse2D
		Ellipse2D.Double shape=new Ellipse2D.Double(parent.toPixX(x-abrad),
				parent.toPixY(y+abrad),
				2.0*abrad*parent.pixFactor,2.0*abrad*parent.pixFactor);
		// TODO: might try "area" in Graphics2D.
		if (!shape.intersects(g2.getClipBounds())) {  // view test
//			System.out.println("no hit");
			return;
		}
		// fill option
		if (fill) {
			if (fcolor!=null)
				g2.setColor(fcolor);
			if (radius<0) {
				Area outdisc=new Area(
						new Rectangle(0,0,parent.pixWidth,parent.pixHeight));
				Area ell=new Area(shape);
				outdisc.subtract(ell);
				g2.fill(outdisc);
			}
			// TOTO: if radius < 0, need to fill outside
			else {
				g2.fill(shape);
			}
		}
		// draw option
		if (draw) {
			if (bcolor!=null)
				g2.setColor(bcolor);
			else if (origcolor!=null)
				g2.setColor(origcolor);
			g2.draw(shape);
		}
		// reset color?
		if (origcolor!=null)
			g2.setColor(origcolor);
		return;	
		
		// Revert to hands-on drawing: (12/2009) still bugs in Ellipse2D
/*		path.reset();
		int indx_inc=1;
		double visSize=abrad*parent.pixFactor; // size in pixels
		if (visSize<cir_sizes[0]) // note problem (fixed above): using real world size 
			indx_inc=32;
		else if (visSize<cir_sizes[1]) 
			indx_inc=16;
		else if (visSize<cir_sizes[2]) indx_inc=8;
		else if (visSize<cir_sizes[3]) indx_inc=4;
		else if (visSize<cir_sizes[4]) indx_inc=2; 

		path.moveTo(parent.toPixX(abrad+x),parent.toPixY(y));
		int i=indx_inc;
		for (i=indx_inc;i<128;i += indx_inc)
			path.lineTo(parent.toPixX(x+abrad*cosine[i]),
					parent.toPixY(y+abrad*sine[i]));
		path.closePath();
		// fill options
		if (fill) {
			path.closePath();
			g2.fill(path);
			if (col2==null) return;
			old_color=g2.getColor();
			g2.setColor(col2);
			g2.draw(path);
			g2.setColor(old_color);
			return;
		}
		// draw options
		g2.draw(path);
	*/	
	}
	
	public void setData(double X,double Y,double Rad) {
		x=X;
		y=Y;
		radius=Rad;
		if (geometry>0 && radius>Math.PI) radius=Math.PI;
	}
	
	public void setX(double X) {
		x = X;
	}

	public void setY(double Y) {
		y = Y;
	}

	public void setRadius(double r) {
		radius = r;
	}

	public double getRadius() {
		return radius;
	}

	// used to display info on selected circle
	public String toString() {
		String s = "";
		String X = util.MathUtil.d2String(this.x);
		String Y = util.MathUtil.d2String(this.y);
		String R = util.MathUtil.d2String(this.radius);
		s = "Center: (" + X + "," + Y + ")";
		if (radius >= 0)
			s += " Radius: " + R;
		else
			s += " Radius: Infinity";
		return s;
	}
	
	/**
	 * Manage various passes through 'sphDrawOnFront'. One pass
	 * for front and, if sphere is not opaque, one pass (with
	 * color 'wash') for back. In each pass may involve fill
	 * and draw operations with separate colors. Possibilities for 
	 * (fill,col2) parameters (applying in both passes:	 
	 *    (false,*): draw in context's color
	 *    (true,colr): fill with col1, draw bdry in col2
	 *    (true,null): fill with col1, don't draw bdry
	 * @param draw boolean
	 * @param bcolor Color, object color, may be null
	 * @param fill boolean
	 * @param fcolor Color, fill color, may be null
	 */
	public void sphDrawIt(boolean draw,Color bcolor,
			boolean fill,Color fcolor) {
		sphDrawIt(draw,bcolor,fill,fcolor,parent.imageContextReal);
	}
	
	/**
	 * Manage various passes through 'sphDrawOnFront'. One pass
	 * for front and, if sphere is not opague, one pass (with
	 * color 'wash') for back. In each pass may involve fill
	 * and draw operations with separate colors. Possibilities for 
	 * (fill,col2) parameters (applying in both passes:	 
	 *    (false,*): draw in context's color
	 *    (true,colr): fill with col1, draw bdry in col2
	 *    (true,null): fill with col1, don't draw bdry
	 * @param draw boolean
	 * @param bcolor Color, object color, may be null
	 * @param fill boolean
	 * @param fcolor Color, fill color, may be null
	 * @param g2 Graphics2D 
	 */
	public void sphDrawIt(boolean draw,Color bcolor,
			boolean fill,Color fcolor,Graphics2D g2) {
		
		boolean backtoo=parent.sphereOpacity<250;
		
		// will we change colors?
		Color holdColor=null;
		if ((draw && bcolor!=null) || (fill && fcolor!=null) || backtoo)
			holdColor=g2.getColor();
		
		int m=0;
		path.reset();
		try {
			m=ellCreateOnFront(path,new Complex(x,y),radius,fill,parent);
		} catch(Exception dex) {}
		if (m>=0 && path.intersects(g2.getClipBounds())) { // some or all on front
			if (fill) {
				if (fcolor!=null) 
					g2.setColor(fcolor);
				g2.fill(path);
				g2.setColor(holdColor);
			}
			if (draw) {
				if (bcolor!=null)
					g2.setColor(bcolor);
				g2.draw(path);
			}
		}

		// Do we need to do the back? we need to wash out the colors
		if (backtoo && m!=0) { 

			// reflect in yz-plane and treat as though on front
			if (x>=0.0) x=Math.PI-x;
			else x=-(Math.PI+x);
			path.reset();
			try {
				m=ellCreateOnFront(path,new Complex(x,y),radius,fill,parent);
			} catch(Exception dex) {}
			if (m>=0 && path.intersects(g2.getClipBounds())) { // some/all on back (now the front)
				if (fill) {
					if (fcolor!=null)
						g2.setColor(ColorUtil.ColorWash(fcolor,parent.sphereOpacity/255.0));
					g2.fill(path);
				}
				if (draw) {
					if (bcolor!=null)
						g2.setColor(ColorUtil.ColorWash(bcolor,parent.sphereOpacity/255.0));
					else
						g2.setColor(ColorUtil.ColorWash(holdColor,parent.sphereOpacity/255.0));
					g2.draw(path);
				}
			}
		}
		if (holdColor!=null)
			g2.setColor(holdColor);
		return;
	}
	
	/**
	 * Convert x,y,radius from hyp info to eucl. Note that 'radius' 
	 * starts as an 'x-radius'; it's converted to s-radius in the computations.
	 * Recall x=1-exp(-2h)=1-s^2.
	 */
	public void hyp2eucl() {
	   if(radius < 0){ // negative means infinite hyperbolic radius; 
		   // value is (should be) negative of eucl radius of horocycle,
		   // center should be on unit circle.
		   radius *= -1.0;
		   double a = 1 - radius;
		   x*=a;
		   y*=a;
		   return;
	   }
	   double s_rad = Math.sqrt(1 - radius); // convert to s-rad
	   double ahc = MathComplex.abs(x,y);
	   if(ahc > 0.999999999999) // very close to unit circle
		   ahc = 0.999999999999;

	   double g = ((1 + ahc) / (1 - ahc));
	   double a = g / s_rad;
//System.err.println("s_rad "+s_rad);
	   double d = (a - 1) / (a + 1);
	   double b = g * s_rad;
	   double k = (b - 1) / (b + 1);
	   double aec = (d + k) * 0.5; 
	   radius=(d-k)*0.5; // eucl radius
	   
	   if (ahc>.000000000001) {
		   b = aec / ahc;
		   x*=b;
		   y*=b;
	   }
	   return;
	}
	
	/**
	 * Add circle to 'gpath', using Ellipse2D. Calling routine 
	 * may close gpath
	 * @param path, Path2D.Double; new or reset, we add segments
	 * @param z, spherical 'apparent' center (x,y) (theta,phi).
	 * @param r, spherical radius 
	 * @param cp, true ==> will be closing
	 * @return 1 if path was augmented, -1 if okay, but, eg., on back, 
	 * 0 if wholly on front.
	 */
	public static int ellCreateOnFront(Path2D.Double path,
			Complex z,double r,boolean cp,CPdrawing cpS) {

		/**
		 * Strategy: Compute circle 3D center C on the sphere;
		 * r is the spherical radius. (Recall that x-axis is directly 
		 * towards the viewer, y-axis is horizontal to right, z-axis
		 * is vertical.)
		*/
		
		Point3D C=new Point3D(z.x,z.y);
		
		/* Start by building ellipse in standard position:
		 * namely, assume circle is in a vertical plane, so its
		 * center is on the xy-plane. Let L denote the y-coord of
		 * the center; we assume L >= 0, so vector to center lies 
		 * in xy-plane pointing in positive y direction. We can
		 * continue to use X as the x-coord of the center. 
		 * (Note: conceptually, we've rotated the actual situation
		 * around the x-axis.) (Note: L^2 = 1-X^2 = Y^2+Z^2.) */
		
		double L=Math.sqrt(1-C.x*C.x);
		 
		 /* Our perspective on the circle is toward the origin 
		  * from out along the ray through its center. The circle is 
		  * parameterized (angle t=0) starting to our right (negative x 
		  * direction) where it leaves the xy-plane. The radius of the
		  * circle (eucl radius in its plane) is sin(r). */  

		double sinr=Math.sin(r);
		double cosr=Math.cos(r);
		
		/* The vector to the center is rotated by angle mu
		 * out of the yz-plane, where sin(mu) = C.x. The circle will
		 * fail to cross the yz-plane
		 *    |mu| <= r; in this case, it is visible iff C.x>0
		 *    |mu| > Pi-r; in this case, circle visible iff C.x<0,
		 *           fill either whole hemisphere iff C.x > 0,
		 *           annulus iff C.x < 0.
		 * proceed to the drawing operations. */
		
		double mu = Math.asin(C.x); // note: |mu| <= Pi/2
		
		// Some quick eliminations: 
		// ones entirely (essentially) on back
		if (Math.abs(mu)-r >= -.00001 && C.x<0) return -1;
		// large circle  
		if (Math.abs(mu)>Math.PI-r) {
			// on back; if not filled, return
			if (C.x>0 && !cp) return -1;
			if (cp) { // if filled, put unit circle in path
				Shape sp=new Ellipse2D.Double(cpS.toPixX(-1.0),
						cpS.toPixY(1.0),2.0*cpS.pixFactor,
						2.0*cpS.pixFactor);
				path.append(sp,true);
				// center on front? done
				if (C.x>0) return 1;
				
				// now have to build annulus; unit circle already set
				path.closePath(); 
				// with circle itself, bounds annulus
				
				Arc2D.Double ell=new Arc2D.Double(cpS.toPixX(L*cosr-Math.abs(C.x)*sinr),
						cpS.toPixY(sinr),cpS.pixFactor*2.0*Math.abs(C.x)*sinr,
						cpS.pixFactor*2.0*sinr,0,360,Arc2D.OPEN);
				path.append((Shape)ell,false);
				AffineTransform rot=new AffineTransform();
				double origx=cpS.toPixX(0.0);
				double origy=cpS.toPixY(0.0);
				rot.translate(origx,origy);
				rot.rotate(-Math.atan2(C.z,C.y));
				rot.translate(-origx,-origy);
				path.transform(rot);
				return 1;
			}
		}
		
		/* For the rest, need first to build the visible arc. 
		 *   Later will add arc of horizon, if called for, and
		 *   then rotate to correct orientation.
		 * The data on the ellipse (the circle as seen in perspective
		 * by the viewer from the x-direction) is:
		 *    height: sin(r)
		 *    width: |sin(mu)|*sin(r)=|X|*sin(r)
		 *    major axis (in z direction): 2*sin(r)
		 *    minor axis (y direction): 2*|X|*sin(r)
		 *    center: ( L*cos(r), 0) (in yz-plane) 
		 *    lower left corner: ( L*cos(r)-width,0-height)*/
		
		double width=Math.abs(C.x)*sinr;
		double centx=L*cosr;
		
		// entirely (essentially) on front
		if (Math.abs(mu)-r >= -.00001) {
			Shape sp=new Ellipse2D.Double(cpS.toPixX(centx-Math.abs(C.x)*sinr),
					cpS.toPixY(sinr),
					cpS.pixFactor*2.0*width,cpS.pixFactor*2.0*sinr);
			path.append(sp,true);
		}
		
		// intersects the horizon
		else {
		
			/* If |mu|<r (by some tolerance), then a computation 
			 * shows that the parameter value t0 where the circle first
			 * enters the hemisphere towards the viewer (positive x) is:
			 *    t0 = arccos((X*cos(r)) / (L*sin(r))) (always >= 0)
			 * and it leaves at 2*pi - t0 (always >= t0). Extent is
			 * 2*(pi-t0). Convert these to degrees. */
		
			double t0=Math.acos(C.x*cosr/(L*sinr));
			if (Double.isNaN(t0)) t0=0.0;
		 
			/* NOTE: in the java routines that draw ellipses, the SAME
			 * angle parameter is used as with the circle; one does NOT
			 * have to adjust the angle parameters to account for the
			 * eccentricity of the ellipse. (It's as though the drawing
			 * were done and then the circle is squashed to the ellipse.)
			 * 
			 *  Arc depends on X (and all must be converted to degrees)
			 *  but does not depend on r:
			 *  (i) X = 0 (up to some error): edge-on, use straight line
			 *    [(L*cos(r),height), (L*cos(r), -height)] */
		 
			// circle essentially edge-on; oriented line, top to bottom
			Shape ac=null;
			if (Math.abs(C.x)<.0001) { 
				path.moveTo(cpS.toPixX(centx),cpS.toPixY(sinr));
				path.lineTo(cpS.toPixX(centx),cpS.toPixY(-sinr));
				t0=Math.PI/2.0;
			}
			
			//  X > 0: starting angle: t0, extent: 2(pi-t0) */
			else if (C.x>0) {
				ac=new Arc2D.Double(cpS.toPixX(centx-Math.abs(C.x)*sinr),
						cpS.toPixY(sinr),
						cpS.pixFactor*2.0*width,cpS.pixFactor*2.0*sinr,
						rad2deg*t0,2.0*rad2deg*(Math.PI-t0),Arc2D.OPEN);
				path.append(ac,true);
			}
			// X < 0: starting angle: Pi-t0, extent: -2*(Pi-t0) (clockwise)
			else {
				ac=new Arc2D.Double(cpS.toPixX(centx-Math.abs(C.x)*sinr),
						cpS.toPixY(sinr),
						cpS.pixFactor*2.0*width,cpS.pixFactor*2.0*sinr,
						rad2deg*(Math.PI-t0),-2.0*rad2deg*(Math.PI-t0),Arc2D.OPEN);
				path.append(ac,true);
			}
		
			// For filled circles we have to append part of the 
			//    horizon, properly oriented.
			if (cp) {
			 /*  Set c=arcsin(sin(t0)*sin(r)) (always positive);
			 *  if r > Pi/2, set c = Pi-c;      
			 *  Arc of horizon starts at -c and has extent 2*c.*/
				double c=rad2deg*Math.asin(Math.sin(t0)*sinr);
				if (r>Math.PI/2.0) c=180-c;
				Shape unitarc=new Arc2D.Double(cpS.toPixX(-1.0),
						cpS.toPixY(1.0),cpS.pixFactor*2.0,
						cpS.pixFactor*2.0,
						-c,2.0*c,Arc2D.OPEN);
				path.append(unitarc,true);
			}				

			 
			/* (7). There are several other considerations:
			 * 
			 *   (a). If circle is drawn for the boundary of the sphere, then
			 *   have redraw it at end (after all drawing operations?)
			 *   
			 *   (b). If circle is fully on back but r >= Pi/2 and we are drawing
			 *   a filled circle, then we have to fill whole sphere.
			 *   
			 *   (c). If circle is fully on front but r >= Pi/2 and we are drawing
			 *   a filled circle, then we have to color an annular region between
			 *   the circle and the unit circle.*/

		} // done with appending horizon
		
		// image has to be rotated about origin to put its center in the 
		//   correct yz-location: in particular, rotate -alpha where 
		//   alpha = arctan2(C.z,C.y).
		AffineTransform rot=new AffineTransform();
		double origx=cpS.toPixX(0.0);
		double origy=cpS.toPixY(0.0);
		rot.translate(origx,origy);
		rot.rotate(-Math.atan2(C.z,C.y));
		rot.translate(-origx,-origy);
		path.transform(rot);
		return 1;
	}
	
}
