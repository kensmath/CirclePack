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
	 * @param EdgeLink, elist (e.g., vertexMap)
	 * @param int v
	 * @param forward: boolean: true, return w's in (v,w); else return
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
	 * Idea is to translate face 'f_in' of p to face of q, including
	 * translation via 'elist' (e.g., like 'vertexMap') if elist is not null. 
	 * Trivial case: if elist==null and q==p, return f_out=f_in. 
	 * Return 0 if vertices of f_in are not found or (their translates) don't 
	 * define a face in q. If 'forward' is true, translate from p to q,
	 * else from q to p. 
	 * TODO: note that translation only uses first of potentially many mates.
	 * Hard to see an easy way to handle multiple translation cases.
	 */
	public static FaceLink face_translate(PackData p,EdgeLink elist,int f_in,
			PackData q,boolean forward) {
	    if (p==null || !p.status) return null;
	    if (q==null || !q.status) q=p; 
	    if (f_in<0 || (forward && f_in>p.faceCount) 
	    		|| (!forward && f_in>q.faceCount)) return null;
	    if (q==p && elist==null) // just return f_in alone
	    	return new FaceLink((PackData)null,Integer.valueOf(f_in).toString()); 
	    int v0=0,v1=0,v2=0;
	    if (forward) {
		v0=p.faces[f_in].vert[0];
		v1=p.faces[f_in].vert[1];
		v2=p.faces[f_in].vert[2];
	    }
	    else {
		v0=q.faces[f_in].vert[0];
		v1=q.faces[f_in].vert[1];
		v2=q.faces[f_in].vert[2];
	    }
	    int w0,w1,w2;
	    try {
	    	w0=vert_translate(elist,v0,forward).get(0);
	    	w1=vert_translate(elist,v1,forward).get(0);
	    	w2=vert_translate(elist,v2,forward).get(0);
	    } catch (Exception ex) {
	    	return null;
	    }
	    int f_out=0;
	    if (forward) {
	    	f_out=q.what_face(w0,w1,w2);
	    	return new FaceLink(q,Integer.valueOf(f_out).toString());
	    }
	    else { 
	    	f_out=p.what_face(w0,w1,w2);
	    	return new FaceLink(p,Integer.valueOf(f_out).toString());
	    }
	}
	
	// TODO: need method for 'HalfEdge' links.

	/**
	 * See description of face_translate
	 * @param p 'PackData'
	 * @param elist 'EdgeLink'
	 * @param e_in
	 * @param q
	 * @param forward
	 * @return  'EdgeLink' linked list of edges
	 */
	public static EdgeLink edge_translate(PackData p, EdgeLink elist,
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
			return new EdgeLink((PackData)null,e_in); // nothing to do
		int v,w;
		try {
			v = vert_translate(elist, e_in.v, forward).get(0);
			w = vert_translate(elist, e_in.w, forward).get(0);
		} catch (Exception ex) {
			return null;
		}
		
		if (forward && q.nghb(v, w) >= 0)
			return new EdgeLink(q,new EdgeSimple(v, w));
		if (!forward && p.nghb(v, w) >= 0)
			return new EdgeLink(p,new EdgeSimple(v, w));
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
