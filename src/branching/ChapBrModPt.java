package branching;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import canvasses.DisplayParser;
import circlePack.PackControl;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import dcel.RawManip;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import ftnTheory.GenModBranching;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.HalfLink;
import math.Mobius;
import util.StringUtil;

/**
 * This is a new approach to "chaperone" generalized branch
 * points. The old approach in 'ChapBranchPt' excised a flower,
 * then manipulated it separately from the parent packing. In
 * this new approach, we will modify the parent packing itself
 * in a way that can be undone. 
 * 
 * The parameters are the "jump" circles w1/w2 and associated 
 * overlap angles. 
 * 
 * Construction goes like this:
 * (1) split_flower of myIndex, creating sister2
 *     and sisterEdge=<myIndex,sister2>. Petals of sister2
 *     go from w1 to preJump[2], petals of myIndex go
 *     from we to preJump[1].
 * (2) set chapEdge[1]=<sister2,w1>, then
 *     split_edge; end up with chapEdge[1]=<sister2,chap1>
 * (3) set chapEdge[2]=<myIndex,w2>, then
 *     split_edge; end up with chapEdge[2]=<myIndex,chap2>
 * (4) split_edge sisterEdge to create newBrSpot.
 * 
 * Delete by reversing: melt_edge in this order
 *   sisterEdge -> chapEdge[2] -> chapEdge[1] -> sisterEdge
 *   
 * The overlaps o1/o2 are assigned to cclw spokes from chap1/2
 * to preJump[1]/[2], and complementary angles to edges next
 * cclw spokes.
 *  
 * @author kstephe2, 9/2021
 *
 */
public class ChapBrModPt extends GenBrModPt {
	
	int myIndex;         // index for 'myEdge.origin'
	
	// parameters: indices start with 1 (not 0)
	int[] jumpCircle;
	double[] cos_overs;
	
	// needed in construction/destruction
	HalfEdge[] jumpEdge; // from tangent sister
	HalfEdge[] preJump;	 // from other sister
	HalfEdge[] chapEdge; // from sister1/2 to chap1/2
	HalfEdge sisterEdge; // <myIndex,sister2>
	
	// need for overlap data
	HalfEdge[] overEdge; // for overlaps, <chap[*],preJump[*]>
	HalfEdge[] compEdge; // for overlap complements

	// four added circles: chaperones and these
	Vertex[] chap;     // chap[1]/[2]
	Vertex sister2;	// sister created for 'myIndex'
	Vertex newBrSpot;	// vertex carrying 'myAim'
	
	// Constructor
	public ChapBrModPt(GenModBranching g,int bID,double aim,
			int v,int w1,int w2,double o1,double o2) {
		super(g,bID,aim);
		gmb=g;
		myType=GenBrModPt.CHAPERONE;
		myEdge=pdc.vertices[v].halfedge;
		myIndex=v;
		
		if (p.getBdryFlag(myIndex)!=0 || p.countFaces(myIndex)<5)
			throw new CombException(
					"chaperone vert must be interior, degree at least 5");

		int[] petals=p.getPetals(myIndex);
		int num=petals.length;
		int indx1=p.nghb(myIndex, w1);
		int indx2=p.nghb(myIndex, w2);
		if (indx1<0 || indx2<0)
			throw new ParserException(
					w1+" and/or "+w2+" is not a neighbor of "+v);
		if (indx1==indx2)
			throw new CombException(
					"petals must be distinct");
		
		// jump circles
		jumpCircle=new int[3];
		jumpCircle[1]=w1;
		jumpCircle[2]=w2;
		
		jumpEdge=new HalfEdge[3];
		jumpEdge[1]=pdc.findHalfEdge(new EdgeSimple(myIndex,jumpCircle[1]));
		jumpEdge[2]=pdc.findHalfEdge(new EdgeSimple(myIndex,jumpCircle[2]));
		
		preJump=new HalfEdge[3];
		preJump[1]=jumpEdge[1].twin.next;
		preJump[2]=jumpEdge[2].twin.next;
		
		chapEdge=new HalfEdge[3];

		// set overlaps
		cos_overs=new double[3];
		cos_overs[1]=Math.cos(o1*Math.PI);
		cos_overs[2]=Math.cos(o2*Math.PI);

		// debug help
		System.out.println("chapMod attempt: a = "+aim/Math.PI+
				"; v = "+v+"; jumps "+w1+" "+w2+"; "+
				"overlaps/Pi "+o1+" "+o2);
				
		// this modifies the parent packing
		modifyPackData();
		
		success=true;
	}
	
	// **** abstract methods *************
	public void dismantle() {
		RawManip.meldEdge_raw(pdc,sisterEdge);
		RawManip.meldEdge_raw(pdc,chapEdge[2]);
		RawManip.meldEdge_raw(pdc,chapEdge[1]);
		RawManip.meldEdge_raw(pdc,sisterEdge);
		return;
	}
	
	public String reportExistence() {
		if (success)
			return new String(
				"Started 'chaperone' branching at v = "+myIndex);
		else 
			return new String(
					"Failed to initiate 'chaperone' branching for v = "+myIndex);
	}
	
	public String reportStatus() {
		return new String("'chap' ID"+branchID+": vert="+myIndex+
				"; jump circles "+jumpCircle[1]+", "+jumpCircle[2]+
				"; over1="+Math.acos(cos_overs[1])/Math.PI+
				"; over2="+Math.acos(cos_overs[2])/Math.PI+
				"; aim="+myAim/Math.PI+"; holonomy err="+
				Mobius.frobeniusNorm(getLocalHolonomy()));
	}

	/**
	 * The parameters for 'chaperone' type are (jump1, jump2),
	 * (indices for circles jumping to new sister) and 
	 * associated overlap angles over1, over2.
	 * @return String
	 */
	public String getParameters() {
		return new String("'chaperone' branch point: aim "+
				myAim/Math.PI+"*Pi, vertex "+myIndex+", jump circles "+
				jumpCircle[1]+", "+jumpCircle[2]+
				", overlaps "+Math.acos(cos_overs[1])/Math.PI+"*Pi "+
				Math.acos(cos_overs[2])/Math.PI+"*Pi");
	}

	/**
	 * Chaperone branch point parameters are jump circles 
	 * and corresponding overlap angles (to be multiplied by 
	 * PI here). We read "w1 w2 a1 a2"; a1, a2 are multiplied 
	 * by Pi and their cosines are stored in cos_overs[] 
	 * (negative is cosine of supplementary angle).
	 * @param flagSegs Vector<Vector<String>>, "w1 w2 a1 a2"
	 * @return int count; 0 on error, negative if new 'layoutTree' 
	 * is required in parent
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("usage: -a aim -j w1 w2 -o o1 o2");
		int count=0;
		boolean gotjumps=false;
		boolean gotovers=false;
		double []ovlp=new double[2];
		
		// parse the parameter info: -a aim, -j w1 w2, -o o1 o2
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				if (!StringUtil.isFlag(items.get(0)))
					throw new ParserException("usage: -a aim -j j1 j2 -o o1 o2");
				String str=items.remove(0);
				switch (str.charAt(1)) {
				case 'a': // new aim
				{
					myAim=Double.parseDouble(items.get(0))*Math.PI;
					p.setAim(newBrSpot.vertIndx,myAim);
					p.setRadius(newBrSpot.vertIndx,0.5); // kick-start repacking
					count++;
					break;
				}
				case 'j': // jump petals (as vertex indices from parent)
				{
					jumpCircle=new int[3]; 
					jumpCircle[1]=Integer.parseInt(items.get(0));
					jumpCircle[2]=Integer.parseInt(items.get(1));
					count += 2;
					gotjumps=true;
					break;
				}
				case 'o': // overlaps
				{
					for (int i=0;i<2;i++) {
						ovlp[i]=Double.parseDouble(items.remove(0));
						if (ovlp[i]<0 || ovlp[i]>1.0) {
							throw new ParserException("overlap not in [0,1]");
						}
						cos_overs[i+1]=Math.cos(ovlp[i]*Math.PI);
						count++;
					}
					count++;
					gotovers=true;
					break;
				}
				} // end of switch
			
				// TODO: may want to accommodate more jumps/overlaps in future,
				//       depending, e.g., on 'myAim'.
			} catch(Exception ex) {
				throw new ParserException(ex.getMessage());
			}
		}

		if (gotjumps) {
			int ans=modifyPackData();
			if (ans==0) {
				throw new ParserException("failed to modify packData");
			}
		}
		if (gotovers) {

			// reset overlaps
			return resetOverlaps(ovlp[0],ovlp[1]);
		}
		return count;
	}
	
	/**
	 * Changes in 'packData' may require us to reassert
	 * the data for this branch point. This is also called
	 * when a newly created branch point is installed.
	 */
	public void renew() {
		// reset the overlaps
		resetOverlaps(cos_overs[1],cos_overs[2]);
		// reset aim
		p.setAim(myIndex,myAim);
		p.setCircleColor(myIndex,new Color(125,0,0)); // light red
		p.setCircleColor(sister2.vertIndx,new Color(255,0,0)); // dark red
		p.setCircleColor(chap[1].vertIndx,new Color(0,200,0)); // green
		p.setCircleColor(chap[2].vertIndx,new Color(0,0,200)); // blue
		p.setCircleColor(newBrSpot.vertIndx,new Color(205,205,205)); // grey
	}
	
	// **********************
	
	/**
	 * This incorporates this branch point into 'packData'
	 * @return int, vertCount
	 */
	public int modifyPackData() {
		
		// start horizon with non-bdry edge
		eventHorizon=pdc.vertices[myIndex].getOuterEdges();
		HalfEdge tmphe=null;
		for (int j=0;(j<eventHorizon.size() && tmphe==null);j++) {
			HalfEdge he=eventHorizon.get(j);
			if (he.twin.face==null || he.twin.face.faceIndx>=0)
				tmphe=he;
		}
		eventHorizon=HalfLink.rotateMe(eventHorizon,tmphe);

		// first, split the flower, then split 3 edges
		overEdge=new HalfEdge[3];
		compEdge=new HalfEdge[3];
		chap=new Vertex[3];
		sisterEdge=RawManip.splitFlower_raw(pdc,jumpEdge[1],
				jumpEdge[2]);
		sister2=pdc.vertices[pdc.vertCount]; 
		// deBugging.DCELdebug.vertConsistency(pdc,myIndex);
		
		// create chaperone 1, to allow jump1 to separate from 'myIndex'
		tmphe=pdc.findHalfEdge(new EdgeSimple(
				myIndex,jumpCircle[1]));
		chapEdge[1]=RawManip.splitEdge_raw(pdc,tmphe);
		chap[1]=chapEdge[1].twin.origin;
		overEdge[1]=chapEdge[1].twin.prev.twin;
		compEdge[1]=overEdge[1].prev.twin;
		
		// create chaperone 2, to allow jump2 to separate from 'sister2'
		tmphe=pdc.findHalfEdge(new EdgeSimple(
				sister2.vertIndx,jumpCircle[2]));
		chapEdge[2]=RawManip.splitEdge_raw(pdc,tmphe);
		chap[2]=chapEdge[2].twin.origin;
		overEdge[2]=chapEdge[2].twin.prev.twin;
		compEdge[2]=overEdge[2].prev.twin;
		
		// create newBrPt
		RawManip.splitEdge_raw(pdc,sisterEdge);
		newBrSpot=sisterEdge.twin.origin;

		// get encircling link for local holonomy   
		myHoloBorder=HalfLink.leftsideLink(pdc,eventHorizon);
		myHoloBorder.add(0,eventHorizon.get(0));
		
		// add 'myHoloBorder' plus edge to pick up 'newBrSpot'
		layoutAddons=new HalfLink();
		layoutAddons.abutMore(myHoloBorder);
		layoutAddons.add(preJump[1].prev.twin);

		// record exclusions
		myExclusions=new ArrayList<combinatorics.komplex.Vertex>();
		Iterator<HalfEdge> eis=eventHorizon.iterator();
		while (eis.hasNext()) {
			myExclusions.add(eis.next().origin);
		}
		myExclusions.add(pdc.vertices[myIndex]);
		myExclusions.add(sister2);
		myExclusions.add(chap[1]);
		myExclusions.add(chap[2]);
		myExclusions.add(newBrSpot);
		
		return pdc.vertCount;
	}
	
	public int resetOverlaps(double o1,double o2) {
		if (o1<0 || o1>=1.0 || o2<0 || o2>=1.0)
			throw new DataException(
					"'chaperon' usage: 2 overlaps in [0,1]");
		
		// if jump 1 and jump 2 are separated by just one
		//   circle, neighbors then ovlp can be anything
		//   in [0,1], but I think ovlp[1] must equal ovlp[0].
		// REASON: If sister 2 goes to zero in radius, then sister1, jump 1, and
		//   prejump 2 go though common point (at the 'newBrSpot'), so sum of overlaps
		//   must be Pi, but jump 1 and prejump 2 are tangent, so overlap of jump 1
		//   and prejump2 must be Pi-ovlp[0]. By definition is this same overlap is
		//   the supplement of ovlp[1], so ovlp[0]=ovlp[1].
		// ?????? can sister 2 go to zero in radius ?????
		if (jumpEdge[1].prev.twin.prev.twin==jumpEdge[2]) {
			if (o2>o1)
				o2=o1;
			CirclePack.cpb.msg(
				"short jump: overlap 2 cut back to size of overlap 1");
		}
		else if (jumpEdge[2].prev.twin.prev.twin==jumpEdge[1]) {
			if (o1>o2)
				o1=o2;
			CirclePack.cpb.msg(
				"short jump: overlap 1 cut back to size of overlap 2");
		}
	
		overEdge[1].setInvDist(o1);
		compEdge[1].setInvDist(-1.0*o1);

		overEdge[2].setInvDist(o2);
		compEdge[2].setInvDist(-1.0*o2);
		return 1;
	}

	/**
	 * See if there are special actions for display. If so,
	 * do them, remove them, and pass the rest to 'super'.
	 * @param flagSegs flag sequences
	 * @return int count of display actions
	 */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		StringBuilder pulloff=new StringBuilder();
		Vector<Vector<String>> newFlagSegs=new Vector<Vector<String>>(1);
		Vector<String> items=new Vector<String>(2);
		int n=0;
		for (int j=0;j<flagSegs.size();j++) {
			items=flagSegs.get(j);
			String str=items.get(0);
			
			// get info to reconstruct new command: 
			// e.g. -s1fc20 converts to -cfc20 <sister1>
			String suff="";  // save suffex of original, e.g. 'fc5t4' 
			String target=null; // build target list
			Character c=null;  // possible number character
			if (str.length()>2)
				c=Character.valueOf(str.charAt(2));

			// look for objects to parse here
			char c2;
			if (str.length()>1 && 
					((c2=str.charAt(1))=='s' || c2=='h' || c2=='y' || c2=='j')) {
				if (str.startsWith("-s")) { // sisters: 1=original, 2=sister2 
					if (c!=null && (char)c=='1' || (char)c=='2') {
						if ((char)c=='1')
							target=new String(" "+myIndex);
						else
							target=new String(" "+sister2.vertIndx+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else { 
						target=new String(" "+myIndex+" "+sister2.vertIndx+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
				}
				else if (str.startsWith("-h")) { // chaperones
					if (c!=null && (char)c=='1' || (char)c=='2') {
						if ((char)c=='1')
							target=new String(" "+chap[1].vertIndx+" ");
						else
							target=new String(" "+chap[2].vertIndx+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else {
						target=new String(" "+chap[1].vertIndx+" "+chap[2].vertIndx+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
				}
				else if (str.contains("-j")) { // jump circles
					if (c!=null && ((char)c=='1' || (char)c=='2')) {
						if ((char)c=='1')
							target=new String(" "+jumpCircle[1]+" ");
						else
							target=new String(" "+jumpCircle[2]+" ");
						if (str.length()>3)
							suff=str.substring(3);
					}
					else { 
						target=new String(" "+jumpCircle[1]+" "+jumpCircle[2]+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
				}
				else if (str.startsWith("-y")) { // newBrSpot
					target=new String(" "+newBrSpot+" ");
				}
				
				// build display string
				pulloff.append("-c");
				pulloff.append(suff);
				pulloff.append(target);
				
				// found something? display on parent canvas
				if (pulloff.length()>0) {
					n=DisplayParser.dispParse(p,p.cpDrawing,StringUtil.flagSeg(pulloff.toString()));
				}
				
			}
			// else if -w flag, have parent run it, or store it to pass on to parent
			else {
				if (items.size()>0 && items.get(0).startsWith("-w")) {
					String fs=items.remove(0);
					CommandStrParser.jexecute(p,"disp "+fs);
					n++;
					if (items.size()>0) // save anything after the -w flag
						newFlagSegs.add(items);
				}
				else 
					newFlagSegs.add(items);
			}
				
		} // end of loop through items

		n+=DisplayParser.dispParse(p,p.cpDrawing,newFlagSegs);
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(p,false); 
		return n;
	}
	
}
