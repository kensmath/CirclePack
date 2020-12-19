package dcel;

/**
 * In a DCEL structure there is (except in case of the sphere) a 'redChain' of
 * 'RedHEdge's. For any vertex encountered along the redChain, we instantiate
 * a 'preRedVertex' and use this for bookkeeping; we process it to insert 
 * 'RedVertex's in the 'redChain' as necessary to split vertices and form new
 * boundary vertices. 
 * 
 * TODO: 'spokes' is only used during building the redChain, so we might
 * make this an inner class in 'CombDCEL' and only have a "redFlag" set in
 * normal 'Vertex' to indicate a red vertex. (Need to know which vertices
 * are red so we know how to find the appropriate center/radius.)
 * 
 * @author kstephe2, 8/2020
 */
public class RedVertex extends Vertex {

	public HalfEdge[] spokes; // outgoing spokes
	
	public RedVertex(int v) {
		super(v);
	}

}
