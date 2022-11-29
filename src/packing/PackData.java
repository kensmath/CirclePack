package packing;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import JNI.DelaunayData;
import JNI.ProcessDelaunay;
import allMains.CPBase;
import allMains.CirclePack;
import baryStuff.BaryPtData;
import circlePack.PackControl;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.Face;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import complex.MathComplex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.PairLink;
import dcel.RawManip;
import dcel.SideData;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.DataException;
import exceptions.PackingException;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import komplex.DualTri;
import komplex.EdgeSimple;
import komplex.Triangulation;
import listManip.BaryCoordLink;
import listManip.BaryLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.TileLink;
import listManip.Translators;
import listManip.VertList;
import listManip.VertexMap;
import math.Matrix3D;
import math.Mobius;
import math.Point3D;
import microLattice.Smoother;
import posting.PostFactory;
import rePack.EuclPacker;
import rePack.HypPacker;
import rePack.RePacker;
import tiling.Tile;
import tiling.TileData;
import util.ColorUtil;
import util.DispFlags;
import util.PathBaryUtil;
import util.PathUtil;
import util.RadIvdPacket;
import util.SphView;
import util.StringUtil;
import util.TriAspect;
import util.UtilPacket;
import widgets.AngSumSliders;
import widgets.RadiiSliders;
import widgets.SchwarzSliders;

/**
 * This is the fundamental data repository for a circle packing and is
 * associated with a CPDrawing for display. This is a workhorse, with 
 * methods for reading/writing, combinatorics, and manipulations.
 * @author kens, sometime last century
 *
 */
public class PackData{
	
	public static double TOLER=.0000000001; // TODO: fix up all thresholds
	public static double OKERR=.000000001; 
	public static final int MAX_ACCUR=15;   // digits of accuracy for file writing
	public static final int MAX_PETALS=1000; // the most petals a flower can have
	
	public enum PackState { INITIAL,NODECOUNT,CHECKCOUNT,TILECOUNT,
		TRIANGULATION,NEUTRAL,PACKNAME,ABG,
		GEOMETRY,CIRCLE_PLOT_FLAGS,FACE_PLOT_FLAGS,SELECT_RADII,ANGLE_AIMS,
		INV_DISTANCES,ANGLESUMS,C_COLORS,CIRCLE_COLORS,TILE_COLORS,VERT_MARK,
		TRI_COLORS,T_COLORS,VERTEX_MAP,VERT_LIST,GLOBAL_VERT_LIST,GLOBAL_FACE_LIST,FACE_TRIPLES,
		EDGE_LIST,GLOBAL_EDGE_LIST,DUAL_EDGE_LIST,GLOBAL_DUAL_EDGE_LIST,POINTS,
		RADII_INTERACTIONS,DOUBLES,INTEGERS,COMPLEXES,BARY_VECTOR,BARY_DATA,
		SCHWARZIAN};

//	public enum PackExtensions {BROOKS_QUAD,RIEMANN_HILBERT,CONF_WELD,WORD_WALK,
//		CURV_FLOW,VELLING_PANTS,NULL};
		
	public RePacker rePacker=null;  // holds repacker (e.g., GOrepack) for continued use
	
    static int MAX_COMPONENTS=20;

	// Main data vectors
	public Vector<PackExtender> packExtensions;  // vector of extensions
	public TileData tileData; // for associated tilings 
	
    // various counts
	public int nodeCount;   // number of nodes
    public int faceCount;     // number of faces 
    public int intNodeCount;  // number of interior nodes
    
    public PackDCEL packDCEL; // for possible DCEL structure 

    // various global pack info
    public int hes;           // curvature of ambient geometry,-1=hyp,0=eucl,1=sph 
    public boolean status;    // false when pack is empty
	public String fileName;   // filename when file was read/written
    public int euler;   	  // Euler characteristic
    public int genus;         // genus of complex. Euler+#bdry=2-2g, g=(2-Euler-#bdy)/2 
    public int intrinsicGeom; // intrinsic geometry (due to combinatorics only)
    public int activeNode;    // currently active_node 
    public int colorIndx;      // last index used for "spread" colors, see 'color_circles'. 
    
    // associated data
    public NodeLink vlist;    // pack utility vert list
    public FaceLink flist;    // pack utility face list
    public EdgeLink elist;    // pack utility edge list
    public HalfLink hlist;    // pack utility halfedge list
    public TileLink tlist;    // pack utility tile list
    public GraphLink glist;   // pack utility dual graph link
    public PointLink zlist;   // pack utility complex point list
    public BaryLink blist;    // pack utility barycentric point list
    public VertexMap vertexMap;// optional map of (some) indices 
    public Point3D[] xyzpoint;  // TODO: should be vector? pointer to associated 3D coords
    public Vector<Double> utilDoubles;  // utility vector to hold doubles (caution: indexed from 0)
    public Vector<Integer> utilIntegers;  // utility vector to hold integers
    public Vector<Complex> utilComplexes;  // utility vector to hold complex numbers
    public Vector<BaryPtData> utilBary;    // utility vector of BaryPtData, holds points and data
    
    // combinatoric 
    public int[] bdryStarts;  // indices for verts starting the bdry components (indexed from 1) 
    public int packNum;		// lies between 0 and CPBase.NUM_PACKS-1  

    // utility variables passing info in methods -- use immediately after method call!
    public int util_A;
    public int util_B;
    public boolean util_bool;

    public CPdrawing cpDrawing; // pointer to screen associated with this packdata 
    
    public String getDispOptions; // if not null, was set on readpack with 'DISP_FLAGS:'
    
    public RadiiSliders radiiSliders;
    public SchwarzSliders schwarzSliders;
    public AngSumSliders angSumSliders;
    
    public Smoother smoother;    // 6/2020. add a smoother
    
    public PackData(int packnum) {
    	this(null);
    	this.fileName="Empty";
    	packNum=packnum;
    }
    
    // Constructor
    public PackData(CPdrawing parentScreen){
        cpDrawing = parentScreen;
        // Note: creating new speculative PackData sets, use 'null' CPDrawing until finished. 
        if (cpDrawing !=null) 
        	packNum = cpDrawing.getPackNum();
        else 
        	packNum=CPBase.NUM_PACKS; // temporary number
        packDCEL=new PackDCEL();
        packDCEL.p=this;
    	xyzpoint=null;
    	packExtensions=new Vector<PackExtender>(2);
    	tileData=null;
    	this.fileName="Empty";
    	getDispOptions=null;
    	utilDoubles=null;
    	utilIntegers=null;
    	utilComplexes=null;
    	vertexMap=null;
    	colorIndx=0;
    	smoother=null;
    }
    
    /**
     * Create a DCEL structure for 'this' packing and then attach it.
     * @return int
     */
    public int attachDCEL() {
    	PackDCEL raw=CombDCEL.getRawDCEL(this);
    	raw.p=this;
       	PackDCEL pdc=CombDCEL.extractDCEL(raw,null,raw.alpha);
       	return attachDCEL(pdc);
    }
    
    /**
     * Attach a new or modified DCEL structure for this packing. 
     * 
     * NOTE: on leaving, 'pdcel.oldNew' is set to null. Calling 
     * routine needs to save it first and reinstall if needed. 
     *  
     * TODO: may need to save additional info when 
     * swapping in new dcel: e.g., 'invDist's, 
     * 'schwarzian's, face colors, etc. And may need
     * to adjust vlist, elist, flist, etc. based on 'oldNew'.
     * 
     * @param pdcel PackDCEL
     * @return int, vertCount on success, 0 on failure
     */
    public int attachDCEL(PackDCEL pdcel) {

    	packDCEL=pdcel;
    	pdcel.p=this;
    	if (pdcel.alpha==null)
    		pdcel.setAlpha(0,null,true);
    	if (pdcel.gamma==null)
    		pdcel.setGamma(0);
		
    	// set some counts
		nodeCount=pdcel.vertCount;
		faceCount=pdcel.faceCount;
    	euler=nodeCount-(pdcel.edgeCount/2)+faceCount;
		genus=(2-euler-pdcel.idealFaceCount)/2;
		intrinsicGeom=PackData.getIntrinsicGeom(this);
    	fileName=StringUtil.dc2name(fileName);
     	fillcurves();
    	if (pdcel.gamma==null)
    		pdcel.gamma=pdcel.alpha.next;
    	return pdcel.vertCount;
    }
    
    /**
     * Reset pack data space and set PackDCEL.sizeLimit.
     * @param new_size int (often current 'sizeLimit')
     * @return 1
     */
    public int reset_pack_space(int new_size) {
        xyzpoint=null;
        nodeCount=0;
        status=false;
        fileName = "";
        tileData=null;
       	packDCEL=new PackDCEL();
       	packDCEL.p=this;
       	packDCEL.sizeLimit=((int)((new_size-1)/1000))*1000+1000;
       	packDCEL.vertices=new Vertex[packDCEL.sizeLimit+1];
       	return 1;
    }

    /*
     *  Drawing flag scheme: flags indicate drawing instructions
     *  for objects -- circles/lines/faces. Bits set as follows:
     *  1  -  draw object?
     *  2  -  fill? (4 and 16 imply 2, also)
     *  4  -  off = foreground, on = background 
     *  (applies to interior, not border, overriden by bit 16)
     *  8  -  border color? (default=foreground, else recorded color)
     *  16 -  interior color? (default set by bit 4, on -- recorded color)
     *  32 -  display label?
       
     *  Eg.  flag=3: filled object, in foreground
     *  flag=9: open object, border in (recorded) color
     *  (for 'edge', this gives colored edge)
     *  flag=19: filled in color, border in foreground
     *  flag=27: filled, border and interior in color
     *  flag=15: filled with background, border in color
     *  flag=32: label only (face or circle)
       
     *  Normally, flag for each type of object; often passed on to
     *  subroutine, so may need color code with it:
     *  Eg. (cflag, ccol, ecol) for circle flag, int color, border color.
    */
  
    /** 
     * Choose 'alpha' halfedge; acts as root vertex. 
     * Should be interior if possible. Keep current 
     * value if it is legal. Drawing order recomputed 
     * if needed. 
     */
    public void chooseAlpha(){
   		packDCEL.setAlpha(0,null,true);
   		return;
    }

    /**
     * Choose 'gamma' halfedge, normally placed on 
     * positive y-axis. Must be distinct from 'alpha'. 
     * Keep current value, if legal.
     */
    public void chooseGamma() { // avoid alpha
   		packDCEL.setGamma(0);
   		return;
    } 
    
    /**
     * Set prescribed 'alpha' vertex; must be interior. Move 'gamma' 
     * if necessary. Face drawing order is automatically recomputed.
     * @param v int, preferred or 0
     * @return 1, 0 on failure
     */
    public int setAlpha(int v) {
    	if (!status) 
    		return 0;
   		return packDCEL.setAlpha(v,null,false);
    } 

    /**
     * Set packing 'gamma' index
     * @param i int, can't be 'alpha'
     * @return 1 on success, 0 on failure
     */
    public int setGamma(int i) {
        if (status && i>0 && i<= nodeCount && i != packDCEL.alpha.origin.vertIndx)
       		return packDCEL.setGamma(i);
        return 0;
    } 
    
    /** Set the 'fileName'; on failure, set to 'NoName'
     * @param s String
     */
	public void setName(String s) {
		if (s!=null && (s=s.trim()).length()>0)
			fileName = s;
		else fileName="NoName";
		if (cpDrawing!=null)
			cpDrawing.setPackName(); // record in small canvas label
	}
	
	/** 
	 * Get 'fileName' for this packing
	 * @return new String
	 */
	public String getName() {
		return new String(fileName);
	}

	/** 
	 * @return int
	 */
	public int getAlpha() {
		return packDCEL.alpha.origin.vertIndx;
	}
	
	/** 
	 * @return int
	 */
	public int getGamma() {
		return packDCEL.gamma.origin.vertIndx;
	}
	
	/**
	 * Returns string listing key data on this circle packing.
	 * @return String
	 */
	public String toString() {
		String temp = "";
		temp += "File name: " + fileName + "\n";
		temp += "Node count: " + nodeCount + "\n";
		temp += "Face count: " + faceCount + "\n";
		temp += "Number of interior nodes: " + intNodeCount + "\n";
		temp += "Number of boundary nodes: " + (nodeCount - intNodeCount) + "\n";
		temp += "Number of boundary components: " + getBdryCompCount() + "\n";
		temp += "Geometry: " + intToGeometry(hes) + "\n";
		temp += "Intrinsic geometry: " + intToGeometry(intrinsicGeom) + "\n";
		temp += "Genus: " + genus + "\n";
		temp += "Euler charasteric: " + euler + "\n";
		temp += "Alpha: " + packDCEL.alpha.origin.vertIndx + "\n";
		return temp;
	}

	/**
	 * return string for geometry associated with integer hes
	 * @param hes int
	 * @return String, empty if hes not in {
	 */ 
	public static String intToGeometry(int hes) {
		if (hes >0 )	return "Spherical";
		if (hes <0 ) return "Hyperbolic";
		return "Euclidean";
	}
	
	/**
	 * 'aim' from 'Vertex'
	 * @param v int
	 * @return double
	 */
	public double getAim(int v) {
		return packDCEL.vertices[v].aim;
	}
	
	/** 
	 * Store 'aim' in 'Vertex'
	 * @param v int
	 * @param aim double
	 */
	public void setAim(int v,double aim) {
		packDCEL.vertices[v].aim=aim;
	}
	
	/**
	 * get number of non-ideal faces at 'v'; this is usual
	 * meaning of 'num' for traditional vertices.
	 * @param v int
	 * @return int
	 */
	public int countFaces(int v) {
		return packDCEL.countFaces(packDCEL.vertices[v]);
	}
	
	/**
	 * get number of petals at 'v'
	 * @param v int
	 * @return int (same as 'countFaces' for interior 'v')
	 */
	public int countPetals(int v) {
		return packDCEL.countPetals(v);
	}
	
	/**
	 * 'curv' from 'Vertex'
	 * @param v int
	 * @return double
	 */
	public double getCurv(int v) {
		return packDCEL.vertices[v].curv;
	}
	
	/** 
	 * Store 'curv' in 'Vertex'
	 * TODO: might eliminate this call
	 * @param v int
	 * @param aim double
	 */
	public void setCurv(int v,double curv) {
		packDCEL.vertices[v].curv=curv;
	}
	
	/**
	 * Return the bdryFlag of vertex v; often used for its
	 * value '1' to be used in 'for' loops.
	 * @param v int
	 * @return int (should be 0 or 1)
	 */
	public int getBdryFlag(int v) {
		return packDCEL.vertices[v].bdryFlag;
	}
	
	public void setBdryFlag(int v,int flag) {
		packDCEL.vertices[v].bdryFlag=flag;
	}
	
	/**
	 * Is this a boundary vertex? Depends on bdry edges
	 * being identified with 'faceIndx'<0.
	 * @param v int
	 * @return boolean
	 */
	public boolean isBdry(int v) {
		return packDCEL.isBdry(v);
	}
	
	/**
	 * A face is 'boundary' if one or more of its vertices
     * is boundary.
	 * @param f int
	 * @return boolean
	 */
	public boolean isBdryFace(int f) {
		if (f<1 || f>faceCount)
			return false;
		int[] fverts=getFaceVerts(f);
		for (int j=0;j<fverts.length;j++)
			if (isBdry(fverts[j]))
				return true;
		return false;
	}
	
	/**
	 * Do v and w share an edge?
	 * @param v int
	 * @param w int
	 * @return boolean
	 */
	public boolean areNghbs(int v,int w) {
		return packDCEL.vertices[v].halfedge.isNghb(w);
	}
	
	/**
	 * Are v, w bdry and on same bdry component?
	 * @param v int
	 * @param w int
	 * @return boolean
	 */
	public boolean onSameBdryComp(int v,int w) {
		if (!isBdry(v) || !isBdry(w))
			return false;
		if (v==w)
			return true;
		return CombDCEL.onSameBdryComp(packDCEL,v,w);
	}
	
	/**
	 * Get array of vertices for face 'f'
	 */
	public int[] getFaceVerts(int f) {
		return packDCEL.faces[f].getVerts();
	}
	
	public Complex[] getFaceCorners(int f) {
		if (f<=0)
			return null;
		Complex[] corners=new Complex[3];
		combinatorics.komplex.DcelFace dface=packDCEL.faces[f];
		HalfEdge he=dface.edge;
		for (int j=0;j<3;j++) {
			corners[j]=packDCEL.getVertCenter(he);
			he=he.next;
		}
		return corners;
	}
	
	/**
	 * Get flower petals, but don't close up for interior
	 * @param v int
	 * @return int[]
	 */
	public int[] getPetals(int v) {

		int[] petals;
		ArrayList<Integer> al=new ArrayList<Integer>(0);
		HalfEdge he=packDCEL.vertices[v].halfedge;
		do {
			al.add(he.twin.origin.vertIndx);
			he=he.prev.twin; // cclw
		} while (he!=packDCEL.vertices[v].halfedge);
		int n=al.size();
		petals=new int[n];
		for (int j=0;j<n;j++)
			petals[j]=al.get(j);
		return petals;
	}
	
	/**
	 * Get the traditional array of nghb'ing vertices;
	 * meaning first repeats at end if 'v' is interior.
	 * @param v int
	 * @return int[]
	 */
	public int[] getFlower(int v) {
		return packDCEL.vertices[v].getFlower(true);
	}
	
	/** 
	 * the first cclw petal. If not bdry, this is
	 * rather ambiguous.
	 * @param v int
	 * @return int
	 */
	public int getFirstPetal(int v) {
		Vertex vert=packDCEL.vertices[v];
		return vert.halfedge.twin.origin.vertIndx;
	}

	/** 
	 * Get the last petal of the flower for 'v';
	 * same as first petal if 'v' is interior.
	 * @param v int
	 * @return int
	 */
	public int getLastPetal(int v) {
		int[] flower=getFlower(v);
		return flower[flower.length-1];
	}
	
	/**
	 * get the jth petal for vertex v.
	 * @param v int
	 * @param j int
	 * @return int
	 */
	public int getPetal(int v, int j) {
		int[] flower=getFlower(v);
		if (j<0 || j>=flower.length)
			throw new CombException("flower for "+v+" does not have petal index "+j);
		return flower[j];
	}
	
	/**
	 * Get 'j'th entry in face flower of 'v'
	 * @param v
	 * @param j
	 * @return
	 */
	public int getFaceFlower(int v,int j) {
		int[] fflower=getFaceFlower(v);
		if (j<0 || j> fflower.length) 
			throw new CombException("face flower for "+v+
					" doesn't have index "+j);
		return fflower[j];
	}

	/**
	 * Get array of cclw nghb'ing face indices, closed if
	 * interior, omitting any ideal faces (there should be
	 * at most 1).
	 */
	public int[] getFaceFlower(int v) {
		int[] flower;
		Vertex vert=packDCEL.vertices[v];
		int n=vert.getNum();
		flower=new int[n];
		if (!vert.isBdry()) // close up
			flower=new int[n+1];
		int ftick=0;
		HalfEdge he=vert.halfedge;
		do {
			flower[ftick]=he.face.faceIndx;
			ftick++;
			he=he.prev.twin;
		} while (ftick<n);
		if (!vert.isBdry())
			flower[n]=flower[0];
		return flower;
	}

	/**
	 * TODO: cut out this call
	 * Get the center of the incircle for face index 'f'.
	 * @param f int
	 * @return Complex
	 */
	public Complex getFaceCenter(int f) {
		return packDCEL.getFaceCenter(packDCEL.faces[f]).center;
	}
		
	/**
	 * Reset the geometry for the cpDrawing graphic objects;
	 * if 'cpDrawing' is null, just return;
	 * @param hes int, 1,0, or -1
	 */
	public void setGeometry(int hes) {
		if (cpDrawing!=null) 
			cpDrawing.setGeometry(hes);
	}
	
	/**
	 * Get the count of bdry components
	 * @return
	 */
	public int getBdryCompCount() {
		return packDCEL.idealFaceCount;
	}
	
	/**
	 * Return a vertex on the j_th bdry component;
	 * indexing starts at 1.
	 * @param j int
	 * @return int, bdry vert index
	 */
	public int getBdryStart(int j) {
		return packDCEL.idealFaces[j].edge.origin.vertIndx;
	}
	
	/**
	 * Get center/radius from 'Vertex' in 'CircleSimple' form.
	 * Note: see 'RedEdge.getCircleSimple' to get the data
	 * from a red edge.
	 * @param v int
	 * @return CircleSimple
	 */
	public CircleSimple getCircleSimple(int v) {
		return new CircleSimple(packDCEL.vertices[v].center,packDCEL.vertices[v].rad);
	}
	
	/** 
	 * Set data only in 'Vertex'. See 'RedEdge.setCircleSimple'
	 * to set data in a red edge. 
	 * @param cS CircleSimple
	 */
	public void setCircleSimple(int v,CircleSimple cS) {
		packDCEL.vertices[v].center=cS.center;
		packDCEL.vertices[v].rad=cS.rad;
	}
	
	/**
	 * Enter center (x,y)
	 * @param v int
	 * @param x double
	 * @param y double
	*/
	public void setCenter(int v,double x,double y) {
		setCenter(v,new Complex(x,y));
	}
	
	/**
	 * Set the center for 'vert'. If hyperbolic and |z| greater than 1,
	 * scale to put in disc. If spherical, assume z=(theta,phi)
	 * and store as (theta, phi).
	 * @param v int
	 * @param z Complex
	 */
	public void setCenter(int v,Complex z) {
		// TODO: somewhere, have to do checks in hyp/sph
		//    cases. Problem we might be in the midst of 
		//    changing geometry.
		packDCEL.setCent4Edge(packDCEL.vertices[v].halfedge,z);
	}
	
	/**
	 * Return center as a new 'Complex'.
	 * @param v int
	 * @return new Complex
	 */
	public Complex getCenter(int v) {
		Complex z=null; 
		z=packDCEL.getVertCenter(packDCEL.vertices[v].halfedge);
		return z;
	}
	
	/**
	 * Get clone of face color
	 * @param f int
	 * @return new Color
	 */
	public Color getFaceColor(int f) {
		return packDCEL.faces[f].getColor();
	}
	
	/**
	 * set color to clone of 'color'
	 * @param f int
	 * @param color Color
	 */
	public void setFaceColor(int f,Color color) {
		packDCEL.faces[f].setColor(color);
	}

	/**
	 * Get clone of edge color
	 * @param he HalfEdge
	 * @return new Color
	 */
	public Color getEdgeColor(HalfEdge he) {
		return he.getColor();
	}
	
	/**
	 * set color to clone of 'color'
	 * @param he HalfEdge
	 * @param color Color
	 */
	public void setEdgeColor(HalfEdge he,Color color) {
		he.setColor(color);
	}
	
	/**
	 * get clone of circle color
	 * @param v int
	 * @return new Color
	 */
	public Color getCircleColor(int v) {
		return packDCEL.vertices[v].getColor();
	}
	
	/**
	 * set circle color to clone of 'color'
	 * @param v int 
	 * @param color Color 
	 */
	public void setCircleColor(int v,Color color) {
		try {
			packDCEL.vertices[v].setColor(color);
		} catch(Exception ex) {}
	}
	
	public int getVertUtil(int v) {
		return packDCEL.vertices[v].vutil;
	}
	
	public void setVertUtil(int v,int m) {
		packDCEL.vertices[v].vutil=m;
	}

	public int getEdgeUtil(int e) {
		return packDCEL.edges[e].eutil;
	}
	
	public void setEdgeUtil(int e,int m) {
		packDCEL.edges[e].eutil=m;
	}
	
	public int getVertMark(int v) {
		return packDCEL.vertices[v].mark;
	}
	
	public void setVertMark(int v,int m) {
		packDCEL.vertices[v].mark=m;
	}
	
	public int getPlotFlag(int v) {
		return packDCEL.vertices[v].plotFlag;
	}
	
	public void setPlotFlag(int v,int m) {
		packDCEL.vertices[v].plotFlag=m;
	}
	
	public int getFaceMark(int f) {
		return packDCEL.faces[f].mark;
	}
	
	public void setFaceMark(int f,int m) {
		packDCEL.faces[f].mark=m;
	}
	
	/** 
	 * Return actual radius of a vertex, meaning in the hyp case the
	 * "x-radius" is converted to the actual hyperbolic radius, which 
	 * is what outside world should see. In hyp case, when x-radius<0, 
	 * it is -r for eucl radius r, so we just return -r.
	 * @param v int
	 * @return double
	*/
	public double getActualRadius(int v) {
		double x=packDCEL.getVertRadius(packDCEL.vertices[v].halfedge);
		
		// check for hyp case
	    if (hes<0 && x> 0.0) {
	    	if (x>.0001) return ((-0.5)*Math.log(1.0-x));
	    	else return (x*(1.0+x*(0.5+x/3))/2);
	    }
	    return x;
	}
	
	/**
	 * Return the stored radius. This means in the hyp case,
	 * return the x-radius. (See 'getActualRadius' to instead
	 * convert x-radius to actual hyp radius.).
	 * @param v int
	 * @return double
	 */
	public double getRadius(int v) {
		return packDCEL.getVertRadius(packDCEL.vertices[v].halfedge);
	}
	  
	/**
	 * Store actual radius 'r' in internal form; only issue is hyp case,
	 * when 'r' is the actual hyperbolic radius, and is converted 
	 * to x_radius for rData. 
	 * For numerical reasons, small hyp radii 'r' is converted 
	 * to x=2*r*(2-r*(1-2*r/3)). (Recall x=1-exp(-2h)=1-s^2.)
	 * @param v int
	 * @param r double
	 */
	public void setRadiusActual(int v,double r) {
		double rad=r;
		if(hes < 0) { // hyperbolic: store as x-radii
			if(r > 0.0) {
				if(r > 0.0001) 
					rad=1-Math.exp(-2.0*r);
				else // for small values, use polynomial approximation
					rad=2.0*r*(1.0 - r*(1.0-2.0*r/3.0));
			}
			// can be negative (useful as storage of eucl radius for horocycles)
		}
		setRadius(v,rad);
	}
	
	/** 
	 * Store radius for 'v' (in all its locations). ('r' is in 
	 * internal form; i.e., in hyp case, 'r' should already 
	 * be in x_radius form. If it needs to be converted, call
	 * 'setRadiusActual'.)
	 * e.g., in 'RedEdge's.)
	 * @param v int
	 * @param r double
	 */
	public void setRadius(int v,double r) {
		if (hes>0 && r>=Math.PI) 
			r=Math.PI-OKERR;
		packDCEL.setVertRadii(v,r);
		return;
	}

	/**
	 * Currently, just send error to statusPanel.
	 * TODO: would like to have 'beep' sound
	 * @param errmsg
	 */
	public void flashError(String errmsg) {
		PackControl.consoleCmd.dispConsoleMsg(errmsg);
		PackControl.shellManager.recordError(errmsg);
		// beep
//		System.out.print("\007");
//		System.out.flush();
	}
	
	/**
	 * Details for debugging can be put in 'Error' tab.
	 * @param errmsg String
	 */
	void detailError(String errmsg) {
		// TODO: fixup 
	}

/* ================== combinatoric utilies for complexes ================ */	
	
	/**
	 * If w is neighbor of v, return its index in the flower of v; 
	 * else return -1. Note this works for DCEL structures, but
	 * answer is no so useful if not -1.
	 * @param v
	 * @param w
	 * @return int index, -1 on problem
	 */
	public int nghb(int v,int w) {
		HalfEdge vhe=packDCEL.vertices[v].halfedge;
		HalfEdge he=vhe;
		int j=0;
		do {
			if (he.twin.origin.vertIndx==w)
				return j;
			he=he.prev.twin;
			j++;
		} while (he!=vhe);
		return -1;
	}
	
	/**
	 * If {v,w} is edge, find the clw edge about 'v', if it exists.
	 * @param v int
	 * @param w int
	 * @return EdgeSimple, null on failure
	 */
	public EdgeSimple clwEdge(int v,int w) {
		int u;
		int indvw=nghb(v,w);
		if (indvw<0 || (indvw==0 && isBdry(v)))
			return null;
		int[] flower=getFlower(v); 
		if (indvw==0) { // must be interior
			u=flower[this.countFaces(v)-1];
			return new EdgeSimple(v,u);
		}
		return new EdgeSimple(v,flower[indvw-1]);
	}
	
	/**
	 * Return non-closed list of corners for dual face for v,
	 * mainly centers of faces. For bdry v, add the partial 
	 * edges ending at the tang pts of the two bdry edges. For v 
	 * red, assume initial face is in place, but the proceed 
	 * face-by-face.
	 * @param v int
	 * @return Complex[]
	 */
	public Complex[] corners_dual_face(int v) {
		Vertex vert=packDCEL.vertices[v];
		HalfEdge spoke=vert.halfedge;
		ArrayList<Complex> zs=new ArrayList<Complex>();
		
		// if !simple, then will be laying faces out
		//    successively
		boolean simple=true;
		if (vert.redFlag) {
			HalfEdge upstream=spoke.twin.next.twin;
			if (spoke.myRedEdge==null || 
					spoke.myRedEdge.prevRed.myEdge!=upstream)
				simple=false;
		}
		ArrayList<combinatorics.komplex.DcelFace> faceflower=vert.getFaceFlower();
		Iterator<combinatorics.komplex.DcelFace> fis=faceflower.iterator();
		
		// use faces as already placed
		if (simple) {			
			while(fis.hasNext()) {
				combinatorics.komplex.DcelFace face=fis.next();
				if (face.faceIndx>=0)
					zs.add(packDCEL.getFaceIncircle(face).center);
			}
			
			// for bdry, include v itself, and bdry tangency points
			if (vert.bdryFlag!=0) {
				CircleSimple cs0=packDCEL.getVertData(vert.halfedge);
				CircleSimple cs1=packDCEL.getVertData(vert.halfedge.next);
				CircleSimple csup=packDCEL.getVertData(vert.halfedge.twin.next.twin);
				zs.add(0,CommonMath.genTangPoint(cs0,cs1,hes));
				zs.add(0,packDCEL.getVertCenter(spoke)); // include v itself
				zs.add(CommonMath.genTangPoint(cs0,csup,hes));
			}
		}
		
		// else treat first face as in place, but 
		else {
			HalfLink spokes=vert.getEdgeFlower();
			HalfEdge he=spokes.remove(0);
			TriAspect tri=new TriAspect(packDCEL,he.face);
			do {
				CircleSimple cs=tri.getFaceIncircle();
				zs.add(cs.center);
				he=he.prev.twin;
				tri=workshops.LayoutShop.analContinue(packDCEL,he.twin,tri,false);
			} while (tri!=null && he!=vert.halfedge);

			// for bdry, include v itself, and bdry tangency points
			if (vert.bdryFlag!=0) {
				CircleSimple cs0=packDCEL.getVertData(vert.halfedge);
				CircleSimple cs1=packDCEL.getVertData(vert.halfedge.next);
				CircleSimple csup=packDCEL.getVertData(vert.halfedge.twin.next.twin);
				zs.add(0,CommonMath.genTangPoint(cs0,cs1,hes));
				zs.add(0,packDCEL.getVertCenter(spoke)); // include v itself
				zs.add(CommonMath.genTangPoint(cs0,csup,hes));
			}
		}			

		int n=zs.size();
		Complex[] pts=new Complex[n];
		Iterator<Complex> zis=zs.iterator();
		int tick=0;
		while (zis.hasNext())
			pts[tick++]=zis.next();
		return pts;
	}
	
	/**
	 * Return non-closed list of corners hull of v,
	 * formed by the tangency points of edges from v,
	 * including v itself if v is bdry.
	 * @param v int
	 * @return Complex[]
	 */
	public Complex[] vertex_hull(int v) {
		Vertex vert=packDCEL.vertices[v];
		HalfEdge he=vert.halfedge;
		ArrayList<Complex> zs=new ArrayList<Complex>();
		
		boolean simple=true;
		if (vert.redFlag) {
			HalfEdge upstream=he.twin.next.twin;
			if (he.myRedEdge==null || 
					he.myRedEdge.prevRed.myEdge!=upstream)
				simple=false;
		}
		HalfLink spokes=vert.getEdgeFlower();
		Iterator<HalfEdge> sis=spokes.iterator();
		
		// use spokes as already placed
		if (simple) {			
			while(sis.hasNext()) {
				he=sis.next();
				zs.add(CommonMath.genTangPoint(
						packDCEL.getVertData(he),
						packDCEL.getVertData(he.next),hes));
			}
		}
		
		// else treat first face as in place, but 
		else {
			he=sis.next();
			zs.add(CommonMath.genTangPoint(
					packDCEL.getVertData(he),
					packDCEL.getVertData(he.next),hes));
			TriAspect tri=new TriAspect(packDCEL,he.face);
			do {
				int j=tri.edgeIndex(he);
				CircleSimple c1=new CircleSimple(tri.center[j],tri.radii[j]);
				CircleSimple c2=new CircleSimple(tri.center[(j+2)%3],tri.radii[(j+2)%3]);
				zs.add(CommonMath.genTangPoint(c1,c2,hes));
				he=he.prev.twin;
				tri=workshops.LayoutShop.analContinue(packDCEL,he.twin,tri,false);
			} while (tri!=null && he!=vert.halfedge);
		}			

		// for bdry, include v itself, and bdry tangency points
		if (vert.bdryFlag!=0) 
			zs.add(packDCEL.getVertCenter(vert.halfedge)); // v itself
		int n=zs.size();
		Complex[] pts=new Complex[n];
		Iterator<Complex> zis=zs.iterator();
		int tick=0;
		while (zis.hasNext())
			pts[tick++]=zis.next();
		return pts;
	}
	
	/**
	 * Return corners of "hull" for this face; that is,
	 * formed by the tangency points of the face edges.
	 * @param face Face
	 * @return Complex[], non-closed array
	 */
	public Complex[] face_hull(combinatorics.komplex.DcelFace face) {
		ArrayList<Complex> zs=new ArrayList<Complex>();
		HalfEdge he=face.edge;
		do {
			zs.add(tangencyPoint(he));
			he=he.next;
		} while (he!=face.edge);

		// convert
		int n=zs.size();
		Complex[] pts=new Complex[n];
		Iterator<Complex> zis=zs.iterator();
		int tick=0;
		while (zis.hasNext())
			pts[tick++]=zis.next();

		return pts;
	}
	
	/**
	 * Return non-closed list of corners for face f, in correct order. 
	 * @param f int
	 * @return Complex[]
	 */
	public Complex[] corners_face(int f) {
		ArrayList<Complex> zs=new ArrayList<Complex>();
		combinatorics.komplex.DcelFace face=packDCEL.faces[f];

		HalfEdge he=face.edge;
		do {
			zs.add(packDCEL.getVertCenter(he));
			he=he.next;
		} while(he!=face.edge);

		int n=zs.size();
		Complex[] pts=new Complex[n];
		Iterator<Complex> zis=zs.iterator();
		int tick=0;
		while (zis.hasNext())
			pts[tick++]=zis.next();
		return pts;
	}		
	
	/**
	 * Return non-closed list of corners for "paver" of v, i.e.,
	 * polygonal region defined as union of v's faces (include
	 * center of v itself if bdry). If v is red, we treat its 
	 * first face as in place, but successively recompute the 
	 * remaining faces based on radii.
	 * @param v int
	 * @return Complex[], non-closed array 
	 */
	public Complex[] corners_paver(int v) {
		ArrayList<Complex> zs=new ArrayList<Complex>();
		Vertex vert=packDCEL.vertices[v];
		HalfEdge he=vert.halfedge;
		
		boolean simple=true;
		if (vert.redFlag) {
			HalfEdge upstream=he.twin.next.twin;
			if (he.myRedEdge==null || 
					he.myRedEdge.prevRed.myEdge!=upstream)
				simple=false;
		}
		HalfLink spokes=vert.getEdgeFlower();
		Iterator<HalfEdge> sis=spokes.iterator();
	
		// assume faces in place
		if (!simple) {
			while(sis.hasNext()) 
				zs.add(packDCEL.getVertCenter(sis.next().next));
		}
		else {
			HalfEdge spoke=vert.halfedge;
			zs.add(packDCEL.getVertCenter(spoke.next));
			TriAspect tri=new TriAspect(packDCEL,spoke.face);
			do {
				int j=tri.edgeIndex(spoke);
				zs.add(tri.center[(j+2)%3]);
				spoke=spoke.prev.twin;
				tri=workshops.LayoutShop.analContinue(packDCEL,spoke.twin,tri,false);
			} while (tri!=null && spoke!=vert.halfedge);
		}

		if (vert.bdryFlag!=0)
			zs.add(0,packDCEL.getVertCenter(vert.halfedge)); // v itself
		int n=zs.size();
		Complex[] pts=new Complex[n];
		Iterator<Complex> zis=zs.iterator();
		int tick=0;
		while (zis.hasNext())
			pts[tick++]=zis.next();
		return pts;
	}

	/**
	 * Return complex locations of ends of dual edge <f,g>.
	 * If the edge between is red, we do a faux layout of g
	 * to get its incircle.
	 * @param edge EdgeSimple
	 * @return Complex[2], null on failure
	 */
	public Complex[] ends_dual_edge(EdgeSimple edge) {
		HalfEdge hedge=packDCEL.dualEdge_to_halfedge(edge);
		// incircle of f
		CircleSimple cSf= CommonMath.tri_incircle(
				packDCEL.getVertCenter(hedge),
				packDCEL.getVertCenter(hedge.next),
				packDCEL.getVertCenter(hedge.next.next),hes);
		// incircle of g
		CircleSimple cSg=null;
		if (hedge.myRedEdge!=null) {
			// set up tmp 'TriAspect' for g to compute incircle
			TriAspect tri_g=new TriAspect(hes);
			tri_g.setCircleData(0,packDCEL.getVertData(hedge.next));
			tri_g.setCircleData(1,packDCEL.getVertData(hedge));
			double rad=packDCEL.getVertRadius(hedge.twin.prev);
			tri_g.setRadius(rad,2);
			tri_g.setCircleData(2,tri_g.compOppCircle(0,false));
			cSg=tri_g.compIncircle();
		}
		else {
			cSg= CommonMath.tri_incircle(
					packDCEL.getVertCenter(hedge.twin),
					packDCEL.getVertCenter(hedge.twin.next),
					packDCEL.getVertCenter(hedge.twin.next.next),hes);
		}

		Complex []pts=new Complex[2];
		pts[0]=cSf.center;
		pts[1]=cSg.center;
		return pts;
	}

	/**
	 * return the fan of petal vertices from index j1 to and
	 * including j2. Null on error or if v is bdry and j1>=j2. 
	 * If v is interior, treat j1, j2 mod num, and if j1==j2,
	 * return full flower, but starting/ending with j1.
	 * 
	 * Note: added 2/2015 for conformal tiling 'feedback' code,
	 * but may be useful to improve other routines.
	 * 
	 * @param v
	 * @param j1
	 * @param j2
	 * @return int[], null on error.
	 */
	public int []flowerFan(int v,int j1,int j2) {
		int []fan=null;
		int[] flower=getFlower(v);
		try {
			int num=countFaces(v);
			if (isBdry(v)) { // bdry case
				if (j1>=j2)
					return null;
				fan=new int[j2-j1+1];
				for (int i=j1;i<=j2;i++)
					fan[i-j1]=flower[i];
				return fan;
			}
			
			// interior case
			j1=j1%num;
			j2=j2%num;
			int tick=(j2+num-j1)%num+1;
			if (j1==j2) // want full flower 
				tick=num+1;
			fan=new int[tick];
			for (int i=0;i<tick;i++) 
				fan[i]=flower[(j1+i)%num];
			
		} catch(Exception ex) {
			return null;
		}
		
		return fan;
	}

	/**
	 * Find index of vert v in face f.
	 * @param f int
	 * @param v int
	 * @return index or -1 if v not a vert of face f
	 */
	public int face_index(int f,int v) {
		return packDCEL.faces[f].getVertIndx(v);
	}
	
	/** 
	 * Return index of face on right side of oriented edge {v,w} 
	 * @param v int
	 * @param w int
	 * @return int index f or -1 if there is no such face 
	 */
	public int face_right_of_edge(int v,int w) {
		HalfEdge he=packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (he==null)
			return -1;
		return he.twin.face.faceIndx;
	}

	/**
	 * Given face with index 'f' has 'v' as a vertex, 
	 * find index of ngbh face 'g' opposite to 'v'. 
	 * @param f int, face index
	 * @param v int, vert index
	 * @return int, face index or -1 on error
	 */
	public int face_opposite(int f,int v) {
		combinatorics.komplex.DcelFace face =packDCEL.faces[f];
		return face.faceOpposite(v).faceIndx;
	}
	
	/** 
	 * Return face index f if {a,b,c} or {a,c,b} is a face
	 * @param a int
	 * @param b int
	 * @param c int
	 * @return int f, else return 0. 
	 */
	public int what_face(int a,int b,int c) {
		combinatorics.komplex.DcelFace fce=packDCEL.whatFace(a, b, c);
		if (fce==null)
			return 0;
		return fce.faceIndx;
	}
	
	/**
	 * Which bdry component is 'w' in? 
	 * @param w int
	 * @return int, bdryStarts index or -1 if 'w' not found
	 */
	public int whichBdryComp(int w) {
		if (w<1 || w>nodeCount || !isBdry(w))
			return -1;
		int hit=-1;
		for (int i=1;(i<=getBdryCompCount() && hit<0);i++) {
			int ws=bdryStarts[i];
			if (ws==w)
				hit=i;
			int ww=getFirstPetal(ws);
			while (ww!=ws && hit<0) {
				if (ww==w)
					hit=i;
				ww=getFirstPetal(ww);
			}
		}
		return hit;
	}
	
	/** 
	 * TODO: works for DCEL, but see 'Face.faceNghb' for
	 * new version. Need to see how the index return value 
	 * is used to see if there are other adjustment as well.
	 * 
	 * Check if faces f2 and f1 share an edge e. Return index 
	 * of begin vertex of e (relative to f1) in 'vert' data 
	 * of face f1 or return -1 if face f1 doesn't share edge 
	 * e with face f2.
	 * 
	 * @param f2 int
	 * @param f1 int, (NOTE the order of arguments!)
	 * @return -1 if f1 doesn't share edge with f2 or if f1==f2.
	*/
	public int face_nghb(int f2,int f1) {
	  int nj,mj,v1,v2;

	  if (f2<1 || f2 > faceCount || f1<1 || f1 > faceCount || f2==f1) 
	    return -1;
	  int[] fverts1=getFaceVerts(f1);
	  for (nj=0;nj<=2;nj++) {
	      v1=fverts1[nj];
	      v2=fverts1[(nj+1)%3];
		  int[] fverts2=getFaceVerts(f2);
	      for (mj=0;mj<=2;mj++)
	    	  if ( (v1==fverts2[mj]) && 
	    			  (v2==fverts2[(mj+2)%3]) )
	    		  return nj;
	  }
	  return -1;
	}

	/** 
	 * Return -1 if ordered edge {v,w} not in face f. Else, return 
	 * index of v in verts of f.
	 * @return int index or -1 on failure
	*/
	public int check_face(int f,int v,int w) {
		HalfEdge he=packDCEL.findHalfEdge(v,w);
		if (he==null || he.face.faceIndx!=f)
			return -1;
		
		int[] edges=getFaceVerts(f);
		for (int j=0;j<edges.length;j++) {
			if (edges[j]==v)
				return j;
		}
		return -1;
	}
	
	/**
	 * Return vert across designated edge from 'v'. 
	 * Edge is that from 'w' to next cclw petal of 'v'.
	 * Return 0 on failure, e.g., invalid data or
	 * bdry edge. 
	 * @param v int
	 * @param w int, petal
	 * @return vert index, 0 on failure
	 */
	public int getOppVert(int v,int w) {
		if (v<1 || v>nodeCount || w<1 || w>nodeCount)
			return 0;
		HalfEdge vw=packDCEL.findHalfEdge(v,w);
		HalfEdge desig=vw.next;
		if (vw==null || packDCEL.isBdryEdge(desig)) 
			return 0;
		return desig.twin.next.twin.origin.vertIndx;
	}
	
	/**
	 * Return face to left of edge v w if not ideal. 
	 * On failure, return ans[0]=0. Also return ans[1]=u, 
	 * the third vert of face. 
	 * @param int v
	 * @param int w
	 * @return int ans[2], ans[0]=face to left, ans[1]=third vertex.
	*/
	public int[] left_face(int v,int w) {
		EdgeSimple edge=new EdgeSimple(v,w);
		return left_face(edge);
	}
	
	/**
	 * Return face to left of edge <v w> if it's not an ideal
	 * face. On failure, return ans[0]=0. Also return ans[1]=u, 
	 * the third vert of face. 
	 * @param edge EdgeSimple
	 * @return ans[2], with ans[0]=face to left and ans[1]=third vertex.
	*/
	public int []left_face(EdgeSimple edge) {
	    int []ans=new int[2];
	    HalfEdge he=packDCEL.findHalfEdge(edge);
	    if (he==null || he.face==null || he.face.faceIndx<0) {
	    	ans[0]=0;
	    	return ans;
	    }
	    ans[0]=he.face.faceIndx;
	    ans[1]=he.next.next.origin.vertIndx;
   		return ans; 
	} 
	
	/**
	 * Find the tangency point between the circles of given edge.
	 * Actually, interpolate if circles are not quite tangent; should
	 * be a point on the geodesic between the centers. Return is in
	 * (theta,phi) form for spherical case.
	 * @param edge HalfEdge
	 * @return new Complex, null on error or if vertices are not nghbs
	 */
	public Complex tangencyPoint(HalfEdge edge) {
		if (edge==null)
			return null;
		Complex z1 = packDCEL.getVertCenter(edge);
		double r1 = packDCEL.getVertRadius(edge);
		Complex z2 = packDCEL.getVertCenter(edge.next);
		double r2 = packDCEL.getVertRadius(edge.next);
		Complex ctr = null;
		if (hes < 0)
			ctr = HyperbolicMath.hyp_tangency(z1,z2,r1,r2);
		else if (hes > 0)
			ctr = SphericalMath.sph_tangency(z1,z2,r1,r2);
		else
			ctr = EuclMath.eucl_tangency(z1,z2,r1,r2);
		return ctr;
	}
	
	/**
	 * If v is interior and hex, w is petal, return petal u opposite w. 
	 * If v bdry, 3 faces, w bdry, return petal on bdry opposite w.
	 * @param v int
	 * @param w int 
	 * @return int, 0 on failure. 
	*/
	public int hex_proj(int v,int w) {

	  if (v==w || (isBdry(v) && countFaces(v)!=3)
	      || (!isBdry(v) && countFaces(v)!=6))
	    return 0;
	  if (isBdry(v)) {
		  HalfEdge he=packDCEL.vertices[v].halfedge;
		  int u=he.twin.next.twin.origin.vertIndx;
		  if (he.twin.origin.vertIndx==w)
			  return u;
	      if (u==w)
	    	  return he.twin.origin.vertIndx;
	      return 0;
	    }
	  
	  // else interior hes
	  HalfEdge he=packDCEL.findHalfEdge(new EdgeSimple(v,w));
	  he=he.prev.twin; // rotate cclw 3
	  he=he.prev.twin;
	  he=he.prev.twin;
	  return he.twin.origin.vertIndx;
	}
	
	/**
	 * If v is interior and even degree, w is petal, return petal u 
	 * opposite w. If v bdry, return boundary most opposite to w. 
	 * If v==w, or v interior and odd degree, return 0.
	 * 
	 * TODO: replace with DCEL version; until then, use this
	 * @param v int
	 * @param w int 
	 * @return petal u, 0 on failure. 
	*/
	public int axis_proj(int v,int w) {
		if (v==w || (isBdry(v) && !isBdry(w))) // ?? don't understand this
			return 0;
		
		HalfEdge spoke=packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (spoke==null)
			return 0;
		HalfEdge oppspoke=Vertex.oppSpoke(spoke,false);
		return oppspoke.twin.origin.vertIndx;
	}
	
	/**
	 * Find an common edge opposite to both v and w.
	 * v will be to its left, w to its right.
	 * CAUTION: edge may not be unique.
	 * @param v int
	 * @param w int
	 * @return EdgeSimple, null on failure
	 */
	public EdgeSimple getCommonEdge(int v, int w) {
		HalfEdge he=RawManip.getCommonEdge(packDCEL, v, w);
		return HalfEdge.getEdgeSimple(he);
	}
		
	/** 
	 * Find index of common nghb u to v and w which 
	 * is on left of directed edge (v,w).
	 * @param int v, beginning vert
	 * @param int w, ending vert
	 * @return int, indx of u in flower of v, -1 if none exists
	*/
	public int find_common_left_nghb(int v,int w) {
		int i=nghb(v,w);
	    if (i<0 || i==countFaces(v))
	    	return -1;
	    if (i==(countFaces(v)-1) && !isBdry(v))
	    	return 0;
	    return i+1;
	}
	
	/**
	 * Return true if faces share one or more vertices (may be same face)
	 * @param f1 int
	 * @param f2 int
	 * @return boolean
	 */
	public boolean faces_incident(int f1,int f2) {
		int[] vert1=getFaceVerts(f1);
		int[] vert2=getFaceVerts(f2);
		for (int j=0;j<vert2.length;j++) {
			int v=vert2[j];
			for (int k=0;k<vert1.length;k++)
				if (vert1[k]==v)
					return true;
		}
		return false;
	}
	
	/** 
	 * Return count of edges in bdry component of v. 
	 * @param v int
	 * @return 0 if v not bdry or with combinatorial error. 
	 */
	public int bdry_comp_count(int v) {
	  int next,count=1,maxcount;

	  if (!isBdry(v)) 
		  return 0;
	  maxcount=nodeCount-intNodeCount;
	  next=v;
	  while ((next=getFirstPetal(next))!=v 
		 && count<= maxcount) 
		  count++;
	  if (count>maxcount) 
		  return 0;
	  return count;
	}

	/**
	 * Return edge for v,w, null if they're not neighbors
	 * @param v int
	 * @param w int
	 * @return EdgeSimple
	 */
	public EdgeSimple getEdge(int v,int w) {
		EdgeSimple edge=new EdgeSimple(v,w);
		if (packDCEL.findHalfEdge(edge)!=null) 
			return edge;
		return null;
	}

	/**
	 * traditional
	 * 
	 * OBE:Convenient combination call of 'complex_count(false)' and 
	 * 'facedraworder(false)'. One can always call them separately.
	 * @return 0 on problem
	 */
	public int setCombinatorics() throws DCELException {
		// TODO: eventually, just return; but for debugging, keep this.
		throw new DCELException("DCEL combinatorics are required.");
	}

	/**
	 * Return true if complex is simply connected: use
	 * 'genus'==0 and 'euler' either 1 or 2.
	 * @return boolean
	 */
	public boolean isSimplyConnected() {
		if (genus==0 && (euler==1 || euler==2))
			return true;
		else 
			return false;
	}
	
	/**
	 * traditional: 
	 * 
	 * Have to find where this is done in DCEL cases.
	 * This sets 'bdryFlag's, 'bdryCompCount', and 'bdryStarts[]'.
	 * (Formerly done in complex_count; separated to use 
	 * during constructions.)
	 * @return int, count of bdry edges
	 */
	public int setBdryFlags() {
		bdryStarts=new int[MAX_COMPONENTS];
		int bcount=0;
		int bs=0;
		for (int i=1;i<=nodeCount;i++) 
			setVertUtil(i,0);
		boolean debug=false;
		
		// debugging
		if (debug) { // debug=true;
			for (int i=1;i<=nodeCount;i++) {
				int[] flower=getFlower(i);
				System.err.print("vert "+i+": flower: ");
				for (int j=0;j<=countFaces(i);j++) 
					System.err.print(" "+flower[j]);
				System.err.print("\n");
			}
		}
		
		// each time we encounter a new bdry vert, process its whole component
		for (int i=1;i<=nodeCount;i++) {
			
			// debug
			if (debug) System.err.println("index i: "+i);
			
			if (getVertUtil(i)==0) {
				if (getFirstPetal(i)==getLastPetal(i)) {
					bcount++;
					bs++; // bdryStart counter
					if (bs>=MAX_COMPONENTS) {
						CirclePack.cpb.errMsg("More components ("+bs+") than allowed");
						return 0;
					}
					bdryStarts[bs]=i; // note: indexing starts with 1
					setBdryFlag(i,1);
					setVertUtil(i,i);
					int j=getFirstPetal(i);
					do {
						// TODO: I've disabled the exception because corner verts can have 
						//       just two neighbors; this may cause other problems
						if (getVertUtil(j)!=0 || getFirstPetal(j)==getLastPetal(j))
							CirclePack.cpb.errMsg("Caution: bdry vert "+j+" has only 2 neighbors");
//							throw new CombException("error tracing bdry for "+i);
						setBdryFlag(j,1);
						setVertUtil(j,i);
						bcount++;
						j=getFirstPetal(j);
					} while (j!=i && bcount<nodeCount);
					if (bcount>=nodeCount)
						CirclePack.cpb.errMsg("error tracing bdry with "+i);
				}
				else setBdryFlag(i,0);
			}
		}
		return bcount;
	}

	/**
	 * Return incircle of face f. Note: this is the incircle of the
	 * triangle formed by the centers of the circles, irrespective of
	 * whether the packing has non-trivial inv distances. 
	 * @param f int
	 * @return CircleSimple, null on error
	 */
	public CircleSimple faceIncircle(int f) {
		if (f<1 || f>faceCount) 
			return null;
		Complex []pts=corners_face(f);
		if (hes<0) { //
			int[] verts=packDCEL.faces[f].getVerts();
			CircleSimple sc1=HyperbolicMath.hyp_tang_incircle(
					pts[0],pts[1],pts[2],
					getRadius(verts[0]),
					getRadius(verts[1]),
					getRadius(verts[2]));
			
			// TODO: the new method, hyp_tri_incircle is not working
//			CircleSimple sc2=HyperbolicMath.hyp_tri_incircle(pts[0],pts[1],pts[2]);
//			return sc2;
			return sc1;
		}
		else if (hes>0) { // sph
			return SphericalMath.sph_tri_incircle(pts[0],pts[1],pts[2]);
		}
		else 
			return EuclMath.eucl_tri_incircle(pts[0],pts[1],pts[2]);
	}
	
	/** 
	 * Put current curvatures into aim. If flag, put neg aim in for 
	 * bdry, since they are considered free. 
	 * @param flag boolean, if true, set bdry negative
	 * @return int nodeCount
	*/
	public int set_aim_current(boolean flag) {
	    for (int i=1;i<=nodeCount;i++) {
	      setAim(i,getCurv(i));
	      if (flag && isBdry(i)) 
		  setAim(i,-1.0);
	    }
	    return nodeCount;
	}
	
	/**
	 * If 3D 'xyzpoint' data exists, set aims to the existing
	 * euclidean angles in the 3D faces.
	 * @param flag boolean: true --> set bdry aims to -(angle sum)
	 * @return int count
	 */
	public int set_aim_xyz(boolean flag) {
		int count=0;
		if (xyzpoint==null) 
			return count;
		for (int v=1;v<=nodeCount;v++) {
			double angsum=0.0;
			int[] flower=getFlower(v);
			for (int j=0;j<countFaces(v);j++) {
				int n=flower[j];
				int m=flower[j+1];
				angsum +=Math.acos(EuclMath.e_cos_3D(xyzpoint[v],xyzpoint[n],xyzpoint[m]));
			}
			if (flag && isBdry(v)) {
				setAim(v,-1.0*angsum);
			}
			else {
				setAim(v,angsum);
				count++;
			}
		}
		return count;
	}

	/** 
	 * Default radius is .5 in eucl/sph and x-radius (1-1/e) in hyp
	 */
	public void set_rad_default() {
       	double rad=0.5;
    	if (hes<0) rad=1.0-Math.exp(-1.0);
	    for (int i=1;i<=nodeCount;i++) 
	    	setRadius(i,rad);
	}

	/**
	 * Set default invDistances to 1.0 (i.e., tangency)
	 */
	public void set_invD_default() {
		for (int e=1;e<=packDCEL.edgeCount;e++) 
			packDCEL.edges[e].setInvDist(1.0);
	}
	
	/**
	 * Set aims at 2pi for all interior and -1 for all bdry vertices
	*/
	public void set_aim_default() {
	    for (int i=1;i<=nodeCount;i++) {
		      if (getBdryFlag(i)==1) 
		    	  setAim(i,-1.0);
		      else setAim(i,2.0*Math.PI);
		    }
	} 
	
	/**
	 * Adjust all interior aims toward 2pi by factor x>0. So if
	 * x is small, make only small adjustment toward 2pi.
	 * @param x double
	 */
	public void set_aim_x_flatten(double x) {
		if (x<.000001 || x>1.0)
			throw new ParserException("set_aim -t flag: value x "+x+" should be in (0,1]");
		for (int i=1;i<=nodeCount;i++) {
		      if (!isBdry(i))  {
		    	  double curv=2.0*Math.PI-getAim(i);
		    	  setAim(i,getAim(i)+x*curv);
		      }
		}
	}
	
	/**
	 * Given a list of vertices, set aims to 2pi for interior
	 * and -1 for bdry vertices.
	 * @param vlist NodeLink
	*/
	public void set_aim_default(NodeLink vlist) {
		if (vlist==null || vlist.size()==0)
			set_aim_default();
		Iterator<Integer> vit=vlist.iterator();
		while (vit.hasNext()) {
			int v=vit.next();
		    if (isBdry(v)) 
		    	setAim(v,-1.0);
		    else setAim(v,2.0*Math.PI);
		}
	} 
	
	/** 
	 * Reset 'plotFlags' to 1 (true)
	 */
	public void set_plotFlags() {
		for (int i=1;i<=nodeCount;i++) 
			setPlotFlag(i,1);
	}

	/** 
	 * Return inversive distance between circles for v and w,
	 * not necessarily neighbors. InvDist rho goes from -1 to infinity. 
	 * rho is in [-1,1] for overlap situation, then rho=cos(overlap angle); 
	 * rho=0 for orthogonal circles; rho=1 for tangency; rho>1 for
	 * separated circles, where invDist is cosh of the hyperbolic distance
	 * between the circles (bubbled into hyperbolic upper half space).
	 *  
	 * TODO: routines not very robust
	 * 
	 * @param v int
	 * @param w int
	 * @return double, 1.0 (default tangency) on error/problem
	 */
	public double comp_inv_dist(int v,int w)
	{
	  double erad1,erad2;
	  Complex ectr1,ectr2;
	  CircleSimple sc;

	  if (v<1 || w<1 || v>nodeCount || w>nodeCount) 
		  return 1.0; 
	  // note: tangency is default 
	  if (hes>0) { // spherical
		  double []xyz_v=SphericalMath.s_pt_to_vec(getCenter(v));
		  double []xyz_w=SphericalMath.s_pt_to_vec(getCenter(w));
		  // formula: (cos(r_v)*cos(r_w)-cos(dist(v,w))/(sin(r_v)*sin(r_w));
		  double I=1.0/(Math.tan(getRadius(v))*Math.tan(getRadius(w)))-
		  	(xyz_v[0]*xyz_w[0]+xyz_v[1]*xyz_w[1]+xyz_v[2]*xyz_w[2])/
		  	(Math.sin(getRadius(v))*Math.sin(getRadius(w)));
		  return I;
	  }
	  if (hes<0) { // hyperbolic: use euclidean data 
	      sc=HyperbolicMath.h_to_e_data(getCenter(v),getRadius(v));
	      ectr1=new Complex(sc.center);
	      erad1=sc.rad;
	      sc =HyperbolicMath.h_to_e_data(getCenter(w),getRadius(w));
	      ectr2=new Complex(sc.center);
	      erad2=sc.rad;
	  }
	  else
	  {
	      ectr1=getCenter(v);
	      erad1=getRadius(v);
	      ectr2=getCenter(w);
	      erad2=getRadius(w);
	  }
	  return (EuclMath.inv_dist(ectr1,ectr2,Math.abs(erad1),Math.abs(erad2)));
	}
	
	/** 
	 * Find overlap/inversive distance between euclidean spheres
	 * of given 3-space centers/radii. Return 1 (tangency) on error.
	 * @param xyz Point3D
	 * @param XYZ Point3D
	 * @param r double
	 * @param R double
	 * @return double 
	*/
	public double xyz_inv_dist(Point3D xyz,Point3D XYZ,double r,double R) {
	  if (r<0.0 || R<0.0) return 1.0;
	  double ab=(xyz.x-XYZ.x)*(xyz.x-XYZ.x)+(xyz.y-XYZ.y)*(xyz.y-XYZ.y)+
	    (xyz.z-XYZ.z)*(xyz.z-XYZ.z);
	  double rr=r*r;
	  double RR=R*R;
	  if (ab<rr || ab<RR) return 1.0;
	  return ((ab-rr-RR)/(2.0*r*R));
	}

	/**
	 * Fill in the angle sums (curvature) of the packing. Note that 
	 * angle sums at a vertex are computed face-by-face for faces
	 * containing that vertex.
	 * @return int 1
	*/
	public int fillcurves() {
		UtilPacket uP=new UtilPacket();

		uP.value=0.0;
		for (int v=1;v<=nodeCount;v++) {
			if (!CommonMath.get_anglesum(this,v,0.0,uP)) 
				throw new DataException("failed to compute angle sum for "+v);
			setCurv(v,uP.value);
	    }
		return 1;
	}

	/**
	 * Find anglesum at given vertex. Inversive distances not yet
	 * included.
	 * @param v, vert index
	 * @param r, spherical radius of center circle
	 * @param uP, UtilPacket, created by calling routine, 'value'
	 * holds the result. Return 0 if sum of any triple > Pi.
	 * @return true if computation is okay, else false
	*/
	public boolean s_anglesum(int v,double r,UtilPacket uP) {
	  int k,j2;
	  double r1,r2;

	  uP.value=0.0;
	  if (r<=0) 
		  return false;
	  j2=getFirstPetal(v);
	  r2=getRadius(j2);
	  for (k=1;k<=countFaces(v);k++) {
	      r1=r2;
	      r2=getRadius(getPetal(v,k));
	      uP.value += Math.acos(SphericalMath.s_comp_cos(r,r1,r2));
	  }
	  return true;
	} 

	/**
	 * Compute anglesum in hyperbolic geometry. Result in uP.value. 
	 * @param v int, vertex
	 * @param x double, x-rad
	 * @param uP UtilPacket, created by calling routine to get results
	 * @return boolean, true if seems okay
 	 */
	public boolean h_anglesum_overlap(int v,double x,UtilPacket uP) {
	    double x1;
	    uP.value=0.0;
	    if (x<=0) { // infinite radius at vertex of interest 
	    	uP.value=x;
	        return true;
	    } 
	    int j2=getLastPetal(v);
	    double x2=getRadius(j2);
	    if (!haveInvDistances()) {
	        for (int k=1;k<=countFaces(v);k++) {
	        	x1=x2;
	        	x2=getRadius(getPetal(v,k));
	        	uP.value += Math.acos(HyperbolicMath.h_comp_x_cos(x,x1,x2));
	        }
	    }
	    else {
	        double o2=getInvDist(v,getFirstPetal(v));
	        for (int k=1;k<=countFaces(v);k++) {
	        	x1=x2;
	        	double o1=o2;
	        	int j1=j2;
	        	j2=getPetal(v,k);
	        	x2=getRadius(j2);
	        	o2=getInvDist(v,getPetal(v,k));
	        	double ivd=getInvDist(j1,j2);
	        	double cosang=HyperbolicMath.h_comp_cos(x, x1, x2,ivd,o2,o1);
	        	uP.value += Math.acos(cosang);
	        }
	    }
	    return true;
	} 
	
	/** 
	 * Computes radius at vert v that gives anglesum closer to aim; use 
	 * secant method, r = first guess, N is a limit on the number of iterations.
	 * New radius will not increase or decrease beyond what 'factor' allows. 
	 * (Thus, may need repeated calls to this routine.)
	 * Return true if everything seems okay. Calling routine responsible for
	 * actually changing the radius of v, given uP.value.
	 * @param v int
	 * @param r double, guess
	 * @param aim double
	 * @param N int, limit on iterations
	 * @param uP UtilPacket, instantiated by calling routine
	 * @return boolean 
	 * 
	*/
	public boolean h_radcalc(int v,double r,double aim,int N,UtilPacket uP) {
	  double bestcurv,lower=0.5,upper=0.5,upcurv,lowcurv,factor=0.5;
	  UtilPacket curveUp=new UtilPacket();

	  if (!h_anglesum_overlap(v,r,curveUp)) 
		  return false;
	  bestcurv=lowcurv=upcurv=curveUp.value;
	  
	  // may hit upper/lower bounds on radius change
	  if (bestcurv>(aim+OKERR)) {
	      lower=1.0-factor+r*factor; // interpolate
	      if (!h_anglesum_overlap(v,lower,curveUp)) 
	    	  return false;
	      lowcurv=curveUp.value;
	      if (lowcurv>aim) {
		  uP.value=lower;
		  return true;
	      }
	  }
	  else if (bestcurv<(aim-OKERR)) {
	      upper=r*factor;
	      if (!h_anglesum_overlap(v,upper,curveUp)) 
	    	  return false;
	      upcurv=curveUp.value;
	      if (upcurv<aim) {
		  uP.value=upper;
		  return true;
	      }
	  }
	  else {
	      uP.value=r;
	      return true;
	  }

	  for (int n=1;n<=N;n++) {
	      if (bestcurv>(aim+OKERR)) {
		  upper=r;
		  upcurv=bestcurv;
		  r -= (bestcurv-aim)*(lower-r)/(lowcurv-bestcurv);
	      }
	      else if (bestcurv<(aim-OKERR)) {
	    	  lower=r;
	    	  lowcurv=bestcurv;
	    	  r += (aim-bestcurv)*(upper-r)/(upcurv-bestcurv);
	      }
	      else {
	    	  uP.value= r;
	    	  return true;
	      }
	      if (!h_anglesum_overlap(v,r,curveUp)) 
	    	  return false;
	      bestcurv=curveUp.value;
	    }
	  uP.value=r;
	  return true;
	}
	
	/** 
	 * Computes radius at vert v that gives anglesum closer to aim; use 
	 * secant method, r = first guess, N is a limit on the number of iterations.
	 * New radius is returned in uP.value, but will not increase or decrease
	 * beyond what 'factor' allows. (Thus, may need repeated calls to this 
	 * routine.)
	 * Return true if everything seems okay. Calling routine responsible for
	 * actually changing the radius of v.
	 * @param v int, vertex
	 * @param r double, current radius
	 * @param aim double, target angle sum
	 * @param N int, limit on iterations
	 * @param uP UtilPacket, created by calling routine to get info
	 * @return boolean, 
	*/
	public boolean e_radcalc(int v,double r,double aim,int N,UtilPacket uP) {
		double lower=0.5;
		double upper=0.5;
		double factor=0.5;
		double upcurv;
		double lowcurv;
	  
	    Vertex vert=packDCEL.vertices[v];
	    double angsum=packDCEL.getVertAngSum(vert);
	    double bestcurv=lowcurv=upcurv=angsum;
	    if (bestcurv>(aim+OKERR)) {
	      upper=r/factor;
	      angsum=packDCEL.getVertAngSum(vert,upper);
	      upcurv=angsum;
	      if (upcurv>aim) {
	    	  uP.value=upper;
	    	  return true;
	      }
	    }
	    else if (bestcurv<(aim-OKERR)) {
	      lower=r*factor;
	      angsum=packDCEL.getVertAngSum(vert,lower);
	      upcurv=angsum;
	      if (lowcurv<aim) {
	    	  uP.value=lower;
	    	  return true;
	      }
	    }
	    else {
	    	uP.value=r;
	    	return true;
	    }

	    for (int n=1;n<=N;n++) {
	      if (bestcurv>(aim+OKERR)) {
	    	  lower=r;
	    	  lowcurv=bestcurv;
	    	  r += (aim-bestcurv)*(upper-r)/(upcurv-bestcurv);
	      }
	      else if (bestcurv<(aim-OKERR)) {
	    	  upper=r;
	    	  upcurv=bestcurv;
	    	  r -= (bestcurv-aim)*(lower-r)/(lowcurv-bestcurv);
	      }
	      else {
	    	  uP.value= r;
	    	  return true;
	      }
	      angsum=packDCEL.getVertAngSum(vert,r);
	      bestcurv=r;
	    }
	    uP.value=r;
	    return true;
	}
	
	/**
	  * Compute and store hyp radius of given vertex based on 'aim'. 
	  * Currently using 20 naive iterations.
	  * @param v int
	  * @param int, 0 on error
	  */
	public int h_riffle_vert(int v) throws PackingException {
		return h_riffle_vert(v,getAim(v));
	}
	
	/**
	  * Compute and store hyp radius of given vertex to achieve given 'aim'
	  * Currently using 20 naive iterations.
	  * @param v int
	  * @param aim double
	  * @param int, 0 on error
	  */
	public int h_riffle_vert(int v,double aim) throws PackingException {
		int n=0;
		UtilPacket uP=new UtilPacket();

		double r=getRadius(v);
		if (!h_anglesum_overlap(v,r,uP)) 
			return 0;
	    double curv=uP.value;
	    double diff=curv-aim;
	    while (n<20 && (diff>OKERR || diff<(-OKERR)) ) {
	    	if (!h_radcalc(v,r,aim,5,uP)) 
	    		return 0; // something went wrong
	    	r=uP.value;
	    	if (!h_anglesum_overlap(v,r,uP)) 
	    		return 0;
	    	curv=uP.value;
	    	diff=curv-aim;
	    	n++;
	    }
	    if (n>0 && r!=getRadius(v)) { // changed?
	    	setRadiusActual(v,r);
	    	setCurv(v,curv);
	    }  
	    return 1; // seemed to go okay
	  } 

	/**
	  * Compute and store eucl radius of given vertex to get 'aim'. 
	  * Currently using 20 naive iterations.
	  * @param v int
	  * @param int, 0 on error
	  */
	public int e_riffle_vert(int v) throws PackingException {
		return e_riffle_vert(v,getAim(v));
	}
	
	/**
	  * Compute and store eucl radius of given vertex to achieve 
	  * given 'aim'. Currently using 20 naive iterations.
	  * @param v int
	  * @param aim double
	  * @param int, 0 on error
	  */
	public int e_riffle_vert(int v,double aim) throws PackingException {
		Vertex vert=packDCEL.vertices[v];
		int n=0;
		UtilPacket uP=new UtilPacket();

		double orig_r=packDCEL.getVertRadius(vert.halfedge);
	    double curv=packDCEL.getVertAngSum(vert,orig_r);
	    double diff=curv-aim;
		double r=orig_r;
	    while (n<20 && (diff>OKERR || diff<(-OKERR)) ) {
	    	if (!e_radcalc(v,r,aim,5,uP)) 
	    		return 0; // something went wrong
	    	r=uP.value;
	    	curv=packDCEL.getVertAngSum(vert,r);
	    	diff=curv-aim;
	    	n++;
	    }
	    
	    if (n>0 && r!=orig_r) { // changed?
	    	packDCEL.setVertRadii(v,r);
	    	setCurv(v,curv);
	    }  
	    return 1; // seemed to go okay
	  } 

	/**
	 * write SVG image of circles.
	 * TODO: add more types of objects in the future, e.g., edges
	 * @param file BufferedWriter
	 * @param flagSegs
	 * @return int, count
	 */
	public int writeSVG(BufferedWriter file,Vector<Vector<String>> flagSegs) {
		int count=0;
		Vector<String> items=null;
		if (flagSegs!=null && flagSegs.size()>0)
			items=flagSegs.get(0);
		
		// TODO: more sophistication later; for now, assume all circles
		boolean circles=true;
		
		// 'sub_cmd should have options about color, fill, thickness
		String sub_cmd=null;
		if (items!=null && StringUtil.isFlag(items.get(0))) {
			sub_cmd = (String) items.remove(0);
			if (sub_cmd.length()<3)
				sub_cmd=null;
			else 
				sub_cmd = sub_cmd.substring(2); // remove '-' and first char
		}
		
		DispFlags circFlags=new DispFlags(sub_cmd);
		NodeLink vlist=new NodeLink(this,"a");
		if (items!=null && circles) {
			vlist=new NodeLink(this,items);
		}
		
		double minx,miny,maxx,maxy;
		Iterator<Integer> vlst=vlist.iterator();
		int v=vlst.next();
		Complex vz=getCenter(v);
		double vr=getRadius(v);
		minx=vz.x-vr;
		maxx=vz.x+vr;
		miny=vz.y-vr;
		maxy=vz.y+vr;
		while (vlst.hasNext()) {
			v=vlst.next();
			double r=getRadius(v);
			double m=getCenter(v).x-r;
			if (m<minx) minx=m;
			m +=2*r;
			if (m>maxx) maxx=m;
			m=getCenter(v).y-r;
			if (m<miny) miny=m;
			m +=2*r;
			if (m>maxy) maxy=m;
		}
		
		double sz=1.1*(maxy-miny);
		double wdt=1.1*(maxx-minx);
		if (wdt>sz)
			sz=wdt;
		double thick=1;
		if (circFlags.thickness>0)
			thick=circFlags.thickness;

		try {
			file.write("<!DOCTYPE html>\n<html>\n<body>\n");
			file.write("<svg height=\""+sz+"\" width=\""+sz+"\">\n");
			
			vlst=vlist.iterator();
			while (vlst.hasNext()) {
				v=vlst.next();
				file.write("<circle cx=\""+(getCenter(v).x-minx+.05*sz)+"\" cy=\""+
				(maxy-getCenter(v).y+.05*sz)+"\" r=\""+getRadius(v)+
				"\" stroke=\"black\" stroke-width=\""+.5*thick+"\" fill=\"white\"/>\n");
				count++;
			}
			file.write("</svg>\n</body>\n</html>\n");
			file.close();
		}catch(Exception ex) {}

		return count;
	}

	/**
	 * Return side pairing mobius with given label (should be
	 * just one letter, caps converted to two lower case.
	 * @param moniker String
	 * @return Mobius, null on error
	 */
	public Mobius namedSidePair(String moniker) {
		// side pairings indicated with just one letter
		String label= moniker.trim();
		if (label.length()!=1) 
			throw new ParserException("side-pair monikers just one character");
		String tmpLabel=new String(label);
		// Upper case (e.g. 'A') must be changed to double lower ('aa').
		char h=tmpLabel.charAt(0);
		if (h>='A' && h<='Z') {
			h=(char)('a'+h-'A');
			tmpLabel=new String(String.valueOf(h)+String.valueOf(h));
		}
		
		if (packDCEL.pairLink==null || packDCEL.pairLink.size()<2)
			return null;
		Iterator<SideData> sides=packDCEL.pairLink.iterator();
		sides.next(); // first is null
		SideData ep=null;
		while (sides.hasNext()) {
			ep=sides.next();
			if (ep.label.equals(tmpLabel) && ep.mob!=null)
				return ep.mob;
		}
		return null;
	}
	
	/**
	 * Call for appropriate 'repacking' procedure. This creates
	 * tmp 'RePacker' and applies methods depending on geometry,
	 * topology, bdry/overlap conditions, C library availability,
	 * and so forth. Uses HeavyC methods, if available.
	 * NOTE: for 'oldReliable' riffle method, call with arguments
	 * Catches 'PackingException's.
	 * @param passes int, max repack cycles
	 * @return int, 0 on error. (may reflect interations completed)
	 */
	public int repack_call(int passes) {
		return repack_call(passes,false,true);
	}

	/**
	 * Call for appropriate 'repacking' procedure. 
	 * This creates tmp 'RePacker' and applies 
	 * methods depending on geometry, topology, 
	 * bdry/overlap conditions, C library availability, 
	 * and so forth. Can disallow Orick's methods 
	 * in GOpacker. Catches 'PackingException's.
	 * @param passes int, max repack cycles
	 * @param oldRel boolean; true, use old reliable method
	 * @param useC boolean; true, OK to use Orick's method.
	 * @return int, -1 on error. (may reflect iterations completed)
	 */
	public int repack_call(int passes, boolean oldRel, boolean useC) {
		int count = 0;

		try {
			if (hes < 0) { // hyp
				HypPacker h_packer=new HypPacker(this,passes);
				oldRel=oldRel || h_packer.oldReliable;
				int ans=0;
				if (!oldRel) {
					ans=h_packer.genericRePack(passes);
				}
				else
					ans=h_packer.d_oldReliable(1000); 
				if (ans>0) {
					h_packer.reapResults();
					fillcurves();
					return ans;
				}
				else if (ans<0)
					throw new PackingException("dcel hyp repack failure");
			}
			else if (hes>0) { // sph
				CirclePack.cpb.errMsg("DCEL packing routines don't (yet) handle sph geom.");
				return 0;
			}
			else  { // eucl
				EuclPacker e_packer=new EuclPacker(this,passes);
				oldRel=oldRel || e_packer.oldReliable;
				int ans=0;
				if (!oldRel) {
					ans=e_packer.genericRePack(passes);
				}
				else
					ans=e_packer.d_oldReliable(passes); // TODO: specify in call
				if (ans>0) {
					e_packer.reapResults();
					return ans;
				}
				else if (ans<0)
					throw new PackingException("dcel eucl repack failure");
				return 1;
			}
		} catch (Exception pex) {
			CirclePack.cpb.errMsg(pex.getMessage());
			return -1;
		}
		return count;
	}
	  
	/** 
	 * Convert this packing centers/radii to eucl, set geometry
	 * to euclidean. In spherical case, some discs may be outside
	 * their circles.
	 * Note: does NOT adjust 'CPDrawing' geometry.
	 */ 
	public int geom_to_e() {
		if (hes == 0)
			return 1; // eucl already
		int oldhes=hes;
		hes = 0; // change so new data is checked in approp geometry 
		CircleSimple sc;

		if (oldhes < 0) { // hyp
			for (int v = 1; v <= nodeCount; v++) {
				sc = HyperbolicMath.h_to_e_data(getCenter(v), getRadius(v));
				setCenter(v,sc.center);
				setRadius(v,sc.rad);
			}
		} 
		else { // sph
			// Note: sc.flag==-1 means disc is outside of circle
			for (int v = 1; v <= nodeCount; v++) {
				sc = SphericalMath.s_to_e_data(getCenter(v), getRadius(v));
				setCenter(v,sc.center);
				if (sc.flag==-1) 
					sc.rad *=-1.0; // this may not signal what we want
				setRadius(v,sc.rad);
			}
		}
		return 1;
	}

	/** 
	 * Converts centers/radii to euclidean first, then 
	 * scales packing to live in the unit disc and converts
	 * to hyp center and s-radii. Sets hes=-1.
	 * Note: does NOT adjust 'CPDrawing' geometry. 
	 * @return 1 on success (or if already hyp).
	 */
	public int geom_to_h() {
		if (hes<0)
			return 1; // hyp already
		if (hes == 0) { // eucl
			double mx = packDCEL.getVertCenter(packDCEL.alpha).abs()+
					packDCEL.getVertRadius(packDCEL.alpha);
			for (int v = 1; v <= nodeCount; v++) { // translate, set scale
													// factor
			// System.out.println("vert v"+v);
			// rData[v].center=rData[v].center.minus(cent);
				double m = getCenter(v).abs();
				if ((m + getRadius(v)) > mx)
					mx = m + getRadius(v);
			}
			if (mx > .999999999)
				mx *= 1.000005; // so roundoff doesn't push a circle outside
								// disc.
			else
				mx = 1.0;
			for (int v = 1; v <= nodeCount; v++) {
				setCenter(v,getCenter(v).divide(mx));
				setRadius(v,getRadius(v)/mx);
				CircleSimple sc = HyperbolicMath.e_to_h_data(getCenter(v),getRadius(v));
				setCenter(v,sc.center);
				setRadius(v,sc.rad);
			}
			hes=-1;
			return 1;
		} 
		// else pass through euclidean
		geom_to_e();
		return geom_to_h();
	}

	/** 
	 * Converts packing to spherical, with alpha 
	 * vertex at north pole. (Note: our stereographic 
	 * projection puts 0 at the NORTH pole.)
	 * Note: does NOT adjust 'CPDrawing' geometry.
	 * @return 1 
	 */
	public int geom_to_s() {
		if (hes > 0)
			return 1; // sph already
		int oldhes=hes;
		hes=1;
		CircleSimple sc;

		if (oldhes < 0) { // from hyp
			for (int v = 1; v <= nodeCount; v++) {
				sc = HyperbolicMath.h_to_e_data(getCenter(v),getRadius(v));
				sc = SphericalMath.e_to_s_data(sc.center, sc.rad);
				setCenter(v,sc.center);
				setRadius(v,sc.rad);
			}
		} 
		else { // from eucl
			for (int v = 1; v <= nodeCount; v++) {
				sc = SphericalMath.e_to_s_data(getCenter(v),getRadius(v));
				setCenter(v,new Complex(sc.center));
				setRadius(v,sc.rad);
			}
		}
		return 1;
	}

	/**
	 * v1 must be boundary vertex; enfold links nghbs v2 
	 * (cclw) to v3 (clw), making v1 interior. Local data 
	 * is reset, but calling routine must update the packing.
	 * @param v1 int
	 * @return 1, 0 on error
	 */
	public int enfold(int v1) {
		if (!isBdry(v1))
			return 0;
		
		if (getRadius(v1) <= 0) // avoid infinity hyp rad
			setRadius(v1, 0.1);
		int ans=RawManip.enfold_raw(packDCEL,v1);
		if (ans<=0)
			throw new CombException("dcel enfold failed in 'enfold'");
		packDCEL.fixDCEL(this);
		return 1;
	}

	/** 
	 * Interchange two legal vertex numbers (if tiling info 
	 * exists, it is adjusted). 
	 * @param v int
	 * @param w int
	 * @return 1, 0 on error
	 */
	public int swap_nodes(int v, int w) {
		int rslt = packDCEL.swapNodes(v, w);
		if (rslt==0)
			return 0;
		
		// fix up if there is TileData
		if (tileData != null && tileData.tileCount > 0) {
			for (int j = 1; j <= tileData.tileCount; j++) {
				Tile t = tileData.myTiles[j];
				if (t == null)
					continue;
				if (t.baryVert == w)
					t.baryVert = v;
				else if (t.baryVert == v)
					t.baryVert = w;
				for (int k = 0; k < t.vertCount; k++) {
					if (t.vert[k] == w)
						t.vert[k] = v;
					else if (t.vert[k] == v)
						t.vert[k] = w;
				}
			}
		}
		return rslt;
	}

	/**
	 * Swap node numbers, but with bit options for info that will 
	 * be kept (meaning, it is swapped along with the numbers): 
	 * @param v int
	 * @param w int
	 * @param keepFlags int: bits: 1=color,2=mark,4=aim
	 * @return int
	 */
	public int swap_nodes(int v, int w, int keepFlags) {

		// do the combinatorics of swap
		int ans = packDCEL.swapNodes(v, w);
		if (ans == 0)
			return 0;
		
		// Note: for DCEL,color/aim/mark are automatically swapped,
		//    so 'keepFlags' actions will actually swap them back.
		if ((keepFlags & 0001) == 0001) { // swap 'color'
			Color holdcolor = getCircleColor(v);
			Color col = getCircleColor(w);
			setCircleColor(v,ColorUtil.cloneMe(col));
			setCircleColor(w,ColorUtil.cloneMe(holdcolor));
		}
		if ((keepFlags & 0002) == 0002) { // swap 'mark'
			int holdmark = getVertMark(v);
			setVertMark(v,getVertMark(w));
			setVertMark(w,holdmark);
		}
		if ((keepFlags & 0004) == 0004) { // swap 'aim'
			double holdaim = getAim(v);
			setAim(v,getAim(w));
			setAim(w,holdaim);
		}
		return ans;
	}

	/**
	 * Return index of circle shared by faces f1 and f2, or -1
	 * if they don't share a vertex.
	 * @param f1 int
	 * @param f2 int
	 * @return int, index or -1
	 */
	public int face_vert_share(int f1, int f2) {
		int[] v1 = getFaceVerts(f1); 
		int[] v2 = getFaceVerts(f2);
		for (int j = 0; j < 3; j++) {
			if (v1[j] == v2[0])
				return v2[0];
			if (v1[j] == v2[1])
				return v2[1];
			if (v1[j] == v2[2])
				return v2[2];
		}
		return -1;
	}

	  /**
	   * Intended (not actual) edge length from v to w, using invDist
	   * if that is set. This is not well defined in the sphere.
	   * @param he HalfEdge
	   * @return double, -1 on error (e.g., if geometry is spherical)
	   */
	  public double intendedEdgeLength(HalfEdge he) {
		  if (hes>0) { // spherical: not-well defined
			  return -1;
		  }
		  double rv=packDCEL.getVertRadius(he);
		  double rw=packDCEL.getVertRadius(he.next);
		  double t=he.getInvDist();
		  if (hes<0) { // hyperbolic
			  return HyperbolicMath.h_ivd_length(rv,rw,t);
		  }
		  return EuclMath.e_ivd_length(rv,rw,t);
	  }

	  /**
	   * Intended (not actual) edge length from v to w, using invDist
	   * if that is set. This is not well defined in the sphere.
	   * @param v int
	   * @param w int
	   * @return double, -1 on error (e.g., if geometry is spherical)
	   */
	  public double intendedEdgeLength(int v,int w) {
		  if (hes>0) { // spherical: not-well defined
			  return -1;
		  }
		  double rv=getRadius(v);
		  double rw=getRadius(w);
		  double t=getInvDist(v,w);
		  if (hes<0) { // hyperbolic
			  return HyperbolicMath.h_ivd_length(rv,rw,t);
		  }
		  return EuclMath.e_ivd_length(rv,rw,t);
	  }

	  /**
	   * Distance between centers (actual edge length). Compare
	   * to 'intendedEdgeLength'
	   * @param hedge HalfEdge
	   * @return double
	   */
	  public double edgeLength(HalfEdge hedge) {
		  Complex zv=packDCEL.getVertCenter(hedge);
		  Complex zw=packDCEL.getVertCenter(hedge.next);
		  if (hes>0) { // spherical: not-well defined
			  return SphericalMath.s_dist(zv, zw);
		  }
		  if (hes<0) { // hyperbolic
			  return HyperbolicMath.h_dist(zv,zw);
		  }
		  return zv.minus(zw).abs();
	  }
	  
	  /**
	   * OBE: Distance between centers (actual edge length). Compare
	   * to 'intendedEdgeLength'
	   * @param v int
	   * @param w int
	   * @return double
	   */
	  public double edgeLength(int v,int w) {
		  Complex zv=getCenter(v);
		  Complex zw=getCenter(w);
		  if (hes>0) { // spherical: not-well defined
			  return SphericalMath.s_dist(zv, zw);
		  }
		  if (hes<0) { // hyperbolic
			  return HyperbolicMath.h_dist(zv,zw);
		  }
		  return zv.minus(zw).abs();
	  }
	  
	  /**
	   * Update curvatures, then finds l2 error, sqrt(sum[(aim-curv)^2]),
	   * for circles with aim > 0.0.
	   * @return double, l^2 error
	   */
	  public double angSumError() {
		  fillcurves();
		  double err=0.0;
		  for (int v=1;v<=nodeCount;v++) {
			  if (getAim(v)>0.0) {
				  double diff=Math.abs(getAim(v)-getCurv(v));
				  err += diff*diff;
			  }
		  }
		  return Math.sqrt(err);
	  }
	  
	  /**
	   * Compute the sum of areas of all faces, based on radii and
	   * inversive distances.
	   * @return double
	   */
	  public double carrierArea() {
		  double accum=0.0;
		  for (int f=1;f<=faceCount;f++) {
			  accum+=faceArea(f);
		  }
		  return accum;
	  }

	  /**
	   * Find total absolute err (sum |aim-curv|) for vertices
	   * with aim>=0, and the average (for these same vertices).
	   * @return, double[0]=total abs error; double[1]=average
	   */
	  public double []packCurvError() {
		  double []ans=new double[2];
		  fillcurves();
		  int count=0;
		  for (int v=1;v<=nodeCount;v++) {
			  if (getAim(v)>=0.0) {
				  count++;
				  ans[0] += Math.abs(getAim(v)-getCurv(v));
			  }
		  }
		  if (count>0) 
			  ans[1]=ans[0]/count;
		  return ans;
	  }
	  
	  /**
	   * Compute face area based on radii and overlaps (except
	   * overlaps not accounted for in spherical case). (Tacit
	   * assumption is 'num'=3.)
	   * @param f int
	   * @return double
	   */
	  public double faceArea(int f) {
		  RadIvdPacket rip=getRIpacket(f);
		  if (hes<0)  // hyperbolic
			  return HyperbolicMath.h_area(rip);
		  if (hes>0) // spherical
			  return SphericalMath.s_face_area(rip);
		  return EuclMath.eArea(rip);
	  }
	  
	  /**
	   * Get normal bouquet of petal vertices, closed
	   * for interior vertex.
	   * @return int[][]
	   */
	  public int[][] getBouquet() {
		  int bouq[][]=new int[nodeCount+1][];
		  for (int v=1;v<=nodeCount;v++) {
			  int[] flower=getFlower(v);
			  bouq[v]=new int[flower.length];
			  for (int j=0;j<flower.length;j++)
				  bouq[v][j]=flower[j];
		  }
		  return bouq;
	  }

	  /**
	   * This is more general version of 'hex_slide' mechanism.
	   * Perform an edge flip on the edge to the right of each 
	   * halfedge in 'hlink', i.e., flip twin.next. The effect
	   * when 'hlink' is closed half-hex loop is to cut the
	   * packing along hlink and slide the right side one notch
	   * down before reattaching. Continue flipping as long as 
	   * flips are successful, then fix the combinatorics. 
	   * @param hlink HalfLink
	   * @return int count, -count if there was a failure
	  */
	  public int right_slide(HalfLink hlink) {
		  int count=0;
		  Iterator<HalfEdge> his=hlink.iterator();
		  while (his.hasNext()) {
			  HalfEdge he=his.next().twin.prev;
			  if (RawManip.flipEdge_raw(packDCEL,he)==null) {
				  count *=-1;
				  break;
			  }
			  count++;
		  }
		  if (count!=0)
			  packDCEL.fixDCEL(this);
		  return count;
	  }
	  
	  /**
	   * Flip edges from a prepared list
	   * @param fliplist EdgeLink
	   * @return int, count, 0 on error
	   */
	  public int flipList(EdgeLink fliplist) {
		  if (fliplist==null || fliplist.size()==0) {
			  return 0;
		  }
		  int count=0;
		  PackDCEL pdc=packDCEL;
		  Iterator<EdgeSimple> fis=fliplist.iterator();
		  while (fis.hasNext()) {
			  HalfEdge he=pdc.findHalfEdge(fis.next());
			  HalfEdge newhe=RawManip.flipEdge_raw(pdc,he);
			  if (newhe!=null)
				  count ++;
		  }
		  if (count>0) 
			  pdc.fixDCEL(this);
		  return count;
	  }
	  
	  /**
	   * Check flipability of edge clw from <v,w>
	   * @param v int
	   * @param w int
	   * @return boolean
	   */
	  public boolean clwFlipable(int v,int w) {
		  EdgeSimple clwedge=clwEdge(v,w);
		  if (clwedge==null)
			  return false;
		  return flipable(clwedge.v,clwedge.w);
	  }
	  
	  /** 
	   * Determine if edge {v,w} is "flipable" (as in Whitehead move).
	   * Situations that fail: 
	   * * if flip would result in vert of degree 2 
	   * * if the new edge would connect two verts already neighbors 
	   *   (can happen, e.g., when one neighbor has degree three); 
	   * * new edge will have both ends on boundary, could disconnect
	   *   interior or isolate a bdry vert.
	   * @param v int
	   * @param w int
	   * @return boolean
	   */
	  public boolean flipable(int v,int w) {
		  HalfEdge he=packDCEL.findHalfEdge(new EdgeSimple(v, w));
		  try {
			  if (he==null
				  || (isBdry(v) && (getFirstPetal(v)==w || countFaces(v)<=2))
				  || (isBdry(w) && (getFirstPetal(w)==v || countFaces(w)<=2))
				  || (!isBdry(v) && countFaces(v)<=3) 
				  || (!isBdry(w) && countFaces(w)<=3)) 
				  throw new DataException("deg <= 3 or bdry edge");
		  } catch(Exception dex) {
			  CirclePack.cpb.errMsg(dex.getMessage());
			  return false;
		  }
		  return true;
	  }
	  
	  /**
	   * Report the log of ratio width/height of a eucl packing, 
	   * given the four corner vertices in counterclockwise order, 
	   * upper-left first. Throw DataExceptin on error or if off 
	   * from rectangular by more than 5%. The result is 
	   * left-right length divided by top-bottom height.
	   * @param ul int
	   * @param ll int
	   * @param lr int
	   * @param ur int
	   * @return double
	   */
	  public double rect_ratio(int ul,int ll,int lr,int ur) {
		  if (hes!=0) 
			  throw new DataException("packing must be euclidean");
	    // note: first corner is assumed to be upper-left, rest ctrclkwise.
	    double leng=getCenter(lr).minus(getCenter(ll)).abs();
	    double high=getCenter(ur).minus(getCenter(lr)).abs();
	    double leng2=getCenter(ur).minus(getCenter(ul)).abs();
	    double high2=getCenter(ul).minus(getCenter(ll)).abs();
	    // check for inconsistency with rectangle 
	    if (leng<OKERR || leng2<OKERR || high<OKERR || high2<OKERR
	        || Math.abs(leng-leng2)/leng>.05 || Math.abs(high-high2)/high>.05)
	      throw new DataException("verts "+ul+" "+ll+" "+lr+" "+ur+" do not " +
	      		"appear to form rectangle: (aspect = "+
	      		String.format("%.6e",Math.log(leng/high))+")");
	    return Math.log(leng/high);
	  }
	  
	  /** 
	   * Determine generation of vertices starting from given seeds.
	   * If 'mark' is true, store in 'Vertex.mark', else just 
	   * return the index of the last vertex. 'utilFlag' 
	   * is used to pass seed info to 'label_generations'.
	   * @param mx int, if mx>0, stop at generation mx.
	   * @param seedlist NodeLink, list defined as first generation (1)
	   * @param mark boolean, if true, store as 'mark in 'Vertex'.
	   * @return int, index of last_vert (first encountered in max 
	   *   generation), 0 on error
	   */
	public int gen_mark(NodeLink seedlist, int mx, boolean mark) {
		int[] list;

		for (int i = 1; i <= nodeCount; i++)
			setVertUtil(i,0);

		Iterator<Integer> slist = seedlist.iterator();
		while (slist.hasNext()) {
			int v = (Integer) slist.next();
			setVertUtil(v,1);
		}

		UtilPacket uP = new UtilPacket();
		if ((list = packDCEL.label_generations(mx, uP)) == null)
			return 0;
		if (mark)
			for (int i = 1; i <= nodeCount; i++)
				setVertMark(i,list[i]);
		return (int) uP.rtnFlag; // holds last_vert
	}

	  /**
	   * Return combinatorical antipodal vertex to v.
	   * @param v int
	   * @return int, 0 on error
	   */
	  public int antipodal_vert(int v) {
		  NodeLink vts=new NodeLink(this,v);
		  NodeLink ans=antipodal_verts(vts,2);
		  if (ans==null || ans.size()==0)
			  return 0;
		  return ans.get(1); // ans[0] is v itself
	  }

	  /**
	   * Return N successive "antipodal" vertices, starting with 
	   * given list 'ants'. That is, having reached list 
	   * {v1, v2, ..., vj}, inductively the next so it is 
	   * the furthest combinatorial distance from predecessors
	   * until we have N in the list.
	   * @param ants NodeLink; if empty, start with max vert index.
	   * @param N int, number we want in list, (including 'ants')
	   * @return NodeLink, v1,...,vN; null on error.
	   * NOTE: if 'ants' has N or more elements, return these.
	   */
	  public NodeLink antipodal_verts(NodeLink ants,int N) {
		  if (N<1)
			  throw new ParserException("improper request");
		  NodeLink antipods=new NodeLink(this);
		  
		  // no ants? choose 1 as default element
		  if (ants==null || ants.size()==0) {
			  if (N==1) {
				  antipods.add(nodeCount);
				  return antipods;
			  }
			  ants=new NodeLink(this,nodeCount);
		  }

		  packDCEL.zeroEUtil();

		  // start with what we were given
		  for (int j=0;j<ants.size();j++) {
			  int w=ants.get(j);
			  antipods.add(w);
			  setVertUtil(w,1);
		  }
		  
		  UtilPacket uP=new UtilPacket();
		  for (int k=ants.size();k<N;k++) {
			  packDCEL.label_generations(-1,uP);
			  if (uP.value==0.0) { // error: no verts of higher gen found??
				  throw new CombException("ran out of vertices");
			  }
			  antipods.add(uP.rtnFlag);
			  setVertUtil(uP.rtnFlag,1);
		  }
		  return antipods;
	  }
	  
	  /** 
	   * Return array giving the generations of vertices from 'seed'; 
	   * (NOTE: 'util_A' and 'util_B' are used to pass information back,
	   * calling routine must use these immediately on return.)
	   * Array 'greens' must be either null or of length nodeCount+1; 
	   * say vert v is "green" if greens[v]<0. (Typically, greens 
	   * indicates circles already handled, eg., already in some other 
	   * sub-complex.)

	   * Store generations from 'seed' up to max, but stopping at green 
	   * verts. If far is true, continue past max (if necessary) to first 
	   * vert which is boundary or green; 'util_A' returns its index 
	   * (otherwise, 'util_A' returns some vert of the largest generation
	   * reached) as a way to identify the "outside" of the max generation 
	   * sub-complex. (Caution: not all vertices will have an assigned 
	   * generation in returned array.) 
	   * Return 'null' on error; return count via 'util_B' and 'far_vert'
	   * via 'util_A'.
	   * @param seed int, starting vertex
	   * @param greens int[], stop when greens <0
	   * @param max int, stop here if max>0.
	   * @param far boolean
	   * @return int[] containing generations.
	  */
	public int[] label_seed_generations(int seed, int[] greens, int max,
			boolean far) {
		int count = 0;
		int far_vert = seed;

		if (!status || seed < 1 || seed > nodeCount ||
				(greens != null && greens.length > seed && greens[seed] < 0))
			return null;

		int[] final_list = new int[nodeCount + 1];
		VertList genlist = new VertList();
		for (int i = 1; i <= nodeCount; i++)
			setVertUtil(i,0);
		if (greens != null) {
			for (int i = 1; i <= nodeCount; i++)
				if (greens[i] < 0)
					setVertUtil(i,-1);
		}
		genlist.v = seed;
		final_list[seed] = 1;
		count++;
		int last_marked = seed;

		boolean bdry_hits = false;
		// not looking for far_vert?
		if (!far) 
			bdry_hits = true; // pretend that a bdry/green has been found
		boolean hits = true;
		int gen_count = 2;
		while (hits && genlist != null &&
				(max <= 0 || gen_count <= max || bdry_hits)) {
			hits = false;
			VertList vertlist = genlist;
			VertList gtrace;
			genlist = gtrace = new VertList();
			do {
				Vertex vert=packDCEL.vertices[vertlist.v];
				HalfEdge he=vert.halfedge;
				do {
					int w=he.twin.origin.vertIndx;
					if (final_list[w]==0 && getVertUtil(w)==0) {
						final_list[w]=gen_count;
						count++;
						if (!bdry_hits &&
								(isBdry(w) || getVertUtil(w)!=0)) {
							bdry_hits=true;
							far_vert=w;
						}
						gtrace=gtrace.next=new VertList();
						gtrace.v=w;
						hits=true;
						last_marked=w;
						he=he.prev.twin;
					}
				} while(he!=vert.halfedge);
				vertlist = vertlist.next;
			} while (vertlist != null);
			genlist = genlist.next; // first position was empty
			gen_count++;
		}
		if (!bdry_hits) // no bdry or green vert found, return
						// index of largest generation
			far_vert = last_marked; 
		util_A = far_vert;
		util_B = count;
		return final_list;
	}

	  /**
	   * Apply Mobius Mob to specified list of circles. (See more detailed call
	   * for inverse and side-pairing issues.)
	   * @param Mob Mobius
	   * @param vertlist NodeLink
	   * @return int
	   */
	  public int apply_Mobius(Mobius Mob,NodeLink vertlist) {
		  return apply_Mobius(Mob,vertlist,true,true,true);
	  }
	  
	  /** 
	   * Apply Mobius Mob (oriented), else apply inverse to
	   * specified list of circles. If 'red_flag' (default),
	   * adjust selected red chain circle centers; if sp_flag is 
	   * true (default), also recompute the side-pairing maps.
	   * For hyperbolic ideal circles, also adjust the negative
	   * radius (which reflects euclidean radius of horocycle). 
	   * (Note, if Mob is one of the side-pairing maps, then 
	   * application will simply permute the others, so sp_flag 
	   * is turned off, eg., when applying covering maps.)
	   * @param Mob Mobius
	   * @param vertlist NodeLink
	   * @param oriented boolean, false, use Mob^{-1}
	   * @param red_flag boolean, true (default) => also adjust redchain centers
	   * @param sp_flag boolean, true (default) => also adjust side-pairing maps
	   * @return count of circles adjusted
	  */
	  public int apply_Mobius(Mobius Mob,NodeLink vertlist,
	  			boolean oriented,boolean red_flag,boolean sp_flag) {
	    int count=0;
	    CircleSimple sc=new CircleSimple(true);

    	Iterator<Integer> vis=vertlist.iterator();
    	while (vis.hasNext()) {
    		Vertex vert=packDCEL.vertices[vis.next()];
    		if (vert.redFlag) {
    			HalfEdge he=vert.halfedge;
    			// just handle red edges from this 'vert'
    			do {
    				if (he.myRedEdge!=null) {
    	    			CircleSimple circle=he.myRedEdge.getCircleSimple();
    	    			count += Mobius.mobius_of_circle(Mob,hes,circle,
 	    		 	  	       sc,oriented);
     					he.myRedEdge.setCircleSimple(sc);
    				}
    				he=he.prev.twin; // cclw
    			} while (he!=vert.halfedge);
    		}
   			CircleSimple circle=getCircleSimple(vert.vertIndx);
   			count += Mobius.mobius_of_circle(Mob,hes,circle,
   		 	  	       sc,oriented);
				setCircleSimple(vert.vertIndx,sc);
   			count++;
    	}
    	
   		// update the side-pairing (not for spherical)
   		if (hes<=0 && sp_flag)
   			packDCEL.updatePairMob();
    	return count;
	  } 

	  /**
	   * adds a vertex which connects up to all vertices on the boundary
	   * component of vertices in given list. Combinatorics are reset.
	   * Lose any 'xyzpoint' info.
	   * @param vertlist NodeLink
	   * @return int count
	   */
	  public int add_ideal(NodeLink vertlist) {
	    if (vertlist==null || vertlist.size()==0) 
	    	return 0;
	    int count=0;
	    Iterator<Integer> vlist=vertlist.iterator();
   		int origCount=nodeCount;
   		int v;
   		while(vlist.hasNext() && isBdry((v=(Integer)vlist.next()))) {
	    	int loccount=RawManip.addIdeal_raw(packDCEL, v, v);
	    	if (loccount==0) 
	    		throw new CombException("add failed");
	    	count++;
	    } // end of while
	    	
    	if (count==0)
    		return 0;
    		
    	// fix combinatorics
	    packDCEL.fixDCEL(this);
	    	
	    // if a sphere, set cent/rad of new vertices
	    if (hes>0) {
	    	for (int j=origCount+1;j<=nodeCount;j++) {
	    		setRadius(j,Math.PI/2.0);
	    		setCenter(j,new Complex(0,Math.PI));
	    	}
	    }
	    xyzpoint=null;
	    set_aim_default();
	    return count;
	  }

	  /** 
	   * Project hyp max packing (already computed and laid out) to sphere 
	   * with normalization options; combination of 'geom_to_s', 'add_ideal', 
	   * and 'NSpole'. 
	   * 
	   * Use eucl intermediary for numerical reasons. Alpha vert goes to north 
	   * pole, added ideal vertex S to south. If argument 'E' is positive, then 
	   * use it as east pole (so it gets centered at 1 on the sphere); else 
	   * if 'ratio' positive, then use as ratio of alpha/S; else ratio=1.0.
	   * Note: if packing is hyperbolic, but bdry circles are not horocycles,
	   * then the spherical packing will not be coherent.
	   * @param E int
	   * @param ratio double
	   * @return 1
	   */
	  public int proj_max_to_s(int E,double ratio) {
	    double lam=1.0,cabs,rad;
	    Complex factor=new Complex(1.0);

	    geom_to_e();
	    if (E<=0 && ratio<=0.0) {
	        E=0;
	        ratio=1.0;
	    }
	    if (E>0 && E<=nodeCount && 
	        (cabs=getCenter(E).abs())>Mobius.MOB_TOLER) { // have vertex 
	        rad=getRadius(E);
	        lam=1.0/Math.sqrt((cabs+rad)*(cabs-rad));
	        factor=new Complex(0.0,(-1.0)*getCenter(E).arg()).exp().times(lam);
	        ratio=0.0;
	    }
	    else if (ratio>0.0 && ratio<=1.0){ // have ratio instead
	        lam=1.0/Math.sqrt(packDCEL.getVertRadius(packDCEL.alpha));
	        factor=new Complex(lam,0);
	        E=0;
	    }

	    for (int j=1;j<=nodeCount;j++) { // scale, rotate
	    	Complex zz=getCenter(j);
	    	setCenter(j,zz.times(factor));
	      setRadius(j,lam*getRadius(j));
	    }
	    geom_to_s(); // project to sphere 
  	  	setGeometry(1);

  		try { 
  			packDCEL.redChain=null;
  			RawManip.addBary_raw(packDCEL,packDCEL.idealFaces[1].edge,false);
  		} catch(Exception ex) {
  			throw new CombException("'proj' dcel error");
  		}
  		packDCEL.fixDCEL(this);

	    setCenter(nodeCount,new Complex(0.0,Math.PI));
	    setRadius(nodeCount,Math.asin(2.0*lam/(1.0+lam*lam)));
	    if (ratio>0.0 && Math.abs(ratio-1.0)>Mobius.MOB_TOLER) {
	        // adjust for specified ratio when on sphere 
	        factor=new Complex(0.0);
	        Mobius Mob=Mobius.NS_mobius(packDCEL.getVertCenter(packDCEL.alpha),
	        		getCenter(nodeCount),MathComplex.ID,
	        		packDCEL.getVertRadius(packDCEL.alpha),
	        		getRadius(nodeCount),0.0,ratio);
	        vlist=new NodeLink(this,"a");
	        apply_Mobius(Mob,vlist);
	    }
	    return 1;
	  } 

	  /**
	   * set circle color gradations from color ramp based on radii;
	  */
	  public int radius_col_comp() {
	    double b,t;
	    int mid;

	    mid=(int)(ColorUtil.color_ramp_size/2);
	    b=t=getRadius(1);
	    for (int v=2;v<=nodeCount;v++) {
	    	double rad=getRadius(v);
	        if (rad>t) t=rad;
	        if (rad<b) b=rad;
	    }
	    if (b<0) b=0.0;
	    if (t<=0 || Math.abs(t-b)/t<.005) { // problem of small variation
	        for (int v=1;v<=nodeCount;v++) setCircleColor(v,ColorUtil.cloneMe(ColorUtil.coLor(mid)));
	        return 1;
	    }
	    if (hes<0) {
	        for (int v=1;v<=nodeCount;v++) {
	        	setCircleColor(v,ColorUtil.cloneMe(ColorUtil.coLor(1+(int)((mid-2)*(getRadius(v)-b)/(t-b)))));
	        }
	    }
	    else if (hes>=0) {
	        for (int v=1;v<=nodeCount;v++) {
	        	setCircleColor(v,ColorUtil.cloneMe(ColorUtil.coLor(1+(int)((mid-2)*(getRadius(v)-t)/(b-t)))));
	        }
	    }
	    return 1;
	  } 


	  /**
	   * Color code circles by ratio of radii or angle sum, this compared
	   * to pack q; lower indices (blue) indicate q larger.
	   * @param radcomp: true=compare radii; false=compare angle sums
	   * 
	   * TODO: need to allow a 'vertex_map' for conversion of indices
	   */
	  public int compareCircles(PackData q,boolean radcomp,NodeLink nlink) {
	    int node;

	    if (q.hes!=hes) {
	    	CirclePack.cpb.errMsg("Comparison packing has different geometry");
	    	return 0;
	    }
	    node=(nodeCount>q.nodeCount) ? q.nodeCount : nodeCount;
	    Iterator<Integer> vit=nlink.iterator();
	    // collect data and syncronized index vector
	    Vector<Double> data=new Vector<Double>(1); // for data
	    Vector<Integer> indx=new Vector<Integer>(1); // 
	    while (vit.hasNext()) {
	    	int v=vit.next();
	    	if (v>0 && v<=node) {
	    		if (radcomp) { 
	    			if (hes<0) { 
	    				double rp=getRadius(v);
	    				double rq=q.getRadius(v);
	    				if (rp<=0.0) rp=1.0;
	    				if (rq<=0.0) rq=1.0;
	    				data.add(rp/rq);
	    				indx.add(v);
//	    		        if (rData[v].rad>0.0 && q.rData[v].rad>0.0) { 
//	    		  	  		data.add(Math.log(rData[v].rad)/
//	    		  	  				Math.log(q.rData[v].rad));
//	    		  	  		indx.add(v);
//	    		        }
	    			}
	    			else {
	    				data.add(getRadius(v)/q.getRadius(v));
	    				indx.add(v);
	    			}
	    		}
	    		else {
	    			data.add(getCurv(v)/q.getCurv(v));
	    			indx.add(v);
	    		}
	    	}
	    }
	    // TODO: color_conversion task: replace with 'Color' objects
	    Vector<Integer> codes=ColorUtil.blue_red_ratio_ramp(data);
	    if (codes==null || codes.size()==0 || indx.size()!=codes.size()) 
	    	return 0;
	    for (int i=0;i<indx.size();i++) {
	    	int v=indx.get(i);
	    	setCircleColor(v,ColorUtil.cloneMe(ColorUtil.coLor(codes.get(i))));
	    }
	    return indx.size();
	  } 

	  /** 
	   * Set circle colors using encoded string of options.
	   *   q{pnum} or p{pnum} must be first (no '-' for these)
	   *   bg/fg for fore/background
	   *   {c} for color code, 0 to 255.
	   *   rad  for coloring based on radius (all circles)
	   *   s (resp. s0)  spread cyclically from 16 colors, starting at random point (resp. at 0)
	   *   S (resp. S0)  spread cyclically from 16 colors, all the same starting at random point (resp. at 0)
	   * @param vector of flag sequences
	   */
	  public int color_circles(Vector<Vector<String>> flagSegs) {
		  // there should be a segment, and it should start with color spec
		  Vector<String>items=(Vector<String>)flagSegs.get(0);
		  String str=(String)items.remove(0); // throw out, but held in 'str'
		  NodeLink vertlist=null;
		  
		  // "q" and "p" options
		  if (str.startsWith("q")) { // form q {q} or q{q}: compare to q
			  PackData qackData=null;
			  int qnum=-1; 
			  try { // no space, then pack number?
				  qnum=Integer.parseInt(str.substring(1));
			  } catch (Exception ex) {
				  try { // pack number after space/
					  qnum=Integer.parseInt(items.elementAt(1)); // might be a space
					  items.remove(0);
				  } catch (Exception ex1) {
					  return 0;
				  }
			  }
			  if (qnum>=0 && qnum<CPBase.NUM_PACKS) 
				  qackData=PackControl.cpDrawing[qnum].getPackData();
			  else 
				  throw new ParserException("Specified pack numbe, "+qnum+", is out of range");
			  if (hes!=qackData.hes) {
				  throw new ParserException("set_color: rad comparison only if both hyp or both eucl.");
			  }
			  // check for 'angsum' key word, else default to radii
			  boolean radcomp=true;
			  if (items!=null && items.size()>0) {
				  String tstr=(String)items.get(0);
				  if (tstr.startsWith("angsum")) {
					  radcomp=false;
					  items.remove(0);
					  vertlist=new NodeLink(this,items); // rest should be vertices
				  }
				  else vertlist=new NodeLink(this,items);
			  }
			  else vertlist=new NodeLink(this,"a");
			  return compareCircles(qackData,radcomp,vertlist);
		  }
		  if (str.startsWith("p")) { // form p {p} or p{p}; copy from p
			  PackData qackData=null;
			  int pnum=-1; 
			  try { // no space, then pack number?
				  pnum=Integer.parseInt(str.substring(2));
				  items.remove(0);
			  } catch (Exception ex) {
				  try { // pack number after space/
					  pnum=Integer.parseInt(items.elementAt(1)); // might be a space
					  items.remove(1);
					  items.remove(0);
				  } catch (Exception ex1) {
					  return 0;
				  }
			  }
			  if (pnum>=0 && pnum<CPBase.NUM_PACKS) 
				  qackData=PackControl.cpDrawing[pnum].getPackData();
			  else throw new ParserException("Pack number, "+pnum+", is out of range");
			  
			  if (items!=null && items.size()>0)
				  vertlist=new NodeLink(this,items);
			  else vertlist=new NodeLink(this,"a");
			  
			  Iterator<Integer> vlist=vertlist.iterator();
			  while(vlist.hasNext()) {
				  int v=(Integer)vlist.next();
				  if (v<=qackData.nodeCount);
				  Color col=qackData.getCircleColor(v);
				  setCircleColor(v,ColorUtil.cloneMe(col));
			  }
			  return 1;
		  }
		  vertlist=new NodeLink(this,items);
		  if (str.startsWith("rad")) {
			  return radius_col_comp();
		  }
		  int count=0;
		  if (str.startsWith("s") || str.startsWith("S")) { // use 'spread' of colors
			  count=(int)(Math.random()*16);
			  Iterator<Integer> vlist=vertlist.iterator();
			  Color cLr=ColorUtil.getBGColor();
			  count=colorIndx;
			  if (str.length()>1 && str.charAt(1)=='0') { // 's0' or 'S0', then count=0
				  count=0;
				  colorIndx=0;
			  }
			  boolean allc=false;
			  if (str.startsWith("S")) { // random, but all the same
				  allc=true;
				  cLr=ColorUtil.spreadColor(count%16);
			  }
			  while(vlist.hasNext()) {
				  int v=(Integer)vlist.next();
				  if (allc) 
					  setCircleColor(v,ColorUtil.cloneMe(cLr));
				  else 
					  setCircleColor(v,ColorUtil.spreadColor(count%16));
				  count++;
			  }
			  colorIndx=count; // save for next visit
			  return count;
		  }
		  if (str.startsWith("d")) { // color based on degree
			  // 6=white, 5=blue,7=red,
			  Iterator<Integer> vlist=vertlist.iterator();
			  while(vlist.hasNext()) {
				  int v=(int)vlist.next();
				  int deg=countFaces(v);
				  setCircleColor(v,ColorUtil.colorByDegree(deg));
				  count++;
			  }
			  return count;
		  }
		  if (str.startsWith("a")) { // based on argument of center
			  double theta;
			  Iterator<Integer> vlist=vertlist.iterator();
			  while(vlist.hasNext()) {
				  int v=(int)vlist.next();
				  theta=getCenter(v).arg();
				  setCircleColor(v,ColorUtil.ArgWheel(theta));
				  count++;
			  }
			  return count;
		  }
			  
		  // otherwise, find 'coLor', then apply it to all listed vertices 
		  int coLor=ColorUtil.FG_COLOR;
		  if (str.startsWith("bg")) {
			  coLor=ColorUtil.BG_COLOR;
		  }
		  else if (str.startsWith("fg")) {
			  coLor=ColorUtil.FG_COLOR;
		  }
		  else { // read color index
			  try {
				  coLor=Integer.parseInt(str);
			  } catch(Exception ex) {
				  throw new ParserException("Error in color code: "+str);
			  }
			  if (coLor<0 || coLor>255) 
				  throw new ParserException("Color code "+coLor+" out of range [0,255]");
		  }
		  Iterator<Integer> vlist=vertlist.iterator();
		  while(vlist.hasNext()) {
			  int v=(Integer)vlist.next();
			  setCircleColor(v,ColorUtil.cloneMe(ColorUtil.coLor(coLor)));
		  }
		  return 1;
	  }
	  
	  public int color_faces(Vector<Vector<String>> flagSegs) {
		  Vector<String>items=(Vector<String>)flagSegs.get(0);
		  String str=(String)items.remove(0); // throw out, but held in 'str'
		  FaceLink facelist=null;
		  
		  // first, have to check for various letter codes:
		  //   qc (quasi-conformal)
		  //   area (gradation based on area)
		  //   x (compare eucl area in p to 3-space area if 'p.xyzpoints' exists
		  //   s (spread of colors for distinctness)
		  //   v (use vertex colors, majority wins)

		  char c=str.charAt(0);
		  int count=0;
		  switch(c){
		  case 'a':
		  {
			  if (str.startsWith("area")) {
				  return ColorCoding.face_area_comp(this);
			  }	
			  facelist=new FaceLink(this,items);
			  Iterator<Integer> flist=facelist.iterator();
			  while(flist.hasNext()) {
				  int f=flist.next();
				  double arg=0.0;
				  int[] fverts=getFaceVerts(f);
				  Complex z0=getCenter(fverts[0]);
				  Complex z1=getCenter(fverts[1]);
				  Complex z2=getCenter(fverts[2]);
				  CircleSimple sc=null;
				  if (hes<0) { 
					  sc=HyperbolicMath.hyp_tang_incircle(z0,z1,z2,
							  getRadius(fverts[0]),getRadius(fverts[1]),
							  getRadius(fverts[2]));
					  arg=sc.center.arg();
				  }
				  else if (hes>0) {
						  sc=SphericalMath.sph_tri_incircle(z0,z1,z2);
						  arg = SphView.s_pt_to_visual_plane(sc.center).arg();
				  }
				  else {
					  sc=EuclMath.eucl_tri_incircle(z0,z1,z2);
					  arg=sc.center.arg();
				  }
				  setFaceColor(f,ColorUtil.ArgWheel(arg));
				  count++;
			  }
			  return count;
		  }
		  case 'q':
		  {
			  // ========= qc ==============
			  if(str.startsWith("qc")) {
				  flagSegs.remove(0);
				  return color_qc(flagSegs); // pass parsing to subroutine
			  }
			  
			  else {  // form q {q}: compare area to q
				  // TODO: currently, this applies to ALL faces
				  PackData qackData=null;
				  int qnum=-1; 
				  try { // no space, then pack number?
					  qnum=Integer.parseInt(str.substring(2));
					  items.remove(0);
				  } catch (Exception ex) {
					  try { // pack number? there might be a space
						  qnum=Integer.parseInt(items.elementAt(0)); 
						  items.remove(0);
					  } catch (Exception ex1) {
						  return 0;
					  }
				  }
				  if (qnum>=0 && qnum<CPBase.NUM_PACKS) 
					  qackData=PackControl.cpDrawing[qnum].getPackData();
				  else throw new ParserException("Pack number, "+qnum+
						  ", out of range");
				  if (hes!=qackData.hes) {
					  throw new ParserException("set_color: "+
							  "area comparision only if  both hyp "+
							  "or both eucl.");
				  }
				  if (hes<0) 
					  return ColorCoding.h_compare_area(this,qackData);
				  return ColorCoding.e_compare_area(this,qackData);
			  }
		  }
		  case 'p': // form p {p}; copy from p
		  {
			  PackData qackData=null;
				  int pnum=-1; 
				  try { // no space, then pack number?
					  pnum=Integer.parseInt(str.substring(2));
					  items.remove(0);
				  } catch (Exception ex) {
					  try { // pack number after space/
						  pnum=Integer.parseInt(items.elementAt(1)); // might be a space
						  items.remove(1);
						  items.remove(0);
					  } catch (Exception ex1) {
						  return 0;
					  }
				  }
				  if (pnum>=0 && pnum<CPBase.NUM_PACKS) 
					  qackData=PackControl.cpDrawing[pnum].getPackData();
				  else throw new ParserException("Pack number, "+pnum+", out of range");

				  facelist=new FaceLink(this,items);
				  Iterator<Integer> flist=facelist.iterator();
				  while(flist.hasNext()) {
					  int f=(Integer)flist.next();
					  if (f<=qackData.faceCount);
					  Color col=qackData.getFaceColor(f);
					  setFaceColor(f,ColorUtil.cloneMe(col));
					  count++;
				  }
				  return count;
		  }
		  case 's': // use 'spread' of colors: fall through
		  case 'S': // one color for the whole list
		  {
			  facelist=new FaceLink(this,items);
			  Iterator<Integer> flist=facelist.iterator();
			  int f;
			  count=colorIndx;
			  Color cLr=ColorUtil.getBGColor();
			  count=(int)(Math.random()*16);
			  if (str.length()>1 && str.charAt(1)=='0') { // start at 0
				  count=0;
				  colorIndx=0;
			  }
			  if (c=='S') {
				  cLr=ColorUtil.spreadColor(count%16);
			  }
			  while(flist.hasNext()) {
				  f=(Integer)flist.next();
				  if (c=='S') 
					  setFaceColor(f,ColorUtil.cloneMe(cLr));
				  else 
					  setFaceColor(f,ColorUtil.spreadColor(count%16));
				  count++;
			  }
			  colorIndx=count;
			  return count;
		  }
		  case 'x': // compare eucl face areas: 3D/2D
		  {
			  if (hes!=0 || ColorCoding.setXYZ_areas(this)==0)
				  return count;
			  facelist=new FaceLink(this,items);
			  Vector<Double>ratios=PackMethods.areaRatio(this, facelist);
			  Vector<Color> outvec=ColorUtil.richter_red_ramp(ratios);
			  outvec.remove(0); // first spot also unused
			  
			  // set these face colors 
			  Iterator<Integer> flist=facelist.iterator();
			  while(flist.hasNext()) {
				  int f=flist.next();
				  setFaceColor(f,ColorUtil.cloneMe(outvec.remove(0)));
				  count++;
			  }
			  return count;
		  }
		  } // end of switch
		  
		  // otherwise, find 'coLor', then apply it to all listed faces
		  int coLor=ColorUtil.FG_COLOR;
		  if (str.startsWith("bg")) {
			  coLor=ColorUtil.BG_COLOR;
		  }
		  else if (str.startsWith("fg")) {
			  coLor=ColorUtil.FG_COLOR;
		  }
		  else { // read color index
			  try {
				  coLor=Integer.parseInt(str);
			  } catch(Exception ex) {
				  throw new ParserException("Error in color code: "+str);
			  }
			  if (coLor<0 || coLor>255) 
				  throw new ParserException("Color code "+coLor+" out of range [0,255]");
		  }
		  facelist=new FaceLink(this,items);
		  Iterator<Integer> flist=facelist.iterator();
		  while(flist.hasNext()) {
			  int f=(Integer)flist.next();
			  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(coLor)));
			  count++;
		  }
		  return count;
	  }
	  
	  /**
	   * Set the 'color' of edges
	   * @param flagSegs Vector<Vector<String>>
	   * @return int count
	   */
	  public int color_edges(Vector<Vector<String>> flagSegs) {
		  Vector<String>items=(Vector<String>)flagSegs.get(0);
		  String str=(String)items.remove(0); // throw out, but held in 'str'
		  HalfLink edgelist=new HalfLink(this,items);
		  
		  // first, have to check for various letter codes:
		  //   r reset all to foreground
		  //   z (blue-red gradation based on schwarzian)
		  //   s/S (spread of colors for distinctness)

		  char c=str.charAt(0);
		  int count=0;
		  switch(c){
		  case 's':
		  case 'S': // one color for the whole list
		  {
			  Iterator<HalfEdge> his=edgelist.iterator();
			  count=colorIndx;
			  Color cLr=ColorUtil.getBGColor();
			  count=(int)(Math.random()*16);
			  if (str.length()>1 && str.charAt(1)=='0') { // start at 0
				  count=0;
				  colorIndx=0;
			  }
			  if (c=='S') {
				  cLr=ColorUtil.spreadColor(count%16);
			  }
			  while(his.hasNext()) {
				  HalfEdge he=his.next();
				  if (c=='S') 
					  setEdgeColor(he,ColorUtil.cloneMe(cLr));
				  else 
					  setEdgeColor(he,ColorUtil.spreadColor(count%16));
				  count++;
			  }
			  colorIndx=count;
			  return count;
		  }
		  case 'r':
		  {
			  Iterator<HalfEdge> his=edgelist.iterator();
			  while (his.hasNext()) {
				  HalfEdge he=his.next();
				  setEdgeColor(he,ColorUtil.FG_Color);
				  count++;
			  }
			  return count;
		  }
		  case 'z': // schwarzians
		  {
			  // first have to set blue_red ramp entries based
			  //    on all interior edges.
			  HalfLink hlink=new HalfLink(this,"i");
			  ArrayList<Double> data=new ArrayList<Double>();
			  Iterator<HalfEdge> his=hlink.iterator();
			  while (his.hasNext()) 
				  data.add(his.next().getSchwarzian());
			  ArrayList<Integer> codes=ColorUtil.blue_red_color_ramp(data);
			  
			  // Now color edgelist
			  his=edgelist.iterator();
			  while(his.hasNext()) {
				  HalfEdge he=his.next();
				  int index=hlink.indexOf(he);
				  if (index>=0) {
					  int k=codes.get(index);
					  he.setColor(ColorUtil.coLor(k));
				  }
				  count++;
			  }
			  return count;
		  }
		  } // end of switch
		  
		  return count;
	  }

	  /**
	   * Set colors of tiles (tmode=1), dual tiles (tmode==2), or quad 
	   * tiles (tmode==3), if such tilings exist. Options are color spread,
	   * use color of 'baryVert' vertex, use given code.
	   * @param tmode int
	   * @param flagSegs
	   * @return count
	   */
	  public int color_tiles(int tmode,Vector<Vector<String>> flagSegs) {
		  TileData myTileData=tileData;
		  if (tileData==null || tileData.tileCount<=0)
			  return 0;
		  if (tmode==2) {
			  if (tileData.dualTileData==null)
				  return 0;
			  myTileData=tileData.dualTileData;
		  }
		  else if (tmode==2) {
			  if (tileData.quadTileData==null)
				  return 0;
			  myTileData=tileData.quadTileData;
		  }

		  Vector<String>items=(Vector<String>)flagSegs.get(0);
		  String str=(String)items.remove(0); // throw out, but held in 'str'
		  TileLink tilelist=null;
		  
		  // first, have to check for various letter codes:
		  //   v copy color of barycenter vertex
		  //   s (spread of colors for distinctness)
		  //   d for degree (meaning, number of tile vertices)

		  char c=str.charAt(0);
		  int count=0;
		  switch(c){
		  case 'v': // baryCenter color (as vertex)
		  {
			  tilelist=new TileLink(myTileData,items);
			  Iterator<Integer> tlist=tilelist.iterator();
			  int t;
			  while (tlist.hasNext()) {
				  t=tlist.next();
				  Tile tile=myTileData.myTiles[t];
				  Color color=getCircleColor(tile.baryVert);
				  myTileData.myTiles[t].color=ColorUtil.cloneMe(color);
				  count++;
			  }
			  return count;
		  }
		  case 'd': // by number of corners
		  {
			  tilelist=new TileLink(myTileData,items);
			  Iterator<Integer> tlist=tilelist.iterator();
			  int t;
			  while (tlist.hasNext()) {
				  t=tlist.next();
				  Tile tile=myTileData.myTiles[t];
				  tile.color=ColorUtil.colorByDegree(tile.vertCount);
				  count++;
			  }
			  return count;
		  }
		  case 's': // use 'spread' of colors: fall through
		  case 'S': // one color for the whole list
		  {
			  tilelist=new TileLink(myTileData,items);
			  Iterator<Integer> tlist=tilelist.iterator();
			  int t;
			  Color cLr=ColorUtil.getBGColor();
			  count=(int)(Math.random()*16);
			  if (str.length()>1 && str.charAt(1)=='0') // start at 0
				  count=0;
			  if (c=='S') {
				  cLr=ColorUtil.spreadColor(count%16);
			  }
			  try {
				  while(tlist.hasNext()) {
					  t=(Integer)tlist.next();
					  if (c=='S') 
						  myTileData.myTiles[t].color=new Color(cLr.getRed(),cLr.getGreen(),cLr.getBlue()); // all use the same color
					  else 
						  myTileData.myTiles[t].color=ColorUtil.spreadColor(count%16);
					  count++;
				  }
			  } catch (Exception ex) {}
			  return count;
		  }
		  } // end of switch
		  
		  // otherwise, find 'coLor', then apply it to all listed faces
		  int coLor=ColorUtil.FG_COLOR;
		  if (str.startsWith("bg")) {
			  coLor=ColorUtil.BG_COLOR;
		  }
		  else if (str.startsWith("fg")) {
			  coLor=ColorUtil.FG_COLOR;
		  }
		  else { // read color index
			  try {
				  coLor=Integer.parseInt(str);
			  } catch(Exception ex) {
				  throw new ParserException("Error in color code: "+str);
			  }
			  if (coLor<0 || coLor>255) 
				  throw new ParserException("Color code "+coLor+" out of range [0,255]");
		  }
		  
		  //
		  tilelist=new TileLink(myTileData,items);
		  Iterator<Integer> tlist=tilelist.iterator();
		  while(tlist.hasNext()) {
			  int t=(Integer)tlist.next();
			  myTileData.myTiles[t].color=ColorUtil.coLor(coLor);
			  count++;
		  }
		  return count;
	  }

	  /**
	   * Color faces depending on quasiconformal dilatations. Options
	   * include the qc-dilatation for a mapping from p to q; because
	   * face data is ephemeral, we need p and q to have essentially
	   * the same combinatorics and same face indices (for instance,
	   * one can't change alpha since that would reset face indices).
	   * Both p and q must be euclidean.
	   * 
	   * Also can compare to eucl pack p to its stored xyz-coordinates
	   * or (eventually) to those in a file. 
	   * @param flagSegs
	   * @return
	   */
	  public int color_qc(Vector<Vector<String>> flagSegs) {
		  Vector<String> items=null;
		  String str=null;
		  PackData qackData=null;
		  double maxdil=2.0; // upper bound on dilatation, default 2.0.
		  boolean compare=true;
		  
		  // throw out any empty elements
		  for (int j=flagSegs.size()-1;j>=0;j--) {
			  items=flagSegs.get(j);
			  if (items.size()==0) flagSegs.remove(j);
		  }
		  
		  /* First flag string must be one of these, other flags may
		   * follow. Look for, read, and remove each flag.
		   * 	-q {p} compare to pack p
		   *    -x compared to xyz-data stored for this packing
		   *    -m {m} maximal dilatation: those above are colored blue.
		   *  not yet implemented:
		   * 	-n {filename} compare to xyz-data in filename
		   * 	-s {filename} ditto, but data in script file
		   *  Set compare=true (compare to q); false, use xyz data. 
		   */ 
		  Iterator<Vector<String>> its=flagSegs.iterator();
		  while (its.hasNext()) {
			  items=(Vector<String>)its.next();
			  str=(String)items.get(0);
			  items.remove(0); // there must be at least one flag first
			  if (StringUtil.isFlag(str)) {
				  switch(str.charAt(1)) {
				  case 'q': // compare area to pack q
				  {
					  int qnum=StringUtil.qFlagParse(str);
					  if (qnum==-2) { // old style flag '-q {p}' (with space)
						  try {
							  qnum=Integer.parseInt((String)items.get(0));
						  } catch (Exception ex) {}
					  }
					  if (qnum<0 || qnum>=CPBase.NUM_PACKS)
						  throw new ParserException();
					  qackData=PackControl.cpDrawing[qnum].getPackData();
					  break;
				  }
				  case 'm':
				  {
					  maxdil=Double.parseDouble(items.get(0));
					  items.remove(0);
					  break;
				  }
				  case 'n':
				  {
					  compare=false;
//					  String filename=items.remove(0);
					  items.remove(0);
					  // TODO: figure out how to read, store data for use.
					  items.remove(0);
					  break;
				  }
				  case 's':
				  {
					  compare=false;
//					  String filename=items.remove(0);
					  items.remove(0);
					  // TODO: figure out how to read, store data for use.
					  items.remove(0);
					  break;
				  }
				  case 'x':
				  {
					  compare=false;
					  if (xyzpoint==null) {
						  flashError("color error: no xyz-data is stored for this packing");
						  return 0;
					  }
					  break;
				  }
				  default:
				  {
					  throw new ParserException();
				  }
				  } // end of switch
			  } 
		  } // end of while
		  
		  // now to do the computations:
		  if (compare) {
			  maxdil=color_qc_map(qackData,maxdil);
		  }
		  else {
			  maxdil=color_qc_xyz(xyzpoint,faceCount,maxdil);
		  }
		  if (maxdil<0.0) {
			  flashError("color 'qc' option has failed");
			  return 0;
		  }
		  CirclePack.cpb.msg("color qc; maximal dilataion was "+maxdil);
		  return 1;
	  }
	
	  /**
	   * Color faces based on positive values[] given for all vertices; 
	   * uses red half of color ramp.
	   * @param values double[nodeCount+1];
	   * @return int count
	   */
	  public int color_face_interp(double []values) {
		  int count=0;
		  double max=-100000;
		  double min=100000;
	      int  mid=(int)(ColorUtil.color_ramp_size/2);
		  for (int v=1;v<=nodeCount;v++) {
			  max = (values[v]>max) ? values[v] : max;
			  min = (values[v]<min && values[v]>0.0) ? values[v] : min;
		  }
		  
		  // spread colors from smaller of 'min' and 'max'/2 to 'max' 
		  min= (min>max/2.0) ? max/2.0:min;
		  
		  // color based on average of values at vertices
		  for (int f=1;f<=faceCount;f++) {
			  int[] vts=packDCEL.faces[f].getVerts();
			  int num=packDCEL.faces[f].getNum();
			  double accum=0.0;
			  for (int j=0;j<num;j++)
				  accum+=values[vts[j]];
			  accum /=num;
			  if (accum>=min)
				  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(mid+1+(int)((mid-2)*(accum-min)/max))));
			  else
				  setFaceColor(f,ColorUtil.getBGColor());
			  count++;
		  }
		  return count;
	  }
	  
	  /** 
	   * Color faces to show quasiconformal dilatation for map from
	   * 'this' to 'q'. Non-eucl data converted to eucl for the computation
	   * (3D eucl flat triangles for sphere). Shades of red based on 
	   * quasiconformal dilatation, blue for those beyond 'maxdil'.
	   * (Note: the faces should have corresponding triples of vertices, 
	   * as when p and q are packings of the same complex, but we only 
	   * check that the face indices are legal.) 
	   * @param q @see PackData (map from 'this' to 'q')
	   * @param maxdil double, threshold to color blue, default 2.0
	   * @return double, max dilation encountered, negative on error
	  */
	  public double color_qc_map(PackData q,double maxdil) {
	      double []dil;

	      if (!q.status || q.nodeCount<3) {
	    	  throw new ParserException("Packing 'q' appears empty");
	      }
	      int  mid=(int)(ColorUtil.color_ramp_size/2);
	      dil=new double[faceCount+1];
	      for (int f=1;f<=faceCount;f++) dil[f]=1.0; // default 
	      for (int f=1;f<=faceCount;f++) {
	    	  if ((dil[f]=face_dilatation(q,f))<0.0) 
	    		  dil[f]=-1.0;
	      }
	      double kmax=1.0;
	      for (int i=1;i<=faceCount;i++)
	    	  kmax = (dil[i]>kmax) ? dil[i] : kmax;
	      if (kmax<1.0001) kmax=1.0001; // allow for roundoff, eg, so identity gets pale colors 
	      for (int f=1;f<=faceCount;f++) {
	    	  if (dil[f]>0 && dil[f]<=maxdil)
	    		  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(mid+1+(int)((mid-2)*(dil[f]-1.0)/(maxdil-1.0)))));
	    	  else if (dil[f]>0)
	    		  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(2))); // over ceiling? dark blue.
	    	  else
	    		  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(1))); // couldn't compute? black 
	      }
	      return kmax;
	  } 

	  /** 
	   * For map between 2 (eucl) packings, color faces of p in
	   * shades of red based on quasiconformal dilatation of the map. 
	   * (Note: the faces should have corresponding triples of vertices, 
	   * as when p and q are packings of the same complex with same alpha, 
	   * but we only check that the face indices are legal.) 
	   * 
	   * For each carrier face, we use the xyz location of the circle
	   * centers. If geom is hyp, use eucl centers, if sph, use xyz locations
	   * on unit sphere.
	   *  
	   * TODO: This can be a problem is packing isn't laying out coherently 
	   * and you wanted to use stored radii/overlaps to compute edge lengths.
	   * Might put in a flag to cover this, but still have problems in hyp/sph
	   * cases.
	   * 
	   * TODO: Should use correct geometry instead of converting to euclidean:
	   * e.g., result is not Mobius invariant.
	   * 
	   * @param Point3D array
	   * @param xyzcount
	   * @param double, maxdil is max dilatation for red ramp
	   * @return double, maximal dilation encountered; negative number on error. 
	  */
	  public double color_qc_xyz(Point3D []xyz_list,int xyzcount,double maxdil) {
	      double []dil;

	      int  mid=(int)(ColorUtil.color_ramp_size/2);
	      dil=new double[faceCount+1];
	      for (int f=1;f<=faceCount;f++) dil[f]=1.0; // default 

    	  double A,B,C,a,b,c;
	      for (int f=1;f<=faceCount;f++) {
	    	  int[] verts=packDCEL.faces[f].getVerts();
	    	  if (verts[0]<=xyzcount && verts[1]<=xyzcount && 
	    			  verts[2]<=xyzcount) {
	    		  // yes, we have xyz locations for these 
	    		  
	    		  // need euclidean side lengths A, B, C
	    		  Point3D []pt=new Point3D[3];
	    		  if (hes>0) { // sph
	    			  for (int j=0;j<3;j++)
	    				  pt[j]=new Point3D(getCenter(verts[j]));
	    		  }
	    		  if (hes<0) { // hyp
	    			  CircleSimple sc=null;
	    			  for (int j=0;j<3;j++) {
	    				  sc=HyperbolicMath.h_to_e_data(getCenter(verts[j]),
	    					  getRadius(verts[j]));
	    				  pt[j]=new Point3D(sc.center);
	    			  }
	    		  }
	    		  else {
	    			  Complex z;
	    			  for (int j=0;j<3;j++) {
	    				  z=getCenter(verts[j]);
	    				  pt[j]=new Point3D(z.x,z.y,0.0);
	    			  }
	    		  }
	    		  
	    		  // eucl edge lengths in packing
	    		  A=Point3D.distance(pt[0],pt[1]);
	    		  B=Point3D.distance(pt[1],pt[2]);
	    		  C=Point3D.distance(pt[2],pt[0]);

	    		  // eucl edge lengths in xyz data
	    		  a=Math.sqrt(
	    		      (xyz_list[verts[0]].x-xyz_list[verts[1]].x)*
	    		      	(xyz_list[verts[0]].x-xyz_list[verts[1]].x)
	    		      + (xyz_list[verts[0]].y-xyz_list[verts[1]].y)*
	    		      	(xyz_list[verts[0]].y-xyz_list[verts[1]].y)
	    		      + (xyz_list[verts[0]].z-xyz_list[verts[1]].z)*
	    		      	(xyz_list[verts[0]].z-xyz_list[verts[1]].z));
	    		  b=Math.sqrt(
	    		      (xyz_list[verts[2]].x-xyz_list[verts[1]].x)*
	    		      	(xyz_list[verts[2]].x-xyz_list[verts[1]].x)
	    		      + (xyz_list[verts[2]].y-xyz_list[verts[1]].y)*
	    		      	(xyz_list[verts[2]].y-xyz_list[verts[1]].y)
	    		      + (xyz_list[verts[2]].z-xyz_list[verts[1]].z)*
	    		      	(xyz_list[verts[2]].z-xyz_list[verts[1]].z));
	    		  c=Math.sqrt(
	    		      (xyz_list[verts[0]].x-xyz_list[verts[2]].x)*
	    		      	(xyz_list[verts[0]].x-xyz_list[verts[2]].x)
	    		      + (xyz_list[verts[0]].y-xyz_list[verts[2]].y)*
	    		      	(xyz_list[verts[0]].y-xyz_list[verts[2]].y)
	    		      + (xyz_list[verts[0]].z-xyz_list[verts[2]].z)*
	    		      	(xyz_list[verts[0]].z-xyz_list[verts[2]].z));
	    		  
	    		  // compute dilatation
	    		  dil[f-1]=EuclMath.e_dilatation(A,B,C,a,b,c); 
	    		}
	      }
	      double realMax=0.0;
	      for (int ii=1;ii<=faceCount;ii++)
	    	  realMax = (dil[ii]>realMax) ? dil[ii] : realMax;
	      for (int f=1;f<=faceCount;f++) {
	    	  if (dil[f]>0 && dil[f]<=maxdil)
	    		  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(mid+1+(int)((mid-2)*(dil[f]-1.0)/(maxdil-1.0)))));
	    	  else if (dil[f]>0)
	    		  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(2))); // over ceiling? dark blue.
	    	  else
	    		  setFaceColor(f,ColorUtil.cloneMe(ColorUtil.coLor(1))); // couldn't compute? black 
	      }
	      return realMax;
	  } 

	  /** 
	   * Compute the quasiconformal dilatation of map between faces in
	   * 'this' packing and packing q. 
	   * 
	   * We use edge lengths so this can apply to triangulations 
	   * independent of circles -- e.g. random Delaunay.
	   * 
	   * Treat centers as eucl (3D in sphere case). For hyperbolic, 
	   * do NOT convert (that would require radii as well), just 
	   * treat as euclidean. For sphere, use 3D edge lengths.
	   * 
	   * TODO: we only do eucl computations so far.
	   * 
	   * @param q @see PackData (map is from 'this' to 'q')
	   * @param face int
	   * @return dilatation double; negative indicates error.
	  */
	  public double face_dilatation(PackData q,int face) {

		  // eucl lengths from 'this'
		  double A,B,C;
		  int[] fverts=getFaceVerts(face);
		  Complex z0 = getCenter(fverts[0]);
		  Complex z1 = getCenter(fverts[1]);
		  Complex z2 = getCenter(fverts[2]);
		  if (hes<=0) { // hyp or eucl
			  A=z0.minus(z1).abs();
			  B=z1.minus(z2).abs();
			  C=z2.minus(z0).abs();
		  }
		  else { // sph
			  A=SphericalMath.eucl_dist3D(z0,z1);
			  B=SphericalMath.eucl_dist3D(z1,z2);
			  C=SphericalMath.eucl_dist3D(z2,z0);
		  }
		  
		  // eucl lengths from 'this'
		  double a,b,c;
		  z0 = q.getCenter(fverts[0]);
		  z1 = q.getCenter(fverts[1]);
		  z2 = q.getCenter(fverts[2]);
		  if (q.hes<=0) { // hyp or eucl
			  a=z0.minus(z1).abs();
			  b=z1.minus(z2).abs();
			  c=z2.minus(z0).abs();
		  }
		  else { // sph
			  a=SphericalMath.eucl_dist3D(z0,z1);
			  b=SphericalMath.eucl_dist3D(z1,z2);
			  c=SphericalMath.eucl_dist3D(z2,z0);
		  }
		  
		  return EuclMath.e_dilatation(A,B,C,a,b,c);
	  } 
	  
	  /** 
	   * Copy this packing into a new 'PackData' having 
	   * a null 'CPDrawing' and 'packNum' of 3. 'PackExtender's 
	   * are lost. If the new packing is to replace another, 
	   * the calling routine must handle interchange; 
	   * in particular, 'PackData' and 'CPDrawing' point to 
	   * one another, so 'cpDrawing' and 'cpDrawing.packData' 
	   * may need to be reset. 
	   * Combinatorics and drawing are set here. 
	   * @return new PackData
	   */
	  public PackData copyPackTo() {
		  return copyPackTo(true);
	  }
	  
	  /**
	   * Same as 'copyPackTo', but option whether to keep 'TileData'
	   * @param keepTD boolean; true, then recursively copy 'tileData'
	   * @return new PackData clone
	  */
	  public PackData copyPackTo(boolean keepTD) {
		  PackData p=new PackData(null);
		  p.fileName=fileName;
		  p.hes=hes;
		  PackDCEL pdcel=CombDCEL.cloneDCEL(packDCEL);
		  pdcel.fixDCEL(p);
			  
		  // copy tile data, if it exists
		  if (keepTD && tileData!=null && tileData.tileCount>0) {
			  p.tileData=tileData.copyMyTileData();
			  TileData.setPackings(p.tileData,p);
		  }
		  
		  // TODO: Prefer to make copy of layout in case face order 
		  //    red chain, etc. are specially tailored. Need to spend
		  //    some effort to copy everything correctly.
	    
		  // copy xyz data 
		  if (xyzpoint!=null) {
			  p.xyzpoint=new Point3D[nodeCount+1];
			  for (int i=1;i<=nodeCount;i++) 
				  p.xyzpoint[i]=xyzpoint[i];
		  }
		  p.status=true;
		  return p;
	  } 
	  
		/** 
		 * Draw labels for circles ('circles'=true) or else faces; 'msg_flag' is
		 * 3 by default: bit 1 means display on canvas, bit 2 means to put in 
		 * scratch window.
		 * TODO: should not send labels if off screen (though perhaps to scratch
		 * window). 
		 */
		public int labellist(LinkedList<Integer> llist,int msg_flag,boolean circles) {
		    if (llist==null) return 0;
		    Iterator<Integer> list=llist.iterator();

		    int n,count=0;
		    if (circles) {
		    	while (list.hasNext()) {
		    		n=(Integer)list.next();
		    		cpDrawing.drawIndex(getCenter(n),n,msg_flag);
		    		count++;
		    	}
		    	return count;
		    }
		    while (list.hasNext()) {
		    	n=(Integer)list.next();
			    cpDrawing.drawIndex(getFaceCenter(n),n,msg_flag);
			    count++;
			}
		    // last_index_global=v; ???
		    return count;
		}

		/** 
		 * List plotted circles containing canvas point z. There
		 * may be none: if you want the 'closest' circle, call
		 * 'cir_closest'. 
		 * (For sphere, z is already a real (not apparent) point.) 
		 * @param z, Complex
		 * @return NodeLink, null on empty.
		*/
		public NodeLink cir_search(Complex z) {
		  NodeLink vlist=new NodeLink(this);
		  if (hes>0) { 
			    for (int i=1;i<=nodeCount;i++) {
				      if ( (SphericalMath.s_dist(z,getCenter(i))<getRadius(i))) 
//							   && kData[i].plotFlag>0) 
							  vlist.add(i);
			    }
		  }
		  else {
			  for (int i=1;i<=nodeCount;i++) {
				  if (pt_in_cir(i,z)!=0) { // OBE??? && kData[i].plotFlag>0) {
			    	  vlist.add(i);
			      }
			  }
		  }
		  if (vlist.size()==0) 
			  return (NodeLink)null;
		  return vlist;
		} 
		
		/**
		 * Find a list of vertices whose circles contain z if 'inside' is 
		 * true. If 'inside' is false, find circle with center closest to z 
		 * (first in case of ties), regardless of whether z is inside.
		 * @param z Complex
		 * @param inside boolean: true ==> only circles containing z
		 * @return @see NodeLink, null on error or no hits
		 */
		public NodeLink cir_closest(Complex z,boolean inside) {
			NodeLink vlist=new NodeLink(this);

			double dist=100000000;
			double mydist=100000000;
			int vert=0;
			if (hes>0) {
				for (int i=1;i<=nodeCount;i++) {
					mydist=SphericalMath.s_dist(z,getCenter(i));
					if (mydist<getRadius(i))
//						&& kData[i].plotFlag>0) 
						vlist.add(i);
					if (mydist<dist) {
						dist = mydist;
						vert=i;
					}
				}
			}
			else {
				Complex ectr;
				double rad=0.0;
				for (int i=1;i<=nodeCount;i++) {
					if (hes<0) {
						CircleSimple sc=HyperbolicMath.h_to_e_data(getCenter(i),getRadius(i));
						ectr=sc.center;
						rad=sc.rad;
					}
					else {
						ectr=getCenter(i);
						rad=getRadius(i);
					}
					mydist=z.minus(ectr).abs();
					if (mydist<rad)
//						&& kData[i].plotFlag>0) 
						vlist.add(i);
					if (mydist<dist) {
						dist = mydist;
						vert=i;
					}
				}
			}
			if (inside) {
				if (vlist.size()==0) return null;
				return vlist;
			}
			if (vert<=0) return null; // error
			vlist=new NodeLink(this);
			vlist.add(vert);
			return vlist;
		}
		
		/**
		 * TODO: This is very iffy stuff; needs lots of refinement. Given complex
		 * number z, find the closest edge of the packing. Return null if no
		 * appropriate edge is found or if there is too much ambiguity. For now,
		 * can't handle non-simply connected spherical cases. (And in general,
		 * could be flaky in spherical case where there are more ambiguities.)
		 * 
		 * The procedure differs for simply/multiply connected; in the former,
		 * we can look for a face containing z first and if not found, we search
		 * only for the closest bdry edge.
		 * 
		 * For multiply connected, faces may not have coherent layout. So we
		 * search for closest circle center, then search based on distances to
		 * edges from it. This can also fail, e.g., for ghost circles, circles
		 * on the red chain, etc.
		 */
	public EdgeLink edge_search(Complex z) {
		int face = 0;
		CircleSimple sc = null;
		EdgeLink goit = null;
//		Complex cent = null;

		// simply connected case
		if (isSimplyConnected()) {
			// is z in a face? (take first found):
			FaceLink tlist = tri_search(z);
			if (tlist != null && tlist.size() > 0)
				face = (Integer) tlist.get(0);
			else {
				if (getBdryCompCount()==0)
					return null; // no boundary

				// first, find closest bdry circle
				double best, td;
				int bestj = bdryStarts[1];
				best = geom_dist(z, bestj);
				for (int j = 1; j <= nodeCount; j++) {
					if (isBdry(j)) { // check bdry only
						td = geom_dist(z, j);
						if (td < best) {
							best = td;
							bestj = j;
						}
					}
				}

				// Find closer of two neighbors (in eucl distance in hyp/eucl).
				int jup = getLastPetal(bestj);
				int jdown = getFirstPetal(bestj);
				goit = new EdgeLink(this);
				best = geom_dist(z, jup);
				if (geom_dist(z, jdown) < best)
					goit.add(new EdgeSimple(bestj, jdown));
				else
					goit.add(new EdgeSimple(bestj, jup));
				return goit;
			}

			// Reaching here, have containing face; find closest (eucl) edge.
			// return null if z is too close to a vertex.
			int[] vert = this.getFaceVerts(face);
			double[] edgelen = new double[3];
			double dist;

			// find minimum edge length
			double minlen = edgelen[0] = geom_dist(getCenter(vert[0]),
					vert[1]);
			edgelen[1] = geom_dist(getCenter(vert[1]), vert[2]);
			edgelen[2] = geom_dist(getCenter(vert[2]), vert[0]);
			minlen = (edgelen[1] < minlen) ? edgelen[1] : minlen;
			minlen = (edgelen[2] < minlen) ? edgelen[2] : minlen;

			// find min distance from z to vertices
			double mindist = geom_dist(z, vert[0]);
			mindist = ((dist = geom_dist(z, vert[1])) < mindist) ? dist
					: mindist;
			mindist = ((dist = geom_dist(z, vert[2])) < mindist) ? dist
					: mindist;
			if (mindist < (.1) * minlen || minlen < (.1) * mindist)
				return null;

			// find which line is closest
			double []lindist=new double[3];
			if (hes <= 0) { // hyp/eucl
				for (int j = 0; j < 3; j++) {
					lindist[j] = EuclMath.dist_to_line(z,
							getCenter(vert[j]),
							getCenter(vert[(j + 1) % 3]));
				}
			} else { // spherical
				for (int j = 0; j < 3; j++) {
					lindist[j] = SphericalMath.s_dist_pt_to_line(z,
							getCenter(vert[j]),
							getCenter(vert[(j + 1) % 3]));
				}
			}
			int v = 0;
			double shortest = lindist[0];
			if (lindist[1] < shortest) {
				v = 1;
				shortest = lindist[1];
			}
			if (lindist[2] < shortest) {
				v = 2;
			}

			// want closest center to be first vert in the edge
			int e1=vert[v];
			int e2=vert[(v+1)%3];
			Complex z1=getCenter(e1);
			Complex z2=getCenter(e2);
			if (hes>0) {
				edgelen[1]=SphericalMath.s_dist(z1,z);
				edgelen[2]=SphericalMath.s_dist(z2,z);
			}
			else if (hes<0 && z.abs()<.999) {
				edgelen[1]=HyperbolicMath.h_dist(z1,z);
				edgelen[2]=HyperbolicMath.h_dist(z2,z);
			}
			else {
				edgelen[1]=z1.minus(z).abs();
				edgelen[2]=z2.minus(z).abs();
			}
			
			if (edgelen[1]<getRadius(e1) && edgelen[2]>getRadius(e2)); // okay
			else if (edgelen[1]>getRadius(e1) && edgelen[2]<getRadius(e2)
					|| edgelen[2]<edgelen[1]) { // switch order
				int hold=e1;
				e1=e2;
				e2=hold;
			}
			
			return new EdgeLink(this, new EdgeSimple(e1,e2));

		} // end of simply connected case

		// otherwise, multiply connected
		double mindist = 0.0, dist = 0.0;

		// TODO: multiply connected sphere case
		if (hes > 0)
			return null; // not yet ready for sphere case

		if (hes < 0 && z.abs() >= 1.0)
			return null; // z not in disc

		// is z actually in a circle? (use first found)
		int circle = 0;
		NodeLink vlist = cir_search(z);
		if (vlist != null && vlist.size() > 0) {
			circle = (Integer) vlist.get(0);
		}
		// if not, look for closest circle; we use eucl computations
		else {
			circle = 1;
			Complex cent = getCenter(circle);
			if (hes < 0) {
				sc = HyperbolicMath.h_to_e_data(cent, getRadius(circle));
				cent = new Complex(sc.center);
			}
			mindist = z.minus(cent).abs();
			for (int j = 2; j <= nodeCount; j++) {
				cent = getCenter(j);
				if (hes < 0) {
					sc = HyperbolicMath.h_to_e_data(cent,getRadius(j));
					cent = new Complex(sc.center);
				} else
					dist = z.minus(cent).abs();
				if (dist < mindist) {
					mindist = dist;
					circle = j;
				}
			}
		}

		// now, have the closest circle; use arguments to choose petal
		double crad = getRadius(circle);
		Complex ccent = getCenter(circle);
		if (hes < 0) {
			sc = HyperbolicMath.h_to_e_data(ccent, crad);
			ccent = new Complex(sc.center);
		}
			
//		mindist=ccent.minus(z).abs();
		
		// too close/far from center, too ambiguous ro .
//		if (mindist > 1.5 * crad || mindist < .005 * crad)
//			return null;
		
//		if (hes < 0) {
//			sc = HyperbolicMath.h_to_e_data(ccent, rData[circle].rad);
//			ccent = new Complex(sc.center);
//		}

		// load vector of complex arguments of directions to petals
		Complex fz;
		int num = countFaces(circle);
		double[] args = new double[num + 1];
		int[] petals=getPetals(circle);
		for (int k = 0; k < petals.length; k++) {
			int j = petals[k];
			fz = getCenter(j);
			if (hes < 0) {
				sc = HyperbolicMath.h_to_e_data(fz, getRadius(j));
				fz = new Complex(sc.center);
			}
			args[k] = ccent.minus(fz).arg();
		}
		
		double argz = ccent.minus(z).arg();// arg from center to z

		// find point with closest argument
		int thepetal = 0;
		mindist = Math.abs(argz - args[0]);
		double pi2 = 2.0 * Math.PI;
		if (mindist >= pi2)
			mindist -= pi2;
		for (int k = 1; k < petals.length; k++) {
			dist = Math.abs(argz - args[k]);
			if (dist >= pi2)
				dist -= pi2;
			if (dist < mindist) {
				mindist = dist;
				thepetal = k;
			}
		}

		// done
		goit = new EdgeLink(this);
		goit.add(new EdgeSimple(circle,petals[thepetal]));
		return goit;
	}

	/**
	 * Distance from Complex 'z' to center for vertex 'v'; depends on
	 * geometry; use euclidean in both hyp and eucl cases.
	 */
	public double geom_dist(Complex z,int v) {
		Complex cent=getCenter(v);
		if (hes>0) { // spherical
			return SphericalMath.s_dist(cent,z);
		}
		if (hes<0) { // hyp
		    CircleSimple sc=HyperbolicMath.h_to_e_data(cent,getRadius(v));
		    cent=sc.center;
		}
		return z.minus(cent).abs();
	} 

	/** 
	 * Is z in circle n (eucl/hyp only)? 
	 * @param n int
	 * @param z Complex
	 * @return int, 0 on failure
	*/
	public int pt_in_cir(int n,Complex z) {
		double rad=.5;
		Complex w;
		Complex ectr=null;
	  
		if (hes>0) {
			if (SphericalMath.s_dist(z,getCenter(n))<getRadius(n)) 
				return 1;
			return 0;
		}
		if (hes<0) {
			CircleSimple sc=HyperbolicMath.h_to_e_data(getCenter(n),getRadius(n));
		    ectr=sc.center;
		    rad=sc.rad;
		}
		else if (hes==0) {
		    rad=getRadius(n);
		    ectr=getCenter(n);
		}
		w=z.minus(ectr);
		if (w.abs()<rad) 
			return 1;
		return 0;
	}
	/** 
	 * Search for plotted triangles under canvas pt z.
	 * For sphere, z is already a real (not apparent) point in 
	 * spherical coords. 
	 * TODO: should develop hyperbolic formulae - maybe use
	 * paraboloid model and work in 3D. 
	 * @param z Complex
	 * @return FaceLink, null if none found
	*/
	public FaceLink tri_search(Complex z) {
	    FaceLink nodelist=new FaceLink(this);
	    for (int j=1;j<=faceCount;j++) {
	    	if (pt_in_tri(j,z)!=0)
	    		nodelist.add(j);
	    }
	    return nodelist;
	}

	/** 
	 * Is complex number z in triangular face f?
	 * @param f int, face index
	 * @param z Complex, complex pt
	 * @return 1 on true, 0 on false.
	*/
	public int pt_in_tri(int f, Complex z) {
		Complex[] ctr = getFaceCorners(f);
		if (CommonMath.pt_in_triangle(z, ctr[0],ctr[1],ctr[2],hes))
			return 1;
		return 0;
	}

	public static double row_col(double x,double y,double xa,double xb,
			double ya,double yb) {
		return (x*(ya-yb)-y*(xa-xb)+xa*yb-xb*ya);
	}

	  /** 
	   * Remove one interior vert; must have at least 2 generations 
	   * of interior neighbors. Geometry is not changed; lists are 
	   * adjusted, but any face list data is tossed. Calling routine
	   * will fix combinatorics.
	   * @param v int
	   * @return 1 on success, 0 if not suitable
	   */
	  public int puncture_vert(int v) {
		  PackDCEL newDCEL=CombDCEL.puncture_vert(packDCEL, v);
		  if (newDCEL==null) {
			  throw new DCELException("DCEL puncture for "+v+" failed");
		  }
		  attachDCEL(newDCEL);
		  packDCEL.oldNew=null;
		  return 1;
	  }
	  
	  /** 
	   * Remove a face, converting its three vertices to form a new
	   * 3-edge boundary component. Face needs to be at least 2 
	   * generations distant from the original boundary to avoid
	   * disconnecting things. Geometry is not changed; local face list
	   * data is tossed. Calling routine does 'complex_count', etc.
	   * @param f
	   * @return 1 on success, 0 if not suitable
	   */
	  public int puncture_face(int f) {
		  if (f<1 || f>faceCount)
			  return 0;
		  PackDCEL newDCEL=CombDCEL.puncture_face(packDCEL, f);
		  if (newDCEL==null) {
			  throw new DCELException("DCEL puncturing face "+f+" failed");
		  }
		  attachDCEL(newDCEL);
		  packDCEL.oldNew=null;
		  return 1;
	  }
	  
	  /**
	   * We are operating on this packing. Each edge gets new vertex, 
	   * each face broken into 4 faces. Try to propagate old centers/radii,
	   * overlaps, schwarzians to new edges. Return 0 on failure.
	  */
	  public int hex_refine() {
		  RawManip.hexBaryRefine_raw(packDCEL,false);
		  // DCELdebug.printRedChain(packDCEL.redChain);
		  packDCEL.fixDCEL(this);
		  return 1; 
	  }

	  /**
	   * Store 3D data in p->xyzpoint. More options in the future perhaps,
	   * but for now just converts centers to 3D and stores those. (These
	   * lie in plane (z=0) in eucl/hyp cases.)
	   */
	  public int set_xyz_data() {
	      xyzpoint=new Point3D[nodeCount+1];
	      if (hes>0) { // spherical
	      	for (int i=1;i<=nodeCount;i++) {
	  	    Complex z=getCenter(i);
	  	    xyzpoint[i]=new Point3D(Math.sin(z.y)*Math.cos(z.x),
	  			Math.sin(z.y)*Math.sin(z.x),Math.cos(z.y));
	      	}
	      	return 1;
	      }
	      if (hes<0) { // hyperbolic ((x,y) only)
	      	for (int i=1;i<=nodeCount;i++) {
	  	    CircleSimple sc=HyperbolicMath.h_to_e_data(getCenter(i),getRadius(i));
	      	    xyzpoint[i]=new Point3D(sc.center.x,sc.center.y,0.0);
	      	}
	      	return 1;
	      }
	      else { // euclidean ((x,y) only)
	     	for (int i=1;i<=nodeCount;i++) {
	     		xyzpoint[i]=new Point3D(getCenter(i).x,getCenter(i).y,0.0);
	     	}
	     	return 1;
	      }
	  }
	  
	  /**
		 * Use xyz data to set 'invDist's. This command 
		 * is still evolving. Eventually, may want to 
		 * specify which ones to set; for now, set all.
		 * 
		 * flag=1: find max/min edge lengths in xyz data, set 
		 * inv distances as though circle radii were all min/2.
		 * 
		 * flag=2: for each vertex v find min edgelength, 
		 * set inv distance as though radius of v is half that.
		 * 
		 * Radii are NOT changed --- only used to set
		 * invDistances. Return count, 0 on error.
		 * @param xyz_list Point3D[]
		 * @param int flag,
		 * @return int count, 0 on error
		 */
	public int set_xyz_overlaps(Point3D[] xyz_list,int flag) {
		
		// right size list?
		if (!status || xyz_list==null || xyz_list.length!=nodeCount) {
			CirclePack.cpb.errMsg("usage: 'set_xyz_overlaps': recorded 'xyz' "+
					"data is not correct size");
			return 0;
		}

		double miN = 1000000000.0, maX = 0.0;
		double ivdmax= OKERR, ivdmin = 1000;

		if (flag == 1) { // treat as though all radii were miN/2.

			// compute maX/miN distances
			for (int i = 1; i <= nodeCount; i++) {
				int[] petals=getPetals(i);
				int k;
				for (int j = 0; j < petals.length; j++)
					if ((k = petals[j]) > i && i <= nodeCount
							&& k <= nodeCount) {
						double dist = EuclMath.xyz_dist(xyz_list[i], xyz_list[k]);
						miN = (dist < miN) ? dist : miN;
						maX = (dist > maX) ? dist : maX;
					}
			}
			if (miN == 1000000000.0 || maX == 0.0 || (miN < OKERR))
				return 0; // ??
			double rad = miN / 2.0;
			// compute and store overlaps
			for (int v=1;v<=nodeCount;v++) {
				Vertex vert=packDCEL.vertices[v];
				HalfLink spokes=vert.getEdgeFlower();
				Iterator<HalfEdge> sis=spokes.iterator();
				while (sis.hasNext()) {
					HalfEdge he=sis.next();
					int w=he.twin.origin.vertIndx;
					if (v<w) {
						double ivd = xyz_inv_dist(xyz_list[v], xyz_list[w], rad, rad);
						he.setInvDist(ivd);
						ivdmax = (ivd > ivdmax) ? ivd : ivdmax;
						ivdmin = (ivd < ivdmin) ? ivd : ivdmin;
					}
				}
			}
			CirclePack.cpb.msg("Set overlaps using "
					+ "euclidean 3-space distances.\n"
					+ "  side lengths: max = " + maX + ", max/min = "
					+ (maX / miN) + "\n" + "  radius " + rad + "\n"
					+ "  inv distance: max = " + ivdmax + ", min = " + ivdmin);
			return 1;
		}
		if (flag == 2) { /*
							 * set as though each vertex v radius was 1/2 the
							 * minimum eldg length from v.
							 */
			// compute temporary radii
			double[] tmprads = new double[nodeCount + 2];
			for (int i = 1; i <= nodeCount; i++) {
				miN = 100000000;
				int[] petals=getPetals(i);
				int k;
				for (int j = 0; j < petals.length; j++) {
					if ((k = petals[j]) != 0 && i <= nodeCount
							&& k <= nodeCount) {
						double dist = Math.sqrt((xyz_list[i].x - xyz_list[k].x)
								* (xyz_list[i].x - xyz_list[k].x)
								+ (xyz_list[i].y - xyz_list[k].y)
								* (xyz_list[i].y - xyz_list[k].y)
								+ (xyz_list[i].z - xyz_list[k].z)
								* (xyz_list[i].z - xyz_list[k].z));
						miN = (dist < miN) ? dist : miN;
					}
				}
				tmprads[i] = miN / 2.0;
			}
			// compute and store overlaps
			for (int v=1;v<=nodeCount;v++) {
				Vertex vert=packDCEL.vertices[v];
				HalfLink spokes=vert.getEdgeFlower();
				Iterator<HalfEdge> sis=spokes.iterator();
				while (sis.hasNext()) {
					HalfEdge he=sis.next();
					int w=he.twin.origin.vertIndx;
					if (v<w) {
						double ivd =xyz_inv_dist(xyz_list[v], xyz_list[w],
								tmprads[v], tmprads[w]); 
						he.setInvDist(ivd);
						ivdmax = (ivd > ivdmax) ? ivd : ivdmax;
						ivdmin = (ivd < ivdmin) ? ivd : ivdmin;
					}
				}
			}
			CirclePack.cpb.msg("Set overlaps using "
					+ "euclidean 3-space distances and for each vert v, \n"
					+ "  use 1/2 the length of the shortest edge from v .\n"
					+ "  inv distances: max = " + ivdmax + ", min = " + ivdmin);
			return 1;
		}
		return 0;
	}

	  /**
		 * "Euclidean" scaling is applied. In the hyperbolic case, some circles
		 * could be forced outside unit disc, in which case they're pushed back
		 * in as horocycles. In the spherical case, apply z->t*z.
		 */
	  public int eucl_scale(double factor) {
		CircleSimple sc = null;
		boolean hyp_out = false;

		if (hes < 0) { // hyp
			for (int i = 1; i <= nodeCount; i++) {
				sc = HyperbolicMath.h_to_e_data(getCircleSimple(i));
				sc = HyperbolicMath.e_to_h_data(sc.center.times(factor),
						sc.rad*factor);
				if (sc.flag == 0)
					hyp_out = true; // circle was forced into disc
				setCenter(i,sc.center);
				setRadius(i,sc.rad);
			}
			if (packDCEL.redChain != null) {
				RedEdge rtrace = packDCEL.redChain;
				do {
					sc = HyperbolicMath.h_to_e_data(rtrace.getCenter(),
							rtrace.getRadius());
					sc = HyperbolicMath.e_to_h_data(sc.center.times(factor),
							sc.rad*factor);
					rtrace.setCenter(sc.center);
					rtrace.setRadius(sc.rad);
					rtrace = rtrace.nextRed;
				} while (rtrace != packDCEL.redChain); 
			}
			fillcurves();
		} else {   // deBugging.LayoutBugs.log_RedCenters(this);
			Mobius Mob = new Mobius();
			Mob.a.x = factor;
			NodeLink vlist = new NodeLink(this, "a");
			apply_Mobius(Mob, vlist);
		}
		if (hyp_out) {
			CirclePack.cpb.msg("eucl_scale: hyperbolic "
					+ "circle(s) were pushed back " + "into the disc");
		}
		return 1;
	}

	  /**
		 * Rotate pack p by given angle. Note that radii
		 * don't change. The redvert data and side pairing
		 * data are updated as well.
		 * @param ang double, in radians
		 * @return 1
		 */
	  public int rotate(double ang) {
		  Mobius mob=Mobius.rotation(ang/Math.PI);
		  NodeLink vertlist=new NodeLink(this,"a");
    	  return apply_Mobius(mob,vertlist,true,true,true);
	  } 

	  /**
	   * Top level 'adjoin' call. 
	   *  * test for legality
	   *  * adjoin via DCEL
	   *  * fix up results such as 'vlist', 'elist'.
	   * Returned 'PackData' should be fully processed. 
	   * 'PackExtender's and some lists will be lost.
	   * 
	   * @param p1 PackData
	   * @param p2 PackData (may equal p1)
	   * @param v1 int
	   * @param v2 int
	   * @param n int
	   * @return new PackData, null or exception on error
	   */
	  public static PackData adjoinCall(PackData p1,
			  PackData p2,int v1,int v2,int n) {
		  boolean debug=false;
		  PackData newPack=null;

		  boolean selfadjoin=false;
		  if (p1==p2)
			  selfadjoin=true;
		  else { // make enough space
			  int sze = p1.nodeCount+p2.nodeCount+10;
    		  p1.packDCEL.alloc_vert_space(sze,true);
		  }
		  
		  // minimal legality test; later calls check further
		  if ( !p1.status || !p2.status ||  
				  v1<=0 || v2<=0 || v1>p1.nodeCount || v2>p2.nodeCount ||
			      (n>0 && p1.nodeCount<n) || (n>0 && p2.nodeCount<n)) 
			  throw new ParserException("'adjoin' usage: data problem");
		  
		  // create 'newPack'
		  PackDCEL pdc1=CombDCEL.cloneDCEL(p1.packDCEL);
		  PackDCEL pdc2=p2.packDCEL;
		  if (!selfadjoin) {
			  pdc2=CombDCEL.cloneDCEL(p2.packDCEL);
		  }
		  else
			  pdc2=pdc1;
			  
		  // here's the main call.
		  PackDCEL newDCEL=CombDCEL.adjoin(pdc1, pdc2, v1, v2, n);
		  VertexMap oldnew=newDCEL.oldNew;
		  CombDCEL.redchain_by_edge(newDCEL, null, null, selfadjoin);
		  CombDCEL.fillInside(newDCEL);
		  newDCEL.oldNew=oldnew;
		  newPack=new PackData(null);
		  newPack.attachDCEL(newDCEL);
		  PackDCEL pdcel=newPack.packDCEL;
		  newPack.vertexMap=newDCEL.oldNew;
			  
		  if (debug) // debug=true;
			   DCELdebug.printRedChain(pdcel.redChain);

		  // copy the 'p1' info?
   		  if (selfadjoin) 
   			  for (int v=1;v<=pdcel.vertCount;v++) {
   				  pdcel.vertices[v].cloneData(pdc1.vertices[v]);
   			  }
   		  else {
   			  // all 'p1' vertices should still be there, same indices
   			  for (int v=1;v<=p1.nodeCount;v++) {
   				  pdcel.vertices[v].cloneData(pdc1.vertices[v]);
   			  }
   			  // rest are from 'p2'.
   			  for (int v=p1.nodeCount+1;v<=pdcel.vertCount;v++) {
   				  Vertex vert=newPack.packDCEL.vertices[v];
   				  // 'old2v' is original index in 'qackData'
   				  int old2v=newPack.vertexMap.findV(v); 
//System.out.println("<old2v,v>=<"+old2v+","+v+">");    	
   				  vert.cloneData(pdc2.vertices[old2v]);
   			  }
   		  }
    		  
   		  // get red cent/rad from vData
   		  if (pdcel.redChain!=null) {
   			  RedEdge rtrace=pdcel.redChain;
   			  do {
   				  int v=rtrace.myEdge.origin.vertIndx;
   				  rtrace.setCenter(new Complex(newPack.packDCEL.vertices[v].center));
   				  rtrace.setRadius(newPack.packDCEL.vertices[v].rad);
   				  rtrace=rtrace.nextRed;
   			  } while (rtrace!=pdcel.redChain);
   		  }

		  // restablish certain things
		  if (selfadjoin && p1.vlist!=null 
				  && newPack.vertexMap!=null && newPack.vertexMap.size()>0) {
			  newPack.vlist=NodeLink.translate(p1.vlist,newPack.vertexMap);
		  }
		  // fix up elist
		  if (selfadjoin && p1.elist!=null 
				  && newPack.vertexMap!=null && newPack.vertexMap.size()>0) {
			  newPack.elist=EdgeLink.translate(p1.elist,newPack.vertexMap);
		  }

		  // throw out certain things
		  newPack.flist=null;
		  newPack.glist=null;
		  newPack.hlist=null;
		  newPack.xyzpoint=null;
		  newPack.packExtensions=new Vector<PackExtender>();
		  
		  // update
		  newPack.set_aim_default();
		  newPack.fillcurves();
		  newPack.status=true;
		  return newPack;
	  }

	  /**
	   * Return schwarzian for given halfedge
	   * @param es HalfEdge
	   * @return double, 
	   */
	  public double getSchwarzian(HalfEdge he) { // given <v,w>
		  if (he==null)
			  throw new DataException("schwarzian failure: "+
					  "edge was null");
		  return he.getSchwarzian();
	  }

	  /**
	   * Store schwarzian for EdgeSimple <v,w>
	   * @param edge EdgeSimple
	   * @param sch double
	   * @return int 1 on success
	   */
	  public int setSchwarzian(HalfEdge edge,double sch) {
		  edge.setSchwarzian(sch);
		  edge.twin.setSchwarzian(sch);
		  return 1;
	  }
	  
	  /**
	   * Return the inversive distance recorded for edge from
	   * v to nghb w. 1.0 is default.
	   * @param v int
	   * @param w int
	   * @return double
	   */
	  public double getInvDist(int v,int w) {
		  HalfEdge he=packDCEL.findHalfEdge(v,w);
		  if (he!=null)
			  return he.getInvDist();
		  throw new CombException(
			  "invDist error: <"+v+","+w+"> is not a valid edge");
	  }
	  
	  /** 
	   * Fill a 'RadIvdPacket' with data for this face.
	   * @param f int
	   * @return new RadIvdPacket
	   */
	  public RadIvdPacket getRIpacket(int f) {
		  RadIvdPacket rip=new RadIvdPacket();
		  HalfEdge he=packDCEL.faces[f].edge;
		  int k=0;
		  do {
			  rip.rad[k]=getRadius(he.origin.vertIndx);
			  rip.oivd[k]=he.next.getInvDist();
			  k++;
		  } while(k<3);
		  return rip;
	  }
	  
	  /** 
	   * Store one legal inversive distance value for edge and
	   * its twin. Note: 'invDist' values: deep overlap in (-1,0); 
	   * normal overlap in [0,1]; separated circles in (1,infty); 
	   * tangency is 1.
	   * Note: if 'invDist' lies in [-1,1] then intersecting 
	   * circles overlap with angle acos(invDist). If separated, 
	   * 'invDist' lies in [1,infty). 
	   * @param v int 
	   * @param w int 
	   * @param invDist double, lies in [-1,infty)
	   * @return int, 1 on success
	   */
	  public int set_single_invDist(int v,int w,double invDist) {
		  HalfEdge he=packDCEL.findHalfEdge(v,w);
		  if (he==null)
	    	throw new ParserException(
	    			"set_single_overlap error: "+w+" is not a petal of "+v);
		  he.setInvDist(invDist);
		  he.twin.setInvDist(invDist);
		  return 1;
	  }
	    
	  /** 
	   * Return vertices defining an edge path proceeding from 'v1'
	   * and taken from the given 'vertlist'. Currently two methods:
	   * + flag=0: multiple-choice (default: for multi-sheeted surfaces):
	   *   return as many successive neighbors as possible.
	   * + flag=1: return successive neighbors described in edge 'turns' 
	   *   in combinatoric direction. 
	   * First in vertlist must be a neighbor of v1, then succession 
	   * of increments.  E.g., "path_c -i 23 57 2 4 0 3 3" will start 
	   * with edge {23,57}, then {57,x}, where in the flower for 57, 
	   * {57,x} is "2" edges counterclockwise from {57,23}, then "4" 
	   * edges around x, then "0" (back up), etc.  In a hex packing, 
	   * "path_c -i 23 57 3 3 3 ..." defines a combinatorial straight 
	   * path.
	   * @param mode int
	   * @param v1 int
	   * @param vertlist NodeLink
	   * @return NodeLink 
	  */
	  public NodeLink path_construct(int mode,int v1,NodeLink vertlist) {
	    int v,count=0,cur_vert;

	    NodeLink nlist=new NodeLink(this);

	    cur_vert=v1;
	    Iterator<Integer> vlist=vertlist.iterator();
	    if (mode==0) { // pick off successive neighbors
	        while (vlist.hasNext()) {
	  	  v=(Integer)vlist.next();
	  	  if (nghb(cur_vert,v)>=0) {
	  	      nlist.add(v);
	  	      cur_vert=v;
	  	      count++;
	  	  }
	        }
	    }
	    else if (mode==1) { // first must nghb v1, sets comb direction.
	    	int inc,dir,deg;
	        int v2=(Integer)vlist.next();
	        if ((dir=nghb(v2,v1))<0) throw new ParserException();
	        nlist.add(v2); // put v2 in the new list
	        cur_vert=v2;
	        while (vlist.hasNext()) {
	        	inc=(Integer)vlist.next();
	        	deg=countFaces(cur_vert);
	        	int ndir=dir+inc; // intended new direction

	        	// at bdry? there are limits on ndir
	        	if (isBdry(cur_vert) && (ndir<0 || ndir>deg)) {
	        		if (count>0) return nlist;
	        		return null;
	        	}
	        	ndir=ndir % countFaces(cur_vert);
	        	int nvert=getPetal(cur_vert,ndir);
	        	if ((dir=nghb(nvert,cur_vert))<0) // comb error
	        		throw new CombException();
	        	nlist.add(nvert);
	        	count++;
	        	cur_vert=nvert;
	        }
	    }
	    if (count>0) return nlist;
	    return null;
	  }

	  /**
	   * If {v,w} is an edge between vertices, this returns the
	   * dual edge {f,g} of neighboring faces to left/right of 
	   * {v,w}, respectively. Note, dual edge direction is 
	   * clockwise of original edge.
	   * @param v int
	   * @param w int
	   * @return EdgeSimple, null on error
	   */
	  public EdgeSimple dualEdge(int v,int w) {
		  if (nghb(v,w)<0) return null;
		  int fr=face_right_of_edge(v,w);
		  int fl=face_right_of_edge(w,v);
		  if (fl>0 && fr>0 && fr!=fl) {
			  return new EdgeSimple(fl,fr);
		  }
		  return null;
	  }
	  
	  /**
	   * Returns GraphLink of face pairs {f,g} sharing given 
	   * edges {v,w} from input elist. Note orientation: f is 
	   * to left of directed edge {v w}, g is to right. Skip 
	   * edges lying in only one face.
	   * @param elist EdgeLink
	   * @return GraphLink, null on error or if 'elist' is empty
	   */
	  public GraphLink dualEdges(EdgeLink elist) {
		  GraphLink dlink=new GraphLink();
		  if (elist==null || elist.size()==0) 
			  return null;
		  Iterator<EdgeSimple> est=elist.iterator();
		  EdgeSimple edge=null;
		  while (est.hasNext()) {
			  edge=est.next();
			  EdgeSimple dedge=dualEdge(edge.v,edge.w);
			  if (dedge!=null) 
				  dlink.add(dedge);
		  }
		  return dlink;
	  }
	  
	  /**
	   * Find total of length of dual edges in given 'delist'.
	   * @param delist EdgeLink, dual edges
	   * @return double
	   */
	  public double dualLength(EdgeLink delist) {
		  double totlen=0.0;
		  Iterator<EdgeSimple> eit = delist.iterator();
		  while (eit.hasNext()) {
			  Complex[] pts=ends_dual_edge(eit.next());
			  if (hes>0) {
				  totlen += SphericalMath.s_dist(pts[0],pts[1]);
			  }
			  else if (hes<0) {
				  totlen += HyperbolicMath.h_dist(pts[0],pts[1]);
			  }
			  else {
				  totlen += pts[1].minus(pts[0]).abs();
			  }
		  }
		  return totlen;
	  }
	  
	  /**
	   * Given faces f and g, if they share an edge,
	   * return dual edge {v,w}, where f is to left of
	   * {v,w} and g is to the right. (dualEdge)
	   * @param f int
	   * @param g int
	   * @return EdgeSimple {v,w}, null on error
	   */
	  public EdgeSimple reDualEdge(int f,int g) {
		  if (f<1 || g<1 || f>faceCount || g>faceCount)
			  return null;
		  combinatorics.komplex.DcelFace fface=packDCEL.faces[f];
		  HalfEdge he=fface.faceNghb(packDCEL.faces[g]);
		  if (he==null)
			  return null;
		  int v=he.origin.vertIndx;
		  int w=he.twin.origin.vertIndx;
		  return new EdgeSimple(v,w);
	  }
	  
	  /**
	   * Returns 'EdgeLink' of edges {v,w} dual to given list
	   * of dual edge. Note orientation: given dual edge {f,g}
	   * from face f to face g, return {v, w} which is positively
	   * oriented vis-a-vis f.
	   * Skip pairs {f,g} where f=0 ('root' type link) or where
	   * f and g don't share an edge.
	   * @param dlist @see GraphLink
	   * @return @see EdgeLink, null on error or if 'dlist' is empty
	   */
	  public EdgeLink reDualEdges(GraphLink dlist) {
		  EdgeLink elink=new EdgeLink(this);
		  if (dlist==null || dlist.size()==0) return null;
		  Iterator<EdgeSimple> dst=dlist.iterator();
		  EdgeSimple edge=null;
		  while (dst.hasNext()) {
			  edge=dst.next();
			  elink.add(reDualEdge(edge.v,edge.w));
		  }
		  return elink;
	  }
  
	 /**  
 	  * Flattening jagged edge of hex packing by introducing 
	  * special overlaps. Each v must be bdry vert, have 3 nghbs.
	  * Introduces 2*pi/3 overlap with interior neighbor and pi/3
	  * overlap with each bdry nghb. Results in flat edge at v.
	  * @param vertlist @see NodeLink
	  * @return int, count or 0 on error 
	 */
	 public int flat_hex(NodeLink vertlist) {
		 int count = 0;

		 Iterator<Integer> vlist = vertlist.iterator();
		 while (vlist.hasNext()) {
			 Vertex vert = packDCEL.vertices[vlist.next()];
			 if (vert.isBdry() && vert.getNum() == 2) {
				HalfLink spokes=vert.getEdgeFlower();
				spokes.get(0).setInvDist(.5);
				spokes.get(1).setInvDist(-.5);
				spokes.get(2).setInvDist(.5);
				count++;
			 }
		 }
		 return count;
	 }
	 
	 /** 
	  * "Square grid" packings typically have (interior) vertices 
	  * of degrees 4 and 8, and the "ball bearings", those of 
	  * degree 4, have overlaps of angle pi/2 with their neighbors.
	  * This command simply sets pi/2 overlaps for all interior circles
	  * of degree 4 (whether packing has square grid combinatorics or not).
	  * @return int count
	 */
	 public int sq_grid_overlaps() {
	   int count=0;
	   double mcp2=Math.cos(Math.PI/2.0);

	   if (!status) 
		   return count;
	   for (int v=1;v<=nodeCount;v++) {
	     if (!isBdry(v) && countFaces(v)==4) {
	    	 HalfLink spokes=packDCEL.vertices[v].getSpokes(null);
	    	 Iterator<HalfEdge> sis=spokes.iterator();
	    	 while (sis.hasNext()) {
	    		 HalfEdge he=sis.next();
	    		 he.setInvDist(mcp2);
	    	 }
	     }
	   }
	   return count;
	 } 

	 /**
	  * If hyperbolic, apply a Mobius trans of disc putting ctr 
	  * at origin. If euclidean, translate. If sphere, rigid 
	  * Mobius moves ctr to north pole.
	  * @param ctr Complex
	  * @return int
	  */
	public int center_point(Complex ctr) {
		Complex z1, z2;
		Complex z3 = new Complex(0.0);

		if (hes<0) {
			if (ctr.absSq() > (1.0 - TOLER)) {
				flashError("usage: center_point: chosen point is too "
						+ "close to or beyond the ideal boundary.");
				return 0;
			}
			for (int v = 1; v <= nodeCount; v++) {
				Vertex vert=packDCEL.vertices[v];
				// catch horocycles to modify their radii
				double radius = (-1)*vert.rad;
				if (radius > 0) { // yes, a horocycle
					z1 = vert.center;
					z2 = z1.times(1.0 - 2.0 * radius);
					z3.x = (1 - radius) * z1.x - radius * z1.y;
					z3.y = (1 - radius) * z1.y + radius * z1.x;
					z1 = Mobius.mob_trans(z1, ctr);
					z2 = Mobius.mob_trans(z2, ctr);
					z3 = Mobius.mob_trans(z3, ctr);
					CircleSimple sc = EuclMath.circle_3(z1, z2, z3);
					vert.rad=-sc.rad;
				}
				vert.center=Mobius.mob_trans(vert.center, ctr).times(-1.0);
			}
			if (packDCEL.redChain != null) { // adjust centers in red list
				RedEdge rtrace=packDCEL.redChain;
				do {
					double radius=-(rtrace.getRadius());
					// again, catch horocycles
					if (radius>0) {
						z1 = rtrace.getCenter();
						z2 = z1.times(1.0 - 2.0 * radius);
						z3.x = (1 - radius) * z1.x - radius * z1.y;
						z3.y = (1 - radius) * z1.y + radius * z1.x;
						z1 = Mobius.mob_trans(z1, ctr);
						z2 = Mobius.mob_trans(z2, ctr);
						z3 = Mobius.mob_trans(z3, ctr);
						CircleSimple sc = EuclMath.circle_3(z1, z2, z3);
						rtrace.setRadius(-sc.rad);
					}
					Complex tmpctr=Mobius.mob_trans(rtrace.getCenter(), ctr);
					rtrace.setCenter(tmpctr.times(-1.0));
					rtrace = rtrace.nextRed;
				} while (rtrace!=packDCEL.redChain);
			}
		} 
		
		// TODO: what's to happen in the case of the sphere?
		//    (Note; there may still be red chain)
		else if (hes>0) { // sph
			Matrix3D m3d=Matrix3D.rigid2North(ctr);
			for (int v = 1; v <= nodeCount; v++) {
				Point3D pt=Matrix3D.times(m3d,new Point3D(getCenter(v)));
				setCenter(v,Point3D.p3D_2_sph(pt)); // pt.norm();
			}
		}
		else { // eucl
			for (int v = 1; v <= nodeCount; v++) {
				packDCEL.vertices[v].center=
						packDCEL.vertices[v].center.minus(ctr);
			}
			if (packDCEL.redChain != null) {
				RedEdge rtrace=packDCEL.redChain;
				do {
					rtrace.setCenter(rtrace.getCenter().minus(ctr));
					rtrace = rtrace.nextRed;
				} while (rtrace!=packDCEL.redChain);
			}
		}
		if (packDCEL.pairLink != null)
			// fix side-pairing mobius transforms also
			packDCEL.updatePairMob();
		return 1;
	}
	
	/** 
	 * Set given center for given vertices
	 */
	public int set_centers(Complex ctr,NodeLink vertlist) {
	  if (vertlist==null || vertlist.size()==0) return 0;
	  Iterator<Integer> vlist=vertlist.iterator();
	  int v;
	  while(vlist.hasNext()) {
	      v=(Integer)vlist.next();
	      setCenter(v,ctr);
	  }
	  return 1;
	}

	/** 
	 * Create 'TriAspect' face data, which contains data face-by-face
	 * for use, e.g., in 'Schwarzian' and 'ProjStruct'. Set the tanPts.
	 * This does not change data in the packing itself.
	 * @param p PackData
	 * @return int count 
	 */
	public static TriAspect[] getTriAspects(PackData p) {
		PackDCEL pdcel=p.packDCEL;
		TriAspect[] aspect=new TriAspect[p.faceCount+1];
		Iterator<HalfEdge> his=pdcel.fullOrder.iterator();
		int count=0;
		while (his.hasNext()) {
			DcelFace face=his.next().face;
			TriAspect ta=aspect[face.faceIndx]=new TriAspect(pdcel,face);
			// compute/store the tangency points
			DualTri dtri=new DualTri(
					ta.getCenter(0),
					ta.getCenter(1),
					ta.getCenter(2),p.hes);
			ta.tanPts=new Complex[3];
			for (int j=0;j<3;j++)
				ta.tanPts[j]=new Complex(dtri.TangPts[j]);
			count++;
		}

/* OBE???		
		int count=1; 
		boolean ivds=p.haveInvDistances();
		boolean schws=p.haveSchwarzians();
		TriAspect[] aspect=new TriAspect[p.faceCount+1];
		
		// assume first face is in place
		HalfEdge he=pdcel.fullOrder.get(0); // should be 'alpha'
		int next_face=he.face.faceIndx;
		int last_face=next_face;
		int[] mv=pdcel.faces[next_face].getVerts();
		aspect[next_face]=new TriAspect(p.hes);
		aspect[next_face].faceIndx=next_face;
		aspect[next_face].vert=mv;
		aspect[next_face].allocCenters();
		
		aspect[next_face].setCenter(new Complex(p.getCenter(mv[0])),0);
		aspect[next_face].setCenter(new Complex(p.getCenter(mv[1])),1);
		aspect[next_face].setCenter(new Complex(p.getCenter(mv[2])),2);
		aspect[next_face].setRadius(p.getRadius(mv[0]),0);
		aspect[next_face].setRadius(p.getRadius(mv[1]),1);
		aspect[next_face].setRadius(p.getRadius(mv[2]),2);
		
		if (ivds || schws) {
			he=pdcel.fullOrder.get(0);
			int tick=0;
			do {
				if (ivds)
					aspect[next_face].setInvDist(tick,he.getInvDist());
				if (schws)
					aspect[next_face].schwarzian[tick]=he.getSchwarzian();
				he=he.next;
				tick++;
			} while (he!=pdcel.alpha);
		}
		
		// compute/store the tangency points
		DualTri dtri=new DualTri(
				aspect[next_face].getCenter(0),
				aspect[next_face].getCenter(1),
				aspect[next_face].getCenter(2),p.hes);
		aspect[next_face].tanPts=new Complex[3];
		for (int j=0;j<3;j++)
			aspect[next_face].tanPts[j]=new Complex(dtri.TangPts[j]);

		Iterator<HalfEdge> elist = p.packDCEL.fullOrder.iterator();
		elist.next(); // first entry already used
		while (elist.hasNext()) {
			HalfEdge edge = elist.next();
			last_face=edge.twin.face.faceIndx;
			next_face=edge.face.faceIndx;
				
			// create TriAspect 
			aspect[next_face]=new TriAspect(p.hes);
			aspect[next_face].faceIndx=next_face;
			mv=pdcel.faces[next_face].getVerts();
			aspect[next_face].vert=mv;
			aspect[next_face].allocCenters();
			aspect[next_face].radii=new double[3];

			// store any inv distances or schwarzians
			if (ivds || schws) {
				he=edge.face.edge;
				int tick=0;
				do {
					if (ivds)
						aspect[next_face].setInvDist(tick,he.getInvDist());
					if (schws)
						aspect[next_face].schwarzian[tick]=he.getSchwarzian();
					he=he.next;
					tick++;
				} while (he!=edge.face.edge);
			}

			// set rad/centers of next_face based on last_face
			int v=edge.origin.vertIndx;
			int last_indx=aspect[last_face].vertIndex(v);
			int next_indx=aspect[next_face].vertIndex(v);
			// get data for v
			aspect[next_face].setCenter(
					aspect[last_face].getCenter(last_indx),next_indx);
			aspect[next_face].setRadius( 
					aspect[last_face].getRadius(last_indx),next_indx);
			// get data for other end of 'edge'
			aspect[next_face].setCenter( // other end of 'edge'
					aspect[last_face].getCenter((last_indx+2)%3),
					(next_indx+1)%3);
			aspect[next_face].setRadius(
					aspect[last_face].getRadius((last_indx+2)%3),
					(next_indx+1)%3);
			// get radius for opposite vertex, compute center
			aspect[next_face].setRadius(
					aspect[last_face].getRadius((last_indx+1)%3),
					(next_indx+2)%3);

			// compute new center for aspect for next_face
			CircleSimple sc;
			Complex zv=aspect[last_face].getCenter(last_indx);
			double rv=aspect[last_face].getRadius(last_indx);
			Complex zw=aspect[last_face].getCenter((last_indx+2)%3);
			double rw=aspect[last_face].getRadius((last_indx+2)%3);
			double ropp=aspect[next_face].getRadius((next_indx+2)%3);
			if (ivds) { // get inv distances
				double ivd0=aspect[next_face].getInvDist(next_indx);
				double ivd1=aspect[next_face].getInvDist((next_indx+1)%3);
				double ivd2=aspect[next_face].getInvDist((next_indx+2)%3);
				sc = CommonMath.comp_any_center(zv,zw,rv,rw,ropp,
						ivd0,ivd1,ivd2,p.hes);
			} 
			else {
				sc = CommonMath.comp_any_center(zv,zw,rv,rw,ropp,p.hes);
			}
			aspect[next_face].setCenter(sc.center,(next_indx+2)%3);
			
			// compute/store the tangency points
			dtri=new DualTri(
				aspect[next_face].getCenter(0),
				aspect[next_face].getCenter(1),
				aspect[next_face].getCenter(2),p.hes);
			aspect[next_face].tanPts=new Complex[3];
			for (int j=0;j<3;j++)
				aspect[next_face].tanPts[j]=new Complex(dtri.TangPts[j]);
			
			count++;
		} // end of while through edgelist
*/		
		
		if (count!=p.faceCount) {
			throw new CombException(
					"didn't get all faces in building 'TriAspect'");
		}
		return aspect;
	}

	/**
	 * Does the work for the "get_data" and "put_data" calls, 
	 * depending on 'putget' flag. If 'put', then data goes 
	 * from 'this' to 'q'; 'get' goes from 'q' to 'this'. 
	 * Caution: 'translate' true means the 'VertexMap' of 'this' 
	 * is used to associate source with target. So use of 
	 * 'put'/'get' with translation depends on who has the
	 * appropriate 'VertexMap'.
	 * TODO: multiple translations? we currently only use the first
	 * @param q PackData, other packing
	 * @param flagsegs Vector<Vector<String>>, vector of vectors of strings
	 * @param putget boolean, true==>'put' from 'this' to q, false==>'get' from q to 'this'
	 * @param transl boolean, true==>'translate' using VertexMap of 'this' 
	 * @return int = count of actions taken.
	 */
	public int dataPutGet(PackData q,Vector<Vector<String>> flagsegs,
			boolean putget,boolean transl) {

		if (q==null || !q.status)
			throw new ParserException(
				"'get/put_data': target packing null or 'status' false");
		
		// look first for '-t', '-v', and '-V' flags
		boolean transflag=false;
		boolean vflag=false;
		boolean Vflag=false;
		Iterator<Vector<String>> its=flagsegs.iterator();
		while (its.hasNext()) {
			String str=its.next().get(0);
			if (str.startsWith("-t")) // usually this should occur first
				transflag=true;
			else if (str.startsWith("-v") || str.startsWith("-c"))
				vflag=true;
			else if (str.startsWith("-V"))
				Vflag=true;
		}
		
		if (transflag && vertexMap==null) {
			flashError("usage: get/put_data: -t flag set but active packing p"+
					this.packNum+" has no 'vertexMap'.");
			return 0;
		}

		// vflag and Vflag cases: copying/composing vertexMap's
		if (vflag && transflag && putget) { // copy 'this.vertexMap' to q
			if (vertexMap==null || vertexMap.size()==0) {
				flashError("this packing did not have a vertex map");
				return 0;
			}
			q.vertexMap=vertexMap.makeCopy();
			return q.vertexMap.size();
		}
		if (vflag && transflag && !putget) { // copy from q.vertexMap to 'this'
			if (q.vertexMap==null || q.vertexMap.size()==0) {
				flashError("second packing did not have a vertex map");
				return 0;
			}
			vertexMap=q.vertexMap.makeCopy();
			return vertexMap.size();
		}
		if (vflag && transflag && putget) { // compose: 'q.vertexMap' followed by 'vertexMap'
			VertexMap tvm=Translators.composeVMs(q.vertexMap,false,vertexMap,false);
			if (tvm==null || tvm.size()==0) {
				flashError("failed to compose vertex maps");
				return 0;
			}
			q.vertexMap=tvm;
			return tvm.size();
		}
		if (vflag && transflag && !putget) { // compose: 'vertexMap' followed by 'q.vertexMap'
			VertexMap tvm=Translators.composeVMs(vertexMap,false,q.vertexMap,false);
			if (tvm==null || tvm.size()==0) { 
				flashError("failed to compose vertex maps");
				return 0;
			}
			vertexMap=tvm;
			return tvm.size();
		}
		if (Vflag && putget) { // compose
			VertexMap tvm=Translators.composeVMs(q.vertexMap,false,vertexMap,true);
			if (tvm==null || tvm.size()==0) { 
				flashError("failed to compose vertex maps");
				return 0;
			}
			q.vertexMap=tvm;
			return tvm.size();
		}
		if (Vflag && !putget) { // compose
			VertexMap tvm=Translators.composeVMs(vertexMap,false,q.vertexMap,true);
			if (tvm==null || tvm.size()==0) { 
				flashError("failed to compose vertex maps");
				return 0;
			}
			vertexMap=tvm;
			return tvm.size();
		}

		// which way are things going?
		VertexMap vMap=vertexMap;
		PackData source_p=this;
		PackData target_p=q;
		if (!putget) {
			source_p=q;
			target_p=this;
			if (vertexMap!=null && vertexMap.size()>0)
				vMap=vertexMap.flipEachEntry();
		}
		int count=0;
		
		// 'flagSeg's as with commands: process in succession
		
		// circle-related (followed by {v..})
		//  * -z  centers (same as -cz)
		//  * -r  radii   (same as -cr)
		//  * -a  aims    (same as -ca)
		//  * -c[zrcma]  multiple data types: c = color, m = mark
		
		// face-related (followed by {f..})
		//  * -f[mc]  mark and/or color
		//  NOTE: use face's vert triples rather than indices.
		
		// edge-related (followed by {v w..})
		//  * -e[mcis]  mark, color, inv distance, and/or schwarzian
		
		NodeLink vlink=null;
		FaceLink flink=null;
		HalfLink hlink=null;
		boolean cr=false;
		boolean cz=false;
		boolean ca=false;
		
		its=flagsegs.iterator();
		while (its.hasNext()) {
			Vector<String> items=its.next();
			String flag=items.remove(0);
				// must be a flag 
			// Note: on failure, previous actions remain in place
			if (!StringUtil.isFlag(flag)) 
			throw new ParserException("'put/get_data' flag missing");
			
			// next get the appropriate list, "a" by default
			char c=flag.charAt(1);
			switch(c) {
			case 'f': // face-related
			{
				if (items.size()==0)
					flink=new FaceLink(this,"a");
				else
					flink=new FaceLink(this,items);
				break;
			}
			case 'e': // 
			{
				if (items.size()==0)
					hlink=new HalfLink(this,"a");
				else
					hlink=new HalfLink(this,items);
				break;
			}
			case 'z': // fall through
			case 'r': // fall through
			case 'a': // fall through
			case 'c':
			{
				if (items.size()==0)
					vlink=new NodeLink(this,"a");
				else
					vlink=new NodeLink(this,items);
				break;
			}
			} // end of switch for list
			
			// now for the action
			switch(c) {
			case 'f':
			{
				boolean fc=false;
				boolean fm=false;
				if (flag.contains("c"))
					fc=true;
				if (flag.contains("m"))
					fm=true;
				if (!fm && !fc) {
					flashError("face flag without c or m");
						return count;
				}
				
				Iterator<Integer> fis=flink.iterator();
				while(fis.hasNext()) {
					int findx=fis.next();
					int nf=0;
					try {
						if ((nf=Translators.face_trans(source_p,
								target_p,findx,vMap))>0) {
							if (fc) {
								Color col=source_p.getFaceColor(findx);
								target_p.setFaceColor(nf,ColorUtil.cloneMe(col));
								count++;
							}
							if (fm) { 
								target_p.setFaceMark(nf,source_p.getFaceMark(findx));
								count++;
							}
						}
					} catch (Exception ex) {}
				} // end of while through faces
				break;
			} // end of face case					
			case 'e': // edges
			{
				boolean ec=false;
				boolean em=false;
				boolean ei=false;
				boolean es=false;
				boolean ez=false;
				
				if (flag.contains("c"))
					ec=true;
				if (flag.contains("m"))
					em=true;
				if (flag.contains("i"))
					ei=true;
				if (flag.contains("s"))
					es=true;
				if (flag.contains("z"))  
					ez=true;
				if (flag.contains("c"))
					ec=true;
				
				Iterator<HalfEdge> his=hlink.iterator();
				while (his.hasNext()) {
					HalfEdge he=his.next();
					int tv=he.origin.vertIndx;
					int tw=he.twin.origin.vertIndx;
					if (transflag) {
						tv=vMap.findW(tv);
						tw=vMap.findW(tw);
					}
					HalfEdge the=target_p.packDCEL.findHalfEdge(new EdgeSimple(tv,tw));
					if (the!=null) {
						if (ec) {
							the.setColor(he.getColor());
							count++;
						}
						if (em) {
							the.setMark(he.getMark());
							count++;
						}
						if (ei) {
							the.setInvDist(he.getInvDist());
							count++;
						}
						if (es) {
							the.setSchwarzian(he.getSchwarzian());
							count++;
						}
						if (ez) {
							Complex z=source_p.packDCEL.getVertCenter(he);
							target_p.packDCEL.setCent4Edge(the, z);
						}
					}
				} // end of while through edges
				break;
			} // end of edge case
			case 'z': // centers, fall through
			case 'r': // radii, fall through
			case 'a': // aims, fall through
			case 'c':
			{
				boolean cm=false;
				boolean cc=false;
				if (flag.contains("z"))
					cz=true;
				if (flag.contains("r"))
					cr=true;
				if (flag.contains("m"))
					cm=true;
				if (flag.contains("a"))
					ca=true;
				if (flag.indexOf('c',2)>0)
					cc=true;
				
				Iterator<Integer> vis=vlink.iterator();
				while (vis.hasNext()) {
					int v=vis.next();
					int tv=v;
					if (transflag)
						tv=vMap.findW(v);
					if (cz) {
						target_p.setCenter(tv,source_p.getCenter(v));
						count++;
					}
					if (cr) {
						target_p.setRadius(tv,source_p.getRadius(v));
						count++;
					}
					if (cm) {
						target_p.setVertMark(tv,source_p.getVertMark(v));
						count++;
					}
					if (cc) {
						target_p.setCircleColor(tv,source_p.getCircleColor(v));
						count++;
					}
					if (ca) {
						target_p.setAim(tv,source_p.getAim(v));
						count++;
					}
				} // end of while through vlist
				break;
			}
			default:
			{
				flashError("'get/put_data': unrecognized flag");
				return count;
			}
			} // end of switch
		} // end of while through flags
		return count;
	}

	/**
	 * Reset aims of vlist based on their current angle sum, aim, and specified
	 * factor x. If aim is positive, aim(v)=angle sum(v) + x*[aim(v)-angle sum(v)].
	 * Return count of adjustments.
	 */
	public int scale_aims(double x, NodeLink vlist) {
		int count = 0, v;
		UtilPacket uP = new UtilPacket();

		if (!status || vlist == null || x < 0.0)
			return 0;
		Iterator<Integer> vtrace = vlist.iterator();

		while (vtrace.hasNext()) {
			v = (Integer) vtrace.next();
			Vertex vert = packDCEL.vertices[v];
			if (getAim(v) > 0) {
				double angsum;
				double rad = packDCEL.getVertRadius(vert.halfedge);
				// TODO: use DCEL calls if hes!=0
				if (hes < 0) {
					h_anglesum_overlap(v, rad, uP);
					angsum = uP.value;
				} else if (hes > 0) {
					s_anglesum(v, rad, uP);
					angsum = uP.value;
				} else {
					angsum = packDCEL.getVertAngSum(vert, rad);
				}
				setCurv(v, angsum);
				setAim(v, getCurv(v) - x * (getCurv(v) - getAim(v)));
				count++;
			}
		} // end of while
		return count;
	}

	/** 
	 * Reset radii of p to interpolate between current values and
	 * values in q by factor: 
	     rad_p(v)=rad_p(v) + x*[rad_p(v)-rad_q(v)].
	 *Infinite radii in hyperbolic are treated as value 10.0.
	 *Return count of adjustments.*/
	public int scale_rad(PackData q,double x,NodeLink vertlist) {
	  int v,count=0;
	  double rp,rq;

	  if (!status || !q.status || vertlist==null || vertlist.size()==0 || x < 0.0) return 0;
	  Iterator<Integer> vlist=vertlist.iterator();
	  while (vlist.hasNext()) {
	      v=(Integer)vlist.next();
	      rp=getRadius(v);
	      rq=q.getRadius(v);
	    if (hes<0) {
	      if (rp<0) rp=1-Math.exp(-2.0*10.0);
	      if (rq<0) rq=1-Math.exp(-2.0*10.0);
	    }
	    setRadius(v,rp+x*(rq-rp));
	    count++;
	  }
	  return count;
	} 

	/** 
	 * Try to draw a combinatorial hexagon of side length n, 
	 * starting at corner v, in direction of neighbor w. 
	 * Pretend combinatorics are hex: do 6 n-step edges, and 
	 * left turns. Fill 'vertChain' with indices. Return 
	 * length on success, -length if an edge or other 
	 * combinatoric problem (eg, not enough edges from a vertex) 
	 * is encountered. Success means completing the walk, but 
	 * that does not necessarily mean a return to v. (I think 
	 * the "Berger's" vector of physics is the displacement from the 
	 * center of v to the center of the end vertex.)
	 * @param v int
	 * @param w int, neighboring vertices,
	 * @param n int, n>=1, sidelength
	 * @param vertChain NodeLink, created by calling routine.
	 * @return int length if ran to completion, -length if not, 0 on error
	*/
	public int hexCell(int v,int w,int n,NodeLink vertChain) {
		HalfEdge he=packDCEL.findHalfEdge(new EdgeSimple(v,w));
		if (he==null) 
			return 0;
		vertChain.add(v);
		int count=2;
		int edgecount=0;
		while (edgecount<6) {
			// move along edge
			int step=1;
			while (step<n) {
				HalfEdge nxhe=he.HHleft();
				if (nxhe==null) // hit bdry or small degree
					return -count;
				he=nxhe;
				vertChain.add(he.origin.vertIndx);
				step++;
				count++;
			} // end of while
			
			// turn shallow left
			HalfEdge spoke=he.twin;
			int tick=2;
			do {
				tick--;
				// reached a bdry edge?
				if (spoke.twin.face!=null && spoke.twin.face.faceIndx<0) {
					if (tick==0)
						he= spoke;
					else
						he=null;
				}
				else {
					spoke=spoke.twin.next; // rotate clw
				}
				tick--;
			} while (he!=null && tick>0);
			if (tick==0 && he!=null) { // yes, made the turn
				he=spoke;
				vertChain.add(he.origin.vertIndx);
				count++;
			}
			else
				return -count;
			edgecount++;
		} // done with 6 edges
		if (edgecount<5) // shouldn't happen
			return -count;
		return count;
	}

	/**
	 * When clicking on face in this packing, display it for this canvas 
	 * and also display the associated face in the q canvas. Translate
	 * directly if 'this.nodeCount' and 'q.nodeCount' are equal or if 
	 * 'this.vertexMap' doesn't exist. Else, translate using 'this.vertexMap'.
	 * Note: if there are multiple translations, just draw face based for first
	 * (Hard to see how to handle multiple translations).
	 */
	public int face_map_action(PackData q,int face,boolean trans_flag) {
	    int nface=0;

	    if (face<1 || face>faceCount) return 0;
		// translate
	    try {
	    	if (nodeCount==q.nodeCount || vertexMap==null || !trans_flag)
	    		nface=Translators.face_trans(this,q,face,null);
	    	else nface=Translators.face_trans(this,q,face,this.vertexMap);
	    } catch (Exception ex) {
	    	return 0;
	    }
		if (nface!=0) {
			// draw for this packing
			int[] verts=packDCEL.faces[face].getVerts();
			Complex c0=getCenter(verts[0]);
			Complex c1=getCenter(verts[1]);
			Complex c2=getCenter(verts[2]);
			DispFlags dflags=new DispFlags("f");
			dflags.setColor(getFaceColor(face));
			cpDrawing.drawFace(c0,c1,c2,null,null,null,dflags);   
			// draw other packing
			verts=q.packDCEL.faces[nface].getVerts();
		    c0=q.getCenter(verts[0]);
		    c1=q.getCenter(verts[1]);
		    c2=q.getCenter(verts[2]);
			dflags.setColor(q.getFaceColor(nface));
		    q.cpDrawing.drawFace(c0,c1,c2,null,null,null,dflags);
		}
		return 1;
	}
	 
	/**
	 * When clicking on circle in this packing, display it for this canvas 
	 * and also display the associated circle in the q canvas. Translate
	 * directly if 'this.nodeCount' and 'q.nodeCount' are equal or if 
	 * 'this.vertexMap' doesn't exist. Else, translate using 'this.vertexMap'.
	 * @param q PackData
	 * @param v int
	 * @param trans_flag boolean
	 * @return int
	 */
	public int circle_map_action(PackData q,int v,boolean trans_flag) {
	    if (v<1 || v>nodeCount) return 0;
		// translate
	    NodeLink nL=null;
	    try {
	    	if (nodeCount==q.nodeCount || vertexMap==null || !trans_flag)
	    		nL=Translators.vert_translate(null,v,true);
	    	else 
	    		nL=Translators.vert_translate(this.vertexMap,v,true);
	    } catch (Exception ex) {
	    	return 0;
	    }
	    
	    boolean donep=false;
	    DispFlags dispFlags=new DispFlags("f",cpDrawing.fillOpacity);
		if (nL!=null && nL.size()>0) {
			int count=0;
			Iterator<Integer> nli=nL.iterator();
			while (nli.hasNext()) {
				int nv=(Integer)nli.next();
				if (nv>0 && nv<=q.nodeCount) {
					if (!donep) { // draw once for this packing
						dispFlags.setColor(getCircleColor(v));
						cpDrawing.drawCircle(getCenter(v),getRadius(v),dispFlags);
					}
					donep=true;
				}
				if (nv<=q.nodeCount) { // draw all the translates 
					dispFlags.setColor(q.getCircleColor(nv));
					q.cpDrawing.drawCircle(q.getCenter(nv),q.getRadius(nv),dispFlags);
					count++;
				}
			}
			return count;
		}
		return 0;
	}
	
	/**
	 * Return a pointer to 'PackExtender' if there is an existing one
	 * with given 'extensionType' 
	 * @param String xType (different for each subclass)
	 * @return 'PackExtender' or null.
	 */
	public PackExtender findXbyName(String xType) {
		Iterator<PackExtender> pXs=packExtensions.iterator();
		while (pXs.hasNext()) {
			PackExtender pext=pXs.next();
			if (pext.getType().equals(xType))
			return pext;
		}
		return null;
	}

	/**
	 * Return a pointer to 'PackExtender' if there is an existing one
	 * with given 'extensionType' (ignoring case).
	 * @param xAbbrev String 
	 * @return 'PackExtender' or null.
	 */
	public PackExtender findXbyAbbrev(String xAbbrev) {
		Iterator<PackExtender> pXs=packExtensions.iterator();
		while (pXs.hasNext()) {
			PackExtender pext=pXs.next();
			if (pext.getAbbrev().equalsIgnoreCase(xAbbrev))
				return pext;
		}
		return null;
	}

	/**
	 * Currently, put segment number at first circle.
	 * TODO: need better placement; e.g. this will conflict with circle index
	 * @param n int
	 * @return int, 0 on error
	*/
	public int sa_draw_bdry_seg_num(int n) {
		RedEdge rtrace=null;
	    SideData epair=null;
	    if (packDCEL.pairLink==null || n<0 || n>=packDCEL.pairLink.size() 
	    		|| (epair=packDCEL.pairLink.get(n))==null
	    		|| (rtrace=epair.startEdge)==null) 
	    	return 0;
		Complex ctr=packDCEL.getVertCenter(rtrace.myEdge);
		if (hes>0)
			ctr=cpDrawing.sphView.toApparentSph(ctr);
	    cpDrawing.drawIndex(ctr,n,1);
	    return 1;
	}
	
	/**
	 * Currently, put segment number at first circle.
	 * TODO: need better placement; e.g. this will conflict with 
	 * circle index
	 * @param pF PostFactory
	 * @param n int
	 * @return int, 0 on error
	*/
	public int post_bdry_seg_num(PostFactory pF,int n) {
		RedEdge trace=null;
	    SideData epair=null;
	    if (packDCEL.pairLink==null || n<0 || n>=packDCEL.pairLink.size() 
	    		|| (epair=packDCEL.pairLink.get(n))==null
	    		|| (trace=epair.startEdge)==null) 
	    	return 0;
		Complex ctr=trace.getCenter();
		if (hes>0) {
			ctr=cpDrawing.sphView.toApparentSph(ctr);
			if (Math.cos(ctr.x)>=0) 
				pF.postIndex(ctr,n);
		}
		else 
			pF.postIndex(ctr,n);
	    return 1;
	}
	
	/** 
	 * Draw an edge-pairing boundary segment for side n.
	 * @param n int, index of side-pair (indices start at 0)
	 * @param do_label boolean, label side edge also?
	 * @param do_circle boolean, circles also?
	 * @param ecol Color
	 * @param int thickness to draw
	 * @return int
	 */
	public int sa_draw_bdry_seg(int n,boolean do_label,boolean do_circle,
			Color ecol,int thickness) {
	  SideData epair=null;
	  
	  if (packDCEL.pairLink==null || n<0 || n>=packDCEL.pairLink.size() 
			  || (epair=packDCEL.pairLink.get(n))==null
			  || epair.startEdge==null) 
		  // epair.startEdge.hashCode();epair.startEdge.nextRed.hashCode();
		  return 0;
	  RedEdge rtrace=epair.startEdge;
	  int old_thickness=cpDrawing.getLineThickness();

	  DispFlags dflags=new DispFlags(""); 
      cpDrawing.setLineThickness(thickness);
	  
	  Complex w_cent=packDCEL.getVertCenter(rtrace.myEdge);
	  do {
	      Complex v_cent=w_cent;
	      double v_rad=packDCEL.getVertRadius(rtrace.myEdge);
	      rtrace=rtrace.nextRed;
	      w_cent=packDCEL.getVertCenter(rtrace.myEdge);
	      if (do_circle) { // do v circle
		      cpDrawing.setLineThickness(old_thickness);
	    	  cpDrawing.drawCircle(v_cent,v_rad,dflags);
	          cpDrawing.setLineThickness(thickness);
	      }
	      DispFlags df=new DispFlags(null); 
	      df.setColor(ecol);
	      cpDrawing.drawEdge(v_cent,w_cent,df);
	  } while (rtrace!=epair.endEdge.nextRed);
	  
	  // last circle?
	  if (do_circle) {
		  w_cent=packDCEL.getVertCenter(rtrace.myEdge);
		  double w_rad=packDCEL.getVertRadius(rtrace.myEdge);
	      cpDrawing.setLineThickness(old_thickness);
    	  cpDrawing.drawCircle(w_cent,w_rad,dflags);
          cpDrawing.setLineThickness(thickness);
	  }
	  
      cpDrawing.setLineThickness(old_thickness);
	  if (do_label) 
		  sa_draw_bdry_seg_num(n);
	  return 1;
	}
	
	/** 
	 * Post an edge-pairing boundary segment based on starting face and 
	 * index of beginning vert. 
	 * @param pF PostFactory
	 * @param n int, index of side-pair (starting at 0)
	 * @param do_label boolean, label also?
	 * @param do_circle boolean, circles also?
	 * @param ecol Color
	 * @param tx double, thickness factor if > 0
	 * @return int
	 */
	public int post_bdry_seg(PostFactory pF,int n,boolean do_label,
			boolean do_circle,Color ecol,double tx) {
		  SideData epair=null;
		  
		  if (packDCEL.pairLink==null || n<0 || n>=packDCEL.pairLink.size() 
				  || (epair=packDCEL.pairLink.get(n))==null
				  || epair.startEdge==null) 
			  // epair.startEdge.hashCode();epair.startEdge.nextRed.hashCode();
			  return 0;
		  RedEdge rtrace=epair.startEdge;
		  int old_thickness=cpDrawing.getLineThickness();

		  Complex w_cent=packDCEL.getVertCenter(rtrace.myEdge);
		  do {
		      Complex v_cent=w_cent;
		      rtrace=rtrace.nextRed;
		      w_cent=packDCEL.getVertCenter(rtrace.myEdge);
		      if (hes>0) { 
		    	  v_cent=cpDrawing.sphView.toApparentSph(v_cent);
		    	  w_cent=cpDrawing.sphView.toApparentSph(w_cent);
		      }
		      if (do_circle) { // do v circle
			      double v_rad=packDCEL.getVertRadius(rtrace.myEdge);
			      pF.postCircle(hes,v_cent,v_rad);
		      }
		      pF.postColorEdge(hes,v_cent,w_cent,ecol,tx);
		  } while (rtrace!=epair.endEdge.nextRed);
		  
		  // last circle?
		  if (do_circle) {
			  w_cent=packDCEL.getVertCenter(rtrace.myEdge);
	    	  w_cent=cpDrawing.sphView.toApparentSph(w_cent);
			  double w_rad=packDCEL.getVertRadius(rtrace.myEdge);
		      pF.postCircle(hes,w_cent,w_rad);
		  }
		  
	      cpDrawing.setLineThickness(old_thickness);
		  if (do_label) 
			  post_bdry_seg_num(pF,n);
		  return 1;
	}

	public int output_parse(BufferedWriter fp,String prefix,String data,String loop,
			String suffix,Mobius mob) {
		try {
			fp.write(prefix);
			flashError("error writing 'prefix'");
			fp.flush();
			fp.close();
			return 1;
		} catch (Exception ex) {
			return 0;
		}
	}

	/**
	 * Find Mobius 'mob' to apply to this pack to put it in register with q. 
	 * Both packs hyp or both eucl. Apply 'best' automorphism to line up centers 
	 * of designated vertices: v1 with w1, v2 with w2. In hyp case, must have
	 * all centers on unit circle or all interior to disc. Return 0 on error. 
	 * Errors are reflected in 'mob.error'.
	 * (Eventually may want 'best' automorphism for lining up 3 pairs.) 
	 * @param qackData @see PackData other packing
	 * @param v1 int
	 * @param v2 int
	 * @param w1 int
	 * @param w2 int
	 * @param mob Mobius created by calling routine for result
	 * @return int, 0 on error 
	*/
	public int match(PackData qackData, int v1, int v2, int w1, int w2,
			Mobius mob) {
		Complex a1, a2, b1, b2, Two;

		mob.error = 0.0;
		if (!status || !qackData.status || hes > 0 || hes != qackData.hes)
			return 0;
		if (hes < 0) { // hyperbolic automorphism
			a1 = getCenter(v1);
			a2 = getCenter(v2);
			b1 = qackData.getCenter(w1);
			b2 = qackData.getCenter(w2);
			if (a1.abs() >= Mobius.MOD1 && a2.abs() >= Mobius.MOD1
					&& b1.abs() >= Mobius.MOD1 && b2.abs() >= Mobius.MOD1)
			/*
			 * all on unit circle: fixup: need one more real parameter, and I
			 * don't know where to get it; I'll put 2.0, 2.0 in for now so we at
			 * least get a result.
			 */
			{
				Two = new Complex(2.0);
				mob = Mobius.trans_abAB(a1, a2, b1, b2, Two, Two);
			} else if (a1.abs() < Mobius.MOD1 && a2.abs() < Mobius.MOD1
					&& b1.abs() < Mobius.MOD1 && b2.abs() < Mobius.MOD1) {
				// all interior
				mob = Mobius.auto_abAB(a1, a2, b1, b2);
			} else {
				flashError("match: data not suitable");
				return 0;
			}
			return 1; // can check error in 'mob'
		}

		// else: eucl automorphism
		a1 = getCenter(v1);
		a2 = getCenter(v2);
		b1 = qackData.getCenter(w1);
		b2 = qackData.getCenter(w2);
		mob = Mobius.mob_abAB(a1, a2, b1, b2);
		return 1;
	}
	
	/** 
	 * Given bdry vert 'v=ans[0]', find bdry vert 'w=ans[1]' in same bdry 
	 * component which is combinatorially 'opposite' v. Return 0 on error, 
	 * eg. bdry component length < 4..
	 * @param ans[2], ans[0]=v, if given but may be adjusted
	 * @return: distance from v to w along the boundary.
	 * @return ans[]={v,w}.
	*/
	public int comb_bdry_antip(int []ans) {
		int v=ans[0];
	    if (v<1 || v>nodeCount || !isBdry(v)) {
	    	// improper vert? use first bdry vert encountered 
	    	int vv=0;
	    	for (int i=1;i<=nodeCount;i++)
	    		if (isBdry(i)) {
	    			vv=i;
	    			i=nodeCount+1;
	    		}
	    	if (vv==0) 
	    		return 0; // no boundary
	    	v=vv;
	    }
	    int n;
	    if ((n=bdry_comp_count(v))==0 || n<4 )
	    	return 0;
	    n=(int)(n/2);
	    int w=v;
	    for (int i=1;i<=n;i++) 
	    	w=getFirstPetal(v);
	    ans[0]=v;
	    ans[1]=w;
	    return n;
	} 
	
	/** "Blending" refers to the attachment of one packing to another,
	generally with some overlap where a smooth transition in radii
	and centers from one pack to the other can be made. (Eg., packing 
	'islands' generated with PlugPack or "mending" a flaw.)

	Blend pack q centers/radii with pack p centers/radii, putting
	result in pack p. (Assume both p, q have been packed.) The vertexMap 
	of q will be used to identify circles of q with the corresponding 
	circles of p.

	'bdry_vert' is a bdry vert of q; it and its bdry antipital will be used
	for registration purposes, and the resulting mobius is applied to pack q.
	Presumably the packing combinatorics are such that they overlap in
	a certain combinatorial annulus; 'depth' indicates how many generations 
	(counting done in q) are used to smoothly blend the centers of p with 
	those of q -- the closer to bdry q, the more heavily the centers of p 
	are weighted. Depth 1 means just line up bdry_vert and its antipital;
	depth < 0 means allow largest possible depth.

	Return number of generations blended (may be less than depth). Return
	0 on error.
	*/
	public int blend(PackData q,int bdry_vert,int depth) {
	  double factor,ofactor=1.0;

	  if (!status || !q.status || q.vertexMap==null) return 0;

	  // find bdry verts to match for registering packings 
	  int []ans=new int[2];
	  ans[0]=bdry_vert;
	  int n=q.comb_bdry_antip(ans);
	  int v=ans[0]; // if bdry_vert was not adequate, it becomes v in this call 
	  int w=ans[1];
	  int V,W;
	  try {
		  V=Translators.vert_translate(q.vertexMap,v,true).get(0);
		  W=Translators.vert_translate(q.vertexMap,w,true).get(0);
	  } catch (Exception ex) {
		  return 0;
	  }
	  if (n==0 || V<=0 || W<=0) return 0;

	  // find and apply Mobius to get q in register with p.
	  NodeLink bdrylist=new NodeLink(this,"b");
	  Mobius M=new Mobius();
	  if (q.match(this,v,w,V,W,M)!=0 || M.error>TOLER || bdrylist==null
	      || bdrylist.size()==0)
	      return 0;
	  NodeLink vertlist=new NodeLink(this,"a");
	  if (hes==0) { // eucl case: should be affine map 
	      factor=M.a.abs();
	      for (int i=1;i<=q.nodeCount;i++) {
		  q.setRadius(i,getRadius(i)*factor);
		  q.setCenter(i,q.getCenter(i).times(M.a).add(M.b));
	      }
	  }
	  else q.apply_Mobius(M,vertlist);
	  
	  // get list with generation labels 
	  for (int i=1;i<=q.nodeCount;i++) q.setVertUtil(i,0); 
	  Iterator<Integer> blist=bdrylist.iterator();
	  while (blist.hasNext()) {
	      v=(Integer)blist.next();
	      q.setVertUtil(v,1);
	  }
	  UtilPacket uP=new UtilPacket();
	  int []genlist=q.packDCEL.label_generations(depth,uP);
	  if (genlist==null)
	      return 0;
	  
	  // for efficiency, put vertex_map in vector.
	  int []maplist=new int[q.nodeCount+1];
	  Iterator<EdgeSimple> vm=q.vertexMap.iterator();
	  EdgeSimple edge=null;
	  while (vm.hasNext()) {
	      edge=(EdgeSimple)vm.next();
	      maplist[edge.v]=edge.w;
	  }

	  // what is actual number of generations overlap? (up to 'depth').
	  int max_depth=q.nodeCount;
	  for (int i=1;i<=q.nodeCount;i++) {
	      q.setVertMark(i,0);
	      if (genlist[i]>0 && genlist[i]<=max_depth && maplist[i]==0)
		  // vert of q in this generation does have corresp. vert in p 
		  max_depth=genlist[i]-1;
	      else q.setVertMark(i,genlist[i]); // record gen in mark in case it's needed
	    }
	  // bdry doesn't overlap?
	  if (max_depth<1) return 0;
	  if (depth<0) depth=max_depth;
	  if (max_depth<depth) depth=max_depth;

	  /* now set centers of p circles in overlap by going successively 
	     through generations in q and weighting their centers with 
	     centers already in p. */

	  int tv;
	  for (int i=1;i<=depth;i++) {
	      vertlist=new NodeLink(q,"{m.eq."+i+"}");
	      // should have verts in every generation up to depth 
	      if (vertlist.size()==0) 
	    	  return 0;
	      factor=0.0;
	      if (depth>1) factor=(double)(i-1)/(double)(depth-1);
	      ofactor=1.0-factor;
	      Iterator<Integer> vlist=vertlist.iterator();
	      while (vlist.hasNext()) {
		  v=(Integer)vlist.next();
		  if ((tv=maplist[v])==0 || tv>nodeCount) 
		      return 0; // failed translation
		  Complex zt=getCenter(tv);
		  Complex zz=q.getCenter(v);
		  setCenter(tv,zt.times(ofactor).
		      add(zz.times(factor)));
		 
		  setRadius(tv,ofactor*getRadius(tv)+
		    factor*q.getRadius(v));
	      }
	    }

	  /* finally, for all the rest of the vertices of q, map their
	     centers/radii over to p without change */
	  for (int i=1;i<=q.nodeCount;i++) {
	      if ((q.getVertMark(i)>depth || q.getVertMark(i)==0)
		  && (tv=maplist[i])!=0 && tv<=nodeCount) {
	    	  setCenter(tv,q.getCenter(i));
	    	  setRadius(tv,q.getRadius(i));
	      }
	  }
	  return 1;
	}
	
	/** 
	 * Save adjacency matrix of packing (<= 10,000 verts) in form for
	 * matlab to read; the adjacency matrix has a 1 in (i,j) spot if
	 * i and j are neighboring vertices, else a 0. 

	 * If 'tm' is true, divide every row by sum of its entries and set
	 * the rows for boundary vertices to zero: this yields the 
	 * "transition" matrix for the "simple" (ie. equal probability) 
	 * random walk on the edge-graph of the packing with absorbing
	 * boundary. Also add an ordered list of the boundary vertices to
	 * the matlab file.
	 * Return 0 on error.
	*/
	public int adjacency(BufferedWriter fp,boolean tm) {
	  int start,nv,nodes;

	  if ((nodes=nodeCount)>10000) {
		  flashError("usage: adjacency: packing is too large, over 10000");
	      return 0;
	  }
	  try {
	  if (tm) {
	    fp.write("%% transition matrix for the simple random "+
		    "walk on a circle packing graph; "+nodes+" vertices.\n\n"+
		    "echo on\n"+
		    "T=zeros("+nodes+","+nodes+");\n");
	    for (int j=1;j<=nodes;j++) {
	      if (isBdry(j)) 
		  fp.write("T("+j+","+j+")=1;\n"); // absorbing bdry vertices 
	      else {
		for (int i=1;i<=nodes;i++)
		  if (nghb(i,j)>=0) 
		    fp.write("T("+i+","+j+")=1/"+countFaces(j)+";\n");
	      }
	    }
	    if (getBdryCompCount()!=0) { // nonempty boundary? 
	      fp.write("Bdry=[");
	      for (int i=1;i<=getBdryCompCount();i++) {
		  start=bdryStarts[i];
		  fp.write(new String(start+" "));
		  nv=start;
		  while ((nv=getFirstPetal(nv))!=start) {
		    fp.write(new String(nv+" "));
		  }
	      }
	      fp.write("];\n\n");
	      fp.write("pause %% press any key to get exit distribution "+
		      "function of exit probabilities.\n");
	      fp.write("dist=zeros("+nodes+");dist("+
		      packDCEL.alpha.origin.vertIndx+")=1\n");
	      fp.write("equib=T^10000*dist\n");
	      fp.write("Acum(1)=equib(Bdry(1));\n");
	      fp.write("for i=2:size(Bdry,2),\n");
	      fp.write("Acum(i)=Acum(i-1)+equib(Bdry(i));\n");
	      fp.write("end\nAcum\n");
	    }
	  }
	  else {
	    fp.write("% adjacency matrix for a circle packing graph; "+
		    nodes+" vertices\n\n"+"A=zeros("+nodes+","+nodes+");\n");
	    for (int i=1;i<=nodes;i++)
	      for (int j=1;j<=nodes;j++)
		if (nghb(i,j)>=0) 
		  fp.write("A("+i+","+j+")=1;\n");
	  }
	  fp.write("% end\n");
	  fp.flush();
	  fp.close();
	  } catch (Exception ex) {
		  try {
			  fp.flush();
			  fp.close();
		  } catch (Exception exx) {}
		  flashError("adjacency: exception in writing the file.");
		  return 0;
	  }
	  return 1;
	}
	
	/**
	 * adjust a radius by a given factor
	 * @param v, vertex
	 * @param factor, double, positive
	 * @return 0 on error
	 */
	public int adjust_rad(int v,double factor) {
		if (factor==1.0)
			return 1;
		if (v<1 || v>nodeCount || factor<=0.0) 
			return 0;
		double newrad;
		int count=0;
		double rad=getRadius(v);
		if (hes<0) { // hyperbolic
			if (rad<0) { // infinite radius?
				if (factor>0.99999) 
					newrad=rad; // don't change
				else // becomes large but finite
					newrad=.9995;
				count++;
			}
			else if (rad>.99999 && factor>1.0) {
				setRadiusActual(v,5.0); // essentially infinite
				count++;
				return count;
			}
			else { // replace x by x=1-exp(factor*(log(1-x)))
				newrad=1.0-Math.exp(factor*Math.log(1.0-rad));
    			count++;
			}
		}
		else if (hes>0) { // spherical
			newrad=rad*factor;
			if (newrad<=0 || newrad>=Math.PI) 
				newrad=rad; // illegal, don't change
			else 
				count++;
		}
		else { // eucl
			newrad=rad*factor;
			count++;
		}
		if (count>0) 
			setRadius(v,newrad);
		return count;
	}

	/**
	 * create a circular spoke/circle grid from {cent,rad}, filling 
	 * global vector 'Blink' of 'BaryLink's.
	 * @param cent Complex
	 * @param rad double
	 * @param int lineCount, number of spokes and circles (not including unit circle)
	 * @return count of lines, 0 on error
	 */
	public int makeGrid(Complex cent,double rad,int lineCount) {
		PackData localPD=this;
		if (this.hes>0) {
			CirclePack.cpb.errMsg("grids not yet available for spherical packings");
			return 0;
		}
		if (this.hes<0) {
			localPD=this.copyPackTo();
			localPD.geom_to_e();
		}
		CPBase.gridLines=new Vector<BaryCoordLink>(2*lineCount);
		for (int j=1;j<=lineCount;j++) {
			Path2D.Double ray=new Path2D.Double();
			ray.moveTo(cent.x,cent.y);
			double ang=(double)j*Math.PI*2.0/lineCount;
			ray.lineTo(Math.cos(ang),Math.sin(ang));
			CPBase.gridLines.addAll(PathBaryUtil.fromPath(localPD,ray));
			CPBase.gridLines.addAll(PathBaryUtil.fromPath(localPD,
					PathUtil.getCirclePath((double)j/(lineCount+1),
							new Complex(0.0),128)));
		}
		return 1;
	}
	
	/**
	 * create a rectangular grid from {lowl,upr}, filling global 
	 * vector 'baryVector' of 'BaryCoordLink's.
	 * @param lowl, upr, Complex
	 * @param int lineCount
	 * @return count of lines, 0 on error
	 */
	public int makeGrid(Complex lowl,Complex upr,int lineCount) {
		PackData localPD=this;
		if (this.hes>0) {
			CirclePack.cpb.errMsg("grids not yet available for spherical packings");
			return 0;
		}
		if (this.hes<0) {
			localPD=this.copyPackTo();
			localPD.geom_to_e();
		}
		CPBase.gridLines=new Vector<BaryCoordLink>(2*lineCount+2);
		for (int j=0;j<=lineCount;j++) {
			double high=upr.y-lowl.y;
			double wide=upr.x-lowl.x;
			if (high<=0) high=1;
			if (wide<=0) wide=1;
			high /=lineCount;
			wide /=lineCount;
			// vertical line
			double spot=lowl.x+(double)j*wide;
			Path2D.Double ray=new Path2D.Double();
			ray.moveTo(spot,lowl.y);
			ray.lineTo(spot,upr.y);
			CPBase.gridLines.addAll(PathBaryUtil.fromPath(localPD,ray));
			// horizontal line
			spot=lowl.y+(double)j*high;
			ray=new Path2D.Double();
			ray.moveTo(lowl.x,spot);
			ray.lineTo(upr.x,spot);
			CPBase.gridLines.addAll(PathBaryUtil.fromPath(localPD,ray));
		}
		return lineCount;	
	}

	/**
	 * Represent the combinatorics as an undirected graph in 
	 * 'GraphLink' form. Each edge occurs only once, {u,v}
	 * v greater than u. Leave out all edges to/from vertices 
	 * in 'exclude'.
	 * @param excluce, NodeLink
	 * @return GraphLink, null on error
	 */
	public GraphLink getAsGraph(NodeLink exclude) {
		GraphLink ans=new GraphLink();
		int k=0;
		for (int v=1;v<=nodeCount;v++) {
			if (exclude!=null && exclude.containsV(v)<0) {
				int[] flower=packDCEL.vertices[v].getFlower(true);
				for (int j=0;j<flower.length;j++) {
					if ((k=flower[j])>v && 
							exclude!=null && exclude.containsV(k)<0)
						ans.add(new EdgeSimple(v,k));
				}
			}
		}
		return ans;
	}
	
	/**
	 * Convert 'PackData' combinatorics to 'Triangulation' object
	 * @return Triangulation
	 */
	public Triangulation getTriangulation() {
		Triangulation Tri=new Triangulation();
		Tri.faceCount=faceCount;
		Tri.nodeCount=nodeCount;
		Tri.maxIndex=Tri.nodeCount;
		Tri.faces=new Face[Tri.faceCount+1];
		Tri.nodes=new Point3D[Tri.nodeCount+1];
		
		// build triangles
		for (int f=1;f<=Tri.faceCount;f++) {
			combinatorics.komplex.DcelFace face=packDCEL.faces[f];
			int[] vert=face.getVerts();
			Tri.faces[f]=new combinatorics.komplex.Face(vert.length);
			Tri.faces[f].vert=vert;
		}
		
		// store locations
		for (int v=1;v<=nodeCount;v++) {
			Complex z=getCenter(v);
			Tri.nodes[v]=new Point3D(z.x,z.y,0.0);
		}
		
		return Tri;
	}
	
	/**
	 * Idea of resampling is to apply some filter to the
	 * vertices of 'p', marking those that pass the test. 
	 * I think this method will be made more general and 
	 * placed elsewhere, but for now, it is tailored to 
	 * Poisson density proportional "density" divided by
	 * signed distance to Gamma, mode=1.
	 * @param p
	 * @param Gamma Path2D.Double, Jordan curve
	 * @param mode int. 
	 * @param maxThinning double, Poisson density normally 
	 * 		specified in original creation of this packing; 
	 * 		this concerns how thin deep inside.
	 * @return NodeLink of filter survivors
	 */
	public static NodeLink resample(PackData p,Path2D.Double Gamma,
			int mode,double thinning) {

		// prepare return route
		NodeLink theChosen=null;
		Random rand=new Random(); // could add 'seed' for debugging.
		
		// only case for now
		if (mode==1 && Gamma!=null) {
			
			// store normalized distances to the boundary
			double []val=new double[p.nodeCount+1];
			double maxdepth=-100000;
			for (int v=1;v<=p.nodeCount;v++) {
				val[v]=EuclMath.dist_to_path(p.getCenter(v),PathUtil.gpPolygon(Gamma).get(0));
				maxdepth=(val[v]>maxdepth) ? val[v] : maxdepth;
			}
			if (maxdepth<=.000000001) {
				throw new DataException("spread of depths appears to be off");
			}
			
			// TODO: here we need a function describing how to set the
			//       probability based on 'val'.
			double a=.25;
			double b=.9;
			double prob;
			theChosen=new NodeLink(p);
			for (int v=1;v<=p.nodeCount;v++) {
				val[v] /= maxdepth; // seems reasonable (??) to normalize to [-1,1] to
									//   prepare for more general situations.

				prob=1.0;
				if (val[v]<a)
					prob=1.0;
				else if (val[v]>b)
					prob=thinning;
				else { // interpolate
					prob=1.0+(val[v]-a)*(thinning-1.0)/(b-a);
				}
				if (rand.nextDouble()<=prob) {
					theChosen.add(v);
				}
			}
			return theChosen;
		}
		
		// for future alternate schemes
		else { 
			throw new ParserException("so far, only mode=1 is set up");
		}
	}

	/**
	 * Given packing and NodeLink of chosen interior 
	 * vertices, create new packing by Delaunay 
	 * triangulating the chosen vertices, plus all the 
	 * original packing's boundary vertices, at their current 
	 * locations.
	 *
	 * TODO: Extend to cutting out a topological disc, 
	 * retriangulating it and pasting new triangulation back 
	 * in. Would be fun to alternate: cut, Delaunary 
	 * triangulate, pack, layout, repeat.
	 * 
	 * TODO: This has not been thoroughly debugged.
	 * 
	 * @param p PackData, initial packing
	 * @param chosen NodeLink, verts to include, along with bdry
	 * @return PackData, vertexMap contains {orig,new} matchings.
	 */
	public static PackData sampledSubPack(PackData p,NodeLink chosen) {
		if (p.hes>0) {
			CirclePack.cpb.errMsg("'sampledSubPack' cannot be called "
					+ "for spherical packings");
			return null;
		}

		// get full list, chosen plus bdry
		int[] vhits=new int[p.nodeCount+1];
		Iterator<Integer> vlst=chosen.iterator();
		while (vlst.hasNext()) 
			vhits[vlst.next()]=1;
		NodeLink bdry=new NodeLink(p,"b");
		Iterator<Integer> bis=bdry.iterator();
		while (bis.hasNext()) 
			vhits[bis.next()]=1;

		NodeLink thechosen=new NodeLink(p);
		for (int v=1;v<=p.nodeCount;v++) 
			if (vhits[v]!=0)
				thechosen.add(v);
		
		// create DelaunayData
		DelaunayData dData=new DelaunayData();
		dData.geometry=0; // treat as euclidean
		dData.pointCount=thechosen.size();
		dData.ptX=new double[dData.pointCount+1];
		dData.ptY=new double[dData.pointCount+1];
		  
		// must associate old/new indices
		VertexMap vmap=new VertexMap();
		  
		vlst=thechosen.iterator();
		int v;
		int hit=1;  // previous code suggests numbering from 1
		while (vlst.hasNext()) {
			v=vlst.next();
			Complex pz=p.getCenter(v);
			dData.ptX[hit]=pz.x;
			dData.ptY[hit]=pz.y;
			vmap.add(new EdgeSimple(v,hit)); // {original,new}
			hit++;
		}
		  
		// get edge information
		EdgeLink bdrylist=new EdgeLink(p,"b");
		dData.edgeV=null;
		dData.edgeW=null;
		if (bdrylist!=null) { 
			dData.bdryCount=bdrylist.size();
			dData.edgeV=new int[dData.bdryCount];
			dData.edgeW=new int[dData.bdryCount];
			Iterator<EdgeSimple> eit=bdrylist.iterator();
			int hits=0;
			while (eit.hasNext()) {
				EdgeSimple edge=(EdgeSimple)eit.next();
				dData.edgeV[hits]=vmap.findW(edge.v);
				dData.edgeW[hits]=vmap.findW(edge.w);
				hits++;
			}
		}
		  
		ProcessDelaunay.planeDelaunay(dData);
		Triangulation Tri = dData.getTriangulation();
		PackData samplePack=Triangulation.tri_to_Complex(Tri,dData.geometry);
		samplePack.fileName=new String("resampled"+dData.pointCount);
		return samplePack;
	}
	
	public void setSidePairs(PairLink plink) {
		packDCEL.pairLink=plink;
	}
	
	public PairLink getSidePairs() {
		return packDCEL.pairLink;
	}
	
	/**
	 * Get the indices of the first sides in all side pairs
	 * @return int[], null if no paired sides
	 */
	public NodeLink getPairIndices() {
		NodeLink indices=new NodeLink();
		if (packDCEL.pairLink!=null || packDCEL.pairLink.countPairs()==0)
			return null;
		for (int j=1;j<packDCEL.pairLink.size();j++) {
			SideData sidd=packDCEL.pairLink.get(j);
			if (sidd.mateIndex>sidd.spIndex)
				indices.add(sidd.spIndex);
		}
		return indices;
	}

	/**
	 * Return the Mobius with the given side-pair index
	 * @param e int, for DCEL, starts with 1
	 * @return Mobius
	 */
	public Mobius getSideMob(int e) {
		try {
			return packDCEL.pairLink.get(e).mob;
		} catch(Exception ex) {
			throw new CombException("failed to get side pair "+e);
		}
	}
	
	/**
	 * check for any non-trivial 'schwarzian's 
	 * @return boolean
	 */
	public boolean haveSchwarzians() {
		boolean schflag=false;
		for (int v=1;(v<=nodeCount && !schflag);v++) {
			HalfLink spokes=packDCEL.vertices[v].getSpokes(null);
			Iterator<HalfEdge> sis=spokes.iterator();
			while(sis.hasNext() ) {
				HalfEdge he=sis.next();
				if (he.getSchwarzian()!=0.0)
					schflag=true;
			}
		}
		return schflag;
	}
	
	/**
	 * Are there any non-trivial inversive distances?
	 * @return boolean
	 */
	public boolean haveInvDistances() {
		boolean ivdflag=false;
		for (int v=1;(v<=this.nodeCount && !ivdflag);v++) {
			HalfLink spokes=packDCEL.vertices[v].getSpokes(null);
			Iterator<HalfEdge> sis=spokes.iterator();
			while(sis.hasNext() ) {
				HalfEdge he=sis.next();
				if (he.getInvDist()!=1.0)
					ivdflag=true;
			}
		}
		return ivdflag;
	}
	
	/**
	 * Get the 'intrinsicGeom', -1, 0, 1, based on
	 * combinatorics, i.e., number of bdry 
	 * components and genus. 
	 * @param p PackData
	 * @return int -1, 0, 1, default -1
	 */
	public static int getIntrinsicGeom(PackData p) {
		int iG=-1;
		if (p.getBdryCompCount()==0) {
			if (p.genus==0) // sphere
				iG=1;
			else if (p.genus==1) // torus
				iG=0;
		}
		else if (p.genus==0) // 
			iG=0;
		return iG;
	}
	
	/**
	 * Compute Mobius associated with holonomy along 
	 * a closed chain of faces. The faces are defined
	 * by given 'HalfLink', the first assumed to be 
	 * in the desired geometric location. Centers are
	 * then recomputed iteratively until the final
	 * locations of the original face vertices are found. 
	 * Note that data in the packing is not changed.
	 * The transformation and its Frobenius norm (how 
	 * close to identity) are displayed in 'Messages' and
	 * written to a file if fp!=null. 
	 * @param p PackData,
	 * @param hlink HalfLink
	 * @return Mobius, null on error
	 */
	public static Mobius holonomyMobius(PackData p,HalfLink hlink) {

		  PackDCEL pdcel=p.packDCEL;
		  HalfEdge startedge=hlink.getFirst();
		  HalfEdge endedge=hlink.getLast();
		  
		  Iterator<HalfEdge> his=hlink.iterator();
		  
		  // locate the first face
		  HalfEdge currhe=his.next();
		  HalfEdge nexthe=currhe;
		  CircleSimple cs0=pdcel.getVertData(currhe);
		  CircleSimple cs1=pdcel.getVertData(currhe.next);
		  double rad=pdcel.getVertRadius(currhe.next.next);
		  CircleSimple cs2=CommonMath.comp_any_center(cs0,cs1,rad,
				  currhe.next.getSchwarzian(),
				  currhe.next.next.getSchwarzian(),
				  currhe.getSchwarzian(),p.hes);
		  Complex[] startZ=new Complex[3];
		  startZ[0]=new Complex(cs0.center);
		  startZ[1]=new Complex(cs1.center);
		  startZ[2]=new Complex(cs2.center);
		  while (his.hasNext()) {
			  currhe=nexthe;
			  nexthe=his.next();
			  
			  if (nexthe.twin.prev==currhe) // head-to-head?
 				  cs0=cs2;
			  else // tail-to-tail
				  cs1=cs2;

			  rad=pdcel.getVertRadius(nexthe.next.next);
			  cs2=CommonMath.comp_any_center(cs0,cs1,rad,
				  nexthe.next.getSchwarzian(),
				  nexthe.next.next.getSchwarzian(),
				  nexthe.getSchwarzian(),p.hes);
		  }
		  
		  // line up triples
		  Complex[] endZ=new Complex[3];
		  if (endedge.next==startedge) { // head-to-tail
			  endZ[0]=cs1.center;
			  endZ[1]=cs2.center;
			  endZ[2]=cs0.center;
		  }
		  else { // tail-to-head
			  endZ[0]=cs2.center;
			  endZ[1]=cs0.center;
			  endZ[2]=cs1.center;
		  }
		  
//		  String opts=null;
//		  if (draw) opts=new String("-ff"); // draw the colored faces as we go
//		  DispFlags dflags=new DispFlags(opts,p.cpDrawing.fillOpacity);
		  Mobius mob=new Mobius(); // initialize transformation 
		  if (p.hes<0) // hyp
			  mob=Mobius.auto_abAB(startZ[0],startZ[1],endZ[0],endZ[1]);
		  else if (p.hes==0) { // eucl
			  Complex denom=startZ[1].minus(startZ[0]);
			  if (denom.abs()<.00000001) 
				  return null;
			  mob.a=endZ[0].minus(endZ[1]).divide(denom);
			  mob.b=endZ[0].minus(mob.a.times(startZ[0]));
		  }
		  else { // sph: TODO: this is untested
			  mob=Mobius.mob_xyzXYZ(startZ[0],startZ[1],startZ[2],
					  endZ[0],endZ[1],endZ[2],1,1);
		  }
		  return mob;
	}
	
		
} // end of 'PackData' class
