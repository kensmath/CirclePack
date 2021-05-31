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

import JNI.DelaunayBuilder;
import JNI.DelaunayData;
import JNI.JNIinit;
import allMains.CPBase;
import allMains.CirclePack;
import canvasses.ActiveWrapper;
import canvasses.CursorCtrl;
import canvasses.DisplayParser;
import canvasses.MainFrame;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import complex.Complex;
import complex.MathComplex;
import cpContributed.BoundaryValueProblems;
import cpContributed.CurvFlow;
import cpContributed.FracBranching;
import dcel.CombDCEL;
import dcel.DataDCEL;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RawDCEL;
import dcel.RedHEdge;
import dcel.Vertex;
import deBugging.DCELdebug;
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.JNIException;
import exceptions.LayoutException;
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
import ftnTheory.FlattenTri;
import ftnTheory.FlipStrategy;
import ftnTheory.GenBranching;
import ftnTheory.Graphene;
import ftnTheory.HarmonicMap;
import ftnTheory.HexPlaten;
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
import ftnTheory.ShepherdCircles;
import ftnTheory.SphereLayout;
import ftnTheory.SpherePack;
import ftnTheory.TileColoring;
import ftnTheory.TorusEnergy;
import ftnTheory.TorusModulus;
import ftnTheory.WeldManager;
import ftnTheory.WordWalker;
import ftnTheory.iGame;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.NSpole;
import geometry.SphericalMath;
import komplex.CookieMonster;
import komplex.DualGraph;
import komplex.EdgeSimple;
import komplex.Embedder;
import komplex.Face;
import komplex.HexPaths;
import komplex.RedList;
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
import listManip.VertexMap;
import math.Matrix3D;
import math.Mobius;
import microLattice.MicroGrid;
import microLattice.Smoother;
import packQuality.QualMeasures;
import packing.PackCreation;
import packing.PackData;
import packing.PackExtender;
import packing.PackMethods;
import packing.RData;
import packing.RedChainer;
import packing.Schwarzian;
import panels.CPScreen;
import panels.ImagePanel;
import panels.OutPanel;
import panels.PathManager;
import posting.PostFactory;
import posting.PostParser;
import random.RandomTriangulation;
import rePack.EuclPacker;
import rePack.GOpacker;
import rePack.HypPacker;
import rePack.SphPacker;
import rePack.d_HypPacker;
import rePack.d_SphPacker;
import script.ScriptBundle;
import tiling.TileData;
import util.BuildPacket;
import util.CallPacket;
import util.DispFlags;
import util.PathBaryUtil;
import util.PathUtil;
import util.ResultPacket;
import util.SphView;
import util.StringUtil;
import util.UtilPacket;
import util.ViewBox;
import widgets.CreateSliderFrame;
import widgets.SliderFrame;

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
  public static final double LAYOUT_THRESHOLD=.00001; // for layouts based on quality
  
  /**
   * Send on to 'jexecute' using active packing.
   * @param s, command string (see limitations)
   * @return 0 on error or no action
   */
  public static int jexecute(String s) {
	  return jexecute(CirclePack.cpb.getActivePackData(),s);
  }
  
  /**
	 * This is where individual commands are analyzed and sent 
	 * to appropriate Java routines. 
	 * 
	 * Commands arriving are individual commands: preprocessing leaves
	 *   no ';' separators:
	 *   no 'for' or 'FOR' loops:
	 *   no 'delay's:
	 *   no [n] named commands to resolve:
	 *   no '!!' repeats to resolve:
	 *   no '|pe|' 'PackExtender' calls:
	 *   no '-p' flag (already caught):
	 *   
	 * This routine handles housekeeping, separating cmd and
	 * flag sequences, then catching a small number of certain
	 * commands. Note that there is a call to 'packExecute' 
	 * for commands requiring pack status. Commands not
	 * requiring pack status, are then processed in a switch 
	 * based on the command's first letter. 
	 * 
	 * @param PackData packData
	 * @param String cmdstr, cmd and flag sequences
	 * @return 0 on error or no action
	 */
  public static int jexecute(PackData packData, String cmdstr) {
	  if (cmdstr==null) return 0;
	  CPScreen cpScreen=packData.cpScreen;
	  int count=0;
	  String cmd=null;

	  if (cmdstr.contains("-p")) {
		  throw new ParserException("'jexecute': '-p' flag should have been handled before the call");
	  }

      // split off processing for query messages
      if (cmdstr.charAt(0)=='?') {
    	  return QueryParser.processQuery(packData,cmdstr,true);
      }
      
	  // Note: 'string2vec' catches variables, reconstitutes parens/brackets
	  Vector<String> allitems=StringUtil.string2vec(cmdstr,true);
	  cmd=(String)allitems.remove(0);
	  
	  /* NOTE: Vector 'flagSegs' will hold only the flag strings 
	   * occurring after the command --- the command itself is 'cmd' */
	  Vector<Vector<String>> flagSegs=StringUtil.flagSeg(allitems);
	  Vector<String> items=new Vector<String>(0);
      if (flagSegs.size()>0)
    	  items=flagSegs.get(0);

	  // 'fix' is deprecated
	  if (cmd.startsWith("fix")) cmd=new String("layout");

	  // "Cleanse" all packings
      if (cmd.startsWith("Clean")) { 
    	  for (int i=0;i<CPBase.NUM_PACKS;i++) {
    		  PackData pdata=CPBase.packings[i];
    		  CPScreen cps=CPBase.cpScreens[i];
    		  // TODO: want to white out underlying canvas to avoid flashing
    		  if (pdata.status) {
    			  pdata.packExtensions=new Vector<PackExtender>(2); // trash any extensions
    			  // put new packing in place
    			  PackData newP=CPBase.packings[i]=new PackData(i);
    			  newP.cpScreen=CPBase.cpScreens[i];
    			  newP.cpScreen.setPackData(newP);
    			  newP.cpScreen.updateXtenders();
    			  cps.emptyScreen();
    			  cps.updateXtenders();
    			  count++;
    		  }
    	  }
    	  PackControl.mapPairFrame.setTeleState(true);
    	  return 1;
      }
      
	  if (cmd.startsWith("act")) {
		  try {
			  int newpnum=Integer.parseInt(items.get(0));
			  if (newpnum<0 || newpnum>=CPBase.NUM_PACKS) return 0;
			  PackControl.switchActivePack(newpnum);
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
			  if (widehigh<PackControl.MinActiveSize) widehigh=PackControl.MinActiveSize;
			  if (widehigh>PackControl.MaxActiveSize) widehigh=PackControl.MaxActiveSize;
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
	   * First, check for certain commands with multiple names
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
		  if (sz==0) return 0;

		  // first, get the edges
    	  // file name should be last in last flag segment
		  String filename=flagSegs.lastElement().lastElement();
		  File dir=CPFileManager.PackingDirectory;
		  if (cmd.charAt(0)=='R') {
			  if (filename.startsWith("~/")) 
				  filename=new String(CPFileManager.HomeDirectory+File.separator+filename.substring(2));
			  dir=new File(filename);
			  filename=dir.getName();
			  dir=new File(dir.getParent());
		  }
		  
		  // -s flag means to read from script, -q{n} flag says to also
		  //     create its simple packing in pack n (though this depends
		  //     on the tiling's dual being trivalent).
    	  boolean script_flag=false;
    	  int simplepack=-1; // if this is set to legal pack, store simple pack there
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
							  CirclePack.cpb.errMsg("Failed to create simple packing in 'read_CT'");
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

		  // first, get the number of vertices
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
				  CirclePack.cpb.myErrorMsg("Usage: read_CT form is 'CHECKCOUNT: <n>'");
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
					  
					  // get the face with edge <v,w>; keep if v is the smallest index
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
		  
		  PackDCEL pDCEL = CombDCEL.getRawDCEL(bouquet);
		  int origVCount=pDCEL.vertCount;
		  FaceLink allfaces=new FaceLink();
		  ArrayList<dcel.Face> farray=new ArrayList<dcel.Face>(); 
		  for (int f=1;f<=pDCEL.intFaceCount;f++) 
			  farray.add(pDCEL.faces[f]);
		  if (pDCEL==null || RawDCEL.addBaryCents_raw(pDCEL,farray)==0) {
			  CirclePack.cpb.myErrorMsg("Failed to get initial DCEL, "+
					  "or failed to add barycenters to faces");
			  return 0;
		  }

		  // create a new 'PackData' from pDCEL

		  PackData newPack=DataDCEL.dcel_to_packing(pDCEL);
		  if (newPack!=null && newPack.status==true && newPack.nodeCount>3) {
			  CirclePack.cpb.msg("Have replaced packing with new "+
					  "one derived from '"+filename+"'.");
			  int pnum=packData.packNum;
			  CirclePack.cpb.swapPackData(newPack,pnum,false);
			  packData=newPack;
			  count=packData.nodeCount;
			  
			  try {
				  for (int i=1;i<=origVCount;i++)
					  newPack.setVertMark(i,1);
			  } catch (Exception ex) {
				  CirclePack.cpb.errMsg("error in marking network vertices");
			  }
			  
			  // 'pave' to get tilings
			  TileData td=TileData.paveMe(packData,origVCount+1);
    		  if (td==null) {
    			  CirclePack.cpb.errMsg("'pave' failed with new packing from 'read_CT'");
    			  return 0;
    		  }
    		  td.packData=packData;
    		  packData.tileData=td;
    		  CommandStrParser.jexecute(packData,"layout -F");
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
            			  CirclePack.cpb.errMsg("failed to create simple packing for p"+simplepack+" in 'read_CT'");
    			  } catch(CombException cex) {
    				  CirclePack.cpb.errMsg("The companion 'simple' packing in p"+simplepack+" has failed; "+
    						  "the tiling dual must be trivalent for this to work.");
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
				  filename=new String(CPFileManager.HomeDirectory+File.separator+filename.substring(2));
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
			  
	  // ====== readLite =========
	  if (cmd.startsWith("readL") || cmd.startsWith("infile_readL") ||
			  cmd.startsWith("ReadL")) {

		  int sz=items.size();
		  if (sz==0) return 0;
    	  boolean script_flag=false;
    	  if (cmd.charAt(0)=='i')  // catch "infile"
    		  script_flag=true;
    	  if (sz>1 && items.get(0).equals("-s")) {
    		  items.remove(0);
    		  script_flag=true;
    	  }

		  String filename=StringUtil.reconItem(items);
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
			  PackData p_from_lite= PackData.readLite(fp, filename);
			  if (p_from_lite!=null) {
				  fp.close();
				  p_from_lite.packNum=packData.packNum;
				  p_from_lite.cpScreen=packData.cpScreen;
				  packData=p_from_lite;
				  packData.cpScreen.setPackData(packData);
				  packData.cpScreen.setGeometry(packData.hes);
				  packData.status=true;
				  packData.setName(filename);
				  if (packData.getDispOptions != null)
					  CommandStrParser.jexecute(packData, "disp -wr");
				  return packData.nodeCount;
			  }
				
			  fp.close();
			  return 0;
		  } catch (IOException iox) {
			  try {
				  if (fp != null)
					  fp.close();
			  } catch (IOException e) {
			  }
			  throw new ParserException("trying to read PackLite " + filename + ": "
					  + iox.getMessage());
		  }
	  }		  
		  
	  // ====== read === infile_read ===== Read ===== 
	  else if (cmd.startsWith("read") || cmd.startsWith("infile_read") || 
			  cmd.startsWith("Read")) { // read a packing file
		  // in script with 'infile' or flag '-s'
		  int sz=items.size();
		  if (sz==0) return 0;
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
				int rslt = packData.readpack(fp, filename);
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
					CirclePack.cpb.errMsg("Some failure in reading "+filename+" as a triangulation");
				}
				
				fp.close();
				
				if (tri!=null)
					CirclePack.cpb.msg("Have read '" + filename+ "' as a triangulation");
				else {
					throw new InOutException("failed to read " + filename
						+ " as packing or triangulation. Check its format.");
				}

/* Old try to read as point set is fraught with potential problems, e.g. C library crash.

 				if (tri == null) {
					fp = CPFileManager.openReadFP(dir, filename, script_flag);
					UtilPacket uP = new UtilPacket();
					RandomTriangulation.readPoints(fp, uP);
					fp.close();
					if (uP.errval < 0)
						throw new InOutException("failed to read " + filename
								+ " as packing, triangulation, or point set");
					if (uP.rtnFlag == 3)
						hes = 1;
					try {
						tri=Triangulation.pts2triangulation(hes, uP.z_vec);
					} catch (Exception ex) {
						throw new InOutException("failed to read " + filename+ " as point set");
					}
					if (tri == null)
						throw new InOutException("failed to read " + filename
								+ " as packing, triangulation, or point set");
					CirclePack.cpb.errMsg("read '" + filename
							+ "' as a point set");
				}
*/				 
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
			  CirclePack.cpb.swapPackData(pdata,pnum,false);
			  packData=pdata;
			  
			  packData.chooseAlpha();
			  packData.chooseGamma();
			  packData.setCombinatorics();
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
							  packData.setCenter(i,new Complex(tri.nodes[i].x,tri.nodes[i].y));
					  }
				  }
			  }

			  packData.fillcurves();
			  packData.set_plotFlags();
              packData.cpScreen.reset();
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
	    	  int offset=0;

	    	  if (pnum1<0 || pnum1>=CPBase.NUM_PACKS 
	    			  || !CPBase.cpScreens[pnum1].getPackData().status 
	    			  || pnum2<0 || pnum2>=CPBase.NUM_PACKS 
	    			  || !CPBase.cpScreens[pnum2].getPackData().status) 
	    		  throw new ParserException("illegal or inactive packings specified");
	    	  packData=CPBase.cpScreens[pnum1].getPackData(); // this is where the final pack will go
	    	  PackData qackData=CPBase.cpScreens[pnum2].getPackData();
	    	  
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
	    		  if (packData.packDCEL!=null) {
	    			  PackDCEL pdc=packData.packDCEL;
	    			  HalfEdge he=pdc.vertices[v1].halfedge.twin.next;
	    			  HalfEdge startedge=he;
	    			  while (he.next.origin.vertIndx!=w && he!=startedge.prev) {
	    				  tick++;
	    				  he=he.next;
	    			  } 
	    			  N=tick;
	    		  }
	    		  
	    		  // traditional
	    		  else {
	    			  int safty=packData.nodeCount;
	    			  int ne=packData.getLastPetal(v1);
	    			  while (ne != v1 && safty>0) {
	    				  ne=packData.getLastPetal(ne);
	    				  tick++;
	    				  safty--;
	    			  }
	    			  if (safty<=0) 
	    				  throw new ParserException("emergency exit");
	    			  N=tick;
	    		  }
	    	  }
	    	  else // n is given
	    		  N=Integer.parseInt((String)items.get(4));
	    	  
	    	  // traditional: save overlaps
	    	  if (packData.packDCEL==null) {
		    	  if (pnum1!=pnum2) 
		    		  offset=packData.nodeCount;
		    	  boolean overlap_flag=false;
		    	  if (packData.overlapStatus || qackData.overlapStatus)
		    		  overlap_flag=true;
		  	  
		    	  // save pnum1 overlaps
		    	  Overlap overlaps=null;
		    	  Overlap trace=null;
				  double angle;
		    	  if (packData.overlapStatus) {
		    		  overlaps=new Overlap();
		    		  trace=overlaps;
		    		  for (int v=1;v<=packData.nodeCount;v++) {
		    			  int[] flower=packData.getFlower(v);
		    			  for (int j=0;j<(packData.countFaces(v)+packData.getBdryFlag(v));j++) {
		    				  // only store for petals with larger indices
		    				  if (v<flower[j]
		    				     && (angle=packData.getInvDist(v,flower[j]))!=1.0 ) {
		    					  trace.v=v;
		    					  trace.w=flower[j];
		    					  trace.angle=angle;
		    					  trace=trace.next=new Overlap();
		    				  }
		    			  }
		    		  }
		    	  }
	    		  
	    	  }
	    	  
	    	  // call to adjoin
	    	  PackData newPack=PackData.adjoinCall(packData, qackData, v1, v2, N);
	    	  
	    	  if (newPack==null) {
    			  CirclePack.cpb.errMsg("'adjoin' failed: ");
    			  return 0;
	    	  }
	    	  
	    	  int ans=CirclePack.cpb.swapPackData(newPack,pnum1,false);

			  return 1;
	      } // e)nd of 'adjoin'
	      
	      // =========== affpack ===========
	      else if(cmd.startsWith("affp")) {
	    	  // 1 or 2 doubles as side-pairing factors
    		  ArrayList<Double> ftrs=new ArrayList<Double>();
	    	  if (flagSegs!=null && flagSegs.size()>0) {
	    		  items=(Vector<String>)flagSegs.get(0);
	    		  try {
	    			  while (items.size()>0) {
	    				  ftrs.add(Double.parseDouble(items.remove(0)));
	    			  }
	    		  } catch(Exception ex) {
	    			  throw new ParserException("Usage: affpack [a [b]]");
	    		  }
	    	  }
	    	  int n=ftrs.size();
	    	  if (n>2) // get at most 2 doubles
	    		  n=2;
	    	  double[] factors=new double[2];
	    	  for (int i=0;i<n;i++) 
	    		  factors[i]=ftrs.get(i);
	    	  
	    	  // now try the affine packing
	    	  return ProjStruct.affinePack(packData,factors,0);
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
				  if (tryfile.exists()) // if "scripts" exists under new directory, change to it 
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
			  newP.cpScreen=CPBase.cpScreens[pnum];
			  newP.cpScreen.setPackData(newP);
			  newP.cpScreen.emptyScreen();
			  newP.cpScreen.updateXtenders();
			  
			  // point local 'packData' to new one 
			  packData=newP;
			  return 1;
	      }
	      
		  // ========== close ===========
		  if (cmd.startsWith("close")) {
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
				  else if (windStr.startsWith("pair") || windStr.startsWith("map")) { // dual screen mode
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
					  if (PackControl.functionPanel.isLocked()) {
						  PackControl.functionPanel.loadHover();
						  PackControl.functionPanel.lockedFrame.setVisible(false);
					  }
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
			  int param=1;
			  int []pinParam=new int[2];
			  String type = null;
			  try {
				  type=items.remove(0);
				  try {
					  param=Integer.parseInt(items.remove(0)); // get number first
				  } catch (Exception ex) {
					  param=1;
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
				  else if (type.startsWith("pin") || type.startsWith("Pin")) { // pinwheel
					  mode=10;
					  pinParam[0]=param; // use one we already have as backup
					  try {
						  pinParam[0]=Integer.parseInt(items.remove(0));
						  pinParam[1]=Integer.parseInt(items.remove(0));
					  } catch(Exception ex) {
						  pinParam[1]=3;
					  }
				  }
				  else if (type.startsWith("fib") || type.startsWith("Fib")) {
					  mode=11;
				  }
				  else if (type.startsWith("tetra") || type.startsWith("Tetra")) {
					  mode=13;
				  }
			  } catch (Exception ex) {
				  throw new ParserException("usage: create "+type+" {n}");
			  }

			  if (param<1)
				  throw new ParserException("usage: create "+type+" {n}, n must be at least 1");
			  
			  PackData newPack=null;
			  switch (mode) {
			  case 1: // seed
			  {
				  newPack=PackCreation.seed(param,0);
				  break;
			  }
			  case 2: // hex/Hex
			  {
				  if (type.charAt(0)=='h' && param>100) {
					  throw new DataException("Use 'Hex' (cap 'H') for more than 100 generations");
				  }
				  newPack=PackCreation.hexBuild(param);
				  break;
			  }
			  case 3: // square grid
			  {
				  if (type.charAt(0)=='s' && param>8) {
					  throw new DataException("Use 'Sq_grid' (cap 'S') for more than 8 generations");
				  }
				  newPack=PackCreation.squareGrid(param);
				  break;
			  }
			  case 4: // chair, with 'TileData'
			  {
				  if (type.charAt(0)=='c' && param>8) {
					  throw new DataException("Use 'Chair' (cap 'C') for more than 8 generations");
				  }
				  newPack=PackCreation.chairTiling(param);
				  break;
			  }
			  case 5: // triangle group
			  {
				  if (type.charAt(0)=='t' && param>15) {
					  throw new DataException("Use 'Tri_group' (cap 'T') for more than 15 generations");
				  }
				  double a=2.0;
				  double b=3.0;
				  double c=7.0;  
				  try {
					  a=Double.parseDouble(items.get(0));
					  b=Double.parseDouble(items.get(1));
					  c=Double.parseDouble(items.get(2));
				  } catch (Exception ex) {} // go with defaults
				  // set degrees: params should be (at worst) half ints
				  if (Math.abs(2.0*a-(int)(2.0*a))>.0001 ||
						  Math.abs(2.0*b-(int)(2.0*b))>.0001 ||
						  Math.abs(2.0*c-(int)(2.0*c))>.0001)
					  throw new DataException("usage: create tri_group: paremeters must be form n/2");
				  if (Math.abs(2.0*((int)a)-2.0*a)>.1) {
					  if (((int)b-(int)c)>0.1) 
						  throw new DataException("usage: create tri_group: 'a' is half-integer, but b, c not equal");
				  }
				  else if (Math.abs(2.0*((int)b)-2.0*b)>.1) {
					  if (((int)a-(int)c)>0.1) 
						  throw new DataException("usage: create tri_group: 'b' is half-integer, but a, c not equal");
				  }
				  if (Math.abs(2.0*((int)c)-2.0*c)>.1) {
					  if (((int)b-(int)a)>0.1) 
						  throw new DataException("usage: create tri_group: 'c' is half-integer, but a, b not equal");
				  }
				  int A=(int)(2.01*a);
				  int B=(int)(2.01*b);
				  int C=(int)(2.01*c);

				  newPack=PackCreation.triGroup(A,B,C,param);
				  break;
			  }
			  case 6: // pentagonal tiling, with 'TileData'
			  {
				  newPack=PackCreation.pentTiling(param);
				  break;
			  }
			  case 7: // pentagonal triple point, with 'TileData'
			  {
				  newPack=PackCreation.pent3Expander(param);
				  break;
			  }
			  case 8: // pentagonal quadruple point, with 'TileData'
			  {
				  newPack=PackCreation.pent4Expander(param);
				  break;
			  }
			  case 9: // dyadic (hyp penrose), with 'TileData'
			  {
				  newPack=PackCreation.pentHypTiling(param);
				  break;
			  }
			  case 10: // pinwheel: end/hypotenuse lengths pinParam[0]/[1]; with 'TileData'
			  {
				  if (type.charAt(0)=='p' && param>8) {
					  throw new DataException("Use 'Pinwheel' (cap 'P') for more than 8 generations");
				  }
				  newPack=PackCreation.pinWheel(param,pinParam[0],pinParam[1]);
				  break;
			  }
			  case 11: // fibonnacci 2D: W, H, X, width/height/base, with 'TileData'
			  {
				  if (type.charAt(0)=='f' && param>8) {
					  throw new DataException("Use 'Fib2d' (cap 'F') for more than 8 generations");
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
			  } // end of switch

			  if (newPack==null) {
				  throw new ParserException("failed to create "+type+" packing");
			  }
			  
			  newPack.status=true;
			  int pnum=packData.packNum;
			  CirclePack.cpb.swapPackData(newPack,pnum,false);
			  packData=newPack;
			  count=packData.nodeCount;

			  return count;
		  }
		  break;
	  } // end of 'c'
	  case 'd':
	  {
		  if (cmd.startsWith("dualG")) {
			  packData.dualGraph=DualGraph.buildDualGraph(packData,packData.firstFace,null);
		  }
		  
		  // ========= delaunay (this is 2D) ==========
		  else if (cmd.startsWith("delaun") || cmd.startsWith("Delaun")) {
			  int N=0;
			  String str;
			  boolean script_flag=false; // data from script?
			  boolean fromFile=false; // data in some file?
			  boolean cpack=false; // from packing centers?
			  Vector<Complex> pts=null;
			  UtilPacket uP=new UtilPacket();
			  int geom=100; // intended geometry
			  
			  if (!JNIinit.DelaunayStatus()) {
				  throw new JNIException("'delaunay' requires the 'DelaunayBuild' library, which is not installed");
			  }
			  
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
								  throw new ParserException("'f' and 's' flags conflict with 'c' flag");
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
								  throw new ParserException("'c' flag conflicts with 'f','s' flags");
							  if (!packData.status)
								  throw new ParserException("packing status is false");
							  cpack=true;
							  geom=packData.hes;
							  pts=new Vector<Complex>(packData.nodeCount+1);
							  for (int v=1;v<=packData.nodeCount;v++) { 
								  if (str.contains("m")) {
									  if (PackControl.functionPanel.ftnField.getText().trim().length()==0)
										  throw new ParserException("No specification in function frame");
									  pts.add(PackControl.functionPanel.getFtnValue(packData.getCenter(v)));
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
			  if (geom==1 && uP.rtnFlag==1) { // unit square pts? must put in (theta,phi) form
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
			  CirclePack.cpb.swapPackData(randPack,pnum,false);
			  packData=randPack;
			  
			  packData.chooseAlpha();
			  packData.chooseGamma();
			  packData.setCombinatorics();
			  packData.set_aim_default();
			  packData.set_rad_default();
			  for (int v=1;v<=packData.nodeCount;v++) 
				  packData.kData[v].plotFlag=1;
			  return N;
		  } // end of 'delaunay'
		  
		  else if (cmd.startsWith("debug")) {
			  char flag='d'; // default
			  try {
//				  items=(Vector<String>)flagSegs.get(0); // flag
				  if (StringUtil.isFlag(items.get(0)))
					  flag=items.get(0).charAt(1);
				  else flag=items.get(0).charAt(0);
		    	  
			  } catch(Exception ex) {
				  flag='d';
			  }
			  
	    	  switch(flag) {
	    	  case 'r': // log_RedList
	    	  {
	    		  LayoutBugs.log_RedList(packData,packData.redChain);
	    		  return 1;
	    	  }
	    	  case 'd': // face drawing order
	    	  {
	    		  LayoutBugs.log_faceOrder(packData);
	    		  return 1;
	    	  }
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
	    		  PackControl.scriptManager.debugLayoutRecurse(PackControl.scriptManager.cpScriptNode);
	    		  PackControl.scriptManager.cpDataNode.debugSize();
	    		  PackControl.scriptManager.debugLayoutRecurse(PackControl.scriptManager.cpDataNode);
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
	    				  CirclePack.cpb.myErrorMsg("Pack "+packData.packNum+" has no extensions");
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
	    			  
	    			  cpScreen.updateXtenders();
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
	    			  cpScreen.updateXtenders();
	    		  }
	    		  
	    		  // may be no flag, just the abbreviation
	    		  else if (stg!=null && stg.charAt(0)!='-') {
	    			  str=stg;
	    		  }

	    	  } // end of while for flags
	    		  
	    	  // ************ hard coded abbreviations ***********
	    	  if (str!=null) {
	    	  if (str.equalsIgnoreCase("bvp")) {
			   	  if (!packData.status || packData.nodeCount==0) return 0;
			   	  BoundaryValueProblems px=new BoundaryValueProblems(packData);
			   	  if (px.running) {
				   	  CirclePack.cpb.msg("Pack "+packData.packNum+
				   			  ": started "+px.extensionAbbrev+" extender");
			    	  px.StartUpMsg();
			    	  returnVal=1;
				     }
			  }
	    	  else if (str.equalsIgnoreCase("bf")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
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
	    				  throw new ParserException("'extender' mg call needs packing or "+
	    						  "file info");
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
	    						  ": started "+px.extensionAbbrev+" extender, mode 1");
	    				  px.StartUpMsg();
	    				  returnVal=1;
	    			  }
	    		  }
	    		  // TODO: mode 2 is new (2/2020) and under development
	    		  else if (StringUtil.qFlagParse(srpt)>=0) {
	    			  script_flag=true;
	    			  int qnum=StringUtil.qFlagParse(srpt);
	    			  
	    			  MicroGrid px=new MicroGrid(packData,
	    					  CPBase.cpScreens[qnum].getPackData(),null,script_flag);
	    			  if (px.running) {
	    				  CirclePack.cpb.msg("Pack "+packData.packNum+
	    						  ": started "+px.extensionAbbrev+" extender, mode 2");
	    				  px.StartUpMsg();
	    				  returnVal=1;
	    			  }
	    		  }
			  }
	    	  else if (str.equalsIgnoreCase("hp")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  HexPlaten px=new HexPlaten(packData);
	    		  if (px.running) {
				   	  CirclePack.cpb.msg("Pack "+packData.packNum+
				   			  ": started "+px.extensionAbbrev+" extender");
			    	  px.StartUpMsg();
			    	  returnVal=1;
				  }
			  }
	    	  else if (str.equalsIgnoreCase("ft")) { // FlattenTri
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  FlattenTri ft=new FlattenTri(packData);
	    		  if (ft.running) {
				   	  CirclePack.cpb.msg("Pack "+packData.packNum+
				   			  ": started "+ft.extensionAbbrev+" extender");
			    	  ft.StartUpMsg();
			    	  returnVal=1;
				  }
			  }
	    	  else if (str.equalsIgnoreCase("rh")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  PackExtender px=new RiemHilbert(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("fb")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  FracBranching px=new FracBranching(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("gp")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  Graphene px=new Graphene(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("pb")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  PolyBranching px=new PolyBranching(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("tc")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  TileColoring px=new TileColoring(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("cf")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  CurvFlow px=new CurvFlow(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ct")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  ConformalTiling px=new ConformalTiling(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }

	    	  }
	    	  else if (str.equalsIgnoreCase("TE")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  TorusEnergy px=new TorusEnergy(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("JP")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  JammedPack px=new JammedPack(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("PR")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  Percolation px=new Percolation(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("mmc")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  MeanMove px=new MeanMove(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ca")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  ComplexAnalysis px=new ComplexAnalysis(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ps")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  ProjStruct px=new ProjStruct(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("ap") || str.equalsIgnoreCase("ss")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  AffinePack px=new AffinePack(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("sm")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  SchwarzMap px=new SchwarzMap(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("gb")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  GenBranching px=new GenBranching(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("nk")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  Necklace px=new Necklace(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("fs")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  FlipStrategy px=new FlipStrategy(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("IG")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  iGame px=new iGame(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("rm")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
	    		  RationalMap px=new RationalMap(packData);
	    		  if (px.running) {
		    		  CirclePack.cpb.msg("Pack "+packData.packNum+
		    				  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  returnVal=1;
		    	  }
	    	  }
	    	  else if (str.equalsIgnoreCase("sp")) {
	    		  if (!packData.status || packData.nodeCount==0) return 0;
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
	    	  else if (str.equalsIgnoreCase("sc")) {
	    		  ShepherdCircles px=new ShepherdCircles(packData);
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
				  cpScreen.updateXtenders();

	    	  } // end of hard-coded cases
	    	  
	    	  // if nothing yet found, get from dialog
	    	  if (str==null || returnVal==0) {
    			  PackExtender px=null;
	    		  File extFile=FileDialogs.loadDialog(FileDialogs.EXTENDER,true);
	    		  if (extFile!=null) {
	    			  try {
	    	            @SuppressWarnings("unchecked")
	    	            Class<PackExtender> extClass = (Class<PackExtender>)
	    	            new circlePack.PackExtenderLoader().loadClass(extFile.getCanonicalPath());
	    	            
	    	            // start the new class.
	    	            // TODO: questions: how do I send an argument 'packData'?
	    	            px= extClass.getConstructor(packing.PackData.class).newInstance(packData);
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

	    		  if(px.running) {
	    			  CirclePack.cpb.msg("Pack "+packData.packNum+
	    					  ": started "+px.extensionAbbrev+" extender");
	    			  px.StartUpMsg();
	    			  cpScreen.updateXtenders();
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
	    			  ResultPacket rP=new ResultPacket(CirclePack.cpb.getActivePackData(),line);
	    			  CPBase.trafficCenter.parseCmdSeq(rP,0,null);
	    			  count+=Integer.valueOf(rP.cmdCount);
	    		  }
	    		  fp.close();
			  } catch (Exception ex) {
				  throw new ParserException("error in 'fexec' processing: "+ex.getMessage());
			  }
	          return count;
		  }
	      break;
	  } // end of 'f'
	  case 'g': // fall through
	  case 'G': 
	  {
		  
		  // flags: s=start, r=restart, c=continue, g=get rad/cent, q=quality
		  if (cmd.startsWith("GOpack")) {
			  
			  if (!JNIinit.SparseStatus()) {
				  CirclePack.cpb.errMsg("Don't use 'GOpack': C++ library for sparse matrix operations not available");
			  	  return 0;
			  }
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
						  throw new ParserException("GOpack usage: GOpack -v {v..}");
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
			  if (packData.rePacker==null || !(packData.rePacker instanceof GOpacker) ||
					  packData.rePacker.p==null || packData.rePacker.p!= packData ||
					  packData.nodeCount!=((GOpacker)packData.rePacker).getOrigNodeCount()) {  
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
				  CirclePack.cpb.msg("GOpacker: did "+ct+" passes, l2-error = "+goPack.myPLiteError);
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
							  CirclePack.cpb.msg("GOpack can set vertices only on initialization");
							  return 0;
						  }
						  
					  }
					  
					  // set all radii to 1, do no riffles yet
					  case 's':
					  {
						  goPack.startRiffle();
						  CirclePack.cpb.msg("GOpack is initialized, constant radii, no cycles yet");
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
								  throw new ParserException("GOpack usage: -n {n}, n iterations");
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
								  throw new ParserException("GOpack usage: -n {n}, n iterations");
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
						  if (goPack.setSphBdry()>0) // these sets bdry rad/centers (if there are 3 bdry verts)
							  goPack.setMode(GOpacker.FIXED_BDRY);
							  
						  if (items.size()>0) { // passes specified?
							  try {
								  passes=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("GOpack usage: -n {n}, n iterations");
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
							  throw new ParserException("GOpack usage: -h is only for euclidean packings");
						  gotflag=true;
						  gotc=c;
						  goPack.setMode(GOpacker.FIXED_BDRY);
						  
						  if (items.size()>0) { // passes specified?
							  try {
								  passes=Integer.parseInt(items.get(0));
							  } catch (Exception ex) {
								  throw new ParserException("GOpack usage: -n {n}, n iterations");
							  }
						  }
						  break;
					  }
					  // give 3 or more corners for polygonal packing; get 'passes' via -n flag
					  case 'b': 
					  {
						  int n=0;
						  try {
							  n=Integer.parseInt(str.substring(2));
							  if (n<3)
								  throw new ParserException("GOpack: usage -c {v1 v2 v3 ..}. Must be at least 3 corner verts");
							  int []pCorners=new int[n];
							  double []pAngles=null;
							  int m=items.size();
							  NodeLink crns=null;
							  if (m==1) { // must be something like 'Vlist'
								  crns=new NodeLink(packData,items);
								  if (crns!=null) {
									  m=crns.size();
									  if (m!=n)
										  throw new ParserException("GOpack usage: corner specification error");
									  for (int i=0;i<m;i++)
										  pCorners[i]=crns.get(i);
								  }
							  }
							  else if (m<n)
								  throw new ParserException("GOpack usage -c problem");

							  else {  // read n corner vertices and (possibly) n angles

								  for (int i=0;i<n;i++) 
									  pCorners[i]=Integer.parseInt(items.get(i));
								  // read n corner interior angles theta/pi (default to pi-2pi/n)
								  if (m>n) {
									  if (m<2*n)
										  throw new ParserException("GOpack usage -c problem");
									  pAngles=new double[n];
									  for (int i=0;i<n;i++)
										  pAngles[i]=Double.parseDouble(items.get(n+i));
								  }
							  }
							  
							  // set corner data up, mode to POLY_PACK
							  count +=goPack.setCorners(pCorners,pAngles);
							  
						  } catch (Exception ex) {
							  throw new ParserException("GOpack: usage -c, failure to read corner vert");
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
							  throw new ParserException("GOpack usage: -n {n}, n iterations");
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
							  CirclePack.cpb.msg("exiting 'GOpack', last quality reading: "+results);
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
					  CirclePack.cpb.msg("GOpack restarted using data from the packing, "+count+" cycles");
					  break;
				  }
				  case 'm':
				  {
					  count+=goPack.continueRiffle(passes);
					  CirclePack.cpb.msg("GOpack continued for max packing, "+count+" cycles");
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
					  CirclePack.cpb.msg("GOpack: harmonic layout of local 'centers' based on bdry and 'radii'");
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
	    	  PackData Hp=CPBase.cpScreens[0].getPackData();
	    	  PackData Gp=CPBase.cpScreens[1].getPackData();
	    	  
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
	    	  
	    	  RData []newRdata;
	    	  if ((newRdata=HarmonicMap.h_g_bar(Hp,Gp))==null) {
	    		  CirclePack.cpb.msg("h_g_bar failed");
	    		  return 0;
	    	  }
	    	  
	    	  // duplicate p0 in p2, then replace its RData by the new stuff
	    	  int holdPNum=packData.packNum;
	    	  PackData tmpPD=Hp.copyPackTo();
	    	  CirclePack.cpb.swapPackData(tmpPD,2,false);
			  if (holdPNum==2) packData=CPBase.cpScreens[2].getPackData();
			  CPBase.packings[2].rData=newRdata;
	    	  jexecute(CPBase.packings[2],"set_screen -a");
	    	  CirclePack.cpb.msg("h_g_bar: p2 contains the data for h+conj(g)");
	    	  return 1;
	      }
	      
	      // =========== h_g_add ===========
	      if (cmd.startsWith("h_g_add")) {
	    	  PackData Hp=CPBase.cpScreens[0].getPackData();
	    	  PackData Gp=CPBase.cpScreens[1].getPackData();
	    	  
	    	  // check: status? euclidean? same size? 
	    	  if (HarmonicMap.ck_size(Hp,Gp)==0) {
	    		  return 0;
	    	  }

	    	  RData []newRdata;
	    	  if ((newRdata=HarmonicMap.h_g_add(Hp,Gp))==null) {
	    		  CirclePack.cpb.msg("h_g_add failed");
	    		  return 0;
	    	  }
	    	  
	    	  // duplicate p0 in p2, then replace its RData by the new stuff
	    	  int holdPNum=packData.packNum;
	    	  PackData tmpPD=Hp.copyPackTo();
	    	  CirclePack.cpb.swapPackData(tmpPD,2,false);
			  if (holdPNum==2) packData=CPBase.packings[2];
			  tmpPD.rData=newRdata;
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
	      if (cmd.startsWith("j_ftn")) {
	    	  int n0=0,n1=0,maxsize=0;
	    	  try {
//		    	  items=(Vector<String>)flagSegs.get(0); // should be just one segment
	    		  n0=Integer.parseInt((String)items.get(0));
	    		  n1=Integer.parseInt((String)items.get(1));
	    		  maxsize=Integer.parseInt((String)items.get(2));
	    	  } catch(Exception ex) {
	    		  throw new ParserException("usage: n0, n1, desired orders,"+
	    	    	      " 'maxsize' limits size: "+ex.getMessage());
	    	  }
	    	  try {
	    		  int pnum=packData.packNum;
	    		  PackData newData=PackCreation.build_j_function(n0,n1,maxsize);
	    		  if (newData==null) 
	    			  throw new CombException("new packing failed");
	    		  CirclePack.cpb.swapPackData(newData,pnum,false);
	    		  packData=newData;
	    	  } catch(CombException cex) {
	    		  throw new ParserException("build failed.");
	    	  }
	    	  cpScreen.reset();
	    	  cpScreen.clearCanvas(true);
	    	  packData.setName("j_ftn_"+n0+"_"+n1);
	    	  packData.setGeometry(-1);
	    	  return 1;
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
						cpScreen.repaint();
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
	  case 'm': // fall through
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
				  p=CPBase.cpScreens[pnm].getPackData();
				  q=CPBase.cpScreens[qnm].getPackData();
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
				  CirclePack.cpb.swapPackData(pdata,pnum,false);
				  packData=pdata;
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
		  if (cmd.startsWith("open")) {
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
					  if (PackControl.msgHover.isLocked() && PackControl.msgHover.lockedFrame.getState() == JFrame.ICONIFIED)
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
					  if (PackControl.functionPanel.isLocked() && 
							  PackControl.functionPanel.lockedFrame.getState() == JFrame.ICONIFIED)
						  	PackControl.functionPanel.lockedFrame.setState(Frame.NORMAL);
					  else {
						  PackControl.functionPanel.lockframe();
						  PackControl.functionPanel.locked = true;
					  }
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
			  CPScreen qCPS=null;
			  try { // try to read (and remove) -q{p} flag
				  items=(Vector<String>)flagSegs.get(0);
				  if (items.size()==1)
					  flagSegs.remove(0);
				  else 
					  items.remove(0);
				  String st=(String)items.get(0);
				  qackData=PackControl.cpScreens[StringUtil.qFlagParse(st)].getPackData();
				  qCPS=qackData.cpScreen;
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
				  Vector<String> all=StringUtil.string2vec(packData.cpScreen.dispOptions.toString());
				  Vector<Vector<String>> flgseg=StringUtil.flagSeg(all);
				  count +=DisplayParser.dispParse(packData,qCPS,flgseg);
			  }
			  
			  // send for parsing/execution
			  else { 
				  count +=DisplayParser.dispParse(packData,qCPS,flagSegs);
			  }
			  if (count>0) 
				  PackControl.canvasRedrawer.paintMyCanvasses(qackData,false);
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
	    	  if (packData.hes>0 || packData.overlapStatus) {
	    		  CirclePack.cpb.errMsg("'perron' methods only apply to eucl/hyp "+
	    				  "packings without overlaps");
	    		  return 0;
	    	  }
	    	  
	    	  boolean SparseC=true;
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
	    			  case 'n': // -noC flag? use Java code
	    			  {
	    				  if(str.contains("noC"))
	    					  SparseC=false;
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

	    	  // use C++ code?
	    	  if (!JNIinit.SparseStatus())
	    			  SparseC=false;
	    	  
	    	  double []perronResults=new double[4];
	    	  
	    	  // TODP: have not yet implemented C++ code
	    	  SparseC=false; 

	    	  if (packData.hes<0) {
	    		  if (SparseC) {
	    			  
	    		  }
	    		  else
	    			  perronResults=HypPacker.hypPerron(packData,direction, passes);
	    	  }
	    	  else { 
	    		  if (SparseC) {
	    			  
	    		  }
	    		  else 
	    			  perronResults=EuclPacker.euclPerron(packData,direction, passes);
	    	  }
	    	  
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
			  if (uP.rtnFlag!=0)
				  throw new ParserException("Points are not in unit square");
 			  // put data in vectors for native calls
 			  // NOTE: nodes indexed from 1
//			  int N=uP.z_vec.size();
// 			  double []xx=new double[N+1];
// 			  double []yy=new double[N+1];
//			  Point3D []nodes=new Point3D[N+1];
			  int hes=0;
			  if (uP.rtnFlag==3) // points should be (theta,phi)
				  hes=1; 
			  
			  DelaunayData dData=null;
			  try {
				  dData=new DelaunayData(hes,uP.z_vec);
				  dData=new DelaunayBuilder().apply(dData);
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
			  CirclePack.cpb.swapPackData(randPack,pnum,false);
			  packData=randPack;

			  packData.hes=heS;
			  packData.chooseAlpha();
			  packData.chooseGamma();
			  packData.setCombinatorics();
			  packData.set_aim_default();
			  packData.set_rad_default();

// OBE???
//			  for (int v=1;v<=packData.nodeCount;v++) 
//				  packData.kData[v].plotFlag=1;
			  return packData.nodeCount;
		  }
		  
		  // =========== rand_tri =========
		  if (cmd.startsWith("rand_tri") || cmd.startsWith("random_tri")) {
			  if (!JNIinit.DelaunayStatus()) {
				  throw new JNIException("call requires the 'DelaunayBuild' C++ library.");
			  }
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
					  if (items.size()>0) newFS.add(items);
				  }
				  if (newFS.size()==0) newFS=null;
			  }
			  
			  // now we've removed any -d flags; check for others
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
							  if (CPBase.ClosedPath!=null) Gamma=CPBase.ClosedPath;
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
								  throw new ParserException("usage: there must be a current path");
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
			  if (heS<=0 && aspect<=0 && Tau==null && Gamma==null) { // default case
				  if ((randPack=RandomTriangulation.randomHypKomplex(randN,seed1))==null) {
					  throw new CombException("Random disc packing has failed");
				  }
				  heS=-1;
			  }
			  else {
//				System.out.println("RANDTRI: into random_Tri");
				  
				  // TODO: when Gamma is given, should use "constrained" Delaunay so that bdry
				  //       edges are edges in the triangulation.
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
					  randPack.setCombinatorics();

					  // use 'cookie' to prune
					  CookieMonster cM=null;
					  int outcome=-1;
					  cM=new CookieMonster(randPack,"b");
					  outcome=cM.goCookie();
					  
					  // got new packing? swap it out
					  if (outcome>0) {
						  randPack=cM.getPackData();
					  }
								
					  randPack.poisonVerts=null;
					  randPack.poisonEdges=null;
				  } catch (Exception ex) {
					  throw new DataException("tri_to_Complex failed: "+ex.getMessage());
				  }
			  }
			  
			  // put new packing in place
			  randPack.fileName=new String("rand_pack");
			  int pnum=packData.packNum;
			  CirclePack.cpb.swapPackData(randPack,pnum,false);
			  packData=randPack;
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
					  packData.swap_nodes(v1,1);
					  packData.swap_nodes(v2,2);
					  packData.swap_nodes(v3,3);
					  packData.swap_nodes(v4,4);
				  }
			  }
			  
			  packData.setCombinatorics();
			  packData.facedraworder(false);
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
			  if (!JNIinit.DelaunayStatus()) {
				  throw new JNIException("requires the 'DelaunayBuild' C library, which is not loaded");
			  }
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
				  if (randN<4) randN=200;
			  } catch (Exception ex) {
				  randN=200;
			  }
			  
			  PackData randPack=null;
			  for (int j=0;j<12;j++) {
				  try {
					  if ((randPack=RandomTriangulation.randomHypKomplex(randN,seed1))!=null) {
			  
						  // choose alpha far from boundary
						  int da=randPack.gen_mark(new NodeLink(randPack,"b"),-1,false);
						  if (da>0)
							  randPack.setAlpha(da);
						  
						  // put new packing in place
						  int pnum=packData.packNum;
						  CirclePack.cpb.swapPackData(randPack,pnum,false);
						  packData=randPack;
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
		  
		  // =========== ring ======
		  else if (cmd.startsWith("ring")) {
			  if (flagSegs==null || flagSegs.size()==0 ||
					  (items=flagSegs.elementAt(0)).size()==0) {
				  throw new ParserException("check 'ring' usage");
			  }
			  NodeLink nodeLink=new NodeLink(packData,items);
			  
			  Iterator<Integer> nlk=nodeLink.iterator();
			  while (nlk.hasNext()) {
				  int v=nlk.next();
				  if (packData.ring_vert(v)>0 && packData.setCombinatorics()!=0)
					  count++;
			  }
			  return count;
		  }
		  
		  // =========== rld =======
		  else if (cmd.startsWith("rld")) { // repack, layout, disp
			  jexecute(packData,"repack");
			  jexecute(packData,"layout");
			  jexecute(packData,"disp -wr");
			  return 1;
		  }
		  
	      break;
	  } // end of 'r' and 'R'
	  case 's':
	  {
	      // ============== screendump ============
	      if (cmd.startsWith("screend")) {
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
			    				  ImagePanel.imageCount=(int)Math.abs(nbr);
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
	   		  	  	} // end of flag switch
	     		  }
	   		  	  // unflagged or fall-through: read 'n', number of petals (default 6) 
	   		  	  if (items.size()>item_index) {
	   		  	  	try {
	   		  	  		n=Double.valueOf(items.get(item_index)).intValue();
	   		  	  		if (n<3)
	   		  	  			n=3;
	   		  	  	} catch (Exception ex) {n=6;}
	   		  	  }
	     	  } // end of while
	   	  
	     	  try{  // have to hold this; packData get's replaced
	     		  int pnum=packData.packNum;
	     		  PackData newData=PackCreation.seed(n,hes);
	     		  if (newData==null) 
	     			  throw new CombException("seed has failed");
	     		  CirclePack.cpb.swapPackData(newData,pnum,false);
	     		  packData=newData;
	     		  jexecute(packData,"disp -w -c");
	     	  } catch(Exception ex) {
	     		  throw new ParserException(" "+ex.getMessage());
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
	    		  return PackControl.screenCtrlFrame.imagePanel.setIMG(items.get(0));
	    	  }
	      
	    	  // =========== set_display == (full computer screen)
	    	  if (cmd.startsWith("displ")) {
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
    		  PackData p1=CPBase.cpScreens[Integer.parseInt((String)items.get(0))].getPackData();
    		  NodeLink blist=new NodeLink(p1,"b");
    		  PackData p2=CPBase.cpScreens[Integer.parseInt((String)items.get(1))].getPackData();
    		  if (!p1.status || !p2.status || p1.hes>0 || p2.hes!=0
    				|| blist==null || blist.size()<=0) {
    			  throw new ParserException("need two appropriate packings.");
    		  }
    		  if (PackControl.functionPanel.ftnField.hasError()) {
    			  throw new ParserException("a valid function is needed in 'Function' tab.");
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
    			  Complex w=PackControl.functionPanel.getFtnValue(ctr);
    			  p2.setRadius(v,w.abs()*rad);
    			  count++;
    		  }
    		  return count;
    	  }

	      // ========= set_brush ============
    	  if (cmd.startsWith("brush")) {
	    	  int n=1;
    		  try {
//        		  items=(Vector<String>)flagSegs.get(0); // 
    			  String str=(String)items.get(0);
    			  n=Integer.parseInt(str);
    		  } catch(ParserException pex) {}
    		 if (n<0 || n>12) n=1; // 0-12 are values for LineThick slider in SupportFrame. 
    		 cpScreen.setLineThickness(n+1);
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
    					  cpScreen.sphView.defaultView();
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
    							  cpScreen.sphView.viewMatrix=mat3d;
    						  else return 0;
    					  } catch (Exception ex) {
    						  throw new ParserException("error setting 'viewMatrix'");
    					  }
    					  return 1;
    				  }
    				  if (c=='N') { // look directly at the origin, the north pole
    					  cpScreen.sphView.viewMatrix=
    							  Matrix3D.FromEulerAnglesXYZ(0.0,0.5*Math.PI,0.5*Math.PI);
    					  return 1;
    				  }
    				  else if (c=='S') { // look directly at infinity, the south pole
    					  cpScreen.sphView.viewMatrix=
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
    			  if (inc_flag) cpScreen.sphView.viewMatrix = 
    				  Matrix3D.times(trans,cpScreen.sphView.viewMatrix);
    			  else 
    				  cpScreen.sphView.viewMatrix=trans;
    			  return 1;
    		  }
			  throw new DataException("nan error in setting 'sphView'");
    	  }
    	  
          // =============== set_screen =====
    	  // Note: if 'packData.status' is true, parsing takes place in other routine
          if (cmd.startsWith("screen")) {
        	  ViewBox vbox=packData.cpScreen.realBox;
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
    				  // TODO: need tailored versions for speed, change 'CPScreen.update' too
        			  {
        				  double []corners=new double[4];
        				  try {
        					  for (int i=0;i<4;i++)
        						  corners[i]=Double.parseDouble(items.get(i+1));
        					  cpScreen.realBox.setView(new Complex(corners[0],corners[1]),
        							  new Complex(corners[2],corners[3]));
        				  } catch (Exception ex) {
        					  CirclePack.cpb.myErrorMsg("'"+cmd+"' parsing error.");
        					  return count;
        				  }
        				  count++;
        				  cpScreen.update(2);
        				  break;
        			  }
        			  case 'd':	// default canvas size, sphView
        			  {
        				  vbox.reset();
        				  count++;
        				  cpScreen.update(2);
        				  break;
        			  }
        			  case 'f': // scale by given factor
        			  {
        				  try {
        					  count += packData.cpScreen.realBox.scaleView(Double.parseDouble(items.get(1)));
        					  cpScreen.update(2);
        				  } catch (NumberFormatException nfe) {
        					  CirclePack.cpb.myErrorMsg("usage: set_screen -f <x>: "+nfe.getMessage());
        				  }
        				  break;
        			  }
        			  case 'i': // incremental moves (typically from mouse click)
        			  {
        				  try {
        					  utilz=new Complex(Double.parseDouble(items.get(1)),Double.parseDouble(items.get(2)));
        					  count +=vbox.transView(utilz);
        				  } catch (NumberFormatException nfe) {
        					  CirclePack.cpb.myErrorMsg("usage: set_screen -i <x> <y>: "+nfe.getMessage());
        				  }
        				  break;
        			  }
        			  case 'h': // height (or fall through for width)
        			  {}
        			  case 'w': // width
        			  {  	
        				  try {
        					  double f=Double.parseDouble(items.get(1));
        					  count += packData.cpScreen.realBox.scaleView(f/vbox.getWidth());
        					  cpScreen.update(2);
        				  } catch (NumberFormatException nfe) {
        					  CirclePack.cpb.myErrorMsg("usage: set_screen -w(or h) <x>: "+nfe.getMessage());
        				  }
        				  break;
        			  }
        			  } // end of flag switch
        		  } // done handling a given flag
        		  else { // no flags? default to default screen
        			  vbox.reset();
        			  count++;
        			  cpScreen.update(2);
        		  }
        	  } // end of while
    	  return count;
          }// done with 'set_screen' (with 'status' false)
    	  
    	  // ========= set_disp_flags =============
          if (cmd.startsWith("disp_str") || cmd.startsWith("disp_fla") || cmd.startsWith("disp_text")) {
        	  /* CirclePack sends a string to put in DispOptions of 
        	   * designated packing; if this is the active pack, the 
        	   * string is displayed as dispText and checkbox is set.
        	   */
        	  // Reconstitute the string from flag segments, with 
        	  //    separating spaces
        	  String flagstr=StringUtil.reconstitute(flagSegs);
        	  if (flagstr==null) return 0;
              cpScreen.dispOptions.usetext=true;
              cpScreen.dispOptions.tailored=flagstr;
              if (packData.packNum==CirclePack.cpb.getActivePackNum()) {
            	  PackControl.screenCtrlFrame.displayPanel.flagField.setText(
            			  cpScreen.dispOptions.tailored);
            	  PackControl.screenCtrlFrame.displayPanel.setFlagBox(true);
              }
              return 1;
          }
    	  
    	  // ========= set_function_text ===============
          if (cmd.startsWith("func") || cmd.startsWith("ftn")) {
        	  String functiontext=StringUtil.reconstitute(flagSegs);
      		  if (PackControl.functionPanel.setFunctionText(functiontext))
      			  return 1;
      		  return 0;
          }
    	  
    	  // ========= set_path_text ===============
          if (cmd.startsWith("path_tex")) {
        	  String pathtext=StringUtil.reconstitute(flagSegs);
      		  if (PackControl.functionPanel.setPathText(pathtext))
      			  return 1;
      		  return 0;
          }
          
	      // ======== set_path ===============
	      else if (cmd.startsWith("path")) {
	    	  // no optional string? use Function panel "Path" string
	    	  String ftnstr=PackControl.functionPanel.paramField.getText();
			  if (flagSegs!=null && flagSegs.size()!=0) {
				  String tmpstr=StringUtil.reconstitute(flagSegs);
				  ftnstr=StringUtil.varSub(tmpstr);
			  }
			  CPBase.ClosedPath=PackControl.functionPanel.setClosedPath(ftnstr);
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
        						  cent=new Complex(Double.parseDouble(items.get(0)),Double.parseDouble(items.get(1)));
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
        						  lowl=new Complex(Double.parseDouble(items.get(0)),Double.parseDouble(items.get(1)));
        						  upr=new Complex(Double.parseDouble(items.get(2)),Double.parseDouble(items.get(3)));
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
        					  CPBase.gridLines.addAll(PathBaryUtil.fromPath(packData,CPBase.ClosedPath));
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
        		  cpScreen.customPS=(String)flagSegs.get(0).get(0);
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
        	  cpScreen.setFillOpacity(opacity);
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
        	  cpScreen.setSphereOpacity(opacity);
        	  return 1;
          }	
          
          // ========== set_variable ================
          // NOTE: may be reconstructed from ":=" command, see 'TrafficCenter'
          
          // TODO: two forms, vname:=?querystring and vname:={..cmd..} should trigger
          //       calls to commands returning values, as in 'valueExecute'.
          if (cmd.startsWith("var")) {
        	  String vname=null;
        	  try {
//        		  items=flagSegs.get(0);
        		  vname=items.remove(0); // no white space allowed in name
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
          
          // ========== set_?list (VEFGT only) =========
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
		  // Note: if smoother is to work in concert with a 'MicroGrid', then it
		  //   is initiated in that 'MicroGrid'.
		  if (packData.smoother==null) { // try to create
			  // start the smoother
			  packData.smoother=new Smoother(packData,null); // no 'MicroGrid' attached
			  if (packData.smoother==null) {
				  CirclePack.cpb.errMsg("error: smoother failed to start for pack "+packData.packNum);
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
				  CirclePack.cpb.errMsg("usage: smoother -q{n} -a -b {b} -c {n] -d {flags} -r -s {x} -x");
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
		  int node=0;
    	  items=flagSegs.elementAt(0); // should be only one segment
   		  EdgeLink edgeLink=new EdgeLink(packData,items);
   		  
   		  if (edgeLink==null || edgeLink.size()==0) return 0;
   		  if ((node=packData.nodeCount+edgeLink.size()+1) > (packData.sizeLimit)
   				  && packData.alloc_pack_space(node,true)==0 ) {
   			  throw new DataException("Space allocation problem with adding vertices to edges.");
   		  }

   		  Iterator<EdgeSimple> elst=edgeLink.iterator();
   		  while (elst.hasNext()) {
   			  EdgeSimple edge=elst.next();
   			  if (packData.packDCEL!=null) {
   				  HalfEdge he=packData.packDCEL.findHalfEdge(edge);
   				  if (he==null)
					  CirclePack.cpb.errMsg("{"+edge.v+" "+edge.w+"} is not an edge");
   				  else {
   					  int vindx=RawDCEL.splitEdge_raw(packData.packDCEL,he);
   					  if (vindx>0) {
   						  packData.packDCEL.fixDCEL_raw(packData);
   						  count++;
   					  }
   				  }
   			  }
   			  else {
   				  if (packData.nghb(edge.v,edge.w)<0) { // not neighbors?
   					  CirclePack.cpb.errMsg("{"+edge.v+" "+edge.w+"} is not an edge");
   				  }
   				  else {
   					  int returnVal=packData.split_edge(edge.v, edge.w);
   					  if (returnVal>0) {
   						  packData.setCombinatorics();
   						  count++;
   					  }
   				  }
   			  }
   		  }
   		  return count;
	  }
	  
	  // ========= split_flower =============
	  if (cmd.startsWith("split_flo")) {
		  boolean merge=false;
		  int v,w;
		  int u=0;
		  try {
			  items=flagSegs.get(0);
			  if (StringUtil.isFlag(items.get(0))) {
				  String flg=items.remove(0);
				  if (flg.equals("-m"))
					  merge=true;
			  }
			  v=Integer.parseInt(items.get(0));
			  w=Integer.parseInt(items.get(1));
			  if (!merge)
				  u=Integer.parseInt(items.get(2));
		  } catch(Exception ex) {
			  throw new ParserException("usage: v u w or -m v w");
		  }
		  
		  int returnVal=0;
		  if (!merge) {
			  returnVal=packData.split_flower(v,w,u);
		  }
//		  else
//		  	  returnVal=packData.merge_vert(v,w);
		  
		  packData.setCombinatorics();
		  return returnVal;
	  }
   	  break;
  } // end of 's'
  case 'T': // "test" routines, meant to be temporary, developmental
  {
	  // ======= faceSurround ====
	  if (cmd.startsWith("T_islandSurround")) {
		  NodeLink beach=null;
		  try {
			  beach=new NodeLink(packData,flagSegs.get(0));
		  } catch(Exception ex) {
			  throw new ParserException("usage; T_islandSurround {v..}");
		  }
		  packData.flist=PackData.islandSurround(packData,beach);
		  if (packData.flist==null) {
			  CirclePack.cpb.errMsg("islandSurround failed");
			  return 0;
		  }
		  return 1;
	  }
	  
	  // ========= layout =============
	  else if (cmd.startsWith("T_layout")) {
		  
	  }

	  break;
  } // end of 'T'
  case 't':
  {
	  
	  // ========= timer ==========
	  if (cmd.startsWith("timer")) {
		  
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
		  double []tor=TorusModulus.torus_tau(packData);
		  if (tor!=null && tor[2]>0) {
			  
			  // display both 'tau' and '1/tau'
			  CirclePack.cpb.msg("torus_tau: modulus = ("+
					  String.format("%.6e",tor[0])+"+"+String.format("%.6e",tor[1])+"i)");
			  Complex taurecip=new Complex(tor[0],tor[1]).reciprocal(); 
			  CirclePack.cpb.msg("   (and 1/modulus = ("+
					  String.format("%.6e",taurecip.x)+"+"+String.format("%.6e",taurecip.y)+"i))");
			  return 1;
		  }
		  if (tor==null) 
			  throw new ParserException("error found by torus_t");
		  else if (Math.abs(tor[2]+1)<.4)
			  throw new ParserException("perhaps not torus by topology or side-pairings");
		  else if (Math.abs(tor[2]+2)<.4)
			  throw new ParserException("side-pairings must be computed");
	  }
	  
	  // ========= triG ===========
	  if (cmd.startsWith("triG")) {
		  double a=2.0;
		  double b=3.0;
		  double c=7.0;
		  int maxgen=5;
		  items=null;
		  try {
	      	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	      	  while (nextFlag.hasNext()) {
	      		  items=(Vector<String>)nextFlag.next();
	      		  String str=(String)items.get(0);
	      		  if (StringUtil.isFlag(str)) {
	      			  char ch=str.charAt(1);
	      			  switch(ch) {
	      			  // Note: triangle has angles pi/a, pi/b, pi/c
	      			  // by convention, a, b, c are (at worst) half integers
	      			  case 'd': { // read a b c 
	    		    	  a=Double.parseDouble((String)items.get(1));
	    		    	  b=Double.parseDouble((String)items.get(2));
	    		    	  c=Double.parseDouble((String)items.get(3));
	    		    	  break;
	      			  }
	      			  case 'g': { // number of generations
	      				  int mg=Integer.parseInt((String)items.get(1));
	      				  if (mg<1 || mg>1000) mg=10; // default
	      				  maxgen=mg;
	      				  break;
	      			  }
	      			  } // end of switch
	      		  }
	      		  else { // not flagged? default to a b c
    		    	  a=Double.parseDouble((String)items.get(0));
    		    	  b=Double.parseDouble((String)items.get(1));
    		    	  c=Double.parseDouble((String)items.get(2));
	      		  }
	      	  } // end of while
		  } catch (Exception ex) {
			  if (items!=null)
			  	throw new ParserException("usage: -d {a b c} -g {n}");
		  }
		  
		  // set degrees: params should be (at worst) half ints
		  if (Math.abs(2.0*a-(int)(2.0*a))>.0001 ||
				  Math.abs(2.0*b-(int)(2.0*b))>.0001 ||
				  Math.abs(2.0*c-(int)(2.0*c))>.0001)
			  throw new DataException("paremeters must be form n/2");
		  if (Math.abs(2.0*((int)a)-2.0*a)>.1) {
			  if (((int)b-(int)c)>0.1) 
				  throw new DataException("'a' is half-integer, but b, c not equal");
		  }
		  else if (Math.abs(2.0*((int)b)-2.0*b)>.1) {
			  if (((int)a-(int)c)>0.1) 
				  throw new DataException("'b' is half-integer, but a, c not equal");
		  }
		  if (Math.abs(2.0*((int)c)-2.0*c)>.1) {
			  if (((int)b-(int)a)>0.1) 
				  throw new DataException("'c' is half-integer, but a, b not equal");
		  }
//		  int []degs=new int[3]; 
		  int A=(int)(2.01*a);
		  int B=(int)(2.01*b);
		  int C=(int)(2.01*c);
		  
	   	  try{
	   		  // have to hold this; packData get's replaced
	   		  PackData newPack=PackCreation.triGroup(A,B,C,maxgen);
	   		  if (newPack!=null) {
	   			  int pnum=packData.packNum;
	   			  CirclePack.cpb.swapPackData(newPack,pnum,false);
	   			  packData=newPack;
	   			  return packData.nodeCount;
	   		  }
	   		  CirclePack.cpb.errMsg("triGroup failed to create a packing");
	   		  return 0;
	   	  } catch(Exception ex) {
	   		  throw new ParserException(" "+ex.getMessage());
	   	  }
	   	  
		  // geometry
/*			  int hees=-1; // default: hyp
		  double recipsum=1.0/a+1.0/b+1.0/c;
		  if (Math.abs(recipsum-1)<.0001)
			  hees=0; // eucl
		  else if (recipsum>1.0) hees=1; // sph
		  
		  int gencount=1;
		  // start seed
	   	  try{
	   		  // have to hold this; packData get's replaced
	   		  CPScreen cps=packData.cpScreen;  
	   		  count += cps.seed(degs[0],hees);
	   		  packData=cps.packData; 
	   	  } catch(Exception ex) {
	   		  throw new ParserException(" "+ex.getMessage());
	   	  }
	   	  // mark vertices of first flower
	   	  packData.setVertMark(1,0);
	   	  for (int j=2;j<=packData.nodeCount;j++) {
	   		  packData.setVertMark(j,(j)%2+1);
	   	  }
	   	  count++;
	   	  
	   	  // hyperbolic cases
		  while (hees<0 && gencount<=maxgen) { 
			  if (packData.bdryCompCount==0)
				  throw new CombException("no boundary verts at gencount = "+gencount);
			  int []alt=new int[2];
			  int w=packData.bdryStarts[1];
			  int stopv=packData.kData[w].flower[packData.getNum(w)];
			  int next=packData.kData[w].flower[0];
			  boolean wflag=false;
			  while (!wflag && count<10000) {
//				  System.err.println("gencount="+gencount+", working on w="+w+
//						  "; w's mark="+packData.getVertMark(w));
				  if (w==stopv) wflag=true;
				  int prev=packData.kData[w].flower[packData.getNum(w)];
				  int n=degs[packData.getVertMark(w)]-packData.getNum(w)-1;
				  if (n<-1)
					  throw new CombException("violated degree at vert "+w);

				  // add the n circles; two marks alternate around w
				  alt[0]=packData.getVertMark(prev);
				  int vec=(alt[0]-packData.getVertMark(w)+3)%3;
				  alt[1]=(alt[0]+vec)%3;
//				  System.out.println("w mark="+packData.getVertMark(w)+
//						  "; prev mark (alt[0])="+alt[0]+"; alt[1]="+alt[1]);
				  for (int i=1;i<=n;i++) { 
					  packData.add_vert(w);
					  packData.setVertMark(packData.nodeCount],alt[i%2]);
				  }
				  if (n==-1) { 
					  int xv=packData.close_up(w); // vertex removed?
					  if (xv>0 && xv<=stopv) // if yes, reset stopv
						  stopv--;
					  if (xv>0 && xv<=next) // may have to reset next, too
						  next--;
				  }
				  else packData.enfold(w);
				  packData.complex_count(true);
				  w=next;
				  next=packData.kData[w].flower[0];
				  count++;
			  } // end of while
			  
			  // debug
			  NodeLink nodelink=new NodeLink(packData,"b");
			  Iterator<Integer> nlink=nodelink.iterator();
//			  System.out.println("bdry verts, marks");
			  while (nlink.hasNext()) {
				  int dw=nlink.next();
//				  System.out.println("v "+dw+", "+packData.getVertMark(dw));
			  }
			  
			  
			  gencount++;
		  }
		  packData.setCombinatorics();
		  return count; */
	  }
	  // ========= toggle ========
/*	  if (cmd.startsWith("togg")) {
		  String window=null;
		  try {
			  items=flagSegs.get(0);
			  window=items.get(0);
		  } catch(Exception ex) {
			  window=new String("cp"); // default to 'PackContol' window
		  }
		  if (window.toLowerCase().contains("cp")) {
			  if (CPBase.frame.isVisible())
				  CPBase.frame.setVisible(false);
			  else
				  CPBase.frame.setVisible(true);
			  return 1;
		  }
		  else if (window.toLowerCase().contains("sc")) {
			  if (PackControl.scriptHover.isLocked()) {
				  PackControl.scriptHover.loadHover();
				  PackControl.scriptHover.lockedFrame.setVisible(false);
			  }
			  else { 
				  PackControl.scriptHover.lockframe();
			  }
			  return 1;
		  }
		  else if (window.toLowerCase().contains("msg")) {
			  // TODO: toggle the message window
			  return 1;
		  }
	  }
*/
	  
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
				  throw new InOutException("usage: write_custom ... -[fa] {filename}");
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
				  throw new InOutException("Failed to open '"+file.toString()+"' for custom writing");
		  
			  // there must be a flag indicating type of data
			  items=flagSegs.get(0);
			  if (items==null || items.size()==0 || !StringUtil.isFlag(items.get(0)))
				  throw new InOutException("'write_custom' must have a flag for type of output");
			  char c=items.remove(0).charAt(1);
			  switch(c) {
			  case 'G': // grid (meaning dual graph): for 3D printing grid output 11/2019
				  // TODO: only do euclidean case now
			  {
				  if (packData.hes!=0) 
					  throw new InOutException("'write_custom -G' currently for euclidean only");
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
					  Complex fcent=packData.face_center(fdex.next());
					  fp.append(String.format("%.6f",fcent.x)+"  "+String.format("%.6f",fcent.y)+"\n");
				  }
				
				  // now write the order pairs of indices, using new values
				  fp.append("\nEdge pairs: \n");
				  Iterator<EdgeSimple> dedges = dualedges.iterator();
				  while (dedges.hasNext()) {
					  EdgeSimple edge = (EdgeSimple) dedges.next();
					  fp.append(fhits[edge.v]+" "+fhits[edge.w]+"\n");
				  }
				  
				  fp.close();
				  count++;
			  }
			  } // end of switch

	    	  CirclePack.cpb.msg("Wrote custom -"+c+" to "+dir+
	    			  File.separator+file.getName());
	    	  
		  } catch (Exception ex) {
    		  throw new InOutException("write_custom failed: "+ex.getMessage());
		  }
	  }
	  
      // ========= write_path ========
	  else if (cmd.startsWith("write_path") || cmd.startsWith("Write_path")) {
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
    		  throw new InOutException("Failed to open '"+file.toString()+"' for writing");
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
    			  CPFileManager.CurrentDirectory+File.separator+file.getName());
    	  return 1;
      }
      
      // ========= write_tiling ========
      else if (cmd.startsWith("write_til") || cmd.startsWith("Write_til")) {
    	  if (packData.tileData==null || packData.tileData.tileCount<=0)
    		  throw new DataException("this packing does not have tiling data");
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
    		  throw new InOutException("Failed to open '"+file.toString()+"' for writing");
    	  try {
    		  int act=020000;
    		  packData.writePack(fp,act,script_flag);
    	  } catch (Exception ex) {
    		  throw new InOutException("write tiling failed: "+ex.getMessage());
    	  }
    	  if (script_flag) {
    		  CPBase.scriptManager.includeNewFile(file.getName());
    		  CirclePack.cpb.msg("Wrote tiling to "+
    				  file.getName()+" in the script");
    		  return 1;
    	  }
    	  CirclePack.cpb.msg("Wrote tiling to "+
    			  CPFileManager.CurrentDirectory+File.separator+file.getName());
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
   * internally called to handle packings with 'status' true.
   * @param packData @see PackData
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
	  CPScreen cpS=packData.cpScreen;
	  
	  // ============ get_data/put_data ===========
      if (cmd.startsWith("get_data") || cmd.startsWith("put_data")) {
    	  // need to pick off the source/target packing -q{p} and -t flag
    	  //  for 'translate', if it is there. Rest is parsed in 'dataPutGet'
    	  try {
    		  items=(Vector<String>)flagSegs.remove(0);
    	  } catch(Exception ex){ 
    		  return 0;
    	  }
    	  String str=(String)items.get(0);
    	  int qnum=StringUtil.qFlagParse(str);
    	  PackData q=null;
    	  if (qnum<0 || (q=CPBase.cpScreens[qnum].getPackData())==null || 
    			  !q.status) {
    		  throw new ParserException("usage: get_data must start with '-q{p}' indicating the "+
    				  "other packing");
    	  }
    	  items=(Vector<String>)flagSegs.get(0);
    	  str=(String)items.get(0);
    	  boolean translate=false;
    	  if (str.startsWith("-t")) {
    		  translate=true;
    		  flagSegs.remove(0);
    	  }
    	  boolean putget=true;
    	  if (cmd.startsWith("get")) putget=false;
    	  return packData.dataPutGet(q,flagSegs,putget,translate);
      }
      
      // ============ Mobius ===============
      if (cmd.startsWith("Mobius") || cmd.startsWith("inv_Mobius") ||
    		  cmd.startsWith("mobiu") || cmd.startsWith("inv_obiu")) {
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
    	  return packData.apply_Mobius(CPBase.Mob,vertlist,oriented,true,do_pairs);
      }
      
	  /* ================ main switch ============== */
	  switch (cmd.charAt(0)) {
	  case 'a':
	  {
	      // =========== alpha ============
	      if (cmd.startsWith("alpha")) {
	    	  int a=NodeLink.grab_one_vert(packData,flagSegs);
	    	  if (packData.packDCEL!=null)
	    		  return packData.packDCEL.setAlpha(a,null);
	    	  return packData.setAlpha(a);
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
	    		  items=(Vector<String>)flagSegs.get(0); // just one sequence: {x} {v..}
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
	    		  count +=packData.adjust_rad((Integer)vlist.next(), factor);
	    	  if (count<=0) {
	    		  CirclePack.cpb.myErrorMsg("adjust_radii: no radii were adjusted.");
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
	      		  vertlist=new NodeLink(packData,"B"); // all boundary starts
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
	    	  
	    	  // in vertex mode?? (default, look for further flags
	    	  if (addVert) {
	    		  try {
	    			  int v,w;
	    			  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
		      	  
	    			  // look for flags -b and/or -s first
	    			  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
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
	    									  w+" aren't on the same bdry component");
	    						  if (packData.packDCEL!=null) {
	    							  try {
	    								  count=RawDCEL.addIdeal_raw(packData.packDCEL, v, w);
	    								  if (count==0) 
	    									  throw new CombException("add failed");
	    								  packData.packDCEL.fixDCEL_raw(packData);
	    							  } catch(Exception ex) {
	    								  throw new DCELException("addIdeal failed: "+ex.getMessage());
	    							  }
	    							  packData.xyzpoint=null;
	    							  return count;
	    						  }
	    						  
	    						  // traditional
	    						  if (packData.ideal_bdry_node(v, w)!=0) {
	    							  packData.chooseAlpha();
	    							  packData.xyzpoint=null;
	    							  packData.setCombinatorics();
	    							  count++;
	    						  }
	    						  break;
	    					  }
	    					  default: // not a legal flag
	    					  {
	    						  throw new ParserException();
	    					  }
		      			  } // end of switch
	    				  } 
	    				  else { // no flag, just use vertices
	    					  vertlist=new NodeLink(packData,items);
	    					  count += packData.add_ideal(vertlist); // DCELdebug.redConsistency(packData.packDCEL);
	    					  return count;
	    				  }
	    			  } // end of while
	    		  } catch(Exception ex){
	    			  throw new ParserException("add_ideal: error added vertices; perhaps an undefined flag");	    		  
	    		  }
	    		  return count;
	    	  } // done with ideal vertex case
	    	  

	    	  // now handle ideal faces
	    	  // '-f' flag is gone, check for list of vertices, else default to all bdry components
	    	  if (flagSegs.get(0).size()==0) {
	      		  vertlist=new NodeLink(packData,"B"); // all boundary starts
	    	  }
	    	  else
	    		  vertlist=new NodeLink(packData,flagSegs.get(0));
	    	  
	    	  Iterator<Integer> vlst=vertlist.iterator();
	    	  while (vlst.hasNext()) {
	    		  int v=vlst.next();
	    		  if (packData.isBdry(v)) {
	    			  int ans=packData.add_ideal_face(v);
	    			  if (ans>0)
	    				  packData.setCombinatorics();
	    			  count++;
	    		  }
	    	  }
	    	  return count;
	      }
	      
	      // =========== add_cir =========
	      else if (cmd.startsWith("add_cir")) {
	    	  int v;
	    	  try {
	    		  items=flagSegs.elementAt(0); // should be only one segment
	    	  } catch(Exception ex) {
	    		  return 0;
	    	  }
	   		  NodeLink nodeLink=new NodeLink(packData,items);

   			  if ((packData.nodeCount + nodeLink.size()) > (packData.sizeLimit)
   					&& packData.alloc_pack_space(packData.nodeCount+1, true) == 0) 
   				  throw new CombException("Pack space allocation failure");
   			
	   		  Iterator<Integer> vlist=nodeLink.iterator();
   			  if (packData.packDCEL!=null) {
   				  int origVCount=packData.packDCEL.vertCount;
   		   		  while (vlist.hasNext()) {
   		   			  int w=vlist.next();
   		   			  Vertex vert=RawDCEL.addVert_raw(packData.packDCEL,w);
   		   			  if (vert!=null) {
   		   				  count++;
   		   				  packData.setRadius(vert.vertIndx,packData.getRadius(vert.vutil));
   		   			  }
  		   		  }
	   			  // process 
   		   		  if (count>0) {
   		   		  	  packData.packDCEL.fixDCEL_raw(packData);
   		   		  }
   		   		  return count;
   			  }

   			  // traditional
   			  while (vlist.hasNext()) {
	   			  v=(Integer)vlist.next();
	   			  count += packData.add_vert(v);
	   		  }
	   		  if (count>0) {
   				  packData.setCombinatorics();
	   			  packData.fillcurves();
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

	   		  while (vlist.hasNext()) {
	   			  int v=vlist.next();
	   			  if (!vlist.hasNext())
	   				  return count;
	   			  int w=vlist.next();
	   			  
	   			  // is {v w} already an edge?
	   			  if (packData.areNghbs(v,w)) {
   					  CirclePack.cpb.errMsg("<"+v+" "+w+"> is already an edge");
   					  break;
	   			  }
	   			  if (!packData.isBdry(v) || !packData.isBdry(w)) {
	   				  CirclePack.cpb.errMsg("usage: add_edge v w, vertices must be on boundary");
	   				  break;
	   			  }

	   			  // dcel case
   				  if (packData.packDCEL!=null) {
   					  if (RawDCEL.addEdge_raw(packData.packDCEL, v, w)!=null)
   						  count++;
   					  else
   						  break;
   				  }

   				  // traditional packing
   				  else {
   	   				  
	   				  // reaching here, 2 boundary verts.	   				  
	   				  int afterv=packData.getFirstPetal(v);
	   				  int afterw=packData.getFirstPetal(w);
		   			  
	   				  // Special case: bdry component is {v,afterv,w,afterw,v}
	   				  if (packData.nghb(afterv,w)>=0 && packData.nghb(afterw,v)>=0) {
	   					  // two steps: 
	   					  //  * add edge {v,w} with "enfold", leaving bdry component {v,w,afterw}
	   					  //  * "enclose 0 afterw" to make that bdry component into an interior face
	   					  if (packData.enfold(afterv)==0)
	   						  return count;
	   					  packData.complex_count(false);
	   					  String cs=new String("enclose 0 "+afterw);
	   					  if (jexecute(packData,cs)==0)
	   						  return count;
	   					  packData.complex_count(false);
	   					  count++;
	   				  }

	   				  else {
	   					  // downstream from v, is this common neighbor?
	   					  if (packData.nghb(afterv,w)>=0) { // yes
	   						  if (packData.enfold(afterv)==0)
	   							  return count;
	   						  packData.complex_count(false);
	   						  count++;
	   					  }
	   					  // else, downstream from w, is this common neighbor?
	   					  if (packData.nghb(afterw,v)>=0) { // yes
	   						  if (packData.enfold(afterw)==0)
	   							  return count;
	   						  packData.complex_count(false);
	   						  count++;
	   					  }
	   				  } // end of normal case
	   			  }
	   		  }
	   		  if (count>0) {
	   			  packData.xyzpoint=null;
	   			  if (packData.packDCEL!=null) {
	   				  packData.packDCEL.fixDCEL_raw(packData);
	   			  }
	   			  else 
	   				  packData.setCombinatorics();
	   			  packData.fillcurves();
	   		  }
	   		  return count;
	      }
	      
	      // ========= add_barycenter ========
		  // ========= add_face_triple =======
	      else if (cmd.startsWith("add_b") || cmd.startsWith("add_face_t")) {
	    	  boolean baryOpt=true;  // add_barycenter
	    	  if (cmd.charAt(4)=='f') baryOpt=false;  // add face triple
	    	  int f;
	    	  int node=packData.nodeCount;
	    	  items=flagSegs.elementAt(0); // should be only one segment
	   		  FaceLink faceLink=new FaceLink(packData,items);
	   		  if (faceLink==null || faceLink.size()<1) return 0;
	      		  
	   		  Iterator<Integer> flist=faceLink.iterator();
	   		  int []xdup=new int[packData.faceCount+1]; // to avoid duplication
	   		  
	   		  if (packData.packDCEL!=null) {
	   			  if (!baryOpt) 
   					  throw new ParserException("'face_triple' not yet available for dcel case.");

		   		  while (flist.hasNext()) {
		   			  f=(Integer)flist.next();
		   			  dcel.Face face=packData.packDCEL.faces[f];
		   			  if (xdup[f]==0) {
		   				  int ans;
	   					  ans=RawDCEL.addBary_raw(packData.packDCEL,face,false);
	   					  xdup[f]=1;
		   				  if (ans!=0 && face.faceIndx<0)
		   					  packData.packDCEL.redChain=null; // must redo
		   				  count += ans;
		   			  }
		   		  }
		   		  
		   		  if (count==0)
		   			  return 0;
		   		  if (packData.packDCEL.redChain==null)
		   			  CombDCEL.redchain_by_edge(packData.packDCEL,null,null);
		   		  CombDCEL.d_FillInside(packData.packDCEL);
		   		  packData.attachDCEL(packData.packDCEL);
		   		  return count;
	   		  }
	   		  
	   		  // traditional packing
	   		  while (flist.hasNext()) {
	   			  f=(Integer)flist.next();
		   		  if ((node=packData.nodeCount+faceLink.size()+10) > (packData.sizeLimit)
		      		       && packData.alloc_pack_space(node,true)==0 ) {
		      		      throw new DataException("Space allocation problem with adding "+
		      		    		  "barycenter vertices.");
		   		  }
	   			  if (xdup[f]==0) {
	   				  int ans;
	   				  if (baryOpt)
	   					  ans=packData.add_barycenter(f);
	   				  else 
	   					  ans=packData.add_face_triple(f);
	   			  }
	   	      }
	   		  if (count>0) {
	   			  packData.xyzpoint=null;
	   			  packData.setCombinatorics();
	   			  packData.fillcurves();
	   		  }
	   		  return count;
	      }

	      // ========= aspect ====== (formerly 'rect_ratio')
	      else if (cmd.startsWith("aspect")) {
	    	  CallPacket cP=CommandStrParser.valueExecute(packData,cmd,flagSegs);
	    	  if (cP.error) {
	    		  CirclePack.cpb.errMsg("aspect call failed");
	    		  return 0;
	    	  }
	    	  StringBuilder strb=new StringBuilder("Log(Aspect) (log(width/height)) of p"+
	  				packData.packNum+", corners [");
	    	  for (int j=0;j<4;j++)
	    		  strb.append(cP.int_vec.get(j)+" ");
	  		  strb.append("] is "+cP.double_vec.get(0));
	  		  CirclePack.cpb.msg(strb.toString());
	  		  return 1;
	      }
	            
	      // =============== add_layer
	      else if (cmd.startsWith("add_lay")) {
	    	  // modes
	    	  int TENT=0;
	    	  int DEGREE=1;
	    	  int DUPLICATE=2;
	    	  
	    	  int mode=TENT; // default
	    	  int degree=3; // default
	    	  int v1,v2;
	    	  
	    	  if (packData.getBdryCompCount()==0 || flagSegs.size()==0) {
	    		  throw new ParserException("perhaps packing has no boundary?");
	    	  }
	    	  
	      	  items=flagSegs.elementAt(0); // should have only one segment
	      	  if (StringUtil.isFlag(items.elementAt(0))) {
	      		  char c=items.elementAt(0).charAt(1);
	      		  items.remove(0);
	      		  
	   			  switch(c) {
	   			  
	   			  // Two flags; process rest in following
	   			  case 't': // 
	   			  {
	   				  mode=TENT;
	   				  break;
	   			  }
	   			  case 'd': // 
	   			  {
	   				  mode=DUPLICATE;
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
	      		  mode=DEGREE;
	      	  
	   		  // TENT/DUPLICATE modes take 2 arguments, v1 v2; DEGREE has <d> also (at end)
	   		  if ((mode==DEGREE && items.size()!=3) || (mode!=DEGREE && items.size()!=2)) {
	   			  throw new DataException("usage: -[dt] {d} v1 v2.");
	   		  }
	   		  if (mode==DEGREE) {
	   			  try {
	   				 degree=Integer.parseInt(items.elementAt(0));
	   				 if (degree<4 || degree>PackData.MAX_PETALS) {
	     				  throw new ParserException("improper degree "+degree);
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

   			  if (!packData.isBdry(v1) || !packData.isBdry(v2))
   				  throw new ParserException("One of "+v1+" or "+v2+" is not a boundary vertex");

	   		  // dcel case
   			  int ans;
	   		  if (packData.packDCEL!=null) {
	   			  PackDCEL pdcel=packData.packDCEL;
	   			  pdcel.zeroVUtil();
	   			  ans= RawDCEL.addlayer_raw(pdcel,mode,degree,v1,v2);
	   			  if (ans<=0)
	   				  return 0;
	   			  VertexMap vmap=pdcel.reapVUtil();
	   			  pdcel.fixDCEL_raw(packData);
	   			  pdcel.modRadCents(vmap);
	   			  return ans;
	   		  }
	   		  
	   		  // traditional
	   		  ans = packData.add_layer(mode,degree,v1,v2);
	   		  packData.setCombinatorics();
	   		  return ans;
	      }
	      
	      // =============== add_gen
	      else if (cmd.startsWith("add_gen")) {
	    	  // modes
	    	  int TENT=0;
	    	  int DEGREE=1;
	    	  int DUPLICATE=2;
	    	  
	    	  int mode=TENT; // default
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
	    			  mode=DEGREE;
	    		  }
	    		  else
	    			  mode=TENT;
	    	  
	    		  // checked for/handled -b flag (must be last flag)
	    		  int lastf=flagSegs.size()-1;
	    		  if (lastf>=0) {
	    			  items=flagSegs.get(lastf);
	    			  if (!b_flag && items.get(0).startsWith("-b")) {
	    				  items.remove(0);
	    				  bdrylist=new NodeLink(packData,items); // for now, do all
	    				  if (bdrylist.size()>0) b_flag=true;
	    				  flagSegs.remove(lastf);
	    			  }
	    		  }

	    		  // now for other flags
	    		  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    		  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  char c=items.get(0).charAt(1);
	    			  items.remove(0);

	    			  switch(c) {
	   			  
	    			  // Two flags; process rest in following
	    			  case 'd':  
	    			  {
	    				  mode=DUPLICATE;
	    				  break;
	    			  }
	    			  case 'b': // already handled
	    			  {
	    				  break;
	    			  }
	    			  default:
	    			  {
	    				  mode=TENT;
	    			  }
	    			  } // end of flag switch
	    		  }
	    		  }
	    	  } catch  (Exception ex) {
	    		  throw new ParserException("usage: add_gen {n} [{d}] [-dt] [-b {v..}]");
	    	  }
	    	  int origVCount=packData.nodeCount;
	    	  
	    	  // Finally, calls to add_layer for each boundary component
			  int v1,v2;
			  if (packData.packDCEL!=null) {
				  PackDCEL pdcel=packData.packDCEL;
				  if (!b_flag) { // just one boundary component
					  for (int n=1;n<=numGens;n++) {
						  pdcel.zeroVUtil();
						  v1=v2=pdcel.idealFaces[1].edge.origin.vertIndx;
						  count += RawDCEL.addlayer_raw(pdcel, mode, degree, v1, v2);
						  VertexMap vmap=pdcel.reapVUtil();
						  pdcel.fixDCEL_raw(packData);
						  pdcel.modRadCents(vmap);
					  }
				  }
				  else if (bdrylist.size()>0) { // Note: have to adjust v1, v2 each time because there'a a new start
					  Iterator<Integer> Bverts=bdrylist.iterator();
					  while (Bverts.hasNext()) {
						  int b=(Integer)Bverts.next();
						  for (int n=1;n<=numGens;n++) {
							  pdcel.zeroVUtil();
							  v1=v2=pdcel.idealFaces[b].edge.origin.vertIndx;
							  count += RawDCEL.addlayer_raw(pdcel, mode, degree, v1, v2);
							  VertexMap vmap=pdcel.reapVUtil();
							  pdcel.fixDCEL_raw(packData);
							  pdcel.modRadCents(vmap);
						  }
					  }
				  }
				  return count;
			  }

			  // traditional
			  try {
				  if (!b_flag) { // just one boundary component
					  for (int n=1;n<=numGens;n++) {
						  v1=v2=packData.bdryStarts[1];
						  count+= packData.add_layer(mode,degree,v1,v2);
					  }
				  }
				  else if (bdrylist.size()>0) { // Note: have to adjust v1, v2 each time because there'a a new start
					  Iterator<Integer> Bverts=bdrylist.iterator();
					  while (Bverts.hasNext()) {
						  int b=(Integer)Bverts.next();
						  for (int n=1;n<=numGens;n++) {
							  v1=v2=packData.bdryStarts[b];
							  count += packData.add_layer(mode,degree,v1,v2);
						  }
					  }
				  }
			  } catch (NumberFormatException nfe) {
				  throw new DataException("bad data.");
			  }
			  packData.setCombinatorics();
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
	    		  qackData=CPBase.cpScreens[qnum].getPackData();
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
	    	  if (packData.bary_refine()==0) return 0;
	    	  CirclePack.cpb.msg("Packing p"+packData.packNum+" has "+
	    			  "been barycentrically refined");
	    	  return 1;
	      }
	  } // end of 'b' 
	  case 'c':
	  {
		  
		  // ======== test command ========= 
		  if (cmd.startsWith("cooK")) {
	    	  CookieMonster cM=null;
	    	  try {
	    		  cM=new CookieMonster(packData,flagSegs);
	    	  } catch (Exception ex) {
	    		  System.err.println(ex.getMessage());
	    		  return 0;
	    	  }
	    	  PackData newp=cM.cookie_out();
	    	  int pnum=packData.packNum;
	    	  if (newp!=null) {
	    		  CirclePack.cpb.swapPackData(newp,pnum,false);
	    		  packData=newp;
	    	  }
	    	  return packData.nodeCount;
		  }

	      // =========== cookie ===========
	      if (cmd.startsWith("cookie")) {
	    	  
	    	  // catch '-Z' flag, if there, meaning "zigzag_cookie"
	    	  // Note: this turned out to be useless, since it led
	    	  //   too many bdry vertices w/o interior ngbhs.
	    	  boolean zigzag=false;
	    	  try {
	    		  items=(Vector<String>)flagSegs.get(0);
	  	          if (items.get(0).startsWith("-Z")) 
	  	        	  zigzag=true;
	    	  } catch(Exception ex) {}
	    	  
	    	  if (packData.packDCEL!=null) { // packData.packDCEL.newOld;
	    		  
	    		  // identify forbidden edges (and possibly new 'alpha')
	    		  HalfLink hlink=CombDCEL.d_CookieData(packData,flagSegs);
	    		  
	    		  // cookie out to form new DCEL structure
	    		  PackDCEL cutDCEL=CombDCEL.redchain_by_edge(
	    				  packData.packDCEL,hlink,packData.packDCEL.alpha);
	    		  CombDCEL.d_FillInside(cutDCEL);
	    		  if (cutDCEL!=null) {
	    			  packData.attachDCEL(cutDCEL);
	    			  return cutDCEL.vertCount;
	    		  }
	    		  return 0;
	    	  }
	    	  // else, traditional cookie methods
	    	  else {
	    		  CookieMonster cM=null;
	    		  try {
	    			  cM=new CookieMonster(packData,flagSegs);
	    		  } catch (Exception ex) {
	    			  System.err.println(ex.getMessage());
	    			  return 0;
	    		  }
	    	  
	    		  int outcome=-1;
	    		  if (zigzag) {}
	    		  else {  	
	    			  outcome=cM.goCookie();
	    			  if (outcome<0) {
	    				  cM=null;
	    				  throw new ParserException("cookie failed: packing should be okay");
	    			  }
	    		  }
	    	  
	    		  if (outcome>0) {
	    			  int pnum=packData.packNum;
	    			  CirclePack.cpb.swapPackData(cM.getPackData(),pnum,false);
	    			  packData=cM.getPackData();
	    		  }
	    		  CirclePack.cpb.msg("Cookie seems to have succeeded");
	    		  packData.poisonEdges=null;
	    		  packData.poisonVerts=null;
	    		  cM=null;
	    		  return 1;
	    	  }
	      }
		  
	      //  =========== cir_invert ===============
	      else if (cmd.startsWith("cir_invert")) {
	        double CPrad2=1.0;
	        Complex ctr2=null;
	        boolean u_flag=false;

	        items=(Vector<String>)flagSegs.get(0);
	        String str=(String)items.get(0);
	        if (StringUtil.isFlag(str)) {
	            if (!str.startsWith("-u")) throw new ParserException("usage: must start with -u");
	            ctr2=new Complex(0.0);
	            CPrad2=1.0;
	            u_flag=true;
	            items.remove(0);
	        }
	        NodeLink vertlist=new NodeLink(packData,items);
	        int v1=(Integer)vertlist.get(0);
	        if (packData.hes<0) packData.geom_to_e(); 
	        Complex ctr1=packData.getCenter(v1);
	        double CPrad1=packData.getRadius(v1);
	        if (packData.hes>0) {
	      	CircleSimple sc=SphericalMath.s_to_e_data(ctr1,CPrad1);
	      	ctr1=new Complex(sc.center);
	      	CPrad1=sc.rad;
	        }
	        if (!u_flag) { // use v2
	            int v2=(Integer)vertlist.get(1); // not -u, so get w
	            ctr2=packData.getCenter(v2);
	            CPrad2=packData.getRadius(v2);
	            if (packData.hes>0) {
	            	CircleSimple sc=SphericalMath.s_to_e_data(ctr2,CPrad2);
	            	ctr2=new Complex(sc.center);
	            	CPrad2=sc.rad;
	            }
	        }
	        Mobius mob=Mobius.cir_invert(ctr1,CPrad1,ctr2,CPrad2);
	        packData.apply_Mobius(mob,new NodeLink(packData,"a"));
	        return 1;
	      }
	      
	      // ============== count ==========
	      else if (cmd.startsWith("count")) {
	    	  CallPacket cP=CommandStrParser.valueExecute(packData,cmd,flagSegs);
	    	  if (cP==null || cP.int_vec==null || cP.int_vec.size()==0)
	    		  return 0; // failed
	    	  return (int)cP.int_vec.get(0);
	      }
	      
	      // ========= copy ==============
	      else if (cmd.startsWith("copy")) {
	    	  items=(Vector<String>)flagSegs.get(0); // should be just the pack number
	    	  try {
	    		  int qnum=Integer.parseInt((String)items.get(0));
	    		  if (qnum==packData.packNum) return 1; // same packing
	    		  PackData tmpPD=packData.copyPackTo();
	    		  CirclePack.cpb.swapPackData(tmpPD,qnum,false);
	    		  tmpPD.setName(packData.fileName);
//	    		  packData=cps.getPackData(); 
	    		  return 1;
	    	  } catch (Exception ex) {
	    		  throw new ParserException("copy failed: "+ex.getMessage());
	    	  }
	      }
	      
	      // ========= color ============
		  else if (cmd.startsWith("color")) {
			  items=(Vector<String>)flagSegs.elementAt(0);
			  String str=(String)items.elementAt(0);
			  // faces or circles?
			  if(str.startsWith("-f")) {
				  items.remove(0);
				  if (items.size()==0)
					  flagSegs.remove(0);
				  packData.color_faces(flagSegs);
				  return 1;
			  }
			  if (str.startsWith("-c") || str.startsWith("-v")) {
				  items.remove(0);
				  if (items.size()==0)
					  flagSegs.remove(0);
				  packData.color_circles(flagSegs);
				  return 1;
			  }
			  if (str.startsWith("-T") || str.startsWith("-D") || str.startsWith("-Q") ) {
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
				  else str=str.concat(" v "+v+" "+(double)(packData.getRadius(v)/cabs)+"\n");
				  tick++;
			  }
			  return tick;
		  }
	      
	      // ========= center, center_vert/point =======
	      else if (cmd.startsWith("center")) {
	    	  // options: center_vert v, center_point x y, center -v {v}, center -z {x y}
	    	  Complex z=null;
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  String str=(String)items.get(0);
	    	  int v=packData.activeNode;
	    	  if (cmd.startsWith("center_vert")) {
	    		  v=NodeLink.grab_one_vert(packData,str);
	    		  z=packData.getCenter(v);
	    	  }
	    	  else if (cmd.startsWith("center_point")) {
	    		  z=new Complex(Double.parseDouble(items.get(0)),Double.parseDouble(items.get(1)));
	    	  }
	    	  else {
	    		  if (str.startsWith("-v")) {
	    			  str=(String)items.get(1);
	        		  v=NodeLink.grab_one_vert(packData,str);
	        		  z=packData.getCenter(v);
	    		  }
	    		  else if (str.startsWith("-z")) {
	        		  z=new Complex(Double.parseDouble(items.get(1)),Double.parseDouble(items.get(2)));
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
			// =============== dual_layout (OBE: sch_layout)
			if (cmd.startsWith("dual_lay")) {
				if (!packData.haveSchwarzians()) {
					CirclePack.cpb.errMsg("Schwarzians are not allocated for p"+packData.packNum);
					return 0;
				}
				if (packData.hes < 0) {
					CirclePack.cpb.errMsg("usage: dual_layout is not used for hyperbolic packings");
					return 0;
				}
				// look for list of face pairs; default to a spanning tree
				boolean debug = false;
				GraphLink graph = null;
				String cflags = null; // flags for drawing circles
				String fflags = null; // flags for drawing faces
				if (flagSegs != null && flagSegs.size() > 0 && flagSegs.get(0).size() > 0) {
					Iterator<Vector<String>> its = flagSegs.iterator();
					items = null;
					// may have flags to see results -c{disp_ops} and/or -f{disp_ops}
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
							case 'f': // report and (if draw==true) draw face labels
							{
								fflags = new String(str);
								break;
							}
							} // end of switch
						}
					}

					// 'items' should be dual edge list, default to spanning tree
					if (items != null && items.size() > 0)
						graph = new GraphLink(packData, items);
					else
						graph = DualGraph.easySpanner(packData, true);
				} else // no flags or list?
					graph = DualGraph.easySpanner(packData, true);

				// Do we need to place the first face? Only if
				// we start with a "root".
				EdgeSimple edge = graph.get(0);
				int baseface = 0;
				if (edge.v == 0) { // root? yes, then have to place
					graph.remove(0);
					baseface = edge.w;
				}

				// yes, place the base equilateral face and zero out  all 'curv'
				if (baseface > 0) {
					
					// zero out the curvatures
					for (int v=1;v<=packData.nodeCount;v++)
						packData.setCurv(v,0.0);
					
					// note that we reset the radii and centers of 'baseface'
					int[] verts=packData.faces[baseface].vert;
					Complex[] Z=new Complex[3];
					Z[0]=new Complex(1.0,-CPBase.sqrt3);
					Z[1]=new Complex(1.0,CPBase.sqrt3);
					Z[2]=new Complex(-2.0);
					if (packData.hes>0) {
						for (int j=0;j<3;j++) { 
							CircleSimple cS=SphericalMath.e_to_s_data(Z[j],CPBase.sqrt3);
							packData.setCenter(verts[j],new Complex(cS.center));
							packData.setRadius(verts[j],cS.rad);
							packData.setCurv(verts[j],packData.getCurv(verts[j])+Math.PI);
						}
					}
					else {
						for (int j=0;j<3;j++) {
							packData.setCenter(verts[j],Z[j]);
							packData.setRadius(verts[j],CPBase.sqrt3);
							packData.setRadius(verts[j],packData.getRadius(verts[j]) +Math.PI/3.0);
						}
					}

					// TODO: layout problems can occur if not in sph geometry,
					//  but should figure out check and just warn user.
//					if (packData.hes <= 0) {
//						packData.geom_to_s();
//						packData.fillcurves();
//						packData.setGeometry(packData.hes);
//						if (cpS != null)
//							cpS.setPackName();
//						jexecute(packData, "disp -w");
//					}

					if (cflags != null) {
						StringBuilder strbld = new StringBuilder("disp " + cflags);
						for (int j = 0; j < 3; j++) { // show all three circles
							strbld.append(" " + packData.faces[baseface].vert[j]);
						}
						jexecute(packData, strbld.toString());
					}
					if (fflags != null) {
						StringBuilder strbld = new StringBuilder("disp " + fflags + " " + baseface);
						jexecute(packData, strbld.toString());
					}
					count++;
				}

				// TODO: layout problems can occur if we don't convert to sph geometry,
				//    but instead of automatically converting, I should check to outcome
				//    for problems and just send a message to the user.
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
					double s=packData.getSchwarzian(new EdgeSimple(verts[0],verts[1]));

					int m = packData.face_nghb(f, g);
					int target = packData.faces[g].vert[(m + 2) % 3];

					// assume circles of f are in place, need only compute
					// the third circle of g, 'target' (across the shared edge).
					try {
						// compute map from base equilateral
						Mobius bm_f = Schwarzian.faceBaseMob(packData, f);

						// compute the target circle
						CircleSimple sC = Schwarzian.getThirdCircle(s, j, bm_f, packData.hes);

						// debug info
						if (debug) {// debug=true;
							deBugging.DebugHelp.mob4matlab("bm_f", bm_f);

							// display the computed tangency points, print cents/rads
							if (packData.hes > 0) {
								Complex[] tp = new Complex[3];
								int[] vert = packData.faces[f].vert;
								System.out.println("circles <" + vert[0] + " " + vert[1] + " " + vert[2]
										+ "> :\nSchwarian is s =" + s);
								for (int jj = 0; jj < 3; jj++) {
									Complex zjj = packData.getCenter(vert[jj]);
									double rjj = packData.getRadius(vert[jj]);
									System.out.println("C(" + jj + ",:) = [" + zjj.x + " " + zjj.y + " " + rjj + "]");
									tp[jj] = SphericalMath.sph_tangency(zjj, packData.getCenter(vert[(jj + 1) % 3]),
											rjj, packData.getRadius(vert[(jj + 1) % 3]));
									String str = new String("disp -dpfc5 " + vert[jj] + " " + vert[(jj + 1) % 3]);
									// draw the tangency point
									CommandStrParser.jexecute(packData, str);
								}
							}

							// the outcome circle
							System.out.println("new circles: cent = " + sC.center + "; r = " + sC.rad);
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
							double ang=CommonMath.get_face_angle(radii[q],radii[(q+1)%3],radii[(q+2)%3],packData.hes);
							packData.setCurv(verts[q],packData.getCurv(verts[q])+ang);
						}
						
					} catch (Exception ex) {
						CirclePack.cpb.errMsg("problem applying some schwarzian\n");
					}
					if (cflags != null) {
						StringBuilder strbld = new StringBuilder("disp " + cflags + " " + target);
						jexecute(packData, strbld.toString());
					}
					if (fflags != null) {
						StringBuilder strbld = new StringBuilder("disp " + fflags + " " + g);
						jexecute(packData, strbld.toString());
					}

					count++;
				}
				return count;
			}

			// ========= DCEL <stuff> ==========
			
			// TODO: this is experimental related to DCEL combinatorics
			else if (cmd.startsWith("DCE")) {
				
				// have to pull off the dcel command and maintain the rest
				if (flagSegs==null || flagSegs.size()==0) // nothing?
					return 0;
				items=(Vector<String>)flagSegs.get(0);
				String str=items.remove(0);
				if (items.size()==0) { // get next items
					flagSegs.remove(0);
					if (flagSegs.size()>0)
						items=flagSegs.get(0);
				}
				
				// debug
				if (str.contains("compOrder")) {
					packData.glist=new GraphLink(packData,"s");
					DualGraph.printGraph(packData.glist);
					DCELdebug.visualDualEdges(packData.packDCEL,
							packData.packNum,packData.glist);
					return 1;
				}
			
				// Create
				if (str.contains("clone")) {
					int qnum= StringUtil.qFlagParse(items.remove(0));
					
					// 'vlist' of non-keepers defaults to empty
					NodeLink vlist=null;
					if (items.size()>0)
						vlist=new NodeLink(packData,items); // non-keepers

					// Now three situations: 'vlist' takes precedence over 'poisonVerts'
					PackDCEL raw=CombDCEL.getRawDCEL(packData);
					raw.p=packData;
					PackDCEL pdcel=null;
					HalfLink hlink=null;
					if (vlist!=null && vlist.size()>0) {
						CPBase.Vlink=vlist;
						String nstr="-n Vlist";
						hlink=CombDCEL.d_CookieData(packData,nstr);
					}
					else if (packData.poisonVerts!=null && packData.poisonVerts.size()>0) {
						CPBase.Vlink=packData.poisonVerts;
						String nstr="-n Vlist";
						hlink=CombDCEL.d_CookieData(packData,nstr);
					}
					pdcel=CombDCEL.extractDCEL(raw,hlink,raw.alpha);
					
					pdcel.p=packData;
					PackData p=DataDCEL.dcel_to_packing(pdcel);
					CirclePack.cpb.swapPackData(p,qnum,false);
					pdcel.D_CompCenters();
					p.fillcurves();
					p.set_aim_default();
					return 1;
				}
				
				else if (str.contains("reorie")) {
					
					// reorient packDCEL
					CombDCEL.reorient(packData.packDCEL);
					
					// reflect all centers across imaginary axis
					if (packData.hes<=0) {
						for (int v=1;v<=packData.nodeCount;v++) 
							packData.vData[v].center=packData.vData[v].center.times(-1.0).conj();
					}
					else {
						for (int v=1;v<=packData.nodeCount;v++) 
							packData.vData[v].center.x=packData.vData[v].center.x*(-1.0);
					}
					RedHEdge rtrace=packData.packDCEL.redChain;
					do {
						if (packData.hes<=0)
							rtrace.setCenter(rtrace.getCenter().times(-1.0).conj());
						else
							rtrace.setCenter(rtrace.getCenter().times(-1.0));
					} while (rtrace!=packData.packDCEL.redChain);

					return 1;
				}
				
				// keep redchain, reprocess the interior with/w.o. blueshift option
				else if (str.contains("refil")) {
					CombDCEL.d_FillInside(packData.packDCEL);
				}
				
				// show redChain edges and their twinRed,
				//    show starts/stops of sides
				else if (str.contains("reddebug")) {
					DCELdebug.redChainDetail(packData.packDCEL);
					return 1;
				}
				
				else if (str.contains("red")) {
					// TODO: have to replace after debugging
					DCELdebug.drawRedChain(packData, packData.packDCEL.redChain);
					PackControl.canvasRedrawer.paintMyCanvasses(packData,false);
					return 1;
				}

				else if (str.contains("dcel")) {
					return packData.attachDCEL();
//					PackDCEL raw=CombDCEL.getRawDCEL(packData.getBouquet());
//					packData.packDCEL = CombDCEL.d_redChainBuilder(raw,null,false,packData.getAlpha());
//					if (packData.packDCEL == null || packData.packDCEL.vertCount != packData.nodeCount)
//						throw new CombException("failed to create packDCEL");
//					return 1;
				}
				else if (str.contains("export")) {
					if (packData.packDCEL==null)
						return 0;
					
					// what packing? default to current
					int qnum=packData.packNum;
					if (items!=null && items.size()>0) {
						qnum= StringUtil.qFlagParse(items.get(0));
						if (qnum==-1 || qnum>=CPBase.NUM_PACKS) // no '-q{q}' flag
							qnum=packData.packNum; // replace existing packing
						else
							items.remove(0);
					}
					
					PackData pdata=DataDCEL.dcel_to_packing(packData.packDCEL);
					pdata.setCombinatorics();
					pdata.set_aim_default();
					return CirclePack.cpb.swapPackData(pdata,qnum,false);
				}
				else if (str.contains("redcook")) {
					if (packData.packDCEL==null)
						return 0;
					if (items==null || items.size()==0) 
						return 0;
					int qnum= StringUtil.qFlagParse(items.get(0));
					if (qnum==-1 || qnum>=CPBase.NUM_PACKS) // no '-q{q}' flag
						qnum=packData.packNum; // replace existing packing
					else
						items.remove(0);
					
					// find desired vertices, default to all
					NodeLink vlink=new NodeLink(packData,items);
					if (vlink==null || vlink.size()==0)
						vlink=new NodeLink(packData,"a");
					
					// create new 'PackDCEL'
					PackDCEL tmpdcel=packData.packDCEL.redCookie(vlink);
					if (tmpdcel==null || tmpdcel.vertCount==0) {
						CirclePack.cpb.errMsg("'redCookie' failed to produce DCEL");
						return 0;
					}
					
					// convert to a new packing
					PackData tmppack=DataDCEL.dcel_to_packing(tmpdcel);
					if (tmppack==null) {
						CirclePack.cpb.errMsg("failed to convert 'PackDCEL' into packing");
						return 0;
					}
					tmppack.setCombinatorics();
					tmppack.set_aim_default();
					return CirclePack.cpb.swapPackData(tmppack,qnum,false);
				}
				else if (str.contains("layout")) {
					if (packData.packDCEL==null)
						return 0;
					return packData.packDCEL.dcelCompCenters(packData.packDCEL.computeOrder);
				} 
				else if (str.contains("syncF")) { // sync p.faces to packDCEL.faces
					if (packData.packDCEL==null)
						return 0;
					return packData.packDCEL.syncFaceData();
				}
				else if (str.contains("bary")) { // baryrefine given faces
					FaceLink flink=null;
					if (items==null || items.size()==0) // default to 'all'
						flink=new FaceLink(packData,"a");
					else
						flink=new FaceLink(packData,items);
					ArrayList<dcel.Face> farray=new ArrayList<dcel.Face>();
					Iterator<Integer> fits=flink.iterator();
					while (fits.hasNext()) {
						farray.add(packData.packDCEL.faces[fits.next()]);
					}
					return RawDCEL.addBaryCents_raw(packData.packDCEL,farray);
				}
				else if (str.contains("frac")) { // do local refinement at given vertices
					NodeLink vlist=null;
					if (items==null || items.size()==0) // default to 'all'
						vlist=new NodeLink(packData,"a");
					else
						vlist=new NodeLink(packData,items);
					return packData.packDCEL.localRefine(vlist);
				}
				else if (str.contains("write")) {
					int len;
					String fname = null;
					String flagstr = null;
					boolean append_flag = false;
					boolean script_flag = false;

					boolean faulty = false;
					try { // should have just one flag string
						if (!StringUtil.isFlag(flagstr = items.firstElement())) {
							// take as filename (may have blanks)
							fname = StringUtil.reconItem(items); 
							flagstr = null;
						} 
						else if (items.size() == 1)
							faulty = true; // flags, but no filename
						else {
							items.remove(0); // held in 'flagstr'
							fname = StringUtil.reconItem(items); // take as
																	// filename
																	// (may have
																	// blanks)
						}
					} catch (Exception ex) {
						throw new InOutException("check usage: " + ex.getMessage());
					}
					if (faulty) {
						throw new ParserException("check usage: [-<flags>] <filename>");
					}

					if (flagstr != null && flagstr.length() > 0 && StringUtil.isFlag(flagstr)) {
						flagstr = flagstr.substring(1);
						len = flagstr.length();

						// "s" to go
						if (len == 1 && flagstr.equalsIgnoreCase("s")) {
							if (cmd.charAt(0) == 'W') {
								CirclePack.cpb.myErrorMsg("Can't 'Write' (cap 'W') to script");
								return 0;
							}
							script_flag = true;
						}

						else if (flagstr != null) {
							for (int j = 0; j < len; j++) {
								switch (flagstr.charAt(j)) {
								case 'A': {
									append_flag = true;
									break;
								}
								case 's': { // write to the script file
									if (cmd.charAt(0) == 'W') {
										CirclePack.cpb.myErrorMsg("Can't 'Write' (cap 'W') to script");
										return 0;
									}
									script_flag = true;
									break;
								}
								} // end of flag parsing switch
							} // end of for
						} // end of flag parsing
					}

					File dir = CPFileManager.PackingDirectory;
					if (cmd.charAt(0) == 'W') { // use given directory
						if (fname.startsWith("~/")) {
							fname = new String(
									CPFileManager.HomeDirectory + File.separator + fname.substring(2).trim());
						}
						dir = new File(fname);
						fname = dir.getName();
						dir = new File(dir.getParent());
					}
					BufferedWriter fp = CPFileManager.openWriteFP(dir, append_flag, fname, script_flag);
					try {
						if (str.contains("_dual")) {
							packData.writeDCEL(fp, true);
						}
						else 
							packData.writeDCEL(fp, false); 
					} catch (Exception ex) {
						throw new InOutException("write failed");
					}
					if (script_flag) { // include in script
						CPBase.scriptManager.includeNewFile(fname);
						CirclePack.cpb.msg("Wrote packing " + fname + " to the script");
						return 1;
					}
					CirclePack.cpb.msg("Wrote packing to " + dir.getPath() + File.separator + fname);
					return 1;
				} // end of 'write'
			} // end of 'DCEL' calls
		  
	      // ========= doyle_point ========
	      if (cmd.startsWith("doyle_point")) {
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  int f=FaceLink.grab_one_face(packData,(String)items.get(0));
	    	  double r0,r1,r2;
	    	  Complex z0,z1,z2;

	    	  if (packData.hes!=0 || f<1 || f>packData.faceCount) {
	    		  throw new ParserException("must specify face f of eucl packing");
	    	  }
	        r0=packData.getRadius(packData.faces[f].vert[0]);
	        r1=packData.getRadius(packData.faces[f].vert[1]);
	        r2=packData.getRadius(packData.faces[f].vert[2]);
	        z0=packData.getCenter(packData.faces[f].vert[0]);
	        z1=packData.getCenter(packData.faces[f].vert[1]);
	        z2=packData.getCenter(packData.faces[f].vert[2]);
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
	    	  throw new ParserException("'doyle_annulus' processing appears to be unfinished.");
	      }

	      // ============== double =============
	      else if (cmd.startsWith("double")) {
	    	  int alp_sym=0;
	    	  NodeLink vertlist=null;
	    	  if (flagSegs==null || flagSegs.size()==0) { // nothing, do all
	    		  vertlist=new NodeLink(packData,"B");
	    		  alp_sym=packData.double_K(vertlist);
	    	  }
	    	  else {
	    		  items=(Vector<String>)flagSegs.get(0);
	    		  String str=(String)items.get(0);
	    		  if (str.contains("b") && str.contains("(")) { // should be form b(u v)
	    			  vertlist=new NodeLink(packData,str);
	    			  alp_sym=packData.double_on_edge(vertlist);
	    			  if (alp_sym>0) packData.setCombinatorics();
	    		  }
	    		  else {
	    			  vertlist=new NodeLink(packData,items);
	    			  alp_sym=packData.double_K(vertlist);
	    		  }
	    	  }
	    	  if (alp_sym!=0) {
	    		  CirclePack.cpb.msg("double: vert symmetric to 'alpha' is "+alp_sym);
	    		  return alp_sym;
	    	  }
	    	  return 0;
	      }
		  
		  // ========= disp (and dISp) ======== 
	      // 'dISp' is used internally: just paint active, not secondary canvasses
	      if (cmd.startsWith("dISp") || cmd.startsWith("disp")
	    		  || cmd.startsWith("DISp") || cmd.startsWith("Disp")) {
	    	  // DEBUG
//	    	  System.err.println("'disp'");
	    	  String setText=null;
	    	  if (cmd.charAt(0)=='D')
	    		  setText=StringUtil.reconstitute(flagSegs);
	    	  boolean dispLite=false; // disp only the active canvas?
	    	  if (cmd.charAt(1)=='I') dispLite=true; // yes, only active
			  
			  // No flag strings? use dispOptions 
			  // (DisplayPanel (checkboxes or tailored string))
			  if (flagSegs==null || flagSegs.size()==0) {
				  String tmpstr=new String("disp "+cpS.dispOptions.toString());
				  jexecute(packData,tmpstr);
				  if (setText!=null && !dispLite) // record as display text?
					  jexecute(packData,new String("set_disp_text "+setText));
				  return 1;
			  }
			  
			  Vector<String> first_seg=flagSegs.get(0);
			  // -w should be first; -wr redraws and exits
			  String fs=first_seg.get(0).toString().trim();
			  if (fs.startsWith("-w")) {
				  cpS.clearCanvas(false);
				  if (fs.startsWith("-wr")) {
					  String tmpstr=cpS.dispOptions.toString().trim();
					  if (tmpstr.equals("-w"))
						  return jexecute(packData,cmd+" ");
					  if (tmpstr.startsWith("-w")) 
						  tmpstr=tmpstr.substring(3); // remove redundant -w (or -wr)
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
					  CPScreen qScreen=CPBase.cpScreens[qnum];
					  if (qScreen.getPackData().status) {
						  flagSegs.remove(0); // dump this -q segment
						  count +=DisplayParser.dispParse(packData,qScreen,flagSegs);
						  if (count>0) 
							  PackControl.canvasRedrawer.paintMyCanvasses(qScreen.getPackData(),dispLite);
						  return count;
					  }
				  }
				  else // failed to get another pack
					  return 0;
			  }
				  
			  // send for parsing/execution
			  count +=DisplayParser.dispParse(packData,flagSegs);
			  if (count>0) 
				  PackControl.canvasRedrawer.paintMyCanvasses(packData,dispLite);
			  if (setText!=null && !dispLite) // record as display text?
				  jexecute(packData,new String("set_disp_text "+setText));
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
				  throw new CombException("failed to get edge list: "+ex.getMessage());
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
    			  int vert=(Integer)vlist.next();
    			  if (packData.isBdry(vert)) {
    				  int n=N;
    				  
    				  // reset n to get total degree N
    				  if (totalFlag) {
    					  if (N>=PackData.MAX_PETALS) {
    						  throw new ParserException("max degree limit "+
    								  PackData.MAX_PETALS);
    					  }
    					  n=N-(packData.countFaces(vert)+1);
    					  if (n<0) {
    						  overCount++;
    						  n=0;
    					  }
    				  }
    				  
    				  // else adding n circles (up to limit)
    				  else {
    					  int m=PackData.MAX_PETALS-packData.countFaces(vert)-1;
	    				  n=(n<m)? n:m;
    				  }

    				  // add the n circles and close up
    				  for (int i=1;i<=n;i++) 
    					  packData.add_vert(vert);
    				  packData.enfold(vert);
    				  int[] flower=packData.getFlower(vert);
    				  Complex z=packData.getCenter(flower[0]);
    				  Complex w=packData.getCenter(flower[packData.countFaces(vert)-1]);
    				  cpS.drawEdge(z,w,new DispFlags(null));
    				  count++;
    			  }
    		  } // end of while
	    	  
	    	  if (count>0) {
	    		  if (packData.packDCEL!=null)
	    			  packData.packDCEL.fixDCEL_raw(packData);
	    		  else
	    			  packData.setCombinatorics();
	    		  packData.fillcurves();
	    	  }
    		  if (overCount>0) {
    			  CirclePack.cpb.msg("One or more circles exceeded desired degree");
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
	    		  if ((q=CPBase.cpScreens[StringUtil.qFlagParse(str)].
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
	    	    	  CirclePack.cpb.msg("Full embedding in the complex of pack "+
	    	    			  q.packNum+" succeeded, embedding stored in Vertex Map");
	    	      else 
	    	    	  CirclePack.cpb.msg("Partial embedding, "+count+" vertices, in "+
	    	    			  "the complex of pack "+q.packNum+", stored in Vertex Map");
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
	    	  items=flagSegs.elementAt(0); // should be only one segment
	    	  String fstr="v"; // default to listed vertices
	    	  if (StringUtil.isFlag(items.get(0))) {
	    		  fstr=items.remove(0).substring(1);
	    	  }
	    	  
	    	  // Dispense first with 'h' flag, to project forward to 
	    	  // next (half-hex) edge, then flip clockwise edge 
	    	  // from that. The next edge itself is stored in 'elist'
	    	  // in case this is called again.
	    	  if (fstr.contentEquals("h")) {
	    		  int rslt=0;
		   		  EdgeSimple edge=EdgeLink.grab_one_edge(packData,flagSegs);
		   		  if (edge==null)
		   			  edge=EdgeLink.grab_one_edge(packData,"elist");
		   		  if (edge==null || packData.nghb(edge.v, edge.w)<0) {
		   			  CirclePack.cpb.errMsg(("usage: flip -h {v,w}"));
		   			  return 0;
		   		  }
		   		  int v=edge.w;
		   		  int w=edge.v;
		   		  int num=packData.countFaces(v);
		   		  if (pdc!=null) {
			   		  Boolean redProblem=Boolean.valueOf(false); // for dcel version 
		   			  try {
		   				  HalfEdge hedge=pdc.findHalfEdge(edge).twin;
		   				  Vertex vert=hedge.origin;
		   				  packData.elist=new EdgeLink(packData); // may store new edge in 'elist'
		   				  if (vert.isBdry()) { // bdry considerations
		   					  int tick=0;
		   					  HalfEdge he=vert.halfedge.prev.twin;
		   					  while(he.twin.origin.vertIndx!=w && he!=vert.halfedge) {
		   						  tick++;
		   						  he=he.prev.twin;
		   					  }
		   					  if (tick<2) // can't advance to new edge; kill 'baseEdge'
		   						  return 0;
		   					  if (tick==2) { // advanced but no flip
		   						  packData.elist.add(new EdgeSimple(v,vert.halfedge.origin.vertIndx));
		   						  return -1;
		   					  }
		   					  he=he.twin.next.twin.next.twin.next;
		   					  packData.elist.add(new EdgeSimple(v,he.origin.vertIndx)); // next half-hex edge
		   					  he=he.twin.next;
		   					  rslt=RawDCEL.flipEdge_raw(pdc, he);
		   					  if (rslt==0)// didn't flip 
		   						  return -1;
		   					  pdc.fixDCEL_raw(packData);
		   				  }
		   				  else { // interior
		   			   		  if (num==3) { // interior of degree 3? just reverse edge to go other way
		   		   				  packData.elist.add(new EdgeSimple(v,w));
		   		   				  return -1; // no flip
		   			   		  }	  
		   			   		  HalfEdge theedge=hedge.twin.next.twin.next.twin.next;
		   			   		  packData.elist.add(new EdgeSimple(v,theedge.twin.origin.vertIndx));
		   			   		  theedge=theedge.twin.next;
		   			   		  rslt=RawDCEL.flipEdge_raw(pdc,theedge);
		   			   		  if (rslt==0)
		   						return -1; // advanced but didn't flip
		   					  pdc.fixDCEL_raw(packData);
		   				  }
		   				  
		   			  } catch(Exception ex) {
		   				  CirclePack.cpb.errMsg("flip failed on edge <"+v+" "+w+">");
		   				  return 0;
		   			  }
		   			  return 1;
		   		  } // done with DCEL case
		   		  
		   		  // traditional packing
		   		  int indxvb=packData.nghb(v, w);
		   		  if (packData.isBdry(v)) { // bdry?
		   			  if (indxvb<3)  // can't advance to new edge; kill 'baseEdge'
		   				  return 0;
		   			  edge=new EdgeSimple(v,packData.kData[v].flower[indxvb-3]);
	   				  packData.elist=new EdgeLink(packData); // store new edge in 'elist'
	   				  packData.elist.add(edge);
	   				  if (indxvb==3) // can't flip
	   					  return -1; // advanced but no flip
	   				  rslt=packData.flip_edge(v,packData.kData[v].flower[indxvb-4],2);
	   				  if (rslt==0)
	   					  return -1; // advanced but no flip
	   				  packData.setCombinatorics();
	   				  return 1; // advanced and flipped
		   		  }
		   		  // interior?
		   		  if (num==3) { // interior of degree 3? just reverse edge to go other way
	   				  packData.elist.add(new EdgeSimple(v,w));
	   				  return -1; // reversed but no flip
		   		  }
		   		  int indxn=(indxvb-3+num)%num;
		   		  w=packData.kData[v].flower[indxn];
		   		  packData.elist.add(new EdgeSimple(v,w));
		   		  int k=(indxn-1+num)%num;
		   		  rslt=packData.flip_edge(v,packData.kData[v].flower[k],2);
		   		  if (rslt==0)
					return -1; // advanced but didn't flip
		   		  packData.setCombinatorics();
		   		  // calling routine does repack, layout, etc.
		   		  return 1; // advanced and flipped
	    	  } // done with 'h' flag case
	    	  
	    	  // For remaining cases, just build 'elink' of edges to flip, .
	   		  EdgeLink elink=new EdgeLink();
	   		  EdgeLink origLink=new EdgeLink(packData,items);
	   		  if (origLink==null || origLink.size()==0)
	   			  return 0;
	   		  Iterator<EdgeSimple> elist=origLink.iterator();

	    	  // -cc flag (deprecated -hh): for each edge, flip the next 
	    	  //     counterclockwise edge. (E.g., 'half-hex' path flips, see 'hh_path')
	    	  if (fstr.startsWith("cc") || fstr.startsWith("hh")) { 
	    		  while (elist.hasNext()) {
		   			  EdgeSimple edge=(EdgeSimple)elist.next();
		   			  if (pdc!=null) {
		   				  HalfEdge he=pdc.findHalfEdge(edge);
		   				  elink.add(new EdgeSimple(he.prev.twin.origin.vertIndx,
		   						  he.prev.origin.vertIndx)); // cclw edge
		   			  }
		   			  else {
			   			  int indx=packData.nghb(edge.v,edge.w);
			   			  if (indx>=0 && indx<packData.countFaces(edge.v)) { // flip cclw edge
			   				  int w=packData.getPetal(edge.v,indx+1);
			   				  elink.add(new EdgeSimple(edge.v,w));
			   			  }
		   			  }
	    		  }
	    	  }
	    	  else if (fstr.startsWith("cw")) {
	    		  int w;
	    		  while (elist.hasNext()) {
		   			  EdgeSimple edge=(EdgeSimple)elist.next();
		   			  if (pdc!=null) {
		   				  HalfEdge he=pdc.findHalfEdge(edge);
		   				  elink.add(new EdgeSimple(he.twin.next.origin.vertIndx,
		   						  he.twin.next.twin.origin.vertIndx)); // cclw edge
		   			  }
		   			  else {
			   			  int indx=packData.nghb(edge.v,edge.w);
			   			  if (indx==0) { // must be interior
			   				  w=packData.getPetal(edge.v,packData.countFaces(edge.v)-1);
			   			  }
			   			  else 
			   				  w=packData.getPetal(edge.v,indx-1);
		   				  elink.add(new EdgeSimple(edge.v,w));
		   			  }
	    		  }
	    	  }
	    	  else if (fstr.contentEquals("r")) { // one random, try up to 20 times 
	        	  Random rand=new Random();
	        	  int safety=20;
	        	  boolean didflip=false;
	        	  while (safety>0 && !didflip) {
	        		  int v=Math.abs((rand.nextInt())%(packData.nodeCount))+1;
	        		  if (packData.isBdry(v)) { // if boundary, try more indices
	        			  int j=1;
	        			  while (j<=packData.nodeCount  
	   						  && packData.isBdry((v=(v+j)%(packData.nodeCount)+1)))
	        				  j++;
	        			  if (packData.isBdry(v)) 
	        				  return 0; // didn't find interior vert
	        		  }
	        		  int[] flower=packData.getFlower(v);
	        		  int num=packData.countFaces(v);
	        		  int w=flower[Math.abs((rand.nextInt())%(num))];
	   				  elink.add(new EdgeSimple(v,w));
	        	  }
	    	  }
	    	  else { // no flags, just flip edges
	    		  if (origLink==null || origLink.size()<1) 
	    			  return 0;
	    		  elist=origLink.iterator();

	    		  while (elist.hasNext()) {
    				  elink.add((EdgeSimple)elist.next());
	    		  }
	    	  }  
	    		
	    	  // done building list, now to flip
	    	  return packData.flipList(elink);
	      }
	      
	      // ============ flat_hex =========
	      if (cmd.startsWith("flat_hex")) {
	    	  NodeLink vertlist=new NodeLink(packData,(Vector<String>)flagSegs.get(0));
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
	    	  count=QualMeasures.face_error(packData,crit,facelist);
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

	    	  items=(Vector<String>)flagSegs.get(0); // should be just one segment
	    	  String str=(String)items.get(0);
	    	  try {
	    		  if (str.startsWith("-vs")) { // also set screen size 
	    			  zoom=true;
	    			  x=Double.parseDouble((String)items.get(1));
	    			  v=NodeLink.grab_one_vert(packData,(String)items.get(2));
	    		  }
	    		  else if (str.startsWith("-v")) {
	    			  v=NodeLink.grab_one_vert(packData,(String)items.get(1));
	    		  }
	    		  else if (str.startsWith("-z")) {
	    			  vert_mode=false;
	    			  z=new Complex(Double.parseDouble((String)items.get(1)),
	    					  Double.parseDouble((String)items.get(2)));
	    		  }
	    		  else {
	    			  v=NodeLink.grab_one_vert(packData,str); // read first str as vertex
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
	    			  z=cpS.sphView.toApparentSph(z);
	    			  // TODO: do we need to check if on back?
	    		  }
	    		  if (zoom) {
	    			  count += cpS.realBox.setWidthHeight(2.0*rad*x);
	    		  }
	    	  }
	    	  count += cpS.realBox.focusView(z);//.times(-1.0));
	    	  cpS.update(2);
	    	  try {
	    		  jexecute(packData,"disp -wr");
	    	  } catch (Exception ex) {}
	    	  if (count>0) {
	    		  packData.cpScreen.repaint();
	    	  }
	    	  return count;
	      }
	      
	      // ========= frackMe ========
	      else if (cmd.startsWith("frac")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just one
	    	  NodeLink verts=new NodeLink(packData,items);
	    	  if (verts==null || verts.size()==0) {
	    		  CirclePack.cpb.errMsg("usage: frack {v..}; must provide vertices");
	    		  return 0;
	    	  }
	    	  return packData.frackMe(verts);
	      }
	      
		  break;
	  } // end of 'f'
	  case 'g':
	  {
	      // =========== gamma ============
	      if (cmd.startsWith("gamma")) {
	    	  int a=NodeLink.grab_one_vert(packData,flagSegs);
	    	  if (packData.packDCEL!=null)
	    		  return packData.packDCEL.setGamma(a);
	    	  return packData.setGamma(a);
	      }
	      
	      // ========= gen_cut =========
	      else if (cmd.startsWith("gen_cut")) {
	    	  if (packData.locks!=0 || !packData.isSimplyConnected()) {
	    		  throw new ParserException("packing must be simply connected");
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
	    		  CirclePack.cpb.msg("gen_cut: error or no vertices were cut");
	    		  return 1;
	    	  }
	    	  int pnum=packData.packNum;
	    	  CirclePack.cpb.swapPackData(pd,pnum,false);
	    	  packData=pd;
			  CirclePack.cpb.msg("gen_cut: the new packing has "+
					  pd.nodeCount+" vertices");
	    	  return pd.nodeCount;
	      }
	      
	      // =========== gen_mark ==========
	      else if (cmd.startsWith("gen_mark")) {
	    	  // Call routine which records generations of vertices or faces
	    	  // as measured from 'seeds' (specified in datastr). Records
	    	  // generation in 'mark' and returns the last vertex or face marked. 
	    	  // Option '-m n' tells it to stop at max generation n.
	    	  CallPacket cP=CommandStrParser.valueExecute(packData,cmd,flagSegs);
	    	  if (cP==null || cP.int_vec==null || cP.int_vec.size()==0)
	    		  return 0; // failed
	    	  return (int)cP.int_vec.get(0);
	      }
	      
	      // =============== geom_to_
	      if (cmd.startsWith("geom_to_")) {
	    	  boolean leave_flag=false;
	    	  double []radii=new double[packData.nodeCount+1];

	      	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  switch(items.get(0).charAt(1)) {
	    			  	case 'l': // leave old radii the same, even though geom changes
	    			  		{leave_flag=true;}
	    			  } // end of flag switch
	    		  } // done handling a given flagged segment
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
	    	  
	    	  packData.fillcurves();
	    	  packData.setGeometry(packData.hes);
	    	  if (cpS!=null) cpS.setPackName();
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
			  int N=1;
	    	  try {
	    		  N=Integer.parseInt(flagSegs.get(0).get(0).trim());
	    	  } catch (Exception ex) {
	    		  
	    	  }
	    	  return packData.hex_refine(N);
	      }

	      // =========== hex_slide ==========
		  else if (cmd.startsWith("hex_slide")) {
	    	  EdgeLink edgelist=null;
	    	  EdgeSimple edge=null;
	    	  try {
	        	  items=(Vector<String>)flagSegs.get(0); // just one
	    		  edgelist=new EdgeLink(packData,items);
	    		  edge=(EdgeSimple)edgelist.get(0);
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: hex_slide v w ");
	    	  }
	    	  // get 'hexChain' simple closed hex axis
	    	  edgelist=new EdgeLink(packData,"eh "+edge.v+" "+edge.w);
	    	  int ans= packData.hex_slide(edgelist);
	    	  if (ans==0) {
	    		  edge=(EdgeSimple)edgelist.get(0);
	    		  throw new ParserException("failed for edge <"+edge.v+" "+edge.w+">");
	    	  }
	    	  else {
	    		  count +=ans;
	    		  CirclePack.cpb.msg("hex_slide "+edge.v+" "+edge.w+" succeeded");
	    	  }
	    	  return count;
	      }
		  
		  
		  // =========== hh_path =================
		  else if (cmd.startsWith("hh_path")) {
/*			    OPTIONS: (default to -b -S -x)
		(16)    -a     append to given edgepath (i.e., {e} is a list {e..}) 
		(8)		-b     stop when the next edge would lie in the boundary
		(1)	    -c     continue --- no stop options
				-N {n} add at most n edges (counting e)  
		(4)		-S     stop when edge runs into AND lines up with the original 
					   edge e. (If this flag is set, it overrides -x flag only
					   in this instance.)
		(2)		-x	   stop when encountering vertex already hit by the path
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
	    					  throw new ParserException("hh_path: usage hh_path -N {n}");
	    				  }
	    				  if (N<1) throw new ParserException("hh_path: usage -N {n}, n>0");
	    			  }
	    			  if (!its.hasNext()) { // flag seq is last --- should have edgelist
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
	       	  		CirclePack.cpb.errMsg("Failed to open '"+filename+"' for writing");	
	    	  }
	    	  
	    	  // this should be facelist
	    	  items=(Vector<String>)flagSegs.get(0);
	    	  FaceLink facelist=new FaceLink(packData,items);
	    	  if (facelist==null || facelist.size()==0) {
	    		  throw new ParserException("failed to get facelist");
	    	  }
	    	  double frobNorm=PolyBranching.holonomy_trace(packData,fp,facelist,true);
	    	  if (fp!=null) {
	    		  try {
	    			  fp.flush();
	    			  fp.close();
	    		  } catch(Exception ex) {
	    			  CirclePack.cpb.myErrorMsg("holonomy_tr: IOException: "+ex.getMessage());
	    		  }
	    	  }
	    	  if (frobNorm>=0)
	    		  return 1;
	    	  return 0;
	      }
		  
	      // ========== h_dist ===========
	      else if (cmd.startsWith("h_dist")) {
	    	  items=(Vector<String>)flagSegs.get(0); // expect just two complex numbers
	    	  Complex z=new Complex(Double.parseDouble((String)items.get(0)),
	    			  Double.parseDouble((String)items.get(1)));
	    	  Complex w=new Complex(Double.parseDouble((String)items.get(2)),
	    			  Double.parseDouble((String)items.get(3)));
	    	  double dist=HyperbolicMath.h_dist(z,w);
	    	  if (dist==0.0) 
	    		  CirclePack.cpb.msg("h_dist: points are essentially equal");
	    	  else if (dist<0.0)	    		  
	    		  CirclePack.cpb.msg("h_dist: one/both are on or "+
	    				  "outside the unit circle; euclidean separation is "+(-1.0*dist));
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
	    	  items=(Vector<String>)flagSegs.get(0); // should be at most one flag
	    	  String str=(String)items.get(0);
	    	  if (StringUtil.isFlag(str)) {
	    		  if (str.startsWith("-f")) circles=false; // locate faces, circles is default
	    		  items.remove(0);
	    	  }
	    	  try {
	    		  z=new Complex(Double.parseDouble((String)items.get(0)),
	    				  Double.parseDouble((String)items.get(1)));
	    	  } catch (Exception ex) {}
	    	  if (packData.hes>0) { // sphere
	    		  z=SphView.visual_plane_to_s_pt(z);
	    		  z=packData.cpScreen.sphView.toRealSph(z);
	    	  }
	    	  if (circles && (list=packData.cir_search(z))!=null && list.size()>0) { // circles
	    		  count=packData.labellist(list,msg_flag,true);
	    		  packData.activeNode=(Integer)list.get(0);
	    	  }
	    	  else if (!circles && (list=packData.tri_search(z))!=null && list.size()>0) { // faces
	    		  count=packData.labellist(list,msg_flag,false);
	    	  }
	    	  return count;
	      }
		  
	      // ========= layout (deprecated: fix) =======
	      
	      /* 
	       * Various layout functions, ie., locating circles based on 
	       * radii and combinatorics; also checking/updating data on
	       * combinatorics of layout and values of angle sums, etc.
	       * 
	       * NOTE: some options change the information held in faces
	       * about the drawing order, others use use various info to
	       * set centers.
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
	       *    -d v      layout by drawing order, reporting location(s) of v.
	       *    -e {e..}  redo facedraworder with closed edgelist to define red chain
	       *    -F        redo everything
	       *    -f {f..}  (must be last flag) redo facedraworder using face list
	       *    -h vwn    drawing order by 'hex_walk' routine (not active)
	       *    -K        redo combinatorics
	       *    -r {f..}  recompute (don't draw) centers along given facelist
	       *    -s        recompute angle sums
	       *    -l        suppress poorly placed circles (better to use -cf option above)
	       *    -t        compute centers from 'tailored' drawing order; vertices with 'mark'
	       *              set will not (to extent possible) be used in drawing order.
	       *    -T        same as -t, but routine will NOT use the vertices with 'mark' set;
	       *    		  it will simply stop once it has done all it can without them.
	       *    -x        experimental routine.
	       */
		  else if (cmd.startsWith("layout")) {
	    	  double crit=LAYOUT_THRESHOLD;
	    	  // Options for computing center of v:
	    	  //   opt=1: use only one pair of contiguous neighbors, typically specified
	    	  //          in the data of face used to plot v.
	    	  //   opt=2: use all pairs of contiguous neighbors already plotted, average 
	    	  //          the resulting centers for v.
	    	  int opt=2;             // default to use all plotted neighbors
	    	  boolean errflag=false; // only use 'well-plotted' in layout
	    	  boolean dflag=false;   // debugging help
	    	  
	    	  // most typical call
	    	  if (flagSegs.size()==0) { 
	    		  try {    // dflag=true; to spit out debugging file
	    			  if (packData.packDCEL!=null) 
	    				  return jexecute(packData,"DCEL layout");
	    			  packData.fillcurves(); // TODO: is this necessary here?
	    			  packData.comp_pack_centers(errflag,dflag,opt,crit);
	    			  count++;
	    			  return count; // LayoutBugs.pRedEdges(packData);
	    		  } catch(Exception ex) {
	    			  throw new CombException("layout: "+ex.toString());
	    		  }
	    	  }

	    	  // TODO: implement other options appropriate to DCEL case
	    	  if (packData.packDCEL!=null) {
	    		  throw new DCELException("usage: flags for 'layout' calls not "+
	    				  "yet implemented for DCEL setting");
	    	  }
	    	  
	    	  Iterator<Vector<String>> its=flagSegs.iterator();
	    	  String str=null;
	    	  boolean tflag=false;
	    	  Face []newfaces=null;
	    	  while (its.hasNext()) {
	    		  items=(Vector<String>)its.next();
	    		  str=(String)items.remove(0);
	    		  switch(str.charAt(1)) {
	    		  case 'a': // default aims
	    		  {packData.set_aim_default();count++;break;}
	    		  case 's': // recompute angle sums
	    		  {packData.fillcurves();count++;break;}
	    		  case 'c': // compute centers (with options) 
	    		  {
	    			  str=str.substring(2);
	    			  if (str.contains("d")) dflag=true;
	    			  if (str.contains("f")) errflag=true;
	    			  if (str.contains("s")) opt=1;
	    			  if (str.contains("c")) { 
	    				  try {
	    					  crit=Double.parseDouble((String)items.remove(0));
	    				  } catch(Exception ex) {
	    					  crit=LAYOUT_THRESHOLD;
	    					  CirclePack.cpb.myErrorMsg("layout: error reading 'crit' value for -cc flag.");
	    				  }
	    			  }
	    			  try { 
	    				  packData.comp_pack_centers(errflag,dflag,opt,crit);
	    				  count++;
	    			  } catch (Exception ex) {
	    				  CirclePack.cpb.myErrorMsg("layout: error in computing pack centers.");
	    				  return count;
	    			  }
	    			  break;
	    		  }
	    		  case 'd': // 'd [{v}]' layout by drawing order, normalize, report, return
	    			  	    // 'dt [{v}]' for torus only, tries to layout 2-side pairs, with
	    			  		//  optional corner vertex 'v'.
	    		  {
	    			  if (str.charAt(2)=='t') { // does nothing if not a torus
	    				  int v=0;
	    				  if (items.size()>0) {
	    					  str=(String)items.get(0);
	    					  v=NodeLink.grab_one_vert(packData, str);
	    				  }
	    				  if (ProjStruct.torus4layout(packData, v)!=null) { // yes, it worked
//	    					  packData.complex_count(true);
//	    					  packData.fillcurves();
//	    					  try {
//	    						  packData.comp_pack_centers(errflag,dflag,opt,LAYOUT_THRESHOLD);
//	    					  } catch (Exception ex) {
//	    						  CirclePack.cpb.myErrorMsg("layout: error in computing pack centers");
//	    					  }
	    					  count++;
	    				  }
	    				  break;
	    			  }
	    			  else { // 'd' with optional vert whose locations to report
	    				  str=(String)items.get(0);
	    				  int v=NodeLink.grab_one_vert(packData,str);
	    				  packData.layout_report(v,true,false);
	    				  count++;
	    				  break;
	    			  }
	    		  }			
	    		  case 'F': // redo everything
	    		  {
	    			    packData.complex_count(true);
	    			    boolean pflag=false;
	    			    if (items.size()>0 && items.get(0).equals("P"))
	    			    	pflag=true; // use poison verts/edges
	    			    packData.facedraworder(pflag);
	    			    packData.fillcurves();
	    			    packData.set_aim_default();
	    			    try {
	    			    	packData.comp_pack_centers(errflag,dflag,opt,LAYOUT_THRESHOLD);
	    			    } catch (Exception ex) {
	    			    	CirclePack.cpb.myErrorMsg("layout: error in computing pack centers");
	    			    }
	    			    return 1;
	    		  }
	/*    		  case 'h': // drawing order via hex_walk routine
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
	    		  case 'r': // recompute centers along given facelist 
	    		  {
	    			  FaceLink facelist=new FaceLink(packData,items);
	    			  if (facelist==null || facelist.size()==0) {
	    				  CirclePack.cpb.myErrorMsg("layout -r: no faces were provided.");
	    				  break;
	    			  }
	    			  count += packData.reLayList(facelist,0);
	    			  break;
	    		  }
	    		  case 'T': // tailored (falls through to 't')
	    		  {
	    			  tflag=true;
	    		  }
	    		  case 't': // tailored
	    		  {
	       			  str=str.substring(2);
	    			  if (str.contains("d")) dflag=true;
	    			  if (str.contains("f")) errflag=true;
	    			  if (str.contains("s")) opt=1;
	    			  if (str.contains("c")) { 
	    				  try {
	    					  crit=Double.parseDouble((String)items.remove(0));
	    				  } catch(Exception ex) {
	    					  crit=LAYOUT_THRESHOLD;
	    					  CirclePack.cpb.myErrorMsg("layout: error reading 'crit' value for -cc flag: "+
	    							  ex.getMessage());
	    				  }
	    			  }
	    			    int tick=0;
	    			    for (int i=1;i<=packData.nodeCount;i++) 
	    			    	if(packData.getVertMark(i)!=0) tick++;
	    			    if (tick==0) {
	    			    	CirclePack.cpb.myErrorMsg("layout -[tT]: no vertices have been marked?");
	    			    }
	    			    else if ((newfaces=packData.tailor_face_order(tflag))!=null) {
	    			    	packData.faces=newfaces;
	    			    	packData.firstFace=packData.util_A;
	    			    	try {
	    			    		count += packData.comp_pack_centers(errflag,dflag,opt,crit);
	    			    	}catch (Exception ex) {
	    			    		CirclePack.cpb.myErrorMsg("error in computing pack centers: "+ex.getMessage());
	    			    		return count;
	    			    	}
	    			    }
	    			    if (count>0) packData.fillcurves();
	    			    break;
	    		  }
	    		  case 'e': // use edgelist of poison edges.
	    		  {
	    			  if (items.size()==0) { // no edges given, use 'packData.poisonEdges'
	    				  if (packData.poisonEdges==null) {
	    					  throw new ParserException("no poison edge were provided.");
	    				  }
	    			  }
	    			  else packData.poisonEdges=new EdgeLink(packData,items);
	    			  
	    			  if (packData.poisonEdges==null || packData.poisonEdges.size()==0
	    					  || packData.poisonEdges.size()>=
	    						  (packData.nodeCount+packData.faceCount-packData.euler)) {
	    				  throw new CombException("error in poison edges.");
	    			  }
	    			  packData.poisonVerts=null; // trash poison vertices.
	    			  
	    			  // until 1/2011, used 'facedraworder', but that gives problems
//	    			  packData.facedraworder(true);
	    			  
	    			  // use dual tree approach
	    				// build list of dual edges
	    			  EdgeLink cutDuals=new EdgeLink();
	    			  Iterator<EdgeSimple> cutlst=packData.poisonEdges.iterator();
	    			  while (cutlst.hasNext()) {
	    				  EdgeSimple edg=cutlst.next();
	    				  cutDuals.add(packData.dualEdge(edg.v,edg.w));
	    			  }
	    				
	    			  // create the full dual graph
	    			  GraphLink fullDual=DualGraph.buildDualGraph(packData,packData.firstFace,null);
	    			
	    			  // extract the dual tree, but with no edges belonging to 'cutDuals'
	    			  GraphLink tree=fullDual.extractSpanTree(packData.firstFace,cutDuals);
	    				
// debug		
	    		//System.err.println("dTree");		
//	    				Iterator<EdgeSimple> trl=dTree.iterator();
//	    				while (trl.hasNext()) {
//	    					EdgeSimple ed=trl.next();
	    		//System.err.println("<"+ed.v+","+ed.w+">");
//	    				}
	    				
	    			  // get RedList from dual tree
	    			  RedList newRedList=DualGraph.graph2red(packData,tree,packData.firstFace);
	    					
	    			  // process to get redChain
	    			  RedChainer newRC=new RedChainer(packData);
	    			  BuildPacket bP=new BuildPacket();
	    			  bP=newRC.redface_comb_info(newRedList, false);
	    			  if (!bP.success) {
	    				  throw new LayoutException("Layout error in ProjStruct");
	    			  }
	    			  packData.setSidePairs(bP.sidePairs);
	    			  packData.labelSidePairs(); // establish 'label's
	    			  packData.redChain=packData.firstRedEdge=bP.firstRedEdge;
	    			  packData.facedraworder(false);
	    			  // LayoutBugs.log_Red_Hash(packData,packData.redChain,packData.firstRedEdge);
	    			  // dump poisonEdges
	    			  packData.poisonEdges=null;
	    			  try {
	    				  count+=packData.comp_pack_centers(errflag,dflag,opt,crit);
	    				  return count;
	    			  } catch (Exception ex) {
	    				  throw new CombException("error in layout: "+ex.getMessage());
	    			  }
	    		  }
	       		  case 'f': // use given facelist to create list of poison edges.
	    		  {
	    			  if (items.size()==0) { // no faces given
	    				  throw new CombException("no poison edge were provided.");
	    			  }
	    			  FaceLink facelist=new FaceLink(packData,items);
	    			  packData.poisonEdges=packData.outer_edges(facelist);
	    			  if (packData.poisonEdges==null || packData.poisonEdges.size()==0
	    					  || packData.poisonEdges.size()>=
	    						  (packData.nodeCount+packData.faceCount-packData.euler)) {
	    				  throw new CombException("error in number of poison edges.");
	    			  }
	    			  packData.poisonVerts=null; // trash poison vertices.
	    			  packData.facedraworder(true);
	    			  packData.poisonEdges=null;
	    			  try { // dflag=true; to spit out debugging file
	    				  count+=packData.comp_pack_centers(errflag,dflag,opt,crit);
	    				  return count;
	    			  } catch (Exception ex) {
	    				  throw new CombException("-e: error in the layout");
	    			  }
	    		  }   		  
	    		  } // end of switch
	    	  } // end of while
	    	  return count;
	      }		  
		   break;
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
	        	  qackData=CPBase.cpScreens[StringUtil.qFlagParse(str)].getPackData();
	          } catch (Exception ex) {
        		  throw new ParserException("'q' packing is not active");
	          }
			  String filestr=flagSegs.lastElement().get(0);
			  if (!filestr.startsWith("-f") && !filestr.startsWith("-a")) 
				  throw new InOutException("usage: write_custom ... -[fa] {filename}");
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
				  throw new InOutException("Failed to open '"+file.toString()+"' for writing in 'map_bary'");
			  
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
	    			  message=new String("output data appended as file '"+filename+"' in script");
	    		  }
	    		  else if (append_flag)
	    			  message=new String("output data was appended to file '"+
	    					  CPFileManager.CurrentDirectory.getPath()+File.separator+filename+"'");
	    		  else 
	    			  message=new String("output data saved in file '"+
	    					  CPFileManager.CurrentDirectory.getPath()+File.separator+filename+"'");
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
	        	  qackData=CPBase.cpScreens[StringUtil.qFlagParse(str)].getPackData();
	        	  if (qackData==null) throw new ParserException("'q' packing is not active");

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
	        	  if (pverts==null || qverts==null || pverts.size()<2 || qverts.size()<2)
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
	    		  if (!StringUtil.isFlag(str)) { // can happen on first segment only; default to circles 
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
	    		      case 'w': // wipe out all marks, faces/circles
	    			  {
	    				  for (int v=1;v<=packData.nodeCount;v++) 
	    					  packData.setVertMark(v,0);
	    				  for (int f=1;f<=packData.faceCount;f++) 
	    					  packData.setFaceMark(f,0);
	    				  count++;
	    				  break;
	    			  }
	    			  case 'c': // circles
	    			  {
	    				  if (str.length()>2 && str.charAt(2)=='o') { // mark by drawing order
	    					  if (packData.vert_draw_order()>0) {
	    						  int tick=1;
	    						  packData.setVertMark(packData.getAlpha(),tick++);
	    						  int nv=packData.kData[packData.getAlpha()].nextVert;
	    						  while (nv>0 && nv!=packData.getAlpha()) {
	    							  packData.setVertMark(nv,tick++);
	    							  nv=packData.kData[nv].nextVert;
	    							  count++;
	    						  }
	    					  }
	    					  break;
	    				  }
	    				  else if (str.length()>2 && str.charAt(2)=='w') { // wipe out first
	        				  for (int v=1;v<=packData.nodeCount;v++) 
	        					  packData.setVertMark(v,0);
	        				  count++;
	    				  }
	    				  if (items.size()==0) break; // do not default to all here
	    				  NodeLink vlist=new NodeLink(packData,items);
	    				  if (vlist==null || vlist.size()==0) break;
	    				  Iterator<Integer> vs=vlist.iterator();
	    				  while (vs.hasNext()) {
	    					  packData.setVertMark((Integer)vs.next(),1);
	    					  count++;
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
	    				  if (items.size()==0) break; // do not default to all here
	    				  FaceLink flist=new FaceLink(packData,items);
	    				  if (flist==null || flist.size()==0) break;
	    				  Iterator<Integer> fs=flist.iterator();
	    				  while (fs.hasNext()) {
	    					  packData.setFaceMark((Integer)fs.next(),1);
	    					  count++;
	    				  }
	    				  break;
	    			  }
	    			  case 'g': // mark gives generation of circles from given vertlist (default to alpha)
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
	    				  int []gens=packData.label_generations(-1,uP);
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
	    		  
	    	  try {
	    		  items=(Vector<String>)flagSegs.elementAt(0);
	    		  
	    		  // first entry a flags?
	    		  if (StringUtil.isFlag(items.elementAt(0))) {
	    			  if (packData.hes>0 && items.elementAt(0).equals("-r")) {
		    			  puncture_v=NodeLink.grab_one_vert(packData,(String)items.get(1));
		    			  if (puncture_v<1 || puncture_v>packData.nodeCount) {
		    				  CirclePack.cpb.errMsg("improper puncture; ignored");
		    				  puncture_v=-1;
		    			  }
	    			  }
	    			  else if (items.elementAt(0).equals("-r"))
	    				  CirclePack.cpb.errMsg("'-r' flag ignored; "+
	    				  	"applies to spherical case only");
	    		  }
	    		  
	    		  if (items.size()==1 || items.size()==3) { // last entry is [k]
	    			  int k=Integer.parseInt(items.lastElement());
	    			  if (k<1) cycles=1;
	    			  else if (k>100000) cycles=100000;
	    			  else cycles=k;
	    		  }
	    	  } catch(Exception ex) {}

	    	  if ((packData.intrinsicGeom==0 && packData.bdryCompCount>0) || 
	    			  packData.intrinsicGeom < 0) { // hyperbolic case 
	    		  if (packData.hes >=0) {
	    			  packData.geom_to_h();
	    			  packData.setGeometry(-1);
	    		  }
	    		  packData.set_aim_default();
	    		  try { // e.g., there may be no boundary vertices
	    			  jexecute(packData,"set_rad 5.0 b");
	    		  } catch (Exception ex) { } 
	    		  
	    		  if (packData.packDCEL!=null) {
						d_HypPacker h_packer=new d_HypPacker(packData,-1);
						count=h_packer.maxPack(cycles);
	    		  }
	    		  else {
	    			  HypPacker hypPacker=new HypPacker(packData); // will use Orick's code, if available
	    			  count=hypPacker.maxPack(cycles);
	    		  }
	    	  }
	    	  else if (packData.intrinsicGeom == 0) { // must be 1-torus 
	    		  if (packData.hes !=0) {
	    			  jexecute(packData,"geom_to_e");
	    		  }	
	    		  packData.set_aim_default();
	    		  count=packData.repack_call(cycles); // will use Orick's code, if available
				  packData.fillcurves();
				  try {
					  packData.comp_pack_centers(false,false,2,LAYOUT_THRESHOLD);
				  } catch (IOException ex) {};
	    	  }
	    	  else if (packData.intrinsicGeom > 0) { // sphere: Note that NSpole is included
	    		  packData.hes=1;
    			  packData.setGeometry(1);
	    		  packData.set_aim_default();
	    		  if (packData.packDCEL!=null) {
	    			  d_SphPacker d_sphpack=new d_SphPacker(packData,cycles);
	    			  count=d_sphpack.maxPack(cycles);
	    		  }
	    		  else {
	    			  SphPacker sphpack=new SphPacker(packData);
	    			  count=sphpack.maxPack(puncture_v,cycles);
	    		  }
	    	  }
	    	  else return 0;
	    	  if (CirclePack.cpb!=null)
	    		  CirclePack.cpb.msg("max_pack: "+count+" repacking cycles");
	    	  return count;
	      }
	      
	      // ========= migrate ======
	      else if (cmd.startsWith("migrate")) {
	    	  items=(Vector<String>)flagSegs.get(0); // just v w
			  NodeLink vlist=new NodeLink(packData,items);
			  int v=(Integer)vlist.get(0);
			  int w=(Integer)vlist.get(1);
	    	  return packData.migrate(v,w);
	      }
		  break;
	  } // end of 'm' and 'M'
	  case 'n': // fall through
	  case 'N':
	  {
	      // =========== norm_scale ========
	      /* normalize eucl packing using one (only) of these options. 
	      e: radius of v in p equals radius of w in q.
	      a: specified eucl area
	      u: designated vert on unit circle 
	      c: designated vert to prescribed radius
	      h: rotate so designated verts in horizontal line
	      i: scale/rotate to center designated vert at z=i.
	      Return 0 on error. */
	      // TODO: might be better organized
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
	    			  // TODO: is this only for euclidean?
	    		  {
	    			  double x=Double.parseDouble((String)items.get(0));
	    			  if (x<PackData.OKERR) return 0;
	    			  double area=0.0;
	    			  for (int j=1;j<=packData.faceCount;j++) {
	    			      area += packData.faceArea(j);
	    			  }
	    			  Double factor=Double.valueOf(Math.sqrt(x/area));
	    			  if (!factor.isNaN())
	    				  return packData.eucl_scale(factor);
	    		  }
	    		  case 'u': // designated vert on unit circle 
	    		  {
	    			  int v=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    			  double ctr=packData.getCenter(v).abs();
	    			  if (Math.abs(ctr-1.0) < PackData.OKERR) return 1; // don't bother, close enough
	    			  double factor=1.0/ctr;
	    			  return packData.eucl_scale(factor);
	    		  }
	    		  case 'U': // scale (down, only) eucl packing to fit in unit disc, 
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
	    		  case 'c': // scale to give v the prescribed radius
	    		  {
	    			  int v=NodeLink.grab_one_vert(packData,(String)items.get(0));
	    			  double rad=Double.parseDouble((String)items.get(1));
	    			  double factor=rad/packData.getRadius(v);
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
	    			  if (x<PackData.OKERR) return 1; // don't need to adjust
	    			  double factor=1.0/x;
	    			  double ang=(-1.0)*(MathComplex.Arg(z))+Math.PI/2.0;
	    			  packData.rotate(ang);
	    			  return (packData.eucl_scale(factor));
	    		  }
	    		  case 'e': // scale so vertex v has same radius as vert w in pack q
	    			  // data in the form 'q v w'.
	    		  {
	    			  int q=Integer.parseInt((String)items.remove(0));
	    			  if (q<0 || q>=CPBase.NUM_PACKS 
	    					  || !CPBase.cpScreens[q].getPackData().status) 
	    				  throw new ParserException("pack q not valid");
	    			  NodeLink vertlist=new NodeLink(packData,items);
	    			  int v=(Integer)vertlist.get(0);
	    			  double rad=packData.getRadius(v);
	    			  int w=(Integer)vertlist.get(1);
	    			  PackData qackData=CPBase.cpScreens[q].getPackData();
	    			  if (w>qackData.nodeCount || rad<PackData.OKERR) 
	    				  throw new ParserException("problem with 'w'");
	    			  double factor=qackData.getRadius(w)/rad;
	    			  return packData.eucl_scale(factor);
	    		  }	  
	    		  case 't': // u v z1 z2 apply linear transformation to center
	    			  // circles for u v at z1 z2
	    		  {
	    			  int u=1;
	    			  int v=1;
	    			  Complex z1=null;
	    			  Complex z2=null;
	    			  try {
	    				  u=Integer.parseInt((String)items.get(0));
	    				  v=Integer.parseInt((String)items.get(1));
	    				  z1=new Complex(Double.parseDouble((String)items.get(2)),Double.parseDouble((String)items.get(3)));
	    				  z2=new Complex(Double.parseDouble((String)items.get(4)),Double.parseDouble((String)items.get(5)));
	    			  } catch(Exception ex) {
	    				  throw new ParserException("usage: norm_scale -t u v xu yu xv yv");
	    			  }
	    			  Mobius mymob=null;
	    			  try {
	    				  mymob=Mobius.affine_mob(packData.getCenter(u),packData.getCenter(v),z1,z2);
	    			  } catch (Exception ex) {
	    				  throw new DataException("failed to create Mobius: "+ex.getMessage());
	    			  }
	    			  
	    			  // apply this mobius to the packing
	    			  return (packData.apply_Mobius(mymob,new NodeLink(packData,"a")));
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
				return nsPoler.parseNSpole(flagSegs);
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
	    	  int rtncnt=OutPanel.outputter(fp,packData,part[0],part[1],part[2],part[3]);
	    	  if (rtncnt>0) {
	    		  // for "message" window
	    		  if (script_flag) {
	     			  CPBase.scriptManager.includeNewFile(filename);
	    			  message=new String("output data appended as file '"+filename+"' in script");
	    		  }
	    		  else if (append_flag)
	    			  message=new String("output data was appended to file '"+
	    					  CPFileManager.CurrentDirectory.getPath()+File.separator+filename+"'");
	    		  else 
	    			  message=new String("output data saved in file '"+
	    					  CPFileManager.CurrentDirectory.getPath()+File.separator+filename+"'");
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
	    	  if (packData.redChain==null || packData.firstRedEdge==null 
	    			  || packData.getSidePairs()==null 
	    			  || packData.getSidePairs().size()==0) {
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
	        	  throw new ParserException("usage: 'label' not matched with pair_mob");
			  return packData.apply_Mobius(mb,new NodeLink(packData,"a"),true,true,false);
	      }
	      
	      // =========== perp_pack ==========
	      else if (cmd.startsWith("perp")) {
	    	  
	    	  if (!packData.status || packData.euler!=1 || packData.genus!=0) {
	    		  CirclePack.cpb.errMsg("usage: perp_pack only applies in topological disc case.");
	    		  return 0;
	    	  }
	    	  
	      	  int cycles=CPBase.RIFFLE_COUNT;
	    	  if (flagSegs!=null && (items=flagSegs.get(0))!=null) {
    			  try {
    				  int k=Integer.parseInt(items.elementAt(0));
    				  if (k<1) cycles=1;
    				  else if (k>100000) cycles=100000; // 100,000 limit
    				  else cycles=k;
    			  } catch(Exception ex) {
    				  cycles=5000;
    			  }
	    	  }
	    	  
	    	  // convert to euclidean geometry
	    	  CommandStrParser.jexecute(packData,"geom_to_e");
	    	  
	    	  // create copy
	    	  PackData holdPack=packData.copyPackTo();
	    	  
	    	  // double the copy
	    	  CPBase.Vlink=new NodeLink(packData,"B"); // all of the bdry
	    	  int antip=holdPack.double_K(CPBase.Vlink);
	    	  holdPack.hes=1; // make spherical
	    	  
	    	  // max_pack the copy
	    	  int ans=0;
	    	  if ((ans+=CommandStrParser.jexecute(holdPack,"max_pack "+cycles))==0) {
	    		  CirclePack.cpb.errMsg("hum.. ran into packing problem with 'perp_pack'");
	    		  return 0;
	    	  }
	    	
	    	  // normalize the copy
	    	  StringBuilder strbld=new StringBuilder("NSPole "+holdPack.getAlpha()+" "+antip);
	    	  if (CommandStrParser.jexecute(holdPack,strbld.toString())==0) {
	    		  CirclePack.cpb.errMsg("hum.. ran into normalizing problem with 'perp_pack'");
	    		  return 0;
	    	  }
	    	  
	    	  // project the copy to the plane
	    	  CommandStrParser.jexecute(holdPack,"geom_to_e");
	    	  
	    	  // now copy centers and radii into 'packData'
	    	  for (int v=1;v<=packData.nodeCount;v++) {
	    		  packData.setRadiusActual(v,holdPack.getActualRadius(v));
	    		  packData.setCenter(v,holdPack.getCenter(v));
	    	  }
	    	  
	    	  return packData.nodeCount;
	      }
	      
	      // =========== pre_cookie ===========
	      else if (cmd.startsWith("pre_cook")) {
	    	  CookieMonster cM=null;
	    	  try {
	    		  cM=new CookieMonster(packData,flagSegs);
		    	  RedList redlist=cM.pre_cookie();
		    	  if (redlist==null) return 0;
		    	  RedList trace=redlist;
		    	  boolean keepon=true;
		    	  packData.flist=new FaceLink(packData);
		    	  while ((trace!=null && trace!=redlist) || keepon) {
		    		  keepon=false;
		    		  packData.flist.add(trace.face);
		    		  trace=trace.next;
		    	  }
	    	  } catch (Exception ex) {
	    		  System.err.println("Error in CookieMonster or in pre_cookie");
	    		  return 0;
	    	  }
	    	  CirclePack.cpb.msg("pre_cookie: 'flist' contains the red chain");
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
	  				if (packData.puncture_vert(pv)==0) return 0;
	  				packData.xyzpoint=null; // ditch any xyz data
	    	  		if (packData.packDCEL==null) {
	    	  			packData.setCombinatorics();
		    	  		packData.chooseAlpha();
		    	  		packData.chooseGamma();
	    	  		}
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
	    	  			if (packData.puncture_vert(pv)==0) return 0;
	    	  			packData.xyzpoint=null;
	    	  		}
	    	  		else { // puncture a face
	    	  			pf=FaceLink.grab_one_face(packData,flagSegs);
	    	  			count+=pf;
	    	  			if (packData.puncture_face(pf)==0) return 0;
	    	  		}
	    	  	} catch (Exception ex) {
	    	  		CirclePack.cpb.errMsg("attemp to puncture at "+pv+" went wrong.");
	    	  		return 0;
	    	  	}
    	  	
	    	  	if (packData.packDCEL==null) {
	    	  		packData.chooseAlpha();
	    	  		packData.chooseGamma();
	    	  		packData.setCombinatorics();
	    	  	}
	    	  	return count;
	      }
	      
	      // ========== proj =============
	      else if (cmd.startsWith("proj")) {
	/*    	  Options (in order of precedence): 
	    		   -e v   gives vert to go at 1 on equator.
	    		   -m     compute a vertex furthest in generations from alpha and S
	    		          and place it at 1 on equator.
	    		   -t x   dilation amount; sets ratio of spherical radii, alpha/S.
	    		          default is "-t 1" (equal radii at N and S). */   	  
	    	  if (packData.hes>=0 || packData.getBdryCompCount()!=1) {
	    		  throw new ParserException("invalid packing, check conditions");
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
	    		      				  packData.kData[j].utilFlag=1;
	    		      			  else packData.kData[j].utilFlag=0;
	    		      			}
	    		      			packData.kData[packData.getAlpha()].utilFlag=1;
	    		      			UtilPacket uP=new UtilPacket();
//	    		      			int []list=packData.label_generations(-1,uP);
	    		      			if ((E=uP.rtnFlag)>0 && E!=packData.getAlpha() &&
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
	    	  
	    	  // still problems with NSpole
//	    	  if (E==0) { // use NSpole (tangency mode)
//	    		  NSpole.NSCentroid(packData,2);
//	    		  return 1;
//	    	  }
	    	  
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
	    			  || vertlist.size()==0) throw new ParserException();
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
					  jexecute(packData,PackControl.outputFrame.postPanel.createSuffix());
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
						  CPBase.postManager.open_psfile(cpS,mode,nmstr,insstr);
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
						  if (CPBase.postManager.close_psfile(cpS)>0) { 
							  CirclePack.cpb.msg("post: saved PostScript in "+hold);
						  }
						  else 
							  CirclePack.cpb.msg("post: there seems to be no file "+hold);
						  if (sub_cmd!=null && sub_cmd.length()>2) {
							  char d=sub_cmd.charAt(2);
							  switch(d){
							  case 'l': // send to printer
							  {
								  throw new InOutException("'print' postscript option not yet implemented");
								  // TODO:
							  }
							  case 'j': 
							  {
								  throw new InOutException("'jpg' posting option not yet implemented");
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
									  CirclePack.cpb.myErrorMsg("Ghostview popup, command '"+gvcmd+"', has failed.");
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
	    		  if ((q=CPBase.cpScreens[StringUtil.qFlagParse(str)].getPackData())==packData) {
	    			  throw new ParserException();
	    		  }
	    	  } catch (Exception ex) {
	    		  throw new ParserException("usage: qc_dil -q{q} {f..}");
	    	  }
		      if (packData.nodeCount != q.nodeCount || packData.hes!=0 || q.hes!=0) {
		    	  throw new ParserException("comparing p and q requires they be eucl and equal sized");
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
					CirclePack.cpb.msg("quality: worst visual error = "+String.format("%.2e",cP.double_vec.get(0))+
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
    			  CirclePack.cpb.msg("quality: worst relative error = "+String.format("%.2e",cP.double_vec.get(0))+
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
    				  CirclePack.cpb.msg("quality: v = "+cP.int_vec.get(0)+" has radius = NaN");
    			  else if (cP.strValue.startsWith("nc"))
    				  CirclePack.cpb.msg("quality: v = "+cP.int_vec.get(0)+" has center = NaN");
    			  else 
    				  return 0; // some error
    			  return 1;
    		  }
    		  case 'o':
    		  {
    			  if (!cP.error)
    				  CirclePack.cpb.msg("There are no face orientation errors");
    			  else
    				  CirclePack.cpb.msg("Some faces are no oriented correctly, e.g., "+cP.int_vec.get(0));
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
	      // =========== rotate ===========
	      if (cmd.startsWith("rotate")) {
	    	  double x=StringUtil.getOneDouble(flagSegs);
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
	    	  if (packData.overlapStatus) oldReliable=true;
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
	    			  case 'n': // 'noC' means not to use C libraries
	    			  {
	    				  if (str.contains("noC"))
	    					  use_C=false;
	    				  break;
	    			  }
	    			  // TODO: need to implement other packing routines and put
	    			  //   in the options here, e.g., 't' (which should be default for
	    			  //   inversive distance cases.)
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
			  
			  if (cmd.startsWith("bary")) { // remove barycenters
	    		  NodeLink vertlist=new NodeLink(packData,items);
	    		  
	    		  // default to all 3-degree interior
	    		  if (vertlist==null || vertlist.size()==0) 
	    			  vertlist=new NodeLink(packData,"{c:(i).and.(d.eq.3)}");
	    		  
	    		  count=packData.remove_barycenters(vertlist);
	    		  if (count>0) { 
	    			  CirclePack.cpb.msg("rm_bary: removed "+count+" barycenters from p"+packData.packNum);
	    		  }
	    		  else return 1; // don't want to return 0 
			  }

			  else if (cmd.startsWith("cir")) { // rm_circles
	    		  NodeLink vertlist=new NodeLink(packData,items);
	    		  count=packData.remove_circle(vertlist);
	    		  if (count>0) { 
	    			  CirclePack.cpb.msg("rm_cir: removed "+count+" circles from p"+packData.packNum);
	    			  if (packData.packDCEL!=null)
	    				  return count;
	    		  }
	    		  else return 1;
	    	  }
			  else if (cmd.startsWith("quad")) { // remove one quad vertex
				  try {
		    		  NodeLink vertlist=new NodeLink(packData,items);
					  int v=(Integer)vertlist.get(0);
					  int w=(Integer)vertlist.get(1);
					  count=packData.remove_quad_vert(v,w);
					  if (count>0) { 
		    			  CirclePack.cpb.msg("rm_quad: removed quad vertex "+v);
					  }
					  else return 1;
				  } catch (Exception ex) {
					  throw new ParserException("must specify 'v' and 'w'");
				  }
	    	  }
			  else if (cmd.startsWith("edge")) { // remove edges
				  String strg=items.get(0);
				  if (StringUtil.isFlag(strg)) {
					  if (!strg.equals("-c"))
						  throw new ParserException("illegal flag "+strg);
					  // collapsing int/bdry edges
					  items.remove(0); // shuck this entry
					  EdgeLink edgelist=new EdgeLink(packData,items);
					  count=packData.collapse_edge(edgelist);
				  }
				  else { // removing bdry edges
					  EdgeLink edgelist=new EdgeLink(packData,items);
					  count=packData.remove_edge(edgelist);
				  }
	    		  if (count>0) { 
	    			  CirclePack.cpb.msg("rm_edge: removed "+count+" edges from p"+packData.packNum);
	    		  }
	    		  else return 1;
			  }
			  
			  // fix things up
			  packData.chooseAlpha();
			  packData.chooseGamma();

		      packData.setCombinatorics();
		      packData.fillcurves();
		      return count;
	      }
	      
	      // ========= reorient =======
	      else if (cmd.startsWith("reorie")) {
	    	  int ans=packData.reverse_orient();
	    	  if (ans>0) {
	    		  packData.setCombinatorics();
		    	  if (packData.packDCEL!=null) {
		    		  PackDCEL pdcel=packData.packDCEL.reverseOrientation();
		    		  pdcel.p=packData;
		    		  packData.packDCEL=pdcel;
		    	  }
	    	  }
	    	  return ans;
	      }
	      
	      // ========= red_from_el ======
	      else if (cmd.startsWith("red_from_el")) {
	    	  BuildPacket bP=packData.redChainer.red_from_outlist(packData.elist);
	    	  if (!bP.success) return 0;
	    	  packData.redChain=bP.redList;
	    	  packData.firstRedEdge=bP.firstRedEdge;
	    	  packData.setSidePairs(bP.sidePairs);
	    	  return 1;
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
					if (!packData.haveSchwarzians()) {
						if (CommandStrParser.jexecute(packData,"set_sch")<=0)
							throw new DataException("failed to compute schwarzians");
					}
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
								NodeLink nl = new NodeLink(packData, items);
								hits +=packData.radiiSliders.addObject(nl.toString());
							} else if (type == 1 && packData.schwarzSliders != null) {
								GraphLink el= new GraphLink(packData, items);
								hits +=packData.schwarzSliders.addObject(el.toString());
							} else if (type == 2 && packData.angSumSliders != null) {
								NodeLink nl= new NodeLink(packData, items);
								hits +=packData.angSumSliders.addObject(nl.toString());
							}

							break;
						}
						case 'r': // remove object
						{
							items.remove(0);
							if (type == 0 && packData.radiiSliders != null) {
								NodeLink nl = new NodeLink(packData, items);
								hits +=packData.radiiSliders.removeObject(nl.toString());
							} else if (type == 1 && packData.schwarzSliders != null) {
								GraphLink gl= new GraphLink(packData, items);
								hits +=packData.schwarzSliders.addObject(gl.toString());
							} else if (type == 1 && packData.schwarzSliders != null) {
								NodeLink nl= new NodeLink(packData, items);
								hits +=packData.angSumSliders.addObject(nl.toString());
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
							throw new ParserException("usage: illegal slider flag");
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
				CirclePack.cpb.errMsg("OBE: 'sch_layout' has been replaced by 'dual_layout'.");
				return 0;
			}
			

		  // =============== scale ==========
		  else if (cmd.startsWith("scale") && !cmd.startsWith("scale_")) {
			  
// debug  deBugging.LayoutBugs.log_RedList(packData,packData.redChain); deBugging.LayoutBugs.log_RedCenters(packData);
			  
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
			  return packData.scale_rad(CPBase.cpScreens[qnum].getPackData(),factor,vertlist);
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
		    	  else packData.swap_nodes(nL.get(0),nL.get(1));
		    	  if (packData.packDCEL==null)
		    		  packData.setCombinatorics();
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
	    			  EdgeLink edgelist=new EdgeLink(packData,items);
	    			  if (!packData.overlapStatus) packData.alloc_overlaps();
	    			  Iterator<EdgeSimple> elist=edgelist.iterator();
	    			  EdgeSimple edge=null;
	    			  double value=1.0;
	    			  int j;
	    			  while (elist.hasNext()) {
	    				  edge=(EdgeSimple)elist.next();
						  j=packData.nghb(edge.v,edge.w);
	    				  if (factor_flag) { // use a factor
	    					  if (jiggle) {
	    						  factor=Math.exp(randizer.nextGaussian()*pctg);
	    					  }
	    					  else factor=low+randizer.nextDouble()*(high/low);
	    					  value=packData.getInvDist(edge.v,packData.kData[edge.v].flower[j])*factor;
	    				  }
	    				  else value=low+randizer.nextDouble()*(high/low);
	    				  packData.set_single_invDist(edge.v,edge.w,value);
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
	    						  factor=Math.exp(randizer.nextGaussian()*pctg);
	    					  }
	    					  else factor=low+randizer.nextDouble()*(high-low);
	    					  packData.setRadiusActual(v,packData.getActualRadius(v)*factor);
	    				  }
	    				  else packData.setRadiusActual(v,low+randizer.nextDouble()*(high-low));
	    				  count++;
	    			  }
	    		  }
	    		  if (count>0) packData.fillcurves();
	    		  return count;
	    	  }
	    	  
	    	  // ========= set_poison ==========
	    	  if (cmd.startsWith("poison")) { // set utilFlags=-1 for poison vertices
	    		  return packData.set_poison((Vector<String>)flagSegs.get(0));
	    	  }

	    	  // ========= set_schwarzians ==========
	    	  if (cmd.startsWith("sch")) { 
    			  EdgeLink clink=null; // those done using current layout
    			  EdgeLink rlink=null; // those done using current radii only

	    		  // no arguments, set all to current values based on radii
    			  if (flagSegs==null || flagSegs.size()==0 || flagSegs.get(0).size()==0) 
    				  rlink=new EdgeLink(packData,"a");
    			  else if ((items=flagSegs.get(0)).size()!=0 && StringUtil.isFlag(items.get(0))) {
    				  Iterator<Vector<String>> its=flagSegs.iterator();
    				  while (its.hasNext()) {
    					  items=flagSegs.remove(0);
    					  String str=items.get(0);
    					  if (StringUtil.isFlag(str)) {
    						  items.remove(0);
    						  char c=str.charAt(1);
    						  switch(c) {
    						  case 'r': // use current radii
    						  {
    							  rlink=new EdgeLink(packData,items); // default to all
    							  break;
    						  }
    						  default: // use current layout
    						  {
    							  clink=new EdgeLink(packData,items); // default to all
    						  }
    						  } // end of switch
    					  }
    					  else // default to all using current layout
    						  clink=new EdgeLink(packData,items);
    				  } // end of reading option
    			  }
    				
    			  // if method is not flagged, then use given values for designated edges
    			  if (flagSegs!=null && flagSegs.size()>0 && (items=flagSegs.get(0)).size()>0
    					  && clink==null && rlink==null) {
    				  double sch_value=0.0;
    				  try {
    					  sch_value=Double.parseDouble(items.remove(0));
    				  } catch (Exception ex) {
    					  throw new InOutException("usage: set_sch x {v w ...}");
    				  }
    				  EdgeLink elink=new EdgeLink(packData,items);
    				  if (elink==null || elink.size()==0)
    					  return count;
    				  
    				  // allocate if needed
    				  if (!packData.haveSchwarzians()) {
    					  for (int vv=1;vv<=packData.nodeCount;vv++)
    						  packData.kData[vv].schwarzian=new double[packData.countFaces(vv)+1];
    				  }
    				  
    				  Iterator<EdgeSimple> elk=elink.iterator();
    				  while (elk.hasNext()) {
    					  try {
    						  EdgeSimple edge=elk.next();
    						  int ind_vw=packData.nghb(edge.v, edge.w);
    						  int ind_wv=packData.nghb(edge.w, edge.v);
    						  int f=packData.face_right_of_edge(edge.w,edge.v);
    						  int g=packData.face_right_of_edge(edge.v,edge.w);
    						  if (g<0 || f<0) {
    							  packData.kData[edge.v].schwarzian[ind_vw]=0.0;
    							  packData.kData[edge.w].schwarzian[ind_wv]=0.0;
    							  count++;
    						  }
    						  else {
    							  packData.kData[edge.v].schwarzian[ind_vw]=sch_value;
    							  packData.kData[edge.w].schwarzian[ind_wv]=sch_value;
    							  count++;
    						  }
    					  } catch (Exception ex) {
    						  throw new DataException("error in set_schwarz: perhaps schwarzians not allocated?");
    					  }
    				  }
    				  return count;
    			  }
    			  
    			  if (clink!=null) {
    				  count += Schwarzian.set_rad_or_cents(packData, clink,2);
	    		  }
    			  if (rlink!=null) {
    				  count += Schwarzian.set_rad_or_cents(packData, rlink,1);
	    		  }
	    		  
    			  return count;
	    	  }

	    	  // ========= set_invdist  ========
	    	  if (cmd.startsWith("invdist")) { 
	    		  Iterator<Vector<String>> its=flagSegs.iterator();
	    		  items=(Vector<String>)its.next();
	    		  String str=(String)items.remove(0);
	    		  EdgeLink edgelist=null;
				  EdgeSimple edge=null;
	    		  if (str.startsWith("-d")) { // default: i.e. tangency
	    			  if (items.size()>0) { // a list of edges given
	    				  if (!packData.overlapStatus) return 1; // already at default
	    				  edgelist=new EdgeLink(packData,items);
	    				  Iterator<EdgeSimple> elist=edgelist.iterator();
	    				  while(elist.hasNext()) {
	    					  edge=(EdgeSimple)elist.next();
	    					  count+=packData.set_single_invDist(edge.v,edge.w,1.0);
	    				  }
	    			  }
	    			  else {
	    				  packData.free_overlaps();
	    				  count=packData.nodeCount;
	    			  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("set_over: set "+count+
	    					  " overlaps to default");
	    			  return count;
	    		  }
	    		  if (str.startsWith("-c")) { // set to current
	    			  if (items.size()>0) { // a list of edges given
	    				  edgelist=new EdgeLink(packData,items);
	    			  }
	    			  else edgelist=new EdgeLink(packData,"a");
	    			  if (!packData.overlapStatus) packData.alloc_overlaps();
	    			  Iterator<EdgeSimple> elist=edgelist.iterator();
	    			  while(elist.hasNext()) {
	    				  edge=(EdgeSimple)elist.next();
	    				  count+=packData.set_single_invDist(
	    						  edge.v,edge.w,packData.comp_inv_dist(edge.v,edge.w));
	    				  // note: inv_dist routine not very robust 
	    			  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("set_over: set "+count+
	    					  " overlaps to default");
	    			  return count;
	    		  }
	    		  if (str.startsWith("-t")) { // truncate at given value
	    		      if (!packData.overlapStatus) return 1;
	    			  double uplim=Double.parseDouble((String)items.get(0));
	    		      if (uplim<=1.0) {
	    		    	  throw new DataException("usage: truncation value x must be >=1");
	    		      }
	   				  edgelist=new EdgeLink(packData,items);
					  Iterator<EdgeSimple> elist=edgelist.iterator();
					  while(elist.hasNext()) {
						  edge=(EdgeSimple)elist.next();
						  int nb=packData.nghb(edge.v,edge.w);
						  if (packData.getInvDist(edge.v,packData.kData[edge.v].flower[nb])>uplim)
						  count+=packData.set_single_invDist(edge.v,edge.w,uplim);
					  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("Cut "+(int)count/2+
	    					   " inversive distances down to max of "+uplim);
	    		      return count;
	    		  }
	    		  if (str.startsWith("-x")) { // use xyz-data: set all based on max/min distances
	    			  if (flagSegs.size()==0) { // no filename, use 'packData.xyzpoint'
	    				  return packData.set_xyz_overlaps(packData.xyzpoint,packData.nodeCount,1);
	    			  }
	    			  items=(Vector<String>)flagSegs.get(1);
	    			  String dataname=StringUtil.reconItem(items);
	    			  boolean in_script=false;
	    			  if (dataname.startsWith("-s")) { // get from script
	    				  in_script=true;
	    				  dataname=(String)items.get(1);
	    			  }
	    			  if (CPFileManager.readDataFile(packData,dataname,in_script,1)==0)
	    				  return 0;
	    			  return packData.set_xyz_overlaps(packData.xyzpoint,packData.nodeCount,1);
	    		  }
	    		  if (str.startsWith("-h")) { // use packData.xyzpoint, 
	    			  // but set based on local edge lengths
	    			  return packData.set_xyz_overlaps(packData.xyzpoint,packData.nodeCount,2);
	    		  }
	    		  
	    		  // no flag? 'inv dist' followed by edge list (default to all)
	    		  // NOTE: this is always an inversive distance, NOT an angle/Pi.
	    		  //    [-1,0) deep overlap (cos(t), where t is in (Pi/2, Pi]
	    		  //    [0,1) overlap (cos(t), where t is in (0,Pi/2]
	    		  //    1 tangency
	    		  //    (1, infty) separated (cosh(t), where t is hyp distance of circles
	    		  double invDist=Double.parseDouble(str);
     			  if (invDist<-1.0) throw new ParserException("'invDist' < -1.0");
	     		  
	     		  // Is space allocated?
	     		  if (!packData.overlapStatus) {
	     			  // 'invDist' essentially default and nothing to reset? 
	     			  if (Math.abs(invDist-1.0)<=.0000001)  
	     				  return 0;
	     			  packData.alloc_overlaps();
	     		  }
	     		  
	     		  edgelist=new EdgeLink(packData,items); // default to all
	     		  Iterator<EdgeSimple> elist=edgelist.iterator();
	     		  while (elist.hasNext()) {
	     			  edge=(EdgeSimple)elist.next();
	     			  count+=packData.set_single_invDist(edge.v,edge.w,invDist);
	     		  }
				  packData.fillcurves();
				  CirclePack.cpb.msg("Set "+count+
						   " inversive distances to "+invDist);
			      return count;	  

	    	  }
	    	  
	    	  // ========= set_overlaps ========
	    	  if (cmd.startsWith("ove")) { 
	    		  Iterator<Vector<String>> its=flagSegs.iterator();
	    		  items=(Vector<String>)its.next();
	    		  String str=(String)items.remove(0);
	    		  EdgeLink edgelist=null;
				  EdgeSimple edge=null;
	    		  
	    		  if (str.startsWith("-d")) { // default: i.e. tangency
	    			  if (items.size()>0) { // a list of edges given
	    				  if (!packData.overlapStatus) return 1; // already at default
	    				  edgelist=new EdgeLink(packData,items);
	    				  Iterator<EdgeSimple> elist=edgelist.iterator();
	    				  while(elist.hasNext()) {
	    					  edge=(EdgeSimple)elist.next();
	    					  count+=packData.set_single_invDist(edge.v,edge.w,1.0);
	    				  }
	    			  }
	    			  else {
	    				  packData.free_overlaps();
	    				  count=packData.nodeCount;
	    			  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("set_over: set "+count+
	    					  " overlaps to default");
	    			  return count;
	    		  }
	    		  if (str.startsWith("-c")) { // set to current
	    			  if (items.size()>0) { // a list of edges given
	    				  edgelist=new EdgeLink(packData,items);
	    			  }
	    			  else edgelist=new EdgeLink(packData,"a");
	    			  if (!packData.overlapStatus) packData.alloc_overlaps();
	    			  Iterator<EdgeSimple> elist=edgelist.iterator();
	    			  while(elist.hasNext()) {
	    				  edge=(EdgeSimple)elist.next();
	    				  count+=packData.set_single_invDist(
	    						  edge.v,edge.w,packData.comp_inv_dist(edge.v,edge.w));
	    				  // note: inv_dist routine not very robust 
	    			  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("set_over: set "+count+
	    					  " overlaps to default");
	    			  return count;
	    		  }
	    		  if (str.startsWith("-t")) { // truncate at given value
	    		      if (!packData.overlapStatus) return 1;
	    			  double uplim=Double.parseDouble((String)items.get(0));
	    		      if (uplim<=1.0) {
	    		    	  throw new DataException("usage: truncation value x must be >=1");
	    		      }
	   				  edgelist=new EdgeLink(packData,items);
					  Iterator<EdgeSimple> elist=edgelist.iterator();
					  while(elist.hasNext()) {
						  edge=(EdgeSimple)elist.next();
						  int nb=packData.nghb(edge.v,edge.w);
						  if (packData.getInvDist(edge.v,packData.kData[edge.v].flower[nb])>uplim)
						  count+=packData.set_single_invDist(edge.v,edge.w,uplim);
					  }
	    			  packData.fillcurves();
	    			  CirclePack.cpb.msg("Cut "+(int)count/2+
	    					   " inversive distances down to max of "+uplim);
	    		      return count;
	    		  }
	    		  if (str.startsWith("-x")) { // use xyz-data: set all based on max/min distances
	    			  if (flagSegs.size()==0) { // no filename, use 'packData.xyzpoint'
	    				  return packData.set_xyz_overlaps(packData.xyzpoint,packData.nodeCount,1);
	    			  }
	    			  items=(Vector<String>)flagSegs.get(1);
	    			  String dataname=StringUtil.reconItem(items);
	    			  boolean in_script=false;
	    			  if (dataname.startsWith("-s")) { // get from script
	    				  in_script=true;
	    				  dataname=(String)items.get(1);
	    			  }
	    			  if (CPFileManager.readDataFile(packData,dataname,in_script,1)==0)
	    				  return 0;
	    			  return packData.set_xyz_overlaps(packData.xyzpoint,packData.nodeCount,1);
	    		  }
	    		  if (str.startsWith("-h")) { // use packData.xyzpoint, 
	    			  // but set based on local edge lengths
	    			  return packData.set_xyz_overlaps(packData.xyzpoint,packData.nodeCount,2);
	    		  }
	    		  
	    		  // no flag? <a> = *inv_dist or <a> = overlap, followed by edge list (default all)
	    		  double invDist=1.0;  // NOTE: may be overlap or inversive distance
	     		  if (str.charAt(0)=='*') { // indicates inv_dist in (1, infty)
	     			  str=str.substring(1,str.length());
	     			  invDist=Double.parseDouble(str);
	     			  if (invDist<0.0) throw new ParserException("'invDist' negative");
	     		  }
	     		  else {
	     			  invDist=Double.parseDouble(str);
	     			  if (invDist<0.0 || invDist>1.0) 
	     				  throw new ParserException("Use '*' for 'inversive distance' parameter");
	     			  invDist=Math.cos(invDist*Math.PI);
	     		  }
	     		  
	     		  // Is space allocated?
	     		  if (!packData.overlapStatus) {
	     			  // 'invDist' essentially default and nothing to reset? 
	     			  if (Math.abs(invDist-1.0)<=.0000001)  
	     				  return 0;
	     			  packData.alloc_overlaps();
	     		  }
	     		  
	     		  edgelist=new EdgeLink(packData,items); // default to all
	     		  Iterator<EdgeSimple> elist=edgelist.iterator();
	     		  while (elist.hasNext()) {
	     			  edge=(EdgeSimple)elist.next();
	     			  count+=packData.set_single_invDist(edge.v,edge.w,invDist);
	     		  }
				  packData.fillcurves();
				  CirclePack.cpb.msg("Set "+count+
						   " inversive distances to "+invDist);
			      return count;
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
	    				  qackData=CPBase.cpScreens[qnum].getPackData();
	    				  
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
	    					  throw new DataException("set_rad error in flag options: "+ex.getMessage());
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
	    			  if (packData.hes>=0 && rad<=0.0) {
	    				  throw new DataException("radius can be negative only in the hyperbolic setting.");
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
	    	  if (cmd.startsWith("center")) {
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
	    			  if (PackControl.functionPanel.ftnField.getText().trim().length()==0) {
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
	    				  Complex w=PackControl.functionPanel.getFtnValue(z);
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
		    					  packData.setCenter(vv,packData.xyzpoint[vv].x,packData.xyzpoint[vv].y);
		    					  count++;
		    				  }
		    				  else { // sphere?
		    					  packData.setCenter(vv,SphericalMath.proj_vec_to_sph(packData.xyzpoint[vv]));
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
	        						 CirclePack.cpb.myErrorMsg("set_aim: usage: -% x. Must have x>0.");
	        					 }
	        					 items.remove(0);
	        				  } catch (NumberFormatException nfe) {
	        					  CirclePack.cpb.myErrorMsg("set_aim: usage: -% x where x>0 is multiplicative factor.");
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
	        			  case 't': // give factor by which to move toward 2pi for interior
	        			  {
	        				  mode=5; 
	        				  try {
	         					 inc=Double.parseDouble(items.get(0));
	        					 if (inc<=0.0 || inc>1.0) {
	        						 CirclePack.cpb.myErrorMsg("set_aim: usage: -t x, x in (0,1].");
	        					 }
	        					 items.remove(0);
	        				  } catch (NumberFormatException nfe) {
	        					  CirclePack.cpb.myErrorMsg("set_aim: usage: -t x where x>0 is factor.");
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
	        					  CirclePack.cpb.myErrorMsg("set_aim: usage: -t x where x>0 is factor.");
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
	    					  CirclePack.cpb.myErrorMsg("set_aim: usage: x {v..} where x>0 is intended aim.");
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
//	        			  KData []kdata=packData.kData;
//	        			  RData []rdata=packData.rData;
	        			  
	        			  while (vl.hasNext()) {
	        				  int v=(Integer)vl.next();
	        				  if (mode==1) {
	        					  packData.set_aim_default(vertlist);
	        					  count++;
	        				  }
	        				  else if (mode==2) { packData.rData[v].aim=packData.rData[v].curv;count++;} // current
	        				  else if (mode==3) { packData.rData[v].aim=packData.rData[v].aim+inc*Math.PI;count++;} // TODO: not right adjustment
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
	    			  packData.kData[(Integer)vlist.next()].plotFlag=pf;
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
	        	  ViewBox vbox=packData.cpScreen.realBox;
	        	  Complex utilz=new Complex(0.0);
	        	  char c;
	    	  
	        	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
	    	  
	        	  while (nextFlag.hasNext() && (items=nextFlag.next()).size()>0) {
	        		  if (StringUtil.isFlag(items.elementAt(0))) {
	        			  c=items.get(0).charAt(1);
	        			  switch(c) {
	    			  
	    			  case 'a': // adjust up to see all circles (eucl only)
	    			  {
	    				  double dist;
	    				  if (packData.hes!=0) return 1; // not an error, but no effect
	    				  RData []rdata=packData.rData;
	    				  
	    				  double hwid=vbox.getWidth()/2.0;
	    				  double hhgt=vbox.getHeight()/2.0;
	    			      
	    			      // get start at vert 1
	    			      double maxw = Math.abs(rdata[1].center.x)+rdata[1].rad;
	    			      double maxh = Math.abs(rdata[1].center.y)+rdata[1].rad;
	    			      for (int i=2;i<=packData.nodeCount;i++) {
	    			    	  dist=Math.abs(rdata[i].center.x)+rdata[i].rad;
	    			    	  if (dist>maxw) maxw=dist;
	    			    	  dist=Math.abs(rdata[i].center.y)+rdata[i].rad;
	    			    	  if (dist>maxh) maxh=dist;
	    			      }
	    			      if (maxw>hwid || maxh>hhgt) { // need to scale vbox up
	    			    	  double factor=(maxw/hwid>maxh/hhgt) ? maxw/hwid : maxh/hhgt;
	    			    	  vbox.scaleView(1.1*factor); // scale with a margin
	    			    	  cpS.update(2);
	    			      }
	    			      count++;
	    			      break;
	    			  }
	    			  case 'b': // set real box (lx,ly), (rx,ry)
	    				  // TODO: need tailored versions for speed, change 'CPScreen.update' too
	    			  {
	    				  double []corners=new double[4];
	    				  try {
	    					  for (int i=0;i<4;i++)
	    						  corners[i]=Double.parseDouble(items.get(i+1));
	    					  cpS.realBox.setView(new Complex(corners[0],corners[1]),
	    							  new Complex(corners[2],corners[3]));
	    				  } catch (Exception ex) {
	    					  CirclePack.cpb.myErrorMsg("'"+cmd+"' parsing error.");
	    					  return count;
	    				  }
	    				  count++;
	    				  cpS.update(2);
	    				  break;
	    			  }
	    			  case 'd':	// default canvas size, sphView
	    			  {
	    				  vbox.reset();
	    				  count++;
	    				  cpS.update(2);
	    				  cpS.sphView.defaultView();
	    				  break;
	    			  }
	    			  case 'f': // scale by given factor
	    			  {
	    				  try {
	    					  count += packData.cpScreen.realBox.scaleView(Double.parseDouble(items.get(1)));
	    					  cpS.update(2);
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen -f <x>: "+nfe.getMessage());
	    				  }
	    				  break;
	    			  }
	    			  case 'i': // incremental moves (typically from mouse click)
	    			  {
	    				  try {
	    					  utilz=new Complex(Double.parseDouble(items.get(1)),Double.parseDouble(items.get(2)));
	    					  count +=vbox.transView(utilz);
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen -i <x> <y>: "+nfe.getMessage());
	    				  }
	    				  break;
	    				  
	    			  }
	    			  case 'h': // height (or fall through for width)
	    			  {}
	    			  case 'w': // width
	    			  {  	
	    				  try {
	    					  double f=Double.parseDouble(items.get(1));
	    					  count += cpS.realBox.scaleView(f/vbox.getWidth());
	    					  cpS.update(2);
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen -w(or h) <x>: "+nfe.getMessage());
	    				  }
	    				  break;
	    			  }
	    			  case 'c': // center at the origin. Fall through first to 'z', 
	    				  // then further, to 'v'.
	    			  {
	    				  utilz=new Complex(0.0); // default, in case of problems below
	    			  }
	    			  case 'z': // Fall through to 'v'
	    			  {
	    				  if (c=='z') {
	       				  try {
	    					  utilz=new Complex(Double.parseDouble(items.get(1)),Double.parseDouble(items.get(2)));
	    				  } catch (NumberFormatException nfe) {
	    					  CirclePack.cpb.myErrorMsg("usage: set_screen -[zc] <x> <y>: "+nfe.getMessage());
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
	    						  utilz=cpS.sphView.toApparentSph(utilz);
	    						  // TODO: do we need to check if on back?
	    					  } 
	    					  } catch (NumberFormatException nfe) {
	    						  CirclePack.cpb.myErrorMsg("usage: set_screen -v <n>: "+nfe.getMessage());
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
    				  cpS.update(2);
	    		  }
	    	  } // end of while

	    	  if (count>0) {
	    		  cpS.repaint();
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
	    	  int []ans=packData.slit_complex(vertlist);
	    	  if (ans==null) return 0;
	    	  return ans[3]; // return count of edges slit
	      }
		  break;
      } // end of 's'
      case 't':
      {

		  // ========= triG ===========
		  if (cmd.startsWith("triG")) {
			  double a=2.0;
			  double b=3.0;
			  double c=7.0;
			  int maxgen=5;
			  items=null;
			  try {
		      	  Iterator<Vector<String>> nextFlag=flagSegs.iterator();
		      	  while (nextFlag.hasNext()) {
		      		  items=(Vector<String>)nextFlag.next();
		      		  String str=(String)items.get(0);
		      		  if (StringUtil.isFlag(str)) {
		      			  char ch=str.charAt(1);
		      			  switch(ch) {
		      			  // Note: triangle has angles pi/a, pi/b, pi/c
		      			  // by convention, a, b, c are (at worst) half integers
		      			  case 'd': { // read a b c 
		    		    	  a=Double.parseDouble((String)items.get(1));
		    		    	  b=Double.parseDouble((String)items.get(2));
		    		    	  c=Double.parseDouble((String)items.get(3));
		    		    	  break;
		      			  }
		      			  case 'g': { // number of generations
		      				  int mg=Integer.parseInt((String)items.get(1));
		      				  if (mg<1 || mg>1000) mg=10; // default
		      				  maxgen=mg;
		      				  break;
		      			  }
		      			  } // end of switch
		      		  }
		      		  else { // not flagged? default to a b c
	    		    	  a=Double.parseDouble((String)items.get(0));
	    		    	  b=Double.parseDouble((String)items.get(1));
	    		    	  c=Double.parseDouble((String)items.get(2));
		      		  }
		      	  } // end of while
			  } catch (Exception ex) {
				  if (items!=null)
				  	throw new ParserException("usage: -d {a b c} -g {n}");
			  }
			  
			  // set degrees: params should be (at worst) half ints
			  if (Math.abs(2.0*a-(int)(2.0*a))>.0001 ||
					  Math.abs(2.0*b-(int)(2.0*b))>.0001 ||
					  Math.abs(2.0*c-(int)(2.0*c))>.0001)
				  throw new DataException("paremeters must be form n/2");
			  if (Math.abs(2.0*((int)a)-2.0*a)>.1) {
				  if (((int)b-(int)c)>0.1) 
					  throw new DataException("'a' is half-integer, but b, c not equal");
			  }
			  else if (Math.abs(2.0*((int)b)-2.0*b)>.1) {
				  if (((int)a-(int)c)>0.1) 
					  throw new DataException("'b' is half-integer, but a, c not equal");
			  }
			  if (Math.abs(2.0*((int)c)-2.0*c)>.1) {
				  if (((int)b-(int)a)>0.1) 
					  throw new DataException("'c' is half-integer, but a, b not equal");
			  }
//			  int []degs=new int[3]; 
			  int A=(int)(2.01*a);
			  int B=(int)(2.01*b);
			  int C=(int)(2.01*c);
			  
		   	  try{
		   		  // have to hold this; packData get's replaced
		   		  int pnum=packData.packNum;
		   		  PackData newPack=PackCreation.triGroup(A,B,C,maxgen);
		   		  if (newPack!=null) {
		   			  CirclePack.cpb.swapPackData(newPack,pnum,false);
		   			  packData=newPack;
		   			  return packData.nodeCount;
		   		  }
		   		  CirclePack.cpb.errMsg("triGroup failed to create a packing");
		   		  return 0;
		   	  } catch(Exception ex) {
		   		  throw new ParserException(" "+ex.getMessage());
		   	  }
		   	  
			  // geometry
/*			  int hees=-1; // default: hyp
			  double recipsum=1.0/a+1.0/b+1.0/c;
			  if (Math.abs(recipsum-1)<.0001)
				  hees=0; // eucl
			  else if (recipsum>1.0) hees=1; // sph
			  
			  int gencount=1;
			  // start seed
		   	  try{
		   		  // have to hold this; packData get's replaced
		   		  CPScreen cps=packData.cpScreen;  
		   		  count += cps.seed(degs[0],hees);
		   		  packData=cps.packData; 
		   	  } catch(Exception ex) {
		   		  throw new ParserException(" "+ex.getMessage());
		   	  }
		   	  // mark vertices of first flower
		   	  packData.setVertMark(1,0);
		   	  for (int j=2;j<=packData.nodeCount;j++) {
		   		  packData.setVertMark(j,(j)%2+1);
		   	  }
		   	  count++;
		   	  
		   	  // hyperbolic cases
			  while (hees<0 && gencount<=maxgen) { 
				  if (packData.bdryCompCount==0)
					  throw new CombException("no boundary verts at gencount = "+gencount);
				  int []alt=new int[2];
				  int w=packData.bdryStarts[1];
				  int stopv=packData.kData[w].flower[packData.getNum(w);
				  int next=packData.kData[w].flower[0];
				  boolean wflag=false;
				  while (!wflag && count<10000) {
//					  System.err.println("gencount="+gencount+", working on w="+w+
//							  "; w's mark="+packData.getVertMark(w));
					  if (w==stopv) wflag=true;
					  int prev=packData.kData[w].flower[packData.getNum(w)];
					  int n=degs[packData.getVertMark(w)]-packData.getNum(w)-1;
					  if (n<-1)
						  throw new CombException("violated degree at vert "+w);

					  // add the n circles; two marks alternate around w
					  alt[0]=packData.getVertMark(prev);
					  int vec=(alt[0]-packData.getVertMark(w)+3)%3;
					  alt[1]=(alt[0]+vec)%3;
//					  System.out.println("w mark="+packData.getVertMark(w)+
//							  "; prev mark (alt[0])="+alt[0]+"; alt[1]="+alt[1]);
					  for (int i=1;i<=n;i++) { 
						  packData.add_vert(w);
						  packData.getVertMark(packData.nodeCount,alt[i%2]);
					  }
					  if (n==-1) { 
						  int xv=packData.close_up(w); // vertex removed?
						  if (xv>0 && xv<=stopv) // if yes, reset stopv
							  stopv--;
						  if (xv>0 && xv<=next) // may have to reset next, too
							  next--;
					  }
					  else packData.enfold(w);
					  packData.complex_count(true);
					  w=next;
					  next=packData.kData[w].flower[0];
					  count++;
				  } // end of while
				  
				  // debug
				  NodeLink nodelink=new NodeLink(packData,"b");
				  Iterator<Integer> nlink=nodelink.iterator();
//				  System.out.println("bdry verts, marks");
				  while (nlink.hasNext()) {
					  int dw=nlink.next();
//					  System.out.println("v "+dw+", "+packData.getVertMark(dw));
				  }
				  
				  
				  gencount++;
			  }
			  packData.setCombinatorics();
			  return count; */
		  }
    	  break;
	  }
      case 'u':
      {
	      // ========= unflip ========
	      if (cmd.startsWith("unflip")) {
	    	  items=flagSegs.elementAt(0); // should be only one segment
	   		  EdgeLink edgeLink=new EdgeLink(packData,items);
	   		  if (edgeLink==null || edgeLink.size()<1) return 0;
	   		  Iterator<EdgeSimple> elist=edgeLink.iterator();

	   		  while (elist.hasNext()) {
	   			  EdgeSimple edge=(EdgeSimple)elist.next();
	   			  count += packData.flip_edge(edge.v,edge.w,3);
	   	      }
	   		  if (count>0) {
	   			  if (packData.packDCEL!=null) {
	   				  packData.packDCEL.fixDCEL_raw(packData);
	   			  }
	   			  else {
	   				  packData.complex_count(false);
	   				  packData.facedraworder(false);
	   			  }
	   			  packData.fillcurves();
	   		  }
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
      
	  case 'w': 
	  {
	      // =============== writeLite ==============
		  /**
		   * Parse options for writing 'Lite' packings given a list of of
		   * vertices defining some patch of interest. 
		   * 
		   * Default is 'rz'. Note: all content options in first continuous
		   * string directly after '-'. If 'prePath' is true, then prepend path
		   * (i.e., not default).
		   * 
		   * r radii (g needed) 
		   * z centers 
		   * i non-default inv_dist and aims 
		   * b add ideal verts to all but outer bdry component
		   * 
		   * -v {v..} core vertices (else default to interior verts)
		   * -A {a} suggested alpha vert
		   * -G {g} suggested gamma vert 
		   * -[fs] {filename} 
		   */
	      if ((cmd.startsWith("writeL") || cmd.startsWith("WriteL")) && !cmd.contains("_")) {
	      	  int act=00030;  // bit-encoded write flags
	      	  String flagstr=null;
//	      	  boolean append_flag=false; // no append option for now
	      	  boolean script_flag=false;
	      	  int alp=-1; // user's alpha choice
	      	  int gam=-1; // user's gamma choice
	      	  NodeLink intV=null;
	      	  
	      	  // Get and remove trailing filename as first step
	      	  StringBuilder strbld=new StringBuilder();
	      	  int fra=CPFileManager.trailingFile(flagSegs, strbld);
	      	  if (fra==0)
	      		  throw new ParserException("No filename in 'writeLite'");
	      	  String fname=strbld.toString();
	      	  
	      	  // process just the first flag sequence
	      	  if (flagSegs!=null && flagSegs.size()>0) {
	      		  items=flagSegs.remove(0);

	      		  // string of options should be first flag string
	      		  
	      		  // if not flag string, then should get list of vertices
	     		  if (!StringUtil.isFlag(flagstr=items.firstElement())) {
	     			  flagstr="rz"; // default to radii/centers
     				  intV=new NodeLink(packData,items);
	     		  }
	     		  
	     		  // else, -A {alpha}, -G {gamma}, -v {v..}, or options 
	     		  else {
	     			  String fstr=items.remove(0).substring(1);
	     			  if (fstr.charAt(0)=='A') { // alpha?
	     				  try {
	     					  alp=Integer.parseInt(items.remove(0));
	     				  } catch(Exception ex) {
	     					  throw new ParserException("usage -A {alpha}");
	     				  }
	     			  }
	     			  else if (fstr.charAt(0)=='G') { // gamma?
	     				  try {
	     					  gam=Integer.parseInt(items.remove(0));
	     				  } catch(Exception ex) {
	     					  throw new ParserException("usage -G {gamma}");
	     				  }
	     			  }
	     			  else if (fstr.charAt(0)=='v') { // vertices?
	     				  intV=new NodeLink(packData,items);
	     			  }
	     			  else {
	     				  flagstr=new String(fstr);
	     			  }
	     		  }
	      	  } // done looking at first flag sequence
	      		  
	      	  // else look for other flags, -A, -G, -f
     		  while (flagSegs!=null && flagSegs.size()>0) {
     			  items=flagSegs.remove(0);
	     			  
     			  String str=items.get(0);
	     			  
     			  // if not a flag, should be string of vertices
     			  if (!StringUtil.isFlag(str)) {
     				  intV=new NodeLink(packData,items);
     			  }
     			  
     			  // is a flag
     			  else { 
	     			  String fstr=items.remove(0).substring(1);
	     			  if (fstr.charAt(0)=='A') {
	     				  try {
	     					  alp=Integer.parseInt(items.remove(0));
	     				  } catch(Exception ex) {
	     					  throw new ParserException("usage -A {alpha}");
	     				  }
	     			  }
	     			  else if (fstr.charAt(0)=='G') {
	     				  try {
	     					  gam=Integer.parseInt(items.remove(0));
	     				  } catch(Exception ex) {
	     					  throw new ParserException("usage -G {gamma}");
	     				  }
	     			  } 
	     			  else if (fstr.charAt(0)=='v') { // vertices?
	     				  items.remove(0);
	     				  intV=new NodeLink(packData,items);
	     			  }
     			  }
     		  }

     		  // default settings
     		  if (alp==-1)
     			  alp=packData.getAlpha();
     		  if (gam==-1)
     			  gam=packData.gamma;
     		  if (intV==null || intV.size()==0)
     			  intV=new NodeLink(packData,"i");
	     		  
     		  // parsing the options (if some were given)
     		  if (flagstr!=null && flagstr.length()>0) { 
     			  int len=flagstr.length();

     			  // just "s"? equivalent to "rzs" 
     			  if (len==1 && flagstr.equalsIgnoreCase("s")) {
     				  if (cmd.charAt(0)=='W') {
     					  CirclePack.cpb.myErrorMsg("Can't 'Write' (cap 'W') to script");
     					  return 0;
     				  }
     				  script_flag=true;
     				  act=00030;
     			  }
     			  
     			  // build 'act' encoding
     			  for(int j=0;j<len;j++) {
     				  switch(flagstr.charAt(j)) {
     				  case 'r': {act |= 00010;break;}
     				  case 'z': {act |= 00020;break;}
     				  case 'i': {act |= 00004;break;} // non-default aims/invdist
     				  case 'b': {act |= 0400000;break;} // misc (add_ideal)
     				  } // end of switch
     			  } // done with 'act'
     		  } // done with options flags

     		  // now process
   	  		  File dir=CPFileManager.PackingDirectory;
   	   		  if (cmd.charAt(0)=='W') { // use given directory
   	   			  if (fname.startsWith("~/")) {
   	   				  fname=new String(CPFileManager.HomeDirectory+
   	   						  File.separator+fname.substring(2).trim());
   	   			  }
   	   			  dir=new File(fname);
   	   			  fname=dir.getName();
   	   			  dir=new File(dir.getParent());
   	   		  }
   	   		  BufferedWriter fp=CPFileManager.openWriteFP(dir,false,fname,script_flag);
   	   		  try {
   	   			  packData.writeLite(fp,act,intV,alp,gam); // 
   	   		  } catch(Exception ex) {
   	   			  throw new InOutException("writeLite failed");
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
	      // fall through from 'w' to 'W'
	  }
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
		      	  if ((code | 02)==02) 
		      		  append_flag=true;
		      	  if ((code | 04)==04)
		      		  script_flag=true;
		      	  File tmpdir=new File(strbld.toString().trim());
		      	  fname=tmpdir.getName();
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
	  	     					  throw new ParserException("'-x' in write call requires xyz date: see 'set_xyz'");
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
	      	  try {
	      		  packData.writePack(fp,act,false); // (00017 & 00004)==00004;
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
	          try {
	        	  n=Integer.parseInt((String)items.remove(0));
	          } catch(Exception ex) {n=-1;}  // default: do whole bdry comp
	          
	          // do only one vertex
	          int v=NodeLink.grab_one_vert(packData,items.get(0));
        	  if (!packData.isBdry(v)) {
        		  CirclePack.cpb.errMsg("'zip' usage: n v, 'v' must be boundary");
        		  return 0;
        	  }
        	  int b=packData.bdry_comp_count(v);
        	  int m=b/2;
        	  if (b!=2*m) m=(b-1)/2;
        	  if (n<0 || n>=m) n=m; // full bdry
        	  StringBuilder strbld=new StringBuilder("adjoin "+
        			  packData.packNum+" "+packData.packNum+" "+
        			  v+" "+v+" "+n);
        	  return CommandStrParser.jexecute(packData,strbld.toString());
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
			p=CPBase.cpScreens[pnum].getPackData();
	}
	
	/* NOTE: Vector 'flagSegs' will hold only the flag strings 
	 * occurring after the command --- the command itself is 'cmd' */
	Vector<Vector<String>> flagSegs=StringUtil.flagSeg(allitems);
	return valueExecute(p,cmd,flagSegs);
}

/**
 * Should be called from 'valueExecute' or from 'jexecute', and in both cases, any -p
 * flag should have been processed and removed.
 * @param packData
 * @param cmd
 * @param flagSegs Vector<Vector<String>>
 * @return CallPacket or null on error
 */
public static CallPacket valueExecute(PackData packData,String cmd,Vector<Vector<String>> flagSegs) {
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
		    			CirclePack.cpb.msg("count: no vertices specified");
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
		    				CirclePack.cpb.msg("count: no faces specified");
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
		    				CirclePack.cpb.msg("count: no edges specified");
		        			return null;
		    			}
		    			CirclePack.cpb.msg("count of edges: "+count+
		    					"(with possible redundancies, orientations) ");
			    		rtnCp=new CallPacket("count");
			    		rtnCp.int_vec=new Vector<Integer>();
			    		rtnCp.int_vec.add(count);
			    		return rtnCp;
		    		}
		    		
		    		// TODO: might want 't' option for tiles
		    		
		    		default: // default to vertices
		    		{
		    			NodeLink vertlist=new NodeLink(packData,items);
			    		count=NodeLink.countMe(vertlist);
			    		if (count==0) {
			    			CirclePack.cpb.msg("count: no vertices specified");
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
								mx = Integer.parseInt((String) items.elementAt(0));
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
							if ((g0 = packData.getVertMark(packData.faces[f].vert[0])) != 0
									&& (g1 = packData.getVertMark(packData.faces[f].vert[1])) != 0
									&& (g2 = packData.getVertMark(packData.faces[f].vert[2])) != 0) {
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
	    			EdgeSimple worstedge=QualMeasures.rel_contact_error(packData, elt, uP);
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
	    			EdgeLink elt=new EdgeLink(packData,items);
	    			EdgeSimple worstedge=QualMeasures.visualErrMax(packData, elt, uP);
	    			if (worstedge==null)
	    				throw new DataException("error in measuring visual error");
	    			if (uP.rtnFlag<0) { // some radii too small?
	    				rtnCp.strValue="v -- some radii too small to use";
	    			}
					rtnCp.int_vec = new Vector<Integer>();
					rtnCp.int_vec.add(Integer.valueOf(worstedge.v));
					rtnCp.int_vec.add(Integer.valueOf(worstedge.w));
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
	case '?': // return first string of query result, and then only if it's
			// a number or true/false.
	{
		String query=cmd.substring(1);
		if (query.length()<=0)
			return null;
		
		// we only use first string from 'ans'
		String ans=StringUtil.grabNext(QueryParser.queryParse(packData, query, flagSegs, false));
		if (ans==null || ans.length()==0)
			return null;
		
		// only return via 'strValue' if 'ans' represents a double (or integer) or true/false.
		rtnCp=new CallPacket(query);
		try {
			Double.valueOf(ans); // if not a double, should throw exception here
			rtnCp.strValue=new String(ans);
			return rtnCp;
		} catch(Exception ex) {
			if (ans.equalsIgnoreCase("true") || ans.equalsIgnoreCase("false")) {
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

