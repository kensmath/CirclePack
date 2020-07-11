package dcel;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import allMains.CirclePack;
import complex.Complex;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import geometry.EuclMath;
import geometry.CircleSimple;
import input.CPFileManager;
import komplex.EdgeSimple;
import komplex.KData;
import komplex.Triangulation;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import packing.RData;

/** The "DCEL" is a common way that computer scientists 
 * encode graphs; it's also called a 'HalfEdge' structure.
 * At the suggestion of John Bowers, I may incorporate this 
 * in CirclePack; it should only effect the combinatorics.
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
	public int euler;           // euler characteristic of surface
	
	public Vertex []vertices; // indexed from 1 
	public ArrayList<HalfEdge> edges;
	public ArrayList<Face> faces; // indexed from 1 (first entry 'null')
	public ArrayList<Face> idealFaces; // "ideal" faces
	public ArrayList<Face> LayoutOrder; // order for computing centers
	public VertexMap newOld; // NEEDED FOR CIRCLEPACK
	public RedHEdge redChain; // doubly-linked, cclw edges about a fundamental region
	boolean debug;
	public HalfEdge alpha;
	
	// Constructor(s)
	public PackDCEL() {
		p=null;
		vertCount=0;
		vertices=null;
		edges=null;
		faces=null;
		idealFaces=null;
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
		euler=createDCEL(bouquet); 
		alpha=chooseAlpha(null);
		try {
			LayoutOrder=simpleLayout();
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
		euler=createDCEL(bouquet);
		alpha=chooseAlpha(null);
		LayoutOrder=simpleLayout();
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

		euler=createDCEL(bouquet);
	}		
	
	/**
	 * Use traditional combinatoric data alone to create a
	 * DCEL structure based on array of flowers (with usual
	 * counterclockwise order, indexed contiguously from 1, 
	 * bdry/interior flower conventions). For bdry vertices,
	 * 'HalfEdge' should have 'twin.face' which is ideal face.
	 * Return the Euler Characteristic (including ideal faces).
	 * @param bouguet int[][]
	 * @return euler characteristic of closed surface 
	 */
	public int createDCEL(int [][]bouquet) {
		edges=new ArrayList<HalfEdge>();
		ArrayList<HalfEdge> bdryedges=new ArrayList<HalfEdge>();
		idealFaces=new ArrayList<Face>(); // bdry face list, may be empty
		
		vertCount=bouquet.length-1;
		vertices=new Vertex[vertCount+1];
		int degreecount=0;
		edgeCount=0;
		int facecount=0;
		
		// create arrays of 'HalfEdge's for all vertices
		HalfEdge [][]heArrays=new HalfEdge[vertCount+1][];
		
		// create new 'Vertex's and array of 'HalfEdge's
		for (int v=1;v<=vertCount;v++) {
			int []flower=bouquet[v];
			int count=flower.length;
// debug			
//			System.err.println("vert "+v+", count "+count);
			if (flower[0]==flower[count-1]) // v interior
				count--;
			degreecount+=count; // add in the degree of this vert
			if (count==0)
				throw new CombException("bad count for vertex "+v);
			heArrays[v]=new HalfEdge[count];

			// create the new 'Vertex' and first edge
			Vertex newV=new Vertex();
			newV.vertIndx=v;
			heArrays[v][0]=new HalfEdge(newV);
			heArrays[v][0].edgeIndx=++edgeCount;
			newV.halfedge=heArrays[v][0];
			vertices[v]=newV;
			edges.add(heArrays[v][0]);
			
			// create the rest of outgoing edges
			for (int k=1;k<count;k++) {
				heArrays[v][k]=new HalfEdge(newV);
				heArrays[v][k].edgeIndx=++edgeCount;
				edges.add(heArrays[v][k]);
			}
		} // end of for loop on 'vertCount'
		
		// establish all twins (using info in 'bouquet')
		for (int v=1;v<=vertCount;v++) {
			int []flower=bouquet[v];
			int count=flower.length;
			if (flower[0]==flower[count-1]) // v interior
				count--;
			for (int k=0;k<count;k++) {
				int w=flower[k];
				int indx_wv=nghb(w, v,bouquet);
				if (indx_wv<0) {
					CirclePack.cpb.errMsg("Error: missing edge in 'CreateDCEL'");
				}
				heArrays[v][k].twin=heArrays[w][indx_wv];
			}
		}
		
		// establish next/prev
		for (int v=1;v<=vertCount;v++) {
			int []flower=bouquet[v];
			int count=flower.length;
			if (flower[0]==flower[count-1]) // v interior
				count--;
			for (int k=0;k<count;k++) {
				HalfEdge he=heArrays[v][k];
				int w=flower[k];
				
				// prev = twin of cclw edge
				int m=(k+1)%count;
				he.prev=heArrays[v][m].twin;
				
				// next = edge from w clw to edge to v
				m=nghb(w, v,bouquet);
				int []wflower=bouquet[w];
				int wcount=wflower.length;
				if (wflower[0]==wflower[wcount-1]) // w interior
					wcount--;
				m=(m-1+wcount)%wcount; // clockwise
				he.next=heArrays[w][m];
			}
		}
		
		// find bdryedges (use criteria in 'bouquet' --- flower not closed)
		for (int v=1;v<=vertCount;v++) {
			int []flower=bouquet[v];
			if (flower[0]!=flower[flower.length-1]) 
				bdryedges.add(heArrays[v][0].twin);
		}
		
		// Establish faces, face indices, 'bdryFaces'
		facecount=indexFaces(edges,bdryedges);
		if (facecount<=0)
			throw new CombException("Failed to form packDCEL; 'facecount' negative");
		
		if ((degreecount & 1)!=0 || (edgeCount & 1)!=0 || degreecount!=edgeCount) // one/both are odd
			throw new CombException("Failed to form packDCEL; 'degreeCount' and 'edgecount' diff or are odd");
		
		// return euler characteristic
		return (vertCount-(edgeCount/2)+facecount);
		
	} // end of createDCEL 
	
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
		ArrayList<Vertex> arrayV=new ArrayList<Vertex>();
		for (int n=1;n<=vertCount;n++)
			if (vhits[n]==1) 
				arrayV.add(vertices[n]);
		
		return redCookie(arrayV);
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
	public PackDCEL redCookie(ArrayList<Vertex> arrayV) {
		
		// we're building a new 'PackDCEL' (without parent p)
		//   and new 'vertices', 'edges', 'faces', 'bdryFaces'
		PackDCEL pdcel=new PackDCEL();
		int [][]bouquet=getBouquet();
		euler=pdcel.createDCEL(bouquet);
		
		// do we have a stand in for alpha?
		int alpIndx=-1;
		if (alpha!=null) {
			alpIndx=alpha.origin.vertIndx;
			if (alpIndx<=0 || alpIndx>pdcel.vertCount)
				alpIndx=-1;
		}

		int[] keepV = new int[pdcel.vertCount + 1];
		if (arrayV != null) {
			Iterator<Vertex> ait = arrayV.iterator();
			while (ait.hasNext()) {
				int an=ait.next().vertIndx;
				if (an>0 && an<=pdcel.vertCount)
					keepV[an] = 1;
			}
		} 
		else // keep all
			for (int k = 1; k <= pdcel.vertCount; k++)
				keepV[k] = 1;

		// prepare various lists
		int[] doneV = new int[vertCount + 1]; // -1=touched; 1=engulfed
		int[] doneF = new int[faces.size()]; // 1=placed
		
		// Or processing leads to a drawing order. Until we have faces,
		//   however, we keep a tmp list of 'HalfEdge's until the 
		//   faces are indexed at the end.
		ArrayList<HalfEdge> orderEdges=new ArrayList<HalfEdge>(); 
		
		// mark ideal faces as done
		Iterator<Face> fit = pdcel.idealFaces.iterator();
		while (fit.hasNext())
			doneF[Math.abs(fit.next().faceIndx)] = 1;

		// find first interior keeper with petal keepers
		ArrayList<Integer> keepX=new ArrayList<Integer>();
		for (int k=1;k<=pdcel.vertCount;k++)
			if (keepV[k]>0)
				keepX.add(k);
		
		// can we set 'alpha'
		if (alpIndx>0) {
			keepX.add(0,alpIndx); // put alpha first in list
			pdcel.alpha=pdcel.vertices[alpIndx].halfedge;
		}
		
		Vertex firstV = null;
		Iterator<Integer> xit=keepX.iterator();
		while (firstV == null && xit.hasNext()) {
			Vertex vert = pdcel.vertices[(int)xit.next()];
			int v = vert.vertIndx;
			if (pdcel.vertIsBdry(vert) == null && keepV[v] == 1) {
				ArrayList<HalfEdge> spokes = vert.getEdgeFlower();
				boolean good = true;
				Iterator<HalfEdge> sit = spokes.iterator();
				while (sit.hasNext() && good)
					if (keepV[sit.next().origin.vertIndx] == 0)
						good = false;
				if (good) { // success
					firstV = vert;
				}
			}
		}
		if (firstV == null)
			throw new CombException("no appropriate keeper vertex");

		// get redchain started at firstV
		doneV[firstV.vertIndx] = 1; // engulfed
		ArrayList<HalfEdge> spokes = firstV.getEdgeFlower();
		ArrayList<RedHEdge> redchain = new ArrayList<RedHEdge>();
		for (int k = 0; k < spokes.size(); k++) {
			HalfEdge he = spokes.get(k);
			redchain.add(new RedHEdge(he.next));
			he.face.edge = he;
			doneF[Math.abs(he.face.faceIndx)] = 1; // done with this face
			orderEdges.add(he);
			he.twin.origin.halfedge=he.next;
		}
		
		// interlink the red chain, touch the petal vertices
		int n = redchain.size();
		for (int k = 0; k < redchain.size(); k++) {
			RedHEdge re = redchain.get(k);
			if (debug)
				deBugging.DCELdebug.halfedgeends(pdcel, re.myedge);
			re.prev = redchain.get((k - 1 + n) % n);
			re.next = redchain.get((k + 1) % n);
			doneV[re.myedge.origin.vertIndx] = -1; // touched
			re.myedge.origin.halfedge = re.myedge;
		}

		// ======================= main loop ==============
		// loop through red chain as long as we're adding faces
		
		pdcel.redChain=redchain.get(0); // must always point to a link
		if (debug)
			deBugging.DCELdebug.printRedChain(pdcel);
		RedHEdge currRed=null;
		boolean hit = true; // true if we got a hit this time around
		while (hit && pdcel.redChain!=null) {
			hit = false;
			
			// current count of red chain as stop mechanism
			int redN=0;
			RedHEdge re=pdcel.redChain;
			do {
				redN++;
				re=re.next;
			} while(re!=pdcel.redChain);
			
			if (redN<=2) {
				pdcel.redChain=null;
				break;
			}
			
			// look at 'redN'+1 successive 'RedHEdge's 
			for (int N=0;N<=redN;N++) {
				
				if (debug) {
					deBugging.DCELdebug.printRedChain(pdcel);
					deBugging.DCELdebug.halfedgeends(pdcel, pdcel.redChain.myedge);
				}
				
				currRed = pdcel.redChain;
				pdcel.redChain = currRed.next; // should be there for next pass

				// check for degeneracy: triangulation of sphere
				if (currRed.next.next == currRed) {
					pdcel.redChain = null;
					break;
				}

				// ****************** main work *****************

				// processing the current red edge
				// note: we set 'halfedge' of red edge 'origin' to red edge
				// 'myedge'
				int v = currRed.myedge.origin.vertIndx;
				if (doneV[v] == -1) {

				// not done, so process fan of faces outside red chain
				HalfEdge upspoke = currRed.prev.myedge.twin;
				HalfEdge downspoke = currRed.myedge;
				ArrayList<HalfEdge> fan = currRed.myedge.origin.getEdgeFlower(upspoke, downspoke);

				// doubling back on itself? then collapse this edge
				if (upspoke == downspoke) {
					upspoke.origin.halfedge=downspoke; // safety measure
					currRed.prev.prev.next = currRed.next;
					currRed.next.prev = currRed.prev.prev;
					doneV[v] = 1;
					currRed.next.myedge.origin.halfedge = currRed.next.myedge;
					hit = true;
				}

				// bdry vertex we're done with?
				else if (pdcel.idealFaces.contains(upspoke.face) && 
						pdcel.idealFaces.contains(downspoke.twin.face)) {
					downspoke.origin.halfedge=downspoke;
					doneV[v] = 1;
				}

				// else we have to look closer at fan
				else {

					// start cclw adding faces as possible
					Iterator<HalfEdge> ffit = fan.iterator();
					boolean goon = true;
					Face lastface = null;
					HalfEdge lastspoke=null;
					ArrayList<RedHEdge> redseg = new ArrayList<RedHEdge>();
					while (ffit.hasNext() && goon) {
						goon = false;
						lastspoke = ffit.next();
						lastface = lastspoke.face;
						Vertex opp = lastspoke.next.twin.origin;

						// face not added? opposite vert included?
						if (doneF[Math.abs(lastface.faceIndx)] == 0 && keepV[opp.vertIndx] == 1) {
							lastface.edge = lastspoke;
							doneF[Math.abs(lastface.faceIndx)] = 1;
							orderEdges.add(lastspoke);
							redseg.add(new RedHEdge(lastspoke.next));
							lastspoke.next.origin.halfedge = lastspoke.next;
							int u = lastspoke.next.origin.vertIndx;
							if (doneV[u] == 0)
								doneV[u] = -1; // touched
							opp.halfedge=lastspoke.next.next;
							goon = true;
						}
					} // end of while cclw through fan

					// did we get something
					if (redseg.size() > 0) {
						RedHEdge firstre=redseg.get(0);
						int m=redseg.size();
						RedHEdge lastre = redseg.get(m - 1);

						// internally link up the new red edges
						for (int kk = 0; kk < (m - 1); kk++) {
							redseg.get(kk).next = redseg.get(kk + 1);
							redseg.get(kk + 1).prev = redseg.get(kk);
						}

						// now link begin of 'redseg' into red chain
						currRed.prev.prev.next = firstre;
						firstre.prev = currRed.prev.prev;
						firstre.myedge.origin.halfedge = firstre.myedge;

						// Typical: bypass 'currRed' and 'currRed.prev'
						if (lastface == downspoke.twin.face) {
							lastre.next = currRed.next;
							currRed.next.prev = lastre;
							doneV[currRed.myedge.origin.vertIndx] = 1;
						}

						// else just get cclw part (so far) ending with 'lastspoke' 
						else {
							RedHEdge newre = new RedHEdge(lastspoke.twin);
							lastre.next = newre;
							newre.prev = lastre;
							newre.next=currRed;
							currRed.prev=newre;
							currRed.myedge.origin.halfedge = currRed.myedge;
						}
						hit = true;
					} // done with cclw

					// now look for clw faces
					redseg = new ArrayList<RedHEdge>();
					HalfEdge nxtspoke = null;
					lastspoke=null;
					lastface=null;
					goon=true;
					for (int j = fan.size() - 1; (j > 0 && goon); j--) {
						goon=false;
						nxtspoke = fan.get(j);
						// in clw direction, end of spoke is the potential new vert
						Vertex tip = nxtspoke.twin.origin;  

						// okay? 
						if (keepV[tip.vertIndx]==1 && doneF[Math.abs(nxtspoke.face.faceIndx)] == 0) {
							lastspoke=nxtspoke; // hold these for when we exit
							lastface=lastspoke.face;
							lastface.edge = lastspoke;
							RedHEdge newre = new RedHEdge(lastspoke.next);
							tip.halfedge = lastspoke.next;
							doneF[Math.abs(lastface.faceIndx)]=1;
							orderEdges.add(lastspoke);
							redseg.add(0, newre); // insert first to orient
							if (doneV[tip.vertIndx] == 0)
								doneV[tip.vertIndx] = -1; // touched
							goon=true;
						}
					}

					// any to add to the red chain?
					if (redseg.size() > 0) {
						RedHEdge firstre=redseg.get(0);
						int m=redseg.size();
						RedHEdge lastre=redseg.get(m-1);
						// first internal links
						for (int j = 0; j < (m - 1); j++) {
							redseg.get(j).next = redseg.get(j + 1);
							redseg.get(j + 1).prev = redseg.get(j);
						}

						// now link into full chain, starting with 'lastspoke'
						RedHEdge ns = new RedHEdge(lastspoke); 
						ns.prev=currRed.prev;
						currRed.prev.next=ns;
						ns.next=firstre;
						firstre.prev = ns;
						lastspoke.origin.halfedge=lastspoke;
						lastre.next = currRed.next;
						currRed.next.prev = lastre;
						hit = true;
					} // done with this origin
				} // done processing fan
			} // done processing this 'RedNEdge'
			} // end for loop on N
		} // end of while(hit)
		
		// ------------------ create new dcel structure -----------------
		
		if (debug)
			deBugging.DCELdebug.printRedChain(pdcel);
		
		// count remaining vertices: note, even some keepV may be cut out
		ArrayList<Vertex> verts=new ArrayList<Vertex>();
		int tick=0;
		for (int m=1;m<=pdcel.vertCount;m++) 
			if (doneV[pdcel.vertices[m].vertIndx]!=0) {
				verts.add(pdcel.vertices[m]); 
				tick++;
			}
		pdcel.vertCount=tick;
		
		// select edges with both ends remaining
		ArrayList<HalfEdge> newedges=new ArrayList<HalfEdge>();
		Iterator<HalfEdge> eit=pdcel.edges.iterator();
		while(eit.hasNext()) {
			HalfEdge he=eit.next();
			int a=he.origin.vertIndx;
			int b=he.twin.origin.vertIndx;
			if (doneV[a]!=0 && doneV[b]!=0) // both ends must be valid
				newedges.add(he);
		}
		pdcel.edges=newedges;
		
		if (debug) {
			System.out.println("before fixing red chain");
			deBugging.DCELdebug.showEdges(pdcel);
		}
		
		// Any choke points? vertices (orig indexing) have two
		//    or more boundary arcs through them
		int []redverts=new int[vertCount+1];
		RedHEdge nxtre=pdcel.redChain;
		boolean chit=false;
		do {
			int v=nxtre.myedge.origin.vertIndx;
			redverts[v]++;
			if (redverts[v]>1)
				chit=true;
			nxtre=nxtre.next;
		} while (nxtre!=pdcel.redChain);
		
		// yes, some potential choke points to check out
		ChokeData []chokeData=new ChokeData[vertCount+1];
		if (chit) {
			nxtre=pdcel.redChain;
			do {
				int v=nxtre.myedge.origin.vertIndx;
				if (redverts[v]>1 || redverts[v]<0) {
					if (redverts[v]>1) {
						
						// create ChokeData
						chokeData[v]=new ChokeData();
						chokeData[v].vert=v;
						chokeData[v].redpairs=new HalfEdge[redverts[v]][2];
						int ii=redverts[v]-1;
						chokeData[v].redpairs[ii][0]=nxtre.prev.myedge.twin; // upspoke
						chokeData[v].redpairs[ii][1]=nxtre.myedge; // downspoke
						redverts[v] =-(redverts[v]-1); // change sign to help count
					}
					else {
						int ij=-redverts[v]-1;
						chokeData[v].redpairs[ij][0]=nxtre.prev.myedge.twin; // upspoke
						chokeData[v].redpairs[ij][1]=nxtre.myedge; //downspoke
						redverts[v] +=1;
					}
				} 
				nxtre=nxtre.next;
			} while (nxtre!=pdcel.redChain);
		}
		
		// travel around red chain, adjusting 'HalfEdge.next/prev' to remove
		//   unneeded edges.
		nxtre=pdcel.redChain;
		do {
			Vertex vert=nxtre.myedge.origin;
			vert.halfedge=nxtre.myedge; // deBugging.DCELdebug.showEdges(pdcel);
			HalfEdge uedge=null;
			HalfEdge dedge=null;
			
			// choke point? Must determine the spokes involved
			if (chokeData[vert.vertIndx]!=null) {
				ChokeData cdata=chokeData[vert.vertIndx];
				
				// get edge flower from first pair downstream spoke
				HalfEdge start=cdata.redpairs[0][1];
				ArrayList<HalfEdge> eflower=vert.getEdgeFlower(start,start);
				int k=eflower.size();
				int []indxflower=new int[k];
				int []hitflower=new int[k]; // 1 if downstream, -1 if upstream, 0 else
				for (int j=0;j<k;j++) // gather ordered indices 
					indxflower[j]=eflower.get(j).origin.vertIndx;
				int cdn=cdata.redpairs.length;
				for (int jk=0;jk<cdn;jk++) {
					int us=eflower.indexOf(cdata.redpairs[jk][0]); // upstream hit
					hitflower[us] -=1; // subtract 1
					int ds=eflower.indexOf(cdata.redpairs[jk][1]); // downstream hit
					hitflower[ds] +=1; // add 1
				}
				
				// any gaps? ups/downs might well cancel, or partially cancel
				if (hitflower[k-1]==-1) { // gap between up at end and down at beginning
					uedge=eflower.get(k-1);
					dedge=eflower.get(0);
					dedge.twin.next=uedge;
					uedge.prev=dedge.twin;
					hitflower[0]=hitflower[k-1]=0; // fixed
				}
				for (int mk=1;mk<k;mk++) {
					if (hitflower[mk]==-1) { // beginning of gap
						int gapend=-1;
						for (int mm=mk+1;(mm<=k && gapend<0) ;mm++)
							if (hitflower[mm%k]==1)
								gapend=mm%k;
						uedge=eflower.get(mk);
						dedge=eflower.get(gapend);
						dedge.twin.next=uedge;
						uedge.prev=dedge.twin;
						hitflower[mk]=hitflower[gapend]=0; // fixed
					}
				}
			}
			// fix things at a normal non-choke vertex
			else {
				uedge=nxtre.prev.myedge.twin; 
				dedge=nxtre.myedge.twin; 
				dedge.next=uedge;
				uedge.prev=dedge;
				// deBugging.DCELdebug.halfedgeends(pdcel,nxtre.myedge);
				// deBugging.DCELdebug.halfedgeends(pdcel,uedge);
				// deBugging.DCELdebug.halfedgeends(pdcel,dedge);
			}
			nxtre=nxtre.next;
		} while (nxtre!=pdcel.redChain);

		if (debug) {
			System.out.println("after fixing red chain");
			deBugging.DCELdebug.showEdges(pdcel);
		}

		// store and reindex the remaining vertices
		pdcel.vertices=new Vertex[tick+1];
		Iterator<Vertex> vit=verts.iterator();
		tick=1;
		pdcel.newOld=new VertexMap();
		while (vit.hasNext()) {
			Vertex v=vit.next();
			pdcel.newOld.add(new EdgeSimple(tick,v.vertIndx));
			v.vertIndx=tick;
			pdcel.vertices[tick]=v;
			tick++;
		}
		
		// find bdry edges
		ArrayList<HalfEdge> bdrys=new ArrayList<HalfEdge>();
		
		// reset original bdry faces as not done
		Iterator<Face> bit=pdcel.idealFaces.iterator();
		while(bit.hasNext())
			doneF[Math.abs(bit.next().faceIndx)]=0;
		
		if (debug)		
			deBugging.DCELdebug.printRedChain(pdcel);
		
		// boundary have twins with faces not done
		if (pdcel.redChain!=null) { // else is a sphere
			RedHEdge re=pdcel.redChain;
			do {
				if (doneF[Math.abs(re.myedge.twin.face.faceIndx)]==0)
					bdrys.add(re.myedge.twin);
				re=re.next;
			} while (re!=pdcel.redChain);
		}

		// index the faces
		int cnt=pdcel.indexFaces(pdcel.edges, bdrys);
		if (cnt==0) {
			CirclePack.cpb.errMsg("failed in 'indexFaces'");
			return null;
		}
			
		// use 'orderEdges' to set 'LayoutOrder'
		pdcel.LayoutOrder=new ArrayList<Face>();
		// only add face if opposite vert not yet placed
		int []vhit=new int[pdcel.vertCount+1]; 
		Iterator<HalfEdge> oit=orderEdges.iterator();
		while (oit.hasNext()) {
			HalfEdge he=oit.next();
			Face face =he.face;
			face.edge=he;
			int oppv=he.next.next.origin.vertIndx;
			if (vhit[oppv]==0) {
				vhit[oppv]=1;
				pdcel.LayoutOrder.add(face);
			}
		}
		
		return pdcel;
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
		faces=new ArrayList<Face>();
		faces.add(null); // index from 1, first entry 'null'
		idealFaces=new ArrayList<Face>();
		
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
						idealFaces.add(newFace);
					}
					// else, regular face
					else {
						newFace.faceIndx=++faceCount;
						faces.add(newFace);
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
			Iterator<Face> ifit=idealFaces.iterator();
			while (ifit.hasNext()) {
				Face idealface=ifit.next();
				idealface.faceIndx=-faceCount+idealface.faceIndx; // convert tmp index, make negative
				faces.add(idealface);
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
	 * u will have been laid out in earlier in the process;
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
						if (!idealFaces.contains(he.face) && 
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
						if (!idealFaces.contains(he.face) && 
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
	 * whose circle is put at the origin. Checking given
	 * e first to see if it is suitable.
	 * @param e HalfEdge, generally will be null
	 * @return Halfedge, null on error or none found
	 */
	public HalfEdge chooseAlpha(HalfEdge e) {
		if (e!=null && vertIsBdry(e.origin)==null)
			return e;
		Iterator<HalfEdge> eit=edges.iterator();
		while (eit.hasNext()) {
			HalfEdge he=eit.next();
			if (vertIsBdry(he.origin)==null)
				return he;
		}
		return null;
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
				arrayf.add(faces.get(j)); // get face
		}
		else {
			Iterator<Integer> flst=facelink.iterator();
			while (flst.hasNext()) {
				arrayf.add(faces.get(flst.next())); // get face of that index
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
					edges.add(he); // add both to parent array
					edges.add(he.twin);
				}	
				
				// set 'edge' null to avoid reuse
				face.edge=null;
				idealFaces.remove(face); // a bdry face becomes interior
				
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
		indexFaces(edges,getBdryEdges());
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
			if ((he=findEdge(edge.v,edge.w))!=null)
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
					if (!idealFaces.contains(face)) // omit ideal faces
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
			if (idealFaces.contains(he.twin.face))
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
			
			edges.remove(he);
			edges.remove(he.twin);
		}				

		// we flip the interior ones
		n=flipEdges(arrayInt);
		indexFaces(edges,getBdryEdges());
		
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
				bouq[v]=usualFlower(vertices[v]);
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
		if ((vertCount-edges.size()+(faces.size()-1))==2) 
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
		Iterator<Face> bit=idealFaces.iterator();
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
 	 * NEEDED FOR CIRCLEPACK
	 * Dual graph store in 'EdgeLink'. This is linked list of 
	 * 'EdgeSimple' objects, which are just pairs {f,g} of 
	 * indices of faces sharing an edge.
	 * @param ideal boolean; if true, include edges to ideal faces.
	 * @return Edgelink, null on error
	 */
	public EdgeLink getDualEdges(boolean ideal) {
		EdgeLink elink=new EdgeLink();
		Iterator<Face> fit=faces.iterator();
		fit.next();  // toss first 'null' entry
		while(fit.hasNext()) {
			Face f=fit.next();
			HalfEdge edge=f.edge;
			HalfEdge nxtedge=edge;
			do {
				Face tf=nxtedge.twin.face;
				if ((ideal || !idealFaces.contains(tf)) && 
						Math.abs(tf.faceIndx)>Math.abs(f.faceIndx))
					elink.add(new EdgeSimple(f.faceIndx,tf.faceIndx));
				nxtedge=nxtedge.next;
			} while (nxtedge!=edge);
		}
		return elink;
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
			if (idealFaces!=null && idealFaces.contains(nxtedge.twin.face))
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
		if (idealFaces.contains(he.face))
			return true;
		return false;
	}

	/**
	 * Find index of w in flower of v. See 'PackData.nghb' 
	 * @param v int
	 * @param w int
	 * @param bouq int[][], array of flowers
	 * @return int, -1 on error
	 */
	public static int nghb(int v,int w,int [][]bouq) {
		int len=bouq.length-1;
		if (v<1 || v>len || w<1 || w>len) 
			return -1; 
		int []flower=bouq[v];
		for (int j=0;j<=flower.length;j++)
			if (flower[j]==w) return j;
		return -1;
	}

	/**
	 * Find if there is a halfedge from v to w. 
	 * @param v int
	 * @param w int
	 * @return HalfEdge, null if not found
	 */
	public HalfEdge findEdge(int v,int w) {
		Iterator<HalfEdge> eit=edges.iterator();
		while (eit.hasNext()) {
			HalfEdge he=eit.next();
			if (he.origin.vertIndx==v) {
				ArrayList<HalfEdge> eflower=he.origin.getEdgeFlower();
				Iterator<HalfEdge> heit=eflower.iterator();
				while (heit.hasNext()) {
					HalfEdge hfe=heit.next();
					if (hfe.twin.origin.vertIndx==w)
						return hfe;
				}
			}
		}
		return null;
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
		p.faceCount=(faces.size()-1)-idealFaces.size(); 
		p.faces=new komplex.Face[p.faceCount+1]; // new face structure
		Iterator<Face> flst=faces.iterator();
		flst.next(); // toss first 'null' entry
		int tick=0;
		while (flst.hasNext()) {
			Face face=flst.next();
			if (!idealFaces.contains(face)) {
				face.faceIndx=++tick; // reset indices (perhaps already in order)
				// for new komplex.Face
				p.faces[tick]=new komplex.Face();
				p.faces[tick].vert=face.getVerts();
				p.faces[tick].vertCount=p.faces[tick].vert.length;
				p.faces[tick].indexFlag=0;
			}
		}
		
		if (LayoutOrder!=null) 
			p.firstFace=LayoutOrder.get(0).faceIndx;
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
		if (idealFaces.contains(nxtedge.twin.face))
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
		
		Iterator<HalfEdge> eit=edges.iterator();
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
		int indx_vw=nghb(v,w,bouquet);
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
			int indx=nghb(nextv,v,bouquet);
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
	 * @param face Face
	 * @return Complex, null on error
	 */
	public Complex faceCenter(Face face) {
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
		Iterator<Face> fit=faces.iterator();
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
		fit=faces.iterator();
		face=fit.next(); // flush first null face
		while (fit.hasNext()) {
			face=fit.next();
			if (face.faceIndx>0) // ignore ideal faces
				qdcel.vertices[face.faceIndx].center=faceCenter(face);
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
	    	edgeCount=edges.size();
	    	HalfEdge []edgearray=new HalfEdge[edgeCount+1];
	    	int eindx=0;
	    	Iterator<HalfEdge> eit=edges.iterator();
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
		Iterator<HalfEdge> deit=edges.iterator();
		while (deit.hasNext()) {
			HalfEdge he=deit.next();
			if (he!=null)
				dedges[he.edgeIndx-1]=he;
		}
				
		// dual vertices are regular faces
		Iterator<Face> dvit=faces.iterator();
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
	    		Complex z=faceCenter(face);
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

class ChokeData {
	public int vert;
	public HalfEdge [][]redpairs;
}

