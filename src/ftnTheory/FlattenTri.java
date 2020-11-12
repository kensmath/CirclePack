package ftnTheory;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.CombException;
import exceptions.InOutException;
import geometry.EuclMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;

/**
 * This PackExtender was developed for the Honors Seminar as we discussed 
 * the flat display of triangulations of the sphere. Given such a T, the
 * idea is to build a pattern G of equilateral euclidean triangles in the
 * plane so that there's a one-to-one map of faces of T to faces of G.
 * The rules are that G should be simply connected, continguous faces of
 * T should be identified with contiguous faces of G to the extent possible.
 * When this is not possible, the edge occurs twice in the boundary of G
 * and we make an identification (e.g., color-coded).
 * 
 * This tool lets one choose a root face, then try building a layout
 * tree by adding faces with mouse clicks. 
 * 
 * @author kstephe2, September 2017
 *
 */
public class FlattenTri extends PackExtender {
	
	double fixRad;
	int rootface;
	MatchFace []matchfaces;
	PackData flatPack;
	int colorIndx;
	CPScreen cpS;
	EdgeLink occupied; // <n,m> means hex grid location (n,m) is taken 
	double TOL; // numerical tolerance
	
	public FlattenTri(PackData p) {
		super(p);
		packData=p;
		
		// common radius for all circles
		fixRad=0.2;
		TOL=.000001;
		extensionType="FLATTEN_TRIANGULATION";
		extensionAbbrev="FT";
		toolTip="Flatten a triangulation to equilateral faces in the plane";
		registerXType();
		try {
			int pnum=packData.packNum;
			int qnum=(pnum+1)%CPBase.NUM_PACKS;
			
			// move copy of packing to 'qnum'
			cpCommand(packData,"copy "+qnum);
			flatPack=CPBase.pack[qnum].getPackData();
			cpCommand(flatPack,"geom_to_e");
			cpS=flatPack.cpScreen;
			reset();
		} catch(Exception ex) {
			rootface=0;
		}		
		if (running) {
			packData.packExtensions.add(this);
		}
		rootface=1; // default
	}
	
	/**
	 * Reset everything
	 * @return flatPack nodeCount
	 */
	public int reset() {
		cpCommand(flatPack,"set_rad .01 a); // "+fixRad+" a");
		CPBase.Glink=new GraphLink();
		CPBase.Elink=new EdgeLink();
		matchfaces=new MatchFace[flatPack.faceCount+1];
		colorIndx=0;
		occupied=new EdgeLink();
		return flatPack.nodeCount;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		if (cmd.startsWith("save")) {
			String filename="dualtree.q"; // default
			StringBuilder strbuf=new StringBuilder("");
			int code=CPFileManager.trailingFile(flagSegs, strbuf);
			boolean scriptflag=false;
			if ((code & 04) == 04) // to script
				scriptflag=true;
			if (code!=0) {
				filename=strbuf.toString();
			}
			File file=new File(filename);
			int cnt=0;
			try {
				BufferedWriter fp=CPFileManager.openWriteFP((File)CPFileManager.PackingDirectory,false,
						file.getName(),scriptflag);
				fp.write("CHECKCOUNT: "+flatPack.nodeCount+"\n");
				fp.write("PACKNAME: " + filename + "\n");
				fp.write("GLOBAL_DUAL_EDGE_LIST:\n");
				Iterator<EdgeSimple> eli = CPBase.Glink.iterator();
				while (eli.hasNext()) {
					EdgeSimple el = (EdgeSimple) eli.next();
					fp.write(" " + el.v + " " + el.w + "\n");
					cnt++;
				}
				fp.write(" (done)\n\n");
				fp.flush();
				fp.close();
				if (scriptflag)
					CirclePack.cpb.msg("Wrote FT dualtree to '"+filename+"' in script");
				else
					CirclePack.cpb.msg("Wrote FT dualtree to '"+filename+"' in "+CPFileManager.PackingDirectory);
			} catch(Exception ex) {
				throw new InOutException("failed to write the dual tree");
			}
			return cnt;
		}
		
		else if (cmd.startsWith("layout")) {
			if (CPBase.Glink!=null && CPBase.Glink.size()>0) {
				GraphLink templ=CPBase.Glink.makeCopy();
				int cnt=0;
				reset();
				EdgeSimple dedge=templ.get(0);
				rootface=dedge.v;
				startRoot();
				Iterator<EdgeSimple> git=templ.iterator();
				while (git.hasNext()) {
					dedge=git.next();
					int f=dedge.v;
					int g=dedge.w;
					int rslt=attach(f,g);
					if (rslt==0) {
						errorMsg("failed to attach faces "+f+" and "+g);
						return 0;
					}
					cnt +=rslt;
				}
				return cnt;
			}
			return 0;
		}
		
		else if (cmd.startsWith("root")) {
			rootface=1;
			try {
				items=flagSegs.get(0);
				int rf=Integer.parseInt(items.get(0));
				if (rf>0 && rf<=flatPack.faceCount)
					rootface=rf;
				else {
					errorMsg("improper root choice");
					return 0;
				}
			} catch (Exception ex) {
				errorMsg("failed to set root");
				return 0;
			}
				
			reset();

			return startRoot();
		}
		
		else if (cmd.startsWith("att")) {
			String rstr;
			try {
				
				// find new face to attach
				rstr=StringUtil.reconItem(flagSegs.get(0));
			} catch (Exception ex) {
				errorMsg("must give edge");
				return 0;
			}
			
			EdgeSimple edge=EdgeLink.grab_one_edge(packData, rstr);
			int f=flatPack.left_face(edge.v,edge.w)[0];
			int g=flatPack.left_face(edge.w,edge.v)[0];
			return attach(f,g);
		}
		
		else if (cmd.startsWith("draw")) {  // should be just one flag seqment
			CPBase.Elink=new EdgeLink();
			
			// check for display flags, default is face number only
			String dstr=new String("n");
			try {
				items=flagSegs.get(0);
				if (items.get(0).startsWith("-")) { // display flags
					dstr=items.remove(0).substring(1); // remove '-'
				}
			} catch (Exception ex) {} 
			DispFlags dflags=new DispFlags(dstr);
			
			// any remaining strings should be face list
			FaceLink flink=null;
			if (items!=null && items.size()>0) {
				if (items.get(0).equals("a"))
					items.remove(0);
				else // get listed faces
					flink=new FaceLink(flatPack,items);
			}

			// just redraw selected faces
			if (flink!=null && flink.size()>0) {
				int fcount=0;
				Iterator<Integer> fit=flink.iterator();
				while (fit.hasNext()) {
					int f=fit.next();
					if (matchfaces[f]!=null) {
						matchfaces[f].drawMe(dflags);
						PackControl.canvasRedrawer.paintMyCanvasses(flatPack,false);
						fcount++;
					}
				}
				return fcount;
			}
			
			// user must redraw with explicit command
			cpCommand(flatPack,"Disp -w -ff "+rootface);
			return drawAll(dflags);
		}
		
		else if (cmd.startsWith("undo")) {  // remove end of dualtree
			if (CPBase.Glink==null || CPBase.Glink.size()==0) {
				errorMsg("no faces to remove");
				return 0;
			}
			
			int lastface=CPBase.Glink.getLast().w;
			matchfaces[lastface].undoMe();
			matchfaces[lastface]=null;
			CPBase.Glink.removeLast();
			return 1;
		}
		
		// else default to superclass
		return super.cmdParser(cmd,flagSegs);
	}
	
	/** 
	 * attach faces f and g, as appropriate.
	 * @param f int
	 * @param g int
	 * @return int 0 on error
	 */
	public int attach(int f,int g) {
		if (matchfaces[f]==null && matchfaces[g]==null) {
			errorMsg("Neither face f="+f+" nor g="+g+" is attached");
			return 0;
		}
		if (matchfaces[f]!=null && matchfaces[g]!=null) {
			errorMsg("Faces f="+f+" and g="+g+" are already attached");
			return 0;
		}
			
		// add to dualtree
		int fold=f;
		int fnew=g;
		if (matchfaces[g]!=null) {
			fold=g;
			fnew=f;
		}
		
		CPBase.Glink.add(new EdgeSimple(fold,fnew)); // store also in global GLink
		MatchFace nmf=null;
		try {
			nmf=new MatchFace(fnew,fold);
		} catch(CombException cex) {
			errorMsg(cex.getMessage());
			return 0;
		}
		matchfaces[fnew]=nmf;
		int nf=CPBase.Glink.size();
		if (nf==flatPack.faceCount-1)
			msg("ALL faces are now laid out.");
		else
			msg(nf+" of the "+flatPack.faceCount+" faces have been placed");
		return 1;
	}
	
	public int startRoot() {
		// start root face
		int []vert=flatPack.faces[rootface].vert;
		double d=fixRad/Math.sqrt(3.0);
		matchfaces[rootface]=new MatchFace(rootface,0);
		matchfaces[rootface].myCenters[0]=new Complex(-fixRad,-d);
		flatPack.setCenter(vert[0],matchfaces[rootface].myCenters[0]);
		matchfaces[rootface].myCenters[1]=new Complex(fixRad,-d);
		flatPack.setCenter(vert[1],matchfaces[rootface].myCenters[1]);
		matchfaces[rootface].myCenters[2]=new Complex(0,2*d);
		flatPack.setCenter(vert[2],matchfaces[rootface].myCenters[2]);
		matchfaces[rootface].myCentroid=new Complex(0.0);
		flatPack.faces[rootface].color=CPScreen.coLor(80);
		matchfaces[rootface].mySpot=new EdgeSimple(0,0);
		occupied.add(matchfaces[rootface].mySpot);
		return rootface;
	}
	
	/**
	 * Draw all the faces in order of 'Glink' 
	 * @param dflags
	 * @return count
	 */
	public int drawAll(DispFlags dflags) {
		int dcount=0;
		dcount+=matchfaces[rootface].drawMe(dflags);
		if (CPBase.Glink.size()==0) 
			return dcount;

		// follow tree to draw faces in order
		Iterator<EdgeSimple> dit=CPBase.Glink.iterator();
		while (dit.hasNext()) {
			EdgeSimple dedge=dit.next();
			int oldf=dedge.v;
			int newf=dedge.w;
			if (matchfaces[newf]==null)
				throw new CombException("Error: 'MatchFace' should exist for face "+newf);
			dcount+=matchfaces[newf].drawMe(dflags);
			
			// draw the dual edge between newf and oldf as blue
			DispFlags edflags=new DispFlags("t5c5");
			cpS.drawEdge(matchfaces[oldf].myCentroid,matchfaces[newf].myCentroid,edflags);
		}
		
		return dcount;
	}
	
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("root","<f>",null,
				"Set the root face, restart 'Glink'"));
		cmdStruct.add(new CmdStruct("undo",null,null,
				"undo the last added face in 'Glink'; can be repeated"));
		cmdStruct.add(new CmdStruct("app","v w",null,
				"append face across edge <v,w>, find centers, put in 'Glink'.'"));
		cmdStruct.add(new CmdStruct("draw","[-drawflags] {f..}",null,
				"redraw indicated faces, if in place; default to full 'Glink'; "+
				"clear the screen first."));
		cmdStruct.add(new CmdStruct("save","[[-s] <filename>]",null,
				"Save the Global dual edge list 'Glink' to a file"));
		cmdStruct.add(new CmdStruct("layout",null,null,
				"Use the global 'Glink' as a dual tree and layout as many faces "+
				"as possible in order"));
	}

	class MatchFace {
		int myFaceIndx;
		int myParent; // 0 only for rootface
		int baseindx; // which of my neighbors is the parent?
		int []myFaceflower; // indexed as with face's indices.
		public Complex []myCenters;
		public Complex myCentroid; // convenience for drawing dual edges
		Color []myColors;
		EdgeSimple mySpot; // hex site this face occupies
		
		public MatchFace(int me,int parent) {
			myFaceIndx=me;
			myParent=parent; 
			int []vert=flatPack.faces[me].vert;
			myFaceflower=new int[3];
			for (int j=0;j<3;j++)
				myFaceflower[j]=flatPack.left_face(vert[(j+1)%3],vert[j])[0];
			myCenters=new Complex[3];
			myColors=new Color[3];
			for (int j=0;j<3;j++) {
				myCenters[j]=null;
				myColors[j]=null;
			}
			
			// if this isn't root, set centers, colors, spot, etc.
			if (myParent>0) {
				baseindx=flatPack.face_nghb(myParent,myFaceIndx);
				int pindx=flatPack.face_nghb(myFaceIndx,myParent);
				myCenters[baseindx]=matchfaces[myParent].myCenters[(pindx+1)%3];
				myCenters[(baseindx+1)%3]=matchfaces[myParent].myCenters[pindx];
				myCenters[(baseindx+2)%3]=EuclMath.e_compcenter(myCenters[baseindx],myCenters[(baseindx+1)%3],
						fixRad,fixRad,fixRad).center;
				myCentroid=myCenters[0].add(myCenters[1].add(myCenters[2])).divide(3.0);
								
				// find mySpot; throw exception if occupied already
				int []sincs=newSpot(matchfaces[myParent].myCentroid,myCentroid);
				EdgeSimple mpspot=matchfaces[myParent].mySpot;
				mySpot=new EdgeSimple(mpspot.v+sincs[0],mpspot.w+sincs[1]);
				if (occupied.isThereVW(mySpot.v,mySpot.w)!=-1) // is occupied already
					throw new CombException("New spot is already occupied.");
				occupied.add(mySpot);
				
				// edge pasted to parent is blue
//				myColors[baseindx]=CPScreen.coLor(5);
				
				// check other two edges
				int nindx=(baseindx+1)%3;
				MatchFace mf=null;
				if ((mf=matchfaces[myFaceflower[nindx]])!=null) {
					int xindx=flatPack.face_nghb(myFaceIndx,myFaceflower[nindx]);

					if (xindx>=0) { 
						// do the edges align?
						Complex z1=myCenters[nindx];
						Complex z2=myCenters[(nindx+1)%3];
						Complex x1=mf.myCenters[xindx];
						Complex x2=mf.myCenters[(xindx+1)%3];
						double error=z1.minus(x2).abs()+z2.minus(x1).abs();
					
						// no, so set identification coloring
						if (error>.25*fixRad) { 
							myColors[nindx]=ColorUtil.spreadColor(colorIndx%16);
							mf.myColors[xindx]=ColorUtil.spreadColor(colorIndx%16);
							colorIndx++;
						}
					}
				}
				nindx=(baseindx+2)%3;
				if ((mf=matchfaces[myFaceflower[nindx]])!=null) {
					int xindx=flatPack.face_nghb(myFaceIndx,myFaceflower[nindx]);

					if (xindx>=0) {
						// do the edges align?
						Complex z1=myCenters[nindx];
						Complex z2=myCenters[(nindx+1)%3];
						Complex x1=mf.myCenters[xindx];
						Complex x2=mf.myCenters[(xindx+1)%3];
					
						// no, so set identification coloring
						if (z1.minus(x2).abs()>.25*fixRad || z2.minus(x1).abs()>.25*fixRad) { 
							myColors[nindx]=ColorUtil.spreadColor(colorIndx%16);
							mf.myColors[xindx]=ColorUtil.spreadColor(colorIndx%16);
							colorIndx++;
							int []verts=flatPack.faces[myFaceIndx].vert;
							CPBase.Elink.add(new EdgeSimple(verts[nindx],verts[(nindx+1)%3]));
						}
					}
				}

			}
				
		}
		
		public void setColor(int j,int col) {
			myColors[j]=CPScreen.coLor(col);
		}
		
		public Color getColor(int j) {
			if (myColors[j]==null)
				return null;
			return CPScreen.cloneColor(myColors[j]);
		}
		
		/**
		 * Draw the face using 'dflags', then draw 1 or 2 colored edges, if 
		 * their colors are set. (These would be ones identified with separate
		 * edge somewhere.)
		 * @param dflags
		 * @return 1
		 */
		public int drawMe(DispFlags dflags) {
			dflags.setLabel(Integer.toString(myFaceIndx));
			// shrink towards centroid by 4% so neighboring face edges don't
			//    quite cover up one another.
			Complex []sZ=new Complex[3];
			sZ[baseindx]=myCenters[baseindx].minus(myCentroid).times(.96).add(myCentroid);
			sZ[(baseindx+1)%3]=myCenters[(baseindx+1)%3].minus(myCentroid).times(.96).add(myCentroid);
			sZ[(baseindx+2)%3]=myCenters[(baseindx+2)%3].minus(myCentroid).times(.96).add(myCentroid);
			double fR=fixRad*.96;
			cpS.drawFace(sZ[0],sZ[1],sZ[2],fR,fR,fR,dflags);
			int []verts=flatPack.faces[myFaceIndx].vert;
			
			// color edges?
			for (int j=0;j<3;j++) {
				Color eCol=myColors[(baseindx+j)%3];
				if (eCol!=null) {
					DispFlags edflags=new DispFlags("t8c"+CPScreen.col_to_table(eCol));
					cpS.drawEdge(sZ[(baseindx+j)%3],sZ[(baseindx+j+1)%3],edflags);
					CPBase.Elink.add(new EdgeSimple(verts[(baseindx+j)%3],verts[(baseindx+j+1)%3]));
				}
			}
			return 1;
		}
		
		/**
		 * To undo, need to reset identification color in cases when a 
		 * neighbor's edge color was set. Remove mySpot from 'occupied'
		 * @return 1
		 */
		public int undoMe() {
			int nindx=(baseindx+1)%3;
			MatchFace mf=null;
			if ((mf=matchfaces[myFaceflower[nindx]])!=null) {
				int xindx=flatPack.face_nghb(myFaceIndx,myFaceflower[nindx]);
				if (xindx>=0) 
					mf.myColors[xindx]=null;
			}
			nindx=(baseindx+2)%3;
			if ((mf=matchfaces[myFaceflower[nindx]])!=null) {
				int xindx=flatPack.face_nghb(myFaceIndx,myFaceflower[nindx]);
				if (xindx>=0) 
					mf.myColors[xindx]=null;
			}
			occupied.remove(mySpot);
			return 1;
		}
		
		/**
		 * Using vector from parent face centroid to myCentroid, rotated cclw by pi/2,
		 * determine increments <i,j> to parent.mySpot location.   
		 * @param pc Complex 
		 * @param mc Complex
		 * @return int[2]
		 */
		public int []newSpot(Complex pc,Complex mc) {
			Complex vec=mc.minus(pc).times(new Complex(0.0,1.0)); //
			int []ans=new int[2];
			if (Math.abs(vec.y)<TOL) {  // horizontally left/right?
				if (vec.x>TOL) {
					ans[0]=1;
					ans[1]=1;
					return ans;
				}
				ans[0]=-1;
				ans[1]=-1;
				return ans;
			}
			if (vec.y>TOL) {
				if (vec.x>TOL) {
					ans[0]=0;
					ans[1]=1;
					return ans;
				}
				ans[0]=-1;
				ans[1]=0;
				return ans;
			}
			if (vec.x>TOL) {
				ans[0]=1;
				ans[1]=0;
				return ans;
			}
			ans[0]=0;
			ans[1]=-1;
			return ans;
		}
	}
}
