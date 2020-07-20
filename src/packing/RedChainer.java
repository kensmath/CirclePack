package packing;

import java.awt.Color;
import java.util.Iterator;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import deBugging.LayoutBugs;
import exceptions.CombException;
import exceptions.LayoutException;
import exceptions.RedListException;
import komplex.SideDescription;
import komplex.EdgeSimple;
import komplex.RedEdge;
import komplex.RedList;
import listManip.EdgeLink;
import listManip.PairLink;
import listManip.VertList;
import panels.CPScreen;
import util.BuildPacket;
import util.ColorUtil;
import util.UtilPacket;

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
	 * Given a 'seed' vertex in simply connected complex and 
	 * 'gen' > 1, return a new redchain for the smallest simply 
	 * connected sub-complex containing 'seed' and having vertices up 
	 * to/including generation 'gen'. 
	 * NOTE: 'count' comes back in 'p.util_A' from call to 
	 * 'label_seed_generation' and is returned via 'util_1'; the 
	 * calling routine must use it immediately on return.

	 * May need to avoid "green" verts indicated by vec_seed[v]<0
	 * (eg., those already  included in some other complex). Main 
	 * difficulty is filling in holes formed by higher generation 
	 * vertices -- inclusions; this is handled by 'simplify_redchain'.
	*/
	public RedList build_gen_redlist(int seed, int []vec_seed,int gen) {
	  int k,num;
	  int []gen_list;
	  int []gen_numbers;
	  int keep_vert;
	  int out_vert=1;
	  int min_gen,cgen,cvert,n,v;
	  RedList redlist=null;
	  RedList rtrace;
	  RedList keep_ptr=null;
	  EdgeLink lifeline=null;

	  gen_list=p.label_seed_generations(seed,vec_seed,gen-1,false);
	  if (gen_list==null || p.util_A>=p.nodeCount) { // nothing/everything?
	      util_1=p.util_A;
	      return null; 
	  }

//	 set generation gen and above verts and green verts to poison 
	  for (int i=1;i<=p.nodeCount;i++) {
	      if (gen_list[i]<=0 || gen_list[i]>=gen) 
		p.kData[i].utilFlag=-1;
	      else p.kData[i].utilFlag=0;
	    }
//	 remove any isolated poison verts 
	  for (int i=1;i<=p.nodeCount;i++) {
	      if (p.kData[i].utilFlag==-1) {
		  k=0;
		  for (int j=0;j<=p.kData[i].num;j++) 
		    k+= p.kData[p.kData[i].flower[j]].utilFlag;
		  if (k==0) {
		      p.kData[i].utilFlag=0;
		  }
	      }
	  }

	  // TODO: not sure if we're using poison here
	  BuildPacket bP=build_redchain(seed,false);
	  if (!bP.success) {
		  CirclePack.cpb.myErrorMsg(bP.buildMsg);
	      util_1=0;
	      return null;
	  }
	  redlist=bP.redList;
	  
	  /* We have to make the region inside the redchain simply connected. 
	   * We need a persistent redchain entry and vertex on redchain outer edge
	   * so we don't discard the wrong portions of redchain.
	   *
	   * If p is top sphere, we use out_vert (furthest from seed) and lifeline
	   * to it. If top disc, first attempt is to find a bdry vert on redchain
	   * outer edge, otherwise throw lifeline from the vert closest to bdry. 
	   */

	  keep_vert=0;
	  for (int i=1;i<=p.nodeCount;i++) p.kData[i].utilFlag=0;

	  if (p.bdryCompCount>0) { // top disc 
	      for (int i=1;i<=p.nodeCount;i++) // flag bdry verts 
		if (p.kData[i].bdryFlag!=0) p.kData[i].utilFlag=1;
	      v=red_share_vert(p,redlist);
	      if (p.kData[v].bdryFlag!=0) {
		  keep_vert=v;
		  keep_ptr=redlist;
	      }
	      else {
		  rtrace=redlist.next;
		  while (rtrace!=redlist) {
		      v=red_share_vert(p,rtrace);
		      if (p.kData[v].bdryFlag!=0) {
			  keep_vert=v;
			  keep_ptr=rtrace;
			}
		      rtrace=rtrace.next;
		    }
		}
	    }
	  else { // top sphere 
	      // find 'out_vert', furthest vert from seed. 
	      UtilPacket uP=new UtilPacket();
	      gen_numbers=p.label_generations(0,uP);
	      out_vert=uP.rtnFlag;
	      p.kData[out_vert].utilFlag=1; // flag out_vert 
	      if ((v=red_share_vert(p,redlist))==out_vert) {
		  keep_vert=v;
		  keep_ptr=redlist;
	      }
	      else {
		  rtrace=redlist.next;
		  while (rtrace!=redlist) {
		      if ((v=red_share_vert(p,rtrace)) == out_vert) {
			  keep_vert=v;
			  keep_ptr=rtrace;
		      }
		      rtrace=rtrace.next;
		  }
	      }
	  }

	  if (keep_vert==0) { // must establish a lifeline 
	      /* determine distance of points from the flagged vert(s) (those
		 with utilFlag set */
	      UtilPacket uP=new UtilPacket();
	      gen_numbers=p.label_generations(-1,uP);
	      // find vert on redchain outer edge closest to the flagged vert(s)
	      keep_vert=red_share_vert(p,redlist);
	      keep_ptr=redlist;
	      min_gen=gen_numbers[keep_vert];
	      
	      rtrace=redlist.next;
	      while (rtrace!=redlist) {
		  n=red_share_vert(p,rtrace);
		  if (gen_numbers[n]<min_gen) {
		      keep_vert=n;
		      keep_ptr=rtrace;
		      min_gen=gen_numbers[n];
		    }
		  rtrace=rtrace.next;
	      }
	      // build lifeline 
	     
	      cvert=keep_vert;
	      cgen=gen_numbers[cvert];
	      lifeline = new EdgeLink(p);
	      do {
		  num=p.kData[cvert].num;
		  for (int j=0;j<=num;j++) {
		      k=p.kData[cvert].flower[j];
		      if (gen_numbers[k]<cgen) {
			  lifeline.add(new EdgeSimple(cvert,k));
			  cgen=gen_numbers[k];
			  j=num+1;
		      }
		  }
		} while (p.kData[cvert].utilFlag==0);
	  }

	  /* set redlist to start at persistent entry, then process red 
	     chain to remove inclusions */

	  redlist=keep_ptr;
	  if (simplify_redchain(redlist,lifeline,keep_vert,keep_ptr)==0) {
		  CirclePack.cpb.myErrorMsg("Failed to simplify properly.");
	      util_1=0;
	      return null;
	     }
	  return redlist;
	}

	/** 
	 * Given a red chain, want to cut 'inclusions' and reentrant
	 * peninsulas to get a new redchain. E.g., if p is simply connected,
	 * can cookie this new redchain to get smallest simply connected 
	 * sub-complex containing the original red chain. (I don't know
	 * what might happen when p is not simply connected.)

	 * Idea is to walk around outside of redchain and find any shortcuts. 
	 * To identify the "outside", which parts of redchain are kept and
	 * which removed as inclusions, we use 'lifeline', 'keep_vert', and
	 * redchain entry 'keep_ptr'. The later two are 'persistent', should 
	 * remain in final redchain. lifeline will be null if 'keep_vert' is
	 * in bdry of p. When p is a sphere, lifeline should run to some
	 * designated outside vert; otherwise, should run to bdry; verts
	 * along lifeline should end up outside of the final redchain.

	 * Catalog of p must be stored and destroyed by calling program;
	 * this routine uses 'utilFlag'. Return 0 on error. 
	*/
	public int simplify_redchain(RedList redlist,EdgeLink lifeline,
				     int keep_vert,RedList keep_ptr) {
	  int num,dum,n_fan,new_vert,up_new_vert;
	  int focus_vert,up_vert,down_vert,v1,v2,u;
	  int down_indx,up_indx,indx,indx1,indx2;
	  int hit_flag,begin_flag,end_flag,bflag;
	  boolean last_flag=false;
	  boolean trip_flag=true;
	  int []f_util;
	  RedList rtrace,ftrace,next_red,ftc;
	  RedList fan,fan2,focus_red;
	  RedList kill_start,kill_stop,up_red;

	  if (!p.status || redlist==null || keep_ptr==null || keep_vert==0
	      || p.face_index(keep_ptr.face,keep_vert)<0
	      || (keep_vert!=red_share_vert(p,keep_ptr) 
	    		  && keep_vert!=red_share_vert(p,keep_ptr.prev)
	    		  && keep_ptr.next.face!=keep_ptr.prev.face)
	    		  )
	    return 0;

	  /* persistent 'keep_vert' must be on outside of 'keep_ptr' face
	   * NOTE: lifeline must be arranged by the calling routine; eg,
	   * it can't cross redchain, should have at most one vert (namely 
	   * at one end), etc. */

	  // mark lifeline 
	  for (int n=1;n<=p.nodeCount;n++) p.kData[n].utilFlag=0;
	  if (lifeline!=null && lifeline.size()!=0) {
		  Iterator<EdgeSimple> lline=lifeline.iterator();
		  EdgeSimple edge=null;
		  while (lline.hasNext()) {
			  edge=(EdgeSimple)lline.next();
			  p.kData[edge.v].utilFlag=p.kData[edge.w].utilFlag=1;
		  }
	  }

	  // 'f_util' vector keeps track of faces currently in redchain 
	  f_util=new int[p.faceCount+1];
	  f_util[redlist.face]=1;
	  ftrace=redlist.next;
	  while (ftrace!=redlist) {
	      f_util[ftrace.face]=1;
	      ftrace=ftrace.next;
	  }

	/* Modify red chain to fill 'inclusions'. Method: walk around 
	 * outer edge verts and look for 'shortcuts': when vert is
	 * repeated (outside the current fan of red faces) or when a
	 * nghb of vert is encounterd. Discard the intervening red chain.
	 * (Stop search when you reach the 'keep_ptr' to ensure that 'keep_ptr'
	 * is kept). Repeat as needed. Resulting red chain should define 
	 * simply connected sub-complex (assuming p is simply connected).
	*/
	   
//	 ------------------------ main loop ------------------

	  while (trip_flag) { // main loop: go until pruning is done 
	      trip_flag=false;
	      
	      /* 'focus_red' and 'focus_vert' are fixed while we search for 
		 shortcuts to them; start from 'keep_vert'. */
	      
	      focus_red=keep_ptr;
	      focus_vert=keep_vert;
	      /* collect some local info around focus; set 'focus_red' as
	       * far downstream as possible with 'focus_vert' as a vertex. */
	      up_red=upstream_red(focus_vert,focus_red);
	      up_indx=util_1;
	      focus_red=downstream_red(focus_vert,focus_red);
	      down_indx=util_2;

	  /* ------------------------ outer loop ------------------*/
	      do {
	    	  /* look for shortcuts; start with 'new_vert' strictly downstream 
	    	   * of verts in 'focus_red.face' */
		  rtrace=focus_red;
		  new_vert=red_share_vert(p,focus_red);
		  /* have to know when to stop search; namely, when 'keep_ptr' is 
		   * encountered. Complications: may run into 'keep_ptr' right at the
		   * beginning or right at the end. Set up 'begin_flag' and 'end_flag'; 
		   * former is set only on first pass, latter influences last pass. 
		   * Their states depend on relationship of 'focus_red' and 'keep_ptr'. */

		  if (focus_red==keep_ptr) begin_flag=1;
		  else begin_flag=0;
		  if (keep_ptr==upstream_red(keep_vert,keep_ptr))
			  end_flag=1;
		  else end_flag=0;
		  last_flag=false;
		    /* this means that 'rtrace' could end up matching 'keep_ptr' 
		     * when we're done searching for shortcuts. In this 'while' 
		     * call, 'last_flag' is true only if 'end_flag' is set and 
		     * rtrace==keep_ptr. */
		       
	/* ------------------------ inner loop ------------------*/

		  while (!last_flag 
				  && (new_vert=jump_down_red(rtrace,new_vert,rtrace,keep_ptr))!=0
			 && (util_1==0 || begin_flag!=0 || (last_flag=(end_flag!=0 && (rtrace==keep_ptr)
					    && red_share_vert(p,rtrace.prev)!=keep_vert)))) {
			  hit_flag=util_1; // got this during 'jump_down_red' 
		    /* summary: keep going if: haven't set last_flag; have found
		       new_vert to check for shortcut; didn't encounter keep_ptr,
		       or if we did, it's okay because: it's the first pass and
		       we knew we would encounter it, or we hit it just at the
		       end and it's a valid shortcut (don't screw up keep_vert).*/
		      begin_flag=0;
		      
		      /* Various criteria disqualify shortcuts; some of
			 these are also error checks. Use 'skip_to_end' exception
			 to continue with another pass of the 'while' loop. */
		      try {
		      indx=p.nghb(focus_vert,new_vert);
		      if (new_vert!=focus_vert && indx<0)
		    	  throw new skip_to_end();
		      if (rtrace==focus_red || rtrace==up_red
			  || rtrace.face==focus_red.face
			  ||(dum=p.face_index(rtrace.face,new_vert))<0)
		    	  throw new skip_to_end();
		      if ((up_new_vert=p.faces[rtrace.face].vert[(dum+2)%3])==focus_vert)
		    	  throw new CombException(); // shouldn't happen 
		      /*	      if (new_vert!=focus_vert
				      && nghb(p,focus_vert,up_new_vert)==down_indx)
			goto CONT; */
		      down_vert=p.kData[focus_vert].flower[down_indx];
		      if (EdgeLink.ck_in_elist(lifeline,new_vert,up_new_vert)
		    		  || EdgeLink.ck_in_elist(lifeline,focus_vert,down_vert))
		    	  throw new skip_to_end(); // first or last face crosses lifeline 
		      num=p.kData[focus_vert].num;
		      if (p.kData[focus_vert].bdryFlag!=0) { // bdry case 
			  if (new_vert==focus_vert && ((dum=p.nghb(focus_vert,up_new_vert))<0 
					  || dum>down_indx))
		    	  throw new skip_to_end();
			  else if (p.nghb(focus_vert,new_vert)>=down_indx)
		    	  throw new skip_to_end();
		      }
		      else if (new_vert!=focus_vert) { // interior petal case 
		    	  /* new connections to petals between down_vert
		    	   * and up_vert (including down_vert, which would
		    	   * be taken care of later) aren't allowed */
		    	  if (up_indx==down_indx) break;
		    	  dum=up_indx;
		    	  if (up_indx<down_indx) dum += num;
		    	  if (indx<down_indx) indx += num;
		    	  if (indx<=dum) 
			    	  throw new skip_to_end();
		      }

		      // Now, do shortcut 

		      kill_start=kill_stop=null;

		      if (new_vert==focus_vert) {
		    	  // may be direct shortcut; have further checking to do 
		    	  indx=p.nghb(focus_vert,up_new_vert);
	          
		    	  int []ans=new int[2];
		    	  fan=build_fan(focus_vert,indx,down_indx,ans);
		    	  bflag=ans[0];
		    	  n_fan=ans[1];
		    	  if (bflag!=0 && fan!=null) fan=null;
		    	  if (bflag==0) { /* Note: avoid problems, like bdry 
					 verts ending up on inside of redchain. */
		    		  kill_start=focus_red.next;
		    		  kill_stop=rtrace.prev;
		    	  }
		      }
		      else { 
			/* have to bridge across gap using two fans; first is 
			 * clockwise from 'down_red' until we get a face containing 
			 * 'new_vert', then clockwise around 'new_vert' until we 
			 * link up to 'rtrace'. */
		    	  up_vert=p.kData[focus_vert].flower[up_indx]; 
		    	  if(upstream_red(up_vert,up_red)==rtrace) 
			    	  throw new skip_to_end();
		    	  num=p.kData[focus_vert].num;
		    	  int []ans=new int[2];
		    	  fan=build_fan(focus_vert,indx,down_indx,ans);
		    	  bflag=ans[0];
		    	  n_fan=ans[1];
		    	  if (bflag!=0 && fan!=null) fan=null;
		    	  if (bflag==0 && fan!=null) { // should get non-empty fan 
		    		  u=p.kData[focus_vert].flower[(indx+1)%num];
		    		  indx2=p.nghb(new_vert,u);
		    		  indx1=p.nghb(new_vert,up_new_vert);
		    		  ans=new int[2];
		    		  fan2=build_fan(new_vert,indx1,indx2,ans);
		    		  bflag=ans[0];
		    		  int n=ans[1];
		    		  n_fan += n;
		    		  if (bflag==0 && fan2!=null) { // concatenate fans 
		    			  ftrace=fan;
		    			  while(ftrace.next!=null && ftrace.next!=fan) 
		    				  ftrace=ftrace.next;
		    			  ftrace.next=fan2;
		    			  fan2.prev=ftrace;
		    			  ftrace=fan2;
		    			  while(ftrace.next!=null && ftrace.next!=fan2)
		    				  ftrace=ftrace.next;
		    			  ftrace.next=fan;
		    			  fan.prev=ftrace;
		    		  }
		    		  kill_start=focus_red.next;
		    		  kill_stop=rtrace.prev;
		    	  }
		      }

		      if (kill_start!=null) { // we have a shortcut ready 
		    	  if (n_fan!=0) { // insert fan 
		    		  /* check fan to see if it picks up face 
		    		   * already in a different section of the redchain. */
		    		  ftc=fan;
		    		  for (int i=1;i<=n_fan;i++) {
		    			  if (f_util[ftc.face]!=0) { // face already in redchain
		    				  // if it's in the part of redchain being cut out, okay
		    				  dum=0;
		    				  ftrace=kill_start;
		    				  boolean bdum=(ftrace.face==ftc.face);
		    				  while (ftrace.next!=rtrace && !bdum) { // (dum=(ftrace.face==ftc.face))==0)
		    					  ftrace=ftrace.next;
		    					  bdum=(ftrace.face==ftc.face);
		    				  }
		    				  if (!bdum) { // didn't find it, so scrap this shortcut
		    					  fan=null;
		    			    	  throw new skip_to_end();
		    				  }
		    			  }
		    			  ftc=ftc.next;
		    		  }
		    		  // check fan to see if it crosses lifeline 
		    		  if (lifeline!=null) {
		    			  ftrace=fan;
		    			  while (ftrace.next!=fan) {
		    				  indx=p.face_nghb(ftrace.next.face,ftrace.face);
		    				  v1=p.faces[ftrace.face].vert[indx];
		    				  v2=p.faces[ftrace.face].vert[(indx+1)%3];
		    				  if (EdgeLink.ck_in_elist(lifeline,v1,v2)) {
		    					  fan=null;
		    			    	  throw new skip_to_end();
		    				  }
		    				  ftrace=ftrace.next;
		    			  }
		    		  }
		    		  // okay, so mark these faces in f_util 
		    		  f_util[fan.face]=1;
		    		  ftrace=fan.next;
		    		  while (ftrace!=fan) {
		    			  f_util[ftrace.face]=1;
		    			  ftrace=ftrace.next;
		    		  }

		    		  // now, hook in shortcut 
		    		  focus_red.next=fan;
		    		  fan.prev=focus_red;
		    		  ftrace=fan;
		    		  while(ftrace.next!=null && ftrace.next!=fan) 
		    			  ftrace=ftrace.next;
		    		  ftrace.next=rtrace;
		    		  rtrace.prev=ftrace;
		    	  }
		    	  else { // nothing to add, just short out 
		    		  focus_red.next=rtrace;
		    		  rtrace.prev=focus_red;
		    	  }

		    	  // reset f_util for faces no longer in redchain 
		    	  kill_stop.next=null;
		    	  ftrace=kill_start;
		    	  while (ftrace!=null) {
		    		  f_util[ftrace.face]=0;
		    		  ftrace=ftrace.next;
		    	  }

		    	  // close up and free kill chain 
		    	  kill_start.prev=kill_stop;
		    	  kill_stop.next=kill_start;
		    	  kill_start=null;

		    	  // indicate that we did a shortcut 
		    	  trip_flag=true; 

		    	  /* update info around focus_vert and check
		    	   * settings of flags concerning search. */
		    	  next_red=downstream_red(focus_vert,focus_red);
		    	  down_indx=util_2;
		    	  if (next_red==focus_red) // shouldn't happen; stop the inner loop.
		    		  last_flag=true; 
		    	  focus_red=next_red;
		    	  if (focus_red==keep_ptr) /* reset begin_flag because
						      we can continue looping with the same focus */
		    		  begin_flag=1;
		    	  // continue search strictly downstream 
		    	  rtrace=focus_red;
		    	  new_vert=red_share_vert(p,focus_red);
		      }
		      } catch (skip_to_end ste) {} // just continue the loop
		  } /* end of 'jump' while; focus doesn't change yet,
			 * we might find other shortcuts for it. */
		  
		  // jump to change the focus, stopping if keep_ptr crossed. 
		  focus_vert=jump_down_red(focus_red,focus_vert,focus_red,keep_ptr);
		  hit_flag=util_1;

		  if (focus_vert!=0 && hit_flag==0) {
			  // update local info around focus, checking to see if we pass keep_ptr.
			  up_red=upstream_red(focus_vert,focus_red);
		      up_indx=util_1;
		      next_red=downstream_red(focus_vert,focus_red);
		      down_indx=util_2;
		      ftrace=focus_red;
		      while (ftrace!=next_red) {
		    	  if (ftrace==keep_ptr) hit_flag=1;
		    	  ftrace=ftrace.next;
		      }
		      focus_red=next_red;
		  }

	      } while (focus_vert!=0 && hit_flag==0);
	      /* keep going until you get back to the beginning or while
		   * shortcuts are being found */
	  } // end of outer while; didn't make any changes on last pass.

	  return 1;
	} 	

    /** 
     * Find furthest contiguous upstream face in redlist containing vert.
     * Return null on error or if redchain encircles single vert. 
     * */
	public RedList upstream_red(int vert, RedList redlist) {
	  int cface,pface,indx,v;
	  RedList trace;

	  if (redlist==null || p.face_index((cface=redlist.face),vert)<0) 
	    return null;
	  if ((redlist.next.face==redlist.prev.face)) { // blue? 
	      v=red_share_vert(p,redlist.prev);
	      indx=p.face_index(cface,v);
	      if (vert==p.faces[cface].vert[(indx+1)%3]) {
		  util_1=p.nghb(vert,v);
		  return redlist;
	      }
	      else if (vert==p.faces[cface].vert[(indx+2)%3]) {
		  util_1=p.nghb(vert,p.faces[cface].vert[(indx+1)%3]);
		  return redlist;
		}
	    }
	  else if (redlist.face==redlist.prev.prev.face
		   && vert==red_share_vert(p,redlist.prev)) { // prev is blue 
	      indx=p.face_index(redlist.prev.face,vert);
	      util_1=p.nghb(vert,p.faces[redlist.prev.face].vert[(indx+2)%3]);
	      return redlist.prev;
	    }
	  trace=redlist;
	  pface=trace.prev.face;
	  while ((vert==red_share_vert(p,trace.prev)) && pface!=cface) {
	      trace=trace.prev;
	      pface=trace.prev.face;
	  }
	  if (pface==cface) /* red chain encircles vert */
	    return null; 
	  indx=p.face_index(trace.face,vert);
	  util_1=p.nghb(vert,p.faces[trace.face].vert[(indx+2)%3]);
	  return trace;
	}

    /**
     * Find farthest contiguous downstream face in redlist containing vert.
     * @param vert
     * @param redlist
     * @return RedList; null on error or if redchain encircles single vert. 
     */
	public RedList downstream_red(int vert,RedList redlist) {
	  int cface,nface,indx,v;
	  RedList trace;

	  if (redlist==null || p.face_index((cface=redlist.face),vert)<0)
	    return null;
	  if ((redlist.next.face==redlist.prev.face)) { // blue? 
	      v=red_share_vert(p,redlist);
	      indx=p.face_index(cface,v);
	      if (vert==p.faces[cface].vert[(indx+1)%3]) {
		  util_2=p.nghb(vert,p.faces[cface].vert[(indx+2)%3]);
		  return redlist;
	      }
	      else if (vert==p.faces[cface].vert[(indx+2)%3]) {
		  util_2=p.nghb(vert,v);
		  return redlist;
	      }
	    }
	  else if (redlist.face==redlist.next.next.face
		   && vert==red_share_vert(p,redlist)) { // next is blue 
	      indx=p.face_index(redlist.next.face,vert);
	      util_2=p.nghb(vert,p.faces[redlist.next.face].vert[(indx+1)%3]);
	      return redlist.next;
	  }
	  trace=redlist;
	  nface=trace.next.face;
	  while ((vert==red_share_vert(p,trace)) && nface!=cface) {
	      trace=trace.next;
	      nface=trace.next.face;
	  }
	  if (nface==cface) /* red chain encircles vert */
	    return null;
	  indx=p.face_index(trace.face,vert);
	  util_2=p.nghb(vert,p.faces[trace.face].vert[(indx+1)%3]);
	  return trace;
	} 

    /** 
     * Coming in, start should be face having edge in outside of red
	chain and start_vert should be downstream end of that edge.
	Have to be sure to check blue face situation, where two
	vertices of face can satisfy this.

	Move down redlist until new outside vert is found. Set new_ptr
	to new redlist entry, return vert. Set 'util_1' if keep_ptr is 
	encountered; return 0 on error. */
	public int jump_down_red(RedList start,int start_vert,
			  RedList new_ptr,RedList keep_ptr) {
	  int n,f,indx;
	  RedList ptr;

	  util_1=0;
	  if (start.next.face==start.prev.face) { // at blue face? 
	      f=start.face;
	      indx=p.face_index(f,start_vert);
	      if (start_vert!=red_share_vert(p,start)) { // at isolated vert 
		  n=p.faces[f].vert[(indx+1)%3];
		  new_ptr=start;
		  if (start==keep_ptr) // encountered keep_ptr 
		    util_1=1;
		  return n;
		}
	    }
	  ptr=start;
	  while ((n=red_share_vert(p,ptr))==start_vert) {
	      if (ptr==keep_ptr) util_1=1;
	      ptr=ptr.next;
	    }
	  new_ptr=ptr;
	  if (ptr.next.face==ptr.prev.face) { // new one is blue? return its isolated vert.
	      f=ptr.face;
	      n=p.faces[f].vert[(p.face_index(f,n)+2)%3];
	  }
	  return n;
	}

    /** 
     * Build fan about vert between indices; petal verts must be 
	 * interior; return []ans.
	 * @param vert = target vert for fan
	 * @param interior petal vertices between indx1 and indx2
	 * @return ans[0]=count, ans[1]=flag (set if a bdry petal is encountered)
	 */
	public RedList build_fan(int vert,int indx1,int indx2,int []ans) {
	  int num;
	  
	  ans[0]=ans[1]=0;
	  num=p.kData[vert].num;
	  if (p.kData[vert].bdryFlag!=0 && indx1>indx2) { // bdry, wrong order 
	      ans[0]=1;
	      return null;
	    }
	  if (indx1==indx2) return null; // okay, but count is 0
	  if (indx2<indx1) indx2 += num;
	  int j=indx1+1;
	  while (j<=indx2) {
	      if (p.kData[p.kData[vert].flower[j%num]].bdryFlag!=0)
	    	  ans[0]=1;
	      ans[1]++;
	      j++;
	    }
	  
	  // create fan as closed linked list 
	  RedList fan=new RedList(p);
	  fan.face=p.kData[vert].faceFlower[(indx2+num-1)%num];
	  fan.center=new Complex(0.0);
	  RedList rtrace=fan;
	  for (int jj=indx2-1;jj>indx1;jj--) {
	      rtrace.next=new RedList(p);
	      rtrace.next.prev=rtrace;
	      rtrace=rtrace.next;
	      rtrace.face=p.kData[vert].faceFlower[(jj+num-1)%num];
		  rtrace.center=new Complex(0.0);
	    }
	  fan.prev=rtrace;
	  rtrace.next=fan;
	  return fan;
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
	   * Given a packing, return outer (righthand side) closed edgelist
	   * around the redchain; return null on error. mode will be used
	   * in future for options.
	   * 
	   * Intention: Want to make redchains for multiply connected 
	   * surfaces more stable, so, e.g., if one does an edge-flip, 
	   * the whole ordering doesn't (necessarily) get redone.
	   * 
	   * Idea: record the "red edge" list (Edgelist) of outside edges 
	   * for a given red chain; after whatever modification is made, 
	   * an attempt is made to build the new redchain using the red 
	   * edges. May want options to force a check, eg, that all 
	   * faces are hit.
	   * @param p PackData
	   * @param mode int (not yet used)
	   * @return EdgeLink, null if there's a problem.
	  */
	  public EdgeLink red_to_outlist(PackData p,int mode) {
	    int w,lv=1,count=0;
	    RedList spot,Spot;

	    // is there a 'redChain'?
	    if (p.redChain==null) 
	    	return null;

	    // keep pointer
	    Spot=spot=p.redChain;

	    // Getting started:
	    // first face blue? put two edges in.
	    EdgeLink elist=new EdgeLink(p);
	    if (spot.prev.face==spot.next.face) { // blue
	        w=p.faces[spot.face].vert[(spot.vIndex+2)%3];
	        elist.add(new EdgeSimple(p.faces[spot.face].vert[(spot.vIndex+1)%3],w));
	        elist.add(new EdgeSimple(w,p.faces[spot.face].vert[spot.vIndex]));
	    }
	    else {  // go until a redface has a red edge
	      int rv=red_share_vert(p,spot.prev);
	      lv=red_share_vert(p,spot);
	      while (spot!=null && lv==rv) {
	      	spot=spot.next;
	      	lv=red_share_vert(p,spot);
	      }

	      elist.add(new EdgeSimple(rv,lv));
	    }

	    // reiterate
	    boolean keepon=true;
	    int nlv=1;
	    while (spot!=null && (keepon || spot!=Spot)) {
	        keepon=false;
	        // Go until you get a new edge (count is safty check)
	        while (spot!=null && count<10*p.faceCount && (nlv=red_share_vert(p,spot))>-1 
	      	&& nlv==lv) {
	        spot=spot.next;
	        if (spot==Spot) return elist;
	        count++;
	      }
	      if (spot==null || count>=10*p.faceCount || nlv<0 || nlv==lv) { // some error
	  	throw new CombException();
	      }
	      elist.add(new EdgeSimple(lv,(lv=nlv)));
	    }
	    if (spot==Spot) { // done
	      return elist;
	    }
	    return null;
	  }

	  /** 
	   * Given a closed edgelist, try to create and process a red chain
	   * that will have this as its outer edge (right-hand side).
	  */
	  public BuildPacket red_from_outlist(EdgeLink elist) {
	    int u,nu,nf,f1,f2,ff,u1,u2,common,k,num,count=0,stop=0;
	    int []ans;
	    RedList rlist=null;
	    RedList nextr=null;
	    RedList backfan=null;
	    RedList btrace=null;
	    EdgeSimple edge1=null;
	    EdgeSimple edge=null;
	    BuildPacket bP=null;

	    // is path closed?
	    boolean out=false;
	    if (elist!=null) {
	    	edge1=(EdgeSimple)elist.getFirst();
	    	edge=(EdgeSimple)elist.getLast();
	    	if (edge.w!= edge1.v)
	    			out=true;
	    }
	    if (elist==null || out) 
	        throw new CombException(); // edgelist isn't closed.

	    // compare first/last faces to see if blue
	    ans=p.left_face(edge1.v,edge1.w);
	    f1=ans[0];
	    u1=ans[1];
	    ans=p.left_face(edge.v,edge.w);
	    ff=ans[0];
	    if (ff==f1) { // yes? rejigger so last face is moved to first.
	    	// find new last edge.
	    	edge=(EdgeSimple)elist.removeLast();
	    	elist.add(0,edge);
	    }

	    
	    // now continue (still could have blue f1, but first edge is hit first
	    Iterator<EdgeSimple> elst=elist.iterator();

	    edge=(EdgeSimple)elst.next();
	    ans=p.left_face(edge.v,edge.w);
	    f1=ans[0];
	    u1=ans[1];
	    nextr=new RedList(p);
	    nextr.face=f1;
	    edge=(EdgeSimple)elst.next();
	    ans=p.left_face(edge.v,edge.w);
	    f2=ans[0];
	    u2=ans[1];
	    if (f1==f2) { // blue face
	    	edge=(EdgeSimple)elst.next();
	    	ans=p.left_face(edge.v,edge.w);
	    	f2=ans[0];
	    	u2=ans[1];
	    }
	    common=edge.v;

	    rlist=new RedList(p);
	    rlist.center=new Complex(0.0);

	    // continue through all the edges: coming in with f1, u1, f2, u2, common.
	    
	    while (count<10*p.nodeCount) {
	      // have to build fan between f2 and f1:

	      // f2 is first face in fan moving back to f1
	      u=u2;
	      backfan=btrace=new RedList(p);
	      btrace.face=f2;
	      btrace.center=new Complex(0.0);
	      num=p.kData[common].num;
	      while (u!=u1) {
	        k=(p.nghb(common,u)+1)%num;
	        nu=p.kData[common].flower[k];
	        nf=p.what_face(common,u,nu);
	        RedList tmp=btrace;
	        btrace=btrace.prev=new RedList(p);
	        btrace.next=tmp;
	        btrace.face=nf;
	        btrace.center=new Complex(0.0);
	        u=nu;
	      }
	      // hook this new list in, point to new end
	      btrace.prev=nextr;
	      nextr.next=btrace;
	      nextr=backfan;
	      count++;

	      // this is the exit spot; backfan's face is a repeat
	      if (stop!=0) { // this was last; close up and return
	  	  nextr=nextr.prev;
	  	  nextr.next=rlist;
	  	  rlist.prev=nextr;
	  	  // fix redchain data, 'vIndex's, pairings, etc.
	  	  bP=redface_comb_info(rlist,false);
	  	  return bP;
	      }    	

	      // set up for repeat
	      f1=f2;
	      u1=u2;
	      common=edge.w;
	      // get next new face
	      if (elst.hasNext())
	    	  edge=(EdgeSimple)elst.next();
	      else { // need first edge to finish up
	      	stop=1;
	      	elst=elist.iterator(); // restart iterator
	      	edge=(EdgeSimple)elst.next();
	      }
	      ans=p.left_face(edge.v,edge.w);
	      f2=ans[0];
	      u2=ans[1];
	      if (f1==f2) { // blue?
	    	  common=edge.w;
		      if (elst.hasNext())
		    	  edge=(EdgeSimple)elst.next();
		      else { // need first edge to finish up
		      	stop=1;
		      	elst=elist.iterator(); // restart iterator
		      	edge=(EdgeSimple)elst.next();
		      }
		      ans=p.left_face(edge.v,edge.w);
		      f2=ans[0];
		      u2=ans[1];
		      if (f2<0) throw new CombException();
	      }
	    } // end of while
	    return bP;
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
		  
		  if (debug) System.out.println("Current redlist:\n"+LayoutBugs.quick_redlist(redface)); // debug=true;

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

		  if (debug) LayoutBugs.log_Red_Hash(p,redface,null); // debug=true;
			  
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
			  epair.color=CPScreen.getFGColor();
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

		  if (debug) LayoutBugs.log_Red_Hash(p,redface,p.firstRedEdge);
		  
		  // find, record pairings
		  if (debug) LayoutBugs.log_PairLink(p,e_pairs);
		  
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

		  if (debug) LayoutBugs.log_PairLink(p,e_pairs);
		  
		  bP=new BuildPacket();
		  bP.firstRedEdge=firstRedEdge;
		  bP.redList=firstRedEdge;
		  bP.sidePairs=e_pairs;
		  bP.success=true;
		  return bP;
		}

		/** 
		 * Input: 'spot_ptr' in red chain and index 'indx' for a 'red edge' 
		 * of its face. Goal: to return the red face having the next contiguous 
		 * red edge forward (dir=1) or backward (dir=-1) and to pass back via
		 * 'util' the index of this new red edge (calling routine must grab
		 * 'util' on return). If 'indx' inappropriate (eg., -1), then return 
		 * the first red edge of this face if it has one, else the next red 
		 * edge (forward only). Return 'null' on error.
		 *   
		 * (Note: 'red edge' is edge on outer side of the red chain; 'index' 
		 * of an edge is the index of its first vertex in 'vert' vector for 
		 * the face.)  
		 */
		public RedList next_red_edge(int dir,RedList spot_ptr,int indx) 
		throws RedListException {
		  int vert,n_indx,inx;
		  RedList trace;

		  inx=(p.face_nghb(spot_ptr.prev.face,spot_ptr.face)+1) % 3;
		  // see if 'indx' is beginning of a red edge; if not, use inx
		  if (indx!=inx && ((spot_ptr.prev.face!=spot_ptr.next.face) ||
				    indx!=(inx+1)%3)) {
		      indx=inx;
		      vert=p.faces[spot_ptr.face].vert[indx];
		      if ((n_indx=p.face_nghb(spot_ptr.next.face,spot_ptr.face))!=indx) {
		    	  // return with red edge in this face 
		    	  spot_ptr.util=indx;
		    	  return spot_ptr;
		      }
		      trace=spot_ptr.next;
		      while (trace!=spot_ptr) {
		    	  n_indx=p.face_nghb(trace.next.face,trace.face);
		    	  if (p.faces[trace.face].vert[n_indx]!=vert) {
		    		  trace.util=(n_indx+2) % 3;
		    		  return trace;
		    	  }
		    	  trace=trace.next;
		      }
		      return null; // should reach here iff there are no red edges at all.
		    }

		  // look forward in red chain 
		  if (dir==1) {
		      vert=p.faces[spot_ptr.face].vert[(indx+1) % 3];
		      if (spot_ptr.next.face==spot_ptr.prev.face
			  && spot_ptr.vIndex==(indx+1) % 3) {
			/* blue face, automatically has two red edges. If indx pts 
			   to first, pass back same face with index to second edge. */
			  spot_ptr.util=(indx+1) % 3;
			  return spot_ptr;
		      }
		      trace=spot_ptr.next;
		      while (trace!=spot_ptr) {
			  n_indx=p.face_nghb(trace.next.face,trace.face);
			  if (p.faces[trace.face].vert[n_indx]!=vert) {
			      if (trace.next.face==trace.prev.face)
				trace.util=(n_indx+1)%3;
			      else trace.util=(n_indx+2) % 3;
			      return trace;
			  }
			  trace=trace.next;
		      }
		      throw new RedListException();
		    }
		  // look backward in red chain 
		  vert=p.faces[spot_ptr.face].vert[indx];
		  if (spot_ptr.next.face==spot_ptr.prev.face
		      && spot_ptr.vIndex==indx) { /* blue face at second edge; 
						    pass back index to first */
		      spot_ptr.util=(indx+2) % 3;
		      return spot_ptr;
		  } 
		  trace=spot_ptr.prev;
		  while (trace!=spot_ptr) {
		      n_indx=(p.face_nghb(trace.prev.face,trace.face)+1) % 3;
		      if (p.faces[trace.face].vert[n_indx]!=vert) {
			  if (trace.prev.face==trace.next.face) // blue 
			    trace.util=(n_indx+1)%3;
			  else trace.util=n_indx;
			  return trace;
		      }
		      trace=trace.prev;
		  }
		  throw new RedListException();
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
		  num=p.kData[seed].num;
		  f=p.kData[seed].faceFlower[0];
		  red_chain=new RedList(p,f); // start fresh
		  p.faces[f].rwbFlag=1;
		  p.faces[f].indexFlag=red_chain.vIndex=p.face_index(f,seed);
		  
		  VertList faceOrder=new VertList(f);
		  fDo_tmp=faceOrder; // global pointer to faceOrder 
		  for (int i=1;i<num;i++) {
		      f=p.kData[seed].faceFlower[i];
		      fDo_tmp=new VertList(fDo_tmp,f);
		      red_chain=new RedList(red_chain,f);
		      red_chain.done=isDone(p,red_chain);
		      p.faces[f].rwbFlag++;
		      p.faces[f].indexFlag=p.face_index(f,seed);
		      red_chain.vIndex=((p.faces[f].indexFlag+2)%3);
		  }
		  
		  // If 'seed' is boundary, backtrack to get its full flower
		  if (p.kData[seed].bdryFlag!=0)  
		      for (int i=num-2;i>0;i--) {
				  red_chain=new RedList(red_chain,f);
			      red_chain.done=isDone(p,red_chain);
				  f=p.kData[seed].faceFlower[i];
				  w=p.kData[seed].flower[i];
				  red_chain.vIndex=p.face_index(f,w);
		      }

		  stop_ptr=red_chain;
	      if (debug) { // debug=true;  see the current red chain
			  System.out.println("red_chain (first pass): stop="+stop_ptr.face+"\n"+
					  LayoutBugs.quick_redlist(red_chain));
			  CPBase.Flink=LayoutBugs.redChain2redLink(red_chain); // now display them
		  }
		  
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
		    	  if (debug) { 
					  System.out.println("red_chain: stop="+stop_ptr.face+"\n"+
							  LayoutBugs.quick_redlist(red_chain));
					  CPBase.Flink=LayoutBugs.redChain2redLink(red_chain); // now display them
		    	  }
			  }

		      // ============================
		      // multiple passes, loop-overs only (don't create blues)
		      while (tripCount<2 && loop_count<looplimit) { 
		    	  if (loop_vert(false)) { // if true, added a face, so reset stop_ptr
		    		  stop_ptr=red_chain;
		    		  if (debug) {
						  System.out.println("red_chain: stop="+stop_ptr.face+"\n"+
								  LayoutBugs.quick_redlist(red_chain));
						  CPBase.Flink=LayoutBugs.redChain2redLink(red_chain); // now display them
		    		  }
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
						  "stop="+stop_ptr.face+"\n"+
						  LayoutBugs.quick_redlist(red_chain));
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
		    		  if (debug) {
		    			  System.out.println("loop_vert passes with loop true:\n"+
		    					  LayoutBugs.quick_redlist(red_chain));
		    			  CPBase.Flink=LayoutBugs.redChain2redLink(red_chain); // now display them
		    		  }
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
					  "stop="+stop_ptr.face+"\n"+
					  LayoutBugs.quick_redlist(red_chain));
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
		      if ((num=p.kData[vert].num) > 1) { // any more faces?
		    	  // findex = flower index of first vertex in cface 
		    	  findex=0;
		    	  while (p.kData[vert].faceFlower[findex]!=cface 
		    			  && findex<(num-1)) 
		    		  findex++;
		    	  eindex=bindex=findex; // all these are the same in this case
		 
		    	  // if vert interior, see if we can loop red chain around it
		    	  if (p.kData[vert].bdryFlag==0) {
		    		  i=bindex+1;
		    		  laso_flag=true;
		    		  while ((j=(i % num))!=eindex && laso_flag) {
		    			  if (p.faces[p.kData[vert].faceFlower[j]].rwbFlag>0) 
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
		    			  int nf=p.kData[vert].faceFlower[i];
		    			  fDo_tmp=add_face_order(fDo_tmp,nf,of);
		    			  new_end=new RedList(new_end,nf);
		    			  new_end.done=isDone(p,new_end);
		    			  p.faces[nf].rwbFlag++;
		    			  got_one=true;

		    			  // continue until you get a new redface for cface
		    			  i=(i+1) % num;
		    			  while ( i!=((findex+1) % num)) {
		    				  of=new_end.face; 
		    				  nf=p.kData[vert].faceFlower[i];
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
		    	  if (mode && (p.kData[vert].bdryFlag!=0 || !laso_flag)) {
		    		  j=(eindex-1+num) % num; 
		    		  if ( ((p.kData[vert].bdryFlag!=0 && (eindex > 0)) 
		    				  || p.kData[vert].bdryFlag==0)
		    				  && p.faces[p.kData[vert].faceFlower[j]].rwbFlag==0) {
		    			  f=p.kData[vert].faceFlower[j];
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
		  num=p.kData[vert].num;
		  findex=0;
		  while (p.kData[vert].faceFlower[findex]!=cface && findex<(num-1)) 
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
		  while(bzip.prev.face==p.kData[vert].faceFlower[(i+1) % num]
			  && bzip.prev.face!=cface ) {
		      bzip=bzip.prev;
		      i++;
		  }
		  bindex=(i % num);
		  
		  /* If 'red_chain' is not blue, 'vert' is boundary, and its 
		   * whole fan is already red, don't need to revisit this face, 
		   * so set done=true.
		   */
		  if (p.kData[vert].bdryFlag!=0 && eindex==0 && bindex==(num-1) 
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
		  if (p.kData[vert].bdryFlag==0) { // vert is interior
	    	  new_end=bzip;
		      i=bindex+1;
		      while ( (j=(i % num))!=eindex && laso_flag) {
		    	  if (p.faces[p.kData[vert].faceFlower[j]].rwbFlag!=0) 
		    		  laso_flag=false;
		    	  i++;
		      }

		      // yes, yes, rest of faces white and vert is not poison 
		      if (laso_flag && (!poisonFlag || !p.vert_isPoison(vert))) {
	    		  got_one=true;
		    	  if ( (i=(bindex+1) % num)!=eindex ) { // new redfaces
		    		  int of=new_end.face;
		    		  int nf=p.kData[vert].faceFlower[i];
		    		  fDo_tmp=add_face_order(fDo_tmp,nf,of);
		    		  new_end=new RedList(new_end,nf);
		    		  new_end.done=isDone(p,new_end);
		    		  p.faces[nf].rwbFlag++;
		    		  i++;
		    		  while ( (j=i % num)!=eindex) {
		    			  of=new_end.face; 
		    			  nf=p.kData[vert].faceFlower[j];
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
		    		  p.faces[p.kData[vert].faceFlower[i]].rwbFlag--;
		    		  if (p.faces[p.kData[vert].faceFlower[i]].rwbFlag==0)
		    			  p.faces[p.kData[vert].faceFlower[i]].rwbFlag=-1;
		    		  i=(i+1)%num;
		    	  }
		      }
		  }
		  
		  /* otherwise, if possible, add blue face preceeding fan (i.e., 
		   * clockwise from first face of fan (which is eindex). */ 
		  if (mode && (p.kData[vert].bdryFlag!=0 || !laso_flag)) {
		      cface=p.kData[vert].faceFlower[eindex];
		      j=(eindex-1+num) % num;
		      f=p.kData[vert].faceFlower[j]; // ??? was (j+1) % num];
		      // is there a blue face to add?
		      if (((p.kData[vert].bdryFlag!=0 && (eindex > 0)) 
			    || p.kData[vert].bdryFlag==0) && p.faces[f].rwbFlag==0) {
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
				if (p.kData[v].num==1) return true;
			}
			
			if (vd==vu) return true; // rl.face is inside a fan of faces
			if (poisonFlag && ((p.vert_isPoison(vd) && p.vert_isPoison(vu)) ||
					p.edge_isPoison(vd,vu))) 
				return true;
			int j=p.nghb(vd,vu);
			if (p.kData[vd].bdryFlag!=0 && p.kData[vd].num==j)
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
		
		/**
		 * This clones a full linked list of 'RedList' (and/or 'RedEdge')
		 * objects (assumed to be a closed list) so it is independent of 
		 * the original. In particular, all new next/prev and nextRed/prevRed 
		 * pointers point to the new linked list. (This can be used, e.g., 
		 * to save a redchain while an alternate is created in case the 
		 * original is still needed. Careful not to change 
		 * 'packData.redchain' and mess up the original.)
		 * @param redchain 'RedList'
		 * @return new 'RedList' pointing to the new linked list, or null
		 */
		public static RedList cloneRedChain(RedList redchain) {
			if (redchain==null) return null;
			RedList newList=null;

			RedList ntrace=newList;
			RedList trace=redchain.next;
			
			// clone the first entry, save initial pointers
			if (redchain instanceof RedEdge) {
				RedEdge redge=(RedEdge)redchain;
				newList=RedEdge.clone(redge);
			}
			else newList=RedList.clone(redchain);
			ntrace=newList;

			// Now clone down the list, setting up next/prev; we'll
			//   do a second pass to set nextRed/prevRed.
			int safety=0;
			while (trace!=redchain && safety<100000) {
				if (trace instanceof RedEdge) {
					RedEdge redge=(RedEdge)trace;
					ntrace.next=RedEdge.clone(redge);
					ntrace.next.prev=ntrace;
				}
				else { 
					ntrace.next=RedEdge.clone(trace);
					ntrace.next.prev=ntrace;
				}
				ntrace=ntrace.next;
				trace=trace.next;
				safety++;
			}
			if (safety>=100000) {
				throw new RedListException();
			}
			ntrace.next=newList;
			newList.prev=ntrace;
			
			// done with cloning and next/prev linkage
			
			// Now have to set nextRed/prevRed pointers
			//  We go down the lists in step.
			trace=redchain;
			ntrace=newList;
			
			// check first spot
			safety=0;
			RedEdge hold_re=null;
			RedEdge first_re=null;
			if (redchain instanceof RedEdge) {
				first_re=hold_re=(RedEdge)ntrace;
				RedEdge redge=(RedEdge)trace;
				while (trace!=redge.nextRed && safety<100000) {
					trace=trace.next;
					ntrace=ntrace.next;
					safety++;
				}
				if (safety>=100000 || !(ntrace instanceof RedEdge)) 
					throw new RedListException();
				hold_re.nextRed=(RedEdge)ntrace;
				hold_re.nextRed.prevRed=hold_re;
				hold_re=hold_re.nextRed;
			}
			else {
				trace=trace.next;
				ntrace=ntrace.next;
			}
			
			// now get the rest
			safety=0;
			while (trace!=redchain && safety<100000) {
				// find the next 'RedEdge'
				while (trace!=redchain && !(trace instanceof RedEdge) 
						&& safety<100000) {
					trace=trace.next;
					ntrace=ntrace.next;
					safety++;
				}
				if (safety>=100000 || !(ntrace instanceof RedEdge)) 
					throw new RedListException();
				if (trace==redchain) {
					if (first_re==null) return newList; // no 'RedEdge' entries
					if (hold_re==first_re)  
						throw new RedListException(); // must be more than one
					
					// OK, just close 'RedEdge' links
					hold_re.nextRed=first_re;
					first_re.prevRed=hold_re;
					return newList;
				}
				if (first_re==null) first_re=hold_re=(RedEdge)ntrace;
				else {
					hold_re.nextRed=(RedEdge)ntrace;
					hold_re.nextRed.prevRed=hold_re;
					hold_re=hold_re.nextRed;
				}
				safety++;
			}
			if (safety>=100000) throw new RedListException();
			throw new RedListException(); // shouldn't get here
		}
		
}		

/** local utility class for inner loop in 'simplify_redchain' */
class skip_to_end extends Exception {

	private static final long 
	serialVersionUID = 1L;
}
