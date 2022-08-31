package komplex;

import java.awt.Color;
import java.awt.geom.Path2D;
import java.io.BufferedReader;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import JNI.DelaunayData;
import JNI.ProcessDelaunay;
import allMains.CirclePack;
import combinatorics.komplex.Face;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.JNIException;
import listManip.GraphLink;
import listManip.HalfLink;
import listManip.VertexMap;
import math.Point3D;
import packing.CPdrawing;
import packing.PackData;
import util.ColorUtil;
import util.StringUtil;

/**
 * Holds combinatoric triangulations; eventually want more general 
 * combinatoric polygonal cells. Faces indexed starting at 1.
 * @author kens
 *
 */
public class Triangulation {
	public int faceCount;
	public int maxIndex; // largest index among the nodes;not always set
	public Face []faces; // mainly need vert[3] data of 'Face' object
	public Color []vertColors; // array of colors 
	public Color []faceColors; // array of colors
	public int nodeCount;
	public Point3D []nodes;  // may want these as locations (e.g., from point sets)
	public GraphLink dualGraph; // Optional: may need graph of which faces 
	    // are neighbors. Example: when multiple edges connect same vertices; 
	    // Note: may be more than one edge shared by two triangles.
	public VertexMap triVertexMap; // often null. But, if original vert index o is
	    // changed to n, then should have pair <n,o> (i.e., <new,original>)
	
	// Constructor
	public Triangulation() {
		faceCount=nodeCount=0;
		faces=null;
		nodes=null;
		dualGraph=null;
		vertColors=null;
		faceColors=null;
		triVertexMap=null;
	}
	
	/**
	 * Attempt to create 'PackData' from 'Triangulation' object. 
	 * Calling routine must set radii, aims, etc. Errors may not 
	 * show up until packing is processed in calling routine.
	 * @param Tri Triangulation
	 * @param hes geometry
	 * @return PackData, null on failure
	 */
	public static PackData tri_to_Complex(Triangulation Tri,int hes) {
		if (Tri==null || Tri.faceCount<1) 
			return null;
	    int n0=1;
    	double min0=10000.0;
    	double dist=0.0;

		// if Tri has node locations, find the face having node 
    	//   closest to origin
    	int starter=1;
	    if (Tri.nodes!=null) {
	    	Point3D z=null;
	    	for (int i=1;i<=Tri.faceCount;i++) {
//	    		System.out.println("RANDTRI: face="+i+" vert[0]="+
//	    				Tri.faces[i].vert[0]+" "+
//	    				"vert[1]="+Tri.faces[i].vert[1]+" "+"vert[2]="+
//	    				Tri.faces[i].vert[2]);
	    		try {
	    		for (int j=0;j<3;j++) {
	    			if ((z=Tri.nodes[Tri.faces[i].vert[j]])!=null);
	    			dist=Math.abs(z.x)+Math.abs(z.y)+Math.abs(z.z); // simple metric
	    			if (dist<min0) {
	    				min0=dist;
	    				starter=i;
	    				n0=Tri.faces[i].vert[j];
	    			}
	    		}
	    		} catch (Exception ex) {
	    			System.out.println("Triangulation: problem with i = "+i);
	    		}
	    	}
	    }
	  
		// ===== generate temporary 'TmpVert' array from Triangulation
		TmpVert[] tmpVert=null;
		int[] ans=new int[2];
		try {
			tmpVert=parse_triangles(Tri,starter,ans);
		} catch (Exception ex) {
			throw new CombException("try_to_pack error: "+ex.getMessage());
		}
		if (tmpVert==null || ans[0]<3) {
			CirclePack.cpb.errMsg("tri_to_pack failed");
			return null;
		}
		
		// ========== create bouquet from KData array
		int[][] bouquet=new int[tmpVert.length][];
		for (int v=1;v<=ans[0];v++) {
			bouquet[v]=tmpVert[v].flower;
		}
	  
		// =========== create the packing itself
		PackData p=new PackData((CPdrawing)null);
		PackDCEL pdcel=CombDCEL.getRawDCEL(bouquet);
		pdcel.fixDCEL(p);
		p.hes=hes;
		for (int v=1;v<=p.nodeCount;v++) {
			p.packDCEL.vertices[v].mark=tmpVert[v].mark; // original index in triangulation
		}
	  
		// Record the node locations and vert colors from Tri, 
		//   if they exist (orig indices stored in 'mark').
		if (Tri.nodes!=null) {
			for (int i=1;i<=p.nodeCount;i++) {
				int j=p.packDCEL.vertices[i].mark;
				if (hes<=0) {
					p.setCenter(i,new Complex(Tri.nodes[j].x,Tri.nodes[j].y));
				}
				else 
					p.setCenter(i,new Complex(Tri.nodes[p.getVertMark(i)])); // (theta,phi) form
			}
		}
		if (Tri.vertColors!=null) {
			for (int i=1;i<=p.nodeCount;i++) {
				p.setCircleColor(i,ColorUtil.
						cloneMe(Tri.vertColors[p.getVertMark(i)]));
			}
		}
		
		if (n0>0 && n0<=p.nodeCount && !p.isBdry(n0)) 
			p.setAlpha(n0);
		p.fileName=new String("randKomplex");
		return p;
	}
	
	/** 
	 * If verts in 'fvert[3]' have edge {t,v}, return third vert w (or -1 on 
	 * failure) via 'ans[0]'. If 'fb' is true, then the search is forward 
	 * wrt oriented edge <t v>, else backward. Set 'ans[1]' to 1 if the
	 * face orientation is reverse of what it should be and face needs 
	 * reorientation.
	*/
	public int[] face_get_w(int t, int v, int[] fvert, boolean fb) {
		int[] ans = new int[2];
		ans[1] = 0; // default is not to reorient
		for (int j = 0; j < 3; j++) {
			if (fvert[j] == t) {
				if (fvert[(j + 1) % 3] == v) {
					if (!fb)
						ans[1] = 1; // signal to reorient
					ans[0] = fvert[(j + 2) % 3];
					return ans;
				}
				if (fvert[(j + 2) % 3] == v) {
					if (fb)
						ans[1] = 1; // signal to reorient
					ans[0] = fvert[(j + 1) % 3];
					return ans;
				}
				ans[0] = -1; // edge not found
				return ans;
			}
		}
		ans[0] = -1; // edge not found
		return ans;
	}
	
	/** 
	 * Node v has neighbor c and w in one of its faces, and
	 * c already has a flower. Return 1 if <c,v,w> is positively
	 * oriented, -1 if it's negatively oriented, 0 on error.
	*/
	public int face_orientation(int c,int v,int w,int []flower_c,int num) {
	  if (flower_c==null) return 0;
	  int hit=-1;
	  for (int i=0;i<num;i++) if(flower_c[i]==v) hit=i;
	  if (hit>0 && hit<num) {
	    if (flower_c[hit-1]==w) return -1;
	    if (flower_c[hit+1]==w) return 1;
	    return 0;
	  }
	  if (hit==0) {
	    if (flower_c[1]==w) return 1;
	    if (flower_c[num]!=v || flower_c[num-1]!=w) return 0; 
	    return -1;
	  }
	  if (hit==num) {
	    if (flower_c[num-1]==w) return -1;
	    if (flower_c[0]!=v || flower_c[1]!=w) return 0; 
	    return 1;
	  }
	  return 0;
	} 

	/** 
	 * Process collection of triangles to see if it can	
	 * form a packing complex. For now we don't bother to try 
	 * to salvage bad data --- just throw 'DataException'.
	 * 
	 * Do a number of adjustments: adjust indices to start at 1,
	 * run contiguously; original indices stored in 'TmpVert.mark'.
	 * Make face orientations consistent, etc. 
	 * 
	 * Return pointer to new 'TmpVert', which calling program 
	 * can use to generate a bouquet for the new packing; 
	 * 'DataException' on error. ans[0] gives nodecount. 
	 * 
	 * TODO: weakness = connected set of faces, so may throw 
	 * out the major portion of the triangulation.
	 * 
	 * @param T Triangulation
	 * @param start int, index of first face
	 * @param []ans int[], instantiated by calling routine to get data: 
	 * 	ans[0]=nodecount. 
	 * @return TmpVert[], null on error. Original indices stored in 'mark'
	*/
	public static TmpVert[] parse_triangles(Triangulation T, 
			int start, int[] ans)
			throws DataException {

		boolean debug=false;
		int N=T.faceCount;
		
		if (T == null || N < 4)
			throw new DataException("Triangulation null or < 4 faces");
		if (start < 1 || start>N)
			start = 1;

		// debug info
		/*
		 * System.err.println("parse_triangles T faces"); for (int m=1;m<=N;m++)
		 * { StringBuilder bfstr=new StringBuilder("T.face "+m+":   "); for (int
		 * jj=0;jj<3;jj++) { bfstr.append(T.faces[m].vert[jj]+" "); }
		 * bfstr.append("\n"); System.err.print(bfstr); }
		 */

		// find smallest and largest of given node indices
		int bottom=0;
		int top=0;
		for (int i = 0; i < 3; i++) {
			int k = T.faces[start].vert[i];
			bottom = (k > bottom) ? k : bottom;
		}
		for (int f = 1; f <= N; f++) {
			for (int j = 0; j < 3; j++) {
				int k = T.faces[f].vert[j];
				bottom = (k < bottom) ? k : bottom;
				top = (k > top) ? k : top;
			}
		}
		
		// check if '0' was used as an index (but don't allow negative indices)
		if (bottom<0)
			throw new DataException("Data has some negative indices");
		if (bottom==0) { // adjust everyone up by +1
			for (int f = 1; f <= N; f++) {
				for (int j = 0; j < 3; j++) 
					T.faces[f].vert[j]++;
			}
			top++;
			bottom++;
		}
		
		// store initial count of faces contiguous to each node
		int []clicks = new int[top + 1];
		for (int f = 1; f <= N; f++) {
			for (int j = 0; j < 3; j++) {
				clicks[T.faces[f].vert[j]]++;
			}
		}

		// make list of faces for each node
		int [][]nodefaces = new int[top + 1][];
		for (int v = 0; v <= top; v++)
			nodefaces[v] = null;

		int []count = new int[top + 1];
		for (int f = 1; f <= N; f++) {
			for (int j = 0; j < 3; j++) {
				int k = T.faces[f].vert[j];
				if (clicks[k] <= 0)
					throw new DataException("Conflicting data");
				// first visit to this face? allocate space
				if (count[k] == 0)
					nodefaces[k] = new int[clicks[k]];
				// add face to list for this vertex and increment the index
				nodefaces[k][count[k]] = f;
				count[k]++;
			}
		}

		// debug: give non-empty nodefaces
		/*
		 * for (int nf=1;nf<=top;nf++) { if (nodefaces[nf]!=null) {
		 * StringBuilder bfstr=new StringBuilder("nodefaces for "+nf+":   ");
		 * for (int jj=0;jj<clicks[nf];jj++) {
		 * bfstr.append(nodefaces[nf][jj]+" "); } bfstr.append("\n");
		 * System.err.print(bfstr); } }
		 */
		
		// debug: any vertices missing? debug=true;
		if (debug) {
			int clks0=0;
			int clks1=0;
			for (int v=1;v<=top;v++) {
				if (clicks[v]==0)
					clks0++;
				else if (clicks[v]==1)
					clks1++;
			}
			System.out.println("There are "+clks0+" verts with no clicks, and "+clks1+" with just 1.");
		}

		// create K_data (but will adjust it later)
		TmpVert[] pK = new TmpVert[top + 1];
		for (int i = 1; i <= top; i++)
			pK[i] = new TmpVert();

		// Here we build flowers for the vertices. Begin with first vertex 
		//   of 'start' face. For each petal w added to its flower, if w is not already processed, its utilFlag shows the
		//   first face that encounters it. Also, each face we encounter is 
		//   adjusted, if necessary, to be positively oriented vis-a-vis the
		//   first face. Convert utilFlag to -1 for vertices whose flowers 
		//   have been finished.

		int[] fvert = T.faces[start].vert;
		int vtarget = fvert[0];
		int hit=0;
		int first_face=0;
		int ffindx=0;
		pK[vtarget].utilFlag = start;
		
		int []hitface=new int[N+1]; // for debugging 
		hitface[start]=1;
		
		while (vtarget != 0) {
			hit = 0;
			first_face = pK[vtarget].utilFlag;
			fvert = T.faces[first_face].vert;

			// must find first_face's index in target's nodefaces
			ffindx = -1;
			for (int i = 0; ((ffindx == -1) && i < count[vtarget]); i++)
				if (nodefaces[vtarget][i] == first_face)
					ffindx = i;
			if (ffindx == -1)
				throw new DataException("didn't find a first face index");

			// make room, allowing for growth forward or back
			int[] preflower = new int[2 * count[vtarget] + 4];

			// put verts of first face in middle of preflower space
			int front = 0;
			int back = 0;
			for (int j = 0; j < 3; j++) {
				if (fvert[j] == vtarget) {
					back = count[vtarget];
					front = back + 1;
					// Note: the very first face put down here determines
					// orientation of the whole complex
					preflower[back] = fvert[(j + 1) % 3];
					preflower[front] = fvert[(j + 2) % 3];
					pK[vtarget].num = 1;
					// indicate which face was used
					if (pK[preflower[back]].utilFlag == 0)
						pK[preflower[back]].utilFlag = first_face;
					if (pK[preflower[front]].utilFlag == 0)
						pK[preflower[front]].utilFlag = first_face;
					nodefaces[vtarget][ffindx] = 0; // this face has been used
					j = 3; // get out of for-loop
				}
			}
			if (nodefaces[vtarget][ffindx] != 0)
				throw new DataException();

			// now add petals forward (counterclockwise), then backward.

			// forward
			hit = 1;
			int next_face=0;
			int swtch=0;
			int v=0;
			int w=0;
			while (hit != 0 && preflower[front] != preflower[back]) {
				hit = 0;
				for (int i = 0; (preflower[front] != preflower[back])
						&& i < count[vtarget]; i++) {
					if ((next_face = nodefaces[vtarget][i]) > 0) { // face not
																	// yet used
						fvert = T.faces[next_face].vert;
						v = preflower[front];
						int[] tmp = T.face_get_w(vtarget, v, fvert, true);
						w = tmp[0];
						swtch = tmp[1];
						if (w >= 0 && w != preflower[front - 1]) {
							front++;
							preflower[front] = w;
							pK[vtarget].num++;
							if (pK[w].utilFlag == 0)
								pK[w].utilFlag = next_face;
							hit = 1;
							if (swtch != 0) { // face needs reorientation for
												// possible later use
								int temp = T.faces[next_face].vert[0];
								T.faces[next_face].vert[0] = T.faces[next_face].vert[1];
								T.faces[next_face].vert[1] = temp;
							}
							nodefaces[vtarget][i] = 0; // this face has been used for this vert
						}
					}
				}
			} // done forward

			// flower still open? add petals backward
			if (preflower[front] != preflower[back]) {
				hit = 1;
				while (hit != 0 && preflower[front] != preflower[back]) {
					hit = 0;
					for (int i = 0; (preflower[front] != preflower[back])
							&& i < count[vtarget]; i++) {
						if ((next_face = nodefaces[vtarget][i]) > 0
								&& w != preflower[back + 1]) {
							fvert = T.faces[next_face].vert;
							v = preflower[back];
							int[] tmp = T.face_get_w(vtarget, v, fvert, false);
							w = tmp[0];
							swtch = tmp[1];
							if (w >= 0) {
								back--;
								preflower[back] = w;
								pK[vtarget].num++;
								if (pK[w].utilFlag == 0)
									pK[w].utilFlag = next_face;
								hit = 1;
								if (swtch != 0) { // face needs reorientation
													// for possible later use
									int temp = T.faces[next_face].vert[0];
									T.faces[next_face].vert[0] = T.faces[next_face].vert[1];
									T.faces[next_face].vert[1] = temp;
								}
								nodefaces[vtarget][i] = 0; // this face used for this vert
							}
						}
					}
				} // while
			} 
			else if (pK[vtarget].num < 3) // interior vert needs at least 3 faces
				throw new DataException("Interior vert is in less than 3 faces"); 

			// Note: could do check here that we've used all the faces,
			// but for now, we just forget this and use what faces we get.

			// fix up the real flower
			pK[vtarget].bdryFlag = 0;
			if (preflower[front] != preflower[back])
				pK[vtarget].bdryFlag = 1;
			pK[vtarget].flower = new int[pK[vtarget].num + 1];
			for (int nn = 0; nn <= pK[vtarget].num; nn++)
				pK[vtarget].flower[nn] = preflower[back + nn];

			// finished with this vertex, set utilFlag to -1
			pK[vtarget].utilFlag = -1;

			// look for next target vertex; this procedure should ensure
			// that result is connected, but we may not reach every face.
			vtarget = 0;
			for (int nn = 1; nn <= top; nn++) {
				if (pK[nn].utilFlag > 0) {
					vtarget = nn;
					nn = top + 1; // just to stop looping
				}
			}
		} // end of while(vtarget)

		// Now have to reprocess to make sure nodes are indexed from 1
		// with no gaps; order is preserved.

		int[] re_indx = new int[top + 1];
		int click = 0;
		for (int i = bottom; i <= top; i++) {
			// error? vert is in some flower but has not itself been processed
			if (pK[i].utilFlag > 0)
				throw new DataException();
			if (pK[i].utilFlag == -1) {
				re_indx[i] = ++click;
			}
		}
		if (click < 3) // too few vertices for a packing
			throw new DataException("Too few vertices for a packing.");
		
		// debug=true;
		if (debug) {
			int tb1=top-bottom+1;
			if (click!=tb1) {
				System.err.println("Debugging in 'parse_triangles.\n");
				StringBuilder strb=new StringBuilder("These indices were not encountered: \n");
				for (int i = bottom; i <= top; i++) {
					if (pK[i].utilFlag ==0)
						strb.append(" "+i);
				}
				strb.append("\n");
				System.err.println(strb.toString());
				strb=new StringBuilder("Flowers of these indices were not finished: \n");
				for (int i = bottom; i <= top; i++) {
					if (pK[i].utilFlag >0)
						strb.append(" "+i);
				}
				strb.append("\n");
				System.err.println(strb.toString());
			}
		}
		
		ans[0] = click;

		// create the final K_data
		TmpVert[] tmpVert = new TmpVert[ans[0] + 1];
		for (int i = bottom; i <= top; i++) {
			int n = re_indx[i];
			if (n != 0) {
				// save former index in 'mark' for use on return.
				tmpVert[n] = pK[i];
				tmpVert[n].mark = i;
				for (int j = 0; j <= tmpVert[n].num; j++)
					tmpVert[n].flower[j] = re_indx[tmpVert[n].flower[j]];
			}
		}
		return tmpVert;
	}

	/**
	 * Read a file of combinatorial polygons (n-tuples of indices, one 
	 * n-tuple per line) and create a 'Triangulation' object. Try to
	 * handle several possible formats: CirclePack, OFF, VTK, and 
	 * (for triangulations) even more rudimentary formats. Data may 
	 * include 2D or 3D locations as well, and possibly color codes, 
	 * hence all the complications. Ignore empty and '#' comment lines.
	 *  
	 * First hurdle is to get beyond the header lines (if any) and
	 * other data, to determine the type of file. Then we look for
	 * combinatorics: find by key word or line starting with integer,
	 * then reading succeeding lines until non-integer is encountered.
	 * Likewise for XY[Z] data: find by key work or line starting with
	 * double and continuing until non doubel is encountered. (We ignore
	 * indications of how may vertices or faces there are.)
	 * 
	 * Create a 'Triangulation' object from the results, breaking n-gons 
	 * into n triangles when n>3. Include any xy[z] infor, possibly color 
	 * info, and 'VertexMap' <new,old>.
	 * 
	 * Here are possible formats:
	 * 
	 * =====
	 *  CirclePack possibilities:
	 *     - CirclePack format: 'TRIANGULATION: {N), N=number of faces.
	 *     - CirclePack format: 'CHECKCOUNT:' look for 'FACE_TRIPLES:'
	 *       and 'POINTS:'
	 *       
	 * =====
	 *  OFF format: look for 'OFF' in first line
	 * 
	 *     OFF
     *     {V} {F} [{E}] (we ignore)
     *     x_0 y_0 z_0 [r g b a]   real x, y, z (note: indexing starts at zero)
     *     ....
     *     x_{V-1} y_{V-1} z_{V-1}
     *     {now the faces}
     *     n   v_1 v_2 ..... v_n [r g b a]   
     *     ....
     *     k   v_1 ..... v_k

     *     Note: r, g, b, a are doubles; multiply by 255 and cast to integers. 
     *     Note: In reading face lines, if line item count == 3, we deduce n=3
     *           and rgba data must be missing.
     * =====
     *  VTK format: legacy ascii version starts with '# vtk' in first line
     *     
     *     # vtk DataFile Version x.x
     *     ..
     *     POINTS N (or POINT_DATA N)
     *     x y [z]
     *     ...
     *     POLYGONS M (or CELL_DATA M)
     *     n v0 v1 v2
     *     ...
     *     
     *     Note: Newer vtk format may be different
     * =====
     *  Generic: (none of the above), may get exception
     *     - If first line is non-numeric, throw exception.
     *     - If first line is <=2 integers, disregard (vertices/faces)
	 *     - If first line is three integers, take as triangular face and
	 *       continue reading triangular faces.
	 *     - If first line starts with a double, read lines as xy or xyz data.
	 *     - Then look for integer data and use line item count: if 3, then
	 *       assume triangules, else assume form 
	 *           n v0 v1 ... vn
     *
     * Post processing: after data is read, we may have adjustments:
     * 
	 *   Any n-gons, n>3, are broken into n triangles by creating 
	 *   a barycenter vert (and possibly setting its coords and colors)
	 *   
	 *   Indices may start at 0 and/or be non-consecutive: we adjust
	 *   indices, keeping them in order, to start at index 1. Translations
	 *   kept in 'triVertexMap'.
	 *   
	 *   Triangles might not be compatibly oriented, etc.; this  
	 *   should be rectified later by user call to 'parse_triangles'.
	 *   
	 *   Worked this over on 2/4/2020 and again 5/27/2020 for VTK
	 *  
	 * @param fp BufferedReader, opened by calling routine
	 * @return Triangulation, null on error
	 */
	public static Triangulation readTriFile(BufferedReader fp) {
		if (fp==null) 
			throw new InOutException("Reading triangulation failed: file not open");
		String errMsg="";
		VertexMap tVertMap=null;
		Triangulation tri=null;
		
		// first task, is find what type of data
		int type=0; // 0=generic type, 1=CHECKCOUNT, 2=TIRIANGULATION, 3=OFF,43=VTK

		// catch all exceptions at end and use 'errMsg' as error.
		try {

		// check for 'VTK' first because we have to check a comment line
		String line=StringUtil.ourNextLine(fp);
		if (line==null) {
			errMsg="Empty file error";
			throw new InOutException();
		}
		if (line.startsWith("#")) {
			if (line.startsWith("# vtk")) 
				type=4;
			else {
				try {
				fp.reset(); // toss this line
				} catch(Exception ex) {
					errMsg="reset 1 failed";
					throw new InOutException();
				}
			}
			line=StringUtil.ourNextLine(fp,true); 
		}
		
		StringTokenizer tok=new StringTokenizer(line);
		String str=tok.nextToken();
		
		if (str.startsWith("CHECKCOUNT:"))
			type=1;
		else if (str.startsWith("TRIANGULATION:")) 
			type=2;
		else if (str.startsWith("OFF") || str.startsWith("off"))
			type=3;
		else // none-of-the-above 
			tok=new StringTokenizer(line); // retokenize
		
		// now we're ready to process 
		int V=0;
		int F=0;
    	Vector<Color> vertColors=new Vector<Color>(0);
    	Vector<Color> faceColors=new Vector<Color>(0);
    	Vector<Point3D> tmpVerts=new Vector<Point3D>(0);
    	Vector<Face> tmpFaces=new Vector<Face>(0);
    	
    	// process depending on type: fill the above vectors
		boolean faceHit=false;
		boolean vertHit=false;
    	if (type==0 || type==3) { // generic, OFF data; discard V [F] in first line
    		if (tok.countTokens()<3 && StringUtil.lineType(line)==2)
    			line=StringUtil.ourNextLine(fp,true); 
    		
    		// search for face/xyz data 
    		while (line!=null && (!faceHit || !vertHit)) {
    			// use line type: 2 (integer) or 3 (double)
        		int lt=-1; 
    			while (line!=null && (lt=StringUtil.lineType(line))!=2 && lt!=3)
    				line=StringUtil.ourNextLine(fp,true); 
    			if (lt==2 && !faceHit) { // starts with int, should be face data
    				faceHit=true;
    				line=readFaces(fp,tmpFaces,faceColors,line);
    				F=tmpFaces.size();
    			}
    			else if (lt==3 && !vertHit) {  // starts with a double, should be xyz data
    				vertHit=true;
    				line=readXYZ(fp,tmpVerts,vertColors,line);
    				V=tmpVerts.size();
    			}

    		} // end search for data
    	} // done with generic and OFF format
    	
    	else if (type==1) { // CHECKCOUNT
    		// search for face/xyz data 
    		while (line!=null && (!faceHit || !vertHit)) {
    			// flush lines until we get one of the key words
    			while ((line=StringUtil.ourNextLine(fp,true))!=null &&
    					!line.startsWith("FACE_TRIPLES:") &&
								!line.startsWith("POINTS:"));
    			if (line!=null && line.startsWith("FACE_TRIPLES:")) {
    				faceHit=true;
    				line=StringUtil.ourNextLine(fp,true);
    				line=readFaces(fp,tmpFaces,faceColors,line);
    				F=tmpFaces.size();
    			}
    			else if (line!=null && line.startsWith("POINTS:")) {
    				vertHit=true;
    				line=StringUtil.ourNextLine(fp,true);
    				line=readXYZ(fp,tmpVerts,vertColors,line);
    				V=tmpVerts.size();
    			}
    		} // end search for data
    	} // done with CHECKPOINT case
    	
    	else if (type==2) { // TRIANGULATION, normally expect raw integer triples
    		tok=new StringTokenizer(line);
    		if (tok.countTokens()>=3) {
    			try {
    				Integer.parseInt(tok.nextToken());
    				line=readFaces(fp,tmpFaces,faceColors,line);
    				if (tmpFaces!=null) { 
    					faceHit=true; // got something
    					F=tmpFaces.size();
    				}
    			} catch(NumberFormatException nfx) {}
    		}
    		
    		// otherwise, search for face/xyz data with key words
    		while (line!=null && (!faceHit || !vertHit)) {
    			// flush lines until we get one of the key words
    			while ((line=StringUtil.ourNextLine(fp,true))!=null &&
    					!line.startsWith("FACE_TRIPLES:") &&
								!line.startsWith("POINTS:"));
    			if (line!=null && line.startsWith("FACE_TRIPLES:")) {
    				faceHit=true;
    				line=StringUtil.ourNextLine(fp,true);
    				line=readFaces(fp,tmpFaces,faceColors,line);
    				F=tmpFaces.size();
    			}
    			else if (line!=null && line.startsWith("POINTS:")) {
    				vertHit=true;
    				line=StringUtil.ourNextLine(fp,true);
    				line=readXYZ(fp,tmpVerts,vertColors,line);
    				V=tmpFaces.size();
    			}
    		} // end search for TRIANGULATION data with key words
		}
    	
    	else if (type==4) {  // VTK style data
    		// search for face/xyz data 
    		while (line!=null && (!faceHit || !vertHit)) {
    			// flush lines until we get one of the key words
    			while (!line.startsWith("POINT") && 
    					!line.startsWith("POLYGON") && !line.startsWith("CELL_DATA"))
    				line=StringUtil.ourNextLine(fp,true);
    			if (line!=null && (line.startsWith("POLY") || line.startsWith("CELL"))) {
    				faceHit=true;
    				line=StringUtil.ourNextLine(fp,true);
    				line=readFaces(fp,tmpFaces,faceColors,line);
    				F=tmpFaces.size();
    			}
    			else if (line!=null && line.startsWith("POIN")) {
    				vertHit=true;
    				line=StringUtil.ourNextLine(fp,true);
    				line=readXYZ(fp,tmpVerts,vertColors,line);
    				V=tmpVerts.size();
    			}
    		} // end search for data
    	} // done with VTK case
    	
    	// ========== now to build 'tri' ==================
    	
    	// first, reconfigure the data we gathered
    	Face[] holdFaces=null;
    	if (F>0) {
    		holdFaces=new Face[F+1];
    		Iterator<Face> tfst=tmpFaces.iterator();
    		int tick=0;
    		while (tfst.hasNext()) 
    			holdFaces[++tick]=tfst.next();
    	}
    	else { // didn't get crucial data --- the faces
    		tmpFaces=new Vector<Face>(0);
    		errMsg="No face data was found";
    		throw new InOutException();
    	}
    	
    	Point3D[] holdPoints=null;
    	if (V>0) {
    		holdPoints=new Point3D[V+1];
    		Iterator<Point3D> pst=tmpVerts.iterator();
    		int tick=0;
    		while (pst.hasNext())
    			holdPoints[++tick]=pst.next();
    	}

    	// prepare to adjust indexing, etc, later
    	int baryCount=0; // number of barycenters to add
    	int extraFaces=0; // number of extra faces due to bary subdivisions
    	
    	// get range of vert indices
    	int min_indx=10000000; // get min/max indices
    	int max_indx=-1;
    	for (int f=1;f<=F;f++) {
    		int[] vs=holdFaces[f].vert;
    		int num=vs.length;
    		for (int j=0;j<num;j++) {
    			int v=vs[j];
    			min_indx=(v<min_indx) ? v:min_indx;
    			max_indx=(v>max_indx) ? v:max_indx;
    		}
    	}

    	// Mark array with all vertices that occur in faces
    	int []indxhits=new int[max_indx+1];
    	for (int f=1;f<=F;f++) {
    		int[] vs=holdFaces[f].vert;
    		int num=vs.length;
    		for (int j=0;j<num;j++) {
    			indxhits[vs[j]]++;
	    	}
    		if (num>3) { // will have to add barycenter
    			baryCount++;
    			extraFaces +=num-1;
    		}
    	}
    	
    	// Adjust vert indexing if needed, contiguous from 1; store 
    	//   translation in 'triVertexMap' as <new,old>.
    	// Note: order of vert data is the numerical order original indexing
	    int new_nodeCount=0;
	    for (int i=0;i<=max_indx;i++) {
	    	if (indxhits[i]>0) {
	    		indxhits[i] = ++new_nodeCount; 	// note, i is the original index
	    		if (i!=new_nodeCount) {
	    			if (tVertMap==null) 
	    				tVertMap=new VertexMap();
	    			tVertMap.add(new EdgeSimple(new_nodeCount,i));
	    		}
	    	}
	    }

	    // if V (no. vertices read) and new_nodeCount disagree, 
	    //   discard 'tmpVerts (but keep working with face info)
	    if (V>0 && V!=new_nodeCount) {
	    	CirclePack.cpb.errMsg("'readTriFile': counts disn't match, discard xy[z] info");
	    	holdPoints=null;
	    	vertColors=null;
	    	V=0;
	    }
	    
	    // Create 'tri': properly size and fill tri data
    	tri=new Triangulation();
    	tri.faceCount=F+extraFaces;
    	tri.nodeCount=new_nodeCount+baryCount;
    	tri.triVertexMap=tVertMap;
    	
    	// here are the faces 
    	tri.faces=new Face[tri.faceCount+1];
    	// load the original F n-gons
    	for (int f=1;f<=F;f++) {
    		tri.faces[f]=holdFaces[f];
    	}
    	
    	// replace original indices by new ones
    	for (int f=1;f<=F;f++) {
	    	int num=tri.faces[f].vert.length;
	    	for (int j=0;j<num;j++)
	    		tri.faces[f].vert[j]=indxhits[tri.faces[f].vert[j]];
    	}
    	
    	// did we get 3D locations?
    	tri.nodes=null;
    	if (holdPoints!=null) {
    		tri.nodes=new Point3D[tri.nodeCount+1];
    		for (int v=1;v<=V;v++) {
    			tri.nodes[v]=holdPoints[v];
    		}
    	}
    	
    	// transfer vertex colors if they exist
    	tri.vertColors=null;
    	if (vertColors!=null && vertColors.size()>0) {
    		tri.vertColors=new Color[tri.nodeCount+1];
    		Iterator<Color> clst=vertColors.iterator();
    		int tick=0;
    		while (clst.hasNext()) 
    			tri.vertColors[++tick]=clst.next();
    	}
    	
    	// transfer face colors if they exist
    	tri.faceColors=null;
    	if (faceColors!=null && faceColors.size()>0) {
    		tri.faceColors=new Color[tri.faceCount+1];
    		Iterator<Color> flst=faceColors.iterator();
    		int tick=0;
    		while (flst.hasNext()) 
    			tri.faceColors[++tick]=flst.next();
    	}
	    
    	// create barycenters for any n-gons, n>3
	    if (baryCount>0) {
	    	// for tracking new indices/faces
	    	int vtick=new_nodeCount; 
	    	int ftick=F;

	    	// Build new Face[] array
	    	Face[] newFaces=new Face[tri.faceCount+1];
	    	for (int f=1;f<=F;f++) {
	    		int num=tri.faces[f].vert.length;
	    		if (num==3) 
	    			newFaces[f]=tri.faces[f]; // just copy the original
	    		else { // else new vertex for barycenter and new faces 
    				int []vert=tri.faces[f].vert;

    				// new index for barycenter
    				vtick++;
    					
    				// locations specified? use mean value location
	    			if (V>0) {
	    				double xmean=0;
	    				double ymean=0;
	    				double zmean=0;
	    				for (int j=0;j<num;j++) {
	    					int k=vert[j];
	    					Point3D pt=tri.nodes[k];
	    					xmean+=pt.x;
	    					ymean+=pt.y;
	    					zmean+=pt.z;
	    				}
	    				double denm=1/((double)num);
	    				tri.nodes[vtick]=new Point3D(xmean*denm,ymean*denm,zmean*denm);
	    				if (tri.vertColors!=null)
	    					tri.vertColors[vtick]=new Color(255,255,255); // white
	    			}
	    				
	    			// break face into num triangles; replace original face by first of these
	    			newFaces[f]=new Face(3);
	    			newFaces[f].vert=new int[3];
	    			newFaces[f].vert[0]=vert[0];
	    			newFaces[f].vert[1]=vert[1];
	    			newFaces[f].vert[2]=vtick;
	    				
	    			// create rest of new faces
	    			Color tmpCol=new Color(255,255,255);
    				if (tri.faceColors!=null) 
    					tmpCol=(Color)tri.faceColors[f];
	    				
	    			for (int j=1;j<num;j++) {
	    				newFaces[++ftick]=new Face(3);
	    				newFaces[ftick].vert=new int[3];
	    				newFaces[ftick].vert[0]=vert[j];
	    				newFaces[ftick].vert[1]=vert[(j+1)%num];
	    				newFaces[ftick].vert[2]=vtick;
	    				if (tri.faceColors!=null) // same color
	    					tri.faceColors[ftick]=new Color(tmpCol.getRed(),tmpCol.getGreen(),tmpCol.getBlue());
	    			}
	    			
	    		} // done breaking up this face
	    	} // done going through all the face
	    	tri.faces=newFaces;
	    } // done with adding new stuff

		} catch (InOutException iox) {
			if (errMsg.length()==0)
				throw new InOutException(errMsg);
			CirclePack.cpb.errMsg("Error reading triangulation faces or locations");
			return null;
		}
	    	
	    return tri;
	} // done with OFF reading
    
	/**
	 * Read the original faces from the file, possibly not all triangles.
	 * @param fp BufferedReader, open	
	 * @param tmpfaces Vector<Face>, instantiated, but must be size 0
	 * @param faceclrs Vector<Color>, instantiated, but must be size 0
	 * @param line String, current line
	 * @return String, next line
	 * @throws InOutException
	 * @throws NumberFormatException
	 */
	public static String readFaces(BufferedReader fp,
			Vector<Face> tmpFaces,Vector<Color> faceClrs,String line) 
					throws InOutException, NumberFormatException {

    	boolean colorHit=false;
    	int r,g,b,a;
    	
    	// check first line to see if colors are given
    	StringTokenizer tok=new StringTokenizer(line);
    	int toknum=tok.countTokens();
    	if (toknum>3) {
    		int n=Integer.parseInt(tok.nextToken());
    		if (toknum>=n+4)
    			colorHit=true;
    	}
    	
    	// will read as long as we're getting faces
    	int f=0;
    	while (StringUtil.lineType(line)==2) {
        	tok=new StringTokenizer(line);
        	toknum=tok.countTokens();
        	if (toknum<3) {
        		throw new InOutException("failed reading face line "+f);
        	}
        	int numsides=3; // typical, triangular faces
        	if (toknum>3) { // must look for leading 'n' 
        		numsides=Integer.parseInt(tok.nextToken());
        		if (numsides<=2)
        			throw new InOutException("error in face "+f+", n<3");
        		toknum--;
        	}
        	
        	Face newFace=new Face(numsides);
			newFace.vert=new int[numsides];
			for (int j=0;j<numsides;j++) {
				int v=Integer.parseInt(tok.nextToken());
				newFace.vert[j]=v;
			}
			tmpFaces.add(newFace);
			
			// if colors are expected
			if (colorHit) {
				try {
					int cnum=toknum-(numsides+1);
					if (cnum>=3) { // may be rgb[a] data
						r=(int)((Double.parseDouble(tok.nextToken()))*255);
						g=(int)((Double.parseDouble(tok.nextToken()))*255);
						b=(int)((Double.parseDouble(tok.nextToken()))*255);
						if (cnum>3) { 
							a=(int)((Double.parseDouble(tok.nextToken()))*255);
							faceClrs.add(new Color(r,g,b,a));
						}
						else 
							faceClrs.add(new Color(r,g,b));
					}
					else
						faceClrs.add(new Color(255,255,255)); // white
				} catch(Exception ex) {
					faceClrs.add(new Color(255,255,255)); // white
				}
    		}

    		f++;
    		line=StringUtil.ourNextLine(fp,true);
    	} // finished while reading in lines for faces
    	
    	return line;
	}
 	
    /**
     * Parse lines with doubles to get xyz data and possibly colors. 
     * Start with incoming line, return with next line.   	
     * @param fp BufferedReader, open by calling routine
     * @param pts Vector<Point3D>, instantiated, but must be size 0
     * @param vertClrs Vector<Color>, instantiated, but must be size 0
     * @param line String, current line should start with integer
     * @return String, next line
     * @throws InOutException
     * @throws NumberFormatException
     */
    public static String readXYZ(BufferedReader fp,
    		Vector<Point3D> pts,Vector<Color> vertClrs,String line) 
    				throws InOutException, NumberFormatException {
    	if (line==null) 
    		throw new InOutException("bad data for readXYZ");
    	int dim=0;
    	boolean colorHit=false;
    	int r,g,b,a;

    	// learn format from the first line
		StringTokenizer tok=new StringTokenizer(line);
		int toknum=tok.countTokens();
		switch(toknum) {
		case 2: { // x y
			dim=2;
			break;
		}
		case 3: { // x y z
			dim=3;
			break;
		}
		case 5: { // x y r g b
			dim=2;
			colorHit=true;
			break;
		}
		case 6: { // assume x y z r g b rather than x y r g b a)
			dim=3;
			colorHit=true;
			break;
		}
		case 7: { // x y z r g b a
			dim=3;
			colorHit=true;
			break;
		}
		default: {
			throw new InOutException("item count error "+toknum);
		}
		} // end of switch
			
		// read this and following lines
		while (StringUtil.lineType(line)==3) { // while doubles
    		tok=new StringTokenizer(line);
    		toknum=tok.countTokens();
    		if ((toknum=tok.countTokens())<=3 && toknum!=dim) 
    			throw new InOutException("dimension doesn't match");
    		if (dim==2) 
    			pts.add(new Point3D(Double.parseDouble(tok.nextToken()),
    				Double.parseDouble(tok.nextToken()),0.0));
    		else  // dim==3
    			pts.add(new Point3D(Double.parseDouble(tok.nextToken()),
    				Double.parseDouble(tok.nextToken()),
    				Double.parseDouble(tok.nextToken())));
    		
   			if (colorHit) {
    	    	int rem=toknum-dim;
    	    	try {
    	    		if (rem>=3) {
    	    			r=(int)((Double.parseDouble(tok.nextToken()))*255);
    	    			g=(int)((Double.parseDouble(tok.nextToken()))*255);
    	    			b=(int)((Double.parseDouble(tok.nextToken()))*255);
    	    			if (rem>3) {
    						a=(int)((Double.parseDouble(tok.nextToken()))*255);
    						vertClrs.add(new Color(r,g,b,a));
    					}
    					else 
    						vertClrs.add(new Color(r,g,b));
    				}
    	    		else 
        				vertClrs.add(new Color(255,255,255));
    			} catch(Exception ex) {
    				vertClrs.add(new Color(255,255,255));
    			}
    		}

	    	line=StringUtil.ourNextLine(fp,true);
		} // end of while through vertex info

		return line;
    }    		
	
	/**
	 * Create Delaunay triangulation from points (2D or spherical);
	 * no boundary specified in this call.
	 * @param hes int, prescribed geometry
	 * @param pts Vector<Complex>, Complex
	 * @return Triangulation
	 */
	public static Triangulation pts2triangulation(int hes,Vector<Complex> pts) {
		
		// load data
		DelaunayData dData=new DelaunayData(hes,pts);
		
		// try processBuilder calls
		int tick=-1;
		try {
			if (hes>0) {
				tick=ProcessDelaunay.sphDelaunay(dData);
			}
			else {
				tick=ProcessDelaunay.planeDelaunay(dData);
			}
		} catch (Exception ex) {
			  ex.printStackTrace();
			  throw new JNIException("Problem creating Delaunay");
		}

		if (tick<=0)
			throw new JNIException("Problems with Delaunay creation");
		
		return dData.getTriangulation();
	}
	
	/**
	 * Given face and index in its vert[3], find neighboring bdry vertex.
	 * @param face int
	 * @param indx int
	 * @param posboolean: true, then positive (counterclockwise)
	 *  direction, else clockwise.
	 * @return int v
	 */
	public int find_bdry_nghb(int face,int indx,boolean pos) {
		int s=0;
		int e=0;
		int incr=1; 
		if (!pos) incr=2;
		try {
			s=this.faces[face].vert[indx];
			e=this.faces[face].vert[(indx+incr)%3];
		} catch (Exception ex) {
			throw new CombException("error in face "+face+", index "+indx);
		}
		// have to walk around s to get successive faces
		boolean hit=true;
		while (hit) {
			hit=false;
			int []ans=face_with_se(s,e,!pos);
			if (ans[0]>0 && ans[1]>=0) {
				e=this.faces[ans[0]].vert[(ans[1]+incr)%3];
				hit=true;
			}
		}
		return e;
	}

	/**
	 * Return index of first face containing edge [s,e], else -1's
	 * @param s int, start vertex
	 * @param e int, end vertex
	 * @param pos boolean. true means want [s,e] as oriented edge
	 * @return int[2]=face index, index of s in face vertices; [-1,-1] on failure
	 */
	public int []face_with_se(int s,int e,boolean pos) {
		int []ans=new int[2];
		ans[0]=ans[1]=-1;
		int inc=1; 
		if (!pos) inc=2;
		for (int f=1;f<=this.faceCount;f++) {
			for (int j=0;j<3;j++) {
				if (this.faces[f].vert[j]==s && this.faces[f].vert[(j+inc)%3]==e) {
					ans[0]=f;
					ans[1]=j;
					return ans;
				}
			}
		}
		return ans;
	}

	/**
	 * Given faces f1 and f2 sharing vertex v, return indices
	 * in f1 and f2, respectively, for the first end of the shared edge
	 * containing v. 
	 * @param tri Triangulation
	 * @param v int
	 * @param f1 int
	 * @param f2 int
	 * @return int[2]; on error return null
	 */
	public static int []ind2(Triangulation tri,int v,int f1,int f2) {
		if (f1==f2) return null;
		int j1=-1;
		int j2=-1;
		int []ans=new int[2];
		for (int j=0;j<3;j++) {
			if (tri.faces[f1].vert[j]==v)
				j1=j;
			if (tri.faces[f2].vert[j]==v)
				j2=j;
		}
		if (j1<0 || j2<0) {
			return null;
		}
		if (tri.faces[f1].vert[(j1+1)%3]==tri.faces[f2].vert[(j2+2)%3]) {
			ans[0]=j1;
			ans[1]=(j2+2)%3;
			return ans;
		}
		if (tri.faces[f1].vert[(j1+2)%3]==tri.faces[f2].vert[(j2+1)%3]) {
			ans[0]=(j1+2)%3;
			ans[1]=j2;
			return ans;
		}
		return null;
	}

	/**
	 * Alternate type of cookie cutting: (Jan 2017) We basically 
	 * keep only those faces whose centroids are in the region. 
	 * We want a simply connected result, however, so we must 
	 * fill in any encircled faces. Problem: many boundary faces 
	 * poke out, leaving a vertex with no interior neighbors. 
	 * Hence, this turns out to be less useful than I had hoped.
	 * 
	 * @param Tri Triangulation, with 'nodes' giving 2D locations
	 * @param Gamma Path2D.Double, closed curve, assumed Jordan
	 * @return Triangulation or null on error
	 */
	public static Triangulation zigzag_cutter(Triangulation Tri, 
			Path2D.Double Gamma) {

		// store vertex centers
		Complex[] Z = new Complex[Tri.nodeCount+1];
		for (int n = 1; n <= Tri.nodeCount; n++)
			Z[n] = new Complex(Tri.nodes[n].x, Tri.nodes[n].y);
		
		// Now the processing to prune this; create and work with DCEL data
		int[] ans=new int[2];
		TmpVert[] tmpVert=Triangulation.parse_triangles(Tri,0,ans);
		int nodecount=ans[0];
		if (nodecount<=2)
			throw new DataException("DCEL: parse_triangles came up short");
		
		// get the bouquet of flowers
		int [][]bouquet=new int[nodecount+1][];
		for (int v=1;v<=nodecount;v++) 
			bouquet[v]=tmpVert[v].flower;

		PackDCEL pdc=CombDCEL.getRawDCEL(bouquet);
		PackDCEL myDCEL=CombDCEL.extractDCEL(pdc,null,pdc.alpha);
		int facecount = myDCEL.intFaceCount;

		// find the centroids
		Complex[] faceC = new Complex[facecount+1]; // centroids
		int tick = 0;
		for (int f=1;f<=myDCEL.faceCount;f++) {
			HalfLink edges=myDCEL.faces[f].getEdges();
			Iterator<HalfEdge> eit=edges.iterator();
			Complex accum=new Complex(0.0);
			while (eit.hasNext()) {
				HalfEdge he = eit.next();
				accum = accum.add(Z[he.origin.vertIndx]);
			}
			// average of vertex locations
			faceC[++tick] = new Complex(accum.times(1.0/(double)edges.size())); 
		}

		// determine which faces have centroids in Gamma; these are included
		Complex firstC = null;
		combinatorics.komplex.DcelFace firstFace=null;
		int[] facestat = new int[facecount + 1]; // 1=included

		for (int j = 1; j <= facecount; j++) {
			if (Gamma.contains(faceC[j].x, faceC[j].y)) {
				facestat[j] = 1;
				if (firstC == null) { // mark the first included centroid and its face
					firstC = new Complex(faceC[j]);
					firstFace=myDCEL.faces[j];
				}
			}
		}
		
		// There may be other faces included, we may have to fill in holes.
		// Idea is to find simple closed path from included, and
		// add those inside this path.

		// Find all 'HalfEdge's of included faces with non-included (or
		// ideal) face on the other side.
		Vector<HalfEdge> putbdry = new Vector<HalfEdge>(0);
		for (int f = 1; f <= Tri.faceCount; f++) {
			if (facestat[f] > 0) {
				HalfLink edges=myDCEL.faces[f].getEdges();
				Iterator<HalfEdge> eit=edges.iterator();
				while (eit.hasNext()) {
					HalfEdge he=eit.next();
					combinatorics.komplex.DcelFace oppface = he.twin.face;
					if (Math.abs(oppface.faceIndx) > facecount || facestat[Math.abs(oppface.faceIndx)] == 0)
						putbdry.add(he);
				}
			}
		}

		// sift through 'putbdry', removing cclw closed chains until
		// we get one which wraps positively about 'firstC'. This
		// should be "outer" bdry.

		Path2D.Double outerPath = null;
		
		while (outerPath == null && putbdry.size() > 0) {
			// debug: 'disp -ffc120 flist -tfc195t2 z zlist;'
			
			// start a new closed edge path
			Vector<HalfEdge> bpath = new Vector<HalfEdge>(0);
			HalfEdge start = putbdry.remove(0);
			bpath.add(start);
			
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
		int []inouter=new int[myDCEL.faceCount+1];
		for (int j = 1; j <= facecount; j++) { 
			if (outerPath.contains(faceC[j].x, faceC[j].y)) { 
				inouter[j]=1;
				if (startindx==-1)
					startindx=j;
			}
		}
		
		// if 'firstFace' is no longer included, use new starting place
		if (inouter[Math.abs(firstFace.faceIndx)]==0)
			firstFace=myDCEL.faces[startindx];
			
		// cycle through adding faces to 'firstFace'
		Vector<combinatorics.komplex.DcelFace> nextF=new Vector<combinatorics.komplex.DcelFace>();
		Vector<combinatorics.komplex.DcelFace> currF=nextF;
		nextF.add(firstFace);
		int[] newfaces = new int[myDCEL.faceCount+1];
		newfaces[Math.abs(firstFace.faceIndx)]=1;
		int infacecount = 1;
		tick=0;
		while(nextF.size()>0) {
			currF=nextF;
			nextF=new Vector<combinatorics.komplex.DcelFace>();
			while (currF.size()>0) {
				combinatorics.komplex.DcelFace currface=currF.remove(0);
				HalfLink edges=currface.getEdges();
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
				int []fv= myDCEL.faces[j].getVerts();
				StringBuilder ddstr=new StringBuilder("face: ");
				for (int nm=0;nm<fv.length;nm++)
					ddstr.append(" "+fv[nm]);
				System.err.println(ddstr.toString());
				
				int []verts=myDCEL.faces[j].getVerts();
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
				HalfEdge he = myDCEL.faces[j].edge;
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
				zzTri.faces[++tick]=new Face(3);
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
	 * Create new triangulation by barycentrically subdividing
	 * the given triangulation, breaking each face into six faces. 
	 * The main need for this is triangulations with multiple edges
	 * between the same vertices and should yield legitimate
	 * "type 3" triangulations.
	 * @param tri Triangulation
	 * @return
	 */
/*	public static Triangulation baryRefine(Triangulation tri) {
		int []f2vcount=new int[tri.maxIndex+1];
		
		// count faces for each vertex
		for (int f=1;f<=tri.faceCount;f++) 
			for (int j=0;j<3;j++)
				f2vcount[tri.faces[f].vert[j]]++;
		
		// storage space for data by vertex
		TmpVert []tmpVert=new TmpVert[tri.maxIndex+1];
		for (int v=1;v<=tri.maxIndex;v++) {
			if (f2vcount[v]!=0)
				tmpVert[v]=new TmpVert(v,f2vcount[v]);
		}
		
		// store the faces containing each vertex
		for (int f=1;f<=tri.faceCount;f++) {
			for (int j=0;j<3;j++) {
				if (!tmpVert[tri.faces[f].vert[j]].add(f))
					throw new CombException("failure to store faces");
			}
		}
		
		// storage space for new vertex indices by face
		int newvert=tri.maxIndex;
		TmpFace []tmpFace=new TmpFace[tri.faceCount+1];
		for (int f=1;f<=tri.faceCount;f++) {
			tmpFace[f]=new TmpFace(f,++newvert);
			tmpFace[f].newverts[0]=++newvert;
			tmpFace[f].newverts[1]=++newvert;
			tmpFace[f].newverts[2]=++newvert;
		}

		// renumber matching new verts (at most 2 choices, choose smaller)
		for (int f=1;f<=tri.faceCount;f++) {
			int f1=f;
			for (int j=0;j<3;j++) {
				int v=tri.faces[f1].vert[j];
				int count=tmpVert[v].count;
				for (int k=0;k<count;k++) {
					int f2=tmpVert[v].faces[k];
					int []inds=ind2(tri,v,f1,f2);
					if (inds!=null) {
						int ind1=tmpFace[f1].newverts[inds[0]];
						int ind2=tmpFace[f2].newverts[inds[1]];
						if (ind1<ind2)
							tmpFace[f2].newverts[inds[1]]=ind1;
						else if (ind2<ind1)
							tmpFace[f1].newverts[inds[0]]=ind2;
					}
				}
			}
		}
		
		// set new, consecutive vertex indices
		int []newindices=new int[newvert+1];
		// identify those that occur with -1
		for (int f=1;f<=tri.faceCount;f++) {
			newindices[tmpFace[f].bary]=-1;
			for (int j=0;j<3;j++) {
				newindices[tmpFace[f].newverts[j]]=-1;
				newindices[tri.faces[f].vert[j]]=-1;
			}
		}
		int nextindex=0;
		for (int i=1;i<=newvert;i++) {
			if (newindices[i]==-1)
				newindices[i]=++nextindex;
		}
		
		Vector<Face> facevec=new Vector<Face>(6*tri.faceCount+1);
		// 6 new faces for each old, using new indices
		for (int f=1;f<=tri.faceCount;f++) {
			for (int j=0;j<3;j++) {
				Face face=new Face();
				face.vert[0]=newindices[tri.faces[f].vert[j]];
				face.vert[1]=newindices[tmpFace[f].newverts[j]];
				face.vert[2]=newindices[tmpFace[f].bary];
				facevec.add(face);
				face=new Face();
				face.vert[0]=newindices[tmpFace[f].newverts[j]];
				face.vert[1]=newindices[tri.faces[f].vert[(j+1)%3]];
				face.vert[2]=newindices[tmpFace[f].bary];
				facevec.add(face);
			}
		}
		
		// convert vector to triangulation
		Triangulation ans=new Triangulation();
		ans.faces=new Face[facevec.size()+1];
		ans.maxIndex=ans.faceCount=0;
		for (int v=0;v<facevec.size();v++) {
			ans.faces[v+1]=facevec.elementAt(v);
			for (int j=0;j<3;j++)
				ans.maxIndex=(ans.faces[v+1].vert[j]>ans.maxIndex) ? 
						ans.faces[v+1].vert[j] : ans.maxIndex;
			ans.faceCount++;			
		}
		
		return ans;
	}
*/
	
}

class TmpVert {
	int vert;  // index of this vertex
	int num;
	int[] flower; // counterclockwise flower
	int utilFlag;
	int bdryFlag;
	int mark;
		
	public TmpVert() {
	}
}		
 
class TmpFace {
	int face;        // index of this face
	int bary;        // index of barycenter
	int []newverts;  // indices for edges
	
	public TmpFace(int f,int bc) {
		face=f;
		bary=bc;
		newverts=new int[3];
	}
}
