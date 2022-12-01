package ftnTheory;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import dcel.RawManip;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.ParserException;
import input.CPFileManager;
import komplex.EdgeSimple;
import komplex.Triangulation;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import panels.PathManager;
import random.RandomTriangulation;
import util.CmdStruct;
import util.RandPaths;
import util.StringUtil;

/** 
 * Extender for managing "conformal welding" manipulations.
 * 
 * TODO: currently converting to DCEL while also generalizing
 * to work with non-simply connected situations. 1/2022.
 * 
 * To "weld" is to attach two packings to one another along 
 * part or all of their boundaries. The attachment rules
 * involve three maps: m1 (resp. m2) is a map from the relevant
 * segment of vertices in bdry p1 (resp. p2) to [0,1] and
 * h is a welding "map", a piecewise continuous map 
 * h: [0,1] -> [0,1]. 
 * 
 * Typically, m1 (or m2) might be (normalized) harmonic 
 * measure (w.r.t. some interior point) or eucl length. 
 * Eventually, it could be explicitly specified. Also
 * might eventually attach multiply-connected complexes
 * along more that one boundary component.
 * 
 * FOR NOW: 
 *  * weld one component at a time
 *  * welds occur along full boundary components
 *  * eucl/sph case: use edge lengths
 *  * hyperbolic case: bdry components must be fully 
 *  *   ideal vertices or fully finite vertices. In the
 *  *   former case, harmonic measure w.r.t. the origin 
 *  *   (i.e., arc length on the unit circle)
 *  
 * Pasting requires alignment of the two boundary's 
 * vertices, so there is a process to match/add vertices.  
 * 
 * After the welding, weld-vertices are in vlist and elist. 
 * Depending on options, 'adjoin' is carried out here or 
 * one can call it separately now that bdrys have the same 
 * numbers of vertices.
 * 
 * Objects involved:
 * 
 * We maintain local packings p1 and p2. We always weld p1 onto p2. 
 * That is, we assume the welding map 'h' takes the bdry component 
 * of p1 onto that of p2. In simply connected case, p1 plays 
 * role of interior of unit disc, p2 the exterior. The 
 * convention in the welding map is counterclockwise around 
 * bdry of p1 and clockwise around p2 (opposite to the convention 
 * in 'adjoin').
 * 
 * welding map: real function h:[0,1]-->[0,1] (reflecting a
 * homeomorphism of the circle).
 * 
 * welding list: Intermediate data which specifies how vertices 
 * on the two packings are matched/augmented. For now this is 
 * stored in '/tmp/weldList_xxxxx.w' Format: Vv Nv Vn Nn ... Vv 
 * First character, capitalized, refers to bdry of p, the 
 * second, lower case, bdry of q. Letters V/v represent 
 * original verts, N/n represent new added verts.
 * 
 * So reading through the file, a 'Vv' means an existing vertex 
 * of p should weld to an existing vertex of q. 'Nv' means a 
 * new vertex should be added to p and welded to an existing 
 * vertex of q; conversely for 'Vn'. (note: original two 
 * first verts should be identified, as should be original 
 * two last vertices, so list should start Vv and end Vv.) 
 * 
 * Here are routines involved; some could be called for specific 
 * tasks, but generally they are parts underlying a single 
 * processing call.
 * 
 * readweldmap: read a welding map into a Pathlist variable
 * (generally temporary). Format for data is 
 *      PATH x1 y1 x2 y2 ... xn yn END
 *      
 * weld_map_to_list: this is the main workhorse routine. 
 * It reads a real-valued welding map from a file and stores 
 * the welding list of V's and N's. 
 * 
 * weld: This actually walks around the boundaries and 
 * figures out how to match/augment the vertices based on 
 * a given welding list.
 * 
 * add_between: utility to add verts when necessary, fix 
 * flowers, etc. This actually changes its target packing, 
 * so if things go wrong, that packing may be damaged.
 * 
 * weldUsingMap: function normally called to gather all 
 * these operations together. Need to give it the filename 
 * for a welding map. It ends up adjusting p and q; results 
 * land in p ('packData'). 'vlist' gives the welded vertices.
 * 
 * @author kens
 * This is based on Brock William's code, though conventions 
 * regarding the maps have often been changed.
 *
 */
public class WeldManager extends PackExtender {
	
	PackData p1; // local copy, inside packing
	PackData p2; // local copy, outside packing
	PackData packOut; // holding results of various calls; 'copy' 
	
	public double[] weldmapDomain;
	public double[] weldmapRange;
	String weldListFileName; // tmp "weldlist"
	
	// Constructor
	public WeldManager(PackData p) {
		super(p);
		extensionType="CONFORMAL_WELDING";
		extensionAbbrev="CW";
		toolTip="'WeldManager': handling 'conformal welding' operations";
		weldListFileName=new String("weldList_"+CPBase.debugID+".w");
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}
		p1=packData.copyPackTo(); // local copy
	}
	
	/** 
	 * Record corresponding arguments in weldmapDomain/Range 
	 * associated with welding between maximal packings p1 and p2 
	 * determined by matching their bdry vertices. Note: for 
	 * "closed" welding (full boundaries), p1, p2 must have the 
	 * same number of bdry verts. Domain is p1, range is p2.
	 * 
	 * Function h(t):[0,1]->[0,1] (interpolated linearly from 
	 * domain/range values) is an orientation preserving homeomorphism 
	 * of the unit circle (or arcs of the unit circle). (Alternate 
	 * conventions occur in the literature: h could be 
	 * orientation-reversing.) Here we also allow maps 
	 * defined in a subarc of [0,1]; namely, 'n' specifies how 
	 * many bdry vertices to use. If n<0, or if the boundary counts 
	 * are equal and <= n, then use the full boundary: domain/range 
	 * arrays are then closed up modulo 1.
	 * @param p and q, PackData: must be hyperbolic max packings.
	 * @param V,W starting bdry vertices in p and q, respectively.
	 * @param n, number of vertices to match; n<0 means match full bdrys
	 * @return 0 on error
	*/
	public int findWeldMap(PackData p,PackData q, int V, int W, int n) {
		int count = 0;
		boolean closeup = false;

		if (!p.status || !q.status || p.hes >= 0 || q.hes >= 0 || 
				p.euler != 1 ||	p.genus != 0 || q.euler != 1 ||	q.genus != 0) {
			Oops("findWeldMap: packings must be "
					+ " topological discs in hyperbolic geometry");
		}
		if (V < 1 || V > p.nodeCount ||	!p.isBdry(V) || 
				W < 1 || W > q.nodeCount ||	!q.isBdry(W)) {
			Oops("findWeldMap: given vertices are improper");
		}

		// reset 'bdryStarts'
		p.bdryStarts[1] = V;
		q.bdryStarts[1] = W;

		// find
		NodeLink p_blist = new NodeLink(p, "b");
		NodeLink q_blist = new NodeLink(q, "b");
		// must reverse the order of q_blist and rotate last to first
		q_blist = q_blist.reverseMe();
		q_blist.removeLast();
		q_blist.add(0,Integer.valueOf(W));

		int npb = p_blist.size();
		int nqb = q_blist.size();

		if (npb < 3 || nqb < 3 || npb < n || (n > 0 && nqb < n)) { // asked for
																	// n matches
			Oops("findWeldMap: not enough edges vis-a-vis given n = " + n);
		}
		if (n < 0 && npb != nqb) { // n<0 means match full boundaries
			Oops("findWeldMap: edge counts don't agree.");
		}
		if (n < 0 || npb <= n)
			closeup = true;

		// Create bdry center list, reverse q, normalize to argument 0, check
		// modulus
		if (closeup) { // full bdry
			weldmapDomain = new double[npb + 1]; // room to close up
			weldmapRange = new double[npb + 1]; // room to close up
		} else {
			weldmapDomain = new double[n];
			weldmapRange = new double[n];
		}

		double dist;
		double maxx = -1000000;
		double p_arg0, q_arg0;

		// normalize args in both p and q to start at zero
		weldmapDomain[0] = 0.0;
		p_arg0 = p.getCenter(V).arg();
		count = 1;
		Iterator<Integer> pl = p_blist.iterator();
		int v = (Integer)pl.next();
		while (pl.hasNext()) {
			v = (Integer) pl.next();
			weldmapDomain[count] = p.getCenter(v).arg() - p_arg0;
			while (weldmapDomain[count] < weldmapDomain[count - 1])
				weldmapDomain[count] += 2.0 * Math.PI;
			maxx = ((dist = Math.abs(1.0 - (p.getCenter(v)).abs())) > maxx) ? dist
					: maxx;
			if (maxx>.01) 
				System.out.println("p "+v);
			count++;
		}
		if (closeup)
			weldmapDomain[count] = 2.0 * Math.PI; // TODO: or do we want 0.0?

		// reverse normalization for q (recall: ordering of q_blist already
		// reversed)
		weldmapRange[0] = 0.0;
		q_arg0 = q.getCenter(W).arg();
		count = 1;
		Iterator<Integer> ql = q_blist.iterator();
		v=(Integer)ql.next();
		while (ql.hasNext()) {
			v = (Integer) ql.next();
			weldmapRange[count] = q_arg0 - q.getCenter(v).arg();
			while (weldmapRange[count] < weldmapRange[count - 1])
				weldmapRange[count] += 2.0 * Math.PI;
			maxx = ((dist = Math.abs(1.0 - (q.getCenter(v)).abs())) > maxx) ? dist
					: maxx;
			if (maxx>.01) 
				System.out.println("q "+v);
			count++;
		}
		if (closeup)
			weldmapRange[count] = 2.0 * Math.PI; // TODO: or do we want 0.0?

		// drifted too far from unit circle?
		if (maxx > .01) {
			Oops("findWeldMap: packings don't seem to be maximal.");
		}
		return count;
	}
	
	/**
	 * Output weld map, stored as arguments in weldmapDomain/Range, 
	 * to 'filename'. The output file is matlab or 'map' form. 
	 * The 'map' form is suitable for reading as a welding map, 
	 * e.g. in 'weld', and is homeomorphism h normalized to go from 
	 * [0,1] to [0,1]. Matlab output maintains info in form of arguments
	 * @param String
	 * @param out_flag: (not yet ready) 0=postscript, 1=postscript+popup, 
	 *        2=matlab, 3=map
	 * @param boolean: true, store in script
	 * @return welding point count, 0 on error 
	 */
	public int writeWeldMap(String filename, int out_flag, 
			boolean toScript) {
		// minimal check on data
		int count=0;
		if (weldmapDomain == null || weldmapRange == null
				|| (count = weldmapDomain.length) < 3
				|| count != weldmapRange.length) {
			Oops("writeWeldMap: domain/range array not set or "+
					"not the same size");
		}

		BufferedWriter fp = null;
		try {
			if ((fp = CPFileManager.openWriteFP(filename, toScript)) == null)
				throw new IOException();
		} catch (Exception iox) {
			Oops("writeWeldMap: failed to open file "+ filename);
		}

		// now output depends on 'out_flag'
		if (out_flag == 3) { 
			/* weld map h: [0,2pi]-->[0,2pi]; should be
			 * ready to call in with 'weld_map_call'
			 */
			// TODO: ?????? check what is needed; is it 'unweld'?

			// normalize first
			double minx;
			double miny = minx = 1000000;
			double maxx;
			double maxy = maxx = -1000000;
			for (int n = 0; n < count; n++) {
				if (weldmapDomain[n] < minx)
					minx = weldmapDomain[n];
				if (weldmapRange[n] < miny)
					miny = weldmapRange[n];
				if (weldmapDomain[n] > maxx)
					maxx = weldmapDomain[n];
				if (weldmapRange[n] > maxy)
					maxy = weldmapRange[n];
			}
			double factorx = 1.0/(maxx - minx);
			double factory = 1.0/(maxy - miny);

			try {
				fp.write("PATH\n");
				for (int n = 0; n < count; n++) {
					fp.write((weldmapDomain[n] - minx) * factorx + "  "
							+ (weldmapRange[n] - miny) * factory + "\n");
				}
				fp.write("END\n");
				msg("weld_map: output in PATH form in file "+ filename);
				fp.flush();
				fp.close();
			} catch (IOException iox) {
				throw new InOutException("writing weld map: "+iox.getMessage());
			}
   		  	if (toScript) { // include in script
 			  CPBase.scriptManager.includeNewFile(filename);
 			  CirclePack.cpb.msg("Wrote packing "+filename+" to the script");
   		  	}
		} 
		else if (out_flag == 2) { // matlab form
			try {
				// preample and size
				fp.write("%% matlab file for welding map fingerprint:\n"
						+ "%%  x gives arguments in domain; y, arguments in range; "
						+ "lists are closed.\n\nN=" + (count + 1)
						+ "\n\nx=[\n");
				for (int n = 0; n < count; n++)
					fp.write(weldmapDomain[n] + "\n");
				fp.write("]\ny=[\n");
				for (int n = 0; n < count; n++)
					fp.write(weldmapRange[n] + "\n");
				fp.write("]\n");
				msg("weld_map: output in matlab form in file "+filename);
				fp.flush();
				fp.close();
			} catch (IOException iox) {
				throw new InOutException("writing weld map: "+iox.getMessage());
			}
		} else { // postscript
			// TODO: not yet converted
			/*
			 * if(graph_popup(x,y,np+1,filename,out_flag)) {
			 * CommandStrParser.flashError("weld_map: output graph in file
			 * %s",filename); } else { CommandStrParser.flashError("weld_map:
			 * call to graph popup failed."); return 0; }
			 */
			errorMsg("CW: not ready for 'postscript' output.");
			try {
				fp.flush();
				fp.close();
			} catch (IOException iox) {
				return 0;
			}
		}
		return count;
	} 

	/**
	 * Goal is to create new packing by splitting p along a
	 * given closed oriented edgepath --- this path plays 
	 * the role of a "welding" curve to be analyzed. If the
	 * edgepath separates p, then return the complex to the
	 * left of the path. 
	 * 
	 * Note that depending on combinatorics, our cookie method 
	 * may do some unwanted pruning of faces in p.
	 * @param p PackData
	 * @param elist EdgeLink
	 * @return PackData, null on error. 
	 */
	public PackData unweld(PackData p,EdgeLink elist) {

		if (elist==null || elist.size()==0)
			return null;
		PackData q=p.copyPackTo();
		HalfLink hlist=new HalfLink();
		hlist.addSimpleEdges(q.packDCEL,elist);

		// check that 'hlist' is simple and closed
		int[] vhits=new int[q.nodeCount+1];
		Iterator<HalfEdge> his=hlist.iterator();
		int firstv=his.next().origin.vertIndx;
		int lastv=firstv;
		vhits[lastv]=-1; // on the path
		NodeLink nghbs=new NodeLink();
		while (his.hasNext()) {
			HalfEdge he=his.next();
			int v=he.origin.vertIndx;
			if (v!=lastv || vhits[v]!=0) {
				CirclePack.cpb.errMsg(
					"unweld: closed path was not simple or not connected");
					return null;
				}
			vhits[v]=-1;
			
			// catalog vertices to the left of 'hlist'
			int w=he.prev.origin.vertIndx;
			if (vhits[w]>=0 && !q.isBdry(w)) {
				vhits[w]=1;
				nghbs.add(w);
			}
			
			lastv=v;
		}
		
		if (lastv!=firstv) {
			CirclePack.cpb.errMsg("unweld: path did not close up");
			return null;
		}
		
		// set alpha to be to the left of hlist
		Iterator<Integer> nis=nghbs.iterator();
		HalfEdge alp=null;
		while (alp==null && nis.hasNext()) {
			Vertex vert=q.packDCEL.vertices[nis.next()];
			HalfLink spokes=vert.getSpokes(null);
			Iterator<HalfEdge> sis=spokes.iterator();
			while (alp==null && sis.hasNext()) {
				HalfEdge he=sis.next();
				int w=he.twin.origin.vertIndx;
				if (vhits[w]==0 && !q.isBdry(w))
					alp=he;
			}
		}
		if (alp==null) {
			CirclePack.cpb.errMsg(
					"unweld: did not find an alpha left of path");
			return null;
		}
		q.packDCEL.alpha=alp;
		q.setAlpha(alp.origin.vertIndx);

		// cookie out by using 'hlist' as the new red chain
		int ans=CombDCEL.redchain_by_edge(
				q.packDCEL,hlist,q.packDCEL.alpha,false);
		if (ans<=0)
			CirclePack.cpb.errMsg("unweld: failed to get red chain");

		q.packDCEL.fixDCEL(q);
		return q;
	}		

	/**
	 * Reads 'weldmapfile' and writes "weld list" information
	 * in temp file 'weldListFileName'. This list gives 
	 * instructions for matching/adding vertices to p and q
	 * so they are ready to weld.
	 * @param p,q, domain/range
	 * @param p_start_vert,q_start_vert;
	 * @param p_count,q_count (number of vertices to use, -1 for all).
	 *    Circumstance may make us adjust p_count, q_count.
	 * @param weldmapfile, pts (x,y) for oriented map [0,1]->[0,1]
	 * 			if 'null' default to identity
	 * @param opt_flag:
	 * @return 0 on error
	 */
	public int weld_map_to_list(PackData p, PackData q, int p_start_vert,
			int q_start_vert, int p_count, int q_count, String weldmapfile,
			boolean script_flag,int opt_flag) {
		int j, n, pn, qn;
		int[] p_vert = null; // counterclockwise closed list of 'p' boundary vertices
		int[] q_vert = null; // clockwise closed list of 'q' boundary vertices
		boolean full_flag = true;
		double x1=0.0;
		double x2=0.0;
		double y1=0.0;
		double y2=0.0;
		double tolr;
		double[] p_coord = null; // coordinates of p's bdry verts (on [0,1]).
		double[] q_coord = null; // coordinates of q's bdry verts (on [0,1]).

		// read the weld map of x-y coords.
		Path2D.Double path_list = PathManager.readpath(weldmapfile,script_flag);
		if (path_list == null) { // default to identity: [0,1] to [0,1]
			path_list = new Path2D.Double();
			path_list.moveTo(0.0, 0.0);
			path_list.lineTo(0.5, 0.5);
			path_list.lineTo(1.0, 1.0);
		}

		// TODO: need to tune this cutoff for rounding off a vertex
		tolr = 0.1;

		// TODO: should do more error checking
		if (!p.isBdry(p_start_vert) || !q.isBdry(q_start_vert))
			Oops("start vertices must be on the boundary");

		// open temporary file for weld list
		try {
			BufferedWriter fp = CPFileManager.openWriteFP(new File(System
					.getProperty("java.io.tmpdir")), weldListFileName, false);
			if (fp == null) {
				Oops("weld: couldn't open " + weldListFileName);
			}

			/*
			 * Create appropriate bdry lists in p_vert/q_vert vectors. Count =
			 * -1 indicates full bdry; we also set this if conditions 
			 * force it.
			 */

			// default to full boundaries; not full is not yet used
			if (p_count > 2 && q_count > 2)
				full_flag = false; 

			// find boundary counts
			int p_hits = 0;
			for (int vv = 1; vv <= p.nodeCount; vv++)
				if (p.isBdry(vv))
					p_hits++;
			int q_hits = 0;
			for (int vv = 1; vv <= q.nodeCount; vv++)
				if (q.isBdry(vv))
					q_hits++;

			// create space
			if (full_flag || p_count >= p_hits || q_count >= q_hits) {
				// Not enough edges, have to do all of both bdry's
				full_flag = true;
				p_count = p_hits;
				q_count = q_hits;
			}
			p_vert = new int[p_count + 1];
			q_vert = new int[q_count + 1];

			// for full bdry, close with repeated first entry
			int last;
			p_vert[0] = last = p_start_vert;
			for (n = 1; n <= p_count; n++)
				// p list positive (counterclockwise)
				p_vert[n] = last = p.getFirstPetal(last);
			q_vert[0] = last = q_start_vert;
			for (n = 1; n <= q_count; n++)
				// Recall: q list must be clockwise.
				q_vert[n] = last = q.getLastPetal(last);

			// Use opt_flag here (TODO: expect more options eventually).
			
			// default: two hyperbolic max packings --- check that they
			//     appear to be maximal, use arguments of center points
			//     (increasing arguments normalized to [0,1]).
			// eucl: use bdry edge length --- check that both are eucl;
			//     both are normalized to [0,1]. */

			// if first bit not set and eucl, set 0001
			if ((opt_flag & (0001 | 0002 | 0004)) == 0) { 
				if (p.hes == 0 && q.hes == 0)
					opt_flag |= 0001;
			}

			// if first bit not now set, check that these are
			//    max packings --- bdry centers on unit circle?
			double dist;
			double pmax=0.0;
			double qmax=0.0;
			if ((opt_flag & (0001 | 0002 | 0004)) == 0) { 
				for (n = 0; n <= p_count; n++)
					pmax = ((dist = Math.abs(1.0 - p.getCenter(p_vert[n]).abs())) 
							> pmax) ? dist : pmax;
				for (n = 0; n <= q_count; n++)
					qmax = ((dist = Math.abs(1.0 - q.getCenter(q_vert[n]).abs())) 
							> qmax) ? dist : qmax;

				if (pmax > .01 || qmax > .01) {
					Oops("weld_map: packings don't seem to be maximal.");
				}

				// put coords in vectors, normalize each to [0,1].
				p_coord = new double[p_count + 1];
				double p_ang0 = p.getCenter(p_vert[0]).arg();
				p_coord[0] = 0.0;
				double p_arg;
				for (n = 1; n <= p_count; n++) {
					p_arg = p.getCenter(p_vert[n]).arg();
					p_coord[n] = p_arg - p_ang0;
					// make arguments monotone
					while (p_coord[n] < p_coord[n - 1])
						p_coord[n] += 2.0*Math.PI;
				}
				for (n = 1; n <= p_count; n++)
					p_coord[n] /= p_coord[p_count];

				q_coord = new double[q_count + 2];
				double q_ang0 = q.getCenter(q_vert[0]).arg();
				q_coord[0] = 0.0;
				double q_arg;
				for (n = 1; n <= q_count; n++) {
					q_arg = q.getCenter(q_vert[n]).arg();
					q_coord[n] = q_ang0 - q_arg;
					// make arguments monotone
					while (q_coord[n] < q_coord[n - 1])
						q_coord[n] += 2.0*Math.PI;
				}
				for (n = 1; n <= q_count; n++)
					q_coord[n] /= q_coord[q_count];

			} // end of opt_flag=0 setup

			// use euclidean edge lengths, normalized to 1
			// TODO: currently use sum of radii instead of distance
			//       between centers.
			else if ((opt_flag & 0001) != 0) { 
				if (p.hes != 0 || q.hes != 0) { // not eucl
					Oops("weld: packings should be euclidean for given option");
				}
				p_coord = new double[p_count + 2];
				p_coord[0] = 0.0;
				for (n = 1; n <= p_count; n++)
					p_coord[n] = p_coord[n - 1] + p.getRadius(p_vert[n - 1])
							+ p.getRadius(p_vert[n]);
				for (j = 1; j <= p_count; j++)
					p_coord[j] /= p_coord[p_count]; // normalize
				q_coord = new double[q_count + 2];
				q_coord[0] = 0.0;
				for (n = 1; n <= q_count; n++)
					q_coord[n] = q_coord[n - 1] + q.getRadius(q_vert[n - 1])
							+ q.getRadius(q_vert[n]);
				for (j = 1; j <= q_count; j++)
					q_coord[j] /= q_coord[q_count]; // normalize
			} // end of eucl case

			/*
			 * Want to transfer p_coords/q_coords to common 
			 * interval [0,1] so they can be compared. For 
			 * balance we do this in a neutral way, in essence 
			 * replacing the path x-->y by x-->(x+y)/2. This should
			 * maintain monotonicity, but not favor p or q. 
			 * We simultaneously do the linear interpolation.
			 */

			double[] coords = new double[2];
			@SuppressWarnings("unused")
			int type=0;

			// first with p
			PathIterator plist = path_list
					.getPathIterator(new AffineTransform());

			// have to get started with first couple points
			type = plist.currentSegment(coords);
			x1 = coords[0];
			y1 = coords[1];
			if (!plist.isDone()) {
				plist.next();
				type = plist.currentSegment(coords);
				x2 = coords[0];
				y2 = coords[1];
			} 
			else {
				Oops("weld_map_to_list: too few coords.");
			}
			pn = 1;

			while (pn < p_count) {
				// arrange x1<p_coord<x2
				while (!plist.isDone() && (x2 < p_coord[pn])) { 
					x1 = x2;
					y1 = y2;
					plist.next();
					type = plist.currentSegment(coords);
					x2 = coords[0];
					y2 = coords[1];
				}
				if (plist.isDone()) { // ran out of function?
					Oops("weld_map_to_list: problem: coords don't match map.");
				}
				p_coord[pn] = ((y1 + (p_coord[pn] - x1)
						* ((y2 - y1) / (x2 - x1))) + p_coord[pn]) / 2.0;
				pn++;
			}
			// get the last p coord
			plist = path_list.getPathIterator(new AffineTransform());
			do {
				plist.next();
				type = plist.currentSegment(coords);
				x1 = coords[0];
				y1 = coords[1];
			} while (!plist.isDone());
			p_coord[p_count] = (x1 + y1) / 2.0;

			// same with q
			plist = path_list.getPathIterator(new AffineTransform());

			// have to get started with first couple points
			type = plist.currentSegment(coords);
			x1 = coords[0];
			y1 = coords[1];
			if (!plist.isDone()) {
				plist.next();
				type = plist.currentSegment(coords);
				x2 = coords[0];
				y2 = coords[1];
			} else {
				Oops("weld_map_to_list: too few coords.");
			}
			pn = 1;

			while (pn < q_count) {
				// arrange y1<p_coord<y2
				while (!plist.isDone() && (y2 < q_coord[pn])) { 
					x1 = x2;
					y1 = y2;
					plist.next();
					type = plist.currentSegment(coords);
					x2 = coords[0];
					y2 = coords[1];
				}
				if (plist.isDone()) { // ran out of function?
					Oops("weld_map_to_list: problem: coords don't match map.");
				}
//				q_coord[pn] = ((y1 + (q_coord[pn] - x1)
//						* ((y2 - y1) / (x2 - x1))) + q_coord[pn]) / 2.0;
				q_coord[pn]=((x1+(q_coord[pn]-y1)*((x2-x1)/(y2-y1)))+q_coord[pn])/2.0;
				pn++;
			}
			// get the last p coord
			plist = path_list.getPathIterator(new AffineTransform());
			do { 
				plist.next();
				type = plist.currentSegment(coords);
				x1 = coords[0];
				y1 = coords[1];
			} while (!plist.isDone());
			q_coord[q_count] = (x1 + y1) / 2.0;

			// Now for the determination of new vertex creation

			fp.write("Vv\n"); // Always weld p_start_vert to q_start_vert for now;
							  // might change the convention later

			/*
			 * Heart of the algorithm: walk around bdry of p and q looking at
			 * locations of p_vert and q_vert. (We always weld p onto q.)
			 * 
			 * If q_vert maps "past" p_vert, then we could add vertex to q so
			 * that p_vert has a vertex to glue onto. If q_vert maps to a
			 * location "before" p_vert, then we could add a vertex to p so that
			 * q_vert has a vertex to glue onto.
			 * 
			 * Since p_coord/q_coord values are now intermingled on same
			 * interval [0,1], just have to tick off new/existing verts in
			 * order. Hard part is deciding when two are close enough to be
			 * identified.
			 */

			pn = qn = 1;
			while (pn <= p_count && qn <= q_count) {
				/*
				 * TODO: very hard to decide how to parse this. 
				 * In the theory you need to avoid "thin triangles", 
				 * mainly occurring when new verts generated are 
				 * too close to existing ones. To avoid this, 
				 * try to put interval of attraction around each 
				 * vert to tell when new verts should be identified. 
				 * Simple rule now: bracket each vert by "tolr" 
				 * proportion of distance to previous/next vert, 
				 * if they intersect, identify those verts. (This 
				 * can be preempted if other verts intervene.) 
				 * Also have to take care in approaching the 
				 * end verts.
				 */

				if (pn==p_count && qn==q_count) { // ends?, identify
					fp.write("Vv\n");
					pn++;
					qn++;
					break;
				}
				if (pn == p_count) { // end of p, must add new to q
					fp.write("Nv\n");
					qn++;
				} else if (qn == q_count) { // end of q, must add to p
					fp.write("Vn\n");
					pn++;
				} else if (p_coord[pn] <= q_coord[qn]) {
					if (p_coord[pn + 1] <= q_coord[qn]
							|| p_coord[pn] < (q_coord[qn] - tolr
									* (q_coord[qn] - q_coord[qn - 1]))) {
						fp.write("Vn\n");
						pn++;
					} else {
						fp.write("Vv\n");
						qn++;
						pn++;
					}
				} else {
					if (q_coord[qn + 1] <= p_coord[pn]
							|| q_coord[qn] < (p_coord[pn] - tolr
									* (p_coord[pn] - p_coord[pn - 1]))) {
						fp.write("Nv\n");
						qn++;
					} else {
						fp.write("Vv\n");
						qn++;
						pn++;
					}
				}
			}

			fp.flush();
			fp.close();
		} catch (Exception ex) {
			throw new InOutException("Problems writing 'weldlist' file");
		}
		return 1;
	} 

	/**
	 * Go counterclockwise around p, clockwise around q, starting at v and w,
	 * resp, until pasting directions in '/tmp/weldListFileName' are all 
	 * completed.
	 * @param p,q
	 * @param v,w
	 * @param opt_flag:
	 *            0010 ==> actually do the adjoin operation;
	 *            otherwise, just create the two packings
	 *            (TODO: don't yet know other uses of opt_flag)
	 */
	int weld(PackData p, PackData q, int v, int w, int opt_flag) {
		int ans = 1;
		int count = 0;
		int v_next, w_next;
		int v_orig = v;
		int w_orig = w;

		if (!p.status || !q.status || (v > p.nodeCount) || (w > q.nodeCount)
				|| v < 1 || w < 1 || !p.isBdry(v) || !q.isBdry(w)) {
			Oops("weld: improper data; e.g., verts " + v + " and " + w
					+ " cannot be used");
		}
		// TODO: might save orig p and q in case this bombs

		// Augment p and q
		try {
			BufferedReader fpr = CPFileManager.openReadFP(new File(System
					.getProperty("java.io.tmpdir")), weldListFileName, false);
			if (fpr == null) {
				throw new DataException("weld file " + weldListFileName
						+ " failed to open.");
			}
			String line = StringUtil.ourNextLine(fpr);
			if (!line.contains("V") || !line.contains("v")) {
				Oops("weld list: first line must be 'Vv'.");
			}
			v_next = p.getFirstPetal(v);
			w_next = q.getLastPetal(w);
			while ((line = StringUtil.ourNextLine(fpr)) != null) {
				count = count + 1;

				// check 'p' situation (upper case letters)
				if (line.contains("V")) {
					v = v_next;
					v_next = p.getFirstPetal(v);
				} else if (line.contains("N")) {
					if (add_between(p, v, v_next) == 0)
						return (-1);
					v = p.getFirstPetal(v);
					// v = newly added new vertex
				} else { /* extraneous stuff? */
					Oops("weld: weld file format problem, upper case");
				}

				if (line.contains("v")) {
					w = w_next;
					w_next = q.getLastPetal(w);
				} else if (line.contains("n")) {
					if (add_between(q, w_next, w) == 0)
						return -1;
					w = q.getLastPetal(w);
					// w = newly added new vertex
				} else { /* extraneous stuff? */
					Oops("weld: weld file format problem, lower case");
				}
			} // end of while
		} catch (Exception ex) {
			throw new DataException("weld: problem reading list: "
					+ ex.getMessage());
		}

		String buf = null;
		if (v == p.getFirstPetal(v_orig)) {
			buf = new String("b");
		} else {
			v = p.getLastPetal(v);
			buf = new String("b(" + v_orig + " " + v + ")");
		}
		p.vlist = new NodeLink(p, buf);
		p.elist = new EdgeLink(p, buf);
		if (w == q.getLastPetal(w_orig)) {
			buf = new String("b");
		} else {
			w = q.getFirstPetal(w);
			buf = new String("b(" + w + " " + w_orig + ")");
		}
		q.vlist = new NodeLink(q, buf);
		q.elist = new EdgeLink(q, buf);

		// Adjoin p and q?
		if (opt_flag>0) { 
			q.bdryStarts[1] = w_orig;

			p.packDCEL=CombDCEL.adjoin(p.packDCEL,
				q.packDCEL, v_orig, w_orig, count);
			p.packDCEL.fixDCEL(p);
				// TODO: note that 'adjoin' pastes p clockwise, q
				//   counterclockwise; no problem for full bdry, but 
				//   with partial bdry's, have to do something else.
				// TODO: use to pass back 'Oldnew' to define vertex_map of q.
			for (v = 1; v <= p.nodeCount; v++)
				p.setPlotFlag(v,1);
		} 
		else {
			msg("weld: two packs appear ready to adjoint:\n"
					+ " designated bdry vertices are " + v + " and " + w
					+ ", resp.," + " alpha vertex of outer pack is " + q.getAlpha());
		}
		return ans;
	} 

	/**
	 * Adds a new vert on boundary between v and v_next. 
	 * Assumes both v, v_next are on boundary and 
	 * v_next=p.getFirstPetal(v). If v or v_next lies in 
	 * just one face, must add interior vert on opposite
	 * edge to which the new vert is attached.

	 * @param p PackData
	 * @param v int
	 * @param v_next
	 * @return 0 on error or inappropriate, else nodeCount
	 */
	public int add_between(PackData p, int v, int v_next) {
		// Basic checks
		HalfEdge hedge=p.packDCEL.findHalfEdge(new EdgeSimple(v,v_next));
		if (!p.isBdry(v) || !p.isBdry(v_next) || 
				hedge==null || !hedge.isBdry()) {
			CirclePack.cpb.errMsg(
					"add_between: bad data");
			return 0;
		}
		
		if (hedge.face.faceIndx<0)
			hedge=hedge.twin;
		
		// if no interior petal, have to split opposite edge first
		if (hedge.next.isBdry()) {
			HalfEdge opp=hedge.next.next;
			if (opp.isBdry() || 
					RawManip.splitEdge_raw(p.packDCEL,opp)==null) {
				CirclePack.cpb.errMsg(
						"add_between: opp edge is bdry or failed to split");
				return 0;
			}
		}
		
		// add new vertex between v and v_next
		if (RawManip.splitEdge_raw(p.packDCEL,hedge)==null) {
			CirclePack.cpb.errMsg(
					"add_between: failure in splitting "+hedge);
			return 0;
		}
		
		p.packDCEL.fixDCEL(p);
		return p.nodeCount;

	}

	/**
	 * Most comprehensive of procedures: does all steps.
	 * 
	 * TODO; Currently, weld list is assumed to be in
	 * 'weldListFileName'; can change after debugging. 
	 * (For now it might be helpful to be able to easily see 
	 * the welding list.) Later, we might want to create a weld 
	 * list data structure and just keep the list in memory.
	 * 
	 * TODO: when q_count and p_count are -1, use whole bdry; 
	 * that is all we're prepared for now.
	 * 
	 * @param p
	 * @param q
	 * @param p_start_vert
	 * @param q_start_vert
	 * @param p_count
	 * @param q_count
	 * @param weldmapfile
	 * @param opt_flag
	 * @return
	 */
	int weldUsingMap(PackData p, PackData q, int p_start_vert,
			int q_start_vert, int p_count, int q_count, 
			String weldmapfile,boolean script_flag,int opt_flag) {
		int ans = 0;
		if (weld_map_to_list(p, q, p_start_vert, q_start_vert, p_count,
				q_count, weldmapfile,script_flag, opt_flag) == 0)
			return 0;
		if ((ans = weld(p, q, p_start_vert, q_start_vert, opt_flag)) > 0) {
			p.fillcurves();
			q.fillcurves();
			p.set_aim_default();
			q.set_aim_default();
			p.activeNode = p.getGamma();
			q.activeNode = q.getGamma();
			if (weldmapfile == null || weldmapfile.trim().length() < 2)
				msg("weld_with_map: verts " + p_start_vert + " " + q_start_vert
						+ " weldmap=identity map;");
			else
				msg("weld_with_map: verts " + p_start_vert + " " + q_start_vert
						+ " weldmapfile " + weldmapfile);
			return ans;
		}
		errorMsg("Something went wrong with 'weldUsingMap' routine; " +
				"packing(s) "+ "may be corrupted.");
		return ans;
	} 

	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		String str=null;
		
		// ----- findWM ------------
		if (cmd.startsWith("findW")) {
			int domV=0;
			int ranV=0;
			int N=0;

			Iterator<Vector<String>> its=flagSegs.iterator();
			while (its.hasNext()) {
				items=(Vector<String>)its.next();
				if (StringUtil.isFlag(items.get(0))) {
		  			str=(String)items.remove(0);
		  			switch(str.charAt(1)) {
		  			case 'q': // get receiving pack
		  			{
		  				try {
		  					p2=PackControl.cpDrawing[StringUtil.
		  					         qFlagParse(str)].getPackData();
		  				} catch (Exception ex) {
		  					throw new ParserException("Indicated weld partner "+
		  							"pack is not valid");
		  				}
		  				break;
		  			}
		  			} // end of switch
		  		
				} // end of flag processing
				
				try {
					domV=Integer.parseInt(items.remove(0));
					ranV=Integer.parseInt(items.remove(0));
					N=Integer.parseInt(items.remove(0));
				} catch (Exception ex) {
					Oops("usage: findWM -q{p} v w n: "+ex.getMessage());
				}
				return findWeldMap(p1,p2,domV,ranV,N);
			} // end of while
		}
		
		// --------- homeo -------------
		if (cmd.startsWith("writeHomeo")) {
			int n=0;
			boolean script_flag=false;
			if (weldmapDomain==null || weldmapRange==null ||
					(n=weldmapDomain.length)==0 || weldmapRange.length==0 ||
					weldmapRange.length!=n) {
				Oops("weldmapDomain/Range are not set or don't agree");
			}
			
			// default file
			String filename=null;
			try {
				items=flagSegs.remove(0);
				str=items.remove(0);
				if (str.equals("-s")) 
					script_flag=true;
				filename=items.remove(0);
			} catch (Exception ex) {
				filename=new String("homeo.g");
			}
			return this.writeWeldMap(filename,3,script_flag);
		}
		
		// ---------- randC -----------
		if (cmd.startsWith("randC")) {
			int N=4;
			try {
				items=flagSegs.remove(0);
				N=Integer.parseInt(items.remove(0));
			} catch (Exception ex) {
				errorMsg("Failed to get N, set N=4.");
			}
			if (N<4) N=4;
			Complex []circPts=RandPaths.unitCirclePath(N,true);
			Triangulation Tri=RandomTriangulation.randomPolyPts(N*N,false,circPts);
			p2=null;
			try {
				if ((p2=Triangulation.tri_to_Complex(Tri,-1))==null) {
					Oops("tri_to_Complex has failed.");
				}
				p2.chooseAlpha();
				p2.chooseGamma();
				p2.set_aim_default();
				p2.set_rad_default();

				cpCommand(p2,"max_pack");
			} catch (Exception ex) {
				Oops("failed to build packing: "+ex.getMessage());
			}
			return p2.nodeCount;
		}
		
		// ------------ weld ------------
		if (cmd.startsWith("weld")) {
			int qnum=-1;
			String filename=null;
			int adjoin_flag=0;
			int v=0;
			int w=0;
			boolean script_flag=false;
			Iterator<Vector<String>> its=flagSegs.iterator();
			while (its.hasNext()) {
				items=flagSegs.remove(0);
				if (StringUtil.isFlag(items.get(0))) {
		  			str=(String)items.remove(0);
		  			char c=str.charAt(1);
		  			switch(c) {
		  			case 'q': // get receiving pack
		  			{
		  				try {
		  					qnum=StringUtil.qFlagParse(str);
		  					p2=CPBase.cpDrawing[qnum].getPackData().copyPackTo();
		  				} catch (Exception ex) {
		  					throw new ParserException("Indicated weld partner "+
		  							"pack is not valid");
		  				}
		  				
		  				// get starting vertices; default, use first bdry vert for both
		  				try {
		  					v=Integer.parseInt(items.remove(0));
		  					w=Integer.parseInt(items.remove(0));
		  				} catch (Exception ex) {
		  					v=NodeLink.grab_one_vert(p1,"b");
		  					w=NodeLink.grab_one_vert(p2,"b");
		  				}
		  				break;
		  			}
		  			case 'a': // adjoin?? 
		  			{
		  				adjoin_flag=1;
		  				break;
		  			}
		  			case 'f': // fall through
		  			case 's':
		  			{
		  				filename=items.remove(0).trim();
		  				if (c=='s')
		  					script_flag=true;
		  				break;
		  			}
		  			} // end of switch
		  		
				} // end of flag processing
			} // end of while

			return weldUsingMap(p1,p2,v,w,-1,-1,filename,script_flag,adjoin_flag);
		}
		
		// ----------- unweld --------------
		if (cmd.startsWith("unweld")) {
			@SuppressWarnings("unused")
			boolean wantInside=true;
			EdgeLink elist=null;

			Iterator<Vector<String>> its=flagSegs.iterator();
			while (its.hasNext()) {
				items=(Vector<String>)its.next();
				if (StringUtil.isFlag(items.get(0))) {
		  			str=(String)items.remove(0);
		  			switch(str.charAt(1)) {
		  			case 'o': // want outside
		  			{
		  				wantInside=false;
		  				break;
		  			}
//		  			case 'q': // get receiving pack
//		  			{
//		  				try {
//		  					qackData=PackControl.pack[StringUtil.
//		  					         qFlagParse(str)].packData;
//		  				} catch (Exception ex) {
//		  					throw new ParserException("Indicated receiving "+
//		  							"pack is not valid");
//		  				}
		  				
//		  				break;
//		  			}
		  			} // end of switch
		  		
				} // end of flag processing

				if (!its.hasNext()) // last flag segment has the edges
					elist=new EdgeLink(packData,items);
			} // end of segment while
		
			p1=packData.copyPackTo();
			packOut=unweld(p1,elist);
			if (packOut==null) {
				Oops("unweld has failed");
			}
			this.msg("'unweld' result is in 'packOut'");
			return packOut.nodeCount;
		}

		// ========== copy <pnum> 
		if (cmd.startsWith("copy")) { // copy 'packOut' into some pack
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				if (packOut!=null && packOut.nodeCount>0) {
					CirclePack.cpb.swapPackData(packOut,pnum,false);
				}
				else return 0;
			} catch (Exception ex) {
				Oops("Failure to copy 'packOut'; check pack number");
			}
			return packOut.nodeCount;
		}	
		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("findWM","-q{q} v w n",null,
			"find weld map between max packings, 'packData' to pack q, "+
			"starting verts v, w, n edges "+
			"(all if n<0, must be equal-length bdrys)"));
		cmdStruct.add(new CmdStruct("writeHomeo","-[fs] {filename}",null,"Write welding data as "+
				"homeomorphism, [0,1]-->[0,1], to filename (default '/tmp/homeo."));
		cmdStruct.add(new CmdStruct("unweld","[-o] {e..}",null,"cut packing along "+
				"edgelist, result put in 'packOut'; -o means want outside"));
		String info=new String("weld: hyp, max packs; eucl, by arc length: weld map "+
				"in {filename}; option int 'm': if 8 bit is set, then adjoin. Result in " +
				"'packOut'");
		cmdStruct.add(new CmdStruct("weld","-q{p} v w -[fs] {filename} m",null,info));
		cmdStruct.add(new CmdStruct("randC","{N}",null,"Create a packing "+
				"for a Delaunay triangulation of the unit disc using N random "+
				"points on the unit circle, one at the point z=1"));
		cmdStruct.add(new CmdStruct("copy","{pnum}",null,"Results are normally in 'packOut'; "+
				"this will copy them to the designated packing"));
	}	
}
