package canvasses;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import baryStuff.BaryPoint;
import circlePack.PackControl;
import complex.Complex;
import deBugging.LayoutBugs;
import exceptions.ParserException;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import geometry.SphericalMath;
import komplex.AmbiguousZ;
import komplex.DualGraph;
import komplex.DualTri;
import komplex.EdgePair;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.BaryCoordLink;
import listManip.BaryLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.TileLink;
import packing.PackData;
import panels.CPScreen;
import tiling.Tile;
import util.DispFlags;
import util.PathUtil;
import util.StringUtil;

/**
 * This static code parses the vector of display flag segments 
 * for canvasses and carries out the specified display actions.
 * 
 * Note: the first version is the usual one, but the second
 * allows us to separate the packing from its screen, so one
 * can put images from another packing on an existing screen.
 *  
 * Preliminary clear/repeat and closing repaint actions are 
 * handled in the calling routine.
 * @author kens
 */
public class DisplayParser {
	
	/**
	 * Display objects from packing p on its own @see CPScreen
	 * based on flag segments given in 'flagSegs' 
	 * @param p @see PackData
	 * @param flagSegs
	 * @return int, count of display actions
	 */
	public static int dispParse(PackData p, Vector<Vector<String>> flagSegs) {
		return dispParse(p,p.cpScreen,flagSegs);
	}
	
	/**
	 * Display objects from packing p on designated @see CPScreen
	 * based on flag segments given in 'flagSegs' 
	 * @param p @see PackData
	 * @param cpScreen @see CPScreen
	 * @param flagSegs
	 * @return int, count of display actions
	 */
	public static int dispParse(PackData p, CPScreen cpScreen, Vector<Vector<String>> flagSegs) {
		if (flagSegs == null || flagSegs.size() == 0)
			return 0;
		int count = 0;
		boolean debug = false;

		// iterate through successive flag segments.
		Iterator<Vector<String>> its = flagSegs.iterator();

		while (its.hasNext()) {
			Vector<String> items = (Vector<String>) its.next();
			if (!StringUtil.isFlag(items.get(0)))
				return count;

			/* ============ the work is in processing ======== */

			String sub_cmd = (String) items.remove(0);
			char c = sub_cmd.charAt(1); // grab first letter of flag
			sub_cmd = sub_cmd.substring(2); // remove '-' and first char
			int v;
			
			// first, those cases NOT using 'DispFlags' options
			if (c == 'n' || c == 'x') {
				if (c == 'n') { // various labels
					if (sub_cmd.length() == 0)
						break;
					char sc = sub_cmd.charAt(0);
					try {
						if (sc == 'f') { // faces
							FaceLink link = new FaceLink(p, items);
							count += p.labellist(link, 1, false);
						} else if (sc == 'v' || sc == 'c') { // circles
							NodeLink link = new NodeLink(p, items);
							count += p.labellist(link, 1, true);
						} else if (sc == 'l') { // -nl {v} {str} str at center
												// of v
							v = NodeLink
									.grab_one_vert(p, (String) items.get(0));
							cpScreen.drawStr(p.rData[v].center, (String) items
									.get(1));
							count++;
						} else if (sc == 'z') { // -nl {x} {y} {str}
							cpScreen.drawStr(new Complex(Double.parseDouble(
									(String) items.get(0)), Double.parseDouble(
									(String) items.get(1))), (String) items
									.get(2));
							count++;
						}
					} catch (Exception ex) {
						throw new ParserException("label error: "
								+ ex.getMessage());
					}
				} // end of 'n'

				else if (c == 'x') { // set display/undisplay mode for coord
										// axes
					if (sub_cmd.length() > 0 && sub_cmd.charAt(0) == 'u')
						cpScreen.setAxisMode(false);
					else
						cpScreen.setAxisMode(true);
				}
			} // done with 'n' and 'x'

			// --------- now, watch for 'DispFlag' ---------------
			// typical display flags involve optional color/thickness specs
			Complex z = null;
			int thickhold = -1;
			char dualChar = 'e'; // default to dual edges
			int trinket = 0; // for trinket code

			// Have to catch dual flags '-d..' to get second character
			if (c == 'd' || c=='D') { // 'dc','df', 'dF', 'de' (default), 'dg', 'dt', 'dw', or 'dp'?
				if (sub_cmd.length() > 0) {
					dualChar = sub_cmd.charAt(0); // need second char
					sub_cmd = sub_cmd.substring(1); // remove this char
					
					// 't','p'? set 'trinket', must start with next character
					if ((dualChar=='t' || dualChar=='p') && sub_cmd.length() > 0) {
						String tstr = StringUtil.getDigitStr(sub_cmd, 0);
						if (tstr != null) {
							sub_cmd = sub_cmd.substring(tstr.length()); // remove
							trinket = Integer.parseInt(tstr);
							// TODO: may want more varieties of trinkets
							if (trinket < 0 || trinket > 9)
								trinket = 0;
						}
					}
				} 
				else 
					sub_cmd = "";
			}
			
			// Catch 'trinket' flags to get code (catch again later)
			else if (c == 't') {
				// digits may be first; have to read/remove them
				String tstr = StringUtil.getDigitStr(sub_cmd, 0);
				if (tstr != null) {
					sub_cmd = sub_cmd.substring(tstr.length());
					trinket = Integer.parseInt(tstr);
					// TODO: may want more trinkets
					if (trinket < 0 || trinket > 9)
						trinket = 0;
				}
			}

			// parse display flag string 
			DispFlags dispFlags=new DispFlags(sub_cmd,cpScreen.fillOpacity);

			/* ====== now go through numerous "disp" options ========= */

			switch (c) {
			case 'a': // face 'sectors' (eucl only) based on centers only
			{
				if (p.hes != 0)
					break;
				int f;
				double r2deg=180.0/Math.PI;
				FaceLink faceLink = new FaceLink(p, items);
				Iterator<Integer> flist = faceLink.iterator();
				while (flist.hasNext()) {
					f = (Integer) flist.next();
					Complex[] sides = new Complex[3];
					double[] lgths = new double[3];
					for (int j = 0; j < 3; j++) {
						sides[j] = p.rData[p.faces[f].vert[(j + 1) % 3]].center
								.minus(p.rData[p.faces[f].vert[j]].center);
						lgths[j] = sides[j].abs();
					}
					for (int j = 0; j < 3; j++) {
						Complex cent = p.rData[p.faces[f].vert[j]].center;
						double arg1 = sides[j].arg()*r2deg;
						double extent = sides[(j + 2) % 3].times(-1.0)
								.divide(sides[j]).arg()*r2deg;
						double rad = 0.5 * (lgths[j] - lgths[(j + 1) % 3] + lgths[(j + 2) % 3]);
						cpScreen.drawArc(cent,rad,arg1,extent,dispFlags);
						count++;
					}
				}
				break;
			}
			
			case 'b': // bary-coord encoded paths, use color/thickness options
			{
				Color holdcolor = cpScreen.imageContextReal.getColor();
				Path2D.Double mypath = null;
				
				// default to gridlines
				Vector<BaryCoordLink> myLines=CPBase.gridLines;
				if (sub_cmd!=null && sub_cmd.length()>0 && sub_cmd.charAt(0)=='s') // streamLines?
					myLines=CPBase.streamLines;
				
				if (myLines == null || myLines.size() == 0)
					break;
				PackData localPD=p;
				if (p.hes > 0) {
					CirclePack.cpb.errMsg("'grid' can not yet be used with spherical packings");
					break;
				}
				if (p.hes<0) {
					localPD=p.copyPackTo();
					localPD.geom_to_e();
				}
				if (dispFlags.colorIsSet)  // if color was specified
					cpScreen.imageContextReal.setColor(dispFlags.getColor());
				for (int j = 0; j < myLines.size(); j++) {
					mypath = PathUtil.baryLink2path(localPD, myLines.get(j));
					if (mypath != null) {
						cpScreen.drawPath(mypath);
						count++;
					}
				}
				
				// restore color
				cpScreen.imageContextReal.setColor(holdcolor);
				break;
			}
			case 'g': // draw 'ClosedPath'; use color/thickness, default, blue/3
			{
				Color holdcolor = null;
				if (CPBase.ClosedPath == null)
					break;
				// default to larger thickness (if not overridden)
				if (thickhold < 0) {
					thickhold = cpScreen.getLineThickness();
					cpScreen.setLineThickness(3);
				}
				// default color, may be overridden below
				cpScreen.imageContextReal.setColor(Color.BLUE);
				if (dispFlags.colorIsSet) { // if specified
					holdcolor = cpScreen.imageContextReal.getColor();
					cpScreen.imageContextReal.setColor(dispFlags.getColor());
					cpScreen.drawPath(CPBase.ClosedPath);
					cpScreen.imageContextReal.setColor(holdcolor);
				}
				else {
					cpScreen.drawPath(CPBase.ClosedPath);
				}
				count++;
				break;
			}
			// show recomputed faces and/or circles in given face order;
			//   if no list is given, then default to drawing order.
			// NOTE: this may change stored centers
			case 'B': // both faces and circles
			case 'C': // circles
			case 'F': // faces
			{

				// a second 'F' indicates that layout uses redChain data
				boolean useRed=false;
				if (sub_cmd.length()>0 && sub_cmd.startsWith("F"))
					useRed=true;
				
				if (debug) // debug=true;
					LayoutBugs.log_faceOrder(p);
				
				GraphLink graphlist=null;
				int first_face = 0;
				if (items.size() == 0) { // default to drawing order (plus stragglers 
										 // (i.e., not needed in drawing order)) 
					graphlist=new GraphLink(p,"s");
				}
				else { // there is a given list
					graphlist=new GraphLink(p,items);
				}
				if (graphlist==null || graphlist.size()==0)
					break;
				first_face=graphlist.get(0).w;
				
				// NOTE: We do NOT recompute the location for the first face
				//   (unless it occurs again later in the list); to lay out
				//   the first face, you do that separately, e.g. in layout.
				
				// When circles are indicated, we need to handle the first two 
				//   of the first face separately here; the third is handled 
				//   in layout_facelist call.
				if (c == 'C' || c == 'B') {
					int indx = p.faces[first_face].indexFlag;

					Face face = p.faces[first_face];
					for (int i = 0; i < 2; i++) {
						v = face.vert[(indx + i) % 3];
						z = new Complex(p.rData[v].center);
						
						// set up color (there's only one)
						if (!dispFlags.colorIsSet)
							dispFlags.setColor(p.kData[v].color);

						// label?
						if (dispFlags.label) 
							dispFlags.setLabel(Integer.toString(v));
						
						// now draw it
						cpScreen.drawCircle(z, p.rData[v].rad, dispFlags);
						
					} // end of for loop
					count++;
				} // done with first two circles of first face

				// now proceed with layout
				if (c == 'F') {
					count += p.layoutTree(null, graphlist, dispFlags,null,
							true, useRed,-1.0);
				} 
				else if (c == 'C') {
					count += p.layoutTree(null, graphlist, null,dispFlags,
							true,useRed,-1.0);
				} 
				else if (c == 'B') { // we have only one color we can use
					count += p.layoutTree(null, graphlist, dispFlags,dispFlags,
							true, useRed,-1.0);
				}
				break;
			} // done with C/B/F
			
			case 'c': { // circles
				NodeLink nodeLink = new NodeLink(p, items);
				if (nodeLink.size() <= 0)
					break; // nothing in list

				Iterator<Integer> vlist = nodeLink.iterator();
				while (vlist.hasNext()) {
					v = (Integer) vlist.next();
					z = p.rData[v].center;

					// color? label?
					if (!dispFlags.colorIsSet && (dispFlags.fill || dispFlags.colBorder))
						dispFlags.setColor(p.kData[v].color);
					if (dispFlags.label)
						dispFlags.setLabel(Integer.toString(v));

					cpScreen.drawCircle(z,p.rData[v].rad,dispFlags);
					count++;
				}
				break;
			}
			case 'D': { // dual faces, recomp'd by drawing order
				
				// TODO:
				
				
				break;
			}
			case 'd': { // dual objects: 
				// 'de' (default), 'dc', 'df', 'dg', 'dt', 'dw', or 'dp'
				AmbiguousZ []ambigZs=AmbiguousZ.getAmbiguousZs(p);
				EdgeLink redEs=null;
				
				// if not simply connected, assume usual layout using red chain 
				//     is in place, so need to know about red objects
				if (ambigZs!=null) {
					redEs=p.redChainer.red_to_outlist(p,0); // edges outside redchain
				}
				
				// now the parsing by 'dualChar'
				switch (dualChar) {
				case 'c': // dual 'circles' indexed by faces
				{
					FaceLink faceLink = new FaceLink(p, items);
					if (faceLink != null && faceLink.size() <= 0) // nothing in list
						break;
					Iterator<Integer> flist = faceLink.iterator();
					int f;
					while (flist.hasNext()) {
						f = flist.next();
						Complex []pts=p.corners_face(f, ambigZs);
						CircleSimple sC=CommonMath.tri_incircle(pts[0],pts[1],pts[2],p.hes);

						if (!dispFlags.colorIsSet && (dispFlags.fill || dispFlags.colBorder))
							dispFlags.setColor(p.faces[f].color);
						if (dispFlags.label)
							dispFlags.setLabel(Integer.toString(f));
						cpScreen.drawCircle(sC.center,sC.rad, dispFlags);
						count++;
					}
					break;
				}
				case 'f': // dual 'faces' are indexed by packing vertices
				{
					NodeLink nodeLink = new NodeLink(p, items);
					if (nodeLink.size() <= 0)
						break; // nothing in list
					Iterator<Integer> vlist = nodeLink.iterator();
					while (vlist.hasNext()) {
						v = (Integer) vlist.next();
						
						// TODO: unsettled issue; what about bdry v?
						
						Complex []pts=p.corners_dual_face(v,ambigZs);
						int num = pts.length;
						double[] fanCenters = new double[2 * num];
						for (int j=0;j<num;j++) {
							fanCenters[j*2]=pts[j].x;
							fanCenters[j*2+1]=pts[j].y;
						}
						
						if (!dispFlags.colorIsSet)
							dispFlags.setColor(p.kData[v].color);
						if (dispFlags.label)
							dispFlags.setLabel(Integer.toString(v));
						
						if (p.kData[v].bdryFlag==0) // interior
							cpScreen.drawClosedPoly(num,fanCenters,dispFlags);
						else
							cpScreen.drawOpenPoly(num,fanCenters,dispFlags);
							
						count++;
					}
					break;
				}
				case 'e': // dual edges from list of vertex pairs <v,w>
				{
					GraphLink dualedges=null;
					EdgeLink elist = new EdgeLink(p, items);
					// get list of pairs of neighboring face indices
					dualedges = p.dualEdges(elist); // older method

					if (dualedges != null && dualedges.size() > 0) {
						EdgeSimple edge = null;
						Iterator<EdgeSimple> dedges = dualedges.iterator();
						while (dedges.hasNext()) {
							edge = (EdgeSimple) dedges.next();
							
							Complex []pts=p.ends_dual_edge(edge,ambigZs);
							if (pts==null)
								CirclePack.cpb.errMsg("problem with dual edge");
							else 
								cpScreen.drawEdge(pts[0],pts[1],dispFlags);
							count++;
						}
					}
					break;
				}
				case 'g': // dual edges from list of face pairs <f,g>; 
					// default to draw packData.dualGraph if it is not null
					// or as created here.
				{
					GraphLink dualedges=null;
					if (items!=null && items.size()>0) {
						dualedges=new GraphLink(p,items);
					}
					if (dualedges==null || dualedges.size()==0) {
						if (!p.isSimplyConnected()) {
							p.dualGraph=DualGraph.buildDualGraph(p,-1,null);
						}
						else {
							p.dualGraph=DualGraph.buildDualGraph(p,-1,redEs);
						}
						dualedges=p.dualGraph;
					}
					
					// hopefully, have list of dual edges
					if (dualedges!= null && dualedges.size()>0) {
						EdgeSimple edge = null;
						Iterator<EdgeSimple> dedges = dualedges.iterator();
						while (dedges.hasNext()) {
							edge = (EdgeSimple) dedges.next();
							if (edge.v!=0) {  // skip root
								int f=edge.v;
								z = p.face_center(f); 
								Complex w = p.face_center(edge.w);
								cpScreen.drawEdge(z, w,dispFlags);
								count++;
							}
						}
					}
					break;
				}

				case 'G': // draw Glink interpreted as dual edges
				{
					if (CPBase.Glink!= null) {
						EdgeSimple edge = null;
						Iterator<EdgeSimple> dedges = CPBase.Glink.iterator();
						while (dedges.hasNext()) {
							edge = (EdgeSimple) dedges.next();
							if (edge.v!=0) {  // skip root
								int f=edge.v;
								z = p.face_center(f); 
								Complex w = p.face_center(edge.w);
								if (z!=null && w!=null) {
									cpScreen.drawEdge(z, w,dispFlags);
									count++;
								}
							}
						}
					}
					break;
				}

				case 'F': // real (not dual) faces from dual edgelist; recompute/store new circles along the way
				{
					GraphLink graphLink=new GraphLink(p,items);
					if (graphLink != null && graphLink.size() <= 0) // nothing in list
						break;
					Iterator<EdgeSimple> glist = graphLink.iterator();
					EdgeSimple edge=null;
					while (glist.hasNext()) {
						edge=glist.next();
						int g=-1;
						if (edge.v!=0 && p.layByFaces(edge.v,(g=edge.w))) {
							int []verts=p.faces[g].vert;
							if (!dispFlags.colorIsSet)
								dispFlags.setColor(p.faces[g].color);
							if (dispFlags.label)
								dispFlags.setLabel(Integer.toString(g));
							cpScreen.drawFace(p.rData[verts[0]].center,p.rData[verts[1]].center,p.rData[verts[2]].center,
									p.rData[verts[0]].rad,p.rData[verts[1]].rad,p.rData[verts[2]].rad,dispFlags);
							count++;
						}
					} // end of while
					break;
				}
				case 'w': // dual 'triangles' (i.e., formed by interstice
						  // tangency pts, indexed by face index
				{
					FaceLink faceLink = new FaceLink(p, items);
					if (faceLink != null && faceLink.size() <= 0) // nothing in list
						break;
					Iterator<Integer> flist = faceLink.iterator();
					int f;
					while (flist.hasNext()) {
						f = flist.next();
						int[] vts = p.faces[f].vert;
						DualTri dtri = new DualTri(p.hes,
								p.rData[vts[0]].center, p.rData[vts[1]].center,
								p.rData[vts[2]].center);
						if (!dispFlags.colorIsSet)
							dispFlags.setColor(p.faces[f].color);
						if (dispFlags.label)
							dispFlags.setLabel(Integer.toString(f));
						cpScreen.drawFace(dtri.TangPts[0],dtri.TangPts[1], dtri.TangPts[2],
										null,null,null,dispFlags);
						count++;
					}
					break;
				}
				case 'p': // trinket at tangency points, indexed by edge
				{
					EdgeLink elist = new EdgeLink(p, items);
					if (elist != null && elist.size() > 0) {
						EdgeSimple edge = null;
						Iterator<EdgeSimple> edges = elist.iterator();
						while (edges.hasNext()) {
							edge = (EdgeSimple) edges.next();
							Complex ctr=p.tangencyPoint(edge);
							if (ctr==null)
								break;
							cpScreen.drawTrinket(trinket,ctr, dispFlags);

							count++;
						}
					}
					break;
				}
				case 't': // trinket at dual centers, indexed by faces
				{
					FaceLink faceLink = new FaceLink(p, items);
					if (faceLink != null && faceLink.size() <= 0) 
						break;
					Iterator<Integer> flist = faceLink.iterator();
					int f;
					while (flist.hasNext()) {
						f = flist.next();
						Complex []pts = p.corners_face(f, ambigZs);
						CircleSimple sc=CommonMath.tri_incircle(pts[0],pts[1],pts[2],p.hes);
						
						if (!dispFlags.colorIsSet)
							dispFlags.setColor(p.faces[f].color);
						cpScreen.drawTrinket(trinket,sc.center,dispFlags);
						count++;
					}
					break;						
				}
				
				} // end of switch for dual faces/edges/circles/triangles

				break;
			} // end of 'd' dual options
			case 'e': // edges
			{
				EdgeLink edgelist = new EdgeLink(p);
				// axis extended edges? 
				if (sub_cmd.length() > 0 && sub_cmd.charAt(0) == 'e'
						&& items.size() > 0) {
					edgelist.addEdgeLinks(items, true);
				} 
				// else if description empty, default to all
				else { 
					edgelist.addEdgeLinks(items, false);
				}
				if (edgelist != null && edgelist.size() > 0) {
					AmbiguousZ []amb=AmbiguousZ.getAmbiguousZs(p);
					Iterator<EdgeSimple> elist = edgelist.iterator();
					EdgeSimple edge = null;
					while (elist.hasNext()) {
						edge = (EdgeSimple) elist.next();
						Complex []pts=p.ends_edge(edge, amb);
						cpScreen.drawEdge(pts[0],pts[1],dispFlags);
						count++;
					}
				}
				break;
			} // finished with edges
			case 'f': { // faces
				int f;
				boolean circleToo=false;
				if (sub_cmd.contains("b"))
					circleToo=true;
				FaceLink faceLink = new FaceLink(p, items);
				if (faceLink.size() <= 0)
					break; // nothing in list

				AmbiguousZ []amb=AmbiguousZ.getAmbiguousZs(p);

				Iterator<Integer> flist = faceLink.iterator();
				while (flist.hasNext()) {
					f = (Integer) flist.next();
					Complex []pts=p.corners_face(f, amb);
					if (!dispFlags.colorIsSet)
						dispFlags.setColor(p.faces[f].color);
					if (dispFlags.label)
						dispFlags.setLabel(Integer.toString(f));
					cpScreen.drawFace(pts[0],pts[1], pts[2], null, null, null, dispFlags);
					if (circleToo) { // also, color circle this face is responsible for
						int cirIndx=p.faces[f].vert[(p.faces[f].indexFlag+2)%3];
						if (!dispFlags.colorIsSet)
							dispFlags.setColor(p.kData[cirIndx].color);
						// suppress label
						dispFlags.setLabel(null);
						
						cpScreen.drawCircle(pts[cirIndx],p.rData[cirIndx].rad,
								dispFlags);
					}
// debug			System.out.println("count = "+count);
					count++;
				}
				break;
			} // done with faces
			case 'P': // pavers --- i.e., polygons defined by outer edges of
				// faces sharing specified vertices (see 'pave' command);
			{
				NodeLink nodeLink = new NodeLink(p, items);
				if (nodeLink.size() <= 0)
					break; // nothing in list
				AmbiguousZ []amb=AmbiguousZ.getAmbiguousZs(p);
				
				// get centers
				Iterator<Integer> vlist = nodeLink.iterator();
				while (vlist.hasNext()) {
					v=vlist.next();

					// some set up
					if (!dispFlags.colorIsSet)
						dispFlags.setColor(p.kData[v].color);
					if (dispFlags.label)
						dispFlags.setLabel(Integer.toString(v));

					Complex []pts=p.corners_paver(v,amb);
					int n=pts.length;

					double []fanCenters=new double[2*n];
					for (int j=0;j<n;j++) {
						fanCenters[2*j]=pts[j].x;
						fanCenters[2*j+1]=pts[j].y;
					}
					cpScreen.drawClosedPoly(n, fanCenters, dispFlags);

					if (dispFlags.label)
						cpScreen.drawIndex(p.rData[v].center, v, 1);

					count++;
				} // end of while on v
				break;
			} // done with pavers

			case 'R': // display side-pairings
			{
				// TODO: should we allow color info override??
				int numSides = -1;
				if (p.getSidePairs() == null || (numSides = p.getSidePairs().size()) == 0)
					break;
				boolean do_mate = false;
				boolean do_circle = false;
				boolean do_label = false;
				if (sub_cmd.length() > 0) { // additional options
					for (int j = 0; j < sub_cmd.length(); j++) {
						char cc = sub_cmd.charAt(j);
						if (cc == 'p')
							do_mate = true;
						else if (cc == 'n')
							do_label = true;
						else if (cc == 'c')
							do_circle = true;
					}
				}
				Vector<Integer> verts = new Vector<Integer>(numSides);
				// default or 'a' to all
				if (items == null || items.size() == 0
						|| ((String) items.get(0)).contains("a")) {
					for (int j = 0; j < numSides; j++)
						verts.add(j, j);
				} else {
					for (int j = 0; j < items.size(); j++) {
						try {
							int n = Integer.valueOf(items.get(j));
							if (n >= 0 && n < numSides)
								verts.add(j, n);
						} catch (Exception ex) {
						}
					}
				}
				int n, k;
				for (int j = 0; j < verts.size(); j++) {
					n = (Integer) verts.get(j);
					EdgePair epair = p.getSidePairs().get(n);
					int thickness = cpScreen.getLineThickness();
					if (thickness<4) thickness=4;

					count += p.sa_draw_bdry_seg(n, do_label, do_circle,
							epair.color, thickness);
					// deBugging.LayoutBugs.log_RedCenters(p); // LayoutBugs.pfacered(p);
					
					if (do_mate) {
						// do the paired edge?
						EdgePair ep = (EdgePair) p.getSidePairs().get(n);
						if ((k = ep.mateIndex) >= 0)
							p.sa_draw_bdry_seg(k, do_label, do_circle,
									epair.color, thickness);
					}
				}
				break;
			}
			case 's': // shape defined by comb geodesics through vert list 
			{
				NodeLink vertlist = new NodeLink(p, items);
				if (vertlist.size()==0)
					break;
				if (vertlist.getFirst()!=vertlist.getLast())
					vertlist.add(vertlist.getFirst());
				
				// now find comb geodesic, create corner list
				EdgeLink elist=EdgeLink.verts2edges(p,vertlist,false);
				int lnum=elist.size();
				double[] corners = new double[2 * (lnum+1)];
				int tick=0;
				z=new Complex(p.rData[((EdgeSimple)elist.get(0)).v].center);
				corners[tick*2]=z.x;
				corners[tick*2+1]=z.y;
				Iterator<EdgeSimple> el=elist.iterator();
				EdgeSimple edge=null;
				while (el.hasNext()) {
					edge=el.next();
					z=new Complex(p.rData[edge.w].center);
					corners[tick*2]=z.x;
					corners[tick*2+1]=z.y;
					tick++;
				}
				cpScreen.drawClosedPoly(lnum,corners,dispFlags);
				count++;
				break;
			}
			case 't': // trinket (i.e., dots, crosses, etc.) at given points
				// note: trinkets also used with 'd' dual calls, e.g., at tangency pts
			{
				// format: 't{x}' or 't{x}c{col}' where 'x' is trinket code.
				// Note: should already have set 'trinket' code.
				
				// process options: 'z' at given points; 'f' at given BaryPoints
				try {
				String str=items.get(0);
				if (str.charAt(0)=='z') { // at explicit complex points
					items.remove(0);
					PointLink ptlink=new PointLink(p,items);
					Iterator<Complex> ptl=ptlink.iterator();
					while (ptl.hasNext()) {
						Complex ptz=ptl.next();
						cpScreen.drawTrinket(trinket,ptz,dispFlags);
						count++;
					}
					break;
				}
				if (str.charAt(0)=='f') { // in given face at given barycentric coords
					items.remove(0);
					BaryLink bylink=new BaryLink(p,items);
					Iterator<BaryPoint> byl=bylink.iterator();
					while (byl.hasNext()) {
						BaryPoint bp=byl.next();
						if (bp.face>0 && bp.face<=p.faceCount) { // must have face index
							int []vert=p.faces[bp.face].vert;
							z=bp.bp2Complex(p.hes,p.rData[vert[0]].center,p.rData[vert[1]].center,
									p.rData[vert[2]].center);
							cpScreen.drawTrinket(trinket,z,dispFlags);
							count++;
						}
					}
					break;
				}
				} catch(Exception ex) {}
				
				// default to centers for list of vertices
				NodeLink nodeLink = new NodeLink(p, items);
				if (nodeLink.size() <= 0)
					break; // nothing in list
				Iterator<Integer> vit = nodeLink.iterator();
				while (vit.hasNext()) {
					v = (Integer) vit.next();
					z = p.rData[v].center;
					if (!dispFlags.colorIsSet)
						dispFlags.setColor(p.kData[v].color);
					cpScreen.drawTrinket(trinket, z,dispFlags);
					count++;
				}
				break;
			} // done with 't'
			case 'T': // tiles (if they exist) (note: 'ConformalTiling' extender 
				// has more complete options)
			{
				if (p.tileData!=null && p.tileData.tileCount>0) {
					// default to 'all'
					TileLink tileLink=new TileLink(p.tileData,items);
					if (tileLink==null || tileLink.size()==0)
						for (int t=1;t<=p.tileData.tileCount;t++)
							tileLink.add(t);
					if (tileLink.size() <= 0)
						break; // nothing in list

					Iterator<Integer> vit = tileLink.iterator();
					while (vit.hasNext()) {
						int t = (Integer) vit.next();
						if (t>0 && t<=p.tileData.tileCount) {
							Tile tile=p.tileData.myTiles[t];
							if (tile==null)
								break;
							if (!dispFlags.colorIsSet)
								dispFlags.setColor(tile.color);
							
							if (!dispFlags.colorIsSet) {
								dispFlags.setColor(tile.color);
							}
							
							// get list of tile border and make axis-extended edgelist
							NodeLink cornlist=tile.tileBorderLink();
							EdgeLink tedgelist=EdgeLink.verts2edges(p,cornlist,true);
							Iterator<EdgeSimple> tel=tedgelist.iterator();
							EdgeSimple edge=null;
							int lnum=tedgelist.size();
							double[] corners = new double[2 * (lnum+1)];
							int tick=0;
							z=new Complex(p.rData[((EdgeSimple)tedgelist.get(0)).v].center);
							corners[tick*2]=z.x;
							corners[tick*2+1]=z.y;
							while (tel.hasNext()) {
								edge=tel.next();
								z=new Complex(p.rData[edge.w].center);
								corners[tick*2]=z.x;
								corners[tick*2+1]=z.y;
								tick++;
							}
							DispFlags tmpFlags=dispFlags.clone();
							tmpFlags.label=false;
							cpScreen.drawClosedPoly(lnum, corners, tmpFlags);
							
							// debug=true;
							if (debug) 
								PackControl.canvasRedrawer.paintMyCanvasses(p,false);

							if (dispFlags.label) { // put at approximate center
								Complex wc=null;
								// if there is a 'baryVert', use its center
								int bv=tile.baryVert;
								if (bv>0 && bv<=p.nodeCount) {
									wc=p.rData[tile.baryVert].center;
								}
								
								// else use average of corner verts centers
								else {
									Vector<Complex> cz=new Vector<Complex>(0);
									for (int jj=0;jj<tile.vertCount;jj++) 
										cz.add(p.rData[tile.vert[jj]].center);
									
									// for sphere, compute via vectors --- may end up at antipodal point 
									if (p.hes>0) {
										double xc=0.0;
										double yc=0.0;
										double zc=0.0;
										Iterator<Complex> czit=cz.iterator();
										while (czit.hasNext()) {
											double []xyz=SphericalMath.s_pt_to_vec(czit.next());
											xc+=xyz[0];
											yc+=xyz[1];
											zc+=xyz[2];
										}
										xc /=tile.vertCount;
										yc /=tile.vertCount;
										zc /=tile.vertCount;
										wc=SphericalMath.proj_vec_to_sph(xc,yc,zc);
									}
									else {
										Iterator<Complex> zpts=cz.iterator();
										wc=new Complex(0.0);
										while (zpts.hasNext()) {
											wc=wc.add(zpts.next());
										}
										wc=wc.divide((double)tile.vertCount);
									}
								}
								
//								Complex wc=new Complex(0.0);
//								for (int vj=0;vj<tile.vertCount;vj++)
//									wc = wc.add(p.rData[tile.vert[vj]].center);
//								wc=wc.divide((double)tile.vertCount);

								cpScreen.drawIndex(wc,tile.tileIndex, 1);
								count++;
							} // end of label display
							
							count++;
						} 
					} // end of while
				} // done with tiles
				break;
			} // done with case 'T'
			case 'u': // unit circle
			{
				Complex cz = new Complex(0.0);
				double rad=1.0;
				if (p.hes > 0) 
					rad=Math.PI/2.0;
				cpScreen.drawCircle(cz, rad, dispFlags);
				count++;
				break;
			}
			case 'y': // circles defined by face centers (as in Delaunay triangulations)
			{
				FaceLink flink=new FaceLink(p,items); // should default to 'all'
				Iterator<Integer> fit=flink.iterator();
				CircleSimple sc=null;
				while (fit.hasNext()) {
					int face=fit.next();
					Complex z0=null;
					Complex z1=null;
					Complex z2=null;
					if (face>0 && face<=p.faceCount) {
						int []vert=p.faces[face].vert;
						z0=p.rData[vert[0]].center;
						z1=p.rData[vert[1]].center;
						z2=p.rData[vert[2]].center;
						if (p.hes<0) {  // hyp
							sc=HyperbolicMath.h_to_e_data(z0, p.rData[vert[0]].rad);
							z0=sc.center;
							sc=HyperbolicMath.h_to_e_data(z1, p.rData[vert[1]].rad);
							z1=sc.center;
							sc=HyperbolicMath.h_to_e_data(z2, p.rData[vert[2]].rad);
							z2=sc.center;
							sc=EuclMath.circle_3(z0,z1,z2);
						}
						else if (p.hes>0) {
							sc=SphericalMath.circle_3_sph(z0,z1,z2);
						}
						else {
							sc=EuclMath.circle_3(z0,z1,z2);
						}
						cpScreen.drawCircle(sc.center,sc.rad,dispFlags);
						count++;
					}
				}
				break;
			}
			} // end of main switch

			// if thickness changed, reset it
			if (thickhold >= 0 && cpScreen!=null)
				cpScreen.setLineThickness(thickhold);

		} // end of while to process flag segments

		return count;
	}
	
}
