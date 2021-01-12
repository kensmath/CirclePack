package microLattice;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.InOutException;
import exceptions.ParserException;
import input.CPFileManager;
import input.CommandStrParser;
import komplex.AmbiguousZ;
import komplex.CookieMonster;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.KData;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.VertexMap;
import packing.PackData;
import packing.PackExtender;
import packing.PackMethods;
import packing.RData;
import panels.CPScreen;
import panels.PathManager;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.PathUtil;
import util.PlatenParams;
import util.StringUtil;

/**
 * NOTE: 3/30/17. This is tailored for constructions associated with 
 * ORNL and 3Dprinting and should be easy to convert to C++ or matlab.
 * The original code started in 'HexPlaten', but has been improved and
 * streamlined. 
 * 
 *  * Intensities will be interpolated from data provided in a file: 
 *    the intensity "field" has been a rectangular grid, 0 intensity 
 *    outside; 
 *    TOTO: we need more general methods of getting intensities.
 *  * The intensity file or the user should also specify: 'center' 
 *    and 'angle' for positioning the microgrid.
 *  * Shape will be cut out after construction instead of before.
 *  * Shape/intensity data are left in their real-world locations; our
 *    microgrid is scaled, translated, rotated to match.
 *    
 * Motivation: creating circle packings whose duals can be used as 2D grids 
 * for 3D printer head planning.
 * Started in response to inquiries by Greg Dreifus while he was at ORNL's,
 *    carried on with John Bowers (JMU), Pum Kim, and others at ORNL MDF.
 * 
 * Potential advantages: * can use triangulation or dual; * can manipulate (within
 * limits) to change the shape; * connection to analytic/harmonic functions -- e.g.,
 * heat dissipation * dual graph generically has trivalent vertices, good for
 * rigidity.
 * 
 * Aim Here: Use interconnected hex lattices so we can change circle sizes to
 * accommodate different strength/density requirements. However, we will have to
 * use overlaps and inversive distances mixed in with the usual tangencies. The
 * idea is to allow selective refinement but still maintain ability to manipulate
 * with conformal-like behavior.
 * 
 * At the base is a "microgrid" --- several hex generations around the shape center
 * forming a global hex pattern. Within that we define "supergrids", uniform hex 
 * lattices chosen from the microgrid (always including the center as a supergrid 
 * vertex). The numerical "diameter" of the microgrid is 1, while supergrids have 
 * integral diameters.
 * 
 * The underlying packing always represents the full microgrid, but it is scaled by 
 * 'microScaling' (so its radii are 'microScaling'/2.0).
 * Only selected centers will end up being used; the data is kept in arrays of
 * 'Node's, which know 'myVert' (their index in the microgrid), their integral
 * diameters, and other pertinent data.
 * 
 * @author kstephe2, February 2017
 *
 */
public class MicroGrid extends PackExtender {
	
	final int MAX_GEN=300;
	final int MAX_LEVELS=15; 

	// some mode differences
	int mode;                   // 1 = using intensity file, general shape (original mode)
								// 2 = in disc, intensity+area density (2/2020)
	String pathFileName;  	    // If given file name, mode 1
	Path2D.Double myClosedPath; // backup for 'CPBase.ClosedPath' in case that is reused
	String intensityFile;   	// type of intensity file depends on mode  
	Vector<Double> areaDensity; // used in mode 2.
	boolean script_flag;        // are files in the script? 

	PackData smoothPack;        // results packing with attached 'smoother'
	PackData qackData;          // base packing, packing in disc, bdry perpendicular to unit circle 
								// domain for conformal map to the target curved surface 
	
	PlatenParams platenP;		// hold the various microGrid parameters
	int max_gen;                // maximum number of generations in microGrid
	int microN;					// computed number of generations of microGrid
	
	// intensity raw data for mode=1
	int xcols;					// number of rows 
	int yrows;					// number of columns
	double []iBox;				// {lowx, lowy, upx, upy}
	double [][]intensityField;  // holds irows x icols matrix for interpolating intensity
	double xinc;				// x increment for intensityField
	double yinc;				// y increment for intensityField
	double minIntensity;	    // min among non-zero intensities
	double maxIntensity;		// max intensity
	Complex [][]gridPoints;		// intensity field grid points for debugging

	// 'level's start at 1 (smallest circles) to 'levelcount'
	int basediam;               // smallest combinatorial diameter; depends on ratio of radii between steps
	int levelCount;				// number of levels of circle sizes
	int []stepDiam;				// combinatorial diameters in terms of microgrid steps; index from 1
	double []stepIntensity;		// minimum intensity level for each step; index from 1
	double []stepRad;           // radius associated with each intensity step, index from 1
	int []gridN;				// if n=gridN[level], then nodeLUW[level] is (2*n+1)x(2*n+1), index from 1
	
	// microgrid alignment
	double encircleRad;			// rad of disc containing the path's rectangle
	double microScaling;		// scaling to put microgrid in the plane (2*radius for packData)
	Complex microCenter;		// where to translate the center
	double microAngle;			// radians, cclw rotation (e.g., for better alignment with path)
	
	int [][]micro2v;			// array to store 'packData' vert index by microgrid coords <u,w>
	int [][]v2micro;			// array to store coords <u,w> by vert index
	double []microIntensity;	// interpolated intensity for each microgrid vertex;
	Path2D.Double trimPath;		// for trimming final packing to avoid false edge around the boundary
	
	Node [][][]nodeLUW;			// 'Node' associated with level L, location <u,w>, U=u+microN, W=w+microN  

	NodeLink latestBdry;		// outer bdry from the support from the latest processed level
	
	// debugging stuff: utility: calls can transfer this to 'packData' so list can be viewed
	NodeLink vlist;
	FaceLink flist;		
	
	Vector<Vector<Node>> myLists;	// utility lists: nodes at supergrid levels (0 entry null)
	
	// processing status
	int lastProcessed;			// developmental: last level of circles processed
	int lastTriangled;			// developmental: last level of full triangles processed 
	int []processed;			// status of each v:
								//    0: not yet processed
								//    n>0: v is center at stage n
								//    -n<0: v was "covered" at stage n (ineligible to be center)
	int []numChosen;            // running tally at various levels
	// Exclusion stencils: edge <ur,vr> = microgrid location relative to a given grid location.
	// Two stencils for each level specifying exclusions
	EdgeLink []nixSmall;        // exclusions for this and next smaller radius
	EdgeLink []nixLarge;		// exclusions for this and next larger radius
	EdgeLink []nixTangPt;    	// exclusions about tangency of two of this radius
	Vector<Face> triangles;		// triangles for final complex (may be OBE due to Delaunay)
	EdgeLink constraintBdry;    // initialized for use as Delaunay constraint edges
	
	// constructors
	// this is the original mode, flat case, with path and intensity field
	public MicroGrid(PackData p,String pathfile,String intfile,boolean s_flag) {
		super(p);
		mode=1;   // this is mode for original development of MicroGrid
		qackData=null;
		script_flag=s_flag;
		pathFileName=pathfile;
		intensityFile=intfile;
		extensionType="MICROGRID";
		extensionAbbrev="MG";
		toolTip="'MicroGrid' for creating hex based grids for planar regionsvariable sized for circle packings to be used in 3D printing.";
		registerXType();
		if (running)
			packData.packExtensions.add(this);
		smoothPack=null;
		initialize();
	}
	
	// this is mode for new developments, namely printing on surfaces, 2/2020
	public MicroGrid(PackData p,PackData q,String intfile,boolean s_flag) {
		super(p);
		mode=2;   
		qackData=q.copyPackTo(); // copy and hold packing here
		script_flag=s_flag;
		pathFileName=null;
		extensionType="MICROGRID";
		extensionAbbrev="MG";
		toolTip="'MicroGrid' for creating hex based grids for curved surfaces to be used in 3D printing.";
		registerXType();
		if (running)
			packData.packExtensions.add(this);
		smoothPack=null;
		initialize();
	}

	public void initialize() {
		
		// common initializations
		levelCount=1;
		stepDiam=null;
		stepIntensity=null;
		microScaling=1.0;
		max_gen=(int)MAX_GEN; 
		microN=-1; // indicates not yet set
		platenP=new PlatenParams();
		platenP.set_trigger(true); // indicates need to be set
		
		// original mode -- planar regions
		if (mode==1) { 
			// read intensity field: this should give 'microCenter', 'microAngle', 'iBox', etc.
			microCenter=null;
			gridPoints=mode1_Intensity(intensityFile,script_flag);

			// read the path, set center
			File dir=CPFileManager.PackingDirectory;
			CPBase.ClosedPath=PathManager.readpath(dir,pathFileName,script_flag);
			myClosedPath=PathManager.readpath(dir,pathFileName,script_flag); // hold as backup
			Rectangle rect=CPBase.ClosedPath.getBounds();
			// (x,y) is lower left corner (not upper left) due to orientation of y.
			Complex corner=new Complex(rect.getX(),rect.getY());
			if (microCenter==null) { // not specified in intensity file
				microCenter=new Complex(rect.getX()+rect.getWidth()/2.0,rect.getY()+rect.getHeight()/2.0);
			}
		
			// find radius of disc at microCenter which encircles the curve's rectangle
			double mdist=microCenter.minus(corner).abs();
			corner.x +=rect.getWidth();
			mdist=(mdist<microCenter.minus(corner).abs()) ? microCenter.minus(corner).abs() : mdist;
			corner.y +=rect.getHeight();
			mdist=(mdist<microCenter.minus(corner).abs()) ? microCenter.minus(corner).abs() : mdist;
			corner.x -=rect.getWidth();
			mdist=(mdist<microCenter.minus(corner).abs()) ? microCenter.minus(corner).abs() : mdist;
			encircleRad=mdist*1.2; // add some extra
		}
		// curved surface mode
		else if (mode==2) {
			microCenter=new Complex(0.0);
			microAngle=0.0;
			encircleRad=1.1; // slightly larger than unit disc
			CPBase.ClosedPath=PathUtil.getCirclePath(1.0,new Complex(0.0),180);
			myClosedPath=PathUtil.getCirclePath(1.0,new Complex(0.0),180); // backup
			
			// TODO: gridPoints are for debugging, not used yet in mode 2
//			gridPoints=mode2_Intensity(intensityFile,script_flag);
			
			
		}
		
		// build closed hex path for trimming
		trimPath=new Path2D.Double();
		Complex z=new Complex(1.0);
		trimPath.moveTo(z.x, z.y);
		z=new Complex(1/2.0,CPBase.sqrt3by2);
		trimPath.lineTo(z.x,z.y);
		z=new Complex(-1.0/2.0,CPBase.sqrt3by2);
		trimPath.lineTo(z.x,z.y);
		z=new Complex(-1.0);
		trimPath.lineTo(z.x,z.y);
		z=new Complex(-1.0/2.0,-1.0*CPBase.sqrt3by2);
		trimPath.lineTo(z.x,z.y);
		z=new Complex(1.0/2.0,-1.0*CPBase.sqrt3by2);
		trimPath.lineTo(z.x,z.y);
		trimPath.closePath();
		
		// get everything started
		reset(0); // default mode=0
	}
	
	/**
	 * TODO: Still working on this new mode=2 situation. We have two effects, the 
	 * area density attached to faces of 'qackData'; this comes with the 
	 * conformal map. On top of that we may impose an additional density 
	 * pulled back from the surface under the conformal map. These will be
	 * combined to replace the intensity.
	 * @param intensityfile String
	 * @param s_flag boolean
	 * @return Complex[][]
	 */
	public Complex [][]mode2_Intensity(String intensityfile,boolean s_flag) {

		// TODO: load the input file
//		File dir=CPFileManager.PackingDirectory;
//		BufferedReader fp=CPFileManager.openReadFP(dir,intensityfile,s_flag);
		
		qackData.utilBary=null;
		Complex [][]ans=null;
		FaceLink flink=new FaceLink(qackData,"a");
		areaDensity=PackMethods.areaRatio(qackData,flink);
		
		return ans;
	}
	
	/**
	 * Format for intensity file, mode=1 (planar case) 
	 * (NOTE: i,j are flipped here. Each row is a y-level, each column an x-level
	 * 
	 * 		Rows(y)/Columns(x): {yrows} {xcols}
	 * 		Box[lx,ly,ux.uy]: {lx,ly,ux.uy} 
	 * 		Center: {x} {y}
	 * 		Angle/pi: {ang}
	 * 		Intensity:
	 *        {yrows lines, each xcols long}
	 * 
	 * Note that each row is a y-value, and as row index goes up, y-value goes up.
	 * Suppose we have NxM data, N=xcols, M=yrows. Then break Box into N-1 equal
	 * vertical pieces and into M-1 horizontal pieces. So, for 3x5 data, we'll 
	 * have intensity data at 15 locations, including values around the outer 
	 * edge of Box.       
	 * @param intensityfile String
	 * @param script_flag boolean, stored in script?
	 * @return Complex[][] grid locations for debugging
	 */
	public Complex [][]mode1_Intensity(String intensityfile,boolean s_flag) {
		
		// load the input file
		File dir=CPFileManager.PackingDirectory;
		BufferedReader fp=CPFileManager.openReadFP(dir,intensityfile,s_flag);
		
		// initialize
		xcols=0;
		yrows=0;
		boolean boxhit=false;
		boolean centhit=false;
		boolean inthit=false;
		String line=null;
		microAngle=0.0;
		Complex [][]zlist;
		
		try {
        while((line=StringUtil.ourNextLine(fp))!=null) {
    		String pieces[] = line.split("\\s+");
        	if (pieces[0].startsWith("Rows(y)/Columns(x):")) {
        		yrows=Integer.parseInt(pieces[1]);
        		xcols=Integer.parseInt(pieces[2]);
        		intensityField=new double[xcols][];
        		for (int i=0;i<xcols;i++)
        			intensityField[i]=new double[yrows];
        	}
        	else if (pieces[0].startsWith("Box[lx,ly,ux,uy]:")) {
        		iBox=new double[4];
        		iBox[0]=Double.parseDouble(pieces[1]); // lower x
        		iBox[1]=Double.parseDouble(pieces[2]); // lower y
        		iBox[2]=Double.parseDouble(pieces[3]); // upper x
        		iBox[3]=Double.parseDouble(pieces[4]); // upper y
        		
        		// get size of subboxes to use when interpolating for intensities
        		xinc=(iBox[2]-iBox[0])/(double)(xcols-1);
        		yinc=(iBox[3]-iBox[1])/(double)(yrows-1);
        		boxhit=true;
        	}
        	else if (pieces[0].startsWith("Center:")) {
        		microCenter=new Complex(Double.parseDouble(pieces[1]),Double.parseDouble(pieces[2]));
        		centhit=true;
        	}
        	else if (pieces[0].startsWith("Angle/pi:")) {
        		microAngle=Double.parseDouble(pieces[1])*Math.PI;
        	}

        	// read/store the intensities 
        	else if (pieces[0].startsWith("Intensity:")) {
        		for (int j=0;j<yrows;j++) {
//        			System.out.println("j="+j);
        			line=StringUtil.ourNextLine(fp);
        			String data[] = line.split("\\s+");
        			for (int i=0;i<xcols;i++) {
//            			System.out.println("i="+i);
        				double d=Double.parseDouble(data[i]);
        				if (d<0.0)
        					Oops("There is a negative 'intensity'");
        				intensityField[i][j]=d;
        			}
        		}
        		inthit=true;
        	}
        	
        	// if nothing found based on formating, try to just
        	//    read "yrows xcols" in first row, and if that works,
        	//    then try to read the intensities.
        	else {
        		if (!inthit && pieces.length==2) {
            		yrows=Integer.parseInt(pieces[1]);
            		xcols=Integer.parseInt(pieces[2]);
            		intensityField=new double[xcols][];
            		for (int i=0;i<xcols;i++)
            			intensityField[i]=new double[yrows];

            		while ((line=StringUtil.ourNextLine(fp))!=null) {
                		for (int j=0;j<yrows;j++) {
//                			System.out.println("j="+j);
                			line=StringUtil.ourNextLine(fp);
                			String data[] = line.split("\\s+");
                			for (int i=0;i<xcols;i++) {
//                    			System.out.println("i="+i);
                				double d=Double.parseDouble(data[i]);
                				if (d<0.0)
                					Oops("There is a negative 'intensity'");
                				intensityField[i][j]=d;
                			}
                		}
            		}
            		inthit=true;
        		}
        		
        	}
        } // end of while
		} catch(Exception ex) {
			Oops("Failed in reading intensity field from '"+intensityfile+"'");
		}
        
        // if no box specified
        if (!boxhit) {
    		iBox=new double[4];
    		iBox[0]=0.0; // lower x
    		iBox[1]=0.0; // lower y
    		iBox[2]=(double)xcols; // upper x
    		iBox[3]=(double)yrows; // upper y
    		
    		// get size of subboxes to use when interpolating for intensities
    		xinc=(iBox[2]-iBox[0])/(double)(xcols-1);
    		yinc=(iBox[3]-iBox[1])/(double)(yrows-1);
    		boxhit=true;
    	}
        
        // is center not specified
        if (!centhit) {
    		microCenter=new Complex((double)xcols,(double)yrows);
    		centhit=true;
    	} 
        	
        // set centers
        zlist=new Complex[xcols][];
        for (int i=0;i<xcols;i++)
        	zlist[i]=new Complex[yrows];
        
        for (int i=0;i<xcols;i++) {
        	for (int j=0;j<yrows;j++) {
        		zlist[i][j]=new Complex(iBox[0]+i*xinc,iBox[1]+j*yinc);
        	}
        }
        
		return zlist;
	}
	
	/**
	 * Here we check the specified parameters and if they're in place, we
	 * compute the various quantities that depend on them:
	 * TODO: for future tailoring, we call with a 'mode' 
	 * @param mode int, default to mode=0.
	 * @return int nodeCount
	 */
	public int reset(int mode) {
		
		lastProcessed=0;
		double Qm1=platenP.get_Q()-1;
	
		// integral diameter of smallest circle accommodating 'ratioQ' 
		//   ratio of radii for neighboring circles, enforcing basediam>=2.     
		basediam=1;
		while (Math.floor(Qm1*basediam)<=0)
			basediam++;

		if (platenP.minR<encircleRad/max_gen) // avoid minR being too small
			platenP.set_minR(encircleRad,encircleRad/max_gen);
		
		// compute 'microN', no. of hex generations in microgrid, up to max_gen.
		microN=(int)Math.floor(encircleRad/(CPBase.sqrt3*platenP.minR))*basediam+2*basediam;
		double denom=(double)basediam;
		microScaling=2.0*platenP.minR/denom;
		
		// allocate before call
		int ndct=3*microN*microN+3*microN+1;
		micro2v=new int[2*microN+1][];
		for (int i=0;i<=2*microN;i++)
			micro2v[i]=new int[2*microN+1];
		v2micro=new int[ndct+1][];
		for (int kv=0;kv<=ndct;kv++)
			v2micro[kv]=new int[2];

		PackData newPack=GridMethods.hexByHand(microN,micro2v,v2micro);
		swapPackData(newPack);

		for (int v=1;v<=packData.nodeCount;v++) {
			int flg=0;
			packData.setCenter(v,packData.getCenter(v).times(microScaling));
			packData.setRadius(v,packData.getRadius(v)*microScaling); // so radii = microScalling/2.0.
			int []uw=v2micro[v];
			if ((uw[0]%basediam)==0 && (uw[1]%basediam)==0)
				flg=-1;
			packData.kData[v].utilFlag=flg; // -1 for basegrid vertices
		}

		// if called for, apply optional rotation, then translation
		if (Math.abs(microAngle)>.0001)
			cpCommand("rotate "+microAngle/Math.PI);
		else microAngle=0.0;
		if (microCenter.abs()>.0001) 
			for (int v=1;v<=packData.nodeCount;v++)
				packData.setCenter(v,packData.getCenter(v).add(microCenter));
		else microCenter=new Complex(0.0);
		
		// debug
//		int ckcount=0;
//		int []util=new int[packData.nodeCount+1];
//		for (int u=0;u<=2*microN;u++)
//			for (int w=0;w<=2*microN;w++) {
//				int v=micro2v[u][w];
//				if (v!=0 && packData.kData[v].utilFlag==-1) {
//					packData.kData[v].mark=1;
//					ckcount++;
//				}
//				else if (v!=0)
//					packData.kData[v].mark=0;
//			}
//		int df=packData.nodeCount-ckcount;
//		System.out.println("nodecount - ckcount = "+df);

		// store intensity values at microgrid centers, get min/max;
		setMicroIntensity(mode);

		// set up all the step arrays, 'gridN[]', 'stepDiam[]', etc.
		setStepData(mode); 
		
		numChosen=new int[levelCount+1]; // reset the counters
		
		// set up our exclusion zones: when a vertex is chosen at some level 'lev'
		//   other vertices must be excluded from being chosen, so we need to
		//   mark those in the microgrid. At the same level 'lev', we exclude
		//   vertices in hex generations strictly less than 'rads[lev]' about
		//   the chosen vertex. When 'lev'=1, this is the diameter of the smallest
		//   circles; otherwise, rads[lev] is mim distance between centers of
		//   circles of this and next largest size so that overlap is <= pi/2.

		// first, calculate these exclusion radii
		double []rads=new double[levelCount+1];
		rads[1]=(double)stepDiam[1]/2.0;
		for (int lev=2;lev<=levelCount;lev++) { // compute 'rads'
			double d=(double)stepDiam[lev];
			double dpre=(double)stepDiam[lev-1]; 
			// rads[lev]: no level 'lev' and 'lev-1' circles can be any closer 
			//   than this (radius in microgrid before scaling)
			rads[lev]=Math.sqrt(d*d+dpre*dpre)/2.0;
//			rads[lev]=(d+dpre)/2.0;
		}

		boolean debug=false; // debug=true; 

		// now populate the stencils.
		nixSmall=new EdgeLink[levelCount+1];
		nixLarge=new EdgeLink[levelCount+1];
		nixTangPt=new EdgeLink[levelCount+1];
		
		for (int lev=1;lev<=levelCount;lev++) {
			
			// 'nixSmall' is used to exclude verts strictly inside 
			//     2*radius (diameter); though this doesn't affect 
			//     other 'lev' nodes, it prevents these being chosen 
			//     at later levels.
			double nSmallRad=(double)(stepDiam[lev]);
			nixSmall[lev]=formStencil(nSmallRad-.01);

//			System.out.println("L"+lev+", small rad="+String.format("%.4f",(double)(stepDiam[lev])/2.0));
			
			// 'nixLarge' is used to exclude next 'lev+1' nodes too
			//    close to chosen 'lev' nodes.
			// NOTE: distance between centers of circles in lev and lev+1 
			//    might be less (because on different subgrids and allowed
			//    overlap) than centers of two at lev; so nixLarge may be 
			//    smaller than nixSmall.
			if (lev<levelCount) {
				// Experimenting with options here

				// PREF: I tend to prefer this option:
				// center of smaller almost on bdry of larger nghb;
				//    roughly, overlap at most 2*pi/3.
				double nLargeRad=(double)(stepDiam[lev+1])/2.0;
				if (nLargeRad<stepDiam[lev]) // make sure it's at least 'level' radius
					nLargeRad += 1.0;
				
				// Other: overlap at most pi/2; three versions:
				// (1)
//				double rd1=(double)(stepDiam[lev])/2.0;
//				double rd2=(double)(stepDiam[lev+1])/2.0;
//				nixRadius=Math.sqrt(rd1*rd1+rd2*rd2);
				// (2) using 'rads', already computed, at lev+1.
//				nixRadius=rads[lev+1];
				// (3) rather a mish-mash using 'rads'
 //				nixRadius=Math.sqrt(rads[lev]*rads[lev]+rads[lev+1]*rads[lev+1])/2.0;
				
				// now form the stencil
				nixLarge[lev]=formStencil(nLargeRad-.01);
//				if (debug) {
//					System.out.println("level "+lev+
//							": radius ="+String.format("%.4f",(double)(stepDiam[lev])/2.0)+
//							" and large nixRadius = "+
//							String.format("%.6f",nixRadius));
//				}
			}
			else {
				nixLarge[lev]=formStencil((double)(stepDiam[lev])-.01);
//				if (debug) {
//					System.out.println("level "+lev+
//							": radius ="+String.format("%.4f",(double)(stepDiam[lev])/2.0)+
//							" and large nixRadius = "+
//							String.format("%.6f",(double)(stepDiam[lev])));
//				}
			}
			
			// After choosing tangent circles at lev+1 in Step 2, exclude 
			//   verts whose circle would include their tangency point   
			if (lev>1)
				nixTangPt[lev]=formStencil(rads[lev-1]-.01);
//				nixTangPt[lev]=formStencil((double)stepDiam[lev-1]/2.0-.01);
		}

		// create arrays of 'Node's from the various superlattice levels 
		//   nodeLUW[L,U,W] is the 'Node' for level L for superlattice
		//   coords <U,W>.
		nodeLUW=new Node[levelCount+1][][];
		for (int lev=1;lev<=levelCount;lev++) {
			int DD=gridN[lev]*2+1;
			
			// DD-by-DD array (though because of hex pattern, some corners are not used)
			nodeLUW[lev]=new Node[DD][];
			for (int k=0;k<DD;k++)
				nodeLUW[lev][k]=new Node[DD];
			int d=stepDiam[lev];
			int v=0;
			
			// indices [ui,wj] at this level 
			for (int ui=0;ui<DD;ui++) {
				for (int wj=0;wj<DD;wj++) {
					
					// coords <u,w> at microgrid level
					int u=(ui-gridN[lev])*d;
					int w=(wj-gridN[lev])*d;
					
					// indices [mu,mw] into micro2v array
					int mu=u+microN;
					int mw=w+microN;
					v=micro2v[mu][mw];
					if (v!=0) {
						Node node=new Node(v,u,w,d);
						node.color=ColorUtil.spreadColor(lev-1);
						nodeLUW[lev][ui][wj]=node;
					}
				}
			}
		}
		
		// instantiate 'myLists' vectors
		myLists=new Vector<Vector<Node>>(levelCount+1);
		myLists.add(null); // first spot empty
		for (int j=1;j<=levelCount;j++)
			myLists.add(new Vector<Node>(0));
		
		// create array that monitors the processing stages
		processed=new int[packData.nodeCount+1];

		// assume we have accommodated all parameter changes.
		platenP.chgTrigger=false; 

		if (debug) { // debug=true; // color code microGrid circles by their intensity bracket
			for (int v=1;v<=packData.nodeCount;v++) {
				double ity=microIntensity[v];
				int l=1;
				while (l<levelCount && ity<stepIntensity[l])
					l++;
				packData.setCircleColor(v,ColorUtil.spreadColor(l));
			}
		}
		
		return packData.nodeCount;
	}
	
	/**
	 * Caution: requires data to have been read into p1. Compute 
	 * the length of the dual graph edges which hit the path.
	 * @param p PackData
	 * @return double
	 */
	public double gridLength(PackData p) {
		
		EdgeLink theEdges=null;
		
		try {
			GraphLink dgraph=p.dualEdges(new EdgeLink(p,"a"));
		
			// keep only those with at least one endpoint inside the path
			theEdges=new EdgeLink(p);
			Iterator<EdgeSimple> git=dgraph.iterator();
			while(git.hasNext()) {
				EdgeSimple edge=git.next();
				Complex z=p.getCenter(edge.v);
				Complex w=p.getCenter(edge.w);
				if (CPBase.ClosedPath.contains(z.x,z.y) || CPBase.ClosedPath.contains(w.x,w.y))
					theEdges.add(edge);
			}
		} catch (Exception ex) {
			Oops("Some error in calculating dual graph length");
		}
		return p.dualLength(theEdges);
	}
	
	/**
	 * This creates a packing of the 'level' superlattice.
	 * It's 'VertexMap' entrys <v,mv> identify each vert v with 
	 * the associated vert mv in the full microGrid. 
	 * @param level int
	 * @return new PackData
	 */
	public PackData getNGrid(int level) {
		int N=gridN[level];
		int d=stepDiam[level];
		int ndct=3*N*N+3*N+1;
		int [][]m2v=new int[2*N+1][];
		for (int i=0;i<=2*N;i++)
			m2v[i]=new int[2*N+1];
		int [][]v2m=new int[ndct+1][];
		for (int kv=0;kv<=ndct;kv++)
			v2m[kv]=new int[2];
		PackData newPack=GridMethods.hexByHand(N,m2v,v2m);
		
// debug
//		System.out.println(" d*N = "+d*N+", while microN = "+microN);
		int shift=microN-d*N;
		newPack.vertexMap=new VertexMap();
		for (int i=0;i<=2*N;i++) 
			for (int j=0;j<=2*N;j++) {
				if (GridMethods.isLegalGridPt(i-N,j-N,N)) {
					try {
					int v_super=m2v[i][j]; // local vertex
					int v_micro=micro2v[d*i+shift][d*j+shift]; // vertex in microGrid
// System.out.println("vertmap: v "+v_super+" to vm "+v_micro);					
					newPack.vertexMap.add(new EdgeSimple(v_super,v_micro));
					newPack.kData[v_super].mark=v_micro;
					} catch(Exception ex) {
						System.out.println(" hum..? what error?");
					}
				}
			}
		
		// set radii/centers
		double rad=1.0/(CPBase.sqrt3*N);
		for (int v=1;v<=newPack.nodeCount;v++) {
			int v_macro=newPack.kData[v].mark;
			newPack.setRadius(v,rad);
			newPack.setCenter(v,new Complex(packData.getCenter(v_macro)));
		}
		
		return newPack;
	}

	/**
	 * TODO: want to allow either formula or data set via interpolation.
	 * Currently use 'intensityField'.
	 * @param z Complex
	 * @return non-negative double, intensity; 0 for outside intensity field
	 */
	public double getIntensity(Complex z) {
		
		double ans=0.0;
		int ix=-1;
		int jy=-1;
		
		try {
		// z is outside intensityFieldf
		if (z.x<iBox[0] || z.x>iBox[2] || z.y<iBox[1] || z.y>iBox[3])
			return 0.0;

		// find indices for grid box within the field
		ix=(int)Math.floor((z.x-iBox[0])/xinc);
		jy=(int)Math.floor((z.y-iBox[1])/yinc);
		
		// have problem if ix==xcols or jy=yrows, so fix
		if (ix==xcols) ix=xcols-1;
		if (jy==yrows) jy=yrows-1;
		
		// average the 4 corners
		ans=(intensityField[ix][jy]+intensityField[ix+1][jy]+
				intensityField[ix+1][jy]+intensityField[ix+1][jy+1])/4.0;
		} catch (Exception ex) {
			System.out.println(" problem setting intensityField.");
		}
		return ans;
	}
	
	/**
	 * Get the radius from 'stepRad' associated with intensity
	 * at the point z.
	 * @param z Complex
	 * @return double
	 */
	public double getRadius(Complex z) {
		double ity=getIntensity(z);
		if (ity>=stepIntensity[1])
			return stepRad[1];
		for (int s=2;s<levelCount;s++) {
			if (ity>=stepIntensity[s] && ity<stepIntensity[s-1]) {
				return stepRad[s];
			}
		}
		return stepRad[levelCount];
	}

	/**
	 * Based on microgrid: fill 'microIntensity' array with 
	 * intensities and find max/min.
	 *  mode 1: use intensityField; 
	 *  mode 2: use area density and
	 *  intensity brought back from surface.
	 *  @param md int, mode
	 */
	public void setMicroIntensity(int md) {
		maxIntensity=0.0;
		minIntensity=100000.0;
		microIntensity=new double[packData.nodeCount+1];
		for (int v=1;v<=packData.nodeCount;v++) {
//			System.out.println("v="+v);
			try {
			Complex z=packData.getCenter(v);
			double value=0.0;
			// mode 1, get from intensity data
			if (md==1)
				value=getIntensity(z);
			// mode 2, use reciprocal of local average radius
			else if (md==2) { 
				NodeLink nlist=qackData.cir_closest(z,false);
				if (CPBase.ClosedPath.contains(z.x,z.y)) { // leave outside at 0.0
					int qv=-1;
					if (nlist!=null && nlist.size()>0 && (qv=nlist.get(0))>0) {
						// find average radius of qv and nghbs
						value=qackData.getRadius(qv);
						int nbr=qackData.getNum(qv)+qackData.getBdryFlag(qv);
						for (int j=0;j<nbr;j++) 
							value += qackData.getRadius(qackData.kData[qv].flower[j]);
						value =(double)(nbr+1)/value;
					}
				}
			}	
			microIntensity[v]=value;
			maxIntensity=(value>maxIntensity) ? value:maxIntensity;
			minIntensity=(value>0.0 && value<minIntensity) ? value:minIntensity;
			
// debug
//			System.out.println("next v="+ v+" and value="+value+
//			    "; inside path? "+CPBase.ClosedPath.contains(z.x,z.y));
			
			} catch(Exception ex) {
				Oops("error in setMicroIntensity");
			}
		}
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		int count=0;
		int level=-1;
		Node node=null;
		boolean debug=false;
		
		// ============= smooth =========
		if (cmd.startsWith("smoo")) {
			
			// There must be a -q{n} flag
			int qnum=-1;
			for (int i=0;i<flagSegs.size();i++) {
				items=flagSegs.get(i);
				qnum=StringUtil.qFlagParse(items.get(0));
				if (qnum>=0) // got it
					flagSegs.remove(i);
			}
				
			// failed?
			if (qnum<0 || qnum>CPBase.NUM_PACKS) {
				errorMsg("usage: smoother must have -q{n}");
				return 0;
			}
				
			// target packing not there 
			smoothPack=CPBase.pack[qnum].getPackData();
			if (!smoothPack.status) {
//				errorMsg("usage: smoother -q{"+qnum+"}; packing "+qnum+" is empty");
				return 0;
			}
			
			System.out.println("dummy");
			
			// do we need to start the smoother?
			if (smoothPack.smoother==null) {
				smoothPack.smoother=new Smoother(smoothPack,this);
				count++;
			}

			if (flagSegs==null || flagSegs.size()==0)
				return count;
				
			// else, handle other flags in 'smoother' call
			StringBuilder strbld=new StringBuilder("smooth -p"+qnum+" ");
			strbld.append(StringUtil.reconstitute(flagSegs));
			return cpCommand(strbld.toString());
		}
		
		// ============= v_status ======
		else if (cmd.startsWith("v_stat")) {
			NodeLink vlink=null;
			try {
				items=flagSegs.get(0);
				vlink=new NodeLink(packData,items);
				if (vlink==null || vlink.size()==0) {
					Oops("No vertices speicified");
					return 0;
				}
				if (vlink.size()>10) {
					CirclePack.cpb.msg("'v_status' will only take 10 vertices");
				}
			} catch(Exception ex) {
				Oops("something wrong with 'v_status'");
				return 0;
			}

			int tick=10;
			Iterator<Integer> vlst=vlink.iterator();
			CirclePack.cpb.msg("Results of v_status call:\n");
			while (tick>0 && vlst.hasNext()) {
				tick--;
				int v=vlst.next();
				int []uw=v2micro[v];
				int u=uw[0];
				int w=uw[1];
				StringBuilder strbld=new StringBuilder("  Status for vert "+v+"= ("+u+","+w+")\n");
				for (int lev=1;lev<=levelCount;lev++) {
					Node nde=getNodeLUW(lev,u,w);
					if (nde!=null) {
						strbld.append("     Is node at level "+lev+
								": chosen? "+nde.chosen+"; mark="+nde.mark+"  : ");
					}
				}
				CirclePack.cpb.msg(strbld.toString());
				count++;
			}
			return count;
		}
		
		// ============== add_bearings =========
		if (cmd.startsWith("add_bear")) {
			int qnum=-1;
			double thresh=2.0;
			PackData qdata=null;
			try {
				items=flagSegs.get(0);
				qnum=StringUtil.qFlagParse(items.get(0));
				qdata=CPBase.pack[qnum].getPackData();
			} catch(Exception ex) {
				throw new ParserException("must specify '-q{n}' for pack number");
			}
			try {
				thresh=(double)(Double.parseDouble(items.get(1)));
			} catch (Exception ex) {}
			return addBearings(qdata,thresh);
		}
		
		// ============== clean delaunay (developing) ============
		else if (cmd.startsWith("clean")) {
			PackData qPack=CPBase.pack[1].getPackData();
			if (!qPack.status || qPack.nodeCount<=3) 
				Oops("write_dual: p1 does not seem to be ready");

			double basefactor=encircleRad/CPBase.sqrt3by2;
			double scalefactor=0.90; // default
			try {// read scale factor
				double sf=Double.parseDouble(flagSegs.get(0).get(0)); 
				scalefactor=sf;
			} catch(Exception ex) { // take default	
			}
			Path2D.Double tmpTrim=(Path2D.Double)trimPath.clone();
			tmpTrim.transform(AffineTransform.getRotateInstance(microAngle));
			tmpTrim.transform(AffineTransform.getScaleInstance(scalefactor*basefactor,
					scalefactor*basefactor));
			tmpTrim.transform(AffineTransform.getTranslateInstance(microCenter.x,
					microCenter.y));
			
			Path2D.Double holdPath=CPBase.ClosedPath;
			CPBase.ClosedPath=tmpTrim;
			
			// now, 'cookie' based on poison vertices;
	    	CookieMonster cM=null;
	    	try {
	    		cM=new CookieMonster(qPack,(Vector<Vector<String>>)null);
	    		int outcome=cM.goCookie();
		    	if (outcome<0) {
		    		throw new ParserException("cookie crumbled");
		    	}
		    	else if (outcome>0) {
		    		if (!debug) { // debug=true;
		    			qPack.cpScreen.swapPackData(cM.getPackData(),true);
		    			qPack=qPack.cpScreen.getPackData();
		    		}
		    	}
//		    	else {
		    		// debug
//					qPack.cpScreen.drawPath(tmpTrim);
//					PackControl.canvasRedrawer.paintMyCanvasses(qPack,false); 
//		    	}
	    	} catch(Exception ex) {
	    		CPBase.ClosedPath=holdPath;
	    		Oops("cookie failed in MicroGrid");
	    	}
	    	
    		CPBase.ClosedPath=holdPath;
	    	return 1;
		}

		// ============== write dual dcel structure from p1 ======
		else if (cmd.startsWith("write_dual")) {
			PackData qPack=CPBase.pack[1].getPackData();
			if (!qPack.status || qPack.nodeCount<=3) 
				Oops("write_dual: p1 does not seem to be ready");
			StringBuilder strbld=new StringBuilder();
			int frsl=CPFileManager.trailingFile(flagSegs,strbld);
			if (frsl==0)
				Oops("usage: write_dual <filename>");
			
			// create dcel
			qPack.packDCEL = CombDCEL.d_redChainBuilder(
					CombDCEL.getRawDCEL(qPack.getBouquet()),null,false,qPack.alpha);
			PackDCEL qdcel=qPack.packDCEL.createDual(false);
			BufferedWriter fp=null;
			File file=null;
			try {
				file=new File(strbld.toString());
				String dir=CPFileManager.PackingDirectory.toString();
				fp=CPFileManager.openWriteFP(new File(dir),
						false,file.getName(),false);
				if (fp==null)
					throw new InOutException();
			} catch (Exception ex) {
				throw new InOutException("Failed to open '"+file.toString()+
						"' for writing");
			}
			return qdcel.writeDCEL(fp);
		}
		
		// ============== debug options =================
		else if (cmd.startsWith("debug")) {
			
			String str=StringUtil.getOneString(flagSegs);
			if (str==null)
				str="f"; // default to faces

			if (str.startsWith("vinten")) { // vertices with positive intensity
				packData.vlist=new NodeLink(packData);
				for (int v=1;v<=packData.nodeCount;v++)
					if (microIntensity[v]>0.0) {
						packData.vlist.add(v);
						count++;
					}
				return count;
			}
			
			char c=str.charAt(0);
			switch(c) {
			case 'v':
			{
				packData.vlist=vlist;
				count++;
				break;
			}
			case 'f':
			{
				packData.flist=flist;
				count++;
				break;
			}
			}
			return count;
		}
		
		// ============== grid_length =====
		else if (cmd.startsWith("grid_l")) {
			PackData qpack=CPBase.pack[1].getPackData(); 
			if (!qpack.status || qpack.nodeCount<10 || CPBase.ClosedPath==null)
				Oops("The packing must be put in p1, must have a path");

			CirclePack.cpb.msg("Length of all dual edges touching region is "+
					String.format("%.5f",qpack.dualLength(new EdgeLink(qpack,"a"))));
			return 0;
		}
		
		// ============== stencil  
		else if (cmd.startsWith("stenc")) {
			// form -L{n} -[s/l/t] v
			int type=1; // 1=small, 2=large, 3=tangent pt stensil
			int colr=5; // 5=blue, 195=red, 218=green
			
			int L=lastProcessed;
			// no '-L{l}' flag? default to all
			if ((level=getLevelStr(flagSegs))>0) {
				L=level;
				flagSegs.remove(0); // next info should be in remaining flag segment
			}
			
			items=flagSegs.get(0);
			if (StringUtil.isFlag(items.get(0))) {
				char c=items.remove(0).charAt(1);
				switch(c) {
				case 'l': {type=2;colr=195;break;}
				case 't': {type=3;colr=218;break;}
				default: {type=1;colr=5;}
				}
			} 
				
			int v=NodeLink.grab_one_vert(packData,items.get(0));
			if (v>0) {
				int []uw=v2micro[v];
				EdgeLink mylink=null;
				switch(type) {
				case 2: {mylink=nixLarge[L];break;}
				case 3: {mylink=nixTangPt[L];break;}
				default: {mylink=nixSmall[L];}
				}
				Iterator<EdgeSimple> mlst=mylink.iterator();
				while (mlst.hasNext()) {
					EdgeSimple uwinc=mlst.next();
					int u=uw[0]+uwinc.v;
					int w=uw[1]+uwinc.w;
					if (GridMethods.isLegalGridPt(u,w,microN)) {
						int vv=micro2v[u+microN][w+microN];
						cpCommand("disp -t"+type+"fc"+colr+" "+vv);
						count++;
					}
				}
			}
			return count;
		}

		// ============== put_rad
		else if (cmd.startsWith("put")) {
			
			int low=1;
			int high=levelCount;
			
			// no '-L{l}' flag? default to all
			if ((level=getLevelStr(flagSegs))<0) {
				low=1;
				high=levelCount;
			}
			else {
				low=level;
				high=level;
			}
			
			for (int lev=low;lev<=high;lev++) {
				int D=gridN[lev]*2;
				double rad=(double)stepDiam[lev]*.5*microScaling;
	    		Node [][]nodeL=nodeLUW[lev];
	    		for (int i=0;i<=D;i++) {
	    			for (int j=0;j<=D;j++) {
	    				Node nde=nodeL[i][j];
	    				if (nde!=null && nde.chosen) {
	    					packData.setRadius(nde.myVert,rad);
	    					count++;
	    				}
	    			}
	    		}
			}
			return count;
		}
		
		// ============== save data for matlab processing =========
		else if (cmd.startsWith("export")) {

		    boolean script_flag=false;
		    boolean append_flag=false;
		    String dir=CPFileManager.PackingDirectory.toString();
		    
/* TODO: get C calls to 'triangle' running again! please!	
     	
	    	// data is held in 'dData'
			DelaunayData dData=new DelaunayData(0,Zvec);
			
			// try the new 'EDelaunay.dll' library (4/25/17)
			DelaunayBuilder dbuilder=new DelaunayBuilder();
			DelaunayData outDData=dbuilder.apply(dData);
			PackData newPacking=
				Triangulation.tri_to_Complex(outDData.getTriangulation(),0);

			File file=new File("Delaunay.p");
			BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
		    		append_flag,file.getName(),script_flag);
		    if (fp==null)
		    	throw new InOutException("Failed to open '"+file.toString()+
		    	"' for writing");
		    try {
		    	newPacking.writePack(fp,020017,append_flag);
		    	fp.flush();
		    	fp.close();
			    CirclePack.cpb.msg("Wrote 'Delaunay.p' "+
			    		  CPFileManager.PackingDirectory+
			    		  File.separator+file.getName());
			    return 1;
		    } catch(Exception ex) {
		    	Oops("failed writing Delaunay triangulation");
		    }
*/

			// TODO: when C code is ready, we will call 'DelaunayBuilder' here

			// ========== temporarily must write to files, use matlab ========
			// Matlab file is in repository: 'getDelaunay.m' and puts results in
			//   'hexTriangulation.m'.

			// 'vertexMap' will index translations, <parent, triangulation>
			packData.vertexMap=new VertexMap();

		    File file=new File("MicroExport.m");
		    BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
		    		append_flag,file.getName(),script_flag);
		    if (fp==null)
		    	throw new InOutException("Failed to open '"+
		    			file.toString()+"' for writing");
		    int i=0;
		    int j=0;
		    int D=0;
	    	int tick=1;
	    	Node nde=null;
	    	int lev=1;
		    try {
		    	fp.write("V_R_X_Y=[\n");
		    	for (lev=1;lev<=levelCount;lev++) {
		    		D=gridN[lev]*2;
		    		Node [][]nodeL=nodeLUW[lev];
		    		for (i=0;i<=D;i++) {
		    			for (j=0;j<=D;j++) {
		    				nde=nodeL[i][j];
		    				if (nde!=null && nde.mark==lev) {
		    					Complex z=packData.getCenter(nde.myVert);
		    					double rad=0.5*microScaling*stepDiam[lev];
		    					fp.write(" "+tick+"   "+rad+"   "+z.x+" "+z.y+"\n");
		    					packData.vertexMap.add(
		    							new EdgeSimple(nde.myVert,++tick));
		    				}
		    			}
		    		}
		    	}
		    	fp.write("];\n");
		    	fp.flush();
		    	fp.close();
		    } catch(Exception ex) {
		    	try {
		    		fp.close();
		    	} catch(Exception exc) {}
		    	Oops("failed writing Hex Centers");
		    }
		    CirclePack.cpb.msg("Wrote "+
		    		CPFileManager.PackingDirectory+File.separator+file.getName()+
		    		"\n  User can run 'PtsPacking.m' in Matlab, which generates "+
		    		"'MicroPacking.p'.");
		    return 1;
		}
		
		// =============== process ============
		// Default: reset, then process levels.
		// If level is specified, then process up to that level
		else if (cmd.startsWith("process")) {
			int step=9; // defualt to all steps
			
			// up to specified level '-L{n}'? Must be first flag
			int mylevel=getLevelStr(flagSegs);
			if (mylevel>0) { // yes, up to specified level
				flagSegs.remove(0); // shuck this

				// in this case only, check for "step" flag -s{n}, return
				//    after this number of steps in 'processLevel' code
				if (flagSegs!=null && flagSegs.size()>0) {
					items=flagSegs.get(0);
					try {
						String str=items.get(0);
						if (StringUtil.isFlag(str) && str.charAt(1)=='s') {
							if (str.length()==3) {
								step=Integer.parseInt(str.substring(2, 3));
							}
							else {
								str=items.get(1); // there's a space before the {n}
								step=Integer.parseInt(str.substring(0,1));
							}
						}
					} catch(Exception ex) {}
				}
				count+= processControl(mylevel,step);
				CirclePack.cpb.msg("'lastProcessed' is "+lastProcessed);
				return count;
			}
			
			// else do all levels, all steps.
			CirclePack.cpb.msg("'lastProcessed' is "+lastProcessed);
			count+=processControl(levelCount,9); // all levels, all steps.
			return count;
		}
		
		// ================== set nodelist
		else if (cmd.startsWith("set_nlist")) {

			if ((level=getLevelStr(flagSegs))<0)
				Oops("usage: set_nlist -L{n} ... for level n");
			
			try {
				items=flagSegs.get(0);
			} catch (Exception ex) {
				items.add("a"); // default to all
			}
			
			Vector<Node> newNodes=new Vector<Node>(0);
			
			Iterator<String> its=items.iterator();
			while (its.hasNext()) {
				String nspec=its.next();

				char c=nspec.charAt(0);
				nspec=nspec.substring(1);
				switch(c) {
				case 'a': { // all
					int N=gridN[level];
					for (int i=0;i<=2*N;i++) {
						for (int j=0;j<=2*N;j++) {
							if ((node=nodeLUW[level][i][j])!=null)
								newNodes.add(node);
							count++;
						}
					}
					break;
				}	
				case 'm': { // marked
					int N=gridN[level];
					for (int i=0;i<=2*N;i++) {
						for (int j=0;j<=2*N;j++) {
							if ((node=nodeLUW[level][i][j])!=null && node.mark!=0) {
								newNodes.add(node);
								count++;
							}
						}
					}
					break;
				}
				case 'n': { // include current nodes?
					if (nspec.startsWith("list")) {
						Vector<Node> currNodes=myLists.get(level);
						Iterator<Node> cit=currNodes.iterator();
						while (cit.hasNext()) { 
							node=cit.next();
							if (node!=null) {
								newNodes.add(cit.next());
								count++;
							}
						}
					}
					break;
				}
				case 'h':  // hex nghbs of (u,w) in superlattice
				{
					int u=0;
					int w=0;
					
					try {
						u=Integer.parseInt(its.next());
						w=Integer.parseInt(its.next());
					} catch(Exception ex) {
						break;
					}
					
					Vector<Node> vecN= getHexRing(level,u,w);
					if (vecN!=null && vecN.size()>0) {
						Iterator<Node> cit=vecN.iterator();
						while (cit.hasNext()) {
							node=cit.next();
							if (node!=null) {
								newNodes.add(node);
								count++;
							}
						}
					}
					break;
				}
				} // end of switch
			} // end of while through specifications
			myLists.setElementAt(newNodes, level);
			return ++count;
		}
		
		else if (cmd.startsWith("status")) {
			StringBuilder strbld=new StringBuilder("MicroGrid p"+packData.packNum+": ");
			if (platenP.chgTrigger)
				strbld.append(" Reset is needed. ");
			else 
				strbld.append(" No reset needed. ");
			if (mode==1)
				strbld.append(" Intensity set by function. ");
			else if (mode==2)
				strbld.append(" Intensity set by data. ");
			CirclePack.cpb.msg(strbld.toString());

			strbld=new StringBuilder("");
			strbld.append("Genes: "+microN+"; 'minR' = "+
					String.format("%.4f",platenP.minR.doubleValue())+
					"; 'maxR' = "+String.format("%.4f",platenP.maxR.doubleValue())+
					"; 'Q' = "+String.format("%.4f",platenP.ratioQ.doubleValue())+
					"; 'angle' = "+String.format("%.4f",microAngle)+"\n");
			CirclePack.cpb.msg(strbld.toString());
			
			strbld=new StringBuilder("");
			strbld.append("'levelCount' = "+levelCount+";");
			strbld.append("; stepDiam = [");
			for (int k=1;k<=levelCount;k++) {
				strbld.append(" "+stepDiam[k]+",  ");
			}
			strbld.append("]");
			CirclePack.cpb.msg(strbld.toString());
			
			strbld=new StringBuilder("");
			strbld.append("'stepIntensity' = [");
			for (int k=1;k<=levelCount;k++) {
				strbld.append(" "+String.format("%.4f",stepIntensity[k])+",  ");
			}
			strbld.append("]");
			CirclePack.cpb.msg(strbld.toString());
			count=1;
		}
		
		else if (cmd.startsWith("reset")) {
			return reset(0); // default mode=0
		}
		
		else if (cmd.startsWith("set_mark")) {
			// mark nodes that meet the intensity for their level
			for (int s=1;s<=levelCount;s++) {
				int N=gridN[s];
				for (int i=0;i<=2*N;i++) {
					for (int j=0;j<=2*N;j++) {
						
						// debug
//						System.err.println(" i, j = "+i+" "+j);
						
						if ((node=nodeLUW[s][i][j])!=null) {
							
							// debug
//							System.err.println("myVert = "+node.myVert);
							
							double mi=microIntensity[node.myVert];
							if (s>1) {
								if (mi>=stepIntensity[s] && mi<stepIntensity[s-1]) {
									node.mark=1;
									count++;
								}
							}
							else if (mi>=stepIntensity[s]) {
								node.mark=1;
								count++;
							}
						}
					}
				}
			}
		}

		else if (cmd.startsWith("set_")) {
			String scmd=cmd.substring(4);
			try {
				items=flagSegs.get(0);
				if (scmd.startsWith("max_g")) {
					max_gen=Integer.parseInt(items.get(0));
					count++;
				}
				if (scmd.startsWith("mM")) {
					platenP.set_minR(encircleRad,Double.parseDouble(items.get(0)));
					platenP.set_maxR(Double.parseDouble(items.get(1)));
					count++;
				}
				else if (scmd.startsWith("minR")) {
					platenP.set_minR(encircleRad,Double.parseDouble(items.get(0)));
					count++;
				}
				else if (scmd.startsWith("maxR")) {
					platenP.set_maxR(Double.parseDouble(items.get(0)));
					count++;
				}
				else if (scmd.startsWith("Q")) {
					platenP.set_Q(Double.parseDouble(items.get(0)));
					count++;
				}
				else if (scmd.startsWith("angle")) { // angle should be radians/pi.
					double angle=Double.parseDouble(items.get(0));
					microAngle=angle*Math.PI;
					count++;
				}
				else if (scmd.startsWith("center")) {
					Complex cent=
							new Complex(Double.parseDouble(items.get(0)),
									Double.parseDouble(items.get(1)));
					microCenter=cent;
				}
			} catch(Exception ex) {
				Oops("error with '"+cmd+"'");
			}
			
		}
		
		// ============= grid points =====================
		else if (cmd.startsWith("grid_p")) {
			packData.zlist=new PointLink();
			for (int i=0;i<xcols;i++) {
				for (int j=0;j<yrows;j++) {
					packData.zlist.add(new Complex(gridPoints[i][j]));
				}
			}
			cpCommand("disp -t0 z zlist");
			count++;
		}
			
		else if (cmd.startsWith("Disp")) {
			if (lastProcessed==0)
				Oops("can't display, no levels are processed");
			String flgBase=StringUtil.reconstitute(flagSegs);

			int rslt=1;
			for (int l=1;(l<=Math.abs(lastProcessed) && rslt>0);l++) {
				Vector<Vector<String>> flgRe=
						StringUtil.flagSeg("-L"+l+" "+flgBase);
				rslt=cmdParser("disp",flgRe);
				if (rslt>0)
					count++;
			}
			return count;
		}
		
		else if (cmd.startsWith("disp")) {
			DispFlags dispflgs=new DispFlags("");
			if ((level=getLevelStr(flagSegs))<0)
				Oops("usage: disp -L{n} ... for level n");
			
			// expect more flagged specifications: e.g. -c 
			flagSegs.remove(0); // get rid of '-L{n}' level spec
			if (flagSegs.size()==0)
				Oops("usage: disp -L{n} [specs] (e.g. -c ) {...}");
			Iterator<Vector<String>> fit=flagSegs.iterator();
			while (fit.hasNext()) {
				items=fit.next();
				
				String flag=items.remove(0);
				if (!StringUtil.isFlag(flag))
					Oops("usage example: disp -L{n} -c a");
				char c=flag.charAt(1);
				flag=flag.substring(2);

				switch(c) {
				case 'c': // circles
				{
					// there may be some of usual display flags
					if (flag.length()>0) {
						dispflgs=new DispFlags(flag);
					}
					
					// empty spec means do all
					String spec=null;
					try{
						spec=items.get(0);
					} catch(Exception ex) {
						items.add("a");
					}
					
					Vector<Node> selects=new Vector<Node>(0);

					// selection processing, iterate through strings
					Iterator<String> its=items.iterator();
					while (its.hasNext()) {
						spec=its.next();
						
						if (spec.equalsIgnoreCase("nlist")) {
							Vector<Node> nlist=null;
							if (spec.startsWith("nlist") && 
									myLists!=null && 
									(nlist=myLists.get(level))!=null && nlist.size()>0) {
								Iterator<Node> nit=nlist.iterator();
								while (nit.hasNext()) {
									selects.add(nit.next());
									count++;
								}
							}
						}
						
						// else go through and check each node against criterion
						else {
						char d=spec.charAt(0);

						int N=gridN[level];
						for (int i=0;i<=2*N;i++) {
							for (int j=0;j<=2*N;j++) {
								if ((node=nodeLUW[level][i][j])!=null) {
									switch(d) {
									case 'a': // all circles at this level
									{
										selects.add(node);
										break;
									}
									case 'm':
									{
										if (node.mark!=0)
											selects.add(node);
										break;
									}
									} // end of inner switch
								}
							} // end of inside stuff
						} // end of for loop
						} 
					} // end of while for selection
					
					// display them
					Iterator<Node> sit=selects.iterator();
					while (sit.hasNext()) {
						node=sit.next();
						Complex z=packData.getCenter(node.myVert);
						double rad=0.5*microScaling*stepDiam[level];
						dispflgs.setColor(node.color);
						cpScreen.drawCircle(z,rad,dispflgs);
						count++;
					}

					if (count>0)
						PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
					break;
				} // end of 'circles' case
				case 't':
				{
					if (triangles==null || triangles.size()==0) {
						return count;
					}

					// there may be some of usual display flags
					if (flag.length()>0) {
						dispflgs=new DispFlags(flag);
					}

					Iterator<Face> tir=triangles.iterator();
					while (tir.hasNext()) {
						Face f=tir.next();
						if (f.mark==level) {
							double []corners=new double[6];
							for (int kk=0;kk<3;kk++) {
								Complex z=packData.getCenter(f.vert[kk]);
								corners[2*kk]=z.x;
								corners[2*kk+1]=z.y;
							}
							cpScreen.drawClosedPoly(3,corners,dispflgs);
							count++;
						}
					}

					if (count>0)
						PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
					break;
				}
				default: // error if we got here
				{
					Oops("error is 'disp' specifications");
				}
				} // end of switch on objects to display
			} // end of while through 'disp' flags segments
			if (count==0)
				return 1;
			return count;
		} // end of 'disp' 
		
		// color faces by average intensity of vertices
		else if (cmd.startsWith("intensity")) {
			packData.color_face_interp(microIntensity);
			PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
			count++;
		}
				
		return count;
		
	} // end of 'cmdParser'
	
	/**
	 * Make selections up to desired level and step. (Currently, each level 
	 * involves 5 steps. If all are completed at level L, then 'lastProcessed'
	 * is set to L. If something fails in processing or if the S<5 (partial
	 * processing), then 'lastProcessed' is set to -level.)
	 * 
	 * On entering, if 'lastProcessed' is non-positive, then do a reset and
	 * start processing from the first level. Else, if L>lastProcessed, then
	 * continue through level L and step S. 
	 * 
	 * @param L int, complete to this level
	 * @param S int, complete to this step
	 * @return int, 0 if nothing done, negative on error, else level reached
	*/
	public int processControl(int L,int S) {
		int count=0;
		
		if (lastProcessed<0) {
			reset(0); // default mode=0
			CirclePack.cpb.msg("MultiGrid was reset\n");
		}
		if (L>levelCount)
			L=levelCount;
		if (L<=lastProcessed) {
			CirclePack.cpb.msg("Process?" +
					"Already processed to level "+lastProcessed+", so we have reset");
			reset(0); // default mode=0
		}
		
		// do from last up to and including L (up to step)
		for (int n=lastProcessed+1;n<=L;n++) {
			if (S>=5 || n<L) {
				int ans=processLevel(n,9); // full processing at level n
				if (ans<=0) { // It may be okay that no grid points for this level
					CirclePack.cpb.errMsg("Processing got nothing at level "+n);
//					return count;
				}
				lastProcessed=n;
				count+=ans;
			}
		}
		if (L>lastProcessed) { // most have specified S<=5
			int ans=processLevel(L,S);
			lastProcessed=-L;
			count+=ans;
			if (ans<=0)  
				CirclePack.cpb.errMsg("Processing got nothing at level "+L+", up to step "+S);
		}
		
		// report 'numChosen' results?
		StringBuilder strbldr=new StringBuilder("Numbers chosen: ");
		for (int j=1;j<=levelCount;j++) 
			strbldr.append("L"+j+": "+numChosen[j]+";  ");
		CirclePack.cpb.msg(strbldr.toString());
		
		return count;
		
	}


	/**
	 * This is the heart of the current algorithm, although it is 
	 * still under development. 
	 * 
	 * Currently each level is a 5-stage process, with circles
	 * possibly chosen at stages 1, 3, and 4; other steps help 
	 * keep track of chosen and exclusions in 'processed'.
	 * 
	 * (1) We choose vertices at this level, based on 'microIntensity' 
	 * and 'processed' exclusion info. Thus, if qualified by intensity 
	 * and not chosen or excluded at this or lesser level, then choose and 
	 * record nixLarge[level] stencil exclusions as -(level+1) in 'processed'.
	 * 
	 * (2) We want a barrier around the components of 'level' verts
	 * identified in Step 1. The idea? To prevent 'level' verts 
	 * from getting nghbs at any level higher than 'level+1'; 
	 * to allow addition of 'level' verts filling some of the 
	 * gap formed by the new 'level+1's; and yet to prevent 
	 * 'level' vert choices from spilling across the barrier 
	 * into regions that should have larger circles. In this 
	 * step we compile a vector of coords (U,V) for the 
	 * 'level+1' superlattice to surround components of 'level'. 
	 * Not easy: we create a new packing (put in p2 for debug 
	 * purposes) representing the 'level+1' superlattice and 
	 * with vertexMap back to the microGrid. We mark faces 
	 * having a microGrid vert which is chosen or excluded at 
	 * -(level+1), then create the 'barrier' vector of 'level+1' 
	 * verts having at least one but not all its faces marked. 
	 * 
	 * (3) Now we construct the barrier itself, which consists
	 * of chosen verts and verts excluded at '-level'. Passing
	 * through 'barrier', our (U,V) vector from Step 2, we set these 
	 * as 'chosen' at 'level+1' and for them and any 'ghost' nghbs we 
	 * exclude '-level' neighborhood using their 'nixSmall[level+1]'.
	 * Next we use 'nixTangPt[level]' to also exclude '-level'
	 * verts around tangency points of between chosen 'level+1's 
	 * and/or ghosts. This is needed because otherwise, due to 
	 * allowed overlaps, there can be leakage of 'level' verts 
	 * across near these tangency points. 
	 *  
	 * (4) Look around boundary of current level to see 
	 * if we should add more from the current level --- these didn't 
	 * qualify by intensity, but if they do not overlap those added
	 * in Step 3, choose
	 * them to help avoid big gaps.
	 * 
	 * (5) Finally, go back to all level+1 chosen in Step 2 and use 
	 * 'nixSmall[level+1]' stencil to exclude at level+1.
	 * 
	 * @param level int
	 * @param step int, if 1 or 2, then stop after that step
	 * @return int count
	 */
	public int processLevel(int level,int step) {
		int count=0;
		Node node=null;
		vlist=new NodeLink(packData); // debug tool for various uses
		flist=new FaceLink(packData);
		int N=gridN[level];
		boolean debug=false;

		// Some initiation at first level, smallest radii (highest intensity)
		if (level==1) { 
			for (int v=1;v<=packData.nodeCount;v++) {
				packData.kData[v].mark=0;
			}
			for (int f=1;f<=packData.faceCount;f++) {
				packData.faces[f].mark=0;
			}
			processed=new int[packData.nodeCount+1];
			numChosen=new int[levelCount+1];
		}
		
		// Step 1: =========================================
		//   Add circles at this 'level' based on intensity, 
		//   avoiding ones excluded from with '-level'
		if (debug)
			System.out.println("\nLevel \"+level+\", Step 1: add by intensity, avoid excluded");
		
		// only look a vertices in 'level' superlattice		
		for (int i=0;i<=2*N;i++) {
			for (int j=0;j<=2*N;j++) {

				if ((node=nodeLUW[level][i][j])!=null) {
					int v=node.myVert; 
						
					// choose if untouched or excluded only for larger circles
					if (microIntensity[v]>=stepIntensity[level] && 
							(processed[v]==0 || processed[v]<-level)) { 
						
						// store results
						node.chosen=true;
						node.mark=level; // set 'mark' for node
						if (debug)
//							System.out.println(" lev "+level+"     "+v);
						processed[v]=level; // this vert is a center at this level
						packData.kData[v].mark=level; 
						packData.setCircleColor(v,ColorUtil.cloneMe(node.color));
						count++;
						numChosen[level]++;

						// mark exclusions at '-(level+1)'
						if (level<levelCount) {
							Iterator<EdgeSimple> lel=nixLarge[level].iterator();
							while(lel.hasNext()) {
								EdgeSimple uw=lel.next();
								int u=node.u+uw.v;
								int w=node.w+uw.w;
								if (GridMethods.isLegalGridPt(u,w,microN)) {
									int cvert=micro2v[u+microN][w+microN];
									// not yet marked? exclude at level+1 size
									if (processed[cvert]==0 || processed[cvert]>=-level) {
										processed[cvert]=-(level+1);
										packData.kData[cvert].mark=-(level+1);
// System.out.println(" cvert "+cvert+" set to "+-(level+1));										
									}
								}
							}
						}
					}
				}
			}
		} // done iterating through 'level' superlattice

// debug
//if (count>0) {		
//int pcum=0;
//for (int v=1;v<=packData.nodeCount;v++) 
//	if (processed[v]!=0)
//		pcum++;
//System.out.println("Count of 'processed[.]!=0' after level "+level+", step 1: "+pcum);
//}

		// are we stopping after Step 1? 
		int tally=0;
		for (int el=1;el<=level;el++) 
			tally += numChosen[el];
		if (tally==0 || step==1 || level==levelCount) {
			if (step==1)
				lastProcessed=-level;
			return count;
		}
		
		// Step 2: ==========================================
		//   Now we create 'barrier' vector of coords (U,W) in 'level+1' 
		//   superlattice of verts surrounding components of vertices hit
		//   or excluded in Step 1 or in earlier processing levels.
		if (debug)
			System.out.println("\nLevel "+level+", Step 2 ; surround by (level+1)="+(level+1));
		
		// create packing representing 'level+1' superlattice
		PackData gridPack=getNGrid(level+1);
		gridPack.fileName=new String("gridPack_L_"+(level+1));
		if (debug) { // put this packing in p2
			gridPack.set_aim_current(true);
			gridPack.fillcurves();
			CommandStrParser.jexecute(gridPack,"layout");
			CommandStrParser.jexecute(CPBase.pack[2].getPackData(),"disp -w -c -e b");
			gridPack.facedraworder(false);
			CPBase.pack[2].swapPackData(gridPack,false);
		}
		for (int v=1;v<=gridPack.nodeCount;v++) // need to use 'utilFlag's
			gridPack.kData[v].utilFlag=0;
		
		// Mark faces f of 'gridPack' that contain a processed microGrid vert.
		boolean gotahit=false;
		int []abc=new int[3];
		for (int f=1;f<=gridPack.faceCount;f++) {
			int []verts=gridPack.faces[f].vert;
// debug
//			System.out.println(" f "+f);
			
			boolean fhit=false;
			for (int j=0;(j<3 && !fhit);j++) {
				int k=verts[j];
				abc[j]=gridPack.kData[k].mark;  // the microGrid vert
				if (processed[abc[j]]!=0) {
					fhit=true;
					if (processed[abc[j]]==-(level+1))
						gridPack.kData[k].utilFlag=-(level+1); // mark for later 
				}
			}
			// if no face corners were processed, check the other microGrid
			// vertices in the interior or boundary
			if (!fhit) {
				EdgeLink elink=GridMethods.equil2tri(abc[0],abc[1],abc[2],
						v2micro,stepDiam[level+1]);
				Iterator<EdgeSimple> elst=elink.iterator();
				while(!fhit && elst.hasNext()) {
					EdgeSimple edge=elst.next();
					int mv=micro2v[edge.v+microN][edge.w+microN];
					if (processed[mv]!=0) 
						fhit=true;
				}
			}
			
			// Yes, mark this face in gridPack; we also set the vert 'utilFlag',
			//   either to '-(level+1)' if the corresponding microGrid vert was 
			//   excluded at '-(level+1)' (see above) or else to 'level+1' to
			//   bring it to attention (see below).
			if (fhit) {
				gridPack.faces[f].mark=level+1;
				for (int j=0;j<3;j++) {
					int k=gridPack.faces[f].vert[j];
					if (gridPack.kData[k].utilFlag!=-(level+1))
						gridPack.kData[k].utilFlag=(level+1);
				}
				gotahit=true;
			}
			else
				gridPack.faces[f].mark=0;
		}
		
		// there should be hits unless we're done with all the processing
		if (!gotahit) {
			msg("Didn't get any face hits in Step 3 of level "+level);
			return count;
		}
		
		// list verts of gridPack which have one but not all contiguous
		//   faces marked; note that 'utilFlag==-(level+1)' means this
		//   vert was excluded, so it is not put in 'vhits'.
		NodeLink vhits=new NodeLink(gridPack);
		for (int v=1;v<=gridPack.nodeCount;v++) { 
			if (gridPack.kData[v].utilFlag==(level+1)) { // some nghb face was hit
				int num=gridPack.getNum(v);
				boolean notAll=false;
				for (int j=0;(j<num && !notAll);j++) {
					int g=gridPack.kData[v].faceFlower[j];
					if (gridPack.faces[g].mark==0)
						notAll=true;
				}
				if (notAll) // but not all faces, so include v
					vhits.add(v);
			}
		}
		
		// complete 'barrier' list of surrounding 'level+1' microGrid verts
		Vector<EdgeSimple> barrier=new Vector<EdgeSimple>(0);
		Iterator<Integer> vits=vhits.iterator();
		while(vits.hasNext()) {
			int vgP=vits.next(); // vert in superlattice
			gridPack.setCircleColor(vgP,ColorUtil.spreadColor(1)); // color
			int vmg=gridPack.vertexMap.findW(vgP); // vert in microGrid
			barrier.add(new EdgeSimple(v2micro[vmg][0],v2micro[vmg][1]));
		}
		
// debug
//		System.out.println("barrier count: "+barrier.size());
		
/*		
		packData.vlist=new NodeLink();
		gridPack.vlist=new NodeLink();
		Iterator<EdgeSimple> tmp_uws=barrier.iterator();
		while (tmp_uws.hasNext()) {
			EdgeSimple ege=tmp_uws.next();
			int tmp_v=micro2v[ege.v+microN][ege.w+microN];
			System.out.println("to packData vlist "+tmp_v);
			packData.vlist.add(tmp_v);
			
			EdgeSimple es=GridMethods.uw2UW(ege.v, ege.w, microN, gridN[level+1], stepDiam[level+1]);
			Node nd=nodeLUW[level+1][es.v+gridN[level+1]][es.w+gridN[level+1]];
			int sv=gridPack.vertexMap.findV(nd.myVert);
			gridPack.vlist.add(sv);
			System.out.println("to gridPack vlist "+sv);
		}
*/
		
		gridPack=null; // packing may still reside in p2 for debugging
		
		// are we stopping after Step 2? step=2;
		if (step==2) {
			lastProcessed=-level;
			return count;
		}
		
		// Step 3: =========================================
		//    Here we use the vector 'barrier' to exclude various
		//    verts. As we go through the list, we augment 'barrier' with 
		//    any 'level+1' superlattice ghost neighbors, we exclude with
		//    '-level' using 'nixLarge[level]', and we exclude with '-level'
		//    those too near tangency points between the 'barrier' verts
		//    using 'nixTangPt'.
		if (debug)
			System.out.println("\nLevel "+level+", Step 3; choose/exclude using 'barrier'.");
		
		// First, we choose all 'barrier' verts at 'level+1'
		Iterator<EdgeSimple> uws=barrier.iterator();
		while (uws.hasNext()) {
			EdgeSimple edge=uws.next();
			edge=GridMethods.uw2UW(edge.v,edge.w,microN,gridN[level+1],stepDiam[level+1]);
			if (edge==null) 
				throw new CombException("pt not in superlattice");
			Node nde=nodeLUW[level+1][edge.v+gridN[level+1]][edge.w+gridN[level+1]];
			nde.chosen=true;
			nde.mark=level+1;
			int v=nde.myVert;
			if (processed[v]!=0)
				throw new CombException(" error setting vert "+v+" to chosen for level "+(level+1));
			processed[v]=level+1;
			packData.kData[v].mark=level+1;
			count++;
			numChosen[level+1]++;
		}
		
		// now pass through again
		uws=barrier.iterator();
		while (uws.hasNext()) {
			EdgeSimple edge=uws.next(); // signed coords (u,w) in microGrid
			edge=GridMethods.uw2UW(edge.v,edge.w,microN,gridN[level+1],stepDiam[level+1]);
			// signed coords (U,W) in superlattice
			int U=edge.v;
			int W=edge.w;
			Node nde=nodeLUW[level+1][U+gridN[level+1]][W+gridN[level+1]];
			int v=nde.myVert;
// debug
//			System.out.println("barrier vert "+v);
			
			int []uw=v2micro[v];
			/*
			System.out.println("barrier: "+v+":\n 'node' says (u,v) = ("+
					nde.u+","+nde.w+"), started with (edge.v,edge.w) = ("+
					edge.v+","+edge.w+"), and v2micro gives ("+uw[0]+","+uw[1]+")");
			*/
			
			// First, we check for any ghosts and handle related exclusions
			EdgeLink g_edges=GridMethods.getGhosts(uw[0],uw[1],microN,
					gridN[level+1],stepDiam[level+1]);
			Iterator<EdgeSimple> g_uws=null;
			if (g_edges!=null)
				g_uws=g_edges.iterator();
			while(g_uws!=null && g_uws.hasNext()) {
				EdgeSimple euws=g_uws.next();
				
				// microGrid coords of a ghost
				int gu=euws.v;
				int gw=euws.w;
				
				// exclude nearby 
				Iterator<EdgeSimple> sel=nixLarge[level].iterator();
				while(sel.hasNext()) {
					EdgeSimple uwe=sel.next();
					int u=gu+uwe.v;
					int w=gw+uwe.w;
					if (GridMethods.isLegalGridPt(u,w,microN)) {
						int cvert=micro2v[u+microN][w+microN];
						// exclude if untouched
						if (processed[cvert]==0 || processed[cvert]==-level+1) {
							processed[cvert]=-level; // exclude current size
							packData.kData[cvert].mark=-level;
						}
					}
				}
			
				// exclude near tangencies with chosen 'level+1' superlattice verts
				EdgeLink myStar=GridMethods.hexStar(stepDiam[level+1]);
				Iterator<EdgeSimple> mst=myStar.iterator();
				while (mst.hasNext()) {
					EdgeSimple se=mst.next();
					int nu=gu+se.v;
					int nw=gw+se.w;
					if (GridMethods.isLegalGridPt(nu,nw,microN)) { // should be 'level+1' node in microGrid
						int vv=micro2v[nu+microN][nw+microN];
						if (processed[vv]==(level+1)) { // yes, a chosen nghb
							// tangency point coords
							int tvu=gu+(int)(se.v/2);
							int tvw=gw+(int)(se.w/2);
							
							Iterator<EdgeSimple> edsim=nixTangPt[level+1].iterator();
							while(edsim.hasNext()) {
								EdgeSimple uwe=edsim.next();
								int u=tvu+uwe.v;
								int w=tvw+uwe.w;
								if (GridMethods.isLegalGridPt(u,w,microN)) {
									int cvert=micro2v[u+microN][w+microN];
									// exclude at level if untouched or excluded 
									if (processed[cvert]<=0) {
										processed[cvert]=-level; // exclude current size
										packData.kData[cvert].mark=-level;
									}
								}
							}
						}
					}
				}
			}
			
			// now handle this vert itself; first exclude nghbs at '-level'
			Iterator<EdgeSimple> sel=nixLarge[level].iterator();
			while(sel.hasNext()) {
				EdgeSimple uwe=sel.next();
				int u=uw[0]+uwe.v;
				int w=uw[1]+uwe.w;
				if (GridMethods.isLegalGridPt(u,w,microN)) {
					int cvert=micro2v[u+microN][w+microN];
					// exclude if untouched
					if (processed[cvert]==0 || processed[cvert]==-(level+1)) {
						processed[cvert]=-level; // exclude current size
						packData.kData[cvert].mark=-level;
					}
				}
			}
			// exclude verts near tangencies with any chosen 'level+1' nghbs
			EdgeLink myStar=GridMethods.hexStar(stepDiam[level+1]);
			Iterator<EdgeSimple> mst=myStar.iterator();
			while (mst.hasNext()) {
				EdgeSimple se=mst.next();
				int nu=uw[0]+se.v;
				int nw=uw[1]+se.w;
				if (GridMethods.isLegalGridPt(nu,nw,microN)) { // should be 'level+1' node in microGrid
					int vv=micro2v[nu+microN][nw+microN];
					if (processed[vv]==(level+1)) { // yes, a chosen nghb
						// tangency point coords
						int tvu=uw[0]+(int)(se.v/2);
						int tvw=uw[1]+(int)(se.w/2);
						
						Iterator<EdgeSimple> edsim=nixTangPt[level+1].iterator();
						while(edsim.hasNext()) {
							EdgeSimple uwe=edsim.next();
							int u=tvu+uwe.v;
							int w=tvw+uwe.w;
							if (GridMethods.isLegalGridPt(u,w,microN)) {
								int cvert=micro2v[u+microN][w+microN];
								// exclude at level if untouched or excluded 
								if (processed[cvert]<=0) {
									processed[cvert]=-level; // exclude current size
									packData.kData[cvert].mark=-level;
								}
							}
						}
					}
				}
			}
		} // done with Step 3

		// are we stopping after Step 3? step=3;
		if (step==3) {
			lastProcessed=-level;
			return count;
		}

		// Step 4: =============================================
		//    Look at level nodes next to chosen level nodes and
		//    not excluded in Step 3 and choose them, irrespective of 
		//    intensity to help close gap formed by Step 1, 2, and 3. 
		//    TODO: Want to take more than one pass, but have that on hold.
		Vector<Node> currNode=new Vector<Node>();
		Vector<Node> nextNode=new Vector<Node>();
		
		// Find first list of qualifying nodes at this level:
		//   are unchosen or excluded at higher level and have 
		//   a chosen nghb at this level.
		if (debug)
			System.out.println("\nLevel "+level+", Step 4; this level, not ex'ed in Step 3");
		for (int i=0;i<=2*N;i++) {
			for (int j=0;j<=2*N;j++) {
				if ((node=nodeLUW[level][i][j])!=null) {

					// survey chosen nodes at this level and build list of
					//    nghbs at this level who are not chosen, not excluded
					int v=node.myVert;
					if (processed[v]==level) {
						int []vuw=v2micro[v];
						Vector<Node> hexNghbs=getHexRing(level,vuw[0],vuw[1]);
						Iterator<Node> hlst=hexNghbs.iterator();
						while (hlst.hasNext()) {
							Node nde=hlst.next();
							int mv=nde.myVert;
							if (processed[mv]==0) // not chosen or excluded 
								nextNode.add(nde);
						}
					}
				}
			}
		}

		while (nextNode.size()>0) {
			currNode=nextNode;
			nextNode=new Vector<Node>();
			Iterator<Node> clst=currNode.iterator();
			while (clst.hasNext()) {
				Node nextnde=clst.next();
				int v=nextnde.myVert;
				
				// should we choose this node (may have already)
				if (processed[v]==0) {
					nextnde.chosen=true;
					nextnde.mark=level; // set 'mark' for node
					if (debug)
//						System.out.println("    lev "+level+"    "+v);					
					processed[v]=level; // this vert is a center at this level
					packData.kData[v].mark=level;
					packData.setCircleColor(v,ColorUtil.cloneMe(nextnde.color));
					count++;
					numChosen[level]++;

					// exclude nodes at this level
					Iterator<EdgeSimple> sel=nixSmall[level].iterator();
					while(sel.hasNext()) {
						EdgeSimple uwe=sel.next();
						int u=nextnde.u+uwe.v;
						int w=nextnde.w+uwe.w;
						if (GridMethods.isLegalGridPt(u,w,microN)) {
							int cvert=micro2v[u+microN][w+microN];
							// exclude at level if untouched or excluded
							if (processed[cvert]<=0) {
								processed[cvert]=-level; // exclude current size
								packData.kData[cvert].mark=-level;
							}
						}
					}

					// garner neighbors who might be chosen next
					int []vuw=v2micro[v];
					Vector<Node> hexNghbs=getHexRing(level,vuw[0],vuw[1]);
					Iterator<Node> hlst=hexNghbs.iterator();
					while (hlst.hasNext()) {
						Node nde=hlst.next();
						int mv=nde.myVert;
						if (processed[mv]==0) // not chosen or excluded 
							nextNode.add(nde);
					}
				}
			} // done with 'currNode'
		} // done with Step 4
		
		// are we stopping after Step 4? step=4;
		if (step==4) {
			lastProcessed=-level;
			return count;
		}
		
		// Step 5: ==============================================
		//   'barrier' nodes and reset excluded nodes
		//   using 'nixSmall[level+1]'.
		if (debug)
			System.out.println("\nLevel "+level+", Step 5; revisit 'barrier'.");
		if (level<levelCount) {
			uws=barrier.iterator();
			while (uws.hasNext()) {
				EdgeSimple edge=uws.next();
				edge=GridMethods.uw2UW(edge.v,edge.w,microN,gridN[level+1],stepDiam[level+1]);
				if (edge==null) 
					throw new CombException("pt not in superlattice");
				Node nde=nodeLUW[level+1][edge.v+gridN[level+1]][edge.w+gridN[level+1]];
				Iterator<EdgeSimple> sel=nixSmall[level+1].iterator();
				while(sel.hasNext()) {
					EdgeSimple uwe=sel.next();
					int u=nde.u+uwe.v;
					int w=nde.w+uwe.w;
					if (GridMethods.isLegalGridPt(u,w,microN)) {
						int cvert=micro2v[u+microN][w+microN];
						// exclude at level+1 if untouched or excluded
						if (processed[cvert]<=0) {
							processed[cvert]=-(level+1); // exclude current size
							packData.kData[cvert].mark=-(level+1);
						}
					}
				}
			}
		}
		
		if (debug)
			System.out.println("------------- done with level "+level+"; count "+count);
		
		lastProcessed=level;
		return count;
	} // done with 'processLevel'

	public static int addBearings(PackData q,double thresh) {
		int count=0;
		AmbiguousZ []amb=AmbiguousZ.getAmbiguousZs(q);
		for (int f=1;f<=q.faceCount;f++) {
			boolean marked=false;
			int []vert=q.faces[f].vert;
			double []radii=new double[3];
			for (int j=0;j<3;j++) {
				radii[j]=q.getRadius(vert[j]);
			}
			
			// first check if the 3 radii are not (essentially) equal
			boolean maybe=false;
			for (int j=0;(j<3 && !maybe);j++)
				if (Math.abs(radii[j]-radii[(j+1)%3])>.02*radii[j])
					maybe=true;
			
			// further check for inversive distance > thresh
			if (maybe) {
				for (int j=0;(j<3 && !marked);j++) {
					// an edge overlap exceeds the given threshold?
					if (q.getInvDist(vert[j],vert[(j+1)%3])>thresh)
						marked=true;
				}
			}
			
			// for marked faces, add a new circle, center at average
			if (marked) {
				Complex []pts=q.corners_face(f, amb);
				Complex bcent=pts[0].add(pts[1].add(pts[2])).divide(3.0);
				double brad=(radii[0]+radii[1]+radii[2])/6.0; // half the average

				
			    int newval=q.nodeCount+1;
			    if (newval>q.sizeLimit)
			    	q.alloc_pack_space(newval+10,true);
			    q.nodeCount++;
			    q.kData[newval]=new KData();
			    q.rData[newval]=new RData();
			    q.kData[newval].num=3;
			    q.kData[newval].flower=new int[4];
			    q.kData[newval].flower[0]=q.kData[newval].flower[3]=
			    		q.faces[f].vert[0];
			    q.kData[newval].flower[1]=q.faces[f].vert[1];
			    q.kData[newval].flower[2]=q.faces[f].vert[2];
			    if (q.overlapStatus) {
			    	q.kData[newval].invDist=new double[4];
			    	q.set_single_invDist(newval,q.kData[newval].flower[0],1.0);
			    	q.set_single_invDist(newval,q.kData[newval].flower[1],1.0);
			    	q.set_single_invDist(newval,q.kData[newval].flower[2],1.0);
			    	q.set_single_invDist(newval,q.kData[newval].flower[0],1.0);
			    }
			    q.setBdryFlag(newval,0);
			    q.kData[newval].mark=++count;
			    q.setCircleColor(newval,ColorUtil.getFGColor());
			    q.setRadius(newval,brad);
			    q.setCenter(newval,new Complex(bcent));
			    q.setAim(newval,2.0*Math.PI);
			    
			    // adjust nghb flowers
			    q.kData[vert[0]].add_petal_w(newval,vert[1]);
			    q.kData[vert[1]].add_petal_w(newval,vert[2]);
			    q.kData[vert[2]].add_petal_w(newval,vert[0]);
			}
		} // end of for loop on f
		
		q.setCombinatorics();
		return count;
	}
	
	/**
	 * After 'processLevel', vertex 'mark' shows status, >0 for those that are
	 * centers, <0 for those that are excluded. This routine marks the faces
	 * with vertices marked and returns a list of vertices on the boundary
	 * of the support.
	 * 
	 * @return NodeLink, boundary vertices
	 */
	public NodeLink markSupport() {
		NodeLink bdryVerts=new NodeLink(packData);
		int []logv=new int[packData.nodeCount+1];
		
		int f=1;
		
		// reset face marks, mark with largest level among vertices
		for (f=1;f<=packData.faceCount;f++) {
			int num=packData.faces[f].vertCount;
			int []verts=packData.faces[f].vert;
			
			int mxlevel=1;
			for (int j=0;j<num;j++) {
				int v=verts[j];
				int mk=Math.abs(packData.kData[v].mark);
				if (mk==0)
					mxlevel=0;
			}

			// mark face? mark vertices; those not hit are 0
			if (mxlevel>0) {
				packData.faces[f].mark=mxlevel;
				for (int j=0;j<num;j++) {
					int v=verts[j];
					if (logv[v]==0)
						logv[v]=f;
				}
				
				flist.add(f); // debug: put in our utility list
			}
		}
			
		// Find vertices in 'logv' which are boundary
		for (int v=1;v<=packData.nodeCount;v++) {
			if (logv[v]!=0) {
				boolean bdry=false;
				int []fflower=packData.kData[v].faceFlower;
				for (int j=0;(j<fflower.length && !bdry);j++)
					if (packData.faces[fflower[j]].mark==0)
						bdry=true;
				if (bdry)
					bdryVerts.add(v);
			}
		}

		return bdryVerts;
	}
	
	/**
	 * Get vector of 'Node's in 'level' superlattice that are closest
	 * to the points with signed microgrid coords (u,w).
	 * @param level int
	 * @param u int
	 * @param w int
	 * @return Vector<Node>, null on error
	 */
	public Vector<Node> getHexRing(int level,int u,int w) {
		Vector<Node> ans=new Vector<Node>(0);
		int d=stepDiam[level]; 

		// find signed superlattice coords (U,W) (of form (d*x,d*y))
		//   for closest lower left superlattice point
		int ud=(int)Math.floor((double)u/(double)d);
		int U=ud*d;
		int wd=(int)Math.floor((double)w/(double)d);
		int W=wd*d;
		
		// several situations:
		// (u,w) = vert in superlattice; return 6 superlattice neighbors
		Node nde=null;
		if (u==U && w==W) {
			nde=getNodeLUW(level,U,W-d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U-d,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U-d,W-d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// (u,w) is on an edge of superlattice, return 4
		if (u==U) { // w!=W
			nde=getNodeLUW(level,U,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U-d,W);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}

		// else on another edge
		if (w==W) { // u!=U
			nde=getNodeLUW(level,U,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U,W-d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W+d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// else yet another edge
		if ((u-U)==(w-W)) {
			nde=getNodeLUW(level,U,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W+d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// interior to upper superlattice triangle? return 3
		if ((w-W)>(u-U)) {
			nde=getNodeLUW(level,U,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U,W+d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// or to lower superlattice triangle? last chance
		if ((w-W)<(u-U)) {
			nde=getNodeLUW(level,U,W);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,U+d,W);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
			
		else // throw exception
			Oops("should have no possibilities left?");

		return ans;
	}

	/**
	 * Build linked list of hex lattice points (in relative
	 * coords) with at most given distance (in lattice units) 
	 * about a lattice point; the point itself is not included. 
	 * (To ensure points are strictly inside given radius, 
	 * user can decrease rad value slightly.)
	 * @param rad double, radius in lattice units 
	 * @return EdgeLink, (u,w)
	 */
	public EdgeLink formStencil(double rad) {
		// count generations of hex to encircle rad
		int nrad=(int)Math.floor(rad/CPBase.sqrt3by2);
		EdgeLink elist=new EdgeLink();
		for (int i=nrad;i>=0;i--) {
		for (int j=nrad;j>=1;j--) {
			double x=(double)(i+j)/2.0;
			double y=(double)(j-i)*CPBase.sqrt3by2;
			double dis=Math.sqrt(x*x+y*y);
			if (dis<=rad) 
				elist.add(new EdgeSimple(i,j));
			x=(double)(j-i-i)/2.0;
			y=(double)(j)*CPBase.sqrt3by2;
			if (dis<=rad)
				elist.add(new EdgeSimple(j-i,-i));
			x=(double)(i-j-2)/2.0;
			y=(double)(i)*CPBase.sqrt3by2;
			if (dis<=rad)
				elist.add(new EdgeSimple(-j,i-j));
		}
		}
		return elist;
	}
		
	/**
	 * Utility to parse 'level' in command strings: usage "-L{n}". Remove first 
	 * string of fsegs(0) and return n. 
	 * @param fsegs Vector<Vector<String>>
	 * @return int, -1 on error or if input is null
	 */
	public int getLevelStr(Vector<Vector<String>> fsegs) {
		try {
			String str=fsegs.get(0).get(0).trim();
			if (!str.startsWith("-L") || str.length()==2)
				return -1;
			str=str.substring(2);
			int level=Integer.parseInt(str);
			if (level<1 || level>levelCount)
				return -1;
			fsegs.get(0).remove(0);
			return level;
		} catch(Exception ex) {
			return -1;
		}
	}

	/**
	 * Given signed microGrid coords (u,w), return the 'Node' for
	 * (u,w) if it is a point in the 'level' superlattice. Return
	 * null if not.
	 * @param level int
	 * @param u int
	 * @param w int
	 * @return Node or null
	 */
	public Node getNodeLUW(int level,int u,int w) {
		EdgeSimple edge=GridMethods.uw2UW(u, w, microN, gridN[level],stepDiam[level]);
		if (edge==null)
			return null;
		Node nde=null;
		try {
			nde=nodeLUW[level][edge.v+gridN[level]][edge.w+gridN[level]];
		} catch(Exception ex) {
			return null;
		}
		return nde;
	}
	
	/**
	 * This initializes 'levelCount', 'gridN[]', 'stepRad[]', 'stepDiam[]', 'stepIntensity[]'.
	 * Default (mode==0) means equal increments (using log scale and max ratio). 
	 * TODO: in future, allow user to specify how to set up relation between radii and intensity
	 * @param mode
	 */
	public void setStepData(int mode) {
		
		double Qm1=platenP.get_Q()-1;

		// default: equal increments (wrt. log) based on Qm1.
		if (mode==0) {
			// Use log to find how many integral levels we can have
			//    with radius ratio (1.0+Qm1).
			double LR=Math.log(platenP.maxR)-Math.log(platenP.minR);

			// set number and size of steps, intensity levels:
			// Note: intensity goes down as level goes up.
			levelCount=(int)Math.floor(LR/Math.log((double)(Qm1+1)))+1;
			stepDiam=new int[levelCount+1];
			stepIntensity=new double[levelCount+1];
			double tdiff=(maxIntensity-minIntensity)/(double)(levelCount);
			// Note: indexing from 1
			stepDiam[1]=basediam;
			stepIntensity[1]=maxIntensity-tdiff;
			for (int j=2;j<=levelCount;j++) {
				stepDiam[j]=stepDiam[j-1]+(int)Math.floor(Qm1*(double)stepDiam[j-1]);
				stepIntensity[j]=stepIntensity[j-1]-tdiff;
			}
			stepIntensity[levelCount]=0.00;
			
			gridN=new int[levelCount+1]; // number of generations in the superlattices
			stepRad=new double[levelCount+1]; // radii for each level
			for (int lev=1;lev<=levelCount;lev++) {
				int N=(int) (Math.floor(microN/stepDiam[lev]));
				gridN[lev]=N;
				stepRad[lev]=1.0/(CPBase.sqrt3*N);
			}
		}
	}
	
	public void initCmdStruct() {
		super.initCmdStruct();
		
		cmdStruct.add(new CmdStruct("status",null,null,
				"Display current parameters and other settings; "+
				"can be reset with other calls"));
		cmdStruct.add(new CmdStruct("set_max_gen","n",null,
				"Set max number of generations in the microGrid"));
		cmdStruct.add(new CmdStruct("set_mM","{r R}",null,
				"Set min/max radius "+
				"of circles to 'r' and 'R': 0.1 and 0.5 are defaults."));
		cmdStruct.add(new CmdStruct("set_minR","{r}",null,
				"Set minimum radius "+
				"of circles to 'r': path radius/400 is default."));
		cmdStruct.add(new CmdStruct("set_maxR","{R}",null,
				"Set maximum radius "+
				"of circles to 'R': 0.5 is default."));
		cmdStruct.add(new CmdStruct("set_Q","{Q}",null,
				"Set the maximal ratio Q between "+
				"radii of incident circles: 1.5 is default."));
		cmdStruct.add(new CmdStruct("set_angle","{a}",null,
				"Rotate the hex by a*pi radians "+
				"counterclockwise."));
		cmdStruct.add(new CmdStruct("set_center","{x} {y}",null,
				"Set the center of the microgrid"));
		cmdStruct.add(new CmdStruct("intensity",null,null,
				"Set face colors based on average "+
				"intensity of its vertices"));
		cmdStruct.add(new CmdStruct("Disp and disp","[-L{n}] -c{} [am] [nlist]",
				null,"display circles, a=all, "+
				"m=marked, those in 'nlist', etc. 'Disp' does all levels."));
		cmdStruct.add(new CmdStruct("set_nlist","-L{n} [am] [nlist] [h u w]",null,
				"Sets utility node list. "+
				"This selects nodes for the list (including 'nlist' so you can add), "
				+ "h u w to get hex neighbors to <u,w>."));
		cmdStruct.add(new CmdStruct("process","[-L{x} [-s{n}]]",null,"Choose circles "+
				"to include: '-L option for up to that level; -s option for stage to "+
				"stop at in current level --- circles are chosen at levels 1, 2, and 4"));
		cmdStruct.add(new CmdStruct("put_rad","[-L{n}]",null,
				"Sets parent radii for the various "+
				"'Node's at given level (default to all levels)'."));
		cmdStruct.add(new CmdStruct("export",null,null,"Save the chosen "+
				"circle radii/centers to 'MicroExport.m'. Then by running "+
				"the Matlab code 'PtsPacking.m', which Delaunay triangulates "+
				"and puts results in 'MicroPacking.p'."+
				"This command also sets up the important 'vertexMap'"));
		cmdStruct.add(new CmdStruct("grid_length",null,null,
				"Compute the length of the dual graph edges having at "+
				"least one endpoint inside the path. This requires "+
				"'Node's at given level (default to all levels)'."));
		cmdStruct.add(new CmdStruct("grid_points",null,null,
				"Display the intensity field grid points"));
		cmdStruct.add(new CmdStruct("add_bearings","-q{n} [x]",null,
				"Adds a circle to faces when on or more edge inversive "+
				"distances is > x "+
				"(default, x=2.0). The center is at the average "+
				"of the corner circles."));
		cmdStruct.add(new CmdStruct("v_status","v..",null,
				"Show microgrid coords, nodes, etc. for given vertices"));
		cmdStruct.add(new CmdStruct("stencil","-L{} {sht} v",null,
				"Draw stencil in dots about v, given level L and s/h/t, small/large/tang"));
		cmdStruct.add(new CmdStruct("smooth","-q{n}",null,"Initiate a 'Smoother' object for "+
				"'field-based smoothing' so that it is attached to this 'MicroGrid', but also "+
				"to the independent packing p{n}, which should contain the exported results. "+
				"For other options, see 'smooth' in Help."));
	}
	

	// ======================= internal class ==============================
	/** 
	 * A 'Node' represents a point of the basic microgrid (before scaling). 
	 * A node location is in integer coords (n,m), where location is
	 * v = n*u + m*w where
	 * 		u = <1/2, -CPBase.sqrt3by2> and 
	 * 		w = <1/2, CPBase.sqrt3by2>.
	 * 
	 * 'numDiam' is in grid units, so diameter 1 is smallest circle possible
	 * and radius is 1/2 (in unscaled grid).
	 *   
	 * @author kstephe2, 2/2017
	 */
	class Node {
		public int myVert;		// index in 'packData'
		int u;			// u coord in microgrid
		int w;			// w coord in microgrid
		Color color;	// 
		boolean chosen; // true if node is a center at its level
		int mark;		
		
		// constructor
		public Node(int v,int uu,int ww,int nR) {
			myVert=v;
			u=uu;
			w=ww;
			chosen=false;
			mark=0;
		}
		
		/** 
		 * Find real world location
		 * @return Complex
		 */
		public Complex getZ() {
			double mrad=microScaling/2.0;
			return new Complex(mrad*((double)(u+w)),mrad*CPBase.sqrt3*((double)(w-u)));
		}
		

	} // end of 'Node' class
	
}