package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import exceptions.DCELException;
import komplex.EdgeSimple;

/**
 * In a DCEL structure there is (except in case of the sphere) a 'redChain' of
 * 'RedHEdge's. For any vertex encountered along the redChain, we instantiate
 * a 'RedVertex' and use this for bookkeeping; 'kData[v].redFlag' points to this.
 * 
 * It takes 2 steps to build a 'RedVertex': it is created, then calling
 * routine inserts initial 'redSpoke' and 'inSpoke' and 'num' data,
 * hen we call 'process' to finish up.
 *  
 * The 'RedVertex' has a 'flower' of cclw nghb'ing vertices, but it may break
 * into a number of 'OutIn's and 'bdryFan's of contiguous petals. An 'OutIn' is a
 * pair <o,i> of indices for a 'redSpoke' (outgoing 'RedHEdge' from the 
 * 'redChain') and the index of its paired 'inSpoke', the 'prevRed'. One or
 * more contiguous 'OutIn's may consolidate into a 'bdryFan'. So a 'bdryFan' is an
 * ordered pair <o,i>, where o is index of a 'redSpoke' and j is index of some 
 * cclw 'inSpoke', and any out/in between were pasted (i.e., are 'twinRed's
 * with one another). A vertex is 'interior' iff there's a single 'bdryFan',
 * but there must be at least two 'OutIn's (else this red edge would have
 * collapsed.) 
 * 
 * Processing uses bouquet with initial list of neighbors, but as we 
 * extract the 'OutIn's and 'bdryFan's, vertices get re-indexed, etc. we 
 * adjust so spoke[0] is the beginning of a 'bdryFan', and
 * therefore pairs <i,j> of afan indices satisfy i<j . 
 * 
 * Regarding 'spokes': 
 *  + all outgoing 'HalfEdge's that remain after processing are 
 * 	  included in their original cclw order. 
 *  + But these are generally not like our usual flower: contiguous 
 * 	  'bdryFan's may or may not share a common spoke. The do if 
 *    there's no gap between them, but they're not pasted. 
 *  * For contiguous 'bdryFan's, the last edge of the first and the 
 *    first edge of the next will not form a face, whether or not they
 *    are contiguous entries in 'bouquet'. (We use data in 'RedVertex's
 *    to determine faces for 'RedVertex's.) 
 *  * if 'tmpopen' fails (i.e., the original bouquet was closed)
 *    then due to our re-indexing, first and last spokes are never
 *    pasted (ie. are not one another's 'twinRed's) else this edge
 *    would have been collapsed during construction. 
 *  + So when will first/last spokes be the same? only if last ends 
 *    up in a 'bdryFan'. In this case, 'redSpoke[0]' and 'inSpoke[num]'
 *    are distinct 'RedHEdge's, though they they may share the same 
 *    'myEdge'. 
 *  * TODO: These details play havoc in the CirclePack 'flower' paradigm.
 * 
 * We will us 'bdryFan's with 'spokes' to catalog faces.
 * 
 * @author kstephe2, 8/2020
 *
 */
public class RedVertex extends Vertex {

	int bdryFlag;
	HalfEdge[] spokes; // outgoing spokes
	// one outin and beginning of next (when they're not pasted)
	// A spoke for 'RedHEdge' may be incoming/outgoing (or both)
	int num; // as usual, num+1 is length of spokes 
	public RedHEdge[] redSpoke; // outgoing red spokes (or null)
	public RedHEdge[] inSpoke;  // incoming red spokes (or null)
	public ArrayList<EdgeSimple> redOutIn;  // <o,i> delineates a fan between an outgoing
                                 // and the next cclw incoming.
	public ArrayList<EdgeSimple> bdryFan; // <o,i> fan between outgoing ans cclw incoming, 
									   // but possibly consolidated
	// used during processing 
	boolean tmpopen; 
	
	public RedVertex(int v) {
		super(v);
		redSpoke=null;
		inSpoke=null;
	}

   /**		
	 *After creation, should have initial 'redSpoke' and 'inSpoke' 
	 * data, 'num'. We process to fill 'redOutIn', 'fan', re-index 
	 * 'spokes', to finalize 'redSpoke' and 'inSpoke', etc.
	 * @param bouq int[][]
	 */
	public void process(int[][] bouq) {
	
		// organize temporary 'outins' and 'afans'
		// note they're .w indices may be > num --- work mod(num)
		//    if we have closed flower.
		ArrayList<EdgeSimple> outins=new ArrayList<EdgeSimple>();
		ArrayList<EdgeSimple> afans=new ArrayList<EdgeSimple>();
		bdryFlag=1; // default

		// first build 'outins'
		int count=0;
		if (tmpopen) { // open case
			for (int j=0;j<num;j++) {
				if (redSpoke[j]!=null) {
					int bgn=j;
					int edn=j;
					for (int k=j+1;k<=num;k++) {
						if (inSpoke[k]!=null) {
							if (inSpoke[k]!=redSpoke[j].prevRed)
								throw new DCELException("'redSpoke', inSpoke incompatibility");
							edn=k;
							outins.add(new EdgeSimple(bgn,edn));
							count++;
							k=num+1; // kick out of 'for'
						}
					}
					if (bgn==edn) { // didn't find a match
						throw new DCELException("'didn't find incoming match");
					}
				}
			}
			if (count==0) {
				throw new DCELException("error: there is no redSpoke");
			}
		}
		else {
			// closed case
			// Note that the last 'outin' <j,k> will still have k>j because
			//    we work mod(num).
			// Also, <k,k> is okay if fan is the whole flower, 
			//   but in this case, first and last must not be red 
			//   twins, else this common edge would have been collapsed.
			//   e.g., may be slit ending at non-keeper interior vertex.
			for (int j = 0; j < num; j++) {
				if (redSpoke[j] != null) {
					int bgn = j;
					int edn = j;
					for (int n = 0; n < num; n++) {
						int m = bgn + 1;
						if (inSpoke[m%num] != null) {
							// single fan, should not be pasted to one another
							if ((m%num) == bgn) { 
								if (inSpoke[m%num].twinRed == redSpoke[j] || 
										inSpoke[m%num] == redSpoke[j].twinRed)
									throw new DCELException(
											"single fan should not be pasted; should have collapsed");
							}
							if (inSpoke[m%num] != redSpoke[j].prevRed)
								throw new DCELException("'redSpoke', 'inSpoke' incompatibility");
							edn = m;
							outins.add(new EdgeSimple(bgn, edn));
							count++;
							n = num + 1; // exit to look for next 'out'
						}
					}
					if (bgn == edn && count == 0) { // didn't find a match
						throw new DCELException("'didn't find incoming match");
					}
				} // look for next out
			}
			if (count == 0) {
				throw new DCELException("error: there is no redSpoke");
			}
		}
		
		// now organize temporary 'afans'
		Iterator<EdgeSimple> esit=outins.iterator();
		int start=-1;
		int last=-1;
		EdgeSimple nfan=null;
		while(esit.hasNext()) {
			EdgeSimple es=esit.next();
			if (start==-1) { // start a new search
				start=es.v;
				last=es.w;
				nfan=new EdgeSimple(start,last);
			}
			else {
				// if pasted, adjoin
				if (es.v==last && inSpoke[last].twinRed==redSpoke[es.v] &&
						redSpoke[es.v].twinRed==inSpoke[last]) {
					nfan.w=es.w;
					last=es.w;
				}
				else {
					afans.add(nfan);
					start=-1;
				}
			}
		} // end of 'while'
		
		// have to check if last fan is pasted to first
		int fancount=afans.size();
		EdgeSimple fan1=afans.get(0); // first
		EdgeSimple fan2=afans.get(fancount-1); // last (may = first)
		int a=fan1.v;
		int b=fan2.w;
		if (!tmpopen) { 
			b=b%num;
		}
		
		// single fan? (note: if !tmpopen, more than one 
		//    outin or edge would have collapsed)
		if (fancount==1) { // a single fan? 
			// if ends are pasted, must be interior
			if (a==b && (inSpoke[b].twinRed==redSpoke[a] &&
					redSpoke[a].twinRed==inSpoke[b])) {
					bdryFlag=0;
			}
		}
		// otherwise see if last/first are pasted; if yes, replace
		//   last by new consolidated fan, then remove the first
		else if (a==b && (inSpoke[b].twinRed==redSpoke[a] &&
				redSpoke[a].twinRed==inSpoke[b])) {
			EdgeSimple consol=new EdgeSimple(fan1.v,fan2.w);
			afans.remove(afans.size()-1);
			afans.add(consol);
			afans.remove(0);
		}

		// now to create 'tmpspokes', 'oldnew', and 'newold'
		HalfEdge[] tmpspokes=new HalfEdge[num+1];
		int[] newold=new int[num+1];
		int[] oldnew=new int[num+1];
		int tick=-1;
		
		// now iterate through all 'bdryFan's
		HalfEdge lasthe=null;
		for (int fi=0;fi<afans.size();fi++) { 
			EdgeSimple es=afans.get(fi);
			HalfEdge nexthe=redSpoke[es.v].myEdge;
			if (nexthe!=lasthe) { // not a repeat, so include
				tmpspokes[++tick]=nexthe;
				newold[tick]=es.v;
				oldnew[es.v]=tick;
			}
			for (int j=es.v+1;j<=es.w;j++) {
				nexthe=nexthe.prev.twin; // cclw edge
				tmpspokes[++tick]=nexthe;
				newold[tick]=j;
				oldnew[j]=tick;
			}
		}
		
		// adjust array sizes and indices
		num=tick;
		spokes=new HalfEdge[num+1];
		RedHEdge[] tmpRedSpokes=new RedHEdge[num+1];
		RedHEdge[] tmpInSpokes=new RedHEdge[num+1];
		
		for (int j=0;j<=num;j++) {
			spokes[j]=tmpspokes[newold[j]];
			tmpRedSpokes[j]=redSpoke[newold[j]];
			tmpInSpokes[j]=inSpoke[newold[j]];
		}
		
		// adjust 'outins'
		redOutIn=new ArrayList<EdgeSimple>();
		Iterator<EdgeSimple> oist=outins.iterator();
		while (oist.hasNext()) {
			EdgeSimple es=oist.next();
			es.v=oldnew[es.v];
			es.w=oldnew[es.w];
			redOutIn.add(es);
		}

		// adjust 'fans'
		bdryFan=new ArrayList<EdgeSimple>();
		Iterator<EdgeSimple> afit=outins.iterator();
		while (afit.hasNext()) {
			EdgeSimple es=afit.next();
			es.v=oldnew[es.v];
			es.w=oldnew[es.w];
			bdryFan.add(es);
		}
		
		// precaution in case this wasn't done already
		if (bdryFlag==0)
			spokes[num]=spokes[0];

	} // processing should be complete!
	
	/** 
	 * Find index for incoming 'RedHEdge'.
	 * @param redge RedHEdge
	 * @return index, -1 on failure
	 */
	public int inSpokeIndx(RedHEdge redge) {
		for (int j=0;j<=num;j++) {
			if (inSpoke[j]==redge)
				return j;
		}
		return -1;
	}

	/** 
	 * Find index for incoming 'RedHEdge'; if repeated at end for closed
	 * flower, then 0 is returned.
	 * @param redge RedHEdge
	 * @return index, -1 on failure
	 */
	public int redSpokeIndx(RedHEdge redge) {
		for (int j=0;j<=num;j++) {
			if (redSpoke[j]==redge)
				return j;
		}
		return -1;
	}

	/**
	 * Every spoke must be in at least one 'bdryFan' <i,j>. Return
	 * 'redSpoke[i]' and 'inSpoke[j]'. But the fans are contiguous,
	 * except possibly the end of the last fan with the beginning of
	 * the first, so edges that bound a fan will often belong to two
	 * fans, the cclw end of the first and beginning of the next.
	 * Which fan are we asking for? If ambiguous, 'begin' may tell 
	 * you which to return: begin=true, then return the fan for 
	 * which 'he' is the beginning (i.e., for which 'he' is the 
	 * 'redSpoke'), else the end (i.e., for which 'he' is the 
	 * 'inSpoke'). Should always return some fan unless 'edge' is
	 * not a spoke.
	 * @param edge HalfEdge
	 * @param begin boolean
	 * @return [redspoke,inspoke]
	 */
	public RedHEdge[] myBdryFan(HalfEdge edge,boolean begin) {
		int j=-1;
		for (int n=0;(n<=num && j<0);n++) {
			if (spokes[n]==edge) 
				j=n;
		}
		if (j<0)
			return null;
		RedHEdge[] ans=new RedHEdge[2];
		
		// take first (perhaps only) one
		if (begin || bdryFan.size()==1) { 
			Iterator<EdgeSimple> eit=redOutIn.iterator();
			while (eit.hasNext()) {
				EdgeSimple es=eit.next();
				if (j<=es.w) {
					ans[0]=redSpoke[es.v];
					ans[1]=inSpoke[es.w];
					return ans;
				}
			}
		}
		
		// Else, check for ambiguity. If j=0 we want the last fan.
		// If j==num, then the flower must be open and the only
		//    fan 'he' is in is the last.
		EdgeSimple oi_last=bdryFan.get(bdryFan.size()-1);
		if (j==0 || j==num) {
			ans[0]=redSpoke[oi_last.v];
			ans[1]=inSpoke[oi_last.w];
			return ans;
		}
		
		// else find first fan containing j (not including the last
		//   fan) and return the next fan.
		for (int k=0;k<(bdryFan.size()-1);k++) {
			EdgeSimple es=bdryFan.get(k);
			if (j<es.w) {
				ans[0]=redSpoke[es.v];
				ans[1]=inSpoke[es.w];
				return ans;
			}
			if (j==es.w) {
				es=bdryFan.get(k+1);
				ans[0]=redSpoke[es.v];
				ans[1]=inSpoke[es.w];
				return ans;
			}
		}
		
		EdgeSimple es=bdryFan.get(bdryFan.size()-1);
		ans[0]=redSpoke[es.v];
		ans[1]=inSpoke[es.w];
		return ans;
	}
	
}




