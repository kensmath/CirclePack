package posting;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import circlePack.PackControl;
import complex.Complex;
import exceptions.DrawingException;
import exceptions.InOutException;
import geometry.CircleSimple;
import geometry.HypGeodesic;
import geometry.HyperbolicMath;
import geometry.SphGeodesic;
import geometry.SphericalMath;
import graphObjects.CPEdge;
import math.Point3D;
import packing.CPdrawing;
import tiling.SubdivisionRules;
import tiling.TileRule;
import util.ColorUtil;
import util.DispFlags;
	
/**
 * This contains methods for adding circle/faces/edges/etc to postscript
 * files; it is created by/dies with 'PostManager'.
 * 
 * Note: these are largely parallel to drawing routines (e.g., in 'CPCircle')
 * HOWEVER, for spherical cases (unlike the drawing routines, which know
 * their environment), the calling routine must move the points to the 
 * 'apparent' sphere.
 * @author kens
 */
public class PostFactory {

	static double rad2deg=180/Math.PI;
	
	// pointer to PostManager.BufferedWriter; updated whenever fp is created
	BufferedWriter fp; // RandomAccessFile fp;
	static final double DEGPI=180/Math.PI;
	static final int MAX_BOTTOM_TEXT_LENGTH=300;
	
	// Constructor
	public PostFactory() {
		fp=null;
	}
	
	/**
	 * post open circle, normal color, thickness
	 * @param hes
	 * @param z
	 * @param rad
	 */
	public void postCircle(int hes,Complex z,double rad) {
		postCircle(hes,z,rad,null,null,-1.0);
	}
	
	/**
	 * post open circle with thickness
	 * @param hes
	 * @param z
	 * @param rad
	 * @param tx, double thickness factor if > 0
	 */
	public void postCircle(int hes,Complex z,double rad,double tx) {
		postCircle(hes,z,rad,null,null,tx);
	}
	
	/**
	 * post filled circle, normal thickness
	 * @param hes
	 * @param z
	 * @param rad
	 * @param col
	 */
	public void postFilledCircle(int hes,Complex z,double rad,Color col) {
		postCircle(hes,z,rad,col,null,-1.0); 
	}

	/**
	 * post filled circle with thickness
	 * @param hes int
	 * @param z Complex
	 * @param rad double
	 * @param col Color
	 * @param tx double, thickness factor if > 0
	 */
	public void postFilledCircle(int hes,Complex z,double rad,Color col,double tx) {
		postCircle(hes,z,rad,col,null,tx); 
	}

	/**
	 * post open circle, with bdry color, normal thickness
	 * @param hes int
	 * @param z Complex
	 * @param rad double
	 * @param bcol Color, boundary
	 */
	public void postColorCircle(int hes,Complex z,double rad,Color bcol) {
		postCircle(hes,z,rad,null,bcol,-1.0);
	}
	
	/**
	 * post open circle, with bdry color, thickness
	 * @param hes int
	 * @param z Complex
	 * @param rad double
	 * @param bcol Color
	 * @param tx double, thickness factor if > 0
	 */
	public void postColorCircle(int hes,Complex z,double rad,Color bcol,double tx) {
		postCircle(hes,z,rad,null,bcol,tx);
	}

	/**
	 * post filled circle, color, normal thickness
	 * @param hes int
	 * @param z Complex
	 * @param rad double
	 * @param col Color, interior
	 * @param bcol Color, boundary
	 */
	public void postFilledColorCircle(int hes,Complex z,double rad,Color col,Color bcol) {
		postCircle(hes,z,rad,col,bcol,-1.0);
	}

	/**
	 * post filled circle, color, given thickness
	 * @param hes
	 * @param z
	 * @param rad
	 * @param col
	 * @param bcol
	 * @param tx, double thickness factor if > 0
	 */
	public void postFilledColorCircle(int hes,Complex z,double rad,Color col,Color bcol,double tx) {
		postCircle(hes,z,rad,col,bcol,tx);
	}

	/** 
	 * Draw a circle in postscript given z, rad and geometry. 
	 * Possible color arguments (col,bcol): col!=null is fill color; 
	 * bcol!=null is bdry color.
	 * @param hes int, geometry
	 * @param z Complex, center
	 * @param rad double, radius
	 * @param col Color, fill color if not null
	 * @param bcol Color, bdry color if not null
	 * @param tx double, thickness factor if > 0
	 */
	public void postCircle(int hes,Complex z,double rad,
			Color col,Color bcol,double tx) {
		try {
			// handle sphere separately
			if (hes>0) {
				boolean needfill=false;
				if (col!=null) needfill=true;
				String pathstr=sphCirclePath(z,rad,needfill);
				if (pathstr!=null) {
					fp.write("gs "+pathstr);
					if (tx>0)
						fp.write("getlinewidth "+String.format("%.6e",tx)+
								" mul ourlinewidth\n");
					if (col!=null) // fill with given color
						fp.write(" gs "+postRGBsrgb(col)+" fill gr\n"); 
					if (bcol!=null) // stroke with given boundary color
						fp.write(" gs "+postRGBsrgb(bcol)+" s gr\n");
					else fp.write(" s "); // stroke
					fp.write(" gr\n"); 
				}
				return;
			}
			// hyp? convert data to euclidean
			if (hes<0) {
				CircleSimple sc=HyperbolicMath.h_to_e_data(z,rad);
				z=sc.center;
				rad=sc.rad;
			}
			if (tx>0)
				fp.write(" gs getlinewidth "+String.format("%.6e",tx)+
						" mul ourlinewidth\n");
			if (col!=null) // fill with given color
				fp.write(postRGB(col)+z.x+" "+z.y+" "+rad+" cd \n");
			if (bcol!=null) // stroke with given boundary color
				fp.write(postRGB(bcol)+z.x+" "+z.y+" "+rad+" cc \n");
			else fp.write(z.x+" "+z.y+" "+rad+" c\n");
			if (tx>0) fp.write(" gr\n");
		} catch(IOException iox) {}
	}

	/**
	 * post the unit circle, default line thickness.
	 * @param hes
	 * @param z, center needed in sph case as apparent center
	 * @return
	 */
	public int postUnitCircle(int hes,Complex z) {
		try {
			fp.write("gs 1.0 ourlinewidth ");
			if (hes>0) postCircle(hes,z,Math.PI/2.0);
			else postCircle(hes,z,1.0);
			fp.write(" gr\n");
		} catch(IOException iox) {}
		return 1;
	}
	
	/**
	 * Draw a circular eucl "pie" sector, possibly with color
	 * @param z, center
	 * @param rad, radius
	 * @param arg1, start argument
	 * @param extent, counterclockwise extent in radians
	 * @param col, -1 if no color
	 * @return
	 */
	public int postSector(Complex z,double rad,double arg1,double extent,Color col) {
		try {
			double x=z.x+rad*Math.cos(arg1);
			double y=z.y+rad*Math.sin(arg1);
			fp.write("n "+z.x+" "+z.y+" m\n"+x+" "+y+" l\n");
			fp.write(z.x+" "+z.y+" "+rad+" "+arg1*180.0/Math.PI+" "+(arg1+extent)*180.0/Math.PI+" a cp ");
			if (col!=null) // fill with given color
				fp.write(" gs"+postRGBsrgb(col)+" fill gr ");
			fp.write(" s\n");
		} catch(IOException iox) {}
		return 1;
	}
	
	/**
	 * post edge, normal color, thickness
	 * @param hes
	 * @param p1
	 * @param p2
	 * @return
	 */
	public int postEdge(int hes,Complex p1,Complex p2) {
		return postColorEdge(hes,p1,p2,ColorUtil.FG_Color,-1.0);
	}
	
	/**
	 * post edge, normal color, given thickness
	 * @param hes
	 * @param p1
	 * @param p2
	 * @param tx, double thickness factor
	 * @return
	 */
	public int postEdge(int hes,Complex p1,Complex p2,double tx) {
		return postColorEdge(hes,p1,p2,ColorUtil.FG_Color,tx);
	}
	
	/**
	 * post edge with color, thickness options
	 * @param hes
	 * @param p1
	 * @param p2
	 * @param col
	 * @param tx, double thickness factor, if > 0
	 * @return
	 */
	public int postColorEdge(int hes,Complex p1,Complex p2,Color col,double tx) {
		try {
			fp.write("gs n ");
			if (tx>0.0) 
				fp.write(" getlinewidth "+String.format("%.6e",tx)+" mul ourlinewidth\n");
			// sphere first
			if (hes>0) {
				Point3D start=new Point3D(p1.x,p1.y);
				SphGeodesic sg=new SphGeodesic(start,new Point3D(p2.x,p2.y));
				if (sg.isVisible()) {
					Complex a=SphericalMath.sphToVisualPlane(sg.z1.x,sg.z1.y);
					fp.write(a.x+" "+a.y+" m "); // move to first end
					String pathstr=sphEdgePath(sg);
					fp.write(pathstr);
					if (col!=ColorUtil.FG_Color) 
						fp.write(" "+postRGBsrgb(col));
				}
			}
			else if (hes<0) { // hyp
				HypGeodesic geo = new HypGeodesic(p1,p2);
				fp.write(postRGBsrgb(col)+p1.x+" "+p1.y+" m ");
				fp.write(hg2PS(geo));
			}
			else {
				fp.write(postRGBsrgb(col)+p1.x+" "+p1.y+" m\n"+p2.x+" "+p2.y+" l ");
			}
			fp.write(" s gr\n");
			return 1;
		} catch(IOException iox) {}
		return 1;
	}

	// TODO: rationalize face/poly posting routines (parallel to drawing)
	
	/**
	 * Post polygon with all options
	 * @param hes, int
	 * @param Z Complex[]
	 * @param fcol Color; null if fill=false
	 * @param bcol Color; null if draw=false
	 * @param tx double; thickness as adjusted for posting
	 * @return 0 on exception or nothing shows, else 1
	 */
	public int post_Poly(int hes,Complex []Z,Color fcol,Color bcol,double tx) {
		
		try {
			// sph requires more work
			if (hes>0) {
				int n=Z.length;
				double []crns=new double[n*2+2];
				for (int j=0;j<n;j++) {
					crns[2*j]=Z[j].x;
					crns[2*j+1]=Z[j].y;
				}
				// close up
				crns[n*2]=crns[0];
				crns[n*2+1]=crns[1];
				
				// get visible path(s); size 0 means nothing shows
				Vector<String> paths=sphPolyPath(crns);
				
				if (paths.size()==0)
					return 0;

				// linewidth specification?
				fp.write(" gs "+getThickStr(tx)+"\n");

				// if to be filled, do that first (create one path)
				if (fcol!=null) {
					fp.write("gs "+sph_closed_Poly(crns)+" "+
							postRGBsrgb(fcol)+"fill gr\n");
				}
				if (bcol!=null) {
					Iterator<String> pit=paths.iterator();
					while (pit.hasNext()) {
						String pth=pit.next();
						fp.write("gs "+pth+" "+postRGBsrgb(bcol)+"s gr\n");
					}
				}
				fp.write(" gr\n\n");
				return 1;
			}
			else {
				if (fcol!=null) 
					fp.write("gs "+poly_outline(hes,Z,true)+" "+
							postRGBsrgb(fcol)+" fill gr\n");
				if (bcol!=null)
					fp.write("gs "+poly_outline(hes,Z,false)+" "+
							postRGBsrgb(bcol)+" s \n");
				
				fp.write(" gr\n\n"); // matched with gs for linewidth
				return 1;
			}
			
		} catch (Exception iox) {
			return 0;
		}
	}

	
	/**
	 * post open polygon, normal thickness
	 * @param hes int,geometry
	 * @param N int, number of vertices
	 * @param Z Complex[]
	 * @return int
	 */
	public int postPoly(int hes,int N,Complex []Z) {
		return postFilledColorPoly(hes,N,Z,null,null,-1.0);
	}

	/**
	 * post open polygon, given thickness
	 * @param hes int, geometry
	 * @param N int, number of corners
	 * @param Z Complex[]
	 * @param tx double; thickness factor if > 0
	 * @return int
	 */
	public int postPoly(int hes,int N,Complex []Z,double tx) {
		return postFilledColorPoly(hes,N,Z,null,null,tx);
	}

	/**
	 * post open polygon, normal thickness
	 * @param hes
	 * @param N
	 * @param Z
	 * @param bdry color
	 * @return
	 */
	public int postColorPoly(int hes,int N,Complex []Z,Color bcol) {
		return postFilledColorPoly(hes,N,Z,null,bcol,-1.0);
	}

	/**
	 * post open polygon, given thickness
	 * @param hes
	 * @param N
	 * @param Z
	 * @param bdry color
	 * @param tx double; thickness factor if > 0
	 * @return
	 */
	public int postColorPoly(int hes,int N,Complex []Z,Color bcol,double tx) {
		return postFilledColorPoly(hes,N,Z,null,bcol,tx);
	}

	/**
	 * post filled polygon, normal thickness
	 * @param hes
	 * @param N
	 * @param Z
	 * @param col
	 * @return
	 */
	public int postFilledPoly(int hes,int N,
			Complex []Z,Color col) {
		return postFilledColorPoly(hes,N,Z,col,null,-1.0);
	}
	
	/**
	 * post filled polygon, given thickness
	 * @param hes
	 * @param N
	 * @param Z
	 * @param col
	 * @param tx, double thickness factor if > 0
	 * @return
	 */
	public int postFilledPoly(int hes,int N,
			Complex []Z,Color col,double tx) {
		return postFilledColorPoly(hes,N,Z,col,null,tx);
	}
	
	public int postFilledColorPoly(int hes,int N,
			Complex []Z,Color col,Color bcol,double tx) {
		try {
			
			// TODO: more work needed in sph case if goes over horizon
			fp.write("gs ");
			if (tx>0.0) 
				fp.write("getlinewidth "+String.format("%.6e",tx)+
					" mul ourlinewidth\n");
			if (col!=null) 
				fp.write("gs "+poly_outline(hes,Z,true)+" "+
						postRGB(col)+" srgb fill gr\n");
			fp.write(poly_outline(hes,Z,false)+"\n");
			if (bcol!=null)
				fp.write(postRGB(bcol)+ " srgb ");
			fp.write(" s gr\n");
		} catch (Exception iox) {
			return 0;
		}
		return 1;
	}
	
	/**
	 * Returns visible segments of sph polygon as array of
	 * Path2D.Double, for use, eg, when drawing polygon bdry.
	 * (Compare to 'sphClosedPath' which adds segments of 
	 * horizon and also puts results in 'gpath'.)
	 * 
	 * @param []crns, double[], corners x,y (length is 2*N)
	 * @param N, int, number of corners
	 * @return Vector<Path2D.Double>, length 0 if nothing visible.
	 * If length=1, then entire polygon is visible, but calling 
	 * routine must decide whether to close the path.
	 */
	public static Vector<Path2D.Double> sphPolyBdry(double []crns,int N,CPdrawing cpS) 
	throws DrawingException {
		
		Vector<Path2D.Double> paths=new Vector<Path2D.Double>();
		
		// Cheap check: are all points invisible? Just check x-coords
		boolean hit=false;
		for (int j=0;(j<N && !hit);j++)
			if (Math.cos(crns[2*j])>0.0) hit=true;
		if (!hit) return paths; // nothing on front
		
		Point3D []pts=new Point3D[N];
		for (int j=0;j<N;j++)
			pts[j]=new Point3D(crns[2*j],crns[2*j+1]);
		
		SphGeodesic[] sg=new SphGeodesic[N];
		hit=false;
		boolean allFront=true;
		boolean allHorizon=true;
		int firstX=-1;
		for (int j=N-1;j>=0;j--) {
			sg[j]=new SphGeodesic(pts[j],pts[(j+1)%N]);
			if (sg[j].isVisible()) {
				hit=true;
				firstX=j;
				if (sg[j].hitHorizon())
					allFront=false;
				if (sg[j].followHorizon()!=1) { // not horizon or clockwise
					allHorizon=false;
				}
			}
		}
		if (!hit) return paths; // nothing on front
		
		// everything on front or around horizon (counterclockwise)
		if (allFront || allHorizon) {
			Path2D.Double oneseg=new Path2D.Double();
			CPEdge.sphCreateEdge(oneseg, sg[0], cpS);
			for (int i=1;i<N;i++) 
				CPEdge.sphCreateEdge(oneseg, sg[i], cpS);
			// Note, we don't close up
			paths.add(oneseg);
			return paths; // signals calling routine that there's nothing on back
		}
		
		if (firstX<0) 
			throw new DrawingException("Didn't find visible geodesic");

		SphGeodesic firstSG=sg[firstX];
		SphGeodesic nextSG=firstSG;
		int i=firstX;
		
		boolean needNew=true;
		Path2D.Double newseg=new Path2D.Double();
		
		// on each reentry, nextSG is visible but not yet added to path
		while (nextSG.isVisible() && i<N) {
			if (needNew) {
				newseg=new Path2D.Double();
				needNew=false;
			}

			// add geodesics until you get 'horizonEnd' (they should be visible)
			while (i<(N-1) && !nextSG.horizonEnd) {
				CPEdge.sphCreateEdge(newseg, nextSG, cpS);
				i++;
				nextSG=sg[i];
			}
			
			// if it went beyond horizon, store this path
			if (nextSG.horizonEnd) {
				paths.add(newseg);
				needNew=true;
			}

			// get next segment if there is one
			if (i<(N-1)) {
				i++;
				nextSG=sg[i];
			}
			
			// ignore geodesics until one becomes visible
			while (i<(N-1) && !nextSG.isVisible()) {
				i++;
				nextSG=sg[i];
			}

			// now have last segment
			if (i==(N-1)) {
				i=N; // kick out
				if (!nextSG.isVisible()) {
					return paths;
				}
				else { // visible, must have come from the back
					newseg=new Path2D.Double();
					CPEdge.sphCreateEdge(newseg, nextSG, cpS);
					paths.add(newseg);
				}
			}
		} // end of while	
		return paths;
	}		

	public void postIndex(Complex z,int n) {
		try {
			fp.write(new String(z.x+" "+z.y+" moveto\n("+n+")show\n"));
		} catch(IOException iox) {}
	}
	
	public void postStr(Complex z,String str) {
		try {
			fp.write(new String(z.x+" "+z.y+" moveto\n("+str+")show\n"));
		} catch(IOException iox) {}
	}
	
	public void postLine(int hes,Complex z,Complex w) {
		// TODO: have to do other geometries
		try {
			if (hes==0) fp.write("n "+z.x+" "+z.y+" moveto\n"+w.x+" "+w.y+" l  s\n");
		} catch(IOException iox) {}
	}
	
	/**
	 * Add Path2D.Double into postscript file as a eucl 
	 * polygonal path. 
	 * @param zpath, Vector<Vector<Complex>>
	 * @param col, 
	 * @param tx, double thickness factor if > 0
	 * @param clsd, boolean: true, then close the path
	 */
	public void postPath(Vector<Vector<Complex>> zpath,Color col,double tx,boolean clsd) {
		if (zpath==null || zpath.size()<1)
			return;
		try {
		fp.write("gs n \n");
		if (tx>0)
			fp.write("getlinewidth "+String.format("%.6e",tx)+
					" mul ourlinewidth\n");
		if (col!=null) // stroke with given color
			fp.write(postRGBsrgb(col)+"\n");
		for (int i=0;i<zpath.size();i++) {
			Vector<Complex> loc_path=zpath.get(i);
			Iterator<Complex> zp=loc_path.iterator();
			Complex z1=(Complex)zp.next();
			Complex z=null;
			fp.write(+z1.x+" "+z1.y+" m\n");
			while (zp.hasNext()) {
				z=(Complex)zp.next();
				fp.write(z.x+" "+z.y+" l\n");
			}
			// intended to be closed?
			if (clsd || z1.minus(z).abs()<.000001) fp.write("cp \n");
		}
		fp.write(" s gr\n");
		} catch (IOException iox) {
			throw new InOutException("Posting error: "+iox.getMessage());
		}
		return;
	}

	/**
	 * Add one or more paths to the postscript file using display 
	 * options. Path segments are prepared in calling routine: 
	 * In general there is just one segment for draw and/or fill, 
	 * but spherical case requires separate calls for 'fill', 
	 * where paths may be complicated by horizon segments, and 
	 * 'draw', which may contain open segments.
	 * @param zlists Vector,Vector,Complex
	 * @param dflags DispFlags
	 * @param tx double, thickness factor if > 0
	 * @param cp boolean, yes, close up
	 * @return int count of segments
	 */
	public int post_Path(Vector<Vector<Complex>> zlists,DispFlags dflags,double tx,boolean cp) {
		
		if (zlists==null || zlists.size()<1)
			return 0;
		
		int count=0;
		Color fcolor=null;
		Color bcolor=null;
		if (dflags.getColor()!=null) {
			if (dflags.fill)
				fcolor=dflags.getFillColor();
			if (dflags.draw && dflags.colBorder)
				bcolor=dflags.getColor();
		}	
		
		Iterator<Vector<Complex>> outVec=zlists.iterator();
		while (outVec.hasNext()) {
			Vector<Complex> loc_path=outVec.next();
			try {
				// fill first
				if (dflags.fill && fcolor!=null) {
					fp.write("gs n \n");
					fp.write(postRGBsrgb(fcolor)+"\n");
					Iterator<Complex> zp=loc_path.iterator();
					Complex z1=(Complex)zp.next();
					Complex z=null;
					fp.write(+z1.x+" "+z1.y+" m\n");
					while (zp.hasNext()) {
						z=(Complex)zp.next();
						fp.write(z.x+" "+z.y+" l\n");
					}
					fp.write(" cp fill gr\n");
				}
				if (dflags.draw) {
					fp.write("gs n \n");
					if (tx>0)
						fp.write("getlinewidth "+String.format("%.6e",tx)+
							" mul ourlinewidth\n");
					if (bcolor!=null) 
						fp.write(postRGBsrgb(fcolor)+"\n");
					Iterator<Complex> zp=loc_path.iterator();
					Complex z1=(Complex)zp.next();
					Complex z=null;
					fp.write(+z1.x+" "+z1.y+" m\n");
					while (zp.hasNext()) {
						z=(Complex)zp.next();
						fp.write(z.x+" "+z.y+" l\n");
					}
					fp.write(" s gr\n");
				}
				count++;
			} catch (IOException iox) {
				throw new InOutException("Posting error: "+iox.getMessage());
			}
		}
		
		return count;
	}
	
	/**
	 * Place a "mark" comand in the file, background color; assume trinket shape
	 * already defined (see 'post_shape'), also assume z is in visual plane 
	 * (e.g., in sph case). 'diam' is for scaling (shapes are roughly 2) 
	 * @param z, Complex in visual plane
	 * @param diam, double, typically 5/cpDrawing.pixFactor. 
	 */
	public void postTrinket(Complex z,double diam) {
		postColorTrinket(z,diam,ColorUtil.BG_Color);
	}
	
	/**
	 * Put "s" for stroke in the file
	 */
	public void postStroke() {
		try {
			fp.write("s\n");
		} catch (IOException iox) {return;}
	}
	
	/**
	 * Place a "mark" comand in the file with color; assume trinket shape
	 * already defined (see 'post_shape'), also assume z is in visual plane 
	 * (e.g., in sph case). 'diam' is for scaling (shapes are roughly 2) 
	 * @param z, Complex in visual plane
	 * @param diam, double, typically 5/cpDrawing.pixFactor. 
	 */
	public void postColorTrinket(Complex z,double diam,Color color) {
		try {
			fp.write(new String(postRGB(color)+diam+" "+diam+"  "+z.x+" "+z.y+" mark\n"));
		} catch (IOException iox) {}
	}
	
	/* Place a predefined 'trinket' shape in postscript file, position and
	 * (optional) color determined by vertex info. For the -t options: 
	 * -t -tc -t? -t?c, where '?' denotes an integer code of trinket 
	 * shape (parallel to screen trinkets in 'CPTrinket.java') and 'c'
	 * for color (defaults to grey). 
	 * Current shape options: 0=dot,1=square,2=triangle,3=plus,4=diamond.
	 * Trinkets are created with diameter about 2; they are scaled for screen
	 * size in calls to 'post_trinket'. Calls are 'r g b s s x y mark', 
	 * (r=g=b=.5 for default grey), s = screenwidth*10/1200 (to get roughly the 
	 * same proportions as on the canvas), and x y is real world center in
	 * the visual plane..
	 */
	public int post_shape(int trink) {
		try {
	    switch(trink) {
	    case 0: 
		{ 
		    fp.write("/mark {gs tr sc n 0 0 1 0 360 a srgb gs fill gr 0 sg 30 ourlinewidth s gr} def\n");
		    break;
		}
	    case 1: 
		{ 
		    fp.write("/mark {gs tr sc srgb n 1 1 moveto -2 0 "+
			     "rlineto 0 -2 rlineto 2 0 rlineto cp gs fill gr "+
			     "0 sg 30 ourlinewidth s gr} def\n");
		    break;
		}
	    case 2: // triangle
		{
		    fp.write("/mark {gs tr sc srgb n 0 1 moveto -1 -2 "+
			     "rlineto 2 0 rlineto cp gs fill gr 0 sg "+
			     "30 ourlinewidth s gr} def\n");
		    break;
		}
	    case 3: // plus
		{
		    fp.write("/mark {gs tr sc srgb n 1 .33 moveto -.67 0 "+
			     "rlineto 0 .67 rlineto -.67 0 rlineto "+
			     "0 -.67 rlineto -.67 0 rlineto 0 -.67 "+
			     "rlineto .67 0 rlineto 0 -.67 rlineto .67 0 "+
			     "rlineto 0 .67 rlineto .67 0 rlineto cp "+
			     "gs fill gr 0 sg 30 ourlinewidth s gr} def\n");
		    break;		
		}
	    case 4: // diamond
		{
		    fp.write("/mark {gs tr sc srgb n 0 1 moveto -1 -1 "+
			     "rlineto 1 -1 rlineto 1 1 rlineto cp "+
			     "gs fill gr 0 sg 30 ourlinewidth s gr} def\n");
		    break;		
		}
		// TODO: x shape not yet in postscript (4/2013)
//	    case 5: // xshape
//	    {
//	    	
//	    }
	    }
	    return 1;
		} catch (IOException iox) {return 0;}
	} 

	/** For opening/closing postscript file code when you want to limit effects 
	 * of the code.
	 */
	public void postGSave() {try { fp.write(" gs ");}catch(Exception ex){}}	
	public void postGRestore()  {try { fp.write(" gr ");}catch(Exception ex){}}

	/**
	 * If a postscript file is open, reset its linewidth to n*PS_UNIT_LINEWIDTH.
	 * @param n
	 */
	public void postLineThickness(int n) {
		if (n<0 || n>12) return;
		try {
			fp.write(new String((double)(n*PostManager.PS_UNIT_LINEWIDTH)+" ourlinewidth\n"));
		} catch(IOException iox) {}
		
	}
	
	/**
	 * Prepare string giving rgb colors for postscript line; for faces, have to add 'srgb'.
	 * @param col int index from 0 to 255
	 * @return String that goes in postscript file, just has <r> <g> <b> values between 0 and 1.
	 */
	public String postRGB(Color col) {
		return new String(String.format("%.4e",(double)((double)col.getRed()/255))+" "+
				String.format("%.4e",(double)((double)col.getGreen()/255))+" "+
				String.format("%.4e",(double)((double)col.getBlue()/255))+" ");
	}
	
	/**
	 * Prepare string of rgb colors, and include 'srgb' postscript command
	 * @param col int index from 0 to 255
	 * @return String for postscript file, just has <r> <g> <b> values between 0 and 1.
	 */
	public String postRGBsrgb(Color col) {
		return (postRGB(col)+" srgb ");
	}
	
	/**
	 * Create a string for use in postscript which describes a 
	 * polygonal path in the appropriate geometry. Calling routine
	 * must start/close the postscript description: e.g., "n ", " cp s\n", etc. 
	 * @param hes int, geometry
	 * 
	 * @param Z Complex[], (theta,phi) for spherical
	 * @param cp boolean: if true, then closeup in sph case
	 * @return 
	 */
	public String poly_outline(int hes,Complex []Z,boolean cp) {
		
		int N=Z.length;
		// spherical
		// TODO: have to handle closeup issue
		if (hes>0) { 
			double[] cnrs=new double[2*N];
			for (int i=0;i<N;i++) {
				cnrs[2*i]=Z[i].x;
				cnrs[2*i+1]=Z[i].y;
			}
			try {
				if (cp)
					return sph_closed_Poly(cnrs);
			} catch (Exception ex) {
				return null;
			}
		}			

		// hyp case
		if (hes < 0) {
			HypGeodesic []hg;
			hg=new HypGeodesic[N];
			for (int i=0;i<N;i++)
				hg[i] = new HypGeodesic(Z[i],Z[(i+1)%N]);
			StringBuilder str = new StringBuilder("n\n");
			str.append(Z[0].x + " " + Z[0].y + " m\n");
			for (int i=0;i<N;i++)
				str.append(hg2PS(hg[i]));
			str.append("cp\n");
			return str.toString();
		} 
		
		// eucl case
		else if (hes == 0) {
			StringBuilder str=new StringBuilder("n\n");
			str.append(Z[0].x+" "+Z[0].y+" m\n");
			for (int i=1;i<N;i++)
				str.append(" "+Z[i].x+" "+Z[i].y+" l\n");
			str.append("cp\n");
			return str.toString();
		}
		return "";
	}
	
	/** 
	 * Sting for postscript giving hyp geodesic. (Note: usual coord
	 * orientation in postscript, whereas canvasses flip the y-coord.)
	 * @param hg, Geodesic for hyp case
	 * @return string to append to postscript
	 */
	public static String hg2PS(HypGeodesic hg) {
		if (hg.lineFlag)
			return new String(hg.z2.x + " " + hg.z2.y + " l\n");
		if (hg.extent>=0) 
			return new String(hg.center.x +" "+hg.center.y+" "+hg.rad+" "+
					(hg.startAng*DEGPI)+" "+((hg.startAng+hg.extent)*DEGPI)+" a\n");
		return new String(hg.center.x +" "+hg.center.y+" "+hg.rad+" "+
				(hg.startAng*DEGPI)+" "+((hg.startAng+hg.extent)*DEGPI)+" an\n");
	}
	
	/**
	 * Accumulate text for bottom of the file in 'textBuffer'. No fancy editing:
	 * break into lines of length ??? separated by line breaks. End with line 
	 * break so next call will start new line. 
	 * @param text
	 */
	public void postString(String text) {
		int SL=60; // segment length
		if (PackControl.postManager.textBuffer==null) {
			PackControl.postManager.textBuffer=new StringBuilder();
			StringBuilder sb=PackControl.postManager.textBuffer;
			
			// preliminary stuff
			sb.append("\n% --- bottom text --- \n"+
					"  72 72 sc\n  4.25 5.5 tr\n"+
					"  /Times-Roman ff 0.22 scf sf\nn\n"+
					"  -3.5 -3.6 m\n");
			PackControl.postManager.textLineCount=0;
		}
		int len=text.length();
		
		// cut if too long
		if (len>MAX_BOTTOM_TEXT_LENGTH) 
			text=text.substring(0,MAX_BOTTOM_TEXT_LENGTH);
		String frag;
		int spot=0;
		while (spot<len) {
			int endindx=SL;
			if (len<SL) endindx=len;
			frag=text.substring(spot,endindx);
			PackControl.postManager.textBuffer.append("0.0 -0.2 rmoveto\n"+
					"gs ("+frag+") show gr\n");
			spot +=SL;
			PackControl.postManager.textLineCount++;
		}
	}
	
	/**
	 * Create a PostScript string which defines a new current path;
	 * 'gs', 'gr', 'stroke', and 'fill' are NOT included.
	 * @param z
	 * @param r
	 * @param cp, true: path to be closed up (possibly with horizon arc)
	 * @return PostScript string defining a new current path
	 */
	public String sphCirclePath(Complex z,double r,boolean cp) {

		StringBuilder strbuf=new StringBuilder(" ");
		
		// Strategy: see 'ellCreateOnFront'
		
		Point3D C=new Point3D(z.x,z.y);
		double L=Math.sqrt(1-C.x*C.x);
		double sinr=Math.sin(r);
		double cosr=Math.cos(r);
		double mu = Math.asin(C.x); // note: |mu| <= Pi/2
		if (Math.abs(C.x)>.9999) { // C on x-axis? round circle
			if (C.x<0) { // center on back
				if (r<Math.PI/2.0) return null; // on back
				if (cp) { // arc of circle and arc unit circle in one path
					return new String("n 0.0 0.0 "+String.format("%.6e",sinr)+" 0 360 arc "+
							" 1.0 0.0 m 0.0 0.0 1.0 0 360 arc ");
				}
				// circle only
				else return new String("n 0 0 "+String.format("%.6e",sinr)+" 0 360 arc ");
			}					
			if (C.x>0) { // center on front
				if (r<Math.PI/2.0) 
					return new String("n 0 0 "+String.format("%.6e",sinr)+" 0 360 arc ");
				if (cp) // return unit circle to fill
					return new String("n 0 0 1 0 360 arc ");
				return null; 
			}
		}
		
		String rot=new String(" "+String.format("%.6e",rad2deg*Math.atan2(C.z,C.y))+" rot ");
		String unrot=new String(" "+String.format("%.6e",(-rad2deg*Math.atan2(C.z,C.y)))+" rot ");
		String trans=new String(" "+String.format("%.6e",cosr*C.y)+" "+String.format("%.6e",cosr*C.z)+" tr ");
		String untrans=new String(" "+String.format("%.6e",(-cosr*C.y))+" "+String.format("%.6e",(-cosr*C.z))+" tr ");
		double wide=Math.abs(C.x)*sinr;
		double high=sinr;
		String scle=new String(" "+String.format("%.6e",wide)+" "+String.format("%.6e",high)+" sc ");
		String unscle=new String(" "+String.format("%.6e",1/wide)+" "+String.format("%.6e",1/high)+" sc ");

		// Some quick eliminations: 
		// ones entirely (essentially) on back
		if (Math.abs(mu)-r >= -.00001 && C.x<0) return null;
		// large circle  
		if (Math.abs(mu)>Math.PI-r) {
			// on back; if not filled, return
			if (C.x>0 && !cp) return null;
			if (cp) { // if filled, put unit circle in path
				// center on front? done
				if (C.x>0) return new String("n 0.0 0.0 1.0 0 360 arc \n");

				// else circle itself and unit circle bound annulus
				strbuf.append(" n "+trans+rot+"\n"+scle+
						"0 0 1 0 -360 arcn "+unscle+unrot+untrans+"\n"+
						"1 0 moveto 0 0 1 0 360 arc \n");
				return strbuf.toString();
			}
		}
		
		/* For the rest, need first to build the visible arc. 
		 *   Later will add arc of horizon, if called for */

		// entirely (essentially) on front
		if (Math.abs(mu)-r >= -.00001) { 
			return new String("n "+trans+rot+scle+" 0 0 1 0 360 arc "+
						unscle+unrot+untrans+"\n");
		}
		
		// intersects the horizon
		else {
			double t0=Math.acos(C.x*cosr/(L*sinr));
			if (Double.isNaN(t0)) t0=0.0;

			// circle essentially edge-on; oriented line, top to bottom
			if (Math.abs(C.x)<.0001) {
				strbuf.append("n "+rot+" "+String.format("%.6e",L*cosr)+" "+
						String.format("%.6e",sinr)+" m "+
						String.format("%.6e",L*cosr)+" "+
						String.format("%.6e",(-sinr))+" l \n");
				t0=Math.PI/2.0;
			}
			
			//  X > 0: starting angle: t0, extent: 2(pi-t0) */
			else if (C.x>0) {
				strbuf.append("n "+trans+rot+scle+" 0 0 1 "+
						String.format("%.6e",rad2deg*t0)+" "+
						String.format("%.6e",rad2deg*(2.0*Math.PI-t0))+" arc "+
						unscle+unrot+untrans+"\n");
			}
			// X < 0: starting angle: Pi-t0, extent: -2*(Pi-t0) (clockwise)
			else {
				strbuf.append("n "+trans+rot+scle+" 0 0 1 "+
						String.format("%.6e",rad2deg*(Math.PI-t0))+" "+
						String.format("%.6e",(-rad2deg*(Math.PI-t0)))+
						" arcn "+unscle+unrot+untrans+"\n");
			}
		
			// For filled circles we have to append part of the 
			//    horizon, properly oriented.
			if (cp) {
				double c=rad2deg*Math.asin(Math.sin(t0)*sinr);
				if (r>Math.PI/2.0) c=180-c;
				strbuf.append(" "+rot+" 0 0 1 "+String.format("%.6e",-c)+" "+
						String.format("%.6e",c)+" arc\n");
			}
		} // done with appending horizon
		return strbuf.toString();
	}
	
	/**
	 * Create a PostScript string which defines an edge as a new 
	 * current path; NOTE: no 'n' at start and 'rotation' and 'scale' 
	 * are undone so this can be used in building polygons; no 'gs', 
	 * 'gr', or 'stroke' are included.
	 * (stolen from 'sphCreateEdge')
	 * @param sg, SphGeodesic
	 * @return String
	 */
	public String sphEdgePath(SphGeodesic sg) {	

		StringBuilder strbuf=new StringBuilder(" ");
		
		if (!sg.isVisible()) return null; // both are on back
		if (sg.lineFlag) { // handle a straight line
			Complex a=SphericalMath.sphToVisualPlane(sg.z1.x,sg.z1.y);
			Complex b=SphericalMath.sphToVisualPlane(sg.z2.x,sg.z2.y);
			strbuf.append(" "+String.format("%.6e",a.x)+" "+String.format("%.6e",a.y)+
					" l "+String.format("%.6e",b.x)+" "+String.format("%.6e",b.y)+" l ");
			return strbuf.toString();
		}
		if (sg.followHorizon()!=0) { // handle geodesic along the horizon
			strbuf.append(" 0 0 1 "+String.format("%.6e",sg.z1.arg()*rad2deg)+" "+
					String.format("%.6e",sg.z2.arg()*rad2deg)+" arc ");
			return strbuf.toString();
		}
		
		// part of great circle
		Point3D C=new Point3D(sg.center.x,sg.center.y);
		double plen=Math.sqrt(C.y*C.y+C.z*C.z);
		Point3D A=new Point3D(0,-C.z/plen,C.y/plen); // unit orthog to yz-proj of C

		// Creation of 'sg' should have made both ends visible
		Point3D end1=new Point3D(sg.z1.x,sg.z1.y);
		Point3D end2=new Point3D(sg.z2.x,sg.z2.y);

		// set up rotation and scaling
		double wide=Math.abs(C.x);
		String rotsc=new String(" "+rad2deg*Math.atan2(C.z,C.y)+
				" rot "+String.format("%.6e",wide)+" 1 sc ");
		String unrotsc=new String(" "+String.format("%.6e",(1/wide))+" 1 sc "+
				(String.format("%.6e",(-1.0*rad2deg*Math.atan2(C.z,C.y)))+" rot "));

		double dp=Point3D.DotProduct(A,end1);
		double a1=Math.acos(dp);
		if (dp>=1.0) a1=0.0;
		else if (dp<=-1.0) a1=Math.PI;
		dp=Point3D.DotProduct(A,end2);
		double a2=Math.acos(dp);
		if (dp>=1.0) a2=0.0;
		else if (dp<=-1.0) a2=Math.PI;
		strbuf.append(rotsc);
		if (C.x>=0) { // seen from front
			a1+=Math.PI/2.0;
			a2+=Math.PI/2.0;
			strbuf.append(" 0 0 1 "+String.format("%.6e",rad2deg*a1)+" "+
					String.format("%.6e",rad2deg*a2)+" arc ");
		}
		else { // seen from back
			a1 = Math.PI/2.0-a1;
			a2 = Math.PI/2.0-a2;
			strbuf.append(" 0 0 1 "+String.format("%.6e",rad2deg*a1)+" "+
					String.format("%.6e",rad2deg*a2)+" arcn ");
		}
		strbuf.append(unrotsc); // undo the rotation and scaling
		return strbuf.toString();
	}
		
	/** 
	 * Create PostScript string(s) of the visual boundary of a
	 * spherical polygon; 'rotation' and 'scale' are undone, no 'gs', 
	 * 'gr', 'fill', or 'stroke' are included. For a closed path
	 * (which would include horizon arcs), use 'sph_closed_Poly'
	 * @param []crns corners
	 * @param N number of corners
	 * @param cp, boolean, true==>close the path
	 * @return Vector<String> with visual edge segments; length 0 for
	 * no visual segments; may be entirely visible, but calling
	 * routine must close
	 * TODO: have to handle huge faces, with interior containing hemisphere.
	 *   May have to fill in hemisphere, or build annular region between
	 *   triangle and unit circle
	 */
	public Vector<String> sphPolyPath(double []crns) 
	throws DrawingException { 
		int N=crns.length/2;
		Vector<String> paths=new Vector<String>();
		StringBuilder strbuf;
		
		// Cheap check: are all points invisible? Just check x-coords
		boolean hit=false;
		for (int j=0;(j<N && !hit);j++)
			if (Math.cos(crns[2*j])>0.0) hit=true;
		if (!hit) return paths;
		
		Point3D []pts=new Point3D[N];
		for (int j=0;j<N;j++) {
			pts[j]=new Point3D(crns[2*j],crns[2*j+1]);
//			System.out.println("j="+j+" "+pts[j].x+","+pts[j].y);
		}
		
		SphGeodesic[] sg=new SphGeodesic[N];
		hit=false;
		boolean allFront=true;
		boolean allHorizon=true;
		int firstX=-1;
		for (int j=N-1;j>=0;j--) {
			sg[j]=new SphGeodesic(pts[j],pts[(j+1)%N]);
			if (sg[j].isVisible()) {
				hit=true;
				firstX=j;
				if (sg[j].hitHorizon())
					allFront=false;
				if (sg[j].followHorizon()!=1) { // not horizon or clockwise
					allHorizon=false;
				}
			}
		}
		if (!hit) return paths; // nothing on front
		
		// everything on front or around horizon (counterclockwise)
		if (allFront || allHorizon) {
			strbuf=new StringBuilder(" n ");
			Complex a=SphericalMath.sphToVisualPlane(sg[0].z1.x,sg[0].z1.y);
			strbuf.append(" "+String.format("%.6e",a.x)+" "+String.format("%.6e",a.y)+" m "); // move to first end
			for (int i=0;i<N;i++) {
				strbuf.append(sphEdgePath(sg[i])+"\n");
//				System.out.println("i="+i+" y's are "+-1.0*Math.cos(sg[i].z1.y)+","+-1.0*Math.cos(sg[i].z2.y));
			}
			paths.add(strbuf.toString());
			return paths;
		}
		
		if (firstX<0) 
			throw new DrawingException("Didn't find visible geodesic");

		// general case, part on front, part on back
		
		// get in order
		int safety=N+1;
		int spot=firstX;
		while (safety>0 && !sg[spot].horizonStart && !sg[(spot+N-1)%N].horizonEnd) {
			safety--;
			spot=(spot+N-1)%N;
		}
		int []indx=new int[N];
		for (int j=0;j<N;j++)
			indx[j]=(spot+j)%N;
			
		SphGeodesic firstSG=sg[indx[0]];
		SphGeodesic nextSG=firstSG;
		int i=0;
		
		// on each reentry, nextSG is visible but not yet added to path
		while (nextSG.isVisible() && i<(N-1)) {
			strbuf=new StringBuilder(" n ");
			Complex a=SphericalMath.sphToVisualPlane(nextSG.z1.x,nextSG.z1.y);
			strbuf.append(" "+String.format("%.6e",a.x)+" "+String.format("%.6e",a.y)+" m "); // move to first end

			// add geodesics until you get 'horizonEnd' (they should be visible)
			while (!nextSG.horizonEnd) {
				strbuf.append(sphEdgePath(nextSG)+"\n");
				i++;
				nextSG=sg[indx[i]];
			}
			
			// if it went beyond horizon, append this segment, record exit spot
			if (nextSG.horizonEnd) {
				strbuf.append(sphEdgePath(nextSG)+"\n");
				paths.add(strbuf.toString()); // save to vector
			}

			// get next segment if there is one
			if (i<(N-1)) {
				i++;
				nextSG=sg[indx[i]];
			}
			
			// ignore geodesics until one becomes visible
			while (i<(N-1) && !nextSG.isVisible()) {
				i++;
				nextSG=sg[indx[i]];
			}
		} // end of while	

		return paths;
	}
	/** 
	 * Create a PostScript string which defines a polygon as a
	 * new current path closed; the 'rotation' and 'scale' used
	 * are undone. Unit circle arcs are included. Result should
	 * be suitable for 'fill', but no 'gs', 'gr', 'fill', or 's' 
	 * are included. 
	 * @param []crns double, corners
	 * @return String for new current path;
	 * TODO: have to handle huge faces, with interior containing hemisphere.
	 *   May have to fill in hemisphere, or build annular region between
	 *   triangle and unit circle
	 */
	public String sph_closed_Poly(double []crns) 
	throws DrawingException { 
		int N=crns.length/2;
		
		StringBuilder strbuf=new StringBuilder(" n ");
		
		// Cheap check: are all points invisible? Just check x-coords
		boolean hit=false;
		for (int j=0;(j<N && !hit);j++)
			if (Math.cos(crns[2*j])>0.0) hit=true;
		if (!hit) return null;
		
		Point3D []pts=new Point3D[N];
		for (int j=0;j<N;j++)
			pts[j]=new Point3D(crns[2*j],crns[2*j+1]);
		
		SphGeodesic[] sg=new SphGeodesic[N];
		hit=false;
		boolean allFront=true;
		boolean allHorizon=true;
		int firstX=-1;
		for (int j=N-1;j>=0;j--) {
			sg[j]=new SphGeodesic(pts[j],pts[(j+1)%N]);
			if (sg[j].isVisible()) {
				hit=true;
				firstX=j;
				if (sg[j].hitHorizon())
					allFront=false;
				if (sg[j].followHorizon()!=1) { // not horizon or clockwise
					allHorizon=false;
				}
			}
		}
		if (!hit) return null; // nothing on front
		
		// everything on front or around horizon (counterclockwise)
		if (allFront || allHorizon) {
			Complex a=SphericalMath.sphToVisualPlane(sg[0].z1.x,sg[0].z1.y);
			strbuf.append(" "+a.x+" "+a.y+" m "); // move to first end
			strbuf.append(sphEdgePath(sg[0])+"\n");
			for (int i=1;i<N;i++) 
				strbuf.append(sphEdgePath(sg[i])+"\n");
			strbuf.append(" cp "); // close path
			return strbuf.toString(); 
		}
		if (firstX<0) 
			throw new DrawingException("Didn't find visible geodesic");

		// part on front, part on back
		SphGeodesic firstSG=sg[firstX];
		Complex a=SphericalMath.sphToVisualPlane(firstSG.z1.x,firstSG.z1.y);
		strbuf.append(" "+a.x+" "+a.y+" m "); // move to first end
		SphGeodesic nextSG=firstSG;
		Complex toBackSpot=null;
		int i=firstX;
		
		// on each reentry, nextSG is visible but not yet added to path
		while (nextSG.isVisible() && i<N) {
			toBackSpot=null;

			// add geodesics until you get 'horizonEnd' (they should be visible)
			while (i<(N-1) && !nextSG.horizonEnd) {
				strbuf.append(sphEdgePath(nextSG)+"\n");
				i++;
				nextSG=sg[i];
			}
			
			// if it went beyond horizon, add this segment and record exit spot
			if (nextSG.horizonEnd) {
				toBackSpot=SphericalMath.sphToVisualPlane(nextSG.z2);
				strbuf.append(sphEdgePath(nextSG)+"\n");
			}

			// if last segment, have to finish up or catch error
			if (i==(N-1)) {
				i=N; // just to kick out of processing
				if (toBackSpot!=null) { // already drawn, just close up
					if (!firstSG.horizonStart)
						throw new DrawingException("path not closed");
					// close up with arc of horizon
					double a2=SphericalMath.sphToVisualPlane(firstSG.z1).arg();
					strbuf.append(" 0 0 1 "+rad2deg*toBackSpot.arg()+" "+
							rad2deg*a2+" arc ");
				}
				else // on front, should close, add it
					strbuf.append(sphEdgePath(nextSG)+"\n");
			}
			
			// after this, toBackSpot is set

			// get next segment if there is one
			if (i<(N-1)) {
				i++;
				nextSG=sg[i];
			}
			
			// ignore geodesics until one becomes visible
			while (i<(N-1) && !nextSG.isVisible()) {
				i++;
				nextSG=sg[i];
			}

			// now have last segment
			if (i==(N-1)) {
				i=N; // kick out
				if (!nextSG.isVisible()) {
					if (!firstSG.horizonStart)
						throw new DrawingException("never got back from the back");
					// include final arc of horizon
					double a2=SphericalMath.sphToVisualPlane(firstSG.z1).arg();
					strbuf.append(" 0 0 1 "+rad2deg*toBackSpot.arg()+" "+
							rad2deg*a2+" arc ");
				}
				else { // visible, must have come from the back
					// include arc first
					double a2=SphericalMath.sphToVisualPlane(nextSG.z1).arg();
					strbuf.append(" 0 0 1 "+rad2deg*toBackSpot.arg()+" "+
							rad2deg*a2+" arc ");
					// include the last arc itself
					strbuf.append(sphEdgePath(nextSG)+"\n");
				}
			}
			else if (i<N) { // have to add arc (only)
				double a2=SphericalMath.sphToVisualPlane(nextSG.z1).arg();
				strbuf.append(" 0 0 1 "+rad2deg*toBackSpot.arg()+" "+
						rad2deg*a2+" arc ");
			}
		} // end of while	

		strbuf.append(" cp ");
		return strbuf.toString();
	}

	
	/**
	 * recursively post eucl tile shapes for subdivision rule to given depth; 
	 *   tile rules must have optional position data from *.r rules file.
	 * 
	 * @param sRules SubdivisionRules, (with optional position data)
	 * @param tiletype int, type of this tile
	 * @param base Complex[2], base of this tile
	 * @param depth int, recursive depth
	 * @return int
	 */
	public int postTileRecurs(SubdivisionRules sRules,int tiletype,Complex []base,int depth) {
		TileRule topRule=sRules.tileRules.get(tiletype-4);
		
		// for transformations
		Complex origin=base[0];
		Complex basedir=base[1].minus(base[0]);
		
		// first, draw yourself, then position and recursively draw any children
		Complex []stdC =new Complex[topRule.stdCorners.length];
		for (int j=0;j<topRule.stdCorners.length;j++)
			stdC[j]=new Complex(topRule.stdCorners[j].times(basedir).add(origin));
		try {
			postFilledColorPoly(0, stdC.length, stdC,null,null,2.0);
		} catch(Exception ex) {
			throw new InOutException("failed in posting polygon.");
		}
		int count=1;

		// recurse through children
		if (depth > 0) {
			for (int n = 1; n <= topRule.childCount; n++) {
				Complex []subtileBase=new Complex[2];
				subtileBase[0] = new Complex(topRule.tileBase[n][0].times(basedir).add(origin));
				subtileBase[1] = new Complex(topRule.tileBase[n][1].times(basedir).add(origin));
				int rslt = postTileRecurs(sRules, topRule.childType[n], subtileBase, depth - 1);
				if (rslt <= 0)
					return 0;
				count += rslt;
			}
		}
		return count;
	}
	
	public static String getThickStr(double tx) {
		if (tx<0)
			tx=1.0;
		int tk=(int)tx;
		switch(tk) {
		case 1:	{return "onetk ";}
		case 2:	{return "twotk ";}
		case 3:	{return "threetk ";}
		case 4:	{return "fourtk ";}
		case 5:	{return "fivetk ";}
		case 6:	{return "sixtk ";}
		case 7:	{return "seventk ";}
		case 8:	{return "eighttk ";}
		case 9:	{return "ninetk ";}
		case 10: {return "tentk ";}
		default: {return new String(String.format("%.6e",tx)+" ourlinewidth ");}
		}
	}
	
}
