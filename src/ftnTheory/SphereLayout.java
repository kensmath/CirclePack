package ftnTheory;

import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.CPFileManager;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import listManip.NodeLink;
import math.Point3D;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.ColorUtil;
import allMains.CPBase;
import allMains.CirclePack;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.ParserException;

/**
 * Alternate procedure for laying out spherical circle packings:
 * Identify 4 'beacons', combinatorially well-distributed vertices. 
 * For each, puncture and max pack, then record the inv distances of other
 * points to that beacon. Now quadrangulate circles with respect
 * to the beacons.
 * 
 * Points of H^3 (hyp 3D) are represented points on one hyperboloid
 * sheet:  
 * 
 *    S+= {(t,x,y,z): t^2-x^2-y^2-z^2 = 1, t>0}
 *    
 * Points of S^2, the boundary, comprise the Riemann sphere
 * 
 *    S^2={(x,y,z): x^2+y^2+z^2 = 1}
 *    
 * Discs on S^2 are represented by points on a related 
 * one-sheeted hyperboloid. Discs are
 * 
 *   {(t,x,y,z): t^2-x^2-y^2-z^2 = -1}, t>0
 *   
 * Boundary of disc (t,x,y,z) is 
 * 
 *   {(a,b,c):a^2+b^2+c^2=1, ax+by+cz=t}
 *   
 * where t/(x^2+y^2+z^2)=cos(r), r = sph radius.
 *   
 * (TODO: Not sure if this gives oriented disc: how about t<0?
 * and great circles seem to be t=0.)
 *   
 * The inversive distance between discs (t,x,y,z) and
 * (s,a,b,c) is st-ax-by-cz.
 * 
 * TODO: must resolve ambiguity about orientation
 *
 * @author kstephe2 based on ideas of Edward Crane
 *
 */

public class SphereLayout extends PackExtender {
	NodeLink beacons;
	VertGPS []vertGPS;
	PackData layoutPack;
	double [][]bD;  // matrix of 4 beacon's pairwise inv distances d_ij
					//   computed from hyperbolic radii of max packings in disc.
	double [][]M;   // positioning matrix, derived by putting beacons
					//   in normalized positions based on their inversive
					//   distances. Each row is (t,x,y,z) representation of 
					//   circle, t^2-x^2-y^2-z^2 = -1. First is (0,0,0,-1), 
					//   the southern hemisphere. Second is (*,0,0,d_01),
					//   centered at north pole. Third is (*,0,*,d_02), on
					//   imaginary axis. Last is (*,*,*,d_03). 
					//   TODO: possible ambiguity regarding orientation?
					//     e.g. if 4th is on imaginary axis.
	boolean debug;  // set for debugging mode
	
	// Constructor
	public SphereLayout(PackData p) {
		super(p);
		extensionType="SPHERE_LAYOUT";
		extensionAbbrev="SL";
		toolTip="'Sphere Layout': experiment with an alternate method for "+
			"computing sphere layouts, quadrangulating using inversive distances";
		registerXType();
		if (packData.hes<=0) {
			cpCommand("geom_to_s");
		}
		if (packData.bdryCompCount>0 || packData.genus>0 || packData.euler!=2) {
			CirclePack.cpb.msg("SL Warning: this does not seem to be a topological sphere");
		}
		if (running) {
			packData.packExtensions.add(this);
		}
		
		// initiate with 4 random beacons, starting with 1. Can reset at will
		beacons=packData.antipodal_verts(null,4);
		packData.vlist=beacons.makeCopy();
		bD=new double[4][4];
		debug=true;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		if (flagSegs!=null)
			items=flagSegs.get(0);
		
		if (cmd.startsWith("colo")) {
			if(vertGPS==null) {
				CirclePack.cpb.errMsg("GPS coordinates must be set first");
				return 0;
			}
			
			// get rescale info: minima=1, get max for each beacon
			double []Max=new double[3];
			double gps=-1.0;
			for (int v=1;v<=packData.nodeCount;v++) {
				for (int j=0;j<3;j++) {
					if ((gps=vertGPS[v].coord[j])>Max[j])
						Max[j]=gps;
				}
			}
			
			// set color intensities in [0,1]: colors r g b are for b0 b1 b2, resp.  
			double []div=new double[3];
			for (int j=0;j<3;j++)
				div[j]=1.0/(Max[j]-1.0);
			for (int v=1;v<=packData.nodeCount;v++) {
				for (int j=0;j<3;j++)
					vertGPS[v].colIntensity[j]=(Max[j]-vertGPS[v].coord[j])*div[j];
			}
			
			// ================ choice of colors ===================
			// adjust to combine: E.g. set b = 255*sqrt{(1-r)*(1-g)*b}
			for (int v=1;v<=packData.nodeCount;v++) {
				double []tmpI=vertGPS[v].colIntensity;
				int r=(int)(255.0*Math.sqrt((1.0-tmpI[1])*(1.0-tmpI[2])*vertGPS[v].colIntensity[0]));
				int g=(int)(255.0*Math.sqrt((1.0-tmpI[0])*(1.0-tmpI[2])*vertGPS[v].colIntensity[1]));
				int b=(int)(255.0*Math.sqrt((1.0-tmpI[0])*(1.0-tmpI[1])*vertGPS[v].colIntensity[2]));
				vertGPS[v].color=new Color(r,g,b);
			}
			
			// for certainty, reset colors for beacons b0,b1,b2
			vertGPS[beacons.get(0)].color=Color.red;
			vertGPS[beacons.get(1)].color=Color.green;
			vertGPS[beacons.get(2)].color=Color.blue;
			
			// record color in packData
			// TODO: should be able to clone color more easily
			for (int v=1;v<=packData.nodeCount;v++) {
				packData.setCircleColor(v,ColorUtil.cloneMe(vertGPS[v].color));
			}
		}
		
		if (cmd.startsWith("set_beac")) {
			NodeLink bcns=null;
			if (items!=null) {
				bcns=new NodeLink(packData,items);
				if (bcns.size()>4)
					bcns=null;
			}
			
			beacons=packData.antipodal_verts(bcns,4);
			vertGPS=null;
			M=null;
			bD=null;
			packData.vlist=beacons.makeCopy();
			return 1;
		}
		
		else if (cmd.startsWith("set_GPS")) {
			if (beacons==null || beacons.size()!=4)
				throw new ParserException("set beacons first");
			int []bea=new int[4];
			for (int j=0;j<4;j++)
				bea[j]=beacons.get(j);
			
			// See if a cycle count is specified
			int cycles=CPBase.RIFFLE_COUNT;
			int cyc;
			try {
				cyc=Integer.parseInt(items.get(0));
				items.remove(0);
			} catch (Exception ex) {
				cyc=cycles;
			}
			cycles=cyc;
			
			// Check for input packings (instead of computing)
			PackData []puncturedPack=new PackData[4];
			String baseName=null;
			String fname = null;
			try {
				baseName=new String(items.get(0));
				items.remove(0);
			} catch (Exception ex) {}
			
			// prepare storage space
			vertGPS=null;
			vertGPS=new VertGPS[packData.nodeCount+1];
			for (int v=1;v<=packData.nodeCount;v++)
				vertGPS[v]=new VertGPS(v);
			// may want to re-establish old results to do further computation
			
			// Now fill in the 'coord' data
			int nodes=packData.nodeCount;
			bD=new double[4][4];
			
			// Now either READ 4 punctured packings or compute them
			// Note: must be punctured at the appropriate beacon vertex.
			boolean gotThem=false;
			if (baseName!=null) {
				gotThem=true;
				puncturedPack=new PackData[4];
				for (int n=0;(n<4 && gotThem);n++) {
					File dir=CPFileManager.PackingDirectory;
					fname=new String(baseName+n+".p");
					try {
						BufferedReader fp=
							CPFileManager.openReadFP(dir,fname,false);
						int flags=puncturedPack[n].readpack(fp,fname);
						// need combinatorics and radii at a minimum
						if (flags<=0 || (flags & 00011)!=00011) 
							gotThem=false;
					} catch(Exception ex) {
						throw new InOutException("reading failed for "+dir+File.separator+fname);
					}
				}
				if (gotThem) 
			   		CirclePack.cpb.msg("Read in 4 packings with radii");
				else
					throw new InOutException("Failed to get 4 packings '"+fname+"?.p'");
			}
			
			// compute here (and optionally STORE in 'SL_pack?.p')
			else {

				for (int b = 0; b < 4; b++) {
//					CirclePack.cpb.msg("starting GPS for vert " + bea[b]);
					puncturedPack[b] = packData.copyPackTo();
					puncturedPack[b].puncture_vert(bea[b]);
					if (puncturedPack[b].packDCEL==null)
						puncturedPack[b].complex_count(false);
					puncturedPack[b].geom_to_h();
					puncturedPack[b].set_aim_default();
					NodeLink blist = new NodeLink(puncturedPack[b], "b");
					Iterator<Integer> blt = blist.iterator();
					while (blt.hasNext()) {
						int k = blt.next();
						puncturedPack[b].setRadiusActual(k, 10.0);
					}
					int repackCount = puncturedPack[b].repack_call(cycles,false,
							false);

					if (debug) {
						File dir = CPFileManager.PackingDirectory;
						fname = new String("SL_pack" + b + ".p");
						BufferedWriter fp = CPFileManager.openWriteFP(dir,
								false, fname, false);
						try {
							puncturedPack[b].writePack(fp, 0017, false); // cgri options
						} catch (Exception ex) {
							throw new InOutException("write of 'wP' failed");
						}
						CirclePack.cpb.msg("Wrote temp packing to "
								+ dir.getPath() + File.separator + fname);
					}

					// store the inversive distances
					for (int v = 1; v < bea[b]; v++) {
						vertGPS[v].coord[b] = HyperbolicMath
								.x_rad2invdist(puncturedPack[b].getRadius(v)); 
					}
					for (int v = bea[b] + 1; v <= nodes; v++) { // indices are
																// shifted due
																// to puncture
						vertGPS[v].coord[b] = HyperbolicMath
								.x_rad2invdist(puncturedPack[b].getRadius(v-1));
					}
					vertGPS[bea[b]].coord[b] = -1.0;
					for (int j = 0; j < 4; j++) {
						if (j != b) {
							if (bea[j] < bea[b]) {
								bD[j][b] += HyperbolicMath
										.x_rad2invdist(puncturedPack[b].getRadius(bea[j]));
								if (Double.isNaN(bD[j][b]))
									throw new DataException("bD[" + j + "]["
											+ b + "] is NaN");
							} else {
								bD[j][b] += HyperbolicMath
										.x_rad2invdist(puncturedPack[b].getRadius(bea[j] - 1));
								if (Double.isNaN(bD[j][b]))
									throw new DataException("bD[" + j + "]["
											+ b + "] is NaN");
							}
						}
					}
					CirclePack.cpb.msg("finished GPS, vert " + bea[b]
							+ ", count " + repackCount);
				}
			}
			
			// bD should be symmetric: make it symmetric here to accomodate
			//   roundoff error. 
			// TODO: can put quality check here
			for (int j=0;j<4;j++) {
				for (int b=0;b<j;b++) {
					bD[j][b] += bD[b][j];
					bD[j][b] /=2.0;
					bD[b][j]=bD[j][b];
				}
				bD[j][j]=-1.0;
			}
			
			// TODO: Seems there can be two levels of ambiguity regarding
			//       orientation. Once b0, b1, b2 are in standard position,
			//       there should be two positions for b3.
			// But also, if b3 is on the circle through b0, b1, and b2, then
			//     each individual circle placement may have ambiguity.
			
			return 1;
		}
		
		else if (cmd.startsWith("set_M")) {
			
			// set positioning matrix M: rows are t, x, y, z coords

			M=new double[4][4];
			M[0][3]=-1.0;
			M[1][3]=bD[1][0];
			M[2][3]=bD[2][0];
			M[3][3]=bD[3][0];
			M[1][0]=Math.sqrt(M[1][3]*M[1][3]-1.0);
			M[2][0]=(bD[0][1]*bD[0][2]+bD[1][2])/(M[1][0]);
			M[2][2]=Math.sqrt(1.0+M[2][0]*M[2][0]-bD[0][2]*bD[0][2]);
			M[3][0]=(bD[0][1]*bD[0][3]+bD[1][3])/(M[1][0]);
			M[3][2]=(M[2][0]*M[3][0] -bD[2][3]-bD[0][2]*bD[0][3])/M[2][2];
			M[3][1]=Math.sqrt(1.0+M[3][0]*M[3][0]-M[3][2]*M[3][2]-bD[0][3]*bD[0][3]);
			
			// check that inner products between rows are the given inversive distances
//			double [][]MIP=new double[4][4];
//			for (int i=0; i<4; i++){
//				for (int j=0; j<4; j++){
//					MIP[i][j]=M[i][0]*M[j][0]+M[i][1]*M[j][1] + M[i][2]*M[j][2] - M[i][3]*M[j][3];
//				}
//			}
			for (int ii=0;ii<4;ii++) {
				for (int jj=0;jj<=ii;jj++)
					if (Double.isNaN(M[ii][jj]))
					throw new DataException("M["+ii+"]["+jj+"] is NaN");
			}

			// M[3][1] entry causes ambiguity:
			
			// If M[3][1] is essentially 0, then beacons all lie on imaginary axis,
			//    leading to ambiguity in placement --- not global ambiguity, but
			//    circle-by-circle ambiguity, which is worse.
			if (Math.abs(M[3][1])<.00000001)
				CirclePack.cpb.msg("Note: M matrix in 'SphereLayout' may lead to ambiguous placements");
			
			// Otherwise, sign of M[3][1] affects global orientation. Find oriented triple {b3,v,w},
			//   locate circle centers (should be in one hemisphere since b0,b1,b2 are on y-axis). 
			//   Find b3, v, w centers, compute a X b, a = vector b3 to v,  b = vector of b3 w.
			//   Dot product of b3 with a X b should be positive.
			else {
				int b3=beacons.get(3);
				int v=packData.kData[beacons.get(3)].flower[0];
				int w=packData.kData[beacons.get(3)].flower[1];
				
				// get center for b3
				VertGPS gps=vertGPS[b3];
				double z=gps.coord[0];
				double t=(z*M[1][3]+gps.coord[1])/(M[1][0]);
				double y=(t*M[2][0]-gps.coord[2]-z*M[2][3])/(M[2][2]);
				double x=(t*M[3][0]-gps.coord[3]-y*M[3][2]-z*M[3][3])/(M[3][1]);
				double nm=Math.sqrt(x*x+y*y+z*z);
				Point3D ptb3 =new Point3D(x/nm,y/nm,z/nm);

				// get center for v
				gps=vertGPS[v];
				z=gps.coord[0];
				t=(z*M[1][3]+gps.coord[1])/(M[1][0]);
				y=(t*M[2][0]-gps.coord[2]-z*M[2][3])/(M[2][2]);
				x=(t*M[3][0]-gps.coord[3]-y*M[3][2]-z*M[3][3])/(M[3][1]);
				nm=Math.sqrt(x*x+y*y+z*z);
				Point3D ptv =new Point3D(x/nm,y/nm,z/nm);
				
				// get center for w
				gps=vertGPS[w];
				z=gps.coord[0];
				t=(z*M[1][3]+gps.coord[1])/(M[1][0]);
				y=(t*M[2][0]-gps.coord[2]-z*M[2][3])/(M[2][2]);
				x=(t*M[3][0]-gps.coord[3]-y*M[3][2]-z*M[3][3])/(M[3][1]);
				nm=Math.sqrt(x*x+y*y+z*z);
				Point3D ptw =new Point3D(x/nm,y/nm,z/nm);
				
				Point3D bv=Point3D.displacement(ptb3,ptv);
				Point3D bw=Point3D.displacement(ptb3,ptw);
				Point3D cross=Point3D.CrossProduct(bv, bw);
				double dot=Point3D.DotProduct(ptb3, cross);
				
				// if misoriented, then switch entry of M
				if (dot<0)
					M[3][1]=-M[3][1];
			}
			
			return 1;
		}
		
		else if (cmd.startsWith("layout")) {
			int count=0;
			// set our matrix
			cmdParser("set_M",null);
			layoutPack=packData.copyPackTo();
			
			// read desired vertices, default to 'all'
			NodeLink vertlist=new NodeLink(packData,items);
			Iterator<Integer> vlst=vertlist.iterator();

			// compute circle rep as (t,x,y,z) and store sph centers/radii
			// M has rows tj,xj,yj,zj, j=0,1,2,3, where (tj,xj,yj,zj) represents
			//   the j_th beacon in normalized position. For each circle we
			//   have its GPS coords, we solve 
			//         M[t -x -y -z]^T=[GPS[0],GPS[1],GPS[2],GPS[3]]^T
			//   to get the circle (t,x,y,z).
			VertGPS gps=null;
			while (vlst.hasNext()) {
				int v=vlst.next();
				gps=vertGPS[v];
				double z=gps.coord[0];
				double t=(z*M[1][3]+gps.coord[1])/(M[1][0]);
				double y=(t*M[2][0]-gps.coord[2]-z*M[2][3])/(M[2][2]);
				double x=(t*M[3][0]-gps.coord[3]-y*M[3][2]-z*M[3][3])/(M[3][1]);
				
				// TODO: why is this fudge needed? (We need to avoid NaN)
				// maybe need better way to take care of inaccuracies.
				t=Math.sqrt(x*x+y*y+z*z-1.0);
				
				// store spherical centers, radii in layoutPack
				double R=Math.sqrt(x*x+y*y+z*z);
				layoutPack.setCenter(v,SphericalMath.proj_vec_to_sph(x,y,z));
				double rho=Math.acos(t/R);
				if (Double.isNaN(rho)) {
					CirclePack.cpb.errMsg("vertex "+v+": t is "+t+" and R is "+R);
				}
				layoutPack.setRadius(v,rho);
				count++;
			}
			
			// TODO: orientation should be ambiguous, need way to 
			//       determine if it is correct or not. Perhaps lay out
			//       one face of b3 and see if it is correctly oriented.
			//       Or, perhaps go back and pick a different b3.
			
			return count;
		}
		
		else if (cmd.startsWith("go")) {
			int count=0;
			if (beacons==null || beacons.size()!=4) 
				cmdParser("set_beacons",flagSegs);
			if ((count=cmdParser("set_GPS",flagSegs))!=0 && cmdParser("layout",flagSegs)!=0) {
				CirclePack.cpb.msg("'go' seems to have succeeded; use '|sl| copy' to copy results");
				return count;
			}
			CirclePack.cpb.errMsg("something went wrong in 'go'");
			return 0;
		}
		
		// ========== copy <pnum> 
		else if (cmd.startsWith("copy")) { // copy 'outputData' in some pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				if (pnum==packData.packNum)
					return 0;
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS!=null) {
					cpS.swapPackData(layoutPack,false);
				}
			} catch (Exception ex) {
				return 0;
			}
			return 1;
		}	
		
		// ========== queries
		else if (cmd.startsWith("?beac")) {
			if (beacons==null || beacons.size()!=4) { 
				CirclePack.cpb.msg("beacons not yet set");
				return 1;
			}
			CirclePack.cpb.msg("beacons are: "+beacons.get(0)+", "+beacons.get(1)+", "+
					beacons.get(2)+", "+beacons.get(3));
			return 1;
		}
		
		return super.cmdParser(cmd, flagSegs);
		
	}

	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("copy","{p}",null,"copy 'layoutPack' to pack p."));
		cmdStruct.add(new CmdStruct("set_beacons","[v1,v2,v3,v4]",null,"Choose beacons: "+
			"4 are needed, from 0 to 4 may be specified as arguments"));
		cmdStruct.add(new CmdStruct("set_GPS","[n] [basename]",null,"Compute the inversive distances; optional n=cycles, "
				+"and/or 'basename' to use existing packings 'basename[0-3].p'"));
		cmdStruct.add(new CmdStruct("set_M",null,null,"Compute M, the beacon matrix (automatic in 'set_GPS'"));
		cmdStruct.add(new CmdStruct("layout","{v..}",null,"Do the layout for the given vertices"));
		cmdStruct.add(new CmdStruct("go","[b0,..]",null,"do everything through layout; optionally "+
				"specify some or all beacons b0,...."));
		cmdStruct.add(new CmdStruct("color",null,null,"interpolate Color using inv distances, rgb = b0, b1, b2"));
	}
	
}

class VertGPS {
	double []coord; 		// coord[j] is inversive distance to beacon[j]
	double []colIntensity;  // scaled intensities to [0,1] for r,g,b
	Color color;			// stored color: r,g,b for b0,b1,b2, resp.
	int vert;
	
	public VertGPS(int v) {
		vert=v;
		coord=new double[4];
		colIntensity=new double[3];
		color=ColorUtil.getFGColor();
	}
	
	public double getGPS(int j) {
		if (j<0 || j>3)
			throw new DataException("VertCPS has only 4 coords");
		return coord[j];
	}
	
	public double getIntensity(int j) {
		if (j<0 || j>2)
			throw new DataException("Only 3 intensities");
		return colIntensity[j];
	}
		
}
