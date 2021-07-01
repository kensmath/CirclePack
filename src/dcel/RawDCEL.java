package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import complex.Complex;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.HalfLink;
import listManip.NodeLink;

/**
 * This file is for static methods applied to dcel structures.
 * These "*_raw" methods typically work just with combinatorics
 * (no rad/cents, little or no dependence on PackData parent).
 * 
 * Typical process: 
 *   (1) call 'zeroVUtil'/'zeroEUtil' to zero 'vutil'/'eutil' entries.
 *   (2) call the '*_raw' method to modify the dcel structure,
 *       ensuring 'vertices' is updated.
 *   (3) call 'reapVUtil' to create a 'VertexMap' of "new_to_old"
 *       references (i.e. "the new vert v takes radius from 
 *       old vert w").
 *   (4) call 'fixDCEL_raw':  
 *   	 * If red chain could not be safely modified in the 
 *   	   '*_raw', set 'redChain' null and call 'redchain_by_edge'
 *       * call 'd_FillInside' and 'attachDCEL'. 
 *   (5) call 'modRadCents' with 'VertexMap' to modify rad and/or 
 *       centers (though generally centers aren't worth keeping).
 * 
 * @author kens
 *
 */
public class RawDCEL {
	
	/**
	 * General "generational" marking routine.
	 * 'seedstop' entries >0 for "seed" set, <0 for
	 * "stop" set, 0 for neutral. Seeds are first
	 * generation, with successive generations
	 * connected to them; stop are ones that don't
	 * propagate further. The return entry for 'v' is:
	 *  * n>0 ==> nth generation from some seed
	 *  * -m if stop at mth generation
	 *  * 0 if not reached.
	 *  * ans[0] contains count of positive entries.
	 * If there is no "seed", then use alpha (if it's
	 * not a stop vertex), else a neighbor of alpha,
	 * else first neutral located.
	 *  
     * Examples: count generations from alpha, no stop;
     * Find interior component containing alpha with stops; 
     * etc.
     * 
	 * @param pdcel PackDCEL
	 * @param seedstop int[], entry for each vertex
	 * @return int[]
	 */
	public static int[] markGenerations(PackDCEL pdcel,int[] seedstop) {
		int gen[] = new int[pdcel.vertCount+1];
		NodeLink v_curr=new NodeLink();
		NodeLink v_next=new NodeLink();
		
		// list/mark generation 1
		for (int v=1;v<=pdcel.vertCount;v++) {
			if (seedstop[v]>0) {
				v_next.add(v);
				gen[v]=1;
				gen[0]++;
			}
		}
		
		// no seeds given, find a first seed
		if (v_next.size()==0) {
			int base=0;
			if (pdcel.alpha!=null) {
				int alp=pdcel.alpha.origin.vertIndx;
				if (seedstop[alp]<0) { // try for a nghb 
					int[] petals=pdcel.alpha.origin.getPetals();
					for (int j=0;j<petals.length;j++) {
						if (seedstop[petals[j]]==0)
							base=petals[j];
					}
				}
				if (seedstop[alp]==0)
					base=alp;
			}
			if (base==0) { // else get any non-stop vertex
				for (int v=1;(v<=pdcel.vertCount && base==0);v++)
					if (seedstop[v]==0)
						base=v;
			}
			if (base==0) 
				throw new CombException("no vertex to act as seed");
			
			// list/mark this seed
			v_next.add(base);
			gen[base]=1;
			gen[0]++;
		}

		int gcount=1;
		while (v_next.size()>0) {
			v_curr=v_next;
			v_next=new NodeLink();
			gcount++; // keep track of generation in 'gen'
			Iterator<Integer> vis=v_curr.iterator();
			while (vis.hasNext()) {
				int v=vis.next();
				int[] petals=pdcel.vertices[v].getPetals();
				for (int j=0;j<petals.length;j++) {
					int w=petals[j];
					if (seedstop[w]<0)
						gen[w]=-gcount;
					else if (seedstop[w]==0 && gen[w]==0) {
						gen[w]=gcount;
						gen[0]++;
						v_next.add(w);
					}
				}
			}
		}
		return gen;
	}
	
	/**
	 * Add a new vertex which splits this edge. Red chain
	 * should stay in tact.
	 * @param pdcel PackDCEL
	 * @param edge HalfEdge
	 * @return int, new vertex index
	 */
	public static int splitEdge_raw(PackDCEL pdcel,HalfEdge edge) {
		
		// make room
		if (pdcel.vertCount+1>=pdcel.p.sizeLimit)
			pdcel.p.alloc_pack_space(pdcel.vertCount+11,true);
	
		// switch to twin?
		HalfEdge he=edge;
		if (he.face.faceIndx<=0)
			he=edge.twin;
		boolean twinIdeal=he.twin.face.faceIndx<=0; 
				
		// hold some edges
		HalfEdge he_next=he.next;
		HalfEdge he_prev=he.prev;
		HalfEdge he_twin=he.twin;
		HalfEdge het_next=he.twin.next;
		HalfEdge het_prev=he.twin.prev;
		RedHEdge redge = he.myRedEdge;
		RedHEdge tredge = he.twin.myRedEdge;

		// create new edges to split 'edge'
		HalfEdge newEdge = new HalfEdge();
		newEdge.invDist = he.invDist;
		newEdge.schwarzian = he.schwarzian;
		HalfEdge newTwin = new HalfEdge();
		newTwin.face=he_twin.face; // in case this face is ideal
		newTwin.invDist = he.invDist;
		newTwin.schwarzian = he.schwarzian;

		// new vertex
		Vertex midVert = new Vertex(++pdcel.vertCount);
		pdcel.vertices[pdcel.vertCount] = midVert;
		midVert.halfedge = newEdge;
		newEdge.origin = midVert;
		newTwin.origin = midVert;

		// fix twins
		he.twin = newTwin;
		newTwin.twin = he;

		newEdge.twin = he_twin;
		he_twin.twin = newEdge;

		// fix links at ends of original 'edge'
		newEdge.next=he_next;
		he_next.prev=newEdge;
		newTwin.next=het_next;
		het_next.prev=newTwin;
		
		// add new edge to left of 'edge'
		Vertex oppV=he.next.next.origin;
		HalfEdge leftedge=new HalfEdge(midVert);
		leftedge.twin=new HalfEdge(oppV);
		leftedge.twin.twin=leftedge;
		
		// link in 
		he.next=leftedge;
		leftedge.prev=he;
		
		leftedge.next=he_prev;
		he_prev.prev=leftedge;
		
		leftedge.twin.prev=he_next;
		he_next.next=leftedge.twin;
		
		leftedge.twin.next=newEdge;
		newEdge.prev=leftedge.twin;

		if (redge!=null) {
			midVert.redFlag=true;
			RedHEdge new_redge=new RedHEdge(newEdge);
			newEdge.myRedEdge=new_redge;
			
			new_redge.nextRed=redge.nextRed;
			redge.nextRed.prevRed=new_redge;
			
			redge.nextRed=new_redge;
			new_redge.prevRed=redge;
		}
		
		// handle right side
		if (!twinIdeal) {
			midVert.bdryFlag=1;
			oppV=he_twin.next.next.origin;
			HalfEdge rightedge=new HalfEdge(midVert);
			rightedge.twin=new HalfEdge(oppV);
			rightedge.twin.twin=rightedge;
			
			// link in 
			rightedge.next=het_prev;
			het_prev.prev=rightedge;
			
			rightedge.prev=he_twin;
			he_twin.next=rightedge;
			
			rightedge.twin.next=newTwin;
			newTwin.prev=rightedge.twin;
			
			rightedge.twin.prev=het_next;
			het_next.next=rightedge.twin;
			
			if (tredge!=null) {
				RedHEdge new_redge=new RedHEdge(newTwin);
				newTwin.myRedEdge=new_redge;
				
				new_redge.nextRed=tredge.nextRed;
				tredge.nextRed.prevRed=new_redge;
				
				tredge.nextRed=new_redge;
				new_redge.prevRed=tredge;
			}
		}
		
		else {
			he_twin.next=newTwin;
			newTwin.prev=he_twin;
			midVert.bdryFlag=1;
		}
		
		return midVert.vertIndx;
	}
	
	/**
	 * Split 'V' flower into two cclw flowers, 
	 * depending on situation.  
	 * (1) 'wedge', 'uedge' both non-null, then V must be
	 * interior: first flower is w --> u about V,
	 * while second is u --> w about 'newV'. 
	 * Thus <v',V,u>, <v',w,V> are new oriented faces.
	 * Return new edge <v',v> 
	 * (2) If 'uedge' is null, then V
	 * must be a bdry; split the flower at 'wedge' 
	 * (which must be interior edge): first flower 
	 * downstream bdry to 'wedge' about V, second 
	 * from 'wedge' to upstream bdry about new upstream 
	 * bdry vertex 'newV'. Return new bdry edge <newV,V> 
	 * Red chain is adjusted.
	 * @param pdcel PackDCEL
	 * @param wedge HalfEdge
	 * @param uedge HalfEdge
	 * @return HalfEdge, new edge
	 */
	public static HalfEdge splitFlower_raw(PackDCEL pdcel,HalfEdge wedge,
			HalfEdge uedge) {
		boolean debug=false; // debug=true;

		if (pdcel.isBdryEdge(wedge))
			throw new ParserException("Given 'wedge' should be interior");

		// make room
		if (pdcel.vertCount+1>=pdcel.p.sizeLimit)
			pdcel.p.alloc_pack_space(pdcel.vertCount+11,true);

		Vertex V=wedge.origin;
		HalfLink spks=null;

		// first case: boundary
		if (uedge==null) {
			
			// hold some info
			spks=V.getSpokes(V.halfedge);
			HalfEdge upspoke=V.halfedge.twin.next; // upstream spoke
			HalfEdge wedge_twin=wedge.twin;
			HalfEdge next_spoke=wedge.prev.twin;
			
			// scope out the red chain situation
			RedHEdge red_out=V.halfedge.myRedEdge;
			RedHEdge red_in=red_out.prevRed;
			// if 'wedge' happens to be red, must be twinned
			boolean tricky=false;
			if (wedge.myRedEdge!=null) {
				red_out=wedge.myRedEdge;
				red_in=wedge.myRedEdge.prevRed;
				tricky=true;
			}
				
			// create new vertex
			Vertex newV=new Vertex(++pdcel.vertCount);
			pdcel.vertices[pdcel.vertCount]=newV;
			newV.bdryFlag=1;
			newV.redFlag=true;
			// fix origin for its spoke
			if (next_spoke!=upspoke) {
				HalfEdge he=next_spoke;
				do {
					he.origin=newV;
					he=he.prev.twin;
				} while (he!=upspoke);
			}
			upspoke.origin=newV;
			
			// new bdry edge
			HalfEdge newbdry=new HalfEdge(newV);
			newV.halfedge=newbdry;
			newbdry.twin=new HalfEdge(V);
			newbdry.twin.twin=newbdry;
			newbdry.twin.face=V.halfedge.twin.face; // should be ideal
			// link outside of bdry
			newbdry.twin.next=upspoke;
			upspoke.prev=newbdry.twin;
			
			V.halfedge.twin.next=newbdry.twin;
			newbdry.twin.prev=V.halfedge.twin;
			
			// newedge, <newV, w> 
			HalfEdge newedge=new HalfEdge(newV);
			
			wedge_twin.twin=newedge;
			newedge.twin=wedge_twin;

			newedge.prev=next_spoke.twin;
			next_spoke.twin.next=newedge;
			
			newedge.next=wedge.next;
			wedge.next.prev=newedge;

			// newtwin, <w,v> 
			HalfEdge newtwin=new HalfEdge(wedge.twin.origin);

			newtwin.next=wedge_twin.next;
			wedge_twin.next.prev=newtwin;
			
			newtwin.prev=wedge_twin.prev;
			wedge_twin.prev.next=newtwin;
			
			wedge.twin=newtwin;
			newtwin.twin=wedge;

			// link new face
			newbdry.next=wedge;
			wedge.prev=newbdry;
			wedge.next=wedge_twin;
			wedge_twin.prev=wedge;
			wedge_twin.next=newbdry;
			newbdry.prev=wedge_twin;
			
			// now fix the red chain
			RedHEdge newred=new RedHEdge(newbdry);
			newbdry.myRedEdge=newred;
			
			newred.prevRed=red_in;
			red_in.nextRed=newred;
			
			newred.nextRed=red_out;
			red_out.prevRed=newred;
			
			if (tricky) {
				wedge.twin.myRedEdge=wedge_twin.myRedEdge;
				wedge.twin.myRedEdge.myEdge=wedge.twin;
				wedge_twin.myRedEdge=null;
			}
			
			if (debug) { // debug=true;
				 DCELdebug.vertConsistency(pdcel,newV.vertIndx);
				 DCELdebug.printRedChain(pdcel.redChain,null);
			}
			
			return newbdry;  
		}
		
		// second, more typical, 
		if (wedge.origin!=uedge.origin)
			throw new DCELException("edges' origins don't match");
		if (V.bdryFlag!=0)
			throw new ParserException("given edge is supposed to be interior");
		
		// hold some info
		spks=V.getSpokes(wedge);
		HalfEdge w_twin=wedge.twin;
		HalfEdge u_twin=uedge.twin;
		
		// create newV and fix spokes
		Vertex newV=new Vertex(++pdcel.vertCount);
		pdcel.vertices[pdcel.vertCount]=newV;
		if (uedge.prev.twin!=wedge) {
			HalfEdge he=uedge.prev.twin;
			do {
				he.origin=newV;
				he=he.prev.twin; // clw
			} while (he!=wedge);
		}
		V.halfedge=wedge;
		
		// create edge from newV to v
		HalfEdge new_mid=new HalfEdge(newV);
		newV.halfedge=new_mid;
		new_mid.twin=new HalfEdge(V);
		new_mid.twin.twin=new_mid;

		// new 'uedge'
		HalfEdge new_uedge=new HalfEdge(newV);
		new_uedge.next=uedge.next;
		uedge.next.prev=new_uedge;
		new_uedge.prev=uedge.prev;
		uedge.prev.next=new_uedge;
		new_uedge.twin=u_twin;
		u_twin.twin=new_uedge; // DCELdebug.edge2face(new_wedge.twin);
		
		//new 'uedge.twin'
		HalfEdge new_uedge_twin=new HalfEdge(uedge.twin.origin);
		new_uedge_twin.next=u_twin.next;
		u_twin.next.prev=new_uedge_twin;
		new_uedge_twin.prev=u_twin.prev;
		u_twin.prev.next=new_uedge_twin;
		new_uedge_twin.twin=uedge;
		uedge.twin=new_uedge_twin;
		
		u_twin.prev=uedge;
		uedge.next=u_twin;
		u_twin.next=new_mid;
		new_mid.prev=u_twin;
		new_mid.next=uedge;
		uedge.prev=new_mid;
		
		// now the w side of things
		HalfEdge new_wedge_twin=new HalfEdge(wedge.twin.origin);
		new_wedge_twin.twin=wedge;
		wedge.twin=new_wedge_twin;
		new_wedge_twin.next=new_mid.twin;
		new_mid.twin.prev=new_wedge_twin; // DCELdebug.edge2face(uedge);
		
		HalfEdge new_wedge=new HalfEdge(newV);
		new_wedge.twin=w_twin;
		w_twin.twin=new_wedge;
		new_wedge.prev=new_mid.twin;
		new_mid.twin.next=new_wedge;
		new_wedge.next=new_wedge_twin;
		new_wedge_twin.prev=new_wedge;

		// most typical:
		if (!V.redFlag)
			return new_mid;
		
		// Red chain could need adjustments
		
		// if needed, shift red twins of wedge and/or uedge
		RedHEdge rtrace=null;
		if ((rtrace=w_twin.myRedEdge)!=null) {
			wedge.twin.myRedEdge=rtrace; // wedge.twin is its new twin
			rtrace.myEdge=wedge.twin;
			w_twin.myRedEdge=null;
		}
		if ((rtrace=u_twin.myRedEdge)!=null) {
			uedge.twin.myRedEdge=rtrace; // wedge.twin is its new twin
			rtrace.myEdge=uedge.twin;
			u_twin.myRedEdge=null;
		}
		
		// now all spokes and twins have the proper red edge, but
		//   the red prev/next may need bridging over 'new_mid'

		int num=spks.size();
		HalfEdge[] spokes=new HalfEdge[num];
		ArrayList<EdgeSimple> in_out_pairs=new ArrayList<EdgeSimple>();
		int spot_u=-1;
		for (int k=0;k<num;k++) {
			HalfEdge he=spks.get(k);
			spokes[k]=he;
			he.eutil=k;
			he.twin.eutil=-k;
			if (he==uedge)
				spot_u=k;
		}
		
		for (int k=0;k<num;k++) {
			HalfEdge he=spokes[k];
			if (he.myRedEdge!=null) { // red spoke? outgoing 
				if (he.twin.myRedEdge==null)
					throw new CombException("must be red twinned");
				rtrace=he.myRedEdge.prevRed; // its twin red, incoming
				for (int j=0;j<num;j++) // find incoming index
					if (rtrace.myEdge.twin==spokes[j])
						in_out_pairs.add(new EdgeSimple(j,k));
			}
		}
		
		// handle in/out pairs; most are fine; exactly zero or two
		// can bridge between flowers; may need to adjust for new 
		// wedge, uedge twins. (note: wedge index should be 0,
		// uedge should be spot_u.)
		RedHEdge f1tof2=null;
		RedHEdge f2tof1=null;
		Iterator<EdgeSimple> iot=in_out_pairs.iterator();
		while (iot.hasNext()) {
			EdgeSimple es=iot.next();
			int a=es.v; // index of incoming red edge
			int b=es.w; // following outgoing
			
			// do we cross?
			if (a<=spot_u && b>spot_u) { // crossing: flower 1 to 2
				f1tof2=new RedHEdge(new_mid.twin);
				new_mid.twin.myRedEdge=f1tof2;
				newV.redFlag=true;
				
				spokes[a].twin.myRedEdge.nextRed=f1tof2;
				f1tof2.prevRed=spokes[a].twin.myRedEdge;
				
				spokes[b].myRedEdge.prevRed=f1tof2;
				f1tof2.nextRed=spokes[b].myRedEdge;
			}
			else if (a>spot_u && b<=spot_u) { // crossing: flower 2 to 1
				f2tof1=new RedHEdge(new_mid);
				new_mid.myRedEdge=f2tof1;
				
				spokes[a].twin.myRedEdge.nextRed=f2tof1;
				f2tof1.prevRed=spokes[a].twin.myRedEdge;
				
				spokes[b].myRedEdge.prevRed=f2tof1;
				f2tof1.nextRed=spokes[b].myRedEdge;
			}
		}
		if ((f1tof2==null && f2tof1!=null) || (f1tof2!=null && f2tof1==null))
			throw new DCELException("crossing red chains don't match");
		f1tof2.twinRed=f2tof1;
		f2tof1.twinRed=f1tof2;

		return new_mid;
	}
	
	
	/**
	 * Remove a vertex; 'vertices' are adjusted and reindexed,
	 * with old index in 'vutil'. Calling routine must have
	 * checked for legality, e.g., whether it leaves an interior 
	 * neighbor with only 2 neighbors, etc. 'redChain' is lost;
	 * return 'vlist' of nghb'ing vertices for updating (e.g.
	 * they may become bdry vertices); these vertices become
	 * bdry with 'halfedge's set to first bdry edge, but the
	 * calling routine can use return 'vlist' to adjust as
	 * necessary (e.g., as in 'rm_cir', where 'v' is degree 
	 * 3 and we don't want to create new bdry). 
	 * Calling routine also shifts the 'vData' entries.
	 * @param pdcel PackDCEL
	 * @param v int
	 * @return ArrayList<Vertex> neighbors
	 */
	public static ArrayList<Vertex> rmVert_raw(PackDCEL pdcel,int v) {
		if (pdcel.alpha.origin.vertIndx==v || 
				pdcel.alpha.twin.origin.vertIndx==v)
			pdcel.alpha=null;
		Vertex vert=pdcel.vertices[v];
		
		// determine new face
		Face nface=null;
		if (vert.bdryFlag==1) {
			nface=vert.halfedge.twin.face;
			if (nface.edge==vert.halfedge.twin) {
				nface.edge=vert.halfedge.twin.prev;
			}
		}
		else
			nface=new Face(-1);

		HalfLink spokes=vert.getEdgeFlower();
		ArrayList<Vertex> vlist=new ArrayList<Vertex>();
		
		// save list of nghbs: calling routine has to
		//   reset 'bdryFlag' if necessary and set
		//   ideal face indications.
		Iterator<HalfEdge> sis=spokes.iterator();
		while (sis.hasNext()) 
			vlist.add(sis.next().twin.origin);
		
		// remove the spokes
		sis=spokes.iterator();
		while (sis.hasNext()) {
			HalfEdge he=sis.next();
			HalfEdge infoot=he.twin.prev;
			he.twin.origin.halfedge=infoot.twin;
			HalfEdge outfoot=he.next;
			he.origin.bdryFlag=1;
			infoot.next=outfoot;
			outfoot.prev=infoot;
			infoot.face=outfoot.face=nface;
		}
		for (int w=v;w<pdcel.vertCount;w++) {
			pdcel.vertices[w]=pdcel.vertices[w+1];
			pdcel.vertices[w].vertIndx=w;
			pdcel.vertices[w].vutil=w+1;
		}
		pdcel.vertCount--;
		// give up on red chain
		pdcel.redChain=null; 
		
		// debug: DCELdebug.log_edges_by_vert(pdcel);
		
		return vlist;
	}

	/**
	 * v must be boundary vertex; link cclw and clw nghbs,
	 * making v interior. Red chain is adjusted, but 
	 * calling routine updates combinatorics. If v and its
	 * 2 nghbs form an ideal face, make it interior by
	 * making them interior and adjusting or tossing 
	 * the red chain.
	 * Note: fails if v has just two nghb's.
	 * @param pdcel PackDCEL
	 * @param v int
	 * @return int degree of v
	 */
	public static int enfold_raw(PackDCEL pdcel,int v) {
		  if (!pdcel.vertices[v].isBdry() || pdcel.countPetals(v)<=2)
			  throw new CombException("dcel: can't enfold with just 2 nghbs");
		  
		  // key players
		  Vertex vert=pdcel.vertices[v];
		  HalfEdge out_edge=vert.halfedge;
		  HalfEdge in_edge=out_edge.twin.next.twin;
		  HalfEdge twin_prev=out_edge.twin.prev;
		  HalfEdge twin_next=in_edge.twin.next;
		  Face ideal=out_edge.twin.face;
		  
		  // check if nghbs already connected
		  if (out_edge.twin.next.next.next==out_edge.twin) {
			  
			  // TODO: finish this exceptional case: problem is
			  //   how to handle red chain situations.
			  
			  throw new DCELException("TODO: handle 'enfold' with small bdry");
		  }
		  
		  // new edge and twin
		  HalfEdge new_edge=new HalfEdge(in_edge.origin);
		  HalfEdge new_twin=new HalfEdge(out_edge.twin.origin);
		  
		  new_edge.twin=new_twin;
		  new_twin.twin=new_edge;
		  
		  new_edge.face=null; // don't create new face
		  new_twin.face=ideal;
		  if (ideal.edge==out_edge.twin || ideal.edge==in_edge.twin)
			  ideal.edge=new_twin;
		  
		  // new red 
		  RedHEdge new_red=new RedHEdge(new_edge);
		  new_edge.myRedEdge=new_red;
		  
		  // relink things
		  in_edge.twin.next=new_edge;
		  new_edge.prev=in_edge.twin;
		  
		  new_edge.next=out_edge.twin;
		  out_edge.twin.prev=new_edge;
		  
		  new_twin.prev=twin_prev;
		  twin_prev.next=new_twin;
		  
		  new_twin.next=twin_next;
		  twin_next.prev=new_twin;
		  
		  if (pdcel.redChain==out_edge.myRedEdge || pdcel.redChain==in_edge.myRedEdge)
			  pdcel.redChain=out_edge.myRedEdge.nextRed;
		  
		  // simple red chain case
		  if (in_edge.myRedEdge.nextRed==out_edge.myRedEdge) {
			  vert.redFlag=false;
			  
			  new_red.prevRed=in_edge.myRedEdge.prevRed;
			  in_edge.myRedEdge.prevRed.nextRed=new_red;
			  
			  new_red.nextRed=out_edge.myRedEdge.nextRed;
			  out_edge.myRedEdge.nextRed.prevRed=new_red;
			  
			  in_edge.myRedEdge=null;
			  out_edge.myRedEdge=null;
			  
			  vert.redFlag=false;
		  }
		  // otherwise there are one or more intervening red chains
		  else {
			  RedHEdge red=new RedHEdge(out_edge.twin);
			  
			  new_red.prevRed=in_edge.myRedEdge.prevRed;
			  in_edge.myRedEdge.prevRed.nextRed=new_red;
			  
			  new_red.nextRed=red;
			  red.prevRed=new_red;
			  
			  red.nextRed=in_edge.myRedEdge.nextRed;
			  in_edge.myRedEdge.nextRed.prevRed=red;
			  
			  red.twinRed=out_edge.myRedEdge;
			  out_edge.myRedEdge.twinRed=red;
			  
			  in_edge.myRedEdge=null;
		  }
	
		  vert.bdryFlag=0;
		  new_edge.origin.halfedge=new_edge;
		  return pdcel.countPetals(vert.vertIndx);
	}

	/**
	   * Create a barycenter for face 'f', a new interior
	   * vertex; 'vutil' gives reference vert. Red chain,
	   * 'redFlag's, and 'bdryFlag's should normally remain 
	   * undisturbed. 
	   * 
	   * Note: calling routine should throw out 
	   * 'redChain' if 'f' is ideal face.
	   * 
	   * TODO: 'multi-bary' true, then add three vertices
	   * to the face instead of just one (if the face
	   * has 3 edges?)
	   * @param pdcel PackDCEL
	   * @param f Face
	   * @param mutli_bary boolean, 
	   * @return int new index
	   */
	  public static int addBary_raw(PackDCEL pdcel,
			  Face f,boolean multi_bary) {
		  
		  // make room
		  int node=pdcel.vertCount+1; // new index 
		  if (node>=pdcel.p.sizeLimit)
			  pdcel.p.alloc_pack_space(node+10,true);
			
		  HalfLink polyE=null;
		  int n=0;
		  if (f.edge==null || (polyE=f.getEdges())==null || 
				  (n=polyE.size())<=2) 
			  return 0;
		  
		  Vertex newV=new Vertex(node); // this is the barycenter
		  newV.redFlag=false;
		  
		  // create new spokes
		  HalfEdge last_spoke=new HalfEdge(newV);
		  HalfEdge hold_spoke=last_spoke;
		  HalfEdge base;
		  HalfEdge next_in;
		  for (int j=0;j<(n-1);j++) {
			  base=polyE.get(j);
			  base.origin.bdryFlag=0; // becomes interior
			  base.face=null;
			  next_in=new HalfEdge(base.twin.origin);
	
			  // link around the face
			  base.next=next_in;
			  next_in.prev=base;
			  
			  next_in.next=last_spoke;
			  last_spoke.prev=next_in;
			  
			  last_spoke.next=base;
			  base.prev=last_spoke;
			  
			  last_spoke=new HalfEdge(newV);
			  last_spoke.twin=next_in;
			  next_in.twin=last_spoke;
		  }
		  
		  // last face
		  base=polyE.get(n-1);
		  base.face=null;
		  base.origin.bdryFlag=0;
		  next_in=new HalfEdge(base.twin.origin);
		  
		  base.next=next_in;
		  next_in.prev=base;
	
		  next_in.next=last_spoke;
		  last_spoke.prev=next_in;
		  
		  last_spoke.next=base;
		  base.prev=last_spoke;
		  
		  next_in.twin=hold_spoke;
		  hold_spoke.twin=next_in;
		  
		  // fix up 'newV'
		  newV.halfedge=hold_spoke;
		  newV.bdryFlag=0;
		  newV.vutil=newV.halfedge.twin.origin.vertIndx;
		  pdcel.vertices[node]=newV;
		  pdcel.vertCount=node;
				
		  return node;
	  }
		
	  /**
	   * Add barycenters to given list of faces. 
	   * A barycenter is a new vertex interior to the 
	   * face and connected to its bdry vertices.
	   * @param farray ArrayList<dcel.Face>
	   * @return int count, 0 on error
	   */
	  public static int addBaryCents_raw(PackDCEL pdcel,
			  ArrayList<Face> farray) {
		  int count=0;

		  Iterator<Face> flst=farray.iterator();
		  while (flst.hasNext()) {
			  Face face=flst.next();
			  count += RawDCEL.addBary_raw(pdcel,face,false);
				
			  // face was ideal? toss 'redChain'
			  if (face.faceIndx<0) 
				  pdcel.redChain=null;
			  face.edge=null; // to avoid repeat in facelist
		  } // end of while through facelist
		  return count;
	  }

	  /**
	   * We modify an existing 'PackDCEL' with a new boundary
	   * edge between v and w. Call routine insures that v and w
	   * have common bdry neighbor u. Only possible anbiguity is
	   * if bdry component has 4 vertices: we choose u so <v,u,w>
	   * is cclw. Adjust edge links, adjust red chain; may be
	   * called several times before parent completes combinatorics.
	   * @param pdcel PackDCEL
	   * @param v int
	   * @param w int
	   * @return new HalfEdge, null on error
	   */
	  public static HalfEdge addEdge_raw(PackDCEL pdcel,int v,int w) {
		  Vertex v1=pdcel.vertices[v];
		  HalfEdge edge1=v1.halfedge;
		  
		  // two step cclw may be w
		  Vertex v2=edge1.twin.prev.origin;
		  if (v2.vertIndx!=w) { 
			  v1=pdcel.vertices[w];
			  edge1=v1.halfedge;
			  v2=edge1.twin.prev.origin;
			  if (v2.vertIndx!=w)
				  throw new CombException("vertices "+v+" and "+w+" can't form a legal edge");
			  int hld=w;
			  w=v;
			  v=hld;
		  }

		  // v1, u, v2 should be cclw nghbs, edge1=[v1,u], edge2=[u,v2] 
		  HalfEdge edge2=edge1.twin.prev.twin;
		  RedHEdge red_in=edge1.myRedEdge;
		  RedHEdge red_out=edge2.myRedEdge;
		  if (red_in==null || red_out==null)
			  throw new DCELException("add_edge usage: edges are not red");
		  
		  // adjust redChain if needed
		  if (pdcel.redChain==red_in)
			  pdcel.redChain=red_in.nextRed;
		  if (pdcel.redChain==red_out)
			  pdcel.redChain=red_out.nextRed;
		  
		  // new objects
		  HalfEdge newedge=new HalfEdge(v1);
		  HalfEdge newtwin=newedge.twin=new HalfEdge(v2);
		  newtwin.twin=newedge;
		  RedHEdge newred=red_in.clone(); // inherit cent/rad
		  
		  // fix red pointers
		  newred.myEdge=newedge;
		  newedge.myRedEdge=newred;
		  
		  newred.prevRed=red_in.prevRed;
		  red_in.prevRed.nextRed=newred;
		  
		  // simplest case, red1 and red2 orphaned
		  if (red_in.nextRed==red_out) { 
			  
			  newred.nextRed=red_out.nextRed;
			  red_out.nextRed.prevRed=newred;
			  edge1.myRedEdge=null;
			  edge2.myRedEdge=null;
			  edge2.origin.redFlag=false;

			  // orphan red_in/red_out
			  red_in.nextRed=null;
			  red_out.prevRed=null;
		  }
		  // else, reroute: red1 orphaned, red2 gets twin
		  else {
			  RedHEdge redTwin=new RedHEdge(edge2.twin);
			  edge2.twin.myRedEdge=redTwin;
			  
			  redTwin.prevRed=newred;
			  newred.nextRed=redTwin;
			  redTwin.nextRed=red_in.nextRed;
			  redTwin.nextRed.prevRed=redTwin;
			  red_out.twinRed=redTwin;
			  redTwin.twinRed=red_out;
			  edge1.myRedEdge=null;
		  }
		  
		  // fix edge pointers
		  edge1.twin.next.prev=newtwin;
		  newtwin.next=edge1.twin.next;
		  
		  edge2.twin.prev.next=newtwin;
		  newtwin.prev=edge2.twin.prev;
		  
		  edge1.twin.next=newedge;
		  newedge.prev=edge1.twin;
		  
		  edge2.twin.prev=newedge;
		  newedge.next=edge2.twin;
		  
		  // fix face pointers
		  edge1.twin.face=null;
		  edge2.twin.face=null;
		  newtwin.face=newtwin.next.face; // should be ideal face
		  
		  edge2.origin.bdryFlag=0;
		  v1.halfedge=newedge;
		  
		  return newedge;
	  }

	/** 
		   * Add a layer of nodes to bdry segment from vertex v1 to v2.
		   * Three modes:
		   * 
		   * TENT=0: add one-on-one layer, a new bdry vert for 
		   *   each edge between v1 and v2. Unless v1==v2, 
		   *   v1 and v2 remain as bdry vertices.	
		   *   
		   * DEGREE=1: add nghb's to make vertices from v1 to v2,
		   *   inclusive, interior with degree d. However, no edge
		   *   should connect existing bdry vertices. If v1==v2 or
		   *   v1 is nghb of v2, do whole bdry component.
		   *   
		   * DUPLICATE=2: attach "square" face with bary center 
		   *   to each edge between v1 and v2. Unless v1==v2, 
		   *   v1 and v2 remain on bdry.
		   *   
		   * Calling routine checks v1,v2 and updates combinatorics.
		   * @param pdcel PackDCEL
		   * @param mode int, how to add: 0=TENT, 1=DEGREE, 2=DUPLICATE
		   * @param degree int
		   * @param v1 int, start bdry vert
		   * @param v2 int, end bdry vert
		   * @return int, count of added vertices 
		   */
		  public static int addlayer_raw(PackDCEL pdcel,int mode,
				  int deg,int v1,int v2) {
			  int count=0;
			  
	    	  // modes
	    	  int TENT=0;
	    	  int DEGREE=1;
	    	  int DUPLICATE=2;
	    	  
			  if (mode == DEGREE) {
				  Vertex vert=pdcel.vertices[v1];
				  if (v2 == v1)
					  v2 = vert.halfedge.twin.next.twin.origin.vertIndx;
				  int v = v1;
				  int nextv = v;
	
				  // handle v first; must add new circle shared with upstream nghb.
				  RawDCEL.addVert_raw(pdcel,v);
				  count++;
	
				  // go until you finish v2
				  do {
					  v=nextv; // get 'v' and downstream bdry nghb 'nextv'
					  nextv=pdcel.vertices[v].halfedge.twin.origin.vertIndx;
					  int need=deg-pdcel.countPetals(v);
					  for (int i = 1; i <= need; i++) {
						  RawDCEL.addVert_raw(pdcel,v);
						  count++;
					  }
					  enfold_raw(pdcel,v);
				  } while (v!=v2);
	
				  return count;
			  }
			  if (mode==TENT) {
	//			  int lastv=pdcel.vertices[v2].halfedge.twin.next.twin.origin.vertIndx;
				  HalfEdge edge=pdcel.vertices[v1].halfedge;
				  int w=edge.origin.vertIndx;
				  int v=edge.twin.origin.vertIndx;
				  int nextv=edge.twin.prev.origin.vertIndx;
				  RawDCEL.addVert_raw(pdcel,v);
				  while (v!=v2) {
					  w=v;
					  v=nextv;
					  edge=pdcel.vertices[v].halfedge;
					  nextv=edge.twin.origin.vertIndx;
					  RawDCEL.addVert_raw(pdcel,v);
					  enfold_raw(pdcel,w);
					  count++;
				  }
				  if (v1==v2) {
					  enfold_raw(pdcel,v1);
					  count++;
				  }
				  return count;
			  }
			  if (mode==DUPLICATE) { // DCELdebug.redConsistency(pdcel);
				  count+=RawDCEL.baryBox_raw(pdcel, v1, v2);
			  }
			  return count;
		  }

	/**
	   * Add vertex nghb'ing bdry vertex w and clw bdry nghb.
	   * Set 'vutil' to 'w'. Requires red chain and adjusts it.
	   * @param pdcel PackDCEL
	   * @param w int
	   * @return new Vertex
	   */
	  public static Vertex addVert_raw(PackDCEL pdcel,int w) {
		  HalfEdge out_edge=pdcel.vertIsBdry(pdcel.vertices[w]);
		  if (out_edge==null)
			  throw new CombException(w+" is not a bdry vertex");
	
		  // make sure we have space
		  int node=pdcel.vertCount+1;
		  if (node > (pdcel.p.sizeLimit)
				  && pdcel.p.alloc_pack_space(node+10, true) == 0) 
			  throw new CombException("Pack space allocation failure");
		  
		  // key players; tent over 'base_edge'
		  HalfEdge base_edge=out_edge.twin.next.twin; 
		  Vertex w_vert=out_edge.origin;
		  Vertex v_vert=base_edge.origin;
		  HalfEdge base_twin=base_edge.twin;
		  HalfEdge twin_prev=out_edge.twin;
		  HalfEdge twin_next=base_twin.next;
		  RedHEdge base_red=base_edge.myRedEdge; // from v to w
		  if (pdcel.redChain==base_red)
			  pdcel.redChain=base_red.nextRed;
		  base_edge.myRedEdge=null;
	
		  // create new bdry 'Vertex'
		  Vertex new_vert=new Vertex(node);
		  
		  // 2 new edges and twins
		  HalfEdge e1=new HalfEdge(v_vert);
		  HalfEdge t1=new HalfEdge(new_vert);
		  e1.twin=t1;
		  t1.twin=e1;
		  
		  HalfEdge e2=new HalfEdge(new_vert);
		  HalfEdge t2=new HalfEdge(w_vert);
		  e2.twin=t2;
		  t2.twin=e2;
	
		  // set faces; no new face, link in outer face
		  e1.face=null; 
		  e2.face=null;
		  Face outFace=base_twin.face;
		  base_twin.face=null;
		  if (outFace!=null && outFace.faceIndx<0) {
			  t1.face=outFace;
			  t2.face=outFace;
			  if (outFace.edge==base_twin)
				  outFace.edge=t1;
		  }
		  else {
			  t1.face=new Face(-1);
			  t2.face=t1.face;
		  }
		  
		  // 2 new red edges
		  RedHEdge red1=new RedHEdge(e1);
		  e1.myRedEdge=red1;
		  red1.center=new Complex(base_red.center);
		  red1.rad=base_red.rad;
		  RedHEdge red2=new RedHEdge(e2);
		  e2.myRedEdge=red2;
		  red2.center=new Complex(base_red.center);
		  red2.rad=base_red.rad;
	
		  // relink everything
		  base_edge.twin.next=e1;
		  e1.prev=base_edge.twin;
		  
		  e1.next=e2;
		  e2.prev=e1;
		  
		  e2.next=base_twin;
		  base_twin.prev=e2;
		  
		  twin_prev.next=t2;
		  t2.prev=twin_prev;
		  
		  t2.next=t1;
		  t1.prev=t2;
		  
		  t1.next=twin_next;
		  twin_next.prev=t1;
	
		  red1.prevRed=base_red.prevRed;
		  base_red.prevRed.nextRed=red1;
		  
		  red1.nextRed=red2;
		  red2.prevRed=red1;
		  
		  red2.nextRed=base_red.nextRed;
		  base_red.nextRed.prevRed=red2;
		  
		  e1.origin.halfedge=e1;
		  e2.origin.halfedge=e2;
	
		  // fix vert
		  pdcel.vertCount++;
		  Vertex[] newvertices=new Vertex[pdcel.vertCount+1];
		  for (int j=1;j<pdcel.vertCount;j++)
			  newvertices[j]=pdcel.vertices[j];
		  newvertices[node]=new_vert;
		  pdcel.vertices=newvertices;
		  new_vert.bdryFlag=1;
		  new_vert.redFlag=true;
		  new_vert.vutil=w;
	
		  return new_vert;
	  }

	/**
	   * add combinatorics for a layer of "square" 
	   * faces with barycenters, one for each bdry
	   * edge, 'v1' to 'v2'. 'vutil' of new vertex is
	   * index of reference circle on original bdry.
	   * Calling routine does the rest.
	   * @param pdcel PackDCEL
	   * @param v1 int
	   * @param v2 int
	   * @return int, new 'vertCount'
	   */
	  public static int baryBox_raw(PackDCEL pdcel,int v1,int v2) {
		  
			// make space
			int e_count = pdcel.p.verts_share_bdry(v1, v2);
			if (e_count == 0)
				throw new CombException("verts " + v1 + " and " + v2 + " are not " + "on the same component");
			int nodes = pdcel.vertCount + 2 * e_count;
			if (nodes > pdcel.p.sizeLimit)
				pdcel.p.alloc_pack_space(nodes + 10, true);
	
			// place a "square" 'Face' next to each bdry
			// if we close up, shift v1 cclw as stop signal
			boolean closeup = false;
			if (v1 == v2) {
				closeup = true;
				v1 = pdcel.p.getFirstPetal(v1);
			}
	
			int count=0;
	
			// get relevant edges, vertices
			HalfEdge he = pdcel.vertices[v2].halfedge;
			HalfEdge twin_prev=he.twin;
			HalfEdge base_twin = twin_prev.next;
			HalfEdge twin_next=base_twin.next;
			
			dcel.Face outFace = base_twin.face;
			Vertex lastV = base_twin.origin;
			Vertex nextV = twin_next.origin;
			
			// relevant red edges: note, prev/next
			//    red may not be on bdry.
			RedHEdge base_red = base_twin.twin.myRedEdge;
			RedHEdge next_red=base_red.nextRed; 
			RedHEdge prev_red=base_red.prevRed;
			base_twin.twin.myRedEdge=null;
	
			// create first square face: prep for iteration.
			
			// left spike
			Vertex v_left = new Vertex(++pdcel.vertCount);
			pdcel.vertices[pdcel.vertCount] = v_left;
			v_left.vutil=lastV.vertIndx; // reference vertex
			v_left.bdryFlag = 1;
			v_left.redFlag=true;
			HalfEdge down_left = new HalfEdge(v_left);
			HalfEdge up_left = new HalfEdge(lastV);
			up_left.face = outFace;
			v_left.halfedge = down_left;
			
			up_left.twin = down_left;
			down_left.twin = up_left;
			
			up_left.prev=twin_prev;
			twin_prev.next=up_left;
	
			// right spike
			Vertex v_right = new Vertex(++pdcel.vertCount);
			pdcel.vertices[pdcel.vertCount] = v_right;
			v_right.vutil=nextV.vertIndx; // reference vertex
			v_right.bdryFlag = 1;
			v_right.redFlag=true;
			HalfEdge down_right = new HalfEdge(v_right);
			HalfEdge up_right = new HalfEdge(nextV);
			nextV.halfedge=up_right;
			down_right.face = outFace;
			
			up_right.twin = down_right;
			down_right.twin = up_right;
			
			down_right.next=twin_next;
			twin_next.prev=down_right;
	
			// top
			HalfEdge top = new HalfEdge(v_right);
			v_right.halfedge = top;
			HalfEdge top_tw = new HalfEdge(v_left);
			top_tw.face = outFace;
			dcel.Face new_face = new dcel.Face(pdcel.vertCount);
			top.face = new_face;
			count++;
			
			top.twin = top_tw;
			top_tw.twin = top;
			
			top_tw.next=down_right;
			down_right.prev=top_tw;
			
			top_tw.prev=up_left;
			up_left.next=top_tw;
			
			// link around face
			base_twin.next=up_right;
			up_right.prev=base_twin;
			base_twin.face=new_face;
			new_face.edge=base_twin;
			
			up_right.next=top;
			top.prev=up_right;
			up_right.face=new_face;
			
			top.next=down_left;
			down_left.prev=top;
			down_left.face=new_face;
			
			down_left.next=base_twin;
			base_twin.prev=down_left;
			
			// add barycenter
			addBary_raw(pdcel,new_face,false);
			
			// create red
			RedHEdge up_red=new RedHEdge(up_right);
			up_right.myRedEdge=up_red;
			RedHEdge top_red=new RedHEdge(top);
			top.myRedEdge=top_red;
			RedHEdge down_red=new RedHEdge(down_left);
			down_left.myRedEdge=down_red;
			
			// get red chain out of the way
			pdcel.redChain=top_red;
			
			// link red
			up_red.prevRed=prev_red;
			prev_red.nextRed=up_red;
			
			up_red.nextRed=top_red;
			top_red.prevRed=up_red;
			
			top_red.nextRed=down_red;
			down_red.prevRed=top_red;
			
			down_red.nextRed=next_red;
			next_red.prevRed=down_red;
			
			base_twin.twin.myRedEdge=null;
			
			// save some info for closing up
			RedHEdge firstRed = down_left.myRedEdge;
	
			// ======= iterate until finishing with v1 =======
			while (nextV.vertIndx!=v1) {
				
				// shift
				down_left = down_right;
				up_left = up_right;
				v_left = v_right;
				lastV=nextV;
	
				// set new base 'bdry_twin' and save 'twin_next'
				base_twin = twin_next;
				twin_next = base_twin.next;
				base_red=base_twin.twin.myRedEdge;
				prev_red=base_red.prevRed;
				next_red=base_red.nextRed;
				nextV=base_red.myEdge.origin;
	
				// right spike
				v_right = new Vertex(++pdcel.vertCount);
				pdcel.vertices[pdcel.vertCount] = v_right;
				v_right.vutil=nextV.vertIndx; // reference vertex
				v_right.bdryFlag = 1;
				v_right.redFlag=true;
				down_right = new HalfEdge(v_right);
				up_right = new HalfEdge(nextV);
				nextV.halfedge=up_right;
				down_right.face = outFace;
				
				up_right.twin = down_right;
				down_right.twin = up_right;
				
				down_right.next=twin_next;
				twin_next.prev=down_right;
	
				// top
				top = new HalfEdge(v_right);
				v_right.halfedge = top;
				top_tw = new HalfEdge(v_left);
				top_tw.face = outFace;
				new_face = new dcel.Face(pdcel.vertCount);
				top.face = new_face;
				count++;
				
				top.twin = top_tw;
				top_tw.twin = top;
				
				top_tw.next=down_right;
				down_right.prev=top_tw;
				
				top_tw.prev=down_left.prev;
				down_left.prev.next=top_tw;
				
				// link around face
				base_twin.next=up_right;
				up_right.prev=base_twin;
				base_twin.face=new_face;
				new_face.edge=base_twin;
				
				up_right.next=top;
				top.prev=up_right;
				up_right.face=new_face;
				
				top.next=down_left;
				down_left.prev=top;
				down_left.face=new_face;
				
				down_left.next=base_twin;
				base_twin.prev=down_left;
				
				// add barycenter
				addBary_raw(pdcel,new_face,false);
				
				// create red
				up_red=new RedHEdge(up_right);
				up_right.myRedEdge=up_red;
				top_red=new RedHEdge(top);
				top.myRedEdge=top_red;
				down_red=new RedHEdge(down_left);
				down_left.myRedEdge=down_red;
				
				// link red
				up_red.prevRed=prev_red;
				prev_red.nextRed=up_red;
				
				up_red.nextRed=top_red;
				top_red.prevRed=up_red;
				
				top_red.nextRed=down_red;
				down_red.prevRed=top_red;
				
				down_red.nextRed=next_red;
				next_red.prevRed=down_red;
				
				base_twin.twin.myRedEdge=null;
				
				// left reds typically switchback, so remove
				if (down_left.myRedEdge.nextRed==up_left.myRedEdge) {
					top_red.nextRed=up_left.myRedEdge.nextRed;
					up_left.myRedEdge.nextRed.prevRed=top_red;
					
					up_left.myRedEdge=null;
					down_left.myRedEdge=null;
					
					lastV.redFlag=false;
				}
				lastV.bdryFlag=0;
			} // end of while
	
			// if closing up, need one more face
			if (closeup) {
	
				base_twin = twin_next;
				base_red = base_twin.twin.myRedEdge;
				prev_red = base_red.prevRed;
				next_red = base_red.nextRed;
				lastV=nextV;
				nextV=base_twin.twin.origin;
				up_left=up_right;
				down_left=down_right;
				
				down_right=firstRed.myEdge;
				up_right=down_right.twin;
				
				v_left=v_right;
				v_right=firstRed.myEdge.origin;
	
				// top
				top = new HalfEdge(v_right);
				v_right.halfedge = top;
				top_tw = new HalfEdge(v_left);
				top_tw.face = outFace;
				new_face = new dcel.Face(pdcel.vertCount+1);
				top.face = new_face;
				count++;
				
				top.twin = top_tw;
				top_tw.twin = top;
				
				top_tw.next=up_right.next;
				up_right.next.prev=top_tw;
				
				top_tw.prev=down_left.prev;
				down_left.prev.next=top_tw;
				
				// link around face
				base_twin.next=up_right;
				up_right.prev=base_twin;
				base_twin.face=new_face;
				new_face.edge=base_twin;
				
				up_right.next=top;
				top.prev=up_right;
				up_right.face=new_face;
				
				top.next=down_left;
				down_left.prev=top;
				down_left.face=new_face;
				
				down_left.next=base_twin;
				base_twin.prev=down_left;
	
				// create barycenter
				addBary_raw(pdcel,new_face,false);
				
				// create red
				up_red=new RedHEdge(up_right);
				up_right.myRedEdge=up_red;
				top_red=new RedHEdge(top);
				top.myRedEdge=top_red;
				down_red=new RedHEdge(down_left);
				down_left.myRedEdge=down_red;
				
				// link red
				up_red.prevRed=prev_red;
				prev_red.nextRed=up_red;
				
				up_red.nextRed=top_red;
				top_red.prevRed=up_red;
				
				top_red.nextRed=down_red;
				down_red.prevRed=top_red;
				
				down_red.nextRed=next_red;
				next_red.prevRed=down_red;
				
				base_twin.twin.myRedEdge=null;
				
				// left reds typically switchback, so remove
				if (down_left.myRedEdge.nextRed==up_left.myRedEdge) {
					top_red.nextRed=up_left.myRedEdge.nextRed;
					up_left.myRedEdge.nextRed.prevRed=top_red;
					
					up_left.myRedEdge=null;
					down_left.myRedEdge=null;
	
					lastV.redFlag=false;
				}
				
				// right typically switchback, so remove
				if (prev_red==firstRed) {
					firstRed.prevRed.nextRed=top_red;
					top_red.prevRed=firstRed.prevRed;
					up_right.myRedEdge=null;
					firstRed.myEdge.myRedEdge=null;
	
					nextV.redFlag=false;
				}
				lastV.bdryFlag=0;
			}
	
			// Only one? barycenter not connected to interior
			if (count==1) {
				pdcel.redChain=null;
			}
			return count;
	  }
	  
	  /**
	   * If 'rededge' backtracks, we can shrink red chain 
	   * (moving 'redChain' as necessary). No action? return
	   * 'rededge'. If red chain is just two edges, 
	   * this becomes a sphere, return null. Else return next
	   * downstream red after backtrack.
	   * @param rededge RedHEdge
	   * @return RedHEdge, possibly null
	   */
	  public static RedHEdge contractRed_raw(PackDCEL pdcel,RedHEdge rededge) {
		  HalfEdge he=rededge.myEdge;
		  if (rededge.twinRed==null)
			  return rededge;
		  HalfEdge twinhe=rededge.twinRed.myEdge;
		  
		  // Only 2 red edges? make a sphere
		  if (rededge.nextRed.nextRed==rededge) { 
			  pdcel.redChain=null;
			  he.origin.redFlag=false;
			  he.origin.bdryFlag=0;
			  he.myRedEdge=null;
			  twinhe.origin.redFlag=false;
			  twinhe.origin.bdryFlag=0;
			  twinhe.myRedEdge=null;
			  return null;
		  }
		  
		  if (rededge.prevRed==rededge.twinRed) {
			  if (rededge==pdcel.redChain || rededge.prevRed==pdcel.redChain)
				  pdcel.redChain=rededge.nextRed;
			  
			  rededge.nextRed.prevRed=rededge.prevRed.prevRed;
			  rededge.prevRed.prevRed.nextRed=rededge.nextRed;
			  
			  he.myRedEdge=null;
			  twinhe.myRedEdge=null;
			  he.origin.redFlag=false;
			  he.origin.bdryFlag=0;
			  
			  return rededge.nextRed;
		  }
		  
		  if (rededge.nextRed==rededge.twinRed) {
			  if (rededge==pdcel.redChain || rededge.nextRed==pdcel.redChain)
				  pdcel.redChain=rededge.twinRed.nextRed;
			  
			  rededge.prevRed.nextRed=rededge.nextRed.nextRed;
			  rededge.nextRed.nextRed.prevRed=rededge.prevRed;
			  
			  he.myRedEdge=null;
			  twinhe.myRedEdge=null;
			  twinhe.origin.redFlag=false;
			  twinhe.origin.bdryFlag=0;
			  
			  return rededge.nextRed.nextRed;
		  }
		  
		  return rededge; // return 'rededge' to indicate no action
	  }
	  
	  /**
	   * Given initial HalfEdge <v.w>, do two things: 
	   * (1) advance in half-hex direction (pass two edges on left) 
	   * for new 'baseEdge' <w,u> and
	   * (2) flip the next clw edge about v, <v,c>, if possible.
	   * Return two 'HalfEdge's: [0] is new 'baseEdge' and
	   * [1] is the edge resulting from the flip. If we can't
	   * advance, then return null. If we an advance, [1] may
	   * be null if we can't flip. 
	   * Note that 'RedChain' may be null if red edge is flipped.
	   * Calling routine must complete the combinatorics.
	   * @param pdcel PackDCEL
	   * @param edge HalfEdge
	   * @return HalfEdge[2], null if can't advance
	   */
	  public static HalfEdge[] flipAdvance_raw(PackDCEL pdcel, 
			  HalfEdge edge) {
		  HalfEdge[] ans=new HalfEdge[2];

		  // can we advance?
		  HalfEdge adedge=edge.HHleft();
		  if (adedge==null)
			  return null;
		  
		  // if edge's end is deg 3, then reverse, but no flip
		  if (adedge==edge.twin) { 
			  ans[0]=adedge;
			  ans[1]=null;
			  return ans;
		  }

		  // can we flip? get next clw spoke about v
		  HalfEdge fledge=null;
		  if (edge.twin.face==null || edge.twin.face.faceIndx>=0) {
			  HalfEdge tryedge=edge.twin.next;
			  fledge=RawDCEL.flipEdge_raw(pdcel, tryedge);
		  }
		  ans[0]=adedge;
		  ans[1]=fledge; // could be null if flip is illegal
		  return ans;
	  }

	  /**
	   * Flip an edge in a triangulation, subject to combinatoric
	   * conditions on 'edge':
	   * + must be shared by two non-ideal faces. 
	   * + must not have an edge of degree 3.
	   * + if end of degree 4, it cannot have nghb of degree 3.
	   * + new edge ends cannot already be neighbors.
	   * To "flip" means to replace 'edge' by the other 
	   * diagonal in the union of its two faces. The 
	   * numbers of faces, edges, and verts is unchanged.
	   * If 'edge' is in red chain, then set 'redChain' null. 
	   * Calling routine handles processing.
	   * 
	   * @param pdcel PackDCEL
	   * @param edge  HalfEdge
	   * @return HalfEdge, null failure
	   */
	  public static HalfEdge flipEdge_raw(PackDCEL pdcel, HalfEdge edge) {
		  
		  if (pdcel.isBdryEdge(edge))
			  return null;
		  // check conditions
		  int v=edge.origin.vertIndx;
		  int w=edge.twin.origin.vertIndx;
		  int n;
		  if ((n=pdcel.countPetals(v))<=4) {
			  if (n<=3)
				  return null;
			  if (n==4) {
				  HalfEdge he=pdcel.vertices[v].halfedge;
				  do {
					  if (pdcel.countPetals(he.twin.origin.vertIndx)<=3)
						  return null;
					  he=he.prev.twin; // cclw
				  } while (he!=pdcel.vertices[v].halfedge);
			  }
		  }
		  if ((n=pdcel.countPetals(w))<=4) {
			  if (n<=3)
				  return null;
			  if (n==4) {
				  HalfEdge he=pdcel.vertices[w].halfedge;
				  do {
					  if (pdcel.countPetals(he.twin.origin.vertIndx)<=3)
						  return null;
					  he=he.prev.twin; // cclw
				  } while (he!=pdcel.vertices[w].halfedge);
			  }
		  }

		  if (edge.myRedEdge != null) // in redchain
			  pdcel.redChain = null;
		  Vertex leftv = edge.next.next.origin;
		  Vertex rightv = edge.twin.next.next.origin;
		  
		  // are leftv/rightv already nghbs?
		  if (pdcel.findHalfEdge(new EdgeSimple(leftv.vertIndx,rightv.vertIndx))!=null)
			  return null;
		  
		  HalfEdge new_edge = new HalfEdge(leftv);
		  HalfEdge new_twin = new HalfEdge(rightv);

		  new_edge.twin = new_twin;
		  new_twin.twin = new_edge;

		  // check end verts 'halfedge's
		  if (edge.origin.halfedge == edge)
			  edge.origin.halfedge = edge.prev.twin; // cclw spoke
		  if (edge.twin.origin.halfedge == edge.twin)
			  edge.twin.origin.halfedge = edge.twin.prev.twin; // cclw spoke

		  // save some info for later
		  HalfEdge hn = edge.next;
		  HalfEdge hp = edge.prev;
		  HalfEdge twn = edge.twin.next;
		  HalfEdge twp = edge.twin.prev;

		  // new 'alpha' needed? (packData.alpha should not change)
		  if (edge==pdcel.alpha) 
			  pdcel.alpha=hp.twin;
		  else if (edge.twin==pdcel.alpha)
			  pdcel.alpha=twn.twin;
			  
		  // reconnect things
		  new_twin.next = hp;
		  hp.prev = new_twin;

		  hp.next = twn;
		  twn.prev = hp;

		  twn.next = new_twin;
		  new_twin.prev = twn;

		  new_edge.next = twp;
		  twp.prev = new_edge;

		  twp.next = hn;
		  hn.prev = twp;

		  hn.next = new_edge;
		  new_edge.prev = hn;

		  return new_edge;
	  }

	  /**
	   * Either hex or bary refinie the given DCEL. Start either
	   * process by first dividing each edge in half and either 
	   * adding edges between the new edge vertices (hex refining)
	   * or adding a bary center and new edges (bary refining).
	   * The 'pdcel.redChain' should remain in tact, but is subdivided 
	   * to get the new red chain. All original vertices have their 
	   * original 'halfedge's, 'vutil' holds index of reference 
	   * verts (see 'reapVUtils')
	   * Process involves cycling original spokes of original 
	   * vertices. At the end, 'vertices' is up to date, 'faces' and 'edges' 
	   * are outdated, but ideal faces and their indices should
	   * remain. Should be able to repeat 'Ntimes' times before 
	   * calling routine processes combinatorics.
	   * 
	   * (Nominally works for non-triangular faces, but that has
	   * not been tested. Also can be repeated 'Ntimes', but that
	   * also has not been thoroughly tested.)
	   *  
	   * @param pdcel  PackDCEL
	   * @param Ntimes int
	   * @param baryFlag boolean; true, then do bary refine, else hex
	   * @return int number of repeats
	   */
		public static int hexBaryRefine_raw(PackDCEL pdcel, int Ntimes,boolean baryFlag) {

			// inherited 'vutil' refers back to original vertex
			// NOTE: probably not useful when Ntimes>1.
			for (int v = 1; v <= pdcel.vertCount; v++)
				pdcel.vertices[v].vutil = v;

			int tick = 0;
			while (tick < Ntimes) {
				tick++;

				// 'eutil' flags:
				// 0 means untouched yet
				// 1 means this edge has been subdivided
				// 2 means this edge's face has been subdivided.
				pdcel.zeroEUtil();

				// we loop through the original vertices, and
				// for each vert loop through its faces,
				// splitting its edges (and also their twins),
				// then adding its new interior, depending on
				// hex or bary process
				int origVertCount = pdcel.vertCount;

				HalfEdge newEdge;
				HalfEdge n_base, n_newEdge, n_newTwin;
				HalfEdge n_base_tw = null;
				Vertex midVert, n_midVert;

				for (int v = 1; v <= origVertCount; v++) {
					HalfLink fflower = pdcel.vertices[v].getEdgeFlower();
					int sz = fflower.size();
					fflower.add(fflower.get(0));
					for (int j = 0; j < sz; j++) {
						HalfEdge spoke = fflower.get(j);

						// if not yet processed and not an edge of
						// an ideal face, we create a face so we
						// can call some face methods.

						// 'spoke' is done or is edge of ideal face
						if (spoke.eutil == 2 || (spoke.myRedEdge == null && spoke.twin.myRedEdge != null)) {
							continue;
						}

						// else, will process this edge and its face
						spoke.face = new Face(1);
						HalfLink fedges = spoke.face.getEdges(spoke);

						// only want edges starting at original vertices
						int n = fedges.size();
						for (int i = n - 1; i >= 0; i--) {
							if (fedges.get(i).origin.vertIndx > origVertCount)
								fedges.remove(i);
						}

						// now count and close up
						n = fedges.size();
						fedges.add(fedges.getFirst());

						// need some info to build center face later
						HalfEdge[] centedges = new HalfEdge[n + 1];

						// get first divided edge
						n_base = spoke;

						// 'base' already divided; copy needed elements
						if (n_base.eutil == 1) {
							n_newEdge = n_base.next;
							n_newTwin = n_base.twin;
							n_midVert = n_newEdge.origin;
							n_midVert.halfedge = n_newEdge;
							n_base.eutil = 2;
						}

						// else, divide this base
						else {
							n_base.eutil = 2;
							n_base_tw = n_base.twin;
							n_base_tw.eutil = 1;

							RedHEdge redge = n_base.myRedEdge;
							RedHEdge tredge = n_base.twin.myRedEdge;

							// two new edges
							n_newEdge = new HalfEdge();
							n_newEdge.eutil = 1;
							n_newEdge.invDist = n_base.invDist;
							n_newEdge.schwarzian = n_base.schwarzian;
							n_newTwin = new HalfEdge();
							n_newTwin.eutil = 1;
							n_newTwin.invDist = n_base.invDist;
							n_newTwin.schwarzian = n_base.schwarzian;

							// new vertex
							n_midVert = new Vertex(++pdcel.vertCount);
							pdcel.vertices[pdcel.vertCount] = n_midVert;
							n_midVert.halfedge = n_newEdge;
							n_midVert.vutil = n_base.origin.vutil;
							n_newEdge.origin = n_midVert;
							n_newTwin.origin = n_midVert;

							// fix twins
							n_base.twin = n_newTwin;
							n_newTwin.twin = n_base;

							n_newEdge.twin = n_base_tw;
							n_base_tw.twin = n_newEdge;

							// fix
							n_newEdge.next = n_base.next;
							n_newEdge.next.prev = n_newEdge;

							n_base.next = n_newEdge;
							n_newEdge.prev = n_base;

							n_newTwin.next = n_base_tw.next;
							n_newTwin.next.prev = n_newTwin;

							n_newTwin.prev = n_base_tw;
							n_base_tw.next = n_newTwin;

							// if this is a red edge
							if (redge != null || tredge != null) {
								n_midVert.redFlag = true;

								// does 'edge' have red edge?
								if (redge != null) {
									RedHEdge newRedge = new RedHEdge(n_newEdge);
									n_newEdge.myRedEdge = newRedge;
									newRedge.nextRed = redge.nextRed;
									newRedge.prevRed = redge;
									redge.nextRed.prevRed = newRedge;
									redge.nextRed = newRedge;
								}
								// does 'tedge' have red edge?
								if (tredge != null) {
									RedHEdge newRedge = new RedHEdge(n_newTwin);
									n_newTwin.myRedEdge = newRedge;
									newRedge.nextRed = tredge.nextRed;
									newRedge.prevRed = tredge;
									tredge.nextRed.prevRed = newRedge;
									tredge.nextRed = newRedge;
								}
								// if both, then set up red twinning
								if (redge != null && tredge != null) {
									redge.twinRed = n_newTwin.myRedEdge;
									n_newTwin.myRedEdge.twinRed = redge;
									tredge.twinRed = n_newEdge.myRedEdge;
									n_newEdge.myRedEdge.twinRed = tredge;
								}
							}
						}

						// now do rest of edges, divide the face
						for (int k = 0; k < n; k++) {

							// shift of previous edge
							newEdge = n_newEdge;
							midVert = n_midVert;

							// load/divide next side about this face
							n_base = fedges.get(k + 1);
							// if (n_base.eutil==2)
							// throw new CombException("edge "+n_base+ " should not have been handled.");

							// 'base' already divided; copy needed elements
							if (n_base.eutil >= 1) {
								n_newEdge = n_base.next;
								n_newTwin = n_base.twin;
								n_midVert = n_newEdge.origin;
								n_midVert.halfedge = n_newEdge;
								n_base.eutil = 2;
							}

							// else, divide this side
							else {
								n_base.eutil = 2;
								n_base_tw = n_base.twin;
								n_base_tw.eutil = 1;

								RedHEdge redge = n_base.myRedEdge;
								RedHEdge tredge = n_base.twin.myRedEdge;

								// two new edges
								n_newEdge = new HalfEdge();
								n_newEdge.eutil = 1;
								n_newEdge.invDist = n_base.invDist;
								n_newEdge.schwarzian = n_base.schwarzian;
								n_newTwin = new HalfEdge();
								n_newTwin.eutil = 1;
								n_newTwin.invDist = n_base.invDist;
								n_newTwin.schwarzian = n_base.schwarzian;

								// new vertex
								n_midVert = new Vertex(++pdcel.vertCount);
								pdcel.vertices[pdcel.vertCount] = n_midVert;
								n_midVert.halfedge = n_newEdge;
								n_midVert.vutil = n_base.origin.vutil;
								n_newEdge.origin = n_midVert;
								n_newTwin.origin = n_midVert;

								// fix twins
								n_base.twin = n_newTwin;
								n_newTwin.twin = n_base;

								n_newEdge.twin = n_base_tw;
								n_base_tw.twin = n_newEdge;

								// fix
								n_newEdge.next = n_base.next;
								n_newEdge.next.prev = n_newEdge;

								n_base.next = n_newEdge;
								n_newEdge.prev = n_base;

								n_newTwin.next = n_base_tw.next;
								n_newTwin.next.prev = n_newTwin;

								n_newTwin.prev = n_base_tw;
								n_base_tw.next = n_newTwin;

								// if this is a red edge
								if (redge != null || tredge != null) {
									n_midVert.redFlag = true;
									n_newEdge.origin = n_midVert;
									n_newTwin.origin = n_midVert;

									// does 'edge' have red edge?
									if (redge != null) {
										RedHEdge newRedge = new RedHEdge(n_newEdge);
										n_newEdge.myRedEdge = newRedge;
										newRedge.nextRed = redge.nextRed;
										newRedge.prevRed = redge;
										redge.nextRed.prevRed = newRedge;
										redge.nextRed = newRedge;
									}
									// does 'tedge' have red edge?
									if (tredge != null) {
										RedHEdge newRedge = new RedHEdge(n_newTwin);
										n_newTwin.myRedEdge = newRedge;
										newRedge.nextRed = tredge.nextRed;
										newRedge.prevRed = tredge;
										tredge.nextRed.prevRed = newRedge;
										tredge.nextRed = newRedge;
									}
									// if both, then set up red twinning
									if (redge != null && tredge != null) {
										redge.twinRed = n_newTwin.myRedEdge;
										n_newTwin.myRedEdge.twinRed = redge;
										tredge.twinRed = newEdge.myRedEdge;
										n_newEdge.myRedEdge.twinRed = tredge;
									}
								}
							}

							// hex refining is done incrementally as we
							//    split the sides.
							if (!baryFlag) { 
								// form face at corner of side and next side
								HalfEdge opp = new HalfEdge(n_midVert);
								HalfEdge opp_tw = new HalfEdge(midVert);
								opp.twin = opp_tw;
								opp_tw.twin = opp;

								centedges[k] = opp_tw;

								// link around this
								n_base.next = opp;
								opp.prev = n_base;

								opp.next = newEdge;
								newEdge.prev = opp;
							}
							
						} // finish loop through sides

						if (!baryFlag) {
							centedges[n] = centedges[0];
							for (int k = 0; k < n; k++) {
								centedges[k].next = centedges[k + 1];
								centedges[k + 1].prev = centedges[k];
							}
						}
						
						// bary refining is done after all sides are split
						else { 
							
							// new vertex
							Vertex baryCent=new Vertex(++pdcel.vertCount);
							pdcel.vertices[pdcel.vertCount]=baryCent;
							baryCent.vutil=spoke.origin.vertIndx;
							
							// create first new edges
							HalfEdge new_in=new HalfEdge(spoke.origin);
							HalfEdge new_out=new HalfEdge(baryCent);
							baryCent.halfedge=new_out;
							HalfEdge hold_first_in=new_in; // needed for last face
							
							new_in.twin=new_out;
							new_out.twin=new_in;
							
							// now proceed about the outer edges
							HalfEdge outer=spoke;
							HalfEdge stopper;
							HalfEdge old_out;
							do {
								stopper=outer.next; // to stop the loop
								
								old_out=new_out;
								
								new_in=new HalfEdge(outer.twin.origin);
								new_out=new HalfEdge(baryCent);
								
								new_in.twin=new_out;
								new_out.twin=new_in;
								
								// link around to form new face
								outer.next=new_in;
								new_in.prev=outer;
								new_in.next=old_out;
								old_out.prev=new_in;
								old_out.next=outer;
								outer.prev=old_out;
								
								// mark these edges as done
								new_in.eutil=2;
								old_out.eutil=2;
								outer.eutil=2; 
								
								outer=stopper; // next outer edge to process
								stopper=outer.next;
							} while (stopper!=spoke);
							
							// complete the last face
							outer.next=hold_first_in;
							hold_first_in.prev=outer;
							hold_first_in.next=new_out;
							new_out.prev=hold_first_in;
							new_out.next=outer;
							outer.prev=new_out;
							
							// mark as done
							outer.eutil=2;
							hold_first_in.eutil=2;
							new_out.eutil=2;
						}

					} // done with spokes of v
				} // done with all vertices
			} // done with 'Ntimes' passes

			return Ntimes;
		}
		  
		/**
		 * Add a new vertex to some or all vertices bounding 
		 * an ideal face, start with 'v' and go cclw about the 
		 * boundary component (clw about the ideal face) until 
		 * getting to 'w'. Calling must check that v, w on same 
		 * bdry comp, and handles combinatorics. New vert should 
		 * have the largest index. 
		 * Exception if bdry comp has just two edges.
		 * @param pdcel PackDCEL
		 * @param v int
		 * @param w int
		 * @return int, count of edgepairs added, 0 on error
		 */
		public static int addIdeal_raw(PackDCEL pdcel,int v,int w) {
			
			boolean debug=false;
			
			HalfEdge vedge=pdcel.vertices[v].halfedge;
			HalfEdge nextedge=vedge.twin.prev.twin;
			HalfEdge prevedge=vedge.twin.next.twin;
			  
			// tent 'vedge'; 'vertCount', 'vertices' should be updated
			RawDCEL.addVert_raw(pdcel,nextedge.origin.vertIndx);
			int newVindx=pdcel.vertCount;
			  
			int count=2;
			  
			// now proceed along the bdry edge adding new edges
			while (nextedge!=prevedge && nextedge.origin.vertIndx!=w) {
				nextedge=nextedge.twin.prev.twin;
				RawDCEL.addEdge_raw(pdcel,newVindx,nextedge.origin.vertIndx);
				count++;
				if (debug) // pdcel.p.getCenter(300);
					DCELdebug.redConsistency(pdcel);
			  } 
			  
			  // finish if we reach w (which thus cannot be v)
			  if (nextedge.origin.vertIndx==w) 
				  return count;
			  
			  // otherwise, left with bdry having 3 edges?
			  Face triface=new Face(-1);
			  triface.edge=prevedge.twin;
			  prevedge.twin.face=triface;
			  RawDCEL.addIdealFace_raw(pdcel,prevedge.twin.face);
			  if (debug) { 
//				  v=4;DCELdebug.vertConsistency(pdcel,v);
				  DCELdebug.redConsistency(pdcel);
			  }
			  return count;
		}
			  
	/**
	 * If ideal face 'f' has precisely three edges, then 
	 * make it into a normal face. Check for sphere and
	 * adjust red chain as necessary.
	 * @param pdcel
	 * @param f dcel.face
	 * @return int 1 on success
	 */
	public static int addIdealFace_raw(PackDCEL pdcel,Face f) {
		if (f.faceIndx>=0)
			throw new ParserException("Face "+f.faceIndx+
					" appears not to be ideal");
			
		// get edges around this face
		HalfLink hlink=f.getEdges();
		hlink=HalfLink.reverseLink(hlink); // order clw
		if (hlink.size()!=3) 
			throw new ParserException("face "+f.faceIndx+
				  " must have precisely three vertices");
		
		// set edge 'face's to null
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext()) {
			his.next().face=null;
		}

		// Get the three red edges.
		RedHEdge[] reds=new RedHEdge[3];
		for (int j=0;j<3;j++) {
			reds[j]=hlink.get(j).twin.myRedEdge;
			if (reds[j]==null)
				throw new ParserException("Face "+f.faceIndx+" missing red edges");
		}
			
		// Count twinned red edges; if twinned reds impinge
		//   at the end of reds[j], then hits[j] is set to 1.
		int[] hits=new int[3];
		int rcount=0;
		for (int j=0;j<3;j++) {
			if (reds[j].nextRed!=reds[(j+1)%3]) {
				hits[j]=1;
				rcount++;
			}
		}

		// rcount 0 means this is a sphere; just 1, means red chain 
		//   should contract; if two or more, check for some collapse.
		if (rcount==0) { // a sphere 
			for (int j=0;j<3;j++) {
				Vertex vert=hlink.get(j).origin;
				vert.redFlag=false;
				vert.bdryFlag=0;
				
				// orphan red edges
				reds[j].myEdge.myRedEdge=null;
				reds[j].nextRed=reds[j].prevRed=null;
			}
			pdcel.redChain=null;
				return 1;
		}

		// Find offset to point to reference corner
		int offset=0;
		if (rcount==1) {
			for (int j=0;j<3;j++)
				if (hits[j]==1)
					offset=(j+1)%3;
		}
		else if (rcount==2) {
			for (int j=0;j<3;j++) 
				if (hits[j]==0)
					offset=(j+2)%3;
		}
		// else all corners have twinned reds coming in:
		//   red chain gets a triple point at one of the
		//   corners: choose the one with highest degree.
		else { 
			int[] degs=new int[3];
			for (int j=0;j<3;j++)
				degs[j]=reds[j].myEdge.origin.getNum();
			int bestj=0;
			if (degs[1]>degs[0])
				bestj=1;
			if (degs[2]>degs[bestj])
				bestj=2;
			offset=(bestj+1)%3;
		}
			
		// Now we get red edge 'rhe' ending at reference corner;
		//   remove rhe as a red edge, create red twins for
		//   prevred and nxtred. 
		RedHEdge rhe=reds[(offset+2)%3]; 
		RedHEdge prevred=reds[(offset+1)%3];
		RedHEdge nxtred=reds[offset];
			
		// new twins
		RedHEdge prev_twin=new RedHEdge(prevred.myEdge.twin);
		prev_twin.myEdge.myRedEdge=prev_twin;
		RedHEdge nxt_twin=new RedHEdge(nxtred.myEdge.twin);
		nxt_twin.myEdge.myRedEdge=nxt_twin;
			
		// link in
		prev_twin.prevRed=rhe.prevRed;
		rhe.prevRed.nextRed=prev_twin;
		prev_twin.twinRed=prevred;
		prevred.twinRed=prev_twin;
			
		prev_twin.nextRed=nxt_twin;
		nxt_twin.prevRed=prev_twin;
		
		nxt_twin.nextRed=rhe.nextRed;
		rhe.nextRed.prevRed=nxt_twin;
		nxt_twin.twinRed=nxtred;
		nxtred.twinRed=nxt_twin;
			
		// orphan rhe
		rhe.myEdge.myRedEdge=null;
			
		// recursively contract red chain? No contraction if
		//    rcount==3, 1 edge if rcount==2, and many if rcount==1.
		RedHEdge nextedge=prev_twin;
		RedHEdge shiftred=null;
		while (nextedge!=shiftred) {
			shiftred=nextedge; // DCELdebug.redConsistency(pdcel); 
			nextedge=RawDCEL.contractRed_raw(pdcel,shiftred);
		}
			
		// DCELdebug.printRedChain(pdcel.redChain,null);
			
		// create standin new interior face
		Face newface=new Face(pdcel.faceCount+1);
		newface.edge=hlink.get(0);
		for (int j=0;j<3;j++) {
			HalfEdge he=hlink.get(j);
			he.face=newface;
			he.origin.bdryFlag=0; // all become interior
		}
		return 1;
	}
		  	  
	/**
	 * Find an common edge opposite to both v and w: 
	 * v will be to its left, w to its
	 * right.
	 * 
	 * @param v int
	 * @param w int
	 * @return EdgeSimple, null on failure
	 */
	public static HalfEdge getCommonEdge(PackDCEL pdcel, int v, int w) {
		HalfEdge he = pdcel.vertices[v].halfedge; // spoke
		do {
			if (he.next.twin.next.twin.origin.vertIndx == w) {
				return he.next;
			}
			he = he.prev.twin; // cclw spoke
		} while (he != pdcel.vertices[v].halfedge);
		return null;
	}

	/**
	 * Build a raw PackDCEL consisting of n>=3 copies of 'base'
	 * attached about its bdry vertex 'v', each pasting involving
	 * 'sides'>=1 bdry edges from 'v'.
	 * @param base PackDCEL
	 * @param v int
	 * @param sides int
	 * @param n int
	 * @return PackDCEL, null on error
	 */
	public static PackDCEL polyCluster(PackDCEL base,int v,int sides,int n) {
		if (sides<1 || n<3 || v<0 || v>base.vertCount)
			throw new ParserException("'polyCluster: bad data");
		Vertex vert=base.vertices[v];
		if (vert.bdryFlag==0)
			throw new ParserException("'polyCluster': "+v+" is not on the bdry");
		
		// for last pasting, need vertex 'w' which is 'sides' 
		//   edges cclw from 'v'
		Vertex wert=base.vertices[v];
		for (int j=0;j<sides;j++) {
			wert=wert.halfedge.twin.origin;
		}
		int w=wert.vertIndx;
		
		PackDCEL pdcel=CombDCEL.cloneDCEL(base);
		PackDCEL pdcel2=CombDCEL.cloneDCEL(base); // n=2;
		
		for (int j=2;j<n;j++) {
			PackDCEL next=CombDCEL.cloneDCEL(base);
			pdcel=CombDCEL.d_adjoin(pdcel,next,v,v,sides);
		}
		CombDCEL.d_adjoin(pdcel,pdcel2,w,v,2*sides);
		
		return pdcel;
	}

	/**
	 * Simply swap vertices v and w in a raw PackDCEL
	 * @param pdcel PackDCEL
	 * @param v int
	 * @param w int
	 * @return 0 on error
	 */
	public static int swapNodes_raw(PackDCEL pdcel,int v,int w) {
		if (v<1 || v>pdcel.vertCount || w<1 || w>pdcel.vertCount)
			return 0;
		if (v==w) // nothing to do
			return 1;
		Vertex hold=pdcel.vertices[v];
		pdcel.vertices[v]=pdcel.vertices[w];
		pdcel.vertices[w]=hold;
		pdcel.vertices[w].vertIndx=w;
		pdcel.vertices[v].vertIndx=v;
		return 1;
	}
	
	/**
	 * Wipe out the linked 'RedHEdge's starting with 'redChain': 
	 * null 'myEdge' references to 'myRedEdge', null 'redChain', 
	 * orphan all 'RedHEdges' so they can be garbaged. 
	 * Return 0 if there seems to be a problem -- e.g., the 
	 * redChain isn't closed.
	 * 
	 * NOTE: not sure this is ever necessary; generally, 
	 * just setting 'redChain' to null is enough.
	 * 
	 * @param pdcel PackDCEL
	 * @param redchain RedHEdge
	 * @return int count if seems successfull, -count if problem
	 */
	public static int wipeRedChain(PackDCEL pdcel,RedHEdge redchain) {
		int count=0;
		RedHEdge start=redchain; // so 'redchain' doesn't get garbaged
		RedHEdge rtrace=redchain;
		RedHEdge hold=null;
		int safety=5000;
		do {
			safety--;
			rtrace.myEdge.myRedEdge=null;
			rtrace.prevRed=null;
			hold=rtrace.nextRed;
			rtrace.nextRed=null; // here 'rtrace' should be orphaned
			count++;
			rtrace=hold;
		} while (hold!=redchain && safety>0);
		pdcel.redChain=null;

		if (safety==0) // didn't close up
			return -count;
		return count;
	}
	
}
