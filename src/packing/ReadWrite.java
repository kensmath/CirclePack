package packing;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import baryStuff.BaryPacket;
import baryStuff.BaryPoint;
import baryStuff.BaryPtData;
import circlePack.PackControl;
import combinatorics.komplex.Face;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import komplex.EdgeSimple;
import komplex.Triangulation;
import listManip.BaryCoordLink;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Point3D;
import packing.PackData.PackState;
import tiling.Tile;
import tiling.TileData;
import util.ColorUtil;
import util.StringUtil;

public class ReadWrite {
	
	public static double TOLER=.0000000001; // TODO: fix up all thresholds
	public static double OKERR=.000000001;
	public static final int MAX_ACCUR=15;   // digits of accuracy for file writing
	public static final int MAX_PETALS=1000; // the most petals a flower can have

	public static int[] readOldNew;

	/**
	 * Read new circle packing (or data for existing packing) into pack
	 * 'p' from an open file. Return 0 on error. Key "NODECOUNT:" or 
	 * "TRIANTULATION:" indicates new packing, in which case basic 
	 * combinatorics are read; "CHECKCOUNT:" indicates data for 'p'; 
	 * "TILECOUNT:" indicates tiling information (tiles and their 
	 * surrounding vertex indices);* "TRIANGULATION:" indicates 
	 * triples forming faces. (OBE: New packing in 'Lite' form
	 * starts with magic number 1234321.)
	 * 
	 * @return bit-coded integer for what was read as follows:
	 * 
	 *         1: 00001 basic combinatorics (new pack) 2: 00002 geometry 3: 00004
	 *         non-default inv_dist & aimsinv_dist
	 * 
	 *         4: 00010 radii 5: 00020 centers 6: 00040 angle sums
	 * 
	 *         7: 00100 vertex_map (if it exists) 8: 00200 non-empty lists
	 *         vlist/flist/elist 9: 00400 colors (circle or face, non-default)
	 * 
	 *         10: 01000 vertex/face plotFlags 11: 02000 xyz points 12: 04000
	 *         edge-pairing Mobius transformations
	 * 
	 *         13: 010000 triangles 14: 020000 tiling data 15: 040000 display flags
	 * 
	 *         16: 0100000 non-empty global lists Vlist/Flist/Elist 17: 0200000 dual
	 *         tiling (only, and as 'TILECOUNT:' file) 18: 0400000 misc other:
	 *         interactions
	 * 
	 *         19: 01000000 utility integers 20: 02000000 utility double values 21:
	 *         04000000 utility complex values
	 * 
	 *         22: 010000000 neutral, and used so we can return non-zero 'flags'
	 * 
	 *         CAUTION: responsibility of calling routine to update pack info (eg.,
	 *         aims, centers, etc) based on bits set in return value. For a new
	 *         pack, reading vertex_map, lists, or colors causes defaults to be set
	 *         before info is read in. When pack is not new pack (CHECKCOUNT case),
	 *         the info read in supercedes that in pack.
	 * 
	 *         CAUTION: this may be instanceof 'TileData' without being a tiling.
	 * 
	 *         In CHECKCOUNT case, return 0 if checkcount exceeds nodeCount; if
	 *         checkcount <= nodeCount, then FLOWER info could be inconsistent with
	 *         the packing.
	 * 
	 *         NEUTRAL is for data that doesn't depend on nodeCount match.
	 * 
	 *         4/2021: Processing dcel structure changes vertex indices, so 
	 *         we use 'readOldNew[]' and call 'rON' to read data based on 
	 *         old indices into appropriate new indices.
	 *         
	 * @param fp BufferedReader, (opened by calling routine)
	 * @param p PackData, (instantiated by calling routine)
	 * @param filename String
	 * @return int 'flags' bit-encodes what was read, -1 or 0 on error
	 */
	public static int readpack(BufferedReader fp,
			PackData p,String filename) {
		readOldNew = null;
		try {
			fp.mark(1000); // mark, only needed for Lite form
		} catch (Exception ex) {
		} // proceed anyway
		p.getDispOptions = null;
		int newAlpha = -1;
		int newGamma = -1;
		int flags = 0;
		int vert = 0;
		double x, y;
		double f;
		boolean newPacking = false;
		boolean gotFlowers = false;
		boolean col_c_flag = false;
		boolean col_f_flag = false;
		boolean gotAims=false;
		EdgeLink vertMarks = null; // holds optional marks
//	        boolean dcelread=false; // true, dcel data, triggered by key "BOUQUET:"

		PackState state = PackState.INITIAL;
		String line;

		// Must first find 'NODECOUNT:', 'CHECKCOUNT:', 'TILECOUNT:', or 'NEUTRAL:'
		try {
			while (state == PackState.INITIAL && (line = StringUtil.ourNextLine(fp)) != null) {
				StringTokenizer tok = new StringTokenizer(line);
				while (tok.hasMoreTokens()) {
					String mainTok = tok.nextToken();
					if (mainTok.equals("NODECOUNT:")) {
						flags |= 0001;
						newPacking = true;
						p.fileName = "";
						p.hes = 0;
						int intdata = Integer.parseInt(tok.nextToken());
						if (intdata < 3) {
							// TODO: error message
							return 0;
						}
						p.reset_pack_space(intdata);
						p.nodeCount = intdata;
						p.tileData = null;
						state = PackState.NODECOUNT;
					} else if (mainTok.equals("CHECKCOUNT:")) {
						if (!p.status) {
							p.flashError("Pack " + p.packNum + 
									" is empty; can't read extra data");
							return 0;
						}
						// sometimes we want to proceed even if counts don't match
						try {
							mainTok = tok.nextToken();
							int intdata = Integer.parseInt(mainTok);
							if (intdata != p.nodeCount) {
								p.flashError("CHECKCOUNT failed to match 'nodeCount'");
								return -1;
							}
						} catch (Exception ex) {
							p.flashError("CHECKCOUNT w/o number: proceed with reading");
						}
						state = PackState.CHECKCOUNT;
					} else if (mainTok.equals("TILECOUNT:")) {
						flags |= 0001;
						flags |= 020000;
						newPacking = true;
						p.fileName = "";
						p.hes = 0;
						String chaug = tok.nextToken();
						int intdata = -1;

						// my have " (augmented) " before count
						if (chaug.startsWith("(aug"))
							intdata = Integer.parseInt(tok.nextToken());
						else
							intdata = Integer.parseInt(chaug);
						if (intdata < 1) {
							// TODO: error message
							return 0;
						}
						p.reset_pack_space(intdata);
						p.tileData = new TileData(p,intdata,3); // default to tile mode 3
						p.nodeCount = 0; // this should be updated with FLOWERS processing
						state = PackState.TILECOUNT;
					} else if (mainTok.startsWith("TRIANGULATION")) {
						state = PackState.TRIANGULATION;
						flags |= 0001;
						newPacking = true;
						break;
					} else if (mainTok.equals("NEUTRAL:")) {
						state = PackState.NEUTRAL;
					}

					// last hope: this is a raw triangulation
					else {

						try {
							Integer.parseInt(mainTok);
						} catch (Exception ex) {
							break;
						}
						tok = new StringTokenizer(line);
						
						// check if looks like a triangulation
						if (tok.countTokens() < 3) // does not
							break;
						state = PackState.TRIANGULATION;
						while (tok.hasMoreTokens()) {
							tok.nextToken();
						}
						fp.reset(); // to restore first line
						newPacking = true;
						flags |= 0001;
						break;
					}
				}
			} // end of while for various 'PackState's
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("Exception in reading '" + filename + "'.");
			return -1;
		}

		// didn't get necessary key word at start
		if (state == PackState.INITIAL) {
			CirclePack.cpb.errMsg("Read of '" + filename
					+ "' failed: 'NODECOUNT:', 'CHECKCOUNT:', 'TILECOUNT:', 'TRIANGULATION:' or PackLite magic number not at top");
			return -1;
		}

		// Reaching here, state must be NODECOUNT, CHECKCOUNT, TILECOUNT,
		// TRIANGULATION or NEUTRAL
		// If NODECOUNT, must get FLOWERS next (can pick up PACKNAME,
		// ALPHA..,, GEOM along the way); if TILECOUNT, must get TILES.
		try {
			while (newPacking && !gotFlowers && (line = StringUtil.ourNextLine(fp)) != null) {
				StringTokenizer tok = new StringTokenizer(line);
				while (tok.hasMoreTokens()) {
					String mainTok = tok.nextToken();
					if (mainTok.equals("PACKNAME:")) {
						if (tok.hasMoreElements()) {
							p.fileName = tok.nextToken();
						}
					} else if (mainTok.equals("GEOMETRY:") && newPacking) {
						p.hes = 0; // eucl by default
						if (tok.hasMoreElements()) {
							String ts = tok.nextToken();
							if (ts.contains("yp") || ts.contains("YP")) {
								p.hes = -1;
							} // hyperbolic
							else if (ts.contains("ph") || ts.contains("PH")) {
								p.hes = 1;
							} // spherical
						}
					} else if (mainTok.equals("ALPHA/GAMMA:")) {
						try {
							newAlpha = Integer.parseInt(tok.nextToken());
							newGamma = Integer.parseInt(tok.nextToken());
						} catch (Exception ex) {
							continue;
						}
					} else if (mainTok.equals("ALPHA/BETA/GAMMA:")) { // old version
						try {
							newAlpha = Integer.parseInt(tok.nextToken());
							int deadbeta=Integer.parseInt(tok.nextToken());
							newGamma = Integer.parseInt(tok.nextToken());
						} catch (Exception ex) {
							continue;
						}
					} else if ((state == PackState.NODECOUNT
							&& (mainTok.equals("FLOWERS:") || mainTok.equals("BOUQUET:")))
							|| (state == PackState.TILECOUNT
									&& (mainTok.equals("TILES:") || mainTok.equals("TILEFLOWERS:")))) {

						// NOTE: as of 8/11/2021,
						// equate "BOUQUET:" and "FLOWERS:" calls
						// read all or until error.
//	                    	if (mainTok.equals("BOUQUET:") || mainTok.equals("FLOWERS:"))
//	                    		dcelread=true;
//	                    	int num;

						// normal packing
						if (state == PackState.NODECOUNT) {
							try {
								int maxv=-1;
								int[][] bouquet = new int[p.nodeCount + 1][];
								// data: contiguous index order, starting at v=1.
								// must have v n v_0 .... v_n is all on one line
								while (vert <p.nodeCount && 
										(line = StringUtil.ourNextLine(fp)) != null) {
									StringTokenizer loctok = new StringTokenizer(line);
									vert = Integer.valueOf(loctok.nextToken());
									int num = Integer.valueOf(loctok.nextToken());
									if (num <= 0)
										throw (new Exception()); // bomb out
									bouquet[vert] = new int[num + 1];

									for (int i = 0; i <= num; i++) {
										int nv=Integer.valueOf(loctok.nextToken());
										maxv=(nv>maxv) ? nv:maxv;
										bouquet[vert][i] = nv;
									}
								} // end of while
								if (vert < p.nodeCount) {
									p.flashError("Read failed while getting flowers");
									return -1;
								}

								PackDCEL pdc;
								int tmpAlpha = newAlpha;
								if (tmpAlpha == -1) {
									tmpAlpha=1; // just need something
								}
								if ((pdc = CombDCEL.getRawDCEL(bouquet, tmpAlpha)) == null) {
									p.flashError("Problem reading DCEL data");
									return -1;
								}
								pdc.redChain = null; // pdc.oldNew.size();
								
								// catch problems with layout, e.g. redchain or fix 
								try {
									pdc.fixDCEL(p);
									if (pdc.oldNew != null && pdc.oldNew.size() > 0) {
										readOldNew = new int[maxv + 1];
										Iterator<EdgeSimple> vmp = pdc.oldNew.iterator();
										while (vmp.hasNext()) {
											EdgeSimple edge = vmp.next();
											readOldNew[edge.v] = edge.w;
										}
									}
								} catch(Exception ex) {
									p.flashError("problem with redchain or fixDCEL");
								}

								gotFlowers = true;
							} catch (Exception ex) { // try to reset to previous line and proceed
//	                    			try {fp.reset();} catch(IOException ioe) {
								p.flashError("reading exception:" + ex.getMessage());
								return -1;
//	                    			}
							}
						}

						// else TILECOUNT: two options, TILES: or TILEFLOWERS:
						else {
							if (mainTok.equals("TILES:")) { // don't know 'nodeCount', and indices may not be
								// in sequence; find largest index for now, handled in 'tiles2packing'

								try {
									boolean augmented = false;
									if (tok.hasMoreTokens()) {
										if (tok.nextToken().startsWith("(aug"))
											augmented = true;
									}
									int tick = 1;
									p.nodeCount = 0;
									// TILES data lines: 't n v_0 v_1 ... v_(n-1)' where
									// t=tile number (1 to tileCount); n=number of vertices;
									// and list of verts (NOT neighboring tiles).
									// If "augmented", there are three vertices added between
									// each pair of corners.
									// Note: tiles from 1 to tileCount, irrespective of given t.
									while (tick <= p.tileData.tileCount && (line = StringUtil.ourNextLine(fp)) != null) {
										StringTokenizer loctok = new StringTokenizer(line);
										@SuppressWarnings("unused")
										int t = Integer.valueOf(loctok.nextToken()); // disregard t
										int num = Integer.valueOf(loctok.nextToken());
										if (num <= 0)
											throw (new Exception()); // bomb out
										p.tileData.myTiles[tick] = new Tile(p, p.tileData, num);
										p.tileData.myTiles[tick].tileIndex = tick;
										if (augmented) {
											p.tileData.myTiles[tick].augVertCount = 4 * num;
											p.tileData.myTiles[tick].augVert = new int[4 * num];
										}
										for (int i = 0; i < num; i++) {
											int nextp = Integer.valueOf(loctok.nextToken());
											p.nodeCount = (nextp > p.nodeCount) ? nextp : p.nodeCount;
											p.tileData.myTiles[tick].vert[i] = nextp;
											// if augmented, there are 3 augVerts between each pair of verts
											if (augmented) {
												p.tileData.myTiles[tick].augVert[4 * i] = nextp;
												for (int ii = 1; ii <= 3; ii++) {
													nextp = Integer.valueOf(loctok.nextToken());
													p.nodeCount = (nextp >p.nodeCount) ? nextp : p.nodeCount;
													p.tileData.myTiles[tick].augVert[4 * i + ii] = nextp;
												}
											}
										}
										tick++;
									} // end of while
									if (tick < (p.tileData.tileCount + 1)) {
										p.flashError("error: TILECOUNT not reached");
										return -1;
									}
								} catch (Exception ex) { // try to reset to previous line and proceed
									try {
										fp.reset();
									} catch (IOException ioe) {
										p.flashError("IOException: TILECOUNT, TILES, " + ex.getMessage());
										return -1;
									}
								}
							} // done with TILES:

							// else TILEFLOWERS: for each tile there's a line
							else {
								try {
									int tick = 1;
									p.nodeCount = 0;
									// TILEFLOWERS: data lines: 't n t0 e0 t1 e1 t2 e2 ... t(n-1) e(n-1)'
									// t=tile number (1 to tileCount); n=number of edges;
									// and list of pairs, tj = index of tile across (or 0) and ej is the index
									// in tile tj of its corresponding edge shared with t.
									// This allows for multiple edges or sharing edges with itself
									// Note: tiles from 1 to tileCount, given 't' value is ignored
									while (tick <= p.tileData.tileCount && 
											(line = StringUtil.ourNextLine(fp)) != null) {
										StringTokenizer loctok = new StringTokenizer(line);
										// TODO: may want to allow more flexible formating: e.g.,
										// prescribe indexes, let them be out of order of
										// have some that are missing. I don't know all the
										// consequences, so leave this for now.
										@SuppressWarnings("unused")
										int t = Integer.valueOf(loctok.nextToken()); // ignore t
										int vCount = Integer.valueOf(loctok.nextToken());
										if (vCount <= 0)
											throw (new Exception()); // bomb out
										Tile tile = new Tile(p,p.tileData, vCount);
										p.tileData.myTiles[tick] = tile;
										tile.tileIndex = tick;
										tile.tileFlower = new int[vCount][2];

										// read off the t e pairs:
										// We may want to read just a portion of the tiling,
										// so we zero out (make into bdry) any tj ej pair
										// with tj greater thatn tileCount
										for (int i = 0; i < vCount; i++) {
											int tt = Integer.valueOf(loctok.nextToken());
											int te = Integer.valueOf(loctok.nextToken());
											if (tt > p.tileData.tileCount) {
												tt = 0;
												te = 0;
											}
											tile.tileFlower[i][0] = tt;
											tile.tileFlower[i][1] = te;
										}
										tick++;
									} // end of while
									if (tick < (p.tileData.tileCount + 1)) {
										p.flashError("error: TILECOUNT not reached");
										return -1;
									}
								} catch (Exception ex) { // try to reset to previous line and proceed
									try {
										fp.reset();
									} catch (IOException ioe) {
										p.flashError("IOException: TILECOUNT, TILEFLOWERS, " + ex.getMessage());
										return -1;
									}
								}

							}
						} // end of TILES:/TILEFLOWERS: branch

						// success: other settings, toss old data
						p.status = true;
						p.vlist = null;
						p.elist = null;
						p.flist = null;
						p.hlist = null;
						p.glist = null;
						p.zlist = null;
						p.blist = null;
						p.vertexMap = null;
						p.xyzpoint = null;
					} // done with "FLOWERS", "BOUQUET", or "TILES"
					else if (state == PackState.TRIANGULATION && !gotFlowers) {
						Triangulation tri = new Triangulation();
						Vector<Face> theFaces = new Vector<Face>(50);
						boolean okay = true;
						Face face = null;
						do {
							tok = new StringTokenizer(line);
							if (tok.countTokens() != 3) {
								okay = false;
								fp.reset();
							} else {
								try {
									face = new Face(3);
									face.vert[0] = Integer.parseInt((String) tok.nextToken());
									face.vert[1] = Integer.parseInt((String) tok.nextToken());
									face.vert[2] = Integer.parseInt((String) tok.nextToken());
									theFaces.add(face);
									fp.mark(2000);
								} catch (Exception ex) {
									okay = false;
									fp.reset();
								}
							}
						} while (okay && (line = StringUtil.ourNextLine(fp, true)) != null);
						int Nfaces = theFaces.size();
						if (Nfaces < 3)
							throw new InOutException("Reading triangulation: found less than 3 faces");
						tri.faces = new Face[Nfaces + 1];
						tri.faceCount = Nfaces;
						for (int j = 0; j < theFaces.size(); j++)
							tri.faces[j + 1] = (Face) theFaces.elementAt(j);

						// create PackData
						PackData pdata = Triangulation.tri_to_Complex(tri, 0);
						if (pdata == null)
							throw new CombException(
									"Failed while reading TRIANGULATION");

						// success: other settings, toss old data
						p.status = true;
						p.vlist = null;
						p.elist = null;
						p.flist = null;
						p.hlist = null;
						p.glist = null;
						p.zlist = null;
						p.blist = null;
						p.vertexMap = null;
						p.xyzpoint = null;
						p.packExtensions = new Vector<PackExtender>(2);

						// fix up
						pdata.packDCEL.fixDCEL(p);
						p.set_aim_default();
						gotFlowers = true;
						break;
					} // end of TRIANGULATION case
				}

				// if TILECOUNT, then create the barycentric packing
				// and return
				if (state == PackState.TILECOUNT && gotFlowers) {

					// =========== main action, creating the packing =====
					PackData newPack = TileData.tiles2packing(p.tileData);
					if (newPack == null) {
						p.flashError("failed somehow in 'tiles2packing'");
						return -1;
					}
					p.status = true;
					p.packExtensions = new Vector<PackExtender>(2); // trash extenders
					p.nodeCount = newPack.nodeCount;
					p.hes = newPack.hes;
					p.intrinsicGeom = newPack.intrinsicGeom;
					p.vertexMap = null;
					p.xyzpoint = null;
					p.vlist = null;
					p.flist = null;
					p.elist = null;
					p.blist = null;
					p.glist = null;
					p.tlist = null;
					p.zlist = null;
					p.tileData = newPack.tileData;

					flags |= 020001;
					return flags;
				}

				if (gotFlowers)
					state = PackState.INITIAL;

			} // end of while for FLOWERS/GEOM/ etc.
		} catch (Exception ex) {
			p.flashError("Read of " + filename + " failed: " + ex.getMessage());
			return -1;
		}

		// Now, search for the rest of the data (have to allow
		// picking up GEOM,ABG, PACKNAME here too).
		try {
			boolean foundEnd = false;
			while ((line = StringUtil.ourNextLine(fp)) != null) {
				StringTokenizer tok = new StringTokenizer(line);
				while (tok.hasMoreTokens()) {
					String mainTok = tok.nextToken();

					// should have already been read if newPacking
					if (mainTok.equals("FLOWERS:") || mainTok.equals("BOUQUET:")) {
						p.flashError("FLOWERS/BOUQUET not allowed w/o NODECOUNT/TILECOUNT; disregard them");
						continue;
					} else if (mainTok.equals("PACKNAME:")) {
						if (tok.hasMoreElements()) {
							p.fileName = tok.nextToken();
						}
					} else if (mainTok.equals("GEOMETRY:") && newPacking) {
						p.hes = 0; // eucl by default
						if (tok.hasMoreElements()) {
							String ts = tok.nextToken();
							if (ts.contains("yp")) {
								p.hes = -1;
							} // hyperbolic
							else if (ts.contains("ph")) {
								p.hes = 1;
							} // spherical
						}
					} else if (mainTok.equals("ALPHA/GAMMA:")) {
						try {
							newAlpha = Integer.parseInt(tok.nextToken());
							newGamma = Integer.parseInt(tok.nextToken());
						} catch (Exception ex) {
							continue;
						}
					} else if (mainTok.equals("ALPHA/BETA/GAMMA:")) { // old version
						try {
							newAlpha = Integer.parseInt(tok.nextToken());
							newGamma = Integer.parseInt(tok.nextToken());
						} catch (Exception ex) {
							continue;
						}
					} else if (mainTok.equals("RADII:")) {
						vert = 1;
						// data must start on new line; read all or until error.
						try {
							while (vert <= p.nodeCount && (line = StringUtil.ourNextLine(fp)) != null) {
								StringTokenizer loctok = new StringTokenizer(line);
								f = Double.parseDouble(loctok.nextToken());
								p.setRadiusActual(rON(vert), f);
								vert++;
								while (vert <= p.nodeCount && loctok.hasMoreElements()) {
									f = Double.parseDouble(loctok.nextToken());
									p.setRadiusActual(rON(vert), f);
									vert++;
								}
							}
						} catch (Exception ex) { // try to reset to previous line and proceed
							if (vert <= p.nodeCount) {
								p.flashError("Shortage in number of radii; remainder set to default");
								double rad = 0.5;
								if (p.hes < 0)
									rad = 1.0 - Math.exp(-1.0);
								for (int i = vert; i <= p.nodeCount; i++)
									p.setRadius(rON(i), rad);
							}
							try {
								fp.reset();
							} catch (IOException ioe) {
								p.flashError("IOException: " + ioe.getMessage());
								return -1;
							}
						}
						state = PackState.INITIAL;
						if (vert <= p.nodeCount) {
							p.flashError("Shortage in number of radii; remainder set to default");
							double rad = 0.5;
							if (p.hes < 0)
								rad = 1.0 - Math.exp(-1.0);
							for (int i = vert; i <= p.nodeCount; i++)
								p.setRadius(rON(i), rad);
						} else
							flags |= 0010;
					} else if (mainTok.equals("CENTERS:")) {
						// DCELdebug.rededgecenters(packDCEL);
						vert = 1;
						// data must start on new line; read all or until error.
						try {
							while (vert <= p.nodeCount && (line = StringUtil.ourNextLine(fp)) != null) {
								StringTokenizer loctok = new StringTokenizer(line);
								x = Double.parseDouble(loctok.nextToken());
								y = Double.parseDouble(loctok.nextToken());
								p.packDCEL.setVertCenter(rON(vert), new Complex(x, y));
								vert++;
								while (vert <= p.nodeCount && loctok.hasMoreElements()) {
									x = Double.parseDouble(loctok.nextToken());
									y = Double.parseDouble(loctok.nextToken());
									p.packDCEL.setVertCenter(rON(vert), new Complex(x, y));
									vert++;
								}
							}
						} catch (Exception ex) { // try to reset to previous line and proceed
							if (vert <= p.nodeCount) {
								p.flashError("Shortage in number of centers; remainder set to zero");
								Complex z = new Complex(0.0, 0.0);
								for (int i = vert; i <= p.nodeCount; i++)
									p.setCenter(rON(i), z);
							}
							try {
								fp.reset();
							} catch (IOException ioe) {
								p.flashError("IOException: " + ioe.getMessage());
								return -1;
							}
						}
						if (vert <= p.nodeCount) {
							p.flashError("Shortage in number of centers; remainder set to zero");
							Complex z = new Complex(0.0, 0.0);
							for (int i = vert; i <= p.nodeCount; i++)
								p.setCenter(rON(i), z);
						} else
							flags |= 0020;
						state = PackState.INITIAL;
					}

					else if (mainTok.equals("TILES:")) {
						try {
							boolean augmented = false;
							int tick = -1;
							if (tok.hasMoreTokens()) {
								String nxtok = tok.nextToken();
								if (nxtok.startsWith("(aug")) {
									augmented = true;
									tick = Integer.parseInt(tok.nextToken()); // get tileCount
								} else // current token should be tileCount
									tick = Integer.parseInt(nxtok);
							} else
								throw new DataException("usage: 'tileCount' is missing");

							p.tileData = new TileData(p, tick, 3); // default to tile mode 3
							tick = 1;
							// TILES data lines: 't n x v_0 v_1 ... v_(n-1)' where
							// t=tile number (1 to tileCount); n=number of vertices;
							// and list of verts (NOT neighboring tiles).
							// If "augmented", there are 3 additional vertices between
							// each pair of corners.
							// Note: tiles from 1 to tileCount, irrespective of given t.
							while (tick <= p.tileData.tileCount && (line = StringUtil.ourNextLine(fp)) != null) {
								StringTokenizer loctok = new StringTokenizer(line);
								Integer.valueOf(loctok.nextToken()); // disregard t
								int num = Integer.valueOf(loctok.nextToken());
								if (num <= 0)
									throw (new Exception()); // bomb out
								p.tileData.myTiles[tick] = new Tile(p,p.tileData, num);
								p.tileData.myTiles[tick].tileIndex = tick;
								if (augmented) {
									p.tileData.myTiles[tick].augVertCount = 4 * num;
									p.tileData.myTiles[tick].augVert = new int[4 * num];
								}
								for (int i = 0; i < num; i++) {
									int nextp = Integer.valueOf(loctok.nextToken());
									p.tileData.myTiles[tick].vert[i] = nextp;
									// if augmented, there are 3 augVerts between each pair of verts
									if (augmented) {
										p.tileData.myTiles[tick].augVert[4 * i] = nextp;
										for (int ii = 1; ii <= 3; ii++) {
											nextp = Integer.valueOf(loctok.nextToken());
											p.nodeCount = (nextp > p.nodeCount) ? nextp : p.nodeCount;
											p.tileData.myTiles[tick].augVert[4 * i + ii] = nextp;
										}
									}
								}
								tick++;
							} // end of while
							if (tick <= p.tileData.tileCount) {
								p.flashError("error: TILECOUNT not reached");
								return -1;
							}
						} catch (Exception ex) { // try to reset to previous line and proceed
							try {
								fp.reset();
							} catch (IOException ioe) {
								p.flashError("IOException: TILES, " + ioe.getMessage());
								return -1;
							}
						}
					} else if (mainTok.equals("DISP_FLAGS:")) {
						// flags on the next line
						p.getDispOptions = StringUtil.ourNextLine(fp);
						// may start with "Disp" or "disp"
						if (p.getDispOptions.startsWith("disp") || 
								p.getDispOptions.startsWith("Disp")) {
							int k = p.getDispOptions.indexOf(" "); // find first space
							p.getDispOptions = p.getDispOptions.substring(k).trim();
						}
						// else, first must be a flag
						else if (p.getDispOptions==null || p.getDispOptions.length()==0 ||
								!StringUtil.isFlag(p.getDispOptions))
							p.getDispOptions = null;
					}

					// OBE; don't use this
					else if (mainTok.equals("CIRCLE_PLOT_FLAGS:") && !newPacking) {
						state = PackState.CIRCLE_PLOT_FLAGS;
						while (state == PackState.CIRCLE_PLOT_FLAGS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = Integer.parseInt(str);
								int flg = Integer.parseInt((String) loctok.nextToken());
								p.setPlotFlag(v, flg);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						flags |= 01000;
					}
					// TODO: problem with reading TILES in an existing packing is that we don't
//	       have 'newOrig' vertex translation info.                    
//	                  else if (mainTok.equals("TILES:")) { 
//	           		}

					// OBE; don't use this
					/*
					 * else if(mainTok.equals("FACE_PLOT_FLAGS:") && !newPacking){ state =
					 * PackState.FACE_PLOT_FLAGS; while (state==PackState.FACE_PLOT_FLAGS &&
					 * (line=StringUtil.ourNextLine(fp))!=null) { StringTokenizer loctok = new
					 * StringTokenizer(line); try { String str=(String)loctok.nextToken(); int
					 * v=Integer.parseInt(str); int v1=Integer.parseInt((String)loctok.nextToken());
					 * int v2=Integer.parseInt((String)loctok.nextToken()); int
					 * flg=Integer.parseInt((String)loctok.nextToken()); int[]
					 * faceFlower=getFaceFlower(v); for (int j=0;j<countFaces(v);j++) { int
					 * k=faceFlower[j]; int[] verts=packDCEL.faces[k].getVerts(); int ind; if (
					 * ((ind=check_face(k,v,v1)) >= 0 && verts[(ind+2)%3]==v2) ||
					 * ((ind=check_face(k,v1,v)) >= 0 && verts[(ind+2)%3]==v2)) {
					 * setFacePlotFlag(k,flg); j=countFaces(v); // t stop loop } } } catch(Exception
					 * ex) {state=PackState.INITIAL;} } flags |= 01000; }
					 */

					else if (mainTok.equals("SELECT_RADII:") && !newPacking) {
						// only for CHECKCOUNT cases
						state = PackState.SELECT_RADII;
						while (state == PackState.SELECT_RADII && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								double rad = Double.parseDouble((String) loctok.nextToken());
								p.setRadiusActual(v, rad);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}

						flags |= 0010;
					} else if (mainTok.equals("ANGLE_AIMS:")) {
						state = PackState.ANGLE_AIMS;
						if (newPacking)
							p.set_aim_default();
						while (state == PackState.ANGLE_AIMS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								double aim = Double.parseDouble((String) loctok.nextToken());
								p.setAim(v, aim);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						flags |= 00004;// problem: code both for aims and inv dist
						gotAims=true;
					} else if (mainTok.equals("INV_DISTANCES:")) {
						state = PackState.INV_DISTANCES;
						while (state == PackState.INV_DISTANCES && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								int v = rON(Integer.parseInt((String) loctok.nextToken()));
								int w = rON(Integer.parseInt((String) loctok.nextToken()));
								double invDist = Double.parseDouble((String) loctok.nextToken());
								p.set_single_invDist(v, w, invDist);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						flags |= 00004;
					} else if (mainTok.equals("ANGLESUMS:")) {
						state = PackState.ANGLESUMS;
						int v = 1;
						while (state == PackState.ANGLESUMS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							while (state == PackState.ANGLESUMS && 
									loctok.hasMoreTokens() && v <= p.nodeCount) {
								try {
									String str = (String) loctok.nextToken();
									double anglesums = Double.parseDouble(str);
									p.setCurv(rON(v), anglesums);
									v++;
								} catch (Exception ex) {
									state = PackState.INITIAL;
								}
							}
						}
						flags |= 0040;
					}
					// 'real' Schwarzian for edges, see 'Schwarzian.java'
					// TODO: Have to adjust for dcel structures.
					// data: v w schw
					else if (mainTok.equals("SCHWARZIANS:")) {
						state = PackState.SCHWARZIAN;
						while (state == PackState.SCHWARZIAN && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							while (state == PackState.SCHWARZIAN && loctok.hasMoreTokens()
									&& loctok.countTokens() == 3) {
								try {
									int v = rON(Integer.parseInt((String) loctok.nextToken()));
									int w = rON(Integer.parseInt((String) loctok.nextToken()));
									double schw = Double.parseDouble((String) loctok.nextToken());
									// TODO: can we find 'he' at this stage?
									HalfEdge he = p.packDCEL.findHalfEdge(new EdgeSimple(v, w));
									he.setSchwarzian(schw);
								} catch (Exception ex) {
									CirclePack.cpb.errMsg("Failed in reading some Schwarzians");
									state = PackState.INITIAL;
								}
							}
						}
						flags |= 020000000;
					} else if (mainTok.equals("C_COLORS:")) { // note: replaces CIRCLE_COLORS that used old indices
						state = PackState.C_COLORS;
						if (newPacking)
							for (int i = 1; i <= p.nodeCount; i++)
								p.setCircleColor(rON(i), ColorUtil.getFGColor());
						while (state == PackState.C_COLORS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int rd = (int) Math.floor(Double.parseDouble((String) loctok.nextToken()));
								int gn = (int) Math.floor(Double.parseDouble((String) loctok.nextToken()));
								int bl = (int) Math.floor(Double.parseDouble((String) loctok.nextToken()));
								p.setCircleColor(v, new Color(rd, gn, bl));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						flags |= 0400;
						col_c_flag = true;
					} else if (mainTok.equals("CIRCLE_COLORS:")) { // OBE: use 'Color' objects now (see C_COLORS)
						state = PackState.CIRCLE_COLORS;
						if (newPacking)
							for (int i = 1; i <= p.nodeCount; i++)
								p.setCircleColor(i, ColorUtil.getFGColor());
						while (state == PackState.CIRCLE_COLORS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int col = Integer.parseInt((String) loctok.nextToken());
								p.setCircleColor(v, ColorUtil.cloneMe(ColorUtil.coLor(col)));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						flags |= 0400;
						col_c_flag = true;
					} else if (mainTok.equals("VERTEX_MAP:")) {
						state = PackState.VERTEX_MAP;
						VertexMap vertMap = new VertexMap();
						while (state == PackState.VERTEX_MAP && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int V = Integer.parseInt((String) loctok.nextToken());
								vertMap.add(new EdgeSimple(v, V));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (vertMap != null && vertMap.size() > 0) {
							p.vertexMap = vertMap;
							flags |= 0100;
						}
					} else if (mainTok.equals("VERT_MARK:")) {
						state = PackState.VERT_MARK;
						vertMarks = new EdgeLink(p);
						while (state == PackState.VERT_MARK && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int m = Integer.parseInt((String) loctok.nextToken());
								vertMarks.add(new EdgeSimple(v, m));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
					}
					// for tile colors, must have 'tileData' already
					else if (mainTok.equals("TILE_COLORS:") && p.tileData != null) {
						state = PackState.TILE_COLORS;
						while (state == PackState.TILE_COLORS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							NodeLink vlist = null;
							try {
								String str = (String) loctok.nextToken();
								int tc = Integer.parseInt(str); // tilecount
								vlist = new NodeLink();
								for (int n = 0; n <= tc; n++)
									vlist.add(Integer.parseInt((String) loctok.nextToken()));
								int tileindx = p.tileData.whichTile(vlist);
								if (tileindx >= 0) {
									int rd = Integer.parseInt((String) loctok.nextToken());
									int gn = Integer.parseInt((String) loctok.nextToken());
									int bl = Integer.parseInt((String) loctok.nextToken());
									p.tileData.myTiles[tileindx].color = new Color(rd, gn, bl);
								}
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						} // end of while
					}
					// TODO: have to fix this for dcel structures
					else if (mainTok.equals("TRI_COLORS:")) { // OBE: uses color indices (see T_COLORS)
						state = PackState.TRI_COLORS;
						if (newPacking)
							for (int i = 1; i <=p.faceCount; i++)
								p.setFaceColor(i, ColorUtil.getFGColor());
						while (state == PackState.TRI_COLORS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int v1 = rON(Integer.parseInt((String) loctok.nextToken()));
								int v2 = rON(Integer.parseInt((String) loctok.nextToken()));
								int colindx = Integer.parseInt((String) loctok.nextToken());
								int[] faceFlower = p.getFaceFlower(v);
								for (int j = 0; j < faceFlower.length; j++) {
									int k = faceFlower[j];
									int[] verts = p.packDCEL.faces[k].getVerts();
									int ind;
									if (((ind = p.check_face(k, v, v1)) >= 0 && verts[(ind + 2) % 3] == v2)
											|| ((ind = p.check_face(k, v1, v)) >= 0 && verts[(ind + 2) % 3] == v2)) {
										p.setFaceColor(k, ColorUtil.cloneMe(ColorUtil.coLor(colindx)));
										break;
									}
								}
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						flags |= 0400;
						col_f_flag = true;
					} else if (mainTok.equals("VERT_LIST:")) {
						state = PackState.VERT_LIST;
						p.vlist = new NodeLink(p);
						String str = null;
						while (state == PackState.VERT_LIST && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							while (state == PackState.VERT_LIST && loctok.hasMoreTokens()) {
								try {
									str = (String) loctok.nextToken();
									int v = rON(Integer.parseInt(str));
									p.vlist.add(v);
								} catch (Exception ex) {
									state = PackState.INITIAL;
								}
							}
						}
						if (p.vlist.size() == 0)
							p.vlist = null;
						else
							flags |= 0200;
					} else if (mainTok.equals("GLOBAL_VERT_LIST:")) {
						state = PackState.GLOBAL_VERT_LIST;
						CPBase.Vlink = new NodeLink(p);
						while (state == PackState.GLOBAL_VERT_LIST && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								CPBase.Vlink.add(v);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (CPBase.Vlink.size() == 0)
							CPBase.Vlink = null;
						else
							flags |= 020000;
					}
					// TODO: fix this for dcel structure
					else if (mainTok.equals("FACE_TRIPLES:")) {
						state = PackState.FACE_TRIPLES;
						p.flist = new FaceLink(p);
						while (state == PackState.FACE_TRIPLES && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int v1 = rON(Integer.parseInt((String) loctok.nextToken()));
								int v2 = rON(Integer.parseInt((String) loctok.nextToken()));
								int k = p.what_face(v, v1, v2);
								if (k >= 1)
									p.flist.add(k);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (p.flist.size() == 0)
							p.flist = null;
						else
							flags |= 0200;
					} else if (mainTok.equals("GLOBAL_FACE_LIST:")) {
						state = PackState.GLOBAL_FACE_LIST;
						CPBase.Flink = new FaceLink(p);
						while (state == PackState.GLOBAL_FACE_LIST && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = Integer.parseInt(str);
								CPBase.Flink.add(v);
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (CPBase.Flink.size() == 0)
							CPBase.Flink = null;
						else
							flags |= 020000;
					} else if (mainTok.equals("EDGE_LIST:")) {
						state = PackState.EDGE_LIST;
						p.elist = new EdgeLink(p);
						while (state == PackState.EDGE_LIST && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int w = rON(Integer.parseInt((String) loctok.nextToken()));
								p.elist.add(new EdgeSimple(v, w));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (p.elist.size() == 0)
							p.elist = null;
						else
							flags |= 0200;
					} else if (mainTok.equals("GLOBAL_EDGE_LIST:")) {
						state = PackState.GLOBAL_EDGE_LIST;
						CPBase.Elink = new EdgeLink(p);
						while (state == PackState.GLOBAL_EDGE_LIST && 
								(line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int w = rON(Integer.parseInt((String) loctok.nextToken()));
								CPBase.Elink.add(new EdgeSimple(v, w));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (CPBase.Elink.size() == 0)
							CPBase.Elink = null;
						else
							flags |= 020000;
					} else if (mainTok.equals("DUAL_EDGE_LIST:")) {
						state = PackState.DUAL_EDGE_LIST;
						p.glist = new GraphLink(p);
						while (state == PackState.DUAL_EDGE_LIST && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = Integer.parseInt(str);
								int w = Integer.parseInt((String) loctok.nextToken());
								p.glist.add(new EdgeSimple(v, w));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (p.glist.size() == 0)
							p.glist = null;
						else
							flags |= 0200;
					} else if (mainTok.equals("GLOBAL_DUAL_EDGE_LIST:")) {
						state = PackState.GLOBAL_DUAL_EDGE_LIST;
						CPBase.Glink = new GraphLink(p);
						while (state == PackState.GLOBAL_DUAL_EDGE_LIST
								&& (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = Integer.parseInt(str);
								int w = Integer.parseInt((String) loctok.nextToken());
								CPBase.Glink.add(new EdgeSimple(v, w));
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (CPBase.Glink.size() == 0)
							CPBase.Glink = null;
						else
							flags |= 020000;
					} else if (mainTok.equals("POINTS:")) { // get N
						int N = p.nodeCount;
						try {
							N = Integer.parseInt((String) tok.nextToken());
						} catch (Exception ex) {
							N = p.nodeCount; // default
						}
						if (N >= 1 && N <= p.nodeCount) {
							// Note: read N xyz's into CONSECUTIVE vertices
							int v = 1;
							p.xyzpoint = new Point3D[p.nodeCount + 1];
							state = PackState.POINTS;
							while (state == PackState.POINTS && (line = StringUtil.ourNextLine(fp)) != null) {
								StringTokenizer loctok = new StringTokenizer(line);
								try {
									String str = (String) loctok.nextToken();
									double X = Double.parseDouble(str);
									double Y = Double.parseDouble((String) loctok.nextToken());

									// may have just 2 coords
									double Z;
									try {
										Z = Double.parseDouble((String) loctok.nextToken());
									} catch (Exception ex) {
										Z = 0.0;
									}

									p.xyzpoint[rON(v)] = new Point3D(X, Y, Z);
									v++;
								} catch (Exception ex) {
									state = PackState.INITIAL;
								}
							}
							if (v == 1)
								p.xyzpoint = null;
							else
								flags |= 02000;
						}
					} else if (mainTok.equals("INTEGERS:")) { // get list of integers
						int N = p.nodeCount;
						try {
							N = Integer.parseInt((String) tok.nextToken());
						} catch (Exception ex) {
							N = p.nodeCount; // likely value
						}
						p.utilIntegers = new Vector<Integer>(N);
						state = PackState.INTEGERS;
						int di = 0;
						while (di < N && state == PackState.INTEGERS && (line = StringUtil.ourNextLine(fp)) != null) {
							try {
								p.utilIntegers.add(Integer.parseInt(line));
							} catch (Exception ex) {
								di = N; // bomb out, leave what has been found
								state = PackState.INITIAL;
							}
						} // end of while

						if (p.utilIntegers.size() == 0)
							p.utilIntegers = null;
						else
							flags |= 01000000;
						state = PackState.INITIAL;
					} 
					else if (mainTok.equals("DOUBLES:")) { // get list of double values
						int N = p.nodeCount;
						try {
							N = Integer.parseInt((String) tok.nextToken());
						} catch (Exception ex) {
							N = p.nodeCount; // likely value
						}
						p.utilDoubles = new Vector<Double>(N);
						state = PackState.DOUBLES;
						int di = 0;
						while (di < N && state == PackState.DOUBLES && (line = StringUtil.ourNextLine(fp)) != null) {
							try {
								double val = Double.parseDouble(line);
								p.utilDoubles.add(Double.valueOf(val));
							} catch (Exception ex) {
								di = N; // bomb out, leave what has been found
								state = PackState.INITIAL;
							}
						} // end of while

						if (p.utilDoubles.size() == 0)
							p.utilDoubles = null;
						else
							flags |= 02000000;
						state = PackState.INITIAL;
					} else if (mainTok.equals("COMPLEXES:")) { // get utility list of complex numbers
						int N = p.nodeCount;
						try {
							N = Integer.parseInt((String) tok.nextToken());
						} catch (Exception ex) {
							N = p.nodeCount; // likely value
						}
						p.utilComplexes = new Vector<Complex>(N);
						state = PackState.COMPLEXES;
						int di = 0;
						while (di < N && state == PackState.COMPLEXES && (line = StringUtil.ourNextLine(fp)) != null) {
							try {
								StringTokenizer loctok = new StringTokenizer(line);
								x = Double.parseDouble(loctok.nextToken());
								y = Double.parseDouble(loctok.nextToken());
								p.utilComplexes.add(new Complex(x, y));
							} catch (Exception ex) {
								di = N; // bomb out, leave what has been found
								state = PackState.INITIAL;
							}
						} // end of while

						if (p.utilComplexes.size() == 0)
							p.utilComplexes = null;
						else
							flags |= 04000000;
						state = PackState.INITIAL;
					} else if (mainTok.equals("RADII_INTERACTIONS:") && 
							p.hes == 0 && !newPacking) {
						state = PackState.RADII_INTERACTIONS;

						/*
						 * This read actually changes eucl radii: entry "i j f" 
						 * means to multiply rad(j) by (rad(i)/rad(j))^f.
						 */
						double[] newrad = new double[p.nodeCount + 1];
						int count = 0;
						for (int i = 1; i <= p.nodeCount; i++) {
							int ij = rON(i);
							newrad[ij] = p.getRadius(ij);
						}
						while (state == PackState.RADII_INTERACTIONS && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);
							try {
								String str = (String) loctok.nextToken();
								int v = rON(Integer.parseInt(str));
								int w = rON(Integer.parseInt((String) loctok.nextToken()));
								double fac = Double.parseDouble((String) loctok.nextToken());
								newrad[v] *= Math.pow(p.getRadius(w) / p.getRadius(v), fac);
								count++;
							} catch (Exception ex) {
								state = PackState.INITIAL;
							}
						}
						if (count > 0)
							for (int i = 1; i <= p.nodeCount; i++) {
								int ij = rON(i);
								p.setRadius(ij, newrad[ij]);
							}
						flags |= 0400000; // 040000;
					}

					else if (mainTok.equals("BARY_DATA:") && !newPacking) {
						state = PackState.BARY_DATA;

						try {
							// store results in 'utilBary'
							p.utilBary = new Vector<BaryPtData>(0);
							while (state == PackState.BARY_DATA && (line = StringUtil.ourNextLine(fp)) != null) {
								StringTokenizer loctok = new StringTokenizer(line);

								// data form: 'v0 v1 v2 b0 b1 x [y]
								// b0 b1 are barycentric coords (third is computed)
								// string after b1 is to be read as a complex (possibly real)

								// get three vertices defining the face
								loctok = new StringTokenizer(line);
								int v0 = rON(Integer.parseInt((String) loctok.nextToken()));
								int v1 = rON(Integer.parseInt((String) loctok.nextToken()));
								int v2 = rON(Integer.parseInt((String) loctok.nextToken()));
								// get 2 doubles
								double b0 = Double.parseDouble((String) loctok.nextToken());
								double b1 = Double.parseDouble((String) loctok.nextToken());

								// is this a face of 'this'? If so, may correct the order
								int face = p.what_face(v0, v1, v2);
								if (face != 0) {

									// check that order is right
									int j0 = p.packDCEL.faces[face].getVerts()[0];
									if (j0 == v1) {
										int hold = v0;
										v0 = v1;
										v1 = v2;
										v2 = hold;
										b0 = b1;
										b1 = 1.0 - (b0 + b1);
									} else if (j0 == v2) {
										int hold = v0;
										v0 = v2;
										v2 = v1;
										v1 = hold;
										b0 = 1.0 - (b0 + b1);
										b1 = b0;
									}
								}

								// create the point
								BaryPtData bptd = new BaryPtData(v0, v1, v2, b0, b1);
								if (face != 0)
									bptd.utilint = face;

								p.utilBary.add(bptd);
							} // end of while
						} catch (Exception ex) {
							break; // got no data, so stop this loop
						}
						state = PackState.INITIAL;
					} // done with 'BARY_VECTOR'
						// TODO: fix for dcel structures
					else if (mainTok.equals("BARY_VECTOR:") && !newPacking) {
						state = PackState.BARY_VECTOR;
						int count = 0;

						// toss old vector of barylinks
						CPBase.gridLines = new Vector<BaryCoordLink>(1);
						while (state == PackState.BARY_VECTOR && (line = StringUtil.ourNextLine(fp)) != null) {
							StringTokenizer loctok = new StringTokenizer(line);

							// search for 'BARYLIST's
							String str = (String) loctok.nextToken();
							if (str.startsWith("BARYLIST:")) {

								// creating a new linked list
								BaryCoordLink bcl = new BaryCoordLink(p);
								int n = 0;
								while (state == PackState.BARY_VECTOR && (line = StringUtil.ourNextLine(fp)) != null) {
									try {

										// data form: 'v0 v1 v2 start0 start1 end0 end1'
										// start0/1 and end0/1 are barycentric coods (third is computed)

										// get three vertices defining the face
										loctok = new StringTokenizer(line);
										int v0 = rON(Integer.parseInt((String) loctok.nextToken()));
										int v1 = rON(Integer.parseInt((String) loctok.nextToken()));
										int v2 = rON(Integer.parseInt((String) loctok.nextToken()));
										int face = p.what_face(v0, v1, v2);
										if (face == 0) {
											state = PackState.INITIAL;
											break;
										}
										// get 4 doubles
										double s0 = Double.parseDouble((String) loctok.nextToken());
										double s1 = Double.parseDouble((String) loctok.nextToken());
										double e0 = Double.parseDouble((String) loctok.nextToken());
										double e1 = Double.parseDouble((String) loctok.nextToken());

										// check that order is right
										int j0 = p.packDCEL.faces[face].getVerts()[0];
										if (j0 == v1) {
											int hold = v0;
											v0 = v1;
											v1 = v2;
											v2 = hold;
											s0 = s1;
											s1 = 1.0 - (s0 + s1);
											e0 = e1;
											e1 = 1.0 - (e0 + e1);
										} else if (j0 == v2) {
											int hold = v0;
											v0 = v2;
											v2 = v1;
											v1 = hold;
											s0 = 1.0 - (s0 + s1);
											s1 = s0;
											e0 = 1.0 - (e0 + e1);
											e1 = e0;
										}
										BaryPacket nbp = new BaryPacket(p, face);
										nbp.start = new BaryPoint(s0, s1);
										nbp.end = new BaryPoint(e0, e1);
										bcl.add(nbp);
										n++;
									} catch (Exception ex) {
										break; // got no data, so stop this loop
									}
								} // end of while for 'BARYLIST'

								// if we got something, add to vector
								if (n > 0) {
									CPBase.gridLines.add(bcl);
									count++;
								}
							} else if (str.startsWith("END")) {
								state = PackState.INITIAL;
							}

							if (count > 0)
								flags |= 010000000; // show we got something

						} // end of while for 'BARY_VECTOR's

						state = PackState.INITIAL;
					} // done with 'BARY_VECTOR'

					else if (mainTok.equals("END")) {
						foundEnd = true;
						break;
					}
				}
				if (foundEnd)
					break;
			}
		} catch (Exception ex) {
			p.flashError("Read of " + filename + " has failed: " + ex.getMessage());
			return -1;
		}

		// if vertex marks were specified
		if (vertMarks != null && vertMarks.size() > 0) {
			Iterator<EdgeSimple> vm = vertMarks.iterator();
			while (vm.hasNext()) {
				EdgeSimple edge = vm.next();
				if (edge.v > 0 && edge.v <= p.nodeCount)
					p.setVertMark(rON(edge.v), rON(edge.w));
			}
		}

		// this was a CHECKCOUNT file, return
		if (!newPacking) {
			if (p.getDispOptions != null) {
				p.cpDrawing.dispOptions.usetext = true;
				p.cpDrawing.dispOptions.tailored = p.getDispOptions;
				if (p.packNum == CirclePack.cpb.getActivePackNum()) {
					PackControl.screenCtrlFrame.displayPanel.
						flagField.setText(p.cpDrawing.dispOptions.tailored);
					PackControl.screenCtrlFrame.displayPanel.setFlagBox(true);
				}
			}
			return flags;
		}

		// ============== update for new packings ============

		if ((flags & 0010) != 0010) { // need radii
			double rad = 0.025;
			if (p.hes < 0)
				rad = 1.0 - Math.exp(-1.0);
			for (int i = 1; i <= p.nodeCount; i++)
				p.setRadius(rON(i), rad);
		}

		if ((flags & 0020) != 0020) { // set centers
			for (int i = 1; i <= p.nodeCount; i++)
				p.setCenter(rON(i), new Complex(0.0, 0.0));
		}

		// make sure alpha/gamma are set
		if (newAlpha > 0) {
			p.setAlpha(newAlpha);
		} else
			p.chooseAlpha();
		if (newGamma > 0)
			p.setGamma(newGamma);
		else if (p.getGamma() <= 0)
			p.chooseGamma();

		p.activeNode = p.packDCEL.alpha.origin.vertIndx;

		if ((flags & 0010) != 0 && (flags & 0020) == 0) { // new radii, no centers
			try {
				p.packDCEL.layoutPacking();
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("'readpack': exception in drawing order");
			}
		}
		if ((flags & 0040) == 0 && !newPacking) { // no angle sums were read
			p.fillcurves();
		}

		if (!gotAims) { // need to set default aims
			p.set_aim_default();
		}

		if ((flags & 02000) == 0) { // outdated xyz data
			p.xyzpoint = null;
		}

		if (!col_c_flag) {
			for (int i = 1; i <= p.nodeCount; i++)
				p.setCircleColor(rON(i), ColorUtil.getFGColor());
		}

		if (!col_f_flag) {
			for (int i = 1; i <= p.faceCount; i++)
				p.setFaceColor(i, ColorUtil.getFGColor());
		}

		// TODO: set pack name and put it on label?

		if (p.cpDrawing != null) {
			p.cpDrawing.reset();
			if (p.getDispOptions != null) {
				p.cpDrawing.dispOptions.usetext = true;
				p.cpDrawing.dispOptions.tailored = p.getDispOptions;
				if (p.packNum == CirclePack.cpb.getActivePackNum()) {
					PackControl.screenCtrlFrame.displayPanel.
						flagField.setText(p.cpDrawing.dispOptions.tailored);
					PackControl.screenCtrlFrame.displayPanel.setFlagBox(true);
				}
			}
		}
		p.setGeometry(p.hes);
		p.set_plotFlags();
		return flags; // this.getFlower(1132);
	}


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
	  @param p PackData
	  @param act int
	  @param append boolean
	  @return int
	  */
	public static int writePack(BufferedWriter file,PackData p,int act,boolean append)
			throws IOException {
		PackDCEL pdc=p.packDCEL;
		int flag;

		if (file == null)
			return 0;
		if (append)
			act &= 07776; // append mode, can't have 0001 bit
		
		// tiling data ONLY? If it exists, write and return.
		if (act== 020000 && p.tileData!=null && p.tileData.tileCount>0) { // tiling data

			file.write("TILECOUNT: "+p.tileData.tileCount+"\n\n");
			
      	//   The data rows are 't  n  v_0 v_1 ... v_(n-1)' where
      	//   t=tile number (1 to tileCount); n=number of vertices. 

			// check for augmented: in this case, 3 additional 
			//      vertices between corners
			boolean augmntd=true;
			for (int j=1;(j<=p.tileData.tileCount && augmntd);j++) {
				if (p.tileData.myTiles[j].augVert==null ||
						p.tileData.myTiles[j].augVertCount<=0)
					augmntd=false;
			}
			if (augmntd)
				file.write("TILES: (augmented) "+p.tileData.tileCount+"\n");
			else
				file.write("TILES: "+p.tileData.tileCount+"\n");
			for (int j=1;j<=p.tileData.tileCount;j++) {
				Tile tile=p.tileData.myTiles[j];
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
			file.close();
			return p.tileData.tileCount;
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
			file.write("TILECOUNT: "+p.nodeCount+"\n\n");
			file.write("TILES: "+p.nodeCount+"\n\n");
			int tick=p.faceCount+1;
			for (int v=1;v<=p.nodeCount;v++) {
				int[] faceFlower=p.getFaceFlower(v);
				file.write("\n"+v+" "+faceFlower.length+" "+"   ");
				for (int j = 0; j < faceFlower.length; j++) 
					file.write(" "+faceFlower[j]);
				// convention for bdry half-tile: include new index for v itself
				if (pdc.isBdry(v)) 
					file.write(" "+tick++);
			}			
			file.write("\nEND\n");
			file.flush();
			file.close();
			return p.nodeCount;
		}

		// lead info
		if ((act & 0001) == 0001) { // new pack basic comb info
			file.write("NODECOUNT:  " + p.nodeCount + "\n");
		} 
		// one of "neutral" data types only
		else if (act == 01000000 || act == 04000000 ) {
			file.write("NEUTRAL: \n");
		}
		else {
			file.write("CHECKCOUNT: " + p.nodeCount + "\n"); // partial data
		}
		
		if ((act & 0002) == 0002 || (act & 0010) == 0010
				|| (act & 0020) == 0020) {
			// geometry (needed if radii or centers given)
			file.write("GEOMETRY: ");
			if (p.hes < 0)
				file.write("hyperbolic\n");
			else if (p.hes > 0)
				file.write("spherical\n");
			else
				file.write("euclidean\n");
		}
		if ((act & 0001) == 0001) {
			file.write("ALPHA/GAMMA:  " + p.packDCEL.alpha.origin.vertIndx +
					" " + " " + p.packDCEL.gamma.origin.vertIndx + "\n");
			if (p.fileName.length() > 0)
				file.write("PACKNAME: " + p.fileName + "\n");
			file.write("BOUQUET: ");
			for (int n = 1; n <= p.nodeCount; n++) {
				int[] gfl=p.getFlower(n);
				int cnt=gfl.length-1;
				file.write("\n" + n + " " + cnt + "  ");
				for (int i = 0; i < gfl.length; i++)
					file.write(" "+gfl[i]);
			}
			file.write("\n\n");
		}

		else if (act == 020000000) { // real schwarzians
			String hitstr=new String("SCHWARZIANS:\n");
			boolean hitflag=false;
			double schw;
			for (int v = 1; v <= pdc.vertCount; v++) {
				HalfLink spokes=pdc.vertices[v].getEdgeFlower();
				Iterator<HalfEdge> sis=spokes.iterator();
				while (sis.hasNext()) {
					HalfEdge he=sis.next();
					int kk=he.twin.origin.vertIndx;
					schw=he.getSchwarzian();
					if (schw!=1.0 && v < kk) {
						if (!hitflag) {
							hitflag=true;
							file.write(hitstr);
						}
						file.write("\n" + v + " " + kk + "  "
								+ String.format("%.10e",schw));
					}
				}
				file.write("\n");
			}

		}

		if ((act & 0004) == 0004) { // inv_dist? aims? (non-default only)
			boolean hitflag=false;
			if (p.haveInvDistances()) {
				double ang;
				for (int v = 1; v <= pdc.vertCount; v++) {
					HalfLink spokes=pdc.vertices[v].getSpokes(null);
					Iterator<HalfEdge> sis=spokes.iterator();
					while (sis.hasNext()) {
						HalfEdge he=sis.next();
						if ((ang=he.getInvDist())!=1.0) {
							if (!hitflag) {
								hitflag=true;
								file.write("INV_DISTANCES:\n");
							}
							hitflag=true;
							file.write("\n" + v + " " + he.twin.origin.vertIndx+
								"  "+String.format("%.6e", ang));
						}
					}
				}
				if (hitflag) {
					file.write("\n  (done)\n\n");
				}
			}
			
			// also, non-default aims
			hitflag=false;
			for (int v = 1; v <= p.nodeCount; v++) {
				double aim=pdc.vertices[v].aim;
				if ((pdc.isBdry(v) && aim >= 0.0)
						|| (!pdc.isBdry(v) && (aim < (2.0 * Math.PI - OKERR) || 
								aim > (2.0 * Math.PI + OKERR)))) {
					if (!hitflag) {
						hitflag=true;
						file.write("ANGLE_AIMS:\n");
					}
					file.write(" " + v + "  "
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
			for (int i = 1; i <= p.nodeCount; i++) {
				String fms = new String("%." + digits + "e");
				file.write(String.format(fms, p.getActualRadius(i)) + "  ");
				if ((i % 4) == 0)
					file.write("\n");
				else
					file.write(" ");
			}
			file.write("\n\n");
		}
		if ((act & 0020) == 0020) { // centers? (often easier to recompute)
			file.write("CENTERS:\n");
			for (int i = 1; i <= p.nodeCount; i++) {
				Complex ztr=pdc.vertices[i].center;
				file.write(String.format("%.10e", ztr.x) + " "
						+ String.format("%.10e", ztr.y) + "  ");
				if ((i % 2) == 0)
					file.write("\n");
			}
			file.write("\n\n");
		}
		if ((act & 020000) == 020000 && p.tileData!=null &&
				p.tileData.tileCount>0) { // tiling data

      	//   The data rows are 't  n   v_0 v_1 ... v_(n-1)' where
      	//   t=tile number (1 to tileCount); n=number of vertices.
			
			//   check for augmented: in this case, 3 additional vertices between corners
			boolean augmntd=true;
			for (int j=1;(j<=p.tileData.tileCount && augmntd);j++) {
				if (p.tileData.myTiles[j].augVert==null || 
						p.tileData.myTiles[j].augVertCount<=0)
					augmntd=false;
			}
			if (augmntd)
				file.write("TILES: (augmented) "+p.tileData.tileCount+"\n");
			else
				file.write("TILES: "+p.tileData.tileCount+"\n");
			for (int j=1;j<=p.tileData.tileCount;j++) {
				Tile tile=p.tileData.myTiles[j];
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
			for (int i = 1; i <= p.nodeCount; i++) {
				file.write(" " + String.format("%.10e",pdc.vertices[i].curv) + "\t");
				if ((i % 5) == 0)
					file.write("\n");
			}
			file.write("\n\n");
		}
		if ((act & 010000) == 010000) { // triangles (triples of verts)
			file.write("FACE_TRIPLES: \n");
			for (int i = 1; i <= p.faceCount; i++) {
				int[] fverts=p.getFaceVerts(i);
				file.write(" ");
				for (int j=0;j<fverts.length;j++)
					file.write(fverts[0] + "  ");
				file.write("\n");
			}
			file.write("(done)\n\n");
		}

		if ((act & 01000) == 01000) { // nonpositive plot_flags
			flag = 0;
			for (int i = 1; (i <= p.nodeCount && flag == 0); i++)
				if (p.getPlotFlag(i) <= 0)
					flag++;
			if (flag > 0) { // got some nondefault circle plot_flags
				file.write("CIRCLE_PLOT_FLAGS: \n");
				int j = 0;
				for (int i = 1; i <= p.nodeCount; i++)
					if (p.getPlotFlag(i) <= 0) {
						file.write(i + " \t" + p.getPlotFlag(i) + " ");
						j++;
						if ((j % 5) == 0)
							file.write("\n");
					}
				file.write("\n (done)\n\n");
			}
//			flag = 0;
//			for (int i = 1; (i <= faceCount && flag == 0); i++)
//				if (getFacePlotFlag(i) <= 0)
//					flag++;
//			if (flag > 0) { // got some nondefault face plot_flags
//				file.write("FACE_PLOT_FLAGS: \n");
//				int j = 0;
//				for (int i = 1; i <= faceCount; i++)
//					if (getFacePlotFlag(i) <= 0) {
//						int[] verts=packDCEL.faces[i].getVerts();
//						file.write(" " + verts[0] + " "
//								+ verts[1] + " " + verts[2]
//								+ "   " + getFacePlotFlag(i));
//						j++;
//						if ((j % 3) == 0)
//							file.write("\n");
//					}
//				file.write("\n (done)\n\n");
//			}
		}
		if ((act & 02000) == 02000 && p.xyzpoint != null) { // xyz data
			file.write("POINTS: " + p.nodeCount + "\n");
			for (int i = 1; i <= p.nodeCount; i++) {
				file.write(String.format("%.10e", p.xyzpoint[i].x) + " "
						+ String.format("%.10e", p.xyzpoint[i].y) + " "
						+ String.format("%.10e", p.xyzpoint[i].z) + "\n");
			}
			file.write("\n");
		}
		
		if ((act & 01000000) == 01000000 && p.utilIntegers!=null && 
				p.utilIntegers.size()>0) { // utility integer values
			file.write("INTEGERS: "+p.utilIntegers.size()+"\n");
			for (int i=0;i<p.utilIntegers.size();i++) {
				file.write(p.utilIntegers.get(i).toString()+"\n");
			}
			file.write("\n");
		}
			
		if ((act & 02000000) == 02000000 && p.utilDoubles!=null && 
				p.utilDoubles.size()>0) { // utility double values
			file.write("DOUBLES: "+p.utilDoubles.size()+"\n");
			for (int i=0;i<p.utilDoubles.size();i++) {
				file.write(String.format("%.12e", (double)p.utilDoubles.get(i))+"\n");
			}
			file.write("\n");
		}
			
		if ((act & 04000000) == 04000000 && p.utilComplexes!=null && 
				p.utilComplexes.size()>0) { // utility complex values
			file.write("COMPLEXES: "+p.utilComplexes.size()+"\n");
			for (int i=0;i<p.utilComplexes.size();i++) {
				file.write(String.format("%.12e", (double)(p.utilComplexes.get(i).x))+" "+
						String.format("%.12e", (double)(p.utilComplexes.get(i).y))+"\n");
			}
			file.write("\n");
		}

		if ((act & 04000) == 04000) { // edge-pairing Mobius
			NodeLink pairIndices=p.getPairIndices();
			if (pairIndices!=null) {
				Iterator<Integer> pis=pairIndices.iterator();
				while (pis.hasNext()) {
					int e=pis.next();
					file.write("EDGE_PAIRING MOBIUS: "+e+"\n\n");
					file.write(p.getSideMob(e).mob2String().toString()+"\n");
				}
			}
		}

		if ((act & 0400) == 0400) { // any non default colors?
			
			// check vertex colors
			int colorflag = 0;
			Color vcol;
			for (int i = 1; i <= p.nodeCount && colorflag == 0; i++) {
				vcol=p.getCircleColor(i);
				if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0)  
					colorflag++;
			}
			if (colorflag > 0) { // found some non-default circle colors
				file.write("C_COLORS:\n");
				for (int i = 1; i <= p.nodeCount; i++) { // one vertex per line
					vcol=p.getFaceColor(i);
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
			for (int i = 1; i <= p.faceCount && colorflag == 0; i++) {
				vcol=p.getFaceColor(i);
				if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0)  
					colorflag++;
			}
			if (colorflag > 0) { // found some non-default colors
				file.write("T_COLORS:\n"); // T_COLORS are for faces; superceded TRI_COLORS
				for (int i = 1; i <= p.faceCount; i++) { // one face per line
					vcol=p.getFaceColor(i);
					if (vcol.getRed()!=0 || vcol.getGreen()!=0 || 
							vcol.getBlue()!=0) { 
						int[] verts=p.getFaceVerts(i);
						file.write(" " + verts[0] + " "
								+ verts[1] + " " + verts[2]+"   "+
								vcol.getRed() + " "+
								vcol.getGreen()+" "+vcol.getBlue()+"\n");
					}
				}
				file.write("\n (done)\n\n");
			}

			// check tile colors
			if (p.tileData!=null) {
				colorflag = 0;
				for (int i=1; (i<=p.tileData.tileCount && colorflag==0); i++) {
					vcol=p.tileData.myTiles[i].color;
					if (vcol.getRed()!=0 || vcol.getGreen()!=0 || 
							vcol.getBlue()!=0)  
						colorflag++;
				}
				if (colorflag > 0) { // found some non-default colors
					file.write("TILE_COLORS:\n"); 
					for (int i = 1; i <= p.tileData.tileCount; i++) { // one face per line
						Tile tile=p.tileData.myTiles[i];
						vcol=tile.color;
						if (vcol.getRed()!=0 || vcol.getGreen()!=0 || vcol.getBlue()!=0) { 
							file.write(" " + tile.vertCount+ " ");
							for (int ik=0;ik<p.tileData.myTiles[i].vertCount;ik++)
								file.write(p.tileData.myTiles[i].vert[ik]+" ");
							file.write("  "+vcol.getRed() + " "+vcol.getGreen()+" "+vcol.getBlue()+"\n");
						}
					}
				}
				file.write("\n (done)\n\n");
			}
		} // done with default colors
		if ((act & 0200) == 0200) { // print non empgy lists?
									// (vlist/flist/elist)
			if (p.vlist != null && p.vlist.size() != 0) {
				file.write("VERT_LIST:\n");
				Iterator<Integer> vli = p.vlist.iterator();
				while (vli.hasNext()) {
					file.write(" " + (Integer) vli.next() + "\n");
				}
				file.write(" (done)\n\n");
			}
			if (p.flist != null && p.flist.size() > 0) {
				file.write("FACE_TRIPLES:\n");
				Iterator<Integer> fli = p.flist.iterator();
				while (fli.hasNext()) {
					int fl = (Integer) fli.next();
					int[]  verts=p.getFaceVerts(fl);
					file.write(" " + verts[0] + " "
							+ verts[1] + " " + verts[2]	+ "\n");
				}
				file.write(" (done)\n\n");
			}
			if (p.elist != null && p.elist.size() != 0) {
				file.write("EDGE_LIST:\n");
				Iterator<EdgeSimple> eli = p.elist.iterator();
				while (eli.hasNext()) {
					EdgeSimple el = (EdgeSimple) eli.next();
					file.write(" " + el.v + " " + el.w + "\n");
				}
				file.write(" (done)\n\n");
			}
		}
		if ((act & 0100) == 0100 && p.vertexMap != null) { // vertex_map
			file.write("VERTEX_MAP:\n");
			Iterator<EdgeSimple> eli = p.vertexMap.iterator();
			while (eli.hasNext()) {
				EdgeSimple el = (EdgeSimple) eli.next();
				file.write(el.v + "  " + el.w + "\n");
			}
			file.write("   (done)\n\n");
		}
		if ((act & 040000) == 040000) { // display flags
			file.write("DISP_FLAGS:\n");
			file.write("Disp "+p.cpDrawing.dispOptions.toString()+"\n");
		}		
		if (!append)
			file.write("END\n");
		file.flush();
		file.close();
		return 1;
	}
		
	
	/**
	 * Write *.dcel output; this format is tentative as of 4/2017.
	 * @param fp BufferedWriter
	 * @param pdcel PackDCEL
	 * @param dual boolean, if yes, write the dual dcel
	 * @return int, 0 on error
	 */
	public static int writeDCEL(BufferedWriter fp,PackDCEL pdcel,boolean dual) 
			throws IOException {
		
		if (fp==null)
			throw new IOException("BufferedWriter was not set");
		if (dual) {
			PackDCEL dualdcel=pdcel.createDual(false);
			return dualdcel.writeDCEL(fp);
		}
		return pdcel.writeDCEL(fp);
	}
		
	
	/**
	 * translation needed when reading "BOUQUET".
	 * 
	 * @param old_v int
	 * @return int
	 */
	public static int rON(int old_v) {
		if (readOldNew != null && readOldNew[old_v] > 0) {
			return readOldNew[old_v];
		}
		return old_v;
	}
	    
}
