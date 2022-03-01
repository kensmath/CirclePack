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
import mytools.MyTool;
import packing.PackData;
import packing.PackExtender;
import tiling.SubdivisionRules;
import tiling.TileRule;
import util.ColorUtil;
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

    // screen setting defaults
    public static final int defaultthickness=CPBase.DEFAULT_LINETHICKNESS;
    public int linethickness;
	public int fillOpacity;
	public int sphereOpacity;
	public int textSize; // for packing canvasses

	// global default file for PostScript
	public static final String customGlobal="CP_custom.ps";
	// default PostScript file chosen for this packing; may be null.
	public String customPS; 

	public PackData packData;
	int screenNum; // normally alligns with packData.packNum
	
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
    public Rectangle2D.Double canvasRect; // for drawing screen background
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
    
	// Constructors    
    public CPScreen() {
    	this(0);
    }
    
    // Constructor(s)
	public CPScreen(int screennum) {
		if (screennum<0 || screennum>=CPBase.NUM_PACKS) 
			screennum=0;
		screenNum=screennum;
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
		
		// persistent graphing objects: change data and reuse
		circle=new CPCircle(); 
		face=new CPFace();
		edge=new CPEdge();
		trinket=new CPTrinket();

		// screen display options
		dispOptions=new DispOptions(this);
		postOptions=new PostOptions(this);
		dataFormater=new DataFormater();
		new DropTarget(this,new ToolDropListener(this,screenNum,false));
		
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
	 * Note: may prefer different behavior, eg., when copying another 
	 * packing.
	 * @param startup, boolean: true only when first instantiating 'CPScreen'.
	 */
	public void reset(boolean startup) {
		setAntialiasing(true);
		setAxisMode(false);
		realBox.reset();
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
			bI.createGraphics().
				drawImage(packImage.getScaledInstance(wide,high,
				Image.SCALE_SMOOTH),new AffineTransform(),null);
		else 
			bI.createGraphics().fillRect(0,0,wide,high);
		imageContextReal=(Graphics2D) bI.createGraphics();
		imageContextReal.clip(new Rectangle(0,0,wide,high));
		imageContextReal.setColor(Color.white);
		imageContextReal.setColor(ColorUtil.getFGColor());
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
			  PackControl.switchActivePack(this.getPackNum());
			} catch (Exception ex) {return;}
		}
		// TODO: doesn't seem to work to bring activeFrame to the top
//		else if (e.getClickCount()>=1) { // click active screen to top
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
	 * @param msg_flag int, bit 1=in canvas,2=in scratch,3=both (default)
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
	 * Draws string, up to length 12, in blue at given 
	 * Complex z. In sph case, this routine takes care 
	 * to move z to apparent sphere, then to
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
	public void drawArc(Complex z,double rad,double ang1,
			double extent,DispFlags dflags) {
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
		drawCircle(new CircleSimple(z,rad),dflags);
	}
	
	/**
	 * Draw circle using 'DispFlags' encoding for color, thickness, 
	 * label, etc. 
	 * @param cs CircleSimple
	 * @param dflags DispFlags
	 */
	public void drawCircle(CircleSimple cs,DispFlags dflags) {
		Complex z=cs.center;
		try {
			if (packData.hes>0)
				z=sphView.toApparentSph(z);
			circle.x = z.x;
			circle.y = z.y;
			circle.radius = cs.rad;
			
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
					imageContextReal.drawString(dflags.getLabel(),
							real_to_pix_x(z.x), real_to_pix_y(z.y));
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
	 * @param z0 Complex
	 * @param z1 Complex
	 * @param z2 Complex
	 * @param r0 double
	 * @param r1 double
	 * @param r2 double
	 * @param dflags DispFlags
	 */
	public void drawFace(Complex z0,Complex z1,Complex z2,
			Double r0,Double r1,Double r2,DispFlags dflags) {

// debugging
//	.out.println("actual centers: "+z0+" "+z1+" "+z2);
	
		try {
			if (packData.hes > 0) {
				z0 = sphView.toApparentSph(z0);
				z1 = sphView.toApparentSph(z1);
				z2 = sphView.toApparentSph(z2);
			}
			double[] cnrs = new double[6];
			cnrs[0] = z0.x;
			cnrs[1] = z0.y;
			cnrs[2] = z1.x;
			cnrs[3] = z1.y;
			cnrs[4] = z2.x;
			cnrs[5] = z2.y;
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
					sc=HyperbolicMath.hyp_tang_incircle(z0,z1,z2,r0,r1,r2);
//					sc=HyperbolicMath.hyp_tri_incircle(z1,z2,z3);
				else if (packData.hes>0) 
					sc=SphericalMath.sph_tri_incircle(z0,z1,z2);
				else 
					sc=EuclMath.eucl_tri_incircle(z0,z1,z2);
				Complex z=sc.center;
				if (packData.hes <= 0 || Math.cos(z.x) >= 0) {
					if (packData.hes > 0)
						z = SphView.s_pt_to_visual_plane(z);
					tmpcolor = imageContextReal.getColor();
					imageContextReal.setColor(Color.BLUE);
					imageContextReal.drawString(dflags.getLabel(), 
							real_to_pix_x(z.x), real_to_pix_y(z.y));
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
					Complex z = sphView.
							toApparentSph(new Complex(corners[j * 2],
									corners[j * 2 + 1]));
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
	 * Recursively draw euclidean tile shapes for subdivision 
	 * rule to given depth; tile rules must have optional 
	 * position data from *.r rules file.
	 * @param sRules SubdivisionRules, (with optional position data)
	 * @param tiletype int, type of this tile
	 * @param base Complex[2], base of this tile
	 * @param depth int, recursive depth
	 * @return int
	 */
	public int drawTileRecurs(SubdivisionRules sRules,
			int tiletype,Complex []base,int depth,DispFlags dflags) {
		TileRule topRule=sRules.tileRules.get(tiletype-4);

		// for transformations
		Complex origin=base[0];
		Complex basedir=base[1].minus(base[0]);
		
		// first draw yourself, then position, recursively draw children
		double []stdC=new double[2*topRule.stdCorners.length];
		for (int j=0;j<topRule.stdCorners.length;j++) {
			Complex z=new Complex(topRule.stdCorners[j].
					times(basedir).add(origin));
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
				subtileBase[0] = new Complex(topRule.tileBase[n][0].
						times(basedir).add(origin));
				subtileBase[1] = new Complex(topRule.tileBase[n][1].
						times(basedir).add(origin));
				int rslt = drawTileRecurs(sRules, topRule.childType[n],
						subtileBase, depth - 1,dflags);
				if (rslt <= 0)
					return 0;
				count += rslt;
			}
		}
		return count;
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
	 * Call for repaint of canvas and small canvas.
	 */
	public void rePaintAll() {
		PackControl.canvasRedrawer.paintMyCanvasses(this,false);
	}

	/**
	 * CPScreen directly manages only the small canvass. For more
	 * see 'PackControl.canvasReDrawManager.paintCanvasses'.
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

	/**
	 * get the current 'packData' for this screen
	 * @return PackData
	 */
	public PackData getPackData() {
		return packData;
	}
	
	/**
	 * Replace the 'packData' for this screen; clean out any
	 * 'SliderFrame' objects.
	 * @param p
	 * @return int, nodeCount
	 */
	public int setPackData(PackData p) {
		p.packNum=screenNum;
		if (packData!=null && packData!=p) { // a different packing?
			if (packData.radiiSliders!=null) {
				packData.radiiSliders.dispose();
				packData.radiiSliders=null;
			}
			if (packData.schwarzSliders!=null) {
				packData.schwarzSliders.dispose();
				packData.schwarzSliders=null;
			}
			if (packData.angSumSliders!=null) {
				packData.angSumSliders.dispose();
				packData.angSumSliders=null;
			}			
			packData.smoother=null;
		}
		// handshake
		packData=p;
		p.cpScreen=this;
		this.setGeometry(p.hes);
		this.setPackName();
		return packData.nodeCount;
	}
	
	/**
	 * Update displayed Xtender tools (if any) for this packing
	 */
	public void updateXtenders() {
		// remove all Xtender tools
		int pnum=getPackNum();
		SmallCanvasPanel scp=PackControl.smallCanvasPanel;
		while (scp.cpInfo[pnum].getComponentCount()>1)
			scp.cpInfo[pnum].remove(1);
		scp.cpInfo[pnum].revalidate();
		// re-add those for this packing
		Vector<PackExtender> Xvec=packData.packExtensions;
		for (int i=0;i<Xvec.size();i++) {
			PackExtender pX=Xvec.get(i);
			MyTool Xtool=pX.XtenderTool;
			if (Xtool!=null) scp.cpInfo[pnum].add(Xtool);
		}
		scp.cpInfo[pnum].revalidate();
		scp.cpInfo[pnum].repaint();
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
	 * Return the geometry of 'packData'
	 * @return
	 */
	public int getGeom(){
		return packData.hes;
	}
	

	/**
	 * Update small canvas pack label using 'packData.fileName'.
	 */
	public void setPackName() {
		if (packData.fileName== null || packData.fileName.trim().length()==0) 
			packData.setName("NoName");
		PackControl.smallCanvasPanel.packName[getPackNum()].
				setText("P"+getPackNum()+" "+
						packData.fileName+geomAbbrev[packData.hes+1]);
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
	 * Return the 'packData' pack index, or -1 if not set
	 * @return int
	 */
	public int getPackNum() {
		if (packData==null)
			return -1;
		return packData.packNum;
	}
	
	/**
	 * Call if new packing has been put in place; clear/reset the screen
	 * and 'dispOptions'.
	 */
	public int emptyScreen() {
		reset();
		sphView.defaultView();
		clearCanvas(true);
		return 1;
	}
	 
	public int getLineThickness() {
		return linethickness;
	}

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
 
	public int getFillOpacity() {
		return fillOpacity;
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
		return (int)(.5+(y-(YMin+YHeight))*-pixFactor);  // - due to y flip
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
	

} 
