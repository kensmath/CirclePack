package posting;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import dcel.D_SideData;
import dcel.HalfEdge;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.SphericalMath;
import komplex.DualTri;
import komplex.EdgeSimple;
import listManip.BaryCoordLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.TileLink;
import packing.PackData;
import panels.CPScreen;
import tiling.Tile;
import util.ColorUtil;
import util.DispFlags;
import util.PathBaryUtil;
import util.PathUtil;
import util.SphView;
import util.StringUtil;

/**
 * This static code parses PostScript writing actions
 * and carries them out. Preliminary file opening and
 * closing actions are handled in the calling routine.
 * @author kens
 */
public class PostParser {

	public static int postParse(PostFactory pF,PackData p,
			Vector<Vector<String>>flagSegs) {
		if (pF==null) {
			CirclePack.cpb.errMsg("'PostFactory' is not open.");
			return 0;
		}
		if (flagSegs==null || flagSegs.size()==0)
			return 0;
		int count=0;
		CPScreen cpScreen=p.cpScreen;

		// iterate through successive flag segments.
		Iterator<Vector<String>> its=flagSegs.iterator();
		
		while (its.hasNext()) {
			Vector<String> items=(Vector<String>)its.next();
			if (!StringUtil.isFlag(items.get(0))) 
				return count;
			  
			/* ============ the work is in processing ======== */

			String sub_cmd=(String)items.remove(0); 
			char c=sub_cmd.charAt(1); // grab first letter of flag
			sub_cmd=sub_cmd.substring(2); // remove '-' and first char
			int v;
			  
			// first, those cases NOT using 'compactDispFlags' options
			if (c == 'n') { // various labels
				if (sub_cmd.length() == 0)
					break;
				char sc = sub_cmd.charAt(0);
				try {
					if (sc == 'f') { // faces
						FaceLink link = new FaceLink(p, items);
						Iterator<Integer> fl = link.iterator();
						int f;
						while (fl.hasNext()) {
							f = (Integer) fl.next();
							Complex tri_cent = p.getFaceCenter(f);
							if (p.hes <= 0) {
								pF.postIndex(tri_cent, f);
							} else {
								tri_cent = cpScreen.sphView
										.toApparentSph(tri_cent);
								if (Math.cos(tri_cent.x) >= 0) // sphere; on
																// front?
									pF.postIndex(SphView
											.s_pt_to_visual_plane(tri_cent), f);
							}
							count++;
						}
					} else if (sc == 'v' || sc == 'c') { // circles
						NodeLink nodeLink = new NodeLink(p, items);
						Iterator<Integer> nl = nodeLink.iterator();
						while (nl.hasNext()) {
							v = (Integer) nl.next();
							Complex pt = p.getCenter(v);
							if (p.hes > 0) {
								pt = cpScreen.sphView.toApparentSph(pt);
								if (Math.cos(pt.x) >= 0.0) {
									pt = SphView.s_pt_to_visual_plane(pt);
									pF.postIndex(pt, v);
								}
							} else
								pF.postIndex(pt, v);
							count++;
						}
					} else if (sc == 'l') { // -nl {v} {str} str at center of v
						v = NodeLink.grab_one_vert(p, (String) items.get(0));
						pF.postStr(p.getCenter(v), (String) items.get(1));
						count++;
					} else if (sc == 'z') { // -nl {x} {y} {str}
						pF.postStr(new Complex(
								Double.parseDouble((String) items.get(0)),Double.parseDouble(
										(String) items.get(1))), (String) items
								.get(2));
						count++;
					}
				} catch (Exception ex) {
					throw new ParserException();
				}
			} // end of 'n'

			else if (c == 'W') { // text for postscript file (not displayed?)
				if (items.size() == 0)
					break;
				StringBuilder tmbf = new StringBuilder(items.remove(0) + " ");
				while (items.size() > 0) {
					tmbf.append(items.remove(0) + " ");
				}
				pF.postString(tmbf.toString());
				count++;
			}

			// --------- now, watch for 'compactDispFlag' ---------------
			// typical display flags involve optional color/thickness/fill
			Color col = ColorUtil.getFGColor();
			Complex z = null;
			double tx = -1.0; // thickness factor
			char dualChar = 'e';
			int trinket = 0; // for trinket code
	  
			// Have to catch dual flags '-d..' to get second character
			if (c == 'd') { // 'dc','df', 'de', 'dt', or 'dp'?
				if (sub_cmd.length() > 0) {
					dualChar = sub_cmd.charAt(0); // will need this second
													// character
					sub_cmd = sub_cmd.substring(1); // remove first char
				} else
					sub_cmd = "";
			}			// Have to catch 'trinket' flags to get code
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
			  
			DispFlags dispFlags=new DispFlags(sub_cmd,p.cpScreen.fillOpacity);

			// was a thickness factor specified? set 'tx'
			if (dispFlags.thickness>0) {
				int cd2 = dispFlags.thickness;
				if (cd2 > 15)
					cd2 = 15; // max for now
				// TODO: may need to play with this to mimic screen
				tx = (double)dispFlags.thickness;
			}
		  
			  /* ====== now go through numerous "disp" options ========= */
			switch (c) {
			case 'a': // face 'sectors', eucl only
			{
				if (p.hes != 0)
					break;
				
				FaceLink faceLink = new FaceLink(p, items);
				Iterator<Integer> flist = faceLink.iterator();
				while (flist.hasNext()) {
					int[] verts=p.getFaceVerts(flist.next());
					Complex[] sides = new Complex[3];
					double[] lgths = new double[3];
					for (int j = 0; j < 3; j++) {
						sides[j] = p.getCenter(verts[(j + 1) % 3])
								.minus(p.getCenter(verts[j]));
						lgths[j] = sides[j].abs();
					}
					for (int j = 0; j < 3; j++) {
						Complex cent = p.getCenter(verts[j]);
						double arg1 = sides[j].arg();
						double extent = sides[(j + 2) % 3].times(-1.0)
								.divide(sides[j]).arg();
						double rad = 0.5 * (lgths[j] - lgths[(j + 1) % 3] + lgths[(j + 2) % 3]);
						pF.postSector(cent, rad, arg1, extent, null);
					}
					count++;
				}
				break;
			}
			case 'g': // post path, if it exists
			{
				Vector<Vector<Complex>> paths = null;
				if (CPBase.ClosedPath != null)
					paths = PathUtil.gpPolygon(CPBase.ClosedPath);
				if (tx < 0.0)
					tx = 2.0; // default is thicker
				if (!dispFlags.colorIsSet)
					col = ColorUtil.coLor(1); // default to deep blue
				pF.postPath(paths, col, tx, true);
				count++;
				break;
			}
			case 'b': // bary-coord encoded paths, 'b' or 'bs'
			{
				Vector<Vector<Complex>> paths = null;

				// default to gridLines
				Vector<BaryCoordLink> myLines = CPBase.gridLines;
				if (sub_cmd != null && sub_cmd.length() > 0
						&& sub_cmd.charAt(0) == 's') // streamLines?
					myLines = CPBase.streamLines;

				if (myLines != null && myLines.size() > 0 && p.hes == 0) {
					// create paths from global vector Blink
					Path2D.Double path = null;
					for (int j = 0; j < myLines.size(); j++) {
						path = PathBaryUtil.baryLink2path(p, myLines.get(j));
						if (path != null) {
							paths = PathUtil.gpPolygon(path);
							pF.postPath(paths, ColorUtil.coLor(1), tx, true);
							count++;
						}
					}
				}
				break;
			}
			case 'B': // post faces and circles recomputed in drawing order
			case 'C': // post circles recomputed in drawing order
			case 'F': // post faces recomputed in drawing order
			{
				HalfLink hlink=null;
				FaceLink facelist = null;
				int first_face = 0;
				// no list? default to drawing order (plus stragglers)
				if (items.size() == 0) {
					hlink=p.packDCEL.fullOrder;
					facelist = new FaceLink(p, "Fs");
					// handle first face circles (without adjusting centers)
					if (c == 'C' || c == 'B') {
						first_face = p.firstFace;
						dcel.Face face = hlink.get(0).face;
						int[] verts=face.getVerts();
						for (int i = 0; i < 3; i++) {
							v = verts[i];
							z = p.getCenter(v);
							if (p.hes > 0)
								z = cpScreen.sphView.toApparentSph(z);
							if (!dispFlags.fill) { // not filled
								if (!dispFlags.colBorder) {
									pF.postCircle(p.hes, z, p.getRadius(v), tx);
								} else {
									if (!dispFlags.colBorder)
										col = p.vData[v].color;
									pF.postColorCircle(p.hes, z,
											p.getRadius(v), col, tx);
								}
							} 
							else {
								if (!dispFlags.colorIsSet)
									col = p.vData[v].color;
								if (!dispFlags.colBorder)
									pF.postCircle(p.hes, z, p.getRadius(v), tx);
								else
									pF.postColorCircle(p.hes, z,
											p.getRadius(v), col, tx);
							}
							if (dispFlags.label) { // label the face
								if (p.hes > 0 && Math.cos(z.x) >= 0.0) {
									z = util.SphView.s_pt_to_visual_plane(z);
									pF.postIndex(z, v);
								} else
									pF.postIndex(z, v);
							}
						} // end of for loop
						count++;
					} // done with first three circles
				} 
				else {	// there is a list
					facelist = new FaceLink(p, items);
					hlink=facelist.getHalfLink(p.packDCEL);
				}
				

				// now proceed with layout
				if (c == 'F') {
					count += p.packDCEL.layoutFactory(pF,hlink,dispFlags,
							null,true,false,tx);
				} else if (c == 'C') {
					count += p.packDCEL.layoutFactory(pF,hlink,null,
							dispFlags,true,false,tx);
				} else if (c == 'B') { // we have only one color we can use
					count += p.packDCEL.layoutFactory(pF,hlink,dispFlags,
							dispFlags,true,false,tx);
				}
				break;
			}
			case 'c': // circles
			{
				NodeLink nodeLink = new NodeLink(p, items);
				if (nodeLink.size() <= 0)
					break; // nothing in list

				Iterator<Integer> vlist = nodeLink.iterator();
				while (vlist.hasNext()) {
					v = (Integer) vlist.next();
					z = p.getCenter(v);

					// Note: unlike display calls, convert sph center here
					if (p.hes > 0)
						z = cpScreen.sphView.toApparentSph(z);

					if (!dispFlags.fill) {
						if (!dispFlags.colBorder)
							pF.postCircle(p.hes, z, p.getRadius(v), tx);
						else if (dispFlags.colorIsSet) // use special color
							pF.postColorCircle(p.hes, z, p.getRadius(v), dispFlags.getColor(),
									tx);
						else
							// use recorded color
							pF.postColorCircle(p.hes, z, p.getRadius(v),
									p.kData[v].color, tx);
					} else {
						if (!dispFlags.colorIsSet) // none set? use recorded color
							col = p.kData[v].color;
						if (!dispFlags.colBorder)
							pF.postFilledCircle(p.hes, z, p.getRadius(v), col,tx);
						else
							pF.postFilledColorCircle(p.hes, z, p.getRadius(v),col, col, tx);
					}
					count++;
				}

				// may still want to display indices
				if (dispFlags.label) {
					vlist = nodeLink.iterator();
					while (vlist.hasNext()) {
						v = (Integer) vlist.next();
						Complex pt = p.getCenter(v);
						if (p.hes > 0) {
							pt = cpScreen.sphView.toApparentSph(pt);
							if (Math.cos(pt.x) >= 0.0) {
								pt = SphView.s_pt_to_visual_plane(pt);
								pF.postIndex(pt, v);
							}
						} else
							pF.postIndex(pt, v);
					}
				}
				break;
			} // done with circles
			case 'd': { // dual objects: 'de' (default),'dc', 'df', 'dt', or
						// 'dp'
				switch (dualChar) {
				case 'e': // dual edges
				{
					EdgeLink elist = new EdgeLink(p, items);
					// get list of pairs of neighboring face indices
					GraphLink dualedges = p.dualEdges(elist);
					if (dualedges != null && dualedges.size() > 0) {
						EdgeSimple edge = null;
						Iterator<EdgeSimple> dedges = dualedges.iterator();
						while (dedges.hasNext()) {
							edge = (EdgeSimple) dedges.next();
							z = p.getFaceCenter(edge.v);
							Complex w = p.getFaceCenter(edge.w);
							if (p.hes > 0) {
								z = cpScreen.sphView.toApparentSph(z);
								w = cpScreen.sphView.toApparentSph(w);
							}
							if (!dispFlags.colorIsSet)
								pF.postEdge(p.hes, z, w, tx);
							else
								pF.postColorEdge(p.hes, z, w, dispFlags.getColor(), tx);
							count++;
						}
					}
					break;
				}
				case 'p': // tangency points, indexed by edge, marked by
							// 0-trinket
				{
					// record 'mark' for trinket 0
					pF.post_shape(0);
					// for scaling trinkets
					double diam = 5 / cpScreen.pixFactor;

					HalfLink hlist = new HalfLink(p, items);
					if (hlist != null && hlist.size() > 0) {
						HalfEdge edge = null;
						Iterator<HalfEdge> edges = hlist.iterator();
						while (edges.hasNext()) {
							edge = (HalfEdge) edges.next();
							Complex ctr = p.tangencyPoint(edge);
							if (ctr == null)
								break;
							if (p.hes > 0) {
								ctr = cpScreen.sphView.toApparentSph(ctr);
								if (Math.cos(ctr.x) >= 0) { // sphere; on front?
									double x = Math.sin(ctr.y)
											* Math.sin(ctr.x);
									double y = Math.cos(ctr.y);
									ctr = new Complex(x, y);
								}
							}

							if (!dispFlags.colorIsSet)
								pF.postTrinket(ctr, diam);
							else
								pF.postColorTrinket(ctr, diam, dispFlags.getColor());
							count++;
						}
					}
					break;
				}
				case 'c': // dual 'circles' indexed by faces
				{
					FaceLink faceLink = new FaceLink(p, items);
					// nothing in list?
					if (faceLink != null && faceLink.size() <= 0) 
						break;
					Iterator<Integer> flist = faceLink.iterator();
					int f;
					while (flist.hasNext()) {
						f = flist.next();
						CircleSimple sc = p.faceIncircle(f);
						z = sc.center;
						if (p.hes > 0)
							z = cpScreen.sphView.toApparentSph(z);
						if (!dispFlags.fill) {
							if (!dispFlags.colBorder)
								pF.postCircle(p.hes, z, sc.rad, tx);
							else if (dispFlags.colorIsSet)
								pF.postColorCircle(p.hes, z, sc.rad, dispFlags.getColor(), tx);
							else
								pF.postColorCircle(p.hes, z, sc.rad,dispFlags.getColor() , tx);
						} else { // filled
							if (!dispFlags.colBorder)
								pF.postFilledCircle(p.hes, z, sc.rad, dispFlags.getFillColor(), tx);
							else
								pF.postFilledColorCircle(p.hes, z, sc.rad,
										dispFlags.getFillColor(),dispFlags.getColor(), tx);
						}

						if (dispFlags.label) {
							pF.postIndex(z, f);
						}
						count++;
					}
					break;
				}
				case 'f': // dual 'faces' are indexed by vertices
				{
					NodeLink nodeLink = new NodeLink(p, items);
					if (nodeLink.size() <= 0)
						break; // nothing in list
					Iterator<Integer> vlist = nodeLink.iterator();
					while (vlist.hasNext()) {
						v = (Integer) vlist.next();
						if (!dispFlags.colorIsSet)
							dispFlags.setColor(p.kData[v].color);
						int num = p.countFaces(v);
						int[] faceFlower=p.getFaceFlower(v);
						Complex[] fanCenters = new Complex[num
								+ p.getBdryFlag(v)];
						for (int j = 0; j < num; j++) {
							int ff = faceFlower[j];
							z = new Complex(p.faceIncircle(ff).center);
							if (p.hes > 0)
								z = cpScreen.sphView.toApparentSph(z);
							fanCenters[j] = z;
						}
						// for bdry v, add v to list
						if (p.isBdry(v)) {
							z = p.getCenter(v);
							if (p.hes > 0)
								z = cpScreen.sphView.toApparentSph(z);
							fanCenters[num] = z;
						}

						if (!dispFlags.fill) {
							if (!p.isBdry(v)) {
								if (!dispFlags.colorIsSet)
									pF.postPoly(p.hes, num, fanCenters, tx);
								else
									pF.postColorPoly(p.hes, num, fanCenters,
											dispFlags.getFillColor(), tx);
							} else { // just edges
								Complex z2 = new Complex(fanCenters[0]);
								Complex z1 = null;
								for (int j = 1; j < num; j++) {
									z1 = z2;
									z2 = new Complex(fanCenters[j]);
									if (!dispFlags.colorIsSet)
										pF.postEdge(p.hes, z1, z2, tx);
									else
										pF.postColorEdge(p.hes, z1, z2,dispFlags.getColor(),
												tx);
								}
							}
						} else { // filled
							if (!dispFlags.colBorder)
								pF.postFilledPoly(p.hes, num
										+ p.getBdryFlag(v), fanCenters,
										dispFlags.getFillColor(), tx);
							else
								pF.postFilledColorPoly(p.hes, num
										+ p.getBdryFlag(v), fanCenters,
										dispFlags.getFillColor(),dispFlags.getColor(), tx);
						}

						if (dispFlags.label) {
							z = p.getCenter(v);
							if (p.hes > 0)
								z = cpScreen.sphView.toApparentSph(z);
							pF.postIndex(z, v);
						}
						count++;
					}
					break;
				}
				case 't': // dual 'triangles' (i.e., formed by faces 3 tangency
							// pts)
							// indexed by face index
				{
					FaceLink faceLink = new FaceLink(p, items);
					if (faceLink != null && faceLink.size() <= 0) // nothing in
																	// list
						break;
					Iterator<Integer> flist = faceLink.iterator();
					int f;
					while (flist.hasNext()) {
						f = flist.next();
						int[] vts = p.getFaceVerts(f);
						DualTri dtri = new DualTri(
								p.getCenter(vts[0]), p.getCenter(vts[1]),
								p.getCenter(vts[2]),p.hes);
						Complex []Z=new Complex[3];
						Z[0] = new Complex(dtri.TangPts[0]);
						Z[1] = new Complex(dtri.TangPts[1]);
						Z[2] = new Complex(dtri.TangPts[2]);
						if (p.hes > 0) {
							Z[0] = cpScreen.sphView.toApparentSph(Z[0]);
							Z[1] = cpScreen.sphView.toApparentSph(Z[1]);
							Z[2] = cpScreen.sphView.toApparentSph(Z[2]);
						}
						
						// set face/bdry colors
						Color fcolor=null;
						Color bcolor=null;
						if (dispFlags.fill) {  
							if (!dispFlags.colorIsSet) 
								dispFlags.getColor();
							fcolor=dispFlags.getFillColor();
						}
						if (dispFlags.draw) {
							if (dispFlags.colBorder)
								bcolor=dispFlags.getColor();
							else 
								bcolor=ColorUtil.getFGColor();
						}
						pF.post_Poly(p.hes, Z, fcolor, bcolor, tx);

						// may still want to display indices
						if (dispFlags.label) {
							Complex cent = CommonMath.tripleIncircle(
									dtri.TangPts[0], dtri.TangPts[1],
									dtri.TangPts[2],p.hes);
							if (p.hes > 0)
								cent = cpScreen.sphView.toApparentSph(cent);
							pF.postIndex(cent, f);
						}
						count++;
					}
					break;
				}
				} // end of switch for dual faces/edges/circles/triangles/tang
					// pts

				break;
			} // end of 'd' dual options
			case 'e': // edges
			{
				EdgeLink edgelist = new EdgeLink(p);
				// hex extended edges? (if description empty, default to all)
				if (sub_cmd.length() > 0 && sub_cmd.charAt(0) == 'e'
						&& items.size() > 0) {
					edgelist.addEdgeLinks(items, true);
				} else
					edgelist.addEdgeLinks(items, false);
				if (edgelist != null && edgelist.size() > 0) {
					Iterator<EdgeSimple> elist = edgelist.iterator();
					EdgeSimple edge = null;
					while (elist.hasNext()) {
						edge = (EdgeSimple) elist.next();
						Complex c1 = p.getCenter(edge.v);
						Complex c2 = p.getCenter(edge.w);
						if (p.hes > 0) {
							c1 = cpScreen.sphView.toApparentSph(c1);
							c2 = cpScreen.sphView.toApparentSph(c2);
						}
						if (dispFlags.colBorder)
							pF.postColorEdge(p.hes, c1, c2, dispFlags.getColor(), tx);
						else
							pF.postEdge(p.hes, c1, c2, tx);
						count++;
					}
				}
				break;
			} // finished with edges
			case 'f': { // faces
				// options encoded along with 'f': f c n fg bg
				int f;
				FaceLink faceLink = new FaceLink(p, items);
				if (faceLink.size() <= 0)
					break; // nothing in list

				Iterator<Integer> flist = faceLink.iterator();
				while (flist.hasNext()) {
					dcel.Face face=p.packDCEL.faces[flist.next()];
					int[] verts=face.getVerts();
					Complex []Z=new Complex[3];
					for (int j=0;j<3;j++) {
						Z[j] = p.getCenter(verts[j]);
						if (p.hes > 0) 
							Z[j] = cpScreen.sphView.toApparentSph(Z[j]);
					}
					// set face/bdry colors
					Color fcolor=null;
					Color bcolor=null;
					if (dispFlags.fill) {  
						if (!dispFlags.colorIsSet) 
							dispFlags.setColor(face.color);
						fcolor=dispFlags.getFillColor();
					}
					if (dispFlags.draw) {
						if (dispFlags.colBorder)
							bcolor=dispFlags.getColor();
						else 
							bcolor=ColorUtil.getFGColor();
					}
					pF.post_Poly(p.hes, Z, fcolor, bcolor, tx);
					count++;
				}

				// may still want to post face indices
				if (dispFlags.label) {
					flist = faceLink.iterator();
					while (flist.hasNext()) {
						f = (Integer) flist.next();
						Complex tri_cent = p.getFaceCenter(f);
						if (p.hes <= 0) {
							pF.postIndex(tri_cent, f);
						} else {
							tri_cent = cpScreen.sphView.toApparentSph(tri_cent);
							if (Math.cos(tri_cent.x) >= 0) // sphere; on front?
								pF.postIndex(
										SphView.s_pt_to_visual_plane(tri_cent),f);
						}
						count++;
					}
				}
				break;
			} // done with faces
			case 'R': // post side-pairings
			{
				// TODO: should we allow color/thickness info override??
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
							int n = Integer.parseInt((String) items.get(j));
							if (n >= 0 && n < numSides)
								verts.add(j, n);
						} catch (Exception ex) {
						}
					}
				}
				int n, k;
				for (int j = 0; j < verts.size(); j++) {
					n = (Integer) verts.get(j);
					D_SideData epair = p.getSidePairs().get(n);
					count += p.post_bdry_seg(pF, n, do_label, do_circle,
							epair.color, tx);
					if (do_mate) { // do paired edge?
						D_SideData ep =p.getSidePairs().get(n);
						if ((k = ep.mateIndex) >= 0)
							p.post_bdry_seg(pF, k, do_label, do_circle,
									epair.color, tx);
					}
				}
				break;
			}
			case 't': // trinket (dots,crosses,etc.) at cir centers
			{
				// format: 't{x}' or 't{x}c{col}' where 'x' is trinket code.
				// Note: should already have set 'trinket' code.
				NodeLink nodeLink = new NodeLink(p, items);
				if (nodeLink.size() <= 0)
					break; // nothing in list

				// record 'mark' definition in the PostScript file
				pF.post_shape(trinket);
				// for scaling trinkets
				double diam = 5 / cpScreen.pixFactor;

				Iterator<Integer> vit = nodeLink.iterator();
				while (vit.hasNext()) {
					v = (Integer) vit.next();
					z = p.getCenter(v);
					boolean front = true;
					if (p.hes > 0) { // move z to visual plane
						z = cpScreen.sphView.toApparentSph(z);
						if (Math.cos(z.x) < 0)
							front = false; // on back
						else
							z = SphView.s_pt_to_visual_plane(z);
					}
					if (p.hes <= 0 || front) {
						if (dispFlags.colBorder) {
							if (!dispFlags.colorIsSet)
								dispFlags.setColor(p.kData[v].color);
							pF.postColorTrinket(z, diam, dispFlags.getColor());
						} else
							pF.postTrinket(z, diam); // background
						count++;
					}
				}
				break;
			}
			case 'T': // tiles (if they exist) (note: 'ConformalTiling' extender 
				// has more complete options)
				// TODO: emulating DisplayParser; not yet done (7/9/2019)
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
							
							if (!dispFlags.colorIsSet) {
								dispFlags.setColor(tile.color);
							}
							
							// get list of tile border and make axis-extended edgelist
							NodeLink cornlist=tile.tileBorderLink();
							EdgeLink tedgelist=
								EdgeLink.verts2edges(p.packDCEL,cornlist,true);
							Iterator<EdgeSimple> tel=tedgelist.iterator();

							// set face/bdry colors
							Color fcolor=null;
							Color bcolor=null;
							if (dispFlags.fill) {  
								if (!dispFlags.colorIsSet) 
									dispFlags.setColor(tile.color);
								fcolor=dispFlags.getFillColor();
							}
							if (dispFlags.draw) {
								if (dispFlags.colBorder)
									bcolor=dispFlags.getColor();
								else 
									bcolor=ColorUtil.getFGColor();
							}
							
							Complex []Z=new Complex[tedgelist.size()];
							int tick=0;
							while (tel.hasNext()) {
								EdgeSimple edge=tel.next();
								int vv=edge.v;
								Z[tick]=p.getCenter(vv);
								if (p.hes>0)
									Z[tick]=p.cpScreen.sphView.toApparentSph(Z[tick]);
								tick++;
							}
							pF.post_Poly(p.hes, Z, fcolor, bcolor, tx);
							
							if (dispFlags.label) { // put at approximate center
								Complex wc=null;
								// if there is a 'baryVert', use its center
								int bv=tile.baryVert;
								if (bv>0 && bv<=p.nodeCount) {
									wc=p.getCenter(tile.baryVert);
								}
								
								// else use average of corner verts centers
								else {
									Vector<Complex> cz=new Vector<Complex>(0);
									for (int jj=0;jj<tile.vertCount;jj++) 
										cz.add(p.getCenter(tile.vert[jj]));
									
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
			case 'u': // the unit circle
			{
				z = new Complex(0.0);
				if (p.hes > 0)
					z = cpScreen.sphView.toApparentSph(z);
				pF.postUnitCircle(p.hes, z);
				break;
			}

			} // end of main switch
	} // end of while
	return count;
	}
	
}
