package tiling;

import java.io.BufferedReader;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
import exceptions.InOutException;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Point3D;
import util.StringUtil;

/**
 * This class holds the structures for finite subdivision rules,
 * which describe how a finite collection of polygonal tile types 
 * are to be subdivided so each is a finite union of tiles from 
 * this same collection.
 * 
 * This code is used with 'ConformalTiling' extender, 'TileData',
 * and 'Tile' classes. The initial structure was adopted from Bill 
 * Floyd's software, built in conjunction with Jim Cannon and Walter 
 * Parry. In particular, we can read their *.r "rules" files (we also
 * augment them with some optional data or data to resolve ambiguities
 * in general situations --- as with self-pasting; see below).
 * 
 * The 'tileType's are numbers. In "rules" files they are contiguous
 * integers starting at 4 (since 0,1,2,3 were used for rectangle edges)
 * for labeling the rules. Currently the tile types are in one-to-one
 * correspondence with the rules. Traditionally, each rule applies to 
 * n-gons for some n, though more than one rule can apply for the same n. 
 * 
 * FUTURE: We will also (eventually) allow generic subdivision rules, 
 * rules which apply to n-gons for any n (e.g., the tiling version of
 * 'pentagonal' subdivision). We also wish to include some more general 
 * tiles: e.g. uni-gons, bi-gons, multiply connected (so edges or vertices 
 * may be identified), or with penetrating edges, etc.
 * 
 * For each 'Tile', the first entry 'vert[0]' in its vertex list
 * is treated as its "principal vertex" (the beginning of the 0th
 * edge the way CFP conceived of things).
 * 
 * OPTIONAL INFO: I've added four optional data sections which must
 * come at the end of the rules file:
 *   + Edge_marks:
 *   	{t} {i} {m} (tile type, edge index, and 'mark') 
 *   	
 *   + Tile_marks:
 *      {t} {n} {m} (tile type, index, and 'mark')
 *      ...
 *      (done)
 *   + Tile_flowers: (not yet fully developed, look in 'readRulesFile')
 *      
 *   + Euclidean_location_data 
 *   	Type_number_(the_first_one_should_be_4_since_the_ends_are_typed_0_1_2_3):
 *   	  t
 *   	Eucl_corner (standard position, first corner at 0, next at 1
 *        0.0 0.0
 *        1.0 0.0
 *        x3 y3
 *        ....
 *      Subtile_base_endpoints_(z.x_z.y_w.x_w.y):
 *        x0 y0  x1 y1
 *        ...
 *      
 *      Type_number ... etc.
 * 
 * @author kens, November 2013
 *
 */
public class SubdivisionRules {
	
	// name of this finite subdivision rule; generally from *.r filename
	public String FSDRname; 
		
	// put 'TileRule's in a vector
	public Vector<TileRule> tileRules;
	
	// use EdgeLink for pairs <tt,r>, tt>=4 is tile type, r>=0 is index
	//   in 'tileRules' vector of applicable rule.
	public EdgeLink type2Rule;
	
	// use EdgeLink for pairs <tt,n>, tt>=4 is tile type, n is number of
	//   edges (and vertices) of this tile type. (For generic rules,
	//   this will somehow be variable)
	public EdgeLink type2Size;
	
	// constructor
	public SubdivisionRules() {
		FSDRname=""; 
		tileRules=new Vector<TileRule>();
		type2Rule=new EdgeLink();
		type2Size=new EdgeLink();
	}
	
	/** 
	 * Utility routine to open a specified file
	 * @param dir File
	 * @param filename String
	 * @return BufferedReader, null on error (and 'fp' closed)
	 */
	public static BufferedReader getBufferedReader(File dir,String filename,boolean script_flag) {
		BufferedReader fp=null;
        try {
        	fp=CPFileManager.openReadFP(dir,filename,script_flag);
        } catch (Exception ex) {
        	try {
        		fp.close();
        	} catch (Exception exx) {
        		fp=null;
        	}
        }
        return fp;
	}
	
	/**
	 * This routine is for reading Cannon/Floyd/Parry subdivision rules 
	 * *.r files. The 'getRulesTD' routine is then needed to create the
	 * minimal 'TileData' associated with the subdivision for each tile
	 * type. Note that the 'mode' 1, 2, 3, determines the construction,
	 * with 3 (default) being the most complicated. 

	 * PROBLEM: We interpret the rule's data as counterclockwise, so the
	 * results are mirror images of those of CFP. (may have fixed this??)
	 *  
	 * @param fp BufferedReader, opened by calling routine, closed here
	 * @param filename String, just to use as name of these rules
	 * @return SubdivisionRules or throw an exception
	 */
	public static SubdivisionRules readRulesFile(BufferedReader fp,String filename) {
        String line="";
        SubdivisionRules subRules=null;
        boolean gotCount=false;
        int ruleCount=0;
    	Vector<int[]> optPastings=null;
    	
    	if (fp==null) 
    		throw new DataException("BufferedReader was not open");
        
        try {
        	// must get opening line first
        	while((line=StringUtil.ourNextLine(fp,true))!=null && !gotCount) { 
        		if (line.startsWith("Number_of_tile-types")) {
        			line=StringUtil.ourNextLine(fp,true);
        			ruleCount=Integer.parseInt(line);
    				gotCount=true;
        			subRules=new SubdivisionRules();
        			
        			// dump next line "Size_..."
        			line=StringUtil.ourNextLine(fp,true);
        			
        			// Note: the 'types' start at 4 and are sequential.
        			// Read the numbers of edges of the child tiles:
        			line=StringUtil.ourNextLine(fp,true);
    				String []numbs=line.split("\\s+");
        			for (int j=0;j<ruleCount;j++) { // type starts at 4
        				int n=Integer.parseInt(numbs[j]);
        				subRules.tileRules.add(new TileRule(4+j,n));
        				// careful, have to allow j=0 entry
        				subRules.type2Rule.add(new EdgeSimple(4+j,j)); 
        				subRules.type2Size.add(new EdgeSimple(4+j,n));
        			}
        		}
        	}
        	if (!gotCount)
        		throw new DataException("didn't get tileCount");
        	
        	// ======== now iterate through the subtile information ====
        	int tiletick=0;
        	while(tiletick<ruleCount && line.startsWith("Subdivision-tiling:")) {
        		int myType=-1;
        		TileRule myRule;

       			// flush next line, "Type_number" and get the number itself
       			line=StringUtil.ourNextLine(fp,true);
       			myType=Integer.parseInt(StringUtil.ourNextLine(fp,true));
       			myRule=subRules.tileRules.get(subRules.type2Rule.findW(myType));
        			
       			// flush next line, "Numer_of_tiles_into_which ..."
       			line=StringUtil.ourNextLine(fp,true);

       			// get the number
        		myRule.childCount=Integer.parseInt(StringUtil.ourNextLine(fp,true));
        		myRule.childType=new int[myRule.childCount+1];
        		myRule.childMark=new int[myRule.childCount+1];
        			
        		// flush next line, "Type_of_each_of_these..."
        		line=StringUtil.ourNextLine(fp,true);
        			
        		// keep reading until we get 'childCount' types:
        		int tick=0;
        		while (tick<myRule.childCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
        			String []numbs=line.split("\\s+");
        			for (int j=0;j<numbs.length;j++) {
        				myRule.childType[++tick]=Integer.parseInt(numbs[j]);
        			}
        		}
        		if (tick<myRule.childCount) 
        			throw new DataException("didn't get child types");

        		// temp storage vector of vectors of tile neighbors (into 'childFlower' later)
        		Vector<Vector<int[]>> tmpFlower=new Vector<Vector<int[]>>(myRule.childCount);
        		for (int j=0;j<myRule.childCount;j++) {
        			tmpFlower.add(new Vector<int[]>(0));
        		}

        		// flush next line, "Tile-ids_--_Adjacent_tiles_.."
        		line=StringUtil.ourNextLine(fp,true);

        		// get the neighbors across edges for each subtile; 
        		// Careful:
        		//   * each list must be on one line
        		//   * children indexed from 1, not 0.
        		// In some cases (non-contiguous edges shared) the data may
        		//   come in pairs, see below
        		tick=0;
        		int mxsize=0;
        		while (tick<myRule.childCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
        			String []numbs=line.split("\\s+");
        			int ci=Integer.parseInt(numbs[0]); 
        			if (ci!=tick)
        				throw new DataException("missing/scrambled list for 'Tile-ids'");
        			int readlength=subRules.type2Size.findW(myRule.childType[ci+1]);
        			
        			// usual situation: flower of tile indices (possibly -1) across edges.
        			if ((numbs.length-1)==readlength) {
        				int click=0;
        				for (int j=1;j<=readlength;j++) {
        					int []ei=new int[2];
        					ei[0]=Integer.parseInt(numbs[j])+1;  // we are adding 1 to Floyd's indexing
        					ei[1]=-2; // default to -2 when these entries are not provided
        					tmpFlower.get(ci).add(ei);
        					click++;
        				}
        				tick++;
        				mxsize=(click>mxsize) ? click:mxsize;
        			}
        			// sometimes (e.g., tiles sharing non-contiguous edges) the
        			//   numbers come in pairs (t,e), where 't' is as above, the
        			//   nghb tile index, and 'e' is the index within 't' of this
        			//   shared edge
        			else if ((numbs.length-1)==2*readlength){
        				int click=0;
        				for (int j=0;j<readlength;j++) {
        					int []ei=new int[2];
        					ei[0]=Integer.parseInt(numbs[2*j])+1;  // we are adding 1 to Floyd's indexing
        					ei[1]=Integer.parseInt(numbs[2*j+1]);  // get the index of this edge in nghb
        					tmpFlower.get(ci).add(ei);
        					click++;
        				}
        				tick++;
        				mxsize=(click>mxsize) ? click:mxsize;
        			}
        			else 
        				throw new DataException("error in tile flower number");
        			
        		} // end of while
        		if (tick<myRule.childCount) 
        			throw new DataException("didn't get child types");
        		

        		// store 'childFlower' array of arrays of (nghb, nghb-edge)
        		// (Since tiles indexed from 1, first entry is null.)
        		myRule.childFlower=new int[myRule.childCount+1][mxsize][2];
        		for (int j=0;j<myRule.childCount;j++) {
        			Vector<int[]> vint=tmpFlower.get(j);
        			for (int k=0;k<vint.size();k++) {
        				myRule.childFlower[j+1][k][0]=vint.get(k)[0];
        				myRule.childFlower[j+1][k][1]=vint.get(k)[1]; // usually set to -2
        			}
        		}
        		
        		// flush next line, "Corresponding_boundary-tiling:"
        		line=StringUtil.ourNextLine(fp,true);

        		// Then flush "Boundary_of_type_number.." 
        		line=StringUtil.ourNextLine(fp,true);

        		// now the type itself is given and must be 'myType'
        		line=StringUtil.ourNextLine(fp,true);
        		if (Integer.parseInt(line)!=myType)
        			throw new DataException("tile type didn't match 'myType'");

        		// flush "Number_of_edges_before_subdivision:" 
        		line=StringUtil.ourNextLine(fp,true);
        		// The number itself must be 'myRule.edgeCount'
        		line=StringUtil.ourNextLine(fp,true);
        		if (Integer.parseInt(line)!=myRule.edgeCount)
        			throw new DataException("edgecount didn't match 'myRule.edgeCount'");
        			
        		// flush "Number_of_edges_into_which_each_of_these.."
        		line=StringUtil.ourNextLine(fp,true);
        			
        		// keep reading until we get 'subEdgeCount' for all edges
        		tick=0;
        		while (tick<myRule.edgeCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
        			String []numbs=line.split("\\s+");
        			for (int j=0;j<numbs.length;j++) {
        				myRule.edgeRule[tick++]=new EdgeRule(Integer.parseInt(numbs[j]));
        			}
        		}
        		if (tick<myRule.edgeCount) 
        		throw new DataException("missing edges for type "+myRule.targetType);
        			
        		// flush "Original_edgeid_-_numbers_in_pairs ... (tileno_edgeno)"
        		line=StringUtil.ourNextLine(fp,true);
        			
        		int etick=0;
        		while (etick<myRule.edgeCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
        			String []numbs=line.split("\\s+");

        			// first get index of this edge, allocate space
        			int ei=Integer.parseInt(numbs[0]); 
        			int seCount=myRule.edgeRule[ei].subEdgeCount;
        			myRule.edgeRule[ei].tileedge=new int[seCount][2];
        			
        			// Get n pairs: tile number of tile along this edge, and index in 
        			//   its bdry where the subedge in this edge starts.
        			// CAUTION: these are listed "clockwise" along the edge, not
        			//   "counterclockwise".
        			Vector<Integer> pairs=new Vector<Integer>(numbs.length);
        			int setick=0;
        			for (int j=1;j<numbs.length;j++) {
        				pairs.add(Integer.parseInt(numbs[j]));
        				setick++;
        			}
        			while (setick<seCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
        				numbs=line.split("\\s");
            			for (int j=0;j<numbs.length;j=j++) {
            				pairs.add(Integer.parseInt(numbs[j]));
            				setick++;
            			}
        			}
        			if (setick!=2*seCount) 
        				throw new DataException("got too few/many subedge pairs");
        			
        			// now move the pairs into the 'tileedge' array
        			Iterator<Integer> pl=pairs.iterator();
        			setick=0;
        			while (pl.hasNext()) {
        				myRule.edgeRule[ei].tileedge[setick][0]=pl.next()+1;
        				myRule.edgeRule[ei].tileedge[setick][1]=pl.next();
        				setick++;
        			}
        				
        			etick++;
       			} // end of while through tileno_edgeno lines
    			if (etick<myRule.edgeCount)
    				throw new DataException("didn't get all 'tileno_edgeno' info. ");
        			
        		tiletick++;
        		if (tiletick<ruleCount)
        			// get next line
            		line=StringUtil.ourNextLine(fp,true);
        			
        	} // end of while through "Subdivision-tiling:" sections

        	if (tiletick<ruleCount)
        		throw new DataException("didn't get all tiles");
      
        	// ======================= optional data =============================
        	// This goes beyond the CFP data, consolidated at the end of the file
        	
			while ((line = StringUtil.ourNextLine(fp,true)) != null) {

				// get 'Edge_marks' data (new to *.r files, 1/2014) if it
				// exists; must come after other data
				if (line.startsWith("Edge_marks")) {
					try {
						while ((line = StringUtil.ourNextLine(fp,true)) != null) {
							String[] numbs = line.split("\\s+");
							if (numbs.length != 3) {
								if (!line.contains("done"))
									CirclePack.cpb
											.errMsg("format of 'Edge_marks' data is '{t} {i} {m}', tile type, edge index, mark");
								throw new DataException();
							}
							TileRule tr = subRules.tileRules
									.get(subRules.type2Rule.findW(Integer.parseInt(
											numbs[0])));
							tr.edgeRule[Integer.parseInt(numbs[1])].mark = Integer.parseInt(
									numbs[2]);
						}
					} catch (Exception ex) {
					} // catch, e.g., "(done)" at the end of this segment
				}

				// get 'Tile_marks' data (new to *.r files, 7/2014) if it
				// exists; must come after other data
				// form: {t} {n} {m}, where:
				//       t=tile type (starting from 4)
				//       n=child index (starting from 0)
				//       m=specified 'mark' (an integer)
				else if (line.startsWith("Tile_marks")) {
					try {
						while ((line = StringUtil.ourNextLine(fp,true)) != null) {
							String[] numbs = line.split("\\s+");
							if (numbs.length != 3) {
								if (!line.contains("done"))
									CirclePack.cpb
											.errMsg("format of 'Tile_marks' data is '{t} {n} {m}', tile type, subtile number, mark");
								throw new DataException();
							}
							TileRule tr = subRules.tileRules
									.get(subRules.type2Rule.findW(Integer.parseInt(
											numbs[0])));
							tr.childMark[Integer.parseInt(numbs[1]) + 1] = Integer.parseInt(
									numbs[2]);
						}
					} catch (Exception ex) {
					} // catch, e.g., "(done)" at the end of this segment
				}

				// 'tile flower' information: This is not yet fully developed.
				// In some situations
				// the basic CFP data leaves ambiguities about which edges of
				// tiles are identified:
				// e.g. with multiple, non-contiguous edges, reentrant
				// self-pasted edges, other
				// self-pastings, etc.
				// The user can optionally provide lines 'tt c i nc j'.
				// * tt is tile type
				// * c is child index of subtile within tile's rules
				// * i is the edge number (indexed from 0) of this tile
				// * nc is child index of subtile across this edge
				// * j is the edge index of c w.r.t. tile nc
				// These will override the entries that are automatically
				// generated when
				// reading a tiling
				// Every entry must have a companion entry interchanging c and
				// nc.

				else if (line.startsWith("Tile_flowers")) {
					try {
						optPastings = new Vector<int[]>(0);
						while ((line = StringUtil.ourNextLine(fp,true)) != null) {
							String[] numbs = line.split("\\s+");
							if (numbs.length != 5) {
								if (!line.contains("done"))
									CirclePack.cpb
											.errMsg("optioinal 'Tile_flowers' data has wrong format");
								throw new DataException();
							}
							int[] data = new int[5];
							data[0] = Integer.parseInt(numbs[0]); // tile type (>=4)
							data[1] = Integer.parseInt(numbs[1]) + 1; // child,
																	// indexed
																	// from 1
							data[2] = Integer.parseInt(numbs[2]); // edge of child,
																// indexed from
																// 0
							data[3] = Integer.parseInt(numbs[3]) + 1; // nghb child,
																	// indexed
																	// from 1
							data[4] = Integer.parseInt(numbs[4]); // nghb child edge,
																// indexed from
																// 0
							TileRule myRule = subRules.tileRules
									.get(subRules.type2Rule.findW(data[0]));
							int ctype = myRule.childType[data[1]];
							int nctype = myRule.childType[data[3]];
							TileRule cRule = subRules.tileRules
									.get(subRules.type2Rule.findW(ctype));
							TileRule ncRule = subRules.tileRules
									.get(subRules.type2Rule.findW(nctype));
							if (data[2] < 0 || data[2] >= cRule.edgeCount
									|| data[4] < 0
									|| data[4] >= ncRule.edgeCount) {
								CirclePack.cpb
										.errMsg("'Tile_flowers': format mistake in i or j");
								throw new DataException();
							}
							optPastings.add(data);
						}
					} catch (Exception ex) {
					} // catch, e.g., "(done)" at the end of this segment
				}
				
				else if (line.startsWith("Euclidean_location")) {
			     	tiletick=0;
		       		line=StringUtil.ourNextLine(fp,true);
		       		try{
		        	while(tiletick<ruleCount && line!=null &&
		        			line.startsWith("Type_number")) {
		        		int myType=-1;
		        		TileRule myRule;
	        			Point3D normal=null;
	        			
		        		// check line "Type_number" for key work "_normal" to
		        		//   indicate this type of face may not lie in the plane
		        		if (line.contains("normal")) {
		        			String []parts=line.split("\\s+");
		        			try {
		        				normal=new Point3D(Double.valueOf(parts[1]),Double.valueOf(parts[2]),Double.valueOf(parts[3]));
		        			} catch (Exception exc) {
		        				CirclePack.cpb.errMsg("failed to read normal");
		        			}		        				
		        		}
		        		
		        		// flush next line, "Type_number" and get the number itself
			       		line=StringUtil.ourNextLine(fp,true);
			       		myType=Integer.parseInt(line);
			       		myRule=subRules.tileRules.get(subRules.type2Rule.findW(myType));
			       		
			       		// unit normal? default to perpendicular
			       		if (normal!=null) 
			       			myRule.stdNormal=normal.normalize(); // unit normal
			       		else
			       			myRule.stdNormal=null;
			       		
			       		// get the standard corners:
			       		//  always in the xy-plane, first/second should be 0.0/1.0
			       		myRule.stdCorners=new Complex[myRule.edgeCount];
			        			
			       		// flush next line, "Eucl_corner ..."
			       		line=StringUtil.ourNextLine(fp,true);
			       		int cornertick=0;
			       		while (cornertick<myRule.edgeCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
							String[] numbs = line.split("\\s+");
			       			try {
								myRule.stdCorners[cornertick]=
										new Complex(Double.parseDouble(numbs[0]),Double.parseDouble(numbs[1])); 
			       			} catch (Exception ex) {
			       				throw new DataException("problem with 'Eucl_corner...' data");
			       			}
			       			cornertick++;
			       		}
			       		
			       		// flush next line, "Subtile_base ..." and record sets of 
			       		if ((line=StringUtil.ourNextLine(fp,true))!=null && !line.startsWith("Subtile_base"))
			       			throw new DataException("missing 'Subtile_base ..'");
			       		myRule.tileBase=new Complex[myRule.childCount+1][];
			       		int subtiletick=1;
			       		while (subtiletick<=myRule.childCount && (line=StringUtil.ourNextLine(fp,true))!=null) {
							String[] numbs = line.split("\\s+");
							Complex []tmpBases=new Complex[2];
			       			try {
			       				tmpBases[0]=new Complex(Double.parseDouble(numbs[0]),Double.parseDouble(numbs[1]));
			       				tmpBases[1]=new Complex(Double.parseDouble(numbs[2]),Double.parseDouble(numbs[3]));
			       			} catch (Exception ex) {
			       				throw new DataException("problem with 'Subtile_base...' data");
			       			}
			       			myRule.tileBase[subtiletick]=tmpBases;
			       			subtiletick++;
			       		}
			       		line=StringUtil.ourNextLine(fp,true);
			       		tiletick++;
		        	}
		       		} catch (DataException dex) {
		       			break;
		       		}
				}
			} // end of while catching optional stuff
        	
        } catch (NumberFormatException iox) {
        	try {
        		fp.close();
        	} catch (Exception ex) {}
        	throw new InOutException("readSubRules: line = "+line+" :"+iox.getMessage());
        } catch (DataException dex) {
        	try {
        		fp.close();
        	} catch (Exception ex) {}
        	throw new DataException("Reading subdivision rules has failed: "+dex.getMessage());
        }
        
        // close the reader
    	try {
    		fp.close();
    	} catch (Exception ex) {}

        
        // complete the 'childFlower' info for each rule
        for (int r=0;r<ruleCount;r++) {
        	TileRule trule=subRules.tileRules.get(r);
        	for (int t=1;t<=trule.childCount;t++) {
        		int [][]myflower=trule.childFlower[t];
        		int []hts=new int[myflower.length];
        		for (int j=0;j<myflower.length;j++) {
        			if (hts[j]==0) {
        				int ngt=myflower[j][0]; 
        				if (ngt>0) {
        					int [][]nghbflower=trule.childFlower[ngt];
        					Vector<EdgeSimple> tvec=Tile.tile2tileMatch(myflower,t,nghbflower,ngt);
        					Iterator<EdgeSimple> tv=tvec.iterator();
        					while (tv.hasNext()) {
        						EdgeSimple cc=tv.next();
        						myflower[cc.v][1]=cc.w; // -3 if we failured in 'tile2tileMatch'
        						hts[cc.v]=1;
        					}
        				}
        				else hts[j]=1;
        			}
        		}
        	}
        }
        
        // override with 'Tile_flower' data if any was provided.
        if (optPastings!=null && optPastings.size()>0) {
        	Iterator<int[]> oP=optPastings.iterator();
        	while (oP.hasNext()) {
        		int []data=oP.next();
        		TileRule trule=subRules.tileRules.get(subRules.type2Rule.findW(data[0]));
        		trule.childFlower[data[1]][data[2]][0]=data[3]; // nghb index
        		trule.childFlower[data[1]][data[2]][1]=data[4]; // nghb's edge index
        	}
        }
        
		subRules.FSDRname=new String(filename);
        return subRules;
        
	}
	
	/**
	 * Build the minimal 'TileData' structure for a given tile type
	 * its associated "subdivision rule", a la Cannon/Floyd/Parry 
	 * 'rules' files, and the current 'mode'. The rule specifies the 
	 * subtile types, their neighbor relations, edge breakup patterns,
	 * and builds lists of vertices (local indices). 
	 * 
	 * CAUTION: we assume edge matching is set via 'childFlowers'
	 * in 'Tile.tile2tileMatch', but this is not robust for
	 * non-standard situations --- self-pasting, multiple edges,
	 * non-simply connected, etc. For complex situations, however,
	 * the rules file lines "Original_edgeid ..." can provide
	 * the additional information on matching.
	 * 
	 * We must create the actual tiles and set consistent vertices. 
	 * The vertices of the original parent tile are set to 
	 * 1, 2, ..., n. Other vertices depend on current 'mode'
	 * 
	 * TODO: However, if the parent tile has some self-pasting
	 * specified in its 'tileFlower', then these will be modified
	 * before returning, if the 'mode' permits.
	 *  
	 * We build vertices via their full face flowers.
	 * So after encountering an unprocessed vertex (index < 0) we 
	 * process it fully before proceeding.
	 * 
	 * @param tRule TileRule
	 * @param typeSize int[], contains the number of sides for each tile type.
	 * @param md int, mode 1, 2, or 3 (simplest to most complicated)
	 * @return TileData or null on error
	 */
	public static TileData getRulesTD(TileRule tRule, EdgeLink type2Size,int md) {

		// initiate TileData and its tiles, start with 'vert's set to -1.
		TileData tileData = new TileData(tRule.childCount,md);
		for (int j = 1; j <= tRule.childCount; j++) {
			int sz = type2Size.findW(tRule.childType[j]);
			tileData.myTiles[j] = new Tile(sz);
			tileData.myTiles[j].tileIndex = j;
			tileData.myTiles[j].tileType = tRule.childType[j];
			tileData.myTiles[j].mark = tRule.childMark[j];
			tileData.myTiles[j].tileFlower = new int[sz][2];
			for (int k = 0; k < sz; k++) {
				tileData.myTiles[j].vert[k] = -1; // set negative to start
				tileData.myTiles[j].tileFlower[k][0] = tRule.childFlower[j][k][0];
				tileData.myTiles[j].tileFlower[k][1] = tRule.childFlower[j][k][1];
			}
		}

		// last vertex index used
		int lastv = 0;
		int[] doneTiles = new int[tileData.tileCount + 1];

		// keep two lists of tile indices in processing
		NodeLink curr = new NodeLink();
		NodeLink next = new NodeLink();
		next.add(1);
		while (next.size() > 0) {
			curr = next;
			next = new NodeLink();

			while (curr.size() > 0) {

				// next tile to handle if not done
				int base_tile = curr.remove(0);
				if (base_tile > 0 && doneTiles[base_tile] <= 0) {

					Tile tile = tileData.myTiles[base_tile];

					int safety = 10 * tile.vertCount;
					while (safety > 0 && doneTiles[base_tile] <= 0) {
						safety--;

						// check if we're done with all its vertices
						boolean done = true;
						for (int j = 0; (j < tile.vertCount && done); j++)
							if (tile.vert[j] < 0)
								done = false;

						if (done) {
							doneTiles[base_tile] = 1;
							break;
						}

						// process vertices that aren't finished
						for (int j = 0; j < tile.vertCount; j++) {
							int base_v = tile.vert[j];

							if (base_v < 0) {
								base_v=tile.vert[j]=++lastv;

								// ======= go ccw through faces first

								// we always have current situation and next situation
								// use einfo = (nghbtile, nghbindex) to move ccw around 'base_v'
								int curr_tile = base_tile;
								Tile cTile = tileData.myTiles[curr_tile];
								int curr_edge = (j - 1 + cTile.vertCount)% cTile.vertCount; // upstream edge
								int[] einfo = cTile.tileFlower[curr_edge];
								int next_tile = einfo[0];
								int next_edge = einfo[1];

								// touch this tile for future processing
								if (next_tile > 0 && next.containsV(next_tile) < 0)
									next.add(next_tile);

								// proceed until reaching bdry edge or returning to base_tile across edge j
								while (next_tile > 0 && (next_tile!=base_tile || next_edge!=j )) {

									Tile nTile = tileData.myTiles[next_tile];

									// check vert at base_v on next_tile
									int nv = nTile.vert[next_edge];
									if (nv > 0 && nv != base_v)
										throw new DataException("error: doesn't match base_v");
									if (nv < 0)
										nv = nTile.vert[next_edge] = base_v;

									// done? base_tile, edge downstream from base_v?
									// Note: may be okay to reenter base_tile on some other edge
									if (next_tile == base_tile && next_edge == j) {
										next_tile = 0;
										break;
									}

									// else reset curr/next.
									curr_tile = next_tile;
									cTile = tileData.myTiles[curr_tile];
									curr_edge = (next_edge - 1 + cTile.vertCount)%cTile.vertCount;
									einfo = cTile.tileFlower[curr_edge];
									next_tile = einfo[0];
									next_edge = einfo[1];

									// touch this tile for future processing
									if (next_tile > 0 && next.containsV(next_tile) < 0)
										next.add(next_tile);

								} // done with while

								// =========== now go cw through faces.
								// Note: we have not wrapped all the way around, done

								if (next_tile != base_tile || next_edge != j) {
									curr_tile = base_tile;
									cTile = tileData.myTiles[curr_tile];
									curr_edge = j;
									einfo = cTile.tileFlower[curr_edge];
									next_tile = einfo[0];
									next_edge = einfo[1];

									// touch this tile for future processing
									if (next_tile > 0 && next.containsV(next_tile) < 0)
										next.add(next_tile);

									while (next_tile > 0) {

										// the next tile
										Tile nTile = tileData.myTiles[next_tile];

										// check vert at base_v on next_tile
										int nv = nTile.vert[(next_edge + 1)%nTile.vertCount];
										if (nv > 0 && nv != base_v)
											throw new DataException("error: nv "+ nv+ " doesn't match base_v "+ base_v);
										if (nv < 0)
											nv = nTile.vert[(next_edge + 1)%nTile.vertCount] = base_v;

										// ccw neighbor v on current tile
										int ccw = cTile.vert[(curr_edge + 1)%cTile.vertCount];
										// cw neighbor of v on next_tile
										int cw = nTile.vert[next_edge];
										if (cw > 0 && cw != ccw)
											throw new DataException("error: ccw " + ccw+ " and " + cw+ " don't match");

										// because we did ccw direction first, should never get back
										// to base_tile on edge upstream from base_v. Note, however, 
										// that we re-enter base_tile on another edge

										// reset curr/next.
										curr_tile = next_tile;
										cTile = tileData.myTiles[curr_tile];
										curr_edge = (next_edge + 1)%cTile.vertCount;
										einfo = cTile.tileFlower[curr_edge];
										next_tile = einfo[0];
										next_edge = einfo[1];

										// touch this tile for future processing
										if (next_tile > 0 && next.containsV(next_tile) < 0)
											next.add(next_tile);

									} // done with while
								} // done with cw tiles, should be done with base_v
							}
						} // done with for on vertices of base_tile
						doneTiles[base_tile]=1;
					} // end of while on base_tile
					if (safety == 0) {
						throw new DataException("safetied out of loop on base_tile");
					}

				}
			} // end of while on 'curr'
		} // end of while on 'next'

		// reset all vertices so 1, 2, ..., n become 'vert's of original tile.
		
		VertexMap permute=new VertexMap();
		
		// for each edge, find the location of its initial corner:
		//    Get this from tile/edge info for last entry
		for (int e=1;e<=tRule.edgeCount;e++) {
			EdgeRule erule=tRule.edgeRule[e-1];
			int []info=erule.tileedge[erule.subEdgeCount-1];
			Tile vTile=tileData.myTiles[info[0]];
			int iv=vTile.vert[info[1]];
			if (iv!=e) { // apply new a permutation
				permute.add(new EdgeSimple(iv,e));
				int ev=permute.findV(e); 
				
				// compose with previous swap?
				if (ev!=iv && ev>0) {
					permute.removeOrdered(new EdgeSimple(ev,e));
					permute.add(new EdgeSimple(ev,iv));
				}
				
				else 
					permute.add(new EdgeSimple(e,iv));
			}
		}
		
		int []oldnew=new int[lastv+1];
		Iterator<EdgeSimple> pt=permute.iterator();
		while(pt.hasNext()) {
			EdgeSimple edge=pt.next();
			oldnew[edge.v]=edge.w;
		}
		
		// swap out the old vertex numbers
		for (int t = 1; t <= tileData.tileCount; t++) {
			Tile tile = tileData.myTiles[t];
			for (int j = 0; j < tile.vertCount; j++)
				if (oldnew[tile.vert[j]]!=0)
					tile.vert[j] = oldnew[tile.vert[j]];
		}

		boolean debug = false;
		// debug=true;
		if (debug) {
			for (int t = 1; t <= tileData.tileCount; t++) {
				Tile tile = tileData.myTiles[t];
				StringBuilder tfb = new StringBuilder("Tile flowers for tile "
						+ t + ": ");
				StringBuilder vb = new StringBuilder("Verts for tile " + t
						+ ": ");
				for (int j = 0; j < tile.vertCount; j++) {
					tfb.append(" " + tile.tileFlower[j][0]);
					vb.append(" " + tile.vert[j]);
				}
				System.out.println(tfb.toString());
				System.out.println(vb.toString());
			}
		}

		return tileData;
	}

	/**
	 * Return the first tile type having n sides.
	 * @param n int, number of sides
	 * @return int, tile type, -1 on error or failure
	 */
	public int getPossibleType(int n) {
		if (n==0 || type2Size==null || type2Size.size()==0)
			return -1;
		return type2Size.findV(n);
	}
	
	/**
	 * This manipulates a rules file and saves it into given directory/name.
	 * It then reads the new file in the usual way to continue. Note that
	 * optional data (like euclidean corners, etc.) is not copied into the
	 * new rules file. The manipulations are very limited for now and
	 * can be done only one at a time 
	 * 
	 *  * given "-r s t", rotate subtile s (indexed from 0, as 
	 *    in Floyd's rules file) within the tile of type t by one edge 
	 *    counterclockwise. 
	 *  * given "-d t j i", we divide tile type t into two tiles, one
	 *    replaces type t and has the same number of edges and vertex j
	 *    replaced by j' (order of vertices preserved), while the other 
	 *    is a quad with corners (j,j+1,j',j-1) with the i_th one being
	 *    the designated corner.
	 *    
	 * Unfortunately, we can do minimal consistency at this time. The
	 * result is saved with the given name in the given directory (defaulting
	 * to CPFileManager.ScriptDirectory. 
	 * 
	 * If we successfully write the new file, we the call 'readRulesFile'
	 * on it and continue.
	 * @param fp BufferedReader, opened in calling routine, closed here
	 * @param dir File, directory for saving
	 * @param filename String, simply for naming these rules
	 * @param cmd String, specify one pair (t s)
	 * @return SubdivisionRules or throw an exception
	 */
	public SubdivisionRules manipulateRules(BufferedReader fp,String cmd,String newname) {
		return null;
	}
	
}
