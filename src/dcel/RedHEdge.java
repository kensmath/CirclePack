package dcel;

public class RedHEdge {
	public HalfEdge myedge;
	public RedHEdge next;
	public RedHEdge prev;
	
	// Constructor
	public RedHEdge(HalfEdge he) {
		myedge=he;
		next=prev=null;
	}
}
