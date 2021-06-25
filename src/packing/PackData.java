package packing;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Vector;

import JNI.DelaunayBuilder;
import JNI.DelaunayData;
import allMains.CPBase;
import allMains.CirclePack;
import baryStuff.BaryPacket;
import baryStuff.BaryPoint;
import baryStuff.BaryPtData;
import circlePack.PackControl;
import complex.Complex;
import complex.MathComplex;
import dcel.CombDCEL;
import dcel.D_SideData;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RawDCEL;
import dcel.RedHEdge;
import dcel.VData;
import dcel.Vertex;
import deBugging.DCELdebug;
import deBugging.DebugHelp;
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.LayoutException;
import exceptions.MobException;
import exceptions.PackingException;
import exceptions.ParserException;
import exceptions.RedListException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import komplex.AmbiguousZ;
import komplex.CookieMonster;
import komplex.DualGraph;
import komplex.DualTri;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.GraphSimple;
import komplex.KData;
import komplex.RedEdge;
import komplex.RedList;
import komplex.SideDescription;
import komplex.Triangulation;
import listManip.BaryCoordLink;
import listManip.BaryLink;
import listManip.CentList;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PairLink;
import listManip.PointLink;
import listManip.TileLink;
import listManip.Translators;
import listManip.VertList;
import listManip.VertexMap;
import math.Matrix3D;
import math.Mobius;
import math.Point3D;
import microLattice.Smoother;
import panels.CPScreen;
import posting.PostFactory;
import rePack.EuclPacker;
import rePack.HypPacker;
import rePack.RePacker;
import rePack.SphPacker;
import rePack.d_EuclPacker;
import rePack.d_HypPacker;
import tiling.Tile;
import tiling.TileData;
import util.BuildPacket;
import util.ColorUtil;
import util.DispFlags;
import util.PathBaryUtil;
import util.PathUtil;
import util.RadIvdPacket;
import util.SelectSpec;
import util.SphView;
import util.StringUtil;
import util.TriAspect;
import util.UtilPacket;
import widgets.AngSumSliders;
import widgets.RadiiSliders;
import widgets.SchwarzSliders;

/**
 * This is the fundamental data repository for a circle packing and is
 * associated with a CPScreen for display. This is a workhorse, with 
 * methods for reading/writing, combinatorics, and manipulations.
 * @author kens, sometime last century
 *
 */
public class PackData{
	
	public static double TOLER=.0000000001; // TODO: fix up all thresholds
	public static double OKERR=.000000001; 
	public static final int MAX_ACCUR=15;   // digits of accuracy for file writing
	public static final int MAX_PETALS=1000; // the most petals a flower can have
	
	public enum PackState { INITIAL,NODECOUNT,CHECKCOUNT,TILECOUNT,PACKLITE,
		TRIANGULATION,NEUTRAL,PACKNAME,ABG,
		GEOMETRY,CIRCLE_PLOT_FLAGS,FACE_PLOT_FLAGS,SELECT_RADII,ANGLE_AIMS,
		INV_DISTANCES,ANGLESUMS,C_COLORS,CIRCLE_COLORS,TILE_COLORS,VERT_MARK,
		TRI_COLORS,T_COLORS,VERTEX_MAP,VERT_LIST,GLOBAL_VERT_LIST,GLOBAL_FACE_LIST,FACE_TRIPLES,
		EDGE_LIST,GLOBAL_EDGE_LIST,DUAL_EDGE_LIST,GLOBAL_DUAL_EDGE_LIST,POINTS,
		RADII_INTERACTIONS,DOUBLES,INTEGERS,COMPLEXES,BARY_VECTOR,BARY_DATA,
		SCHWARZIAN};

//	public enum PackExtensions {BROOKS_QUAD,RIEMANN_HILBERT,CONF_WELD,WORD_WALK,
//		CURV_FLOW,VELLING_PANTS,NULL};
		
	public RedChainer redChainer;  // for handling redchain manipulations	
	public RePacker rePacker=null;  // holds repacker (e.g., GOrepack) for continued use
	
    static int MAX_COMPONENTS=20;

	// Main data vectors
	public KData kData[];   // pointer to combinatoric data 
	public RData rData[];   // pointer to double data 
	public VData vData[];   // instantiated only with packDCEL
	public Vector<PackExtender> packExtensions;  // vector of extensions
	public TileData tileData; // for associated tilings 
	
    // various counts, set in 'complex_count'
	public int nodeCount;   // number of nodes
    public int faceCount;     // number of faces 
    public int intNodeCount;  // number of interior nodes
    public int bdryCompCount; // number of bdry components 
    public int intCompCount;  // number of interior components  
    
    public PackDCEL packDCEL; // for possible DCEL structur 

    // various global pack info
    public int hes;           // curvature of ambient geometry,-1=hyp,0=eucl,1=sph 
    public boolean status;    // false when pack is empty
	public String fileName;   // filename when file was read/written
    public int intrinsicGeom; // intrinsic geometry (due to combinatorics only)
    public int sizeLimit;     // current max number of nodes without reallocating
    int alpha;         // index of alpha node (origin)
    public int beta;          // nghb of alpha, <alpha,beta> is base edge
    public int gamma;         // index of node to be plotted on positive y-axis
    public int euler;   	  // Euler characteristic
    public int genus;         // genus of complex. Euler+#bdry=2-2g, g=(2-Euler-#bdy)/2 
    public int activeNode;    // currently active_node 
    public int locks;         // locks placed by remote processes; bitwise flags
    public boolean overlapStatus; // true if non-default overlaps/inv dists set
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
    public EdgeLink poisonEdges; // 
    public NodeLink poisonVerts; //
    public Point3D []xyzpoint;  // TODO: should be vector? pointer to associated 3D coords
    public Vector<Double> utilDoubles;  // utility vector to hold doubles (caution: indexed from 0)
    public Vector<Integer> utilIntegers;  // utility vector to hold integers
    public Vector<Complex> utilComplexes;  // utility vector to hold complex numbers
    public Vector<BaryPtData> utilBary;    // utility vector of BaryPtData, holds points and data
    
    // combinatoric -- created/maintained mainly 'complex_count' and layout calls
    public Face []faces;      // array of face data
    public int bdryStarts[];  // indices for verts starting the bdry components (indexed from 1) 
    public int intStarts[];   // indices of nodes in interior components (indexed from 1)
    public int firstFace;     // index of first face in plotting order
    public int firstRedFace;  // index of start of "red" chain of faces
    public int []fUtil;       // temp utility flags; user de/allocates as needed.
    public RedList redChain;   // current entry into double linked list called the "red chain"
    public RedEdge firstRedEdge;  // current entry to chain of 'RedEdge's (subord to 'redChain')
    PairLink sidePairs; // linked list, side-pairings/border segments (non-simply connected)

    public int packNum;		// lies between 0 and CPBase.NUM_PACKS-1  

    // utility variables passing info in methods -- use immediately after method call!
    public int util_A;
    public int util_B;
    public boolean util_bool;
    public RedList util_red;
    
    public String[] ListText=new String[3]; // utility area for 'List' tab strings

    public CPScreen cpScreen; // pointer to screen associated with this packdata 
    
    public String getDispOptions; // if not null, was set on readpack with 'DISP_FLAGS:'
    
    public GraphLink dualGraph;  // developmental (5/12)
    public GraphLink drawingTree; // developmental (7/12): hope to layout using this tree
    public GraphLink utilGraph;  // utility for developing dual graph, tree, etc.
    public int layoutOption;     // for testing layout procedures
    
    public RadiiSliders radiiSliders;
    public SchwarzSliders schwarzSliders;
    public AngSumSliders angSumSliders;
    
    public Smoother smoother;    // 6/2020. add a smoother
    
    int[] readOldNew;     // used in 'readpack' for DCEL translation
    
    public PackData(int packnum) {
    	this(null);
    	this.fileName="Empty";
    	packNum=packnum;
    }
    
    // Constructor
    public PackData(CPScreen parentScreen){
        cpScreen = parentScreen;
        // Note: creating new speculative PackData sets, use 'null' CPScreen until finished. 
        if (cpScreen !=null) packNum = cpScreen.getPackNum();
        else packNum=CPBase.NUM_PACKS; // temporary number
    	ListText[0]=new String("");
    	ListText[1]=new String("");
    	ListText[2]=new String("");
    	poisonEdges=null;
    	poisonVerts=null;
    	xyzpoint=null;
    	alloc_pack_space(500,false);
    	packExtensions=new Vector<PackExtender>(2);
    	tileData=null;
    	redChainer=new RedChainer(this);
    	this.fileName="Empty";
    	getDispOptions=null;
    	dualGraph=null;
    	utilGraph=null;
    	layoutOption=1; // default (current working layout)
    	utilDoubles=null;
    	utilIntegers=null;
    	utilComplexes=null;
    	vertexMap=null;
    	colorIndx=0;
    	smoother=null;
    	vData=null;
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
     * Attach a new or modified DCEL structure for this packing and 
     * sync the associated 'vData[]'. If this is an existing packing 
     * and the dcel structure is just created, then populate the new 
     * 'vData' using existing 'rData', with 'pdcel.newOld' to 
     * translate indices. If this is a modified dcel (for example,
     * if a new structure was cookied from the original), then go to
     * the existing 'vData' to populate the new 'vData', using
     * 'pdcel.newOld'.
     *  
     * TODO: may need to save additional info when 
     * swapping in new dcel: e.g., 'invDist's, 
     * 'schwarzian's, face colors, etc. And may need
     * to adjust vlist, elist, hlist, etc. based on
     * 'newOld'.
     * 
     * @param pdcel PackDCEL
     * @return int, vertCount on success, 0 on failure
     */
    public int attachDCEL(PackDCEL pdcel) {

    	if (pdcel.vertCount>nodeCount)
    		alloc_pack_space(pdcel.vertCount+10,true);
    	
    	packDCEL=pdcel;
    	pdcel.p=this;
    	int origNodeCount=nodeCount;
    	if (pdcel.alpha!=null)
    		alpha=pdcel.alpha.origin.vertIndx;
    	else
    		alpha=1;
		
    	// set some counts
		nodeCount=pdcel.vertCount;
		faceCount=pdcel.faceCount;
		setBdryCompCount(pdcel.idealFaceCount);
    	euler=nodeCount-(pdcel.edgeCount/2)+faceCount;
		genus=(2-euler-pdcel.idealFaceCount)/2;
		intrinsicGeom=PackData.getIntrinsicGeom(this);
    	fileName=StringUtil.dc2name(fileName);
    	
		// may need to expand 'vertices' to sizeLimit
		if (pdcel.vertices.length<sizeLimit+1) {
			Vertex[] new_vs=new Vertex[sizeLimit+1];
			for (int v=1;v<=pdcel.vertCount;v++)
				new_vs[v]=pdcel.vertices[v];
			pdcel.vertices=new_vs;
		}

    	// no existing dcel structure? get data from 'rData'
    	if (vData==null) {
    		
    		// instantiate 'vData' space
    		vData=new VData[sizeLimit+1];
    		for (int v=1;v<=sizeLimit;v++) 
    			vData[v]=new VData();
    		
    		// translation? make more efficient
    		int[] nOld=null;
    		if (pdcel.newOld!=null) {
    			nOld=new int[nodeCount+1];
        		Iterator<EdgeSimple> nost=pdcel.newOld.iterator();
        		while (nost.hasNext()) {
        			EdgeSimple es=nost.next();
        			nOld[es.v]=es.w;
        		}
    		}
    		 
    		for (int v=1;v<=nodeCount;v++) {
    			Vertex vert=pdcel.vertices[v];
    			if (vert.isBdry()) 
    				vData[v].setBdryFlag(1);
    			int oldv=v;
    			int w=0;
    			if (nOld!=null && (w=nOld[v])>0) 
    				oldv=w;
    			if (oldv<=origNodeCount) {
    				pdcel.setVertRadii(v,rData[oldv].rad);
    				pdcel.setVertCenter(v,new Complex(rData[oldv].center));
    				vData[v].color=ColorUtil.cloneMe(kData[oldv].color);
    			}
    			else {
    				if (vert.isBdry())
    					vData[v].aim=-0.1;
    				else
    					vData[v].aim=2.0*Math.PI;
    			}
    		}

        	// preassign arrays of indices
    		for (int v=1;v<=nodeCount;v++)
    			pdcel.setVDataIndices(v);
    		    		
        	fillcurves(); // compute all curvatures
        	set_aim_default(); // too difficult to figure out old aims

        	if (pdcel.gamma==null)
        		pdcel.gamma=pdcel.alpha.next;
    		return nodeCount;
    	}

    	// Otherwise, we may have old data to use:
    	//   Sometimes this is from 'rData', but in cases like
    	//   "adjoin", may have built 'vData' to hold combined data.
    	VData[] oldVData=new VData[origNodeCount+1];
		for (int ov=1;ov<=origNodeCount;ov++) 
			oldVData[ov]=vData[ov];
		
		// allocate new 'vData'
		vData=new VData[sizeLimit+1];
		for (int v=1;v<=sizeLimit;v++) 
			vData[v]=new VData();
		
		// translation? make more efficient
		int[] nOld=null;
		if (pdcel.newOld!=null) {
			nOld=new int[nodeCount+1];
    		Iterator<EdgeSimple> nost=pdcel.newOld.iterator();
    		while (nost.hasNext()) {
    			EdgeSimple es=nost.next();
    			nOld[es.v]=es.w;
    		}
		}

		// note: 'nodeCount' may be larger than 'origNodeCount'
		for (int v=1;v<=nodeCount;v++) {
			Vertex vert=pdcel.vertices[v];
			HalfEdge he=vert.halfedge;
   			int oldv=v;
   			int w=0;
   			if (nOld!=null && (w=nOld[v])>0) 
   				oldv=w;
   			// if there is existing data, copy it
   			if (oldv<=origNodeCount) {
   				Complex z=new Complex(0.0);
   				double rad=0.05;
   				try {
   					z=oldVData[oldv].center;
   					rad=oldVData[oldv].rad;
   			
   					// need to store in any 'RedHEdge's from this vertex
   					if (vert.redFlag) {
   						HalfEdge trace=he.prev.twin;
   						do {
   							if(trace.myRedEdge!=null) {
   								trace.myRedEdge.setCenter(z);
   								trace.myRedEdge.setRadius(rad);
   							}
   							trace=trace.prev.twin;
   						} while (trace!=he);
   					}
   				} catch(Exception ex) {}
				pdcel.setVertData(he, new CircleSimple(z,rad));
				vData[v].color=oldVData[oldv].color;
				vData[v].mark=oldVData[oldv].mark;
				vData[v].aim=oldVData[oldv].aim;
   			}
   			else {
    			if (vert.isBdry()) { 
    				vData[v].setBdryFlag(1);
    				vData[v].aim=-0.1;
    			}
    			else
					vData[v].aim=2.0*Math.PI;
   			}
			pdcel.setVDataIndices(v);
    	}

		// TODO: convert lists before killing 'newOld'??  
		pdcel.newOld=null;
		
    	set_aim_default(); // too difficult to figure out old aims
    	fillcurves();
    	if (pdcel.gamma==null)
    		pdcel.gamma=pdcel.alpha.next;
    	return pdcel.vertCount;
    }

    /**
     * Read new circle packing (or data for existing packing) 
     * into this packing from an open file. Return 0 on error. 
     * Key "NODECOUNT:" and "TRIANTULATION:" indicates new 
     * packings, in which case basic combinatorics are read; 
     * "CHECKCOUNT:" indicates data for current packing; 
     * "TILECOUNT:" indicates tiling information (tiles and 
     * their surrounding vertex indices).
     * "TRIANGULATION:" indicates triples forming faces.
     * New packing in 'Lite' form starts with magic number 1234321.

     * @return bit-coding for what was read as follows: 

       1: 00001     basic combinatorics (new pack)
       2: 00002     geometry
       3: 00004     non-default inv_dist & aimsinv_dist

       4: 00010     radii 
       5: 00020     centers
       6: 00040     angle sums

       7: 00100     vertex_map (if it exists)
       8: 00200     non-empty lists vlist/flist/elist
       9: 00400     colors (circle or face, non-default)

       10: 01000    vertex/face plotFlags
       11: 02000    xyz points
       12: 04000    edge-pairing Mobius transformations
       
       13: 010000   triangles
       14: 020000   tiling data
       15: 040000   display flags
       
       16: 0100000  non-empty global lists Vlist/Flist/Elist
       17: 0200000  dual tiling (only, and as 'TILECOUNT:' file)
       18: 0400000  misc other: interactions
                 
       19: 01000000 utility integers
       20: 02000000 utility double values
       21: 04000000 utility complex values
       
       22: 010000000 neutral, and used so we can return non-zero 'flags'

       CAUTION: responsibility of calling routine to update pack info
       (eg., aims, centers, etc) based on bits set in return value. 
       For a new pack, reading vertex_map, lists, or colors causes 
       defaults to be set before info is read in. When pack is not 
       new pack (CHECKCOUNT case), the info read in supercedes that in 
       pack.
       
       CAUTION: this may be instanceof 'TileData' without being a tiling.

       In CHECKCOUNT case, return 0 if checkcount exceeds nodeCount;
       if checkcount <= nodeCount, then FLOWER info could be
       inconsistent with the packing.
       
       NEUTRAL is for data that doesn't depend on nodecount match.
       
       4/2021: distinguishing dcel ("BOUQUET:" vs. "FLOWERS:"): 
       Processing dcel structure changes vertex indices, so we
       use 'readOldNew[]' and call 'rON' to read data
       based on old indices into appropriate new indices. 
       
	* @param fp BufferedReader (open)
	* @param filename String
	* @return int 'flags' encodes what was read, -1 or 0 on error
    */
    public int readpack(BufferedReader fp,String filename) {
    	readOldNew=null;
    	try {
    		fp.mark(1000); // mark, only needed for Lite form
    	} catch(Exception ex) {} // proceed anyway
    	getDispOptions=null; 
    	int newAlpha=0;
    	int newGamma=-1;
        int flags = 0; 
        int vert = 0;
        double x,y;
        double f;
        boolean newPacking = false;
        boolean gotFlowers = false;
        boolean col_c_flag = false;
        boolean col_f_flag = false;
        EdgeLink vertMarks=null; // holds optional marks 
        boolean dcelread=false; // true, dcel data, triggered by key "BOUQUET:"
        
        PackState state = PackState.INITIAL;
        String line;

        // Must first find 'NODECOUNT:', 'CHECKCOUNT:', 'TILECOUNT:', or 'NEUTRAL:' 
        try {
            while(state==PackState.INITIAL && (line=StringUtil.ourNextLine(fp))!=null) {
                StringTokenizer tok = new StringTokenizer(line);
                while(tok.hasMoreTokens()) {
                    String mainTok = tok.nextToken();
                    if(mainTok.equals("NODECOUNT:")) {
                        flags |= 0001;
                        newPacking=true;
                        fileName = "";
                        hes = 0;
                       	int intdata = Integer.parseInt(tok.nextToken());
                       	if(intdata < 3) {
                           // TODO: error message 
                        	return 0;
                        }
                        alloc_pack_space(intdata,false);
                        nodeCount = intdata;
                        tileData=null;
                        state = PackState.NODECOUNT;
                    }
                    else if(mainTok.equals("CHECKCOUNT:")) {
                        if(!status) {
                            flashError("Pack "+packNum+" is empty; can't read extra data");
                            return 0;
                        }
                        // sometimes we want to proceed even if counts don't match
                        try {
                        	mainTok=tok.nextToken();
                        	int intdata = Integer.parseInt(mainTok);
                        	if(intdata!=nodeCount) { 
                        		flashError("CHECKCOUNT failed to match 'nodeCount'");
                        		return -1;
                        	}
                        } catch (Exception ex) {
                        	flashError("CHECKCOUNT w/o number: proceed with reading");
                        }
                        state = PackState.CHECKCOUNT;
                    }
                    else if (mainTok.equals("TILECOUNT:")) {
                        flags |= 0001;
                        flags |= 020000;
                        newPacking=true;
                        fileName = "";
                        hes = 0;
                        String chaug=tok.nextToken();
                        int intdata=-1;
                        
                        // my have " (augmented) " before count
                        if (chaug.startsWith("(aug"))
                        	intdata=Integer.parseInt(tok.nextToken());
                        else 
                        	intdata = Integer.parseInt(chaug);
                       	if(intdata < 1) {
                           // TODO: error message 
                        	return 0;
                        }
                        alloc_pack_space(intdata,false);
                        tileData=new TileData(this,intdata,3); // default to tile mode 3
                        nodeCount=0; // this should be updated with FLOWERS processing
                        state = PackState.TILECOUNT;
                    }
                    else if (mainTok.startsWith("TRIANGULATION")) {
                        flags |= 0001;
                        newPacking=true;
                        fileName = "";
                        hes = 0;
                       	int intdata = Integer.parseInt(tok.nextToken());
                       	if(intdata < 3) {
                            flashError("Error in TRIANGULATION data: less than 3 faces");
                        	return 0;
                        }
                        alloc_pack_space(intdata,false);
                        nodeCount = intdata;
                        tileData=null;
                    	state=PackState.TRIANGULATION;
                    	break;
                    }
                    else if (mainTok.equals("1234321")) {
                    	state=PackState.PACKLITE;
                    }
                    else if (mainTok.equals("NEUTRAL:")) {
                    	state=PackState.NEUTRAL;
                    }
                    
                    // last hope: this is a raw triangulation
                    else {
                    	
                    	try {
                    		Integer.parseInt(mainTok);
                    	} catch (Exception ex) {
                    		break;
                    	}
                    	tok = new StringTokenizer(line);
                    	if (tok.countTokens()<3)
                    		break;
                    	state=PackState.TRIANGULATION;
                        while(tok.hasMoreTokens()) {
                            tok.nextToken();
                        }
                    	fp.reset();
                    	newPacking=true;
                        flags |= 0001;
                        break;
                    }
                }
            } // end of while for various 'PackState's
        } catch (Exception ex) {
        	CirclePack.cpb.errMsg("Exception in reading '"+filename+"'.");
        	return -1;
        }
        
        // didn't get necessary key word at start
        if (state==PackState.INITIAL) { 
        	CirclePack.cpb.errMsg("Read of '"+filename+
        			"' failed: 'NODECOUNT:', 'CHECKCOUNT:', 'TILECOUNT:', 'TRIANGULATION:' or PackLite magic number not at top");
        	return -1;
        }
        if (state==PackState.PACKLITE) {
        	PackData newPack=null;
        	try {
        		fp.reset();
        		newPack=PackData.readLite(fp,"lite");
        		if (newPack==null) 
        			throw new ParserException();
        	} catch(Exception ex) {
    			flashError("failed in reading file as 'PackLite'");
    			return -1;
        	}

       		this.status=true;
       		this.packExtensions=new Vector<PackExtender>(2); // trash extenders
       		this.nodeCount=newPack.nodeCount;
       		this.hes=newPack.hes;
       		this.cpScreen.setGeometry(this.hes);
       		this.intrinsicGeom=newPack.intrinsicGeom;
       		this.alpha=newPack.alpha;
       		this.gamma=newPack.gamma;
       		this.sizeLimit=newPack.sizeLimit;
       		this.kData=newPack.kData;
       		this.rData=newPack.rData;
       		this.poisonEdges=newPack.elist;
       		this.poisonVerts=newPack.vlist;
       		this.vertexMap=null;
       		this.xyzpoint=null;
       		this.vlist=null;
       		this.flist=null;
       		this.elist=null;
       		this.blist=null;
       		this.glist=null;
       		this.tlist=null;
       		this.zlist=null;
       		this.overlapStatus=false;
       		this.free_overlaps();
       		this.locks=0;
       		this.tileData=newPack.tileData;
       		this.setCombinatorics();
			
       		flags |= 00033;
       		return flags;
        }
        // Reaching here, state must be NODECOUNT, CHECKCOUNT, TILECOUNT, 
        //     PACKLITE, TRIANGULATION or NEUTRAL
        // If NODECOUNT, must get FLOWERS next (can pick up PACKNAME, 
        //    ALPHA..,, GEOM along the way); if TILECOUNT, must get TILES;
        //    if PACKLITE send to 'readLite'
        try {
            while(newPacking && !gotFlowers 
            		&& (line=StringUtil.ourNextLine(fp))!=null) {
                StringTokenizer tok = new StringTokenizer(line);
                while(tok.hasMoreTokens()) {
                    String mainTok = tok.nextToken();
                	if(mainTok.equals("PACKNAME:")) {
                		if (tok.hasMoreElements()) {
                			fileName = tok.nextToken();
                		}
                	}
                    else if(mainTok.equals("GEOMETRY:") && newPacking) {
                    	hes=0; // eucl by default
                		if (tok.hasMoreElements()) {
                			String ts=tok.nextToken();
                            if(ts.contains("yp") || ts.contains("YP")) {hes=-1;}// hyperbolic
                            else if(ts.contains("ph") || ts.contains("PH")) {hes=1;} // spherical
                		}
                    }
                    else if(mainTok.equals("ALPHA/BETA/GAMMA:")) {
                    	try {
                    		newAlpha = Integer.parseInt(tok.nextToken());
                    		beta = Integer.parseInt(tok.nextToken());
                    		newGamma = Integer.parseInt(tok.nextToken());
                    	} catch(Exception ex){continue;}
                    }
                    else if ((state==PackState.NODECOUNT && 
                    		(mainTok.equals("FLOWERS:") || mainTok.equals("BOUQUET:"))) ||
                    		(state==PackState.TILECOUNT && (mainTok.equals("TILES:") || 
                    				mainTok.equals("TILEFLOWERS:")))) {
                		
                    	// read all or until error. 
                    	if (mainTok.equals("BOUQUET:"))
                    		dcelread=true;
                        gotFlowers = true;
                    	int num;
                    	
                    	// normal packing
                    	if (state==PackState.NODECOUNT) { 
                    		try {
                    			int[][] bouquet=new int[nodeCount+1][];
                    			// data: contiguous index order, starting at v=1.
                    			// must have v  n v_0 .... v_n is all on one line
                    			while (vert<nodeCount && (line=StringUtil.ourNextLine(fp))!=null) {
                    				StringTokenizer loctok = new StringTokenizer(line);
                    				vert=Integer.valueOf(loctok.nextToken());
                    				num=Integer.valueOf(loctok.nextToken());
                    				if (num<=0) 
                    					throw(new Exception()); // bomb out
                    				bouquet[vert]=new int[num+1];
                    			
                    				for (int i=0;i<=num;i++) {
                    					bouquet[vert][i]=Integer.valueOf(loctok.nextToken());
                    				}
                    				setBdryFlag(vert,0);
                    			} // end of while
                    			if (vert<nodeCount) {
                    				flashError("Read failed while getting flowers");
                    				return -1;
                    			}
                    			// this is dcel data
                    			if (dcelread) {
                    				PackDCEL pdc;
                    				if (newAlpha==0)
                    					newAlpha=alpha;
                    				if ((pdc=CombDCEL.getRawDCEL(bouquet,newAlpha))==null) {
                    					flashError("Problem reading DCEL data");
                    					return -1;
                    				}
                    				pdc.redChain=null;
                    				pdc.fixDCEL_raw(this);
                    				if (pdc.newOld!=null) {
                						readOldNew=new int[pdc.vertCount+1];
                    					Iterator<EdgeSimple> vmp=pdc.newOld.iterator();
                    					while (vmp.hasNext()) {
                    						EdgeSimple edge=vmp.next();
                    						readOldNew[edge.w]=edge.v;
                    					}
                    				}
                    			}
                    			// traditional packing
                    			else {
                    				for (int i=1;i<=nodeCount;i++) {
                    					kData[i].num=bouquet[i].length-1;
                    					kData[i].flower=bouquet[i];
                    					kData[i].plotFlag=1;
                    				}
                    				try {
                    					if (complex_count(true)<=0)
                    						flashError("Failed to set packing combinatorics (may be tiling data)");
                    				} catch (Exception ex) {
                    					flashError("Exception setting packing combinatorics (may be tiling data)");
                    				}
//                        			status=false;
//                        			return -1;
                    				for (int i=1;i<=faceCount;i++) 
                    					faces[i].plotFlag=1;
                    				
                    			}
                    		} catch(Exception ex){ // try to reset to previous line and proceed
                    			try {fp.reset();} catch(IOException ioe) {
                    				flashError("IOException: "+ioe.getMessage());
                    				return -1;
                    			}
                    		}
                    	}
                    	
                    	// else TILECOUNT: two options, TILES: or TILEFLOWERS:
                    	else {
                    		if (mainTok.equals("TILES:")) { // don't know 'nodeCount', and indices may not be
                    				// in sequence; find largest index for now, handled in 'tiles2packing'

                        		try {
                        			boolean augmented=false;
                                    if (tok.hasMoreTokens()) {
                                        if (tok.nextToken().startsWith("(aug"))
                                        		augmented=true;
                                    }
                        			int tick=1;
                        			nodeCount=0;
                                	// TILES data lines: 't n   v_0 v_1 ... v_(n-1)' where
                                	//   t=tile number (1 to tileCount); n=number of vertices;
                                	//   and list of verts (NOT neighboring tiles).
                        			// If "augmented", there are three vertices added between
                        			//    each pair of corners.
                        			// Note: tiles from 1 to tileCount, irrespective of given t.
                    				while (tick<=tileData.tileCount && (line=StringUtil.ourNextLine(fp))!=null) {
                    					StringTokenizer loctok = new StringTokenizer(line);
                    					@SuppressWarnings("unused")
										int t=Integer.valueOf(loctok.nextToken()); // disregard t
                    					num=Integer.valueOf(loctok.nextToken());
                            			if (num<=0) throw(new Exception()); // bomb out
                            			tileData.myTiles[tick]=new Tile(this,tileData,num);
                            			tileData.myTiles[tick].tileIndex=tick;
                            			if (augmented) { 
                            				tileData.myTiles[tick].augVertCount=4*num;
                            				tileData.myTiles[tick].augVert=new int[4*num];
                            			}
                            			for (int i=0;i<num;i++) {
                            				int nextp=Integer.valueOf(loctok.nextToken());
                            				nodeCount=(nextp>nodeCount)? nextp:nodeCount;
                            				tileData.myTiles[tick].vert[i]=nextp;
                            				// if augmented, there are 3 augVerts between each pair of verts
                            				if (augmented) { 
                                				tileData.myTiles[tick].augVert[4*i]=nextp;
                                				for (int ii=1;ii<=3;ii++) {
                                					nextp=Integer.valueOf(loctok.nextToken());
                                					nodeCount=(nextp>nodeCount)? nextp:nodeCount;
                                					tileData.myTiles[tick].augVert[4*i+ii]=nextp;
                                				}
                            				}
                            			}
                            			tick++;
                    				} // end of while
                    				if (tick<(tileData.tileCount+1)) {
                            			flashError("error: TILECOUNT not reached");
                        				return -1;
                    				}
                            	} catch(Exception ex){ // try to reset to previous line and proceed
                            		try {fp.reset();} catch(IOException ioe) {
                            			flashError("IOException: TILECOUNT, TILES, "+ex.getMessage());
                            			return -1;
                            		}
                            	}
                        	} // done with TILES:
                    		
                    		// else TILEFLOWERS: for each tile there's a line
                    		else {
                    			try {
                           			int tick=1;
                        			nodeCount=0;
                                	// TILEFLOWERS: data lines: 't n  t0 e0  t1 e1   t2 e2  ...  t(n-1) e(n-1)' 
                                	//   t=tile number (1 to tileCount); n=number of edges;
                                	//   and list of pairs, tj = index of tile across (or 0) and ej is the index 
                        			//      in tile tj of its corresponding edge shared with t.
                        			//   This allows for multiple edges or sharing edges with itself
                        			// Note: tiles from 1 to tileCount, given 't' value is ignored
                    				while (tick<=tileData.tileCount && (line=StringUtil.ourNextLine(fp))!=null) {
                    					StringTokenizer loctok = new StringTokenizer(line);
                    					// TODO: may want to allow more flexible formating: e.g.,
                    					//       prescribe indexes, let them be out of order of
                    					//       have some that are missing. I don't know all the
                    					//       consequences, so leave this for now.
                    					@SuppressWarnings("unused")
										int t=Integer.valueOf(loctok.nextToken()); // ignore t
                    					int vCount=Integer.valueOf(loctok.nextToken());
                            			if (vCount<=0) throw(new Exception()); // bomb out
                            			Tile tile=new Tile(this,tileData,vCount);
                            			tileData.myTiles[tick]=tile;
                            			tile.tileIndex=tick;
                        				tile.tileFlower=new int[vCount][2];

                        				// read off the t e pairs: 
                        				// We may want to read just a portion of the tiling,
                        				//    so we zero out (make into bdry) any tj ej pair
                        				//    with tj greater thatn tileCount
                            			for (int i=0;i<vCount;i++) {
                            				int tt=Integer.valueOf(loctok.nextToken());
                            				int te=Integer.valueOf(loctok.nextToken());
                            				if (tt>tileData.tileCount) {
                            					tt=0;
                            					te=0;
                            				}
                            				tile.tileFlower[i][0]=tt;
                            				tile.tileFlower[i][1]=te;
                            			}
                            			tick++;
                    				} // end of while
                    				if (tick<(tileData.tileCount+1)) {
                            			flashError("error: TILECOUNT not reached");
                        				return -1;
                    				}
                    			} catch(Exception ex){ // try to reset to previous line and proceed
                            		try {fp.reset();} catch(IOException ioe) {
                            			flashError("IOException: TILECOUNT, TILEFLOWERS, "+ex.getMessage());
                            			return -1;
                            		}
                            	}
                    			
                    		}
                    	} // end of TILES:/TILEFLOWERS: branch
                    	
                    	// success: other settings, toss old data
                    	status=true; 
                    	firstRedEdge=null;
                    	firstRedFace=0;
                    	firstFace=0;
                    	vlist=null;
                    	elist=null;
                    	flist=null;
                    	glist=null;
                    	zlist=null;
                    	blist=null;
                    	vertexMap=null;
                    	xyzpoint=null;
                    	overlapStatus=false;
                    	try {
                    		free_overlaps();
                    	} catch(Exception ex)
                    	{}
                    	poisonEdges=null;
                        poisonVerts=null;  
                        locks=0;
                    } // done with "FLOWERS", "BOUQUET", or "TILES"
                    else if (state==PackState.TRIANGULATION && !gotFlowers) {
                    	Triangulation tri=new Triangulation();
                		Vector<Face> theFaces=new Vector<Face>(50);
                		boolean okay=true;
                		Face face=null;
                		do {
                			tok = new StringTokenizer(line);
                			if (tok.countTokens()!=3) { 
                				okay=false;
                				fp.reset();
                			}
                			else {
                				try {
                					face=new Face();
                					face.vert[0]=Integer.parseInt((String)tok.nextToken());
                					face.vert[1]=Integer.parseInt((String)tok.nextToken());
                					face.vert[2]=Integer.parseInt((String)tok.nextToken());
                    				theFaces.add(face);
                    				fp.mark(2000);
                    			} catch(Exception ex) {
                					okay=false;
                					fp.reset();
                				}
                			}
                		} while (okay && (line=StringUtil.ourNextLine(fp,true))!=null);
                		int Nfaces=theFaces.size();
                  		if (Nfaces<3) 
                  			throw new InOutException("Reading triangulation: found less than 3 faces");
                		tri.faces=new Face[Nfaces+1];
                		tri.faceCount=Nfaces;
                		for (int j=0;j<theFaces.size();j++)
                			tri.faces[j+1]=(Face)theFaces.elementAt(j);
                		
                		// create PackData
                		PackData pdata=Triangulation.tri_to_Complex(tri,0);
                		if (pdata==null) 
                			throw new CombException("Failed while reading TRIANGULATION");
               			this.nodeCount=pdata.nodeCount;
               			this.kData=pdata.kData;
               			this.rData=pdata.rData;
               			this.faces=pdata.faces;
                			
               			// success: other settings, toss old data
               			this.status=true; 
               			this.firstRedEdge=null;
               			this.firstRedFace=0;
               			this.firstFace=0;
               			this.vlist=null;
               			this.elist=null;
               			this.flist=null;
               			this.glist=null;
               			this.zlist=null;
               			this.blist=null;
               			this.vertexMap=null;
               			this.xyzpoint=null;
               			this.overlapStatus=false;
                       	try {
                       		free_overlaps();
                       	} catch(Exception ex)
                       	{}
                       	this.poisonEdges=null;
                       	this.poisonVerts=null;  
                       	this.locks=0;
                       	this.packExtensions=new Vector<PackExtender>(2);
                       	
                       	// fix up 
                       	this.setCombinatorics();
               			this.hes=this.intrinsicGeom;
                       	this.set_aim_default();
                       	gotFlowers=true;
                       	break;
                    } // end of TRIANGULATION case
                }
                
                // if TILECOUNT, then create the barycentric packing and return
                if (state==PackState.TILECOUNT && gotFlowers) {
                	
                	// =========== main action, creating the packing =====
                	PackData newPack=TileData.tiles2packing(tileData);
                	
    				if (newPack==null) {
    	    			flashError("failed somehow in 'tiles2packing'");
    	    			return -1;
    				}
    				this.status=true;
    				this.packExtensions=new Vector<PackExtender>(2); // trash extenders
    				this.nodeCount=newPack.nodeCount;
    				this.hes=newPack.hes;
    				this.intrinsicGeom=newPack.intrinsicGeom;
    				this.alpha=newPack.alpha;
    				this.gamma=newPack.gamma;
    				this.sizeLimit=newPack.sizeLimit;
    				this.kData=newPack.kData;
    				this.rData=newPack.rData;
    				this.poisonEdges=newPack.elist;
    				this.poisonVerts=newPack.vlist;
    				this.vertexMap=null;
    				this.xyzpoint=null;
    				this.vlist=null;
    				this.flist=null;
    				this.elist=null;
    				this.blist=null;
    				this.glist=null;
    				this.tlist=null;
    				this.zlist=null;
    				this.overlapStatus=false;
    				this.free_overlaps();
    				this.locks=0;
    				this.tileData=newPack.tileData;
    				this.complex_count(false);
    				this.setCombinatorics();
    				
                	flags |= 020001;
                	return flags;
                }
                
                if (gotFlowers)
                	state=PackState.INITIAL;
                
            } // end of while for FLOWERS/GEOM/ etc.
        } catch(Exception ex){
            flashError("Read of "+filename+" failed: "+ex.getMessage());
            return -1;
        }

        // Now, search for the rest of the data (have to allow picking up GEOM, 
        //    ABG, PACKNAME here too). 
        try {
        	boolean foundEnd=false;
        	while((line=StringUtil.ourNextLine(fp))!=null) {
        		StringTokenizer tok = new StringTokenizer(line);
        		while(tok.hasMoreTokens()) {
                    String mainTok = tok.nextToken();
                	
                    // should have already been read if newPacking
                    if (mainTok.equals("FLOWERS:") || mainTok.equals("BOUQUET:")) { 
                        flashError("FLOWERS/BOUQUET not allowed w/o NODECOUNT/TILECOUNT; disregard them");
                    	continue; 
                    }
                    else if(mainTok.equals("PACKNAME:")) {
                		if (tok.hasMoreElements()) {
                			fileName = tok.nextToken();
                		}
                	}
                    else if(mainTok.equals("GEOMETRY:") && newPacking) {
                    	hes=0; // eucl by default
                		if (tok.hasMoreElements()) {
                			String ts=tok.nextToken();
                            if(ts.contains("yp")) {hes=-1;}// hyperbolic
                            else if(ts.contains("ph")) {hes=1;} // spherical
                		}
                    }
                    else if(mainTok.equals("ALPHA/BETA/GAMMA:")) {
                    	try {
                    		newAlpha = Integer.parseInt(tok.nextToken());
                    		beta = Integer.parseInt(tok.nextToken());
                    		newGamma = Integer.parseInt(tok.nextToken());
                    	}catch(Exception ex){continue;}
                    }
                    else if(mainTok.equals("RADII:")){
                        vert=1;
                    	// data must start on new line; read all or until error.
                    	try {
                    		while (vert<=nodeCount && (line=StringUtil.ourNextLine(fp))!=null) {
                    			StringTokenizer loctok = new StringTokenizer(line);
                    			f=Double.parseDouble(loctok.nextToken());
                    			setRadiusActual(rON(vert),f);
                    			vert++;
                    			while (vert<=nodeCount && loctok.hasMoreElements()) {
                    				f=Double.parseDouble(loctok.nextToken());
                    				setRadiusActual(rON(vert),f);
                    				vert++;
                    			}
                    		}
                    	} catch(Exception ex){ // try to reset to previous line and proceed
                        	if (vert<=nodeCount) {
                        		flashError("Shortage in number of radii; remainder set to default");
                            	double rad=0.5;
                            	if (hes<0) rad=1.0-Math.exp(-1.0);
                        		for (int i=vert;i<=nodeCount;i++) 
                            		setRadius(rON(i),rad);
                        	}
                    		try {fp.reset();} catch(IOException ioe) {
                    			flashError("IOException: "+ioe.getMessage());
                    			return -1;
                    		}
                    	}
                    	state=PackState.INITIAL; 
                    	if (vert<=nodeCount) {
                    		flashError("Shortage in number of radii; remainder set to default");
                        	double rad=0.5;
                        	if (hes<0) rad=1.0-Math.exp(-1.0);
                    		for (int i=vert;i<=nodeCount;i++) 
                        		setRadius(rON(i),rad);
                    	}
                    	else flags |= 0010;
                    }
                    else if (mainTok.equals("CENTERS:")) {
                    	vert=1;
                    	// data must start on new line; read all or until error.
                    	try {
                    		while (vert<=nodeCount && (line=StringUtil.ourNextLine(fp))!=null) {
                    			StringTokenizer loctok = new StringTokenizer(line);
                    			x=Double.parseDouble(loctok.nextToken());
                    			y=Double.parseDouble(loctok.nextToken());
                    			setCenter(rON(vert),x,y);
                    			vert++;
                    			while (vert<=nodeCount && loctok.hasMoreElements()) {
                    				x=Double.parseDouble(loctok.nextToken());
                    				y=Double.parseDouble(loctok.nextToken());
                    				setCenter(rON(vert),x,y);
                    				vert++;
                    			}
                    		}
                    	} catch(Exception ex){ // try to reset to previous line and proceed
                        	if (vert<=nodeCount) {
                        		flashError("Shortage in number of centers; remainder set to zero");
                            	Complex z=new Complex(0.0,0.0);
                        		for (int i=vert;i<=nodeCount;i++) 
                            		setCenter(rON(i),z);
                        	}
                    		try {fp.reset();} catch(IOException ioe) {
                    			flashError("IOException: "+ioe.getMessage());
                    			return -1;
                    		}
                    	}
                    	if (vert<=nodeCount) {
                    		flashError("Shortage in number of centers; remainder set to zero");
                        	Complex z=new Complex(0.0,0.0);
                    		for (int i=vert;i<=nodeCount;i++) 
                        		setCenter(rON(i),z);
                    	}
                    	else flags |= 0020;
                    	state=PackState.INITIAL; 
                    }    

                    else if (mainTok.equals("TILES:")) {
                    	try {
                			boolean augmented=false;
                			int tick=-1;
                            if (tok.hasMoreTokens()) {
                    			String nxtok=tok.nextToken();
                    			if (nxtok.startsWith("(aug")) {
                                		augmented=true;
                                		tick=Integer.parseInt(tok.nextToken()); // get tileCount
                    			}
                    			else // current token should be tileCount
                    				tick=Integer.parseInt(nxtok); 
                            }
                            else
                            	throw new DataException("usage: 'tileCount' is missing");
                            
                    		tileData=new TileData(this,tick,3); // default to tile mode 3
                    		tick=1;
                        	// TILES data lines: 't  n  x  v_0 v_1 ... v_(n-1)' where
                        	//   t=tile number (1 to tileCount); n=number of vertices; 
                        	//   and list of verts (NOT neighboring tiles).
                    		//   If "augmented", there are 3 additional vertices between
                    		//      each pair of corners.
                			// Note: tiles from 1 to tileCount, irrespective of given t.
                    		while (tick<=tileData.tileCount && (line=StringUtil.ourNextLine(fp))!=null) {
                    			StringTokenizer loctok = new StringTokenizer(line);
                    			Integer.valueOf(loctok.nextToken()); // disregard t
                    			int num=Integer.valueOf(loctok.nextToken());
                    			if (num<=0) throw(new Exception()); // bomb out
                    			tileData.myTiles[tick]=new Tile(this,tileData,num);
                    			tileData.myTiles[tick].tileIndex=tick;
                    			if (augmented) { 
                    				tileData.myTiles[tick].augVertCount=4*num;
                    				tileData.myTiles[tick].augVert=new int[4*num];
                    			}
                    			for (int i=0;i<num;i++) {
                    				int nextp=Integer.valueOf(loctok.nextToken());
                    				tileData.myTiles[tick].vert[i]=nextp;
                    				// if augmented, there are 3 augVerts between each pair of verts
                    				if (augmented) { 
                        				tileData.myTiles[tick].augVert[4*i]=nextp;
                        				for (int ii=1;ii<=3;ii++) {
                        					nextp=Integer.valueOf(loctok.nextToken());
                        					nodeCount=(nextp>nodeCount)? nextp:nodeCount;
                        					tileData.myTiles[tick].augVert[4*i+ii]=nextp;
                        				}
                    				}
                    			}
                    			tick++;
                    		} // end of while
                    		if (tick<=tileData.tileCount) {
                    			flashError("error: TILECOUNT not reached");
                    			return -1;
                    		}
                    	}  catch(Exception ex){ // try to reset to previous line and proceed
                    		try {fp.reset();} catch(IOException ioe) {
                    			flashError("IOException: TILES, "+ioe.getMessage());
                    			return -1;
                    		}
                    	}
                    }
                    else if(mainTok.equals("DISP_FLAGS:")) {
                    	// flags on the next line
                    	getDispOptions=StringUtil.ourNextLine(fp);
                    	// may start with "Disp" or "disp"
                    	if (getDispOptions.startsWith("disp") || getDispOptions.startsWith("Disp")) {
                    		int k=getDispOptions.indexOf(" ");
                    		getDispOptions=getDispOptions.substring(k).trim();
                    	}
                    	// else, first entry should be a flag
                    	else if (!getDispOptions.startsWith("-"))
                    		getDispOptions=null;
                    	
                    	if (getDispOptions.length()==0)
                    		getDispOptions=null;
                    }
                    
                    // OBE; don't use this 
                    else if(mainTok.equals("CIRCLE_PLOT_FLAGS:") && !newPacking){
                        state = PackState.CIRCLE_PLOT_FLAGS;
                		while (state==PackState.CIRCLE_PLOT_FLAGS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=Integer.parseInt(str);
                				int flg=Integer.parseInt((String)loctok.nextToken());
                				kData[v].plotFlag=flg;
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                		flags |= 01000;
                    }
// TODO: problem with reading TILES in an existing packing is that we don't
//       have 'newOrig' vertex translation info.                    
//                  else if (mainTok.equals("TILES:")) { 
//           		}
                    
                    // OBE; don't use this 
                    else if(mainTok.equals("FACE_PLOT_FLAGS:") && !newPacking){
                        state = PackState.FACE_PLOT_FLAGS;
                  		while (state==PackState.FACE_PLOT_FLAGS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=Integer.parseInt(str);
                				int v1=Integer.parseInt((String)loctok.nextToken());
                				int v2=Integer.parseInt((String)loctok.nextToken());
                				int flg=Integer.parseInt((String)loctok.nextToken());
                				int[] faceFlower=getFaceFlower(v);
                				for (int j=0;j<countFaces(v);j++) {
                					int k=faceFlower[j];
                					int ind;
                					if ( ((ind=check_face(k,v,v1)) >= 0 && faces[k].vert[(ind+2)%3]==v2)
                         				 || ((ind=check_face(k,v1,v)) >= 0 && faces[k].vert[(ind+2)%3]==v2) ) {
                						faces[k].plotFlag=flg;
                						j=countFaces(v); // to stop loop 
                					}
                				}
                				} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                        flags |= 01000;
                    }
                    else if(mainTok.equals("SELECT_RADII:") && !newPacking) {
                    	// only for CHECKCOUNT cases
                        state = PackState.SELECT_RADII;
                		while (state==PackState.SELECT_RADII 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				double rad=Double.parseDouble((String)loctok.nextToken());
                				setRadiusActual(v,rad);
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}

                        flags |= 0010;
                    }
                    else if(mainTok.equals("ANGLE_AIMS:")){
                        state = PackState.ANGLE_AIMS;
                        if (newPacking) set_aim_default();
                		while (state==PackState.ANGLE_AIMS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				double aim=Double.parseDouble((String)loctok.nextToken());
                				setAim(v,aim);
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                		flags |= 00004;
                    }
                    else if(mainTok.equals("INV_DISTANCES:")){
                        state = PackState.INV_DISTANCES;
                        if (newPacking || !overlapStatus) alloc_overlaps();
                		while (state==PackState.INV_DISTANCES 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				int w=rON(Integer.parseInt((String)loctok.nextToken()));
                				double invDist=Double.parseDouble((String)loctok.nextToken());
                				this.set_single_invDist(v,w,invDist);
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                		flags |= 00004;
                    }
                    else if(mainTok.equals("ANGLESUMS:")){
                        state = PackState.ANGLESUMS;
                        int v=1;
                		while (state==PackState.ANGLESUMS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			while (state==PackState.ANGLESUMS 
                					&& loctok.hasMoreTokens() && v<=nodeCount) {
                				try {
                					String str=(String)loctok.nextToken();
                					double anglesums=Double.parseDouble(str);
                					setCurv(rON(v),anglesums);
                					v++;
                				} catch(Exception ex) {state=PackState.INITIAL;}
                			}
                		}
                		flags |= 0040;
                    }
                    // 'real' Schwarzian for edges, see 'Schwarzian.java'
                    // TODO: Have to adjust for dcel structures.
                    else if (mainTok.equals("SCHWARZIANS:")) { 
                    	state=PackState.SCHWARZIAN;
                    	int v=1;
                		while (state==PackState.SCHWARZIAN 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			while (state==PackState.SCHWARZIAN 
                					&& loctok.hasMoreTokens() && v<=nodeCount) {
                				try {
                					int num=countFaces(v);
                					kData[v].schwarzian=new double[num+1];
                					if (loctok.countTokens()!=num+1)
                						throw new InOutException("");
                					// shuck first entry, vertex 
                					loctok.nextToken(); 
                					for (int j=0;j<num;j++)
                						kData[v].schwarzian[j]=Double.parseDouble((String)loctok.nextToken());
                					v++;
                				} catch(Exception ex) {state=PackState.INITIAL;}
                			}
                		}                    	
                		flags |= 020000000;
                    }
                    else if(mainTok.equals("C_COLORS:")){ // note: replaces CIRCLE_COLORS that used old indices
                        state = PackState.C_COLORS;
                        if (newPacking) for (int i=1;i<=nodeCount;i++) 
                        	setCircleColor(rON(i),ColorUtil.getFGColor());
                		while (state==PackState.C_COLORS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				int rd=(int)Math.floor(Double.parseDouble((String)loctok.nextToken()));
                				int gn=(int)Math.floor(Double.parseDouble((String)loctok.nextToken()));
                				int bl=(int)Math.floor(Double.parseDouble((String)loctok.nextToken()));
                        	    setCircleColor(v,new Color(rd,gn,bl));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  	  	flags |= 0400;
                  	  	col_c_flag=true;
                    }
                    else if(mainTok.equals("CIRCLE_COLORS:")){ // OBE: use 'Color' objects now (see C_COLORS)
                        state = PackState.CIRCLE_COLORS;
                        if (newPacking) for (int i=1;i<=nodeCount;i++) setCircleColor(i,ColorUtil.getFGColor());
                		while (state==PackState.CIRCLE_COLORS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				int col=Integer.parseInt((String)loctok.nextToken());
                        	    setCircleColor(v,ColorUtil.cloneMe(ColorUtil.coLor(col)));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  	  	flags |= 0400;
                  	  	col_c_flag=true;
                    }
                    else if(mainTok.equals("VERTEX_MAP:")){
                        state = PackState.VERTEX_MAP;
                        VertexMap vertMap=new VertexMap();
                		while (state==PackState.VERTEX_MAP 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				int V=Integer.parseInt((String)loctok.nextToken());
                        	    vertMap.add(new EdgeSimple(v,V));
                			} catch(Exception ex) {
                					state=PackState.INITIAL;
                				}
                		}
                		if (vertMap!=null && vertMap.size()>0) {
                			this.vertexMap=vertMap;
                  	  		flags |= 0100;
                		}
                    }
                    else if(mainTok.equals("VERT_MARK:")){
                        state = PackState.VERT_MARK;
                        vertMarks=new EdgeLink(this);
                		while (state==PackState.VERT_MARK 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				int m=Integer.parseInt((String)loctok.nextToken());
                        	    vertMarks.add(new EdgeSimple(v,m));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                    }
                    // for tile colors, must have 'tileData' already
                    else if (mainTok.equals("TILE_COLORS:") && tileData!=null) { 
                    	state=PackState.TILE_COLORS;
                		while (state==PackState.TILE_COLORS
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			NodeLink vlist=null;
                			try {
                				String str=(String)loctok.nextToken();
                				int tc=Integer.parseInt(str); // tilecount
                				vlist=new NodeLink();
                				for (int n=0;n<=tc;n++) 
                					vlist.add(Integer.parseInt((String)loctok.nextToken()));
                    			int tileindx=tileData.whichTile(vlist);
                    			if (tileindx>=0) {
                    				int rd=Integer.parseInt((String)loctok.nextToken());
                    				int gn=Integer.parseInt((String)loctok.nextToken());
                    				int bl=Integer.parseInt((String)loctok.nextToken());
                    				tileData.myTiles[tileindx].color=new Color(rd,gn,bl);
                    			}
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		} // end of while
                    }
                    // TODO: have to fix this for dcel structures
                    else if(mainTok.equals("TRI_COLORS:")){ // OBE: uses color indices (see T_COLORS)
                        state = PackState.TRI_COLORS;
                        if (newPacking) for (int i=1;i<=faceCount;i++) setFaceColor(i,ColorUtil.getFGColor());
                   		while (state==PackState.TRI_COLORS 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				int v1=rON(Integer.parseInt((String)loctok.nextToken()));
                				int v2=rON(Integer.parseInt((String)loctok.nextToken()));
                				int colindx=Integer.parseInt((String)loctok.nextToken());
                				int[] faceFlower=getFaceFlower(v);
                				for (int j=0;j<countFaces(v);j++) {
                					int k=faceFlower[j];
                					int ind;
                					if ( ((ind=check_face(k,v,v1)) >= 0 && faces[k].vert[(ind+2)%3]==v2)
                         				 || ((ind=check_face(k,v1,v)) >= 0 && faces[k].vert[(ind+2)%3]==v2) ) {
                						setFaceColor(k,ColorUtil.cloneMe(ColorUtil.coLor(colindx)));
                						j=countFaces(v); // to stop loop 
                					}
                				}
                				} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  	  	flags |= 0400;
                  	  	col_f_flag=true;
                    }
                    else if(mainTok.equals("VERT_LIST:")){
                        state = PackState.VERT_LIST;
                        vlist=new NodeLink(this);
        				String str=null;
                  		while (state==PackState.VERT_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                  			StringTokenizer loctok = new StringTokenizer(line);
                  			while (state==PackState.VERT_LIST && loctok.hasMoreTokens()) {
                  				try {
                  					str=(String)loctok.nextToken();
                					int v=rON(Integer.parseInt(str));
                					vlist.add(v);
                  				} catch(Exception ex) {state=PackState.INITIAL;}
                  			}
                		}
                  		if (vlist.size()==0) vlist=null;
                  		else flags |= 0200;                       
                    }
                    else if(mainTok.equals("GLOBAL_VERT_LIST:")){
                        state = PackState.GLOBAL_VERT_LIST;
                        CPBase.Vlink=new NodeLink(this);
                  		while (state==PackState.GLOBAL_VERT_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                				int v=rON(Integer.parseInt(str));
                				CPBase.Vlink.add(v);
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (CPBase.Vlink.size()==0) CPBase.Vlink=null;
                  		else flags |= 020000;                       
                    }
                    // TODO: fix this for dcel structure
                    else if(mainTok.equals("FACE_TRIPLES:")){
                    	state = PackState.FACE_TRIPLES;
                        flist=new FaceLink(this);
                  		while (state==PackState.FACE_TRIPLES 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                   				int v=rON(Integer.parseInt(str));
                				int v1=rON(Integer.parseInt((String)loctok.nextToken()));
                				int v2=rON(Integer.parseInt((String)loctok.nextToken()));
                				int k=what_face(v,v1,v2);
                				if (k>=1) flist.add(k);
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (flist.size()==0) flist=null;
                  		else flags |= 0200;                       
                    }
                    else if(mainTok.equals("GLOBAL_FACE_LIST:")){
                    	state = PackState.GLOBAL_FACE_LIST;
                        CPBase.Flink=new FaceLink(this);
                  		while (state==PackState.GLOBAL_FACE_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                   				int v=Integer.parseInt(str);
                				CPBase.Flink.add(v);
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (CPBase.Flink.size()==0) CPBase.Flink=null;
                  		else flags |= 020000;                       
                    }
                    else if(mainTok.equals("EDGE_LIST:")){
                        state = PackState.EDGE_LIST;
                        elist=new EdgeLink(this);
                  		while (state==PackState.EDGE_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                   				int v=rON(Integer.parseInt(str));
                				int w=rON(Integer.parseInt((String)loctok.nextToken()));
                				elist.add(new EdgeSimple(v,w));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (elist.size()==0) 
                  			elist=null;
                  		else flags |= 0200;
                    }
                    else if(mainTok.equals("GLOBAL_EDGE_LIST:")){
                        state = PackState.GLOBAL_EDGE_LIST;
                        CPBase.Elink=new EdgeLink(this);
                  		while (state==PackState.GLOBAL_EDGE_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                   				int v=rON(Integer.parseInt(str));
                				int w=rON(Integer.parseInt((String)loctok.nextToken()));
                				CPBase.Elink.add(new EdgeSimple(v,w));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (CPBase.Elink.size()==0) 
                  			CPBase.Elink=null;
                  		else flags |= 020000;
                    }
                    else if(mainTok.equals("DUAL_EDGE_LIST:")){
                        state = PackState.DUAL_EDGE_LIST;
                        glist=new GraphLink(this);
                  		while (state==PackState.DUAL_EDGE_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                   				int v=Integer.parseInt(str);
                				int w=Integer.parseInt((String)loctok.nextToken());
                				glist.add(new EdgeSimple(v,w));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (glist.size()==0) 
                  			glist=null;
                  		else flags |= 0200;
                    }
                    else if(mainTok.equals("GLOBAL_DUAL_EDGE_LIST:")){
                        state = PackState.GLOBAL_DUAL_EDGE_LIST;
                        CPBase.Glink=new GraphLink(this);
                  		while (state==PackState.GLOBAL_DUAL_EDGE_LIST 
                				&& (line=StringUtil.ourNextLine(fp))!=null) {
                			StringTokenizer loctok = new StringTokenizer(line);
                			try {
                				String str=(String)loctok.nextToken();
                   				int v=Integer.parseInt(str);
                				int w=Integer.parseInt((String)loctok.nextToken());
                				CPBase.Glink.add(new EdgeSimple(v,w));
                			} catch(Exception ex) {state=PackState.INITIAL;}
                		}
                  		if (CPBase.Glink.size()==0) 
                  			CPBase.Glink=null;
                  		else flags |= 020000;
                    }
                    else if(mainTok.equals("POINTS:")){ // get N
                    	int N=nodeCount;
                    	try {
                			N=Integer.parseInt((String)tok.nextToken());
                		} catch(Exception ex) {
							  N=nodeCount; // default
                		}
               			if (N>=1 && N<=nodeCount) {
               				// Note: read N xyz's into CONSECUTIVE vertices
               				int v=1;
               				xyzpoint=new Point3D[nodeCount+1];
               				state = PackState.POINTS;
               				while(state==PackState.POINTS 
               						&& (line=StringUtil.ourNextLine(fp))!=null) {
               					StringTokenizer loctok = new StringTokenizer(line);
                       			try {
                    				String str=(String)loctok.nextToken();
                       				double X=Double.parseDouble(str);
                    				double Y=Double.parseDouble((String)loctok.nextToken());
                    				
                    				// may have just 2 coords
                    				double Z;
                    				try {
                    					Z=Double.parseDouble((String)loctok.nextToken());
                    				} catch(Exception ex) {
                    					Z=0.0;
                    				}
                    				
                    				xyzpoint[rON(v)]=new Point3D(X,Y,Z);
                    				v++;
                    			} catch(Exception ex) {state=PackState.INITIAL;}
                    		}
                      		if (v==1) xyzpoint=null;
                      		else flags |= 02000;         
               			}
                    }
                    else if(mainTok.equals("INTEGERS:")){ // get list of integers
                    	int N=nodeCount;
                    	try {
                			N=Integer.parseInt((String)tok.nextToken());
                		} catch(Exception ex) {
                			N=nodeCount; // likely value
                		}
                    	utilIntegers=new Vector<Integer>(N);
                    	state = PackState.INTEGERS;
                    	int di=0;
                        while(di<N && state==PackState.INTEGERS
                        		&& (line=StringUtil.ourNextLine(fp))!=null) {
                        	try {
                        		utilIntegers.add(Integer.parseInt(line));
                        	} catch(Exception ex) {
                        		di=N; // bomb out, leave what has been found
                        		state=PackState.INITIAL;
                        	}
                        } // end of while
                        
                  		if (utilIntegers.size()==0) utilIntegers=null;
                  		else flags |= 01000000;
                		state=PackState.INITIAL;
                    }
                    else if(mainTok.equals("DOUBLES:")){ // get list of double values
                    	int N=nodeCount;
                    	try {
                			N=Integer.parseInt((String)tok.nextToken());
                		} catch(Exception ex) {
                			N=nodeCount; // likely value
                		}
                    	utilDoubles=new Vector<Double>(N);
                    	state = PackState.DOUBLES;
                    	int di=0;
                        while(di<N && state==PackState.DOUBLES 
                        		&& (line=StringUtil.ourNextLine(fp))!=null) {
                        	try {
                        		double val=Double.parseDouble(line);
                        		utilDoubles.add(Double.valueOf(val));
                        	} catch(Exception ex) {
                        		di=N; // bomb out, leave what has been found
                        		state=PackState.INITIAL;
                        	}
                        } // end of while
                        
                  		if (utilDoubles.size()==0) utilDoubles=null;
                  		else flags |= 02000000;
                		state=PackState.INITIAL;
                    }
                    else if(mainTok.equals("COMPLEXES:")){ // get utility list of complex numbers
                    	int N=nodeCount;
                    	try {
                			N=Integer.parseInt((String)tok.nextToken());
                		} catch(Exception ex) {
                			N=nodeCount; // likely value
                		}
                    	utilComplexes=new Vector<Complex>(N);
                    	state = PackState.COMPLEXES;
                    	int di=0;
                        while(di<N && state==PackState.COMPLEXES
                        		&& (line=StringUtil.ourNextLine(fp))!=null) {
                        	try {
                    			StringTokenizer loctok = new StringTokenizer(line);
                    			x=Double.parseDouble(loctok.nextToken());
                    			y=Double.parseDouble(loctok.nextToken());
                        		utilComplexes.add(new Complex(x,y));
                        	} catch(Exception ex) {
                        		di=N; // bomb out, leave what has been found
                        		state=PackState.INITIAL;
                        	}
                        } // end of while
                        
                  		if (utilComplexes.size()==0) utilComplexes=null;
                  		else flags |= 04000000;
                		state=PackState.INITIAL;
                    }
                    else if(mainTok.equals("RADII_INTERACTIONS:") 
                    		&& hes==0 && !newPacking) {
                        state = PackState.RADII_INTERACTIONS;
                        
                        /* This read actually changes eucl radii: entry "i j f" means to
                         multiply rad(j) by (rad(i)/rad(j))^f. */
                        double []newrad=new double[nodeCount+1];
                        int count=0;
                        for (int i=1;i<=nodeCount;i++) {
                        	int ij=rON(i);
                        	newrad[ij]=getRadius(ij);
                        }
                        while(state==PackState.RADII_INTERACTIONS 
                        		&& (line=StringUtil.ourNextLine(fp))!=null) {
                        	StringTokenizer loctok = new StringTokenizer(line);
                        	try {
                        		String str=(String)loctok.nextToken();
                        		int v=rON(Integer.parseInt(str));
                        		int w=rON(Integer.parseInt((String)loctok.nextToken()));
                        		double fac=Double.parseDouble((String)loctok.nextToken());
                        		newrad[v] *= Math.pow(getRadius(w)/getRadius(v),fac);
                        		count++;
                        	} catch(Exception ex) {state=PackState.INITIAL;}
                        }
                        if (count>0)
                        	for (int i=1;i<=nodeCount;i++) {
                        		int ij=rON(i);
                        		setRadius(ij,newrad[ij]);
                        	}
                        flags |= 0400000; // 040000;
                    }
                    
                    else if (mainTok.equals("BARY_DATA:") && !newPacking)
                    {
                        state = PackState.BARY_DATA;
                        
                        try {
                        // store results in 'utilBary'
                        utilBary=new Vector<BaryPtData>(0);
                        while(state==PackState.BARY_DATA 
                        		&& (line=StringUtil.ourNextLine(fp))!=null) {
                        	StringTokenizer loctok = new StringTokenizer(line);
                        	
                        	// data form: 'v0 v1 v2 b0 b1 x [y]
                        	// b0 b1 are barycentric coords (third is computed)
                        	// string after b1 is to be read as a complex (possibly real)
                        	
              				// get three vertices defining the face
                        	loctok=new StringTokenizer(line);
                        	int v0=rON(Integer.parseInt((String)loctok.nextToken()));
                        	int v1=rON(Integer.parseInt((String)loctok.nextToken()));
                        	int v2=rON(Integer.parseInt((String)loctok.nextToken()));
                        	// get 2 doubles
                        	double b0=Double.parseDouble((String)loctok.nextToken());
                        	double b1=Double.parseDouble((String)loctok.nextToken());

                        	// is this a face of 'this'? If so, may correct the order
                        	int face=what_face(v0,v1,v2);
                        	if (face!=0) {
                        	
                        		// check that order is right
                        		int j0=faces[face].vert[0];
                        		if (j0==v1) {
                        			int hold=v0;
                        			v0=v1;
                        			v1=v2;
                        			v2=hold;
                        			b0=b1;
                        			b1=1.0-(b0+b1);
                        		}
                        		else if (j0==v2) {
                        			int hold=v0;
                        			v0=v2;
                        			v2=v1;
                        			v1=hold;
                        			b0=1.0-(b0+b1);
                        			b1=b0;
                        		}
                        	}
                            
                        	// create the point
                            BaryPtData bptd=new BaryPtData(v0,v1,v2,b0,b1);
                            if (face!=0)
                            	bptd.utilint=face;
                            
                            utilBary.add(bptd);
                        } // end of while
                        } catch(Exception ex) {
                        	break; // got no data, so stop this loop
                        }
                        state=PackState.INITIAL;
                    } // done with 'BARY_VECTOR'
                    // TODO: fix for dcel structures
                    else if (mainTok.equals("BARY_VECTOR:") && !newPacking)
                    {
                        state = PackState.BARY_VECTOR;
                        int count=0;
                        
                        // toss old vector of barylinks
                        CPBase.gridLines=new Vector<BaryCoordLink>(1);
                        while(state==PackState.BARY_VECTOR 
                        		&& (line=StringUtil.ourNextLine(fp))!=null) {
                        	StringTokenizer loctok = new StringTokenizer(line);
                        	
                        	// search for 'BARYLIST's
                        	String str=(String)loctok.nextToken();
                        	if (str.startsWith("BARYLIST:")) {
                        		
                        		// creating a new linked list
                        		BaryCoordLink bcl=new BaryCoordLink(this);
                        		int n=0;
                        		while (state==PackState.BARY_VECTOR &&
                        				(line=StringUtil.ourNextLine(fp))!=null) {
                        			try {
                        				
                        				// data form: 'v0 v1 v2 start0 start1 end0 end1'
                        				// start0/1 and end0/1 are barycentric coods (third is computed)
                        				
                        				// get three vertices defining the face
                        				loctok=new StringTokenizer(line);
                        				int v0=rON(Integer.parseInt((String)loctok.nextToken()));
                        				int v1=rON(Integer.parseInt((String)loctok.nextToken()));
                        				int v2=rON(Integer.parseInt((String)loctok.nextToken()));
                        				int face=what_face(v0,v1,v2);
                        				if (face==0) {
                        					state=PackState.INITIAL;
                        					break;
                        				}
                        				// get 4 doubles
                        				double s0=Double.parseDouble((String)loctok.nextToken());
                        				double s1=Double.parseDouble((String)loctok.nextToken());
                        				double e0=Double.parseDouble((String)loctok.nextToken());
                        				double e1=Double.parseDouble((String)loctok.nextToken());
                        				
                        				// check that order is right
                        				int j0=faces[face].vert[0];
                        				if (j0==v1) {
                        					int hold=v0;
                        					v0=v1;
                        					v1=v2;
                        					v2=hold;
                        					s0=s1;
                        					s1=1.0-(s0+s1);
                        					e0=e1;
                        					e1=1.0-(e0+e1);
                        				}
                        				else if (j0==v2) {
                        					int hold=v0;
                        					v0=v2;
                        					v2=v1;
                        					v1=hold;
                        					s0=1.0-(s0+s1);
                        					s1=s0;
                        					e0=1.0-(e0+e1);
                        					e1=e0;
                        				}
                        				BaryPacket nbp=new BaryPacket(this,face);
                        				nbp.start=new BaryPoint(s0,s1);
                        				nbp.end=new BaryPoint(e0,e1);
                        				bcl.add(nbp);
                        				n++;
                        			} catch(Exception ex) {
                        				break; // got no data, so stop this loop
                        			} 
                        		} // end of while for 'BARYLIST'
                        	
                        		// if we got something, add to vector 
                        		if (n>0) {
                        			CPBase.gridLines.add(bcl);
                        			count++;
                        		}
                        	}
                        	else if (str.startsWith("END")) {
                        		state=PackState.INITIAL;
                        	}
                        	
                        	if (count>0)
                        		flags |= 010000000; // show we got something
                        
                        } // end of while for 'BARY_VECTOR's
                   
                        state=PackState.INITIAL;
                    } // done with 'BARY_VECTOR'
                    
                    else if(mainTok.equals("END")) {
                    	foundEnd=true;
                    	break;
                    }
        		}
        		if (foundEnd)
        			break;
        	}
        } catch(Exception ex){
        	flashError("Read of "+filename+" has failed: "+ex.getMessage());
        	return -1;
        }
        
        // if vertex marks were specified
    	if (vertMarks!=null && vertMarks.size()>0) {
    		Iterator<EdgeSimple> vm=vertMarks.iterator();
    		while (vm.hasNext()) {
    			EdgeSimple edge=vm.next();
    			if (edge.v>0 && edge.v<=this.nodeCount)
    				setVertMark(rON(edge.v),rON(edge.w));
    		}
    	}

        // this was a CHECKCOUNT file, return
        if (!newPacking) {
        	if (getDispOptions!=null) {
                cpScreen.dispOptions.usetext=true;
                cpScreen.dispOptions.tailored=getDispOptions;
                if (packNum==CirclePack.cpb.getActivePackNum()) {
                	PackControl.screenCtrlFrame.displayPanel.flagField.setText(
                			cpScreen.dispOptions.tailored);
                	PackControl.screenCtrlFrame.displayPanel.setFlagBox(true);
                }
        	}
        	return flags;
        }
        
        // ============== update for new packings ============
        // Note: complex_count was done during reading.
        
        if ((flags & 0010)!=0010) { // need radii
        	double rad=0.025;
        	if (hes<0) rad=1.0-Math.exp(-1.0);
        	for (int i=1;i<=nodeCount;i++)
        		setRadius(rON(i),rad);
        }
        
        if ((flags & 0020)!=0020) { // set centers
        	for (int i=1;i<=nodeCount;i++)
        		setCenter(rON(i),new Complex(0.0,0.0));
        }
        
        if (newAlpha!=0)
        	chooseAlpha();
        if (packDCEL!=null)
        	activeNode = packDCEL.alpha.origin.vertIndx;
        else 
        	activeNode=alpha;
        if (newGamma!=0)
        	chooseGamma();
        if (packDCEL==null)
        	facedraworder(false);
        if ((flags & 0010)!= 0 && (flags & 0020)==0) { // new radii, no centers
        	try {
        		comp_pack_centers(false,false,2,OKERR);
        	} catch(Exception ex) {}
        }
        if ((flags & 0040)==0) { // no angle sums were read
        	fillcurves();
        }

        if ((flags & 00004)!=00004) { // set default aims
        	set_aim_default();
        }

        if ((flags & 02000)==0){ // outdated xyz data 
            xyzpoint=null;
        }

        if (!col_c_flag) {
        	for (int i=1;i<=nodeCount;i++)
        		setCircleColor(rON(i),ColorUtil.getFGColor());
        }

        if (!col_f_flag) {
        	for (int i=1;i<=faceCount;i++)
        		setFaceColor(i,ColorUtil.getFGColor());
        }
                
        // TODO: set pack name and put it on label?
        
        if (cpScreen!=null) {
        	cpScreen.reset();
        	if (getDispOptions!=null) {
        		cpScreen.dispOptions.usetext=true;
        		cpScreen.dispOptions.tailored=getDispOptions;
        		if (packNum==CirclePack.cpb.getActivePackNum()) {
        			PackControl.screenCtrlFrame.displayPanel.flagField.setText(
        					cpScreen.dispOptions.tailored);
        			PackControl.screenCtrlFrame.displayPanel.setFlagBox(true);
        		}
        	}
        }
        setGeometry(hes);
    	set_plotFlags();
        if (packDCEL==null) {
        	CirclePack.cpb.msg("Note: traditional packing in p"+this.packNum+
        			". Call 'DCEL dcel' to attach DCEL structure.");
        }
        return flags; // this.getFlower(1132);
    }
    
    /**
     * translation needed when dcel reading, "BOUQUET". 
     * @param old_v int
     * @return int
     */
    public int rON(int old_v) {
    	if (readOldNew!=null && readOldNew[old_v]>0) {
    		return readOldNew[old_v];    		
    	}
    	return old_v;
    }
    
    /**
     * Read a file in 'PackLite' form and convert it to a packing. 
     * Set the VertexMap (new,old) to convert new indices
     * to indices of some parent based on 'origIndices'. Careful, 
     * watch for negative vertex indices, indicating a vertex which was added 
     * as an ideal vertex and is not represented in the parent.
     * @param fp BufferedReader, instantiated by the calling routine
     * @param filename String
     * @return PackData, null on error
     */
    public static PackData readLite(BufferedReader fp,String filename) 
    throws IOException {
    	
    	PackLite pL=new PackLite(null);  // instantiate empty PackLite
    	boolean yesRadii=false;
    	boolean yesCenters=false;

    	Scanner src=new Scanner(fp);
    	int tick=1;
    	pL.counts=new int[21];
    	
    	try {
    		tick=1;

    		// dispose of the "magic" number identifying this as 'Lite'
    		if (src.hasNextInt()) {
    			int magic=src.nextInt();
    			if (magic != 1234321)  // not the magic number? read as first entry
    				pL.counts[tick++]=magic;
    		}
    		
    		// read 20 lead integers
    		while (tick<=20 && src.hasNextInt()) {
    			pL.counts[tick++]=src.nextInt();
    		}
    		if (tick<21) {
    			src.close();
    			throw new InOutException("Only got "+tick+" lead integers");
    		}
    		
    		// store the lead information
    		pL.checkCount=pL.counts[1];
    		pL.hes=pL.counts[2];
    		pL.vertCount=pL.counts[3];
    		pL.intVertCount=pL.counts[4];
    		pL.vCount=pL.counts[5];
    		pL.flowerCount=pL.counts[6]; // may be 0
    		pL.aimCount=pL.counts[7]; // may be 0
    		pL.invDistCount=pL.counts[8]; // may be 0
    		if (pL.counts[9]>0)
    			yesRadii=true;
    		if (pL.counts[10]>0)
    			yesCenters=true;
    		
    		// **** varIndices
    		pL.varIndices=new int[pL.vCount];
    		tick=0;
    		for (int n=0;n<pL.vCount;n++) {
    			if (src.hasNextInt())
    				pL.varIndices[tick++]=src.nextInt();
    		}
    		
    		// **** flowers
    		if (pL.flowerCount>0) {
    			pL.flowerHeads=new int[pL.flowerCount];
    			tick=0;
    			for (int n=0;n<pL.flowerCount;n++) {
    				if (src.hasNextInt())
    					pL.flowerHeads[tick++]=src.nextInt();
    			}
    		}
    		 
    		// **** read original indices, create back list
    		pL.v2parent=new int[pL.vertCount+1];
    		pL.parent2v=new int[pL.checkCount+1];
    		tick=1;
    		for (int n=1;n<=pL.vertCount;n++) {
    			if (src.hasNextInt()) {
    				int ni=src.nextInt();
    				pL.v2parent[tick]=ni;
    				if (ni<=pL.checkCount)
    					pL.parent2v[ni]=tick;
    				tick++;
    			}
    		}
    		
    		// **** radii 
    		if (yesRadii) {
    			tick=1;
    			pL.radii=new double[pL.vertCount+1];
        		for (int n=1;n<=pL.vertCount;n++) {
        			if (src.hasNextDouble())
        				pL.radii[tick++]=src.nextDouble();
        		}
    		}
    		
    		// **** Centers
    		if (yesCenters) {
    			tick=1;
    			pL.centers=new Complex[pL.vertCount+1];
        		for (int n=1;n<=pL.vertCount;n++) {
        			double x=0.0;
        			double y=0.0;
        			if (src.hasNextDouble())
        				x=src.nextDouble();
        			if (src.hasNextDouble())
        				y=src.nextDouble();
        			pL.centers[tick++]=new Complex(x,y);
        		}
    		}
    		
    		// **** aim indices
    		if (pL.aimCount>0) {
    			pL.aimIndices=new int[pL.aimCount];
    			tick=0;
    			for (int n=0;n<pL.aimCount;n++)
    				if (src.hasNextInt()) 
    					pL.aimIndices[tick++]=src.nextInt();
    		}
    		
    		// **** aims
    		if (pL.aimCount>0) {
    			pL.aims=new double[pL.aimCount];
    			tick=0;
    			for (int n=0;n<pL.aimCount;n++)
    				if (src.hasNextDouble()) 
    					pL.aims[tick++]=src.nextDouble();
    		}
    		
    		// **** inv distances
    		if (pL.invDistCount>0) {
    			pL.invDistLink=new EdgeLink();
    			tick=0;
    			for (int n=0;n<pL.invDistCount;n++) {
    				int v=0;
    				int w=0;
    				if (src.hasNextInt()) {
    					v=src.nextInt();
    					if (src.hasNextInt())
    						w=src.nextInt();
    					tick++;
    				}
    				pL.invDistLink.add(new EdgeSimple(v,w));
    			}
    		}
    		
    		// **** invDistances
    		if (pL.invDistCount>0) {
    			pL.invDistances=new double[pL.invDistCount];
    			tick=0;
    			for (int n=0;n<pL.invDistCount;n++) 
    				if (src.hasNextDouble()) 
    					pL.invDistances[tick++]=src.nextDouble();
    		}

    	} catch (InOutException iox) { // exceptions I throw
    		src.close();
    		throw new IOException(iox.getMessage());
    	} 
    	
    	src.close();
    	
    	// Note: convertTo may reestablish original indices
    	return pL.convertTo();
    	
    }
    
    /**
     * Enlarge (or reduce) pack data space, increments of 1000.
     * Free old space, allocate space for KData, RData, VData
     * (and possibly expand 'packDCEL.vertices')
     * Size is stored in 'sizeLimit'.
     * @param new_size int (often currend 'sizeLimit')
     * @param keepit boolean: true, adjust size of current pack,
     *   else a new pack.
     * @return 1
     */
    public int alloc_pack_space(int new_size,boolean keepit) {
        int size=((int)((new_size-1)/1000))*1000+1000;
        if (keepit && size==sizeLimit) { // almost no action needed 
        	if (packDCEL!=null && packDCEL.vertices.length<sizeLimit+1) {
        		Vertex[] new_vs=new Vertex[sizeLimit+1];
        		for (int j=1;j<=packDCEL.vertCount;j++) 
        			new_vs[j]=packDCEL.vertices[j];
        		packDCEL.vertices=new_vs;
        	}
            return 1; 
        }
        sizeLimit=size; // to keep track of space already allocated
        KData []newK = new KData[sizeLimit+1];
        RData []newR = new RData[sizeLimit+1];
        VData []newV = new VData[sizeLimit+1];
        
        if (keepit) { // transfer the old data, allocate expansion space
            for (int v=1;v<=nodeCount;v++) {
                newK[v]=kData[v];
                newR[v]=rData[v];
                if (vData!=null)
                	newV[v]=vData[v];
            }
            for (int v=nodeCount+1;v<sizeLimit+1;v++) {
                newK[v] = new KData();
                newR[v] = new RData();
                if (vData!=null)
                	newV[v]=new VData();
            }
            if (packDCEL!=null) {
            	Vertex[] new_vertices=new Vertex[sizeLimit+1];
            	for (int v=1;v<=packDCEL.vertCount;v++)
            		new_vertices[v]=packDCEL.vertices[v];
            }
        }
        else{ 
            // empty out pack and reset 
            for(int v = 0; v < sizeLimit+1; v++) {
                newK[v] = new KData();
                newR[v] = new RData();
                if (vData!=null)
                	newV[v]=new VData();
            }
            faces=null;
            fUtil=null;
            xyzpoint=null;
            overlapStatus=false;
            status=false;
            nodeCount=0;
            firstRedFace=0;
            status=false;
            locks=0;
            fileName = "";
            tileData=null;
            if (packDCEL!=null)
            	packDCEL.vertices=new Vertex[sizeLimit+1];
        }

        kData=newK;
        rData=newR;
        if (vData!=null)
        	vData=newV;
        
        return 1;
    } 
    
    /**
     * Allocate new space for 5000 vertices
     * @return 1 on success
     */
    public int alloc_pack_space() {
    	return alloc_pack_space(5000,false);
    }

    /* Drawing flag scheme: flags indicate drawing instructions
       for objects -- circles/lines/faces. Bits set as follows:
       1  -  draw object?
       2  -  fill? (4 and 16 imply 2, also)
       4  -  off = foreground, on = background 
       (applies to interior, not border, overriden by bit 16)
       8  -  border color? (default=foreground, else recorded color)
       16 -  interior color? (default set by bit 4, on -- recorded color)
       32 -  display label?
       
       Eg.  flag=3: filled object, in foreground
       flag=9: open object, border in (recorded) color
       (for 'edge', this gives colored edge)
       flag=19: filled in color, border in foreground
       flag=27: filled, border and interior in color
       flag=15: filled with background, border in color
       flag=32: label only (face or circle)
       
       Normally, flag for each type of object; often passed on to
       subroutine, so may need color code with it:
       Eg. (cflag, ccol, ecol) for circle flag, int color, border color.
    */
    
    /** 
     * Allocate space for circle overlaps/inversive distances. 
     * With new DCEL structure, space is already allocated in
     * 'HalfEdge's, so this call is OBE. 
     * Otherwise, former values are lost, set to default 1.0 (tangency).
    */
    public int alloc_overlaps() {
    	if (packDCEL!=null)
    		return 1;
        overlapStatus=true;
        for (int v=1;v<=nodeCount;v++) {
        	kData[v].invDist=new double[countFaces(v)+1];
        }
        for (int v=1;v<=nodeCount;v++) {
        	for (int i=0;i<=countFaces(v);i++)
        		set_single_invDist(v,kData[v].flower[i],1.0);
        }
        return 1;
    }

    /** 
     * Choose 'alpha' vertex or 'alpha' halfedge; 
     * acts as root vertex. Should be interior if 
     * possible. Keep current value if it is legal.
     * Drawing order recomputed if needed. 
     */
    public void chooseAlpha(){
    	if (packDCEL!=null) {
    		packDCEL.setAlpha(0,null);
    		return;
    	}
    	
        // is the current alpha okay?
        if (alpha>0 && alpha<= nodeCount 
        		&& kData[alpha].flower[0]==kData[alpha].flower[countFaces(alpha)]) {
            if (alpha==gamma){
                gamma=kData[alpha].flower[0];
            }
            return;
        }

        int flag=0;
        int i=0;
        do{
            i++;
            if (kData[i].flower[0]==kData[i].flower[countFaces(i)]){
                flag=1;
            }
        } while (i<nodeCount && flag==0);
        if (flag != 0) 
        	alpha=i;
        else 
        	alpha=1;
        if (gamma==alpha) {
            gamma=kData[alpha].flower[0];
        }
        return;
    } 
    
    /**
     * Choose packing's 'gamma' vertex or 'gamma' halfedge, 
     * normally placed on positive y-axis. Must be distinct 
     * from 'alpha'. Keep current value, if legal.
     */
    public void chooseGamma() { // avoid alpha
    	if (packDCEL!=null) {
    		packDCEL.setGamma(0);
    		return;
    	}
    	
    	// traditional packing
        int i=gamma;
        if (i>0 && i<= nodeCount && i!= alpha){
            return; // this choice is okay 
        }
        if (alpha==1) gamma=kData[1].flower[0];
        else gamma=1;
        return;
    } 
    
    /**
     * Only used to avoid 'setAlpha' calls which loop
     * between 'PackData' and 'PackDCEL'. Sometimes used
     * if no 'PackDCEL' is involved.
     * @param v int
     */
    public void directAlpha(int v) {
    	alpha=v;
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
		int alp=getAlpha(); // backup
    	if (packDCEL!=null) {
    		return packDCEL.setAlpha(v,null);
        }
    	if (v<=0 || v>nodeCount) {
    		if (alp>0 && alp<=nodeCount)
    			this.alpha=alp;
    		else this.alpha=1; // default to 1
    		return 1;
    	}
    	
        alpha=v;
        if (v==gamma) gamma=getFirstPetal(v);
        facedraworder(false);
        return 1;
    } 

    /**
     * Set packing 'gamma' index
     * @param i int, can't be 'alpha'
     * @return 1 on success, 0 on failure
     */
    public int setGamma(int i) {
        if (status && i>0 && i<= nodeCount && i != alpha){
            gamma=i;
            return 1;
        }
        return 0;
    } 
    
    /** Set the 'fileName'; on failure, set to 'NoName'
     * @param s String
     */
	public void setName(String s) {
		if (s!=null && (s=s.trim()).length()>0)
			fileName = s;
		else fileName="NoName";
		if (cpScreen!=null)
			cpScreen.setPackName(); // record in small canvas label
	}

	/**
	 * Typically 'nodeCount', but if 'packDCEL' exists, use 'vertCount'
	 * @return
	 */
	public int getNodeCount() {
		if (packDCEL==null)
			return nodeCount;
		else
			return packDCEL.vertCount;
	}
	
	/** 
	 * Get 'fileName' for this packing
	 * @return new String
	 */
	public String getName() {
		return new String(fileName);
	}

	/** 
	 * I make 'alpha' private for debugging
	 * @return int
	 */
	public int getAlpha() {
		if (packDCEL!=null && packDCEL.alpha!=null) {
			int alp=packDCEL.alpha.origin.vertIndx;
			if (alp>0 && alp<packDCEL.vertCount)
				directAlpha(alp); // update this.alpha 
			return alp;
		}
		return this.alpha;
	}
	
	/**
	 * Sets the text string for this packing's vertex/edge/face list (n=0/1/2). 
	 * @param n int
	 * @param listtext String
	 */
	public void setListText(int n,String listtext) {
		if (n<0 || n>2) return;
		ListText[n]=new String(listtext);
	}

	/**
	 * Gets the text string for this packing's vertex/edge/face list (n=0/1/2).
	 * @param n
	 */
	public String getListText(int n) {
		if (n<0 || n>2) return (new String(""));
		return new String(ListText[n].toString());
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
		temp += "Number of interior components: " + intCompCount + "\n";
		temp += "Geometry: " + intToGeometry(hes) + "\n";
		temp += "Intrinsic geometry: " + intToGeometry(intrinsicGeom) + "\n";
		temp += "Genus: " + genus + "\n";
		temp += "Euler charasteric: " + euler + "\n";
		temp += "Alpha: " + alpha + "\n";
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
	 * 'aim' from 'vData', if available, else from 'rData'
	 * @param v int
	 * @return double
	 */
	public double getAim(int v) {
		try {
			if (packDCEL!=null) 
				return vData[v].aim;
			else
				return rData[v].aim;
		} catch(Exception ex) {
			throw new DataException("error in getting 'aim' for v = "+v);
		}
	}
	
	/** Store 'aim' in 'vData', if available, else in 'rData'
	 * @param v int
	 * @param aim double
	 */
	public void setAim(int v,double aim) {
		try {
			if (packDCEL!=null) { 
				vData[v].aim=aim;
				rData[v].aim=aim; // backup
			}
			else
				rData[v].aim=aim;
		} catch(Exception ex) {
			throw new DataException("error in setting 'aim' for v = "+v);
		}
	}
	
	/**
	 * get number of non-ideal faces at 'v'; should work with raw
	 * structure.
	 * @param v int
	 * @return int (usual meaning of 'num')
	 */
	public int countFaces(int v) {
		if (packDCEL==null)
			return kData[v].num;
		Vertex vert=packDCEL.vertices[v];
		HalfEdge he=vert.halfedge;
		int count=0;
		if (he.face!=null && he.twin.face.faceIndx<0)
			count--;
		do {
			he=he.prev.twin;
			count++;
		} while (he!=vert.halfedge && 
				(he.twin.face==null || he.twin.face.faceIndx>=0));
		return count;
	}
	
	/**
	 * get number of petals at 'v'
	 * @param v int
	 * @return int (same as 'countFaces' for interior 'v')
	 */
	public int countPetals(int v) {
		if (packDCEL!=null)
			return packDCEL.countPetals(v);
		return kData[v].num+kData[v].bdryFlag;
	}
	
	/**
	 * 'aim' from 'vData', if available, else from 'rData'
	 * @param v int
	 * @return double
	 */
	public double getCurv(int v) {
		try {
			if (packDCEL!=null) 
				return vData[v].curv;
			else
				return rData[v].curv;
		} catch(Exception ex) {
			throw new DataException("error in getting 'curv' for v = "+v);
		}
	}
	
	/** Store 'curv' in 'vData', if available, else in 'rData'
	 * @param v int
	 * @param aim double
	 */
	public void setCurv(int v,double curv) {
		try {
			if (packDCEL!=null) { 
				vData[v].curv=curv;
				rData[v].curv=curv; // backup
			}
			else
				rData[v].curv=curv;
		} catch(Exception ex) {
			throw new DataException("error in setting 'curv' for v = "+v);
		}
	}
	
	/**
	 * Return the bdryFlag of vertex v; often used for its
	 * value '1' to be used in 'for' loops.
	 * @param v int
	 * @return int (should be 0 or 1)
	 */
	public int getBdryFlag(int v) {
		if (packDCEL!=null)
			return vData[v].getBdryFlag();
		return kData[v].bdryFlag;
	}
	
	public void setBdryFlag(int v,int flag) {
		if (packDCEL!=null)
			vData[v].setBdryFlag(flag);
		else
			kData[v].bdryFlag=flag;
	}
	
	/**
	 * Is this a boundary vertex? Depends on bdry edges
	 * being identified with 'faceIndx'<0.
	 * @param v int
	 * @return boolean
	 */
	public boolean isBdry(int v) {
		if (packDCEL!=null) {
			HalfEdge he=packDCEL.vertices[v].halfedge;
			if (he.twin.face!=null && he.twin.face.faceIndx<0)
				return true;
			return false;
		}
		if (kData[v].bdryFlag!=0)
			return true;
		return false;
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
		if (packDCEL!=null)
			return packDCEL.vertices[v].halfedge.isNghb(w);
		if (nghb(v,w)>=0)
			return true;
		return false;
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
		if (packDCEL!=null) {
			return CombDCEL.onSameBdryComp(packDCEL,v,w);
		}
		
		// traditional packing
		int nxt=kData[v].flower[0];
		while (nxt!=v) {
			if (nxt==w) 
				return true;
			nxt=kData[nxt].flower[0];
		}
		return false;
	}
	
	/**
	 * Get array of vertices for face 'f'
	 */
	public int[] getFaceVerts(int f) {
		if (packDCEL!=null) {
			return packDCEL.faces[f].getVerts();
		}
		return faces[f].vert;
	}
	
	/**
	 * Get flower petals, but don't close up for interior
	 * @param v int
	 * @return int[]
	 */
	public int[] getPetals(int v) {
		if (packDCEL!=null)
			return packDCEL.vertices[v].getPetals();
		
		// traditional
		int n=kData[v].num+kData[v].bdryFlag;
		int[] petals=new int[n];
		for (int j=0;j<n;j++)
			petals[j]=kData[v].flower[j];
		return petals;
	}
	
	/**
	 * Get the traditional array of nghb'ing vertices;
	 * meaning first repeats at end if 'v' is interior.
	 * @param v int
	 * @return int[]
	 */
	public int[] getFlower(int v) {
		if (packDCEL!=null) { 
			return packDCEL.vertices[v].getFlower(true);
		}
		return kData[v].flower;
	}
	
	/** 
	 * the first cclw petal. If not bdry, this is
	 * rather ambiguous.
	 * @param v int
	 * @return int
	 */
	public int getFirstPetal(int v) {
		if (packDCEL!=null) {
			Vertex vert=packDCEL.vertices[v];
			return vert.halfedge.twin.origin.vertIndx;
		}
		// traditional packing
		return kData[v].flower[0];
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
		if (packDCEL!=null) {
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
		int n=kData[v].faceFlower.length;
		flower=new int[n];
		for (int j=0;j<n;j++)
			flower[j]=kData[v].faceFlower[j];
		return flower;
	}

	/**
	 * Get the center of the incircle for face index 'f'.
	 * @param f int
	 * @return Complex
	 */
	public Complex getFaceCenter(int f) {
		if (packDCEL!=null) {
			return packDCEL.getFaceCenter(packDCEL.faces[f]);
		}
		Complex []pts = corners_face(f, null);
		CircleSimple sc=CommonMath.tri_incircle(pts[0],pts[1],pts[2],hes);
		return sc.center;
	}
	
		
	/**
	 * Reset the geometry for the cpScreen graphic objects;
	 * if 'cpScreen' is null, just return;
	 * @param hes int, 1,0, or -1
	 */
	public void setGeometry(int hes) {
		if (cpScreen!=null) 
			cpScreen.setGeometry(hes);
	}
	
	/**
	 * Get the count of bdry components
	 * @return
	 */
	public int getBdryCompCount() {
		if (packDCEL!=null)
			return packDCEL.idealFaceCount;
		return bdryCompCount;
	}
	
	/**
	 * Return a vertex on the j_th bdry component;
	 * indexing starts at 1.
	 * @param j int
	 * @return int, bdry vert index
	 */
	public int getBdryStart(int j) {
		if (packDCEL!=null)
			return packDCEL.idealFaces[j].edge.origin.vertIndx;
		return bdryStarts[j];
	}
	
	/**
	 * This only sets the traditional count, not the
	 * 'idealFaceCount' in DCEL strutures
	 * @param k
	 */
	public void setBdryCompCount(int k) {
		bdryCompCount=k;
	}
	
	/**
	 * Enter center (x,y) in rData
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
		if (v<1 || v>nodeCount) return;
		if(hes < 0) { // hyperbolic: must be in unit disc
			double abval=z.absSq();
			if (abval>1.0) { // error; scale until it's in the unit disc
				double sqabval=Math.sqrt(abval);
				z.mult(1/sqabval);
			}
		}
		if (hes>0) { // sphere: y=phi should be between 0 and pi
			while (z.y<0.0) z.y += Math.PI;
			while (z.y>Math.PI) z.y = Math.PI; // truncate at Pi
		}
		if (packDCEL!=null)
			packDCEL.setCent4Edge(packDCEL.vertices[v].halfedge,z);
		else
			rData[v].center=new Complex(z);
	}
	
	/**
	 * Return center as a new 'Complex'.
	 * @param v int
	 * @return new Complex
	 */
	public Complex getCenter(int v) {
		Complex z=null; 
		if (packDCEL!=null) {
			z=packDCEL.getVertCenter(packDCEL.vertices[v].halfedge);
		}
		else
			z=new Complex(rData[v].center);
		return z;
	}
	
	/**
	 * Get clone of face color
	 * @param f int
	 * @return new Color
	 */
	public Color getFaceColor(int f) {
		if (packDCEL!=null) {
			return packDCEL.faces[f].getColor();
		}
		return ColorUtil.cloneMe(faces[f].color);
	}
	
	/**
	 * set color to clone of 'color'
	 * @param f int
	 * @param color Color
	 */
	public void setFaceColor(int f,Color color) {
		if (packDCEL!=null) {
			packDCEL.faces[f].setColor(color);
		}
		else
			faces[f].color=ColorUtil.cloneMe(color);
	}
	
	/**
	 * get clone of circle color
	 * @param v int
	 * @return new Color
	 */
	public Color getCircleColor(int v) {
		if (packDCEL!=null) {
			return vData[v].getColor();
		}
		return ColorUtil.cloneMe(kData[v].color);
	}
	
	/**
	 * set circle color to clone of 'color'
	 * @param v int 
	 * @param color Color 
	 */
	public void setCircleColor(int v,Color color) {
		try {
		if (packDCEL!=null) 
				vData[v].setColor(color);
		else
			kData[v].color=ColorUtil.cloneMe(color);
		} catch(Exception ex) {}
	}
	
	public int getVertUtil(int v) {
		if (packDCEL!=null)
			return packDCEL.vertices[v].vutil;
		else
			return kData[v].utilFlag;
	}
	
	public void setVertUtil(int v,int m) {
		if (packDCEL!=null)
			packDCEL.vertices[v].vutil=m;
		else
			kData[v].utilFlag=m;
	}

	public int getVertMark(int v) {
		if (packDCEL!=null)
			return vData[v].mark;
		else 
			return kData[v].mark;
	}
	
	public void setVertMark(int v,int m) {
		if (packDCEL!=null) 
			vData[v].mark=m;
		else 
			kData[v].mark=m;
	}
	
	public int getPlotFlag(int v) {
		if (packDCEL!=null) 
			return vData[v].plotFlag;
		return kData[v].plotFlag;
	}
	
	public void setQualFlag(int v,int m) {
		if (packDCEL!=null) 
			vData[v].qualFlag=m;
		else 
			kData[v].qualFlag=m;
	}
	
	public int getFacePlotFlag(int f) {
		if (packDCEL!=null)
			return packDCEL.faces[f].plotFlag;
		else
			return faces[f].plotFlag;
	}
	
	public void setFacePlotFlag(int f,int m) {
		if (packDCEL!=null)
			packDCEL.faces[f].plotFlag=m;
		else
			faces[f].plotFlag=m;
	}
	
	public int getQualFlag(int v) {
		if (packDCEL!=null) 
			return vData[v].qualFlag;
		return kData[v].qualFlag;
	}
	
	public void setPlotFlag(int v,int m) {
		if (packDCEL!=null) 
			vData[v].plotFlag=m;
		else 
			kData[v].plotFlag=m;
	}
	
	public int getFaceMark(int f) {
		if (packDCEL!=null) 
			return packDCEL.faces[f].mark;
		else 
			return faces[f].mark;
	}
	
	public void setFaceMark(int f,int m) {
		if (packDCEL!=null) 
			packDCEL.faces[f].mark=m;
		else 
			faces[f].mark=m;
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
		double x=rData[v].rad;
		if (packDCEL!=null) {
			x=packDCEL.getVertRadius(packDCEL.vertices[v].halfedge);
		}
		
		// check for hyp case
	    if (hes<0 && x> 0.0) {
	    	if (x>.0001) return ((-0.5)*Math.log(1.0-x));
	    	else return (x*(1.0+x*(0.5+x/3))/2);
	    }
	    return x;
	}
	
	/**
	 * Return the internal radius. This means in the hyp case,
	 * return the x-radius. (See 'getActualRadius' to instead
	 * convert x-radius to actual hyp radius.).
	 * TODO: want to turn "rData[].rad" to private as part of DCEL
	 * conversion, so I've set this up
	 * @param v int
	 * @return double
	 */
	public double getRadius(int v) {
		double x=rData[v].rad;
		if (packDCEL!=null) {
			x=packDCEL.getVertRadius(packDCEL.vertices[v].halfedge);
		}
	    return x;
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
				else 
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
	 * e.g., in 'RedHEdge's.)
	 * @param v int
	 * @param r double
	 */
	public void setRadius(int v,double r) {
		if (hes>0 && r>=Math.PI) 
			r=Math.PI-OKERR;
		
		if (packDCEL!=null) { 
			packDCEL.setVertRadii(v,r);
			return;
		}
		
		// traditional packing
		if (v<1 || v>nodeCount) 
			return;
		rData[v].rad=r;
	}

	/**
	 * Currently, just send error to statusPanel.
	 * TODO: would like to have 'beep' sound
	 * @param errmsg
	 */
	private void flashError(String errmsg) {
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
	
	/**
	 * allocate face data space (knowing facecount)
	 * @return 1 
	 */
	public int alloc_faces_space() {
		faces=new Face[faceCount+1];
		for (int i=1;i<=faceCount;i++)
			faces[i]=new Face(this);
		fUtil=null;
		return 1;
	} 
	
/* =========================== layout routines =================================== */

	/** 
	 * Simplified drawing order creation, but only for simply connected complexes.
	 * Use limited "tailoring". Main motivation is to avoid use of deg 3/4 
	 * vertices in subsequent layout, and to improve speed.
	 * 
	 * option_flag:
	 *   0     default: avoid using 3/4 degree vertices in the layout
	 *   1     no tailoring
	 *   2     avoid vertices with "mark" set to nonzero.
	 *   
	 * Marked vertices are the ones to be avoided: that is, lay out
	 * all others first. When you run out of other faces, place one
	 * of the 'offlist' vertices, then again try to lay out others 
	 * until you run into another wall, etc.
	 * 
	 * We will keep a linked list of vertices whose faces we should
	 * search through for new vertices to place at a given stage, 
	 * adding to the end of the list as we progress; when that's 
	 * used up, we try some that were skipped, creating a new list 
	 * to search. Cycle through this process until all circles are
	 * eventually placed.
	 * On success, we set a whole new "faces" data structure and set
	 * firstFace;	it is the responsibility of the calling routine 
	 * to use the new stuff, throw out the redfaces, empty p.rwb_flags, 
	 * allocate PairLink, manage the new faces, etc. Return 0 on error.
	 * (Note: this routine empties and then uses vertex 'utilFlag'.)
	 * @parm option_flag int
	 * @return int vertcount, 0 on error
	*/
	public int simple_layout(int option_flag) throws CombException {
		int num,vert,face=0,v1,v2,lastface;
		int indx,v,w,way;
		int first_face; // value to be put in 'firstFace' before returning

		if (!isSimplyConnected() || faces==null) 
			throw new CombException("Doesn't appear to be simply connected");

		// any vertices to be avoided? put in 'offlist' 
		boolean []offlist=new boolean[nodeCount+1];
		if (option_flag==0) { /* default: avoid interior verts of degree < 5 or
								 bdry verts without interior nghb. */
			for (int i=1;i<=nodeCount;i++) {
				if ((countFaces(i)<5 && !isBdry(i)) || countFaces(i)==1)
					offlist[i]=true;
			}
		}
		else if (option_flag==2) { // avoid the vertices that are marked 
			for (int i=1;i<=nodeCount;i++) {
				if (getVertMark(i)!=0)
					offlist[i]=true;
			}
		}

		// create "newfaces" space, copy most stuff over. 
		Face []newfaces=new Face[faceCount+1];
		for (int j=1;j<=faceCount;j++) {
			newfaces[j]=faces[j].clone();
			newfaces[j].nextFace=newfaces[j].indexFlag=0;
		}
	    
		// create tmp space for keeping track of actions 
		boolean []vflag=new boolean[nodeCount+1];
		boolean []fflag=new boolean[faceCount+1];
	  
		// initialize linked lists for processing
		NodeLink mainLink=new NodeLink(this);
		NodeLink holdLink=new NodeLink(this);
		for (int i=1;i<=nodeCount;i++) // zero out utilFlag 
			kData[i].utilFlag=0;

		/* start with alpha (regardless of its situation) and two contiguous
	       neighbors (unmarked, if possible). */
		v=kData[alpha].flower[0];
		w=kData[alpha].flower[1];
		int nbr=0;
		if (offlist[v] || offlist[w]) {
			for (int i=1;((i<countFaces(alpha)) && nbr==0);i++) {
				if (!offlist[(v=kData[alpha].flower[i])] 
				             && !offlist[(w=kData[alpha].flower[i+1])])
					nbr=i;
			}
		}
		if (nbr==0) { // default to first face 
			v=kData[alpha].flower[0];
			w=kData[alpha].flower[1];
		}

		vflag[alpha]=vflag[v]=vflag[w]=true;
		for (int i=0;i<(countFaces(alpha)+getBdryFlag(alpha));i++)
			kData[kData[alpha].flower[i]].utilFlag++;
		for (int i=0;i<(countFaces(v)+getBdryFlag(v));i++)
			kData[kData[v].flower[i]].utilFlag++;
		for (int i=0;i<(countFaces(w)+getBdryFlag(w));i++)
			kData[kData[w].flower[i]].utilFlag++;
		int vertcount=3;
		mainLink.add(alpha);
		mainLink.add(v);
		mainLink.add(w);
		first_face=lastface=getFaceFlower(alpha,nbr);
		beta=kData[alpha].flower[nbr]; // <alpha,beta> is base edge
		newfaces[lastface].indexFlag=face_index(lastface,alpha);
		fflag[lastface]=true;
		
		/* set all 'nextFace' to 'lastface', so we always have that
		 * index to fall back on. */
		for (int i=1;i<=faceCount;i++) newfaces[i].nextFace=lastface;


		/* We keep two lists of vertices: 'mainLink' is the one we're
		 going through. As long as mainLink isn't empty and we're
	     getting hits of new vertices, put unhandled neighbors of the
	     hits into 'holdLink'. When through mainLink, we adjoin holdLink 
	     to end, restart holdList, pass through mainLink again. 
	     If we get through mainLink and holdLink is empty, we pass 
	     through mainLink and add the first 'marked' vertex to end,
	     then go through mainLink again (may now be able to use 
	     something earlier). Keeping track in utilFlag of number of nghbs 
	     placed to compare to number of petals, fflag indicates faces placed
	     (though others may have all vertices placed). Remove verts 
	     from mainLink if all nghbs have been placed. */

		boolean hits=true;
		boolean pickup=false;
		boolean keepon=true;
		int safety=1000*nodeCount; // prevent infinite looping
		while (mainLink.size() > 0 && (pickup || hits) && safety>0) {
			safety--;
			hits = false;
			Iterator<Integer> ml = mainLink.iterator();

			/* Go through mainLink, add new verts to newlist as faces suitable
			 * for layout are encountered ; remove from mainLink those we're
			 * finished with (namely, when their petal vertices have all been
			 * laid, as reflected in utilFlag tally).
			 */
			while (ml.hasNext()) {
				vert = (Integer) ml.next();
				
				// already hit all neighbors of this vert? remove it.
				if (kData[vert].utilFlag == (countFaces(vert) + getBdryFlag(vert))) {
					ml.remove();
				} 
				else if (!offlist[vert] || pickup) { // process this vert
					num = countFaces(vert); // num of faces
					int nnum = num + getBdryFlag(vert); // num of nghbs
					// go through the faces of 'vert'
					keepon = true;
					while (keepon && kData[vert].utilFlag != nnum) {
						keepon = false;
						for (int f = 0; (f < num && kData[vert].utilFlag != nnum); f++) {
							try {
								face = getFaceFlower(vert,f);
							} catch (Exception ex) {
								// TODO: should put something to prevent looping on error
								System.err.println("face " + face);
							}
							if (!fflag[face]) { // face not marked as done
								indx = face_index(face, vert);
								v1 = newfaces[face].vert[(indx + 1) % 3];
								v2 = newfaces[face].vert[(indx + 2) % 3];
								// can we place either v1 or v2?
								if (vflag[v2] && vflag[v1]) { // verts already done, this face is done
									fflag[face] = true;
								}
								// else, can we use vert and v1 to layout v2?
								else if (!vflag[v2] && vflag[v1]
										&& (pickup || !offlist[v1])) {
									newfaces[lastface].nextFace = face;
									lastface = face;
									newfaces[face].indexFlag = indx;
									fflag[face] = vflag[v2] = true;
									way = countFaces(v2) + getBdryFlag(v2);
									for (int i = 0; i < way; i++)
										kData[kData[v2].flower[i]].utilFlag++;
									hits = true;
									keepon = true;
									vertcount++;
									if (kData[v2].utilFlag < (countFaces(v2) + getBdryFlag(v2))) {
										holdLink.add(v2);
									}
								}
								// else, should be able to use vert and v2 to layout v1
								else if (!vflag[v1] && vflag[v2]
										&& (pickup || !offlist[v2])) {
									newfaces[lastface].nextFace = face;
									lastface = face;
									newfaces[face].indexFlag = (indx + 2) % 3;
									fflag[face] = vflag[v1] = true;
									way = countFaces(v) + getBdryFlag(v);
									for (int i = 0; i < way; i++)
										kData[kData[v1].flower[i]].utilFlag++;
									hits = true;
									keepon = true;
									vertcount++;
									if (kData[v1].utilFlag < (countFaces(v1) + getBdryFlag(v))) {
										holdLink.add(v1);
									}
								}
								if (pickup && hits) { // kick out of for and while's
									f = num + 1;
									keepon = false;
								}
							} // done with this face
						} // end of for 'f'
					} // end of 'keepon' while
				} // end of else processing of vert
			} // end of iterator 'ml' (for mainLink) while
			if (hits) {
				if (holdLink.size() != 0) { // holdLink added to end of
											// mainLink, reset newlist
					mainLink.addAll(holdLink);
					holdLink = new NodeLink(this);
				}
				pickup = false;
			} else if (!pickup)
				pickup = true; /* pickup true means you have permission to
				 use an 'offlist' vert;*/
		} // end of mainLink while */
		
		if (safety<=0) {
			throw new CombException("passed safety limit in simple_layout");
		}
	    
		// clean up, set results, and leave 
		if (vertcount!=nodeCount)
			CirclePack.cpb.myMsg("simple_layout: only placed "+vertcount+" of the "+nodeCount+
					" vertices.");
		if (vertcount!=0) { // set the PackData 'faces' and 'firstFace'
			firstFace=first_face;
			faces=newfaces;
			return vertcount;
		}
		else return 0;
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
		if (v<1 || v>nodeCount || w<1 || w>nodeCount) 
			return -1;
		int[] flower=getFlower(v);
		for (int j=0;j<=countFaces(v);j++)
			if (flower[j]==w) 
				return j;
		return -1;
	}
	
	/**
	 * Return non-closed list of corners for dual face (incircle centers
	 * of faces). For multiply connected cases, may have ambiguous centers
	 * TODO: unsettled issue, what to do for bdry vertices? Currently add
	 * half edges ending at tangency points ob the two bdry edges from v.
	 * Calling routine can also distinguish by size of returned array, one
	 * more than '.num'. 
	 * @param v int
	 * @param ambigZs AmbiguousZ
	 * @return Complex[]
	 */
	public Complex []corners_dual_face(int v,AmbiguousZ []ambigZs) {
		int num = countFaces(v);
		int[] flower=getFlower(v);
		int []faceflower=getFaceFlower(v);
		Complex []pts=new Complex[num];
		boolean easycase= (ambigZs==null) || (hes>0); // 
		CircleSimple sC=null;
		int offset=-1; // offset for starting petal
		
		if (!easycase) {
			boolean ambig=false;
			for (int j=0;j<(num+getBdryFlag(v));j++) {
				if (ambigZs[flower[j]]!=null)
					ambig=true;
				else if (offset<0)
					offset=j; // catch first non-ambiguous petal
			}
			// no ambiguity v or its petals?
			if (!ambig && ambigZs[v]==null)
				easycase=true;
		}

		if (easycase) {
			if (!isBdry(v)) { // interior case
				for (int j=0;j<num;j++) {
					int f=faceflower[j];
					sC=faceIncircle(f,ambigZs);
					pts[j]=new Complex(sC.center);
				}
				return pts;
			}
			
			// else is bdry case
			pts=new Complex[num+2];
			pts[0]=CommonMath.get_tang_pt(getCenter(v), getCenter(flower[0]),
					getRadius(v),getRadius(flower[0]),hes);
			pts[num+1]=CommonMath.get_tang_pt(getCenter(v), getCenter(flower[num]),
					getRadius(v),getRadius(flower[num]),hes);
			for (int j=0;j<num;j++) {
				int f=faceflower[j];
				sC=faceIncircle(f,ambigZs);
				pts[j+1]=new Complex(sC.center);
			}
			return pts;
		}
		
		// for non-easy cases, keep v in place, find solid petal,
		//   then recompute the rest of the centers (though we don't
		//   record these in rData).
		Complex vcent=getCenter(v);
		double rad = getRadius(v);
		int v2 = -1;
		int v1 = -1;
		Complex pt1 = null;
		Complex pt2 = null;
		double ivd1 = 1.0;
		double ivd2 = 1.0;
		double ivd = 1.0;

		// First: find or create reliable first petal; several situations

		// v is boundary; make the first edge work
		if (isBdry(v)) {
			offset=0;
			v2 = flower[offset];
			pt2 = new Complex(getCenter(v2));
			if (ambigZs[v2] != null) {
				ivd2=getInvDist(v,v2);
				sC=ambigZs[v2].theOne(vcent, rad, ivd2, hes);
				pt2=sC.center;
			} 
		}
		
		else { // interior cases
			// v is non-ambiguous
			if (ambigZs[v] == null) {
				if (offset >= 0) { // use this first non-ambiguous nghb
					v2 = flower[offset];
					ivd2=getInvDist(v,v2);
					pt2 = new Complex(getCenter(v2));
				} 
				else { // no non-ambiguous petal, so position first petal
					offset = 0;
					v2 = flower[offset];
					ivd2=getInvDist(v,v2);
					pt2 = ambigZs[v2].theOne(vcent, rad, ivd2, hes).center;
				}
			}
			else { // v int/ambiguous; don't move v; 
				   // note: there should exist some nghb in place.
				offset = -1;
				for (int j = 0; j < num && offset < 0; j++) {
					v2 = flower[j];
					ivd2=getInvDist(v,v2);
					pt2 = getCenter(v2);
					double thislength=-1.0;
					double actuallength=-1.0;
					if (hes<0) {
						thislength=HyperbolicMath.h_ivd_length(rad, getRadius(v2), ivd2);
						actuallength=HyperbolicMath.h_dist(vcent,pt2);
					}
					else {
						thislength= EuclMath.e_ivd_length(rad,getRadius(v2),ivd2);
						actuallength=vcent.minus(pt2).abs();
					}
					if (Math.abs((thislength - actuallength) / rad) < .001)
						offset = j;
				}
				if (offset < 0) {
					throw new DataException("no neighbor in place?");
				}
			}
		}
		
		// position the 'offset' center, then recompute the rest
		for (int j=1;j<=(num+getBdryFlag(v));j++) {
			v1 = v2;
			v2 = flower[(j + offset) % num];
			ivd1 = ivd2;
			ivd2=getInvDist(v,v2);
			pt1 = pt2;
			ivd=getInvDist(v1,v2);
			if (hes<0) // hyp
				sC = HyperbolicMath.h_compcenter(vcent, pt1, rad, getRadius(v1),
						getRadius(v2), ivd, ivd2, ivd1);
			else 
				sC=EuclMath.e_compcenter(vcent, pt1, rad, getRadius(v1),
						getRadius(v2), ivd, ivd2, ivd1);
			pt2=sC.center;
			if (hes<0) 
				sC=HyperbolicMath.hyp_tang_incircle(vcent,pt1,pt2,rad,
						getRadius(v1),getRadius(v2));
			else
				sC=EuclMath.eucl_tri_incircle(vcent,pt1,pt2);
			pts[j-1]=sC.center;
		}
		
		return pts;
	}
	
	/**
	 * Return non-closed list of corners for face f, in correct order. 
	 * May be complicated in non-simply connected situations when
	 * 'ambigZs' is non-trivial; then we have to search for the best
	 * three centers. 
	 * @param f int
	 * @return Complex[]
	 */
	public Complex []corners_face(int f,AmbiguousZ []ambigZs) {
		int []vert=getFaceVerts(f);
		int baseindx=-1;
		Complex []pts=new Complex[3];
		boolean easycase= (ambigZs==null) || (hes>0);

		// if not simply connected and eucl/hyp, assume usual layout 
		//    with red chain is in place.
		if (!easycase) {
			if (ambigZs==null)
				throw new CombException("problems in 'face_corners' creating 'ambigZs'");
			boolean ambig = false;
			for (int j=0;j<3;j++) {
				if (ambigZs[vert[j]] != null)
					ambig = true;
				else if (baseindx < 0)
					baseindx = j; // catch first non-ambiguous corner
			}
			
			// none of these is ambiguous?
			if (!ambig) 
				easycase=true;
		}
		
		// in generic easy case, just list vert centers
		if (easycase) {
			for (int j=0;j<3;j++) {
				pts[j]=getCenter(vert[j]);
			}
			return pts;
		}
		
		// for non-easy cases, various situations:
		double ovlp01=getInvDist(vert[0],vert[1]);
		double ovlp12=getInvDist(vert[1],vert[2]);
		double ovlp20=getInvDist(vert[2],vert[0]);

		// Cases when two are non-ambiguous, can compute the third
		// base 0 and next non-ambiguous
		if (baseindx==0 && ambigZs[vert[1]]==null) {
			pts[0]=getCenter(vert[0]);
			pts[1]=getCenter(vert[1]);
			CircleSimple sC=new CircleSimple();
			if (hes<0) // hyp
				sC = HyperbolicMath.h_compcenter(pts[0], pts[1],
						getRadius(vert[0]),getRadius(vert[1]),
						getRadius(vert[2]), ovlp12, ovlp20, ovlp01);
			else 
				sC=EuclMath.e_compcenter(pts[0], pts[1],
						getRadius(vert[0]),getRadius(vert[1]),
						getRadius(vert[2]), ovlp12, ovlp20, ovlp01);
			pts[2]=sC.center;
			return pts;
		}
		// base 0 and previous non-ambiguous
		if (baseindx==0 && ambigZs[vert[2]]==null) {
			pts[0]=getCenter(vert[0]);
			pts[2]=getCenter(vert[2]);
			CircleSimple sC=new CircleSimple();
			if (hes<0) // hyp
				sC = HyperbolicMath.h_compcenter(pts[2], pts[0],
						getRadius(vert[2]),getRadius(vert[0]),
						getRadius(vert[1]), ovlp01, ovlp12, ovlp20);
			else 
				sC=EuclMath.e_compcenter(pts[2], pts[0],
						getRadius(vert[2]),getRadius(vert[0]),
						getRadius(vert[1]), ovlp01, ovlp12, ovlp20);
			pts[1]=sC.center;
			return pts;
		}
		// base 1 and next non-ambiguous (base =1 ==> previous must be ambiguous)
		if (baseindx==1 && ambigZs[vert[2]]==null) {
			pts[1]=getCenter(vert[1]);
			pts[2]=getCenter(vert[2]);
			CircleSimple sC=new CircleSimple();
			if (hes<0) // hyp
				sC = HyperbolicMath.h_compcenter(pts[1], pts[2],
					getRadius(vert[1]),getRadius(vert[2]),
					getRadius(vert[0]), ovlp12, ovlp20, ovlp01);
			else 
				sC=EuclMath.e_compcenter(pts[1], pts[2],
						getRadius(vert[1]),getRadius(vert[2]),
						getRadius(vert[0]),ovlp12, ovlp20, ovlp01);
			pts[0]=sC.center;
			return pts;
		}
		
		// There msut be at least 2 that are ambiguous
		boolean firsttest=false; // acceptable error with first edge?
		boolean secondtest=false; // acceptable error with second edge?
		Complex z=new Complex(0.0);
		Complex best1=new Complex(0.0);
		Complex best2=new Complex(0.0);

		// is there precisely one non-ambiguous?
		if (baseindx>=0) {
			int v=vert[baseindx];
			int v1=vert[(baseindx+1)%3];
			int v2=vert[(baseindx+2)%3];
			z=getCenter(v);
			double rad=getRadius(v);
			double ovlp=getInvDist(v,v1);
			CircleSimple sC=ambigZs[v1].theOne(z,rad,ovlp,hes);
			best1=sC.center;
			// recall that sC.rad contains rel error (mindist/truedist)
			if (sC.rad<0.1*rad) 
				firsttest=true;
			ovlp=getInvDist(v,v2);
			sC=ambigZs[v2].theOne(z,rad,ovlp,hes);
			best2=sC.center;
			if (sC.rad<0.1*rad) 
				secondtest=true;
			if (firsttest && secondtest) { // TODO: how is the last leg?
				ovlp=getInvDist(v1,v2);
				sC=ambigZs[v2].theOne(best1,getRadius(v1),ovlp,hes);
				if (sC.rad<0.1*rad) {
					pts[baseindx]=z;
					pts[(baseindx+1)%3]=best1;
					pts[(baseindx+2)%3]=best2;
					return pts;
				}
			}
			// else, go with best1 and best2
		}
		
		// all ambiguous (e.g., as with vertices of a blue face in redchain)
		if (baseindx<0) {
			int n=ambigZs[vert[0]].centers.size();
			for (int j=0;j<n;j++) {
				z=ambigZs[vert[0]].centers.get(j);
				double rad=getRadius(vert[0]);
				CircleSimple sC=ambigZs[vert[1]].theOne(z,rad,ovlp01,hes);
				best1=sC.center;
				// recall that sC.rad contains rel error (mindist/truedist)
				if (sC.rad<0.1*rad) 
					firsttest=true;
				sC=ambigZs[vert[2]].theOne(z,rad,ovlp20,hes);
				best2=sC.center;
				if (sC.rad<0.1*rad) 
					secondtest=true;
				if (firsttest && secondtest) { // but what about the last leg?
					sC=ambigZs[vert[2]].theOne(best1,getRadius(vert[1]),ovlp12,hes);
					if (sC.rad<0.1*rad) {
						pts[0]=z;
						pts[1]=best1;
						pts[2]=best2;
						return pts;
					}
				}
			}
		}
		
		// shouldn't leave without an answer
		pts[0]=z;
		pts[1]=best1;
		pts[2]=best2;
		return pts;
	}		
	
	/**
	 * Return non-closed list of corners for "paver" of v, i.e.,
	 * polygonal region given by union of v's faces (including v if v is bdry). 
	 * 
	 * Non-simply connected case is the most complicated (we assume eucl/hyp). 
	 * @param v int
	 * @return Complex[], non-close array 
	 */
	public Complex []corners_paver(int v,AmbiguousZ []ambigZs) {
		int num=countFaces(v);
		int[] flower=getFlower(v);
		int offset=-1;
		Complex []pts=new Complex[num+2*getBdryFlag(v)];
		boolean easycase= (ambigZs==null) || (hes>0);

		// if not simply connected and eucl/hyp, assume usual layout 
		//    with red chain is in place.
		if (!easycase) {
			boolean ambig = false;
			for (int j = 0; j < (num + getBdryFlag(v)); j++) {
				if (ambigZs[flower[j]] != null)
					ambig = true;
				else if (offset < 0)
					offset = j; // catch first non-ambiguous petal
			}
			if (ambigZs[v]==null && !ambig)
				easycase=true;
		}
		
		// in easy cases, just list petal centers as stored, add
		//    center of v if v is bdry.
		if (easycase) {
			for (int j=0;j<(num+getBdryFlag(v));j++) {
				pts[j]=getCenter(flower[j]);
			}
			if (isBdry(v)) 
				pts[num+1]=getCenter(v);
			return pts;
		}
		
		// for non-easy cases, keep v in place, find solid petal,
		//   then recompute the rest of the centers (though we don't
		//   record these in rData).
		Complex vcent=getCenter(v);
		double rad = getRadius(v);
		int v2 = -1;
		int v1 = -1;
		Complex pt1 = null;
		Complex pt2 = null;
		double ovlp1 = 1.0;
		double ovlp2 = 1.0;
		double ovlp = 1.0;

		// First: find or create reliable first petal; several situations

		// v is boundary; make the first edge work
		if (isBdry(v)) {
			offset=0;
			v2 = flower[offset];
			pt2 = getCenter(v2);
			if (ambigZs[v2] != null) {
				ovlp2=getInvDist(v,v2);
				CircleSimple sC=ambigZs[v2].theOne(vcent, rad, ovlp2, hes);
				pt2=sC.center;
			} 
		}
		
		else { // v is interior
			// v is non-ambiguous
			if (ambigZs[v] == null) {
				if (offset >= 0) { // use this first non-ambiguous nghb
					v2 = flower[offset];
					ovlp2=getInvDist(v,v2);
					pt2 = getCenter(v2);
				} 
				else { // no non-ambiguous petal, so position first petal
					offset = 0;
					v2 = flower[offset];
					ovlp2=getInvDist(v,v2);
					pt2 = ambigZs[v2].theOne(vcent, rad, ovlp2, hes).center;
				}
			}
			else { // v int/ambiguous; don't move v; 
				   // note: there should exist some nghb in place.
				offset = -1;
				for (int j = 0; j < num && offset < 0; j++) {
					v2 = flower[j];
					ovlp2=getInvDist(v,v2);
					pt2 = getCenter(v2);
					double thislength=-1.0;
					double actuallength=-1.0;
					if (hes<0) {
						thislength=HyperbolicMath.h_ivd_length(rad, getRadius(v2), ovlp2);
						actuallength=HyperbolicMath.h_dist(vcent,pt2);
					}
					else {
						thislength= EuclMath.e_ivd_length(rad,getRadius(v2),ovlp2);
						actuallength=vcent.minus(pt2).abs();
					}
					if (Math.abs((thislength - actuallength) / rad) < .001)
						offset = j;
				}
				if (offset < 0) {
					throw new DataException("no neighbor in place?");
				}
			}
		}
		
		// position the 'offset' center, then recompute the rest
		pts[0]=getCenter(v2);
		for (int j = 1; j < (num+getBdryFlag(v)); j++) {
			v1 = v2;
			v2 = flower[(j + offset) % num];
			ovlp1 = ovlp2;
			ovlp2=getInvDist(v,v2);
			pt1 = pt2;
			ovlp=getInvDist(v1,v2);
			CircleSimple sC=null;
			if (hes<0) // hyp
				sC = HyperbolicMath.h_compcenter(vcent, pt1, rad,getRadius(v1),
						getRadius(v2), ovlp, ovlp2, ovlp1);
			else 
				sC=EuclMath.e_compcenter(vcent, pt1, rad,getRadius(v1),
						getRadius(v2),ovlp, ovlp2, ovlp1);
			pts[j]=pt2=sC.center;
		}
		if (isBdry(v)) // for bdry, include center of v
			pts[num+1]=getCenter(v);
		
		return pts;
	}
	
	/** 
	 * Find endpoints of given edge; complicated only in non-simply
	 * connected case (eucl or hyp only).
	 * @param edge EdgeSimple
	 * @return Complex[2]
	 */
	public Complex []ends_edge(EdgeSimple edge,AmbiguousZ []ambigZs) {
		if (nghb(edge.v,edge.w)<0)
			return null;
		Complex []pts=new Complex[2];
		boolean easycase= ((ambigZs==null) || (hes>0));
		
		// generic case, use stored centers
		if (easycase) {
			pts[0]=getCenter(edge.v);
			pts[1]=getCenter(edge.w);
			return pts;
		}

		// if not simply connected and eucl/hyp, assume usual layout 
		//    with red chain is in place.
		int goodone=-1;
		int badone=-1;
		boolean oriented=true;
		if (!easycase) {
			if (ambigZs==null)
				throw new CombException("problems creating 'ambigZs' in 'ends_edge' call");
			if (ambigZs[edge.v]==null && ambigZs[edge.w]==null) {
				pts[0]=getCenter(edge.v);
				pts[1]=getCenter(edge.w);
				return pts;
			}
			else if (ambigZs[edge.v]==null) {
				goodone=edge.v;
				badone=edge.w;
			}
			else if (ambigZs[edge.w]==null) {
				goodone=edge.w;
				badone=edge.v;
				oriented=false;
			}
		}
		
		double ovlp=getInvDist(edge.v,edge.w);
		
		if (goodone<0) { // both ambiguous? 
			// try pairings until you meet threshold or return best.
			Complex bestz0=null;
			Complex bestz1=null;
			double besterr=100000.0;

			// note that the current location is first among ambiguous 'centers'
			Iterator<Complex> vit=ambigZs[edge.v].centers.iterator();
			while (vit.hasNext()) {
				double rad=getRadius(edge.v);
				Complex zv=vit.next();
				// get the best fit 
				CircleSimple sC=ambigZs[edge.w].theOne(zv,rad,ovlp,hes); 
				Complex zw=sC.center;
				if (sC.rad<besterr) {
					if (sC.rad<0.05) { // less than 5% relative error?
						pts[0]=zv;
						pts[1]=zw;
						return pts;
					}
					bestz0=zv;
					bestz1=zw;
					besterr=sC.rad;
				}
			}
			if (bestz0==null || bestz1==null) {
				pts[0]=getCenter(edge.v);
				pts[1]=getCenter(edge.w);
			}
			else {
				pts[0]=bestz0;
				pts[1]=bestz1;
			}
			return pts;
		}
		
		// 'goodone' not ambiguous, so find best match 
		pts[0]=getCenter(goodone);
		double rad=getRadius(goodone);
		CircleSimple sC=ambigZs[badone].theOne(pts[0],rad,ovlp,hes); // get the best fit
		pts[1]=sC.center;

		if (!oriented) {
			Complex holdz=new Complex(pts[0]);
			pts[0]=pts[1];
			pts[1]=holdz;
		}
		
		return pts;
	}
	
	/**
	 * Return two ends of dual edge, possibly complicated for 
	 * non-simply connected
	 * @param edge EdgeSimple
	 * @param ambigZs Ambiguous[]
	 * @return Complex[2]
	 */
	public Complex []ends_dual_edge(EdgeSimple edge,AmbiguousZ []ambigZs) {
		int f=edge.v;
		int g=edge.w;
		int indx=-1;
		if (f<1 || g<1 || f>faceCount || g>faceCount || (indx=face_nghb(g,f))<0)
			return null;
		// generic case
		Complex []pts=new Complex[2];
		if (ambigZs==null || hes>0) {
			pts[0]=faceIncircle(f,ambigZs).center;
			pts[1]=faceIncircle(g,ambigZs).center;
			return pts;
		}
		
		// We arbitrarily work with f first
		Complex []zf=corners_face(f,ambigZs);
		
		// get ends v,w for edge between f and g, and third of g
		int v=faces[f].vert[indx];
		int w=faces[f].vert[(indx+1)%3];
		int oddg=faces[g].vert[(face_nghb(f,g)+2)%3];
		
		Complex []zg=new Complex[3];
		zg[0]=zf[(indx+1)%3];
		zg[1]=zf[indx];
		double ovlp2=getInvDist(v,w);
		double ovlp1=getInvDist(w,oddg);
		double ovlp0=getInvDist(v,oddg);
		zg[2]=CommonMath.comp_any_center(zg[0], zg[1],
				getRadius(2),getRadius(v),getRadius(oddg),ovlp0,ovlp1,ovlp2,hes).center;
		pts[0]=CommonMath.tri_incircle(zf[0],zf[1],zf[2],hes).center;
		pts[1]=CommonMath.tri_incircle(zg[0],zg[1],zg[2],hes).center;
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
		try {
			int num=countFaces(v);
			if (isBdry(v)) { // bdry case
				if (j1>=j2)
					return null;
				fan=new int[j2-j1+1];
				for (int i=j1;i<=j2;i++)
					fan[i-j1]=kData[v].flower[i];
				return fan;
			}
			
			// int case
			j1=j1%num;
			j2=j2%num;
			int tick=(j2+num-j1)%num+1;
			if (j1==j2) // want full flower 
				tick=num+1;
			fan=new int[tick];
			for (int i=0;i<tick;i++) 
				fan[i]=kData[v].flower[(j1+i)%num];
			
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
		int m=0;
		while (m<3) { 
			if (faces[f].vert[m]==v) 
				return m;
			m++;
		}
		return -1;
	}
	
	/** 
	 * Return index of face on right side of oriented edge {v,w} 
	 * @param v int
	 * @param w int
	 * @return int index f or -1 if there is no such face 
	 */
	public int face_right_of_edge(int v,int w) {
	  int indx,u,f;

	  if ((indx=nghb(w,v))<0 
	      || (isBdry(v) && w==kData[v].flower[0]))
	    return -1; // {w,v} not an edge or {v,w} is in bdry 
	  u=kData[w].flower[indx+1];
	  if ((f=what_face(w,v,u))==0) return -1;
	  return f;
	}

	/**
	 * Given 'Face' f, vert v, find index of ngbh face g 
	 * opposite to v. 
	 * @param f, Face
	 * @param v, vert index
	 * @return face index or -1 on error
	 */
	public int face_opposite(Face f,int v) {
		for (int j=0;j<3;j++) {
			if (v==f.vert[j])
				return face_right_of_edge(f.vert[(j+1)%3],f.vert[(j+2)%3]);
		}
		return -1;
	}
	
	/** 
	 * Return face index f if {a,b,c} or {a,c,b} is a face
	 * @param a int
	 * @param b int
	 * @param c int
	 * @return int f, else return 0. 
	 */
	public int what_face(int a,int b,int c) {
	  if (!status || a<1 || b<1 || c<1 
	      || a>nodeCount || a>nodeCount || c>nodeCount) return 0;
	  for (int f=1;f<=faceCount;f++)
	    for (int j=0;j<3;j++)
	      if (faces[f].vert[j]==a && ((faces[f].vert[(j+1)%3]==b && faces[f].vert[(j+2)%3]==c)
	    		  || (faces[f].vert[(j+1)%3]==c && faces[f].vert[(j+2)%3]==b)))
		return f;
	  return 0;
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
			int ww=kData[ws].flower[0];
			while (ww!=ws && hit<0) {
				if (ww==w)
					hit=i;
				ww=kData[ww].flower[0];
			}
		}
		return hit;
	}
	
	/** 
	 * Check if faces f2 and f1 share an edge e. Return index 
	 * of begin vertex of e (relative to f1) in 'vert' data 
	 * of face f1 or return -1 if face f1 doesn't share edge 
	 * e with face f2.
	 * @param f2 int
	 * @param f1 int, (NOTE the order of arguments!)
	 * @return -1 if f1 doesn't share edge with f2 or if f1==f2.
	*/
	public int face_nghb(int f2,int f1) {
	  int nj,mj,v1,v2;

	  if (f2<1 || f2 > faceCount || f1<1 || f1 > faceCount || f2==f1) 
	    return -1;
	  for (nj=0;nj<=2;nj++) {
	      v1=faces[f1].vert[nj];
	      v2=faces[f1].vert[(nj+1)%3];
	      for (mj=0;mj<=2;mj++)
	    	  if ( (v1==faces[f2].vert[mj]) && 
	    			  (v2==faces[f2].vert[(mj+2)%3]) )
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
		int m=0;
		if (packDCEL!=null) {
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
		try {
			while (m<3) {
				if (faces[f].vert[m]==v && faces[f].vert[(m+1)%3]==w) return m; 
				m++;
			}
		} catch(Exception ex) {return -1;}
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
		if (packDCEL!=null) {
			HalfEdge vw=packDCEL.findHalfEdge(v,w);
			HalfEdge desig=vw.next;
			if (vw==null || packDCEL.isBdryEdge(desig)) 
				return 0;
			return desig.twin.next.twin.origin.vertIndx;
		}
		
		// traditional adapted from old 'cross_edge_vert'
		int N=countFaces(v);
		int k=nghb(v,w);
		if (k<0 || k>N || (k==N && isBdry(v)))
			return 0;
		int ind_wv=nghb(w,v);
		if (isBdry(w)) {
			if (ind_wv<2) return 0;
		    else return (getFlower(w)[ind_wv-2]);
		}
		int M=countFaces(w);
		return (getFlower(w)[(ind_wv+M-2)%(M)]);
	}
	
	/**
	 * Return face to left of edge v w. On failure, return ans[0]=0.
	 * Also return u, the third vert of face. 
	 * @param edge EdgeSimple
	 * @return int ans[2], with ans[0]=face to left and ans[1]=third vertex.
	*/
	public int[] left_face(EdgeSimple edge) {
		int[] ans=new int[2];
		if (packDCEL!=null) {
			HalfEdge he=packDCEL.findHalfEdge(edge);
			if (he==null || he.face==null)
				return ans;
			ans[0]=he.face.faceIndx;
			ans[1]=he.next.next.origin.vertIndx;
			return ans;
		}
		if (edge==null) {
			ans[0]=0;
			return ans;
		}
		return left_face(edge.v,edge.w);
	}
	
	/**
	 * Return face to left of edge <v w>. On failure, return ans[0]=0.
	 * Also return u, the third vert of face. 
	 * @param v int
	 * @param w int
	 * @return ans[2], with ans[0]=face to left and ans[1]=third vertex.
	*/
	public int []left_face(int v,int w) {
	    int f,u;
	    int []ans=new int[2];

	    for (f=1;f<=faceCount;f++) if (check_face(f,v,w)>=0) break;
	    if (f>faceCount) { // no face
	    	ans[0]=0;
	    	return ans; 
	    }
	    for (int i=0;i<3;i++) if ((u=faces[f].vert[i])!=v && u!=w) {
	    	ans[0]=f;
	    	ans[1]=u;
	    	return ans;
	    }
    	ans[0]=0;
    	return ans; 
	} 

	/**
	 * Return count of bdry verts from v1 to v2 (inclusive) if v1/v2 are
	 * on the same bdry component; otherwise 0.
	 * @param v1 int
	 * @param v2 int
	 * @return int
	 */
	public int verts_share_bdry(int v1,int v2) {
		int count=1;
		if (!status || v1<1 || v1>nodeCount || v2<1 || v2>nodeCount
			|| !isBdry(v1) || !isBdry(v2))
			return 0;
		if (packDCEL!=null) {
			HalfEdge he=packDCEL.vertices[v2].halfedge.twin.next;
			if (v1==v2)
				return he.face.getNum();
			int safety=he.face.getNum()+1;
			do {
				count++;
				he=he.next;
				safety--;
			} while (he.origin.vertIndx!=v1 && safety>0);
			if (safety==0) // not on same bdry segment
				return 0;
			return count;
		}
		
		// traditional packing
		if (v1==v2) { // reset to upstream vert
			v2=kData[v1].flower[countFaces(v1)];
		}
		int vert=v1;
	    int nextvert=kData[vert].flower[0];
	    while (vert!=v2 && isBdry(vert)) {
	      vert=nextvert;
	      nextvert=kData[vert].flower[0];
	      count++;
	      if(vert==v1) return 0; /* didn't find v2 */
	    }
	    if (vert==v2) return count+1;
	    return 0; // must have been error in combinatorics
	} 
	
	/**
	 * Find the tangency point between the circles of given edge.
	 * Actually, interpolate if circles are not quite tangent; should
	 * be a point on the geodesic between the centers. Return is in
	 * (theta,phi) form for spherical case.
	 * @param edge EdgeSimple
	 * @return new Complex, null on error or if vertices are not nghbs
	 */
	public Complex tangencyPoint(EdgeSimple edge) {
		if (nghb(edge.v,edge.w)<0)
			return null;
		Complex z1 = getCenter(edge.v);
		double r1 = getRadius(edge.v);
		Complex z2 = getCenter(edge.w);
		double r2 = getRadius(edge.w);
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
	      if (kData[v].flower[0]==w) 
		return kData[v].flower[3];
	      if (kData[v].flower[3]==w)
		return kData[v].flower[0];
	      return 0;
	    }
	  return kData[v].flower[(nghb(v,w)+3)%6];
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
		
		if (packDCEL!=null) {
			HalfEdge spoke=packDCEL.findHalfEdge(new EdgeSimple(v,w));
			if (spoke==null)
				return 0;
			HalfEdge oppspoke=spoke.origin.oppSpoke(spoke,false);
			return oppspoke.twin.origin.vertIndx;
		}
		
		// traditional
		int[] flower=getFlower(v);
		if (isBdry(v)) {
			int indx=nghb(v,w);
			if (indx<(int)(countFaces(v)/2.0))
				return flower[countFaces(v)];
			else
				return flower[0];
		}
		
		int half=countFaces(v)/2;
		if (2*half!=countFaces(v)) // not even degree
			return 0;
		return flower[(nghb(v,w)+half)% countFaces(v)];
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
		if (packDCEL!=null) {
			HalfEdge he=RawDCEL.getCommonEdge(packDCEL, v, w);
			return HalfEdge.getEdgeSimple(he);
		}
		
		// traditional
		int[] faceflower=kData[v].faceFlower;
		int num=kData[v].num;
		for (int j=0;j<num;j++) {
			int f=faceflower[j];
			Face face=faces[f];
			int g=this.face_opposite(face, v);
			int indx=face_nghb(f,g);
			int[] gverts=getFaceVerts(g);
			if (gverts[(indx+1)%3]==w)
				return new EdgeSimple(gverts[indx],gverts[(indx+2)%3]);
		}
		return null;
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

	  if (!isBdry(v)) return 0;
	  maxcount=nodeCount-intNodeCount;
	  next=v;
	  while ((next=getFirstPetal(next))!=v 
		 && count<= maxcount) count++;
	  if (count>maxcount) return 0;
	  return count;
	}

	/** 
	 * TODO: 'axis-extend' more general. Perhaps that's enough?
     * Check for a "hex-extended" edge from v to w of length no more
	 * than 'lgth'. Return index in flower of 'v' of direction in which
	 * 'w' can be reached in the fewest steps, or -1 on failure. 
	 * See 'hex_extrapolate' to create edgelist.
	 * @param v int
	 * @param v int
	 * @param lgth int
	 * @return int, index or -1 on failure
	*/
	public int hex_extend(int v,int w,int lgth) {
	  int i,dir,next,current,last;

	  if (v==w || lgth<1) return -1;
	  int pets=countFaces(v)+getBdryFlag(v);
	  int []dists=new int[pets];
	  for (dir=0;dir<pets;dir++) {
	      last=v;
	      current=kData[v].flower[dir];
	      i=1;
	      while (current!=w && i<lgth && (next=hex_proj(current,last))!=0) {
	    	  last=current;
	    	  current=next;
	    	  i++;
	      }
	      if (current==w) dists[dir]=i;
	      else dists[dir]=-1;
	  }
	  
	  // look for direction reaching 'w' in smallest number of steps
	  int theDir=-1;
	  int theDist=lgth+1;
	  for (int j=0;j<pets;j++) {
		  if (dists[j]>0 && dists[j]<theDist) {
			  theDir=j;
			  theDist=dists[j];
		  }
	  }
	  return theDir; 
	}

	/** 
	 * Check for an "axis-extended" edge from v to w of length no more
	 * than 'lgth'. Return index in flower of 'v' of direction in which
	 * 'w' can be reached in the fewest steps, or -1 on failure. 
	 * See 'axis_extrapolate' to create edgelist.
	 * @param v int
	 * @param v int
	 * @param lgth int
	 * @return int, index or -1 on failure
	*/
	public int axis_extend(int v,int w,int lgth) {
	  int i,dir,next,current,last;

	  if (v==w || lgth<1) return -1;
//	  int pets=countFaces(v)+getBdryFlag(v);
	  int[] petals=getPetals(v);
	  int []dists=new int[petals.length];
	  for (dir=0;dir<petals.length;dir++) {
	      last=v;
	      current=petals[dir];
	      i=1;
	      while (current!=w && i<lgth && (next=axis_proj(current,last))!=0) {
	    	  last=current;
	    	  current=next;
	    	  i++;
	      }
	      if (current==w) dists[dir]=i;
	      else dists[dir]=-1;
	  }
	  
	  // look for direction reaching 'w' in smallest number of steps
	  int theDir=-1;
	  int theDist=lgth+1;
	  for (int j=0;j<petals.length;j++) {
		  if (dists[j]>0 && dists[j]<theDist) {
			  theDir=j;
			  theDist=dists[j];
		  }
	  }
	  return theDir; 
	}
	
	/**
	 * Return edge for v,w, null if they're not neighbors
	 * @param v int
	 * @param w int
	 * @return EdgeSimple
	 */
	public EdgeSimple getEdge(int v,int w) {
		EdgeSimple edge=new EdgeSimple(v,w);
		if (packDCEL!=null) {
			if (packDCEL.findHalfEdge(edge)!=null) 
				return edge;
			return null;
		}
		
		// traditional packing
		if (nghb(v,w)>=0) 
			return new EdgeSimple(v,w);
		return (EdgeSimple)null;
	}
	
	/**
	 * Redface f is 'blue' if it is responsible for positioning the vertex
	 * NOT shared with prev (and hence also next) face. The next
	 * (counterclockwise) 'vert' of f is the responsibility of some UPSTREAM
	 * face g. Normally g = prev.f. BUT prev.f may have another vert it is
	 * responsible for, so you have to go to prev.prev.f; and so forth.
	 * E.g., if g would (after removing f) be blue itself, then have to go
	 * another step back. This routine moves backward in redchain until it
	 * finds the redface responsible for 'vert'. Return null on failure.
	 * @param redface RedList
	 * @return RedList, null on failure 
	 */
	public RedList back_thru_blue(RedList redface) throws RedListException {
	  if (redface==null || !redface.hasNext() || !redface.hasPrev()
	      || redface.next().face!=redface.prev().face) // not blue
	      return null;
	  int vert=faces[redface.face].vert[(redface.vIndex+1)%3]; // vert of interest
	  RedList trace=redface.prev;
	  while (trace!=null && trace!=redface
		 && faces[trace.face].vert[trace.vIndex]!=vert)
	    trace=trace.prev;
	  if (trace==redface) return null;
	  return trace;
	} 
	
	/** 
	 * v must be a vertex of 'redface' which lies on outside of redchain
	 * of faces. We want to find the maximal sequential fan of faces 
	 * for v in the redchain. We return int[2].
	 * Return null on error.
	 * @param reface RedList
	 * @param v int
	 * @return int[] with int[0]=n1, flower index of first petal
	 *         circle (hence, index of first face in 'faceFlower')
	 *         int[1]=n, number of faces in redchain fan, counterclockwise
	 *         around v.
	*/
	public int []red_fan(RedList redface,int v) throws RedListException {
	  int face,f,cf;
	  RedList rtrace;

	  face=cf=redface.face;
	  rtrace=redface;
	  while ((f=rtrace.next().face)!=face 
		 && v==faces[f].vert[(face_nghb(cf,f)+1)%3]) {
	      rtrace=rtrace.next();
	      cf=rtrace.face;
	    }
	  int n1=nghb(v,faces[cf].vert[(face_index(cf,v)+1)% 3]);
	  int n=1;
	  while (((f=rtrace.prev().face)!=cf) 
		 && v==faces[f].vert[face_nghb(rtrace.face,f)]) {
	      n++;
	      rtrace=rtrace.prev();
	    }
	  int []ans=new int[2];
	  ans[0]=n1;
	  ans[1]=n;
	  return ans;
	}

	/**
	 * Convenient combination call of 'complex_count(false)' and 
	 * 'facedraworder(false)'. One can always call them separately.
	 * @return 0 on problem
	 */
	public int setCombinatorics() throws DCELException {
		int ans=1;
		// TODO: eventually, just return; but for debugging, keep this.
		if (packDCEL!=null) {
			throw new DCELException("DCEL combinatorics are required.");
		}
		if (complex_count(false)==0 || facedraworder(false)==0) 
			ans=0;
		return ans;
	}
	
	/**
	 * Vertex combinatorics are given. Here we create new face indices, identify
	 * and count bdry/interior nodes, bdry/interior components, Euler characteristic,
	 * genus, etc. Store indicators of bdry and int components
	 * (at most MAX_COMPONENTS of each). 
	 * @param defaultColor boolean: true means set default color codes for circles.
	 * @return int 'nodeCount', 0 on error
	*/
	public int complex_count(boolean defaultColor) {
		int m,n,v,w;
		boolean tmp_debug=false;

		if (defaultColor) 
			for (int i=1;i<=nodeCount;i++)
				setCircleColor(i,ColorUtil.getFGColor());
		
		// set 'bdryFlag's, 'bdryCompCount' and 'bdryStarts'
		// combinatorial errors often occur here
		int bcount=0;
		try {
			bcount=setBdryFlags(); // 0=interior, 1=bdry
		} catch (Exception ex) {
			throw new CombException("'complex_count': error in bdry: "+ex.getMessage());
		}

		// identify interior components and pointers to them 
		intStarts=new int[2*MAX_COMPONENTS];
		intNodeCount=nodeCount-bcount; // number of interior vertices
		int icount=0;
		for (int i=1;i<=nodeCount;i++) {
			if (isBdry(i))
				kData[i].utilFlag=-1;
			else {
				kData[i].utilFlag=0;
				icount++;
			}
		}
		int Ii,k;
		int comp_count=0;
		NodeLink nL=null;
		NodeLink lL=null;
		while(icount>0) {
			// find fresh interior
			Ii=0;
			for (int i=1;(i<=nodeCount && Ii==0);i++) 
				if (kData[i].utilFlag==0) Ii=i;
			if (Ii==0) {
				throw new CombException("complex_count: error processing interiors");
			}
			comp_count++;
			icount--;
			intStarts[comp_count]=Ii;
			kData[Ii].utilFlag=comp_count;
			
			// start two lists: follow lL, add to nL (new ones)
			nL=new NodeLink(this,Ii);
			while (nL!=null && nL.size()>0 && icount>0) {
				lL=nL;
				nL=new NodeLink(this); // restart new list
				Iterator<Integer> vlist=lL.iterator();
				while (vlist.hasNext()) {
					v=(int)vlist.next();
					for (int j=0;j<(countFaces(v)+getBdryFlag(v));j++) {
						k=kData[v].flower[j];
						if (kData[k].utilFlag==0) {
							nL.add(k);
							kData[k].utilFlag=comp_count;
							icount--;
						}
					}
				} // end of inner while
			} // end of middle while
		} // end of outer while
		intCompCount=comp_count;
		
		// find Euler characteristic and genus 
		int count=0;
		for (k=1;k<=nodeCount;k++) count += countFaces(k);
		faceCount=count/3;
		int num_edges=(count + bcount)/2;
		euler=nodeCount-num_edges+faceCount;
		genus=(2-euler-getBdryCompCount())/2;
		// check if geom is appropriate 
		if (bdryCompCount==0 && genus==0 && hes<=0) {
			CirclePack.cpb.msg("NOTE: This complex is a topological sphere");
		}
		
		// allocate faces space
		if (alloc_faces_space()==0) {
			CirclePack.cpb.errMsg("Error allocating 'faces' data space.");
			return 0;
		}
		count=1;
		int hit=1;
		while (count<=faceCount && hit<=nodeCount) {
			for (int j=0;j<countFaces(hit);j++) {
				m=kData[hit].flower[j];
				n=kData[hit].flower[j+1];
				if (m>hit && n>hit) {
					faces[count].vert[0]=hit;
					faces[count].vert[1]=m;
					faces[count].vert[2]=n;
					
					// want interior in [0] location: rotate if necessary
					if (isBdry(hit)) {
						if (!isBdry(m)) {
							faces[count].vert[0]=m;
							faces[count].vert[1]=n;
							faces[count].vert[2]=hit;
						}
						else if (!isBdry(n)) {
							faces[count].vert[0]=n;
							faces[count].vert[1]=hit;
							faces[count].vert[2]=m;
						}
					}
					setFaceColor(count,ColorUtil.getFGColor());
					faces[count].plotFlag=1;
					count++;
				}
			}
			hit++;
		}
		
		// Some feedback if there's an error
		if (count < faceCount) {
			CirclePack.cpb.errMsg("Combinatoric error in facecount.");
			count=0;
			for (int i=1;i<=nodeCount && count < 25;i++)
				for (int j=0;j<(countFaces(i)+getBdryFlag(i));j++)
					if (nghb((v=kData[i].flower[j]),i) < 0) {
						if (count ==0 ) {
							detailError("  Here are some vertices which " +
							"disagree about begin neighbors:\n");
						}
						detailError("     "+i+" -- "+v);
						count ++;
					}
			if (count == 25) {
				CirclePack.cpb.errMsg("complex_count: Stop: too many errors.");
				detailError("Stop counting (there may be yet more combinatoric errors).");
			}
			return 0;
		}
		
		// Catalog the faces in 'faceFlower' arrays
		for (int j=1;j<=nodeCount;j++) kData[j].faceFlower=new int[countFaces(j)];
		for (int f=1;f<=faceCount;f++)
			for (int j=0;j<3;j++) {
				v=faces[f].vert[j];
				w=faces[f].vert[(j+1)%3];
				if ((n=nghb(v,w))<0) {
					throw new CombException("nbhb error for v,w = "+v+","+w);
				}
				int vw=kData[v].faceFlower[n];
				if (vw!=0 && vw!=f) {
					boolean debug=false; // debug=true;
					if (debug)
						DebugHelp.debugPackWrite(this,"badflowers.p"); // DebugHelp.debugPackWrite(this,"badflowers.p");
					throw new CombException("error in face numbering");  
				}
				kData[v].faceFlower[n]=f;
			}
			
		// determine intrinsic geometry (but let user set 'hes')
		intrinsicGeom=PackData.getIntrinsicGeom(this);

		if (tmp_debug) { // set during debugging
			  File file = new File(System.getProperty("java.io.tmpdir")+
					  File.separator+"CompCount.dblog");
			  try {
				BufferedWriter dbw = new BufferedWriter(new FileWriter(file));
				dbw.write("Debug file: nodeCount="+nodeCount+", faceCount="+faceCount+"\n");
				for (int jj=1;jj<=faceCount;jj++) {
					dbw.write("Face "+jj+", vert[]={");
					dbw.write(faces[jj].vert[0]+","+faces[jj].vert[1]+","+faces[jj].vert[2]+"}\n");
				}
				dbw.flush();
				dbw.close();
			  } catch(Exception ex) {
				System.err.print(ex.toString());
			}
		}
		
		return nodeCount;
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
	 * This sets 'bdryFlag's, 'bdryCompCount', and 'bdryStarts[]'.
	 * (Formerly done in comlex_count; separated to use during constructions.)
	 * @return int, count of bdry edges
	 */
	public int setBdryFlags() {
		bdryStarts=new int[MAX_COMPONENTS];
		int bcount=0;
		int bs=0;
		for (int i=1;i<=nodeCount;i++) 
			kData[i].utilFlag=0;
		boolean debug=false;
		
		// debugging
		if (debug) { // debug=true;
			for (int i=1;i<=nodeCount;i++) {
				System.err.print("vert "+i+": flower: ");
				for (int j=0;j<=countFaces(i);j++) System.err.print(" "+kData[i].flower[j]);
				System.err.print("\n");
			}
		}
		
		// each time we encounter a new bdry vert, process its whole component
		for (int i=1;i<=nodeCount;i++) {
			
			// debug
			if (debug) System.err.println("index i: "+i);
			
			if (kData[i].utilFlag==0) {
				if (kData[i].flower[0]!=kData[i].flower[countFaces(i)]) {
					bcount++;
					bs++; // bdryStart counter
					if (bs>=MAX_COMPONENTS) {
						CirclePack.cpb.errMsg("More components ("+bs+") than allowed");
						return 0;
					}
					bdryStarts[bs]=i; // note: indexing starts with 1
					setBdryFlag(i,1);
					kData[i].utilFlag=i;
					int j=kData[i].flower[0];
					do {
						// TODO: I've disabled the exception because corner verts can have 
						//       just two neighbors; this may cause other problems
						if (kData[j].utilFlag!=0 || kData[j].flower[0]==kData[j].flower[countFaces(j)])
							CirclePack.cpb.errMsg("Caution: bdry vert "+j+" has only 2 neighbors");
//							throw new CombException("error tracing bdry for "+i);
						setBdryFlag(j,1);
						kData[j].utilFlag=i;
						bcount++;
						j=kData[j].flower[0];
					} while (j!=i && bcount<nodeCount);
					if (bcount>=nodeCount)
						CirclePack.cpb.errMsg("error tracing bdry with "+i);
				}
				else setBdryFlag(i,0);
			}
		}
		setBdryCompCount(bs);
		return bcount;
	}
	
	/**
	 * Just trying out this new drawing order, 5/12: Not working yet.
	 * TODO: switch uses with original 'facedraworder' by changing names.
	 * @param poison boolean, if true, take account of poison vertices
	 * @return int, count of faces laid out or -1 on error
	 */
	public int newfacedraworder(boolean poison) {
		EdgeLink pE=null;
		if (poison) pE=new EdgeLink(this,"Ivw P");
		GraphLink dG=DualGraph.buildDualGraph(this,this.firstFace,pE);
		dG=DualGraph.drawSpanner(this,dG,this.firstFace);
		return DualGraph.tree2Order(this,dG);
	}
	
	/* Specify an order appropriate for drawing faces of a circle packing */

	/* A separate routine (simple_layout, new as of Feb. 2004) is used 
	   for simply connected complexes. It avoids using vertices of 
	   degree < 5 in laying out the remaining circles. If pflag is set 
	   (see below), then skip simple_layout. */

	/* Main difficulty arises with non-simply connected complexes.
	   Key idea is to define a fundamental domain, or "core", which
	   is simply connected. Grow a "red" chain, a closed doubly 
	   linked chain of faces (possibly passing through same face
	   more than once) defining the positively oriented outer bdry 
	   of the core. In the simply connected case, this is just the 
	   chain of faces about the boundary. In non-simply connected 
	   cases, the red chain will pick up cuts where it runs into 
	   itself as it grows (and it will also incorporate any boundary 
	   vertices it encounters). A general routine builds the red 
	   chain and fDo (face drawing order); the fDo is thrown away 
	   after storing that information in redfaces and f_data. */

	/* The red chain starts as the star of faces about alpha, then 
	   continually expands until it runs into itself (or the boundary). 
	   Main difficulty is blue faces; for a blue face, the prev and 
	   next faces in the red chain are the same, requiring extra care. 
	   We try to avoid blue faces, but that is not possible in general. 
	   Other problems can arise when part of the packing is attached 
	   to the core through a narrow neck having just one edge. */

	/* Terminology/strategy: "free" faces are simply those not 
	   processed yet in building the red chain. Eventually,
	   Faces should = "red" or "white". Red = simple closed chain 
	   of bdry faces and ones along each side of "cuts" defining 
	   core; rest are white. A 'blue' face (often temporary condition) 
	   represents a kink in red chain (the faces of the 'prev' and 
	   'next' entries of the chain are the same). */

	/* Finally, end up with linked list for drawing all faces 
	   (nextFace element of f_data) and separate closed linked 
	   list of red faces (next_red element of f_data). If the
	   complex is NOT simply connected, then redFace points one of
	   the doubly linked list of faces forming the final red
	   chain. */

	/* One can influence red chain by use of "poison" vertices or
	   edges, stored in 'poisonVerts' and 'poisonEdges'. 'pflag' set 
	   indicates one or the other, 'poisonEdges' takes precedence if
	   not null. (Poison edges are new, being incorporated starting 11/06. 
	   Effect is 'build_redchain' through 'loop_vert'.) */
	   
	/* Poison vertices are in 'poisonVerts' (set up and maintained by 
	   calling routine). For instance, if there is a desired red chain of 
	   faces, then one can mark the outside edge vertices as poison --- 
	   evolving red chain won't loop over these. For other purposes, we 
	   allow quite arbitrary collections of poison verts, but caution is 
	   in order; any faces outside dividing curve of poison verts may not 
	   end up anywhere in the face drawing order. */

	/* fixup: these routines could be more efficient -- they're too 
	   slow when working with large packings */

	/** 
	 * Find and store order for computing all circle centers. Uses faces
	 * starting with one containing alpha. Every subsequent face f has two
	 * of its circles in place and is responsible for placing a new circle. 
	 * NOTE: T is NOT necessarily a tree in the dual graph; e.g., faces are 
	 * omitted if their circles are all placed using other faces.
	 * For spanning tree, see 'dualSpanner', 'DualGraph' stuff.
	 * 
	 * If pflag is set, then take account of "poison" edges (first 
	 * precedence) or vertices which are stored by the calling routine in 
	 * 'PackData.poisonEdges' and 'PackData.poisonVerts'. This is typically 
	 * how one can influence the form of final red chain, which is otherwise 
	 * determined automatically. 
	 * @param pflag boolean: true, then take account of 'poison' verts.
	 * @return int 1; errors give exceptions 
	*/
	public int facedraworder(boolean pflag) 
	throws RedListException, CombException, LayoutException {
	  int k,f,lastface,w,stop_vert;
	  RedList trace;
	  VertList faceOrder;
	  VertList fDo;
	  boolean debug=false;
	  
	  // Initialize
	  if (pflag && poisonEdges==null && poisonVerts==null) pflag=false;
	  if (alpha>nodeCount || alpha<=0 || isBdry(alpha)) 
		  chooseAlpha();

	  // ============= Try "simple" layout first if it applies 
	  
	  boolean simplayoutflag=false;
	  if (!pflag && isSimplyConnected()) {
		  int sl=0;
		  try {
			  sl=simple_layout(0);
		  } catch (Exception ex) {}
		  if (sl!=0)
			  simplayoutflag=true;
	  }
	  
	  if (simplayoutflag) { // simple layout worked
		  
		  // Now we create/process the redChain
		  
		  // Couple of checks first: Sphere? thus, no redChain needed
	      if (getBdryCompCount()==0) {
	    	  redChain=null;
	    	  return 1;
	      }
	      if (getBdryCompCount()>1) 
	    	  throw new CombException("Claims simply-connected, but 'bdryCompCount'== "+getBdryCompCount());
	      
		  // Need to start with bdry vert having at least two faces
	      int bstart=bdryStarts[1]; 
	      int v=bstart;
		  int safety=nodeCount;
		  while (countFaces(v)<2 && (v=kData[v].flower[0])!=bstart && safety>0) {
			  safety--;
		  }
		  if (countFaces(v)<2 || safety==0) 
			  throw new CombException("No bdry vertices with interior neighbor");
		  
		  // redChain is all bdry faces; start with last face 
		  //    at v (which won't be blue)
	      w=stop_vert=kData[v].flower[0];
		  redChain=trace=new RedList(this,getFaceFlower(w,countFaces(w)-1));
		  trace.center=getCenter(w);
		  trace.vIndex=face_index(trace.face,w);
		  
		  // rwbFlag is reset later in 'build_redchain' and used to identify
		  //   when we get to a red face.
		  faces[trace.face].rwbFlag=1;

		  // add rest of w's flower (reverse order); then continue around boundary
		  boolean keepon=true;
		  while (keepon || w!=stop_vert) {
		      keepon=false;
		      for (int i=countFaces(w)-2;i>=0;i--) {
		    	  trace=trace.next=new RedList(trace,getFaceFlower(w,i));
		      }
		      w=kData[w].flower[0];
		  }
		  // drop the last face, it's a repeat of the initial one
		  trace=trace.remove();
		  redChain=trace.next; // should now point to first 'RedEdge'
		  trace=null; // toss
		  
		  // Main processing: set pointers, set vIndex, center, rad, sidepairing's, etc.
		  firstRedFace=redChain.face;
		  BuildPacket bP=new BuildPacket();
		  bP=redChainer.redface_comb_info(redChain,pflag); //
		  if (!bP.success) {
			  throw new LayoutException("Layout error, simply connected case ");
		  }
		  sidePairs=bP.sidePairs;
		  labelSidePairs(); // establish the pairing 'label's
		  redChain=firstRedEdge=bP.firstRedEdge;
		  // set centers
		  if (redChain!=null) {
			  keepon=true;
			  trace=redChain;
			  while (trace!=redChain || keepon) {
				  keepon=false;
				  trace.center=new Complex(0.0);
				  trace=trace.next;
			  }
		  }
		  
	      if (debug) { // see the current red chain
			  System.out.println("redChain, simply connected case\n"+
					  LayoutBugs.quick_redlist(redChain));
		  }
	      
	      return 1;
	  }

	  // ============== Can't use simple_layout, so use general methods
	  
	  for (int i=1;i<=nodeCount;i++) kData[i].utilFlag=0;
	  for (int j=1;j<=faceCount;j++) faces[j].rwbFlag=0;

	  /* Normally start with 'alpha'. However, if 'pflag' is set and 
	   * 'alpha' is poison, search for non-poison start among nghb's 
	   * of 'alpha', else first eligible interior, else abandon poison 
	   * designations and proceed. */
	  if (pflag && kData[alpha].utilFlag==-1) {
		  k=0;
		  while (kData[kData[alpha].flower[k]].utilFlag==-1
				  && k<=countFaces(alpha)) k++;
		  if (k>countFaces(alpha)) {
			  k=1;
			  while (k<=nodeCount && (isBdry(k) || (kData[k].utilFlag==-1)))
				  k++;
			  if (k==(nodeCount+1)) { // can't find any interior, non-poison; abandon poisons
				  flashError("Had to abandon given poison vertices;"+
						  "Try again with a different initial vertex.");
				  for (int i=1;i<=nodeCount;i++) kData[i].utilFlag=0;
			  }
		  }
		  else alpha=kData[alpha].flower[k]; // replace 'alpha' by its first non-poison petal
	  }
			
	  /* If not a sphere, compute red chain ('pflag' true, then 
	   * watch for poison edges/verts) and preliminary face order.*/
	  BuildPacket bP=redChainer.build_redchain(alpha,pflag);

	  if (!bP.success) {
		  throw new RedListException(bP.buildMsg);
	  }
	  faceOrder=bP.faceOrdering;
	  redChain=bP.redList;
	  firstRedEdge=bP.firstRedEdge; // debug=true; // LayoutBugs.pRedEdges(this);
	  
	  if (debug) LayoutBugs.log_build_faceOrder(this,faceOrder);
	  
	  // 'redChain' should be null only for sphere
	  if (redChain==null && intrinsicGeom<=0) {
		  throw new RedListException("layout error: no 'red chain'");
	  }
	  
	  // Process the red chain to find edges, pairings, etc.
	  initRedCenters(firstRedEdge);
	  if (redChain!=null) {

		  bP=redChainer.redface_comb_info(redChain,pflag);
		  if (!bP.success) {
			  throw new LayoutException("multiply-connected layout error");
		  }
		  sidePairs=bP.sidePairs;
		  labelSidePairs();
		  redChain=firstRedEdge=bP.firstRedEdge;
	  }

	  if (debug) LayoutBugs.log_PairLink(this,sidePairs);
	  
	  // set final order info in 'Face' array 
	  lastface=1;
	  fDo=faceOrder;
	  int []ans=final_order(fDo);
	  if (ans[0]!=0 ) { // this is the outcome flag:
	      // have some missing faces?
	      if (ans[0]==-1) { // error, set up to redo by old reliable methods
	    	  for (int i=1;i<=nodeCount;i++) 
	    		  kData[i].utilFlag=0;
	    	  kData[alpha].utilFlag=1;
	    	  f=getFaceFlower(alpha,0);
	    	  for (int i=0;i<3;i++) 
	    		  kData[faces[f].vert[i]].utilFlag=1;
	    	  fDo=faceOrder; // now, fall through 
	      }
	      if (wrapup_order(lastface)==0) { // old-style order 
	    	  flashError("A drawing order error has occurred");
	      }
	  }

	  if (redChain==null) { // e.g., sphere; throw away red chain 
		  firstRedFace=0;
		  firstRedEdge=null;
	  }

	  for (int i=1;i<=faceCount;i++) {
		  if (faces[i].nextFace==0)
			  faces[i].nextFace=firstFace;
		  if (faces[i].rwbFlag>0 && faces[i].nextRed==0)
			  faces[i].nextRed=firstRedFace;
	  }
	  if (debug) LayoutBugs.log_build_faceOrder(this,faceOrder);
	  return 1;
	} 
	
	/** 
	 * Back through red face list (counterclockwise) while faces share 'vert';
	 * return the face furthest upstream (clockwise).
	 * @param spot RedList
	 * @param vert int
	 * @return RedList
	*/
	public RedList back_red_face(RedList spot,int vert) {
	  int n;

	  while ((n=face_nghb(spot.face,spot.prev.face))>=0
		 && faces[spot.prev.face].vert[n]==vert)
	    spot=spot.prev;
	  return spot;
	} 
	
	/** 
	 * Input: spot_ptr in red chain and index for an edge. (Note: 'index' 
	 * of an edge is index of its first vertex in 'vert' vector for face.)
	 * If indx inappropriate, report first red edge of this face or
	 * next red edge (forward). Otherwise, indx MUST point to a red edge 
	 * and goal is to find the red face having the next contiguous red 
	 * edge forward (dir=1) or backward (dir=-1). Return pointer to 
	 * corresponding red face and pass back index of new red edge as
	 * 'RedList.util' (user must get it right away);null on error.
	 * @param dir in, +1=forward direction, -1=backward (clockwise)
	 * @param spot_ptr RedList 
	 * @param indx int, index of red edge (ie, first vert in face's 'vert') 
	 * @return @RedList, null if not red edges
	 * */
	public RedList next_red_edge(int dir,RedList spot_ptr,int indx) 
	throws RedListException {
	  int vert,n_indx,inx;
	  RedList trace;

	  inx=(face_nghb(spot_ptr.prev.face,spot_ptr.face)+1) % 3;
	  if (indx!=inx && ((spot_ptr.prev.face!=spot_ptr.next.face) ||
			    indx!=(inx+1)%3)) {
	      // indx inappropriate, use new indx
	      indx=inx;
	      vert=faces[spot_ptr.face].vert[indx];
	      if ((n_indx=face_nghb(spot_ptr.next.face,spot_ptr.face))!=indx) {
		  // found red edge in this face 
		  spot_ptr.util=indx;
		  return spot_ptr;
	      }
	      trace=spot_ptr.next;
	      while (trace!=spot_ptr) {
		  n_indx=face_nghb(trace.next.face,trace.face);
		  if (faces[trace.face].vert[n_indx]!=vert) {
		      trace.util=(n_indx+2) % 3;
		      return trace;
		  }
		  trace=trace.next;
	      }
	      return null; // should reach here iff there are no red edges.
	    }

	  // look forward in red chain 
	  if (dir==1) {
	      vert=faces[spot_ptr.face].vert[(indx+1) % 3];
	      if (spot_ptr.next.face==spot_ptr.prev.face
		  && spot_ptr.vIndex==(indx+1) % 3) {
		/* blue face, automatically has two red edges. If indx pts 
		   to first, pass back same face with index to second edge. */
		  spot_ptr.util=(indx+1) % 3;
		  return spot_ptr;
	      }
	      trace=spot_ptr.next;
	      while (trace!=spot_ptr) {
		  n_indx=face_nghb(trace.next.face,trace.face);
		  if (faces[trace.face].vert[n_indx]!=vert) {
		      if (trace.next.face==trace.prev.face)
			trace.util=(n_indx+1)%3;
		      else trace.util=(n_indx+2) % 3;
		      return trace;
		  }
		  trace=trace.next;
	      }
	      throw new RedListException("while looking forward in red chain");
	    }
	  // look backward in red chain 
	  vert=faces[spot_ptr.face].vert[indx];
	  if (spot_ptr.next.face==spot_ptr.prev.face
	      && spot_ptr.vIndex==indx) { /* blue face at second edge; 
					    pass back index to first */
	      spot_ptr.util=(indx+2) % 3;
	      return spot_ptr;
	  } 
	  trace=spot_ptr.prev;
	  while (trace!=spot_ptr) {
	      n_indx=(face_nghb(trace.prev.face,trace.face)+1) % 3;
	      if (faces[trace.face].vert[n_indx]!=vert) {
		  if (trace.prev.face==trace.next.face) // blue 
		    trace.util=(n_indx+1)%3;
		  else trace.util=n_indx;
		  return trace;
	      }
	      trace=trace.prev;
	  }
	  throw new RedListException("error in 'next_red_edge'");
	} 

	/**
	 * Look in given redlist for face f1 having next red f2, return
	 * pointer to RedList with face f2.
	 * 
	 * TODO: shouldn't this be 'RedList' method?
	 * 
	 * @param redlist
	 * @param f1
	 * @param f2
	 * @return RedList having face f2 or null on error or not found
	 */
	public static RedList findContigRed(RedList redlist,int f1,int f2) {
		if (redlist==null) return null;
		RedList trace=redlist;
		int safety=10000;
		while (trace.face!=f1 && safety>0) {
			safety--;
			trace=trace.next;
		}
		if (safety==0)
			return null;
		trace=trace.next;
		safety=10000;
		while (trace.face!=f2) {
			safety--;
			trace=trace.next;
		}
		if (safety==0)
			return null;
		return trace;
	}		

	/** 
	 * Return true if this edge is poison, false otherwise.
	 * If 'poisonEdges' is non-null, we only check if {v,w} is in it;
	 * otherwise, check if both endpoints are in 'poisonVerts'. 
	 * (have utilFlag == -1).
	 * @param v int
	 * @param w int
	 * @return boolean
	*/
	public boolean edge_isPoison(int v,int w) {
		if (poisonEdges!=null) {
			Iterator<EdgeSimple> pe=poisonEdges.iterator();
			while (pe.hasNext()) {
				EdgeSimple edge=(EdgeSimple)pe.next();
				if ((edge.v==v && edge.w==w) || (edge.w==v && edge.v==w)) 
					return true;
			}
			return false;
		}
		return (vert_isPoison(v) && vert_isPoison(w));
	} 

	/** 
	 * Return true if this vert is poison, false otherwise. If
	 * 'poisonEdges' is not null, then check only if 'v' is an 
	 * endpoint of a poison edge. Otherwise, check if 'v' is in
	 * 'poisonVerts'.
	 * @param v int
	 * @return boolean 
	*/
	public boolean vert_isPoison(int v) {
	  if (poisonEdges!=null && (poisonEdges.findV(v)>0 || poisonEdges.findW(v)>0))
			  return true;
	  if (poisonVerts!=null && poisonVerts.containsV(v)>=0) 
		  return true;
	  return false;
	} 

	/** 
	 * Return int[2], where int[0] is flag: 0 for success, all faces plotted; 
	 * -1 error requiring old method; 1 success, but some circles not plotted. 
	 * int[1] contains 'lastface'. 
	 * 
	 * Strategy: Simply connected case, do in order without regard	to red/white 
	 * color. Non-simply connected case, start with	alpha, proceed to first red 
	 * face, do whole red chain, come back for rest. (Needed because some circles 
	 * get moved while drawing redchain.)
	 * @param fDo VertList
	 * @return int[]
	*/
	public int[] final_order(VertList fDo) throws LayoutException,
			RedListException {
		int red_count = 0, f, index, next_face, lastred;
		VertList temp;
		RedList trace;
		int lastface;
		boolean debug = false;

		temp = fDo;
		firstRedFace = 0;
		firstFace = fDo.v;
		for (int i = 1; i <= nodeCount; i++)
			kData[i].utilFlag = 0;
		for (int i = 1; i <= faceCount; i++)
			faces[i].nextFace = faces[i].nextRed = 0;

		if (debug) {
			for (int i = 1; i <= faceCount; i++) {
				System.out.println("f=" + i + "; rwb=" + faces[i].rwbFlag
						+ "; nextFace=" + faces[i].nextFace);
			}
		}

		// take care of first face bookkeeping
		for (int j = 0; j < 3; j++)
			kData[faces[fDo.v].vert[j]].utilFlag = 1;
		fUtil = new int[faceCount + 1];
		fUtil[fDo.v] = 1;
		if (fDo.next == null || fDo.next.next == null)
			throw new LayoutException(
					"Error in beginning of 'fDo' (facedraworder)");

		/* ----------------- simply connected ------------------------- */

		if (isSimplyConnected()) {
			faces[fDo.v].nextFace = f = fDo.next.v;
			index = faces[f].indexFlag;
			faces[f].indexFlag = nice_index(f, index);
			kData[faces[f].vert[(faces[f].indexFlag + 2) % 3]].utilFlag = 1;
			fUtil[f] = 1;
			lastface = f;
			/* now look for qualifying next_face for f to point to */
			fDo = fDo.next.next;
			while (fDo != null) {
				/*
				 * get all rest in fdo, avoiding repeats (i.e., wiping out an
				 * original 'nextFace')
				 */
				while (fDo != null
						&& (faces[fDo.v].nextFace != 0 || fDo.v == f))
					fDo = fDo.next;
				if (fDo != null) {
					f = faces[f].nextFace = fDo.v;
					index = faces[f].indexFlag;
					faces[f].indexFlag = nice_index(f, index);
					kData[faces[f].vert[(faces[f].indexFlag + 2) % 3]].utilFlag = 1;
					fUtil[f] = 1;
					lastface = f;
					fDo = fDo.next;
				}
			} // end of while

			// set first_red_face and red face list in f_data
			if (redChain != null) {
				// skip 'white' or 'free' faces
				while (temp.next != null && faces[temp.v].rwbFlag <= 0)
					temp = temp.next;
				if (temp.next != null
						&& position_redlist(redChain, temp.v) != 0) {
					// seem to have found one
					firstRedFace = temp.v;
					fUtil[firstRedFace] = 1;
					next_face = redChain.next.face;
					faces[firstRedFace].nextRed = next_face;
					fUtil[next_face] = 1;
					trace = redChain.next;
					while (trace != redChain.prev
							&& red_count < (2 * faceCount)) {
						red_count++;
						next_face = trace.next.face;
						// skip over red faces already plotted
						while (trace != redChain.prev && fUtil[next_face] != 0) {
							trace = trace.next;
							next_face = trace.next.face;
						}
						if (trace != redChain.prev) {
							faces[trace.face].nextRed = next_face;
							fUtil[next_face] = 1;
							trace = trace.next;
						}
					} // end of while
					if (red_count > faceCount) {
						throw new LayoutException("redcount > faceCount");
					}
				} else {
					throw new RedListException("error positioning redlist");
				}
			}
			// check for remaining faces
			for (int i = 1; i <= nodeCount; i++)
				if (kData[i].utilFlag == 0) { // circle not plotted
					int[] ans = new int[2];
					ans[0] = 1;
					ans[1] = lastface;
					return ans;
				}
			int[] ans = new int[2];
			ans[0] = 0;
			ans[1] = lastface;
			return ans;
		}

		/* ------------------------ non-simply connected ----------------- */

		else {

			// first, order the interiors;
			f = lastface = firstFace;

			// special handling to get started with first_face.
			if (faces[f].rwbFlag < 0)
				fDo = fDo.next;

			// seek qualifying next_face for f; avoid repeats
			while (fDo != null && faces[fDo.v].rwbFlag < 0) {
				while (fDo != null && faces[fDo.v].rwbFlag < 0
						&& faces[fDo.v].nextFace != 0)
					fDo = fDo.next;
				if (fDo != null && faces[fDo.v].rwbFlag < 0) {
					f = faces[f].nextFace = fDo.v;
					index = faces[f].indexFlag;
					faces[f].indexFlag = nice_index(f, index);
					kData[faces[f].vert[(faces[f].indexFlag + 2) % 3]].utilFlag = 1;
					lastface = f;
					fDo = fDo.next;
				}
			}
			if (fDo == null || faces[fDo.v].rwbFlag < 0) { // should not happen
				faces[f].nextFace = temp.v;
				throw new LayoutException("fDo error.");
			}
			/*
			 * Should be pointing to first red face (which could also be
			 * 'first_face' itself); now, put in whole red chain, also arrange
			 * 'nextRed' data
			 */
			if (f == firstFace && faces[f].rwbFlag >= 0) {
				// firstFace red? already in place
				firstRedFace = firstFace;
				
			} else {
				f = faces[f].nextFace = firstRedFace = fDo.v;
				index = faces[f].indexFlag;
				faces[f].indexFlag = nice_index(f, index);
				kData[faces[f].vert[(faces[f].indexFlag + 2) % 3]].utilFlag = 1;
			}
			fUtil[f] = 1;
			lastface = f;

			/*
			 * We need a qualifying nextFace for f, but now we look in
			 * 'redChain', being careful to avoid repeats
			 */
			RedList rdlst = redChain;
			position_redlist(rdlst, firstRedFace);
			trace = redChain.next;

			while (trace != redChain && red_count < (3 * faceCount)) {
				red_count++;
				// skip over faces with next_face already set
				// careful not to point to self
				while (trace != redChain
						&& (faces[trace.face].nextFace != 0 || trace.face == f))
					trace = trace.next;
				if (trace != redChain) {
					f = faces[f].nextFace = faces[f].nextRed = trace.face;
					index = faces[f].indexFlag;
					faces[f].indexFlag = nice_index(f, index);
					kData[faces[f].vert[(faces[f].indexFlag + 2) % 3]].utilFlag = 1;
					fUtil[f] = 1;
					lastface = f;
					trace = trace.next;
				}
			}
			if (red_count > (3 * faceCount))
				throw new LayoutException("'red_count' is too large");

			if (debug)
				LayoutBugs.print_drawingorder(this, true); // with faces
			if (debug)
				LayoutBugs.print_drawingorder(this, false);

			// now at last red face
			lastred = f;

			/*
			 * get rest of white by following fDo; avoid repeats, skip red. The
			 * last red face must point to the rest of the list for purposes of
			 * 'comp_pack_centers'. Last face indicated by nextFace=0.
			 */
			while (fDo != null) {
				while (fDo != null
						&& (faces[fDo.v].rwbFlag >= 0 || faces[fDo.v].nextFace != 0))
					fDo = fDo.next;
				if (fDo != null) {
					if (lastred != 0) {
						faces[lastred].nextFace = fDo.v;
						lastred = 0;
					}
					f = faces[f].nextFace = fDo.v;
					index = faces[f].indexFlag;
					faces[f].indexFlag = nice_index(f, index);
					kData[faces[f].vert[(faces[f].indexFlag + 2) % 3]].utilFlag = 1;
					fUtil[f] = 1;
					lastface = f;
					fDo = fDo.next;
				}
			}

			// check: any circles missed?
			for (int i = 1; i <= nodeCount; i++)
				if (kData[i].utilFlag == 0) {
					int[] ans = new int[2];
					ans[0] = 1;
					ans[1] = lastface;
					return ans;
				}
			int[] ans = new int[2];
			ans[0] = 0;
			ans[1] = lastface;
			return ans;
		} // end of non-simply connected case
	}

	/** 
	 * Wrap up face order. fUtil should have been set in
	 * 'final_order' to show faces handled already (reset to zero if 
	 * final_order hit a snag). Put in remaining faces; return 0 on error.
	 * @param lastface int
	 * @return 1, 0 on error 
	*/
	public int wrapup_order(int lastface) {
	  int k=1,hit=1,F,i,index,count=0,n;
	  boolean stop=false;
	  F=faceCount;
	  for (i=1;i<=F;i++) if (fUtil[i]!=0) count++;
	  while (count<F && !stop) {
		if (k > F) {
		  k=1;
		  if (hit==0) stop=true; // no new faces being added 
		  hit=0;
		}
		while (fUtil[k]!=0 && k < F) k++;
		if (fUtil[k]==0 && (index=face_ready(faces[k]))>=0) { 
			if ((n=face_nghb(lastface,k))<0) {
				faces[k].indexFlag=index;
			}
			else faces[k].indexFlag=n;
			faces[lastface].nextFace=k;
			fUtil[k]=1;
			kData[faces[k].vert[(index+2) % 3]].utilFlag=1;
			lastface=k;
			count++;
			hit=1;
		}
		k++;
	  }
	  faces[lastface].nextFace=firstFace;
	  if (count<F) return 0; // should never happen 
	  return 1;
	} 
	
	/**
	 * If face ready to draw, return index of vert to be drawn first.
	 * @param face int
	 * @return int, -1 on error. 
	*/
	public int face_ready(Face face) {
	  if (kData[face.vert[0]].utilFlag !=0 && kData[face.vert[1]].utilFlag!=0)
		  return 0;
	  if (kData[face.vert[1]].utilFlag!=0 && kData[face.vert[2]].utilFlag!=0)
	      return 1;	
	  if (kData[face.vert[2]].utilFlag !=0 && kData[face.vert[0]].utilFlag!=0)
		  return 2;
	  return -1;
	} 
	
	/** 
	 * Position 'redChain' at next occurance of 'face' in red chain 
	 * (skip 'redFace' itself and test it when it comes around at end); 
	 * return 0 if 'face' not found. 
	 * 
	 * TODO: should this be static method in 'RedList'?
	 * 
	 * @param redface RedList
	 * @param face int
	 * @return int, 0 if not found
	*/
	public int position_redlist(RedList redface,int face) {
	  RedList current;

	  current=redface.next;
	  if (current.face==face) {
	      redChain=current;
	      return 1;
	    }
	  current=current.next;
	  while (current!=redface.next) {
	      if (current.face==face) {
		  redChain=current;
		  return 1;
	      }
	      current=current.next;
	  }
	  return 0;
	} 

	/** 
	 * set indexFlag of face f so that, if possible, it uses nghb'ing
	 * white face using verts already drawn. "index" is first
	 * preference and default. index<0 or >2 will give exception.
	 * Note: 'utilFlag' nonnegative for verts already drawn.
	 * Note: 'rwbFlag' is <0 for white faces 
	 * @param f int, face number
	 * @param index int, index in face 'vert' list
	 * @return int, suggested index.
	 */
	public int nice_index(int f,int index) {
	  int v,w,i,m,num,g;

	  for (i=0;i<3;i++) {
	      v=faces[f].vert[(index+i)%3];
	      w=faces[f].vert[(index+i+1)%3];
	      if (kData[v].utilFlag > 0 && !isBdry(v)
		  && kData[w].utilFlag > 0 && !isBdry(w)) {
		  num=countFaces(w);
		  m=0;
		  while (getFaceFlower(w,m)!=f && m<(num-2)) m++;
		  g=getFaceFlower(w,(m+1) % num);
 		  // if face is "white", use it
		  if (faces[g].rwbFlag<0) 
 			  return ((index+i)%3);
		  }
	  }
	  for (i=0;i<3;i++) { // fallback: try for white neighbor 
	      v=faces[f].vert[(index+i)%3];
	      w=faces[f].vert[(index+i+1)%3];
	      if (kData[v].utilFlag > 0 && kData[w].utilFlag!=0) {
		  num=countFaces(w);
		  m=0;
		  while (getFaceFlower(w,m)!=f && m<(num-2)) m++;
		  g=getFaceFlower(w,(m+1) % num);
		  // if face is "white", suggest it
		  if (faces[g].rwbFlag<0) 
			  return ((index+i)%3);
		}
	  }
	  for (i=0;i<3;i++) { // fallback: use any two plotted ngbh's.
	      v=faces[f].vert[(index+i)%3];
	      w=faces[f].vert[(index+i+1)%3];
	      if (kData[v].utilFlag > 0  && kData[w].utilFlag!=0) 
	    	  return ((index+i)%3);
	    }
	  if (index<0 || index>2) return 0;
	  return index;
	} 

	/**
	 * Return incircle of face f. Note: this in the incircle of the
	 * triangle formed by the centers of the circles, irrespective of
	 * whether the packing has non-trivial inv distances. Can also be 
	 * complicated in non-simply connected situation.
	 * @param f int
	 * @param amb AmbiguousZ[]
	 * @return CircleSimple, null on error
	 */
	public CircleSimple faceIncircle(int f,AmbiguousZ []amb) {
		if (f<1 || f>faceCount) return null;
		Complex []pts=corners_face(f,amb);
		if (hes<0) { //
			CircleSimple sc1=HyperbolicMath.hyp_tang_incircle(pts[0],pts[1],pts[2],
					getRadius(faces[f].vert[0]),
					getRadius(faces[f].vert[1]),
					getRadius(faces[f].vert[2]));
			
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
			for (int j=0;j<countFaces(v);j++) {
				int n=kData[v].flower[j];
				int m=kData[v].flower[j+1];
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
	 * Compute centers of face: 'indx' circle at origin, indx+1
	 * in standard orientation (namely, in eucl, on positive x-axis),
	 * indx+2 determined by law of cosines.
	 *  
	 * TODO: hyp radii are stored as x-radius, yet our computations
	 * remain in s-radius, so we have to convert.
	 * @param face int
	 * @param indx int, circle to be at origin
	 * @return int, 0 on layout error
	*/
	public int place_face(int face,int indx) {
	  double ovlp;

	  if (face>faceCount || face<1 || indx <0 || indx >2) return 0;
	  int a=faces[face].vert[indx]; // at origin
	  int k=faces[face].vert[(indx+1) % 3]; // on positive x-axis
	  int v=faces[face].vert[(indx+2) % 3]; // to be computed
	  if (overlapStatus)
	    ovlp=getInvDist(a,k);
	  else ovlp=1.0;
	  if (hes<0) { // hyp case 
	    double x1=getRadius(a);
	    double s1=HyperbolicMath.x_to_s_rad(x1);
	    double x2=getRadius(k);
	    double s2=HyperbolicMath.x_to_s_rad(x2);
	    if (s1<=0) {
	      x1 = 0.99;
	      s1=HyperbolicMath.x_to_s_rad(x1);
	      setRadius(a,x1);
	      /* strcpy(msgbuf,"Circle at origin had "
		     "infinite radius; radius reset.");*/
	    }
	    setCenter(a,0.0,0.0);
	    if (s2<=0) { /* if next one is infinite radius */
	      setCenter(k,1.0,0.0);
	      double erad=x1/((1+s1)*(1+s1));
	      setRadiusActual(k,(-1)*(1-erad*erad)/(2.0+2.0*erad*ovlp));
	    }
	    else { 
	      double x12 = x1*x2;
	      double x1p2 = x1+x2;
	      double s12 = s1*s2;
	      double x = (x1p2-x12)/(s12*(1+s12)) - (2*x1p2 - (1+ovlp)*x12)/(4*s12);
	      double s= x + Math.sqrt(x*(x+2));
	      setCenter(k,s/(s+2),0.0);
	    }
	  }
	  else if (hes>0) { // sphere case 
	      // alpha at north pole 
	    setCenter(a,0.0,0.0);
	    // next out pos x-axis 
	    // TODO: need to incorporate overlaps
	    setCenter(k,0.0,getActualRadius(a)+getActualRadius(k));
	  }
	  else { // eucl case 
	      // alpha at origin 
	    double r=getRadius(a);
	    setCenter(a,0.0,0.0);
	    // next on x-axis
	    double r2=getRadius(k);
	    setCenter(k,Math.sqrt(r*r+r2*r2+2*r*r2*ovlp),0.0);
	  }
	  return (fancy_comp_center(v,nghb(v,a),0,1,1,true,false,TOLER));
	}
	
	/** 
	 * Compute and store center for vert by specified method using 
	 * pair of contiguous petals. 
     * crit_flag: only use centers from computations where invdist_err < crit. Eg.
     * 	in opt=2 case, don't put poor results into average; but note this
     * 	doesn't mean different values going into average might not be off from
     * 	one another. (That's to worry about in the future.)
     * This routine does not set 'plotFlag' for 'vert'.
     * @param vert int, index of circle being computed
     * @param n0 int, petal index for 'opt'=1 case
     * @param n1 int, petal index for 'opt'=2 case
	 * @param n int, number of petals (or faces) to use in 'opt'=2 case
     * @param opt int
	 *    opt=1: by drawing order (original method): use index n0 
	 *    	petal and its oriented neighbor petal only.
	 * 	  opt=2: average all centers as computed from pairs of contig 
	 * 	    petals, starting with flower index n1 and checking next n 
	 * 		faces around.
	 * @param npf boolean, true ==>  disregard 'plotFlag's; false ==> use only 
	 * 		pairs of circles with plotFlag >0.
	 * @param crit double, if placement quality > crit, then do not
	 * 	    set plotFlag.
	 * @return 0 if center is not well-placed 
	 */
	public int fancy_comp_center(int vert,int n0,int n1,int n,
			      int opt,boolean npf,boolean crit_flag,double crit) {
	  int count=0;

	  int num=countFaces(vert)+getBdryFlag(vert);
	  int[] flower=getFlower(vert);
	  if (opt==1) {n1=n0; n=1;}
	  /*  use original center already computed??
	      if (kData[vert].plot_flag>0) z=rData[vert].center;*/
	  Complex z=new Complex(0.0,0.0);
	  for (int i=n1;i<(n1+n);i++) {
		  CircleSimple sc;
	    int j=flower[i % num];
	    int k=flower[(i+1) % num];
	    if (npf || (getPlotFlag(j) > 0 && getPlotFlag(k) > 0)) {
	    	
				if (overlapStatus) {
					double o1 = getInvDist(k,vert);
					double o2 = getInvDist(vert,j);
					double o3 = getInvDist(j, k);
					sc = CommonMath.comp_any_center(getCenter(j), getCenter(k),
							getRadius(j),getRadius(k),getRadius(vert), o1, o2, o3,hes);
				} 
				else
					sc = CommonMath.comp_any_center(getCenter(j),getCenter(k),
							getRadius(j),getRadius(k),getRadius(vert),hes);
				if (!sc.gotError()
						|| !crit_flag
						|| (Math.abs(invdist_err(vert, j)) < crit && Math
								.abs(invdist_err(vert, k)) < crit)) {
					if (sc.save(this, vert) >= 0) { // 
						z = z.add(getCenter(vert));
						count++;
					}
				}
			}
	  }
	  if (count==0) return 0;
	  if (count==1) return 1;
	  z=z.times(1.0/((double)count));
	  setCenter(vert,z);
	  return (count);
	} 
	
	/**
	 * Find circle centers for packing based on current radii by going through
	 * faces in order (see 'facedraworder'). Various options and error cutoffs 
	 * to improve quality, catch errors. (Note: spherical layout is not
	 * robust (actually, it hardly exists); best to project from plane or disc.)
	 * 
	 * Each vertex has 'plot_flag'; set to 1 if placement seems okay; some 
	 * display/print situations may use only circles with plot_flag>0.
	 * 
	 * Start layout with 'firstFace' (normally, 'alpha' at origin); last step
	 * normalizes 'gamma' vert on y>0 axis. Only compute each circle's center
	 * once. If errflag is true, only circles placed w/o too much error have
	 * plot_flag>0.
	 * 
	 * Wrap up tasks: identify/fix any 'nan' centers, which happen most often in
	 * inv dist cases. For now, fix by using average of nghb centers.
	 * 
	 * Record rad/cent in redlist, if it exists, BUT use cent/rad data from pack
	 * (not redlist data).
	 * 
	 * Use redlist data to set any side-pairing Mobius transforms.
	 * 
	 * @param errflag boolean: true, then plot_flag <=0 if center doesn't meet 
	 * placement criteria; use only verts with plotFlag's >0 when computing 
	 * later centers. 
	 * @param dflag boolean: true, then write diagnostic file /tmp/layout.log 
	 * of problem placements.
	 * @param opt int: 1 = use drawing-order only (ie. go to next face, compute center of
	 * third vert from other two -- this is the original 'fix' method); 2 = use
	 * average of already plotted neighbors; 3 = ??? 
	 * @param crit double: gives accuracy criteria (if errflag true). Eg., don't
	 * use computed center of v if nghbs u,w actual distance apart is off by
	 * factor > crit. Should have default value to use for crit in general; can
	 * experiment with it.
	 * @return int, number of failed placements.
	 * 
	 */
	public int comp_pack_centers(boolean errflag,boolean dflag,int opt,double crit) 
	throws CombException,MobException,RedListException,IOException {
		
		// TODO: ignore parameters for now in DCEL case
		if (packDCEL!=null) {
			return packDCEL.dcelCompCenters();
		}
		
	  int nf,n0,n1,n,vert,count,v,indx,lastface;
	  int v_ind;
	  boolean keepon=true;
	  boolean simp_conn_flag=false;
	  boolean flick=true;
	  boolean carryon=false;
	  boolean tmp_debug=false;
	  Complex z;
	  RedList trace;
	  BufferedWriter logwriter=null;
	  boolean debug=false;
	  
	  if (debug) LayoutBugs.print_drawingorder(this,true);

	  if ((euler==1 || euler==2) && genus==0) 
		  simp_conn_flag=true;
	  for (int j=1;j<=nodeCount;j++) { 
		  setPlotFlag(j,0);
		  setQualFlag(j,0);
	  }
	  for (int j=1;j<=faceCount;j++) 
		  setFacePlotFlag(j,1);
	  nf=firstFace;
	  File file=null;
	  
	  if (dflag) { // dflag=true;
	  
		  file = new File(System.getProperty("java.io.tmpdir")+File.separator+"layout_log");
		  try {
			logwriter = new BufferedWriter(new FileWriter(file));
		    logwriter.write("Diagnostic file created in 'comp_pack_centers'.\n");
		    logwriter.write("Packing "+fileName+", nodecount "+nodeCount+
		    		", 'critical' tolerance set at "+crit+"\n\n");
		  } catch(IOException ioe) {
				String cof=new String("Couldn't open '"+file.toString()+"'");
				PackControl.consoleCmd.dispConsoleMsg(cof);
				PackControl.shellManager.recordError(cof);
			return 0;
		  }
	  }
		

	  // bomb out if first face fails to lay out properly

	  if (place_face(nf,faces[nf].indexFlag)==0) {
	    flashError("Layout error in placing the initial face");
	    if (dflag) {
	      logwriter.write("first face, "+nf+" problem.\n");
	      logwriter.flush();
	      logwriter.close();
	      return 0;
	    }
	    return 0;
	  }
	   
	  for (int j=0;j<3;j++) kData[faces[nf].vert[j]].plotFlag=1;
	  count=3;

	/* ------------ simply connected case ----------- */

	  if (simp_conn_flag) {
	    while ( (nf=faces[nf].nextFace)!=firstFace 
		    && nf>0 && nf<=faceCount) {
	      vert=faces[nf].vert[(indx=(faces[nf].indexFlag +2) % 3)];
	      n0=nghb(vert,faces[nf].vert[(indx+1)%3]);
	      if (kData[vert].plotFlag<=0 && (kData[vert].plotFlag=
	    	  fancy_comp_center(vert,n0,0,countFaces(vert),opt,false,errflag,crit))!=0)
	    	  count++;
	      else if (kData[vert].plotFlag<=0 && dflag)
	    	  logwriter.write("\t"+count+"\t"+vert+"\t"+nf+"\n");
	    }
	  } // end of first pass in simply connected case

	/* -------- multiply connected case ----------------- */

	  else { 
	    for (int i=1;i<=nodeCount;i++) kData[i].utilFlag=0;

	    if (opt==2) { /* want to use average, so have to mark outer verts of
					   * red chain using utilFlag; need to find fans. */
	      trace=redChain;
	      keepon=true;
	      while (trace!=redChain || keepon) {
	    	  keepon=false;
	    	  indx=face_nghb(trace.next.face,trace.face);
	    	  v=faces[trace.face].vert[indx];
	    	  kData[v].utilFlag=1;
	    	  if (trace.next.face==trace.prev.face) { // blue?
	    		  v=faces[trace.face].vert[(indx+2) % 3];
	    		  kData[v].utilFlag=1;
	    	  }
	    	  trace=trace.next;
	      }
	    }

	    lastface=firstFace;
	    nf=firstFace;
	    if (faces[firstFace].nextFace==firstRedFace)
	    	nf=firstRedFace;

//	    LayoutBugs.log_Red_Hash(this,this.redChain,this.firstRedEdge);
	    
	    // this while loops exits from a 'break'; count is a safety 
	    while (nf>0 && nf<=faceCount && count<(3*faceCount)) {
	    	
//System.err.println("nf="+nf);	    	

	    	if (flick && nf==firstRedFace) { 	// yes, reached firstRedFace
	    		
//System.err.println("go with red");

	    		/*
	    		 * When we first reach firstRedFace, we switch to layout whole red chain
	    		 * in order. This is: 1) necessary because some faces will occur twice,
	    		 * placing possibly a different one of its circles each time, and 2) to
	    		 * load rad/cent data in red chain faces for other purposes. 
	    		 * Note: the kData[].centers end up wherever last placed.
	    		 */
		
	    		flick=false;
	    		vert=faces[nf].vert[(indx=(faces[nf].indexFlag +2) % 3)];
	    		n0=nghb(vert,faces[nf].vert[(indx+1)%3]);
	    		if (kData[vert].utilFlag!=0) { // red chain, outer vert, using averaging
	    			int ans[] =red_fan(redChain,vert);
	    			n1=ans[0];
	    			n=ans[1];
	    		}
	    		else {
	    			n1=0;
	    			n=countFaces(vert);
	    		}
	    		if (kData[vert].plotFlag<=0 && (kData[vert].plotFlag=
	    			fancy_comp_center(vert,n0,n1,n,opt,false,errflag,crit))!=0) {
	    			count++;
	    		}
	    		else if (kData[vert].plotFlag>=0 && dflag)
	  	    	  logwriter.write("\t"+count+"\t"+vert+"\t"+nf+"\n");
	    		
	    		// position the first outer circle of the red chain
	    		redChain.rad=getRadius(faces[redChain.face].vert[redChain.vIndex]);
	    		redChain.center=new Complex(getCenter(faces[redChain.face].vert[redChain.vIndex]));
	    		
	    		/*
	    		 * Now proceed through rest of the redchain. Which center to compute is
	    		 * based on indices stored in f_data. This should fit 'nextFace' order
	    		 * (which reflects redFace order). Note, this can differ from 'vIndex' 
	    		 * in redlist. Record rad/cent whether or not error is within tolerance.
	    		 */
		
	    		/*
	    		 * This can be tricky. One problem with going through redfaces in
	    		 * order, each depending only on last, is that errors will build up and
	    		 * bdry may detach from white faces. Indices in f_data try to be smart
	    		 * in choosing which of three verts to plot, but I'm not sure if it
	    		 * might rely on outdated locations for the other two. May have to pad
	    		 * the 'nextFace' order with sections of redfaces list which it skips
	    		 * (because those faces already had a 'nextFace' -- see 'final_order').
	    		 */
	    		
	    		lastface=firstRedFace;
	    		trace=redChain.next;
	    		while (!carryon && trace!=redChain) {
	    			nf=trace.face;
	    			if ((indx=face_nghb(lastface,nf))<0) {
	    				flashError("Problem with redFace order, redFace = "+redChain+
	    						", lastface = "+lastface);
	    				lastface=nf;
	    				carryon=true; // jump out and continue
	    			}
	    			if (!carryon) {
	    				indx=(faces[nf].indexFlag+2) % 3;
	    				vert=faces[nf].vert[indx];
	    				n0=nghb(vert,faces[nf].vert[(indx+1)%3]);
	    				if (kData[vert].utilFlag!=0) { // this is redchain outer vert and we're averaging
	    					int ans[]=red_fan(trace,vert);
	    					n1=ans[0];
	    					n=ans[1];
	    				}
	    				else {
	    					n1=0;
	    					n=countFaces(vert);
	    				}
	    				if ((kData[vert].plotFlag=
	    					fancy_comp_center(vert,n0,n1,n,1,false,false,crit))!=0) {

	    					/*
	    					 * Note: since the red faces are needed downstream and it's
	    					 * difficult to keep track of which nghbs should be used to
	    					 * plot a vert at each visit, we use values errflag=0 and
	    					 * opt=1 in this call.
	    					 */
		    
	    					/*
	    					 * fixup: Can we rearrange drawing order so as many verts as
	    					 * possible not on outside of red chain are laid out first,
	    					 * then the red chain?
	    					 */
		    
	    					count++;
	    				}
	    				else if (dflag)
	    	    			logwriter.write("Error: \t face "+nf+";\t vert "+vert+
	    	    					";\tcount "+count+". Failed in fancy_comp_center.\n");
		  
	    				/*
	    				 * Now record rad/cent in redface data. This may not be the data
	    				 * we just computed, so we may have to do yet another
	    				 * computation here (depends on 'vIndex').
	    				 */
		  
	    				v_ind=faces[nf].vert[trace.vIndex];
	    				if (v_ind!=vert) { // update center for 'vIndex' of redface.
	    					n0=nghb(v_ind,faces[nf].vert[((trace.vIndex)+1)%3]);
	    					fancy_comp_center(v_ind,n0,0,0,1,true,false,crit);
	    				}
	    				trace.center=getCenter(v_ind);
	    				trace.rad=getRadius(v_ind);
	    				
	    				// Suppose this is 'RedEdge' and it's blue; then 'nextRed' is a repeat
	    				//    of this face, and we store its center and rad. The center is 
	    				//    stored in the 'prev' face (typically a 'redList' not a 'redEdge').
	    				//    Note: the 'prev' and 'next' faces are the same, but the center
	    				//    of interest is that downstream of the center for this redEdge, and
	    				//    that's stored as 'prev.center'.
	    				if (trace instanceof RedEdge) {
	    					RedEdge rtrace=(RedEdge)trace;
	    					if (rtrace.face==rtrace.nextRed.face) {
	    						rtrace=rtrace.nextRed;
	    						vert=faces[nf].vert[rtrace.vIndex];
	    						rtrace.center=new Complex(rtrace.prev.center);
	    						rtrace.rad=getRadius(vert);
	    					}
	    				}
	    				lastface=nf;
	    				trace=trace.next;
	    			}
		  
	    			if (tmp_debug) {  // set during debugging
	    				if (dflag) LayoutBugs.log_Red_Hash(this,redChain,null);// LayoutBugs.log_Red_Hash(this,redChain,firstRedEdge);
	    			}
		  
	    		} // end of while

//System.err.println("done with red; nf="+nf+" lastface="+lastface);	    		
	    	} // end of if (flick ...
	      
	    	// done with red chain; pick up order from f_data again

	    	if ((nf=faces[lastface].nextFace)==firstFace) {
	    		break;
	    	}
	    	vert=faces[nf].vert[(indx=(faces[nf].indexFlag +2) % 3)];
	    	n0=nghb(vert,faces[nf].vert[(indx+1)%3]);
	    	if (kData[vert].plotFlag<=0 && (kData[vert].plotFlag=
	    		fancy_comp_center(vert,n0,0,countFaces(vert),opt,false,errflag,crit))!=0)
	    		count++;
	    	else if (dflag && kData[vert].plotFlag<=0)
	    		logwriter.write("\t"+count+"\t"+vert+"\t"+nf+"\n");

	    	lastface=nf;
	    	nf=faces[lastface].nextFace;
	    }
	  } // end of multiply connected case
	  
	  if (dflag) {
		  logwriter.write("\nFinished original pass with "+count+" successful"+
				  " placements, nodeCount ="+nodeCount+".\n\n");
		  logwriter.flush();
		  logwriter.close();
		  CirclePack.cpb.msg("Layout logged in "+file);
	  }
	  
	/*
	 * Check whether some circles weren't placed. Make several passes to catch
	 * any further legitimate layouts. Note: count may exceed nodeCount in
	 * multiply-connected cases since a center may be computed several times
	 */

	  keepon=false; // LayoutBugs.pRedEdges(this);
	  for (int i=1;i<=nodeCount;i++)
		  if (kData[i].plotFlag==0) keepon=true;
	  if (keepon) { // Some circles not plotted
	      while (keepon) { // with hits, keep cycling through
	    	  keepon=false;
	    	  nf=firstFace;
	    	  while ((nf=faces[nf].nextFace)!=firstFace && nf>0 && nf<=faceCount) {
	    		  vert=faces[nf].vert[(indx=(faces[nf].indexFlag +2) % 3)];
	    		  if (kData[vert].plotFlag<=0) {
	    			  n0=nghb(vert,faces[nf].vert[(indx+1)%3]);
	    			  if ((kData[vert].plotFlag=
	    				  fancy_comp_center(vert,n0,0,countFaces(vert),opt,false,errflag,crit))!=0) {
	    				  count++;
	    				  keepon=true;
	    			  }
	    		  }
	    	  } // end of inner while
	      } // end of outer while

	    /*
		 * Try to find appropriate center for circles that didn't plot for some
		 * reason (eg. inv dist incompatibility). Simply average the centers of
		 * any petals that are plotted (often problem circles are extremely
		 * small, so this is reasonable). I'll use 'qualFlag' to keep track, and
		 * pass through repeatedly as long as we're getting results. (Some
		 * centers may be based on neighbors which were only set based on their
		 * neighbors, but at least they're getting some location!) I do not set
		 * their plotFlags.
		 */

	    keepon=true;
	    while (keepon) {
	    	keepon=false;
	    	for (vert=1;vert<=nodeCount;vert++) {
	    		if (kData[vert].plotFlag==0 && kData[vert].qualFlag==0) {
	    			z=new Complex(0.0);
	    			n=0;
	    			// first use plotted neighbors
	    			for (int j=0;j<(countFaces(vert)+getBdryFlag(vert));j++) 
	    				if (kData[(v=kData[vert].flower[j])].plotFlag!=0) {
	    					z=z.add(getCenter(v));
	    					n++;
	    				}
	    			if (n==0) { // none? try qualFlag neighbors
	    				for (int j=0;j<(countFaces(vert)+getBdryFlag(vert));j++) 
	    					if (kData[(v=kData[vert].flower[j])].qualFlag!=0) {
	    						z=z.add(getCenter(v));
	    						n++;
	    					}
	    			}
	    			if (n!=0) { // got something!
	    				z=z.times(1/n);
	    				setCenter(vert,z);
	    				kData[vert].qualFlag=1;
	    				count++;
	    				keepon=true;
	    			}
	    		}
	    	} // end of for loop
	    } // end of while loop
	  }

	  if (simp_conn_flag && (trace=redChain)!=null) {
	    keepon=true;
	    while (trace!=redChain || keepon) {
		keepon=false;
	      v=faces[trace.face].vert[trace.vIndex];
	      trace.center=getCenter(v);
	      trace.rad=getRadius(v);
	      trace=trace.next;
	    }
	  }

	  // Set face plotFlag's to default
	  for (int j=1;j<=faceCount;j++) faces[j].plotFlag=1;

	  /*
		 * For simply connected, try this; precision is rather experimental.
		 * fixup: this is much more complicated in multiply conn case.
		 */

	  if (simp_conn_flag && (trace=redChain)!=null && errflag) {
	    for (int j=1;j<=faceCount;j++)
	      if (kData[faces[j].vert[0]].plotFlag<=0
		  || kData[faces[j].vert[1]].plotFlag<=0
		  || kData[faces[j].vert[2]].plotFlag<=0
		  || Math.abs(invdist_err(faces[j].vert[0],faces[j].vert[1]))
		  >100000*TOLER
		  || Math.abs(invdist_err(faces[j].vert[1],faces[j].vert[2]))
		  >100000*TOLER
		  || Math.abs(invdist_err(faces[j].vert[2],faces[j].vert[0]))
		  >100000*TOLER)
		faces[j].plotFlag=0;
	  }
	    
	  // Normalize: fixup? what if errflag is set and gamma is poorly plotted?

	  norm_any_pack(getCenter(alpha),getCenter(gamma)); 

	  if (!simp_conn_flag) {
	    // Record any edge-pairing Mobius transforms
	    this.update_pair_mob();
	    // sprintf(msgbuf,"run updateSidePairs");
	    // debugmsg();
// updateSidePairs(); // send to Java
	  }

	  return (count);
	}

	/**
	 * Update the mobius transformations associated with side pairings in
	 * multiply connected cases. 
	 * TODO: need error-checking; particularly arithmatic errors 
	 * in routines of Mobius.java.
	 * @return 0 if 'redChain' 'firstRedEdge' or 'sidePairs' is null
	 */
	public int update_pair_mob() throws RedListException, MobException {
		if (packDCEL!=null) {
			if (packDCEL.redChain==null || packDCEL.pairLink==null) {
				Iterator<D_SideData> dsis=packDCEL.pairLink.iterator();
				while (dsis.hasNext()) {
					D_SideData dsdata=dsis.next();
					dsdata.set_sp_Mobius();
				}
			}
			return 1;
		}
		
		// traditional
		if (redChain==null || firstRedEdge==null || sidePairs==null) return 0;
		for (int j=0;j<sidePairs.size();j++) {
			SideDescription ep=(SideDescription)sidePairs.get(j);
			ep.set_sp_Mobius();
		}
		return 1;
	} 

	/** 
	 * Compute error (target-actual) for inv_distance (including
	 * overlap) between neighboring vertices v and w.
	 * @param v int
	 * @param w int
	 * @return double
	*/
	public double invdist_err(int v,int w) {
		  int k;
	  double target=1.0;

	  if (v<0 || w<0 || v>nodeCount || w>nodeCount 
	      || (k=nghb(v,w))<0) return 0.0;
	  if (overlapStatus) target=getInvDist(v,kData[v].flower[k]);
	  return (target-comp_inv_dist(v,w));
	}

	/** 
	 * Return inversive distance between circles for v and w,
	 * not necessarily neighbors. Inv dist goes from -1 to infinity. 
	 * rho is in [-1,1] for overlap situation, then rho=cos(overlap angle); 
	 * rho=0 for orthogonal circles; rho=1 for tangency; rho>1 for
	 * separated circles, where invdist is cosh of the hyperbolic distance
	 * between the circles (bubbled into hyperbolic upper half space).
	 * 
	 * NOTE: I often use "overlap" to include both overlap and separated.
	 * Even true overlaps are stored in the data using inversive distance,
	 * i.e., the cosine of actual angle.
	 *  
	 * TODO: routines not very robust
	 *  
	 * @param v int
	 * @param w int
	 * @return double, 1.0 (tangency) on error or problem
	 */
	public double comp_inv_dist(int v,int w)
	{
	  double erad1,erad2;
	  Complex ectr1,ectr2;
	  CircleSimple sc;

	  if (v<1 || w<1 || v>nodeCount || w>nodeCount) return 1.0; 
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
	  if (hes<0) { // hyperbolic: use euclidean dat 
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
	 * Standard normalization: a at origin, g on positive y-axis.
	 * Not ready for spherical case yet
	 * @param a Complex
	 * @param g Complex
	 * @return int, 0 for the sphere
	 */
	public int norm_any_pack(Complex a,Complex g) {
	  if (hes<0) return HyperbolicMath.h_norm_pack(this,a,g);
	  if (hes==0) return EuclMath.e_norm_pack(this,a,g);
	  else return 0; // TODO: fixup? haven't yet done sphere case 
	} 

	/**
	 * Fill in curvatures of the packing. 
	 * @return int 1
	*/
	public int fillcurves() {
		int v;
		UtilPacket uP=new UtilPacket();

		uP.value=0.0;
		for (v=1;v<=nodeCount;v++) {
			if (!CommonMath.get_anglesum(this,v,getRadius(v),uP)) 
				throw new DataException("failed to compute angle sum for "+v);
			setCurv(v,uP.value);
	    }
		return 1;
	}
	
	/** 
	 * Compute angle sum at v, allowing for inversive distances.
	 * uP.value = anglesum. Return false in case of radii/inv dist 
	 * incompatibilies (results are not reliable).
	 * @param v int
	 * @param r double
	 * @param uP UtilPacket
	 * @return boolean, 
	*/
	public boolean e_anglesum_overlap(int v,double r,UtilPacket uP) {
		  int j2=getFirstPetal(v);
		  double r2=getRadius(j2);
		  uP.value=0.0;
		  if (!overlapStatus) {
		      double m2 = r2/(r+r2);
		      for (int n=1;n<=countFaces(v);n++) {
		    	  double m1 = m2;
		    	  r2 = getRadius(getPetal(v,n));
		    	  m2 = r2/(r+r2);
		    	  uP.value += Math.acos(1-2*m1*m2);
		      }
		  }
		  else  {
		      double o2=getInvDist(v,getFirstPetal(v));
		      for (int n=1;n<=countFaces(v);n++) {
		    	  int j1=j2;
		    	  double r1=r2;
		    	  double o1=o2;
		    	  j2=getPetal(v,n);
		    	  r2=getRadius(j2);
		    	  o2=getInvDist(v,getPetal(v,n));
		    	  double ovlp=getInvDist(j1,j2);
		    	  uP.value += Math.acos(EuclMath.e_cos_overlap(r,r1,r2,ovlp,o2,o1));
		      }
		  }
		  return true;
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
	  if (r<=0) return false;
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
	    UtilPacket tmpUP=new UtilPacket();

	    uP.value=0.0;
	    if (x<=0) { // infinite radius at vertex of interest 
	    	uP.value=x;
	        return true;
	    } 
	    int j2=getLastPetal(v);
	    double x2=getRadius(j2);
	    if (!overlapStatus) {
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
	    UtilPacket curveUp=new UtilPacket();
	  
	  if (!e_anglesum_overlap(v,r,curveUp)) 
		  return false;
	  double bestcurv=lowcurv=upcurv=curveUp.value;
	  if (bestcurv>(aim+OKERR)) {
	      upper=r/factor;
	      if (!e_anglesum_overlap(v,upper,curveUp)) 
	    	  return false;
	      upcurv=curveUp.value;
	      if (upcurv>aim) {
	    	  uP.value=upper;
	    	  return true;
	      }
	  }
	  else if (bestcurv<(aim-OKERR)) {
	      lower=r*factor;
	      if (!e_anglesum_overlap(v,lower,curveUp)) 
	    	  return false;
	      lowcurv=curveUp.value;
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
	      if (!e_anglesum_overlap(v,r,curveUp)) return false;
	      bestcurv=curveUp.value;
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
	  * Compute and store eucl radius of given vertex to achieve given 'aim'.
	  * Currently using 20 naive iterations.
	  * @param v int
	  * @param aim double
	  * @param int, 0 on error
	  */
	public int e_riffle_vert(int v,double aim) throws PackingException {
		int n=0;
		UtilPacket uP=new UtilPacket();

		double r=getRadius(v);
		if (!e_anglesum_overlap(v,r,uP)) return 0;
	    double curv=uP.value;
	    double diff=curv-aim;
	    while (n<20 && (diff>OKERR || diff<(-OKERR)) ) {
	    	if (!e_radcalc(v,r,aim,5,uP)) return 0; // something went wrong
	    	r=uP.value;
	    	if (!e_anglesum_overlap(v,r,uP)) return 0;
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
	 * Given spot in the redchain and e_indx pointing to one of its
	 * vertices, return Complex[2] with center for that circle and center 
	 * for same circle but from opposite side of a side-paired edge. 
	 * e_indx must point to vert for which redface (or a predecessor, 
	 * if blue) keeps center info. 

	 * (Key example: redface=edge and e_indx=edge_indx from SideDescription structure.)

	  Return 0 on failure. */

	  /** 
	   * Write circle packing p to an open 'file'. Return 0 on error.
	  If basic combinatoric data is to be included, use key "NODECOUNT:";
	  if only ancillary data is to be included, use "CHECKCOUNT:".
	  (Note: specialized routines may be necessary: eg., when aims are
	  specified, only the non-default ones are included.)

	  When 'append' is set, just add specified data (without NODECOUNT
	  or CHECKCOUNT) to existing file before 'END' (assume file is
	  open in "r+" read/write mode, positioned already, and will be closed 
	  by the calling routine; do not allow 0001 bit to be set in act).

	  Data to write specified in 'act' with bit-code as follows: 

	     basic combinatoric info:     
	       1: 00001     nodecount, a/b/c, flowers, packname
	     (else (if not append), CHECKCOUNT: nodecount)

	     default standard:
	       2: 00002     geometry
	       3: 00004     non-default inv_dist & aims
	       4: 00010     radii

	     optional:
	       5: 00020     centers
	       6: 00040     angle sums

	       7: 00100     vertex_map (if it exists)
	       8: 00200     lists of verts/faces/edges (non default)
	       9: 00400     colors (non default)

	       10: 01000    nonzero vertex plot_flags
	       11: 02000    xyz data
	       12: 04000    edge-pairing Mobius transformations

	       13: 010000   triangles
	       14: 020000   tiling data
           15: 040000   display flags
           
           16: 0100000  global list Verts/Faces/Edges (non default)
           17: 0200000  dual faces as a tiling
           18: 0400000  misc other: interactions
           
           19: 01000000 utility integers
           20: 02000000 utility double values
           21: 04000000 utility complex values
           
           22: 010000000 neutral (if nothing else)
           23: 020000000 schwarzians, if they exist
           
	  Note: standard write would be act= 020017, max would be act=031777.
	  @param file BufferedWriter
	  @param act int
	  @param append boolean
	  @return int
	  */
	public int writePack(BufferedWriter file, int act, boolean append)
			throws IOException {
		int flag;
		double angle;

		if (file == null)
			return 0;
		if (append)
			act &= 07776; // append mode, can't have 0001 bit
		
		// tiling data ONLY? If it exists, write and return.
		if (act== 020000 && tileData!=null && tileData.tileCount>0) { // tiling data

			file.write("TILECOUNT: "+tileData.tileCount+"\n\n");
			
        	//   The data rows are 't  n  v_0 v_1 ... v_(n-1)' where
        	//   t=tile number (1 to tileCount); n=number of vertices. 

			//   check for augmented: in this case, 3 additional vertices between corners
			boolean augmntd=true;
			for (int j=1;(j<=tileData.tileCount && augmntd);j++) {
				if (tileData.myTiles[j].augVert==null || tileData.myTiles[j].augVertCount<=0)
					augmntd=false;
			}
			if (augmntd)
				file.write("TILES: (augmented) "+tileData.tileCount+"\n");
			else
				file.write("TILES: "+tileData.tileCount+"\n");
			for (int j=1;j<=tileData.tileCount;j++) {
				Tile tile=tileData.myTiles[j];
				file.write("\n"+j+" "+tile.vertCount+" "+"   ");
                if (augmntd) {
                	for (int k=0;k<tile.augVertCount;k++)
                		file.write(" "+tile.augVert[k]);
                }
                else {
                	for (int k=0;k<tile.vertCount;k++)
                		file.write(" "+tile.vert[k]);
                }
			}
			file.write("\n");

			if (!append)
				file.write("END\n");
			file.flush();
			return tileData.tileCount;
		}

		// dual tiling as tile data; no other data
		if ((act | 0200000)==0200000) { //
			
			if (act!=0200000) {
				CirclePack.cpb.errMsg("dual tiling as tile data cannot be "+
						"saved with other data.");
				return 0;
			}

        	// The data rows are 't  n  v_0 v_1 ... v_(n-1)' where
        	//    t=tile number (1 to tileCount); n=number of vertices.
			
			// The tile corner indices are face indices from the packing,
			//    with vertex itself thrown in if a bdry vert.
			file.write("TILECOUNT: "+nodeCount+"\n\n");
			file.write("TILES: "+nodeCount+"\n\n");
			int tick=faceCount+1;
			for (int v=1;v<=nodeCount;v++) {
				int num=countFaces(v)+getBdryFlag(v);
				file.write("\n"+v+" "+num+" "+"   ");
				int[] faceFlower=getFaceFlower(v);
				for (int j = 0; j < countFaces(v); j++) 
					file.write(" "+faceFlower[j]);
				// convention for bdry half-tile: include new index for v itself
				if (isBdry(v)) 
					file.write(" "+tick++);
			}			
			file.write("\nEND\n");
			file.flush();
			return nodeCount;
		}

		// lead info
		if ((act & 0001) == 0001) { // new pack basic comb info
			file.write("NODECOUNT:  " + nodeCount + "\n");
		} 
		// one of "neutral" data types only
		else if (act == 01000000 || act == 04000000 ) {
			file.write("NEUTRAL: \n");
		}
		else {
			file.write("CHECKCOUNT: " + nodeCount + "\n"); // partial data
		}
		
		if ((act & 0002) == 0002 || (act & 0010) == 0010
				|| (act & 0020) == 0020) {
			// geometry (needed if radii or centers given)
			file.write("GEOMETRY: ");
			if (hes < 0)
				file.write("hyperbolic\n");
			else if (hes > 0)
				file.write("spherical\n");
			else
				file.write("euclidean\n");
		}
		if ((act & 0001) == 0001) {
			file.write("ALPHA/BETA/GAMMA:  " + alpha + " " + beta + " " + gamma
					+ "\n");
			if (fileName.length() > 0)
				file.write("PACKNAME: " + fileName + "\n");
			if (packDCEL==null)
				file.write("FLOWERS: ");
			else
				file.write("BOUQUET: ");
			for (int n = 1; n <= nodeCount; n++) {
				file.write("\n" + n + " " + countFaces(n) + "  ");
				int[] gfl=getFlower(n);
				for (int i = 0; i <= countFaces(n); i++)
					file.write(" "+gfl[i]);
			}
			file.write("\n\n");
		}

		else if (act == 020000000) { // real schwarzians
			String hitstr=new String("SCHWARZIANS:\n");
			boolean hitflag=false;
			double schw;
			int k;
			if (packDCEL!=null) {
				for (int v = 1; v <= packDCEL.vertCount; v++) {
					HalfLink spokes=packDCEL.vertices[v].getEdgeFlower();
					Iterator<HalfEdge> sis=spokes.iterator();
					while (sis.hasNext()) {
						HalfEdge he=sis.next();
						int kk=he.twin.origin.vertIndx;
						schw=he.getSchwarzain();
						if (schw!=1.0 && v < kk) {
							if (!hitflag) {
								hitflag=true;
								file.write(hitstr);
							}
							file.write("\n" + v + " " + kk + "  "
									+ String.format("%.10e",schw));
						}
					}
				}
				file.write("\n");
			}
			
			else {
				// first check if data is all available
				boolean okay=haveSchwarzians();
				if (okay) {
					file.write("SCHWARZIANS:\n");
					for (int v=1;v<=nodeCount;v++) {
						file.write(v+"    "); // indicate vertex
						for (int j=0;j<=countFaces(v);j++) { // then list schwarzian for each edge 
							file.write(String.format("%.12e",kData[v].schwarzian[j])+" ");
						}
						file.write("\n");
					}
				}
				file.write("\n");
			}
		}

		if ((act & 0004) == 0004) {// inv_dist? aims? (non-default only)
			String hitstr=new String("INV_DISTANCES:\n");
			boolean hitflag=false;
			double ang;
			int k;
			if (packDCEL!=null) {
				for (int i = 1; i <= packDCEL.vertCount; i++) {
					int[] gfl=getFlower(i);
					for (int j = 0; j < gfl.length; j++) {
						if (i < (k = gfl[j])
								&& Math.abs((ang = getInvDist(i,gfl[j])) - 1.0) > OKERR) {
							if (!hitflag) {
								hitflag=true;
								file.write(hitstr);
							}
							file.write("\n" + i + " " + k + "  "
									+ String.format("%.6e", ang));
						}
					}
				}
			}
			else if (overlapStatus) {
				for (int i = 1; i <= nodeCount; i++) {
					int[] gfl=getFlower(i);
					for (int j = 0; j < gfl.length; j++) {
						if (i < (k = gfl[j])
								&& Math.abs((ang = getInvDist(i,gfl[j])) - 1.0) > OKERR) {
							if (!hitflag) {
								hitflag=true;
								file.write(hitstr);
							}
							file.write("\n" + i + " " + k + "  "
									+ String.format("%.6e", ang));
						}
					}
				}
			}
			if (hitflag) {
				file.write("\n  (done)\n\n");
			}
			
			// also, non-default aims
			hitflag=false;
			for (int i = 1; i <= nodeCount; i++) {
				double aim=getAim(i);
				if ((isBdry(i) && aim >= 0.0)
						|| (!isBdry(i) && (aim < (2.0 * Math.PI - OKERR) || 
								aim > (2.0 * Math.PI + OKERR)))) {
					if (!hitflag) {
						hitflag=true;
						file.write("ANGLE_AIMS:\n");
					}
					file.write(" " + i + "  "
							+ String.format("%.6e\n",aim));
				}
			}
			if (hitflag) {
				file.write("\n  (done)\n\n");
			}
		}
		if ((act & 0010) == 0010) { // radii? use appropriate number of digits
			int digits = 1;
			while ((Math.pow(10.0, (double) digits)) * TOLER < 0.1
					&& digits < MAX_ACCUR)
				digits++;
			file.write("RADII: \n");
			for (int i = 1; i <= nodeCount; i++) {
				String fms = new String("%." + digits + "e");
				file.write(String.format(fms, this.getActualRadius(i)) + "  ");
				if ((i % 4) == 0)
					file.write("\n");
				else
					file.write(" ");
			}
			file.write("\n\n");
		}
		if ((act & 0020) == 0020) { // centers? (often easier to recompute)
			file.write("CENTERS:\n");
			for (int i = 1; i <= nodeCount; i++) {
				Complex ztr=getCenter(i);
				file.write(String.format("%.10e", ztr.x) + " "
						+ String.format("%.10e", ztr.y) + "  ");
				if ((i % 2) == 0)
					file.write("\n");
			}
			file.write("\n\n");
		}
		if ((act & 020000) == 020000 && tileData!=null && tileData.tileCount>0) { // tiling data

        	//   The data rows are 't  n   v_0 v_1 ... v_(n-1)' where
        	//   t=tile number (1 to tileCount); n=number of vertices.
			
			//   check for augmented: in this case, 3 additional vertices between corners
			boolean augmntd=true;
			for (int j=1;(j<=tileData.tileCount && augmntd);j++) {
				if (tileData.myTiles[j].augVert==null || tileData.myTiles[j].augVertCount<=0)
					augmntd=false;
			}
			if (augmntd)
				file.write("TILES: (augmented) "+tileData.tileCount+"\n");
			else
				file.write("TILES: "+tileData.tileCount+"\n");
			for (int j=1;j<=tileData.tileCount;j++) {
				Tile tile=tileData.myTiles[j];
				file.write("\n"+j+" "+tile.vertCount+"   ");
				if (augmntd) {
					for (int kk=0;kk<tile.augVertCount;kk++)
						file.write(" "+tile.augVert[kk]);
				}
				else {
					for (int kk=0;kk<tile.vertCount;kk++)
						file.write(" "+tile.vert[kk]);
				}
			}
			file.write("\n\n");
		}
		if ((act & 0040) == 0040) { // angle sums? (often easier to recompute)
			file.write("ANGLESUMS: \n");
			for (int i = 1; i <= nodeCount; i++) {
				file.write(" " + String.format("%.10e", getCurv(i)) + "\t");
				if ((i % 5) == 0)
					file.write("\n");
			}
			file.write("\n\n");
		}
		if ((act & 010000) == 010000) { // triangles (triples of verts)
			file.write("FACE_TRIPLES: \n");
			for (int i = 1; i <= faceCount; i++) {
				int[] fverts=getFaceVerts(i);
				file.write(" ");
				for (int j=0;j<fverts.length;j++)
					file.write(fverts[0] + "  ");
				file.write("\n");
			}
			file.write("(done)\n\n");
		}

		if ((act & 01000) == 01000) { // nonpositive plot_flags
			flag = 0;
			for (int i = 1; (i <= nodeCount && flag == 0); i++)
				if (kData[i].plotFlag <= 0)
					flag++;
			if (flag > 0) { // got some nondefault circle plot_flags
				file.write("CIRCLE_PLOT_FLAGS: \n");
				int j = 0;
				for (int i = 1; i <= nodeCount; i++)
					if (kData[i].plotFlag <= 0) {
						file.write(i + " \t" + kData[i].plotFlag + " ");
						j++;
						if ((j % 5) == 0)
							file.write("\n");
					}
				file.write("\n (done)\n\n");
			}
			flag = 0;
			for (int i = 1; (i <= faceCount && flag == 0); i++)
				if (faces[i].plotFlag <= 0)
					flag++;
			if (flag > 0) { // got some nondefault face plot_flags
				file.write("FACE_PLOT_FLAGS: \n");
				int j = 0;
				for (int i = 1; i <= faceCount; i++)
					if (faces[i].plotFlag <= 0) {
						file.write(" " + faces[i].vert[0] + " "
								+ faces[i].vert[1] + " " + faces[i].vert[2]
								+ "   " + faces[i].plotFlag);
						j++;
						if ((j % 3) == 0)
							file.write("\n");
					}
				file.write("\n (done)\n\n");
			}
		}
		if ((act & 02000) == 02000 && xyzpoint != null) { // xyz data
			file.write("POINTS: " + nodeCount + "\n");
			for (int i = 1; i <= nodeCount; i++) {
				file.write(String.format("%.10e", xyzpoint[i].x) + " "
						+ String.format("%.10e", xyzpoint[i].y) + " "
						+ String.format("%.10e", xyzpoint[i].z) + "\n");
			}
			file.write("\n");
		}
		
		if ((act & 01000000) == 01000000 && utilIntegers!=null && utilIntegers.size()>0) { // utility integer values
			file.write("INTEGERS: "+utilIntegers.size()+"\n");
			for (int i=0;i<utilIntegers.size();i++) {
				file.write(utilIntegers.get(i).toString()+"\n");
			}
			file.write("\n");
		}
			
		if ((act & 02000000) == 02000000 && utilDoubles!=null && utilDoubles.size()>0) { // utility double values
			file.write("DOUBLES: "+utilDoubles.size()+"\n");
			for (int i=0;i<utilDoubles.size();i++) {
				file.write(String.format("%.12e", (double)utilDoubles.get(i))+"\n");
			}
			file.write("\n");
		}
			
		if ((act & 04000000) == 04000000 && utilComplexes!=null && utilComplexes.size()>0) { // utility complex values
			file.write("COMPLEXES: "+utilComplexes.size()+"\n");
			for (int i=0;i<utilComplexes.size();i++) {
				file.write(String.format("%.12e", (double)(utilComplexes.get(i).x))+" "+
						String.format("%.12e", (double)(utilComplexes.get(i).y))+"\n");
			}
			file.write("\n");
		}

		if ((act & 04000) == 04000) { // edge-pairing Mobius
			NodeLink pairIndices=getPairIndices();
			if (pairIndices!=null) {
				Iterator<Integer> pis=pairIndices.iterator();
				while (pis.hasNext()) {
					int e=pis.next();
					file.write("EDGE_PAIRING MOBIUS: "+e+"\n\n");
					file.write(getSideMob(e).mob2String().toString()+"\n");
				}
			}
		}

		if ((act & 0400) == 0400) { // any non default colors?
			
			// check vertex colors
			int colorflag = 0;
			Color vcol;
			for (int i = 1; i <= nodeCount && colorflag == 0; i++) {
				vcol=getCircleColor(i);
				if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0)  
					colorflag++;
			}
			if (colorflag > 0) { // found some non-default circle colors
				file.write("C_COLORS:\n");
				for (int i = 1; i <= nodeCount; i++) { // one vertex per line
					vcol=getFaceColor(i);
					if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0) { 
						file.write(" " + i + " " + vcol.getRed() + " "
								+ vcol.getGreen() + " "
								+ vcol.getBlue() + "\n");
					}
				}
				file.write("\n  (done)\n\n");
			}
			
			// check face colors
			colorflag = 0;
			for (int i = 1; i <= faceCount && colorflag == 0; i++) {
				vcol=getFaceColor(i);
				if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0)  
					colorflag++;
			}
			if (colorflag > 0) { // found some non-default colors
				file.write("T_COLORS:\n"); // T_COLORS are for faces; superceded TRI_COLORS
				for (int i = 1; i <= faceCount; i++) { // one face per line
					vcol=getFaceColor(i);
					if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0) { 
						file.write(" " + faces[i].vert[0] + " "
								+ faces[i].vert[1] + " " + faces[i].vert[2]+"   "+
								vcol.getRed() + " "+vcol.getGreen()+" "+vcol.getBlue()+"\n");
					}
				}
				file.write("\n (done)\n\n");
			}

			// check tile colors
			if (tileData!=null) {
				colorflag = 0;
				for (int i = 1; i <= tileData.tileCount && colorflag == 0; i++) {
					vcol=tileData.myTiles[i].color;
					if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0)  
						colorflag++;
				}
				if (colorflag > 0) { // found some non-default colors
					file.write("TILE_COLORS:\n"); 
					for (int i = 1; i <= tileData.tileCount; i++) { // one face per line
						Tile tile=tileData.myTiles[i];
						vcol=tile.color;
						if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0) { 
							file.write(" " + tile.vertCount+ " ");
							for (int ik=0;ik<tileData.myTiles[i].vertCount;ik++)
								file.write(tileData.myTiles[i].vert[ik]+" ");
							file.write("  "+vcol.getRed() + " "+vcol.getGreen()+" "+vcol.getBlue()+"\n");
						}
					}
				}
				file.write("\n (done)\n\n");
			}
		} // done with default colors
		if ((act & 0200) == 0200) { // print non empgy lists?
									// (vlist/flist/elist)
			if (vlist != null && vlist.size() != 0) {
				file.write("VERT_LIST:\n");
				Iterator<Integer> vli = vlist.iterator();
				while (vli.hasNext()) {
					file.write(" " + (Integer) vli.next() + "\n");
				}
				file.write(" (done)\n\n");
			}
			if (flist != null && flist.size() > 0) {
				file.write("FACE_TRIPLES:\n");
				Iterator<Integer> fli = flist.iterator();
				while (fli.hasNext()) {
					int fl = (Integer) fli.next();
					file.write(" " + faces[fl].vert[0] + " "
							+ faces[fl].vert[1] + " " + faces[fl].vert[2]
							+ "\n");
				}
				file.write(" (done)\n\n");
			}
			if (elist != null && elist.size() != 0) {
				file.write("EDGE_LIST:\n");
				Iterator<EdgeSimple> eli = elist.iterator();
				while (eli.hasNext()) {
					EdgeSimple el = (EdgeSimple) eli.next();
					file.write(" " + el.v + " " + el.w + "\n");
				}
				file.write(" (done)\n\n");
			}
		}
		if ((act & 0100) == 0100 && vertexMap != null) { // vertex_map
			file.write("VERTEX_MAP:\n");
			Iterator<EdgeSimple> eli = vertexMap.iterator();
			while (eli.hasNext()) {
				EdgeSimple el = (EdgeSimple) eli.next();
				file.write(el.v + "  " + el.w + "\n");
			}
			file.write("   (done)\n\n");
		}
		if ((act & 040000) == 040000) { // display flags
			file.write("DISP_FLAGS:\n");
			file.write("Disp "+cpScreen.dispOptions.toString()+"\n");
		}		
		if (!append)
			file.write("END\n");
		file.flush();
		return 1;
	}

	
	/**
	 * Write *.dcel output; this format is tentative as of 4/2017.
	 * @param fp BufferedWriter
	 * @param dual boolean, if yes, write the dual dcel
	 * @return int
	 */
	public int writeDCEL(BufferedWriter fp,boolean dual) 
			throws IOException {
		
		if (fp==null)
			throw new IOException("BufferedWriter was not set");
		if (packDCEL==null || packDCEL.vertCount<=0) {
			
			// create the DCEL structure
			PackDCEL pdc=CombDCEL.getRawDCEL(this);
			packDCEL=CombDCEL.extractDCEL(pdc,null,pdc.alpha);

		}
		if (dual) {
			PackDCEL dualdcel=packDCEL.createDual(false);
			return dualdcel.writeDCEL(fp);
		}
		return packDCEL.writeDCEL(fp);
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
	 * Write an ascii file with compressed data for a portion of the packing
	 * using the PackLite structure (a simple listing of numerical values; 
	 * see 'PackLite'.)
	 * 
	 * Three tasks:
	 * 1. Cut out the connected component of 'intV' containing 'alp' (default 
	 *    to interior vertices and alp=alpha) and find the cclw chain of bdry
	 *    edges around it (there may be more than one and/or isolated verts)
	 * 
	 * 2. Re-index so interiors of 'intV' are listed first, then bdry in cclw
	 *    order, starting with 'gam', and get various counts and arrays of indices. 
	 *    Note that which verts are variable, which fixed, etc. are independent of
	 *    the new indices. E.g., bdry verts may be adjustable if packing a
	 *    rectangle. (Numbering is tailored for use in Orick's algorithm.)
	 *    
	 * 3. Write out the PackLite data for just these int/bdry vertices.
	 * 
	 * "act": (parallel to 'writePack') 
	 *      3: 00004     non-default inv_dist & aims
	 *      4: 00010     radii
	 *      5: 00020     centers
	 *      18: 0400000  misc: add ideal vert for each interior bdry hole

	 * @param file
	 * @param act int, bit encoded choices of data to include
	 * @param intV NodeLink, labeled as "interior" (default to actual interior)
	 * @param alp int, "alpha" vert (default to actual alpha)
	 * @param gam int, "gamma" bdry vert (default to actual gamma) 
	 * @return int, vCount or 0 on error
	 * @throws IOException
	 */
	public int writeLite(BufferedWriter file,int act,NodeLink intV,int alp,int gam)
			throws IOException {
		
		if (file==null)
			throw new IOException("BufferedWriter was not set");
		
		// create a 'PackLite'.
		boolean addIdeals=((act & 0400000)==0400000);
		PackLite pL=new PackLite(this);
		pL.createFrom(this,addIdeals,intV,alp,gam);

		// task 3: --------------- write the packData file ----------------
		
		// **** magic number identifying this as 'Lite' 
		file.write("1234321\n");
		
		// **** preliminary 20 integers
		file.write(nodeCount+"\n"); // nodeCount as check
		file.write(hes+"\n"); // geometry
		file.write(pL.vertCount+"\n");
		file.write(pL.intVertCount+"\n"); 
		file.write(pL.vCount+"\n"); 
		file.write(pL.flowerCount+"\n");
		
		// are we asked fo non-default aims, inv distances?
		if ((act & 00004)==00004) { 
			file.write(pL.aimCount+"\n"); 
			file.write(pL.invDistCount+"\n");
		}
		else file.write("0\n0\n");
		
		if ((act & 00010)==00010)
			file.write("1\n"); // include radii
		else
			file.write("0\n"); // no radii
		if ((act & 00020)==00020)
			file.write("1\n"); // include centers
		else
			file.write("0\n"); // no centers
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		file.write("0\n"); // future use
		
		// **** variable vertices (local indices)
		for (int i=0;i<pL.vCount;i++) {
			file.write(pL.varIndices[i]+"\n");
		}
			
		// **** all flowers n num p0 p1 .... pnum (local indices)
		int tick=0;
		int v=0;
		while (tick<pL.flowerCount && v<pL.vertCount) {
			v=pL.flowerHeads[tick++];
			StringBuilder strbld=new StringBuilder(v+" ");
			int num=pL.flowerHeads[tick++];
			strbld.append(num+" ");
			for (int j=0;j<=num;j++)
				strbld.append(pL.flowerHeads[tick++]+" ");
			strbld.append("\n");
			file.write(strbld.toString());
		}

		// **** original indices
		for (int n=1;n<=pL.vertCount;n++) {
			file.write(pL.v2parent[n]+"\n");
		}
		
		// **** radii
		if (((act & 00010)==00010) && pL.radii!=null) {
			for (int n=1;n<=pL.vertCount;n++) {
				file.write(pL.radii[n]+"\n");
			}
		}
		
		// **** centers
		if (((act & 00020)==00020) && pL.centers!=null) {
			for (int n=1;n<=pL.vertCount;n++) 
				file.write(pL.centers[n].x+" "+pL.centers[n].y+"\n");
		}
		
		if ((act & 00004)==00004) { // non-default aims, inv distances?

			if (pL.aimCount>0 && pL.aimIndices!=null && pL.aims!=null) {
				// **** aimIndices (local indices)
				for (int n=1;n<=pL.vertCount;n++) 
					if (pL.aims[n]!=0.0) 
						file.write(n+"\n");
		
				// **** aims
				for (int n=1;n<=pL.vertCount;n++) 
					if (pL.aims[n]!=0.0)
						file.write(pL.aims[n]+"\n");
			}

			// **** edges with non-default inv distances (local indices, v<w only)
			if (pL.invDistCount>0 && pL.invDistLink!=null && pL.invDistances!=null) {
				Iterator<EdgeSimple> el=pL.invDistLink.iterator();
				while (el.hasNext()) {
					EdgeSimple edge=el.next();
					file.write(edge.v+" "+edge.w+" ");
				}
		
				// **** corresponding inv distances, indexed from 0
				for (int i=0;i<pL.invDistances.length;i++) {
					if (pL.invDistances[i]!=0.0) 
						file.write(pL.invDistances[i]+" ");
				}
			}
		}
		
		file.flush();
		return pL.vCount;
	}
	
	/**
	 * Return side pairing mobius with given label (should be
	 * just one letter)
	 * @param moniker String
	 * @return Mobius
	 */
	public Mobius namedSidePair(String moniker) {
		if (sidePairs==null || sidePairs.size()==0)
			return null;
		
		// side pairings indicated with just one letter
		String label= moniker.trim();
		if (label.length()!=1)
			return null;
        
		Iterator<SideDescription> sides=sidePairs.iterator();
		SideDescription ep=null;
		String tmpLabel=new String(label);
		// Upper case (e.g. 'A') must be changed to double lower ('aa').
		char h=tmpLabel.charAt(0);
		if (h>='A' && h<='Z') {
			h=(char)('a'+h-'A');
			tmpLabel=new String(String.valueOf(h)+String.valueOf(h));
		}
		while (sides.hasNext()) {
			ep=(SideDescription)sides.next();
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
			if (packDCEL!=null) {
				if (hes < 0) { // hyp
					d_HypPacker h_packer=new d_HypPacker(this,passes);
					if (!oldRel) {
						return h_packer.genericRePack(passes);
					}
					int ans=h_packer.d_oldReliable(1000); 
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
					d_EuclPacker e_packer=new d_EuclPacker(this,passes);
					if (!oldRel) {
						return e_packer.genericRePack(passes);
					}
					int ans=e_packer.d_oldReliable(1000); // TODO: specify in call
					if (ans>0) {
						e_packer.reapResults();
//						CirclePack.cpb.msg("Did DCEL eucl repack, count="+ans);
						return ans;
					}
					else if (ans<0)
						throw new PackingException("dcel eucl repack failure");
					return 1;
				}
			}
			
			// else, traditional call
			if (hes < 0) { // hyp
				if (!oldRel) {
					HypPacker hyppack = new HypPacker(this, useC);
					if (hyppack.status > 0)
						count = hyppack.genericRePack(passes);
				} else
					count = HypPacker.oldReliable(this, passes);
			} else if (hes == 0) { // eucl
				if (!oldRel) {
					EuclPacker euclpack = new EuclPacker(this, useC);
					if (euclpack.status > 0)
						count = euclpack.genericRePack(passes);
				} else
					count = EuclPacker.oldReliable(this, passes);
			} else { // sph
				if (this.intrinsicGeom <= 0) {
					CirclePack.cpb
							.errMsg("No packing algorithm in "
									+ "spherical geometry (yet) when the complex is not "
									+ "a sphere");
					return -1;
				}
				// or else, max pack
				SphPacker sphpack = new SphPacker(this, useC);
				count = sphpack.maxPack(0, passes);
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
	 * Note: does NOT adjust 'CPScreen' geometry.
	 */ 
	public int geom_to_e() {
		RedList trace;
		boolean keepon = true;
		CircleSimple sc;

		if (hes == 0)
			return 1; // eucl already
		else if (hes < 0) { // hyp
			for (int v = 1; v <= nodeCount; v++) {
				sc = HyperbolicMath.h_to_e_data(getCenter(v), getRadius(v));
				setCenter(v,sc.center);
				setRadius(v,sc.rad);
			}
			if (packDCEL==null && (trace = redChain) != null) {
				try {
					keepon = true;
					while (trace != redChain || keepon) {
						keepon = false;
						sc = HyperbolicMath
								.h_to_e_data(trace.center, trace.rad);
						trace.center = sc.center;
						trace.rad = sc.rad;
						trace = trace.next;
					}
				} catch (Exception ex) {
				}
			}
		} else if (hes > 0) { // sph
			// Note: sc.flag==-1 means disc is outside of circle
			for (int v = 1; v <= nodeCount; v++) {
				sc = SphericalMath.s_to_e_data(getCenter(v), getRadius(v));
				setCenter(v,sc.center);
				if (sc.flag==-1) 
					sc.rad *=-1.0; // this may not signal what we want
				setRadius(v,sc.rad);
			}
			if (packDCEL==null && (trace = redChain) != null) {
				try {
					keepon = true;
					while (trace != redChain || keepon) {
						keepon = false;
						sc = SphericalMath.s_to_e_data(trace.center, trace.rad);
						trace.center = sc.center;
						trace.rad = sc.rad;
						if (sc.flag==-1)
							trace.rad *=-1.0; // this may not signal what we want
						trace = trace.next;
					}
				} catch (Exception ex) {
				}
			}
		}
		hes = 0;
		return 1;
	}

	/** 
	 * Converts centers/radii to euclidean first, then 
	 * scales packing to live in the unit disc and converts
	 * to hyp center and s-radii. Sets hes=-1.
	 * Note: does NOT adjust 'CPScreen' geometry. 
	 * @return 1 on success (or if already hyp).
	 */
	public int geom_to_h() {
		RedList trace;
		boolean keepon;
		CircleSimple sc;

		if (hes < 0)
			return 1; // hyp already
		else if (hes == 0) { // eucl
			double mx = getCenter(alpha).abs() + getRadius(alpha);
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
				sc = HyperbolicMath.e_to_h_data(getCenter(v),getRadius(v));
				setCenter(v,sc.center);
				setRadius(v,sc.rad);
			}
			if (packDCEL==null && (trace = redChain) != null) { // adjust redface data too.
				try {
					keepon = true;
					while (trace != redChain || keepon) {
						keepon = false;
						trace.center = trace.center.divide(mx);
						trace.rad /= mx;
						sc = HyperbolicMath.e_to_h_data(trace.center,
								rData[trace.vIndex].rad);
						trace.center = sc.center;
						trace.rad = sc.rad;
						trace = trace.next;
					}
				} catch (Exception ex) {
				}
			}
		} 
		else if (hes > 0) { // pass sph through eucl
			geom_to_e();
			geom_to_h();
		}
		hes = -1;
		return 1;
	}

	/** 
	 * Converts packing to spherical, with alpha 
	 * vertex at north pole. (Note: our stereographic 
	 * projection puts 0 at the NORTH pole.)
	 * Note: does NOT adjust 'CPScreen' geometry.
	 * @return 1 
	 */
	public int geom_to_s() {
		boolean keepon;
		RedList trace;
		CircleSimple sc;

		if (hes > 0)
			return 1; // sph already
		if (hes < 0) { // from hyp
			for (int v = 1; v <= nodeCount; v++) {
				sc = HyperbolicMath.h_to_e_data(getCenter(v),getRadius(v));
				sc = SphericalMath.e_to_s_data(sc.center, sc.rad);
				setCenter(v,sc.center);
				setRadius(v,sc.rad);
			}
			if (packDCEL==null && (trace = redChain) != null) {
				try {
					keepon = true;
					while (trace != redChain || keepon) {
						keepon = false;
						sc = HyperbolicMath
								.h_to_e_data(trace.center, trace.rad);
						sc = SphericalMath.e_to_s_data(sc.center, sc.rad);
						trace.center = sc.center;
						trace.rad = sc.rad;
						trace = trace.next;
					}
				} catch (Exception ex) {
				}
			}
		} else { // from eucl
			for (int v = 1; v <= nodeCount; v++) {
				sc = SphericalMath.e_to_s_data(getCenter(v),getRadius(v));
				setCenter(v,new Complex(sc.center));
				setRadius(v,sc.rad);
			}
			if (packDCEL==null && (trace = redChain) != null) {
				try {
					keepon = true;
					while (trace != redChain || keepon) {
						keepon = false;
						sc = SphericalMath.e_to_s_data(trace.center, trace.rad);
						trace.center = sc.center;
						trace.rad = sc.rad;
						trace = trace.next;
					}
				} catch (Exception ex) {
				}
			}
		}
		hes = 1;
		return 1;
	}

	/** 
	 * Checks that v is boundary vertex, then adds circle
	 * nghb'ing v and its clockwise bdry neighbor. Local data is
	 * updated, but calling routine must update for colors,
	 * radii, centers, etc.
	 * @param v int
	 * @return int, 0 on error.
	 */
	public int add_vert(int v) throws CombException {
		if (v < 1 || v > nodeCount || !isBdry(v))
			return 0;
		int node=nodeCount+1; // new index
		if (packDCEL!=null)
			node=packDCEL.vertCount+1;
		if (node > (sizeLimit)
				&& alloc_pack_space(node, true) == 0) 
			throw new CombException("Pack space allocation failure");
		if (getRadius(v) <= 0) { // avoid infinite hyp rad
			setRadius(v,.1);
		}
		int v2 = getLastPetal(v); // upstream nghb
		
		if (packDCEL!=null) {
			Vertex vert=RawDCEL.addVert_raw(packDCEL,v);
			if (vert==null)
				throw new CombException("failed 'add_vert'");
			setRadius(vert.vertIndx,getRadius(vert.vutil));
			packDCEL.fixDCEL_raw(this);
		}
		 
		// traditional packing
		else {
			int n;
			int[] newflower;
			double[] newoverlaps;

			// add to flower of v
			n = countFaces(v);
			newflower = new int[n + 2];
			for (int i = 0; i <= n; i++)
				newflower[i] = kData[v].flower[i];
			newflower[n + 1] = node;
			kData[v].flower = newflower;
			if (kData[v].invDist != null) {
				newoverlaps = new double[n + 2];
				for (int i = 0; i <= n; i++)
					newoverlaps[i] = getInvDist(v, kData[v].flower[i]);
				newoverlaps[n + 1] = 1.0;
				kData[v].invDist = newoverlaps;
			}
			kData[v].num = n + 1;
			// add to flower of v2
			n = countFaces(v2);
			newflower = new int[n + 2];
			for (int i = 1; i <= n + 1; i++)
				newflower[i] = kData[v2].flower[i - 1];
			newflower[0] = node;
			kData[v2].flower = newflower;
			if (kData[v2].invDist != null) {
				newoverlaps = new double[n + 2];
				for (int i = 1; i <= n + 1; i++)
					newoverlaps[i] = getInvDist(v2, kData[v2].flower[i - 1]);
				newoverlaps[0] = 1.0;
				kData[v2].invDist = newoverlaps;
			}
			kData[v2].num = n + 1;
			// add new node
			nodeCount++;
			kData[node] = new KData();
			kData[node].num = 1;
			kData[node].flower = new int[2];
			kData[node].flower[0] = v;
			kData[node].flower[1] = v2;
			if (overlapStatus) {
				kData[node].invDist = new double[2];
				set_single_invDist(node, kData[node].flower[0], 1.0);
				set_single_invDist(node, kData[node].flower[1], 1.0);
			}
			setBdryFlag(node, 1);
			kData[node].plotFlag = 1;
			setVertMark(node, 0);
			rData[node] = new RData();
		}
		
		setCircleColor(node,ColorUtil.getFGColor());
		setRadius(node,getRadius(v));
		CircleSimple sc = CommonMath.comp_any_center(getCenter(v),
				getCenter(v2), getRadius(v),getRadius(v2), getRadius(node),
				hes);
		sc.save(this, node);

		// fix affected curvatures
		UtilPacket utilp = new UtilPacket();
		CommonMath.get_anglesum(this,node, getRadius(node), utilp);
		setCurv(node,utilp.value);
		CommonMath.get_anglesum(this,v, getRadius(v), utilp);
		setCurv(v,utilp.value);
		CommonMath.get_anglesum(this,v2, getRadius(v2), utilp);
		setCurv(v2,utilp.value);

		// set defualt aims
		setAim(node,-1.0);
		setAim(v,-1.0);
		setAim(v2,-1.0);
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
		
		if (packDCEL!=null) {
			if (getRadius(v1) <= 0) // avoid infinity hyp rad
				setRadius(v1, 0.1);
			int ans=RawDCEL.enfold_raw(packDCEL,v1);
			if (ans<=0)
				throw new CombException("dcel enfold failed in 'enfold'");
			packDCEL.fixDCEL_raw(this);
			return 1;
		}

		// traditional packing
			int n, v2, v3;
			int[] newflower;
			double[] newoverlaps;

			n = countFaces(v1);
			v2 = getFirstPetal(v1); // cclw nghb
			v3 = getLastPetal(v1); // clw nghb

			// adjust flower of v1
			newflower = new int[n + 2];
			int[] flower = getFlower(v1);
			for (int i = 0; i <= n; i++)
				newflower[i] = flower[i];
			newflower[n + 1] = v2;
			kData[v1].flower = newflower;
			if (kData[v1].invDist != null) {
				newoverlaps = new double[n + 2];
				for (int i = 0; i <= n; i++)
					newoverlaps[i] = getInvDist(v1, kData[v1].flower[i]);
				newoverlaps[n + 1] = getInvDist(v1, kData[v1].flower[0]);
				kData[v1].invDist = newoverlaps;
			}
			setBdryFlag(v1, 0);
			setAim(v1, 2.0 * Math.PI);
			kData[v1].num++;
			if (getRadius(v1) <= 0) { // avoid infinity rad
				setRadius(v1, 0.1);
			}

			// add to flower of v2
			n = countFaces(v2);
			newflower = new int[n + 2];
			flower = getFlower(v2);
			for (int i = 0; i <= n; i++)
				newflower[i] = flower[i];
			newflower[n + 1] = v3;
			kData[v2].flower = newflower;
			kData[v2].num++;
			if (kData[v2].invDist != null) {
				newoverlaps = new double[n + 2];
				for (int i = 0; i <= n; i++)
					newoverlaps[i] = getInvDist(v2, kData[v2].flower[i]);
				newoverlaps[n + 1] = 1.0;
				kData[v2].invDist = newoverlaps;
			}
			if (kData[v2].flower[0] == v3) {
				setBdryFlag(v2, 0);
				if (getRadius(v2) <= 0) { // avoid infinity
					setRadius(v2, 0.1);
				}
				if (kData[v2].invDist != null)
					set_single_invDist(v2, kData[v2].flower[n + 1], getInvDist(v2, kData[v2].flower[0]));
				setAim(v2, 2.0 * Math.PI);
			}

			// add to flower of v3
			n = countFaces(v3);
			newflower = new int[n + 2];
			for (int i = 1; i <= n + 1; i++)
				newflower[i] = kData[v3].flower[i - 1];
			newflower[0] = v2;
			kData[v3].flower = newflower;
			if (kData[v3].invDist != null) {
				newoverlaps = new double[n + 2];
				for (int i = 1; i <= n + 1; i++)
					newoverlaps[i] = getInvDist(v3, kData[v3].flower[i - 1]);
				newoverlaps[0] = 1.0;
				kData[v3].invDist = newoverlaps;
			}
			kData[v3].num++;
			if (kData[v3].flower[countFaces(v3)] == v2) {
				setBdryFlag(v3, 0);
				if (getRadius(v3) <= 0) { // avoid infinity
					setRadius(v3, 0.1);
				}
				if (kData[v3].invDist != null)
					set_single_invDist(v3, kData[v3].flower[0], getInvDist(v3, kData[v3].flower[n + 1]));
				setAim(v2, 2.0 * Math.PI);
			}

		// calling routine should update pack after returning
		return 1;
	}

	/** 
	 * Add a layer of nodes to bdry segment from vertex v1 to v2.
	 * Three modes:
	 * 
	 * TENT: add one-on-one layer, a new bdry vert for 
	 *   each edge between v1 and v2. Unless v1==v2, 
	 *   v1 and v2 remain as bdry vertices.
	 *   
	 * DEGREE: add nghb's to make vertices from v1 to v2,
	 *   inclusive, interior with degree d. However, no edge
	 *   should connect existing bdry vertices. If v1==v2 or
	 *   v1 is nghb of v2, do whole bdry component.
	 *   
	 * DUPLICATE: attach "square" face with bary center 
	 *   to each edge between v1 and v2. Unless v1==v2, 
	 *   v1 and v2 remain on bdry.
	 *   
	 * Calling routine updates combinatorics.
	 * @param mode int, how to add: 0=TENT, 1=DEGREE, 2=DUPLICATE
	 * @param degree int
	 * @param v1 int, start bdry vert
	 * @param v2 int, end bdry vert
	 * @return int, count of added vertices 
	 */
	public int add_layer(int mode, int degree, int v1, int v2) {
		int count = 0;

		// modes
		int TENT = 0;
		int DEGREE = 1;
		int DUPLICATE = 2;
		
		int edge_count=verts_share_bdry(v1, v2);
		if (v1 < 1 || v1 > nodeCount || v2 < 1 || v2 > nodeCount
				|| !isBdry(v1) || edge_count==0) {
			CirclePack.cpb
					.errMsg("add_layer: vertices must be on same boundary component");
			return 0;
		}

		if (mode == TENT) {
			// first new circle
			int vert = getFirstPetal(v1);
			count += add_vert(vert);

			// cycle until reaching v2
			while (vert != v2 && isBdry(vert)) {
				count += add_vert(vert);
				enfold(vert);
				vert = getFirstPetal(vert);
			}
			if (vert != v2) { //
				throw new CombException(
						"add_layer: encountered non-boundary vertex " + vert);
			}
			if (vert == v1)
				enfold(vert); // full bdry
			if (packDCEL==null) {
				try {
					setBdryFlags();
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("Some problem with add_layer.");
					return 0;
				}
			}
			return count;
		}

		// add to get degree d; every circle, v1 to v2 inclusive,
		// must get at least one new neighbor; we never just enclose
		// since that identifies two circles of the original bdry.
		// (Of course, the new circle may have been added already in
		// a previous step.) TODO: with more work, can probably choose
		// starting point to avoid overage.
		else if (mode == DEGREE) {
			if (v2 == v1)
				v2 = getLastPetal(v2);
			int vert = v1;
			int nextvert = getFirstPetal(vert);

			// new circle shared with upstream nghb.
			count += add_vert(vert); 
			// go until you get to v2
			while (vert != v2) {
				int need=degree-countPetals(vert);
				for (int i = 1; i <= need; i++)
					count += add_vert(vert);
				enfold(vert);
				vert = nextvert;
				nextvert = getFirstPetal(vert);
				if (vert == v1)
					throw new CombException("Error in tracing the boundary: "
						+ "hit " + v1 + " before " + v2);
			}

			// now do v2 itself
			if (vert == v2) {
				int need=degree-countPetals(vert);
				for (int i = 1; i <= need; i++)
					count += add_vert(vert);
				enfold(vert);
			}
			if (packDCEL==null) {
				try {
					setBdryFlags();
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("Problem setting bdry flags in 'add_layer'.");
					return 0;
				}
			}
			return count;
		}

		else if (mode == DUPLICATE) {

			if (packDCEL!=null) {
				int origcount=packDCEL.vertCount;
				// generate combinatoric
				int ans= RawDCEL.baryBox_raw(packDCEL,v1,v2);
				if (ans==0)
					return 0;
				// TODO: too difficult to set radii
				CombDCEL.d_FillInside(packDCEL);
				return attachDCEL(packDCEL);
			}
			
			// traditional packing
			// get started with first edge
			// first circle is a bearing
			int vert = getFirstPetal(v1);
			count += add_vert(vert);
			int bearing = nodeCount;
			add_vert(bearing); // add two circles to start
			count += add_vert(bearing);
			enfold(bearing);

			// cycle until next point is v2
			int nextvert=0;
			if (vert != v2) {
				while ((nextvert = kData[vert].flower[0]) != v2) {
					// add bearing first
					count += add_vert(vert);
					enfold(vert);
					bearing = nodeCount;
					count += add_vert(bearing);
					enfold(bearing);
					vert = nextvert;
				}
				count += add_vert(vert);
				enfold(vert);
				bearing = nodeCount;
				if (v2 != v1) {
					count += add_vert(bearing);
					enfold(bearing);
				} else { // close up for full layer
					enfold(nextvert);
					enfold(bearing);

					// bearing ends up as interior; we want the circle of max
					// index
					// to be on the boundary, so we swap indices.
					int w = kData[bearing].flower[countFaces(bearing)];
					swap_nodes(w, nodeCount);
				}
			}

			try {
				setBdryFlags();
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("Some problem with add_layer.");
				return 0;
			}

			return count;
		}

		return count;
	}

	/** 
	 * Interchange two legal vertex numbers; other indices remain unchanged.
	 * vlist, elist, vertexMap, tiling info are adjusted, flist is lost. 
	 * If there's an error, the packing data will likely be compromised. 
	 * Caution: v w are switched in vertexMap (first entries only)
	 * Caution: calling routine must do complex_count, facedraworder, etc.
	 * @param v int
	 * @param w int
	 * @return 1
	 */
	public int swap_nodes(int v, int w) {
		if (packDCEL!=null) {
			Vertex holdv=packDCEL.vertices[v];
			Vertex holdw=packDCEL.vertices[w];
			VData datav=vData[v];
			VData dataw=vData[w];
			holdv.vertIndx=w;
			holdw.vertIndx=v;
			packDCEL.vertices[w]=holdv;
			packDCEL.vertices[v]=holdw;
			vData[v]=dataw;
			vData[w]=datav;
			packDCEL.fixDCEL_raw(this);
		}
		else {
			RData holdR = rData[v];
			KData holdK = kData[v];
			rData[v] = rData[w];
			kData[v] = kData[w];
			rData[w] = holdR;
			kData[w] = holdK;
			int vdum = 0;

			// for petals in v's new flower, replace w's with vdum's
			for (int i = 0; i < (countFaces(v) + getBdryFlag(v)); i++) {
				int pet = kData[v].flower[i];
				if (pet == v)
					pet = w;
				for (int j = 0; j <= countFaces(pet); j++)
					if (kData[pet].flower[j] == w)
						kData[pet].flower[j] = vdum;
			}

			// for petals in w's new flower, replace v's with w's
			for (int i = 0; i < (countFaces(w) + getBdryFlag(w)); i++) {
				int pet = kData[w].flower[i];
				if (pet == vdum)
					pet = v;
				for (int j = 0; j <= countFaces(pet); j++)
					if (kData[pet].flower[j] == v)
						kData[pet].flower[j] = w;
			}

			// for petals in v's new flower, replace vdum's with v
			for (int i = 0; i <= countFaces(v); i++) {
				int pet = kData[v].flower[i];
				for (int j = 0; j <= countFaces(pet); j++)
					if (kData[pet].flower[j] == vdum)
						kData[pet].flower[j] = v;
			}

		}
		
		// other adjustments
		if (activeNode == v)
			activeNode = w;
		else if (activeNode == w)
			activeNode = v;
		if (alpha == v)
			alpha = w;
		else if (alpha == w)
			alpha = v;
		if (gamma == v)
			gamma = w;
		else if (gamma == w)
			gamma = v;

		// fix up various lists: flist, glist will be invalidated by
		// complex_count
		flist = null;
		glist = null;

		// fix up first entries in vertex_map
		if (vertexMap != null) {
			Iterator<EdgeSimple> etrace = vertexMap.iterator();
			while (etrace.hasNext()) {
				EdgeSimple es = (EdgeSimple) etrace.next();
				if (es.v == v)
					es.v = w;
				else if (es.v == w)
					es.v = v;
			}
		}

		// fix up elist
		if (elist != null) 
			elist.swapVW(v, w);;
			
		// fix up vlist
		if (vlist != null) 
			vlist=vlist.swapVW(v, w);

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
		return 1;
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
		int ans = swap_nodes(v, w);
		if (ans == 0)
			return 0;
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
			setAim(v,getAim(v));
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
		int[] v1 = faces[f1].vert;
		int[] v2 = faces[f2].vert;
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
	 * Return center of incircle of face with given index.
	 * @param f int
	 * @return Complex, null on error
	 */
	public Complex face_center(int f) {
		if (f < 1 || f > faceCount)
			return null;
		int[] fverts=getFaceVerts(f);
		CircleSimple sc = null;
		Complex p0 = getCenter(fverts[0]);
		Complex p1 = getCenter(fverts[1]);
		Complex p2 = getCenter(fverts[2]);
		if (hes < 0)
			sc = HyperbolicMath.hyp_tang_incircle(p0, p1, p2,
					getRadius(fverts[0]),
					getRadius(fverts[1]),
					getRadius(fverts[2]));
		else if (hes > 0)
			sc = SphericalMath.sph_tri_incircle(p0, p1, p2);
		else
			sc = EuclMath.eucl_tri_incircle(p0, p1, p2);
		return sc.center;
	}

	  /**
	   * Intended (not actual) edge length from v to w, using invDist
	   * is that is set. This is not well defined in the sphere.
	   * @param v
	   * @param w
	   * @return double, -1 on error (e.g., if geometry is spherical)
	   */
	  public double intendedEdgeLength(int v,int w) {
		  double rv=getRadius(v);
		  double rw=getRadius(w);
		  double t=1.0;
		  if (hes>0) { // spherical: not-well defined
			  return -1;
		  }
		  if (overlapStatus) {
			  t=getInvDist(v,w);
		  }
		  if (hes<0) { // hyperbolic
			  return HyperbolicMath.h_ivd_length(rv,rw,t);
		  }
		  return EuclMath.e_ivd_length(rv,rw,t);
	  }

	  /**
	   * Distance between centers (actual edge length). Compare
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
	   * Get normal bouquet of vertices.
	   * @return
	   */
	  public int[][] getBouquet() {
		  int bouq[][]=new int[nodeCount+1][];
		  for (int v=1;v<=nodeCount;v++) {
			  int num=countFaces(v);
			  bouq[v]=new int[num+1];
			  for (int j=0;j<=num;j++)
				  bouq[v][j]=kData[v].flower[j];
		  }
		  return bouq;
	  }

	  /**
	   * Given v1 and v2, build edgelist from v1 to v2 through up to 
	   * 'lgth' hex interior vertices. Return null if no such path
	   * exists.   
	   * @param v1, v2, given vertices
	   * @param lgth, limit on length to search
	   * @return EdgeLink of hex edges from v1 to v2.
	   */
	  public EdgeLink get_extended_edge(int v1,int v2,int lgth) {
	    int next=0,dir;
	    EdgeLink edgeLink;

	    if (!status || v1==v2) return null;
	    if ((dir=nghb(v1,v2))!=-1) { // immediate neighbor
	        edgeLink=new EdgeLink(this);
	        edgeLink.add(new EdgeSimple(v1,v2));
	        return edgeLink;
	    }
	    // search in various directions, see if you find v2
	    for (dir=0;dir<(countFaces(v1)+getBdryFlag(v1));dir++) {
	        edgeLink=new EdgeLink(this);
	        int v=v1;
	        int w=kData[v1].flower[dir];
	        edgeLink.add(new EdgeSimple(v,w));
	        int i=1;
	        while (i<lgth && (next=hex_proj(w,v))!=0 && next!=v2) {
	        	v=w;
	        	w=next;
	        	edgeLink.add(new EdgeSimple(v,w));
	        	i++;
	        }
	        if (next==v2) {  // found it! 
	        	v=w;
	        	w=next;
	        	edgeLink.add(new EdgeSimple(v,w));
	        	return edgeLink;
	        }
	    }
	    return null;
	  } 
	  
	  /**
	   * TODO: 'axis-extrapolate' more general. Perhaps that's enough?
	   * From vertex v, look out edge in direction 'indx' and see if 
	   * vertex w is encountered within 'lgth' edges along a hex axis. 
	   * (Note: we do NOT check whether v and/or w themselves are hex 
	   * and/or interior. Return edge list forming the "hex axis" from 
	   * v to w, or null on failure. 
	   * If w equals v, then we're looking for a closed hex axis; 
	   * in particular, v=w must be interior and edge through v must
	   * be hex axis. (In particular, can reenter v in wrong way, but
	   * can continue and might come in in correct way within 'lgth' 
	   * distance.)
	   * @param v, starting vertex
	   * @param indx, which direction to look
	   * @param w, which vertex we're aiming at
	   * @param lgth, limit on how many edge 
	   * @return EdgeLink or null on error
	   */
	  public EdgeLink hex_extrapolate(int v,int indx,int w,int lgth) {
		  if (v==w && (isBdry(v) || countFaces(v)!=6)) {
	    	  flashError("hex_extrapolate: v=w must be interior and hex");
	    	  return null;
	      }
		  EdgeLink edgelist=new EdgeLink(this);
		  boolean okay=true; 
		  int next=0;
	      int i=0;
		  while (okay && i<lgth) { // allow multiple returns to v when v=w.
			  if (v!=w) okay=false;
			  int last=v;
			  int current=kData[v].flower[indx];
			  edgelist.add(new EdgeSimple(last,current));
			  i++;
			  while (current!=w && i<lgth && (next=hex_proj(current,last))!=0) {
				  edgelist.add(new EdgeSimple(current,next));
				  last=current;
				  current=next;
				  i++;
			  }
			  if (current!=w) return null; // didn't find w
			  if (v!=w) return edgelist; // found our axis.
	      
			  // in v=w situation, so check axis condition through v.
			  EdgeSimple edge=(EdgeSimple)edgelist.getFirst();
			  next=edge.w;
			  int j=nghb(v,last);
			  indx=nghb(v,next);
			  if (j==(indx+3)%6) return edgelist; // is a hex axis through v
	      
			  // closed, but not an axis? so try again
		  } // end of 'okay' while
		  return null; // must have failed
	  }
	  
	  /**
	   * From vertex v, look out edge in direction 'indx' and see if 
	   * vertex w is encountered within 'lgth' edges along an axis.
	   * 
	   * (An 'axis' is an interior edge list which leaves same number
	   * of edges on left and right (e.g., a "hex" axis, with 2 edges
	   * to each side). It can also extend along a boundary, the only
	   * condition that degree >= 2.)
	   *  
	   * Note: we do NOT check whether v and/or w themselves are even
	   * degree and/or interior. 
	   * Return edge list forming the "axis" from v to w, or null on failure. 
	   * If w equals v, then we're looking for a closed axis; 
	   * in particular, v=w must be interior and edge through v must
	   * be an axis. (In particular, can reenter v in wrong way, but
	   * can continue and might come in in correct way within 'lgth' 
	   * distance.)
	   * @param v int, starting vertex
	   * @param indx int, which direction to look
	   * @param w int, which vertex we're aiming at
	   * @param lgth int, limit on how many edge 
	   * @return EdgeLink or null on error
	   */
	  public EdgeLink axis_extrapolate(int v,int indx,int w,int lgth) {
		  if (v==w && (isBdry(v) || 2*(countFaces(v)/2)!=countFaces(v))) {
	    	  flashError("axis_extrapolate: if v=w, must be interior, even degree");
	    	  return null;
	      }
		  EdgeLink edgelist=new EdgeLink(this);
		  boolean okay=true; 
		  int next=0;
	      int i=0;
		  while (okay && i<lgth) { // allow multiple returns to v when v=w.
			  if (v!=w) okay=false;
			  int last=v;
			  int current=kData[v].flower[indx];
			  edgelist.add(new EdgeSimple(last,current));
			  i++;
			  while (current!=w && i<lgth && (next=axis_proj(current,last))!=0) {
				  edgelist.add(new EdgeSimple(current,next));
				  last=current;
				  current=next;
				  i++;
			  }
			  if (current!=w) return null; // didn't find w
			  if (v!=w) return edgelist; // found our axis.
	      
			  // in v=w situation, so check axis condition through v.
			  EdgeSimple edge=(EdgeSimple)edgelist.getFirst();
			  next=edge.w;
			  int j=nghb(v,last);
			  int half=countFaces(v)/2;
			  indx=nghb(v,next);
			  if (j==(indx+half)%countFaces(v)) // is a hex axis through v 
				  return edgelist; 
	      
			  // closed, but not an axis? so try again
		  } // end of 'okay' while
		  return null; // must have failed
	  }
	  
	  /**
	   * Given a closed chain of hex interior vertices (along a hex axis),
	   * "slide" the right side forward along the chain one notch.
	   * This amounts to cutting the packing in two along the chain,
	   * then reattaching with a shift. Return 0 on error. 
	   * 
	   * TODO: make this handle more general situations. 
	   * 
	   * @param hexChain EdgeLink
	   * @return 1
	  */
	  public int hex_slide(EdgeLink hexChain) {
		  EdgeSimple edge=null;
		  EdgeSimple last_edge=null;
		  EdgeSimple first_edge=null;
		  
		  	
	    /* check that the chain is closed, all verts are hex, and
	       the chain edges form a hex axis (two verts on right, two
	       on left) (including where the start/end connect) */

	      try {
			  	if (hexChain==null || hexChain.size()==0) throw new CombException("Failed to get hex chain");
			  	last_edge=(EdgeSimple)hexChain.getLast();
			  	first_edge=(EdgeSimple)hexChain.getFirst();
			  	int v=first_edge.v;
			  	if (last_edge.w!=v || countFaces(v)!=6 || isBdry(v) ||
			  			(nghb(v,last_edge.v)+3)%6 !=nghb(v,first_edge.w) ) {
			  		throw new ParserException("hex_slide: problems with the given edge chain");
			  	}
			  	Iterator<EdgeSimple> hc=hexChain.iterator();
			  	while (hc.hasNext()) {
			  		edge=(EdgeSimple)hc.next();
			  		if (edge.v!=v || countFaces(edge.w)!=6 || isBdry(edge.w)) 
			  			throw new ParserException("Error in hex chain");
			  		v=edge.w;
			  	}
	      } catch (Exception ex) { 
	  		throw new ParserException("usage: hex_slide: some error in edge chain");
	      }

	      // have to have room to hold the new flowers.
	      int count=hexChain.size();
	      int [][]Nflowers=new int[2*count][];
	      int []Nindices=new int[2*count]; // points to vert index of Nflower entry.
	      int index_ptr=0;
	      // modify flowers for parallel chain of verts to the right
	      Iterator<EdgeSimple> hc=hexChain.iterator();
	      int k,m,num;
	      int n_w;
	      EdgeSimple next_edge=last_edge;
	      
	      // along the chain we have vertices: p_v -> v -> n_v -> nn_v
	      int v=next_edge.v;
	      int n_v=first_edge.v;
	      int nn_v=first_edge.w;
	      edge=(EdgeSimple)hexChain.get(hexChain.size()-2);
	      int p_v=edge.v;

	      // along the parallel, pp_w -> p_w -> w -> n_w
	      num=countFaces(n_v); // should be 6
	      k=nghb(n_v,v);
	      int w=kData[n_v].flower[(k+1)%num];
	      num=countFaces(p_v); // should be 6
	      k=nghb(p_v,v);
	      int p_w=kData[p_v].flower[(k-1+num)%num];
	      int pp_w=kData[p_v].flower[(k-2+num)%num];
	      num=countFaces(w);
	      k=nghb(w,n_v);
	      n_w=kData[w].flower[(k-1+num)%num];
	      while (hc.hasNext()) {
	    	  edge=next_edge;
	    	  next_edge=(EdgeSimple)hc.next();
	    	  p_v=v;
	    	  v=n_v;
	    	  n_v=nn_v;
	    	  // next along chain
	    	  nn_v=hex_proj(n_v,v);
	    	  
	    	  pp_w=p_w;
	    	  p_w=w;
	    	  w=n_w;
		      // next along parallel
		      num=countFaces(w);
		      k=nghb(w,n_v);
		      n_w=kData[w].flower[(k-1+num)%num];
	    	  
		      // new flower for 'w' 
		      m=(nghb(w,v)+1)%num;
		      Nflowers[index_ptr]=new int[num+1];
		      Nindices[index_ptr]=w;
	    	  m=nghb(w,n_v);
		      if (isBdry(w)) {
		    	  for (int i=0;i<=m-1;i++)
		    		  Nflowers[index_ptr][i]=kData[w].flower[i];
		    	  Nflowers[index_ptr][m]=nn_v;
		    	  Nflowers[index_ptr][(m+1)%num]=n_v;
		    	  for (int i=m+2;i<=num;i++)
		    		  Nflowers[index_ptr][i]=kData[w].flower[i];
		      }
		      else {
		    	  Nflowers[index_ptr][m]=nn_v;
		    	  Nflowers[index_ptr][(m+1)%num]=n_v;
		    	  for (int i=2;i<num;i++) 
		    		  Nflowers[index_ptr][(m+i)%num]=kData[w].flower[(m+i)%num];
		    	  Nflowers[index_ptr][num]=Nflowers[index_ptr][0];
		      }
		      index_ptr++;
		      
		      // new flower for 'v'
		      num=countFaces(v); // should be 6
		      m=nghb(v,p_w);
		      Nflowers[index_ptr]=new int[num+1];
		      Nindices[index_ptr]=v;
		      Nflowers[index_ptr][m]=pp_w;
		      Nflowers[index_ptr][(m+1)%num]=p_w;;
		      for (int i=2;i<num;i++) 
		    	  Nflowers[index_ptr][(m+i)%num]=kData[v].flower[(m+i)%num];
		      Nflowers[index_ptr][num]=Nflowers[index_ptr][0];
		      
		      index_ptr++;
	      }		      
	      
	      // now, replace flowers with the new flowers
	      for (int i=0;i<2*count;i++)
	    	  kData[Nindices[i]].flower=Nflowers[i];
	      
	      setCombinatorics();
	      return 1;
	  }     
	  
	  /**
	   * This splits a vertex v into 2 nghb'ing verts, v, v'.
	   * Split occurs in combinatoric direction determined by edges 
	   * {v,w}, {v,u}: namely, {v,w}, {v,u}, {v',w}, {v',u} are all 
	   * edges and {v',w,v} and {v',v,u} are oriented faces of new 
	   * complex. Only flowers of v,w,u, new v', and nghbs switched
	   * from v to v' are affected. v can be bdry: new edge direction 
	   * determined by orientation of w and u vis-a-vis v. The
	   * calling routine must call 'complex_count'.
	   * Note: 'flist' is invalidated, all overlaps are lost
	   * 
	   * @param v int, vert to be split to give edge {v,v'}
	   * @param w int, vert on 'left' of new edge {v,v'}
	   * @param u int, vert on 'right' of new edge {v,v'}
	   * @return int v', the number of the new vertex, 0 on error
	   */
	  public int split_flower(int v,int w,int u) {
		  int ind_wv,ind_uv,ind_vw,ind_vu;
		  if (w<1 || w>nodeCount || v<1 || v>nodeCount || u<1 || u>nodeCount ||
				  u==w || u==v || w==v || 
				  (ind_wv=nghb(w,v))<0 || (ind_uv=nghb(u,v))<0 || 
				  (ind_vw=nghb(v,w))<0 || (ind_vu=nghb(v,u))<0)
			  throw new ParserException("improper data for split");

		  int newV=++nodeCount;
		  alloc_pack_space(nodeCount+1,true);
		  if (overlapStatus) 
			  free_overlaps();
		  int []newFlower=null;
		  
		  // if v is interior
		  if (!isBdry(v)) {
			  kData[newV].num=(ind_vw-ind_vu+countFaces(v))%countFaces(v)+2;
			  kData[newV].flower=new int[countFaces(newV)+1];
			  
			  // newV's flower starts/ends with v
			  kData[newV].flower[0]=kData[newV].flower[countFaces(newV)]=v;
			  // rest of petals are thos omitted from v's flower
			  for (int j=0;j<(countFaces(newV)-1);j++) {
				  kData[newV].flower[j+1]=kData[v].flower[(ind_vu+j)%countFaces(v)];
			  }
			  setBdryFlag(newV,0); // newV is interior
			  setRadius(newV,getRadius(v));
			  setAim(newV,2.0*Math.PI); // default aim
			  
			  // v's flower; let it start and end with newV;
			  int num=countFaces(v);
			  int k=(ind_vu-ind_vw+countFaces(v))%countFaces(v);
			  kData[v].num=k+2;
			  newFlower=new int[countFaces(v)+1];
			  newFlower[0]=newFlower[countFaces(v)]=newV;
			  for (int j=0;j<=k;j++) {
				  newFlower[j+1]=kData[v].flower[(ind_vw+j)%num];
			  }
			  kData[v].flower=newFlower;
			  
			  // fix w; add newV after v in flower
			  num=countFaces(w);
			  newFlower=new int[num+2];
			  for (int j=0;j<=ind_wv;j++) 
				  newFlower[j]=kData[w].flower[j];
			  newFlower[ind_wv+1]=newV;
			  for (int j=ind_wv+1;j<=num;j++)
				  newFlower[j+1]=kData[w].flower[j];
			  if (ind_wv==0 && !isBdry(w))
				  newFlower[num+1]=v;
			  kData[w].flower=newFlower;
			  kData[w].num++;

			  // fix u; add newV before v in flower
			  newFlower=new int[countFaces(u)+2];
			  for (int j=0;j<ind_uv;j++)
				  newFlower[j]=kData[u].flower[j];
			  newFlower[ind_uv]=newV;
			  for (int j=ind_uv;j<=countFaces(u);j++)
				  newFlower[j+1]=kData[u].flower[j];
			  kData[u].num++;
			  if (ind_uv==0 && !isBdry(u))
				  newFlower[countFaces(u)]=newV;
			  kData[u].flower=newFlower;
			  
			  // fix up verts that used to be nghb v
			  for (int j=2;j<(countFaces(newV)-1);j++) {
				  int vv=kData[newV].flower[j];
				  int ind_vvv=nghb(vv,v);
				  kData[vv].flower[ind_vvv]=newV;
				  if (ind_vvv==0 && !isBdry(vv))
					  kData[vv].flower[countFaces(vv)]=newV;
			  }
		  }
		  else { // v is boundary vert
			  if (ind_vw>ind_vu) { // newV will be interior
				  
				  kData[newV].num=ind_vw-ind_vu+2;
				  kData[newV].flower=new int[countFaces(newV)+1];
				  
				  // newV's flower starts/ends with v
				  kData[newV].flower[0]=kData[newV].flower[countFaces(newV)]=v;
				  // rest of petals are those omitted from v's flower
				  for (int j=0;j<(countFaces(newV)-1);j++) {
					  kData[newV].flower[j+1]=kData[v].flower[(ind_vu+j)];
				  }
				  setBdryFlag(newV,0);
				  setRadius(newV,getRadius(v));
				  setAim(newV,2.0*Math.PI);
				  
				  // v's flower; find up to u and after w
				  int num=countFaces(v);
				  int k=(ind_vu+num-ind_vw);
				  kData[v].num=k+2;
				  newFlower=new int[countFaces(v)+1];
				  
				  // up to, including, u
				  for (int j=0;j<=ind_vu;j++) {
					  newFlower[j]=kData[v].flower[j];
				  }
				  newFlower[ind_vu+1]=newV;
				  k=ind_vu+2;
				  
				  // from w on
				  for (int j=0;j<=(num-ind_vw);j++)
					  newFlower[k+j]=kData[v].flower[ind_vw+j];
				  kData[v].flower=newFlower;
				  
				  // fix w; add newV after v in flower
				  newFlower=new int[countFaces(w)+2];
				  for (int j=0;j<=ind_wv;j++) 
					  newFlower[j]=kData[w].flower[j];
				  newFlower[ind_wv+1]=newV;
				  for (int j=ind_wv+1;j<=countFaces(w);j++)
					  newFlower[j+1]=kData[w].flower[j];
				  kData[w].num++;
				  if (ind_wv==0 && !isBdry(w))
					  newFlower[countFaces(w)]=v;
				  kData[w].flower=newFlower;
				  
				  // fix u; add newV before v in flower
				  newFlower=new int[countFaces(u)+2];
				  for (int j=0;j<ind_uv;j++)
					  newFlower[j]=kData[u].flower[j];
				  newFlower[ind_uv]=newV;
				  for (int j=ind_uv;j<=countFaces(u);j++)
					  newFlower[j+1]=kData[u].flower[j];
				  kData[u].num++;
				  if (ind_uv==0 && !isBdry(u))
					  newFlower[countFaces(u)]=newV;
				  kData[u].flower=newFlower;
				  
				  // fix up verts that used to nghb v
				  for (int j=2;j<(countFaces(newV)-1);j++) {
					  int vv=kData[newV].flower[j];
					  int ind_vvv=nghb(vv,v);
					  kData[vv].flower[ind_vvv]=newV;
					  if (ind_vvv==0 && !isBdry(vv))
						  kData[vv].flower[countFaces(vv)]=newV;
				  }
				  
			  }
			  else { // newV will be a boundary vert, inhereting some ngbhs of v

				  int num=countFaces(v);
				  kData[newV].num=ind_vw+num-ind_vu+2;
				  kData[newV].flower=new int[countFaces(newV)+1];
				  
				  // nghbs of v up to w
				  for (int j=0;j<=ind_vw;j++)
					  kData[newV].flower[j]=kData[v].flower[j];
				  int k=ind_vw+1;
				  kData[newV].flower[k]=v;
				  
				  // nghbs of v after u
				  for (int j=0;j<=(num-ind_vu);j++)
					  kData[newV].flower[k+j+1]=kData[v].flower[ind_vu+j];

				  setBdryFlag(newV,1); // boundary vertex this time
				  setRadius(newV,getRadius(v));
				  setAim(newV,-2.0*Math.PI);
				  
				  // v's flower, starts/ends with newV
				  k=ind_vu-ind_vw;
				  kData[v].num=k+2;
				  newFlower=new int[countFaces(v)+1];
				  newFlower[0]=newFlower[countFaces(v)]=newV;
				  for (int j=0;j<=k;j++)
					  newFlower[j+1]=kData[v].flower[ind_vw+j];
				  kData[v].flower=newFlower;
				  setAim(v,2.0*Math.PI);

				  // fix w; add newV after v in flower
				  newFlower=new int[countFaces(w)+2];
				  for (int j=0;j<=ind_wv;j++) 
					  newFlower[j]=kData[w].flower[j];
				  newFlower[ind_wv+1]=newV;
				  for (int j=ind_wv+1;j<=countFaces(w);j++)
					  newFlower[j+1]=kData[w].flower[j];
				  kData[w].num++;
				  if (ind_wv==0 && !isBdry(w))
					  newFlower[countFaces(w)]=v;
				  kData[w].flower=newFlower;
				  
				  // fix u; add newV before v in flower
				  newFlower=new int[countFaces(u)+2];
				  for (int j=0;j<ind_uv;j++)
					  newFlower[j]=kData[u].flower[j];
				  newFlower[ind_uv]=newV;
				  for (int j=ind_uv;j<=countFaces(u);j++)
					  newFlower[j+1]=kData[u].flower[j];
				  kData[u].num++;
				  if (ind_uv==0 && !isBdry(u))
					  newFlower[countFaces(u)]=newV;
				  kData[u].flower=newFlower;
				  
				  // fix up verts that use to nghb v
				  for (int j=2;j<(countFaces(newV)-1);j++) {
					  int vv=kData[newV].flower[j];
					  int ind_vvv=nghb(vv,v);
					  kData[vv].flower[ind_vvv]=newV;
					  if (ind_vvv==0 && !isBdry(vv))
						  kData[vv].flower[countFaces(vv)]=newV;
				  }
			  }
		  }
		  return newV;
	  }
	  
	  
	  /**
	   * Merge neighbors v and M (if the result is legal).
	   * CAUTION: The calling routine, given v and w, is assumed to 
	   * have done a 'swap' of w with M. At the end of this routine, 
	   * M remains, but as a degree-3 barycenter, and the calling 
	   * routine must do 'complex_count', then remove M. This should 
	   * ensure renumbering of lists, etc. 
	   * Note, 'flist' and 'zyzpont' are invalidated.
	   * @param v int
	   * @return int, 0 on error, else return merged index (namely, v)
	   */
	  public int merge_verts(int v) {
		  int M=nodeCount;
		  int ind_vM=-1;
		  if (v==M || (ind_vM=nghb(v,M))<0) {
			  CirclePack.cpb.errMsg("improper 'merge' call");
			  return -1;
		  }
		  int ind_Mv=nghb(M,v);
		  
		  // Is one of M or v a barycenter?
		  if (!isBdry(M) && countFaces(M)==3)
			  return v;
		  if (!isBdry(v) && countFaces(v)==3) {
			  // swap v and M
			  int []holdflower=kData[v].flower;
			  int holdnum=countFaces(M);
			  kData[v].flower=kData[M].flower;
			  kData[v].num=holdnum;
			  setBdryFlag(v,getBdryFlag(M));
			  kData[M].flower=holdflower;
			  kData[M].num=3;
			  setBdryFlag(M,0);
			  
			  // swap v and M as petals of one another
			  kData[v].flower[ind_Mv]=M;
			  if (ind_Mv==0 && !isBdry(v))
				  kData[v].flower[countFaces(v)]=M;
			  kData[M].flower[ind_vM]=v;
			  if (ind_vM==0)
				  kData[M].flower[countFaces(M)]=v;
			  
			  // fix neighbors of new v
			  for (int j=0;j<(countFaces(v)+getBdryFlag(v));j++) {
				  int u=kData[v].flower[j];
				  int ind_uv=nghb(u,M);
				  if (ind_uv>=0)
					  kData[u].flower[ind_uv]=v;
				  if (ind_uv==0 && !isBdry(u))
					  kData[u].flower[countFaces(u)]=v;
			  }
			  // fix neighbors of new M
			  for (int j=0;j<3;j++) {
				  int u=kData[M].flower[j];
				  int ind_uM=nghb(u,v);
				  if (ind_uM>=0)
					  kData[u].flower[ind_uM]=M;
				  if (ind_uM==0 && !isBdry(u))
					  kData[u].flower[countFaces(u)]=M;
			  }
			  this.flist=null;
			  this.xyzpoint=null;
			  return v;
		  }
		  
// =============================
		  // TODO: real work remains
// =============================		  
		  
		  return 1;
	  }

	  /**
	   * Fracking is a combinatorial refinement process. We make a
	   * copy of the KData to use in processing. Given a set of
	   * vertices, we first add a barycenter to each neighboring face.
	   * Then we flip each edge shared by two of these faces. Finally,
	   * we remove any boundary edges of these faces. We insert the
	   * new data into packData, process it and return the count of new
	   * vertices. 
	   * @param verts NodeLink
	   * @return int, count of new vertices, -1 on error
	   */
	  public int frackMe(NodeLink verts) {
		  if (verts==null || verts.size()==0)
			  return 0;
		  
		  // new storage areas
		  int fcount=1; // how many new barycenters?
		  Iterator<Integer> vlst=verts.iterator();
		  while (vlst.hasNext()) {
			  fcount+=countFaces(vlst.next());
		  }
		  
		  KData []newK=new KData[nodeCount+fcount];
		  RData []newR=new RData[nodeCount+fcount];
		  int vcount=0;
		  for (int v=1;v<=nodeCount;v++) {
			  newK[v]=kData[v].clone();
			  newR[v]=rData[v].clone();
		  }
		  
		  // find the faces for barycenters; i.e. nghbs to verts
		  int []f4b=new int[faceCount+1];
		  vlst=verts.iterator();
		  while (vlst.hasNext()) {
			  int v=vlst.next();
			  if (countFaces(v)>1) { // ignore v having single face
				  int[] faceFlower=getFaceFlower(v);
				  for (int k=0;k<countFaces(v);k++)
					  f4b[faceFlower[k]]=1;
			  }
		  }
		  FaceLink flink=new FaceLink(this);
		  for (int k=0;k<=faceCount;k++) {
			  if (f4b[k]==1)
				  flink.add(k);
		  }
		  if (flink==null || flink.size()==0)
			  return 0;
				  
		  // add barycenters
		  Iterator<Integer> flst=flink.iterator();
		  while (flst.hasNext()) {
			  int f=flst.next();
			  vcount++;
			  int nextv=nodeCount+vcount;
//			  if (nextv>sizeLimit)
//		    	alloc_pack_space(newval+10,true);
//	 	      nodeCount++;
			  newK[nextv]=new KData();
			  newK[nextv].num=3;
			  newK[nextv].flower=new int[4];
			  newK[nextv].flower[0]=newK[nextv].flower[3]=faces[f].vert[0];
			  newK[nextv].flower[1]=faces[f].vert[1];
			  newK[nextv].flower[2]=faces[f].vert[2];
			  if (overlapStatus) {
				  newK[nextv].invDist=new double[4];
				  newK[nextv].invDist[0]=newK[nextv].invDist[1]=1.0;
		          newK[nextv].invDist[2]=newK[nextv].invDist[3]=1.0;
			  }
			  newK[nextv].bdryFlag=0;
			  newK[nextv].color=ColorUtil.getFGColor();
			  
			  // insert this barycenter
			  for (int i=0;i<3;i++) {
				  int v=faces[f].vert[i];
				  newK[v].add_petal_w(nextv,faces[f].vert[(i+1)%3]);
			  }
			  
			  // set center, radius at averages
			  newR[nextv]=new RData();
			  for (int i=0;i<3;i++) { 
				  int v=faces[f].vert[i];
				  newR[nextv].center=newR[nextv].center.add(newR[v].center);
				  newR[nextv].rad=newR[nextv].rad+newR[v].rad;
			  }
			  newR[nextv].rad /=6.0;
			  newR[nextv].center=newR[nextv].center.times(1.0/3.0);
			  setAim(nextv,2.0*Math.PI);
		  }
		  
		  int newNodeCount=nodeCount+vcount;
		  
		  // remaining adjustments in two stages: 
		  //  (1) add new edge between baryverts, convert some
		  //      baryverts to bdry
		  //  (2) remove the old edges

		  // (1) add new edges
		  vlst=verts.iterator();
		  while (vlst.hasNext()) {
			  int v=vlst.next();
			  
			  // bdry v? first/last baryverts becomes bdry
			  if (isBdry(v)) {
				  int b=newK[v].flower[1]; // new baryvert
				  if (b<=nodeCount)
					  throw new CombException("should be new baryvert");

				  // remove old edge from its flower, convert it to bdry
				  if (newK[b].remove_edge(v)<=0)
					  throw new CombException("missing this edge?");
				  
				  
				  b=newK[v].flower[newK[v].num-1]; // new baryvert
				  
				  // temporarily reset b as interior and close flower
				  if (newK[b].bdryFlag==1) {
					  newK[b].bdryFlag=0;
					  newK[b].num++;
					  newK[b].flower[newK[b].num]=newK[b].flower[0];
				  }
				  						  
				  if (b<=nodeCount)
					  throw new CombException("should be new baryvert");
				  int w=newK[v].flower[newK[v].num];
				  // this should make b into bdry vert
				  if (newK[b].remove_edge(w)<=0)
					  throw new CombException("missing edge?");
			  }
			  
			  // for each old interior edge, add flipped edge between
			  //   the baryverts on each side; later we'll remove the old edge
			  for (int k=0;k<(newK[v].num-2);k=k+2) {
				  int w=newK[v].flower[k];
				  if (w>nodeCount)
					  throw new CombException("baryvert out of place");
					  
				  // closed flower; first do wrap-around edge 
				  if (k==0 && newK[v].bdryFlag==0) {
					  int b2=newK[v].flower[1];
					  int b1=newK[v].flower[newK[v].num-1];
					  if (b2<=nodeCount || b1<=nodeCount)
						  throw new CombException("problem with wrap");
					  newK[b1].add_petal_w(b2,w);
					  newK[b2].add_petal_w(b1,v);
				  }
				  
				  // in general
				  w=newK[v].flower[k+2];
				  int b1=newK[v].flower[k+1];
				  int b2=newK[v].flower[k+3];
				  if (b1<=nodeCount || b2<=nodeCount)
					  throw new CombException("problem with new bary-edge");
				  newK[b1].add_petal_w(b2,w);
				  newK[b2].add_petal_w(b1,v);
			  }
		  }
		  
		  // (2) remove all the old edges
		  vlst=verts.iterator();
		  while (vlst.hasNext()) {
			  int v=vlst.next();
			  
			  Vector<Integer> oldnghbs=new Vector<Integer>(0);
			  for (int k=0;k<(newK[v].num+newK[v].bdryFlag);k++)
				  if (newK[v].flower[k]<=nodeCount)
					  oldnghbs.add(newK[v].flower[k]);

			  Iterator<Integer> oldv=oldnghbs.iterator();
			  while (oldv.hasNext()) {
				  int w=oldv.next();
				  newK[w].remove_petal_v(v);
				  newK[v].remove_petal_v(w);
			  }
		  }

   		  if (newNodeCount > sizeLimit
   				  && alloc_pack_space(newNodeCount,true)==0 ) {
	   		      throw new DataException("Space allocation problem with 'frack'");
   		  }
   		  
   		  // hold copies of old data
   		  int holdNodeCount=nodeCount;
   		  KData []holdK=kData;
   		  RData []holdR=rData;
   		  
   		  // try the new data structures
   		  nodeCount=newNodeCount;
   		  KData []nwK=new KData[nodeCount+1];
   		  RData []nwR=new RData[nodeCount+1];
   		  for (int v=1;v<=newNodeCount;v++) {
   			  nwK[v]=newK[v];
   			  nwR[v]=newR[v];
   		  }
   		  kData=nwK;
   		  rData=nwR;
   		  int rslt=this.setCombinatorics();
   		  if (rslt==0) {
   			  nodeCount=holdNodeCount;
   			  kData=holdK;
   			  rData=holdR;
   			  CirclePack.cpb.errMsg("frack seems to have failed; old data in place");
   			  return 0;
   		  }

		  return newNodeCount;
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
		  if (pdc!=null) {
	   		  Boolean redProblem=Boolean.valueOf(false); // for dcel version 
			  while (fis.hasNext()) {
    			  count += CombDCEL.flipEdge(pdc,pdc.findHalfEdge(fis.next()), redProblem);
			  }
   			  if (count>0) {
   				  if (redProblem.booleanValue()) { // must build a new red cahin
   					  CombDCEL.redchain_by_edge(pdc,null,pdc.alpha,false);
   				  }
   				  CombDCEL.d_FillInside(pdc);
   				  return attachDCEL(pdc);
   			  }
   			  return 0;
		  }
		  
		  // traditional
		  while (fis.hasNext()) {
			  EdgeSimple ege=fis.next();
			  count += flip_edge(ege.v,ege.w,2);
		  }
		  if (count>0) {
			  setCombinatorics();
			  fillcurves();
			  return count;
		  }
		  return 0;
	  }
	  
	  /** 
	   * Careful, calling routine has to do cleanup; generally call
	   * from 'split_flips' and from 'bary_refine'; if flag==2 and
	   * edge {v,w} is shared by two faces, do Whitehead move -- remove
	   * {v,w} as an edge and make {lv,rv} an edge between common nghb verts
	   * lv, rv. If flag==3 and common nghb's lv, rv form edge, do reverse
	   * -- create edge {v,w}, remove {lv,rv}. Caution: some data not
	   * updated between calls.
	   * @param v int
	   * @param w int
	   * @param flag int  
	   * @return 1, 0 for unflipable edges.
	  */
	  public int flip_edge(int v,int w,int flag) {
	    int ind_v,ind_w,ind_c;
	    double []newol;

	    if (flag==3) { // must find common neighbor to get edge to flip 
	        EdgeLink elink=new EdgeLink();
	        elink.add(getCommonEdge(v,w));
	        return flipList(elink);
	    }
	    
	    if (!flipable(v,w)) {
	    	CirclePack.cpb.errMsg("flip: edge <"+v+" "+w+"> not flipable");
	    	return 0;
	    }
	    
	    int lv=getPetal(v,(ind_v=nghb(v,w))+1);
	    int rv=getPetal(w,(ind_w=nghb(w,v))+1);
	        
	    // adjust flower of v 
	    int []newflower=new int[countFaces(v)];
	    for (int j=0;j<ind_v;j++) 
	    	newflower[j]=getPetal(v,j);
	    for (int j=ind_v+1;j<=countFaces(v);j++)
	    	newflower[j-1]=getPetal(v,j);
	    kData[v].flower=newflower;
	    if (overlapStatus) {
	    	newol=new double[countFaces(v)];
	    	for (int j=0;j<ind_v;j++) newol[j]=getInvDist(v,kData[v].flower[j]);
	    	for (int j=ind_v+1;j<=countFaces(v);j++)
	    		newol[j-1]=getInvDist(v,kData[v].flower[j]);
	    	kData[v].invDist=newol;
	    }
	    kData[v].num--;
	    if (ind_v==0 && !isBdry(v)) 
	    	kData[v].flower[countFaces(v)]=kData[v].flower[0];
	    
	    // adjust flower of w 
	    newflower=new int[countFaces(w)];
	    for (int j=0;j<ind_w;j++) newflower[j]=kData[w].flower[j];
	    for (int j=ind_w+1;j<=countFaces(w);j++)
	    	newflower[j-1]=kData[w].flower[j];
	    kData[w].flower=newflower;
	    if (overlapStatus) {
	    	newol=new double[countFaces(w)];
	    	for (int j=0;j<ind_w;j++) newol[j]=getInvDist(w,kData[w].flower[j]);
	    	for (int j=ind_w+1;j<=countFaces(w);j++)
	    		newol[j-1]=getInvDist(w,kData[w].flower[j]);
	  	  	kData[w].invDist=newol;
	    }
	    kData[w].num--;
	    if (ind_w==0 && !isBdry(w)) 
	    	kData[w].flower[countFaces(w)]=kData[w].flower[0];

	    // adjust nghb flowers 
	    ind_v=nghb(lv,v);
	    newflower=new int[countFaces(lv)+2];
	    for (int j=0;j<=ind_v;j++) newflower[j]=kData[lv].flower[j];
	    for (int j=countFaces(lv);j>ind_v;j--)
	    	newflower[j+1]=kData[lv].flower[j];
	    newflower[ind_v+1]=rv;
	    kData[lv].flower=newflower;
	    if (overlapStatus) {
	    	newol=new double[countFaces(lv)+2];
	    	for (int j=0;j<=ind_v;j++) newol[j]=getInvDist(lv,kData[lv].flower[j]);
	    	for (int j=countFaces(v);j>ind_v;j--)
	    		newol[j+1]=getInvDist(lv,kData[lv].flower[j]);
	  	  	newol[ind_v+1]=1.0;
	  	  	kData[lv].invDist=newol;
	    }
	    kData[lv].num++;
	    ind_w=nghb(rv,w);
	    newflower=new int[countFaces(rv)+2];
	    for (int j=0;j<=ind_w;j++) newflower[j]=kData[rv].flower[j];
	    for (int j=countFaces(rv);j>ind_w;j--)
	    	newflower[j+1]=kData[rv].flower[j];
	    newflower[ind_w+1]=lv;
	    kData[rv].flower=newflower;
	    if (overlapStatus) {
	    	newol=new double[countFaces(rv)+2];
	    	for (int j=0;j<=ind_w;j++) newol[j]=getInvDist(rv,kData[rv].flower[j]);
	    	for (int j=countFaces(rv);j>ind_w;j--)
	    		newol[j+1]=getInvDist(rv,kData[rv].flower[j]);
	    	newol[ind_w+1]=1.0;
	    	kData[rv].invDist=newol;
	    }
	    kData[rv].num++;
	    return 1;
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
		  int ind_v,ind_w,lv,rv;
		  try {
			  if (v<1 || w<1 || v>nodeCount || w>nodeCount 
					  || (ind_v=nghb(v,w))<0 || (ind_w=nghb(w,v))<0 
				  || (isBdry(v) && (kData[v].flower[0]==w || countFaces(v)<=2))
				  || (isBdry(w) && (kData[w].flower[0]==v || countFaces(w)<=2))
				  || (!isBdry(v) && countFaces(v)<=3) 
				  || (!isBdry(w) && countFaces(w)<=3)) {
				  throw new DataException("deg <= 3 or bdry edge");
			  }
			  lv=kData[v].flower[ind_v+1];
			  rv=kData[w].flower[ind_w+1];
	          if ((isBdry(lv) && isBdry(rv)) || nghb(lv,rv)>=0)
	        	  throw new DataException("new edge is repeat or would connect bdry");
	        } catch (DataException dex) {
	        	return false;
	        }
	        return true;
	  }
	  
	  /**
	   * Change combinatorics at vertex 'centV'. Idea is to put a ring
	   * of new vertices around it, each degree 5 and connected to
	   * two petals.
	   * @param centV int
	   * @return int, new nodeCount
	   */
	  public int ring_vert(int centV) {
		  int origCount=nodeCount;
		  this.alloc_pack_space(origCount+countFaces(centV)+2,true);
		  if (!isBdry(centV)) { // interior
			  
			  // build new flower around centV, create new vertices
			  int centNum=countFaces(centV);
			  int []newCentFlower=new int[centNum+1];
			  newCentFlower[0]=newCentFlower[centNum]=++nodeCount;
			  for (int j=1;j<centNum;j++)
				  newCentFlower[j]=++nodeCount;
			  
			  // create data for new vertices
			  for (int j=0;j<centNum;j++) {
				  int newV=origCount+1+j;
				  int []newflower=new int[6];
				  newflower[0]=newflower[5]=centV;
				  newflower[1]=origCount+1+(j+centNum-1)%centNum;
				  newflower[2]=kData[centV].flower[j];
				  newflower[3]=kData[centV].flower[j+1];
				  newflower[4]=origCount+1+(j+1)%centNum;
				  kData[newV]=new KData();
				  setBdryFlag(newV,0);
				  kData[newV].num=5;
				  kData[newV].flower=newflower;
				  if (overlapStatus) { // default
					  kData[newV].invDist=new double[countFaces(newV)+1];
					  for (int k=0;k<=countFaces(newV);k++)
						  set_single_invDist(newV,kData[newV].flower[k],1.0);
				  }
				  rData[newV]=new RData();
				  setCenter(newV,getCenter(centV));
				  setRadius(newV,getRadius(centV));
				  setAim(newV,2*Math.PI);
			  }

			  // fix up original nghbs to centV: new vert
			  for (int j=0;j<centNum;j++) {  
				  int nextNew=origCount+1+j;
				  int lastNew=origCount+1+(j-1+centNum)%centNum;
				  int v=kData[centV].flower[j];
				  int indx=nghb(v,centV);
				  kData[v].flower[indx]=lastNew;
				  if (overlapStatus)
					  set_single_invDist(v,kData[v].flower[indx],1.0);
				  if (indx==0 && !isBdry(v)) {
					  kData[v].flower[countFaces(v)]=lastNew;
					  if (overlapStatus)
						  set_single_invDist(v,kData[v].flower[countFaces(v)],1.0);
				  }
				  insert_petal(v,indx,nextNew);
			  }
			  
			  kData[centV].flower=newCentFlower;
			  if (overlapStatus) { // any overlaps are lost
				  kData[centV].invDist=new double[countFaces(centV)+1];
				  for (int k=0;k<=countFaces(centV);k++)
					  set_single_invDist(centV,kData[centV].flower[k],1.0);
			  }
		  }
		  else { // centV is on the boundary
			  int centNum=countFaces(centV);
			  int downB=kData[centV].flower[0];
			  int upB=kData[centV].flower[centNum];
			  int newDown=++nodeCount;
			  int newUp=++nodeCount;
			  double rad=getRadius(centV);
			  if (hes<0 && (rad<0 || rad>1.0))
					  rad=.5;
			  
			  // build new flower around centV, set new vertex numbers
			  int []newCentFlower=new int[centNum+2];
			  newCentFlower[0]=newDown;
			  newCentFlower[centNum]=newUp;
			  for (int j=1;j<=centNum;j++)
				  newCentFlower[j]=++nodeCount;
			  newCentFlower[centNum+1]=origCount+2;
			  
			  // build newDown 
			  kData[newDown]=new KData();
			  kData[newDown].num=2;
			  setBdryFlag(newDown,1);
			  kData[newDown].flower=new int[3];
			  kData[newDown].flower[0]=downB;
			  kData[newDown].flower[1]=origCount+3;
			  kData[newDown].flower[2]=centV;
			  if (overlapStatus) { 
				  kData[newDown].invDist=new double[3];
				  for (int k=0;k<=2;k++)
					  set_single_invDist(newDown,kData[newDown].flower[k],1.0);
			  }
			  rData[newDown]=new RData();
			  setCenter(newDown,getCenter(centV));
			  setRadius(newDown,rad);
			  setAim(newDown,-1.0);
			  
			  // build newUp
			  kData[newUp]=new KData();
			  kData[newUp].num=2;
			  setBdryFlag(newUp,1);
			  kData[newUp].flower=new int[3];
			  kData[newUp].flower[0]=centV;
			  kData[newUp].flower[1]=nodeCount; // last new vertex
			  kData[newUp].flower[2]=upB;
			  if (overlapStatus) { // 
				  kData[newUp].invDist=new double[3];
				  for (int k=0;k<=2;k++)
					  set_single_invDist(newUp,kData[newUp].flower[k],1.0);
			  }
			  rData[newUp]=new RData();
			  setCenter(newUp,getCenter(centV));
			  setRadius(newUp,rad);
			  setAim(newUp,-0.1);
			  
			  // create first new interior vertex
			  int newV=origCount+3;
			  kData[newV]=new KData();
			  kData[newV].num=5;
			  setBdryFlag(newV,0);
			  kData[newV].flower=new int[6];
			  kData[newV].flower[0]=kData[newV].flower[5]=centV;
			  kData[newV].flower[1]=newDown;
			  kData[newV].flower[2]=downB;
			  kData[newV].flower[3]=kData[centV].flower[1];
			  kData[newV].flower[4]=origCount+4;
			  if (overlapStatus) { 
				  kData[newV].invDist=new double[6];
				  for (int k=0;k<=5;k++)
					  set_single_invDist(newV,kData[newV].flower[k],1.0);
			  }
			  rData[newV]=new RData();
			  setCenter(newV,getCenter(centV));
			  setRadius(newV,rad);
			  setAim(newV,2*Math.PI);
			  
			  // create last new interior vertex
			  newV=nodeCount;
			  kData[newV]=new KData();
			  kData[newV].num=5;
			  setBdryFlag(newV,0);
			  kData[newV].flower=new int[6];
			  kData[newV].flower[0]=kData[newV].flower[5]=centV;
			  kData[newV].flower[1]=nodeCount-1;
			  kData[newV].flower[2]=kData[centV].flower[countFaces(centV)-1];
			  kData[newV].flower[3]=upB;
			  kData[newV].flower[4]=newUp;
			  if (overlapStatus) { 
				  kData[newV].invDist=new double[6];
				  for (int k=0;k<=5;k++)
					  set_single_invDist(newV,kData[newV].flower[k],1.0);
			  }
			  rData[newV]=new RData();
			  setCenter(newV,getCenter(centV));
			  setRadius(newV,rad);
			  setAim(newV,2*Math.PI);
			  
			  // create rest of new interiors
			  for (int j=1;j<(centNum-1);j++) {
				  newV=origCount+3+j;
				  int []newflower=new int[6];
				  newflower[0]=newflower[5]=centV;
				  newflower[1]=newV-1;
				  newflower[2]=kData[centV].flower[j];
				  newflower[3]=kData[centV].flower[j+1];
				  newflower[4]=newV+1;
				  kData[newV]=new KData();
				  setBdryFlag(newV,0);
				  kData[newV].num=5;
				  kData[newV].flower=newflower;
				  if (overlapStatus) { 
					  kData[newV].invDist=new double[6];
					  for (int k=0;k<=5;k++)
						  set_single_invDist(newDown,kData[newDown].flower[k],1.0);
				  }
				  rData[newV]=new RData();
				  setCenter(newV,getCenter(centV));
				  setRadius(newV,rad);
				  setAim(newV,2*Math.PI);
			  }

			  // fix up downB
			  kData[downB].flower[countFaces(downB)]=newDown;
			  if (overlapStatus)
				  set_single_invDist(downB,kData[downB].flower[countFaces(downB)],1.0);
			  insert_petal(downB,countFaces(downB),origCount+3);
			  
			  // fix up upB
			  kData[upB].flower[0]=newUp;
			  if (overlapStatus)
				  set_single_invDist(upB,kData[upB].flower[0],1.0);
			  insert_petal(upB,1,nodeCount);
			  
			  // fix up rest of original nghbs to centV
			  int lastNew=origCount+2;
			  int nextNew=origCount+3;
			  for (int j=1;j<centNum;j++) {  
				  nextNew++;
				  lastNew++;
				  int v=kData[centV].flower[j];
				  int indx=nghb(v,centV);
				  kData[v].flower[indx]=lastNew;
				  if (overlapStatus)
					  set_single_invDist(v,kData[v].flower[indx],1.0);
				  if (indx==0 && !isBdry(v)) {
					  kData[v].flower[countFaces(v)]=lastNew;
					  if (overlapStatus)
						  set_single_invDist(v,kData[v].flower[countFaces(v)],1.0);
				  }
				  insert_petal(v,indx,nextNew);
			  }
			  
			  // fix centV
			  kData[centV].flower=newCentFlower;
			  kData[centV].num++;
			  
			  if (overlapStatus) { // any overlaps are lost
				  kData[centV].invDist=new double[countFaces(centV)+1];
				  for (int k=0;k<=countFaces(centV);k++)
					  set_single_invDist(centV,kData[centV].flower[k],1.0);
			  }
		  }
		  return nodeCount;
	  }
	  
	  /**
	   * Add deg-4 (resp., deg-3) vertex to interior (resp., bdry)
	   * edge {v,w}. Called, eg, from "add_edge". Return 0 if {v,w}
	   * is not an edge or on error. 
	   * NOTE: 'flist' and 'xyzpoint' are invalidated; calling 
	   * routine must reset combinatorics.
	   * @param v int
	   * @param w int 
	   * @return int, new vertex number or 0 
	  */
	  public int split_edge(int v, int w) {
		double[] newol;

		// legitimate edge?
		if (nghb(v, w) < 0 || nghb(w, v) < 0) 
			return 0;
		
		// Neighbors on boundary? arrange orientation <v,w>
		int bflag = 0;
		if (isBdry(v) && isBdry(w)) {
			if (kData[v].flower[0]==w)
				bflag = 1;
			else if (kData[w].flower[0] == v) { // swap v, w
				bflag = 1;
				v = w;
				w = kData[v].flower[0];
			}
		}
		
		// set up variables
		int ind_vw=nghb(v,w);
		int ind_wv=nghb(w,v);
		// vert on left of <v,w>
		int lv = kData[v].flower[ind_vw+1];
		
		// vert on right ow <v,w>
		int rv=0;
		if (bflag == 0)
			rv = kData[w].flower[ind_wv+1];
		
		// new vertex
		alloc_pack_space(nodeCount+10,true);
		int newV = ++nodeCount;
		kData[newV].flower = new int[5-2*bflag];
		if (overlapStatus)
			kData[newV].invDist = new double[5-2*bflag];
		
		// if boundary edge
		if (bflag != 0) {
			kData[newV].num = 2;
			kData[newV].flower[0] = w;
			kData[newV].flower[1] = lv;
			kData[newV].flower[2] = v;
			if (overlapStatus) {
				set_single_invDist(newV,kData[newV].flower[0],1.0);
				set_single_invDist(newV,kData[newV].flower[1],1.0);
				set_single_invDist(newV,kData[newV].flower[2],1.0);
			}
			setBdryFlag(newV,1);
			setVertMark(newV,0);
			setCircleColor(newV,ColorUtil.getFGColor());
			setRadius(newV,getRadius(v));
			setAim(newV,-0.1);
		} 
		
		// else interior edge
		else {
			kData[newV].num = 4;
			kData[newV].flower[0] = kData[newV].flower[4] = v;
			kData[newV].flower[1] = rv;
			kData[newV].flower[2] = w;
			kData[newV].flower[3] = lv;
			setBdryFlag(newV,0);
			if (overlapStatus) {
				set_single_invDist(newV,kData[newV].flower[0],1.0);
				set_single_invDist(newV,kData[newV].flower[1],1.0);
				set_single_invDist(newV,kData[newV].flower[2],1.0);
				set_single_invDist(newV,kData[newV].flower[3],1.0);
			}
			setVertMark(newV,0);
			setCircleColor(newV,ColorUtil.getFGColor());
			setRadius(newV,.5 * getRadius(v));
			setAim(newV,2.0 * Math.PI);
			// compute packed radius
			if (hes < 0)
				h_riffle_vert(newV);
			else if (hes == 0)
				e_riffle_vert(newV);
			// else s_riffle_vert(newval);
		}
		
		// set center
		if (hes <= 0) {
			Complex vz=getCenter(v);
			Complex wz=getCenter(w);
			setCenter(newV,new Complex((vz.x+wz.x) / 2.0,(vz.y+wz.y) / 2.0));
		} else {
			setCenter(newV,0.0,Math.PI);
		}
		
		// adjust nghb flowers
		
		// adjust lv
		int ind_vl = nghb(lv, v);
		int []newflower = new int[countFaces(lv) + 2];
		for (int j = 0; j <= ind_vl; j++)
			newflower[j] = kData[lv].flower[j];
		for (int j = countFaces(lv); j > ind_vl; j--)
			newflower[j + 1] = kData[lv].flower[j];
		newflower[ind_vl + 1] = newV;
		kData[lv].flower = newflower;
		if (overlapStatus) {
			newol = new double[countFaces(lv) + 2];
			for (int j = 0; j <= ind_vl; j++)
				newol[j] = getInvDist(lv,kData[lv].flower[j]);
			for (int j = countFaces(lv); j > ind_vl; j--)
				newol[j + 1] = getInvDist(lv,kData[lv].flower[j]);
			newol[ind_vl + 1] = 1.0;
			kData[lv].invDist = newol;
		}
		kData[lv].num++;
		
		// fix right, if there is one
		if (bflag == 0) {
			int ind_rw = nghb(rv, w);
			newflower = new int[countFaces(rv) + 2];
			for (int j = 0; j <= ind_rw; j++)
				newflower[j] = kData[rv].flower[j];
			for (int j = countFaces(rv); j > ind_rw; j--)
				newflower[j + 1] = kData[rv].flower[j];
			newflower[ind_rw + 1] = newV;
			kData[rv].flower = newflower;
			if (overlapStatus) {
				newol = new double[countFaces(rv) + 2];
				for (int j = 0; j <= ind_rw; j++)
					newol[j] = getInvDist(rv,kData[rv].flower[j]);
				for (int j = countFaces(rv); j > ind_rw; j--)
					newol[j + 1] = getInvDist(rv,kData[rv].flower[j]);
				newol[ind_rw + 1] = 1.0;
				kData[rv].invDist = newol;
			}
			kData[rv].num++;
		}
		
		// adjust v and w
		ind_vw = nghb(v, w);
		kData[v].flower[ind_vw] = newV;
		if (overlapStatus)
			set_single_invDist(v,kData[v].flower[ind_vw],1.0);
		if (ind_vw == 0 && !isBdry(v))
			kData[v].flower[countFaces(v)] = newV;
		ind_wv = nghb(w, v);
		kData[w].flower[ind_wv] = newV;
		if (overlapStatus)
			set_single_invDist(w,kData[w].flower[ind_wv],1.0);
		if (ind_wv == 0 && !isBdry(w))
			kData[w].flower[countFaces(w)] = newV;
		
		this.flist=null;
		this.xyzpoint=null;
		return newV;
	}

	  /**
	   * Called only by 'add_barys' and 'bary_refine'; put trivalent 
	   * vert in face f; data not updated between calls, so face data remains
	   * valid.
	   * @param f int, face index.
	   * @return 1 on success
	  */
	  public int add_barycenter(int f) {
		  return add_barycenter(f,0);
	  }
	  
	  /**
	   * Called only by 'add_barys' and 'bary_refine'; 
	   * put trivalent vert in face f; data not updated 
	   * between calls, so face data remains valid. 
	   * New barycenters get mark.
	   * @param f int, face index.
	   * @param mark int, desired makr for new barycenter
	   * @return 1 on success
	  */
	  public int add_barycenter(int f,int mark) {
	  	
	    // add new vertex 
	    int newval=nodeCount+1;
	    if (newval>sizeLimit)
	    	alloc_pack_space(newval+10,true);
	    nodeCount++;
	    kData[newval]=new KData();
	    rData[newval]=new RData();
	    kData[newval].num=3;
	    kData[newval].flower=new int[4];
	    kData[newval].flower[0]=kData[newval].flower[3]=faces[f].vert[0];
	    kData[newval].flower[1]=faces[f].vert[1];
	    kData[newval].flower[2]=faces[f].vert[2];
	    if (overlapStatus) {
	        kData[newval].invDist=new double[4];
	        set_single_invDist(newval,kData[newval].flower[0],1.0);
	        set_single_invDist(newval,kData[newval].flower[1],1.0);
	        set_single_invDist(newval,kData[newval].flower[2],1.0);
	        set_single_invDist(newval,kData[newval].flower[3],1.0);
	    }
	    setBdryFlag(newval,0);
	    setVertMark(newval,mark);
	    setCircleColor(newval,ColorUtil.getFGColor());
	    setRadius(newval,getRadius(faces[f].vert[0]));
	    setAim(newval,2.0*Math.PI);
	    // compute packed radius
	    if (hes<0) h_riffle_vert(newval);
	    else if (hes==0) e_riffle_vert(newval);
//	    else e_riffle_vert(newval);
	    if (hes<=0) {
	    	Complex z0=getCenter(faces[f].vert[0]);
	    	Complex z1=getCenter(faces[f].vert[1]);
	    	Complex z2=getCenter(faces[f].vert[2]);
	    	setCenter(newval,(z0.x+z1.x+z2.x)/3.0,(z0.y+z1.y+z2.y)/3.0);
	    }
	    else {
	    	setCenter(newval,0.0,Math.PI);
	    }
	    
	    // adjust nghb flowers
	    int v=faces[f].vert[0];
	    int u=faces[f].vert[1];
	    int w=faces[f].vert[2];
	    kData[v].add_petal_w(newval,u);
	    kData[u].add_petal_w(newval,w);
	    kData[w].add_petal_w(newval,v);
	    return 1;
	  }

	  /**
	   * Called only by 'add_face_t'; put triple of new vertices inside 
	   * face f; data not updated between calls, so face data remains
	   * valid.
	   * @param f int
	   * @return 1
	  */
	  public int add_face_triple(int f) {

		// add new vertex
	    int []newV=new int[3];
	    for (int i=0;i<3;i++) {
	    	newV[i]=nodeCount+1+i;
	    }
	    nodeCount += 3;
	    for (int i=0;i<3;i++) {
	    	kData[newV[i]]=new KData();
	    	rData[newV[i]]=new RData();
	    	setCenter(newV[i],getCenter(faces[f].vert[i]));
	    	kData[newV[i]].num=4;
	    	kData[newV[i]].flower=new int[5];
	    	kData[newV[i]].flower[0]=kData[newV[i]].flower[4]=faces[f].vert[i];
	    	kData[newV[i]].flower[1]=faces[f].vert[(i+1)%3];
	    	kData[newV[i]].flower[2]=newV[(i+1)%3];
	    	kData[newV[i]].flower[3]=newV[(i+2)%3];
	    	if (overlapStatus) {
	    		kData[newV[i]].invDist=new double[5];
	    		set_single_invDist(newV[i],kData[newV[i]].flower[0],1.0);
	    		set_single_invDist(newV[i],kData[newV[i]].flower[1],1.0);
	    		set_single_invDist(newV[i],kData[newV[i]].flower[2],1.0);
	    		set_single_invDist(newV[i],kData[newV[i]].flower[3],1.0);
	    		set_single_invDist(newV[i],kData[newV[i]].flower[4],1.0);
	    	}
	    	setBdryFlag(newV[i],0);
	    	setVertMark(newV[i],0);
	    	setCircleColor(newV[i],ColorUtil.getFGColor());
	    	setRadius(newV[i],getRadius(faces[f].vert[i]));
	    	setAim(newV[i],2.0*Math.PI);
	    }
	    // adjust nghb flowers 
	    for (int ii=0;ii<3;ii++) {
	    	int v=faces[f].vert[ii];
	    	int w=faces[f].vert[(ii+1)%3];
	    	int indx=nghb(v,w);
	    	int []newflower=new int[countFaces(v)+3];
	    	for (int j=0;j<=indx;j++) newflower[j]=kData[v].flower[j];
	    	for (int j=countFaces(v);j>indx;j--)
	    		newflower[j+2]=kData[v].flower[j];
	    	newflower[indx+1]=newV[ii];
	    	newflower[indx+2]=newV[(ii+2)%3];
	    	kData[v].flower=newflower;
	    	if (overlapStatus) {
	    		double []newol=new double[countFaces(v)+3];
	    		for (int j=0;j<=indx;j++) newol[j]=getInvDist(v,kData[v].flower[j]);
	    		for (int j=countFaces(v);j>indx;j--)
	    			newol[j+2]=getInvDist(v,kData[v].flower[j]);
	    		newol[indx+1]=1.0;
	    		newol[indx+2]=1.0;
	    		kData[v].invDist=newol;
	    	}
	    	kData[v].num +=2;
	    }
	    return 1;
	  }

	  /**
	 * Replace complex by one of reverse orientation; calling routine
	 * handles updates of combinatorics
	 * @return 1
	 */
	public int reverse_orient() {
		int num;
		KData newKData = null;
		for (int i = 1; i <= nodeCount; i++) {
			newKData = kData[i].clone();
			num = newKData.num;
			newKData.flower = new int[num + 1];
			for (int j = 0; j <= num; j++)
				// change orientation
				newKData.flower[j] = kData[i].flower[num - j];
			if (kData[i].invDist != null) {
				newKData.invDist = new double[num + 1];
				for (int j = 0; j <= num; j++)
					newKData.invDist[j] = getInvDist(i,kData[i].flower[num - j]);
			}
			kData[i] = newKData;
		}
		return 1;
	}
	  
	  /**
	   * Report the log of ratio width/height of a eucl packing, given the four
	   * corner vertices in counterclockwise order, upper-left first. Throw
	   * DataExceptin on error or if off from rectangular by more than 5%. 
	   * The result is left-right length divided by top-bottom height.
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
	   * If 'mark' is true, store in 'kData[].mark', else just return
	   * the index of the last vertex. 'utilFlag' is used to pass
	   * seed info to 'label_generations'.
	   * @param mx int, if mx>0, stop at generation mx.
	   * @param seedlist @see NodeLink, list defined as first generation (1)
	   * @param mark boolean, if true, store generation as 'mark in 'KData'.
	   * @return int, index of last_vert (first encountered in max generation), 0 on error
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
		if ((list = label_generations(mx, uP)) == null)
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
	   * Return n "antipodal" vertices, starting with given list.
	   * That is, given {v1, v2, ...} as first, inductively choose 
	   * some vj which is furthest comb distance from all previous
	   * (namely, last encountered with max distance) until we have n.
	   * @param ants NodeLink; may be empty, then start with max vert index.
	   * @param N int, number we want, (including ants)
	   * @return NodeLink, v1,...,vn; null on error.
	   * NOTE: if ants has N elements, return these.
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

		  for (int j=1;j<=nodeCount;j++) {
			  kData[j].utilFlag=0;
		  }

		  // start with what we were given
		  for (int j=0;j<ants.size();j++) {
			  int w=ants.get(j);
			  antipods.add(w);
			  kData[w].utilFlag=1;
		  }
		  
		  UtilPacket uP=new UtilPacket();
		  for (int k=ants.size();k<N;k++) {
			  label_generations(-1,uP);
			  if (uP.value==0.0) { // error: no verts of higher gen found??
				  throw new CombException("ran out of vertices");
			  }
			  antipods.add(uP.rtnFlag);
			  kData[uP.rtnFlag].utilFlag=1;
		  }
		  return antipods;
	  }

	  /** 
	   * Return an int array with the generations of verts, 
	   * generation "1" being those v with 'util' non-zero. 
	   * 'util' does not get changed during this method. Additional
	   * info is returned in 'uP'.  
	   * @param max int, if max>0, then stop at last vert with generation = max.
	   * @param uP UtilPacket; instantiated by calling routine: returns last 
	   *      vertex as 'uP.rtnFlag' and
	   *      the count of vertices having non-zero generation as 'uP.value'.
	   * @return int[], int[u]=generation of u; return null on error
	   */
	public int[] label_generations(int max, UtilPacket uP) {
		int last_vert = nodeCount;
		int gen_count = 2;
		int count = 0;

		int[] final_list = new int[nodeCount + 1];
		NodeLink genlist = new NodeLink(this);

		// first generation identified by nonzero utilFlag's
		for (int i = 1; i <= nodeCount; i++)
			if (getVertUtil(i) != 0) {
				final_list[i] = 1;
				count++;
				genlist.add(i);
				last_vert = i;
			}
		int n = genlist.size();
		// none/all vertices as seeds?
		if (n == 0 || n == nodeCount)
			return null;

		boolean hits = true;
		int j=0;
		while (hits && genlist.size() > 0 && (max <= 0 || gen_count <= max)) {
			hits = false;
			NodeLink vertlist = genlist; // process old list
			genlist = new NodeLink(this); // start new list
			do {
				int v = vertlist.remove(0);
				int[] flower=getFlower(v);
				int num=countFaces(v);
				for (int i = 0; i <= num; i++)
					if (final_list[(j = flower[i])] == 0) {
						final_list[j] = gen_count;
						count++;
						last_vert = j;
						genlist.add(j);
						hits = true;
					}
			} while (vertlist.size() > 0);
			gen_count++;
		}
		uP.rtnFlag = last_vert;
		uP.value = (double) count;
		return final_list;
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
	   * @param greens []int, stop when greens <0
	   * @param max int stop here if max>0.
	   * @param far boolean
	   * @return int[] containing generations.
	  */
	public int[] label_seed_generations(int seed, int[] greens, int max,
			boolean far) {
		int count = 0;
		int far_vert = seed;

		if (!status || seed < 1 || seed > nodeCount
				|| (greens != null && greens.length > seed && greens[seed] < 0))
			return null;

		int[] final_list = new int[nodeCount + 1];
		VertList genlist = new VertList();
		for (int i = 1; i <= nodeCount; i++)
			kData[i].utilFlag = 0;
		if (greens != null)
			for (int i = 1; i <= nodeCount; i++)
				if (greens[i] < 0)
					kData[i].utilFlag = -1;
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
		while (hits && genlist != null
				&& (max <= 0 || gen_count <= max || bdry_hits)) {
			hits = false;
			VertList vertlist = genlist;
			VertList gtrace;
			genlist = gtrace = new VertList();
			do {
				int v = vertlist.v;
				int j;
				for (int i = 0; i <= countFaces(v); i++)
					if (final_list[(j = kData[v].flower[i])] == 0
							&& kData[j].utilFlag == 0) {
						final_list[j] = gen_count;
						count++;
						if (!bdry_hits
								&& (isBdry(j) || kData[j].utilFlag != 0)) {
							bdry_hits = true;
							far_vert = j;
						}
						gtrace = gtrace.next = new VertList();
						gtrace.v = j;
						hits = true;
						last_marked = j;
					}
				vertlist = vertlist.next;
			} while (vertlist != null);
			genlist = genlist.next; // first position was empty
			gen_count++;
		}
		if (!bdry_hits)
			far_vert = last_marked; /*
									 * no bdry or green vert found, return vert
									 * of largest generation
									 */
		util_A = far_vert;
		util_B = count;
		return final_list;
	}

	  /**
	   * Apply side-pairing Mobius having the specified 'SideDescription.label'. 
	   * Return 'index' on success, zero on failure.
	   * @param pairLabel String
	   * @return int, index on success, 0 on error.
	   */
	  public int apply_pair_mobius(String pairLabel) {
		  int count=0;
		  if (sidePairs==null || sidePairs.size()==0) return 0;
		  Iterator<SideDescription> sides=sidePairs.iterator();
		  SideDescription edge=null;
		  while (sides.hasNext()) {
			  edge=(SideDescription)sides.next();
			  if (edge.label.equals(pairLabel)) {
				  // apply to whole packing, plus redchain, but not to side-pairings
				  apply_Mobius(edge.mob, new NodeLink(this,"a"),true,true,false);
				  count++;
			  }
		  }
		  return count;
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
	   * Apply Mobius Mob (oriented), or inverse (!oriented) to
	   * specified list of circles. If 'red_flag' (default),
	   * adjust selected redchain circle centers; if sp_flag is 
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
	    Iterator<Integer> vlist=vertlist.iterator();
	    CircleSimple sc=new CircleSimple(true);

	    if (packDCEL!=null) {
	    	// record 'vertlist' in 'vutil'
	    	for (int v=1;v<=nodeCount;v++) 
	    		packDCEL.vertices[v].vutil=0;
		    while (vlist.hasNext()) {
		    	packDCEL.vertices[vlist.next()].vutil=1;
		    }
		    
		    // apply to 'vData'
	    	for (int v=1;v<=nodeCount;v++) {
	    		if (packDCEL.vertices[v].vutil==1) {
	    			CircleSimple circle=vData[v].getCircleSimple();
	    			count += Mobius.mobius_of_circle(Mob,hes,circle,
		 	  	       sc,oriented);
	    			vData[v].center=sc.center;
	    			vData[v].rad=sc.rad;
	    		}
	    	}
	    	
	    	// apply to selected vertices in red chain
	    	if (packDCEL.redChain!=null && red_flag) {
	    		RedHEdge rtrace=packDCEL.redChain;
	    		do {
	    			if (rtrace.myEdge.origin.vutil==1) {
		    			CircleSimple circle=rtrace.getCircleSimple();
		    			count += Mobius.mobius_of_circle(Mob,hes,circle,
			 	  	       sc,oriented);
	    				rtrace.setCenter(sc.center);
	    				rtrace.setRadius(sc.rad);
	    			}
    				rtrace=rtrace.nextRed;
	    		} while (rtrace!=packDCEL.redChain);

	    		// update the side-pairing (not for spherical)
	    		if (hes<=0 && sp_flag)
	    			update_pair_mob();
	    	}
	    	return count;
	    }

	    // traditional packing
	    while (vlist.hasNext()) {
	        int v=(Integer)vlist.next();
	        
	        count += Mobius.mobius_of_circle(Mob,hes,getCenter(v),getRadius(v),
	 	  	       sc,oriented);
	        Complex Z=new Complex(sc.center);
	        double R=sc.rad;
	        if (hes==0 && R<0) R=Math.abs(R);
	        /* if (debug && (Double.isNaN(Z.re) 
	  	      || Double.isnan(Z.im) || Double.isNaN(R))) 
	  	{
	  	  flashError("bad data "+v);
	  	}
	        */
	        setCenter(v,Z);
	        setRadius(v,R);
	    }

		// if 'red_flag', adjust centers/radii for redlist/redchain
		// NOTE: not all 'RedEdge's are in 'redChain', so need two passes
		if (red_flag) {
			double R;
			
			// first adjust all the 'RedEdge's 
			if (firstRedEdge!=null) {
				RedEdge rtrack=firstRedEdge;
				boolean keepon=true;
				while (rtrack!=firstRedEdge || keepon) {
					keepon=false;
					Mobius.mobius_of_circle(Mob, hes, rtrack.center, rtrack.rad, sc, oriented);
					R = sc.rad;
					if (hes == 0 && R < 0)
						R = Math.abs(R);
					rtrack.rad = R;
					rtrack.center = new Complex(sc.center);
					rtrack = rtrack.nextRed;
				}
			}
			
			// then go through 'redChain', but only adjust 'redList' elements 
			if (redChain != null) {
				boolean keepon = true;
				RedList rtrace = redChain;
				while (rtrace != redChain || keepon) {
					keepon = false;
					
					// don't do again if object is 'RedEdge'
					if (!(rtrace instanceof RedEdge)) {
						// note that centers may not be set
						try {
							Mobius.mobius_of_circle(Mob, hes, rtrace.center, rtrace.rad, sc, oriented);
							R = sc.rad;
							if (hes == 0 && R < 0)
								R = Math.abs(R);
							rtrace.rad = R;
							rtrace.center = new Complex(sc.center);
						} catch(Exception ex) {}
					}
					rtrace = rtrace.next;
				}
			}
		}
	    
	    // fix side-pairing mobius transforms also 
	    // TODO: side-pairing transforms not yet implemented on sphere
	    if (hes<=0 && red_flag && sp_flag) {
	    	try {
	    		update_pair_mob();
	    	} catch(MobException mex) {
	    		flashError("error in updating side-pairings.");
	    	}
	    }
	    return count;
	  } 

	  /** 
	   * Connect all nodes of bdry containing v to a new node (which 
	   * becomes interior). 
	   * @param v int
	   * @return 1 on success 
	   */
	  public int ideal_bdry_node(int v) {
		  int loccount=0;
		  if (packDCEL!=null) {
			  loccount=RawDCEL.addIdeal_raw(packDCEL, v, v);
			  if (loccount==0) 
				  throw new CombException("add failed");
			  return loccount;
		  }

		  // traditional
		  loccount=ideal_bdry_node(v,v);
		  if (loccount!=0) {
			  setCombinatorics();
			  fillcurves();
		  }
		  return loccount;
	  }

	  /** 
	   * If v and w are on the same bdry component, add a new
	   * vertex connected to all nodes of that component from v 
	   * to w. If v==w, or if w is upstream neighbor of v, then 
	   * do the whole component, in which case the new vertex
	   * becomes interior. Return 0 on error. Calling routine must
	   * update combinatorics.
	   * @param v int
	   * @param w int
	   * @return int, 0 on error, count on success
	   */
	  public int ideal_bdry_node(int v,int w) {
		  
		  if (!isBdry(v) || !isBdry(w))
			  return 0;
		  
		  // dcel version (this routine only called with traditional)
		  if (packDCEL!=null) 
			  return RawDCEL.addIdeal_raw(packDCEL,v,w);
		  
		  // else traditional packing
		  int nextvert,newnode,numb;
		  int []newflower;
		  double []newoverlaps;

		  // check suitability
		  if (!isBdry(v) || !isBdry(w)) 
			  throw new ParserException(v+" and "+w+" are not both boundary vertices.");
		  if (kData[w].flower[0]==v) w=v; 
		  int count=1;
		  if (w!=v) { // error if w not on same component
			  nextvert=v;
			  while ((nextvert=kData[nextvert].flower[0])!=w) {
				  if (nextvert==v) return 0; // didn't find w
				  count++;
	  			}
	  		}
	  		else { // full component
	  			nextvert=v;
	  			while ((nextvert=kData[nextvert].flower[0])!=v) {
	  				count++;
	  			}
	  			if (count<3) throw new CombException("Boundary component is too short"); // too short
	  		}
	  	
		  // may have to upsize the packing
		  if ((nodeCount+1) > sizeLimit && alloc_pack_space(nodeCount+1,true)==0) {
			  throw new CombException("Space allocation problem with adding vertex.");
		  }

		  // create and fix data for 'newnode'; 'count' is its number of faces 
		  newnode=nodeCount+1;
		  kData[newnode].num=count;
		  newflower=new int[count+1];
		  newflower[0]=w;
		  int pvert=w;
		  int tick=1;
		  while ((pvert=kData[pvert].flower[countFaces(pvert)])!=v) {
			  newflower[tick++]=pvert;
		  }
		  newflower[tick]=v;
		  kData[newnode].flower=newflower;
		  setBdryFlag(newnode,1);
		  setAim(newnode,-1.0);
		  if (w==v) {
			  setBdryFlag(newnode,0);
			  setAim(newnode,2.0*Math.PI);
		  }
	    
		  kData[newnode].plotFlag=1;
		  setVertMark(newnode,0);
		  setCircleColor(newnode,ColorUtil.getFGColor());
		  if (overlapStatus) {
		      kData[newnode].invDist=new double[count+1];
		      for (int j=0;j<=count;j++) 
		    	  set_single_invDist(newnode,kData[newnode].flower[j],1.0);
		  }
		  setRadius(newnode,0.5);
		  setCenter(newnode,0.0,0.0);
		  setRadius(newnode,0.5);
		  if (hes>0) { // sphere: newnode is southern hemisphere.
			  setCenter(newnode,0.0,Math.PI);
			  setRadius(newnode,Math.PI/2.0);
		  }
	    
		  // have to capture next downstream vert
		  nextvert=kData[v].flower[0];
	    
		  // fix v and w
		  if (v==w) { // v becomes interior
			  numb=countFaces(v)+2;
			  newflower=new int[numb+1];
			  for (int j=0;j<=(numb-2);j++)
				  newflower[j]=kData[v].flower[j];
			  newflower[numb-1]=newnode;
			  newflower[numb]=newflower[0];
			  kData[v].flower=newflower;
			  if (overlapStatus) {
				  newoverlaps=new double[numb+1];
				  for (int j=0;j<=(numb-2);j++) 
					  newoverlaps[j]=getInvDist(v,kData[v].flower[j]);
				  newoverlaps[numb-1]=newoverlaps[numb]=1.0;
			      kData[v].invDist=newoverlaps;
			  }
			  kData[v].num=numb;
			  setBdryFlag(v,0);
			  setAim(v,2.0*Math.PI);
		  }
		  else { // v and w are bdry
	    	
			  // fix v
			  numb=countFaces(v)+1;
			  newflower=new int[numb+1];
			  for (int j=0;j<numb;j++) 
				  newflower[j+1]=kData[v].flower[j];
			  newflower[0]=newnode;
			  kData[v].flower=newflower;
			  kData[v].num=numb;
			  if (overlapStatus) {
			      newoverlaps=new double[numb+1];
			      for (int j=0;j<=(numb-1);j++) 
			    	  newoverlaps[j+1]=getInvDist(v,kData[v].flower[j]);
			      newoverlaps[0]=1.0;
			      kData[v].invDist=newoverlaps;
			  }
	    	
			  // fix w
			  numb=countFaces(w)+1;
			  newflower=new int[numb+1];
			  for (int j=0;j<numb;j++) 
				  newflower[j]=kData[w].flower[j];
			  newflower[numb]=newnode;
			  kData[w].flower=newflower;
			  kData[w].num=numb;
			  if (overlapStatus) {
			      newoverlaps=new double[numb+1];
			      for (int j=0;j<=(numb-1);j++) 
			    	  newoverlaps[j]=getInvDist(w,kData[w].flower[j]);
			      newoverlaps[numb]=1.0;
			      kData[w].invDist=newoverlaps;
			  }
		  }

		  // fix up bdry between v, w, ie., starting
		  //   with 'nextvert'. There are interior 

		  while (nextvert!=w) {
			  int newnext=kData[nextvert].flower[0];
			  numb=countFaces(nextvert)+2;
			  newflower=new int[numb+1];
			  for (int i=0;i<=(numb-2);i++) 
				  newflower[i]=kData[nextvert].flower[i];
			  newflower[numb-1]=newnode;
			  newflower[numb]=newflower[0];
			  kData[nextvert].flower=newflower;
			  kData[nextvert].num=numb;
			  setBdryFlag(nextvert,0);
			  setAim(nextvert,2.0*Math.PI);
			  if (overlapStatus) {
				  newoverlaps=new double[numb+1];
				  for (int j=0;j<=(numb-2);j++)
					  newoverlaps[j]=getInvDist(nextvert,kData[nextvert].flower[j]);
				  newoverlaps[numb-1]=1.0;
				  newoverlaps[numb]=newoverlaps[0];
				  kData[nextvert].invDist=newoverlaps;
			  }
			  nextvert=newnext;
		  }

		  nodeCount++;
		  return 1;
	  }

	  /**
	   * adds a vertex which connects up to all vertices on the boundary
	   * component of vertices in given list. Combinatorics are reset.
	   * Lose any 'xyzpoint' info.
	   * @param vertlist NodeLink
	   * @return int count
	   */
	  public int add_ideal(NodeLink vertlist) {
	    int count=0,v;

	    if (vertlist==null || vertlist.size()==0) 
	    	return 0;

	    Iterator<Integer> vlist=vertlist.iterator();

	    while(vlist.hasNext() && isBdry((v=(Integer)vlist.next()))) {
	    	if (packDCEL!=null) {
	    		int loccount=RawDCEL.addIdeal_raw(packDCEL, v, v);
	    		if (loccount==0) 
	    			throw new CombException("add failed");
	    		count++;
	    	}
	    	else
	    		count += ideal_bdry_node(v);
	    }
	    if (count==0) 
	    	return 0;
	    xyzpoint=null;
	    if (packDCEL!=null) {
	    	packDCEL.fixDCEL_raw(this); // packDCEL.p.getCenter(300);
	    }
	    else 
	    	setCombinatorics();
	    set_aim_default();
	    fillcurves();
	    

    	if (1==2) {
        	DualGraph.printGraph(packDCEL.computeOrder);
    		DCELdebug.visualDualEdges(packDCEL,-1,packDCEL.computeOrder);
    	}
	    
	    return count;
	  }
	  
	  /**
	   * Given a bdry vert v, attach ideal face if the boundary component
	   * containing v has 3 edges. This is opposite of puncture_face.
	   * Calling routine updates combiinatorics.
	   * @param v int, vertex
	   * @return v or 0 on error or illegal vert
	   */
	  public int add_ideal_face(int v) {
		  if (!isBdry(v))
			  return 0;
		  int []vert=new int[3];
		  vert[0]=kData[v].flower[0];
		  vert[1]=kData[vert[0]].flower[0];
		  vert[2]=kData[vert[1]].flower[0];
		  if (vert[2]!=v) // bdry component is suitable, must have 3 edges
			  return 0;
		  
		  // fix all the flowers
		  for (int j=0;j<3;j++) {
			  kData[vert[j]].num++;
			  int []newflower=new int[countFaces(vert[j])+1];
			  for (int k=0;k<countFaces(vert[j]);k++)
				  newflower[k]=kData[vert[j]].flower[k];
			  newflower[countFaces(vert[j])]=newflower[0];
			  kData[vert[j]].flower=newflower;
			  setBdryFlag(vert[j],0);
		  }
		  
		  return v;
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
	        lam=1.0/Math.sqrt(rData[alpha].rad);
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
  	  	if (packDCEL!=null) {
  	  		try { 
  	  			RawDCEL.wipeRedChain(packDCEL,packDCEL.redChain);
  	  			RawDCEL.addBary_raw(packDCEL,packDCEL.idealFaces[1],false);
  	  		} catch(Exception ex) {
  	  			throw new CombException("'proj' dcel error");
  	  		}
  	  		packDCEL.fixDCEL_raw(this);
  	  	}
  	  	else {
  	  		Integer b=Integer.valueOf(bdryStarts[1]);
  	  		NodeLink vlist=new NodeLink(this,b.toString());
  	  		add_ideal(vlist);
  	  	}
	    setCenter(nodeCount,new Complex(0.0,Math.PI));
	    setRadius(nodeCount,Math.asin(2.0*lam/(1.0+lam*lam)));
	    if (ratio>0.0 && Math.abs(ratio-1.0)>Mobius.MOB_TOLER) {
	        // adjust for specified ratio when on sphere 
	        factor=new Complex(0.0);
	        Mobius Mob=Mobius.NS_mobius(getCenter(alpha),getCenter(nodeCount),
	  	      MathComplex.ID,getRadius(alpha),getRadius(nodeCount),0.0,
	  	      ratio);
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
				  qackData=PackControl.cpScreens[qnum].getPackData();
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
				  qackData=PackControl.cpScreens[pnum].getPackData();
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
				  Complex z0=getCenter(faces[f].vert[0]);
				  Complex z1=getCenter(faces[f].vert[1]);
				  Complex z2=getCenter(faces[f].vert[2]);
				  CircleSimple sc=null;
				  if (hes<0) { 
					  sc=HyperbolicMath.hyp_tang_incircle(z0,z1,z2,
							  getRadius(faces[f].vert[0]),getRadius(faces[f].vert[1]),
							  getRadius(faces[f].vert[2]));
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
					  try { // pack number after space/
						  qnum=Integer.parseInt(items.elementAt(0)); // might be a space
						  items.remove(0);
					  } catch (Exception ex1) {
						  return 0;
					  }
				  }
				  if (qnum>=0 && qnum<CPBase.NUM_PACKS) 
					  qackData=PackControl.cpScreens[qnum].getPackData();
				  else throw new ParserException("Pack number, "+qnum+", out of range");
				  if (hes!=qackData.hes) {
					  throw new ParserException("set_color: area comparision only if  both hyp or both eucl.");
				  }
				  if (hes<0) return ColorCoding.h_compare_area(this,qackData);
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
					  qackData=PackControl.cpScreens[pnum].getPackData();
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
					  qackData=PackControl.cpScreens[qnum].getPackData();
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
			  int []vts=faces[f].vert;
			  int num=faces[f].vertCount;
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
	    	  int i=faces[f].vert[0];
	    	  int j=faces[f].vert[1];
	    	  int k=faces[f].vert[2];
	    	  if (i<=xyzcount && j<=xyzcount && k<=xyzcount) {
	    		  // yes, we have xyz locations for these 
	    		  
	    		  // need euclidean side lengths A, B, C
	    		  Point3D []pt=new Point3D[3];
	    		  if (hes>0) { // sph
	    			  pt[0]=new Point3D(getCenter(i));
	    			  pt[1]=new Point3D(getCenter(j));
	    			  pt[2]=new Point3D(getCenter(k));
	    		  }
	    		  if (hes<0) { // hyp
	    			  CircleSimple sc=null;
	    			  sc=HyperbolicMath.h_to_e_data(getCenter(i),
	    					  getRadius(i));
	    			  pt[0]=new Point3D(sc.center);
	    			  sc=HyperbolicMath.h_to_e_data(getCenter(j),
	    					  getRadius(j));
	    			  pt[1]=new Point3D(sc.center);
	    			  sc=HyperbolicMath.h_to_e_data(getCenter(k),
	    					  getRadius(k));
	    			  pt[2]=new Point3D(sc.center);
	    		  }
	    		  else {
	    			  Complex z=getCenter(i);
	    			  pt[0]=new Point3D(z.x,z.y,0.0);
	    			  z=getCenter(j);
	    			  pt[1]=new Point3D(z.x,z.y,0.0);
	    			  z=getCenter(k);
	    			  pt[2]=new Point3D(z.x,z.y,0.0);
	    		  }
	    		  
	    		  // eucl edge lengths in packing
	    		  A=Point3D.distance(pt[0],pt[1]);
	    		  B=Point3D.distance(pt[1],pt[2]);
	    		  C=Point3D.distance(pt[2],pt[0]);

	    		  // eucl edge lengths in xyz data
	    		  a=Math.sqrt(
	    		      (xyz_list[i].x-xyz_list[j].x)*(xyz_list[i].x-xyz_list[j].x)
	    		      + (xyz_list[i].y-xyz_list[j].y)*(xyz_list[i].y-xyz_list[j].y)
	    		      + (xyz_list[i].z-xyz_list[j].z)*(xyz_list[i].z-xyz_list[j].z));
	    		  b=Math.sqrt(
	    		      (xyz_list[k].x-xyz_list[j].x)*(xyz_list[k].x-xyz_list[j].x)
	    		      + (xyz_list[k].y-xyz_list[j].y)*(xyz_list[k].y-xyz_list[j].y)
	    		      + (xyz_list[k].z-xyz_list[j].z)*(xyz_list[k].z-xyz_list[j].z));
	    		  c=Math.sqrt(
	    		      (xyz_list[i].x-xyz_list[k].x)*(xyz_list[i].x-xyz_list[k].x)
	    		      + (xyz_list[i].y-xyz_list[k].y)*(xyz_list[i].y-xyz_list[k].y)
	    		      + (xyz_list[i].z-xyz_list[k].z)*(xyz_list[i].z-xyz_list[k].z));
	    		  
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
	   * a null 'CPScreen' and 'packNum' of 3. 'PackExtender's 
	   * are lost. If the new packing is to replace another, 
	   * the calling routine must handle interchange; 
	   * in particular, 'PackData' and 'CPScreen' point to 
	   * one another, so 'cpScreen' and 'cpScreen.packData' 
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
		  p.alloc_pack_space((nodeCount + 1),false);
		  p.nodeCount=nodeCount;
		  p.faceCount=faceCount;
		  p.hes=hes;
		  p.intrinsicGeom=intrinsicGeom;
		  p.locks=0;
		  p.fileName=fileName;
		  p.alpha=alpha;
		  p.gamma=gamma;
		  p.activeNode=activeNode;
		  p.packExtensions=new Vector<PackExtender>(2); // old are lost

		  if (packDCEL!=null) {
			  RedHEdge oldred=packDCEL.redChain;
			  PackDCEL pdcel=CombDCEL.cloneDCEL(packDCEL);
			  pdcel.fixDCEL_raw(p);
			  p.vData=new VData[sizeLimit+1];
			  for (int v=1;v<=nodeCount;v++) 
				  p.vData[v]=vData[v].clone(); // this.getCenter(v);
			  
			  // typically, if not a sphere, will have redChain
			  if (oldred!=null) {
				  RedHEdge newred=pdcel.redChain;
				  RedHEdge rhe=newred;
				  do {
					  rhe.setCenter(oldred.getCenter());
					  rhe.setRadius(oldred.getRadius());
					  rhe=rhe.nextRed;
					  oldred=oldred.nextRed;
				  } while (rhe!=newred);
			  }

		  }
		  
		  // traditional: copy rData and kData
		  else {
			  for (int v=1;v<=nodeCount;v++) {
				  p.kData[v]=kData[v].clone();
				  p.rData[v]=rData[v].clone();
			  }
		  }
		  
		  // copy tile data, if it exists
		  if (keepTD && tileData!=null && tileData.tileCount>0) {
			  p.tileData=tileData.copyMyTileData();
			  TileData.setPackings(p.tileData,p);
		  }
		  
		  // set the combinatorics
		  if (packDCEL==null) {
			  p.complex_count(false);
			  p.facedraworder(false);
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
		    		cpScreen.drawIndex(getCenter(n),n,msg_flag);
		    		count++;
		    	}
		    	return count;
		    }
		    while (list.hasNext()) {
		    	n=(Integer)list.next();
			    cpScreen.drawIndex(face_center(n),n,msg_flag);
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
				int jup = kData[bestj].flower[countFaces(bestj)];
				int jdown = kData[bestj].flower[0];
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
	 * For sphere, z is already a real (not apparent) point in spherical coords. 
	 * TODO: should develop hyperbolic formulae - maybe use
	 * paraboloid model and work in 3D. 
	 * @param z Complex
	 * @return FaceLink, null if none found
	*/
	public FaceLink tri_search(Complex z) {
	    FaceLink nodelist=new FaceLink(this);
	    for (int j=1;j<=faceCount;j++) {
		if (pt_in_tri(j,z)!=0) {
		    nodelist.add(j);
		}
	    }
	    return nodelist;
	}

	/** 
	 * Is complex number z in any triangles (any geometry)?
	 * @param f int, face index
	 * @param z Complex, complex pt
	 * @return 1 on true, 0 on false.
	*/
	public int pt_in_tri(int f, Complex z) {
		Complex[] ctr = new Complex[3];
		int[] fverts=getFaceVerts(f);

		ctr[0]=getCenter(fverts[0]);
		ctr[1]=getCenter(fverts[1]);
		ctr[2]=getCenter(fverts[2]);
		if (hes > 0) {
			if (SphericalMath.pt_in_sph_tri(z,ctr[0],ctr[1],ctr[2]))
				return 1;
			else
				return 0;
		}
	
		for (int k = 0; k <= 2; k++) {
			if (hes < 0) { // hypebolic case: use euclidean data
				CircleSimple sc = HyperbolicMath.h_to_e_data(
					ctr[k],getRadius(fverts[k]));
				ctr[k] = new Complex(sc.center);
			} 
			else
				ctr[k] = getCenter(fverts[k]);
		}
		if (EuclMath.pt_in_eucl_tri(z,ctr[0],ctr[1],ctr[2]))
			return 1;
		return 0;
	} 

	public static double row_col(double x,double y,double xa,double xb,
			double ya,double yb) {
		return (x*(ya-yb)-y*(xa-xb)+xa*yb-xb*ya);
	}

	/** 
	 * Record in kData element 'nextVert' the order in which circles
	 * are drawn when the packing is laid out. (Eg. want to be able to mark
	 * the first 500 verts which will be drawn.) 
	 * Caution: I don't do much maintenance or error checking on the 
	 * 'nextObject' info, so it should be updated immediately before use.
	 * @return int, generally, 'nodeCount' 
	*/
	public int vert_draw_order() {
		int nf,next,last,tick=1;
		int []uflag=new int[nodeCount+1];

		/* get started with three verts of first face */
		uflag[alpha]=tick++;
		nf=firstFace;
		next=faces[nf].vert[(faces[nf].indexFlag+1) % 3];
		kData[alpha].nextVert=next;
		last=next;
		uflag[last]=tick++;
		kData[last].nextVert=next=faces[nf].vert[(faces[nf].indexFlag+2) % 3];
		last=next;
		uflag[last]=tick++;
		nf=faces[nf].nextFace;

		/* continue through face drawing order, checking to avoid repeats */
		while (nf!=firstFace) {
			while (nf!=firstFace 
					&& uflag[(next=faces[nf].vert[(faces[nf].indexFlag+2) % 3])]!=0)
				nf=faces[nf].nextFace;
		        if (nf==firstFace) break; // done 
		        kData[last].nextVert=next;
		        last=next;
		        uflag[last]=tick++;
		        nf=faces[nf].nextFace;
		}
		kData[last].nextVert=alpha; // last vert points back to alpha 
		  
		return (tick-1); // in general, tick-1 should equal nodecount 
	} 
		
	/** 
	 * Routine to remove vertex from pack: This routine 
	 * assuming flowers pointing to v have already been 
	 * adjusted. It fixes indices and
	 * various lists. Calling routine will have to do 'complex_count', etc.
	*/
	public int delete_vert(int v) {
		for (int i=1;i<=nodeCount;i++)
			if (i!=v) 
				for (int k=0;k<=countFaces(i);k++)
					if (kData[i].flower[k]>v) kData[i].flower[k]--;
		kData[v].flower=null;
	    kData[v].invDist=null;
	    for (int i=v;i<nodeCount;i++) {
	        kData[i]=kData[i+1];
	        rData[i]=rData[i+1];
	    }
	    kData[nodeCount]=new KData(); // prevent pointing to same objects
	    rData[nodeCount]=new RData();
	    
	    nodeCount--;
	    if (activeNode>=v) {
	    	activeNode--;
	    	if (activeNode<1) activeNode=1;
	    }
	    if (alpha>v) alpha--;
	    if (gamma>v) gamma--;

	    // fix up any existing lists. 

	    flist=null; // indices invalidated, so clear list

	    if (vlist!=null && vlist.size()>0) {
	        Iterator<Integer> vlst=vlist.iterator();
	        NodeLink nvl=new NodeLink(this);
	        while (vlst.hasNext()) {
	        	int c=(Integer)vlst.next();
	        	if (c<v) nvl.add(c);
	        	else if (c>v) nvl.add(c-1);
	        }
	        if (nvl.size()>0) vlist=nvl;
	        else vlist=null;
	    }

	    if (elist!=null && elist.size()>0) {
	        Iterator<EdgeSimple> elst=elist.iterator();
	        EdgeLink nel=new EdgeLink(this);
	        while (elst.hasNext()) {
	        	EdgeSimple e=(EdgeSimple)elst.next();
	        	if (e.v!=v && e.w!=v) {
	        		if (e.v>v) e.v--;
	        		if (e.w>v) e.w--;
	        		nel.add(e);
	        	}
	        }
	        if (nel.size()>0) elist=nel;
	        else elist=null;
	    }

	    // vertex_map 
	    if (vertexMap!=null && vertexMap.size()>0) {
	    	Iterator<EdgeSimple> vm=vertexMap.iterator();
	        VertexMap nel=new VertexMap();
	        while (vm.hasNext()) {
	        	EdgeSimple e=(EdgeSimple)vm.next();
	        	if (e.v!=v) { // just check v entry
	        		if (e.v>v) e.v--;
	        		nel.add(e);
	        	}
	        }
	        if (nel.size()>0) vertexMap=nel;
	        else vertexMap=null;
	    }
	    return 1;
	  } 

	  /** 
	   * Remove one interior vert; must have at least 2 generations 
	   * of interior neighbors. Geometry is not changed; lists are 
	   * adjusted, but any face list data is tossed. Calling routine
	   * will have to do 'complex_count', etc.
	   * @param v int
	   * @return 1 on success, 0 if not suitable
	   */
	  public int puncture_vert(int v) {
		  if (packDCEL!=null) {
			  PackDCEL newDCEL=CombDCEL.d_puncture_vert(packDCEL, v);
			  if (newDCEL==null) {
				  throw new DCELException("DCEL puncture for "+v+" failed");
			  }
			  attachDCEL(newDCEL);
			  packDCEL.newOld=null;
			  return 1;
		  }
		  
		  // else non-DCEL case
		  int w;
		  for (int j=0;j<countFaces(v);j++) { // check suitability
	    	  // TODO: why was this needed?
//	    	  if (getNum(w=kData[v].flower[j])<4) return 0;
	    	  w=kData[v].flower[j];
	    	  for (int k=0;k<=countFaces(w);k++) {
	    		  int n=kData[w].flower[k];
	    		  if (kData[n].flower[0]!=kData[n].flower[countFaces(n)]) // bdry? 
	    			  return 0;
	    	  }
	      }
	    
	      // adjust neighbors' flowers and overlaps 
	      for (int j=0;j<countFaces(v);j++) {
	    	  double []newoverlaps=null;
	    	  w=kData[v].flower[j];
	    	  int start;
	    	  if ((start=nghb(w,v))<0) return 0; // data will be screwy 
	    	  int []newflower=new int[countFaces(w)-1];
	    	  if (kData[w].invDist!=null) 
	    		  newoverlaps=new double[countFaces(w)-1];
	    	  for (int k=0;k<countFaces(w)-1;k++) {
	    		  int indx=(start+1+k) % (countFaces(w));
	    		  newflower[k]=kData[w].flower[indx];
	    		  if (newoverlaps!=null)
	    			  newoverlaps[k]=getInvDist(w,kData[w].flower[indx]);
	    	  }
	    	  kData[w].num -= 2;
	    	  setBdryFlag(w,1);
	    	  setAim(v,-0.1);
	    	  kData[w].flower=newflower;
	    	  if (newoverlaps!=null) {
	    		  kData[w].invDist=newoverlaps;
	    	  }
	      }
	      delete_vert(v);
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
		  if (packDCEL!=null) {
			  PackDCEL newDCEL=CombDCEL.d_puncture_face(packDCEL, f);
			  if (newDCEL==null) {
				  throw new DCELException("DCEL puncturing face "+f+" failed");
			  }
			  attachDCEL(newDCEL);
			  packDCEL.newOld=null;
			  return 1;
		  }
		  
		  Face face=faces[f];
		  
		  // find faces next to boundary, then vertices next to these
		  flist= new FaceLink(this,"-Iv b");
		  NodeLink nlink=new NodeLink(this,"-If flist");
		  for (int k=0;k<3;k++) {
			  int v=face.vert[k];
			  if (isBdry(v) || nlink.containsV(v)>0) {
				  CirclePack.cpb.errMsg("face is too close to the bdry to puncture");
				  return 0;
			  }
		  }
		  flist=null; // wanted to empty 'flist' in any case.
		  
		  // fix flowers
		  for (int j=0;j<3;j++) {
			  int v=face.vert[j];
			  int indx=nghb(v,face.vert[(j+2)%3]);
			  int num=countFaces(v);
			  int []newflower=new int[num+1];
			  for (int kk=0;kk<num;kk++)
				  newflower[kk]=kData[v].flower[(kk+indx)%num];
			  kData[v].num--;
			  kData[v].flower=newflower;
			  setBdryFlag(v,1);
		  }
			  
		  return 1;
	  }
	  
	  /**
	   * We are operating on this packing. Each edge gets new vertex, 
	   * each face broken into 4 faces. Try to propagate old centers/radii,
	   * overlaps, schwarzians to new edges. Return 0 on failure.
	   * Can be iterated before fixing in dcel case.
	   * @param N int
	  */
	  public int hex_refine(int N) {
		  if (packDCEL!=null) {
			  if (N<1 || N>5) // at least 1, at most 5
				  N=1;
			  RawDCEL.hexRefine_raw(packDCEL,N);
			  VertexMap vrads=packDCEL.reapVUtil();
			  packDCEL.fixDCEL_raw(this);

			  Iterator<EdgeSimple> vis=vrads.iterator();
			  while (vis.hasNext()) {
				  EdgeSimple edge=vis.next();
				  if (edge.v!=0 && edge.v!=edge.w) { 
					  setRadius(edge.v,getRadius(edge.w));
					  setCenter(edge.v,getCenter(edge.w));
				  }
			  }
			  return 1; 
		  }
		  
		// else, old style
	    int count=0;
	    EdgeSimple []new_verts=null;

	    int Num=(faceCount)+(nodeCount)-(euler); // number of edges 
	    alloc_pack_space(nodeCount+Num+2,true);
	    
	    // we will build a new 'KData' array
	    KData []tempK=new KData[sizeLimit+1];
	    for(int j=1;j<=nodeCount;j++) {
	    	/* copy needed as changes made. Make space twice as 
		     * long to hold crossref data for vert_for_edge. */
	    	tempK[j]=kData[j].clone();
	    	tempK[j].flower=new int[tempK[j].num+2];
	    	rData[j].rad /=2.0; // half the radii
	    }
	    for (int j=nodeCount+1;j<=nodeCount+Num;j++) {
	    	tempK[j]=new KData();
	    	rData[j]=new RData();
	    }
	    
	    // Catalog edges {v,w} having v<w 
	    new_verts=new EdgeSimple[Num+2];
	    for (int v=1;v<nodeCount;v++) {
	    	int w;
	    	for (int k=0;k<(countFaces(v)+getBdryFlag(v));k++)
	    		if ((w=kData[v].flower[k])>v)
	    			new_verts[++count]=new EdgeSimple(v,w);
	    }
	    if (count!=Num) throw new CombException();

	    for (int v=1;v<=nodeCount;v++)
	    	tempK[v].flower=new int[countFaces(v)+1];
	    
	    // adjust flowers for the original vertices
	    for (int n=1;n<=Num;n++) {
	    	EdgeSimple e=new_verts[n];
	    	tempK[e.v].flower[nghb(e.v,e.w)]=nodeCount+n;
	    	tempK[e.w].flower[nghb(e.w,e.v)]=nodeCount+n;
	    }
	    for (int v=1;v<=nodeCount;v++) // take care of repeat for closed flowers
	    	if (tempK[v].bdryFlag==0) tempK[v].flower[tempK[v].num]=tempK[v].flower[0];

	    // build new flowers for the new vertices
	    for (int n=1;n<=Num;n++) {
	    	EdgeSimple e=new_verts[n];
    		int Nn=nodeCount+n; // the new index

    		// if the new vertex is on a boundary edge
	    	if ((isBdry(e.v) && kData[e.v].flower[0]==e.w)
	    			|| (isBdry(e.w) && kData[e.w].flower[0]==e.v)) {
	    		tempK[Nn].num=3;
	    		tempK[Nn].bdryFlag=1;
	    		tempK[Nn].flower=new int[4];
	    		if (overlapStatus) {
	    			tempK[Nn].invDist=new double[4];
	    			tempK[Nn].invDist[0]=tempK[Nn].invDist[3]=
	    					kData[e.v].invDist[nghb(e.v,e.w)];
	    			tempK[Nn].invDist[1]=tempK[Nn].invDist[2]=1.0;
	    		}
	    		if (kData[e.v].flower[0]==e.w) {
	    			tempK[Nn].flower[0]=e.w;
	    			tempK[Nn].flower[1]=tempK[e.w].flower[countFaces(e.w)-1];
	    			tempK[Nn].flower[2]=tempK[e.v].flower[1];
	    			tempK[Nn].flower[3]=e.v;
		    		setRadius(Nn,getRadius(e.v));
	    		}
	    		else {
	    			tempK[Nn].flower[0]=e.v;
	    			tempK[Nn].flower[1]=tempK[e.v].flower[countFaces(e.v)-1];
	    			tempK[Nn].flower[2]=tempK[e.w].flower[1];
	    			tempK[Nn].flower[3]=e.w;
		    		setRadius(Nn,getRadius(e.w));
	    		}
	    		setAim(Nn,-1.0);
	    	}

	    	// else new vertex is interior
	    	else {
	    		tempK[Nn].num=6;
	    		tempK[Nn].bdryFlag=0;
	    		tempK[Nn].flower=new int[7];
	    		// Note: new flower starts in direction of e.w 
	    		tempK[Nn].flower[0]=tempK[Nn].flower[6]=e.w;
	    		tempK[Nn].flower[3]=e.v;
	    		if (overlapStatus) {
	    			tempK[Nn].invDist=new double[7];
	    			for (int j=0;j<7;j++) tempK[Nn].invDist[j]=1.0;
	    			tempK[Nn].invDist[0]=tempK[Nn].invDist[3]=
	    				kData[e.v].invDist[nghb(e.v,e.w)];
	    		}
	    		// petals 1 and 5 are obtained from e.w
	    		int num_w=countFaces(e.w);
	    		int dir_wv=nghb(e.w,e.v);
	    		tempK[Nn].flower[5]=tempK[e.w].flower[dir_wv+1];
	    		if (isBdry(e.w))  // dir_wv is >0 and < num
	    			tempK[Nn].flower[1]=tempK[e.w].flower[dir_wv-1];
	    		else // have to do modular shift
	    			tempK[Nn].flower[1]=tempK[e.w].flower[(dir_wv-1+num_w)%num_w];
	    		// petals 2 and 4 from e.v
	    		int num_v=countFaces(e.v);
	    		int dir_vw=nghb(e.v,e.w);
	    		tempK[Nn].flower[2]=tempK[e.v].flower[dir_vw+1];
	    		if (isBdry(e.v)) // dir_vw is >0 and < num
		    		tempK[Nn].flower[4]=tempK[e.v].flower[dir_vw-1];
	    		else 
		    		tempK[Nn].flower[4]=tempK[e.v].flower[(dir_vw-1+num_v)%num_v];
	    		
	    		setAim(Nn,2.0*Math.PI);
	    	}
    		// average end centers/radii
    		rData[Nn].center=new Complex(getCenter(e.v).add(getCenter(e.w)).divide(2.0));
    		rData[Nn].rad = (getRadius(e.v)+getRadius(e.w))/2.0;
	    }

	    kData=tempK;
	    nodeCount=nodeCount+Num;
	    setCombinatorics();
	   
	    boolean debug=false;
	    if (debug) LayoutBugs.log_Red_Hash(this, this.redChain,this.firstRedEdge);
	    
	    fillcurves();
	    return count;
	  }

	  /** 
	   * WARNING: use ONLY in hex_refine. Given edge v and index j to a 
	   * petal, return new vert number for midpoint. New indices have been 
	   * temporarily stored as extra data in second half of 'tpack' flowers.
	   * Return -1 on error. 
	  */
	  public int vert_for_edge(int v,int j) {
		int k,w;
	    if (j<0 || j>countFaces(v)) 
	    	return -1;
	    if (!isBdry(v) && j==countFaces(v)) j=0; // only stored with first edge
	    w=kData[v].flower[j];
	    if (v<w) return kData[v].flower[countFaces(v)+1+j];
	    if ((k=nghb(w,v))<0) 
	    	return -1;
	    return kData[w].flower[countFaces(w)+1+k];
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
		 * Use xyz data to set overlaps of pack p. Allocate space if necessary.
		 * This command is still evolving. Eventually, e.g., datastr may contain
		 * list of overlaps to set; for now, all are set.
		 * 
		 * flag=1: find max/min edge lengths in xyz data, set overlaps as though
		 * circle radii were all min/2.
		 * 
		 * flag=2: for each vertex v find min edgelength, set as though radius
		 * of v was half that.
		 * 
		 * Radii are NOT actually changed --- only setting overlaps. Return
		 * count of overlaps set, 0 on error.
		 */
	public int set_xyz_overlaps(Point3D[] xyz_list, int count, int flag) {
		int k;
		double[] tmprads;
		double miN = 1000000000.0, maX = 0.0;
		double ovlp, dist, rad, ovlpmax = OKERR, ovlpmin = 1000;

		if (!status || count <= 0 || xyz_list == null)
			return 0;
		alloc_overlaps();

		if (flag == 1) { // treat as though all radii were miN/2.

			// compute maX/miN distances
			for (int i = 1; i <= nodeCount; i++)
				for (int j = 0; j < (countFaces(i) + getBdryFlag(i)); j++)
					if ((k = kData[i].flower[j]) > i && i <= count
							&& k <= count) {
						dist = EuclMath.xyz_dist(xyz_list[i], xyz_list[k]);
						miN = (dist < miN) ? dist : miN;
						maX = (dist > maX) ? dist : maX;
					}
			if (miN == 1000000000.0 || maX == 0.0 || (miN < OKERR))
				return 0; // ??
			rad = miN / 2.0;
			// compute and store overlaps
			for (int i = 1; i <= nodeCount; i++)
				for (int j = 0; j < (countFaces(i) + getBdryFlag(i)); j++)
					if ((k = kData[i].flower[j]) > i && i <= count
							&& k <= count) {
						ovlp = xyz_inv_dist(xyz_list[i], xyz_list[k], rad, rad);
						set_single_invDist(i, k, ovlp);
						ovlpmax = (ovlp > ovlpmax) ? ovlp : ovlpmax;
						ovlpmin = (ovlp < ovlpmin) ? ovlp : ovlpmin;
					}
			CirclePack.cpb.msg("Set overlaps using "
					+ "euclidean 3-space distances.\n"
					+ "  side lengths: max = " + maX + ", max/min = "
					+ (maX / miN) + "\n" + "  radius " + rad + "\n"
					+ "  overlaps: max = " + ovlpmax + ", min = " + ovlpmin);
			return 1;
		}
		if (flag == 2) { /*
							 * set as though each vertex v radius was 1/2 the
							 * minimum eldg length from v.
							 */
			// compute temporary radii
			tmprads = new double[nodeCount + 2];
			for (int i = 1; i <= nodeCount; i++) {
				miN = 100000000;
				for (int j = 0; j < (countFaces(i) + getBdryFlag(i)); j++) {
					if ((k = kData[i].flower[j]) != 0 && i <= count
							&& k <= count) {
						dist = Math.sqrt((xyz_list[i].x - xyz_list[k].x)
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
			for (int i = 1; i <= nodeCount; i++) {
				for (int j = 0; j < (countFaces(i) + getBdryFlag(i)); j++) {
					if ((k = kData[i].flower[j]) > i && i <= count
							&& k <= count) {
						ovlp = xyz_inv_dist(xyz_list[i], xyz_list[k],
								tmprads[i], tmprads[k]);
						set_single_invDist(i, k, ovlp);
						ovlpmax = (ovlp > ovlpmax) ? ovlp : ovlpmax;
						ovlpmin = (ovlp < ovlpmin) ? ovlp : ovlpmin;
					}
				}
			}
			CirclePack.cpb.msg("Set overlaps using "
					+ "euclidean 3-space distances and for each vert v, \n"
					+ "  use 1/2 the length of the shortest edge from v .\n"
					+ "  overlaps: max = " + ovlpmax + ", min = " + ovlpmin);
			return 1;
		}
		return 0;
	}

	  /** 
	   * Remove boundary vert v from pack p; have verified 
	   * v is bdry, and removal doesn't disconnect or leave 
	   * a nghb w/o any faces. On success, indices larger 
	   * than v will be reduced by 1 and, flowers and lists, 
	   * etc., will be adjusted. 
	   * CAUTION: user must be sure that successive removals 
	   * take account of these dynamic indices, e.g., in 'remove_circle', 'remove_tri_vert',
	   * etc.
	  */
	  public int remove_bdry_vert(int v) {
	    int num,w,indx,start;
	    int []newflower=null;
	    double []newoverlaps=null;

	    // fix flowers of neighbors 
	    for (int i=0;i<=countFaces(v);i++) {
	        w=kData[v].flower[i];
	        num=countFaces(w);
	        if (isBdry(w)) {
	  	  newflower=new int[countFaces(w)];
	  	  if (kData[w].flower[0]==v) 
	  	    for (int j=0;j<countFaces(w);j++) 
	  	      newflower[j]=kData[w].flower[j+1];
	  	  else if (kData[w].flower[countFaces(w)]==v)
	  	    for (int j=0;j<countFaces(w);j++)
	  	      newflower[j]=kData[w].flower[j];
	  	  if (kData[w].invDist!=null) {
	  	      newoverlaps=new double[countFaces(w)];
	  	      if (kData[w].flower[0]==v) 
	  		for (int j=0;j<countFaces(w);j++) 
	  		  newoverlaps[j]=getInvDist(w,kData[w].flower[j+1]);
	  	      else if (kData[w].flower[countFaces(w)]==v)
	  		for (int j=0;j<countFaces(w);j++)
	  		  newoverlaps[j]=getInvDist(w,kData[w].flower[j]);
	  	      kData[w].invDist=newoverlaps;
	  	    }
	  	  kData[w].flower=newflower;
	  	  kData[w].num--;
	  	}
	        else {
	  	  indx=nghb(w,v);
	  	  newflower=new int[countFaces(w)-1];
	  	  start=(indx+1)%countFaces(w);
	  	  for (int j=0;j<countFaces(w)-1;j++) newflower[j]=
	  	    kData[w].flower[(start+j)%countFaces(w)];
	  	  if (kData[w].invDist!=null) {
	  	      newoverlaps=new double[countFaces(w)-1];
	  	      start=(indx+1)%countFaces(w);
	  	      for (int j=0;j<countFaces(w)-1;j++) 
	  	    	  newoverlaps[j]=getInvDist(w,kData[w].flower[(start+j)%countFaces(w)]);
	  	      kData[w].invDist=newoverlaps;
	  	  }
	  	  kData[w].flower=newflower;
	  	  kData[w].num=num-2;
	  	  setBdryFlag(w,1);
	  	}
	    } // done removing references to v
	    delete_vert(v);
	    return 1;
	  }
	  
	  /** 
	   * Remove a trivalent, interior vertex. Already checked that v qualifies.
	   * On success, indices larger than v will be reduced by 1 and,
	   * flowers and lists, etc., will be adjusted. 
	   * CAUTION: user must be sure that successive removals take account 
	   * of these dynamic indices, e.g., in 'remove_circle', 'remove_tri_vert',
	   * etc.
	   */
	  public int remove_tri_vert(int v) {
	    int w,indx;
	    int []newflower=null;
	    double []newoverlaps=null;
	  	
	    // adjust nghbs 
	    for (int k=0;k<3;k++) {
	        w=kData[v].flower[k];
	        indx=nghb(w,v);
	        newflower=new int[countFaces(w)];
	        for (int j=0;j<indx;j++) newflower[j]=kData[w].flower[j];
	        for (int j=indx;j<countFaces(w);j++) {
	  	  newflower[j]=kData[w].flower[j+1];
	        }
	        kData[w].flower=newflower;
	        if (kData[w].invDist!=null) {
	  	  newoverlaps=new double[countFaces(w)];
	  	  for (int j=0;j<indx;j++) 
	  	    newoverlaps[j]=getInvDist(w,kData[w].flower[j]);
	  	  for (int j=indx;j<countFaces(w);j++) {
	  	      newoverlaps[j]=getInvDist(w,kData[w].flower[j+1]);
	  	  }
	  	  kData[w].invDist=newoverlaps;
	        }
	        kData[w].num--;
	        if (indx==0 && !isBdry(w)) {
	  	  kData[w].flower[countFaces(w)]=
	  	    kData[w].flower[0];
	  	  if (kData[w].invDist!=null)
	  	    set_single_invDist(w,kData[w].flower[countFaces(w)],getInvDist(w,kData[w].flower[0]));
	        }
	      }
	    delete_vert(v);
	    return 1;
	  } 
	  
	  /** 
	   * Remove 4-deg interior vert v, but put in edge between w and 
	   * the vertex z which is opposite v. Note that indices larger
	   * than v are decreased by 1, lists are adjusted, etc. Calling
	   * routine must be aware of this.
	   * TODO: Currently does only one vert at a time and overlaps 
	   * are discarded. Calling routine does 'complex_count', etc.
	  */
	  public int remove_quad_vert(int v,int w) {
	    int z,x,indx;
	    int []newflower=null;
	    double []newol=null;

	    if (isBdry(v) || countFaces(v)!=4
	        || (indx=nghb(w,v))<0) return 0;
	    z=kData[v].flower[(nghb(v,w)+2)%countFaces(v)]; 
	    // fix data for w and z
	    if (indx==0) {
	        kData[w].flower[0]=kData[w].flower[countFaces(w)]=z;
	        if (overlapStatus) {
	        	set_single_invDist(w,kData[w].flower[0],1.0);
	        	set_single_invDist(w,kData[w].flower[countFaces(w)],1.0);
	        }
	    }
	    else {
	        kData[w].flower[indx]=z;
	        if (overlapStatus)
	        	set_single_invDist(w,kData[w].flower[indx],1.0);
	    }
	    if ((indx=nghb(z,v))==0) {
	        kData[z].flower[0]=kData[z].flower[countFaces(z)]=w;
	        if (overlapStatus) {
	        	set_single_invDist(z,kData[z].flower[0],1.0);
	        	set_single_invDist(z,kData[z].flower[countFaces(z)],1.0);
	        }
	    }
	    else {
	        kData[z].flower[indx]=w;
	        if (overlapStatus)
	        	set_single_invDist(z,kData[z].flower[indx],1.0);
	    }
	    // remove v from others 
	    x=kData[v].flower[(nghb(v,w)+1)%countFaces(v)];
	    indx=nghb(x,v);
	    newflower=new int[countFaces(x)];
	    for (int j=0;j<indx;j++) newflower[j]=kData[x].flower[j];
	    for (int j=indx;j<countFaces(x);j++)
	      newflower[j]=kData[x].flower[j+1];
	    kData[x].flower=newflower;
	    if (overlapStatus) {
	        newol=new double[countFaces(x)];
	        for (int j=0;j<indx;j++) 
	        	newol[j]=getInvDist(x,kData[x].flower[j]);
	        for (int j=indx;j<countFaces(x);j++)
	        	newol[j]=getInvDist(x,kData[x].flower[j+1]);
	        kData[x].invDist=newol;
	    }
	    kData[x].num--;
	    x=kData[v].flower[(nghb(v,z)+1)%countFaces(v)];
	    indx=nghb(x,v);
	    newflower=new int[countFaces(x)];
	    for (int j=0;j<indx;j++) newflower[j]=kData[x].flower[j];
	    for (int j=indx;j<countFaces(x);j++)
	      newflower[j]=kData[x].flower[j+1];
	    kData[x].flower=newflower;
	    if (overlapStatus) {
	        newol=new double[countFaces(x)];
	        for (int j=0;j<indx;j++) 
	        	newol[j]=getInvDist(x,kData[x].flower[j]);
	        for (int j=indx;j<countFaces(x);j++)
	        	newol[j]=getInvDist(x,kData[x].flower[j+1]);
	        kData[x].invDist=newol;
	    }
	    kData[x].num--;
	    delete_vert(v);
	    return 1;
	  } 
	  
	  /**
	   * Insert 'newV' as petal of 'v' so it has petal index 'indx'. Return
	   * the new 'num' of faces. Only kData[v] is changed; calling routine
	   * must clean up; e.g., fix the flower of 'w', 'complex_count', etc.
	   * @param v int, center vertex
	   * @param indx int, desired indx of newV
	   * @param newV int, new petal vertex index
	   * @return int 'num', 0 on error (should be unharmed)
	   */
	  public int insert_petal(int v,int indx,int newV) {
		  int num=countFaces(v);
		  if (indx<0 || indx>(num+1))
			  return 0;
		  int []newflower=new int[num+2];
		  double []newInvDist=null;
		  if (overlapStatus)
			  newInvDist=new double[num+2];
		  
		  if (indx==0 && isBdry(v)) { // bdry, first petal
			  newflower[0]=newV;
			  if (overlapStatus)
				  newInvDist[0]=1.0;
			  for (int k=0;k<=num;k++) {
				  newflower[k+1]=kData[v].flower[k];
				  if (overlapStatus)
					  newInvDist[k+1]=getInvDist(v,kData[v].flower[k]);
			  }
			  num++;
			  kData[v].flower=newflower;
			  kData[v].invDist=newInvDist;
			  kData[v].num=num;
			  return num;
		  }
		  if (indx==(num+1) && isBdry(v)) { // bdry, last petal
			  for (int k=0;k<=num;k++) {
				  newflower[k]=kData[v].flower[k];
				  if (overlapStatus)
					  newInvDist[k]=getInvDist(v,kData[v].flower[k]);
			  }
			  newflower[num+1]=newV;
			  if (overlapStatus)
				  newInvDist[num+1]=1.0;
			  num++;
			  kData[v].flower=newflower;
			  kData[v].invDist=newInvDist;
			  kData[v].num=num;
			  return num;
		  }
		  else if (indx==(num+1)) { // interior, at end? same as at beginning
			  indx=0;
		  }
		  if (indx==0 && !isBdry(v)) { // interior, first petal
			  newflower[0]=newV;
			  if (overlapStatus)
				  newInvDist[0]=1.0;
			  for (int k=0;k<num;k++) {
				  newflower[k+1]=kData[v].flower[k];
			  	  if (overlapStatus)
			  		  newInvDist[k+1]=getInvDist(v,kData[v].flower[k]);
			  }
			  num++;
			  newflower[num]=newV; // repeat at end
			  if (overlapStatus)
				  newInvDist[num]=1.0;
			  kData[v].flower=newflower;
			  kData[v].invDist=newInvDist;
			  kData[v].num=num;
			  return num;
		  }
		  for (int k=0;k<indx;k++) {
			  newflower[k]=kData[v].flower[k];
			  if (overlapStatus) 
				  newInvDist[k]=getInvDist(v,kData[v].flower[k]);
		  }
		  newflower[indx]=newV;
		  if (overlapStatus)
			  newInvDist[indx]=1.0;
		  for (int k=indx;k<=num;k++) {
			  newflower[k+1]=kData[v].flower[k];
			  if (overlapStatus)
				  newInvDist[k+1]=getInvDist(v,kData[v].flower[k]);
		  }
		  num++;
		  kData[v].flower=newflower;
		  kData[v].invDist=newInvDist;
		  kData[v].num=num;
		  return num;
	  }
	  	
	  /** 
	   * Remove listed vertices: remove qualifying bdry 
	   * vertex and/or a list of trivalent interior 
	   * verts. In the bdry case, we make sure there is
	   * an interior remaining --- later processing
	   * may remove additional vertices. In the trivalent
	   * case, do not idenfity vertices around v as bdry.
	   * (Use a "puncture" for interior vertices if you
	   * want the nghb's to become bdry.)
	   * 
	   * Note: vertex indices are adjusted: keep track 
	   * of changes in array 'newIndex': to start, 
	   * newIndex[v]=v, then if vert v is deleted
	   * newIndex[v]=0 and entries greater than v are 
	   * lowered by one.
	   * @param vertlist NodeLink
	   * @return int count
	   */
	  public int remove_circle(NodeLink vertlist) {
	      if (vertlist==null || vertlist.size()==0) 
	    	  return 0;
	      
	      int count=0;
	      int flag=0;
	      int orig_nodeCount=nodeCount;
	      int []newIndex=new int[orig_nodeCount+1];
	      for (int j=1;j<=nodeCount;j++) 
	    	  newIndex[j]=j;
	      Iterator<Integer> vlist=vertlist.iterator();
	      while (vlist.hasNext()) {
	    	  boolean nogo=false;
	    	  // start with orig index, find current index
	    	  int tv=(Integer)vlist.next(); 
	    	  int v=newIndex[tv];
	    	  // Note: skip if v==0, already removed
	    	  if (v>0) {
	    		  int[] flower=this.getFlower(v);
	    		  if (!isBdry(v)) {
	    			  if (countFaces(v)!=3) 
	    				  nogo=true;
	    			  else for (int i=0;i<3;i++) {
	    				  int w=flower[i];
	    				  if ((!isBdry(w) && countFaces(w)<=3)
	    						  || (isBdry(w) && countFaces(w)<2))
	    					  nogo=true;
	    			  }
	    		  }
	    		  else if (isBdry(v)) {
	    			  if (countFaces(flower[0])<2
	    					  || countFaces(getLastPetal(v))<2)
	    				  nogo=true;
	    			  for (int i=1;i<countFaces(v);i++) 
	    				  if (isBdry(flower[i])) 
	    					  nogo=true;
	    		  }
	  	    
	    		  // if vertex qualifies
	    		  if (!nogo) { 
    				  boolean didit=false;
	    			  if (packDCEL!=null) {
	    				  Vertex vert=packDCEL.vertices[tv];
	    				  HalfLink hlink=vert.getOuterEdges();
	    				  int[] intverts=CombDCEL.findComponent(packDCEL,0, hlink);
	    				  
	    				  // will there be an interior remaining?
	    				  if (intverts[0]>0) {
	    					  boolean isbdry=isBdry(tv);
	    					  ArrayList<Vertex> blist=RawDCEL.rmVert_raw(packDCEL, tv);
	    					  Iterator<Vertex> bis=blist.iterator();
	    					  if (!isbdry) { // bdry
	    						  while (bis.hasNext()) {
	    							  vert=bis.next();
	    							  vert.bdryFlag=0;
	    							  vert.halfedge.twin.face=null;
	    						  }
	    					  }
	    					  didit=true;
	    				  }
	    			  }
	    			  else {
	    				  if (isBdry(v)) {
	    					  for (int i=0;i<=countFaces(v);i++) 
	    						  setAim(flower[i],-0.1);
	    					  if (remove_bdry_vert(v)!=0) 
	    						  didit=true;
	    				  }
	    				  else if (remove_tri_vert(v)!=0) 
	    					  didit=true;
	    			  }
	    			  if (didit) { // yes, removed vert v
	    				  count++;
	    				  newIndex[v]=0;
	    				  for (int j=1;j<=orig_nodeCount;j++)
	    					  if (newIndex[j]>v) newIndex[j]--;
	    			  }
	    		  }
	    	  } // end of while
	      }
	      if (count==0) { 
	    	  flashError("No vertices qualified for deletion");
	    	  return 0;
	      }
	      if (packDCEL!=null) {
	    	  for (int k=1;k<=orig_nodeCount;k++) {
	    		  int nw=newIndex[k];
	    		  if (nw!=0 && nw!=k) {
	    			  vData[nw]=vData[k];
	    			  vData[nw].setBdryFlag(packDCEL.vertices[nw].bdryFlag);;
	    		  }
	    	  }
	    	  packDCEL.fixDCEL_raw(this);
	      }
	      return count;
	  }
	  
	  /**
	   * Remove vertices of degree three from the list (as
	   * far as possible). 'vertexMap' has (old,new) info.
	   * By default, we try to remove all degree 3 vertices.
	   * We check legality first; so, e.g., we don't remove
	   * ones which become degree 3 during removal of others.
	   * @param vertlist NodeLink
	   * @return int count
	   */
	  public int remove_barycenters(NodeLink vertlist) {
	      if (vertlist==null || vertlist.size()==0) 
	    	  return 0;
	      int origNodeCount=nodeCount;
		  int count=0;
	      
	      // organize 
	      for (int v=1;v<=nodeCount;v++) {
	    	  kData[v].utilFlag=0;
	      }

	      // set 'mark' to -1 for vertices to consider for removal:
	      //    * in given list * interior * no nghbs of deg 2 
	      //    * no interior nghbs of deg 3
	      int vtick=0;
	      Iterator<Integer> vlst=vertlist.iterator();
	      while (vlst.hasNext()) {
	    	  int v=vlst.next();
	    	  
	    	  if (!isBdry(v) && countFaces(v)==3) {
	    		  int strike=0;
	    		  for (int j=0;(strike==0 && j<=countFaces(v));j++) {
	    			  int k=kData[v].flower[j];
	    			  if (countFaces(k)<3 || (!isBdry(k) && countFaces(k)==3))
	    				  strike++;
	    		  }
	    		  if (strike==0) {
	    			  kData[v].utilFlag=-1;
	    			  vtick++;
	    		  }
	    	  }
	      }
	      
	      if (vtick==0) {
	    	  CirclePack.cpb.errMsg("No barycenters qualified for removal");
	    	  return 0;
	      }
	      
	      // set utilFlag = m if vert has m nghbs not set for removal
	      for (int v=1;v<=nodeCount;v++) {
	    	  if (kData[v].utilFlag>=0) {
	    		  int good=0;
	    		  for (int j=0;j<(countFaces(v)+getBdryFlag(v));j++) {
	    			  int k=kData[v].flower[j];
	    			  if (isBdry(k) || kData[k].utilFlag>=0)
	    				  good++;
	    		  }
	    		  kData[v].utilFlag=good;
	    	  }
	      }  
	      
	      // Now proceed in indexed order; if removal causes problems 
	      //   (e.g., neighbor with too small degree), just skip it.
	      VertexMap oldnew=new VertexMap();
	      int new_indx=1;
	      for (int v=1;v<=nodeCount;v++) {
	    	  
	    	  // keep, don't remove?
	    	  if (kData[v].utilFlag>=0) { 
	    		  oldnew.add(new EdgeSimple(v,new_indx++));
	    	  }
	    	  
	    	  // if slated for removal, check if it can be removed
	    	  else {
	    		  
	    		  // count petals who will be kept (so far)
	    		  int ncount=0;
	    		  for (int j=0;j<(countFaces(v)+getBdryFlag(v));j++) {
	    			  int k=kData[v].flower[j];
	    			  
	    			  // look at petal k
	    			  if (isBdry(k)) // on bdry? keep 
	    				  ncount++;
	    			  else if (kData[k].utilFlag>=3) // enough ngbs who will be kept? keep
	    				  ncount++;
	    			  else { // here have to see if petal has already lost too many
	    	    		  int good=0;
	    	    		  for (int jj=0;jj<(countFaces(k)+getBdryFlag(k));jj++) {
	    	    			  int kk=kData[k].flower[jj];
	    	    			  // if petal 'k's petal is bdry, not slated, or larger and
	    	    			  //    hence not yet removed
	    	    			  if (kData[kk].utilFlag>=0 || kk>v || isBdry(kk)) // 
	    	    				  good++;
	    	    		  }
	    	    		  if (good>=3) // okay, it will have 3 petals left (not counting v)
	    	    			  ncount++;
	    			  }
	    		  }

	    		  // no, can't remove so keep
	    		  if (ncount<3) {
	    			  oldnew.add(new EdgeSimple(v,new_indx++));
	    			  kData[v].utilFlag=-2; // can't remove after all
	    		  }
	    	  }
	      }
	      
	      // now ready to build the new packing
	      
	      // didn't throw out anything?
	      int newNodeCount=oldnew.size();
	      if (newNodeCount==nodeCount) {
		        flashError("No vertices qualified for deletion");
		        return 0;
	      }
	      
	      // else list the new indexes in an array
	      int []vertmap=new int[nodeCount+1];
	      Iterator<EdgeSimple> vm=oldnew.iterator();
	      while (vm.hasNext()) {
	    	  EdgeSimple on=vm.next();
	    	  vertmap[on.v]=on.w;
	      }
	      KData []newKData=new KData[sizeLimit];
	      RData []newRData=new RData[sizeLimit];
	      for (int k=1;k<=nodeCount;k++) {
	    	  if (vertmap[k]>0) {
	    		  
	    		  // create kData and fix flower
	    		  newKData[vertmap[k]]=kData[k].clone();
	    		  int num=0;
	    		  for (int j=0;j<(countFaces(k)+getBdryFlag(k));j++)
	    			  if (vertmap[kData[k].flower[j]]>0)
	    				  num++;
	    		  
	    		  // is there a problem?
	    		  if (num<2 || (!isBdry(k) && num<3))
	    			  throw new CombException("problem building new flower in rm_barycenters");

	    		  int []flower=new int[num+1];
	    		  
	    		  // handle closed flowers whose first petal is now gone
	    		  if (!isBdry(k) && vertmap[kData[k].flower[0]]==0) {
	    			  int tick=0;
	    			  for (int jj=0;jj<countFaces(k);jj++)
	    				  if (vertmap[kData[k].flower[jj]]>0) {
	    					  flower[tick]=vertmap[kData[k].flower[jj]];
	    					  tick++;
	    				  }
	    			  flower[num]=flower[0]; // close up
	    			  newKData[vertmap[k]].flower=flower;
	    			  newKData[vertmap[k]].num=num;
	    		  }
	    		  
	    		  else {
	    			  int tick=0;
	    			  for (int jj=0;jj<=countFaces(k);jj++)
	    				  if (vertmap[kData[k].flower[jj]]>0) {
	    					  flower[tick]=vertmap[kData[k].flower[jj]];
	    					  tick++;
	    				  }
	    			  newKData[vertmap[k]].flower=flower;
	    			  newKData[vertmap[k]].num=tick-1;
	    		  }

	    		  newRData[vertmap[k]]=rData[k].clone();
	    	  }
	      } // done with for loop through vertices
	    	  
	      // put new info in place
	      count=nodeCount=newNodeCount;
	      kData=newKData;
	      rData=newRData;

	      chooseAlpha();
	      chooseGamma();
	      setCombinatorics();

	      // debug
	      System.out.println("'rm_bary': vtick="+vtick+"; origNodeCount="+origNodeCount+
	    		  "; newNodeCount="+newNodeCount);
	      
	      return count;
	  }

	  /** 
	   * Remove specified bdry edges; appropriate edges should be
	   *.(u,v) where u and v are bdry neighbors. 
	   * @param edgelist EdgeLink
	   * @return int, count removed
	   */
	public int remove_edge(EdgeLink edgelist) {
	    int v,w,n,m,v1,v2,count=0;
	    int []newflower=null;
	    double []newoverlaps=null;

	    Iterator<EdgeSimple> elist=edgelist.iterator();
	    while (elist.hasNext()) {
	        EdgeSimple edge=(EdgeSimple)elist.next();
	        v1=edge.v;
	        v2=edge.w;

	        // verify data: given edge (u,v), let w be common nghb. Then
	        //   w must be interior and the number of changes, bdry/int,
	        //   as you go through petal must be 2 or 0 (if w is isolated
	        //   interior.
	        boolean failed=false;
	        if (!isBdry(v1) || !isBdry(v2)
	  	  || countFaces(v1)==1 || countFaces(v2)==1
	  	  || (v2!=kData[v1].flower[0] && v1!=kData[v2].flower[0]) ) {
	  	  failed=true;
	        }
	        if (!failed) {
	  	  if (v1==kData[v2].flower[0]) { // v1, v2 ctrclw
	  	      int nhold=v1;
	  	      v1=v2;
	  	      v2=nhold;
	  	  }
	  	  w=kData[v1].flower[1]; // common neighbor w
	  	  if (isBdry(w)) failed=true;
	  	  if (!failed) {
	  	      int hits=0;
	  	      for (int j=0;j<countFaces(w);j++) {
	  		  m=kData[w].flower[j];
	  		  int mm=kData[w].flower[j+1];
	  		  if ((!isBdry(m) && isBdry(mm))
	  		      || (!isBdry(mm) && isBdry(m)))
	  		      hits++;
	  	      }
	  	      if (hits!=0 && hits!=2) failed=true;
	  	  }
	        }
	        if (failed) {
	  	  if (count==0)
	  	      flashError("rm_edge: no edges removed before "+
	  			 "illegal edge ("+v1+","+v2+") was encountered.");
	  	  else 
	  	      flashError("rm_edge: "+count+" edges removed before "+
	  			 "illegal edge ("+v1+","+v2+") was encountered.");
	  	  return count;
	        }

	        // fix up v1
	        newflower=new int[countFaces(v1)+1];
	        if (overlapStatus)
	        	newoverlaps=new double[countFaces(v1)+1];
	        for (int i=1;i<=countFaces(v1);i++) {
	        	newflower[i-1]=kData[v1].flower[i];
	        	if (overlapStatus)
	        		newoverlaps[i-1]=getInvDist(v1,kData[v1].flower[i]);
	        }
	        kData[v1].flower=newflower;
	        if (overlapStatus) {
	        	kData[v1].invDist=newoverlaps;
	        }
	        kData[v1].num--;
	        setBdryFlag(kData[v1].flower[0],1);
	        setAim(v1,-1.0);

	        // fix up v2 
	        newflower=new int[countFaces(v2)+1];
	        if (overlapStatus)
	        	newoverlaps=new double[countFaces(v2)+1];
	        for (int i=0;i<countFaces(v2);i++) {
	        	newflower[i]=kData[v2].flower[i];
	        	if (overlapStatus)
	        		newoverlaps[i]=getInvDist(v2,kData[v2].flower[i]);
	        }
	        kData[v2].flower=newflower;
	        if (overlapStatus) {
	        	kData[v2].invDist=newoverlaps;
	        }
	        kData[v2].num--;
	        setAim(v2,-1.0);

	        // fix up common neighbor v 
	        v=kData[v1].flower[0];
	        newflower=new int[countFaces(v)+1];
	        if (overlapStatus)
	        	newoverlaps=new double[countFaces(v)+1];
	        n=nghb(v,v2);
	        m=countFaces(v);
	        for (int i=n;i<=m;i++) {
	        	newflower[i-n]=kData[v].flower[i];
	        	if (overlapStatus)
		  	      newoverlaps[i-n]=getInvDist(v,kData[v].flower[i]);
	        }
	        for (int i=1;i<n;i++) {
	        	newflower[m-n+i]=kData[v].flower[i];
	        	if (overlapStatus)
	        		newoverlaps[m-n+i]=getInvDist(v,kData[v].flower[i]);
	        }
	        kData[v].num--;
	        kData[v].flower=newflower;
	        if (overlapStatus) {
	        	kData[v].invDist=newoverlaps;
	        }
	        setAim(v,-1.0);
	        count++;
	    } // end of while
	    return count;
	  } 

	  /**
	   * Successively collapse edges (v,u), fusing v and u to a new vertex
	   * (index is smaller of v and u) whose flower is built from
	   * those of v and u. Don't proceed if this would disconnect the
	   * interior or cause a bdry vert to have no interior neighbor;
	   * on problem, stop processing in this case. 
	   * TODO: Overlap data is currently discarded; try to save it
	   * @param EdgeLink edgelist
	   * @return index of last fused vertex or -1 on error
	   */
	  public int collapse_edge(EdgeLink edgelist) {
		  int v1,v2;
		  int lastFused=-1;
		  int []newflower=null;

		  while (edgelist!=null && edgelist.size()>0) {
			  EdgeSimple edge=(EdgeSimple)edgelist.remove(0);
			  v1=edge.v;
			  v2=edge.w;
			  int v3=v1;
			  int k12=-1;
			  int k21=-1;
			  // ensure that v1<v2; v1 will be the new fused vertex
			  if (v1>v2) {
				  v1=v2;
				  v2=v3;
			  }

			  if ((k12=nghb(v1,v2))<0 || (k21=nghb(v2,v1))<0)
				  throw new CombException("non-neighbors");

			  int num1=countFaces(v1);
			  int num2=countFaces(v2);
			  int newNum;
			  int newBdryFlag=1;
			  int lv=0;
			  int rv=0;
			  
			  // both v1, v2 bdry
			  if (isBdry(v1) && isBdry(v2)) {
				  if (kData[v1].flower[0]!=v2 && kData[v2].flower[0]!=v1)
					  throw new CombException("interior edge, bdry ends");
				  if (kData[v1].flower[0]==v2 &&
						  kData[kData[v2].flower[0]].flower[0]==v1) 
					  throw new CombException("too few bdry edges");
				  if (num1==1 || num2==1)
					  throw new CombException("not enough faces");
				  
				  // okay
				  newNum=num1+num2-2;
				  newflower=new int[newNum+1];
				  // v1 upstream
				  if (kData[v1].flower[0]==v2) { 
					  lv=kData[v1].flower[1];
					  int spot=nghb(v2,lv);
					  for (int j=0;j<=spot;j++)
						  newflower[j]=kData[v2].flower[j];
					  int k=nghb(v1,lv);
					  for (int j=(k+1);j<=num1;j++)
						  newflower[spot+j-k]=kData[v1].flower[j];
				  }
				  // v2 upstream
				  else {
					  rv=kData[v2].flower[1];
					  int spot=nghb(v1,rv);
					  for (int j=0;j<=spot;j++)
						  newflower[j]=kData[v1].flower[j];
					  int k=nghb(v2,rv);
					  for (int j=(k+1);j<=num2;j++)
						  newflower[spot+1]=kData[v2].flower[j];
				  }
			  }				  

			  // v1 interior, v2 bdry
			  else if (isBdry(v2)) {
				  lv=kData[v1].flower[(k12+1)%num1];
				  rv=kData[v1].flower[(k12+num1-1)%num1];
				  newNum=num1+num2-4;
				  newflower=new int[newNum+1];
				  int spot=k21;
				  for (int j=0;j<spot;j++)
					  newflower[j]=kData[v2].flower[j];
				  for (int j=0;j<num1-2;j++)
					  newflower[spot+j]=kData[v1].flower[(k12+2+j)%num1];
				  spot += num1-2;
				  for (int j=0;j<num2-k21-1;j++)
					  newflower[spot+j]=kData[v2].flower[k21+2+j];
			  }

			  // v1 bdry, v2 interior
			  else if (isBdry(v1)) {
				  lv=kData[v2].flower[(k21+1)%num2];
				  rv=kData[v2].flower[(k21+num2-1)%num2];
				  newNum=num1+num2-4;
				  newflower=new int[newNum+1];
				  int spot=k12;
				  for (int j=0;j<spot;j++)
					  newflower[j]=kData[v1].flower[j];
				  for (int j=0;j<num2-2;j++)
					  newflower[spot+j]=kData[v2].flower[(k21+2+j)%num2];
				  spot += num2-2;
				  for (int j=0;j<num1-k12-1;j++)
					  newflower[spot+j]=kData[v1].flower[k12+2+j];
			  }
			  
			  // both v1, v2 interior
			  else {
				  newBdryFlag=0;
				  lv=kData[v1].flower[(k12+1)%num1];
				  rv=kData[v1].flower[(k12+num1-1)%num1];
				  newNum=num1+num2-4;
				  newflower=new int[newNum+1];
				  int spot=num1-1;
				  for (int j=0;j<spot;j++)
					  newflower[j]=kData[v1].flower[(k12+1+j)%num1];
				  for (int j=0;j<num2-2;j++)
					  newflower[spot+j]=kData[v2].flower[(k21+2+j)%num2];
			  }
			  
			  // failure if flower has some vertex twice
			  int num=newNum;
			  if (newBdryFlag==1) num++;
			  for (int j=0;j<num-1;j++) {
				  for (int k=j+1;k<num;k++) {
					  if (newflower[j]==newflower[k])
						  throw new CombException("would give multiple edge");
				  }
			  }
			  
			  // failure if a common neighbor has too few faces
			  if (lv>0) {
				  if ((isBdry(lv) && countFaces(lv)==1) ||
						  (!isBdry(lv) && countFaces(lv)<=3))
					  throw new CombException("common vertex has too few faces");
			  }
			  if (rv>0) {
				  if ((isBdry(rv) && countFaces(rv)==1) ||
						  (!isBdry(rv) && countFaces(rv)<=3))
					  throw new CombException("common vertex has too few faces");
			  }
			  
			  // Everything seems okay: fix things up
			  
			  if (overlapStatus) this.free_overlaps();
			  // TODO: try to save these in future
			  flist=null;
			  
			  // fix lv
			  if (lv>0) {
				  int nm=countFaces(lv);
				  int kl1=nghb(lv,v1);
				  for (int j=kl1+1;j<nm;j++)
					  kData[lv].flower[j]=kData[lv].flower[j+1];
				  if (!isBdry(lv)) {
					  if (kl1==nm) {
						  kData[lv].flower[nm]=kData[lv].flower[0];
					  }
				  }
				  kData[lv].num -= 1;
			  }
			  
			  // fix rv
			  if (rv>0) {
				  int nm=countFaces(rv);
				  int kl2=nghb(rv,v2);
				  for (int j=kl2+1;j<nm;j++)
					  kData[rv].flower[j]=kData[rv].flower[j+1];
				  if (!isBdry(rv)) {
					  if (kl2==nm) {
						  kData[rv].flower[nm]=kData[rv].flower[0];
					  }
				  }
				  // replace v2 by v1
				  kData[rv].flower[kl2]=v1;
				  if (kl2==0 && !isBdry(rv)) 
					  kData[rv].flower[nm-1]=v1;
				  kData[rv].num -= 1;
			  }
			  
			  // fix any flowers pointing to v2 (other than rv and lv)
			  for (int j=0;j<newNum+newBdryFlag;j++) {
				  int v=newflower[j];
				  if (v!=rv && v!=lv) {
					  for (int jj=0;jj<(countFaces(v)+getBdryFlag(v));jj++) {
						  if (kData[v].flower[jj]==v2) kData[v].flower[jj]=v1;
					  }
				  }
			  }
			  
			  // fix v1
			  setBdryFlag(v1,newBdryFlag);
			  if (newBdryFlag==1) setAim(v1,-0.1);
			  kData[v1].num=newNum;
			  kData[v1].flower=newflower;
			  
			  // fix packData: remove v2 (which is larger than v1)
			  for (int j=v2;j<nodeCount;j++) {
				  kData[j]=kData[j+1].clone();
				  rData[j]=rData[j+1].clone();
			  }
			  
			  nodeCount--;
			  if (alpha>=v2) alpha--;
			  if (gamma>=v2) gamma--;
			  
			  // fix numbering in flowers due to deletion of index v2
			  for (int v=1;v<=nodeCount;v++) {
				  for (int j=0;j<=countFaces(v);j++) {
					  if (kData[v].flower[j]>v2)
						  kData[v].flower[j]--;
				  }
			  }
			  
			  lastFused=v1;

			  // adjust indices in rest of edgelist
			  int N=edgelist.size();
			  for (int j=0;j<N;j++) {
				  edge=(EdgeSimple)edgelist.get(j);
				  if (edge.v>=v2) edge.v--;
				  if (edge.w>=v2) edge.w--;
			  }
			  
			  // adjust vlist, elist indices >= v2; convert v2 to v1
			  if (vlist!=null && vlist.size()>0) {
				  NodeLink newV=new NodeLink(this);
				  Iterator<Integer> vlst=vlist.iterator();
				  while(vlst.hasNext()) {
					  int v=vlst.next();
					  if (v==v2) newV.add(v1);
					  if (v>v2) newV.add(v-1);
					  if (v<v2) newV.add(v);
				  }
				  if (newV.size()>0)
					  vlist=newV;
				  else vlist=null;
			  }

			  if (elist!=null && elist.size()>0) {
				  EdgeLink newE=new EdgeLink(this);
				  Iterator<EdgeSimple> elst=elist.iterator();
				  while(elst.hasNext()) {
					  EdgeSimple eg=elst.next();
					  if (eg.v!=v2 && eg.w!=v2) {
						  if (eg.v>v2) eg.v--;
						  if (eg.w>v2) eg.w--;
						  newE.add(eg);
					  }
					  else {
						  if (eg.v==v2) eg.v=v1;
						  if (eg.v>v2) eg.v--;
						  if (eg.w==v2) eg.w=v1;
						  if (eg.w>v2) eg.w--;
						  newE.add(eg);
					  }
				  }
				  if (newE.size()>0)
					  elist=newE;
				  else elist=null;
			  }

		  } // end of while

		  return lastFused;
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
			double e_rad;
			Complex e_center;
			for (int i = 1; i <= nodeCount; i++) {
				sc = HyperbolicMath.h_to_e_data(getCenter(i), getRadius(i));
				e_center = sc.center.times(factor);
				e_rad = sc.rad * factor;
				sc = HyperbolicMath.e_to_h_data(e_center, e_rad);
				if (sc.flag == 0)
					hyp_out = true; // circle was forced into disc
				setCenter(i,sc.center);
				setRadius(i,sc.rad);
			}
			if (redChain != null) {
				boolean keepon = true;
				RedList rtrace = redChain;
				while (rtrace != redChain || keepon) {
					keepon = false;
					sc = HyperbolicMath.h_to_e_data(rtrace.center, rtrace.rad);
					e_center = sc.center.times(factor);
					e_rad = sc.rad * factor;
					sc = HyperbolicMath.e_to_h_data(e_center, e_rad);
					rtrace.center = sc.center;
					rtrace.rad = sc.rad;
					rtrace = rtrace.next;
				}
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
		 * don't change.
		 * @param ang double, in radians
		 * @return 1
		 */
	  public int rotate(double ang) {
		  Mobius mob=Mobius.rotation(ang/Math.PI);
		  if (packDCEL!=null) {
			  if (hes>0) { // sphere
				  for (int v=1;v<=nodeCount;v++) {
					  Complex z=vData[v].center;
					  vData[v].center=new Complex(z.x+ang,z.y);
				  }
				  if (packDCEL.redChain!=null) {
					  RedHEdge rtrace=packDCEL.redChain;
					  do {
						  Complex z=rtrace.getCenter();
						  if (z!=null)
							  rtrace.setCenter(new Complex(z.x+ang,z.y));
						  rtrace=rtrace.nextRed;
					  } while(rtrace!=packDCEL.redChain);
				  }
				  return 1;
			  }
			  
			  // hyp/eucl
			  for (int v=1;v<=nodeCount;v++) 
				  vData[v].center=mob.apply(vData[v].center);
			  if (packDCEL.redChain!=null) {
				  RedHEdge rtrace=packDCEL.redChain;
				  do {
					  Complex z=rtrace.getCenter();
					  if (z!=null)
						  rtrace.setCenter(mob.apply(z));
					  rtrace=rtrace.nextRed;
				  } while(rtrace!=packDCEL.redChain);
			  }
			  return 1;
		  }
	      
	      // traditional
		  if (hes>0) {
			  for (int i=1;i<=nodeCount;i++) {
				  Complex z=getCenter(i);
				  setCenter(i,z.x+ang,z.y);
			  }
			  return 1;
		  }
		  for (int i=1;i<=nodeCount;i++)
			  setCenter(i,mob.apply(getCenter(i)));
		  return 1;
	  } 

	  /**
	   * Given an edge-path, create a 'Path2D.Double' from it. 
	   * @param edgelist EdgeList
	   * @return Path2D.Double, null on error
	   */
	  public Path2D.Double elist_to_path(EdgeLink edgelist) {
	      int count=0;
	      if (edgelist==null || edgelist.size()==0) return null;
	      Path2D.Double gpath=new Path2D.Double();
	      Iterator<EdgeSimple> elist=edgelist.iterator();
	      EdgeSimple edge=(EdgeSimple)elist.next();
	      Complex vz=getCenter(edge.v);
	      gpath.moveTo(vz.x,vz.y);
	      Complex wz=getCenter(edge.w);
	      gpath.moveTo(wz.x,wz.y);
	      while(elist.hasNext()) {
	    	  edge=(EdgeSimple)elist.next();
		      vz=getCenter(edge.v);
		      gpath.lineTo(vz.x,vz.y);
		      wz=getCenter(edge.w);
		      gpath.lineTo(wz.x,wz.y);
	      }
	      gpath.closePath();
	      if (count>3) return gpath;
	      return null;
	  } 

	  public void free_overlaps() {
		  for (int v=1;v<=nodeCount;v++)
			  kData[v].invDist=null;
		  overlapStatus=false;
	  }
	  
	  /** 
	   * Identify edges/vert on opposite sides of given bdry vertex.
	   * Adjust the local structure, but calling routine must handle
	   * update of the packing.
	   * @param vert int, 
	   * @return int, -1 on error, 0 okay, but no vert removed, v>0 if
	   *   vertex v is removed.
	  */
	  public int close_up(int vert) {
	    int next,v,w,newnum,hold;
	    double vert_next_angle=0.0,next_w_angle=0.0;
	    double []newoverlaps=null;
	    int []newflower;

	    // fix up 'next', vertex after vert 
	    if (countFaces(vert)<3) return -1; // too few faces 
	    v=kData[vert].flower[0];
	    next=kData[vert].flower[countFaces(vert)];
	    // if next==v, then vert and next should be interior; nothing more to do 
	    if (next==v) {
	        setBdryFlag(vert,0);
	        setBdryFlag(next,0);
	        setAim(vert,2.0*Math.PI);
	        setAim(next,2.0*Math.PI); 
	        return 0;
	    }
	    // fix up vert 
	    kData[vert].flower[0]=next;
	    setBdryFlag(vert,0);
	    setAim(vert,2.0*Math.PI);
	    if (overlapStatus) { // average the overlaps with v and next 
	        vert_next_angle=(0.5)*(getInvDist(vert,kData[vert].flower[0])+
	  			     getInvDist(vert,kData[vert].flower[countFaces(vert)]));
	        set_single_invDist(vert,kData[vert].flower[0],vert_next_angle);
	        set_single_invDist(vert,kData[vert].flower[countFaces(vert)],vert_next_angle);
	    }

	    /* if next and v share common neighbors: either {v,vert,next} or
	       {v,vert,next,w} is a closed bdry comp; in former case, modify w
	       to put in latter case (one face disappears). */

	    else if ((kData[v].flower[0]==
	  	       (w=kData[next].flower[countFaces(next)]))
	  	   || kData[v].flower[0]==next) {
	        if (kData[v].flower[0]==next) {
	  	  EdgeLink elink=new EdgeLink(this);
	  	  elink.add(new EdgeSimple(v,next));
	  	  remove_edge(elink);
	  	  w=kData[next].flower[countFaces(next)];
	        }
	        newnum=countFaces(v)+countFaces(next);
	        newflower=new int[newnum+1];
	        if (overlapStatus) {
	  	  newoverlaps=new double[newnum+1];
	  	  next_w_angle=(0.5)*
	  	    (getInvDist(w,kData[w].flower[0])+
	  	     getInvDist(w,kData[w].flower[countFaces(w)]));
	        }
	        hold=countFaces(next);
	        for (int i=0;i<=countFaces(next);i++) {
	  	  newflower[i]=kData[next].flower[i];
	  	  if (overlapStatus) newoverlaps[i]=getInvDist(next,kData[next].flower[i]);
	        }
	        for (int i=1;i<=countFaces(v);i++) {
	  	  newflower[i+countFaces(next)]=kData[v].flower[i];
	  	  if (overlapStatus) newoverlaps[i+countFaces(next)]=
	  				   getInvDist(next,kData[next].flower[i]);
	        }
	        kData[next].flower=newflower;
	        kData[next].num=newnum;
	        if (overlapStatus) {
	  	  kData[next].invDist=newoverlaps;
	  	  set_single_invDist(next,kData[next].flower[0],vert_next_angle);
	  	  set_single_invDist(next,kData[next].flower[newnum],vert_next_angle);
	  	  set_single_invDist(next,kData[next].flower[hold],next_w_angle);	
	        }
	        // fix up w 
	        kData[w].flower[countFaces(w)]=next;
	        if (overlapStatus)
	        	set_single_invDist(w,kData[w].flower[0],vert_next_angle);
	        	set_single_invDist(w,kData[w].flower[countFaces(w)],vert_next_angle);
	        setBdryFlag(next,0);
	        setBdryFlag(w,0);
	        setAim(next,2.0*Math.PI);
	        setAim(w,2.0*Math.PI);
	      }

	    // otherwise, next remains boundary vertex 
	    else {
	        newnum=countFaces(v)+countFaces(next);
	        newflower=new int[newnum+1];
	        if (overlapStatus) 
	  	newoverlaps=new double[newnum+1];
	        for (int i=0;i<=countFaces(v);i++) {
	  	  newflower[i]=kData[v].flower[i];
	  	  if (overlapStatus)
	  	    newoverlaps[i]=getInvDist(v,kData[v].flower[i]);
	        }
	        for (int i=1;i<=countFaces(next);i++) {
	        	newflower[i+countFaces(v)]=kData[next].flower[i];
	        	if (overlapStatus)
	        		newoverlaps[i+countFaces(v)]=getInvDist(v,kData[v].flower[i]);
	        }
	        if (overlapStatus)
	        	newflower[countFaces(v)]=(int) vert_next_angle;
	        kData[next].flower=newflower;
	        kData[next].num=newnum;
	        if (overlapStatus) {
	        	kData[w].invDist=newoverlaps;
	        }
	        setAim(next,-1.0);
	    }

	    // fix up things with v in their flowers (v will be removed) 
	    for (int i=0;i<=countFaces(v);i++) {
	        int ii=kData[v].flower[i];
	        for (int j=0;j<=countFaces(ii);j++) {
	        	int jj=kData[ii].flower[j];
	        	if (jj==v) kData[ii].flower[j]=next;
	        }
	    }
	    delete_vert(v);
	    return v;
	  } 
	  
	  /**
	   * Consolidating 'adjoin' calls. p1 and p2 may or
	   * may not have dcel structures; if either has dcel
	   * structure, then result will have dcel.
	   *  * test for legality
	   *  * adjoin via dcel or traditional methods
	   *  * fix up results such as 'vlist', 'elist'.
	   * Returned 'PackData' should be fully processed. 
	   * 'PackExtender's and some lists will be lost.
	   * 
	   * TODO: replace some calls to old 'adjoin'
	   * 
	   * @param p1 PackData
	   * @param p2 PackData (may equal p1)
	   * @param v1 int
	   * @param v2 int
	   * @param n int
	   * @return new PackData, null or exception on error
	   */
	  public static PackData adjoinCall(PackData p1,PackData p2,int v1,int v2,int n) {
		  PackData newPack=null;
		  PackData np1=null;
		  PackData np2=null;
		  
		  // flags
		  boolean dcelcase=false;
		  int offset=0;
		  Overlap overlaps=null;
		  boolean overlap_flag=false;
		  boolean selfadjoin=false;
		  if (p1==p2)
			  selfadjoin=true;
		  else { // make enough space
			  int sze = p1.nodeCount+p2.nodeCount+10;
    		  p1.alloc_pack_space(sze,true);
		  }
		  
		  // minimal legality test; later calls check further
		  if ( !p1.status || !p2.status ||  
				  v1<=0 || v2<=0 || v1>p1.nodeCount || v2>p2.nodeCount ||
			      (n>0 && p1.nodeCount<n) || (n>0 && p2.nodeCount<n)) 
			  throw new ParserException("'adjoin' usage: data problem");
		  
		  // create 'newPack'
		  
		  // DCEL situation: note, we use clones
		  if (p1.packDCEL!=null) {
			  dcelcase=true;
			  PackDCEL pdc1=CombDCEL.cloneDCEL(p1.packDCEL);
			  PackDCEL pdc2=p2.packDCEL;
			  if (!selfadjoin) {
				  if (p2.packDCEL==null) 
					  pdc2=CombDCEL.cloneDCEL(CombDCEL.getRawDCEL(p2));
				  else
					  pdc2=CombDCEL.cloneDCEL(p2.packDCEL);
			  }
			  else
				  pdc2=pdc1;
			  
			  // here's the main call.
			  PackDCEL newDCEL=CombDCEL.d_adjoin(pdc1, pdc2, v1, v2, n);
			  newPack=new PackData(null);
			  newPack.attachDCEL(newDCEL);
			  PackDCEL pdcel=newPack.packDCEL;
			  newPack.vertexMap=pdcel.newOld;
			  pdcel.newOld=null;
			  pdcel.redChain=null;
			  
    		  // Set up 'VData' (at original size) 
    		  VData[] newV=new VData[p1.sizeLimit+1];
    		  
    		  // copy the 'p1' info?
    		  if (selfadjoin) 
    			  for (int v=1;v<=pdcel.vertCount;v++) {
    				  newV[v]=p1.vData[pdcel.vertices[v].vutil].clone();
    			  }
    		  else {
    			  // all 'p1' vertices should still be there, same indices
    			  for (int v=1;v<=p1.nodeCount;v++)
    				  newV[v]=p1.vData[v].clone();
    			  // rest are from 'p2'.
    			  for (int v=p1.nodeCount+1;v<=pdcel.vertCount;v++) {
    				  newV[v]=new VData();
    				  // v is 'oldv' in 'qackData'
    				  int oldv=newPack.vertexMap.findW(v-p1.nodeCount); 
//System.out.println("<v,oldv>="+v+","+oldv);    				  
    				  newV[v].rad=p2.getRadius(oldv);
    				  newV[v].center=p2.getCenter(oldv);
    				  newV[v].aim=p2.getAim(oldv);
    			  }
    		  }
    		  newPack.vData=newV;

			  // ensure bdry twins have face with negative index. 
    		  try {
    			  if (pdcel.redChain!=null) {
    				  RedHEdge rhe=pdcel.redChain;
    				  do {
    					  if (rhe.twinRed==null)
    						  rhe.myEdge.twin.face=new dcel.Face(-1);
    					  rhe=rhe.nextRed;
    				  } while (rhe!=pdcel.redChain);
    			  }
    			  pdcel.fixDCEL_raw(newPack);
    		  } catch(Exception ex) {
    			  throw new CombException("'adjoinCall' error: "+ex.getMessage());
    		  }
		  }
		  
		  // traditional: again, we operate on clones
		  else {
			  np1=p1.copyPackTo();
			  if (!selfadjoin) 
				  np2=p2.copyPackTo();
			  
			  // save overlaps
	    	  if (!selfadjoin) { 
	    		  offset=p1.nodeCount;
	    		  overlap_flag=false;
	    		  if (p1.overlapStatus || p2.overlapStatus )
	    			  overlap_flag=true;
	  	  
	    		  // save pnum1 overlaps
	    		  Overlap trace=null;
	    		  double angle;
	    		  if (p1.overlapStatus) {
	    			  overlaps=new Overlap();
	    			  trace=overlaps;
	    			  for (int v=1;v<=p1.nodeCount;v++) {
	    				  int[] petals=p1.getPetals(v);
	    				  for (int j=0;j<petals.length;j++) {
	    					  // only store for petals with larger indices
	    					  if (v<petals[j]
	    							  && (angle=p1.getInvDist(v,petals[j]))!=1.0 ) {
	    						  trace.v=v;
	    						  trace.w=petals[j];
	    						  trace.angle=angle;
	    						  trace=trace.next=new Overlap();
	    					  }
	    				  }
	    			  }
	    		  }
	    	  }
	    	  
			  PackData.adjoin(np1, np2, v1, v2, n);
			  newPack=np1;
			  newPack.complex_count(true);
		  }
		  
		  // restablish certain things
		  
		  // traditional; restablish saved overlaps  
		  if (!dcelcase) {
		      try {
			  if(overlap_flag && newPack.alloc_overlaps()!=0) {
				  if (offset==0 && overlaps!=null) { // self-adjoin, use new indices 
					  Overlap trace=overlaps;
				      while (trace!=null && trace.next!=null) {
				    	  int vv=newPack.vertexMap.findW(trace.v);
				    	  int ww=newPack.vertexMap.findW(trace.w);
				    	  newPack.set_single_invDist(vv,ww,trace.angle);
				    	  trace=trace.next;
				      }
				  }
				  else if (overlaps!=null) { // reestablish pnum1 overlaps 
				      Overlap trace=overlaps;
				      while (trace!=null && trace.next!=null) {
				    	  newPack.set_single_invDist(trace.v,trace.w,trace.angle);
				    	  trace=trace.next;
				      }
				  }
				  if ( offset!=0 && p2.overlapStatus) { // new overlaps from p2? 

				      for(int v=1;v<=p2.nodeCount;v++) {
				    	  double angle;
				    	  int[] petals=p2.getPetals(v);
				    	  int vv=newPack.vertexMap.findW(v);
				    	  for(int j=0;j<petals.length;j++)
				    		  if (v<petals[j]) {
				    			  int ww=newPack.vertexMap.findW(petals[j]);
				    			  if ((angle=p2.getInvDist(v,petals[j]))!=1.0)
				    				  newPack.set_single_invDist(vv,ww,angle);
				    		  }
				      }
				  }
			  }
		      } catch (Exception ex) {}
		  }

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
	   * Adjoin p2 to 'this': start with vert v2 of p2 to vert v1 
	   * of 'this', proceed n additional verts CLOCKWISE (negative 
	   * direction) about bdry of 'this' (counterclockwise about
	   * bdry of p2). 
	   * Put results in 'this'. If p2 and 'this' not equal, then 
	   * p2 should remain unchanged. 'this.vertexMap' holds {orig,new}, 
	   * i.e., for each original index in p2, what its new index 
	   * is in 'this'.
	   * 
	   * Note: Calling routine must handle saving overlap data
	   * 
	   * Note: If n<0, do successive edge identifications as long as 
	   * they are legal; e.g. do a complete closed bdry component 
	   * if it's the same size in both packings.
	   *
	   * p1 Contains the new packing; calling routine handles 
	   * combinatorial processing after return.
	   * 
	   * @param p2 PackData, other packing to be adjoined to 'this' (may = 'this')
	   * @param v1 int, vertex in bdry of 'this'
	   * @param v2 int, vertex in bdry of p2
	   * @param n int, number of edges (or negative)
	   * @return 1 if successful.
	   * TODO: are all cases covered here? 
	   */
	  public static int adjoin(PackData p1,PackData p2, int v1, int v2, int n) {
	    int ll,m,mm,nn,node,newnode,endvertex,endnode;
	    int count=0,dist,w,indx;
	    int next,v,old_vert,new_vert;
	    int oldNodeCount;
	    int []old2new=null;
	    int []oldnewflag=null;
	    int []newflower=null;

	    // suitable? 
	    int b1=p1.bdry_comp_count(v1);
	    int b2=p2.bdry_comp_count(v2);
	    if ( !p2.status || n==0 || v1<=0 || v2<=0 
	        || v1>p1.nodeCount || v2>p2.nodeCount
	        || (n>0 && b1<n)
	        || (n>0 && b2<n) || p1.locks!=0) {
	    	throw new ParserException(" traditional adjoin: some format error");
	    }
	    
	    if (n<0) { // do the full boundary
	      n=b1=p1.bdry_comp_count(v1); 
	      b2=p2.bdry_comp_count(v2);
	    }
	   
	    // overlaps are saved before adjoin and reset after; faces are corrupted
	    p1.free_overlaps();
	    p1.flist=null;

	    // ==== procedure for adjoining distinct packs ================== 
	    if (p2!=p1) {
	    	oldNodeCount=p2.nodeCount;
	        old2new=new int[p2.nodeCount+2];
	        oldnewflag=new int[p2.nodeCount+2];

	        // store new indices along identified boundary segments
	        node=v1;
	        newnode=v2;
	        for (int i=1;i<=n;i++) {
	        	oldnewflag[newnode]=1; // this data will reside where 'old2new' points
	        	old2new[newnode]=node;
	        	node=p1.getLastPetal(node);
	        	newnode=p2.getFirstPetal(newnode);
	        }
	        endvertex=newnode; 	// last p2 vertex 
	        endnode=node; 		// last vertex of this 
	        if (b2>n) { // didn't get back to first vertex
	        	oldnewflag[endvertex]=1;
	        	old2new[endvertex]=endnode;
	        }
	  		
	        // get rest of new indices
	        newnode=p1.nodeCount+1;
	        for (int i=1;i<=p2.nodeCount;i++) {
	        	if (oldnewflag[i]==0) { // doesn't already have a new index
	        		old2new[i]=newnode;
	        		newnode++;
	        	}
	        }
	        if ((newnode+3)>p1.sizeLimit) {
	        	p1.alloc_pack_space(newnode+100,true);
	        }
	        p1.nodeCount=newnode-1;

	        // fix up flowers 
	        
	        int i,j;

	        try {
	        for (i=1;i<=p2.nodeCount;i++) {
	        	j=old2new[i];

	        	if (i==endvertex) { // special measures for last vertex 
	        		if ((b1 > n) && (b2 > n)) { // flower begins with petals from p2 
	        			ll=p2.countFaces(i);
	        			m=p1.countFaces(j);
	        			newflower=new int[ll+m+1];
	        			for (int k=1;k<=m;k++) 
	        				newflower[ll+m-k+1]=
	        					p1.getPetal(j,m-k+1);
	        			for (int k=0;k<=ll;k++) 
	        				newflower[k]=
	        					old2new[p2.getPetal(i,k)];
	        			// should be okay, even in dcel case
	        			p1.kData[j].flower=newflower;
	        			p1.kData[j].num=ll+m;
	        		}
	        		else if ((b1==n) && (b2>n)) {
	        			/* identify endnode and v1 in this; use parts of three flowers;
	  				   endnode to be deleted later. */
	        			for (int ii=0;ii<=p1.countFaces(endnode);ii++) {
	        				// remove references to endnode in this
	        				w=p1.getPetal(endnode,ii);
	        				indx=p1.nghb(w,node);
	        				if (indx==0 && !p1.isBdry(w))
	        					p1.kData[w].flower[0]=
	        						p1.kData[w].flower[p1.countFaces(w)]=v1;
	        				else if (indx>=0) p1.kData[w].flower[indx]=v1;
	        			}
	        			ll=p2.countFaces(endvertex);
	        			mm=p1.countFaces(v1);
	        			nn=p2.countFaces(v2);
	        			newflower=new int[ll+mm+nn+1];
	        			for (int k=0;k<ll;k++)
	        				newflower[k]=old2new[p2.getPetal(endvertex,k)];
	        			for (int k=ll;k<=(ll+mm);k++)
	        				newflower[k]=p1.getPetal(v1,k-ll);
	        			for (int k=(ll+mm+1);k<=(ll+mm+nn);k++)
	        				newflower[k]=old2new[p2.getPetal(v2,ll+mm-k)];
	        			p1.kData[v1].flower=newflower;
	        			p1.kData[v1].num=ll+mm+nn;
	        		}
	        		else if (b1==n && b2==n) {
	        			/* whole bdrys; all become interior */
	        			ll=p2.countFaces(v2);
	        			mm=p1.countFaces(v1);
	        			newflower=new int[ll+mm+1];
	        			for (int k=0;k<ll;k++)
	        				newflower[k]=old2new[p2.getPetal(v2,k)];
	        			for (int k=ll;k<=(ll+mm);k++)
	        				newflower[k]=p1.getPetal(v1,k-ll);
	        			p1.kData[v1].flower=newflower;
	        			p1.kData[v1].num=ll+mm;
	        		}
	        	}

	        	// rest of flowers are more routine 

	        	else if (oldnewflag[i]!=0) { // this is one of identified vertices
	        		ll=p2.countFaces(i);
	        		mm=p1.countFaces(j);
	        		newflower=new int[ll+mm+1];
	        		for (int k=0;k<=mm;k++)
	        			newflower[k]=p1.getPetal(j,k);
	        		for (int k=mm+1;k<=ll+mm;k++)
	        			newflower[k]=old2new[p2.getPetal(i,k-mm)];
	        		p1.kData[j].flower=newflower;
	        		p1.kData[j].num = ll+mm;
	        	}
	        	else {
	        		p1.setCenter(j,new Complex(p2.getCenter(i)));
	        		p1.setRadius(j,p2.getRadius(i));
	        		p1.kData[j].num=p2.countFaces(i);
	        		newflower=new int[p2.countFaces(i)+1];
	        		for (int k=0;k<=p2.countFaces(i);k++)
	        			newflower[k]= old2new[p2.getPetal(i,k)];
	        		p1.kData[j].flower=newflower;
	        	}
	        } // end of for loop
	        } catch (Exception ex) {
	        	ex.printStackTrace();
	        }
	        
	        if (b1>n && b2==n) p1.delete_vert(endnode);
	        /* use all bdry comp of p2, but not of this; causes identification
	  	 	of endnode and v1 in this. References to endnode have been changed,
	  	 	so endnode can be deleted. */
	        
	    } // done with case of different packs 

	    else { // adjoin pack to itself
	    	oldNodeCount=p1.nodeCount;
	        // first check if everything is compatable.
	        next=v1;
	        if ((count=p1.bdry_comp_count(v1))==0) 
	        	throw new CombException();
	        dist=1;
	        while ((next=p1.getFirstPetal(next))!=v2 
	  	     && dist<=count) dist++;
	        // dist counts edges to v2, if on same bdry comp
	        if (count>=p1.nodeCount || count<2 // error in combinatorics 
	        		|| dist==1			// would leave self-id'd edge 
	        		|| n>count			// not enough edges 
	        		|| (dist<count && ((2*n)>(count-dist)||(2*n)==(count-dist-1)))
	        		|| (v1==v2 && (2*n==(count-1) || 2*n>count))) /* leave self-id'd edge or
	        					conflicting ident's */
	        {
// debug
	        	next=v1;
	        	System.err.println("bdry listing\n next = "+v1+"\n "+p1.kData[v1].flower[0]);
	        	while ((next=p1.getFirstPetal(next))!=v1) {
	        		System.err.println(" "+p1.getFirstPetal(next));
	        	}
	        	throw new CombException();
	        }
	        if ((dist=count-dist)==2*n) { /* will be same as 'zip' from vert 
	  				     half way tween v1 and v2; indicate by setting v2=v1. */
	  	  dist=n;
	  	  while (dist>0) {
	  	      v1=p1.getFirstPetal(v2);
	  	      v2=v1;
	  	      dist--;
	  	  }
	        }
	        if (count==dist && v1!=v2) { // v1, v2 on separate bdry comps
	  	  next=v2;
	  	  // conflict on whether comps close up 
	  	  if ((dist=p1.bdry_comp_count(v2))==0
	  	      || dist<3 		// error in complex 
	  	      || n > dist		// not long enough 
	  	      || (n==count && n!=dist) || (n==dist && n!=count))
	  	      throw new CombException();
	        }
	        // now handle various possibilities 
	        old2new=new int[oldNodeCount+2];
	        for (int kk=1;kk<=oldNodeCount;kk++) old2new[kk]=kk;
	        next=p1.getLastPetal(v1);
	        v=p1.getFirstPetal(v2);
	        if (next==v && v1!=v2) { // verts v2, next, v1 along edge 
	  	  old_vert=p1.getFirstPetal(next); // v1 
	  	  new_vert=p1.getLastPetal(next); // v2 
	  	  if (p1.close_up(next)<0) return 0;
	  	  for (int kk=1;kk<=oldNodeCount;kk++) {
	  	      if (old2new[kk]==old_vert) old2new[kk]=new_vert;
	  	      else if (old2new[kk]>old_vert) old2new[kk] -= 1;
	  	  }
	        }
	        else if (v1==v2) { // zip up n edges 
	  	  next=v1;
	  	  for (int j=1;j<=n;j++) {
	  	      old_vert=p1.getFirstPetal(next);
	  	      new_vert=p1.getLastPetal(next);
	  	      if (old_vert!=new_vert) {

	  		  if (p1.close_up(next)<0) return 0;
	  		  for (int kk=1;kk<=oldNodeCount;kk++) {
	  		      if (old2new[kk]==old_vert) old2new[kk]=new_vert;
	  		      if (old2new[kk]>old_vert) old2new[kk] -= 1;
	  		    }
	  		  if (new_vert>old_vert) new_vert--;
	  		  next=new_vert;
	  		}
	  	      else {p1.setBdryFlag(next,1);j=n+1;}
	  	    }
	  	}
	    else {
	        // attach first edge 
	        // fix up v1 (will remove v2) 
	  	  ll=p1.countFaces(v1);
	  	  mm=p1.countFaces(v2);
	  	  newflower=new int[ll+mm+1];
	  	  for (int i=0;i<=ll;i++) newflower[i]=p1.getPetal(v1,i);
	  	  for (int i=(ll+1);i<=(ll+mm);i++) 
	  	    newflower[i]= p1.getPetal(v2,i-ll);
	  	  p1.kData[v1].flower=newflower;
	  	  p1.kData[v1].num = ll+mm;
	  	  /* fix up 'next', clockwise from v1 (will remove ctrclk'w 
	  	     nghb v of v2) */
	  	  ll=p1.countFaces(v);
	  	  mm=p1.countFaces(next);
	  	  newflower=new int[ll+mm+1];
	  	  for (int i=0;i<=ll;i++) 
	  		  newflower[i]=p1.getPetal(v,i);
	  	  for (int i=ll+1;i<=(ll+mm);i++) 
	  	    newflower[i]= p1.getPetal(next,i-ll);
	  	  p1.kData[next].flower=newflower;
	  	  p1.kData[next].num = ll+mm;
	  	  // fix up things pointed to v (which is to be removed) 
	  	  for (int i=0;i<=p1.countFaces(v);i++) {
	  	      int ii=p1.getPetal(v,i);
	  	      for (int j=0;j<=p1.countFaces(ii);j++) {
	  		  int jj=p1.getPetal(ii,j);
	  		  if (jj==v) 
	  			  p1.kData[ii].flower[j]=next;
	  	      }
	  	  }
	  	  // fix up things pointed to v2 (which is to be removed )
	  	  for (int i=0;i<=p1.countFaces(v2);i++) {
	  	      int ii=p1.getPetal(v2,i);
	  	      for (int j=0;j<=p1.countFaces(ii);j++) {
	  		  int jj=p1.getPetal(ii,j);
	  		  if (jj==v2) 
	  			  p1.kData[ii].flower[j]=v1;
	  		}
	  	    }
	  	  /* now to remove v and v2, replaced by next and v1, respectively. 
	  	     Old names should no longer be in any flowers.*/
	  	  p1.delete_vert(v);
	  	  for (int kk=1;kk<=oldNodeCount;kk++) {
	  	      if (old2new[kk]==v) old2new[kk]=next;
	  	      if (old2new[kk]>v) old2new[kk]--;
	  	  }
	  	  if (v2>v) v2--;
	  	  if (next>v) next--;
	  	  if (v1>v) v1--;
	  	  p1.delete_vert(v2);
	  	  for (int kk=1;kk<=oldNodeCount;kk++) {
	  	      if (old2new[kk]==v2) old2new[kk]=v1;
	  	      if (old2new[kk]>v2) old2new[kk]--;
	  	  }
	  	  if (next>v2) next--;
	  	  // now zip up chain of edges for rest of attachments 
	  	  if (n>1) for (int j=1;j<=n-1;j++) {
	  	      old_vert=p1.getFirstPetal(next);
	  	      new_vert=p1.getLastPetal(next);
	  	      if (old_vert!=new_vert) {
	  		  if (p1.close_up(next)<0) return 0;
	  		  for (int kk=1;kk<=oldNodeCount;kk++) {
	  		      if (old2new[kk]==old_vert) old2new[kk]=new_vert;
	  		      if (old2new[kk]>old_vert) old2new[kk]--;
	  		  }
	  		  if (new_vert>old_vert) new_vert--;
	  		  next=new_vert;
	  	      }
	  	      else {p1.setBdryFlag(next,1);j=n+1;}
	  	    }
	    }
	    } /* done with case of self-adjoin. */

//	  finish up 
	  for (int i=1;i<=p1.nodeCount;i++) { // set bdry and plot flags
		  if (p1.getFirstPetal(i)==p1.getLastPetal(i)) {
			  p1.setBdryFlag(i,0);
			  if (p1.getRadius(i)<=0) p1.setRadius(i,0.7);
			  // so ones now in interior don't have infinite radius
		  }
	      else p1.setBdryFlag(i,1);
	      p1.setPlotFlag(i,1);
	  }
	  p1.chooseGamma();
	  // this.vertexMap saves {p2-index,new}, list matching orig p2 indices (or p1 indices if
	  // self-adjoining) with new indices in this.
	  // NOTE: when called from one of 'double' routines, p2-index=p1-index.
	  p1.vertexMap=new VertexMap();
	  for (int j=1;j<=oldNodeCount;j++) 
		  p1.vertexMap.add(j,old2new[j]);
	  return 1;
	  } 
	  
	  /**
	   * Return schwarzian for edge <v,w>
	   * @param es EdgeSimple
	   * @return double, 
	   */
	  public double getSchwarzian(EdgeSimple es) { // given <v,w>
		  if (packDCEL!=null) {
			  HalfEdge he=packDCEL.findHalfEdge(es);
			  if (he==null)
				  throw new DataException("schwarzian failure: "+
						  es.v+" and "+es.w+" are not neighbors");
			  return he.getSchwarzain();
		  }
		  
		  // else check in kData.
		  int k=nghb(es.v,es.w);
		  if (k<0) 
			  throw new DataException("schwarzian failure: "+
					  es.v+" and "+es.w+" are not neighbors");
		  return kData[es.v].schwarzian[k];
	  }
	  
	  /**
	   * Return schwarzian for  dual edge <f,g>, 
	   * @param fg GraphSimple
	   * @return double
	   */
	  public double getSchwarzian(GraphSimple fg) {
		  return getSchwarzian(reDualEdge(fg.v,fg.w));
	  }
	  
	  /**
	   * Store schwarzian for EdgeSimple <v,w>
	   * @param edge EdgeSimple
	   * @param sch double
	   * @return int 1 on success
	   */
	  public int setSchwarzian(EdgeSimple edge,double sch) {
		  return setSchwarzian(edge.v,edge.w,sch);
	  }
	  
	  /**
	   * Store schwarzian for edge <v,w> either in packDCEL
	   * or in kData.
	   * @param v int
	   * @param w int
	   * @param sch double
	   * @return int 1 on success
	   */
	  public int setSchwarzian(int v,int w,double sch) {
		  if (packDCEL!=null) {
			  HalfEdge he=packDCEL.findHalfEdge(v,w);
			  he.setSchwarzian(sch);
			  he.twin.setSchwarzian(sch);
			  return 1;
		  }
		  try {
			  kData[v].schwarzian[nghb(v,w)]=sch;
			  kData[w].schwarzian[nghb(w,v)]=sch;
			  return 1;
		  } catch(Exception ex) {}
		  return 0;
	  }
	  
	  /**
	   * Return the inversive distance recorded for edge from
	   * v to nghb w. 1.0 is default. 1.0 for default
	   * @param v int
	   * @param w int
	   * @return double
	   */
	  public double getInvDist(int v,int w) {
		  if (packDCEL!=null) {
			  HalfEdge he=packDCEL.findHalfEdge(v,w);
			  if (he!=null)
				  return he.getSchwarzain();
			  throw new CombException("invDist error: <"+v+","+w+"> is not a valid edge");
		  }
		  
		  // else look in kData
		  if (!overlapStatus)
			  return 1.0;
		  int j=nghb(v,w);
		  if (j<0) // not an edge
			  return 1.0;
		  return getPetal(v,nghb(v,w));
	  }
	  
	  /** 
	   * Fill a 'RadIvdPacket' with data for this face.
	   * @param f int
	   * @return new RadIvdPacket
	   */
	  public RadIvdPacket getRIpacket(int f) {
		  RadIvdPacket rip=new RadIvdPacket();
		  int[] fverts=getFaceVerts(f);
		  if (packDCEL==null) {
			  for (int j=0;j<3;j++) {
				  rip.rad[j]=getRadius(fverts[(j)]);
				  rip.oivd[j]=getInvDist(fverts[(j+1)%3],fverts[(j+2)%3]);
			  }
		  }
		  else {
			  HalfEdge he=packDCEL.faces[f].edge;
			  int k=0;
			  do {
				  rip.rad[k]=getRadius(he.origin.vertIndx);
				  rip.oivd[k]=he.next.getInvDist();
				  k++;
			  } while(k<3);
		  }
		  return rip;
	  }
	  
	  /** 
	   * Store one legal inversive distance value in all approp places. Storage for
	   * inv distances must be allocated.
	   * Note: 'invDist' values: deep overlap in (-1,0); normal overlap in [0,1]; 
	   * separated circles in (1,infty); tangency is 1. 
	   * Note: if 'invDist' lies in [-1,1] then intersecting circles overlap 
	   * angle acos(invDist). If separated, 'invDist' lies in [1,infty). 
	   * @param v int, first vert of edge
	   * @param j int, (IMPORTANT) index of other end in flower of v
	   * @param invDist double, lies in [-1,infty)
	   * @return int, 1 on success
	   * @exception ParserException if space has not been allocated. 
	   */
	  public int set_single_invDist(int v,int w,double invDist) {
	    if (!overlapStatus) throw new ParserException("set_overlaps: space not allocated");
	    int indx=nghb(v,w);
	    if (indx<0)
	    	throw new ParserException("set_single_overlap error: "+w+" is not a petal of "+v);
	    if (packDCEL!=null) {
	    	HalfEdge he=packDCEL.findHalfEdge(v,w);
	    	if (he==null)
		    	throw new ParserException("set_single_overlap error: "+w+" is not a petal of "+v);
	    	he.setInvDist(invDist);
	    	he.twin.setInvDist(invDist);
	    	return 1;
	    }
	    
	    // else, store in kData
	    kData[v].invDist[indx]=invDist;
	    if (indx==0 && !isBdry(v))
	    	kData[v].invDist[countFaces(v)]=invDist;
	    indx=nghb(w,v);
	    kData[w].invDist[indx]=invDist;
	    if (indx==0 && !isBdry(w))
	    	kData[w].invDist[countFaces(w)]=invDist;
	    return 1;
	  }
	  
	  /** 
	   * Return vertices defining an edge path proceeding from 'v1'
	  and taken from the given 'vertlist'. Currently two methods:
	  + flag=0: multiple-choice (default: for multi-sheeted surfaces):
	            return as many successive neighbors as possible.
	  + flag=1: return successive neighbors described in edge 'turns' 
	  in combinatoric direction. First in vertlist must be a neighbor 
	  of v1, then succession of increments.  E.g., 
	  "path_c -i 23 57 2 4 0 3 3" will start with edge
	  {23,57}, then {57,x}, where in the flower for 57, {57,x} is "2" edges
	  counterclockwise from {57,23}, then "4" edges around x, then "0" (back
	  up), etc.  In a hex packing, "path_c -i 23 57 3 3 3 ..." defines a
	  combinatorial straight path. 
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
	   * Routine to 'migrate' a branch point in a complex. This is based on
	   * the geometric method for creating branch points, namely, attaching 
	   * two packings along a common slit, the tips of the slit becoming the 
	   * branch circle. If the local combinatorics of the two complexes were
	   * identical, then the slit could be extended on each piece to end at 
	   * the common neighbors of the original tips so that after pasting the 
	   * branching would occur at that neighbor. This routine adjusts the 
	   * combinatorics of the original pasted complex locally to accomplish 
	   * the same result -- so a branch point v is "migrated" to a neighbor w.
	   * 
	   * Branching is a geometric phenomenon, while the work here seems purely 
	   * combinatoric. The local combinatorics must have certain symmetries to
	   * make this work: namely, the original 'branch' vertex v must have even 
	   * degree (at least 6) and the neighbor w must have the same degree as
	   * the vertex on the opposite side of v. 
	   * 
	   * After migration, as a bookkeeping convenience, indices are reset so v 
	   * again points to the branch vertex, w is one of the two neighbors
	   * corresponding to the original branch point.
	   * 
	   * Return 0 on error, return new w on success. 
	   */
	   
	  public int migrate(int v,int w) {
	   	int u,jdex,dex,ww,node=nodeCount;
	   	
	   	if (v<1 || v>nodeCount || w<1 || w>nodeCount
	   		|| isBdry(v) || isBdry(w)) {
	  	    return 0;
	  	}
	   	int num_v=countFaces(v);
	   	int halfv=(int)(num_v/2);
	   	int ind_w=nghb(v,w);
	   	if (ind_w<0 || num_v<6 || halfv*2!=num_v) return 0;
	   	int num_w=countFaces(w);
	   	ww=getPetal(v,(ind_w+halfv)%num_v);
	   	if (isBdry(ww) || num_w!=countFaces(ww)) return 0;
	   	
	   	/* At end will identify ww with w to become new branch point. "Half" 
	   	 * of v starting at w will become new vert w; other half, vert ww.*/
    	int[] flower_w=new int[halfv+1];
	  	int[] flower_ww=new int[halfv+1];
	  	
	  	flower_w[0]=flower_w[halfv]=v; 
	  	for (int i=1;i<halfv;i++) {
	  		flower_w[i]=getPetal(v,(ind_w+i)%num_v);
	  	}
	  	flower_ww[0]=flower_ww[halfv]=v;
	  	for (int i=1;i<halfv;i++) {
	  		flower_ww[i]=getPetal(v,(ind_w+halfv+i)%num_v);
	  	}
	  	
	  	// At end, new v is combination of old w and old ww 
	  	int[] flower_v=new int[2*num_w+1];
	  	flower_v[0]=flower_v[2*num_w]=w;
	  	dex=nghb(ww,v);
	  	for (int i=1;i<num_w;i++) {
	  		flower_v[i]=getPetal(ww,(dex+i)%num_w);
	  	}
	  	flower_v[num_w]=ww;
	  	dex=nghb(w,v);
	  	for (int i=1;i<num_w;i++) {
	  		flower_v[num_w+i]=getPetal(w,(dex+i)%num_w);
	  	}

	  	// temporarily fix nghbs of original v (use fake indices for now) 
	  	for (int i=1;i<num_v;i++) {
	  		u=getPetal(v,(ind_w+i)%num_v);
	  		if (u!=w && u!=ww && (dex=nghb(u,v))>=0) {
	  			kData[u].flower[dex]=node+w;
	  			if (dex==0 && !isBdry(u))
	  			kData[u].flower[countFaces(u)]=node+w;
	  		}
	  		u=getPetal(v,(ind_w+halfv+i)%num_v);
	  		if (u!=w && u!=ww && (dex=nghb(u,v))>=0) {
	  			kData[u].flower[dex]=node+ww;
	  	  		if (dex==0 && !isBdry(u))
	  		    	kData[u].flower[countFaces(u)]=node+ww;
	  		}
	  	}
	  	// fix nghbs of original w 
	  	dex=nghb(w,v);
	  	for (int i=1;i<num_w;i++) {
	  		u=getPetal(w,(dex+i)%num_w);
	  		jdex=nghb(u,w);
	  		kData[u].flower[jdex]=v;
	  		if (jdex==0 && !isBdry(u)) 
	  			kData[u].flower[countFaces(u)]=v;
	  	}
	  	// fix nghbs of original ww 
	  	dex=nghb(ww,v);
	  	for (int i=1;i<num_w;i++) {
	  	        u=getPetal(ww,(dex+i)%num_w);
	  		jdex=nghb(u,ww);
	  		kData[u].flower[jdex]=v;
	  		if (jdex==0 && !isBdry(u)) 
	  			kData[u].flower[countFaces(u)]=v;
	      }

	  	// now replace the fake indices of original v 
	  	for (int i=0;i<num_v;i++) {
	  		u=kData[v].flower[i];
	  		for (int j=0;j<=countFaces(u);j++) {
	  			if (getPetal(u,j)==node+w) 
	  				kData[u].flower[j]=w;
	  			else if (getPetal(u,j)==node+ww) 
	  				kData[u].flower[j]=ww;
	  		}
	  	}


	  	// implement new flowers, num's, etc. 

	  	kData[v].flower=flower_v;
	  	kData[v].num=2*num_w;
	  	kData[w].flower=flower_w;
	  	kData[w].num=halfv;
	  	kData[ww].flower=flower_ww;
	  	kData[w].num=kData[ww].num=halfv;

	  	complex_count(true);
	  	facedraworder(false);

	  	return 1;
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
	   * Returns @see GraphLink of pairs {n,m} where m and n indices
	   * of faces sharing an edge {v,w} from the given input elist.
	   * Note orientation: n is to left of directed edge {v w}, m is 
	   * to right. Skip edges lying in only one face.
	   * @param elist @see EdgeLink
	   * @return GraphLink, null on error or if 'elist' is empty
	   */
	  public GraphLink dualEdges(EdgeLink elist) {
		  GraphLink dlink=new GraphLink();
		  if (elist==null || elist.size()==0) return null;
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
		  Complex z=null;
		  Complex w=null;
		  while (eit.hasNext()) {
			  EdgeSimple dedge=eit.next();
			  z=face_center(dedge.v);
			  w=face_center(dedge.w);
			  if (hes>0) {
				  totlen += SphericalMath.s_dist(z,w);
			  }
			  else if (hes<0) {
				  totlen += HyperbolicMath.h_dist(z,w);
			  }
			  else {
				  totlen += z.minus(w).abs();
			  }
		  }
		  return totlen;
	  }
	  
	  /**
	   * Given faces f and g, if they share an edge,
	   * return dual edge {v,w}, where w is to left of
	   * {f,g} and v is to the right. (@see dualEdge)
	   * @param f
	   * @param g
	   * @return EdgeSimple {v,w}, null on error
	   */
	  public EdgeSimple reDualEdge(int f,int g) {
		  if (f>0 && g>0 && f<=faceCount && g<=faceCount) { 
			  int j=face_nghb(g,f);
			  if (j<0) return null;
			  int v=faces[f].vert[j];
			  int w=faces[f].vert[(j+1)%3];
			  return new EdgeSimple(v,w);
		  }
		  return null;
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
	   * Generate new combinatorics in pack p which are the barycentric
	   * subdivision of the original faces. I.e., each edge gets new vertex, 
	   * each face gets a hex barycenter. The original vertices and degrees
	   * are unchanged, the top vertex indices are those for face barycenters.
	   * Idea: (1) hex refine (2) add barycenters to the new 'middle' faces
	   *  (3) "flip" each of the 3 edges around each new barycenter.

	   * For now, most pack information is lost; may try to fixup in the
	   * future. For example, should make stab at new radii, centers, maybe
	   * save overlaps, branching, etc., can interpolate new xyz locations 
	   * if there is xyz data. 
	   * Note: on error, p might be corrupted.
	   * @return int, 0 on error;
	  */
	  public int bary_refine() {
		  return bary_refine(0);
	  }
	  
	  /** 
	   * Generate new combinatorics in pack p which are 
	   * the barycentric subdivision of the original 
	   * faces. I.e., each edge gets new vertex, each 
	   * face gets a hex barycenter. The original 
	   * vertices and degrees are unchanged, the 
	   * top vertex indices are those for face barycenters.
	   * Idea: (1) hex refine (2) add barycenters to the new 'middle' faces
	   *  (3) "flip" each of the 3 edges around each new barycenter.

	   * For now, most pack information is lost; may try to fixup in the
	   * future. For example, should make stab at new radii, centers, maybe
	   * save overlaps, branching, etc., can interpolate new xyz locations 
	   * if there is xyz data. 
	   * Note: on error, p might be corrupted.
	   * @param mark int; mark for new barycenter verts
	   * @return int, 0 on error;
	  */
	  public int bary_refine(int mark) {
	    int orig_count=nodeCount;
	    if (hex_refine(1)==0) 
	    	return 0;
	    int min_new=orig_count+1;
	    int max_new=nodeCount;
	    boolean debug=false;
	    
	    // visit faces in order, find 'middle' faces (all verts new), add barys.
	    int count=0;
	    for (int f=1;f<=faceCount;f++) 
	      if (faces[f].vert[0]>=min_new && faces[f].vert[0]<=max_new
	      	&& faces[f].vert[1]>=min_new && faces[f].vert[1]<=max_new
	  		&& faces[f].vert[2]>=min_new && faces[f].vert[2]<=max_new) { 
	    	  if (debug) // debug=true;
	    		  System.err.println("f = "+f+" and vert[] = ["+faces[f].vert[0]+" "+faces[f].vert[1]+" "+faces[f].vert[2]+"]");
	    	  count +=add_barycenter(f,mark);
	      }

	    if (count==0) 
	    	return 0;

	    // Note: the barycenters have the higher vertex indices 
	    int j0,j1,j2;
	    for (int v=max_new+1;v<=nodeCount;v++) {
	      j0=kData[v].flower[0];
	      j1=kData[v].flower[1];
	      j2=kData[v].flower[2];
	      if (flip_edge(j0,j1,2)==0
	    		  || flip_edge(j1,j2,2)==0
	    		  || flip_edge(j2,j0,2)==0)
	    	  return 0;
	    }

	    setCombinatorics();
	    return count;
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
		int v, count = 0;

		Iterator<Integer> vlist = vertlist.iterator();
		while (vlist.hasNext()) {
			v = (Integer) vlist.next();
			if (isBdry(v) && countFaces(v) == 2) {
				if (!overlapStatus && alloc_overlaps() == 0)
					return 0;
				set_single_invDist(v, kData[v].flower[1], -0.5); // overlap 2*pi/3 
				set_single_invDist(v, kData[v].flower[0], 0.5); // overlap pi/3 
				set_single_invDist(v, kData[v].flower[2], 0.5); // overlap pi/3 
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
	   int count=0,setflag=0;

	   if (!status) return count;
	   if (!overlapStatus) {
	     alloc_overlaps();
	     setflag=1;
	   }
	   for (int v=1;v<=nodeCount;v++) {
	     if (!isBdry(v) && countFaces(v)==4) {
	       for (int j=0;j<4;j++) 
	 	  if (set_single_invDist(v,kData[v].flower[j],Math.cos(Math.PI/2.0))!=0) count++;
	     }
	   }
	   if (count==0 && setflag!=0) free_overlaps();
	   return count;
	 } 

	 /** 
	  * Slit a complex along simple edgepath created from given 
	  * 'vertlist'. (No vert should be repeated except perhaps the
	  * last one that closes the list.) Clone verts along the slit
	  * to end up with one on each side.
	  * 
	  * This will not disconnect complex: both ends boundary?
	  * or endpoints interior and equal, then error if path would 
	  * disconnect. If both ends are interior, there must be at 
	  * least two edges; if closed and interior, at least three.
	  * Verts, other than one or both endpoints, must be interior. 
	  *
	  * Replace 'vertexMap' with list {new,orig} of new (i.e., cloned) 
	  * verts with their orig indices.
	  *  
	  * CAUTION: Not very solid -- bad path may screw up complex. For interior cut,
	  * need at least two edges. Lose overlap data.
	  * 
	  * @param vertlist @see NodeLink of vertices defining edges to slit
	  * @return int[4], [cloneV,end,start,count]: cloneV (=0 means
	  * slit starts and ends at interior), end of slit, and start
	  * vertex are in counterclockwise order (positive) around the bdry.
	  * Count is the number of edges slit.
	  */
	 public int []slit_complex(NodeLink vertlist) {
	   int ind,back,forward,next,num,wnum,u;
	   int []oldflower=null;
	   int []newflower=null;
       Iterator<Integer> vlst=null;
       int []ans=null;

	   vlst=vertlist.iterator();
	   int initV=(int)vertlist.getFirst();
	   int finalV=(int)vertlist.getLast();

       // check endpoints, non-separation, simple, etc.
	   int []check=new int[nodeCount+1];
    	   
	   // is this a simple path (except for possibly closing up)?
	   int ecount=0;
	   while (vlst.hasNext()) {
		   int vv=vlst.next();
		   check[vv]++;
		   ecount++;
		   if (check[vv]>2 || (check[vv]>1 && vv!=initV)) // not simple
    		   throw new ParserException("check usage: edge chain not simple");
       }
	   
	   // if closed, does it have at least two edges?
	   if (initV==finalV && ecount<3)
		   throw new ParserException("closed with too few edges");
       
       // if closed, does it separate?
       if (check[initV]==2) { // closed path; check non-separation
    	   if (NodeLink.separates(this,vertlist)!=0)
    		   throw new ParserException("given vertices will separate the complex");
       }

       // may need to reverse list to start on bdry
       if (!isBdry(initV) && isBdry(finalV))
    	   vertlist=vertlist.reverseMe();
    	         
       // make up edgelist
       int v,w;
       try {
    	   vlst=vertlist.iterator(); 
    	   initV=v=(Integer)vlst.next();
    	   finalV=w=(Integer)vlst.next();
       } catch (Exception ex) {
    	   throw new ParserException("not a legitimate list");
       }
	       
       if (nghb(v,w)<0) {
    	   throw new ParserException("Verts "+v+" and "+w+" don't give an edge");
       }

	   EdgeLink edgelist=new EdgeLink(this,new EdgeSimple(v,w));
	   v=w;
	   try {
	       while(vlst.hasNext() && (!isBdry((w=(Integer)vlst.next())) || w!=finalV)
	    		   && nghb(v,w)>=0) { // next edge
	    	   edgelist.add(new EdgeSimple(v,w));
	    	   v=w;
	       } 
	       
	   } catch (Exception ex) {
		   throw new ParserException("error in forming edges");
	   }

	   // use the edgelist, keep track in vertexMap
	   vertexMap=new VertexMap();
	   Iterator<EdgeSimple> elst=edgelist.iterator();
	   EdgeSimple edge=(EdgeSimple)elst.next();
	   int currV=edge.v;
	   int nextV=edge.w;
	   ecount=1;
	   int newval=0;
	   
	   // keep track: is initial vertex interior?
	   boolean initInterior=true;
	   if (isBdry(edge.v))
		   initInterior=false;

	   // if 'currV' (='initV') is bdry vert, just start opening edges
	   if (isBdry(currV)) {
		   int []newvs=open_edge(currV,nextV);
		   if (newvs==null) {
			   throw new ParserException("slit: failed to open the first edge ("+currV+" "+nextV+")");
		   }
		   vertexMap.add(new EdgeSimple(newvs[0],currV));
	   }
	   // otherwise 'currV' (and full list) interior: must open 2 interior edges first
	   else { 
	       if (!elst.hasNext()) { // need at least two edges to start 
	    	   throw new ParserException("need at least two edges for interior start.");
	       }
	       edge=(EdgeSimple)elst.next();
	       w=nextV;
	       nextV=edge.w;
	       // three verts are (in order) currV,w,nextV, slitting towards endV
	       // need only one new vert (w splits into two) 
	       newval=++nodeCount;
	       alloc_pack_space(newval+1,true);
	       // fix v 
	       v=currV;
	       ind=nghb(v,w);
	       newflower=new int[countFaces(v)+1];
	       for (int k=1;k<=countFaces(v);k++)
	    	   newflower[k]=kData[v].flower[(k+ind) % countFaces(v)];
	       kData[v].flower=newflower;
	       kData[v].flower[0]=newval;
	       setBdryFlag(v,1);
	       // fix w
	       back=nghb(w,v);
	       next=nextV;
	       forward=nghb(w,next);
	       num=(forward+countFaces(w)-back) % (wnum=countFaces(w));
	       oldflower=new int[countFaces(w)+1];
	       for (int k=0;k<=countFaces(w);k++)
	    	   oldflower[k]=kData[w].flower[k];
	       newflower=new int[num+1];
	       for (int k=0;k<=num;k++)
	    	   newflower[k]=oldflower[(k+back) % wnum];
	       kData[w].flower=newflower;
	       kData[w].num=num;
	       setBdryFlag(w,1);
	       // fix next 
	       ind=nghb(next,w);
	       newflower=new int[countFaces(next)+1];
	       for (int k=0;k<countFaces(next);k++)
	    	   newflower[k]=kData[next].flower[(k+ind) %countFaces(next)];
	       kData[next].flower=newflower;
	       kData[next].flower[countFaces(next)]=newval;
	       setBdryFlag(next,1);
	       // fix newval 
	       num=(back+wnum-forward) % wnum;
	       kData[newval].flower=new int[num+1];
	       for (int k=1;k<num;k++)
	    	   kData[newval].flower[k]=oldflower[(k+forward) % wnum];
	       kData[newval].flower[0]=next;
	       kData[newval].flower[num]=v;
	       kData[newval].num=num;
	       setBdryFlag(newval,1);
	       for (int k=1;k<num;k++) {
	    	   back=nghb((u=kData[newval].flower[k]),w);
	    	   kData[u].flower[back]=newval;
	    	   if (back==0 && kData[u].flower[countFaces(u)]==w)
	    		   kData[u].flower[countFaces(u)]=newval;
	       }
	       setRadius(newval,getRadius(w));
	       setCenter(newval,getCenter(w));
	       ecount++;
	       vertexMap.add(new EdgeSimple(newval,w));
	   } // end of else
	   
	   // set up the array of return values
	   ans=new int[4];
	   if (!initInterior) ans[0]=newval; // zero if startV was interior
	   ans[2]=currV;
	   
	   // Now, just continue opening successive prescribed edges
	   while (elst.hasNext()) {
		   edge=(EdgeSimple)elst.next();
		   int []newvs=open_edge(edge);
		   if (newvs==null) {
			   return ans;
		   }
		   vertexMap.add(new EdgeSimple(newvs[0],edge.v));
		   if (newvs[1]!=0) // can only happen on last edge
			   vertexMap.add(new EdgeSimple(newvs[1],edge.w));
		   nextV=edge.w;
		   ecount++;
	   } // end of while
	   ans[1]=nextV; // note: last vert may have been cloned
	   ans[3]=ecount;
	   
	   setCombinatorics();
	   return ans;
	 } 
	 
	 /**
	  * Open interior edge {v,w}, v interior, edge interior.
	  * calling for cloning of v, and cloning of w if it is bdry. 
	  * Return 0 on failure and return index of the
	  * clone of the initial bdry vertex on success. 
	  * Note: in the positive direction (counterclockwise), the order 
	  * of verts is {clone, w, v} when w is interior.
	  * @param edge @see EdgeSimple interior edge
	  * @return int[2], indices new_V and new_W (or 0); null on error
	 */
	 public int []open_edge(EdgeSimple edge) {
		 return open_edge(edge.v,edge.w);
	 }
	 
	 /**
	  * Open interior edge from a boundary vertex v to neighbor w,
	  * calling for cloning of v, and cloning of w if it is bdry. 
	  * Return 0 on failure and return index of the
	  * clone of the initial bdry vertex on success. 
	  * Note: 'nodeCount' is adjusted
	  * Note: in the positive direction (counterclockwise), the order 
	  * of verts is {clone, w, v} when w is interior.
	  * @param v bdry vert
	  * @param w neighbor along an interior edge
	  * @return int[2], indices new_V and new_W (or 0); null on error
	 */
	public int []open_edge(int v, int w) {
		int ind;
		int[] newflower = null;
		double[] newoverlaps;
		int []ans=new int[2]; // may return 2 cloned indices

		if (v < 0 || v > nodeCount || !isBdry(v)
				|| (ind = nghb(v, w)) < 0 )
			return null;
		int indwv=nghb(w,v);
		// new vert
		int new_V = ans[0]=nodeCount+1;
		alloc_pack_space(new_V, true);
		nodeCount=new_V; // now can change nodecount;
		kData[new_V].num = countFaces(v) - ind;
		kData[new_V].flower = new int[countFaces(new_V) + 1];
		for (int k = 0; k <= countFaces(new_V); k++)
			kData[new_V].flower[k] = kData[v].flower[k + ind];
		if (overlapStatus) {
			kData[new_V].invDist = new double[countFaces(new_V) + 1];
			for (int k = 0; k <= countFaces(new_V); k++)
				set_single_invDist(new_V,kData[new_V].flower[k],getInvDist(v,kData[v].flower[k + ind]));
		} else
			kData[new_V].invDist = null;
		setBdryFlag(new_V,1);
		setAim(new_V,-1.0);
		setRadius(new_V,getRadius(v));
		setCenter(new_V,new Complex(getCenter(v)));
		
		// fix ngb's pointing to new_V (except for w, handled later)
		for (int k = 1; k <= countFaces(new_V); k++) {
			int u;
			int back = nghb((u = kData[v].flower[k + ind]), v);
			kData[u].flower[back] = new_V;
			if (back == 0 && !isBdry(u))
				kData[u].flower[countFaces(u)] = new_V;
		}
		
		// fix v's flower
		newflower = new int[ind + 1];
		for (int k = 0; k <= ind; k++)
			newflower[k] = kData[v].flower[k];
		kData[v].flower = newflower;
		if (kData[v].invDist != null) {
			newoverlaps = new double[ind + 1];
			for (int k = 0; k <= ind; k++)
				newoverlaps[k] = getInvDist(v,kData[v].flower[k]);
			kData[v].invDist = newoverlaps;
		} else
			kData[v].invDist=null;
		kData[v].num = ind;
		
		// consider w: if bdry, we have to clone it as well
		if (isBdry(w)) { // bdry
			int new_W=ans[1]=++nodeCount;
			alloc_pack_space(new_W, true);
			kData[new_W].num = indwv;
			kData[new_W].flower = new int[indwv + 1];
			for (int k = 0; k < countFaces(new_W); k++)
				kData[new_W].flower[k] = kData[w].flower[k];
			kData[new_W].flower[countFaces(new_W)]=new_V;  // points to v's clone
			if (overlapStatus) {
				kData[new_W].invDist = new double[countFaces(new_W) + 1];
				for (int k = 0; k <= countFaces(new_W); k++)
					set_single_invDist(new_W,kData[new_W].flower[k],getInvDist(w,kData[w].flower[k]));
			} else
				kData[new_W].invDist = null;
			setBdryFlag(new_W,1);
			setAim(new_W,-1.0);
			setRadius(new_W, getRadius(w));
			setCenter(new_W, new Complex(getCenter(w)));
			
			// fix ngb's pointing to neww
			kData[new_V].flower[0]=new_W;
			for (int k = 1; k <= countFaces(new_W); k++) {
				int u;
				int back = nghb((u = kData[w].flower[k]), w);
				kData[u].flower[back] = new_W;
				if (back == 0 && !isBdry(u))
					kData[u].flower[countFaces(u)] = new_W;
			}

			// fix w
			int num=countFaces(w);
			kData[w].num=num-indwv;
			newflower=new int[num-indwv+1];
			newflower[0]=v;
			for (int k=1;k<=countFaces(w);k++)
				newflower[k]=kData[w].flower[k+indwv];
			kData[w].flower=newflower;
		}
		else { // w was interior
			newflower = new int[countFaces(w) + 1];
			for (int k = 0; k < countFaces(w); k++)
				newflower[k] = kData[w].flower[(k + indwv) % countFaces(w)];
			newflower[countFaces(w)] = new_V;
			kData[w].flower = newflower;
			if (kData[w].invDist != null) {
				newoverlaps = new double[countFaces(w) + 1];
				for (int k = 0; k < countFaces(w); k++)
					newoverlaps[k] = getInvDist(w,kData[w].flower[(k + indwv) % countFaces(w)]);
				newoverlaps[countFaces(w)] = getInvDist(w,kData[w].flower[indwv]);
				kData[w].invDist = newoverlaps;
			}
			setBdryFlag(w,1);
		}
		setAim(w,-1.0);
		return ans;
	}
	 
	/**
	 * To see how much of this complex embeds in p2, starting from given
	 * pair of neighbors, adding faces as possible. The result is not
	 * unique, in general. For now, we are not very sophisticated; we
	 * are actually checking for embedding of the faces. Might end up with
	 * identifications for the 3 vertices of a face from p1 which are
	 * not vertices of a face in p2.

	 * a and A must be interiors of this and p2, respectively. Return 
	 * Edgelist (v, w), w the index in p2 corresponding to v in this. 

	 * Do our search on interior vertices of this: check degree first, then
	 * check that matched neigbhors are consistent. Cycle through two 
	 * linked lists.

	 * Keep vstat, Vstat arrays of vertex identifications for this and p2.
	 * @return @see EdgeLink or null on error or embedding failure.
	 */
	 public EdgeLink embedding(PackData p2,int a,int b,int A,int B) {
	   int U=0,num,v,V,indv,indV,i,u;
	   NodeLink news=null;
	   EdgeLink result=null;

	   if (!status || !p2.status 
	       || (indv=nghb(a,b))<0 || (indV=p2.nghb(A,B))<0 
	       || isBdry(a) || isBdry(A)
	       || countFaces(a)!=p2.countFaces(A))
	      return null;

	   int []vstat=new int[nodeCount+1];
	   int []Vstat=new int[p2.nodeCount+1];
	   NodeLink big=new NodeLink(this,a);
	   big.add(b);
	   vstat[a]=A;Vstat[A]=a;
	   vstat[b]=B;Vstat[B]=b;

	   // entering main loop 
	   while (big!=null && big.size()>0) {
	       news=new NodeLink(this);
	       Iterator<Integer> bigtr=big.iterator();
	       while (bigtr.hasNext()) {
	 	  v=(Integer)bigtr.next();
	 	  V=vstat[v];
	 	  if (!isBdry(v) && !isBdry(V)
	 	      && (num=countFaces(v))==p2.countFaces(V)) {
	 	      i=0;
	 	      while (i<num && (U=vstat[(u=kData[v].flower[i])])==0) i++;
	 	      if (i==num) return null;
	 	      indv=i;
	 	      indV=p2.nghb(V,U);
	 	      for (int ii=1;ii<num;ii++) {
	 		  u=getPetal(v,(indv+ii)%num);
	 		  U=p2.getPetal(V,(indV+ii)%num);
	 		  if (vstat[u]==0 && Vstat[U]==0) { // new identification 
	 		      vstat[u]=U;
	 		      Vstat[U]=u;
	 		      news.add(u);
	 		    }
	 	      }
	 	  }
	       } // end of inner while 
	       big=news;
	   }
	   result=new EdgeLink(this);
	   EdgeSimple edge=null;
	   for (int uu=1;uu<=nodeCount;uu++)
	       if ((U=vstat[uu])!=0) {
	    	   edge=new EdgeSimple(uu,U);
	    	   result.add(edge);
	       }
	   return result;
	 } 
	   
	 /**
	  * If hyperbolic, apply a Mobius trans of disc putting ctr at origin. If
	  * euclidean, translate. If sphere, rigid Mobius moves ctr to north pole.
	  */
	public int center_point(Complex ctr) {
		Complex z1, z2;
		Complex z3 = new Complex(0.0);
		double radius;
		RedList trace;
		boolean keepon = true;

		if (hes < 0) {
			if ((ctr.x * ctr.x + ctr.y * ctr.y) > (1.0 - TOLER)) {
				flashError("usage: center_point: chosen point is too "
						+ "close to ideal boundary.");
				return 0;
			}
			for (int i = 1; i <= nodeCount; i++) {
				// catch horocycles to modify their radii
				radius = (-1)*getRadius(i);
				if (radius > 0) { // yes, a horocycle
					z1 = getCenter(i);
					z2 = z1.times(1.0 - 2.0 * radius);
					z3.x = (1 - radius) * z1.x - radius * z1.y;
					z3.y = (1 - radius) * z1.y + radius * z1.x;
					z1 = Mobius.mob_trans(z1, ctr);
					z2 = Mobius.mob_trans(z2, ctr);
					z3 = Mobius.mob_trans(z3, ctr);
					CircleSimple sc = EuclMath.circle_3(z1, z2, z3);
					setRadius(i,(-sc.rad));
				}
				setCenter(i,Mobius.mob_trans(getCenter(i), ctr));
				setCenter(i,getCenter(i).times(-1.0));
			}
			if ((trace = redChain) != null) // adjust centers in red list
				while (trace != redChain || keepon) {
					keepon = false;
					int j = faces[trace.face].vert[trace.vIndex];
					// again, catch horocycles
					radius = (-(trace.rad));
					if (radius > 0) {
						z1 = getCenter(j);
						z2 = z1.times(1.0 - 2.0 * radius);
						z3.x = (1 - radius) * z1.x - radius * z1.y;
						z3.y = (1 - radius) * z1.y + radius * z1.x;
						z1 = Mobius.mob_trans(z1, ctr);
						z2 = Mobius.mob_trans(z2, ctr);
						z3 = Mobius.mob_trans(z3, ctr);
						CircleSimple sc = EuclMath.circle_3(z1, z2, z3);
						trace.rad = (-sc.rad);
					}
					if (trace.center==null) {
						throw new RedListException("The redChain center for face "+trace.face+" is 'null'");
					}
					trace.center = Mobius.mob_trans(trace.center, ctr);
					trace.center=trace.center.times(-1.0);
					trace = trace.next;
				}
			if (sidePairs != null)
				// fix side-pairing mobius transforms also
				update_pair_mob();
			return 1;
		} 
		else if (hes>0) { // sph
			Matrix3D m3d=Matrix3D.rigid2North(ctr);
			for (int i = 1; i <= nodeCount; i++) {
				Point3D pt=Matrix3D.times(m3d,new Point3D(getCenter(i))); // pt.norm();
				setCenter(i,Point3D.p3D_2_sph(pt)); // pt.norm();
			}
			return 1;
		}
		else { // eucl
			for (int i = 1; i <= nodeCount; i++) {
				Complex zc=getCenter(i).minus(ctr);
				setCenter(i,zc);
			}
			keepon = true;
			if ((trace = redChain) != null)
				while (trace != redChain || keepon) {
					keepon = false;
					trace.center = trace.center.minus(ctr);
					trace = trace.next;
				}
			if (sidePairs != null)
				// fix side-pairing mobius transforms also
				update_pair_mob();
			return 1;
		}
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
	 * Layout faces along a given facelist, recomputing as you go
	 * @param facelist
	 * @param last_face, if non-zero, try to place first face of
	 * list contiguous to last_face
	 * @return last_face
	 */
	public int reLayList(FaceLink facelist,int last_face) {
		int next_face=0;
		Iterator<Integer> flist = facelist.iterator();
		if (last_face < 0 || last_face > faceCount)
			last_face = 0;
		while (flist.hasNext()) {
			next_face = (Integer) flist.next();
			// skip repeated and illegal indices
			if (next_face != last_face && next_face > 0
					&& next_face <= faceCount) {
				int jj=0;
				int j=0;
				// if next_face/last_face share edge, place contiguously
				if ((jj = face_nghb(last_face, next_face)) >= 0)
						j = jj;
				else // default to placing 'indexFlag' vert
					j = faces[next_face].indexFlag;
				int v0 = faces[next_face].vert[j];
				int v1 = faces[next_face].vert[(j + 1) % 3];
				int v2 = faces[next_face].vert[(j + 2) % 3]; // this is one computed

				CircleSimple sc;
				if (overlapStatus) { // oj for edge opposite vj
					double o0 = getInvDist(v1,v2);
					double o1 = getInvDist(v2,v0);
					double o2 = getInvDist(v0,v1);
					sc = CommonMath.comp_any_center(getCenter(v0),getCenter(v1),
							getRadius(v0), getRadius(v1),
							getRadius(v2), o0, o1, o2,hes);
				} 
				else
					sc = CommonMath.comp_any_center(getCenter(v0), getCenter(v1),
							getRadius(v0), getRadius(v1),getRadius(v2),hes);
				// compute and store new center
				sc.save(this, v2);
			}
		last_face=next_face;
		} // end of while
		return last_face;
	}
	
	/**
	 * Recompute the centers of circles as determined by ordered 'facelist'.
	 * Assume the first face is already in desired location (but note that it
	 * may occur again in the list and be relocated). For contiguous faces
	 * g, f in the list, compute and store the center for circle of f opposite g. 
	 * If g and f don't share an edge, skip layout for f; result may then be 
	 * ambiguous. 
	 * 
	 * Similar to 'layout_facelist', except there are no drawing operations.
	 * 
	 * @param facelist @see FaceLink
	 * @return int, count of centers computed.
	 */
	public int recomp_facelist(FaceLink facelist) {
		int count=0;
		if (facelist==null || facelist.size()<2)
			return count;
		int []myVerts=new int[3];
		double []myRadii=new double[3];
		Complex []myCenters=new Complex[3];
		double []myOvl=new double[3];
		
		// go through the list
		Iterator<Integer> flist=facelist.iterator();
		int next=flist.next();
		int curr=next;
		while (flist.hasNext()) {
			curr=next;
			next=flist.next();
			int k=-1;
			if ((k=this.face_nghb(curr,next))>=0) {
				myVerts[0] = faces[next].vert[k];
				myVerts[1] = faces[next].vert[(k+1)%3];
				myVerts[2] = faces[next].vert[(k+2)%3]; // this is one computed

				// radii to use?
				myRadii[0]=getRadius(myVerts[0]);
				myRadii[1]=getRadius(myVerts[1]);
				myRadii[2]=getRadius(myVerts[2]);

				// centers
				myCenters[0] = getCenter(myVerts[0]);
				myCenters[1] = getCenter(myVerts[1]);
				myCenters[2] = getCenter(myVerts[2]);

				// compute new center
				CircleSimple sc;
				if (overlapStatus) { // oj for edge opposite vj
					myOvl[0] = getInvDist(myVerts[1],myVerts[2]);
					myOvl[1] = getInvDist(myVerts[2],myVerts[0]);
					myOvl[2] = getInvDist(myVerts[0],myVerts[1]);
					sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
							myRadii[2],myOvl[0],myOvl[1],myOvl[2],hes);
				} 
				else {
					sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
							myRadii[2],hes);
				}
				
				// store the new center
				sc.save(this, myVerts[2]);
				count++;
			}
		} // end of while
		return count;
	}
	
	/** 
	 * TODO: convert to use of 'layoutTree' in place of this routine.
	 * 
	 * Draw or post circles and/or faces along a specified linked
	 * list of faces, possibly redrawing as you proceed.
	 * @param pF PostFactory: if non-null, post rather than draw
	 * @param facelist FaceLink: linked list of faces.
	 * @param faceFlags DispFlags, if null or bit flags 0, don't do faces, 
	 * @param circFlags DispFlags, if null or bit flags 0, don't do circles,
	 * (Note: strip off '-f', '-F', '-B', '-C' portion of the flags)
	 * @param fix boolean, true means to locate faces contiguously when they
	 *    share edges (thus changing some stored centers)
	 * @param useRed boolean, true (in conjunction with fix) means that when
	 *    laying out a red face, if predecessor is the previous red
	 *    face in the redchain, then use the red data (i.e., use rad
	 *    stored in red face data). (Used, e.g., for affine tori.)
	 * @param last_face int, indicates last live face, layout starts there
	 * @param tx double, thickness factor if positive, only for post calls
	 * @return int, the new 'lastface' 
	*/
	public int layout_facelist(PostFactory pF,FaceLink facelist,
			DispFlags faceFlags,DispFlags circFlags,boolean fix,boolean useRed,
			int last_face,double tx) {
		
		if (facelist == null || facelist.size() == 0)
			return 0;
		if (!fix) useRed=false;
		RedList redlist=redChain;
		// deBugging.LayoutBugs.log_RedList(this,this.redChain);
		
		// faces?
		boolean faceDo=false;
		if (faceFlags!=null && faceFlags.draw)
			faceDo=true;
		
		// circles?
		boolean circDo=false;
		if (circFlags!=null && circFlags.draw)
			circDo=true;

		if (!faceDo && !circDo) return 0;

		int next_face=0;
		int j, jj;
		int []myVerts=new int[3];
		double []myRadii=new double[3];
		Complex []myCenters=new Complex[3];
		double []myOvl=new double[3];
		
//System.err.println("\n enter layout_facelist");

		Iterator<Integer> flist = facelist.iterator();
		int wflag=0;
		if (last_face < 0 || last_face > faceCount)
			last_face = 0;
		while (flist.hasNext()) {
			next_face = (Integer) flist.next();
			// skip repeated and illegal indices
			if (next_face > 0 && next_face <= faceCount &&
					(0==wflag++ || (next_face != last_face))) {
				// note: 'wflag' lets next_face==last_face through on first pass only.
				//   this is needed because we often have first in facelist equal to
				//   last_face so it will be drawn but not fixed.
				j = faces[next_face].indexFlag;
				myVerts[0] = faces[next_face].vert[j];
				myVerts[1] = faces[next_face].vert[(j + 1) % 3];
				myVerts[2] = faces[next_face].vert[(j + 2) % 3]; // this is one computed
				
				// when 'fix' is set, we recompute centers
				if (fix && last_face!=next_face) { 
					// if next_face/last_face share edge, place contiguously by
					//     rejiggering the order
					if ((jj = face_nghb(last_face, next_face)) >= 0 && jj!=j) {
						j = jj;
						myVerts[0] = faces[next_face].vert[j];
						myVerts[1] = faces[next_face].vert[(j + 1) % 3];
						myVerts[2] = faces[next_face].vert[(j + 2) % 3]; // this is one computed
					}
//System.err.println(" next_face = "+next_face+"; v0="+myVerts[0]+" v1="+myVerts[1]+" v2="+myVerts[2]);

					// radii to use?
					myRadii[0]=getRadius(myVerts[0]);
					myRadii[1]=getRadius(myVerts[1]);
					myRadii[2]=getRadius(myVerts[2]);

					// centers
					myCenters[0] = getCenter(myVerts[0]);
					myCenters[1] = getCenter(myVerts[1]);
					myCenters[2] = getCenter(myVerts[2]);
					
					// Want to use 'red-to-red'? (that is, 'useRed' is set, this
					//    face is red, and 'next_face' is 'nextRed'.
					//    Need to get 'redlist' location for 'next_face' so we
					//    use the radius stored in redface data.
					if (useRed && last_face>0 && faces[last_face].nextRed==next_face) {
						redlist=findContigRed(redlist,last_face,next_face);
						if (redlist==null || redlist.face!=next_face)
							throw new CombException("Error in laying out red segment");
//System.err.println(" this is red: "+next_face);						
						int next_vert=faces[redlist.face].vert[redlist.vIndex];
						int hitindx=-1;
						for (int k=0;(k<3 && hitindx<0);k++) {
							if (next_vert==myVerts[k])
								hitindx=k;
						}
							
						// we found the local index; use redchain radius
						if (hitindx>=0) {
							myRadii[hitindx]=rData[myVerts[hitindx]].rad=redlist.rad;
//System.err.println(" hitindx="+hitindx+", vert="+myVerts[hitindx]+", and rad="+redlist.rad);								
							if (hitindx!=2) { // rotate so computed center is index 2
								int myvert=myVerts[hitindx];
								double myrad=myRadii[hitindx];
								for (int m=0;m<2;m++) {
									myVerts[(m+hitindx)%3]=myVerts[(m+hitindx+1)%3];
									myRadii[(m+hitindx)%3]=myRadii[(m+hitindx+1)%3];
									myCenters[(m+hitindx)%3]=new Complex(myCenters[(m+hitindx+1)%3]);
								}
								myVerts[2]=myvert;
								myRadii[2]=myrad;
								// note: don't need to set myCenter[2]
							}
						}
					}
					
					// compute and store new center
					CircleSimple sc;
					if (overlapStatus) { // oj for edge opposite vj
						myOvl[0] = getInvDist(myVerts[1],myVerts[2]);
						myOvl[1] = getInvDist(myVerts[2],myVerts[0]);
						myOvl[2] = getInvDist(myVerts[0],myVerts[1]);
						sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
								myRadii[2],myOvl[0],myOvl[1],myOvl[2],hes);
					} 
					else {
						sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
								myRadii[2],hes);
					}
					sc.save(this, myVerts[2]);
				}
				
				// get centers/radii
				myCenters[0] = getCenter(myVerts[0]);
				myCenters[1] = getCenter(myVerts[1]);
				myCenters[2] = getCenter(myVerts[2]);
				
				myRadii[0]=getRadius(myVerts[0]);
				myRadii[1]=getRadius(myVerts[1]);
				myRadii[2]=getRadius(myVerts[2]);
				
				if (faceDo && pF==null) { // draw the faces
					if (!faceFlags.colorIsSet && 
							(faceFlags.fill || faceFlags.colBorder)) 
						faceFlags.setColor(getFaceColor(next_face));
					if (faceFlags.label)
						faceFlags.setLabel(Integer.toString(next_face));
					cpScreen.drawFace(myCenters[0],myCenters[1],myCenters[2],
							myRadii[0],myRadii[1],myRadii[2],faceFlags);
				}
				if (circDo && pF==null) { // also draw the circles
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder)) 
						circFlags.setColor(getCircleColor(myVerts[2]));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(myVerts[2]));
					cpScreen.drawCircle(myCenters[2],
							getRadius(myVerts[2]),circFlags);
				}
				
				if (pF!=null && hes>0) { // post routines don't know how to convert
					myCenters[0]=new Complex(cpScreen.sphView.toApparentSph(myCenters[0]));
					myCenters[1]=new Complex(cpScreen.sphView.toApparentSph(myCenters[1]));
					myCenters[2]=new Complex(cpScreen.sphView.toApparentSph(myCenters[2]));
				}

				if (faceDo && pF!=null) { // also post the faces
					
					// set face/bdry colors
					Color fcolor=null;
					Color bcolor=null;
					if (faceFlags.fill) {  
						if (!faceFlags.colorIsSet) 
							fcolor=getFaceColor(next_face);
						if (faceFlags.colBorder)
							bcolor=fcolor;
					}
					if (faceFlags.draw) {
						if (faceFlags.colBorder)
							bcolor=getFaceColor(next_face);
						else 
							bcolor=ColorUtil.getFGColor();
					}
					pF.post_Poly(hes, myCenters, fcolor, bcolor, tx);

					if (faceFlags.label) { // label the face
						Complex z=face_center(next_face);
						if (hes>0) {
							z=cpScreen.sphView.toApparentSph(z);
							if(Math.cos(z.x)>=0.0) {
								z=util.SphView.s_pt_to_visual_plane(z);
								pF.postIndex(z,next_face);
							}
						}
						else pF.postIndex(z,next_face);
					}
				}
				if (circDo && pF!=null) { // also post the circles
					if (!circFlags.fill) { // not filled
						if (circFlags.colBorder)
							pF.postColorCircle(hes,myCenters[2],
									getRadius(myVerts[2]),getCircleColor(myVerts[2]),tx);
						else 
							pF.postCircle(hes,myCenters[2],getRadius(myVerts[2]),tx);
					} 
					else {
						Color ccOl=ColorUtil.getFGColor();
						if (!circFlags.colorIsSet)
							ccOl = getCircleColor(myVerts[2]);
						if (circFlags.colBorder) {
							pF.postFilledColorCircle(hes,myCenters[2],getRadius(myVerts[2]),ccOl,ccOl,tx);
						}
						else 
							pF.postFilledCircle(hes,myCenters[2],getRadius(myVerts[2]),ccOl,tx);
					}
					if (circFlags.label) { // label the face
						if (hes>0) {
							Complex z=cpScreen.sphView.toApparentSph(myCenters[2]);
							if(Math.cos(z.x)>=0.0) {
								z=util.SphView.s_pt_to_visual_plane(z);
								pF.postIndex(z,myVerts[2]);
							}
						}
						else pF.postIndex(myCenters[2],myVerts[2]);
					}
				}
			}
			last_face=next_face;
		} // end of while through facelist
		return last_face;
	}

	/** 
	 * Create 'TriAspect' face data, which contains data face-by-face
	 * for use, e.g., in 'Schwarzian' and 'ProjStruct'. This does not
	 * change data in the packing itself.
	 * @return int count 
	 */
	public static TriAspect []getTriAspects(PackData p) {
		// get drawing order (plus stragglers, i.e., not needed in drawing order)
		GraphLink facetree=new GraphLink(p,"s");
		if (facetree==null || facetree.size()==0)
			throw new CombException("Failed to get drawing order tree");
		int count=0; 
		
		TriAspect []aspect=new TriAspect[p.faceCount+1];
		
		int next_spot=0; // last spot for root; may be more components
		int next_root_indx=-1;
		while ((next_root_indx=facetree.findRootSpot(next_spot))>=0) {
			next_spot++;
			int new_root=facetree.get(next_root_indx).w;
			if (new_root<=0 || new_root>p.faceCount)
				break;
			
			GraphLink thisTree=facetree.extractComponent(new_root);
			Iterator<EdgeSimple> elist = thisTree.iterator();
			while (elist.hasNext()) {
				EdgeSimple edge = elist.next();
				int last_face=edge.v;
				int next_face=edge.w;
				
				// create TriAspect 
				aspect[next_face]=new TriAspect(p.hes);
				aspect[next_face].face=next_face;
				aspect[next_face].vert=new int[3];
				aspect[next_face].vert[0] = p.faces[next_face].vert[0];
				aspect[next_face].vert[1] = p.faces[next_face].vert[1];
				aspect[next_face].vert[2] = p.faces[next_face].vert[2]; 
				aspect[next_face].allocCenters();
				
				// set rad/centers; root face must be placed already
				if (last_face==0) { // this is root, use stored centers
					int []mv=p.faces[next_face].vert;
					aspect[next_face].setCenter(new Complex(p.getCenter(mv[0])),0);
					aspect[next_face].setCenter(new Complex(p.getCenter(mv[1])),1);
					aspect[next_face].setCenter(new Complex(p.getCenter(mv[2])),2);
					aspect[next_face].setRadius(p.getRadius(mv[0]),0);
					aspect[next_face].setRadius(p.getRadius(mv[1]),1);
					aspect[next_face].setRadius(p.getRadius(mv[2]),2);
				}
				
				// else recompute center of opposite vert of next_face
				else {
					int j=p.face_nghb(next_face,last_face);
					int jj=p.face_nghb(last_face, next_face);
					if (j<0 || jj< 0) {
						throw new CombException("face "+next_face+" not aligned with "+next_face);
					}

					// store data of next_face using shared edge with last_face
					int []myVerts=new int[3];
					myVerts[0]=p.faces[next_face].vert[jj];
					myVerts[1]=p.faces[next_face].vert[(jj+1)%3];
					myVerts[2]=p.faces[next_face].vert[(jj+2)%3];
					double []myRadii=new double[3];
					myRadii[0]=p.getRadius(myVerts[0]);
					myRadii[1]=p.getRadius(myVerts[1]);
					myRadii[2]=p.getRadius(myVerts[2]);
					Complex []myCenters=new Complex[3]; // two centers from aspect of last_face
					myCenters[0]=new Complex(aspect[last_face].getCenter((j+1)%3));
					myCenters[1]=new Complex(aspect[last_face].getCenter(j));

					// compute new center for aspect for next_face
					CircleSimple sc;
					if (p.overlapStatus) { // oj for edge opposite vj
						double []myOvl=new double[3];
						myOvl[0] = p.getInvDist(myVerts[1],myVerts[2]);
						myOvl[1] = p.getInvDist(myVerts[2],myVerts[0]);
						myOvl[2] = p.getInvDist(myVerts[0],myVerts[1]);
						sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
								myRadii[2],myOvl[0],myOvl[1],myOvl[2],p.hes);
					} 
					else {
						sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
								myRadii[2],p.hes);
					}
					
					aspect[next_face].setCenter(myCenters[0],jj);
					aspect[next_face].setCenter(myCenters[1],(jj+1)%3);
					aspect[next_face].setCenter(sc.center,(jj+2)%3);
					aspect[next_face].setRadius(myRadii[0],jj);
					aspect[next_face].setRadius(myRadii[1],(jj+1)%3);
					aspect[next_face].setRadius(myRadii[2],(jj+2)%3);
				}

				// compute/store the tangency points
				DualTri dtri=new DualTri(p.hes,aspect[next_face].getCenter(0),
						aspect[next_face].getCenter(1),aspect[next_face].getCenter(2));
				aspect[next_face].tanPts=new Complex[3];
				for (int j=0;j<3;j++)
					aspect[next_face].tanPts[j]=new Complex(dtri.TangPts[j]);

				count++;
			} // end of while through edgelist

			if (count!=p.faceCount) {
				throw new CombException("didn't get all faces in building 'TriAspect'");
			}
			
		} // end of while through trees
		return aspect;
	}

	/** 
	 * Recompute, draw, and/or post circles and/or faces along a 
	 * specified GraphLink tree. NOTE: must be a tree, so starts with
	 * {0,f} root. 
	 * @param pF PostFactory
	 * @param faceTree GraphLink
	 * @param faceFlags DispFlags, may be null
	 * @param circFlags DispFlags, may be null
	 * @param fix boolean
	 * @param useRed boolean
	 * @param tx double
	 * @return int count 
	 */
	public int layoutTree(PostFactory pF,GraphLink faceTree,
			DispFlags faceFlags,DispFlags circFlags,boolean fix,
			boolean useRed,double tx) {

		boolean debug=false;
		
		// debug=true;
		if (debug) {
			LayoutBugs.log_GraphLink(this,faceTree);
		}
		
		int count=0; // CPBase.Glink=faceTree;DualGraph.printGraph(faceTree);
		if (faceTree == null || faceTree.get(0).v!=0) // not a tree?
			return count;
		if (!fix) useRed=false;
		RedList redlist=redChain;
		
		// not drawing anything? this just serves to redo layout
		boolean faceDo=false;
		if (faceFlags!=null && faceFlags.draw)
			faceDo=true;
		boolean circDo=false;
		if (circFlags!=null && circFlags.draw)
			circDo=true;
		
		// deBugging.LayoutBugs.log_RedList(this,this.redChain);

		int next_spot=0; // last spot for root; may be more components
		int j, jj;
		int []myVerts=new int[3];
		double []myRadii=new double[3];
		Complex []myCenters=new Complex[3];
		double []myOvl=new double[3];
		int next_root_indx=-1;
		while ((next_root_indx=faceTree.findRootSpot(next_spot))>=0) {
			next_spot++;
			int new_root=faceTree.get(next_root_indx).w;
			if (new_root<=0 || new_root>faceCount)
				break;
			
			GraphLink thisTree=faceTree.extractComponent(new_root);
			Iterator<EdgeSimple> elist = thisTree.iterator();
			while (elist.hasNext()) {
				EdgeSimple edge = elist.next();
				int last_face=edge.v;
				int next_face=edge.w;
				j=faces[next_face].indexFlag; // using stored info
				myVerts[0] = faces[next_face].vert[j];
				myVerts[1] = faces[next_face].vert[(j + 1) % 3];
				myVerts[2] = faces[next_face].vert[(j + 2) % 3]; // this is one computed
				
				// when 'fix' is set, we recompute centers and rejigger the order
				if (fix && last_face>0) { 
					// if next_face/last_face share edge, change order and place contiguously
					if ((jj = face_nghb(last_face, next_face)) >= 0 && jj!=j) {
						j = jj;
						myVerts[0] = faces[next_face].vert[j];
						myVerts[1] = faces[next_face].vert[(j + 1) % 3];
						myVerts[2] = faces[next_face].vert[(j + 2) % 3]; // this is one computed
					}
//System.err.println(" next_face = "+next_face+"; v0="+myVerts[0]+" v1="+myVerts[1]+" v2="+myVerts[2]);

					// radii to use?
					myRadii[0]=getRadius(myVerts[0]);
					myRadii[1]=getRadius(myVerts[1]);
					myRadii[2]=getRadius(myVerts[2]);

					// centers
					myCenters[0] = getCenter(myVerts[0]);
					myCenters[1] = getCenter(myVerts[1]);
					myCenters[2] = getCenter(myVerts[2]);
					
					// Want to use 'red-to-red'? (that is, 'useRed' is set, this
					//    face is red, and 'next_face' is 'nextRed'.
					//    Need to get 'redlist' location for 'next_face' so we
					//    use the radius stored in redface data.
					if (useRed && last_face>0 && faces[last_face].nextRed==next_face) {
						redlist=findContigRed(redlist,last_face,next_face);
						if (redlist==null || redlist.face!=next_face)
							throw new CombException("Error in laying out red segment");
//System.err.println(" this is red: "+next_face);						
						int next_vert=faces[redlist.face].vert[redlist.vIndex];
						int hitindx=-1;
						for (int k=0;(k<3 && hitindx<0);k++) {
							if (next_vert==myVerts[k])
								hitindx=k;
						}
							
						// we found the local index; use redchain radius
						if (hitindx>=0) {
							myRadii[hitindx]=redlist.rad;
							setRadius(myVerts[hitindx],redlist.rad);
//System.err.println(" hitindx="+hitindx+", vert="+myVerts[hitindx]+", and rad="+redlist.rad);								
							if (hitindx!=2) { // rotate so computed center is index 2
								int myvert=myVerts[hitindx];
								double myrad=myRadii[hitindx];
								for (int m=0;m<2;m++) {
									myVerts[(m+hitindx)%3]=myVerts[(m+hitindx+1)%3];
									myRadii[(m+hitindx)%3]=myRadii[(m+hitindx+1)%3];
									myCenters[(m+hitindx)%3]=new Complex(myCenters[(m+hitindx+1)%3]);
								}
								myVerts[2]=myvert;
								myRadii[2]=myrad;
								// note: don't need to set myCenter[2]
							}
						}
					}
					
					// compute and store new center
					CircleSimple sc;
					if (overlapStatus) { // oj for edge opposite vj
						myOvl[0] = getInvDist(myVerts[1],myVerts[2]);
						myOvl[1] = getInvDist(myVerts[2],myVerts[0]);
						myOvl[2] = getInvDist(myVerts[0],myVerts[1]);
						sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
								myRadii[2],myOvl[0],myOvl[1],myOvl[2],hes);
					} 
					else {
						sc = CommonMath.comp_any_center(myCenters[0],myCenters[1],myRadii[0],myRadii[1],
								myRadii[2],hes);
					}
					sc.save(this, myVerts[2]);
				}
				
				// get centers/radii
				myCenters[0] = getCenter(myVerts[0]);
				myCenters[1] = getCenter(myVerts[1]);
				myCenters[2] = getCenter(myVerts[2]);
				
				myRadii[0]=getRadius(myVerts[0]);
				myRadii[1]=getRadius(myVerts[1]);
				myRadii[2]=getRadius(myVerts[2]);
				
				if (faceDo && pF==null) { // draw the faces
					if (!faceFlags.colorIsSet && 
							(faceFlags.fill || faceFlags.colBorder)) 
						faceFlags.setColor(getFaceColor(next_face));
					if (faceFlags.label)
						faceFlags.setLabel(Integer.toString(next_face));
					cpScreen.drawFace(myCenters[0],myCenters[1],myCenters[2],
							myRadii[0],myRadii[1],myRadii[2],faceFlags);
				}
				
				if (circDo && pF==null) { // also draw the circles
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder)) 
						circFlags.setColor(getCircleColor(myVerts[2]));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(myVerts[2]));
					cpScreen.drawCircle(myCenters[2],
							getRadius(myVerts[2]),circFlags);
				}
				
				if (pF!=null && hes>0) { // post routines don't know how to convert
					myCenters[0]=new Complex(cpScreen.sphView.toApparentSph(myCenters[0]));
					myCenters[1]=new Complex(cpScreen.sphView.toApparentSph(myCenters[1]));
					myCenters[2]=new Complex(cpScreen.sphView.toApparentSph(myCenters[2]));
				}

				// postscript
				if (faceDo && pF!=null) { // also post the faces
					
					// set face/bdry colors
					Color fcolor=null;
					Color bcolor=null;
					if (faceFlags.fill) {  
						if (!faceFlags.colorIsSet) 
							fcolor=getFaceColor(next_face);
						if (faceFlags.colBorder)
							bcolor=fcolor;
					}
					if (faceFlags.draw) {
						if (faceFlags.colBorder)
							bcolor=getFaceColor(next_face);
						else 
							bcolor=ColorUtil.getFGColor();
					}
					pF.post_Poly(hes, myCenters, fcolor, bcolor, tx);

					if (faceFlags.label) { // label the face
						Complex z=face_center(next_face);
						if (hes>0) {
							z=cpScreen.sphView.toApparentSph(z);
							if(Math.cos(z.x)>=0.0) {
								z=util.SphView.s_pt_to_visual_plane(z);
								pF.postIndex(z,next_face);
							}
						}
						else pF.postIndex(z,next_face);
					}
				}
				if (circDo && pF!=null) { // also post the circles
					if (!circFlags.fill) { // not filled
						if (circFlags.colBorder)
							pF.postColorCircle(hes,myCenters[2],getRadius(myVerts[2]),
									getCircleColor(myVerts[2]),tx);
						else 
							pF.postCircle(hes,myCenters[2],getRadius(myVerts[2]),tx);
					} 
					else {
						Color ccOl=ColorUtil.getFGColor();
						if (!circFlags.colorIsSet)
							ccOl = getCircleColor(myVerts[2]);
						if (circFlags.colBorder) {
							pF.postFilledColorCircle(hes,myCenters[2],
									getRadius(myVerts[2]),ccOl,ccOl,tx);
						}
						else 
							pF.postFilledCircle(hes,myCenters[2],
									getRadius(myVerts[2]),ccOl,tx);
					}
					if (circFlags.label) { // label the face
						if (hes>0) {
							Complex z=cpScreen.sphView.toApparentSph(myCenters[2]);
							if(Math.cos(z.x)>=0.0) {
								z=util.SphView.s_pt_to_visual_plane(z);
								pF.postIndex(z,myVerts[2]);
							}
						}
						else pF.postIndex(myCenters[2],myVerts[2]);
					}
				}
				count++;
			} // end of while through edgelist

		} // end of while through trees
		return count;
	}

	/**
	 * Given faces f, g sharing edge e, assume circles for e are in place.
	 * Compute and store the circle of g across from f.
	 * @param f int
	 * @param g int
	 * @return boolean, true on success
	 */
	public boolean layByFaces(int f,int g) {
		int k=-1;
		if ((k=this.face_nghb(f,g))<0)
			return false;
		int v0 = faces[g].vert[k];
		int v1 = faces[g].vert[(k+1)%3];
		int v2 = faces[g].vert[(k+2)%3]; // this is one the one to compute

		// radii to use?
		double r0=getRadius(v0);
		double r1=getRadius(v1);
		double r2=getRadius(v2);

		// compute new center
		CircleSimple sc;
		double o0=1.0;
		double o1=1.0;
		double o2=1.0;
		if (overlapStatus) { // oj for edge opposite vj
			o0=getInvDist(v1,v2);
			o1=getInvDist(v2,v0);
			o2=getInvDist(v0,v1);
			sc = CommonMath.comp_any_center(getCenter(v0),getCenter(v1),r0,r1,r2,o0,o1,o2,hes);
		} 
		else {
			sc = CommonMath.comp_any_center(getCenter(v0),getCenter(v1),r0,r1,r2,hes);
		}
			
		// store the new center
		sc.save(this, v2);
		return true;
	}
	
	/**
	 * Does the work for the "get_data" and "put_data" calls, depending
	 * on 'putget' flag. If 'put', then data goes from 'this' to 'q';
	 * 'get' goes from 'q' to 'this'. Caution: 'translate' true means
	 * the 'VertexMap' of 'this' is used to associate source with target.
	 * So use of 'put' or 'get' with translation depends on who has the
	 * appropriate 'VertexMap'.
	 * TODO: if there are multiple translations, we only use the first currently
	 * @param q, other packing
	 * @param flagsegs, vector of vectors of strings
	 * @param putget, true==>'put' from 'this' to q, false==>'get' from q to 'this'
	 * @param transl, true==>'translate' using VertexMap of 'this' 
	 * @return int = count of actions taken.
	 */
	public int dataPutGet(PackData q,Vector<Vector<String>> flagsegs,
			boolean putget,boolean transl) {
		boolean face_colors=false;
		boolean face_marks=false;
		boolean cir_colors=false;
		boolean radii=false;
		boolean centers=false;
		boolean aims=false;
//		boolean ang_sums=false;  // OBE: needed 's' flag for 'schwarzian'
		boolean marks=false;
		boolean invD=false;
		boolean schwarzian=false;
		boolean v_map=false;
		boolean V_map=false;
		int count=0;

		if (transl && vertexMap==null) {
			flashError("usage: get/put_data: -t flag set but active packing p"+this.packNum+" " +
					"has no 'vertexMap'.");
			return 0;
		}
		
		// set flags based on indicated data to put/get
		Iterator<Vector<String>> its=flagsegs.iterator();
		Vector<String> items=null;
		String str=null;
		while (its.hasNext()) {
			items=(Vector<String>)its.next();
			str=(String)items.get(0);
			if (StringUtil.isFlag(str)) {
				if (str.equals("-f")) face_colors=true;
				else if (str.equals("-fm")) face_marks=true;
				else if (str.equals("o")) {
					if (face_colors || face_marks) {
						flashError("usage: get/put_data: can't specify both faces and edges");
						return count;
					}
					invD=true; 
				}
				else if (str.equals("-c")) { 
					if (face_colors || face_marks || invD) {
						flashError("usage: get/put_data: can't specify vertices and faces/edges");
						return count;
					}
					cir_colors=true;
				}
				else if (str.equals("-r")) radii=true;
				else if (str.equals("-z")) centers=true;
				else if (str.equals("-a")) aims=true;
				else if (str.equals("-s")) schwarzian=true;
				else if (str.equals("-m")) marks=true;
				
				// manipulating vertex maps:
				else if (str.equals("-v")) v_map=true;
				else if (str.equals("-V")) V_map=true;				
			}
			else throw new ParserException();
		} // end of while
		
		items=(Vector<String>)flagsegs.lastElement();
		str=(String)items.remove(0);
		if (items.size()==0) items.add("a"); // default to all
		
		// See 'get_data' in 'CmdDetails' help file to sort this out
		
		// which way are things going?
		PackData source_p=this;
		PackData target_p=q;
		if (!putget) {
			source_p=q;
			target_p=this;
		}
		
		// v_map and V_map cases: moving or composing vertexMap's
		if (v_map && !transl && putget) { // copy 'this.vertexMap' to q
			if (vertexMap==null || vertexMap.size()==0) return 0;
			q.vertexMap=vertexMap.makeCopy();
			return q.vertexMap.size();
		}
		if (v_map && !transl && !putget) { // copy from q.vertexMap to 'this'
			if (q.vertexMap==null || q.vertexMap.size()==0) return 0;
			vertexMap=q.vertexMap.makeCopy();
			return vertexMap.size();
		}
		if (v_map && transl && putget) { // compose: 'q.vertexMap' followed by 'vertexMap'
			VertexMap tvm=Translators.composeVMs(q.vertexMap,false,vertexMap,false);
			if (tvm==null || tvm.size()==0) return 0;
			q.vertexMap=tvm;
			return tvm.size();
		}
		if (v_map && transl && !putget) { // compose: 'vertexMap' followed by 'q.vertexMap'
			VertexMap tvm=Translators.composeVMs(vertexMap,false,q.vertexMap,false);
			if (tvm==null || tvm.size()==0) return 0;
			vertexMap=tvm;
			return tvm.size();
		}
		if (V_map && putget) { // compose
			VertexMap tvm=Translators.composeVMs(q.vertexMap,false,vertexMap,true);
			if (tvm==null || tvm.size()==0) return 0;
			q.vertexMap=tvm;
			return tvm.size();
		}
		if (V_map && !putget) { // compose
			VertexMap tvm=Translators.composeVMs(vertexMap,false,q.vertexMap,true);
			if (tvm==null || tvm.size()==0) return 0;
			vertexMap=tvm;
			return tvm.size();
		}
		
		// Remaining options
		
		// handle faces
		if (face_colors || face_marks) {
		    FaceLink facelist=new FaceLink(this,items);
			Iterator<Integer> flst=facelist.iterator();
			int f=1;
			int nf=0;
			while (flst.hasNext()) {
				f=(Integer)flst.next();
				try {
					if ((nf=Translators.face_translate(this,vertexMap,f,q,putget).get(0))>0) {
						if (face_colors) {
							Color col=source_p.getFaceColor(f);
							target_p.setFaceColor(nf,ColorUtil.cloneMe(col));
						}
						if (face_marks) target_p.setFaceMark(nf,source_p.getFaceMark(f));
						count++;
					}
				} catch (Exception ex) {}
			}
			return count;
		}
		else if (invD) {	
			EdgeLink edgelist=new EdgeLink(this,items);
			Iterator<EdgeSimple> elst=edgelist.iterator();
			EdgeSimple edge=null;
			EdgeSimple ne=null;
			// if looking for inv distances, are there any? 
			if (invD) {
				if (!source_p.overlapStatus) return 0;
				if (!target_p.overlapStatus) target_p.alloc_overlaps();
			}
			while (elst.hasNext()) {
				edge=(EdgeSimple)elst.next();
				int j=source_p.nghb(edge.v,edge.w);
				try {
					if (j>=0 && (ne=Translators.edge_translate(this,vertexMap,
							edge,q,putget).get(0))!=null) {
						if (invD) {
							double ovlp=source_p.getInvDist(edge.v,kData[edge.v].flower[j]);
							count+=target_p.set_single_invDist(ne.v,ne.w,ovlp);
						}
					}
				} catch (Exception ex) {}
			}
			return count;
		}
		else {
		    NodeLink vertlist=new NodeLink(this,items);
			Iterator<Integer> vlst=vertlist.iterator();
			int v=1,w=0;
			while (vlst.hasNext()) {
				v=w=(Integer)vlst.next();
				if (transl) {
					try {
						// TODO: do we want to use multiple translations?
						// Note: 'v' and 'vertexMap' are always from 'this'
						w=Translators.vert_translate(vertexMap,v,true).get(0);
					} catch (Exception ex) {
						w=0;
					}
				}
				
				// which vert is which 
				int src_v=v;
				int tgt_v=w;
				if (!putget) {
					src_v=w;
					tgt_v=v;
				}
				
				// move the data
				if (tgt_v>0 && tgt_v<=target_p.nodeCount) {
					if (radii) {target_p.setRadius(tgt_v,source_p.getRadius(src_v));count++;}
					if (aims) {target_p.setAim(tgt_v,source_p.getAim(src_v));count++;}
					if (marks) {target_p.setVertMark(tgt_v,source_p.getVertMark(src_v));count++;}
					if (centers) {target_p.setCenter(tgt_v,new Complex(source_p.getCenter(src_v)));count++;}
					if (cir_colors) {
						Color col=source_p.getCircleColor(src_v);
						target_p.setCircleColor(tgt_v,ColorUtil.cloneMe(col));
					}
					if (schwarzian) {
						try {
							target_p.kData[tgt_v].schwarzian=source_p.kData[src_v].schwarzian;
						} catch (Exception ex) {
							throw new DataException("get/put usage: 'schwarzian' data is missing for either source or target");
						}
					}
				}
				count++;
			}
			return count;
		}
	}
	
	/** 
	 * Given a closed chain of faces, this returns an EdgeLink 
	 * listing the 'outer' edges (those on the outer, i.e., right
	 * hand side of the face list). Useful for making edge list
	 * of poison vertices for face drawing order.
	 * Note: this is a less sophisticated version of 'red_to_outlist'
	 * because we don't have the tools we have for redlists.
	 * Simply check the faces before/after each member of the
	 * list (checking ends if it's closed also); include the edge
	 * that neither shares it it's on the right hand side
	*/
	public EdgeLink outer_edges(FaceLink facelist) {
	  int f1,f2,f3,j=-1;

	  EdgeLink edgelist=new EdgeLink(this);
	  if (facelist.size()<=2) return null;
	  f2=facelist.get(facelist.size()-1); // find last face
	  Iterator<Integer> flst=facelist.iterator();
	  f3=(Integer)flst.next();
	  while (flst.hasNext()) { // cycle to end
		  // here's the next three faces
		  f1=f2;
		  f2=f3;
		  f3=(Integer)flst.next();
		  if ((j=face_nghb(f1,f2))>=0) 
			  if (face_index(f3,faces[f2].vert[j])>=0) // share inside vert, hence leave outside edge
				  edgelist.add(new EdgeSimple(faces[f2].vert[(j+1)%3],faces[f2].vert[(j+2)%3]));
	  }	    		  		
	  return edgelist;
	} 

	/** 
	 * 'Poison' verts and edge are typically used to help define 
	 * subcomplexes, as in the cookie process or in layouts and
	 * dual graphs. Default to circles; '-e' flag for edges.
	 * Previous contents are lost; use 'P' flag to retain,
	 * as in 'set_poison P 7 8'. 
	 * @param items Vector<String>
	 * @return int, size of 'poisonVerts' or 'poisonEdges' array
	*/
	public int set_poison(Vector<String> items) {
		boolean circs=true;
		if (items!=null && items.size()>0 && StringUtil.isFlag(items.get(0))) {
			String fstr=items.remove(0);
			if (fstr.startsWith("-e"))
				circs=false;
		}
		if (circs) {
			poisonVerts=new NodeLink(this,items);
			if (poisonVerts==null) return 0;
			return poisonVerts.size();
		}
		else {
			poisonEdges=new EdgeLink(this,items);
			if (poisonEdges==null) return 0;
			return poisonEdges.size();
		}
	}

	/** 
	 * Mark as 'poison' (utilFlag=-1) the connected component of 
	 * the packing containing 'seed' but avoiding given list of 
	 * verts. Return vert on shared bdry of this patch and its
	 * complement, or 0 on error. 
	*/
	public int poison_patch(int seed,String datastr) {

	  int k,v;
	  VertList genlist=null;
	  VertList gtrace;

	  if (!status || locks!=0 || seed<0 || seed > nodeCount) return 0;
	  NodeLink vertlist=new NodeLink(this,datastr);
	  if (vertlist==null || vertlist.size()==0) return 0;
	  for (int i=1;i<=nodeCount;i++) kData[i].utilFlag=0;
	  Iterator<Integer> vlist=vertlist.iterator();
	  while (vlist.hasNext()) {
	      v=(Integer)vlist.next();
	      kData[v].utilFlag=1;
	    }
	  // seed should not be among list of verts 
	  if (kData[seed].utilFlag!=0) return 0;

	  genlist=gtrace=new VertList();
	  genlist.v=seed;
	  kData[seed].utilFlag=-1;
	  /* go through adding to end of list and knocking off from
	     front until all connected verts are marked as poison */
	  while (genlist!=null) {
	      for (int j=0;j<(countFaces((v=genlist.v))+getBdryFlag(v));j++)
		  if (kData[(k=kData[v].flower[j])].utilFlag==1) {
		    kData[k].utilFlag=-1;
		    gtrace=gtrace.next=new VertList();
		    gtrace.v=k;
		  }
	      genlist=genlist.next;
	  }   
	  /* now find an interior of p which is not poison, but is on 
	     edge of poison patch. */
	  for (int i=1;i<=nodeCount;i++)
	    if (!isBdry(i) && kData[i].utilFlag>=0)
	      for (int j=0;j<countFaces(i);j++)
		if (kData[kData[i].flower[j]].utilFlag==-1)
		  return i;
	  return 0; // didn't find appropriate bdry vert 
	} 

	/** 
	 * Reset aims of vlist based on their current angle sum, aim, 
	 * and specified factor x. If aim is positive,
	 * aim(v)=angle sum(v) + x*[aim(v)-angle sum(v)].
	 * Return count of adjustments.
	 */
	public int scale_aims(double x,NodeLink vlist) {
	  int count=0,v;
	  UtilPacket uP=new UtilPacket();

	  if (!status || vlist==null || x < 0.0) return 0;
	  Iterator<Integer> vtrace=vlist.iterator();

	  while (vtrace.hasNext()) {
	    v=(Integer)vtrace.next();
	    if (getAim(v)>0) {
	      if (hes<0)
		h_anglesum_overlap(v,getRadius(v),uP);
	      else if (hes>0)
		s_anglesum(v,getRadius(v),uP);
	      else 
		e_anglesum_overlap(v,getRadius(v),uP);
	      setCurv(v,uP.value);
	      setAim(v,getCurv(v)-x*(getCurv(v)-getAim(v)));
	      count++;
	    }
	  }
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
	 * Given linked list of edges, return list of faces on their left.
	 * @param edgelist EdgeLink 
	 * @param clse boolean; true means to close up
	 * @return FaceLink; On error return partial 
	 * list if consistent, or null.
	*/
	public FaceLink path_follow(EdgeLink edgelist,boolean clse) {
	  int cur_u,cur_v,next_u,cur_w,stopv;
	  int first_f,last_f=0,first_v,first_w;
	     
	  if (edgelist==null || edgelist.size()==0) 
		  throw new ParserException();
	  FaceLink facelist=new FaceLink(this);
	  int []ans=null;
	  EdgeSimple edge=edgelist.remove(0);
	  // get started with first face
	  first_v=cur_v=edge.v;
	  first_w=cur_w=edge.w;
	  ans=left_face(edge);
	  first_f=ans[0];
	  if (!facelist.add(first_f)) return null;
	  cur_u=ans[1];
	  Iterator<EdgeSimple> elist=edgelist.iterator();
	  while (elist.hasNext()) {
	      edge=(EdgeSimple)elist.next();
	      if (edge.v!=cur_w) // edgelist not connected
		  throw new ParserException();
	      if (edge.w!=cur_u) { // need to add clockwise faces about cur_w
		  stopv=edge.w; // stop on reaching stopv
		  next_u=cur_u;
		  while (next_u!=stopv) {
		      ans=left_face(next_u,cur_w);
		      last_f=ans[0];
		      if (!facelist.add(last_f)) 
			  throw new ParserException();
		      next_u=ans[1];
		  }
		  if (next_u!=stopv) return facelist; // ran into boundary 
	      }
	      cur_v=edge.v;
	      cur_w=edge.w;
	      ans=left_face(cur_v,cur_w); 
	      last_f=ans[0];
	      // done if we're back to first edge (and first face)
	      if (last_f==first_f && first_v==cur_v && first_w==cur_w) 
		  return facelist;
	      cur_u=ans[1];
	  }

	  // return if: have closed; don't want to close; or edgelist isn't closed
	  if (!clse || last_f==first_f || cur_w!=first_v) return facelist;

	  // faces to add around last vertex, cur_w; reuse first edge
	  stopv=first_w;
	  next_u=cur_u;
	  while (next_u!=stopv && last_f!=first_f) {
	      ans=left_face(next_u,cur_w);
	      last_f=ans[0];
	      if (!facelist.add(last_f)) 
	    	  throw new ParserException();
	      next_u=ans[1];
	  }
	  return facelist; // Note: may have closed or run into bdry
	}
	
	/** 
	 * Given a vertlist, string together as many as form a valid edgelist. 
	 * @param vertlist NodeLink
	 * @return EdgeLink, throws ParserException
	*/
	public EdgeLink node_to_edge(NodeLink vertlist) {
	    if (vertlist==null || vertlist.size()==0) 
		throw new ParserException();
	    EdgeLink edgelist=new EdgeLink(this);
	    int last_v=(Integer)vertlist.remove(0);
	    Iterator<Integer> vlist=vertlist.iterator();
	    int v=0;
	    while (vlist.hasNext()) {
		// shuck repeats
		while (vlist.hasNext() && (v=(Integer)vlist.next())==last_v);
		// break in list, then return
		if (!vlist.hasNext() || nghb(v,last_v)<0) return edgelist;
		edgelist.add(new EdgeSimple(last_v,v));
		last_v=v;
	    }
	    return edgelist;
	}	

	/** 
	 * For 'hex_walk', called by 'hex_parallel_call' and 'fix -h' to draw 
	 * parallelogram. Edge lengths n, start edge = {v w}, counterclockwise. 
	 * Return corner verts in 'corners'; v2,v4 "sharp" corners, last corner
	 * is corners[4] and may fail to be equal to v if the walk doesn't 
	 * close.
	 * @param V int
	 * @param W int
	 * @param n int
	 * @param corners int[]
	 * @return FaceLink, throws ParserException
	 */
	public FaceLink try_hex_pg(int V,int W,int n,int []corners)
	throws ParserException {
	    int next_w;
	    int v=V,w=W;
	    int num;

	    if (nghb(v,w)<0) throw new ParserException(); // must start with edge 
	    // record first two vertices 
	    corners[0]=v;
	    NodeLink vertlist=new NodeLink(this,v);
	    vertlist.add(w);
	     // first side 
	     for (int i=2;i<=n;i++) {
	          num=countFaces(w);
		  next_w=kData[w].flower[(nghb(w,v)+num-3) % num];
	          vertlist.add(next_w);
	          v=w;
	          w=next_w;
	      }
	     // sharp left turn 
	     corners[1]=w;
	     num=countFaces(w);
	     next_w=kData[w].flower[(nghb(w,v)+num-1) % num];
	     v=w;
	     w=next_w;
	     // second side
	     for (int i=2;i<=n;i++) {
	          num=countFaces(w);
		  next_w=kData[w].flower[(nghb(w,v)+num-3) % num];
	          vertlist.add(next_w);
	          v=w;
	          w=next_w;
	      }
	     // left turn 
	     corners[2]=w;
	     num=countFaces(w);
	     next_w=kData[w].flower[(nghb(w,v)+num-2) % num];
	     v=w;
	     w=next_w;
	     // third side
	     for (int i=2;i<=n;i++) {
	          num=countFaces(w);
		  next_w=kData[w].flower[(nghb(w,v)+num-3) % num];
	          vertlist.add(next_w);
	          v=w;
	          w=next_w;
	      }
	     corners[3]=w;
	     num=countFaces(w);
	     next_w=kData[w].flower[(nghb(w,v)+num-2) % num];
	     v=w;
	     w=next_w;
	     // fourth side
	     for (int i=2;i<=n;i++) {
	          num=countFaces(w);
		  next_w=kData[w].flower[(nghb(w,v)+num-3) % num];
	          vertlist.add(next_w);
	          v=w;
	          w=next_w;
	     }
	     EdgeLink edgelist=node_to_edge(vertlist);
	     return path_follow(edgelist,true);
	} 
	
	/** 
	 * Try to draw a combinatorial hexagon of side length n, starting at 
	 * corner v, in direction of neighbor w. Pretend combinatorics are hex: 
	 * do 6 n-step edges, and left turns. Return chain of circles. Return
	 * length on success, -length if an edge or other combinatoric problem 
	 * (eg, not enough edges from a vertex) is encountered. Success means 
	 * completing the walk, but that does not necessarily mean a return to v. 
	 * (I think the "Berger's" vector of physics is the displacement from the 
	 * center of v to the center of the end vertex.)
	 * @param v,w neighboring vertices,
	 * @param integer n>=1, sidelength
	 * @param vertChain NodeLink, created by calling routine.
	 * @return length if ran to completion, -length if not, 0 on error
	*/
	public int hexCell(int v,int w,int n,NodeLink vertChain) {
		int edgecount=0;
		int step,k,num;
		if (nghb(v,w)<0) return 0;
		int v1=v;
		int v2=w;
		vertChain.add(v1);
		vertChain.add(v2);
		int count=2;
		
		while (edgecount<6) {
			// move along edge
			step=1;
			while (step<n) {
				k=nghb(v2,v1);
				num=countFaces(v2);
				if (isBdry(v2)) {
					if (k<3) return count;
					k=k-3;
				}
				else {
					if (num<=3) return count;
					k=(k+num-3)%num;
				}
				v1=v2;
				v2=kData[v2].flower[k];
				vertChain.add(v2);
				count++;
				step++;
			} // end of while
			
			// move around corner
			k=nghb(v2,v1);
			num=countFaces(v2);
			if (isBdry(v2)) {
				if (k<2) return count;
				k=k-2;
			}
			else {
				if (num<=2) return count;
				k=(k+num-2)%num;
			}
			v1=v2;
			v2=kData[v2].flower[k];
			if (edgecount<5) vertChain.add(v2); // suppress last one
			count++;

			edgecount++;
		} // end of while
		
		return count;
	}
	
	/** Alternative face drawing orders for simply connected complexes.
	The aim is to avoid some layout problems by, e.g., avoiding use of
	3/4-degree vertices and/or vertices having extremely small radii.
	 
	Marked vertices are the ones to be avoided: that is, lay out
	all others first. If flag=0, then when you run out of other faces,
	you place one using the marked vertices, then again try to lay
	out others until you run into another wall, etc. If flag=1, then
	never use the marked vertices --- just stop when all possible are
	laid out without it.

	We will keep a linked list of vertices whose faces we should 
	search through for new vertices to place at a given stage, adding 
	to the end of the list as we progress; when that's used up, we try 
	(if keepon=true) some that were skipped, creating a new list to search 
	through. Cycle through this process until all circles are 
	eventually placed or (eg. if keepon=false) you give up.

	We will return a whole new "faces" data array and 'first_face',
	which is stored in 'packData.util_A' for transfer.
	It is the responsibility of the calling routine to use the new
	stuff, throw out the redfaces, empty the rwb_flags, allocate
	edge_pair, set first_face from util_A, manage the new Face[], 
	etc. This routine empties and uses vertex 'utilFlag'. 
	*/
	public Face []tailor_face_order(boolean keepon) {

	  int num,vert,face,v1,v2,lastface,indx,v,w,way;
	  int vertcount=0,nnum;
	  VertList priortrace,trace,endmain,endnew;
	  VertList newlist=null,mainlist=null;

	  boolean pickup=false;
	  boolean hits=true;
	  boolean fstop=true;

	  if (isSimplyConnected()) {
	      flashError("Tailored layouts (layout -t) currently limited "+
			 "to simply connected complexes.");
	      throw new ParserException();
	  }
	  // create "newfaces" space, copy most stuff over.
	  Face []newfaces=new Face[faceCount+1];
	  for (int j=1;j<=faceCount;j++) {
		  newfaces[j]=new Face(this);
	      newfaces[j]=faces[j].clone();
	      newfaces[j].plotFlag=newfaces[j].nextFace=newfaces[j].indexFlag=0;
	    }
	    
	  // initialize 
	  mainlist=trace=priortrace=endmain=new VertList();
	  newlist=endnew=new VertList();
	  for (int i=1;i<=nodeCount;i++) kData[i].utilFlag=kData[i].plotFlag=0;

	  /* start with alpha (regardless of its situation) and contiguous
	     neighbors (unmarked, if possible). */
	  v=kData[alpha].flower[0];
	  w=kData[alpha].flower[1];
	  int nbr=0;
	  if (getVertMark(v)!=0 || getVertMark(w)!=0) {
	      for (int i=0;((i<countFaces(alpha)) && nbr==0);i++)
		if (getVertMark((v=kData[alpha].flower[i]))==0
		    && getVertMark((w=kData[alpha].flower[i+1]))==0)
		  nbr=i;
	    }
	  kData[alpha].plotFlag=kData[v].plotFlag=kData[w].plotFlag=1;
	  for (int i=0;i<=countFaces(alpha);i++)
	    kData[kData[alpha].flower[i]].utilFlag++;
	  for (int i=0;i<=countFaces(v);i++)
	    kData[kData[v].flower[i]].utilFlag++;
	  for (int i=0;i<=countFaces(w);i++)
	    kData[kData[w].flower[i]].utilFlag++;
	  vertcount=3;
	  endmain.v=alpha;
	  endmain=endmain.next=new VertList();
	  endmain.v=v;
	  endmain=endmain.next=new VertList();
	  endmain.v=w;
	  util_A=lastface=getFaceFlower(alpha,nbr);// **** *(face_org[alpha]+nbr+1);
	  newfaces[lastface].indexFlag=face_index(lastface,alpha);
	  newfaces[lastface].plotFlag=1;
	  for (int i=1;i<=faceCount;i++) newfaces[i].nextFace=lastface;


	  /* We keep two lists of vertices: 'mainlist' is the one we're
	     going through. As long as mainlist isn't empty and we're
	     getting hits of new vertices, build 'newlist' of newly added
	     vertices. When through mainlist, we adjoin newlist to end,
	     pass through mainlist again. If we get through mainlist and
	     newlist is null, we pass through mainlist to add first 'marked'
	     vertex, add it to end of mainlist, then go through mainlist
	     again. Keeping track in utilFlag of number of nghbs placed
	     (depends on with int/bdry), plotFlag indicates faces placed
	     (though others may have all vertices placed). Remove verts 
	     from mainlist if all nghbs placed. */

	  hits=true;
	  while (mainlist!=null && (pickup || hits)) {
	      hits=false;
	      priortrace=trace=mainlist;

	      /* Go through mainlist of verts. Add new verts to newlist as
		 encountered, remove from mainlist those we're finished
		 with. */
	      while (trace!=null) {
		  vert=trace.v;
		  // already hit all neighbors of this vert? 
		  if (kData[vert].utilFlag
		      ==(countFaces(vert)+getBdryFlag(vert))) {
		      if (trace==mainlist) { // at beginning of list 
			  priortrace=trace=mainlist.next;
			  mainlist=trace;
			}
		      else if (trace==endmain) { // at end of list 
			  endmain=priortrace;
			  endmain.next=trace=null;
			}
		      else {
			  priortrace.next=trace.next;
			  trace=priortrace.next;
			}
		    }
		  else if (getVertMark(vert)==0 || pickup) { // process this vert 
		      num=countFaces(vert); // num of faces
		      nnum=num+getBdryFlag(vert); // num of nghbs 
		      // go through the faces 
		      fstop=true;
		      while (fstop && kData[vert].utilFlag!=nnum) {
			  fstop=false;
			  for (int f=0;(f<num && kData[vert].utilFlag!=nnum);f++) 
			      // **** replaced a face_org here. okay??
			      if (newfaces[(face=getFaceFlower(vert,f))].plotFlag<=0) {
			      indx=face_index(face,vert);
			      v1=newfaces[face].vert[(indx+1)%3];
			      v2=newfaces[face].vert[(indx+2)%3];
			      // can we place either v1 or v2? 
			      if (kData[v2].plotFlag>0 && kData[v1].plotFlag>0)
				newfaces[face].plotFlag=1;
			      else if (kData[v2].plotFlag<=0 
				       && kData[v1].plotFlag>0
				       && (pickup || getVertMark(v1)==0)) {
				  newfaces[lastface].nextFace=face;
				  lastface=face;
				  newfaces[face].indexFlag=indx;
				  newfaces[face].plotFlag=1;
				  kData[v2].plotFlag=1;
				  way=countFaces(v2)+getBdryFlag(v2);
				  for (int i=0;i<way;i++)
				    kData[kData[v2].flower[i]].utilFlag++;
				  hits=true;
				  fstop=true;
				  vertcount++;
				  if (kData[v2].utilFlag<
				      (countFaces(v2)+getBdryFlag(v2))) {
				      endnew=endnew.next=new VertList();
				      endnew.v=v2;
				  }
			      }
			      else if (kData[v1].plotFlag<=0 
				       && kData[v2].plotFlag>0
				       && (pickup || getVertMark(v2)==0)) {
				  newfaces[lastface].nextFace=face;
				  lastface=face;
				  newfaces[face].indexFlag=(indx+2)%3;
				  newfaces[face].plotFlag=1;
				  kData[v1].plotFlag=1;
				  way=countFaces(v1)+getBdryFlag(v1);
				  for (int i=0;i<way;i++)
				    kData[kData[v1].flower[i]].utilFlag++;
				  hits=true;
				  fstop=true;
				  vertcount++;
				  if (kData[v1].utilFlag<
				      (countFaces(v1)+getBdryFlag(v1))) {
				      endnew=endnew.next=new VertList();
				      endnew.v=v1;
				    }
				}
			      if (pickup && hits) { // kick out of for and while's 
				  f=num+1;
				  fstop=false;
				  trace=null;
				}
			      } // end of for f 
		      } // end of face while 
		      if (trace!=null) {
			  priortrace=trace;
			  trace=trace.next;
		      }
		  } // end of else processing 
		  else {
		      priortrace=trace;
		      trace=trace.next;
		    }
		} // end of trace while 
	      if (hits) {
		  if (newlist.next!=null) { // add newlist to end of mainlist 
		      endmain.next=newlist.next;
		      endmain=endnew;
		      newlist.next=null;
		      endnew=newlist;
		    }
		  pickup=false;
		}
	      else if (keepon && !pickup) pickup=true; /* pickup true means to
						      use a marked vert;
						      we resort to this
						      only when keepon is true. */
	      else pickup=false; // no hits among marked/unmarked; quit. 
	    } // end of mainlist while 
	    
	  if (vertcount!=nodeCount)
	    CirclePack.cpb.msg("tailor_face_order: only placed "+
						   vertcount+" of the "+
						   nodeCount+" vertices");
	  else 
	    CirclePack.cpb.msg("tailor_face_order success");
	  if (vertcount!=0) return newfaces;
	  return null;
	}
		      
	/** 
	 * Recompute centers of circles using face drawing order as in 
	 * 'draw_in_order'. Report all locations of vertex v. Option to
	 * recompute first face or leave it in current location. Option
	 * to apply usual alpha/gamma normalization. 
	 * Note: this command modifies the recorded centers.
	 * @param v int, index of vertex whose locations are reported (0 for none)
	 * @param place_first boolean, if true place the first face
	 * @param norm boolean, true then do usual alpha/gamma normalization
	 * @return int, 0 on error 
	*/
	public int layout_report(int v, boolean place_first, boolean norm) {
		int start = 0;
		int move = 0;
		int vflag = 1;

		CentList centlist = null;
		CentList ctmp;
		CentList ctrace = null;
		if (v > 0 && v <= nodeCount)
			vflag = 0;
		int nf = firstFace;
		if (place_first)
			place_face(nf, faces[nf].indexFlag);
		for (int j = 0; j < 3; j++)
			// initial location of v
			if (faces[nf].vert[j] == v) { // record first center
				start = j + 1;
				ctmp = new CentList();
				ctmp.z = new Complex(getCenter(v));
				if (centlist == null)
					centlist = ctrace = ctmp;
				else
					ctrace = ctrace.next = ctmp;
			}
		int ck = 0;
		while ((nf = faces[nf].nextFace) != firstFace && ck < 2 * faceCount
				&& nf > 0 && nf <= faceCount) {
			int indx=(faces[nf].indexFlag + 2) % 3;
			int vert = faces[nf].vert[indx];
			int n0 = nghb(vert, faces[nf].vert[(indx + 1) % 3]);
			fancy_comp_center(vert, n0, n0, 1, 0, true, true, TOLER);
			if (vflag == 0 && vert == v) { // record center
				move++;
				ctmp = new CentList();
				ctmp.z = getCenter(v);
				if (centlist == null)
					centlist = ctrace = ctmp;
				else
					ctrace = ctrace.next = ctmp;
			}
			ck++;
		}
		if (nf != firstFace) { // some error
			flashError("error in 'simple_layout'");
			throw new ParserException();
		}
		if (vflag == 0 && start != 0 && move != 0) {
			// if v in first face and has moved, also include final location
			ctmp = new CentList();
			ctmp.z = getCenter(v);
			if (centlist == null)
				centlist = ctrace = ctmp;
			else
				ctrace = ctrace.next = ctmp;
		}

		// normalize?
		if (norm) {
			Complex a = getCenter(alpha);
			Complex g = getCenter(gamma);
			norm_any_pack(a, g);

			// adjust locations reported for v because of normalization
			if (centlist != null) {
				ctrace = centlist;
				while (ctrace != null) {
					Complex newz=new Complex(ctrace.z);
					Complex z = new Complex(ctrace.z);
					if (hes < 0) // hyp
						newz = Mobius.mobDiscValue(z, a, g);
					else if (hes == 0) { // eucl
						Complex w = g.minus(a);
						Complex I = new Complex(0.0, w.abs());
						Complex modw = I.divide(w);
						Complex y = z.minus(a);
						newz = modw.times(y);
					}
					CirclePack.cpb.msg("  " + newz.x + " " + newz.y + "i ;");
					ctmp = ctrace;
					ctrace = ctrace.next;
				}
			}
		} // done with normalization
		
		// report locations
		if (centlist!=null) {
			CirclePack.cpb.msg("Locations of vertex " + v);
			ctrace = centlist;
			while (ctrace != null) {
				CirclePack.cpb.msg("  " + ctrace.z.x + " " + ctrace.z.y + "i ;");
				ctrace=ctrace.next;
			}
		}

		return (vflag + start + move); // error if this is zero
	}

	/**
	 * Make up list by looking through SetBuilder specs (from {..} set-builder notation).
	 * Use 'utilFlag' to collect information before creating the NodeLink for return.
	 * @param specs Vector<SelectSpec>
	 * @return NodeLink list of specified circles.
	 */
	public NodeLink circleSpecs(Vector<SelectSpec> specs) {
		if (specs==null || specs.size()==0) return null;
		SelectSpec sp=null;
		int count=0;

		// will store results in 'utilFlag'
		for (int v=1;v<=nodeCount;v++) {
			kData[v].utilFlag=0;
		}
		// loop through all the specifications: these should alternate
		//   between 'specifications' and 'connectives', starting with the former,
		//   although typically there will be just one specification in the vector
		//   and no connective.
		UtilPacket uPx=null;
		UtilPacket uPy=null;
		boolean isAnd=false; // true for '&&' connective, false for '||'.
		for (int j=0;j<specs.size();j++) {
			sp=(SelectSpec)specs.get(j);
			if ((sp.object & 01)!=01) throw new ParserException(); // specification must be for circles
			try {
				for (int v=1;v<=nodeCount;v++) {
					
					// success?
					boolean outcome=false;
					uPx=sp.node_to_value(this,v,0);
					if (sp.unary) {
						if (uPx.rtnFlag!=0)
							outcome=sp.comparison(uPx.value,0);
					}
					else {
						uPy=sp.node_to_value(this,v,1);
						if (uPy.rtnFlag!=0)
							outcome=sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && kData[v].utilFlag==0) { // 'or' situation
							kData[v].utilFlag=1;
							count++;
						}
					}
					else { // no, fails this condition
						if (isAnd && kData[v].utilFlag!=0) { // 'and' situation
							kData[v].utilFlag=0;
							count--;
						}
					}
				}
			} catch (Exception ex) {
				throw new ParserException();
			}
			
			// if specs has 2 or more additional specifications, the next must
			//    be a connective. Else, finish loop.
			if ((j+2)<specs.size()) {
				sp=(SelectSpec)specs.get(j+1);
				if (!sp.isConnective) throw new ParserException();
				isAnd=sp.isAnd; 
				j++;
			}
			else j=specs.size(); // kick out of loop
		}
		
		if (count>0) {
			NodeLink nl=new NodeLink(this);
			for (int v=1;v<=nodeCount;v++)
				if (kData[v].utilFlag!=0) nl.add(v);
			return nl;
		}
		else return null;
	}
	
	/**
	 * Make up list by looking through SetBuilder specs (from {..} set-builder notation).
	 * Use 'utilFlag' to collect information before creating the NodeLink for return.
	 * @param specs Vector<SelectSpec>
	 * @return NodeLink list of specified faces.
	 */
	public FaceLink facesSpecs(Vector<SelectSpec> specs) {
		if (specs==null || specs.size()==0) return null;
		SelectSpec sp=null;
		int count=0;

		// will store results in 'tmpUtil'
		int []tmpUtil=new int[faceCount+1];
		for (int f=1;f<=faceCount;f++) {
			tmpUtil[f]=0;
		}
		// loop through all the specifications: these should alternate
		//   between 'specifications' and 'connectives', starting with the former,
		//   although typically there will be just one specification in the vector.
		UtilPacket uPx=null;
		UtilPacket uPy=null;
		boolean isAnd=false; // true for '&&' connective, false for '||'.
		for (int j=0;j<specs.size();j++) {
			sp=(SelectSpec)specs.get(j);
			if ((sp.object & 02)!=02) throw new ParserException(); // specification must be for faces
			try {
				for (int f=1;f<=faceCount;f++) {

					// success?
					boolean outcome=false;
					uPx=sp.node_to_value(this,f,0);
					if (sp.unary) {
						if (uPx.rtnFlag!=0)
							outcome=sp.comparison(uPx.value,0);
					}
					else {
						uPy=sp.node_to_value(this,f,1);
						if (uPy.rtnFlag!=0)
							outcome=sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && tmpUtil[f]==0) { // 'or' situation
							tmpUtil[f]=1;
							count++;
						}
					}
					else { // no, fails this condition
						if (isAnd && tmpUtil[f]!=0) { // 'and' situation
							tmpUtil[f]=0;
							count--;
						}
					}			
				}
			} catch (Exception ex) {
				throw new ParserException();
			}
			
			// if specs has 2 or more additional specifications, the next must
			//    be a connective. Else, finish loop.
			if ((j+2)<specs.size()) {
				sp=(SelectSpec)specs.get(j+1);
				if (!sp.isConnective) throw new ParserException();
				isAnd=sp.isAnd; 
			}
			else j=specs.size(); // kick out of loop
		}
		
		if (count>0) {
			FaceLink nl=new FaceLink(this);
			for (int f=1;f<=faceCount;f++)
				if (tmpUtil[f]!=0) nl.add(f);
			return nl;
		}
		else return null;
	}		
	
	/**
	 * Make up list by looking through SetBuilder specs (from {..} set-builder notation).
	 * Use 'utilFlag' to collect information before creating the TileLink for return.
	 * @param specs Vector<SelectSpec>
	 * @return NodeLink list of specified tiles.
	 */
	public TileLink tileSpecs(Vector<SelectSpec> specs) {
		if (specs==null || specs.size()==0 || tileData==null) return null;
		SelectSpec sp=null;
		int count=0;

		// will store results in 'utilFlag'
		for (int t=1;t<=tileData.tileCount;t++) {
			tileData.myTiles[t].utilFlag=0;
		}
		// loop through all the specifications: these should alternate
		//   between 'specifications' and 'connectives', starting with the former,
		//   although typically there will be just one specification in the vector
		//   and no connective.
		UtilPacket uPx=null;
		UtilPacket uPy=null;
		boolean isAnd=false; // true for '&&' connective, false for '||'.
		for (int j=0;j<specs.size();j++) {
			sp=(SelectSpec)specs.get(j);
			if ((sp.object & 04)!=04) throw new ParserException(); // spec must be for tiles
			try {
				for (int t=1;t<=tileData.tileCount;t++) {
					
					// success?
					boolean outcome=false;
					uPx=sp.node_to_value(this,t,0);
					if (sp.unary) {
						if (uPx.rtnFlag!=0)
							outcome=sp.comparison(uPx.value,0);
					}
					else {
						uPy=sp.node_to_value(this,t,1);
						if (uPy.rtnFlag!=0)
							outcome=sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && tileData.myTiles[t].utilFlag==0) { // 'or' situation
							tileData.myTiles[t].utilFlag=1;
							count++;
						}
					}
					else { // no, fails this condition
						if (isAnd && tileData.myTiles[t].utilFlag!=0) { // 'and' situation
							tileData.myTiles[t].utilFlag=0;
							count--;
						}
					}
				}
			} catch (Exception ex) {
				throw new ParserException();
			}
			
			// if specs has 2 or more additional specifications, the next must
			//    be a connective. Else, finish loop.
			if ((j+2)<specs.size()) {
				sp=(SelectSpec)specs.get(j+1);
				if (!sp.isConnective) throw new ParserException();
				isAnd=sp.isAnd; 
				j++;
			}
			else j=specs.size(); // kick out of loop
		}
		
		if (count>0) {
			TileLink nl=new TileLink(tileData);
			for (int t=1;t<=tileData.tileCount;t++)
				if (tileData.myTiles[t].utilFlag!=0) nl.add(t);
			return nl;
		}
		else return null;
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
	    		nface=Translators.face_translate(this,null,face,q,true).get(0);
	    	else nface=Translators.face_translate(this,this.vertexMap,face,q,true).get(0);
	    } catch (Exception ex) {
	    	return 0;
	    }
		if (nface!=0) {
			// draw for this packing
			Complex c0=getCenter(faces[face].vert[0]);
			Complex c1=getCenter(faces[face].vert[1]);
			Complex c2=getCenter(faces[face].vert[2]);
			DispFlags dflags=new DispFlags("f");
			dflags.setColor(getFaceColor(face));
			cpScreen.drawFace(c0,c1,c2,null,null,null,dflags);   
			// draw other packing
		    c0=q.getCenter(q.faces[nface].vert[0]);
		    c1=q.getCenter(q.faces[nface].vert[1]);
		    c2=q.getCenter(q.faces[nface].vert[2]);
			dflags.setColor(q.getFaceColor(nface));
		    q.cpScreen.drawFace(c0,c1,c2,null,null,null,dflags);
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
	    DispFlags dispFlags=new DispFlags("f",cpScreen.fillOpacity);
		if (nL!=null && nL.size()>0) {
			int count=0;
			Iterator<Integer> nli=nL.iterator();
			while (nli.hasNext()) {
				int nv=(Integer)nli.next();
				if (nv>0 && nv<=q.nodeCount) {
					if (!donep) { // draw once for this packing
						dispFlags.setColor(getCircleColor(v));
						cpScreen.drawCircle(getCenter(v),getRadius(v),dispFlags);
					}
					donep=true;
				}
				if (nv<=q.nodeCount) { // draw all the translates 
					dispFlags.setColor(q.getCircleColor(nv));
					q.cpScreen.drawCircle(q.getCenter(nv),q.getRadius(nv),dispFlags);
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
	*/
	public int sa_draw_bdry_seg_num(int n) {
		RedEdge trace=null;
	    SideDescription epair=null;
	    if (sidePairs==null || n<0 || n>=sidePairs.size() 
	    		|| (epair=(SideDescription)sidePairs.get(n))==null
	    		|| (trace=epair.startEdge)==null) 
	    	return 0;
		Complex ctr=new Complex(RedList.whos_your_daddy(trace,trace.startIndex).center);
	    cpScreen.drawIndex(ctr,n,1);
	    return 1;
	}
	
	/**
	 * Currently, put segment number at first circle.
	 * TODO: need better placement; e.g. this will conflict with circle index
	*/
	public int post_bdry_seg_num(PostFactory pF,int n) {
		RedEdge trace=null;
	    SideDescription epair=null;
	    if (sidePairs==null || n<0 || n>=sidePairs.size() 
	    		|| (epair=(SideDescription)sidePairs.get(n))==null
	    		|| (trace=epair.startEdge)==null) 
	    	return 0;
		Complex ctr=new Complex(RedList.whos_your_daddy(trace,trace.startIndex).center);
		if (hes>0) {
			ctr=cpScreen.sphView.toApparentSph(ctr);
			if (Math.cos(ctr.x)>=0) pF.postIndex(ctr,n);
		}
		else pF.postIndex(ctr,n);
	    return 1;
	}
	
	/** 
	 * Draw an edge-pairing boundary segment based on starting face and 
	 * index of beginning vert. 
	 * @param n int, index of side-pair (indices start at 0)
	 * @param do_label boolean, label also?
	 * @param do_circle boolean, circles also?
	 * @param ecol Color
	 * @param int thickness to draw
	 * @return int
	 */
	public int sa_draw_bdry_seg(int n,boolean do_label,boolean do_circle,
			Color ecol,int thickness) {
	  RedEdge trace=null;
	  SideDescription epair=null;
	  
	  if (sidePairs==null || n<0 || n>=sidePairs.size() 
			  || (epair=(SideDescription)sidePairs.get(n))==null
			  || (trace=epair.startEdge)==null)  // epair.startEdge.hashCode();epair.startEdge.nextRed.hashCode();
		  return 0;
	  int old_thickness=cpScreen.getLineThickness();
//	  LayoutBugs.log_Red_Hash(this,this.redChain,null);LayoutBugs.log_RedCenters(this);
//      int v_indx=trace.vert(trace.startIndex);    // deBugging.LayoutBugs.log_RedCenters(this);
	  RedList wyd_w=RedList.whos_your_daddy(trace,trace.startIndex); // wyd_w.center; wyd_w.hashCode();
	  Complex w_cent=new Complex(wyd_w.center);
      DispFlags dflags=new DispFlags(""); // System.out.println("v_indx="+v_indx+", wyd_w center.x "+wyd_w.center.x+" and hash "+wyd_w.hashCode());
	  if (do_circle) { // handle draw/label for first circle
	      int w_indx=trace.vert(trace.startIndex);
	      if (do_label) { 
	    	  dflags.label=true;
	    	  dflags.setLabel(Integer.toString(w_indx));
	      }
	      cpScreen.drawCircle(w_cent,getRadius(w_indx),dflags);
	  }
	  RedEdge goon=trace;
      cpScreen.setLineThickness(thickness);
	  do {
	      Complex v_cent=w_cent;
	      int w_indx=goon.vert((goon.startIndex+1)%3);    // deBugging.LayoutBugs.log_RedCenters(this);
		  RedList wyd_v=RedList.whos_your_daddy(goon,(goon.startIndex+1)%3); // wyd_v.center; wyd_v.hashCode();
	      w_cent=new Complex(wyd_v.center);
	      DispFlags df=new DispFlags(null); // System.out.println("w_indx="+w_indx+", wyd_v center.x "+wyd_v.center.x+" and hash "+wyd_v.hashCode());
	      df.setColor(ecol);
	      cpScreen.drawEdge(v_cent,w_cent,df);
	      if (do_circle) { 
	          if (do_label) { 
	        	  dflags.label=true;
	        	  dflags.setLabel(Integer.toString(w_indx));
	          }
		      cpScreen.setLineThickness(old_thickness);
	    	  cpScreen.drawCircle(w_cent,getRadius(w_indx),dflags);
	          cpScreen.setLineThickness(thickness);
	      }
	      goon=goon.nextRed;
	    } while (goon!=epair.endEdge.nextRed);
      cpScreen.setLineThickness(old_thickness);
	  if (do_label) sa_draw_bdry_seg_num(n);
	  return 1;
	}
	
	/** 
	 * Post an edge-pairing boundary segment based on starting face and 
	 * index of beginning vert. 
	 * @param n int, index of side-pair (starting at 0)
	 * @param do_label boolean, label also?
	 * @param do_circle boolean, circles also?
	 * @param ecol Color
	 * @param tx, double thickness factor if > 0
	 * @return int
	 */
	public int post_bdry_seg(PostFactory pF,int n,boolean do_label,boolean do_circle,
			Color ecol,double tx) {
	  int w_indx=0;
	  Complex v_cent,w_cent;
	  RedEdge trace=null;
	  SideDescription epair=null;
	  
	  if (sidePairs==null || n<0 || n>=sidePairs.size() 
			  || (epair=(SideDescription)sidePairs.get(n))==null
			  || (trace=epair.startEdge)==null) 
		  return 0;
	  w_cent=new Complex(RedList.whos_your_daddy(trace,trace.startIndex).center);
	  if (do_circle) { // handle draw/label for first circle
	      w_indx=trace.vert(trace.startIndex);
	      if (hes>0) w_cent=cpScreen.sphView.toApparentSph(w_cent);
	      pF.postCircle(hes,w_cent,getRadius(w_indx));
	      if (do_label) {
	    	  if (hes>0 && Math.cos(w_cent.x)>=0) 
	    		  pF.postIndex(w_cent,w_indx);
	    	  else pF.postIndex(w_cent,w_indx);
	      }
	  }
	  RedEdge goon=trace;
	  do {
	      v_cent=w_cent;
	      w_cent=new Complex(RedList.whos_your_daddy(goon,(goon.startIndex+1)%3).center);
	      w_indx=goon.vert((goon.startIndex+1)%3);
	      if (hes>0) {
	    	  v_cent=cpScreen.sphView.toApparentSph(v_cent);
	    	  w_cent=cpScreen.sphView.toApparentSph(w_cent);
	      }
	      pF.postColorEdge(hes,v_cent,w_cent,ecol,tx);
	      if (do_circle) { 
	    	  pF.postCircle(hes,w_cent,getRadius(w_indx));
	    	  if (do_label) pF.postIndex(w_cent,w_indx);
	      }
	      goon=goon.nextRed;
	    } while (goon!=epair.endEdge.nextRed);
	  if (do_label) post_bdry_seg_num(pF,n);
	  return 1;
	}

	/**
	 * Double packing across some or all bdry components (in latter case, get
	 * compact complex). Error should leave pack unharmed, but there is not a
	 * lot of checking. Note: this only does full boundary components.
	 * 'vertexMap' gets vertex conversion: {old,new} where 'old' is the index
	 * from the original packing and 'new' is the index of the double vertex.
	 * @param, NodeLink vert list, at least one on each bdry component
	 *     to be doubled 
	 * @return: the index of the vertex symmetic to 'alpha'
	 */
	public int double_K(NodeLink vertlist) {
		int final_node_count, num, newval;
		int[] newflower = null;
		double[] newol = null;
		int[] oldnewflag = null; // non-zero indicates index data not needed 
		int[] old2new = null; // holds temporary new index number during construction
		KData[] newK_ptr = null;
		RData[] newR_ptr = null;

		// check suitability
		if (getBdryCompCount()==0) { // already compact
			throw new CombException("this complex has no boundary vertices");
		}
		if (vertlist == null)
			vertlist = new NodeLink(this, "B"); // all bdry components
		int node = nodeCount;
		for (int i = 1; i <= node; i++)
			kData[i].utilFlag = 0;
		/*
		 * mark all bdry verts to be identified. Illegal to have v hanging or to
		 * have v and one of its 'middle' petals (ie. not first or last) marked;
		 * throw exception.
		 */
		Iterator<Integer> vlist = vertlist.iterator();
		while (vlist.hasNext()) {
			int v = (Integer) vlist.next();
			if (isBdry(v) && kData[v].utilFlag == 0) {
				int vert = v;
				for (int j = 1; j < countFaces(v); j++)
					if (countFaces(v) < 2
							|| kData[kData[v].flower[j]].utilFlag != 0) {
						throw new CombException("combinatoric problem at v="+v);
					}
				kData[v].utilFlag = 1;
				v = kData[vert].flower[0];
				while (v != vert) {
					for (int j = 1; j < countFaces(v); j++)
						if (countFaces(v) < 2
								|| kData[kData[v].flower[j]].utilFlag != 0) {
							throw new CombException("combinatoric problem at v="+v);
						}
					kData[v].utilFlag = 1;
					v = kData[v].flower[0];
				}
			}
		}

		// create new data areas
		alloc_pack_space(2 * nodeCount, true);
		newK_ptr = new KData[sizeLimit + 1];
		newR_ptr = new RData[sizeLimit + 1];
		for (int i = 1; i <= node; i++) {
			newK_ptr[i] = kData[i].clone();
			newK_ptr[i + node] = kData[i].clone();
			newR_ptr[i] = rData[i].clone();
			newR_ptr[i + node] = rData[i].clone();
			num = newK_ptr[i].num;
			newflower = new int[num + 1];
			for (int j = 0; j <= num; j++)
				// change orientation
				newflower[j] = kData[i].flower[num - j] + node;
			newK_ptr[i + node].flower = newflower;
			if (overlapStatus) {
				newol = new double[num + 1];
				for (int j = 0; j <= num; j++)
					// change orientation
					newol[j] = getInvDist(i,kData[i].flower[num - j]);
				newK_ptr[i + node].invDist = newol;
			}
		}

		oldnewflag = new int[node + 1];
		old2new = new int[node + 1];
		for (int i = 1; i <= node; i++) { 
			// set to one when data for the double has moved to new location
			oldnewflag[i] = 0;
			// track new indices of doubles
			old2new[i] = i+node;
		}
		
		// We want to return index for vert symmetric to alpha, so track it
		int alp_sym=node+alpha;

		// begin processing boundary components
		vlist = vertlist.iterator();
		while (vlist.hasNext()) {
			int startv = (Integer) vlist.next();
			int v = startv;
			if (newK_ptr[v].bdryFlag != 0) {
				
				// take care of this whole bdry component
				do { 
					// 'alpha' on original bdry? identify as 'alp_sym'  
					if ((v+node)==alp_sym)  
						alp_sym=v;
					old2new[v] = v;
					oldnewflag[v] = 1;
					if (hes < 0 && getRadius(v) <= 0)
						setRadius(v,0.5);
					// fix flower
					num = countFaces(v);
					newK_ptr[v].num = 2 * num;
					newK_ptr[v].bdryFlag = 0;
					newR_ptr[v].aim = 2.0 * Math.PI;
					if (num < 2) {
						throw new CombException("the flower of bdry vert " + v
								+ " is too small.");
					}
					newflower = new int[2 * num + 1];
					for (int j = 0; j <= num; j++)
						newflower[j] = newK_ptr[v].flower[j];
					for (int j = 1; j < num; j++)
						newflower[j + num] = newK_ptr[v + node].flower[j];
					newK_ptr[v].flower = newflower;
					newK_ptr[v].flower[2 * num] = newK_ptr[v].flower[0];
					if (overlapStatus) {
						newol = new double[2 * num + 1];
						for (int j = 0; j <= num; j++)
							newol[j] = newK_ptr[v].invDist[j];
						for (int j = 1; j < num; j++)
							newol[j + num] = newK_ptr[v + node].invDist[j];
						newK_ptr[v].invDist = newol;
						newK_ptr[v].invDist[2 * num] = newK_ptr[v].invDist[0];
					}
					/* remove refs to old nums; flowers get only (temporarily) good numbers */
					for (int j = 0; j <= countFaces(v); j++) {
						int jj = newK_ptr[v + node].flower[j];
						for (int k = 0; k <= newK_ptr[jj].num; k++) {
							int vert=newK_ptr[jj].flower[k];
							if (vert>node && oldnewflag[vert-node] != 0)
								newK_ptr[jj].flower[k] = old2new[vert-node];
						}
					}
					v = kData[v].flower[0];
				} while (v != startv);
			}
		}

		// throw out unused numbs, replacing from top. 
		final_node_count = 2 * node;
		for (int i = node + 1; i <= 2 * node; i++) {
			int j,jj,k;
			if (oldnewflag[i-node] != 0) { // i is available to be used for new data
				// start from top and drop until we find something not already moved elsewhere 
				while (oldnewflag[final_node_count-node] != 0) 
					final_node_count--;
				if ((newval = final_node_count) > i) {
					// swap nums: good data 'newval' goes to opening at i.
					for (j = 0; j < (newK_ptr[newval].num+newK_ptr[newval].bdryFlag); j++) {
						jj = newK_ptr[newval].flower[j];
						for (k = 0; k <=newK_ptr[jj].num; k++) {
							if (newK_ptr[jj].flower[k] == newval)
								newK_ptr[jj].flower[k] = i;
						}
					}
					newR_ptr[i] = newR_ptr[newval].clone();
					newK_ptr[i] = newK_ptr[newval].clone();
					// indicate newval as available, i as now in use
					oldnewflag[newval-node] = 1;
					oldnewflag[i-node] = 0;
					
					// change tracking indices
					if (newval==alp_sym) alp_sym=i;
					for (int kk=1;kk<=node;kk++) 
						if (old2new[kk]==newval) old2new[kk]=i;
				}
			}
		}
		
		// attach new data structures, clean up, and leave 
		free_overlaps();
		nodeCount = final_node_count;
		kData = newK_ptr;
		rData = newR_ptr;
		alloc_pack_space(nodeCount, true);
		setCombinatorics();
		fillcurves();
		// 'vertexMap' holds {orig,double} indices
		vertexMap=new VertexMap();
		for (int i=1;i<=node;i++)
			vertexMap.add(new EdgeSimple(i,old2new[i]));
		return alp_sym;
	} 

	/**
	 * Replace this packing by a new one which has been doubled across the edge(s) 
	 * defined by 'vertlist'. 'vertexMap' holds {orig,double} pairings; for each
	 * original vertex, gives its doubled index
	 * @param vertlist
	 * @return 0 on error or improper data
	 */
	public int double_on_edge(NodeLink vertlist) {
		if (vertlist==null || vertlist.size()<3) return 0;
		int count=0;
		Iterator<Integer> vlist=vertlist.iterator();
		int v,w=0;
		int startv=v=(Integer)vlist.next();
		count++;
		if (!isBdry(v)) return 0;
		while (vlist.hasNext()) {
			w=(Integer)vlist.next();
			if (w!=kData[v].flower[0]) return 0;
			count++;
			v=w;
		}
		int stopv=w;
		int bdrycnt=0;
		// CMD_REORG -- should do directly
		try {
			bdrycnt=NodeLink.countMe(
					new NodeLink(this,new String("b("+startv+" "+startv+")")));
		} catch (Exception ex) {return 0;}
		if (count>bdrycnt-3 && count!=(bdrycnt+1)) { // while boundary is okay
			CirclePack.cpb.errMsg("double: the indicated segment is in error or too long");
			return 0;
		}
		
		PackData p=this.copyPackTo();
		p.reverse_orient();
		return PackData.adjoin(this,p,stopv,stopv,count-1);
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
	 * Cut largest simply connected subcomplex containing 'v' (generation 1) 
	 * and vertices of generation up to and including 'gen'.
	 * @param v int
	 * @param gen int
	 * @return @see PackData, null on error or no change
	 */
	public PackData gen_cut(int v,int gen) {
	    int oldcount,count;

	    // build the appropriate red chain 
	    RedList redlist=redChainer.build_gen_redlist(v,null,gen);
	    count=redChainer.util_1;
	    if (redlist==null || count>=nodeCount) { // packing unchanged
	    	return null;
	    }

	    /* Mark the outside verts 'poison' and call cookie to finish up */

	    for (int i=1;i<=nodeCount;i++) setVertMark(i,0);
	    RedList rtrace=redlist;
	    boolean keepon=true;
	    while (rtrace!=redlist || keepon) {
	    	keepon=false;
	    	setVertMark(faces[rtrace.face].vert[face_nghb(rtrace.next.face,rtrace.face)],1);
	    	rtrace=rtrace.next;
	    }
	    
	    // now for cookie
	    oldcount=nodeCount; // save orig nodecount
	    String cookit=new String("m -v "+v);
	    CookieMonster cM=null;
	    try {
	    	cM=new CookieMonster(this,cookit);
	    } catch (Exception ex) {
	    	System.err.println("Error in starting CookieMonster");
	    	cM=null;
	    	return null;
	    }
	    int outcome=cM.goCookie();
	    if (outcome<0) {
	    	flashError("gen_cut: CookieMonster returned negative outcome");
	    	cM=null;
	    	return null;
	    }
	    if (outcome==0) {
	    	CirclePack.cpb.msg("gen_cut: no change in the packing");
	    	cM=null;
		    return null;
	    }

	    // for outcome >0, set up vertexMap
	    if (nodeCount<oldcount) {
	    	for (int i=1;i<=nodeCount;i++) kData[i].plotFlag=1;
	    	if (cM.new2old!=null) {
	    		vertexMap=new VertexMap();
	    		int w;
	    		for (int j=1;j<=nodeCount;j++)
	    			if ((w=cM.new2old[j])!=0) {
	    				vertexMap.add(new EdgeSimple(j,w));
	    			}
	    	}
		    CirclePack.cpb.msg("gen_cut seems to have succeeded; nodeCount is "+nodeCount);
	    }
	    PackData newp=cM.getPackData();
	    cM=null;
	    return newp;
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
		mob = Mobius.affine_mob(a1, a2, b1, b2);
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
	    	if (vv==0) return 0; // no boundary
	    	v=vv;
	    }
	    int n;
	    if ((n=bdry_comp_count(v))==0 || n<4 )
		return 0;
	    n=(int)(n/2);
	    int w=v;
	    for (int i=1;i<=n;i++) w=kData[w].flower[0];
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
	  int []genlist=q.label_generations(depth,uP);
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
		  while ((nv=kData[nv].flower[0])!=start) {
		    fp.write(new String(nv+" "));
		  }
	      }
	      fp.write("];\n\n");
	      fp.write("pause %% press any key to get exit distribution "+
		      "function of exit probabilities.\n");
	      fp.write("dist=zeros("+nodes+");dist("+alpha+")=1\n");
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
	 * Set the 'sidePairs' label 'Character's. Paired sides will 
	 * be labeled 'a','b', etc, with pair 'A', 'B', etc. Any nonpaired 
	 * will be labeled '1','2', etc.
	 * This is to be used in 'disp -Rn' calls and in 'pair_mob' calls
	 * to apply side-pairings.
	 * @param sp_index in 'sidePairs'; >= 0, < 'sidePairs.size()'
	 * @return count or zero on error.
	 */
	public int labelSidePairs() throws CombException {
		int count=0;
		int pairNum=0;
		int edgeNum=1;
		if (sidePairs==null || sidePairs.size()==0) return 0;
		Iterator<SideDescription> sides=sidePairs.iterator();
		SideDescription epair=null;
		while (sides.hasNext()) {
				epair=(SideDescription)sides.next();
				if (epair.label==null) { // need label
					if (epair.pairedEdge!=null) {
						char c=(char)('a'+pairNum);
						epair.label=String.valueOf(c);
						// Note: in place of upper case, use repeat lower
						//   (e.g. instead of 'A', label is 'aa'.)
						epair.pairedEdge.label=
							new String(String.valueOf(c)+String.valueOf(c));
						pairNum++;
						count++;
					}
					else { // gets next integer as label
						epair.label=String.valueOf(edgeNum);
						edgeNum++;
						count++;
					}
				}
		} // end of 'while'
		return count;
	}
	
	/**
	 * adjust a radius by a given factor
	 * @param v, vertex
	 * @param factor, double, positive
	 * @return 0 on error
	 */
	public int adjust_rad(int v,double factor) {
		if (v<1 || v>nodeCount || factor<=0.0) return 0;
		double newrad;
		int count=0;
		double rad=getRadius(v);
		if (hes<0) { // hyperbolic
			if (rad<0) { // infinite radius becomes large, finite
				newrad=.9995;
				count++;
			}
			else { // replace x by x=1-exp(factor*(log(1-x)))
				newrad=1.0-Math.exp(factor*Math.log(1.0-rad));
    			count++;
			}
		}
		else if (hes>0) { // spherical
			newrad=rad*factor;
			if (newrad<=0 || newrad>=Math.PI) newrad=rad; // illegal, don't change
			else count++;
		}
		else { // eucl
			newrad=rad*factor;
			count++;
		}
		if (count>0) setRadius(v,newrad);
		return count;
	}

	/**
	 * Initialize the 'center' elements of the 'redChain' to the
	 * center stored in 'rData' or to 0.
	 * @return count set
	 */
	public int initRedCenters(RedList redchain) {
		if (redchain==null) return 0;
		int count=0;
		RedList rtrace=redchain;
		boolean keepon=true;
		Complex z=null;
		while ((keepon || rtrace!=redchain) && count<3*faceCount) {
			keepon=false;
			int f=rtrace.face;
			int v=faces[f].vert[rtrace.vIndex];
			z=getCenter(v);
			if (z!=null)
				rtrace.center=new Complex(z);
			else 
				rtrace.center=new Complex(0.0);
			rtrace=rtrace.next;
			count++;
		}
		if (count>=3*faceCount) {
			throw new CombException("redchain doens't close");
		}
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
	 * Given a designated closed ordered chain 'cutNodes' of 
	 * vertices, return the shortest closed ordered path 
	 * from 'base' through a single vertex on 'cutNodes'. 
	 * Null on error. Result should pass from right to left across
	 * 'cutNodes'.
	 * @param base vertex
	 * @param cutNodes
	 * @return, closed chain of vertices or null on error
	 */
	public NodeLink findShortPath(int base,NodeLink cutNodes) {

		// convert to graph excluding cutNodes
		GraphLink graph=getAsGraph(cutNodes);
		if (base<1 || base>nodeCount)
			base=alpha;
		if (cutNodes.containsV(base)>=0) {
			int nb=base;
			for (int j=0;(j<countFaces(base) && nb==base);j++) {
				int p=kData[base].flower[j];
				if (cutNodes.containsV(p)<0)
					nb=p;
			}
			if (nb==base) 
				throw new CombException("base vertex "+base+" is not suitable");
			base=nb;
		}
		
		// label generations from base 
		int []vgen=graph.graphDistance(base,nodeCount);
				
		// prepare storage
		int sz=cutNodes.size()-1;
		int []leftv=new int[sz];
		int []rightv=new int[sz];
		
		// pass through vert list and process 
		int vert=0;
		int prev=0;
		int next=0;
		for (int v=1;v<sz;v++) {
			vert=cutNodes.get(v);
			prev=cutNodes.get(v-1);
			next=cutNodes.get(v+1);
//System.err.println("prev="+prev+"; vert="+vert+"; next="+next);			
			
			// find petal on right of curve of lowest generation
			int num=countFaces(vert);
			int preindx=(nghb(vert,prev)+1)%num;
			int nextindx=nghb(vert,next);
			int infgen=nodeCount;
			if (nextindx<preindx)
				nextindx +=num;
			for (int j=preindx;j<nextindx;j++) {
				int k=kData[vert].flower[j%num];
				if (k<=graph.maxIndex && vgen[k]>0 && vgen[k]<infgen) {
					infgen=vgen[k];
					rightv[v]=k;
				}
			}

			// find petal on left of curve of lowest generation
			nextindx=(nghb(vert,next)+1)%num;
			preindx=nghb(vert,prev);
			if (preindx<nextindx)
				preindx +=num;
			infgen=nodeCount;
			for (int j=nextindx;j<preindx;j++) {
				int k=kData[vert].flower[j%num];
				if (vgen[k]>0 && vgen[k]<infgen) {
					infgen=vgen[k];
					leftv[v]=k;
				}
			}
//System.err.println(" vert "+vert+": left "+leftv[v]+" right "+rightv[v]);			
		}
		
		// check for link giving shortest closed path
		int minindx=-1;
		int minlength=1000000;
		int minright=-1;
		int minleft=-1;
		for (int v=1;v<sz;v++) {
			if (rightv[v]>0 && leftv[v]>0) {
				int sm=vgen[rightv[v]]+vgen[leftv[v]];
				if (sm<minlength) {
					minlength=sm;
					minindx=v;
					minright=rightv[v];
					minleft=leftv[v];
				}
			}
		}
		if (minindx<0) {
			throw new CombException("Didn't find a link (need more work)");
			// due to combinatorics, some cutNOdes may not have a left or
			//   a right petal in the graph.
		}
		
		// here's the vertex closing up the shortest link
		int capvert=cutNodes.get(minindx);
		NodeLink pathList=new NodeLink(this,capvert);
		
		// add to chain from right side to base
		next=minright;
		pathList.add(next);
		while (vgen[next]>1) {
			int bestpetal=kData[next].flower[0];
			int min=vgen[bestpetal];
			for (int j=1;j<(countFaces(next)+getBdryFlag(next));j++) {
				int k=kData[next].flower[j];
				int pgen=vgen[k];
				if (pgen>0 && (min<=0 || pgen<min)) {
					min=pgen;
					bestpetal=k;
				}
			}
			if (vgen[bestpetal]<=0 || vgen[bestpetal]>=vgen[next]) {
				throw new CombException("problem following graph to base "+base);
			}
			next=bestpetal;
			pathList.add(next);
		}
		
		// reverse order
		pathList=pathList.reverseMe();
		
		// finish chain from left side to base
		int gotHit=-1;  // set this if we encounter a vert in pathList
		next=minleft;
		if (pathList.containsV(next)>=0) 
			gotHit=next;
		pathList.add(next);
		while (gotHit<0 && vgen[next]>1) {
			int bestpetal=kData[next].flower[0];
			int min=vgen[bestpetal];
			for (int j=1;j<(countFaces(next)+getBdryFlag(next));j++) {
				int k=kData[next].flower[j];
				int pgen=vgen[k];
				if (pgen>0 && (min<=0 || pgen<min)) {
					min=pgen;
					bestpetal=k;
				}
			}
			if (vgen[bestpetal]<=0 || vgen[bestpetal]>=vgen[next]) {
				throw new CombException("problem following graph to alpha");
			}
			next=bestpetal;
			if (pathList.containsV(next)>=0)
				gotHit=next;
			pathList.add(next);
		}

		// can shorten list?
		if (gotHit>0) {
			while (pathList.get(0)!=gotHit) {
				pathList.remove(0);
			}
		}
		
		return pathList;
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
			if (exclude!=null && exclude.containsV(v)<0) 
				for (int j=0;j<(countFaces(v)+getBdryFlag(v));j++) {
					if ((k=kData[v].flower[j])>v && exclude!=null && exclude.containsV(k)<0)
						ans.add(new EdgeSimple(v,k));
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
			int vc=faces[f].vertCount;
			int []vert=new int[vc];
			for (int m=0;m<vc;m++) 
				vert[m]=faces[f].vert[m];
			Tri.faces[f]=new Face(vc);
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
	 * Idea of resampling is to apply some filter to the vertices, 
	 * marking those that pass the test. I think this method will be
	 * made more general and placed elsewhere, but for now, it is
	 * tailored to Poisson density proportional "density" divided by
	 * signed distance to Gamma, mode=1.
	 * @param p
	 * @param Gamma, Jordan curve
	 * @param mode, int. 
	 * @param maxThinning, double: Poisson density normally 
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
	 * Given packing and NodeLink of chosen interior vertices, create
	 * new packing by Delaunay triangulating the chosen vertices, 
	 * plus all the original packings boundary vertices, at their current 
	 * locations.
	 *
	 * TODO: Extend to cutting out a top disc, retriangulating it and
	 * pasting new triangulation back in. Would be fun to alternate:
	 * cut, Delaunary triangulate, pack, layout, repeat.
	 * 
	 * TODO: This has not been thoroughly debugged.
	 * 
	 * @param p, initial packing --- must be topological disc 
	 * @param NodeLink of vertices to include in the new packing 
	 * @return PackData, vertexMap contains {orig,new} matchings.
	 */
	public static PackData sampledSubPack(PackData p,NodeLink chosen) {
		  if (p.getBdryCompCount()!=1) {
			  throw new ParserException(
					  "packing must have one and only one bdry component");
		  }
		  // create DelaunayData
		  DelaunayData dData=new DelaunayData();
		  dData.geometry=0;
		  dData.pointCount=chosen.size();
		  dData.ptX=new double[dData.pointCount+1];
		  dData.ptY=new double[dData.pointCount+1];
		  
		  // must associate original and new indices
		  p.vertexMap=new VertexMap();
		  
		  Iterator<Integer> vlst=chosen.iterator();
		  int v;
		  int hit=1;  // previous code suggests numbering from 1
		  while (vlst.hasNext()) {
			  v=vlst.next();
			  Complex pz=p.getCenter(v);
			  dData.ptX[hit]=pz.x;
			  dData.ptY[hit]=pz.y;
			  p.vertexMap.add(new EdgeSimple(v,hit)); // {original,new}
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
				  dData.edgeV[hits]=p.vertexMap.findW(edge.v);
				  dData.edgeW[hits]=p.vertexMap.findW(edge.w);
				  hits++;
			  }
		  }
		  
		  dData=new DelaunayBuilder().apply(dData);
		  Triangulation Tri = dData.getTriangulation();
		  PackData randPack=Triangulation.tri_to_Complex(Tri,dData.geometry);
		  randPack.setCombinatorics();
		  randPack.fileName=new String("resampled"+dData.pointCount);
		  for (int vv=1;vv<=randPack.nodeCount;vv++) 
			  randPack.setPlotFlag(vv,1);
		  return randPack;
	}
	
	/**
	 * Find the oriented chain of faces outside 'beach', which is a 
	 * connected closed chain of successive neighbor vertices. 
	 * If 'beach' verts are all interior, face list should be closed 
	 * (first f is repeated at end). Otherwise, face list will consist 
	 * of one or more open chains of faces. It may be empty if 'beach'
	 * consists of boundary vertices.
	 * 
	 * Combinatorial detail: if face f2 shares an edge e with f0 and
	 * vert v of f2 opposite to e is degree 3, then swallow v into 
	 * the interior; thus, .. f0 f1 f2 .. replaced by .. f0 f2 .. 
	 *  
	 * @param p @see PackData
	 * @param beach @see NodeLink
	 * @return @see FaceLink, null on error
	 */
	public static FaceLink islandSurround(PackData p,NodeLink beach) {

		// check validity of 'beach'
		int bs=0;
		if (beach==null || (bs=beach.size())==0)
			return null;
		if (beach.get(0)==beach.getLast()) {
			beach.removeLast();
			bs=beach.size();
		}
		
		// are verts contiguous?
		for (int i=0;i<(bs-1);i++)
			if (p.nghb(beach.get(i),beach.get(i+1))<0)
				return null;
		if (p.nghb(beach.get(0),beach.getLast())<0)
			return null;
		
		FaceLink flist=new FaceLink(p);
		
		// find 'start', index of vert where a chain of outside faces starts
		//   look for 'curr' a bdry vert with edge to 'next' not a bdry edge; 
		//   then start is 'next'. 
		int curr,prev,next,indx_cn,indx_cp;
		int start=-1;
		for (int i=0;(i<bs && start<0);i++) {
			curr=beach.get(i);
			if (p.isBdry(curr))
				start=i;
		}
		
		boolean full=false;
		if (start<0) {
			// all 'beach' must be interior, full closed chain
			if (!p.isBdry(beach.get(0))) {
				start=0;
				full=true;
			}
			else // there are no outside faces
				return null;
		}
		
		if (full) {
			for (int i=0;i<bs;i++) {
				int b_ind=(start+i)%bs;
				curr=beach.get(b_ind);
				prev=beach.get((b_ind-1+bs)%bs);
				next=beach.get((b_ind+1)%bs);
				indx_cp=p.nghb(curr,prev);
				indx_cn=p.nghb(curr,next);
				int num=p.countFaces(curr);
				int del=indx_cn-indx_cp;
				
				// for first face only, have to add face shared with prev
				int offset=1;
				if (i==0)
					offset=0;
				
				// interior? 
				if (!p.isBdry(curr)) {
					del=(del+num)%num;
					int[] faceFlower=p.getFaceFlower(curr);
					for (int v=offset;v<del;v++) {
						flist.add(faceFlower[(indx_cp+v)%num]);
					}
				}
			}
		}
			
		else {
			for (int i=0;i<=bs;i++) {
				int b_ind=(start+i)%bs;
				curr=beach.get(b_ind);
				prev=beach.get((b_ind-1+bs)%bs);
				next=beach.get((b_ind+1)%bs);
				indx_cp=p.nghb(curr,prev);
				indx_cn=p.nghb(curr,next);
				int num=p.countFaces(curr);
				int del=indx_cn-indx_cp;
				int[] faceFlower=p.getFaceFlower(curr);

				// interior? 
				if (!p.isBdry(curr)) {
					del=(del+num)%num;
					for (int v=0;v<del;v++) {
						flist.add(faceFlower[(indx_cp+v)%num]);
					}
				}
			
				// bdry?
				else {
					if (indx_cp<(num-1) && i!=0) {
						for (int ii=indx_cp;ii<num;ii++)
							flist.add(faceFlower[ii]);
					}
					if (indx_cn>0 && i!=bs) {
						for (int ii=0;ii<indx_cn;ii++)
							flist.add(faceFlower[ii]);
					}
				}
			}
		} // end of loop through beach

		// check: swallow any degree 3 verts? (see note above)
		boolean got=true;
		while (got) { // at least one pass
			got=false;
			int len=flist.size();
			for (int i=0;(i<len && !got);i++) {
				int j=(i+2)%len;
				if (j!=0 && i!=len-1 && p.face_nghb(flist.get(i),flist.get(j))>=0) {
					int kk=(i+1)%len;
					flist.remove(kk);
					got=true;
				}
			}
		} // end of while
		
		return flist;
	}
	
	/**
	 * Given list of "interior" vertices and 'alp' vertex, build 
	 * EdgeLink lists of bdry edges counterclockwise around the connected 
	 * component of 'intV' containing 'alp' (including bdry vertices, even
	 * if in 'intV'). Typical: bdry edges counterclockwise 
	 * around interior. However, we create a vector because there may 
	 * be more components and/or isolated bdry points (given its own list 
	 * as (b,b) and listed after the others).
	 * (Refer to intV as "interior" and nghbs as "bdry") 
	 * 
	 * TODO: for non-typical situations, not always clear what is best: 
	 *   e.g., multiple boundary components, vlist contains bdry verts, 
	 *   hit a boundary vert from two sides, incoming edge, etc, etc.
	 *   
	 * @param intV NodeLink
	 * @param alp int, preferred starting interior, default to first entry
	 * @return Vector<EdgeLink>, null on empty vector
	 */
	public Vector<EdgeLink> surroundComp(NodeLink intV, int alp) {
		Vector<EdgeLink> bdryLinks=new Vector<EdgeLink>();
		if (intV==null || intV.size()==0)
			throw new ParserException("surround list error");
		if (alp<=0 || alp > nodeCount)
			alp=intV.get(0);
		
		// we handle connected components of intV one at a time,
		//    get its outer edge list and add it to our vector
		
		// start by marking intV verts with -v in 'utilFlag'
		for (int v=1;v<=nodeCount;v++)
			kData[v].utilFlag=0;
		Iterator<Integer> vl=intV.iterator();
		while (vl.hasNext()) {
			int v=vl.next();
			kData[v].utilFlag=-v;
		}
		
		if (kData[alp].utilFlag>=0) // alp must be in 'intV'
			alp=intV.get(0);

		// gather info in 'util': 
		//    -v for v in component (and interior of packing)
		//    +v for neighbors of intV or bdry in 'intV'
		int []util=new int[nodeCount+1];
		NodeLink bdryV=new NodeLink(this);
		
		NodeLink currl=new NodeLink();
		NodeLink nextl=new NodeLink();
		util[alp]=-alp;
		nextl.add(alp);
		while (nextl.size()>0) {
			currl=nextl;
			nextl=new NodeLink();
			Iterator<Integer> cl=currl.iterator();
			while (cl.hasNext()) {
				int w=cl.next();
				for (int j=0;j<=(countFaces(w)+getBdryFlag(w));j++) { 
					int k=kData[w].flower[j];
					if (kData[k].utilFlag<0 && util[k]==0) { // new one in 'intV'?
						util[k]=-k;
						if (isBdry(k)) {
							util[k]=k; // is bdry of packing
							bdryV.add(k);
						}
						nextl.add(k);
					}
					else if (util[k]==0) { // is nghb of 'intV'
						bdryV.add(k);
						util[k]=k;
					}
				}
			}
		}
			
		// get next w and process it entirely; it may be hit
		//    multiple times, but all those should be picked
		//    up in same bdry component of current 'comp'.
		Iterator<Integer> bV=bdryV.iterator();
		while (bV.hasNext()) {
			int w=bV.next();
				
			EdgeLink edgelist=new EdgeLink(this);
				
			// sweep clw to find int nghb and subsequent bdry nghb
			boolean done=false;
			int num=countFaces(w);
			int bhit=-1;
			int ihit=-1;
			for (int j=(num+getBdryFlag(w));(j>0 && bhit<0);j--) {
				int k=kData[w].flower[j];
					
				// first vlist nghb?
				if (ihit<0 && util[k]<0)
					ihit=j;
				
				// first bdry nghb after int nghb
				if (ihit>0 && util[k]>0)
					bhit=j;
			}
				
			// no int nghb? error
			if (ihit<0)
				throw new CombException("error in surroundVlist: must have vlist nghb");
				
			// no bdry nghb?
			if (bhit<0) {
				
				// special case: w is bdry vert and only vlist nghb is
				//   the downstream bdry nghb. Start edge, but in reverse
				//   direction. 
				if (isBdry(w) && ihit==0) {
					EdgeSimple edge=new EdgeSimple(kData[w].flower[1],w);
					edgelist.add(edge);
					// reset w, ihit, bhit for later processing
					int u=kData[w].flower[0];
					int oldw=w;
					w=edge.v;
					ihit=nghb(w,u);
					bhit=nghb(w,oldw);
				}
				
				// else its isolated and we're done
				else {
					edgelist.add(new EdgeSimple(w,w)); // (w,w) indicates isolated bdry vert
					done=true;
				}
			}
				
			// build out this edge cclw until we close up, or end at bdry.
			// We have bdry vert w, ihit index to int nghb, bhit index to cclw bdry nghb
			if (!done) {
				int startw=w;
				int nxtW=kData[w].flower[bhit];
				
				// get cclw edges starting with w (can skip in special case above when
				//    we know we have the last cclw segment)
				if (edgelist.size()==0) {
					EdgeSimple startedge=new EdgeSimple(w,nxtW);
					edgelist.add(startedge);
					// go cclw until you get closing edge or end at bdry
					boolean goon=true; 						
					while (nxtW!=startw || goon) {
						goon=false;
						int backindx=nghb(nxtW,w);
						w=nxtW;
						int nhit=-1;
						if (isBdry(w)) { // w is bdry vert?
							if (backindx==0)
								throw new CombException("surroundVlist: error. hit bdry?");
							if (backindx>0) {
								for (int j=backindx-1;(j>0 && nhit<0);j--)
									if (util[kData[w].flower[j]]>=0) // next bdry?
										nhit=j;
							}
						}
						else { // w is interior vert
							int numw=countFaces(w);
							if (util[(backindx-1+numw)%numw]>=0)
								throw new CombException("surroundVlist: clw should be interior");
							for (int j=2;(j<=numw && nhit<0);j++) { 
								int idx=(backindx-j+numw)%numw;
								if (util[kData[w].flower[idx]]>=0)
									nhit=idx;
							}
						}
						
						if (nhit>=0) {
							nxtW=kData[w].flower[nhit];
							EdgeSimple e=new EdgeSimple(w,nxtW);
							if (edgelist.isThereVW(w,nxtW)<0) { // not in list
								edgelist.add(e);
								w=nxtW;
								goon=true;
							}
							else {
								if (e.v!=startedge.v || e.w!=startedge.w)
									throw new CombException("surroundVlist: should be initial edge");
								else 
									done=true;
							}
						} 
					} // end of while
				} // end of cclw direction
				
				// now clw direction: if path were closed, we would be done.
				//   Only way to end is run into bdry(?)
				if (!done) {
					w=startw;
					nxtW=kData[w].flower[bhit];
					while (bhit>=0) {
						int uphit=-1;
						int numw=countFaces(w);
						
						// w is bdry
						if (isBdry(w)) {
							if (bhit==numw || util[kData[w].flower[bhit+1]]>=0)
								throw new CombException("surroundVlist: bhit can't point upstream");
							for (int j=bhit+1;j<=numw;j++) {
								int k=kData[w].flower[j];
								if (util[k]>=0)
									uphit=j;
							}
							bhit=-1;
						}
						
						// w is interior
						else {
							for (int j=1;j<numw;j++) {
								int m=(bhit+1+j)%numw;
								if (util[m]>=0)
									uphit=m;
							}
							bhit=-1;
						}
						
						// found next cclw nghb petal, upstream 
						if (uphit>=0) {
							int upw=kData[w].flower[uphit];
							edgelist.add(0,new EdgeSimple(upw,w));
							bhit=nghb(upw,w);
							w=upw;
						}
							
					} // end of while
				}
				done=true;
				if (edgelist!=null && edgelist.size()>0)
					bdryLinks.add(edgelist);
			}
			
		} // done with while loop
		
		if (bdryLinks==null || bdryLinks.size()==0)
			return null;
		
		// move any isolated to the end 
		Vector<EdgeLink> retLinks=new Vector<EdgeLink>();
		int N=bdryLinks.size();
		int tick=0;
		for (int n=0;n<N;n++) {
			EdgeLink el=bdryLinks.remove(0);
			
			// keep non-isolated in order at front
			if (el.size()>1)
				retLinks.add(tick++,el);
			
			// move isolated to the end
			else 
				retLinks.add(el);
		}

		return retLinks;
	}
	
	public void setSidePairs(PairLink plink) {
		sidePairs=plink;
	}
	
	public PairLink getSidePairs() {
		return sidePairs;
	}
	
	/**
	 * Get the indices of the first sides in all side pairs
	 * @return int[], null if no paired sides
	 */
	public NodeLink getPairIndices() {
		NodeLink indices=new NodeLink();
		if (packDCEL!=null) {
			if (packDCEL.pairLink!=null || packDCEL.pairLink.countPairs()==0)
				return null;
			for (int j=0;j<packDCEL.pairLink.size()-1;j++) {
				D_SideData sidd=packDCEL.pairLink.get(j);
				if (sidd.mateIndex>sidd.spIndex)
					indices.add(sidd.spIndex);
			}
			return indices;
		}
		else {
			if (sidePairs!=null || sidePairs.countPairs()==0)
				return null;
			for (int j=0;j<sidePairs.size()-1;j++) {
				SideDescription sdd=sidePairs.get(j);
				if (sdd.mateIndex>sdd.spIndex)
					indices.add(sdd.spIndex);
			}
			return indices;
		}
	}

	/**
	 * Return the Mobius with the given side-pair index
	 * @param e int
	 * @return Mobius
	 */
	public Mobius getSideMob(int e) {
		if (packDCEL!=null) {
			try {
				return packDCEL.pairLink.get(e).mob;
			} catch(Exception ex) {
				throw new CombException("failed to get side pair "+e);
			}
		}
		try {
			return sidePairs.get(e).mob;
		} catch(Exception ex) {
			throw new CombException("failed to get side pair "+e);
		}
	}
	
	/**
	 * check if 'schwarzian's are allocated in 'kData'
	 * @return boolean
	 */
	public boolean haveSchwarzians() {
		for (int v=1;v<=nodeCount;v++) {
			try {
				for (int j=0;j<=countFaces(v);j++) { 
					if (kData[v].schwarzian==null || kData[v].schwarzian.length<(countFaces(v)+1))
						return false;
				}
			} catch (Exception ex) {
				return false;
			}
		}
		return true;
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
	 * The "conformal geometry", -1,0,1, has to do 
	 * both with 'intrinsicGeom' and with interior 
	 * branching; assume 'genus' is set.
	 * Depend on Riemann-Hurwitz formula.
	 *   mapping pi:S'->S, br=branch count, N=sheeet count,
	 *    then: chi(S')=N*chi(S)-br
	 *    
	 * E.g., torus, 4 simple branch points, has
	 * spherical geometry, i.e., via Weierstrass 
	 * functions (though we can't yet compute it 
	 * in general). A higher genus closed S' 
	 * might be mapped to a torus or the sphere 
	 * (perhaps as an orbifold versus normal 
	 * covering, as with a sphere having one 
	 * branch point mapping to a sphere). 
	 * Note: we only count integral branching based on
	 * 'aim's; throw excption for non-integral branching.
	 * @param p PackData
	 * @return int -1, 0, 1, default -1
	 * @throws ParserException
	 */
	public static int getConfGeometry(PackData p) 
		throws ParserException {
		int iG=getIntrinsicGeom(p);
		int brN=0; // branching: only count integral
		for (int v=1;v<=p.nodeCount;v++) {
			double aim=p.getAim(v);
			if (!p.isBdry(v) && aim>0.0) { // interior, not cusp
				int tick=0;
				while ((aim-(tick+1)*Math.PI*2.0)>.001)
					tick++;
				if (Math.abs(aim-(tick+1)*Math.PI*2.0)>.001)
					throw new ParserException("there is non-integer branching at "+v);
				brN += tick;
			}
		}
		int NX=2-2*p.genus+brN;
		if (NX==0)
			return 0; // should support eucl geom
		else if (2*(NX/2)==NX) // even? NX/2= sheet count
			return 1; // should support spherical geom
		return iG; // just its own geom
	}
		
} // end of 'PackData' class
	  
/** =================== local utility classes ========================
/** for use with 'adjoin' */
class Overlap {
    int v,w;
    double angle;
    Overlap next;
}
	  
