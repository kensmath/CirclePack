package dcel;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

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
import input.CPFileManager;
import input.CommandStrParser;
import komplex.EdgeSimple;
import komplex.KData;
import komplex.RedEdge;
import komplex.RedList;
import komplex.SideDescription;
import komplex.Triangulation;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import packing.RData;
import util.DispFlags;

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
	public int euler;           // euler characteristic of surface
	
	public Vertex[] vertices; // indexed from 1; some being 'RedVertex's.
	public HalfEdge[] edges;  // indexed from 1; some have pointers to 'RedHEdge's
	public Face[] faces;      // indexed from 1
	public Face[] idealFaces; // indexed from 1 (but 'faceIndx' is the negative of the index)
	// note edges and faces are subject to change with new layout
	
	// temporary data structures used during processing
	public ArrayList<HalfEdge> tmpEdges; //
	public ArrayList<Face> tmpFaceList; // intended to be temporary (first entry 'null')
	public ArrayList<Face> tmpIdealFaces; // intended to be temporary 
	public ArrayList<Face> tmpLayout; // order of faces responsible for new vertices
	public ArrayList<Face> tmpfullLayout; // order all faces are encountered

	// final layout information in 'Graphlink's
	public GraphLink faceOrder; // order for laying out all 
	public GraphLink computeOrder;  // sub order for computing centers/radii
	
	public VertexMap newOld; // NEEDED FOR CIRCLEPACK
	public RedHEdge redChain; // doubly-linked, cclw edges about a fundamental region
	public ArrayList<RedHEdge> bdryStarts; // red edges at start of a free (unpasted) side
	public ArrayList<RedHEdge> sideStarts; // red edges starting paired sides, will have 'mobIndx'
	boolean debug;
	public HalfEdge alpha;
	
	public D_PairLink pairLink;  // linked list of 'D_SideData' on side-pairings
	
	// Constructor(s)
	public PackDCEL() {
		p=null;
		vertCount=0;
		vertices=null;
		
		// tmp stuff
		tmpEdges=null;
		tmpFaceList=null;
		tmpIdealFaces=null;
		tmpLayout=null;
		tmpfullLayout=null;

		// things to fill at end of dcel construction
		faceOrder=null;
		computeOrder=null;

		newOld=null;
		redChain=null;
		debug=false;
		euler=3; // impossible euler char 
	}
	
	/**
	 * Build from "bouquet", which has a row for each vertex giving
	 * its cclw neighbors. 'p' remains null.
	 * @param bouquet [][]int
	 */
	public PackDCEL(int [][]bouquet) {
		this();
		vertCount=bouquet.length-1;
		euler=CombDCEL.createVE(this,bouquet,0); 
		alpha=chooseAlpha(null);
		try {
			tmpLayout=simpleLayout();
		} catch (Exception ex) {
			CirclePack.cpb.msg("LayoutOrder failed: this may not be an error (e.g. laying out dual)."
					+" euler char is "+euler);
		}
	}
	
	/** 
	 * Build DCEL structure associate with 'pdata'
	 * @param pdata PackData, 
	 */
	public PackDCEL(PackData pdata) {
		this();
		p=pdata;
		vertCount=p.nodeCount;
		int [][]bouquet= new int[vertCount+1][];
		for (int v=1;v<=vertCount;v++)
			bouquet[v]=p.kData[v].flower;
		euler=CombDCEL.createVE(this,bouquet,0); 
		alpha=chooseAlpha(null);
		tmpLayout=simpleLayout();
	}
	
	/** 
	 * Build from triangulation; 'p' remains null
	 * @param Tri Triangulation
	 */
	public PackDCEL(Triangulation Tri) {
		this();
		p=null;
		int []ans=new int[2];
		KData []kData=Triangulation.parse_triangles(Tri,0,ans);
		int nodecount=ans[0];
		if (nodecount<=2)
			throw new DataException("DCEL: parse_triangles came up short");
		
		// get the bouquet of flowers
		int [][]bouquet=new int[nodecount+1][];
		for (int v=1;v<=nodecount;v++) {
			int num=kData[v].num;
			bouquet[v]=new int[num+1];
			for (int j=0;j<=num;j++)
				bouquet[v][j]=kData[v].flower[j];
		}
		
		// TODO: how to call CombDCEL.createVEF?
	}		
	
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
		NodeLink arrayV=new NodeLink();
		for (int n=1;n<=vertCount;n++)
			if (vhits[n]==1) 
				arrayV.add(n);
		
		// TODO: have to adjust call for ones to eliminate.
		return CombDCEL.d_redChainBuilder(this.p,this.getBouquet(),arrayV,false);
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
		tmpFaceList=new ArrayList<Face>();
		tmpFaceList.add(null); // index from 1, first entry 'null'
		tmpIdealFaces=new ArrayList<Face>(0);
		
		// start with 'alpha'
		edges.remove(alpha);
		edges.add(0, alpha);
		
		// remove all 'face' items from 'edges'
		Iterator<HalfEdge> egs=edges.iterator();
		while (egs.hasNext())
			egs.next().face=null;
		
		// have to look out for bdry components; each gets an ideal face
		//   mark via temporary negative indices, update them later
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
			Iterator<Face> ifit=tmpIdealFaces.iterator();
			while (ifit.hasNext()) {
				Face idealface=ifit.next();
				idealface.faceIndx=-faceCount+idealface.faceIndx; // convert tmp index, make negative
				tmpFaceList.add(idealface);
			}
			faceCount +=idealIndx; // 'faceCount' counts both regular and ideal faces
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
					ArrayList<HalfEdge> fflower=myedge.origin.getEdgeFlower(); 
					int n=fflower.size();
					int k=fflower.indexOf(myedge);
					
					// go counterclockwise
					for (int j=1;j<n;j++) {
						HalfEdge he=fflower.get((j+k)%n);
						
						// should he.face be used in layout? 
						if (!tmpIdealFaces.contains(he.face) && 
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
						if (!tmpIdealFaces.contains(he.face) && 
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
	 * 'alpha' is a designed 'HalfEdge' used for various
	 * normalizations. It should have an interior origin
	 * whose circle is put at the origin. Checking given e
	 * first to see if it is suitable.
	 * @param e HalfEdge, generally will be null
	 * @return Halfedge, null on error or none found
	 */
	public HalfEdge chooseAlpha(HalfEdge e) {
		if (e!=null && vertIsBdry(e.origin)==null)
			return e;
		Iterator<HalfEdge> eit=tmpEdges.iterator();
		while (eit.hasNext()) {
			HalfEdge he=eit.next();
			if (vertIsBdry(he.origin)==null)
				return he;
		}
		return null;
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
		setVertCenter(edge,new Complex(0.0));
		double invd=getInvDist(edge);
		double dist=CommonMath.inv_dist_edge_length(r0,r1,invd,p.hes);
		if (p.hes>0)
			setVertCenter(edge.next,new Complex(0.0,dist));
		else {
			if (p.hes<0) {
				double expdist=Math.exp(dist);
				dist=(expdist-1.0)/(expdist+1.0);
			}
			setVertCenter(edge.next,new Complex(dist,0.0));
		}
		CircleSimple cS=d_compOppCenter(edge);
		setVertCenter(edge.next.next,cS.center);
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
	 * Use 'computeOrder' to compute circle centers, laying base face
	 * first, then the rest. Note that some circles get laid down more
	 * than once, so last position is what is stored in 'PackData' for now.
	 * TODO: for more accuracy, average all computations of center that are
	 * available: Ides: make all centers null so we can see which are set.
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
			setVertCenter(he.next.next,cs.center);
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
				p.rData[v].center=cs.center;
				StringBuilder strbld=new StringBuilder("disp -tf "+v);
				CommandStrParser.jexecute(p,strbld.toString());
			}
			
		}
		
		// final pass around red chain: set 'PackData' centers at first hits
		RedHEdge rtrace=redChain;
		do {
			rtrace.myEdge.origin.util=0;
			rtrace=rtrace.nextRed;
		} while (rtrace!=redChain);
		do {
			if (rtrace.myEdge.origin.util==0) {
				int v=rtrace.myEdge.origin.vertIndx;
				p.rData[v].center=new Complex(rtrace.center);
				rtrace.myEdge.origin.util=1;
			}
			rtrace=rtrace.nextRed;
		} while (rtrace!=redChain);
		
		return count;
	}
		
	/**
	 * List of faces used in order to compute circle
	 * centers. The first the 'alpha' edge has its 'origin'
	 * vertex centered at the origin, its other end on the
	 * positive real axis. Taking faces in turn (order is
	 * that created in 'simpleLayout' for example), the next
	 * face should have two of its vertices in place and we
	 * compute the third. 
	 * Note: This is for euclidean packings and radii are
	 * already computed.
	 * @param faceorder ArrayList<Face>
	 * @return int, count (should be 'vertCount').
	 */
	public int dcelCompCenters(ArrayList<Face> faceorder) {
		
		// lay out the first face
		Face face=faceorder.get(0);
		int v=face.edge.origin.vertIndx;
		int u=face.edge.twin.origin.vertIndx;
		int w=face.edge.next.twin.origin.vertIndx;
		
		// v at origin
	    double rv=p.rData[v].rad;
	    p.setCenter(v,0.0,0.0);
	    
	    // u on x-axis
	    double ru=p.rData[u].rad;
	    double ovlp=1.0; // default overlap (tangency)
	    p.setCenter(u,Math.sqrt(rv*rv+ru*ru+2*rv*ru*ovlp),0.0);
	    int count=1;
	    
	    // find center for w
	    CircleSimple sc=EuclMath.e_compcenter(p.rData[v].center,p.rData[u].center,
	    		rv,ru,p.rData[w].rad);
	    p.setCenter(w, sc.center.x,sc.center.y);
	    
	    // now layout by face
	    Iterator<Face> foi=faceorder.iterator();
	    while (foi.hasNext()) {
	    	face=foi.next();
	    	v=face.edge.origin.vertIndx;
			u=face.edge.twin.origin.vertIndx;
			w=face.edge.next.twin.origin.vertIndx;
		    rv=p.rData[v].rad;
		    ru=p.rData[u].rad;
		    
		    // find location for w
		    sc=EuclMath.e_compcenter(p.rData[v].center,p.rData[u].center,
		    		rv,ru,p.rData[w].rad);
		    p.setCenter(w, sc.center.x,sc.center.y);
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
		RedHEdge trace=null;
		D_SideData epair=null;
	  
		if (pairLink==null || n<0 || n>=pairLink.size() 
				|| (epair=(D_SideData)pairLink.get(n))==null
				|| (trace=epair.startEdge)==null)  // epair.startEdge.hashCode();epair.startEdge.nextRed.hashCode();
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
 	 * NEEDED FOR CIRCLEPACK
	 * Add barycenters faces, listed by indices. A barycenter is a
	 * new vertex inside the face which is connected to all the 
	 * bdry vertices of the face. 
	 * NOTE: added vertices and edges put data out of sync with 
	 * parent packing 'p'. For testing purposes, we can write combinatorics
	 * to a file and read it back into a packing to get things back into sync.
	 * @param facelink FaceLink; if null, do all faces (but not ideal faces)
	 * @return int count, 0 on error
	 */
	public int addBaryCenters(FaceLink facelink) {
		ArrayList<Face> arrayf=new ArrayList<Face>();
		if (facelink==null) {
			for (int j=1;j<=intFaceCount;j++)
				arrayf.add(tmpFaceList.get(j)); // get face
		}
		else {
			Iterator<Integer> flst=facelink.iterator();
			while (flst.hasNext()) {
				arrayf.add(tmpFaceList.get(flst.next())); // get face of that index
			}
		}
		return addBaryCenters(arrayf);
	}
	
	/**
	 * Add barycenters to faces. A barycenter is a
	 * new vertex inside the face which is connected to all the 
	 * bdry vertices of the face. 
	 * NOTE: added vertices and edges put data out of sync with 
	 * parent packing 'p'. For testing purposes, we can write combinatorics
	 * to a file and read it back into a packing to get things back into sync.
	 * @param arrayf ArrayList<Face>
	 * @return int count, 0 on error
	 */
	public int addBaryCenters(ArrayList<Face> arrayf) {
		int count=0;
		int oldVertCount=vertCount;
		Iterator<Face> flst=arrayf.iterator();
		ArrayList<Vertex> newVertices=new ArrayList<Vertex>();
		while (flst.hasNext()) {
			Face face=flst.next(); 
			int n=0;
			ArrayList<HalfEdge> polyE=null;
			
			// note: after processing 'face', set 'face.edge=null' so we don't process 
			//   it again due to repeat in 'facelist'
			if (face.edge!=null && (polyE=face.getEdges())!=null && 
					(n=polyE.size())>2) { 
				
				Vertex newV=new Vertex(); // this is the barycenter
				ArrayList<HalfEdge> edgeflower=new ArrayList<HalfEdge>();
				Iterator<HalfEdge> pE=polyE.iterator();
				while (pE.hasNext()) {
					HalfEdge nextHE=pE.next();

					// new spoke from 'newV'
					HalfEdge he=new HalfEdge(newV);
					he.edgeIndx=++edgeCount;
					edgeflower.add(he);
					he.twin=new HalfEdge(nextHE.origin);
					he.twin.edgeIndx=++edgeCount;
					he.twin.twin=he;
					tmpEdges.add(he); // add both to parent array
					tmpEdges.add(he.twin);
				}	
				
				// set 'edge' null to avoid reuse
				face.edge=null;
				tmpIdealFaces.remove(face); // a bdry face becomes interior
				
				// fix up 'newV'
				count++;
				newV.vertIndx=++vertCount;
				newV.halfedge=edgeflower.get(0);
				newVertices.add(newV);
				
				// fix up halfedges and new faces in order around 'newV'
				for (int j=0;j<n;j++) {
					HalfEdge polye=polyE.get(j);
					HalfEdge spoke=edgeflower.get(j);
					HalfEdge nxt_spoke=edgeflower.get((j+1)%n);
					
					// fix polye
					polye.prev=spoke;
					polye.next=nxt_spoke.twin;
					
					// fix spoke
					spoke.next=polye;
					spoke.prev=nxt_spoke.twin;
					
					// fix nxt_spoke.twin
					nxt_spoke.twin.prev=polye;
					nxt_spoke.twin.next=spoke;
					
				}
			}
		} // end of while through facelist
		
		// re-establish 'vertices'
		Vertex []newarray=new Vertex[vertCount+1];
		for (int v=1;v<=oldVertCount;v++)
			newarray[v]=vertices[v];
		int tick=oldVertCount;
		Iterator<Vertex> vit=newVertices.iterator();
		while (vit.hasNext()) 
			newarray[++tick]=vit.next();
		vertices=newarray;
		vertCount=tick;
		
		// reindex all the faces
		indexFaces(tmpEdges,getBdryEdges());
		return count;
	}

	/**
 	 * NEEDED FOR CIRCLEPACK
	 * Flip specified interior edges. In a triangulation, an interior edge
	 * is shared by two faces, and to "flip" it means to remove it and
	 * replace it with the other diagonal in the union of those faces. The
	 * number of faces, edges, and vertices is not changed, but we are out
	 * of sync with parent 'p'. 
	 * Note: calling routine must ensure there are no repeats in 'flippers'.
	 * @param flippers ArrayList<HalfEdge>, edges to be flipped
	 * @return int count
	 */
	public int flipEdges(EdgeLink elist) {

		ArrayList<HalfEdge> flipthese=new ArrayList<HalfEdge>();
		Iterator<EdgeSimple> eit=elist.iterator();
		while (eit.hasNext()) {
			EdgeSimple edge=eit.next();
			HalfEdge he=null;
			if ((he=findHalfEdge(edge.v,edge.w))!=null)
				flipthese.add(he);
		}
		return flipEdges(flipthese);
	}
	
	/**
	 * Flip the specified edges. In a triangulation, an interior edge is
	 * shared by two faces. To "flip" the edge means to remove it and
	 * replace it with the other diagonal in the union of those faces. The
	 * number of faces, edges, and vertices is not changed, but we are out
	 * of sync with parent 'p'.  
	 * Note: calling routine must ensure there are no repeats in 'flippers'.
	 * @param flippers ArrayList<HalfEdge>, edges to be flipped
	 * @return int count
	 */
	public int flipEdges(ArrayList<HalfEdge> flippers) {
		int count=0;
		Iterator<HalfEdge> lst=flippers.iterator();
		while (lst.hasNext()) {
			HalfEdge he=lst.next();

			if (!edgeIsBdry(he)) {
				Face leftf=he.face;
				Face rightf=he.twin.face;
				Vertex leftv=he.next.twin.origin;
				Vertex rightv=he.twin.next.twin.origin;
				
				// save some info for later
				HalfEdge hn=he.next;
				HalfEdge hp=he.prev;
				HalfEdge twn=he.twin.next;
				HalfEdge twp=he.twin.prev;
				
				// have to make sure ends don't have old 'halfedge's
				if (he.origin.halfedge==he)
					he.origin.halfedge=he.twin.next;
				if (he.twin.origin.halfedge==he.twin)
					he.twin.origin.halfedge=he.next;
				
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
					if (!tmpIdealFaces.contains(face)) // omit ideal faces
						arrayF.add(face);
				}
				ArrayList<HalfEdge> eflower=vert.getEdgeFlower();
				Iterator<HalfEdge> eit=eflower.iterator();
				while (eit.hasNext()) {
					HalfEdge he=eit.next();
					int w=he.twin.origin.vertIndx;
					if (v<w || vhits[w]==0) // this avoid repeats
						arrayE.add(he);
				}
			}
		}
		
		// add barycenters to faces  
		int n=addBaryCenters(arrayF);
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
			if (edgeIsBdry(he)) 
				arrayBdry.add(he);
			else 
				arrayInt.add(he);
		}
		
		// remove the bdry edges and their faces
		heit=arrayBdry.iterator();
		while (heit.hasNext()) {
			HalfEdge he=heit.next();
			if (tmpIdealFaces.contains(he.twin.face))
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
			
			tmpEdges.remove(he);
			tmpEdges.remove(he.twin);
		}				

		// we flip the interior ones
		n=flipEdges(arrayInt);
		indexFaces(tmpEdges,getBdryEdges());
		
		return n;
	}

	/**
 	 * NEEDED FOR CIRCLEPACK
	 * Cookie out a subcomplex based on the list of 
	 * vertices to be included.
	 * @param vlist NodeLink
	 * @return int count
	 */
	public int cookie(NodeLink vlist) {
		
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
		
		return cookie(arrayV);
	}

	/**
	 * Cookie out a subcomplex based on the list of 
	 * vertices to be included.
	 * @param vlist NodeLink
	 * @return int count
	 */
	public int cookie(ArrayList<Vertex> arrayV) {
		
		CirclePack.cpb.errMsg("OBE: should use 'RedDCELBuilder'");
		return 0;
		
/*		
		// We will need to identify new boundary edges later.
		//   We will do this via the bdry vertices; prepare by ensuring
		//   that for each original bdry edge, its 'origin' points to 
		//   its twin. As we detach edges later, their vertices will 
		//   have 'halfedge' pointing to new bdry edge's twin.
		ArrayList<HalfEdge> tmpbdry=getBdryEdges();
		Iterator<HalfEdge> tit=tmpbdry.iterator();
		while (tit.hasNext()) {
			HalfEdge he=tit.next();
			he.twin.origin.halfedge=he.twin;
		}
		
		// form array with included vertices
		int []vhits=new int[vertCount+1];
		Iterator<Vertex> vit=arrayV.iterator();
		while (vit.hasNext()) {
			vhits[vit.next().vertIndx]=1; // included vertex
		}
		
		// Make new edge list by going through 'edges' and
		//   removing any edge with one or both ends excluded
		//   and adding any resulting new bdry edges.
		ArrayList<HalfEdge> newedges=new ArrayList<HalfEdge>();
		Iterator<HalfEdge> eit=edges.iterator();
		while (eit.hasNext()) {
			HalfEdge he=eit.next();
			
			if (he.next!=null) { // this edge hasn't been detached
				int v=he.origin.vertIndx;
				int w=he.twin.origin.vertIndx;
			

				// at least one end excluded? 
				if (vhits[v]==0 || vhits[w]==0) {
					// first, included vertices lead to new bdry edges
					if (vhits[v]==1)
						tmpbdry.add(he.prev);
					if (vhits[w]==1)
						tmpbdry.add(he.twin.prev);
					
					// now 'detach', so nothing points to he
					he.detach();
				}
				else  // this is a keeper
					newedges.add(he);
			}
		} // end of while through 'edges'
		
		// prune any dangling edges; multiple passes may be needed
		boolean ahit=true;
		while (ahit) {
			ahit=false;
			eit=newedges.iterator();
			while (eit.hasNext()) {
				HalfEdge he=eit.next();
				if (he.next==he.twin || he.twin.next==he) { // folds back on self?
					he.detach();
					ahit=true;
					newedges.remove(he);
					newedges.remove(he.twin);
					break;
				}
			}
		}
		
		// we now have the new 'edges'
		edges=newedges;
		
		// define new 'vertices' and 'newOld' map
		int newvertcount=0;
		int []vkept=new int[vertCount+1];
		newOld=new VertexMap();
		ArrayList<Vertex> newvertices=new ArrayList<Vertex>();
		Iterator<HalfEdge> nit=edges.iterator();
		while (nit.hasNext()) {
			HalfEdge he=nit.next();
			Vertex vert=he.origin;
			if (vkept[vert.vertIndx]==0) { // avoid repeats
				newOld.add(new EdgeSimple(++newvertcount,vert.vertIndx));
				vkept[vert.vertIndx]=newvertcount; //
				newvertices.add(vert);
			}
		}
		
		// reset vertex indices and put in new array
		Vertex []newv=new Vertex[newvertcount+1];
		vit=newvertices.iterator();
		while (vit.hasNext()) {
			Vertex vert=vit.next();
			vert.vertIndx=vkept[vert.vertIndx];
			newv[vert.vertIndx]=vert;
		}
		
		// we now have our new data
		vertCount=newvertcount;
		vertices=newv;
		

		// find new bdry by looking through origins of tmpbdry edges
		ArrayList<HalfEdge> newbdry=new ArrayList<HalfEdge>();
		tit=tmpbdry.iterator();
		while (tit.hasNext()) {
			HalfEdge he=tit.next();
			Vertex vert=he.origin;
			if (vkept[vert.vertIndx]>0) 
				newbdry.add(vert.halfedge.twin);
		}
		
		// establish new faces, face indices, new 'bdryFaces'
		return indexFaces(edges,newbdry);
	}
*/

	} // end of OBE 'cookie'


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
	 * Create a traditional packing from this DCEL; this is a
	 * raw packing and the calling routine must further process it.
	 * @return PackData, null on error
	 */
	public PackData getPackData() {
		PackData pdata=new PackData(null);
		pdata.alloc_pack_space(vertCount+100,true);
		// sphere? (note: 'faces' starts with 'null' entry)
		if ((vertCount-tmpEdges.size()+(tmpFaceList.size()-1))==2) 
			pdata.hes=1; 
		else 
			pdata.hes=0;
		pdata.status=true;
		pdata.nodeCount=vertCount;
		
		int [][]bouquet=getBouquet();
		for (int v=1;v<=vertCount;v++) {
			pdata.kData[v]=new KData();
			pdata.kData[v].flower=bouquet[v];
			int num=pdata.kData[v].flower.length;
			pdata.kData[v].num=num-1;
			if (pdata.kData[v].flower[0]!=pdata.kData[v].flower[num-1]) // v bdry
				pdata.kData[v].bdryFlag=1;
			pdata.rData[v]=new RData();
			pdata.rData[v].center=new Complex(0.0);
			pdata.rData[v].rad=.5;
		}
		
		// vertexMap?
		if (newOld!=null)
			pdata.vertexMap=newOld.makeCopy();
		
		// calling routine needs to organize
		return pdata;
	}
	
	/**
	 * Based on current 'bdryFaces', find all bdry edges
	 * @return ArrayList<HalfEdge>
	 */
	public ArrayList<HalfEdge> getBdryEdges() {
		ArrayList<HalfEdge> bdryedges=new ArrayList<HalfEdge>();
		Iterator<Face> bit=tmpIdealFaces.iterator();
		while (bit.hasNext()) {
			HalfEdge he=bit.next().edge;
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
	 * @return Edgelink, null on error
	 */
	public EdgeLink getDualEdges(boolean ideal) {
		EdgeLink elink=new EdgeLink();
		Iterator<Face> fit=tmpFaceList.iterator();
		fit.next();  // toss first 'null' entry
		while(fit.hasNext()) {
			Face f=fit.next();
			HalfEdge edge=f.edge;
			HalfEdge nxtedge=edge;
			do {
				Face tf=nxtedge.twin.face;
				if ((ideal || !tmpIdealFaces.contains(tf)) && 
						Math.abs(tf.faceIndx)>Math.abs(f.faceIndx))
					elink.add(new EdgeSimple(f.faceIndx,tf.faceIndx));
				nxtedge=nxtedge.next;
			} while (nxtedge!=edge);
		}
		return elink;
	}
	
	/**
	 * Find the official rad/cent for the origin of the 
	 * given 'HalfEdge'. If the origin is 'RedVertex', 
	 * then look clw for first 'RedHEdge'. If normal 
	 * 'Vertex', then data is stored in 'PackData'. 
	 * @return CircleSimple
	 */
	public CircleSimple getVertData(HalfEdge edge) {
		// is itself a 'RedHEdge'?
		if (edge.myRedEdge!=null)
			return edge.myRedEdge.getData();
		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'?
		if (!(vert instanceof RedVertex)) {
			return vert.getData();
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
	
	public double getVertRadius(HalfEdge edge) {
		// is itself a 'RedHEdge'? Note, also set in 'PackData'
		if (edge.myRedEdge!=null) {
			return edge.myRedEdge.getRadius();
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'? set in 'PackData'
		if (!(vert instanceof RedVertex)) {
			return vert.getRadius();
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
	
	public Complex getVertCenter(HalfEdge edge) {
		// is itself a 'RedHEdge'? Note, also set in 'PackData'
		if (edge.myRedEdge!=null) {
			return edge.myRedEdge.getCenter();
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'? set in 'PackData'
		if (!(vert instanceof RedVertex)) {
			return vert.getCenter();
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
	 * Set the center in 'PackData', 'origin' and in the appropriate
	 * 'RedHEdge' if 'origin' is a 'RedVertex'.
	 * @param edge HalfEdge
	 * @param z
	 */
	public void setVertCenter(HalfEdge edge,Complex z) {
		Vertex vert=edge.origin;

		// is itself a 'RedHEdge'? Note, also set in 'PackData'
		if (edge.myRedEdge!=null) {
			edge.myRedEdge.setCenter(z);
			vert.setCenter(z);
			p.rData[vert.vertIndx].center=new Complex(z);
			return;
		}
		
		// is a normal 'Vertex'? set in 'PackData'
		if (!(vert instanceof RedVertex)) {
			vert.setCenter(z);
			p.rData[vert.vertIndx].center=new Complex(z);
			return;
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) {
				he.myRedEdge.setCenter(z);
				he.origin.setCenter(z);
				p.rData[he.origin.vertIndx].center=new Complex(z);
				he=edge; // kick out
			}
		} while (he!=edge);
	}

	public void setVertRadius(HalfEdge edge,double rad) {
		// is itself a 'RedHEdge'?
		if (edge.myRedEdge!=null) {
			edge.origin.setRadius(rad);
			edge.myRedEdge.setRadius(rad);
			p.rData[edge.origin.vertIndx].rad=rad;
			return;
		}

		Vertex vert=edge.origin;
		
		// is a normal 'Vertex'?
		if (!(vert instanceof RedVertex)) {
			vert.setRadius(rad);
			p.rData[vert.vertIndx].rad=rad;
			return;
		}
		
		// else, go clw to reach a spoke that has 'myRedEdge'
		HalfEdge he=edge;
		do {
			he=he.twin.next;
			if (he.myRedEdge!=null) {
				he.origin.setRadius(rad);
				he.myRedEdge.setRadius(rad);
				p.rData[he.origin.vertIndx].rad=rad;
			}
		} while (he!=edge);
	}
	
	public void setVertData(HalfEdge edge,CircleSimple cS) {
		setVertCenter(edge,cS.center);
		setVertRadius(edge,cS.rad);
	}

	public double getInvDist(HalfEdge edge) {
		if (!p.overlapStatus)
			return 1.0;
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		return p.kData[v].overlaps[p.nghb(v,w)];
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
			if (tmpIdealFaces!=null && tmpIdealFaces.contains(nxtedge.twin.face))
				return nxtedge;
			nxtedge=nxtedge.prev.twin;
		} while(nxtedge!=v.halfedge);
		return null;
	}
		
	/**
	 * Is this a boundary edge?
	 * @param he HalfEdge
	 * @return boolean
	 */
	public boolean edgeIsBdry(HalfEdge he) {
		if (tmpIdealFaces.contains(he.face))
			return true;
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
	 * @return int face count, 0 on error
	 */
	public int syncFaceData() {
		
		// minimal check on compatibility
		if (p.nodeCount!=vertCount)
			return 0;
		p.faceCount=(tmpFaceList.size()-1)-tmpIdealFaces.size(); 
		p.faces=new komplex.Face[p.faceCount+1]; // new face structure
		Iterator<Face> flst=tmpFaceList.iterator();
		flst.next(); // toss first 'null' entry
		int tick=0;
		while (flst.hasNext()) {
			Face face=flst.next();
			if (!tmpIdealFaces.contains(face)) {
				face.faceIndx=++tick; // reset indices (perhaps already in order)
				// for new komplex.Face
				p.faces[tick]=new komplex.Face();
				p.faces[tick].vert=face.getVerts();
				p.faces[tick].vertCount=p.faces[tick].vert.length;
				p.faces[tick].indexFlag=0;
			}
		}
		
		if (tmpLayout!=null) 
			p.firstFace=tmpLayout.get(0).faceIndx;
		// TODO: could transfer layout order to parent 'p' too.
		return tick;
	}
	
	/**
 	 * NEEDED FOR CIRCLEPACK
	 * Return the traditional type of 'flower', that is, the 
	 * list of petal indices about v. This is closed if v is an interior 
	 * vertex. Return null on error (e.g., more than two bdry edges from v). 
	 * (This routine is helpful to connect new routines with the old ones.)
	 * @param v Vertex
	 * @return int[], null on error
	 */
	public int []usualFlower(Vertex v) {
		ArrayList<Integer> petals=new ArrayList<Integer>();
		HalfEdge nxtedge=v.halfedge;
		boolean bdry=false;
		if (tmpIdealFaces.contains(nxtedge.twin.face))
			bdry=true;
		int safety=vertCount;
		do {
			petals.add(nxtedge.twin.origin.vertIndx);
			nxtedge=nxtedge.prev.twin;
			safety--;
		} while (safety>0 && nxtedge!=v.halfedge);
		if (safety==0)
			throw new CombException("usualFlower, unending loop, vert "+v.vertIndx);
		if (!bdry)
			petals.add(petals.get(0));
		int n=petals.size();
		int []rslt=new int[n];
		for (int k=0;k<n;k++)
			rslt[k]=petals.get(k);
		return rslt;
	}
	
	public void debug() {
		System.out.println("PackDCEL debug: edges with null twins");
		
		Iterator<HalfEdge> eit=tmpEdges.iterator();
		while (eit.hasNext()) {
			HalfEdge he= eit.next();
			if (he.twin==null)
				System.err.println(he.origin.vertIndx);
		}
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
		int []flower=bouquet[v];
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
			Complex p0=new Complex(p.rData[verts[0]].center);
			Complex p1=new Complex(p.rData[verts[1]].center);
			Complex p2=new Complex(p.rData[verts[2]].center);
			return PackData.face_center(p.hes,p0,p1,p2);
		}
		Complex accum=new Complex(p.rData[verts[0]].center);
		for (int j=1;j<verts.length;j++)
			accum=accum.plus(p.rData[verts[j]].center);
		return accum.divide(verts.length);
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
		Iterator<Face> fit=tmpFaceList.iterator();
		Face face=fit.next(); // flush first null face
		while (fit.hasNext()) {
			face=fit.next();
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
		
		PackDCEL qdcel = new PackDCEL(bouquet);
		
		// set centers of the new vertices
		fit=tmpFaceList.iterator();
		face=fit.next(); // flush first null face
		while (fit.hasNext()) {
			face=fit.next();
			if (face.faceIndx>0) // ignore ideal faces
				qdcel.vertices[face.faceIndx].center=faceOppCenter(face);
		}
		
		return qdcel;
	}
	
	/**
	 * Write this DCEL structure to a file.
	 * TODO: This is an early format, 4/2017, and should
	 * probably be rethought, but need if for 3D modeling work 
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
	    		Complex z=v.center;
	    		if (p!=null) 
	    			z=p.rData[v.vertIndx].center;
	    		int xi=(int)Math.round(z.x*1000000.0); // convert to microns
	    		int yi=(int)Math.round(z.y*1000000.0); // convert to microns
	    		fp.write(i+"  "+xi+" "+yi+" 1000 0 \n"); // radius is fake, flag is fake
	    	}
	    	fp.write("</VERTICES>\n");
	    	
	    	// first, some bookkeeping
	    	edgeCount=tmpEdges.size();
	    	HalfEdge []edgearray=new HalfEdge[edgeCount+1];
	    	int eindx=0;
	    	Iterator<HalfEdge> eit=tmpEdges.iterator();
	    	while(eit.hasNext()) {
	    		HalfEdge he=eit.next();
	    		he.edgeIndx=++eindx;
	    		edgearray[he.edgeIndx]=he;
	    	}
	    	
	    	int []eticks=new int[edgeCount];
	    	int ecount=0;
	    	for (int e=1;e<=edgeCount;e++) {
	    		HalfEdge he=edgearray[e];
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
	    	for (int e=0;e<edgeCount;e++) 
	    		if (eticks[e]>0) {
	    			HalfEdge he=edgearray[e+1];
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
		Iterator<HalfEdge> deit=tmpEdges.iterator();
		while (deit.hasNext()) {
			HalfEdge he=deit.next();
			if (he!=null)
				dedges[he.edgeIndx-1]=he;
		}
				
		// dual vertices are regular faces
		Iterator<Face> dvit=tmpFaceList.iterator();
		Face face=dvit.next(); // flush the first, which is null
		while (dvit.hasNext()) {
			face=dvit.next();
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
	    		
	    		face=dverts[i].face;
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

