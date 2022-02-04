package dcel;

import java.util.ArrayList;
import java.util.Iterator;

import exceptions.CombException;
import exceptions.DCELException;
import komplex.EdgeSimple;
import listManip.HalfLink;
import listManip.NodeLink;

/**
 * These are static utility routines concerned with paths
 * in DCEL structures: closed paths, non-separating, etc.
 * These are used, e.g., to find nice red chains for tori.
 * 
 * TODO: Some ideas have not worked out as expected --- it's
 * complicated. I'm still looking for efficient way to find 
 * the "shortest" path in a homotopy class. 

 * @author kstephe2, 7/2021
 *
 */
public class PathDCEL {


	  /**
	   * Using the red chain and 'pairLink', find a short 
	   * non-separating path of interior edges which either
	   * is closed or starts/ends on distinct boundary 
	   * components. CAN NOT guarantee that it is minimal 
	   * length in its homotopy class.
	   * @param pdcel PackDCEL
	   * @return HalfLink or null on failure
	   */
	  public static HalfLink getNonSeparating(PackDCEL pdcel) {
		  
		  if (pdcel.p.isSimplyConnected())
			  throw new CombException("this complex is simply connected");
		  
		  // we depend on the red chain
		  if (pdcel.redChain==null) {
			  int ans=CombDCEL.redchain_by_edge(pdcel, null, null, false);
			  if (ans==0)
				  throw new CombException("No red chain, and failed to create one");
			  CombDCEL.fillInside(pdcel); // this sets side-pairings
		  }
		  
		  // first, check pairLink for shortest side which is either:
		  //   * paired and closed, or
		  //   * paired and starts ends on distinct bdry comps.
		  int bestSide=-1;
		  int shortest=10*pdcel.vertCount;
		  int[] lengths=new int[pdcel.pairLink.size()]; // find lengths
		  Iterator<SideData> dsp=pdcel.pairLink.iterator();
		  dsp.next(); // first is empty
		  while (dsp.hasNext()) {
			  SideData sdata=dsp.next();
			  int mIndx=sdata.mateIndex;
			  if (mIndx>0) { // is paired
				  RedEdge oppStart=pdcel.pairLink.get(mIndx).startEdge;
				  int end1=sdata.startEdge.myEdge.origin.vertIndx;
				  int end2=oppStart.myEdge.origin.vertIndx;
				  
				  // closed?
				  if (end1==end2) {
					  lengths[sdata.spIndex]=sdata.sideCount();
					  if (lengths[sdata.spIndex]<shortest) {
						  shortest=lengths[sdata.spIndex];
						  bestSide=sdata.spIndex;
					  }
				  }
				  
				  // both ends on bdry?
				  else if (oppStart.myEdge.origin.bdryFlag!=0 &&
						  sdata.startEdge.myEdge.origin.bdryFlag!=0 &&
						  !CombDCEL.onSameBdryComp(pdcel, end1, end2)) { 
					  lengths[sdata.spIndex]=sdata.sideCount();
					  if (lengths[sdata.spIndex]<shortest) {
						  shortest=lengths[sdata.spIndex];
						  bestSide=sdata.spIndex;
					  }
				  }
			  }
		  } // done finding qualifying lengths
		  
		  // found one?
		  if (bestSide!=-1)
			  return pdcel.pairLink.get(bestSide).sideHalfLink();
		  
		  // else, cycle through side 'startEdge's origins and find
		  //    the one for which the count of edges between it
		  //    and its first cclw repeat is shortest and those are
		  //    all interior edges.
		  dsp=pdcel.pairLink.iterator();
		  dsp.next(); // first is null
		  while (dsp.hasNext()) {
			  SideData sdata=dsp.next();
			  RedEdge rtrace=sdata.startEdge;
			  int end1=rtrace.myEdge.origin.vertIndx;
			  rtrace=rtrace.nextRed;
			  int tick=0;
			  RedEdge stopRed=sdata.startEdge.prevRed;
			  INNER_WHILE: while (rtrace!=stopRed && tick>=0 && 
					  rtrace.myEdge.origin.vertIndx!=end1) {
				  HalfEdge he=rtrace.myEdge;
				  if (he.twin.face!=null && he.twin.face.faceIndx<0) {
					  tick=0;
					  break INNER_WHILE;
				  }
				  tick++;
				  rtrace=rtrace.nextRed;
			  }
			  if (tick>0) { // will this always happen? I think so
				  lengths[sdata.spIndex]=tick;
			  }
		  } // done getting lengths between repeats

		  for (int j=1;j<lengths.length;j++) {
			  if (lengths[j]<shortest) {
				  shortest=lengths[j];
				  bestSide=j;
			  }
		  }
		  
		  // I don't believe this can fail because one of the
		  //   earlier paths would have necessarily succeeded
		  //   (I think).
		  if (bestSide==-1) {
			  throw new DCELException("'getNonSeparating' error failed");
		  }
		  
		  // return 
		  HalfLink hlink=new HalfLink();
		  RedEdge rtrace=pdcel.pairLink.get(bestSide).startEdge;
		  int end1=rtrace.myEdge.origin.vertIndx;
		  hlink.add(rtrace.myEdge);
		  rtrace=rtrace.nextRed;
		  while (rtrace.myEdge.origin.vertIndx!=end1) {
			  hlink.add(rtrace.myEdge);
			  rtrace=rtrace.nextRed;
		  }
		  
		  return hlink;
	  }
	  
	  /**
	   * Return the shortest closed interior edge path which
	   * misses 'path' except for starting/ending at the origin
	   * of 'seededge' (which must lie in 'path'). Note: 'path' 
	   * may have multiple connected components, but it 'path' 
	   * separates the complex, we get an exception. 
	   * Normally 'path' either closed or with bdry endpoints;
	   * its edges must be interior. We work by counting 
	   * generations of interiors from 'seededge' on the left 
	   * and right of 'path'.  
	   * @param pdcel PackDCEL
	   * @param path HalfLink
	   * @param HalfEdge seededge, and edge of 'path'
	   * @return PackDCEL or null on error
	   */
	  public static HalfLink getCutPath(PackDCEL pdcel,
			  HalfLink path,HalfEdge seededge) {

		  if (path==null || path.size()==0 || seededge==null)
			  return null;

		  // set all 'vutil' to bound = "untouched"
		  int bound=pdcel.vertCount+1;
		  for (int v=1;v<=pdcel.vertCount;v++) {
			  pdcel.vertices[v].vutil=bound;
		  }
		  
		  // zero 'vutil' on 'path', check for bdry edges
		  Iterator<HalfEdge> pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfEdge he=pis.next();
			  if (pdcel.isBdryEdge(he))
				  throw new DCELException("'getCutPath' error: edge "+
						  he+" is a bdry edge");
			  he.origin.vutil=0;
		  }
		  
		  // zero out bdry 'vutil' so we don't try to cross bdry
		  for (int i=1;i<=pdcel.idealFaceCount;i++) {
			  HalfEdge stpe=pdcel.idealFaces[i].edge;
			  HalfEdge he=stpe;
			  do {
				  he.origin.vutil=0;
				  he=he.next;
			  } while (he!=stpe);
		  }

		  // find 'left/rightfan's of 'seededge'; used to get
		  //   'vl' and 'vr'; also needed at to consider shortening
		  ArrayList<Integer> leftfan=new ArrayList<Integer>(0);
		  ArrayList<Integer> rightfan=new ArrayList<Integer>(0);
		  HalfEdge he=seededge.prev.twin;
		  while (he.twin.origin.vutil>0) {
			  leftfan.add(he.twin.origin.vertIndx);
			  he=he.prev.twin; // cclw
		  }
		  he=seededge.twin.next;
		  while (he.twin.origin.vutil>0) {
			  rightfan.add(he.twin.origin.vertIndx);
			  he=he.twin.next; // clw
		  }
		  
		  if (leftfan.size()==0 || rightfan.size()==0)
			  throw new CombException("blue edge?? can't move to nghb on "+
					  		"one side or other");
		  int vl=leftfan.get(0); // left = plus side
		  int vr=rightfan.get(0); // right = minus side
		  int seedOrigin=seededge.origin.vertIndx;

		  // generation 1: 'vutil' +/- 1 on left/right of 'seededge'
		  // two-list method to count successive generations (+/-)
		  NodeLink currv=new NodeLink();
		  NodeLink nextv=new NodeLink();
		  boolean lhit=false;
		  boolean rhit=false;
		  if (pdcel.vertices[vl].vutil==bound) {
			  pdcel.vertices[vl].vutil=1;
			  nextv.add(vl);
			  lhit=true;
		  }
		  if (pdcel.vertices[vr].vutil==bound) {
			  pdcel.vertices[vr].vutil=-1;
			  nextv.add(vr);
			  rhit=true;
		  }
		  if (!lhit || !rhit) {
			  throw new CombException("failed to get started with + or - vertices");
		  }
		  
		  int safety=2*bound;
		  int hitvert=0; // first vert with both + and - nghbs
		  while (nextv.size()>0 && hitvert==0 && safety>0) {
			  currv=nextv;
			  nextv=new NodeLink();
			  Iterator<Integer> cis=currv.iterator();
			  while (cis.hasNext() && hitvert==0) {
				  Vertex vert=pdcel.vertices[cis.next()];
				  int myUtil=vert.vutil; // must already be on left or right
				  int[] petals=vert.getPetals();
				  for (int j=0;(j<petals.length && hitvert==0);j++) {
					  Vertex wert=pdcel.vertices[petals[j]];
					  if (wert.vutil>=bound) { // first touch?
						  if (myUtil<0) // right side hit
							  wert.vutil=myUtil-1;
						  else { // left side hit
							  wert.vutil=myUtil+1;
						  }
						  nextv.add(wert.vertIndx);
					  }
					  // are we done?
					  else if (wert.vutil!=0) {  
						  if ((myUtil<0 && wert.vutil>0) ||
								  (myUtil>0 && wert.vutil<0)) { 
							  hitvert=vert.vertIndx;
						  }
					  }
				  } // done with 'petals'
			  } // done with while on currv
			  safety--;
		  } // done with while on nextv
		  
		  if (safety<=0 || hitvert==0) {
			  throw new DCELException("hum...? no collision "+
					  "or overran safety in 'ShortCut'");
		  }
		  
		  // build 'cutPath' from left side of 'seededge' to
		  //   right side: get from 'hitvert' to 'vl', then
		  //   from 'hitver' to 'vr'.
		  HalfLink cutPath=new HalfLink();
		  
		  // find first + vert, first - vert.
		  Vertex vert=pdcel.vertices[hitvert];
		  int plus1=0;
		  int minus1=0;
		  if (vert.vutil<0) {
			  minus1=vert.vertIndx;
			  int[] petals=vert.getPetals();
			  for (int j=0;(j<petals.length && plus1==0);j++) {
				  if (pdcel.vertices[petals[j]].vutil>0) {
					  plus1=petals[j];
					  break;
				  }
			  }
		  }
		  else {
			  plus1=vert.vertIndx;
			  int[] petals=vert.getPetals();
			  for (int j=0;(j<petals.length && minus1==0);j++) {
				  if (pdcel.vertices[petals[j]].vutil<0) {
					  minus1=petals[j];
					  break;
				  }
			  }
		  }

		  // get the plus side back to 'vl'.
		  int newv=plus1;
		  while (newv!=vl) {
			  vert=pdcel.vertices[newv];
			  int myUtil=vert.vutil; // should be positive
			  int[] petals=vert.getPetals();
			  for (int j=0;j<petals.length;j++) {
				  int nutil=pdcel.vertices[petals[j]].vutil;
				  if (nutil>0 && nutil<myUtil) {
					  cutPath.add(pdcel.
							  findHalfEdge(new EdgeSimple(petals[j],newv)));
					  newv=petals[j];
					  break;
				  }
			  }
		  }
		  if (newv!=vl) {
			  throw new CombException("didn't reach 'vl' as expected");
		  }
		  cutPath.add(pdcel.findHalfEdge(
				  new EdgeSimple(seedOrigin,vl)));
		  
		  // 'HalfEdges' are going right way, just reverse the list
		  cutPath=HalfLink.reverseLink(cutPath);
		  
		  // here's the edge connecting plus to minus halves
		  cutPath.add(pdcel.findHalfEdge(new EdgeSimple(plus1,minus1)));
		  
		  // now get path to 'vr'; make sure hitvert vutil is <0
		  newv=minus1;
		  HalfLink minushalf=new HalfLink();
		  vert=pdcel.vertices[newv];
		  while (newv!=vr) {
			  vert=pdcel.vertices[newv];
			  int myUtil=vert.vutil; // this is negative
			  int[] petals=vert.getPetals();
			  for (int j=0;j<petals.length;j++) {
				  int nutil=pdcel.vertices[petals[j]].vutil;
				  if (nutil<0 && nutil>myUtil) {
					  cutPath.add(pdcel.
							  findHalfEdge(new EdgeSimple(newv,petals[j])));
					  newv=petals[j];
					  break;
				  }
			  }
		  }
		  if (newv!=vr) {
			  throw new CombException("didn't reach 'vr' as expected");
		  }

		  minushalf.add(pdcel.findHalfEdge(
				  new EdgeSimple(vr,seedOrigin)));
		  
		  cutPath.abutMore(minushalf);
		  
		  // May be able to shorten at beginning (plus side)
		  int ed=cutPath.get(1).twin.origin.vertIndx;
		  if (leftfan.contains(ed)) { // yes, can shortcut
			  cutPath.remove(1); // remove first two
			  cutPath.remove(0); 
			  // replace first two steps by <seedOrigin,ed>
			  cutPath.add(0,pdcel.findHalfEdge(seedOrigin,ed)); 
		  }
		  
		  // and/or at end
		  int sz=cutPath.size();
		  ed = cutPath.get(sz-2).origin.vertIndx;
		  if (rightfan.contains(ed)) { // yes, can shortcut
			  cutPath.removeLast(); // remove last two
			  cutPath.removeLast();
			  // replace last two steps by <ed,seedOrigin>
			  cutPath.add(pdcel.findHalfEdge(ed,seedOrigin));
		  }
		  
		  // 'cutPath should start/stop at 'seedOrigin'
		  return cutPath;
	  }
	  
	  /**
	   * Return 'HalfLink' path of interior edges which 
	   * is among the shortest combinatorially starting 
	   * and ending at an end of 'seed' edge without 
	   * crossing 'path'. Make a small shift of ends
	   * if it will close the path without lengthening it
	   * else end with 'seed' itself to close up.
	   * @param pdcel PackDCEL
	   * @param path HalfLink
	   * @param seed HalfLink
	   * @return HalfLink
	   */
	  public static HalfLink getShortPath(PackDCEL pdcel,
			  HalfLink path,HalfLink seed) {
		  HalfLink link1=new HalfLink();
		  int bound=pdcel.vertCount+1;
		  
		  // set all 'vutil' to bound = "untouched"
		  for (int v=1;v<=pdcel.vertCount;v++) {
			  pdcel.vertices[v].vutil=bound;
		  }
		  
		  // two-list method to count generations (+/-)
		  NodeLink currv=new NodeLink();
		  NodeLink nextv=new NodeLink();

		  // set 'vutil' 0 on 'path'
		  Iterator<HalfEdge> pis=path.iterator();
		  while (pis.hasNext()) {
			  HalfEdge he=pis.next();
			  he.origin.vutil=0;
			  he.twin.origin.vutil=0;
		  }
		  
		  // set 'vutil' +/- on left/right of 'seed' edges
		  boolean lhit=false;
		  boolean rhit=false;
		  Iterator<HalfEdge> sis=seed.iterator();
		  while (sis.hasNext()) {
			  HalfEdge he=sis.next();
			  int vl=he.next.next.origin.vertIndx;
			  int vr=he.twin.next.next.origin.vertIndx;
			  if (pdcel.vertices[vl].vutil==bound) {
				  pdcel.vertices[vl].vutil=1;
				  nextv.add(vl);
				  lhit=true;
			  }
			  if (pdcel.vertices[vr].vutil==bound) {
				  pdcel.vertices[vr].vutil=-1;
				  nextv.add(vr);
				  rhit=true;
			  }
		  }
		  if (!lhit || !rhit) {
			  throw new CombException("failed to get started with + or - vertices");
		  }
		  
		  int safety=2*bound;
		  int hitvert=0; // first hit (has both + and - nghb)
		  while (nextv.size()>0 && hitvert==0 && safety>0) {
			  currv=nextv;
			  nextv=new NodeLink();
			  Iterator<Integer> cis=currv.iterator();
			  while (cis.hasNext() && hitvert==0) {
				  Vertex vert=pdcel.vertices[cis.next()];
				  int myUtil=vert.vutil; // already on left or right
				  int[] petals=vert.getPetals();
				  for (int j=0;(j<petals.length && hitvert==0);j++) {
					  Vertex wert=pdcel.vertices[petals[j]];
					  if (wert.vutil>=bound) { // first touch?
						  if (myUtil<0) // right side hit
							  wert.vutil=myUtil-1;
						  else { // left side hit
							  wert.vutil=myUtil+1;
						  }
						  nextv.add(wert.vertIndx);
					  }
					  else { // wert was alreadly left or right
						  
						  // are we done?
						  if ((myUtil<0 && wert.vutil>0) ||
								  (myUtil>0 && wert.vutil<0)) { 
							  hitvert=vert.vertIndx;
						  }
						  else
							  nextv.add(wert.vertIndx);
					  }
				  } // done with 'petals'
			  } // done with while on currv
			  safety--;
		  } // done with while on nextv
		  
		  if (safety<=0 || hitvert==0) {
			  throw new DCELException("hum...? no collision "+
					  "or overran safety in 'ShortCut'");
		  }
		  
		  // +/- generations first collide at 'hitvert'
		  // for 'HalfLink' from left side to right side 
		  if (hitvert!=0) {
			  
			  // find +/- petals
			  Vertex hitVert=pdcel.vertices[hitvert];
			  int vneg=0;
			  int vpos=0;
			  int[] petals=hitVert.getPetals(); // open flower
			  for (int j=0;(j<petals.length && (vneg==0 || vpos==0));j++) {
				  int val=pdcel.vertices[petals[j]].vutil;
				  if (vneg==0 && val<0) 
					  vneg=petals[j];
				  if (vpos==0 && val>0 && val<bound)
					  vpos=petals[j];
			  }
			  if (vneg==0 || vpos==0) {
				  throw new DCELException("not collision at "+hitvert+"??");
			  }
		  
			  // walk back through increasingly smaller + generations
			  link1.add(pdcel.findHalfEdge(new EdgeSimple(hitvert,vpos)));
			  while (pdcel.vertices[vpos].vutil!=0) {
				  HalfLink eflower=pdcel.vertices[vpos].getEdgeFlower();
				  int myindx=pdcel.vertices[vpos].vutil;
				  HalfEdge hhedge=null;
				  Iterator<HalfEdge> eis=eflower.iterator();
				  while (eis.hasNext() && hhedge==null) {
					  HalfEdge he=eis.next();
					  if (he.twin.origin.vutil==myindx-1)
						  hhedge=he;
				  }
				  if (hhedge==null) {
					  throw new DCELException("lost + generational link");
				  }
				  link1.add(hhedge);
				  vpos=hhedge.twin.origin.vertIndx;
			  }
			  link1=HalfLink.reverseElements(link1);
			  link1=HalfLink.reverseLink(link1);
			  
			  // now walk through increasingly less - generations
			  while (pdcel.vertices[vneg].vutil!=0) {
				  HalfLink eflower=pdcel.vertices[vneg].getEdgeFlower();
				  int myindx=pdcel.vertices[vneg].vutil;
				  HalfEdge hhedge=null;
				  Iterator<HalfEdge> eis=eflower.iterator();
				  while (eis.hasNext() && hhedge==null) {
					  HalfEdge he=eis.next();
					  if (he.twin.origin.vutil==(myindx+1))
						  hhedge=he;
				  }
				  if (hhedge==null) {
					  throw new DCELException("lost - generational link");
				  }
				  link1.add(hhedge);
				  vneg=hhedge.twin.origin.vertIndx;
			  }
		  }
		  
		  // closed already?
		  HalfEdge edgefirst=link1.get(0);
		  HalfEdge edgelast=link1.getLast();
		  int v=edgefirst.origin.vertIndx;
		  int w=edgelast.twin.origin.vertIndx;
		  if (v==w)
			  return link1;
		  
		  // simple adjustment?
		  if (edgefirst.next.next.origin.vertIndx==w) {
			  link1.add(0,edgefirst.next.twin);
			  return link1;
		  }
		  if (edgelast.prev.origin.vertIndx==w) {
			  link1.add(0,edgefirst.next.next);
			  return link1;
		  }
		  int lastIndx=link1.size()-1;
		  if (edgelast.twin.prev.origin.vertIndx==v) {
			  link1.add(lastIndx,edgelast.twin.next);
			  return link1;
		  }
		  if (edgelast.next.origin.vertIndx==v) {
			  link1.add(lastIndx,edgelast.twin.prev.twin);
			  return link1;
		  }

		  return link1;
	  }
	  
}
