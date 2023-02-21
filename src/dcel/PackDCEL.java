package dcel;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import deBugging.DCELdebug;
import exceptions.CombException;
import exceptions.DCELException;
import exceptions.DataException;
import exceptions.InOutException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.PointLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import posting.PostFactory;
import tiling.Tile;
import util.ColorUtil;
import util.DispFlags;
import util.TriAspect;
import util.TriData;
import util.UtilPacket;

/** 
 * The "DCEL" is a common way that computer scientists 
 * encode graphs; it's also called a 'half-edge' structure.
 * I have converted the combinatorics underlying CirclePack
 * to use this model (at the suggestion of John Bowers).
 * 
 * 'CirclePack' are marked with "NEEDED FOR CIRCLEPACK". 
 * 
 * Note on indices: Vertices, edges, and faces all get indices;
 * these are independent and all start indexing with 1. Those 
 * for vertices are most important, others are more ephemeral and
 * for convenience. 
 * 
 * The principal route to creating DCEL structures uses 'bouquet's
 * in 'CombDCEL.d_redChainBuilder', which first calls 
 * 'CombDCEL.createVE' to build vertices and faces and set 
 * 'alpha', then creates the redchain, and then calls 
 * 'CombDCEL.fillInside' to finish. (This is done because many 
 * manipulations, such as adding barycenters, need only call 
 * 'fillInside' to adjust the DCEL structure if
 * the redchain was not disturbed.) Note that these routines create 
 * only a 'PackDCEL' object; a call to 'DataDCEL.dcel_to_packing' 
 * will create an associated packing.
 * 
 * @author kstephe2, July 2016
 *
 */
public class PackDCEL {
	public int sizeLimit;
	public PackData p;

	public int vertCount;		// number of vertices (eventually sync'ed with nodeCount)
	public int edgeCount;
	public int faceCount;
	public int intFaceCount;	// number of interior faces (larger face indices are ideal faces)
	public int idealFaceCount;  // indexes are the largest and set to negative
	
	// important objects: vertices is key, edges/faces/idealFaces more ephemeral
	public Vertex[] vertices; // indexed from 1
	public HalfEdge[] edges;  // indexed from 1; some have pointers to 'RedEdge's
	public DcelFace[] faces;      // indexed from 1
	public DcelFace[] idealFaces; // indexed from 1 (but 'faceIndx' is the negative of the index)
	// note edges and faces are subject to change with new layout

	// final layout information 
	public HalfLink layoutOrder; // order sufficient to set every center
	public HalfLink stragglers; // faces not in layoutOrder
	
	public VertexMap oldNew;  // NEEDED FOR CIRCLEPACK {old,new}
	public RedEdge redChain; // doubly-linked, cclw edges about a fundamental region
	public ArrayList<RedEdge> sideStarts; // red edges starting paired sides, will have 'mobIndx'
	public HalfEdge alpha; // origin normally placed at origin
	public HalfEdge gamma; // origin normally on positive y-axis, default 'alpha.twin'
	
	public PairLink pairLink;  // linked list of 'SideData' on side-pairings
	
	// face-by-face data storage: used, e.g. in typical 'repack' 
	//   calls and for work in 'ProjStruct'. Offen the data is
	//   in 'labels' and treated as homogeneous (eucl) data.
	public TriData[] triData; 
	
	boolean debug;
	
	// Constructor(s)
	public PackDCEL() { // naked shell
		sizeLimit=alloc_vert_space(1000,false);
		p=null;
		vertCount=0;
		vertices=null;
		edges=null;
		faces=null;
		idealFaces=null;
		triData=null;
		
		// things to fill at end of dcel construction
		layoutOrder=null;
		stragglers=null;

		oldNew=null;
		redChain=null;
		debug=false;
	}

/*	
    if (keepit && size==sizeLimit) { 
    	if (packDCEL.vertices.length<sizeLimit+1) {
    		packDCEL.alloc_vert_space(sizeLimit,keepit);
    	}
        return 1; 
    }
    
    if (keepit) { // transfer the old data, allocate expansion space
       	Vertex[] new_vertices=new Vertex[sizeLimit+1];
       	for (int v=1;v<=packDCEL.vertCount;v++)
       		new_vertices[v]=packDCEL.vertices[v];
    }
*/    
	
	/**
	 * Cleanup routine: needed because '*_raw' routines 
	 * modify dcel structure w/o complete update: 'vertCount', 
	 * 'vertices', edge connectivity should be in tact and 
	 * often 'redChain' is in tact. The calling routine 
	 * should already have done things like cents/radii. 
	 * If the red chain was broken, the calling routine 
	 * should set 'redChain'=null so that 'redchain_by_edge' is
	 * called. Faces may be outdated or non-existent, 
	 * 'edges', 'faces', counts, etc. need updating, 
	 * so 'fillInside' is called. Also, need to 'attach' 
	 * to a packing (usually the current parent) or not 
	 * if packing is null. This call does not "prune" the
	 * complex.
	 * @param p PackData
	 */
	public void fixDCEL(PackData p) {
		fixDCEL(p,false);
	}
	
	/**
	 * Cleanup routine: needed because '*_raw' routines 
	 * modify dcel structure w/o complete update: 'vertCount', 
	 * 'vertices', edge connectivity should be in tact and 
	 * often 'redChain' is in tact. The calling routine 
	 * should already have done things like cents/radii. 
	 * If the red chain was broken, the calling routine 
	 * should set 'redChain'=null so that 'redchain_by_edge' is
	 * called. Faces may be outdated or non-existent, 
	 * 'edges', 'faces', counts, etc. need updating, 
	 * so 'fillInside' is called. Also, need to 'attach' 
	 * to a packing (usually the current parent) or not 
	 * if packing is null. If 'prune' is set, then vertices
	 * without interior nghbs are prunned. 
	 * @param p PackData
	 * @param prune boolean
	 */
	public void fixDCEL(PackData p,boolean prune) {
		if (p==null)
			p=this.p;
		try {
		  // may need new red chain
		  if (redChain==null) {
			  CombDCEL.redchain_by_edge(this, null, this.alpha,prune);
		  } // DCELdebug.rededgecenters(this);
		  // redChain should now exist, but can be in error, so take 
		  //     two trys
		  try {
			  CombDCEL.fillInside(this); // p.getCenter(300);
		  }
		  catch (Exception ex) { // assume redChain problem, try again
			  CirclePack.cpb.errMsg("Had to try new 'redChain' "+
					  "in 'fixDCE_raw' call");
			  redChain=null;
			  CombDCEL.redchain_by_edge(this, null, this.alpha,prune);
			  CombDCEL.fillInside(this); // p.getCenter(300);
		  } // p.packDCEL.findHalfEdge(11,2).face;

		  if (p!=null)
			  p.attachDCEL(this);
		  p.status=true;
	  } catch (Exception ex) {
		  throw new DCELException("Problem with 'fix_raw' or 'attachDCEL'. "
				  +ex.getMessage());
	  }
	}
	
	/**
	 * Reallocate space for 'vertices' in increments of 1000.
	 * @param new_size int
	 * @param keepit boolean, true, then copy existing
	 * @return int size
	 */
	public int alloc_vert_space(int new_size,boolean keepit) {
        sizeLimit=((int)((new_size-1)/1000))*1000+1000;
		Vertex[] new_vs=new Vertex[sizeLimit+1];
		if (keepit)
			for (int j=1;j<=vertCount;j++) 
				new_vs[j]=vertices[j];
		vertices=new_vs;
		return sizeLimit;
	}
	/**
	 * Create and populate 'triData[]'. This loads 'radii', 
	 * 'invDist's, 'aim'.
	 * @return int faceCount
	 */
	public int allocTriData() {
		triData=new TriData[faceCount+1];
		for (int f=1;f<=faceCount;f++) {
			triData[f]=new TriData(this,faces[f]);
		}
		return faceCount;
	}
	
	/**
	 * Update 'triData', creating, if necessary. Return true 
	 * if there are nontrivial inversive distances so we can set
	 * "oldReliable" flag in repacking.
	 * @return boolean
	 */
	public boolean updateTriDataRadii() {
		boolean hit=false;
		if (triData==null || triData.length!=(faceCount+1)) {
			triData=new TriData[faceCount+1];
			for (int f=1;f<=faceCount;f++) {
				triData[f]=new TriData(this,faces[f]);
				if (triData[f].hasInvDist())
					hit=true;
			}
		}
		for (int f=1;f<=faceCount;f++) { 
			triData[f].hes=p.hes;
			HalfLink eflower=faces[f].getEdges();
			if (eflower.size()!=3)
				throw new DataException();
			for (int j=0;j<3;j++) {
				HalfEdge he=eflower.get(j);
				int v=he.origin.vertIndx;
				triData[f].radii[j]=p.getRadius(v);
				double ivd=he.getInvDist();
				if (ivd!=1.0) {
					triData[f].setInvDist(j,ivd);
					hit=true;
				}
			}	
		}
		return hit;
	}

	/**
	 * The "red" chain is a closed cclw chain of edges about
	 * a simply connected fundamental domain for the complex.
	 * This is rather difficult because 'this' PackDCEL should 
	 * remain unchanged --- we depend on 'bouquet' to create 
	 * new 'Vertex's and 'HalfEdge's.
	 * @param arrayV ArrayList<Vertex>, vertices to keep, if null, keep all
	 * @return PackDCEL
	 */
	public PackDCEL redCookie(NodeLink vlist) {
		
		// to avoid redundant listing
		int []vhits=new int[vertCount+1];
		Iterator<Integer> vit=vlist.iterator();
		while (vit.hasNext())
			vhits[vit.next()]=1;
		
		// make up 'Vertex' array
		CPBase.Vlink=new NodeLink();
		for (int n=1;n<=vertCount;n++)
			if (vhits[n]==0) 
				CPBase.Vlink.add(n);
		String str="-n Vlist"; // list of non-keepers
		HalfLink hlink=CombDCEL.cookieData(p,str);
	    return CombDCEL.extractDCEL(this,hlink,alpha);
	}
	
	/**
	 * Given full set of edges, redo the face numbers. Start by
	 * setting all former '.face' entries to null and, starting
	 * with alpha, resetting them, renumbering from 1. Ideal 
	 * (i.e. outside) faces will have the highest indices.
	 * NOTE: we assume that any 'Vertex' v which is bdry will
	 * have 'v.halfedge.twin' in 'bdryedges'. 
	 * @param edges ArrayList<HalfEdge>
	 * @param bdryedges ArrayList<HalfEdge>
	 * @return int count, 0 on error
	 */
	public int indexFaces(ArrayList<HalfEdge> edges,
			ArrayList<HalfEdge> bdryedges) {

		faceCount=0;
		// need starting place; try 'alpha' first, else look for first interior
		int safety=2*edges.size();
		
		if (alpha==null || bdryedges.contains(alpha.twin)) {
			alpha=null;
			Iterator<HalfEdge> eit=edges.iterator();
			while (eit.hasNext() && alpha==null) {
				HalfEdge he=eit.next();
				if (!bdryedges.contains(he.origin.halfedge.twin))
					alpha=he.origin.halfedge;
			}
		}
		if (alpha==null) {
			CirclePack.cpb.errMsg("no appropriate 'alpha' vertex; use bdry vertex");
			alpha=edges.get(0);
		}
		
		// toss old 'faces' away
		ArrayList<DcelFace> tmpFaceList=new ArrayList<DcelFace>();
		tmpFaceList.add(null); // index from 1, first entry 'null'
		ArrayList<DcelFace> tmpIdealFaces=new ArrayList<DcelFace>(0);
		
		// start with 'alpha'
		edges.remove(alpha);
		edges.add(0, alpha);
		
		// remove all 'face' items from 'edges'
		Iterator<HalfEdge> egs=edges.iterator();
		while (egs.hasNext())
			egs.next().face=null;
		
		// have to look out for bdry components; each gets an ideal face
		//   marked via temporary negative indices, update them later
		int idealIndx=0; 

		// move out systematically looking for new faces; keep two lists
		ArrayList<HalfEdge> currE=new ArrayList<HalfEdge>();
		ArrayList<HalfEdge> nextE=new ArrayList<HalfEdge>();
		nextE.add(alpha);
		while (nextE!=null && nextE.size()>0 && safety>0) {
			currE=nextE;
			nextE=new ArrayList<HalfEdge>();
			
			Iterator<HalfEdge> current=currE.iterator();
			while (current.hasNext() && safety>0) {
				safety--;
				HalfEdge he=current.next();
				if (he.face==null) { // do this one
					
					// a new face is born
					DcelFace newFace=new DcelFace();
					newFace.edge=he;
					
					// ideal face? (reset indx later)
					if (bdryedges.contains(he)) { 
						newFace.faceIndx=-(++idealIndx);
						tmpIdealFaces.add(newFace);
					}
					// else, regular face
					else {
						newFace.faceIndx=++faceCount;
						tmpFaceList.add(newFace);
					}
					
					// point edges to it
					HalfEdge nxtedge=he;
					int localsafety=edges.size();
					do {
						if (nxtedge==null)
							System.err.println("whoops");
						nxtedge.face=newFace;
						if (nxtedge.twin==null) {
							System.err.println("missing twin");
						}
						if (nxtedge.twin.face==null) 
							nextE.add(nxtedge.twin);
						nxtedge=nxtedge.next;
						localsafety--;
						
					} while(nxtedge!=he && localsafety>0);
					
					if (localsafety<=0)
						throw new CombException("runaway loop on face "+he.face.faceIndx);
				}
			} // end of while for this face
			if (safety<=0)
				throw new CombException("runaway loop in face iteration");
				
		} // end of while through nextE
		if (safety<=0)
			throw new CombException("runaway loop in nextE iteration");
					
		// put ideal faces at the end of the list, giving them negative indices
		intFaceCount=faceCount; // now have all the interiors
		if (idealIndx>0) { // any ideal faces?
			idealFaces=new DcelFace[idealIndx+1];
			Iterator<DcelFace> tif=tmpIdealFaces.iterator();
			int tick=0;
			while (tif.hasNext()) {
				DcelFace face=tif.next();
				idealFaces[++tick]=face;
				face.faceIndx=-(faceCount+tick); // increment index, make negative
				tmpFaceList.add(face);
			}
			faceCount +=idealIndx; // 'faceCount' counts both regular and ideal faces
		}
		
		// store all the faces
		faces=new DcelFace[faceCount+1];
		Iterator<DcelFace> fit=tmpFaceList.iterator();
		fit.hasNext(); // shuck first null entry
		int tick=0;
		while (fit.hasNext()) {
			faces[++tick]=fit.next();
		}
		
		return faceCount;
	}

	/**
	 * Standard normalization: a at origin, g on positive y-axis.
	 * Not ready for spherical case yet
	 * @param pdcel PackDCEL
	 * @param a Complex
	 * @param g Complex
	 * @return the Mobius applied
	 */
	public static Mobius norm_any_pack(PackDCEL pdcel,Complex a,Complex g) {
	  if (pdcel.p.hes<0) 
		  return HyperbolicMath.h_norm_pack(pdcel,a,g);
	  if (pdcel.p.hes>0) 
		  return SphericalMath.s_norm_pack(pdcel,a,g);
	  return EuclMath.e_norm_pack(pdcel,a,g);
	} 

	/**
	 * Place a 'HalfEdge' so 'origin' is at z=0 and other
	 * end is on the positive imaginary axis. Store the centers. 
	 * @param edge HalfEdge
	 */
	public void placeFirstEdge(HalfEdge edge) {
		double r0=getVertRadius(edge);
		double r1=getVertRadius(edge.next);
		setCent4Edge(edge,new Complex(0.0));
		double invd=edge.getInvDist();
		double dist=CommonMath.ivd_edge_length(r0,r1,invd,p.hes);
		if (p.hes>0) // sph
			setCent4Edge(edge.next,new Complex(Math.PI/2.0,dist));
		else if (p.hes<0){ // hyp
			if (dist<0) { // horocycle?
				dist=1;

				// must also store negative of eucl radius
				double s_rad=Math.sqrt(1.0-r0);
				double e_rad=(1.0-s_rad)/(1.0+s_rad);
				e_rad=0.5*(1.0-e_rad);
				setRad4Edge(edge.next,-e_rad); 
			}
			else {
				double expdist=Math.exp(dist);
				dist=(expdist-1.0)/(expdist+1.0);
			}
			setCent4Edge(edge.next,new Complex(0.0,dist));
		}
		else // eucl
			setCent4Edge(edge.next,new Complex(0.0,dist));
		return;
	}
	
	/**
	 * Compute the center of the vertex opposite 'edge' in
	 * 'edge.face'. 
	 * @param edge HalfEdge
	 * @return CircleSimple  
	 */
	public CircleSimple d_compOppCenter(HalfEdge edge) {
		CircleSimple c0=getVertData(edge);
		CircleSimple c1=getVertData(edge.next);
		CircleSimple c2=getVertData(edge.next.next);
		double ov0=edge.next.getInvDist();
		double ov1=edge.prev.getInvDist();
		double ov2=edge.getInvDist();
		CircleSimple sC=CommonMath.comp_any_center(c0.center,
				c1.center,c0.rad,c1.rad,c2.rad,ov0,ov1,ov2,p.hes);
		return sC;
	}
	
	/**
	 * Compute center of vert v opposite 'edge' in 'edge.face'.
	 * Compute as average of locations based on all edges
	 * opposite v whose other centers are already in place,
	 * indicated by 'eutil>0' for twin. Set eutil=1 in all
	 * faces used in the computation to indicate these edges
	 * are okay to use in later computations.
	 * @param edge HalfEdge
	 * @return CircleSimple, null on failure
	 */
	public CircleSimple compAvgCenter(HalfEdge edge) {
		Vertex vert=edge.next.next.origin;
		
		// calculate center opposite 'edge'
		CircleSimple c0=getVertData(edge);
		CircleSimple c1=getVertData(edge.next);
		CircleSimple cv=getVertData(edge.next.next);
		double ov0=edge.next.getInvDist();
		double ov1=edge.next.next.getInvDist();
		double ov2=edge.getInvDist();
		CircleSimple sC=CommonMath.comp_any_center(c0.center,
				c1.center,c0.rad,c1.rad,cv.rad,ov0,ov1,ov2,p.hes);
		Complex accum=sC.center;
		edge.eutil=edge.next.eutil=edge.next.next.eutil=1;
		
		// Now calculate using other opposite edges
		HalfLink outers=vert.getOuterFan(edge);
		int tick=1;
		int n=outers.size();
		for (int j=0;j<n;j++) {
			HalfEdge he=outers.get(j);
			// don't use red edge: too complicated to see if dependable
			if (he.eutil==0 && he.twin.eutil!=0 && he.myRedEdge==null) {
				c0=getVertData(he);
				c1=getVertData(he.next);
				CircleSimple c2=getVertData(he.next.next);
				ov0=he.next.getInvDist();
				ov1=he.next.next.getInvDist();
				ov2=he.getInvDist();
				sC=CommonMath.comp_any_center(c0.center,
						c1.center,c0.rad,c1.rad,c2.rad,ov0,ov1,ov2,p.hes);
				accum=accum.add(sC.center);
				tick++;
				he.eutil=he.next.eutil=he.next.next.eutil=1;
			}
		}
		cv.center=accum.divide(tick);
		return cv;
	}
	
	/** 
	 * Return integer array with the generations of verts, 
	 * generation "1" being those v with 'vutil' 
	 * non-zero. Additional info is returned via 'uP'.  
	 * @param max int, if max>0, stop at last with gen = max.
	 * @param uP UtilPacket; instantiated by calling routine: 
	 *    returns last vertex as 'uP.rtnFlag' and
	 *    count of vertices as 'uP.value'.
	 * @return int[], int[u]=generation of u; return null on error
	 */
	public int[] label_generations(int max, UtilPacket uP) {
		int last_vert = vertCount;
		int gen_count = 2;
		int count = 0;

		int[] final_list = new int[vertCount + 1];
		NodeLink genlist = new NodeLink();

		// first generation identified by nonzero utilFlag's
		for (int i = 1; i <= vertCount; i++)
			if (p.getVertUtil(i) != 0) {
				final_list[i] = 1;
				count++;
				genlist.add(i);
				last_vert = i;
			}
		int n = genlist.size();
		// none/all vertices as seeds?
		if (n == 0 || n == vertCount)
			return null;

		boolean hits = true;
		while (hits && genlist.size() > 0 && (max <= 0 || gen_count <= max)) {
			hits = false;
			NodeLink vertlist = genlist; // process old list
			genlist = new NodeLink(); // start new list
			do {
				Vertex vert=vertices[vertlist.remove(0)];
				HalfEdge he=vert.halfedge;
				do {
					int w=he.twin.origin.vertIndx;
					if (final_list[w]==0) {
						final_list[w]=gen_count;
						count++;
						last_vert=w;
						genlist.add(w);
						hits=true;
					}
					he=he.prev.twin; // cclw
				} while (he!=vert.halfedge);
			} while (vertlist.size() > 0);
			gen_count++;
		}
		uP.rtnFlag = last_vert;
		uP.value = (double) count;
		return final_list;
	}
	
	/**
	 * Use 'layoutOrder' and radii to recompute centers. 
	 * Default to using 'layoutOrder' and not using Schwarzians.
	 * However, in some case (e.g. for debugging), may use
	 * other 'HalfLink' orders.
	 * @return int count
	 */
	public int layoutPacking() {
		return layoutPacking(null,false,null);
	}
	
	public int layoutPacking(HalfEdge firstHE) {
		return layoutPacking(null,false,firstHE);
	}
	
	public int layoutPacking(boolean useSchw) {
		return layoutPacking(null,useSchw,null);
	}
	
	public int layoutPacking(HalfLink hlink) {
		return layoutPacking(hlink,false,null);
	}
	  
	/**
	 * Use 'hlink' (default to 'layoutOrder') to compute
	 * centers, laying the first (generally 'alpha') edge 
	 * in normalized position (namely, from 0 to point on 
	 * positive y-axis). Then computing faces for successive
	 * 'hlink' entries. Finally, rotate so 'gamma' is on
	 * positive y-axis.
	 * 
	 * If 'hlink' is null, use 'layoutOrder', and compute 
	 * successive centers as averages using all opposite 
	 * edges which are already laid out. If 'hlink' is not 
	 * null, successive center are computed only using the 
	 * one face from 'hlink'. Some circles may get laid down 
	 * more than once depending on 'hlink'.
	 * 
	 * Note: When an angle sum at v is not a multiple of 2pi,
	 * then using averages for circles around v can end up
	 * jumbles, not forming any appropriate face. E.g., 'seed'
	 * with wrong radii.
	 * 
	 * If 'hlink' is null but 'firstHE' is not, then leave 
	 * 'firstHE' fixed in current position and do full 
	 * layout and do not rotate at end.
	 *  
	 * Layout can be based on radii or (in tangency setting) using 
	 * schwarzians. Side-pairing maps are updated.
	 * 
	 * Note: this command modifies the recorded centers.
	 * 
	 * @param hlink HalfLink
	 * @param useSchw boolean
	 * @param firstRE HalfEdge
	 * @return int count
	 */
	public int layoutPacking(HalfLink hlink,boolean useSchw, HalfEdge firstHE) {
	    int count=1;
	    boolean face_only=false;
	    boolean fixFirst=false;	    
	    HalfEdge firsthe=null;
	    HalfLink order=layoutOrder;

	    if (hlink!=null) {
	    	order=hlink;
	    	face_only=true; // use only one face to layout
		    firsthe=order.getFirst();
	    }
	    else if (firstHE!=null) {
	    	fixFirst=true;
	    	firsthe=firstHE;
	    }
	    else firsthe=order.getFirst();
	    
	    // eutil>0 means we expect ends to be in proper relative locations.
    	zeroEUtil();
	    
	    boolean debug=false;
//	    boolean debug=true;
	    int prev_g=-1;
	    
	    // first face is that of given firstHE?
	    if (!fixFirst) {
	    	placeFirstEdge(firsthe); // from 0 to point on positive y-axis
	    }
	    firsthe.eutil=firsthe.next.eutil=firsthe.next.next.eutil=1;
	    CircleSimple cs=d_compOppCenter(firsthe);
	    setCent4Edge(firsthe.prev, cs.center);
	    
		if (debug) {// debug=true;
			DCELdebug.drawEFC(this,firsthe);
		}
	    
	    TriAspect ftri=null;
	    TriAspect gtri=null;
	    if (useSchw) {
	    	gtri=new TriAspect(this,firsthe.face);
	    	prev_g=firsthe.face.faceIndx;
	    }
	    
	    // now layout face-by-face
		Iterator<HalfEdge> hit=order.iterator();
		hit.next(); // remove first, already laid out
	    while (hit.hasNext()) {
	    	HalfEdge he=hit.next();
	    	
// debugging
//	    	System.out.println("he="+he);
/*
System.out.println("length error ("+he.origin.vertIndx+","+he.next.origin.vertIndx+") = "+
  	((double)(he.origin.center.minus(he.next.origin.center).abs())-
  	(he.origin.rad+he.next.origin.rad)));
System.out.println("length error ("+he.next.origin.vertIndx+","+he.next.next.origin.vertIndx+") = "+
  	(he.next.origin.center.minus(he.next.next.origin.center).abs()-
  	(he.next.origin.rad+he.next.next.origin.rad)));
System.out.println("length error ("+he.prev.origin.vertIndx+","+he.origin.vertIndx+") = "+
  	(he.prev.origin.center.minus(he.origin.center).abs()-
  	(he.prev.origin.rad+he.origin.rad)));
*/  	
	    	
	    	if (!useSchw) {
	    		if (face_only)
	    			cs=d_compOppCenter(he);
	    		else 
	    			cs=compAvgCenter(he);
	    		setCent4Edge(he.prev,cs.center);
	    	}
	    	else { // use Schwarzian
	    		// TODO: not yet using average of computed centers
	    		if (he.twin.face.faceIndx==prev_g)
	    			ftri=gtri; // reuse this data
	    		else
	    			ftri=new TriAspect(this,he.twin.face);
	    		gtri=new TriAspect(this,he.face);
	    		prev_g=gtri.faceIndx;
	    		int ans=-1;
	    		ans= workshops.LayoutShop.schwPropogate(ftri,gtri,he.twin,
	    				he.getSchwarzian(),1);
	    	
	    		// get new data
	    		if (ans>=0) {
	    			gtri.data2pdcel(this);
	    			count++;

// debugging	    			
//	    			int v=he.prev.origin.vertIndx;
//		    		System.out.println("v="+v+" to z="+gtri.getCenter(
//	    				gtri.vertIndex(v)));
		    		
	    		}
	    		he.eutil=he.next.eutil=he.next.next.eutil=1;
	    	}

	    	if (debug) {// debug=true;
				DCELdebug.drawEFC(this,he);
			}

	    }
	    
	    // if not leaving first edge in place, then we
	    //    rotate so gamma is on positive y-axis
	    if (!fixFirst && gamma!=null) {
	    	try {
	    		double gammaarg=getVertCenter(gamma).arg();
	    		Complex rot=new Complex(0,Math.PI/2.0-gammaarg).exp();
	    		
	    		if (Math.abs(gammaarg-Math.PI/2.0)>.001) {
	    			for (int v=1;v<=vertCount;v++) 
	    				vertices[v].center=vertices[v].center.times(rot);
	    			if (redChain!=null) {
	    				RedEdge rtrace=redChain;
	    				do {
	    					rtrace.setCenter(rtrace.getCenter().times(rot));
	    					rtrace=rtrace.nextRed;
	    				} while (rtrace!=redChain);
	    			}
	    		}
	    	} catch (Exception ex) {}
	    			
	    }

		updatePairMob();
	    return count;
	}
	
	/** 
	 * Recompute centers of circles via 'layoutOrder' as usual,
	 * but if 'v' is valid vertex, then report its placements,
	 * which may be more than one. Option to recompute first 
	 * face or leave it in current location. Option to apply 
	 * usual alpha/gamma normalization. Option to use schwarzian
	 * instead of radii.
	 * Note: this command modifies the recorded centers.
	 * @param v int, vert index (<=0 for none)
	 * @param place_first boolean, if true place the first face
	 * @param norm boolean, true then do usual alpha/gamma normalization
	 * @param useSchw boolean, use schwarzians to propogate, not radii
	 * @return int, 0 on error 
	*/
	public int layoutReport(int v,
			boolean place_first,boolean norm,boolean useSchw) {
	    int count=1;
		Vertex V=null;
	    HalfEdge firsthe=layoutOrder.get(0);
	    boolean debug=false;
	    int prev_g=-1;
	    TriAspect gtri=null;
	    CircleSimple cs=null;
	    
		PointLink centlist = new PointLink();
		if (v > 0 && v <= vertCount)
			V=vertices[v];
		
		if (place_first) { 
			placeFirstEdge(firsthe);
		    cs=d_compOppCenter(firsthe);
		    setCent4Edge(firsthe.prev, cs.center);
			if (useSchw) {
				gtri=new TriAspect(this,firsthe.face);
			    prev_g=firsthe.face.faceIndx;
			}
		    
			if (debug) {// debug=true;
				DCELdebug.drawEFC(this,firsthe);
			}
			
			if (V!=null) { // first face may involve 'v'
				int[] verts=firsthe.face.getVerts();
				HalfEdge vhe=null;
				if (v==verts[0])
					vhe=firsthe;
				else if (v==verts[1])
					vhe=firsthe.next;
				else if (v==verts[2])
					vhe=firsthe.next.next;
				centlist.add(getVertCenter(vhe));
			}
		}
	    
	    // now layout face-by-face
		Iterator<HalfEdge> hit=layoutOrder.iterator();
		hit.next(); // toss first, already handled
	    TriAspect ftri=null;
	    while (hit.hasNext()) {
	    	HalfEdge he=hit.next();
	    	
	    	
	    	if (!useSchw) {
	    		cs=d_compOppCenter(he);
	    		setCent4Edge(he.prev,cs.center);
	    	}
	    	else {
	    		if (he.twin.face.faceIndx==prev_g)
	    			ftri=gtri; // reuse this data
	    		else
	    			ftri=new TriAspect(this,he.twin.face);
	    		gtri=new TriAspect(this,he.face);
	    		prev_g=gtri.faceIndx;
	    		int ans=-1;
	    		ans= workshops.LayoutShop.schwPropogate(ftri,gtri,he.twin,
	    				he.getSchwarzian(),1);
		    	if (V!=null && V==he.next.next.origin) // add new location
		    		centlist.add(getVertCenter(he.next.next));
	    	
	    		// get new data
	    		if (ans>=0) {
	    			gtri.data2pdcel(this);
	    			count++;

	    			if (debug) {// debug=true;
	    				DCELdebug.drawEFC(this,he);
	    			}
	    			
// debugging	    			
//	    			int v=he.prev.origin.vertIndx;
//		    		System.out.println("v="+v+" to z="+gtri.getCenter(
//	    				gtri.vertIndex(v)));
		    		
	    		}
	    	}
	    } // end of while

	    if (norm) {
	    	Mobius mob=new Mobius();
	    	Complex g=getVertCenter(gamma);
	    	if (place_first) { // just need to rotate
	    		double ang=-g.arg()+Math.PI/2.0;
	    		mob=Mobius.rotation(ang/Math.PI);
    			p.rotate(ang); // usual normalization
	    	}
	    	else {
	    		Complex a=getVertCenter(alpha);
	    		mob=norm_any_pack(this,a,g);
	    	}
	    	updatePairMob();
	    	
	    	// adjust 'centlist'
	    	if (centlist!=null && centlist.size()>0) {
	    		CirclePack.cpb.msg("Layout locations for vertex "+v);
	    		for (int j=0;j<centlist.size();j++) {
	    			Complex z=mob.apply(centlist.get(j));
	    			CirclePack.cpb.msg("  " + z.x + " " + z.y + "i ;");
	    		}
	    	}
	    }
	    return count;
	}

	/** 
	 * Draw an edge-pairing boundary segment.
	 * @param n int, index of side-pair (indices start at 1)
	 * @param do_label boolean, label also?
	 * @param do_circle boolean, circles also?
	 * @param ecol Color
	 * @param int thickness to draw
	 * @return 1
	 */
	public int d_draw_bdry_seg(int n,boolean do_label,boolean do_circle,
			Color ecol,int thickness) {
		SideData epair=null;
		
// debugging		
		boolean debug=false; // debug=true;
	  
		if (pairLink==null || n<1 || n>pairLink.size() 
				|| (epair=(SideData)pairLink.get(n))==null
				|| epair.startEdge==null)  // epair.startEdge.hashCode();epair.startEdge.nextRed.hashCode();
			return 0;
		int old_thickness=p.cpDrawing.getLineThickness();

		RedEdge rtrace=epair.startEdge;
		Complex w_cent=new Complex(getVertCenter(rtrace.myEdge));
		double rad_w=getVertRadius(rtrace.myEdge);
		if (do_label) // label first circle with side indx
			p.cpDrawing.drawIndex(w_cent,n,1);
		DispFlags dflags=new DispFlags(""); // System.out.println("v_indx="+v_indx+", wyd_w center.x "+wyd_w.center.x+" and hash "+wyd_w.hashCode());
		if (do_circle) { // handle draw/label for first circle
			int w_indx=rtrace.myEdge.origin.vertIndx;
			if (do_label) { 
				dflags.label=true;
				dflags.setLabel(Integer.toString(w_indx));
			}
			p.cpDrawing.drawCircle(w_cent,rad_w,dflags);
		}
		Complex v_cent=null;
		do {
			rtrace=rtrace.nextRed;
			v_cent=w_cent;
			w_cent=new Complex(getVertCenter(rtrace.myEdge));
			p.cpDrawing.setLineThickness(thickness);
			int w_indx=rtrace.myEdge.origin.vertIndx;
			DispFlags df=new DispFlags(null);
			df.setColor(ecol);
			p.cpDrawing.drawEdge(v_cent,w_cent,df);
			
			if (debug) { // debug=true;
				p.cpDrawing.rePaintAll();
			}
			
			if (do_circle) { 
				if (do_label) { 
					dflags.label=true;
					dflags.setLabel(Integer.toString(w_indx));
				}
				p.cpDrawing.setLineThickness(old_thickness);
				p.cpDrawing.drawCircle(w_cent,getVertRadius(rtrace.myEdge),dflags);
				p.cpDrawing.setLineThickness(thickness);
			}
	    } while (rtrace!=epair.endEdge.nextRed);
		p.cpDrawing.setLineThickness(old_thickness);
		return 1;
	}

	/**
	 * Flip the specified edges. In a triangulation, an interior edge is
	 * shared by two faces. To "flip" the edge means to remove it and
	 * replace it with the other diagonal in the union of those faces. The
	 * number of faces, edges, and vertices is not changed, but we are out
	 * of sync with parent 'p'.  
	 * Note: calling routine must ensure there are no repeats in 'flippers'.
	 * @param flippers ArrayList<HalfEdge>, edges to be flipped
	 * @return int count, -count if some redchain was disrupted
	 */
	public int flipEdges(ArrayList<HalfEdge> flippers) {
		int count=0;
		boolean redprob=false;
		Iterator<HalfEdge> lst=flippers.iterator();
		while (lst.hasNext()) {
			HalfEdge he=lst.next();

			if (!isBdryEdge(he)) { // if not bdry
				if (he.myRedEdge!=null) // in redchain
					redprob=true;
				DcelFace leftf=he.face;
				DcelFace rightf=he.twin.face;
				Vertex leftv=he.next.next.origin;
				Vertex rightv=he.twin.next.next.origin;
				
				// save some info for later
				HalfEdge hn=he.next;
				HalfEdge hp=he.prev;
				HalfEdge twn=he.twin.next;
				HalfEdge twp=he.twin.prev;
				
				// have to make sure ends don't have old 'halfedge's
				if (he.origin.halfedge==he)
					he.origin.halfedge=he.prev.twin; // cclw spoke
				if (he.twin.origin.halfedge==he.twin)
					he.twin.origin.halfedge=he.twin.prev.twin; // cclw spoke
				
				// fix he and its twin
				he.origin=rightv;
				he.twin.origin=leftv;
				he.next=hp;
				he.prev=twn;
				he.twin.next=twp;
				he.twin.prev=hn;
				he.face=rightf;
				he.twin.face=leftf;
				
				hn.next=he.twin;
				hn.prev=twp;
				hp.next=twn;
				hp.prev=he;
				twn.prev=hp;
				twn.next=he;
				twp.next=hn;
				twp.prev=he.twin;
				
				count++;
			}
		} // end of while

		if (redprob)
			return -count;
		return count;
	}
	
	/**
 	 * NEEDED FOR CIRCLEPACK
	 * "local refine" is a process for adding additional vertices
	 * as barycenters and flipping edges to get a finer circle
	 * packing near the designated vertices.
	 * @param arrayV ArrayList<Vertex>
	 * @return int count
	 */
	public int localRefine(NodeLink vlist) {
		
		// to avoid redundant listing
		int []vhits=new int[vertCount+1];
		Iterator<Integer> vit=vlist.iterator();
		while (vit.hasNext())
			vhits[vit.next()]=1;
		
		// make up 'Vertex' array
		ArrayList<Vertex> arrayV=new ArrayList<Vertex>();
		for (int n=1;n<=vertCount;n++)
			if (vhits[n]==1) 
				arrayV.add(vertices[n]);
		
		return localRefine(arrayV);
	}
	
	/**
	 * "local refine" is a process for adding additional vertices
	 * as barycenters and flipping edges to get a finer circle
	 * packing near the designated vertices.
	 * @param arrayV ArrayList<Vertex>
	 * @return int count
	 */
	public int localRefine(ArrayList<Vertex> arrayV) {
		ArrayList<HalfEdge> arrayE=new ArrayList<HalfEdge>();
		ArrayList<DcelFace> arrayF=new ArrayList<DcelFace>();
		
		// create array of existing edges so we can add to it
		ArrayList<HalfEdge> tmpedges=new ArrayList<HalfEdge>();
		tmpedges.add(null);
		for (int e=1;e<=edgeCount;e++) {
			tmpedges.add(edges[e]);
		}
		
		// to avoid 'Vertex' repeats
		int []vhits=new int[vertCount+1];
		Iterator<Vertex> ait=arrayV.iterator();
		while (ait.hasNext()) 
			vhits[ait.next().vertIndx]=1;
		
		// gather all (non ideal) faces and non-repeating edges
		for (int v=1;v<=vertCount;v++) {
			if (vhits[v]==1) {
				Vertex vert=vertices[v];
				ArrayList<DcelFace> myfaceflower=vert.getFaceFlower();
				Iterator<DcelFace> fit=myfaceflower.iterator();
				while (fit.hasNext()) {
					DcelFace face=fit.next();
					if (face.faceIndx>0) // omit ideal faces
						arrayF.add(face);
				}
				HalfLink eflower=vert.getEdgeFlower();
				Iterator<HalfEdge> eit=eflower.iterator();
				while (eit.hasNext()) {
					HalfEdge he=eit.next();
					int w=he.twin.origin.vertIndx;
					if (v<w || vhits[w]==0) // this avoids repeats
						arrayE.add(he);
				}
			}
		}
		
		// add barycenters to faces  
		int n=RawManip.addBaryCents_raw(this,arrayF);
		if (n<=0) {
			CirclePack.cpb.errMsg("didn't add barycenters");
			return 0;
		}
		
		// first, sort arrayE into bdry and interior
		ArrayList<HalfEdge> arrayInt=new ArrayList<HalfEdge>();
		ArrayList<HalfEdge> arrayBdry=new ArrayList<HalfEdge>();
		Iterator<HalfEdge> heit=arrayE.iterator();
		while (heit.hasNext()) {
			HalfEdge he=heit.next();
			if (!isBdryEdge(he)) 
				arrayBdry.add(he);
			else 
				arrayInt.add(he);
		}
		
		// remove the bdry edges and their faces
		heit=arrayBdry.iterator();
		while (heit.hasNext()) {
			HalfEdge he=heit.next();
			if (he.twin.face.faceIndx<0) // twin is ideal face
				he=he.twin;
			DcelFace bface=he.face;
			HalfEdge pre=he.prev;
			HalfEdge post=he.next;
			HalfEdge twpre=he.twin.prev;
			HalfEdge twpost=he.twin.next;
			if (he.face.edge==he)
				he.face.edge=pre;
			pre.next=twpost;
			twpost.prev=pre;
			twpre.next=post;
			post.prev=twpre;
			twpost.face=bface;
			twpre.face=bface;
			
			he.origin.halfedge=pre.twin;
			he.twin.origin.halfedge=twpre.twin;
			twpre.origin.halfedge=twpost.twin;
			
			tmpedges.remove(he);
			tmpedges.remove(he.twin);
		}				

		// we flip the interior ones
		n=flipEdges(arrayInt);
		indexFaces(tmpedges,getBdryEdges());
		
		return n;
	}

	/**
	 * Form bouquet of the combinatorial flowers, eg., for writing or
	 * creating DCEL structure.
	 * @return int[][], null on error
	 */
	public int[][] getBouquet() {
		if (vertCount<=0 || vertices==null)
			return null;
		int [][]bouq=new int[vertCount+1][];
		
		for (int v=1;v<=vertCount;v++) {
			try{
				bouq[v]=vertices[v].getFlower(true);
			} catch (Exception ex) {
				System.err.println("getFlower fails for "+v);
			}
		}
		return bouq;
	}
	
	/**
	 * Directly count the distinct petals. (Don't recount first
	 * for closed flower)
	 * @param v int
	 * @return
	 */
	public int countPetals(int v) {
		int num=0;
		HalfEdge he=vertices[v].halfedge;
		do {
			he=he.prev.twin;
			num++;
		} while (he!=vertices[v].halfedge);
		return num;
	}

	/**
	 * Count the number of non-ideal faces at 'vert'
	 * @param vert Vertex
	 * @return int
	 */
	public int countFaces(Vertex vert) {
		HalfEdge he=vert.halfedge;
		int count=0;
		if (he.face!=null && he.twin.face.faceIndx<0)
			count--;
		do {
			he=he.prev.twin;
			count++;
		} while (he!=vert.halfedge && 
				(he.twin.face==null || he.twin.face.faceIndx>=0));
		return count;
	}
	

	/**
	 * Return count of bdry verts from v1 to v2 (inclusive) if v1/v2 are
	 * on the same bdry component; otherwise 0.
	 * @param v1 int
	 * @param v2 int
	 * @return int
	 */
	public int verts_share_bdry(int v1,int v2) {
		if (v1<1 || v1>vertCount || v2<1 || v2>vertCount
			|| !isBdry(v1) || !isBdry(v2))
			return 0;
		int count=1;
		HalfEdge he=vertices[v2].halfedge.twin.next;
		if (v1==v2)
			return he.face.getNum();
		int safety=he.face.getNum()+1;
		do {
			count++;
			he=he.next;
			safety--;
		} while (he.origin.vertIndx!=v1 && safety>0);
		if (safety==0) // not on same bdry segment
			return 0;
		return count;
	}
	
	/**
	 * Based on current 'bdryFaces', find all bdry edges
	 * @return ArrayList<HalfEdge>
	 */
	public ArrayList<HalfEdge> getBdryEdges() {
		ArrayList<HalfEdge> bdryedges=new ArrayList<HalfEdge>();
		for (int k=1;k<=idealFaceCount;k++) {
			HalfEdge he=idealFaces[k].edge;
			HalfEdge nxtedge=he;
			do {
				bdryedges.add(nxtedge);
				nxtedge=nxtedge.next;
			} while (nxtedge!=he);
		}
		return bdryedges;
	}

	/**
	 * Given directed dual (f,g) between faces, return 
	 * halfedge (v,w) which is cclw to (f,g); so f is 
	 * on the left of (v,w), g is on the right.
	 * @param dedge EdgeSimple
	 * @return HalfEdge, null on failure
	 */
	public HalfEdge dualEdge_to_halfedge(EdgeSimple dedge) {
		int f=dedge.v;
		int g=dedge.w;
		HalfEdge edge=faces[f].edge;
		do {
			if (edge.twin.face.faceIndx==g) 
				return edge;
			edge=edge.next;	
		} while (edge!=faces[f].edge);
		
		// reaching here, found edge with g across
		return null;
	}
	
	/**
	 * Given HalfEdge (v,w), return ordered ends, g then f,
	 * of dual edge (f,g). CAUTION: f is to left of (v,w), g 
	 * to right, so result goes from right to left across (v,w).
	 * Return null if f an ideal face. If (v,w) is a oriented 
	 * bdry edge (red w/o 'twinRed') then go from point on 
	 * edge (v,w) to center of f. Else if (v,w) is red
	 * with 'twinRed', then compute location g would have if it
	 * shared edge (v,w) with f and go from g to f. 
	 * @param he HalfEdge
	 * @return Complex[2] or null
	 */
	public Complex[] getDualEdgeEnds(HalfEdge he) {
		Complex[] pts=new Complex[2];
		DcelFace f=he.face;
		DcelFace g=he.twin.face;
		if (f.faceIndx<0) 
			return null;
		
		pts[1]=getFaceCenter(f).center;
		
		// he is normal interior edge
		if (he.myRedEdge==null) {
			pts[0]=getFaceCenter(g).center;
			return pts;
		}

		// for red edge, may be bdry or twinned
		CircleSimple cs1=getCircleSimple(he);
		CircleSimple cs2=getCircleSimple(he.next);
		if (he.myRedEdge.twinRed==null) { // bdry edge
			pts[0]=CommonMath.genTangPoint(cs1, cs2,p.hes);
			return pts;
		}
		
		// he is twinned red edge; recompute opp center of g
		//   using centers radii of shared edge.
		double r3=getVertRadius(he.twin.next.next);
		CircleSimple cs3=CommonMath.comp_any_center(cs1,cs2,r3,
				he.twin.getSchwarzian(),he.twin.next.getSchwarzian(),
				he.twin.next.next.getSchwarzian(),p.hes);
		pts[0]=CommonMath.circle3Incircle(cs1, cs2, cs3,p.hes).center;
		
		return pts;
	}

	/**
 	 * NEEDED FOR CIRCLEPACK
	 * Dual graph store in 'EdgeLink'. This is linked list of 
	 * 'EdgeSimple' objects, which are just pairs {f,g} of 
	 * indices of faces sharing an edge.
	 * @param ideal boolean; if true, include edges to ideal faces.
	 * @return Graphlink, null on error
	 */
	public GraphLink getDualEdges(boolean ideal) {
		GraphLink glink=new GraphLink();
		for (int f=1;f<=faceCount;f++) {
			int fdx=faces[f].faceIndx;
			if (fdx<0 && !ideal)
				continue;
			fdx=Math.abs(faces[f].faceIndx);
			HalfLink edgs=faces[f].getEdges();
			Iterator<HalfEdge> eit=edgs.iterator();
			while (eit.hasNext()) {
				HalfEdge he=eit.next();
				int gdx=he.twin.face.faceIndx;
				if (gdx<0 && !ideal)
					continue;
				gdx=Math.abs(he.twin.face.faceIndx);
				if (gdx>fdx)
					glink.add(new EdgeSimple(fdx,gdx));
			}
		}
		return glink;
	}

	/**
	 * Get angle in 'edge.face' at 'edge.origin' using current
	 * radii.
	 * @param edge HalfEdge
	 * @return double, 0.0 if bdry edge
	 */
	public double getEdgeAngle(HalfEdge edge) {
		return getEdgeAngle(edge,getVertRadius(edge));
	}

	/**
	 * Get angle in 'edge.face' at 'edge.origin' assuming origin
	 * radius is 'r'. If 'r' is <= 0, then use current recorded radius.
	 * @param edge HalfEdge
	 * @param r double, possibly <=0
	 * @return double, 0.0 if bdry edge
	 */
	public double getEdgeAngle(HalfEdge edge,double r) {
		if (edge.face==null || edge.face.faceIndx<0) {
			CirclePack.cpb.errMsg("this is a boundary edge, "+edge.toString());
			return 0.0;
		}
		double r0=r;
		if (r0<=0)
			r0=getVertRadius(edge);
		double r1=getVertRadius(edge.next);
		double r2=getVertRadius(edge.prev);
		double ivd0=edge.getInvDist();
		double ivd1=edge.next.getInvDist();
		double ivd2=edge.prev.getInvDist();
		return CommonMath.get_face_angle(r0, r1, r2, ivd0, ivd1, ivd2, p.hes);
	}
	
	/**
	 * Get the angle sum at 'vert' using radius 'rad'.
	 * Note: if 'rad' is <= 0, then computation uses current 
	 * stored radius.
	 * @param vert Vertex
	 * @param rad double, possibly <= 0
	 * @return double
	 */
	public double getVertAngSum(Vertex vert,double rad) {
		double angsum=0.0;
		HalfEdge he=vert.halfedge;
		
		do {
			
// debugging
			if (he.face==null)
				throw new CombException(" angle sum: face null for "+he);
			
			if (he.face.faceIndx>0)
				angsum+=getEdgeAngle(he,rad);
			he=he.prev.twin;
		} while (he!=vert.halfedge);
		return angsum;
	}
	
	/**
	 * Get the angle sum at 'vert' using current radii 
	 * stored for edges, face-by-face.
	 * @param vert Vertex
	 * @return double
	 */
	public double getVertAngSum(Vertex vert) {
		double angsum=0.0;
		HalfEdge he=vert.halfedge;
		do {
			if (he.face.faceIndx>0)
				angsum+=getEdgeAngle(he);
			he=he.prev.twin;
		} while (he!=vert.halfedge);
		return angsum;
	}
	
	/**
	 * Find the official rad/cent for the origin of the 
	 * given 'HalfEdge'. If the origin is 'RedVertex', 
	 * then look clw for first 'RedEdge'. If normal 
	 * 'Vertex', then data is stored in 'PackData.vData'. 
	 * @return CircleSimple
	 */
	public CircleSimple getVertData(HalfEdge edge) {
		// is itself a 'RedEdge'?
		if (edge.myRedEdge!=null)
			return edge.myRedEdge.getData();
		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'?
		if (!vert.redFlag) {
			int v=vert.vertIndx;
			return new CircleSimple(p.packDCEL.vertices[v].center,
					p.packDCEL.vertices[v].rad);
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) 
				return he.myRedEdge.getData();
		} while (he!=edge);
		return null;
	}
	
	/**
	 * Get the radius of 'edge.origin' appropriate to this 'edge';
	 * e.g., it may be stored with nearest clw red edge. 
	 * Get its internal form (i.e., x-rad for hyp case).
	 * @param edge HalfEdge
	 * @return double
	 */
	public double getVertRadius(HalfEdge edge) {
		// is itself a 'RedEdge'? 
		if (edge.myRedEdge!=null) {
			return edge.myRedEdge.getRadius();
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'? 
		if (!vert.redFlag) {
			return vert.rad;
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) {
				return he.myRedEdge.getRadius();
			}
		} while (he!=edge);
		throw new DCELException("didn't find any 'RedEdge' for this 'Vertex'");
	}
	
	/**
	 * Get appropriate center, perhaps in nearest clw red edge.
	 * @param edge HalfEdge
	 * @return new Complex
	 */
	public Complex getVertCenter(HalfEdge edge) {
		// is itself a 'RedEdge'? Note, also set in 'PackData'
		if (edge.myRedEdge!=null) {
			return edge.myRedEdge.getCenter();
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'? set in 'PackData.vData'
		if (!vert.redFlag) {
			return new Complex(vert.center);
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) {
				return he.myRedEdge.getCenter();
			}
		} while (he!=edge);
		throw new DCELException("didn't find any 'RedEdge' for this 'Vertex'");
	}
	
	public CircleSimple getCircleSimple(HalfEdge he) {
		return new CircleSimple(getVertCenter(he),getVertRadius(he));
	}
	
	/**
	 * Set center for v in all locations and in any associated 
	 * 'RedEdges'. (Compare with 'setCent4Edge' which only set's 
	 * the value associated with one edge.)
	 * @param v int
	 * @param z Complex
	 */
	public void setVertCenter(int v, Complex z) {
		if (p.hes<0) { // check is appropriate for disc
			double absz=z.abs();
			if (absz>1.000000001) // outside disc?
				z=z.divide(absz+.00000001); // pull in
		}
		Vertex vert=vertices[v];
		vert.center=new Complex(z);
		HalfEdge he=vert.halfedge;
		do {
			if (he.myRedEdge!=null)
				he.myRedEdge.setCenter(new Complex(z));
			he=he.prev.twin;
		} while (he!=vert.halfedge);
	}
	
	/**
	 * Set cent/rad for 'origin' and appropriate 'RedEdge's 
	 * (if a 'RedVertex'). Data for a vert may differ in 
	 * different 'RedEdge's having that vert. 
	 * @param edge HalfEdge
	 * @param cs CircleSimple
	 */
	public void setCircle4Edge(HalfEdge edge,CircleSimple cs) {
		if (p.hes<0) { // check if appropriate for disc
			double absz=cs.center.abs();
			if (absz>1.000000001) // outside disc?
				cs.center=cs.center.divide(absz+.00000001); // pull in
		}

		Vertex vert=edge.origin;
		vert.center=new Complex(cs.center);
		vert.rad=cs.rad;

		// a normal 'Vertex'? 
		if (!vert.redFlag) {
			return;
		}

		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;   // he=edge;
		while (he.myRedEdge==null) {
			he=he.twin.next;
		}
		he.myRedEdge.setCenter(cs.center);
		he.myRedEdge.setRadius(cs.rad);
		return;
	}
	
	/**
	 * Set center for origin v of given 'edge'. If v is 
	 * a 'RedVertex', then v's center is stored in first 
	 * clw red edge; it may differ in other red edges with
	 * origin v and may differ from Vertex.origin. 
	 * (See 'setVertCenter', to set center in all locations.)
	 * @param edge HalfEdge
	 * @param z Complex
	 */
	public void setCent4Edge(HalfEdge edge,Complex z) {
		if (p.hes<0) { // check if appropriate for disc
			double absz=z.abs();
			if (absz>1.000000001) // outside disc?
				z=z.divide(absz+.00000001); // pull in
		}

		Vertex vert=edge.origin;
		vert.center=new Complex(z);

		// a normal 'Vertex'? 
		if (!vert.redFlag) {
			return;
		}

		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;   // he=edge;
		while (he.myRedEdge==null) {
			he=he.twin.next;
		}
		he.myRedEdge.setCenter(z);
		return;
	}

	/**
	 * Set vert's radius (hyp: in its internal x-rad form) 
	 * in all locations; i.e., in 'Vertex' and in red edge
	 * for any with this origin. (Compare with 'setRad4Edge' 
	 * which only sets it for one associated red edge.)
	 * @param v int
	 * @param rad double
	 */
	public void setVertRadii(int v,double rad) {
		Vertex vert=vertices[v];
		vert.rad=rad;
		if (vert.redFlag) {
			HalfEdge he=vert.halfedge;
			do {
				if (he.myRedEdge!=null)
					he.myRedEdge.setRadius(rad);
				he=he.prev.twin;
			} while (he!=vert.halfedge);
		}
	}
	
	/**
	 * Set the radius (in its internal form x-rad for hyp case)
	 * in 'Vertex' and nearest clw red edge, if there is one.
	 * (Compare with 'setVertRadii' which sets this radius in all
	 * occurrences of its origin vertex.)
	 * @param edge HalfEdge
	 * @param rad double
	 */
	public void setRad4Edge(HalfEdge edge,double rad) {
		Vertex vert=edge.origin;
		vert.rad=rad;
		
		// a normal 'Vertex'?
		if (!vert.redFlag) {
			return;
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		while (he.myRedEdge==null) {
			he=he.twin.next;
		}
		he.myRedEdge.setRadius(rad);
		return;
	}
	
	/**
	 * Multiply all radii stored for 'v' by the given 
	 * positive factor. Initially, intended for eucl 
	 * geom only as part of affine packing. (3/2021)
	 * @param v int
	 * @param factor double
	 */
	public void setRadii_by_factor(int v,double factor) {
		Vertex vert=vertices[v];
		if (!vert.redFlag) { // only one storage location
			double rad=p.getRadius(v);
			p.setRadius(v, rad*factor);
			return;
		}
		RedEdge rtrace=redChain;
		do {
			rtrace.setRadius(rtrace.getRadius()*factor);
			rtrace=rtrace.nextRed;
		} while(rtrace!=redChain);
	}
	
	/**
	 * When new vertices are created, e.g., from "add_cir",
	 * we accumulate associated edges in order. They should
	 * already have radii, so we set an approximate center by
	 * using it along with data from the edge.
	 * @param hlink HalfLink
	 * @return count
	 */
	public int addedVertData(HalfLink hlink) {
		int count=0;
		Iterator<HalfEdge> his=hlink.iterator();
		while (his.hasNext()) {
			HalfEdge he=his.next();
			CircleSimple cs=CommonMath.naiveData(he,p.hes);
			setCent4Edge(he.prev,cs.center);
			setRad4Edge(he.prev,cs.rad);
		}
		return count;
	}
	
	/**
	 * set center and radius (in its internal form);
	 * this sets data only for the given edge, so for
	 * 'RedVertex' it stores in the appropriate 'RedEdge'.
	 * @param edge HalfEdge
	 * @param cS CircleSimple
	 */
	public void setVertData(HalfEdge edge,CircleSimple cS) {
		setCent4Edge(edge,cS.center);
		setRad4Edge(edge,cS.rad);
	}
	
	/**
	 * Determine if vertex is bdry; if no, return null,
	 * else return first 'HalfEdge', that is, one with 
	 * 'twin.face' being an ideal face. (This normally is 
	 * 'v.halfedge', but calling routine can use return
	 * info to reset 'v.halfedge' if desired.) 
	 * @param v Vertex
	 * @return HalfEdge, null if not bdry vertex
	 */
	public HalfEdge vertIsBdry(Vertex v) {
		HalfEdge nxtedge=v.halfedge;
		do {
			if (nxtedge.twin.face!=null && nxtedge.twin.face.faceIndx<0) // ideal face?
				return nxtedge;
			nxtedge=nxtedge.prev.twin;
		} while(nxtedge!=v.halfedge);
		return null;
	}
	
	/**
	 * Is this a boundary vertex? Depends on bdry edges
	 * being identified with 'faceIndx'<0.
	 * @param v int
	 * @return boolean
	 */
	public boolean isBdry(int v) {
		HalfEdge he=vertices[v].halfedge;
		if (he.twin.face!=null && he.twin.face.faceIndx<0)
			return true;
		return false;
	}
	
	/**
	 * boundary edge? Depends on face existing and
	 * having negative index, indicating ideal face.
	 * @param he HalfEdge
	 * @return boolean
	 */
	public boolean isBdryEdge(HalfEdge he) {
		if ((he.face!=null && he.face.faceIndx<0) ||
				(he.twin.face!=null && he.twin.face.faceIndx<0)) {
			return true;
		}
		return false;
	}

	/**
	 * Find the 'HalfEdge' for edge <v.w>
	 * @param es EdgeSimple
	 * @return HalfEdge or null on failure
	 */
	public HalfEdge findHalfEdge(EdgeSimple es) {
		if (es.v<=0 || es.w<=0)
			return null;
		try {
			Vertex vert=vertices[es.v];
			HalfEdge trace=vert.halfedge;
			do {
				if (trace.twin.origin.vertIndx==es.w) 
					return trace;
				trace=trace.prev.twin;
			} while (trace!=vert.halfedge);
		} catch (Exception ex) {}
		return null;
	}

	/**
	 * Find the 'HalfEdge' for edge <v.w>
	 * @param v int
	 * @param v int
	 * @return HalfEdge or null on failure
	 */
	public HalfEdge findHalfEdge(int v,int w) {
		return findHalfEdge(new EdgeSimple(v,w));
	}

	/**
	 * Find the 'HalfEdge' based on 'vertIndx's of
	 * the given 'he' (typically from a related 
	 * DCEL structure).
	 * @param he HalfEdge
	 * @return HalfEdge, null on failure to find
	 */
	public HalfEdge findHalfEdge(HalfEdge he) {
		EdgeSimple es=new EdgeSimple(he);
		return findHalfEdge(es);
	}
	
	/**
	 * Find 'dcel.Face' for given edge <v,w> 
	 * @param v int
	 * @param w int
	 * @return dcel.Face or null on failure
	 */
	public combinatorics.komplex.DcelFace findFace(int v, int w) {
		HalfEdge he=findHalfEdge(new EdgeSimple(v,w));
		if (he!=null)
			return he.face;
		return null;
	}

	public int layoutFactory(HalfLink heTree,
			boolean fix,boolean placeFirst,boolean useSchw) {
		return layoutFactory(null,heTree,null,null,fix,placeFirst,useSchw,-1.0);
	}

	/** 
	 * Recompute, draw, and/or post circles and/or faces along a
	 * specified HalfLink. Typical call has 'heTree' null, in 
	 * which case we redo the full 'layoutTree' using averaging
	 * to compute centers. For faces we further draw and/or post 
	 * the faces in 'stragglers'.
	 * @param pF PostFactory
	 * @param heTree HalfLink, may be null
	 * @param faceFlags DispFlags, may be null
	 * @param circFlags DispFlags, may be null
	 * @param fix boolean, store the new centers
	 * @param placeFirst boolean: true: place anew; false, assume 2 verts in place
	 * @param useSchw boolean: true use schwarzians than radii
	 * @param tx double, thickness, only used for postscript output
	 * @return int count 
	 */
	public int layoutFactory(PostFactory pF,HalfLink heTree,
			DispFlags faceFlags,DispFlags circFlags,boolean fix,
			boolean placeFirst,boolean useSchw,double tx) {
		int count=0; 

		// do we need to compute layout using 'layoutOrder'?
		boolean doLayout=false; 
		boolean debug=false;
		if (debug) { // debug=true;
			DCELdebug.drawRedChain(p,redChain);
			DCELdebug.printRedChain(redChain);
		}
		
		// not drawing anything? this just serves to redo layout
		boolean faceDo=false;
		if (faceFlags!=null && faceFlags.draw)
			faceDo=true;
		boolean circDo=false;
		if (circFlags!=null && circFlags.draw)
			circDo=true;
		
		// default to full layout: may do layout first
		if (heTree==null || heTree.size()==0) {
			heTree=layoutOrder;
			if (fix) {
				doLayout=true;
				if (placeFirst)
					layoutPacking();
				else // use first face in current location 
					layoutPacking(layoutOrder.getFirst());
			}
			if (faceFlags!=null) {// pick up remaining faces
				heTree=new HalfLink();
				heTree.abutMore(layoutOrder);
				heTree.abutMore(stragglers);
			}
		}
		
		int[] myVerts=new int[3];
		double[] myRadii=new double[3];
		Complex[] myCenters=new Complex[3];
		
		// draw the first face and/or circles first
		HalfEdge he=null;
		he=heTree.getFirst();
		if (!doLayout) {
			if (placeFirst && fix) { // place in standard position, 0, imaginary axis 
				placeFirstEdge(he);
			}
			if (fix) { // assume 'he' ends are already in place
				setCent4Edge(he.prev,d_compOppCenter(he).center);
			}
		}
		
		TriAspect gtri=null;
		int prev_g=-1;
		if (useSchw) {
			gtri=new TriAspect(this,he.face);
			prev_g=he.face.faceIndx;
		}
			
		// Note, could be old data if we didn't recompute first face
		myCenters[0]=getVertCenter(he);
		myCenters[1]=getVertCenter(he.next);
		myCenters[2]=getVertCenter(he.next.next);
		myRadii[0]=getVertRadius(he);
		myRadii[1]=getVertRadius(he.next);
		myRadii[2]=getVertRadius(he.next.next);
			
		if (faceDo && pF==null) { // draw the first face
			if (!faceFlags.colorIsSet && 
					(faceFlags.fill || faceFlags.colBorder)) 
				faceFlags.setColor(p.getFaceColor(he.face.faceIndx));
			if (faceFlags.label)
				faceFlags.setLabel(Integer.toString(he.face.faceIndx));
			p.cpDrawing.drawFace(myCenters[0],myCenters[1],myCenters[2],
					myRadii[0],myRadii[1],myRadii[2],faceFlags);
			
			// may want to see them as laid out
			if (debug)  // debug=true;
				p.cpDrawing.rePaintAll();
		}  
			
		if (circDo && pF==null) { // also draw the circles for first face
			if (!circFlags.colorIsSet && 
					(circFlags.fill || circFlags.colBorder)) 
				circFlags.setColor(p.getCircleColor(he.next.next.origin.vertIndx));
			if (circFlags.label)
				circFlags.setLabel(Integer.toString(he.origin.vertIndx));
			p.cpDrawing.drawCircle(myCenters[0],myRadii[0],circFlags);
			if (circFlags.label)
				circFlags.setLabel(Integer.toString(he.next.origin.vertIndx));
			p.cpDrawing.drawCircle(myCenters[1],myRadii[1],circFlags);
			if (circFlags.label)
				circFlags.setLabel(Integer.toString(he.next.next.origin.vertIndx));
			p.cpDrawing.drawCircle(myCenters[2],myRadii[2],circFlags);
			
			if (debug) 
				p.cpDrawing.rePaintAll(); 
		}
		
		// TODO: if pF!=null, need to layout and post the first face and/or circles

		// now go through the rest of the tree
		Iterator<HalfEdge> hest = heTree.iterator();
		hest.next(); // ditch the first, already handled
		TriAspect ftri=null;
		while (hest.hasNext()) {
			he = hest.next();  // normally, this edge is already in place

			if (fix && !doLayout) {
				if (!useSchw) {
					CircleSimple cs=d_compOppCenter(he);
					setCent4Edge(he.prev,cs.center);
				}
				
				else {
					if (he.twin.face.faceIndx==prev_g)
						ftri=gtri; // reuse this data
					else
						ftri=new TriAspect(this,he.twin.face);
					gtri=new TriAspect(this,he.face);
					prev_g=gtri.faceIndx;
					int ans=-1;
					ans = workshops.LayoutShop.radPropogate(ftri,gtri,he.twin,
							getVertRadius(he.prev),1);
		    	
					// get new data
					if (ans>=0) {
						gtri.data2pdcel(this);
						count++;
					}
				}
			}
			
			if (faceDo || circDo || pF!=null) {
				
				// could be old data if we didn't recompute and store
				myCenters[0]=getVertCenter(he);
				myCenters[1]=getVertCenter(he.next);
				myCenters[2]=getVertCenter(he.next.next);
				myRadii[0]=getVertRadius(he);
				myRadii[1]=getVertRadius(he.next);
				myRadii[2]=getVertRadius(he.next.next);
				
				if (faceDo && pF==null) { // draw the faces
					if (!faceFlags.colorIsSet && 
							(faceFlags.fill || faceFlags.colBorder)) 
						faceFlags.setColor(p.getFaceColor(he.face.faceIndx));
					if (faceFlags.label)
						faceFlags.setLabel(Integer.toString(he.face.faceIndx));
					p.cpDrawing.drawFace(myCenters[0],myCenters[1],myCenters[2],
							myRadii[0],myRadii[1],myRadii[2],faceFlags);
					
					if (debug) 
						p.cpDrawing.rePaintAll();
				} 
				
				if (circDo && pF==null) { // also draw the circles
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder)) 
						circFlags.setColor(p.getCircleColor(he.next.next.origin.vertIndx));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(he.next.next.origin.vertIndx));
					p.cpDrawing.drawCircle(myCenters[2],myRadii[2],circFlags);
					
					if (debug) 
						p.cpDrawing.rePaintAll();
				}
				
				// postscript
				if (pF!=null) {
					if (p.hes>0) { // post routines don't know how to convert
						myCenters[0]=new Complex(p.cpDrawing.
							sphView.toApparentSph(myCenters[0]));
						myCenters[1]=new Complex(p.cpDrawing.
							sphView.toApparentSph(myCenters[1]));
						myCenters[2]=new Complex(p.cpDrawing.
							sphView.toApparentSph(myCenters[2]));
					}

					if (faceDo) { // also post the faces
					
						// set face/bdry colors
						Color fcolor=null;
						Color bcolor=null;
						if (faceFlags.fill) {  
							if (!faceFlags.colorIsSet) 
								fcolor=p.getFaceColor(he.face.faceIndx);
							if (faceFlags.colBorder)
								bcolor=fcolor;
						}
						if (faceFlags.draw) {
							if (faceFlags.colBorder)
								bcolor=p.getFaceColor(he.face.faceIndx);
							else 
								bcolor=ColorUtil.getFGColor();
						}
						pF.post_Poly(p.hes, myCenters, fcolor, bcolor, tx);

						if (faceFlags.label) { // label the face
							Complex z=getFaceCenter(faces[he.face.faceIndx]).center;
							if (p.hes>0) {
								z=p.cpDrawing.sphView.toApparentSph(z);
								if(Math.cos(z.x)>=0.0) {
									z=util.SphView.s_pt_to_visual_plane(z);
									pF.postIndex(z,he.face.faceIndx);
								}
							}
							else pF.postIndex(z,he.face.faceIndx);
						}
					}
					if (circDo) { // also post the circles
						if (!circFlags.fill) { // not filled
							if (circFlags.colBorder)
								pF.postColorCircle(p.hes,myCenters[2],myRadii[2],
									p.getCircleColor(he.next.next.origin.vertIndx),tx);
							else 
								pF.postCircle(p.hes,myCenters[2],myRadii[2],tx);
						} 
						else {
							Color ccOl=ColorUtil.getFGColor();
							if (!circFlags.colorIsSet)
								ccOl = p.getCircleColor(he.next.next.origin.vertIndx);
							if (circFlags.colBorder) {
								pF.postFilledColorCircle(p.hes,myCenters[2],
										myRadii[2],ccOl,ccOl,tx);
							}
							else 
								pF.postFilledCircle(p.hes,myCenters[2],
										myRadii[2],ccOl,tx);
						}
						if (circFlags.label) { // label the circle
							if (p.hes>0) {
								Complex z=p.cpDrawing.sphView.
									toApparentSph(myCenters[2]);
								if(Math.cos(z.x)>=0.0) {
									z=util.SphView.s_pt_to_visual_plane(z);
									pF.postIndex(z,myVerts[2]);
								}
							}
							else pF.postIndex(myCenters[2],myVerts[2]);
						}
					}
				}
			}
			count++;
		} // end of while through trees
//		p.cpDrawing.rePaintAll();
		return count;
	}
	
	/**
	 * Get the incircle of the triangle formed by three
	 * circles. In hyperbolic case, need radii since we
	 * use the generalized tangency points in the computation.
	 * @param c0 CircleSimple
	 * @param c1 CircleSimple
	 * @param c2 CircleSimple
	 * @param hes int
	 * @return CircleSimple
	 */
	public static CircleSimple getTriIncircle(CircleSimple c0,
		CircleSimple c1,CircleSimple c2,int hes) {
		
		// for eucl and spherical, only need centers
		if (hes==0)
			return EuclMath.eucl_tri_incircle(c0.center,c1.center,c2.center);
		if (hes>0) 
			return SphericalMath.sph_tri_incircle(c0.center,c1.center,c2.center);
		
		// hyp case; use generalized tangency points, so need radii also
		CircleSimple[] cS=new CircleSimple[3];
		cS[0]=c0;
		cS[1]=c1;
		cS[2]=c2;
		Complex[] pts=new Complex[3];
		for (int j=0;j<3;j++) 
			pts[j]=CommonMath.genTangPoint(cS[j],cS[(j+1)%3],hes);
		return HyperbolicMath.e_to_h_data(
				EuclMath.circle_3(pts[0], pts[1], pts[2]));
	}
	
	/**
	 * Find the incircle for the given face. For eucl
	 * and sph cases, just use 3 centers; for hyp case,
	 * use 3 generalized tangency points.
	 * @param face Face
	 * @return CircleSimple
	 */
	public CircleSimple getFaceIncircle(DcelFace face) {
		HalfEdge he=face.edge;
		CircleSimple c0=getVertData(he);
		CircleSimple c1=getVertData(he.next);
		CircleSimple c2=getVertData(he.next.next);
		return getTriIncircle(c0,c1,c2,p.hes);
	}

	/**
	 * Return center of incircle of face with given index.
	 * @param face int
	 * @return CircleSimple, null on error
	 */
	public CircleSimple getFaceCenter(DcelFace face) {
		if (face.faceIndx<0)
			return null;
		HalfEdge he=face.edge;
		
		if (p.hes < 0) { // hyperbolic
			CircleSimple[] cS=new CircleSimple[3];
			for (int j=0;j<3;j++) {
				cS[j]=new CircleSimple(p.packDCEL.getVertCenter(he),
						p.packDCEL.getVertRadius(he));
				he=he.next;
			}
			Complex[] pts=new Complex[3];
			for (int j=0;j<3;j++) {
				pts[j]=CommonMath.genTangPoint(cS[j],cS[(j+1)%3],p.hes);
			}
			CircleSimple theCircle=EuclMath.circle_3(pts[0], pts[1], pts[2]);
			theCircle=HyperbolicMath.e_to_h_data(theCircle);
			return theCircle;
		}
		
		Complex p0 = getVertCenter(he);
		Complex p1 = getVertCenter(he.next);
		Complex p2 = getVertCenter(he.next.next);
		CircleSimple sc;
		if (p.hes > 0)
			sc = SphericalMath.sph_tri_incircle(p0, p1, p2);
		else
			sc = EuclMath.eucl_tri_incircle(p0, p1, p2);
		return sc;
	}
	
	/**
	 * Return Face consisting of vertices {a,b,c} or 
	 * {a,c,b}, null on failure, eg. if 'this' face 
	 * is ideal.
	 * @param a int
	 * @param b int
	 * @param c int
	 * @return Face, null on failure
	 */
	public DcelFace whatFace(int a,int b,int c) {
		Vertex vert=vertices[a];
		HalfLink spokes=vert.getEdgeFlower();
		Iterator<HalfEdge> his=spokes.iterator();
		while (his.hasNext()) {
			HalfEdge he=his.next();
			DcelFace face=he.face;
			// not an ideal face?
			if (face!=null && face.faceIndx>0) { 
				int w=he.next.origin.vertIndx;
				int u=he.next.next.origin.vertIndx;
				if ((w==b && u==c) || (u==b && w==c))
					return face;
			}
		}
		return null;
	}

	/**
	 * Return open list of centers of corners of this face.
	 * (Generally will have 3 corners). The first corner
	 * is for the origin of 'face.edge'.
	 * @param face Face
	 * @return Complex[]
	 */
	public Complex[] getFaceCorners(DcelFace face) {
		HalfLink hlink=face.getEdges();
		if (hlink==null || hlink.size()==0)
			throw new CombException("face "+face+" has no edges");
		Complex[] corners=new Complex[hlink.size()];
		Iterator<HalfEdge> hits=hlink.iterator();
		int tick=0;
		while (hits.hasNext()) {
			HalfEdge he=hits.next();
			corners[tick++]=getVertCenter(he);
		}
		return corners;
	}
	
	/**
	 * Get open cclw list of face centers for the 
	 * interior faces around 'vert'.
	 * 
	 * TODO: Should lay this out to avoid ambiguity
	 * in multiply connected situation. 
	 * 
	 * @param vert Vertex
	 * @return Complex[]
	 */
	public Complex[] getDualFaceCorners(Vertex vert) {
		ArrayList<DcelFace> farray=vert.getFaceFlower();
		int num=farray.size();
		Complex[] corners=new Complex[num];
		Iterator<DcelFace> fits=farray.iterator();
		int tick=0;
		while (fits.hasNext()) {
			DcelFace face=fits.next();
			if (face.faceIndx>=0)
				corners[tick++]=getFaceCenter(face).center;
		}
		return corners;
	}

	/**
	 * Various checks on consistency of a bouquet:
	 *  * vertices match vertex count
	 *  * every edge is listed twice and only twice
	 *  * count the faces.
	 *  * check euler
	 *  
	 *  TODO: in progress
	 */
	public static int checkBouquet(int [][]bouquet) {
		int vcount=bouquet.length-1;
		for (int v=1;v<=vcount;v++) {
			
		}
		
		
		return -1;
	}
	
	/**
	 * Return the vertices forming the face starting at edge from
	 * v to w, one of its petals. 
	 * @param bouquet
	 * @param v int, vertex
	 * @param w int, petal
	 * @return int[], null on error
	 */
	public static int []getFace(int [][]bouquet,int v,int w) {
		int vcount=bouquet.length-1;
		if (v<1 || v> vcount) {
			return null;
		}
		int[] flower=bouquet[v];
		int indx_vw=CombDCEL.nghb(bouquet,v,w);
		if (indx_vw<0)
			return null;
		
		NodeLink nlink=new NodeLink();
		nlink.add(v);
		int nextv=w;
		int holdv=v;
		int safety=bouquet.length;
		while (nextv!=holdv && safety>0) {
			safety--;
			nlink.add(nextv);
			flower=bouquet[nextv];
			int num=flower.length;
			int indx=CombDCEL.nghb(bouquet,nextv,v);
			if (indx==0) // first and last repeat
				indx=num-2;
			else
				indx=(indx+num-1)%num;
			v=nextv;
			nextv=flower[indx];
		}
		if (safety==0)
			throw new CombException("loop crash in 'getFace'");
		
		int n=nlink.size();
		int []list=new int[n];
		for (int k=0;k<n;k++)
			list[k]=nlink.get(k);
		
		return list;
	}
	
	/**
	 * Find the complex "center" of given 'Face'. If the face has
	 * three vertices (typical), then return center of incircle of
	 * triangle formed by vertices. Else, return average of centers
	 * of vertices. 
	 * TODO: OBE
	 * @param face Face
	 * @return Complex, null on error
	 */
	public Complex faceOppCenter(DcelFace face) {
		int []verts=face.getVerts();
		if (verts.length==3) {
			Complex p0=p.getCenter(verts[0]);
			Complex p1=p.getCenter(verts[1]);
			Complex p2=p.getCenter(verts[2]);
			return CommonMath.tripleIncircle(p0,p1,p2,p.hes);
		}
		Complex accum=p.getCenter(verts[0]);
		for (int j=1;j<verts.length;j++)
			accum=accum.plus(p.getCenter(verts[j]));
		return accum.divide(verts.length);
	}
	
	/**
	 * Create a new packDCEL object, with p=null, whose orientation 
	 * is opposite to 'this'.
	 * @return PackDCEL
	 */
	public PackDCEL reverseOrientation() {
		int[][] bouq=CombDCEL.reverseOrientation(getBouquet()); 
	    return CombDCEL.extractDCEL(CombDCEL.getRawDCEL(bouq),null,alpha);
	}
	
	/**
	 * Swap vertices for 'v' and 'w'. This has minimal impact on the DCEL
	 * structure, eg., drawing order, etc., but calling routine may
	 * adjust various lists. Note that all the 'VData' info
	 * goes along, 'aim', 'rad', etc., so if you didn't want to swap
	 * this, the calling routine has to swap it back.
	 * @param v int
	 * @param w int
	 * @return 1, 0 on failure
	 */
	public int swapNodes(int v,int w) {
		if (v==w)
			return 0;
		triData=null; // outdated
		Vertex holdv=vertices[v];
		Vertex holdw=vertices[w];
		holdv.vertIndx=w;
		holdw.vertIndx=v;
		vertices[w]=holdv;
		vertices[v]=holdw;
		
		// swap 'VData' and all data with it, aims, rad, centers, etc.
		// fix up if there parent has TileData
		if (p.tileData != null && p.tileData.tileCount > 0) {
			for (int j = 1; j <= p.tileData.tileCount; j++) {
				Tile t = p.tileData.myTiles[j];
				if (t == null)
					continue;
				if (t.baryVert == w)
					t.baryVert = v;
				else if (t.baryVert == v)
					t.baryVert = w;
				for (int k = 0; k < t.vertCount; k++) {
					if (t.vert[k] == w)
						t.vert[k] = v;
					else if (t.vert[k] == v)
						t.vert[k] = w;
				}
			}
		}
		
		return 1;
	}
	
	/**
	 * Create the dual DCEL structure. 
	 * 
	 * If 'full' is false, don't include dual edges to ideal 
	 * faces; in particular, the dual of this dual will not be 
	 * the original.
	 * @param full boolean, false (default)
	 * TODO: what to do for 'full' true???
	 * @return PackDCEL
	 */
	public PackDCEL createDual(boolean full) {
		
		int [][]bouquet=new int[intFaceCount+1][];
		for (int f=1;f<=faceCount;f++) {
			DcelFace face=faces[f];
			int fi=Math.abs(face.faceIndx);
			ArrayList<Integer> flower=face.faceFlower();
			if (flower==null || flower.size()==0)
				throw new CombException("dcel faceFlower problem with 'faceIndx' "+fi);
			
			// if an ideal face is a neighbor, it is last
			if (flower.get(0)!=flower.get(flower.size()-1)) {
				if (!full)
					flower.remove(flower.size()-1); // remove it, leaving open list
				else {
					int iindx=flower.get(flower.size()-1);
					iindx=Math.abs(iindx); // change sign to positive
					flower.add(flower.get(0)); // and close the list up
				}
			}
			if (fi<=intFaceCount || full) {
				bouquet[fi]=new int[flower.size()];
				for (int i=0;i<flower.size();i++) {
					bouquet[fi][i]=flower.get(i);
				}
			}
		}
		
		PackDCEL qdcel = CombDCEL.getRawDCEL(bouquet,0);
		
		// TODO: OBE. vertices no longer hold center/rad data
/*		// set centers of the new vertices
		for (int f=1;f<=faceCount;f++) {
			Face face=faces[f];
			if (face.faceIndx>0) // ignore ideal faces
				qdcel.vertices[face.faceIndx]=faceOppCenter(face);
		}
*/
		
		return qdcel;
	}

	/**
	 * Reset all 'vutil' to zero. Some 'RawManip' routines,
	 * e.g., use 'vutil' to feed back reference indices. 
	 */
	public void zeroVUtil() {
		for (int v=1;v<=vertCount;v++) 
			vertices[v].vutil=0;
	}

	/**
	 * Reset all 'futil' to zero.
	 */
	public void zeroFUtil() {
		for (int f=1;f<=faceCount;f++)
			faces[f].futil=0;
	}
	
	/**
	 * Reset all 'eutil' to zero. 
	 */
	public void zeroEUtil() {
		for (int v=1;v<=vertCount;v++) {
			HalfEdge he=vertices[v].halfedge;
			do {
				he.eutil=0;
				he=he.prev.twin; // cclw
			} while (he!=vertices[v].halfedge);
		}
	}
	
	/**
	 * Update the side-pairing maps using updated rededge centers.
	 * @return 0 if 'redChain' or 'pairLink' doesn't exist or
	 * a computation fails.
	 */
	public int updatePairMob() {
		if (redChain==null || pairLink==null) 
			return 0;
			
		Iterator<SideData> dsis=pairLink.iterator();
		dsis.next(); // first is null
		while (dsis.hasNext()) {
			SideData dsdata=dsis.next();
			if (dsdata.set_sp_Mobius()==0)
				return 0;
		}
		return 1;
	}
	
	/**
	 * Set 'alpha' edge; its vert is normally placed at origin,
	 * should be interior. 'v'>0 indicates preference;
	 * if 'v'<=0 or 'v' not interior, try current 'alpha' if
	 * it's interior. Else look for first interior; nothing
	 * works, use current 'alpha' or choose 1.
	 * 'recomb' true means if we change 'alpha', call 
	 * 'fillInside' to adjust combinatorics. 
	 * Also, may need to change 'gamma'. (This also, 
	 * set 'p.alpha', 'p.gamma' if 'p' is not null.)
	 * @param v int
	 * @param nlink NodeLink, forbidden vertices
	 * @param recomb boolean
	 * @return 'v' 
	 */
	public int setAlpha(int v,NodeLink nlink,boolean recomb) {
		HalfEdge myTry=null; // current best option
		int alpIndex=0;
		if (alpha!=null) {
			alpIndex=alpha.origin.vertIndx;
			if (nlink!=null && nlink.containsV(alpIndex)>=0)
				alpha=null;
		}
		
		// if 'alpha' interior, not forbidden
		if (alpha!=null && !alpha.origin.isBdry())
			myTry=alpha;

		if (v>0 && v<=vertCount) {
			
			// if it's interior, not forbidden, use it
			if (!vertices[v].isBdry() && (nlink==null || nlink.containsV(v)<0))
				myTry=vertices[v].halfedge;
			
			// else, use alpha or get first non-forbidden interior
			if (myTry==null)
				for (int j=1;(j<=vertCount && (myTry==null));j++)
					if (!vertices[j].isBdry() && 
							(nlink!=null && nlink.containsV(j)<0))
						myTry=vertices[j].halfedge;
		}
		
		// else, no preference: use alpha or first non-forbidden interior
		else {
			if (myTry!=null) { // myTry should be 'alpha'
				if (gamma==myTry)
					gamma=myTry.twin;
				return myTry.origin.vertIndx;
			}
			if (nlink==null) { // 'myTry' first edge with int ends
				for (int j=1;(j<=vertCount && myTry==null);j++) {
					HalfEdge ahe=vertices[j].halfedge;
					if (!vertices[j].isBdry() && 
							!vertices[ahe.next.origin.vertIndx].isBdry())
						myTry=ahe;
				}
			}
			else { // get first interior with both ends not in 'nlink'
				for (int j=1;(j<=vertCount && myTry==null);j++) { 
				HalfEdge ahe=vertices[j].halfedge;
				if (!vertices[j].isBdry() && nlink.containsV(j)<0 &&
						nlink.containsV(ahe.next.origin.vertIndx)<0)
					myTry=vertices[j].halfedge;
				}
			}
		}

		// still no option?
		if (myTry==null) {
			
			// stick with current alpha
			if (alpha!=null)
				myTry=alpha;
			// desperation: just use vert 1
			else 
				myTry=vertices[1].halfedge;
		}
		
		// wrap up: did we change 'alpha'?
		if (myTry!=alpha) {
			alpha=myTry;
			if (p!=null) {
				if (recomb)  // adjust combinatorics
					CombDCEL.fillInside(p.packDCEL);
			}
		}
		
		if (gamma==alpha) {
			gamma=alpha.twin;
		}
		return myTry.origin.vertIndx;
	}
	
    /**
     * Set packing 'gamma' halfedge; it's vertex generally 
     * placed on y+ axis and can't be the origin of 'alpha'.
     * Default is 'gamma=alpha.twin'.
     * @param v int, preference or 0
     * @return 1 on success, 0 on failure
     */
    public int setGamma(int v) {
    	
    	// make sure 'alpha' is set
    	if (alpha==null)
    		setAlpha(0,null,false); // don't call 'fillInside'
        if (v<=0 || v>vertCount) {
        	if (gamma==null)
        		gamma=alpha.twin;
        	return 1;
        }
		Vertex vertex =vertices[v];
		if (vertex==alpha.origin) { 
			CirclePack.cpb.errMsg("'gamma' cannot be "+
					"set to have the 'alpha' edge origin");
			gamma=alpha.twin; // default
			return 0;
		}
		gamma=vertex.halfedge;
        return gamma.origin.vertIndx;
    } 
    
	/**
	 * Write this DCEL structure to a file.
	 * TODO: This is an early format, 4/2017, and should
	 * probably be rethought, but need it for 3D modeling work 
	 * now.
	 * @param filename
	 * @return 0 on failure
	 */
	public int writeDCEL(BufferedWriter fp) {
	    try {
	    	
	    	// vertices
	    	fp.write("<VERTICES>\n"+vertCount+"\n");
	    	for (int i=0;i<vertCount;i++) {
	    		Vertex v=vertices[i+1];
	    		
	    		// center may be from 'PackData' or is recorded, e.g., for dual
	    		Complex z=getVertCenter(v.halfedge);
	    		if (p!=null) 
	    			z=p.getCenter(v.vertIndx);
	    		int xi=(int)Math.round(z.x*1000000.0); // convert to microns
	    		int yi=(int)Math.round(z.y*1000000.0); // convert to microns
	    		fp.write(i+"  "+xi+" "+yi+" 1000 0 \n"); // radius is fake, flag is fake
	    	}
	    	fp.write("</VERTICES>\n");
	    	
	    	int []eticks=new int[edgeCount];
	    	int ecount=0;
	    	for (int e=1;e<=edgeCount;e++) {
	    		HalfEdge he=edges[e];
	    		int indx=he.edgeIndx-1;
	    		if (eticks[indx]==0) {
	    			int te=he.twin.edgeIndx-1;
	    			eticks[indx]=te;  // points to twin indx
	    			eticks[te]=-e;    // minus indicates this is twin
	    			ecount++;
	    		}
	    	}
	    	
	    	// TODO: note the shift in indices to start at 0. reason?
	    	fp.write("<EDGES>\n"+ecount+"\n");
	    	for (int e=0;e<edgeCount;e++) 
	    		if (eticks[e]>0) {
	    			HalfEdge he=edges[e+1];
	    			int v=he.origin.vertIndx-1;
	    			int w=he.twin.origin.vertIndx-1;
	    			fp.write(e+" "+eticks[e]+" "+v+" "+w+"\n");
	    		}
	    	fp.write("</EDGES>\n");
	    	
	    	// dual faces: don't need these yet, just put default
	    	fp.write("<FACES>\n"+"1\n-1 0\n</FACES>\n");
	    	
	    	fp.flush();
	    	fp.close();
	    } catch(Exception ex) {
	    	try{
	    		fp.flush();
	    		fp.close();
	    	} catch(Exception iox) {}
	    	throw new InOutException("failed writing dual DCEL data");
	    }
	    
	    return edgeCount;
	}
	
	/**
	 * Write the dual graph to a file.
	 * TODO: This is an early format, 4/2017, and should probably 
	 * be rethought, but need if for 3D modeling work now.
	 * @param filename
	 * @return 0 on failure
	 */
	public int hold_writeDual(String filename) {
		
		BufferedWriter fp=null;
		File file=null;
		try {
			file=new File(filename);
			String dir=CPFileManager.PackingDirectory.toString();
			fp=CPFileManager.openWriteFP(new File(dir),
					false,file.getName(),false);
			if (fp==null)
				throw new InOutException();
		} catch (Exception ex) {
			throw new InOutException("Failed to open '"+file.toString()+
					"' for writing");
		}

		HalfEdge []dedges=new HalfEdge[edgeCount];
		HalfEdge []dverts=new HalfEdge[faceCount];
		
		// dual edges are regular edges
    	for (int e=1;e<=edgeCount;e++) {
			HalfEdge he=edges[e];
			if (he!=null)
				dedges[he.edgeIndx-1]=he;
		}
				
		// dual vertices are regular faces
    	for (int f=1;f<=faceCount;f++) {
    		DcelFace face=faces[f];
			if (face.edge!=null)
				dverts[face.faceIndx-1]=face.edge;
		}
			
		// dual faces are regular vertices
		HalfEdge []dfaces=new HalfEdge[vertCount];
		for (int i=1;i<=vertCount;i++)
			dfaces[vertices[i].vertIndx-1]=vertices[i].halfedge;

		// write the data to the file
	    try {
	    	
	    	// dual vertices
	    	fp.write("<VERTICES>\n"+faceCount+"\n");
	    	for (int i=0;i<faceCount;i++) {
	    		
	    		DcelFace face=dverts[i].face;
	    		Complex z=faceOppCenter(face);
	    		int xi=(int)Math.round(z.x*1000000.0); // convert to microns
	    		int yi=(int)Math.round(z.y*1000000.0); // convert to microns
	    		fp.write(i+"  "+xi+" "+yi+" 1000 0 \n");
	    	}
	    	fp.write("</VERTICES>\n");
	    	
	    	// dual edges. First, bookkeeping: match with twins
	    	int []eticks=new int[edgeCount];
	    	int ecount=0;
	    	for (int e=0;e<edgeCount;e++) {
	    		HalfEdge he=dedges[e];
	    		int indx=he.edgeIndx-1;

// debug
//	    		System.out.println("e = "+e);
	    		
	    		if (eticks[indx]==0) {
	    			int te=he.twin.edgeIndx-1;
	    			eticks[indx]=te;  // points to twin indx
	    			eticks[te]=-e;    // minus indicates this is twin
	    			ecount++;
	    		}
	    	}
	    	fp.write("<EDGES>\n"+ecount+"\n");
	    	for (int i=0;i<edgeCount;i++) 
	    		if (eticks[i]>0) {
	    			HalfEdge he=dedges[i];
	    			int v=he.origin.vertIndx;
	    			int w=he.twin.origin.vertIndx;
	    			fp.write(i+" "+eticks[i]+" "+v+" "+w+"\n");
	    		}
	    	fp.write("</EDGES>\n");
	    	
	    	// dual faces: don't need these yet, just put default
	    	fp.write("<FACES>\n"+"1\n-1 0\n</FACES>\n");
	    	
	    	fp.flush();
	    	fp.close();
	    } catch(Exception ex) {
	    	try{
	    		fp.flush();
	    		fp.close();
	    	} catch(Exception iox) {}
	    	throw new InOutException("failed writing dual DCEL data");
	    }
	    CirclePack.cpb.msg("Wrote dual DCEL data to "+
	    		  CPFileManager.PackingDirectory+File.separator+file.getName());
		return 1; // temp return.
	}
}

// inner utility class to catalog incoming/outgoing red spokes.
class ChokeData {
	public RedEdge[] redSpokes; // outgoing red spokes 
	public RedEdge[] inSpokes;  // incoming red spokes
	public RedEdge[] inEdge;    // null unless this spoke has incoming
	public RedEdge[] outEdge;    // null unless this spoke has outgoing
	public int doublecount;      // how many edges are both in and out
	
	public ChokeData() {
	}
	
}

