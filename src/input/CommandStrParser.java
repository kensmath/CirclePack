package input;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JFrame;

import JNI.DelaunayData;
import JNI.ProcessDelaunay;
import allMains.CPBase;
import allMains.CirclePack;
import canvasses.ActiveWrapper;
import canvasses.CursorCtrl;
import canvasses.DisplayParser;
import canvasses.MainFrame;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import circlePack.ShellControl;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import complex.MathComplex;
import cpContributed.BoundaryValueProblems;
import cpContributed.CurvFlow;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.RawManip;
import dcel.Schwarzian;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.MobException;
import exceptions.ParserException;
import exceptions.VarException;
import ftnTheory.AffinePack;
import ftnTheory.BeltramiFlips;
import ftnTheory.BeurlingFlow;
import ftnTheory.BrooksQuad;
import ftnTheory.ComplexAnalysis;
import ftnTheory.ConformalTiling;
import ftnTheory.Erf_function;
import ftnTheory.Exponential;
import ftnTheory.FeedBack;
import ftnTheory.FlipStrategy;
import ftnTheory.GenModBranching;
import ftnTheory.Graphene;
import ftnTheory.HarmonicMap;
import ftnTheory.HypDensity;
import ftnTheory.JammedPack;
import ftnTheory.MeanMove;
import ftnTheory.Necklace;
import ftnTheory.Percolation;
import ftnTheory.PolyBranching;
import ftnTheory.ProjStruct;
import ftnTheory.RationalMap;
import ftnTheory.RiemHilbert;
import ftnTheory.SchwarzMap;
import ftnTheory.ShapeShifter;
import ftnTheory.SphereLayout;
import ftnTheory.SpherePack;
import ftnTheory.TileColoring;
import ftnTheory.TorusEnergy;
import ftnTheory.WeldManager;
import ftnTheory.WordWalker;
import ftnTheory.iGame;
import geometry.CircleSimple;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.NSpole;
import geometry.SphericalMath;
import komplex.EdgeSimple;
import komplex.Embedder;
import komplex.HexPaths;
import komplex.Triangulation;
import listManip.BaryCoordLink;
import listManip.BaryLink;
import listManip.DoubleLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.TileLink;
import listManip.VertexMap;
import math.Matrix3D;
import math.Mobius;
import microLattice.MicroGrid;
import microLattice.Smoother;
import packing.CPdrawing;
import packing.Interpolator;
import packing.PackCreation;
import packing.PackData;
import packing.PackExtender;
import packing.PackMethods;
import packing.QualMeasures;
import packing.ReadWrite;
import packing.TorusData;
import panels.OutPanel;
import panels.PathManager;
import panels.ScreenShotPanel;
import posting.PostFactory;
import posting.PostParser;
import random.RandomTriangulation;
import rePack.EuclPacker;
import rePack.GOpacker;
import rePack.HypPacker;
import rePack.SphPacker;
import script.ScriptBundle;
import tiling.TileData;
import util.CallPacket;
import util.DispFlags;
import util.PathBaryUtil;
import util.PathUtil;
import util.ResultPacket;
import util.SphView;
import util.StringUtil;
import util.TriAspect;
import util.UtilPacket;
import util.ViewBox;
import util.ZRhold;
import widgets.CreateSliderFrame;
import widgets.SliderFrame;
import workshops.LayoutShop;

/**
 * This class handles parsing of individual commands for CirclePack.
 * These are generally sent from 'TrafficCenter'. 
 * (That's where preprocessing takes place: splitting 
 * command strings, catching "-p" flags, repeats, variable 
 * creation/substition, 'PackExtender' handoffs, management
 * of threads, etc. takes place.)
 * 
 * author: Ken Stephenson
 * 
 */
public class CommandStrParser {

  public enum Energy {COULOMB,L2,LOG,MIN_DIST}; // for 'PointEnergies'

  public static PostFactory pF; // 'PostFactory' updated externally.
  // TODO: make pF safer for threads
  public static final double LAYOUT_THRESHOLD=.00001; // layouts quality

  /**
   * Send on to 'jexecute' using active packing.
   * @param s, command string (see limitations)
   * @return 0 on error or no action
   */
  public static int jexecute(String s) {
	  return jexecute(CirclePack.cpb.getActivePackData(),s);
  }
  
  /**
	 * This is where individual commands are analyzed 
	 * and sent to appropriate Java routines. 
	 * 
	 * Commands arriving are individual commands: 
	 * preprocessing leaves
	 *   no ';' separators:
	 *   no 'for' or 'FOR' loops:
	 *   no 'delay's:
	 *   no [n] named commands to resolve:
	 *   no '!!' repeats to resolve:
	 *   no '|pe|' 'PackExtender' calls:
	 *   no '-p' flag (already caught):
	 *   
	 * This routine handles housekeeping, separating 
	 * cmd and flag sequences, then catching a small 
	 * number of certain commands. Note that there 
	 * is a call to 'packExecute' for commands 
	 * requiring pack status. Commands not requiring 
	 * pack status, are then processed in a switch 
	 * based on the command's first letter. 
	 * 
	 * @param PackData packData
	 * @param String cmdstr, cmd and flag sequences
	 * @return 0 on error or no action
	 */
  public static int jexecute(PackData packData, String cmdstr) {
	  
	  // initial checks
	  if (cmdstr==null) 
		  return 0;
	  if (cmdstr.contains("-p")) 
		  throw new ParserException("'jexecute': '-p' flag "
		  		+ "should have been handled before the call");

	  // catch variables, reconstitute parens/brackets, etc.
	  Vector<String> allitems=StringUtil.string2vec(cmdstr,true);
	  String cmd=(String)allitems.get(0);
	  
      // split off processing for query messages
      if (cmd.charAt(0)=='?') {
    	  return QueryParser.processQuery(packData,allitems,true);
      }
      
	  /* NOTE: Vector 'flagSegs' will hold only the flag 
	   * strings occurring after the command --- the 
	   * command itself is 'cmd' */
      allitems.remove(0); // 'cmd' is separated off
	  Vector<Vector<String>> flagSegs=StringUtil.flagSeg(allitems);
	  Vector<String> items=new Vector<String>(0);
	  int count=0;
      if (flagSegs.size()>0)
    	  items=flagSegs.get(0);

	  // 'fix' is deprecated
	  if (cmd.startsWith("fix")) 
		  cmd=new String("layout");

	  // "Cleanse" all packings
      if (cmd.startsWith("Clean")) { 
    	  for (int i=0;i<CPBase.NUM_PACKS;i++) {
    		  PackData pdata=CPBase.packings[i];
    		  CPdrawing cpd=CPBase.cpDrawing[i];
    		  // TODO: want to white out underlying canvas to avoid flashing
    		  if (pdata.status) {
    			  // trash any extensions
    			  pdata.packExtensions=new Vector<PackExtender>(2); 
    			  // put new packing in place
    			  PackData newP=CPBase.packings[i]=new PackData(i);
    			  newP.cpDrawing=CPBase.cpDrawing[i];
    			  newP.cpDrawing.setPackData(newP);
    			  newP.cpDrawing.updateXtenders();
    			  cpd.emptyScreen();
    			  cpd.updateXtenders();
    			  count++;
    		  }
    	  }
    	  PackControl.mapPairFrame.setTeleState(true);
    	  return 1;
      }
      
	  if (cmd.startsWith("act")) {
		  try {
			  int newpnum=Integer.parseInt(items.get(0));
			  if (newpnum<0 || newpnum>=CPBase.NUM_PACKS) 
				  return 0;
			  if (CPBase.GUImode!=0)
				  PackControl.switchActivePack(newpnum);
			  else 
				  ShellControl.switchActivePack(newpnum);
			  return 1;
		  } catch (Exception ex) {
		  	return 0;
		  }
	  }
	  
      // Change MainFrame or PairedFrame sizes
	  // ========= chgScreen/Paired =======
	  if (cmd.startsWith("chg")) {
		  int widehigh=200; // safety default
		  try {
			  widehigh=Integer.parseInt(items.get(0));
		  } catch (Exception ex) {
			  widehigh=200;
		  }

		  // TODO-layout: used to do 'pack()' here.
		  
		  // reset all Main canvas size, hence all BufferedImage's
		  if (cmd.charAt(3)=='S') {
			  if (widehigh<PackControl.MinActiveSize) 
				  widehigh=PackControl.MinActiveSize;
			  if (widehigh>PackControl.MaxActiveSize) 
				  widehigh=PackControl.MaxActiveSize;
			  MainFrame.setCanvasDim(widehigh,widehigh);
			  PackControl.activeFrame.layMeOut();
			  return 1;
		  }
		  
		  // else reset the PairedFrame canvas sizes
		  if (widehigh<PackControl.MinMapSize) widehigh=PackControl.MinMapSize;
		  if (widehigh>PackControl.MaxMapSize) widehigh=PackControl.MaxMapSize;
		  PackControl.mapPairFrame.layMeOut(new Dimension(widehigh,widehigh));
		  return 1;
	  } // done with screen size changes
	  
	  /* =============================================================
	   * First, check for certain commands with 
	   * multiple names
	   */
	  
	  // 
	  // ========= read_CT ====== (tiling of compact surface, 7/2017)
	  // Intended to read "glass" combinatorial data involving positions of 
	  //    atoms or reduced networks on a torus. For "jammed"  Idea is 
	  //    to read flowers and create a conventional packing, putting 
	  //    a barycenter in each tile, and
	  //    from that creating a standard 'tiling', as via the "pave" command.
	  if (cmd.startsWith("read_CT") || cmd.startsWith("Read_CT")) {

		  boolean debug=false;
		  int sz=items.size();
		  if (sz==0) 
			  return 0;

		  // first, get the edges
    	  // file name should be last in last flag segment
		  String filename=flagSegs.lastElement().lastElement();
		  File dir=CPFileManager.PackingDirectory;
		  if (cmd.charAt(0)=='R') {
			  if (filename.startsWith("~/")) 
				  filename=new String(CPFileManager.HomeDirectory+
						  File.separator+filename.substring(2));
			  dir=new File(filename);
			  filename=dir.getName();
			  dir=new File(dir.getParent());
		  }
		  
		  // -s flag means to read from script, -q{n} flag says to also
		  //     create its simple packing in pack n (though this depends
		  //     on the tiling's dual being trivalent).
    	  boolean script_flag=false;
    	  // if this is set to legal pack, store simple pack there
    	  int simplepack=-1; 
    	  Iterator<Vector<String>> fseg=flagSegs.iterator();
		  while (fseg.hasNext()) {
			  try {
				  items=fseg.next();
				  String str=(String)items.get(0);

				  if (StringUtil.isFlag(str)) {
					  if (str.equals("-s")) {
			    		  script_flag=true;
			    	  }
					  else if (str.startsWith("-q")) {
						  int qnum=StringUtil.qFlagParse(str);
						  if (qnum<0) {
							  CirclePack.cpb.errMsg("Failed to create "
							  		+ "simple packing in 'read_CT'");
						  }
						  else
							  simplepack=qnum;
					  }
				  }
			  } catch(Exception ex) {
				  throw new ParserException("flag error :"+ex.getMessage());
			  }
		  } // done getting flags

		  BufferedReader fp=null;
		  fp=CPFileManager.openReadFP(dir,filename,script_flag);
		  
		  // TODO: should allow file to be a list of pairs forming the edges

		  // get the number of vertices
		  String line=StringUtil.ourNextLine(fp);
		  StringTokenizer tok = new StringTokenizer(line);
		  int tc=tok.countTokens();
		  if (tc>2) {
			  CirclePack.cpb.myErrorMsg("read_CT file '" + filename + 
					  "' must start with integer 'n' (vertex count) or "+
					  " with 'CHECKCOUNT: n'");
			  try {
				  fp.close();
			  } catch(Exception cex) {}
			  return 0;
		  }
		  String kywd=tok.nextToken();
		  int vCount=0;
		  try {
			  // need to flush CHECKCOUNT, NODECOUNT, etc., look for count
			  while (vCount==0) {
				  try {
					  vCount=Integer.parseInt(kywd);
				  } catch(Exception ex) {
					  kywd=tok.nextToken();
				  }
			  }
		  } catch(Exception ex) {
			  CirclePack.cpb.myErrorMsg("'read_CT' failed to find count of nodes");
			  try {
				  fp.close();
			  } catch(Exception cex) {}
			  return 0;
		  }
		  if (vCount==0) {
			  CirclePack.cpb.myErrorMsg("'read_CT' failed to find count of nodes");
			  try {
				  fp.close();
			  } catch(Exception cex) {}
			  return 0;
		  }
		  int [][]bouquet=new int[vCount+1][];
		  
		  for (int n=1;n<=vCount;n++) {
			  line = StringUtil.ourNextLine(fp);
			  try {
				  tok = new StringTokenizer(line);
				  int indx=Integer.parseInt(tok.nextToken()); // get index
				  int num=tok.countTokens(); // count the rest
				  Vector<Integer> bvec=new Vector<Integer>(1);
//				  bouquet[indx]=new int[num+1];
				  for (int j=0;j<num;j++) {
					  int v = Integer.parseInt((String) tok.nextToken());
					  bvec.add(Integer.valueOf(v));
//					  bouquet[indx][j]=v;
				  }
				  // close up (TODO: data should be required to close up itself)
				  if (bvec.lastElement().intValue()!=bvec.firstElement().intValue())
					  bvec.add(Integer.valueOf(bvec.get(0)));
				  bouquet[indx]=new int[bvec.size()];
				  Iterator<Integer> bit=bvec.iterator();
				  int tick=0;
				  while(bit.hasNext()) {
					  bouquet[indx][tick++]=bit.next();
				  }
//				  if (bouquet[indx][num-1]!=bouquet[indx][0])
//					  bouquet[indx][num]=bouquet[indx][0]; 
			  } catch (Exception ex) {
				  CirclePack.cpb.myErrorMsg("Usage: read_CT form "
				  		+ "is 'CHECKCOUNT: <n>'");
				  try {
					  fp.close();
				  } catch(Exception cex) {}
				  return 0;
			  }
		  } // end of reading flowers
		  try {
			  fp.close();
		  } catch(Exception cex) {}

		  // create the PackDCEL, add all normal face barycenters
		  
		  // To see the faces, listed by smallest vertex; // debug=true; 
		  if (debug) {
			  System.out.println("degugging 'bouquet':\n");
			  for (int v=1;v<=vCount;v++) {
				  int []flower=bouquet[v];
				  for (int j=0;j<flower.length;j++) {
					  
					  // get face containing <v,w>; keep if v is smallest index
					  int []fverts=PackDCEL.getFace(bouquet, v,flower[j]);
					  int n=fverts.length;
					  for (int k=0;k<n && fverts!=null;k++)
						  if (fverts[k]<v)
							  fverts=null;
					  
					  // if kept, print
					  if (fverts!=null) {
						  StringBuilder strbld=new StringBuilder();
						  for (int k=0;k<n;k++)
							  strbld.append(fverts[k]+" ");
						  strbld.append("\n");
						  System.out.println(strbld.toString());
					  }
				  }
			  }
		  }

		  // create pDCEL and a new 'PackData' from it
		  PackDCEL pDCEL = CombDCEL.getRawDCEL(bouquet);
		  PackData newPack=new PackData(null);
		  pDCEL.fixDCEL(newPack);
		  newPack.set_aim_default();
		  int origVCount=pDCEL.vertCount;

// debugging: 
		  // read_CT was specialized for working with Varda Hagh
		  // and should be moved to a flagged option in normal reading
		  
		  // add a barycenter to every face (with triangular or not)
		  if (!debug) { // to avoid barycenter, debug=true;
			  ArrayList<combinatorics.komplex.DcelFace> farray=
					  new ArrayList<combinatorics.komplex.DcelFace>(); 
			  for (int f=1;f<=pDCEL.intFaceCount;f++) 
				  farray.add(pDCEL.faces[f]); 
			  int ans=RawManip.addBaryCents_raw(pDCEL,farray);
			  if (pDCEL==null || ans==0) {
				  CirclePack.cpb.myErrorMsg("Failed to get initial DCEL, "+
						  "or failed to add barycenters to faces");
				  return 0;
			  }
			  newPack.packDCEL.fixDCEL(newPack);
		  }
		  
		  if (newPack!=null && newPack.status==true && newPack.nodeCount>3) {
			  CirclePack.cpb.msg("Have replaced packing with new "+
					  "one derived from '"+filename+"'.");
			  int pnum=packData.packNum;
			  packData=CirclePack.cpb.swapPackData(newPack,pnum,false);
			  count=packData.nodeCount;
			  if (debug)
				  return count;
			  
			  try {
				  for (int i=1;i<=origVCount;i++)
					  newPack.setVertMark(i,1);
			  } catch (Exception ex) {
				  CirclePack.cpb.errMsg("error in marking network vertices");
			  }
			  
			  // 'pave' to get tilings
			  TileData td=TileData.paveMe(packData,origVCount+1);
    		  if (td==null) {
    			  CirclePack.cpb.errMsg("'pave' failed with new "
    			  		+ "packing from 'read_CT'");
    			  return 0;
    		  }
    		  td.packData=packData;
    		  packData.tileData=td;
//    		  CommandStrParser.jexecute(packData,"layout -F");
    		  packData.set_aim_default();
    		  
    		  // if -q{n} flag was set, also create simple circle packing in pn.
    		  if (simplepack>=0) {
    			  try {
    				  newPack=TileData.tiles2simpleCP(packData.tileData);
        			  newPack.set_aim_default();
        			  if (newPack!=null && newPack.status) {
        				  CirclePack.cpb.swapPackData(newPack,simplepack,false);
        			  }
        			  else
            			  CirclePack.cpb.errMsg("failed to create "
            			  		+ "simple packing for p"+simplepack+" in 'read_CT'");
    			  } catch(CombException cex) {
    				  CirclePack.cpb.errMsg("The companion 'simple' "
    				  		+ "packing in p"+simplepack+" has failed; "
    						+ "the tiling dual must be trivalent for "
    						+ "this to work.");
    			  }
    		  } 
			  return count;
		  }

		  return 0;
	  }
	  
      // ========= read_path =====
	  if (cmd.startsWith("read_path") || cmd.startsWith("infile_path") 
			  || cmd.startsWith("Read_p")) {

		  int sz=items.size();
		  if (sz==0) return 0;

		  // in script with 'infile' or flag '-s'
    	  boolean script_flag=false;
    	  if (cmd.charAt(0)=='i') 
    		  script_flag=true;
		  
    	  // check for script and/or append
		  boolean append=false;
		  String str=items.get(0);
		  if (StringUtil.isFlag(str)) {
			  if (str.contains("s"))
				  script_flag=true;
			  if (str.contains("a"))
				  append=true;
		  }

    	  // file name should be last in last flag segment
		  String filename=flagSegs.lastElement().lastElement();
		  File dir=CPFileManager.PackingDirectory;
		  if (cmd.charAt(0)=='R') {
			  if (filename.startsWith("~/")) 
				  filename=new String(CPFileManager.
						  HomeDirectory+File.separator+filename.substring(2));
			  dir=new File(filename);
			  filename=dir.getName();
			  dir=new File(dir.getParent());
		  }

    	  Path2D.Double gpath=PathManager.readpath(dir,filename,script_flag);
    	  if (gpath==null) 
    		  return 0;
    	  if (append && CPBase.ClosedPath!=null) {
				CPBase.ClosedPath.append(gpath.getPathIterator(null),false);
				CirclePack.cpb.msg("Appended new curve to 'ClosedPath'");
    	  }
    	  else {
    		  CPBase.ClosedPath=gpath;
    		  CirclePack.cpb.msg("Loaded a new 'ClosedPath'");
    	  }
    	  return 1;
      }
			  
	  // ====== read === infile_read ===== Read ===== 
	  else if (cmd.startsWith("read") || cmd.startsWith("infile_read") || 
			  cmd.startsWith("Read")) { // read a packing file
		  // in script with 'infile' or flag '-s'
		  int sz=items.size();
		  if (sz==0) 
			  return 0;
    	  boolean script_flag=false;
    	  if (cmd.charAt(0)=='i') 
    		  script_flag=true;
    	  if (sz>1 && items.get(0).equals("-s")) {
    		  items.remove(0);
    		  script_flag=true;
    	  }

		  String filename=StringUtil.reconItem(items);
		  Triangulation tri=null;
		  int hes=0;
		  BufferedReader fp=null;
		  
			try {
				File dir = CPFileManager.PackingDirectory;
				if (cmd.charAt(0) == 'R') {
					if (filename.startsWith("~/"))
						filename = new String(CPFileManager.HomeDirectory
								+ File.separator + filename.substring(2));
					dir = new File(filename);
					filename = dir.getName();
					dir = new File(dir.getParent());
				}
				fp = CPFileManager.openReadFP(dir, filename, script_flag);
				if (fp == null) {
					throw new InOutException("failed to open " + filename
							+ ", directory " + dir.toString());
				}
				int rslt = ReadWrite.readpack(fp,packData,filename); 
				// DCELdebug.rededgecenters(packData.packDCEL);
				if (rslt > 0) {
					fp.close();
					packData.setName(filename);
					if (packData.getDispOptions != null)
						CommandStrParser.jexecute(packData, "disp -wr");
					return rslt;
				}
				fp.close();

				// failed? try reading as triangulation; if not, try as points
				// for delaunay triangulation
				CirclePack.cpb.msg("");
				fp = CPFileManager.openReadFP(dir, filename, script_flag);
				try {
					tri = Triangulation.readTriFile(fp);
				} catch (Exception ex) {
					tri = null;
				}
				// yes another failure?
				if (tri==null) {
					CirclePack.cpb.errMsg("Some failure in reading "+
							filename+" as a triangulation");
				}
				
				fp.close();
				
				if (tri!=null)
					CirclePack.cpb.msg("Have read '" + filename
							+"' as a triangulation");
				else {
					throw new InOutException("failed to read " + filename
						+ " as packing or triangulation. Check its format.");
				}
			} catch (IOException iox) {
				try {
					if (fp != null)
						fp.close();
				} catch (IOException e) {
				}
				throw new ParserException("trying to read " + filename + ": "
						+ iox.getMessage());
			}
    	  
    	  // seems we got a triangulation
		  PackData pdata=Triangulation.tri_to_Complex(tri,hes);
		  if (pdata!=null) {
			  int pnum=packData.packNum;
			  packData=CirclePack.cpb.swapPackData(pdata,pnum,false);
			  packData.chooseAlpha();
			  packData.chooseGamma();
			  packData.set_aim_default();
			  packData.set_rad_default();
			  packData.activeNode = packData.getAlpha();
			  packData.setGeometry(hes);
      		
			  // set/reset stuff
          	  packData.packExtensions=new Vector<PackExtender>(2);
			  packData.setName(filename);
			  
			  // set small constant default radius
			  double rad=0.025; 
			  if (packData.hes<0) rad=1.0-Math.exp(-1.0);
			  for (int i=1;i<=packData.nodeCount;i++)
				  packData.setRadius(i,rad);
			  
			  // if 'nodes' were obtained, save them, store centers
			  if (tri.nodes!=null) {
				  packData.xyzpoint=tri.nodes;
			  	  // set centers
				  if (hes>0) { // sphere? assume xyz info is (theta,phi,0) form
					  for (int i=1;i<=packData.nodeCount;i++) {
						  if (tri.nodes[i]!=null) 
							  packData.setCenter(i,new Complex(packData.xyzpoint[i]));
					  }
				  }
				  else { // assume (x,y) is center in the plane (or disc)
					  for (int i=1;i<=packData.nodeCount;i++) {
						  if (tri.nodes[i]!=null) 
							  packData.setCenter(i,
									  new Complex(tri.nodes[i].x,tri.nodes[i].y));
					  }
				  }
			  }

			  packData.fillcurves();
			  packData.set_plotFlags();
              packData.cpDrawing.reset();
			  return 1;
		  }
		  return 0;
	  }
	  
      /* ==============================================
       * call 'packExecute' for routines that require 'status' to be true
       */  
      int retint=0;
      if (packData.status) {
    	  retint=packExecute(packData,cmd,flagSegs);
    	  if (retint>0) { // found command, return if success 
    		  return retint;
    	  }
      }
      
      // on reaching here, cmd wasn't found among those requiring 
      //    'status' to be set or it returned value <=0. 
      //    Look for the command here:
      
      /* **
       * **
       * *********** Main 'switch' to look for commands **********
       * Note that 'items' is already set to first 'flagSeg'
       * **
       * **
       */
	  switch (cmd.charAt(0)) {
	  case 'a':
	  {
		  
		  // TODO: should put more work in a separate method
	      // =========== adjoin ==========
	      if (cmd.startsWith("adjoin")) {
	    	  // one seg: p1 p2 v1 v2 n or p1 p2 v1 v2 (v1 w)
	    	  int pnum1=Integer.parseInt((String)items.get(0));
	    	  int pnum2=Integer.parseInt((String)items.get(1));

	    	  if (pnum1<0 || pnum1>=CPBase.NUM_PACKS 
	    			  || !CPBase.cpDrawing[pnum1].getPackData().status 
	    			  || pnum2<0 || pnum2>=CPBase.NUM_PACKS 
	    			  || !CPBase.cpDrawing[pnum2].getPackData().status) 
	    		  throw new ParserException("illegal or inactive packings specified");
	    	  packData=CPBase.cpDrawing[pnum1].getPackData(); // where the final pack will go
	    	  PackData qackData=CPBase.cpDrawing[pnum2].getPackData();
	    	  
	    	  int v1=NodeLink.grab_one_vert(packData,(String)items.get(2));
	    	  int v2=NodeLink.grab_one_vert(qackData,(String)items.get(3));
	    	  if (!packData.isBdry(v1) || !qackData.isBdry(v2))
	    		  throw new DataException("one or both vertices are not on the boundary");
	    	  
	    	  if (pnum1==pnum2 && v1==v2 && packData.countFaces(v1)<3)
	    		  throw new DataException("zip up start vertex "+v1+" has too few neighbors");
	    	  
	    	  // last entry has two forms: n or (v1 w)
	    	  int N;
	    	  String str=(String)items.get(4);
	    	  if (str.contains("(")) { // alternate form (v1 w)
	    		  /* check if v1 and w are bdry, on the same bdry
	    		   * component; count (clockwise) edges from v1 to w.*/
	    		  NodeLink nl=new NodeLink(packData,str);
	    		  int vv1=(Integer)nl.get(0);
	    		  int w=(Integer)nl.get(1);
	    		  if (w==vv1 || vv1!=v1 || !packData.isBdry(vv1) || !packData.isBdry(v1))
	    			  throw new ParserException("vertices equal or not on same bdry component");
	    		  
	    		  // count edges clw about bdry until reaching 'w' 
	    		  int tick=1;
    			  PackDCEL pdc=packData.packDCEL;
    			  HalfEdge he=pdc.vertices[v1].halfedge.twin.next;
    			  HalfEdge startedge=he;
    			  while (he.next.origin.vertIndx!=w && he!=startedge.prev) {
    				  tick++;
    				  he=he.next;
    			  } 
    			  N=tick;
	    	  }
	    	  else // n is given
	    		  N=Integer.parseInt((String)items.get(4));
	    	  
	    	  // call to adjoin
	    	  PackData newPack=PackData.adjoinCall(packData, qackData, v1, v2, N);
	    	  if (newPack==null) {
    			  CirclePack.cpb.errMsg("'adjoin' failed: ");
    			  return 0;
	    	  }
	    	  
	    	  newPack.packDCEL.fixDCEL(newPack);
	    	  packData=CirclePack.cpb.swapPackData(newPack,pnum1,true);
			  return packData.nodeCount;
	      } // end of 'adjoin'
	      
	      // =========== torpack ===========
	      else if(cmd.startsWith("torpack")) {
	    	  // 1 or 2 doubles as side-pairing factors
	    	  double[] factors=new double[2];
	    	  factors[0]=1.0;
	    	  factors[1]=1.0;
    		  ArrayList<Double> ftrs=new ArrayList<Double>();
	    	  if (flagSegs!=null && flagSegs.size()>0) {
	    		  items=(Vector<String>)flagSegs.get(0);
	    		  try {
	    			  while (items.size()>0) {
	    				  ftrs.add(Double.parseDouble(items.remove(0)));
	    			  }
	    		  } catch(Exception ex) {
	    			  throw new ParserException("Usage: torpack [A B]");
	    		  }
	    	  }
	    	  int n=ftrs.size();
	    	  if (n>=1)
	    		  factors[0]=ftrs.get(0);
	    	  if (n>=2) 
	    		  factors[1]=ftrs.get(1);
	    	  
	    	  // now try the affine packing
	    	  if (!ProjStruct.affineSet(packData,null,factors[0],factors[1]))
	    		  throw new ParserException("torpack failed");
	    	  
	    	  EuclPacker e_packer=new EuclPacker(packData,-1);
	    	  EuclPacker.affinePack(packData,-1);
				
	    	  // store results as radii
	    	  NodeLink vlist=new NodeLink();
	    	  for (int i=0;i<e_packer.aimnum;i++) {
	    		  vlist.add(e_packer.index[i]);
	    	  }

	    	  e_packer.reapResults();
	    	  return packData.packDCEL.layoutPacking();
	      }
		  break;
	  } // end of 'a'
	  case 'b':
	  {
		  break;
	  }
	  case 'c':
	  {
		  
	      // ========= cd ================
		  if (cmd.startsWith("cd")) {
			  String str="~";
			  try {
				  items=(Vector<String>)flagSegs.get(0);
				  str=(String)items.get(0).trim();
			  } catch (Exception ex) {}
			  File file=null;
			  try {
				  // TODO: other syntax for home?
				  if (str.startsWith("~")) {
					  str=str.substring(1);
					  file=new File(CPFileManager.HomeDirectory,str);
				  }
				  // e.g. ../.. (see what happens)
				  else if (!str.startsWith(File.separator)) {
					  file =new File(CPFileManager.CurrentDirectory,str);
				  }
				  else file=new File(str);

				  if (!file.exists()) {
					  throw new ParserException("Directory "+str+" does not exist");
		    	  }
				  if (file.isDirectory()) // is it a directory?
					  CPFileManager.CurrentDirectory=new File(file.getAbsolutePath());
				  else // else, get its parent directory
					  CPFileManager.CurrentDirectory=new File(file.getParent());
				  
				  // change script and packing directories, also
				  File tryfile=new File(CPFileManager.CurrentDirectory,"scripts");
				  if (tryfile.exists()) // "scripts" in new directory? change to it 
					  CPFileManager.ScriptDirectory=tryfile;
				  tryfile=new File(CPFileManager.CurrentDirectory,"packings");
				  if (tryfile.exists()) 
					  CPFileManager.PackingDirectory=tryfile;
				  tryfile=new File(CPFileManager.CurrentDirectory,"pics");
				  if (tryfile.exists()) 
					  CPFileManager.ImageDirectory=tryfile;
		    	  str=new String(file.getCanonicalPath());
			  } catch (Exception ex) {
				  return 0;
			  }
			  if (PackControl.frame!=null)
				  PackControl.frame.setTitle("CirclePack (dir; "+str+")");
			  return 1;
		  }
		  
		  // =========== cleanse ==========
	      if (cmd.startsWith("clean")) {
			  packData.packExtensions=new Vector<PackExtender>(2); // trash any extensions
			  
			  // put new packing in place
			  int pnum=packData.packNum;
			  PackData newP=new PackData(pnum);
			  CPBase.packings[pnum]=newP;
			  newP.cpDrawing=CPBase.cpDrawing[pnum];
			  newP.cpDrawing.setPackData(newP);
			  newP.cpDrawing.emptyScreen();
			  newP.cpDrawing.updateXtenders();
			  
			  // point local 'packData' to new one 
			  packData=newP;
			  return 1;
	      }
	      
		  // ========== close ===========
		  if (cmd.startsWith("close") && CPBase.GUImode!=0) {
			  // default to 'advanced' 
			  if (items==null || items.size()==0) {
				  if (PackControl.anybodyOpen()>=2)
					  PackControl.frame.setVisible(false);
				  return 1;
			  }
			  Iterator<String> ws=items.iterator();
			  while (ws.hasNext()) {
				  String windStr=ws.next().toLowerCase();
			  
				  if (windStr.startsWith("adv")) {
					  int k=PackControl.anybodyOpen();
					  if (k>=2) { // CirclePack.cpb.anybodyOpen();
						PackControl.frame.setVisible(false);
					  }
				  }
				  else if (windStr.startsWith("act")) {
					  if (PackControl.activeFrame.isVisible()) {
						  PackControl.activeFrame.setState(JFrame.ICONIFIED);
					  }
				  }
				  else if (windStr.startsWith("pair") || 
						  windStr.startsWith("map")) { // dual screen mode
					  if (PackControl.mapPairFrame.isVisible()) {
						  PackControl.mapPairFrame.setState(JFrame.ICONIFIED);
					  }
				  }
				  else if (windStr.startsWith("scre")) { // screen: must preceed 'scr'
					  PackControl.screenCtrlFrame.setVisible(false);
				  }
				  else if (windStr.startsWith("mes") || windStr.startsWith("msg")) {
					  if (PackControl.anybodyOpen()>=2) {

						  // keep locked (though not necessarily visible)
						  PackControl.msgHover.locked=true;
						  PackControl.msgHover.lockedFrame.setVisible(false);
						  
						  // TODO: remove hover behavior to see if it's causing timing problems
/*						  PackControl.msgHover.loadHover();
						  PackControl.msgHover.locked=false;
*/
					  }
				  }
				  else if (windStr.startsWith("conf")) {
					  if (PackControl.prefFrame!=null) {
						  PackControl.prefFrame.setVisible(false);
						  PackControl.prefFrame=null;
					  }
				  }
				  else if (windStr.startsWith("scr")) {
					  if (PackControl.scriptHover.isLocked()) {
						  PackControl.scriptHover.loadHover();
						  PackControl.scriptHover.lockedFrame.setVisible(false);
					  }
				  }
				  else if (windStr.startsWith("fun")) {
					  PackControl.newftnFrame.setVisible(false);
				  }
				  else if (windStr.startsWith("mob")) {
					  PackControl.mobiusFrame.setVisible(false);
				  }
				  else if (windStr.startsWith("www") || windStr.startsWith("bro")) {
					  PackControl.browserFrame.setVisible(false);
				  }
				  else if (windStr.startsWith("abo")) {
					  PackControl.aboutFrame.setVisible(false);
				  }
				  else if (windStr.startsWith("hel")) {
					  if (PackControl.helpHover.isLocked()) { 
						  PackControl.helpHover.lockedFrame.setVisible(false);
						  PackControl.helpHover.loadHover();
						  PackControl.helpHover.locked=false;
					  }
				  }
				  else if (windStr.startsWith("inf")) { // info frame
					  PackControl.packDataHover.setLocked(false);
				  }
				  else if (windStr.startsWith("www") || windStr.startsWith("bro")) {
					  PackControl.browserFrame.setVisible(false);
				  }
				  else if (windStr.startsWith("abo")) {
					  PackControl.aboutFrame.setVisible(false);
					  PackControl.aboutFrame=null;
				  }
				  else if (windStr.startsWith("sav")) { // save frame
					  PackControl.outputFrame.setVisible(false);
				  }
			  } // end of while
			  return 1;
		  }

		  // ============== create ==============
		  else if (cmd.startsWith("create")) {
			  int mode=1;
			  int param=0;
			  flagSegs.remove(0);
			  String type = null;
			  try {
				  type=items.remove(0);
				  try {
					  param=Integer.parseInt(items.remove(0)); // get number first
				  } catch (Exception ex) {
					  param=0;
				  }
				  if (type.startsWith("seed"))
					  mode=1;
				  else if (type.startsWith("hex_tor"))
					  mode=12;
				  else if (type.startsWith("hex") || type.startsWith("Hex"))
					  mode=2;
				  else if (type.startsWith("sq") || type.startsWith("Sq")) 
					  mode=3;
				  else if (type.startsWith("chai") || type.startsWith("Chai")) {
					  mode=4;
				  }
				  else if (type.startsWith("tri_g") || type.startsWith("Tri_g")) {
					  mode=5;
				  }
				  else if (type.startsWith("j") || type.startsWith("J")) {
					  mode=5; // special triangle group
				  }
				  else if (type.startsWith("pent3")) {
					  mode=7;
				  }
				  else if (type.startsWith("pent4")) {
					  mode=8;
				  }
				  else if (type.startsWith("pent")) {
					  mode=6;
				  }
				  else if (type.startsWith("dyadic")) {
					  mode=9;
				  }
				  else if (type.startsWith("fib") || type.startsWith("Fib")) {
					  mode=11;
				  }
				  else if (type.startsWith("tetra") || type.startsWith("Tetra")) {
					  mode=13;
				  }
				  else if (type.startsWith("Kag")) {
					  mode=14;
				  }
			  } catch (Exception ex) {
				  throw new ParserException("usage: create "+type+" {n}");
			  }

			  if (param<0)
				  throw new ParserException("usage: create "+type+" [{n}], "
				  		+ "n must be non-negative");
			  
			  PackData newPack=null;
			  switch (mode) {
			  case 1: // seed
			  {
				  // -s flag? create from schwarzians
				  if (flagSegs==null || flagSegs.size()==0
						  || !(items=flagSegs.remove(0)).remove(0).startsWith("-s"))
					  newPack=PackCreation.seed(param,0);
				  else {
					  Vector<Double> schvec=new Vector<Double>();
					  // get as many schwarzians as available
					  while (items.size()>0) {
						  String numstr=items.remove(0);
						  try {
							  Double dbl=Double.parseDouble(numstr);
							  schvec.add(dbl);
						  } catch(Exception iex) {
							  CirclePack.cpb.errMsg("seed -s: schwarzian failure");
							  break;
						  }
					  }
					  int sz=schvec.size();
					  int szp=sz+1;
					  if (szp<param+1) 
						  szp=param+1;
					  double[] schlist=new double[szp];
					  int tick=1;
					  Iterator<Double> sst=schvec.iterator();
					  while (sst.hasNext() && tick<=param) {
						  schlist[tick++]=(double)sst.next();
					  }
					  
					  newPack=PackCreation.seed(param,0,schlist);
				  }
				  break;
			  }
			  case 2: // hex/Hex
			  {
				  if (type.charAt(0)=='h' && param>100) {
					  throw new DataException("Use 'Hex' (cap 'H') for "
					  		+ "more than 100 generations");
				  }
				  newPack=PackCreation.hexBuild(param);
				  newPack.packDCEL.layoutPacking();
				  break;
			  }
			  case 3: // square grid
			  {
				  if (type.charAt(0)=='s' && param>8) {
					  throw new DataException("Use 'Sq_grid' (cap 'S') for "
					  		+ "more than 8 generations");
				  }
				  newPack=PackCreation.squareGrid(param);
				  break;
			  }
			  case 4: // chair, with 'TileData'
			  {
				  throw new DataException("No longer active: use tile machinery");
			  }
			  case 5: // triangle group, and j-function 
			  {
				  if ((type.charAt(0)=='t' || type.charAt(0)=='j') && 
						  param>15) {
					  throw new DataException(
							  "Use 'Tri_group' or 'J_ftn' (capitalize) for more "
							  + "than 15 generations");
				  }

				  // later we reorder to put A,B,C in ascending order.
				  double a,b,c;
				  boolean jftn=false;
				  if (items.size()==2)
					  jftn=true;
				  
				  // j-function version specified via 'j-ftn' command
				  //    or by specifying 0 as first parameter
				  
				  // defaults: 
				  //   if 3 parameters, a=2, b=3, c=7 (classical hyp example)
				  //   if 2 parameters, a=0, b=2, c=3 (j-ftn)
				  try {
					  a=Double.parseDouble(items.get(0));
					  b=Double.parseDouble(items.get(1));
					  if (!jftn) 
						  c=Double.parseDouble(items.get(2));
					  else {
						  b=a;
						  c=b;
						  a=0.0;
					  }
				  } catch (Exception ex) {
					  if (jftn) { // j-functions version
						  a=0.0;
						  b=2.0;
						  c=3.0;
					  }
					  else {
						  a=2.0;
						  b=3.0;
						  c=7.0;
					  }
				  } // go with defaults
				  
				  if (a==0.0)  
					  jftn=true;
				  
				  // j-ftn case: parameters B and C must be integers >= 2
				  if (jftn) {
					  
					  int B=(int)(2.01*b);
					  int C=(int)(2.01*c);
					  if (B<2 || C<2)
						  throw new DataException(
								  "usage: j-function parameters "
								  + "be positive integers >= 2");
					  newPack=PackCreation.buildTriGroup(param,0,B,C); 
					  break;
				  }
				  
				  if (a<0.000000001 || b<0.00000001 || c<0.0000001) 
					  throw new DataException(
							  "usage: tri-group parameters must be positive");
				  
				  // set degrees: parameters should be (at worst) half ints
				  if (Math.abs(2.0*a-(int)(2.0*a))>.0001 ||
						  Math.abs(2.0*b-(int)(2.0*b))>.0001 ||
						  Math.abs(2.0*c-(int)(2.0*c))>.0001)
					  throw new DataException(
							  "usage: tri_group: paremeters must be form n/2");
				  if (Math.abs(2.0*((int)a)-2.0*a)>.1) {
					  if (((int)b-(int)c)>0.1) 
						  throw new DataException(
							  "usage: tri_group: 'a' half-int, "
							  + "then b, c must be equal");
				  }
				  else if (Math.abs(2.0*((int)b)-2.0*b)>.1) {
					  if (((int)a-(int)c)>0.1) 
						  throw new DataException(
								  "usage: tri_group: 'b' is "
								  + "half-integer, but a, c not equal");
				  }
				  if (Math.abs(2.0*((int)c)-2.0*c)>.1) {
					  if (((int)b-(int)a)>0.1) 
						  throw new DataException(
								  "usage: create tri_group: 'c' is "
								  + "half-integer, but a, b not equal");
				  }
				  int A=(int)(2.01*a);
				  int B=(int)(2.01*b);
				  int C=(int)(2.01*c);

				  newPack=PackCreation.buildTriGroup(param, A, B, C);
				  break;
			  }
			  case 6: // pentagonal tiling, with 'TileData'
			  {
				  if (param<0)
					  param=0;
				  PackDCEL pdcel=PackCreation.pentagonal_dcel(param);
				  newPack=new PackData(null); // DCELdebug.printRedChain(pdcel.redChain);
				  newPack.attachDCEL(pdcel);
				  newPack.set_rad_default();
				  newPack.set_aim_default();
				  
				  for (int v=1;v<=newPack.nodeCount;v++) {
						if (newPack.isBdry(v))
							newPack.setAim(v,Math.PI);
				  }
				  for (int v=1;v<=5;v++)
					  newPack.setAim(v,3.0*Math.PI/5.0);

				  newPack.repack_call(1000);
				  newPack.status=true;
				  newPack.setAlpha(1);
				  CommandStrParser.jexecute(newPack,"layout");
				  CommandStrParser.jexecute(newPack,"norm_scale -u 3");
				  CommandStrParser.jexecute(newPack,"norm_scale -h 1 5");
				  CommandStrParser.jexecute(newPack,"pave 6");
				  
				  // TODO: call pave
				  
				  break;
			  }
			  case 7: // pentagonal triple point, with 'TileData'
			  case 8: // pentagonal quadruple point, with 'TileData'
			  {
				  if (param<0)
					  param=0;
				  int N=mode-4; // number to cluster at center
				  
				  // get pentagonal packing, right number of generations
				  PackDCEL pent=PackCreation.pentagonal_dcel(param);
				  int sidelength=(int)Math.pow(2.0,param);
				  PackDCEL pdcel=RawManip.polyCluster(pent,1,sidelength,N);
				  CombDCEL.fillInside(pdcel);
				  
				  // attach and set
				  newPack=new PackData(null);
				  newPack.attachDCEL(pdcel);
				  newPack.set_rad_default();
				  newPack.set_aim_default();
				  double ang=((double)N-1.0)/((double)N)*Math.PI;
				  for (int v=1;v<=newPack.nodeCount;v++) {
						if (newPack.isBdry(v)) {
							newPack.setAim(v,Math.PI);
							if (newPack.packDCEL.vertices[v].getNum()==2)
								  newPack.setAim(v,ang); // reset at pent corners
						}
				  }
				  newPack.repack_call(1000);
				  newPack.status=true;
				  newPack.setAlpha(1);
				  CommandStrParser.jexecute(newPack,"layout");
				  CommandStrParser.jexecute(newPack,"norm_scale -u 3");
				  CommandStrParser.jexecute(newPack,"norm_scale -h 1 5");
				  CommandStrParser.jexecute(newPack,"pave 6");
				  
				  break;
			  }
			  case 9: // dyadic (hyp penrose), with 'TileData'
			  {
				  newPack=PackCreation.pentHypTiling(param);
				  break;
			  }
			  case 11: // fibonnacci 2D: W, H, X, width/height/base, with 'TileData'
			  {
				  if (type.charAt(0)=='f' && param>8) {
					  throw new DataException("Use 'Fib2d' (cap 'F') for "
					  		+ "more than 8 generations");
				  }
				  int W=1;
				  int H=1;
				  int X=1;
				  try {
					  W=Integer.parseInt(items.get(0));
					  H=Integer.parseInt(items.get(1));
					  X=Integer.parseInt(items.get(2));
				  } catch (Exception ex) {} // go with defaults
				  newPack=PackCreation.fibonacci2D(param, W, H, X);
				  break;
			  }
			  case 12: // hex torus, H height, W width
			  {
				  int W=0;
				  int H=0;
				  try {
					  H=param;
					  W=Integer.parseInt(items.get(0));
				  } catch (Exception ex) {
	        		  if (H<3)
	        			  H=3;
	        		  if (W<3)
	        			  W=3;
	        		  CirclePack.cpb.errMsg("Usage: hex_torus <h> <w>: "+
	        			  "default to "+H+" and "+W);
	        	  }
	        	  newPack=PackCreation.hexTorus(H,W);
	        	  jexecute(newPack,"layout");
	        	  
	        	  break;
			  }
			  case 13: // regular tetrahedron on the sphere
			  {
				  newPack=PackCreation.tetrahedron();
				  break;
			  }
			  case 14: // Kagome lattice
			  {
				  newPack=PackCreation.buildKagome(param);
				  break;
			  }
			  } // end of switch

			  if (newPack==null) {
				  throw new ParserException("failed to create "+type+" packing");
			  }
			  
			  newPack.status=true;
			  int pnum=packData.packNum;
			  packData=CirclePack.cpb.swapPackData(newPack,pnum,false);
			  return packData.nodeCount;
		  }
		  break;
	  } // end of 'c'
	  case 'd':
	  {
		  
		  // ========= delaunay (this is 2D) ==========
		  if (cmd.startsWith("delaun") || cmd.startsWith("Delaun")) {
			  int N=0;
			  String str;
			  boolean script_flag=false; // data from script?
			  boolean fromFile=false; // data in some file?
			  boolean cpack=false; // from packing centers?
			  Vector<Complex> pts=null;
			  UtilPacket uP=new UtilPacket();
			  int geom=100; // intended geometry
			  
			  // check for flags
			  Iterator<Vector<String>> fseg=flagSegs.iterator();
			  while (fseg.hasNext()) {
				  try {
					  items=fseg.next();
					  str=(String)items.get(0);

					  if (StringUtil.isFlag(str)) {
						  
						  // -f and/or -s (script): read data from a file
						  if (str.startsWith("-f") || str.startsWith("-s")) {
							  if (cpack) 
								  throw new ParserException("'f' and 's' flags "
								  		+ "conflict with 'c' flag");
							  items.remove(0); // look for filename
							  fromFile=true;
							  if (str.startsWith("-s"))
								  script_flag=true;
							  String filename=StringUtil.reconItem(items);
							  BufferedReader fp=
								  CPFileManager.openReadFP(CPFileManager.
										  PackingDirectory,filename,script_flag);
							  if (fp==null) { 
								  throw new InOutException("failed to open "+
										  CPFileManager.PackingDirectory.toString()+
										  File.separator+filename);
							  }
							  RandomTriangulation.readPoints(fp,uP);
							  if (uP.errval<0) {
								  throw new ParserException("Error reading points");
							  }
							  pts=uP.z_vec;
						  } // should have pts
						  
						  // from current packing
						  else if (str.startsWith("-c")) { 
							  if (fromFile) 
								  throw new ParserException("'c' flag "
								  		+ "conflicts with 'f','s' flags");
							  if (!packData.status)
								  throw new ParserException("packing status is false");
							  cpack=true;
							  geom=packData.hes;
							  pts=new Vector<Complex>(packData.nodeCount+1);
							  for (int v=1;v<=packData.nodeCount;v++) { 
								  if (str.contains("m")) {
									  pts.add(CirclePack.cpb.getFtnValue(packData.getCenter(v)));
								  }
								  else
									  pts.add(packData.getCenter(v));
							  }
						  }
						  
						  // geometry: 0 eucl or 1 sphere. Mainly for files with keyword
						  //    "UNIT_SQUARE", which may be intended to be euclidean or
						  //    to be converted to sphere.
						  else if (str.startsWith("-g")) {
							  items.remove(0);
							  try {
								  char c=items.get(0).charAt(0);
								  if (c=='h') // hyp
									  geom=-1; 
								  else if (c=='e') // eucl
									  geom=0; 
								  else if (c=='s') // sph
									  geom=1;
							  } catch (Exception ex) {}
						  }
						  
					  } // done with flag segments
				  } catch(Exception ex) {
					  throw new ParserException("flag error :"+ex.getMessage());
				  }
			  } // end of while

			  // Now, must have pts vector
			  if (pts==null) {
				  throw new ParserException("flags missing, do not have any points");
			  }
			  if (cpack) // override -g flag if we're using centers
				  geom=packData.hes;
			  
			  // geometry not yet reset? 
			  if (geom==100) {
				  if (uP.rtnFlag==1 || uP.rtnFlag==2) { // eucl: unit square or 2D
					  geom=0; 
				  }
				  else if (uP.rtnFlag==3) { // sphere
					  geom=1;
				  }
				  else
					  throw new ParserException("confusion about geometry");
			  }
			  
			  N=pts.size();
			  if (geom==1 && uP.rtnFlag==1) { // unit square pts? (theta,phi) form
				  for (int i=0;i<N;i++) {
					  Complex pz=pts.get(i);
					  pz.x=2.0*Math.PI*pz.x;
					  pz.y=Math.acos(2.0*(pz.y-.5));
					  pts.set(i,pz);
				  }
			  }
			  
			  Triangulation Tri=Triangulation.pts2triangulation(geom, pts);
			  PackData randPack=Triangulation.tri_to_Complex(Tri,geom);
//				  if (randPack==null) {
//					  throw new CombException("'tri_to_Complex' failed");
//				  } 
			  // put new packing in place
			  randPack.fileName=new String("Delaunay"+N);
			  int pnum=packData.packNum;
			  packData=CirclePack.cpb.swapPackData(randPack,pnum,false);
			  packData.chooseAlpha();
			  packData.chooseGamma();
			  packData.set_aim_default();
			  packData.set_rad_default();
			  for (int v=1;v<=packData.nodeCount;v++) 
				  packData.setPlotFlag(v,1);
			  return N;
		  } // end of 'delaunay'
		  
		  else if (cmd.startsWith("debug")) {
			  char flag='d'; // default
			  try {
				  if (StringUtil.isFlag(items.get(0)))
					  flag=items.get(0).charAt(1);
				  else 
					  flag=items.get(0).charAt(0);
			  } catch(Exception ex) {
				  flag='d';
			  }
			  
	    	  switch(flag) {
	    	  case 's': // print stackbox sizes
	    	  {
	    		  System.err.println("Here's the current script layout info:");
	    		  System.err.println("* stackArea size w,h = "+
	    				  PackControl.scriptHover.stackArea.getWidth()+" "+
	    				  PackControl.scriptHover.stackArea.getHeight());
	    		  System.err.println("* stackScroll size w,h = "+
	    				  PackControl.scriptHover.stackScroll.getWidth()+" "+
	    				  PackControl.scriptHover.stackScroll.getHeight());
	    		  PackControl.scriptManager.cpScriptNode.debugSize();
	    		  PackControl.scriptManager.debugLayoutRecurse(
	    				  PackControl.scriptManager.cpScriptNode);
	    		  PackControl.scriptManager.cpDataNode.debugSize();
	    		  PackControl.scriptManager.debugLayoutRecurse(
	    				  PackControl.scriptManager.cpDataNode);
	    		  System.err.println("end of stackbox info\n");
	    		  return 1;
	    	  }
	    	  case 'e': // send each command to stderr before executing
	    	  {
	    		  CPBase.cmdDebug=true;
	    		  return 1;
	    	  }
	    	  case 'x': // turn 'e' off
	    	  {
	    		  CPBase.cmdDebug=false;
	    		  return 1;
	    	  }
	    	  } // end of switch
			  return 0;
		  }
		  else if (cmd.startsWith("dela")) { // delay
			  double dy=0.001;
			  try {
				  items=flagSegs.get(0);
				  dy=Double.parseDouble(items.get(0));
				  Thread.sleep((long) (dy * 1000.0));
			  } catch (InterruptedException ie) {
				  throw new ParserException("usage: delay <seconds>");
			  }
			  return 1;
		  }
	      break;
	  } // end of 'd'
	  case 'e':
	  {
	      // ============ erf_ftn =========
	      if (cmd.startsWith("erf_ftn")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just pnum, qnum, n
	    	  int p1num=Integer.parseInt((String)items.get(0));
	    	  int p2num=Integer.parseInt((String)items.get(1));
	    	  int n=Integer.parseInt((String)items.get(2));
	    	  if (p1num<0 || p1num>=CPBase.NUM_PACKS || p2num<0
	    			  || p2num==p1num || p2num>=CPBase.NUM_PACKS
	    			  || n<1 || n>3) {
	    		  throw new ParserException("improper call");
	    	  }
	    	  PackData p=Erf_function.erf_ftn(CPBase.packings[p1num],n);
	    	  if (p==null) 
	    		  return 0;
	    	  CirclePack.cpb.swapPackData(p,p2num,false); // install as new pack[p2num]
	    	  return p.nodeCount;
	      }
	      
	      // ============= extender ===========
	      else if (cmd.startsWith("extender")) {
	    	  String str=null;
    		  int returnVal=0;
    		  
	    	  Iterator<Vector<String>> fgsg=flagSegs.iterator();
	    	  while (fgsg.hasNext()) {
	    		  items=fgsg.next();
	    		  String stg=(String)items.get(0);
	    	  
	    		  // for listing existing extenders (then return)
	    		  if (stg!=null && stg.startsWith("?")) { 
	    			  if (packData.packExtensions.size()==0) {
	    				  CirclePack.cpb.msg("Pack "+packData.packNum+
	    						  " has no existing extender");
	    			  }
	    			  Iterator<PackExtender> pXs=packData.packExtensions.iterator();
	    			  while (pXs.hasNext()) {
	    				  CirclePack.cpb.msg("Pack "+packData.packNum+" has "+
	    						  pXs.next().extensionAbbrev+" extender");
	    			  }
	    			  return 1;
	    		  }
	    		  
	    		  // kill sign?? (then return)
	    		  else if (stg!=null && stg.startsWith("-x")) {
	    			  items.remove(0);
	    			  if (packData.packExtensions.size()==0) {
	    				  CirclePack.cpb.myErrorMsg("Pack "+
	    						  packData.packNum+" has no extensions");
	    				  return 1;
	    			  }
	    			  if (items.size()==0) { // kill all extensions
	    				  // TODO: need confirmation popup
	    				  while (packData.packExtensions.size()>0)
	    					  packData.packExtensions.get(0).killMe();
	    				  packData.packExtensions=new Vector<PackExtender>(2);
	    				  return 1;
	    			  }
	    			  else {
	    				  str=(String)items.get(0);
	    				  PackExtender px=null;
	    				  while ((px=packData.findXbyAbbrev(str))!=null) {
	    					  px.killMe();
	    					  CirclePack.cpb.msg("Pack "+
	    							  packData.packNum+": kill "+str+" extension");
	    					  px=null;
	    				  }
	    			  }
	    			  
	    			  packData.cpDrawing.updateXtenders();
	    			  return 1;
	    		  }
	    		  
	    		  // restart (i.e., kill and then restart)
	    		  else if (stg!=null && stg.startsWith("-r")) {
	    			  items.remove(0);
	    			  if (items.size()>0) {
	    				  str=(String)items.get(0);
	    				  PackExtender px=null;
	    				  while ((px=packData.findXbyAbbrev(str))!=null) {
	    					  px.killMe();
	    				  }
	    			  }
	    			  packData.cpDrawing.updateXtenders();
	    		  }
	    		  
	    		  // may be no flag, just the abbreviation
	    		  else if (stg!=null && stg.charAt(0)!='-') {
	    			  str=stg;
	    		  }

	    	  } // end of while for flags
	    		  
	    	  // ************ hard coded abbreviations ***********
	    	  if (str!=null) {
	    	  if (str.equalsIgnoreCase("bvp")) {
			   	  if (!packData.status || packData.nodeCount==0) 
			   		  return 0;
			   	  BoundaryValueProblems px=new BoundaryValueProblems(packData);
			   	  if (px.running) {
				   	  CirclePack.cpb.msg("Pack "+packData.packNum+
				   			  ": started "+px.extensionAbbrev+" extender");
			    	  px.StartUpMsg();
			    	  returnVal=1;
				     }
			  }
	    	  else if (str.equalsIgnoreCase("bf")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  BeurlingFlow px=new BeurlingFlow(packData);
	    		  if (px.running) {
				   	  CirclePack.cpb.msg("Pack "+packData.packNum+
				   			  ": started "+px.extensionAbbrev+" extender");
			    	  px.StartUpMsg();
			    	  returnVal=1;
				  }
			  }
	    	  
	    	  // developing this.
	    	  else if (str.equalsIgnoreCase("mg")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  if (items.size()<=2) { // there must be a -s or -q flag
	    			  try {
	    				  items=flagSegs.get(1);
	    			  } catch(Exception ex) {
	    				  throw new ParserException(
	    						  "'extender' mg call needs packing or "
	    						  +"file info");
	    			  }
	    		  }
	    		  String srpt=items.get(0);
	    		  boolean script_flag=false;
	    		  if (srpt.equals("-s")) {
	    			  script_flag=true;
	    		  
	    			  MicroGrid px=new MicroGrid(packData,
	    					  items.get(1),items.get(2),script_flag);
	    			  if (px.running) {
	    				  CirclePack.cpb.msg("Pack "+packData.packNum+
	    						  ": started "+px.extensionAbbrev+
	    						  " extender, mode 1");
	    				  px.StartUpMsg();
	    				  returnVal=1;
	    			  }
	    		  }
	    		  // TODO: mode 2 is new (2/2020) and under development
	    		  else if (StringUtil.qFlagParse(srpt)>=0) {
	    			  script_flag=true;
	    			  int qnum=StringUtil.qFlagParse(srpt);
	    			  
	    			  MicroGrid px=new MicroGrid(packData,
	    					  CPBase.cpDrawing[qnum].getPackData(),null,script_flag);
	    			  if (px.running) {
	    				  CirclePack.cpb.msg("Pack "+packData.packNum+
	    						  ": started "+px.extensionAbbrev+
	    						  " extender, mode 2");
	    				  px.StartUpMsg();
	    				  returnVal=1;
	    			  }
	    		  }
			  }
	    	  else if (str.equalsIgnoreCase("rh")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  PackExtender px=new RiemHilbert(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("gp")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  Graphene px=new Graphene(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("pb")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  PolyBranching px=new PolyBranching(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("tc")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  TileColoring px=new TileColoring(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("cf")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  CurvFlow px=new CurvFlow(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ct")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  ConformalTiling px=new ConformalTiling(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }

	    	  }
	    	  else if (str.equalsIgnoreCase("TE")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  TorusEnergy px=new TorusEnergy(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("JP")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  JammedPack px=new JammedPack(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("PR")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  Percolation px=new Percolation(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("mc") || str.equalsIgnoreCase("mmc")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  MeanMove px=new MeanMove(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ca")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  ComplexAnalysis px=new ComplexAnalysis(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ps")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  ProjStruct px=new ProjStruct(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ap") || str.equalsIgnoreCase("ss")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  AffinePack px=new AffinePack(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("sm")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  SchwarzMap px=new SchwarzMap(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("gb")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  GenModBranching px=new GenModBranching(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("nk")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  Necklace px=new Necklace(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("fs")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  FlipStrategy px=new FlipStrategy(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("IG")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  iGame px=new iGame(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("rm")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  RationalMap px=new RationalMap(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("sp")) {
	    		  if (!packData.status || packData.nodeCount==0) 
	    			  return 0;
	    		  SpherePack px=new SpherePack(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
//  	OBE: old experiments for John Velling, VellingPants removed from
//	    repository, 6/2015.
//	    	  else if (str.equalsIgnoreCase("VP")) {
//	    		  VellingPants px=new VellingPants(packData);
//	    		  if (px.running) {
//		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
//		    				  ": started "+px.extensionAbbrev+" extender");
//	    			  px.StartUpMsg();
//	    			  returnVal=1;
//		    	  }	    		  
//	    	  }
	    	  else if (str.equalsIgnoreCase("SS")) {
	    		  ShapeShifter px=new ShapeShifter(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("WW")) {
	    		  WordWalker px=new WordWalker(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("fk")) {
	    		  FeedBack px=new FeedBack(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("bq")) {
	    		  BrooksQuad px=new BrooksQuad(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("bl")) {
	    		  BeltramiFlips px=new BeltramiFlips(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("hd")) {
	    		  HypDensity px=new HypDensity(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("sl")) {
	    		  SphereLayout px=new SphereLayout(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }	    		  
	    	  }
	    	  else if (str.equalsIgnoreCase("cw")) {
	    		  WeldManager px=new WeldManager(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  
	    	  if (returnVal==1)
				  packData.cpDrawing.updateXtenders();

	    	  } // end of hard-coded cases
	    	  
	    	  // if nothing yet found, get from dialog
	    	  if (str==null || returnVal==0) {
    			  PackExtender px=null;
	    		  File extFile=FileDialogs.loadDialog(FileDialogs.EXTENDER,true);
	    		  if (extFile!=null) {
	    			  try {
	    	            @SuppressWarnings("unchecked")
	    	            Class<PackExtender> extClass = (Class<PackExtender>)
	    	            new circlePack.PackExtenderLoader().loadClass(extFile.
	    	            		getCanonicalPath());
	    	            
	    	            // start the new class.
	    	            // TODO: questions: how do I send an argument 'packData'?
	    	            px= extClass.getConstructor(packing.PackData.class).
	    	            		newInstance(packData);
	    			  } catch (IOException e) {
	    				  e.printStackTrace();
	    			  } catch (ClassNotFoundException e) {
	    				  e.printStackTrace();
	    			  } catch (InstantiationException e) {
	    				  e.printStackTrace();
	    			  } catch (IllegalAccessException e) {
	    				  e.printStackTrace();
	    			  } catch (InvocationTargetException e) {
	    				  e.printStackTrace();
	    			  } catch (NoSuchMethodException e) {
	    				  e.printStackTrace();
	    			  }
	    		  }

	    		  if(px!=null && px.running) {
	    			  CirclePack.cpb.msg("Pack "+packData.packNum+
	    					  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  packData.cpDrawing.updateXtenders();
	    			  returnVal=1;
	    		  }
	    	  }
	    	  
	    	  return returnVal;
	      }
	      
	      // ============= exit ===========
	      else if (cmd.equals("exit")) {
	    	  // AF: Query user to exit CirclePack.
	    	  ((PackControl) CirclePack.cpb).queryUserForQuit();
	    	  // AF: If we haven't exited, return 0.
	    	  return 0;
	      }
	      
	      // ============ evalp ============
	      else if (cmd.startsWith("evalp")) {
	    	  if (flagSegs==null || flagSegs.size()==0)
	    		  throw new ParserException("usage: evalp <t>, "
	    		  		+ "expects double argument");
	    	  double t;
	    	  try {
	    		  t=Double.parseDouble(flagSegs.get(0).get(0));
	    	  } catch(Exception ex) {
	    		  throw new ParserException("usage: evalp <t> failed");
	    	  }
	    	  com.jimrolf.complex.Complex jrz=new com.jimrolf.complex.Complex(t,0.0);
	    	  com.jimrolf.complex.Complex jrw=
	    			  CirclePack.cpb.ParamParser.evalFunc(jrz);
	    	  Complex w=new Complex(jrw.re(),jrw.im());
	    	  CirclePack.cpb.msg("Path value at t="+t+" is "+w.toString());
	    	  return 1;
	      }
	      
	      // ========== eval ===================
	      else if (cmd.startsWith("eval")) {
	    	  if (flagSegs==null || flagSegs.size()==0)
	    		  throw new ParserException("usage: eval <z>, "
	    		  		+ "expects complex argument");
	    	  String zstr=StringUtil.reconItem(flagSegs.get(0));
	    	  Complex z=Complex.string2Complex(zstr);
	    	  com.jimrolf.complex.Complex jrz=
	    			  new com.jimrolf.complex.Complex(z.x,z.y);
	    	  com.jimrolf.complex.Complex jrw=
	    			  CirclePack.cpb.FtnParser.evalFunc(jrz);
	    	  if (jrw==null) {
	    		  CirclePack.cpb.errMsg("evaluation failed, "
	    		  		+ "check function specification");
	    		  return 0;
	    	  }
	    	  Complex w=new Complex(jrw.re(),jrw.im());
	    	  CirclePack.cpb.msg("Function value at z="+z.toString()+
	    			  " is "+w.toString());
	    	  return 1;
	      }
	      
	      break;
	  } // end of 'e'
	  case 'f':
	  {

		  // ============ fexec =============
		  if (cmd.startsWith("fexec")) {
	    	  BufferedReader fp=CPFileManager.openReadTail(flagSegs);
	    	  String line=null;
	    	  try {
	    		  
	    		  // NOTE: this does not stop if an error occurs, it keeps
	    		  //       trying the next line. 
	    		  // Also, it works in the main thread, so there can be
	    		  //       delays.
	    		  while((line=StringUtil.ourNextLine(fp))!=null) {
	    			  // apply commands to the active pack
	    			  ResultPacket rP=new ResultPacket(CirclePack.cpb.
	    					  getActivePackData(),line);
	    			  CPBase.trafficCenter.parseCmdSeq(rP,0,null);
	    			  count+=Integer.valueOf(rP.cmdCount);
	    		  }
	    		  fp.close();
			  } catch (Exception ex) {
				  throw new ParserException("error in 'fexec' processing: "+
						  ex.getMessage());
			  }
	          return count;
		  }
	      break;
	  } // end of 'f'
	  case 'g': // fall through
	  case 'G': 
	  {
		  
	      // =========== get_func ===========
	      if (cmd.startsWith("get_fun")) {
	    	  CirclePack.cpb.msg("Function expression: "+
	    			  CirclePack.cpb.FtnSpecification.toString()+"\n");
	    	  CirclePack.cpb.msg("Parametric expression: "+
	    			  CirclePack.cpb.ParamSpecification.toString()+"\n");
	      }
	      
		  // NOTE: this is not operational now (3/2022), as the JNI 
		  //       calls to C code have been removed.
		  // flags: s=start, r=restart, c=continue, g=get rad/cent, q=quality
		  else if (cmd.startsWith("GOpack")) {
			  count=0;
			  GOpacker goPack;
			  
			  // first, check for -v {v..} flag specifying which vertices are
			  //   treated as "interior". If this is found, then new GOPacker is
			  //   initiated and other flags are ignored --- user must make
			  //   separate calls to us the GOpacker.
			  Iterator<Vector<String>> fsq=flagSegs.iterator();
			  while (fsq.hasNext()) {
				  Vector<String> itms=fsq.next();
				  if (itms.size()>0 && itms.get(0).startsWith("-v")) {
					  itms.remove(0);
					  NodeLink v_int=new NodeLink(packData,itms);
					  if (v_int==null || v_int.size()<1)
						  throw new ParserException("GOpack usage: GOpack "
						  		+ "-v {v..}");
					  goPack=new GOpacker(packData,v_int);
				  	  packData.rePacker=goPack;
				  	  CirclePack.cpb.msg("GOpack started for subcomplex");
				  	  return 1;
				  }
			  }
			  
			  // otherwise, normal processing
			  int defaultPasses=10;
			  int passes=-1;
			  char c='s'; // default to "start" only
			  char gotc='s'; // exclusive flag
			  boolean gotflag=false; // some flags are exclusive
			  
			  // start the persistent 'RePacker' if not already running
			  if (packData.rePacker==null || 
					  !(packData.rePacker instanceof GOpacker) ||
					  packData.rePacker.p==null || 
					  packData.rePacker.p!= packData ||
					  packData.nodeCount!=((GOpacker)packData.rePacker).
					  	getOrigNodeCount()) {  
				  goPack=new GOpacker(packData,passes);
			  	  packData.rePacker=goPack;
			  }
			  else {
				  goPack=(GOpacker)packData.rePacker;
				  c='c'; // default to "continue"
				  gotc=c;
			  }
			  
			  // default: continue with whatever was going on
			  if (flagSegs==null || flagSegs.size()==0) {
				  int ct=goPack.continueRiffle(defaultPasses);
				  CirclePack.cpb.msg("GOpacker: did "+ct+
						  " passes, l2-error = "+goPack.myPLiteError);
				  return ct;
			  }
			  
			  Iterator<Vector<String>> its=flagSegs.iterator();
			  while (its.hasNext()) {
				  items=its.next();
				  String str=items.get(0);
				  if (StringUtil.isFlag(str)) {
					  items.remove(0);
					  c=str.charAt(1);
					  switch(c) {
					  
					  // specified "interior" (versus default to usual interior)
					  case 'v': 
					  {
						  if (goPack.mode!=GOpacker.NOT_YET_SET) {
							  CirclePack.cpb.msg("GOpack can set vertices "
							  		+ "only on initialization");
							  return 0;
						  }
						  
					  }
					  
					  // set all radii to 1, do no riffles yet
					  case 's':
					  {
						  goPack.startRiffle();
						  CirclePack.cpb.msg("GOpack is initialized, "
						  		+ "constant radii, no cycles yet");
						  return 1;
					  }
					  
					  // continue with another batch of repacking, 'n' passes
					  case 'c':
					  {
						  if (gotflag)
							  break;
						  gotflag=true;
						  gotc=c;
						  if (items.size()>0) { // passes specified?
							  try {
								  passes=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("GOpack usage: "
								  		+ "-n {n}, n iterations");
							  }
						  }
						  break;
					  }
					  
					  // 'restart', meaning start with the parent radii 
					  case 'r':
					  {
						  if (gotflag)
							  break;
						  gotflag=true;
						  gotc=c;
						  if (items.size()>0) { // passes specified?
							  try {
								  passes=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("GOpack usage: "
								  		+ "-n {n}, n iterations");
							  }
						  }
						  break;
					  }
					  
					  // max packing
					  case 'm': 
					  {
						  if (gotflag) // preempted?
							  break;
						  gotflag=true;
						  gotc=c;
						  goPack.setCorners(null,null); // null corner info; mode to MAX_PACK
						  goPack.setMode(GOpacker.MAX_PACK); // default
						  
						  // Is this a sphere? then handled with a punctured face
						  // set bdry rad/centers (if there are 3 bdry verts)
						  if (goPack.setSphBdry()>0) 
							  goPack.setMode(GOpacker.FIXED_BDRY);
							  
						  if (items.size()>0) { // passes specified?
							  try {
								  passes=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("GOpack usage: "
								  		+ "-n {n}, n iterations");
							  }
						  }
						  break;
					  }
					  
					  // harmonic layout, i.e., fixed boundary radii/centers
					  case 'h':
					  {
						  if (gotflag) // preempted?
							  break;
						  if (packData.hes!=0)
							  throw new ParserException("GOpack usage: "
							  		+ "-h is only for euclidean packings");
						  gotflag=true;
						  gotc=c;
						  goPack.setMode(GOpacker.FIXED_BDRY);
						  
						  if (items.size()>0) { // passes specified?
							  try {
								  passes=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("GOpack usage: "
								  		+ "-n {n}, n iterations");
							  }
						  }
						  break;
					  }
					  // give 3 or more corners for polygonal packing; 
					  //     get 'passes' via -n flag
					  case 'b': 
					  {
						  int n=0;
						  try {
							  n=Integer.parseInt(str.substring(2));
							  if (n<3)
								  throw new ParserException("GOpack: usage "
								  		+ "-c {v1 v2 v3 ..}. Must be at least 3 "
								  		+ "corner verts");
							  int []pCorners=new int[n];
							  double []pAngles=null;
							  int m=items.size();
							  NodeLink crns=null;
							  if (m==1) { // must be something like 'Vlist'
								  crns=new NodeLink(packData,items);
								  if (crns!=null) {
									  m=crns.size();
									  if (m!=n)
										  throw new ParserException("GOpack usage: "
										  		+ "corner specification error");
									  for (int i=0;i<m;i++)
										  pCorners[i]=crns.get(i);
								  }
							  }
							  else if (m<n)
								  throw new ParserException("GOpack usage -c problem");

							  else {  // read n corner vertices and (possibly) n angles

								  for (int i=0;i<n;i++) 
									  pCorners[i]=Integer.parseInt(items.get(i));
								  // read n corner interior angles theta/pi 
								  //     (default to pi-2pi/n)
								  if (m>n) {
									  if (m<2*n)
										  throw new ParserException("GOpack usage "
										  		+ "-c problem");
									  pAngles=new double[n];
									  for (int i=0;i<n;i++)
										  pAngles[i]=Double.parseDouble(items.get(n+i));
								  }
							  }
							  
							  // set corner data up, mode to POLY_PACK
							  count +=goPack.setCorners(pCorners,pAngles);
							  
						  } catch (Exception ex) {
							  throw new ParserException("GOpack: usage -c, "
							  		+ "failure to read corner vert");
						  }
						  count++;
						  break;
					  }

					  case 'l': // testing bdry layout
					  {
						  if (gotflag)
							  break;
						  gotflag=true;
						  gotc=c;
						  break;
					  }
					  // get 'passes'
					  case 'n':
					  {
						  try {
							  passes=Integer.parseInt(items.get(0));
						  } catch (Exception ex) {
							  throw new ParserException("GOpack usage: "
							  		+ "-n {n}, n iterations");
						  }
						  count++;
						  break;
					  }

					  // print the l2 quality
					  case 'q':
					  {
						  double results=goPack.l2quality(.001);
						  CirclePack.cpb.msg("GOpack quality result: "+results);
						  count++;
						  break;
					  }
					  
					  // close the GOpacker
					  case 'x':
					  {
						  if (packData.rePacker!=null) {
							  double results=goPack.l2quality(.001);
							  CirclePack.cpb.msg("exiting 'GOpack', last "
							  		+ "quality reading: "+results);
							  packData.rePacker=null;
						  }
						  return 1;
					  }
					  
					  // status of packer?
					  case '?':
					  {
						  CirclePack.cpb.msg(goPack.getStatus());
						  break;
					  }

					  } // end switch
				  }

				  else { // no flags, only thing here should be 'passes'
					
					  try {
						  passes=Integer.parseInt(items.get(0));
					  } catch(Exception ex) {
						  passes=defaultPasses;
					  }
				  }
				  
			  } // end of while through flag sequences
			  
			  if (gotflag) {
				  switch(gotc) {
				  case 'r':
				  {
					  count+=goPack.reStartRiffle(passes);
					  CirclePack.cpb.msg("GOpack restarted using data "
					  		+ "from the packing, "+count+" cycles");
					  break;
				  }
				  case 'm':
				  {
					  count+=goPack.continueRiffle(passes);
					  CirclePack.cpb.msg("GOpack continued for max packing, "+
							  count+" cycles");
					  break;
				  }
				  case 'c':
				  {
					  count+=goPack.continueRiffle(passes);
					  CirclePack.cpb.msg("GOpack continued with its own data, "+count+" cycles");
					  break;
				  }
				  case 'h': // do harmonic layout
				  {
					  count += goPack.layoutCenters();
					  CirclePack.cpb.msg("GOpack: harmonic layout of local "
					  		+ "'centers' based on bdry and 'radii'");
					  break;
				  }
				  case 'l': // layout boundary only
				  {
					  count +=goPack.layoutBdry();
					  goPack.reapResults();
					  break;
				  }
				  } // end of switch
			  }

			  return count;
		  }
	      break;
	  } // end of 'g'
	  case 'h':
	  {
	      // =========== h_g_bar ===========
	      if (cmd.startsWith("h_g_bar")) {
	    	  PackData Hp=CPBase.cpDrawing[0].getPackData();
	    	  PackData Gp=CPBase.cpDrawing[1].getPackData();
	    	  
	    	  // check: status? euclidean? same size? 
	    	  if (HarmonicMap.ck_size(Hp,Gp)==0) {
	    		  return 0;
	    	  }

	    	  // TODO: may want options to enforce these properties
	    	  /*
	    	  KData []HK_ptr=Hp.kData;
	    	  RData []HR_ptr=Hp.rData;
	    	  RData []GR_ptr=Gp.rData;

	    	  for (j=1;(j<=Hp.nodeCount && hit==0);j++) {
	    	    // bdry radii must be larger in Hp 
	    	    if (HK_ptr[j].bdryFlag!=0) {
	    	      if (GR_ptr[j].rad>HR_ptr[j].rad) hit++;
	    	    }
	    	    // Hp should be locally univalent
	    	    else if (Math.abs(HR_ptr[j].curv-2.0*Math.PI)>.0001) {
	    	      hit++;
	    	    }
	    	  }
	    	  if (hit>0) // problem data
	    		  CirclePack.cpb.msg("h_g_bar: failure: either radii of "+
	    				  "p1 not all less than p0 or else p0 is not locally univalent\n");
	    	  */
	    	  
	    	  // duplicate p0 in p2, then update centers/radii
	    	  int holdPNum=packData.packNum;
	    	  PackData tmpPD=Hp.copyPackTo();
	    	  CirclePack.cpb.swapPackData(tmpPD,2,false);
			  if (holdPNum==2) 
				  packData=CPBase.cpDrawing[2].getPackData();
			  
			  for (int v=1;v<=tmpPD.nodeCount;v++) {
					packData.setCenter(v,Hp.getCenter(v).add(Gp.getCenter(v).conj()));
			  }

	    	  jexecute(CPBase.packings[2],"set_screen -a");
	    	  CirclePack.cpb.msg("h_g_bar: p2 contains the data for h+conj(g)");
	    	  return 1;
	      }
	      
	      // =========== h_g_add ===========
	      if (cmd.startsWith("h_g_add")) {
	    	  PackData Hp=CPBase.cpDrawing[0].getPackData();
	    	  PackData Gp=CPBase.cpDrawing[1].getPackData();
	    	  
	    	  // check: status? euclidean? same size? 
	    	  if (HarmonicMap.ck_size(Hp,Gp)==0) {
	    		  return 0;
	    	  }
	    	  
	    	  // duplicate p0 in p2, then update centers/redii
	    	  int holdPNum=packData.packNum;
	    	  PackData tmpPD=Hp.copyPackTo();
	    	  CirclePack.cpb.swapPackData(tmpPD,2,false);
			  if (holdPNum==2)
				  packData=CPBase.packings[2];

	    	  // TODO: only want rad/center
			  for (int v=1;v<=tmpPD.nodeCount;v++) {
				  tmpPD.setRadius(v,Hp.getRadius(v)+Gp.getRadius(v));
				  tmpPD.setCenter(v,Hp.getCenter(v).add(Gp.getCenter(v)));
			  }

	    	  jexecute(CPBase.packings[2],"set_screen -a");
	    	  CirclePack.cpb.msg("h_g_add: p2 contains the data for h+conj(g)");
	    	  return 1;
	      }
	      break;
	  } // end of 'h'
	  case 'i':
	  {
		  break;
	  } // end of 'i'
	  case 'j':
	  {
	      // =========== j_ftn ==============
		  // OBE: call "create j_ftn ..."
	      if (cmd.startsWith("j_ftn")) {
	    	  StringBuilder strbld=new StringBuilder("create j_ftn ");
	    	  Iterator<String> sis=items.iterator();
	    	  while (sis.hasNext()) {
	    		  strbld.append(sis.next()+" ");
	    	  }
	    	  return jexecute(packData,strbld.toString());
	      }
	      break;
	  } // end of 'j'
	  case 'k':
	  {
		  break;
	  } // end of 'k'
	  case 'l':
	  {
		  // ============== load_pack =======
		  if (cmd.startsWith("load_pac")) {
				File theFile;
				boolean swdir=true;
				// '-f' flag means do not change directory
				try {
//					items=flagSegs.get(0);
					if (items.get(0).startsWith("-f"))
						swdir=false;
				} catch (Exception ex) {
				}
				if ((theFile=FileDialogs.loadDialog(FileDialogs.FILE,swdir))!=null) {
					try {
						jexecute(packData,"disp -w");
						jexecute(packData,"Read " + theFile);
						packData.setName(theFile.getName());
						packData.cpDrawing.repaint();
					} catch (Exception ex) {
						throw new ParserException("failed in loading file: "+ex.getMessage());
					}
					count++;
					return packData.nodeCount;
				}
				return 0;
		  }
	      break;
	  } // end of 'l'
	  case 'm': 
	  {
		  // ============ map ====================
		  if (cmd.startsWith("map")) {
			  if (packData.vertexMap==null || packData.vertexMap.size()==0) {
				  CirclePack.cpb.errMsg(
						  "usage 'map': packing has no 'vertexMap'");
				  return 0;
			  }
			  
			  // check for/remove 'reverse' flag -r
			  boolean reverse=false;
			  String str=items.get(0);
			  if (str.startsWith("-r")) {
				  reverse=true;
				  items.remove(0);
				  if (items.size()==0) { // there must be later flags
					  flagSegs.remove(0);
				  }
			  }
			  
			  // there should be one action to process
			  items=flagSegs.get(0);
			  str=items.get(0);
				  
			  // faces?
			  if (str.startsWith("-f")) {
				  items.remove(0);
				  boolean trans2q=false;
				  int qnum=-1;
				  if (items.size()==0) { // must be -q flag
					  items=flagSegs.get(1);
					  if ((qnum=StringUtil.qItemParse(items))>=0 &&
							  qnum<CPBase.NUM_PACKS &&
							  CPBase.packings[qnum].status) {
						  trans2q=true;
						  items.remove(0);
					  }
					  else {
						  throw new ParserException(
							"usage: 'map -f -q{p} {f..}' parsing failure");
					  }
				  }
			  
				  FaceLink flink=new FaceLink(packData,items);
				  if (trans2q) {
					  PackData qdata=CPBase.packings[qnum];
					  CPBase.Flink=new FaceLink(packData);
					  Iterator<Integer> fis=flink.iterator();
					  while (fis.hasNext()) {
						  int f=fis.next();
						  HalfEdge he=packData.packDCEL.faces[f].edge;
						  int wa=-1;
						  int wb=-1;
						  if (reverse) {
							  wa=packData.vertexMap.findV(he.origin.vertIndx);
							  wb=packData.vertexMap.findV(he.twin.origin.vertIndx);
						  }
						  else {
							  wa=packData.vertexMap.findW(he.origin.vertIndx);
							  wb=packData.vertexMap.findW(he.twin.origin.vertIndx);
						  }
						  HalfEdge qedge=qdata.packDCEL.findHalfEdge(wa,wb);
						  if (qedge!=null) {
							  CPBase.Flink.add(qedge.face.faceIndx);
							  count++;
						  }
					  }
				  }
				  else {
					  CPBase.Flink=flink;
					  count += flink.size();
				  }
				  return count;
			  } // done with face case

			  // reaching here, must be vertices w or w/o flag
			  if (str.startsWith("-v") || str.startsWith("-c")) {
				  items.remove(0); // shuck this flag
				  if (items.size()==0) 
					  throw new ParserException(
							  "usage 'map': no vertices listed");
			  }
			  
			  NodeLink vlist=new NodeLink(packData,items);
			  CPBase.Vlink=new NodeLink();
			  Iterator<Integer> vis=vlist.iterator();
			  while (vis.hasNext()) {
				  int v=vis.next();
				  int w=-1;
				  if (reverse) {
					  w=packData.vertexMap.findV(v);
				  }
				  else
					  w=packData.vertexMap.findW(v);
				  if (w>0) {
					  CPBase.Vlink.add(w);
					  count++;
				  }
			  } 
			  return count;
		  }
		  

			  
	      // ============ mode_change =============
		  else if (cmd.startsWith("mode_chan")) {
	    	  String str=null;
			  try {
				  items=(Vector<String>)flagSegs.remove(0);
				  str=items.get(0);
				  for (int j=0;j<CursorCtrl.scriptModes.size();j++) {
					  MyCanvasMode mcm=CursorCtrl.scriptModes.get(j);
					  if (str.equals(mcm.nameString)) {
						  PackControl.activeFrame.mainToolHandler.setCanvasMode(mcm);
						  return 1;
					  }
				  }
			  } catch(Exception ex) {} // go to default
			  PackControl.activeFrame.mainToolHandler.setCanvasMode(ActiveWrapper.defaultMode);
			  return 1;
	      }
	      // ============ msg =============
		  else if (cmd.startsWith("msg") || 
				  cmd.startsWith("messa")) {
			  if (flagSegs.size()>0) {
				  StringBuilder strbld=
					new StringBuilder();				  items=flagSegs.get(0);
				  for (int j=0;j<items.size();j++) {
					  strbld.append(items.get(j));
					  strbld.append(" ");
				  }
				  
				  CirclePack.cpb.msg(strbld.toString());
				  return 1;
			  }
			  return 0;
		  }
		  
	      break;
	  }
	  case 'M':
	  {
//		  if (cmd.startsWith("mlhistodegree")) {
//			  MatLabActions.DegreeHistoPlot(packData);
//		  }
		  // =========== Map ============
	      if (cmd.startsWith("Map")) { // expect p q [flagged options]
			  PackData p=null,q=null;
			  int pnm,qnm;
			  
			  // first check for options not requiring p q.
			  try {
				  items=(Vector<String>)flagSegs.remove(0);
				  String str=(String)items.remove(0);
				  if (str.startsWith("-x")) { 
					  PackControl.mapCanvasAction(false);
					  return 1;
				  }
				  if (str.startsWith("-o")) { // open with current packings
					  PackControl.mapCanvasAction(true);
					  return 1;
				  }
				  if (str.startsWith(("-t"))) { // teleTool
					  if (str.startsWith("-tNO")) { 
						  PackControl.mapPairFrame.setTeleState(false);
					  }
					  else if (str.startsWith("-tYES")) { 
						  PackControl.mapPairFrame.setTeleState(true);
					  }
					  return 1;
				  }
				  
				  // otherwise, find the specified p and q
				  pnm=Integer.parseInt(str);
				  qnm=Integer.parseInt((String)items.remove(0));
				  if (pnm<0 || pnm>=CPBase.NUM_PACKS || qnm<0 || qnm>=CPBase.NUM_PACKS)
					  throw new ParserException();
				  p=CPBase.cpDrawing[pnm].getPackData();
				  q=CPBase.cpDrawing[qnm].getPackData();
			  } catch (Exception ex) {
				  throw new ParserException("usage: Map p q [options]");
			  }
			  
			  // no other data? default to open (if not open) or resetting pack numbers
			  if (flagSegs.size()==0) {
				  PackControl.openMap(pnm,qnm);
				  return 1;
			  }
			  
			  // otherwise, parse remaining data. 
			  Iterator<Vector<String>> its=flagSegs.iterator();
			  String str=null;
			  while (its.hasNext()) {
				  items=(Vector<String>)its.next();
				  str=(String)items.remove(0);
				  switch(str.charAt(1)) {
				  case 'x': // close map windows
				  {
					  PackControl.mapCanvasAction(false);
					  return 1;
				  }
				  case 'o': // open map windows
				  {
					  PackControl.mapPairFrame.setDomainNum(pnm);
					  PackControl.mapPairFrame.setRangeNum(qnm);
					  PackControl.mapCanvasAction(true);
					  count++;
					  break;
				  }
				  // NOTE: these don't effect 'PairedFrame' or its packings
				  case 'f': // faces
				  {
					  FaceLink facelist=new FaceLink(p,items);
					  Iterator<Integer> flist=facelist.iterator();
					  int f;
					  while (flist.hasNext()) {
						  f=(Integer)flist.next();
						  count+=p.face_map_action(q,f,true);
					  }
					  break;
				  }
				  case 'c': // circles
				  case 'v': // circles
				  {
					  NodeLink vertlist=new NodeLink(p,items);
					  Iterator<Integer> vlist=vertlist.iterator();
					  int v;
					  while (vlist.hasNext()) {
						  v=(Integer)vlist.next();
						  count+=p.circle_map_action(q,v,true);
					  }
					  break;
				  }
				  } // end of switch
			  } // end of while
			  return count;
	      }
	      // ============ mode_change =============
	      else if (cmd.startsWith("mode_chan")) {
	    	  String str=null;
			  try {
				  items=(Vector<String>)flagSegs.remove(0);
				  str=items.get(0);
				  for (int j=0;j<CursorCtrl.scriptModes.size();j++) {
					  MyCanvasMode mcm=CursorCtrl.scriptModes.get(j);
					  if (str.equals(mcm.nameString)) {
						  PackControl.activeFrame.mainToolHandler.setCanvasMode(mcm);
						  return 1;
					  }
				  }
			  } catch(Exception ex) {} // go to default
			  PackControl.activeFrame.mainToolHandler.setCanvasMode(ActiveWrapper.defaultMode);
			  return 1;
	      }
	      break;
	  } // end of 'm' and 'M'
	  case 'n': // fall through
	  {
		  // ============ necklace ============
	      if (cmd.startsWith("neckl")) {
//	    	  items=(Vector<String>)flagSegs.get(0); // 
	    	  int n=Integer.parseInt((String)items.get(0));
	    	  
	    	  // TODO: set up flags to designate mode 1. Currently
	    	  //    default to mode 2 (original two-end construction)
	    	  // Note: Integer is for debugging seed.
	    	  PackData pdata=PackCreation.randNecklace(n,2,(Integer)null);

			  if (pdata!=null) {
				  int pnum=packData.packNum;
				  packData=CirclePack.cpb.swapPackData(pdata,pnum,false);
				  return 1;
			  }
			  return 0;
	      }
		  break;
	  }
	  case 'N':
	  {
		  break;
	  } // end of 'n' and 'N'
	  case 'o':
	  {
		  // ========== open ===========
		  if (cmd.startsWith("open") && CPBase.GUImode!=0) {
			  // default to 'active' 
			  if (items==null || items.size()==0) {
				  PackControl.mapCanvasAction(true);
				  PackControl.activeFrame.setState(Frame.NORMAL);
				  count++;
			  }
			  Iterator<String> ws=items.iterator();
			  while (ws.hasNext()) {
				  String windStr=ws.next().toLowerCase();
			  
				  if (windStr.startsWith("adv")) {
					  if (PackControl.frame.getState() == JFrame.ICONIFIED)
						  PackControl.frame.setState(Frame.NORMAL);
					  PackControl.frame.setVisible(true);
				  }
				  else if (windStr.startsWith("act")) { // active packing single window mode
					  PackControl.mapCanvasAction(false);
					  PackControl.activeFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("pair") || windStr.startsWith("map")) { // dual screen mode
					  PackControl.mapCanvasAction(true);
					  PackControl.mapPairFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("hel")) { // help frame
					  PackControl.helpHover.lockframe();
					  PackControl.helpHover.hoverFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("inf")) { // info frame
					  PackControl.packDataHover.setLocked(true);
					  PackControl.packDataHover.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("scre")) { // Screen frame; must preceed 'scr'
					  PackControl.screenCtrlFrame.setTab(1); // show screen shots
					  PackControl.screenCtrlFrame.setVisible(true);
					  PackControl.screenCtrlFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("mes") || windStr.startsWith("msg")) {
					  // if iconified, bring it up
					  if (PackControl.msgHover.isLocked() && 
							  PackControl.msgHover.lockedFrame.getState() == JFrame.ICONIFIED)
						  PackControl.msgHover.lockedFrame.setState(Frame.NORMAL);
					  PackControl.msgHover.lockframe();
				  }
				  else if (windStr.startsWith("conf")) {
					  PackControl.prefFrame=PackControl.preferences.displayPreferencesWindow();
					  PackControl.prefFrame.setVisible(true);
				  }
				  else if (windStr.startsWith("scr")) {
					  // if iconified, bring it up
					  if (PackControl.scriptHover.isLocked() && 
							  PackControl.scriptHover.lockedFrame.getState() == JFrame.ICONIFIED)
						  PackControl.scriptHover.lockedFrame.setState(Frame.NORMAL);
					  else {
						  PackControl.scriptHover.lockframe();
						  PackControl.scriptHover.locked = true;
					  }
				  }
				  else if (windStr.startsWith("fun")) {
					  PackControl.newftnFrame.setVisible(true);
					  PackControl.newftnFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("mob")) {
					  PackControl.mobiusFrame.setVisible(true);
					  PackControl.mobiusFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("www") || windStr.startsWith("bro")) {
					  PackControl.browserFrame.setVisible(true);
					  PackControl.browserFrame.setState(Frame.NORMAL);
				  }
				  else if (windStr.startsWith("abo")) {
					  PackControl.aboutFrame.openAbout();
				  }
				  else if (windStr.startsWith("sav")) { // save frame
					  PackControl.outputFrame.setVisible(true);
					  PackControl.outputFrame.setState(Frame.NORMAL);
				  }

			  } // end of while
			  return 1;
		  }
		  
		  // ========== overlay ========
		  else if (cmd.startsWith("overla")) {
			  
			  PackData qackData=null;
			  CPdrawing qCPS=null;
			  try { // try to read (and remove) -q{p} flag
				  items=(Vector<String>)flagSegs.get(0);
				  if (items.size()==1)
					  flagSegs.remove(0);
				  else 
					  items.remove(0);
				  String st=(String)items.get(0);
				  qackData=PackControl.
					cpDrawing[StringUtil.qFlagParse(st)].
					getPackData();
				  qCPS=qackData.cpDrawing;
				  if (qackData==null || qCPS==null)
					  throw new ParserException();
			  } catch (Exception ex) {
				  throw new ParserException("'overlay' failed");
			  }
			
			  items=flagSegs.get(0);
			  // -w and -w are removed, no effect
			  String fs=items.get(0);
			  if (fs.startsWith("-w")) {
				  flagSegs.remove(0);
			  }
			  
			  // No flag strings? use dispOptions 
			  // (DisplayPanel (checkboxes or tailored string))
			  if (flagSegs==null || flagSegs.size()==0) {
				  Vector<String> all=StringUtil.
					string2vec(packData.cpDrawing.
							dispOptions.toString(),false);
				  Vector<Vector<String>> flgseg=StringUtil.
						  flagSeg(all);
				  count +=DisplayParser.
						  dispParse(packData,qCPS,flgseg);
			  }
			  
			  // send for parsing/execution
			  else { 
				  count +=DisplayParser.
						  dispParse(packData,qCPS,flagSegs);
			  }
			  if (count>0) 
				  PackControl.canvasRedrawer.
				  paintMyCanvasses(qackData,false);
			  return count;
		  }
	      break;
	  } // end of 'o'
	  case 'p':
	  {
		  // ============ path_Mobius ============
	      if (cmd.startsWith("path_Mob") || cmd.startsWith("path_mob")) {
	    	  CPBase.ClosedPath=Mobius.path_Mobius(CPBase.Mob,
	    			  CPBase.ClosedPath,true);
	    	  return 1;
	      }
	      
	      // =============== pave ================
	      if (cmd.startsWith("pave")) {
	    	  
	    	  // get the seed tile barycenter vertex
	    	  int V=-1;
	    	  try {
	    		  items=(Vector<String>)flagSegs.get(0);
	    		  V=NodeLink.grab_one_vert(packData,items.get(0));
	    	  } catch (Exception ex) {
	    		  V=packData.activeNode;
	    	  }
    		  if (V<1 || V>packData.nodeCount)
    			  V=packData.getAlpha();
    		  
    		  TileData td=TileData.paveMe(packData,V);
    		  if (td==null)
    			  return 0;
    		  td.packData=packData;
    		  packData.tileData=td;
    		  return td.tileCount;
	      }
	      
	      // ============ perronDown ============
	      if (cmd.startsWith("perron")) {
	    	  if (packData.hes>0 || packData.haveInvDistances()) {
	    		  CirclePack.cpb.errMsg("'perron' methods only apply "+
	    				  "to eucl/hyp packings without overlaps");
	    		  return 0;
	    	  }

	    	  int passes=2000;
    		  int direction=0;
	    	  items=null;
	    	  Iterator<Vector<String>> fsegs=flagSegs.iterator();
	    	  while (fsegs.hasNext()) {
	    		  items=fsegs.next();
	    		  String str=items.get(0);
	    		  if (StringUtil.isFlag(str)) {
	    			  items.remove(0);
	    			  char c=str.charAt(1);
	    			  switch(c) {
	    			  case 'd': // downward only
	    			  {
	    				  direction=-1;
	    				  break;
	    			  }
	    			  case 'D': // down/up (usual "upward" Perron, a la Beardon/Stephenson)
	    			  {
	    				  direction=-2;
	    				  break;
	    			  }
	    			  case 'u': // upward only
	    			  {
	    				  direction=1;
	    				  break;
	    			  }
	    			  case 'U': // up/down (usual "downward" Perron, a la Bowers)
	    			  {
	    				  direction=2;
	    				  break;
	    			  }
	    			  } // end of switch
	    		  }
			  }

	    	  // item should still contain 'passes', if given
	    	  try {
	    		  passes=Integer.parseInt(items.get(0));
	    	  } catch(Exception ex) {
	    		  passes=2000;
	    	  }

	    	  double []perronResults=new double[4];


	    	  if (packData.hes<0) 
    			  perronResults=HypPacker.hypPerron(packData,direction, passes);

	    	  else 
    			  perronResults=EuclPacker.euclPerron(packData,direction, passes);
	    	  
	    	  if (perronResults[0]<0) {
	    		  CirclePack.cpb.errMsg("Perron failed: deficiencies: \n"+
	    				  "up "+perronResults[1]+", down "+perronResults[2]+", error "+perronResults[3]);
	    		  return 0;
	    	  }
    		  CirclePack.cpb.msg("Perron count = "+(int)perronResults[0]+": deficiencies: \n"+
    				  "up "+perronResults[1]+", down "+perronResults[2]+", error "+perronResults[3]);
    		  return 1;
	      }
	      
	      // ============= pdata ==================
	      else if (cmd.startsWith("pdat")) {
	    	  PackControl.packDataHover.update(packData);
	    	  return 1;
	      }
	      
	      // ============= polypack =============
	      else if (cmd.startsWith("polyp")) {
	    	  int ccount=0;
	    	  boolean useC=true;
	    	  if (items.get(0).equals("-o"))
	    		  useC=false;
	    	  NodeLink clink=new NodeLink(packData,items);
	    	  try {
	    		  Iterator<Integer> clk=clink.iterator();
	    		  while (clk.hasNext()) {
	    			  if (!packData.isBdry(clk.next()))
	    				  throw new DataException();
	    			  ccount++;
	    		  }
	    		  if (ccount<3)
	    			  throw new DataException();
	    	  } catch(Exception ex) {
	    		  throw new DataException();
	    	  }
	    	  
    		  // repack/layout/display
    		  return EuclPacker.polyPack(packData,clink,useC);
	      }

	      // ========== pwd =============
	      else if (cmd.startsWith("pwd")) {
	    	  try {
	    		  CirclePack.cpb.msg(CPFileManager.CurrentDirectory.getCanonicalPath());
	    	  } catch (IOException iox) {
	    		  throw new ParserException("failed to get working directory.");
	    	  }
	    	  return 1;
	      }
	      break;
	  } // end of 'p'
	  case 'q': // fall through
	  case 'Q': 
	  {
		  // ============ quit ===============
		  if (cmd.equals("quit")) {
			  // AF: Changed to use exit function.
	    	  ((PackControl) CirclePack.cpb).queryUserForQuit();
		  }
		  break;
	  } // end of 'q' and 'Q'
	  case 'r': // fall through
	  case 'R':
	  {
		  // =========== rand_pt_read =======
		  if (cmd.startsWith("rand_pt_r")) {
//			  items=(Vector<String>)flagSegs.get(0);
			  String filename=StringUtil.reconItem(items);
			  File dir=CPFileManager.PackingDirectory;
			  BufferedReader fp=
				  CPFileManager.openReadFP(dir,filename,false);
			  if (fp==null) { 
				  throw new InOutException("failed to open "+filename+
						  ", directory "+dir.toString());
			  }
			  UtilPacket uP=new UtilPacket();
			  RandomTriangulation.readPoints(fp,uP);
			  if (uP.errval<0)
				  throw new ParserException("failed to read points");
			  int hes=0;
			  if (uP.rtnFlag==3) // points should be (theta,phi)
				  hes=1; 
			  
			  DelaunayData dData=null;
			  try {
				  dData=new DelaunayData(hes,uP.z_vec);
				  if (hes>0)
					  ProcessDelaunay.sphDelaunay(dData);
				  else
					  ProcessDelaunay.planeDelaunay(dData);
			  } catch (Exception ex) {
				  CirclePack.cpb.errMsg("randomHypTriangulation failed: "
						  +ex.getMessage());
				  return 0;
			  }
			  
			  // ============= create the triangulation
			  Triangulation Tri = dData.getTriangulation();
			  int heS=dData.geometry;
			  PackData randPack=Triangulation.tri_to_Complex(Tri,heS);
			  if (randPack==null) {
				  throw new CombException("'tri_to_Complex' failed");
			  }
		  
			  // put new packing in place
			  randPack.fileName=new String(filename);
			  int pnum=packData.packNum;
			  packData=CirclePack.cpb.swapPackData(randPack,pnum,false);
			  packData.hes=heS;
			  packData.chooseAlpha();
			  packData.chooseGamma();
			  packData.set_aim_default();
			  packData.set_rad_default();
			  return packData.nodeCount;
		  }
		  
		  // =========== rand_tri =========
		  if (cmd.startsWith("rand_tri") || cmd.startsWith("random_tri")) {
			  boolean seed1=false; // true for debug so random seed is not called
			  int randN=200;
			  int heS=0; // default geometry to set
			  Vector<Vector<String>> newFS=null;
			  
			  // these are turned off by default; flags can activate one or more
			  double aspect=0.0; // not set
			  Path2D.Double Gamma=null;
			  Complex Tau=null;
			  
			  // check for (and remove) "-d" flag: seed=(long)1 for debugging
			  if (flagSegs!=null && flagSegs.size()>0) {
				  // create a replacement vector of flag sequences
				  newFS=new Vector<Vector<String>>(flagSegs.size());
				  Iterator<Vector<String>> its=flagSegs.iterator();
				  while (its.hasNext()) {
					  items=(Vector<String>)its.next();
					  String str=(String)items.get(0);
					  if (str.startsWith("-d")) { 
						  seed1=true;
						  items.remove(0);
					  }
					  if (items.size()>0) 
						  newFS.add(items);
				  }
				  if (newFS.size()==0) 
					  newFS=null;
			  }
			  
			  // having removed any -d flag, check for others
			  flagSegs=newFS;
			  if (flagSegs!=null && flagSegs.size()>0) {
				  Iterator<Vector<String>> its=flagSegs.iterator();
				  while (its.hasNext()) {
					  items=(Vector<String>)its.next();
					  String str=(String)items.remove(0);
					  if (StringUtil.isFlag(str)) {
						  switch (str.charAt(1)) {
						  case 'd': // debug; should have been handled
						  {
							  break;
						  }
						  case 'A': // given aspect ratio
						  {
							  aspect=1.0; // default
							  try {
								  aspect=Double.parseDouble(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("usage: -A <a>");
							  }
							  break;
						  }
						  case 'g': // using a path: either default or 'filename'
						  {
							  if (CPBase.ClosedPath!=null) 
								  Gamma=CPBase.ClosedPath;
							  try {
								  if (str.length()>2 && str.charAt(2)=='s') // from script
									  Gamma=PathManager.readpath(StringUtil.reconItem(items),true); 
								  else
									  Gamma=PathManager.readpath(StringUtil.reconItem(items),false);
							  } catch (Exception ex) {
							  }
							  if (Gamma==null) {
								  throw new ParserException("usage: -g[s] <filename>");
							  }
							  break;
						  }
						  case 'S': // Sphere
						  {
							  int n=200;
							  try {
								  n=Integer.parseInt((String)items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("usage: -S <n>");
							  }
							  if (n<12) n=12;
							  randN=n;
							  heS=1;
							  break;
						  }
						  case 'T': // torus, read Tau (eventually as COMPLEX) or default
						  {
							  Tau=new Complex(.5,Math.sqrt(2.0)); // default
							  if (items.size()>1) {
								  try {
									  Tau.x=Double.parseDouble((String)items.get(0));
									  Tau.y=Double.parseDouble((String)items.get(1));
								  } catch (Exception ex) {
									  throw new ParserException("usage: -T z.x z.y");
								  }
							  }
							  break;
						  }
						  case 'u': // unit disc
						  {
							  Gamma=PathUtil.getCirclePath(1.0,new Complex(0.0),128);
							  break;
						  }
						  case 'N': // number of interior random points; default 200
						  {
							  int n=200;
							  try {
								  n=Integer.parseInt((String)items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("usage: -N <n>");
							  }
							  if (n<12) n=12;
							  randN=n;
							  break;
						  }
						  case 'Z': // zigzag method
						  {
							  if (CPBase.ClosedPath==null)
								  throw new ParserException(
										  "usage: there must be a current path");
							  int n=200;
							  try {
								  n=Integer.parseInt((String)items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("usage: -Z <n>");
							  }
							  if (n<12) n=12;
							  randN=n;
							  break;
						  }
						  } // end of switch
					  }
				  } // end of while
			  } // end of checking flags
			  
			  PackData randPack=null;
			  // default case?
			  if (heS<=0 && aspect<=0 && Tau==null && Gamma==null) {
				  randPack=RandomTriangulation.randomHypKomplex(randN,seed1);
				  if (randPack==null) {
					  throw new CombException(
							  "Random disc packing has failed");
				  }
				  heS=-1;
			  }
			  else {
				  Triangulation Tri=RandomTriangulation.random_Triangulation(randN,seed1,
						  heS,aspect,Gamma,Tau);
				  if (Tri==null) {
					  throw new CombException("random_Triangulation failed");
				  }
				  
				  try {
					  randPack=Triangulation.tri_to_Complex(Tri,heS);
					  if (randPack==null) {
						  throw new CombException("'tri_to_Complex' failed");
					  }

					  // "prune" the packing
					  CombDCEL.pruneDCEL(randPack.packDCEL);
					  randPack.packDCEL.fixDCEL(randPack);
				  } catch (Exception ex) {
					  throw new DataException("tri_to_Complex failed: "+ex.getMessage());
				  }
			  }
			  
			  // put new packing in place
			  randPack.fileName=new String("rand_pack");
			  int pnum=packData.packNum;
			  packData=CirclePack.cpb.swapPackData(randPack,pnum,false);
			  packData.hes=heS;
			  
			  // for rectangles, 1,2,3,4 are to be the corners
			  if (aspect>0.0 && Tau==null && Gamma==null) { 
				  double a2=aspect*aspect;
				  double Asp_x=Math.sqrt((a2)/(a2+1.0));
				  double Asp_y=Math.sqrt(1.0/(a2+1.0));
				  Complex []corner=new Complex[5]; 
				  corner[1]=new Complex(-Asp_x,Asp_y);
				  corner[2]=new Complex(-Asp_x,-Asp_y);
				  corner[3]=new Complex(Asp_x,-Asp_y);
				  corner[4]=new Complex(Asp_x,Asp_y);
				  int v1=NodeLink.grab_one_vert(packData,"c "+corner[1].x+" "+corner[1].y+" b");
				  int v2=NodeLink.grab_one_vert(packData,"c "+corner[2].x+" "+corner[2].y+" b");
				  int v3=NodeLink.grab_one_vert(packData,"c "+corner[3].x+" "+corner[3].y+" b");
				  int v4=NodeLink.grab_one_vert(packData,"c "+corner[4].x+" "+corner[4].y+" b");
				  if (v1>0 && v2>0 && v3>0 && v4>0) {
					  packData.packDCEL.swapNodes(v1,1);
					  packData.packDCEL.swapNodes(v2,2);
					  packData.packDCEL.swapNodes(v3,3);
					  packData.packDCEL.swapNodes(v4,4);
				  }
			  }
			  
			  packData.packDCEL.fixDCEL(packData);
			  packData.set_aim_default();
			  packData.set_rad_default();
			  packData.fillcurves();
			  packData.set_plotFlags(); // set all to 1
			  
			  CirclePack.cpb.msg("Created a random packing with "+packData.nodeCount+
			  	" vertices");
			  return packData.nodeCount;
		  }
		  
		  // =========== random_pack =======
		  if (cmd.startsWith("random_pack")) { // random hyperbolic packing
			  boolean seed1=false;
			  int randN=200;
			  try { // see if count is given
//				  items=(Vector<String>)flagSegs.get(0);
				  String str=(String)items.get(0);
				  if (str.startsWith("-d")) {
					  seed1=true;
					  items.remove(0);
					  str=items.get(0);
				  }
				  randN=Integer.parseInt(str);
				  if (randN<4) 
					  randN=200;
			  } catch (Exception ex) {
				  randN=200;
			  }
			  
			  PackData randPack=null;
			  for (int j=0;j<12;j++) { 
				  try {
					  if ((randPack=RandomTriangulation.randomHypKomplex(
							  randN,seed1))!=null) {
			  
						  // choose alpha far from boundary
						  int da=randPack.gen_mark(new NodeLink(randPack,"b"),-1,false);
						  if (da>0)
							  randPack.setAlpha(da);
						  
						  // put new packing in place
						  int pnum=packData.packNum;
						  packData=CirclePack.cpb.swapPackData(randPack,pnum,false);
						  packData.packDCEL.fixDCEL(packData);
						  jexecute(packData,"max_pack 2000");

						  CirclePack.cpb.msg("Created a random packing in the disc with "+packData.nodeCount
								  +" vertices");
						  return packData.nodeCount;
					  }
				  } catch (Exception ex) {}
			  } // end of for loop 
			  
			  // failed several times?
			  throw new ParserException("Random triangulation failed 12 times; try more vertices.");
		  }
		  
		  // =========== ring (OBE: see ======
		  else if (cmd.startsWith("ring")) {
			  CirclePack.cpb.errMsg("The 'ring' command has been replaced by 'frackMe'");
			  return 0;
		  }
		  
		  // =========== rld =======
		  else if (cmd.startsWith("rld")) { // repack, layout, disp
			  jexecute(packData,"repack");
			  jexecute(packData,"layout");
			  jexecute(packData,"disp -wr");
			  return 1;
		  }
		  
		  // =========== rlsd =======
		  else if (cmd.startsWith("rlsd")) { // repack, layout, disp
			  if (packData.hes<0) {
				  CirclePack.cpb.errMsg("Layout using schwarzians is not "
				  		+ "yet allowed in the hyp setting");
				  return 0;
			  }
			  jexecute(packData,"repack");
			  jexecute(packData,"set_sch");
			  jexecute(packData,"layout -s");
			  jexecute(packData,"disp -wr");
			  return 1;
		  }
		  
	      break;
	  } // end of 'r' and 'R'
	  case 's':
	  {
		  
		  // ============== sch_data ============
		  if (cmd.startsWith("sch_data")) {
			  
	    	  if (flagSegs==null || flagSegs.size()==0) {
	    		  CirclePack.cpb.errMsg("usage: sch_data d N -f <filename>");
	    		  return 0;
	    	  }
	    	  boolean randdegree=false;
	    	  boolean randcenter=false;
	    	  
	    	  StringBuilder header=
	    			  new StringBuilder(StringUtil.reconstitute(flagSegs));
	    	  
	    	  // get filename first
	    	  if (!StringUtil.ckTrailingFileName(flagSegs)) {
	    		  CirclePack.cpb.errMsg("sch_data: missing the file name");
	    		  return 0;
	    	  }
	    	  StringBuilder strbuf=new StringBuilder("");
	    	  int code=CPFileManager.trailingFile(flagSegs, strbuf);
	    	  File file=new File(strbuf.toString());
	    	  boolean append=false;
	    	  if ((code & 02) == 02) // append
	    		  append=true;
	    	  BufferedWriter fp=CPFileManager.openWriteFP(
	    			  (File)CPFileManager.PackingDirectory,append,
	    			  file.getName(),false);

	    	  items=flagSegs.get(0);
	    	  int d=6;
	    	  
	    	  // catch randomize flag
	    	  if (StringUtil.isFlag(items.get(0))) {
	    		  if (items.get(0).contains("r"))
	    			  randdegree=true;
	    		  if (items.get(0).contains("c"))
	    			  randcenter=true;
	    	  }
	    	  
	    	  // else d format
	    	  else {
	    		  try {
	    			  d=Integer.parseInt(items.get(0));
	    		  } catch(Exception ex) {
	    			  CirclePack.cpb.errMsg("usage: sch_data d ...");
	    			  return 0;
	    		  }
	    		  if (d<3) {
	    			  CirclePack.cpb.errMsg("usage: sch_data d>=3");
	    			  return 0;
	    		  }
	    	  }

	    	  // get N
	    	  int N=0;
    		  try {
    			  N=Integer.parseInt(items.get(1));
    		  } catch (Exception ex) {
    			  N=0;
    		  }
    		  if (N==0) {
    			  CirclePack.cpb.errMsg("usage: sch_data deg N");
	    		  N=12; 
    		  }
	    	  
    		  // put in header
    		  try {
    			  fp.write(header.toString());
    			  fp.write("\n\n");
    		  } catch(Exception ex) {}
    		  
	    	  // run the trials on temp packing
    		  PackData tmpPack=new PackData(null);
    		  if (!randdegree)
    			  jexecute(tmpPack,"seed "+d);
    		  for (int n=1;n<=N;n++) {
    			  if (randdegree) { // reset d in [3,15]
    				  d=util.DegreeDistribution.getRandDegree();
    				  tmpPack=PackCreation.seed(d,0);
    			  }
    				  
    			  jexecute(tmpPack,"set_rand -.3 3.0 b");
    			  if (!randcenter)
    				  jexecute(tmpPack,"repack");
    			  jexecute(tmpPack,"set_sch");
	    	  
    			  HalfEdge he=tmpPack.packDCEL.vertices[1].halfedge;
    			  try {
    				  do {
    					  fp.write(he.getSchwarzian()+"  ");
    					  he=he.prev.twin; // cclw
    				  } while (he!=tmpPack.packDCEL.vertices[1].halfedge);
    				  fp.write("\n\n");
    			  } catch (Exception ex) {}
    			  count++;
    		  }
    		  try {
    			  fp.flush();
    			  fp.close();
    		  } catch(Exception ex) {
    			  try{
    				  fp.flush();
    				  fp.close();
    			  } catch(Exception iox) {}
    			  throw new InOutException("failed writing sch_data file");
    		  }

	    	  CirclePack.cpb.msg("Wrote schwarzian data to "+
	    			  CPFileManager.PackingDirectory+File.separator+
		    		  file.getName());

	    	  return count;
		  }
		  
	      // ============== screendump ============
		  else if (cmd.startsWith("screend")) {
	    	  boolean doCanvas=true;
	    	  
	    	  // if a flag(s) is given, then don't do a screendump, must call again
	    	  if (flagSegs!=null && flagSegs.size()>0) {
	    		  Iterator<Vector<String>> fseg=flagSegs.iterator();
	    		  while (fseg.hasNext()) {
	    			  items=fseg.next();
	    			  String str=items.remove(0);
			    	  if (StringUtil.isFlag(str)) {
			    		  try {
			    			  switch(str.charAt(1)) {
			    			  case 'm': // flag to do Map pair instead of active
			    			  {
			    				  doCanvas=false;
			    				  break;
			    			  }
			    			  case 'd': // set directory 
			    			  {
			    				  String newdir=items.get(0);
			    				  if (newdir.startsWith("~")) {
			    					  String pl=null;
			    					  if (newdir.length()>1)
			    							pl=newdir.substring(1);
			    						else pl="";
			    						try {
			    							newdir=new String(CPFileManager.HomeDirectory.getCanonicalPath()+
			    									File.separator+pl);
			    						} catch(Exception ex) {
			    							newdir=new String(System.getProperty("java.io.tmpdir"));
			    						} 
			    				  }
			    				  PackControl.screenCtrlFrame.imagePanel.setDirectory(newdir);
			    				  break;
			    			  }
			    			  case 'b': // set file name root
			    			  {
			    				  PackControl.screenCtrlFrame.imagePanel.setNameField(items.get(0));
			    				  break;
			    			  }
			    			  case 'n': // set slide counter (caution: may end up overwriting)
			    			  {
			    				  double nbr=Double.parseDouble(items.get(0));
			    				  ScreenShotPanel.imageCount=(int)Math.abs(nbr);
			    				  break;
			    			  }
			    			  } // end of switch
			    		  } catch (Exception ex) {
			    			  throw new ParserException("error in flags");
			    		  }
			    	  }
	    			  
	    		  } // end of while for flags
	    		  
	    		  // if 'm' flag was present
	    		  if (!doCanvas)
	    			  PackControl.screenCtrlFrame.imagePanel.storeCPImage(null);
	    		  return 1;
	    	  } // end of flag processing
	    	  
	    	  // if no flags, then do screen dump; null indicates to use mappair frame 
	    	  if (packData.status && PackControl.screenCtrlFrame.imagePanel.storeCPImage(packData))
	    		  return 1;
	    	  return 0;
	      }
	      
		  // =========== scroll fix ===========
	      else if (cmd.startsWith("scrol")) {
			  PackControl.scriptHover.stackScroll.getViewport().revalidate();
			  PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0,0));
			  PackControl.scriptHover.repaint();  
		  }

	      // =============== script =========
		  else if (cmd.startsWith("script")) {
			  String filename=null;
			  try {
//				  items=(Vector<String>)flagSegs.get(0);
				  filename=(String)items.get(0);
			  } catch (Exception ex) {}
			  
			  // note: filename==null means that a dialog will pop up.
			  int n=CPBase.scriptManager.getScript(filename,filename,true);
			  if (n>0) {
				  TrafficCenter.cmdGUI("scroll");
				  ScriptBundle.m_locator.setSuccess();
			  }
			  else
				  ScriptBundle.m_locator.setFailure();
			  return n;
		  }

	      // =========== seed =========  
	      if (cmd.startsWith("seed")) {
	    	  int hes=0;
	    	  int n=6;
	      	  int item_index=0;
	      	  boolean swap=false; 
	     	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	     	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	     		  if (StringUtil.isFlag(items.elementAt(0))) {
	   		  	  	item_index=1;
	   		  	  	switch(items.get(0).charAt(1)) {
	   			  
	   			  // flags for geometry (default is eucl): -h, -s, -e
	     			  case 'h': { 
	       				  hes=-1;
	       				  break;
	       			  }
	       			  case 'e': { 
	       				  hes=0;
	       				  break;
	       			  }
	       			  case 's': { 
	       				  hes=1;
	       				  break;
	       			  }
	       			  case 'm': { // swap 1 and M
	       				  swap=true;
	       				  break;
	       			  }
	   		  	  	} // end of flag switch
	     		  }
	   		  	  // unflagged or fall-through: read 'n', number of petals (default 6) 
	   		  	  if (items.size()>item_index) {
	   		  	  	try {
	   		  	  		n=Double.valueOf(items.get(item_index)).intValue();
	   		  	  		if (n<3)
	   		  	  			n=3;
	   		  	  		if (n>1000) {
	   		  	  			throw new ParserException(
	   		  	  					"'seed' petal count limited to 1000");
	   		  	  		}
	   		  	  	} catch (Exception ex) {n=6;}
	   		  	  }
	     	  } // end of while
	   	  
	     	  try{  // have to hold this; packData get's replaced
	     		  int pnum=packData.packNum;
	     		  PackData newData=PackCreation.seed(n,hes);
	     		  if (newData==null) 
	     			  throw new CombException("seed has failed");
	     		  packData=CirclePack.cpb.swapPackData(newData,pnum,false);
	     		  jexecute(packData,"disp -w -c");
	     	  } catch(Exception ex) {
	     		  throw new ParserException(" "+ex.getMessage());
	     	  }

	     	  // shall we swap so center is index M?
	     	  //   also, set gamma to n and redo layout
	     	  if (swap) {
	     		  packData.swap_nodes(1, n+1);
	     		  packData.setAlpha(n+1);
	     		  packData.setGamma(n);
	     		  jexecute(packData,"layout");
	     	  }
	     	  
	     	  return packData.nodeCount;
	      } // end of 'seed'
	      
		  // ========= set =========
	      if (cmd.startsWith("set_")) {
	    	  cmd=cmd.substring(4);

	    	  // ========= set_accur =========
	    	  if (cmd.startsWith("accur")) {
	    		  double accur=StringUtil.getOneDouble(flagSegs);
	    		  if (accur>.01) accur=.01;
	    		  if (accur<0.0000000000000001) accur=0.0000000000000001;
	    		  // represents max of about 15 decimal accur 
	    		  PackData.TOLER=accur;
	    		  if (PackData.OKERR>(.01)*PackData.TOLER) PackData.OKERR = (.01)*PackData.TOLER;
	    		  return 1;
	    	  }
	    	  	    	  
	    	  // =========== set_dump_format =====
	    	  if (cmd.startsWith("dump_")) {
	    		  return CirclePack.cpb.setIMG(items.get(0));
	    	  }
	      
	    	  // =========== set_display == (full computer screen)
	    	  if (cmd.startsWith("displ") && CPBase.GUImode!=0) {
	    		  double fracMax=-1.0;
	    		  try {
	    			  
	    			  // set relative to screen maximum
	    			  if (items.get(0).startsWith("-m")) {
	    				  try {
	    					  fracMax=Double.parseDouble(items.get(1));
	    				  } catch(Exception ex) {
	    					  fracMax=1.0; // full max
	    				  }
	    			  }
	    			  
	    			  // TODO: other options?
	    		  } catch(Exception e){}
	    		  PackControl pc=(PackControl)CirclePack.cpb;
	    		  pc.resetDisplay(fracMax);
	    		  return 1;
	    	  }

    	  // =========== set_ratio ========
    	  /* Boundary radii of p2 are set using boundary radii of p1
    	  multiplied by values from function specified in "Function" panel. */
    	  if (cmd.startsWith("ratio")) {
//    		  items=(Vector<String>)flagSegs.get(0); // one segment
    		  PackData p1=CPBase.cpDrawing[Integer.parseInt((String)items.get(0))].getPackData();
    		  NodeLink blist=new NodeLink(p1,"b");
    		  PackData p2=CPBase.cpDrawing[Integer.parseInt((String)items.get(1))].getPackData();
    		  if (!p1.status || !p2.status || p1.hes>0 || p2.hes!=0
    				|| blist==null || blist.size()<=0) {
    			  throw new ParserException("need two appropriate packings.");
    		  }
    		  if (CirclePack.cpb.FtnParser.funcHasError()) {
    			  throw new ParserException("function specification is not valid");
    		  }
    		  Iterator<Integer> bl=blist.iterator();
    		  int v;
    		  while (bl.hasNext()) {
    			  v=(Integer)bl.next();
    			  Complex ctr=p1.getCenter(v);
    			  double rad=p1.getRadius(v);
    			  if (p1.hes<0) {
    				  CircleSimple sc=
    					  HyperbolicMath.h_to_e_data(p1.getCenter(v),p1.getRadius(v));
    				  ctr=sc.center;
    				  rad=sc.rad;
    			  }
    			  Complex w=CirclePack.cpb.getFtnValue(ctr);
    			  p2.setRadius(v,w.abs()*rad);
    			  count++;
    		  }
    		  return count;
    	  }

	      // ========= set_brush ============
    	  if (cmd.startsWith("brush")) {
	    	  int n=1;
	    	  if (items!=null && items.size()>0) {
	    		  try {
//        		  items=(Vector<String>)flagSegs.get(0); // 
    			  String str=(String)items.get(0);
    			  n=Integer.parseInt(str);
	    		  } catch(ParserException pex) {}
	    	  }
	    	  if (n<0 || n>24) n=1; // 0-25 are values for LineThick slider in ScreenPanel. 
	    	  packData.cpDrawing.setLineThickness(n+1);
	    	  if (CPBase.GUImode!=0)
	    		  PackControl.screenCtrlFrame.screenPanel.setLine(n+1);
	    	  return 1;
    	  }
	    	  
    	  // ========= set_ps_brush ======== (goes in postscript file, if open)
    	  if (cmd.startsWith("ps_brush")) {
       		  int n=1;
       		  try {
//        		  items=(Vector<String>)flagSegs.get(0); // 
        		  String str=(String)items.get(0);
    			  n=Integer.parseInt(str);
    		  } catch(ParserException pex) {}
    		 if (n<0 || n>12) n=1; // 0-12 are values for LineThick slider in SupportFrame
    		 try {
    			 pF.postLineThickness(n);
    		 } catch(Exception ex) {
    			 throw new DataException("'PostFactory' not open?");
    		 }
    		 return 1;
    	  }
    	  

    	  // ========= set_mobius (set_Mobius) ==============
    	  if (cmd.trim().equalsIgnoreCase("mobius")) {
    		  Mobius mob=null;
    		  
    		  String str=items.get(0);
    		  if (StringUtil.isFlag(str)) {
    			  
    			  // map 3 complex points to 3 other complex points
    			  if (str.equals("-xyzXYZ")) {
    				  items.remove(0);

    				  Iterator<String> its=items.iterator();

    				  // read 6 complex numbers
    				  try {
    					  Complex x=new Complex(Double.parseDouble((String)its.next()),
        					  Double.parseDouble((String)its.next()));
    					  Complex y=new Complex(Double.parseDouble((String)its.next()),
        					  Double.parseDouble((String)its.next()));
    					  Complex z=new Complex(Double.parseDouble((String)its.next()),
        					  Double.parseDouble((String)its.next()));
    					  Complex X=new Complex(Double.parseDouble((String)its.next()),
        					  Double.parseDouble((String)its.next()));
    					  Complex Y=new Complex(Double.parseDouble((String)its.next()),
        					  Double.parseDouble((String)its.next()));
    					  Complex Z=new Complex(Double.parseDouble((String)its.next()),
        					  Double.parseDouble((String)its.next()));
    					  CPBase.Mob=Mobius.mob_xyzXYZ(x, y, z, X, Y, Z,0,0);
    				  } catch (Exception ex) {
    					  throw new MobException("Mobius flag -xyzXYZ format error");
    				  }

    				  return 1;
    			  }
    			  
    			  // otherwise, send flags to define 'HalfLink'
    			  else { 
    				  HalfLink hlink=new HalfLink(packData,items);
    				  CPBase.Mob=PackData.holonomyMobius(packData,hlink);
    				  return 1;
    			  }
    		  }
    		  
    		  // default is 8 doubles, plus perhaps one integer
    		  else {
    			  
    			  Iterator<String> its=items.iterator();
        		  
    			  try {
    				  Complex a=new Complex(Double.parseDouble((String)its.next()),
    					  Double.parseDouble((String)its.next()));
    				  Complex b=new Complex(Double.parseDouble((String)its.next()),
    					  Double.parseDouble((String)its.next()));
    				  Complex c=new Complex(Double.parseDouble((String)its.next()),
    					  Double.parseDouble((String)its.next()));
    				  Complex d=new Complex(Double.parseDouble((String)its.next()),
    					  Double.parseDouble((String)its.next()));
    				  mob=new Mobius(a,b,c,d);
    			  } catch(Exception ex) {
    				  throw new MobException("Mobius format error");
    			  }
    		  
    			  try {
    				  int flip=Integer.parseInt((String)its.next());
    				  if (flip!=0) mob.oriented=false;
    			  } catch(Exception ex) {} // this integer may not be present
    			  CPBase.Mob=mob; 
    			  return 1;
    		  }
    		  
    	  }
    	  
    	  // ========= set_cycles ============
    	  if (cmd.startsWith("cycle")) {
//    		  items=(Vector<String>)flagSegs.get(0);
    		  try {
    			  int n=Integer.parseInt((String)items.get(0));
    			  if (n>0 && n<1000001) 
    				  CPBase.RIFFLE_COUNT=n;
    		  } catch(Exception ex) {return 0;}
    		  return 1;
    	  }
    	  
    	  // ========== set_sv (set_sphere_view) =======
    	  if (cmd.startsWith("sphere_vi") || cmd.startsWith("sv")) {
    		  boolean inc_flag=false;
    		  double xang=0.0;
    		  double yang=0.0;
    		  double zang=0.0;
    		  try {
//    			  items=(Vector<String>)flagSegs.elementAt(0); // should be just one flag string
    			  if (StringUtil.isFlag(items.elementAt(0))) {
    				  String sub_cmd=(String)items.elementAt(0);
    				  char c=sub_cmd.charAt(1);
    				  items.remove(0);
    				  if (c=='d') { // default
    					  packData.cpDrawing.sphView.defaultView();
    					  return 1;
    				  }
    				  if (c=='t') { // set or set and update
    					  try {
    						  Matrix3D mat3d=new Matrix3D(
    								  Double.parseDouble(items.get(0)),Double.parseDouble(items.get(1)),
    								  Double.parseDouble(items.get(2)),Double.parseDouble(items.get(3)),
    								  Double.parseDouble(items.get(4)),Double.parseDouble(items.get(5)),
    								  Double.parseDouble(items.get(6)),Double.parseDouble(items.get(7)),
    								  Double.parseDouble(items.get(8)));
    						  if (!Matrix3D.isNaN(mat3d))
    							  packData.cpDrawing.sphView.viewMatrix=mat3d;
    						  else return 0;
    					  } catch (Exception ex) {
    						  throw new ParserException("error setting 'viewMatrix'");
    					  }
    					  return 1;
    				  }
    				  if (c=='N') { // look directly at the origin, the north pole
    					  packData.cpDrawing.sphView.viewMatrix=
    							  Matrix3D.FromEulerAnglesXYZ(0.0,0.5*Math.PI,0.5*Math.PI);
    					  return 1;
    				  }
    				  else if (c=='S') { // look directly at infinity, the south pole
    					  packData.cpDrawing.sphView.viewMatrix=
    							  Matrix3D.FromEulerAnglesXYZ(0.0,-0.5*Math.PI,0.5*Math.PI);
    					  return 1;
    				  }
    				  else if (c=='i') // incremental, increments should be given
    					  inc_flag=true;
    			  }
    			  xang=Double.parseDouble(items.elementAt(0))*Math.PI;
    			  yang=Double.parseDouble(items.elementAt(1))*Math.PI;
    			  zang=Double.parseDouble(items.elementAt(2))*Math.PI;
    		  } catch(Exception ex) {
    			  throw new ParserException("error in sph_view data");
    		  }
    		  Matrix3D trans=Matrix3D.FromEulerAnglesXYZ(xang,yang,zang);
    		  if (!Matrix3D.isNaN(trans)) {
    			  if (inc_flag) packData.cpDrawing.sphView.viewMatrix = 
    				  Matrix3D.times(trans,packData.cpDrawing.sphView.viewMatrix);
    			  else 
    				  packData.cpDrawing.sphView.viewMatrix=trans;
    			  return 1;
    		  }
			  throw new DataException("nan error in setting 'sphView'");
    	  }
    	  
          // =============== set_screen =====
    	  // Note: if 'packData.status' is true, parsing takes place in other routine
          if (cmd.startsWith("screen")) {
        	  ViewBox vbox=packData.cpDrawing.realBox;
        	  Complex utilz=new Complex(0.0);
        	  char c;
    	  
        	  if (flagSegs==null || flagSegs.size()==0) {
        		  throw new ParserException("usage: set_screen ...");
        	  }
        	  
        	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
    	  
        	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
        		  if (StringUtil.isFlag(items.elementAt(0))) {
        			  c=items.get(0).charAt(1);
        			  switch(c) {
        			  case 'b': // set real box (lx,ly), (rx,ry)
    				  // TODO: need tailored versions for speed, change 'CPDrawing.update' too
        			  {
        				  double []corners=new double[4];
        				  try {
        					  for (int i=0;i<4;i++)
        						  corners[i]=Double.parseDouble(items.get(i+1));
        					  packData.cpDrawing.realBox.setView(new Complex(corners[0],corners[1]),
        							  new Complex(corners[2],corners[3]));
        				  } catch (Exception ex) {
        					  CirclePack.cpb.myErrorMsg("'"+cmd+"' parsing error.");
        					  return count;
        				  }
        				  count++;
        				  packData.cpDrawing.update(2);
        				  break;
        			  }
        			  case 'd':	// default canvas size, sphView
        			  {
        				  vbox.reset();
        				  count++;
        				  packData.cpDrawing.update(2);
        				  break;
        			  }
        			  case 'f': // scale by given factor
        			  {
        				  try {
        					  count += packData.cpDrawing.realBox.
        							  scaleView(Double.parseDouble(items.get(1)));
        					  packData.cpDrawing.update(2);
        				  } catch (NumberFormatException nfe) {
        					  CirclePack.cpb.myErrorMsg("usage: set_screen -f <x>: "+
        							  nfe.getMessage());
        				  }
        				  break;
        			  }
        			  case 'i': // incremental moves (typically from mouse click)
        			  {
        				  try {
        					  utilz=new Complex(Double.parseDouble(items.get(1)),
        							  Double.parseDouble(items.get(2)));
        					  count +=vbox.transView(utilz);
        				  } catch (NumberFormatException nfe) {
        					  CirclePack.cpb.myErrorMsg("usage: set_screen -i <x> <y>: "+
        				  nfe.getMessage());
        				  }
        				  break;
        			  }
        			  case 'h': // height (or fall through for width)
        			  {}
        			  case 'w': // width
        			  {  	
        				  try {
        					  double f=Double.parseDouble(items.get(1));
        					  count += packData.cpDrawing.realBox.
        							  scaleView(f/vbox.getWidth());
        					  packData.cpDrawing.update(2);
        				  } catch (NumberFormatException nfe) {
        					  CirclePack.cpb.myErrorMsg("usage: set_screen -w(or h) <x>: "+
        							  nfe.getMessage());
        				  }
        				  break;
        			  }
        			  } // end of flag switch
        		  } // done handling a given flag
        		  else { // no flags? default to default screen
        			  vbox.reset();
        			  count++;
        			  packData.cpDrawing.update(2);
        		  }
        	  } // end of while
    	  return count;
          }// done with 'set_screen' (with 'status' false)
    	  
    	  // ========= set_disp_flags (dep: disp_text) ==
          if (cmd.startsWith("disp_fla") || cmd.startsWith("disp_tex")) {
        	  /* CirclePack sends a string to put in DispOptions of 
        	   * designated packing; if this is the active pack, the 
        	   * string is displayed as 'dispText' and checkbox is set.
        	   */
        	  // Reconstitute the string from flag segments, with 
        	  //    separating spaces
        	  String flagstr=StringUtil.reconstitute(flagSegs);
        	  if (flagstr==null) 
        		  return 0;
              packData.cpDrawing.dispOptions.usetext=true;
              packData.cpDrawing.dispOptions.tailored=flagstr;
              if (CPBase.GUImode!=0 && 
            		  packData.packNum==CirclePack.cpb.getActivePackNum()) {
            	  PackControl.screenCtrlFrame.displayPanel.flagField.setText(
            			  packData.cpDrawing.dispOptions.tailored);
            	  PackControl.screenCtrlFrame.displayPanel.setFlagBox(true);
              }
              return 1;
          }
    	  
    	  // ========= set_function_text ===============
          if (cmd.startsWith("fun") || cmd.startsWith("ftn")) {
        	  String ftntext=StringUtil.reconstitute(flagSegs);
        	  if (!CirclePack.cpb.setFtnSpec(ftntext)) {
   				  throw new ParserException(
   						  "Error in function expression: "+
   								  CirclePack.cpb.FtnParser.getFuncErrorInfo());
        	  }
        	  else {
        		  CirclePack.cpb.FtnSpecification=new StringBuilder(ftntext);
        	  }
      		  if (CPBase.GUImode!=0) 
      			  PackControl.newftnFrame.setFunctionText();
      		  return 1;
          }
    	  
    	  // ========= set_path_text ===============
          if (cmd.startsWith("path_tex")) {
        	  String pathtext=StringUtil.reconstitute(flagSegs);
      		  if (!CirclePack.cpb.setParamSpec(pathtext)) {
   				  throw new ParserException(
   						  "Error in path expression: "+
   								  CirclePack.cpb.ParamParser.getFuncErrorInfo());
      		  }
        	  else {
        		  CirclePack.cpb.ParamSpecification=new StringBuilder(pathtext);
        	  }
      		  if (CPBase.GUImode!=0) 
      			  PackControl.newftnFrame.setPathText();
      		  return 1;
          }
          
	      // ======== set_path ===============
	      else if (cmd.startsWith("path")) {
	    	  // no optional string? use Function panel "Utility Path" string
	    	  String ftnstr;
			  if (flagSegs!=null && flagSegs.size()!=0) 
				  ftnstr=StringUtil.reconstitute(flagSegs);
			  else 
				  ftnstr=CirclePack.cpb.ParamParser.getFuncInput();
			  Path2D.Double newpath=PathUtil.path_from_text(ftnstr);
			  if (newpath!=null)
				  CPBase.ClosedPath=newpath;
			  return 1;
	      }
	          	  
    	  // ========= set_grid ============
          if (cmd.startsWith("grid")) {
        	  int mode=0; // 0=circle, 1=rectangle
        	  int lineCount=8;
   			  Complex cent=new Complex(0.0);
   			  double rad=1.0;
   			  Complex lowl=new Complex(-1.0,-1.0);
   			  Complex upr=new Complex(1.0,1.0);
   			  
   			  // get flags
        	  try {
        		  for (int j=0;j<flagSegs.size();j++) {
        			  items=flagSegs.get(j);
        			  String str=items.remove(0);
        			  if (StringUtil.isFlag(str)) {
        				  switch(str.charAt((1))) {
        				  case 'c': // circle 
        				  {
        					  mode=0;
        					  try{
        						  cent=new Complex(Double.parseDouble(items.get(0)),
        								  Double.parseDouble(items.get(1)));
        						  rad=Double.parseDouble(items.get(2));
        					  } catch (Exception ex) {
        						  cent=new Complex(0.0);
        						  rad=1.0;
        					  }
        					  break;
        				  }
        				  case 'r': // rectangle
        				  {
        					  mode=1;
        					  try{
        						  lowl=new Complex(Double.parseDouble(items.get(0)),
        								  Double.parseDouble(items.get(1)));
        						  upr=new Complex(Double.parseDouble(items.get(2)),
        								  Double.parseDouble(items.get(3)));
        					  } catch (Exception ex) {
        						  lowl=new Complex(-1.0,-1.0);
        						  upr=new Complex(1.0,1.0);
        					  }
        					  break;
        				  }
        				  case 'g': // current path
        				  {
        					  if (CPBase.ClosedPath==null)
        						  break;
        					  CPBase.gridLines=new Vector<BaryCoordLink>(2*lineCount+2);
        					  CPBase.gridLines.addAll(PathBaryUtil.fromPath(packData,
        							  CPBase.ClosedPath));
        					  return 1;
        				  }
        				  case 'N': // drop through
        				  case 'n': // count, default to 8
        				  {
        					  try{
        						  lineCount=Integer.parseInt(items.get(0));
        						  if (lineCount<4) lineCount=4;
        					  } catch (Exception ex) {
        						  lineCount=8;
        					  }
        					  break;
        				  }
        				  } // end of switch
        			  } // done with flags
        			  
        		  } // end of for loop
        	  } catch (Exception ex) {
        		  // use default: circle/spoke pattern, lineCount=8
        	  }
        	  
   			  // now call routine
   			  if (mode==0) // circle
   				  return packData.makeGrid(cent,rad,lineCount);
   			  else if (mode==1) // rectangle
   				  return packData.makeGrid(lowl,upr,lineCount);
          }
    	  
    	  // ========= set_custom =============
	      if (cmd.startsWith("custom")) {
        	  try {
        		  packData.cpDrawing.customPS=(String)flagSegs.get(0).get(0);
        	  } catch(Exception ex) {
        		  throw new InOutException("set_custom failed;"+ex.getMessage());
        	  }
        	  return 1;
          }
    	  
    	  // ========== set_fill_opacity =======
          if (cmd.startsWith("fill_op")) {
        	  int opacity=CPBase.DEFAULT_FILL_OPACITY;
        	  try {
        		  opacity=Integer.parseInt(flagSegs.get(0).get(0));
        	  } catch(Exception ex) {
        		  CirclePack.cpb.errMsg("set_fill_opacity failed: "+ex.getMessage());
        	  }
        	  packData.cpDrawing.setFillOpacity(opacity);
        	  return 1;
          }

          // ========== set_sph_opacity =======
          if (cmd.startsWith("sph_op")) {
        	  int opacity=CPBase.DEFAULT_SPHERE_OPACITY;
        	  try {
        		  opacity=Integer.parseInt(flagSegs.get(0).get(0));
        	  } catch(Exception ex) {
        		  CirclePack.cpb.errMsg("set_sph_opacity failed: "+ex.getMessage());
        	  }
        	  packData.cpDrawing.setSphereOpacity(opacity);
        	  return 1;
          }	
          
          // ========== set_variable ================
          // NOTE: may be constructed from ":=" command, 
          //   see 'TrafficCenter'
          
          // TODO: two forms, vname:=?querystring and 
          //   vname:={..cmd..} should trigger calls 
          //   to commands returning values, as in 
          //   'valueExecute'.
          if (cmd.startsWith("var")) {
        	  String vname=null;
        	  try { // only letters/digits allowed in name
        		  StringBuilder nbld=
        			new StringBuilder(items.remove(0));
        		  for (int j=0;j<nbld.length();j++) {
        			  if (!Character.isLetterOrDigit(nbld.charAt(j)))
        				  throw new ParserException("");
        		  }
        		  vname=nbld.toString();
        	  } catch (Exception ex) {
        		  throw new ParserException("problem getting variable name");
        	  }
        	  
        	  // Note: there should be more either in 'items' or 'flagSegs'
        	  if (vname.length()==0 || (items.size()==0 && flagSegs.size()==1)) {
        		  throw new ParserException("problem parsing 'set_variable'");
        	  }
        	  // if items is empty, get rid of it
        	  if (items.size()==0) 
        		  flagSegs.remove(0);
        	  if (!CPBase.varControl.putVariable(packData,vname,flagSegs)) 
        		  return 0;
        	  return 1;
          }
          
          // ========== set_vertexMap ===============
          if (cmd.startsWith("vertex")) {
    		  try { // just one flag sequence
    			  if (!items.get(0).contains("-a")) // no append, so clear
    				  packData.vertexMap=new VertexMap();
    			  items=(Vector<String>)flagSegs.elementAt(0);
    			  if (items.size()==0) items=(Vector<String>)null;
    		  } catch(Exception ex){
    			  items=(Vector<String>)null;
    		  }
        	  
          }
          
          // ========== set_dir ===================
          if (cmd.startsWith("dir")) {
        	  // TODO: want to be able to change the 'PackingDirectory'
          }
          
          // ========== set_?list (VEFGTDZ only) =========
    	  if (cmd.substring(1).startsWith("list")) {
    		  char c=cmd.charAt(0);
    		  try { // just one flag sequence
//    			  items=(Vector<String>)flagSegs.elementAt(0);
    			  if (items.size()==0) items=(Vector<String>)null;
    		  } catch(Exception ex){
    			  items=(Vector<String>)null;
    		  }
    		  switch(c) {
    		  case 'V':
    		  {
    			  if (items==null) {
    				  CPBase.Vlink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Vlink=new NodeLink(packData,items);
    				  count=CPBase.Vlink.size();
    			  }
    			  break;
    		  }
    		  case 'E':
    		  {
    			  if (items==null) {
    				  CPBase.Elink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Elink=new EdgeLink(packData,items);
    				  count=CPBase.Elink.size();
    			  }
    			  break;
    		  }
    		  case 'F':
    		  {
    			  if (items==null) {
    				  CPBase.Flink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Flink=new FaceLink(packData,items);
    				  count=CPBase.Flink.size();
    			  }
    			  break;
    		  }
    		  case 'T': // tiles in 'tileData' only
    		  {
    			  if (items==null) {
    				  CPBase.Tlink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Tlink=new TileLink(packData.tileData,items);
    				  count=CPBase.Tlink.size();
    			  }
    			  break;
    		  }
    		  case 'G':
    		  {
    			  if (items==null) {
    				  CPBase.Glink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Glink=new GraphLink(packData,items);
    				  count=CPBase.Glink.size();
    			  }
    			  break;
    		  }
    		  case 'D': // linked list of Doubles
    		  {
    			  if (items==null) {
    				  CPBase.Dlink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Dlink=new DoubleLink(items);
    				  count=CPBase.Dlink.size();
    			  }
    			  break;
    		  }
    		  case 'Z':
    		  {
    			  if (items==null) {
    				  CPBase.Zlink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Zlink=new PointLink(items);
    				  count=CPBase.Zlink.size();
    			  }
    			  break;
    		  }
    		  case 'B':
    		  {
    			  if (items==null) {
    				  CPBase.Blink=null;
    				  count=1;
    			  }
    			  else {
    				  CPBase.Blink=new BaryLink(packData,items);
    				  count=CPBase.Blink.size();
    			  }
    			  break;
    		  }
    		  } // end of switch
    		  
    		  return count;
    	  }
          
      } // done with all 'set_' commands
	      
	  // ============= smooth =========
	  if (cmd.startsWith("smoo")) {
		  // Note: if smoother is to work in concert with a 'MicroGrid', 
		  //  then it is initiated in that 'MicroGrid'.
		  if (packData.smoother==null) { // try to create
			  // start the smoother
			  packData.smoother=new Smoother(packData,null); // no 'MicroGrid' attached
			  if (packData.smoother==null) {
				  CirclePack.cpb.errMsg(
						  "error: smoother failed to start for pack "+
								  packData.packNum);
				  return 0;
			  }
		  }
		  if (flagSegs==null || flagSegs.size()==0)
			  return 1;

		  // parse the various flags
		  Iterator<Vector<String>> fst=flagSegs.iterator();
		  while (fst.hasNext()) {
			  items=fst.next();
			  String str=items.get(0);
			  if (str.startsWith("-q")) // toss, redundant (or wrong)
				  continue;
			  if (!StringUtil.isFlag(str)) {
				  CirclePack.cpb.errMsg("usage: smoother "+
						  "-q{n} -a -b {b} -c {n] -d {flags} "+
						  "-r -s {x} -x");
				  return 0;
			  }
			  items.remove(0); // toss the flag, there may be other stuff
			  char c=str.charAt(1);
			  switch(c) {
			  case 'a': // accept adjustments
			  {
				  count +=packData.smoother.acceptNewData();
				  break;
			  }
			  case 'b':  // set balance
			  {
				  try {
					  double value=Double.parseDouble(items.get(0));
					  count += packData.smoother.setRadPressure(value);
				  } catch(Exception ex) {
					  CirclePack.cpb.errMsg("usage: smoother -b {b}");
				  }
				  break;
			  }
			  case 'c': // cycles to run 
			  {
				  try {
					  int cycles=Integer.parseInt(items.get(0));
					  count += packData.smoother.computeCycles(cycles);
				  } catch(Exception ex) {
					  CirclePack.cpb.errMsg("usage: smoother -c {n}");
				  }
				  break;
			  }
			  case 'd': // display 
			  {
				  packData.smoother.dispNewData(flagSegs);
				  break;
			  }
			  case 'r': 
			  {
				  packData.smoother.reset();
				  break;
			  }
			  case 's': 
			  {
				  try {
					  double value=Double.parseDouble(items.get(0));
					  count += packData.smoother.setSpeed(value);
				  } catch(Exception ex) {
					  CirclePack.cpb.errMsg("usage: smoother -s {s}");
				  }
				  break;
			  }
			  case 'x': // kill the smoother
			  {
				  count +=packData.smoother.exit();
				  break;
			  }
			  } // end of switch
			  return count;				
		  } // end of while
	  }
			
	  // ========= socketServer =========
	  if (cmd.startsWith("socketS")) {
		  if (CPBase.cpMultiServer!=null)
			  throw new InOutException("Socket serve already exists: host "+
					  CPBase.cpSocketHost+" port "+CPBase.cpSocketPort);
		  int port=0;
		  try {
			  port=Integer.parseInt(items.get(0));
		  } catch (Exception ex) {
			  if (CPBase.cpSocketPort>0)
				  port=CPBase.cpSocketPort;
			  else
				  port=3736;
		  }
		  return PackControl.startCPSocketServer(port);
	  }
	  
	  // ========= split_edge ==============
	  if (cmd.startsWith("split_edg")) {
    	  items=flagSegs.elementAt(0); // should be only one segment
   		  EdgeLink edgeLink=new EdgeLink(packData,items);
   		  
   		  if (edgeLink==null || edgeLink.size()==0) 
   			  return 0;
   		  Iterator<EdgeSimple> elst=edgeLink.iterator();
   		  while (elst.hasNext()) {
   			  EdgeSimple edge=elst.next();
			  HalfEdge he=packData.packDCEL.findHalfEdge(edge);
			  if (he==null)
				  CirclePack.cpb.errMsg(
						  "{"+edge.v+" "+edge.w+"} is not an edge");
			  else {
				  // TODO: have to update this routine
				  RawManip.splitEdge_raw(packData.packDCEL,he);
				  packData.packDCEL.fixDCEL(packData);
				  count++;
			  }
   		  }
   		  return count;
	  }
	  
	  // ========= split_flower =============
	  if (cmd.startsWith("split_flo")) {
		  int v,w;
		  int u=0;
		  try {
			  // should have 2 or 3 integers
			  v=Integer.parseInt(items.get(0));
			  w=Integer.parseInt(items.get(1));
			  try {
				  u=Integer.parseInt(items.get(2));
				  if (u==w || u==v || w==v)
					  throw new ParserException();
			  } catch (Exception ex) {}; // u remains 0
		  } catch(Exception ex) {
			  throw new ParserException("usage: v w [u]");
		  } 
		  
		  HalfEdge newEdge;
		  HalfEdge wedge=packData.packDCEL.findHalfEdge(new EdgeSimple(v,w));
		  // bdry case
		  if (packData.isBdry(v)) {
			  if (wedge==null || packData.isBdry(w))
				  throw new ParserException("usage: v w when v bdry, w interior");
			  newEdge=RawManip.splitFlower_raw(packData.packDCEL, wedge,null);
		  }
		  // interior case
		  else {
			  if (u==0) {
				  throw new ParserException("usage: v w u when v is interior");
			  }
			  HalfEdge uedge=packData.packDCEL.findHalfEdge(new EdgeSimple(v,u));
			  if (wedge==null || uedge==null) 
				  throw new ParserException("usage: v w u; w & u must be nghbs of v");
			  newEdge=RawManip.splitFlower_raw(packData.packDCEL, wedge,uedge);
		  }
		  if (newEdge==null) 
			  return 0;
		  packData.packDCEL.fixDCEL(packData);
		  int newindx=newEdge.origin.vertIndx;
		  // a,b are ref vertices; for debug, center new vert between a,b.
		  int a=newEdge.twin.origin.vertIndx;
		  int b=newEdge.next.twin.origin.vertIndx;
		  Complex za=packData.getCenter(a);
		  Complex zb=packData.getCenter(b);
		  packData.setCenter(newindx,za.plus(zb).divide(2.0));
		  packData.setRadius(newindx,packData.getRadius(a));
		  return newindx;
	  }
   	  break;
  } // end of 's'
  case 'T': // "test" routines, meant to be temporary, developmental
  {
	  	  
	  // ======= T_islandSurround ====
	  if (cmd.startsWith("T_isl")) {
		  NodeLink beach=null;
		  try {
			  beach=new NodeLink(packData,flagSegs.get(0));
		  } catch(Exception ex) {
			  throw new ParserException("usage; T_islandSurround {v..}");
		  }
		  ArrayList<HalfLink> hllist=
				  RawManip.islandSurround(packData.packDCEL,beach);
		  if (hllist==null) {
			  CirclePack.cpb.errMsg("RawManip.islandSurround failed");
			  return 0;
		  }
		  Iterator<HalfLink> his=hllist.iterator();
		  while (his.hasNext()) {
			  packData.hlist=his.next();
			  CommandStrParser.jexecute(packData,"disp -ff hlist");
		  }
		  return 1;
	  }
	  
	  // Propogate across an edge based on intrinsic Schwarzian
	  else if (cmd.startsWith("T_s_prop")) {
		  items=flagSegs.get(0);
		  double s=0;
		  HalfEdge he=null;
		  try {
			  s=Double.parseDouble(items.remove(0));
			  he=HalfLink.grab_one_edge(packData,flagSegs);
		  } catch(Exception ex) {
			  CirclePack.cpb.errMsg("usage: T_s_prop <s> <halfedge>");
			  return 0;
		  }
		  if (he==null || he.face.faceIndx<0) {
			  CirclePack.cpb.errMsg("T_s_prop needs an interior edge");
			  return 0;
		  }
		  
		  // get the 'TriAspect
		  TriAspect ftri=new TriAspect(packData.packDCEL,he.face);
		  TriAspect gtri=new TriAspect(packData.packDCEL,he.twin.face);
		  int ans=0;
		  try {
			  ans=workshops.LayoutShop.schwPropogate(ftri,gtri,he,s,1);
		  } catch(Exception ex) {
			  CirclePack.cpb.errMsg("T_s_prop, sch propogate failed.");
			  return 0;
		  }
		  if (ans<0)
			  return 0;
		  
		  // store results
		  int oppV=he.twin.prev.origin.vertIndx;
		  int j=gtri.vertIndex(oppV);
		  Complex oppCenter=gtri.getCenter(j);
		  double oppRad=gtri.getRadius(j);
		  packData.packDCEL.setCent4Edge(he.twin.prev, oppCenter);
		  packData.packDCEL.setRad4Edge(he.twin.prev, oppRad);
		  return 1;
	  }
	  
	  // ========= layout =============
	  else if (cmd.startsWith("T_layout")) {
		  
	  }

	  //
	  else if (cmd.startsWith("T_bary")) {
		  int origNodeCount=packData.nodeCount;
		  ArrayList<combinatorics.komplex.DcelFace> farray=
				  new ArrayList<combinatorics.komplex.DcelFace>(); 
		  for (int f=1;f<=packData.packDCEL.intFaceCount;f++) 
			  farray.add(packData.packDCEL.faces[f]);
		  int ans=RawManip.addBaryCents_raw(packData.packDCEL,farray);
		  if (ans==0) {
			  CirclePack.cpb.myErrorMsg("Failed to add barycenters to faces");
			  return 0;
		  }  
		  packData.packDCEL.fixDCEL(packData);
		  for (int j=origNodeCount+1;j<=packData.nodeCount;j++)
			  packData.setAim(j,2.0*Math.PI);
		  return ans;
	  }
	  
	  break;
  } // end of 'T'
  case 't':
  {
	  
      // =========== torpack ===========
      if(cmd.startsWith("torpack")) {
    	  // 1 or 2 doubles as side-pairing factors
    	  double[] factors=new double[2];
    	  factors[0]=1.0;
    	  factors[1]=1.0;
		  ArrayList<Double> ftrs=new ArrayList<Double>();
    	  if (flagSegs!=null && flagSegs.size()>0) {
    		  items=(Vector<String>)flagSegs.get(0);
    		  try {
    			  while (items.size()>0) {
    				  ftrs.add(Double.parseDouble(items.remove(0)));
    			  }
    		  } catch(Exception ex) {
    			  throw new ParserException("Usage: torpack [A B]");
    		  }
    	  }
    	  int n=ftrs.size();
    	  if (n>=1)
    		  factors[0]=ftrs.get(0);
    	  if (n>=2) 
    		  factors[1]=ftrs.get(1);
    	  
    	  // now try the affine packing
    	  if (!ProjStruct.affineSet(packData,null,factors[0],factors[1]))
    		  throw new ParserException("torpack failed");
    	  
    	  EuclPacker e_packer=new EuclPacker(packData,-1);
    	  EuclPacker.affinePack(packData,-1);
			
    	  // store results as radii
    	  NodeLink vlist=new NodeLink();
    	  for (int i=0;i<e_packer.aimnum;i++) {
    		  vlist.add(e_packer.index[i]);
    	  }

    	  e_packer.reapResults();
    	  return packData.packDCEL.layoutPacking();
      }
	  
	  // ========= timer ==========
	  else if (cmd.startsWith("timer")) {
		  
		  // at most one flag: check for -s or -x
		  if (flagSegs!=null && flagSegs.size()!=0) {
			  items=flagSegs.get(0);
			  if (items.size()>0) {
				  String str=items.get(0);
				  if (str.contains("x")) {
					  PackControl.cpTimer.reset();
					  return 1;
				  }
				  else if (str.contains("s"))
					  PackControl.cpTimer.reset();
			  }
		  }
		  CirclePack.cpb.msg(PackControl.cpTimer.singleTime());
		  return 1;
	  }
	  
	  
	  // ========= torus_t ========
	  if (cmd.startsWith("torus_t")) {
		  TorusData torusData;
		  try {
			  torusData=new TorusData(packData);
		  } catch (Exception ex) {
			  throw new DataException("failed to get 'TorusData'");
		  }

		  // display both 'tau' and '1/tau'
		  CirclePack.cpb.msg("torus_tau: modulus = "+torusData.tau);
		  Complex taurecip=torusData.tau.reciprocal(); 
		  CirclePack.cpb.msg("   (and 1/modulus = "+taurecip+")");
		  return 1;
	  }
	  
	  // ========= triG ===========
	  // also see "create tri_gr" and "create j_ftn"
	  if (cmd.startsWith("triG")) {
		  StringBuilder strbld=new StringBuilder("create tri_gr ");
		  strbld.append(StringUtil.reconstitute(flagSegs));
		  count +=jexecute(packData,strbld.toString());
	  }
	  break;
  } // end of 't'
  case 'w': // fall through
  case 'W':
  {
	  // ========= write_custom ======

	  if (cmd.startsWith("write_cus") || cmd.startsWith("Write_cus")) {
		  // make sure a filename is specified in -f or -a format
		  try {
			  String filestr=flagSegs.lastElement().get(0);
			  if (!filestr.startsWith("-f") && !filestr.startsWith("-a")) 
				  throw new InOutException(
						  "usage: write_custom ... -[fa] {filename}");
			  StringBuilder strbuf=new StringBuilder("");
			  int code=CPFileManager.trailingFile(flagSegs, strbuf);
			  File file=new File(strbuf.toString());
			  String dir=file.getParent();
			  if (dir==null && cmd.charAt(0)=='W')
				  dir=CPFileManager.HomeDirectory.toString();
			  else if (dir==null)
				  dir=CPFileManager.CurrentDirectory.toString();
			  boolean script_flag=((code & 04)==04);
			  boolean append_flag=((code & 02)==02);
			  BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
				  append_flag,file.getName(),script_flag);
			  if (fp==null)
				  throw new InOutException("Failed to open '"+
						  file.toString()+"' for custom writing");
		  
			  // there must be a flag indicating type of data
			  items=flagSegs.get(0);
			  if (items==null || items.size()==0 || 
					  !StringUtil.isFlag(items.get(0)))
				  throw new InOutException("'write_custom' "+
						  "must have a flag for type of output");
			  char c=items.remove(0).charAt(1);
			  switch(c) {
			  case 'G': // grid (meaning dual graph): 
				  // for 3D printing grid output 11/2019
				  // TODO: only do euclidean case now
			  {
				  if (packData.hes!=0) 
					  throw new InOutException("'write_custom -G' "+
							  "currently for euclidean only");
				  // based on vertices (dual faces), defaults to all
				  // TODO: for now, only for interior dual faces.
				  // Create non-redundant list of edges from these vertices
				  NodeLink nlink=new NodeLink(packData,items);
				  int []vhits=new int[packData.nodeCount+1];
				  int []fhits=new int[packData.faceCount+1];
				  EdgeLink elink=new EdgeLink(packData);
				  Iterator<Integer> nlk=nlink.iterator();
				  while (nlk.hasNext()) {
					  int v=nlk.next();
					  if (!packData.isBdry(v)) {
						  int[] flower=packData.getFlower(v);
						  for (int j=0;j<packData.countFaces(v);j++) {
							  int w=flower[j];
							  if (vhits[w]==0) { // new edge?
								  elink.add(new EdgeSimple(v,w));
							  }
						  }
						  vhits[v]=1;
					  }
				  }
				  
				  // get list of dual edges and index faces encountered
				  GraphLink dualedges = packData.dualEdges(elink);
				  
				  // have to index the faces that occur for later
				  int tick=0;
				  FaceLink findx=new FaceLink(packData);
				  if (dualedges != null && dualedges.size() > 0) {
					  EdgeSimple edge = null;
					  Iterator<EdgeSimple> dedges = dualedges.iterator();
					  while (dedges.hasNext()) {
						  edge = (EdgeSimple) dedges.next();
						  if (fhits[edge.v]==0) {
							  fhits[edge.v]=tick++;
							  findx.add(edge.v);
						  }
						  if (fhits[edge.w]==0) {
							  fhits[edge.w]=tick++;
							  findx.add(edge.w);
						  }
					  }
				  }
						  
				  // now we can write the face centers in indexed order 	  
				  fp.append("Nodes: \n");
				  Iterator<Integer> fdex=findx.iterator();
				  while (fdex.hasNext()) {
					  Complex fcent=packData.getFaceCenter(fdex.next());
					  fp.append(String.format("%.6f",fcent.x)+
							  "  "+String.format("%.6f",fcent.y)+"\n");
				  }
				
				  // now write the order pairs of indices, using new values
				  fp.append("\nEdge pairs: \n");
				  Iterator<EdgeSimple> dedges = dualedges.iterator();
				  while (dedges.hasNext()) {
					  EdgeSimple edge = (EdgeSimple) dedges.next();
					  fp.append(fhits[edge.v]+" "+fhits[edge.w]+"\n");
				  }
				  
				  fp.flush();
				  fp.close();
				  count++;
			  }
			  } // end of switch

	    	  CirclePack.cpb.msg("Wrote custom -"+c+" to "+dir+
	    			  File.separator+file.getName());
	    	  
		  } catch (Exception ex) {
    		  throw new InOutException("write_custom failed: "+
    				  ex.getMessage());
		  }
	  }
	  
      // ========= write_path ========
	  else if (cmd.startsWith("write_path") || 
			  cmd.startsWith("Write_path")) {
    	  StringBuilder strbuf=new StringBuilder("");
    	  int code=CPFileManager.trailingFile(flagSegs, strbuf);
    	  File file=new File(strbuf.toString());
    	  String dir=file.getParent();
    	  if (dir==null && cmd.charAt(0)=='W')
    		  dir=CPFileManager.HomeDirectory.toString();
    	  else if (dir==null)
    		  dir=CPFileManager.CurrentDirectory.toString();
    	  boolean script_flag=((code & 04)==04);
    	  boolean append_flag=((code & 02)==02);
    	  BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
    			  append_flag,file.getName(),script_flag);
    	  if (fp==null)
    		  throw new InOutException("Failed to open '"+
    				  file.toString()+"' for writing");
    	  try {
    		  PathManager.writepath(fp,CPBase.ClosedPath);
    	  } catch (Exception ex) {
    		  throw new InOutException("write failed: "+ex.getMessage());
    	  }
    	  if (script_flag) {
    		  CPBase.scriptManager.includeNewFile(file.getName());
    		  CirclePack.cpb.msg("Wrote global path to "+
    				  file.getName()+" in the script");
    		  return 1;
    	  }
    	  CirclePack.cpb.msg("Wrote global path to "+
    			  CPFileManager.CurrentDirectory+
    			  File.separator+file.getName());
    	  return 1;
      }
      
      // ========= write_tiling ========
      else if (cmd.startsWith("write_til") || 
    		  cmd.startsWith("Write_til")) {
    	  if (packData.tileData==null || packData.tileData.tileCount<=0)
    		  throw new DataException("this packing "+
    				  "does not have tiling data");
    	  StringBuilder strbuf=new StringBuilder("");
    	  int code=CPFileManager.trailingFile(flagSegs, strbuf);
    	  File file=new File(strbuf.toString());
    	  String dir=file.getParent();
    	  if (dir==null && cmd.charAt(0)=='W')
    		  dir=CPFileManager.HomeDirectory.toString();
    	  else if (dir==null)
    		  dir=CPFileManager.CurrentDirectory.toString();
    	  boolean script_flag=((code & 04)==04);
    	  boolean append_flag=((code & 02)==02);
    	  BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
    			  append_flag,file.getName(),script_flag);
    	  if (fp==null)
    		  throw new InOutException("Failed to open '"+
    				  file.toString()+"' for writing");
    	  try {
    		  int act=020000;
    		  ReadWrite.writePack(fp,packData,act,script_flag);
    	  } catch (Exception ex) {
    		  throw new InOutException("write tiling failed: "+
    				  ex.getMessage());
    	  }
    	  if (script_flag) {
    		  CPBase.scriptManager.includeNewFile(file.getName());
    		  CirclePack.cpb.msg("Wrote tiling to "+
    				  file.getName()+" in the script");
    		  return 1;
    	  }
    	  CirclePack.cpb.msg("Wrote tiling to "+
    			  CPFileManager.CurrentDirectory+
    			  File.separator+file.getName());
    	  return 1;
      }
      
      break;
  } // end of 'w' 
  default: // no command found, merely flash message
  {
	  if (PackControl.consoleCmd!=null)
		  PackControl.consoleCmd.dispConsoleMsg(
				  "Command '"+cmd+"' not found");
	  count=0;
  }
  } // end of main switch for jexecute
  return count;
 } // end of jexecute
  
  
  /**
   * internally called to handle packings with 'status' 
   * true.
   * @param packData PackData
   * @param cmd String
   * @param flagSegs Vector<Vector<String>>
   * @return int
   * 
   */
  private static int packExecute(PackData packData,String cmd,
		  Vector<Vector<String>> flagSegs) {
	  int count=0;
	  Vector<String> items;
	  if (!packData.status) 
		  return 0;
	  
	  // ============ get_data/put_data ===========
      if (cmd.startsWith("get_data") || cmd.startsWith("put_data")) {
    	  // need to pick off the source/target packing -q{p} 
    	  //   and -t flag for 'translate', if it is there. 
    	  //   Rest is parsed in 'dataPutGet'
    	  try {
    		  items=(Vector<String>)flagSegs.remove(0);
    	  } catch(Exception ex){ 
    		  return 0;
    	  }
    	  String str=(String)items.get(0);
    	  int qnum=StringUtil.qFlagParse(str);
    	  PackData q=null;
    	  if (qnum<0 || (q=CPBase.cpDrawing[qnum].getPackData())==null || 
    			  !q.status) {
    		  throw new ParserException("usage: get_/put_data "+
    				  "must start with '-q{k}' indicating the "+
    				  "other packing");
    	  }
    	  if (flagSegs.size()==0) {
    		  throw new ParserException("usage: get_/put_data; "+
    				  "check formating of data to pass");
    	  }
    	  
    	  // next must be '-t' if translation is desired
    	  items=(Vector<String>)flagSegs.get(0);
    	  str=(String)items.get(0);
    	  boolean translate=false;
    	  if (str.startsWith("-t")) {
    		  translate=true;
    		  flagSegs.remove(0);
    	  }
    	  
    	  // no data flags?
    	  if (flagSegs.size()==0) {
    		  throw new ParserException("usage: get_/put_data; "+
    				  "check formating of data to pass");
    	  }
    	  
    	  // put or get?
    	  boolean putget=true;
    	  if (cmd.startsWith("get")) 
    		  putget=false;
    	  
    	  // parse the request
    	  return packData.dataPutGet(q,flagSegs,putget,translate);
      }
      
      // ============ Mobius ===============
      if (cmd.startsWith("Mobius") || cmd.startsWith("inv_Mobius") ||
    		  cmd.startsWith("mobiu") || cmd.startsWith("inv_mobiu")) {
    	  String str=null;
    	  if (flagSegs.size()==0 || 
    			  (items=(Vector<String>)flagSegs.remove(0))==null || 
    			  items.size()==0) { // default to "all"
    		  items=new Vector<String>(1);
    		  str=new String("a");
    		  items.add(str);
    	  }
    	  else {
    		  str=(String)items.get(0);
    	  }    	  	

    	  // orientation??
    	  boolean oriented=true;
    	  if (cmd.contains("inv")) oriented=false;
    	  	
    	  // look for sidepair flag
    	  boolean do_pairs=true;
    	  if (str.startsWith("-s")) { // suppress modifying side-pairs
    		  do_pairs=false;
    		  items.remove(0);
    	  }
    	  NodeLink vertlist=new NodeLink(packData,items);
    	  return packData.apply_Mobius(CPBase.Mob,
    			  vertlist,oriented,do_pairs);
      }
      
	  /* ================ main switch ============== */
	  switch (cmd.charAt(0)) {
	  case 'a':
	  {
	      // =========== alpha ============
	      if (cmd.startsWith("alpha")) {
	    	  int a=NodeLink.grab_one_vert(packData,flagSegs);
    		  return packData.packDCEL.setAlpha(a,null,true);
	      }
		  
		  // ============ appMob ==========
	      if (cmd.startsWith("appMob")) { // apply Mobius
    		  Mobius mob=new Mobius();
	    	  try {
	    		  items=(Vector<String>)flagSegs.get(0); // one string
	    		  Iterator<String> its=items.iterator();
	    		  mob.a.x=Double.parseDouble((String)its.next());
	    		  mob.a.y=Double.parseDouble((String)its.next());
	    		  mob.b.x=Double.parseDouble((String)its.next());
	    		  mob.b.y=Double.parseDouble((String)its.next());
	    		  mob.c.x=Double.parseDouble((String)its.next());
	    		  mob.c.y=Double.parseDouble((String)its.next());
	    		  mob.d.x=Double.parseDouble((String)its.next());
	    		  mob.d.y=Double.parseDouble((String)its.next());
	    		  if (its.hasNext()) {
	    			  double tmp=Double.parseDouble((String)its.next());
	    			  if (tmp<0) mob.oriented=false;
	    		  }
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: appMob .... ");
	    	  }
	    	  return packData.apply_Mobius(mob, new NodeLink(packData,"a"));
	      }
	      
	      // =========== adjust_rad =======
	      if (cmd.startsWith("adjust_rad")) {
	    	  double factor=1.0;
	    	  try {
	    		  // just one sequence: {x} {v..}
	    		  items=(Vector<String>)flagSegs.get(0); 
	    		  factor=Double.parseDouble((String)items.get(0));
	    		  items.remove(0);
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: {x} {v..}");
	    	  }
	    	  NodeLink vertlist=new NodeLink(packData,items);
	    	  if (factor<=0.0 || vertlist==null || vertlist.size()==0) 
	    		  return count;
	    	  Iterator<Integer> vlist=vertlist.iterator();

	    	  while (vlist.hasNext()) 
	    		  count +=packData.adjust_rad(
	    				  (Integer)vlist.next(), factor);
	    	  if (count<=0) {
	    		  CirclePack.cpb.myErrorMsg("adjust_radii: "+
	    				  "no radii were adjusted.");
	    		  return 0;
	    	  }
	    	  packData.fillcurves();
	    	  return count;
	      }

	      // =========== adjust_sch =======
	      if (cmd.startsWith("adjust_sch")) {
	    	  double factor=1.0;
	    	  try {
	    		  items=(Vector<String>)flagSegs.get(0); 
	    		  factor=Double.parseDouble((String)items.get(0));
	    		  items.remove(0);
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: {x} {v w ..}");
	    	  }
	    	  HalfLink edgelist=new HalfLink(packData,items);
	    	  if (factor<=0.0 || edgelist==null || edgelist.size()==0) 
	    		  return count;
	    	  Iterator<HalfEdge> elist=edgelist.iterator();

	    	  while (elist.hasNext()) 
	    		  count +=packData.adjust_uzian(
	    				  (HalfEdge)elist.next(), factor);
	    	  if (count<=0) {
	    		  CirclePack.cpb.myErrorMsg("adjust_sch: "+
	    				  "no schwarzians were adjusted.");
	    		  return 0;
	    	  }
	    	  packData.fillcurves();
	    	  return count;
	      }

	      // =========== add_ideal =========  
	      else if (cmd.startsWith("add_ideal")) {
	    	  
	    	  // no boundary? return 0
	    	  if (packData.getBdryCompCount()<=0) 
	    		  return 0;

	    	  boolean addVert=true; // default
	    	  NodeLink vertlist=null;
	    	  	
	    	  // default to add_ideal vertex to every bdry component
	    	  if (flagSegs==null || flagSegs.size()==0) {
	    		  // all boundary starts
	      		  vertlist=new NodeLink(packData,"B"); 
	      		  return packData.add_ideal(vertlist);
	    	  }
	  			
	    	  // first, look for '-f' flag
	    	  items=(Vector<String>)flagSegs.get(0); // one segment
	    	  String str=(String)items.get(0);
	    	  if (StringUtil.isFlag(str)) { 
	    		  char c=str.charAt(1);
  	  				switch(c) {
  	  				case 'f': // face
  	  				{
  	  					items.remove(0);
  	  					addVert=false;
  	  					break;
  	  				}
  	  				default:
  	  				{
  	  					addVert=true;
  	  				}
  	  				} // end of switch
	    	  }
	    	  
	    	  // in vertex mode?? (default, look for further flags)
	    	  if (addVert) {
	    		  try {
	    			  int v,w;
	    			  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
		      	  
	    			  // look for flags -b and/or -s first
	    			  while (nextFlag.hasNext() && 
	    					  (items=nextFlag.next()).size()>0) {
	    				  str=(String)items.get(0);
	    				  if (StringUtil.isFlag(str)) {
	    					  items.remove(0);
	    					  char c=str.charAt(1);
	    					  switch (c) {
	    					  case 'b': // selected full bdry components
	    					  {
	    						  vertlist=new NodeLink(packData,items);
	    						  count += packData.add_ideal(vertlist);
	    						  break;
	    					  }
	    					  case 's': // given pair v,w, on same bdry, 
	    						  // new vert attaches to vertices 
	    						  //   from v to w
	    					  {
	    						  vertlist=new NodeLink(packData,items);
	    						  Iterator<Integer> vlist=vertlist.iterator();
	    						  v=(Integer)vlist.next();
	    						  w=(Integer)vlist.next();
	    						  if (!packData.onSameBdryComp(v,w)) 
	    							  throw new CombException(v+" and "+
	    									  w+" aren't on the same "+
	    									  "bdry component");
    							  try {
    								  count=RawManip.addIdeal_raw(
    										  packData.packDCEL, v, w);
    								  if (count==0) 
    									  throw new CombException(
    											  "add failed");
    								  packData.packDCEL.fixDCEL(
    										  packData);
    							  } catch(Exception ex) {
    								  throw new DCELException(
    										  "addIdeal failed: "+
    												  ex.getMessage());
    							  }
    							  packData.xyzpoint=null;
    							  return count;
	    					  }
	    					  default: // not a legal flag
	    					  {
	    						  throw new ParserException();
	    					  }
		      			  } // end of switch
	    				  } 
	    				  else { // no flag, just use vertices
	    					  vertlist=new NodeLink(packData,items);
	    					  count += packData.add_ideal(vertlist); 
	    					  // DCELdebug.redConsistency(packData.packDCEL);
	    					  return count;
	    				  }
	    			  } // end of while
	    		  } catch(Exception ex){
	    			  throw new ParserException(
	    					  "add_ideal: error added vertices; "+
	    					  "perhaps an undefined flag");	    		  
	    		  }
	    		  return count;
	    	  } // done with ideal vertex case
	    	  

	    	  // now handle ideal faces
	    	  // '-f' flag is gone, check for list of vertices, 
	    	  //       else default to all bdry components
	    	  if (flagSegs.get(0).size()==0) {
	      		  vertlist=new NodeLink(packData,"B"); // all boundary starts
	    	  }
	    	  else
	    		  vertlist=new NodeLink(packData,flagSegs.get(0));
	    	  
	    	  Iterator<Integer> vlst=vertlist.iterator();
	    	  while (vlst.hasNext()) {
	    		  Vertex vert=packData.packDCEL.vertices[vlst.next()];
	    		  if (vert.bdryFlag!=0) {
	    			  combinatorics.komplex.DcelFace newface=
	    					  new combinatorics.komplex.DcelFace(
	    							  packData.packDCEL.faceCount+1);
	    			  HalfEdge he=vert.halfedge.twin;
	    			  newface.edge=he;
	    			  if (he.next.next.next==he) { // 3-sided?
	    				  do {
	    					  he.face=newface;
	    					  he.origin.bdryFlag=0;
	    					  he.origin.redFlag=false;
	    					  he=he.next;
	    				  } while (he!=vert.halfedge.twin);
	    				  count++;
	    			  }
	    		  }
	    	  }
	    	  if (count>0)
	    		  packData.packDCEL.fixDCEL(packData);
	    	  return count;
	      }
	      
	      // =========== add_cir =========
	      else if (cmd.startsWith("add_cir")) {
	    	  try {
	    		  items=flagSegs.elementAt(0); // should be only one segment
	    	  } catch(Exception ex) {
	    		  return 0;
	    	  }
	    	  HalfLink addedEdges=new HalfLink(); // for setting cent/rad
	   		  NodeLink nodeLink=new NodeLink(packData,items);
	   		  Iterator<Integer> vlist=nodeLink.iterator();
   	   		  while (vlist.hasNext()) {
   	   			  Vertex V=packData.packDCEL.vertices[vlist.next()];
   	   			  if (V.bdryFlag==0) 
   	   				  continue;
   	   			  HalfEdge he=V.halfedge.twin.next;
   	   			  Vertex vert=RawManip.addVert_raw(packData.packDCEL,he);
   	   			  if (vert!=null) {
   	   				  count++;
   	   				  addedEdges.add(he);
   	   			  }
  	   		  }
	 		  // process new edges to set cent/rad
   	   		  if (count>0) {
   	   			  packData.packDCEL.addedVertData(addedEdges);
   	   			  packData.packDCEL.fixDCEL(packData);
	   		  }
	   		  return count;
	      }
		  
	      // =========== add_edge =========
	      else if (cmd.startsWith("add_e")) {
	    	  items=flagSegs.elementAt(0); // should be only one segment
	    	  NodeLink nodeLink=new NodeLink(packData,items);
	   		  
	   		  if (nodeLink==null || nodeLink.size()<2) 
	   			  return 0;

	   		  // proceed while we get successive pairs that work
	   		  Iterator<Integer> vlist=nodeLink.iterator();
	   		  boolean ehit=true;

	   		  while (vlist.hasNext() && ehit) {
	   			  ehit=false;
	   			  int v=vlist.next();
	   			  if (!vlist.hasNext())
	   				  return count;
	   			  int w=vlist.next();
	   			  
	   			  if (!packData.isBdry(v) || !packData.isBdry(w)) {
	   				  CirclePack.cpb.errMsg("usage: "+
	   						  "add_edge v w, vertices must "+
	   						  "be on boundary");
	   				  break;
	   			  }
	   			  
	   			  // is {v w} already an edge?
	   			  if (packData.areNghbs(v,w)) {
   					  CirclePack.cpb.errMsg("<"+v+" "+w+"> "+
   							  "is already an edge");
   					  break;
	   			  }
	   			  
	   			  int u=-1;
	   			  HalfEdge he=packData.packDCEL.vertices[v].halfedge;
	   			  if (he.twin.prev.origin.vertIndx==w) 
	   				  u=he.twin.origin.vertIndx; // v,u,w is cclw
	   			  else if (he.twin.next.next.twin.origin.vertIndx==w)
	   				  u=he.twin.next.next.origin.vertIndx; // w,u,v is cclw
	   			  else {
   					  CirclePack.cpb.errMsg(
   							  "v = "+v+" and w = "+w+" don't have common nghb");
   					  break;
	   			  }

	   			  RawManip.enfold_raw(packData.packDCEL,u);
	   			  packData.setAim(u,2*Math.PI);
	   			  count++;
	   			  ehit=true;
	   		  } // end of while
	   		  
	   		  if (count>0) {
	   			  packData.xyzpoint=null;
   				  packData.packDCEL.fixDCEL(packData);
	   			  packData.fillcurves();
	   		  }
	   		  return count;
	      }
	      
	      // ========= add_barycenter ========
		  // ========= add_face_triple =======
	      else if (cmd.startsWith("add_b") || 
	    		  cmd.startsWith("add_face_t")) {
	    	  boolean baryOpt=true;  // add_barycenter
	    	  // add face triple
	    	  if (cmd.charAt(4)=='f') 
	    		  baryOpt=false; 
	    	  int f;
	    	  
	    	  // should be only one segment
	    	  items=flagSegs.elementAt(0); 
	   		  FaceLink faceLink=new FaceLink(packData,items);
	   		  if (faceLink==null || faceLink.size()<1) 
	   			  return 0;
	      		  
	   		  Iterator<Integer> flist=faceLink.iterator();
	   		  // avoid duplication
	   		  int []xdup=new int[packData.faceCount+1];
	   		  
   			  if (!baryOpt)
   				  // TODO: add this option
				  throw new ParserException("'face_triple' "+
						  "call is not yet available for dcel case.");

	   		  while (flist.hasNext()) {
	   			  f=(Integer)flist.next();
	   			  combinatorics.komplex.DcelFace 
	   			  	face=packData.packDCEL.faces[f];
	   			  
	   			  boolean debug=false;
	   			  if (debug) { // debug=true;
	   				  HalfEdge hef=face.edge;
	   				  if (hef.origin.vertIndx==11 || 
	   					  hef.next.origin.vertIndx==11 ||
	   					  hef.next.next.origin.vertIndx==11)
	   				  System.out.println("face "+f+", "+face);
	   			  }
	   			  
	   			  if (xdup[f]==0) {
	   				  int ans;
   					  ans=RawManip.addBary_raw(
   							  packData.packDCEL,face.edge,false);
   					  xdup[f]=1;
	   				  if (ans!=0 && face.faceIndx<0)
	   					  packData.packDCEL.redChain=null; // redo red
	   				  count += ans;
	   			  }
	   		  }
		   		  
	   		  if (count==0)
	   			  return 0;

	   		  packData.packDCEL.fixDCEL(packData);
	   		  return count;
	      }

	      // ========= aspect ====== (formerly 'rect_ratio')
	      else if (cmd.startsWith("aspect")) {
	    	  CallPacket cP=CommandStrParser.valueExecute(
	    			  packData,cmd,flagSegs);
	    	  if (cP.error) {
	    		  CirclePack.cpb.errMsg("aspect call failed");
	    		  return 0;
	    	  }
	    	  StringBuilder strb=new StringBuilder(
	    			  "Log(Aspect) (log(width/height)) of p"+
	    					  packData.packNum+", corners [");
	    	  for (int j=0;j<4;j++)
	    		  strb.append(cP.int_vec.get(j)+" ");
	  		  strb.append("] is "+cP.double_vec.get(0));
	  		  CirclePack.cpb.msg(strb.toString());
	  		  return 1;
	      }
	            
	      // =============== add_layer
	      else if (cmd.startsWith("add_lay")) {
	    	  int mode=CPBase.TENT; // default
	    	  int degree=3; // default
	    	  int v1,v2;
	    	  
	    	  if (packData.getBdryCompCount()==0 || flagSegs.size()==0) {
	    		  throw new ParserException("perhaps packing "+
	    				  "has no boundary?");
	    	  }
	    	  
	    	  // should have only one segment
	      	  items=flagSegs.elementAt(0);
	      	  if (StringUtil.isFlag(items.elementAt(0))) {
	      		  char c=items.elementAt(0).charAt(1);
	      		  items.remove(0);
	      		  
	   			  // Two flags; process rest in following
	      		  switch(c) {
	   			  case 't': // 
	   			  {
	   				  mode=CPBase.TENT;
	   				  break;
	   			  }
	   			  case 'd': // 
	   			  {
	   				  mode=CPBase.DUPLICATE;
	   				  break;
	   			  }
	   			  default:
	   			  {
	   				  throw new ParserException("undefined flag");
	   			  }
	   			  } // end of flag switch
	   		  } // done handling the flag
	      	  
	      	  // no flag and three numbers, should be 'N v w' form
	      	  else if (items.size()==3)
	      		  mode=CPBase.DEGREE;
	      	  
	   		  // TENT/DUPLICATE modes take 2 arguments, v1 v2; 
	      	  //    DEGREE has <d> also (at end)
	   		  if ((mode==CPBase.DEGREE && items.size()!=3) || 
	   				  (mode!=CPBase.DEGREE && items.size()!=2)) {
	   			  throw new DataException("usage: -[dt] {d} v1 v2.");
	   		  }
	   		  if (mode==CPBase.DEGREE) {
	   			  try {
	   				 degree=Integer.parseInt(items.elementAt(0));
	   				 if (degree<4 || degree>PackData.MAX_PETALS) {
	     				  throw new ParserException("improper degree "+
	     						  degree);
	   				 }
	   				 items.remove(0);
	   			 } catch(NumberFormatException nfe) {
	 				  throw new ParserException(nfe.getMessage());
	   			 }
	   		  }
	   		  
	   		  // Should get two vertices, v1, v2 
	   		  //     (let add_layer check validity)
	   		  try {
	   			  NodeLink vertlist=new NodeLink(packData,items);
	   			  v1=(Integer)vertlist.get(0);
	   			  v2=(Integer)vertlist.get(1);
	   		  } catch (NumberFormatException nfe) {
				  throw new DataException("bad data.");
	   		  } catch (CombException cex) {
	   			  throw new CombException(cex.getMessage());
	   		  }

   			  PackDCEL pdcel=packData.packDCEL;
  			  int ans= CombDCEL.addlayer(pdcel,mode,degree,v1,v2);
   			  if (ans<=0)
   				  return 0;
   			  pdcel.fixDCEL(packData);
   			  return ans;
	      }
	      
	      // =============== add_gen
	      else if (cmd.startsWith("add_gen")) {
	    	  int mode=CPBase.TENT; // default
	    	  int degree=6;
	    	  int numGens=1; // default
	    	  NodeLink bdrylist=null;
	    	  boolean b_flag=false;
	    	  
	    	  if (packData.getBdryCompCount()==0 || flagSegs.size()==0) {
	    		  throw new CombException("packing has no boundary");
	    	  }
	    	  
	    	  try {
	    		  items=flagSegs.remove(0);
	    		  if (StringUtil.isFlag(items.get(0)))
	    			  throw new ParserException("usage: add_gen {n} ...");
	    		  numGens=Integer.parseInt(items.remove(0));
	    		  if (items.size()>0) {
	    			  degree=Integer.parseInt(items.get(0));
	    			  mode=CPBase.DEGREE;
	    		  }
	    		  else
	    			  mode=CPBase.TENT;
	    	  
	    		  // checked for/handled -b flag (must be last flag)
	    		  int lastf=flagSegs.size()-1;
	    		  if (lastf>=0) {
	    			  items=flagSegs.get(lastf);
	    			  if (!b_flag && items.get(0).startsWith("-b")) {
	    				  items.remove(0);
	    				  // for now, do all
	    				  bdrylist=new NodeLink(packData,items);
	    				  if (bdrylist.size()>0) b_flag=true;
	    				  flagSegs.remove(lastf);
	    			  }
	    		  }

	    		  // now for other flags
	    		  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    		  while (nextFlag.hasNext() && 
	    				  (items=nextFlag.next()).size()>0) {
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  char c=items.get(0).charAt(1);
	    			  items.remove(0);

	    			  // Two flags; process rest in following
	    			  switch(c) {
	    			  case 'd':  
	    			  {
	    				  mode=CPBase.DUPLICATE;
	    				  break;
	    			  }
	    			  case 'b': // already handled
	    			  {
	    				  break;
	    			  }
	    			  default:
	    			  {
	    				  mode=CPBase.TENT;
	    			  }
	    			  } // end of flag switch
	    		  }
	    		  }
	    	  } catch  (Exception ex) {
	    		  throw new ParserException("usage: "+
	    				  "add_gen {n} [{d}] [-dt] [-b {v..}]");
	    	  }
	    	  
	    	  // Finally, calls to addLayer for each boundary component
			  int v1,v2;
			  PackDCEL pdcel=packData.packDCEL;
			  if (!b_flag) { // just one boundary component
				  for (int n=1;n<=numGens;n++) {
					  v1=v2=pdcel.idealFaces[1].edge.origin.vertIndx;
					  count += CombDCEL.addlayer(pdcel,
							  mode, degree, v1, v2);
					  pdcel.fixDCEL(packData);
				  }
			  }
			  // Note: have to adjust v1, v2 each time 
			  //    because there'a a new start
			  else if (bdrylist.size()>0) {
				  Iterator<Integer> Bverts=bdrylist.iterator();
				  while (Bverts.hasNext()) {
					  int b=(Integer)Bverts.next();
					  for (int n=1;n<=numGens;n++) {
						  v1=v2=pdcel.idealFaces[b].
								  edge.origin.vertIndx;
						  count += CombDCEL.addlayer(pdcel,
								  mode, degree, v1, v2);
						  pdcel.fixDCEL(packData);
					  }
				  }
			  }
			  return count;
	      }
		  break;
	  } // end of 'a'
	  case 'b': // fall through
	  case 'B':  
	  {
	      // ============= blend ===========
	      if(cmd.startsWith("blend")) {
	    	  items=(Vector<String>)flagSegs.remove(0);  // -q{p} v n
	    	  String str=(String)items.get(0);
	    	  int qnum=StringUtil.qFlagParse(str);
	    	  PackData qackData=null;
	    	  int v,n;
	    	  try {
	    		  qackData=CPBase.cpDrawing[qnum].getPackData();
	    		  if (qackData==null) throw new ParserException();
	    		  v=NodeLink.grab_one_vert(packData,(String)items.get(1));
	    		  n=Integer.parseInt((String)items.get(2));
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: blend -q{p} v n");
	    	  }
	    	  return packData.blend(qackData,v,n);
	      }
	      
	      // =========== bary_refine =======
	      else if (cmd.startsWith("bary_refine")) {
    		  ArrayList<Integer> a=RawManip.hexBaryRefine_raw(
    				  packData.packDCEL,true);
    		  if (a==null)
    			  return 0;
    		  packData.packDCEL.fixDCEL(packData);
    		  // DCELdebug.printRedChain(packData.packDCEL.redChain);
	    	  CirclePack.cpb.msg("Packing p"+packData.packNum+" has "+
	    			  "been barycentrically refined");
    		  return 1;
	      }
	  } // end of 'b' 
	  case 'c':
	  {
		  
	      // =========== cookie ===========
	      if (cmd.startsWith("cookie")) {
	    	  
    		  // identify forbidden edges (and possibly new 'alpha')
    		  HalfLink hlink=CombDCEL.cookieData(packData,flagSegs);
	    		  
    		  // cookie out by forming new red chain
    		  CombDCEL.redchain_by_edge(
    				  packData.packDCEL,hlink,
    				  packData.packDCEL.alpha,true);
	    	  packData.packDCEL.fixDCEL(packData);
    		  packData.set_aim_default();
   			  return packData.packDCEL.vertCount;
	      }
		  
	      //  =========== cir_invert ===============
	      else if (cmd.startsWith("cir_invert")) {
	        double CPrad2=1.0;
	        Complex ctr2=null;
	        boolean u_flag=false;

	        items=(Vector<String>)flagSegs.get(0);
	        String str=(String)items.get(0);
	        if (StringUtil.isFlag(str)) {
	            if (!str.startsWith("-u")) 
	            	throw new ParserException(
	            			"usage: only flag is -u");
	            ctr2=new Complex(0.0);
	            CPrad2=1.0;
	            u_flag=true;
	            items.remove(0);
	        }
	        NodeLink vertlist=new NodeLink(packData,items);
	        int v1=(Integer)vertlist.get(0);
	        if (packData.hes<0) 
	        	packData.geom_to_e(); 
	        Complex ctr1=packData.getCenter(v1);
	        double CPrad1=packData.getRadius(v1);
	        if (packData.hes>0) {
	      	CircleSimple sc=SphericalMath.s_to_e_data(ctr1,CPrad1);
	      	ctr1=new Complex(sc.center);
	      	CPrad1=sc.rad;
	      	if (sc.flag==-1) {
	      		CPrad1 *=-1.0;
	      	}
	        }
	        if (!u_flag) { // use v2
	            int v2=(Integer)vertlist.get(1); // not -u, so get w
	            ctr2=packData.getCenter(v2);
	            CPrad2=packData.getRadius(v2);
	            if (packData.hes>0) {
	            	CircleSimple sc=SphericalMath.s_to_e_data(
	            			ctr2,CPrad2);
	            	ctr2=new Complex(sc.center);
	            	CPrad2=sc.rad;
	    	      	if (sc.flag==-1) {
	    	      		CPrad2 *=-1.0; // disc is outside of circle
	    	      	}
	            }
	        }
	        Mobius mob=Mobius.cir_invert(ctr1,CPrad1,ctr2,CPrad2);
	        packData.apply_Mobius(mob,new NodeLink(packData,"a"));
	        return 1;
	      }
	      
	      // ============== count ==========
	      else if (cmd.startsWith("count")) {
	    	  CallPacket cP=CommandStrParser.valueExecute(
	    			  packData,cmd,flagSegs);
	    	  if (cP==null || cP.int_vec==null || cP.int_vec.size()==0)
	    		  return 0; // failed
	    	  return (int)cP.int_vec.get(0);
	      }
	      
	      // ========= copy ==============
	      else if (cmd.startsWith("copy")) {
	    	  // should be just the pack number
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  try {
	    		  int qnum=Integer.parseInt((String)items.get(0));
	    		  if (qnum==packData.packNum) 
	    			  return 1; // same packing
	    		  PackData tmpPD=packData.copyPackTo();
	    		  CirclePack.cpb.swapPackData(tmpPD,qnum,false);
	    		  return 1;
	    	  } catch (Exception ex) {
	    		  throw new ParserException("copy failed: "+ex.getMessage());
	    	  }
	      }
	      
	      // ========= color ============
		  else if (cmd.startsWith("color")) {
			  items=(Vector<String>)flagSegs.elementAt(0);
			  String str=(String)items.elementAt(0);
			  // circles, faces, edges, tiles?
			  if (str.startsWith("-c") || str.startsWith("-v")) {
				  items.remove(0);
				  if (items.size()==0)
					  flagSegs.remove(0);
				  packData.color_circles(flagSegs);
				  return 1;
			  }
			  if(str.startsWith("-f")) {
				  items.remove(0);
				  if (items.size()==0)
					  flagSegs.remove(0);
				  packData.color_faces(flagSegs);
				  return 1;
			  }
			  if(str.startsWith("-e")) {
				  items.remove(0);
				  if (items.size()==0)
					  flagSegs.remove(0);
				  packData.color_edges(flagSegs);
				  return 1;
			  }
			  if (str.startsWith("-T") || str.startsWith("-D") || 
					  str.startsWith("-Q") ) {
				  char c=items.remove(0).charAt(1);
				  int tmode=1; // default to -T, 'tile'
				  if (c=='D')
					  tmode=2;
				  else if (c=='Q')
					  tmode=3;

				  if (items.size()==0)
					  flagSegs.remove(0);
				  packData.color_tiles(tmode,flagSegs);
				  return 1;
			  }
			  return 0;
		  }
	      
	      // ============== cir_aspect =========
		  if (cmd.startsWith("cir_aspect")) {
			  if (packData.hes!=0) {
				  throw new ParserException("only for euclidean packings.");
			  }
			  items=(Vector<String>)flagSegs.get(0); // one segment
			  NodeLink vertlist=new NodeLink(packData,items);
			  Iterator<Integer> vlist=vertlist.iterator();
			  int tick=0;
			  int v;
			  String str=new String("Circle aspects:");
			  while (tick<5 && vlist.hasNext()) {
				  v=(Integer)vlist.next();
				  double cabs=packData.getCenter(v).abs();
				  if (packData.getRadius(v) >= cabs) {
					  str=str.concat(" v "+v+": encloses origin\n");
				  }
				  else str=str.concat(" v "+v+" "+
						  (double)(packData.getRadius(v)/cabs)+"\n");
				  tick++;
			  }
			  return tick;
		  }
	      
	      // ========= center, center_vert/point =======
	      else if (cmd.startsWith("center")) {
	    	  // options: center_vert v, center_point x y, 
	    	  //    center -v {v}, center -z {x y}
	    	  Complex z=null;
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  String str=(String)items.get(0);
	    	  int v=packData.activeNode;
	    	  if (cmd.startsWith("center_vert")) {
	    		  v=NodeLink.grab_one_vert(packData,str);
	    		  z=packData.getCenter(v);
	    	  }
	    	  else if (cmd.startsWith("center_point")) {
	    		  z=new Complex(Double.parseDouble(items.get(0)),
	    				  Double.parseDouble(items.get(1)));
	    	  }
	    	  else {
	    		  if (str.startsWith("-v")) {
	    			  str=(String)items.get(1);
	        		  v=NodeLink.grab_one_vert(packData,str);
	        		  z=packData.getCenter(v);
	    		  }
	    		  else if (str.startsWith("-z")) {
	        		  z=new Complex(Double.parseDouble(items.get(1)),
	        				  Double.parseDouble(items.get(2)));
	    		  }
	    	  }
	    	  
	    	  // should have 'z' now
	    	  return packData.center_point(z);
	      }
	      break;      
  	  } // end of 'c'   
	  case 'D': // fall through
	  case 'd':
	  {

			// =============== dual_layout (replaced 'sch_layout')
			if (cmd.startsWith("dual_lay")) {
				
				// always convert to spherical; if you
				// don't want this, make call via "layout -s".
				jexecute(packData,"geom_to_s");
				HalfEdge firsthe=packData.packDCEL.layoutOrder.get(0);
				double base_rad=.25;
				
				// first face is an equilateral triangle
				packData.packDCEL.setRad4Edge(firsthe,base_rad);
				packData.packDCEL.setRad4Edge(firsthe.next,base_rad);
				packData.packDCEL.setRad4Edge(firsthe.next.next,base_rad);
				
				// use 'layoutOrder' and use single edge, 
				//   not average across all available edges.
				int ans=packData.packDCEL.layoutPacking(
					packData.packDCEL.layoutOrder,true,true);
				return ans;
			}
			
/* TODO: OBE have to redo this without a dual graph of the old style.
 * 		  
			// =============== dual_layout (replaced 'sch_layout')
			if (cmd.startsWith("dual_lay")) {
				if (packData.hes < 0) {
					CirclePack.cpb.errMsg("usage: dual_layout "+
							"is not used for hyperbolic packings");
					return 0;
				}
				// look for list of face pairs; default to a spanning tree
				boolean debug = false;
				HalfLink hlink=null;
				boolean first=false; // first face first 
				String cflags = null; // flags for drawing circles
				String fflags = null; // flags for drawing faces
				if (flagSegs != null && flagSegs.size() > 0 && 
						flagSegs.get(0).size() > 0) {
					Iterator<Vector<String>> its = flagSegs.iterator();
					items = null;
					// may have flags to see results -c{disp_ops}
					//    and/or -f{disp_ops}
					while (its.hasNext()) {
						items = its.next();
						if (StringUtil.isFlag(items.get(0))) {
							String str = items.remove(0);
							char c = str.charAt(1);
							switch (c) {
							case 'v':
							case 'c': // draw as laid out
							{
								cflags = new String(str);
								break;
							}
							case 'f': // report and (if draw==true) 
								// draw face labels
							{
								fflags = new String(str);
								break;
							}
							} // end of switch
						}
					}

					// 'items' should be HalfEdge list,
					if (items != null && items.size() > 0)
						hlink=new HalfLink(packData,items);
					else { // use layoutOrder and layout first face
						hlink = packData.packDCEL.layoutOrder;
						first=true;
					}
				} else { // no flags or list?
					hlink = packData.packDCEL.layoutOrder;
					first=true;
				}

				// TODO: how to signify this using hlink???
				
				// Do we need to place the first face? Only if
				// we start with a "root".
				
				HalfEdge edge = hlink.remove(0);

				// yes, place base equilateral, zero out 'curv's
				if (edge==packData.packDCEL.alpha) {
					
					// zero out the curvatures
					for (int v=1;v<=packData.nodeCount;v++)
						packData.setCurv(v,0.0);
					
					// note that we reset rad/center of 'baseface'
					int[] verts=packData.faces[baseface].vert;
					Complex[] Z=new Complex[3];
					Z[0]=new Complex(1.0,-CPBase.sqrt3);
					Z[1]=new Complex(1.0,CPBase.sqrt3);
					Z[2]=new Complex(-2.0);
					if (packData.hes>0) {
						for (int j=0;j<3;j++) { 
							CircleSimple cS=SphericalMath.
									e_to_s_data(Z[j],CPBase.sqrt3);
							packData.setCenter(
									verts[j],new Complex(cS.center));
							packData.setRadius(
									verts[j],cS.rad);
							packData.setCurv(verts[j],
									packData.getCurv(verts[j])+Math.PI);
						}
					}
					else {
						for (int j=0;j<3;j++) {
							packData.setCenter(verts[j],Z[j]);
							packData.setRadius(verts[j],CPBase.sqrt3);
							packData.setRadius(verts[j],
									packData.getRadius(verts[j])+
									Math.PI/3.0);
						}
					}

					// TODO: layout problems can occur if not 
					//   in sph geometry, but should figure 
					//   out check and just warn user. 
//					if (packData.hes <= 0) {
//						packData.geom_to_s();
//						packData.fillcurves();
//						packData.setGeometry(packData.hes);
//						if (cpS != null)
//							cpS.setPackName();
//						jexecute(packData, "disp -w");
//					}

					if (cflags != null) {
						StringBuilder strbld = new StringBuilder(
								"disp " + cflags);
						// show all three circles
						for (int j = 0; j < 3; j++) { 
							strbld.append(" " + 
									packData.faces[baseface].vert[j]);
						}
						jexecute(packData, strbld.toString());
					}
					if (fflags != null) {
						StringBuilder strbld = new StringBuilder(
								"disp " + fflags + " " + baseface);
						jexecute(packData, strbld.toString());
					}
					count++;
				}

				// TODO: layout problems can occur if we don't 
				//    convert to sph geometry, but instead of 
				//    automatically converting, I should check 
				//    to outcome for problems and just send a 
				//    message to the user.
//				if (packData.hes <= 0) {
//					packData.geom_to_s();
//					packData.fillcurves();
//					packData.setGeometry(packData.hes);
//					if (cpS != null)
//						cpS.setPackName();
//					jexecute(packData, "disp -w");
//				}

				// now progress through edges <f,g> of 'graph'
				Iterator<EdgeSimple> gst = graph.iterator();
				while (gst.hasNext()) {
					EdgeSimple dedge = gst.next();
					int f = dedge.v;
					int g = dedge.w;
					if (f == 0) // root should have been handled
						break;
					int j = packData.face_nghb(g, f);

					// to get s, need edge <v,w>, f on its left
					int[] verts=new int[3];
					verts[0] = packData.faces[f].vert[j];
					verts[1] = packData.faces[f].vert[(j + 1) % 3];
					double s=packData.getSchwarzian(
							new EdgeSimple(verts[0],verts[1]));

					int m = packData.face_nghb(f, g);
					int target = packData.faces[g].vert[(m + 2) % 3];

					// assume circles of f are in place, need 
					//   only compute the third circle of g, 
					//   'target' (across the shared edge).
					try {
						// compute map from base equilateral
						Mobius bm_f = D_Schwarzian.faceBaseMob(packData, f);

						// compute the target circle
						CircleSimple sC = D_Schwarzian.getThirdCircle(
								s, j, bm_f, packData.hes);

						// debug info
						if (debug) {// debug=true;
							deBugging.DebugHelp.mob4matlab("bm_f", bm_f);

							// display computed tangency points, 
							//    print cents/rads
							if (packData.hes > 0) {
								Complex[] tp = new Complex[3];
								int[] vert = packData.faces[f].vert;
								System.out.println("circles <" + 
										vert[0] + " " +	vert[1] + 
										" " + vert[2]+
										"> :\nSchwarian is s =" + s);
								for (int jj = 0; jj < 3; jj++) {
									Complex zjj = packData.getCenter(vert[jj]);
									double rjj = packData.getRadius(vert[jj]);
									System.out.println("C(" + jj + ",:) = [" 
											+ zjj.x + " " + zjj.y + " " + 
											rjj + "]");
									tp[jj] = SphericalMath.sph_tangency(zjj,
											packData.getCenter(vert[(jj + 1) % 3]),
											rjj, packData.getRadius(vert[(jj + 1) % 3]));
									String str = new String("disp -dpfc5 " + 
											vert[jj] + " " + vert[(jj + 1) % 3]);
									// draw the tangency point
									CommandStrParser.jexecute(packData, str);
								}
							}

							// the outcome circle
							System.out.println("new circles: cent = "+
									sC.center + "; r = " + sC.rad);
							debug = false;
						}

						packData.setRadius(target,sC.rad);
						packData.setCenter(target,sC.center);
						
						// compute and store angles
						verts[2]=target;
						double[] radii=new double[3];
						radii[0]=packData.getRadius(verts[0]);
						radii[1]=packData.getRadius(verts[1]);
						radii[2]=sC.rad;

						for (int q=0;q<3;q++) {
							double ang=CommonMath.get_face_angle(
									radii[q],radii[(q+1)%3],
									radii[(q+2)%3],packData.hes);
							packData.setCurv(verts[q],
									packData.getCurv(verts[q])+ang);
						}
						
					} catch (Exception ex) {
						CirclePack.cpb.errMsg("problem applying "+
								"some schwarzian\n");
					}
					if (cflags != null) {
						StringBuilder strbld = new StringBuilder(
								"disp " + cflags + " " + target);
						jexecute(packData, strbld.toString());
					}
					if (fflags != null) {
						StringBuilder strbld = new StringBuilder(
								"disp " + fflags + " " + g);
						jexecute(packData, strbld.toString());
					}

					count++;
				}
				return count;
			}
*/

	      // ========= doyle_point ========
	      if (cmd.startsWith("doyle_point")) {
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  int f=FaceLink.grab_one_face(packData,(String)items.get(0));
	    	  double r0,r1,r2;
	    	  Complex z0,z1,z2;

	    	  if (packData.hes!=0 || f<1 || f>packData.faceCount) 
	    		  throw new ParserException("must specify face f of eucl packing");

	    	  int[] verts=packData.packDCEL.faces[f].getVerts();
	    	  r0=packData.getRadius(verts[0]);
	    	  r1=packData.getRadius(verts[1]);
	    	  r2=packData.getRadius(verts[2]);
	    	  z0=packData.getCenter(verts[0]);
	    	  z1=packData.getCenter(verts[1]);
	    	  z2=packData.getCenter(verts[2]);
	    	  double []ans=new double[4];
	    	  if (Exponential.doyle_point(packData,r0,r1,r2,z0,z1,z2,ans)==0) 
	    		  throw new ParserException("failed");
	    	  CirclePack.cpb.msg("doyle_point for face "+f+", pt ="+
	      					 ans[2]+" "+ans[3]+"i, parameters a="+ans[0]+
	      					 " and b="+ans[1]);
	    	  return 1;
	      }
	          
	      // ========= doyle_annulus =========
	      else if (cmd.startsWith("doyle_ann")) {
	    	  items=(Vector<String>)flagSegs.get(0); // form: p q n
	    	  int p,q,n;
	    	  try {
	    		  p=Integer.parseInt((String)items.get(0));
	    		  q=Integer.parseInt((String)items.get(1));
	    		  n=Integer.parseInt((String)items.get(2));
	    	  } catch (Exception ex) {
	    		  throw new DataException("usage: p q n: "+ex.getMessage());
	    	  }
	    	  if (p>q) { // make sure q>=p
	    		  int h=q;
	    		  q=p;
	    		  p=h;
	    	  }
	    	  // check parameters
	    	  
	    	  if ((p==0 && q<3) || (p+q)<3 || n<1) {
	    		    throw new ParserException("usage: Check legal values (p,q)");
	    	  }
	    	  throw new ParserException("'doyle_annulus' processing "+
	    			  "appears to be unfinished.");
	      }

	      // ============== double =============
	      else if (cmd.startsWith("double")) {
	    	  
	    	  boolean segment=false;
	    	  NodeLink vertlist=null;
	    	  
	    	  // tmp: to find symmetric alpha in the doubled result
    		  int sym_alp_tmp=packData.getAlpha()+packData.nodeCount; 
    		  int alp_sym=0;
    		  
	    	  // first, get data: either list of verts, one for each
	    	  //   bdry comp (default to all), or one bdry component
	    	  //   segment of form "b(v,w)".
	    	  if (flagSegs==null || flagSegs.size()==0) { // no flag? do all
	    		  vertlist=new NodeLink(packData,"B");
	    	  }
	    	  else {
	    		  items=(Vector<String>)flagSegs.get(0);
	    		  String str=(String)items.get(0);
	    		  if (str.contains("b(")) { // should be form b(u v)
	    			  vertlist=new NodeLink(packData,str);
	    			  segment=true;
	    		  }
	    		  else
	    			  vertlist=new NodeLink(packData,items);
	    	  }
	    	  if (vertlist==null || vertlist.size()==0) 
	    		  throw new ParserException("usage: double v1 v2 [b(v,w)]");

    		  int origVC=packData.packDCEL.vertCount;
    		  PackDCEL pdans=CombDCEL.doubleDCEL(packData.packDCEL,
    				  vertlist,segment);
    		  alp_sym=pdans.oldNew.findW(packData.getAlpha());
    		  if (alp_sym==0)
    			  alp_sym=sym_alp_tmp;
    		  pdans.redChain=null;
    		  VertexMap vmap=pdans.oldNew;
    		  pdans.fixDCEL(packData);
    		  packData.vertexMap=vmap;
	    		  
    		  // duplicate radii (ignore centers);
    		  if (packData.vertexMap!=null) {
    			  for (int v=origVC+1;v<=packData.packDCEL.vertCount;v++) {
    				  int orig_v=packData.vertexMap.findW(v);
    				  if (orig_v>0)
    					  packData.setRadius(v,packData.getRadius(orig_v));
    			  }
    		  }
    		  return 1;
	      }
		  
		  // ========= disp (and dISp) ======== 
	      // 'dISp' is used internally: just paint active, not secondary canvasses
	      if (cmd.startsWith("disp") || cmd.startsWith("Disp")
	    		  || cmd.startsWith("dISp") || cmd.startsWith("DISp")) {
	    	  String setText=null;
	    	  if (cmd.charAt(0)=='D')
	    		  setText=StringUtil.reconstitute(flagSegs);
	    	  boolean dispLite=false; // disp only the active canvas?
	    	  if (cmd.charAt(1)=='I') dispLite=true; // yes, only active
			  
			  // No flag strings? Use dispOptions, else no action 
			  // (DisplayPanel (checkboxes or tailored string))
			  if (flagSegs==null || flagSegs.size()==0) {
				  String tmpstr=packData.cpDrawing.dispOptions.toString().trim();
				  if (tmpstr.length()==0)
					  return 1; // no error, but no action
				  StringBuilder strbld=new StringBuilder(cmd);
				  strbld.append(" ");
				  strbld.append(tmpstr);
				  jexecute(packData,strbld.toString());
				  if (setText!=null && !dispLite) // record as display text?
					  jexecute(packData,new String("set_disp_flags "+setText));
				  return 1;
			  }
			  
			  Vector<String> first_seg=flagSegs.get(0);
			  // -w should be first; -wr redraws and exits
			  String fs=first_seg.get(0).toString().trim();
			  if (fs.startsWith("-w")) {
				  packData.cpDrawing.clearCanvas(false);
				  if (fs.startsWith("-wr")) {
					  String tmpstr=packData.cpDrawing.dispOptions.toString().trim();
					  if (tmpstr.equals("-w"))
						  return jexecute(packData,cmd+" ");
					  // remove redundant -w (or -wr)
					  if (tmpstr.startsWith("-w"))
						  tmpstr=tmpstr.substring(3); 
					  return jexecute(packData,cmd+" "+tmpstr);
				  }
				  count++;
				  flagSegs.remove(0);
				  fs=null;
			  }
			  
			  // there might be a -q flag as well
			  if (fs==null && flagSegs.size()>0) {
				  first_seg=flagSegs.get(0);
				  fs=first_seg.get(0).toString().trim();
			  }
			  
			  // display in canvas of a different packing?
			  if (fs!=null && fs.startsWith("-q")) {
				  int qnum=StringUtil.qFlagParse(fs);
				  if (qnum>=0) {
					  CPdrawing qScreen=CPBase.cpDrawing[qnum];
					  if (qScreen.getPackData().status) {
						  flagSegs.remove(0); // dump this -q segment
						  count +=DisplayParser.dispParse(
								  packData,qScreen,flagSegs);
						  if (count>0) 
							  PackControl.canvasRedrawer.
							  	paintMyCanvasses(qScreen.getPackData(),
							  			dispLite);
						  return count;
					  }
				  }
				  else // failed to get another pack
					  return 0;
			  }
				  
			  // send for parsing/execution
			  count +=DisplayParser.dispParse(packData,flagSegs);
			  if (count>0) 
				  PackControl.canvasRedrawer.
				  	paintMyCanvasses(packData,dispLite);
			  if (setText!=null && !dispLite) // record as display text?
				  jexecute(packData,new String("set_disp_flags "+setText));
			  return count;
		  } 
	      break;
	  } // end of 'd'
	  case 'e':
	  {
		  // ========= elist_to_path =======
		  if (cmd.startsWith("elist_to_pat")) {
			  EdgeLink elink=null;
			  try {
				  elink=new EdgeLink(packData,flagSegs.get(0));
			  } catch (Exception ex) {
				  throw new CombException("failed to get edge list: "+
						  ex.getMessage());
			  }
			  // NOTE: 'ClosePath' is reset
			  Path2D.Double gpath=CPBase.ClosedPath=new Path2D.Double();
			  Iterator<EdgeSimple> elt=elink.iterator();
			  EdgeSimple edge=null;
			  Complex z=null;
			  if (elt.hasNext()) { // start
				  edge=elt.next();
				  z=packData.getCenter(edge.v);
				  if (packData.hes>0) // spherical is moved to plane
					  z=SphericalMath.s_pt_to_plane(z);
				  gpath.moveTo(z.x,z.y);
				  z=packData.getCenter(edge.w);
				  if (packData.hes>0) // spherical is moved to plane
					  z=SphericalMath.s_pt_to_plane(z);
				  gpath.lineTo(z.x,z.y);
			  }
			  while(elt.hasNext()) { // add point
				  edge=elt.next();
				  z=packData.getCenter(edge.w);
				  if (packData.hes>0) // spherical is moved to plane
					  z=SphericalMath.s_pt_to_plane(z);
				  gpath.lineTo(z.x,z.y);
			  }
			  gpath.closePath();
			  count++;
		  }
	      // =========== enclose ============
		  if (cmd.startsWith("enclose")) {
			  boolean totalFlag=false;
	    	  items=(Vector<String>)flagSegs.get(0); // data: -[t] n {v..}
	    	  
	    	  // easy to make error
	    	  if (items.size()==1) {
	    		  packData.flashError("usage: enclose -[t] n {v..}");
	    		  return 0;
	    	  }
	    	  if (StringUtil.isFlag(items.get(0))) {
	    		  if (!items.get(0).equals("-t"))
	    			  throw new ParserException("illegal flag");
	    		  totalFlag=true;
	    		  items.remove(0);
	    	  }
	    	  
	    	  int overCount=0;
	    	  int N=Integer.parseInt((String)items.remove(0));
	    	  if (N<0 || N>(PackData.MAX_PETALS-3)) {
	    		  throw new DataException("usage -[t] n {v..}; n>=0, n<"+
	    				  (int)(PackData.MAX_PETALS-2));
	    	  }
	    	  NodeLink vertlist=new NodeLink(packData,items);
	    	  Iterator<Integer> vlist=vertlist.iterator();
    		  while (vlist.hasNext()) {
    			  Vertex vert=packData.packDCEL.vertices[vlist.next()];
    			  int v=vert.vertIndx;
    			  if (vert.isBdry()) {
    				  int num=packData.countFaces(v);
    				  int n=N;
    				  
    				  // reset n to get total degree N
    				  if (totalFlag) {
    					  if (N>=PackData.MAX_PETALS) {
    						  throw new ParserException("max degree limit "+
    								  PackData.MAX_PETALS);
    					  }
    					  n=N-(num+1);
    					  if (n<0) {
    						  overCount++;
    						  n=0;
    					  }
    				  }
    				  
    				  // else adding n circles (up to limit)
    				  else {
    					  int m=PackData.MAX_PETALS-num-1;
	    				  n=(n<m)? n:m;
    				  }

    				  // add the n circles and close up
    				  HalfLink addedEdges=new HalfLink();
    				  int tick=0;
    				  for (int i=1;i<=n;i++) {
    					  HalfEdge he=vert.halfedge.twin.next;
    					  Vertex newV=RawManip.addVert_raw(packData.packDCEL,he);
    					  if (newV==null)
    						  throw new CombException("failure in adding edge to "+
    								  vert.vertIndx);
    					  addedEdges.add(he);
    					  tick++;
    				  }
    				  if (tick>0) { // set successive cent/rad
    					  packData.packDCEL.addedVertData(addedEdges);
    				  }
    				  packData.enfold(v); // this call does 'fixDCEL'
    				  Complex z=packData.getCenter(
    						  packData.getFirstPetal(v));
    				  Complex w=packData.getCenter(
    						  packData.getLastPetal(v));
    				  packData.cpDrawing.drawEdge(z,w,new DispFlags(null));
    				  count++;
    			  }
    		  } // end of while
	    	  
	    	  if (count>0) {
    			  packData.packDCEL.fixDCEL(packData);
	    		  packData.fillcurves();
	    	  }
    		  if (overCount>0) {
    			  CirclePack.cpb.msg("One or more circles "+
    					  "exceeded desired degree");
    		  }
	    	  return count;
	      }
	      
	      // ============ embed ============
	      else if (cmd.startsWith("embed")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just one segment
	    	  String str=(String)items.get(0);
	    	  // must start with other packing number
	    	  PackData q=null;
	    	  try {
	    		  if ((q=CPBase.cpDrawing[StringUtil.qFlagParse(str)].
	    				  getPackData())==packData) {
	    			  throw new ParserException();
	    		  }
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: embed: -q{q} a b A B");
	    	  }
	    	  int a=Integer.parseInt((String)items.get(1));
	    	  int b=Integer.parseInt((String)items.get(2));
	    	  int A=Integer.parseInt((String)items.get(3));
	    	  int B=Integer.parseInt((String)items.get(4));
	    	  VertexMap vMap=Embedder.embed(packData,q,a,b,A,B);
	    	  count=vMap.size();
	    	  if (count>0) {
	    	      packData.vertexMap=vMap;
	    	      if (count==packData.nodeCount) 
	    	    	  CirclePack.cpb.msg("Full embedding in the "+
	    	    			  "complex of pack "+q.packNum+
	    	    			  " succeeded, embedding stored in Vertex Map");
	    	      else 
	    	    	  CirclePack.cpb.msg("Partial embedding, "+count+
	    	    			  " vertices, in "+
	    	    			  "the complex of pack "+
	    	    			  q.packNum+", stored in Vertex Map");
	    	      return count;
	    	  }
	    	  return 0;
	      }
		  break;
	  } // end of 'e'
	  case 'f':
	  {
	      // ========= flip ========
	      if (cmd.startsWith("flip")) {
			  PackDCEL pdc=packData.packDCEL;
			  if (flagSegs==null || flagSegs.size()==0) 
				  return 0;
	    	  items=flagSegs.elementAt(0); // should be one segment
	    	  String fstr="v"; // default to listed vertices
	    	  if (StringUtil.isFlag(items.get(0))) 
	    		  fstr=items.remove(0).substring(1);
	    	  
	    	  // Check for 'h' flag first: project edge {v,w} 
	    	  //   forward to next (half-hex) edge {w,u}, then 
	    	  //   flip the edge at v clockwise from {v,w}.
	    	  //   The next edge {w,u} itself is stored in 'elist'
	    	  //   to prepare next call.

    		  if (fstr.charAt(0)=='h') { 
    			  HalfLink hlink=new HalfLink(packData,flagSegs.get(0));
    			  HalfEdge he=hlink.get(0); // only one edge
    			  HalfEdge[] fls=null;
    			  if (he!=null) { 
    				  fls=RawManip.flipAdvance_raw(pdc,he);
    				  if (fls==null) { 
    					  CirclePack.cpb.errMsg(("usage: flip -h {v,w}"));
    					  return 0;
    				  }
	    				  
    				  // should have new edge; add it to 'elist'
    				  if (packData.elist==null)
    					  packData.elist=new EdgeLink(packData);
   					  packData.elist.add(new EdgeSimple(fls[0]));
   					  
   					  // flipped edge as well?
    				  if (fls!=null && fls[1]!=null) { 
    	    			  pdc.fixDCEL(packData);
    	    			  return 1;
    	    		  }
    				  else // only moved, no flip
    					  return -1;
    			  }
    			  else {
		   			  CirclePack.cpb.errMsg(("usage: flip -h {v,w}"));
		   			  return 0;
    			  }
	    	  } // end of 'h' flag case

	    	  // for the non-'h' cases, first collect the flips
/*	    		  // just a list of flips
	    		  else {
	    			  rslt=CombDCEL.flipEdgeList(pdc,hlink);
	    			  if (rslt>0)
	    				  pdc.fixDCEL(packData);
	    			  return rslt;
	    		  }
*/	    		  
	    	  // one random, try up to 20 times 
	    	  if (fstr.contentEquals("r")) { 
	        	  Random rand=new Random();
	        	  int safety=20;
	        	  while (safety>0) {
	        		  int v=Math.abs((
	        				  rand.nextInt())%(packData.nodeCount))+1;
 	        		  // if boundary, try for next interior
	        		  if (packData.isBdry(v)) { 
	        			  int j=1;
	        			  while (j<=packData.nodeCount  
	   						  && packData.isBdry(
	   								  (v=(v+j)%(packData.nodeCount)+1)))
	        				  j++;
	        			  if (packData.isBdry(v)) 
	        				  return 0; // didn't find interior vert
	        		  }
	        		  HalfLink spokes=packData.packDCEL.
	        				  vertices[v].getSpokes(null);
	        		  int num=spokes.size();
	        		  // pick a random spoke
	        		  HalfEdge he=spokes.get(Math.abs((rand.nextInt())%(num)));
	        		  
	        		  // try to flip
	   				  if (RawManip.flipEdge_raw(packData.packDCEL,he)!=null) {
		   				  packData.packDCEL.fixDCEL(packData);
		   				  return 1;
	   				  }
	        	  }
	        	  return 0;
	    	  }

	    	  // For remaining cases, just build 'elink' of edges to flip, .
	   		  EdgeLink elink=new EdgeLink();
	   		  if (items.size()==0)
	   			  return 0;
	   		  EdgeLink origLink=new EdgeLink(packData,items);
	   		  if (origLink==null || origLink.size()==0)
	   			  return 0;
	   		  Iterator<EdgeSimple> elist=origLink.iterator();

	    	  // -cc flag (deprecated -hh): for each edge, flip the next 
	    	  //     counterclockwise edge. 
	   		  // (E.g., 'half-hex' path flips, see 'hh_path')
	    	  if (fstr.startsWith("cc") || fstr.startsWith("hh")) { 
	    		  while (elist.hasNext()) {
		   			  EdgeSimple edge=(EdgeSimple)elist.next();
		   			  int indx=packData.nghb(edge.v,edge.w);
 		   			  // flip cclw edge
		   			  if (indx>=0 && indx<packData.countFaces(edge.v)) {
		   				  int w=packData.getPetal(edge.v,indx+1);
		   				  elink.add(new EdgeSimple(edge.v,w));
		   			  }
	    		  }
	    	  }
	    	  // flip the clockwise edge (as along half-hex paths)
	    	  else if (fstr.startsWith("cw")) { 
	    		  int w;
	    		  while (elist.hasNext()) {
		   			  EdgeSimple edge=(EdgeSimple)elist.next();
		   			  int indx=packData.nghb(edge.v,edge.w);
		   			  if (indx==0) { // must be interior
		   				  w=packData.getPetal(edge.v,
		   						  packData.countFaces(edge.v)-1);
		   			  }
		   			  else 
		   				  w=packData.getPetal(edge.v,indx-1);
	   				  elink.add(new EdgeSimple(edge.v,w));
	    		  }
	    	  }
	    	  
	    	  // reaching here, just flip edges in the list
	    	  else { 
	    		  if (origLink==null || origLink.size()<1) 
	    			  return 0;
	    		  elist=origLink.iterator();

	    		  while (elist.hasNext()) {
    				  elink.add((EdgeSimple)elist.next());
	    		  }
	    	  }  
	    		
	    	  // done building list, so flip
	    	  return packData.flipList(elink);
	      }
	      
	      // ============ flat_hex =========
	      if (cmd.startsWith("flat_hex")) {
	    	  NodeLink vertlist=new NodeLink(packData,
	    			  (Vector<String>)flagSegs.get(0));
	    	  return packData.flat_hex(vertlist);
	      }
	      // ========= face_err ============
	      else if (cmd.startsWith("face_err")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just one
	    	  double crit=Double.parseDouble((String)items.remove(0));
	    	  if (crit<=1.0) {
	    		  throw new ParserException("usage: x must exceed 1.0.");
	    	  }
	    	  FaceLink facelist=new FaceLink(packData,items);
	    	  int hits=facelist.size();
	    	  count=QualMeasures.count_face_error(packData,crit,facelist);
	    	  CirclePack.cpb.msg("face_error: found "+count+
						 " poorly placed faces out of "+hits);
	    	  return 1;  
	      }
	      
	      // ========= focus ========
	      else if (cmd.startsWith("focus")) {
	    	  boolean vert_mode=true; // 
	    	  boolean zoom=false;
	    	  Complex z=null;
	    	  double x=1.0;
	    	  double rad=1.0;
	    	  int v=packData.activeNode;
	    	  // should be just one segment
	    	  items=(Vector<String>)flagSegs.get(0); 
	    	  String str=(String)items.get(0);
	    	  try {
	    		  if (str.startsWith("-vs")) { // also set screen size 
	    			  zoom=true;
	    			  x=Double.parseDouble((String)items.get(1));
	    			  v=NodeLink.grab_one_vert(packData,
	    					  (String)items.get(2));
	    		  }
	    		  else if (str.startsWith("-v")) {
	    			  v=NodeLink.grab_one_vert(packData,
	    					  (String)items.get(1));
	    		  }
	    		  else if (str.startsWith("-z")) {
	    			  vert_mode=false;
	    			  z=new Complex(Double.parseDouble((String)items.get(1)),
	    					  Double.parseDouble((String)items.get(2)));
	    		  }
	    		  else { // read first str as vertex
	    			  v=NodeLink.grab_one_vert(packData,str); 
	    		  }
	    	  } catch (Exception ex) {
	    		  v=packData.activeNode;
	    	  }
	    	  if (vert_mode) {
	    		  z=packData.getCenter(v);
	    		  rad=packData.getRadius(v);
	    		  if (packData.hes<0) {
	    			  CircleSimple sc=HyperbolicMath.h_to_e_data(z,rad);
	    			  z=sc.center;
	    			  rad=sc.rad;
	    		  }
	    		  else if (packData.hes>0) {
	    			  z=packData.cpDrawing.sphView.toApparentSph(z);
	    			  // TODO: do we need to check if on back?
	    		  }
	    		  if (zoom) {
	    			  count += packData.cpDrawing.realBox.setWidthHeight(2.0*rad*x);
	    		  }
	    	  }
	    	  count += packData.cpDrawing.realBox.focusView(z);//.times(-1.0));
	    	  packData.cpDrawing.update(2);
	    	  try {
	    		  jexecute(packData,"disp -wr");
	    	  } catch (Exception ex) {}
	    	  if (count>0) {
	    		  packData.cpDrawing.repaint();
	    	  }
	    	  return count;
	      }
	      
	      // ========= frackMe ========
	      else if (cmd.startsWith("frac")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just one
	    	  NodeLink verts=new NodeLink(packData,items);
	    	  if (verts==null || verts.size()==0) {
	    		  CirclePack.cpb.errMsg("usage: frack {v..}; "+
	    				  "must provide vertices");
	    		  return 0;
	    	  }
	    	  
	    	  Iterator<Integer> vis=verts.iterator();
	    	  while (vis.hasNext() && 
	    			  RawManip.frackVert(packData.packDCEL,vis.next())>0) {
	    		  count++;
	    	  }
	    	  if (count>0)
	    		  packData.packDCEL.fixDCEL(packData);
	    	  return packData.nodeCount;
	      }
	      
		  break;
	  } // end of 'f'
	  case 'g':
	  {
	      // =========== gamma ============
	      if (cmd.startsWith("gamma")) {
	    	  int a=NodeLink.grab_one_vert(packData,flagSegs);
    		  packData.packDCEL.setGamma(a);
    		  return packData.packDCEL.layoutPacking();
	      }
	      
	      // ========= gen_cut =========
	      else if (cmd.startsWith("gen_cut")) {
	    	  
	    	  // TODO: Need to recode this; not sure what behaviour
	    	  //   was intended, but some of the old 'RedChainer'
	    	  //   code has not been updated.
	    	  
	    	  throw new ParserException(
	    			  "'gen_cut' call is no longer implemented");
/*
 	    	  if (packData.locks!=0 || !packData.isSimplyConnected()) {
	    		  throw new ParserException(
	    				  "packing must be simply connected");
	    	  }
	    	  items=(Vector<String>)flagSegs.get(0); // form: v n
	    	  int v,n;
	    	  try {
	    		  v=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    		  n=Integer.parseInt((String)items.get(1));
	    		  if (n<2 || v<1 || v>packData.nodeCount) 
	    			  throw new ParserException("'v' or 'n' improper");
	    	  } catch (Exception ex) {
	    		  throw new DataException("usage: v n");
	    	  }
	    	  PackData pd = packData.gen_cut(v,n);
	    	  if (pd==null) {
	    		  CirclePack.cpb.msg("gen_cut: error "+
	    				  "or no vertices were cut");
	    		  return 1;
	    	  }
	    	  int pnum=packData.packNum;
	    	  CirclePack.cpb.swapPackData(pd,pnum,false);
	    	  packData=pd;
			  CirclePack.cpb.msg("gen_cut: the new packing has "+
					  pd.nodeCount+" vertices");
	    	  return pd.nodeCount;
*/
	      }
	      
	      // =========== gen_mark ==========
	      else if (cmd.startsWith("gen_mark")) {
	    	  // Call routine which records generations of 
	    	  // vertices or faces as measured from 'seeds' 
	    	  // (specified in datastr). Records generation 
	    	  // in 'mark' and returns the last vertex or 
	    	  // face marked. Option '-m n' tells it to stop 
	    	  // at max generation n.
	    	  CallPacket cP=CommandStrParser.valueExecute(
	    			  packData,cmd,flagSegs);
	    	  if (cP==null || cP.int_vec==null || cP.int_vec.size()==0)
	    		  return 0; // failed
	    	  return (int)cP.int_vec.get(0);
	      }
	      
	      // =============== geom_to_
	      if (cmd.startsWith("geom_to_")) {
	    	  boolean leave_flag=false;
	    	  double []radii=new double[packData.nodeCount+1];

	      	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    	  while (nextFlag.hasNext() && 
	    			  (items=nextFlag.next()).size()>0) {
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  switch(items.get(0).charAt(1)) {
	    			  	case 'l': // leave old radii, even with geom changes
	    			  		{leave_flag=true;}
	    			  } // end of flag switch
	    		  } // done handling a given flag segment
	    	  }
	    	  
	    	  if (leave_flag) {
	    		for (int v=1;v<=packData.nodeCount;v++) 
	    			radii[v]=packData.getRadius(v);
	    	  }
	    	  int old_hes=packData.hes;
	    	  char c=cmd.charAt(8);
	    	  switch(c) { // to which geometry?
	    	  case 'h': // to hyperbolic
	    	  {
	    		  packData.geom_to_h();
	    		  packData.fillcurves();
	    		  break;
	    	  }
	    	  case 's': // to spherical
	    	  {
	    		  packData.geom_to_s();
	    		  break;
	    	  }
	    	  case 'e': // to eucl
	    	  {
	    		  packData.geom_to_e();
	    		  packData.fillcurves();
	    		  break;
	    	  }
	    	  } // end of switch
	    	  if (leave_flag) {
	    		  if (old_hes<0) {
	    			  for (int v=1;v<=packData.nodeCount;v++) {
	    				  if (radii[v]<=0) radii[v]=5.0;
	    			  }
	    		  }
				  for (int v=1;v<=packData.nodeCount;v++) {
					  packData.setRadius(v,radii[v]);
				  }
	    	  }
	    	  
	    	  packData.setGeometry(packData.hes);
	    	  if (packData.cpDrawing!=null) 
	    		  packData.cpDrawing.setPackName();
	    	  return 1;
	      } 
	      
	      // =============== get_var
	      else if (cmd.startsWith("get_var")) {
	    	  String vname=null;
	    	  try {
	    		  vname=flagSegs.get(0).get(0).trim();
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: variable name missing");
	    	  }
	    	  if (vname==null || vname.length()==0)
	    		  throw new ParserException("usage: variable name missing");
	    	  String gval=null;
	    	  if ((gval=CPBase.varControl.getValue(vname))!=null) {
	    		  CirclePack.cpb.msg(new String(vname+" = "+gval));
	    		  return 1;
	    	  }
	    	  else
	    		  return 0;
	      }
	      
		  break;
	  } // end of 'g'
	  case 'h':
	  {
		  
	      // =========== hex_refine =========
		  if (cmd.startsWith("hex_ref")) {
	    	  return packData.hex_refine();
	      }

	      // =========== hex_slide ==========
		  else if (cmd.startsWith("hex_slide")) {
	    	  HalfLink edgelist=null;
	    	  HalfEdge he=null;
	    	  try {
	        	  items=(Vector<String>)flagSegs.get(0); // just one
	    		  edgelist=new HalfLink(packData,items);
	    		  he=edgelist.get(0);
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: hex_slide v w ");
	    	  }
	    	  // get 'hexChain' simple closed hex axis
	    	  edgelist=new HalfLink(packData,"eh "+he);
	    	  int ans= packData.right_slide(edgelist);
	    	  if (ans==0) {
	    		  he=edgelist.get(0);
	    		  throw new ParserException("failed for edge <"+
	    				  he+">");
	    	  }
	    	  else {
	    		  count +=ans;
	    		  CirclePack.cpb.msg(
	    				  "hex_slide "+he+" succeeded");
	    	  }
	    	  return count;
	      }
		  
		  
		  // =========== hh_path =================
		  else if (cmd.startsWith("hh_path")) {
/*			    OPTIONS: (default to -b -S -x)
		(16)    -a     append to given edgepath 
		               (i.e., {e} is a list {e..}) 
		(8)		-b     stop when the next edge would 
		               lie in the boundary
		(1)	    -c     continue --- no stop options
				-N {n} add at most n edges (counting e)  
		(4)		-S     stop when edge runs into AND 
		               lines up with the original edge e. 
		               (If this flag is set, it overrides -x flag only
					   in this instance.)
		(2)		-x	   stop when encountering vert already hit on path
*/				
			  if (flagSegs==null || flagSegs.size()==0) 
				  throw new ParserException("hh_path: check usage");
			  Iterator<Vector<String>> its=flagSegs.iterator();
			  int stopCon=0;
			  int N=-1;
			  EdgeLink edgelist=null;
			  while (its.hasNext()) {
				  items=(Vector<String>)its.next();
	    		  String str=(String)items.get(0);
	    		  if (StringUtil.isFlag(str)) { // 
	    			  if (str.equals("-a")) // append 
	    				  stopCon+=16;
	    			  else if (str.equals("-b")) // bdry stop
	    				  stopCon+=8;
	    			  else if (str.equals("-c")) // bdry stop
	    				  stopCon+=1;
	    			  else if (str.equals("-x")) // cross path
	    				  stopCon+=2;
	    			  else if (str.equals("-S")) // close up
	    				  stopCon+=4;
	    			  else if (str.equals("-N")) { // length limit
	    				  try {
	    					  items.remove(0); // dump 'str' entry
	    					  N=Integer.parseInt((String)items.remove(0));
	    				  } catch(Exception ex) {
	    					  throw new ParserException(
	    							  "hh_path: usage hh_path -N {n}");
	    				  }
	    				  if (N<1) throw new ParserException(
	    						  "hh_path: usage -N {n}, n>0");
	    			  }
	    			  // flag seq is last --- should have edgelist
	    			  if (!its.hasNext()) { 
		    			  edgelist=new EdgeLink(packData,items);
	    			  }
	    		  }
	    		  else {
	    			  edgelist=new EdgeLink(packData,items);
	    		  }
			  }
			  if (edgelist==null || edgelist.size()==0)
				  throw new ParserException("hh_path: no edgelist given");
			  if (stopCon==0) stopCon=14; // default
	    	  EdgeLink edge_rslts=HexPaths.halfHexPath(packData,edgelist,stopCon,N);
	    	  if (edge_rslts==null || edge_rslts.size()==0) { 
	    		  throw new CombException("no half-hex path found");
	    	  }
	    	  else packData.elist=edge_rslts;
	    	  return edge_rslts.size(); 
		  }
		  
	      // =========== holonomy_tr ==============
	      else if (cmd.startsWith("holonomy_tr")) {
	    	  String filename=null;
	    	  BufferedWriter fp=null;
	    	  boolean script_flag=false;
	    	  // TODO: not handling append for script files.
	    	  boolean append_flag=false;
	    	  
	    	  // check for filename [-[fas] <filename>]
	    	  if (StringUtil.ckTrailingFileName(flagSegs)) {
	       	  	StringBuilder strbuf=new StringBuilder("");
	       	  	int code=CPFileManager.trailingFile(flagSegs, strbuf);
	       	  	File file=new File(strbuf.toString());
	       	  	filename=file.getName();
	       	  	String dir=file.getParent();
	       	  	if (dir==null)
	       	  		dir=CPFileManager.CurrentDirectory.toString();
	       	  	script_flag=((code & 04)==04);
	       	  	append_flag=((code & 02)==02);
	       	  	fp=CPFileManager.openWriteFP(new File(dir),
	    			  append_flag,filename,script_flag);
	       	  	if (fp==null)
	       	  		CirclePack.cpb.errMsg("Failed to open '"+filename+
	       	  				"' for writing");	
	    	  }
	    	  
	    	  // '-s {n}' flag or halfedge list
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  HalfLink hlink=new HalfLink();
	    	  if (StringUtil.isFlag(items.get(0))) {
	    		  if (items.get(0).charAt(1)=='s') { // side index
	    			  int sideIndx=-1;
	    			  try {
	    			  if (items.size()==1 && items.get(0).length()>2) { // no space?
	    				  String substr=items.get(0).substring(2);
	    				  sideIndx=Integer.parseInt(substr);
	    			  }
	    			  else 
	    				  sideIndx=Integer.parseInt(items.get(1));
	    			  } catch (Exception ex) {
	    				  throw new ParserException("usage: holonomy -s {n}");
	    			  }
	    			  hlink=HalfLink.HoloHalfLink(packData.packDCEL,sideIndx);
	    		  }
	    	  }
	    	  else {
	    		  FaceLink facelist=new FaceLink(packData,items);
	    		  if (facelist==null || facelist.size()<2) {
	    			  throw new ParserException("usage: holonomy: {f..}");
	    		  }
	    	  
	    		  // convert to corresponding 'HalfLink'
	    		  Iterator<Integer> fis=facelist.iterator();
	    		  combinatorics.komplex.DcelFace currF=packData.packDCEL.faces[fis.next()];
	    		  combinatorics.komplex.DcelFace nextF=packData.packDCEL.faces[fis.next()];
	    		  HalfEdge he=currF.faceNghb(nextF);
	    		  if (he==null) {
	    			  throw new ParserException("first two faces not contiguous");
	    		  }
	    	  
	    		  hlink.add(he.prev);
	    		  hlink.add(he.twin);
	    		  while (fis.hasNext()) {
	    			  currF=nextF;
	    			  nextF=packData.packDCEL.faces[fis.next()];
	    			  he=currF.faceNghb(nextF);
	    			  if (he==null) 
	    				  throw new ParserException("Faces "+currF.faceIndx+
	    					  " and "+nextF.faceIndx+" are not contiguous");
	    			  hlink.add(he.twin);
	    		  }
	    	  }
	    	  
	    	  if (hlink==null || hlink.size()<3) {
	    		  throw new ParserException(
	    				  "usage holonomy: failed to get HalfLink");
	    	  }
	    	  combinatorics.komplex.DcelFace firstF=hlink.getFirst().face;
	    	  combinatorics.komplex.DcelFace lastF=hlink.getLast().face;
	    	  if (firstF==null || lastF==null || firstF!=lastF)
	    		  throw new ParserException(
	    				  "usage holonomy: list doesn't have same face first and last");
	    	  Mobius holomob=PackData.holonomyMobius(packData,hlink);
	    	  double frobNorm=Mobius.frobeniusNorm(holomob);
			  CirclePack.cpb.msg(
					  "Frobenius norm "+String.format("%.8e",frobNorm)+
					  ", \nMobius is: \n"+
					  "  a = ("+String.format("%.8e",holomob.a.x)+","+
					  String.format("%.8e",holomob.a.y)+
					  ")   b = ("+String.format("%.8e",holomob.b.x)+","+
					  String.format("%.8e",holomob.b.y)+")\n"+
					  "  c = ("+String.format("%.8e",holomob.c.x)+","+
					  String.format("%.8e",holomob.c.y)+
					  ")   d = ("+String.format("%.8e",holomob.d.x)+","+
					  String.format("%.8e",holomob.d.y)+")");
			  if (fp!=null) { // print to file also 
				  try {
			    fp.write("\nFrobenius norm:\n  ");
			    fp.write(frobNorm+" \n");
			    // print mobius 
			    fp.write("Mobius:\n  a= "+holomob.a.x+" + i*("+holomob.a.y+")\n");
			    fp.write("  b= "+holomob.b.x+" + i*("+holomob.b.y+")\n");
			    fp.write("  c= "+holomob.c.x+" + i*("+holomob.c.y+")\n");
			    fp.write("  d= "+holomob.d.x+" + i*("+holomob.d.y+")\n\n");
				  } catch(Exception ex) {
					  CirclePack.cpb.myErrorMsg("There were IOExceptions");
				  }
			  }
	    	  if (fp!=null) {
	    		  try {
	    			  fp.flush();
	    			  fp.close();
	    		  } catch(Exception ex) {
	    			  CirclePack.cpb.myErrorMsg("holonomy_tr: "+
	    					  "IOException: "+ex.getMessage());
	    		  }
	    	  }
	    	  if (frobNorm>=0)
	    		  return 1;
	    	  return 0;
	      }
		  
	      // ========== h_dist ===========
	      else if (cmd.startsWith("h_dist")) {
	    	  items=(Vector<String>)flagSegs.get(0); // expect two complex numbers
	    	  Complex z=new Complex(Double.parseDouble((String)items.get(0)),
	    			  Double.parseDouble((String)items.get(1)));
	    	  Complex w=new Complex(Double.parseDouble((String)items.get(2)),
	    			  Double.parseDouble((String)items.get(3)));
	    	  double dist=HyperbolicMath.h_dist(z,w);
	    	  if (dist==0.0) 
	    		  CirclePack.cpb.msg("h_dist: points are essentially equal");
	    	  else if (dist<0.0)	    		  
	    		  CirclePack.cpb.msg("h_dist: one/both are on or "+
	    				  "outside the unit circle; euclidean "+
	    				  "separation is "+(-1.0*dist));
	    	  else CirclePack.cpb.msg("h_dist is "+dist);
	    	  return 1;
	      }
	      		  
		  break;
	  } // end of 'h'
	  case 'i':
	  {
		  break;
	  } // end of 'i'
	  case 'j':
	  {	  
		  break;
	  } // end of 'j'
	  case 'k':
	  {
		  break;
	  } // end of 'k'
	  case 'l':
	  {
		  // ============== locate ==========
		  if (cmd.startsWith("locat")) {
	    	  boolean circles=true;
	    	  int msg_flag=3;
	    	  LinkedList<Integer> list=null;
	    	  Complex z=null;
	    	  items=(Vector<String>)flagSegs.get(0); // at most one flag
	    	  String str=(String)items.get(0);
	    	  if (StringUtil.isFlag(str)) {
	    		  // locate faces, circles is default
	    		  if (str.startsWith("-f")) circles=false;
	    		  items.remove(0);
	    	  }
	    	  try {
	    		  z=new Complex(Double.parseDouble((String)items.get(0)),
	    				  Double.parseDouble((String)items.get(1)));
	    	  } catch (Exception ex) {}
	    	  if (packData.hes>0) { // sphere
	    		  z=SphView.visual_plane_to_s_pt(z);
	    		  z=packData.cpDrawing.sphView.toRealSph(z);
	    	  }
	    	  if (circles && (list=packData.cir_search(z))!=null && 
	    			  list.size()>0) { // circles
	    		  count=packData.labellist(list,msg_flag,true);
	    		  packData.activeNode=(Integer)list.get(0);
	    	  }
	    	  else if (!circles && (list=packData.tri_search(z))!=null && 
	    			  list.size()>0) { // faces
	    		  count=packData.labellist(list,msg_flag,false);
	    	  }
	    	  return count;
	      }
		  
	      // ========= layout (deprecated: fix) =======
	      
	      /* 
	       * Various layout functions, ie., locating circles based on 
	       * radii and combinatorics; also checking/updating data on
	       * packing combinatorics, layout order, values of angle sums, etc.
	       * 
	       * NOTE: some options change the information held in faces
	       * about the drawing order, others use various info to
	       * set centers.
	       * 
	       * TODO: not all options are yet covered in DCEL case.
	       * 
	       * Typical call is just 'layout' without any flags.
	       * 
	       * Option:
	       *	-a        default aims
	       *	-c [dfsc] compute centers
	       *       d   write diagnostics in 'layout.log'
	       *       f   use only 'well-placed' circles in layout
	       *       s   use drawing order only -- not average.
	       *       c x (with f) critical value x (double)
	       *    -d [v]    layout by drawing order, reporting location(s) of v.
	       *    -dt [c]   special for torus: create 2 side-pairing layout, report
	       *    		  locations of corner.
	       *    -F        redo everything
	       *    -h vwn    drawing order by 'hex_walk' routine (not active)
	       *    -K        redo combinatorics and dcel
	       *    -r {f..}  recompute (don't draw) centers along given facelist
	       *    -s        recompute angle sums
	       *    -l        suppress poorly placed circles 
	       *    		  (better to use -cf option above)
	       *    -t        compute centers from 'tailored' drawing order; 
	       *              vertices with 'mark' set will not (to extent 
	       *              possible) be used in drawing order.
	       *    -T        same as -t, but routine will NOT use the 
	       *    		  vertices with 'mark' set; it will simply stop 
	       *    		  once it has done all it can without them.
	       *    -v {v..}  redo facedraworder with vertices defining the bdry
	       *    -x        experimental routine.
	       */
		  else if (cmd.startsWith("layout")) {
			  PackDCEL pdc=packData.packDCEL;
			  boolean useSchw=false; 
			  
			  // most typical call, no flags
	    	  if (flagSegs.size()==0) {
	    		  pdc.layoutPacking();
	    		  packData.fillcurves();
	    		  return 1;
	    	  }

	    	  items=flagSegs.get(0);

	    	  // not a flag? try to extract HalfLink
	    	  if (items.get(0).length()>0 && !StringUtil.isFlag(items.get(0))) {
	    		  int gc=0;
	    		  HalfLink hlink=new HalfLink(packData,items.get(0));
	    		  if (hlink!=null && hlink.size()>0)
	    			  gc=pdc.layoutPacking(hlink); // return count
	    		  return gc;
	    	  }
	    	  
	    	  // first flag is "-s" indicates use schwarzians for layouts
	    	  if (items.get(0).startsWith("-s")) {
				  if (packData.hes<0) {
					  CirclePack.cpb.errMsg("Can't use schwarzians for "
					  		+ "layout in the hyperbolic setting");
					  return 0;
				  }
	    		  items.remove(0);
	    		  useSchw=true;
	    		  if (items.size()==0) {
	    			  flagSegs.remove(0);
	    			  if (flagSegs.size()==0) {
	    				  pdc.layoutPacking(useSchw);
	    				  return 1;
	    			  }
	    		  }
	    	  }

	    	  Iterator<Vector<String>> its=flagSegs.iterator();
	    	  String str=null;
	    	  boolean tflag=false;
	    	  while (its.hasNext()) {
	    		  items=(Vector<String>)its.next();
	    		  str=(String)items.remove(0);
	    		  switch(str.charAt(1)) {
	    		  case 'a': // default aims
	    		  {
	    			  packData.set_aim_default();
	    			  count++;
	    			  break;
	    		  }
	    		  case 'c': // compute center:
	    		  {
    				  pdc.layoutPacking(useSchw);
    				  count++;
	    			  break;
	    		  }
	    		  case 'd': // 'd [v]' layout by drawing order, normalize, report
  			  	    // 'dt [v]' for torus only, tries to layout 2-side pairs, with
  			  		//  optional corner vertex 'v'.
	    		  {
	    			  if (str.charAt(2)=='t') { // does nothing if not a torus
	    				  if (packData.genus!=1 || packData.getBdryCompCount()!=0) {
	    					  CirclePack.cpb.errMsg(
	    							  "usage: 'layout -dt' only applies to "+
	    							  "complex that is a 1-torus.");
	    					  break;
	    				  }
		    				
	    				  // TODO: formerly, could specify common corner
	    				  //       vertex for the layout
//  			  	      int v=0;
//	  			      	  if (items.size()>0) {
//	    				  	str=(String)items.get(0);
//	    				  	v=NodeLink.grab_one_vert(packData, str);
//  				  	  }

	    				  if (CombDCEL.torus4Sides(pdc)==null) {
	    					  pdc.fixDCEL(packData);
	    					  throw new CombException("torus 4-sided layout failed");
	    				  }
	    				  CombDCEL.fillInside(pdc);
	    				  pdc.layoutPacking(useSchw);
	    				  break;
	    			  }
	    			  else { // 'd' with optional vert whose locations to report
	    				  str=(String)items.get(0);
	    				  int v=NodeLink.grab_one_vert(packData,str);
	    				  pdc.layoutReport(v,true,false,useSchw);
	    				  count++;
	    				  break;
	    			  }
	    		  }		
	    		  case 'e': // use edgelist of poison edges.
	    		  {
    		    	  NodeLink vlink=new NodeLink(packData,items);
    		    	  RedEdge newRed=RawManip.vlink2red(packData.packDCEL,vlink);
	    	    	  
	    	    	  if (newRed==null) { 
	    	    		  CirclePack.cpb.errMsg("failed to get a new red chain");
	    	    		  break;
	    	    	  }
	    	    		  
	        		  // clear out old red info, then install 'newRed'
	        		  RawManip.wipeRedChain(packData.packDCEL,packData.packDCEL.redChain);
	        		  packData.packDCEL.redChain=newRed;

	        		  // zero out 'eutil'; can set negative for edges to prevent
	        		  //   them from being red-twinned.
	        		  for (int e=1;e<=packData.packDCEL.edgeCount;e++)
	        			  packData.packDCEL.edges[e].eutil=0;
	    				
	    	    	  CombDCEL.finishRedChain(pdc,pdc.redChain);
	    	    	  packData.packDCEL.fixDCEL(packData);
	    	    	  packData.packDCEL.layoutPacking(useSchw);	
	    	    	  break;
	    		  }
	    		  case 'F': // redo combinatorics, reset aims/curv
	    		  {
    				  pdc.redChain=null;
    				  pdc.fixDCEL(packData);
    				  pdc.layoutPacking(useSchw);
	    			  
	    			  // TODO: some traditional pflag options 
	    			  //    aren't implemented in DCEL version yet
	    			  
	    			  packData.fillcurves();
	    			  packData.set_aim_default();
	    			  return 1;
	    		  }
	    		  
/*  TODO: update 
  		  		  case 'h': // drawing order via hex_walk routine
	    		  {
	    			  int v=0,w=0,n=1;
	    			  try {
	    				  v=Integer.parseInt((String)items.get(0));
	    				  w=Integer.parseInt((String)items.get(1));
	    				  n=Integer.parseInt((String)items.get(2));
	    			  } catch (Exception ex) {
	    				  throw new ParserException();
	    			  }
	    			  int []corners=new int[5];
	    			  FaceLink hexwalklist=packData.try_hex_pg(v,w,n,corners);
	    			  if (hexwalklist==null || hexwalklist.size()==0) {
	    				  CirclePack.cpb.myErrorMsg("layout -h: error in finding this face list");
	    				  break;
	    			  }
	    			  packData.poisonEdges=packData.outer_edges(hexwalklist);
	    			  count +=packData.facedraworder(true);
	    			  break;
	    		  }
	*/
	    		  case 'K': // redo combinatorics only
	    		  {
	    			  pdc.redChain=null;
	    			  pdc.fixDCEL(packData);
	    			  count++;
	    			  break;
	    		  }
	    		  case 'r': // recompute centers along given facelist 
	    		  {
	    			  FaceLink facelist=new FaceLink(packData,items);
	    			  if (facelist==null || facelist.size()==0) {
	    				  CirclePack.cpb.myErrorMsg("layout -r: no "+
	    						  "faces were provided.");
	    				  break;
	    			  }
    				  count +=LayoutShop.
    						  layoutFaceList(pdc,facelist,packData.hes,useSchw);
	    			  break;
	    		  }
	    		  case 's': // lay out using schwarzians
	    		  {
	    			  useSchw=true;
		    		  pdc.layoutPacking(useSchw);
		    		  packData.fillcurves();
		    		  count++;
		    		  break;
	    		  }
	    		  case 'T': // tailored (falls through to 't')
	    		  {
	    			  tflag=true;
	    		  }
	    		  case 't': // tailored
	    		  {
	    			  NodeLink markedV=new NodeLink(packData);
	    			  HalfLink newOrder;
	    			  for (int i=1;i<=packData.nodeCount;i++)
	    				  if(packData.getVertMark(i)!=0)  
	    					  markedV.add(i);
	    			  if (markedV.size()==0) {
	    				  CirclePack.cpb.myErrorMsg("layout -[tT]: no vertices "+
	    			    		"have been marked?");
	    			  }
	    			  else if ((newOrder=CombDCEL.
	    					  tailorFaceOrder(pdc,markedV,tflag))!=null) {
	    				  pdc.layoutOrder=newOrder;
	    				  pdc.layoutPacking();
	    				  count++;
    				  }
	    			  packData.fillcurves();
	    			  break;
	    		  }


	    		  } // done with cases
	    	  }  // end of while
	    	  return count;
	    	  
			  // TODO: reintroduce these options in DCEL setting??
//	    	  double crit=LAYOUT_THRESHOLD;
	    	  // Options for computing center of v:
	    	  //   opt=1: use only one pair of contiguous neighbors, 
	    	  //          typically specified in the data of face 
	    	  //          used to plot v.
	    	  //   opt=2: use all pairs of contiguous neighbors 
	    	  //          already plotted, average the resulting 
	    	  //          centers for v.
//	    	  int opt=2;             // default to use all plotted neighbors
//	    	  boolean errflag=false; // only use 'well-plotted' in layout
//	    	  boolean dflag=false;   // debugging help

		  }
	  } // end of 'l'
	  case 'm': // fall through
	  case 'M':
	  {
		  //    ========== map_bary ===========
		  if (cmd.startsWith("map_bary")) {
			  
			  String filename="";
			  String message="";
			  
			  // have to have the base packing (associated with conformal map)
	          items=(Vector<String>)flagSegs.get(0); // -q{p} 
	          String str=(String)items.remove(0);
	          PackData qackData=null;
	          try {
	        	  qackData=CPBase.cpDrawing[StringUtil.
	        	                            qFlagParse(str)].getPackData();
	          } catch (Exception ex) {
        		  throw new ParserException("'q' packing is not active");
	          }
			  String filestr=flagSegs.lastElement().get(0);
			  if (!filestr.startsWith("-f") && !filestr.startsWith("-a")) 
				  throw new InOutException("usage: "+
						  "write_custom ... -[fa] {filename}");
			  StringBuilder strbuf=new StringBuilder("");
			  int code=CPFileManager.trailingFile(flagSegs, strbuf);
			  filename=strbuf.toString();
			  File file=new File(filename);
			  String dir=file.getParent();
			  if (dir==null)
				  dir=CPFileManager.CurrentDirectory.toString();
			  else if (cmd.charAt(0)=='W')
				  dir=CPFileManager.HomeDirectory.toString();
			  boolean script_flag=((code & 04)==04);
			  boolean append_flag=((code & 02)==02);
			  BufferedWriter fp=CPFileManager.openWriteFP(new File(dir),
				  append_flag,file.getName(),script_flag);
			  if (fp==null)
				  throw new InOutException("Failed to open '"+file.toString()+
						  "' for writing in 'map_bary'");
			  
			  int ans=PackMethods.writeDualBarys(fp, packData, qackData);
			  try {
				  fp.close();
			  } catch(Exception ex) {
				  CirclePack.cpb.errMsg("problem in 'map_bary'");
			  }

	    	  if (ans>0) {
	    		  // for "message" window
	    		  if (script_flag) {
	     			  CPBase.scriptManager.includeNewFile(filename);
	    			  message=new String("output data "+
	     			  "appended as file '"+filename+"' in script");
	    		  }
	    		  else if (append_flag)
	    			  message=new String("output data was appended to file '"+
	    					  CPFileManager.CurrentDirectory.getPath()+
	    					  File.separator+filename+"'");
	    		  else 
	    			  message=new String("output data saved in file '"+
	    					  CPFileManager.CurrentDirectory.getPath()+
	    					  File.separator+filename+"'");
	    	  }
	    	  CirclePack.cpb.msg(message);
			  return ans;
		  }
		  
		  //    ========== match ==============
		  else if(cmd.startsWith("match")) {
	          items=(Vector<String>)flagSegs.get(0); // -q{p} v w V W
	          String str=(String)items.remove(0);
	          PackData qackData=null;
	          NodeLink pverts=null;
	          NodeLink qverts=null;
	          try {
	        	  qackData=CPBase.cpDrawing[StringUtil.qFlagParse(str)].getPackData();
	        	  if (qackData==null) 
	        		  throw new ParserException("'q' packing is not active");

	        	  // next two entries are verts from this packing
	        	  StringBuilder strb=new StringBuilder((String)items.get(0));
	        	  strb.append(" ");
	        	  strb.append((String)items.get(1));
	        	  pverts=new NodeLink(packData,strb.toString());
	        	  // next two from qackData
	        	  strb=new StringBuilder((String)items.get(2));
	        	  strb.append(" ");
	        	  strb.append((String)items.get(3));
	        	  qverts=new NodeLink(qackData,strb.toString());
	        	  if (pverts==null || qverts==null || 
	        			  pverts.size()<2 || qverts.size()<2)
	        		  throw new ParserException("node lists not big enough");
	          } catch(Exception ex) {
	        	  throw new ParserException("usage: match -q{p} v w V W");
	          }
	          if (packData.hes>0 || packData.hes!=qackData.hes) {
	        	  throw new ParserException("usage: both packings eucl or both hyperbolic");
	          }
	          Mobius M=new Mobius();
	          if (packData.match(qackData,(int)pverts.get(0),(int)pverts.get(1),
	        		  (int)qverts.get(0),(int)qverts.get(1),M)==0	|| 
	        		  Math.abs(M.error)>PackData.TOLER) {
	        	  throw new ParserException("some error in generating the "+
	        	  "Mobius transformation.");
	          }
	          NodeLink vertlist=new NodeLink(packData,"a");
	          packData.apply_Mobius(M,vertlist);
	          return 1;
	      } 

	      // ============ mark ===============
	      else if (cmd.startsWith("mark")) {
	    	  Iterator<Vector<String>> its=flagSegs.iterator();
	    	  while (its.hasNext()) {
	    		  items=(Vector<String>)its.next();
	    		  String str=(String)items.get(0);
	    		  if (!StringUtil.isFlag(str)) { // on first segment only; default to circles 
	    			  NodeLink vlist=new NodeLink(packData,items);
	    			  if (vlist==null || vlist.size()==0) return 0;
	    			  Iterator<Integer> vl=vlist.iterator();
	    			  while (vl.hasNext()) {
	    				  int v=(Integer)vl.next();
	    				  packData.setVertMark(v,1);
	    				  count++;
	    			  }
	    			  return count;
	    		  }
	    		  
	    		  // else, must have some flag
	    		  items.remove(0);
	    		  switch(str.charAt(1)) {
	    		      case 'w': // wipe out all marks, faces/circles/edges
	    			  {
	    				  for (int v=1;v<=packData.nodeCount;v++) 
	    					  packData.setVertMark(v,0);
	    				  for (int f=1;f<=packData.faceCount;f++) 
	    					  packData.setFaceMark(f,0);
    					  for (int e=1;e<=packData.packDCEL.edgeCount;e++)
    						  packData.packDCEL.edges[e].mark=0;
	    				  count++;
	    				  break;
	    			  }
	    			  case 'c': // circles
	    			  {
	    				  // mark by drawing order:
	    				  if (str.length()>2 && str.charAt(2)=='o') {
	    					  Iterator<HalfEdge> vis=
	    							  packData.packDCEL.layoutOrder.iterator();
	    					  HalfEdge he=vis.next(); // mark first face
	    					  int tick=1;
	    					  packData.setVertMark(he.origin.vertIndx,tick++);
	    					  he=he.next;
	    					  packData.setVertMark(he.origin.vertIndx,tick++);
	    					  he=he.next;
	    					  packData.setVertMark(he.origin.vertIndx,tick++);
	    					  while (vis.hasNext()) {
	    						  he=vis.next();
	    						  packData.setVertMark(he.next.next.origin.vertIndx,tick++);
	    					  }
	    					  count=tick;
	    					  break;
	    				  }
	    				  else if (str.length()>2 && str.charAt(2)=='w') { // wipe first
	        				  for (int v=1;v<=packData.nodeCount;v++) 
	        					  packData.setVertMark(v,0);
	        				  count++;
	    				  }
	    				  if (items.size()==0) 
	    					  break; // do not default to all here
	    				  NodeLink vlist=new NodeLink(packData,items);
	    				  if (vlist==null || vlist.size()==0) break;
	    				  Iterator<Integer> vs=vlist.iterator();
	    				  while (vs.hasNext()) {
	    					  packData.setVertMark((Integer)vs.next(),1);
	    					  count++;
	    				  }
	    				  break;
	    			  }
	    			  case 'e': // edges 
	    			  {
	    				  if (str.length()>2 && str.charAt(2)=='w') { // wipe first
	        				  for (int e=1;e<=packData.packDCEL.edgeCount;e++) {
	        					  packData.packDCEL.edges[e].mark=0;
	        					  count++;
	        				  }
	    				  }
	    				  if (items.size()==0) 
	    					  break; // do not default to all here

	    				  // mark selected
	    				  HalfLink hlink=new HalfLink(packData,items);
	    				  Iterator<HalfEdge> his=hlink.iterator();
	    				  while(his.hasNext()) {
	    					  HalfEdge he=his.next();
	    					  he.mark=1;
	    				  }
	    				  break;
	    			  }
	    			  case 'f': // faces
	    			  {
	    				  if (str.length()>2 && str.charAt(2)=='w') { // wipe out first
	        				  for (int f=1;f<=packData.faceCount;f++) 
	        					  packData.setFaceMark(f,0);
	        				  count++;
	    				  }
	    				  if (items.size()==0) 
	    					  break; // do not default to all here
	    				  FaceLink flist=new FaceLink(packData,items);
	    				  if (flist==null || flist.size()==0) 
	    					  break;
	    				  Iterator<Integer> fs=flist.iterator();
	    				  while (fs.hasNext()) {
	    					  packData.setFaceMark((Integer)fs.next(),1);
	    					  count++;
	    				  }
	    				  break;
	    			  }
	    			  case 'g': // mark gives generation of circles from 
	    				        // given vertlist (default to alpha)
	    			  {
	    				  int V;
	    				  try {
	    					  V=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    				  } catch(Exception ex) {
	    					  V=packData.getAlpha();
	    				  }
	    				  for (int v=1;v<=packData.nodeCount;v++) 
	    					  packData.setVertUtil(v,0);
	    				  packData.setVertUtil(V,1);
	    				  UtilPacket uP=new UtilPacket();
	    				  int []gens=packData.packDCEL.label_generations(-1,uP);
	    				  for (int v=1;v<=packData.nodeCount;v++) {
	    					  packData.setVertMark(v,gens[v]);
	    					  count++;
	    				  }
	    				  packData.setVertMark(V,1);
	    				  break;
	    			  }
	    		  } // end of switch
	    	  }
	    	  return count;
	      }
	      
	      // ============= max_pack ============
	      else if (cmd.startsWith("max_pa")) {
	    	  if (!packData.status || packData.nodeCount<=0) {
	    		  CirclePack.cpb.errMsg("bad packing data");
	    		  return 0;
	    	  }
	    	  int puncture_v=-1;
	    	  int cycles=CPBase.RIFFLE_COUNT;
	    	  // Note: there should be only one flag segment. Call forms:
	    	  //	max_pack [k], for k cycles
	    	  //	max_pack -r {v}, sphere only, puncture at v.
	    	  //    max_pack -r {v} [k], both
	    		 
	    	  if (flagSegs!=null && flagSegs.size()>0) {
	    	  try {
	    		  items=(Vector<String>)flagSegs.elementAt(0);
	    		  
	    		  // first entry a flag?
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  if (packData.hes>0 && items.elementAt(0).equals("-v")) {
		    			  puncture_v=NodeLink.grab_one_vert(packData,(String)items.get(1));
		    			  if (puncture_v<1 || puncture_v>packData.nodeCount) {
		    				  CirclePack.cpb.errMsg("improper puncture; ignored");
		    				  puncture_v=-1;
		    			  }
	    			  }
	    			  else if (items.elementAt(0).equals("-v"))
	    				  CirclePack.cpb.errMsg("'-v' flag ignored; "+
	    				  	"applies to spherical case only");
	    		  }
	    		  
	    		  if (items.size()==1 || items.size()==3) { // last entry, cycles
	    			  int k=Integer.parseInt(items.lastElement());
	    			  if (k<1) cycles=1;
	    			  else if (k>100000) cycles=100000;
	    			  else cycles=k;
	    		  }
	    	  } catch(Exception ex) {}
	    	  }

	    	  if ((packData.intrinsicGeom==0 && 
	    			  packData.packDCEL.idealFaceCount>0) || 
	    			  packData.intrinsicGeom < 0) { // hyperbolic case 
	    		  if (packData.hes >=0) {
	    			  packData.geom_to_h();
	    			  packData.setGeometry(-1);
	    		  }
	    		  packData.set_aim_default();
	    		  try { // e.g., there may be no boundary vertices
	    			  jexecute(packData,"set_rad 9.0 b");
	    		  } catch (Exception ex) { } 
	    		  
	    		  HypPacker h_packer=new HypPacker(packData,-1);
    			  count=h_packer.maxPack(cycles);
	    	  }
	    	  else if (packData.intrinsicGeom == 0) { // must be 1-torus 
	    		  if (packData.hes !=0) {
	    			  jexecute(packData,"geom_to_e");
	    		  }	
	    		  packData.set_aim_default(); // Orick's code, if available
	    		  EuclPacker e_packer=new EuclPacker(packData,-1);
	    		  count=e_packer.genericRePack(cycles);
	    		  if (count>0)
	    			  e_packer.reapResults();
				  packData.fillcurves();
				  packData.packDCEL.layoutPacking();
	    	  }
	    	  else if (packData.intrinsicGeom > 0) { // sph (NSpole also called)
	    		  packData.hes=1;
    			  packData.setGeometry(1);
	    		  packData.set_aim_default();
    			  SphPacker sphpack=new SphPacker(packData,puncture_v,cycles);
    			  count=sphpack.maxPack(cycles);
    			  sphpack.reapResults();
	    	  }
	    	  else 
	    		  return 0;
	    	  if (CirclePack.cpb!=null)
	    		  CirclePack.cpb.msg("max_pack: "+count+" repacking cycles");
	    	  return count;
	      }
		  
		  // ========= meld_edge =======
	      else if (cmd.startsWith("meld_ed")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just v w
	    	  HalfLink hlink=new HalfLink(packData,items);
	    	  Iterator<HalfEdge> his=hlink.iterator();
	    	  while (his.hasNext()) {
	    		  HalfEdge he=his.next();
	    		  if (RawManip.meldEdge_raw(packData.packDCEL,he)>0)
	    			  count++;
	    	  }
	    	  if (count>0) {
	    		  packData.packDCEL.fixDCEL(packData);
	    	  }
	    	  return count;
	      }
	      
	      // ========= migrate ======
	      else if (cmd.startsWith("migrate")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just v w
			  HalfLink hlist=new HalfLink(packData,items);
			  HalfEdge edge=hlist.get(0);
	    	  int rslt = RawManip.migrate(packData.packDCEL,edge);
	    	  if (rslt==0)
	    		  return 0;
	    	  packData.packDCEL.fixDCEL(packData);
	    	  return rslt;
	      }
		  
		  // ============ motion =============10/2024
		  else if (cmd.startsWith("moti")) {
			  // capture the initial centers/radii
			  ArrayList<ZRhold> bottom=Interpolator.loadZR(packData);
			  int N=75; //default
			  double delay=.004;
			  StringBuilder quoted;
			  
			  // quoted string is last string of last entry
			  int fn=flagSegs.size();
			  int in=flagSegs.get(fn-1).size();
			  quoted=new StringBuilder(
				  flagSegs.get(fn-1).remove(in-1).trim());
			  if (quoted.charAt(0)!='\"')
				  throw new ParserException("motion usage: missing commands");
			  
			  // are there any flags?
			  Iterator<Vector<String>> fit=flagSegs.iterator();
			  while (fit.hasNext()) {
				  items=fit.next();
				  if (items.size()==2)
				  try {
					  if (items.get(0).startsWith("-d")) {
						  double d=Double.parseDouble(items.remove(1));
						  delay=d;
					  }
					  else if (items.get(0).startsWith("-n")) {
						  int n=Integer.parseInt(items.remove(1));
						  if (n>1)
							  N=n;
					  }		
				  } catch(Exception ex) {
					  throw new ParserException(
						  "motion usage: motion -d {x} -n {k} cmds");
				  }
			  }

			  quoted.deleteCharAt(0);
			  int k=quoted.indexOf("\"");
			  quoted.deleteCharAt(k);
					  
			  String cmds=quoted.toString();

			  ResultPacket rP=new ResultPacket(CirclePack.cpb.
					  getActivePackData(),cmds);
			  CPBase.trafficCenter.parseCmdSeq(rP,0,null);
			  int ans=Integer.valueOf(rP.cmdCount);
			  if (ans==0) // if it failed
				  return 0;

			  // now interpolate: 'bottom' --> new packing
			  Interpolator inlator=new Interpolator(bottom,packData,N);
			  for (int jj=1;jj<=N;jj++) {
				  ArrayList<ZRhold> zrh=inlator.get(jj);
				  Interpolator.restoreZR(packData,zrh);
				  jexecute(packData,"disp");
				  try {
					  Thread.sleep((long) (delay * 1000.0));
				  } catch (InterruptedException ie) {}
				  count++;
			  }
			  jexecute(packData,"disp -wr");
			  return count;
		  }
		  break;
	  } // end of 'm' and 'M'
	  case 'n': // fall through
	  case 'N':
	  {
	      // =========== norm_scale ========
	      /* normalize eucl packing using one (only) of these options. 
	      a: scale specified eucl area
	      c: designated vert to prescribed radius
	      e: radius of v in p equals radius of w in q.
	      h: rotate so designated verts in horizontal line
	      i: scale/rotate to center designated vert at z=i.
	      m: apply trans. locating u,v at z1 z2
	      s: special for schwarzian, to normalize to half planes
	      t: torus: normalize fundamental domain, report tau.
	      u: designated vert on unit circle 
	      U: scale down (only) to fit packing in unit disc
	      Return 0 on error. */
	      if (cmd.startsWith("norm_scale")) {
	    	  if (packData.hes!=0) {
	    		  CirclePack.cpb.errMsg("'norm_scale' applies only to euclidean packings");
	    		  return 1; // but don't consider it an error
	    	  }
	    	  try {
	    		  items=(Vector<String>)flagSegs.get(0); // only allow one flag segment
	    		  String str=items.remove(0);
	    		  switch(str.charAt(1)) {

	    		  case 'a': // scale to given area. 
	    		  {
	    			  double x=Double.parseDouble((String)items.get(0));
	    			  if (x<PackData.OKERR) 
	    				  return 0;
	    			  double area=0.0;
	    			  for (int j=1;j<=packData.faceCount;j++) {
	    			      area += packData.faceArea(j);
	    			  }
	    			  Double factor=Double.valueOf(Math.sqrt(x/area));
	    			  if (!factor.isNaN())
	    				  return packData.eucl_scale(factor);
	    		  }
	    		  case 'c': // scale to give v the prescribed radius
	    		  {
	    			  int v=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    			  double rad=Double.parseDouble((String)items.get(1));
	    			  double factor=rad/packData.getRadius(v);
	    			  return packData.eucl_scale(factor);
	    		  }
	    		  case 'e': // scale so vertex v has same radius as vert w in pack q
	    			  // data in the form 'q v w'.
	    		  {
	    			  int q=Integer.parseInt((String)items.remove(0));
	    			  if (q<0 || q>=CPBase.NUM_PACKS 
	    					  || !CPBase.cpDrawing[q].getPackData().status) 
	    				  throw new ParserException("pack q not valid");
	    			  NodeLink vertlist=new NodeLink(packData,items);
	    			  int v=(Integer)vertlist.get(0);
	    			  double rad=packData.getRadius(v);
	    			  int w=(Integer)vertlist.get(1);
	    			  PackData qackData=CPBase.cpDrawing[q].getPackData();
	    			  if (w>qackData.nodeCount || rad<PackData.OKERR) 
	    				  throw new ParserException("problem with 'w'");
	    			  double factor=qackData.getRadius(w)/rad;
	    			  return packData.eucl_scale(factor);
	    		  }	  
	    		  case 'h': // v --> w horizontal, left to right
	    		  {
	    			  NodeLink vertlist=new NodeLink(packData,items);
	    			  int v=vertlist.get(0);
	    			  int w=vertlist.get(1);
	    			  Complex z=packData.getCenter(w).minus(packData.getCenter(v));
	    			  double ang=(-1.0)*(MathComplex.Arg(z));
	    			  return (packData.rotate(ang));
	    		  }
	    		  case 'i': // center v at z=i (if v is not too close to origin)
	    		  {
	    			  int v=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    			  Complex z=packData.getCenter(v);
	    			  double x=z.abs();
	    			  if (x<PackData.OKERR) 
	    				  return 1; // don't need to adjust
	    			  double factor=1.0/x;
	    			  double ang=(-1.0)*(MathComplex.Arg(z))+Math.PI/2.0;
	    			  packData.rotate(ang);
	    			  return (packData.eucl_scale(factor));
	    		  }
	    		  case 'u': // designated vert on unit circle 
	    		  {
	    			  int v=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    			  double ctr=packData.getCenter(v).abs();
	    			  // if already good, don't bother, close enough
	    			  if (Math.abs(ctr-1.0) < PackData.OKERR) 
	    				  return 1;
	    			  // if ctr is to close to zero, abort
	    			  if (ctr<.001)
	    				  return 0;
	    			  double factor=1.0/ctr;
	    			  return packData.eucl_scale(factor);
	    		  }
	    		  case 'U': // scale (down, only) to fit in unit disc, 
	    		  {
	    			  if (packData.hes!=0) 
	    				  return 0;
	    			  double max=0;
	    			  NodeLink blist=new NodeLink(packData,"b");
	    			  Iterator<Integer> bl=blist.iterator();
	    			  while (bl.hasNext()) {
	    				  int v=bl.next();
	    				  double dist=packData.getCenter(v).abs()+packData.getRadius(v);
	    				  max=(dist>max) ? dist:max;
	    			  }
	    			  // scale down
	    			  if (max>.999) {
	    				  return jexecute(packData,"scale "+1.0/max);
	    			  }
	    			  return 1; // don't scale, but don't fail (nice rhyme!)
	    		  }
	    		  case 'm': // [u v x1 y1 x2 y2] given, apply linear trans 
	    			  // to center u v at z1 z2.
	    		  {
	    			  int u=1;
	    			  int v=1;
	    			  Complex z1=null;
	    			  Complex z2=null;
	    			  try {
	    				  u=Integer.parseInt((String)items.get(0));
	    				  v=Integer.parseInt((String)items.get(1));
	    				  z1=new Complex(Double.parseDouble((String)items.get(2)),
	    						  Double.parseDouble((String)items.get(3)));
	    				  z2=new Complex(Double.parseDouble((String)items.get(4)),
	    						  Double.parseDouble((String)items.get(5)));
	    			  } catch(Exception ex) {
	    				  throw new ParserException("usage: "+
	    						  "norm_scale -t u v xu yu xv yv");
	    			  }
	    			  Mobius mymob=null;
	    			  try {
	    				  mymob=Mobius.mob_abAB(packData.getCenter(u),
	    						  packData.getCenter(v),z1,z2);
	    			  } catch (Exception ex) {
	    				  throw new DataException("failed to create Mobius: "+
	    						  ex.getMessage());
	    			  }
	    			  
	    			  // apply this mobius to the packing
	    			  return (packData.apply_Mobius(mymob,
	    					  new NodeLink(packData,"a")));
	    		  }
	    		  case 's': // normalize a la schwarzian flowers
	    		  {
	    			  if (packData.hes!=0)
	    		    	  CommandStrParser.jexecute(packData,"geom_to_e");
	    	    	  CommandStrParser.jexecute(packData,"norm_scale -c M 1.0");
	    	    	  double r=packData.packDCEL.vertices[packData.getGamma()].rad;
	    	    	  double a=2*r/(1.0+r);
	    	    	  Mobius smob=new Mobius(new Complex(0.0,-a),new Complex(a,0.0),
	    	    			  new Complex(1.0),new Complex(0.0,-1.0));
	    	    	  
	    			  // apply this mobius to the packing
	    	    	  packData.apply_Mobius(smob,new NodeLink(packData,"a"));

	    	    	  // put next petal at origin, set its radius to 1
	    	    	  int nextpetal=packData.packDCEL.vertices[packData.getGamma()].halfedge.twin.origin.vertIndx;
	    	    	  double x=packData.packDCEL.vertices[nextpetal].center.x;
	    	    	  r=packData.packDCEL.vertices[nextpetal].rad;
	    	    	  smob=new Mobius(new Complex(1.0),new Complex(-x,0.0),
	    	    			  new Complex(0.0),new Complex(1.0,0.0));
	    			  int ans=packData.apply_Mobius(smob,
	    					  new NodeLink(packData,"a"));
	    			  return ans;
	    		  }
	    		  case 't': // normalize a torus and report tau
	    		  {
	    			  TorusData torusData;
	    			  try {
	    				  torusData=new TorusData(packData);
	    			  } catch (Exception ex) {
    					  throw new ParserException("usage: "
    							  +"norm_scale -t only applies to"
    							  + "tori");
	    			  }
    				  
    				  // report 'tau' and its reciprocal 
	    			  CirclePack.cpb.msg("torus_tau: modulus = "+torusData.tau);
	    			  Complex taurecip=torusData.tau.reciprocal(); 
	    			  CirclePack.cpb.msg("   (and 1/modulus = "+taurecip+")");
    				  return 1;
    			  }
	    		  } // end of switch
	    	  } catch (Exception ex) {
	    		  throw new ParserException("No flags found");
	    	  }
	    	  return 0;
	      }

	      // ===========  NSPole ==========
	      else if (cmd.startsWith("NSpole") || cmd.startsWith(("NSPole"))) {
				if (packData.hes <= 0) {
					CirclePack.cpb.errMsg("NSpole: pack must be spherical");
					return 1;
				}
				NSpole nsPoler=new NSpole(packData);  // routines are here
				int rslt=nsPoler.parseNSpole(flagSegs);
				return rslt; 
	      }
	      
	      // =========== newRed ==========
	      else if (cmd.startsWith("newRe")) {
	    	  if (flagSegs==null || flagSegs.size()==0)
	    		  return 0;
	    	  items=flagSegs.get(0); // DCELdebug.printRedChain(packData.packDCEL.recChain);
	    	  RedEdge newRed=null;
	    	  
	    	  // torus: '-t' flag only
	    	  if (items.get(0).startsWith("-t") &&
	    			  packData.genus==1 && packData.getBdryCompCount()==0) {
				newRed=CombDCEL.torus4Sides(packData.packDCEL);
	    	  }
	    	  
	    	  else { // possibly hex extended link
		    	  NodeLink vlink=new NodeLink(packData,items);
		    	  newRed=RawManip.vlink2red(packData.packDCEL,vlink);
	    	  }
	    	  
	    	  if (newRed==null) { 
	    		  CirclePack.cpb.errMsg("failed to get a new red chain");
	    		  return 0;
	    	  }
	    		  
    		  // clear out old red info, then install 'newRed'
    		  RawManip.wipeRedChain(packData.packDCEL,packData.packDCEL.redChain);
    		  packData.packDCEL.redChain=newRed;

    		  // zero out 'eutil'; can set negative for edges to prevent
    		  //   them from being red-twinned.
    		  for (int e=1;e<=packData.packDCEL.edgeCount;e++)
    			  packData.packDCEL.edges[e].eutil=0;
				
	    	  CombDCEL.finishRedChain(packData.packDCEL,packData.packDCEL.redChain);
	    	  packData.packDCEL.fixDCEL(packData);
	    	  return 1;
	      }
	      break;
	  } // end of 'n' and 'N'
	  case 'o':
	  {
	      // ========= output ===========
	      if (cmd.startsWith("output")) {
	    	  String filename=null;
	    	  String message=null;
	    	  BufferedWriter fp=null;
    		  boolean script_flag=false;
    		  boolean append_flag=false;
	    	  
	    	  // Is there a filename??
	    	  StringBuilder theName=new StringBuilder();
	    	  int code=CPFileManager.trailingFile(flagSegs,theName);
	    	  if (code!=0) { // yes
	    		  File file=new File(theName.toString());
	    		  if ((code & 04)==04 && CPBase.scriptManager.isScriptLoaded())
	    			  script_flag=true;
	    		  if ((code & 02)==02) 
	    			  append_flag=true;
	    		  filename=file.getName();
	    		  String dir=file.getParent();
	    		  if (dir==null || dir.length()==0)
	    			  dir=CPFileManager.CurrentDirectory.toString();
	    		  fp=CPFileManager.openWriteFP(new File(dir),append_flag,filename,script_flag);
	    	  }
	    	  if (fp==null) {
	    		  throw new InOutException("usage: output ... <filename>");
	    	  }
	    	  
	    	  // now process the output description
	    	  String fullstr=StringUtil.reconstitute(flagSegs);
	    	  String []part=new String[4];
	    	  part=fullstr.toString().split("::");
	    	  try {
	    		  for (int i=0;i<4;i++) part[i]=part[i].trim();
	    	  } catch (Exception ex) {
	    		  throw new ParserException(
	    				  "structure: 'prefix :: data :: loop :: suffix'");
	    	  }

	    	  // write to and close the file, post message
	    	  int rtncnt=OutPanel.outputter(fp,packData,part[0],
	    			  part[1],part[2],part[3]);
	    	  if (rtncnt>0) {
	    		  // for "message" window
	    		  if (script_flag) {
	     			  CPBase.scriptManager.includeNewFile(filename);
	    			  message=new String("output data "+
	    					  "appended as file '"+filename+"' in script");
	    		  }
	    		  else if (append_flag)
	    			  message=new String("output data was appended to file '"+
	    					  CPFileManager.CurrentDirectory.
	    					  getPath()+File.separator+filename+"'");
	    		  else 
	    			  message=new String("output data saved in file '"+
	    					  CPFileManager.CurrentDirectory.
	    					  getPath()+File.separator+filename+"'");
	    	  }
	    	  CirclePack.cpb.msg(message);
	    	  return rtncnt;
	      }		  
		  break;
	  } // end of 'o'
	  case 'p':
	  {
	      // ============ pair_mob ===============
	      if (cmd.startsWith("pair_mob")) {
    		  if (packData.packDCEL.pairLink==null || 
    				  packData.packDCEL.pairLink.size()<2) {
	    		  CirclePack.cpb.errMsg("Packing has no side-pairings");
	    		  return 0;
    		  }
	    	  
	          items=(Vector<String>)flagSegs.get(0); // one segment
	          String str=(String)items.get(0);
	          if (StringUtil.isFlag(str)) { // -s, currently the only flag
	        	  if (!str.equals("-s")) {
	        		  throw new ParserException("usage: pair_mob -s {label}");
	        	  }
	        	  items.remove(0); // toss it, keep rest
	          }

	          if (items.size()==0)
	        	  throw new ParserException("usage: pair_mob {label}");

	          Mobius mb=packData.namedSidePair(items.get(0).trim());
	          if (mb==null)
	        	  throw new ParserException("usage: 'label' not "+
	        			  "matched with pair_mob");
			  return packData.apply_Mobius(mb,
					  new NodeLink(packData,"a"),true,false);
	      }
	      
	      // =========== perp_pack ==========
	      else if (cmd.startsWith("perp")) {
	    	  
	    	  if (!packData.status || packData.euler!=1 || packData.genus!=0) {
	    		  CirclePack.cpb.errMsg("usage: perp_pack only "+
	    				  "applies in topological disc case.");
	    		  return 0;
	    	  }
	    	  CommandStrParser.jexecute(packData,"geom_to_e");
	      	  int cycles=CPBase.RIFFLE_COUNT;
	    	  if (flagSegs!=null && flagSegs.size()>0 && (items=flagSegs.get(0))!=null) {
    			  try {
    				  int k=Integer.parseInt(items.elementAt(0));
    				  if (k<1) 
    					  cycles=1;
    				  else if (k>100000) 
    					  cycles=100000; // 100,000 limit
    				  else 
    					  cycles=k;
    			  } catch(Exception ex) {
    				  cycles=5000;
    			  }
	    	  }
	    	  
	    	  // create copy and double across bdry
	    	  PackData holdPack=packData.copyPackTo();
	    	  PackDCEL newpdcel=CombDCEL.doubleDCEL(holdPack.packDCEL,null,false);
    		  int antip=newpdcel.oldNew.findW(packData.getAlpha());
	    	  newpdcel.fixDCEL(holdPack);
	    	  CommandStrParser.jexecute(holdPack,"geom_to_s");

	    	  // max_pack this topological sphere; this normalizes also
	    	  int ans=0;
	    	  if ((ans+=CommandStrParser.jexecute(holdPack,"max_pack -v "+antip+" "+cycles))==0) {
	    		  CirclePack.cpb.errMsg("hum.. ran into packing problem with 'perp_pack'");
	    		  return 0;
	    	  }
	    	  
	    	  // project the copy to the plane
	    	  CommandStrParser.jexecute(holdPack,"geom_to_e");
	    	  
	    	  // now copy center/rad into 'packData'
	    	  for (int v=1;v<=packData.nodeCount;v++) {
	    		  packData.setRadiusActual(v,holdPack.getActualRadius(v));
	    		  packData.setCenter(v,holdPack.getCenter(v));
	    	  }
	    	  
	    	  return packData.nodeCount;
	      }

	      // =========== prune ============
	      else if (cmd.startsWith("prun")) {
	    	  int rslt=CombDCEL.pruneDCEL(packData.packDCEL);
	    	  if (rslt>0) {
	    		  packData.packDCEL.fixDCEL(packData);
	    		  
	    		  // store new-to-old indices in 'vertexMap'.
	    		  EdgeLink newold=(EdgeLink)packData.packDCEL.oldNew;
	    		  newold.flipEdgeEntries();
	    		  packData.vertexMap=(VertexMap)newold;
	    		  
	    		  return rslt;
	    	  }
	    	  return 1;
	      }

	      // ============ puncture ==========
	      else if (cmd.startsWith("puncture")) {
	    	  	boolean cutVert=true;
	    	  	int pf=-1;
	    	  	int pv=-1;
	    	  	
	    	  	// default to puncture maximal vertex index
	    	  	if (flagSegs==null || flagSegs.size()==0) { 
	    	  		pv=packData.nodeCount;
	  				if (packData.puncture_vert(pv)==0) 
	  					return 0;
	  				packData.xyzpoint=null; // ditch any xyz data
	    	  		return 1;
	    	  	}
	  			
	    	  	// else look for flags or index
	    	  	items=(Vector<String>)flagSegs.get(0); // one segment
	    	  	String str=(String)items.get(0);
	    	  	if (StringUtil.isFlag(str)) { 
	    	  		items.remove(0); // dump this string
	    	  		char c=str.charAt(1);
	    	  		switch(c) {
	    	  		case 'f': // face
	    	  		{
	    	  			cutVert=false;
	    	  			break;
	    	  		}
	    	  		default:
	    	  		{
	    	  			cutVert=true;
	    	  		}
	    	  		} // end of switch
	    	  	}
	    	  	try {
	    	  		if (cutVert) { // puncture a vertex
	    	  			pv=NodeLink.grab_one_vert(packData,flagSegs); 
	    	  			if (pv<=0)
	    	  				pv=packData.nodeCount; // default to max index
	    	  			count +=pv;
	    	  			if (packData.puncture_vert(pv)==0) 
	    	  				return 0;
	    	  			packData.xyzpoint=null;
	    	  		}
	    	  		else { // puncture a face
	    	  			pf=FaceLink.grab_one_face(packData,flagSegs);
	    	  			count+=pf;
	    	  			if (packData.puncture_face(pf)==0) 
	    	  				return 0;
	    	  		}
	    	  	} catch (Exception ex) {
	    	  		CirclePack.cpb.errMsg("attemp to puncture at "+pv+" went wrong.");
	    	  		return 0;
	    	  	}
	    	  	return count;
	      }
	      
	      // ========== proj =============
	      else if (cmd.startsWith("proj")) {
	/*    	  Options (in order of precedence): 
	    		   -e v   gives vert to place at 1 on equator.
	    		   -m     compute a vertex furthest in generations from alpha 
	    		   		  and S and place it at 1 on equator.
	    		   -t x   dilation amount; sets ratio of spherical radii, alpha/S.
	    		          default is "-t 1" (equal radii at N and S). */   	  
	    	  if (packData.hes>=0 || packData.getBdryCompCount()!=1) {
	    		  throw new ParserException("packing must be hyperbolic");
	    	  }

	    	  // find vertex to be put at 1 on equator
	    	  double ratio=1.0;
	    	  int E=0;
	      	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  switch(items.get(0).charAt(1)) {
	    			  
	    			  // Here's the specific parsing of flag itself
	    			  case 'e': // fall through to 'E' 
	    			  case 'E':
	    			  {
	    				  E=NodeLink.grab_one_vert(packData,(String)items.get(1));
	    				  if (E<1 || E==packData.getAlpha() || packData.isBdry(E))
	    					  E=0;
	    				  if (E==0) {
	    					  throw new CombException("invalid vertex");
	    				  }
	    				  ratio=0.0;
	    				  break;
	    			  }
	    			  case 'm': // combinatorial midpoint between alpha and bdry
	    			  {
	    				  if (E<3) { // 'E flag takes precedence
	    		    			for (int j=1;j<=packData.nodeCount;j++){
	    		      			  if (packData.isBdry(j)) 
	    		      				  packData.setVertUtil(j,1);
	    		      			  else 
	    		      				  packData.setVertUtil(j,0);
	    		      			}
	    		      			packData.setVertUtil(packData.getAlpha(),1);
	    		      			UtilPacket uP=new UtilPacket();
//	    		      			int []list=packData.label_generations(-1,uP);
	    		      			if ((E=uP.rtnFlag)>0 && 
	    		      					E!=packData.getAlpha() &&
	    		      					!packData.isBdry(E)) { // okay choice
	    		      				ratio=0.0;
	    		      			}
	    		      			else {
	    	    					  throw new ParserException("-m option failed");
	    		      			}
	    				  }
	    				  break;
	    			  }
	    			  case 't':  // arrange right ratio of radii 
	    			  {
	    				  if (E!=0) { // earlier choices take precedence
	    					  try {
	    						  ratio=Double.parseDouble((String)items.get(1));
	    					  } catch(Exception ex) {}
	    					  if (ratio<=0.0 || ratio>1.0) ratio=1.0;
	    				  }
	    				  break;
	    			  }
	    			  } // end of flag switch
	    		  } // done handling a given flagged segment
	    	  } // end of while
	    	  
	    	  return packData.proj_max_to_s(E,ratio);
	      }
	      
	      // ========= path_construct ======
	      else if (cmd.startsWith("path_c")) {
	    	  items=(Vector<String>)flagSegs.get(0); // only one segment
	    	  int mode=0; // default is -m
	    	  String str=(String)items.get(0);
	    	  if (StringUtil.isFlag(str)) {
	    		  items.remove(0);
	    		  switch(str.charAt(1)) {
	    		  case 'm': // default: build edge path 
	    		  {
	    			  mode=0;
	    			  break;
	    		  }
	    		  case 'i': // incremental turns mode
	    		  {
	    			  mode=1;
	    			  break;
	    		  }
	    		  default:
	    		  {
	    			  mode=0;
	    		  }
	    		  }  // end of switch
	    	  }
	    	  
	    	  NodeLink vertlist=new NodeLink(packData,items);
	    	  // pick off v1
	    	  int v1=vertlist.remove(0);
	    	  if (v1<1 || v1>packData.nodeCount || vertlist==null
	    			  || vertlist.size()==0) 
	    		  throw new ParserException();
	    	  NodeLink new_verts=packData.path_construct(mode,v1,vertlist);
	    	  if (new_verts==null || new_verts.size()==0) return 0;
	    	  if (CPBase.Vlink==null) {
	    		  CPBase.Vlink=new NodeLink((PackData)null); // create it
	    		  new_verts.add(0,v1); // put v1 in first spot
	    	  }
	    	  else { // Add v1 before new_verts if not already there
	    		  int last=(Integer)CPBase.Vlink.get(CPBase.Vlink.size()-1);
	    		  if (last!=v1) new_verts.add(0,v1);
	    	  }
	    	  CPBase.Vlink.addAll(new_verts);
	    	  CirclePack.cpb.msg("path_construct: added "+new_verts.size()+
	    			  "vertices to 'Vlist'");
	    	  return new_verts.size();
	      }
	      
	      // ========= post =======
	      else if (cmd.startsWith("post")) {
			  // No flag strings? Use default filename, 'postOptions', 
	    	  //     and popup.
			  if (flagSegs==null || flagSegs.size()==0) {
				  // if postManager is open, then close 'fp' and execute via PSPanel:
				  //  TODO: caution, this uses data from PSPanel, hence from 
				  //    active pack
				  if (CPBase.postManager.isOpen()) {
					  jexecute(packData,PackControl.
							  outputFrame.postPanel.createSuffix());
				  }
				  String cmdbuild=new String(PackControl.outputFrame.postPanel.
							  createPrefix(new String("sel_post_"+packData.packNum))+
							  PackControl.outputFrame.postPanel.formPostFlags()+
							  PackControl.outputFrame.postPanel.createSuffix());
				  jexecute(packData,cmdbuild);
				  return 1;
			  }
			  
			  // Pick off 'o', 'x', create new flag segments with others
			  Vector<Vector<String>> otherSegs=new Vector<Vector<String>>(2);
			  try {
				  int n=flagSegs.size();
				  int i=0;
				  
				  // '-o' flags can only come first
				  items=(Vector<String>)flagSegs.get(0);
				  String sub_cmd=(String)items.get(0);
				  if (sub_cmd.startsWith("-o")) {
					  i=1;
					  items.remove(0);
					  int mode=1;
					  String nmstr=null;
					  String insstr=null;
					  if (items.size()>0) { // must be: <name> and/or <quoted "string">
						  // have to find, remove a quoted string, if it exists
						  //   (problem is, {name} may have whitespace)
						  String recon=StringUtil.reconItem(items);
						  int i1=recon.indexOf('"');
						  int i2=-1;
						  if (i1>=0) {
							  i2=recon.indexOf('"',i1+1);
							  // various errors: no characters between, too many quotes, etc
							  if (i1>recon.length()-4 || i2==i1+1 
									  || i2<0 || recon.indexOf('"',i2+1)>=0) { 
								  recon=recon.substring(0,i1); // toss all after first "
								  i1=-1;
								  i2=-1;
							  }
							  else
								  insstr=recon.substring(i1+1,i2);
						  }
						  // name should be string before or after quoted string
						  if (i1==0) // name must be after quoted
							  recon=recon.substring(i2+1,recon.length()-1).trim();
						  else if (i1>0) // name must be before quoted
							  recon=recon.substring(0,i1).trim();
						  nmstr=new String(recon);
					  }
					  if (sub_cmd.startsWith("-oa")) { // append
						  mode=3;
					  }
					  else if (sub_cmd.startsWith("-oi")) { // insert
						  mode=2;
					  }
					  try {
						  CPBase.postManager.open_psfile(packData.cpDrawing,mode,nmstr,insstr);
						  count++;
					  } catch(InOutException iox) {
						  throw new InOutException("opening failed: "+iox.getMessage());
					  }
				  }
				  
				  // now process the rest
				  for (int j=i;j<n;j++) {
					  if (!CPBase.postManager.isOpen()) {
						  throw new InOutException("Postscript file not open.");
					  }
					  items=(Vector<String>)flagSegs.get(j);
					  sub_cmd=(String)items.get(0);

					  if (sub_cmd.startsWith("-o"))
						  throw new ParserException("post: usage: '-o' flag only first");		  
					  // '-x' end the processing
					  if (sub_cmd.startsWith("-x")) {
						  
						  // process any intervening flag strings
						  if (otherSegs!=null && otherSegs.size()>0) {
							  count += PostParser.postParse(pF,packData,otherSegs);
						  }
						  
						  String hold=CPBase.postManager.psUltimateFile.getCanonicalPath();
						  if (CPBase.postManager.close_psfile(packData.cpDrawing)>0) { 
							  CirclePack.cpb.msg("post: saved PostScript in "+hold);
						  }
						  else 
							  CirclePack.cpb.msg("post: there seems to be no file "+hold);
						  if (sub_cmd!=null && sub_cmd.length()>2) {
							  char d=sub_cmd.charAt(2);
							  switch(d){
							  case 'l': // send to printer
							  {
								  throw new InOutException("'print' postscript "+
										  "option not yet implemented");
								  // TODO:
							  }
							  case 'j': 
							  {
								  throw new InOutException("'jpg' posting option "+
										  "not yet implemented");
								  // TODO:
							  }
							  case 'g':
							  {
								  // TODO: don't know how to call, and how to avoid looping
								  break;
								  // try executing system call
/*							  Runtime rt=Runtime.getRuntime();
							  try {
								  String gvcmd=new String(PackControl.preferences.getGvCmd()+
								  " "+hold);
								  String params[]={"ping","127.0.0.1"};
								  Process proc=rt.exec(params);
//								  Process proc=rt.exec(gvcmd);
								  BufferedReader reader=new BufferedReader(
								  new InputStreamReader(proc.getInputStream()));
								  String lineRead=null;
								  while ((lineRead=reader.readLine())!=null) {
									  System.out.println(lineRead);
								  }
								  int exitVal=proc.waitFor();
								  if (exitVal!=0) {
									  CirclePack.cpb.myErrorMsg("Ghostview popup, 
									  command '"+gvcmd+"', has failed.");
								  }
							  } catch (Exception ex) {
								  CirclePack.cpb.myErrorMsgError("Ghostview 'popup' failed");
								  break;
							  }
							  break;*/
							  }
							  } // end of switch
						  }
						  return count+1;
					  } // done with '-x'
					  
					  otherSegs.add(items);
				  } // end of for

				  count += PostParser.postParse(pF,packData,otherSegs);
				  return count;
			  } catch (Exception ex) {
				  throw new ParserException("post parsing error: "+ex.getMessage());
			  }
			  
	      } // end of 'post'
	  
		  break;
	  } // end of 'p'
	  case 'q':
	  {
	      // ========= qc_dil ======
	      if (cmd.startsWith("qc_dil")) {
	    	  items=(Vector<String>)flagSegs.get(0); // one segment
	    	  String str=items.remove(0);
	    	  PackData q=null;
	    	  // must start with other packing number
	    	  try {
	    		  if ((q=CPBase.cpDrawing[StringUtil.
	    		                          qFlagParse(str)].getPackData())==packData) {
	    			  throw new ParserException();
	    		  }
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: qc_dil -q{q} {f..}");
	    	  }
		      if (packData.nodeCount != q.nodeCount || packData.hes!=0 || q.hes!=0) {
		    	  throw new ParserException("comparing p and q "+
		    			  "requires they be eucl and equal sized");
		      }
		      FaceLink facelist=new FaceLink(packData,items);
		      double maxdil=1.0;
		      double dil=1.0;
		      int f;
		      Iterator<Integer> flist=facelist.iterator();
		      while (flist.hasNext()) {
		    	  f=(Integer)flist.next();
		    	  dil=packData.face_dilatation(q,f);
		    	  maxdil=(dil>maxdil) ? dil : maxdil;
		    	  count++;
		      }
		      CirclePack.cpb.msg("Maximal dilatation, p"+
		    		  packData.packNum+" q"+q.packNum+" is "+maxdil);
		      return count;
	      }		
	      
	      // =========== quality ===================
	      if (cmd.startsWith("qual")) {
	    	  
	    	  // TODO: functionality should be extended.
	    	  CallPacket cP=null;
	    	  try {
	    		  cP=CommandStrParser.valueExecute(packData,cmd,flagSegs);
	    	  } catch(Exception ex) {
	    		  cP=null;
	    	  }
    		  if (cP==null || cP.strValue.length()==0) {
	    		  CirclePack.cpb.errMsg("qual call failed");
	    		  return 0;
	    	  }
    		  
    		  char c=cP.strValue.charAt(0);
    		  switch(c) {
    		  case 'v':
    		  {
    			  if (cP.error) {
					return 0;
    			  }
    			  else {
					CirclePack.cpb.msg("quality: worst visual error = "+
							String.format("%.2e",cP.double_vec.get(0))+
							" on edge ("+cP.int_vec.get(0)+" "+cP.int_vec.get(1)+")");
					if (cP.strValue.length()>3) // there is a "too small" message
	    				  CirclePack.cpb.msg("(Some radii too small to rely on)");
    			  }
    			  return 1;
    		  }
    		  case 'a':
    		  {
    			  if (!cP.error)
    				  CirclePack.cpb.msg("The worst |angsum - aim| was "+cP.double_vec.get(0)+
    						  " at v = "+cP.int_vec.get(0));
    			  return 1;
    		  }
    		  case 'r':
    		  {
    			  if (cP.error)
    				  return 0;
    			  CirclePack.cpb.msg("quality: worst relative error = "+
    				  String.format("%.2e",cP.double_vec.get(0))+
    					  " on edge ("+cP.int_vec.get(0)+" "+cP.int_vec.get(1)+")");
    			  return 1;
    		  }
    		  case 'n':
    		  {
    			  if (!cP.error) {
    				  CirclePack.cpb.msg("quality: no radius or center NaN's");
    				  return 1;
    			  }
    			  if (cP.strValue.startsWith("nr"))
    				  CirclePack.cpb.msg("quality: v = "+
    						  cP.int_vec.get(0)+" has radius = NaN");
    			  else if (cP.strValue.startsWith("nc"))
    				  CirclePack.cpb.msg("quality: v = "+
    						  cP.int_vec.get(0)+" has center = NaN");
    			  else 
    				  return 0; // some error
    			  return 1;
    		  }
    		  case 'o':
    		  {
    			  if (!cP.error)
    				  CirclePack.cpb.msg("There are no face orientation errors");
    			  else
    				  CirclePack.cpb.msg("Some faces are no oriented "+
    						  "correctly, e.g.,"+cP.int_vec.get(0));
    			  return 1;
    		  }
    		  } // end of switch
	  		  return 0;
	      }
		  break;
	  } // end of 'q'
	  case 'r': // fall through
	  case 'R':
	  {
		  
		  // =========== renumber =========
		  if (cmd.startsWith("renum")) {
			  int rslt=CombDCEL.reNumber(packData.packDCEL); // packData.getFlower(1132);
			  if (rslt>0) {
				  CirclePack.cpb.msg("Renumbering seemed to work");
				  return rslt;
			  }

			  return 0;
		  }
		  
	      // =========== rotate ===========
		  else if (cmd.startsWith("rotate")) {
			  double x=0.0;
			  try {
				  x=StringUtil.getOneDouble(flagSegs);
			  } catch(Exception ex) {
				  CirclePack.cpb.errMsg("usage: rotate <x>, x a double");
				  return 0;
			  }
	    	  return packData.rotate(x*Math.PI);
	      }
	      
	      // ========== repack ============= 
	      else if (cmd.startsWith("repack")) {
	    	  if (packData.hes>0) {
	    		  CirclePack.cpb.msg("repack: no spherical algorithm yet "+
	    				  "exists; you can use 'max_pack'");
	    		  return 0;
	    	  }
	    	  
	    	  // flags controlling the calls
	    	  boolean oldReliable=false;
	    	  boolean use_C=true;

	    	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	      	  int cycles=CPBase.RIFFLE_COUNT;
	    	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  String str=items.remove(0);
	    			  char c=str.charAt(1);
	    			  switch(c) {
	    			  case 'v': // specified vertices only; must be last flag segment
	    			  {
	    				  NodeLink vertlist=new NodeLink(packData,items);
	    				  
	    				  Iterator<Integer> vlist=vertlist.iterator();
	    				  while(vlist.hasNext()) {
	    					  int v=(Integer)vlist.next();
	    					  if (packData.hes<0) 
	    						  count +=packData.h_riffle_vert(v);
	    					  else if (packData.hes==0)
	    						  count +=packData.e_riffle_vert(v);
	    					  // else packData.s_riffle_vert(v);
	    				  }
	    				  if (count<=0) {
	    					  CirclePack.cpb.msg("repack: no repacking was needed");
	    					  return 1;
	    				  }
	    				  return count;
	    			  }
	    			  case 'o': // use 'oldReliable' -- caution: slow
	    			  {
	    				  oldReliable=true;
	    				  // fix cycles (I think this is old meaning of 'cycles' so 
	    				  //     may be smaller than anticipated.)
		    			  try {
		    				  int k=Integer.parseInt(items.elementAt(0));
		    				  if (k<1) cycles=1;
		    				  else if (k>100000) cycles=100000; // 100,000 limit
		    				  else cycles=k;
		    			  } catch(Exception ex) { }
		    			  break;
		    		  }	
	    			  // TODO: need to implement other packing routines and put
	    			  //   in the options here, e.g., 't' (which should be default 
	    			  //   for inversive distance cases.)
	    			  } // end of switch
	    		  }
	    		  else { // should be 'cycles' indicator
	    			  try {
	    				  int k=Integer.parseInt(items.elementAt(0));
	    				  if (k<1) cycles=1;
	    				  else if (k>100000) cycles=100000; // 100,000 limit
	    				  else cycles=k;
	    			  } catch(Exception ex) { }
	    		  }
	    	  } // end of while
	    	  count=packData.repack_call(cycles,oldReliable,use_C);

			  if (count==0) {
				  // TODO: what about errors? do they give exceptions?
				  CirclePack.cpb.msg("repack: no repacking was needed");
				  return 1;
			  }
			  return count;
	   	  }

	      // ========== rm_? ===========
	      else if (cmd.startsWith("rm_")) {
	    	  cmd=cmd.substring(3);
    		  items=new Vector<String>();
	    	  if (flagSegs!=null && flagSegs.size()>0)
	    		  items=(Vector<String>)flagSegs.get(0); // just one seqment
	    	  
			  if (cmd.startsWith("bary")) { // "rm_bary": barycenters
				  
	    		  // default to all 3-degree interior
				  NodeLink vertlist=null;
				  if (items==null || items.size()==0)
	    			  vertlist=new NodeLink(packData,"{c:(i).and.(d.eq.3)}");
				  else
					  vertlist=new NodeLink(packData,items);
	    		  
	    		  if (vertlist==null || vertlist.size()==0) {
	    			  CirclePack.cpb.errMsg("'rm_bary': no vertices specified");
	    			  return count;
	    		  }
	    		  
	    		  // DCEL setting
    			  packData.packDCEL.oldNew=new VertexMap();
    			  Iterator<Integer> vis=vertlist.iterator();
    			  while(vis.hasNext()) {
    				  int v=vis.next();
    				  int newv=packData.packDCEL.oldNew.findW(v);
    				  if (newv==0)
    					  newv=v;
    				  int rslt=RawManip.rmBary_raw(packData.packDCEL,
    						  packData.packDCEL.vertices[newv]);
    				  if (rslt==0) {
    					  CirclePack.cpb.errMsg("'rm_bary' failed on vertex "+v);
    					  if (count>0)
    						  packData.packDCEL.fixDCEL(packData);
    					  return count;
    				  }
    				  count++;
    			  }
    			  packData.packDCEL.oldNew=null;
				  packData.packDCEL.fixDCEL(packData);
    			  return count++;
    		  }

			  else if (cmd.startsWith("cir")) { // "rm_circle"
	    		  NodeLink vertlist=new NodeLink(packData,items);
	    		  
	    		  // is this just removing one barycenter??
	    		  if (vertlist!=null && vertlist.size()==1) {
	    			  Vertex vert=packData.packDCEL.vertices[vertlist.get(0)];
	    			  if (!vert.isBdry() && vert.getNum()==3) {
	    				  return CommandStrParser.jexecute(packData,"rm_bary "+vert.vertIndx);
	    			  }
	    		  }
	    		  
    	    	  int origCount=packData.packDCEL.vertCount;
    	    	  HalfLink hlink=new HalfLink();
    	    	  Iterator<Integer> vis=vertlist.iterator();
    	    	  while (vis.hasNext()) {
    	    		  int v=vis.next();
    	    		  HalfLink hlk=packData.packDCEL.vertices[v].
    	    				  getOuterEdges();
    	    		  hlink.abutMore(hlk);
    	    	  }
    	    	  PackDCEL pdc=CombDCEL.extractDCEL(packData.packDCEL,
    	    			  hlink,null);
    	    	  pdc.fixDCEL(packData);
    	    	  packData.xyzpoint=null;
    	    	  int n=origCount-packData.packDCEL.vertCount;
    			  CirclePack.cpb.msg("rm_cir: removed "+n+" circles from p"+
    					  packData.packNum);
    	    	  if (n>0)
    	    		  return n;
    	    	  return 1;
	    	  }
			  else if (cmd.startsWith("quad")) { // "rm_quad"
				  
				  HalfLink hlink=new HalfLink(packData,items);
				
				  if (hlink==null || hlink.size()==0) {
					  CirclePack.cpb.errMsg("usage: rm_quad {u v ...} (give edges)");
					  return count;
				  }
					  
				  // repeat while succeeding: a vert 
				  //   may/maynot qualify after previous actions.
				  Iterator<HalfEdge> his=hlink.iterator();
					  
				  packData.packDCEL.oldNew=new VertexMap();
				  while (his.hasNext()) { 
					  // DCELdebug.printRedChain(packData.packDCEL.redChain);
					  HalfEdge edge=his.next();
					  int rslt=RawManip.rmQuadNode(packData.packDCEL,edge);
					  if (rslt==0) {
						  CirclePack.cpb.errMsg("rm_quad failed for edge "+edge);
						  if (count>0)
							  packData.packDCEL.fixDCEL(packData);
						  return count;
					  }
					  count++;
				  }
					  
				  // finish up
				  packData.packDCEL.fixDCEL(packData);
				  return count;
	    	  }
			  else if (cmd.startsWith("edge")) { // "rm_edge"
				  
		    	  // check for '-c' (consolidate) flag (only for "rm_edge")
		    	  boolean consolid=false;
				  String strg=items.get(0);
				  if (StringUtil.isFlag(strg)) {
					  if (!strg.equals("-c"))
						  throw new ParserException("illegal flag "+strg);
					  // collapsing int/bdry edges
					  items.remove(0); // shuck this entry
					  consolid=true;
				  }
				  
				  HalfLink hlink=new HalfLink(packData,items);
				  packData.packDCEL.oldNew=new VertexMap();
				  Iterator<HalfEdge> his=hlink.iterator();
				  while(his.hasNext()) {
					  HalfEdge edge=his.next();
					  int rslt=0;
					  if (consolid)
						  rslt=RawManip.meldEdge_raw(packData.packDCEL,edge);
					  else
						  rslt=RawManip.rmEdge_raw(packData.packDCEL,edge);
					  if (rslt==0) {
						  CirclePack.cpb.errMsg("rm_edge failed for edge "+edge);
						  if (count>0)
							  packData.packDCEL.fixDCEL(packData);
						  return count;
					  }
					  
					  // negative: reset default new red edge data using
					  //    current vData. (-rslt has just become a bdry 
					  //    vertex, so it comes back with red edge having
					  //    default data)
					  if (rslt<0) {
						  Vertex vert=packData.packDCEL.vertices[-rslt];
						  RedEdge redge=vert.halfedge.myRedEdge;
						  
						  // get the original index for this vertex
						  int origv=packData.packDCEL.oldNew.findV(-rslt);
						  if (origv==0)
							  origv=-rslt;
				  		  redge.setCenter(new Complex(packData.packDCEL.vertices[origv].center));
				  		  redge.setRadius(packData.packDCEL.vertices[origv].rad);
					  }
					  count++;
				  }
				  packData.packDCEL.fixDCEL(packData);
				  return count;
			  }
			  
			  // fix things up
			  packData.chooseAlpha();
			  packData.chooseGamma();
		      return count;
	      }
	      
	      // ========= reorient =======
	      else if (cmd.startsWith("reorie")) {
	    	  CombDCEL.reorient(packData.packDCEL);
	    	  packData.packDCEL.fixDCEL(packData);
	    	  return packData.packDCEL.vertCount;
	      }
		  break;
	  } // end of 'r' and 'R'
	  case 's':
	  {
		  
		  // ============== slider ===========
			if (cmd.startsWith("slide")) {
				if (flagSegs == null || flagSegs.size() == 0)
					return 0;

				// must be initial flag: -R, -S, -A
				int type = -1; // 0=radii, 1=edge schwarzian, 2=angle sum
				items = flagSegs.remove(0);
				if (!StringUtil.isFlag(items.get(0))) {
					CirclePack.cpb.errMsg("usage: 'slider' must start with -[RSA] flag");
					return 0;
				}

				char c = items.remove(0).charAt(1);
				switch (c) {
				case 'R': // start for radii
				{
					type = 0;
					break;
				}
				case 'S': // start for schwarzians
				{
					type = 1;
//					if (!packData.haveSchwarzians()) {
//						if (CommandStrParser.jexecute(packData,"set_sch")<=0)
//							throw new DataException("failed to compute schwarzians");
//					}
					break;
				}
				case 'A': // start for angle sum sliders
				{
					type=2;
					break;
				}
				default: {
					CirclePack.cpb.errMsg("usage: 'slider' must start with -R, -S, or -A flag");
					return 0;
				}
				} // end of switch
				
				SliderFrame generic=packData.radiiSliders;
				if (type==1) 
					generic=packData.schwarzSliders;
				else if (type==2)
					generic=packData.angSumSliders;
								
				// three situations:
				// * no other flags: create with specified objects (perhaps to default)
				// * -c, -m, or -o flags, reconstitute flagSegs and pass for creation
				// * nothing else in first item, but then flags such as -a or -r (add/remove)
				if (flagSegs.size() == 0) {
					return CreateSliderFrame.createSliderFrame(packData, type, items);
				}
				items = flagSegs.get(0);
				
				try {
					
					// not open? ONly the -c, -m, or -o flag means to create
					char fc=items.get(0).charAt(1);
					if ((generic==null || generic.packData.nodeCount!=packData.nodeCount) &&
							(fc=='c' || fc=='m' || fc=='o')) {
						StringBuilder strbld=new StringBuilder(StringUtil.reconstitute(flagSegs));
						return CreateSliderFrame.createSliderFrame(packData,type,strbld);
					}

					if (generic==null) { 
						throw new ParserException("expected sliderframe does not exist.");
					}
						
					int hits=0;
					Iterator<Vector<String>> fls=flagSegs.iterator();
					while (fls.hasNext()) {
						items=fls.next();
						fc = items.remove(0).charAt(1);
						switch (fc) {
						// set change command: quoted text
						case 'c': 
						{
							StringBuilder chgbld=new StringBuilder(StringUtil.reconItem(items));
							// get first quote-enclosed string
							int k=chgbld.indexOf("\"",0);
							if (k>=0 && k<chgbld.length()-1) {
								int n=chgbld.indexOf("\"",k+1);
								if (n>=0) {
									generic.changeCmdField.setText(chgbld.substring(k+1,n));
									generic.changeCheck.setSelected(true);
									hits++;
								}
							}
							break;
						}
						// set move command: quoted text
						case 'm': 
						{
							StringBuilder mvbld=new StringBuilder(StringUtil.reconItem(items));
							// get first quote-enclosed string
							int k=mvbld.indexOf("\"",0);
							if (k>=0 && k<mvbld.length()-1) {
								int n=mvbld.indexOf("\"",k+1);
								if (n>=0) {
									generic.optCmdField.setText(mvbld.substring(k+1,n));
									generic.motionCheck.setSelected(true);
									hits++;
								}
							}
							break;
						}
						// set optional command: quoted text
						case 'o': 
						{
							StringBuilder opbld=new StringBuilder(StringUtil.reconItem(items));
							// get first quote-enclosed string
							int k=opbld.indexOf("\"",0);
							if (k>=0 && k<opbld.length()-1) {
								int n=opbld.indexOf("\"",k+1);
								if (n>=0) {
									generic.optCmdField.setText(opbld.substring(k+1,n));
									hits++;
								}
							}
							break;
						}
						case 'a': // add object
						{
							items.remove(0);
							if (type == 0 && packData.radiiSliders != null) {
								hits +=packData.radiiSliders.addObject(
										StringUtil.reconItem(items));
							} else if (type == 1 && packData.schwarzSliders != null) {
								hits +=packData.schwarzSliders.addObject(
										StringUtil.reconItem(items));
							} else if (type == 2 && packData.angSumSliders != null) {
								hits +=packData.angSumSliders.addObject(
										StringUtil.reconItem(items));
							}

							break;
						}
						case 'r': // remove object
						{
							items.remove(0);
							if (type == 0 && packData.radiiSliders != null) {
								hits +=packData.radiiSliders.removeObject(
										StringUtil.reconItem(items));
							} else if (type == 1 && packData.schwarzSliders != null) {
								hits +=packData.schwarzSliders.removeObject(
										StringUtil.reconItem(items));
							} else if (type == 1 && packData.schwarzSliders != null) {
								hits +=packData.angSumSliders.removeObject(
										StringUtil.reconItem(items));
							}
							break;
						}
						case 'd': // download from packdata to sliders
						{
							generic.downloadData();
							hits++;
							break;
						}
						case 'l': // set lower (min) value for sliders
						{
							double min=0.0;
							try {
								min=Double.parseDouble(items.get(0));
							} catch(NumberFormatException nfx) {
								throw new DataException(nfx.getMessage());
							}
							generic.resetMin(min);
							hits++;
							break;
						}
						case 'u': // set lower (min) value for sliders
						{
							double max=0.0;
							try {
								max=Double.parseDouble(items.get(0));
							} catch(NumberFormatException nfx) {
								throw new ParserException(nfx.getMessage());
							}
							generic.resetMax(max);
							hits++;
							break;
						}
						case 'x': // destroy the slider and frame
						{
							if (type==0) {
								if (packData.radiiSliders!=null)
									packData.radiiSliders.dispose();
								packData.radiiSliders=null;
							}
							else if (type==1) {
								if (packData.schwarzSliders!=null)
									packData.schwarzSliders.dispose();
								packData.schwarzSliders=null;
							}
							else if (type==2) {
								if (packData.angSumSliders!=null)
									packData.angSumSliders.dispose();
								packData.angSumSliders=null;
							}
							return 1;
						}
						default:
						{
							throw new ParserException("usage: illegal "+
									"slider flag");
						}
						} // end of switch
					} // end of while though flag segments
					if (hits>0)	// got some flags
						return hits;
				} catch (Exception ex) {
					throw new ParserException("problem in slider call");
				}

			} // done with 'slider'
			
			// =============== sch_report =======
			if (cmd.startsWith("sch_repo")) {
				return Schwarzian.schwarzReport(packData,flagSegs);
			}

			// =============== sch_layout ========
			else if (cmd.startsWith("sch_lay")) {
				CirclePack.cpb.errMsg("OBE: 'sch_layout' has "+
						"been replaced by 'dual_layout'.");
				return 0;
			}
			

		  // =============== scale ==========
		  else if (cmd.startsWith("scale") && !cmd.startsWith("scale_")) {
			  
// debug  deBugging.LayoutBugs.log_RedList(packData,packData.redChain); 
//deBugging.LayoutBugs.log_RedCenters(packData);
// DCELdebug.rededgecenters(packData.packDCEL);			  
			  
			  double factor = 1.1; 
			  try {
				  factor=StringUtil.getOneDouble(flagSegs);
			  } catch (Exception ex) {}
		      if (factor<=0.0) 
		    	  return 0;
		      return packData.eucl_scale(factor);
		  }
		  
	      //  ============= scale_aims =============
		  else if (cmd.startsWith("scale_aim")) {
			  items=(Vector<String>)flagSegs.get(0); // just one segment
			  String str=(String)items.remove(0);
			  double factor=Double.parseDouble(str);
			  if (factor<0.0) {
				  throw new ParserException("given factor x is negative");
			  }
			  NodeLink vertlist=new NodeLink(packData,items);
			  return packData.scale_aims(factor,vertlist);
		  }
		      
	      // ============= scale_radii =============
		  else if (cmd.startsWith("scale_rad")) {
			  // just one segment: -q{} x {v..} 
			  items=(Vector<String>)flagSegs.get(0); 
			  String str=(String)items.remove(0);
			  int qnum=-1;
			  if (!StringUtil.isFlag(str) || items.size()==0) {
				  CirclePack.cpb.errMsg("usage: scale_radii -q{.} x {v..}");
				  return 0;
			  }
			  else 
				  qnum=StringUtil.qFlagParse(str);
			  if (qnum<0 || qnum>=CPBase.NUM_PACKS)
				  throw new ParserException("Failed to read '-q{}' flag");
			  str=(String)items.remove(0);
			  double factor=Double.parseDouble(str);
			  if (factor<0.0) {
				  throw new ParserException("given factor x is negative");
			  }
			  NodeLink vertlist=new NodeLink(packData,items);
			  return packData.scale_rad(CPBase.cpDrawing[qnum].getPackData(),factor,vertlist);
		  }
		    	      
	      // =========== sq_grid_overlaps ===========
		  else if (cmd.startsWith("sq_grid_ov")) {
	    	  return packData.sq_grid_overlaps();
	      }
	      
	      // =========== svg ========================
	      // TODO: this is very simple preliminary routine
		  else if (cmd.startsWith("svg")) {
//	      	  boolean append_flag=false; // no append option for now
	      	  boolean script_flag=false;
	      	  // Get and remove trailing filename as first step
	      	  StringBuilder strbld=new StringBuilder();
	      	  int fra=CPFileManager.trailingFile(flagSegs, strbld);
	      	  if (fra==0)
	      		  throw new ParserException("No filename given in 'svg' call");
	      	  String fname=strbld.toString();
     		  // now process
   	  		  File dir=CPFileManager.PackingDirectory;
   	   		  BufferedWriter fp=CPFileManager.openWriteFP(dir,false,fname,script_flag);
   	   		  int act;
   	   		  try {
   	   			  act=packData.writeSVG(fp,flagSegs); // 
   	   		  } catch(Exception ex) {
   	   			  throw new InOutException("writeSVG failed");
   	   		  }

   	   		  CirclePack.cpb.msg("Wrote SVG image to "+dir.getPath()+
   	   				  File.separator+fname);
   	   		  return act;
	      }
	      
	      // =============== swap =========  
		  else if (cmd.startsWith("swap")) { // swap vert indices
	    	  
	    	  // look for any flags first
	    	  int keepFlag=0;
	    	  Iterator<Vector<String>> fit=flagSegs.iterator();
	    	  while (fit.hasNext()) {
	    		  items=fit.next();
	    		  if (items.size()>0 && StringUtil.isFlag(items.get(0))) {
	    			  String itst=items.remove(0); // grab and remove the flag
	    			  if (itst.contains("c"))
	    				  keepFlag = keepFlag | 00001; // set for color
	    		  	  if (itst.contains("m"))
	    				  keepFlag = keepFlag | 00002; // set for mark
	    		  	  if (itst.contains("a"))
	    				  keepFlag = keepFlag | 00004; // set for aim
	    		  }
	    	  } // done looking for flags
	    	  
	    	  // now 'items' should have just v and w
		      try {
		    	  items=(Vector<String>)flagSegs.get(0);
		    	  NodeLink nL=new NodeLink(packData,items);
		    	  if (keepFlag!=0)
		    		  packData.swap_nodes(nL.get(0),nL.get(1),keepFlag);
		    	  else 
		    		  packData.swap_nodes(nL.get(0),nL.get(1));
		      } catch(Exception ex) {
		    	  throw new ParserException("usage: swap [-cma] v w");
		      }
		      return 1;
	      }
		  
	      // ============= square_fit =========
	      /** For euclidean packings only. Scale packing up or down so it just
	      fits in the square having corners (-1,-1), (1,1).
	      */
		  else if (cmd.startsWith("square_fit")) {
	        double maxdist=0.0,x=0.0,y=0.0;

	        if (packData.hes!=0) {
	          throw new ParserException("packing must be euclidean");
	        }
	        for (int i=1;i<=packData.nodeCount;i++)
	            if (packData.isBdry(i)) {
	      	  x=Math.abs(packData.getCenter(i).x)+packData.getRadius(i);
	      	  y=Math.abs(packData.getCenter(i).y)+packData.getRadius(i);
	      	  maxdist = (x>maxdist) ? x : maxdist;
	      	  maxdist = (y>maxdist) ? y : maxdist;
	            }
	        if (maxdist>0.0) packData.eucl_scale(1/maxdist);
	        return 1; 
	      }

		  // ========= set =========
		  else if (cmd.startsWith("set_")) {
	    	  cmd=cmd.substring(4);
	    	  	  
	    	  // ========= set_active ==========
	    	  if (cmd.startsWith("active")) { // set's active node
	    		  int v;
	    		  if ((v=NodeLink.grab_one_vert(packData,flagSegs))<0) return 1;
	    		  packData.activeNode=v;
	    		  return v;
	    	  }
	    	  
	    	  // ========= set_rand ============
	    	  if (cmd.startsWith("rand")) { // set random radii or inv distances
	    		  Iterator<Vector<String>> its=flagSegs.iterator();
	    		  boolean iDist=false;
	    		  boolean jiggle=false;
	    		  boolean factor_flag=false; // 'factor' or 'range' mode
	    		  double low=.005;
	    		  double high=5.0;
	    		  double pctg=1.0;
	    		  String str=null;
	    		  items=null;
	    		  
	    		  while (its.hasNext()) {
	    			  items=(Vector<String>)its.next();
	    			  str=(String)items.get(0);
	    			  if (StringUtil.isFlag(str)) {
	    				  items.remove(0);
	    				  switch(str.charAt(1)) {
	    				  case 'o': // overlaps
	    				  {
	    					  iDist=true;
	    					  break;
	    				  }
	    				  case 'j': // jiggle
	    				  {
	    					  jiggle=true;
	    					  factor_flag=true;
	    					  try {
	    						  pctg=Double.parseDouble((String)items.remove(0));
	    						  if (Math.abs(pctg)>10.0) pctg=10.0;
	    						  pctg = pctg/100.0;
	    					  } catch (Exception ex) {
	    						  low=.99;
	    						  high=1.01;
	    					  }
	    					  break;
	    				  }
	    				  case 'f': // factor
	    				  {
	    					  factor_flag=true;
	    					  break;
	    				  }
	    				  case 'r': // read range
	    				  {
	    					  try {
	    						  low=Double.parseDouble((String)items.remove(0));
	    						  high=Double.parseDouble((String)items.remove(0));
	    						  if (low>=high) throw new ParserException();
	    					  } catch (Exception ex) {
	    						  CirclePack.cpb.myErrorMsg("usage: set_random: -f {low} {high}");
	    					  }
	    					  break;
	    				  }
	    				  } // end of switch
	    			  } // done with flags
	    		  } // done with while
	    		  
	    		  // 'items' should now have {v..} or {e..}
				  Random randizer=new Random();
				  double factor=1.0;
	    		  if (iDist) { // adjust inversive distance
	    			  HalfLink hlink=new HalfLink(packData,items);
	    			  Iterator<HalfEdge> his=hlink.iterator();
	    			  while (his.hasNext()) {
	    				  HalfEdge he=his.next();
		    			  double value=1.0;
	    				  if (factor_flag) { // use a factor
	    					  if (jiggle) {
	    						  factor=Math.exp(randizer.nextGaussian()*pctg);
	    					  }
	    					  else 
	    						  factor=low+randizer.nextDouble()*(high/low);
	    					  value=he.getInvDist()*factor;
	    				  }
	    				  else 
	    					  value=low+randizer.nextDouble()*(high/low);
	    				  he.setInvDist(value);
	    				  count++;
	    			  }
	    		  }
	    		  else { // adjust radii
	    			  NodeLink vertlist=new NodeLink(packData,items);
	       			  Iterator<Integer> vlist=vertlist.iterator();
	       			  int v;
	    			  while (vlist.hasNext()) {
	    				  v=(Integer)vlist.next();
	    				  if (factor_flag) { // use a factor
	    					  if (jiggle) {
	    						  factor=Math.exp(
	    							randizer.nextGaussian()*pctg);
	    					  }
	    					  else 
	    						  factor=low+randizer.nextDouble()*(high-low);
	    					  packData.setRadiusActual(v,
	    						  packData.getActualRadius(v)*factor);
	    				  }
	    				  else packData.setRadiusActual(v,
	    						  low+randizer.nextDouble()*(high-low));
	    				  count++;
	    			  }
	    		  }
	    		  if (count>0) packData.fillcurves();
	    		  return count;
	    	  }
	    	  
	    	  // ========= set_schwarzians ==========
	    	  if (cmd.startsWith("sch")) { 
    			  HalfLink hlink=null; 
    			  boolean givenx=false; 
    			  double sch_value=0.0;

    			  if (flagSegs==null || flagSegs.size()==0 || 
    					  flagSegs.get(0).size()==0) {
    				  hlink=new HalfLink(packData,"a");
    			  }
    			  else {
    				  items=flagSegs.remove(0);
    				  if (StringUtil.isFlag(items.get(0))) {
    					  if (items.get(0).startsWith("-s")) {
    						  items.remove(0);
    						  try {
    		   					  sch_value=Double.parseDouble(items.get(0));
    		   					  givenx=true;
    		   					  items.remove(0);
    		   				  } catch (Exception ex) {
    		   					  throw new ParserException("usage: set_schw -[su] {x} {v w ..}");
    		   				  }
    					  }
    					  else if (items.get(0).startsWith("-u")) {
    						  items.remove(0);
    						  try {
    		   					  sch_value=1.0-Double.parseDouble(items.get(0));
    		   					  givenx=true;
    		   					  items.remove(0);
    		   				  } catch (Exception ex) {
    		   					  throw new ParserException("usage: set_schw -s {x} {v w ..}");
    		   				  }
    					  }
    					  else
    						  throw new ParserException("usage: set_schw -[su] ..");
    				  }
    				  if (items.size()==0)
        				  hlink=new HalfLink(packData,"a");
    				  else
        				  hlink=new HalfLink(packData,items);
    			  }
    			  
    			  if (givenx) {
    				  if (hlink==null || hlink.size()==0)
    					  return count;
    				  
    				  Iterator<HalfEdge> his=hlink.iterator();
    				  while (his.hasNext()) {
    					  try {
    						  HalfEdge edge=his.next();
    						  if (edge.isBdry()) {
    							  edge.setSchwarzian(0.0);
    							  edge.twin.setSchwarzian(0.0);
    						  }
    						  else {
    							  edge.setSchwarzian(sch_value);
    							  edge.twin.setSchwarzian(sch_value);
    						  }
							  count++;
    					  } catch (Exception ex) {
    						  throw new DataException("error in set_schwarz: "+
    								  "perhaps schwarzians not allocated?");
    					  }
    				  }
    				  return count;
    			  }
    			  
    			  // else set to current
   				  return Schwarzian.comp_schwarz(packData, hlink);
	    	  }

	    	  // ========= set_invdist  ========
	    	  if (cmd.startsWith("invdis")) {
	    		  
	    		  // if no segments, error
	    		  if (flagSegs==null || flagSegs.size()==0) { 
	    			  CirclePack.cpb.errMsg("usage: 'set_invdist' has no arguments");
	    			  return 0;
	    		  }

	    		  items=flagSegs.get(0);
	    		  String str=(String)items.remove(0);

	    		  // look for a file of xyz values; must start "-x <name>"
	    		  if (str.startsWith("-x")) {
	    			  StringBuilder strbld=new StringBuilder();
	    			  int rwflag=CPFileManager.trailingFile(flagSegs,strbld);
	    			  // error or no filename, use 'packData.xyzpoint'
	    			  if (rwflag==0 || (rwflag & 01)== 01) 
	    				  return packData.set_xyz_overlaps(packData.xyzpoint,1);
	    			  boolean in_script=false;
	    			  if ((rwflag & 04)==04) // bit 3 
	    				  in_script=true;
    				  String dataname=strbld.toString();
    				  
    				  // try to read the data into 'packData.xyzpoint'.
	    			  if (CPFileManager.readDataFile(packData,dataname,in_script,1)==0) {
	    				  CirclePack.cpb.errMsg(
	    						  "Failed to load file "+dataname);
	    				  return 0;
	    			  }
	    			  return packData.set_xyz_overlaps(packData.xyzpoint,1);
	    		  }
	    		  
	    		  // look for a flag
				  EdgeLink edgelist=new EdgeLink(packData,items);
    			  Iterator<EdgeSimple> eis=edgelist.iterator();
				  
				  // to default (tangency)
	    		  if (str.startsWith("-d")) {
	    			  
	    			  // empty list, do all
		    		  if (edgelist.size()==0) { // default to all
		    			  packData.set_invD_default();
		    			  packData.fillcurves();
		    			  CirclePack.cpb.msg("set_invdist: set all inversive "+
		    					  "distances to default (1.0, tangency)");
						  return 1;
		    		  }
		    		  
					  while (eis.hasNext()) {
						  HalfEdge he=packData.packDCEL.findHalfEdge(eis.next());
						  he.setInvDist(1.0);
						  count++;
					  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("set_invdist: set "+count+
	    					  " inversive distances to default");
	    			  return count;
	    		  }
	    		  
	    		  else if (str.startsWith("-c")) { // set to current
	    			  
    				  while (eis.hasNext()) {
    					  HalfEdge he=packData.packDCEL.findHalfEdge(eis.next());
    					  he.setInvDist(packData.comp_inv_dist(
    							  he.origin.vertIndx,he.twin.origin.vertIndx));
    					  count++;
    				  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("set_invdist: set "+count+
	    					  " inversive distances to their current values");
    				  return count;
	    		  }
	    		  
	    		  // truncate to given upper limit
	    		  else if (str.startsWith("-t")) {
	    			  double uplim=Double.parseDouble((String)items.get(0));
	    		      if (uplim<=1.0) {
	    		    	  throw new DataException(
	    		    			 "usage: truncation value x must be >=1");
	    		      }
	    			  
    				  while(eis.hasNext()) {
    					  HalfEdge he=packData.packDCEL.findHalfEdge(eis.next());
    					  if (he.getInvDist()>uplim) {
    						  he.setInvDist(uplim);
    						  count++;
    					  }
    				  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("Cut "+count+
	    					   " inversive distances down to max of "+uplim);
	    		      return count;
	    		  }
	    		  
	    		  else if (str.startsWith("-h")) { // use packData.xyzpoint, 
	    			  // but set based on local edge lengths
	    			  return packData.set_xyz_overlaps(packData.xyzpoint,2);
	    		  }
	    		  
	    		  // no flag? 'inv dist' followed by edge list (default to all)
	    		  // NOTE: this is always an inversive distance, NOT an angle/Pi.
	    		  //    [-1,0) deep overlap (cos(t), where t is in (Pi/2, Pi]
	    		  //    [0,1) overlap (cos(t), where t is in (0,Pi/2]
	    		  //    1 tangency
	    		  //    (1, infty) separated (cosh(t), where t is hyp 
	    		  //    distance of circles
	    		  
	    		  double invDist=1.0;
	    		  try {
	    			  invDist=Double.parseDouble(str);
	    		  } catch (Exception ex) {
	    			  throw new ParserException(
	    				  "usage: invdist <x> {v w ...}"+ex.getMessage());
	    		  }
     			  
	    		  if (invDist<-1.0) 
     				  throw new ParserException(" tried to set 'invDist' < -1.0");
	     		  
    			  while (eis.hasNext()) {
    				  HalfEdge he=packData.packDCEL.findHalfEdge(eis.next());
    				  if (he!=null) {
    					  he.setInvDist(invDist);
    					  count++;
    				  }
    			  }
				  packData.fillcurves();
				  CirclePack.cpb.msg("Set "+count+
						   " inversive distances to "+invDist);
    			  return count;
	    	  }
	    	  
	    	  // ========= set_overlaps ========
	    	  // OBE. use 'set_invdist' after adjusting 'invDist', if given
	    	  if (cmd.startsWith("ove")) { 
	    		  
	    		  // if no segments, exception
	    		  if (flagSegs==null || flagSegs.size()==0) { 
	    			  throw new ParserException(
	    					  "usage: 'set_overlaps' has no edges specified");
	    		  }
	    		  
	    		  items=flagSegs.get(0);
    			  StringBuilder strbld=new StringBuilder("set_invdist ");
	    		  
	    		  // if a flag, just pass to 'set_invdist'
	    		  if (StringUtil.isFlag(items.get(0))) {
	    			  strbld.append(StringUtil.reconstitute(flagSegs));
	    		  }
	    		  
	    		  // otherwise look for <x> or <*x> format
	    		  else {
	    			  String str=items.get(0);
	    			  if (str.charAt(0)=='*') { // indicates inv_dist in (1, infty)
	    				  String newstr=str.substring(1,str.length()); // remove '*'
	    				  items.remove(0);
	    				  Double invdist=Double.parseDouble(newstr);
	    				  strbld.append(invdist.toString());
	    				  strbld.append(StringUtil.reconstitute(flagSegs));
	    			  }
	    			  else {
	    				  String number=items.remove(0);
	    				  Double costheta=Math.cos(Double.parseDouble(number)*Math.PI);
	    				  strbld.append(costheta.toString()+" ");
	    				  strbld.append(StringUtil.reconstitute(flagSegs));
	    			  }
	    		  }
				  return CommandStrParser.jexecute(packData,strbld.toString());
	    	  }

	    	  // ========= set_xyz =============
	    	  if (cmd.startsWith("xyz")) {
	    		  if (flagSegs==null || flagSegs.size()==0) 
	    			  return packData.set_xyz_data();
	    		  // TODO: may want to pass filename ???
	    		  return 0;
	    	  }
	    	  
	    	  // ========= set_rad =============
	    	  if (cmd.startsWith("rad")) {
	    		  if (flagSegs==null || flagSegs.size()==0 ||
	    				  (items=flagSegs.elementAt(0)).size()==0) {
	    			  throw new ParserException("check usage");
	    		  }
	    		  NodeLink nodeLink=null;
	    		  
	    		  // there are the flag options (more to come, I'm sure)
	    		  String str=(String)items.get(0);
	    		  if (StringUtil.isFlag(str)) {
	    			  PackData qackData=packData;
	    			  if (str.startsWith("-q")) {
	    				  int qnum=StringUtil.qFlagParse(str);
	    				  if (qnum<0) {
	    					  throw new ParserException("-q{p} option failed");
	    				  }
	    				  qackData=CPBase.cpDrawing[qnum].getPackData();
	    				  
	    				  // there must be additional flags
	    				  try {
	    					  items=flagSegs.get(1);
	    					  str=(String)items.remove(0);
	    					  if (!StringUtil.isFlag(str)) {
	    						  throw new ParserException("option flags missing");
	    					  }
	    					  char mode=str.charAt(1);
	    					  nodeLink=new NodeLink(qackData,items);
	    					  
	    					  Iterator<Integer> nlist=nodeLink.iterator();
	    					  while (nlist.hasNext()) {
	    						  int v=nlist.next();
	    						  if (v<=packData.nodeCount) {
	    							  count++;
	    							  double qr=qackData.getRadius(v);
	    							  switch(mode) {
	    							  case 'M': // maximum 
	    							  {
	    								  if (qr>packData.getRadius(v))
	    									  packData.setRadius(v,qr);
	    								  break;
	    							  }
	    							  case 'm': // minimum
	    							  {
	    								  if (qr<packData.getRadius(v))
	    									  packData.setRadius(v,qr);
	    								  break;
	    							  }
	    							  case 'a': // average
	    							  {
	    								  packData.setRadius(v,packData.getRadius(v)+qr);
	    								  packData.setRadius(v,packData.getRadius(v)/2.0);
	    								  break;
	    							  }
	    							  } // end of switch
	    						  } 
	    					  } // end of while
	    					  return count;
	    				  } catch (Exception ex) {
	    					  throw new DataException("set_rad error "+
	    							  "in flag options: "+ex.getMessage());
	    				  }
	    			  }
	    			  else {
	    				  throw new ParserException("-q{p} flag missing?");
	    			  }
	    		  } // end of flag option parsing
	    		  
	    		  else { // this is non-flag option: <x> <v..>
	    			  if (flagSegs.size()>1 || items.size()<2) {
	    				  throw new ParserException("check usage");
	    			  }
	    			  double rad=Double.parseDouble(items.elementAt(0));
	    			  if (rad<=0.0) {
	    				  if (packData.hes>=0)
	    					  throw new DataException("radius can be negative "+
	    						  "only in the hyperbolic setting.");
	    				  else 
	    					  rad=9.0; // essentially infinite.
	    			  }
	    			  items.remove(0);
	    			  nodeLink=new NodeLink(packData,items);
	    			  if (nodeLink==null || nodeLink.size()==0) {
	    				  throw new ParserException("no valid vertices are provided.");
	    			  }
	    		  
	    			  Iterator<Integer> vlist=nodeLink.iterator();
	    			  while (vlist.hasNext()) {
	    				  packData.setRadiusActual((Integer)vlist.next(),rad);
	    				  count++;
	    			  }
	    			  return count;
	    		  }
	    	  }
	    	  
	    	  // ========= set_center ==========
	    	  if (cmd.startsWith("cent")) {
	    		  if (flagSegs==null || flagSegs.size()==0 ||
	    				  (items=flagSegs.elementAt(0)).size()==0) {
	    			  throw new ParserException("check usage: [XY] -[fx] {v..}");
	    		  }
	    		  NodeLink vertlist=null;
	    		  
	    		  // there are the flag and data options 
	    		  String str=(String)items.get(0);
	    		  if (!StringUtil.isFlag(str)) {
	    			  Complex z=null;
	    			  if (str.startsWith("Zli") || str.startsWith("zli")) {
	    				  items.remove(0);
	    				  PointLink plk=CPBase.Zlink;
	    				  if (plk==null)
	    					  plk=packData.zlist;
	    				  if (plk!=null && plk.size()>0)
	    					  z=plk.get(0);
	    				  else 
	    					  throw new ParserException("zlist empty");
	    			  }
	    			  
		    		  // typical data: X Y {v..}. However, if first string
	    			  //   alone gives a non-real complex, assume rest are
	    			  //   vertices. (This isn't perfect, but ...???...)
	    			  else {
	    			  try {
	    				  z=Complex.string2Complex(items.remove(0));
	    				  // if real, take as hint that next is Y
	    				  if (z.y==0.0) {
	    					  Complex zz=Complex.string2Complex(items.remove(0));
		    				  z.y=zz.x;
	    				  }
	    			  } catch (VarException vex) {
	    				  throw new ParserException(vex.getMessage());
	    			  }
	    			  }
	    			  
	    			  // now get the vertices
	    			  vertlist=new NodeLink(packData,items);
	    			  return packData.set_centers(z,vertlist);
	    		  }
	    		  items.remove(0); // str holds flag
	    		  char c=str.charAt(1);
	    		  switch(c) {
	    		  case 'f': // fall through use mapping: center --> "Function Frame" value
	    		  case 'm': // deprecated
	    		  {
	    			  if (PackControl.newftnFrame.ftnField.getText().trim().length()==0) {
	    				  CirclePack.cpb.errMsg("'Function' frame is not set");
	    				  return 0;
	    			  }
	    			  if (items.size()==0) // default to 'all'
	    				  vertlist=new NodeLink(packData,"a");
	    			  else 
	    				  vertlist=new NodeLink(packData,items);
	    			  Iterator<Integer> vlst=vertlist.iterator();
	    			  while (vlst.hasNext()) {
	    				  int v=vlst.next();
	    				  Complex z=packData.getCenter(v);
	    				  // convert to complex to pass to function
	    				  if (packData.hes>0)  
	    					  z=SphericalMath.s_pt_to_plane(z);
	    				  Complex w=CirclePack.cpb.getFtnValue(z);
	    				  if (packData.hes>0) // back to sphere
	    					  w=SphericalMath.proj_pt_to_sph(w);
	    				  packData.setCenter(v,w);
	    				  count++;
	    			  } // end of while
	    			  break;
	    		  }
	    		  case 'x': // use xyz points, if available, depending on geometry
	    		  {
	    			  if (str.contains("xy") && packData.xyzpoint!=null) {
		    			  vertlist=new NodeLink(packData,items);
		    			  Iterator<Integer> vlst=vertlist.iterator();
		    			  while (vlst.hasNext()) {
		    				  int vv=vlst.next();
		    				  
		    				  // hyp/eucl, just use x, y coords
		    				  if (packData.hes<=0) {
		    					  packData.setCenter(vv,packData.xyzpoint[vv].x,
		    							  	packData.xyzpoint[vv].y);
		    					  count++;
		    				  }
		    				  else { // sphere?
		    					  packData.setCenter(vv,
		    							  SphericalMath.proj_vec_to_sph(
		    									  packData.xyzpoint[vv]));
		    					  count++;
		    				  }
		    			  }
	    			  }
	    			  break;
	    		  }
	    		  } // end of switch
	    		  return count;
	    	  }  	   	  
	    	  

	          // ========= set_aims ==========
	    	  if (cmd.startsWith("aim")) {
	        	  int mode=3;
	        	  double inc=1.0;
	        	  double aim=2*Math.PI;
	        	  
	        	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	        	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	        		  if (StringUtil.isFlag(items.elementAt(0))) {
	        			  char c=items.get(0).charAt(1);
	        			  items.remove(0);
	        			  switch(c) {
	    			  
	        			  // Here's the specific parsing of flag itself    			 
	        			  case 'd': // mode = set to default angle sums
	        			  {
	        				  mode=1;
	        				  break;
	        			  }
	        			  case 'c': // mode = set to current angle sums 
	        			  {
	        				  mode=2;
	        				  break;
	        			  }
	        			  case '%': // mode = modify current aims by multiplicative factor
	        			  {
	        				  // TODO: what to do with inversive distances >= 1?
	        				  mode=3; 
	        				  try {
	         					 inc=Double.parseDouble(items.get(0));
	        					 if (inc<=0.0) {
	        						 CirclePack.cpb.myErrorMsg(
	        								 "set_aim: usage: -% x. Must have x>0.");
	        					 }
	        					 items.remove(0);
	        				  } catch (NumberFormatException nfe) {
	        					  CirclePack.cpb.myErrorMsg("set_aim: usage: -% x "+
	        							  "where x>0 is multiplicative factor.");
	        					  return count;
	        				  }
	        				  break;
	        			  }
	        			  case 'x': // use 3D 'xyz' data, if available
	        			  {
	        				  mode=4;
	        				  if (packData.xyzpoint==null)
	        					  return 0;
	        				  break;
	        			  }
	        			  case 't': // give factor for moving interiors toward 2pi
	        			  {
	        				  mode=5; 
	        				  try {
	         					 inc=Double.parseDouble(items.get(0));
	        					 if (inc<=0.0 || inc>1.0) {
	        						 CirclePack.cpb.myErrorMsg("set_aim: usage: "+
	        								 "-t x, x in (0,1].");
	        					 }
	        					 items.remove(0);
	        				  } catch (NumberFormatException nfe) {
	        					  CirclePack.cpb.myErrorMsg("set_aim: usage: "+
	        							  "-t x where x>0 is factor.");
	        					  return count;
	        				  }
	        				  break;
	        			  }
	        			  case 'a': // add given amount to aim, may be +,-
	        			  {
	        				  mode=6; 
	        				  try {
	         					 inc=Double.parseDouble(items.get(0));
	        					 items.remove(0);
	        				  } catch (NumberFormatException nfe) {
	        					  CirclePack.cpb.myErrorMsg("set_aim: usage: "+
	        							  "-t x where x>0 is factor.");
	        					  return count;
	        				  }
	        				  break;
	        			  }
	        			  } // end of flag switch
	        		  }
	        		  else { // no flag; default action is to read a double aim first.
	        			  mode=0; // normal "set this value for these vertices"
	        			  try {
	    					 aim=Double.parseDouble(items.remove(0));
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("set_aim: usage: x {v..} "+
	    							  "where x>0 is intended aim.");
	    					  return count;
	    				  }
	        		  }
	        		  
	        		  if (items.size()==0) { // no list
	        			  if (mode==0 || mode==3 || mode==6) return 0;
	        			  if (mode==1) { // default
	        				  packData.set_aim_default();
	        				  return (packData.nodeCount);
	        			  }
	        			  if (mode==2) { // current
	        				  return packData.set_aim_current(false);
	        			  }
	        			  if (mode==4) { // based on 'xyzpoint'
	        				  return packData.set_aim_xyz(true);
	        			  }
	        			  if (mode==5) { // move by factor towards flat
	        				  packData.set_aim_x_flatten(inc);
	        			  }
	        			  return 0;
	        		  }
	        		  
	        		  // Now read the vertex list
	        		  NodeLink vertlist=new NodeLink(packData,items);
	        		  if (vertlist.size()>0) {
	        			  Iterator<Integer> vl=vertlist.iterator();
	        			  
	        			  while (vl.hasNext()) {
	        				  int v=(Integer)vl.next();
	        				  if (mode==1) {
	        					  packData.set_aim_default(vertlist);
	        					  count++;
	        				  }
	        				  else if (mode==2) {
	        					  packData.setAim(v,packData.getCurv(v));
	        					  count++;
	        				  }
	        				  else if (mode==3) { // TODO: is this the right adjustment?
	        					  packData.setAim(v,packData.getAim(v)+inc*Math.PI);
	        					  count++;
	        				  }
	        				  else if (mode==0) {
	        					  if (aim!=0.0 || ((packData.hes < 0) && packData.isBdry(v))) {
	        						  packData.setAim(v,aim*Math.PI);
	        					  	  count++;
	        					  }
	        					  else count--;
	        				  }
	        				  else if (mode==4) { // based on 'xyzpoint' data
	        						double angsum=0.0;
	        						for (int j=0;j<packData.countFaces(v);j++) {
	        							int n=packData.getPetal(v,j);
	        							int m=packData.getPetal(v,j+1);
	        							angsum += Math.acos(EuclMath.e_cos_3D(
	        									packData.xyzpoint[v],packData.xyzpoint[n],
	        									packData.xyzpoint[m]));
	        						}
	        						packData.setAim(v,angsum);
	  	        				  count++;
	        				  }
	        				  else if (mode==5 && !packData.isBdry(v)) { // towards flat by increment
	        					  double curv=2.0*Math.PI-packData.getAim(v);
	        					  packData.setAim(v, packData.getAim(v)+inc*curv);
		        				  count++;
	        				  }
	        				  else if (mode==6) { // additive amount x in [-0.1,0.1].
	        					  packData.setAim(v, packData.getAim(v)+inc);
		        				  count++;
	        				  }
 	        			  }
	        			  return count;
	        		  }
	        		  return 0;
	        	  } // end of set_aim while
	          } // done with 'set_aim'

	    	  // =============== set_plot_flags =======
	    	  if (cmd.startsWith("plot_f")) {
	    		  int pf=1;
	    		  if (flagSegs==null || flagSegs.size()>1 || (items=flagSegs.elementAt(0)).size()<2) {
	    			  throw new ParserException("check usage");
	    		  }
	    		  pf=Integer.parseInt(items.get(0));
	    		  
	    		  NodeLink nodeLink=new NodeLink(packData,(String)items.get(1));
	    		  Iterator<Integer> vlist=nodeLink.iterator();
	    		  while (vlist.hasNext()) {
	    			  packData.setPlotFlag((Integer)vlist.next(),pf);
	    			  count++;
	    		  }
	    	  }
	    	  
	    	  // ========== set_?list =========
	    	  if (cmd.substring(1).startsWith("list")) {
	    		  char c=cmd.charAt(0);
	    		  try { // just one flag sequence
	    			  items=(Vector<String>)flagSegs.elementAt(0);
	    			  if (items.size()==0) items=(Vector<String>)null;
	    		  } catch(Exception ex){
	    			  items=(Vector<String>)null;
	    		  }
	    		  switch(c) {
	    		  case 'v': 
	    		  {
	    			  if (items==null) {
	    				  packData.vlist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.vlist=new NodeLink(packData,items);
	    				  count=packData.vlist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'V':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Vlink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Vlink=new NodeLink(packData,items);
	    				  count=CPBase.Vlink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'e':
	    		  {
	    			  if (items==null) {
	    				  packData.elist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.elist=new EdgeLink(packData,items);
	    				  count=packData.elist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'E':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Elink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Elink=new EdgeLink(packData,items);
	    				  count=CPBase.Elink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'h':
	    		  {
	    			  if (items==null) {
	    				  packData.hlist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.hlist=new HalfLink(packData,items);
	    				  count=packData.hlist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'H':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Hlink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Hlink=new HalfLink(packData,items);
	    				  count=CPBase.Hlink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'f':
	    		  {
	    			  if (items==null) {
	    				  packData.flist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.flist=new FaceLink(packData,items);
	    				  count=packData.flist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'F':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Flink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Flink=new FaceLink(packData,items);
	    				  count=CPBase.Flink.size();
	    			  }
	    			  break;
	    		  }
	    		  //  'set_tlist' can also be called through 'ConformalTiling'.
	    		  case 't': // 'tileData' tiles only;
	    		  {
	    			  if (items==null) {
	    				  packData.tlist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.tlist=new TileLink(packData.tileData,items);
	    				  count=packData.tlist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'T': // 'tileData' tiles only
	    		  {
	    			  if (items==null) {
	    				  CPBase.Tlink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Tlink=new TileLink(packData.tileData,items);
	    				  count=CPBase.Tlink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'g':
	    		  {
	    			  if (items==null) {
	    				  packData.glist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.glist=new GraphLink(packData,items);
	    				  count=packData.glist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'G':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Glink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Glink=new GraphLink(packData,items);
	    				  count=CPBase.Glink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'z':
	    		  {
	    			  if (items==null) {
	    				  packData.zlist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.zlist=new PointLink(items);
	    				  count=packData.zlist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'D':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Dlink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Dlink=new DoubleLink(packData,items);
	    				  count=CPBase.Dlink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'Z':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Zlink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Zlink=new PointLink(items);
	    				  count=CPBase.Zlink.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'b':
	    		  {
	    			  if (items==null) {
	    				  packData.blist=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  packData.blist=new BaryLink(packData,items);
	    				  count=packData.blist.size();
	    			  }
	    			  break;
	    		  }
	    		  case 'B':
	    		  {
	    			  if (items==null) {
	    				  CPBase.Blink=null;
	    				  count=1;
	    			  }
	    			  else {
	    				  CPBase.Blink=new BaryLink(packData,items);
	    				  count=CPBase.Blink.size();
	    			  }
	    			  break;
	    		  }
	    		  } // end of switch
	    		  
	    		  return count;
	    	  }

	          // =============== set_screen =====
	    	  // this is full set of options; 
	    	  //     processing in parent for those not needing packing
	          if (cmd.startsWith("screen")) {
	        	  ViewBox vbox=packData.cpDrawing.realBox;
	        	  Complex utilz=new Complex(0.0);
	        	  char c;
	    	  
	        	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    	  
	        	  while (nextFlag.hasNext() && 
	        			  (items=nextFlag.next()).size()>0) {
	        		  if (StringUtil.isFlag(items.elementAt(0))) {
	        			  c=items.get(0).charAt(1);
	        			  switch(c) {
	    			  
	    			  case 'a': // adjust up to see all circles (eucl only)
	    			  {
	    				  double dist;
	    				  if (packData.hes!=0) // not error, but no effect 
	    					  return 1;
	    				  
	    				  double hwid=vbox.getWidth()/2.0;
	    				  double hhgt=vbox.getHeight()/2.0;
	    			      
	    			      // get start at vert 1
	    				  Complex z=packData.getCenter(1);
	    				  double rad=packData.getRadius(1);
	    			      double maxw = Math.abs(z.x)+rad;
	    			      double maxh = Math.abs(z.y)+rad;
	    			      for (int i=2;i<=packData.nodeCount;i++) {
	    			    	  z=packData.getCenter(i);
	    			    	  rad=packData.getRadius(i);
	    			    	  dist=Math.abs(z.x)+rad;
	    			    	  if (dist>maxw) maxw=dist;
	    			    	  dist=Math.abs(z.y)+rad;
	    			    	  if (dist>maxh) maxh=dist;
	    			      }
	    			      if (maxw>hwid || maxh>hhgt) { // need to scale vbox up
	    			    	  double factor=(maxw/hwid>maxh/hhgt) ? maxw/hwid : maxh/hhgt;
	    			    	  vbox.scaleView(1.1*factor); // scale with a margin
	    			    	  packData.cpDrawing.update(2);
	    			      }
	    			      count++;
	    			      break;
	    			  }
	    			  case 'b': // set real box (lx,ly), (rx,ry)
	    				  // TODO: need tailored versions for speed, 
	    				  //     change 'CPDrawing.update' too
	    			  {
	    				  double []corners=new double[4];
	    				  try {
	    					  for (int i=0;i<4;i++)
	    						  corners[i]=Double.parseDouble(items.get(i+1));
	    					  packData.cpDrawing.realBox.setView(new Complex(corners[0],corners[1]),
	    							  new Complex(corners[2],corners[3]));
	    				  } catch (Exception ex) {
	    					  CirclePack.cpb.myErrorMsg("'"+cmd+"' parsing error.");
	    					  return count;
	    				  }
	    				  count++;
	    				  packData.cpDrawing.update(2);
	    				  break;
	    			  }
	    			  case 'd':	// default canvas size, sphView
	    			  {
	    				  vbox.reset();
	    				  count++;
	    				  packData.cpDrawing.update(2);
	    				  packData.cpDrawing.sphView.defaultView();
	    				  break;
	    			  }
	    			  case 'f': // scale by given factor
	    			  {
	    				  try {
	    					  count += packData.cpDrawing.realBox.
	    							  scaleView(Double.parseDouble(items.get(1)));
	    					  packData.cpDrawing.update(2);
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen "+
	    							  "-f <x>: "+nfe.getMessage());
	    				  }
	    				  break;
	    			  }
	    			  case 'i': // incremental moves (typically from mouse click)
	    			  {
	    				  try {
	    					  utilz=new Complex(Double.parseDouble(items.get(1)),
	    							  Double.parseDouble(items.get(2)));
	    					  count +=vbox.transView(utilz);
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen "+
	    							  "-i <x> <y>: "+nfe.getMessage());
	    				  }
	    				  break;
	    				  
	    			  }
	    			  case 'h': // height (or fall through for width)
	    			  {}
	    			  case 'w': // width
	    			  {  	
	    				  try {
	    					  double f=Double.parseDouble(items.get(1));
	    					  count += packData.cpDrawing.realBox.scaleView(f/vbox.getWidth());
	    					  packData.cpDrawing.update(2);
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen "+
	    							  "-w(or h) <x>: "+nfe.getMessage());
	    				  }
	    				  break;
	    			  }
	    			  case 'c': // center at the origin. Fall through first to 'z', 
	    				  // then further, to 'v'.
	    			  {
	    				  utilz=new Complex(0.0); // default, if problems below
	    			  }
	    			  case 'z': // Fall through to 'v'
	    			  {
	    				  if (c=='z') {
	       				  try {
	    					  utilz=new Complex(Double.parseDouble(items.get(1)),
	    							  Double.parseDouble(items.get(2)));
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen -[zc]"+
	    							  " <x> <y>: "+nfe.getMessage());
	    				  }
	    				  }
	    			  }
	    			  case 'v': // center on center of v
	    			  {
	    				  if (c=='v') { // did not fall through
	    					  try {
							  int v=NodeLink.grab_one_vert(packData,(String)items.get(1));
	    					  utilz=packData.getCenter(v);
	    					  if (packData.hes<0) {
	    						  CircleSimple sc=HyperbolicMath.h_to_e_data(utilz,packData.getRadius(v));
	    						  utilz=sc.center;
	    					  }
	    					  else if (packData.hes>0) {
	    						  utilz=packData.cpDrawing.sphView.toApparentSph(utilz);
	    						  // TODO: do we need to check if on back?
	    					  } 
	    					  } catch (NumberFormatException nfe) {
	    						  CirclePack.cpb.myErrorMsg("usage: set_screen -v <n>: "+
	    								  nfe.getMessage());
	    					  }
	    				  }
	    				      				  
	    				  // now should have 'utilz' one way or another
	    				  count +=vbox.focusView(utilz);
	    				  break;
	    			  }
	    			  
	    			  } // end of flag switch
	    		  } // done handling a given flag
	    		  else { // no flags? default to default screen
    				  vbox.reset();
    				  count++;
    				  packData.cpDrawing.update(2);
	    		  }
	    	  } // end of while

	    	  if (count>0) {
	    		  packData.cpDrawing.repaint();
	    	  }
	    	  return count;
	          } // done with 'set_screen'
	      } // done with all "set_" command parsing

	      // ========= spiral =======
	      if (cmd.startsWith("spiral")) {
	    	  boolean inc_flag=false;
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  String str=(String)items.get(0);
	    	  if (str.startsWith("-f")) {
	    		  inc_flag=true;
	    		  items.remove(0);
	    	  }
	    	  double a=Double.parseDouble((String)items.get(0));
	    	  double b=Double.parseDouble((String)items.get(1));
		    	  
	    	  if (inc_flag) {
	    		  int al=packData.getAlpha();
	    		  double rad=packData.getRadius(al);
//		    	  if (rad<=packData.OKERR) { // this shouldn't happen 
//		    		  throw new DataException();
//		    	  }
	    	  
	    		  int[] flower=packData.getFlower(al);
	    		  int j=flower[0];
	    		  a=a*packData.getRadius(j)/rad;
	    		  j=flower[1];
	    		  b=b*packData.getRadius(j)/rad;
	    	  }
	    	  return Exponential.spiral(packData,a,b);
	      }
					 	
	      // ========= slit ========
	      if (cmd.startsWith("slit")) {
	    	  NodeLink vertlist=new NodeLink(packData,(Vector<String>)flagSegs.get(0));
	    	  if (vertlist==null || vertlist.size()==0)
	    		  return 0;

    		  HalfLink hlink=HalfLink.getChain(packData.packDCEL, vertlist);
    		  if (hlink!=null) {
    			  int[] rslt=CombDCEL.slitComplex(packData.packDCEL, hlink);
    			  if (rslt==null)
    				  throw new CombException("something wrong in 'slit' attempt");
    			  packData.attachDCEL(packData.packDCEL);
    			  CirclePack.cpb.msg("'slit' gave new bdry edges from "+
    				  rslt[0]+" to "+rslt[1]);
    		  }
    		  return 1;
	      }
		  break;
      } // end of 's'
      case 'u':
      {
	      // ========= unflip ========
	      if (cmd.startsWith("unflip")) {
	    	  items=flagSegs.elementAt(0); // should be only one segment
	   		  EdgeLink edgeLink=new EdgeLink(packData,items);
	   		  if (edgeLink==null || edgeLink.size()<1) 
	   			  return 0;
	   		  Iterator<EdgeSimple> elist=edgeLink.iterator();
	   		  
	   		  HalfLink hlink=new HalfLink(packData);
	   		  while (elist.hasNext()) {
	   			  EdgeSimple edge=elist.next();
	   			  HalfEdge he=RawManip.getCommonEdge(packData.packDCEL,edge.v,edge.w);
	   			  hlink.add(he); // might be null
	   		  }
    		  if (hlink==null || hlink.size()==0)
    			  return 0;
    		  Iterator<HalfEdge> his=hlink.iterator();
    		  while (his.hasNext()) {
    			  HalfEdge he=his.next();
    			  HalfEdge newhe=RawManip.flipEdge_raw(packData.packDCEL,he);
    			  if (newhe!=null)
    				  count++;
    		  }
    		  if (count>0)
   				  packData.packDCEL.fixDCEL(packData);
   			  packData.fillcurves();
    		  return count;
	      }
    	  break;
      } // end of 'u'
      case 'v':
      {
	      // ========= vert_map_off =========
	      if (cmd.startsWith("vert_map_off")) {
	    	  packData.vertexMap=null;
	    	  return 1;
	      }
    	  break;
      } // end of 'v'
      
	  case 'w':  // fall through from 'w' to 'W'
	  case 'W': 
	  {  	      // =============== write ==============
	      /**
			 * Parse options for writing packings. Caution, no confirmation
			 * requested. Default is 'cgir'. Note: all options in one continuous
			 * string directly after '-'. If 'prePath' is true, then prepend path
			 * (i.e., not default).
			 * 
			 * A append (must have some accompanying options) 
			 * (TODO: replace 'A' option with '-a <filename>' versus '-f <filename>' at
			 * end of command string)
			 * c combinatorics 
			 * d display option flags
			 * g geometry 
			 * i non-default inv_dist and aims 
			 * r radii (g needed) 
			 * z centers 
			 * a angle sums 
			 * v vertex_map 
			 * l non-default lists: verts/faces/edges 
			 * L non-default Global lists: Verts/Faces/Edges
			 * o non-default colors 
			 * f plot_flags which are .le. 0 (e.g. means poorly placed) 
			 * s goes into script file included in script
			 * e edge-pairing mobius (new, 6/07) 
			 * t faces (as triples of triangles) (new, 6/07) 
			 * T tiling (if 'tileData' exists)
			 * F dual faces as a tiling; no other data
			 * x xyz data
			 * 
			 * m minimum = cgri 0017 
			 * M max = cgriazoflv 0777
			 */
	      if ((cmd.startsWith("write") || cmd.startsWith("Write")) &&
	    		  !cmd.contains("L") && !cmd.contains("_")) {
	      	  int len;
	      	  String fname=null;
	      	  int act=0000;  // bit-encoded write flags
	      	  String flagstr=null;
	      	  boolean append_flag=false;
	      	  boolean script_flag=false;
	      	  File dir=CPFileManager.PackingDirectory;
	      	  
	      	  try {
	      		  // should be just one flag string for display codes and
	      		  //   possibly one for trailing filename
	      		  items=(Vector<String>)flagSegs.get(0);
	      		  flagstr=items.firstElement();
	     		  if (!StringUtil.isFlag(flagstr)) {
	     			  act=020017; 
	     			  flagstr=null; // can only be the filename
	     		  }

		      	  // get filename 
		      	  StringBuilder strbld=new StringBuilder();
		      	  int code=CPFileManager.trailingFile(flagSegs, strbld);
		      	  if ((code & 02)==02) 
		      		  append_flag=true;
		      	  if ((code & 04)==04)
		      		  script_flag=true;
		      	  File tmpdir=new File(strbld.toString().trim());
		      	  fname=tmpdir.getName().trim();
		      	  if (fname.length()==0)
		      		  fname=packData.fileName;
		      	  if (tmpdir.getParentFile()!=null) {
		      		  if (cmd.startsWith("W"))  // use given directory
		      			  dir=tmpdir.getParentFile();
		      		  else // append to current 'dir'
		      			  dir=new File(dir.getName()+tmpdir.getParent()); 
		      	  }
		      	  // Note: if "Write" but no directory given, default to current 'dir'

	      	  } catch (Exception ex) {
	      		  throw new InOutException("check usage: "+ex.getMessage());
	      	  }
	      	  
	      	  if (flagstr!=null && flagstr.length()>0 && StringUtil.isFlag(flagstr)) {
	      		  flagstr=flagstr.substring(1);
	      		  len=flagstr.length();

	      		  // just "s"? equivalent to "cgirzs" 
	      		  if (len==1 && flagstr.equalsIgnoreCase("s")) {
	      			  if (cmd.charAt(0)=='W') {
	      				  CirclePack.cpb.myErrorMsg("Can't 'Write' (cap 'W') to script");
	      				  return 0;
	      			  }
	      			  script_flag=true;
	      			  act=00037;
	      		  }
	      		  // "A" (append) must be accompanied by other options
	      		  else if (len==1 && flagstr.equals("A")) {
	      			  return 0;
	      		  }
   		  
	      		  // build 'act' encoding
	      		  else if (flagstr!=null) {
	      			  for(int j=0;j<len;j++) {
	      				  switch(flagstr.charAt(j)) {
	      				  case 'A': {append_flag=true;break;}
	      				  case 'c': {act |= 00001;break;}
	      				  case 'd': {act |= 040000;break;}
	      				  case 'g': {act |= 00002;break;}
	      				  case 'i': {act |= 00004;break;}
	      				  case 'r': {act |= 00010;break;}
	      				  case 'z': {act |= 00020;break;}
	      				  case 'a': {act |= 00040;break;}
	      				  case 'v': {act |= 00100;break;}
	      				  case 'l': {act |= 00200;break;}
	      				  case 'L': {act |= 0100000;break;}
	      				  case 'o': {act |= 00400;break;}
	  	     			  case 'f': {act |= 01000;break;}
	  	     			  case 't': {act |= 010000;break;} // triangles
	  	     			  case 'T': {act |= 020000;break;} // tiles (if 'TileData' exists)
	  	     			  case 'F': {act |= 0200000;break;} // dual faces as a tiling
	  	     			  case 's': { // write to the script file
	  	     				  if (cmd.charAt(0)=='W') {
	  	     					  CirclePack.cpb.myErrorMsg("Can't 'Write' (cap 'W') to script");
	  	     					  return 0;
	  	     				  }
	  	     				  script_flag=true;
	  	     				  break;
	  	     			  }
	  	     			  case 'e': { // side pairing Mobius maps
	  	     				  if (packData.getSidePairs()!=null) act |= 04000;
	  	     				  break;
	  	     			  }
	  	     			  case 'm': {act |= 020017;break;}
	  	     			  case 'M': {act |= 031777;break;}
	  	     			  case 'x': { // xyz points
	  	     				  if (packData.xyzpoint!=null) 
	  	     					  act |= 02000;
	  	     				  else {
	  	     					  throw new ParserException("'-x' in write call "+
	  	     							  "requires xyz date: see 'set_xyz'");
	  	     				  }
	  	     			  }
	  	     			  case 'n': {act |= 01000000;break;} // util list of integers
	  	     			  case 'y': {act |= 02000000;break;} // util list of doubles
	  	     			  case 'w': {act |= 04000000;break;} // util list of complexes

	  	     			  case 'S': {act |= 020000000;break;} // schwarzians (if exist) 

	      				  } // end of flag parsing switch
	      			  } // end of for
	      		  } // end of flag parsing
	      	  }
	      	  
	      	  BufferedWriter fp=CPFileManager.openWriteFP(dir,append_flag,fname,script_flag);
	      	  try { // (00017 & 00004)==00004;
	      		  ReadWrite.writePack(fp,packData,act,false);
	      	  } catch(Exception ex) {
	      		  throw new InOutException("write failed");
	      	  }
	      	  if (script_flag) { // include in script
	      		  CPBase.scriptManager.includeNewFile(fname);
	      		  CirclePack.cpb.msg("Wrote packing "+fname+" to the script");
	      		  return act;
	      	  }
	      	  CirclePack.cpb.msg("Wrote packing to "+dir.getPath()+
   				  File.separator+fname);
	      	  return act;
	      	}
	      	break;
	  } // end of 'w' and 'W'
	  
	  case 'z':
	  {
		  // ============ zip ==============
	      if (cmd.startsWith("zip")) {
	      /** 
	       * A special case of 'adjoin'; p1=p2 and v1=v2; one is 'zipping' up 
	       * one bdry component (like reverse of 'slit'). Other options the same, 
	       * so n<0 means do as many as possible; if the bdry component has an 
	       * odd number of edges, then have to scale back to leave 3 unpasted 
	       * edges. For other cautions, see 'adjoin', e.g., losing overlap data,
	       * and vertexMap contains <orig,new>.
	      */
	          int n;
	          items=(Vector<String>)flagSegs.get(0);
	          if (items.size()<2)
	        	  throw new ParserException("usage: zip n v");

	          // get n
	          try {
	        	  n=Integer.parseInt((String)items.remove(0));
	          } catch(Exception ex) {n=-1;}  // default: do whole bdry comp
	          
	          // vertex index (one only) comes first
	          int v=NodeLink.grab_one_vert(packData,items.remove(0));
        	  if (!packData.isBdry(v)) {
        		  throw new ParserException("'zip' usage: n v, 'v' must be boundary");
        	  }
	          
        	  int b=packData.bdry_comp_count(v);
        	  int m=b/2;
        	  if (b!=2*m) 
        		  m=(b-1)/2;
        	  if (n<0 || n>=m) 
        		  n=m; // full bdry
        	  
       		  PackDCEL pdcel=packData.packDCEL;
       		  for (int j=1;j<=pdcel.vertCount;j++) 
       			  pdcel.vertices[j].vutil=j;
			  HalfEdge nxtedge=
					  pdcel.vertices[v].halfedge.twin.next;
			  for (int j=1;j<=n;j++) {
				  Vertex vert=nxtedge.origin;
				  nxtedge=nxtedge.next;
				  CombDCEL.zipEdge(pdcel,vert);
			  }
			  pdcel=CombDCEL.wrapAdjoin(pdcel,pdcel);
			  VertexMap oldnew=pdcel.oldNew;
			  pdcel.fixDCEL(packData);
			  packData.vertexMap=oldnew;
			  packData.vlist=NodeLink.translate(packData.vlist,oldnew);
			  packData.elist=EdgeLink.translate(packData.elist,oldnew);
			  return packData.nodeCount;
	      } 		  
		  break;
	  } // end of 'z'
	  default: 
	  {
		  return -1; // didn't find the command here
	  }  // end of switch
  } // end of main switch
	
	  return -1; // indicates that command was not processed
  } // end of 'packExecute'
  
/**
 * Execute commands that return a value of some type. This handles
 * just a single call (one command with accompanying flags) and is 
 * called when 'jexecute' encounters a command in braces, '{cmd..}' 
 * (e.g., after '?' or ':=') or directly from 'TrafficCenter.parseValueCall'.
 * In either case, preprocessing leaves
	 *   no ';' separators:
	 *   no 'for' or 'FOR' loops:
	 *   no 'delay's:
	 *   no [n] named commands to resolve:
	 *   no '!!' repeats to resolve:
*   
* TODO: We do have to catch possible '|pe|' 'PackExtender' calls, as was
* done in 'TrafficCenter.parseCmdSeq'.
*   
* This routine handles housekeeping, separating cmd and
* flag sequences. Flow splits to handle those cases where the packing 
* and its status are important, then the remaining commands. 
* 
* TODO: move commands here as we decide that we need to catch
* their return values for some purpose -- e.g., writing to a file.
* 
* @param packData PackData, possibly null
* @param cmdstr String; command string
* @return CallPacket; null or 'error'=true
*/
public static CallPacket valueExecute(PackData packData,String cmdstr) {
	// normally just a single command (and its flags); we toss any others
	cmdstr = cmdstr.split(";")[0].trim(); // discard all but first command string
	
	// Note: 'string2vec' catches variables, reconstitutes parens/brackets
	Vector<String> allitems=StringUtil.string2vec(cmdstr,true);
	String cmd=(String)allitems.remove(0);
	
	// need to check for -p flag, should now be first string
	PackData p=packData;
	if (allitems.size()>0 && allitems.get(0).startsWith("-p")) {
		StringBuilder sb=new StringBuilder(allitems.remove(0));
		int pnum=StringUtil.extractPackNum(sb);
		if (pnum>=0)
			p=CPBase.cpDrawing[pnum].getPackData();
	}
	
	/* NOTE: Vector 'flagSegs' will hold only the flag strings 
	 * occurring after the command --- the command itself is 'cmd' */
	Vector<Vector<String>> flagSegs=StringUtil.flagSeg(allitems);
	return valueExecute(p,cmd,flagSegs);
}

/**
 * Should be called from 'valueExecute' or from 'jexecute', 
 * and in both cases, any -p flag should have been 
 * processed and removed.
 * @param packData PackData
 * @param cmd String
 * @param flagSegs Vector<Vector<String>>
 * @return CallPacket or null on error
 */
public static CallPacket valueExecute(PackData packData,
		String cmd,Vector<Vector<String>> flagSegs) {
	CallPacket rtnCp=null;
	
	Vector<String> items=null;
	if (flagSegs.size()>0) 
		items=flagSegs.get(0);

	// ============ first: commands needing packing ================ 
	if (packData!=null && packData.status) {
		
		// note: if successfull, create and return 'rtnCp'
		switch(cmd.charAt(0)) {
		case 'a':
		{
		      // ========= aspect ====== (formerly 'rect_ratio')
		      if (cmd.startsWith("aspect")) {
		    	  if (packData.hes!=0) 
		    		  throw new ParserException("only euclidean packings (for now)");
//		    	  items=flagSegs.elementAt(0); // should be only one segment
		    	  NodeLink vertlist=new NodeLink(packData,items);
		    	  if (vertlist.size()<4) {
		    		  throw new DataException("requires 4 (counterclockwise) boundary vertices.");
		    	  }
		    	  int []cnrs=new int[4];

		    	  Iterator<Integer> vlist=vertlist.iterator();
		    	  int i=0;
		    	  while (vlist.hasNext()) {
		    		  int v=(Integer)vlist.next();
		    		  cnrs[i]=v;
		    		  i++;
		    	  }
		    	  for (i=0;i<4;i++) 
		    		  if (!packData.isBdry(cnrs[i])) {
		    			  throw new DataException("corners must be boundary vertices.");
		    		  }
		    	  double aspect=packData.rect_ratio(cnrs[0],cnrs[1],cnrs[2],cnrs[3]);
		    	  rtnCp=new CallPacket("aspect");
		    	  rtnCp.double_vec=new Vector<Double>();
		    	  rtnCp.double_vec.add(aspect);
		    	  rtnCp.int_vec=new Vector<Integer>();
		    	  for (int j=0;j<4;j++)
		    		  rtnCp.int_vec.add(Integer.valueOf(cnrs[j]));
		    	  return rtnCp;
		      }
		      break;
		}
		case 'c':
		{
			
			// ============ count ==========
			if (cmd.startsWith("count")) {
				int count=0;
		    	items=(Vector<String>)flagSegs.get(0); // should be at most one flag
		    	String str=(String)items.get(0);
		    	if (!StringUtil.isFlag(str)) { // by default, must want circles
		    		NodeLink vertlist=new NodeLink(packData,items);
		    		count=NodeLink.countMe(vertlist);
		    		if (count==0) {
		    			CirclePack.cpb.msg("count -v: no vertices specified");
		    			return null;
		    		}
		    		CirclePack.cpb.msg("count of vertices: "+count);
		    		rtnCp=new CallPacket("count");
		    		rtnCp.int_vec=new Vector<Integer>();
		    		rtnCp.int_vec.add(count);
		    		return rtnCp;
		    	}
		    	else {
		    		items.remove(0);
		    		char c=str.charAt(1);
		    		switch(c) {
		    		case 'f': 
		    		{
		    			FaceLink facelist=new FaceLink(packData,items);
		    			count=FaceLink.countMe(facelist);
		    			if (count==0) {
		    				CirclePack.cpb.msg("count -f: no faces specified");
		    				return null;
			    		}
			    		CirclePack.cpb.msg("count of faces: "+count);
			    		rtnCp=new CallPacket("count");
			    		rtnCp.int_vec=new Vector<Integer>();
			    		rtnCp.int_vec.add(count);
			    		return rtnCp;
		    		}
		    		case 'e': // edges (no check for redundancies)
		    		{
		    			EdgeLink edgelist=new EdgeLink(packData,items);
		    			if (edgelist==null || (count=edgelist.size())==0) {
		    				CirclePack.cpb.msg("count -e: no edges specified");
		        			return null;
		    			}
		    			CirclePack.cpb.msg("count of edges: "+count+
		    					"(with possible redundancies, orientations) ");
			    		rtnCp=new CallPacket("count");
			    		rtnCp.int_vec=new Vector<Integer>();
			    		rtnCp.int_vec.add(count);
			    		return rtnCp;
		    		}
		    		case 't': // tiles (if they exist)
		    		{
		    			if (packData.tileData==null)
		    				return null;
		    			TileLink tilelist=new TileLink(packData.tileData,items);
		    			if (tilelist==null || (count=tilelist.size())==0) {
		    				CirclePack.cpb.msg("count -t: no tiles specified");
		        			return null;
		    			}
		    			CirclePack.cpb.msg("count of tiles: "+count+
		    					"(with possible redundancies, orientations) ");
			    		rtnCp=new CallPacket("count");
			    		rtnCp.int_vec=new Vector<Integer>();
			    		rtnCp.int_vec.add(count);
			    		return rtnCp;
		    			
		    		}
		    		
		    		default: // default to vertices
		    		{
		    			NodeLink vertlist=new NodeLink(packData,items);
			    		count=NodeLink.countMe(vertlist);
			    		if (count==0) {
			    			CirclePack.cpb.msg("count -v: no vertices specified");
			    			return null;
			    		}
			    		CirclePack.cpb.msg("count of vertices: "+count);
			    		rtnCp=new CallPacket("count");
			    		rtnCp.int_vec=new Vector<Integer>();
			    		rtnCp.int_vec.add(count);
			    		return rtnCp;
		    		}
		    		} // end of switch
		    	  }
			}
			break;
		}
		case 'g':
		{
			// ========= gen_mark =====
				if (cmd.startsWith("gen_mark")) {
					boolean face_flag = false;
					NodeLink seedlist = null;
					int mx = 0;
					int last_vert=0;
					rtnCp=null;

					Iterator<Vector<String>> nextFlag = flagSegs.iterator();
					while (nextFlag.hasNext()
							&& (items = nextFlag.next()).size() > 0) {
						if (StringUtil.isFlag(items.elementAt(0))) {
							char c = items.get(0).charAt(1);
							items.remove(0);
							switch (c) {
							// Here's the specific parsing of flag itself;
							// default to 'c'
							case 'f': // mark faces
							{
								face_flag = true;
								break;
							}
							case 'm': // max
							{
								mx = Integer.parseInt((String)items.elementAt(0));
								if (mx < 1)
									mx = 0;
								items.remove(0);
							}
							} // end of flag switch

							if (items.size() > 0) // seeds follow
								seedlist = new NodeLink(packData, items);
						} // done handling a given flagged segment
						else
							seedlist = new NodeLink(packData, items);
					} // end of while

					// default to seed = 'alpha'
					if (seedlist == null) {
						seedlist = new NodeLink(packData);
						seedlist.add(packData.getAlpha());
					}

					if ((last_vert = packData.gen_mark(seedlist, mx, true)) <= 0
							|| last_vert > packData.nodeCount) {
						throw new ParserException("usage: [fm] {v..}");
					}
					mx = packData.getVertMark(last_vert);
					if (face_flag) {
						int Gmax = 0, g0, g1, g2, gmax, gmin, last_face = 0;
						for (int f = 1; f <= packData.faceCount; f++)
							packData.setFaceMark(f,0);
						for (int f = 1; f <= packData.faceCount; f++) {
							gmax = gmin = 0;
							int[] verts=packData.packDCEL.faces[f].getVerts();
							if ((g0 = packData.getVertMark(verts[0])) != 0
									&& (g1 = packData.getVertMark(verts[1])) != 0
									&& (g2 = packData.getVertMark(verts[2])) != 0) {
								gmax = g0;
								gmax = (g1 > gmax) ? g1 : gmax;
								gmax = (g2 > gmax) ? g2 : gmax;
								gmin = g0;
								gmin = (g1 < gmin) ? g1 : gmin;
								gmin = (g2 < gmin) ? g2 : gmin;
							}
							if (gmax > gmin)
								packData.setFaceMark(f,gmax - 1);
							else if (gmax != 0)
								packData.setFaceMark(f,gmin); // gmax=gmin
							if (packData.getFaceMark(f) > Gmax) {
								Gmax = packData.getFaceMark(f);
								last_face = f;
							}
						}
						if (last_face > 0) {
							CirclePack.cpb.msg("gen_mark: last face marked = "
									+ last_face + ", generation is " + Gmax);
							rtnCp = new CallPacket("last_face");
							rtnCp.int_vec = new Vector<Integer>();
							rtnCp.int_vec.add(last_face);
						}
						return rtnCp;
					}
					if (last_vert > 0) {
						CirclePack.cpb.msg("gen_mark: last vertex marked = "
								+ last_vert + ", generation is " + mx);
						rtnCp = new CallPacket("last_vert");
						rtnCp.int_vec = new Vector<Integer>();
						rtnCp.int_vec.add(last_vert);
					}
					return rtnCp;
				}
				break;
		}
		case 'q':
		{
			// ========= quality ====== 
		    if (cmd.startsWith("qual")) {
		    	// results are processed by calling routine
		    	
		    	// default: '-v {e..}', worst edge visual error (eucl/hyp only)
		    	// '-n', NaN in some center or radius
		    	// '-r {e..}', worst edge relative error
		    	// '-o {f..}', orientation error
		    	// '-a {v..}', worst and average angle sum error
		    	// '-e {v..}', worst relative effective radius error, |rad - effrad|/rad.
		    	// lists default to 'all'

		    	// only one flagSeg is allowed; handle default situations first
		    	items=null;
		    	char c='v'; // default to 'visual'
		    	if (flagSegs!=null && flagSegs.size()>0 && (items=flagSegs.get(0))!=null &&
		    			items.size()>0 && StringUtil.isFlag(items.get(0)))
		    		c=items.remove(0).charAt(1);
		    	if (items==null) {
		    		items=new Vector<String>();
		    		items.add("a"); // default to all
		    	}
		    		
	    		switch(c) {
	    		case 'n': 
	    		{
	    			for (int v=1;v<=packData.nodeCount;v++) {
	    				if (Double.isNaN(packData.getRadius(v))) {
	    					rtnCp = new CallPacket("qual");
							rtnCp.strValue="nr";
							rtnCp.int_vec = new Vector<Integer>(1);
							rtnCp.int_vec.add(Integer.valueOf(v));
							rtnCp.error=true;
							return rtnCp;
	    				}
	    				if (Complex.isNaN(packData.getCenter(v))) {
	    					rtnCp = new CallPacket("qual");
							rtnCp.strValue = "nc";
							rtnCp.int_vec = new Vector<Integer>();
							rtnCp.int_vec.add(Integer.valueOf(v));
							rtnCp.error=true;
							return rtnCp;
	    				}
	    			}
	    			
	    			// reaching here, no NaN problems
					rtnCp = new CallPacket("qual");
	    			rtnCp.strValue="n";
					rtnCp.error=false;
	    			return rtnCp;
	    		}
	    		case 'o':
	    		{
	    			FaceLink flk=new FaceLink(packData,items);
	    			int f=QualMeasures.badOrientation(packData,flk);
	    			if (f<0)
	    				throw new CombException("orientation check ran into error");
					rtnCp = new CallPacket("qual");
	   				rtnCp.strValue="o";
	    			if (f==0) {
	    				rtnCp.error=false; // no problem faces
	    				return rtnCp;
	    			}
	    			rtnCp.int_vec=new Vector<Integer>(1);
	    			rtnCp.int_vec.add(Integer.valueOf(f));
	    			rtnCp.error=true;
	    			return rtnCp;
	    		}
	    		case 'a': // anglesum error
	    		{
	    			NodeLink nlink=new NodeLink(packData,items);
	    			double angsumerr=0.0;
	    			int vert=-1;
	    			packData.fillcurves();
	    			Iterator<Integer> nlst=nlink.iterator();
	    			while(nlst.hasNext()) {
	    				int v=nlst.next();
	    				if (packData.getAim(v)>0) {
	    					double diff=Math.abs(packData.getCurv(v)-packData.getAim(v));
	    					angsumerr=(diff>angsumerr) ? diff:angsumerr;
	    					vert=v;
	    				}
	    			}
					rtnCp = new CallPacket("qual");
	    			rtnCp.strValue="a";
	    			rtnCp.error=true; // no valid case found
	    			if (vert>0) {
	    				rtnCp.double_vec=new Vector<Double>(1);
	    				rtnCp.double_vec.add(Double.valueOf(angsumerr));
	    				rtnCp.int_vec=new Vector<Integer>(1);
	    				rtnCp.int_vec.add(Integer.valueOf(vert));
	    				rtnCp.error=false;
	    			}
	    			else
	    				CirclePack.cpb.msg("No angsum-aim comparisons could be computed");
	    			return rtnCp;
	    		}
	    		case 'e': // error in effective radii -- not yet implemented
	    		{
					rtnCp = new CallPacket("qual");
	    			rtnCp.strValue="e";
	    			rtnCp.error=false;
	    			return rtnCp;
	    		}
	    		case 'r':
	    		{
					rtnCp = new CallPacket("qual");
					rtnCp.strValue="r";
	    			EdgeLink elt=new EdgeLink(packData,items);
	    			if (packData.hes<0) { // keep only those with interior ends
	    				Iterator<EdgeSimple> els=elt.iterator();
	    				EdgeLink newelt=new EdgeLink(packData);
	    				while (els.hasNext()) { 
	    					EdgeSimple edge=els.next();
	    					if (!packData.isBdry(edge.v) &&	!packData.isBdry(edge.w))
	    						newelt.add(edge);
	    				}
	    				elt=newelt;
	    			}
	    			if (elt.size()==0) {
	    				CirclePack.cpb.msg("Quality, relative error: no valid edges to compute");
	    				rtnCp.error=true;
	    				return rtnCp;
	    			}
	    			UtilPacket uP=new UtilPacket();
	    			EdgeSimple worstedge=QualMeasures.worst_rel_err(packData, elt, uP);
	    			if (worstedge==null)
	    				throw new DataException("exception in measuring visual error");
					rtnCp.int_vec = new Vector<Integer>(2);
					rtnCp.int_vec.add(Integer.valueOf(worstedge.v));
					rtnCp.int_vec.add(Integer.valueOf(worstedge.w));
					rtnCp.double_vec=new Vector<Double>(1);
					rtnCp.double_vec.add(Double.valueOf(uP.value));
					rtnCp.error=false;
					return rtnCp;
	    		}
	    		default: // 'v' for visual error
	    		{
					rtnCp = new CallPacket("qual");
					rtnCp.strValue="v";
	    			UtilPacket uP=new UtilPacket();
	    			HalfLink elt=new HalfLink(packData,items);
	    			HalfEdge worstedge=QualMeasures.visualErrMax(packData, elt, uP);
	    			if (worstedge==null)
	    				throw new DataException("error in measuring visual error");
	    			if (uP.rtnFlag<0) { // some radii too small?
	    				rtnCp.strValue="v -- some radii too small to use";
	    			}
					rtnCp.int_vec = new Vector<Integer>();
					rtnCp.int_vec.add(Integer.valueOf(worstedge.origin.vertIndx));
					rtnCp.int_vec.add(Integer.valueOf(worstedge.twin.origin.vertIndx));
					rtnCp.double_vec=new Vector<Double>(1);
					rtnCp.double_vec.add(Double.valueOf(uP.value));
					rtnCp.error=false;
					return rtnCp;
	    		}
		    	} // end of switch
		    		
		    } // end of 'qual' processing
		    
			break;
		}
		
		} // end of switch
	} // end of calls needing packing
	
	// ============ second: other commands (or earlier fall throughs) =====
	switch(cmd.charAt(0)) {
	case 'a':
	{
		break;
	}
	case '?': // return first string of query result, 
			// and then only if it's a number or true/false.
	{
		String query=cmd.substring(1);
		if (query.length()<=0)
			return null;
		
		// we only use first string from 'ans'
		String ans=StringUtil.grabNext(QueryParser.
				queryParse(packData, query, flagSegs, false));
		if (ans==null || ans.length()==0)
			return null;
		
		// only return via 'strValue' if 'ans' represents a 
		//   double (or integer) or true/false.
		rtnCp=new CallPacket(query);
		try {
			Double.valueOf(ans); // if not a double, should throw exception here
			rtnCp.strValue=new String(ans);
			return rtnCp;
		} catch(Exception ex) {
			if (ans.equalsIgnoreCase("true") || 
					ans.equalsIgnoreCase("false")) {
				rtnCp.strValue=new String(ans);
				return rtnCp;
			}
		}
		
		return null; // didn't get number or true/false
		
	}
	} // end of switch

	return rtnCp;
}
	
} // end of 'CommandStrParser' class


/** =================== local utility classes ========================
/** for use with 'adjoin' */
class Overlap {
  int v,w;
  double angle;
  Overlap next;
}

/** for use with 'set_overlap': parse the <a> {(v,u)..} strings */
class LapList {
	boolean invDist_flag=false; // indication: inv_dist or overlap 
	double angle;
	EdgeLink edgelist;
}

