package komplex;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import dcel.HalfEdge;
import dcel.PackDCEL;
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.ParserException;
import listManip.EdgeLink;
import listManip.NodeLink;
import listManip.OverlapLink;
import listManip.VertList;
import listManip.VertexMap;
import math.Point3D;
import packing.PackData;
import packing.RData;
import panels.PathManager;
import util.BuildPacket;
import util.StringUtil;

/**
 * This is a specialized class created for calls to 'cookie' and
 * containing specialized combinatoric routines. It first creates a
 * clone of the packing and any poison lists (saving aside various other 
 * lists); the calling routine checks 'outcomeFlag' to find the results: 
 * 0 initially, 1 on success in creating this monster, negative for 
 * various failures. (In future, might give further input and proceed, 
 * but for now, the class is instantiated, tries to do its thing, then 
 * parent checks outcomeFlag and kills this monster if it didn't work.)
 * @author kens
  */
public class CookieMonster {
	PackData monsterPackData;
	int orig_nodeCount;
	NodeLink vlist;
	EdgeLink elist;
	EdgeLink vertexMap;
	Point3D []xyz_data;
	public int[] new2old;  // for new index j, new2old[j] gives former index
	public int[] old2new;  // for old index k, old2new[k] gives new index (or zero if k was cut)
	OverlapLink overlaps;
	int seed,hold_alpha;
	int []cmPoison;
	
	int outcomeFlag;  // this can be tested for status of this class

	// Constructors
	public CookieMonster(PackData p, Vector<Vector<String>> flagSegs) {
		outcomeFlag=0;
		orig_nodeCount=p.nodeCount;
		// clone the packing and poison lists
		monsterPackData=p.copyPackTo(); 
		if (p.poisonEdges!=null) 
			monsterPackData.poisonEdges=new EdgeLink(p,"P");
		if (p.poisonVerts!=null) 
			monsterPackData.poisonVerts=new NodeLink(p,"P");
		new2old=null;
		old2new=null;
		xyz_data=null;
		overlaps = null;
		cmPoison=null;
		seed=hold_alpha=p.alpha;

		// save [vfe]list, vertexMap, etc.
		saveData();
		
		// copy packing
		
		// Process the commands to set seed, poison verts/edges in cmUtil
		try {
			outcomeFlag=parseCookieData(flagSegs);
		} catch (Exception ex) {
			outcomeFlag=-1;
			throw new ParserException("error in seed/poison vertices: "+ex.getMessage());
		}
	}
	
	/**
	 * Instantiate with String; break into flag segments
	 * @param p PackData
	 * @param datastr String
	 */
	public CookieMonster(PackData p,String datastr) {
		this(p,StringUtil.flagSeg(datastr));
	}
	
	/**
	 * Return the packing we created here in the monster. Calling routine
	 * may need to reattach this to the correct @see CPScreen.
	 * @return @see PackData
	 */
	public PackData getPackData() {
		return monsterPackData;
	}
	
	/**
	 * Save what data you can from packData and restore later (adjusted if
	 * changed, same if packData reverts to original.)
	 */
	void saveData() {
		// save original data: overlaps, xyz, vlist, elist (flist is invalidated)
		if (monsterPackData.overlapStatus) {
			overlaps = new OverlapLink(monsterPackData);
			double oangle;
			int ocount=0;
			for (int v = 1; v <= monsterPackData.nodeCount; v++)
				for (int j=0;j<(monsterPackData.kData[v].num + monsterPackData.kData[v].bdryFlag);j++)
					if (v < monsterPackData.kData[v].flower[j]
							&& (oangle=monsterPackData.kData[v].overlaps[j])!=1.0) { // non-default?
						overlaps.add(new EdgeMore(v,monsterPackData.kData[v].flower[j], oangle));
						ocount++;
					}
			if (ocount == 0)
				overlaps = null; // no overlaps 
		}

		// save xyz data to avoid losing it in alloc_pack_space.
		xyz_data = monsterPackData.xyzpoint;
		monsterPackData.xyzpoint = null;
		vlist=monsterPackData.vlist;
		monsterPackData.vlist=null;
		elist=monsterPackData.elist;
		// TODO: what about 'glist'?
		monsterPackData.elist=null;
		vertexMap=monsterPackData.vertexMap;
		monsterPackData.vertexMap=null;
	}
	
	/**
	 * Once 'CookieMonster' is instantiated, this calls the actual cookie
	 * action and returns 'outcomeFlag'. Messages and exceptions may also
	 * occur. After checking, the calling routine should normally garbage
	 * this 'CookieMonster'. 
	 * 
	 * @return int outcomeFlag: <0 means error; =0 means no verts cut;
	 * positive gives nodecount, call 'getPackData' to get the
	 * resulting (smaller) packing.
	 */
	public int goCookie() {
		
		outcomeFlag=cookie_cutter();
		
		// nothing to do if there are no poisons (which may be okay) 
		if (outcomeFlag==0)
			return 0;
		
		// no vertices cut? Calling routine
		if (outcomeFlag==orig_nodeCount)
			return 0;

		// finish restoring and processing the new complex
		if (outcomeFlag>0) {
			restoreData();
			if ((monsterPackData.alpha=old2new[seed])==0) monsterPackData.chooseAlpha();
			if ((monsterPackData.gamma=old2new[monsterPackData.gamma])==0) monsterPackData.chooseGamma();

			monsterPackData.setCombinatorics();
			monsterPackData.fillcurves();
		}
		return outcomeFlag;
	}

	/**
	 * Cookie using zigzag idea with 'ClosedPath'.
	 * @return int nodecount
	 */
	public int zigzagCookie() {
		
		try {
			Triangulation Tri=zigzag_cutter(monsterPackData.getTriangulation(),CPBase.ClosedPath);
			monsterPackData=Triangulation.tri_to_Complex(Tri, 0);
		} catch (Exception ex) {
			throw new CombException("tri_to_complex error: "+ex.getMessage());
		}
		monsterPackData.setCombinatorics();
		monsterPackData.fillcurves();
		return monsterPackData.nodeCount;
	}

	/**
	 * After 'cookie', need to restore various data using new
	 * indexing. We also set the vertexMap <new, old> indices
	 */
	public void restoreData() {
		// set up vertex_map: {j,k} converts new to old indices
		monsterPackData.vertexMap = new VertexMap();
		if (new2old != null) {
			int k;
			for (int j = 1; j <= monsterPackData.nodeCount; j++)
				if ((k = new2old[j]) != 0)
					monsterPackData.vertexMap.add(new EdgeSimple(j, k));
		}

		// restore former xyz data 
		monsterPackData.xyzpoint = xyz_data;
		if (monsterPackData.xyzpoint != null && monsterPackData.vertexMap != null) { // reset xyz data 
			Point3D[] new_xyz = new Point3D[monsterPackData.nodeCount + 1];
			Iterator<EdgeSimple> pxyz = monsterPackData.vertexMap.iterator();
			EdgeSimple edge = null;
			while (pxyz.hasNext()) {
				edge = (EdgeSimple) pxyz.next();
				new_xyz[edge.v] = (Point3D) monsterPackData.xyzpoint[edge.w];
			}
			monsterPackData.xyzpoint = new_xyz;
		}

		// reset saved overlaps 
		if (overlaps != null && monsterPackData.alloc_overlaps() != 0) {
			EdgeMore edgeM;

			// First, change indices new values, toss those not in new complex.
			Iterator<EdgeMore> nlap = overlaps.iterator();
			int v, w;
			while (nlap.hasNext()) {
				edgeM = (EdgeMore) nlap.next();
				v = old2new[edgeM.v];
				w = old2new[edgeM.w];
				if (v == 0 || w == 0) // throw out
					nlap.remove();
				else {
					edgeM.v = v;
					edgeM.w = w;
				}
			}

			// Now store in packData
			nlap = overlaps.iterator();
			int indx;
			while (nlap.hasNext()) {
				edgeM = (EdgeMore) nlap.next();
				indx = monsterPackData.nghb(edgeM.v, edgeM.w);
				monsterPackData.kData[edgeM.v].overlaps[indx] = edgeM.overlap;
				if (monsterPackData.kData[edgeM.v].bdryFlag == 0 && indx == 0)
					monsterPackData.kData[edgeM.v].overlaps[monsterPackData.kData[edgeM.v].num] = edgeM.overlap;
				indx = monsterPackData.nghb(edgeM.w, edgeM.v);
				monsterPackData.kData[edgeM.w].overlaps[indx] = edgeM.overlap;
				if (monsterPackData.kData[edgeM.w].bdryFlag == 0 && indx == 0)
					monsterPackData.kData[edgeM.w].overlaps[monsterPackData.kData[edgeM.w].num] = edgeM.overlap;
			}
		} 
		
		// TODO: reattach vlist, elist but with new indices. (Was this done elsewhere?)
		
		return;
	}

	/**
	 * Process the incoming strings to set seed and poison vertices and edges:
	 * 
	 * If there is an input list of poison vertices, they should appear in 
	 *   first vector of strings in 'flags' without a preceding flag (cancels 
	 *   other poison settings).
	 * 
	 * Then check for flag segments:
	 * * Flags: -v {v}, for identifying a seed in place of 'alpha' (the default).
	 * * Flag -e {u v...} is poison edge list (cancels other poison settings).
     *
	 * If no verts are listed and poisonVerts was empty on entry, then the 
	 *   points on the side of 'ClosedPath' (if there is one) opposite to 'seed' 
	 *   are poison by default.
	 * 
	 * @param flags Vector<Vector<String>>; may be null
	 * @return int count of poisons
	 */
	int parseCookieData(Vector<Vector<String>> flags) {

		// read incoming data
		while (flags!=null && flags.size()>0) { 
			Vector<String> items=(Vector<String>)flags.remove(0);
			if (!StringUtil.isFlag(items.get(0))) { // not flag? must be poison vertices
				monsterPackData.poisonEdges=null;
				monsterPackData.poisonVerts=new NodeLink(monsterPackData,items);
			}
			else {
				String str=(String)items.get(0);
				if (str.equals("-v")) { // set seed
					if (items.size()<2) 
						throw new ParserException("cookie crumbed: error in -v flag");
					seed=Integer.parseInt((String)items.get(1));
					items.remove(1);
					items.remove(0);
				}
				else if (str.equals("-e")) { // get poison edges (kill any poison verts)
					if (items.size()<2) 
						throw new ParserException("cookie crumbed: error in -e flag");
					items.remove(0);
					monsterPackData.poisonVerts=null;
					monsterPackData.poisonEdges=new EdgeLink(monsterPackData,items);
				}
			}
		}
		
		// catalog the poison vertices
		cmPoison=new int[monsterPackData.nodeCount+1];
		boolean gotPoison=false;

		// first, those specified in lists
		if (monsterPackData.poisonVerts!=null && monsterPackData.poisonVerts.size()!=0) {
			Iterator<Integer> nlt=monsterPackData.poisonVerts.iterator();
			while (nlt.hasNext()) {
				int pv=(int)nlt.next();
				cmPoison[pv]=-1;
				gotPoison=true;
			}
			monsterPackData.poisonVerts=null;
		}
		if (monsterPackData.poisonEdges!=null && monsterPackData.poisonEdges.size()==0) {
			Iterator<EdgeSimple> elst=monsterPackData.poisonEdges.iterator();
			while (elst.hasNext()) {
				EdgeSimple edge=elst.next();
				cmPoison[edge.v]=-1;
				cmPoison[edge.w]=-1;
				gotPoison=true;
			}
			monsterPackData.poisonEdges=null;
		}
				
		// If no poisons so far, then use stored 'ClosePath'
		if (!gotPoison) {
			if (CPBase.ClosedPath==null) 
				throw new ParserException("cookie: No path defined.");
			boolean seed_wrap=PathManager.path_wrap(monsterPackData.rData[seed].center); // which side is seed on?
			for (int v=1;v<=monsterPackData.nodeCount;v++) {
				if (seed_wrap!=PathManager.path_wrap(monsterPackData.rData[v].center)) { 
					cmPoison[v]=-1;
					gotPoison=true;
				}
			}
			// also want to make immediate nghbs of outside vertices poison; recall,
			//   poisons do get included in cutout packing, we just don't loop around
			//    them. Set cmPoison to +1
			for (int v=1;v<=monsterPackData.nodeCount;v++) {
				for (int j=0;(j<=monsterPackData.kData[v].num && cmPoison[v]==0);j++) 
					if (cmPoison[monsterPackData.kData[v].flower[j]]==-1) cmPoison[v]=1;
			}
		}

		// no poison?
		if (!gotPoison) {
			CirclePack.cpb.errMsg("cookie: crumbed because no verts are poison");
			return 0;
		}
		
		// Remove any isolated poisons 
		for (int i=1;i<=monsterPackData.nodeCount;i++) {
			if (cmPoison[i]!=0)  {
				int k=0;
				for (int j=0;j<=monsterPackData.kData[i].num;j++) 
					if (cmPoison[monsterPackData.kData[i].flower[j]]!=0) k++;
				if (k==0) // no poison neighbors 
					cmPoison[i]=0;
			}
		}

		// seed can't be poison
		if (cmPoison[seed]<0) {
			cmPoison=null;
			CirclePack.cpb.errMsg("cookie: crumbed because seed is poison");
			return 0;
//	    	throw new ParserException("cookie: crumbled because seed "+seed+" is poison");
		}

		// fill 'poisonVerts', but only with verts having at least one non-poison neighbor.
		// Note: 'poisonVerts' is used in 'build_redchain', that's why we need poisonVerts,
		//   but checking for poison verts is very slow.
		monsterPackData.poisonVerts=new NodeLink(monsterPackData);
		for (int v=1;v<=monsterPackData.nodeCount;v++) {
			if (cmPoison[v]!=0) {
				int k=0;
				for (int j=0;(j<=monsterPackData.kData[v].num && k==0);j++) 
					if (cmPoison[monsterPackData.kData[v].flower[j]]==0) k++;
				if (k!=0) // v has non-poison neighbors 
					monsterPackData.poisonVerts.add(v);
			}
		}
		
		int ticks=monsterPackData.poisonVerts.size();
		if (ticks==0 || ticks==monsterPackData.nodeCount) {
			monsterPackData.poisonVerts=null;
			cmPoison=null;
			throw new ParserException("cookie: crumbed because no verts or all vertices are poison");
		}
		
		return ticks;
	}
	
	/**
	 * Redo the cookie routine: just build new flowers, renumber
	 * @return PackData, null on error
	 */
	public PackData cookie_out() {

		// make a copy
		PackData p=monsterPackData;
		
		// keep track of status: -1 means poison
		int []util=new int[p.nodeCount+1];
		Iterator<Integer> pv=p.poisonVerts.iterator();
		while (pv.hasNext()) 
			util[pv.next()]=-1;

		// connected component of non-poisons containing seed
		util[seed]=1;
		NodeLink curr=null;
		NodeLink next=new NodeLink(p,seed);
		while (next.size()>0) {
			curr=next;
			next=new NodeLink(p);
			Iterator<Integer> currit=curr.iterator();
			while (currit.hasNext()) {
				int v=currit.next();
				util[v]=1;
				for (int j=0;j<(p.kData[v].num+p.kData[v].bdryFlag);j++) {
					int k=p.kData[v].flower[j];
					if (util[k]==0) {
						next.add(k);
						util[k]=1;
					}
				}
			}
		}
		// vertices: keep if util=1; toss  if util<=0; 
		
		boolean debug=false; // debug=true;
		NodeLink vlst_keep=null;
		NodeLink vlst_kill=null;
		if (debug) {
			vlst_keep=new NodeLink();
			vlst_kill=new NodeLink();
			for (int kk=1;kk<=p.nodeCount;kk++) {
				if (util[kk]==1)
					vlst_keep.add(kk);
				if (util[kk]==-1)
					vlst_kill.add(kk);
			}
		}  // CPBase.Vlink=vlst_keep;  CPBase.Vlink=vlst_kill;   
		
		// temporary storage for new vertices and their flowers
		Vector<NewVertData> newFlowers=new Vector<NewVertData>(10);
		int []old2new=new int[p.nodeCount+1];
		NodeLink debugV=new NodeLink(); // debugging help
		
		// build NewVertData 'petals'
		int newIndx=1;
		for (int v=1;v<=p.nodeCount;v++) {
			if (util[v]>0) {
				int num=p.kData[v].num;

				Vector<Integer> jumps=new Vector<Integer>(1);
				for (int j=0;j<p.kData[v].num;j++) {
					int k=p.kData[v].flower[j];
					int kk=p.kData[v].flower[j+1];
					if (util[k]!=util[kk]) {
						if (util[k]==1) // jump to negative at j
							jumps.add(-j); 
						else { // jump to positive at j+1
							if ((j+1)==num && p.kData[v].bdryFlag==0)
								jumps.add(0);
							else 
								jumps.add(j+1);
						}
					}
				}

				// now determine if there are multiple fans of keepers
				if (jumps.size()>2) // ambiguity: would have to clone 
					util[v]=-1;
				
				else if (p.kData[v].bdryFlag==0) { // interior
					if (jumps.size()==1)
						throw new CombException("hum? too few jumps?");
					
					// easy case, all petals are in the new packing 
					else if (jumps.size()==0) {
						NewVertData nvd=new NewVertData();
						nvd.origIndx=v;
						nvd.newIndx=newIndx++;
						old2new[nvd.origIndx]=nvd.newIndx;
						debugV.add(nvd.origIndx); // debug
						nvd.petals=new int[p.kData[v].num+1];
						for (int jj=0;jj<=p.kData[v].num;jj++)
							nvd.petals[jj]=p.kData[v].flower[jj];
						nvd.num=p.kData[v].num;
						newFlowers.add(nvd);
					}
					else { // one fan, starts with J 
						int J=jumps.get(0);
						if (J<0)
							J=jumps.get(1);
						if (J<0)
							throw new CombException("bad jumps");
						NewVertData nvd=new NewVertData();
						nvd.origIndx=v;
						nvd.newIndx=newIndx++;
						old2new[nvd.origIndx]=nvd.newIndx;
						debugV.add(nvd.origIndx); // debug
						nvd.petals=new int[p.kData[v].num+1];
						int noc=0;
						int flag=util[p.kData[v].flower[(J+num)%num]];
						while (flag>0 && noc<num) {
							nvd.petals[noc]=p.kData[v].flower[(J+num)%num];
							J++;
							flag=util[p.kData[v].flower[(J+num)%num]];
							noc++;
						}
						nvd.num=noc;
						newFlowers.add(nvd);
					}
				}
				
				else { // bdry
				  
					// jump to -1 and back or jump to 1 at very end, then kill
					if (util[p.kData[v].flower[0]]==1 && jumps.size()==2 ||
							(jumps.size()==1 && jumps.get(0)==num) )
						util[v]=-1; // two jumps, start with keeper? remove 
					else {
					  int J=0;
					  if (jumps.size()>0)
						  J=jumps.get(0);
					  if (J<0)
						  J=0;
					  NewVertData nvd=new NewVertData();
					  nvd.origIndx=v;
					  nvd.newIndx=newIndx++;
					  old2new[nvd.origIndx]=nvd.newIndx;
					  debugV.add(nvd.origIndx); // debug
					  nvd.petals=new int[p.kData[v].num+1];
					  int noc=0;
					  int flag=util[p.kData[v].flower[(J+num)%num]];
					  while (flag>0 && noc<num) {
						  nvd.petals[noc]=p.kData[v].flower[(J+noc)%num];
						  flag=util[p.kData[v].flower[(J+noc)%num]];
						  noc++;
					  }
					  nvd.num=noc;
					  newFlowers.add(nvd);
				  }
				}
			}
		} // end of for loop through vertices
		// CPBase.Vlink=debugV;
		
		// create the new packing
		PackData newP=new PackData(null);
		newP.alloc_pack_space(newIndx+100,false);
		newP.nodeCount=newIndx-1;
		
		// create kData[.] and rData[.] elements
		Iterator<NewVertData> newit=newFlowers.iterator();
		while (newit.hasNext()) {
			NewVertData nvd=newit.next();
			int V=nvd.origIndx;
			int v=nvd.newIndx;
			newP.kData[v].num=nvd.num;
			newP.kData[v].flower=nvd.petals;
			if (newP.kData[v].flower[0]==newP.kData[v].flower[newP.kData[v].num])
				newP.kData[v].bdryFlag=0;
			else 
				newP.kData[v].bdryFlag=1;
			Color col=p.kData[V].color;
			newP.kData[v].color=new Color(col.getRed(),col.getGreen(),col.getBlue());
			newP.kData[v].mark=p.kData[V].mark;
			newP.kData[v].overlaps=null;
			
			newP.rData[v]=p.rData[V].clone();
		}
		
		// convert flowers to new indices
		for (int w=1;w<=newP.nodeCount;w++) {
			try {
				for (int jw=0;jw<=newP.kData[w].num;jw++) {
					int k=newP.kData[w].flower[jw];
					newP.kData[w].flower[jw]=old2new[k];
				}
			} catch (Exception ex) {
				throw new CombException("problem with newP flowers");
			}
		}
		
		// save conversions in VertexMap: order (new old) 
		newP.vertexMap=new VertexMap();
		for (int u=1;u<=p.nodeCount;u++)
			if (old2new[u]!=0)
				newP.vertexMap.add(new EdgeSimple(old2new[u],u));
						
		newP.status=true;
		newP.setCombinatorics();
		return newP;
	}
	
	 /** 
	  * 'Cookie' out part of 'monsterPackData' based on vertices 
	  * specified in its 'poisonVerts' and 'poisonEdges'. 
	  * On success, fill array 'new2old' to hold vertex conversions.
	  * @return int, new nodeCount or 0 on problem (e.g., sphere, no poison) 
	 */
	 public int cookie_cutter() {
	     int return_value=0;
	     boolean debug=false;

	     if (monsterPackData.locks!=0) 
	    	 return 0;
	     new2old=new int[monsterPackData.nodeCount+1];
	     monsterPackData.alpha=seed;
	     
	     /* call build_redchain to define core of the new complex 
	      * from seed, stopping at poison verts; need to process 
	      * the red chain, etc. */
	     BuildPacket bP=monsterPackData.redChainer.build_redchain(seed,true); // CPBase.pack[1].packData.poisonVerts=monsterPackData.poisonVerts.makeCopy();
	     if (!bP.success) {
	    	 CirclePack.cpb.errMsg("'build_redchain' "+
	    			 "failed, perhaps a sphere with no poison?");
	    	 return 0;
	     }
	     RedList redlist=bP.redList;
	     VertList face_order=bP.faceOrdering;  // CPBase.Flink=face_order.toFaceLink();
	     
	     if (debug) LayoutBugs.pfacered(monsterPackData); // debug=true;
	     
	     bP=monsterPackData.redChainer.redface_comb_info(redlist, true); // CPBase.Flink=LayoutBugs.redChain2redLink(redlist);
	     if (!bP.success) {
	       throw new CombException("Error in 'redface_comb_info'.");
	     }
	     redlist=bP.redList; 
	     
//	     if (debug) LayoutBugs.log_Red_Hash(packData,?????? redface, rededge)

	     // create new complex; may throw out some vertices, close others
	     monsterPackData.redChain=null;
	     monsterPackData.firstRedEdge=null;
	     if (rationalize_subcomplex(redlist,face_order)==0) { // LayoutBugs.redChain2redLink(redlist);
	    	 throw new CombException("Error rationalizing subcomplex in cookie.");
	     }
	     return_value=monsterPackData.nodeCount;
	     return return_value;
	 } // cookie_cutter 

	 /**
	  * Build the redchain of faces defining a core, as with 'cookie',
	  * however, do not use the chain for actually cutting the
	  * packing out. Typically use this to check the red chain.
	  * @return @see RedList
	  */
	 public RedList pre_cookie() {
	     monsterPackData.alpha=seed;
	     
	     /* call build_redchain to define core of the new complex from seed,
	        stopping at poison verts; need to process the red chain, etc.*/
	     BuildPacket bP=monsterPackData.redChainer.build_redchain(seed,true);
	     if (!bP.success) {
	    	 throw new CombException(bP.buildMsg);
	     }
	     return bP.redList;
	 }
	 
	 /**
		 * Given a red chain of faces in packData, throw out vertices outside
		 * it; also may need to clone some vertices. -- if they define two or
		 * more different boundary verts, if they are side-paired poison verts.
		 * Result is put back in packData, so packData could be corrupted on
		 * error. Return new nodecount, or zero on error. Set alpha to be
		 * 'seed', set 'new2old' for conversion of indices.
		 * 
		 * Main complication is cloning. Need to clone a vert whose flower is
		 * not completely in the subcomplex, moreover the part that is in the
		 * subcomplex may be broken into two or more subfans. In addition, if
		 * fan is partitioned for a 'poison' vert, (indicated by util[]=-1) then
		 * need to clone it. Note, don't clone all verts which repeat in the
		 * outer bdry of the red chain --- if not poison and complete fan is in
		 * subcomplex, they don't need to be cloned. In particular, new complex
		 * is not necessarily simply connected even if 'Mainwindow.ClosedPath'
		 * or the poison vertices seemed to form a simply connected region.
		 * (Side-paired parts of the red chain which come about because of
		 * multiple connectedness rather than because of poison verts are not
		 * cloned.)
		 * @param redlist RedList, the intended redchain
		 * @param fdo VertList, the face drawing order
		 * @return int, should be nodecount 
		 */
	public int rationalize_subcomplex(RedList redlist, VertList fdo) {
		int[] new_flower = null;
		Face[] new_faces = null;
		BoundaryData trace, temp;

		BoundaryData bdrydata = null;CPBase.Flink=LayoutBugs.redChain2redLink(redlist);
		if ((bdrydata = organize_bdry_data(redlist)) == null) { // CPBase.Flink=LayoutBugs.redChain2redLink(redlist);
			return monsterPackData.nodeCount;
		}
		// either error in bdry data, or no change in combinatorics

		int Nodes, old_nodecount;
		Nodes = old_nodecount = monsterPackData.nodeCount; // Nodes adjusts for new
													// vert indices

		// Create new KData area, save radii, create new Face data

		KData []nK_ptr = new KData[2 * old_nodecount + 1];
		RData []nR_ptr = new RData[2 * old_nodecount + 1];
		for (int i = 1; i <= old_nodecount; i++) {
			nK_ptr[i] = monsterPackData.kData[i].clone();
			nK_ptr[i].utilFlag = 0;
			nR_ptr[i] = monsterPackData.rData[i].clone();
			nK_ptr[i].flower = new int[nK_ptr[i].num + 1];
			for (int j = 0; j <= nK_ptr[i].num; j++)
				nK_ptr[i].flower[j] = monsterPackData.kData[i].flower[j];
		}
		if (monsterPackData.overlapStatus) // overlaps? replicate data.
			for (int i = 1; i <= old_nodecount; i++) {
				nK_ptr[i].overlaps = new double[nK_ptr[i].num + 1];
				for (int j = 0; j <= nK_ptr[i].num; j++)
					nK_ptr[i].overlaps[j] = monsterPackData.kData[i].overlaps[j];
			}
		new_faces = new Face[monsterPackData.faceCount + 1];
		for (int j = 1; j <= monsterPackData.faceCount; j++) {
			new_faces[j] = new Face(monsterPackData);
			for (int i = 0; i < 3; i++) {
				new_faces[j].vert[i] = monsterPackData.faces[j].vert[i];
			}
		}

		/*
		 * Create new vert numbers, cloning vert associated with every
		 * sub-sublist in bdrydata. Record the new vert numbers in the new face
		 * structure. (Make up flowers, overlaps later.)
		 */

		zero_duty_flags(bdrydata);
		trace = bdrydata;
		int vert,indx;
		while (trace.dutyFlag == 0) {
			trace.dutyFlag = 1;
			vert = trace.v;
			// face_ptr=packData.kData[vert].faces+1;
			temp = trace;
			boolean keepon = true;
			while (temp != trace || keepon) {
				keepon = false;
				temp.dutyFlag = 1;
				
				// new index
				Nodes = indx = temp.newIndex = Nodes + 1; 
				nK_ptr[indx]=new KData();
				nR_ptr[indx]=new RData();

				nK_ptr[indx].bdryFlag = nK_ptr[indx].plotFlag = 1;
				nK_ptr[indx].num = temp.num;
				nK_ptr[indx].flower = new int[nK_ptr[indx].num + 1];
				if (monsterPackData.overlapStatus)
					nK_ptr[indx].overlaps = new double[nK_ptr[indx].num + 1];
				Color col=monsterPackData.kData[vert].color;
				nK_ptr[indx].color=new Color(col.getRed(),col.getGreen(),col.getBlue());
				nR_ptr[indx].rad = monsterPackData.rData[vert].rad;
				nR_ptr[indx].center = new Complex(monsterPackData.rData[vert].center);
				nR_ptr[indx].aim = -.1;
				int i = temp.index1;
				if (temp.index1 == temp.index2) { // just one face
					int f = monsterPackData.kData[vert].faceFlower[i];
					int j = monsterPackData.face_index(f, vert);
					new_faces[f].vert[j] = temp.newIndex;
				} else
					for (int iii = 0; iii < temp.num; iii++) {
						int ii = (temp.index1 + iii) % monsterPackData.kData[vert].num;
						int f = monsterPackData.kData[vert].faceFlower[ii];
						int j = monsterPackData.face_index(f, vert);
						new_faces[f].vert[j] = temp.newIndex;
					}
				temp = temp.nextObj;
			}
			trace = trace.next;
			while (trace.dutyFlag != 0 && trace != bdrydata)
				trace = trace.next;
		} // end of while

		/*
		 * Go through the fan of faces of each entry of bdrydata, use the new
		 * index numbers recorded in the new face structure to fix up the
		 * flowers of the newly created vertices and modify the flowers of the
		 * OLD vert numbers.
		 */

		zero_duty_flags(bdrydata);
		trace = bdrydata;
		int v,k;
		while (trace.dutyFlag == 0) {
			trace.dutyFlag = 1;
			vert = trace.v;
			// nghb vert of first face
			int f = monsterPackData.kData[vert].faceFlower[trace.index1];
			int j = monsterPackData.face_index(f, vert); // distinguished vert index
			nK_ptr[trace.newIndex].flower[0] = v = new_faces[f].vert[(j + 1) % 3];
			int num;
			if (v <= old_nodecount) {
				k = monsterPackData.nghb(v, vert);
				/*
				 * if first vertex v from fan is an old vertex and is interior,
				 * then it becomes bdry and flower must be opened. (The vert at
				 * the end of fan will be handled likewise below.)
				 */
				if (nK_ptr[v].bdryFlag == 0) {
					num = nK_ptr[v].num;
					new_flower = new int[num + 1];
					for (int jj = 0; jj < num; jj++)
						new_flower[jj] = nK_ptr[v].flower[(k + jj) % num];
					/*
					 * TODO: need to take care of overlaps if (overlapStatus) {
					 * new_overlaps=new double[num+1]; for (int jj=0;jj<num;jj++)
					 * new_overlaps[jj]=nK_ptr[v].overlaps[(k+jj) % num];
					 * new_overlaps[num]=nK_ptr[v].overlaps[k];
					 * nK_ptr[v].overlaps=new_overlaps; }
					 */
					new_flower[num] = trace.newIndex;
					nK_ptr[v].flower = new_flower;
					nK_ptr[v].bdryFlag = 1;
					nR_ptr[v].aim = -.1;
					monsterPackData.kData[v].flower = new int[num + 1];
					for (int i = 0; i <= num; i++)
						monsterPackData.kData[v].flower[i] = nK_ptr[v].flower[i];
				} else
					// old boundary vert
					nK_ptr[v].flower[k] = trace.newIndex;
			}
			// next verts of rest of faces
			int i = 0;
			while (i < trace.num) {
				f = monsterPackData.kData[vert].faceFlower[(trace.index1 + i)
						% (monsterPackData.kData[vert].num)];
				int jj = (monsterPackData.face_index(f, vert) + 2) % 3;
				nK_ptr[trace.newIndex].flower[i + 1] = new_faces[f].vert[jj];
				/* old vert number; need to adjust its flower */
				if ((v = new_faces[f].vert[jj]) <= monsterPackData.nodeCount) {
					k = monsterPackData.nghb(v, vert);
					/* have to open flower? */
					if (i == trace.num && nK_ptr[v].bdryFlag == 0) {
						num = nK_ptr[v].num;
						new_flower = new int[num + 1];
						for (int jjj = 0; jjj < num; jjj++)
							new_flower[jjj] = nK_ptr[v].flower[(k + jjj) % num];
						new_flower[0] = trace.newIndex;
						nK_ptr[v].flower = new_flower;
						nK_ptr[v].bdryFlag = 1;
						nR_ptr[v].aim = -.1;
						monsterPackData.kData[v].flower = new int[num + 1];
						for (int ii = 0; ii <= num; ii++)
							monsterPackData.kData[v].flower[ii] = nK_ptr[v].flower[ii];
					} else {
						nK_ptr[v].flower[k] = trace.newIndex;
						if (k == 0 && nK_ptr[v].bdryFlag == 0)
							nK_ptr[v].flower[monsterPackData.kData[v].num] = trace.newIndex;
					}
				}
				i++;
			}
			trace=trace.next;
		}

		/*
		 * We must renumber all vertices; 'new_faces' contain all the indices to
		 * be saved; we throw out rest, put these in order, then adjust
		 * 'packData'. Run through the faces of 'fdo' to find all vertices to
		 * be saved, mark them via 'nK_ptr utilFlag' = 1.
		 */
		for (int i = 1; i <= Nodes; i++)
			nK_ptr[i].utilFlag = 0;
		while (fdo != null) {
			for (int i = 0; i < 3; i++)
				nK_ptr[new_faces[fdo.v].vert[i]].utilFlag = 1;
			fdo = fdo.next;
		}

		int count = 0;
		for (int i = 1; i <= Nodes; i++)
			if (nK_ptr[i].utilFlag != 0)
				count++;

		// clean out old packing, adjust size
		monsterPackData.alloc_pack_space(((int) ((count - 1) / 5000)) * 5000 + 5000,
				false);
		monsterPackData.status = true;
		monsterPackData.nodeCount = count;

		new2old = new int[Nodes + 1];
		old2new = new int[Nodes + 1];

		count = 0;
		for (int i = 1; i <= Nodes; i++) {
			if (nK_ptr[i].utilFlag != 0) {
				count++;
				old2new[i] = count;
				monsterPackData.kData[count] = nK_ptr[i];
				monsterPackData.rData[count] = nR_ptr[i];
				if (i <= old_nodecount)
					new2old[count] = i;
				else { // look up original index of this new vert
					trace = bdrydata;
					while (trace != null) {
						if (trace.newIndex == i) {
							new2old[count] = trace.v;
							trace = null;
						} else
							trace = trace.next;
					}
				}
			} else
				nK_ptr[i].flower = null;
		}

		// update indices in original flowers
		for (int i = 1; i <= monsterPackData.nodeCount; i++)
			for (int j = 0; j <= monsterPackData.kData[i].num; j++)
				monsterPackData.kData[i].flower[j] = old2new[monsterPackData.kData[i].flower[j]];

		// TODO: save inv dist data when possible
		return monsterPackData.nodeCount;
	} 

	 /**
		 * Intialize 'dutyFlag' to zero in linked list of 'BdryData';
		 * length limited to 100000.
		 * @param bdrydata BoundaryData
		 * @return 0 if bdrydata is null, else 1.
		 */
	 public int zero_duty_flags(BoundaryData bdrydata) {
		 if (bdrydata==null)
			 return 0;
		 
		 BoundaryData trace=bdrydata;
		 boolean keepon=true;
		 int safety=100000;
		 while(safety>=0 && (trace!=bdrydata || keepon)) {
			 keepon=false;
			 trace.dutyFlag=0;
			 trace=trace.next;
			 safety--;
		 }
		 return 1;
	 } 

	 /** 
	  * Go through a red chain (closed linked list of 'RedList''s) 'packData', 
	  * pick off successive "outer" verts (those on righthand edge of the chain) 
	  * and generate 'BoundaryData' structures. Assume poison verts are 
	  * indicated by cmUtil[]= -1. Special treatment needed for blue faces 
	  * (as always!). 
	  * @param redlist 'RedList' (redlist does NOT appear to be changed)
	  * @return new @see BoundaryData
	  */
	 public BoundaryData organize_bdry_data(RedList redlist) {
		int indx = -1;
		BoundaryData bdrydata = null;
		BoundaryData trace = null;
		BoundaryData temp = null;
		BoundaryData hold = null;
		BoundaryData orig;
		RedList rtrace;
		RedList stop_ptr = null;
		boolean debug=false;
		@SuppressWarnings("unused")
		NodeLink bugl=null; // debug info, if needed

		// start at face having an edge
		redlist = monsterPackData.next_red_edge(1, redlist, indx);
		indx=redlist.util;
		int v;
		boolean keepon = true;
		while (redlist != stop_ptr) {
			temp = new BoundaryData();
			temp.nextObj = temp.prevObj = temp; // default: points to self
			temp.v = v = monsterPackData.faces[redlist.face].vert[redlist.vIndex];
			temp.boundaryFlag = 0;
			if (monsterPackData.kData[v].flower[0] != monsterPackData.kData[v].flower[monsterPackData.kData[v].num])
				temp.boundaryFlag = 1;
			temp.poisonFlag = 0;
			if (cmPoison[v] == -1)
				temp.poisonFlag = 1;
			// set indices of faces
			int num = monsterPackData.kData[v].num;
			temp.index2 = num - 1;
			while (monsterPackData.kData[v].faceFlower[temp.index2] != redlist.face
					&& temp.index2 > 0)
				temp.index2--;
			rtrace = redlist;
			int i = temp.index2;
			while (rtrace.next.face == monsterPackData.kData[v].faceFlower[(i + num - 1)
					% num]
					&& rtrace.next.face != redlist.face) {
				rtrace = rtrace.next;
				i--;
			}
			temp.num = temp.index2 - i + 1;
			temp.index1 = ((i + num) % num);
			
			if (keepon) { // first time through? start double linked list
				keepon = false;
				bdrydata = hold = temp;
				bdrydata.next = bdrydata.prev = bdrydata;
				stop_ptr = redlist;
			} else { // interleaf new data
				trace = hold.next;
				hold.next = temp;
				temp.prev = hold;
				temp.next = trace;
				trace.prev = temp;
				hold = temp;
			}

			// if this face is blue, do everything again, but for next vertex.
			if (redlist.prev.face == redlist.next.face) {
				indx = (indx + 1) % 3;
				temp = new BoundaryData();
				temp.nextObj = temp.prevObj = temp; // default: points to self
				temp.v = v = monsterPackData.faces[redlist.face].vert[(redlist.vIndex + 1) % 3];
				temp.boundaryFlag = 0;
				if (monsterPackData.kData[v].flower[0] != monsterPackData.kData[v].flower[monsterPackData.kData[v].num])
					temp.boundaryFlag = 1;
				temp.poisonFlag = 0;
				if (cmPoison[v] == -1)
					temp.poisonFlag = 1;
				// set indices of faces
				num = monsterPackData.kData[v].num;
				temp.index2 = num - 1;
				while (monsterPackData.kData[v].faceFlower[temp.index2] != redlist.face
						&& temp.index2 > 0)
					temp.index2--;
				rtrace = redlist;
				i = temp.index2;
				while (rtrace.next.face == monsterPackData.kData[v].faceFlower[(i
						+ num - 1)
						% num]
						&& rtrace.next.face != redlist.face) {
					rtrace = rtrace.next;
					i--;
				}
				temp.num = temp.index2 - i + 1;
				temp.index1 = ((i + num) % num);
				trace = hold.next;
				hold.next = temp;
				temp.prev = hold;
				temp.next = trace;
				trace.prev = temp;
				hold = temp;
			}
			redlist = monsterPackData.next_red_edge(1, redlist, indx);
			indx=redlist.util;
		} // end of while

		if (debug) { // debug=true;
			bugl=printBdryData(bdrydata); // CPBase.Vlink=bugl;
		}

		/*
		 * next, we'll look through this list, set up sub linkings between those
		 * sharing same v, throw out any with complete fans -- they can't be
		 * cloned and flower of v won't need adjustment.
		 */

		zero_duty_flags(bdrydata);
		trace = bdrydata;
		int vert;
		int safety=0;
		while (trace.dutyFlag == 0) {
			// prevent runaway
			if (safety++>2*monsterPackData.nodeCount) 
				throw new CombException("Error processing boundary in forming complex");
			// if this vertex has a complete fan, we can remove it from the list
			if (trace.num == monsterPackData.kData[trace.v].num) {
				
				// remove trace, but does list collapse? (I think this is what is intended)
				BoundaryData tmpbd=drop_bd(trace,bdrydata);
				if (tmpbd==null) 
					return null;
				if (trace==bdrydata || tmpbd==null) {
					bdrydata=tmpbd; // this updates 'bdrydata'
				}
				trace=tmpbd;
				if (bdrydata != null && bdrydata.next == bdrydata)
					return null;
			} 
			else { // create/add to sub links
				vert = trace.v;
				if (trace.nextObj != trace)
					trace.dutyFlag = 1; // already in a sublist
				else {
					int n = trace.num;
					hold = trace;
					temp = trace.next;
					while (temp != trace) { // find other occurances of vert
						if (temp.v == vert) {
							hold.nextObj = temp;
							temp.prevObj = hold;
							hold = temp;
							n += hold.num;
						}
						hold.nextObj = trace;
						trace.prevObj = hold; /*
												 * last points to first (or
												 * possibly first points to
												 * itself).
												 */
						temp = temp.next;
					} // end of while
					if (trace.poisonFlag == 0 && n == monsterPackData.kData[vert].num) {
						/*
						 * vert's not poison and the various fans account for
						 * all its faces. Drop this whole sublist of entries
						 */
						temp = orig = trace;
						boolean keepitup = true;
						BoundaryData ntrace=null;
						while (temp != orig || keepitup) {
							keepitup = false;
							ntrace = temp.nextObj;
							// remove this; be sure to fix bdrydata if necessary
							BoundaryData tmpbd=drop_bd(temp,bdrydata); // remove it
							if (temp==bdrydata || tmpbd==null) {
								bdrydata=tmpbd; // this updates 'bdrydata'
							}
							temp=tmpbd;
							if (bdrydata != null && bdrydata.next == bdrydata)
								return null;
							temp = ntrace;
						}
					} else
						trace.dutyFlag = 1;
				}
				trace = trace.next;
			}
		} // end of while
		if (bdrydata == null || bdrydata.next == bdrydata)
			return null;

		if (debug) { // debug=true;
			bugl=printBdryData(bdrydata); // CPBase.Vlink=bugl;
		}

		/*
		 * Organize sublists with more than one entry in increasing fan-index
		 * order, then pass through this list to consolidate fans where
		 * possible.
		 */

		zero_duty_flags(bdrydata);
		trace = bdrydata;
		boolean keepn = true;
		while (trace.dutyFlag == 0) {
			vert = trace.v;
			// **** face_ptr=face_org[vert]+1;

			// find orig: lowest face index among fans in sublist
			orig = trace;
			temp = orig.nextObj;
			while (temp != trace) {
				if (temp.index1 < orig.index1)
					orig = temp;
				temp = temp.nextObj;
			}
			if (keepn) { // first time through? reset to help exit loop
				keepn = false;
				trace = bdrydata = orig;
			}

			// Leave orig unchanged, find successive lowest indices.

			BoundaryData last = orig;
			while (last.nextObj != orig && last.nextObj.nextObj != orig) {
				BoundaryData low = last.nextObj;
				temp = low.nextObj;
				while (temp != orig) {
					if (temp.index1 < low.index1)
						low = temp;
					temp = temp.nextObj;
				}
				if (low != last.nextObj) { // put low right after last in
											// nextObj list
					low.prevObj.nextObj = low.nextObj;
					low.nextObj.prevObj = low.prevObj;
					low.prevObj = last;
					low.nextObj = last.nextObj;
					last.nextObj.prevObj = low;
					last.nextObj = low;
				}
				last = low;
			} // go get next lowest

			/*
			 * check this sublist for contiguous fans -- consolidate when
			 * possible. Not all contig fans are melded; may remain separate
			 * depending on need for cloning.
			 */

			hold = orig;
			if (orig.nextObj == orig)
				orig.dutyFlag = 1; // only one fan
			while (hold.dutyFlag == 0 && hold != hold.nextObj) {
				// hold is next starting place in fan to keepTrying to hook to.
				hold.dutyFlag = 1;
				temp = hold.nextObj;
				int keepTrying = 1;
				while (keepTrying != 0) {
					/*
					 * Is next fan contiguous? (Note that as we come around to
					 * orig at the end, have to allow possibility that it may
					 * get hooked up with the last fan.)
					 */
					if ((monsterPackData.kData[vert].bdryFlag != 0 &&
							hold.index2 < monsterPackData.kData[vert].num - 1 && 
							temp.index1 == hold.index2 + 1) ||
							(monsterPackData.kData[vert].bdryFlag == 0 && 
								temp.index1 == ((hold.index2 + 1) % monsterPackData.kData[vert].num))) {
						int f1 = monsterPackData.kData[vert].faceFlower[hold.index2];
						int f2 = monsterPackData.kData[vert].faceFlower[temp.index1];
						int w = monsterPackData.faces[f1].vert[monsterPackData.face_nghb(f2, f1)];
						/*
						 * the edge between the contig faces is <vert,w>; either
						 * end is not poison, consolidate in first fan, drop second
						 * entry.
						 */
						if (cmPoison[w] != -1 || cmPoison[vert] != -1) {
							hold.index2 = temp.index2;
							hold.num += temp.num;
							hold.nextObj = temp.nextObj;
							temp.nextObj.prevObj = hold;
							BoundaryData tmpbd=drop_bd(temp,bdrydata);
							if (temp==bdrydata || tmpbd==null)
								bdrydata=tmpbd;
							if (bdrydata==null || bdrydata==bdrydata.next)
								return null;
							temp = hold.nextObj; // try the next fan, too
						} else {
							hold = temp;
							keepTrying = 0;
						}
					} else {
						hold = temp;
						keepTrying = 0;
					}
				} // end of inner while
			} // end of while; done with this sublist

			// point to next sublist via trace 
			trace = trace.next;
			int emgy=0;
			while (emgy< 5*monsterPackData.nodeCount &&
					trace != bdrydata && trace.dutyFlag != 0) {
				trace = trace.next;
				emgy++;
			}
			if (emgy>=5*monsterPackData.nodeCount) {
				throw new CombException("emergency exit from organize_bdry_data");
			}
		} // end of outer while; have gone through all the sublists 

		if (debug) { // debug=true;
			bugl=printBdryData(bdrydata); // CPBase.Vlink=bugl;
		}

		return bdrydata;
	}
	 
	/**
	 * Alternate type of cookie cutting: (Jan 2017) We basically keep only those
	 * faces whose centroids are in the region. We want a simply connected
	 * result, however, so we must fill in any encircled faces.
	 * Problem: many boundary faces poke out, leaving a vertex with no interior
	 * neighbors. Hence, this is not a very useful method.
	 * 
	 * @param Tri
	 *            Triangulation, with 'nodes' giving 2D locations
	 * @param Gamma
	 *            Path2D.Double, closed curve, assumed Jordan
	 * @return Triangulation or null on error
	 */
	public static Triangulation zigzag_cutter(Triangulation Tri, Path2D.Double Gamma) {

		// store vertex centers
		Complex[] Z = new Complex[Tri.nodeCount+1];
		for (int n = 1; n <= Tri.nodeCount; n++)
			Z[n] = new Complex(Tri.nodes[n].x, Tri.nodes[n].y);
		

		// Now the processing to prune this; work with DCEL data
		PackDCEL myDCEL = new PackDCEL(Tri);
		int facecount = myDCEL.faces.size()-1-myDCEL.idealFaces.size(); // number of interior faces		

		// find the centroids
		Complex[] faceC = new Complex[facecount+1]; // centroids
		int tick = 0;
		Iterator<dcel.Face> dcf = myDCEL.faces.iterator();
		dcf.next(); // toss first 'null' entry
		while (dcf.hasNext() && tick<facecount) {
			ArrayList<HalfEdge> edges=dcf.next().getEdges();
			Iterator<HalfEdge> eit=edges.iterator();
			Complex accum=new Complex(0.0);
			while (eit.hasNext()) {
				HalfEdge he = eit.next();
				accum = accum.add(Z[he.origin.vertIndx]);
			}
			faceC[++tick] = new Complex(accum.times(1.0/(double)edges.size())); // average of vertex locations
		}

		// determine which faces have centroids in Gamma; these are included
		Complex firstC = null;
		dcel.Face firstFace=null;
		int[] facestat = new int[facecount + 1]; // 1=included

		for (int j = 1; j <= facecount; j++) {
			if (Gamma.contains(faceC[j].x, faceC[j].y)) {
				facestat[j] = 1;
				if (firstC == null) { // mark the first included centroid and its face
					firstC = new Complex(faceC[j]);
					firstFace=myDCEL.faces.get(j);
				}
			}
		}
		
		// There may be other faces included, we may have to fill in holes.
		// Idea is to find simple closed path from included, and
		// add those inside this path.

		// Find all halfedges of included faces with non-included (or
		// ideal) face on the other side.
		Vector<HalfEdge> putbdry = new Vector<HalfEdge>(0);
		for (int f = 1; f <= Tri.faceCount; f++) {
			if (facestat[f] > 0) {
				ArrayList<HalfEdge> edges=myDCEL.faces.get(f).getEdges();
				Iterator<HalfEdge> eit=edges.iterator();
				while (eit.hasNext()) {
					HalfEdge he=eit.next();
					dcel.Face oppface = he.twin.face;
					if (Math.abs(oppface.faceIndx) > facecount || facestat[Math.abs(oppface.faceIndx)] == 0)
						putbdry.add(he);
				}
			}
		}

		// sift through 'putbdry', removing cclw closed chains until
		// we get one which wraps positively about 'firstC'. This
		// should be "outer" bdry.

		Path2D.Double outerPath = null;
		
		// debug
//		PackData tmppd=PackControl.getActivePackData();
		
		while (outerPath == null && putbdry.size() > 0) {

			// debugging
/*			tmppd.elist=new EdgeLink(tmppd);
			tmppd.flist=new FaceLink(tmppd);
			tmppd.zlist=new PointLink(tmppd);
			for (int jj=1;jj<=facecount;jj++) {
				if (facestat[jj]>0) {
					tmppd.flist.add(jj);
					tmppd.zlist.add(new Complex(faceC[jj]));
				}
			}
*/			
			// debug: 'disp -ffc120 flist -tfc195t2 z zlist;'
			
			// start a new closed edge path
			Vector<HalfEdge> bpath = new Vector<HalfEdge>(0);
			HalfEdge start = putbdry.remove(0);
			bpath.add(start);
			
			// debug
/*			
			EdgeSimple edge=new EdgeSimple(start.origin.vertIndx,start.twin.origin.vertIndx);
			tmppd.elist.add(edge);
			String dstr=new String("disp -et3 "+edge.v+" "+edge.w+";");
			System.err.println(dstr);
*/
			
			HalfEdge nextedge = start.twin.prev.twin;
			while (!putbdry.contains(nextedge) && nextedge != start) {
				nextedge = nextedge.prev.twin;
			}
			if (nextedge == start)
				throw new CombException("comb problem in dcel");
			HalfEdge curredge = start;
			while (putbdry.size()>0 && nextedge != start) {
				curredge = nextedge;
				putbdry.remove(curredge);
				bpath.add(curredge);
				
				// debug
/*				
				edge=new EdgeSimple(curredge.origin.vertIndx,curredge.twin.origin.vertIndx);
				tmppd.elist.add(edge);
				dstr=new String("disp -et3 "+edge.v+" "+edge.w+";");
				System.err.println(dstr);
*/
				
				nextedge = curredge.twin.prev.twin;
				while (!putbdry.contains(nextedge) && nextedge!=start && nextedge != curredge) {
					nextedge = nextedge.prev.twin;
				} // now have 'nextedge'
				if (nextedge == curredge)
					throw new CombException("combinatoric problem in dcel");
			} // end of while to close path
			
			// create cclw Path2D.Double using ends of 'bpath'
			Path2D.Double putPath = new Path2D.Double();
			Complex pt = new Complex(Z[bpath.remove(0).twin.origin.vertIndx]);
			putPath.moveTo(pt.x, pt.y);
			Iterator<HalfEdge> bpi = bpath.iterator();
			while (bpi.hasNext()) {
				pt = new Complex(Z[bpi.next().twin.origin.vertIndx]);
				putPath.lineTo(pt.x, pt.y);
			}
			putPath.closePath();

			// is 'putPath' the outer path? Yes, if it contains 'firstC'
			if (putPath.contains(firstC.x, firstC.y))
				outerPath = putPath;
		} // should have outer path

		if (outerPath == null)
			throw new CombException("couldn't build 'outerPath'");

		// convert to triangulation
		Triangulation zzTri = new Triangulation();

		// Two steps: (1) find all faces inside 'outerPath'; 
		//    (2) build out simply connected patch
		int startindx=-1;
		int []inouter=new int[myDCEL.faces.size()];
		for (int j = 1; j <= facecount; j++) { 
			if (outerPath.contains(faceC[j].x, faceC[j].y)) { 
				inouter[j]=1;
				if (startindx==-1)
					startindx=j;
			}
		}
		
		// if 'firstFace' is no longer included, use new starting place
		if (inouter[Math.abs(firstFace.faceIndx)]==0)
			firstFace=myDCEL.faces.get(startindx);
			
		// cycle through adding faces to 'firstFace'
		Vector<dcel.Face> nextF=new Vector<dcel.Face>();
		Vector<dcel.Face> currF=nextF;
		nextF.add(firstFace);
		int[] newfaces = new int[myDCEL.faces.size()];
		newfaces[Math.abs(firstFace.faceIndx)]=1;
		int infacecount = 1;
		tick=0;
		while(nextF.size()>0) {
			currF=nextF;
			nextF=new Vector<dcel.Face>();
			while (currF.size()>0) {
				dcel.Face currface=currF.remove(0);
				ArrayList<HalfEdge> edges=currface.getEdges();
				Iterator<HalfEdge> eit=edges.iterator();
				while (eit.hasNext()) {
					HalfEdge he=eit.next();
					int findx=Math.abs(he.twin.face.faceIndx);
					if (findx<=facecount && inouter[findx]>0 && newfaces[findx]==0) {
						newfaces[findx]=1;
						infacecount++;
						nextF.add(he.twin.face);
					}
				}
			} // end of while on 'currF'
		} // end of while on 'nextF'
		
		// reindex the needed vertices
		int[] old2new = new int[myDCEL.vertCount + 1];
		tick = 0;
		for (int j = 1; j <= facecount; j++) {
			if (newfaces[j]>0) {
				
				// debug
				int []fv= myDCEL.faces.get(j).getVerts();
				StringBuilder ddstr=new StringBuilder("face: ");
				for (int nm=0;nm<fv.length;nm++)
					ddstr.append(" "+fv[nm]);
				System.err.println(ddstr.toString());
				
				int []verts=myDCEL.faces.get(j).getVerts();
				for (int m=0;m<verts.length;m++) {
					int v=verts[m];
					if (old2new[v] == 0)
						old2new[v] = tick++; // new index;
				}
			} 
		} // have new indices


		// fill triangulation data
		zzTri.nodeCount = tick;
		zzTri.maxIndex = tick;
		zzTri.nodes = new Point3D[tick + 1];

		// fill 'faces'
		zzTri.faceCount = infacecount;
		zzTri.faces = new Face[zzTri.faceCount + 1];
		tick = 0;
		for (int j = 1; j <= facecount; j++) {
			if (newfaces[j] == 1) {
				Vector<Integer> vertvec = new Vector<Integer>(0);
				HalfEdge he = myDCEL.faces.get(j).edge;
				vertvec.add(old2new[he.origin.vertIndx]); // new index
				HalfEdge nhe = he.next;
				while (nhe != he) {
					vertvec.add(old2new[nhe.origin.vertIndx]);
					nhe = nhe.next;
				}
				int sz = vertvec.size();
				int[] findx = new int[sz];
				for (int k = 0; k < sz; k++)
					findx[k] = vertvec.get(k);
				zzTri.faces[++tick]=new Face();
				zzTri.faces[tick].vert = findx;
			}
		}
		
		// set node locations
		for (int m = 1; m <= myDCEL.vertCount; m++) {
			if (old2new[m] != 0)
				zzTri.nodes[old2new[m]] = new Point3D(Z[m].x, Z[m].y, 0.0);
		}

		return zzTri;
	}

	/**
	 * Drop an entry of the list. 'bd_ptr' points to the lead element of the
	 * list; if it's the one being removed, the calling routine has to adjust it
	 * (changing it here doesn't help); here we need 'bd_ptr' simply to decide if
	 * the list collapses.
	 * @param ptr BoundaryData, element to be dropped
	 * @param bd_ptr BoundaryData, lead element of the list
	 * @return BoundaryData, next in list after ptr, null if list has collapsed
	 */
	public BoundaryData drop_bd(BoundaryData ptr, BoundaryData bd_ptr) {
		BoundaryData n, p;

		if (ptr == bd_ptr)
			bd_ptr = bd_ptr.next;
		n = ptr.next;
		p = ptr.prev;
		n.prev = p;
		p.next = n;
		ptr = null;
		if (bd_ptr.next == bd_ptr) // list has collapsed
			return null;
		return n;
	}

	/**
	 * debug help: this prints info on BoundaryData list to System.out
	 * and creates NodeLink of the vertices. Limited to 2000 entries.
	 * @return NodeLink of vertices, null if list is null.
	 */
	public NodeLink printBdryData(BoundaryData bd) {
		NodeLink outlist=new NodeLink();
		if (bd==null) {
			System.err.println("BoundaryData list is null");
			return null;
		}
		BoundaryData trace=bd;
		System.out.println("  v="+trace.v+"\n    ;newIndex="+trace.newIndex+
				";num="+trace.num+";boundaryFlag="+trace.boundaryFlag+
				";poisonFlag="+trace.poisonFlag+
				"\n    ;index1/2="+trace.index1+File.separator+trace.index2+
				";dutyFlag="+trace.dutyFlag+";nextObj v="+trace.nextObj.v+";prevObj v="+
				trace.prevObj.v+";next v="+trace.next.v+";prev v="+trace.prev.v+"\n");
		outlist.add(trace.v);
		trace=trace.next;
		int safety=1; //
		while (trace!=null && trace!=bd && safety<2000) {
			System.out.println("  v="+trace.v+"\n    ;newIndex="+trace.newIndex+
				";num="+trace.num+";boundaryFlag="+trace.boundaryFlag+
				";poisonFlag="+trace.poisonFlag+
				"\n    ;index1/2="+trace.index1+File.separator+trace.index2+
				";dutyFlag="+trace.dutyFlag+";nextObj v="+trace.nextObj.v+";prevObj v="+
				trace.prevObj.v+";next v="+trace.next.v+";prev v="+trace.prev.v+"\n");
			outlist.add(trace.v);
			trace=trace.next;
			safety++;
		}
		return outlist;
	}

	/**
	 * Can check this after CookieMonster creation or after 'cookie_cutter' action.
	 * Return is -1 if error or packing is locked, count of poisons verts after 
	 * instantiation of this monster, or nodecount after cookie operation.
	 */
	public int getStatus() {
		return outcomeFlag;
	}
}

class NewVertData{
	int newIndx;
	int origIndx;
	int num;
	int []petals; // old vert indices of petals
}

/**
 * Linked list of information on boundary for use by 'rationalize_subcomplex'
 * @author kens
 */
class BoundaryData{
    int v;                  // original vertex number
    int newIndex;           // assigned new vertex number (temporary)
    int num;                // number of faces in fan 
    int boundaryFlag;
    int poisonFlag;         // 1 if vertex is poison (often 'utilFlag'==1)
    int index1,index2;      // first and last indices in 'faces' of this fan
    int dutyFlag;           // utility flag 
    BoundaryData nextObj;   // pointer to next object sharing v 
    BoundaryData prevObj;   // pointer to prev object sharing v 
    BoundaryData next;
    BoundaryData prev;
}

