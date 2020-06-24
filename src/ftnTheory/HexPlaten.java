package ftnTheory;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.InOutException;
import exceptions.ParserException;
import geometry.EuclMath;
import input.CPFileManager;
import komplex.CookieMonster;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.PathLink;
import listManip.VertexMap;
import packing.PackCreation;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;

/**
 * Motivation: creating circle packings to be used as 2D grids in 3D printing.
 * Started in response to inquiries by Greg Dreifus while he was at ORNL's
 * MDF 3D printing facility.
 * 
 * Potential advantages: * can use triangulation or dual; * can manipulate (within
 * limits) to change the shape; * connection to analytic/harmonic functions -- e.g.,
 * heat dissipation;
 * 
 * Aim Here: Use interconnected hex lattices so we can change circle sizes to
 * accommodate different strength/density requirements. However, we will have to
 * use overlaps and inversive distances mixed in with the usual tangencies. The
 * idea is to allow selective refinement but still maintain ability to manipulate
 * with conformal-like behavior.
 * 
 * At the base is a "microgrid" --- several hex generations around the origin
 * forming a global hex pattern that covers the unit disc. Within that we will 
 * be able to define "supergrids", uniform hex lattices chosen from the microgrid, 
 * always including the origin as a supergrid vertex. The numerical "diameter" of 
 * the microgrid is 1, while supergrids have integral diameters.
 * 
 * The underlying packing always represents the full microgrid, but it is scaled by 
 * 'microScaling' (so its radii are 'microScaling'/2.0.
 * Only selected centers will end up being used; the data is kept in arrays of
 * 'Node's, which know 'myVert' (their index in the microgrid), their integral
 * diameters, and other pertinent data.
 * 
 * @author kstephe2, February 2017
 * 
 * NOTE: 3/22/17. Started 'MicroGrid.java' with for different approach.
 *
 */
public class HexPlaten extends PackExtender {
	
	final double sqrt3=Math.sqrt(3.0);
	
	int [][]nodeV;				// <u+microN,w+microN> entry is associated vert v
	
	PlatenParams platenP;		// hold the various parameters
	int microN;					// number of generations of micro grid
	int intensityType;			// 1 = function, 2 = data, 0 = error
	double []microIntensity;	// record intensity for each microgrid vertex;
								//   0 for points outside 'Omega'
	double minIntensity;	    // computed from intensity function
	double maxIntensity;
	
	// 'level's start at 1 (smallest circles) to 'levelcount'
	int levelCount;				// number of levels of circle sizes
	int []stepDiam;				// combinatorial diameters in terms of microgrid steps; index from 1
	double []stepIntensity;		// minimum intensity level for each step; index from 1
	double microScaling;		// scaling to put microgrid in the plane (2*radius for packData)
	int [][]micro2v;			// array to store 'packData' vert index by microgrid coords <u,w>
	
	Node [][][]nodeLUW;			// 'Node' associated with level L, location <u,w>, U=u+microN, W=w+microN  
	int []gridN;				// if n=gridN[level], then nodeLUW[level] is (2*n+1)x(2*n+1)

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
	EdgeLink []footprint; 		// at each level have a footprint increment --- nghbs to cover.
	Vector<Face> triangles;		// triangles for final complex (may be OBE due to Delaunay)
	EdgeLink constraintBdry;    // initialized for use as Delaunay constraint edges
	EdgeSimple []hexstencil;	// 6 increment directions from a hex location (closed up)
	
	public HexPlaten(PackData p) {
		super(p);
		extensionType="HEXPLATEN";
		extensionAbbrev="HP";
		toolTip="'HexPlaten' for creating hex based, but flexible circle packings.";
		registerXType();
		if (running)
			packData.packExtensions.add(this);

		// initialize
		// default path is unit square
		if (CPBase.ClosedPath==null) {
			PathLink pl=new PathLink();
			pl.add(new Complex(1.0,1.0));
			pl.add(new Complex(-1.0,1.0));
			pl.add(new Complex(-1.0,-1.0));
			pl.add(new Complex(1.0,-1.0));
			pl.add(new Complex(1.0,1.0));
			pl.autoClosure();
			CPBase.ClosedPath=pl.toPath2D();
		}
		intensityType=0;
		levelCount=1;
		stepDiam=null;
		stepIntensity=null;
		microScaling=1.0;
		platenP=new PlatenParams();
		platenP.set_trigger(true);
		microN=-1; // indicates not yet set
		
		// set up stencil
		hexstencil=new EdgeSimple[7];
		hexstencil[0]=hexstencil[6]=new EdgeSimple(1,0);
		hexstencil[1]=new EdgeSimple(1,1);
		hexstencil[2]=new EdgeSimple(0,1);
		hexstencil[3]=new EdgeSimple(-1,0);
		hexstencil[4]=new EdgeSimple(-1,-1);
		hexstencil[5]=new EdgeSimple(0,-1);

		// get everything started
		reset();
	}
	
	/**
	 * Here we check the specified parameters and if they're in place, we
	 * compute the various quantities that depend on them: 
	 * @return
	 */
	public int reset() {
		
		lastProcessed=0;
		
		// must have 'Omega' first and it must be in 'ClosedPath'
		if (!platenP.set_Omega()) {
			CirclePack.cpb.errMsg("User must set 'ClosedPath': load *.g file or use cursor.");
			Oops("User must set 'ClosedPath': load *.g file or use cursor.");
		}
		
		// integral diameter of smallest circle that can accommodate 
		//    'ratioQ' ratio with radii of neighboring circles. However, we 
		//    enforce minimum of diameter 2.     
		double Qm1=platenP.get_Q()-1;
		int basediam=1;
		while (Math.floor(Qm1*basediam)<=0)
			basediam++;
		
		// compute 'microN', number of hex generations in underlying microgrid
		microN=(int)Math.floor(platenP.extentOmega/(sqrt3*platenP.minR))*basediam+2*basediam;
		double denom=(double)basediam;
		microScaling=2.0*platenP.minR/denom;
		
		// create the circle packing and cut it down to 'Omega]
		PackData newPack=PackCreation.hexBuild(microN);
		swapPackData(newPack);

		// normalize: alpha at origin, 10 on +real axis
		cpCommand("gamma 10");
		cpCommand("set_rad "+microScaling/2.0+" a");
		cpCommand("layout");
			
		// TODO: want to cookie so bdry runs through vertices which
		//    are in the smallest supergrid --- because for the
		//    Delaunay triangulation I need these as boundary contraints.

    	// -------------------------------------------- new stuff
	    	
	    // need vertices of smallest supergrid enclosing bdry of 'Omega' 
	    double dist=1.25*basediam*microScaling;
	    NodeLink bdryNghb=new NodeLink(packData,new String("Ig "+String.format("%.6f",dist)));
	    
	    // debug
	    boolean debug=false; // debug=true;
	    if (debug) {
	    	packData.vlist=bdryNghb;
	    	return -1;
	    }
	    
	    // prepare to store v <--> (u,w) info as it emerges
	    int D=2*microN;
	    int [][]tmpUWV=new int[D+1][];
	    for (int i=0;i<=D;i++)
	    	tmpUWV[i]=new int[D+1];
	    
	    // process: prune away those outside 'Omega', find pts on
	    //    smallest supergrid, store v, (u,w) info in 'tmpUWV'
	    int farU=0;
	    int farW=0;
	    double farthest=-1.0;
	    Iterator<Integer> bit=bdryNghb.iterator();
	    while (bit.hasNext()) {
	    	int v=bit.next();
	    	Complex z=packData.rData[v].center;
	    	
	    	// is it inside 'Omega'?
	    	if (CPBase.ClosedPath.contains(new Point2D.Double(z.x,z.y))) {
	    		int []uw=getCoords(z);
	    		// is this on smallest supergrid? (called "basegrid")
	    		if ((uw[0]%basediam)==0 && (uw[1]%basediam)==0) {
	    			
	    			// is it now the furthest from the origin?
	    			if (z.abs()>farthest) {
	    				farU=uw[0];
	    				farW=uw[1];
	    				farthest=z.abs();
	    			}
	    			
	    			// store info of later use
	    			tmpUWV[uw[0]+microN][uw[1]+microN]=v;
	    		}
	    	}
	    } // done processing vertices inside and near bdry of 'Omega'
	    
	    // now we walk around the boundary cclw from 'farvert' using
	    //    hex extended edges between pts in the basegrid.
	    //    We mark them as poison, then we'll cookie.

	    NodeLink outerV=new NodeLink(packData);
	    
	    // found point <farU,farW>, but need indices from 0 to D.
	    int currI=farU+microN;
	    int currJ=farW+microN;
	    
	    // first, need to find "outward" edge from <currI,currJ>
	    int iinc=0;
	    int jinc=0;
	    if (farU>0)
	    	iinc=basediam;
	    else if (farU<0)
	    	iinc=-basediam;
	    else {
	    	if (farW>0)
	    		jinc=basediam;
	    	else 
	    		jinc=-basediam;
	    }
	    
	    // find next bdry point in basegrid, then we can iterate
	    int lastI=currI+iinc;
	    int lastJ=currJ+jinc;
	    int startingV=tmpUWV[currI][currJ];
	    outerV.add(startingV);
	    
	    int []astep=cclwUW(currI,currJ,lastI,lastJ,basediam,tmpUWV);
	    if (astep==null) 
	    	Oops("problem following boundary");
	    lastI=currI;
	    lastJ=currJ;
	    currI=astep[0];
	    currJ=astep[1];
	    int currV=tmpUWV[currI][currJ];
	    outerV.add(currV);
	    
	    // iterate
	    int safety=packData.nodeCount;
	    while(currV!=startingV && safety>0) {
	    	int []step=cclwUW(currI,currJ,lastI,lastJ,basediam,tmpUWV);
	    	if (step==null)
	    		Oops("also problem following bdry");
		    lastI=currI;
		    lastJ=currJ;
		    currI=step[0];
		    currJ=step[1];
		    currV=tmpUWV[currI][currJ];
		    outerV.add(currV);
		    safety--;
	    }
	    
	    // debug
	    if (safety==0) {
	    	packData.vlist=outerV;
	    	return -1;
	    }

	    // debug
	    debug=false; // debug=true;
	    if (debug) {
	    	packData.vlist=outerV;
	    	return -1;
	    }

	    // set to poison
	    CPBase.Vlink=outerV;
	    Vector<String> itms=new Vector<String>(0);
	    itms.add("-e");
	    itms.add("e");
	    itms.add("Vlist");
	    packData.set_poison(itms);
	    
		// now, 'cookie' based on poison vertices;
    	CookieMonster cM=null;
    	try {
    		cM=new CookieMonster(packData,(Vector<Vector<String>>)null);
    		int outcome=cM.goCookie();
	    	if (outcome<0) {
	    		throw new ParserException("cookie crumbled");
	    	}
	    	if (outcome>0) {
	    		packData.cpScreen.swapPackData(cM.getPackData(),true);
    		    packData=packData.cpScreen.getPackData();
	    	}
    	} catch(Exception ex) {
    		Oops("cookie failed in HexPlaten");
    	}

	    // ------------------------------------------ done preparing 'packData'
		
		// create array that monitors the proceesing stages
		processed=new int[packData.nodeCount+1];
		
		// allocate 'micro2v and store verts for coords <u,w> 
		micro2v=new int[2*microN+1][];
		for (int u=-microN;u<=microN;u++) {
			micro2v[u+microN]=new int[2*microN+1];
		}
		for (int v=1;v<=packData.nodeCount;v++) {
			int flg=0;
			int []uw=getCoords(packData.rData[v].center);
			micro2v[uw[0]+microN][uw[1]+microN]=v;
			if ((uw[0]%basediam)==0 && (uw[1]%basediam)==0)
				flg=-1;
			packData.kData[v].utilFlag=flg; // mark basegrid vertices
		}
		
		// debug
//		int ckcount=0;
//		int []util=new int[packData.nodeCount+1];
//		for (int u=0;u<=2*microN;u++)
//			for (int w=0;w<=2*microN;w++) {
//				int v=micro2v[u][w];
//				if (util[v]==0) {
//					packData.kData[v].mark=1;
//					ckcount++;
//				}
//			}
//		int df=packData.nodeCount-ckcount;
//		System.out.println("nodecount - ckcount = "+df);
		
		// need intensity next: currently default to Gaussian e^{-|z|^2/2}
		if (intensityType==0) {
			double []gotmm=set_IntFunction(1,"exp(-1.0*abs(z)*abs(z)/2.0)");
			minIntensity=gotmm[0];
			maxIntensity=gotmm[1];
			intensityType=1;
		}
		
		// store intensity values at microgrid centers, get min/max;
		double []mnmx=setMicroIntensity();
		minIntensity=mnmx[0];
		maxIntensity=mnmx[1];
		
		// set intensity high on the boundary
		NodeLink bdry=new NodeLink(packData,"b");
		bit=bdry.iterator();
		while (bit.hasNext()) {
			int v=bit.next();
			microIntensity[v]=maxIntensity;
		}

    	// Build 'constraintBdry' of pairs (v1,v2) of successive bdry basegrid
		//   points to be used as constraints in Delaunay call later.
	    // (Note: some of our earlier basegrid bdry vertices may have been pruned.)
		Vector<Integer> bvec=new Vector<Integer>(0);
		int stw=packData.bdryStarts[1];
		
		safety=100;
		while (packData.kData[stw].utilFlag>=0 && safety>0) {
			stw=packData.kData[stw].flower[0];
			safety--;
		}
		
		if (safety==0)
			Oops("safety stop setting up 'constraintBdry'");

		bvec.add(Integer.valueOf(stw));
		int b=stw;
		while ((b=packData.kData[b].flower[0])!=stw) {
			if (packData.kData[b].utilFlag<0)
				bvec.add(Integer.valueOf(b));
		}
		bvec.add(stw); // close up
		constraintBdry=new EdgeLink(packData);
		Iterator<Integer> bvi=bvec.iterator();
		int e1=0;
		int e2=bvi.next();
		while (bvi.hasNext()) {
			e1=e2;
			e2=bvi.next();
			constraintBdry.add(new EdgeSimple(e1,e2));
		}
		
		// debug
		CPBase.Elink=constraintBdry;

		// set number and size of steps, intensity levels
		double LR=Math.log(platenP.maxR)-Math.log(platenP.minR);
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
		stepIntensity[levelCount]=0.001; // reset the last minimum to avoid 0
		
		// set up footprints: at a given level of circle, 'footprint' is the
		//   points <u,w> (in relative coords) within max radius so circle of previous level
		//   centered there would overlap by at more than pi.
		
		footprint=new EdgeLink[levelCount+1];
		double []rads=new double[levelCount+1];
		for (int lev=1;lev<=levelCount;lev++) {
			footprint[lev]=new EdgeLink();
			footprint[lev].add(new EdgeSimple(0,0)); // <0,0> in every footprint
			double d=(double)stepDiam[lev];
			double dpre=(double)stepDiam[lev-1]; 
			rads[lev]=Math.sqrt(d*d+dpre*dpre)/2.0;
		}
		
		// biggest radius we need to search 
		int H=2*(int)Math.floor(rads[levelCount]/sqrt3);
		
		for (int i=0;i<=H;i++) {
			for (int j=1;j<=H;j++) {
				double x=(double)(i+j)/2.0;
				double y=(double)(j-i)*sqrt3/2.0;
				double dis=Math.sqrt(x*x+y*y);
				int tick=1;
				while (tick<=levelCount && dis>rads[tick])
					tick++;
				
				// yes, <i,j> is in footprints of level <= 'tick'
				//   along with 5 symmetric points.
				if (tick<=levelCount) {
					for (int k=tick;k<=levelCount;k++) {
						footprint[k].add(new EdgeSimple(i,j));
						footprint[k].add(new EdgeSimple(j-i,-i));
						footprint[k].add(new EdgeSimple(-j,i-j));
					}

				}

			}
		}
		
		// debug
//		StringBuilder strb=new StringBuilder("Footprint[1]: ");
//		Iterator<EdgeSimple> fit=footprint[1].iterator();
//		while (fit.hasNext()) {
//			EdgeSimple es=fit.next();
//			strb.append("("+es.v+","+es.w+") ");
//		}
//		System.out.println(strb.toString());

		// instantiate 'myLists' vectors
		myLists=new Vector<Vector<Node>>(levelCount+1);
		myLists.add(null); // first spot empty
		for (int j=1;j<=levelCount;j++)
			myLists.add(new Vector<Node>(0));
		
		// find number of generations in the supergrids
		gridN=new int[levelCount+1];
		for (int lev=1;lev<=levelCount;lev++) {
			gridN[lev]=(int) (Math.floor(microN/stepDiam[lev]));
		}
		
		// create arrays of 'Node's
		nodeLUW=new Node[levelCount+1][][];
		for (int lev=1;lev<=levelCount;lev++) {
			int DD=gridN[lev]*2+1;
			
			// D-by-D array (though becuase of hex, some corners are not used)
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
					
					if ((v=micro2v[mu][mw])!=0) {
						Node node=new Node(v,u,w,d);
						node.color=ColorUtil.spreadColor(lev-1);
						nodeLUW[lev][ui][wj]=node;
					}
					// debug
//					else System.out.println("bad uu, ww "+ui+","+wj);

				}
			}
		}

		// assume we have accommodated all parameter changes.
		platenP.chgTrigger=false; 
		
		return packData.nodeCount;
	}
	
	/**
	 * Consider points in supergrid with diameter 'diam'. Given <i,j> 
	 * and neighboring point <di,dj>, find coords <I,J> of first edge 
	 * from <i,j> counterclockwise from <di,dj> with tij[I][J] having 
	 * a nonzero entry (ie., a vertex).
	 * @param i int
	 * @param j int
	 * @param di int
	 * @param dj int
	 * @param diam int
	 * @return int[2] = {I,J}, null on error
	 */
	public int []cclwUW(int i,int j, int di,int dj,int diam,int [][]tij) {
		int []ans=new int[2];
		int iinc=(di-i)/diam;
		int jinc=(dj-j)/diam;

		// search stencil starting at direction after <iinc,jinc>
		int startspot=0;
		if (iinc==1 && jinc==0)
			startspot=1;
		else if (iinc==1 && jinc==1)
			startspot=2;
		else if (iinc==0 && jinc==1)
			startspot=3;
		else if (iinc==-1 && jinc==0)
			startspot=4;
		else if (iinc==-1 && jinc==-1)
			startspot=5;

		for (int m=startspot;m<=(startspot+4);m++) {
			EdgeSimple hs=hexstencil[m%6];
			int newI=i+diam*hs.v;
			int newJ=j+diam*hs.w;
			if (tij[newI][newJ]!=0) {
				ans[0]=newI;
				ans[1]=newJ;
				return ans;
			}
		}
		return null;
	}
	
	/**
	 * Intensity is a nonnegative function that should be defined on 'Omega'.
	 * TODO: just mode 1 for now. 
	 * TODO: how to set intensities; maybe user has to provide?
	 * @param mode int, 1=function, 2=file data to interpolate
	 * @param str String; either function description or data 'filename'
	 * @return double[2], min/max intensities
	 */
	public double []set_IntFunction(int mode,String str) {
		double []ans=new double[2];
		// set mode: 1 means 'str' gives a formula in z
		if (mode==1) {
			int rslt=cpCommand("set_func "+str);
			if (rslt==0) {
				intensityType=0;
				Oops("Failed to set function to str = "+str);
			}
			
			// TODO: how to set these?
			// defaulting to Gaussian on disc for now
			ans[1]=1.0;
			ans[0]=Math.exp(-5);
			return ans;
		}
		else 
			mode=0;
		return null;
	}
	
	/**
	 * TODO: want to allow either formula or data set with interpolation.
	 * Currently just return Gaussian
	 * @param z
	 * @return
	 */
	public double getIntensity(Complex z) {
		if (platenP.myOmega==null)
			Oops("Can't get intensity: region 'myOmega' is not set");
		
		// z is not inside Omega
		if (!platenP.myOmega.contains(new Point2D.Double(z.x,z.y)))
			return 0.0;
		
		// TODO: temporarily, intensity given by Gaussian
		return Math.exp(-0.5*(z.absSq()));
	}

	/**
	 * Based on current 'Omega', microgrid, and intensity function,
	 * fill 'microIntesity' array with intensities, 0 for points
	 * outside 'Omega'.
	 * @return double[], min/max non-zero intensity.
	 */
	public double []setMicroIntensity() {
		microIntensity=new double[packData.nodeCount+1];
		double []ans=new double[2];
		double mxI=-1.0;
		double mnI=1000000.0;
		for (int v=1;v<=packData.nodeCount;v++) {
			Complex z=packData.rData[v].center;
			if (platenP.myOmega.contains(new Point2D.Double(z.x,z.y))) {
				double value=getIntensity(z);
				mxI =(value>mxI)?value:mxI;
				mnI=(value<mnI)?value:mnI;
				microIntensity[v]=value;
			}
		}
		ans[0]=mnI;
		ans[1]=mxI;
		return ans;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		int count=0;
		int level=-1;
		Node node=null;
		

		// ============== put_rad
		if (cmd.startsWith("put")) {
			
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
	    					packData.rData[nde.myVert].rad=rad;
	    					count++;
	    				}
	    			}
	    		}
			}
			return count;
		}
		
		// ============== save data for Delaunay =========
		else if (cmd.startsWith("Del")) {

			// 'vertexMap' will index translations, <parent, triangulation>
			packData.vertexMap=new VertexMap();

			// get vector of centers and note new indices
			Vector<Complex> Zvec=new Vector<Complex>(0);
			int tick=0;
	    	for (int lev=1;lev<=levelCount;lev++) {
	    		int D=gridN[lev]*2;
	    		Node [][]nodeL=nodeLUW[lev];
	    		for (int i=0;i<=D;i++) {
	    			for (int j=0;j<=D;j++) {
	    				Node nde=nodeL[i][j];
	    				if (nde!=null && nde.chosen) {
	    					Zvec.add(packData.rData[nde.myVert].center);
	    					packData.vertexMap.add(new EdgeSimple(nde.myVert,++tick));
	    				}
	    			}
	    		}
	    	}
	    	
	    	// get the chosen boundary constraint edges
	    	EdgeLink chosenBdry=new EdgeLink(packData);
	    	Iterator<EdgeSimple> eit=constraintBdry.iterator();
	    	while (eit.hasNext()) {
	    		EdgeSimple edge=eit.next();
	    		int e1=packData.vertexMap.findW(edge.v);
	    		int e2=packData.vertexMap.findW(edge.w);
	    		chosenBdry.add(new EdgeSimple(e1,e2)); // edge with new indices
	    	}
	    	
	    	// data is held in 'dData'
//			DelaunayData dData=new DelaunayData(0,Zvec,chosenBdry);
			
			// TODO: when C code is ready, we will call 'DelaunayBuilder' here

			// =============== temporarily must write to files, use matlab ===============
			// Matlab file is in repository: 'getDelaunay.m' and puts results in
			//   'hexTriangulation.m'.
		    String dir=CPFileManager.PackingDirectory.toString();
		    boolean script_flag=false;
		    boolean append_flag=false;
					
	    	// center points: temporary file
	    	File file=new File("hexPoints.m");
		    BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
		    		append_flag,file.getName(),script_flag);
		    if (fp==null)
		    	throw new InOutException("Failed to open '"+file.toString()+"' for writing");

		    try {
//		    	fp.write("Z=[\n");
		    	Iterator<Complex> cit=Zvec.iterator();
		    	while (cit.hasNext()) {
		    		Complex z=cit.next();
		    		fp.write(z.x+"  "+z.y+"\n");
		    	}
//		    	fp.write("];\n");
		    	fp.flush();
		    	fp.close();
		    } catch(Exception ex) {
		    	Oops("failed writing Hex Centers");
		    }
		    CirclePack.cpb.msg("Wrote 'hexPoints.m' "+
		    		  CPFileManager.PackingDirectory+File.separator+file.getName());

		    // center points: temporary file
		    file=new File("hexConstraints.m");
			fp=CPFileManager.openWriteFP(new File(dir),
					append_flag,file.getName(),script_flag);
			if (fp==null) 
				throw new InOutException("Failed to open '"+file.toString()+"' for writing");

			try {
//		    	fp.write("C=[\n");
		    	eit=chosenBdry.iterator();
		    	while (eit.hasNext()) {
		    		EdgeSimple edge=eit.next();
		    		fp.write(edge.v+" "+edge.w+"\n");
		    	}
//		    	fp.write("];");

		    	fp.flush();
		    	fp.close();
		    } catch(Exception ex) {
		    	Oops("failed writing Hex Constraint matrix");
		    }
		    CirclePack.cpb.msg("Wrote 'hexConstraints.m' "+
		    		  CPFileManager.PackingDirectory+File.separator+file.getName());
	    	return 1;
		}
		
		// ============== erase bdry edges outside constraint
		// TODO: temporary until Delaunay code is cleared up
		else if (cmd.startsWith("erase")) {

			if (constraintBdry==null || constraintBdry.size()==0)
				Oops("erase depends on having constraint bdry");
			int qnum=1;
			String str=StringUtil.getOneString(flagSegs);
			if (str!=null) { // only possibility is '-q{p}' flag to give pnum
				if (str.startsWith("-q") && str.length()==3) {
					try {
						qnum=Integer.parseInt(str.substring(2));
					} catch(Exception ex) {
						Oops("usage: erase -q{q}");
					}
				}
			}
			
			// packing obtained from the triangulation
			PackData qackData=CPBase.pack[qnum].getPackData();

			// translate 'constraintBdry' to local indices
			EdgeLink locCBdry=new EdgeLink(qackData);
			Iterator<EdgeSimple> eit=constraintBdry.iterator();
			while (eit.hasNext()) {
				EdgeSimple edg=eit.next();
				int V=packData.vertexMap.findW(edg.v);
				int W=packData.vertexMap.findW(edg.w);
				if (V==0 || W==0) 
					
					// debug
					System.err.println("parent v,w = "+edg.v+","+edg.w+" but child V,W = "+V+","+W);
					
//					Oops("lost in translation");
				else 
					locCBdry.add(new EdgeSimple(V,W));
			}

			// cycle through removing existing edges (e1,e2) if
			//    edge is NOT in locCBdry
			boolean hit=true;
			int safety=100;
			while (hit && safety>0) {
				hit=false;
				safety--;
				
				EdgeLink toBremoved=new EdgeLink(qackData);
				
				// once around the boundary to collect edges
				int startV=qackData.bdryStarts[1];
				int v=0; // qackData.kData[startV].flower[0];
				int w=startV;
				int wflag=0;
				while (w!=startV || wflag==0) {
					wflag=1;
					v=w;
					w=qackData.kData[v].flower[0];
					if (!EdgeLink.ck_in_elist(locCBdry,v,w)) {
						toBremoved.add(new EdgeSimple(v,w));
					}
				}

				// try to remove
				int nbr=qackData.remove_edge(toBremoved);
				if (nbr>0) {
					hit=true;
					count +=nbr;
					qackData.setCombinatorics(); // ready for another try
				}
			}
			
			if (safety==0)
				CirclePack.cpb.errMsg("safety barrier hit in 'erase'");
			
			return count;
		}
		
		// ============== form triangulation === OBE, using Delaunay instead
//		else if (cmd.startsWith("tri")) {
//
//			if ((level=getLevelStr(flagSegs))<0)
//				level=lastTriangled+1;
//			return processTris(level);
//		}
		
		// ============== debug options =================
		else if (cmd.startsWith("debug")) {
			
			String str=StringUtil.getOneString(flagSegs);
			if (str==null)
				str="f"; // default to faces
			
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
		
		// =============== process; I am developing the algorithm
		else if (cmd.startsWith("process")) {

			if ((level=getLevelStr(flagSegs))<0)
				level=lastProcessed+1;
			return processLevel(level);
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
				case 'h':  // hex nghbs of (u,w) in supergrid
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
			platenP.status();
			count=1;
		}
		
		else if (cmd.startsWith("reset")) {
			return reset();
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
				if (scmd.startsWith("mM")) {
					platenP.set_minR(Double.parseDouble(items.get(0)));
					platenP.set_maxR(Double.parseDouble(items.get(1)));
					count++;
				}
				else if (scmd.startsWith("minR")) {
					platenP.set_minR(Double.parseDouble(items.get(0)));
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
			} catch(Exception ex) {
				Oops("error with '"+cmd+"'");
			}
			
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
					
					// empty spec means all
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
									myLists!=null && (nlist=myLists.get(level))!=null && nlist.size()>0) {
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
						Complex z=packData.rData[node.myVert].center;
						double rad=0.5*microScaling*stepDiam[level];
						dispflgs.setColor(node.color);
						cpScreen.drawCircle(z,rad,dispflgs);
						count++;
					}

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
								Complex z=packData.rData[f.vert[kk]].center;
								corners[2*kk]=z.x;
								corners[2*kk+1]=z.y;
							}
							cpScreen.drawClosedPoly(3,corners,dispflgs);
							count++;
						}
					}

					PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
					break;
				}
				default: // error if we got here
				{
					Oops("error is 'disp' specifications");
				}
				} // end of switch on objects to display
			} // end of while through 'disp' flags segments
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
	 * This is the developing heart of the algorithm
	 * @param level int
	 * @return
	 */
	public int processLevel(int level) {
		
		int count=0;
		Node node=null;
		vlist=new NodeLink(packData); // debug tool for various uses
		flist=new FaceLink(packData);
		
		// at first level have to reset various things
		if (level==1) { // First level: centers at highest intensity, smallest radii.
			
			for (int v=1;v<=packData.nodeCount;v++) {
				packData.kData[v].mark=0;
//				packData.rData[v].rad=0.5*microScaling*(double)stepDiam[1]; // smallest radius
			}
			for (int f=1;f<=packData.faceCount;f++) {
				packData.faces[f].mark=0;
			}
			latestBdry=null;
			processed=new int[packData.nodeCount+1]; 
		}
		
		// previous level done, now look through bdry for circles at new level 
		if (latestBdry!=null && latestBdry.size()>0) {
			
			// go through 'latestBdry' as long as we're adding current 'level' circles
			
			boolean hit=true;
			while (hit) {
				hit=false;
				Iterator<Integer> lit=latestBdry.iterator();
				Vector<Node> vecnode=null;
				while(lit.hasNext()) {
					int v=lit.next();

					// Get surrounding neighbors at this level
					int []vuw=getCoords(packData.rData[v].center);
					vecnode=getHexRing(level,vuw[0],vuw[1]);
					Iterator<Node> vit=vecnode.iterator();
					while(vit.hasNext()) {
						Node nde=vit.next();
						int nv=nde.myVert;
						if (processed[nv]==0) { // yes, can put circle here
							
							// TODO: may want to check overlaps with previous level
							
							hit=true;
							nde.chosen=true;
							nde.mark=level;
							processed[nv]=level;
							packData.kData[nv].mark=level;
//							packData.rData[nv].rad=0.5*microScaling*(double)stepDiam[level]; 
							count++;
							
							// mark footprint about <u,w> as covered
							EdgeLink elink=footprint[level]; 
							Iterator<EdgeSimple> eit=elink.iterator();
							while(eit.hasNext()) {
								EdgeSimple uw=eit.next();
								int ui=nde.u+uw.v+microN;
								int wj=nde.w+uw.w+microN;
								int cvert=micro2v[ui][wj];
								if (processed[cvert]==0 && isInMicro(uw.v,uw.w)) {
									processed[cvert]=-level; // covered at this level
									packData.kData[cvert].mark=-level;
								}
							}
						}
					} // end of while on cell
				} // end of while on ring neighbors
				
			} // end of 'hit' while
			
			// pass through again, may add more circles of previous level
			if (level>1) {
				Iterator<Integer> lit=latestBdry.iterator();
				Vector<Node> vecnode=null;
				while(lit.hasNext()) {
					int v=lit.next();
					if (processed[v]<=0) {
						// get surrounding neighbors at previous level
						int[] vuw = getCoords(packData.rData[v].center);
						vecnode = getHexRing(level - 1, vuw[0], vuw[1]);
						Iterator<Node> vit = vecnode.iterator();
						while (vit.hasNext()) {
							Node nde = vit.next();
							int nv = nde.myVert;
							if (processed[nv] == 0) { // yes, can put circle here
								hit = true;
								nde.chosen = true;
								nde.mark = level - 1;
								processed[nv] = level - 1;
								packData.kData[nv].mark = level - 1;
//								packData.rData[nv].rad=0.5*microScaling*(double)stepDiam[level-1]; 
								count++;
								
								// mark footprint about <u,w> as covered
								EdgeLink elink=footprint[level-1]; 
								Iterator<EdgeSimple> eit=elink.iterator();
								while(eit.hasNext()) {
									EdgeSimple uw=eit.next();
									int ui=nde.u+uw.v+microN;
									int wj=nde.w+uw.w+microN;
									int cvert=micro2v[ui][wj];
									if (processed[cvert]==0 && isInMicro(uw.v,uw.w)) {
										processed[cvert]=-(level-1); // covered at this level
										packData.kData[cvert].mark=-(level-1);
									}
								}

							}
						} // end of while on cell
					}
				} // end of while on ring neighbors
				
			} // end of 'hit' while
		}
		
		// Now add more circles at this level based on intensity
		int N=gridN[level];

		for (int i=0;i<=2*N;i++) {
			for (int j=0;j<=2*N;j++) {
					
			//debug
//			System.out.println("i,j = "+i+","+j);
					
				if ((node=nodeLUW[level][i][j])!=null) {
						
					// vert v and microgrid coords <u,w>
					int v=node.myVert; 
						
					if (microIntensity[v]>=stepIntensity[level] && processed[v]==0) {
						
						// store results
						node.chosen=true;
						node.mark=level; // set 'mark' for node
						processed[v]=level; // this vert is a center at this level
							
						// store radius and color in 'packData'
						packData.kData[v].mark=level; 
						packData.kData[v].color=CPScreen.cloneColor(node.color);
//						packData.rData[v].rad=0.5*microScaling*(double)stepDiam[level];

						// mark footprint about <u,w> as covered
						EdgeLink elink=footprint[level]; 
						Iterator<EdgeSimple> eit=elink.iterator();
						while(eit.hasNext()) {
							EdgeSimple uw=eit.next();
							int ui=node.u+uw.v+microN;
							int wj=node.w+uw.w+microN;
							int cvert=micro2v[ui][wj];
							if (processed[cvert]==0 && isInMicro(uw.v,uw.w)) {
								processed[cvert]=-level; // covered at this level
								packData.kData[cvert].mark=-level;
							}
						}
						count++;
					}
				}
			}
		}
		
		// mark support and find its bdry
		NodeLink levelBdry=markSupport();
		latestBdry=levelBdry.makeCopy();
		
		// debug --- set 'vlist' to bdry so we can look at it
		packData.vlist=levelBdry;
		
		lastProcessed=level;
		return count;

	}
	
	/**
	 * After 'processLevel', vertex 'mark' shows status, >0 for those that are
	 * centers, <0 for those that are covered. This routine marks the faces
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
	 * OBE. Currently using Delaunay to build the triangulation rather
	 * than building up flower by flower. 
	 * @param level
	 * @return
	 */
	public int processTris(int level) {
		int count=0;
		Node node=null;
		
		// at first level have to reset various things
		if (level==1) { 
			lastTriangled=1;
			triangles=new Vector<Face>();
		}
		
		// Start by creating 'flowers' and adding petals at same 'level'
		Node [][]levNodes = nodeLUW[level];
		int N=gridN[level];
		int D=2*N;
		for (int i=0;i<=D;i++) {
			for (int j=0;j<=D;j++) {
				node = levNodes[i][j];
				if (node!=null && node.flower==null) {
					node.num=6;
					node.flower=new int[node.num+1];
				}

				// chosen? get neighbors at this 'level'
				if (node!=null && node.chosen) {
					Node nde=null;
					
					// add cclw from 'u' direction
					int ii=i+1; // 'u' direction first
					int jj=j;
					if (ii<=D) {
						nde=levNodes[ii][j];
						if (nde!=null && nde.chosen) {
							node.flower[0]=nde.myVert;
							node.flower[node.num]=nde.myVert; // may eventually close up
						}
						jj=j+1; // then 'u+w' direction
						if (jj<=D) {
							nde=levNodes[ii][jj];
							if (nde!=null && nde.chosen) 
								node.flower[1]=nde.myVert;
						}
					}
					jj=j+1; // then 'w' direction
					if (jj<=D) {
						nde=levNodes[i][jj];
						if (nde!=null && nde.chosen)
							node.flower[2]=nde.myVert;
					}
					ii=i-1; // then '-u' direction
					if (ii>=0) {
						nde=levNodes[ii][j];
						if (nde!=null && nde.chosen)
							node.flower[3]=nde.myVert;
						jj=j-1;
						if (jj>=0) { // then '-u-w' direction
							nde=levNodes[ii][jj];
							if (nde!=null && nde.chosen)
								node.flower[4]=nde.myVert;
						}
					}
					jj=j-1; // finally, the '-w' direction
					if (jj>=0) {
						nde=levNodes[i][jj];
						if (nde!=null && nde.chosen)
							node.flower[5]=nde.myVert;
					}
				}
			}
		} // end of loop through i,j
		
		// next, we identify triples all at the same level
		for (int i=0;i<=D;i++) {
			for (int j=0;j<=D;j++) {
				node = levNodes[i][j];
				
				if (node!=null) {
					int V=node.myVert;
					for (int k=0;k<node.num;k++) {
						int U=node.flower[k];
						int W=node.flower[k+1];

						// avoid redundancy, V smallest?
						if (U>0 && W>0 && V<U && V<W) {
							Face f=new Face();
							f.vert[0]=V;
							f.vert[1]=U;
							f.vert[2]=W;
							f.mark=level;
							triangles.add(f);
							count++;
						}
					} // end of loop through 'flower'
				}
			} // 
		} // end of loops for i and j
		return count;
	}
	
	/**
	 * Find 'Node' with given <u,w> microgrid coords by searching 
	 * the given vector (usually some level)
	 * @param u int
	 * @param w int
	 * @param nvec Vector<Node>
	 * @return Node, null if none found
	 */
	public Node getNode(int u,int w,Vector<Node> nvec) {
		if (nvec==null || nvec.size()==0)
			return null;
		Iterator<Node> nit=nvec.iterator();
		while (nit.hasNext()) {
			Node node=nit.next();
			if (node.u==u && node.w==w)
				return node;
		}
		return null;
	}
	
	/**
	 * Get vector of 'Node's in supergrid 'level' which are closest
	 * to the microgrid point <u,w>.
	 * @param level int
	 * @param u int
	 * @param w int
	 * @return Vector<Node>, null on error
	 */
	public Vector<Node> getHexRing(int level,int u,int w) {
		Vector<Node> ans=new Vector<Node>(0);
		int d=stepDiam[level]; 

		// find <u,w> relative to supergrid verts (form <d*x,d*y>)
		int ud=(int)Math.floor((double)u/(double)d);
		int A=ud*d;
		int wd=(int)Math.floor((double)w/(double)d);
		int B=wd*d;
		
		// several situations:
		// <u,w> = vertex of supergrid; return 6 subgrid neighbors
		Node nde=null;
		if (u==A && w==B) {
			nde=getNodeLUW(level,A,B-d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A-d,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A-d,B-d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// <u,w> is on an edge of supergrid
		if (u==A) {
			nde=getNodeLUW(level,A,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A-d,B);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}

		// else on another edge
		if (w==B) {
			nde=getNodeLUW(level,A,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A,B-d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B+d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// else yet another edge
		if ((u-A)==(w-B)) {
			nde=getNodeLUW(level,A,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B+d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// interior to supergrid triangle?
		if ((w-B)>(u-A)) {
			nde=getNodeLUW(level,A,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A,B+d);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
		
		// or a second supergrid triangle? last chance
		if ((w-B)<(u-A)) {
			nde=getNodeLUW(level,A,B);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B+d);
			if (nde!=null)
				ans.add(nde);
			nde=getNodeLUW(level,A+d,B);
			if (nde!=null)
				ans.add(nde);
			return ans;
		}
			
		else // throw exception
			Oops("should have no possibilities left?");

		return ans;
	}
	
	/**
	 * Greatest common divisor of two integers 
	 * @param a int
	 * @param b int
	 * @return int
	 */
	public int GCD(int a, int b) {
		a=Math.abs(a);
		b=Math.abs(b);
	    if (b == 0) return a;
	    else return (GCD (b, a % b));
	}
	
	/**
	 * Get inversive distance between circles associated with 'Node's.
	 * @param n1 Node
	 * @param n2 Node
	 * @return double
	 */
	public double getInvDist(Node n1,Node n2) {
		Complex z1=new Complex((double)n1.u/2.0,sqrt3*(double)n1.w/2.0);
		Complex z2=new Complex((double)n2.u/2.0,sqrt3*(double)n2.w/2.0);
		double r1=(double)(n1.numDiam)/2.0;
		double r2=(double)(n2.numDiam)/2.0;
		return EuclMath.inv_dist(z1, z2,r1, r2);
	}
	
	public int getFPSize(int level) {
		int d=stepDiam[level];
		int dpre=0;
		if (level>1) // if there's a previous level
			dpre=stepDiam[level-1];
		return (int)Math.floor((Math.sqrt(d*d+dpre*dpre))/2.0);
	}
	
	// translate standard footprint
	public EdgeLink getFootprint(int level,int u,int w) {
		EdgeLink outedges=new EdgeLink();
		
		// go through footprint
		EdgeLink elink=footprint[level];
		Iterator<EdgeSimple> eit=elink.iterator();
		while (eit.hasNext()) {
			EdgeSimple edge=eit.next();
			if (isInMicro(u+edge.v,w+edge.w))
				outedges.add(new EdgeSimple(u+edge.v,w+edge.w));
		}
		return outedges;
	}
	
	/**
	 * Find the maximum distance between a circle of radius for 
	 * 'level' and one for radius one level lower (if there is one)
	 * so that the circles intersect at no more than a right angle.
	 * This is used as a side length in 'getHexCell' calls. 
	 * @return int
	 */
	public int getCellSide(int level) {
//		return stepDiam[level];
		int d=stepDiam[level];
		int dpre=0;
		if (level<levelCount) // if there's a previous level
			dpre=stepDiam[level+1];
		return (int)Math.floor((Math.sqrt(d*d+dpre*dpre))/2.0-.001);
	}
	
	/**
	 * Given microgrid point <u,w> and integer, find legal microgrid points 
	 * <a,b> forming the hex cell of 'side' length centered at <u,w> 
	 * @param side int,
	 * @param u int
	 * @param w int
	 * @return Vector<EdgeSimple>, null on error
	 */
	public EdgeLink getHexCell(int side,int u,int w) {
		EdgeLink elink=new EdgeLink();
		if (!isInMicro(u,w))
			return null;
		for (int i=0;i<=side;i++)
			for (int j=-side+i;j<=side;j++)
				if (isInMicro(u+i,w+j))
					elink.add(new EdgeSimple(u+i,w+j));
		for (int i=-side;i<0;i++)
			for (int j=-side;j<=side+i;j++)
				if (isInMicro(u+i,w+j))
					elink.add(new EdgeSimple(u+i,w+j));
		if (elink.size()==0)
			return null;
		return elink;
	}
	
	/**
	 * Scale real world location z, find associated microgrid <n,m> coords.
	 * @param z Complex
	 * @return int[2] <n,m>
	 */
	public int []getCoords(Complex z) {
		int []ans=new int[2];
		double factor=1/microScaling;
		double x=z.x*factor;
		double y=z.y*factor;
		ans[0]=(int)Math.round(x-y/sqrt3);
		ans[1]=(int)Math.round(x+y/sqrt3);
		return ans;
	}

	/**
	 * Utility to get level: usage "-L{n}". Remove first string of fsegs(0)
	 * and return n. 
	 * @param fsegs Vector<Vector<String>>
	 * @return int, -1 on error.
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
	 * Given <u,w>, are these coords for point in current microgrid?
	 * @param u int
	 * @param w int
	 * @return boolean
	 */
	public boolean isInMicro(int u,int w) {
		if (u<-microN || u> microN || w<-microN || w>microN)
			return false;
		if ((u<0 && w>(microN+u)) || (u>0 && w<(-microN+u)))
			return false;
		return true;
	}
	
	/**
	 * Get the vertex v for microgrid point <u,w>.
	 * @param u int
	 * @param w int
	 * @return int, 0 on error
	 */
	public int uw2v(int u,int w) {
		if (!isInMicro(u,w))
			return 0;
		return micro2v[u+microN][w+microN];
	}
	
	/**
	 * Given micro coords <u,w>, find indices (i,j) into 'nodeLUW[level,i,j]'
	 * @param level int
	 * @param u int
	 * @param w int
	 * @return int[2]={i,j}, null on error
	 */
	public int []micro_uw2ij(int level,int u,int w) {
		int d=stepDiam[level];
		int n=gridN[level]; // array for 'level' is (2n+1)x(2n+1)
		if (d!=GCD(d,u) || d!=GCD(d,w)) // not multiples of d?
			return null;
		int []ans=new int[2];
		ans[0]=(u/d)+n;
		ans[1]=(w/d)+n;
		return ans;
	}
	
	/**
	 * Get the 'Node' at microgrid point <u,w> in 'level' data;
	 * result may be null, but also return null if <u,w> is not
	 * in the 'level' supergrid.
	 * @param level int
	 * @param u int
	 * @param w int
	 * @return Node or null
	 */
	public Node getNodeLUW(int level,int u,int w) {
		int []ij=micro_uw2ij(level,u,w);
		if (ij==null)
			return null;
		return nodeLUW[level][ij[0]][ij[1]];
	}

	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("read_shape","filename",null,"Read a Jordan curve; "+
				"the origin should be an interior point."));
		cmdStruct.add(new CmdStruct("set_grid","[n]",null,"Create the platen microgrid with "+
				"n hex generations, default to n=24"));
		cmdStruct.add(new CmdStruct("set_jordan","['filename']",null,"Set the "+
				"region Omega; default to 'ClosedPath' "));
		cmdStruct.add(new CmdStruct("set_mM","{r R}",null,"Set min/max radius "+
				"of circles to 'r' and 'R': 0.1 and 0.5 are defaults."));
		cmdStruct.add(new CmdStruct("set_minR","{r}",null,"Set minimum radius "+
				"of circles to 'r': 0.1 is default."));
		cmdStruct.add(new CmdStruct("set_maxR","{R}",null,"Set maximum radius "+
				"of circles to 'R': 0.5 is default."));
		cmdStruct.add(new CmdStruct("set_Q","{Q}",null,"Set the maximal ratio Q between "+
				"radii of incident circles: 1.5 is default."));
		cmdStruct.add(new CmdStruct("intensity",null,null,"Set face colors based on average "+
				"intensity of its vertices"));
		cmdStruct.add(new CmdStruct("disp","-L{n} -c{} [am] [nlist]",null,"display circles, a=all, "+
				"m=marked, those in 'nlist', etc."));
		cmdStruct.add(new CmdStruct("set_nlist","-L{n} [am] [nlist] [h u w]",null,"Sets utility node list. "+
				"This selects nodes for the list (including 'nlist' so you can add), h u w to get hex "+
				"neighbors to <u,w>."));
		cmdStruct.add(new CmdStruct("process",null,null,"Under development: find circles to include "+
				"at the next step size --- must be done in order, smallest to largest"));
		cmdStruct.add(new CmdStruct("erase","-q{q}",null,"Assume Delaunay triangulation is in pack q, "+
				"this removes bdry edge (in layers) that are outside constraintBdry."));
		cmdStruct.add(new CmdStruct("put_rad","[-L{n}]",null,"set the parent packing radii for 'Node's at "+
				"given level, default to all levels."));

	}
	
	// ======================= internal class ==============================
	/** 
	 * For holding, manipulating the various parameters
	 * @author kstephe2
	 *
	 */
	class PlatenParams {
		Double minR;
		Double maxR;
		Double ratioQ;	
		public boolean intensityUp;     // is an intensity method installed?
		public Path2D.Double myOmega;
		public double extentOmega;		// rad, smallest disc about rectangle of Omega 
		
		boolean chgTrigger;		// set true if there's been a change requiring platen reset
								// only changed in reset call
		
		public PlatenParams() {
			minR=Double.valueOf(.1);
			maxR=Double.valueOf(.50);
			ratioQ=Double.valueOf(1.5);
			intensityUp=false;
			myOmega=null;
			chgTrigger=false;
		}
		
		public PlatenParams(Double mnR,Double mxR,Double ratQ,Path2D.Double jordan) {
			super();
			if (mnR!=null)
				minR=Double.valueOf(mnR);
			if (mxR!=null)
				maxR=Double.valueOf(mxR);
			if (ratQ!=null)
				ratioQ=Double.valueOf(ratQ);
			if (jordan!=null) // for now, we set myOmega to ClosedPath
				myOmega=new Path2D.Double(jordan);
		}
		
		/**
		 * Closed path 'myOmega' is currently taken from global 'ClosedPath'
		 */
		public boolean set_Omega() {
			if (CPBase.ClosedPath==null)
				return false;
			myOmega=new Path2D.Double(CPBase.ClosedPath);
			Rectangle Orect=myOmega.getBounds();
			// (x,y) is lower left corner (not upper left, due to orientation of y)
			double dist=Math.sqrt(Orect.x*Orect.x+Orect.y*Orect.y);
			double upright=Math.sqrt((Orect.x+Orect.width)*(Orect.x+Orect.width)+Orect.y*Orect.y);
			double lowleft=Math.sqrt(Orect.x*Orect.x+(Orect.y+Orect.height)*(Orect.y+Orect.height));
			double lowright=Math.sqrt((Orect.x+Orect.width)*(Orect.x+Orect.width)+(Orect.y+Orect.height)*(Orect.y+Orect.height));
			dist=(dist>upright) ? dist:upright;
			dist=(dist>lowleft) ? dist:lowleft;
			dist=(dist>lowright) ? dist:lowright;
			extentOmega=dist;
			chgTrigger=true;
			return true;
		}
		
		public void set_minR(double mR) {
			if (mR<=0) 
				return;
			if (Math.abs(minR.doubleValue()-mR)/mR>.01) { 
				minR=Double.valueOf(mR);
				chgTrigger=true;
			}
			if (maxR.doubleValue()<2.0*minR.doubleValue()) {
				maxR=Double.valueOf(2.0*minR.doubleValue());
				chgTrigger=true;
			}
		}

		public void set_maxR(double mxR) {
			if (mxR<=0) 
				return;
			if (Math.abs(maxR.doubleValue()-mxR)/mxR>.01) { 
				maxR=Double.valueOf(mxR);
				chgTrigger=true;
			}
			if (minR.doubleValue()>.5*maxR.doubleValue()) {
				minR=Double.valueOf(0.5*maxR.doubleValue());
				chgTrigger=true;
			}
		}

		public void set_Q(double Q) {
			if (Q<.05)
				Q=.05;
			if (Q>2.0)
				Q=2.0;
			if (ratioQ!=null && Math.abs(ratioQ.doubleValue()-Q)/Q<.01)
				return;   // didn't really change enough
			ratioQ=Double.valueOf(Q);
			chgTrigger=true;
		}
		
		public boolean get_trigger() {
			return chgTrigger;
		}
		
		public void set_trigger(boolean trig) {
			chgTrigger=trig;
		}
		
		public double get_minR() {
			return (double)minR;
		}
		
		public double get_maxR() {
			return (double)maxR;
		}
		
		public double get_Q() {
			return (double)ratioQ;
		}
		

		/**
		 * Message giving status
		 */
		public void status() {
			StringBuilder strbld=new StringBuilder("HexPlaten p"+packData.packNum+": ");
			if (chgTrigger)
				strbld.append(" Reset is needed. ");
			else 
				strbld.append(" No reset needed. ");
			if (myOmega==null && !set_Omega()) {
				strbld.append("Must create 'ClosedPath' for 'Omega'. ");
			}
			else 
				strbld.append("Omega is set.\n");
			strbld.append("'minR' = "+String.format("%.6f",minR.doubleValue())+
					", 'maxR' = "+String.format("%.6f",maxR.doubleValue())+
					", 'Q' = "+String.format("%.6f",ratioQ.doubleValue())+"\n");
			if (intensityType==1)
				strbld.append(" Intensity set by function. ");
			else if (intensityType==2)
				strbld.append(" Intensity set by data. ");
			else 
				strbld.append(" Intensity function not set. ");
			
			strbld.append("\n");
			CirclePack.cpb.msg(strbld.toString());
		}
		
	}
	
	// ======================= internal class ==============================
	/** 
	 * A 'Node' represents a point of the basic microgrid (before scaling). 
	 * A node location is in integer coords (n,m), where location is
	 * v = n*u + m*w where
	 * 		u = <1/2, -CPBase.sqrt32> and 
	 * 		w = <1/2, CPBase.sqrt32>.
	 * 
	 * 'numDiam' is in grid units, so diameter 1 is smallest circle possible
	 * and radius is 1/2 (in unscaled grid).
	 *   
	 * @author kstephe2, 2/2017
	 */
	class Node {
		public int myVert;		// index in 'packData'
		int u;			// u coord
		int w;			// w coord
		int numDiam;	// diameter in terms of microgrid steps
		Color color;	// 
		boolean chosen; // true if this node is a center at this level
		boolean covered;// true if this node is NOT eligible to be a center
		int mark;		
		int num;		// likely to be 6 at most
		int []flower;   // for building complex K
		
		// constructor
		public Node(int v,int uu,int ww,int nR) {
			myVert=v;
			u=uu;
			w=ww;
			numDiam=nR;
			chosen=false;
			covered=false;
			flower=null;
			num=-1;
		}
		
		/** 
		 * Find real world location
		 * @return Complex
		 */
		public Complex getZ() {
			double mrad=microScaling/2.0;
			return new Complex(mrad*((double)(u+w)),mrad*sqrt3*((double)(w-u)));
		}
		
		/**
		 * Integer diameter of largest supergrid containing this node
		 * @return int
		 */
		public int getGrid() {
			return (GCD(Math.abs(u),Math.abs(w)));
		}
		
		/**
		 * Is this node on the supergrid of diameter 'diam'?
		 * @param diam int, integer diameter
		 * @return boolean
		 */
		public boolean isOnGrid(int diam) {
			diam=Math.abs(diam);
			if (diam==GCD(diam,Math.abs(u)) && diam==GCD(diam,Math.abs(w)))
				return true;
			else
				return false;
		}
	} // end of 'Node' class
	
}