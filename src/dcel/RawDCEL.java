package dcel;

import java.util.ArrayList;

import complex.Complex;
import exceptions.CombException;
import exceptions.DCELException;

/**
 * This file is for static methods applied to dcel structures.
 * These "*_raw" methods typically work just with combinatorics
 * (no rad/cents, little or no dependence on PackData parent).
 * 
 * Typical process: 
 *   (1) call 'zeroVUtil' to zero out 'vutil' entries.
 *   (2) call the '*_raw' method to modify the dcel structure,
 *   (3) call 'reapVUtil' to create a 'VertexMap' of "new_to_old"
 *       references (i.e. "the new vert v takes radius from 
 *       old vert w").
 *   (4) call 'fixDCEL_raw':  
 *   	 * If red chain could not be safely modified in the 
 *   	   '*_raw', set 'redChain' null and call 'redchain_by_edge'
 *       * call 'd_FillInside' and 'attachDCEL'. 
 *   (5) call 'modRadCents' with 'VertexMap' to modify rad and/or cents.
 * 
 * @author kens
 *
 */
public class RawDCEL {

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
	   * Create a barycenter for face 'f'; 'vutil' 
	   * gives reference vert. Calling routine should
	   * throw out 'redChain' if 'f' is ideal face.
	   * @param pdcel PackDCEL
	   * @param f Face
	   * @return int new index
	   */
	  public static int addBary_raw(PackDCEL pdcel,
			  Face f,boolean multi_bary) {
		  
		  // make room
		  int node=pdcel.vertCount+1; // new index 
		  if (node>=pdcel.p.sizeLimit)
			  pdcel.p.alloc_pack_space(node+10,true);
			
		  ArrayList<HalfEdge> polyE=null;
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
		  next_in=new HalfEdge(base.twin.origin);
		  
		  base.next=next_in;
		  next_in.prev=base;
	
		  next_in.next=last_spoke;
		  last_spoke.prev=next_in;
		  
		  last_spoke.next=base;
		  base.prev=last_spoke;
		  
		  next_in.twin=hold_spoke;
		  hold_spoke.twin=next_in;
		  
		  base=polyE.get(0);
	
		  // fix up 'newV'
		  newV.halfedge=hold_spoke;
		  newV.vutil=newV.halfedge.twin.origin.vertIndx;
		  pdcel.vertices[node]=newV;
		  pdcel.vertCount=node;
				
		  return node;
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
		  
		  Vertex v2=edge1.twin.next.next.twin.origin;
		  Vertex u=edge1.twin.next.twin.origin;
		  if (v2.vertIndx==w) { // check clw two steps
			  if (edge1.next.next.origin.vertIndx==w) { // ambiguous: choose cclw
				  v2=edge1.next.next.origin;
				  u=edge1.next.origin;
			  }
			  else { // swap v and w
				  v1=v2;
				  v2=v1.halfedge.next.next.origin;
			  }
		  }
		  else { // else go cclw
			  v2=edge1.twin.prev.origin;
			  if (v2.vertIndx!=w)
				  throw new CombException("vertices "+v+" and "+w+" can't form a legal edge");
			  u=edge1.next.origin;
		  }
		  
		  // now v1, u, v2 should be cclw nghbs
		  edge1=v1.halfedge;
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
		  newtwin.face=newtwin.next.face;
		  
		  edge2.origin.bdryFlag=0;
		  v1.halfedge=newedge;
		  
		  return newedge;
	  }

	/** 
		   * Add a layer of nodes to bdry segment from vertex v1 to v2.
		   * Three modes:
		   * 
		   * TENT: add one-on-one layer, a new bdry vert for 
		   *   each edge between v1 and v2. Unless v1==v2, 
		   *   v1 and v2 remain as bdry vertices.	
		   *   
		   * DEGREE: add nghb's to make vertices from v1 to v2,
		   *   inclusive, interior with degree d. However, no edge
		   *   should connect existing bdry vertices. If v1==v2 or
		   *   v1 is nghb of v2, do whole bdry component.
		   *   
		   * DUPLICATE: attach "square" face with bary center 
		   *   to each edge between v1 and v2. Unless v1==v2, 
		   *   v1 and v2 remain on bdry.
		   *   
		   * Calling routine updates combinatorics.
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
			  if (mode==DUPLICATE) {
				  count+=RawDCEL.baryBox_raw(pdcel, v1, v2);
			  }
			  return count;
		  }

	/**
	   * Add vertex nghb'ing bdry vertex w and clw bdry nghb.
	   * Set 'vutil' to 'w'.
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
		  pdcel.vertices[node]=new_vert;
		  new_vert.bdryFlag=1;
		  new_vert.redFlag=true;
		  new_vert.vutil=w;
		  pdcel.vertCount++;
	
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
			base_twin.myRedEdge=null;
	
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
	   * Flip an edge in a triangulation; 'edge' must be shared by two non-ideal
	   * faces. To "flip" means to replace the edge by the other diagonal in the union
	   * of faces. If 'edge' is in red chain, then set 'redChain' null. The number of
	   * faces, edges, and vertices is not changed; calling routine handles
	   * processing.
	   * 
	   * @param pdcel PackDCEL
	   * @param edge  HalfEdge
	   * @return int, 1 on success, 0 on failure
	   */
	  public static int flipEdge_raw(PackDCEL pdcel, HalfEdge edge) {
		  if (pdcel.isBdryEdge(edge)) // bdry?
			  return 0;
		  if (edge.myRedEdge != null) // in redchain
			  pdcel.redChain = null;
		  Face leftf = edge.face;
		  Face rightf = edge.twin.face;
		  Vertex leftv = edge.next.next.origin;
		  Vertex rightv = edge.twin.next.next.origin;

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

		  return 1;
	  }

}