package panels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.dnd.DropTarget;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import dragdrop.ToolDropListener;
import exceptions.InOutException;
import geometry.CircleSimple;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import graphObjects.CPCircle;
import graphObjects.CPEdge;
import graphObjects.CPFace;
import graphObjects.CPTrinket;
import math.Mobius;
import mytools.MyTool;
import packing.PackData;
import packing.PackExtender;
import tiling.SubdivisionRules;
import tiling.TileRule;
import util.DataFormater;
import util.DispFlags;
import util.DispOptions;
import util.PostOptions;
import util.SphView;
import util.ViewBox;

/**
 * This class maintains 'packImage', an image buffer, and knows 
 * how to draw lines, circles, polygons, etc., maintain info on 
 * colors, real world coords, graphics, mouse, drop, menu events.
 * 
 * In 'CirclePack', we have 'PackData' for a circle packing associated
 * with this CPScreen (in fact, packData is created in this constructor).
 * There are many things that could logically go either in PackData or 
 * CPScreen; in general, try to put things here if they might be used in 
 * non-packing situations. Examples are colors, display flag options for 
 * circles/faces, opacity. Geometry will be kept in PackData, 
 * 
 * CirclePack will have 'NUM_PACKS' (currently 3) CPScreen's, each 
 * managing one small canvas, and it tracks which is 'active' --- its 
 * image is used in the large canvas as well (see 'ActiveScreen'). 
 * @author kens
 */

public class CPScreen extends JPanel implements	MouseListener {

	private static final long 
	serialVersionUID = 1L;

	// set up static color info
	public static final int NUMCOLORS=255;
    public static final int color_ramp_size = 200;
	public static final int []red;
    public static final int []green;
    public static final int []blue;
    // Foreground, background colors
    public static final int FG_COLOR = 0;
    public static final int BG_COLOR = 255;
    public static final Color FG_Color;
    public static final Color BG_Color;

    static {
    	red = new int[NUMCOLORS+1];
    	green = new int[NUMCOLORS+1];
    	blue = new int[NUMCOLORS+1];

    	// set up colors data
    	blue_to_red_ramp(0);

    	// convert fore/background to 'Color' objects
    	FG_Color=new Color(red[FG_COLOR],green[FG_COLOR],blue[FG_COLOR]); 
    	BG_Color=new Color(red[BG_COLOR],green[BG_COLOR],blue[BG_COLOR]);
    }

    // screen setting defaults
    public static final int defaultthickness=CPBase.DEFAULT_LINETHICKNESS;
    public int linethickness;
	public int fillOpacity;
	public int sphereOpacity;
	public int textSize; // for packing canvasses
    
	public static final String customGlobal="CP_custom.ps"; // global default file for PostScript
	public String customPS; // default PostScript file chosen for this packing; may be null.

	public PackData packData;
	int packNum; // (should be more general, say 'userNum')
	
	// instance variables
	public DispOptions dispOptions;
	public PostOptions postOptions;
	public DataFormater dataFormater;
	public double XMin;
	public double YMin;
	public double XMax;
	public double YMax;
	public double XWidth;
	public double YHeight;
	public double pixFactor; // pixels per real world unit
	public AffineTransform trans2pix; // keep updated for real-to-pix
	public int pixXMin;
	public int pixYMin;
    public int pixWidth=PackControl.getActiveCanvasSize();
    public int pixHeight=PackControl.getActiveCanvasSize();
    public Stroke defaultStroke; // for axis, unit sph/disc, etc.
    public Stroke stroke;
    public Rectangle2D.Double canvasRect; // used for drawing screen background
    public ViewBox realBox; // for canvas info
    public SphView sphView; // for spherical info
	public Font indexFont;
    
	// canvas info
	public BufferedImage packImage; // image is buffered here
	public Graphics2D imageContextReal; // for drawing circles/etc. real world
	boolean antialiasing = true;
	public boolean showAxis = false;
	
	// these objects persist: we just change data and call methods
	public CPCircle circle;
	public CPFace face;
	public CPEdge edge;
	public CPTrinket trinket;

	public Color color;
	public Color fillColor;

	// utility: holds data until restored
	private Color tmpcolor; 
	private int tmpthick;
	
    static String []geomAbbrev={" (hyp)"," (eucl)"," (sph)"};  
    public Mobius mobius;
    
	// Constructors    
    public CPScreen() {
    	this(0);
    }
    
    // Constructor(s)
	public CPScreen(int packnum) {
		if (packnum<0 || packnum>=CPBase.NUM_PACKS) packnum=0;
		this.setFocusable(true);
		this.setBorder(new LineBorder(Color.BLACK,2,false));
		this.addMouseListener(this);
		customPS=null;

		// prepare for graphing
		realBox=new ViewBox();
		sphView=new SphView();
		canvasRect=new Rectangle2D.Double();
    	linethickness=defaultthickness;
		defaultStroke=new BasicStroke((float)(defaultthickness), // /pixFactor),
				BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
		stroke=new BasicStroke((float)(defaultthickness), // /pixFactor),
				BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
	
		// create the associated PackData
		packNum = packnum;
		packData=new PackData(this);
		
		// persistent graphing objects: change data and reuse
		circle=new CPCircle(); 
		face=new CPFace();
		edge=new CPEdge();
		trinket=new CPTrinket();

		// screen display options
		dispOptions=new DispOptions(this);
		postOptions=new PostOptions(this);
		dataFormater=new DataFormater();
		new DropTarget(this,new ToolDropListener(this,packnum,false));
		
		// create memory buffered image
		packImage = resetCanvasSize(PackControl.getActiveCanvasSize(),
				PackControl.getActiveCanvasSize());

		reset(true);
	}
	
	public void reset() {
		reset(false);
	}
	
	/**
	 * Reset defaults for CirclePack; e.g., when new packing is loaded.
	 * Note: may prefer different behavior, eg., when copying another packing.
	 * @param startup, boolean: true only when first instantiating 'CPScreen'.
	 */
	public void reset(boolean startup) {
		setAntialiasing(true);
		setAxisMode(false);
		realBox.reset();
		mobius=new Mobius(); // identity
		sphView.defaultView();
		
		fillOpacity = CPBase.DEFAULT_FILL_OPACITY;
		sphereOpacity = CPBase.DEFAULT_SPHERE_OPACITY;
		textSize=CPBase.DEFAULT_INDEX_FONT.getSize();
		setLineThickness(CPBase.DEFAULT_LINETHICKNESS);
		dispOptions.reset();
		fillColor=CPBase.defaultFillColor;
		imageContextReal.setColor(CPBase.defaultCircleColor);
		// reset disp_text in "Screen" panel
        // if this is active, reset current settings
		if (!startup) { 
			setPackName();
			if (this==PackControl.getActiveCPScreen()) { 
				PackControl.screenCtrlFrame.screenPanel.resetSliders();
				PackControl.screenCtrlFrame.displayPanel.flagField.setText("");
				PackControl.screenCtrlFrame.displayPanel.setFlagBox(false);
			}
		}
        // reset to default screen
        update(2); 
	}

	/* DISP_CHOICE -- this is called when 'activeScreen' size is allowed
	 * to change; 'packImage' buffer is resized. Call for approp repaint
	 * @param wide; int pixel width
	 * @param high; int pixel height
	 */
	public BufferedImage resetCanvasSize(int wide,int high) {
		BufferedImage bI=new BufferedImage(wide,high,BufferedImage.TYPE_INT_RGB);	
		
		if (packImage!=null) // copy in old image
			bI.createGraphics().drawImage(packImage.getScaledInstance(wide,high,
				Image.SCALE_SMOOTH),new AffineTransform(),null);
		else 
			bI.createGraphics().fillRect(0,0,wide,high);
		imageContextReal=(Graphics2D) bI.createGraphics();
		imageContextReal.clip(new Rectangle(0,0,wide,high));
		imageContextReal.setColor(Color.white);
		imageContextReal.setColor(FG_Color);
		imageContextReal.setFont(indexFont);
		imageContextReal.setStroke(stroke);
		pixWidth=wide;
		pixHeight=high;
		setAntialiasing(antialiasing);
		update(2);
		return bI;
	}

    /**
     * After a change in real or pixel screen size, update.
     * If option==1, then just change for translations.
     * If option>1, also redo stroke, thresholds, etc.
     */
	public void update(int option) {
    	XMin=realBox.lz.x;
    	YMin=realBox.lz.y;
    	XMax=realBox.rz.x;
    	YMax=realBox.rz.y;
    	XWidth=realBox.rz.x-realBox.lz.x; 
    	YHeight=realBox.rz.y-realBox.lz.y;
    	canvasRect.setRect(0,0,pixWidth,pixHeight);
		indexFont=CPBase.DEFAULT_INDEX_FONT;
    	pixFactor=pixWidth/XWidth;
    	pixXMin=(int)(XMin*pixFactor);
    	pixYMin=(int)(YMin*pixFactor);
    	trans2pix=new AffineTransform(pixFactor,0.0,0.0,-pixFactor,
    			pixWidth/2.0,pixHeight/2.0);
    	
    	// if size has changed, too
    	if (option>1) {
    		stroke=new BasicStroke((float)(linethickness),
    				BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
    		imageContextReal.setStroke(stroke);
    	}
	}
    
	// Some mouse events may first be picked off by parents of this CPScreen
	public void mouseReleased(MouseEvent e) {
		if (e.getClickCount() >= 2) { // double click to change active packing
			try {
			  PackControl.switchActivePack(this.packNum);
			} catch (Exception ex) {return;}
		}
		// TODO: this doesn't seem to work to bring the activeFrame to the top
//		else if (e.getClickCount()>=1) { // single click brings active screen to top
//			PackControl.activeFrame.toFront();
//		}
	}

	// methods required for MouseListener 
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}


	public boolean getAntialiasing() {
		return antialiasing;
	}
	
	public void setAntialiasing(boolean b) {
		antialiasing = b;
		if (antialiasing) {
			imageContextReal.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
		}
		else {
			imageContextReal.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_OFF);
		}
	}

	public void toggleAxisMode() {
		showAxis = !showAxis;
		repaint();
	}

	/** determine if axes are active or not */
	public boolean isAxisMode() {
		return this.showAxis;
	}

	public void setAxisMode(boolean mode) {
		this.showAxis=mode;
		repaint();
	}

	/** 
	 * Draws a circle or face index 'n' at given Complex z. In sph case,
	 * this routine takes care to move z to apparent sphere, then to
	 * visual plane, and checks if it's on front.
	 * @param z Complex: real-world Complex location
	 * @param n int, index to display
	 * @param msg_flag, bit 1=in canvas, 2=in scratch, 3=both (default)
	 */
	public void drawIndex(Complex z, int n, int msg_flag) {
		Integer N = Integer.valueOf(n);
		if (msg_flag == 2 || msg_flag == 3)
			circlePack.PackControl.displayScratch(N.toString());
		if (msg_flag == 1 || msg_flag == 3) {
			if (packData.hes>0) { // sph
				z=sphView.toApparentSph(z);
				if (Math.cos(z.x)<0) return; // on back
				z=SphView.s_pt_to_visual_plane(z); 
			}
			tmpcolor=imageContextReal.getColor();
			imageContextReal.setColor(Color.BLUE);
			imageContextReal.drawString(N.toString(),
					real_to_pix_x(z.x), real_to_pix_y(z.y));
			imageContextReal.setColor(tmpcolor);
			if (packData.packNum==CirclePack.cpb.getActivePackNum())
				PackControl.activeFrame.reDisplay();
			else repaint();
		}
		return;
	}
	
	/**
	 * Draws string, up to length 12, in blue at given Complex z. In sph case,
	 * this routine takes care to move z to apparent sphere, then to
	 * visual plane, and checks if it's on front.
	 * @param z Complex
	 * @param str String
	 */
	public void drawStr(Complex z,String str) {
		if (packData.hes>0) { // sph
			z=sphView.toApparentSph(z);
			if (Math.cos(z.x)<0) return; // on back
			z=SphView.s_pt_to_visual_plane(z); 
		}
		tmpcolor=imageContextReal.getColor();
		imageContextReal.setColor(Color.BLUE);
		int len=str.length();
		if (len>12) len=12;
		imageContextReal.drawString(str.substring(0,len),
				real_to_pix_x(z.x), real_to_pix_y(z.y));
		imageContextReal.setColor(tmpcolor);
		if (packData.packNum==CirclePack.cpb.getActivePackNum())
			  PackControl.activeFrame.reDisplay();
		else repaint();
	}

	/**
	 * clears canvas; prepares disc/sphere background in non eucl cases
	 * @param repaint boolean
	 */
	public void clearCanvas(boolean repaint) {
		if (packData.hes==0) imageContextReal.setColor(Color.white);
		else imageContextReal.setColor(CPBase.DEFAULT_SphDisc_BACKGROUND);
		imageContextReal.fill(canvasRect);
		if (packData.hes!=0) circle.drawSphDisc(true); // blank the sphere/disc
		imageContextReal.setColor(Color.black);
		if (repaint) {
			if (packData.packNum==CirclePack.cpb.getActivePackNum())
				PackControl.activeFrame.reDisplay();
			if (packData.packNum==PackControl.mapPairFrame.getDomainNum())
				PackControl.mapPairFrame.domainScreen.repaint();
			if (packData.packNum==PackControl.mapPairFrame.getRangeNum())
				PackControl.mapPairFrame.rangeScreen.repaint();
		}
	}
	
	/**
	 * Use 'DispFlags' for all arcs
	 * @param z Complex
	 * @param rad double
	 * @param ang1 double
	 * @param extent double
	 * @param dflags DispFlags
	 */
	public void drawArc(Complex z,double rad,double ang1,double extent,DispFlags dflags) {
		if (packData.hes!=0)
			return;
		try {
		circle.x=z.x;
		circle.y=z.y;
		circle.radius=rad;
		circle.start=Double.valueOf(ang1);
		circle.extent=Double.valueOf(extent);
		
		Color fcolor=null;
		Color bcolor=null;
		if (dflags.getColor()!=null) {
			if (dflags.fill)
				fcolor=dflags.getFillColor();
			if (dflags.draw && dflags.colBorder)
				bcolor=dflags.getColor();
		}
		
		// draw it
		circle.drawIt(dflags.draw,bcolor,dflags.fill,fcolor);
		circle.start=null;
		circle.extent=null;
		} catch (Exception ex) {}
	}

	/**
	 * Draw circle using 'DispFlags' encoding for color, thickness, 
	 * label, etc. 
	 * @param z Complex
	 * @param rad double
	 * @param dflags DispFlags
	 */
	public void drawCircle(Complex z,double rad,DispFlags dflags) {
		try {
			if (packData.hes > 0)
				z = sphView.toApparentSph(z);
			circle.x = z.x;
			circle.y = z.y;
			circle.radius = rad;
			
			Color fcolor=null;
			Color bcolor=null;
			if (dflags.getColor()!=null) {
				if (dflags.fill)
					fcolor=dflags.getFillColor();
				if (dflags.draw && dflags.colBorder)
					bcolor=dflags.getColor();
			}
			
			// thickness?
			tmpthick=-1;
			if (dflags.thickness > 0) {
				tmpthick = getLineThickness();
				setLineThickness(dflags.thickness);
			}
			
			// draw it (principal color is set, bcOl = bdry color)
			circle.drawIt(dflags.draw,bcolor,dflags.fill,fcolor);

			// label?
			if (dflags.label && dflags.getLabel()!= null) {

				if (packData.hes <= 0 || Math.cos(z.x) >= 0) {
					if (packData.hes > 0)
						z = SphView.s_pt_to_visual_plane(z);
					tmpcolor = imageContextReal.getColor();
					imageContextReal.setColor(Color.BLUE);
					imageContextReal.drawString(dflags.getLabel(), real_to_pix_x(z.x), real_to_pix_y(z.y));
					imageContextReal.setColor(tmpcolor);
				}
			}

			// restore thickness
			if (tmpthick >= 0)
				setLineThickness(tmpthick);
		} catch (Exception ex) {
		}
	}

	/**
	 * Draw face using 'DispFlags' for color, thickness, label, etc.
	 * (Radii are only needed in labeling in hyperbolic case.)
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param z3 Complex
	 * @param r1 double
	 * @param r2 double
	 * @param r3 double
	 * @param dflags DispFlags
	 */
	public void drawFace(Complex z1,Complex z2,Complex z3,
			Double r1,Double r2,Double r3,DispFlags dflags) {
	
		try {
			if (packData.hes > 0) {
				z1 = sphView.toApparentSph(z1);
				z2 = sphView.toApparentSph(z2);
				z3 = sphView.toApparentSph(z3);
			}
			double[] cnrs = new double[6];
			cnrs[0] = z1.x;
			cnrs[1] = z1.y;
			cnrs[2] = z2.x;
			cnrs[3] = z2.y;
			cnrs[4] = z3.x;
			cnrs[5] = z3.y;
			face.setData(3, cnrs);
			
			Color fcolor=null;
			Color bcolor=null;
			if (dflags.getColor()!=null) {
				if (dflags.fill)
					fcolor=dflags.getFillColor();
				if (dflags.draw && dflags.colBorder)
					bcolor=dflags.getColor();
			}		
			
			tmpthick=-1;
			if (dflags.thickness > 0) {
				tmpthick = getLineThickness();
				setLineThickness(dflags.thickness);
			}
			
			face.drawIt(dflags.draw,bcolor,dflags.fill,fcolor);
			
			// label?
			if (dflags.label && dflags.getLabel()!= null) {
				CircleSimple sc=null;
				if (packData.hes<0)
					sc=HyperbolicMath.hyp_tang_incircle(z1,z2,z3,r1,r2,r3);
//					sc=HyperbolicMath.hyp_tri_incircle(z1,z2,z3);
				else if (packData.hes>0)
					  sc=SphericalMath.sph_tri_incircle(z1,z2,z3);
				else sc=EuclMath.eucl_tri_incircle(z1,z2,z3);
				Complex z=sc.center;
				if (packData.hes <= 0 || Math.cos(z.x) >= 0) {
					if (packData.hes > 0)
						z = SphView.s_pt_to_visual_plane(z);
					tmpcolor = imageContextReal.getColor();
					imageContextReal.setColor(Color.BLUE);
					imageContextReal.drawString(dflags.getLabel(), real_to_pix_x(z.x), real_to_pix_y(z.y));
					imageContextReal.setColor(tmpcolor);
				}
			}
			
			// restore thickness
			if (tmpthick >= 0)
				setLineThickness(tmpthick);
		} catch (Exception ex) {
		}
	}
	
	/**
	 * Draw a closed polygon
	 * @param N int
	 * @param corners double[2*N]
	 * @param dflags DispFlags
	 */
	public void drawClosedPoly(int N, double[] corners, DispFlags dflags) {
		try {
			face.setData(N, corners);
			if (packData.hes > 0) {
				for (int j = 0; j < N; j++) {
					Complex z = sphView.toApparentSph(new Complex(corners[j * 2], corners[j * 2 + 1]));
					face.corners[j * 2] = z.x;
					face.corners[j * 2 + 1] = z.y;
				}
			}

			Color fcolor = null;
			Color bcolor = null;
			if (dflags.getColor() != null) {
				if (dflags.fill)
					fcolor = dflags.getFillColor();
				if (dflags.draw && dflags.colBorder)
					bcolor = dflags.getColor();
			}
			
			tmpthick=-1;
			if (dflags.thickness > 0) {
				tmpthick = getLineThickness();
				setLineThickness(dflags.thickness);
			}

			face.drawIt(dflags.draw, bcolor, dflags.fill, fcolor);
			
			// restore thickness
			if (tmpthick >= 0)
				setLineThickness(tmpthick);

		} catch (Exception ex) {}
	}

	/**
	 * Draw an open polygon (no fill option)
	 * @param N int
	 * @param corners double[2*N]
	 * @param dflags DispFlags
	 */
	public void drawOpenPoly(int N, double[] corners, DispFlags dflags) {
		try {
			Complex []pts=new Complex[N];
			for (int j=0;j<N;j++) {
				pts[j]=new Complex(corners[j*2],corners[j*2+1]);
			}
		
			for (int j=0;j<N-1;j++) {
				drawEdge(pts[j],pts[j+1],dflags);
			}
		} catch (Exception ex) {}
	}

	/**
	 * Draw an geodesic segment
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param dflags DispFlags
	 */
	public void drawEdge(Complex z1,Complex z2,DispFlags dflags) {
		try {
		if (packData.hes>0) {
			z1=sphView.toApparentSph(z1);
			z2=sphView.toApparentSph(z2);
		}
		edge.setData(z1.x,z1.y,z2.x,z2.y);
		
		tmpthick=-1;
		if (dflags.thickness > 0) {
			tmpthick = getLineThickness();
			setLineThickness(dflags.thickness);
		}
		if (dflags.getColor()!=null) {
			tmpcolor=imageContextReal.getColor();
			imageContextReal.setColor(dflags.getColor());
			edge.drawIt();
			imageContextReal.setColor(tmpcolor);
		}
		else
			edge.drawIt();
		
		// restore thickness
		if (tmpthick >= 0)
			setLineThickness(tmpthick);
		} catch (Exception ex) {}
	}
	
	/**
	 * Draw trinket, colors, fill, etc. based on 'DispFlags' info.
	 * Scale is 2^thickness.
	 * @param trink int, index into library of trinket shapes
	 * @param z Complex
	 * @param dflags DispFlags
	 */
	public void drawTrinket(int trinkIndx,Complex z,DispFlags dflags) {
		try {
			if (packData.hes>0) { 
				z=sphView.toApparentSph(z);
				if (Math.cos(z.x)<0.0) return; // on back
				// conversion to visual plane is in 'drawIt'
			}
			trinket.x=z.x;
			trinket.y=z.y;
			int scale=(int)Math.pow(2.0,dflags.thickness);
			trinket.drawIt(dflags.fill,trinkIndx,dflags.getColor(),scale);
		} catch (Exception ex) {}
	}

	/**
	 * Paths are specified in real world coordinates. Have to convert
	 * these based on screen data, then further to pixels before drawing.
	 * Note that color, linewidth etc. are handled by the calling routine.
	 * @param path Path2D.Double
	 */
	public void drawPath(Path2D.Double path) {
		AffineTransform tmp2pix=(AffineTransform)trans2pix.clone();
//		double half=XWidth/2.0;
		tmp2pix.concatenate(new AffineTransform(1.0,0.0,0.0,1.0,
    			-(XMax+XMin)/2.0,-(YMax+YMin)/2.0));
		imageContextReal.draw(new Path2D.Double(path,tmp2pix));
	}
	
    /**
     * Draw shape with given color and stroke
     * @param shape Shape
     * @param color Color
     * @param stroke Stroke
     */
    public void drawShape(Shape shape,Color color,Stroke stroke) {
    	Stroke old_stroke=imageContextReal.getStroke();
    	Color old_color = imageContextReal.getColor();
    	imageContextReal.setStroke(stroke);
    	imageContextReal.setColor(color);
    	imageContextReal.draw(shape);
    	imageContextReal.setStroke(old_stroke);
    	imageContextReal.setColor(old_color);
    }
    
	/**
	 * Recursively draw euclidean tile shapes for subdivision rule to given depth; 
	 *   tile rules must have optional position data from *.r rules file.
	 * @param sRules SubdivisionRules, (with optional position data)
	 * @param tiletype int, type of this tile
	 * @param base Complex[2], base of this tile
	 * @param depth int, recursive depth
	 * @return int
	 */
	public int drawTileRecurs(SubdivisionRules sRules,int tiletype,Complex []base,
			int depth,DispFlags dflags) {
		TileRule topRule=sRules.tileRules.get(tiletype-4);

		// for transformations
		Complex origin=base[0];
		Complex basedir=base[1].minus(base[0]);
		
		// first, draw yourself, then position and recursively draw any children
		double []stdC=new double[2*topRule.stdCorners.length];
		for (int j=0;j<topRule.stdCorners.length;j++) {
			Complex z=new Complex(topRule.stdCorners[j].times(basedir).add(origin));
			stdC[2*j]=z.x;
			stdC[2*j+1]=z.y;
		}
		try {
			drawClosedPoly(topRule.stdCorners.length,stdC,dflags);
		} catch(Exception ex) {
			throw new InOutException("failed in drawing polygon.");
		}
		int count=1;

		// recurse through children
		if (depth > 0) {
			for (int n = 1; n <= topRule.childCount; n++) {
				Complex []subtileBase=new Complex[2];
				subtileBase[0] = new Complex(topRule.tileBase[n][0].times(basedir).add(origin));
				subtileBase[1] = new Complex(topRule.tileBase[n][1].times(basedir).add(origin));
				int rslt = drawTileRecurs(sRules, topRule.childType[n], subtileBase, depth - 1,dflags);
				if (rslt <= 0)
					return 0;
				count += rslt;
			}
		}
		return count;
	}
     
	public int getFillOpacity() {
		return fillOpacity;
	}
	
	public static Color getFGColor() {
		return new Color(FG_Color.getRed(),FG_Color.getGreen(),FG_Color.getBlue());
	}

	public static Color getBGColor() {
		return new Color(BG_Color.getRed(),BG_Color.getGreen(),BG_Color.getBlue());
	}

	/** 
	 * 'fill opacity' controls the brilliance of colors for filled
	 * faces, circles. Less means lighter colors.
	 * TODO: need to update 'screen' tab slider
	 * @param t in [0,256)
	 */
	public void setFillOpacity(int t) {
		if (t >= 0 && t <= 255)
			fillOpacity = t;
		else fillOpacity=CPBase.DEFAULT_FILL_OPACITY;
	}

	public int getSphereOpacity() {
		return sphereOpacity;
	}
	
	/**
	 * 'sphere opacity' controls how much the back of the sphere shows
	 * through.
	 * @param t in [0,256)
	 */
	public void setSphereOpacity(int t) {
		if (t >= 0 && t <= 255)
			sphereOpacity = t;
		else sphereOpacity=CPBase.DEFAULT_SPHERE_OPACITY;
	}
	
	public Font getIndexFont() {
		return indexFont;
	}
	
	/** 
	 * Set font appearing for indices on canvas of this packing.
	 * @param t
	 */
	public void setIndexFont(int t) {
		if (t>=0 && t<= 30) {
			textSize=t;
			indexFont=new Font("Sarif",Font.ITALIC,t);
			imageContextReal.setFont(indexFont);
		}
		else indexFont=CPBase.DEFAULT_INDEX_FONT;
	}

	
	/**
	 * Draw X axis on 'activeScreen'. Draw directly in canvas 
	 * after the image buffer has been put in. 
	 * DISP_CHOICE -- have to adjust yPos, k1, k2 if image buffer 
	 * and 'activeScreen' are not the same pixel size.
	 * @param g
	 */
	public void drawXAxis(Graphics2D g) {
		g.setColor(Color.black);
		// number of marks we want to see on the axis
		int K = 40;
		int yPos;
		yPos = real_to_pix_y(0);
		g.drawLine(0, yPos,pixWidth, yPos);
		int l = (int) (Math.round(Math.log(XWidth/K) / Math.log(10d)));
		double L = Math.pow(10, l);
		int k1 = (int) (Math.floor(XMin / L));
		int k2 = (int) (Math.floor(XMax / L));
		// starting x
		for (int k = k1; k <= k2; k++) {
			int xG = real_to_pix_x(k * L);
			if (xG >= 0) {
				if (k % 10 != 0)
					g.drawLine(xG, yPos, xG, yPos - 7);
				else {
					g.drawLine(xG, yPos, xG, yPos - 14);
					g.drawString(
							util.MathUtil.d2StringNew(k * Math.pow(10, l)),
							xG + 7, yPos - 10);
				}
			}
		}
	}

	/**
	 * Draw Y axis on 'activeScreen'. Draw directly in canvas 
	 * after the image buffer has been put in. 
	 * DISP_CHOICE -- have to adjust xPos, k1, k2 if image buffer 
	 * and 'activeScreen' are not the same pixel size.
	 * @param g
	 */
	public void drawYAxis(Graphics2D g) {
		g.setColor(Color.black);
		// number of marks we want to see on the axis
		int K = 40;
		int xPos;
		xPos = real_to_pix_x(0);
		g.drawLine(xPos, 0, xPos, pixHeight);
		int l = (int) (Math.round(Math.log(YHeight/K) / Math.log(10d)));
		double L = Math.pow(10, l);
		int k1 = (int) (Math.floor(YMin / L));
		int k2 = (int) (Math.floor(YMax / L));
		for (int k = k1; k <= k2; k++) {
			int yG = real_to_pix_y(k * L);
			if (k % 10 != 0)
				g.drawLine(xPos, yG, xPos - 7, yG);
			else {
				g.drawLine(xPos, yG, xPos - 14, yG);
				g.drawString(util.MathUtil.d2StringNew(k * Math.pow(10, l)),
						(int)xPos - 25, yG - 3);
			}
		}
	}

	/**
	 * CPScreen directly manages only the small canvass.
	 * Catch repaints to:
	 *   1. throw in the buffered image
	 *   2. add axes if called for
	 */
	public void paintComponent(Graphics g) {
//		super.paintComponent(g);
		g.drawImage(getThumbnailImage(),0,0,
				this.getWidth(),this.getHeight(),this);
		if (this.showAxis) {
			Graphics2D g2 = (Graphics2D) g;
			this.drawXAxis(g2);
			this.drawYAxis(g2);
		}
	}
	
	public Image getThumbnailImage(){
	    if(packImage != null)
	      return packImage.getScaledInstance(
	    		  this.getWidth(),this.getHeight(),Image.SCALE_SMOOTH);

	    // Return an empty image
	    return new BufferedImage(
	    		this.getWidth(),this.getHeight(),BufferedImage.TYPE_INT_RGB);
	}

	/**
	 * Give string for geometry to label canvas
	 * @return
	 */
	public String getGeomAbbrev(){
		switch (packData.hes) {
			case -1: return new String(" (hyp)");
			case 0:	return new String(" (eucl)");
			default: return new String(" (sph)");
		}
	}
	
	/**
	 * Update displayed Xtender tools (if any) for this packing
	 */
	public void updateXtenders() {
		// remove all Xtender tools
		SmallCanvasPanel scp=PackControl.smallCanvasPanel;
		while (scp.cpInfo[packNum].getComponentCount()>1)
			scp.cpInfo[packNum].remove(1);
		scp.cpInfo[packNum].revalidate();
		// re-add those for this packing
		Vector<PackExtender> Xvec=packData.packExtensions;
		for (int i=0;i<Xvec.size();i++) {
			PackExtender pX=Xvec.get(i);
			MyTool Xtool=pX.XtenderTool;
			if (Xtool!=null) scp.cpInfo[packNum].add(Xtool);
		}
		scp.cpInfo[packNum].revalidate();
		scp.cpInfo[packNum].repaint();
	}
	
	/**
	 * Update small canvas pack label using 'packData.fileName'.
	 */
	public void setPackName() {
		if (packData.fileName== null || packData.fileName.trim().length()==0) 
			packData.setName("NoName");
		PackControl.smallCanvasPanel.packName[packNum].
				setText("P"+packNum+" "+packData.fileName+geomAbbrev[packData.hes+1]);
	}

	/**
	 * Reset the geometries of the graphic objects;
	 * @param geom, int 1,0, or -1
	 */
	public void setGeometry(int geom) {
		circle.resetGeom(geom);
		face.resetGeom(geom);
		edge.resetGeom(geom);
		trinket.resetGeom(geom);
	}
	
	/** 
	 * Return the index of this screen (and its PackData set).
	 * @return
	 */
	public int getPackNum() {
		return packNum;
	}
	
	/**
	 * Attach a new pNew to this screen (normally the previous packData is lost).
	 * Also, clean up: attach this screen to new packing, transfer p.packNum and 
	 * packname to new packing, optionally carry current 'PackExtensions' to pNew.
	 * 
	 * Caution: calling routine's local 'PackData' may have disappeared, so it
	 * should be replaced by 'this.packData' on return.
	 *  
	 * @param pNew @see packData
	 * @param keepX boolean: if true, replace 'PackExtenders' of pNew by those of original.
	 * @return int, new nodeCount
	 */
	public int swapPackData(PackData pNew,boolean keepX) {
		if (pNew==null || !pNew.status || pNew.nodeCount<3) {
			return emptyPacking();
		}
		if (keepX) 
			pNew.packExtensions=packData.packExtensions;
		packData=pNew; // old pack info to garbage.
		packData.cpScreen=this;
		packData.packNum=packNum;
		setGeometry(packData.hes);
		setPackName();
		return packData.nodeCount;
	}
	
	/**
	 * Empty the packing (packing data is lost); clear/reset the screen
	 * and 'dispOptions'.
	 */
	public int emptyPacking() {
		packData=new PackData(this);
		reset();
		sphView.defaultView();
		clearCanvas(true);
		return 1;
	}
	
	
    /** 
     * Note user has to set color_ramp_size, default generally 
     * COLOR_RAMP. Here we set up color ramp, starting with dark 
     * blue, ending with dark red. Index color_ramp_size/2 is white.
     * @param flag, int. If not zero do ramp only, and not the 
     *    'other' colors; caution, other colors are specified based 
     *    on color_ramp_size=200. 
     */
 	public static void blue_to_red_ramp(int flag) {
      int i,mid,col_factor=0;
     
      if ((mid=(int)(color_ramp_size/2.0))>0) {
    	  col_factor=(int)(255.0/mid);
    	  // blue ramp 
    	  for (i=0;i<mid;i++)
    	  {blue[i]=255;red[i]=green[i]=i*col_factor;}
    	  blue[0]=red[0]=green[0]=0;
    	  // middle is white 
    	  blue[mid]=red[mid]=green[mid]=255;
    	  // red ramp 
    	  for (i=mid+1;i<2*mid;i++)
    	  {red[i]=255;blue[i]=green[i]=255-(i-mid)*col_factor;}
    	  if (flag != 0) return;
     }	
     else if (flag != 0) return;
     
     if (mid<1) mid=1;
     
     // Some mixed colors for filler 
     for (i=2*mid;i<210;i++) 
         {red[i]=green[i]=blue[i]=255-255*((int)((i-2*mid)/(210-2*mid)));}
     for (i=231;i<255;i++)
     {red[i]=0;green[i]=blue[i]=255-10*(i-230);}
     /* additional colors by Monica Hurdal */
     
     red[201]=255;green[201]=200;blue[201]=200; /* light pink */
     red[202]=200;green[202]=225;blue[202]=255; /* light blue */
     red[203]=200;green[203]=255;blue[203]=200; /* light green */
     red[204]=255;green[204]=230;blue[204]=200; /* light orange */
     red[205]=180;green[205]=180;blue[205]=240; /* light purple */
     red[206]=50;green[206]=128;blue[206]=50;   /* dark green */
     red[207]=128;green[207]=50;blue[207]=128;  
     red[208]=203;green[208]=255;blue[208]=102;
     red[209]=255;green[209]=255;blue[209]=0;   /* yellow */
     red[210]=255;green[210]=0;blue[210]=0;     /* red */
     red[211]=245;green[211]=94;blue[211]=0;    /* orange ramp: very dark */
     red[212]=255;green[212]=146;blue[212]=29;  /* orange ramp: dark */
     red[213]=255;green[213]=159;blue[213]=56;  /* orange ramp: medium */
     red[214]=255;green[214]=172;blue[214]=84;  /* orange ramp: light */
     red[215]=255;green[215]=185;blue[215]=110; /* orange ramp: lighter */
     red[216]=255;green[216]=204;blue[216]=152; /* orange ramp: lighter */
     red[217]=255;green[217]=218;blue[217]=178; /* orange ramp: very light */
     red[218]=0;green[218]=255;blue[218]=0;     /* bright green */
     red[219]=34;green[219]=139;blue[219]=34;
     red[220]=64;green[220]=224;blue[220]=208;
     red[221]=0;green[221]=255;blue[221]=255;   /* cyan */
     red[222]=135;green[222]=206;blue[222]=250;
     red[223]=95;green[223]=158;blue[223]=160;
     red[224]=205;green[224]=133;blue[224]=63;
     red[225]=160;green[225]=82;blue[225]=45;
     red[226]=235;green[226]=110;blue[226]=100;
     red[227]=255;green[227]=140;blue[227]=0;   /* orange */
     red[228]=221;green[228]=160;blue[228]=221;
     red[229]=185;green[229]=48;blue[229]=185;
     red[230]=205;green[230]=205;blue[230]=205; /* light grey */
     red[231]=50;green[231]=50;blue[231]=50;    /* dark grey */
     
     // 16 "spread" colors (see 'ColorUtil.spreadColorCode/spreadColor')
     // TODO: might analyze these to get clearer distinctions
     red[232]=255;green[232]=0;blue[232]=0;     /* red */
     red[233]=0;green[233]=255;blue[233]=0;     /* bright green */
     red[234]=0;green[234]=0;blue[234]=255;     /* blue */
     red[235]=255;green[235]=blue[235]=125;
     red[236]=0;green[237]=255;blue[237]=255;   /* cyan */
     red[237]=234;green[236]=138;blue[236]=0;   /* orange */
     red[238]=178;green[238]=0;blue[238]=255;   /* purple */
     red[239]=66;green[239]=138;blue[239]=66;   /* forest green */
     red[240]=255;green[240]=140;blue[240]=0;   /* orange */
     red[241]=255;green[241]=0;blue[241]=255;   /* magenta (pink) */
     red[242]=0;green[242]=blue[242]=155;
     red[243]=255;green[243]=255;blue[243]=0;   /* yellow (doesn't show up well)*/
     red[244]=green[244]=125;blue[244]=255;
     red[245]=blue[245]=125;green[245]=255;
     red[246]=255;green[246]=172;blue[246]=84;  /* orange ramp: light */
     red[247]=180;green[247]=180;blue[247]=240; /* light purple */
     
     // misc. 
     red[248]=128;green[248]=128;blue[248]=128; /* grey */
     red[249]=160;green[249]=80;blue[249]=0;    /* brown */
     red[250]=0;green[250]=0;blue[250]=0;       /* black */

     red[255]=blue[255]=green[255]=255;
     
     return;
	} /* blue_to_red_ramp */
 
	/**
	 * Sets the linethickness and adjusts the current and default strokes.
	 * (Note: default needs resetting in case 'pixFactor' changes)
	 * @param thickness
	 */
	public void setLineThickness(int thickness) {
		if (thickness!=linethickness) {
			linethickness=thickness;
			stroke=new BasicStroke((float)(thickness), // /pixFactor)
					BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
			imageContextReal.setStroke(stroke);
		}	
	}
 
	public int getLineThickness() {
		return linethickness;
	}
 
	/**
	 * converts real world double x to pixel int x
	 * @param x
	 * @return int
	 */
	public int real_to_pix_x(double x) {
		return (int)(.5+(x-XMin)*pixFactor);
	}

	/** converts real world double x to pixel double
	 * @param x
	 * @return double
	 */
	public double toPixX(double x) {
		return (x-XMin)*pixFactor;
	}

	/** converts real world double x to pixel double
	 * @param x
	 * @return double
	 */
	public double toPixY(double y) {
		return -(y-YMin-YHeight)*pixFactor;
	}

	/**
	 * converts real world double y to pixel int y
	 * @param y
	 * @return int
	 */
	public  int real_to_pix_y(double y) {
		return (int)(.5+(y-(YMin+YHeight))*-pixFactor);  // - because of y flip
	}

	/**
	 * Given pt on canvas (integer wide,high), return real world point.
	 * @param pt, integer x,y
	 * @param wide, high, integer canvas size
	 * @return Point2D.Double
	 */
	public Point2D.Double pt2RealPt(Point pt,int wide,int high) {
		double x=XMin +(double)pt.x*XWidth/(double)wide;
		double y=YMin+YHeight-(double)pt.y*YHeight/(double)high;
		return new Point2D.Double(x,y);
	}
	
	/** 
	 * find real world center x coord
	 * @return
	 */
	public double getCenterX() {
		return XMin+XWidth/2.0;
	}

	/** 
	 * find real world center y coord
	 * @return
	 */
	public double getCenterY() {
		return YMin+YHeight/2.0;
	}
	
	/**
	 * Create Color object using index to color table and opacity
	 * @param indx int, in [0,255]
	 * @param opacity
	 * @return Color
	 */
	public static Color coLor(int indx,int opacity) {
		if (indx<0 || indx>255)
			return new Color(red[FG_COLOR],green[FG_COLOR],blue[FG_COLOR],opacity);
		return new Color(red[indx],green[indx],blue[indx],opacity);
	}
	
	/**
	 * Create actual 'Color' object using CirclePack index to color table 
	 * @param indx int, in [0,255]
	 * @return Color
	 */
	public static Color coLor(int indx) {
		if (indx<0 || indx>255)
			return CPScreen.getFGColor();
		return new Color(red[indx],green[indx],blue[indx]);
	}
	
	/**
	 * Create 'Color' using Integer (versus int) index to color table 
	 * @param indx, Integer 
	 * @return Color, default to FG_Color
	 */
	public static Color coLor(Integer indx) {
		int val=indx.intValue();
		return coLor(val);
	}
	
	/** 
	 * For converting r g b (0-255) colors into index of "closest" 
	 * color in current colortable. 
	 *
	 * TODO: This is temporary (9/05) until I get rid of colortables 
	 * 
	 * @param rd int
	 * @param gn int
	 * @param bl int
	 * @return int, index to CirclePack colortable
	*/
	public static int col_to_table(int rd, int gn, int bl) {
	  int dist;
	  int idx=0;
	  dist=Math.abs((int)red[idx]-rd)+
	  Math.abs((int)green[idx]-gn)+
	  Math.abs((int)blue[idx]-bl);
	  if (dist==0) return idx;
	  int mndist=1000;
	  for (int i=0;i<=CPScreen.NUMCOLORS;i++) {
	    dist=Math.abs((int)red[i]-rd)+
		Math.abs((int)green[i]-gn)+
		Math.abs((int)blue[i]-bl);
	    if (dist==0) {
	      idx=i;
	      return idx;
	    }
	    if(dist<mndist) {
	      mndist=dist;
	      idx=i;
	    }
	  }
	  return idx;
	} 
	
	/**
	 * Convert 'Color' object to integer index in 'CirclePack' color tables.
	 * @param color Color
	 * @return int, color index
	 */
	public static int col_to_table(Color color) {
		return col_to_table(color.getRed(),color.getGreen(),color.getBlue());
	}
	
	/**
	 * Clone a 'Color' object, including alpha level
	 * @param color Color
	 * @return Color, null if input is null
	 */
	public static Color cloneColor(Color color) {
		if (color==null)
			return null;
		return new Color(color.getRed(),color.getGreen(),color.getBlue(),color.getAlpha());
	}

} 
