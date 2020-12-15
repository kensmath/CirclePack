package dcel;

import java.util.ArrayList;

import allMains.CirclePack;
import deBugging.DCELdebug;
import exceptions.DCELException;

/**
 * A temporary object to hold 'redSpoke' and 'inSpoke' data
 * until 'RedVertex's are created in 'process()'.
 * @author kstephe2, 8/2020
 *
 */
public class PreRedVertex extends RedVertex {

	public RedHEdge[] redSpoke;    // outgoing 'RedHEdge'
	public RedHEdge[] inSpoke;     // incoming 'RedHEdge'
	public int num;
	boolean closed;         // if true, then original flower was closed
	
	// Constructor
	public PreRedVertex(int v) {
		super(v);
		closed=false;
		num=-1;
	}

	/**
	 * This vertex has a closed flower, and we rotate so the
	 * first petal is 'redSpoke'. Further, if there is a "slit", 
	 * meaning a common direction with 'red/inSpoke' which are 
	 * not pasted, then the first slit's 'redSpoke' will be rotated
	 * to be the first petal. If there are no slits then return 0, 
	 * meaning this vertex, although on the 'redChain', must be interior.
	 * @return 'num', -1 on error, and 0 if there are no slits
	 */
	public int rotateMe() {
		if (redSpoke==null) {
			return -1;
		}
		int num=redSpoke.length-1;
		boolean noslit=false;

		// find the first 'redSpoke'
		int firstRSj = -1;
		for (int j = 0; (j < num && firstRSj < 0); j++)
			if (redSpoke[j] != null)
				firstRSj = j;
		// 
		int J = -1;
		for (int j = 0; (j < num && J < 0); j++) {
			if (redSpoke[j] != null) {
				if (inSpoke[j] == null || inSpoke[j].twinRed != redSpoke[j])
					J = j;
			}
		}
		// no slit found, so start at first 'redSpoke'
		if (J < 0) {
			noslit=true;
			J = firstRSj;
		}
		
		// interior?
		if (noslit) {
			redSpoke[0]=redSpoke[firstRSj]; // temporary to set 'helpedge'
			return 0;
		}

		// rotate everything by J
		RedHEdge[] tmpRedSpoke = new RedHEdge[num + 1];
		RedHEdge[] tmpInSpoke = new RedHEdge[num + 1];
		for (int j = 0; j < num; j++) {
			tmpRedSpoke[j] = redSpoke[(j + J) % num];
			tmpInSpoke[j] = inSpoke[(j + J) % num];
		}
		// close up
		tmpRedSpoke[num] = tmpRedSpoke[0];
		tmpInSpoke[num] = tmpInSpoke[0];

		// save
		redSpoke = tmpRedSpoke;
		inSpoke = tmpInSpoke;
		
		return num;
	}

	/**
	 * The 'PreRedVertex' is temporary; we use it to gather 'redSpoke', 'inSpoke',
	 * and we 'rotateMe' if necessary to avoid wrapping problems. In 'process' 
	 * we then find "fans" of contiguous vertices between 
	 * a 'redSpoke' and a cclw 'inSpoke', possibly with intermediate twin'ed 
	 * in/out red edges. We introduce a new 'RedVertex' in the 'redChain' for 
	 * each fan and also add new 'HalfEdge's to last 'inSpoke' of each fan. 
	 * Note that the first of the returned 'RedVertex's will replace the
	 * original, the others will have 'vertIndx's adjusted when the calling 
	 * routine catalogs and vertices.
	 * @return ArrayList<RedVertex> count of new 'RedVertex's
	 */
	public ArrayList<RedVertex> process() {
		boolean debug=false; // debug=true;
		HalfEdge he=null;
		bdryFlag=1;
		ArrayList<RedVertex> redList=new ArrayList<RedVertex>();

		// rotate to get 'redSpoke' as first edge so we avoid worrying 
		//   about index wrapping around; finish if it's interior 
		//   (i.e. all red edges are paired and pasted) 
		if (closed && rotateMe()==0) { 
			RedVertex newV = new RedVertex(vertIndx);
			newV.bdryFlag=0;
			newV.halfedge=redSpoke[0].myEdge;

			// fix 'origin's cclw and return, don't fill 'spokes'
			he=newV.halfedge;
			he.origin=newV;
			do {
				he = he.prev.twin;
				he.origin=newV;
			} while (he != newV.halfedge);
			redList.add(newV);
			return redList;
		}

		// redList.get(0).spokes[0].origin.vertIndx;

		// look for successive 'fans' between 'redSpoke' and 'inSpoke'
		for (int j = 0; j < num; j++) {
			if (redSpoke[j] != null) { // should get hit when j=0
				int v = j;
				int w = j;
				for (int k = j + 1; k <= num; k++) {
					if (inSpoke[k] != null
							&& (redSpoke[k] == null || (redSpoke[k] != null && redSpoke[k].twinRed != inSpoke[k]))) {
						w = k;
						j = w - 1; // when we continue search, this will repeat the last direction
						k = num + 1; // kick out of 'for'
					}
				}
				if (v == w) { // didn't find a match to form the fan
					CirclePack.cpb.errMsg("problem matching in/red spokes");
					throw new DCELException("'didn't find 'inSpoke' match with 'redSpoke'");
				}
				
				// The 'spokes' now form a fan about a new 'RedVertex', from
				// 'redSpoke[v]' cclw to 'inSpoke[w]'. If 'redSpoke[w]' exists
				// (and so was not pasted), then replace the last spoke in this
				// fan by a new twin for the 'inSpoke' (prev/next readjusted later)
				RedVertex newV = new RedVertex(vertIndx); 
				newV.halfedge=redSpoke[v].myEdge;
				redSpoke[v].myEdge.origin=newV;
				newV.spokes = new HalfEdge[w-v + 1];
				newV.bdryFlag = 1;
				if (redSpoke[w]!=null) {
					// replace last of this fan
					HalfEdge new_w = new HalfEdge();
					new_w.face = new Face();
					new_w.face.edge = new_w;
					new_w.origin = newV;
					inSpoke[w].myEdge.twin = new_w;
					new_w.twin = inSpoke[w].myEdge;
					newV.spokes[w-v]=new_w;
				}
				
				he = redSpoke[v].myEdge; //	DCELdebug.triVerts(he);
				he.origin=newV;
				newV.spokes[0]=he;
				int safety = 100;
				int tick = 0;
				do {
					he = he.prev.twin;
					he.origin=newV;
					newV.spokes[++tick] = he;
					safety--;
				} while (he != inSpoke[w].myEdge.twin && safety > 0);
				if (safety == 0)
					throw new DCELException("infinite look, 'redSpoke' for <" + redSpoke[v].myEdge.origin.vertIndx + " "
							+ redSpoke[v].myEdge.twin.origin.vertIndx + ">");

				// The 'spokes' now form a fan about a new 'RedVertex', from
				// 'redSpoke[v]' cclw to 'inSpoke[w]'. If 'redSpoke[w]' exists
				// (it can't be pasted), then replace the last spoke in this
				// fan by a new twin for the 'inSpoke' (prev/next readjusted later)
				if (redSpoke[w]!=null) {
					// replace last of this fan
					HalfEdge new_w = new HalfEdge();
					new_w.face = new Face();
					new_w.face.edge = new_w;
					new_w.origin = newV;
					inSpoke[w].myEdge.twin = new_w;
					new_w.twin = inSpoke[w].myEdge;
					newV.spokes[w-v]=new_w;
				}
				
				// debug=true;
				if (debug) {
					DCELdebug.spokeFaces(newV);
				}
				
				// add this to our array
				redList.add(newV);
			}

		}
		if (redList.size() == 0) {
			throw new DCELException("error: there are no 'bdryFan's");
		}
		return redList;
	}
}
