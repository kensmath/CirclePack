package dcel;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import allMains.CPBase;
import allMains.CirclePack;
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
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import posting.PostFactory;
import util.ColorUtil;
import util.DispFlags;
import util.TriData;

/** 
 * The "DCEL" is a common way that computer scientists 
 * encode graphs; it's also called a 'HalfEdge' structure.
 * At the suggestion of John Bowers, I may incorporate this 
 * in CirclePack; it is only concerned with the combinatorics.
 * The triangulation is assumed to be 3-connected.
 * 
 * This preliminary class is for testing DCEL methods. 
 * In particular, things are not sync'ed well with the 
 * traditional 'PackData' parent. In testing, therefore, 
 * we often write the results out and read them into 
 * 'CirclePack' and create the new DCEL structure, rather
 * than try to update everything with the current DCEL 
 * structure. Interim routines for interacting with 
 * 'CirclePack' are marked with "NEEDED FOR CIRCLEPACK". 
 * 
 * Note on indices: Vertices, edges, and faces all get indices;
 * these are independent and all start with 1. Those for vertices
 * are intended to align with indices of p. Others are a convenience,
 * i.e. for writing DCEL structures to files. In particular, face
 * indices are not sync'ed with face indices in 'p', since these
 * are ephemeral. 
 * 
 * The principal route to creating DCEL structures uses 'bouquet's
 * in 'CombDCEL.d_redChainBuilder', which first calls 'CombDCEL.createVE'
 * to build vertices and faces and set 'alpha', then creates the
 * redchain, and then calls 'CombDCEL.d_FillInside' to finish.
 * (This is done because many manipulations, such as adding barycenters,
 * need only call 'd_FillInside' to adjust the DCEL structure if
 * the redchain was not disturbed.) Note that these routines create 
 * only a 'PackDCEL' object; a call to 'DataDCEL.dcel_to_packing' 
 * will create an associated packing.
 * 
 * @author kstephe2, July 2016
 *
 */
public class PackDCEL {
	
	public PackData p;

	public int vertCount;		// number of vertices (local, may not agree with 'p.nodeCount')
	public int edgeCount;
	public int faceCount;
	public int intFaceCount;	// number of interior faces (larger face indices are ideal faces)
	public int idealFaceCount;  // indexes are the largest and set to negative
	
	public Vertex[] vertices; // indexed from 1; some being 'RedVertex's.
	public HalfEdge[] edges;  // indexed from 1; some have pointers to 'RedHEdge's
	public Face[] faces;      // indexed from 1
	public Face[] idealFaces; // indexed from 1 (but 'faceIndx' is the negative of the index)
	// note edges and faces are subject to change with new layout

	// final layout information in 'Graphlink's
	public GraphLink faceOrder; // order for laying out all 
	public GraphLink computeOrder;  // sub order for computing centers/radii
	
	public VertexMap newOld; // NEEDED FOR CIRCLEPACK
	public RedHEdge redChain; // doubly-linked, cclw edges about a fundamental region
	public ArrayList<RedHEdge> sideStarts; // red edges starting paired sides, will have 'mobIndx'
	public HalfEdge alpha; // origin normally placed at origin
	public HalfEdge gamma; // origin normally on positive y-axis, default 'alpha.twin'
	
	public D_PairLink pairLink;  // linked list of 'D_SideData' on side-pairings
	
	public TriData[] triData;    // 'TriData'; typpically null, used for repacking
	boolean debug;
	
	// Constructor(s)
	public PackDCEL() { // naked shell
		p=null;
		vertCount=0;
		vertices=null;
		edges=null;
		faces=null;
		idealFaces=null;
		triData=null;
		
		// things to fill at end of dcel construction
		faceOrder=null;
		computeOrder=null;

		newOld=null;
		redChain=null;
		debug=false;
	}
	
	/**
	 * Cleanup routine: '*_raw' routines modify dcel 
	 * structure w/o complete update: 'vertCount', 
	 * 'vertices', edge connectivity should be in 
	 * tact and often 'redChain' is in tact. The
	 * calling routine should already have done 
	 * things, like cents/radii. If the red chain
	 * was broken, the calling routine should set
	 * 'redChain' null and 'redchain_by_edge' is
	 * called. Faces may be outdated or
	 * non-existent, 'edges', 'faces', counts, etc. 
	 * need updating, so 'd_FillInside' is called. 
	 * Also, need to 'attach' to a packing (usually 
	 * the current parent).
	 * @param p PackData
	 */
	public void fixDCEL_raw(PackData p) {
		if (p==null)
			p=this.p;
		try {
		  // may need new red chain
		  if (redChain==null) {
			  CombDCEL.redchain_by_edge(this, null, this.alpha);
		  }

		  CombDCEL.d_FillInside(this);
		  p.attachDCEL(this);
	  } catch (Exception ex) {
		  throw new DCELException("Problem with 'fix_raw'. "+ex.getMessage());
	  }
	}
	
	/**
	 * Create and populate 'triData[]'. This loads 'radii', 'invDist's, 'aim',
	 * and computes 'angles'.
	 * @return int faceCount
	 */
	public int allocTriData() {
		triData=new TriData[faceCount+1];
		for (int f=1;f<=faceCount;f++) {
			triData[f]=new TriData(this,f);
		}
		return faceCount;
	}
	
	public void updateTriDataRadii() {
		for (int f=1;f<=faceCount;f++) { 
			triData[f].hes=p.hes;
			ArrayList<HalfEdge> eflower=faces[f].getEdges();
			if (eflower.size()!=3)
				throw new DataException();
			for (int j=0;j<3;j++) {
				HalfEdge he=eflower.get(j);
				int v=he.origin.vertIndx;
				triData[f].radii[j]=p.getRadius(v);
				triData[f].invDist[j]=he.getInvDist();
			}	
		}
	}
	
	/**
	 * Given vertex 'v', store its "indices" in 'p.Vdata[v]';
	 * these point to the faces containing v and the indices
	 * of v in those faces. Also set 'bdryFlag' and 'num'
	 * @param v int
	 * @return int num
	 */
	public int setVDataIndices(int v) {
// System.out.println(" fillIndices:"+v);		
		if (p.vData==null || p.vData[v]==null)
			throw new CombException("'vData' is not allocated");
		Vertex vert=vertices[v];
		ArrayList<Face> facelist=vert.getFaceFlower();
		ArrayList<Integer> f_indices=new ArrayList<Integer>();
		ArrayList<Integer> v_indices=new ArrayList<Integer>();
		Iterator<Face> fist=facelist.iterator();
		while (fist.hasNext()) {
			Face face=fist.next();
			int f=face.faceIndx;
			if (f>0) {
				f_indices.add(f);
				int j=face.getVertIndx(v);
				if (j<0) 
					throw new CombException("v="+v+" is not a vertex of face "+f);
				v_indices.add(j);
			}
		}
		int num=f_indices.size();
		p.vData[v].num=num;
		p.vData[v].findices=new int[num];
		p.vData[v].myIndices=new int[num];
		for (int j=0;j<num;j++) {
			p.vData[v].findices[j]=f_indices.get(j);
			p.vData[v].myIndices[j]=v_indices.get(j);
		}
		
		// set 'bdryFlag'
		if (vert.isBdry())
			vert.bdryFlag=1;
		else vert.bdryFlag=0; 
		p.vData[v].setBdryFlag(vert.bdryFlag);

		return num;
	}
				;
	/**
	 * The "red" chain is a closed cclw chain of edges about
	 * a simple connected fundamental domain for the complex.
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
		HalfLink hlink=CombDCEL.d_CookieData(p,str);
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
	public int indexFaces(ArrayList<HalfEdge> edges,ArrayList<HalfEdge> bdryedges) {

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
		ArrayList<Face> tmpFaceList=new ArrayList<Face>();
		tmpFaceList.add(null); // index from 1, first entry 'null'
		ArrayList<Face> tmpIdealFaces=new ArrayList<Face>(0);
		
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
					Face newFace=new Face();
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
			idealFaces=new Face[idealIndx+1];
			Iterator<Face> tif=tmpIdealFaces.iterator();
			int tick=0;
			while (tif.hasNext()) {
				Face face=tif.next();
				idealFaces[++tick]=face;
				face.faceIndx=-(faceCount+tick); // increment index, make negative
				tmpFaceList.add(face);
			}
			faceCount +=idealIndx; // 'faceCount' counts both regular and ideal faces
		}
		
		// store all the faces
		faces=new Face[faceCount+1];
		Iterator<Face> fit=tmpFaceList.iterator();
		fit.hasNext(); // shuck first null entry
		int tick=0;
		while (fit.hasNext()) {
			faces[++tick]=fit.next();
		}
		
		return faceCount;
	}
	
	/**
	 * Return array of faces to be used in order for
	 * computing circle centers. Start with edge 'alpha'
	 * and its face. As each face is added to the array,
	 * its 'edge' is set to that from v to u, where v and
	 * u will have been laid out earlier in the process;
	 * one uses these to find the third circle w.
	 * @return ArrayList<Face>
	 */
	public ArrayList<Face> simpleLayout() {
		ArrayList<Face> farray=new ArrayList<Face>();
		HalfEdge []vedges=new HalfEdge[p.nodeCount+1]; // edges laid at v 
		int []vhits=new int[p.nodeCount+1];  // verts laid out
		NodeLink currv=new NodeLink();
		NodeLink nextv=new NodeLink();
		
		// first face
		Face f=alpha.face;
		p.firstFace=f.faceIndx;
		f.edge=alpha; 
		farray.add(f); 
		vedges[alpha.origin.vertIndx]=alpha; // marks as laid out
		vedges[alpha.twin.origin.vertIndx]=alpha.next;
		vedges[alpha.next.twin.origin.vertIndx]=alpha.next.next;
		vhits[alpha.origin.vertIndx]=-1; // marks as laid out
		vhits[alpha.twin.origin.vertIndx]=-1;
		vhits[alpha.next.twin.origin.vertIndx]=-1;
		nextv.add(alpha.origin.vertIndx);
		nextv.add(alpha.twin.origin.vertIndx);
		nextv.add(alpha.next.twin.origin.vertIndx);
		int count=3; // number laid out
		
		while (nextv!=null && nextv.size()>0) {
			currv=nextv;
			nextv=new NodeLink();
			Iterator<Integer> cit=currv.iterator();
			while (cit.hasNext()) {
				int v=cit.next();
				
				// process this 'v'?
				if (vhits[v]==-1) {
					HalfEdge myedge=vedges[v];
					HalfLink fflower=myedge.origin.getEdgeFlower(); 
					int n=fflower.size();
					int k=fflower.indexOf(myedge);
					
					// go counterclockwise
					for (int j=1;j<n;j++) {
						HalfEdge he=fflower.get((j+k)%n);
						
						// should he.face be used in layout? 
						if (he.face.faceIndx>0 && 
								vhits[he.twin.origin.vertIndx]!=0 &&
								vhits[he.next.twin.origin.vertIndx]==0) {
							// touched new vert
							int newvert=he.next.twin.origin.vertIndx;
							vhits[newvert]=-1;
							nextv.add(newvert);
							count++;
							
							// identify an edge for it
							vedges[newvert]=he.next.next;
							
							// store this face in layout list
							he.face.edge=he;
							farray.add(he.face);
						}
					}
					
					// now go clockwise -- only needed for boundary vertices
					for (int j=1;j<n;j++) {
						HalfEdge he=fflower.get((k-j+n)%n).twin;
						
						// should he.face be used in layout? 
						if (he.face.faceIndx>0 && 
								vhits[he.origin.vertIndx]!=0 &&
								vhits[he.next.twin.origin.vertIndx]==0) {
							// touched new vert
							int newvert=he.next.twin.origin.vertIndx;
							vhits[newvert]=-1;
							nextv.add(newvert);
							count++;
							
							// identify an edge for it
							vedges[newvert]=he.next.next;
							
							// store this face in layout list
							he.face.edge=he;
							farray.add(he.face);
						}
					}
					
					vhits[v]=2; 
				} // done processing 'v'
			} // end of while on currv
		} // end of while on nextv
		
		if (count!=vertCount)
			CirclePack.cpb.errMsg("dcel layout: missed some vertex?");
		return farray;
	}

	/**
	 * Place the face associated with 'edge', with 'edge.origin' at 
	 * the origin, and 'edge.next.origin' on the positive real axis. 
	 * @param edge HalfEdge
	 * @return CircleSimple
	 */
	public CircleSimple placeFirstFace(HalfEdge edge) {
		double r0=getVertRadius(edge);
		double r1=getVertRadius(edge.next);
		setCent4Edge(edge,new Complex(0.0));
		double invd=getInvDist(edge);
		double dist=CommonMath.ivd_edge_length(r0,r1,invd,p.hes);
		if (p.hes>0) // sph
			setCent4Edge(edge.next,new Complex(0.0,dist));
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
			setCent4Edge(edge.next,new Complex(dist,0.0));
		}
		else // eucl
			setCent4Edge(edge.next,new Complex(dist,0.0));
		CircleSimple cS=d_compOppCenter(edge);
		setCent4Edge(edge.next.next,cS.center);
		return cS;
	}
	
	/**
	 * Compute the center of the vertex oppose 'edge' in its 
	 * face. Use radii stored for 'Vertex's of the face 
	 * and the centers of the ends of 'edge' to compute the 
	 * center of the third vertex. 
	 * @param edge HalfEdge
	 * @return CircleSimple  
	 */
	public CircleSimple d_compOppCenter(HalfEdge edge) {
		CircleSimple c0=getVertData(edge);
		CircleSimple c1=getVertData(edge.next);
		CircleSimple c2=getVertData(edge.next.next);
		double ov0=getInvDist(edge);
		double ov1=getInvDist(edge.next);
		double ov2=getInvDist(edge.next.next);
		CircleSimple sC=CommonMath.comp_any_center(c0.center,
			c1.center,c0.rad,c1.rad,c2.rad,ov0,ov1,ov2,p.hes);
		return sC;
	}
	
	/**
	 * Compute/store centers for 'edge.face' from the opposed 
	 * 'edge.twin.face'. That is, get the centers for the ends 
	 * of 'edge' from the opposing face, compute the center 
	 * opposite 'edge' in 'edge.face', then store all three 
	 * centers for 'edge.face'. Note: if 'edge.myRedEdge' is 
	 * not null, this will change the centers stored for the 
	 * two red vertices at the ends of 'edge'. 
	 * @param edge HalfEdge
	 * @return int, 1 or 3 centers set.
	 */
	public int d_faceXedge(HalfEdge edge) {
		
		// get centers of end vertices from opposed face 
		HalfEdge etwin=edge.twin;
		CircleSimple c0=getVertData(etwin.next);
		CircleSimple c1=getVertData(etwin);
		double rad2=getVertRadius(edge.next.next);
		double ov0=getInvDist(etwin.next);
		double ov1=getInvDist(etwin);
		double ov2=getInvDist(edge.next.next);
		CircleSimple sC=CommonMath.comp_any_center(c0.center,
			c1.center,c0.rad,c1.rad,rad2,ov0,ov1,ov2,p.hes);
		setVertData(edge.next.next,sC);
		if (edge.myRedEdge!=null) {
			setVertData(edge,c0);
			setVertData(edge.next,c1);
			return 3;
		}
		return 1;
	}
	
	/**
	 * Use 'computeOrder' to compute circle centers, laying base face
	 * first, then the rest. Note that some circles get laid down more
	 * than once, so last position is what is stored in 'Vertex' for now.
	 * TODO: for more accuracy, average all computations of center that are
	 * available: Idea: make all centers null so we can see which are set.
	 * @return count
	 */
	public int D_CompCenters() {
		boolean debug=false;
		placeFirstFace(alpha);
		Iterator<EdgeSimple> esit=computeOrder.iterator();
		esit.next(); // first already laid down
		int count=1;
		while (esit.hasNext()) {
			EdgeSimple es=esit.next();
			HalfEdge he=faces[es.w].edge;
			CircleSimple cs=d_compOppCenter(he);
			setCent4Edge(he.next.next,cs.center);
			count++;
			
			if (debug) { // debug=true; 
				CircleSimple c0=getVertData(he);
				CircleSimple c1=getVertData(he.next);
				CircleSimple c2=getVertData(he.next.next);

				System.out.println("face "+es.w+": <"+faces[es.w].toString()+">:  centers(radii): ");
				System.out.println("   "+c0.center+"  ("+c0.rad+")");
				System.out.println("   "+c1.center+"  ("+c1.rad+")");
				System.out.println("  compute: "+cs.center+"  ("+c2.rad+")");
				p.cpScreen.drawEdge(c0.center,c1.center,new DispFlags("c5"));
				p.cpScreen.drawFace(c0.center, c1.center, c2.center, .05, .05, .05, new DispFlags("fc120"));

				// draw third circle
				p.cpScreen.drawCircle(c2.center, c2.rad,new DispFlags("cn"));
				
				// draw dot
				int v=he.next.next.origin.vertIndx;
				p.setCenter(v,cs.center);
				StringBuilder strbld=new StringBuilder("disp -tf "+v);
				CommandStrParser.jexecute(p,strbld.toString());
			}
			
		}

		
/* OBE: final pass around red chain: set 'PackData' centers at first hits
		RedHEdge rtrace=redChain;
		do {
			rtrace.myEdge.origin.util=0;
			rtrace=rtrace.nextRed;
		} while (rtrace!=redChain);
		do {
			if (rtrace.myEdge.origin.util==0) {
				int v=rtrace.myEdge.origin.vertIndx;
				p.setCenter(v,new Complex(rtrace.center));
				rtrace.myEdge.origin.util=1;
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redChain);
*/
		
		return count;
	}
	
	/**
	 * Compute centers in DCEL case using 'computeOrder'
	 * @return
	 */
	public int dcelCompCenters() {
		return dcelCompCenters(computeOrder); 
	}
		
	/**
	 * Compute circle centers based on GraphLink of faces. 
	 * The first is the 'alpha' edge 'origin' vertex at the 
	 * origin, its other end on the positive real axis. Taking 
	 * faces in turn, the next face should have two of its 
	 * vertices in place so we can compute/store the third. 
	 * Note: radii are already computed.
	 * @param faceorder GraphLink
	 * @return int, count (may exceed 'vertCount' as some vertices
	 * are placed multiple times).
	 */
	public int dcelCompCenters(GraphLink faceorder) {
		
		Iterator<EdgeSimple> git=faceorder.iterator();
		EdgeSimple edge=faceorder.get(0);
		int f=edge.v;
		if (f==0) { // this is a root (as expected)
			f=edge.w;
		}
		
		// lay out the first face, it's 'origin' at z=0;
		Face face=faces[f];
		placeFirstFace(face.edge);
	    int count=1;
	    
	    // now layout face-by-face
	    while (git.hasNext()) {
	    	face=faces[git.next().w];
		    CircleSimple sc=CommonMath.comp_any_center(
		    		getVertCenter(face.edge),
		    		getVertCenter(face.edge.next),
		    		getVertRadius(face.edge),getVertRadius(face.edge.next),
		    		getVertRadius(face.edge.next.next),
		    		face.edge.invDist,face.edge.next.invDist,
		    		face.edge.next.next.invDist,p.hes);
		    setCent4Edge(face.edge.next.next, sc.center);
		    if (sc.rad<0) // horocycle?
		    	setRad4Edge(face.edge.next.next,sc.rad);
		    count++;
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
		D_SideData epair=null;
		boolean debug=false;
	  
		if (pairLink==null || n<0 || n>=pairLink.size() 
				|| (epair=(D_SideData)pairLink.get(n))==null
				|| epair.startEdge==null)  // epair.startEdge.hashCode();epair.startEdge.nextRed.hashCode();
			return 0;
		int old_thickness=p.cpScreen.getLineThickness();

		RedHEdge rtrace=epair.startEdge;
		Complex w_cent=new Complex(getVertCenter(rtrace.myEdge));
		double rad_w=getVertRadius(rtrace.myEdge);
		if (do_label) // label first circle with side indx
			p.cpScreen.drawIndex(w_cent,n,1);
		DispFlags dflags=new DispFlags(""); // System.out.println("v_indx="+v_indx+", wyd_w center.x "+wyd_w.center.x+" and hash "+wyd_w.hashCode());
		if (do_circle) { // handle draw/label for first circle
			int w_indx=rtrace.myEdge.origin.vertIndx;
			if (do_label) { 
				dflags.label=true;
				dflags.setLabel(Integer.toString(w_indx));
			}
			p.cpScreen.drawCircle(w_cent,rad_w,dflags);
		}
		Complex v_cent=null;
		do {
			rtrace=rtrace.nextRed;
			v_cent=w_cent;
			w_cent=new Complex(getVertCenter(rtrace.myEdge));
			p.cpScreen.setLineThickness(thickness);
			int w_indx=rtrace.myEdge.origin.vertIndx;
			DispFlags df=new DispFlags(null);
			df.setColor(ecol);
			p.cpScreen.drawEdge(v_cent,w_cent,df);
			
			if (debug) { // debug=true;
				p.cpScreen.rePaintAll();
			}
			
			if (do_circle) { 
				if (do_label) { 
					dflags.label=true;
					dflags.setLabel(Integer.toString(w_indx));
				}
				p.cpScreen.setLineThickness(old_thickness);
				p.cpScreen.drawCircle(w_cent,getVertRadius(rtrace.myEdge),dflags);
				p.cpScreen.setLineThickness(thickness);
			}
	    } while (rtrace!=epair.endEdge.nextRed);
		p.cpScreen.setLineThickness(old_thickness);
		return 1;
	}
	
	/**
	 * Add barycenters to given list of faces. 
	 * A barycenter is a new vertex interior to the 
	 * face and connected to its bdry vertices.
	 * @param facelink FaceLink
	 * @return int count, 0 on error
	 */
	public int addBaryCents_raw(FaceLink facelink) {
		ArrayList<Face> arrayf=new ArrayList<Face>();
		if (facelink==null) {
			for (int j=1;j<=intFaceCount;j++)
				arrayf.add(faces[j]); // get face
		}
		else {
			Iterator<Integer> flst=facelink.iterator();
			while (flst.hasNext()) {
				arrayf.add(faces[flst.next()]); // get face of that index
			}
		}
		return addBaryCents_raw(arrayf);
	}
	
	/**
	 * Add barycenters to given list of faces. 
	 * A barycenter is a new vertex interior to the 
	 * face and connected to its bdry vertices.
	 * @param arrayf ArrayList<Face>
	 * @return int count, 0 on error
	 */
	public int addBaryCents_raw(ArrayList<Face> arrayf) {
		int count=0;

		Iterator<Face> flst=arrayf.iterator();
		while (flst.hasNext()) {
			Face face=flst.next();
			count += RawDCEL.addBary_raw(this, face,false);
			
			// face was ideal? toss 'redChain'
			if (face.faceIndx<0) 
				redChain=null;

			face.edge=null; // to avoid repeat in facelist

		} // end of while through facelist

		return count;
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
				Face leftf=he.face;
				Face rightf=he.twin.face;
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
		ArrayList<Face> arrayF=new ArrayList<Face>();
		
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
				ArrayList<Face> myfaceflower=vert.getFaceFlower();
				Iterator<Face> fit=myfaceflower.iterator();
				while (fit.hasNext()) {
					Face face=fit.next();
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
		int n=addBaryCents_raw(arrayF);
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
			Face bface=he.face;
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
				bouq[v]=vertices[v].getFlower();
			} catch (Exception ex) {
				System.err.println("getFlower fails for "+v);
			}
		}
		return bouq;
	}
	
	/**
	 * Directly count the petals. (Useful when processing
	 * has not updated 'vData'.)
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
	 * Given directed dual <f,g> between faces, return 
	 * directed edge <v,w>; edge <v,w> is cclw to <f,g>. 
	 * @param dedge EdgeSimple
	 * @return EdgeSimple, null on failure
	 */
	public EdgeSimple dualEdge_to_Edge(EdgeSimple dedge) {
		int f=dedge.v;
		int g=dedge.w;
		HalfEdge edge=faces[f].edge;
		if (edge.twin.face.faceIndx==g) {
			return new EdgeSimple(edge.origin.vertIndx,edge.twin.origin.vertIndx);
		}
		HalfEdge trace=edge;
		do {
			trace=edge.next;
			if (trace.twin.origin.vertIndx==g) 
				return new EdgeSimple(trace.origin.vertIndx,trace.twin.origin.vertIndx);
			trace=trace.next;
		} while (trace!=edge);
		
		// reaching here, found no dual
		return null;
	}
	
	/**
	 * Given directed edge <v,w>, return directed edge <f,g> 
	 * between faces; note that <f,g> is clw from <v,w>.
	 * @param dedge EdgeSimple
	 * @return EdgeSimple, null on failure
	 */
	public EdgeSimple edge_to_dualEdge(EdgeSimple dedge) {
		HalfEdge edge=findHalfEdge(dedge);
		return new EdgeSimple(edge.face.faceIndx,edge.twin.face.faceIndx);
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
			ArrayList<HalfEdge> edgs=faces[f].getEdges();
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
	 * radius is 'r'.
	 * @param edge HalfEdge
	 * @param r double
	 * @return double, 0.0 if bdry edge
	 */
	public double getEdgeAngle(HalfEdge edge,double r) {
		if (edge.face==null || edge.face.faceIndx<0) {
			CirclePack.cpb.errMsg("this is a boundary edge, "+edge.toString());
			return 0.0;
		}
		double r0=r;
		double r1=getVertRadius(edge.next);
		double r2=getVertRadius(edge.prev);
		double o0=edge.invDist;
		double o1=edge.next.invDist;
		double o2=edge.prev.invDist;
		return CommonMath.get_face_angle(r0, r1, r2, o0, o1, o2, p.hes);
	}
	
	/**
	 * Get the angle sum at 'vert' using radius 'rad'
	 * @param vert Vertex
	 * @param rad double
	 * @return double
	 */
	public double getVertAngSum(Vertex vert,double rad) {
		double angsum=0.0;
		HalfEdge he=vert.halfedge;
		do {
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
	 * then look clw for first 'RedHEdge'. If normal 
	 * 'Vertex', then data is stored in 'PackData.vData'. 
	 * @return CircleSimple
	 */
	public CircleSimple getVertData(HalfEdge edge) {
		// is itself a 'RedHEdge'?
		if (edge.myRedEdge!=null)
			return edge.myRedEdge.getData();
		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'?
		if (!vert.redFlag) {
			int v=vert.vertIndx;
			return new CircleSimple(p.vData[v].center,p.vData[v].rad);
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
	 * Get the radius in its internal form (i.e., x-rad for hyp case)
	 * @param edge
	 * @return
	 */
	public double getVertRadius(HalfEdge edge) {
		// is itself a 'RedHEdge'? Note, also set in 'PackData'
		if (edge.myRedEdge!=null) {
			return edge.myRedEdge.getRadius();
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'? set in 'PackData'
		if (!vert.redFlag) {
			return p.vData[vert.vertIndx].rad;
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) {
				return he.myRedEdge.getRadius();
			}
		} while (he!=edge);
		throw new DCELException("didn't find any 'RedHEdge' for this 'Vertex'");
	}
	
	/**
	 * Get appropriate center
	 * @param edge HalfEdge
	 * @return new Complex
	 */
	public Complex getVertCenter(HalfEdge edge) {
		// is itself a 'RedHEdge'? Note, also set in 'PackData'
		if (edge.myRedEdge!=null) {
			return edge.myRedEdge.getCenter();
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'? set in 'PackData.vData'
		if (!vert.redFlag) {
			return new Complex(p.vData[vert.vertIndx].center);
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) {
				return he.myRedEdge.getCenter();
			}
		} while (he!=edge);
		throw new DCELException("didn't find any 'RedHEdge' for this 'Vertex'");
	}
	
	/**
	 * Set vert's center in all locations; that is, in 
	 * 'vData', 'rData' (for backup), and in any associated 
	 * 'RedHEdges' for this v. (Compare with 'setRad4Edge' which 
	 * only set's the value associated with one edge.)
	 * @param v int
	 * @param z Complex
	 */
	public void setVertCenter(int v, Complex z) {
		Vertex vert=vertices[v];
		if (this==p.packDCEL) {
			p.vData[v].center=new Complex(z);
			p.rData[v].center=new Complex(z);
		}
		HalfEdge he=vert.halfedge;
		do {
			if (he.myRedEdge!=null)
				he.myRedEdge.center=new Complex(z);
			he=he.prev.twin;
		} while (he!=vert.halfedge);
	}
	
	/**
	 * Set the center for 'origin' and in the appropriate
	 * 'RedHEdge' if 'origin' is a 'RedVertex'. The center
	 * may be different in other 'RedHEdge's. (see 'setVertCenter',
	 * to set center in all locations.)
	 * Note: I no longer set rData[].center.
	 * @param edge HalfEdge
	 * @param z Complex
	 */
	public void setCent4Edge(HalfEdge edge,Complex z) {
		Vertex vert=edge.origin;
		p.vData[vert.vertIndx].center=new Complex(z);

		// a normal 'Vertex'? 
		if (!vert.redFlag) {
			return;
		}

		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		while (he.myRedEdge==null) {
			he=he.twin.next;
		}
		he.myRedEdge.setCenter(z);
		return;
	}

	/**
	 * Set vert's radius (hyp: in its internal form) in all 
	 * locations; i.e., in 'vData', 'rData' (for backup), and in
	 * any associated 'RedHEdges' for this v. (Compare with
	 * 'setRad4Edge' which only set's the value associated
	 * with one edge.)
	 * @param v int
	 * @param rad double
	 */
	public void setVertRadii(int v,double rad) {
		Vertex vert=vertices[v];
		if (p.packDCEL==this) {
			p.vData[v].rad=rad;
			p.rData[v].rad=rad;
		}
		HalfEdge he=vert.halfedge;
		do {
			if (he.myRedEdge!=null)
				he.myRedEdge.rad=rad;
			he=he.prev.twin;
		} while (he!=vert.halfedge);
	}
	
	/**
	 * Set the radius (in its internal form x-rad for hyp case);
	 * (Compare with 'setVertRadii' which sets this radius in all
	 * occurrences of its origin vertex.)
	 * @param edge HalfEdge
	 * @param rad double
	 */
	public void setRad4Edge(HalfEdge edge,double rad) {
		Vertex vert=edge.origin;
		p.vData[vert.vertIndx].rad=rad;
		
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
	}
	
	/**
	 * set center and radius (in its internal form);
	 * this sets data only for the given edge, so for a 'RedVertex'
	 * it sill store in the appropriate 'RedHEdge'.
	 * @param edge HalfEdge
	 * @param cS CircleSimple
	 */
	public void setVertData(HalfEdge edge,CircleSimple cS) {
		setCent4Edge(edge,cS.center);
		setRad4Edge(edge,cS.rad);
	}

	public double getInvDist(HalfEdge edge) {
		if (!p.overlapStatus)
			return 1.0;
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		return p.getInvDist(v,w);
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
			if (idealFaceCount>0 && nxtedge.twin.face.faceIndx<0) // ideal face?
				return nxtedge;
			nxtedge=nxtedge.prev.twin;
		} while(nxtedge!=v.halfedge);
		return null;
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
		Vertex vert=vertices[es.v];
		HalfEdge trace=vert.halfedge;
		do {
			if (trace.twin.origin.vertIndx==es.w) 
				return trace;
			trace=trace.prev.twin;
		} while (trace!=vert.halfedge);
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
 	 * NEEDED FOR CIRCLEPACK
	 * Local data may not agree with parent packing 'p' (e.g., after
	 * creating barycenters). However, if they do agree, we may need
	 * to sync 'p' with the local data. Here's one example: after redoing
	 * the face indexing, we may want to set corresponding 'PackData.faces' 
	 * structure, and if all looks okay, we can insert that in 'p'. 
	 * @return intFaceCount
	 */
	public int syncFaceData() {
		
		// minimal check on compatibility
		if (p.nodeCount!=vertCount)
			return 0;
		p.faceCount=intFaceCount;
		p.faces=new komplex.Face[p.faceCount+1]; // new face structure
		for (int f=1;f<=intFaceCount;f++) {
			Face face=faces[f];
			if (face.faceIndx>0) {
				face.faceIndx=f; // reset indices (perhaps already in order)
				// for new komplex.Face
				p.faces[f]=new komplex.Face();
				p.faces[f].vert=face.getVerts();
				p.faces[f].vertCount=p.faces[f].vert.length;
				p.faces[f].indexFlag=0;
			}
		}
		
		if (faceOrder!=null) {
			EdgeSimple edge=faceOrder.get(0); 
			p.firstFace=edge.v;
			if (edge.v==0)
				p.firstFace=edge.w;
		}

		// TODO: could transfer layout order to parent 'p' too.
		return intFaceCount;
	}
	

	/** 
	 * Recompute, draw, and/or post circles and/or faces along a 
	 * specified GraphLink tree. NOTE: must be a tree, so starts with
	 * {0,f} root. 
	 * (Compare to 'dcelCompCenters' that only uses faces needed to
	 * compute all centers.)
	 * @param pF PostFactory
	 * @param faceTree GraphLink
	 * @param faceFlags DispFlags, may be null
	 * @param circFlags DispFlags, may be null
	 * @param fix boolean
	 * @param placeFirst boolean: true = place anew; false, assume 2 verts in place
	 * @param tx double, only used for postscrupt output
	 * @return int count 
	 */
	public int layoutTree(PostFactory pF,GraphLink faceTree,
			DispFlags faceFlags,DispFlags circFlags,boolean fix,
			boolean placeFirst,double tx) {

		boolean debug=false;
		if (debug) { // debug=true;
//			LayoutBugs.log_GraphLink(this.p,faceTree);
			DCELdebug.drawRedChain(p, redChain);
			DCELdebug.printRedChain(redChain);
		}
		
		int count=0; // CPBase.Glink=faceTree;DualGraph.printGraph(faceTree);
		// not drawing anything? this just serves to redo layout
		boolean faceDo=false;
		if (faceFlags!=null && faceFlags.draw)
			faceDo=true;
		boolean circDo=false;
		if (circFlags!=null && circFlags.draw)
			circDo=true;
		
		if (faceTree==null || faceTree.size()==0)
			faceTree=faceOrder;
		
		// check for root; if there's not one, create one
		int rootface=1;
		if (faceTree.get(0).v!=0) { // no root?
			rootface=faceTree.get(0).v;
			faceTree.add(0, new EdgeSimple(0,rootface));
		}

		int []myVerts=new int[3];
		double []myRadii=new double[3];
		Complex []myCenters=new Complex[3];
		
		int next_spot=0; // last spot for root; (in future, may be more components)
		int next_root_indx=-1;
		while ((next_root_indx=faceTree.findRootSpot(next_spot))>=0) {
			next_spot++;
			int new_root=faceTree.get(next_root_indx).w;
			if (new_root<=0 || new_root>faceCount)
				break;
			GraphLink thisTree=faceTree.extractComponent(new_root);
			
			// handle root first
			rootface=thisTree.get(0).w;
			HalfEdge he=faces[rootface].edge;
			if (placeFirst) { // place in standard position 
				placeFirstFace(he);
			}
			else if (fix) { // assume 'he' ends are in place
				CircleSimple cS=d_compOppCenter(he);
				setVertData(he.next.next,cS);
			}
			
			myCenters[0]=getVertCenter(he);
			myCenters[1]=getVertCenter(he.next);
			myCenters[2]=getVertCenter(he.next.next);
			myRadii[0]=getVertRadius(he);
			myRadii[1]=getVertRadius(he.next);
			myRadii[2]=getVertRadius(he.next.next);
			
			if (faceDo && pF==null) { // draw the faces
				if (!faceFlags.colorIsSet && 
						(faceFlags.fill || faceFlags.colBorder)) 
					faceFlags.setColor(p.getFaceColor(rootface));
				if (faceFlags.label)
					faceFlags.setLabel(Integer.toString(rootface));
				p.cpScreen.drawFace(myCenters[0],myCenters[1],myCenters[2],
						myRadii[0],myRadii[1],myRadii[2],faceFlags);
				if (debug) p.cpScreen.rePaintAll(); // debug=true;
			}  
			
			if (circDo && pF==null) { // also draw the circles
				if (!circFlags.colorIsSet && 
						(circFlags.fill || circFlags.colBorder)) 
					circFlags.setColor(p.getCircleColor(he.next.next.origin.vertIndx));
				if (circFlags.label)
					circFlags.setLabel(Integer.toString(he.origin.vertIndx));
				p.cpScreen.drawCircle(myCenters[0],myRadii[0],circFlags);
				if (circFlags.label)
					circFlags.setLabel(Integer.toString(he.next.origin.vertIndx));
				p.cpScreen.drawCircle(myCenters[1],myRadii[1],circFlags);
				if (circFlags.label)
					circFlags.setLabel(Integer.toString(he.next.next.origin.vertIndx));
				p.cpScreen.drawCircle(myCenters[2],myRadii[2],circFlags);
				if (debug) p.cpScreen.rePaintAll(); 
			}

			thisTree.remove(0);

			// now go through the rest of the tree
			Iterator<EdgeSimple> elist = thisTree.iterator();
			while (elist.hasNext()) {
				EdgeSimple edge = elist.next();
				int last_face=edge.v;
				int next_face=edge.w;
				
				he=faces[next_face].edge; // normally, this edge is already in place
				if (last_face!=0 && he.twin.face.faceIndx!=last_face) {
					HalfEdge nhe=he.next;
					while (nhe.twin.face.faceIndx!=last_face && nhe!=he) {
						nhe=nhe.next;
					}
					if (nhe==he) 
						throw new CombException("Didn't find last face "+last_face+" next to next_face "+next_face);
					he=nhe;
				}
				if (fix) {
					d_faceXedge(he);
					
//					if (debug) { // debug=true;
//						System.out.println("next_face = "+next_face+"; "+
//								"edge "+he.next.next);
//						System.out.println("put vert "+he.next.next.origin.vertIndx+" at "+
//								getVertCenter(he.next.next));
//					}
					
				}
				
				myCenters[0]=getVertCenter(he);
				myCenters[1]=getVertCenter(he.next);
				myCenters[2]=getVertCenter(he.next.next);
				myRadii[0]=getVertRadius(he);
				myRadii[1]=getVertRadius(he.next);
				myRadii[2]=getVertRadius(he.next.next);
				
				if (faceDo && pF==null) { // draw the faces
					if (!faceFlags.colorIsSet && 
							(faceFlags.fill || faceFlags.colBorder)) 
						faceFlags.setColor(p.getFaceColor(next_face));
					if (faceFlags.label)
						faceFlags.setLabel(Integer.toString(next_face));
					p.cpScreen.drawFace(myCenters[0],myCenters[1],myCenters[2],
							myRadii[0],myRadii[1],myRadii[2],faceFlags);
					if (debug) p.cpScreen.rePaintAll();
				} 
				
				if (circDo && pF==null) { // also draw the circles
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder)) 
						circFlags.setColor(p.getCircleColor(he.next.next.origin.vertIndx));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(he.next.next.origin.vertIndx));
					p.cpScreen.drawCircle(myCenters[2],myRadii[2],circFlags);
					if (debug) p.cpScreen.rePaintAll();
				}
				
				if (pF!=null && p.hes>0) { // post routines don't know how to convert
					myCenters[0]=new Complex(p.cpScreen.sphView.toApparentSph(myCenters[0]));
					myCenters[1]=new Complex(p.cpScreen.sphView.toApparentSph(myCenters[1]));
					myCenters[2]=new Complex(p.cpScreen.sphView.toApparentSph(myCenters[2]));
				}

				// postscript
				if (faceDo && pF!=null) { // also post the faces
					
					// set face/bdry colors
					Color fcolor=null;
					Color bcolor=null;
					if (faceFlags.fill) {  
						if (!faceFlags.colorIsSet) 
							fcolor=p.getFaceColor(next_face);
						if (faceFlags.colBorder)
							bcolor=fcolor;
					}
					if (faceFlags.draw) {
						if (faceFlags.colBorder)
							bcolor=p.getFaceColor(next_face);
						else 
							bcolor=ColorUtil.getFGColor();
					}
					pF.post_Poly(p.hes, myCenters, fcolor, bcolor, tx);

					if (faceFlags.label) { // label the face
						Complex z=getFaceCenter(faces[next_face]);
						if (p.hes>0) {
							z=p.cpScreen.sphView.toApparentSph(z);
							if(Math.cos(z.x)>=0.0) {
								z=util.SphView.s_pt_to_visual_plane(z);
								pF.postIndex(z,next_face);
							}
						}
						else pF.postIndex(z,next_face);
					}
				}
				if (circDo && pF!=null) { // also post the circles
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
					if (circFlags.label) { // label the face
						if (p.hes>0) {
							Complex z=p.cpScreen.sphView.toApparentSph(myCenters[2]);
							if(Math.cos(z.x)>=0.0) {
								z=util.SphView.s_pt_to_visual_plane(z);
								pF.postIndex(z,myVerts[2]);
							}
						}
						else pF.postIndex(myCenters[2],myVerts[2]);
					}
				}
				count++;
			} // end of while through edgelist

		} // end of while through trees
		return count;
	}

	/**
	 * Return center of incircle of face with given index.
	 * @param face int
	 * @return Complex, null on error
	 */
	public Complex getFaceCenter(Face face) {
		CircleSimple sc = null;
		HalfEdge he=face.edge;
		Complex p0 = getVertCenter(he);
		Complex p1 = getVertCenter(he.next);
		Complex p2 = getVertCenter(he.next.next);
		if (p.hes < 0)
			sc = HyperbolicMath.hyp_tang_incircle(p0, p1, p2,
					getVertRadius(he),
					getVertRadius(he.next),
					getVertRadius(he.next.next));
		else if (p.hes > 0)
			sc = SphericalMath.sph_tri_incircle(p0, p1, p2);
		else
			sc = EuclMath.eucl_tri_incircle(p0, p1, p2);
		return sc.center;
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
	public Complex faceOppCenter(Face face) {
		int []verts=face.getVerts();
		if (verts.length==3) {
			Complex p0=p.getCenter(verts[0]);
			Complex p1=p.getCenter(verts[1]);
			Complex p2=p.getCenter(verts[2]);
			return PackData.face_center(p.hes,p0,p1,p2);
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
			Face face=faces[f];
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
	 * Set alpha to 'av', avoiding forbidden 'nlink' verts.
	 * If 'nlink' null, try to set alpha to 'av'. 
	 * If 'av' is zero, only change alpha if needed to 
	 * avoid forbidden. Else try to set alpha to 'av', but
	 * choose another vert if 'av' is forbidden.
	 * @param av int, may be 0
	 * @param nlink NodeLink, may be null
	 * @return int, current vert if no change, 0 on error
	 */
	public int setAlpha(int av,NodeLink nlink) {
		if (nlink==null || nlink.size()==0)
			return setAlpha(av);
		int alph=alpha.origin.vertIndx;
		if (av==0) { // no target
			if (nlink.containsV(alph)<0)
				return alph; // nothing to do: Note: may be bdry vert
		}
		else {
			if (nlink.containsV(av)<0)
				return setAlpha(av);
		}
		
		// need to search for interior, non-forbidden
		int[] vhits=new int[vertCount+1];
		for (int v=1;v<=vertCount;v++) {
			if (vertices[v].bdryFlag!=0) // bdry vertices
				vhits[v]=-1;
		}
		
		Iterator<Integer> nis=nlink.iterator();
		while (nis.hasNext()) {
			int u=nis.next();
			if (vhits[u]==0)
				vhits[u]=-2; // interior forbidden
		}

		// get default possibility if all else fails
		int ahope=-1;
		for (int v=1;(v<=vertCount && ahope<0);v++) {
			if (vhits[v]==0)
				ahope=v;
		}
		
		// mark nghbs of 
		int gotvert=-1;
		for (int v=1;(v<=vertCount && gotvert<0);v++) {
			if (vhits[v]==0) { // interior, not forbidden
				int[] flower=vertices[v].getFlower();
				boolean nogood=false;
				for (int j=0;(j<flower.length && !nogood);j++) {
					if (vhits[flower[j]]==-2) {
						vhits[flower[j]]=-1;
						nogood=true;
					}
				}
				
				// do we have a winner?
				if (!nogood)
					gotvert=v;
			}
		}
		
		if (gotvert<0) 
			gotvert=ahope;
		
		if (gotvert>0) {
			return setAlpha(gotvert);
		}
		return -1;
	}
	
	/**
	 * Reset all 'vutil' to zero. Some 'RawDCEL' routines,
	 * e.g., use 'vutil' to feed back reference indices. 
	 */
	public void zeroVUtil() {
		for (int v=1;v<=vertCount;v++) 
			vertices[v].vutil=0;
	}
	
	/**
	 * Reset all 'eutil' to zero. 
	 */
	public void zeroEUtil() {
		for (int v=1;v<=vertCount;v++) {
			HalfLink flwr=vertices[v].getEdgeFlower();
			Iterator<HalfEdge> fis=flwr.iterator();
			while(fis.hasNext())
				fis.next().eutil=0;
		}
	}
	
	/**
	 * Look through 'Vertex.vutil' entries to find references
	 * to other vertex indices, retrun 'VertexMap' with
	 * <new, old>.
	 * @return VertexMap, null on nothing found
	 */
	public VertexMap reapVUtil() {
		VertexMap vmap=new VertexMap();
		for (int v=1;v<=vertCount;v++) { 
			Vertex vert=vertices[v];
			if (vert.vutil>0)
				vmap.add(new EdgeSimple(v,vert.vutil));
		}
		if (vmap.size()==0)
			return null;
		return vmap;
	}
	
	/**
	 * The given 'VertexMap' has <new,old> indices; 
	 * copy rad/center from 'old' vertex to 'new'.
	 * @param vmap VertexMap
	 * @return count
	 */
	public int modRadCents(VertexMap vmap) {
		int count=0;
		Iterator<EdgeSimple> vis=vmap.iterator();
		while(vis.hasNext()) {
			EdgeSimple nwod=vis.next();
			if (nwod.v>0 && nwod.w>0) {
				try {
					p.setRadius(nwod.v,p.getRadius(nwod.w));
					p.setCenter(nwod.v,p.getCenter(nwod.w));
					count++;
				} catch(Exception ex) {
					throw new CombException("'modRadCents' usage error");
				}
			}
		}
		return count;
	}
			
	/**
	 * Set 'alpha' edge; this is the vert normally placed at origin.
	 * @param v int
	 * @return 'v' or 0 on failure
	 */
	public int setAlpha(int v) {
		if (v<=0 || v>vertCount)
			return 0;
		int alph=alpha.origin.vertIndx;
		if (v!=alph) {
			Vertex vertex =vertices[v];
			if (vertex.bdryFlag!=0)
				return 0;
			alpha=vertex.halfedge;
			if (gamma==alpha)
				gamma=alpha.twin;
			CombDCEL.d_FillInside(this);
			return v;
		}
		if (gamma==alpha)
			gamma=alpha.twin;
		return v;
	}
	
    /**
     * Set packing 'gamma' index; it's vertex generally placed on y+ axis.
     * @param i int, can't be 'alpha'
     * @return 1 on success, 0 on failure
     */
    public int setGamma(int v) {
        if (v<=0 || v>vertCount)
        	return 0;
		Vertex vertex =vertices[v];
		if (vertex==alpha.origin) { 
			CirclePack.cpb.errMsg("'gamma' cannot be set to the 'alpha' edge origin");
			return 0;
		}
		gamma=vertex.halfedge;
        return 0;
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
    		Face face=faces[f];
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
	    		
	    		Face face=dverts[i].face;
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
	public RedHEdge[] redSpokes; // outgoing red spokes 
	public RedHEdge[] inSpokes;  // incoming red spokes
	public RedHEdge[] inEdge;    // null unless this spoke has incoming
	public RedHEdge[] outEdge;    // null unless this spoke has outgoing
	public int doublecount;      // how many edges are both in and out
	
	public ChokeData() {
	}
	
}

