package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;

/**
 * This file is for static methods manipulating combinatoric
 * structures. These "*_raw" methods typically handle radii
 * and centers only in copying them to new elements and there
 * is almost no dependence on the PackData parent.
 * The routines expect 'vertices' to be in place and for bdry
 * edges to be identifiable based on face indices. They may
 * update 'vertices', but arrays 'edges' and 'faces' are not 
 * updated. Red chain may be adjusted, but will be null if 
 * there are problems modifying it (sometimes because it is 
 * just too complicated). 'PackDCEL.triData' is set to null, 
 * since this data is generally outdated.
 * 
 * Typical process: 
 *   (1) calling routine may 'zeroEUtil' to zero 'eutil'.
 *   (2) calling routine runs '*_raw' method to modify the dcel 
 *   	 structure.
 *   (3) the '*_raw' routine should ensure 'vertices' and 
 *       'vertCount' are updated and space is allocated and
 *       ideal faces are created and give negative index. If 
 *       red chain could not be safely modified in '*_raw', 
 *       then set 'redChain' null. 
 *   (4) for any new vertices, preliminary data (cent/rad/aim, etc.)
 *       are typically set from appropriat reference vert. 
 *   (5) calling routine runs 'fixDCEL', which computes red
 *   	 chain (if needed) and calls 'FillInside' and 'attachDCEL'.
 *
 * Sometimes '*_raw' may store info in 'PackDCEL.oldNew'. 
 * Many raw routines can be repeated several times before 
 * 'fixDCEL' is needed. I'm going to try this approach:
 * 
 *   (a) Before the first '*_raw' call, set 
 *       'packDCEL.oldNew' null.
 *   
 *   (b) during each call, compose 'packDCEL.oldNew' with local
 *       'oldnew' built during the routine; see 'VertexMap.followedBy'.
 *    
 *   (c) When done, 'packDCEL.oldNew' should have <orig,final>
 *       pairings which are used during 'attachDCEL' to copy
 *       data where needed.
 * 
 * @author kens
 *
 */
/**
 * @author kstephe2
 *
 */
public class RawManip {
	
	  
	/**
	 * Create a new PackDCEL seed with n petals
	 * @param n int
	 * @return PackDCEL
	 */
	public static PackDCEL seed_raw(int n) {
		if (n<3 || n>1000) 
			throw new ParserException("'seed' is limited "+
					"to degree between 3 and 1000");
		int[][] bouquet=new int[n+2][];
		bouquet[1]=new int[n+1];
		for (int j=0;j<n;j++)
			bouquet[1][j]=j+2;
		bouquet[1][n]=bouquet[1][0]; // close up
		for (int k=0;k<n;k++) {
			bouquet[k+2]=new int [3];
			bouquet[k+2][0]=(k+1)%n+2;
			bouquet[k+2][1]=1;
			bouquet[k+2][2]=((k+n)-1)%n+2;
		}
		return CombDCEL.getRawDCEL(bouquet,1);
	}

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
	 * Add a new vertex which splits this edge and connects
	 * to shared neighbors. So new vert degree is 3 for bdry
	 * edge, 4 for interior edge. Red chain should stay intact.
	 * Call to 'meldEdge_raw' should reverse this using the
	 * returned 'HalfEdge'.
	 * @param pdcel PackDCEL
	 * @param edge HalfEdge
	 * @return HalfEdge, 'edge', but with new 'midVert' as end
	 */
	public static HalfEdge splitEdge_raw(PackDCEL pdcel,HalfEdge edge) {
		
		// make room
		if (pdcel.vertCount+1>=pdcel.sizeLimit)
			pdcel.alloc_vert_space(pdcel.vertCount+11,true);
	
		// If bdry, ensure it is cclw edge, set 'twinIdeal'
		HalfEdge he=edge;
		if (he.face!=null && he.face.faceIndx<=0) // bdry
			he=edge.twin;
		boolean bdryCase=false;
		if (he.twin.face!=null && he.twin.face.faceIndx<=0)
			bdryCase=true;
				
		// hold some info
		Vertex vert=he.origin;
		Vertex wert=he.twin.origin;
		HalfEdge he_next=he.next;
		HalfEdge he_prev=he.prev;
		HalfEdge he_twin=he.twin;
		HalfEdge het_next=he.twin.next;
		HalfEdge het_prev=he.twin.prev;
		RedEdge redge = he.myRedEdge;
		RedEdge tredge = he.twin.myRedEdge;

		// create new edges to split 'edge'
		HalfEdge newEdge = new HalfEdge();
		newEdge.setInvDist(he.getInvDist());
		newEdge.setSchwarzian(he.getSchwarzian());
		HalfEdge newTwin = new HalfEdge();
		newTwin.face=he_twin.face; // in case this face is ideal
		newTwin.setInvDist(he.getInvDist());
		newTwin.setSchwarzian(he.getSchwarzian());

		// new vertex
		Vertex midVert = new Vertex(++pdcel.vertCount);
		if (bdryCase) {
			midVert.bdryFlag=1;
			midVert.aim=Math.PI;
		}
		else
			midVert.aim=2.0*Math.PI;
		midVert.rad=(vert.rad+wert.rad)/2.0;
		midVert.center=vert.center.add(wert.center).times(.05);
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
			RedEdge new_redge=new RedEdge(newEdge);
			newEdge.myRedEdge=new_redge;
			new_redge.setRadius(midVert.rad);
			new_redge.setCenter(new Complex(midVert.center));
			
			new_redge.nextRed=redge.nextRed;
			redge.nextRed.prevRed=new_redge;
			
			redge.nextRed=new_redge;
			new_redge.prevRed=redge;
		}
		
		// handle right side
		if (!bdryCase) {
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
				RedEdge new_redge=new RedEdge(newTwin);
				newTwin.myRedEdge=new_redge;
				new_redge.setRadius(tredge.getRadius());
				new_redge.setCenter(new Complex(tredge.getCenter()));
				
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
		
		pdcel.triData=null;
		return edge;
	}
	
	/**
	 * Split 'V' flower into two flowers, depending on 
	 * situation.  
	 * (1) 'uedge', 'wedge' both non-null, then V must be
	 * interior: first flower is u --> w about V, while 
	 * second is w --> u about 'newV'. Thus (V,newV,u), 
	 * (newV,V,w) are new oriented faces. Return new edge 
	 * (V,newV) (can be reversed via call to 'meldEdge' 
	 * using (V,newV)).
	 * (2) If 'wedge' is null, then V must be a bdry; 
	 * split the flower at 'uedge' (which must be interior 
	 * edge): first flower downstream bdry around 'newV' 
	 * to 'uedge'; second from 'uedge' around V to the 
	 * upstream bdry. Return new bdry edge (V,newV). Red 
	 * chain is adjusted. Reverse via 'meldEdge_raw' with 
	 * (V,newV)
	 * @param pdcel PackDCEL
	 * @param uedge HalfEdge
	 * @param wedge HalfEdge
	 * @return HalfEdge, new edge (V,newV)
	 */
	public static HalfEdge splitFlower_raw(PackDCEL pdcel,HalfEdge uedge,
			HalfEdge wedge) {
		if (uedge.isBdry())
			throw new ParserException("Given 'uedge' should be interior");

		// make room
		if (pdcel.vertCount+1>=pdcel.sizeLimit)
			pdcel.alloc_vert_space(pdcel.vertCount+11,true);

		Vertex V=uedge.origin;
		HalfLink spks=null;

		// ***** first case: boundary
		if (wedge==null) {
			double rad=.045;
			if (V.halfedge.myRedEdge!=null)
				rad=V.halfedge.myRedEdge.getRadius();
			
			// hold some info
			spks=V.getSpokes(V.halfedge);
			HalfEdge downspoke=V.halfedge; // downstream edge
			HalfEdge next_spoke=uedge.twin.next; // clw
			RedEdge newred=null;
			
			// scope out the red chain situation
			RedEdge red_out=V.halfedge.myRedEdge;
			RedEdge red_in=V.halfedge.twin.next.twin.myRedEdge;
			if (red_out.prevRed!=red_in) // too messy
				pdcel.redChain=null;
			if (pdcel.redChain!=null && pdcel.redChain==red_out)
				pdcel.redChain=red_out.nextRed;

			// new stuff: vert, bdry edge, twins, and maybe red edge
			Vertex newV=new Vertex(++pdcel.vertCount);
			pdcel.vertices[pdcel.vertCount]=newV;
			newV.cloneData(V);
			newV.bdryFlag=1;
			newV.redFlag=true;

			// new bdry edge and twin
			HalfEdge newbdry=new HalfEdge(newV);
			newV.halfedge=newbdry;
			newbdry.twin=new HalfEdge(downspoke.twin.origin);
			newbdry.twin.twin=newbdry;
			// new spoke 'newV' to end of 'uedge'
			HalfEdge newspoke=new HalfEdge(newV);
			newspoke.twin=new HalfEdge(uedge.twin.origin);
			newspoke.twin.twin=newspoke;
			newbdry.twin.face=downspoke.twin.face;
			// new red and links
			if (pdcel.redChain!=null) {
				newred=new RedEdge(newbdry);
				newbdry.myRedEdge=newred;
				newred.setRadius(rad);

				newred.nextRed=red_out;
				red_out.prevRed=newred;
					
				newred.prevRed=red_in;
				red_in.nextRed=newred;
			}

			// reset old origins
			HalfEdge he=spks.get(1);
			while (he!=wedge) {
				he.origin=newV;
				he=he.prev.twin;
			}
			
			// relink outer edges
			newbdry.twin.prev=downspoke.twin.prev;
			downspoke.twin.prev.next=newbdry.twin;
			downspoke.twin.origin=newV;

			newbdry.twin.next=downspoke.twin; 
			downspoke.twin.prev=newbdry.twin;
			
			// link in 'newbdry'
			he=spks.get(1);
			newbdry.next=downspoke.next;
			downspoke.next.prev=newbdry;
			
			newbdry.prev=he.twin;
			he.twin.next=newbdry;
			
			// link in 'newspoke' and twin
			newspoke.prev=downspoke;
			downspoke.next=newspoke;
			
			newspoke.twin.prev=uedge.twin.prev;
			uedge.twin.prev.next=newspoke.twin;
			
			newspoke.next=uedge.twin;
			uedge.twin.prev=newspoke;
			
			uedge.twin.next=downspoke;
			downspoke.prev=uedge.twin;
				
			if (he==uedge) {
				newspoke.twin.next=newbdry;
				newbdry.prev=newspoke.twin;
			}
			else {
				newspoke.twin.next=next_spoke;
				next_spoke.prev=newspoke.twin;
			}
			
			pdcel.triData=null;
			return V.halfedge;
		}
		
		// **** second, more typical, 
		if (wedge.origin!=uedge.origin)
			throw new DCELException(
					"edges' origins don't match");
		if (V.bdryFlag!=0)
			throw new ParserException(
					"given edge is supposed to be interior");
		
		// hold some info
		spks=V.getSpokes(wedge);
		HalfEdge w_twin=wedge.twin;
		HalfEdge u_twin=uedge.twin;
		
		// create newV and fix spokes
		Vertex newV=new Vertex(++pdcel.vertCount);
		pdcel.vertices[pdcel.vertCount]=newV;
		newV.cloneData(V);
		if (uedge.prev.twin!=wedge) {
			HalfEdge he=uedge.prev.twin;
			do {
				he.origin=newV;
				he=he.prev.twin; // cclw
			} while (he!=wedge);
		}
		V.halfedge=wedge;
		
		// deBugging.DCELdebug.vertConsistency(pdcel,V.vertIndx);

		// create <v,newV> and twin
		HalfEdge new_mid=new HalfEdge(V);
		new_mid.twin=new HalfEdge(newV);
		new_mid.twin.twin=new_mid;
		newV.halfedge=new_mid.twin;
		
		// 'new_uedge' and twin
		HalfEdge new_uedge=new HalfEdge(newV);
		new_uedge.twin=new HalfEdge(uedge.twin.origin);
		new_uedge.twin.twin=new_uedge;
		
		new_uedge.prev=uedge.prev;
		uedge.prev.next=new_uedge;
		
		new_uedge.next=uedge.next;
		uedge.next.prev=new_uedge;

		new_mid.twin.next=uedge;
		uedge.prev=new_mid.twin;

		new_uedge.twin.next=new_mid.twin;
		new_mid.twin.prev=new_uedge.twin;
		
		new_uedge.twin.prev=uedge;
		uedge.next=new_uedge.twin;
		
		// now the w side of things
		HalfEdge new_wedge=new HalfEdge(newV);
		new_wedge.twin=new HalfEdge(wedge.twin.origin);
		new_wedge.twin.twin=new_wedge;
		
		new_wedge.twin.prev=wedge.twin.prev;
		wedge.twin.prev.next=new_wedge.twin;
		
		new_wedge.twin.next=wedge.twin.next; 
		wedge.twin.next.prev=new_wedge.twin;

		new_wedge.prev=new_mid;
		new_mid.next=new_wedge;
		
		new_wedge.next=wedge.twin;
		wedge.twin.prev=new_wedge;

		wedge.twin.next=new_mid;
		new_mid.prev=wedge.twin;
		
		
	      // deBugging.DCELdebug.vertConsistency(pdcel,newV.vertIndx);
		
		// **** most typical:
		if (!V.redFlag) {
			pdcel.triData=null;
			return new_mid;
		}
		
		// Red chain could need adjustments
		
		// if needed, shift red twins of wedge and/or uedge
		RedEdge rtrace=null;
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
		RedEdge f1tof2=null;
		RedEdge f2tof1=null;
		Iterator<EdgeSimple> iot=in_out_pairs.iterator();
		while (iot.hasNext()) {
			EdgeSimple es=iot.next();
			int a=es.v; // index of incoming red edge
			int b=es.w; // following outgoing
			
			// do we cross?
			if (a<=spot_u && b>spot_u) { // crossing: flower 1 to 2
				f1tof2=new RedEdge(new_mid.twin);
				new_mid.twin.myRedEdge=f1tof2;
				newV.redFlag=true;
				
				spokes[a].twin.myRedEdge.nextRed=f1tof2;
				f1tof2.prevRed=spokes[a].twin.myRedEdge;
				
				spokes[b].myRedEdge.prevRed=f1tof2;
				f1tof2.nextRed=spokes[b].myRedEdge;
			}
			else if (a>spot_u && b<=spot_u) { // crossing: flower 2 to 1
				f2tof1=new RedEdge(new_mid);
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

		pdcel.triData=null;
		return new_mid; // should be <V,newV> 
	}
	
	/**
	 * Remove interior degree 3 'vert' (i.e., a "ball bearing")
	 * without introducing new bdry. Adjust 'vertices'. If 'vert' 
	 * is red, adjust red chain.
	 * @param pdcel PackDCEL
	 * @param vert Vertex
	 * @return int, orphaned vert index, 0 on failure
	 */
	public static int rmBary_raw(PackDCEL pdcel,Vertex vert) {
		if (vert.isBdry() || vert.getNum()!=3)
			return 0;

		HalfLink outer=vert.getOuterEdges();
		
		// red? divert red chain first (the most complicated work)
		if (vert.redFlag) {
			HalfLink spokes=vert.getEdgeFlower();
			RedEdge[] reds_in=new RedEdge[3];
			RedEdge[] reds_out=new RedEdge[3];
			int tick=0;
			for (int j=0;j<3;j++) {
				HalfEdge he=spokes.get(j);
				if (he.myRedEdge!=null) {
					reds_out[j]=he.myRedEdge;
					if (pdcel.redChain==reds_out[j])
						pdcel.redChain=pdcel.redChain.nextRed;
					if (he.myRedEdge.twinRed==null)
						throw new CombException(
								"vert "+vert.vertIndx+" can't be bdry.");
					reds_in[j]=he.myRedEdge.twinRed;
					if (pdcel.redChain==reds_in[j])
						pdcel.redChain=pdcel.redChain.prevRed;
					tick++;
				}
			}
			
			// Is 'vert' a triple red point? divert reds to pass 
			//    through corner 0.
			if (tick==3) {
				
				// divert red path from <2,v,0> to <2,0>
				RedEdge red2=new RedEdge(outer.get(2));
				RedEdge red2twin=new RedEdge(outer.get(2).twin);
				outer.get(2).myRedEdge=red2;
				outer.get(2).twin.myRedEdge=red2twin;
				
				red2.twinRed=red2twin;
				red2twin.twinRed=red2;
				
				red2.prevRed=reds_in[2].prevRed;
				red2.prevRed.nextRed=red2;
				red2.nextRed=reds_out[0].nextRed;
				red2.nextRed.prevRed=red2;
				
				red2twin.nextRed=reds_out[2].nextRed;
				red2twin.nextRed.prevRed=red2twin;
				red2twin.prevRed=reds_in[0].prevRed;
				red2twin.prevRed.nextRed=red2twin;

				// divert path from <0,v,1> to <0,1>
				RedEdge red0=new RedEdge(outer.get(0));
				RedEdge red0twin=new RedEdge(outer.get(0).twin);
				outer.get(0).myRedEdge=red0;
				outer.get(0).twin.myRedEdge=red0twin;
				
				red0.twinRed=red0twin;
				red0twin.twinRed=red0;
				
				red0.nextRed=reds_out[1].nextRed;
				red0.nextRed.prevRed=red0;
				red0.prevRed=reds_in[0].prevRed;
				red0.prevRed.nextRed=red0;
				
				red0twin.prevRed=reds_in[1].prevRed;
				red0twin.prevRed.nextRed=red0twin;
				red0twin.nextRed=reds_out[0].nextRed;
				red0twin.nextRed.prevRed=red0.twinRed;
			}
			else if (tick==2) {
				int offset=0;
				while(reds_in[offset]==null)
					offset++;
				
				// offset = 0 or 1; 
				// divert from <offset,v,offset+1> to <offset,offset+1>
				HalfEdge theedge=outer.get(offset);
				RedEdge nred=new RedEdge(theedge);
				RedEdge nredtwin=new RedEdge(theedge.twin);
				theedge.myRedEdge=nred;
				theedge.twin.myRedEdge=nredtwin;
				
				nred.twinRed=nredtwin;
				nredtwin.twinRed=nred;
				
				nred.prevRed=reds_in[offset].prevRed;
				nred.prevRed.nextRed=nred;
				nred.nextRed=reds_out[offset+1].nextRed;
				nred.nextRed.prevRed=nred;
				
				nredtwin.prevRed=reds_in[offset+1].prevRed;
				nredtwin.prevRed.nextRed=nredtwin;
				nredtwin.nextRed=reds_out[offset].nextRed;
				nredtwin.nextRed.prevRed=nredtwin;
			}
			else if (tick<=1) // should not happen 
				pdcel.redChain=null;

		}

		// change 'halfedge's, if needed
		for (int k=0;k<3;k++)
			if (outer.get(k).origin.halfedge==outer.get(k).prev.twin)
				outer.get(k).origin.halfedge=outer.get(k);
		
		// check 'alpha'
		if (pdcel.alpha.origin==vert || pdcel.alpha.twin.origin==vert) {
			pdcel.alpha=null;
		}

		// now can toss out 'vert'
		DcelFace face=new DcelFace(0);
		for (int j=0;j<3;j++) {
			outer.get(j).next=outer.get((j+1)%3);
			outer.get((j+1)%3).prev=outer.get(j);
			outer.get(j).face=face;
		}
		face.edge=outer.get(0);
		
		// update vertices 
		int v=vert.vertIndx;
		RawManip.removeVertIndex(pdcel,v);
		pdcel.triData=null;
		return v;
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
	 * 3 and we don't want to puncture, (no new bdry)). 
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
		DcelFace nface=null;
		if (vert.bdryFlag==1) {
			nface=vert.halfedge.twin.face;
			if (nface.edge==vert.halfedge.twin) {
				nface.edge=vert.halfedge.twin.prev;
			}
		}
		else
			nface=new DcelFace(-1);

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
			pdcel.vertices[w].vutil=w+1; // reference vertex
		}
		pdcel.vertCount--;
		// give up on red chain
		pdcel.redChain=null;
		pdcel.triData=null;
		return vlist;
	}

	/**
	 * v must be boundary vertex; link cclw and clw nghbs
	 * with a new edge, making v interior. Red chain is 
	 * adjusted, but calling routine updates combinatorics. 
	 * If v and its 2 nghbs form an ideal face, make it 
	 * interior by making them interior and adjusting or 
	 * tossing the red chain.
	 * Note: fails if v has just two nghb's.
	 * @param pdcel PackDCEL
	 * @param v int
	 * @return int degree of v
	 */
	public static int enfold_raw(PackDCEL pdcel,int v) {
		  if (!pdcel.vertices[v].isBdry() || pdcel.countPetals(v)<=2)
			  throw new CombException("can't enfold v = "+v+" with just 2 nghbs");
		  
		  // key players
		  Vertex vert=pdcel.vertices[v];
		  HalfEdge out_edge=vert.halfedge;
		  HalfEdge in_edge=out_edge.twin.next.twin;
		  HalfEdge bdryedge=out_edge.twin;
		  HalfEdge twin_prev=bdryedge.prev;
		  HalfEdge twin_next=in_edge.twin.next;
		  DcelFace ideal=bdryedge.face;
		  
		  // check if bdry component has just two edges:
		  //   toss 'oldedge', reconnect, and toss the red chain.
		  if (bdryedge.next.next==bdryedge) {

			  Vertex wert=bdryedge.origin;
			  if (wert.halfedge==in_edge)
				  wert.halfedge=bdryedge;
			  
			  // save connections so we can orphan 'in_edge'
			  HalfEdge oldnxt=in_edge.next;
			  HalfEdge oldprev=in_edge.prev;
			  
			  bdryedge.next=oldnxt;
			  oldnxt.prev=bdryedge;
			  
			  bdryedge.prev=oldprev;
			  oldprev.next=bdryedge;
			  
			  bdryedge.face=in_edge.face;
			  
			  vert.bdryFlag=wert.bdryFlag=0;
			  vert.redFlag=wert.redFlag=false;
			  vert.aim=wert.aim=2.0*Math.PI;
			  pdcel.redChain=null;
			  pdcel.triData=null;
			  return 2;
		  }
		  // check if the bdry has just three edges: create
		  //    a new common face, toss red chain
		  if (bdryedge.next.next.next==bdryedge) {
			  DcelFace dface=new DcelFace();
			  Vertex wert=bdryedge.origin;
			  Vertex uert=bdryedge.prev.origin;
			  bdryedge.face=dface;
			  bdryedge.face.edge=bdryedge;
			  bdryedge.next.face=dface;
			  bdryedge.next.next.face=dface;
			  out_edge.next.origin.bdryFlag=0;
			  out_edge.next.origin.redFlag=false;
			  vert.aim=wert.aim=uert.aim=2.0*Math.PI;
			  vert.bdryFlag=wert.bdryFlag=uert.bdryFlag=0;
			  vert.redFlag=wert.redFlag=uert.redFlag=false;
			  pdcel.redChain=null;
			  pdcel.triData=null;
			  return 3;
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
		  out_edge.twin.face=null;
		  in_edge.twin.face=null;
		  
		  // new red 
		  RedEdge new_red=new RedEdge(new_edge);
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
			  RedEdge red=new RedEdge(out_edge.twin);
			  
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
		  vert.aim=2.0*Math.PI;
		  new_edge.origin.halfedge=new_edge;
		  pdcel.triData=null;
		  return pdcel.countPetals(vert.vertIndx);
	}

	/**
	   * Create a barycenter for face 'edge.face'; 
	   * 'vutil' set to hold reference vert. If 'edge.face'
	   * is ideal, throw out the red chain; otherwise,
	   * 'redFlag's, and 'bdryFlag's should remain 
	   * undisturbed. 
	   * 
	   * TODO: 'multi-bary' true, then add three vertices
	   * to the face instead of just one (if the face
	   * has 3 edges?)
	   * 
	   * @param pdcel PackDCEL
	   * @param edge HalfEdge
	   * @param mutli_bary boolean, 
	   * @return int new index
	   */
	  public static int addBary_raw(PackDCEL pdcel,
			  HalfEdge edge,boolean multi_bary) {

// debugging
//		  if (edge.origin.vertIndx==11 || 
//				  edge.twin.origin.vertIndx==11) {
//			  System.out.println(" edge "+edge);
//		  }
		  
		  boolean ideal=false;
		  if (edge.face.faceIndx<0) {
			  pdcel.redChain=null;
			  ideal=true;
		  }
		  
		  // make room
		  int node=pdcel.vertCount+1; // new index 
		  if (node>=pdcel.sizeLimit)
			  pdcel.alloc_vert_space(node+10,true);
		  
		  HalfLink hlink=HalfLink.nextLink(pdcel,edge);
		  if (hlink==null || hlink.size()<=2)
			  return 0;
		  int n=hlink.size();
		  
		  Vertex newV=new Vertex(node); // this is the barycenter
		  newV.redFlag=false;
		  
		  // create new spokes
		  HalfEdge last_spoke=new HalfEdge(newV);
		  HalfEdge hold_spoke=last_spoke;
		  HalfEdge base;
		  HalfEdge next_in;
		  for (int j=0;j<(n-1);j++) {
			  base=hlink.get(j);
			  if (ideal)
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
		  base=hlink.getLast();
		  base.face=null;
		  if (ideal)
			  base.origin.bdryFlag=0; // becomes interior
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
				
		  pdcel.triData=null;
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
			  ArrayList<DcelFace> farray) {
		  int count=0;

		  Iterator<DcelFace> flst=farray.iterator();
		  while (flst.hasNext()) {
			  DcelFace face=flst.next();
			  count += RawManip.addBary_raw(pdcel,face.edge,false);
				
			  // face was ideal? toss 'redChain'
			  if (face.faceIndx<0) 
				  pdcel.redChain=null;
			  face.edge=null; // to avoid repeat in facelist
		  } // end of while through facelist
		  pdcel.triData=null;
		  return count;
	  }

	/**
	   * Add vertex nghb'ing given clw bdry edge (w,u). Set 'vutil' 
	   * of new vertex to 'w' as its reference vertex. If red chain
	   * exists, adjust it.
	   * @param pdcel PackDCEL
	   * @param clw_edge HalfEdge
	   * @return new Vertex
	   */
	  public static Vertex addVert_raw(PackDCEL pdcel,HalfEdge clw_edge) {
		  
		  if (clw_edge==null || !clw_edge.isBdry())
			  throw new CombException(clw_edge+" is not a bdry edge");
	
		  // ensure edge is clw
		  if (clw_edge.face!=null && clw_edge.face.faceIndx>=0)
			  clw_edge=clw_edge.twin;
		  
		  // make sure we have space
		  if (pdcel.vertCount >= pdcel.vertices.length-1
				  && pdcel.alloc_vert_space(pdcel.vertCount+10, true) == 0) 
			  throw new CombException("'vertices' space allocation failure");
		  
		  // key players; tent over 'base_edge'
		  HalfEdge base_edge=clw_edge.twin; 
		  Vertex w_vert=clw_edge.origin;
		  Vertex v_vert=base_edge.origin;
		  HalfEdge twin_prev=clw_edge.prev;
		  HalfEdge twin_next=clw_edge.next;
		  
	
		  // create new bdry 'Vertex'
		  Vertex new_vert=new Vertex(++pdcel.vertCount);
		  pdcel.vertices[pdcel.vertCount]=new_vert;
		  
		  // 2 new edges and twins
		  HalfEdge e1=new HalfEdge(v_vert);
		  e1.origin.halfedge=e1;
	
		  HalfEdge t1=new HalfEdge(new_vert);
		  e1.twin=t1;
		  t1.twin=e1;
		  
		  HalfEdge e2=new HalfEdge(new_vert);
		  e2.origin.halfedge=e2;
		  HalfEdge t2=new HalfEdge(w_vert);
		  e2.twin=t2;
		  t2.twin=e2;
	
		  // set faces; no new face, link in outer face
		  e1.face=null; 
		  e2.face=null;
		  DcelFace outFace=clw_edge.face;
		  clw_edge.face=null;
		  if (outFace!=null && outFace.faceIndx<0) {
			  t1.face=outFace;
			  t2.face=outFace;
			  if (outFace.edge==clw_edge)
				  outFace.edge=t1;
		  }
		  else { // else create ideal face
			  t1.face=new DcelFace(-1);
			  t2.face=t1.face;
		  }
		  
		  // relink everything
		  clw_edge.next=e1;
		  e1.prev=clw_edge;
		  
		  e1.next=e2;
		  e2.prev=e1;
		  
		  e2.next=clw_edge;
		  clw_edge.prev=e2;
		  
		  twin_prev.next=t2;
		  t2.prev=twin_prev;
		  
		  t2.next=t1;
		  t1.prev=t2;
		  
		  t1.next=twin_next;
		  twin_next.prev=t1;

		  // fix vert
		  new_vert.halfedge=e2;
		  new_vert.bdryFlag=1;
		  new_vert.redFlag=true;
		  new_vert.rad=w_vert.rad;
		  new_vert.aim=-1.0;
	
		  // Adjust red chain?
		  RedEdge base_red=base_edge.myRedEdge; // from v to w
		  if (base_red==null || pdcel.redChain==null) {
			  base_red=null;
			  pdcel.redChain=null;
		  }
		  if (base_red!=null && pdcel.redChain==base_red)
			  pdcel.redChain=base_red.nextRed;
		  base_edge.myRedEdge=null;
		  
		  if (pdcel.redChain!=null) {
			  // 2 new red edges?

			  RedEdge red1=new RedEdge(e1);
			  e1.myRedEdge=red1;
			  red1.setCenter(new Complex(base_red.getCenter()));
			  red1.setRadius(base_red.getRadius());
			  RedEdge red2=new RedEdge(e2);
			  e2.myRedEdge=red2;
			  // just to set something
			  red2.setCenter(new Complex(base_red.getCenter())); 
			  red2.setRadius(base_red.getRadius());

			  red1.prevRed=base_red.prevRed;
			  base_red.prevRed.nextRed=red1;

			  red1.nextRed=red2;
			  red2.prevRed=red1;
		  
			  red2.nextRed=base_red.nextRed;
			  base_red.nextRed.prevRed=red2;
		  }
		  
		  pdcel.triData=null;
		  return new_vert;
	  }
	  
	  /**
	   * Given clw bdry edge, add a "box", a rectangle tile with
	   * barycenter. If 'fullbox', then attach three new bdry edges
	   * to given 'edge' to form the box; else two new bdry edges,
	   * one to base of 'edge' and one to end of 'edge.next'.
	   * @param pdcel PackDCEL
	   * @param edge HalfEdge
	   * @param fullbox boolean,
	   * @return Vertex, barycenter vertex
	   */
	  public static Vertex addBox_raw(PackDCEL pdcel,HalfEdge edge,
			  boolean fullbox) {
		  if (edge==null || !edge.isBdry())
			  return null;
		  
		  // edge should be clw
		  if (edge.face!=null && edge.face.faceIndx>=0)
			  edge=edge.twin;

		  // add barycenter first
		  Vertex baryV=RawManip.addVert_raw(pdcel,edge);
		  baryV.rad=0.13*edge.origin.rad;
		  
		  // create new vertex and bdry edge to end of 'edge'
		  HalfEdge leftleg=edge.prev.twin;
		  Vertex leftV=RawManip.addVert_raw(pdcel,leftleg);
		  leftV.rad=edge.origin.rad;
		  
		  // if full, add second vertex
		  if (fullbox) { 
			  HalfEdge rightleg=edge.next.twin;
			  Vertex rightV=RawManip.addVert_raw(pdcel,rightleg);
			  rightV.rad=edge.twin.origin.rad;
		  }
		  else {
			  RawManip.enfold_raw(pdcel,edge.twin.origin.vertIndx);
		  }
		  
		  RawManip.enfold_raw(pdcel,baryV.vertIndx);
		  return baryV;
	  }

	  /**
	   * add combinatorics for a layer of "square" 
	   * faces with barycenters, one for each bdry edge, 
	   * 'v1' to 'v2'. 'vutil' of new vertex is index of 
	   * reference circle on original bdry. Calling routine 
	   * does the rest.
	   * @param pdcel PackDCEL
	   * @param v1 int
	   * @param v2 int
	   * @return int, new 'vertCount'
	   */
	  public static int baryBox_layer(PackDCEL pdcel, int v1, int v2) {

		  // check legality and count edges
		  if (pdcel.p == null)
			  throw new ParserException("'baryBox_raw' requires 'PackData' parent");

		  int e_count = pdcel.verts_share_bdry(v1, v2);
		  if (e_count == 0)
			  throw new CombException("verts " + v1 + " and " + v2 + " are not " + "on the same component");
		  int nodes = pdcel.vertCount + 2 * e_count;
		  if (nodes > pdcel.sizeLimit)
			  pdcel.alloc_vert_space(nodes + 10, true);

		  // place a "square" 'Face' next to each bdry
		  // if we close up, shift v1 cclw as stop signal
		  boolean closeup = false;
		  if (v1 == v2) {
			  closeup = true; 
			  v1 = pdcel.vertices[v1].halfedge.twin.origin.vertIndx;
		  }

		  int count = 0;

		  // get relevant edges, vertices
		  HalfEdge he = pdcel.vertices[v2].halfedge;
		  HalfEdge twin_prev = he.twin;
		  HalfEdge base_twin = twin_prev.next;
		  HalfEdge twin_next = base_twin.next;

		  combinatorics.komplex.DcelFace outFace = base_twin.face;
		  Vertex lastV = base_twin.origin;
		  Vertex nextV = twin_next.origin;

		  // relevant red edges: note, prev/next
		  // red may not be on bdry.
		  RedEdge base_red = base_twin.twin.myRedEdge;
		  RedEdge next_red = base_red.nextRed;
		  RedEdge prev_red = base_red.prevRed;
		  base_twin.twin.myRedEdge = null;

		  // create first square face: prep for iteration.

		  // left spike
		  Vertex v_left = new Vertex(++pdcel.vertCount);
		  pdcel.vertices[pdcel.vertCount] = v_left;
		  v_left.vutil = lastV.vertIndx; // reference vertex
		  v_left.bdryFlag = 1;
		  v_left.redFlag = true;
		  HalfEdge down_left = new HalfEdge(v_left);
		  HalfEdge up_left = new HalfEdge(lastV);
		  up_left.face = outFace;
		  v_left.halfedge = down_left;

		  up_left.twin = down_left;
		  down_left.twin = up_left;

		  up_left.prev = twin_prev;
		  twin_prev.next = up_left;

		  // right spike
		  Vertex v_right = new Vertex(++pdcel.vertCount);
		  pdcel.vertices[pdcel.vertCount] = v_right;
		  v_right.vutil = nextV.vertIndx; // reference vertex
		  v_right.bdryFlag = 1;
		  v_right.redFlag = true;
		  HalfEdge down_right = new HalfEdge(v_right);
		  HalfEdge up_right = new HalfEdge(nextV);
		  nextV.halfedge = up_right;
		  down_right.face = outFace;
		  
		  up_right.twin = down_right;
		  down_right.twin = up_right;

		  down_right.next = twin_next;
		  twin_next.prev = down_right;

		  // top
		  HalfEdge top = new HalfEdge(v_right);
		  v_right.halfedge = top;
		  HalfEdge top_tw = new HalfEdge(v_left);
		  top_tw.face = outFace;
		  combinatorics.komplex.DcelFace new_face = new combinatorics.komplex.DcelFace(pdcel.vertCount);
		  top.face = new_face;
		  count++;

		  top.twin = top_tw;
		  top_tw.twin = top;

		  top_tw.next = down_right;
		  down_right.prev = top_tw;

		  top_tw.prev = up_left;
		  up_left.next = top_tw;

		  // link around face
		  base_twin.next = up_right;
		  up_right.prev = base_twin;
		  base_twin.face = new_face;
		  new_face.edge = base_twin;

		  up_right.next = top;
		  top.prev = up_right;
		  up_right.face = new_face;

		  top.next = down_left;
		  down_left.prev = top;
		  down_left.face = new_face;

		  down_left.next = base_twin;
		  base_twin.prev = down_left;

		  // add barycenter
		  addBary_raw(pdcel, new_face.edge, false);

		  // create red
		  RedEdge up_red = new RedEdge(up_right);
		  up_right.myRedEdge = up_red;
		  RedEdge top_red = new RedEdge(top);
		  top.myRedEdge = top_red;
		  RedEdge down_red = new RedEdge(down_left);
		  down_left.myRedEdge = down_red;

		  // get red chain out of the way
		  pdcel.redChain = top_red;

		  // link red
		  up_red.prevRed = prev_red;
		  prev_red.nextRed = up_red;

		  up_red.nextRed = top_red;
		  top_red.prevRed = up_red;

		  top_red.nextRed = down_red;
		  down_red.prevRed = top_red;

		  down_red.nextRed = next_red;
		  next_red.prevRed = down_red;

		  base_twin.twin.myRedEdge = null;

		  // save some info for closing up
		  RedEdge firstRed = down_left.myRedEdge;

		  // ======= iterate until finishing with v1 =======
		  while (nextV.vertIndx != v1) {

			  // shift
			  down_left = down_right;
			  up_left = up_right;
			  v_left = v_right;
			  lastV = nextV;

			  // set new base 'bdry_twin' and save 'twin_next'
			  base_twin = twin_next;
			  twin_next = base_twin.next;
			  base_red = base_twin.twin.myRedEdge;
			  prev_red = base_red.prevRed;
			  next_red = base_red.nextRed;
			  nextV = base_red.myEdge.origin;

			  // right spike
			  v_right = new Vertex(++pdcel.vertCount);
			  pdcel.vertices[pdcel.vertCount] = v_right;
			  v_right.vutil = nextV.vertIndx; // reference vertex
			  v_right.bdryFlag = 1;
			  v_right.redFlag = true;
			  down_right = new HalfEdge(v_right);
			  up_right = new HalfEdge(nextV);
			  nextV.halfedge = up_right;
			  down_right.face = outFace;

			  up_right.twin = down_right;
			  down_right.twin = up_right;

			  down_right.next = twin_next;
			  twin_next.prev = down_right;

			  // top
			  top = new HalfEdge(v_right);
			  v_right.halfedge = top;
			  top_tw = new HalfEdge(v_left);
			  top_tw.face = outFace;
			  new_face = new combinatorics.komplex.DcelFace(pdcel.vertCount);
			  top.face = new_face;
			  count++;

			  top.twin = top_tw;
			  top_tw.twin = top;

			  top_tw.next = down_right;
			  down_right.prev = top_tw;

			  top_tw.prev = down_left.prev;
			  down_left.prev.next = top_tw;

			  // link around face
			  base_twin.next = up_right;
			  up_right.prev = base_twin;
			  base_twin.face = new_face;
			  new_face.edge = base_twin;

			  up_right.next = top;
			  top.prev = up_right;
			  up_right.face = new_face;

			  top.next = down_left;
			  down_left.prev = top;
			  down_left.face = new_face;

			  down_left.next = base_twin;
			  base_twin.prev = down_left;

			  // add barycenter
			  addBary_raw(pdcel, new_face.edge, false);

			  // create red
			  up_red = new RedEdge(up_right);
			  up_right.myRedEdge = up_red;
			  top_red = new RedEdge(top);
			  top.myRedEdge = top_red;
			  down_red = new RedEdge(down_left);
			  down_left.myRedEdge = down_red;

			  // link red
			  up_red.prevRed = prev_red;
			  prev_red.nextRed = up_red;

			  up_red.nextRed = top_red;
			  top_red.prevRed = up_red;

			  top_red.nextRed = down_red;
			  down_red.prevRed = top_red;

			  down_red.nextRed = next_red;
			  next_red.prevRed = down_red;

			  base_twin.twin.myRedEdge = null;

			  // left reds typically switchback, so remove
			  if (down_left.myRedEdge.nextRed == up_left.myRedEdge) {
				  top_red.nextRed = up_left.myRedEdge.nextRed;
				  up_left.myRedEdge.nextRed.prevRed = top_red;

				  up_left.myRedEdge = null;
				  down_left.myRedEdge = null;

				  lastV.redFlag = false;
			  }
			  lastV.bdryFlag = 0;
		  } // end of while

		  // if closing up, need one more face
		  if (closeup) {

			  base_twin = twin_next;
			  base_red = base_twin.twin.myRedEdge;
			  prev_red = base_red.prevRed;
			  next_red = base_red.nextRed;
			  lastV = nextV;
			  nextV = base_twin.twin.origin;
			  up_left = up_right;
			  down_left = down_right;

			  down_right = firstRed.myEdge;
			  up_right = down_right.twin;

			  v_left = v_right;
			  v_right = firstRed.myEdge.origin;

			  // top
			  top = new HalfEdge(v_right);
			  v_right.halfedge = top;
			  top_tw = new HalfEdge(v_left);
			  top_tw.face = outFace;
			  new_face = new combinatorics.komplex.DcelFace(pdcel.vertCount + 1);
			  top.face = new_face;
			  count++;

			  top.twin = top_tw;
			  top_tw.twin = top;

			  top_tw.next = up_right.next;
			  up_right.next.prev = top_tw;

			  top_tw.prev = down_left.prev;
			  down_left.prev.next = top_tw;

			  // link around face
			  base_twin.next = up_right;
			  up_right.prev = base_twin;
			  base_twin.face = new_face;
			  new_face.edge = base_twin;

			  up_right.next = top;
			  top.prev = up_right;
			  up_right.face = new_face;

			  top.next = down_left;
			  down_left.prev = top;
			  down_left.face = new_face;

			  down_left.next = base_twin;
			  base_twin.prev = down_left;

			  // create barycenter
			  addBary_raw(pdcel, new_face.edge, false);

			  // create red
			  up_red = new RedEdge(up_right);
			  up_right.myRedEdge = up_red;
			  top_red = new RedEdge(top);
			  top.myRedEdge = top_red;
			  down_red = new RedEdge(down_left);
			  down_left.myRedEdge = down_red;

			  // link red
			  up_red.prevRed = prev_red;
			  prev_red.nextRed = up_red;

			  up_red.nextRed = top_red;
			  top_red.prevRed = up_red;

			  top_red.nextRed = down_red;
			  down_red.prevRed = top_red;

			  down_red.nextRed = next_red;
			  next_red.prevRed = down_red;

			  base_twin.twin.myRedEdge = null;

			  // left reds typically switchback, so remove
			  if (down_left.myRedEdge.nextRed == up_left.myRedEdge) {
				  top_red.nextRed = up_left.myRedEdge.nextRed;
				  up_left.myRedEdge.nextRed.prevRed = top_red;

				  up_left.myRedEdge = null;
				  down_left.myRedEdge = null;

				  lastV.redFlag = false;
			  }

			  // right typically switchback, so remove
			  if (prev_red == firstRed) {
				  firstRed.prevRed.nextRed = top_red;
				  top_red.prevRed = firstRed.prevRed;
				  up_right.myRedEdge = null;
				  firstRed.myEdge.myRedEdge = null;

				  nextV.redFlag = false;
			  }
			  lastV.bdryFlag = 0;
		  }

		  // Only one? barycenter not connected to interior
		  if (count == 1) {
			  pdcel.redChain = null;
		  }
		  pdcel.triData=null;
		  return count;
	  }

	  /**
	   * If 'rededge' backtracks, we can shrink red chain 
	   * (moving 'redChain' as necessary). No action? return
	   * 'rededge'. If red chain is just two edges, 
	   * this becomes a sphere, return null. Else return next
	   * downstream red after backtrack.
	   * @param rededge RedEdge
	   * @return RedEdge, possibly null
	   */
	  public static RedEdge contractRed_raw(PackDCEL pdcel,RedEdge rededge) {
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
			  fledge=RawManip.flipEdge_raw(pdcel, tryedge);
		  }
		  ans[0]=adedge;
		  ans[1]=fledge; // could be null if flip is illegal
		  pdcel.triData=null;
		  return ans;
	  }

	  /**
	   * "Flip" an 'edge' in the combinatorics; that means, 
	   * to replace 'edge' by the other diagonal in the union
	   * of its two faces. The numbers of faces, edges, and 
	   * verts is unchanged. There are combinatoric conditions 
	   * on 'edge':
	   *  + shared by two non-ideal faces (i.e. interior edge)
	   *  + must not have an end of degree 3.
	   *  + if end of degree 4, it cannot have nghb of degree 3.
	   *  + new edge ends cannot already be neighbors.
	   * If 'edge' is in red chain, then set 'redChain' null. 
	   * Calling routine handles processing.
	   * 
	   * @param pdcel PackDCEL
	   * @param edge HalfEdge
	   * @return HalfEdge, new edge, null failure
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

		  // are leftv/rightv already nghbs?
		  Vertex leftv = edge.next.next.origin;
		  Vertex rightv = edge.twin.next.next.origin;
		  if (pdcel.findHalfEdge(new EdgeSimple(leftv.vertIndx,rightv.vertIndx))!=null)
			  return null;
		  
		  if (edge.myRedEdge != null) // in redchain
			  pdcel.redChain = null;
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

		  pdcel.triData=null;
		  return new_edge;
	  }

	  /**
	   * Either hex or bary refine the given DCEL. Start either
	   * process by first dividing each edge in half. Then either 
	   * add edges between the new edge vertices (hex refining)
	   * or add a barycenter and new edges (bary refining). We
	   * return 'ArrayList<Integer>': in baryrefine case, this
	   * list all the new barycenters, in hexrefine case, it is
	   * not null but is empty.  
	   * The 'pdcel.redChain' should remain in tact, but is subdivided 
	   * to get the new red chain. All original vertices have their 
	   * original 'halfedge's, 'vutil' holds index of reference 
	   * verts (see 'reapVUtils').
	   * Process involves cycling original spokes of original 
	   * vertices. At the end, 'vertices' is up to date, 'faces' 
	   * and 'edges' are outdated, but ideal faces and their 
	   * indices should remain.
	   * CAUTION: Here 'pdcel.vertices' will be extended
	   * as necessary; in 'attachDCEL', pack 'sizeLimit' should
	   * get adjusted to accommodate.
	   * 
	   * (Nominally works for non-triangular faces, but that has
	   * not been tested.)
	   *  
	   * @param pdcel  PackDCEL
	   * @param baryFlag boolean; true, then do bary refine, else hex
	   * @return ArrayList<Integer>, may be empty
	   */
		public static ArrayList<Integer> hexBaryRefine_raw(PackDCEL pdcel,
				boolean baryFlag) {

			boolean debug=false; // debug=true;
			// DCELdebug.printRedChain(pdcel.redChain);
			
			ArrayList<Integer> barycents=new ArrayList<Integer>();
			
			// need to make 'vertices' array bigger?
			int newsz=pdcel.vertCount+pdcel.edgeCount;
			if (baryFlag)
				newsz += pdcel.faceCount;
			if (newsz>pdcel.sizeLimit) 
		        pdcel.alloc_vert_space(newsz+2,true);

			// 'eutil' flags:
			// 0 means untouched yet
			// 1 means this edge has been subdivided
			// 2 means this edge's face has been subdivided.
			pdcel.zeroEUtil();

			// we loop through the original vertices, and
			// for each vert loop through its faces,
			// splitting its edges (and also their twins),
			// then adding its new interior faces, and
			// vertices (depending on hex or bary process)
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
					if (spoke.eutil == 2 || 
							(spoke.face!=null && spoke.face.faceIndx<0))
						continue;
					// else, will process this edge and its face
					spoke.face = new DcelFace(1);
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
					RedEdge redge=null;
					RedEdge tredge=null;

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

						redge = n_base.myRedEdge;
						tredge = n_base.twin.myRedEdge;
							
						if (debug && (redge!=null || tredge!=null)) // debug=true;
							System.out.println("     base: redge: "+redge+"; tredge: "+tredge);
							
						// two new edges
						n_newEdge = new HalfEdge();
						n_newEdge.eutil = 1;
						n_newEdge.setInvDist(n_base.getInvDist());
						n_newEdge.setSchwarzian(n_base.getSchwarzian());
						n_newEdge.face=n_base.face;
						n_newTwin = new HalfEdge();
						n_newTwin.eutil = 1;
						n_newTwin.setInvDist(n_base.getInvDist());
						n_newTwin.setSchwarzian(n_base.getSchwarzian());
						n_newTwin.face=n_base.twin.face;

						// new vert; cent/rad = end averages; bdry?
						n_midVert = new Vertex(++pdcel.vertCount);
						n_midVert.rad=(n_base.origin.rad+n_base_tw.origin.rad)/2.0;
						n_midVert.center=(n_base.origin.center.add(
								n_base_tw.origin.center).times(.5));
						// bdry edge?
						if (n_base.twin.face!=null && n_base.twin.face.faceIndx<0) {
							n_midVert.bdryFlag=1;
							n_midVert.aim=Math.PI;
						}
						else
							n_midVert.aim=2.0*Math.PI;
						pdcel.vertices[pdcel.vertCount] = n_midVert;
						n_midVert.halfedge = n_newEdge;
						n_newEdge.origin = n_midVert;
						n_newTwin.origin = n_midVert;
						if (redge!=null || tredge!=null)
							n_midVert.redFlag=true;

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

						// does 'edge' have red edge?
						if (redge != null) {
							RedEdge newRedge = new RedEdge(n_newEdge);
							n_newEdge.myRedEdge = newRedge;
							newRedge.nextRed = redge.nextRed;
							redge.nextRed.prevRed = newRedge;
							newRedge.prevRed = redge;
							redge.nextRed = newRedge;
						}
						// does 'tedge' have red edge?
						if (tredge != null) {
							RedEdge newRedge = new RedEdge(n_newTwin);
							n_newTwin.myRedEdge = newRedge;
							newRedge.nextRed = tredge.nextRed;
							tredge.nextRed.prevRed = newRedge;
							newRedge.prevRed = tredge;
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

					// now do rest of edges, build corner faces
					for (int k = 0; k < n; k++) {

						// shift of previous edge
						newEdge = n_newEdge;
						midVert = n_midVert;

						// load/divide next side about this face
						n_base = fedges.get(k + 1);

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
							redge = n_base.myRedEdge;
							tredge = n_base.twin.myRedEdge;

							if (debug && (redge!=null || tredge!=null)) // debug=true;
								System.out.println("      other red: redge: "+redge+"; tredge: "+tredge);

							// two new edges
							n_newEdge = new HalfEdge();
							n_newEdge.eutil = 1;
							n_newEdge.setInvDist(n_base.getInvDist());
							n_newEdge.setSchwarzian(n_base.getSchwarzian());
							n_newEdge.face=n_base.face;
							n_newTwin = new HalfEdge();
							n_newTwin.eutil = 1;
							n_newTwin.setInvDist(n_base.getInvDist());
							n_newTwin.setSchwarzian(n_base.getSchwarzian());
							n_newTwin.face=n_base.twin.face;

							// new vertex
							n_midVert = new Vertex(++pdcel.vertCount);
							n_midVert.rad=(n_base.origin.rad+n_base_tw.origin.rad)/2.0;
							n_midVert.center=(n_base.origin.center.add(
									n_base_tw.origin.center).times(.5));
							// bdry edge?
							if (n_base.twin.face!=null && n_base.twin.face.faceIndx<0) {
								n_midVert.bdryFlag=1;
								n_midVert.aim=Math.PI;
							}
							else n_midVert.aim=2.0*Math.PI;
							pdcel.vertices[pdcel.vertCount] = n_midVert;
							n_midVert.halfedge = n_newEdge;
							n_newEdge.origin = n_midVert;
							n_newTwin.origin = n_midVert;
							if (redge!=null || tredge!=null)
								n_midVert.redFlag=true;

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

							// does 'edge' have red edge?
							if (redge != null) {
								RedEdge newRedge = new RedEdge(n_newEdge);
								n_newEdge.myRedEdge = newRedge;
								newRedge.nextRed = redge.nextRed;
								redge.nextRed.prevRed = newRedge;
								newRedge.prevRed = redge;
								redge.nextRed = newRedge;
							}
							// does 'tedge' have red edge?
							if (tredge != null) {
								RedEdge newRedge = new RedEdge(n_newTwin);
								n_newTwin.myRedEdge = newRedge;
								newRedge.nextRed = tredge.nextRed;
								tredge.nextRed.prevRed = newRedge;
								newRedge.prevRed = tredge;
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

						// hex refining is incremental as we split the sides.
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
							
					} // finish loop through all sides of this face

					if (!baryFlag) {
						centedges[n] = centedges[0];
						for (int k = 0; k < n; k++) {
							centedges[k].next = centedges[k + 1];
							centedges[k + 1].prev = centedges[k];
						}
							
						if (debug) { // debug=true;
							StringBuilder strbld=new StringBuilder(
									"new mid-face: <"+
											centedges[0]+","+
											centedges[0].next+","+
											centedges[0].next.next+">\n");
							for (int k=0;k<n;k++) {
								HalfEdge he=centedges[k].twin;
								strbld.append(" ===  <"+he+","+he.next+","+
										he.next.next+"> ; ");
							}
							System.out.println(strbld.toString());
						}
					}
						
					// bary refining goes on after all sides are split
					else { 
							
						// new bary center vertex
						Vertex baryCent=new Vertex(++pdcel.vertCount);
						barycents.add(pdcel.vertCount); 
						pdcel.vertices[pdcel.vertCount]=baryCent;
						baryCent.cloneData(spoke.origin);
						baryCent.aim=2.0*Math.PI;
							
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

			if (debug)
				DCELdebug.printRedChain(pdcel.redChain);
			
			pdcel.triData=null;
			return barycents;
		}
		  
		/**
		 * Routine to 'migrate' a branch point from interior point
		 * v to nghb w. Based on a common geometric method for 
		 * creating branch points, namely, by attaching two packings 
		 * along a common slit, the tips of the slits becoming the 
		 * common branch circle. If the local combinatorics of the two 
		 * complexes were identical, then the branch point could be 
		 * modified by extending the slit one more edge on each 
		 * piece to end at the common neighbors of the original 
		 * tips; after pasting, the branching would occur at that 
		 * neighbor. This routine adjusts the combinatorics of 
		 * the original pasted complex locally to accomplish the 
		 * same result -- so a branch point v is "migrated" to 
		 * a neighbor w.
		 * 
		 * Branching is a geometric phenomenon, and while the work
		 * is combinatoric, there must exist certain local 
		 * symmetries to make this work: if 'edge' is <v,w>,
		 * the original 'branch' vertex is 'v' and must have 
		 * even degree (at least 6) and neighbor w must have 
		 * the same degree as the vertex ww directly opposite of v.
		 * 
		 * After migration, 'v' remains as the branch vertex,
		 * 'w' and 'ww' split the old branch vert edges; no need
		 * to adjust aims.
		 * @param pdcel PackDCEL, (only needed to null 'triData')
		 * @param edge HalfEdge
		 * @return int, new 'w', else 0 on error
		 */
		public static int migrate(PackDCEL pdcel,HalfEdge edge) {
			  Vertex vert=edge.origin;
			  Vertex wert=edge.twin.origin;
			  if (vert.isBdry() || wert.isBdry() || vert.getNum()<6)
				  return 0;
			  
			  // must have edge opposite w
			  HalfEdge oppedge=edge.findOppEdge();
			  if (oppedge==null)
				  return 0;
			  Vertex uert=oppedge.twin.origin;

			  // store 
			  HalfEdge holdw_v=edge.twin;
			  HalfEdge holdww_v=oppedge.twin;
			  HalfEdge enext=edge.next;
			  HalfEdge eprev=edge.prev;
			  
			  // new twinning
			  edge.twin=holdw_v;
			  holdw_v.twin=edge;
			  
			  oppedge.twin=holdww_v;
			  holdww_v.twin=oppedge;
			  
			  // new links
			  edge.next=oppedge.next;
			  oppedge.next.prev=edge;
			  oppedge.next=enext;
			  enext.prev=oppedge;
			  edge.prev=oppedge.prev;
			  oppedge.prev.next=edge;
			  oppedge.prev=eprev;
			  eprev.next=oppedge;
			  
			  // new branch point is 
			  vert.halfedge=edge.twin;
			  HalfEdge he=vert.halfedge;
			  do {
				  he.origin=vert;
				  he=he.prev.twin; // cclw
			  } while (he!=vert.halfedge);

			  edge.origin=wert;
			  wert.halfedge=edge;
			  he=wert.halfedge;
			  do {
				  he.origin=wert;
				  he=he.prev.twin; // cclw
			  } while(he!=wert.halfedge);
			  
			  oppedge.origin=uert;
			  uert.halfedge=oppedge;
			  he=uert.halfedge;
			  do {
				  he.origin=uert;
				  he=he.prev.twin; // cclw
			  } while(he!=uert.halfedge);
			  
// debugging
//			  System.out.println("v="+vert.vertIndx+" deg="+vert.getNum()+
//					  "; w="+wert.vertIndx+" deg="+wert.getNum()+
//					  "; u="+uert.vertIndx+" deg="+uert.getNum());
			  
			  pdcel.triData=null;
			  return wert.vertIndx;
		}
		  
		/**
		 * Add a new vertex to some or all vertices bounding 
		 * an ideal face, starting with 'v' and going cclw about 
		 * the bdry component (clw about the ideal face) until the
		 * new vertex neighbors w. If w=v, then we're adding a 
		 * barycenter to the ideal face and this bdry component 
		 * becomes interior. Calling must check that v, w on 
		 * same bdry comp, and handles combinatorics. New vert 
		 * should have the largest index. 
		 * Exception if bdry comp has just two edges.
		 * @param pdcel PackDCEL
		 * @param v int
		 * @param w int
		 * @return int, count of edgepairs added, 0 on error
		 */
		public static int addIdeal_raw(PackDCEL pdcel,int v,int w) {
			
			boolean debug=false;
			
			HalfEdge vedge=pdcel.vertices[v].halfedge;
			if (vedge.twin.prev.twin==vedge.twin.next.twin)
				throw new CombException("bdry component has just two edges");
			HalfEdge clwedge=vedge.twin.prev;
			  
			// first tent over 'vedge' to get the new vertex
			Vertex newV=RawManip.addVert_raw(pdcel,vedge.twin);
			newV.cloneData(vedge.origin);
			int count=2;
			  
			// proceed cclw enfolding vertices until red chain is null
			int u=clwedge.twin.origin.vertIndx;
			while (u!=w && u!=v && pdcel.redChain!=null) {
				clwedge=clwedge.prev;
				
// debugging				
//				DCELdebug.printRedChain(pdcel.redChain);
//				System.out.println("clwedge="+clwedge+" and u="+u);
				
				RawManip.enfold_raw(pdcel,u);
				u=clwedge.twin.origin.vertIndx;
				count++;
				if (debug)
					DCELdebug.redConsistency(pdcel.redChain);
			} 
			if (pdcel.redChain==null) { // have closed up
				newV.bdryFlag=0;
				newV.redFlag=false;
				newV.aim=2.0*Math.PI;
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
	public static int addIdealFace_raw(PackDCEL pdcel,DcelFace f) {
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
		RedEdge[] reds=new RedEdge[3];
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
			pdcel.triData=null;
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
		RedEdge rhe=reds[(offset+2)%3]; 
		RedEdge prevred=reds[(offset+1)%3];
		RedEdge nxtred=reds[offset];
			
		// new twins
		RedEdge prev_twin=new RedEdge(prevred.myEdge.twin);
		prev_twin.myEdge.myRedEdge=prev_twin;
		RedEdge nxt_twin=new RedEdge(nxtred.myEdge.twin);
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
		RedEdge nextedge=prev_twin;
		RedEdge shiftred=null;
		while (nextedge!=shiftred) {
			shiftred=nextedge; // DCELdebug.redConsistency(pdcel); 
			nextedge=RawManip.contractRed_raw(pdcel,shiftred);
		}
			
		// DCELdebug.printRedChain(pdcel.redChain,null);
			
		// create standin new interior face
		DcelFace newface=new DcelFace(pdcel.faceCount+1);
		newface.edge=hlink.get(0);
		for (int j=0;j<3;j++) {
			HalfEdge he=hlink.get(j);
			he.face=newface;
			he.origin.bdryFlag=0; // all become interior
		}
		pdcel.triData=null;
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
			pdcel=CombDCEL.adjoin(pdcel,next,v,v,sides);
		}
		CombDCEL.adjoin(pdcel,pdcel2,w,v,2*sides);
		
		return pdcel;
	}
	
	/**
	 * Remove an interior node of degree 4, namely, the
	 * origin 'v' of 'edge'. If 'w' is other end, introduce 
	 * new edge from opposite vertex 'u' to 'w'. (So we
	 * collapse 'v' to 'u'.) Fix up 'vertices', 'vertCount',
	 * compose to update 'oldNew'. If 'v' is red, null red chain.
	 * May have to reset 'alpha'.
	 * @param pdcel PackDCEL
	 * @param edge HalfEdge
	 * @return int, orphaned vert index, 0 on failure
	 */
	public static int rmQuadNode(PackDCEL pdcel,HalfEdge edge) {
		Vertex vert=edge.origin;
		if (vert.isBdry() || vert.getNum()!=4)
			return 0;

		if (pdcel.alpha!=null && 
				(pdcel.alpha.origin.vertIndx==edge.origin.vertIndx ||
				pdcel.alpha.twin.origin.vertIndx==edge.origin.vertIndx))
			pdcel.alpha=null;
		
		return RawManip.meldEdge_raw(pdcel,edge.twin);
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
		pdcel.triData=null;
		return 1;
	}
	
	/**
	 * Fracking is a combinatorial refinement process. Given 
	 * a vertex, we first add a barycenter to each neighboring face.
	 * Then we flip each edge shared by two of these faces. Finally,
	 * we remove any boundary edges of these faces. Return the 
	 * count of new vertices. Note: if v is bdry with just one face,
	 * then return 0. See the "ring" command.
	 * @param verts NodeLink
	 * @return int, count of new vertices, 0 on error
	 */
	public static int frackVert(PackDCEL pdcel,int v) {
		if (v<1 || v>pdcel.vertCount) 
			return 0;
		Vertex vert=pdcel.vertices[v];
		HalfLink spokes=vert.getEdgeFlower();
		if (vert.isBdry() && spokes.size()==2)
			return 0;
		int count=0;
		Iterator<HalfEdge> sis=spokes.iterator();
		while (sis.hasNext()) {
			HalfEdge he=sis.next();
			if (he.face.faceIndx>=0) {
				addBary_raw(pdcel,he,false);
				count++;
			}
		}
		sis=spokes.iterator();
		while (sis.hasNext()) {
			HalfEdge he=sis.next();
			if (!he.isBdry())
				flipEdge_raw(pdcel,he);
			else {
				int rslt=rmEdge_raw(pdcel,he);
				if (rslt<0)
					pdcel.redChain=null;
			}
		}
		pdcel.triData=null;
		return count;
	}
	
	/**
	 * Remove 'edge', which must be bdry or have both ends 
	 * interior. 
	 * + If edge has two interior ends, this creates a new
	 *   bdry component surrounding the edge, red chain
	 *   is lost. 'alpha' may need to be reset.
	 * + If edge is bdry: if part of a protruding bdry face, 
	 *   then we will orphan one vertex (and set 'oldNew').
	 *   Keep 'edge.origin' vertex, or surviving vertex if
	 *   one end is orphaned. We try to keep the red chain.
	 *  
	 * Return int: 
	 *  * if -w, then vert of index w is now red and
	 *    calling routine should set rad/center.
	 *  * 0 on failure
	 * @param pdcel PackDCEL
	 * @param edge HalfEdge
	 * @return int, 0 on failure
	 */
	public static int rmEdge_raw(PackDCEL pdcel,HalfEdge edge) {
		Vertex vend=edge.origin;
		Vertex wend=edge.twin.origin;
		if (edge.isBdry()) { // DCELdebug.printRedChain(pdcel.redChain);
			// get positive orientation
			if (edge.face!=null && edge.face.faceIndx<0) {
				edge=edge.twin;
				vend=edge.origin;
				wend=edge.twin.origin;
			}
			Vertex oppV=edge.next.next.origin;
			RedEdge redge=edge.myRedEdge;
			
			// special cases: 'edge' is one edge of protruding face,
			//    must remove the face and outside vertex
			if (vend.getNum()==1 || wend.getNum()==1) {
				// adjust so wend will be orphaned
				if (wend.getNum()!=1) {
					edge=edge.prev;
					vend=edge.origin;
					wend=edge.twin.origin;
					oppV=edge.next.next.origin;
					redge=edge.myRedEdge;
				}
			}
			if (wend.getNum()==1) {
				HalfEdge opp=edge.prev.twin;
				if (redge!=null) {
					if (pdcel.redChain==redge || pdcel.redChain==redge.nextRed)
						pdcel.redChain=redge.prevRed;
					RedEdge prered=redge.prevRed;
					RedEdge postred=redge.nextRed.nextRed;
					RedEdge newred=new RedEdge(opp);
					
					// transfer data for 'oppV'
					newred.setRadius(prered.getRadius());
					newred.setCenter(prered.getCenter());
					
					opp.myRedEdge=newred;
					opp.origin.halfedge=opp;
					prered.nextRed=newred;
					newred.prevRed=prered;
					newred.nextRed=postred;
					postred.prevRed=newred;
					edge.myRedEdge=null;
					edge.next.myRedEdge=null;
				}
				
				// cut out face
				opp.twin.next=edge.twin.next;
				edge.twin.next.prev=opp.twin;
				HalfEdge phe=edge.next.twin;
				phe.prev.next=opp.twin;
				opp.twin.prev=phe.prev;
				opp.twin.face=edge.twin.face;
				opp.origin.halfedge=opp;
				
				// toss 'wend'
				int w=wend.vertIndx;
				RawManip.removeVertIndex(pdcel, w);
				return w; // this vertex is lost
			}
				
			// more typical for bdry: just remove edge and relink
			HalfEdge side1=edge.prev.twin;
			HalfEdge side2=edge.next.twin;
			if (redge==null || oppV.redFlag)
				pdcel.redChain=null;
			else {
				if (redge==pdcel.redChain)
					pdcel.redChain=redge.nextRed;
				RedEdge red1=new RedEdge(side1);
				side1.myRedEdge=red1;
				RedEdge red2=new RedEdge(side2);
				side2.myRedEdge=red2;
				red1.nextRed=red2;
				red2.prevRed=red1;
				RedEdge prered=redge.prevRed;
				RedEdge postred=redge.nextRed;
				prered.nextRed=red1;
				red1.prevRed=prered;
				postred.prevRed=red2;
				red2.nextRed=postred;
				edge.myRedEdge=null;
				edge.origin.halfedge=side1;
				oppV.halfedge=side2;
				oppV.redFlag=true;
			}
			
			side2.twin.prev=edge.twin.prev;
			edge.twin.prev.next=side2.twin;
			side2.twin.next=side1.twin;
			side1.twin.prev=side2.twin;
			side1.twin.next=edge.twin.next;
			edge.twin.next.prev=side1.twin;
			
			// add to bdry
			oppV.bdryFlag=1;
			side1.twin.face=edge.twin.face;
			side2.twin.face=edge.twin.face;

			// Note: negative --> calling routine sets new red edge data
			pdcel.triData=null;
			return -oppV.vertIndx;
		} // done with bdry edge case
		
		// interior edge? 
		if (vend.isBdry() || wend.isBdry()) {
			CirclePack.cpb.errMsg(
					"usage: rm_edge: edge either bdry or with two interior ends");
			return 0;
		}
		
		if (vend.vertIndx==pdcel.alpha.origin.vertIndx ||
				wend.vertIndx==pdcel.alpha.origin.vertIndx)
			pdcel.alpha=null; // calling routine must reset
		
		HalfEdge[] outer=new HalfEdge[4];
		outer[0]=edge.next;
		outer[1]=edge.next.next;
		outer[2]=edge.twin.next;
		outer[3]=edge.twin.next.next;
		
		// bypass 'edge'
		outer[1].next=outer[2];
		outer[2].prev=outer[1];
		outer[3].next=outer[0];
		outer[0].prev=outer[3];
			
		DcelFace face=new DcelFace(-1); // new ideal face
		for (int j=0;j<4;j++) {
			outer[j].origin.halfedge=outer[(j-1+4)%4].twin;
			outer[j].face=face;
			outer[j].origin.bdryFlag=1;
		}
		face.edge=outer[2];
		pdcel.redChain=null;
		pdcel.triData=null;
		return 1;
	}
	
	/**
	 * Meld the two ends of 'edge' <v,w>, orphaning the edge
	 * and one end. If <v,w> is cclw bdry edge or <v,w> is 
	 * interior and w is interior, then orphan w.
	 * Try to keep the red chain intact, but if too 
	 * complicated, set to null. 'alpha' may also be set to null.
	 * This can be used to reverse 'splitEdge_raw' and
	 * 'splitFlower_raw' if called with the proper 'edge'
	 * 
	 * TODO: synchronize meldEdge with splitEdge and splitFlwoer
	 * so that the orphaned edge in meldEdge is the newly
	 * created edge during the split operations. 
	 * 
	 * @param pdcel PackDCEL
	 * @param edge HalfEdge
	 * @return int, orphaned vert index, 0 on failure
	 */
	public static int meldEdge_raw(PackDCEL pdcel, HalfEdge edge) {
		Vertex vend = edge.origin;
		Vertex wend = edge.next.origin;
		int w = wend.vertIndx;

		// bdry edge?
		if (edge.isBdry()) { // DCELdebug.printRedChain(pdcel.redChain);
			// get positive orientation
			if (edge.face != null && edge.face.faceIndx < 0) {
				edge = edge.twin;
				vend = edge.origin;
				wend = edge.next.origin;
				w = wend.vertIndx;
			}
			Vertex oppV = edge.next.next.origin;
			if (oppV.bdryFlag == 0 && oppV.getNum() == 3)
				throw new CombException("melding edge " + edge + " woulc create a degree-2 interior nghb");

			RedEdge redge = edge.myRedEdge;
			RedEdge prevred = redge.prevRed;
			RedEdge postred = redge.nextRed;
			HalfEdge side1 = edge.prev.twin;
			HalfEdge side2 = edge.next.twin;
			HalfEdge outerup = edge.twin.prev;
			HalfEdge outerdown = edge.twin.next;

			// settle red chain first
			if (pdcel.redChain == redge)
				pdcel.redChain = redge.nextRed;
			prevred.nextRed = postred;
			postred.prevRed = prevred;
			if (prevred.myEdge == side1.twin) {
				prevred.myEdge = side2;
				side2.myRedEdge = prevred;
			} else if (postred.myEdge == side2.twin) {
				postred.myEdge = side1;
				side1.myRedEdge = postred;
			}

			// orphan wend
			HalfEdge he = wend.halfedge;
			vend.halfedge = he;
			do {
				he.origin = vend;
				he = he.prev.twin;
			} while (he != side2.twin);
			side2.twin.origin = vend;

			// twin the sides
			side1.twin = side2;
			side2.twin = side1;

			outerup.next = outerdown;
			outerdown.prev = outerup;
		}

		// interior case
		else {

			if (vend.isBdry() && wend.isBdry())
				throw new CombException("melding edge " + edge + " would disconnect.");

			// one bdry end; make that 'v'
			if (vend.isBdry() || wend.isBdry()) {
				if (wend.isBdry()) {
					edge = edge.twin;
					vend = edge.origin;
					wend = edge.twin.origin;
					w = wend.vertIndx;
				}
				HalfEdge in_v = vend.halfedge.twin;
				HalfEdge out_v = in_v.next;

				Vertex left_v = edge.prev.origin;
				Vertex right_v = edge.twin.prev.origin;
				if ((left_v.bdryFlag == 0 && left_v.getNum() == 3)
						|| (right_v.bdryFlag == 0 && right_v.getNum() == 3)) {
					CirclePack.cpb.errMsg("can't remove edge " + edge + " because it create a degree-2 interior nghb");
					return 0;
				}

				// handle red chain first
				RedEdge redouter = vend.halfedge.myRedEdge;
				if (pdcel.redChain == redouter)
					pdcel.redChain = redouter.nextRed;

				if (edge.myRedEdge != null) { // must have red twin, too
					RedEdge redge = edge.myRedEdge;
					RedEdge rtwin = edge.twin.myRedEdge;

					if (redge.nextRed == rtwin) { // can shrink, then neglect
						redge.prevRed.nextRed = rtwin.nextRed;
						rtwin.nextRed.prevRed = redge.prevRed;
					} else {
						redge.prevRed.nextRed = redge.nextRed;
						redge.nextRed.prevRed = redge.prevRed;
						rtwin.prevRed.nextRed = rtwin.nextRed;
						rtwin.nextRed.prevRed = rtwin.prevRed;
					}
				}

				// orphan w
				if (pdcel.alpha.getInvDist() == wend.vertIndx)
					pdcel.alpha = null;
				HalfEdge side1 = edge.next.twin;
				HalfEdge side2 = edge.twin.prev.twin;
				HalfEdge he = side2;
				do {
					he.origin = vend;
					he = he.prev.twin;
				} while (he != side1.twin);

				HalfEdge side1twin = side1.twin.next.twin;
				if (side1twin == out_v) { // may be out_v
					out_v.twin.origin.halfedge = side1;
					side1.myRedEdge = out_v.twin.myRedEdge;
					side1.myRedEdge.myEdge = side1;
					out_v.twin = side1;
					side1.twin = out_v;
					side1.origin.halfedge = side1;
				} else {
					side1.twin = side1twin;
					side1twin.twin = side1;
					if (side1twin.myRedEdge != null || side1.myRedEdge != null)
						pdcel.redChain = null;
				}

				HalfEdge side2twin = side2.twin.prev.twin;
				if (side2twin == in_v) { // may be in_v
					in_v.twin = side2;
					side2.twin = in_v;
					side2.myRedEdge = in_v.twin.myRedEdge;
					side2.myRedEdge.myEdge = side2;
				} else {
					if (side2.myRedEdge != null || side2twin.myRedEdge != null)
						pdcel.redChain = null;
					side2twin.twin = side2;
					side2.twin = side2twin;
				}
			}

			// both ends internal
			else {

				// these edges are kept
				HalfEdge v_out = edge.prev.twin;
				HalfEdge w_in = edge.next.twin;
				HalfEdge w_out = edge.twin.prev.twin;
				HalfEdge v_in = edge.twin.next.twin;
				Vertex left_v = w_in.origin;
				Vertex right_v = v_in.origin;

				if ((left_v.bdryFlag == 0 && left_v.getNum() == 3)
						|| (right_v.bdryFlag == 0 && right_v.getNum() == 3)) {
					CirclePack.cpb.errMsg("can't remove edge " + edge + " because it would leave a degree-2 vertex");
					return 0;
				}

				// any red chain involvement?
				if (vend.redFlag || wend.redFlag) {
					RedEdge redge = edge.myRedEdge; // must be twinned, too

					// if others not involved, may be able to fix
					if (!left_v.redFlag && !right_v.redFlag) {
						RedEdge rprev = redge.prevRed;
						RedEdge rnxt = redge.nextRed;
						RedEdge tprev = redge.twinRed.prevRed;
						RedEdge tnxt = redge.twinRed.nextRed;

						// twinned reds before or after? can fix
						if (rprev.twinRed == tnxt || rnxt.twinRed == tprev) {
							rprev.nextRed = rnxt;
							rnxt.prevRed = rprev;
							tprev.nextRed = tnxt;
							tnxt.prevRed = tprev;
						} else
							pdcel.redChain = null;
					} else
						pdcel.redChain = null;
				}

				// check 'halfedge's
				if (vend.halfedge == edge || vend.halfedge == v_in.twin)
					vend.halfedge = v_out; // will replace 'left2v.twin'
				if (left_v.halfedge == v_out.twin)
					left_v.halfedge = w_in;
				if (right_v.halfedge == w_out.twin)
					right_v.halfedge = v_in;

				// orphan wend
				HalfEdge he = w_out;
				he.origin = vend;
				do {
					he = he.prev.twin; // cclw
					he.origin = vend;
				} while (he != w_in.twin);

				// collapse the quad
				w_in.twin = v_out;
				v_out.twin = w_in;
				w_out.twin = v_in;
				v_in.twin = w_out;
			}
// debugging
//		HalfLink spokes=right_v.getEdgeFlower();
//		spokes=left_v.getEdgeFlower();
//		spokes=vend.getEdgeFlower();

		}

		// w is orphaned
		RawManip.removeVertIndex(pdcel, w);
		return w;
	}
	
	/**
	 * Use 'NodeLink' to create a closed chain of new
	 * red edges. The links must be contiguous and must close up.
	 * We set 'myEdge's for the 'RedEdge's but do not set
	 * their 'myRedEdge' or 'Vertex.redFlag's.
	 * @param pdcel PackDCEL
	 * @param vlink NodeLink
	 * @return null on error, disconnected or not closed
	 */
	public static RedEdge vlink2red(PackDCEL pdcel,NodeLink vlink) {
		if (vlink==null || vlink.size()<2)
			return null;
		int v=vlink.remove(0);
		int w=vlink.remove(0);
		HalfEdge startedge=pdcel.findHalfEdge(v,w);
		if (startedge==null)
			throw new CombException("usage: 'vlink2red', initial vertices "
					+v+" and "+w+" do not form an edge");
		RedEdge newChain=new RedEdge(startedge);
		RedEdge lastedge=newChain;
		int lastvert=w;
		int nextvert=w;
		Iterator<Integer> vis=vlink.iterator();
		while (vis.hasNext()) {
			lastvert=nextvert;
			nextvert=vis.next();
			if (nextvert==lastvert) // ignore repeats
				continue;
			HalfEdge he=pdcel.findHalfEdge(lastvert,nextvert);
			if (he!=null) {
				lastedge.nextRed=new RedEdge(he);
				lastedge.nextRed.prevRed=lastedge;
				lastedge=lastedge.nextRed;
			}
			else 
				throw new CombException("usage: 'vlink2red', "+lastvert+
						" and "+nextvert+" are not connected");
		}
		
		// close up?
		HalfEdge he=pdcel.findHalfEdge(nextvert,startedge.origin.vertIndx);
		if (he==null)
			throw new CombException("usage: 'vlink2red', can't close, "+
					nextvert+" and "+startedge.origin.vertIndx+
					" are not  not connected");
		lastedge.nextRed=new RedEdge(he);
		lastedge.nextRed.prevRed=lastedge;
		lastedge=lastedge.nextRed;
		lastedge.nextRed=newChain;
		newChain.prevRed=lastedge;
		return newChain;
	}
	
	/**
	 * Wipe out the linked 'RedEdge's starting with 'redChain': 
	 * null 'myEdge' references to 'myRedEdge', null 'redChain', 
	 * orphan all 'RedEdges' so they can be garbaged. 
	 * Return 0 if there seems to be a problem -- e.g., the 
	 * redChain isn't closed.
	 * 
	 * NOTE: not sure this is ever necessary; generally, 
	 * just setting 'redChain' to null is enough.
	 * 
	 * @param pdcel PackDCEL
	 * @param redchain RedEdge
	 * @return int count if seems successful, -count if problem
	 */
	public static int wipeRedChain(PackDCEL pdcel,RedEdge redchain) {
		int count=0;
		RedEdge rtrace=redchain;
		RedEdge hold=null;
		int safety=5000;
		do {
			safety--;
			rtrace.myEdge.origin.redFlag=false;
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
	
	/**
	 * Remove the 'Vertex' with index 'v', readjusting 'vertices',
	 * 'vertexCount', and 'oldNew'. If 'oldNew' is null and 'v' 
	 * is less than 'vertexCount', then 'oldNew' is initiated.
	 * This code allows various 'raw' routines to be called
	 * multiple times before requiring combinatorial processing.
	 * @param pdcel PackDCEL
	 * @param v int
	 * @return int, new 'vertCount'
	 */
	public static int removeVertIndex(PackDCEL pdcel,int v) {
		VertexMap oldnew = new VertexMap();
		if (v<0 || v>pdcel.vertCount)
			throw new CombException("trying to remove improper index "+v);
		for (int j = v + 1; j <= pdcel.vertCount; j++) {
			pdcel.vertices[j - 1] = pdcel.vertices[j];
			pdcel.vertices[j - 1].vertIndx = j - 1;
			oldnew.add(new EdgeSimple(j, j - 1));
		}
		pdcel.vertCount--;
		// adjust 'oldNew' by composing.
		pdcel.triData=null;
		VertexMap.followedBy(pdcel.oldNew, oldnew);
		return pdcel.vertCount;
	}
	
	/**
	 * Build the 'HalfLink' whose faces define an oriented 
	 * chain outside of 'beach'; beach is typically a closed 
	 * chain of vertices, as a cclw beach around an island. 
	 * If 'beach' is all interior, the result should define a
	 * closed chain of faces (so the first and last 'HalfEdge's
	 * have the same face). Otherwise, 'HalfLink' will define
	 * one or more open chains of faces, which the calling 
	 * routine processes. Return null if 'beach' is all bdry.
	 * 
	 * TODO:
	 * Combinatorial detail: if face f2 shares an edge e 
	 * with f0 and vert v of f2 opposite to e is degree 3, 
	 * then may want to swallow v into the interior; 
	 * i.e., .. f0 f1 f2 .. replaced by .. f0 f2 .. 
	 *  
	 * @param p @see PackData
	 * @param beach @see NodeLink
	 * @return ArrayList<HalfLink>, null on error
	 */
	public static ArrayList<HalfLink> islandSurround(PackDCEL pdcel,
			NodeLink beach) {
		
		// check validity, remove repeat at end 
		if (beach==null || beach.size()==0)
			return null;
		if (beach.get(0)==beach.getLast()) 
			beach.removeLast();
		
		// build 'HalfLink' chain(s) from 'beach'.
		HalfLink blink=new HalfLink();
		Iterator<Integer> bis=beach.iterator();
		int v=bis.next();
		boolean bdryhits=pdcel.vertices[v].isBdry();
		int w=v;
		while (bis.hasNext()) {
			v=w;
			w=bis.next();
			HalfEdge he=pdcel.findHalfEdge(v,w);
			if (he==null)
				return null;
			if (he.origin.isBdry())
				bdryhits=true;
			blink.add(he);
		}

		// close up unless beach has boundary vertices
		HalfEdge he=pdcel.findHalfEdge(beach.getLast(),beach.get(0));
		if (he==null && !bdryhits) 
			return null;
		blink.add(he);
		
		// TODO: thought about cleanup to check for deg-3 
		//       interiors to enclose or intruding faces 
		//       to bypass. But decided it wasn't worth it.
		
		// find bdry edges that are followed segments of interior edges
		ArrayList<HalfLink> multiLinks=new ArrayList<HalfLink>();

		int bcount=blink.size();
		int[] marks=new int[bcount];
		if (bdryhits) {
			for (int j=0;j<bcount;j++) {
				he=blink.get(j);
				if (he.origin.bdryFlag!=0 && he.next.origin.bdryFlag==0)
					marks[j]=1;
			}
		}

		// bdry in 'beach' means possible segments
		if (bdryhits) {
			for (int j=0;j<bcount;j++) {
				// does a new segment starts here?
				if (marks[j]==1) {
					HalfLink tmplink=new HalfLink();
					for (int k=j;k<bcount;k++) 
						tmplink.add(blink.get(k));
					HalfLink linkseg=RawManip.rightsideLink(pdcel,tmplink);

					// may add at beginning/end until we reach bdry
					int safety=1000;
					if (linkseg.size()>0) {
						he=linkseg.get(0).twin.next.next;
						linkseg.add(0,he);
						he=he.twin.next; // clw
						while ((he.twin.face==null || he.twin.face.faceIndx>0) &&
								safety>0) {
							safety--;
							linkseg.add(0,he);
							he=he.twin.next; // clw
						}
						if (safety==0)
							throw new CombException("extending end of 'linkseg'");
						he=linkseg.getLast();
						he=he.next.twin; // cclw
						while ((he.twin.face==null || he.twin.face.faceIndx>0) && 
								safety>0) {
							safety--;
							linkseg.add(he);
							he=he.prev.twin; // cclw
						}
						if (safety==0)
							throw new CombException("extending end of 'linkseg'");
						
						multiLinks.add(linkseg);
					}
				}
			} // loop for new segments
			
			return multiLinks;
		} // done if multiple segments			
		
		HalfEdge nexthe=blink.get(0);
		HalfEdge currhe=nexthe;
		HalfLink seg=RawManip.rightsideLink(pdcel,blink);
		if (seg==null) 
			return null;
		
		// add twin of first edge
		seg.add(blink.get(0).twin);
		
		// add cclw spokes 
		for (int j=0;j<bcount;j++) {
			currhe=blink.get(j);
			nexthe=blink.get((j+1)%bcount);
			he=currhe.twin.prev.twin;
			while (he!=nexthe) { 
				seg.add(he);
				he=he.prev.twin; // cclw
			}
		}
		
		multiLinks.add(seg);
		return multiLinks; 
	}

	/**
	 * Build the 'HalfLink' whose faces define the contiguous
	 * chain of faces to the right of 'hlink'. So these are
	 * halfedges outward from the right; used, e.g., to get
	 * the faces surrounding an island. Start with halfedges 
	 * outward from the end of the first edge of 'hlink' and 
	 * continue only as long as the edges of 'hlink' remain 
	 * contiguous and we don't encounter an outgoing edge 
	 * whose face is ideal. Stop with outgoing edges for 
	 * the origin of the last halfedge, but watch in case 
	 * 'hlink' is closed --- then end with the last cclw 
	 * outgoing edges from the common vertex. The link we
	 * return here does not have the initial edge of 'hlink'
	 * in it: the user may need to insert that or other
	 * edges at one end, depending on the purpose.
	 * @param pdcel PackDCEL
	 * @param hlink HalfLink, should be contiguous, may be closed
	 * @return HalfLink, may be null or empty
	 */
	public static HalfLink rightsideLink(PackDCEL pdcel,HalfLink hlink) {
		if (hlink==null || hlink.size()==0)
			return null;
		HalfLink outgoing=new HalfLink(pdcel.p);
		
		// save first/last
		HalfEdge firsthe=hlink.getFirst();
		HalfEdge lasthe=hlink.getLast();
		 
		Iterator<HalfEdge> his=hlink.iterator();
		HalfEdge currhe=his.next();
		HalfEdge nexthe=currhe;
		while (his.hasNext()) {
			  currhe=nexthe;
			  nexthe=his.next();
			  
			  // not contiguous?
			  if (currhe.twin.origin!=nexthe.origin) {
				  return outgoing;
			  }
			  
			  HalfEdge he=currhe.twin.prev.twin;
			  while (he!=nexthe) {
				  
				  // hit an ideal face?
				  if (he.face!=null && he.face.faceIndx<0) 
					  return outgoing;
				  
				  outgoing.add(he);
				  he=he.prev.twin; // cclw
			  }
		}
		if (nexthe==lasthe) {
			HalfEdge he=nexthe.twin.prev.twin;
			while (he!=firsthe) {
				  
				// hit an ideal face?
				if (he.face!=null && he.face.faceIndx<0) 
					return outgoing;
				  
				outgoing.add(he);
				he=he.prev.twin; // cclw
			}
		}
		return outgoing;
	}

}
