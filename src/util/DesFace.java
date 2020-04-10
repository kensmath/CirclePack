package util;

public class DesFace {
	int orient;	/* half-plane, +1 upper, -1 lower */
	int[] verts[];	/* vertices, always listed 0, 1, infty */
	int newVert_vert[];/* utility storage for resetting vert no's */
	int nghb[];	/* neighboring faces across [0,1], ... */
	int mark;	/* useful flag */
	int midvert;	/* vert at face center */
	int midices[]; /* flower index in verts[j] towards face center */
	int rwb_flag;	/* for marking red chain */

	// Constructor
	public DesFace() {
		;//verts=
	}
}
