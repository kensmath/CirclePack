package listManip;

import exceptions.LayoutException;

/**
 * Linked list of indices (often vertices, but not always).
 * @author kens
 *
 */
public class VertList {
	
	public int v;           // index for this vertex
	public VertList next; 
	
	//Constructor(s)
	public VertList() { 
		v=0;
		next=null;
    }
	
	// create with vertex vv
	public VertList(int vv) {
		v=vv;
		next=null;
	}
	
	// create new entry after current
	public VertList(VertList current,int vv) throws LayoutException {
		if (current==null) throw new LayoutException();
		current.next=this;
		v=vv;
		next=null;
	}
	
	// convert to NodeLink
	public NodeLink toNodeLink() {
		NodeLink nlink=new NodeLink();
		if (v==0)
			return null;
		nlink.add(v);
		VertList vnext=next;
		while (vnext!=null && vnext.v!=0) {
			nlink.add(vnext.v);
			vnext=vnext.next;
		}
		return nlink;
	}

	// convert to FaceLink
	public FaceLink toFaceLink() {
		FaceLink flink=new FaceLink();
		if (v==0)
			return null;
		flink.add(v);
		VertList vnext=next;
		while (vnext!=null && vnext.v!=0) {
			flink.add(vnext.v);
			vnext=vnext.next;
		}
		return flink;
	}
}