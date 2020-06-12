package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.SetBuilderParser;
import komplex.EdgePair;
import komplex.EdgeSimple;
import komplex.KData;
import komplex.RedEdge;
import packQuality.QualMeasures;
import packing.PackData;
import panels.PathManager;
import tiling.Tile;
import util.MathUtil;
import util.PathDistance;
import util.PathInterpolator;
import util.SelectSpec;
import util.SphView;
import util.StringUtil;

/**
 * Linked list for vertices associated (generally) with a 
 * particular circle packing.
 * @author kens
 *
 */
public class NodeLink extends LinkedList<Integer> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public NodeLink(PackData p,String datastr) {
		super();
		packData=p;
		if (datastr!=null) addNodeLinks(datastr);
	}
	
	public NodeLink(PackData p,int n) {
		super();
		packData=p;
		if (n>0 && (packData==null || n<=p.nodeCount)) add(n);
	}
	
	public NodeLink(PackData p,Vector<String> items) {
		super();
		packData=p;
		if (items==null || items.size()==0) { // default to 'a' (all vertices)
			items=new Vector<String>(1);
			items.add("a");
		}
		addNodeLinks(items);
	}
	
	/**
	 * Not associated with any PackData
	 * @param datastr
	 */
	public NodeLink(String datastr) {
		this(null,datastr);
	}
	
	/**
	 * empty list, no packing
	 */
	public NodeLink() {
		super();
		packData=null;
	}
	
	/**
	 * Initiate empty list
	 * @param p
	 */
	public NodeLink(PackData p) {
		this(p,(String)null);
	}
	
	public boolean add(int v) {
		if ((packData!=null && v>0 && v<=packData.nodeCount) || packData==null)
			return super.add((Integer)v);
		return false;
	}
	
	public boolean add(Integer v) {
		if ((packData!=null && v.intValue()>0 && v.intValue()<=packData.nodeCount) || packData==null)
			return super.add(v);
		return false;
	}
	
	/**
	 * Add links to this list (if it is associated with PackData). Note
	 * that argument should not be empty since 'a" would have been
	 * added as default.
	 * @param datastr String
	 * @return int, count
	 */	
	public int addNodeLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addNodeLinks(items);
	}
	
	/**
	 * Add links to this list (if it is associated with PackData). Note
	 * that argument should not be empty since "a" would have been
	 * added as default.
	 * @param items Vector<String>
	 * @return int, count
	 */	
	public int addNodeLinks(Vector<String> items) {
		int count=0;
		KData []kdata;
		int nodecount;
	
		if (packData==null) return -1;
		kdata=packData.kData;
		nodecount=packData.nodeCount;
		
		Iterator<String> its=items.iterator();
		
		while (its!=null && its.hasNext()) {

			/* =============== here's the work ================== */
			
			String str=(String)its.next();
			// it's easy to put in '-' flags by mistake
			if (str.startsWith("-")) { 
				str=str.substring(1);
			}
			

			its.remove(); // shuck the entry (though still in 'str')

			// self call if str is a variable
			if (str.startsWith("_")) {
				count+=addNodeLinks(CPBase.varControl.getValue(str));
			}
			
			// check for '?list' first
			else if (str.substring(1).startsWith("list")) {
				int v;
				NodeLink vlink=null;
				EdgeLink elink=null;
				boolean ck=false;
				
				// vlist or Vlist
				if ((str.startsWith("v") && (vlink=packData.vlist)!=null
						&& vlink.size()>0) ||
						(str.startsWith("V") && (vlink=CPBase.Vlink)!=null
								&& CPBase.Vlink.size()>0)) {
					if (str.startsWith("V")) // v legal for packData?
						ck=true;
					// check for brackets first
					String brst=StringUtil.brackets(str);
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate list
							vlink.add(vlink.getFirst());
						}
						if (brst.startsWith("r") 
								|| brst.startsWith("n")) { // use up first
							v=(Integer)vlink.remove(0);
							if (ck && v>packData.nodeCount) {}
							else { 
								add(v);
								count++;
							}
						}
						if (brst.startsWith("l")) { // last
							v=(Integer)vlink.getLast();
							if (ck && v>packData.nodeCount) {}
							else { 
								add(v);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<vlink.size()) {
									v=vlink.get(n);
									if (ck && v>packData.nodeCount) {}
									else { 
										add(v);
										count++;
									} 
								}
							} catch (NumberFormatException nfe) {}
						}
					}
					// else just adjoin the lists
					else { 
						if (!ck) {
							int n=size();
							addAll(n,vlink);
							count +=vlink.size();
						}
						else {
							Iterator<Integer> vlst=vlink.iterator();
							while (vlst.hasNext()) {
								v=(Integer)vlst.next();
								if (v<=packData.nodeCount) {
									add(v);
									count++;
								}
							}
						}
					}
				}
				
				// elist or Elist
				else if ((str.startsWith("e") && (elink=packData.elist)!=null
						&& elink.size()>0) ||
						(str.startsWith("E") && (elink=CPBase.Elink)!=null
								&& CPBase.Elink.size()>0)) {
					int lastV=0;
					if (this.size()>0)
						lastV=this.getLast();
					EdgeSimple edge=null;
					if (str.startsWith("E")) // v legal for packData?
						ck=true;
					// check for brackets first
					String brst=StringUtil.brackets(str);
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate list
							elink.add(elink.getFirst());
						}
						if (brst.startsWith("r") 
								|| brst.startsWith("n")) { // use up first
							edge=(EdgeSimple)elink.remove(0);
							if (ck) {
								if (edge.v<=packData.nodeCount) {
									add(edge.v);
									count++;
								}
								if (edge.w<=packData.nodeCount) {
									add(edge.w);
									count++;
								}
							}									
							else { 
								add(edge.v);
								add(edge.w);
								count +=2;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<elink.size()) {
									edge=(EdgeSimple)elink.get(n);
									if (ck) {
										if (edge.v<=packData.nodeCount) {
											add(edge.v);
											count++;
										}
										if (edge.w<=packData.nodeCount) {
											add(edge.w);
											count++;
										}
									}									
									else { 
										add(edge.v);
										add(edge.w);
										count +=2;
									}
								}
							} catch (NumberFormatException nfe) {}
						}
					}
					// else just adjoin vertices in the edge list; don't
					//    repeat v2 if (v1,v2) followed by (v2, v3)
					else { 
						Iterator<EdgeSimple> elst=elink.iterator();
						while (elst.hasNext()) {
							edge=(EdgeSimple)elst.next();
							if (ck) {
								if (edge.v<=packData.nodeCount && edge.v!=lastV) {
									lastV=edge.v;
									add(lastV);
									count++;
								}
								if (edge.w<=packData.nodeCount && edge.w!=lastV) {
									lastV=edge.w;
									add(lastV);
									count++;
								}
							}									
							else { 
								if (edge.v!=lastV) {
									lastV=edge.v;
									add(lastV);
									count++;
								}
								if (edge.w!=lastV) {
									lastV=edge.w;
									add(lastV);
									count++;
								}
							}
						}
					}
				} // end of handling elist/Elist
			}
			
			// For 'random', 2 steps: get vert list, then make selection
			else if (str.equals("r")) {
				NodeLink nlk=null;
				if (items==null || items.size()==0)
					nlk=new NodeLink(packData,"a");
				else nlk=new NodeLink(packData,items);
				int v=randVert(nlk);
				if (v>=1) {
					add(v);
					count++;
				}
			}
				 		
			/******************************************************
			 * Now parse remaining options based on first character;
	 		 * default case, see if it's a number. */

			else {
			switch(str.charAt(0)) {
			// all; check for parens
			case 'a':
			{
				int first=1;
				int last=packData.nodeCount;
				String []pair_str=StringUtil.parens_parse(str); // get two strings
				if (pair_str!=null) { // must have 2 strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))!=0) first=a;
					if ((b=NodeLink.grab_one_vert(packData,pair_str[1]))!=0) last=b;
				}
				for (int i=first;i<=last;i++) {
					add(i);
					count++;
				}
				break;
			}
			
			// bdry; check for parens
			case 'b':
			{
				int next;
				int first=1;
				int last=packData.nodeCount;
				boolean bad=false;
				String []pair_str=StringUtil.parens_parse(str); // get two strings
				if (pair_str!=null) { // must have 2 strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))!=0) first=a;
					if ((b=NodeLink.grab_one_vert(packData,pair_str[1]))!=0) last=b;
					// check first/last: on bdry? on same component?
					if (kdata[first].bdryFlag==0 || kdata[last].bdryFlag==0) bad=true;
					else { // on same component?
						next=kdata[first].flower[0];
						int hit=0;
						while (next!=first) {
							if (next==last) hit++;
							next=kdata[next].flower[0];
						}
						if (first==last || hit!=0) { // yes, go ahead
							add(first);
							count++;
							next=kdata[first].flower[0];
							while (next!=last) {
								add(next);
								count++;
								next=kdata[next].flower[0];
							}
							add(last);
							count++;
						}
						else bad=true;
					}
				}
				if (pair_str==null || bad) { // whole boundary; note, 'starts' indices go from 1
					for (int i=1;i<=packData.bdryCompCount;i++) {
						int strt=packData.bdryStarts[i];
						add(strt);
						count++;
						next=kdata[strt].flower[0];
						while (next!=strt) {
							add(next);
							count++;
							next=kdata[next].flower[0];
						}
					}
				}
				break;
			}
			// interior; check for parens
			case 'i':
			{
				int first=1;
				int last=packData.nodeCount;
				String []pair_str=StringUtil.parens_parse(str); // get two strings
				if (pair_str!=null) { // must have 2 strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))!=0) first=a;
					if ((b=NodeLink.grab_one_vert(packData,pair_str[1]))!=0) last=b;
				}
				for (int j=first;j<=last;j++) 
					if (packData.kData[j].bdryFlag==0) {
						add(j);
						count++;
					}
				break;
			}
			case 'h': // hex_walk: v w n (e.g. for Berger's vector)
			{
				NodeLink nodelist=null;
				int v,w,n;
				try {
					String recons=new String(its.next()+" "+its.next()+" "+its.next());
					nodelist=new NodeLink(packData,recons);
					Iterator<Integer> vlist=nodelist.iterator();
					v=(Integer)vlist.next();
					w=(Integer)vlist.next();
					n=(Integer)vlist.next();
				} catch (Exception ex) {
					throw new ParserException("failed to get  'v w n' ");
				}
				int ans=packData.hexCell(v,w,n,this);
				if (ans<0) {
					int len=nodelist.size();
					CirclePack.cpb.msg("hex_walk did not complete; length is "+len);
					return len;
				}
				if (ans>0) {
					CirclePack.cpb.msg(
							"hex_walk completed, edgelength "+n+", first vert "+v+
							", last "+(Integer)this.getLast()+"."); 
					if (packData.hes<=0) {
						Complex vec=packData.rData[(Integer)this.getLast()].center.
							minus(packData.rData[(Integer)this.getFirst()].center);
						// print the "Berger's vector: displacement from first
						//    center to last center
						CirclePack.cpb.msg("Berger's vector is "+
								String.format("%.6e",vec.x)+" "+
								String.format("%.6e",vec.y)+"i");
					}
				}
				return ans;
			}
			case 'e': // vertices along hex-extended or extrapolated edges
			{
				int v=0,w=0,indx=-1;
				if (str.length()>1 && str.charAt(1)=='h') { // hex extrapolated
					// need just first edge to get started
					EdgeSimple edge=null;
					try {
						StringBuilder strb=new StringBuilder((String)items.get(0));
						if (items.size()>1) strb.append((String)items.get(1));
						its=null; // eat rest of 'items'
						edge=EdgeLink.grab_one_edge(packData,strb.toString());
					} catch (Exception ex) 
						{break;}
					if (edge==null) break;
					v=edge.v;
					w=edge.w;
					// v, w must be interior and hex and form an edge 
					if (packData.kData[v].bdryFlag!=0 || packData.kData[v].num!=6
							|| packData.kData[w].bdryFlag!=0 || packData.kData[w].num!=6
							|| (indx=packData.hex_extend(v,w,1))<0) break; // no hex edges to w
					EdgeLink hex_loop=packData.hex_extrapolate(v,indx,v,1025);
					if (hex_loop==null || hex_loop.size()==0) break;
					// add successive vertices to the list 
					add(v);
					count++;
					Iterator<EdgeSimple> elist=hex_loop.iterator();
					while (elist.hasNext()) {
						edge=(EdgeSimple)elist.next();
						add(edge.w);
						count++;
					}
					break;
				}
				else {
					EdgeLink edgelist=new EdgeLink(packData);
					edgelist.addEdgeLinks(items,true); // get hex-extended edge list
					if (edgelist.size()==0) break;
					// add the beginning vertex
					EdgeSimple edge=(EdgeSimple)edgelist.get(0);
					add(edge.v);
					count++;
					// add successive vertices
					Iterator<EdgeSimple> elist=edgelist.iterator();
					while (elist.hasNext()) {
						edge=(EdgeSimple)elist.next();
						add(edge.w);
						count++;
					}
				}
				break;
			}
			case 'm': // marked (or not-marked)
			{
				// check for -mc or -mq<p> or -mcq<p>
				boolean notmarked=false;
				if (str.contains("c")) notmarked=true;
				PackData qackData=packData;
				try {	// use those marked in another packing? pack[q]?
					int qnum;
					if (str.contains("q") && (qnum=Integer.parseInt(str.substring(str.length()-2)))>=0
							&& qnum<3 && PackControl.pack[qnum].packData.status) {
						qackData=PackControl.pack[qnum].packData;
					}
				} catch(Exception ex) {}
				for (int v=1;v<=nodecount;v++)
					if (v<=qackData.nodeCount 
							&& ((notmarked && qackData.kData[v].mark==0) 
									|| (!notmarked && qackData.kData[v].mark!=0))) {
						add(v);
						count++;
					}
				break;
			}
			case 'g': // same side of global 'ClosedPath' as 'alpha'?
			{
				if (CPBase.ClosedPath==null) break;
				boolean alpha_side=PathManager.path_wrap(packData.rData[packData.alpha].center);
				for (int v=1;v<=nodecount;v++) {
					if (PathManager.path_wrap(packData.rData[v].center)==alpha_side) {
						add(v);
						count++;
					}
				}
				break;
			}
			case 'I': // vertices 'Incident' to something:
				// Iv {v..}, Ie {e..}, If {f..}, It {t..}, Ig {x}
			{
				if (str.length()<=1) break;
				int []hits=new int[packData.nodeCount+1];
				switch(str.charAt(1)) {
				case 'f':
				{
				    FaceLink facelist=new FaceLink(packData,items);
					its=null; // eat rest of items
					if (facelist==null || facelist.size()==0) break;
				    Iterator<Integer> flist=facelist.iterator();
				    int f,vert;
				    while (flist.hasNext()) {
					f=(Integer)flist.next();
					for (int j=0;j<3;j++) {
					    vert=packData.faces[f].vert[j];
					    if (hits[vert]==0) {
						add(vert);
						count++;
						hits[vert]=1;
					    }
					}
				    }
				    break; // done with 'If'
				}
				case 'e':
				{
				    EdgeLink edgelist=new EdgeLink(packData,items);
					its=null; // eat rest of items
					if (edgelist==null || edgelist.size()==0) break;
				    Iterator<EdgeSimple> elist=edgelist.iterator();
				    EdgeSimple edge=null;
				    while (elist.hasNext()) {
					edge=(EdgeSimple)elist.next();
					if (hits[edge.v]==0) {
					    add(edge.v);
					    count++;
					    hits[edge.v]=1;
					}
					if (hits[edge.w]==0) {
					    add(edge.w);
					    count++;
					    hits[edge.w]=1;
					}
				    }
				    break; // done with 'Ie'
				}
				case 'c': // fall through, same as 'v' 
				case 'v':
				{
				    NodeLink vertlist=new NodeLink(packData,items);
					its=null; // eat rest of items
					if (vertlist==null || vertlist.size()==0) break;
				    Iterator<Integer> vlist=vertlist.iterator();
				    int v,vert;
				    while (vlist.hasNext()) {
					v=(Integer)vlist.next();
					int num=packData.kData[v].num;
					for (int j=0;j<(num+packData.kData[v].bdryFlag);j++) {
					    vert=packData.kData[v].flower[j];
					    if (hits[vert]==0) {
						add(vert);
						count++;
						hits[vert]=1;
					    }
					}
				    }
				    break; // done with 'Iv' and 'Ic'
				}
				case 't': // incident to tiles in list (i.e., corner verts of tiles)
				{
					TileLink tilelist=new TileLink(packData.tileData,items);
					its=null; // eat rest of items
					if (tilelist==null || tilelist.size()==0) break;
					Iterator<Integer> tlist=tilelist.iterator();
					int t,v;
					while (tlist.hasNext()) {
						t=(Integer)tlist.next();
						Tile tile=packData.tileData.myTiles[t];
						for (int j=0;j<tile.vertCount;j++) {
							v=tile.vert[j];
							if (hits[v]==0) {
								add(v);
								hits[v]=1;
								count++;
							}
						}
					} // end of while
					break; // done with 'It'
				}
				case 'g': // to path
				{
					// we need to use 'ClosedPath'
					if (CPBase.ClosedPath==null)
						return 0;
					
					NodeLink vertlist=null;
					
					// default to all vertices, threshold .01
					double thresh2=.01; // default to distance .01
					if (items==null || items.size()==0) { // nothing given, 
				    	vertlist=new NodeLink(packData,"a");
					}
					else {
						String leadstr=items.remove(0);
						// first item should be double (must have decimal point)
						try {
							thresh2=Double.parseDouble(leadstr);
						} catch(Exception ex) {
							throw new DataException("usage: -Ig {x} {v..}, "+
									"double 'x' (with decimal) must be first");
						}
						
						// get vertices
						vertlist=new NodeLink(packData,items);
						if (vertlist==null || vertlist.size()==0) // default to all
							vertlist=new NodeLink(packData,"a");
					}
				    
				    // create 'PathDistance' class
				    PathDistance pathdist=new PathDistance(CPBase.ClosedPath,thresh2);
					
					Iterator<Integer> vlst=vertlist.iterator();
					while (vlst.hasNext()) {
						int v=vlst.next();
						if (pathdist.distance(packData.rData[v].center)) {
							add(v);
							count++;
						}
					}
					
					break; // done with 'Ig'
				}

				} // end of local switch
				break;
			} // end of 'I' parsing
			case 'R': // red circles, outer edge of "sides" of red chain; 
			      // absorb rest of 'items'
			{
				RedEdge rtrace,hold;
				rtrace=hold=packData.firstRedEdge;
				if (hold==null) break; // no/defective redchain
			
				// 'Ra', want closed list of circles on outer edge of full redchain
				if (str.length()>1 && str.charAt(1)=='a') {
					// get first vertex (which should be hit again at the end)
					add(rtrace.sharedPrev());
					
					// now the rest
					boolean keepon=true;
					while (rtrace!=hold || keepon) {
						keepon=false;
						add(packData.faces[rtrace.face].vert[rtrace.vIndex]);
						// if this is blue face, have to add next vertex as well
						if (rtrace.next.face==rtrace.prev.face) {
							add(packData.faces[rtrace.face].vert[(rtrace.vIndex+1)%3]);
							rtrace=rtrace.nextRed;
							if (rtrace==hold) { // shouldn't happen, but reset to kick out of while
								rtrace=rtrace.prevRed;
							}
						}
						count++;
						rtrace=rtrace.nextRed;
					} // end of while
					break;
				}
				
				// otherwise, circles on outer edge of given edge segments
				//   absorb rest of 'items'
				  int numSides=-1;
				  String itstr=null;
				  if (packData.getSidePairs()==null || (numSides=packData.getSidePairs().size())==0) {
					  while (its.hasNext()) itstr=(String)its.next(); // use up
					  break;
				  }
				  boolean []tag=new boolean[numSides];
				  for (int i=0;i<numSides;i++) tag[i]=false;
				  if (!its.hasNext()) { // default to 'all'
					  for (int i=0;i<numSides;i++) tag[i]=true;
				  }
				  else do {
					  itstr=(String)its.next();
					  if (itstr.startsWith("a"))
						  for (int i=0;i<numSides;i++) tag[i]=true;
					  else {
						  try {
							  int n=Integer.parseInt(itstr);
							  if (n>=0 && n<numSides) tag[n]=true;
						  } catch (NumberFormatException nfx) {}
					  }
				  } while (its.hasNext());

				  // now to traverse the 'RedEdge's in chosen segments
				  Iterator<EdgePair> sp=packData.getSidePairs().iterator();
				  EdgePair ep=null;
				  RedEdge rlst=null;
				  int tick=0;
				  while (sp.hasNext()) {
					  ep=(EdgePair)sp.next();
					  if (tag[tick++]) { // yes, do this one
						  rlst=ep.startEdge;
						  add(rlst.vert(rlst.startIndex));
						  count++;
						  if (ep.startEdge!=ep.endEdge) {
							  do {
								  add(rlst.vert((rlst.startIndex+1)%3));
								  rlst=rlst.nextRed;
							  } while (rlst!=ep.endEdge);
						  }
						  add(rlst.vert((rlst.startIndex+1)%3));
						  count++;
					  }
				  }
				  break;
			} // end of 'R'
			case 'M': // vertex having max index
			{
				add(nodecount);
				count++;
				break;
			}
			case 'n': // nan? (not-a-number check on radius/center
			{
				if (str.startsWith("nan")) {
					for (int v=1;v<=nodecount;v++) {
						if (Double.isNaN(packData.rData[v].rad)
								|| Double.isNaN(packData.rData[v].center.x)
								|| Double.isNaN(packData.rData[v].center.y)
								|| (packData.hes>0 && packData.rData[v].rad-Math.PI<PackData.TOLER)) {
							add(v);
							count++;
						}
					}
				}
				
				else if (str.length()>1 && (str.charAt(1)=='+' || str.charAt(1)=='-')) {
					boolean nxt=true;
					if (str.charAt(1)=='-')
						nxt=false;
					NodeLink vs=new NodeLink(packData,items);
					Iterator<Integer> vsi=vs.iterator();
					while (vsi.hasNext()) {
						int v=vsi.next();
						if (packData.kData[v].bdryFlag!=0) {
							int w=packData.kData[v].flower[0];
							if (!nxt)
								w=packData.kData[v].flower[packData.kData[v].num];
							add(w);
							count++;
						}                        
					} // end of while
				}

				else { // form reaching here should be 'j {v..}', index j>=0
					int jdex=-1;
					try {
						jdex=Integer.parseInt(items.remove(0));
					} catch(Exception ex) {}				
					if (jdex>=0) {
						NodeLink vs=new NodeLink(packData,items);
						if (vs.size()>0) {
							Iterator<Integer> vsi=vs.iterator();
							while (vsi.hasNext()) {
								int v=vsi.next();
								if (jdex<=packData.kData[v].num) {
									add(packData.kData[v].flower[jdex]);
									count++;
								}
							}
						}
					}
				}
				
				break;
			}
			case 'o': // antipodal to given vertices (returns just first encountered)
			{
				NodeLink seedlist=new NodeLink(packData,items);
				int antip_vert=packData.gen_mark(seedlist, -1, false);
				if (antip_vert>0) {
					add(antip_vert);
					count++;
				}
				break;
			}
			case 'q': // quality: v's with worst visual error than given number
			{
				double thresh=.01; // default threshold 
				try{
					thresh=StringUtil.getOneDouble(items.remove(0));
				} catch(Exception ex) {
					thresh=.01;
				}
				NodeLink seedlist=new NodeLink(packData,items);
				Iterator<Integer> sit=seedlist.iterator();
				while (sit.hasNext()) {
					int v=sit.next();
					double verr=QualMeasures.vert_ErrMax(packData, v);
					if (verr>thresh) {
						add(v);
						count++;
					}
				}
				break;
			}
//			case 'w': // inside edge-path. TODO: is this worthwhile????
			case 'A': // alpha vertex
			{
				add(packData.alpha);
				count++;
				break;
			}
			case 'B': // boundary starts (i.e., one vertex on each 
					// boundary component. 
			{
				if (packData.bdryCompCount>0) {
					for (int i=1;i<=packData.bdryCompCount;i++) { // indexing starts at 1
						add(packData.bdryStarts[i]);
						count++;
					}
				}
				break;
			}
			case 'p': // plotFlag set (or 'pc', not set);
			{
				boolean notset=false;
				if (str.substring(1).contains("c")) notset=true;
				for (int v=1;v<=nodecount;v++) {
					int pf=packData.kData[v].plotFlag;
					if ((notset && pf==0) || (!notset && pf!=0)) {
						add(v);
						count++;
					}
				}
				break;
			}
			case 'v': // 'active' node
			{
				int an=packData.activeNode;
				if (an>0 && an<=nodecount) {
					add(an);
					count++;
				}
				break;
			}
			case 'V': // companions v in 'vertexMap' pairs <v,w> for given w's
			{
				if (packData.vertexMap!=null) {
					
					// get list of v's
					NodeLink vlst=null;
					if (items.size()==0) 
						vlst = new NodeLink(packData,"a");
					else vlst = new NodeLink(packData,items);
					
					Iterator<Integer> nits=vlst.iterator();
					int w;
					while (nits.hasNext()) {
						w=(Integer)nits.next();
						try {
							NodeLink transw=
								Translators.vert_translate(packData.vertexMap,w,false);
							Iterator<Integer> vit=transw.iterator();
							while (vit.hasNext()) {
								add(vit.next());
							} 
						} catch (Exception ex) {}
					} // end of while
				}
				break;
			}
			case 'W': // companions w in 'vertexMap' pairs <v,w> for given v's
			{
				if (packData.vertexMap!=null) {
					
					// get list of v's
					NodeLink vlst=null;
					if (items.size()==0) 
						vlst = new NodeLink(packData,"a");
					else vlst = new NodeLink(packData,items);
					
					Iterator<Integer> nits=vlst.iterator();
					int v=0;
					while (nits.hasNext()) {
						v=(Integer)nits.next();
						try {
							NodeLink transv=
								Translators.vert_translate(packData.vertexMap,v,true);
							Iterator<Integer> wit=transv.iterator();
							while (wit.hasNext()) {
								add(wit.next());
							} 
						} catch (Exception ex) {}
					} // end of while
				}
				break;
			}
			case 'c': // closest to given complex number from list
				// format -c x y {v..}
			{
				Complex xypt=null;
				try {
					double x=Double.parseDouble((String)its.next());
					its.remove();
					double y=Double.parseDouble((String)its.next());
					its.remove();
					xypt=new Complex(x,y);
				} catch(Exception ex) {
					throw new ParserException();
				}
				NodeLink vlst=null;
				if (items.size()==0) 
					vlst = new NodeLink(packData,"a");
				else vlst = new NodeLink(packData,items);
				
				Iterator<Integer> nits=vlst.iterator();
				double mindist=10000000.0;
				int v,min_v=1;
				Complex z;
				double dist=1.0;
				while (nits.hasNext()) {
					v=(Integer)nits.next();
					z=packData.rData[v].center;
					if (packData.hes<0) {
					    dist=HyperbolicMath.h_dist(xypt,z);
					    if (dist<0) // improper or one/both on/near unit circle
					    	dist=2*mindist; // take it out of the running
					}
					else if (packData.hes>0)
					    dist=SphericalMath.s_dist(xypt,z);
					else 
					    dist=xypt.minus(z).abs();
					if (dist<mindist) {
					    mindist=dist;
					    min_v=v;
					}
				}
				add((Integer)min_v);
				count++;
				return count; // 'its' iterator is trashed.
			}
			case 'D': // vertices inside a certain disc
			{
				String locs=(String)its.next();
				char c=locs.charAt(0);
				Complex ctr=null;
				double rad;
				
				// Old version: either 'v {v}' or 'z {x} {y}' for center, then radius at end
				if (c=='v' || c=='z') {
					try {
						if (locs.charAt(0)=='v') { // use center of vertex
							int v=Integer.parseInt((String)its.next());
							if (v<1 || v>packData.nodeCount) {
								throw new ParserException();
							}
							ctr=new Complex(packData.rData[v].center);
						}
						else if (locs.charAt(0)=='z') { // use x, y center
							ctr=new Complex(Double.parseDouble((String)its.next()),Double.parseDouble((String)its.next()));
						}
						// next get the radius 'r {r}'
						locs=(String)its.next();
						if (locs.charAt(0)!='r') throw new ParserException();
						rad=Double.parseDouble((String)its.next());
					} catch (Exception ex) {
						throw new ParserException("error in 'D' list format");
					}
				}
				// new version, 6/2015, radius first, then 'z' or default to vert list
				else {
					try {
						rad=Double.parseDouble(locs);
					} catch (Exception ex) {
						throw new ParserException("error in new 'D' list format: {r} [z {x} {y}] [vlist]");
					}
					locs=(String)its.next();
					c=locs.charAt(0);
					if (c=='z') // point given
						ctr=new Complex(Double.parseDouble((String)its.next()),Double.parseDouble((String)its.next()));
					else { // process next string for a single vertex
						try {
							NodeLink nodel=new NodeLink(packData,locs);
							ctr=new Complex(packData.rData[nodel.get(0)].center);
						} catch (Exception ex) {
							throw new ParserException("error getting vertex in new 'D' format: D {r} {v}");
						}
					}
				}
				
				// okay, should have both center and radius now 
				for (int v=1;v<=packData.nodeCount;v++) {
					double dist;
					if (packData.hes<0)
						dist=HyperbolicMath.h_dist(packData.rData[v].center,ctr);
					else if (packData.hes>0)
						dist=SphericalMath.s_dist(packData.rData[v].center,ctr);
					else dist=ctr.minus(packData.rData[v].center).abs();
					if (dist<rad) {
						add(v);
					    count++;
				    }
				}
				break;
			}
			case 'z': // circles containing point (x,y); fall through to 'Z'
			case 'Z': // For sphere, use actual (theta,phi) point: the 'z' flag
				// assumes (x,y) is in visual plane (as from mouse click), while
				// 'Z' means an actual point.
			{
				try {
					Complex z=new Complex(Double.parseDouble((String)its.next()),
							Double.parseDouble((String)its.next()));
					if (packData.hes>0 && str.charAt(0)=='z') {
						z=SphView.visual_plane_to_s_pt(z);
						z=packData.cpScreen.sphView.toRealSph(z);
					}
					NodeLink zsearch=packData.cir_search(z);
					Iterator<Integer> nl=zsearch.iterator();
					while (nl.hasNext()) {
						add((Integer)nl.next());
						count++;
					}
				} catch(Exception ex) {}
				break;
			}
			case 'P': // vertices in 'poisonVerts' and/or 'poisonEdges' 
			{
				if (packData.poisonVerts!=null && packData.poisonVerts.size()>0) {
					Iterator<Integer> ppv=packData.poisonVerts.iterator();
					while (ppv.hasNext()) {
						add((Integer)ppv.next());
						count++;
					}
				}
				if (packData.poisonEdges!=null && packData.poisonEdges.size()>0) {
					Iterator<EdgeSimple> ppv=packData.poisonEdges.iterator();
					while (ppv.hasNext()) {
						EdgeSimple edge=(EdgeSimple)ppv.next();
						add(edge.v);
						add(edge.w);
						count+=2;
					}
				}
				break;
			}
			case 'G': // nodelist approx to given curve (x,y),... (eucl only)
			{
				if (packData.hes!=0) {
					throw new ParserException("path lists only available in euclidean cases");
				}
				int startVert=0;
				// option 'Gv': start with given vert
				if (str.length()>1 && str.charAt(1)=='v') {
					try {
						startVert=Integer.parseInt((String)items.remove(0));
						if (startVert<1 || startVert>packData.nodeCount)
							throw new ParserException("usage: Gf <v>");
					} catch (Exception ex) {
						throw new ParserException(ex.getMessage());
					}
				}
				
				try {
					PathLink pLink=new PathLink(packData.hes,items);
					PathInterpolator pInt=new PathInterpolator(packData.hes);
					pInt.pathInit(pLink);
					EdgeLink el=EdgeLink.path2edgepath(packData,pInt,startVert);
					if (el!=null && el.size()>0) {
						Iterator<EdgeSimple> elst=el.iterator();
						EdgeSimple edge=null;
						while (elst.hasNext()) { 
							edge=elst.next();
							add(edge.v);
							count++;
						}
						add(edge.w);
						count++;
					}
				} catch (Exception ex) {
					throw new ParserException("failed to get or convert path");
				}
			}
			case '{': // set-builder notation; reap results
			{
				SetBuilderParser sbp=new SetBuilderParser(packData,str,01);
				if (!sbp.isOkay()) return 0;
				Vector<SelectSpec> specs=sbp.getSpecVector();
				PackData qackData=sbp.packData;
				NodeLink nl=qackData.circleSpecs(specs);
				if (nl!=null && nl.size()>0) {
					this.addAll(nl);
					count+=nl.size();
				}
				break;
			}
			
			default: // if nothing else, see if it's an integer
			{
				try{
					int v=MathUtil.MyInteger(str);
					add(v);
					count++;
				} catch (NumberFormatException nfe) {
					return count;
				}
			}
			} // end of switch
			} // end of else
		} // end of while
		return count;
	}
	
	/**
	 * Create a list of entries as a string
	 * @return String, null on error
	 */
	public String toString() {
		if (this.size()==0)
			return null;
		Iterator<Integer> myit=this.iterator();
		StringBuilder sb=new StringBuilder();
		while (myit.hasNext()) {
			sb.append(" "+myit.next());
		}
		return sb.toString();
	}
	
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the entries.
	 * @return NodeLink
	 */
	public NodeLink makeCopy() {
		Iterator<Integer> vlist=this.iterator();
		NodeLink newlist=new NodeLink((String)null);
		while (vlist.hasNext()) {
			newlist.add((Integer)vlist.next());
		}
		return newlist;
	}
	
	/**
	 * Abut a copy of given @see NodeLink to the end of this one. 
	 * @param moreNL @see NodeLin
	 * @return count of new vertices (some may be improper, some redundant)
	 */
	public int abutMore(NodeLink moreNL) {
		if (moreNL==null || moreNL.size()==0)
			return 0;
		int ticks=0;
		Iterator<Integer> mit=moreNL.iterator();
		int v=0;
		while (mit.hasNext()) {
			v=(int)mit.next();
			add(v);
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Pick first vertex of list described in string.
	 * @param p PackData
	 * @param str String
	 * @return 0 on failure
	 */
	public static int grab_one_vert(PackData p,String str) {
		NodeLink nlist=new NodeLink(p,str);
		if (nlist!=null && nlist.size()>0) 
			return (int)(nlist.get(0));
		return 0;
	}
	
	/**
	 * Pick first vertex number off first string of first vector of vector
	 * of string vectors. 
	 * @param p PackData
	 * @param flagsegs Vector<Vector<String>>
	 * @return 0 on failure
	 */
	public static int grab_one_vert(PackData p,Vector<Vector<String>> flagsegs) {
		try {
			Vector<String> its=(Vector<String>)flagsegs.get(0);
			NodeLink nlk=new NodeLink(p,its);
			return nlk.getFirst();
		} catch (Exception ex) {
			return 0;
		}
	}
	
	/**
	 * Is 'v' an entry?
	 * @param v
	 * @return int, index of v or -1 on error or not found
	 */
	public int containsV(int v) {
		for (int j=0;j<this.size();j++)
			if ((int)this.get(j)==v)
				return j;
		return -1;
	}
	
	/**
	 * Find first instance successive entries <v,w>
	 * @param v
	 * @param w
	 * @return index of v: -1 on error or not found; 
	 *    -2 if v is last, w first
	 */
	public int findVW(int v,int w) {
		int i=0;
		for (i=0;i<(size()-1);i++)
			if (get(i)==v && get(i+1)==w)
				return i;
		if (get(i)==v && get(0)==w) return -2;
		return -1;
	}
	
	/**
	 * Count my elements (without repeats)
	 * @return int count; -1 on error
	 */
	public static int countMe(NodeLink nl) {
		int count=0;
		if (nl==null || nl.size()==0) return count;
		int max=1;
		if (nl.packData!=null) max=nl.packData.nodeCount;
		// if no packing, have to calculate max
		else {
			Iterator<Integer> it=nl.iterator();
			while (it.hasNext()) {
				int v=it.next();
				max=(v>max) ? v:max;
			}
		}
		int []checks=new int[max+1];
		Iterator<Integer> it=nl.iterator();
		while (it.hasNext()) {
			int v=it.next();
			if (checks[v]==0) {
				checks[v]=1;
				count++;
			}
		}
		return count;
	}
	
	/**
	 * If @see NodeLink is a closed vert list and 'indx' points to
	 * entry v, then rotate, returning a new closed NodeLink starting 
	 * and ending with v.
	 * @param link @see NodeLink
	 * @param indx new starting index
	 * @return @see NodeLink, null if empty, not closed, or on error.
	 */
	public static NodeLink rotateMe(NodeLink link,int indx) {
		int sz=0;
		if (link==null || (sz=link.size())==0 || link.get(0)!=link.get(sz-1))
			return null;
		NodeLink nlink=new NodeLink();
		int i=indx;
		while (i<(sz-1)) { // last one is a repeat
			nlink.add(link.get(i));
			i++;
		}
		i=0;
		while (i<=indx) {
			nlink.add(link.get(i));
			i++;
		}
		return nlink;
	}

	/**
	 * Return a new 'NodeLink' whose order is the reverse of this
	 * @return new 'NodeLink', null if this is empty.
	 */
	public NodeLink reverseMe() {
	    NodeLink qtmp=new NodeLink(packData);
	    if (this.size()==0) return null;
	    Iterator<Integer> it=this.iterator();
	    while (it.hasNext()) {
	    	qtmp.add(0,(Integer)it.next());
	    }
	    return qtmp;
	}
	
	/**
	 * Create new NodeLink in which 'od's with 'nw's are interchanged;
	 * order of list is unchanged.
	 * @param od int, old index
	 * @param nw int, new index
	 * @return new NodeLink or 'this' if empty
	 */
	public NodeLink swap_indices(int od, int nw) {
		int ncnt=this.size();
		if (ncnt==0 || od==nw) 
			return this;
		NodeLink newlink=new NodeLink(packData);
		Iterator<Integer> lst=this.iterator();
		while (lst.hasNext()) {
			int v=lst.next();
			if (v==od)
				newlink.add(nw);
			else if (v==nw)
				newlink.add(od);
			else 
				newlink.add(v);
		}
		return newlink;
	}
	
	/**
     * Return random entry from vertlist; caution, does not adjust
     * for repeat entries.
     * @param vertlist VertList
     * @return -1 on error
     */
    public static int randVert(NodeLink vertlist) {
    	if (vertlist==null || vertlist.size()==0) return -1;
    	int n=new Random().nextInt(vertlist.size());
    	return vertlist.get(n);
    }

	 /**
	  * Does the given list of vertices separate the complex? If
	  * the return is 0, then answer is NO.
	  * 
	  * TODO: this should go somewhere else, perhaps PackData.java?
	  * 
	  * @param p
	  * @param vertlist
	  * @return lowest index of circles not reachable from first
	  * non-green; else 0, and complex is NOT separated.
	  */
	 public static int separates(PackData p,NodeLink vertlist) {
		 int []greens=new int[p.nodeCount+1];
		 Iterator<Integer> vlst=vertlist.iterator();
		 while (vlst.hasNext()) {
			 int v=vlst.next();
			 greens[v]=-1;
		 }
		int seed=0;
		for (int v=1;(v<=p.nodeCount && seed==0);v++) { 
			if (greens[v]==0) {
				seed=v;
			}
		}
		if (seed==0)
			throw new ParserException("failed finding 'seed'");
		
		int []gens=p.label_seed_generations(seed,greens,-1,false);
//		boolean hit=false;
		
		// are their any vertices not reached (not counting green)?
		int not_reached=0;
		for (int v=1;(v<=p.nodeCount && not_reached==0);v++)
			if (greens[v]>=0 && gens[v]==0) // not green and not reached
				not_reached=v;
		
		return not_reached;
	 }
	 
	 /**
	  * Create a new NodeLink that eliminates duplication.
	  * @param nl, NodeLink
	  * @return new NodeLink
	  */
	 public static NodeLink removeDuplicates(NodeLink nl) {
		 NodeLink newNL=new NodeLink(nl.packData);
		 Iterator<Integer> vls=nl.iterator();
		 while (vls.hasNext()) {
			 int v=vls.next();
			 if (newNL.containsV(v)<0)
				 newNL.add(v);
		 }
		 return newNL;
	 }
	 
	 /**
	  * Return fresh NodeLink with entries translated from 'nlink'
	  * using 'vmap'. So, entry v in 'nlink' and entry <v,V> in
	  * 'vmap' leads to new entry V. If there's no translation
	  * for a given v, add it to output link.
	  * @param nlink NodeLink
	  * @param vmap VertexMap (giving pairs <v,V> for translation)
	  * @return NodeLink, new, null if nlink is null.
	  */
	 public static NodeLink translate(NodeLink nlink,VertexMap vmap) {
		 if (nlink==null)
			 return null;
		 NodeLink out=new NodeLink(nlink.packData);
		 
		 // if no 'vmap', return new copy of nlink
		 if (vmap==null || vmap.size()==0) {
			 Iterator<Integer> nl=nlink.iterator();
			 while (nl.hasNext()) { 
				 out.add(nl.next());
			 }
			 return out;
		 }
			 
		 Iterator<Integer> nl=nlink.iterator();
		 while (nl.hasNext()) {
			 int v=nl.next();
			 int V=vmap.findW(v);
			 if (V==0) // keep the original, if not translation
				 out.add(v);
			 else      // replace by new
				 out.add(V);
		 }
		 return out;
	 }
	 
    
}
