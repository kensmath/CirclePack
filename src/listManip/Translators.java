package listManip;

import java.util.Iterator;

import komplex.EdgeSimple;
import packing.PackData;

/**
 * For searching various lists of vertices/edges/faces, possibly with 
 * translations via vertexMap's.
 * @author kens
 *
 */
public class Translators {
	
	/** 
	 * Find mates to v in elist (e.g., vertexMap). Return just v if 
	 * elist is null or return 0 if v is not there. If 'forward' is true, 
	 * return w's in pairs (v,w), else w's in pairs (w,v).
	 * @param elist EdgeLink, (e.g., a 'vertexMap')
	 * @param v int
	 * @param forward boolean: true, return w's in (v,w); else return
	 * w's in (w,v).
	 * @return NodeLink
	*/
	public static NodeLink vert_translate(EdgeLink elist,int v,boolean forward) {
		if (elist==null) return new NodeLink((PackData)null,v);
		NodeLink nl=new NodeLink((PackData)null);
		Iterator<EdgeSimple> el=elist.iterator();
		EdgeSimple edge=null;
		while (el.hasNext()) {
	      edge=(EdgeSimple)el.next();
	      if (forward && edge.v==v) nl.add(edge.w);
	      if (!forward && edge.w==v) nl.add(edge.v);
		}
		return nl;
	}

	/**
	 * Idea is to translate face 'f_in' of 'source_p' 
	 * to 'target_p'. If 'elist' is not null, use it for
	 * translatation as a 'vertexMap'.
	 * Trivial case: if elist==null and source_p==target_pp, 
	 * return f_out=f_in. 
	 * Return 0 if vertices of 'f_in' are not found or (their 
	 * translates) don't define a face in 'target_p'. 
	 * TODO: translation only uses first of potentially many
	 * matches in 'elist'; hard to see an easy way to handle 
	 * multiple translation cases.
	 * @param source_p PackData, source
	 * @param elist EdgeLink
	 * @param f_in int, face index in source_p
	 * @param target_p PackData
	 * @return int, 0 on failure
	 */
	public static int face_trans(PackData source_p,PackData target_p,
			int f_in, EdgeLink elist) {
	    if (target_p==source_p && elist==null) // just return f_in alone
	    	return f_in;
	    if (elist==null || source_p==null ||
	    		f_in<0 || f_in>source_p.faceCount) 
	    	return 0;
	    if (target_p==null)
	    	target_p=source_p;

	    int[] source_v=source_p.getFaceVerts(f_in);
	    int[] target_v=new int[source_v.length];
	    for (int j=0;j<source_v.length;j++) {
	    	target_v[j]=elist.findW(source_v[j]);
	    	if (target_v[j]<=0)
	    		return 0;
	    }
	    
	    // now have to find face index
	    return target_p.what_face(target_v[0],target_v[1],target_v[2]);
	}
	
	/**
	 * See description of 'face_trans'
	 * @param p 'PackData'
	 * @param elist 'EdgeLink'
	 * @param e_in
	 * @param q
	 * @param forward
	 * @return  'EdgeSimple', null on failure
	 */
	public static EdgeSimple edge_translate(PackData p, EdgeLink elist,
			EdgeSimple e_in, PackData q, boolean forward) {
		if (p == null || !p.status)
			return null;
		if (q == null || !q.status)
			q = p;
		if (e_in.v < 0 || e_in.w < 0
				|| (forward && (e_in.v > p.nodeCount || e_in.w > p.nodeCount))
				|| (!forward && e_in.v > q.nodeCount || e_in.w > q.nodeCount))
			return null;
		if (q == p && elist == null)
			return e_in; // nothing to do

		int v;
		int w;
		if (forward) {
			v=elist.findW(e_in.v);
			w=elist.findW(e_in.w);
		}
		else {
			v=elist.findV(e_in.v);
			w=elist.findV(e_in.w);
		}
		
		if (forward && q.nghb(v, w) >= 0)
			return new EdgeSimple(v, w);
		if (!forward && p.nghb(v, w) >= 0)
			return new EdgeSimple(v, w);
		return null;
	}

	// TODO: move this to VertexMap.java
	/**
	 * This 'composes' two VertexMaps (one or both may be inverted), so result
	 * is vm1 followed by vm2. Return null if either input is null or if there
	 * are no matches. 
	 * @param vm1, first map: (v,w), think v --> w.
	 * @param vm2, second: (w,u), think w--> u
	 * @param rev1, if true, reverse vm1 before proceeding
	 * @param rev2, if true, reverse vm2 before proceeding
	 * @return new VertexMap with (v,u)
	 */
	public static VertexMap composeVMs(VertexMap vm1,boolean rev1,VertexMap vm2,boolean rev2) {
		if (vm1==null || vm2==null || vm1.size()==0 || vm2.size()==0) return null;
		VertexMap Vm1,Vm2;
		if (rev1) Vm1=vm1.flipEachEntry();
		else Vm1=vm1;
		if (rev2) Vm2=vm2.flipEachEntry();
		else Vm2=vm2;
		VertexMap vm_out=new VertexMap();
		Iterator<EdgeSimple> vmit=Vm1.iterator();
		EdgeSimple edge=null;
		int u;
		while (vmit.hasNext()) {
			edge=(EdgeSimple)vmit.next();
			if ((u=Vm2.findW(edge.w))>0) vm_out.add(edge.v,u);
		}
		if (vm_out.size()>0) return vm_out;
		return null;
	}
			
}
