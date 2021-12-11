package packing;

import java.awt.Color;
import java.util.Iterator;

import exceptions.LayoutException;
import exceptions.RedListException;
import komplex.RedEdge;
import komplex.RedList;
import komplex.SideDescription;
import listManip.PairLink;
import listManip.VertList;
import util.BuildPacket;
import util.ColorUtil;

/**
 * A class and methods to handle 'red chain' creation, manipulation, etc. 
 * Caution: a packing's red faces and edges involve two linked lists:
 *   the 'redChain' for laying out the packing, and the 'firstRedEdge'
 *   for 'RedEdge's. Due to 'blue' faces, this second list may have
 *   repeated faces that don't occur in 'redChain'.
 * @author kens
 *
 */
public class RedChainer {
	
	PackData p;
	
	// Global variables: caution, used ONLY during local processing
	VertList fDo_tmp;     // face drawing order
	RedList red_chain;    // pointer to red chain during construction
	RedList stop_ptr;     // runaway prevention during construction
	int tripCount;        // Counts of passes during 'build_redchain'
	boolean poisonFlag;   // if true, then watch for poison vertices/edges
	
	// some utility variables
	public RedList util_red;
	public boolean util_bool;
	public int util_1;  // utility for passing values
	public int util_2;
	
	// Constructor
	public RedChainer(PackData pd) {
		p=pd;
		red_chain=null;
		p.firstRedEdge=null;
	}

	/** 
	 * Return outside vert shared by redface and its successor. 
	 * Return 0 on error.
	*/
	public int red_share_vert(PackData p,RedList redface) {
		int indx;

		if (redface==null || !redface.hasNext()) return 0;
		int cface=redface.face;
		int nface=redface.next.face;
		if ((indx=p.face_nghb(nface,cface))<0)
			return 0;
		return p.faces[cface].vert[indx];
	}

		/** 
		 * Process "red chain" of 'RedList' (and 'RedEdge') objects: does 
		 * NOT change the current pack data. Return a 'RedPacket' with
		 * 'firstRedFace' and 'PairLink', the linked list of side-pairings,
		 * and calling routine can use them (normally setting 
		 * 'p.firstRedEdge' and 'p.sidePairs').	
		 * 
		 * This methods sets all 'vIndex' data, creates a subordinate 
		 * closed 'RedEdge' chain by converting 'RedList' entries that have 
		 * red edges into 'RedEdge' entries, sets the various 'RedEdge' data
		 * ('cornerFlag', 'crossRed', etc.). Note that the 'redChain' element 
		 * might have been converted to a 'RedEdge' element, invalidating the 
		 * pointer; on return, calling routine resets 'p.redChain' to 'p.firstRedEdge'. 
		 * @param redface RedList, pointing to existing red chain (often 
		 * 'PackData.redChain')
		 * @param pflag boolean (not used right now, associated with "poison" vertices)
		 * @return 'BuildPacket' with 'firstRedEdge', 'PairLink'. Null if don't exist.
		*/
		public BuildPacket redface_comb_info(RedList redface,boolean pflag)
		  throws RedListException {
		  int click=0;
		  int indx;
		  RedList trace;
		  boolean keepon=true;
		  boolean debug=false;
		  RedEdge firstRedEdge;
		  BuildPacket bP=null;
		  
		  // initiate the side-pairing data.
		  PairLink e_pairs=new PairLink(p);
		  
		  /* The "red" edges are the edges of the complex on the "outside" of 
		   * the red chain of faces. Some faces in the red chain have these, 
		   * some don't. A red face which is "blue" will necessarily have two 
		   * red edges --- these faces always cause extra problems. */

		  /* Each face is "responsible" (due to red chain drawing order) for 
		   * placing one circle (other faces may place the same circle in 
		   * other spots). 'vIndex' points to that circle in 'face.vert[]'.
		   * Thus 'vIndex' points to the circle NOT shared with the previous 
		   * face in the red chain. CAUTION: 'Face.indexFlag' points to a 
		   * different vertex; namely, vert[indexFlag] and vert[(indexFlag+1)%3] 
		   * are USED TO PLOT the vertex associated with 'vIndex'.*/

		  /* Except for blue faces, the edge ENDING at the 'vIndex' vertex 
		   * is the red edge of the face (ie, it's either a bdry edge or 
		   * a paired edge). */

		  /* Note: for a blue face, the 'vIndex' vert is the isolated vert 
		   * (not shared by contiguous faces of the red chain) and this is
		   * the end of its first red edge. The vert at the end of its second
		   * red edge is the responsibility of some previous face (hopefully
		   * just one previous, but not neccessarily) in the red chain. That 
		   * circle has to be in place before the 'vIndex'ed circle of the blue 
		   * face can be drawn. */

		  if (redface.next.face==redface.prev.face)
		      redface=redface.next; // avoid starting at blue face 

		  /* TODO: would like code to eliminate as many blue faces as possible
		   * (while remaining cognizant of poison verts) for a "nicer" red chain.*/ 

		  // ================= set all 'vIndex's =============
		  trace=redface;
		  keepon=true;
		  firstRedEdge=null;
		  while (trace!=redface || keepon) {
		      keepon=false;
		      indx=(p.face_nghb(trace.prev.face,trace.face)+1) % 3;
		      trace.vIndex=(indx+1) % 3;
		      if (firstRedEdge==null && (indx=trace.redEdgeIndex())>=0) {
		    	  firstRedEdge=RedList.redEdgeMe(trace,indx);
		    	  if (redface==trace) 
		    		  redface=firstRedEdge;
		    	  trace=firstRedEdge;
		      }
		      trace=trace.next;
		  }

		  // ============= create linked 'RedEdge' list =========
		  RedEdge etrace=firstRedEdge;
		  RedList nextList=null;
		  while (etrace!=null && (nextList=RedEdge.nextRedEdge(etrace))!=null) {
			  /* First check if etrace is "blue" and associated with its first 
			   * red edge; if so, then clone new 'RedEdge' copy to be associated
			   * with the second red edge. Note: this is only circumstance where 
			   * 'nextRedEdge' doesn't  actually advance the edge pointer.*/
			  if (nextList==etrace) {
				  if (nextList==firstRedEdge) { // are we really at end? or only beginning?
					  if (firstRedEdge.nextRed!=null 
							  && firstRedEdge.face==firstRedEdge.nextRed.face) {
						  // yes, this is end; would already have been handled, so
						  etrace=null; // kick out of 'while'
					  }
				  }
				  // otherwise, clone 'etrace', getting 'newBlue', and continue
				  int ix=(p.face_nghb(etrace.prev.face,etrace.face)+2)%3;
				  RedEdge newBlue=RedList.redEdgeMe(etrace, ix);
				  
				  // weave into 'RedEdge' list, but make invisible in redchain
				  newBlue.nextRed=etrace.nextRed;
				  etrace.nextRed=newBlue;
				  newBlue.prevRed=etrace;
				  // Just to know where we are in red chain, duplicate 'prev'
				  //   because it generally holds the center for the circle
				  //   'newBlue' is responsible for. Set 'next' to null
				  // Note that 'newBlue' is not pointed TO in red chain.
				  newBlue.next=etrace.next; // signifies clone of a blue face
				  newBlue.prev=etrace.prev;
				  // BUT: in this case, 'redEdgeMe' has moved the 'redList' 
				  //   prev/next pointers to 'newBlue'; we have to shift them
				  //   back to etrace. THUS, 'etrace' is seen when traversing 
				  //   the 'redChain' but 'newBlue' is invisible (though BOTH
				  //   'etrace' and 'newBlue' are seen when transiting the 
				  //   'firstRedEdge' list).
				  newBlue.prev.next=etrace;
				  newBlue.next.prev=etrace;
				  // 'next' not needed, so null signifies this is blue clone
				  newBlue.next=etrace.next; 
				  // should be responsible for next vertex
				  newBlue.vIndex=(etrace.vIndex+1)%3;
				  
				  etrace=newBlue; // to continue 'while'
			  }
			  else {
				  if (nextList==firstRedEdge) { // reached end, wrap up
					  firstRedEdge.prevRed=etrace;
					  etrace.nextRed=firstRedEdge;
					  etrace=null; // to kick out of 'while'
				  }
				  else {
					  RedEdge newEdge=RedList.redEdgeMe(nextList);
					  newEdge.prevRed=etrace;
					  newEdge.nextRed=etrace.nextRed; // probably null
					  etrace.nextRed=newEdge;
					  etrace=newEdge; // to cintinue 'while'
				  }
			  }
		  } // end of 'while'; should now have full closed 'RedEdge' linked list

		  // initialize 'crossRed' and 'cornerFlag'
		  etrace=firstRedEdge;
		  keepon=true;
		  while (etrace!=firstRedEdge || keepon) {
			  keepon=false;
			  etrace.crossRed=null;
			  etrace.cornerFlag=0;
			  etrace=etrace.nextRed;
		  }
		  
		  // ================= store 'crossRed' info =============
		  etrace=firstRedEdge;
		  RedEdge chaser=null;
		  keepon=true;
		  click=0;
		  while (etrace!=firstRedEdge || keepon) {
			  keepon=false;
			  chaser=etrace.nextRed;
			  while (etrace.crossRed==null && chaser!=firstRedEdge) {
				  if (etrace.isPaired(chaser)) {
					  etrace.crossRed=chaser;
					  chaser.crossRed=etrace;
					  click++;
				  }
				  chaser=chaser.nextRed;
			  }
			  etrace=etrace.nextRed;
		  }

		  // ============= mark corners =============
		  etrace=firstRedEdge;
		  keepon=true;
		  while (etrace!=firstRedEdge || keepon) {
			  keepon=false;
			  if (etrace.crossRed!=null) { 
				  if (etrace.crossRed.nextRed.crossRed!=etrace.prevRed) {
					  etrace.cornerFlag |= 1;  // beginning of "side"
					  etrace.prevRed.cornerFlag |= 2; // end of "side"
					  etrace.crossRed.cornerFlag |= 2; // end of "side"
					  etrace.crossRed.nextRed.cornerFlag |= 1; // beginning of "side"
				  }
			  }
			  etrace=etrace.nextRed;
		  }
		  
		  // ============= create 'SideDescription's ===========
		  // no pairings found? iff red chain defines complex simply connected.
		  if (click==0) { 
			  if (firstRedEdge==null) return null; // is a sphere or there's an error
			  firstRedEdge.cornerFlag = 1; // call this the start
			  firstRedEdge.prevRed.cornerFlag=2; // call this the end
			  SideDescription epair=new SideDescription(p);
			  epair.startEdge=firstRedEdge;
			  epair.endEdge=firstRedEdge.prevRed;
			  epair.color=ColorUtil.getFGColor();
			  e_pairs.add(epair);
			  bP=new BuildPacket();
			  bP.firstRedEdge=firstRedEdge;
			  bP.redList=firstRedEdge;
			  bP.sidePairs=e_pairs;
			  bP.success=true;
			  bP.buildMsg=new String("no pairings");
			  return bP;
		  }
		  
		  etrace=firstRedEdge;
		  keepon=true;
		  SideDescription sideDes=null;
		  
// DEbug			  
//		  System.out.println("firstRedEdge vert="+firstRedEdge.vert(firstRedEdge.vIndex)+" and hash = "+firstRedEdge.hashCode());

		  // search for side beginnings ONLY; pair up later
		  while (etrace!=firstRedEdge || keepon) {
			  keepon=false;

			  if ((etrace.cornerFlag & 1)==1) { // beginning
				  sideDes=new SideDescription(p);
				  sideDes.startEdge=etrace;

// DEbug
//				  System.out.println("etrace hash is "+etrace.hashCode());
//				  System.out.println("sideDrs.startEdge is "+sideDes.startEdge.hashCode()+"\n");

				  RedEdge goalong=null;
				  if ((etrace.cornerFlag & 2)==2) // same edge also an end?
					  goalong=etrace;
				  else {
					  goalong=etrace.nextRed;
					  while (goalong!=etrace && !((goalong.cornerFlag & 2)==2))
						  goalong=goalong.nextRed;
					  if (goalong==etrace) throw new RedListException();
				  }
				  sideDes.endEdge=goalong;
				  int sz=e_pairs.size();
				  sideDes.spIndex=sz;
				  sideDes.color=ColorUtil.spreadColor(sz); // distinct colors
				  e_pairs.add(sideDes);
			  }
			  etrace=etrace.nextRed;
		  }

		  Iterator<SideDescription> spit=e_pairs.iterator();
		  
		  SideDescription tmppair=null;
		  while (spit.hasNext()) {
			  tmppair=(SideDescription)spit.next();
			  if ((tmppair.startEdge.cornerFlag & 1)==1) {
				  int matedex=PairLink.find_mate(e_pairs,tmppair);
				  if (matedex>=0) {
					  tmppair.mateIndex=matedex;
					  tmppair.pairedEdge=(SideDescription)e_pairs.get(matedex);
				  }
				  else {
					  tmppair.mateIndex=-1;
					  tmppair.pairedEdge=null;
				  }
			  }
		  }
		  
		  // pass through again to get side-pair colors to match
		  spit=e_pairs.iterator();
		  tmppair=null;
		  while (spit.hasNext()) {
			  tmppair=(SideDescription)spit.next();
			  if (tmppair.pairedEdge!=null)
				  tmppair.pairedEdge.color=new Color(tmppair.color.getRed(),tmppair.color.getGreen(),tmppair.color.getBlue());
		  }
		  
		  bP=new BuildPacket();
		  bP.firstRedEdge=firstRedEdge;
		  bP.redList=firstRedEdge;
		  bP.sidePairs=e_pairs;
		  bP.success=true;
		  return bP;
		}

		/** 
		 * The "red chain" is a simple (possibly with some retracing)
		 * closed linked chain of faces which defines a simply connected 
		 * core of faces for use with layouts of circle packings. 
		 * It is needed for multiply-connected complexes and in 
		 * defining subcomplexes, as with 'cookie'. We construct the
		 * red chain and face ordering from 'seed', remaining cognizant 
		 * of any vertices in 'PackData's 'poisonVerts' and 'poisonEdges'
		 * arrays. In 'redface_comb_info', "red chain" is processed and 
		 * a closed linked list of 'RedEdge's is created subordinate 
		 * to it. Working global pointers to the red chain and face 
		 * order are in 'red_chain' and 'fDo_tmp'. Resulting red chain 
		 * and face order passed back via a 'BuildPacket'.
		 * @param int seed vertex
		 * @param boolean pflag; true, watch for poison vertices/edges
		 * @return new 'BuildPacket' containing 'redlist', 'faceOrdering'.
		*/
		public BuildPacket build_redchain(int seed,boolean pflag)
		throws LayoutException, RedListException {
		  int f,num,loop_count=0,looplimit,w;
		  boolean hit_flag=true;
		  boolean debug=false;
		  BuildPacket buildPacket=new BuildPacket();

		  // Return error if 'p' is a sphere and there are no poisons
		  if (!pflag && p.intrinsicGeom>0) {
			  buildPacket.buildMsg="Packing is a sphere and there are no designated poison vertices";
			  buildPacket.success=false;
			  buildPacket.redList=null;
			  buildPacket.faceOrdering=null;
			  return buildPacket;
		  }
		  
		  // initialize variables
		  poisonFlag=pflag;
		  for (int i=1;i<=p.faceCount;i++)
			  p.faces[i].rwbFlag=0;

		  /* Start initial red chain with faces about seed (assume seed 
		   * not poison) Recall: our 'RedList' call should maintain the 
		   * red chain as a closed list. Also, 'rwbFlag' > 0 should count 
		   * times a face is in the red chain, while < 0 means "white", 
		   * i.e., inside the red chain. */
		  num=p.countFaces(seed);
		  f=p.getFaceFlower(seed,0);
		  red_chain=new RedList(p,f); // start fresh
		  p.faces[f].rwbFlag=1;
		  p.faces[f].indexFlag=red_chain.vIndex=p.face_index(f,seed);
		  
		  VertList faceOrder=new VertList(f);
		  fDo_tmp=faceOrder; // global pointer to faceOrder 
		  int[] faceFlower=p.getFaceFlower(seed);
		  for (int i=1;i<num;i++) {
		      f=faceFlower[i];
		      fDo_tmp=new VertList(fDo_tmp,f);
		      red_chain=new RedList(red_chain,f);
		      red_chain.done=isDone(p,red_chain);
		      p.faces[f].rwbFlag++;
		      p.faces[f].indexFlag=p.face_index(f,seed);
		      red_chain.vIndex=((p.faces[f].indexFlag+2)%3);
		  }
		  
		  // If 'seed' is boundary, backtrack to get its full flower
		  if (p.isBdry(seed))  
		      for (int i=num-2;i>0;i--) {
				  red_chain=new RedList(red_chain,f);
			      red_chain.done=isDone(p,red_chain);
				  f=faceFlower[i];
				  w=p.kData[seed].flower[i];
				  red_chain.vIndex=p.face_index(f,w);
		      }

		  stop_ptr=red_chain;
		  
/* --------- main loop for building the list of faces -------------- */

		  looplimit=(100)*p.nodeCount; // emergency limit
		  while (hit_flag && loop_count<looplimit) {
			  tripCount=0;
		      hit_flag=false;
		      stop_ptr=red_chain;

		      // ============================
		      // one pass to get started 
		      if (loop_vert(false)) {
		    	  stop_ptr=red_chain;
		    	  hit_flag=true;
			  }

		      // ============================
		      // multiple passes, loop-overs only (don't create blues)
		      while (tripCount<2 && loop_count<looplimit) { 
		    	  if (loop_vert(false)) { // if true, added a face, so reset stop_ptr
		    		  stop_ptr=red_chain;
		    		  hit_flag=true;
		    	  }

				  // move redlist through faces that are done?				  
		    	  while (red_chain.done && tripCount<2) { 
		    		  red_chain=red_chain.next;
		    		  if (red_chain==stop_ptr) tripCount++;
		    	  }
		    	  loop_count++;
		      } // end of while 
		      
		      if (loop_count>=looplimit) {
				  System.out.println("'build_redchain' failed middle loop: "+
						  "stop="+stop_ptr.face);
			      throw new LayoutException();
		      }
		      tripCount=0;	   
		      stop_ptr=red_chain;

		      // ============================
		      // multiple passes, allowing creation of blues also 
		      while (tripCount<3 && loop_count<looplimit) {
		    	  try {
		    	  if (loop_vert(true)) { // true: added new face, reset stop_ptr
		    		  stop_ptr=red_chain; // CirclePack.cpb.Elink=p.poisonEdges.makeCopy();
		    		  hit_flag=true;
		    	  }
		    	  } catch (Exception ex) {
		    		  ex.printStackTrace();
		    		  tripCount=3;
		    	  }

				  // move redlist through faces that are done?				  
				  while (red_chain.done && tripCount<2) { 
					  red_chain=red_chain.next;
					  if (red_chain==stop_ptr) 
						  tripCount++;
				  }
				  loop_count++;
		      } // end of while

		      // assess situation: all faces blue or white?
		      boolean somefree=false;
		      for (int i=1;i<=p.faceCount && !somefree;i++)
		    	  if (p.faces[i].rwbFlag==0) 
		    		  somefree=true;
		      if (!somefree) hit_flag=false;
		      
/*		      // ============================
		      // a couple more passes, just to make sure
			  for (int i=1;i<=2;i++) {
			    if (loop_vert(true)) {
			    	stop_ptr=redChain;
				    if (debug) {
						  System.out.println("couple more passes loop_vert:");
						  LayoutBugs.quick_redlist(redChain);
					  }
			    	hit_flag=true;
			    }
*/			    

		  } // end of main while 

		  if (loop_count>=looplimit) {
			  System.out.println("'build_redchain' failed final loop: "+
					  "stop="+stop_ptr.face);
		      throw new LayoutException();
		  }

		  buildPacket.buildMsg="Build seems to have succeeded.";
		  buildPacket.success=true;
		  buildPacket.redList=red_chain;
		  buildPacket.faceOrdering=faceOrder; // CPBase.Flink=LayoutBugs.redChain2redLink(red_chain); 

		  // reset global pointers
		  return buildPacket;
		}
		
		/**
		 * Called ONLY by 'build_redchain' due to use of global
		 * variables. 'red_chain' points to the current face in
		 * the red chain. Find the 'vert' shared by this and
		 * the next face in red_chain, find the "fan" of contiguous 
		 * red faces in star of 'vert', then try to add to the
		 * red_chain by "looping" around 'vert'. 
		 * 
		 * When 'vert' is interior, not poison or in poison edge, 
		 * and when remaining faces of its star are still free, 
		 * then we adjust the red chain to loop outside of 'vert'; 
		 * this means adding faces complementary to the fan (and 
		 * perhaps bypassing some faces of the current red chain).
		 * Faces are also added to the face order by global 'fDo_tmp' 
		 * (facedraw order VertList) as appropriate.
		 * 
		 * If this doesn't happen and 'mode' is true, then try 
		 * to add the face in the star of 'vert' after the fan 
		 * as a "blue" face. Do NOT add if the edge between or
		 * its vertices are poison. In ALL cases, reset 'red_chain' 
		 * at least one face downstream.
		 *  
		 * Return 'true' if a change is made to the red chain
		 * (in which case, eg., 'stop_ptr' is reset to 'red_chain'
		 * by the calling routine).
		 * 
		 * @param boolean mode: true, then allow blue faces
		 * @return boolean, true if the red chain was changed
		 */
		public boolean loop_vert(boolean mode)
		throws LayoutException, RedListException {
		  int n,findex=0,bindex,eindex,i,j,num,vert,f;
		  boolean laso_flag=true;
		  RedList bzip,new_end,hold,trace;
		  boolean got_one=false;
		  
		  int cface=red_chain.face;
		  if (red_chain.done) { // finished with face, advance redface
		      red_chain=red_chain.next;
		      if (red_chain==stop_ptr) tripCount++; // completed circuit?
		      return false; // no new faces added
		  }
		  if ((n=p.face_nghb(red_chain.next.face,cface))<0) 
		      throw new RedListException();

		  /* Blue case. First, suppose we are at (currently) blue 
		   * face. May need two passes: starting with the vertex 
		   * NOT shared with next red face (if it is in more than 
		   * one face) and then going on to handle the usual vert 
		   * (one shared with next red). Note: this ensures that 
		   * this vert gets checked.
		   */
		  if (red_chain.next.face==red_chain.prev.face) {
		      vert=p.faces[cface].vert[(n=(n+2) % 3)];

		      /* Note: 'vert' is vertex that cface does NOT share
		       * with its (downstream and upstream) redchain neighbor  
		       */
		      if ((num=p.countFaces(vert)) > 1) { // any more faces?
		    	  // findex = flower index of first vertex in cface 
		    	  findex=0;
		    	  int[] faceFlower=p.getFaceFlower(vert);
		    	  while (faceFlower[findex]!=cface 
		    			  && findex<(num-1)) 
		    		  findex++;
		    	  eindex=bindex=findex; // all these are the same in this case
		 
		    	  // if vert interior, see if we can loop red chain around it
		    	  if (!p.isBdry(vert)) {
		    		  i=bindex+1;
		    		  laso_flag=true;
		    		  while ((j=(i % num))!=eindex && laso_flag) {
		    			  if (p.faces[faceFlower[j]].rwbFlag>0) 
		    				  laso_flag=false;
		    			  i++;
		    		  }

		    		  // yes, we can loop around and vert is not poison 
		    		  if (laso_flag && (!poisonFlag || !p.vert_isPoison(vert))) {
		    			  trace=red_chain.next; // hold this for later
		    			  new_end=red_chain; 
				  
		    			  // start new segment (and add to 'fDo_tmp')
		    			  i=(findex+1) % num;
		    			  int of=new_end.face;
		    			  int nf=faceFlower[i];
		    			  fDo_tmp=add_face_order(fDo_tmp,nf,of);
		    			  new_end=new RedList(new_end,nf);
		    			  new_end.done=isDone(p,new_end);
		    			  p.faces[nf].rwbFlag++;
		    			  got_one=true;

		    			  // continue until you get a new redface for cface
		    			  i=(i+1) % num;
		    			  while ( i!=((findex+1) % num)) {
		    				  of=new_end.face; 
		    				  nf=faceFlower[i];
		    				  fDo_tmp=add_face_order(fDo_tmp,nf,of);
		    				  new_end=new RedList(new_end,nf);
			    			  new_end.done=isDone(p,new_end);
		    				  p.faces[nf].rwbFlag++;
		    				  i=(i+1) % num;
		    			  }

		    			  /* Have now extended red chain to 'new_end'; have to
				           * patch it back into the old red chain and move
				           * the pointers. */
		    			  new_end.next=trace;
		    			  trace.prev=new_end;
		    			  red_chain=trace;
		    		  }
		    	  }

		    	  /* if 'vert' is not interior or we can't loop around 
		    	   * it, we try to add at least one face clockwise around
		    	   * 'vert' from 'cface' (it will be blue to start).
			       */
		    	  if (mode && (p.isBdry(vert) || !laso_flag)) {
		    		  j=(eindex-1+num) % num; 
		    		  if ( ((p.isBdry(vert) && (eindex > 0)) 
		    				  || !p.isBdry(vert))
		    				  && p.faces[faceFlower[j]].rwbFlag==0) {
		    			  f=faceFlower[j];
		    			  int k=p.face_nghb(cface,f);
		    			  if (!poisonFlag || !p.edge_isPoison(vert,p.faces[f].vert[k])) {
		    				  fDo_tmp=add_face_order(fDo_tmp,f,red_chain.face);
		    				  trace=red_chain.next; // hold this to reattach later
		    				  // remember, we're adding new blue onto current blue
		    				  new_end=new RedList(red_chain,f);
		    				  new_end.done=isDone(p,new_end);
		    				  p.faces[f].rwbFlag++;
		    				  got_one=true;
				      
		    				  // need new redface coming back to cface
		    				  new_end=new RedList(new_end,cface);
		    				  new_end.done=isDone(p,new_end);
		    				  p.faces[cface].rwbFlag++;
				      
		    				  // patch new_end back into redchain and reset 
		    				  red_chain=new_end.next=trace;
		    				  trace.prev=new_end;
		    			  } 
		    		  }
		    	  }
		      }
		  } // end of handling isolated vertex in blue face case
		  
		  // Move on to handle the usual 'vert' (shared with next red face)
		  
		  // but note that in blue case we may have moved 'red_chain' forward
		  if (got_one) red_chain=red_chain.prev; 
		  
		  bzip=red_chain;
		  cface=red_chain.face;
		  if ((n=p.face_nghb(red_chain.next.face,cface))<0) 
		      throw new RedListException();
		  vert=p.faces[cface].vert[n];
		  num=p.countFaces(vert);
		  findex=0;
		  int[] faceFlower=p.getFaceFlower(vert);
		  while (faceFlower[findex]!=cface && findex<(num-1)) 
			  findex++;

		  /* Now we find the maximal "fan" (subchain) of red_chain 
		   * containing 'redface' and lying in the flower of 'vert'.
		   * 'red_chain' is shifted to furthest downstream face;
		   * (note, 'red_chain' is shifted at least one position downstream.)
		   */ 
		  i=findex;
		  while (red_chain.next.face==p.kData[vert].
				  faceFlower[(i+num-1) % num] && red_chain.next.face!=cface ) {
		      red_chain=red_chain.next;
		      if (red_chain==stop_ptr) tripCount++; // completed circuit?
		      i--;
		  }
		  eindex= (num+i) % num;

		  /* If 'vert is the single outside vert of whole red chain,
		   *  then 'p' must be combinatorial sphere, which should
		   *  have been caught earlier.
		   */
		  if (red_chain.next.face==cface) {
		      hold=red_chain;
		      boolean keepon=true;
		      while (hold.next!=red_chain && keepon) {
		    	  if (p.face_index(hold.face,vert)<0) keepon=false;
		    	  hold=hold.next;
		      }
		      if (keepon) throw new RedListException();
		  }

		  /* find furthest upstream face, maybe even upstream of 
		   * orginal red_chain (i.e., original bzip).
		   */
		  i=findex;
		  while(bzip.prev.face==faceFlower[(i+1) % num]
			  && bzip.prev.face!=cface ) {
		      bzip=bzip.prev;
		      i++;
		  }
		  bindex=(i % num);
		  
		  /* If 'red_chain' is not blue, 'vert' is boundary, and its 
		   * whole fan is already red, don't need to revisit this face, 
		   * so set done=true.
		   */
		  if (p.isBdry(vert) && eindex==0 && bindex==(num-1) 
				  && red_chain.prev.face!=red_chain.next.face) {
			  red_chain.done=true;
			  red_chain=red_chain.next;
			  if (red_chain==stop_ptr) tripCount++;
			  return got_one;
		  }

		  /* Note: "fan" of contiguous red faces containing cface in 
		   * star of vert goes counterclockwise from eindex to bindex. 
		   * First check if we can loop over vert; otherwise, if face 
		   * preceeding fan (clockwise) is free, we add it as blue face. 
		   */
		  laso_flag=true;
		  if (!p.isBdry(vert)) { // vert is interior
	    	  new_end=bzip;
		      i=bindex+1;
		      while ( (j=(i % num))!=eindex && laso_flag) {
		    	  if (p.faces[faceFlower[j]].rwbFlag!=0) 
		    		  laso_flag=false;
		    	  i++;
		      }

		      // yes, yes, rest of faces white and vert is not poison 
		      if (laso_flag && (!poisonFlag || !p.vert_isPoison(vert))) {
	    		  got_one=true;
		    	  if ( (i=(bindex+1) % num)!=eindex ) { // new redfaces
		    		  int of=new_end.face;
		    		  int nf=faceFlower[i];
		    		  fDo_tmp=add_face_order(fDo_tmp,nf,of);
		    		  new_end=new RedList(new_end,nf);
		    		  new_end.done=isDone(p,new_end);
		    		  p.faces[nf].rwbFlag++;
		    		  i++;
		    		  while ( (j=i % num)!=eindex) {
		    			  of=new_end.face; 
		    			  nf=faceFlower[j];
		    			  fDo_tmp=add_face_order(fDo_tmp,nf,of);
		    			  new_end=new RedList(new_end,nf);
			    		  new_end.done=isDone(p,new_end);
		    			  p.faces[nf].rwbFlag++;
		    			  i++;
		    		  }
		    	  }

		    	  // attach 'new_end' and 'redlist', garbaging the bypassed red faces
		    	  new_end.next=red_chain;
		    	  red_chain.prev=new_end;
			 
		    	  // reset 'rwbFlag' for bypassed red faces
		    	  i=(eindex+1)%num;
		    	  while (i!=bindex) {
		    		  p.faces[faceFlower[i]].rwbFlag--;
		    		  if (p.faces[faceFlower[i]].rwbFlag==0)
		    			  p.faces[faceFlower[i]].rwbFlag=-1;
		    		  i=(i+1)%num;
		    	  }
		      }
		  }
		  
		  /* otherwise, if possible, add blue face preceeding fan (i.e., 
		   * clockwise from first face of fan (which is eindex). */ 
		  if (mode && (p.isBdry(vert) || !laso_flag)) {
		      cface=faceFlower[eindex];
		      j=(eindex-1+num) % num;
		      f=faceFlower[j]; // ??? was (j+1) % num];
		      // is there a blue face to add?
		      if (((p.isBdry(vert) && (eindex > 0)) 
			    || !p.isBdry(vert)) && p.faces[f].rwbFlag==0) {
		    	  int k=p.face_nghb(cface,f);
		    	  if (!poisonFlag || !p.edge_isPoison(vert,p.faces[f].vert[k])) {
		    		  fDo_tmp=add_face_order(fDo_tmp,f,red_chain.face);
		    		  new_end=new RedList(red_chain,f);
		    		  new_end.done=isDone(p,new_end);
		    		  p.faces[f].rwbFlag++;
		    		  got_one=true;

				      // need to return to cface
		    		  new_end=new RedList(new_end,cface);
		    		  new_end.done=isDone(p,new_end);
		    		  p.faces[cface].rwbFlag++;
		    		  
		    		  // reset redlist to this latest copy of cface
		    		  red_chain=new_end;
		    	  } 
		      }
		  }
		  return got_one;
		}
		
		/**
		 * Is this face in redchain 'done'? (doesn't need to be 
		 * revisited).
		 * @param poison, boolean, if true, search for poison
		 */
		public boolean isDone(PackData p,RedList rl) {
			if (rl.done) return true;
			
			// shared downstream/upstream
			int vd=red_share_vert(p,rl.prev);
			int vu=red_share_vert(p,rl);
			
			if (rl.next.face==rl.prev.face) { // blue?
				int v=p.faces[rl.face].vert[rl.vIndex]; // non-shared vert
				if (p.countFaces(v)==1) return true;
			}
			
			if (vd==vu) return true; // rl.face is inside a fan of faces
			if (poisonFlag && ((p.vert_isPoison(vd) && p.vert_isPoison(vu)) ||
					p.edge_isPoison(vd,vu))) 
				return true;
			int j=p.nghb(vd,vu);
			if (p.isBdry(vd) && p.countFaces(vd)==j)
				return true;
			return false;
		}

		/** 
		 * Add face at end of draw order; to set index, we assume given
		 * neighbor 'preface' is already drawn. This is only called
		 * from 'loop_vert'.
		*/
		public VertList add_face_order(VertList fDo,int face,int preface) 
		  throws LayoutException, RedListException {
		  int n;
		  VertList new_fDo=new VertList(fDo,face);
		  if ((n=p.face_nghb(preface,face))<0) {
			  System.err.println("exception in 'add_face_order'");
		      throw new RedListException();
		  }
		  p.faces[face].indexFlag=n;
		  return new_fDo;
		}

}		

/** local utility class for inner loop in 'simplify_redchain' */
class skip_to_end extends Exception {

	private static final long 
	serialVersionUID = 1L;
}
