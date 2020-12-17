package dcel;

/**
 * In a DCEL structure there is (except in case of the sphere) a 'redChain' of
 * 'RedHEdge's. For any vertex encountered along the redChain, we instantiate
 * a 'NakedVertex' and use this for bookkeeping; we process it to insert 
 * 'RedVertex's in the 'redChain' as necessary to split vertices and form new
 * boundary vertices. AFter catalogi'kData[v].redVert' points to the 'RedVertex'.
 * 
 * @author kstephe2, 8/2020
 */
public class RedVertex extends Vertex {

	public HalfEdge[] spokes; // outgoing spokes
	
	public RedVertex(int v) {
		super(v);
	}
 
	/** Revert to regular 'Vertex'
	 * @return new Vertex
	 */
	public Vertex revert() {
		Vertex vtx=new Vertex();
		vtx.halfedge=halfedge;
		vtx.vertIndx=vertIndx;
		vtx.bdryFlag=bdryFlag;
		vtx.util=util;
		return vtx;
	}
}
