package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import geometry.EuclMath;
import input.SetBuilderParser;
import komplex.SideDescription;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.RedList;
import packing.PackData;
import util.FaceParam;
import util.MathUtil;
import util.PathInterpolator;
import util.SelectSpec;
import util.SphView;
import util.StringUtil;

/**
 * Linked list of faces for circle packings.
 * 
 * TODO: Because numbering is ephemeral and access is
 * needed to DCEL structures, we don't yet have a 
 * 'translate' method for faces.
 * @author kens
 */
public class FaceLink extends LinkedList<Integer> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public FaceLink(PackData p,String datastr) {
		super();
		packData=p;
		if (datastr!=null) addFaceLinks(datastr);
	}
	
	public FaceLink(PackData p,Vector<String> items) {
		super();
		packData=p;
		if (items==null || items.size()==0) { // default to 'a' (all faces)
			items=new Vector<String>(1);
			items.add("a");
		}
		addFaceLinks(items);
	}
	/**
	 * Not associated with any PackData
	 * @param datastr
	 */
	public FaceLink(String datastr) {
		this(null,datastr);
	}
	
	/**
	 * empty list, no packing
	 */
	public FaceLink() {
		super();
		packData=null;
	}
	
	/**
	 * Initiate empty list
	 * @param p
	 */
	public FaceLink(PackData p) {
		this(p,(String)null);
	}
	
	public boolean add(int v) {
		if ((packData!=null && v>0 && v<=packData.faceCount) || packData==null)
			return super.add((Integer)v);
		return false;
	}
	
	public boolean add(Integer v) {
		if ((packData!=null && v.intValue()>0 && v.intValue()<=packData.faceCount) || packData==null)
			return super.add(v);
		return false;
	}
	
	/**
	 * Add links to this list (only if it is associated with PackData?)
	 * @param datastr
	 * @return
	 */
	public int addFaceLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addFaceLinks(items);
	}
	
	/**
	 * Add links to this list (only if it is associated with PackData?)
	 * @param datastr
	 * @return
	 */
	public int addFaceLinks(Vector<String> items) {
		int count=0;
		Face []faces;
		
		if (packData==null) return -1;
		int facecount=packData.faceCount;
		faces=packData.faces;
		
		Iterator<String>its=items.iterator();
		
		while (its!=null && its.hasNext()) {

			/* =============== here's the work ==================
	 		parse the various options based on first character */

			String str=(String)its.next();
			// it's easy to use '-' flags by mistake
			if (str.startsWith("-")) { 
				str=str.substring(1);
			}

			its.remove(); // shuck this entry (though still in 'str')
			
			// self call if str is a variable
			if (str.startsWith("_")) {
				count+=addFaceLinks(CPBase.varControl.getValue(str));
			}
			
			// check for '?list' first
			else if (str.substring(1).startsWith("list")) {
				int f;
				FaceLink flink=null;
				boolean ck=false;
				
				// flist or Flist
				if ((str.startsWith("f") && (flink=packData.flist)!=null
						&& flink.size()>0) ||
						(str.startsWith("F") && (flink=CPBase.Flink)!=null
								&& CPBase.Flink.size()>0)) {
					if (str.startsWith("F")) // v legal for packData?
						ck=true;
					// check for brackets first
					String brst=StringUtil.brackets(str);
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate list
							flink.add(flink.getFirst());
						}
						if (brst.startsWith("r") 
								|| brst.startsWith("n")) { // use up first
							f=(Integer)flink.remove(0);
							if (ck && f>packData.faceCount) {}
							else { 
								add(f);
								count++;
							}
						}
						if (brst.startsWith("l")) { // last
							f=(Integer)flink.getLast();
							if (ck && f>packData.faceCount) {}
							else { 
								add(f);
								count++;
							}
						}						
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<flink.size()) {
									f=flink.get(n);
									if (ck && f>packData.faceCount) {}
									else { 
										add(f);
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
							addAll(n,flink);
							count +=flink.size();
						}
						else {
							Iterator<Integer> flst=flink.iterator();
							while (flst.hasNext()) {
								f=(Integer)flst.next();
								if (f<=packData.faceCount) {
									add(f);
									count++;
								}
							}
						}
					}
				}
			}

			// For 'random', 2 steps: get edge list, then make selection
			else if (str.equals("r")) {
				FaceLink flk=null;
				if (items==null || items.size()==0)
					flk=new FaceLink(packData,"a");
				else flk=new FaceLink(packData,items);
				int f=randFace(flk);
				if (f>=1) {
					add(f);
					count++;
				}
			}
			
			/* Now parse remaining options based on first character;
	 		default case, see if it's a number. */

			else {
			switch(str.charAt(0)) {
			
			// all; check for braces
			case 'a':
			{
				int first=1;
				int last=facecount;
				String []pair_str=StringUtil.parens_parse(str); // get two strings
				if (pair_str!=null) { // must have 2 strings
					int a,b;
					if ((a=FaceLink.grab_one_face(packData,pair_str[0]))!=0) first=a;
					if ((b=FaceLink.grab_one_face(packData,pair_str[1]))!=0) last=b;
				}
				for (int i=first;i<=last;i++) {
					add(i);
					count++;
				}
				break;
			}
			
			// shade alternating faces (as far as possible); 
			case 'A':
			{
				// is there a starting face?
				int startface=1;
				if (items!=null && items.size()>0) {
					int sf=grab_one_face(packData,items.get(0));
					if (sf>1 && sf<=packData.faceCount)
						startface=sf;
				}
				
				// gather the faces/verts hit
				int []hitfaces=new int[packData.faceCount+1];
				int []hitverts=new int[packData.nodeCount+1]; // 1=add, 2=done
				hitfaces[startface]=1;
			
				NodeLink currv=null;
				NodeLink nextv=new NodeLink(packData);
				nextv.add(packData.faces[startface].vert[0]);
				
				try {
				while (nextv.size()!=0) {
					currv=nextv;
					nextv=new NodeLink(packData);
	
					Iterator<Integer> ntv=currv.iterator();
					while (ntv.hasNext()) {
						int v=ntv.next();
						int j=-1;
						for (int k=0;(k<packData.countFaces(v) && j<0);k++) { 
							if (hitfaces[packData.getFaceFlower(v,k)]>0)
								j=k;
						}
						if (j>=0) {
							int[] faceFlower=packData.getFaceFlower(v);
							if (((int)(j/2))*2==j) { // j is even
								for (int k=0;k<packData.countFaces(v);k=k+2) {
									int f=faceFlower[k];
									hitfaces[f]=1;
								}
								for (int k=1;k<packData.countFaces(v);k=k+2) {
									int f=faceFlower[k];
									if (hitfaces[f]>0)
										throw new CombException(); // conflict
								}
							}
							else {
								for (int k=1;k<packData.countFaces(v);k=k+2) {
									int f=faceFlower[k];
									hitfaces[f]=1;
								}
								for (int k=0;k<packData.countFaces(v);k=k+2) {
									int f=faceFlower[k];
									if (hitfaces[f]>0)
										throw new CombException(); // conflict
								}
							}
						
							// add flower to nextv for future processing
							int[] flower=packData.getFlower(v);
							for (int k=0;k<(packData.countFaces(v)+packData.getBdryFlag(v));k++) {
								int w=flower[k];
								if (hitverts[w]==0) {
									nextv.add(w);
									hitverts[w]=1; // have touched this
								}
							}
							hitverts[v]=2; // have finished with this
						}
					} // end of while on currv
					
				} // end of outer while
				
				// store faces found
				for (int f=1;f<=packData.faceCount;f++)
					if (hitfaces[f]>0) {
						add(f);
						count++;
					}
				} catch (CombException cex) {
					CirclePack.cpb.errMsg("Failed to choose alternating faces");
				}
				break;
			}

			// all bdry faces
			case 'b':
			{
				for (int i=1;i<=facecount;i++) { 
					if (packData.isBdryFace(i)) {
						add(i);
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
							&& qnum<3 && PackControl.cpScreens[qnum].getPackData().status) {
						qackData=PackControl.cpScreens[qnum].getPackData();
					}
				} catch(Exception ex) {}
				for (int f=1;f<=facecount;f++)
					if (f<=qackData.faceCount 
							&& ((notmarked && qackData.getFaceMark(f)==0) || 
									(!notmarked && qackData.getFaceMark(f)!=0))) {
						add(f);
						count++;
					}
				break;
			}			
			case 'j': // negatively oriented faces
			{
				if (packData.hes>0) break;
				for (int f=1;f<=packData.faceCount;f++) {
					int []vert=packData.getFaceVerts(f);
					if (!EuclMath.ccWise(packData.getCenter(vert[0]),
							packData.getCenter(vert[1]),packData.getCenter(vert[2]))) {
						add(f);
						count++;
					}
				}
				break;
			}
			case 'n': // nan? (not-a-number check on radius/center
			{
				boolean hit;
				for (int f=1;f<=facecount;f++) {
					int[] fverts=packData.getFaceVerts(f);
					for (int j=0;j<3;j++) {
						hit=false;
						int v=fverts[j];
						if (Double.isNaN(packData.getRadius(v))
							|| Double.isNaN(packData.getCenter(v).x)
							|| Double.isNaN(packData.getCenter(v).y)
							|| (packData.hes>0 && packData.getRadius(v)-Math.PI<
									PackData.TOLER)) {
							j=3;
							hit=true;
						}
						if (hit) {
							add(v);
							count++;
						}
					}
				}
				break;
			}
			case 'p': // plotFlag set (or 'pc', not set);
			{
				boolean notset=false;
				if (str.substring(1).contains("c")) notset=true;
				for (int f=1;f<=facecount;f++) {
					int pf=packData.faces[f].plotFlag;
					if ((notset && pf==0) || (!notset && pf!=0)) {
						add(f);
						count++;
					}
				}
				break;
			}
			case 'R': // faces from red chain,
			{
				if (packData.redChain==null) break;
				
				// 'Ra', want full redchain, so it closes up
				if (str.length()>1 && str.charAt(1)=='a') { 
					RedList rlst=(RedList)packData.redChain;
					add(rlst.face);
					while ((rlst=rlst.next)!=(RedList)packData.redChain && count<2*packData.faceCount) {
						add(rlst.face);
						count++;
					}
					if (count>=2*packData.faceCount)
						CirclePack.cpb.errMsg("error in transiting redchain in 'Ra' command");
					break;
				}
				
				// otherwise, select by given 'side' indices: absorbs rest of 'items'.
				  int numSides=-1;
				  if (packData.getSidePairs()==null || (numSides=packData.getSidePairs().size())==0) {
					  while (its.hasNext()); // flush rest of items
					  break;
				  }
				  boolean []tag=new boolean[numSides];
				  for (int i=0;i<numSides;i++) tag[i]=false;
				  if (!its.hasNext()) { // default to 'all'
					  for (int i=0;i<numSides;i++) tag[i]=true;
				  }
				  else do {
					  String itstr=(String)its.next();
					  if (itstr.startsWith("a"))
						  for (int i=0;i<numSides;i++) tag[i]=true;
					  else {
						  try {
							  int n=MathUtil.MyInteger(itstr);
							  if (n>=0 && n<numSides) tag[n]=true;
						  } catch (NumberFormatException nfx) {}
					  }
				  } while (its.hasNext());

				  // now to get the chosen segments
				  // NOTE: some faces between end of one segment and
				  //       start of next are not picked up.
				  Iterator<SideDescription> sp=packData.getSidePairs().iterator();
				  SideDescription ep=null;
				  RedList rlst=null;
				  int tick=0;
				  while (sp.hasNext()) {
					  ep=(SideDescription)sp.next();
					  if (tag[tick++]) { // yes, do this one
						  rlst=(RedList)ep.startEdge;
//System.err.println(" startEdge (tick "+tick+") "+ep.startEdge+", face="+ep.startEdge.face+
//		                     "; endEdge "+ep.endEdge+", endface="+ep.endEdge.face);	
						  if (ep.startEdge!=ep.endEdge) {
							  do {
//System.err.println(" next face is "+rlst.face);							  
								  add(rlst.face);
								  count++;
								  rlst=rlst.next;
							  } while (rlst!=(RedList)ep.endEdge);
						  }
						  add(rlst.face);
						  count++;
					  }
				  }
				  break;
			}
			case 'I': // incident to vertices/faces/edges; redundancies avoided
			{
				if (str.length()<=1) break;
				int []hits=new int[packData.faceCount+1];
				switch(str.charAt(1)) {
				case 'c': // fall through to 'v'
				case 'v': // faces containing 'v' as corner
				{
					NodeLink vertlist=new NodeLink(packData,items);
					its=null; // eat rest of items
					if (vertlist==null || vertlist.size()==0) break;
					Iterator<Integer> vlist=vertlist.iterator();
					int v,f;
					while (vlist.hasNext()) {
						v=(Integer)vlist.next();
						int[] faceFlower=packData.getFaceFlower(v);
						for (int j=0;j<packData.countFaces(v);j++) {
							f=faceFlower[j];
							if (hits[f]==0) {
								add(f);
								hits[f]=1;
								count++;
							}
						}
					}
					break;
				}
				case 'f': // faces sharing a full edge
				{
					FaceLink facelist=new FaceLink(packData,items);
					its=null; // eat rest of items
					if (facelist==null || facelist.size()==0) break;
					Iterator<Integer> flist=facelist.iterator();
					int f;
					while (flist.hasNext()) {
						int[] fverts=packData.getFaceVerts((Integer)flist.next());
						for (int j=0;j<3;j++) {
							int v=fverts[j];
							int w=fverts[(j+1)%3];
							int k=packData.nghb(w,v);
							int[] faceFlower=packData.getFaceFlower(w);
							if (k<packData.countFaces(w)) {
								f=faceFlower[k];
								if (hits[f]==0) {
									add(f);
									count++;
									hits[f]=1;
								}
							}
						}
					}
					break;
				}
				case 'e': // faces containing the given edges
				{
					EdgeLink edgelist=new EdgeLink(packData,items);
					its=null; // eat rest of items
					if (edgelist==null || edgelist.size()==0) break;
					Iterator<EdgeSimple> elist=edgelist.iterator();
					EdgeSimple edge=null;
					int v,w,k,f;
					while (elist.hasNext()) {
						edge=(EdgeSimple)elist.next();
						v=edge.v;
						w=edge.w;
						k=packData.nghb(v, w);
						int[] faceFlower=packData.getFaceFlower(v);
						if (k<packData.countFaces(v)) {
							f=faceFlower[k];
							if (hits[f]==0) {
								add(f);
								hits[f]=1;
								count++;
							}
						}
						k=packData.nghb(w,v);
						faceFlower=packData.getFaceFlower(w);
						if (k<packData.countFaces(w)) {
							f=faceFlower[k];
							if (hits[f]==0) {
								add(f);
								hits[f]=1;
								count++;
							}
						}
					}
					break;
				}
				} // end of switch
				break;
			}
			case 'F': // full drawing order, possibly stragglers too 
				// Notes:  
				//    * not all faces are used in the drawing order.
				//    * faces in list not necessarily contiguous
			{
				boolean debg=false; // degb=true;
				if (debg) LayoutBugs.log_faceOrder(packData);

				int nf=packData.firstFace;
				int []futil=new int[packData.faceCount+1];
				if (nf>0) {
					add(nf);
					futil[nf]=1;
					count++;
				}
				int safty = 0;
				while (nf > 0
						&& nf <= packData.faceCount
						&& (nf = packData.faces[nf].nextFace)<=packData.faceCount
						&& nf!= packData.firstFace
						&& safty < (2 + packData.faceCount)) {
					add(nf);
					futil[nf]=1;
					count++;
				}
				
				// pick up stragglers? yes, if flag is 'Fs'
				if (str.length()>1 && str.charAt(1)=='s') {
					for (int j=1;j<=packData.faceCount;j++) { 
						if (futil[j]==0) {
							add(j);
							count++;
						}
					}
				}
				break;
			}
			case 'z': // faces containing (x,y) point
			case 'Z': // on sphere, use actual (theta,phi) coords
			{
				try {
					PointLink zlink=new PointLink(items);
					Iterator<Complex> zl=zlink.iterator();
					while (zl.hasNext()) {
						Complex zz=zl.next();
						if (packData.hes>0 && str.charAt(0)=='z') {
							zz=SphView.visual_plane_to_s_pt(zz);
							zz=packData.cpScreen.sphView.toRealSph(zz);
						}
						FaceLink zsearch=packData.tri_search(zz);
						Iterator<Integer> nl=zsearch.iterator();
						while (nl.hasNext()) {
							add((Integer)nl.next());
							count++;
						}
					}
				} catch(Exception ex) {}
				break;
			}
			case '{': // set-builder notation; reap results
			{
				SetBuilderParser sbp=new SetBuilderParser(packData,str,02);
				if (!sbp.isOkay()) return 0;
				Vector<SelectSpec> specs=sbp.getSpecVector();
				PackData qackData=sbp.packData;
				FaceLink nl=qackData.facesSpecs(specs);
				if (nl!=null && nl.size()>0)
					this.addAll(nl);
				if (nl!=null)
					count+=nl.size();
				break;
			}
			case 'G': // face chain approximating the given curve (x,y),...
			{
				if (packData.hes!=0) {
					throw new ParserException("path lists only available in euclidean cases");
				}
				int startFace=0;
				// option 'Gf': start with given face
				if (str.length()>1 && str.charAt(1)=='f') {
					try {
						startFace=Integer.parseInt((String)items.remove(0));
						if (startFace<1 || startFace>packData.faceCount)
							throw new ParserException("usage: Gf <f>");
					} catch (Exception ex) {
						throw new ParserException(ex.getMessage());
					}
				}
				
				try {
					PathLink pLink=new PathLink(packData.hes,items);
					PathInterpolator pInt=new PathInterpolator(packData.hes);
					pInt.pathInit(pLink);
					FaceParam fP=FaceLink.pathProject(packData,pInt,startFace);
					FaceParam ftrace=fP;
					while (ftrace.next!=null) {
						add(ftrace.face);
						ftrace=ftrace.next;
						count++;
					}
					add(ftrace.face);
					count++;
				} catch (Exception ex) {
					throw new ParserException("failed to get or convert path");
				}
			}
			default: // if nothing else, see if it's a face index
			{
				try{
					int f=MathUtil.MyInteger(str);
					if (f>0 && f<=facecount) {
						add(f);
						count++;
					}
				} catch (Exception nfe) {
					return count;
				}
			}
			} // end of switch
			} // end of else
		} // end of while
				
		return count;
	}
	
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the indices.
	 * @return NodeLink
	 */
	public FaceLink makeCopy() {
		Iterator<Integer> flist=this.iterator();
		FaceLink newlist=new FaceLink((String)null);
		while (flist.hasNext()) {
			newlist.add((Integer)flist.next());
		}
		return newlist;
	}
	
	/**
	 * Abut a 'FaceLink' to the end of this one.
	 * @param moreFL
	 * @return count of new face indices (some may be improper, some redundant)
	 */
	public int abutMore(FaceLink moreFL) {
		if (moreFL==null || moreFL.size()==0)
			return 0;
		int ticks=0;
		Iterator<Integer> mit=moreFL.iterator();
		int v=0;
		while (mit.hasNext()) {
			v=(Integer)mit.next();
			add(v);
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Pick first vertex number off a string. Return 0 on failure.
	 * @param p
	 * @param str
	 * @return
	 */
	public static int grab_one_face(PackData p,String str) {
//		int idx=str.trim().indexOf(' ');
//		if (idx>0) {
//			str=str.substring(0,idx+1);
//		}
		FaceLink flist=new FaceLink(p,str);
		if (flist.size()>0) return flist.get(0);
		return 0;
	}
	
	public static int grab_one_face(PackData p,Vector<Vector<String>> flagsegs) {
		Vector<String> its=(Vector<String>)flagsegs.get(0);
		String str=(String)its.get(0);
		return grab_one_face(p,str);
	}
	
	/**
	 * Is 'f' an entry?
	 * @param f
	 * @return int, index or -1 if error or not found
	 */
	public int containsV(int f) {
		for (int j=0;j<this.size();j++)
			if ((int)this.get(j)==f)
				return j;
		return -1;
	}
		
	/**
	 * Count my elements (without repeats)
	 * @return int count; -1 on error
	 */
	public static int countMe(FaceLink nl) {
		int count=0;
		if (nl==null || nl.size()==0) return count;
		int max=1;
		if (nl.packData!=null) max=nl.packData.faceCount;
		// if no packing, have to calculate max
		else {
			Iterator<Integer> it=nl.iterator();
			while (it.hasNext()) {
				int f=it.next();
				max=(f>max) ? f:max;
			}
		}
		int []checks=new int[max+1];
		Iterator<Integer> it=nl.iterator();
		while (it.hasNext()) {
			int f=it.next();
			if (checks[f]==0) {
				checks[f]=1;
				count++;
			}
		}
		return count;
	}
	
	/**
	 * If @see FaceLink is a closed face list and 'indx' points to
	 * entry f, then rotate, returning a new closed FaceLink
	 * starting and ending with f.
	 * @param link @see FaceLink
	 * @param indx new starting index
	 * @return @see FaceLink, null if empty, not closed, or on error.
	 */
	public static FaceLink rotateMe(FaceLink link,int indx) {
		int sz=0;
		if (link==null || (sz=link.size())==0 || link.get(0).equals(link.get(sz-1)))
			return null;
		FaceLink nlink=new FaceLink();
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
	 * Return a new 'FaceLink' whose order is the reverse of this
	 * @return new 'FaceLink', null if this is empty.
	 */
	public FaceLink reverseMe() {
	    FaceLink qtmp=new FaceLink(packData);
	    if (this.size()==0) return null;
	    Iterator<Integer> it=this.iterator();
	    while (it.hasNext()) {
	    	qtmp.add(0,(Integer)it.next());
	    }
	    return qtmp;
	}
	
	/**
	 * Given a polygonal path and a packing, return a 'FaceParam' linked list
	 * of faces approximating the path. Should be closed if the path is
	 * closed. This is an alternate to 'path2faceparam' in that it
	 * follows the path locally, so it can be used to follow paths on
	 * multisheeted packings or run up to the edge for paths that 
	 * exit the carrier. NOTE: a closed path does not necessarily lead
	 * to a closed facelist on a multi-sheeted packing.
	 * 
	 * Need to:
	 * (a) find initial face --- set it as 'firm'. May need to designate
	 *     this specifically to get started (e.g., when starting at center
	 *     of a branched circle.)
	 * (b) when in face f, take steps of size 'step' along the path until:
	 *     1) ngbh face g is reached; 2) hold g until a face h not
	 *     sharing a vert with f is reached; 2) 'recurseMideFP' to 
	 *     process from f to h. (Worried about accidentally zigging
	 *     in and out of f.) 3) now we can legitimately return to 
	 *     hit f; 
	 * @param p
	 * @param pInt, PathInterpolator
	 * @param startFace, int, if not zero, try this as first face
	 * @return FaceParam, linked list
	 */
	public static FaceParam pathProject(PackData p,PathInterpolator pInt,int startFace) {
		if (!p.status || pInt==null || p.hes!=pInt.hes)
			throw new ParserException("Problem with packing, path, or geometries don't match");
		if (p.hes!=0) 
			throw new ParserException("Hyperbolic/Spherical cases not yet handled");
		
		if (pInt.length<=0.0) {
			if (startFace>1 && startFace<=p.faceCount) { // adjust pInt
				pInt.pathZ.insertElementAt(p.face_center(startFace),0);
				pInt.domain.add(pInt.pathZ.get(0).minus(pInt.pathZ.get(1)).abs());
				pInt.length=pInt.domain.lastElement();
				pInt.N=2;
			}
			else {
				CirclePack.cpb.errMsg("path has only one entry");
				return null;
			}
		}
		// get start complex points
		Complex startZ=pInt.sToZ(0.0);
		FaceParam startFP=null;
		
		// have to set first face
		FaceParam nextFP=null;
		FaceLink startLink=null;
		try {
			startLink=p.tri_search(startZ);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("path start not in any face");
			return null;
		}
		
		// if 'startFace' is given, see if it contains a point of the path
		if (startFace>0) { 
			boolean hit=true;
			int nghb_face=0;
			if (p.pt_in_tri(startFace,startZ)==0) {
				hit=false;
				Iterator<Integer> its=startLink.iterator();
				while (!hit && its.hasNext()) {
					nghb_face=its.next();
					if (p.face_vert_share(nghb_face,startFace)>0) // this is neighbor
						hit=true;
				}
					
				// if 'startZ' not in 'startFace', but is in 'nghb_face',
				//    then we still start with 'startFace', use center as
				//    'Z', but then need next FaceParam for 'nghb_face'.
				if (hit) { 
					startFP=new FaceParam();
					startFP.face=startFace;
					startFP.param=0.0;
					startFP.Z=p.face_center(startFace);
					startFP.firm=true;
					startFP.next=nextFP=new FaceParam();
					nextFP.face=nghb_face;
					nextFP.param=.000001; // just to be different than 0.0
					nextFP.Z=startZ;
					nextFP.next=null;
				}
				// otherwise, forget startFace
				else startFace=0;
			}
			else { // yes, startZ is in given startFace
				startFP=new FaceParam();
				startFP.face=startFace;
				startFP.param=0.0;
				startFP.Z=startZ;
				startFP.firm=true;
			}
		}
		
		// If 'startFace' is (or was reset to) 0, use first face in 'startLink'
		if (startFace<=0) { 
			startFP=new FaceParam();
			startFP.face=startLink.get(0);
			startFP.param=0.0;
			startFP.Z=startZ;
			startFP.firm=true;
			startFP.next=null;
		}
		
		// ========= Now we have the first face 
		
		// if we already have a next; have to get intervening faces
		if (startFP.next!=null) {
			if (p.face_nghb(startFP.face,startFP.next.face)>0) {
				nextFP=startFP.next;
			}
			else {
				int v=p.face_vert_share(startFP.face,startFP.next.face);
				if (v<=0) {
					CirclePack.cpb.errMsg("error in starting face list");
					return null;
				}
				int num=p.countFaces(v);
				int js=-1;
				int jn=-1;
				for (int j=0;j<num;j++) {
					if (p.getFaceFlower(v,j)==startFP.face) 
						js=j;
					if (p.getFaceFlower(v,j)==startFP.next.face)
						jn=j;
				}
				
				nextFP=startFP;
				FaceParam holdFP=startFP.next;
				int[] faceFlower=p.getFaceFlower(v);
				if (p.isBdry(v)) { // boundary case
					if (js<jn) {
						for (int j=js+1;j<jn;j++) {
							nextFP=nextFP.next=new FaceParam();
							nextFP.face=faceFlower[j];
							nextFP.param=.000001;  // fake
							nextFP.Z=p.face_center(nextFP.face); // fake 
						}
					}
					else {
						for (int j=js-1;j>jn;j--) {
							nextFP=nextFP.next=new FaceParam();
							nextFP.face=faceFlower[j];
							nextFP.param=.000001;  // fake
							nextFP.Z=p.face_center(nextFP.face); // fake 
						}
					}
					nextFP=nextFP.next=holdFP; // reattach the known end one
				}
				else { // interior case
					if ((jn+num-js)%num<(int)(num/2)) { // go counterclockwise
						int jj=(js+1)%num;
						while (jj<jn) {
							nextFP=nextFP.next=new FaceParam();
							nextFP.face=faceFlower[jj];
							nextFP.param=.000001;  // fake
							nextFP.Z=p.face_center(nextFP.face); // fake 
							jj=(jj+1)%num;
						}
					}
					else { // go clockwise
						int jj=(js-1+num)%num;
						while (jj>jn) {
							nextFP=nextFP.next=new FaceParam();
							nextFP.face=faceFlower[jj];
							nextFP.param=.000001;  // fake
							nextFP.Z=p.face_center(nextFP.face); // fake 
							jj=(jj-1+num)%num;
						}
					}
					nextFP=nextFP.next=holdFP; // reattach the known end one
				}
			}
		} 
		else
			nextFP=startFP;
		
		// Note: At this point, should have a chain from 'startFP' and
		//   'nextFP' should have legitimate 'Z' and legitimate 'param'.

		double param=nextFP.param;
		int nextFace=nextFP.face;
		
		while (nextFP!=null && param<pInt.length && nextFace>0) {
			FaceParam fP=prolongFP(p,nextFP,pInt);

			// If we have finished the path
			if (fP==null) 
				return startFP;

			// or we reached end; check for closure
			if (fP.face<=0 || fP.param==pInt.length) {  
				int fAce=Math.abs(fP.face);
				fP.face=fAce;
				// try to get fan of new 'FaceParam's to close up
				if (pInt.closed && fAce!=startFP.face) {
					fP.next=new FaceParam();
					fP.next.face=startFP.face;
					fP.next.param=fP.param+.00001; // fake
					fP.next.Z=pInt.sToZ(0.0);
					FaceParam finalFP=filloutFan(p,fP);
					if (finalFP==null) fP.next=null; // no luck; take what we have 
				}
				return startFP;
			}
			nextFP=fP;
		}
		return startFP;
	}
	
	/**
	 * Prolonging a chain to follow a path. Given the last 'FaceParam' 
	 * element in a chain, add links (with successively distinct faces)
	 * until reaching one not incident to the first; call the last
	 * element with incident face 'midFP', and the first following
	 * element with non-incident face 'nextFP'.
	 * 
	 * Situations:
	 * 1. If we find such a 'nextFP', we eliminate the chain between 
	 * 'initFP', 'midFP' and apply 'filloutFan' to get a new chain.
	 * Then we apply 'filloutFan' to extend this chain to end at 'nextFP';
	 * success, return 'nextFP' so we can repeat.
	 * 
	 * 2. If the chain runs off the edge, we eliminate the chain between 
	 * 'initFP', 'midFP' and apply 'filloutFan' to get a new chain. Also,
	 * extend chain so final face contains a boundary edge.
	 * Return null.
	 * 
	 * 3. If the chain reaches the end of the path, we eliminate the 
	 * chain between 'initFP', 'midFP' and apply 'filloutFan' to get 
	 * a new chain. Return 'midFP', but with 'midFP.face' set to
	 * negative. The calling routine decides what to do; e.g., the
	 * last face might equal the first, so if the path is closed,
	 * we want to close up.
	 * @param p
	 * @param initFP, latest FaceParam
	 * @param pInt, PathInterpolator
	 * @return FaceParam element or null if one is not found
	 */
	public static FaceParam prolongFP(PackData p,FaceParam initFP,PathInterpolator pInt) {
		FaceParam nextFP=null;
		FaceParam midFP=null;
		int initFace=initFP.face;
		
        // Go through successively incident faces until reaching first not 
		//    incident to initFace
		
		int nextFace=initFace;
		int pastFace=initFace;
		double param=initFP.param;
		double pastparam=initFP.param;
		while (nextFace !=0 && param<pInt.length && p.faces_incident(nextFace,initFace)) {
			pastFace=nextFace;
			pastparam=param;
			double []getNext=next_incident_face(p,nextFace,param,pInt);
			nextFace=(int)getNext[0];
			param=getNext[1];
		} // end of while; 'pastFace' should be last incident face encountered
		
		// successes??
		
		// ran off edge? prolong to end with a face having a bdry edge
		if (nextFace==0 && param<pInt.length) {
			// prolong with fan to midFP first
			if (pastFace!=initFace) { 
				midFP=new FaceParam();
				midFP.next=null;
				midFP.face=pastFace;
				midFP.param=pastparam;
				midFP.Z=pInt.sToZ(pastparam);
				initFP.next=midFP;
				
				FaceParam returnFP=filloutFan(p,initFP); 
				if (returnFP==null) 
					throw new CombException("projectFP error in ending path");
				initFP=midFP;
				param=pastparam;
			}
			
			// should have at least one bdry vert in 'pastFace'.
			int []vert=p.getFaceVerts(pastFace);
			if (!p.isBdry(vert[0]) && !p.isBdry(vert[1]) &&
					        !p.isBdry(vert[2])) {
				CirclePack.cpb.errMsg("Did not seem to reach the bdry");
				return null; // hum?? 'pastFace' not on bdry.
			}

			int cnt=0;
			for (int j=0;j<3;j++)
				if (p.isBdry(vert[j])) cnt ++;
			if (cnt>=2) return null; // 'pastFace' is bdry face? okay, done
 			
			// else find a vert from 'pastFace' on the bdry
			int indx=-1;
			for (int j=0;j<3;j++)
				if (p.isBdry(vert[j])) indx=j;
			int bvert=vert[indx];
			indx=-1;
			for (int j=0;j<p.countFaces(bvert);j++)
				if (p.getFaceFlower(bvert,j)==pastFace)
					indx=j;
			// add clockwise chain to reach face containing a bdry edge
			nextFP=initFP;
			for (int j=indx-1;j>=0;j--) {
				param=nextFP.param;
				nextFP=nextFP.next=new FaceParam();
				nextFP.face=p.getFaceFlower(bvert,j);
				nextFP.next=null;
				nextFP.param=param+.0001; // fake
				nextFP.Z=p.face_center(nextFP.face); // fake
			}
			return null; 
		}
		
		// reached end of path
		if (nextFace==0) {  
			midFP=new FaceParam();
			midFP.next=null;
			midFP.param=pInt.length;
			midFP.Z=pInt.pathZ.lastElement();
			initFP.next=midFP;
			
			if (pastFace!=initFace) { // incident faces; can add more links
				midFP.face=pastFace;
				FaceParam returnFP=filloutFan(p,initFP); // get fan
				if (returnFP==null) 
					throw new CombException("projectFP error in ending path");
				return returnFP;
			}
			midFP.face=-initFace; // calling routine decides on what to do
			return midFP;
		}
			
		// reached non-incident face 'nextFace', but need to reach 'midFace' first
		midFP=new FaceParam();
		midFP.next=null;
		midFP.face=pastFace;
		midFP.param=pastparam;
		midFP.Z=pInt.sToZ(pastparam);
		initFP.next=midFP; 
		
		midFP=filloutFan(p,initFP); // get fan
		if (midFP==null) 
			throw new CombException("projectFP error in ending path");

		// now to go for 'nextFace'
		nextFP=new FaceParam();
		nextFP.param=param;
		nextFP.face=nextFace;
		nextFP.next=null;
		nextFP.Z=pInt.sToZ(param);
		midFP.next=nextFP;

		FaceParam returnFP=filloutFan(p,midFP); // get fan
		if (returnFP==null) 
			throw new CombException("projectFP error in ending path");
		return returnFP;
	}
	
	/**
	 * Given 'FaceParam', if 'FaceParam.face' and 'FaceParam.next.face' 
	 * are incident, build chain of 'FaceParam's between them (with
	 * fake 'param', 'Z' values). Return the final 'FaceParam' on success
	 * or null on failure. 
	 * @param p
	 * @param initFP
	 * @return
	 */
	public static FaceParam filloutFan(PackData p,FaceParam initFP) {
		if (initFP==null || initFP.next==null) return null;
		int face=initFP.face;
		int nface=initFP.next.face;
		if (face<=0 || nface<=0) return null;
		if (face==nface) {  // same face? don't resolve redundancy, just return
			return initFP.next;
		}
		int vert=-1;
		if ((vert=p.face_vert_share(face,nface))<0) return null; // not incident
		if (p.face_nghb(face,nface)>=0) return initFP.next; // share an edge: done

		int indx1=-1;
		int indx2=-1;
		for (int j=0;j<p.countFaces(vert);j++) {
			if (p.getFaceFlower(vert,j)==face)
				indx1=j;
			else if (p.getFaceFlower(vert,j)==nface)
				indx2=j;
		}
		if (indx1<0 || indx2<0 || indx1==indx2) return null;
		
		FaceParam nextFP=initFP;
		
		// boundary case
		if (p.isBdry(vert)) { 
			int[] faceFlower=p.getFaceFlower(vert);
			if (indx1<indx2) { // counterclockwise
				for (int j=indx1+1;j<indx2;j++) {
					FaceParam holdFP=nextFP.next;
					double holdparam=nextFP.param;
					nextFP=nextFP.next=new FaceParam();
					nextFP.next=holdFP;
					nextFP.face=faceFlower[j];
					nextFP.param=(holdparam+nextFP.next.param)/2.0;  // fake
					nextFP.Z=p.face_center(nextFP.face); // fake 
				}
			}
			else { // clockwise
				for (int j=indx1-1;j>indx2;j--) {
					FaceParam holdFP=nextFP.next;
					double holdparam=nextFP.param;
					nextFP=nextFP.next=new FaceParam();
					nextFP.next=holdFP;
					nextFP.face=faceFlower[j];
					nextFP.param=(holdparam+nextFP.next.param)/2.0;  // fake
					nextFP.Z=p.face_center(nextFP.face); // fake 
				}
			}
		}
		
		// interior case
		else { 
			int num=p.countFaces(vert);
			int[] faceFlower=p.getFaceFlower(vert);
			// Take shortest fan, bias towards CLOCKWISE (because when used 
			//      to get edge path, we use the left side edgepath.
			if ((indx2+num-indx1)%num<(int)(num/2)) { // go counterclockwise
				int jj=(indx1+1)%num;
				while (jj<indx2) {
					FaceParam holdFP=nextFP.next;
					double holdparam=nextFP.param;
					nextFP=nextFP.next=new FaceParam();
					nextFP.next=holdFP;
					nextFP.face=faceFlower[jj];
					nextFP.param=(holdparam+nextFP.next.param)/2.0;  // fake
					nextFP.Z=p.face_center(nextFP.face); // fake 
					jj=(jj+1)%num;
				}
			}
			else { // go clockwise
				int jj=(indx1-1+num)%num;
				while (jj!=indx2) {
					FaceParam holdFP=nextFP.next;
					double holdparam=nextFP.param;
					nextFP=nextFP.next=new FaceParam();
					nextFP.next=holdFP;
					nextFP.face=faceFlower[jj];
					nextFP.param=(holdparam+nextFP.next.param)/2.0;  // fake
					nextFP.Z=p.face_center(nextFP.face); // fake 
					jj=(jj-1+num)%num;
				}
			}
		}
		
		return nextFP.next; // this should be the end of the chain
	}
	
	/**
	 * In face f at parameter 'param'. Move along the path until you encounter
	 * the next incident face (and you leave f). Return vector ans[]:
	 *    ans[0]=next face index
	 *    ans[1]=new param value
	 * ans[0] if none is found 
	 * Idea: starting in f, find min radius among verts of f and 
	 * contiguous faces. Take steps 1/3 this until we succeed or reach edge
	 * of carrier or end of path.
	 * @param p
	 * @param face
	 * @param param
	 * @param pInt
	 * @return, ans[0]=0, if none found
	 */
	public static double []next_incident_face(PackData p,int f,
			double param,PathInterpolator pInt) {
		double []ans=new double[2];
		
		// find min of radii of f and contiguous faces
		int initFace=f;
		int[] fverts=p.getFaceVerts(f);
		double step=p.getRadius(fverts[0]);
		double rad=p.getRadius(fverts[1]);
		if (rad<step) step=rad;
		rad=p.getRadius(fverts[2]);
		if (rad<step) step=rad;
		
		int v=p.find_common_left_nghb(fverts[1],fverts[0]);
		if (v>0 && (rad=p.getRadius(v))<step) 
			step=rad;
		v=p.find_common_left_nghb(fverts[2],fverts[1]);
		if (v>0 && (rad=p.getRadius(v))<step) 
			step=rad;
		v=p.find_common_left_nghb(fverts[0],fverts[2]);
		if (v>0 && (rad=p.getRadius(v))<step) 
			step=rad;
		
		if (step<=0)
			throw new DataException("problem with radii of face or neighbors");
		
		step/=3; // this should guarantee that we don't step over all incident faces
		
		Complex z=null;
		while (param<pInt.length) {
			param += step;
			if (param>pInt.length) param=pInt.length;
			z=pInt.sToZ(param);
			if (p.pt_in_tri(f,z)==0) { // no longer in f
				FaceLink flink=p.tri_search(z);
				if (flink==null || flink.size()==0) { // not in any face
					ans[0]=0;
					ans[1]=param;
					return ans;
				}
				Iterator<Integer> fls=flink.iterator();
				while (fls.hasNext()) {
					int fs=fls.next();
					if (p.faces_incident(fs,f)) {
						ans[0]=fs;
						ans[1]=param;
						return ans;
					}
				}
				
				// no incident face found
				ans[0]=0;
				ans[1]=param;
				return ans;
			}
		} // end of stepping while

		// nothing found
		ans[0]=0;
		ans[1]=param;
		return ans;
	}

	 /**
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }

	/**
     * Return random entry from facelist; caution, does not adjust
     * for repeat entries.
     * @param facelist
     * @return -1 on error
     */
    public static int randFace(FaceLink facelist) {
    	if (facelist==null || facelist.size()==0) return -1;
    	int n=new Random().nextInt(facelist.size());
    	return facelist.get(n);
    }

}

