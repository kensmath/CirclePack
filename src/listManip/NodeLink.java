package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.SideData;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.SetBuilderParser;
import komplex.EdgeSimple;
import packing.PackData;
import packing.QualMeasures;
import panels.PathManager;
import tiling.Tile;
import util.MathUtil;
import util.PathDistance;
import util.PathInterpolator;
import util.SelectSpec;
import util.SphView;
import util.StringUtil;
import util.UtilPacket;

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
	int vCount=0;
	
	// Constructors
	public NodeLink() { // empty, no packing
		super();
		packData=null;
	}
	
	public NodeLink(PackData p,String datastr) {
		super();
		packData=p;
		if (p!=null)
			vCount=p.packDCEL.vertCount;
		if (datastr!=null) addNodeLinks(datastr);
	}
	
	public NodeLink(PackData p,int n) {
		super();
		packData=p;
		if (p!=null)
			vCount=p.packDCEL.vertCount;
		if (n>0 && (packData==null || n<=vCount)) add(n);
	}
	
	public NodeLink(PackData p,Vector<String> items) {
		super();
		packData=p;
		if (p!=null)
			vCount=p.packDCEL.vertCount;
		if (items==null || items.size()==0) { // default to 'a' (all vertices)
			items=new Vector<String>(1);
			items.add("a");
		}
		addNodeLinks(items);
	}
	
	/**
	 * Not associated with any PackData
	 * @param datastr String
	 */
	public NodeLink(String datastr) {
		this(null,datastr);
	}
	
	/**
	 * Initiate empty list
	 * @param p
	 */
	public NodeLink(PackData p) {
		this(p,(String)null);
	}
	
	public boolean add(int v) {
		if ((packData!=null && v>0 && v<=vCount) || packData==null)
			return add((Integer)v);
		return false;
	}
	
	public boolean add(Integer v) {
		if (v==null) 
			return false;
		if ((packData!=null && v.intValue()>0 && v.intValue()<=vCount) || packData==null)
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
		int nodecount;
	
		if (packData==null) 
			return -1;
		PackDCEL pdcel=packData.packDCEL;

		nodecount=vCount;
		
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
				HalfLink hlink=null;
				GraphLink glink=null;
				boolean ck=false;
				
				// vlist or Vlist
				if ((str.startsWith("v") && 
						(vlink=packData.vlist)!=null && vlink.size()>0) ||
					(str.startsWith("V") && 
						(vlink=CPBase.Vlink)!=null && CPBase.Vlink.size()>0) ||
					(str.startsWith("e") && 
						(elink=packData.elist)!=null && elink.size()>0) ||
					(str.startsWith("E") && 
						(elink=CPBase.Elink)!=null && CPBase.Elink.size()>0) || 
					(str.startsWith("g") && 
						(glink=packData.glist)!=null && glink.size()>0) ||
					(str.startsWith("G") && 
						(glink=CPBase.Glink)!=null && CPBase.Glink.size()>0) ||
					(str.startsWith("h") &&
						(hlink=packData.hlist)!=null && hlink.size()>0) ||
					(str.startsWith("H") && 
						(hlink=CPBase.Hlink)!=null && CPBase.Hlink.size()>0)) {

					if (str.startsWith("V") || str.startsWith("E") || 
							str.startsWith("G") || str.startsWith("H")) 
						ck=true; // must check for legality
					String strdata=str.substring(5).trim(); // remove '?list'	

					// if not vlink, then convert others to elink
					if (vlink==null) {
						if (hlink!=null) {
							elink=new EdgeLink(hlink);
							hlink=null;
						}
						else if (glink!=null) {
							elink=new EdgeLink(glink);
							glink=null;
						}
					}
					
					// check for parens listing range of indices
					int lsize=0;
					if (vlink!=null)
						lsize=vlink.size()-1;
					else if (elink!=null)
						lsize=elink.size()-1;
					int[] irange=StringUtil.get_int_range(strdata, 0,lsize);
					if (irange!=null) {
						int a=irange[0];
						int b=(irange[1]>lsize) ? lsize : irange[1]; 
						if (vlink!=null) {
							for (int j=a;j<=b;j++) {
								v=vlink.get(j);
								if (ck && v>packData.nodeCount) { }
								else {
									add(v);
									count++;
								}
							}
						}
						// else have an elink
						if (elink!=null) {
							int lastV=0;
							if (this.size()>0)
								lastV=this.getLast();
							EdgeSimple edge=null;
							for (int j=a;j<=b;j++) {
								edge=elink.get(j);
								if (ck) {
									if (edge.v<=vCount && edge.v!=lastV) {
										lastV=edge.v;
										add(lastV);
										count++;
									}
									if (edge.w<=vCount && edge.w!=lastV) {
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
					} // done with parentheses
					
					// check for brackets next
					String brst=StringUtil.get_bracket_strings(strdata)[0];
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate: copy first to end
							if (vlink!=null)
								vlink.add(vlink.getFirst());
							else
								elink.add(elink.getFirst());
						}
						if (brst.startsWith("n")) { // use up first
							if (vlink!=null) {
								v=vlink.removeFirst();
								if (v<=vCount) {
									add(v);
									count++;
								}
							}
							else {
								EdgeSimple edge=elink.removeFirst();
								int lastV=this.getLast();
								if (edge.v<=vCount && edge.v!=lastV) {
									add(edge.v);
									lastV=edge.v;
									count++;
								}
								if (edge.w<=vCount && edge.w!=this.getLast()) {
									add(edge.w);
									lastV=edge.w;
									count++;
								}
							}
						}
						if (brst.startsWith("l")) { // last
							if (vlink!=null) {
								v=vlink.getLast();
								if (ck && v>vCount) {}
								else { 
									add(v);
									count++;
								}
							}
							else {
								EdgeSimple edge=elink.getLast();
								int lastV=this.getLast();
								if (edge.v<=vCount && edge.v!=lastV) {
									add(edge.v);
									lastV=edge.v;
									count++;
								}
								if (edge.w<=vCount && edge.w!=this.getLast()) {
									add(edge.w);
									lastV=edge.w;
									count++;
								}
							}
							
							v=vlink.getLast();
							if (ck && v>vCount) {}
							else { 
								add(v);
								count++;
							}
						}
						else {
							int n=0;
							try{
								n=MathUtil.MyInteger(brst);
							} catch (NumberFormatException nfe) {}
							
							if (vlink!=null && n>=0 && n<vlink.size()) {
								v=vlink.get(n);
								if (v>vCount) {}
								else { 
									add(v);
									count++;
								}
							}
							else if (n>=0 && n<elink.size()) {
								EdgeSimple edge=elink.get(n);
								int lastV=this.getLast();
								if (edge.v<=vCount && edge.v!=lastV) {
									add(edge.v);
									lastV=edge.v;
									count++;
								}
								if (edge.w<=vCount && edge.w!=this.getLast()) {
									add(edge.w);
									lastV=edge.w;
									count++;
								}
							}
						}
					} // done with brackets
					
					// else just adjoin the lists
					else { 
						if (vlink!=null) {
							if (!ck) {
								int n=size();
								addAll(n,vlink);
								count +=vlink.size();
							}
							else {
								Iterator<Integer> vlst=vlink.iterator();
								while (vlst.hasNext()) {
									v=(Integer)vlst.next();
									if (v<=vCount) {
										add(v);
										count++;
									}
								}
							}
						}
						else {
							Iterator<EdgeSimple> elst=elink.iterator();
							while (elst.hasNext()) {
								EdgeSimple edge=elst.next();
								int lastV=this.getLast();
								if (edge.v<=vCount && edge.v!=lastV) {
									add(edge.v);
									lastV=edge.v;
									count++;
								}
								if (edge.w<=vCount && edge.w!=this.getLast()) {
									add(edge.w);
									lastV=edge.w;
									count++;
								}
							}
						}
					}
				}
				else // no appropriate list found
					return count;
			} // done with '?list' case
			
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
				int last=vCount;
				String[] pair_str=StringUtil.get_paren_range(str); // get two strings
				if (pair_str!=null && pair_str.length==2) { // must have 2 strings
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
				int first=1;
				int last=vCount;
				boolean bad=false;
				String[] pair_str=StringUtil.get_paren_range(str); // get two strings
				if (pair_str!=null && pair_str.length==2) { // must have 2 strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))!=0) 
						first=a;
					if ((b=NodeLink.grab_one_vert(packData,pair_str[1]))!=0) 
						last=b;
					
					// check first/last: on bdry? on same component?
					NodeLink bdrycomp=CombDCEL.bdryCompVerts(packData,first);
					if (bdrycomp==null || bdrycomp.size()==0 || bdrycomp.containsV(last)<0)
						throw new CombException("vertices "+first+" and "+last+" are not "+
								"on the same bdry component");
					if (first!=last) {
						Iterator<Integer> bdst=bdrycomp.iterator();
						int w=first;
						while (bdst.hasNext()) {
							w=bdst.next();
							if (w==last)
								break;
							add(w);
							count++;
						}
						if (w==last) {
							add(w);
							count++;
						}
					}
					else {
						abutMore(bdrycomp);
						count +=bdrycomp.size();
					}
				}
				if (pair_str==null || bad) { // whole boundary
					for (int f=1;f<=pdcel.idealFaceCount;f++) {
						DcelFace idealface=pdcel.idealFaces[f];
						int[] vs=idealface.getVerts();
						for (int i=vs.length-1;i>=0;i--)
							add(vs[i]);
					}
				}
				break;
			}
			// interior; check for parens
			case 'i':
			{
				int first=1;
				int last=vCount;
				String[] pair_str=StringUtil.get_paren_range(str);
				if (pair_str!=null && pair_str.length==2) {  // get two strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(packData,pair_str[0]))!=0) 
						first=a;
					if ((b=NodeLink.grab_one_vert(packData,pair_str[1]))!=0) 
						last=b;
				}
				for (int j=first;j<=last;j++) 
					if (packData.getBdryFlag(j)==0) {
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
						Complex vec=packData.getCenter((Integer)this.getLast()).
							minus(packData.getCenter((Integer)this.getFirst()));
						// print the "Berger's vector: displacement from first
						//    center to last center
						CirclePack.cpb.msg("Berger's vector is "+
								String.format("%.6e",vec.x)+" "+
								String.format("%.6e",vec.y)+"i");
					}
				}
				return ans;
			}
			case 'e': // vertices along hex-extended edges
				// do the work in 'HalfLink'
			{
				StringBuilder strbld=new StringBuilder("-"+str+" "+
						StringUtil.reconItem(items));
				HalfLink hlink=new HalfLink(packData,strbld.toString());
				if (hlink==null || hlink.size()==0)
					break;
				count +=abutHalfLink(hlink);
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
							&& qnum<3 && PackControl.cpDrawing[qnum].getPackData().status) {
						qackData=PackControl.cpDrawing[qnum].getPackData();
					}
				} catch(Exception ex) {}
				for (int v=1;v<=nodecount;v++)
					if (v<=vCount 
							&& ((notmarked && qackData.getVertMark(v)==0) 
									|| (!notmarked && qackData.getVertMark(v)!=0))) {
						add(v);
						count++;
					}
				break;
			}
			case 'g': // same side of global 'ClosedPath' as 'alpha'?
			{
				if (CPBase.ClosedPath==null) break;
				boolean alpha_side=PathManager.path_wrap(packData.getCenter(packData.getAlpha()));
				for (int v=1;v<=nodecount;v++) {
					if (PathManager.path_wrap(packData.getCenter(v))==alpha_side) {
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
				int []hits=new int[vCount+1];
				switch(str.charAt(1)) {
				case 'f':
				{
				    FaceLink facelist=new FaceLink(packData,items);
					its=null; // eat rest of items
					if (facelist==null || facelist.size()==0) break;
				    Iterator<Integer> flist=facelist.iterator();
				    int v;
				    while (flist.hasNext()) {
					int f=flist.next();
					int[] fverts=packData.getFaceVerts(f);
					for (int j=0;j<3;j++) {
					    v=fverts[j];
					    if (hits[v]==0) {
						add(v);
						count++;
						hits[v]=1;
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
					int[] petals=packData.getPetals(v);
					for (int j=0;j<petals.length;j++) {
					    vert=petals[j];
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
						if (pathdist.distance(packData.getCenter(v))) {
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
				PackDCEL pdc=packData.packDCEL;
				int numsides=pdc.pairLink.size()-1;
				if ((str.length()>1 && str.charAt(1)=='a') ||
						numsides<=0) {
					RedEdge rtrace=pdc.redChain;
					do {
						add(rtrace.myEdge.origin.vertIndx);
						rtrace=rtrace.nextRed;
						count++;
					} while (rtrace!=pdc.redChain);
					add(pdc.redChain.myEdge.origin.vertIndx); // close up
						
					// eat the rest of the stings
					while (its.hasNext()) 
						its.next();
					break;
				}
					
				boolean []tag=new boolean[numsides+1];
				for (int i=1;i<=numsides;i++) 
					tag[i]=false;
				if (!its.hasNext()) // default to 'all'
					for (int i=1;i<=numsides;i++) 
						tag[i]=true;
				else {
					do {
						String itstr=(String)its.next();
						if (itstr.startsWith("a"))
							for (int i=1;i<=numsides;i++) 
								tag[i]=true;
						else {
							try {
								int n=Integer.parseInt(itstr);
								if (n>=0 && n<numsides) 
									tag[n]=true;
							} catch (NumberFormatException nfx) {
								while (its.hasNext()) 
									itstr=(String)its.next(); // toss rest
								break;
							}
						}
					} while (its.hasNext());
				}
				
				// now traverse the chosen sides
				for (int i=1;i<=numsides;i++) {
					if (tag[i]) {
						SideData dsd=pdc.pairLink.get(i);
						RedEdge rtrace=dsd.startEdge;
						do {
							add(rtrace.myEdge.origin.vertIndx);
							rtrace=rtrace.nextRed;
							count++;
						} while (rtrace!=dsd.endEdge);
						add(dsd.endEdge.myEdge.origin.vertIndx);
						
						// should we add end vertex?
						if ((i<numsides && !tag[i+1]) || 
								(i==numsides))
							add(dsd.endEdge.myEdge.twin.origin.vertIndx);
						count+=2;
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
						if (Double.isNaN(packData.getRadius(v))
								|| Double.isNaN(packData.getCenter(v).x)
								|| Double.isNaN(packData.getCenter(v).y)
								|| (packData.hes>0 && packData.getRadius(v)-Math.PI<PackData.TOLER)) {
							add(v);
							count++;
						}
					}
				}
				
				else if (str.length()>1 && (str.charAt(1)=='+' || 
						str.charAt(1)=='-')) {
					boolean nxt=true;
					if (str.charAt(1)=='-')
						nxt=false;
					NodeLink vs=new NodeLink(packData,items);
					Iterator<Integer> vsi=vs.iterator();
					while (vsi.hasNext()) {
						int v=vsi.next();
						int[] flower=packData.getFlower(v);
						if (packData.getBdryFlag(v)!=0) {
							int w=flower[0];
							if (!nxt)
								w=flower[packData.countFaces(v)];
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
								int[] flower=packData.getFlower(v);
								if (jdex<=packData.countFaces(v)) {
									add(flower[jdex]);
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
			case 'q': // fall through
			case 'Q': // quality: v's with worst visual error than given number
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
				add(packData.getAlpha());
				count++;
				break;
			}
			case 'B': // boundary starts (i.e., one vertex on each 
					// boundary component. 
			{
				if (packData.getBdryCompCount()>0) {
					for (int i=1;i<=packData.getBdryCompCount();i++) { // indexing starts at 1
						if (packData.packDCEL!=null) {
							DcelFace idf=packData.packDCEL.idealFaces[i];
							add(idf.edge.origin.vertIndx);
						}
						else 
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
					int pf=packData.getPlotFlag(v);
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
					z=packData.getCenter(v);
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
							if (v<1 || v>vCount) {
								throw new ParserException();
							}
							ctr=packData.getCenter(v);
						}
						else if (locs.charAt(0)=='z') { // use x, y center
							ctr=new Complex(
									Double.parseDouble((String)its.next()),
									Double.parseDouble((String)its.next()));
						}
						// next get the radius 'r {r}'
						locs=(String)its.next();
						if (locs.charAt(0)!='r') throw new ParserException();
						rad=Double.parseDouble((String)its.next());
					} catch (Exception ex) {
						throw new ParserException("error in 'D' list format");
					}
				}
				// (new 6/2015) radius first, then 'z' or default to vert list
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
							ctr=packData.getCenter(nodel.get(0));
						} catch (Exception ex) {
							throw new ParserException("error getting vertex in new 'D' format: D {r} {v}");
						}
					}
				}
				
				// okay, should have both center and radius now 
				for (int v=1;v<=vCount;v++) {
					double dist;
					if (packData.hes<0)
						dist=HyperbolicMath.h_dist(packData.getCenter(v),ctr);
					else if (packData.hes>0)
						dist=SphericalMath.s_dist(packData.getCenter(v),ctr);
					else dist=ctr.minus(packData.getCenter(v)).abs();
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
						z=packData.cpDrawing.sphView.toRealSph(z);
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
						if (startVert<1 || startVert>vCount)
							throw new ParserException("usage: Gf <v>");
					} catch (Exception ex) {
						throw new ParserException(ex.getMessage());
					}
				}
				
				try {
					PathLink pLink=new PathLink(packData.hes,items);
					PathInterpolator pInt=new PathInterpolator(packData.hes);
					pInt.pathInit(pLink);
					EdgeLink el=HalfLink.path2edgepath(packData,pInt,startVert);
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
				SetBuilderParser sbp=new SetBuilderParser(packData,str,'c');
				if (!sbp.isOkay()) 
					return 0;
				Vector<SelectSpec> specs=sbp.getSpecVector();
				PackData qackData=sbp.packData;
				NodeLink nl=circleSpecs(qackData,specs);
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
	 * add end vertices from 'HalfLink'
	 * @param hlink HalfLink
	 * @return count
	 */
	public int abutHalfLink(HalfLink hlink) {
		if (hlink==null || hlink.size()==0)
			return 0;
		int count=0;
		Iterator<HalfEdge> his=hlink.iterator();
		int lastv=-1; // maybe save redundancy for chained edges
		while (his.hasNext()) {
			HalfEdge he=his.next();
			int v=he.origin.vertIndx;
			if (v!=lastv) {
				add(v);
				lastv=v;
				count++;
			}
		}
		return count;
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
	public NodeLink swapVW(int od, int nw) {
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
	 * Given 'VertexMap' with <old, new>, translate this 
	 * 'NodeLink' from old to the new indices.
	 * @param oldnew VertexMap
	 * @return new NodeLink (with null PackData)
	 */
	public NodeLink translateMe(VertexMap oldnew) {
	    NodeLink qtmp=new NodeLink();
	    if (this.size()==0) return qtmp;
	    Iterator<Integer> it=this.iterator();
	    while (it.hasNext()) {
	    	int v=oldnew.findW(it.next());
	    	if (v>0)
	    		qtmp.add(v);
	    }
	    return qtmp;
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
		 int vCount=p.packDCEL.vertCount;
		 int []greens=new int[vCount+1];
		 Iterator<Integer> vlst=vertlist.iterator();
		 while (vlst.hasNext()) {
			 int v=vlst.next();
			 greens[v]=-1;
		 }
		int seed=0;
		for (int v=1;(v<=vCount && seed==0);v++) { 
			if (greens[v]==0) {
				seed=v;
			}
		}
		if (seed==0)
			throw new ParserException("failed finding 'seed'");
		
		int []gens=p.label_seed_generations(seed,greens,-1,false);
//		boolean hit=false;
		
		// are there any vertices not reached (not counting green)?
		int not_reached=0;
		for (int v=1;(v<=vCount && not_reached==0);v++)
			if (greens[v]>=0 && gens[v]==0) // not green and not reached
				not_reached=v;
		
		return not_reached;
	 }
	 
	 /** 
	  * find vertices incident to 'EdgeSimple's
	  * @param elink EdgeLink
	  * @return new NodeLink
	  */
	 public static NodeLink incident(EdgeLink elink) {
		 NodeLink vlink=new NodeLink();
		 Iterator<EdgeSimple> eis=elink.iterator();
		 while (eis.hasNext()) {
			 EdgeSimple edge=eis.next();
			 vlink.add(edge.v);
			 vlink.add(edge.w);
		 }
		 return vlink;
	 }
	 
	 /** 
	  * find vertices incident to 'HalfEdge's
	  * @param hlink HalfLink
	  * @return new NodeLink
	  */
	 public static NodeLink incident(HalfLink hlink) {
		 if (hlink==null)
			 return null;
		 NodeLink vlink=new NodeLink();
		 Iterator<HalfEdge> his=hlink.iterator();
		 while (his.hasNext()) {
			 HalfEdge he=his.next();
			 vlink.add(he.origin.vertIndx);
			 vlink.add(he.next.origin.vertIndx);
		 }
		 return vlink;
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
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }
	 
	 /**
	  * Return fresh NodeLink with entries translated from 'nlink'
	  * using 'vmap'.
	  *  
	  * CAREFUL: convention is that 'vmap' is {new,old} for
	  * translation, so old entry v in 'nlink' and entry 
	  * <v,V> in 'vmap' leads to new entry V. 
	  *  
	  * If there's no translation for a given v, add 'v' itself
	  * to output link.
	  * @param nlink NodeLink
	  * @param vmap VertexMap (giving pairs <v,V> for translation)
	  * @return NodeLink, new, null if nlink is null.
	  */
	 public static NodeLink translate(NodeLink nlink,VertexMap vmap) {
		 if (nlink==null || nlink.size()==0)
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
	 
	/**
	 * Make up list by looking through SetBuilder specs 
	 * (from {..} set-builder notation). Use 'tmpUtil' to 
	 * collect information before creating the NodeLink for 
	 * return.
	 * @param p PackData
	 * @param specs Vector<SelectSpec>
	 * @return NodeLink list of specified circles.
	 */
	public static NodeLink circleSpecs(PackData p,Vector<SelectSpec> specs) {
		if (specs==null || specs.size()==0) 
			return null;
		SelectSpec sp=null;
		int count=0;
			// will store results in 'tmpUtil'
		int[] tmpUtil=new int[p.nodeCount+1];
		// loop through all the specifications: these should alternate
		//   between 'specifications' and 'connectives', starting 
		//   with the former, although typically there will be just 
		//   one specification in the vector and no connective.
		UtilPacket uPx=null;
		UtilPacket uPy=null;
		boolean isAnd=false; // true for '&&' connective, false for '||'.
		for (int j=0;j<specs.size();j++) {
			sp=(SelectSpec)specs.get(j);
			if (sp.object!='c') 
				throw new ParserException(); // spec must be for circles
			try {
				for (int v=1;v<=p.nodeCount;v++) {
					
					// success?
					boolean outcome=false;
					uPx=sp.node_to_value(p,v,0);
					if (sp.unary) {
						if (uPx.rtnFlag!=0)
							outcome=sp.comparison(uPx.value,0);
					}
					else {
						uPy=sp.node_to_value(p,v,1);
						if (uPy.rtnFlag!=0)
							outcome=sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && tmpUtil[v]==0) { // 'or' situation
							tmpUtil[v]=1;
							count++;
						}
					}
					else { // no, fails this condition
						if (isAnd && tmpUtil[v]!=0) { // 'and' situation
							tmpUtil[v]=0;
							count--;
						}
					}
				}
			} catch (Exception ex) {
				throw new ParserException();
			}
			
			// if specs has 2 or more additional specifications, the next must
			//    be a connective. Else, finish loop.
			if ((j+2)<specs.size()) {
				sp=(SelectSpec)specs.get(j+1);
				if (!sp.isConnective) throw new ParserException();
				isAnd=sp.isAnd; 
				j++;
			}
			else j=specs.size(); // kick out of loop
		}
	
		if (count>0) {
			NodeLink nl=new NodeLink(p);
			for (int v=1;v<=p.nodeCount;v++)
				if (tmpUtil[v]!=0) nl.add(v);
			return nl;
		}
		else return null;
	}
		
}
